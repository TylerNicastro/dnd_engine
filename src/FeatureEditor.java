import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;

// Asset Library editor for class / subclass features (field type CustomAssets.FEATURES). Each feature is a card built
// from the same parts the built-in features use (see ClassFeatures for the JSON): activation, uses, targets, healing,
// damage, a lasting effect, turn-economy grants, extra damage on a hit, always-on bonuses and a choice. Only the name and
// description are typed; every rule is a dropdown, a number or a checkbox. Parts are switched on with a checkbox, and
// only switched-on parts are saved.
class FeatureEditor {

static final class UI {
    JComponent component;
    Supplier<Object> value;
}

private static final Color CARD = App.SHEET_CARD, SUB = App.SHEET_ROW_ALT;

// Problems that stop a class from being finished (null = fine). Drafts skip this.
static String validate(List<Map<String, Object>> features) {
    Set<String> names = new HashSet<>();
    for (Map<String, Object> m : features) {
        String n = CustomAssets.str(m, "name").trim();
        if (n.isEmpty()) return "Give every feature a name (level " + CustomAssets.intVal(m, "level", 1) + " has one without).";
        if (!names.add(n.toLowerCase())) return "Two features are named \"" + n + "\". Feature names must be unique (other features refer to them).";
    }
    for (Map<String, Object> m : features) {
        String share = CustomAssets.str(m, "shareWith").trim();
        if (m.containsKey("shareWith") && share.isEmpty()) return "\"" + CustomAssets.str(m, "name") + "\" spends another feature's uses: pick which feature.";
        Object choice = m.get("choice");
        if (choice instanceof Map && ClassFeatures.C_NONE.equals(CustomAssets.str((Map<?, ?>) choice, "kind"))) {
            return "\"" + CustomAssets.str(m, "name") + "\" has a Choice part with no kind: pick what the player chooses, or switch the part off.";
        }
        Object eco = m.get("economy");
        if (eco instanceof Map && ClassFeatures.RS_POOL.equals(CustomAssets.str((Map<?, ?>) eco, "restore"))
                && CustomAssets.str((Map<?, ?>) eco, "restorePool").trim().isEmpty()) {
            return "\"" + CustomAssets.str(m, "name") + "\" refills another feature's uses: pick which feature.";
        }
    }
    return null;
}

static UI build(List<Map<String, Object>> initial) {
    JPanel outer = new JPanel();
    outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS));
    outer.setOpaque(false);
    JPanel rows = new JPanel();
    rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
    rows.setOpaque(false);
    rows.setAlignmentX(Component.LEFT_ALIGNMENT);
    List<Card> cards = new ArrayList<>();
    JLabel empty = muted("No features yet. Add one, or copy a built-in feature to start from.");

    Runnable[] relayout = new Runnable[1];
    relayout[0] = () -> {
        rows.removeAll();
        if (cards.isEmpty()) rows.add(empty);
        for (Card c : cards) {
            rows.add(c.panel);
            rows.add(Box.createRigidArea(new Dimension(0, 8)));
        }
        rows.revalidate();
        rows.repaint();
    };
    Supplier<List<String>> names = () -> {
        List<String> out = new ArrayList<>();
        for (Card c : cards) {
            String n = c.name.getText().trim();
            if (!n.isEmpty() && !out.contains(n)) out.add(n);
        }
        return out;
    };
    java.util.function.BiConsumer<Map<String, Object>, Boolean> add = (m, expanded) -> {
        Card[] self = new Card[1];
        self[0] = new Card(m, names, expanded, () -> {
            cards.remove(self[0]);
            relayout[0].run();
        });
        cards.add(self[0]);
        relayout[0].run();
    };
    for (Map<String, Object> m : initial) add.accept(m, false);
    relayout[0].run();

    JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    btns.setOpaque(false);
    btns.setAlignmentX(Component.LEFT_ALIGNMENT);
    JButton addBtn = App.overlayButton("+ Add feature", false);
    addBtn.addActionListener(e -> {
        Map<String, Object> blank = new LinkedHashMap<>();
        int next = 1;
        for (Card c : cards) next = Math.max(next, (Integer) c.level.getValue());
        blank.put("level", next);
        add.accept(blank, true);
    });
    JButton copy = App.overlayButton("Copy a built-in feature…", false);
    copy.setToolTipText("Start from one of the 2024 class features (Rage, Second Wind, Sneak Attack...) and change any part of it");
    copy.addActionListener(e -> showBuiltinPicker(copy, m -> add.accept(m, true)));
    btns.add(addBtn);
    btns.add(Box.createRigidArea(new Dimension(8, 0)));
    btns.add(copy);

    outer.add(rows);
    outer.add(Box.createRigidArea(new Dimension(0, 6)));
    outer.add(btns);
    UI ui = new UI();
    ui.component = outer;
    ui.value = () -> {
        List<Object> out = new ArrayList<>();
        for (Card c : cards) out.add(c.read());
        return out;
    };
    return ui;
}

// Every built-in feature, grouped by class: picking one adds a copy of its JSON
private static void showBuiltinPicker(Component anchor, java.util.function.Consumer<Map<String, Object>> onPick) {
    JPanel list = new JPanel();
    list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
    list.setOpaque(false);
    Runnable[] close = new Runnable[1];
    for (Map.Entry<String, List<ClassFeatures.Feature>> en : ClassFeatures.BUILTIN.entrySet()) {
        JLabel h = new JLabel(en.getKey().toUpperCase());
        h.setForeground(App.GOLD);
        h.setFont(new Font("SansSerif", Font.BOLD, 11));
        h.setBorder(new EmptyBorder(8, 0, 3, 0));
        h.setAlignmentX(Component.LEFT_ALIGNMENT);
        list.add(h);
        for (ClassFeatures.Feature f : en.getValue()) {
            JButton b = App.overlayButton("", false);
            b.setHorizontalAlignment(SwingConstants.LEFT);
            String sum = ClassFeatures.summary(f);
            b.setText("<html><b>" + App.escapeHtml(f.name) + "</b> <span style='color:#aaa5a0'>· level " + f.level + "</span>"
                    + (sum.isEmpty() ? "" : "<br><span style='color:#aaa5a0; font-size:9px'>" + App.escapeHtml(CustomAssets.snippet(sum, 110)) + "</span>") + "</html>");
            b.setAlignmentX(Component.LEFT_ALIGNMENT);
            b.setMaximumSize(new Dimension(560, 60));
            b.addActionListener(e -> {
                close[0].run();
                onPick.accept(CustomAssets.deepCopy(f.json));
            });
            list.add(b);
            list.add(Box.createRigidArea(new Dimension(0, 4)));
        }
    }
    JScrollPane sp = new JScrollPane(list);
    App.applyDarkScrollBar(sp);
    sp.setBorder(null);
    sp.setOpaque(false);
    sp.getViewport().setOpaque(false);
    sp.setPreferredSize(new Dimension(600, 460));
    close[0] = App.showSheetOverlay(anchor, "Copy a built-in feature", sp, null);
}

