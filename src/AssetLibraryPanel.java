import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;

// Asset Library: every asset, built-in (official) and homebrew, with one tab per category.
//  - The search bar at the very top searches all categories at once; the Sort menu orders the list by
//    name, by tag / source, or by level / category.
//  - Each tab has its own filter and tag menus, plus School / Level / Class chips on the Spells tab.
//  - Homebrew can be created, edited, duplicated, deleted and imported / exported as JSON packs.
//    Built-in assets are read-only apart from their tags.
//  - "Tags & Filters" creates / deletes custom tags and toggles the campaign content filters (Tags.java).
// Editor forms and homebrew detail overlays are built from CustomAssets.SCHEMAS. Everything stays
// inside the main window (cards + App overlays).
class AssetLibraryPanel {

static final String SORT_NAME = "Name (A–Z)", SORT_SOURCE = "Tag / Source", SORT_LEVEL = "Level / Category";
private static final String ALL = "All", ALL_TAGS = "All tags";
private static final int PAGE = 120; // rows rendered at a time (keeps the browser build responsive)

final JPanel panel;

private final CardLayout outerLayout;
private final JPanel outerCards;
private final MapEditorPanel mapEditor;

private final CardLayout innerLayout = new CardLayout();
private final JPanel inner = new JPanel(innerLayout);

static final String ALL_CAT = "All"; // the first tab: every category combined
private String category = ALL_CAT;
private final JPanel tabColumn = new JPanel();
private final JPanel listWrap = new JPanel();
private final JTextField searchField = new JTextField();
private final JComboBox<String> sortCombo = new JComboBox<>(new String[]{SORT_NAME, SORT_SOURCE, SORT_LEVEL});
private final JComboBox<String> filterCombo = new JComboBox<>();
private final JComboBox<String> tagCombo = new JComboBox<>();
// Per-category search (searches only the open tab; the top bar searches everything)
private final JTextField tabSearch = new JTextField();
private final JLabel tabSearchLabel = new JLabel();
private JButton tabSearchClear;
private final javax.swing.Timer tabSearchTimer = new javax.swing.Timer(150, e -> { shownLimit = PAGE; rebuildList(); });
private final JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
private final JPanel spellChips = new JPanel();
private final JLabel statusLabel = new JLabel(" ");
private final JLabel listTitle = new JLabel();
private final JLabel listCount = new JLabel();
private final JLabel filterNote = new JLabel(" ");
private final JButton createButton = new JButton();
private boolean updatingFilter;
private String spellSchool = ALL, spellLevel = ALL, spellClass = ALL;
private int shownLimit = PAGE;
private final javax.swing.Timer searchTimer;

private final JPanel editorHolder = new JPanel(new BorderLayout());

AssetLibraryPanel(CardLayout outerLayout, JPanel outerCards, MapEditorPanel mapEditor) {
    this.outerLayout = outerLayout;
    this.outerCards = outerCards;
    this.mapEditor = mapEditor;
    searchTimer = new javax.swing.Timer(180, e -> {
        shownLimit = PAGE;
        rebuildTabs();
        rebuildFilter();
        rebuildList();
    });
    searchTimer.setRepeats(false);
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
// Entries: one row in the library (a built-in asset or a homebrew asset)
// ===============================================================
static final class Entry {
    final String cat, name;
    final Map<String, Object> asset; // null = built-in
    final List<String> tags;
    final String summary, filterValue;
    final int level;                 // spells: 0-9, otherwise -1
    final String school;
    final List<String> classes;
    private String haystack;

    private Entry(String cat, String name, Map<String, Object> asset, List<String> tags, String summary, String filterValue,
                  int level, String school, List<String> classes) {
        this.cat = cat;
        this.name = name;
        this.asset = asset;
        this.tags = tags;
        this.summary = summary == null ? "" : summary;
        this.filterValue = filterValue;
        this.level = level;
        this.school = school == null ? "" : school;
        this.classes = classes;
    }

    static Entry homebrew(String cat, Map<String, Object> a) {
        boolean spell = CustomAssets.SPELLS.equals(cat);
        return new Entry(cat, CustomAssets.str(a, "name"), a, Tags.tagsOfAsset(cat, a), CustomAssets.summary(cat, a),
                CustomAssets.filterValue(cat, a), spell ? CustomAssets.intVal(a, "level", 1) : -1,
                spell ? CustomAssets.str(a, "school") : "", spell ? CustomAssets.list(a, "classes") : Collections.<String>emptyList());
    }

    static Entry builtin(String cat, String name) {
        Spell s = CustomAssets.SPELLS.equals(cat) ? CustomAssets.builtinSpell(name) : null;
        return new Entry(cat, name, null, Tags.tagsOfBuiltin(cat, name), CustomAssets.builtinSummary(cat, name),
                CustomAssets.builtinFilterValue(cat, name), s != null ? s.level : -1, s != null ? s.school : "",
                s != null ? s.classes : Collections.<String>emptyList());
    }

    boolean builtin() {
        return asset == null;
    }

    boolean hidden() {
        return Tags.anyDisabled(tags);
    }

    String haystack() {
        if (haystack == null) {
            String body;
            if (builtin()) {
                body = CustomAssets.builtinDescription(cat, name);
            } else {
                Map<String, Object> searchable = new LinkedHashMap<>(asset);
                searchable.remove("token"); // a token picture's Base64 text would match random searches
                body = JsonUtil.toJson(searchable);
            }
            haystack = (name + "\n" + summary + "\n" + String.join(" ", tags) + "\n" + cat + "\n" + body).toLowerCase();
        }
        return haystack;
    }
}

// Homebrew first (creation order), then the built-in assets. ALL_CAT = every category.
private static List<Entry> entriesFor(String cat) {
    List<Entry> out = new ArrayList<>();
    if (ALL_CAT.equals(cat)) {
        for (String c : CustomAssets.CATEGORIES) out.addAll(entriesFor(c));
        return out;
    }
    for (Map<String, Object> a : CustomAssets.all(cat)) out.add(Entry.homebrew(cat, a));
    for (String n : CustomAssets.builtinNames(cat)) out.add(Entry.builtin(cat, n));
    return out;
}

private boolean searching() {
    return !searchField.getText().trim().isEmpty();
}

// ===============================================================
// List view
// ===============================================================
private JComponent buildListView() {
    JPanel view = new JPanel(new BorderLayout());
    view.setBackground(App.SHEET_BG);
    view.add(buildTopBar(), BorderLayout.NORTH);

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

    // ---- per-category toolbar ----
    JPanel center = new JPanel(new BorderLayout());
    center.setBackground(App.SHEET_BG);
    center.setBorder(new EmptyBorder(16, 22, 10, 22));

    JPanel toolbar = new JPanel();
    toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.Y_AXIS));
    toolbar.setOpaque(false);

    listTitle.setForeground(App.GOLD);
    listTitle.setFont(new Font("Serif", Font.BOLD, 24));
    listCount.setForeground(App.TEXT_MUTED);
    listCount.setFont(new Font("SansSerif", Font.PLAIN, 13));
    JPanel titleLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
    titleLeft.setOpaque(false);
    titleLeft.add(listTitle);
    titleLeft.add(listCount);
    JPanel titleRow = new JPanel(new BorderLayout());
    titleRow.setOpaque(false);
    titleRow.add(titleLeft, BorderLayout.WEST);

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
    createButton.addActionListener(e -> openEditor(category, null));
    packButtons.add(importBtn);
    packButtons.add(exportBtn);
    packButtons.add(createButton);
    titleRow.add(packButtons, BorderLayout.EAST);
    titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
    titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
    toolbar.add(titleRow);
    toolbar.add(Box.createRigidArea(new Dimension(0, 10)));

    filterRow.setOpaque(false);
    App.styleCombo(filterCombo);
    filterCombo.setPreferredSize(new Dimension(170, 34));
    filterCombo.setToolTipText("Filter this category");
    filterCombo.addActionListener(e -> {
        if (!updatingFilter) { shownLimit = PAGE; rebuildList(); }
    });
    App.styleCombo(tagCombo);
    tagCombo.setPreferredSize(new Dimension(210, 34));
    tagCombo.setMaximumRowCount(16);
    tagCombo.setToolTipText("Show only assets with this tag");
    tagCombo.addActionListener(e -> {
        if (!updatingFilter) { shownLimit = PAGE; rebuildList(); }
    });
    statusLabel.setForeground(App.TEXT_MUTED);
    statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
    tabSearchTimer.setRepeats(false);
    tabSearchLabel.setForeground(App.TEXT_MUTED);
    tabSearchLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
    App.styleTextField(tabSearch);
    tabSearch.setFont(new Font("SansSerif", Font.PLAIN, 14));
    tabSearch.setColumns(18);
    tabSearch.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
        public void insertUpdate(javax.swing.event.DocumentEvent e) { tabSearchTimer.restart(); }
        public void removeUpdate(javax.swing.event.DocumentEvent e) { tabSearchTimer.restart(); }
        public void changedUpdate(javax.swing.event.DocumentEvent e) { tabSearchTimer.restart(); }
    });
    tabSearch.addKeyListener(new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_ESCAPE) tabSearch.setText("");
        }
    });
    filterRow.add(tabSearchLabel);
    filterRow.add(tabSearch);
    tabSearchClear = iconButton("✕", "Clear this search", () -> tabSearch.setText(""));
    filterRow.add(tabSearchClear);
    filterRow.add(filterCombo);
    filterRow.add(tagCombo);
    filterRow.add(Box.createRigidArea(new Dimension(10, 0)));
    filterRow.add(statusLabel);
    filterRow.setAlignmentX(Component.LEFT_ALIGNMENT);
    filterRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
    toolbar.add(filterRow);

    spellChips.setLayout(new BoxLayout(spellChips, BoxLayout.Y_AXIS));
    spellChips.setOpaque(false);
    spellChips.setAlignmentX(Component.LEFT_ALIGNMENT);
    toolbar.add(spellChips);
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
    scroll.getVerticalScrollBar().setUnitIncrement(18);
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    center.add(scroll, BorderLayout.CENTER);
    center.addComponentListener(new ComponentAdapter() { // re-wrap the spell chips when the window resizes
        @Override
        public void componentResized(ComponentEvent e) {
            spellChips.revalidate();
        }
    });

    view.add(center, BorderLayout.CENTER);
    return view;
}

