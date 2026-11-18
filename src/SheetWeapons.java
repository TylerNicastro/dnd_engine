import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

// Character sheet cards for weapons (see WeaponRules for the rules and the saved fields):
//   Hands & Attacks : what each hand holds, the two-handed grip, and the attacks that result
//   Weapon Mastery  : which weapons' mastery properties the character uses (count from class levels);
//                     chosen and changed right here on the sheet, not during level up
class SheetWeapons {

private static Font font(int style, int size) {
    return new Font("SansSerif", style, size);
}

// ===============================================================
// Hands & Attacks
// ===============================================================
static JPanel handsAttacksCard(Map<?, ?> rec, Consumer<Map<String, Object>> onSave) {
    WeaponRules.Hands h = WeaponRules.hands(rec);
    JPanel content = new JPanel();
    content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
    content.setOpaque(false);

    WeaponEntry mainW = WeaponData.find(h.main);
    content.add(handRow("MAIN HAND", h.main, h.twoHanded && mainW != null ? "both hands" : null, () -> showHandPicker(content, rec, true, onSave)));
    content.add(handRow("OFF HAND", h.twoHanded ? "" : h.off, h.twoHanded ? "two-handed grip" : null,
            () -> showHandPicker(content, rec, false, onSave)));

    JCheckBox grip = new JCheckBox("Wield with both hands");
    grip.setOpaque(false);
    grip.setForeground(App.TEXT_LIGHT);
    grip.setFont(font(Font.PLAIN, 12));
    grip.setFocusPainted(false);
    grip.setSelected(h.twoHanded);
    if (mainW != null && mainW.twoHanded()) {
        grip.setEnabled(false);
        grip.setToolTipText(mainW.name + " is Two-Handed: it always needs both hands.");
    } else if (mainW != null && mainW.versatile()) {
        grip.setToolTipText("Versatile: " + mainW.damageText(false) + " in one hand, " + mainW.damageText(true)
                + " in two. Using both hands empties your off hand.");
        grip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        grip.addActionListener(e -> {
            WeaponRules.Hands n = WeaponRules.hands(rec);
            n.twoHanded = grip.isSelected();
            if (n.twoHanded) n.off = "";
            save(rec, n, onSave);
        });
    } else {
        grip.setEnabled(false);
        grip.setToolTipText(mainW == null ? "Hold a Versatile or Two-Handed weapon to use both hands."
                : mainW.name + " isn't Versatile, so two hands don't change its damage.");
    }
    grip.setAlignmentX(Component.LEFT_ALIGNMENT);
    content.add(grip);
    content.add(Box.createRigidArea(new Dimension(0, 6)));

    // attacks
    JLabel cap = new JLabel("ATTACKS");
    cap.setForeground(App.TEXT_MUTED);
    cap.setFont(font(Font.BOLD, 10));
    cap.setAlignmentX(Component.LEFT_ALIGNMENT);
    content.add(cap);
    content.add(Box.createRigidArea(new Dimension(0, 3)));
    List<WeaponRules.Attack> attacks = WeaponRules.characterAttacks(rec);
    int per = WeaponRules.attacksPerAction(rec);
    boolean alt = false;
    for (WeaponRules.Attack a : attacks) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(alt);
        row.setBackground(App.SHEET_ROW_ALT);
        alt = !alt;
        row.setBorder(new EmptyBorder(3, 4, 3, 4));
        JLabel name = new JLabel("<html><b>" + App.escapeHtml(a.name) + "</b>"
                + (a.mastery.isEmpty() ? "" : " <span style='color:#d4af37'>· " + a.mastery + "</span>")
                + "<br><span style='color:#aaa5a0'>" + App.escapeHtml(a.reachText()) + "</span></html>");
        name.setForeground(App.TEXT_LIGHT);
        name.setFont(font(Font.PLAIN, 12));
        row.add(name, BorderLayout.CENTER);
        JLabel nums = new JLabel("<html><div style='text-align:right'><b>" + WeaponRules.signed(a.toHit) + "</b> to hit<br>"
                + App.escapeHtml(a.damageText()) + "</div></html>");
        nums.setForeground(App.TEXT_LIGHT);
        nums.setFont(font(Font.PLAIN, 12));
        row.add(nums, BorderLayout.EAST);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        App.clickable(row, () -> showAttackDetail(row, rec, a));
        content.add(row);
    }
    if (per > 1) {
        JLabel ea = new JLabel("Extra Attack: " + per + " attacks when you take the Attack action.");
        ea.setForeground(App.TEXT_MUTED);
        ea.setFont(font(Font.ITALIC, 11));
        ea.setBorder(new EmptyBorder(4, 2, 0, 0));
        ea.setAlignmentX(Component.LEFT_ALIGNMENT);
        App.clickable(ea, () -> App.showInfoOverlay(ea, "Extra Attack", "Class feature",
                "You can attack " + per + " times instead of once whenever you take the Attack action on your turn.", null));
        content.add(ea);
    }
    return App.sheetCard("Hands & Attacks", content);
}

