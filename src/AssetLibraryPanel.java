import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;

// Asset Library: one tab per homebrew category with search / filter, Create New, Edit, Duplicate,
// Delete, and Import / Export of JSON homebrew packs. Editor forms and detail overlays are built
// from CustomAssets.SCHEMAS. Everything stays inside the main window (cards + App overlays).
class AssetLibraryPanel {

final JPanel panel;

private final CardLayout outerLayout;
private final JPanel outerCards;
private final MapEditorPanel mapEditor;

private final CardLayout innerLayout = new CardLayout();
private final JPanel inner = new JPanel(innerLayout);

private String category = CustomAssets.ITEMS;
private final JPanel tabColumn = new JPanel();
private final JPanel listWrap = new JPanel();
private final JTextField searchField = new JTextField();
private final JComboBox<String> filterCombo = new JComboBox<>();
private final JLabel statusLabel = new JLabel(" ");
private final JLabel listTitle = new JLabel();
private final JButton createButton = new JButton();
private boolean updatingFilter;

private final JPanel editorHolder = new JPanel(new BorderLayout());

AssetLibraryPanel(CardLayout outerLayout, JPanel outerCards, MapEditorPanel mapEditor) {
    this.outerLayout = outerLayout;
    this.outerCards = outerCards;
    this.mapEditor = mapEditor;
    panel = App.buildSectionShell("Asset Library", outerLayout, outerCards, "MENU");
    inner.setBackground(App.SHEET_BG);
    inner.add(buildListView(), "LIST");
    editorHolder.setBackground(App.SHEET_BG);
    inner.add(editorHolder, "EDIT");
    panel.add(inner, BorderLayout.CENTER);
    panel.addComponentListener(new ComponentAdapter() {
        @Override
        public void componentShown(ComponentEvent e) {
            innerLayout.show(inner, "LIST");
            refresh();
        }
    });
}

// ===============================================================
// List view
// ===============================================================
private JComponent buildListView() {
    JPanel view = new JPanel(new BorderLayout());
    view.setBackground(App.SHEET_BG);

    // ---- category tabs (vertical) ----
    tabColumn.setLayout(new BoxLayout(tabColumn, BoxLayout.Y_AXIS));
    tabColumn.setBackground(App.SHEET_CARD);
    tabColumn.setBorder(new EmptyBorder(14, 10, 14, 10));
    JScrollPane tabScroll = new JScrollPane(tabColumn);
    App.applyDarkScrollBar(tabScroll);
    tabScroll.getViewport().setBackground(App.SHEET_CARD);
    tabScroll.setPreferredSize(new Dimension(200, 100));
    tabScroll.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, App.SHEET_BORDER));
    view.add(tabScroll, BorderLayout.WEST);

    // ---- toolbar ----
    JPanel center = new JPanel(new BorderLayout());
    center.setBackground(App.SHEET_BG);
    center.setBorder(new EmptyBorder(16, 22, 10, 22));

    JPanel toolbar = new JPanel(new BorderLayout(0, 10));
    toolbar.setOpaque(false);

    listTitle.setForeground(App.GOLD);
    listTitle.setFont(new Font("Serif", Font.BOLD, 24));
    JPanel titleRow = new JPanel(new BorderLayout());
    titleRow.setOpaque(false);
    titleRow.add(listTitle, BorderLayout.WEST);

    JPanel packButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    packButtons.setOpaque(false);
    JButton importBtn = App.overlayButton("Import JSON", false);
    importBtn.setToolTipText("Import a homebrew pack (.json)");
    importBtn.addActionListener(e -> showImport(importBtn));
    JButton exportBtn = App.overlayButton("Export JSON", false);
    exportBtn.setToolTipText("Export homebrew to a shareable .json pack");
    exportBtn.addActionListener(e -> showExport(exportBtn));
    App.styleAccentButton(createButton);
    createButton.setFont(new Font("SansSerif", Font.BOLD, 13));
    createButton.setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
    createButton.addActionListener(e -> openEditor(null));
    packButtons.add(importBtn);
    packButtons.add(exportBtn);
    packButtons.add(createButton);
    titleRow.add(packButtons, BorderLayout.EAST);
    toolbar.add(titleRow, BorderLayout.NORTH);

    JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    searchRow.setOpaque(false);
    JLabel searchLbl = new JLabel("Search:");
    searchLbl.setForeground(App.TEXT_MUTED);
    App.styleTextField(searchField);
    searchField.setFont(new Font("SansSerif", Font.PLAIN, 14));
    searchField.setColumns(22);
    searchField.setToolTipText("Filter by name, description or any field");
    searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
        public void insertUpdate(javax.swing.event.DocumentEvent e) { rebuildList(); }
        public void removeUpdate(javax.swing.event.DocumentEvent e) { rebuildList(); }
        public void changedUpdate(javax.swing.event.DocumentEvent e) { rebuildList(); }
    });
    App.styleCombo(filterCombo);
    filterCombo.setPreferredSize(new Dimension(170, 34));
    filterCombo.setToolTipText("Filter this category");
    filterCombo.addActionListener(e -> {
        if (!updatingFilter) rebuildList();
    });
    statusLabel.setForeground(App.TEXT_MUTED);
    statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
    searchRow.add(searchLbl);
    searchRow.add(searchField);
    searchRow.add(filterCombo);
    searchRow.add(Box.createRigidArea(new Dimension(10, 0)));
    searchRow.add(statusLabel);
    toolbar.add(searchRow, BorderLayout.SOUTH);
    center.add(toolbar, BorderLayout.NORTH);

    listWrap.setLayout(new BoxLayout(listWrap, BoxLayout.Y_AXIS));
    listWrap.setBackground(App.SHEET_BG);
    listWrap.setBorder(new EmptyBorder(14, 0, 10, 0));
    JPanel listTop = new JPanel(new BorderLayout());
    listTop.setBackground(App.SHEET_BG);
    listTop.add(listWrap, BorderLayout.NORTH);
    JScrollPane scroll = new JScrollPane(listTop);
    App.applyDarkScrollBar(scroll);
    scroll.getViewport().setBackground(App.SHEET_BG);
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    center.add(scroll, BorderLayout.CENTER);

    view.add(center, BorderLayout.CENTER);
    return view;
}

void refresh() {
    rebuildTabs();
    rebuildFilter();
    rebuildList();
}

// Opens the library on a category (e.g. from elsewhere in the app)
void showCategory(String cat) {
    category = cat;
    searchField.setText("");
    outerLayout.show(outerCards, "ASSETS");
    refresh();
}

private void rebuildTabs() {
    tabColumn.removeAll();
    JLabel head = new JLabel("CATEGORIES");
    head.setForeground(App.TEXT_MUTED);
    head.setFont(new Font("SansSerif", Font.BOLD, 11));
    head.setBorder(new EmptyBorder(0, 8, 8, 0));
    head.setAlignmentX(Component.LEFT_ALIGNMENT);
    tabColumn.add(head);
    for (String cat : CustomAssets.CATEGORIES) {
        boolean sel = cat.equals(category);
        JPanel tab = new JPanel(new BorderLayout());
        tab.setBackground(sel ? App.SHEET_ROW_ALT : App.SHEET_CARD);
        tab.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, sel ? App.GOLD : App.SHEET_CARD), new EmptyBorder(8, 10, 8, 10)));
        tab.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        tab.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel name = new JLabel(cat);
        name.setForeground(sel ? App.GOLD : App.TEXT_LIGHT);
        name.setFont(new Font("SansSerif", Font.BOLD, 14));
        JLabel count = new JLabel(String.valueOf(CustomAssets.count(cat)));
        count.setForeground(App.TEXT_MUTED);
        count.setFont(new Font("SansSerif", Font.PLAIN, 12));
        tab.add(name, BorderLayout.CENTER);
        tab.add(count, BorderLayout.EAST);
        tab.setToolTipText("Homebrew " + cat.toLowerCase());
        App.clickable(tab, () -> {
            category = cat;
            searchField.setText("");
            refresh();
        });
        tabColumn.add(tab);
        tabColumn.add(Box.createRigidArea(new Dimension(0, 2)));
    }
    tabColumn.revalidate();
    tabColumn.repaint();
    listTitle.setText(category);
    createButton.setText("+ New " + CustomAssets.SINGULAR.get(category));
}