// Global search + sort + Tags & Filters, above the tabs
private JComponent buildTopBar() {
    JPanel bar = new JPanel(new BorderLayout(12, 0));
    bar.setBackground(App.SHEET_CARD);
    bar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, App.SHEET_BORDER), new EmptyBorder(10, 16, 10, 16)));

    JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    left.setOpaque(false);
    JLabel searchLbl = new JLabel("Search all assets:");
    searchLbl.setForeground(App.GOLD);
    searchLbl.setFont(new Font("SansSerif", Font.BOLD, 13));
    App.styleTextField(searchField);
    searchField.setFont(new Font("SansSerif", Font.PLAIN, 14));
    searchField.setColumns(28);
    searchField.setToolTipText("Searches every category at once: names, rules text, fields and tags");
    searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
        public void insertUpdate(javax.swing.event.DocumentEvent e) { searchTimer.restart(); }
        public void removeUpdate(javax.swing.event.DocumentEvent e) { searchTimer.restart(); }
        public void changedUpdate(javax.swing.event.DocumentEvent e) { searchTimer.restart(); }
    });
    searchField.addKeyListener(new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_ESCAPE) searchField.setText("");
        }
    });
    JButton clear = iconButton("✕", "Clear search", () -> searchField.setText(""));
    JLabel sortLbl = new JLabel("Sort:");
    sortLbl.setForeground(App.TEXT_MUTED);
    sortLbl.setFont(new Font("SansSerif", Font.PLAIN, 13));
    App.styleCombo(sortCombo);
    sortCombo.setPreferredSize(new Dimension(180, 34));
    sortCombo.setToolTipText("Order the list by name, by tag / source, or by level / category");
    sortCombo.addActionListener(e -> { shownLimit = PAGE; rebuildList(); });
    left.add(searchLbl);
    left.add(searchField);
    left.add(clear);
    left.add(Box.createRigidArea(new Dimension(8, 0)));
    left.add(sortLbl);
    left.add(sortCombo);
    bar.add(left, BorderLayout.WEST);

    JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    right.setOpaque(false);
    filterNote.setFont(new Font("SansSerif", Font.PLAIN, 12));
    App.clickable(filterNote, () -> showTagFilters(filterNote, this::refresh));
    JButton tagsBtn = App.overlayButton("Tags & Filters", false);
    tagsBtn.setToolTipText("Create or delete tags, and choose which tags appear in character creation and level up");
    tagsBtn.addActionListener(e -> showTagFilters(tagsBtn, this::refresh));
    right.add(filterNote);
    right.add(tagsBtn);
    bar.add(right, BorderLayout.EAST);
    return bar;
}

void refresh() {
    rebuildTabs();
    rebuildFilter();
    rebuildList();
    updateFilterNote();
}

// Opens the library on a category (e.g. from elsewhere in the app)
void showCategory(String cat) {
    category = cat;
    searchField.setText("");
    outerLayout.show(outerCards, "ASSETS");
    refresh();
}

private void updateFilterNote() {
    List<String> off = Tags.disabledTags();
    if (off.isEmpty()) {
        filterNote.setText("All content shown");
        filterNote.setForeground(App.TEXT_MUTED);
        filterNote.setToolTipText("No tags are hidden from character creation and level up");
    } else {
        List<String> shown = off.subList(0, Math.min(3, off.size()));
        filterNote.setText("Hidden: " + String.join(", ", shown) + (off.size() > 3 ? " +" + (off.size() - 3) + " more" : ""));
        filterNote.setForeground(App.ACCENT_HOVER);
        filterNote.setToolTipText("Assets with these tags are hidden from character creation, level up and equipment pickers");
    }
}

private void rebuildTabs() {
    tabColumn.removeAll();
    JLabel head = new JLabel("CATEGORIES");
    head.setForeground(App.TEXT_MUTED);
    head.setFont(new Font("SansSerif", Font.BOLD, 11));
    head.setBorder(new EmptyBorder(0, 8, 8, 0));
    head.setAlignmentX(Component.LEFT_ALIGNMENT);
    tabColumn.add(head);
    boolean searching = searching();
    List<String> tabs = new ArrayList<>(Collections.singletonList(ALL_CAT));
    tabs.addAll(Arrays.asList(CustomAssets.CATEGORIES));
    for (String cat : tabs) {
        boolean sel = cat.equals(category) && !searching;
        JPanel tab = new JPanel(new BorderLayout());
        tab.setBackground(sel ? App.SHEET_ROW_ALT : App.SHEET_CARD);
        tab.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, sel ? App.GOLD : App.SHEET_CARD), new EmptyBorder(8, 10, 8, 10)));
        tab.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        tab.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel name = new JLabel(cat);
        name.setForeground(sel ? App.GOLD : App.TEXT_LIGHT);
        name.setFont(new Font("SansSerif", Font.BOLD, 14));
        int official = 0, homebrew = 0;
        for (String c : ALL_CAT.equals(cat) ? CustomAssets.CATEGORIES : new String[]{cat}) {
            official += CustomAssets.builtinNames(c).size();
            homebrew += CustomAssets.count(c);
        }
        JLabel count = new JLabel(String.valueOf(official + homebrew));
        count.setForeground(App.TEXT_MUTED);
        count.setFont(new Font("SansSerif", Font.PLAIN, 12));
        tab.add(name, BorderLayout.CENTER);
        tab.add(count, BorderLayout.EAST);
        tab.setToolTipText(official + " official · " + homebrew + " homebrew");
        App.clickable(tab, () -> {
            category = cat;
            shownLimit = PAGE;
            searchField.setText("");
            searchTimer.stop();
            tabSearch.setText("");
            tabSearchTimer.stop();
            refresh();
        });
        tabColumn.add(tab);
        tabColumn.add(Box.createRigidArea(new Dimension(0, ALL_CAT.equals(cat) ? 10 : 2)));
    }
    tabColumn.revalidate();
    tabColumn.repaint();
    boolean all = ALL_CAT.equals(category);
    createButton.setVisible(!all); // pick a category tab to create homebrew
    if (!all) createButton.setText("+ New " + CustomAssets.SINGULAR.get(category));
}

private void rebuildFilter() {
    updatingFilter = true;
    boolean searching = searching();
    boolean spells = CustomAssets.SPELLS.equals(category);
    List<Entry> entries = searching ? Collections.<Entry>emptyList() : entriesFor(category);

    Object prev = filterCombo.getSelectedItem();
    filterCombo.removeAllItems();
    boolean all = ALL_CAT.equals(category); // the All tab filters by category instead
    String key = CustomAssets.FILTER_KEYS.get(category);
    boolean showFilter = (key != null || all) && !spells && !searching;
    filterCombo.setVisible(showFilter);
    filterCombo.setToolTipText(all ? "Show one category" : "Filter this category");
    if (showFilter && all) {
        filterCombo.addItem(ALL);
        for (String c : CustomAssets.CATEGORIES) filterCombo.addItem(c);
        if (prev != null && Arrays.asList(CustomAssets.CATEGORIES).contains(String.valueOf(prev))) filterCombo.setSelectedItem(prev);
    } else if (showFilter) {
        filterCombo.addItem(ALL);
        TreeSet<String> values = new TreeSet<>(this::filterOrder);
        for (Entry e : entries) if (e.filterValue != null) values.add(e.filterValue);
        for (String v : values) filterCombo.addItem(v);
        if (prev != null && values.contains(String.valueOf(prev))) filterCombo.setSelectedItem(prev);
    }

    Object prevTag = tagCombo.getSelectedItem();
    tagCombo.removeAllItems();
    tagCombo.setVisible(!searching && !entries.isEmpty());
    if (!searching) {
        tagCombo.addItem(ALL_TAGS);
        List<String> present = new ArrayList<>();
        for (Entry e : entries) for (String t : e.tags) if (!Tags.isDerived(t) && !Tags.containsCI(present, t)) present.add(t);
        List<String> ordered = new ArrayList<>();
        for (String t : Tags.allTags()) if (Tags.containsCI(present, t)) ordered.add(t);
        for (String t : ordered) tagCombo.addItem(t);
        if (prevTag != null && ordered.contains(String.valueOf(prevTag))) tagCombo.setSelectedItem(prevTag);
    }
    filterRow.setVisible(!searching || !statusLabel.getText().trim().isEmpty());
    // Category tabs get their own search box; the All tab uses the top bar's search
    boolean tabSearchable = !searching && !ALL_CAT.equals(category);
    tabSearchLabel.setVisible(tabSearchable);
    tabSearch.setVisible(tabSearchable);
    tabSearchClear.setVisible(tabSearchable);
    if (tabSearchable) {
        tabSearchLabel.setText("Search " + category + ":");
        tabSearch.setToolTipText("Search only " + category.toLowerCase() + ": names, rules text, fields and tags");
    }
    updatingFilter = false;
    rebuildSpellChips();
}

