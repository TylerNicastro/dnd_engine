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
//   tokens    : [{uid, kind ("PC" | "NPC"), refId, label, x, y, hp, team (-1 none, 0-9), init,
//                 ammo {Arrow: 19, ...}, items {"Potion of Healing": 2, ...}, conditions [...], damageMods {Fire: mask, ...},
//                 dodging, reactionsPerRound, reactionsUsed, hands {main, off, twoHanded} (characters), torch (NPC carries one)}]
//               (x, y) is the token's top-left square. Size, max HP, speed, AC, initiative bonus, attacks and the
//               token's look are read from the character (by its "id", see CharacterStorage.loadWithIds) or the NPC
//               asset every time, so edits made elsewhere show up. Tokens whose character / NPC was deleted are dropped.
//               Combat-only state never touches the character sheet: ammo is copied from the character's Equipment
//               when the token is placed and when combat starts, then counted here; damageMods are per-token
//               adjustments (DamageRules masks, 0 = normal) on top of an NPC's typed traits. `hands` is the
//               character's grip in this encounter: copied from the sheet when placed, changed with Use Item, and
//               used (through combatRec) for its attacks and AC; the sheet's own grip is never changed.
//   teamNames : TEAMS strings ("" shows as "Team N")
//   combat    : {round, active (uid), moveLeft, order: [uid, ...], attacksLeft, actionUsed, attackAction,
//                bonusAttackUsed (= Bonus Action used), disengaged} while combat is running (absent otherwise)
//
// Light (Lighting.java): the map's grid holds its default light, painted Bright / Dim / Dark squares and mounted torches.
// Creatures holding a Torch / Lamp / Hooded Lantern / Candle (characters: in a hand; NPCs: the token's `torch` flag) light
// the squares around them. Attacking a creature you can't see (it's in Darkness beyond your Darkvision) has Disadvantage;
// a target that can't see its attacker is attacked with Advantage; opportunity attacks need a mover you can see.
// Mounted torches can be attacked like creatures (a stand-in Tok of kind OBJ); their HP is written back to the grid.
// Characters' damage modifiers start from their species' resistances (SpeciesTraits.damageMods).
//
// Turn economy: one action (Attack, Dash, Disengage or Dodge), one Bonus Action (the Light-weapon extra attack, or a
// Rogue's Cunning Action Dash / Disengage) and one Reaction per round (opportunity attacks). Leaving an enemy's
// reach with a Move provokes an opportunity attack unless the mover Disengaged; dragging a token never does.
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
private static final String PC = "PC", NPC = "NPC", OBJ = "OBJ"; // OBJ: a mounted torch being attacked (never saved)

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
    Map<String, Integer> sourceMods = new LinkedHashMap<>(); // damage modifiers from NPC traits (DamageRules masks)
    // combat-only state (saved with the map)
    Map<String, Integer> modOverrides = new LinkedHashMap<>(); // per-token damage modifier adjustments
    Map<String, Integer> ammo = new LinkedHashMap<>();         // ammunition left, by item name
    boolean dodging;      // took the Dodge action: attacks against it have Disadvantage until its next turn
    int reactionsPerRound = 1; // Reactions it gets each round (1 for most creatures)
    int reactionsUsed;          // used since its last turn started (opportunity attacks, Shield, ...)
    List<String> conditions = new ArrayList<>(); // combat-only conditions (characters start from their sheet)
    Map<String, Integer> items = new LinkedHashMap<>(); // combat-only usable items (characters start from Equipment)
    boolean itemsLoaded;        // items / conditions were copied from the source once
    Map<String, Object> hands;  // combat-only grip {main, off, twoHanded} (characters; copied from the sheet when placed)
    // animation only (not saved)
    double drawX = Double.NaN, drawY = Double.NaN; // drawn position while sliding (in squares)
    double shakeDx;             // sideways shake offset in pixels
    boolean alert;              // red ring: waiting on its opportunity attack choice
    // lighting
    boolean carriesTorch;       // NPCs: carrying a lit torch this encounter (characters hold lights in their hands instead)
    MapEditorPanel.Torch fixture; // OBJ targets only: the mounted torch this stands for

    boolean hasReaction() {
        return reactionsUsed < reactionsPerRound && !CombatConditions.incapacitated(conditions) && !down();
    }

    Map<String, Integer> damageMods() {
        return DamageRules.effective(sourceMods, modOverrides);
    }

    // Longest melee reach among its attacks (opportunity attacks); 5 ft. by default
    int meleeReach() {
        int r = 0;
        for (WeaponRules.Attack a : attacks) if (a.melee) r = Math.max(r, a.reach);
        return r > 0 ? r : 5;
    }

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
private boolean bonusAttackUsed;      // the Bonus Action is used (Light-weapon extra attack, or Cunning Action)
private boolean actionUsed;           // the action is used (Attack, Dash, Disengage or Dodge)
private boolean attackAction;         // the action used was Attack (more attacks may follow)
private boolean disengaged;           // took Disengage: this turn's movement doesn't provoke opportunity attacks
private WeaponRules.Attack targeting; // attack waiting for a target click, or null
private boolean moveMode;
private Map<Integer, Integer> reach = new HashMap<>(); // anchor key (y * width + x) -> movement cost in feet
private boolean[][] reachCells = new boolean[0][0];
private int[] reachPrev;             // movement search: previous anchor key on the best path (-1 = none)
private boolean animating;           // a token is sliding (or paused for an opportunity attack): board input waits

// view toggles (Combat Settings)
private boolean showHp = true, showNames = true, showRange = true, showCoords = false, skipDefeated = true, showLight = true;

// lighting cache (see Lighting.java): recomputed when a light source moves, a door or torch changes, or the map reloads
private int[][] lightCache;
private String lightKey = "";

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
            t.ammo = intMap(m.get("ammo"));
            t.modOverrides = intMap(m.get("damageMods"));
            t.dodging = CustomAssets.bool(m, "dodging");
            t.reactionsPerRound = Math.max(0, CustomAssets.intVal(m, "reactionsPerRound", 1));
            t.reactionsUsed = CustomAssets.intVal(m, "reactionsUsed", CustomAssets.bool(m, "reactionUsed") ? 1 : 0);
            t.conditions = CustomAssets.list(m, "conditions");
            t.items = intMap(m.get("items"));
            t.itemsLoaded = m.get("items") instanceof Map;
            if (m.get("hands") instanceof Map) {
                t.hands = new LinkedHashMap<>();
                for (Map.Entry<?, ?> en : ((Map<?, ?>) m.get("hands")).entrySet()) t.hands.put(String.valueOf(en.getKey()), en.getValue());
            }
            t.carriesTorch = CustomAssets.bool(m, "torch");
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
        actionUsed = CustomAssets.bool(cm, "actionUsed");
        attackAction = CustomAssets.bool(cm, "attackAction");
        disengaged = CustomAssets.bool(cm, "disengaged");
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
        // what it holds in this encounter: starts as the sheet's grip, then changes with Use Item (never written back)
        if (t.hands == null) t.hands = WeaponRules.handsJson(WeaponRules.hands(rec));
        t.hands = WeaponRules.handsJson(WeaponRules.hands(combatRec(t))); // drops items the character no longer owns
        t.ac = WeaponRules.armorClass(combatRec(t));
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
        t.attacks = WeaponRules.characterAttacks(combatRec(t));
        t.attacksPerAction = WeaponRules.attacksPerAction(t.source);
    } else {
        @SuppressWarnings("unchecked")
        Map<String, Object> npc = (Map<String, Object>) t.source;
        t.attacks = WeaponRules.npcAttacks(npc);
        t.attacksPerAction = WeaponRules.npcAttacksPerAction(npc);
    }
    // damage modifiers: an NPC's typed traits, or a character's species resistances (e.g. a Dwarf resists Poison)
    t.sourceMods = NPC.equals(t.kind) ? DamageRules.fromTraits(t.source) : SpeciesTraits.damageMods(t.source);
    // ammunition this token's attacks need but isn't tracking yet (combat-only copy; the sheet is never changed)
    for (WeaponRules.Attack a : t.attacks) if (!a.ammo.isEmpty() && !t.ammo.containsKey(a.ammo)) t.ammo.put(a.ammo, startingAmmo(t, a.ammo));
    if (!t.itemsLoaded) {
        // first time: copy usable items and current conditions from the character sheet (combat-only copies)
        t.itemsLoaded = true;
        if (PC.equals(t.kind)) {
            t.items = CombatItems.fromEquipment(CharacterStorage.recList(t.source, "equipment"));
            for (String c : App.recConditions(t.source)) if (!"Conscious".equals(c) && !t.conditions.contains(c)) t.conditions.add(c);
        }
    }
    t.size = Math.min(sizeSquares(t.sizeName), Math.max(1, Math.min(model.width, model.height)));
    if (t.hp < 0 || t.hp > t.maxHp) t.hp = t.maxHp;
    return true;
}

// The character record as combat sees it: the sheet, but holding this token's combat grip
private static Map<?, ?> combatRec(Tok t) {
    if (t.source == null || t.hands == null) return t.source;
    Map<Object, Object> copy = new LinkedHashMap<>(t.source);
    copy.put("hands", t.hands);
    return copy;
}

// Changes what a character token holds (combat only) and rebuilds its attacks and AC
private void equipHand(Tok t, String item, int which) {
    if (t.source == null) return;
    WeaponRules.Hands h = WeaponRules.hands(combatRec(t));
    WeaponRules.equip(h, item, which, CharacterStorage.recList(t.source, "equipment"));
    t.hands = WeaponRules.handsJson(h);
    t.hands = WeaponRules.handsJson(WeaponRules.hands(combatRec(t)));
    t.ac = WeaponRules.armorClass(combatRec(t));
    t.attacks = WeaponRules.characterAttacks(combatRec(t));
    for (WeaponRules.Attack a : t.attacks) if (!a.ammo.isEmpty() && !t.ammo.containsKey(a.ammo)) t.ammo.put(a.ammo, startingAmmo(t, a.ammo));
    persistSoon();
    refreshAll();
}

// "Hand 1: Longsword · Hand 2: Shield" (or "Both hands: Greatsword")
private static String handsText(Tok t) {
    WeaponRules.Hands h = WeaponRules.hands(combatRec(t));
    String main = h.main.isEmpty() ? "empty" : h.main, off = h.off.isEmpty() ? "empty" : h.off;
    if (h.twoHanded && !h.main.isEmpty()) return "Both hands: " + main;
    return "Hand 1: " + main + "  ·  Hand 2: " + off;
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
        m.put("ammo", new LinkedHashMap<String, Object>(t.ammo));
        m.put("damageMods", new LinkedHashMap<String, Object>(t.modOverrides));
        m.put("dodging", t.dodging);
        m.put("reactionsPerRound", t.reactionsPerRound);
        m.put("reactionsUsed", t.reactionsUsed);
        m.put("conditions", new ArrayList<Object>(t.conditions));
        m.put("items", new LinkedHashMap<String, Object>(t.items));
        if (t.hands != null) m.put("hands", new LinkedHashMap<String, Object>(t.hands));
        if (t.carriesTorch) m.put("torch", true);
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
        c.put("actionUsed", actionUsed);
        c.put("attackAction", attackAction);
        c.put("disengaged", disengaged);
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
    for (Tok t : tokens) {
        resetAmmo(t); // fresh encounter: ammunition and usable items copied from Equipment again
        if (PC.equals(t.kind) && t.source != null) t.items = CombatItems.fromEquipment(CharacterStorage.recList(t.source, "equipment"));
        t.dodging = false;
        t.reactionsUsed = 0;
    }
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
    if (animating) return; // finish the slide (and any opportunity attacks) first
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
    for (Tok t : tokens) {
        t.dodging = false;
        t.reactionsUsed = 0;
    }
    setStatus("Combat ended. Back to placement: drag tokens freely, or Start Combat again. "
            + "Ammunition used in the fight isn't removed from character sheets: about half of it can be recovered.");
    persistNow();
    refreshAll();
}

