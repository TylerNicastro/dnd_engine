import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;

// Level Up screen: (1) choose existing class or multiclass -> (2) pick the new class (multiclass only)
// -> (3) review gains, roll HP, pick a feat if one is unlocked, then Cancel / Confirm.
class LevelUpPanel {
    final JPanel panel;
    final java.util.function.IntConsumer show;

    private final CardLayout outerLayout;
    private final JPanel outerCards;
    private final JPanel stepHolder = new JPanel(new BorderLayout());
    private final Random rnd = new Random();

    private int charIndex = -1;
    private Map<String, Object> rec;

    LevelUpPanel(CardLayout outerLayout, JPanel outerCards) {
        this.outerLayout = outerLayout;
        this.outerCards = outerCards;
        panel = App.buildSectionShell("Level Up", outerLayout, outerCards, "CHARACTERS");
        stepHolder.setBackground(App.BG_DARK);
        panel.add(stepHolder, BorderLayout.CENTER);
        show = this::open;
    }

    // A feat choice as currently entered in the UI (error != null means it can't be confirmed yet)
    private static class FeatPick {
        String name;
        String category;
        String description;
        Map<String, Integer> increases = new LinkedHashMap<>();
        String error;
    }

    private void open(int index) {
        List<Object> all = CharacterStorage.loadSavedCharacters();
        if (index < 0 || index >= all.size() || !(all.get(index) instanceof Map)) return;
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> en : ((Map<?, ?>) all.get(index)).entrySet()) copy.put(String.valueOf(en.getKey()), en.getValue());
        if (CharacterStorage.recLevel(copy) >= FeatData.MAX_LEVEL) return;
        rec = copy;
        charIndex = index;
        showChooseStep();
        outerLayout.show(outerCards, "LEVEL_UP");
    }

    private void close() {
        outerLayout.show(outerCards, "CHARACTERS");
    }

    // ---------------------------------------------------------------
    // Step 1: level up an existing class, or multiclass
    // ---------------------------------------------------------------
    private void showChooseStep() {
        int level = CharacterStorage.recLevel(rec);
        LinkedHashMap<String, Integer> classLevels = CharacterStorage.recClassLevels(rec);

        JPanel content = column();
        add(content, heading(displayName() + ": Level " + level + " → " + (level + 1)));
        add(content, gap(6));
        add(content, muted("Classes: " + classSummary(classLevels) + "     •     Hit Dice: " + FeatData.hitDiceString(classLevels)));
        add(content, gap(28));
        add(content, subheading("How do you want to level up?"));
        add(content, gap(12));

        for (Map.Entry<String, Integer> en : classLevels.entrySet()) {
            String cls = en.getKey();
            int lv = en.getValue();
            JButton b = new JButton("Level up " + cls + "   (" + lv + " → " + (lv + 1) + ")");
            App.styleAccentButton(b);
            b.setFont(new Font("SansSerif", Font.BOLD, 16));
            b.addActionListener(e -> showDetailsStep(cls, false, this::showChooseStep));
            add(content, b);
            add(content, gap(10));
        }

        List<String> mcOptions = multiclassChoices(classLevels);
        JButton mcButton = new JButton("Multiclass into a new class…");
        App.styleSecondaryButton(mcButton);
        mcButton.setFont(new Font("SansSerif", Font.BOLD, 16));
        mcButton.setBorder(BorderFactory.createCompoundBorder(new LineBorder(App.TEXT_MUTED, 1, true), new EmptyBorder(11, 19, 11, 19)));
        mcButton.setEnabled(!mcOptions.isEmpty());
        mcButton.addActionListener(e -> showMulticlassStep());
        add(content, mcButton);

        setStep(content, null, cancelButton());
    }

    // ---------------------------------------------------------------
    // Step 2 (multiclass only): pick the new class
    // ---------------------------------------------------------------
    private void showMulticlassStep() {
        LinkedHashMap<String, Integer> classLevels = CharacterStorage.recClassLevels(rec);
        List<String> options = multiclassChoices(classLevels);

        JLabel infoTitle = new JLabel();
        JTextArea infoText = new JTextArea();
        Map<String, String> info = new LinkedHashMap<>();
        for (String c : options) {
            String[] ci = GameData.CLASS_INFO.get(c);
            info.put(c, "Hit Die: " + ci[0] + "\n\n" + ci[1]);
        }

        JPanel content = column();
        add(content, heading("Multiclass"));
        add(content, gap(6));
        add(content, muted("Choose a new class. You'll gain level 1 in it and add its hit die to your pool."));
        add(content, gap(18));
        App.CheckListResult list = App.buildCheckListPanel(options, 1, info, infoTitle, infoText, false, Math.min(10, options.size()), sel -> {
            if (!sel.isEmpty()) {
                infoTitle.setText(sel.get(0));
                infoText.setText(info.get(sel.get(0)));
            }
        });
        add(content, list.component);

        JButton back = new JButton("← Back");
        App.styleSecondaryButton(back);
        pad(back);
        back.addActionListener(e -> showChooseStep());

        JButton next = new JButton("Continue →");
        App.styleAccentButton(next);
        next.addActionListener(e -> {
            List<String> sel = list.getSelected.get();
            if (sel.isEmpty()) {
                infoTitle.setText("Choose a class");
                infoText.setText("Select the class you want to multiclass into.");
                return;
            }
            showDetailsStep(sel.get(0), true, this::showMulticlassStep);
        });

        setStep(content, infoPanel(infoTitle, infoText, "Select a class", "Click (i) next to a class for details."), back, cancelButton(), next);
    }

    // ---------------------------------------------------------------
    // Step 3: everything gained at this level + HP roll + feat choice
    // ---------------------------------------------------------------
    private void showDetailsStep(String cls, boolean isNewClass, Runnable onBack) {
        LinkedHashMap<String, Integer> classLevels = CharacterStorage.recClassLevels(rec);
        int oldClassLevel = classLevels.getOrDefault(cls, 0);
        int newClassLevel = oldClassLevel + 1;
        int oldLevel = CharacterStorage.recLevel(rec);
        int newLevel = oldLevel + 1;
        LinkedHashMap<String, Integer> newClassLevels = new LinkedHashMap<>(classLevels);
        newClassLevels.merge(cls, 1, Integer::sum);

        int die = GameData.hitDieMax(cls);
        boolean epic = FeatData.grantsEpicBoon(newClassLevel);
        boolean feat = !epic && FeatData.grantsFeat(cls, newClassLevel);
        Map<String, Integer> baseStats = CharacterStorage.recStatsMap(rec);
        String primaryClass = CharacterStorage.recStr(rec, "characterClass");
        if (primaryClass.isEmpty()) primaryClass = cls;
        final String primary = primaryClass;
        List<Integer> oldRolls = CharacterStorage.recHpRolls(rec);
        int baseCon = baseStats.getOrDefault("CON", 10);

        JLabel infoTitle = new JLabel();
        JTextArea infoText = new JTextArea();

        JPanel content = column();
        add(content, heading(isNewClass ? "New class: " + cls + " (level 1)" : cls + " level " + oldClassLevel + " → " + newClassLevel));
        add(content, gap(6));
        add(content, muted(displayName() + "  •  Character level " + oldLevel + " → " + newLevel));
        add(content, gap(24));

        // ---- What you gain ----
        add(content, subheading("What you gain"));
        add(content, gap(8));
        if (isNewClass) add(content, bullet("Multiclass: you gain your first level in " + cls + "."));
        if (die > 0) {
            add(content, bullet("Hit Die: +1d" + die + "   (Hit Dice " + FeatData.hitDiceString(classLevels)
                    + " → " + FeatData.hitDiceString(newClassLevels) + ")"));
            add(content, bullet("Hit Points: roll 1d" + die + " + your CON modifier (below)."));
        } else {
            add(content, bullet("Hit Points: this class has no hit die defined — enter the HP die result below."));
        }
        int pbOld = GameData.proficiencyBonus(oldLevel);
        int pbNew = GameData.proficiencyBonus(newLevel);
        add(content, bullet(pbNew > pbOld
                ? "Proficiency Bonus: " + App.signed(pbOld) + " → " + App.signed(pbNew)
                : "Proficiency Bonus: " + App.signed(pbOld) + " (no change)"));
        if (epic) add(content, bullet("Epic Boon unlocked: choose one below (it can raise a score up to 30)."));
        else if (feat) add(content, bullet("Feat unlocked: choose one below."));
        else add(content, bullet("No feat at this level." + nextFeatText(cls, newClassLevel)));
        add(content, gap(24));

        // ---- Feat selection state (built before HP so the HP preview can see CON changes) ----
        @SuppressWarnings("unchecked")
        Supplier<FeatPick>[] pickSupplier = new Supplier[]{(Supplier<FeatPick>) () -> null};
        JLabel featPreview = muted(" ");

        // ---- Hit Points ----
        add(content, subheading("Hit Points"));
        add(content, gap(8));

        int[] rolled = {0};
        JLabel rolledLbl = label(die > 0 ? "Rolled: —" : "");
        JButton rollBtn = new JButton("🎲 Roll 1d" + die);
        App.styleAccentButton(rollBtn);
        JTextField manualField = new JTextField(5);
        App.styleTextField(manualField);
        manualField.setMaximumSize(new Dimension(90, 38));
        JLabel conLbl = label(" ");
        JLabel hpLbl = label(" ");
        hpLbl.setForeground(App.GOLD);
        hpLbl.setFont(new Font("SansSerif", Font.BOLD, 16));

        int manualMax = die > 0 ? die : 99;
        Supplier<Integer> currentRoll = () -> {
            String t = manualField.getText().trim();
            if (!t.isEmpty()) {
                try {
                    int v = Integer.parseInt(t);
                    return (v >= 1 && v <= manualMax) ? v : -1;
                } catch (NumberFormatException ex) {
                    return -1;
                }
            }
            return rolled[0] > 0 ? rolled[0] : null;
        };

        Runnable refreshAll = () -> {
            FeatPick p = pickSupplier[0].get();
            Map<String, Integer> inc = (p != null && p.error == null) ? p.increases : Collections.emptyMap();

            // feat preview line
            if (feat || epic) {
                if (p == null) {
                    featPreview.setText("No feat selected yet.");
                    featPreview.setForeground(App.TEXT_MUTED);
                } else if (p.error != null) {
                    featPreview.setText(p.error);
                    featPreview.setForeground(App.ACCENT_HOVER);
                } else if (p.increases.isEmpty()) {
                    featPreview.setText(p.name + " — no ability score change.");
                    featPreview.setForeground(App.TEXT_LIGHT);
                } else {
                    List<String> parts = new ArrayList<>();
                    for (Map.Entry<String, Integer> en : p.increases.entrySet()) {
                        int cur = baseStats.getOrDefault(en.getKey(), 10);
                        parts.add(en.getKey() + " " + cur + " → " + (cur + en.getValue()));
                    }
                    featPreview.setText(p.name + ":  " + String.join(",   ", parts));
                    featPreview.setForeground(App.TEXT_LIGHT);
                }
            }

            int con = baseCon + inc.getOrDefault("CON", 0);
            int conMod = GameData.abilityModifier(con);
            conLbl.setText("CON modifier: " + App.signed(conMod) + (con != baseCon ? "   (after this feat's CON increase)" : ""));

            Integer oldHp = CharacterStorage.computeMaxHp(primary, oldRolls, baseCon);
            Integer roll = currentRoll.get();
            if (roll == null) {
                hpLbl.setText(die > 0 ? "Roll your hit die, or enter your own roll." : "Enter the HP die result.");
            } else if (roll < 0) {
                hpLbl.setText("Enter a whole number from 1 to " + manualMax + ".");
            } else {
                List<Integer> newRolls = new ArrayList<>(oldRolls);
                newRolls.add(roll);
                Integer newHp = CharacterStorage.computeMaxHp(primary, newRolls, con);
                int gain = CharacterStorage.hpGainForRoll(roll, conMod);
                String maxText = (oldHp != null && newHp != null) ? "     •     Max HP " + oldHp + " → " + newHp : "";
                hpLbl.setText("HP gained: " + roll + " " + (conMod >= 0 ? "+ " : "− ") + Math.abs(conMod) + " = " + gain + maxText);
            }
            content.revalidate();
        };

        rollBtn.addActionListener(e -> {
            rolled[0] = rnd.nextInt(die) + 1;
            rolledLbl.setText("Rolled: " + rolled[0]);
            rollBtn.setEnabled(false); // one roll only
            refreshAll.run();
        });
        onTextChange(manualField, refreshAll);

        if (die > 0) {
            JPanel rollRow = row();
            rollRow.add(rollBtn);
            rollRow.add(Box.createRigidArea(new Dimension(14, 0)));
            rollRow.add(rolledLbl);
            add(content, rollRow);
            add(content, gap(10));
        }
        JPanel manualRow = row();
        manualRow.add(label(die > 0 ? "Or enter your own roll (1–" + die + "):  " : "HP die result:  "));
        manualRow.add(manualField);
        add(content, manualRow);
        add(content, gap(10));
        add(content, conLbl);
        add(content, gap(6));
        add(content, hpLbl);
        add(content, gap(24));

        // ---- Feat ----
        if (feat || epic) {
            add(content, subheading(epic ? "Epic Boon" : "Feat"));
            add(content, gap(8));

            Set<String> taken = new HashSet<>();
            for (Map<String, Object> f : CharacterStorage.recFeats(rec)) taken.add(String.valueOf(f.get("name")));

            List<String> names = new ArrayList<>();
            Map<String, String> infoMap = new LinkedHashMap<>();
            if (epic) {
                for (Feat f : FeatData.EPIC_BOONS.values()) {
                    if (f.repeatable || !taken.contains(f.name)) { names.add(f.name); infoMap.put(f.name, f.infoText()); }
                }
            } else {
                names.add(FeatData.ASI_NAME);
                infoMap.put(FeatData.ASI_NAME, FeatData.ASI_DESCRIPTION);
                for (Feat f : FeatData.GENERAL_FEATS.values()) {
                    if (f.repeatable || !taken.contains(f.name)) { names.add(f.name); infoMap.put(f.name, f.infoText()); }
                }
            }
            names.add(FeatData.CUSTOM_NAME);
            infoMap.put(FeatData.CUSTOM_NAME, FeatData.CUSTOM_DESCRIPTION);

            JPanel featOptions = column();
            App.CheckListResult featList = App.buildCheckListPanel(names, 1, infoMap, infoTitle, infoText, true, 8, sel -> {
                featOptions.removeAll();
                if (sel.isEmpty()) {
                    pickSupplier[0] = () -> null;
                } else {
                    String chosen = sel.get(0);
                    infoTitle.setText(chosen);
                    infoText.setText(infoMap.getOrDefault(chosen, ""));
                    pickSupplier[0] = buildFeatOptions(chosen, baseStats, featOptions, refreshAll);
                }
                featOptions.revalidate();
                featOptions.repaint();
                refreshAll.run();
            });
            add(content, featList.component);
            add(content, gap(12));
            add(content, featOptions);
            add(content, gap(8));
            add(content, featPreview);
        }

        refreshAll.run();

        JButton back = new JButton("← Back");
        App.styleSecondaryButton(back);
        pad(back);
        back.addActionListener(e -> onBack.run());

        JButton confirm = new JButton("Confirm Level Up");
        App.styleAccentButton(confirm);
        confirm.addActionListener(e -> {
            Integer roll = currentRoll.get();
            if (roll == null || roll < 0) {
                JOptionPane.showMessageDialog(panel, die > 0
                                ? "Roll your 1d" + die + " hit die, or enter a roll from 1 to " + die + "."
                                : "Enter an HP die result from 1 to " + manualMax + ".",
                        "Hit Points Needed", JOptionPane.WARNING_MESSAGE);
                return;
            }
            FeatPick pick = null;
            if (feat || epic) {
                pick = pickSupplier[0].get();
                if (pick == null) {
                    JOptionPane.showMessageDialog(panel, "Choose a " + (epic ? "Epic Boon" : "feat") + " for this level.",
                            "Feat Needed", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (pick.error != null) {
                    JOptionPane.showMessageDialog(panel, pick.error, "Feat Incomplete", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }
            applyLevelUp(cls, newClassLevels, newClassLevel, newLevel, roll, pick);
        });

        setStep(content, infoPanel(infoTitle, infoText,
                        feat || epic ? "Choose your " + (epic ? "Epic Boon" : "feat") : "Level " + newLevel,
                        feat || epic ? "Click (i) next to a feat to read what it does." : "Roll your hit die, then confirm."),
                back, cancelButton(), confirm);
    }

    // Builds the extra inputs for the selected feat into `holder`, returning a reader for the current pick.
    private Supplier<FeatPick> buildFeatOptions(String name, Map<String, Integer> baseStats, JPanel holder, Runnable onChange) {
        if (FeatData.ASI_NAME.equals(name)) {
            JComboBox<String> mode = new JComboBox<>(new String[]{"+2 to one ability score", "+1 to two ability scores"});
            JComboBox<String> statA = new JComboBox<>(GameData.STAT_ORDER);
            JComboBox<String> statB = new JComboBox<>(GameData.STAT_ORDER);
            statB.setSelectedIndex(1);
            for (JComboBox<String> c : Arrays.asList(mode, statA, statB)) {
                App.styleCombo(c);
                c.addActionListener(e -> onChange.run());
            }
            JPanel rowB = row();
            rowB.add(label("+1 to:  "));
            rowB.add(statB);
            JLabel aLabel = label("+2 to:  ");
            mode.addActionListener(e -> {
                boolean two = mode.getSelectedIndex() == 1;
                aLabel.setText(two ? "+1 to:  " : "+2 to:  ");
                rowB.setVisible(two);
            });
            rowB.setVisible(false);

            JPanel modeRow = row();
            modeRow.add(mode);
            JPanel rowA = row();
            rowA.add(aLabel);
            rowA.add(statA);
            add(holder, modeRow);
            add(holder, gap(6));
            add(holder, rowA);
            add(holder, gap(6));
            add(holder, rowB);

            return () -> {
                FeatPick p = new FeatPick();
                p.name = "Ability Score Improvement";
                p.category = FeatData.CATEGORY_ASI;
                String a = (String) statA.getSelectedItem();
                if (mode.getSelectedIndex() == 0) {
                    p.increases.put(a, 2);
                } else {
                    String b = (String) statB.getSelectedItem();
                    if (a.equals(b)) {
                        p.error = "Choose two different ability scores for +1 / +1.";
                        return p;
                    }
                    p.increases.put(a, 1);
                    p.increases.put(b, 1);
                }
                p.description = describeIncreases(p.increases);
                checkCap(p, baseStats, FeatData.NORMAL_STAT_CAP);
                return p;
            };
        }

        if (FeatData.CUSTOM_NAME.equals(name)) {
            JTextField nameField = new JTextField(22);
            App.styleTextField(nameField);
            nameField.setMaximumSize(new Dimension(360, 38));
            JTextField descField = new JTextField(30);
            App.styleTextField(descField);
            descField.setMaximumSize(new Dimension(520, 38));
            JComboBox<String> stat = statCombo(GameData.STAT_ORDER);
            onTextChange(nameField, onChange);
            onTextChange(descField, onChange);
            stat.addActionListener(e -> onChange.run());

            JPanel r1 = row(); r1.add(label("Feat name:  ")); r1.add(nameField);
            JPanel r2 = row(); r2.add(label("Description (optional):  ")); r2.add(descField);
            JPanel r3 = row(); r3.add(label("+1 to:  ")); r3.add(stat);
            add(holder, r1); add(holder, gap(6));
            add(holder, r2); add(holder, gap(6));
            add(holder, r3);

            return () -> {
                FeatPick p = new FeatPick();
                p.name = nameField.getText().trim();
                p.category = FeatData.CATEGORY_CUSTOM;
                p.description = descField.getText().trim();
                if (p.name.isEmpty()) {
                    p.error = "Enter a name for your custom feat.";
                    return p;
                }
                String s = selectedStat(stat);
                if (s != null) p.increases.put(s, 1);
                checkCap(p, baseStats, FeatData.NORMAL_STAT_CAP);
                return p;
            };
        }

        Feat f = FeatData.lookup(name);
        if (f == null) return () -> null;
        JComboBox<String> stat = f.statOptions.length > 0 ? statCombo(f.statOptions) : null;
        if (stat != null) {
            stat.addActionListener(e -> onChange.run());
            JPanel r = row();
            r.add(label("+1 to:  "));
            r.add(stat);
            r.add(label("   (max " + f.statCap + ")"));
            add(holder, r);
        }
        return () -> {
            FeatPick p = new FeatPick();
            p.name = f.name;
            p.category = f.category;
            p.description = f.description;
            String s = stat != null ? selectedStat(stat) : null;
            if (s != null) p.increases.put(s, 1);
            checkCap(p, baseStats, f.statCap);
            return p;
        };
    }

    private void applyLevelUp(String cls, LinkedHashMap<String, Integer> newClassLevels, int newClassLevel,
                              int newLevel, int roll, FeatPick pick) {
        Map<String, Object> out = new LinkedHashMap<>(rec);
        if (CharacterStorage.recStr(out, "characterClass").isEmpty()) out.put("characterClass", cls);

        Map<String, Object> clOut = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> en : newClassLevels.entrySet()) clOut.put(en.getKey(), en.getValue());
        out.put("classLevels", clOut);

        List<Object> rolls = new ArrayList<>(CharacterStorage.recHpRolls(rec));
        rolls.add(roll);
        out.put("hpRolls", rolls);

        List<Object> feats = new ArrayList<>(CharacterStorage.recFeats(rec));
        if (pick != null) {
            Map<String, Object> featRec = new LinkedHashMap<>();
            featRec.put("name", pick.name);
            featRec.put("category", pick.category);
            featRec.put("className", cls);
            featRec.put("classLevel", newClassLevel);
            featRec.put("characterLevel", newLevel);
            featRec.put("description", pick.description == null ? "" : pick.description);
            Map<String, Object> inc = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> en : pick.increases.entrySet()) inc.put(en.getKey(), en.getValue());
            featRec.put("statIncreases", inc);
            feats.add(featRec);

            Map<String, Integer> stats = CharacterStorage.recStatsMap(rec);
            for (Map.Entry<String, Integer> en : pick.increases.entrySet()) stats.merge(en.getKey(), en.getValue(), Integer::sum);
            Map<String, Object> statsOut = new LinkedHashMap<>();
            for (String s : GameData.STAT_ORDER) if (stats.containsKey(s)) statsOut.put(s, stats.get(s));
            out.put("stats", statsOut);
        }
        out.put("feats", feats);
        CharacterStorage.refreshDerived(out);

        List<Object> all = CharacterStorage.loadSavedCharacters();
        if (charIndex >= 0 && charIndex < all.size()) {
            all.set(charIndex, out);
            CharacterStorage.saveAllCharacters(all);
        }
        JOptionPane.showMessageDialog(panel, displayName() + " is now level " + newLevel + "!",
                "Level Up", JOptionPane.INFORMATION_MESSAGE);
        close();
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------
    private static void checkCap(FeatPick p, Map<String, Integer> baseStats, int cap) {
        for (Map.Entry<String, Integer> en : p.increases.entrySet()) {
            int cur = baseStats.getOrDefault(en.getKey(), 10);
            if (cur + en.getValue() > cap) {
                p.error = en.getKey() + " is " + cur + " and can't go above " + cap + ". Choose a different ability score.";
                return;
            }
        }
    }

    private static String describeIncreases(Map<String, Integer> inc) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> en : inc.entrySet()) parts.add(en.getKey() + " +" + en.getValue());
        return String.join(", ", parts);
    }

    private static final String NO_INCREASE = "(no increase)";

    private static JComboBox<String> statCombo(String[] stats) {
        List<String> opts = new ArrayList<>(Arrays.asList(stats));
        opts.add(NO_INCREASE);
        JComboBox<String> c = new JComboBox<>(opts.toArray(new String[0]));
        App.styleCombo(c);
        return c;
    }

    private static String selectedStat(JComboBox<String> c) {
        Object v = c.getSelectedItem();
        return (v == null || NO_INCREASE.equals(v)) ? null : String.valueOf(v);
    }

    private static List<String> multiclassChoices(Map<String, Integer> classLevels) {
        List<String> out = new ArrayList<>();
        for (String c : FeatData.multiclassOptions()) if (!classLevels.containsKey(c)) out.add(c);
        return out;
    }

    private static String classSummary(Map<String, Integer> classLevels) {
        if (classLevels.isEmpty()) return "—";
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> en : classLevels.entrySet()) parts.add(en.getKey() + " " + en.getValue());
        return String.join(" / ", parts);
    }

    private static String nextFeatText(String cls, int newClassLevel) {
        for (int l = newClassLevel + 1; l <= FeatData.MAX_LEVEL; l++) {
            if (FeatData.grantsEpicBoon(l)) return " Next: Epic Boon at " + cls + " level " + l + ".";
            if (FeatData.grantsFeat(cls, l)) return " Next feat at " + cls + " level " + l + ".";
        }
        return "";
    }

    private String displayName() {
        String n = CharacterStorage.recStr(rec, "name");
        return n.isEmpty() ? "Unnamed Character" : n;
    }

    private JButton cancelButton() {
        JButton b = new JButton("Cancel");
        App.styleSecondaryButton(b);
        pad(b);
        b.addActionListener(e -> close());
        return b;
    }

    private static void pad(JButton b) {
        b.setBorder(BorderFactory.createCompoundBorder(new LineBorder(App.TEXT_MUTED, 1, true), new EmptyBorder(10, 18, 10, 18)));
    }

    // Lays out a step: scrollable content on the left, optional info panel on the right, buttons at the bottom.
    private void setStep(JPanel content, JPanel info, JButton... buttons) {
        JPanel north = new JPanel(new BorderLayout());
        north.setBackground(App.BG_DARK);
        north.setBorder(new EmptyBorder(28, 36, 28, 36));
        north.add(content, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(north);
        App.applyDarkScrollBar(scroll);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 14));
        bottom.setBackground(App.BG_PANEL);
        for (JButton b : buttons) bottom.add(b);

        stepHolder.removeAll();
        stepHolder.add(scroll, BorderLayout.CENTER);
        if (info != null) stepHolder.add(info, BorderLayout.EAST);
        stepHolder.add(bottom, BorderLayout.SOUTH);
        stepHolder.revalidate();
        stepHolder.repaint();
        SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(0));
    }

    private static JPanel infoPanel(JLabel title, JTextArea text, String initialTitle, String initialText) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(App.BG_PANEL);
        p.setBorder(new EmptyBorder(24, 22, 24, 22));
        p.setPreferredSize(new Dimension(340, 10));
        title.setText(initialTitle);
        title.setForeground(App.GOLD);
        title.setFont(new Font("Serif", Font.BOLD, 22));
        App.styleInfoTextArea(text);
        text.setText(initialText);
        p.add(title, BorderLayout.NORTH);
        p.add(text, BorderLayout.CENTER);
        return p;
    }

    private static void onTextChange(JTextField f, Runnable r) {
        f.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
        });
    }

    private static JPanel column() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(App.BG_DARK);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private static JPanel row() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        p.setBackground(App.BG_DARK);
        return p;
    }

    private static void add(JPanel parent, JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (c instanceof JPanel && ((JPanel) c).getLayout() instanceof BoxLayout
                && ((BoxLayout) ((JPanel) c).getLayout()).getAxis() == BoxLayout.X_AXIS) {
            c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        }
        parent.add(c);
    }

    private static JComponent gap(int h) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(0, h));
        p.setMaximumSize(new Dimension(0, h));
        p.setMinimumSize(new Dimension(0, h));
        return p;
    }

    private static JLabel heading(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(App.GOLD);
        l.setFont(new Font("Serif", Font.BOLD, 28));
        return l;
    }

    private static JLabel subheading(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(App.GOLD);
        l.setFont(new Font("SansSerif", Font.BOLD, 18));
        return l;
    }

    private static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(App.TEXT_LIGHT);
        l.setFont(new Font("SansSerif", Font.PLAIN, 15));
        return l;
    }

    private static JLabel muted(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(App.TEXT_MUTED);
        l.setFont(new Font("SansSerif", Font.PLAIN, 14));
        return l;
    }

    private static JLabel bullet(String text) {
        JLabel l = label("•  " + text);
        l.setBorder(new EmptyBorder(3, 4, 3, 0));
        return l;
    }
}