private int filterOrder(String a, String b) {
    // "Cantrip" < "Level 1" < ... < "Level 9"; everything else alphabetical
    int ia = levelRank(a), ib = levelRank(b);
    if (ia >= 0 && ib >= 0) return Integer.compare(ia, ib);
    return a.compareToIgnoreCase(b);
}

private static int levelRank(String v) {
    if (v == null) return -1;
    if (v.equals("Cantrip")) return 0;
    if (v.startsWith("Level ")) return CustomAssets.intVal(Collections.singletonMap("v", v.substring(6)), "v", 99);
    return -1;
}

// School / Level / Class chips on the Spells tab
private void rebuildSpellChips() {
    spellChips.removeAll();
    boolean show = CustomAssets.SPELLS.equals(category) && !searching();
    spellChips.setVisible(show);
    if (show) {
        List<String> schools = new ArrayList<>(Collections.singletonList(ALL));
        schools.addAll(Arrays.asList(CustomAssets.SCHOOLS));
        List<String> levels = new ArrayList<>(Collections.singletonList(ALL));
        levels.addAll(Arrays.asList(Tags.LEVEL_TAGS));
        List<String> classes = new ArrayList<>(Collections.singletonList(ALL));
        classes.addAll(Tags.spellClasses());
        spellChips.add(Box.createRigidArea(new Dimension(0, 10)));
        spellChips.add(chipRow("School", schools, spellSchool, v -> spellSchool = v));
        spellChips.add(chipRow("Level", levels, spellLevel, v -> spellLevel = v));
        spellChips.add(chipRow("Class", classes, spellClass, v -> spellClass = v));
    }
    spellChips.revalidate();
    spellChips.repaint();
}

private JComponent chipRow(String label, List<String> options, String selected, java.util.function.Consumer<String> set) {
    JPanel row = new JPanel(new BorderLayout(8, 0));
    row.setOpaque(false);
    row.setAlignmentX(Component.LEFT_ALIGNMENT);
    JLabel l = new JLabel(label.toUpperCase());
    l.setForeground(App.TEXT_MUTED);
    l.setFont(new Font("SansSerif", Font.BOLD, 11));
    l.setPreferredSize(new Dimension(54, 26));
    l.setVerticalAlignment(SwingConstants.TOP);
    l.setBorder(new EmptyBorder(6, 0, 0, 0));
    row.add(l, BorderLayout.WEST);
    JPanel chips = new JPanel(new WrapLayout(FlowLayout.LEFT, 5, 3));
    chips.setOpaque(false);
    for (String o : options) {
        String text = o.endsWith("-Level") ? o.substring(0, o.length() - 6) : o;
        chips.add(filterChip(text, o.equals(selected), () -> {
            set.accept(o);
            shownLimit = PAGE;
            rebuildSpellChips();
            rebuildList();
        }));
    }
    row.add(chips, BorderLayout.CENTER);
    return row;
}

static JLabel filterChip(String text, boolean selected, Runnable onClick) {
    JLabel chip = new JLabel(text);
    chip.setFont(new Font("SansSerif", Font.BOLD, 12));
    chip.setOpaque(true);
    chip.setForeground(selected ? App.GOLD : App.TEXT_LIGHT);
    chip.setBackground(selected ? new Color(70, 60, 38) : App.SHEET_ROW_ALT);
    chip.setBorder(BorderFactory.createCompoundBorder(new LineBorder(selected ? App.GOLD : App.SHEET_BORDER, 1, true),
            new EmptyBorder(3, 9, 3, 9)));
    App.clickable(chip, onClick);
    return chip;
}

private void rebuildList() {
    listWrap.removeAll();
    String q = searchField.getText().trim().toLowerCase();
    boolean global = !q.isEmpty();
    String[] terms = q.split("\\s+");
    String filter = filterCombo.isVisible() && filterCombo.getSelectedItem() != null ? String.valueOf(filterCombo.getSelectedItem()) : ALL;
    String tag = tagCombo.isVisible() && tagCombo.getSelectedItem() != null ? String.valueOf(tagCombo.getSelectedItem()) : ALL_TAGS;
    boolean spells = CustomAssets.SPELLS.equals(category);
    boolean allTab = ALL_CAT.equals(category);
    boolean multi = global || allTab; // rows from several categories: show category badges / group by category

    List<Entry> pool = new ArrayList<>(entriesFor(global ? ALL_CAT : category));
    String tq = tabSearch.isVisible() ? tabSearch.getText().trim().toLowerCase() : "";
    String[] tabTerms = tq.isEmpty() ? new String[0] : tq.split("\\s+");

    List<Entry> shown = new ArrayList<>();
    for (Entry e : pool) {
        if (global) {
            boolean ok = true;
            for (String t : terms) if (!e.haystack().contains(t)) { ok = false; break; }
            if (!ok) continue;
        } else {
            boolean ok = true;
            for (String t : tabTerms) if (!e.haystack().contains(t)) { ok = false; break; }
            if (!ok) continue;
            if (!ALL.equals(filter) && !filter.equals(allTab ? e.cat : e.filterValue)) continue;
            if (!ALL_TAGS.equals(tag) && !Tags.containsCI(e.tags, tag)) continue;
            if (spells) {
                if (!ALL.equals(spellSchool) && !spellSchool.equalsIgnoreCase(e.school)) continue;
                if (!ALL.equals(spellLevel) && Tags.levelOfTag(spellLevel) != e.level) continue;
                if (!ALL.equals(spellClass) && !Tags.containsCI(e.classes, spellClass)) continue;
            }
        }
        shown.add(e);
    }
    String sort = String.valueOf(sortCombo.getSelectedItem());
    shown.sort(comparator(sort, multi));

    listTitle.setText(global ? "Search results" : allTab ? "All Assets" : category);
    listCount.setText(global ? shown.size() + " match" + (shown.size() == 1 ? "" : "es") + " in all categories"
            : shown.size() == pool.size() ? pool.size() + " total" : shown.size() + " of " + pool.size());

    String lastGroup = null;
    int n = 0;
    for (Entry e : shown) {
        if (n >= shownLimit) break;
        String g = groupLabel(sort, e, multi);
        if (g != null && !g.equals(lastGroup)) {
            listWrap.add(groupHeader(g));
            lastGroup = g;
        }
        listWrap.add(assetRow(e, multi));
        listWrap.add(Box.createRigidArea(new Dimension(0, 8)));
        n++;
    }
    if (shown.size() > shownLimit) {
        JButton more = App.overlayButton("Show more (" + (shown.size() - shownLimit) + " more)", false);
        more.setAlignmentX(Component.LEFT_ALIGNMENT);
        more.addActionListener(e -> { shownLimit += PAGE; rebuildList(); });
        listWrap.add(more);
    }
    if (pool.isEmpty() && !global) {
        listWrap.add(emptyState());
    } else if (shown.isEmpty()) {
        JLabel none = new JLabel(global ? "Nothing in any category matches \"" + searchField.getText().trim() + "\"."
                : "Nothing matches these filters.");
        none.setForeground(App.TEXT_MUTED);
        none.setFont(new Font("SansSerif", Font.PLAIN, 15));
        none.setAlignmentX(Component.LEFT_ALIGNMENT);
        listWrap.add(none);
    }
    listWrap.revalidate();
    listWrap.repaint();
}

// ---- sorting ----
private static final List<String> SOURCE_ORDER = Arrays.asList(Tags.CORE, Tags.XGTE, Tags.TCOE, Tags.MOTM, Tags.EBERRON, Tags.OFFICIAL, Tags.HOMEBREW);

private static int sourceRank(String s) {
    for (int i = 0; i < SOURCE_ORDER.size(); i++) if (SOURCE_ORDER.get(i).equalsIgnoreCase(s)) return i;
    return 100;
}

private static int categoryRank(String cat) {
    return Arrays.asList(CustomAssets.CATEGORIES).indexOf(cat);
}

// Level for spells, otherwise the category's filter bucket (weapon category, armor type, feat type, ...)
private static String levelLabel(Entry e) {
    if (e.level >= 0) return e.level == 0 ? "Cantrips" : "Level " + e.level;
    return e.filterValue;
}