private void setMoveMode(boolean on) {
    if (on && animating) return;
    if (on) targeting = null;
    moveMode = on && inCombat && active != null && moveLeft > 0 && !active.down() && CombatConditions.speedZeroBy(active.conditions).isEmpty();
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
    int[] diag = new int[w * h]; // tie-breaker: fewer diagonal steps, so equal-cost paths look straight
    reachPrev = new int[w * h];
    Arrays.fill(dist, Integer.MAX_VALUE);
    Arrays.fill(reachPrev, -1);
    int start = active.y * w + active.x;
    if (start < 0 || start >= dist.length) return;
    dist[start] = 0;
    PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[0] != b[0] ? a[0] - b[0] : a[2] - b[2]);
    pq.add(new int[]{0, start, 0});
    while (!pq.isEmpty()) {
        int[] cur = pq.poll();
        if (cur[0] > dist[cur[1]] || (cur[0] == dist[cur[1]] && cur[2] > diag[cur[1]])) continue;
        int x = cur[1] % w, y = cur[1] / w;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;
                int nx = x + dx, ny = y + dy;
                if (nx < 0 || ny < 0 || nx + size > w || ny + size > h) continue;
                if (!canStep(active, x, y, dx, dy)) continue;
                int nc = cur[0] + (difficultAt(nx, ny, size) ? 10 : 5);
                int nd = cur[2] + (dx != 0 && dy != 0 ? 1 : 0);
                int nk = ny * w + nx;
                if (nc > moveLeft || nc > dist[nk] || (nc == dist[nk] && nd >= diag[nk])) continue;
                dist[nk] = nc;
                diag[nk] = nd;
                reachPrev[nk] = cur[1];
                pq.add(new int[]{nc, nk, nd});
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

// Moves the active creature to a reachable anchor, sliding square by square along the path the movement
// search found. Before each step that leaves an enemy's reach (without Disengage), the slide pauses, that enemy's
// token shakes, and it may make an opportunity attack; then the movement carries on (or stops if the mover drops).
private void moveActiveTo(int ax, int ay, int cost) {
    Tok mover = active;
    List<int[]> path = reachPath(ax, ay);
    moveLeft = Math.max(0, moveLeft - cost);
    // hide the range overlay while sliding
    moveMode = false;
    reach = new HashMap<>();
    reachCells = new boolean[0][0];
    animating = true;
    setStatus(mover.label + " moves " + cost + " ft.");
    refreshSide();
    animateStep(mover, path, 0, cost, new HashSet<>());
}

// Anchors from the active creature's square to (ax, ay), following the movement search's predecessors
private List<int[]> reachPath(int ax, int ay) {
    List<int[]> path = new ArrayList<>();
    int w = model.width;
    int start = active.y * w + active.x, k = ay * w + ax;
    int guard = w * model.height;
    while (k >= 0 && k != start && guard-- > 0) {
        path.add(0, new int[]{k % w, k / w});
        k = reachPrev != null && k < reachPrev.length ? reachPrev[k] : -1;
    }
    path.add(0, new int[]{active.x, active.y});
    if (path.size() == 1) path.add(new int[]{ax, ay}); // no recorded path: slide straight there
    return path;
}

private static int distanceAt(Tok a, int bx, int by, int bsize) {
    return Math.max(gapCells(a.x, a.size, bx, bsize), gapCells(a.y, a.size, by, bsize)) * 5;
}

private static boolean hasMelee(Tok t) {
    for (WeaponRules.Attack a : t.attacks) if (a.melee) return true;
    return false;
}

private void animateStep(Tok mover, List<int[]> path, int i, int cost, Set<Tok> asked) {
    if (i >= path.size() - 1 || mover.down() || !tokens.contains(mover)) {
        finishMove(mover, cost, i < path.size() - 1);
        return;
    }
    int[] from = path.get(i), to = path.get(i + 1);
    if (!disengaged) {
        // enemies whose reach this step leaves get one chance each (in initiative order)
        for (Tok e : order) {
            if (e == mover || asked.contains(e) || !e.hasReaction() || !hasMelee(e)) continue;
            if (mover.team >= 0 && e.team == mover.team) continue;
            // opportunity attacks need a creature you can see (Darkness or being Blinded hides it)
            if (!canSee(e, mover) || e.conditions.stream().anyMatch("Blinded"::equalsIgnoreCase)) continue;
            int r = e.meleeReach();
            if (distanceAt(e, from[0], from[1], mover.size) <= r && distanceAt(e, to[0], to[1], mover.size) > r) {
                asked.add(e);
                Runnable resume = () -> {
                    e.alert = false;
                    board.repaint();
                    animateStep(mover, path, i, cost, asked); // re-check this step for the next enemy, then slide on
                };
                setStatus(mover.label + " is leaving " + e.label + "'s reach: opportunity attack?");
                afterDelay(220, () -> shake(e, () -> offerOpportunityAttack(mover, e, resume)));
                return;
            }
        }
    }
    slide(mover, from, to, () -> {
        mover.x = to[0];
        mover.y = to[1];
        animateStep(mover, path, i + 1, cost, asked);
    });
}

private void finishMove(Tok mover, int cost, boolean interrupted) {
    animating = false;
    mover.drawX = Double.NaN;
    mover.drawY = Double.NaN;
    if (interrupted && mover.down()) setStatus(mover.label + " drops to 0 HP; the movement stops.");
    else if (active == mover) {
        setMoveMode(moveLeft > 0 && !mover.down());
        if (!moveMode) setStatus(mover.label + " moved " + cost + " ft. No movement left this turn.");
        else setStatus(mover.label + " moved " + cost + " ft. " + moveLeft + " ft. left: click another square, or Esc to stop.");
    }
    persistSoon();
    refreshAll();
}

private static final int SLIDE_MS = 130, FRAME_MS = 15;

// Slides a token's drawn position from one anchor to the next, then runs `then`
private void slide(Tok t, int[] from, int[] to, Runnable then) {
    long start = System.currentTimeMillis();
    javax.swing.Timer timer = new javax.swing.Timer(FRAME_MS, null);
    timer.addActionListener(e -> {
        double p = Math.min(1, (System.currentTimeMillis() - start) / (double) SLIDE_MS);
        double ease = p < 0.5 ? 2 * p * p : 1 - Math.pow(-2 * p + 2, 2) / 2; // ease in-out
        t.drawX = from[0] + (to[0] - from[0]) * ease;
        t.drawY = from[1] + (to[1] - from[1]) * ease;
        board.repaint();
        if (p >= 1) {
            timer.stop();
            then.run();
        }
    });
    t.drawX = from[0];
    t.drawY = from[1];
    timer.start();
}

// Shakes a token side to side with a red alert ring (it stays ringed until its choice is made)
private void shake(Tok t, Runnable then) {
    long start = System.currentTimeMillis();
    int duration = 520;
    t.alert = true;
    javax.swing.Timer timer = new javax.swing.Timer(FRAME_MS, null);
    timer.addActionListener(e -> {
        double p = Math.min(1, (System.currentTimeMillis() - start) / (double) duration);
        t.shakeDx = Math.sin(p * Math.PI * 2 * 5) * Math.min(9, board.scale * 0.16) * (1 - p);
        board.repaint();
        if (p >= 1) {
            timer.stop();
            t.shakeDx = 0;
            board.repaint();
            then.run();
        }
    });
    timer.start();
}

private static void afterDelay(int ms, Runnable r) {
    javax.swing.Timer t = new javax.swing.Timer(ms, e -> r.run());
    t.setRepeats(false);
    t.start();
}

// One enemy's opportunity attack: pick a melee attack (then roll) or let the mover go. Whatever happens,
// closing the prompt or the attack roll resumes the movement; there's no separate "done" step.
private void offerOpportunityAttack(Tok mover, Tok e, Runnable resume) {
    boolean[] handled = {false};
    Runnable once = () -> {
        if (handled[0]) return;
        handled[0] = true;
        resume.run();
    };
    JPanel body = new JPanel();
    body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
    body.setOpaque(false);
    JPanel head = new JPanel(new BorderLayout(10, 0));
    head.setOpaque(false);
    head.setAlignmentX(Component.LEFT_ALIGNMENT);
    head.add(new Tokens.Preview(e.style, e.label, 48, teamColor(e.team)), BorderLayout.WEST);
    JLabel intro = new JLabel("<html><div style='width:340px'><b>" + App.escapeHtml(mover.label) + "</b> is leaving <b>"
            + App.escapeHtml(e.label) + "</b>'s reach (" + e.meleeReach() + " ft.). " + App.escapeHtml(e.label)
            + " can use its <span style='color:#9b6bdf'>Reaction</span> to make one melee attack first.</div></html>");
    intro.setForeground(App.TEXT_LIGHT);
    intro.setFont(new Font("SansSerif", Font.PLAIN, 13));
    App.clickable(intro, () -> App.showInfoOverlay(intro, "Opportunity Attack", "Reaction",
            "You can make an opportunity attack when a creature you can see leaves your reach using its action, Bonus Action, "
                    + "Reaction or movement. You use your Reaction to make one melee attack with a weapon or an Unarmed Strike against the provoking creature, "
                    + "just before it leaves your reach. Taking the Disengage action prevents this, and so does being moved without using movement "
                    + "(teleporting, being pushed, or dragging a token here).", null));
    head.add(intro, BorderLayout.CENTER);
    body.add(head);
    body.add(Box.createRigidArea(new Dimension(0, 12)));
    JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
    btns.setOpaque(false);
    btns.setAlignmentX(Component.LEFT_ALIGNMENT);
    Runnable[] close = new Runnable[1];
    for (WeaponRules.Attack a : e.attacks) {
        if (!a.melee) continue;
        JButton b = economyButton(a.name + "  " + WeaponRules.signed(a.toHit), REACTION_COLOR, "Reaction: " + a.damageText() + " · " + a.reachText());
        b.addActionListener(ev -> {
            handled[0] = true; // the attack roll's closing resumes the movement instead
            close[0].run();
            e.reactionsUsed++;
            TargetCheck tc = new TargetCheck();
            tc.distance = distanceFt(e, mover);
            tc.notes.add("Opportunity attack (uses " + e.label + "'s Reaction).");
            int[] fx = CombatConditions.attackEffects(e.conditions, mover.conditions, tc.distance, tc.notes);
            if (fx[0] == 1) tc.advantage = true;
            if (fx[1] == 1) tc.disadvantage = true;
            if (fx[2] == 1) tc.autoCrit = true;
            if (mover.dodging) {
                tc.disadvantage = true;
                tc.notes.add(mover.label + " is Dodging: Disadvantage.");
            }
            if (a.heavyPenalty) tc.disadvantage = true;
            persistSoon();
            showAttackRoll(board, e, mover, a, tc, true, resume);
        });
        btns.add(b);
    }
    JButton skip = Tokens.smallButton("Don't attack", "Keep the Reaction and let " + mover.label + " go");
    skip.addActionListener(ev -> close[0].run());
    btns.add(skip);
    body.add(btns);
    close[0] = App.showSheetOverlay(board, "Opportunity Attack", body, null, once);
}

// ---------------- Dash / Disengage / Dodge ----------------
// Rogues (level 2+) can Dash or Disengage as a Bonus Action (Cunning Action)
private static boolean cunningAction(Tok t) {
    if (!PC.equals(t.kind) || t.source == null) return false;
    Integer lv = CharacterStorage.recClassLevels(t.source).get("Rogue");
    return lv != null && lv >= 2;
}

// Uses the action, or (Cunning Action) the Bonus Action. Returns what was used, or null if neither is free.
private String spendForAction(Tok t, boolean bonusAllowed) {
    if (!actionUsed) {
        actionUsed = true;
        attackAction = false;
        return "action";
    }
    if (bonusAllowed && cunningAction(t) && !bonusAttackUsed) {
        bonusAttackUsed = true;
        return "Bonus Action (Cunning Action)";
    }
    return null;
}

private void doDash(Tok t) {
    String used = spendForAction(t, true);
    if (used == null) return;
    moveLeft += t.speed;
    setStatus(t.label + " Dashes (" + used + "): +" + t.speed + " ft., " + moveLeft + " ft. of movement left.");
    if (moveMode) computeReach();
    persistSoon();
    refreshAll();
}

private void doDisengage(Tok t) {
    String used = spendForAction(t, true);
    if (used == null) return;
    disengaged = true;
    setStatus(t.label + " Disengages (" + used + "): moving this turn won't provoke opportunity attacks.");
    persistSoon();
    refreshAll();
}

private void doDodge(Tok t) {
    if (spendForAction(t, false) == null) return;
    t.dodging = true;
    setStatus(t.label + " Dodges: attacks against it have Disadvantage until its next turn, and it has Advantage on DEX saves.");
    persistSoon();
    refreshAll();
}

// ===============================================================
// Attacks: range, line of sight, rolls
// ===============================================================
// A new turn: action, Bonus Action, movement extras and the active creature's Reaction / Dodge reset
private void startTurnActions() {
    attacksLeft = active != null ? Math.max(1, active.attacksPerAction) : 0;
    bonusAttackUsed = false;
    actionUsed = false;
    attackAction = false;
    disengaged = false;
    targeting = null;
    if (active != null) {
        active.reactionsUsed = 0; // Reactions come back at the start of your turn
        active.dodging = false;      // Dodge lasts until the start of your next turn
    }
}

private static boolean hasOffHandAttack(Tok t) {
    for (WeaponRules.Attack a : t.attacks) if (a.offHand) return true;
    return false;
}

// Main attacks: the first one takes the action (Attack); the rest of the Attack action's attacks follow
private boolean canMainAttack() {
    return attacksLeft > 0 && (attackAction || !actionUsed);
}

// The Light-property extra attack needs an attack made this turn (with the Attack action) first
private boolean canBonusAttack(Tok t) {
    return hasOffHandAttack(t) && !bonusAttackUsed && attackAction && attacksLeft < Math.max(1, t.attacksPerAction);
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

// {blocked (1/0), cover (1 = half cover from a window / low wall)} along the line between the creatures' centers
private int[] lineOfSight(Tok a, Tok b) {
    return Lighting.sight(model, a.x + a.size / 2.0, a.y + a.size / 2.0, b.x + b.size / 2.0, b.y + b.size / 2.0);
}

// ===============================================================
// Light & vision (Lighting.java)
// ===============================================================
// The light a token carries: the brightest light item a character holds, or an NPC's torch; null for none
private static int[] carriedLight(Tok t) {
    if (OBJ.equals(t.kind)) return null;
    if (NPC.equals(t.kind)) return t.carriesTorch ? Lighting.TORCH_LIGHT : null;
    if (t.source == null) return null;
    WeaponRules.Hands h = WeaponRules.hands(combatRec(t));
    int[] best = null;
    for (String item : new String[]{h.main, h.off}) {
        int[] l = Lighting.heldLight(item);
        if (l != null && (best == null || l[0] + l[1] > best[0] + best[1])) best = l;
    }
    return best;
}

private static String carriedLightName(Tok t) {
    if (NPC.equals(t.kind)) return t.carriesTorch ? "Torch" : "";
    if (t.source == null) return "";
    WeaponRules.Hands h = WeaponRules.hands(combatRec(t));
    if (Lighting.heldLight(h.main) != null) return h.main;
    if (Lighting.heldLight(h.off) != null) return h.off;
    return "";
}

// Light level of every square right now: map light + mounted torches + carried lights
private int[][] lightLevels() {
    List<Lighting.Source> sources = Lighting.torchSources(model);
    for (Tok t : tokens) {
        int[] l = carriedLight(t);
        if (l != null) sources.add(new Lighting.Source(t.x, t.y, t.size, l[0], l[1]));
    }
    StringBuilder key = new StringBuilder().append(System.identityHashCode(model)).append('|').append(model.ambient);
    for (Lighting.Source s : sources) key.append('|').append(s.key());
    for (int[] row : model.hEdges) key.append(Arrays.hashCode(row));
    for (int[] row : model.vEdges) key.append(Arrays.hashCode(row));
    for (int[] row : model.light) key.append(Arrays.hashCode(row));
    String k = key.toString();
    if (lightCache == null || !k.equals(lightKey) || lightCache.length != model.height) {
        lightCache = Lighting.compute(model, sources);
        lightKey = k;
    }
    return lightCache;
}

static int darkvisionOf(Tok t) {
    if (t.source == null || OBJ.equals(t.kind)) return 0;
    return PC.equals(t.kind) ? SpeciesTraits.darkvision(t.source) : CustomAssets.intVal(t.source, "darkvision", 0);
}

// Best light level `viewer` perceives anywhere in `target`'s footprint (Darkvision applied), ignoring walls
private int perceivedLevel(Tok viewer, Tok target) {
    int[][] lv = lightLevels();
    int dv = darkvisionOf(viewer), best = Lighting.DARK;
    for (int y = target.y; y < target.y + target.size && y < model.height; y++) {
        for (int x = target.x; x < target.x + target.size && x < model.width; x++) {
            int d = Lighting.gapFt(viewer.x, viewer.y, viewer.size, x, y);
            best = Math.min(best, Lighting.perceived(lv[y][x], d, dv));
        }
    }
    return best;
}

// Light level of the brightest square a token occupies (as the map is lit, not as anyone sees it)
private int levelAt(Tok t) {
    int[][] lv = lightLevels();
    int best = Lighting.DARK;
    for (int y = t.y; y < t.y + t.size && y < model.height; y++) {
        for (int x = t.x; x < t.x + t.size && x < model.width; x++) best = Math.min(best, lv[y][x]);
    }
    return best;
}

// Can `viewer` see `target`? Needs line of sight, and the target mustn't be in Darkness (to the viewer's eyes).
// Blinded / Invisible are handled by CombatConditions.
private boolean canSee(Tok viewer, Tok target) {
    if (OBJ.equals(viewer.kind)) return false;
    if (lineOfSight(viewer, target)[0] == 1) return false;
    return perceivedLevel(viewer, target) < Lighting.DARK;
}

// ===============================================================
// Mounted torches (map objects that can be attacked)
// ===============================================================
private static final Map<String, Integer> TORCH_IMMUNITIES = new LinkedHashMap<>();
static {
    TORCH_IMMUNITIES.put("Poison", DamageRules.IMMUNE);
    TORCH_IMMUNITIES.put("Psychic", DamageRules.IMMUNE);
}

// A stand-in target for attacking a mounted torch (AC, HP and immunities from the map; never saved as a token)
private static Tok torchTarget(MapEditorPanel.Torch tr) {
    Tok t = new Tok();
    t.kind = OBJ;
    t.uid = "torch:" + tr.x + "," + tr.y;
    t.label = "Mounted Torch";
    t.sourceName = "Mounted Torch";
    t.x = tr.x;
    t.y = tr.y;
    t.size = 1;
    t.ac = tr.ac;
    t.hp = tr.hp;
    t.maxHp = tr.maxHp;
    t.reactionsPerRound = 0;
    t.sourceMods = new LinkedHashMap<>(TORCH_IMMUNITIES);
    t.fixture = tr;
    return t;
}

// Intact torches as attack targets
private List<Tok> torchTargets() {
    List<Tok> out = new ArrayList<>();
    for (MapEditorPanel.Torch tr : model.torches) if (tr.hp > 0) out.add(torchTarget(tr));
    return out;
}

// After damage to a torch stand-in: write its HP back to the map (at 0 it's destroyed and stops shedding light)
private void syncFixture(Tok t) {
    if (t.fixture == null) return;
    t.fixture.hp = Math.max(0, t.hp);
    if (asset != null) asset.put("grid", model.toJson());
    lightCache = null;
    board.repaint();
}

private void saveTorch(MapEditorPanel.Torch tr) {
    if (asset != null) asset.put("grid", model.toJson());
    lightCache = null;
    persistNow();
    refreshAll();
}

// Torch details: AC / HP / status, light or put it out, repair, and set its HP
private void showTorchDetail(Component anchor, MapEditorPanel.Torch tr) {
    JPanel body = new JPanel();
    body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
    body.setOpaque(false);
    Runnable[] close = new Runnable[1];
    JLabel info = new JLabel("<html><div style='width:400px'>" + App.escapeHtml(Lighting.MOUNTED_TORCH_INFO)
            + (inCombat ? "<br><br>To attack it, choose an attack on the Current Turn tab and click the torch." : "") + "</div></html>");
    info.setForeground(App.TEXT_LIGHT);
    info.setFont(new Font("SansSerif", Font.PLAIN, 13));
    info.setAlignmentX(Component.LEFT_ALIGNMENT);
    body.add(info);
    body.add(Box.createRigidArea(new Dimension(0, 10)));
    JPanel tiles = new JPanel(new GridLayout(1, 3, 6, 0));
    tiles.setOpaque(false);
    tiles.setAlignmentX(Component.LEFT_ALIGNMENT);
    tiles.setMaximumSize(new Dimension(420, 52));
    tiles.add(statTile(String.valueOf(tr.ac), "AC", () -> App.showInfoOverlay(tiles, "Armor Class", "Mounted Torch",
            "An attack hits the torch if its roll equals or beats this. Set in the map editor's Light layer.", null)));
    tiles.add(statTile(tr.hp + " / " + tr.maxHp, "HIT POINTS", () -> App.showInfoOverlay(tiles, "Hit Points", "Mounted Torch",
            "Damage from attacks lowers this. At 0 the torch is destroyed and its light goes out. It is immune to Poison and Psychic damage.", null)));
    tiles.add(statTile(Lighting.torchStatus(tr), "STATUS", () -> App.showInfoOverlay(tiles, Lighting.torchStatus(tr), "Mounted Torch",
            tr.hp <= 0 ? "Destroyed: it sheds no light. Repair it below to restore it." : tr.lit
                    ? "Lit: Bright Light in a 20-foot radius and Dim Light for an additional 20 feet." : "Unlit: it sheds no light until lit again.", null)));
    body.add(tiles);
    body.add(Box.createRigidArea(new Dimension(0, 10)));
    JLabel imm = muted("Immune to Poison and Psychic damage.");
    App.clickable(imm, () -> App.showInfoOverlay(imm, "Damage Immunity", "Mounted Torch", DamageRules.INFO, null));
    body.add(imm);
    body.add(Box.createRigidArea(new Dimension(0, 10)));

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    buttons.setOpaque(false);
    buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
    if (tr.hp > 0) {
        JButton toggle = Tokens.smallButton(tr.lit ? "Put Out" : "Light It", tr.lit ? "Extinguish the torch" : "Light the torch");
        toggle.addActionListener(e -> {
            tr.lit = !tr.lit;
            close[0].run();
            saveTorch(tr);
            setStatus("The mounted torch is " + (tr.lit ? "lit." : "put out."));
        });
        buttons.add(toggle);
    }
    if (tr.hp < tr.maxHp) {
        JButton repair = Tokens.smallButton("Repair", "Restore it to full Hit Points" + (tr.hp <= 0 ? " (relit)" : ""));
        repair.addActionListener(e -> {
            if (tr.hp <= 0) tr.lit = true;
            tr.hp = tr.maxHp;
            close[0].run();
            saveTorch(tr);
            setStatus("The mounted torch is repaired.");
        });
        buttons.add(repair);
    }
    JSpinner hpSpin = new JSpinner(new SpinnerNumberModel(Math.max(0, tr.hp), 0, tr.maxHp, 1));
    hpSpin.setPreferredSize(new Dimension(64, 28));
    styleSpinner(hpSpin);
    JButton setHp = Tokens.smallButton("Set HP", "Set its current Hit Points");
    setHp.addActionListener(e -> {
        commit(hpSpin);
        tr.hp = (Integer) hpSpin.getValue();
        close[0].run();
        saveTorch(tr);
        setStatus(tr.hp <= 0 ? "The mounted torch is destroyed." : "Mounted torch: " + tr.hp + " / " + tr.maxHp + " HP.");
    });
    buttons.add(muted2("HP:"));
    buttons.add(hpSpin);
    buttons.add(setHp);
    body.add(buttons);
    close[0] = App.showSheetOverlay(anchor, "Mounted Torch (" + colName(tr.x) + (tr.y + 1) + ")", body, null);
}

static final class TargetCheck {
    boolean ok = true, disadvantage, advantage, ranged, autoCrit;
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
    // light: attacking what you can't see has Disadvantage; a target that can't see you is attacked with Advantage
    int targetLight = levelAt(target);
    if (perceivedLevel(att, target) >= Lighting.DARK) {
        tc.disadvantage = true;
        tc.notes.add(target.label + " is in Darkness and you can't see it: Disadvantage"
                + (darkvisionOf(att) > 0 ? " (beyond your " + darkvisionOf(att) + " ft. Darkvision)." : "."));
    } else if (targetLight == Lighting.DARK) {
        tc.notes.add("Darkvision: you see " + target.label + " in the Darkness as if it were Dim Light.");
    }
    if (!OBJ.equals(target.kind) && !canSee(target, att)) {
        tc.advantage = true;
        tc.notes.add(target.label + " can't see you in the Darkness: Advantage.");
    }
    int[] condFx = CombatConditions.attackEffects(att.conditions, target.conditions, tc.distance, tc.notes);
    if (condFx[0] == 1) tc.advantage = true;
    if (condFx[1] == 1) tc.disadvantage = true;
    if (condFx[2] == 1) tc.autoCrit = true;
    if (target.dodging) {
        tc.disadvantage = true;
        tc.notes.add(target.label + " is Dodging: attacks against it have Disadvantage.");
    }
    int mask = target.damageMods().getOrDefault(DamageRules.normalizeType(atk.damageType), 0);
    if (mask != 0) tc.notes.add(target.label + " is " + DamageRules.levelName(mask).toLowerCase() + " to " + atk.damageType.toLowerCase() + " damage.");
    if (target.down()) {
        tc.advantage = true;
        tc.notes.add(target.label + " is down (0 HP): attacks have Advantage" + (tc.distance <= 5 ? ", and a hit from within 5 ft. is a Critical Hit." : "."));
        if (tc.distance <= 5) tc.autoCrit = true;
    }
    return tc;
}

static final int NPC_DEFAULT_AMMO = 20;

// Ammunition a token starts with: what the character carries in Equipment, or NPC_DEFAULT_AMMO for NPCs
// (NPC stat blocks don't list ammunition; adjust it in the token's details)
private static int startingAmmo(Tok t, String ammo) {
    if (!PC.equals(t.kind)) return NPC_DEFAULT_AMMO;
    int n = 0;
    if (t.source != null) {
        for (String e : CharacterStorage.recList(t.source, "equipment")) {
            AmmoItem a = WeaponData.findAmmo(e);
            if (a != null && a.name.equalsIgnoreCase(ammo)) n += App.itemCount(e);
        }
    }
    return n;
}

// Re-copies every ammunition count from the source (used when combat starts)
private void resetAmmo(Tok t) {
    t.ammo.clear();
    for (WeaponRules.Attack a : t.attacks) if (!a.ammo.isEmpty()) t.ammo.put(a.ammo, startingAmmo(t, a.ammo));
}

// Ammunition left for this token in the encounter (-1 = the attack doesn't use any)
private int ammoCount(Tok t, String ammo) {
    if (ammo == null || ammo.isEmpty()) return -1;
    return t.ammo.getOrDefault(ammo, 0);
}

// Uses (-1) or gives back (+1) ammunition in the encounter's count (the character sheet isn't changed)
private void changeAmmo(Tok t, String ammo, int delta) {
    t.ammo.put(ammo, Math.max(0, t.ammo.getOrDefault(ammo, 0) + delta));
}

static Map<String, Integer> intMap(Object o) {
    Map<String, Integer> out = new LinkedHashMap<>();
    if (o instanceof Map) {
        for (Map.Entry<?, ?> en : ((Map<?, ?>) o).entrySet()) {
            if (en.getValue() instanceof Number) out.put(String.valueOf(en.getKey()), ((Number) en.getValue()).intValue());
        }
    }
    return out;
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
        boolean usable = a.offHand ? canBonusAttack(t) : canMainAttack();
        int ammo = a.ammo.isEmpty() ? -1 : ammoCount(t, a.ammo);
        boolean ammoOk = ammo != 0 || a.melee; // thrown / melee weapons with ammo text still work in melee
        String why = !usable ? (a.offHand ? (bonusAttackUsed ? "Bonus Action already used this turn." : "Attack with a Light weapon first.") : actionUsed && !attackAction ? "Your action is already used this turn." : "No attacks left this turn.")
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
    if (PC.equals(t.kind) && t.source != null) {
        list.add(muted(handsText(t) + ". Attacks come from what " + t.label + " holds."));
        list.add(Box.createRigidArea(new Dimension(0, 4)));
        JButton swap = Tokens.smallButton("Draw / swap weapons", "Change what each hand holds for this encounter (Use Item)");
        swap.setAlignmentX(Component.LEFT_ALIGNMENT);
        swap.addActionListener(e -> {
            close[0].run();
            showUseItem(anchor, t);
        });
        list.add(swap);
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
    if (animating) return;
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
// `reaction`: an opportunity attack (the caller has spent the attacker's Reaction; no action or attack is used)
private void showAttackRoll(Component anchor, Tok att, Tok target, WeaponRules.Attack atk, TargetCheck tc, boolean reaction) {
    showAttackRoll(anchor, att, target, atk, tc, reaction, null);
}

// `afterClose` runs once the roll overlay closes (e.g. to resume a movement after an opportunity attack)
private void showAttackRoll(Component anchor, Tok att, Tok target, WeaponRules.Attack atk, TargetCheck tc, boolean reaction, Runnable afterClose) {
    // the attack is made: spend it (Undo gives it back)
    final boolean usedBonus = !reaction && atk.offHand;
    final boolean startedAttackAction = !reaction && !usedBonus && !attackAction;
    if (usedBonus) bonusAttackUsed = true;
    else if (!reaction) {
        attacksLeft = Math.max(0, attacksLeft - 1);
        if (startedAttackAction) {
            actionUsed = true;   // taking the Attack action
            attackAction = true;
        }
    }
    final boolean usedAmmo = tc.ranged && !atk.ammo.isEmpty() && ammoCount(att, atk.ammo) > 0;
    if (usedAmmo) changeAmmo(att, atk.ammo, -1);
    Map<String, Integer> targetMods = target.damageMods();
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
    JLabel autoNote = muted2("Damage is applied to " + target.label + " when you press Done (rolled for you if you haven't).");
    autoNote.setAlignmentX(Component.LEFT_ALIGNMENT);
    autoNote.setBorder(new EmptyBorder(4, 0, 0, 0));
    body.add(autoNote); // apply / graze stay as hidden helpers that Done triggers

    JLabel masteryLbl = new JLabel();
    masteryLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
    masteryLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
    masteryLbl.setForeground(App.TEXT_LIGHT);
    masteryLbl.setBorder(new EmptyBorder(8, 0, 0, 0));
    body.add(masteryLbl);

    rollDmg.addActionListener(e -> {
        boolean hitNow = natural[0] == 20 || (natural[0] != 1 && natural[0] + atk.toHit >= effectiveAC);
        boolean crit = natural[0] == 20 || (tc.autoCrit && hitNow);
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
        int taken = DamageRules.apply(dmgTotal[0], atk.damageType, targetMods);
        String why = DamageRules.explain(dmgTotal[0], atk.damageType, targetMods);
        target.hp = Math.max(0, target.hp - taken);
        applied[0] = true;
        setStatus(att.label + " hits " + target.label + " for " + taken + " " + atk.damageType.toLowerCase() + " damage"
                + (why.isEmpty() ? "" : " [" + why + "]")
                + (target.down() ? (OBJ.equals(target.kind) ? ". The torch is destroyed and its light goes out!" : ". " + target.label + " is down!")
                        : " (" + target.hp + " / " + target.maxHp + " HP)."));
        syncFixture(target);
        persistSoon();
        refreshAll();
        render[0].run();
    });
    graze.addActionListener(e -> {
        if (applied[0]) return;
        int g = DamageRules.apply(Math.max(0, atk.abilityMod), atk.damageType, targetMods);
        target.hp = Math.max(0, target.hp - g);
        applied[0] = true;
        setStatus("Graze: " + target.label + " takes " + g + " " + atk.damageType.toLowerCase() + " damage on the miss.");
        syncFixture(target);
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
        if (reaction) att.reactionsUsed = Math.max(0, att.reactionsUsed - 1);
        else if (usedBonus) bonusAttackUsed = false;
        else {
            attacksLeft++;
            if (startedAttackAction) {
                actionUsed = false;
                attackAction = false;
            }
        }
        if (usedAmmo) changeAmmo(att, atk.ammo, 1);
        close[0].run();
        setStatus("Attack undone.");
        persistSoon();
        refreshAll();
    });
    JButton done = App.overlayButton("Done", true);
    done.addActionListener(e -> {
        // apply automatically: a hit deals its damage (rolled now if needed); a Graze miss deals the ability modifier
        boolean hitNow = natural[0] == 20 || (natural[0] != 1 && natural[0] + atk.toHit >= effectiveAC);
        if (!applied[0] && hitNow) {
            if (dmgTotal[0] < 0) rollDmg.doClick();
            apply.doClick();
        } else if (!applied[0] && "Graze".equals(atk.mastery) && atk.abilityMod > 0) {
            graze.doClick();
        } else if (!applied[0]) {
            setStatus(att.label + "'s attack on " + target.label + " misses.");
        }
        close[0].run();
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
        boolean fumble = natural[0] == 1;
        int total = natural[0] + atk.toHit;
        boolean hit = natural[0] == 20 || (!fumble && total >= effectiveAC);
        boolean crit = natural[0] == 20 || (tc.autoCrit && hit);
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
        if (applied[0]) done.setText("Done");
        else if (hit) done.setText(dmgTotal[0] < 0 ? "Done: Roll & Apply Damage" : "Done: Apply " + DamageRules.apply(dmgTotal[0], atk.damageType, targetMods) + " Damage");
        else if ("Graze".equals(atk.mastery) && atk.abilityMod > 0) done.setText("Done: Apply Graze (" + DamageRules.apply(atk.abilityMod, atk.damageType, targetMods) + ")");
        else done.setText("Done");
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
            dmgLbl.setText(dmgTotal[0] + " " + atk.damageType.toLowerCase() + " damage (your roll)" + modText(dmgTotal[0], atk.damageType, targetMods));
        } else {
            StringBuilder sb = new StringBuilder();
            for (int v : dmgDice[0]) sb.append(sb.length() == 0 ? "" : " + ").append(v);
            dmgLbl.setText((dmgDice[0].length > 0 ? "[" + sb + "]" : "") + (atk.damageBonus != 0 || dmgDice[0].length == 0 ? " " + (atk.damageBonus >= 0 && dmgDice[0].length > 0 ? "+ " : "") + atk.damageBonus : "")
                    + " = " + dmgTotal[0] + " " + atk.damageType.toLowerCase() + " damage" + modText(dmgTotal[0], atk.damageType, targetMods));
        }
        int mask = targetMods.getOrDefault(DamageRules.normalizeType(atk.damageType), 0);
        apply.setText(mask == 0 || dmgTotal[0] < 0 ? "Apply Damage" : "Apply " + DamageRules.apply(dmgTotal[0], atk.damageType, targetMods) + " Damage");
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
    close[0] = App.showSheetOverlay(anchor, "Attack Roll", body, null, afterClose);
}

// " → target is resistant: takes 6" (empty when the target has no modifier for this type)
private static String modText(int amount, String type, Map<String, Integer> mods) {
    int mask = type == null || type.isEmpty() ? 0 : mods.getOrDefault(DamageRules.normalizeType(type), 0);
    if (mask == 0) return "";
    return "  →  " + DamageRules.levelName(mask).toLowerCase() + ": takes " + DamageRules.apply(amount, type, mods);
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
// Turn economy display, reactions, conditions, items
// ===============================================================
static final Color ACTION_COLOR = new Color(0x3F, 0xA3, 0x4D);   // green: takes your action
static final Color BONUS_COLOR = new Color(0xE0, 0x8A, 0x2E);    // orange: takes your Bonus Action
static final Color REACTION_COLOR = new Color(0x9B, 0x6B, 0xDF); // purple: takes a Reaction
static final Color MOVE_COLOR = new Color(0x4F, 0x9D, 0xE8);     // blue: movement (no action)

// Circles for one resource: filled = still available, outline = used
private static class Pips extends JComponent {
    final String label, status;
    final Color color;
    final int total, available;

    Pips(String label, Color color, int total, int available, String status) {
        this.label = label;
        this.color = color;
        this.total = Math.max(0, total);
        this.available = available;
        this.status = status;
        setPreferredSize(new Dimension(100, 48));
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (isOpaque()) {
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
        }
        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(color);
        g.drawString(label, (getWidth() - fm.stringWidth(label)) / 2, 11);
        int d = 14, gapPx = 5, n = Math.max(1, total);
        int w = n * d + (n - 1) * gapPx, x0 = (getWidth() - w) / 2, y = 16;
        for (int i = 0; i < n; i++) {
            int x = x0 + i * (d + gapPx);
            if (total == 0) {
                g.setColor(App.TEXT_MUTED);
                g.drawLine(x + 2, y + d / 2, x + d - 2, y + d / 2);
                continue;
            }
            if (i < available) {
                g.setColor(color);
                g.fillOval(x, y, d, d);
            } else {
                g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 160));
                g.setStroke(new BasicStroke(2f));
                g.drawOval(x + 1, y + 1, d - 2, d - 2);
            }
        }
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        fm = g.getFontMetrics();
        g.setColor(App.TEXT_MUTED);
        String s = status.length() > 20 ? status.substring(0, 19) + "…" : status;
        g.drawString(s, (getWidth() - fm.stringWidth(s)) / 2, y + d + 13);
        g.dispose();
    }
}

private JComponent economyPips(String label, Color color, int total, int available, String status, Runnable onClick) {
    Pips p = new Pips(label, color, total, available, status);
    p.setToolTipText(label.charAt(0) + label.substring(1).toLowerCase() + ": " + available + " of " + total + " available (filled = available)");
    App.clickable(p, onClick);
    return p;
}

// A button whose colored stripe and text show what it costs (green action, orange Bonus Action, purple Reaction)
static JButton economyButton(String text, Color color, String tip) {
    JButton b = new JButton(text) {
        @Override
        public void setEnabled(boolean on) {
            super.setEnabled(on);
            setForeground(on ? color.brighter() : App.TEXT_MUTED);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createCompoundBorder(new LineBorder(on ? color : App.SHEET_BORDER, 1, true),
                            BorderFactory.createMatteBorder(0, 5, 0, 0, on ? color : App.SHEET_BORDER)),
                    new EmptyBorder(6, 8, 6, 8)));
        }
    };
    b.setToolTipText(tip);
    b.setFont(new Font("SansSerif", Font.BOLD, 13));
    b.setBackground(App.SHEET_ROW_ALT);
    b.setOpaque(true);
    b.setFocusPainted(false);
    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    b.setEnabled(true);
    b.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseEntered(MouseEvent e) { if (b.isEnabled()) b.setBackground(App.SHEET_HOVER); }
        @Override
        public void mouseExited(MouseEvent e) { b.setBackground(App.SHEET_ROW_ALT); }
    });
    return b;
}