private void rebuildFilter() {
    updatingFilter = true;
    Object prev = filterCombo.getSelectedItem();
    filterCombo.removeAllItems();
    String key = CustomAssets.FILTER_KEYS.get(category);
    filterCombo.setVisible(key != null);
    if (key != null) {
        filterCombo.addItem("All");
        TreeSet<String> values = new TreeSet<>(this::filterOrder);
        for (Map<String, Object> a : CustomAssets.all(category)) values.add(CustomAssets.filterValue(category, a));
        for (String v : values) filterCombo.addItem(v);
        if (prev != null && values.contains(String.valueOf(prev))) filterCombo.setSelectedItem(prev);
    }
    updatingFilter = false;
}

private int filterOrder(String a, String b) {
    // "Cantrip" < "Level 1" < ... < "Level 9"; everything else alphabetical
    int ia = a.equals("Cantrip") ? 0 : a.startsWith("Level ") ? CustomAssets.intVal(Collections.singletonMap("v", a.substring(6)), "v", 99) : -1;
    int ib = b.equals("Cantrip") ? 0 : b.startsWith("Level ") ? CustomAssets.intVal(Collections.singletonMap("v", b.substring(6)), "v", 99) : -1;
    if (ia >= 0 && ib >= 0) return Integer.compare(ia, ib);
    return a.compareToIgnoreCase(b);
}

private void rebuildList() {
    listWrap.removeAll();
    String q = searchField.getText().trim().toLowerCase();
    String filter = filterCombo.isVisible() && filterCombo.getSelectedItem() != null ? String.valueOf(filterCombo.getSelectedItem()) : "All";
    List<Map<String, Object>> all = CustomAssets.all(category);
    int shown = 0;
    for (Map<String, Object> a : all) {
        if (!"All".equals(filter) && !filter.equals(CustomAssets.filterValue(category, a))) continue;
        if (!q.isEmpty() && !JsonUtil.toJson(a).toLowerCase().contains(q)) continue;
        listWrap.add(assetRow(a));
        listWrap.add(Box.createRigidArea(new Dimension(0, 8)));
        shown++;
    }
    if (all.isEmpty()) {
        listWrap.add(emptyState());
    } else if (shown == 0) {
        JLabel none = new JLabel("Nothing matches your search.");
        none.setForeground(App.TEXT_MUTED);
        none.setFont(new Font("SansSerif", Font.PLAIN, 15));
        none.setAlignmentX(Component.LEFT_ALIGNMENT);
        listWrap.add(none);
    }
    listWrap.revalidate();
    listWrap.repaint();
}

private JComponent emptyState() {
    JPanel box = new JPanel();
    box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
    box.setOpaque(false);
    box.setAlignmentX(Component.LEFT_ALIGNMENT);
    box.setBorder(new EmptyBorder(30, 0, 0, 0));
    JLabel l = new JLabel("No homebrew " + CustomAssets.noun(category, true) + " yet.");
    l.setForeground(App.TEXT_LIGHT);
    l.setFont(new Font("SansSerif", Font.BOLD, 17));
    JLabel s = new JLabel("<html><div style='width:460px'>" + App.escapeHtml(categoryBlurb(category)) + "</div></html>");
    s.setForeground(App.TEXT_MUTED);
    s.setFont(new Font("SansSerif", Font.PLAIN, 13));
    s.setBorder(new EmptyBorder(6, 0, 14, 0));
    JButton b = App.overlayButton("Create your first " + CustomAssets.noun(category, false), true);
    b.addActionListener(e -> openEditor(null));
    box.add(l);
    box.add(s);
    box.add(b);
    return box;
}

static String categoryBlurb(String cat) {
    switch (cat) {
        case CustomAssets.ITEMS: return "Gear, consumables, wondrous items and tools. They show up in the character sheet's Add Equipment picker.";
        case CustomAssets.WEAPONS: return "Weapons join the weapon list used by equipment choices and the sheet, with damage, properties and mastery.";
        case CustomAssets.ARMOR: return "Armor and shields count toward Armor Class on the character sheet.";
        case CustomAssets.NPCS: return "Stat blocks and notes for the people of your world. Link them to pins on your maps.";
        case CustomAssets.MAPS: return "Grid maps with walls, doors, windows, floor types and clickable encounter pins (5 ft. squares). They also appear on the Combat screen.";
        case CustomAssets.CONDITIONS: return "Homebrew conditions become clickable on character sheets and in spell text, and can be applied from the sheet.";
        case CustomAssets.SPELLS: return "Spells join the class spell lists used by character creation, level up and the Spells tab.";
        case CustomAssets.BACKGROUNDS: return "Backgrounds appear in the character creation wizard with their skills, tools, origin feat and equipment.";
        case CustomAssets.SPECIES: return "Species appear in the character creation wizard and set size, speed, darkvision, languages and traits.";
        case CustomAssets.CLASSES: return "Classes appear in character creation and level up, with hit die, saves, skills, spellcasting and level 1-20 features.";
        case CustomAssets.SUBCLASSES: return "Subclasses are offered when a character reaches the right level in the parent class during level up.";
        default: return "";
    }
}

private JComponent assetRow(Map<String, Object> a) {
    String cat = category;
    String id = CustomAssets.str(a, "id");
    JPanel row = new JPanel(new BorderLayout(12, 0));
    row.setBackground(App.SHEET_CARD);
    row.setBorder(BorderFactory.createCompoundBorder(new LineBorder(App.SHEET_BORDER, 1, true), new EmptyBorder(10, 14, 10, 10)));
    row.setAlignmentX(Component.LEFT_ALIGNMENT);
    row.setMaximumSize(new Dimension(900, 70));

    JPanel text = new JPanel();
    text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
    text.setOpaque(false);
    JLabel name = new JLabel(CustomAssets.str(a, "name"));
    name.setForeground(App.GOLD);
    name.setFont(new Font("Serif", Font.BOLD, 17));
    String sum = CustomAssets.summary(cat, a);
    JLabel sub = new JLabel(sum.isEmpty() ? " " : sum);
    sub.setForeground(App.TEXT_MUTED);
    sub.setFont(new Font("SansSerif", Font.PLAIN, 12));
    text.add(name);
    text.add(Box.createRigidArea(new Dimension(0, 3)));
    text.add(sub);
    row.add(text, BorderLayout.CENTER);

    JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
    actions.setOpaque(false);
    if (CustomAssets.MAPS.equals(cat)) {
        JButton open = App.overlayButton("Open Map", true);
        open.setToolTipText("Open in the interactive map viewer");
        open.addActionListener(e -> openMap(id, false));
        actions.add(open);
        actions.add(iconButton("ⓘ", "Map details", () -> showDetail(row, cat, a, actionsFor(cat, a))));
    }
    actions.add(iconButton("✎", "Edit", () -> openEditor(a)));
    actions.add(iconButton("⧉", "Duplicate", () -> duplicate(cat, id)));
    actions.add(iconButton("🗑", "Delete", () -> confirmDelete(row, cat, a)));
    row.add(actions, BorderLayout.EAST);

    // Maps launch straight into the viewer; everything else opens its detail overlay
    App.clickableCard(row, () -> {
        if (CustomAssets.MAPS.equals(cat)) openMap(id, false);
        else showDetail(row, cat, a, actionsFor(cat, a));
    });
    return row;
}

private static JButton iconButton(String glyph, String tip, Runnable r) {
    JButton b = new JButton(glyph);
    b.setToolTipText(tip);
    b.setFont(new Font("SansSerif", Font.PLAIN, 16));
    b.setForeground(App.TEXT_MUTED);
    b.setContentAreaFilled(false);
    b.setBorderPainted(false);
    b.setFocusPainted(false);
    b.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    b.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseEntered(MouseEvent e) { b.setForeground("Delete".equals(tip) ? App.ACCENT_HOVER : App.GOLD); }
        @Override
        public void mouseExited(MouseEvent e) { b.setForeground(App.TEXT_MUTED); }
    });
    b.addActionListener(e -> r.run());
    return b;
}

private DetailActions actionsFor(String cat, Map<String, Object> a) {
    DetailActions act = new DetailActions();
    String id = CustomAssets.str(a, "id");
    act.edit = () -> openEditor(CustomAssets.find(cat, id));
    act.duplicate = () -> duplicate(cat, id);
    act.delete = anchor -> confirmDelete(anchor, cat, a);
    if (CustomAssets.MAPS.equals(cat)) {
        act.openMap = () -> openMap(id, false);
        act.editMap = () -> openMap(id, true);
    }
    return act;
}