private Comparator<Entry> comparator(String sort, boolean global) {
    Comparator<Entry> byName = (a, b) -> {
        int c = a.name.compareToIgnoreCase(b.name);
        return c != 0 ? c : Integer.compare(categoryRank(a.cat), categoryRank(b.cat));
    };
    if (SORT_SOURCE.equals(sort)) {
        return Comparator.<Entry>comparingInt(e -> sourceRank(Tags.primarySource(e.tags)))
                .thenComparing(e -> Tags.primarySource(e.tags), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(byName);
    }
    if (SORT_LEVEL.equals(sort)) {
        Comparator<Entry> byCat = Comparator.comparingInt(e -> global ? categoryRank(e.cat) : 0);
        return byCat.thenComparing((a, b) -> {
            if (a.level >= 0 || b.level >= 0) return Integer.compare(a.level, b.level);
            String fa = a.filterValue == null ? "" : a.filterValue, fb = b.filterValue == null ? "" : b.filterValue;
            return filterOrder(fa, fb);
        }).thenComparing(byName);
    }
    return byName;
}

private static String groupLabel(String sort, Entry e, boolean global) {
    if (SORT_SOURCE.equals(sort)) return Tags.primarySource(e.tags);
    if (SORT_LEVEL.equals(sort)) {
        String lv = levelLabel(e);
        if (global) return e.cat + (lv == null || lv.isEmpty() ? "" : " · " + lv);
        return lv;
    }
    return null;
}

private static JComponent groupHeader(String text) {
    JLabel h = new JLabel(text.toUpperCase());
    h.setForeground(App.GOLD);
    h.setFont(new Font("SansSerif", Font.BOLD, 12));
    h.setBorder(BorderFactory.createCompoundBorder(new EmptyBorder(8, 0, 6, 0),
            BorderFactory.createMatteBorder(0, 0, 1, 0, App.SHEET_BORDER)));
    h.setAlignmentX(Component.LEFT_ALIGNMENT);
    h.setMaximumSize(new Dimension(900, 34));
    return h;
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
    b.addActionListener(e -> openEditor(category, null));
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
        case CustomAssets.FEATS: return "General feats and Epic Boons are offered at level up; Origin feats can be given to backgrounds.";
        case CustomAssets.BACKGROUNDS: return "Backgrounds appear in the character creation wizard with their skills, tools, origin feat and equipment.";
        case CustomAssets.SPECIES: return "Species appear in the character creation wizard and set size, speed, darkvision, languages and traits.";
        case CustomAssets.CLASSES: return "Classes appear in character creation and level up, with hit die, saves, skills, spellcasting and level 1-20 features.";
        case CustomAssets.SUBCLASSES: return "Subclasses are offered when a character reaches the right level in the parent class during level up.";
        default: return "";
    }
}

private JComponent assetRow(Entry e, boolean showCategory) {
    String cat = e.cat;
    Map<String, Object> a = e.asset;
    String id = a == null ? "" : CustomAssets.str(a, "id");
    JPanel row = new JPanel(new BorderLayout(12, 0));
    row.setBackground(App.SHEET_CARD);
    row.setBorder(BorderFactory.createCompoundBorder(new LineBorder(App.SHEET_BORDER, 1, true), new EmptyBorder(8, 14, 8, 10)));
    row.setAlignmentX(Component.LEFT_ALIGNMENT);
    row.setMaximumSize(new Dimension(900, 74));

    JPanel text = new JPanel();
    text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
    text.setOpaque(false);
    JPanel nameLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    nameLine.setOpaque(false);
    nameLine.setAlignmentX(Component.LEFT_ALIGNMENT);
    JLabel name = new JLabel(e.name);
    name.setForeground(App.GOLD);
    name.setFont(new Font("Serif", Font.BOLD, 17));
    nameLine.add(name);
    int badges = 0;
    for (String t : e.tags) {
        if (Tags.isDerived(t)) continue;
        if (badges++ == 3) break;
        nameLine.add(tagBadge(t));
    }
    if (e.hidden()) {
        JLabel hidden = badge("HIDDEN", App.ACCENT_HOVER);
        hidden.setToolTipText("Hidden from character creation, level up and equipment pickers by your content filters");
        App.clickable(hidden, () -> showTagFilters(hidden, this::refresh));
        nameLine.add(hidden);
    }
    if (showCategory) { // category badge (All tab / search results); click to open that tab
        JLabel catBadge = badge(CustomAssets.SINGULAR.get(cat).toUpperCase(), App.TEXT_LIGHT);
        catBadge.setOpaque(true);
        catBadge.setBackground(App.SHEET_ROW_ALT);
        catBadge.setToolTipText("Open the " + cat + " tab");
        App.clickable(catBadge, () -> showCategory(cat));
        nameLine.add(catBadge, 0);
    }
    String sum = e.summary;
    JLabel sub = new JLabel(sum.trim().isEmpty() ? " " : sum);
    sub.setForeground(App.TEXT_MUTED);
    sub.setFont(new Font("SansSerif", Font.PLAIN, 12));
    sub.setAlignmentX(Component.LEFT_ALIGNMENT);
    text.add(nameLine);
    text.add(Box.createRigidArea(new Dimension(0, 3)));
    text.add(sub);
    row.add(text, BorderLayout.CENTER);

    JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
    actions.setOpaque(false);
    if (a != null && CustomAssets.MAPS.equals(cat)) {
        JButton open = App.overlayButton("Open Map", true);
        open.setToolTipText("Open in the interactive map viewer");
        open.addActionListener(ev -> openMap(id, false));
        actions.add(open);
        actions.add(iconButton("ⓘ", "Map details", () -> showDetail(row, cat, a, actionsFor(cat, a))));
    }
    actions.add(iconButton("Tags", "Edit tags", () -> showTagEditor(row, cat, e.name, a, this::refresh)));
    if (a != null) {
        actions.add(iconButton("✎", "Edit", () -> openEditor(cat, a)));
        actions.add(iconButton("⧉", "Duplicate", () -> duplicate(cat, id)));
        actions.add(iconButton("🗑", "Delete", () -> confirmDelete(row, cat, a)));
    }
    row.add(actions, BorderLayout.EAST);

    // Maps launch straight into the viewer; everything else opens its detail overlay
    App.clickableCard(row, () -> {
        if (a == null) showBuiltinDetail(row, cat, e.name, this::refresh);
        else if (CustomAssets.MAPS.equals(cat)) openMap(id, false);
        else showDetail(row, cat, a, actionsFor(cat, a));
    });
    return row;
}

private static JLabel badge(String text, Color color) {
    JLabel b = new JLabel(text);
    b.setForeground(color);
    b.setFont(new Font("SansSerif", Font.BOLD, 10));
    b.setBorder(BorderFactory.createCompoundBorder(new LineBorder(color, 1, true), new EmptyBorder(1, 5, 1, 5)));
    return b;
}

