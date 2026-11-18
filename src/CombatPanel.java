import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

// Combat screen (main card "COMBAT").
//   PICK  : choose one of the Asset Library's maps.
//   BOARD : the map with tokens. Before combat ("placement mode") you place characters and NPCs and drag them
//           around freely. Start Combat assigns teams and rolls initiative; then the side panel (Current Turn,
//           Initiative Order, Teams, Combat Settings) runs the turns. End Combat returns to placement mode.
//
// Everything is saved in the Maps asset (custom_assets.json) next to its "grid", so it survives leaving the screen:
//   tokens    : [{uid, kind ("PC" | "NPC"), refId, label, x, y, hp, team (-1 none, 0-9), init}]
//               (x, y) is the token's top-left square. Size, max HP, speed, AC, initiative bonus and the token's
//               look are read from the character (by its "id", see CharacterStorage.loadWithIds) or the NPC asset
//               every time, so edits made elsewhere show up. Tokens whose character / NPC was deleted are dropped.
//   teamNames : TEAMS strings ("" shows as "Team N")
//   combat    : {round, active (uid), moveLeft, order: [uid, ...]} while combat is running (absent otherwise)
//
// Movement: 5 ft. per square (diagonals too), Water squares cost double, walls / closed doors / windows block,
// open doors don't, and corners can't be cut. Clear (no floor) and Pit squares can't be entered. Creatures can
// pass through allies (same team) but not enemies, and can't end their move in another creature's space.
class CombatPanel {

static final int TEAMS = 10;
static final Color[] TEAM_COLORS = {
        new Color(0x3B, 0x82, 0xF6), new Color(0xEF, 0x44, 0x44), new Color(0x22, 0xC5, 0x5E), new Color(0xEA, 0xB3, 0x08),
        new Color(0xA8, 0x55, 0xF7), new Color(0xF9, 0x73, 0x16), new Color(0x06, 0xB6, 0xD4), new Color(0xEC, 0x48, 0x99),
        new Color(0x84, 0xCC, 0x16), new Color(0xE5, 0xE7, 0xEB)};
static final String[] TEAM_COLOR_NAMES = {"Blue", "Red", "Green", "Yellow", "Purple", "Orange", "Cyan", "Pink", "Lime", "White"};
private static final Color NO_TEAM = new Color(0x9A, 0x96, 0x8C);
private static final Color REACH_FILL = new Color(80, 170, 255, 60), REACH_EDGE = new Color(120, 190, 255, 150);
private static final String TAB_TURN = "Current Turn", TAB_ORDER = "Initiative Order", TAB_TEAMS = "Teams", TAB_SETTINGS = "Combat Settings";
private static final String PC = "PC", NPC = "NPC";

// ===============================================================
// Size helpers (also used by the sheet and the NPC detail)
// ===============================================================
static int sizeSquares(String size) {
    if (size == null) return 1;
    switch (size.trim()) {
        case "Large": return 2;
        case "Huge": return 3;
        case "Gargantuan": return 4;
        default: return 1; // Tiny, Small, Medium
    }
}

static String footprintText(String size) {
    int n = sizeSquares(size);
    return n == 1 ? "1 × 1 square (5 ft.)" : n + " × " + n + " squares (" + n * 5 + " ft.)";
}

private static int leadingInt(String s, int def) {
    if (s == null) return def;
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("-?\\d+").matcher(s);
    return m.find() ? Integer.parseInt(m.group()) : def;
}

static String teamColorName(int team) {
    return team >= 0 && team < TEAMS ? TEAM_COLOR_NAMES[team] : "None";
}

// ===============================================================
// Token on the board
// ===============================================================
static class Tok {
    String uid = "", kind = NPC, refId = "", label = "";
    int x, y, hp = -1, team = -1, init;
    // read from the character / NPC (see applySource)
    String sourceName = "", sizeName = "Medium", subtitle = "";
    int size = 1, maxHp = 1, speed = 30, ac = 10, initBonus;
    Map<String, Object> style = Tokens.defaults(Tokens.NPC_COLOR);
    Map<?, ?> source; // the character record or NPC asset
    List<WeaponRules.Attack> attacks = new ArrayList<>(); // from held weapons (characters) or stat-block actions (NPCs)
    int attacksPerAction = 1;                              // Extra Attack / Multiattack

    boolean down() {
        return hp <= 0;
    }

    boolean covers(int cx, int cy) {
        return cx >= x && cy >= y && cx < x + size && cy < y + size;
    }
}

// ===============================================================
// State
// ===============================================================
final JPanel panel = new JPanel(new BorderLayout());

private final CardLayout outerLayout;
private final JPanel outerCards;
private final MapEditorPanel mapEditor;
private final CardLayout innerLayout = new CardLayout();
private final JPanel inner = new JPanel(innerLayout);
private final JPanel pickList = new JPanel();
private boolean boardShown;

private Map<String, Object> asset;
private MapEditorPanel.MapModel model = MapEditorPanel.MapModel.blank(20, 15);
private final List<Tok> tokens = new ArrayList<>();
private final String[] teamNames = new String[TEAMS];

private final Board board = new Board();
private final JLabel titleLabel = new JLabel();
private final JLabel savedLabel = new JLabel(" ");
private final JLabel statusLabel = new JLabel(" ");
private final JButton placeButton = Tokens.smallButton("+ Place Token", "Place a character or NPC on the map");
private final JButton startButton = App.overlayButton("⚔ Start Combat", true);
private final JButton editMapButton = Tokens.smallButton("✎ Edit Map", "Edit this map's walls, doors and floors");
private final JLabel combatBadge = new JLabel();

private final JPanel side = new JPanel(new BorderLayout());
private final JPanel sideContent = new JPanel();
private final JScrollPane sideScroll;
private final Map<String, JButton> tabButtons = new LinkedHashMap<>();
private String tab = TAB_TURN;

// placement mode
private Tok placing;      // template of the character / NPC being placed, or null
private int placingLeft;

// combat
private boolean inCombat;
private final List<Tok> order = new ArrayList<>();
private Tok active;
private int round = 1;
private int moveLeft;
private int attacksLeft;              // attacks left in this turn's Attack action
private boolean bonusAttackUsed;      // the Light-property (off-hand) extra attack
private WeaponRules.Attack targeting; // attack waiting for a target click, or null
private boolean moveMode;
private Map<Integer, Integer> reach = new HashMap<>(); // anchor key (y * width + x) -> movement cost in feet
private boolean[][] reachCells = new boolean[0][0];

// view toggles (Combat Settings)
private boolean showHp = true, showNames = true, showRange = true, showCoords = false, skipDefeated = true;

private final javax.swing.Timer saveTimer = new javax.swing.Timer(500, e -> persistNow());
private final Random rng = new Random();

CombatPanel(CardLayout outerLayout, JPanel outerCards, MapEditorPanel mapEditor) {
    this.outerLayout = outerLayout;
    this.outerCards = outerCards;
    this.mapEditor = mapEditor;
    Arrays.fill(teamNames, "");
    saveTimer.setRepeats(false);

    panel.setBackground(App.BG_DARK);
    inner.setBackground(App.BG_DARK);
    panel.add(inner, BorderLayout.CENTER);

    // ---------------- PICK card ----------------
    JPanel pick = App.buildSectionShell("Combat", outerLayout, outerCards, "MENU");
    pickList.setLayout(new BoxLayout(pickList, BoxLayout.Y_AXIS));
    pickList.setBackground(App.BG_DARK);
    pickList.setBorder(new EmptyBorder(40, 30, 30, 30));
    JScrollPane pickScroll = new JScrollPane(pickList);
    App.applyDarkScrollBar(pickScroll);
    pick.add(pickScroll, BorderLayout.CENTER);
    inner.add(pick, "PICK");

    // ---------------- BOARD card ----------------
    JPanel boardCard = new JPanel(new BorderLayout());
    boardCard.setBackground(App.SHEET_BG);

    JPanel top = new JPanel(new BorderLayout());
    top.setBackground(App.BG_PANEL);
    top.setBorder(new EmptyBorder(8, 14, 8, 14));
    JButton back = new JButton("← Maps");
    App.styleSecondaryButton(back);
    back.setBorder(BorderFactory.createCompoundBorder(new LineBorder(App.TEXT_MUTED, 1, true), new EmptyBorder(6, 12, 6, 12)));
    back.setToolTipText("Back to the map list (tokens and combat are saved with the map)");
    back.addActionListener(e -> showPick());
    titleLabel.setForeground(App.GOLD);
    titleLabel.setFont(new Font("Serif", Font.BOLD, 24));
    titleLabel.setBorder(new EmptyBorder(0, 16, 0, 12));
    titleLabel.setToolTipText("Map details");
    hoverLabel(titleLabel, App.GOLD, this::showMapInfo);
    savedLabel.setForeground(App.TEXT_MUTED);
    savedLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
    JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    left.setOpaque(false);
    left.add(back);
    left.add(titleLabel);
    left.add(savedLabel);
    top.add(left, BorderLayout.WEST);

    combatBadge.setForeground(App.GOLD);
    combatBadge.setFont(new Font("SansSerif", Font.BOLD, 13));
    combatBadge.setBorder(BorderFactory.createCompoundBorder(new LineBorder(App.GOLD, 1, true), new EmptyBorder(5, 10, 5, 10)));
    combatBadge.setToolTipText("Initiative order");
    hoverLabel(combatBadge, App.GOLD, () -> setTab(TAB_ORDER));

    JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
    right.setOpaque(false);
    placeButton.addActionListener(e -> showPlacePicker());
    startButton.setToolTipText("Assign teams, roll initiative and start the encounter");
    startButton.addActionListener(e -> showCombatSetup());
    editMapButton.addActionListener(e -> {
        if (asset == null) return;
        persistNow();
        String id = CustomAssets.str(asset, "id");
        mapEditor.open(id, true, "COMBAT", () -> reloadMap(id));
    });
    JButton zoomOut = Tokens.smallButton("−", "Zoom out");
    zoomOut.addActionListener(e -> board.zoomBy(1 / 1.25, board.getWidth() / 2.0, board.getHeight() / 2.0));
    JButton zoomIn = Tokens.smallButton("+", "Zoom in");
    zoomIn.addActionListener(e -> board.zoomBy(1.25, board.getWidth() / 2.0, board.getHeight() / 2.0));
    JButton fit = Tokens.smallButton("Fit", "Fit the whole map in view");
    fit.addActionListener(e -> board.fit());
    right.add(combatBadge);
    right.add(placeButton);
    right.add(startButton);
    right.add(editMapButton);
    right.add(zoomOut);
    right.add(zoomIn);
    right.add(fit);
    top.add(right, BorderLayout.EAST);
    boardCard.add(top, BorderLayout.NORTH);

    boardCard.add(board, BorderLayout.CENTER);

    // side panel (combat only)
    side.setBackground(App.SHEET_CARD);
    side.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, App.SHEET_BORDER));
    side.setPreferredSize(new Dimension(350, 100));
    JPanel tabs = new JPanel(new GridLayout(2, 2, 6, 6));
    tabs.setBackground(App.SHEET_CARD);
    tabs.setBorder(new EmptyBorder(10, 10, 8, 10));
    for (String t : new String[]{TAB_TURN, TAB_ORDER, TAB_TEAMS, TAB_SETTINGS}) {
        JButton b = new JButton(t);
        b.setFont(new Font("SansSerif", Font.BOLD, 12));
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> setTab(t));
        tabButtons.put(t, b);
        tabs.add(b);
    }
    side.add(tabs, BorderLayout.NORTH);
    sideContent.setLayout(new BoxLayout(sideContent, BoxLayout.Y_AXIS));
    sideContent.setBackground(App.SHEET_CARD);
    sideContent.setBorder(new EmptyBorder(6, 14, 14, 14));
    JPanel sideTop = new WidthTrackingPanel(); // follows the viewport width so rows never get clipped
    sideTop.setBackground(App.SHEET_CARD);
    sideTop.add(sideContent, BorderLayout.NORTH);
    sideScroll = new JScrollPane(sideTop);
    App.applyDarkScrollBar(sideScroll);
    sideScroll.setBorder(null);
    sideScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    sideScroll.getViewport().setBackground(App.SHEET_CARD);
    side.add(sideScroll, BorderLayout.CENTER);
    side.setVisible(false);
    boardCard.add(side, BorderLayout.EAST);

    JPanel bottom = new JPanel(new BorderLayout());
    bottom.setBackground(App.BG_PANEL);
    bottom.setBorder(new EmptyBorder(6, 14, 6, 14));
    statusLabel.setForeground(App.TEXT_MUTED);
    statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
    bottom.add(statusLabel, BorderLayout.WEST);
    JLabel help = new JLabel("1 square = 5 ft.   •   Drag tokens to reposition   •   Wheel: zoom   •   Right-drag: pan   •   Esc: cancel");
    help.setForeground(App.TEXT_MUTED);
    help.setFont(new Font("SansSerif", Font.PLAIN, 12));
    bottom.add(help, BorderLayout.EAST);
    boardCard.add(bottom, BorderLayout.SOUTH);
    inner.add(boardCard, "BOARD");

    // Esc cancels placing / moving (overlays answer Esc first when one is open)
    panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "combatCancel");
    panel.getActionMap().put("combatCancel", new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
            cancelModes();
        }

        @Override
        public boolean isEnabled() {
            if (!panel.isShowing() || !boardShown || (placing == null && !moveMode && targeting == null)) return false;
            JRootPane root = SwingUtilities.getRootPane(panel);
            return root != null && root.getLayeredPane().getComponentsInLayer(JLayeredPane.MODAL_LAYER).length == 0;
        }
    });

    panel.addComponentListener(new ComponentAdapter() {
        @Override
        public void componentShown(ComponentEvent e) {
            if (boardShown && asset != null) {
                // characters / NPCs may have been edited elsewhere (token look, HP, size...)
                refreshSources();
                refreshAll();
            } else {
                showPick();
            }
        }
    });
    showPick();
}

// ===============================================================
// Map list
// ===============================================================
private void showPick() {
    if (asset != null) persistNow();
    cancelModes();
    boardShown = false;
    pickList.removeAll();
    JLabel selectMapLabel = new JLabel("Select a map");
    selectMapLabel.setForeground(App.TEXT_LIGHT);
    selectMapLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
    selectMapLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
    pickList.add(selectMapLabel);
    pickList.add(Box.createRigidArea(new Dimension(0, 14)));

    List<Map<String, Object>> maps = CustomAssets.all(CustomAssets.MAPS);
    if (maps.isEmpty()) {
        JLabel none = new JLabel("None available. Create maps in Assets → Maps.");
        none.setForeground(App.TEXT_MUTED);
        none.setFont(new Font("SansSerif", Font.PLAIN, 16));
        none.setAlignmentX(Component.CENTER_ALIGNMENT);
        pickList.add(none);
    }
    for (Map<String, Object> m : maps) {
        String id = CustomAssets.str(m, "id");
        JPanel row = new JPanel(new BorderLayout(10, 2));
        row.setBackground(App.BG_PANEL);
        row.setBorder(BorderFactory.createCompoundBorder(new LineBorder(App.BG_FIELD, 1, true), new EmptyBorder(10, 14, 10, 14)));
        row.setMaximumSize(new Dimension(520, 64));
        row.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel name = new JLabel(CustomAssets.str(m, "name"));
        name.setForeground(App.GOLD);
        name.setFont(new Font("Serif", Font.BOLD, 17));
        int placed = m.get("tokens") instanceof List ? ((List<?>) m.get("tokens")).size() : 0;
        String extra = (placed > 0 ? "  •  " + placed + (placed == 1 ? " token" : " tokens") : "")
                + (m.get("combat") instanceof Map ? "  •  combat in progress" : "");
        JLabel sub = new JLabel(CustomAssets.summary(CustomAssets.MAPS, m) + extra);
        sub.setForeground(App.TEXT_MUTED);
        sub.setFont(new Font("SansSerif", Font.PLAIN, 12));
        row.add(name, BorderLayout.NORTH);
        row.add(sub, BorderLayout.CENTER);
        App.clickableCard(row, () -> open(id));
        pickList.add(row);
        pickList.add(Box.createRigidArea(new Dimension(0, 10)));
    }
    pickList.revalidate();
    pickList.repaint();
    innerLayout.show(inner, "PICK");
}

// ===============================================================
// Loading / saving
// ===============================================================
void open(String mapId) {
    Map<String, Object> a = CustomAssets.find(CustomAssets.MAPS, mapId);
    if (a == null) return;
    asset = a;
    model = MapEditorPanel.MapModel.fromJson(a.get("grid"));
    titleLabel.setText(CustomAssets.str(a, "name"));
    savedLabel.setText(" ");
    placing = null;
    moveMode = false;
    tab = TAB_TURN;
    loadState();
    refreshSources();
    boardShown = true;
    innerLayout.show(inner, "BOARD");
    refreshAll();
    setStatus(tokens.isEmpty() ? "Use + Place Token to put characters and NPCs on the map." : defaultStatus());
    SwingUtilities.invokeLater(board::fit);
}

// After the map editor closes: keep tokens, pick up the new grid (and size)
private void reloadMap(String mapId) {
    Map<String, Object> a = CustomAssets.find(CustomAssets.MAPS, mapId);
    if (a == null) {
        asset = null;
        showPick();
        return;
    }
    asset = a;
    model = MapEditorPanel.MapModel.fromJson(a.get("grid"));
    titleLabel.setText(CustomAssets.str(a, "name"));
    refreshSources();
    persistNow();
    refreshAll();
}

