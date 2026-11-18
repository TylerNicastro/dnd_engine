import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

// Built-in square-grid map editor / viewer (no external images). One square = 5 ft.
//
// Model (saved as the "grid" object of a Maps asset):
//   floor  : one string per row, one char per cell  (' ' none, S stone, W wood, G dirt/grass, ~ water, P pit/void)
//   hEdges : height+1 rows of width digits — the TOP edge of cell (x, y) is hEdges[y][x]
//   vEdges : height rows of width+1 digits — the LEFT edge of cell (x, y) is vEdges[y][x]
//            edge digits: 0 none, 1 wall, 2 closed door, 3 open door, 4 window / half cover
//   pins   : [{x, y, title, notes, npcIds: [...]}]
//
// Controls: left-drag with a tool to paint; right/middle-drag (or drag with Select) to pan; wheel to zoom;
// Ctrl+Z to undo. With Select (and in view mode), clicking a door opens/closes it and clicking a pin opens it.
class MapEditorPanel {

static final int NONE = 0, WALL = 1, DOOR_CLOSED = 2, DOOR_OPEN = 3, WINDOW = 4;

static final char[] FLOOR_CODES = {' ', 'S', 'W', 'G', '~', 'P'};
static final String[] FLOOR_NAMES = {"Clear", "Stone", "Wood", "Dirt / Grass", "Water (difficult)", "Pit / Void"};
static final Color[] FLOOR_COLORS = {
        App.SHEET_BG,
        new Color(0x3B, 0x40, 0x54),   // stone: slate
        new Color(0x5A, 0x47, 0x34),   // wood
        new Color(0x3C, 0x52, 0x3A),   // dirt / grass
        new Color(0x2B, 0x47, 0x66),   // water
        new Color(0x0E, 0x0F, 0x16)};  // pit / void
static final String[] FLOOR_INFO = {
        "No floor: unexplored or outside the map.",
        "Stone floor: normal terrain.",
        "Wooden floor: normal terrain.",
        "Dirt or grass: normal terrain outdoors.",
        "Water: Difficult Terrain (each foot of movement costs 1 extra foot). Deep water may require swimming.",
        "Pit or void: a drop. Creatures that enter may fall (1d6 bludgeoning per 10 ft. fallen)."};

private static final Color WALL_COLOR = new Color(0xD8, 0xD0, 0xBE);
private static final Color DOOR_COLOR = new Color(0x8B, 0x5A, 0x2B);
private static final Color WINDOW_COLOR = new Color(0x8F, 0xC1, 0xE3);
private static final Color GRID_COLOR = new Color(255, 255, 255, 38);
private static final Color GRID_MAJOR = new Color(255, 255, 255, 70);

// ===============================================================
// Model
// ===============================================================
static class Pin {
    int x, y;
    String title = "", notes = "";
    List<String> npcIds = new ArrayList<>();
}

static class MapModel {
    int width, height;
    int[][] floor;   // [y][x] index into FLOOR_CODES
    int[][] hEdges;  // [height+1][width]
    int[][] vEdges;  // [height][width+1]
    List<Pin> pins = new ArrayList<>();

    static MapModel blank(int w, int h) {
        MapModel m = new MapModel();
        m.width = Math.max(1, w);
        m.height = Math.max(1, h);
        m.floor = new int[m.height][m.width];
        for (int[] row : m.floor) Arrays.fill(row, 1); // stone
        m.hEdges = new int[m.height + 1][m.width];
        m.vEdges = new int[m.height][m.width + 1];
        return m;
    }

    Map<String, Object> toJson() {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("width", width);
        o.put("height", height);
        o.put("squareFeet", 5);
        List<Object> f = new ArrayList<>();
        for (int[] row : floor) {
            StringBuilder sb = new StringBuilder();
            for (int v : row) sb.append(FLOOR_CODES[Math.max(0, Math.min(FLOOR_CODES.length - 1, v))]);
            f.add(sb.toString());
        }
        o.put("floor", f);
        o.put("hEdges", digits(hEdges));
        o.put("vEdges", digits(vEdges));
        List<Object> p = new ArrayList<>();
        for (Pin pin : pins) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("x", pin.x);
            pm.put("y", pin.y);
            pm.put("title", pin.title);
            pm.put("notes", pin.notes);
            pm.put("npcIds", new ArrayList<Object>(pin.npcIds));
            p.add(pm);
        }
        o.put("pins", p);
        return o;
    }

    private static List<Object> digits(int[][] grid) {
        List<Object> out = new ArrayList<>();
        for (int[] row : grid) {
            StringBuilder sb = new StringBuilder();
            for (int v : row) sb.append((char) ('0' + Math.max(0, Math.min(9, v))));
            out.add(sb.toString());
        }
        return out;
    }

    static MapModel fromJson(Object json) {
        if (!(json instanceof Map)) return blank(20, 15);
        Map<?, ?> o = (Map<?, ?>) json;
        MapModel m = blank(Math.max(1, CustomAssets.intVal(o, "width", 20)), Math.max(1, CustomAssets.intVal(o, "height", 15)));
        Object f = o.get("floor");
        if (f instanceof List) {
            List<?> rows = (List<?>) f;
            for (int y = 0; y < m.height && y < rows.size(); y++) {
                String row = String.valueOf(rows.get(y));
                for (int x = 0; x < m.width && x < row.length(); x++) {
                    int idx = new String(FLOOR_CODES).indexOf(row.charAt(x));
                    m.floor[y][x] = Math.max(0, idx);
                }
            }
        }
        readDigits(o.get("hEdges"), m.hEdges);
        readDigits(o.get("vEdges"), m.vEdges);
        Object p = o.get("pins");
        if (p instanceof List) {
            for (Object po : (List<?>) p) {
                if (!(po instanceof Map)) continue;
                Map<?, ?> pm = (Map<?, ?>) po;
                Pin pin = new Pin();
                pin.x = CustomAssets.intVal(pm, "x", 0);
                pin.y = CustomAssets.intVal(pm, "y", 0);
                if (pin.x < 0 || pin.y < 0 || pin.x >= m.width || pin.y >= m.height) continue;
                pin.title = CustomAssets.str(pm, "title");
                pin.notes = CustomAssets.str(pm, "notes");
                pin.npcIds = CustomAssets.list(pm, "npcIds");
                m.pins.add(pin);
            }
        }
        return m;
    }

    private static void readDigits(Object json, int[][] into) {
        if (!(json instanceof List)) return;
        List<?> rows = (List<?>) json;
        for (int y = 0; y < into.length && y < rows.size(); y++) {
            String row = String.valueOf(rows.get(y));
            for (int x = 0; x < into[y].length && x < row.length(); x++) {
                int v = row.charAt(x) - '0';
                into[y][x] = (v >= 0 && v <= WINDOW) ? v : NONE;
            }
        }
    }