// A small clickable tag label (opens what the tag is and where it comes from)
static JLabel tagBadge(String tag) {
    Color c = Tags.HOMEBREW.equalsIgnoreCase(tag) ? new Color(110, 190, 120)
            : Tags.isSource(tag) ? App.GOLD_SOFT.brighter() : new Color(140, 170, 220);
    JLabel b = badge(tag, Tags.isEnabled(tag) ? c : App.TEXT_MUTED);
    b.setToolTipText(Tags.kind(tag) + (Tags.isEnabled(tag) ? "" : " (hidden by content filters)"));
    App.clickable(b, () -> showTagInfo(b, tag));
    return b;
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
    act.edit = () -> openEditor(cat, CustomAssets.find(cat, id));
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
// Built-in asset detail (read-only apart from tags)
// ===============================================================
static void showBuiltinDetail(Component anchor, String cat, String name, Runnable onTagsChanged) {
    JPanel body = new JPanel();
    body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
    body.setOpaque(false);

    List<String> tags = Tags.tagsOfBuiltin(cat, name);
    String source = Tags.primarySource(tags);
    JLabel kind = new JLabel(("Official " + CustomAssets.SINGULAR.get(cat) + (Tags.isSource(source) && !Tags.OFFICIAL.equals(source) ? " · " + source : "")).toUpperCase());
    kind.setForeground(App.TEXT_MUTED);
    kind.setFont(new Font("SansSerif", Font.BOLD, 11));
    add(body, kind);
    String sum = CustomAssets.builtinSummary(cat, name);
    if (!sum.isEmpty()) {
        JLabel s = new JLabel("<html><div style='width:420px'>" + App.escapeHtml(sum) + "</div></html>");
        s.setForeground(App.TEXT_LIGHT);
        s.setFont(new Font("SansSerif", Font.ITALIC, 13));
        s.setBorder(new EmptyBorder(4, 0, 6, 0));
        add(body, s);
    }
    if (CustomAssets.findByName(cat, name) != null) {
        JLabel note = new JLabel("<html><div style='width:420px'>Your homebrew " + App.escapeHtml(CustomAssets.noun(cat, false))
                + " of the same name replaces this one while it exists.</div></html>");
        note.setForeground(App.ACCENT_HOVER);
        note.setFont(new Font("SansSerif", Font.PLAIN, 12));
        note.setBorder(new EmptyBorder(0, 0, 6, 0));
        add(body, note);
    }
    String desc = CustomAssets.builtinDescription(cat, name);
    if (!desc.trim().isEmpty()) {
        JPanel sec = section("Rules");
        JEditorPane txt = App.conditionLinkedText(desc, 420, 13, App.TEXT_LIGHT, null);
        txt.setAlignmentX(Component.LEFT_ALIGNMENT);
        sec.add(txt);
        add(body, sec);
    }
    add(body, tagSection(tags));

    JScrollPane scroll = detailScroll(anchor, body);
    JPanel wrap = new JPanel(new BorderLayout(0, 12));
    wrap.setOpaque(false);
    wrap.add(scroll, BorderLayout.CENTER);
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    buttons.setOpaque(false);
    Runnable[] close = new Runnable[1];
    if (CustomAssets.SPELLS.equals(cat)) {
        Spell sp = CustomAssets.builtinSpell(name);
        if (sp != null) {
            JButton card = App.overlayButton("View Spell Card", false);
            card.addActionListener(e -> App.showSpellOverlay(card, sp, name, "Official spell", false));
            buttons.add(card);
        }
    }
    JButton editTags = App.overlayButton("Edit Tags", true);
    editTags.addActionListener(e -> {
        close[0].run();
        showTagEditor(anchor, cat, name, null, onTagsChanged);
    });
    buttons.add(editTags);
    wrap.add(buttons, BorderLayout.SOUTH);
    close[0] = App.showSheetOverlay(anchor, name, wrap, null);
}

private static JScrollPane detailScroll(Component anchor, JPanel body) {
    JScrollPane scroll = new JScrollPane(body);
    App.applyDarkScrollBar(scroll);
    scroll.getViewport().setBackground(App.SHEET_CARD);
    scroll.setBorder(BorderFactory.createEmptyBorder());
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    JRootPane root = SwingUtilities.getRootPane(anchor);
    int maxH = root != null ? Math.max(260, (int) (root.getHeight() * 0.72)) : 520;
    Dimension pref = body.getPreferredSize();
    scroll.setPreferredSize(new Dimension(Math.max(440, pref.width + 18), Math.min(maxH, pref.height + 6)));
    return scroll;
}

// "TAGS" section of chips; each opens the tag's info
static JComponent tagSection(List<String> tags) {
    JPanel sec = section("Tags");
    JPanel chips = new JPanel(new WrapLayout(FlowLayout.LEFT, 5, 4));
    chips.setOpaque(false);
    chips.setAlignmentX(Component.LEFT_ALIGNMENT);
    for (String t : tags) chips.add(tagBadge(t));
    if (tags.isEmpty()) {
        JLabel none = new JLabel("No tags.");
        none.setForeground(App.TEXT_MUTED);
        chips.add(none);
    }
    chips.setPreferredSize(null);
    chips.setMaximumSize(new Dimension(440, Integer.MAX_VALUE));
    sec.add(chips);
    return sec;
}

// ===============================================================
// Tags: info, per-asset editor, and the Tags & Filters overlay
// ===============================================================
static void showTagInfo(Component anchor, String tag) {
    int n = Tags.counts().getOrDefault(tag, 0);
    App.showInfoOverlay(anchor, tag, Tags.kind(tag), Tags.describe(tag), Arrays.asList(
            App.row("Status", Tags.isEnabled(tag) ? "Shown in character creation & level up" : "Hidden by content filters"),
            App.row("Used by", n + (n == 1 ? " asset" : " assets"))));
}

// Add / remove tags on one asset. asset == null means the built-in asset `name`.
static void showTagEditor(Component anchor, String cat, String name, Map<String, Object> asset, Runnable onSaved) {
    boolean builtin = asset == null;
    List<String> current = builtin ? Tags.builtinTags(cat, name) : Tags.assetTags(asset);
    for (int i = 0; i < current.size(); i++) {
        String c = Tags.canonical(current.get(i));
        if (c != null) current.set(i, c);
    }
    List<String> derived = Tags.derivedTags(cat, name, asset);

    JPanel body = new JPanel();
    body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
    body.setOpaque(false);
    JPanel curPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 6));
    curPanel.setOpaque(false);
    JPanel addPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 6));
    addPanel.setOpaque(false);
    JLabel error = new JLabel(" ");
    error.setForeground(App.ACCENT_HOVER);
    error.setFont(new Font("SansSerif", Font.BOLD, 12));

    Runnable[] rebuild = new Runnable[1];
    rebuild[0] = () -> {
        curPanel.removeAll();
        for (String t : new ArrayList<>(current)) {
            JLabel chip = filterChip(t + "  ✕", true, () -> {
                current.remove(t);
                rebuild[0].run();
            });
            chip.setToolTipText("Remove " + t);
            curPanel.add(chip);
        }
        if (current.isEmpty()) curPanel.add(overlayHint("No tags yet. Pick some below."));
        addPanel.removeAll();
        for (String t : Tags.allTags()) {
            if (Tags.containsCI(current, t)) continue;
            JLabel chip = filterChip("+ " + t, false, () -> {
                current.add(t);
                rebuild[0].run();
            });
            chip.setToolTipText("Add " + t + " (" + Tags.kind(t).toLowerCase() + ")");
            addPanel.add(chip);
        }
        if (addPanel.getComponentCount() == 0) addPanel.add(overlayHint("Every tag is already on this asset."));
        error.setText(" ");
        curPanel.revalidate();
        addPanel.revalidate();
        body.revalidate();
        body.repaint();
    };

    add(body, overlayLabel("Tags on this " + CustomAssets.noun(cat, false) + " (click to remove)"));
    add(body, curPanel);
    if (!derived.isEmpty()) {
        body.add(Box.createRigidArea(new Dimension(0, 8)));
        add(body, overlayLabel("Automatic tags (from the spell's school, level and classes)"));
        JPanel d = new JPanel(new WrapLayout(FlowLayout.LEFT, 5, 4));
        d.setOpaque(false);
        for (String t : derived) d.add(tagBadge(t));
        add(body, d);
    }
    body.add(Box.createRigidArea(new Dimension(0, 10)));
    add(body, overlayLabel("Add an existing tag"));
    add(body, addPanel);
    body.add(Box.createRigidArea(new Dimension(0, 10)));
    add(body, overlayLabel("Or create a new tag"));
    JPanel newRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    newRow.setOpaque(false);
    JTextField field = App.overlayField("", 18);
    JButton addBtn = App.overlayButton("Add", false);
    Runnable addNew = () -> {
        String t = Tags.cleanName(field.getText());
        if (t == null) error.setText("Tag names need 1-40 characters (no \"|\").");
        else if (Tags.isDerived(t)) error.setText("School, level and class tags are added automatically.");
        else if (Tags.containsCI(current, t)) error.setText("\"" + t + "\" is already on this asset.");
        else {
            current.add(t);
            field.setText("");
            rebuild[0].run();
        }
    };
    addBtn.addActionListener(e -> addNew.run());
    field.addActionListener(e -> addNew.run());
    newRow.add(field);
    newRow.add(addBtn);
    add(body, newRow);
    body.add(Box.createRigidArea(new Dimension(0, 6)));
    add(body, error);
    rebuild[0].run();

    JScrollPane scroll = new JScrollPane(topAnchored(body));
    App.applyDarkScrollBar(scroll);
    scroll.setBorder(BorderFactory.createEmptyBorder());
    scroll.getViewport().setBackground(App.SHEET_CARD);
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.getVerticalScrollBar().setUnitIncrement(16);
    JRootPane root = SwingUtilities.getRootPane(anchor);
    scroll.setPreferredSize(new Dimension(500, root != null ? Math.min(460, Math.max(260, (int) (root.getHeight() * 0.6))) : 420));

    JPanel wrap = new JPanel(new BorderLayout(0, 12));
    wrap.setOpaque(false);
    wrap.add(scroll, BorderLayout.CENTER);
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    buttons.setOpaque(false);
    Runnable[] close = new Runnable[1];
    if (builtin && Tags.hasOverride(cat, name)) {
        JButton reset = App.overlayButton("Reset to Default", false);
        reset.setToolTipText("Back to " + String.join(" + ", Tags.defaultBuiltinTags(cat, name)));
        reset.addActionListener(e -> {
            Tags.resetBuiltinTags(cat, name);
            close[0].run();
            if (onSaved != null) onSaved.run();
        });
        buttons.add(reset);
    }
    JButton cancel = App.overlayButton("Cancel", false);
    cancel.addActionListener(e -> close[0].run());
    JButton save = App.overlayButton("Save Tags", true);
    save.addActionListener(e -> {
        if (builtin) {
            Tags.setBuiltinTags(cat, name, current);
        } else {
            Map<String, Object> copy = CustomAssets.deepCopy(asset);
            copy.put("tags", new ArrayList<Object>(current));
            CustomAssets.put(cat, copy);
        }
        close[0].run();
        if (onSaved != null) onSaved.run();
    });
    buttons.add(cancel);
    buttons.add(save);
    wrap.add(buttons, BorderLayout.SOUTH);
    close[0] = App.showSheetOverlay(anchor, "Tags: " + name, wrap, field);
}