private static JPanel handRow(String caption, String item, String note, Runnable onClick) {
    JPanel row = new JPanel(new BorderLayout(8, 0));
    row.setOpaque(false);
    row.setBorder(new EmptyBorder(3, 4, 3, 4));
    JLabel c = new JLabel(caption);
    c.setForeground(App.TEXT_MUTED);
    c.setFont(font(Font.BOLD, 10));
    c.setPreferredSize(new Dimension(72, 18));
    row.add(c, BorderLayout.WEST);
    String text = item.isEmpty() ? (note != null ? note : "Empty") : item + (note != null ? "  (" + note + ")" : "");
    JLabel v = new JLabel(text);
    v.setForeground(item.isEmpty() ? App.TEXT_MUTED : App.GOLD);
    v.setFont(font(Font.BOLD, 13));
    row.add(v, BorderLayout.CENTER);
    JLabel edit = new JLabel("change ▾");
    edit.setForeground(App.TEXT_MUTED);
    edit.setFont(font(Font.PLAIN, 11));
    row.add(edit, BorderLayout.EAST);
    row.setAlignmentX(Component.LEFT_ALIGNMENT);
    row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
    row.setToolTipText("Choose what this hand holds");
    App.clickable(row, onClick);
    return row;
}

private static void save(Map<?, ?> rec, WeaponRules.Hands h, Consumer<Map<String, Object>> onSave) {
    Map<String, Object> work = App.recCopy(rec);
    work.put("hands", WeaponRules.handsJson(h));
    onSave.accept(work);
}