private void loadState() {
    tokens.clear();
    order.clear();
    active = null;
    inCombat = false;
    round = 1;
    Arrays.fill(teamNames, "");
    Object tl = asset.get("tokens");
    if (tl instanceof List) {
        for (Object o : (List<?>) tl) {
            if (!(o instanceof Map)) continue;
            Map<?, ?> m = (Map<?, ?>) o;
            Tok t = new Tok();
            t.uid = CustomAssets.str(m, "uid");
            if (t.uid.isEmpty()) t.uid = CustomAssets.newId();
            t.kind = PC.equals(CustomAssets.str(m, "kind")) ? PC : NPC;
            t.refId = CustomAssets.str(m, "refId");
            t.label = CustomAssets.str(m, "label");
            t.x = CustomAssets.intVal(m, "x", 0);
            t.y = CustomAssets.intVal(m, "y", 0);
            t.hp = CustomAssets.intVal(m, "hp", -1);
            t.team = Math.max(-1, Math.min(TEAMS - 1, CustomAssets.intVal(m, "team", -1)));
            t.init = CustomAssets.intVal(m, "init", 0);
            tokens.add(t);
        }
    }
    List<String> names = CustomAssets.list(asset, "teamNames");
    Object rawNames = asset.get("teamNames");
    if (rawNames instanceof List) {
        List<?> rn = (List<?>) rawNames;
        for (int i = 0; i < TEAMS && i < rn.size(); i++) teamNames[i] = rn.get(i) == null ? "" : String.valueOf(rn.get(i)).trim();
    } else {
        for (int i = 0; i < TEAMS && i < names.size(); i++) teamNames[i] = names.get(i);
    }
    Object c = asset.get("combat");
    if (c instanceof Map) {
        Map<?, ?> cm = (Map<?, ?>) c;
        inCombat = true;
        round = Math.max(1, CustomAssets.intVal(cm, "round", 1));
        moveLeft = CustomAssets.intVal(cm, "moveLeft", 0);
        attacksLeft = CustomAssets.intVal(cm, "attacksLeft", 1);
        bonusAttackUsed = CustomAssets.bool(cm, "bonusAttackUsed");
        for (String uid : CustomAssets.list(cm, "order")) {
            Tok t = byUid(uid);
            if (t != null && !order.contains(t)) order.add(t);
        }
        for (Tok t : tokens) if (!order.contains(t)) order.add(t);
        active = byUid(CustomAssets.str(cm, "active"));
        if (active == null && !order.isEmpty()) active = order.get(0);
    }
}

private Tok byUid(String uid) {
    for (Tok t : tokens) if (t.uid.equals(uid)) return t;
    return null;
}

// Re-reads every token's character / NPC; drops tokens whose source is gone and keeps the rest on the map
private void refreshSources() {
    Map<String, Map<?, ?>> pcs = new HashMap<>();
    for (Object o : CharacterStorage.loadWithIds()) {
        if (o instanceof Map) pcs.put(CharacterStorage.recStr((Map<?, ?>) o, "id"), (Map<?, ?>) o);
    }
    boolean changed = false;
    for (Iterator<Tok> it = tokens.iterator(); it.hasNext(); ) {
        Tok t = it.next();
        if (!applySource(t, pcs)) {
            it.remove();
            order.remove(t);
            if (active == t) active = order.isEmpty() ? null : order.get(0);
            changed = true;
            continue;
        }
        int nx = Math.max(0, Math.min(model.width - t.size, t.x)), ny = Math.max(0, Math.min(model.height - t.size, t.y));
        if (nx != t.x || ny != t.y) {
            t.x = nx;
            t.y = ny;
            changed = true;
        }
    }
    if (inCombat && order.isEmpty()) {
        inCombat = false;
        changed = true;
    }
    if (placing != null && !applySource(placing, pcs)) placing = null;
    if (moveMode) computeReach();
    if (changed) persistNow();
}

private boolean applySource(Tok t, Map<String, Map<?, ?>> pcs) {
    if (PC.equals(t.kind)) {
        Map<?, ?> rec = pcs.get(t.refId);
        if (rec == null) return false;
        t.source = rec;
        t.sourceName = CharacterStorage.recStr(rec, "name");
        if (t.sourceName.isEmpty()) t.sourceName = "Unnamed Character";
        t.sizeName = CharacterStorage.recStr(rec, "size").isEmpty() ? "Medium" : CharacterStorage.recStr(rec, "size");
        Integer hp = CharacterStorage.recInt(rec, "hp");
        t.maxHp = hp != null && hp > 0 ? hp : 1;
        Integer sp = CharacterStorage.recInt(rec, "speed");
        t.speed = sp != null ? Math.max(0, sp) : 30;
        int dexMod = GameData.abilityModifier(CharacterStorage.recStatsMap(rec).getOrDefault("DEX", 10));
        t.initBonus = dexMod;
        t.ac = WeaponRules.armorClass(rec);
        t.style = Tokens.read(rec.get("token"), Tokens.PC_COLOR);
        String cls = CharacterStorage.recStr(rec, "characterClass");
        t.subtitle = "Character · Level " + CharacterStorage.recLevel(rec) + (cls.isEmpty() ? "" : " " + cls);
        if (t.label.isEmpty() || !t.label.equals(t.sourceName)) t.label = t.sourceName; // characters always show their name
    } else {
        Map<String, Object> a = CustomAssets.find(CustomAssets.NPCS, t.refId);
        if (a == null) return false;
        t.source = a;
        t.sourceName = CustomAssets.str(a, "name");
        t.sizeName = CustomAssets.str(a, "size").isEmpty() ? "Medium" : CustomAssets.str(a, "size");
        t.maxHp = Math.max(1, leadingInt(CustomAssets.str(a, "hp"), 1));
        t.speed = Math.max(0, leadingInt(CustomAssets.str(a, "speed"), 30));
        t.initBonus = GameData.abilityModifier(CustomAssets.abilities(a).get("DEX"));
        t.ac = CustomAssets.intVal(a, "ac", 10);
        t.style = Tokens.read(a.get("token"), Tokens.NPC_COLOR);
        String role = CustomAssets.str(a, "role");
        t.subtitle = "NPC · CR " + CustomAssets.str(a, "cr") + (role.isEmpty() ? "" : " · " + role);
        if (t.label.isEmpty()) t.label = t.sourceName;
    }
    if (PC.equals(t.kind)) {
        t.attacks = WeaponRules.characterAttacks(t.source);
        t.attacksPerAction = WeaponRules.attacksPerAction(t.source);
    } else {
        @SuppressWarnings("unchecked")
        Map<String, Object> npc = (Map<String, Object>) t.source;
        t.attacks = WeaponRules.npcAttacks(npc);
        t.attacksPerAction = WeaponRules.npcAttacksPerAction(npc);
    }
    t.size = Math.min(sizeSquares(t.sizeName), Math.max(1, Math.min(model.width, model.height)));
    if (t.hp < 0 || t.hp > t.maxHp) t.hp = t.maxHp;
    return true;
}

private void persistSoon() {
    savedLabel.setText(" ");
    saveTimer.restart();
}

private void persistNow() {
    saveTimer.stop();
    if (asset == null) return;
    List<Object> tl = new ArrayList<>();
    for (Tok t : tokens) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("uid", t.uid);
        m.put("kind", t.kind);
        m.put("refId", t.refId);
        m.put("label", t.label);
        m.put("x", t.x);
        m.put("y", t.y);
        m.put("hp", t.hp);
        m.put("team", t.team);
        m.put("init", t.init);
        tl.add(m);
    }
    asset.put("tokens", tl);
    asset.put("teamNames", new ArrayList<Object>(Arrays.asList((Object[]) teamNames)));
    if (inCombat) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("round", round);
        c.put("active", active == null ? "" : active.uid);
        c.put("moveLeft", moveLeft);
        c.put("attacksLeft", attacksLeft);
        c.put("bonusAttackUsed", bonusAttackUsed);
        List<Object> ids = new ArrayList<>();
        for (Tok t : order) ids.add(t.uid);
        c.put("order", ids);
        asset.put("combat", c);
    } else {
        asset.remove("combat");
    }
    CustomAssets.putQuiet(CustomAssets.MAPS, asset);
    savedLabel.setText("Saved");
}

// ===============================================================
// Refresh helpers
// ===============================================================
private void refreshAll() {
    startButton.setVisible(!inCombat);
    combatBadge.setVisible(inCombat);
    combatBadge.setText("Round " + round + (active != null ? "  •  " + active.label + "'s turn" : ""));
    side.setVisible(inCombat);
    refreshSide();
    board.repaint();
    panel.revalidate();
}

private void setStatus(String s) {
    statusLabel.setText(s == null || s.isEmpty() ? " " : s);
}

private String defaultStatus() {
    if (inCombat && active != null) {
        return "Round " + round + ": " + active.label + "'s turn. " + moveLeft + " ft. of movement left.";
    }
    return tokens.size() + (tokens.size() == 1 ? " token" : " tokens") + " on the map. Drag tokens to move them, click one for details.";
}

private void cancelModes() {
    boolean had = placing != null || moveMode || targeting != null;
    targeting = null;
    placing = null;
    placingLeft = 0;
    if (moveMode) setMoveMode(false);
    if (had) {
        setStatus(defaultStatus());
        board.repaint();
    }
}

static String teamName(String[] names, int team) {
    if (team < 0 || team >= TEAMS) return "No team";
    String n = names[team] == null ? "" : names[team].trim();
    return n.isEmpty() ? "Team " + (team + 1) : n;
}

private String teamName(int team) {
    return teamName(teamNames, team);
}

private Color teamColor(int team) {
    return team >= 0 && team < TEAMS ? TEAM_COLORS[team] : NO_TEAM;
}

// ===============================================================
// Placement
// ===============================================================
private void showPlacePicker() {
    cancelModes();
    JPanel list = new JPanel();
    list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
    list.setOpaque(false);
    Runnable[] close = new Runnable[1];
    Map<String, Map<?, ?>> pcs = new LinkedHashMap<>();
    for (Object o : CharacterStorage.loadWithIds()) {
        if (o instanceof Map) pcs.put(CharacterStorage.recStr((Map<?, ?>) o, "id"), (Map<?, ?>) o);
    }

    list.add(sectionCaption("CHARACTERS"));
    if (pcs.isEmpty()) list.add(muted("No characters yet. Create them from Characters on the main menu."));
    for (Map.Entry<String, Map<?, ?>> en : pcs.entrySet()) {
        Tok tpl = new Tok();
        tpl.kind = PC;
        tpl.refId = en.getKey();
        if (!applySource(tpl, pcs)) continue;
        boolean onMap = false;
        for (Tok t : tokens) if (PC.equals(t.kind) && t.refId.equals(tpl.refId)) onMap = true;
        JButton act = App.overlayButton(onMap ? "On Map" : "Place", !onMap);
        act.setEnabled(!onMap);
        if (onMap) act.setToolTipText("Each character can be on the map once. Drag their token to move it.");
        act.addActionListener(e -> {
            close[0].run();
            startPlacing(tpl, 1);
        });
        list.add(pickerRow(tpl, tpl.subtitle + " · " + tpl.sizeName, null, act));
    }

    list.add(Box.createRigidArea(new Dimension(0, 12)));
    list.add(sectionCaption("NPCS"));
    int shown = 0;
    for (Map<String, Object> a : CustomAssets.all(CustomAssets.NPCS)) {
        String id = CustomAssets.str(a, "id");
        boolean onMap = false;
        for (Tok t : tokens) if (NPC.equals(t.kind) && t.refId.equals(id)) onMap = true;
        if (!onMap && !Tags.allowedAsset(CustomAssets.NPCS, a)) continue; // campaign content filters
        Tok tpl = new Tok();
        tpl.kind = NPC;
        tpl.refId = id;
        if (!applySource(tpl, pcs)) continue;
        JSpinner qty = new JSpinner(new SpinnerNumberModel(1, 1, 20, 1));
        qty.setToolTipText("How many to place (they're numbered: " + tpl.sourceName + " 1, " + tpl.sourceName + " 2, ...)");
        qty.setPreferredSize(new Dimension(54, 28));
        styleSpinner(qty);
        JButton act = App.overlayButton("Place", true);
        act.addActionListener(e -> {
            close[0].run();
            startPlacing(tpl, (Integer) qty.getValue());
        });
        list.add(pickerRow(tpl, tpl.subtitle + " · " + tpl.sizeName + " · HP " + tpl.maxHp, qty, act));
        shown++;
    }
    if (shown == 0) list.add(muted("No NPCs to place. Create them with Characters → Add New Character → NPC, or in Assets → NPCs."));

    JScrollPane sp = new JScrollPane(list);
    App.applyDarkScrollBar(sp);
    sp.setBorder(null);
    sp.setOpaque(false);
    sp.getViewport().setOpaque(false);
    sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    sp.setPreferredSize(new Dimension(560, Math.min(460, list.getPreferredSize().height + 8)));
    close[0] = App.showSheetOverlay(board, "Place Token", sp, null);
}

private JPanel pickerRow(Tok tpl, String sub, JComponent extra, JButton act) {
    JPanel row = new JPanel(new BorderLayout(10, 0));
    row.setOpaque(false);
    row.setBorder(new EmptyBorder(4, 2, 4, 2));
    row.setAlignmentX(Component.LEFT_ALIGNMENT);
    row.setMaximumSize(new Dimension(560, 52));
    row.add(new Tokens.Preview(tpl.style, tpl.sourceName, 40, null), BorderLayout.WEST);
    JLabel l = new JLabel("<html><b>" + App.escapeHtml(tpl.sourceName) + "</b><br><span style='color:#aaa5a0'>"
            + App.escapeHtml(sub) + "</span></html>");
    l.setForeground(App.GOLD);
    l.setFont(new Font("SansSerif", Font.PLAIN, 13));
    App.clickable(l, () -> showSourceDetail(l, tpl));
    row.add(l, BorderLayout.CENTER);
    JPanel r = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
    r.setOpaque(false);
    if (extra != null) r.add(extra);
    r.add(act);
    row.add(r, BorderLayout.EAST);
    return row;
}

private void startPlacing(Tok tpl, int count) {
    placing = tpl;
    placingLeft = count;
    setStatus(placingStatus());
    board.repaint();
}

private String placingStatus() {
    return "Click a square to place " + placing.sourceName + (placingLeft > 1 ? " (" + placingLeft + " left)" : "")
            + ". Right-click or Esc to stop.";
}

// Next label for a new token: characters use their name; NPCs are numbered once there's more than one
private String labelFor(Tok tpl) {
    if (PC.equals(tpl.kind)) return tpl.sourceName;
    String name = tpl.sourceName;
    int max = 0;
    boolean any = false;
    for (Tok t : tokens) {
        if (!NPC.equals(t.kind) || !t.refId.equals(tpl.refId)) continue;
        any = true;
        if (t.label.equals(name)) {
            t.label = name + " 1"; // the first one gets a number once a second arrives
            max = Math.max(max, 1);
        } else if (t.label.startsWith(name + " ")) {
            try {
                max = Math.max(max, Integer.parseInt(t.label.substring(name.length() + 1).trim()));
            } catch (NumberFormatException ignored) {
                // renamed token: doesn't affect numbering
            }
        }
    }
    return any ? name + " " + (max + 1) : name;
}

private int[] anchorFor(int size, int cx, int cy) {
    return new int[]{Math.max(0, Math.min(model.width - size, cx)), Math.max(0, Math.min(model.height - size, cy))};
}

// In bounds and not overlapping another token (`ignoreDown`: defeated tokens don't count)
private boolean fits(Tok self, int size, int x, int y, boolean ignoreDown) {
    if (x < 0 || y < 0 || x + size > model.width || y + size > model.height) return false;
    for (Tok o : tokens) {
        if (o == self || (ignoreDown && o.down())) continue;
        if (x < o.x + o.size && o.x < x + size && y < o.y + o.size && o.y < y + size) return false;
    }
    return true;
}

private void placeAt(int cx, int cy) {
    int[] a = anchorFor(placing.size, cx, cy);
    if (!fits(null, placing.size, a[0], a[1], false)) {
        setStatus("That space is taken. " + placingStatus());
        return;
    }
    Tok t = new Tok();
    t.uid = CustomAssets.newId();
    t.kind = placing.kind;
    t.refId = placing.refId;
    t.label = labelFor(placing);
    t.x = a[0];
    t.y = a[1];
    Map<String, Map<?, ?>> pcs = new HashMap<>();
    if (placing.source != null && PC.equals(placing.kind)) pcs.put(placing.refId, placing.source);
    applySource(t, pcs);
    t.hp = t.maxHp;
    tokens.add(t);
    if (inCombat) {
        // joins the fight: default team, rolled initiative, slotted into the order
        t.team = PC.equals(t.kind) ? 0 : 1;
        t.init = rollD20() + t.initBonus;
        order.add(t);
        sortOrder();
    }
    placingLeft--;
    if (placingLeft <= 0 || PC.equals(t.kind)) {
        placing = null;
        setStatus("Placed " + t.label + "." + (inCombat ? " Joined combat with initiative " + t.init + "." : ""));
    } else {
        setStatus("Placed " + t.label + ". " + placingStatus());
    }
    if (moveMode) computeReach();
    persistSoon();
    refreshAll();
}

private void removeToken(Tok t) {
    tokens.remove(t);
    if (inCombat) {
        int idx = order.indexOf(t);
        order.remove(t);
        if (active == t) {
            setMoveMode(false);
            if (order.isEmpty()) active = null;
            else {
                active = order.get(Math.min(idx, order.size() - 1));
                moveLeft = active.speed;
                startTurnActions();
            }
        }
        if (order.isEmpty()) endCombat();
    }
    if (moveMode) computeReach();
    setStatus("Removed " + t.label + " from the map.");
    persistSoon();
    refreshAll();
}

// ===============================================================
// Combat flow
// ===============================================================
private int rollD20() {
    return rng.nextInt(20) + 1;
}