    // New size, keeping everything that still fits (new area gets stone floor)
    MapModel resized(int w, int h) {
        MapModel n = blank(w, h);
        for (int y = 0; y < Math.min(h, height); y++) {
            for (int x = 0; x < Math.min(w, width); x++) n.floor[y][x] = floor[y][x];
        }
        for (int y = 0; y <= Math.min(h, height); y++) {
            for (int x = 0; x < Math.min(w, width); x++) n.hEdges[y][x] = hEdges[y][x];
        }
        for (int y = 0; y < Math.min(h, height); y++) {
            for (int x = 0; x <= Math.min(w, width); x++) n.vEdges[y][x] = vEdges[y][x];
        }
        for (Pin p : pins) if (p.x < w && p.y < h) n.pins.add(p);
        return n;
    }

    // edge = {orientation (0 horizontal, 1 vertical), x, y}
    int getEdge(int[] e) {
        return e[0] == 0 ? hEdges[e[2]][e[1]] : vEdges[e[2]][e[1]];
    }

    void setEdge(int[] e, int v) {
        if (e[0] == 0) hEdges[e[2]][e[1]] = v;
        else vEdges[e[2]][e[1]] = v;
    }

    Pin pinAt(int x, int y) {
        for (Pin p : pins) if (p.x == x && p.y == y) return p;
        return null;
    }
}

// ===============================================================
// Panel
// ===============================================================
final JPanel panel = new JPanel(new BorderLayout());

private final CardLayout outerLayout;
private final JPanel outerCards;
private String returnCard = "ASSETS";
private Runnable onClose = () -> { };

private Map<String, Object> asset; // the Maps asset being shown
private MapModel model = MapModel.blank(20, 15);
private boolean editMode;

private final Canvas canvas = new Canvas();
private final JLabel titleLabel = new JLabel();
private final JLabel statusLabel = new JLabel(" ");
private final JLabel savedLabel = new JLabel(" ");
private final JPanel toolRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
private final JButton undoButton = new JButton("↶ Undo");
private final JButton modeButton = new JButton();
private final Deque<String> undoStack = new ArrayDeque<>();

static final String SELECT = "Select", T_WALL = "Wall", T_DOOR = "Door", T_WINDOW = "Window",
        T_ERASE = "Erase", T_FLOOR = "Floor", T_PIN = "Pin";
private static final String[][] TOOLS = {
        {SELECT, "Select / pan: click doors to open or close them, click pins for details, drag to pan."},
        {T_WALL, "Click or drag along square edges to draw walls. Starting on an existing wall erases instead."},
        {T_DOOR, "Click or drag along edges to place doors. Click an existing door to open or close it."},
        {T_WINDOW, "Click or drag along edges to place windows / half cover."},
        {T_ERASE, "Click or drag along edges to remove walls, doors and windows."},
        {T_FLOOR, "Click or drag over squares to paint the selected floor type."},
        {T_PIN, "Click a square to place a location or encounter pin (or edit the one there)."}};
private String tool = SELECT;
private int floorBrush = 1;
private final Map<String, JToggleButton> toolButtons = new LinkedHashMap<>();
private final JComboBox<String> floorCombo = new JComboBox<>(FLOOR_NAMES);
private final JSpinner widthSpin = new JSpinner(new SpinnerNumberModel(20, 4, 120, 1));
private final JSpinner heightSpin = new JSpinner(new SpinnerNumberModel(15, 4, 120, 1));

MapEditorPanel(CardLayout outerLayout, JPanel outerCards) {
    this.outerLayout = outerLayout;
    this.outerCards = outerCards;
    panel.setBackground(App.SHEET_BG);

    // ---- top bar ----
    JPanel top = new JPanel(new BorderLayout());
    top.setBackground(App.BG_PANEL);
    top.setBorder(new EmptyBorder(8, 14, 4, 14));

    JButton back = new JButton("← Back");
    App.styleSecondaryButton(back);
    back.setBorder(BorderFactory.createCompoundBorder(new LineBorder(App.TEXT_MUTED, 1, true), new EmptyBorder(6, 12, 6, 12)));
    back.addActionListener(e -> close());

    titleLabel.setForeground(App.GOLD);
    titleLabel.setFont(new Font("Serif", Font.BOLD, 24));
    titleLabel.setBorder(new EmptyBorder(0, 16, 0, 16));
    titleLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    titleLabel.setToolTipText("Map details");
    titleLabel.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseReleased(MouseEvent e) {
            if (App.isClickRelease(e)) showMapInfo();
        }
        @Override
        public void mouseEntered(MouseEvent e) { titleLabel.setForeground(App.ACCENT_HOVER); }
        @Override
        public void mouseExited(MouseEvent e) { titleLabel.setForeground(App.GOLD); }
    });

    JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    left.setOpaque(false);
    left.add(back);
    left.add(titleLabel);
    savedLabel.setForeground(App.TEXT_MUTED);
    savedLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
    left.add(savedLabel);
    top.add(left, BorderLayout.WEST);

    JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
    right.setOpaque(false);
    styleSmall(undoButton);
    undoButton.setToolTipText("Undo the last change (Ctrl+Z)");
    undoButton.addActionListener(e -> undo());
    JButton zoomOut = smallButton("−", "Zoom out");
    zoomOut.addActionListener(e -> canvas.zoomBy(1 / 1.25, canvas.getWidth() / 2.0, canvas.getHeight() / 2.0));
    JButton zoomIn = smallButton("+", "Zoom in");
    zoomIn.addActionListener(e -> canvas.zoomBy(1.25, canvas.getWidth() / 2.0, canvas.getHeight() / 2.0));
    JButton fit = smallButton("Fit", "Fit the whole map in view");
    fit.addActionListener(e -> canvas.fit());
    styleSmall(modeButton);
    modeButton.addActionListener(e -> setEditMode(!editMode));
    right.add(undoButton);
    right.add(zoomOut);
    right.add(zoomIn);
    right.add(fit);
    right.add(modeButton);
    top.add(right, BorderLayout.EAST);

    // ---- tool row ----
    toolRow.setBackground(App.BG_PANEL);
    ButtonGroup group = new ButtonGroup();
    for (String[] t : TOOLS) {
        JToggleButton b = new JToggleButton(t[0]);
        b.setToolTipText(t[1]);
        b.setFocusPainted(false);
        b.setFont(new Font("SansSerif", Font.BOLD, 13));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> setTool(t[0]));
        group.add(b);
        toolButtons.put(t[0], b);
    }
    App.styleCombo(floorCombo);
    floorCombo.setSelectedIndex(1);
    floorCombo.setToolTipText("Floor type to paint");
    floorCombo.addActionListener(e -> {
        floorBrush = floorCombo.getSelectedIndex();
        setTool(T_FLOOR);
    });
    floorCombo.setRenderer(new DefaultListCellRenderer() {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
            JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, sel, focus);
            int i = Arrays.asList(FLOOR_NAMES).indexOf(String.valueOf(value));
            if (i >= 0) l.setIcon(new SwatchIcon(FLOOR_COLORS[i]));
            return l;
        }
    });

    for (JSpinner sp : new JSpinner[]{widthSpin, heightSpin}) {
        sp.setPreferredSize(new Dimension(62, 28));
        JComponent ed = sp.getEditor();
        if (ed instanceof JSpinner.DefaultEditor) {
            JFormattedTextField tf = ((JSpinner.DefaultEditor) ed).getTextField();
            tf.setBackground(App.BG_FIELD);
            tf.setForeground(App.TEXT_LIGHT);
            tf.setCaretColor(App.TEXT_LIGHT);
        }
    }

    JPanel north = new JPanel(new BorderLayout());
    north.add(top, BorderLayout.NORTH);
    north.add(toolRow, BorderLayout.SOUTH);
    panel.add(north, BorderLayout.NORTH);

    panel.add(canvas, BorderLayout.CENTER);

    JPanel bottom = new JPanel(new BorderLayout());
    bottom.setBackground(App.BG_PANEL);
    bottom.setBorder(new EmptyBorder(6, 14, 6, 14));
    statusLabel.setForeground(App.TEXT_MUTED);
    statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
    bottom.add(statusLabel, BorderLayout.WEST);
    JLabel scaleLbl = new JLabel("1 square = 5 ft.   •   Wheel: zoom   •   Right-drag: pan   •   Ctrl+Z: undo");
    scaleLbl.setForeground(App.TEXT_MUTED);
    scaleLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
    bottom.add(scaleLbl, BorderLayout.EAST);
    panel.add(bottom, BorderLayout.SOUTH);

    panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "undoMap");
    panel.getActionMap().put("undoMap", new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (panel.isShowing() && editMode) undo();
        }
    });
}