private JComponent economyLegend(Tok t) {
    JLabel l = new JLabel("<html><span style='color:#3fa34d'>●</span> Action &nbsp; <span style='color:#e08a2e'>●</span> Bonus Action &nbsp; "
            + "<span style='color:#9b6bdf'>●</span> Reaction &nbsp; <span style='color:#4f9de8'>●</span> Movement</html>");
    l.setForeground(App.TEXT_MUTED);
    l.setFont(new Font("SansSerif", Font.PLAIN, 11));
    App.clickable(l, () -> showEconomyInfo(t));
    return l;
}

private void showEconomyInfo(Tok t) {
    App.showInfoOverlay(sideContent, "Your Turn", t.label,
            "On your turn you can move up to your Speed and take one action (green): Attack, Dash (extra movement equal to your Speed), "
                    + "Disengage (your movement doesn't provoke opportunity attacks this turn), Dodge (until your next turn, attacks against you "
                    + "have Disadvantage and you have Advantage on Dexterity saving throws) or Utilize an item.\n\n"
                    + "Bonus Action (orange): only when something gives you one, like the Light-weapon extra attack, drinking a healing potion "
                    + "or a Rogue's Cunning Action.\n\n"
                    + "Reaction (purple): an instant response, like an opportunity attack, usable even on other creatures' turns. "
                    + "It comes back at the start of your turn.\n\n"
                    + "Filled circles are still available; empty circles are used.",
            Arrays.asList(App.row("Action", actionUsed && t == active ? "used" : "available"),
                    App.row("Bonus Action", bonusAttackUsed && t == active ? "used" : "available"),
                    App.row("Reactions", Math.max(0, t.reactionsPerRound - t.reactionsUsed) + " of " + t.reactionsPerRound + " left")));
}