private void sortOrder() {
    order.sort((a, b) -> {
        if (a.init != b.init) return b.init - a.init;
        if (a.initBonus != b.initBonus) return b.initBonus - a.initBonus;
        return a.label.compareToIgnoreCase(b.label);
    });
}

private void showCombatSetup() {
    cancelModes();
    if (tokens.isEmpty()) {
        setStatus("Place at least one token before starting combat (+ Place Token).");
        return;
    }
    JPanel body = new JPanel();
    body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
    body.setOpaque(false);

    // ---- team names ----
    body.add(sectionCaption("TEAMS"));
    JLabel teamHelp = muted("Up to 10 color-coded teams. Leave a name blank to show \"Team 1\", \"Team 2\" and so on.");
    body.add(teamHelp);
    body.add(Box.createRigidArea(new Dimension(0, 6)));
    JPanel teamGrid = new JPanel(new GridLayout(0, 2, 10, 6));
    teamGrid.setOpaque(false);
    teamGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
    JTextField[] nameFields = new JTextField[TEAMS];
    for (int i = 0; i < TEAMS; i++) {
        JPanel r = new JPanel(new BorderLayout(6, 0));
        r.setOpaque(false);
        r.add(swatch(TEAM_COLORS[i], 18, TEAM_COLOR_NAMES[i]), BorderLayout.WEST);
        JTextField f = App.overlayField(teamNames[i], 12);
        f.setToolTipText(TEAM_COLOR_NAMES[i] + " team name (blank = Team " + (i + 1) + ")");
        nameFields[i] = f;
        r.add(f, BorderLayout.CENTER);
        teamGrid.add(r);
    }
    body.add(teamGrid);
    body.add(Box.createRigidArea(new Dimension(0, 14)));

    // ---- combatants ----
    body.add(sectionCaption("COMBATANTS & INITIATIVE (d20 + DEX modifier)"));
    JPanel rows = new JPanel(new GridBagLayout());
    rows.setOpaque(false);
    GridBagConstraints c = new GridBagConstraints();
    c.insets = new Insets(3, 4, 3, 4);
    c.anchor = GridBagConstraints.WEST;
    List<Tok> list = new ArrayList<>(tokens);
    Map<Tok, JComboBox<Integer>> teamCombos = new LinkedHashMap<>();
    Map<Tok, JSpinner> initSpins = new LinkedHashMap<>();
    List<Runnable> rerolls = new ArrayList<>();
    int y = 0;
    for (Tok t : list) {
        c.gridy = y++;
        c.gridx = 0;
        rows.add(new Tokens.Preview(t.style, t.label, 30, teamColor(t.team)), c);
        c.gridx = 1;
        JLabel nm = new JLabel(t.label);
        nm.setForeground(App.TEXT_LIGHT);
        nm.setFont(new Font("SansSerif", Font.BOLD, 13));
        nm.setPreferredSize(new Dimension(150, 22));
        App.clickable(nm, () -> showTokenDetail(nm, t));
        rows.add(nm, c);
        c.gridx = 2;
        JComboBox<Integer> tc = teamCombo(t.team >= 0 ? t.team : (PC.equals(t.kind) ? 0 : 1), nameFields);
        teamCombos.put(t, tc);
        rows.add(tc, c);
        c.gridx = 3;
        JLabel rollLbl = new JLabel();
        rollLbl.setForeground(App.TEXT_MUTED);
        rollLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        rollLbl.setPreferredSize(new Dimension(92, 22));
        rows.add(rollLbl, c);
        c.gridx = 4;
        JSpinner sp = new JSpinner(new SpinnerNumberModel(0, -20, 60, 1));
        sp.setPreferredSize(new Dimension(60, 28));
        sp.setToolTipText("Initiative (type to overwrite the roll)");
        styleSpinner(sp);
        initSpins.put(t, sp);
        rows.add(sp, c);
        c.gridx = 5;
        JButton re = Tokens.smallButton("🎲", "Re-roll initiative for " + t.label);
        Runnable roll = () -> {
            int d = rollD20();
            rollLbl.setText("d20 " + d + " " + (t.initBonus >= 0 ? "+ " : "− ") + Math.abs(t.initBonus));
            sp.setValue(d + t.initBonus);
        };
        re.addActionListener(e -> roll.run());
        rerolls.add(roll);
        roll.run();
        rows.add(re, c);
    }
    JScrollPane sp = new JScrollPane(rows);
    App.applyDarkScrollBar(sp);
    sp.setBorder(new LineBorder(App.SHEET_BORDER, 1, true));
    sp.setOpaque(false);
    sp.getViewport().setOpaque(false);
    sp.setPreferredSize(new Dimension(Math.max(560, rows.getPreferredSize().width + 20), Math.min(300, rows.getPreferredSize().height + 8)));
    sp.setAlignmentX(Component.LEFT_ALIGNMENT);
    body.add(sp);
    body.add(Box.createRigidArea(new Dimension(0, 12)));

    Runnable[] close = new Runnable[1];
    JPanel buttons = new JPanel(new BorderLayout());
    buttons.setOpaque(false);
    buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
    JButton rerollAll = App.overlayButton("🎲 Re-roll All", false);
    rerollAll.addActionListener(e -> {
        for (Runnable r : rerolls) r.run();
    });
    JPanel rightB = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    rightB.setOpaque(false);
    JButton cancel = App.overlayButton("Cancel", false);
    cancel.addActionListener(e -> close[0].run());
    JButton begin = App.overlayButton("⚔ Begin Combat", true);
    begin.addActionListener(e -> {
        for (int i = 0; i < TEAMS; i++) teamNames[i] = nameFields[i].getText().trim();
        for (Tok t : list) {
            if (!tokens.contains(t)) continue;
            Object sel = teamCombos.get(t).getSelectedItem();
            t.team = sel instanceof Integer ? (Integer) sel : -1;
            try {
                initSpins.get(t).commitEdit();
            } catch (java.text.ParseException ignored) {
                // keep the last valid value
            }
            t.init = (Integer) initSpins.get(t).getValue();
        }
        close[0].run();
        beginCombat();
    });
    rightB.add(cancel);
    rightB.add(begin);
    buttons.add(rerollAll, BorderLayout.WEST);
    buttons.add(rightB, BorderLayout.EAST);
    body.add(buttons);
    close[0] = App.showSheetOverlay(board, "Start Combat", body, begin);
}

private void beginCombat() {
    inCombat = true;
    order.clear();
    order.addAll(tokens);
    sortOrder();
    round = 1;
    active = null;
    for (Tok t : order) {
        if (!skipDefeated || !t.down()) {
            active = t;
            break;
        }
    }
    if (active == null && !order.isEmpty()) active = order.get(0);
    moveLeft = active != null ? active.speed : 0;
    startTurnActions();
    tab = TAB_TURN;
    setStatus(defaultStatus());
    persistNow();
    refreshAll();
}

private void endTurn() {
    if (!inCombat || order.isEmpty()) return;
    setMoveMode(false);
    int n = order.size();
    int i = Math.max(0, order.indexOf(active));
    Tok next = active;
    for (int step = 0; step < n; step++) {
        i = (i + 1) % n;
        if (i == 0) round++;
        next = order.get(i);
        if (!skipDefeated || !next.down()) break;
    }
    active = next;
    moveLeft = active.speed;
    startTurnActions();
    setStatus(defaultStatus());
    persistSoon();
    refreshAll();
}

private void endCombat() {
    setMoveMode(false);
    inCombat = false;
    order.clear();
    active = null;
    round = 1;
    setStatus("Combat ended. Back to placement: drag tokens freely, or Start Combat again.");
    persistNow();
    refreshAll();
}

private void setMoveMode(boolean on) {
    if (on) targeting = null;
    moveMode = on && inCombat && active != null && moveLeft > 0 && !active.down();
    if (moveMode) {
        computeReach();
        setStatus(reach.isEmpty() ? active.label + " can't reach any open square from here."
                : "Click a highlighted square to move " + active.label + " (" + moveLeft + " ft. left). Right-click or Esc to stop.");
    } else {
        reach = new HashMap<>();
        reachCells = new boolean[0][0];
    }
    board.repaint();
}

// ===============================================================
// Movement (reachable squares)
// ===============================================================
private boolean passable(int cx, int cy) {
    if (cx < 0 || cy < 0 || cx >= model.width || cy >= model.height) return false;
    int f = model.floor[cy][cx];
    return f != 0 && f != 5; // no floor / pit
}

// True if the edge crossed when stepping from (cx, cy) by one orthogonal square blocks movement
private boolean edgeBlocks(int cx, int cy, int dx, int dy) {
    if (cx < 0 || cy < 0 || cx >= model.width || cy >= model.height) return true;
    int v;
    if (dx == 1) v = model.vEdges[cy][cx + 1];
    else if (dx == -1) v = model.vEdges[cy][cx];
    else if (dy == 1) v = model.hEdges[cy + 1][cx];
    else v = model.hEdges[cy][cx];
    return v == MapEditorPanel.WALL || v == MapEditorPanel.DOOR_CLOSED || v == MapEditorPanel.WINDOW;
}

private boolean enemyAt(Tok self, int cx, int cy) {
    for (Tok o : tokens) {
        if (o == self || o.down() || !o.covers(cx, cy)) continue;
        if (self.team < 0 || o.team != self.team) return true;
    }
    return false;
}

private boolean canStep(Tok t, int x, int y, int dx, int dy) {
    for (int oy = 0; oy < t.size; oy++) {
        for (int ox = 0; ox < t.size; ox++) {
            int cx = x + ox, cy = y + oy, nx = cx + dx, ny = cy + dy;
            if (!passable(nx, ny) || enemyAt(t, nx, ny)) return false;
            if (dx != 0 && dy != 0) {
                // no corner cutting: both L-shaped paths around the corner must be open
                if (edgeBlocks(cx, cy, dx, 0) || edgeBlocks(cx + dx, cy, 0, dy)
                        || edgeBlocks(cx, cy, 0, dy) || edgeBlocks(cx, cy + dy, dx, 0)) return false;
            } else if (edgeBlocks(cx, cy, dx, dy)) {
                return false;
            }
        }
    }
    return true;
}

private boolean difficultAt(int x, int y, int size) {
    for (int oy = 0; oy < size; oy++) for (int ox = 0; ox < size; ox++) if (model.floor[y + oy][x + ox] == 4) return true;
    return false;
}

private void computeReach() {
    reach = new HashMap<>();
    reachCells = new boolean[model.height][model.width];
    if (active == null) return;
    int w = model.width, h = model.height, size = active.size;
    int[] dist = new int[w * h];
    Arrays.fill(dist, Integer.MAX_VALUE);
    int start = active.y * w + active.x;
    if (start < 0 || start >= dist.length) return;
    dist[start] = 0;
    PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[0] - b[0]);
    pq.add(new int[]{0, start});
    while (!pq.isEmpty()) {
        int[] cur = pq.poll();
        if (cur[0] > dist[cur[1]]) continue;
        int x = cur[1] % w, y = cur[1] / w;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;
                int nx = x + dx, ny = y + dy;
                if (nx < 0 || ny < 0 || nx + size > w || ny + size > h) continue;
                if (!canStep(active, x, y, dx, dy)) continue;
                int nc = cur[0] + (difficultAt(nx, ny, size) ? 10 : 5);
                if (nc > moveLeft || nc >= dist[ny * w + nx]) continue;
                dist[ny * w + nx] = nc;
                pq.add(new int[]{nc, ny * w + nx});
            }
        }
    }
    for (int k = 0; k < dist.length; k++) {
        if (k == start || dist[k] == Integer.MAX_VALUE) continue;
        int x = k % w, y = k / w;
        if (!fits(active, size, x, y, true)) continue; // can't end in another creature's space
        reach.put(k, dist[k]);
        for (int oy = 0; oy < size; oy++) for (int ox = 0; ox < size; ox++) reachCells[y + oy][x + ox] = true;
    }
}

// Cheapest reachable anchor whose footprint covers square (cx, cy), or null
private int[] reachAnchorFor(int cx, int cy, double fx, double fy) {
    if (active == null || reach.isEmpty()) return null;
    int w = model.width, size = active.size;
    int[] best = null;
    double bestScore = Double.MAX_VALUE;
    for (Map.Entry<Integer, Integer> en : reach.entrySet()) {
        int ax = en.getKey() % w, ay = en.getKey() / w;
        if (cx < ax || cy < ay || cx >= ax + size || cy >= ay + size) continue;
        double d = Math.hypot(ax + size / 2.0 - fx, ay + size / 2.0 - fy);
        double score = en.getValue() * 10 + d;
        if (score < bestScore) {
            bestScore = score;
            best = new int[]{ax, ay, en.getValue()};
        }
    }
    return best;
}

private void moveActiveTo(int ax, int ay, int cost) {
    active.x = ax;
    active.y = ay;
    moveLeft = Math.max(0, moveLeft - cost);
    setMoveMode(moveLeft > 0);
    if (!moveMode) setStatus(active.label + " moved " + cost + " ft. No movement left this turn.");
    else setStatus(active.label + " moved " + cost + " ft. " + moveLeft + " ft. left: click another square, or Esc to stop.");
    persistSoon();
    refreshAll();
}

// ===============================================================
// Attacks: range, line of sight, rolls
// ===============================================================
private void startTurnActions() {
    attacksLeft = active != null ? Math.max(1, active.attacksPerAction) : 0;
    bonusAttackUsed = false;
    targeting = null;
}

private static boolean hasOffHandAttack(Tok t) {
    for (WeaponRules.Attack a : t.attacks) if (a.offHand) return true;
    return false;
}

// The Light-property extra attack needs an attack made this turn (with the Attack action) first
private boolean canBonusAttack(Tok t) {
    return hasOffHandAttack(t) && !bonusAttackUsed && attacksLeft < Math.max(1, t.attacksPerAction);
}

private static int gapCells(int a, int as, int b, int bs) {
    if (b > a + as - 1) return b - (a + as - 1);
    if (a > b + bs - 1) return a - (b + bs - 1);
    return 0;
}

// Grid distance between two creatures' nearest squares (2024: every square, diagonals too, is 5 ft.)
static int distanceFt(Tok a, Tok b) {
    return Math.max(gapCells(a.x, a.size, b.x, b.size), gapCells(a.y, a.size, b.y, b.size)) * 5;
}

private int edgeValue(int cx, int cy, int dx, int dy) {
    if (cx < 0 || cy < 0 || cx >= model.width || cy >= model.height) return MapEditorPanel.WALL;
    if (dx == 1) return model.vEdges[cy][cx + 1];
    if (dx == -1) return model.vEdges[cy][cx];
    if (dy == 1) return model.hEdges[cy + 1][cx];
    return model.hEdges[cy][cx];
}

private static boolean blocksSight(int v) {
    return v == MapEditorPanel.WALL || v == MapEditorPanel.DOOR_CLOSED;
}

// {blocked (1/0), cover (1 = half cover from a window / low wall)} along the line between the creatures' centers
private int[] lineOfSight(Tok a, Tok b) {
    double x0 = a.x + a.size / 2.0, y0 = a.y + a.size / 2.0, x1 = b.x + b.size / 2.0, y1 = b.y + b.size / 2.0;
    double len = Math.hypot(x1 - x0, y1 - y0);
    int steps = Math.max(1, (int) Math.ceil(len / 0.05));
    int px = (int) Math.floor(x0), py = (int) Math.floor(y0);
    boolean cover = false;
    for (int i = 1; i <= steps; i++) {
        double t = i / (double) steps;
        int nx = (int) Math.floor(x0 + (x1 - x0) * t), ny = (int) Math.floor(y0 + (y1 - y0) * t);
        if (nx == px && ny == py) continue;
        int dx = Integer.signum(nx - px), dy = Integer.signum(ny - py);
        if (dx != 0 && dy != 0) {
            // passing a corner: blocked only if both ways around it are blocked
            int a1 = edgeValue(px, py, dx, 0), a2 = edgeValue(px + dx, py, 0, dy);
            int b1 = edgeValue(px, py, 0, dy), b2 = edgeValue(px, py + dy, dx, 0);
            boolean pathA = blocksSight(a1) || blocksSight(a2), pathB = blocksSight(b1) || blocksSight(b2);
            if (pathA && pathB) return new int[]{1, 0};
            if (a1 == MapEditorPanel.WINDOW || a2 == MapEditorPanel.WINDOW || b1 == MapEditorPanel.WINDOW || b2 == MapEditorPanel.WINDOW) cover = true;
        } else {
            int v = edgeValue(px, py, dx, dy);
            if (blocksSight(v)) return new int[]{1, 0};
            if (v == MapEditorPanel.WINDOW) cover = true;
        }
        px = nx;
        py = ny;
    }
    return new int[]{0, cover ? 1 : 0};
}

static final class TargetCheck {
    boolean ok = true, disadvantage, advantage, ranged;
    int distance, cover;
    String reason = "";
    List<String> notes = new ArrayList<>();
}

private boolean enemyWithin5(Tok att) {
    for (Tok o : tokens) {
        if (o == att || o.down()) continue;
        if (att.team >= 0 && o.team == att.team) continue;
        if (distanceFt(att, o) <= 5) return true;
    }
    return false;
}