// Opens a Maps asset. returnCard is the main card to go back to; onClose runs when leaving.
void open(String mapId, boolean edit, String returnCard, Runnable onClose) {
    Map<String, Object> a = CustomAssets.find(CustomAssets.MAPS, mapId);
    if (a == null) return;
    this.asset = a;
    this.returnCard = returnCard;
    this.onClose = onClose != null ? onClose : () -> { };
    this.model = MapModel.fromJson(a.get("grid"));
    undoStack.clear();
    titleLabel.setText(CustomAssets.str(a, "name"));
    savedLabel.setText(" ");
    setEditMode(edit);
    outerLayout.show(outerCards, "MAP_EDITOR");
    SwingUtilities.invokeLater(canvas::fit);
}

private void close() {
    outerLayout.show(outerCards, returnCard);
    onClose.run();
}

private void setEditMode(boolean edit) {
    editMode = edit;
    modeButton.setText(edit ? "View Mode" : "✎ Edit Map");
    modeButton.setToolTipText(edit ? "Switch to the read-only table view" : "Edit walls, floors and pins");
    undoButton.setVisible(edit);
    toolRow.removeAll();
    if (edit) {
        for (String[] t : TOOLS) {
            toolRow.add(toolButtons.get(t[0]));
            if (T_FLOOR.equals(t[0])) toolRow.add(floorCombo);
        }
        toolRow.add(Box.createRigidArea(new Dimension(18, 0)));
        toolRow.add(mutedLabel("Size:"));
        widthSpin.setValue(model.width);
        heightSpin.setValue(model.height);
        toolRow.add(widthSpin);
        toolRow.add(mutedLabel("×"));
        toolRow.add(heightSpin);
        JButton resize = smallButton("Resize", "Apply the new width and height (squares outside the new size are removed)");
        resize.addActionListener(e -> applyResize(resize));
        toolRow.add(resize);
    } else {
        toolRow.add(mutedLabel("Table view: click doors to open or close them, and pins for their details. Drag to pan."));
    }
    setTool(edit ? tool : SELECT);
    toolRow.revalidate();
    toolRow.repaint();
    canvas.repaint();
}

private void setTool(String t) {
    tool = t;
    for (Map.Entry<String, JToggleButton> en : toolButtons.entrySet()) {
        JToggleButton b = en.getValue();
        boolean on = en.getKey().equals(t);
        b.setSelected(on);
        b.setContentAreaFilled(false); // let our background show instead of the look-and-feel's selected tint
        b.setBackground(on ? App.GOLD : App.SHEET_ROW_ALT);
        b.setForeground(on ? App.SHEET_BG : App.TEXT_LIGHT);
        b.setOpaque(true);
        b.setBorder(BorderFactory.createCompoundBorder(new LineBorder(on ? App.GOLD : App.SHEET_BORDER, 1, true), new EmptyBorder(5, 11, 5, 11)));
    }
    for (String[] row : TOOLS) if (row[0].equals(t)) statusLabel.setText(row[1]);
    canvas.setCursor(Cursor.getPredefinedCursor(SELECT.equals(t) ? Cursor.DEFAULT_CURSOR : Cursor.CROSSHAIR_CURSOR));
    canvas.repaint();
}

private void applyResize(Component anchor) {
    int w = (Integer) widthSpin.getValue(), h = (Integer) heightSpin.getValue();
    if (w == model.width && h == model.height) return;
    Runnable apply = () -> {
        pushUndo();
        model = model.resized(w, h);
        persist();
        canvas.fit();
    };
    if (w < model.width || h < model.height) {
        App.showConfirmOverlay(anchor, "Shrink Map", "Shrinking to " + w + " × " + h
                + " removes walls, floors and pins outside the new edges. You can undo this.", "Resize", apply);
    } else {
        apply.run();
    }
}

private void pushUndo() {
    undoStack.push(JsonUtil.toJson(model.toJson()));
    while (undoStack.size() > 60) undoStack.removeLast();
}

private void undo() {
    if (undoStack.isEmpty()) {
        savedLabel.setText("Nothing to undo");
        return;
    }
    model = MapModel.fromJson(new MiniJsonParser(undoStack.pop()).parse());
    widthSpin.setValue(model.width);
    heightSpin.setValue(model.height);
    persist();
    canvas.repaint();
}