// ===============================================================
// One feature
// ===============================================================
private static final class Card {
    final JPanel panel = new JPanel(new BorderLayout(0, 6));
    final JSpinner level;
    final JTextField name;
    final JTextArea desc;
    final JComboBox<String> activation = combo(ClassFeatures.ACTIVATIONS);
    final List<Part> parts = new ArrayList<>();
    final JLabel summary = new JLabel();

    Card(Map<String, Object> m, Supplier<List<String>> names, boolean expanded, Runnable onRemove) {
        panel.setBackground(CARD);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createCompoundBorder(new LineBorder(App.SHEET_BORDER, 1, true), BorderFactory.createMatteBorder(0, 3, 0, 0, App.GOLD)),
                new EmptyBorder(8, 10, 10, 10)));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(700, Integer.MAX_VALUE));

        ClassFeatures.Feature f = ClassFeatures.parse(m);
        level = AssetLibraryPanel.spinner(f.level, 1, 20);
        level.setPreferredSize(new Dimension(64, 30));
        name = AssetLibraryPanel.textField(f.name, "Feature name (cosmetic)");
        name.setColumns(18);
        desc = AssetLibraryPanel.textArea(f.description, 3);
        activation.setSelectedItem(f.activation);

        JPanel head = new JPanel(new BorderLayout(8, 0));
        head.setOpaque(false);
        JPanel lv = row(muted("Level"), level);
        head.add(lv, BorderLayout.WEST);
        head.add(name, BorderLayout.CENTER);
        JButton toggle = Tokens.smallButton(expanded ? "Hide parts" : "Edit parts", "Show or hide this feature's rules");
        JButton remove = AssetLibraryPanel.iconButton("✕", "Remove this feature", onRemove);
        JPanel hb = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        hb.setOpaque(false);
        hb.add(toggle);
        hb.add(remove);
        head.add(hb, BorderLayout.EAST);
        panel.add(head, BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        summary.setForeground(App.TEXT_MUTED);
        summary.setFont(new Font("SansSerif", Font.PLAIN, 12));
        summary.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(summary);

        JPanel details = new JPanel();
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setOpaque(false);
        details.setAlignmentX(Component.LEFT_ALIGNMENT);
        details.add(caption("Description (cosmetic: shown on the sheet)"));
        JScrollPane ds = AssetLibraryPanel.areaScroll(desc, 640);
        ds.setAlignmentX(Component.LEFT_ALIGNMENT);
        details.add(ds);
        details.add(Box.createRigidArea(new Dimension(0, 6)));
        details.add(row(muted("Activation"), activation, help("Passive features are always on. On a hit / on a failed check are offered automatically in Combat. "
                + "Outside combat features are used from their card on the character sheet.")));

        parts.add(new UsesPart(f, names));
        parts.add(new TargetPart(f));
        parts.add(new HealPart(f));
        parts.add(new DamagePart(f));
        parts.add(new EffectPart(f));
        parts.add(new EconomyPart(f, names));
        parts.add(new OnHitPart(f));
        parts.add(new PassivePart(f));
        parts.add(new ChoicePart(f));
        for (Part p : parts) details.add(p.panel);
        body.add(details);
        panel.add(body, BorderLayout.CENTER);

        Runnable refreshSummary = () -> {
            ClassFeatures.Feature now = ClassFeatures.parse(read());
            String s = ClassFeatures.summary(now);
            summary.setText("<html><div style='width:540px'>" + App.escapeHtml(s.isEmpty() ? "Passive · no rules yet (a text-only feature)." : s) + "</div></html>");
        };
        refreshSummary.run();
        details.setVisible(expanded);
        toggle.addActionListener(e -> {
            details.setVisible(!details.isVisible());
            toggle.setText(details.isVisible() ? "Hide parts" : "Edit parts");
            refreshSummary.run();
            panel.revalidate();
            panel.repaint();
        });
    }

    Map<String, Object> read() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("level", level.getValue());
        out.put("name", name.getText().trim());
        out.put("description", desc.getText().trim());
        out.put("activation", String.valueOf(activation.getSelectedItem()));
        for (Part p : parts) if (p.on.isSelected()) p.write(out);
        return out;
    }
}

// ===============================================================
// Parts: a checkbox header, then its controls (shown while ticked)
// ===============================================================
private abstract static class Part {
    final JPanel panel = new JPanel(new BorderLayout(0, 4));
    final JCheckBox on;
    final JPanel body = new JPanel();

    Part(String title, String tip, boolean enabled) {
        panel.setOpaque(true);
        panel.setBackground(SUB);
        panel.setBorder(BorderFactory.createCompoundBorder(new EmptyBorder(6, 0, 0, 0),
                BorderFactory.createCompoundBorder(new LineBorder(App.SHEET_BORDER, 1, true), new EmptyBorder(4, 8, 6, 8))));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(680, Integer.MAX_VALUE));
        on = new JCheckBox(title, enabled);
        on.setOpaque(false);
        on.setForeground(App.GOLD);
        on.setFont(new Font("SansSerif", Font.BOLD, 13));
        on.setToolTipText(tip);
        on.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        panel.add(on, BorderLayout.NORTH);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        panel.add(body, BorderLayout.CENTER);
        body.setVisible(enabled);
        on.addActionListener(e -> {
            body.setVisible(on.isSelected());
            panel.revalidate();
            panel.repaint();
        });
    }

    void add(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(c);
    }

    abstract void write(Map<String, Object> out);
}