TargetCheck checkTarget(Tok att, Tok target, WeaponRules.Attack atk) {
    TargetCheck tc = new TargetCheck();
    tc.distance = distanceFt(att, target);
    boolean inReach = atk.melee && tc.distance <= atk.reach;
    boolean inRange = atk.rangeLong > 0 && tc.distance <= atk.rangeLong;
    if (!inReach && !inRange) {
        tc.ok = false;
        tc.reason = "Out of range (" + tc.distance + " ft.; " + atk.reachText() + ").";
        return tc;
    }
    int[] los = lineOfSight(att, target);
    if (los[0] == 1) {
        tc.ok = false;
        tc.reason = "No line of sight: a wall or closed door is in the way.";
        return tc;
    }
    tc.cover = los[1];
    if (tc.cover > 0) tc.notes.add("Half cover from a window or low wall: +2 AC.");
    tc.ranged = !inReach;
    if (tc.ranged) {
        if (atk.rangeNormal > 0 && tc.distance > atk.rangeNormal) {
            tc.disadvantage = true;
            tc.notes.add("Beyond normal range (" + atk.rangeNormal + " ft.): Disadvantage.");
        }
        boolean crossbowExpert = PC.equals(att.kind) && att.source != null && WeaponRules.featCount(att.source, "Crossbow Expert") > 0;
        if (enemyWithin5(att) && !crossbowExpert) {
            tc.disadvantage = true;
            tc.notes.add("An enemy is within 5 ft. of you: ranged attacks have Disadvantage.");
        }
        if (atk.melee && atk.weapon != null && atk.weapon.thrown()) tc.notes.add("Thrown: the weapon lands near the target.");
    }
    if (atk.heavyPenalty) {
        tc.disadvantage = true;
        tc.notes.add("Heavy weapon without 13 " + (atk.melee ? "Strength" : "Dexterity") + ": Disadvantage.");
    }
    if (target.down()) {
        tc.advantage = true;
        tc.notes.add(target.label + " is down (0 HP): attacks have Advantage" + (tc.distance <= 5 ? ", and a hit from within 5 ft. is a Critical Hit." : "."));
    }
    return tc;
}

// Ammunition of this name in a character's equipment
private int ammoCount(Tok t, String ammo) {
    if (!PC.equals(t.kind) || t.source == null || ammo.isEmpty()) return -1;
    int n = 0;
    for (String e : CharacterStorage.recList(t.source, "equipment")) {
        AmmoItem a = WeaponData.findAmmo(e);
        if (a != null && a.name.equalsIgnoreCase(ammo)) n += App.itemCount(e);
    }
    return n;
}

// Uses (-1) or gives back (+1) one piece of ammunition in characters.json
private void changeAmmo(Tok t, String ammo, int delta) {
    List<Object> all = CharacterStorage.loadWithIds();
    for (Object o : all) {
        if (!(o instanceof Map)) continue;
        @SuppressWarnings("unchecked")
        Map<String, Object> rec = (Map<String, Object>) o;
        if (!t.refId.equals(CharacterStorage.recStr(rec, "id"))) continue;
        List<String> eq = CharacterStorage.recList(rec, "equipment");
        if (delta < 0) {
            for (int i = 0; i < eq.size(); i++) {
                AmmoItem a = WeaponData.findAmmo(eq.get(i));
                if (a != null && a.name.equalsIgnoreCase(ammo)) {
                    App.changeItemCount(eq, i, -1);
                    break;
                }
            }
        } else {
            App.addItem(eq, ammo);
        }
        rec.put("equipment", new ArrayList<Object>(eq));
        CharacterStorage.saveAllCharacters(all);
        t.source = rec;
        return;
    }
}

private void showAttackPicker(Component anchor, Tok t) {
    if (t.attacks.isEmpty()) {
        setStatus(t.label + " has no attacks.");
        return;
    }
    JPanel list = new JPanel();
    list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
    list.setOpaque(false);
    Runnable[] close = new Runnable[1];
    for (WeaponRules.Attack a : t.attacks) {
        boolean usable = a.offHand ? canBonusAttack(t) : attacksLeft > 0;
        int ammo = a.ammo.isEmpty() ? -1 : ammoCount(t, a.ammo);
        boolean ammoOk = ammo != 0 || a.melee; // thrown / melee weapons with ammo text still work in melee
        String why = !usable ? (a.offHand ? (bonusAttackUsed ? "Bonus attack already used this turn." : "Attack with a Light weapon first.") : "No attacks left this turn.")
                : !ammoOk ? "No " + a.ammo.toLowerCase() + "s left in Equipment." : "";
        JButton b = App.overlayButton("", false);
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setText("<html><b>" + App.escapeHtml(a.name) + "</b>  <span style='color:#d4af37'>" + WeaponRules.signed(a.toHit) + " to hit</span>"
                + (a.mastery.isEmpty() ? "" : "  <span style='color:#aaa5a0'>· Mastery: " + a.mastery + "</span>")
                + "<br><span style='color:#aaa5a0'>" + App.escapeHtml(a.damageText() + " · " + a.reachText()
                + (ammo >= 0 ? " · " + ammo + " " + a.ammo.toLowerCase() + (ammo == 1 ? "" : "s") : "")
                + (a.offHand ? " · Bonus Action" : "")) + "</span>"
                + (why.isEmpty() ? "" : "<br><span style='color:#dc3c3c'>" + App.escapeHtml(why) + "</span>") + "</html>");
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(520, 70));
        b.setEnabled(usable && ammoOk);
        b.addActionListener(e -> {
            close[0].run();
            startTargeting(a);
        });
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(560, 72));
        row.add(b, BorderLayout.CENTER);
        JButton info = Tokens.smallButton("i", "Attack breakdown");
        info.addActionListener(e -> SheetWeapons.showAttackDetail(info, PC.equals(t.kind) ? t.source : null, a));
        row.add(info, BorderLayout.EAST);
        list.add(row);
        list.add(Box.createRigidArea(new Dimension(0, 5)));
    }
    if (PC.equals(t.kind)) {
        JLabel hint = muted("Attacks come from what " + t.label + " holds (Hands & Attacks on the character sheet).");
        list.add(hint);
    }
    JScrollPane sp = new JScrollPane(list);
    App.applyDarkScrollBar(sp);
    sp.setBorder(null);
    sp.setOpaque(false);
    sp.getViewport().setOpaque(false);
    sp.setPreferredSize(new Dimension(580, Math.min(440, list.getPreferredSize().height + 10)));
    close[0] = App.showSheetOverlay(anchor, t.label + ": Attack", sp, null);
}

private void startTargeting(WeaponRules.Attack a) {
    if (moveMode) setMoveMode(false);
    placing = null;
    targeting = a;
    int inRange = 0;
    for (Tok o : tokens) if (o != active && checkTarget(active, o, a).ok) inRange++;
    setStatus("Attack with " + a.name + " (" + a.reachText() + "): click a target. " + inRange
            + (inRange == 1 ? " creature is" : " creatures are") + " in range. Right-click or Esc to cancel.");
    refreshSide();
    board.repaint();
}

// Roll to hit (Advantage / Disadvantage, reroll, or replace the d20 with your own roll), then roll damage
// (doubled dice on a Critical Hit, reroll or replace the total) and apply it to the target
private void showAttackRoll(Component anchor, Tok att, Tok target, WeaponRules.Attack atk, TargetCheck tc) {
    // the attack is made: spend it (Undo gives it back)
    final boolean usedBonus = atk.offHand;
    if (usedBonus) bonusAttackUsed = true;
    else attacksLeft = Math.max(0, attacksLeft - 1);
    final boolean usedAmmo = tc.ranged && !atk.ammo.isEmpty() && ammoCount(att, atk.ammo) > 0;
    if (usedAmmo) changeAmmo(att, atk.ammo, -1);
    persistSoon();
    refreshSide();

    int effectiveAC = target.ac + (tc.cover > 0 ? 2 : 0);
    int[] mode = {tc.advantage && !tc.disadvantage ? 1 : tc.disadvantage && !tc.advantage ? 2 : 0};
    int[][] d20 = {new int[0]};
    boolean[] replaced = {false}, applied = {false}, dmgReplaced = {false};
    int[] natural = {0}, dmgTotal = {-1};
    int[][] dmgDice = {new int[0]};

    JPanel body = new JPanel();
    body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
    body.setOpaque(false);
    Runnable[] close = new Runnable[1];
    Runnable[] render = new Runnable[1];

    JLabel head = new JLabel("<html><div style='width:430px'><b>" + App.escapeHtml(att.label) + "</b> attacks <b>" + App.escapeHtml(target.label)
            + "</b> with <b>" + App.escapeHtml(atk.name) + "</b></div></html>");
    head.setForeground(App.TEXT_LIGHT);
    head.setFont(new Font("SansSerif", Font.PLAIN, 14));
    head.setAlignmentX(Component.LEFT_ALIGNMENT);
    body.add(head);
    body.add(Box.createRigidArea(new Dimension(0, 8)));

    JPanel facts = new JPanel(new GridLayout(1, 3, 6, 0));
    facts.setOpaque(false);
    facts.setAlignmentX(Component.LEFT_ALIGNMENT);
    facts.setMaximumSize(new Dimension(460, 52));
    facts.add(statTile(tc.distance + " ft.", tc.ranged ? "RANGED" : "MELEE", () -> App.showInfoOverlay(facts, "Distance", atk.name,
            "Measured square to square (every square, diagonals too, is 5 ft.). " + atk.reachText() + ".",
            Arrays.asList(App.row("Distance", tc.distance + " ft."), App.row("Attack type", tc.ranged ? "Ranged" : "Melee")))));
    facts.add(statTile(String.valueOf(effectiveAC), "TARGET AC", () -> App.showInfoOverlay(facts, "Armor Class", target.label,
            "The attack hits if the d20 + attack bonus equals or beats this. A natural 20 always hits (Critical Hit) and a natural 1 always misses.",
            Arrays.asList(App.row("AC", String.valueOf(target.ac)), App.row("Cover", tc.cover > 0 ? "+2 (half cover)" : "none")))));
    facts.add(statTile(WeaponRules.signed(atk.toHit), "TO HIT", () -> SheetWeapons.showAttackDetail(facts, PC.equals(att.kind) ? att.source : null, atk)));
    body.add(facts);
    body.add(Box.createRigidArea(new Dimension(0, 8)));
    for (String n : tc.notes) {
        JLabel l = muted(n);
        body.add(l);
    }

    // ---- roll mode ----
    JPanel modeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
    modeRow.setOpaque(false);
    modeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
    String[] modeNames = {"Normal", "Advantage", "Disadvantage"};
    JButton[] modeButtons = new JButton[3];
    for (int i = 0; i < 3; i++) {
        final int m = i;
        modeButtons[i] = Tokens.smallButton(modeNames[i], i == 0 ? "Roll one d20" : i == 1 ? "Roll two d20s, keep the higher" : "Roll two d20s, keep the lower");
        modeButtons[i].addActionListener(e -> {
            mode[0] = m;
            rollD20s(d20, natural, mode[0]);
            replaced[0] = false;
            render[0].run();
        });
        modeRow.add(modeButtons[i]);
    }
    body.add(modeRow);

    // ---- attack roll ----
    JLabel rollLbl = new JLabel();
    rollLbl.setFont(new Font("SansSerif", Font.BOLD, 16));
    rollLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
    rollLbl.setBorder(new EmptyBorder(6, 0, 4, 0));
    body.add(rollLbl);
    JPanel rollRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    rollRow.setOpaque(false);
    rollRow.setAlignmentX(Component.LEFT_ALIGNMENT);
    JButton reroll = Tokens.smallButton("🎲 Reroll", "Roll the attack again (e.g. Lucky, or a feature that grants a reroll)");
    reroll.addActionListener(e -> {
        rollD20s(d20, natural, mode[0]);
        replaced[0] = false;
        render[0].run();
    });
    JSpinner own = new JSpinner(new SpinnerNumberModel(10, 1, 20, 1));
    own.setPreferredSize(new Dimension(58, 28));
    own.setToolTipText("Your own d20 result");
    styleSpinner(own);
    JButton useOwn = Tokens.smallButton("Use this d20", "Replace the rolled d20 with the value you rolled at the table");
    useOwn.addActionListener(e -> {
        commit(own);
        natural[0] = (Integer) own.getValue();
        d20[0] = new int[]{natural[0]};
        replaced[0] = true;
        render[0].run();
    });
    rollRow.add(reroll);
    rollRow.add(muted2("or set d20:"));
    rollRow.add(own);
    rollRow.add(useOwn);
    body.add(rollRow);
    body.add(Box.createRigidArea(new Dimension(0, 10)));

    // ---- damage ----
    JLabel dmgCap = sectionCaption("DAMAGE");
    body.add(dmgCap);
    JLabel dmgLbl = new JLabel(" ");
    dmgLbl.setFont(new Font("SansSerif", Font.BOLD, 15));
    dmgLbl.setForeground(App.TEXT_LIGHT);
    dmgLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
    body.add(dmgLbl);
    JPanel dmgRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
    dmgRow.setOpaque(false);
    dmgRow.setAlignmentX(Component.LEFT_ALIGNMENT);
    JButton rollDmg = App.overlayButton("🎲 Roll Damage", true);
    JSpinner ownDmg = new JSpinner(new SpinnerNumberModel(0, 0, 999, 1));
    ownDmg.setPreferredSize(new Dimension(64, 28));
    ownDmg.setToolTipText("Your own damage total");
    styleSpinner(ownDmg);
    JButton useOwnDmg = Tokens.smallButton("Use total", "Replace the damage with the total you rolled at the table");
    JButton apply = App.overlayButton("Apply Damage", true);
    dmgRow.add(rollDmg);
    dmgRow.add(muted2("or set:"));
    dmgRow.add(ownDmg);
    dmgRow.add(useOwnDmg);
    body.add(dmgRow);
    JPanel applyRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    applyRow.setOpaque(false);
    applyRow.setAlignmentX(Component.LEFT_ALIGNMENT);
    applyRow.add(apply);
    JButton graze = App.overlayButton("Apply Graze", false);
    graze.setToolTipText("Graze mastery: on a miss, deal damage equal to the ability modifier used for the attack");
    if ("Graze".equals(atk.mastery)) applyRow.add(graze);
    body.add(applyRow);

    JLabel masteryLbl = new JLabel();
    masteryLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
    masteryLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
    masteryLbl.setForeground(App.TEXT_LIGHT);
    masteryLbl.setBorder(new EmptyBorder(8, 0, 0, 0));
    body.add(masteryLbl);

    rollDmg.addActionListener(e -> {
        boolean crit = natural[0] == 20;
        int count = atk.diceCount * (crit ? 2 : 1);
        dmgDice[0] = WeaponRules.rollDice(rng, count, atk.diceSides, atk.greatWeaponFighting);
        int sum = 0;
        for (int v : dmgDice[0]) sum += v;
        dmgTotal[0] = Math.max(0, sum + atk.damageBonus);
        dmgReplaced[0] = false;
        render[0].run();
    });
    useOwnDmg.addActionListener(e -> {
        commit(ownDmg);
        dmgTotal[0] = (Integer) ownDmg.getValue();
        dmgReplaced[0] = true;
        render[0].run();
    });
    apply.addActionListener(e -> {
        if (dmgTotal[0] < 0 || applied[0]) return;
        target.hp = Math.max(0, target.hp - dmgTotal[0]);
        applied[0] = true;
        setStatus(att.label + " hits " + target.label + " for " + dmgTotal[0] + " " + atk.damageType.toLowerCase() + " damage"
                + (target.down() ? ". " + target.label + " is down!" : " (" + target.hp + " / " + target.maxHp + " HP)."));
        persistSoon();
        refreshAll();
        render[0].run();
    });
    graze.addActionListener(e -> {
        if (applied[0]) return;
        int g = Math.max(0, atk.abilityMod);
        target.hp = Math.max(0, target.hp - g);
        applied[0] = true;
        setStatus("Graze: " + target.label + " takes " + g + " " + atk.damageType.toLowerCase() + " damage on the miss.");
        persistSoon();
        refreshAll();
        render[0].run();
    });

    // ---- footer ----
    body.add(Box.createRigidArea(new Dimension(0, 12)));
    JPanel foot = new JPanel(new BorderLayout());
    foot.setOpaque(false);
    foot.setAlignmentX(Component.LEFT_ALIGNMENT);
    JButton undo = App.overlayButton("Undo Attack", false);
    undo.setToolTipText("Take the attack back: no damage, and the attack" + (usedAmmo ? " and the " + atk.ammo.toLowerCase() : "") + " are refunded");
    undo.addActionListener(e -> {
        if (applied[0]) return;
        if (usedBonus) bonusAttackUsed = false;
        else attacksLeft++;
        if (usedAmmo) changeAmmo(att, atk.ammo, 1);
        close[0].run();
        setStatus("Attack undone.");
        persistSoon();
        refreshAll();
    });
    JButton done = App.overlayButton("Done", true);
    done.addActionListener(e -> {
        close[0].run();
        if (!applied[0]) setStatus(att.label + "'s attack on " + target.label + ": " + (natural[0] + atk.toHit >= effectiveAC && natural[0] != 1 || natural[0] == 20 ? "hit, no damage applied." : "miss."));
        refreshAll();
    });
    foot.add(undo, BorderLayout.WEST);
    foot.add(done, BorderLayout.EAST);
    body.add(foot);

    render[0] = () -> {
        for (int i = 0; i < 3; i++) {
            boolean on = i == mode[0];
            modeButtons[i].setBackground(on ? App.GOLD : App.SHEET_ROW_ALT);
            modeButtons[i].setForeground(on ? App.SHEET_BG : App.TEXT_LIGHT);
        }
        boolean crit = natural[0] == 20, fumble = natural[0] == 1;
        int total = natural[0] + atk.toHit;
        boolean hit = crit || (!fumble && total >= effectiveAC);
        StringBuilder dice = new StringBuilder();
        for (int v : d20[0]) {
            if (dice.length() > 0) dice.append(" ");
            dice.append(v == natural[0] ? "[" + v + "]" : "(" + v + ")");
        }
        String verdict = crit ? "CRITICAL HIT!" : fumble ? "MISS (natural 1)" : hit ? "HIT" : "MISS";
        rollLbl.setText("<html>d20 " + dice + (replaced[0] ? " <span style='color:#aaa5a0'>(your roll)</span>" : "")
                + " " + WeaponRules.signed(atk.toHit) + " = <b>" + total + "</b> vs AC " + effectiveAC + "  →  "
                + "<span style='color:" + (hit ? "#7cd38a" : "#dc3c3c") + "'>" + verdict + "</span></html>");
        rollLbl.setForeground(App.TEXT_LIGHT);
        boolean canDamage = hit && !applied[0];
        rollDmg.setEnabled(canDamage);
        useOwnDmg.setEnabled(canDamage);
        ownDmg.setEnabled(canDamage);
        apply.setEnabled(canDamage && dmgTotal[0] >= 0);
        graze.setEnabled(!hit && !applied[0] && atk.abilityMod > 0);
        graze.setText("Apply Graze (" + Math.max(0, atk.abilityMod) + ")");
        reroll.setEnabled(!applied[0]);
        useOwn.setEnabled(!applied[0]);
        undo.setEnabled(!applied[0]);
        String dmgDiceText = WeaponData.diceText(atk.diceCount * (crit ? 2 : 1), atk.diceSides, atk.damageBonus);
        dmgCap.setText("DAMAGE: " + dmgDiceText + " " + atk.damageType.toUpperCase() + (crit ? " (dice doubled: Critical Hit)" : "")
                + (atk.greatWeaponFighting ? " · GWF: 1s and 2s count as 3" : ""));
        if (applied[0]) {
            dmgLbl.setText("Applied. " + target.label + " now has " + target.hp + " / " + target.maxHp + " HP" + (target.down() ? " (down)." : "."));
        } else if (!hit) {
            dmgLbl.setText("No damage on a miss" + ("Graze".equals(atk.mastery) && atk.abilityMod > 0 ? " (Graze still deals " + atk.abilityMod + ")." : "."));
        } else if (dmgTotal[0] < 0) {
            dmgLbl.setText("Roll damage, or set your own total.");
        } else if (dmgReplaced[0]) {
            dmgLbl.setText(dmgTotal[0] + " " + atk.damageType.toLowerCase() + " damage (your roll)");
        } else {
            StringBuilder sb = new StringBuilder();
            for (int v : dmgDice[0]) sb.append(sb.length() == 0 ? "" : " + ").append(v);
            dmgLbl.setText((dmgDice[0].length > 0 ? "[" + sb + "]" : "") + (atk.damageBonus != 0 || dmgDice[0].length == 0 ? " " + (atk.damageBonus >= 0 && dmgDice[0].length > 0 ? "+ " : "") + atk.damageBonus : "")
                    + " = " + dmgTotal[0] + " " + atk.damageType.toLowerCase() + " damage");
        }
        if (!atk.mastery.isEmpty()) {
            boolean onMiss = "Graze".equals(atk.mastery);
            boolean relevant = onMiss ? !hit : hit || "Nick".equals(atk.mastery);
            masteryLbl.setText("<html><div style='width:430px'><b style='color:#d4af37'>Mastery: " + atk.mastery + "</b>"
                    + (relevant ? "" : " <span style='color:#aaa5a0'>(only " + (onMiss ? "on a miss" : "on a hit") + ")</span>")
                    + "<br>" + App.escapeHtml(WeaponRules.MASTERY_INFO.getOrDefault(atk.mastery, "")) + "</div></html>");
            masteryLbl.setForeground(relevant ? App.TEXT_LIGHT : App.TEXT_MUTED);
        }
        body.revalidate();
        body.repaint();
    };
    rollD20s(d20, natural, mode[0]);
    render[0].run();
    close[0] = App.showSheetOverlay(anchor, "Attack Roll", body, null);
}