private void duplicate(String cat, String id) {
    Map<String, Object> copy = CustomAssets.duplicate(cat, id);
    refresh();
    if (copy != null) statusLabel.setText("Duplicated as \"" + CustomAssets.str(copy, "name") + "\".");
}

private void confirmDelete(Component anchor, String cat, Map<String, Object> a) {
    String name = CustomAssets.str(a, "name");
    String extra = "";
    if (CustomAssets.CLASSES.equals(cat) && !CustomAssets.subclassesFor(name).isEmpty()) {
        extra = " Its subclasses stay in the library but won't be offered until a class with this name exists again.";
    } else if (CustomAssets.NPCS.equals(cat)) {
        extra = " It is also unlinked from any map pins.";
    }
    App.showConfirmOverlay(anchor, "Delete " + CustomAssets.SINGULAR.get(cat),
            "Delete \"" + name + "\"? Characters that use it keep the name, but its homebrew rules will no longer apply." + extra,
            "Delete", () -> {
                CustomAssets.delete(cat, CustomAssets.str(a, "id"));
                statusLabel.setText("Deleted \"" + name + "\".");
                refresh();
            });
}

private void openMap(String id, boolean edit) {
    mapEditor.open(id, edit, "ASSETS", this::refresh);
}

// ===============================================================
// Detail overlay (static: also used by map pins for linked NPCs)
// ===============================================================
static class DetailActions {
    Runnable edit, duplicate, openMap, editMap;
    java.util.function.Consumer<Component> delete;
}

static void showDetail(Component anchor, String cat, Map<String, Object> a, DetailActions actions) {
    JPanel body = new JPanel();
    body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
    body.setOpaque(false);

    JLabel kind = new JLabel(("Homebrew " + CustomAssets.SINGULAR.get(cat)).toUpperCase());
    kind.setForeground(App.TEXT_MUTED);
    kind.setFont(new Font("SansSerif", Font.BOLD, 11));
    add(body, kind);
    String sum = CustomAssets.summary(cat, a);
    if (!sum.isEmpty()) {
        JLabel s = new JLabel("<html><div style='width:420px'>" + App.escapeHtml(sum) + "</div></html>");
        s.setForeground(App.TEXT_LIGHT);
        s.setFont(new Font("SansSerif", Font.ITALIC, 13));
        s.setBorder(new EmptyBorder(4, 0, 6, 0));
        add(body, s);
    }

    // Short fields as a label/value table; long ones as sections below
    JPanel table = new JPanel(new GridBagLayout());
    table.setOpaque(false);
    GridBagConstraints c = new GridBagConstraints();
    c.insets = new Insets(2, 0, 2, 0);
    c.anchor = GridBagConstraints.WEST;
    int row = 0;
    List<JComponent> sections = new ArrayList<>();

    for (CustomAssets.Field f : CustomAssets.SCHEMAS.get(cat)) {
        if ("name".equals(f.key)) continue;
        switch (f.type) {
            case CustomAssets.TEXT:
            case CustomAssets.INT:
            case CustomAssets.CHOICE:
            case CustomAssets.BOOL: {
                String v = displayValue(cat, f, a);
                if (v.isEmpty()) break;
                c.gridy = row++;
                c.gridx = 0;
                c.weightx = 0;
                JLabel l = new JLabel(f.label.replace(" (ft.)", "").replace(" (squares)", ""));
                l.setForeground(App.TEXT_MUTED);
                l.setFont(new Font("SansSerif", Font.PLAIN, 13));
                l.setBorder(new EmptyBorder(0, 0, 0, 18));
                table.add(l, c);
                c.gridx = 1;
                c.weightx = 1;
                JLabel val = new JLabel("<html><div style='width:250px'>" + App.escapeHtml(v) + "</div></html>");
                val.setForeground(f.link != null ? App.GOLD : App.TEXT_LIGHT);
                val.setFont(new Font("SansSerif", Font.BOLD, 13));
                if (f.link != null) {
                    String raw = CustomAssets.str(a, f.key);
                    App.clickable(val, () -> openLink(val, f.link, raw));
                }
                table.add(val, c);
                break;
            }
            case CustomAssets.MULTI: {
                List<String> vals = CustomAssets.list(a, f.key);
                if (vals.isEmpty()) break;
                sections.add(chipSection(f, vals));
                break;
            }
            case CustomAssets.AREA: {
                String v = CustomAssets.str(a, f.key).trim();
                if (v.isEmpty()) break;
                JPanel sec = section(f.label);
                JEditorPane txt = App.conditionLinkedText(v, 420, 13, App.TEXT_LIGHT, null);
                txt.setAlignmentX(Component.LEFT_ALIGNMENT);
                sec.add(txt);
                sections.add(sec);
                break;
            }
            case CustomAssets.LIST: {
                List<String> vals = CustomAssets.list(a, f.key);
                if (vals.isEmpty()) break;
                JPanel sec = section(f.label);
                for (String v : vals) {
                    JLabel l = new JLabel("• " + v);
                    l.setForeground(App.TEXT_LIGHT);
                    l.setFont(new Font("SansSerif", Font.PLAIN, 13));
                    l.setBorder(new EmptyBorder(1, 4, 1, 4));
                    l.setAlignmentX(Component.LEFT_ALIGNMENT);
                    String desc = CustomAssets.itemDescription(v);
                    App.clickable(l, () -> App.showInfoOverlay(l, v, f.label, desc != null ? desc
                            : "Listed under " + f.label.toLowerCase() + " for " + CustomAssets.str(a, "name") + ".", null));
                    sec.add(l);
                }
                sections.add(sec);
                break;
            }
            case CustomAssets.ABILITIES:
                sections.add(abilitySection(a));
                break;
            case CustomAssets.ENTRIES: {
                List<Map<String, Object>> es = CustomAssets.entries(a, f.key);
                if (es.isEmpty()) break;
                if (f.withLevel) es.sort(Comparator.comparingInt(e -> CustomAssets.intVal(e, "level", 1)));
                JPanel sec = section(f.label);
                for (Map<String, Object> e : es) sec.add(entryCard(cat, a, f, e));
                sections.add(sec);
                break;
            }
            default:
        }
    }
    if (row > 0) {
        table.setAlignmentX(Component.LEFT_ALIGNMENT);
        table.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, App.SHEET_BORDER), new EmptyBorder(8, 0, 4, 0)));
        add(body, table);
    }
    for (JComponent s : sections) add(body, s);

    // Category extras
    JPanel extra = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    extra.setOpaque(false);
    if (CustomAssets.SPELLS.equals(cat)) {
        JButton card = App.overlayButton("View Spell Card", false);
        card.addActionListener(e -> App.showSpellOverlay(card, CustomAssets.toSpell(a), CustomAssets.str(a, "name"), "Homebrew spell", false));
        extra.add(card);
    }
    if (CustomAssets.SUBCLASSES.equals(cat) || CustomAssets.CLASSES.equals(cat)) {
        String cls = CustomAssets.CLASSES.equals(cat) ? CustomAssets.str(a, "name") : CustomAssets.str(a, "parentClass");
        List<Map<String, Object>> subs = CustomAssets.subclassesFor(cls);
        if (CustomAssets.CLASSES.equals(cat) && !subs.isEmpty()) {
            JPanel sec = section("Subclasses");
            for (Map<String, Object> s : subs) {
                JLabel l = new JLabel("• " + CustomAssets.str(s, "name"));
                l.setForeground(App.GOLD);
                l.setFont(new Font("SansSerif", Font.BOLD, 13));
                l.setBorder(new EmptyBorder(1, 4, 1, 4));
                App.clickable(l, () -> showDetail(l, CustomAssets.SUBCLASSES, s, null));
                sec.add(l);
            }
            add(body, sec);
        }
    }
    if (actions != null && actions.openMap != null) {
        JButton open = App.overlayButton("Open Map", true);
        JButton edit = App.overlayButton("Edit Map", false);
        extra.add(open);
        extra.add(edit);
        Runnable[] holder = new Runnable[1];
        open.addActionListener(e -> { holder[0].run(); actions.openMap.run(); });
        edit.addActionListener(e -> { holder[0].run(); actions.editMap.run(); });
        closeHolderForExtras = holder;
    }
    if (extra.getComponentCount() > 0) {
        extra.setBorder(new EmptyBorder(12, 0, 0, 0));
        add(body, extra);
    }

    JScrollPane scroll = new JScrollPane(body);
    App.applyDarkScrollBar(scroll);
    scroll.getViewport().setBackground(App.SHEET_CARD);
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    JRootPane root = SwingUtilities.getRootPane(anchor);
    int maxH = root != null ? Math.max(260, (int) (root.getHeight() * 0.72)) : 520;
    Dimension pref = body.getPreferredSize();
    scroll.setPreferredSize(new Dimension(Math.max(440, pref.width + 18), Math.min(maxH, pref.height + 6)));

    JPanel wrap = new JPanel(new BorderLayout(0, 12));
    wrap.setOpaque(false);
    wrap.add(scroll, BorderLayout.CENTER);
    Runnable[] close = new Runnable[1];
    if (actions != null) {
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton del = App.overlayButton("Delete", false);
        del.setForeground(App.ACCENT_HOVER);
        JButton dup = App.overlayButton("Duplicate", false);
        JButton edit = App.overlayButton("Edit", true);
        del.addActionListener(e -> { close[0].run(); actions.delete.accept(anchor); });
        dup.addActionListener(e -> { close[0].run(); actions.duplicate.run(); });
        edit.addActionListener(e -> { close[0].run(); actions.edit.run(); });
        buttons.add(del);
        buttons.add(dup);
        buttons.add(edit);
        wrap.add(buttons, BorderLayout.SOUTH);
    }
    Runnable[] extrasClose = closeHolderForExtras;
    closeHolderForExtras = null;
    close[0] = App.showSheetOverlay(anchor, CustomAssets.str(a, "name"), wrap, null);
    if (extrasClose != null) extrasClose[0] = close[0];
}