// Pick what a hand holds: nothing, a weapon, a shield or any other item from Equipment
private static void showHandPicker(Component anchor, Map<?, ?> rec, boolean mainHand, Consumer<Map<String, Object>> onSave) {
    WeaponRules.Hands cur = WeaponRules.hands(rec);
    List<String> equipment = CharacterStorage.recList(rec, "equipment");
    JPanel list = new JPanel();
    list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
    list.setOpaque(false);
    Runnable[] close = new Runnable[1];

    List<String> weapons = new ArrayList<>(), shields = new ArrayList<>(), other = new ArrayList<>();
    for (String e : equipment) {
        String base = GameData.stripEquipmentCount(e);
        if (WeaponData.find(base) != null) weapons.add(base);
        else if (WeaponRules.isShield(base)) shields.add(base);
        else if (WeaponData.findAmmo(base) == null && GameData.ARMOR_AC.get(base) == null) other.add(base); // body armor and ammo aren't held
    }
    String otherHand = mainHand ? cur.off : cur.main;
    java.util.function.BiConsumer<String, String> addOption = (item, sub) -> {
        boolean usedByOther = !item.isEmpty() && item.equalsIgnoreCase(otherHand) && WeaponRules.countOf(equipment, item) < 2 && !cur.twoHanded;
        WeaponEntry w = WeaponData.find(item);
        boolean twoHandedOff = !mainHand && w != null && w.twoHanded();
        JButton b = App.overlayButton(item.isEmpty() ? "Empty hand" : item, false);
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setText("<html><b>" + App.escapeHtml(item.isEmpty() ? "Empty hand" : item) + "</b>"
                + (sub.isEmpty() ? "" : "<br><span style='color:#aaa5a0'>" + App.escapeHtml(sub) + "</span>") + "</html>");
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(420, 52));
        if (usedByOther) {
            b.setEnabled(false);
            b.setToolTipText("Already in your other hand (you have only one).");
        } else if (twoHandedOff) {
            b.setEnabled(false);
            b.setToolTipText("Two-Handed weapons go in your main hand and take both hands.");
        }
        b.addActionListener(e -> {
            WeaponRules.Hands n = WeaponRules.hands(rec);
            if (mainHand) {
                n.main = item;
                WeaponEntry nw = WeaponData.find(item);
                if (nw != null && nw.twoHanded()) {
                    n.twoHanded = true;
                    n.off = "";
                } else if (nw == null || !nw.versatile()) {
                    n.twoHanded = false;
                }
            } else {
                n.off = item;
                if (!item.isEmpty()) n.twoHanded = false; // the off hand is busy again
            }
            close[0].run();
            save(rec, n, onSave);
        });
        list.add(b);
        list.add(Box.createRigidArea(new Dimension(0, 4)));
    };
    addOption.accept("", "Free for spellcasting components, grappling, or drawing an item.");
    if (!weapons.isEmpty()) list.add(caption("WEAPONS"));
    for (String w : weapons) {
        WeaponEntry we = WeaponData.find(w);
        addOption.accept(w, we.damage + (we.versatile() ? " (" + we.damageText(true) + " two-handed)" : "")
                + (we.properties.isEmpty() ? "" : " · " + String.join(", ", we.properties)));
    }
    if (!shields.isEmpty()) list.add(caption("SHIELDS"));
    for (String s : shields) addOption.accept(s, "+" + GameData.shieldBonus(GameData.ARMOR_AC.get(s)) + " AC while held");
    if (!other.isEmpty()) list.add(caption("OTHER ITEMS"));
    for (String o : other) addOption.accept(o, "");
    if (weapons.isEmpty() && shields.isEmpty() && other.isEmpty()) {
        JLabel none = new JLabel("Nothing to hold yet. Add weapons or a shield under Equipment.");
        none.setForeground(App.TEXT_MUTED);
        list.add(none);
    }
    JScrollPane sp = new JScrollPane(list);
    App.applyDarkScrollBar(sp);
    sp.setBorder(null);
    sp.setOpaque(false);
    sp.getViewport().setOpaque(false);
    sp.setPreferredSize(new Dimension(440, Math.min(420, list.getPreferredSize().height + 10)));
    close[0] = App.showSheetOverlay(anchor, mainHand ? "Main Hand" : "Off Hand", sp, null);
}

private static JLabel caption(String text) {
    JLabel l = new JLabel(text);
    l.setForeground(App.TEXT_MUTED);
    l.setFont(font(Font.BOLD, 11));
    l.setBorder(new EmptyBorder(8, 0, 4, 0));
    l.setAlignmentX(Component.LEFT_ALIGNMENT);
    return l;
}