// Reactions can be spent any time (e.g. on someone else's turn): use / restore, and set how many per round
private void showReactionEditor(Component anchor, Tok t) {
    JPanel body = new JPanel();
    body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
    body.setOpaque(false);
    JLabel intro = new JLabel("<html><div style='width:360px'>A Reaction is an instant response to a trigger, such as an opportunity attack "
            + "or a spell like <i>Shield</i>, and can happen on any creature's turn. " + App.escapeHtml(t.label)
            + "'s Reactions come back at the start of its turn.</div></html>");
    intro.setForeground(App.TEXT_LIGHT);
    intro.setFont(new Font("SansSerif", Font.PLAIN, 13));
    intro.setAlignmentX(Component.LEFT_ALIGNMENT);
    body.add(intro);
    body.add(Box.createRigidArea(new Dimension(0, 10)));
    JPanel holder = new JPanel(new BorderLayout());
    holder.setOpaque(false);
    holder.setAlignmentX(Component.LEFT_ALIGNMENT);
    Runnable[] render = new Runnable[1];
    render[0] = () -> {
        holder.removeAll();
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        Pips p = new Pips("REACTIONS", REACTION_COLOR, t.reactionsPerRound, Math.max(0, t.reactionsPerRound - t.reactionsUsed),
                Math.max(0, t.reactionsPerRound - t.reactionsUsed) + " of " + t.reactionsPerRound + " left");
        p.setPreferredSize(new Dimension(130, 48));
        row.add(p);
        JButton use = economyButton("Use one", REACTION_COLOR, "Mark a Reaction as used");
        use.setEnabled(t.reactionsUsed < t.reactionsPerRound);
        use.addActionListener(e -> {
            t.reactionsUsed++;
            persistSoon();
            refreshAll();
            render[0].run();
        });
        JButton restore = Tokens.smallButton("Restore", "Give a Reaction back");
        restore.setEnabled(t.reactionsUsed > 0);
        restore.addActionListener(e -> {
            t.reactionsUsed = Math.max(0, t.reactionsUsed - 1);
            persistSoon();
            refreshAll();
            render[0].run();
        });
        row.add(use);
        row.add(restore);
        holder.add(row, BorderLayout.NORTH);
        JPanel per = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        per.setOpaque(false);
        JLabel pl = new JLabel("Reactions per round");
        pl.setForeground(App.TEXT_MUTED);
        JSpinner sp = new JSpinner(new SpinnerNumberModel(t.reactionsPerRound, 0, 10, 1));
        sp.setPreferredSize(new Dimension(56, 28));
        styleSpinner(sp);
        sp.setToolTipText("Most creatures have 1; some monsters and features give more");
        sp.addChangeListener(e -> {
            t.reactionsPerRound = (Integer) sp.getValue();
            t.reactionsUsed = Math.min(t.reactionsUsed, t.reactionsPerRound);
            persistSoon();
            refreshAll();
            SwingUtilities.invokeLater(render[0]);
        });
        per.add(pl);
        per.add(sp);
        holder.add(per, BorderLayout.SOUTH);
        if (CombatConditions.incapacitated(t.conditions)) {
            JLabel no = new JLabel(t.label + " is " + CombatConditions.incapacitatedBy(t.conditions) + " and can't take Reactions.");
            no.setForeground(App.ACCENT_HOVER);
            holder.add(no, BorderLayout.CENTER);
        }
        holder.revalidate();
        holder.repaint();
    };
    render[0].run();
    body.add(holder);
    App.showSheetOverlay(anchor, "Reactions: " + t.label, body, null);
}