// Campaign content filters + custom tag management. onChange runs after every change.
static void showTagFilters(Component anchor, Runnable onChange) {
    JPanel body = new JPanel();
    body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
    body.setOpaque(false);
    Runnable[] rebuild = new Runnable[1];
    Runnable changed = () -> {
        rebuild[0].run();
        if (onChange != null) onChange.run();
    };
    rebuild[0] = () -> {
        body.removeAll();
        Map<String, Integer> counts = Tags.counts();
        add(body, overlayHint("Turn a tag off to hide everything carrying it from character creation, level up, and the "
                + "equipment and condition pickers. Characters keep anything they already have. Click a tag's ⓘ to see what it is."));

        add(body, overlayLabel("Sources"));
        for (String t : Tags.SOURCE_TAGS) add(body, filterRowFor(t, counts, false, changed));

        body.add(Box.createRigidArea(new Dimension(0, 10)));
        add(body, overlayLabel("Custom tags"));
        List<String> custom = new ArrayList<>();
        for (String t : Tags.allTags()) if (Tags.isCustom(t)) custom.add(t);
        if (custom.isEmpty()) add(body, overlayHint("No custom tags yet. Create one below, then add it to assets with their 🏷 button."));
        for (String t : custom) add(body, filterRowFor(t, counts, true, changed));
        JPanel newRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        newRow.setOpaque(false);
        JTextField field = App.overlayField("", 18);
        JButton create = App.overlayButton("Create Tag", false);
        JLabel err = new JLabel(" ");
        err.setForeground(App.ACCENT_HOVER);
        err.setFont(new Font("SansSerif", Font.BOLD, 12));
        Runnable doCreate = () -> {
            String problem = Tags.createTag(field.getText());
            if (problem != null) err.setText(problem);
            else changed.run();
        };
        create.addActionListener(e -> doCreate.run());
        field.addActionListener(e -> doCreate.run());
        newRow.add(field);
        newRow.add(create);
        body.add(Box.createRigidArea(new Dimension(0, 4)));
        add(body, newRow);
        add(body, err);

        derivedGroup(body, "Spell schools", Arrays.asList(CustomAssets.SCHOOLS), changed);
        derivedGroup(body, "Spell levels", Arrays.asList(Tags.LEVEL_TAGS), changed);
        List<String> classTags = new ArrayList<>();
        for (String c : Tags.spellClasses()) classTags.add(c + Tags.CLASS_SPELL_SUFFIX);
        derivedGroup(body, "Class spell lists", classTags, changed);
        body.revalidate();
        body.repaint();
    };
    rebuild[0].run();

    JScrollPane scroll = new JScrollPane(topAnchored(body));
    App.applyDarkScrollBar(scroll);
    scroll.setBorder(BorderFactory.createEmptyBorder());
    scroll.getViewport().setBackground(App.SHEET_CARD);
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.getVerticalScrollBar().setUnitIncrement(16);
    JRootPane root = SwingUtilities.getRootPane(anchor);
    scroll.setPreferredSize(new Dimension(540, root != null ? Math.min(600, Math.max(280, (int) (root.getHeight() * 0.68))) : 480));

    JPanel wrap = new JPanel(new BorderLayout(0, 12));
    wrap.setOpaque(false);
    wrap.add(scroll, BorderLayout.CENTER);
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    buttons.setOpaque(false);
    Runnable[] close = new Runnable[1];
    JButton showAll = App.overlayButton("Show Everything", false);
    showAll.setToolTipText("Turn every tag back on");
    showAll.addActionListener(e -> {
        for (String t : Tags.disabledTags()) Tags.setEnabled(t, true);
        changed.run();
    });
    JButton done = App.overlayButton("Done", true);
    done.addActionListener(e -> close[0].run());
    buttons.add(showAll);
    buttons.add(done);
    wrap.add(buttons, BorderLayout.SOUTH);
    close[0] = App.showSheetOverlay(anchor, "Tags & Content Filters", wrap, done);
}

// One tag row: on/off toggle, usage count, info, and delete (custom tags; click twice to confirm)
private static JComponent filterRowFor(String tag, Map<String, Integer> counts, boolean deletable, Runnable changed) {
    JPanel row = new JPanel(new BorderLayout(8, 0));
    row.setOpaque(false);
    row.setBorder(new EmptyBorder(2, 0, 2, 0));
    row.setMaximumSize(new Dimension(520, 34));
    boolean on = Tags.isEnabled(tag);
    JLabel toggle = filterChip((on ? "☑ " : "☐ ") + tag, on, () -> {
        Tags.setEnabled(tag, !Tags.isEnabled(tag));
        changed.run();
    });
    toggle.setToolTipText(on ? "Shown. Click to hide assets tagged " + tag + " from character creation and level up"
            : "Hidden. Click to show assets tagged " + tag + " again");
    JPanel west = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    west.setOpaque(false);
    west.add(toggle);
    row.add(west, BorderLayout.WEST);
    int n = counts.getOrDefault(tag, 0);
    JLabel count = new JLabel(n + (n == 1 ? " asset" : " assets") + (on ? "" : " hidden"));
    count.setForeground(on ? App.TEXT_MUTED : App.ACCENT_HOVER);
    count.setFont(new Font("SansSerif", Font.PLAIN, 12));
    row.add(count, BorderLayout.CENTER);
    JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
    east.setOpaque(false);
    JButton info = iconButton("ⓘ", "What is " + tag + "?", () -> { });
    info.addActionListener(e -> showTagInfo(info, tag));
    east.add(info);
    if (deletable) {
        JButton del = iconButton("🗑", "Delete", () -> { });
        del.setToolTipText("Delete this tag from every asset");
        boolean[] armed = {false};
        del.addActionListener(e -> {
            if (!armed[0]) {
                armed[0] = true;
                del.setText("Delete " + tag + "?");
                del.setFont(new Font("SansSerif", Font.BOLD, 12));
                del.setForeground(App.ACCENT_HOVER);
                return;
            }
            Tags.deleteTag(tag);
            changed.run();
        });
        east.add(del);
    }
    row.add(east, BorderLayout.EAST);
    row.setAlignmentX(Component.LEFT_ALIGNMENT);
    return row;
}

// Derived spell tags as compact toggle chips
private static void derivedGroup(JPanel body, String title, List<String> tags, Runnable changed) {
    body.add(Box.createRigidArea(new Dimension(0, 10)));
    add(body, overlayLabel(title + " (automatic)"));
    JPanel chips = new JPanel(new WrapLayout(FlowLayout.LEFT, 5, 4));
    chips.setOpaque(false);
    for (String t : tags) {
        boolean on = Tags.isEnabled(t);
        JLabel chip = filterChip((on ? "☑ " : "☐ ") + t, on, () -> {
            Tags.setEnabled(t, !Tags.isEnabled(t));
            changed.run();
        });
        chip.setToolTipText(Tags.describe(t).split("\n")[0]);
        chips.add(chip);
    }
    chips.setMaximumSize(new Dimension(520, Integer.MAX_VALUE));
    add(body, chips);
}

// Keeps overlay content at its natural height (top-aligned) inside a taller scroll pane
private static JPanel topAnchored(JComponent c) {
    JPanel p = new JPanel(new BorderLayout());
    p.setOpaque(false);
    p.add(c, BorderLayout.NORTH);
    return p;
}

// FlowLayout that wraps onto new rows and reports the wrapped height (for chip rows)
static class WrapLayout extends FlowLayout {
    WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension d = layoutSize(target, false);
        d.width -= getHgap() + 1;
        return d;
    }

    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            Container c = target;
            while (c.getSize().width == 0 && c.getParent() != null) c = c.getParent();
            int targetWidth = c.getSize().width;
            if (targetWidth == 0) targetWidth = Integer.MAX_VALUE;
            Insets in = target.getInsets();
            int maxWidth = targetWidth - (in.left + in.right + getHgap() * 2);
            Dimension dim = new Dimension(0, 0);
            int rowW = 0, rowH = 0;
            for (Component m : target.getComponents()) {
                if (!m.isVisible()) continue;
                Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                if (rowW > 0 && rowW + getHgap() + d.width > maxWidth) {
                    addRow(dim, rowW, rowH);
                    rowW = 0;
                    rowH = 0;
                }
                if (rowW > 0) rowW += getHgap();
                rowW += d.width;
                rowH = Math.max(rowH, d.height);
            }
            addRow(dim, rowW, rowH);
            dim.width += in.left + in.right + getHgap() * 2;
            dim.height += in.top + in.bottom + getVgap() * 2;
            if (SwingUtilities.getAncestorOfClass(JScrollPane.class, target) != null && target.isValid()) dim.width -= getHgap() + 1;
            return dim;
        }
    }

    private void addRow(Dimension dim, int w, int h) {
        dim.width = Math.max(dim.width, w);
        if (dim.height > 0) dim.height += getVgap();
        dim.height += h;
    }
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
            case CustomAssets.SPELL_LIST: {
                List<String> vals = CustomAssets.list(a, f.key);
                if (vals.isEmpty()) break;
                sections.add(chipSection(f, vals));
                break;
            }
            case CustomAssets.MULTI: {
                List<String> vals = CustomAssets.list(a, f.key);
                if (vals.isEmpty()) break;
                sections.add(chipSection(f, vals));
                break;
            }
            case CustomAssets.TAGS:
                sections.add(tagSection(Tags.tagsOfAsset(cat, a))); // stored + automatic spell tags
                break;
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
            case CustomAssets.TOKEN: {
                Map<String, Object> tok = Tokens.read(a.get(f.key), Tokens.NPC_COLOR);
                JPanel sec = section(f.label);
                JPanel rowP = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
                rowP.setOpaque(false);
                Tokens.Preview pv = new Tokens.Preview(tok, CustomAssets.str(a, "name"), 64, App.GOLD);
                String size = CustomAssets.str(a, "size");
                JLabel desc = new JLabel("<html>" + App.escapeHtml(CustomAssets.str(tok, "shape"))
                        + (Tokens.hasImage(tok) ? " with a picture" : ", " + App.escapeHtml(Tokens.colorName(CustomAssets.str(tok, "color"))))
                        + "<br><span style='color:#aaa5a0'>" + App.escapeHtml(size.isEmpty() ? "Medium" : size) + " · "
                        + CombatPanel.footprintText(size) + " on the map</span></html>");
                desc.setForeground(App.TEXT_LIGHT);
                desc.setFont(new Font("SansSerif", Font.PLAIN, 13));
                rowP.add(pv);
                rowP.add(desc);
                rowP.setAlignmentX(Component.LEFT_ALIGNMENT);
                App.clickable(rowP, () -> App.showInfoOverlay(rowP, "Map Token", CustomAssets.str(a, "name"), Tokens.INFO, null));
                sec.add(rowP);
                sections.add(sec);
                break;
            }
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