// Breakdown of one attack: to-hit and damage parts, properties, mastery, ammunition
static void showAttackDetail(Component anchor, Map<?, ?> rec, WeaponRules.Attack a) {
    JPanel body = new JPanel();
    body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
    body.setOpaque(false);
    body.add(table("ATTACK ROLL: d20 " + WeaponRules.signed(a.toHit), a.hitBreakdown));
    body.add(Box.createRigidArea(new Dimension(0, 8)));
    body.add(table("DAMAGE: " + a.damageText(), a.damageBreakdown));
    body.add(Box.createRigidArea(new Dimension(0, 8)));
    List<String[]> info = new ArrayList<>();
    info.add(new String[]{"Reach / range", a.reachText()});
    if (!a.ammo.isEmpty()) {
        int have = 0;
        if (rec != null) for (String e : CharacterStorage.recList(rec, "equipment")) {
            AmmoItem am = WeaponData.findAmmo(e);
            if (am != null && am.name.equalsIgnoreCase(a.ammo)) have += App.itemCount(e);
        }
        info.add(new String[]{"Ammunition", a.ammo + (rec != null ? " (" + have + " in Equipment)" : "")});
    }
    if (!a.source.isEmpty()) info.add(new String[]{"From", a.source});
    body.add(table("DETAILS", info));
    if (a.weapon != null && !a.weapon.rawProperties.isEmpty()) {
        body.add(Box.createRigidArea(new Dimension(0, 8)));
        body.add(caption("PROPERTIES (CLICK FOR RULES)"));
        JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        chips.setOpaque(false);
        chips.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (int i = 0; i < a.weapon.rawProperties.size(); i++) {
            String p = a.weapon.rawProperties.get(i);
            String label = i < a.weapon.properties.size() ? a.weapon.properties.get(i) : p;
            chips.add(chip(label, () -> App.showInfoOverlay(chips, p, "Weapon property", WeaponRules.PROPERTY_INFO.getOrDefault(p, ""), null)));
        }
        body.add(chips);
    }
    if (a.weapon != null && !"—".equals(a.weapon.mastery)) {
        body.add(Box.createRigidArea(new Dimension(0, 8)));
        String m = a.weapon.mastery;
        boolean has = !a.mastery.isEmpty();
        JLabel ml = new JLabel("<html><div style='width:360px'><b style='color:#d4af37'>Mastery: " + m + "</b>"
                + (has ? "" : " <span style='color:#aaa5a0'>(not one of your Weapon Mastery choices)</span>")
                + "<br>" + App.escapeHtml(WeaponRules.MASTERY_INFO.getOrDefault(m, "")) + "</div></html>");
        ml.setForeground(has ? App.TEXT_LIGHT : App.TEXT_MUTED);
        ml.setFont(font(Font.PLAIN, 12));
        ml.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(ml);
    }
    if (!a.notes.isEmpty()) {
        body.add(Box.createRigidArea(new Dimension(0, 8)));
        JLabel n = new JLabel("<html><div style='width:360px'>" + App.escapeHtml(a.notes) + "</div></html>");
        n.setForeground(App.TEXT_MUTED);
        n.setFont(font(Font.ITALIC, 12));
        n.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(n);
    }
    App.showSheetOverlay(anchor, a.name, body, null);
}

private static JPanel table(String title, List<String[]> rows) {
    JPanel p = new JPanel(new GridBagLayout());
    p.setOpaque(false);
    p.setAlignmentX(Component.LEFT_ALIGNMENT);
    GridBagConstraints c = new GridBagConstraints();
    c.anchor = GridBagConstraints.WEST;
    c.insets = new Insets(1, 0, 1, 0);
    c.gridx = 0;
    c.gridy = 0;
    c.gridwidth = 2;
    JLabel t = new JLabel(title);
    t.setForeground(App.GOLD);
    t.setFont(font(Font.BOLD, 12));
    p.add(t, c);
    c.gridwidth = 1;
    for (String[] r : rows) {
        c.gridy++;
        c.gridx = 0;
        c.weightx = 1;
        JLabel l = new JLabel(r[0]);
        l.setForeground(App.TEXT_MUTED);
        l.setFont(font(Font.PLAIN, 12));
        p.add(l, c);
        c.gridx = 1;
        c.weightx = 0;
        JLabel v = new JLabel(r[1]);
        v.setForeground(App.TEXT_LIGHT);
        v.setFont(font(Font.BOLD, 12));
        v.setBorder(new EmptyBorder(0, 16, 0, 0));
        p.add(v, c);
    }
    return p;
}

private static JLabel chip(String text, Runnable onClick) {
    JLabel l = new JLabel(text);
    l.setForeground(App.TEXT_LIGHT);
    l.setFont(font(Font.BOLD, 11));
    l.setBorder(BorderFactory.createCompoundBorder(new LineBorder(App.SHEET_BORDER, 1, true), new EmptyBorder(2, 7, 2, 7)));
    App.clickable(l, onClick);
    return l;
}