private void rollD20s(int[][] d20, int[] natural, int mode) {
    if (mode == 0) {
        d20[0] = new int[]{rollD20()};
        natural[0] = d20[0][0];
    } else {
        int a = rollD20(), b = rollD20();
        d20[0] = new int[]{a, b};
        natural[0] = mode == 1 ? Math.max(a, b) : Math.min(a, b);
    }
}

private static JLabel muted2(String text) {
    JLabel l = new JLabel(text);
    l.setForeground(App.TEXT_MUTED);
    l.setFont(new Font("SansSerif", Font.PLAIN, 12));
    return l;
}

// ===============================================================
// Detail overlays
// ===============================================================
private void showTokenDetail(Component anchor, Tok t) {
    JPanel body = new JPanel();
    body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
    body.setOpaque(false);
    Runnable[] close = new Runnable[1];

    JPanel head = new JPanel(new BorderLayout(12, 0));
    head.setOpaque(false);
    head.setAlignmentX(Component.LEFT_ALIGNMENT);
    Tokens.Preview pv = new Tokens.Preview(t.style, t.label, 72, teamColor(t.team));
    pv.dimmed = t.down();
    App.clickable(pv, () -> App.showInfoOverlay(pv, "Token", t.sourceName, Tokens.INFO
            + (PC.equals(t.kind) ? "\n\nChange it on the character sheet (click the token next to the name)." : "\n\nChange it in the NPC's editor (Assets → NPCs)."), null));
    head.add(pv, BorderLayout.WEST);
    JPanel headText = new JPanel();
    headText.setLayout(new BoxLayout(headText, BoxLayout.Y_AXIS));
    headText.setOpaque(false);
    JLabel sub = new JLabel(t.subtitle.toUpperCase());
    sub.setForeground(App.TEXT_MUTED);
    sub.setFont(new Font("SansSerif", Font.BOLD, 11));
    headText.add(sub);
    headText.add(Box.createRigidArea(new Dimension(0, 4)));
    JLabel teamLbl = new JLabel("● " + teamName(t.team) + (t.team >= 0 ? " (" + teamColorName(t.team) + ")" : ""));
    teamLbl.setForeground(teamColor(t.team));
    teamLbl.setFont(new Font("SansSerif", Font.BOLD, 13));
    headText.add(teamLbl);
    headText.add(Box.createRigidArea(new Dimension(0, 4)));
    JLabel where = new JLabel("Square " + colName(t.x) + (t.y + 1) + (t.size > 1 ? " (top-left)" : "") + (t.down() ? "  •  DOWN (0 HP)" : ""));
    where.setForeground(t.down() ? App.ACCENT_HOVER : App.TEXT_MUTED);
    where.setFont(new Font("SansSerif", Font.PLAIN, 12));
    headText.add(where);
    head.add(headText, BorderLayout.CENTER);
    body.add(head);
    body.add(Box.createRigidArea(new Dimension(0, 10)));

    // label
    JPanel labelRow = new JPanel(new BorderLayout(8, 0));
    labelRow.setOpaque(false);
    labelRow.setAlignmentX(Component.LEFT_ALIGNMENT);
    JLabel ll = new JLabel("Label on map");
    ll.setForeground(App.TEXT_MUTED);
    ll.setFont(new Font("SansSerif", Font.PLAIN, 13));
    labelRow.add(ll, BorderLayout.WEST);
    JTextField labelField = App.overlayField(t.label, 18);
    labelField.setEditable(NPC.equals(t.kind));
    labelField.setToolTipText(NPC.equals(t.kind) ? "Rename this token (e.g. Goblin Boss)" : "Characters always show their name");
    labelField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
        void upd() {
            String s = labelField.getText().trim();
            if (s.isEmpty() || s.equals(t.label)) return;
            t.label = s;
            board.repaint();
            persistSoon();
            refreshSide();
        }
        public void insertUpdate(javax.swing.event.DocumentEvent e) { upd(); }
        public void removeUpdate(javax.swing.event.DocumentEvent e) { upd(); }
        public void changedUpdate(javax.swing.event.DocumentEvent e) { upd(); }
    });
    labelRow.add(labelField, BorderLayout.CENTER);
    body.add(labelRow);
    body.add(Box.createRigidArea(new Dimension(0, 10)));

    // stats (each opens its own explanation)
    JPanel stats = new JPanel(new GridLayout(1, 4, 6, 0));
    stats.setOpaque(false);
    stats.setAlignmentX(Component.LEFT_ALIGNMENT);
    stats.add(statTile(String.valueOf(t.ac), "AC", () -> App.showInfoOverlay(stats, "Armor Class", t.label,
            "Attacks hit this creature when the attack roll equals or beats its Armor Class.",
            Arrays.asList(App.row("AC", String.valueOf(t.ac)), App.row("From", PC.equals(t.kind) ? "Character sheet (armor + DEX)" : "NPC stat block")))));
    stats.add(statTile(signedInt(t.initBonus), "INIT", () -> App.showInfoOverlay(stats, "Initiative", t.label,
            "At the start of combat everyone rolls d20 + this bonus (the DEX modifier). Higher results act first.",
            Arrays.asList(App.row("Bonus", signedInt(t.initBonus)), App.row("Rolled this combat", inCombat ? String.valueOf(t.init) : "—")))));
    stats.add(statTile(t.speed + " ft.", "SPEED", () -> App.showInfoOverlay(stats, "Speed", t.label,
            "How far this creature can move on its turn. Each square is 5 ft. (diagonals too); Water costs double. "
                    + "Walls, closed doors and windows block movement, and enemies' spaces can't be crossed.",
            Arrays.asList(App.row("Speed", t.speed + " ft. (" + t.speed / 5 + " squares)"),
                    App.row("Left this turn", inCombat && t == active ? moveLeft + " ft." : "—")))));
    stats.add(statTile(t.sizeName, "SIZE", () -> App.showInfoOverlay(stats, "Size: " + t.sizeName, t.label,
            "Size sets how many squares a creature's token covers. Tiny, Small and Medium creatures take 1 square, "
                    + "Large 2 × 2, Huge 3 × 3 and Gargantuan 4 × 4.",
            Collections.singletonList(App.row("Footprint", footprintText(t.sizeName))))));
    body.add(stats);
    body.add(Box.createRigidArea(new Dimension(0, 10)));

    // attacks (each opens its breakdown)
    if (!t.attacks.isEmpty()) {
        body.add(sectionCaption("ATTACKS" + (t.attacksPerAction > 1 ? " (" + t.attacksPerAction + " PER ATTACK ACTION)" : "")));
        for (WeaponRules.Attack a : t.attacks) {
            JLabel al = new JLabel("<html><b>" + App.escapeHtml(a.name) + "</b>  " + WeaponRules.signed(a.toHit) + " to hit · "
                    + App.escapeHtml(a.damageText()) + " · <span style='color:#aaa5a0'>" + App.escapeHtml(a.reachText())
                    + (a.mastery.isEmpty() ? "" : " · " + a.mastery) + "</span></html>");
            al.setForeground(App.TEXT_LIGHT);
            al.setFont(new Font("SansSerif", Font.PLAIN, 12));
            al.setBorder(new EmptyBorder(2, 4, 2, 4));
            al.setAlignmentX(Component.LEFT_ALIGNMENT);
            App.clickable(al, () -> SheetWeapons.showAttackDetail(al, PC.equals(t.kind) ? t.source : null, a));
            body.add(al);
        }
        body.add(Box.createRigidArea(new Dimension(0, 10)));
    }

    // HP
    JLabel hpCap = sectionCaption("HIT POINTS");
    body.add(hpCap);
    Bar hpBar = new Bar();
    hpBar.setAlignmentX(Component.LEFT_ALIGNMENT);
    Runnable hpRefresh = () -> {
        hpBar.set(t.maxHp <= 0 ? 0 : t.hp / (double) t.maxHp, t.hp + " / " + t.maxHp + " HP", hpColor(t));
        pv.dimmed = t.down();
        pv.repaint();
        where.setText("Square " + colName(t.x) + (t.y + 1) + (t.size > 1 ? " (top-left)" : "") + (t.down() ? "  •  DOWN (0 HP)" : ""));
        where.setForeground(t.down() ? App.ACCENT_HOVER : App.TEXT_MUTED);
    };
    hpRefresh.run();
    App.clickable(hpBar, () -> App.showInfoOverlay(hpBar, "Hit Points", t.label,
            "Damage lowers hit points; at 0 the creature is down (Unconscious, or dead for most monsters). Healing can't go above the maximum. "
                    + "HP changes here are tracked for this map's encounter only.",
            Arrays.asList(App.row("Current", String.valueOf(t.hp)), App.row("Maximum", t.maxHp + (PC.equals(t.kind) ? " (character sheet)" : " (NPC stat block)")))));
    body.add(hpBar);
    body.add(Box.createRigidArea(new Dimension(0, 6)));
    body.add(hpControls(t, () -> {
        hpRefresh.run();
        refreshSide();
        board.repaint();
        persistSoon();
    }));
    body.add(Box.createRigidArea(new Dimension(0, 12)));

    // team / initiative
    JPanel teamRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    teamRow.setOpaque(false);
    teamRow.setAlignmentX(Component.LEFT_ALIGNMENT);
    JLabel tl = new JLabel("Team");
    tl.setForeground(App.TEXT_MUTED);
    tl.setFont(new Font("SansSerif", Font.PLAIN, 13));
    teamRow.add(tl);
    JComboBox<Integer> tc = teamComboWithNone(t.team);
    tc.addActionListener(e -> {
        Object sel = tc.getSelectedItem();
        t.team = sel instanceof Integer ? (Integer) sel : -1;
        pv.outline = teamColor(t.team);
        pv.repaint();
        teamLbl.setText("● " + teamName(t.team) + (t.team >= 0 ? " (" + teamColorName(t.team) + ")" : ""));
        teamLbl.setForeground(teamColor(t.team));
        if (moveMode) computeReach();
        board.repaint();
        refreshSide();
        persistSoon();
    });
    teamRow.add(tc);
    if (inCombat) {
        JLabel il = new JLabel("   Initiative");
        il.setForeground(App.TEXT_MUTED);
        il.setFont(new Font("SansSerif", Font.PLAIN, 13));
        teamRow.add(il);
        JSpinner is = new JSpinner(new SpinnerNumberModel(t.init, -20, 60, 1));
        is.setPreferredSize(new Dimension(60, 28));
        styleSpinner(is);
        is.addChangeListener(e -> {
            t.init = (Integer) is.getValue();
            sortOrder();
            refreshAll();
            persistSoon();
        });
        teamRow.add(is);
    }
    body.add(teamRow);
    body.add(Box.createRigidArea(new Dimension(0, 14)));

    // actions
    JPanel acts = new JPanel(new BorderLayout());
    acts.setOpaque(false);
    acts.setAlignmentX(Component.LEFT_ALIGNMENT);
    JButton remove = App.overlayButton("Remove from Map", false);
    remove.setForeground(App.ACCENT_HOVER);
    remove.addActionListener(e -> App.showConfirmOverlay(remove, "Remove Token",
            "Remove " + t.label + " from the map" + (inCombat ? " and the initiative order" : "") + "? The "
                    + (PC.equals(t.kind) ? "character" : "NPC") + " itself isn't deleted.", "Remove", () -> {
                close[0].run();
                removeToken(t);
            }));
    acts.add(remove, BorderLayout.WEST);
    JPanel rb = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    rb.setOpaque(false);
    JButton full = App.overlayButton(PC.equals(t.kind) ? "Character Summary" : "Full Stat Block", false);
    full.addActionListener(e -> showSourceDetail(full, t));
    rb.add(full);
    if (inCombat && t == active && !t.down() && moveLeft > 0) {
        JButton mv = App.overlayButton("Move", true);
        mv.addActionListener(e -> {
            close[0].run();
            setMoveMode(true);
        });
        rb.add(mv);
    }
    acts.add(rb, BorderLayout.EAST);
    body.add(acts);

    close[0] = App.showSheetOverlay(anchor, t.label, body, null);
}

// Amount + Damage / Heal row shared by the token overlay and the Current Turn tab
private JPanel hpControls(Tok t, Runnable after) {
    JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    row.setOpaque(false);
    row.setAlignmentX(Component.LEFT_ALIGNMENT);
    JSpinner amt = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
    amt.setPreferredSize(new Dimension(64, 28));
    amt.setToolTipText("Amount of damage or healing");
    styleSpinner(amt);
    JButton dmg = App.overlayButton("Damage", false);
    dmg.setForeground(App.ACCENT_HOVER);
    JButton heal = App.overlayButton("Heal", false);
    heal.setForeground(new Color(0x7C, 0xD3, 0x8A));
    dmg.addActionListener(e -> {
        commit(amt);
        t.hp = Math.max(0, t.hp - (Integer) amt.getValue());
        setStatus(t.label + " takes " + amt.getValue() + " damage" + (t.down() ? " and is down!" : " (" + t.hp + " / " + t.maxHp + " HP)."));
        after.run();
    });
    heal.addActionListener(e -> {
        commit(amt);
        t.hp = Math.min(t.maxHp, t.hp + (Integer) amt.getValue());
        setStatus(t.label + " heals to " + t.hp + " / " + t.maxHp + " HP.");
        after.run();
    });
    row.add(amt);
    row.add(dmg);
    row.add(heal);
    return row;
}

private static void commit(JSpinner sp) {
    try {
        sp.commitEdit();
    } catch (java.text.ParseException ignored) {
        // keep the last valid value
    }
}