// Spell Combat fields (shown only when the spell has a Combat mode) and NPC spellcasting numbers (only for casters)
private static final Set<String> SPELL_COMBAT_KEYS = new HashSet<>(Arrays.asList("combatTarget", "areaSize", "targetCount", "targetsPerLevel",
        "attackType", "saveAbility", "halfOnSave", "damageDice", "damagePerLevel", "damageType", "addModifier", "smiteWith", "effectDuration",
        "hostileEffect", "repeatSave", "ongoingDice", "ongoingType", "ongoingSave", "acBonus", "speedBonus", "rollDie", "againstDie", "markDice",
        "markType", "advantageAgainst", "revealInvisible", "noReactions", "tempHp"));
private static final Set<String> NPC_SPELL_KEYS = new HashSet<>(Arrays.asList("spellcastingAbility", "spellSaveDC", "spellAttackBonus", "spellcasterLevel"));

private static String displayValue(String cat, CustomAssets.Field f, Map<String, Object> a) {
    if (CustomAssets.SPELLS.equals(cat)) {
        String mode = CustomAssets.str(a, "combatMode");
        if ("combatMode".equals(f.key) && (mode.isEmpty() || "None".equals(mode))) return "";
        if (SPELL_COMBAT_KEYS.contains(f.key)) {
            if (mode.isEmpty() || "None".equals(mode)) return "";
            String v = CustomAssets.str(a, f.key).trim();
            if ("None".equals(v) || "0".equals(v)) return "";
        }
    }
    if (CustomAssets.NPCS.equals(cat) && NPC_SPELL_KEYS.contains(f.key)) {
        if (!CombatSpells.npcCaster(a)) return "";
        if (CustomAssets.INT.equals(f.type) && CustomAssets.intVal(a, f.key, 0) == 0) return "auto";
    }
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
        case CustomAssets.LINK_SPELL:
            App.showSpellOverlay(anchor, SpellData.lookup(value), value, null, false);
            break;
        case CustomAssets.LINK_TAG:
            showTagInfo(anchor, value);
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
    String traitType = CustomAssets.str(e, "traitType");
    boolean damageTrait = f.traitTypes && DamageRules.bitFor(traitType) != 0;
    if (damageTrait) {
        sub = traitType + " · " + CustomAssets.str(e, "damageType");
        if (desc.isEmpty()) desc = DamageRules.levelName(DamageRules.bitFor(traitType)) + " to "
                + (DamageRules.ALL_DAMAGE.equals(CustomAssets.str(e, "damageType")) ? "all damage" : CustomAssets.str(e, "damageType") + " damage") + ".";
    }
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
    String shown = desc.isEmpty() ? "No description." : desc + (damageTrait ? "\n\n" + DamageRules.INFO : "");
    App.clickable(card, () -> App.showInfoOverlay(card, title, damageTrait ? source + " · " + traitType : source, shown, null));
    return card;
}

// ===============================================================
// Editor
// ===============================================================
private interface FieldUI {
    JComponent component();
    Object value();
}

private JTextField currentNameField; // the open editor's name field (set while building the form)

@SuppressWarnings("unchecked")
private static Map<String, Object> castMap(Object o) {
    return (Map<String, Object>) o;
}

// Opens the create / edit form for `existing` (null = new asset in category `cat`)
void openEditor(String cat, Map<String, Object> existing) {
    category = cat; // e.g. editing a search result from another tab
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
            currentNameField = nameField; // token previews show the name's initials
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
            JPanel wrap = new JPanel(new BorderLayout()) { @Override public Dimension getMaximumSize() { return new Dimension(760, getPreferredSize().height); } };
            wrap.setOpaque(false);
            wrap.add(grid, BorderLayout.WEST);
            return ui(wrap, () -> {
                List<Object> out = new ArrayList<>();
                for (String o : opts) if (selected.contains(o)) out.add(o);
                return out;
            });
        }
        case CustomAssets.SPELL_LIST:
            return spellListUI(f, CustomAssets.list(holder, "v"));
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
        case CustomAssets.TAGS:
            return tagsUI(current);
        case CustomAssets.TOKEN: {
            JTextField nameField = currentNameField;
            Tokens.Editor ed = Tokens.editor(current instanceof Map ? castMap(current) : null,
                    () -> nameField != null ? nameField.getText() : "", Tokens.NPC_COLOR, null);
            if (nameField != null) {
                nameField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                    public void insertUpdate(javax.swing.event.DocumentEvent e) { ed.panel.repaint(); }
                    public void removeUpdate(javax.swing.event.DocumentEvent e) { ed.panel.repaint(); }
                    public void changedUpdate(javax.swing.event.DocumentEvent e) { ed.panel.repaint(); }
                });
            }
            return ui(ed.panel, ed::value);
        }
        default:
            JTextField tf = textField(CustomAssets.str(holder, "v"), "");
            return ui(tf, tf::getText);
    }
}

// Spell list picker: chips of the picked spells (✕ removes), and a search box + list to add more
private static FieldUI spellListUI(CustomAssets.Field f, List<String> current) {
    List<String> picked = new ArrayList<>(current);
    List<String> all = f.optionList();
    JPanel box = new JPanel();
    box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
    box.setOpaque(false);
    JPanel chips = new JPanel(new GridLayout(0, 3, 4, 4));
    chips.setOpaque(false);
    chips.setAlignmentX(Component.LEFT_ALIGNMENT);
    Runnable[] paint = new Runnable[1];
    paint[0] = () -> {
        chips.removeAll();
        if (picked.isEmpty()) {
            JLabel none = new JLabel("None yet.");
            none.setForeground(App.TEXT_MUTED);
            chips.add(none);
        }
        for (String s : picked) {
            Spell sp = SpellData.lookup(s);
            JLabel chip = new JLabel("✕  " + s + (sp != null ? (sp.level == 0 ? " · cantrip" : " · " + sp.level) : ""));
            chip.setForeground(App.GOLD);
            chip.setFont(new Font("SansSerif", Font.BOLD, 12));
            chip.setOpaque(true);
            chip.setBackground(App.SHEET_ROW_ALT);
            chip.setBorder(BorderFactory.createCompoundBorder(new LineBorder(App.SHEET_BORDER, 1, true), new EmptyBorder(4, 6, 4, 6)));
            chip.setToolTipText("Remove " + s);
            App.clickable(chip, () -> {
                picked.remove(s);
                paint[0].run();
            });
            chips.add(chip);
        }
        chips.revalidate();
        chips.repaint();
    };
    paint[0].run();
    JPanel add = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
    add.setOpaque(false);
    add.setAlignmentX(Component.LEFT_ALIGNMENT);
    JTextField search = new JTextField(12);
    App.styleTextField(search);
    search.setToolTipText("Type to filter the spell list");
    DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
    JComboBox<String> combo = new JComboBox<>(model);
    App.styleCombo(combo);
    combo.setPreferredSize(new Dimension(240, 30));
    Runnable filter = () -> {
        String q = search.getText().trim().toLowerCase();
        model.removeAllElements();
        for (String s : all) {
            if (picked.contains(s) || (!q.isEmpty() && !s.toLowerCase().contains(q))) continue;
            Spell sp = SpellData.lookup(s);
            model.addElement(s + (sp != null ? (sp.level == 0 ? "  (cantrip)" : "  (level " + sp.level + ")") : ""));
        }
    };
    filter.run();
    search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
        public void insertUpdate(javax.swing.event.DocumentEvent e) { filter.run(); }
        public void removeUpdate(javax.swing.event.DocumentEvent e) { filter.run(); }
        public void changedUpdate(javax.swing.event.DocumentEvent e) { filter.run(); }
    });
    JButton addB = new JButton("Add");
    App.styleSecondaryButton(addB);
    Runnable doAdd = () -> {
        Object v = combo.getSelectedItem();
        if (v == null) return;
        String name = String.valueOf(v).replaceAll(" +\\((cantrip|level [0-9]+)\\)$", "");
        if (!picked.contains(name)) picked.add(name);
        paint[0].run();
        filter.run();
    };
    addB.addActionListener(e -> doAdd.run());
    search.addActionListener(e -> doAdd.run()); // Enter adds the first match
    JLabel sl = new JLabel("Search");
    sl.setForeground(App.TEXT_MUTED);
    add.add(sl);
    add.add(search);
    add.add(combo);
    add.add(addB);
    box.add(chips);
    box.add(add);
    JPanel wrap = new JPanel(new BorderLayout()) { @Override public Dimension getMaximumSize() { return new Dimension(760, getPreferredSize().height); } };
    wrap.setOpaque(false);
    wrap.add(box, BorderLayout.WEST);
    return ui(wrap, () -> new ArrayList<Object>(picked));
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