// Lets the Open/Edit Map buttons (built before the overlay exists) close it
private static Runnable[] closeHolderForExtras;

private static void add(JPanel body, JComponent c) {
    c.setAlignmentX(Component.LEFT_ALIGNMENT);
    body.add(c);
}

private static String displayValue(String cat, CustomAssets.Field f, Map<String, Object> a) {
    switch (f.type) {
        case CustomAssets.BOOL:
            return CustomAssets.bool(a, f.key) ? "Yes" : "";
        case CustomAssets.INT: {
            int v = CustomAssets.intVal(a, f.key, 0);
            if ("strRequirement".equals(f.key) || "darkvision".equals(f.key)) return v > 0 ? String.valueOf(v) + ("darkvision".equals(f.key) ? " ft." : "") : "";
            if ("level".equals(f.key) && CustomAssets.SPELLS.equals(cat)) return v == 0 ? "Cantrip" : String.valueOf(v);
            if ("baseAC".equals(f.key)) return CustomAssets.acText(a);
            if ("speed".equals(f.key)) return v + " ft.";
            if (("startingGold".equals(f.key) || "packageBGold".equals(f.key))) return v > 0 ? v + " GP" : "";
            return String.valueOf(v);
        }
        case CustomAssets.CHOICE: {
            String v = CustomAssets.str(a, f.key);
            if ("dexCap".equals(f.key)) return "";  // folded into the AC text
            if ("mastery".equals(f.key) && "None".equals(v)) return "";
            if ("spellListFrom".equals(f.key) && CustomAssets.OWN_SPELL_LIST.equals(v)) return "";
            if ("spellcastingAbility".equals(f.key) && "None".equals(CustomAssets.str(a, "spellcasting"))) return "";
            return v;
        }
        default:
            return CustomAssets.str(a, f.key).trim();
    }
}

private static JPanel section(String title) {
    JPanel sec = new JPanel();
    sec.setLayout(new BoxLayout(sec, BoxLayout.Y_AXIS));
    sec.setOpaque(false);
    sec.setBorder(new EmptyBorder(10, 0, 0, 0));
    JLabel h = new JLabel(title.toUpperCase());
    h.setForeground(App.GOLD);
    h.setFont(new Font("SansSerif", Font.BOLD, 11));
    h.setBorder(new EmptyBorder(0, 0, 4, 0));
    h.setAlignmentX(Component.LEFT_ALIGNMENT);
    sec.add(h);
    return sec;
}

private static JComponent chipSection(CustomAssets.Field f, List<String> vals) {
    JPanel sec = section(f.label);
    JPanel chips = new JPanel(new GridLayout(0, 3, 4, 4));
    chips.setOpaque(false);
    chips.setAlignmentX(Component.LEFT_ALIGNMENT);
    for (String v : vals) {
        JLabel chip = new JLabel(v, SwingConstants.CENTER);
        chip.setForeground(f.link != null ? App.GOLD : App.TEXT_LIGHT);
        chip.setFont(new Font("SansSerif", Font.BOLD, 12));
        chip.setOpaque(true);
        chip.setBackground(App.SHEET_ROW_ALT);
        chip.setBorder(BorderFactory.createCompoundBorder(new LineBorder(App.SHEET_BORDER, 1, true), new EmptyBorder(4, 6, 4, 6)));
        String link = f.link;
        App.clickable(chip, () -> {
            if (link != null) openLink(chip, link, v);
            else App.showInfoOverlay(chip, v, f.label, propertyInfo(f.key, v), null);
        });
        chips.add(chip);
    }
    sec.add(chips);
    return sec;
}

static String propertyInfo(String v) {
    switch (v) {
        case "Ammunition": return "You need ammunition to make a ranged attack with this weapon; drawing it is part of the attack. You can recover half your spent ammunition after a fight.";
        case "Finesse": return "Use your choice of STR or DEX for the attack and damage rolls.";
        case "Heavy": return "Disadvantage on attack rolls with this weapon if your STR (melee) or DEX (ranged) is below 13.";
        case "Light": return "When you attack with a Light weapon, you can make one extra attack as a Bonus Action with a different Light weapon (no ability modifier to its damage unless negative).";
        case "Loading": return "You can fire only one piece of ammunition from this weapon per action, bonus action or reaction, regardless of extra attacks.";
        case "Range": return "The weapon can make ranged attacks: normal range, then long range at Disadvantage.";
        case "Reach": return "Adds 5 ft. to your reach when you attack with this weapon, and for Opportunity Attacks.";
        case "Thrown": return "You can throw the weapon to make a ranged attack, using the same ability modifier as for a melee attack.";
        case "Two-Handed": return "Requires two hands when you attack with it.";
        case "Versatile": return "Can be used with one or two hands; the damage in parentheses applies when used two-handed.";
        case "V": return "Verbal: you must be able to speak to cast the spell.";
        case "S": return "Somatic: you need a free hand to perform the spell's gestures.";
        case "M": return "Material: you need the listed material, a component pouch, or a spellcasting focus.";
        default:
            return v + ".";
    }
}

static String propertyInfo(String fieldKey, String v) {
    if ("armorTraining".equals(fieldKey)) {
        return "Shields".equals(v) ? "Training with Shields: wield one for +2 AC without penalty."
                : "Training with " + v + " armor: wear it without Disadvantage on STR/DEX tests or losing the ability to cast spells.";
    }
    if ("weaponProficiencies".equals(fieldKey)) {
        return "Proficiency with all " + v + " weapons: add your Proficiency Bonus to attack rolls with them.";
    }
    return propertyInfo(v);
}

static void openLink(Component anchor, String link, String value) {
    switch (link) {
        case CustomAssets.LINK_SKILL:
            App.showInfoOverlay(anchor, value, GameData.SKILL_ABILITY.getOrDefault(value, "") + " skill",
                    GameData.SKILL_INFO.getOrDefault(value, ""), null);
            break;
        case CustomAssets.LINK_CONDITION:
            App.showConditionOverlay(anchor, value);
            break;
        case CustomAssets.LINK_STAT:
            App.showInfoOverlay(anchor, value, "Ability score", GameData.STAT_INFO.getOrDefault(value, ""), null);
            break;
        case CustomAssets.LINK_LANGUAGE:
            App.showInfoOverlay(anchor, value, "Language", GameData.LANGUAGE_INFO.getOrDefault(value, "A language."), null);
            break;
        case CustomAssets.LINK_FEAT: {
            String d = CustomAssets.featDescription(value);
            App.showInfoOverlay(anchor, value, CustomAssets.ORIGIN_FEATS.containsKey(value) ? "Origin feat" : "Feat",
                    d.isEmpty() ? "A homebrew feat." : d, null);
            break;
        }
        case CustomAssets.LINK_CLASS: {
            Map<String, Object> ca = CustomAssets.classAsset(value);
            if (ca != null) {
                showDetail(anchor, CustomAssets.CLASSES, ca, null);
            } else {
                String[] info = GameData.CLASS_INFO.get(value);
                App.showInfoOverlay(anchor, value, "Class", info != null ? info[1] : "",
                        info != null ? Collections.singletonList(App.row("Hit die", info[0])) : null);
            }
            break;
        }
        default:
    }
}