// Saves the grid into the asset (maps don't affect rules, so no runtime re-apply)
private void persist() {
    if (asset == null) return;
    asset.put("grid", model.toJson());
    asset.put("width", model.width);
    asset.put("height", model.height);
    CustomAssets.putQuiet(CustomAssets.MAPS, asset);
    savedLabel.setText("Saved");
}

// ===============================================================
// Pins & info overlays
// ===============================================================
private void showMapInfo() {
    if (asset == null) return;
    int doors = 0, walls = 0, windows = 0;
    for (int[][] grid : new int[][][]{model.hEdges, model.vEdges}) {
        for (int[] row : grid) for (int v : row) {
            if (v == WALL) walls++;
            else if (v == DOOR_CLOSED || v == DOOR_OPEN) doors++;
            else if (v == WINDOW) windows++;
        }
    }
    List<String[]> rows = new ArrayList<>();
    rows.add(App.row("Size", model.width + " × " + model.height + " squares"));
    rows.add(App.row("Area", model.width * 5 + " ft. × " + model.height * 5 + " ft."));
    rows.add(App.row("Wall segments", String.valueOf(walls)));
    rows.add(App.row("Doors", String.valueOf(doors)));
    rows.add(App.row("Windows", String.valueOf(windows)));
    rows.add(App.row("Pins", String.valueOf(model.pins.size())));
    String notes = CustomAssets.str(asset, "notes");
    App.showInfoOverlay(canvas, CustomAssets.str(asset, "name"), "Map", notes.isEmpty() ? "No notes." : notes, rows);
}

private void showPinDetail(Pin pin) {
    JPanel body = new JPanel();
    body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
    body.setOpaque(false);
    JLabel where = new JLabel("PIN • SQUARE " + (pin.x + 1) + ", " + (pin.y + 1) + "  (" + pin.x * 5 + " ft., " + pin.y * 5 + " ft.)");
    where.setForeground(App.TEXT_MUTED);
    where.setFont(new Font("SansSerif", Font.BOLD, 11));
    where.setAlignmentX(Component.LEFT_ALIGNMENT);
    body.add(where);
    body.add(Box.createRigidArea(new Dimension(0, 8)));
    JLabel notes = new JLabel("<html><div style='width:340px'>" + App.escapeHtml(pin.notes.isEmpty() ? "No notes." : pin.notes).replace("\n", "<br>") + "</div></html>");
    notes.setForeground(App.TEXT_LIGHT);
    notes.setFont(new Font("SansSerif", Font.PLAIN, 13));
    notes.setAlignmentX(Component.LEFT_ALIGNMENT);
    body.add(notes);

    List<Map<String, Object>> npcs = new ArrayList<>();
    for (String id : pin.npcIds) {
        Map<String, Object> n = CustomAssets.find(CustomAssets.NPCS, id);
        if (n != null) npcs.add(n);
    }
    if (!npcs.isEmpty()) {
        body.add(Box.createRigidArea(new Dimension(0, 12)));
        JLabel h = new JLabel("LINKED NPCS");
        h.setForeground(App.TEXT_MUTED);
        h.setFont(new Font("SansSerif", Font.BOLD, 11));
        h.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(h);
        body.add(Box.createRigidArea(new Dimension(0, 4)));
        for (Map<String, Object> n : npcs) {
            JLabel l = new JLabel("<html><b>" + App.escapeHtml(CustomAssets.str(n, "name")) + "</b> <span style='color:#aaa5a0'>"
                    + App.escapeHtml(CustomAssets.summary(CustomAssets.NPCS, n)) + "</span></html>");
            l.setForeground(App.GOLD);
            l.setFont(new Font("SansSerif", Font.PLAIN, 13));
            l.setBorder(new EmptyBorder(3, 6, 3, 6));
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            App.clickable(l, () -> AssetLibraryPanel.showDetail(l, CustomAssets.NPCS, n, null));
            body.add(l);
        }
    }

    Runnable[] close = new Runnable[1];
    if (editMode) {
        body.add(Box.createRigidArea(new Dimension(0, 14)));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton edit = App.overlayButton("Edit Pin", true);
        edit.addActionListener(e -> {
            close[0].run();
            showPinEditor(pin, false);
        });
        buttons.add(edit);
        body.add(buttons);
    }
    close[0] = App.showSheetOverlay(canvas, pin.title.isEmpty() ? "Untitled Pin" : pin.title, body, null);
}