private static final String USES_OWN = "Its own uses", USES_SHARED = "Another feature's uses";

private static final class UsesPart extends Part {
    final JComboBox<String> mode = combo(USES_OWN, USES_SHARED);
    final AmountUI amount;
    final JComboBox<String> recharge = combo(ClassFeatures.RECHARGES);
    final JCheckBox spendAny = check("Spend any number of uses at once (a pool of points, like Lay on Hands)");
    final JComboBox<String> share;
    final JSpinner cost;

    UsesPart(ClassFeatures.Feature f, Supplier<List<String>> names) {
        super("Uses & recharge", "How many times it can be used before a rest, or which other feature's uses it spends",
                f.uses != null || !f.shareWith.isEmpty());
        amount = new AmountUI(f.uses != null ? f.uses : ClassFeatures.Amount.fixed(1), false);
        recharge.setSelectedItem(f.recharge);
        spendAny.setSelected(f.spendAny);
        share = namesCombo(names, f.shareWith);
        cost = AssetLibraryPanel.spinner(f.cost, 0, 20);
        mode.setSelectedItem(f.shareWith.isEmpty() ? USES_OWN : USES_SHARED);
        JPanel own = new JPanel();
        own.setLayout(new BoxLayout(own, BoxLayout.Y_AXIS));
        own.setOpaque(false);
        own.add(row(muted("Uses"), amount.panel));
        own.add(row(muted("Recharge"), recharge));
        own.add(row(spendAny));
        JPanel shared = new JPanel();
        shared.setLayout(new BoxLayout(shared, BoxLayout.Y_AXIS));
        shared.setOpaque(false);
        shared.add(row(muted("Spends uses of"), share, help("Like Divine Spark spending Channel Divinity, or Flurry of Blows spending Focus Points. "
                + "In a subclass, you can name a feature of its parent class.")));
        own.setAlignmentX(Component.LEFT_ALIGNMENT);
        shared.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(row(muted("Uses come from"), mode));
        add(own);
        add(shared);
        add(row(muted("Uses spent per activation"), cost, help("0 = free (it just needs uses to exist, e.g. converting with Font of Magic).")));
        Runnable sync = () -> {
            boolean s = USES_SHARED.equals(mode.getSelectedItem());
            own.setVisible(!s);
            shared.setVisible(s);
            body.revalidate();
        };
        mode.addActionListener(e -> sync.run());
        sync.run();
    }

    void write(Map<String, Object> out) {
        if (USES_SHARED.equals(mode.getSelectedItem())) {
            Object s = share.getSelectedItem();
            out.put("shareWith", s == null ? "" : String.valueOf(s).trim());
        } else {
            out.put("uses", amount.value().toJson());
            out.put("recharge", String.valueOf(recharge.getSelectedItem()));
            if (spendAny.isSelected()) out.put("spendAny", true);
        }
        out.put("cost", cost.getValue());
    }
}

private static final class TargetPart extends Part {
    final JComboBox<String> target = combo(ClassFeatures.TARGETS);
    final JSpinner range;
    final MultiUI types;
    final JComboBox<String> save = combo(ClassFeatures.DICE_ABILITIES);
    final JComboBox<String> dc = combo(ClassFeatures.DICE_ABILITIES);

    TargetPart(ClassFeatures.Feature f) {
        super("Targets & saving throw", "Who it affects, how far it reaches, and the save enemies make against it",
                !ClassFeatures.T_SELF.equals(f.target) || f.range > 0 || !f.onlyTypes.isEmpty() || !f.dcAbility.isEmpty() || !f.save.isEmpty());
        target.setSelectedItem(f.target);
        range = AssetLibraryPanel.spinner(f.range, 0, 500);
        types = new MultiUI(Arrays.asList(CustomAssets.CREATURE_TYPES), f.onlyTypes, 4);
        save.setSelectedItem(f.save);
        dc.setSelectedItem(f.dcAbility);
        add(row(muted("Target"), target));
        add(row(muted("Range (ft.)"), range, help("5 = touch. Area targets (allies / enemies in range) use it as the radius around you.")));
        add(caption("Only these creature types (none ticked = any)"));
        add(types.panel);
        add(row(muted("Enemies make a saving throw"), save, help("Blank: no save. On a failure they take the damage and the lasting effect; "
                + "on a success they take half (if the Damage part says so) and no effect.")));
        add(row(muted("Save DC ability"), dc, help("Its save DC is 8 + Proficiency Bonus + this modifier. Blank: the class's spellcasting ability (or WIS).")));
    }

    void write(Map<String, Object> out) {
        out.put("target", String.valueOf(target.getSelectedItem()));
        out.put("range", range.getValue());
        if (!types.value().isEmpty()) out.put("onlyTypes", new ArrayList<Object>(types.value()));
        String s = String.valueOf(save.getSelectedItem());
        if (!s.isEmpty()) out.put("save", s);
        String d = String.valueOf(dc.getSelectedItem());
        if (!d.isEmpty()) out.put("dcAbility", d);
    }
}

private static final class HealPart extends Part {
    final DiceUI dice;

    HealPart(ClassFeatures.Feature f) {
        super("Healing", "Hit Points it restores (to allies; with damage too, allies are healed and enemies harmed)", f.heal != null);
        dice = new DiceUI(f.heal != null ? f.heal : ClassFeatures.dice(1, 8));
        add(dice.panel);
        add(help("With \"spend any number of uses\", each use spent heals 1 Hit Point instead of rolling."));
    }

    void write(Map<String, Object> out) {
        out.put("heal", dice.value().toJson());
    }
}

private static final class DamagePart extends Part {
    final DiceUI dice;
    final JComboBox<String> type = combo(CustomAssets.DAMAGE_TYPES), type2 = combo(CustomAssets.withNone(CustomAssets.DAMAGE_TYPES));
    final JCheckBox half = check("Half damage on a successful save");