// Condition chips: click for the rules; with `editable`, ✕ removes and + adds (combat-only, never the sheet)
private JPanel conditionChips(Tok t, boolean editable) {
    JPanel p = new JPanel(new AssetLibraryPanel.WrapLayout(FlowLayout.LEFT, 4, 4));
    p.setOpaque(false);
    p.setAlignmentX(Component.LEFT_ALIGNMENT);
    for (String c : new ArrayList<>(t.conditions)) {
        JPanel chip = new JPanel(new BorderLayout(4, 0));
        chip.setBackground(App.SHEET_ROW_ALT);
        chip.setBorder(BorderFactory.createCompoundBorder(new LineBorder(new Color(0xE0, 0xA0, 0x30), 1, true), new EmptyBorder(1, 6, 1, 4)));
        JLabel l = new JLabel(c);
        l.setForeground(new Color(0xF0, 0xC0, 0x60));
        l.setFont(new Font("SansSerif", Font.BOLD, 11));
        chip.add(l, BorderLayout.CENTER);
        if (editable) {
            JButton x = new JButton("✕");
            x.setFont(new Font("SansSerif", Font.BOLD, 10));
            x.setForeground(App.TEXT_MUTED);
            x.setContentAreaFilled(false);
            x.setBorderPainted(false);
            x.setFocusPainted(false);
            x.setBorder(new EmptyBorder(0, 2, 0, 0));
            x.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            x.setToolTipText("Remove " + c);
            x.addActionListener(e -> {
                t.conditions.remove(c);
                persistSoon();
                refreshAll();
                p.remove(chip);
                p.revalidate();
                p.repaint();
            });
            chip.add(x, BorderLayout.EAST);
        }
        App.clickable(chip, () -> App.showInfoOverlay(chip, c, "Condition · " + t.label, CombatConditions.info(c)
                + "\n\nTracked for this encounter only (the character sheet isn't changed).", null));
        p.add(chip);
    }
    return p;
}

private void showConditionPicker(Component anchor, Tok t, Runnable after) {
    JPanel grid = new JPanel(new GridLayout(0, 3, 6, 6));
    grid.setOpaque(false);
    Runnable[] close = new Runnable[1];
    for (String c : CombatConditions.options()) {
        if (t.conditions.contains(c)) continue;
        JButton b = App.overlayButton(c, false);
        b.setToolTipText(CombatConditions.info(c));
        b.addActionListener(e -> {
            t.conditions.add(c);
            if (CombatConditions.incapacitated(t.conditions) && t == active && moveMode) setMoveMode(false);
            close[0].run();
            setStatus(t.label + " is now " + c + ".");
            persistSoon();
            refreshAll();
            after.run();
        });
        grid.add(b);
    }
    if (grid.getComponentCount() == 0) {
        JLabel all = new JLabel("Every condition is already applied.");
        all.setForeground(App.TEXT_MUTED);
        grid.add(all);
    }
    close[0] = App.showSheetOverlay(anchor, "Add Condition: " + t.label, grid, null);
}

// Use Item: a healing potion is a Bonus Action (roll to heal); anything else takes the action (Utilize)
private void showUseItem(Component anchor, Tok t) {
    JPanel list = new JPanel();
    list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
    list.setOpaque(false);
    Runnable[] close = new Runnable[1];
    boolean myTurn = inCombat && t == active;
    JLabel intro = new JLabel("<html><div style='width:440px'>Items " + App.escapeHtml(t.label) + " can use in this encounter"
            + (PC.equals(t.kind) ? " (copied from Equipment; using one here doesn't change the character sheet)" : "") + ".</div></html>");
    intro.setForeground(App.TEXT_MUTED);
    intro.setFont(new Font("SansSerif", Font.PLAIN, 12));
    intro.setAlignmentX(Component.LEFT_ALIGNMENT);
    list.add(intro);
    list.add(Box.createRigidArea(new Dimension(0, 8)));
    if (PC.equals(t.kind) && t.source != null) {
        addHandsSection(list, anchor, t, close);
        list.add(sectionCaption("OTHER ITEMS"));
    }
    if (t.items.isEmpty()) list.add(muted("No usable items. Add one below (e.g. a Potion of Healing the DM hands out)."));
    for (Map.Entry<String, Integer> en : new ArrayList<>(t.items.entrySet())) {
        String item = en.getKey();
        int[] potion = CombatItems.potion(item);
        boolean bonus = potion != null;
        boolean free = !myTurn || (bonus ? !bonusAttackUsed : !actionUsed);
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(520, 56));
        String desc = App.itemDescription(item);
        JLabel l = new JLabel("<html><b>" + App.escapeHtml(item) + "</b> ×" + en.getValue() + "<br><span style='color:#aaa5a0'>"
                + App.escapeHtml(bonus ? "Bonus Action · heals " + WeaponData.diceText(potion[0], potion[1], potion[2]) : "Action (Utilize)"
                + (CombatItems.consumable(item) ? " · used up" : "")) + "</span></html>");
        l.setForeground(App.TEXT_LIGHT);
        l.setFont(new Font("SansSerif", Font.PLAIN, 12));
        App.clickable(l, () -> App.showInfoOverlay(l, item, bonus ? "Potion · Bonus Action" : "Item · Utilize action",
                desc == null || desc.isEmpty() ? "No description." : desc, null));
        row.add(l, BorderLayout.CENTER);
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 6));
        btns.setOpaque(false);
        JButton use = economyButton(bonus ? "Drink / Give" : "Use", bonus ? BONUS_COLOR : ACTION_COLOR,
                !free ? (bonus ? "Bonus Action already used this turn" : "Action already used this turn")
                        : bonus ? "Bonus Action: drink it, or give it to a creature within 5 ft." : "Action (Utilize)");
        use.setEnabled(free && en.getValue() > 0);
        use.addActionListener(e -> {
            close[0].run();
            if (bonus) showPotion(anchor, t, item, potion);
            else useGenericItem(t, item);
        });
        JButton minus = Tokens.smallButton("−", "Remove one from this encounter's items");
        minus.addActionListener(e -> {
            int n = t.items.getOrDefault(item, 0) - 1;
            if (n <= 0) t.items.remove(item);
            else t.items.put(item, n);
            persistSoon();
            close[0].run();
            showUseItem(anchor, t);
        });
        btns.add(use);
        btns.add(minus);
        row.add(btns, BorderLayout.EAST);
        list.add(row);
        list.add(Box.createRigidArea(new Dimension(0, 4)));
    }
    // add an item for this encounter
    list.add(Box.createRigidArea(new Dimension(0, 8)));
    list.add(sectionCaption("ADD AN ITEM FOR THIS ENCOUNTER"));
    JPanel add = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    add.setOpaque(false);
    add.setAlignmentX(Component.LEFT_ALIGNMENT);
    List<String> presets = new ArrayList<>(CombatItems.POTIONS.keySet());
    for (String k : App.ITEM_PRESETS.keySet()) {
        if (!presets.contains(k) && WeaponData.find(k) == null && WeaponData.findAmmo(k) == null && GameData.ARMOR_AC.get(k) == null) presets.add(k);
    }
    for (Map<String, Object> hb : CustomAssets.all(CustomAssets.ITEMS)) {
        String n = CustomAssets.str(hb, "name");
        if (!presets.contains(n) && Tags.allowedAsset(CustomAssets.ITEMS, hb)) presets.add(n);
    }
    presets.removeIf(n -> !Tags.allowed(CustomAssets.ITEMS, n));
    JComboBox<String> pick = new JComboBox<>(presets.toArray(new String[0]));
    App.styleCombo(pick);
    pick.setEditable(true);
    pick.setFont(new Font("SansSerif", Font.PLAIN, 13));
    pick.setPreferredSize(new Dimension(230, 30));
    JSpinner qty = new JSpinner(new SpinnerNumberModel(1, 1, 99, 1));
    qty.setPreferredSize(new Dimension(54, 28));
    styleSpinner(qty);
    JButton addB = Tokens.smallButton("Add", "Add to this token's items (this encounter only)");
    addB.addActionListener(e -> {
        Object v = pick.getEditor().getItem();
        String name = v == null ? "" : String.valueOf(v).trim();
        if (name.isEmpty()) return;
        commit(qty);
        t.items.merge(name, (Integer) qty.getValue(), Integer::sum);
        persistSoon();
        close[0].run();
        refreshSide();
        showUseItem(anchor, t);
    });
    add.add(pick);
    add.add(qty);
    add.add(addB);
    list.add(add);
    JScrollPane sp = new JScrollPane(list);
    App.applyDarkScrollBar(sp);
    sp.setBorder(null);
    sp.setOpaque(false);
    sp.getViewport().setOpaque(false);
    sp.setPreferredSize(new Dimension(560, Math.min(460, list.getPreferredSize().height + 12)));
    sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    close[0] = App.showSheetOverlay(anchor, "Use Item: " + t.label, sp, null);
}

// Use Item's HANDS part: what each hand holds now (with Stow), then every weapon and shield from Equipment with
// buttons to put it in Hand 1, Hand 2 or both hands. Combat only: the sheet's grip is left alone.
private void addHandsSection(JPanel list, Component anchor, Tok t, Runnable[] close) {
    List<String> equipment = CharacterStorage.recList(t.source, "equipment");
    WeaponRules.Hands h = WeaponRules.hands(combatRec(t));
    Runnable reopen = () -> {
        close[0].run();
        showUseItem(anchor, t);
    };
    list.add(sectionCaption("HANDS"));
    String[][] held = h.twoHanded && !h.main.isEmpty() ? new String[][]{{"Both hands", h.main, "1"}}
            : new String[][]{{"Hand 1", h.main, "1"}, {"Hand 2", h.off, "2"}};
    for (String[] hd : held) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(520, 34));
        JLabel l = new JLabel("<html><span style='color:#aaa5a0'>" + hd[0] + ":</span> <b>" + App.escapeHtml(hd[1].isEmpty() ? "empty" : hd[1]) + "</b></html>");
        l.setForeground(App.TEXT_LIGHT);
        l.setFont(new Font("SansSerif", Font.PLAIN, 13));
        String item = hd[1];
        App.clickable(l, () -> App.showInfoOverlay(l, item.isEmpty() ? "Empty Hand" : item, hd[0],
                item.isEmpty() ? "Nothing held. A free hand can hold a spellcasting focus or component, grapple, or draw an item."
                        : handItemInfo(item), null));
        row.add(l, BorderLayout.CENTER);
        if (!item.isEmpty()) {
            JButton stow = Tokens.smallButton("Stow", "Put it away (this encounter only)");
            int which = Integer.parseInt(hd[2]);
            stow.addActionListener(e -> {
                if (which == 1 && h.twoHanded) equipHand(t, "", 3);
                else equipHand(t, "", which);
                reopen.run();
            });
            JPanel b = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
            b.setOpaque(false);
            b.add(stow);
            row.add(b, BorderLayout.EAST);
        }
        list.add(row);
    }
    // weapons and shields the character owns
    List<String> holdable = new ArrayList<>();
    for (String e : equipment) {
        String base = GameData.stripEquipmentCount(e);
        boolean light = Lighting.heldLight(base) != null; // Torch, Lamp, Hooded Lantern, Candle
        if ((WeaponData.find(base) != null || WeaponRules.isShield(base) || light) && holdable.stream().noneMatch(base::equalsIgnoreCase)) holdable.add(base);
    }
    list.add(Box.createRigidArea(new Dimension(0, 6)));
    if (holdable.isEmpty()) {
        list.add(muted("No weapons, shields or light sources in " + t.label + "'s Equipment. Add them on the character sheet."));
    }
    for (String item : holdable) {
        WeaponEntry w = WeaponData.find(item);
        int owned = WeaponRules.countOf(equipment, item);
        boolean pair = owned >= 2;
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(520, 52));
        int[] lightOf = Lighting.heldLight(item);
        String sub = w != null ? w.damage + (w.versatile() ? " (" + w.damageText(true) + " two-handed)" : "") + (w.twoHanded() ? " · Two-Handed" : "") + (w.light() ? " · Light" : "")
                : lightOf != null ? "Light source: " + Lighting.heldLightText(lightOf)
                : "+" + GameData.shieldBonus(GameData.ARMOR_AC.get(item)) + " AC while held";
        JLabel l = new JLabel("<html><b>" + App.escapeHtml(item) + "</b>" + (owned > 1 ? " ×" + owned : "") + "<br><span style='color:#aaa5a0'>"
                + App.escapeHtml(sub) + "</span></html>");
        l.setForeground(App.TEXT_LIGHT);
        l.setFont(new Font("SansSerif", Font.PLAIN, 12));
        App.clickable(l, () -> App.showInfoOverlay(l, item, w != null ? w.category + " Weapon" : lightOf != null ? "Light source" : "Shield", handItemInfo(item), null));
        row.add(l, BorderLayout.CENTER);
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 6));
        btns.setOpaque(false);
        boolean inMain = item.equalsIgnoreCase(h.main), inOff = item.equalsIgnoreCase(h.off);
        boolean twoOnly = w != null && w.twoHanded();
        if (!twoOnly) {
            JButton h1 = Tokens.smallButton("Hand 1", "Hold it in your main hand");
            h1.setEnabled(!(inMain && !h.twoHanded));
            h1.addActionListener(e -> { equipHand(t, item, 1); reopen.run(); });
            JButton h2 = Tokens.smallButton("Hand 2", "Hold it in your off hand");
            h2.setEnabled(!inOff);
            h2.addActionListener(e -> { equipHand(t, item, 2); reopen.run(); });
            btns.add(h1);
            btns.add(h2);
        }
        boolean twoGrip = w != null && (w.twoHanded() || w.versatile());
        if (twoGrip || pair && w != null) {
            JButton both = Tokens.smallButton("Both", twoGrip ? (twoOnly ? "Two-Handed: it takes both hands" : "Wield it with both hands (Versatile damage)")
                    : "One in each hand");
            both.setEnabled(twoGrip ? !(inMain && h.twoHanded) : !(inMain && inOff));
            both.addActionListener(e -> { equipHand(t, item, 3); reopen.run(); });
            btns.add(both);
        }
        row.add(btns, BorderLayout.EAST);
        list.add(row);
        list.add(Box.createRigidArea(new Dimension(0, 2)));
    }
    JLabel note = new JLabel("<html><div style='width:400px'>Drawing or stowing a weapon is part of an attack or your free object "
            + "interaction, so it doesn't use an action here. Only this encounter changes; the sheet keeps its own grip.</div></html>");
    note.setForeground(App.TEXT_MUTED);
    note.setFont(new Font("SansSerif", Font.PLAIN, 11));
    note.setAlignmentX(Component.LEFT_ALIGNMENT);
    list.add(Box.createRigidArea(new Dimension(0, 4)));
    list.add(note);
    list.add(Box.createRigidArea(new Dimension(0, 12)));
}