private void showPinEditor(Pin pin, boolean isNew) {
    JPanel body = new JPanel();
    body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
    body.setOpaque(false);

    JTextField title = App.overlayField(pin.title, 26);
    JTextArea notes = new JTextArea(pin.notes, 5, 30);
    notes.setLineWrap(true);
    notes.setWrapStyleWord(true);
    notes.setBackground(App.SHEET_BG);
    notes.setForeground(App.TEXT_LIGHT);
    notes.setCaretColor(App.TEXT_LIGHT);
    notes.setFont(new Font("SansSerif", Font.PLAIN, 13));
    notes.setBorder(new EmptyBorder(6, 6, 6, 6));
    JScrollPane notesScroll = new JScrollPane(notes);
    notesScroll.setBorder(new LineBorder(App.SHEET_BORDER, 1, true));

    body.add(fieldLabel("Title"));
    body.add(title);
    body.add(Box.createRigidArea(new Dimension(0, 10)));
    body.add(fieldLabel("Notes"));
    body.add(notesScroll);
    body.add(Box.createRigidArea(new Dimension(0, 10)));
    body.add(fieldLabel("Linked NPCs (from the Asset Library)"));

    List<String> npcNames = new ArrayList<>();
    Map<String, String> nameToId = new LinkedHashMap<>();
    Map<String, String> npcInfo = new LinkedHashMap<>();
    for (Map<String, Object> n : CustomAssets.all(CustomAssets.NPCS)) {
        String nm = CustomAssets.str(n, "name");
        npcNames.add(nm);
        nameToId.put(nm, CustomAssets.str(n, "id"));
        npcInfo.put(nm, CustomAssets.summary(CustomAssets.NPCS, n));
    }
    App.CheckListResult npcList = null;
    if (npcNames.isEmpty()) {
        JLabel none = new JLabel("No NPCs yet. Create them in the Asset Library's NPCs tab.");
        none.setForeground(App.TEXT_MUTED);
        none.setFont(new Font("SansSerif", Font.PLAIN, 12));
        none.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(none);
    } else {
        npcList = App.buildCheckListPanel(npcNames, npcNames.size(), null, null, null,
                npcNames.size() > 6, Math.min(5, npcNames.size()), null);
        List<String> pre = new ArrayList<>();
        for (Map.Entry<String, String> en : nameToId.entrySet()) if (pin.npcIds.contains(en.getValue())) pre.add(en.getKey());
        npcList.setSelected.accept(pre);
        npcList.component.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(npcList.component);
    }
    for (Component c : body.getComponents()) if (c instanceof JComponent) ((JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    buttons.setOpaque(false);
    buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
    Runnable[] close = new Runnable[1];
    if (!isNew) {
        JButton del = App.overlayButton("Delete Pin", false);
        del.setForeground(App.ACCENT_HOVER);
        del.addActionListener(e -> {
            pushUndo();
            model.pins.remove(pin);
            persist();
            canvas.repaint();
            close[0].run();
        });
        buttons.add(del);
    }
    JButton cancel = App.overlayButton("Cancel", false);
    cancel.addActionListener(e -> close[0].run());
    JButton save = App.overlayButton(isNew ? "Place Pin" : "Save Pin", true);
    final App.CheckListResult npcListRef = npcList;
    save.addActionListener(e -> {
        pushUndo();
        pin.title = title.getText().trim();
        pin.notes = notes.getText().trim();
        pin.npcIds = new ArrayList<>();
        if (npcListRef != null) for (String nm : npcListRef.getSelected.get()) pin.npcIds.add(nameToId.get(nm));
        if (isNew && !model.pins.contains(pin)) model.pins.add(pin);
        persist();
        canvas.repaint();
        close[0].run();
    });
    buttons.add(cancel);
    buttons.add(save);
    body.add(Box.createRigidArea(new Dimension(0, 14)));
    body.add(buttons);

    close[0] = App.showSheetOverlay(canvas, isNew ? "New Pin (" + (pin.x + 1) + ", " + (pin.y + 1) + ")" : "Edit Pin", body, title);
}

private static JLabel fieldLabel(String text) {
    JLabel l = new JLabel(text);
    l.setForeground(App.GOLD);
    l.setFont(new Font("SansSerif", Font.BOLD, 12));
    l.setBorder(new EmptyBorder(0, 0, 4, 0));
    return l;
}

private static JLabel mutedLabel(String text) {
    JLabel l = new JLabel(text);
    l.setForeground(App.TEXT_MUTED);
    l.setFont(new Font("SansSerif", Font.PLAIN, 13));
    return l;
}

private static JButton smallButton(String text, String tip) {
    JButton b = new JButton(text);
    b.setToolTipText(tip);
    styleSmall(b);
    return b;
}

private static void styleSmall(JButton b) {
    b.setFont(new Font("SansSerif", Font.BOLD, 13));
    b.setForeground(App.TEXT_LIGHT);
    b.setBackground(App.SHEET_ROW_ALT);
    b.setOpaque(true);
    b.setFocusPainted(false);
    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    b.setBorder(BorderFactory.createCompoundBorder(new LineBorder(App.SHEET_BORDER, 1, true), new EmptyBorder(5, 11, 5, 11)));
    b.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseEntered(MouseEvent e) { b.setForeground(App.GOLD); }
        @Override
        public void mouseExited(MouseEvent e) { b.setForeground(App.TEXT_LIGHT); }
    });
}

private static class SwatchIcon implements Icon {
    final Color c;
    SwatchIcon(Color c) { this.c = c; }
    public int getIconWidth() { return 14; }
    public int getIconHeight() { return 14; }
    public void paintIcon(Component comp, Graphics g, int x, int y) {
        g.setColor(c);
        g.fillRect(x, y, 14, 14);
        g.setColor(App.SHEET_BORDER);
        g.drawRect(x, y, 13, 13);
    }
}

// ===============================================================
// Canvas
// ===============================================================
private class Canvas extends JComponent {
    double scale = 36;     // pixels per square
    double offX = 20, offY = 20;

    // interaction state
    private Point pressPoint, lastPoint;
    private boolean panning, stroking, strokeChanged;
    private int strokeValue;            // edge value / floor index being painted this stroke
    private int lockOrient = -1, lockLine = -1;
    private int[] hoverEdge;
    private int hoverCellX = -1, hoverCellY = -1;

    Canvas() {
        setOpaque(true);
        setBackground(App.SHEET_BG);
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
                hoverEdge = null;
                hoverCellX = hoverCellY = -1;
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
        scale = Math.max(8, Math.min(80, Math.min((w - 40.0) / model.width, (h - 40.0) / model.height)));
        offX = (w - model.width * scale) / 2;
        offY = (h - model.height * scale) / 2;
        repaint();
    }

    private boolean isEdgeTool() {
        return T_WALL.equals(tool) || T_DOOR.equals(tool) || T_WINDOW.equals(tool) || T_ERASE.equals(tool);
    }

    // Nearest grid edge to (x, y) in square units, or null. With a stroke lock, stays on one grid line.
    int[] pickEdge(double x, double y) {
        boolean vertical;
        int line;
        if (lockOrient >= 0) {
            vertical = lockOrient == 1;
            line = lockLine;
            double dist = vertical ? Math.abs(x - line) : Math.abs(y - line);
            if (dist > 0.6) return null;
        } else {
            int rx = (int) Math.round(x), ry = (int) Math.round(y);
            double dv = Math.abs(x - rx), dh = Math.abs(y - ry);
            vertical = dv < dh;
            if (Math.min(dv, dh) > 0.3) return null;
            line = vertical ? rx : ry;
        }
        if (vertical) {
            int cy = (int) Math.floor(y);
            if (line < 0 || line > model.width || cy < 0 || cy >= model.height) return null;
            return new int[]{1, line, cy};
        }
        int cx = (int) Math.floor(x);
        if (line < 0 || line > model.height || cx < 0 || cx >= model.width) return null;
        return new int[]{0, cx, line};
    }

    int[] cellAt(double x, double y) {
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
        if (cx < 0 || cy < 0 || cx >= model.width || cy >= model.height) return null;
        return new int[]{cx, cy};
    }