    DamagePart(ClassFeatures.Feature f) {
        super("Damage", "Damage it deals to enemies (set their saving throw under Targets & saving throw)", f.damage != null);
        dice = new DiceUI(f.damage != null ? f.damage : ClassFeatures.dice(1, 8));
        if (!f.damageType.isEmpty()) type.setSelectedItem(f.damageType);
        type2.setSelectedItem(f.damageType2.isEmpty() ? "None" : f.damageType2);
        half.setSelected(f.half);
        add(dice.panel);
        add(row(muted("Type"), type, muted("or"), type2, help("A second type lets the user pick one each time (Divine Spark: Radiant or Necrotic).")));
        add(row(half, help("Only matters when the Targets part gives enemies a saving throw. Without one, the damage just happens.")));
    }

    void write(Map<String, Object> out) {
        out.put("damage", dice.value().toJson());
        out.put("damageType", String.valueOf(type.getSelectedItem()));
        String t2 = String.valueOf(type2.getSelectedItem());
        if (!"None".equals(t2)) out.put("damageType2", t2);
        out.put("halfOnSave", half.isSelected());
    }
}

private static final class EffectPart extends Part {
    final JComboBox<String> duration = combo(ClassFeatures.DURATIONS);
    final JCheckBox sustain = check("Ends early unless extended each turn (attack an enemy or force a save), like Rage");
    final JCheckBox endOnDamage = check("Ends if it takes damage");
    final JCheckBox endOnIncap = check("Ends if the creature is Incapacitated");
    final JCheckBox noSpells = check("Can't cast spells or keep Concentration");
    final JCheckBox advStr = check("Advantage on Strength checks and saving throws");
    final JCheckBox reckless = check("Advantage on attack rolls using Strength");
    final JCheckBox advAgainst = check("Attack rolls against it have Advantage");
    final JCheckBox advSpell = check("Advantage on spell attack rolls");
    final JCheckBox shape = check("Becomes one of this feature's chosen creature forms (Wild Shape)");
    final MultiUI conds, resist, advSaves;
    final OptAmount melee;
    final JSpinner ac, speed, dcBonus;
    final OptDice insp, temp;

    EffectPart(ClassFeatures.Feature f) {
        super("Effect on the target", "What it does to its target: conditions, resistances, bonuses, a die to add, Temporary Hit Points. "
                + "\"Instant\" applies only the die / Temporary Hit Points.", f.hasEffect() || f.inspiration != null || f.tempHp != null);
        boolean on = f.hasEffect() || f.inspiration != null || f.tempHp != null;
        duration.setSelectedItem(on ? f.duration : ClassFeatures.D_MINUTE);
        sustain.setSelected(f.sustain);
        endOnDamage.setSelected(f.endOnDamage);
        endOnIncap.setSelected(f.endOnIncap);
        noSpells.setSelected(f.noSpells);
        advStr.setSelected(f.advStr);
        reckless.setSelected(f.recklessAtk);
        advAgainst.setSelected(f.advAgainst);
        advSpell.setSelected(f.advSpellAtk);
        shape.setSelected(f.shape);
        conds = new MultiUI(CustomAssets.conditionOptions(), f.conds, 4);
        resist = new MultiUI(Arrays.asList(CustomAssets.DAMAGE_TYPES), f.resist, 4);
        advSaves = new MultiUI(Arrays.asList(ClassFeatures.ABILITIES), f.advSaves, 6);
        melee = new OptAmount("Bonus damage on Strength attacks (weapon or Unarmed Strike)", f.meleeDamage, ClassFeatures.Amount.fixed(2));
        ac = AssetLibraryPanel.spinner(f.ac, -10, 10);
        speed = AssetLibraryPanel.spinner(f.speed, -60, 60);
        dcBonus = AssetLibraryPanel.spinner(f.dcBonus, -5, 5);
        insp = new OptDice("A die it can add to one failed D20 Test (Bardic Inspiration)", f.inspiration, ClassFeatures.dice(1, 6));
        temp = new OptDice("Temporary Hit Points", f.tempHp, ClassFeatures.dice(1, 8));
        add(row(muted("Lasts"), duration));
        add(sustain);
        add(endOnDamage);
        add(endOnIncap);
        add(caption("Conditions (on a failed save, if it has one)"));
        add(conds.panel);
        add(caption("Resistance to"));
        add(resist.panel);
        add(caption("Advantage on saving throws"));
        add(advSaves.panel);
        add(advStr);
        add(reckless);
        add(advAgainst);
        add(advSpell);
        add(shape);
        add(noSpells);
        add(melee.panel);
        add(row(muted("AC change"), ac, muted("Speed change (ft.)"), speed, muted("Spell save DC change"), dcBonus));
        add(insp.panel);
        add(temp.panel);
    }

    void write(Map<String, Object> out) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("duration", String.valueOf(duration.getSelectedItem()));
        flag(e, "sustain", sustain);
        flag(e, "endOnDamage", endOnDamage);
        flag(e, "endOnIncap", endOnIncap);
        flag(e, "noSpells", noSpells);
        flag(e, "advStr", advStr);
        flag(e, "recklessAtk", reckless);
        flag(e, "advAgainst", advAgainst);
        flag(e, "advSpellAtk", advSpell);
        flag(e, "shape", shape);
        if (!conds.value().isEmpty()) e.put("conds", new ArrayList<Object>(conds.value()));
        if (!resist.value().isEmpty()) e.put("resist", new ArrayList<Object>(resist.value()));
        if (!advSaves.value().isEmpty()) e.put("advSaves", new ArrayList<Object>(advSaves.value()));
        if (melee.on()) e.put("meleeDamage", melee.value().toJson());
        if ((Integer) ac.getValue() != 0) e.put("ac", ac.getValue());
        if ((Integer) speed.getValue() != 0) e.put("speed", speed.getValue());
        if ((Integer) dcBonus.getValue() != 0) e.put("dcBonus", dcBonus.getValue());
        if (insp.on()) e.put("inspiration", insp.value().toJson());
        if (temp.on()) e.put("tempHp", temp.value().toJson());
        out.put("effect", e);
    }
}