private static String handItemInfo(String item) {
    WeaponEntry w = WeaponData.find(item);
    if (w != null) return w.infoText();
    String d = App.itemDescription(item);
    return d == null || d.isEmpty() ? "No description." : d;
}

private void useGenericItem(Tok t, String item) {
    if (inCombat && t == active) spendForAction(t, false);
    boolean usedUp = CombatItems.consumable(item);
    if (usedUp) {
        int n = t.items.getOrDefault(item, 0) - 1;
        if (n <= 0) t.items.remove(item);
        else t.items.put(item, n);
    }
    setStatus(t.label + " uses " + item + (inCombat && t == active ? " (Utilize action)" : "") + (usedUp ? "; it's used up." : "."));
    persistSoon();
    refreshAll();
}

// Healing potion: pick who drinks it (self or a creature within 5 ft.), roll the healing (reroll or set), apply on Done
private void showPotion(Component anchor, Tok user, String item, int[] dice) {
    JPanel body = new JPanel();
    body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
    body.setOpaque(false);
    Runnable[] close = new Runnable[1];
    List<Tok> targets = new ArrayList<>();
    targets.add(user);
    for (Tok o : tokens) if (o != user && distanceFt(user, o) <= 5) targets.add(o);
    JLabel intro = new JLabel("<html><div style='width:420px'>As a <b style='color:#e08a2e'>Bonus Action</b>, " + App.escapeHtml(user.label)
            + " drinks the " + App.escapeHtml(item) + " or gives it to a creature within 5 ft. The drinker regains "
            + WeaponData.diceText(dice[0], dice[1], dice[2]) + " Hit Points.</div></html>");
    intro.setForeground(App.TEXT_LIGHT);
    intro.setFont(new Font("SansSerif", Font.PLAIN, 13));
    intro.setAlignmentX(Component.LEFT_ALIGNMENT);
    body.add(intro);
    body.add(Box.createRigidArea(new Dimension(0, 10)));
    JPanel whoRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    whoRow.setOpaque(false);
    whoRow.setAlignmentX(Component.LEFT_ALIGNMENT);
    JLabel wl = new JLabel("Drinker");
    wl.setForeground(App.TEXT_MUTED);
    String[] names = new String[targets.size()];
    for (int i = 0; i < targets.size(); i++) {
        Tok o = targets.get(i);
        names[i] = (o == user ? o.label + " (self)" : o.label) + "  ·  " + o.hp + " / " + o.maxHp + " HP";
    }
    JComboBox<String> who = new JComboBox<>(names);
    App.styleCombo(who);
    who.setFont(new Font("SansSerif", Font.PLAIN, 13));
    whoRow.add(wl);
    whoRow.add(who);
    body.add(whoRow);
    body.add(Box.createRigidArea(new Dimension(0, 10)));

    int[][] rolled = {new int[0]};
    int[] total = {0};
    boolean[] replaced = {false};
    JLabel result = new JLabel();
    result.setFont(new Font("SansSerif", Font.BOLD, 16));
    result.setForeground(new Color(0x7C, 0xD3, 0x8A));
    result.setAlignmentX(Component.LEFT_ALIGNMENT);
    Runnable roll = () -> {
        rolled[0] = WeaponRules.rollDice(rng, dice[0], dice[1], false);
        int s = dice[2];
        for (int v : rolled[0]) s += v;
        total[0] = s;
        replaced[0] = false;
    };
    Runnable render = () -> {
        StringBuilder sb = new StringBuilder();
        for (int v : rolled[0]) sb.append(sb.length() == 0 ? "" : " + ").append(v);
        result.setText(replaced[0] ? "Heals " + total[0] + " HP (your roll)" : dice[0] + "d" + dice[1] + " [" + sb + "] + " + dice[2] + " = heals " + total[0] + " HP");
    };
    roll.run();
    render.run();
    body.add(result);
    JPanel rr = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
    rr.setOpaque(false);
    rr.setAlignmentX(Component.LEFT_ALIGNMENT);
    JButton reroll = Tokens.smallButton("🎲 Reroll", "Roll the healing again");
    reroll.addActionListener(e -> {
        roll.run();
        render.run();
    });
    JSpinner own = new JSpinner(new SpinnerNumberModel(dice[0] * dice[1] / 2 + dice[2], 0, 999, 1));
    own.setPreferredSize(new Dimension(64, 28));
    styleSpinner(own);
    JButton useOwn = Tokens.smallButton("Use total", "Replace the healing with the total you rolled at the table");
    useOwn.addActionListener(e -> {
        commit(own);
        total[0] = (Integer) own.getValue();
        replaced[0] = true;
        render.run();
    });
    rr.add(reroll);
    rr.add(muted2("or set:"));
    rr.add(own);
    rr.add(useOwn);
    body.add(rr);
    body.add(Box.createRigidArea(new Dimension(0, 12)));
    JPanel foot = new JPanel(new BorderLayout());
    foot.setOpaque(false);
    foot.setAlignmentX(Component.LEFT_ALIGNMENT);
    JButton cancel = App.overlayButton("Cancel", false);
    cancel.addActionListener(e -> close[0].run());
    JButton done = App.overlayButton("Done: Heal", true);
    done.setToolTipText("Uses the Bonus Action and the potion, and heals the drinker");
    done.addActionListener(e -> {
        Tok drinker = targets.get(Math.max(0, who.getSelectedIndex()));
        int before = drinker.hp;
        drinker.hp = Math.min(drinker.maxHp, drinker.hp + total[0]);
        if (before <= 0 && drinker.hp > 0) drinker.conditions.remove("Unconscious");
        if (inCombat && user == active) bonusAttackUsed = true;
        int n = user.items.getOrDefault(item, 0) - 1;
        if (n <= 0) user.items.remove(item);
        else user.items.put(item, n);
        close[0].run();
        setStatus((drinker == user ? user.label + " drinks " : user.label + " gives " + drinker.label + " ") + item + ": +"
                + (drinker.hp - before) + " HP (" + drinker.hp + " / " + drinker.maxHp + ").");
        persistSoon();
        refreshAll();
    });
    foot.add(cancel, BorderLayout.WEST);
    foot.add(done, BorderLayout.EAST);
    body.add(foot);
    close[0] = App.showSheetOverlay(anchor, item, body, done);
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
    // damage modifiers (NPC traits + combat-only adjustments)
    body.add(sectionCaption("DAMAGE MODIFIERS"));
    JPanel modRow = new JPanel(new BorderLayout(8, 0));
    modRow.setOpaque(false);
    modRow.setAlignmentX(Component.LEFT_ALIGNMENT);
    modRow.setMaximumSize(new Dimension(460, 40));
    String modSummary = DamageRules.summary(t.damageMods());
    JLabel modLbl = new JLabel("<html><div style='width:320px'>" + App.escapeHtml(modSummary.isEmpty() ? "None: takes normal damage of every type." : modSummary) + "</div></html>");
    modLbl.setForeground(modSummary.isEmpty() ? App.TEXT_MUTED : App.TEXT_LIGHT);
    modLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
    App.clickable(modLbl, () -> App.showInfoOverlay(modLbl, "Damage Modifiers", t.label,
            DamageRules.INFO + (NPC.equals(t.kind) ? "\n\nNPC traits with a Damage Vulnerability / Resistance / Immunity type set these; edit them in Assets → NPCs."
                    : "\n\nA character starts with its species' resistances (for example, a Dwarf resists Poison damage)."), null));
    modRow.add(modLbl, BorderLayout.CENTER);
    Runnable[] reopen = new Runnable[1];
    JButton modEdit = Tokens.smallButton("Adjust", "Change this token's resistances, vulnerabilities and immunities for this encounter");
    modRow.add(modEdit, BorderLayout.EAST);
    body.add(modRow);
    body.add(Box.createRigidArea(new Dimension(0, 10)));

    // light & vision
    body.add(sectionCaption("LIGHT & VISION"));
    int here = levelAt(t), dv = darkvisionOf(t);
    String lightName = carriedLightName(t);
    String visionText = "Standing in " + Lighting.NAMES[here] + "  ·  " + (dv > 0 ? "Darkvision " + dv + " ft." : "No Darkvision")
            + (lightName.isEmpty() ? "" : "  ·  Carrying: " + lightName);
    JLabel visionLbl = new JLabel("<html><div style='width:420px'>" + App.escapeHtml(visionText) + "</div></html>");
    visionLbl.setForeground(App.TEXT_LIGHT);
    visionLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
    visionLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
    List<String[]> visionRows = new ArrayList<>();
    visionRows.add(App.row("Light here", Lighting.NAMES[here]));
    visionRows.add(App.row("Darkvision", dv > 0 ? dv + " ft. (" + (PC.equals(t.kind) ? "species" : "NPC stat block") + ")" : "none"));
    int[] carried = carriedLight(t);
    if (carried != null) visionRows.add(App.row("Light carried", lightName + ": " + Lighting.heldLightText(carried)));
    App.clickable(visionLbl, () -> App.showInfoOverlay(visionLbl, "Light & Vision", t.label, Lighting.INFO[here]
            + (dv > 0 ? "\n\n" + SpeciesTraits.DARKVISION_INFO : "")
            + (PC.equals(t.kind) ? "\n\nTo carry a light, put a Torch, Lamp, Hooded Lantern or Candle in a hand with Use Item → Hands."
                    : "\n\nSet an NPC's Darkvision in its stat block (Assets → NPCs)."), visionRows));
    body.add(visionLbl);
    if (NPC.equals(t.kind)) {
        JCheckBox torchBox = new JCheckBox("Carrying a lit torch (" + Lighting.heldLightText(Lighting.TORCH_LIGHT) + ")", t.carriesTorch);
        torchBox.setOpaque(false);
        torchBox.setForeground(App.TEXT_LIGHT);
        torchBox.setFont(new Font("SansSerif", Font.PLAIN, 12));
        torchBox.setFocusPainted(false);
        torchBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        torchBox.addActionListener(e -> {
            t.carriesTorch = torchBox.isSelected();
            persistSoon();
            board.repaint();
            refreshSide();
        });
        body.add(torchBox);
    }
    body.add(Box.createRigidArea(new Dimension(0, 10)));

    // ammunition (combat-only count)
    if (!t.ammo.isEmpty()) {
        body.add(sectionCaption("AMMUNITION (THIS ENCOUNTER)"));
        JPanel ammoRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        ammoRow.setOpaque(false);
        ammoRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (String am : new ArrayList<>(t.ammo.keySet())) {
            JLabel al = new JLabel(am);
            al.setForeground(App.TEXT_LIGHT);
            al.setFont(new Font("SansSerif", Font.PLAIN, 12));
            AmmoItem item = WeaponData.findAmmo(am);
            App.clickable(al, () -> App.showInfoOverlay(al, am, "Ammunition", (item != null ? item.description() : "")
                    + "\n\nThis count is for this encounter only: it starts from " + (PC.equals(t.kind) ? "the character's Equipment" : NPC_DEFAULT_AMMO + " for NPCs")
                    + " when the token is placed and when combat starts, and using it doesn't change the character sheet.", null));
            JSpinner sp = new JSpinner(new SpinnerNumberModel((int) t.ammo.get(am), 0, 999, 1));
            sp.setPreferredSize(new Dimension(64, 28));
            styleSpinner(sp);
            sp.addChangeListener(e -> {
                t.ammo.put(am, (Integer) sp.getValue());
                persistSoon();
                refreshSide();
            });
            ammoRow.add(al);
            ammoRow.add(sp);
        }
        body.add(ammoRow);
        body.add(Box.createRigidArea(new Dimension(0, 10)));
    }
    // conditions (combat-only; any time, not just on its turn)
    JPanel condHead = new JPanel(new BorderLayout(8, 0));
    condHead.setOpaque(false);
    condHead.setAlignmentX(Component.LEFT_ALIGNMENT);
    condHead.setMaximumSize(new Dimension(460, 30));
    condHead.add(sectionCaption("CONDITIONS"), BorderLayout.WEST);
    JButton addCond = Tokens.smallButton("+ Add", "Apply a condition for this encounter");
    condHead.add(addCond, BorderLayout.EAST);
    body.add(condHead);
    if (t.conditions.isEmpty()) body.add(muted("None."));
    else body.add(conditionChips(t, true));
    body.add(Box.createRigidArea(new Dimension(0, 10)));

    // reactions (tracked on every creature, any time)
    JPanel reactRow = new JPanel(new BorderLayout(8, 0));
    reactRow.setOpaque(false);
    reactRow.setAlignmentX(Component.LEFT_ALIGNMENT);
    reactRow.setMaximumSize(new Dimension(460, 52));
    Pips rp = new Pips("REACTIONS", REACTION_COLOR, t.reactionsPerRound, Math.max(0, t.reactionsPerRound - t.reactionsUsed),
            Math.max(0, t.reactionsPerRound - t.reactionsUsed) + " of " + t.reactionsPerRound + " left");
    rp.setPreferredSize(new Dimension(130, 48));
    reactRow.add(rp, BorderLayout.WEST);
    JLabel dodgeLbl = new JLabel(t.dodging ? "Dodging: attacks against it have Disadvantage until its next turn." : "");
    dodgeLbl.setForeground(new Color(0x7C, 0xD3, 0x8A));
    dodgeLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
    reactRow.add(dodgeLbl, BorderLayout.CENTER);
    JButton reactEdit = Tokens.smallButton("Use / Restore", "Spend or restore a Reaction (works on any creature's turn)");
    reactRow.add(reactEdit, BorderLayout.EAST);
    body.add(reactRow);
    body.add(Box.createRigidArea(new Dimension(0, 10)));

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
    reopen[0] = () -> {
        close[0].run();
        refreshAll();
        showTokenDetail(anchor, t);
    };
    modEdit.addActionListener(e -> showDamageModsEditor(modEdit, t, reopen[0]));
    addCond.addActionListener(e -> showConditionPicker(addCond, t, reopen[0]));
    reactEdit.addActionListener(e -> {
        close[0].run();
        showReactionEditor(anchor, t);
    });
}