// The NPC's full stat block, or a summary of the character
private void showSourceDetail(Component anchor, Tok t) {
    if (NPC.equals(t.kind)) {
        Map<String, Object> a = CustomAssets.find(CustomAssets.NPCS, t.refId);
        if (a != null) AssetLibraryPanel.showDetail(anchor, CustomAssets.NPCS, a, null);
        return;
    }
    Map<?, ?> rec = t.source;
    if (rec == null) return;
    List<String[]> rows = new ArrayList<>();
    Map<String, Integer> classLevels = CharacterStorage.recClassLevels(rec);
    List<String> cls = new ArrayList<>();
    for (Map.Entry<String, Integer> en : classLevels.entrySet()) cls.add(en.getKey() + " " + en.getValue());
    rows.add(App.row("Class", cls.isEmpty() ? "—" : String.join(" / ", cls)));
    String species = CharacterStorage.recStr(rec, "species");
    rows.add(App.row("Species", species.isEmpty() ? "—" : species));
    String bg = CharacterStorage.recStr(rec, "background");
    rows.add(App.row("Background", bg.isEmpty() ? "—" : bg));
    rows.add(App.row("Armor Class", String.valueOf(t.ac)));
    rows.add(App.row("Max HP", String.valueOf(t.maxHp)));
    rows.add(App.row("Speed", t.speed + " ft."));
    Map<String, Integer> stats = CharacterStorage.recStatsMap(rec);
    StringBuilder sb = new StringBuilder();
    for (String s : GameData.STAT_ORDER) {
        int v = stats.getOrDefault(s, 10);
        sb.append(s).append(' ').append(v).append(" (").append(signedInt(GameData.abilityModifier(v))).append(")   ");
    }
    App.showInfoOverlay(anchor, t.sourceName, "Character",
            sb.toString().trim() + "\n\nOpen Characters on the main menu for the full sheet.", rows);
}

private void showMapInfo() {
    if (asset == null) return;
    int doors = 0;
    for (int[][] grid : new int[][][]{model.hEdges, model.vEdges}) {
        for (int[] row : grid) for (int v : row) if (v == MapEditorPanel.DOOR_CLOSED || v == MapEditorPanel.DOOR_OPEN) doors++;
    }
    List<String[]> rows = new ArrayList<>();
    rows.add(App.row("Size", model.width + " × " + model.height + " squares"));
    rows.add(App.row("Area", model.width * 5 + " ft. × " + model.height * 5 + " ft."));
    rows.add(App.row("Doors", String.valueOf(doors)));
    rows.add(App.row("Pins", String.valueOf(model.pins.size())));
    rows.add(App.row("Tokens", String.valueOf(tokens.size())));
    rows.add(App.row("Combat", inCombat ? "Round " + round : "Not started"));
    String notes = CustomAssets.str(asset, "notes");
    App.showInfoOverlay(board, CustomAssets.str(asset, "name"), "Map", notes.isEmpty() ? "No notes." : notes, rows);
}

// ===============================================================
// Side panel
// ===============================================================
private void setTab(String t) {
    tab = t;
    refreshSide();
    SwingUtilities.invokeLater(() -> sideScroll.getVerticalScrollBar().setValue(0));
}

private void refreshSide() {
    for (Map.Entry<String, JButton> en : tabButtons.entrySet()) {
        boolean on = en.getKey().equals(tab);
        JButton b = en.getValue();
        b.setBackground(on ? App.GOLD : App.SHEET_ROW_ALT);
        b.setForeground(on ? App.SHEET_BG : App.TEXT_LIGHT);
        b.setBorder(BorderFactory.createCompoundBorder(new LineBorder(on ? App.GOLD : App.SHEET_BORDER, 1, true), new EmptyBorder(7, 6, 7, 6)));
    }
    sideContent.removeAll();
    if (inCombat) {
        switch (tab) {
            case TAB_ORDER: buildOrderTab(); break;
            case TAB_TEAMS: buildTeamsTab(); break;
            case TAB_SETTINGS: buildSettingsTab(); break;
            default: buildTurnTab();
        }
    }
    sideContent.revalidate();
    sideContent.repaint();
}

private void addSide(JComponent c) {
    c.setAlignmentX(Component.LEFT_ALIGNMENT);
    sideContent.add(c);
}

private void gap(int h) {
    sideContent.add(Box.createRigidArea(new Dimension(0, h)));
}

private void buildTurnTab() {
    if (active == null) {
        addSide(muted("No one is in the initiative order."));
        return;
    }
    Tok t = active;
    int pos = order.indexOf(t) + 1;
    addSide(sectionCaption("ROUND " + round + "  •  TURN " + pos + " OF " + order.size()));
    gap(4);

    JPanel head = new JPanel(new BorderLayout(12, 0));
    head.setOpaque(false);
    Tokens.Preview pv = new Tokens.Preview(t.style, t.label, 84, teamColor(t.team));
    pv.dimmed = t.down();
    pv.setToolTipText("Details");
    App.clickable(pv, () -> showTokenDetail(pv, t));
    head.add(pv, BorderLayout.WEST);
    JPanel col = new JPanel();
    col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
    col.setOpaque(false);
    JLabel name = new JLabel("<html>" + App.escapeHtml(t.label) + "</html>");
    name.setForeground(App.GOLD);
    name.setFont(new Font("Serif", Font.BOLD, 22));
    App.clickable(name, () -> showTokenDetail(name, t));
    col.add(name);
    col.add(Box.createRigidArea(new Dimension(0, 4)));
    JLabel badge = teamBadge(t.team);
    App.clickable(badge, () -> setTab(TAB_TEAMS));
    badge.setToolTipText("Teams");
    col.add(badge);
    col.add(Box.createRigidArea(new Dimension(0, 4)));
    JLabel ini = new JLabel("Initiative " + t.init);
    ini.setForeground(App.TEXT_MUTED);
    ini.setFont(new Font("SansSerif", Font.PLAIN, 12));
    App.clickable(ini, () -> setTab(TAB_ORDER));
    ini.setToolTipText("Initiative order");
    col.add(ini);
    for (Component c : col.getComponents()) ((JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
    head.add(col, BorderLayout.CENTER);
    addSide(head);
    gap(12);

    addSide(sectionCaption("HIT POINTS"));
    Bar hp = new Bar();
    hp.set(t.maxHp <= 0 ? 0 : t.hp / (double) t.maxHp, t.hp + " / " + t.maxHp + " HP", hpColor(t));
    App.clickable(hp, () -> showTokenDetail(hp, t));
    hp.setToolTipText("Details");
    addSide(hp);
    gap(6);
    addSide(hpControls(t, () -> {
        refreshSide();
        board.repaint();
        persistSoon();
    }));
    if (t.down()) {
        gap(4);
        JLabel down = new JLabel("At 0 HP: " + t.label + " is down.");
        down.setForeground(App.ACCENT_HOVER);
        down.setFont(new Font("SansSerif", Font.BOLD, 12));
        addSide(down);
    }
    gap(12);

    addSide(sectionCaption("MOVEMENT"));
    Bar mv = new Bar();
    mv.set(t.speed <= 0 ? 0 : moveLeft / (double) t.speed, moveLeft + " / " + t.speed + " ft. left", new Color(0x4F, 0x9D, 0xE8));
    App.clickable(mv, () -> App.showInfoOverlay(mv, "Movement", t.label,
            "Speed " + t.speed + " ft. per turn. Each square costs 5 ft. (diagonals too) and Water costs 10 ft. "
                    + "Walls, closed doors and windows block movement (click a door on the map to open it). "
                    + "You can pass through allies' spaces but not enemies', and can't end your move in another creature's space.",
            Arrays.asList(App.row("Speed", t.speed + " ft."), App.row("Left", moveLeft + " ft."), App.row("Size", t.sizeName + " · " + footprintText(t.sizeName)))));
    addSide(mv);
    gap(14);

    JPanel actions = new JPanel(new GridLayout(1, 2, 8, 0));
    actions.setOpaque(false);
    JButton move = App.overlayButton(moveMode ? "Cancel Move" : "Move", !moveMode);
    move.setEnabled(moveMode || (moveLeft > 0 && !t.down()));
    move.setToolTipText(moveLeft <= 0 ? "No movement left this turn" : t.down() ? "Down creatures can't move" : "Highlight the squares " + t.label + " can reach");
    move.addActionListener(e -> {
        setMoveMode(!moveMode);
        if (!moveMode) setStatus(defaultStatus());
        refreshSide();
    });
    boolean canBonus = canBonusAttack(t);
    JButton attack = App.overlayButton(targeting != null ? "Cancel Attack" : "Attack", targeting == null);
    attack.setEnabled(targeting != null || (!t.down() && (attacksLeft > 0 || canBonus)));
    attack.setToolTipText(t.down() ? "Down creatures can't attack" : attacksLeft <= 0 && !canBonus ? "No attacks left this turn"
            : "Pick a weapon, then click a target in range");
    attack.addActionListener(e -> {
        if (targeting != null) cancelModes();
        else showAttackPicker(attack, t);
        refreshSide();
    });
    actions.add(move);
    actions.add(attack);
    actions.setMaximumSize(new Dimension(400, 36));
    addSide(actions);
    gap(6);
    JLabel budget = new JLabel("Attacks left: " + attacksLeft + " of " + t.attacksPerAction
            + (hasOffHandAttack(t) ? "   •   Bonus attack: " + (bonusAttackUsed ? "used" : canBonus ? "ready" : "after a Light weapon attack") : ""));
    budget.setForeground(App.TEXT_MUTED);
    budget.setFont(new Font("SansSerif", Font.PLAIN, 12));
    App.clickable(budget, () -> App.showInfoOverlay(budget, "Attacks", t.label,
            "Taking the Attack action lets you make " + t.attacksPerAction + (t.attacksPerAction == 1 ? " attack" : " attacks")
                    + (t.attacksPerAction > 1 ? (PC.equals(t.kind) ? " (Extra Attack)" : " (Multiattack)") : "") + " this turn. "
                    + "Holding a Light weapon in each hand gives one extra Bonus Action attack after you attack with a Light weapon "
                    + "(no ability modifier on its damage unless you have Two-Weapon Fighting).", null));
    addSide(budget);
    gap(8);
    JButton end = App.overlayButton("Skip / End Turn", false);
    end.setToolTipText("Pass to the next combatant in initiative order");
    end.addActionListener(e -> endTurn());
    end.setMaximumSize(new Dimension(400, 34));
    addSide(end);
    gap(14);

    Tok next = peekNext();
    if (next != null && next != t) {
        JPanel nr = new JPanel(new BorderLayout(8, 0));
        nr.setOpaque(false);
        nr.add(new Tokens.Preview(next.style, next.label, 26, teamColor(next.team)), BorderLayout.WEST);
        JLabel nl = new JLabel("Next up: " + next.label);
        nl.setForeground(App.TEXT_LIGHT);
        nl.setFont(new Font("SansSerif", Font.PLAIN, 13));
        nr.add(nl, BorderLayout.CENTER);
        App.clickable(nr, () -> showTokenDetail(nr, next));
        nr.setMaximumSize(new Dimension(400, 34));
        addSide(nr);
    }
}

private Tok peekNext() {
    if (order.isEmpty() || active == null) return null;
    int i = order.indexOf(active);
    for (int step = 0; step < order.size(); step++) {
        i = (i + 1) % order.size();
        Tok c = order.get(i);
        if (!skipDefeated || !c.down()) return c;
    }
    return null;
}

private void buildOrderTab() {
    JPanel head = new JPanel(new BorderLayout());
    head.setOpaque(false);
    JLabel r = new JLabel("Round " + round);
    r.setForeground(App.GOLD);
    r.setFont(new Font("Serif", Font.BOLD, 22));
    App.clickable(r, () -> App.showInfoOverlay(r, "Round " + round, "Combat round",
            "A round is about 6 seconds: everyone takes one turn in initiative order. When the last combatant ends their turn, a new round starts.", null));
    head.add(r, BorderLayout.WEST);
    JButton resort = Tokens.smallButton("Re-sort", "Sort by initiative (highest first)");
    resort.addActionListener(e -> {
        sortOrder();
        persistSoon();
        refreshAll();
    });
    head.add(resort, BorderLayout.EAST);
    head.setMaximumSize(new Dimension(400, 40));
    addSide(head);
    gap(4);
    addSide(muted("Click a number to change initiative, a name for details, or use ▲ ▼ to reorder."));
    gap(8);

    for (int i = 0; i < order.size(); i++) {
        Tok t = order.get(i);
        final int idx = i;
        boolean isActive = t == active;
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBackground(isActive ? App.SHEET_ROW_ALT : App.SHEET_CARD);
        row.setOpaque(true);
        row.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(isActive ? App.GOLD : App.SHEET_BORDER, isActive ? 2 : 1, true),
                BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 6, 0, 0, teamColor(t.team)), new EmptyBorder(4, 8, 4, 4))));
        row.setMaximumSize(new Dimension(400, 46));

        JLabel init = new JLabel(String.valueOf(t.init), SwingConstants.CENTER);
        init.setForeground(App.GOLD);
        init.setFont(new Font("SansSerif", Font.BOLD, 16));
        init.setPreferredSize(new Dimension(34, 30));
        init.setToolTipText("Change initiative");
        App.clickable(init, () -> showInitEditor(init, t));
        row.add(init, BorderLayout.WEST);

        JPanel mid = new JPanel(new BorderLayout(6, 0));
        mid.setOpaque(false);
        mid.add(new Tokens.Preview(t.style, t.label, 26, teamColor(t.team)), BorderLayout.WEST);
        JLabel nm = new JLabel("<html><b>" + App.escapeHtml(t.label) + "</b>" + (isActive ? " <span style='color:#d4af37'>◀ turn</span>" : "")
                + "<br><span style='color:" + (t.down() ? "#dc3c3c" : "#aaa5a0") + "'>HP " + t.hp + " / " + t.maxHp
                + (t.down() ? " · down" : "") + "</span></html>");
        nm.setForeground(t.down() ? App.TEXT_MUTED : App.TEXT_LIGHT);
        nm.setFont(new Font("SansSerif", Font.PLAIN, 12));
        mid.add(nm, BorderLayout.CENTER);
        App.clickable(mid, () -> showTokenDetail(mid, t));
        row.add(mid, BorderLayout.CENTER);

        JPanel arrows = new JPanel(new GridLayout(2, 1, 0, 2));
        arrows.setOpaque(false);
        JButton up = arrowButton("▲", "Move up");
        up.setEnabled(idx > 0);
        up.addActionListener(e -> swapOrder(idx, idx - 1));
        JButton down = arrowButton("▼", "Move down");
        down.setEnabled(idx < order.size() - 1);
        down.addActionListener(e -> swapOrder(idx, idx + 1));
        arrows.add(up);
        arrows.add(down);
        row.add(arrows, BorderLayout.EAST);
        addSide(row);
        gap(5);
    }
}

private void swapOrder(int a, int b) {
    if (a < 0 || b < 0 || a >= order.size() || b >= order.size()) return;
    Collections.swap(order, a, b);
    persistSoon();
    refreshAll();
}

private void showInitEditor(Component anchor, Tok t) {
    JPanel body = new JPanel(new BorderLayout(0, 12));
    body.setOpaque(false);
    JLabel l = new JLabel("<html><div style='width:280px'>Initiative for <b>" + App.escapeHtml(t.label) + "</b> (bonus "
            + signedInt(t.initBonus) + "). The order re-sorts when you save.</div></html>");
    l.setForeground(App.TEXT_LIGHT);
    l.setFont(new Font("SansSerif", Font.PLAIN, 13));
    body.add(l, BorderLayout.NORTH);
    JPanel mid = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    mid.setOpaque(false);
    JSpinner sp = new JSpinner(new SpinnerNumberModel(t.init, -20, 60, 1));
    sp.setPreferredSize(new Dimension(70, 30));
    styleSpinner(sp);
    JButton roll = Tokens.smallButton("🎲 Roll", "Roll d20 + " + t.initBonus);
    roll.addActionListener(e -> sp.setValue(rollD20() + t.initBonus));
    mid.add(sp);
    mid.add(roll);
    body.add(mid, BorderLayout.CENTER);
    Runnable[] close = new Runnable[1];
    JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    btns.setOpaque(false);
    JButton cancel = App.overlayButton("Cancel", false);
    cancel.addActionListener(e -> close[0].run());
    JButton save = App.overlayButton("Save", true);
    save.addActionListener(e -> {
        commit(sp);
        t.init = (Integer) sp.getValue();
        sortOrder();
        close[0].run();
        persistSoon();
        refreshAll();
    });
    btns.add(cancel);
    btns.add(save);
    body.add(btns, BorderLayout.SOUTH);
    close[0] = App.showSheetOverlay(anchor, "Initiative", body, sp);
}