private static final class EconomyPart extends Part {
    final JCheckBox extra = check("One additional action (Action Surge)");
    final MultiUI actions;
    final JSpinner strikes, removeCost;
    final JCheckBox needsAttack = check("Only after taking the Attack action this turn");
    final JCheckBox beforeAttack = check("Only before your first attack roll this turn (like Reckless Attack)");
    final JComboBox<String> restore = combo(ClassFeatures.RESTORES);
    final JComboBox<String> pool;
    final MultiUI removeConds;

    EconomyPart(ClassFeatures.Feature f, Supplier<List<String>> names) {
        super("Turn economy & resources", "Extra actions, free Dash / Disengage / Dodge / Hide, Unarmed Strikes, regaining uses or spell slots",
                f.extraAction || !f.actions.isEmpty() || f.strikes > 0 || f.needsAttack || f.beforeAttack
                        || !ClassFeatures.RS_NONE.equals(f.restore) || !f.removeConds.isEmpty());
        extra.setSelected(f.extraAction);
        actions = new MultiUI(Arrays.asList(ClassFeatures.ACTION_GRANTS), f.actions, 4);
        strikes = AssetLibraryPanel.spinner(f.strikes, 0, 6);
        needsAttack.setSelected(f.needsAttack);
        beforeAttack.setSelected(f.beforeAttack);
        restore.setSelectedItem(f.restore);
        pool = namesCombo(names, f.restorePool);
        removeConds = new MultiUI(CustomAssets.conditionOptions(), f.removeConds, 4);
        removeCost = AssetLibraryPanel.spinner(f.removeCost, 0, 50);
        add(extra);
        add(caption("Also takes these actions (no extra cost)"));
        add(actions.panel);
        add(row(muted("Unarmed Strikes it grants"), strikes));
        add(needsAttack);
        add(beforeAttack);
        add(row(muted("Resources"), restore));
        add(row(muted("Feature whose uses it refills / converts"), pool));
        add(caption("Conditions it can remove from the target"));
        add(removeConds.panel);
        add(row(muted("Uses that costs"), removeCost, help("Lay on Hands: 5 points to remove Poisoned.")));
    }

    void write(Map<String, Object> out) {
        Map<String, Object> e = new LinkedHashMap<>();
        flag(e, "extraAction", extra);
        if (!actions.value().isEmpty()) e.put("actions", new ArrayList<Object>(actions.value()));
        if ((Integer) strikes.getValue() > 0) e.put("strikes", strikes.getValue());
        flag(e, "needsAttack", needsAttack);
        flag(e, "beforeAttack", beforeAttack);
        String rs = String.valueOf(restore.getSelectedItem());
        if (!ClassFeatures.RS_NONE.equals(rs)) e.put("restore", rs);
        Object p = pool.getSelectedItem();
        if (p != null && !String.valueOf(p).trim().isEmpty()) e.put("restorePool", String.valueOf(p).trim());
        if (!removeConds.value().isEmpty()) {
            e.put("removeConds", new ArrayList<Object>(removeConds.value()));
            e.put("removeCost", removeCost.getValue());
        }
        out.put("economy", e);
    }
}

private static final class OnHitPart extends Part {
    final DiceUI dice;
    final JCheckBox finesse = check("Needs a Finesse or Ranged weapon");
    final JCheckBox adv = check("Needs Advantage, or an ally within 5 ft. of the target (and no Disadvantage)");
    final JCheckBox melee = check("Melee attacks only");

    OnHitPart(ClassFeatures.Feature f) {
        super("Extra damage on a hit", "Added once per turn to a weapon or Unarmed Strike hit that meets the requirements (Sneak Attack)", f.hitDice != null);
        dice = new DiceUI(f.hitDice != null ? f.hitDice : ClassFeatures.dice(1, 6));
        finesse.setSelected(f.hitFinesseOrRanged);
        adv.setSelected(f.hitNeedsAdvantage);
        melee.setSelected(f.hitMeleeOnly);
        add(dice.panel);
        add(finesse);
        add(adv);
        add(melee);
        add(help("Set the activation to \"" + ClassFeatures.ON_HIT + "\" so Combat offers it on qualifying hits. The damage has the weapon's type."));
    }

    void write(Map<String, Object> out) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("dice", dice.value().toJson());
        flag(e, "finesseOrRanged", finesse);
        flag(e, "needsAdvantage", adv);
        flag(e, "meleeOnly", melee);
        out.put("onHit", e);
    }
}

private static final class PassivePart extends Part {
    final JComboBox<String> unarmored = combo(ClassFeatures.DICE_ABILITIES);
    final JCheckBox shieldOk = check("A Shield still allows it");
    final OptAmount speed, martial, mastery;
    final JCheckBox jack = check("Jack of All Trades: + half Proficiency Bonus to checks without proficiency");
    final MultiUI advSaves, bonusActions, weapons, armor;

    PassivePart(ClassFeatures.Feature f) {
        super("Always on", "Bonuses that apply all the time (Unarmored Defense, Danger Sense, Cunning Action...)",
                !f.unarmoredAc.isEmpty() || f.unarmoredSpeed != null || f.jackOfAllTrades || !f.passiveAdvSaves.isEmpty() || f.martialArts != null
                        || !f.bonusActions.isEmpty() || !f.weaponTraining.isEmpty() || !f.armorTraining.isEmpty() || f.weaponMastery != null);
        unarmored.setSelectedItem(f.unarmoredAc);
        shieldOk.setSelected(f.unarmoredShield);
        speed = new OptAmount("Extra Speed (ft.) while wearing no armor and no Shield", f.unarmoredSpeed, ClassFeatures.Amount.fixed(10));
        martial = new OptAmount("Martial Arts die size for Unarmed Strikes and Monk weapons (6 = d6)", f.martialArts, ClassFeatures.Amount.fixed(6));
        mastery = new OptAmount("Weapon Mastery: number of weapons", f.weaponMastery, ClassFeatures.Amount.fixed(2));
        jack.setSelected(f.jackOfAllTrades);
        advSaves = new MultiUI(Arrays.asList(ClassFeatures.ABILITIES), f.passiveAdvSaves, 6);
        bonusActions = new MultiUI(Arrays.asList("Dash", "Disengage", "Hide"), f.bonusActions, 3);
        weapons = new MultiUI(Arrays.asList(CustomAssets.WEAPON_TRAINING), f.weaponTraining, 2);
        armor = new MultiUI(Arrays.asList(CustomAssets.ARMOR_TRAINING), f.armorTraining, 4);
        add(row(muted("Unarmored AC: 10 + DEX +"), unarmored, shieldOk));
        add(speed.panel);
        add(jack);
        add(caption("Advantage on saving throws (Danger Sense)"));
        add(advSaves.panel);
        add(martial.panel);
        add(caption("Can take these actions as a Bonus Action (Cunning Action)"));
        add(bonusActions.panel);
        add(caption("Weapon proficiency"));
        add(weapons.panel);
        add(caption("Armor training"));
        add(armor.panel);
        add(mastery.panel);
    }