// Amount + Damage / Heal row shared by the token overlay and the Current Turn tab
private static final String UNTYPED = "Untyped";

private JPanel hpControls(Tok t, Runnable after) {
    JPanel box = new JPanel();
    box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
    box.setOpaque(false);
    box.setAlignmentX(Component.LEFT_ALIGNMENT);
    JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    row.setOpaque(false);
    row.setAlignmentX(Component.LEFT_ALIGNMENT);
    JSpinner amt = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
    amt.setPreferredSize(new Dimension(64, 28));
    amt.setToolTipText("Amount of damage or healing");
    styleSpinner(amt);
    String[] types = new String[CustomAssets.DAMAGE_TYPES.length + 1];
    types[0] = UNTYPED;
    System.arraycopy(CustomAssets.DAMAGE_TYPES, 0, types, 1, CustomAssets.DAMAGE_TYPES.length);
    JComboBox<String> type = new JComboBox<>(types);
    App.styleCombo(type);
    type.setFont(new Font("SansSerif", Font.PLAIN, 12));
    type.setPreferredSize(new Dimension(120, 28));
    type.setToolTipText("Damage type: the creature's resistances, vulnerabilities and immunities apply");
    JButton dmg = App.overlayButton("Damage", false);
    dmg.setForeground(App.ACCENT_HOVER);
    JButton heal = App.overlayButton("Heal", false);
    heal.setForeground(new Color(0x7C, 0xD3, 0x8A));
    dmg.addActionListener(e -> {
        commit(amt);
        String dt = UNTYPED.equals(type.getSelectedItem()) ? "" : String.valueOf(type.getSelectedItem());
        int raw = (Integer) amt.getValue();
        int taken = DamageRules.apply(raw, dt, t.damageMods());
        String why = DamageRules.explain(raw, dt, t.damageMods());
        t.hp = Math.max(0, t.hp - taken);
        setStatus(t.label + " takes " + taken + (dt.isEmpty() ? "" : " " + dt.toLowerCase()) + " damage" + (why.isEmpty() ? "" : " [" + why + "]")
                + (t.down() ? " and is down!" : " (" + t.hp + " / " + t.maxHp + " HP)."));
        after.run();
    });
    heal.addActionListener(e -> {
        commit(amt);
        t.hp = Math.min(t.maxHp, t.hp + (Integer) amt.getValue());
        setStatus(t.label + " heals to " + t.hp + " / " + t.maxHp + " HP.");
        after.run();
    });
    row.add(amt);
    row.add(type);
    JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
    row2.setOpaque(false);
    row2.setAlignmentX(Component.LEFT_ALIGNMENT);
    row2.add(dmg);
    row2.add(heal);
    box.add(row);
    box.add(row2);
    return box;
}