// Tag picker for the editor form: toggle any existing tag, or type a new one
private static FieldUI tagsUI(Object current) {
    Map<String, Object> holder = new HashMap<>();
    holder.put("tags", current);
    LinkedHashSet<String> selected = new LinkedHashSet<>();
    for (String t : Tags.assetTags(holder)) {
        String c = Tags.canonical(t);
        selected.add(c != null ? c : t);
    }
    List<String> opts = Tags.allTags();
    for (String s : selected) if (!Tags.containsCI(opts, s)) opts.add(s);

    JPanel grid = new JPanel(new GridLayout(0, 3, 6, 6)); // fixed grid: predictable height inside the form
    grid.setOpaque(false);
    JPanel chips = new JPanel(new BorderLayout());
    chips.setOpaque(false);
    chips.setAlignmentX(Component.LEFT_ALIGNMENT);
    chips.add(grid, BorderLayout.WEST);
    Runnable rebuild = () -> {
        grid.removeAll();
        for (String o : opts) grid.add(toggleChip(o, selected));
        grid.revalidate();
        grid.repaint();
    };
    rebuild.run();

    JPanel newRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    newRow.setOpaque(false);
    newRow.setAlignmentX(Component.LEFT_ALIGNMENT);
    JTextField field = textField("", "New tag name");
    field.setColumns(16);
    JButton add = App.overlayButton("+ New Tag", false);
    JLabel err = new JLabel(" ");
    err.setForeground(App.ACCENT_HOVER);
    err.setFont(new Font("SansSerif", Font.BOLD, 12));
    Runnable addNew = () -> {
        String t = Tags.cleanName(field.getText());
        if (t == null) {
            err.setText("Tag names need 1-40 characters (no \"|\").");
            return;
        }
        if (Tags.isDerived(t)) {
            err.setText("School, level and class tags are added automatically.");
            return;
        }
        if (!Tags.containsCI(opts, t)) opts.add(t);
        if (!Tags.containsCI(selected, t)) selected.add(t);
        field.setText("");
        err.setText(" ");
        rebuild.run();
    };
    add.addActionListener(e -> addNew.run());
    field.addActionListener(e -> addNew.run());
    newRow.add(field);
    newRow.add(add);
    newRow.add(err);

    JPanel wrap = new JPanel() { @Override public Dimension getMaximumSize() { return new Dimension(760, getPreferredSize().height); } };
    wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
    wrap.setOpaque(false);
    wrap.add(chips);
    wrap.add(Box.createRigidArea(new Dimension(0, 6)));
    wrap.add(newRow);
    return ui(wrap, () -> {
        List<Object> out = new ArrayList<>();
        for (String o : opts) if (selected.contains(o)) out.add(o);
        return out;
    });
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
        // NPC traits: Trait type (plain, or a damage vulnerability / resistance / immunity) + damage type
        JComboBox<String> traitType = null, damageType = null;
        JPanel north = head;
        if (f.traitTypes) {
            JComboBox<String> tt = new JComboBox<>(DamageRules.TRAIT_TYPES);
            JComboBox<String> dt = new JComboBox<>(DamageRules.damageTypeOptions());
            for (JComboBox<String> c : Arrays.asList(tt, dt)) {
                App.styleCombo(c);
                c.setFont(new Font("SansSerif", Font.PLAIN, 13));
                c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }
            String curType = CustomAssets.str(e, "traitType");
            tt.setSelectedItem(Arrays.asList(DamageRules.TRAIT_TYPES).contains(curType) ? curType : DamageRules.TRAIT);
            String curDmg = CustomAssets.str(e, "damageType");
            dt.setSelectedItem(curDmg.isEmpty() ? "Fire" : curDmg);
            tt.setToolTipText("Trait type: a damage vulnerability, resistance or immunity changes the damage this NPC takes in Combat");
            dt.setToolTipText("Damage type affected");
            JLabel typeLbl = new JLabel("Type");
            typeLbl.setForeground(App.TEXT_MUTED);
            JLabel dmgLbl = new JLabel("Damage");
            dmgLbl.setForeground(App.TEXT_MUTED);
            JPanel typeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            typeRow.setOpaque(false);
            typeRow.add(typeLbl);
            typeRow.add(tt);
            typeRow.add(dmgLbl);
            typeRow.add(dt);
            Runnable sync = () -> {
                boolean dmg = !DamageRules.TRAIT.equals(tt.getSelectedItem());
                dmgLbl.setVisible(dmg);
                dt.setVisible(dmg);
                name.setToolTipText(dmg ? "Name (optional: blank shows e.g. \"Fire Resistance\")" : "Name");
                typeRow.revalidate();
            };
            tt.addActionListener(ev -> sync.run());
            sync.run();
            north = new JPanel(new BorderLayout(0, 6));
            north.setOpaque(false);
            north.add(head, BorderLayout.NORTH);
            north.add(typeRow, BorderLayout.SOUTH);
            traitType = tt;
            damageType = dt;
        }
        card.add(north, BorderLayout.NORTH);
        card.add(areaScroll(desc, 600), BorderLayout.CENTER);

        final JComboBox<String> traitTypeF = traitType, damageTypeF = damageType;
        Supplier<Map<String, Object>> reader = () -> {
            Map<String, Object> out = new LinkedHashMap<>();
            if (level != null) out.put("level", level.getValue());
            String nm = name.getText().trim();
            if (traitTypeF != null && !DamageRules.TRAIT.equals(traitTypeF.getSelectedItem())) {
                String tt = String.valueOf(traitTypeF.getSelectedItem());
                String dt = String.valueOf(damageTypeF.getSelectedItem());
                out.put("traitType", tt);
                out.put("damageType", dt);
                if (nm.isEmpty()) nm = (DamageRules.ALL_DAMAGE.equals(dt) ? "All Damage" : dt) + " " + tt.replace("Damage ", "");
            }
            out.put("name", nm);
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

    JTextArea paste = new JTextArea(5, 34);
    JLabel error = new JLabel(" ");
    if (DataPaths.web) {
        // Browser: pick a file from the computer (fills the JSON box below)
        add(body, overlayLabel("Pack file"));
        JButton choose = App.overlayButton("Choose File…", false);
        choose.addActionListener(e -> {
            error.setText("Choose a .json file in your browser's file picker…");
            WebBridge.pickTextFileAsync(text -> {
                if (text == null) error.setText("The browser couldn't open a file.");
                else if (text.isEmpty()) error.setText("No file chosen.");
                else if (WebBridge.TOO_LARGE.equals(text)) error.setText("That file is larger than 8 MB.");
                else {
                    paste.setText(text);
                    paste.setCaretPosition(0);
                    error.setText("File loaded. Click Import to add it.");
                }
            });
        });
        add(body, choose);
    } else {
        add(body, overlayLabel("Pack file (path to a .json file)"));
        add(body, path);
    }
    if (!files.isEmpty() && !DataPaths.web) {
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
                error.setText(DataPaths.web ? "Choose a file or paste JSON." : "Pick or type a pack file, or paste JSON.");
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
    add(body, overlayLabel(DataPaths.web ? "File name" : "Save as"));
    JTextField path = App.overlayField("homebrew_pack.json", 30);
    add(body, path);
    add(body, overlayHint(DataPaths.web ? "The pack is downloaded by your browser."
            : "Relative paths are saved next to the app: " + DataPaths.userDirectory().toAbsolutePath().normalize()));

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
        if (DataPaths.web) {
            try {
                String name = java.nio.file.Paths.get(p).getFileName().toString();
                int n = 0;
                for (String c : chosen) n += CustomAssets.count(c);
                WebBridge.downloadFile(name, JsonUtil.toPrettyJson(CustomAssets.packOf(chosen)));
                close[0].run();
                statusLabel.setText("Downloaded " + n + " assets as " + name + ".");
            } catch (Throwable ex) {
                error.setText("The browser couldn't start the download (" + ex.getClass().getSimpleName() + ").");
            }
            return;
        }
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