    void write(Map<String, Object> out) {
        Map<String, Object> e = new LinkedHashMap<>();
        String ua = String.valueOf(unarmored.getSelectedItem());
        if (!ua.isEmpty()) {
            e.put("unarmoredAc", ua);
            if (shieldOk.isSelected()) e.put("unarmoredShield", true);
        }
        if (speed.on()) e.put("unarmoredSpeed", speed.value().toJson());
        flag(e, "jackOfAllTrades", jack);
        if (!advSaves.value().isEmpty()) e.put("advSaves", new ArrayList<Object>(advSaves.value()));
        if (martial.on()) e.put("martialArts", martial.value().toJson());
        if (!bonusActions.value().isEmpty()) e.put("bonusActions", new ArrayList<Object>(bonusActions.value()));
        if (!weapons.value().isEmpty()) e.put("weaponTraining", new ArrayList<Object>(weapons.value()));
        if (!armor.value().isEmpty()) e.put("armorTraining", new ArrayList<Object>(armor.value()));
        if (mastery.on()) e.put("weaponMastery", mastery.value().toJson());
        out.put("passive", e);
    }
}

private static final class ChoicePart extends Part {
    final JComboBox<String> kind = combo(ClassFeatures.CHOICE_KINDS);
    final AmountUI count;
    final JPanel optsHolder = new JPanel(new BorderLayout());
    final JLabel optsCaption = caption("Limit to these options (none ticked = all)");
    MultiUI opts;
    // creature forms (Wild Shape): allowed types, max CR by class level, Fly Speed from a level
    final JPanel formPanel = new JPanel();
    final MultiUI formTypes;
    final CrUI maxCr;
    final JSpinner flyFrom;

    ChoicePart(ClassFeatures.Feature f) {
        super("Choice", "Something the player picks on the character sheet (Expertise, a Fighting Style, Metamagic, Wild Shape forms...)", !ClassFeatures.C_NONE.equals(f.choiceKind));
        kind.setSelectedItem(f.choiceKind);
        count = new AmountUI(f.choiceCount != null ? f.choiceCount : ClassFeatures.Amount.fixed(1), false);
        optsHolder.setOpaque(false);
        optsHolder.setAlignmentX(Component.LEFT_ALIGNMENT);
        formTypes = new MultiUI(Arrays.asList(CustomAssets.CREATURE_TYPES), f.formTypes.isEmpty() && f.maxCr == null ? Collections.singletonList("Beast") : f.formTypes, 4);
        maxCr = new CrUI(f.maxCr != null ? f.maxCr : ClassFeatures.Amount.fixed(ClassFeatures.crIndex("1/4")));
        flyFrom = AssetLibraryPanel.spinner(f.flyFrom, 0, 21);
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setOpaque(false);
        formPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel tcap = caption("Forms of these creature types (none ticked = any)");
        formPanel.add(tcap);
        formPanel.add(formTypes.panel);
        formPanel.add(row(muted("Highest CR"), maxCr.panel));
        formPanel.add(row(muted("Forms with a Fly Speed from class level"), flyFrom, help("0 = always allowed, 21 = never. Wild Shape: 8.")));
        add(row(muted("The player chooses"), kind));
        add(row(muted("How many"), count.panel));
        add(formPanel);
        add(optsCaption);
        add(optsHolder);
        List<String> initial = new ArrayList<>(f.choiceOptions);
        Runnable rebuild = () -> {
            String k = String.valueOf(kind.getSelectedItem());
            boolean forms = ClassFeatures.C_FORM.equals(k);
            formPanel.setVisible(forms);
            // forms are limited by type / CR / Fly Speed instead of a list of a hundred creatures
            optsCaption.setVisible(!forms);
            optsHolder.setVisible(!forms);
            List<String> keep = opts == null ? initial : opts.value();
            opts = new MultiUI(forms ? Collections.<String>emptyList() : ClassFeatures.allOptionsFor(k), keep, 3);
            optsHolder.removeAll();
            optsHolder.add(opts.panel, BorderLayout.CENTER);
            optsHolder.revalidate();
            optsHolder.repaint();
        };
        kind.addActionListener(e -> rebuild.run());
        rebuild.run();
    }

    void write(Map<String, Object> out) {
        Map<String, Object> e = new LinkedHashMap<>();
        String k = String.valueOf(kind.getSelectedItem());
        e.put("kind", k);
        e.put("count", count.value().toJson());
        if (opts != null && !opts.value().isEmpty()) e.put("options", new ArrayList<Object>(opts.value()));
        if (ClassFeatures.C_FORM.equals(k)) {
            e.put("formTypes", new ArrayList<Object>(formTypes.value()));
            e.put("maxCr", maxCr.value().toJson());
            e.put("flyFrom", flyFrom.getValue());
        }
        out.put("choice", e);
    }
}

// A challenge rating limit: one CR, or one per class level (stored as indexes into CustomAssets.CHALLENGE_RATINGS)
private static final class CrUI {
    final JPanel panel = new JPanel();
    final JComboBox<String> kind = combo(ClassFeatures.A_FIXED, ClassFeatures.A_TABLE);
    final JComboBox<String> fixed = combo(CustomAssets.CHALLENGE_RATINGS);
    final List<JComboBox<String>> table = new ArrayList<>();