    private void onPress(MouseEvent e) {
        requestFocusInWindow();
        pressPoint = lastPoint = e.getPoint();
        panning = false;
        stroking = false;
        strokeChanged = false;
        if (!SwingUtilities.isLeftMouseButton(e) || SELECT.equals(tool) || !editMode) {
            panning = true; // left-drag in Select mode pans too, but a click (no drag) still acts on release
            return;
        }
        double x = fx(e.getX()), y = fy(e.getY());
        if (isEdgeTool()) {
            lockOrient = -1;
            int[] edge = pickEdge(x, y);
            if (edge == null) return;
            int cur = model.getEdge(edge);
            if (T_DOOR.equals(tool) && (cur == DOOR_CLOSED || cur == DOOR_OPEN)) {
                pushUndo();
                model.setEdge(edge, cur == DOOR_CLOSED ? DOOR_OPEN : DOOR_CLOSED);
                persist();
                repaint();
                return;
            }
            pushUndo();
            stroking = true;
            lockOrient = edge[0];
            lockLine = edge[0] == 1 ? edge[1] : edge[2];
            switch (tool) {
                case T_WALL: strokeValue = cur == WALL ? NONE : WALL; break;
                case T_WINDOW: strokeValue = cur == WINDOW ? NONE : WINDOW; break;
                case T_DOOR: strokeValue = DOOR_CLOSED; break;
                default: strokeValue = NONE;
            }
            applyEdge(edge);
        } else if (T_FLOOR.equals(tool)) {
            int[] cell = cellAt(x, y);
            if (cell == null) return;
            pushUndo();
            stroking = true;
            strokeValue = floorBrush;
            applyFloor(cell);
        } else if (T_PIN.equals(tool)) {
            int[] cell = cellAt(x, y);
            if (cell == null) return;
            Pin existing = model.pinAt(cell[0], cell[1]);
            if (existing != null) {
                showPinEditor(existing, false);
            } else {
                Pin p = new Pin();
                p.x = cell[0];
                p.y = cell[1];
                p.title = "Pin " + (model.pins.size() + 1);
                showPinEditor(p, true);
            }
        }
        repaint();
    }

    private void onDrag(MouseEvent e) {
        Point p = e.getPoint();
        if (panning) {
            offX += p.x - lastPoint.x;
            offY += p.y - lastPoint.y;
            lastPoint = p;
            if (pressPoint.distance(p) > 4) setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            repaint();
            return;
        }
        if (stroking) {
            // Interpolate so fast drags don't skip squares / edges
            double dist = lastPoint.distance(p);
            int steps = Math.max(1, (int) Math.ceil(dist / Math.max(2, scale * 0.2)));
            for (int i = 1; i <= steps; i++) {
                double t = i / (double) steps;
                double x = fx(lastPoint.x + (p.x - lastPoint.x) * t), y = fy(lastPoint.y + (p.y - lastPoint.y) * t);
                if (isEdgeTool()) {
                    int[] edge = pickEdge(x, y);
                    if (edge != null) applyEdge(edge);
                } else if (T_FLOOR.equals(tool)) {
                    int[] cell = cellAt(x, y);
                    if (cell != null) applyFloor(cell);
                }
            }
            lastPoint = p;
            onMove(p);
        }
    }

    private void onRelease(MouseEvent e) {
        boolean wasClick = pressPoint != null && pressPoint.distance(e.getPoint()) <= 4;
        if (panning) {
            setCursor(Cursor.getPredefinedCursor(SELECT.equals(tool) || !editMode ? Cursor.DEFAULT_CURSOR : Cursor.CROSSHAIR_CURSOR));
            if (wasClick && SwingUtilities.isLeftMouseButton(e)) clickSelect(fx(e.getX()), fy(e.getY()));
        }
        if (stroking && strokeChanged) persist();
        panning = stroking = false;
        lockOrient = -1;
        repaint();
    }

    // Select tool / view mode click: pins first, then doors
    private void clickSelect(double x, double y) {
        int[] cell = cellAt(x, y);
        if (cell != null) {
            Pin p = model.pinAt(cell[0], cell[1]);
            if (p != null && pinHit(p, x, y)) {
                showPinDetail(p);
                return;
            }
        }
        int[] edge = pickEdge(x, y);
        if (edge != null) {
            int cur = model.getEdge(edge);
            if (cur == DOOR_CLOSED || cur == DOOR_OPEN) {
                pushUndo();
                model.setEdge(edge, cur == DOOR_CLOSED ? DOOR_OPEN : DOOR_CLOSED);
                persist();
                repaint();
                return;
            }
            if (cur == WALL || cur == WINDOW) {
                App.showInfoOverlay(this, cur == WALL ? "Wall" : "Window", "Square edge",
                        cur == WALL ? "A solid wall. It blocks movement and line of sight, and provides Total Cover."
                                : "A window or low wall. It blocks movement but not sight, and gives Half Cover (+2 AC and DEX saves) to creatures behind it.",
                        null);
                return;
            }
        }
        if (cell != null) {
            int fl = model.floor[cell[1]][cell[0]];
            App.showInfoOverlay(this, FLOOR_NAMES[fl], "Square " + (cell[0] + 1) + ", " + (cell[1] + 1) + " • 5 ft.",
                    FLOOR_INFO[fl], null);
        }
    }

    private boolean pinHit(Pin p, double x, double y) {
        double r = Math.max(0.3, 9 / scale);
        return Math.hypot(x - (p.x + 0.5), y - (p.y + 0.5)) <= r + 0.15;
    }

    private void applyEdge(int[] edge) {
        if (model.getEdge(edge) != strokeValue) {
            model.setEdge(edge, strokeValue);
            strokeChanged = true;
        }
    }

    private void applyFloor(int[] cell) {
        if (model.floor[cell[1]][cell[0]] != strokeValue) {
            model.floor[cell[1]][cell[0]] = strokeValue;
            strokeChanged = true;
            repaint();
        }
    }