// ===============================================================
// Weapon Mastery
// ===============================================================
static JPanel masteryCard(Map<?, ?> rec, Consumer<Map<String, Object>> onSave) {
    int max = WeaponRules.masteryCount(rec);
    List<String> chosen = WeaponRules.masteries(rec);
    JPanel content = new JPanel();
    content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
    content.setOpaque(false);

    JLabel count = new JLabel(max == 0 && chosen.isEmpty() ? "None from your classes or feats."
            : chosen.size() + " of " + max + " chosen" + (chosen.size() < max ? " (click ✎ to pick " + (max - chosen.size()) + " more)" : ""));
    count.setForeground(chosen.size() > max ? App.ACCENT_HOVER : App.TEXT_MUTED);
    count.setFont(font(Font.PLAIN, 12));
    count.setAlignmentX(Component.LEFT_ALIGNMENT);
    App.clickable(count, () -> showMasteryInfo(count, rec));
    content.add(count);
    if (chosen.size() > max) {
        JLabel over = new JLabel("Too many: remove " + (chosen.size() - max) + " (your mastery count went down).");
        over.setForeground(App.ACCENT_HOVER);
        over.setFont(font(Font.BOLD, 11));
        over.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(over);
    }
    JPanel chips = new JPanel();
    chips.setLayout(new BoxLayout(chips, BoxLayout.Y_AXIS));
    chips.setOpaque(false);
    chips.setAlignmentX(Component.LEFT_ALIGNMENT);
    for (String name : chosen) {
        WeaponEntry w = WeaponData.find(name);
        String m = w == null ? "?" : w.mastery;
        JLabel l = new JLabel("<html><b>" + App.escapeHtml(name) + "</b> <span style='color:#d4af37'>· " + App.escapeHtml(m) + "</span></html>");
        l.setForeground(App.TEXT_LIGHT);
        l.setFont(font(Font.PLAIN, 12));
        l.setBorder(new EmptyBorder(2, 4, 2, 4));
        App.clickable(l, () -> App.showInfoOverlay(l, m, "Weapon Mastery · " + name,
                WeaponRules.MASTERY_INFO.getOrDefault(m, "Unknown weapon.") + "\n\nYou can use this property with " + name
                        + " because it's one of your Weapon Mastery choices.", null));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        chips.add(l);
    }
    if (!chosen.isEmpty()) {
        content.add(Box.createRigidArea(new Dimension(0, 4)));
        content.add(chips);
    }
    if (max == 0 && chosen.isEmpty()) {
        JLabel hint = new JLabel("<html><div style='width:250px'>Barbarians, Fighters, Paladins, Rangers and Rogues get Weapon Mastery, and anyone can with the Weapon Master feat.</div></html>");
        hint.setForeground(App.TEXT_MUTED);
        hint.setFont(font(Font.ITALIC, 11));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(hint);
        return App.sheetCard("Weapon Mastery", content);
    }
    JButton edit = App.sheetIconButton("✎", "Choose your Weapon Mastery weapons");
    edit.addActionListener(e -> showMasteryEditor(edit, rec, onSave));
    return App.sheetCardWithAction("Weapon Mastery", edit, content);
}

private static void showMasteryInfo(Component anchor, Map<?, ?> rec) {
    List<String[]> rows = new ArrayList<>();
    for (String s : WeaponRules.masterySources(rec)) {
        int colon = s.indexOf(':');
        rows.add(new String[]{s.substring(0, colon), s.substring(colon + 1).trim()});
    }
    rows.add(new String[]{"Total", String.valueOf(WeaponRules.masteryCount(rec))});
    App.showInfoOverlay(anchor, "Weapon Mastery", "Class feature",
            "Your training lets you use the Mastery property of a number of kinds of weapons you're proficient with. "
                    + "Pick them here; you can change them on this sheet whenever your table allows (the rules let you swap one after each Long Rest). "
                    + "The count rises with Barbarian and Fighter levels.", rows);
}