    CrUI(ClassFeatures.Amount a) {
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        boolean tbl = ClassFeatures.A_TABLE.equals(a.kind);
        kind.setSelectedItem(tbl ? ClassFeatures.A_TABLE : ClassFeatures.A_FIXED);
        fixed.setSelectedIndex(clamp(a.value));
        JPanel grid = new JPanel(new GridLayout(4, 10, 3, 2));
        grid.setOpaque(false);
        for (int i = 0; i < 20; i++) {
            JComboBox<String> c = combo(CustomAssets.CHALLENGE_RATINGS);
            c.setSelectedIndex(clamp(tbl ? a.table[i] : a.value));
            c.setToolTipText("At class level " + (i + 1));
            c.setPreferredSize(new Dimension(58, 26));
            table.add(c);
        }
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 10; c++) {
                JLabel l = new JLabel("Lv " + (r * 10 + c + 1), SwingConstants.CENTER);
                l.setForeground(App.TEXT_MUTED);
                l.setFont(new Font("SansSerif", Font.PLAIN, 10));
                grid.add(l);
            }
            for (int c = 0; c < 10; c++) grid.add(table.get(r * 10 + c));
        }
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(row(kind, fixed));
        panel.add(grid);
        Runnable sync = () -> {
            boolean t = ClassFeatures.A_TABLE.equals(kind.getSelectedItem());
            fixed.setVisible(!t);
            grid.setVisible(t);
            panel.revalidate();
        };
        kind.addActionListener(e -> sync.run());
        sync.run();
    }

    private static int clamp(int i) {
        return Math.max(0, Math.min(CustomAssets.CHALLENGE_RATINGS.length - 1, i));
    }

    ClassFeatures.Amount value() {
        ClassFeatures.Amount a = new ClassFeatures.Amount();
        a.kind = String.valueOf(kind.getSelectedItem());
        a.value = fixed.getSelectedIndex();
        for (int i = 0; i < 20; i++) a.table[i] = table.get(i).getSelectedIndex();
        return a;
    }
}

// ===============================================================
// Value widgets
// ===============================================================
// An Amount: fixed number, Proficiency Bonus, ability modifier, class level (× N), half class level, or a per-level table
private static final class AmountUI {
    final JPanel panel = new JPanel();
    final JComboBox<String> kind = combo(ClassFeatures.AMOUNTS);
    final JSpinner value, mult;
    final JComboBox<String> ability = combo(ClassFeatures.ABILITIES);
    final JSpinner[] table = new JSpinner[20];
    final JPanel tablePanel = new JPanel(new GridLayout(4, 10, 3, 2));
    final boolean die;
    final JComboBox<String> dieCombo;

    AmountUI(ClassFeatures.Amount a, boolean die) {
        this.die = die;
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        kind.setSelectedItem(a.kind);
        value = AssetLibraryPanel.spinner(a.value, -50, 500);
        value.setPreferredSize(new Dimension(70, 30));
        mult = AssetLibraryPanel.spinner(a.mult, 1, 20);
        mult.setPreferredSize(new Dimension(60, 30));
        ability.setSelectedItem(a.ability);
        String[] dies = new String[ClassFeatures.DIE_SIZES.length];
        for (int i = 0; i < dies.length; i++) dies[i] = "d" + ClassFeatures.DIE_SIZES[i];
        dieCombo = combo(dies);
        dieCombo.setSelectedItem("d" + a.value);
        tablePanel.setOpaque(false);
        for (int i = 0; i < 20; i++) {
            table[i] = AssetLibraryPanel.spinner(a.table[i], -50, 500);
            table[i].setPreferredSize(new Dimension(54, 26));
            table[i].setToolTipText("At class level " + (i + 1));
        }
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 10; c++) {
                JLabel l = new JLabel("Lv " + (r * 10 + c + 1), SwingConstants.CENTER);
                l.setForeground(App.TEXT_MUTED);
                l.setFont(new Font("SansSerif", Font.PLAIN, 10));
                tablePanel.add(l);
            }
            for (int c = 0; c < 10; c++) tablePanel.add(table[r * 10 + c]);
        }
        tablePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel top = row(kind, die ? dieCombo : value, muted("×"), mult, ability);
        JLabel times = (JLabel) top.getComponent(2);
        panel.add(top);
        panel.add(tablePanel);
        Runnable sync = () -> {
            String k = String.valueOf(kind.getSelectedItem());
            (die ? dieCombo : value).setVisible(ClassFeatures.A_FIXED.equals(k));
            boolean lvl = ClassFeatures.A_LEVEL.equals(k) && !die;
            times.setVisible(lvl);
            mult.setVisible(lvl);
            ability.setVisible(ClassFeatures.A_MOD.equals(k));
            tablePanel.setVisible(ClassFeatures.A_TABLE.equals(k));
            panel.revalidate();
        };
        kind.addActionListener(e -> sync.run());
        sync.run();
    }

    ClassFeatures.Amount value() {
        ClassFeatures.Amount a = new ClassFeatures.Amount();
        a.kind = String.valueOf(kind.getSelectedItem());
        if (die) {
            String d = String.valueOf(dieCombo.getSelectedItem());
            a.value = Integer.parseInt(d.substring(1));
        } else {
            a.value = (Integer) value.getValue();
        }
        a.mult = (Integer) mult.getValue();
        a.ability = String.valueOf(ability.getSelectedItem());
        for (int i = 0; i < 20; i++) a.table[i] = (Integer) table[i].getValue();
        return a;
    }
}

// Dice: count (an Amount) d die (an Amount) + ability modifier + class level + flat bonus
private static final class DiceUI {
    final JPanel panel = new JPanel();
    final AmountUI count, die;
    final JComboBox<String> ability = combo(ClassFeatures.DICE_ABILITIES);
    final JCheckBox level = check("+ class level");
    final JSpinner bonus;

    DiceUI(ClassFeatures.Dice d) {
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        count = new AmountUI(d.count, false);
        die = new AmountUI(d.die, true);
        ability.setSelectedItem(d.ability);
        level.setSelected(d.addLevel);
        bonus = AssetLibraryPanel.spinner(d.bonus, -50, 100);
        bonus.setPreferredSize(new Dimension(64, 30));
        panel.add(row(muted("Number of dice"), count.panel));
        panel.add(row(muted("Die"), die.panel));
        panel.add(row(muted("+ modifier of"), ability, level, muted("+"), bonus));
    }