    private void onMove(Point p) {
        double x = fx(p.x), y = fy(p.y);
        hoverEdge = editMode && isEdgeTool() ? pickEdge(x, y) : null;
        int[] cell = cellAt(x, y);
        hoverCellX = cell == null ? -1 : cell[0];
        hoverCellY = cell == null ? -1 : cell[1];
        if (cell != null) {
            Pin pin = model.pinAt(cell[0], cell[1]);
            statusLabel.setText("Square " + (cell[0] + 1) + ", " + (cell[1] + 1) + "  •  " + cell[0] * 5 + " ft. × " + cell[1] * 5 + " ft."
                    + "  •  " + FLOOR_NAMES[model.floor[cell[1]][cell[0]]] + (pin != null ? "  •  Pin: " + pin.title : ""));
        }
        boolean overClickable = false;
        if (SELECT.equals(tool) || !editMode) {
            if (cell != null && model.pinAt(cell[0], cell[1]) != null && pinHit(model.pinAt(cell[0], cell[1]), x, y)) overClickable = true;
            int[] e = pickEdge(x, y);
            if (e != null && model.getEdge(e) != NONE) overClickable = true;
            if (!panning) setCursor(Cursor.getPredefinedCursor(overClickable ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        }
        repaint();
    }

    // ---------------- painting ----------------
    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        MapPainter p = new MapPainter(model, scale, offX, offY);
        p.paintBase(g, getWidth(), getHeight());

        // hover cell (floor / pin tools)
        if (editMode && hoverCellX >= 0 && (T_FLOOR.equals(tool) || T_PIN.equals(tool))) {
            g.setColor(new Color(212, 175, 55, 160));
            g.setStroke(new BasicStroke(2f));
            g.draw(new Rectangle2D.Double(offX + hoverCellX * scale + 1, offY + hoverCellY * scale + 1, scale - 2, scale - 2));
        }

        p.paintEdges(g);

        // hover edge
        if (hoverEdge != null) {
            double ax = hoverEdge[1], ay = hoverEdge[2];
            double bx = hoverEdge[0] == 0 ? ax + 1 : ax, by = hoverEdge[0] == 0 ? ay : ay + 1;
            g.setColor(new Color(212, 175, 55, 170));
            g.setStroke(new BasicStroke((float) Math.max(4, scale * 0.18), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Line2D.Double(offX + ax * scale, offY + ay * scale, offX + bx * scale, offY + by * scale));
        }

        p.paintPins(g);
        p.paintLegend(g, getHeight());
        g.dispose();
    }
}

// ===============================================================
// Map painter: floors, grid, walls / doors / windows, pins and the scale legend at a given zoom.
// Shared by the editor canvas and the Combat board (CombatPanel) so both draw the same map.
// ===============================================================
static class MapPainter {
    final MapModel model;
    final double scale, offX, offY;

    MapPainter(MapModel model, double scale, double offX, double offY) {
        this.model = model;
        this.scale = scale;
        this.offX = offX;
        this.offY = offY;
    }

    // Background, floors, grid lines and the map border for a w × h viewport
    void paintBase(Graphics2D g, int w, int h) {
        g.setColor(App.SHEET_BG);
        g.fillRect(0, 0, w, h);

        int x0 = Math.max(0, (int) Math.floor((0 - offX) / scale)), y0 = Math.max(0, (int) Math.floor((0 - offY) / scale));
        int x1 = Math.min(model.width - 1, (int) Math.floor((w - offX) / scale)), y1 = Math.min(model.height - 1, (int) Math.floor((h - offY) / scale));

        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                int f = model.floor[y][x];
                double px = offX + x * scale, py = offY + y * scale;
                g.setColor(FLOOR_COLORS[f]);
                g.fill(new Rectangle2D.Double(px, py, scale + 0.5, scale + 0.5));
                if (scale >= 16) paintTexture(g, f, px, py, x, y);
            }
        }

        g.setStroke(new BasicStroke(1f));
        for (int x = Math.max(0, x0); x <= Math.min(model.width, x1 + 1); x++) {
            g.setColor(x % 5 == 0 ? GRID_MAJOR : GRID_COLOR);
            double px = offX + x * scale;
            g.draw(new Line2D.Double(px, offY, px, offY + model.height * scale));
        }
        for (int y = Math.max(0, y0); y <= Math.min(model.height, y1 + 1); y++) {
            g.setColor(y % 5 == 0 ? GRID_MAJOR : GRID_COLOR);
            double py = offY + y * scale;
            g.draw(new Line2D.Double(offX, py, offX + model.width * scale, py));
        }
        g.setColor(App.SHEET_BORDER);
        g.setStroke(new BasicStroke(2f));
        g.draw(new Rectangle2D.Double(offX, offY, model.width * scale, model.height * scale));
    }

    void paintEdges(Graphics2D g) {
        for (int y = 0; y <= model.height; y++) {
            for (int x = 0; x < model.width; x++) {
                int v = model.hEdges[y][x];
                if (v != NONE) paintEdge(g, v, x, y, x + 1, y);
            }
        }
        for (int y = 0; y < model.height; y++) {
            for (int x = 0; x <= model.width; x++) {
                int v = model.vEdges[y][x];
                if (v != NONE) paintEdge(g, v, x, y, x, y + 1);
            }
        }
    }

    void paintPins(Graphics2D g) {
        for (int i = 0; i < model.pins.size(); i++) paintPin(g, model.pins.get(i), i + 1);
    }

    // Scale bar in the bottom-left corner of a viewport `h` pixels tall
    void paintLegend(Graphics2D g, int h) {
        double barPx = scale * (scale < 20 ? 5 : 1);
        String barLbl = scale < 20 ? "25 ft." : "5 ft.";
        int lx = 14, ly = h - 18;
        g.setColor(new Color(0, 0, 0, 120));
        g.fill(new RoundRectangle2D.Double(lx - 6, ly - 18, barPx + 60, 28, 8, 8));
        g.setColor(App.TEXT_LIGHT);
        g.setStroke(new BasicStroke(2f));
        g.draw(new Line2D.Double(lx, ly, lx + barPx, ly));
        g.draw(new Line2D.Double(lx, ly - 4, lx, ly + 2));
        g.draw(new Line2D.Double(lx + barPx, ly - 4, lx + barPx, ly + 2));
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        g.drawString(barLbl, (float) (lx + barPx + 8), ly + 4);
    }