private static JComponent abilitySection(Map<String, Object> a) {
    JPanel sec = section("Ability scores");
    JPanel grid = new JPanel(new GridLayout(1, 6, 4, 0));
    grid.setOpaque(false);
    grid.setAlignmentX(Component.LEFT_ALIGNMENT);
    for (Map.Entry<String, Integer> en : CustomAssets.abilities(a).entrySet()) {
        JPanel box = new JPanel(new GridLayout(3, 1));
        box.setBackground(App.SHEET_ROW_ALT);
        box.setBorder(BorderFactory.createCompoundBorder(new LineBorder(App.SHEET_BORDER, 1, true), new EmptyBorder(4, 4, 4, 4)));
        JLabel k = new JLabel(en.getKey(), SwingConstants.CENTER);
        k.setForeground(App.TEXT_MUTED);
        k.setFont(new Font("SansSerif", Font.BOLD, 10));
        JLabel v = new JLabel(String.valueOf(en.getValue()), SwingConstants.CENTER);
        v.setForeground(App.TEXT_LIGHT);
        v.setFont(new Font("SansSerif", Font.BOLD, 15));
        JLabel m = new JLabel(App.signed(GameData.abilityModifier(en.getValue())), SwingConstants.CENTER);
        m.setForeground(App.GOLD);
        m.setFont(new Font("SansSerif", Font.PLAIN, 11));
        box.add(k);
        box.add(v);
        box.add(m);
        String stat = en.getKey();
        int score = en.getValue();
        App.clickable(box, () -> App.showInfoOverlay(box, stat + " " + score, "Ability score", GameData.STAT_INFO.get(stat),
                Arrays.asList(App.row("Score", String.valueOf(score)), App.row("Modifier", App.signed(GameData.abilityModifier(score))))));
        grid.add(box);
    }
    sec.add(grid);
    return sec;
}

private static JComponent entryCard(String cat, Map<String, Object> a, CustomAssets.Field f, Map<String, Object> e) {
    String title = CustomAssets.str(e, "name").isEmpty() ? "Untitled" : CustomAssets.str(e, "name");
    String desc = CustomAssets.str(e, "description");
    String[] words = f.label.split(" ");
    String kind = words[words.length - 1].replaceAll("s$", ""); // "Species traits" -> "trait", "Actions" -> "Action"
    String sub = f.withLevel ? "Level " + CustomAssets.intVal(e, "level", 1) : kind;
    JPanel card = new JPanel(new BorderLayout());
    card.setBackground(App.SHEET_ROW_ALT);
    card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createCompoundBorder(new EmptyBorder(0, 0, 4, 0), BorderFactory.createMatteBorder(0, 3, 0, 0, App.GOLD)),
            new EmptyBorder(6, 10, 6, 10)));
    card.setAlignmentX(Component.LEFT_ALIGNMENT);
    JLabel l = new JLabel("<html><div style='width:390px'><b style='color:#d4af37'>" + App.escapeHtml(title) + "</b>"
            + " <span style='color:#aaa5a0; font-size:9px'>" + App.escapeHtml(sub.toUpperCase()) + "</span>"
            + (desc.isEmpty() ? "" : "<br>" + App.escapeHtml(CustomAssets.snippet(desc, 160))) + "</div></html>");
    l.setForeground(App.TEXT_LIGHT);
    l.setFont(new Font("SansSerif", Font.PLAIN, 12));
    card.add(l, BorderLayout.CENTER);
    String source = CustomAssets.str(a, "name") + (f.withLevel ? " · level " + CustomAssets.intVal(e, "level", 1) : "");
    App.clickable(card, () -> App.showInfoOverlay(card, title, source, desc.isEmpty() ? "No description." : desc, null));
    return card;
}

// ===============================================================
// Editor
// ===============================================================
private interface FieldUI {
    JComponent component();
    Object value();
}