    ClassFeatures.Dice value() {
        ClassFeatures.Dice d = new ClassFeatures.Dice();
        d.count = count.value();
        d.die = die.value();
        d.ability = String.valueOf(ability.getSelectedItem());
        d.addLevel = level.isSelected();
        d.bonus = (Integer) bonus.getValue();
        return d;
    }
}

// An optional Amount (checkbox + amount)
private static final class OptAmount {
    final JPanel panel = new JPanel();
    final JCheckBox box;
    final AmountUI amount;

    OptAmount(String label, ClassFeatures.Amount current, ClassFeatures.Amount fallback) {
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        box = check(label);
        box.setSelected(current != null);
        amount = new AmountUI(current != null ? current : fallback, false);
        amount.panel.setVisible(current != null);
        box.addActionListener(e -> {
            amount.panel.setVisible(box.isSelected());
            panel.revalidate();
        });
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        amount.panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        amount.panel.setBorder(new EmptyBorder(0, 22, 0, 0));
        panel.add(box);
        panel.add(amount.panel);
    }

    boolean on() { return box.isSelected(); }
    ClassFeatures.Amount value() { return amount.value(); }
}

// Optional Dice (checkbox + dice)
private static final class OptDice {
    final JPanel panel = new JPanel();
    final JCheckBox box;
    final DiceUI dice;

    OptDice(String label, ClassFeatures.Dice current, ClassFeatures.Dice fallback) {
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        box = check(label);
        box.setSelected(current != null);
        dice = new DiceUI(current != null ? current : fallback);
        dice.panel.setVisible(current != null);
        box.addActionListener(e -> {
            dice.panel.setVisible(box.isSelected());
            panel.revalidate();
        });
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        dice.panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        dice.panel.setBorder(new EmptyBorder(0, 22, 0, 0));
        panel.add(box);
        panel.add(dice.panel);
    }

    boolean on() { return box.isSelected(); }
    ClassFeatures.Dice value() { return dice.value(); }
}

// A grid of checkboxes
private static final class MultiUI {
    final JPanel panel;
    final Map<String, JCheckBox> boxes = new LinkedHashMap<>();

    MultiUI(List<String> options, List<String> selected, int cols) {
        panel = new JPanel(new GridLayout(0, Math.max(1, cols), 6, 0));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (String o : options) {
            JCheckBox b = check(o);
            b.setSelected(selected != null && selected.contains(o));
            boxes.put(o, b);
            panel.add(b);
        }
        panel.setMaximumSize(new Dimension(660, panel.getPreferredSize().height));
    }

    List<String> value() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, JCheckBox> en : boxes.entrySet()) if (en.getValue().isSelected()) out.add(en.getKey());
        return out;
    }
}

// ===============================================================
// Small helpers
// ===============================================================
private static void flag(Map<String, Object> m, String key, JCheckBox b) {
    if (b.isSelected()) m.put(key, true);
}

static JComboBox<String> combo(String... options) {
    JComboBox<String> c = new JComboBox<>(options);
    App.styleCombo(c);
    c.setFont(new Font("SansSerif", Font.PLAIN, 13));
    c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    c.setMaximumSize(new Dimension(420, 30));
    return c;
}

// The other features' names (refreshed each time the list opens), keeping `current` even before the list loads
private static JComboBox<String> namesCombo(Supplier<List<String>> names, String current) {
    JComboBox<String> c = combo();
    c.addItem(current == null ? "" : current);
    c.setSelectedIndex(0);
    c.setPrototypeDisplayValue("Font of Magic: Create Spell Slot");
    c.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
        public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
            Object sel = c.getSelectedItem();
            c.removeAllItems();
            List<String> ns = new ArrayList<>(names.get());
            if (sel != null && !String.valueOf(sel).isEmpty() && !ns.contains(String.valueOf(sel))) ns.add(0, String.valueOf(sel));
            if (ns.isEmpty()) ns.add("");
            for (String n : ns) c.addItem(n);
            c.setSelectedItem(sel == null || String.valueOf(sel).isEmpty() ? ns.get(0) : sel);
        }
        public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) { }
        public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) { }
    });
    return c;
}

private static JCheckBox check(String text) {
    JCheckBox b = new JCheckBox(text);
    b.setOpaque(false);
    b.setForeground(App.TEXT_LIGHT);
    b.setFont(new Font("SansSerif", Font.PLAIN, 13));
    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    b.setAlignmentX(Component.LEFT_ALIGNMENT);
    return b;
}

private static JPanel row(JComponent... cs) {
    JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
    p.setOpaque(false);
    p.setAlignmentX(Component.LEFT_ALIGNMENT);
    for (JComponent c : cs) p.add(c);
    p.setMaximumSize(new Dimension(680, p.getPreferredSize().height + 40));
    return p;
}

private static JLabel muted(String text) {
    JLabel l = new JLabel(text);
    l.setForeground(App.TEXT_MUTED);
    l.setFont(new Font("SansSerif", Font.PLAIN, 13));
    l.setAlignmentX(Component.LEFT_ALIGNMENT);
    return l;
}

private static JLabel caption(String text) {
    JLabel l = new JLabel(text);
    l.setForeground(App.TEXT_MUTED);
    l.setFont(new Font("SansSerif", Font.BOLD, 11));
    l.setBorder(new EmptyBorder(6, 0, 2, 0));
    l.setAlignmentX(Component.LEFT_ALIGNMENT);
    return l;
}

// A small gold "?" that explains a control when clicked
private static JLabel help(String text) {
    JLabel l = new JLabel("ⓘ");
    l.setForeground(App.GOLD);
    l.setFont(new Font("SansSerif", Font.BOLD, 14));
    l.setToolTipText("<html><div style='width:300px'>" + App.escapeHtml(text) + "</div></html>");
    App.clickable(l, () -> App.showInfoOverlay(l, "How this works", "Feature builder", text, null));
    return l;
}
}