// Combat-only damage modifiers for one token: each damage type Normal / Resistant / Vulnerable / Immune.
// NPC traits set the defaults; changes here are saved with the map (never on the character sheet or the NPC).
private void showDamageModsEditor(Component anchor, Tok t, Runnable after) {
    JPanel body = new JPanel(new BorderLayout(0, 10));
    body.setOpaque(false);
    JLabel intro = new JLabel("<html><div style='width:420px'>How " + App.escapeHtml(t.label) + " takes each damage type in this encounter"
            + (NPC.equals(t.kind) ? ". Defaults come from the NPC's traits." : " (e.g. a Barbarian's Rage: Resistant to Bludgeoning, Piercing and Slashing).")
            + " Changes stay with this map, not the " + (PC.equals(t.kind) ? "character sheet." : "NPC.") + "</div></html>");
    intro.setForeground(App.TEXT_LIGHT);
    intro.setFont(new Font("SansSerif", Font.PLAIN, 12));
    App.clickable(intro, () -> App.showInfoOverlay(intro, "Damage Modifiers", "Resistance, Vulnerability, Immunity", DamageRules.INFO, null));
    body.add(intro, BorderLayout.NORTH);
    String[] levels = {"Normal", "Resistant", "Vulnerable", "Immune"};
    int[] masks = {0, DamageRules.RESIST, DamageRules.VULN, DamageRules.IMMUNE};
    JPanel grid = new JPanel(new GridLayout(0, 2, 10, 4));
    grid.setOpaque(false);
    Map<String, JComboBox<String>> combos = new LinkedHashMap<>();
    Map<String, Integer> eff = t.damageMods();
    for (String dt : CustomAssets.DAMAGE_TYPES) {
        JPanel cell = new JPanel(new BorderLayout(6, 0));
        cell.setOpaque(false);
        int source = t.sourceMods.getOrDefault(dt, 0);
        JLabel l = new JLabel(dt + (source != 0 ? " *" : ""));
        l.setForeground(App.TEXT_LIGHT);
        l.setFont(new Font("SansSerif", Font.PLAIN, 12));
        l.setPreferredSize(new Dimension(95, 24));
        if (source != 0) l.setToolTipText("NPC trait: " + DamageRules.levelName(source));
        JComboBox<String> c = new JComboBox<>(levels);
        App.styleCombo(c);
        c.setFont(new Font("SansSerif", Font.PLAIN, 12));
        int m = eff.getOrDefault(dt, 0);
        c.setSelectedIndex((m & DamageRules.IMMUNE) != 0 ? 3 : (m & DamageRules.RESIST) != 0 ? 1 : (m & DamageRules.VULN) != 0 ? 2 : 0);
        combos.put(dt, c);
        cell.add(l, BorderLayout.WEST);
        cell.add(c, BorderLayout.CENTER);
        grid.add(cell);
    }
    JPanel center = new JPanel(new BorderLayout(0, 4));
    center.setOpaque(false);
    center.add(grid, BorderLayout.CENTER);
    if (!t.sourceMods.isEmpty()) {
        JLabel star = new JLabel("* set by the NPC's traits");
        star.setForeground(App.TEXT_MUTED);
        star.setFont(new Font("SansSerif", Font.ITALIC, 11));
        center.add(star, BorderLayout.SOUTH);
    }
    body.add(center, BorderLayout.CENTER);
    Runnable[] close = new Runnable[1];
    JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    btns.setOpaque(false);
    JButton reset = App.overlayButton("Reset to Defaults", false);
    reset.addActionListener(e -> {
        t.modOverrides.clear();
        close[0].run();
        persistSoon();
        after.run();
    });
    JButton cancel = App.overlayButton("Cancel", false);
    cancel.addActionListener(e -> close[0].run());
    JButton save = App.overlayButton("Save", true);
    save.addActionListener(e -> {
        Map<String, Integer> next = new LinkedHashMap<>();
        for (Map.Entry<String, JComboBox<String>> en : combos.entrySet()) {
            int chosen = masks[en.getValue().getSelectedIndex()];
            int src = t.sourceMods.getOrDefault(en.getKey(), 0);
            // keep the trait's exact mask (e.g. Resistant + Vulnerable) when the choice matches its main level
            if (src != 0 && DamageRules.levelName(src).startsWith(levels[en.getValue().getSelectedIndex()])) continue;
            if (chosen != src) next.put(en.getKey(), chosen);
        }
        t.modOverrides = next;
        close[0].run();
        persistSoon();
        after.run();
    });
    btns.add(reset);
    btns.add(cancel);
    btns.add(save);
    body.add(btns, BorderLayout.SOUTH);
    close[0] = App.showSheetOverlay(anchor, "Damage Modifiers: " + t.label, body, null);
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
    rows.add(App.row("Default light", Lighting.NAMES[Math.max(0, Math.min(2, model.ambient))]));
    int intact = 0;
    for (MapEditorPanel.Torch tr : model.torches) if (tr.hp > 0) intact++;
    rows.add(App.row("Mounted torches", model.torches.isEmpty() ? "none" : intact + " of " + model.torches.size() + " intact"));
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
    gap(10);

    if (PC.equals(t.kind) && t.source != null) {
        addSide(sectionCaption("HANDS"));
        JLabel hl = new JLabel("<html><div style='width:280px'>" + App.escapeHtml(handsText(t)) + "</div></html>");
        hl.setForeground(App.TEXT_LIGHT);
        hl.setFont(new Font("SansSerif", Font.PLAIN, 13));
        hl.setToolTipText("What " + t.label + " is holding. Click to draw, swap or stow a weapon or shield.");
        App.clickable(hl, () -> showUseItem(hl, t));
        addSide(hl);
        gap(14);
    } else {
        gap(4);
    }

    // ---- turn economy: filled circle = still available, empty circle = used ----
    boolean incap = t.down() || CombatConditions.incapacitated(t.conditions);
    String incapWhy = t.down() ? "at 0 HP" : CombatConditions.incapacitatedBy(t.conditions);
    String still = CombatConditions.speedZeroBy(t.conditions);
    JPanel eco = new JPanel(new GridLayout(1, 3, 6, 0));
    eco.setOpaque(false);
    eco.setMaximumSize(new Dimension(400, 50));
    eco.add(economyPips("ACTION", ACTION_COLOR, 1, actionUsed ? 0 : 1,
            actionUsed ? (attackAction ? "Attack" + (attacksLeft > 0 ? " · " + attacksLeft + " left" : "") : "used") : "ready", () -> showEconomyInfo(t)));
    eco.add(economyPips("BONUS", BONUS_COLOR, 1, bonusAttackUsed ? 0 : 1, bonusAttackUsed ? "used" : "ready", () -> showEconomyInfo(t)));
    eco.add(economyPips("REACTION", REACTION_COLOR, t.reactionsPerRound, Math.max(0, t.reactionsPerRound - t.reactionsUsed),
            t.reactionsUsed >= t.reactionsPerRound ? "used" : "ready", () -> showReactionEditor(sideContent, t)));
    addSide(eco);
    gap(8);
    if (incap || !still.isEmpty() || disengaged || t.dodging) {
        List<String> notes = new ArrayList<>();
        if (incap) notes.add("<span style='color:#dc3c3c'>" + App.escapeHtml(t.label) + " is " + App.escapeHtml(incapWhy) + ": no actions, Bonus Actions or Reactions.</span>");
        if (!still.isEmpty()) notes.add("<span style='color:#e0a030'>" + App.escapeHtml(still) + ": Speed is 0.</span>");
        if (disengaged) notes.add("<span style='color:#7cd38a'>Disengaged: moving won't provoke opportunity attacks.</span>");
        if (t.dodging) notes.add("<span style='color:#7cd38a'>Dodging until its next turn.</span>");
        JLabel nl = new JLabel("<html><div style='width:280px'>" + String.join("<br>", notes) + "</div></html>");
        nl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        addSide(nl);
        gap(8);
    }

    // movement + attack
    JPanel actions = new JPanel(new GridLayout(1, 2, 8, 0));
    actions.setOpaque(false);
    JButton move = economyButton(moveMode ? "Cancel Move" : "Move", MOVE_COLOR,
            moveLeft <= 0 ? "No movement left this turn" : !still.isEmpty() ? still + ": Speed is 0" : t.down() ? "Down creatures can't move"
                    : "Highlight the squares " + t.label + " can reach (movement, not an action)");
    move.setEnabled(moveMode || (moveLeft > 0 && !t.down() && still.isEmpty()));
    move.addActionListener(e -> {
        setMoveMode(!moveMode);
        if (!moveMode) setStatus(defaultStatus());
        refreshSide();
    });
    boolean canBonus = canBonusAttack(t);
    JButton attack = economyButton(targeting != null ? "Cancel Attack" : "Attack", ACTION_COLOR,
            incap ? "Can't act: " + incapWhy : !canMainAttack() && !canBonus ? (actionUsed && !attackAction ? "Action already used this turn" : "No attacks left this turn")
                    : "Action: pick a weapon, then click a target in range");
    attack.setEnabled(targeting != null || (!incap && (canMainAttack() || canBonus)));
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

    // Dash / Disengage / Dodge / Use Item (actions; a Rogue's Cunning Action can Dash or Disengage as a Bonus Action)
    boolean cunning = cunningAction(t) && !bonusAttackUsed;
    JPanel other = new JPanel(new GridLayout(2, 2, 6, 6));
    other.setOpaque(false);
    other.setMaximumSize(new Dimension(400, 72));
    boolean dashByBonus = actionUsed && cunning;
    JButton dash = economyButton("Dash", dashByBonus ? BONUS_COLOR : ACTION_COLOR, (dashByBonus ? "Bonus Action (Cunning Action)" : "Action")
            + ": gain extra movement equal to your Speed (" + t.speed + " ft.) this turn");
    dash.setEnabled(!incap && still.isEmpty() && (!actionUsed || cunning));
    dash.addActionListener(e -> doDash(t));
    JButton dis = economyButton(disengaged ? "Disengaged" : "Disengage", dashByBonus ? BONUS_COLOR : ACTION_COLOR,
            (dashByBonus ? "Bonus Action (Cunning Action)" : "Action") + ": your movement doesn't provoke opportunity attacks for the rest of the turn");
    dis.setEnabled(!incap && !disengaged && (!actionUsed || cunning));
    dis.addActionListener(e -> doDisengage(t));
    JButton dodge = economyButton(t.dodging ? "Dodging" : "Dodge", ACTION_COLOR,
            "Action: until your next turn, attacks against you have Disadvantage and you have Advantage on DEX saves");
    dodge.setEnabled(!incap && !t.dodging && !actionUsed);
    dodge.addActionListener(e -> doDodge(t));
    int itemCount = 0;
    for (int n : t.items.values()) itemCount += n;
    JButton useItem = economyButton("Use Item" + (itemCount > 0 ? " (" + itemCount + ")" : ""), ACTION_COLOR,
            "Use an item: most take your action (Utilize); a healing potion takes a Bonus Action");
    useItem.setEnabled(!incap);
    useItem.addActionListener(e -> showUseItem(useItem, t));
    other.add(dash);
    other.add(dis);
    other.add(dodge);
    other.add(useItem);
    addSide(other);
    gap(6);
    addSide(economyLegend(t));
    gap(6);
    JLabel budget = new JLabel((actionUsed && !attackAction ? "Attack: action already used this turn" : "Attacks left: " + attacksLeft + " of " + t.attacksPerAction)
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
    if (!t.ammo.isEmpty()) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> en : t.ammo.entrySet()) parts.add(en.getKey() + ": " + en.getValue());
        JLabel ammoLbl = new JLabel("Ammunition  " + String.join("   ", parts));
        ammoLbl.setForeground(App.TEXT_LIGHT);
        ammoLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        ammoLbl.setToolTipText("Ammunition left in this encounter (click to adjust)");
        App.clickable(ammoLbl, () -> showTokenDetail(ammoLbl, t));
        addSide(ammoLbl);
        gap(8);
    }
    if (!t.conditions.isEmpty()) {
        addSide(conditionChips(t, false));
        gap(8);
    }
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
        row.setMaximumSize(new Dimension(400, t.conditions.isEmpty() ? 46 : 62));

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
                + (t.down() ? " · down" : "") + (t.dodging ? " · dodging" : "") + "</span>"
                + (t.conditions.isEmpty() ? "" : "<br><span style='color:#f0c060'>" + App.escapeHtml(String.join(", ", t.conditions)) + "</span>")
                + "</html>");
        nm.setForeground(t.down() ? App.TEXT_MUTED : App.TEXT_LIGHT);
        nm.setFont(new Font("SansSerif", Font.PLAIN, 12));
        mid.add(nm, BorderLayout.CENTER);
        App.clickable(mid, () -> showTokenDetail(mid, t));
        row.add(mid, BorderLayout.CENTER);

        // Reaction circle(s): filled = available; click to spend / restore, even on others' turns
        Pips rp = new Pips("R", REACTION_COLOR, t.reactionsPerRound, Math.max(0, t.reactionsPerRound - t.reactionsUsed), "");
        rp.setPreferredSize(new Dimension(Math.max(26, t.reactionsPerRound * 19 + 6), 36));
        rp.setToolTipText("Reactions: " + Math.max(0, t.reactionsPerRound - t.reactionsUsed) + " of " + t.reactionsPerRound + " left (click to use or restore)");
        App.clickable(rp, () -> showReactionEditor(rp, t));
        JPanel east = new JPanel(new BorderLayout(4, 0));
        east.setOpaque(false);
        east.add(rp, BorderLayout.WEST);

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
        east.add(arrows, BorderLayout.EAST);
        row.add(east, BorderLayout.EAST);
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
    addSide(toggle("Show lighting", showLight, v -> showLight = v, "Shade Dim and Dark squares. Light still affects attacks when hidden."));
    gap(10);
    addSide(sectionCaption("LIGHT"));
    gap(4);
    JPanel ambientRow = new JPanel(new BorderLayout(8, 0));
    ambientRow.setOpaque(false);
    ambientRow.setAlignmentX(Component.LEFT_ALIGNMENT);
    ambientRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
    JLabel ambientLbl = new JLabel("Whole map:");
    ambientLbl.setForeground(App.TEXT_LIGHT);
    ambientLbl.setFont(new Font("SansSerif", Font.PLAIN, 13));
    JComboBox<String> ambient = new JComboBox<>(Lighting.SHORT);
    App.styleCombo(ambient);
    ambient.setSelectedIndex(Math.max(0, Math.min(2, model.ambient)));
    ambient.setToolTipText("The map's default light. Squares painted in the map editor's Light layer and torches override it.");
    ambient.addActionListener(e -> {
        if (ambient.getSelectedIndex() == model.ambient) return;
        model.ambient = ambient.getSelectedIndex();
        if (asset != null) asset.put("grid", model.toJson());
        lightCache = null;
        persistNow();
        setStatus("The whole map is now " + Lighting.NAMES[model.ambient] + " by default.");
        board.repaint();
    });
    ambientRow.add(ambientLbl, BorderLayout.WEST);
    ambientRow.add(ambient, BorderLayout.CENTER);
    addSide(ambientRow);
    gap(4);
    JLabel lightRules = muted("Dim Light: Disadvantage on sight-based Perception. Darkness: creatures can't see "
            + "into it (attacks at Disadvantage, attacks against them at Advantage). Darkvision lifts it one step. Click for details.");
    App.clickable(lightRules, () -> App.showInfoOverlay(lightRules, "Light & Vision", "2024 rules",
            Lighting.INFO[0] + "\n\n" + Lighting.INFO[1] + "\n\n" + Lighting.INFO[2]
                    + "\n\nLight sources: mounted torches (placed in the map editor's Light layer) and creatures holding a Torch, Lamp, "
                    + "Hooded Lantern or Candle (Use Item → Hands; NPCs: Carry a torch in the token details). Walls and closed doors block light.",
            Arrays.asList(App.row("Torch", Lighting.heldLightText(Lighting.HELD_LIGHTS.get("Torch"))),
                    App.row("Lamp", Lighting.heldLightText(Lighting.HELD_LIGHTS.get("Lamp"))),
                    App.row("Hooded Lantern", Lighting.heldLightText(Lighting.HELD_LIGHTS.get("Hooded Lantern"))),
                    App.row("Candle", Lighting.heldLightText(Lighting.HELD_LIGHTS.get("Candle"))))));
    addSide(lightRules);
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

    private double drawnX(Tok t) {
        return Double.isNaN(t.drawX) ? t.x : t.drawX;
    }

    private double drawnY(Tok t) {
        return Double.isNaN(t.drawY) ? t.y : t.drawY;
    }

    private void onPress(MouseEvent e) {
        if (animating) {
            pressPoint = lastPoint = e.getPoint();
            panning = true; // the map can still be panned while a token slides
            dragTok = null;
            dragging = false;
            return;
        }
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
        if (animating) return; // wait for the slide (and any opportunity attack) to finish
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
        if (placing != null) {
            if (inMap(cx, cy)) placeAt(cx, cy);
            return;
        }
        if (targeting != null) {
            Tok target = inMap(cx, cy) ? tokenAt(cx, cy) : null;
            if (target == null && inMap(cx, cy)) {
                MapEditorPanel.Torch tr = model.torchAt(cx, cy);
                if (tr != null && tr.hp > 0) target = torchTarget(tr);
            }
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
            showAttackRoll(this, active, target, atk, tc, false);
            repaint();
            return;
        }
        Tok t = tokenAt(cx, cy);
        if (t != null) {
            showTokenDetail(this, t);
            return;
        }
        MapEditorPanel.Torch torch = torchAt(x, y);
        if (torch != null && !moveMode) {
            showTorchDetail(this, torch);
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
            int lv = lightLevels()[cy][cx];
            App.showInfoOverlay(this, MapEditorPanel.FLOOR_NAMES[fl], "Square " + colName(cx) + (cy + 1) + " • 5 ft. • " + Lighting.NAMES[lv],
                    MapEditorPanel.FLOOR_INFO[fl] + "\n\n" + Lighting.INFO[lv], null);
        }
    }

    // A mounted torch under the pointer (x, y in squares), or null
    private MapEditorPanel.Torch torchAt(double x, double y) {
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
        if (!inMap(cx, cy)) return null;
        MapEditorPanel.Torch t = model.torchAt(cx, cy);
        if (t == null) return null;
        double r = Math.max(0.3, 7 / scale);
        return Math.hypot(x - (t.x + 0.5), y - (t.y + 0.5)) <= r + 0.12 ? t : null;
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
            if (doorAt(x, y) != null || pinAt(x, y) != null || torchAt(x, y) != null) clickable = true;
            if (!panning) setCursor(Cursor.getPredefinedCursor(clickable ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        }
        if (targeting != null && hoverX >= 0) {
            Tok t = tokenAt(hoverX, hoverY);
            if (t == null) {
                MapEditorPanel.Torch tr = model.torchAt(hoverX, hoverY);
                if (tr != null && tr.hp > 0) t = torchTarget(tr);
            }
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
        if (showLight) Lighting.paintOverlay(g, lightLevels(), model, scale, offX, offY, getWidth(), getHeight());

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
        for (MapEditorPanel.Torch tr : model.torches) Lighting.paintTorch(g, tr, scale, offX, offY, tr.x == hoverX && tr.y == hoverY);
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
            paintToken(g, t, drawnX(t), drawnY(t), 1f);
        }
        for (Tok t : drawOrder) if (!(dragging && t == dragTok)) paintLabels(g, t, drawnX(t), drawnY(t));

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
        if (showLight && model.hasLighting()) Lighting.paintLegend(g, getWidth(), getHeight(), model.ambient);
        g.dispose();
    }

    // Targeting: ring every other creature green (in range), amber (long range: Disadvantage) or red (can't attack),
    // and draw a line with the distance to the hovered one
    private void paintTargets(Graphics2D g0) {
        Graphics2D g = (Graphics2D) g0.create();
        double ax = offX + (active.x + active.size / 2.0) * scale, ay = offY + (active.y + active.size / 2.0) * scale;
        Tok hovered = hoverX >= 0 ? tokenAt(hoverX, hoverY) : null;
        List<Tok> targets = new ArrayList<>(tokens);
        for (Tok tt : torchTargets()) {
            if (tokenAt(tt.x, tt.y) != null) continue; // a creature standing there is the target instead
            targets.add(tt);
            if (hovered == null && tt.x == hoverX && tt.y == hoverY) hovered = tt;
        }
        for (Tok t : targets) {
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

    private void paintToken(Graphics2D g0, Tok t, double x, double y, float alpha) {
        Graphics2D g = (Graphics2D) g0.create();
        if (alpha < 1f) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        double s = t.size * scale;
        double cx = offX + x * scale + s / 2 + t.shakeDx, cy = offY + y * scale + s / 2;
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
        if (t.alert) {
            // waiting on its opportunity attack: red ring
            g.setColor(new Color(230, 70, 70, 200));
            g.setStroke(new BasicStroke(ow * 0.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(Tokens.shape(CustomAssets.str(t.style, "shape"), cx, cy, r + ow * 1.6));
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
    private void paintLabels(Graphics2D g, Tok t, double x, double y) {
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
        String cond = t.conditions.isEmpty() || scale < 20 ? "" : String.join(", ", t.conditions) + (t.dodging ? ", Dodging" : "");
        if (cond.isEmpty() && t.dodging && scale >= 20) cond = "Dodging";
        if (cond.length() > 28) cond = cond.substring(0, 27) + "…";
        double boxW = Math.max(Math.max(names ? fm.stringWidth(name) + 10 : 0, hp ? barW + hfm.stringWidth(hpText) + 14 : 0), cond.isEmpty() ? 0 : hfm.stringWidth(cond) + 10);
        double boxH = (names ? fm.getHeight() : 0) + (hp ? 10 : 0) + (cond.isEmpty() ? 0 : hfm.getHeight()) + 4;
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
            yy += 10;
        }
        if (!cond.isEmpty()) {
            g.setFont(hf);
            g.setColor(new Color(0xF0, 0xC0, 0x60));
            g.drawString(cond, (float) (cx - hfm.stringWidth(cond) / 2.0), (float) (yy + hfm.getAscent()));
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