private static void showMasteryEditor(Component anchor, Map<?, ?> rec, Consumer<Map<String, Object>> onSave) {
    int max = WeaponRules.masteryCount(rec);
    LinkedHashSet<String> picked = new LinkedHashSet<>(WeaponRules.masteries(rec));
    List<String> owned = new ArrayList<>();
    for (String e : CharacterStorage.recList(rec, "equipment")) owned.add(GameData.stripEquipmentCount(e).toLowerCase());

    JPanel body = new JPanel(new BorderLayout(0, 10));
    body.setOpaque(false);
    JLabel counter = new JLabel();
    counter.setFont(font(Font.BOLD, 13));
    body.add(counter, BorderLayout.NORTH);

    JPanel grid = new JPanel(new GridLayout(0, 2, 8, 2));
    grid.setOpaque(false);
    List<JCheckBox> boxes = new ArrayList<>();
    Runnable refresh = () -> {
        counter.setText(picked.size() + " of " + max + " chosen");
        counter.setForeground(picked.size() > max ? App.ACCENT_HOVER : App.GOLD);
        for (JCheckBox b : boxes) b.setEnabled(b.isSelected() || picked.size() < max);
    };
    Set<String> owner = new HashSet<>(picked);
    Tags.setOwned(owner);
    for (WeaponEntry w : WeaponData.WEAPON_DB.weapons) {
        boolean already = picked.contains(w.name);
        if (!already && (!WeaponRules.masteryAllowed(rec, w) || !Tags.allowed(CustomAssets.WEAPONS, w.name))) continue;
        JPanel cell = new JPanel(new BorderLayout(4, 0));
        cell.setOpaque(false);
        JCheckBox cb = new JCheckBox(w.name + (owned.contains(w.name.toLowerCase()) ? " ●" : ""), already);
        cb.setOpaque(false);
        cb.setForeground(App.TEXT_LIGHT);
        cb.setFont(font(Font.PLAIN, 12));
        cb.setFocusPainted(false);
        cb.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cb.setToolTipText(w.infoText().replace("\n", " · "));
        cb.addActionListener(e -> {
            if (cb.isSelected()) picked.add(w.name);
            else picked.remove(w.name);
            refresh.run();
        });
        boxes.add(cb);
        cell.add(cb, BorderLayout.CENTER);
        JLabel m = new JLabel(w.mastery);
        m.setForeground(App.GOLD);
        m.setFont(font(Font.BOLD, 11));
        m.setBorder(new EmptyBorder(0, 4, 0, 6));
        App.clickable(m, () -> App.showInfoOverlay(m, w.mastery, "Mastery property", WeaponRules.MASTERY_INFO.getOrDefault(w.mastery, ""), null));
        cell.add(m, BorderLayout.EAST);
        grid.add(cell);
    }
    JScrollPane sp = new JScrollPane(grid);
    App.applyDarkScrollBar(sp);
    sp.setBorder(new LineBorder(App.SHEET_BORDER, 1, true));
    sp.setOpaque(false);
    sp.getViewport().setOpaque(false);
    sp.setPreferredSize(new Dimension(520, Math.min(380, grid.getPreferredSize().height + 10)));
    JPanel center = new JPanel(new BorderLayout(0, 6));
    center.setOpaque(false);
    center.add(sp, BorderLayout.CENTER);
    JLabel legend = new JLabel("● = in your Equipment.  Limits: " + String.join("; ", WeaponRules.masterySources(rec)));
    legend.setForeground(App.TEXT_MUTED);
    legend.setFont(font(Font.PLAIN, 11));
    center.add(legend, BorderLayout.SOUTH);
    body.add(center, BorderLayout.CENTER);

    Runnable[] close = new Runnable[1];
    JButton cancel = App.overlayButton("Cancel", false);
    cancel.addActionListener(e -> close[0].run());
    JButton saveB = App.overlayButton("Save", true);
    saveB.addActionListener(e -> {
        if (picked.size() > max) {
            counter.setText("Choose at most " + max + " (" + picked.size() + " selected).");
            counter.setForeground(App.ACCENT_HOVER);
            return;
        }
        Map<String, Object> work = App.recCopy(rec);
        work.put("weaponMasteries", new ArrayList<Object>(picked));
        close[0].run();
        onSave.accept(work);
    });
    JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    btns.setOpaque(false);
    btns.add(cancel);
    btns.add(saveB);
    body.add(btns, BorderLayout.SOUTH);
    refresh.run();
    close[0] = App.showSheetOverlay(anchor, "Weapon Mastery", body, null);
}
}