    private void paintTexture(Graphics2D g, int f, double px, double py, int cx, int cy) {
        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(1f));
        switch (f) {
            case 1: { // stone: faint offset flagstone seams (kept weaker than the 5-ft. grid lines)
                g.setColor(new Color(0, 0, 0, 22));
                double h = scale / 2;
                g.draw(new Line2D.Double(px, py + h, px + scale, py + h));
                double off = (cy % 2 == 0) ? scale * 0.35 : scale * 0.7;
                g.draw(new Line2D.Double(px + off, py, px + off, py + h));
                g.draw(new Line2D.Double(px + scale - off, py + h, px + scale - off, py + scale));
                break;
            }
            case 2: { // wood planks
                g.setColor(new Color(0, 0, 0, 55));
                for (int i = 1; i < 4; i++) {
                    double ly = py + scale * i / 4.0;
                    g.draw(new Line2D.Double(px, ly, px + scale, ly));
                }
                g.setColor(new Color(255, 230, 190, 25));
                double jx = px + scale * (((cx * 7 + cy * 3) % 4) / 4.0 + 0.12);
                g.draw(new Line2D.Double(jx, py, jx, py + scale / 4.0));
                break;
            }
            case 3: { // grass tufts
                g.setColor(new Color(150, 200, 120, 60));
                Random r = new Random(cx * 7919L + cy * 104729L);
                for (int i = 0; i < 4; i++) {
                    double gx = px + r.nextDouble() * scale, gy = py + r.nextDouble() * scale;
                    g.draw(new Line2D.Double(gx, gy, gx - scale * 0.05, gy - scale * 0.12));
                    g.draw(new Line2D.Double(gx, gy, gx + scale * 0.05, gy - scale * 0.12));
                }
                break;
            }
            case 4: { // water ripples
                g.setColor(new Color(170, 210, 255, 55));
                for (int i = 0; i < 2; i++) {
                    double wy = py + scale * (0.35 + i * 0.35);
                    Path2D wave = new Path2D.Double();
                    wave.moveTo(px + scale * 0.12, wy);
                    wave.curveTo(px + scale * 0.3, wy - scale * 0.1, px + scale * 0.5, wy + scale * 0.1, px + scale * 0.7, wy);
                    wave.curveTo(px + scale * 0.78, wy - scale * 0.05, px + scale * 0.84, wy - scale * 0.05, px + scale * 0.9, wy);
                    g.draw(wave);
                }
                break;
            }
            case 5: { // pit: inner shadow
                g.setColor(new Color(0, 0, 0, 120));
                double in = scale * 0.12;
                g.fill(new Rectangle2D.Double(px + in, py + in, scale - 2 * in, scale - 2 * in));
                g.setColor(new Color(255, 255, 255, 18));
                g.draw(new Rectangle2D.Double(px + in, py + in, scale - 2 * in, scale - 2 * in));
                break;
            }
            default:
        }
        g.setStroke(old);
    }

    private void paintEdge(Graphics2D g, int v, double ax, double ay, double bx, double by) {
        double x1 = offX + ax * scale, y1 = offY + ay * scale, x2 = offX + bx * scale, y2 = offY + by * scale;
        float wallW = (float) Math.max(3, scale * 0.14);
        Stroke wallStroke = new BasicStroke(wallW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        if (v == WALL) {
            g.setColor(WALL_COLOR);
            g.setStroke(wallStroke);
            g.draw(new Line2D.Double(x1, y1, x2, y2));
            return;
        }
        double dx = x2 - x1, dy = y2 - y1;
        double len = Math.hypot(dx, dy);
        double ux = dx / len, uy = dy / len;     // along the edge
        double nx = -uy, ny = ux;                // normal
        double stub = v == WINDOW ? 0.2 : 0.15;
        // wall stubs at both ends
        g.setColor(WALL_COLOR);
        g.setStroke(wallStroke);
        g.draw(new Line2D.Double(x1, y1, x1 + dx * stub, y1 + dy * stub));
        g.draw(new Line2D.Double(x2 - dx * stub, y2 - dy * stub, x2, y2));
        double sx = x1 + dx * stub, sy = y1 + dy * stub;        // opening start
        double ex = x2 - dx * stub, ey = y2 - dy * stub;        // opening end
        double openLen = len * (1 - 2 * stub);
        if (v == WINDOW) {
            double t = Math.max(2.5, scale * 0.07);
            Path2D pane = new Path2D.Double();
            pane.moveTo(sx + nx * t, sy + ny * t);
            pane.lineTo(ex + nx * t, ey + ny * t);
            pane.lineTo(ex - nx * t, ey - ny * t);
            pane.lineTo(sx - nx * t, sy - ny * t);
            pane.closePath();
            g.setColor(new Color(143, 193, 227, 70));
            g.fill(pane);
            g.setColor(WINDOW_COLOR);
            g.setStroke(new BasicStroke(1.6f));
            g.draw(new Line2D.Double(sx + nx * t, sy + ny * t, ex + nx * t, ey + ny * t));
            g.draw(new Line2D.Double(sx - nx * t, sy - ny * t, ex - nx * t, ey - ny * t));
            return;
        }
        double t = Math.max(2.5, scale * 0.11);
        if (v == DOOR_CLOSED) {
            Path2D slab = new Path2D.Double();
            slab.moveTo(sx + nx * t, sy + ny * t);
            slab.lineTo(ex + nx * t, ey + ny * t);
            slab.lineTo(ex - nx * t, ey - ny * t);
            slab.lineTo(sx - nx * t, sy - ny * t);
            slab.closePath();
            g.setColor(DOOR_COLOR);
            g.fill(slab);
            g.setColor(App.GOLD);
            g.setStroke(new BasicStroke(1.2f));
            g.draw(slab);
        } else { // open door: leaf swung 90 degrees from the hinge, with its swing arc
            double hx = sx, hy = sy;
            double lx = hx + nx * openLen, ly = hy + ny * openLen;
            g.setColor(new Color(212, 175, 55, 90));
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{3f, 3f}, 0f));
            double startDeg = Math.toDegrees(Math.atan2(-uy, ux));
            double sweep = Math.toDegrees(Math.atan2(-ny, nx)) - startDeg;
            while (sweep > 180) sweep -= 360;
            while (sweep < -180) sweep += 360;
            g.draw(new Arc2D.Double(hx - openLen, hy - openLen, openLen * 2, openLen * 2, startDeg, sweep, Arc2D.OPEN));
            g.setColor(DOOR_COLOR);
            g.setStroke(new BasicStroke((float) Math.max(3, t * 1.2), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Line2D.Double(hx, hy, lx, ly));
        }
    }

    private void paintPin(Graphics2D g, Pin p, int number) {
        double cx = offX + (p.x + 0.5) * scale, cy = offY + (p.y + 0.5) * scale;
        double r = Math.max(8, scale * 0.3);
        g.setColor(new Color(0, 0, 0, 110));
        g.fill(new Ellipse2D.Double(cx - r + 1.5, cy - r + 2.5, r * 2, r * 2));
        g.setColor(App.GOLD);
        g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        g.setColor(App.SHEET_BG);
        g.setStroke(new BasicStroke(2f));
        g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        g.setFont(new Font("SansSerif", Font.BOLD, (int) Math.max(10, r * 0.95)));
        FontMetrics fm = g.getFontMetrics();
        String s = String.valueOf(number);
        g.drawString(s, (float) (cx - fm.stringWidth(s) / 2.0), (float) (cy + fm.getAscent() / 2.0 - 2));
        if (scale >= 26 && !p.title.isEmpty()) {
            g.setFont(new Font("SansSerif", Font.BOLD, 11));
            fm = g.getFontMetrics();
            String t = p.title.length() > 24 ? p.title.substring(0, 23) + "…" : p.title;
            double tw = fm.stringWidth(t) + 10, ty = cy + r + 4;
            g.setColor(new Color(20, 22, 30, 210));
            g.fill(new RoundRectangle2D.Double(cx - tw / 2, ty, tw, 16, 6, 6));
            g.setColor(App.TEXT_LIGHT);
            g.drawString(t, (float) (cx - tw / 2 + 5), (float) (ty + 12));
        }
    }
}
}