// Opens the create / edit form for `existing` (null = new asset in the current category)
void openEditor(Map<String, Object> existing) {
    String cat = category;
    boolean isNew = existing == null;
    Map<String, Object> base = isNew ? CustomAssets.newAsset(cat) : CustomAssets.deepCopy(existing);

    editorHolder.removeAll();
    JPanel shell = new JPanel(new BorderLayout());
    shell.setBackground(App.SHEET_BG);

    JLabel title = new JLabel((isNew ? "New " : "Edit ") + CustomAssets.SINGULAR.get(cat)
            + (isNew ? "" : ": " + CustomAssets.str(existing, "name")));
    title.setForeground(App.GOLD);
    title.setFont(new Font("Serif", Font.BOLD, 24));
    title.setBorder(new EmptyBorder(16, 24, 4, 24));
    shell.add(title, BorderLayout.NORTH);

    JPanel form = new JPanel();
    form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
    form.setBackground(App.SHEET_BG);
    form.setBorder(new EmptyBorder(8, 24, 24, 24));

    Map<String, FieldUI> uis = new LinkedHashMap<>();
    JLabel overrideNote = new JLabel(" ");
    overrideNote.setForeground(App.TEXT_MUTED);
    overrideNote.setFont(new Font("SansSerif", Font.ITALIC, 12));

    for (CustomAssets.Field f : CustomAssets.SCHEMAS.get(cat)) {
        if (f.section != null) {
            JLabel sec = new JLabel(f.section.toUpperCase());
            sec.setForeground(App.TEXT_MUTED);
            sec.setFont(new Font("SansSerif", Font.BOLD, 12));
            sec.setBorder(BorderFactory.createCompoundBorder(
                    new EmptyBorder(14, 0, 6, 0), BorderFactory.createMatteBorder(0, 0, 1, 0, App.SHEET_BORDER)));
            sec.setAlignmentX(Component.LEFT_ALIGNMENT);
            sec.setMaximumSize(new Dimension(760, 40));
            form.add(sec);
        }
        FieldUI ui = buildFieldUI(f, base.get(f.key));
        uis.put(f.key, ui);
        form.add(fieldBlock(f, ui.component()));
        if ("name".equals(f.key)) {
            JTextField nameField = (JTextField) ui.component();
            Runnable check = () -> {
                String o = CustomAssets.overridesBuiltIn(cat, nameField.getText());
                overrideNote.setText(o == null ? " " : "Replaces the built-in \"" + o + "\" while this asset exists (deleting it restores the original).");
            };
            nameField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { check.run(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { check.run(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { check.run(); }
            });
            check.run();
            overrideNote.setAlignmentX(Component.LEFT_ALIGNMENT);
            form.add(overrideNote);
        }
    }

    JPanel formWrap = new JPanel(new App.MaxWidthCenterLayout(800));
    formWrap.setBackground(App.SHEET_BG);
    formWrap.add(form);
    JScrollPane scroll = new JScrollPane(formWrap);
    App.applyDarkScrollBar(scroll);
    scroll.getViewport().setBackground(App.SHEET_BG);
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    shell.add(scroll, BorderLayout.CENTER);

    JLabel error = new JLabel(" ");
    error.setForeground(App.ACCENT_HOVER);
    error.setFont(new Font("SansSerif", Font.BOLD, 13));
    JButton cancel = App.overlayButton("Cancel", false);
    JButton save = App.overlayButton(isNew ? "Create " + CustomAssets.SINGULAR.get(cat) : "Save Changes", true);
    cancel.addActionListener(e -> {
        innerLayout.show(inner, "LIST");
        refresh();
    });
    save.addActionListener(e -> {
        Map<String, Object> out = CustomAssets.deepCopy(base);
        for (Map.Entry<String, FieldUI> en : uis.entrySet()) out.put(en.getKey(), en.getValue().value());
        out.put("name", CustomAssets.str(out, "name").trim());
        String problem = CustomAssets.validate(cat, out);
        if (problem != null) {
            error.setText(problem);
            return;
        }
        if (CustomAssets.MAPS.equals(cat)) {
            MapEditorPanel.MapModel m = MapEditorPanel.MapModel.fromJson(out.get("grid"));
            int w = CustomAssets.intVal(out, "width", m.width), h = CustomAssets.intVal(out, "height", m.height);
            if (w != m.width || h != m.height) out.put("grid", m.resized(w, h).toJson());
        }
        CustomAssets.put(cat, out);
        statusLabel.setText((isNew ? "Created \"" : "Saved \"") + CustomAssets.str(out, "name") + "\".");
        if (isNew && CustomAssets.MAPS.equals(cat)) {
            openMap(CustomAssets.str(out, "id"), true); // straight into the grid editor
        } else {
            innerLayout.show(inner, "LIST");
            refresh();
        }
    });
    JPanel bottom = new JPanel(new BorderLayout());
    bottom.setBackground(App.BG_PANEL);
    bottom.setBorder(new EmptyBorder(10, 24, 10, 24));
    bottom.add(error, BorderLayout.CENTER);
    JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    btns.setOpaque(false);
    btns.add(cancel);
    btns.add(save);
    bottom.add(btns, BorderLayout.EAST);
    shell.add(bottom, BorderLayout.SOUTH);

    editorHolder.add(shell, BorderLayout.CENTER);
    editorHolder.revalidate();
    editorHolder.repaint();
    innerLayout.show(inner, "EDIT");
    SwingUtilities.invokeLater(() -> uis.get("name").component().requestFocusInWindow());
}

private static JComponent fieldBlock(CustomAssets.Field f, JComponent input) {
    JPanel block = new JPanel();
    block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
    block.setOpaque(false);
    block.setBorder(new EmptyBorder(6, 0, 6, 0));
    block.setAlignmentX(Component.LEFT_ALIGNMENT);
    if (!CustomAssets.BOOL.equals(f.type)) {
        JLabel l = new JLabel(f.label);
        l.setForeground(App.GOLD);
        l.setFont(new Font("SansSerif", Font.BOLD, 14));
        l.setBorder(new EmptyBorder(0, 0, 4, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        block.add(l);
    }
    input.setAlignmentX(Component.LEFT_ALIGNMENT);
    block.add(input);
    if (!f.help.isEmpty()) {
        JLabel h = new JLabel("<html><div style='width:560px'>" + App.escapeHtml(f.help) + "</div></html>");
        h.setForeground(App.TEXT_MUTED);
        h.setFont(new Font("SansSerif", Font.PLAIN, 12));
        h.setBorder(new EmptyBorder(3, 0, 0, 0));
        h.setAlignmentX(Component.LEFT_ALIGNMENT);
        block.add(h);
    }
    return block;
}

private static JTextField textField(String value, String hint) {
    JTextField tf = new JTextField(value, 32);
    App.styleTextField(tf);
    tf.setFont(new Font("SansSerif", Font.PLAIN, 14));
    tf.setMaximumSize(new Dimension(560, 38));
    if (!hint.isEmpty()) tf.setToolTipText(hint);
    return tf;
}

private static JTextArea textArea(String value, int rows) {
    JTextArea ta = new JTextArea(value, rows, 40);
    ta.setLineWrap(true);
    ta.setWrapStyleWord(true);
    ta.setBackground(App.BG_FIELD);
    ta.setForeground(App.TEXT_LIGHT);
    ta.setCaretColor(App.TEXT_LIGHT);
    ta.setFont(new Font("SansSerif", Font.PLAIN, 14));
    ta.setBorder(new EmptyBorder(6, 8, 6, 8));
    return ta;
}

private static JScrollPane areaScroll(JTextArea ta, int width) {
    JScrollPane sp = new JScrollPane(ta);
    App.applyDarkScrollBar(sp);
    sp.setBorder(new LineBorder(App.TEXT_MUTED, 1, true));
    sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    Dimension d = new Dimension(width, ta.getPreferredSize().height + 6);
    sp.setPreferredSize(d);
    sp.setMaximumSize(new Dimension(width, d.height));
    return sp;
}

private static JSpinner spinner(int value, int min, int max) {
    JSpinner sp = new JSpinner(new SpinnerNumberModel(Math.max(min, Math.min(max, value)), min, max, 1));
    sp.setFont(new Font("SansSerif", Font.PLAIN, 14));
    Dimension d = new Dimension(80, 30);
    sp.setPreferredSize(d);
    sp.setMaximumSize(d);
    JComponent ed = sp.getEditor();
    if (ed instanceof JSpinner.DefaultEditor) {
        JFormattedTextField tf = ((JSpinner.DefaultEditor) ed).getTextField();
        tf.setBackground(App.BG_FIELD);
        tf.setForeground(App.TEXT_LIGHT);
        tf.setCaretColor(App.TEXT_LIGHT);
    }
    return sp;
}

private static FieldUI ui(JComponent c, Supplier<Object> v) {
    return new FieldUI() {
        public JComponent component() { return c; }
        public Object value() { return v.get(); }
    };
}

private FieldUI buildFieldUI(CustomAssets.Field f, Object current) {
    Map<String, Object> holder = new HashMap<>();
    holder.put("v", current);
    switch (f.type) {
        case CustomAssets.TEXT: {
            JTextField tf = textField(CustomAssets.str(holder, "v"), f.placeholder);
            return ui(tf, () -> tf.getText().trim());
        }
        case CustomAssets.AREA: {
            JTextArea ta = textArea(CustomAssets.str(holder, "v"), 4);
            return ui(areaScroll(ta, 560), () -> ta.getText().trim());
        }
        case CustomAssets.INT: {
            int def = f.def instanceof Number ? ((Number) f.def).intValue() : 0;
            JSpinner sp = spinner(CustomAssets.intVal(holder, "v", def), f.min, f.max);
            return ui(sp, () -> (Integer) sp.getValue());
        }
        case CustomAssets.BOOL: {
            JCheckBox cb = new JCheckBox(f.label, Boolean.TRUE.equals(current));
            cb.setForeground(App.TEXT_LIGHT);
            cb.setBackground(App.SHEET_BG);
            cb.setFont(new Font("SansSerif", Font.BOLD, 14));
            cb.setFocusPainted(false);
            cb.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            return ui(cb, cb::isSelected);
        }
        case CustomAssets.CHOICE: {
            List<String> opts = new ArrayList<>(f.optionList());
            String cur = CustomAssets.str(holder, "v");
            if (!cur.isEmpty() && !opts.contains(cur)) opts.add(0, cur);
            if (f.editable && !opts.contains("")) opts.add(0, "");
            JComboBox<String> combo = new JComboBox<>(opts.toArray(new String[0]));
            App.styleCombo(combo);
            combo.setEditable(f.editable);
            combo.setSelectedItem(cur.isEmpty() && !f.editable && !opts.isEmpty() ? opts.get(0) : cur);
            combo.setMaximumSize(new Dimension(360, 32));
            combo.setPreferredSize(new Dimension(300, 32));
            combo.setMaximumRowCount(14);
            if (f.editable && combo.getEditor().getEditorComponent() instanceof JTextField) {
                JTextField ed = (JTextField) combo.getEditor().getEditorComponent();
                ed.setBackground(App.BG_FIELD);
                ed.setForeground(App.TEXT_LIGHT);
                ed.setCaretColor(App.TEXT_LIGHT);
            }
            return ui(combo, () -> {
                Object v = f.editable ? combo.getEditor().getItem() : combo.getSelectedItem();
                return v == null ? "" : String.valueOf(v).trim();
            });
        }
        case CustomAssets.MULTI: {
            List<String> opts = new ArrayList<>(f.optionList());
            LinkedHashSet<String> selected = new LinkedHashSet<>(CustomAssets.list(holder, "v"));
            for (String s : selected) if (!opts.contains(s)) opts.add(s); // keep values whose option disappeared
            int cols = opts.size() <= 4 ? opts.size() : opts.size() <= 9 ? 3 : 4;
            JPanel grid = new JPanel(new GridLayout(0, Math.max(1, cols), 6, 6));
            grid.setOpaque(false);
            for (String o : opts) grid.add(toggleChip(o, selected));
            if (opts.isEmpty()) {
                JLabel none = new JLabel("No options available yet.");
                none.setForeground(App.TEXT_MUTED);
                grid.add(none);
            }
            JPanel wrap = new JPanel(new BorderLayout());
            wrap.setOpaque(false);
            wrap.add(grid, BorderLayout.WEST);
            wrap.setMaximumSize(new Dimension(760, Integer.MAX_VALUE));
            return ui(wrap, () -> {
                List<Object> out = new ArrayList<>();
                for (String o : opts) if (selected.contains(o)) out.add(o);
                return out;
            });
        }
        case CustomAssets.LIST: {
            JTextArea ta = textArea(String.join("\n", CustomAssets.list(holder, "v")), 3);
            return ui(areaScroll(ta, 560), () -> {
                List<Object> out = new ArrayList<>();
                for (String line : ta.getText().split("\\r?\\n")) if (!line.trim().isEmpty()) out.add(line.trim());
                return out;
            });
        }
        case CustomAssets.ABILITIES: {
            Map<String, Object> wrapA = new HashMap<>();
            wrapA.put("abilities", current);
            Map<String, Integer> scores = CustomAssets.abilities(wrapA);
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            row.setOpaque(false);
            Map<String, JSpinner> spins = new LinkedHashMap<>();
            for (String s : GameData.STAT_ORDER) {
                JPanel cell = new JPanel(new BorderLayout(0, 2));
                cell.setOpaque(false);
                JLabel l = new JLabel(s, SwingConstants.CENTER);
                l.setForeground(App.TEXT_MUTED);
                l.setFont(new Font("SansSerif", Font.BOLD, 11));
                JSpinner sp = spinner(scores.get(s), 1, 30);
                sp.setPreferredSize(new Dimension(64, 30));
                JLabel mod = new JLabel(App.signed(GameData.abilityModifier(scores.get(s))), SwingConstants.CENTER);
                mod.setForeground(App.GOLD);
                mod.setFont(new Font("SansSerif", Font.PLAIN, 11));
                sp.addChangeListener(e -> mod.setText(App.signed(GameData.abilityModifier((Integer) sp.getValue()))));
                cell.add(l, BorderLayout.NORTH);
                cell.add(sp, BorderLayout.CENTER);
                cell.add(mod, BorderLayout.SOUTH);
                row.add(cell);
                spins.put(s, sp);
            }
            row.setMaximumSize(new Dimension(760, 80));
            return ui(row, () -> {
                Map<String, Object> out = new LinkedHashMap<>();
                for (Map.Entry<String, JSpinner> en : spins.entrySet()) out.put(en.getKey(), en.getValue().getValue());
                return out;
            });
        }
        case CustomAssets.ENTRIES:
            return entriesUI(f, CustomAssets.entries(holder, "v"));
        default:
            JTextField tf = textField(CustomAssets.str(holder, "v"), "");
            return ui(tf, tf::getText);
    }
}

private static JComponent toggleChip(String option, Set<String> selected) {
    JLabel chip = new JLabel(option, SwingConstants.CENTER);
    chip.setFont(new Font("SansSerif", Font.BOLD, 13));
    chip.setOpaque(true);
    chip.setPreferredSize(new Dimension(Math.max(120, chip.getPreferredSize().width + 34), 32));
    Runnable paint = () -> {
        boolean on = selected.contains(option);
        chip.setText((on ? "☑ " : "☐ ") + option);
        chip.setBackground(on ? new Color(70, 60, 38) : App.SHEET_ROW_ALT);
        chip.setForeground(on ? App.GOLD : App.TEXT_LIGHT);
        chip.setBorder(BorderFactory.createCompoundBorder(new LineBorder(on ? App.GOLD : App.SHEET_BORDER, 1, true), new EmptyBorder(4, 8, 4, 8)));
    };
    paint.run();
    chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    chip.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseReleased(MouseEvent e) {
            if (!App.isClickRelease(e)) return;
            if (!selected.remove(option)) selected.add(option);
            paint.run();
        }
        @Override
        public void mouseEntered(MouseEvent e) {
            if (!selected.contains(option)) chip.setBackground(App.SHEET_HOVER);
        }
        @Override
        public void mouseExited(MouseEvent e) { paint.run(); }
    });
    return chip;
}

// Repeating name + description rows (with a level for class / subclass features)
private FieldUI entriesUI(CustomAssets.Field f, List<Map<String, Object>> initial) {
    JPanel outer = new JPanel();
    outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS));
    outer.setOpaque(false);
    JPanel rows = new JPanel();
    rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
    rows.setOpaque(false);
    rows.setAlignmentX(Component.LEFT_ALIGNMENT);
    List<Supplier<Map<String, Object>>> readers = new ArrayList<>();
    List<JComponent> rowComps = new ArrayList<>();

    String noun = f.withLevel ? "feature" : CustomAssets.NPCS.equals(category) && "actions".equals(f.key) ? "action" : "trait";
    JLabel empty = new JLabel("None yet.");
    empty.setForeground(App.TEXT_MUTED);
    empty.setFont(new Font("SansSerif", Font.PLAIN, 13));
    empty.setAlignmentX(Component.LEFT_ALIGNMENT);

    Runnable[] relayout = new Runnable[1];
    java.util.function.Consumer<Map<String, Object>> addRow = e -> {
        JPanel card = new JPanel(new BorderLayout(8, 6));
        card.setBackground(App.SHEET_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(new LineBorder(App.SHEET_BORDER, 1, true), new EmptyBorder(8, 10, 8, 10)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(640, Integer.MAX_VALUE));
        JPanel head = new JPanel(new BorderLayout(8, 0));
        head.setOpaque(false);
        JSpinner level = f.withLevel ? spinner(CustomAssets.intVal(e, "level", 1), 1, 20) : null;
        if (level != null) {
            JPanel lv = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            lv.setOpaque(false);
            JLabel ll = new JLabel("Level");
            ll.setForeground(App.TEXT_MUTED);
            lv.add(ll);
            lv.add(level);
            head.add(lv, BorderLayout.WEST);
        }
        JTextField name = textField(CustomAssets.str(e, "name"), "Name");
        name.setColumns(20);
        head.add(name, BorderLayout.CENTER);
        JButton remove = iconButton("✕", "Remove this " + noun, () -> { });
        head.add(remove, BorderLayout.EAST);
        JTextArea desc = textArea(CustomAssets.str(e, "description"), 3);
        card.add(head, BorderLayout.NORTH);
        card.add(areaScroll(desc, 600), BorderLayout.CENTER);

        Supplier<Map<String, Object>> reader = () -> {
            Map<String, Object> out = new LinkedHashMap<>();
            if (level != null) out.put("level", level.getValue());
            out.put("name", name.getText().trim());
            out.put("description", desc.getText().trim());
            return out;
        };
        readers.add(reader);
        rowComps.add(card);
        remove.addActionListener(ev -> {
            readers.remove(reader);
            rowComps.remove(card);
            relayout[0].run();
        });
        relayout[0].run();
    };
    relayout[0] = () -> {
        rows.removeAll();
        if (rowComps.isEmpty()) rows.add(empty);
        for (JComponent c : rowComps) {
            rows.add(c);
            rows.add(Box.createRigidArea(new Dimension(0, 6)));
        }
        rows.revalidate();
        rows.repaint();
    };
    relayout[0].run();
    for (Map<String, Object> e : initial) addRow.accept(e);

    JButton add = App.overlayButton("+ Add " + noun, false);
    add.setAlignmentX(Component.LEFT_ALIGNMENT);
    add.addActionListener(e -> {
        Map<String, Object> blank = new LinkedHashMap<>();
        if (f.withLevel) {
            int next = 1;
            for (Supplier<Map<String, Object>> r : readers) next = Math.max(next, CustomAssets.intVal(r.get(), "level", 1));
            blank.put("level", next);
        }
        addRow.accept(blank);
    });
    outer.add(rows);
    outer.add(Box.createRigidArea(new Dimension(0, 4)));
    outer.add(add);
    return ui(outer, () -> {
        List<Object> out = new ArrayList<>();
        for (Supplier<Map<String, Object>> r : readers) {
            Map<String, Object> m = r.get();
            if (!CustomAssets.str(m, "name").isEmpty() || !CustomAssets.str(m, "description").isEmpty()) out.add(m);
        }
        return out;
    });
}

// ===============================================================
// Import / Export overlays (in-window: no file chooser dialogs)
// ===============================================================
private static final Set<String> NON_PACK_FILES = new HashSet<>(Arrays.asList(
        "characters.json", CustomAssets.FILE, "spells.json", "weapons.json"));

private void showImport(Component anchor) {
    JPanel body = new JPanel();
    body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
    body.setOpaque(false);

    JTextField path = App.overlayField("", 30);
    List<String> files = new ArrayList<>();
    try (java.nio.file.DirectoryStream<java.nio.file.Path> ds = java.nio.file.Files.newDirectoryStream(DataPaths.userDirectory(), "*.json")) {
        for (java.nio.file.Path p : ds) {
            String n = p.getFileName().toString();
            if (!NON_PACK_FILES.contains(n)) files.add(n);
        }
    } catch (Exception ignored) {
        // folder unreadable: the path field still works
    }
    Collections.sort(files);

    add(body, overlayLabel("Pack file (path to a .json file)"));
    add(body, path);
    if (!files.isEmpty()) {
        add(body, overlayHint("Packs found next to the app (click to pick):"));
        for (String n : files) {
            JLabel l = new JLabel("• " + n);
            l.setForeground(App.GOLD);
            l.setFont(new Font("SansSerif", Font.PLAIN, 13));
            l.setBorder(new EmptyBorder(2, 6, 2, 6));
            App.clickable(l, () -> path.setText(n));
            add(body, l);
        }
    }
    body.add(Box.createRigidArea(new Dimension(0, 10)));
    add(body, overlayLabel("…or paste pack JSON"));
    JTextArea paste = new JTextArea(5, 34);
    paste.setLineWrap(true);
    paste.setBackground(App.SHEET_BG);
    paste.setForeground(App.TEXT_LIGHT);
    paste.setCaretColor(App.TEXT_LIGHT);
    paste.setFont(new Font("Monospaced", Font.PLAIN, 12));
    paste.setBorder(new EmptyBorder(6, 6, 6, 6));
    JScrollPane ps = new JScrollPane(paste);
    ps.setBorder(new LineBorder(App.SHEET_BORDER, 1, true));
    add(body, ps);
    body.add(Box.createRigidArea(new Dimension(0, 10)));
    add(body, overlayLabel("When a name already exists"));
    JComboBox<String> conflict = new JComboBox<>(new String[]{CustomAssets.CONFLICT_KEEP, CustomAssets.CONFLICT_REPLACE, CustomAssets.CONFLICT_SKIP});
    App.styleCombo(conflict);
    conflict.setMaximumSize(new Dimension(260, 30));
    add(body, conflict);

    JLabel error = new JLabel(" ");
    error.setForeground(App.ACCENT_HOVER);
    error.setFont(new Font("SansSerif", Font.BOLD, 12));
    body.add(Box.createRigidArea(new Dimension(0, 8)));
    add(body, error);

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    buttons.setOpaque(false);
    Runnable[] close = new Runnable[1];
    JButton cancel = App.overlayButton("Cancel", false);
    cancel.addActionListener(e -> close[0].run());
    JButton go = App.overlayButton("Import", true);
    go.addActionListener(e -> {
        String json = paste.getText().trim();
        if (json.isEmpty()) {
            String p = path.getText().trim();
            if (p.isEmpty()) {
                error.setText("Pick or type a pack file, or paste JSON.");
                return;
            }
            try {
                json = new String(java.nio.file.Files.readAllBytes(DataPaths.userFile(p)), java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception ex) {
                error.setText("Couldn't read \"" + p + "\" (" + ex.getClass().getSimpleName() + ").");
                return;
            }
        }
        CustomAssets.ImportResult r = CustomAssets.importPack(json, String.valueOf(conflict.getSelectedItem()));
        if (r.error != null) {
            error.setText("<html><div style='width:330px'>" + App.escapeHtml(r.error) + "</div></html>");
            return;
        }
        close[0].run();
        statusLabel.setText(r.summary());
        refresh();
    });
    buttons.add(cancel);
    buttons.add(go);
    buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
    body.add(Box.createRigidArea(new Dimension(0, 6)));
    body.add(buttons);
    close[0] = App.showSheetOverlay(anchor, "Import Homebrew Pack", body, path);
}

private void showExport(Component anchor) {
    JPanel body = new JPanel();
    body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
    body.setOpaque(false);

    add(body, overlayLabel("Categories to include"));
    LinkedHashSet<String> chosen = new LinkedHashSet<>();
    for (String c : CustomAssets.CATEGORIES) if (CustomAssets.count(c) > 0) chosen.add(c);
    JPanel grid = new JPanel(new GridLayout(0, 3, 6, 6));
    grid.setOpaque(false);
    for (String c : CustomAssets.CATEGORIES) {
        JComponent chip = toggleChip(c, chosen);
        chip.setToolTipText(CustomAssets.count(c) + " " + c.toLowerCase());
        grid.add(chip);
    }
    add(body, grid);
    body.add(Box.createRigidArea(new Dimension(0, 10)));
    add(body, overlayLabel("Save as"));
    JTextField path = App.overlayField("homebrew_pack.json", 30);
    add(body, path);
    add(body, overlayHint("Relative paths are saved next to the app: " + DataPaths.userDirectory().toAbsolutePath().normalize()));

    JLabel error = new JLabel(" ");
    error.setForeground(App.ACCENT_HOVER);
    error.setFont(new Font("SansSerif", Font.BOLD, 12));
    body.add(Box.createRigidArea(new Dimension(0, 8)));
    add(body, error);

    boolean[] overwriteOk = {false};
    path.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
        public void insertUpdate(javax.swing.event.DocumentEvent e) { overwriteOk[0] = false; }
        public void removeUpdate(javax.swing.event.DocumentEvent e) { overwriteOk[0] = false; }
        public void changedUpdate(javax.swing.event.DocumentEvent e) { overwriteOk[0] = false; }
    });

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    buttons.setOpaque(false);
    Runnable[] close = new Runnable[1];
    JButton cancel = App.overlayButton("Cancel", false);
    cancel.addActionListener(e -> close[0].run());
    JButton go = App.overlayButton("Export", true);
    go.addActionListener(e -> {
        String p = path.getText().trim();
        if (chosen.isEmpty()) {
            error.setText("Choose at least one category.");
            return;
        }
        if (p.isEmpty()) {
            error.setText("Enter a file name.");
            return;
        }
        if (!p.toLowerCase().endsWith(".json")) p += ".json";
        String fileName = DataPaths.userFile(p).getFileName().toString();
        if (NON_PACK_FILES.contains(fileName)) {
            error.setText("\"" + fileName + "\" is one of the app's own data files; pick another name.");
            return;
        }
        if (java.nio.file.Files.exists(DataPaths.userFile(p)) && !overwriteOk[0]) {
            overwriteOk[0] = true;
            error.setText("\"" + p + "\" already exists. Click Export again to overwrite it.");
            return;
        }
        try {
            int n = CustomAssets.exportPack(chosen, p);
            close[0].run();
            statusLabel.setText("Exported " + n + " assets to " + DataPaths.userFile(p).toAbsolutePath().normalize() + ".");
        } catch (Exception ex) {
            error.setText("Couldn't write \"" + p + "\" (" + ex.getClass().getSimpleName() + ").");
        }
    });
    buttons.add(cancel);
    buttons.add(go);
    buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
    body.add(Box.createRigidArea(new Dimension(0, 6)));
    body.add(buttons);
    close[0] = App.showSheetOverlay(anchor, "Export Homebrew Pack", body, go);
}

private static JLabel overlayLabel(String t) {
    JLabel l = new JLabel(t);
    l.setForeground(App.GOLD);
    l.setFont(new Font("SansSerif", Font.BOLD, 12));
    l.setBorder(new EmptyBorder(0, 0, 4, 0));
    return l;
}

private static JLabel overlayHint(String t) {
    JLabel l = new JLabel("<html><div style='width:340px'>" + App.escapeHtml(t) + "</div></html>");
    l.setForeground(App.TEXT_MUTED);
    l.setFont(new Font("SansSerif", Font.PLAIN, 11));
    l.setBorder(new EmptyBorder(4, 0, 4, 0));
    return l;
}
}