private void buildTeamsTab() {
    addSide(muted("Rename a team by typing in its box. Use a member's team box to move them; pick an unused color to start a new team."));
    gap(10);
    Map<Integer, List<Tok>> byTeam = new TreeMap<>();
    for (Tok t : order) byTeam.computeIfAbsent(t.team, k -> new ArrayList<>()).add(t);
    for (Map.Entry<Integer, List<Tok>> en : byTeam.entrySet()) {
        int team = en.getKey();
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(App.SHEET_BG);
        card.setBorder(BorderFactory.createCompoundBorder(new LineBorder(teamColor(team), 2, true), new EmptyBorder(8, 10, 8, 10)));
        card.setMaximumSize(new Dimension(400, 1000));

        JPanel head = new JPanel(new BorderLayout(8, 0));
        head.setOpaque(false);
        JLabel sw = swatch(teamColor(team), 18, team >= 0 ? TEAM_COLOR_NAMES[team] : "No team");
        App.clickable(sw, () -> showTeamInfo(sw, team));
        head.add(sw, BorderLayout.WEST);
        if (team >= 0) {
            JTextField nf = App.overlayField(teamNames[team], 14);
            nf.setToolTipText("Team name (blank = Team " + (team + 1) + ")");
            nf.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                void upd() {
                    teamNames[team] = nf.getText().trim();
                    board.repaint();
                    persistSoon();
                }
                public void insertUpdate(javax.swing.event.DocumentEvent e) { upd(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { upd(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { upd(); }
            });
            head.add(nf, BorderLayout.CENTER);
        } else {
            JLabel nl = new JLabel("No team");
            nl.setForeground(App.TEXT_LIGHT);
            nl.setFont(new Font("SansSerif", Font.BOLD, 13));
            head.add(nl, BorderLayout.CENTER);
        }
        JLabel count = new JLabel(en.getValue().size() + (en.getValue().size() == 1 ? " member" : " members"));
        count.setForeground(App.TEXT_MUTED);
        count.setFont(new Font("SansSerif", Font.PLAIN, 11));
        head.add(count, BorderLayout.EAST);
        head.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(head);
        card.add(Box.createRigidArea(new Dimension(0, 6)));

        for (Tok t : en.getValue()) {
            JPanel row = new JPanel(new BorderLayout(6, 0));
            row.setOpaque(false);
            row.setBorder(new EmptyBorder(2, 0, 2, 0));
            JPanel who = new JPanel(new BorderLayout(6, 0));
            who.setOpaque(false);
            who.add(new Tokens.Preview(t.style, t.label, 24, teamColor(t.team)), BorderLayout.WEST);
            JLabel nm = new JLabel(t.label + (t.down() ? " (down)" : ""));
            nm.setForeground(t.down() ? App.TEXT_MUTED : App.TEXT_LIGHT);
            nm.setFont(new Font("SansSerif", Font.PLAIN, 12));
            who.add(nm, BorderLayout.CENTER);
            App.clickable(who, () -> showTokenDetail(who, t));
            row.add(who, BorderLayout.CENTER);
            JComboBox<Integer> tc = teamComboWithNone(t.team);
            tc.setPreferredSize(new Dimension(118, 26));
            tc.addActionListener(e -> {
                Object sel = tc.getSelectedItem();
                int nt = sel instanceof Integer ? (Integer) sel : -1;
                if (nt == t.team) return;
                t.team = nt;
                persistSoon();
                // rebuild after the combo's popup has closed
                SwingUtilities.invokeLater(this::refreshAll);
            });
            row.add(tc, BorderLayout.EAST);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(row);
        }
        addSide(card);
        gap(10);
    }
}

private void showTeamInfo(Component anchor, int team) {
    List<String[]> rows = new ArrayList<>();
    for (Tok t : tokens) if (t.team == team) rows.add(App.row(t.label, "HP " + t.hp + " / " + t.maxHp));
    App.showInfoOverlay(anchor, teamName(team), team >= 0 ? TEAM_COLOR_NAMES[team] + " team" : "Unassigned",
            "Tokens on this team are outlined in " + (team >= 0 ? TEAM_COLOR_NAMES[team].toLowerCase() : "gray")
                    + ". Teammates can move through each other's spaces; everyone else blocks movement.", rows);
}

private void buildSettingsTab() {
    addSide(sectionCaption("MAP DISPLAY"));
    gap(4);
    addSide(toggle("Show HP bars", showHp, v -> showHp = v, "Draw each token's HP bar and value above it."));
    addSide(toggle("Show names", showNames, v -> showNames = v, "Draw each token's name above it."));
    addSide(toggle("Show movement range", showRange, v -> showRange = v, "Highlight reachable squares while moving."));
    addSide(toggle("Show grid coordinates", showCoords, v -> showCoords = v, "Letters along the top and numbers down the side (A1 is the top-left square)."));
    gap(10);
    addSide(sectionCaption("TURNS"));
    gap(4);
    addSide(toggle("Skip defeated combatants", skipDefeated, v -> skipDefeated = v, "Creatures at 0 HP are skipped in the turn order."));
    gap(16);
    addSide(sectionCaption("ENCOUNTER"));
    gap(6);
    JButton end = App.overlayButton("End Combat", true);
    end.setBackground(App.ACCENT);
    end.setForeground(App.TEXT_LIGHT);
    end.setBorder(BorderFactory.createCompoundBorder(new LineBorder(App.ACCENT, 1, true), new EmptyBorder(7, 14, 7, 14)));
    end.addActionListener(e -> App.showConfirmOverlay(end, "End Combat",
            "End this encounter? Tokens stay where they are with their HP and teams, and the map goes back to placement mode.",
            "End Combat", this::endCombat));
    addSide(end);
    gap(6);
    addSide(muted("Tokens, HP, teams and the turn order are saved with the map, so you can leave and come back."));
}

private JCheckBox toggle(String text, boolean value, java.util.function.Consumer<Boolean> set, String tip) {
    JCheckBox cb = new JCheckBox(text, value);
    cb.setForeground(App.TEXT_LIGHT);
    cb.setBackground(App.SHEET_CARD);
    cb.setFont(new Font("SansSerif", Font.PLAIN, 13));
    cb.setFocusPainted(false);
    cb.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    cb.setToolTipText(tip);
    cb.addActionListener(e -> {
        set.accept(cb.isSelected());
        if (moveMode) computeReach();
        board.repaint();
        refreshSide();
    });
    return cb;
}

// ===============================================================
// Small UI helpers
// ===============================================================
private static JLabel sectionCaption(String text) {
    JLabel l = new JLabel(text);
    l.setForeground(App.TEXT_MUTED);
    l.setFont(new Font("SansSerif", Font.BOLD, 11));
    l.setBorder(new EmptyBorder(2, 0, 4, 0));
    l.setAlignmentX(Component.LEFT_ALIGNMENT);
    return l;
}

private static JLabel muted(String text) {
    JLabel l = new JLabel("<html><div style='width:250px'>" + App.escapeHtml(text) + "</div></html>");
    l.setForeground(App.TEXT_MUTED);
    l.setFont(new Font("SansSerif", Font.PLAIN, 12));
    l.setAlignmentX(Component.LEFT_ALIGNMENT);
    return l;
}

private static JLabel swatch(Color c, int size, String tip) {
    JLabel l = new JLabel() {
        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int s = Math.min(getWidth(), getHeight()) - 2;
            g.setColor(c);
            g.fillOval((getWidth() - s) / 2, (getHeight() - s) / 2, s, s);
            g.setColor(new Color(0, 0, 0, 120));
            g.drawOval((getWidth() - s) / 2, (getHeight() - s) / 2, s, s);
            g.dispose();
        }
    };
    l.setPreferredSize(new Dimension(size + 4, size + 4));
    l.setToolTipText(tip);
    return l;
}

private JLabel teamBadge(int team) {
    JLabel l = new JLabel(teamName(team)) {
        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(teamColor(team));
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
            g.dispose();
            super.paintComponent(g0);
        }
    };
    Color c = teamColor(team);
    double lum = 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
    l.setForeground(lum > 150 ? App.SHEET_BG : Color.WHITE);
    l.setFont(new Font("SansSerif", Font.BOLD, 12));
    l.setBorder(new EmptyBorder(3, 10, 3, 10));
    return l;
}

private static JPanel statTile(String value, String caption, Runnable onClick) {
    JPanel p = new JPanel(new BorderLayout(0, 2));
    p.setBackground(App.SHEET_BG);
    p.setBorder(BorderFactory.createCompoundBorder(new LineBorder(App.SHEET_BORDER, 1, true), new EmptyBorder(6, 4, 6, 4)));
    JLabel v = new JLabel(value, SwingConstants.CENTER);
    v.setForeground(App.TEXT_LIGHT);
    v.setFont(new Font("SansSerif", Font.BOLD, 15));
    JLabel c = new JLabel(caption, SwingConstants.CENTER);
    c.setForeground(App.TEXT_MUTED);
    c.setFont(new Font("SansSerif", Font.BOLD, 10));
    p.add(v, BorderLayout.CENTER);
    p.add(c, BorderLayout.SOUTH);
    App.clickable(p, onClick);
    return p;
}

private static JButton arrowButton(String text, String tip) {
    JButton b = new JButton(text);
    b.setToolTipText(tip);
    b.setFont(new Font("SansSerif", Font.PLAIN, 9));
    b.setForeground(App.TEXT_MUTED);
    b.setContentAreaFilled(false);
    b.setFocusPainted(false);
    b.setBorder(new EmptyBorder(0, 6, 0, 6));
    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    b.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseEntered(MouseEvent e) { if (b.isEnabled()) b.setForeground(App.GOLD); }
        @Override
        public void mouseExited(MouseEvent e) { b.setForeground(App.TEXT_MUTED); }
    });
    return b;
}

private static void styleSpinner(JSpinner sp) {
    sp.setFont(new Font("SansSerif", Font.PLAIN, 13));
    JComponent ed = sp.getEditor();
    if (ed instanceof JSpinner.DefaultEditor) {
        JFormattedTextField tf = ((JSpinner.DefaultEditor) ed).getTextField();
        tf.setBackground(App.BG_FIELD);
        tf.setForeground(App.TEXT_LIGHT);
        tf.setCaretColor(App.TEXT_LIGHT);
    }
}

private static void hoverLabel(JLabel l, Color normal, Runnable onClick) {
    l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    l.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseReleased(MouseEvent e) {
            if (App.isClickRelease(e)) onClick.run();
        }
        @Override
        public void mouseEntered(MouseEvent e) { l.setForeground(App.ACCENT_HOVER); }
        @Override
        public void mouseExited(MouseEvent e) { l.setForeground(normal); }
    });
}

// Team picker (teams 0-9). `names` (optional) are live name fields, e.g. in the setup overlay.
private JComboBox<Integer> teamCombo(int selected, JTextField[] names) {
    Integer[] items = new Integer[TEAMS];
    for (int i = 0; i < TEAMS; i++) items[i] = i;
    JComboBox<Integer> c = new JComboBox<>(items);
    c.setSelectedItem(Math.max(0, Math.min(TEAMS - 1, selected)));
    styleTeamCombo(c, names);
    return c;
}

// Team picker with "No team" (-1) first
private JComboBox<Integer> teamComboWithNone(int selected) {
    Integer[] items = new Integer[TEAMS + 1];
    items[0] = -1;
    for (int i = 0; i < TEAMS; i++) items[i + 1] = i;
    JComboBox<Integer> c = new JComboBox<>(items);
    c.setSelectedItem(Math.max(-1, Math.min(TEAMS - 1, selected)));
    styleTeamCombo(c, null);
    return c;
}

private void styleTeamCombo(JComboBox<Integer> c, JTextField[] names) {
    c.setBackground(App.BG_FIELD);
    c.setForeground(App.TEXT_LIGHT);
    c.setFont(new Font("SansSerif", Font.PLAIN, 12));
    c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    c.setMaximumRowCount(TEAMS + 1);
    c.setPreferredSize(new Dimension(140, 28));
    c.setRenderer(new DefaultListCellRenderer() {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
            JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, sel, focus);
            int team = value instanceof Integer ? (Integer) value : -1;
            String name;
            if (team >= 0 && names != null) {
                String typed = names[team].getText().trim();
                name = typed.isEmpty() ? "Team " + (team + 1) : typed;
            } else {
                name = teamName(team);
            }
            l.setText(name);
            l.setIcon(new Icon() {
                public int getIconWidth() { return 12; }
                public int getIconHeight() { return 12; }
                public void paintIcon(Component comp, Graphics g0, int x, int y) {
                    Graphics2D g = (Graphics2D) g0.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(teamColor(team));
                    g.fillOval(x, y, 12, 12);
                    g.dispose();
                }
            });
            l.setBackground(sel ? App.SHEET_HOVER : App.BG_FIELD);
            l.setForeground(App.TEXT_LIGHT);
            return l;
        }
    });
}

private static String signedInt(int n) {
    return n >= 0 ? "+" + n : String.valueOf(n);
}

private static Color hpColor(Tok t) {
    double f = t.maxHp <= 0 ? 0 : t.hp / (double) t.maxHp;
    if (f > 0.5) return new Color(0x4C, 0xAF, 0x50);
    if (f > 0.25) return new Color(0xE0, 0xA0, 0x30);
    return new Color(0xD9, 0x53, 0x4F);
}

static String colName(int x) {
    StringBuilder sb = new StringBuilder();
    int n = x + 1;
    while (n > 0) {
        int r = (n - 1) % 26;
        sb.insert(0, (char) ('A' + r));
        n = (n - 1) / 26;
    }
    return sb.toString();
}

// Scroll-pane content that always matches the viewport's width (only scrolls vertically)
private static class WidthTrackingPanel extends JPanel implements Scrollable {
    WidthTrackingPanel() {
        super(new BorderLayout());
    }
    public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
    public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(16, r.height - 32); }
    public boolean getScrollableTracksViewportWidth() { return true; }
    public boolean getScrollableTracksViewportHeight() { return false; }
}

// Horizontal bar with centered text (HP, movement)
private static class Bar extends JComponent {
    double frac;
    String text = "";
    Color color = Color.GRAY;

    Bar() {
        setPreferredSize(new Dimension(300, 24));
        setMaximumSize(new Dimension(400, 24));
    }

    void set(double frac, String text, Color color) {
        this.frac = Math.max(0, Math.min(1, frac));
        this.text = text;
        this.color = color;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        g.setColor(isOpaque() ? getBackground() : App.SHEET_BG);
        g.fillRoundRect(0, 0, w - 1, h - 1, 10, 10);
        g.setColor(color);
        g.fillRoundRect(0, 0, (int) Math.round((w - 1) * frac), h - 1, 10, 10);
        g.setColor(App.SHEET_BORDER);
        g.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        FontMetrics fm = g.getFontMetrics();
        int tx = (w - fm.stringWidth(text)) / 2, ty = (h + fm.getAscent() - fm.getDescent()) / 2;
        g.setColor(new Color(0, 0, 0, 140));
        g.drawString(text, tx + 1, ty + 1);
        g.setColor(Color.WHITE);
        g.drawString(text, tx, ty);
        g.dispose();
    }
}

// ===============================================================
// Board canvas
// ===============================================================
private class Board extends JComponent {
    double scale = 36, offX = 20, offY = 20;
    private Point pressPoint, lastPoint;
    private boolean panning, dragging;
    private Tok dragTok;
    private int grabX, grabY;           // square offset inside the dragged token
    private int ghostX = -1, ghostY = -1;
    private int hoverX = -1, hoverY = -1;
    private double hoverFx, hoverFy;

    Board() {
        setOpaque(true);
        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { onPress(e); }
            @Override
            public void mouseDragged(MouseEvent e) { onDrag(e); }
            @Override
            public void mouseReleased(MouseEvent e) { onRelease(e); }
            @Override
            public void mouseMoved(MouseEvent e) { onMove(e.getPoint()); }
            @Override
            public void mouseExited(MouseEvent e) {
                hoverX = hoverY = -1;
                repaint();
            }
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                zoomBy(Math.pow(1.12, -e.getPreciseWheelRotation()), e.getX(), e.getY());
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
        addMouseWheelListener(ma);
    }

    double fx(double px) { return (px - offX) / scale; }
    double fy(double py) { return (py - offY) / scale; }

    void zoomBy(double k, double cx, double cy) {
        double ns = Math.max(8, Math.min(140, scale * k));
        k = ns / scale;
        offX = cx - (cx - offX) * k;
        offY = cy - (cy - offY) * k;
        scale = ns;
        repaint();
    }

    void fit() {
        int w = Math.max(200, getWidth()), h = Math.max(200, getHeight());
        double margin = showCoords ? 56 : 40;
        scale = Math.max(8, Math.min(80, Math.min((w - margin) / model.width, (h - margin) / model.height)));
        offX = (w - model.width * scale) / 2;
        offY = (h - model.height * scale) / 2;
        repaint();
    }

    private Tok tokenAt(int cx, int cy) {
        for (int i = tokens.size() - 1; i >= 0; i--) if (tokens.get(i).covers(cx, cy)) return tokens.get(i);
        return null;
    }

    private boolean inMap(int cx, int cy) {
        return cx >= 0 && cy >= 0 && cx < model.width && cy < model.height;
    }

    // Nearest door edge to (x, y) in square units, or null ({orientation, x, y} as in MapEditorPanel)
    private int[] doorAt(double x, double y) {
        int rx = (int) Math.round(x), ry = (int) Math.round(y);
        double dv = Math.abs(x - rx), dh = Math.abs(y - ry);
        boolean vertical = dv < dh;
        if (Math.min(dv, dh) > 0.22) return null;
        int[] e;
        if (vertical) {
            int cy = (int) Math.floor(y);
            if (rx < 0 || rx > model.width || cy < 0 || cy >= model.height) return null;
            e = new int[]{1, rx, cy};
        } else {
            int cx = (int) Math.floor(x);
            if (ry < 0 || ry > model.height || cx < 0 || cx >= model.width) return null;
            e = new int[]{0, cx, ry};
        }
        int v = model.getEdge(e);
        return v == MapEditorPanel.DOOR_CLOSED || v == MapEditorPanel.DOOR_OPEN ? e : null;
    }

    private MapEditorPanel.Pin pinAt(double x, double y) {
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
        MapEditorPanel.Pin p = model.pinAt(cx, cy);
        if (p == null) return null;
        double r = Math.max(0.3, 9 / scale);
        return Math.hypot(x - (p.x + 0.5), y - (p.y + 0.5)) <= r + 0.15 ? p : null;
    }

    private void onPress(MouseEvent e) {
        requestFocusInWindow();
        pressPoint = lastPoint = e.getPoint();
        panning = dragging = false;
        dragTok = null;
        if (SwingUtilities.isLeftMouseButton(e) && placing == null && targeting == null) {
            int cx = (int) Math.floor(fx(e.getX())), cy = (int) Math.floor(fy(e.getY()));
            Tok t = tokenAt(cx, cy);
            if (t != null) {
                dragTok = t;
                grabX = cx - t.x;
                grabY = cy - t.y;
                return;
            }
        }
        panning = true;
    }

    private void onDrag(MouseEvent e) {
        Point p = e.getPoint();
        if (dragTok != null) {
            if (!dragging && pressPoint.distance(p) > 4) dragging = true;
            if (dragging) {
                int cx = (int) Math.floor(fx(p.x)), cy = (int) Math.floor(fy(p.y));
                int[] a = anchorFor(dragTok.size, cx - grabX, cy - grabY);
                ghostX = a[0];
                ghostY = a[1];
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                setStatus("Drop " + dragTok.label + " on square " + colName(ghostX) + (ghostY + 1)
                        + (inCombat ? " (repositioning doesn't use movement; use Move for that)" : "") + ".");
                repaint();
            }
            return;
        }
        if (panning) {
            offX += p.x - lastPoint.x;
            offY += p.y - lastPoint.y;
            lastPoint = p;
            if (pressPoint.distance(p) > 4) setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            repaint();
        }
    }

    private void onRelease(MouseEvent e) {
        boolean wasClick = pressPoint != null && pressPoint.distance(e.getPoint()) <= 4;
        if (dragTok != null && dragging) {
            Tok t = dragTok;
            if (ghostX >= 0 && fits(t, t.size, ghostX, ghostY, false) && (ghostX != t.x || ghostY != t.y)) {
                t.x = ghostX;
                t.y = ghostY;
                if (moveMode) computeReach();
                persistSoon();
                setStatus("Moved " + t.label + " to " + colName(t.x) + (t.y + 1) + ".");
            } else if (ghostX >= 0 && (ghostX != t.x || ghostY != t.y)) {
                setStatus("That space is taken.");
            } else {
                setStatus(defaultStatus());
            }
            ghostX = ghostY = -1;
        } else if (wasClick) {
            if (SwingUtilities.isLeftMouseButton(e)) click(fx(e.getX()), fy(e.getY()));
            else if (placing != null || moveMode || targeting != null) cancelModes(); // right-click stops placing / moving / targeting
        }
        dragTok = null;
        dragging = panning = false;
        onMove(e.getPoint());
    }

    private void click(double x, double y) {
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
        if (placing != null) {
            if (inMap(cx, cy)) placeAt(cx, cy);
            return;
        }
        if (targeting != null) {
            Tok target = inMap(cx, cy) ? tokenAt(cx, cy) : null;
            if (target == null || target == active) {
                setStatus("Click a creature to attack with " + targeting.name + ". Right-click or Esc to cancel.");
                return;
            }
            TargetCheck tc = checkTarget(active, target, targeting);
            if (!tc.ok) {
                setStatus(target.label + ": " + tc.reason + " Pick another target, or Esc to cancel.");
                return;
            }
            WeaponRules.Attack atk = targeting;
            targeting = null;
            showAttackRoll(this, active, target, atk, tc);
            repaint();
            return;
        }
        Tok t = tokenAt(cx, cy);
        if (t != null) {
            showTokenDetail(this, t);
            return;
        }
        int[] door = doorAt(x, y);
        if (door != null) {
            int cur = model.getEdge(door);
            model.setEdge(door, cur == MapEditorPanel.DOOR_CLOSED ? MapEditorPanel.DOOR_OPEN : MapEditorPanel.DOOR_CLOSED);
            if (asset != null) asset.put("grid", model.toJson());
            if (moveMode) computeReach();
            setStatus(cur == MapEditorPanel.DOOR_CLOSED ? "Door opened." : "Door closed.");
            persistNow();
            repaint();
            return;
        }
        MapEditorPanel.Pin pin = pinAt(x, y);
        if (pin != null) {
            App.showInfoOverlay(this, pin.title.isEmpty() ? "Untitled Pin" : pin.title, "Pin • square " + colName(pin.x) + (pin.y + 1),
                    pin.notes.isEmpty() ? "No notes." : pin.notes, null);
            return;
        }
        if (moveMode) {
            int[] a = inMap(cx, cy) ? reachAnchorFor(cx, cy, x, y) : null;
            if (a != null) moveActiveTo(a[0], a[1], a[2]);
            else setStatus(active.label + " can't reach that square (" + moveLeft + " ft. left). Esc to stop moving.");
            return;
        }
        if (inMap(cx, cy)) {
            int fl = model.floor[cy][cx];
            App.showInfoOverlay(this, MapEditorPanel.FLOOR_NAMES[fl], "Square " + colName(cx) + (cy + 1) + " • 5 ft.",
                    MapEditorPanel.FLOOR_INFO[fl], null);
        }
    }

    private void onMove(Point p) {
        double x = fx(p.x), y = fy(p.y);
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
        hoverFx = x;
        hoverFy = y;
        hoverX = inMap(cx, cy) ? cx : -1;
        hoverY = inMap(cx, cy) ? cy : -1;
        if (dragging) return;
        if (placing != null) {
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        } else {
            boolean clickable = inMap(cx, cy) && (tokenAt(cx, cy) != null || (moveMode && reachCells.length > 0 && reachCells[cy][cx]));
            if (doorAt(x, y) != null || pinAt(x, y) != null) clickable = true;
            if (!panning) setCursor(Cursor.getPredefinedCursor(clickable ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        }
        if (targeting != null && hoverX >= 0) {
            Tok t = tokenAt(hoverX, hoverY);
            if (t != null && t != active) {
                TargetCheck tc = checkTarget(active, t, targeting);
                setStatus(t.label + ": " + tc.distance + " ft. away. " + (tc.ok ? (tc.disadvantage ? "In long range (Disadvantage). " : "In range. ")
                        + (tc.cover > 0 ? "Half cover (+2 AC). " : "") + "Click to attack." : tc.reason));
                setCursor(Cursor.getPredefinedCursor(tc.ok ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            } else {
                setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            }
        } else if (moveMode && hoverX >= 0) {
            int[] a = reachAnchorFor(hoverX, hoverY, x, y);
            if (a != null) setStatus("Move " + active.label + " here: " + a[2] + " ft. (" + (moveLeft - a[2]) + " ft. left after).");
        } else if (!moveMode && placing == null && hoverX >= 0) {
            Tok t = tokenAt(hoverX, hoverY);
            if (t != null) setStatus(t.label + "  •  HP " + t.hp + " / " + t.maxHp + "  •  AC " + t.ac + "  •  " + teamName(t.team) + "  •  click for details, drag to move");
        }
        repaint();
    }

    // ---------------- painting ----------------
    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        MapEditorPanel.MapPainter p = new MapEditorPanel.MapPainter(model, scale, offX, offY);
        p.paintBase(g, getWidth(), getHeight());

        // reachable squares
        if (moveMode && showRange && reachCells.length == model.height) {
            for (int y = 0; y < model.height; y++) {
                for (int x = 0; x < model.width; x++) {
                    if (!reachCells[y][x]) continue;
                    Rectangle2D r = new Rectangle2D.Double(offX + x * scale + 1, offY + y * scale + 1, scale - 2, scale - 2);
                    g.setColor(REACH_FILL);
                    g.fill(r);
                    g.setColor(REACH_EDGE);
                    g.setStroke(new BasicStroke(1f));
                    g.draw(r);
                }
            }
        }

        p.paintEdges(g);
        p.paintPins(g);
        if (showCoords) paintCoords(g);

        // tokens: defeated underneath, the active combatant on top
        List<Tok> drawOrder = new ArrayList<>(tokens);
        drawOrder.sort((a, b) -> {
            int ka = a == active ? 2 : a.down() ? 0 : 1, kb = b == active ? 2 : b.down() ? 0 : 1;
            return ka - kb;
        });
        for (Tok t : drawOrder) {
            if (dragging && t == dragTok) {
                paintToken(g, t, t.x, t.y, 0.35f);
                continue;
            }
            paintToken(g, t, t.x, t.y, 1f);
        }
        for (Tok t : drawOrder) if (!(dragging && t == dragTok)) paintLabels(g, t, t.x, t.y);

        // ghosts: drag target, placement preview, move preview
        if (targeting != null && active != null) paintTargets(g);

        if (dragging && dragTok != null && ghostX >= 0) {
            boolean ok = fits(dragTok, dragTok.size, ghostX, ghostY, false);
            paintFootprint(g, ghostX, ghostY, dragTok.size, ok);
            paintToken(g, dragTok, ghostX, ghostY, 0.8f);
        } else if (placing != null && hoverX >= 0) {
            int[] a = anchorFor(placing.size, hoverX, hoverY);
            boolean ok = fits(null, placing.size, a[0], a[1], false);
            paintFootprint(g, a[0], a[1], placing.size, ok);
            Tok ghost = new Tok();
            ghost.style = placing.style;
            ghost.label = placing.sourceName;
            ghost.size = placing.size;
            ghost.hp = 1;
            ghost.team = inCombat ? (PC.equals(placing.kind) ? 0 : 1) : -1;
            paintToken(g, ghost, a[0], a[1], 0.6f);
        } else if (moveMode && hoverX >= 0) {
            int[] a = reachAnchorFor(hoverX, hoverY, hoverFx, hoverFy);
            if (a != null) {
                paintFootprint(g, a[0], a[1], active.size, true);
                paintToken(g, active, a[0], a[1], 0.5f);
            }
        }

        p.paintLegend(g, getHeight());
        g.dispose();
    }

    // Targeting: ring every other creature green (in range), amber (long range: Disadvantage) or red (can't attack),
    // and draw a line with the distance to the hovered one
    private void paintTargets(Graphics2D g0) {
        Graphics2D g = (Graphics2D) g0.create();
        double ax = offX + (active.x + active.size / 2.0) * scale, ay = offY + (active.y + active.size / 2.0) * scale;
        Tok hovered = hoverX >= 0 ? tokenAt(hoverX, hoverY) : null;
        for (Tok t : tokens) {
            if (t == active) continue;
            TargetCheck tc = checkTarget(active, t, targeting);
            Color c = !tc.ok ? new Color(230, 80, 80, 200) : tc.disadvantage ? new Color(240, 180, 60, 230) : new Color(110, 220, 130, 230);
            double s = t.size * scale, cx = offX + t.x * scale + s / 2, cy = offY + t.y * scale + s / 2, r = s / 2 * 0.98;
            g.setColor(c);
            g.setStroke(new BasicStroke(t == hovered ? 4f : 2.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                    tc.ok ? null : new float[]{5f, 4f}, 0f));
            g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
            if (t == hovered) {
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[]{6f, 5f}, 0f));
                g.draw(new Line2D.Double(ax, ay, cx, cy));
                String label = tc.distance + " ft." + (tc.cover > 0 ? " · half cover" : "");
                g.setFont(new Font("SansSerif", Font.BOLD, 12));
                FontMetrics fm = g.getFontMetrics();
                double mx = (ax + cx) / 2, my = (ay + cy) / 2;
                g.setColor(new Color(20, 22, 30, 220));
                g.fill(new RoundRectangle2D.Double(mx - fm.stringWidth(label) / 2.0 - 6, my - 10, fm.stringWidth(label) + 12, 20, 8, 8));
                g.setColor(c);
                g.drawString(label, (float) (mx - fm.stringWidth(label) / 2.0), (float) (my + 4));
            }
        }
        // the attacker's melee reach as a soft square
        if (targeting.melee && targeting.reach > 0) {
            int rc = targeting.reach / 5;
            Rectangle2D reachBox = new Rectangle2D.Double(offX + (active.x - rc) * scale, offY + (active.y - rc) * scale,
                    (active.size + rc * 2) * scale, (active.size + rc * 2) * scale);
            g.setColor(new Color(212, 175, 55, 28));
            g.fill(reachBox);
            g.setColor(new Color(212, 175, 55, 120));
            g.setStroke(new BasicStroke(1.5f));
            g.draw(reachBox);
        }
        g.dispose();
    }

    private void paintFootprint(Graphics2D g, int x, int y, int size, boolean ok) {
        Rectangle2D r = new Rectangle2D.Double(offX + x * scale + 1, offY + y * scale + 1, size * scale - 2, size * scale - 2);
        g.setColor(ok ? new Color(120, 220, 140, 50) : new Color(230, 80, 80, 60));
        g.fill(r);
        g.setColor(ok ? new Color(120, 220, 140, 200) : new Color(230, 80, 80, 200));
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{5f, 4f}, 0f));
        g.draw(r);
    }

    private void paintToken(Graphics2D g0, Tok t, int x, int y, float alpha) {
        Graphics2D g = (Graphics2D) g0.create();
        if (alpha < 1f) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        double s = t.size * scale;
        double cx = offX + x * scale + s / 2, cy = offY + y * scale + s / 2;
        double r = s / 2 * 0.84;
        float ow = (float) Math.max(2.5, Math.min(8, scale * 0.1 * Math.sqrt(t.size)));
        if (t == active && inCombat && alpha >= 1f) {
            // active combatant: gold footprint + halo
            g.setColor(new Color(212, 175, 55, 45));
            g.fill(new Rectangle2D.Double(offX + x * scale + 1, offY + y * scale + 1, s - 2, s - 2));
            g.setColor(new Color(212, 175, 55, 210));
            g.setStroke(new BasicStroke(2f));
            g.draw(new Rectangle2D.Double(offX + x * scale + 1, offY + y * scale + 1, s - 2, s - 2));
            g.setStroke(new BasicStroke(ow * 0.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(212, 175, 55, 120));
            g.draw(Tokens.shape(CustomAssets.str(t.style, "shape"), cx, cy, r + ow * 1.2));
        }
        Tokens.paint(g, t.style, t.label, cx, cy, r, teamColor(t.team), ow);
        if (t.down()) {
            Shape sh = Tokens.shape(CustomAssets.str(t.style, "shape"), cx, cy, r);
            g.setColor(new Color(15, 16, 22, 150));
            g.fill(sh);
            g.setColor(new Color(220, 60, 60, 220));
            g.setStroke(new BasicStroke((float) Math.max(2, r * 0.14), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double k = r * 0.5;
            g.draw(new Line2D.Double(cx - k, cy - k, cx + k, cy + k));
            g.draw(new Line2D.Double(cx + k, cy - k, cx - k, cy + k));
        }
        g.dispose();
    }

    // Name and HP bar above the token
    private void paintLabels(Graphics2D g, Tok t, int x, int y) {
        boolean names = showNames && scale >= 20;
        boolean hp = showHp && scale >= 12;
        if (!names && !hp) return;
        double s = t.size * scale;
        double cx = offX + x * scale + s / 2, top = offY + y * scale + s / 2 - s / 2 * 0.84 - 3;
        Font f = new Font("SansSerif", Font.BOLD, (int) Math.max(10, Math.min(13, scale * 0.3)));
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        String name = t.label.length() > 22 ? t.label.substring(0, 21) + "…" : t.label;
        String hpText = t.hp + "/" + t.maxHp;
        Font hf = new Font("SansSerif", Font.BOLD, 10);
        FontMetrics hfm = g.getFontMetrics(hf);
        double barW = Math.max(34, Math.min(s * 0.9, 90));
        double boxW = Math.max(names ? fm.stringWidth(name) + 10 : 0, hp ? barW + hfm.stringWidth(hpText) + 14 : 0);
        double boxH = (names ? fm.getHeight() : 0) + (hp ? 10 : 0) + 4;
        double bx = cx - boxW / 2, by = top - boxH;
        g.setColor(new Color(20, 22, 30, 215));
        g.fill(new RoundRectangle2D.Double(bx, by, boxW, boxH, 7, 7));
        g.setColor(t == active && inCombat ? App.GOLD : new Color(teamColor(t.team).getRed(), teamColor(t.team).getGreen(), teamColor(t.team).getBlue(), 170));
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Double(bx, by, boxW, boxH, 7, 7));
        double yy = by + 2;
        if (names) {
            g.setColor(t.down() ? App.TEXT_MUTED : App.TEXT_LIGHT);
            g.drawString(name, (float) (cx - fm.stringWidth(name) / 2.0), (float) (yy + fm.getAscent()));
            yy += fm.getHeight();
        }
        if (hp) {
            double barX = bx + 5;
            double frac = t.maxHp <= 0 ? 0 : Math.max(0, Math.min(1, t.hp / (double) t.maxHp));
            g.setColor(new Color(0, 0, 0, 160));
            g.fill(new RoundRectangle2D.Double(barX, yy + 2, barW, 6, 4, 4));
            g.setColor(hpColor(t));
            g.fill(new RoundRectangle2D.Double(barX, yy + 2, barW * frac, 6, 4, 4));
            g.setFont(hf);
            g.setColor(App.TEXT_LIGHT);
            g.drawString(hpText, (float) (barX + barW + 5), (float) (yy + 9));
        }
    }

    private void paintCoords(Graphics2D g) {
        if (scale < 12) return;
        g.setFont(new Font("SansSerif", Font.BOLD, (int) Math.max(9, Math.min(12, scale * 0.3))));
        FontMetrics fm = g.getFontMetrics();
        double colY = Math.max(fm.getAscent() + 2, offY - 5);
        double rowX = Math.max(4, offX - 6);
        boolean inside = offX - 6 < 22; // map starts at the left edge: draw row numbers just inside
        int step = scale < 18 ? 2 : 1;
        for (int x = 0; x < model.width; x += step) {
            String s = colName(x);
            double px = offX + (x + 0.5) * scale - fm.stringWidth(s) / 2.0;
            if (px < -20 || px > getWidth()) continue;
            g.setColor(new Color(0, 0, 0, 140));
            g.drawString(s, (float) px + 1, (float) colY + 1);
            g.setColor(App.GOLD);
            g.drawString(s, (float) px, (float) colY);
        }
        for (int y = 0; y < model.height; y += step) {
            String s = String.valueOf(y + 1);
            double py = offY + (y + 0.5) * scale + fm.getAscent() / 2.0 - 1;
            if (py < 0 || py > getHeight() + 20) continue;
            double px = inside ? rowX + 2 : rowX - fm.stringWidth(s);
            g.setColor(new Color(0, 0, 0, 140));
            g.drawString(s, (float) px + 1, (float) py + 1);
            g.setColor(App.GOLD);
            g.drawString(s, (float) px, (float) py);
        }
    }
}
}
