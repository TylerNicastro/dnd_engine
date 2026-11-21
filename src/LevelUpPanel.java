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
    final java.util.function.IntConsumer showDown; // Level Down: remove the last level from one class

    private final CardLayout outerLayout;
    private final JPanel outerCards;
    private final JPanel stepHolder = new JPanel(new BorderLayout());
    private final Random rnd = new Random();

    private int charIndex = -1;
    private Map<String, Object> rec;

    LevelUpPanel(CardLayout outerLayout, JPanel outerCards) {
        this.outerLayout = outerLayout;
        this.outerCards = outerCards;
        panel = App.buildSectionShell("Level Up / Down", outerLayout, outerCards, "CHARACTERS");
        stepHolder.setBackground(App.BG_DARK);
        panel.add(stepHolder, BorderLayout.CENTER);
        show = this::open;
        showDown = this::openDown;
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
        Tags.setOwned(null); // level-up pickers follow the content filters (Asset Library > Tags & Filters)
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
        int[] oldSlots = SpellData.spellSlots(classLevels), newSlots = SpellData.spellSlots(newClassLevels);
        if (!Arrays.equals(oldSlots, newSlots)) {
            add(content, bullet("Spell slots: " + SpellData.slotSummary(oldSlots) + "  →  " + SpellData.slotSummary(newSlots)));
        }
        if ("Warlock".equals(cls)) {
            int oc = SpellData.pactSlotCount(oldClassLevel), nc = SpellData.pactSlotCount(newClassLevel);
            int ol = SpellData.pactSlotLevel(oldClassLevel), nl = SpellData.pactSlotLevel(newClassLevel);
            if (oc != nc || ol != nl) {
                add(content, bullet("Pact Magic: " + (oc == 0 ? "none" : oc + " × level " + ol) + "  →  " + nc + " × level " + nl + " (recharge on a Short Rest)"));
            }
        }
        if (SpellData.isCaster(cls)) add(content, bullet("Spells: see the Spells section below."));

        // Class features gained at this level: homebrew (Asset Library) or built-in 2024 (ClassFeatures)
        Map<String, Object> classAsset = CustomAssets.classAsset(cls);
        if (classAsset != null) {
            for (Map<String, Object> f : CustomAssets.featuresAt(classAsset, newClassLevel)) {
                add(content, featureBullet(f, cls + " " + newClassLevel, infoTitle, infoText));
            }
        } else {
            for (ClassFeatures.Feature f : ClassFeatures.builtin(cls)) {
                if (f.level == newClassLevel) add(content, featureBullet(f.json, cls + " " + newClassLevel, infoTitle, infoText));
            }
        }
        String currentSub = CustomAssets.recSubclasses(rec).get(cls);
        Map<String, Object> subAsset = currentSub == null ? null : CustomAssets.subclassAsset(cls, currentSub);
        if (subAsset != null) {
            for (Map<String, Object> f : CustomAssets.featuresAt(subAsset, newClassLevel)) {
                add(content, featureBullet(f, currentSub + " " + newClassLevel, infoTitle, infoText));
            }
        }
        add(content, gap(24));

        // ---- Homebrew subclass choice (offered once the character reaches a subclass's level) ----
        String[] subclassPick = {null};
        if (currentSub == null || subAsset == null) {
            List<String> subNames = new ArrayList<>();
            Map<String, String> subInfo = new LinkedHashMap<>();
            for (Map<String, Object> s : CustomAssets.subclassesFor(cls)) {
                if (CustomAssets.intVal(s, "subclassLevel", 3) > newClassLevel) continue;
                if (!Tags.allowedAsset(CustomAssets.SUBCLASSES, s)) continue;
                String n = CustomAssets.str(s, "name");
                subNames.add(n);
                StringBuilder sb = new StringBuilder(CustomAssets.str(s, "description"));
                for (Map<String, Object> f : CustomAssets.featuresUpTo(s, newClassLevel)) {
                    sb.append(sb.length() > 0 ? "\n\n" : "").append("Level ").append(CustomAssets.intVal(f, "level", 1))
                            .append(": ").append(CustomAssets.str(f, "name"));
                    if (!CustomAssets.str(f, "description").isEmpty()) sb.append(" — ").append(CustomAssets.str(f, "description"));
                }
                subInfo.put(n, sb.toString());
            }
            if (!subNames.isEmpty()) {
                String none = "No homebrew subclass";
                subNames.add(none);
                subInfo.put(none, "Keep going without one of the Asset Library's subclasses (for example, if you use a published subclass). You'll be offered the choice again next level.");
                add(content, subheading(cls + " Subclass"));
                add(content, gap(8));
                add(content, muted("Homebrew subclasses available at " + cls + " level " + newClassLevel + ". Their features up to this level apply right away."));
                add(content, gap(8));
                App.CheckListResult subList = App.buildCheckListPanel(subNames, 1, subInfo, infoTitle, infoText, false,
                        Math.min(6, subNames.size()), sel -> {
                            subclassPick[0] = sel.isEmpty() || none.equals(sel.get(0)) ? null : sel.get(0);
                            if (!sel.isEmpty()) {
                                infoTitle.setText(sel.get(0));
                                infoText.setText(subInfo.get(sel.get(0)));
                            }
                        });
                add(content, subList.component);
                add(content, gap(24));
            }
        }

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
            int speciesHp = SpeciesTraits.hpBonus(rec) > 0 ? 1 : 0; // Dwarven Toughness: +1 per level
            if (oldHp != null) oldHp += speciesHp * oldLevel;
            Integer roll = currentRoll.get();
            if (roll == null) {
                hpLbl.setText(die > 0 ? "Roll your hit die, or enter your own roll." : "Enter the HP die result.");
            } else if (roll < 0) {
                hpLbl.setText("Enter a whole number from 1 to " + manualMax + ".");
            } else {
                List<Integer> newRolls = new ArrayList<>(oldRolls);
                newRolls.add(roll);
                Integer newHp = CharacterStorage.computeMaxHp(primary, newRolls, con);
                if (newHp != null) newHp += speciesHp * (oldLevel + 1);
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
                    if (!Tags.allowed(CustomAssets.FEATS, f.name)) continue;
                    if (f.repeatable || !taken.contains(f.name)) { names.add(f.name); infoMap.put(f.name, f.infoText()); }
                }
            } else {
                names.add(FeatData.ASI_NAME);
                infoMap.put(FeatData.ASI_NAME, FeatData.ASI_DESCRIPTION);
                for (Feat f : FeatData.GENERAL_FEATS.values()) {
                    if (!Tags.allowed(CustomAssets.FEATS, f.name)) continue;
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
            add(content, gap(24));
        }

        // ---- Spells ----
        Supplier<SpellChoice> spellSupplier = SpellData.isCaster(cls)
                ? buildSpellSection(content, cls, oldClassLevel, newClassLevel, infoTitle, infoText)
                : null;

        refreshAll.run();

        JButton back = new JButton("← Back");
        App.styleSecondaryButton(back);
        pad(back);
        back.addActionListener(e -> onBack.run());

        JButton confirm = new JButton("Confirm Level Up");
        App.styleAccentButton(confirm);
        confirm.addActionListener(e -> {
            Integer roll = currentRoll.get();
            // Validation messages show inline in the info panel (no dialog windows; see CLAUDE.md)
            if (roll == null || roll < 0) {
                infoTitle.setText("Hit Points Needed");
                infoText.setText(die > 0
                        ? "Roll your 1d" + die + " hit die, or enter a roll from 1 to " + die + "."
                        : "Enter an HP die result from 1 to " + manualMax + ".");
                return;
            }
            FeatPick pick = null;
            if (feat || epic) {
                pick = pickSupplier[0].get();
                if (pick == null) {
                    infoTitle.setText((epic ? "Epic Boon" : "Feat") + " Needed");
                    infoText.setText("Choose a " + (epic ? "Epic Boon" : "feat") + " for this level.");
                    return;
                }
                if (pick.error != null) {
                    infoTitle.setText("Feat Incomplete");
                    infoText.setText(pick.error);
                    return;
                }
            }
            SpellChoice spells = null;
            if (spellSupplier != null) {
                spells = spellSupplier.get();
                if (spells.error != null) {
                    infoTitle.setText("Spells Incomplete");
                    infoText.setText(spells.error);
                    return;
                }
            }
            applyLevelUp(cls, newClassLevels, newClassLevel, newLevel, roll, pick, spells, subclassPick[0]);
        });

        boolean caster = spellSupplier != null;
        setStep(content, infoPanel(infoTitle, infoText,
                        feat || epic ? "Choose your " + (epic ? "Epic Boon" : "feat") : caster ? "Choose your spells" : "Level " + newLevel,
                        feat || epic ? "Click (i) next to a feat to read what it does."
                                : caster ? "Click a spell to read it here, or Preview to open its full card." : "Roll your hit die, then confirm."),
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

    // ---------------------------------------------------------------
    // Spells gained at this level (2024 PHB progression)
    // ---------------------------------------------------------------
    private static class SpellChoice {
        List<String> cantrips = new ArrayList<>();
        List<String> prepared = new ArrayList<>();
        List<String> spellbook = new ArrayList<>();
        List<String> arcanum = new ArrayList<>();
        String cantripOut, cantripIn, spellOut, spellIn;
        String error;
    }

    private Supplier<SpellChoice> buildSpellSection(JPanel content, String cls, int oldClassLevel, int newClassLevel,
                                                    JLabel infoTitle, JTextArea infoText) {
        Map<String, List<String>> cur = CharacterStorage.recClassSpells(rec).getOrDefault(cls, CharacterStorage.emptyClassSpells());
        Set<String> always = SpellData.alwaysPrepared(rec).keySet();
        boolean wizard = SpellData.isPreparedFromBook(cls);
        int oldMax = SpellData.maxSpellLevel(cls, oldClassLevel);
        int maxLv = SpellData.maxSpellLevel(cls, newClassLevel);

        int cantripTarget = SpellData.cantripsKnown(cls, newClassLevel);
        int cantripNeed = Math.max(0, cantripTarget - cur.get("cantrips").size());
        int preparedNow = 0;
        for (String s : cur.get("prepared")) if (!always.contains(s)) preparedNow++;
        int prepTarget = SpellData.preparedCount(cls, newClassLevel);
        int prepNeed = Math.max(0, prepTarget - preparedNow);
        int bookNeed = wizard ? (newClassLevel == 1 ? 6 : 2) : 0;
        int arcanumLevel = 0;
        if ("Warlock".equals(cls)) for (int[] a : SpellData.MYSTIC_ARCANUM) if (a[0] == newClassLevel) arcanumLevel = a[1];

        add(content, subheading("Spells — " + cls));
        add(content, gap(8));
        if (cantripTarget > 0) add(content, bullet("Cantrips known: " + cur.get("cantrips").size() + " → " + cantripTarget
                + (cantripNeed > 0 ? "   (learn " + cantripNeed + ")" : "")));
        add(content, bullet("Prepared spells: " + preparedNow + " → " + prepTarget + (prepNeed > 0 ? "   (prepare " + prepNeed + " more)" : "")));
        add(content, bullet(maxLv > oldMax
                ? "Spell level unlocked: you can now prepare level " + maxLv + " " + cls + " spells."
                : "Highest spell level: " + maxLv + (maxLv < 9 && !"Warlock".equals(cls) ? nextSpellLevelText(cls, newClassLevel, maxLv) : "")));
        if (wizard) add(content, bullet("Spellbook: add " + bookNeed + " Wizard spells (level " + maxLv + " or lower) for free."));
        if (arcanumLevel > 0) add(content, bullet("Mystic Arcanum: choose one level " + arcanumLevel + " Warlock spell, cast once per Long Rest without a slot."));
        add(content, gap(12));

        Runnable noop = () -> { };
        Set<String> knownCantrips = new HashSet<>(cur.get("cantrips"));
        knownCantrips.addAll(always);
        List<Spell> cantripOptions = filter(SpellData.classSpells(cls, 0, 0), knownCantrips);

        SpellPicker cantripPicker = null;
        if (cantripNeed > 0) {
            add(content, label("Learn " + cantripNeed + " new cantrip" + (cantripNeed == 1 ? "" : "s") + ":"));
            add(content, gap(6));
            cantripPicker = new SpellPicker(cls, cantripOptions, cantripNeed, infoTitle, infoText, noop);
            add(content, cantripPicker.panel);
            add(content, gap(16));
        }

        SpellPicker bookPicker = null;
        if (wizard) {
            add(content, label("Add " + bookNeed + " spells to your spellbook:"));
            add(content, gap(6));
            bookPicker = new SpellPicker(cls, filter(SpellData.classSpells(cls, 1, maxLv), new HashSet<>(cur.get("spellbook"))),
                    bookNeed, infoTitle, infoText, null);
            add(content, bookPicker.panel);
            add(content, gap(16));
        }

        // Prepared-spell picker. For Wizards it draws from the spellbook (including spells added above),
        // so it's rebuilt whenever the spellbook picks change.
        JPanel prepHolder = column();
        SpellPicker[] prepPicker = {null};
        Set<String> notPreparable = new HashSet<>(cur.get("prepared"));
        notPreparable.addAll(always);
        final SpellPicker bookRef = bookPicker;
        Runnable buildPrep = () -> {
            List<String> keep = prepPicker[0] != null ? prepPicker[0].selected() : Collections.emptyList();
            prepHolder.removeAll();
            prepPicker[0] = null;
            if (prepNeed > 0) {
                List<Spell> opts;
                if (wizard) {
                    LinkedHashSet<String> book = new LinkedHashSet<>(cur.get("spellbook"));
                    if (bookRef != null) book.addAll(bookRef.selected());
                    opts = new ArrayList<>();
                    for (String n : book) {
                        Spell s = SpellData.lookup(n);
                        if (s != null && s.level >= 1 && s.level <= maxLv && !notPreparable.contains(n)) opts.add(s);
                    }
                    opts.sort(Comparator.comparingInt((Spell s) -> s.level).thenComparing(s -> s.name));
                } else {
                    opts = filter(SpellData.classSpells(cls, 1, maxLv), notPreparable);
                }
                add(prepHolder, label("Prepare " + prepNeed + " more spell" + (prepNeed == 1 ? "" : "s")
                        + (wizard ? " from your spellbook:" : " (level " + maxLv + " or lower):")));
                add(prepHolder, gap(6));
                if (opts.isEmpty()) {
                    add(prepHolder, muted(wizard ? "Add spells to your spellbook above first." : "No more spells available on the " + cls + " list."));
                } else {
                    prepPicker[0] = new SpellPicker(cls, opts, prepNeed, infoTitle, infoText, noop);
                    prepPicker[0].setSelected(keep);
                    add(prepHolder, prepPicker[0].panel);
                }
                add(prepHolder, gap(16));
            }
            prepHolder.revalidate();
            prepHolder.repaint();
        };
        if (bookPicker != null) bookPicker.onChange = buildPrep;
        buildPrep.run();
        add(content, prepHolder);

        SpellPicker arcanumPicker = null;
        if (arcanumLevel > 0) {
            add(content, label("Mystic Arcanum (level " + arcanumLevel + "):"));
            add(content, gap(6));
            arcanumPicker = new SpellPicker(cls, filter(SpellData.classSpells(cls, arcanumLevel, arcanumLevel), new HashSet<>(cur.get("arcanum"))),
                    1, infoTitle, infoText, noop);
            add(content, arcanumPicker.panel);
            add(content, gap(16));
        }

        // ---- Optional swaps (only when the class already had spells) ----
        SpellPicker cantripOutPicker = null, cantripInPicker = null, spellOutPicker = null, spellInPicker = null;
        if (oldClassLevel >= 1) {
            boolean levelUpSwap = "Bard".equals(cls) || "Sorcerer".equals(cls) || "Warlock".equals(cls);
            if (cantripTarget > 0 && !cur.get("cantrips").isEmpty() && !cantripOptions.isEmpty()) {
                add(content, subheading("Replace a cantrip (optional)"));
                add(content, gap(4));
                add(content, muted("2024 rules: whenever you gain a " + cls + " level, you can replace one cantrip with another from the " + cls + " list."));
                add(content, gap(8));
                add(content, label("Forget:"));
                add(content, gap(4));
                cantripOutPicker = new SpellPicker(cls, toSpells(cur.get("cantrips")), 1, infoTitle, infoText, noop);
                add(content, cantripOutPicker.panel);
                add(content, gap(8));
                add(content, label("Learn instead:"));
                add(content, gap(4));
                cantripInPicker = new SpellPicker(cls, cantripOptions, 1, infoTitle, infoText, noop);
                add(content, cantripInPicker.panel);
                add(content, gap(16));
            }
            List<String> swappable = new ArrayList<>();
            for (String s : cur.get("prepared")) if (!always.contains(s)) swappable.add(s);
            List<Spell> inOptions;
            if (wizard) {
                inOptions = new ArrayList<>();
                for (String n : cur.get("spellbook")) {
                    Spell s = SpellData.lookup(n);
                    if (s != null && s.level >= 1 && s.level <= maxLv && !notPreparable.contains(n)) inOptions.add(s);
                }
            } else {
                inOptions = filter(SpellData.classSpells(cls, 1, maxLv), notPreparable);
            }
            if (!swappable.isEmpty() && !inOptions.isEmpty()) {
                add(content, subheading("Replace a prepared spell (optional)"));
                add(content, gap(4));
                add(content, muted(levelUpSwap
                        ? "2024 rules: whenever you gain a " + cls + " level, you can replace one prepared spell with another " + cls + " spell."
                        : wizard ? "Wizards can change prepared spells (from the spellbook) after any Long Rest; swap one here if you like."
                        : cls + "s can change prepared spells after a Long Rest; swap one here if you like."));
                add(content, gap(8));
                add(content, label("Replace:"));
                add(content, gap(4));
                spellOutPicker = new SpellPicker(cls, toSpells(swappable), 1, infoTitle, infoText, noop);
                add(content, spellOutPicker.panel);
                add(content, gap(8));
                add(content, label("With:"));
                add(content, gap(4));
                spellInPicker = new SpellPicker(cls, inOptions, 1, infoTitle, infoText, noop);
                add(content, spellInPicker.panel);
                add(content, gap(16));
            }
        }

        final SpellPicker cp = cantripPicker, bp = bookPicker, ap = arcanumPicker;
        final SpellPicker cOut = cantripOutPicker, cIn = cantripInPicker, sOut = spellOutPicker, sIn = spellInPicker;
        return () -> {
            SpellChoice ch = new SpellChoice();
            if (cp != null) {
                ch.cantrips = cp.selected();
                int need = Math.min(cantripNeed, cp.optionCount());
                if (ch.cantrips.size() < need) return fail(ch, "Choose " + need + " new cantrip" + (need == 1 ? "" : "s") + " for " + cls + ".");
            }
            if (bp != null) {
                ch.spellbook = bp.selected();
                int need = Math.min(bookNeed, bp.optionCount());
                if (ch.spellbook.size() < need) return fail(ch, "Choose " + need + " spells to add to your spellbook.");
            }
            if (prepPicker[0] != null) {
                ch.prepared = prepPicker[0].selected();
                int need = Math.min(prepNeed, prepPicker[0].optionCount());
                if (ch.prepared.size() < need) return fail(ch, "Choose " + need + " more prepared spell" + (need == 1 ? "" : "s") + ".");
            }
            if (ap != null) {
                ch.arcanum = ap.selected();
                if (ch.arcanum.isEmpty() && ap.optionCount() > 0) return fail(ch, "Choose a Mystic Arcanum spell.");
            }
            if (cOut != null) {
                List<String> o = cOut.selected(), i = cIn.selected();
                if (o.isEmpty() != i.isEmpty()) return fail(ch, "To replace a cantrip, pick both the cantrip to forget and the one to learn (or neither).");
                if (!o.isEmpty()) {
                    if (ch.cantrips.contains(i.get(0))) return fail(ch, i.get(0) + " is already one of your new cantrips. Pick a different replacement.");
                    ch.cantripOut = o.get(0);
                    ch.cantripIn = i.get(0);
                }
            }
            if (sOut != null) {
                List<String> o = sOut.selected(), i = sIn.selected();
                if (o.isEmpty() != i.isEmpty()) return fail(ch, "To replace a spell, pick both the spell to replace and its replacement (or neither).");
                if (!o.isEmpty()) {
                    if (ch.prepared.contains(i.get(0))) return fail(ch, i.get(0) + " is already one of your newly prepared spells. Pick a different replacement.");
                    ch.spellOut = o.get(0);
                    ch.spellIn = i.get(0);
                }
            }
            return ch;
        };
    }

    private static SpellChoice fail(SpellChoice ch, String error) {
        ch.error = error;
        return ch;
    }

    private static List<Spell> filter(List<Spell> spells, Set<String> exclude) {
        List<Spell> out = new ArrayList<>();
        // ...and spells hidden by the content filters (Tags & Filters); owned spells never pass through here
        for (Spell s : spells) if (!exclude.contains(s.name) && Tags.allowed(CustomAssets.SPELLS, s.name)) out.add(s);
        return out;
    }

    // Registry spells for a list of names (unknown names get a minimal placeholder so they can still be picked)
    private static List<Spell> toSpells(List<String> names) {
        List<Spell> out = new ArrayList<>();
        for (String n : names) {
            Spell s = SpellData.lookup(n);
            if (s == null) {
                s = new Spell();
                s.name = n;
            }
            out.add(s);
        }
        return out;
    }

    private static String nextSpellLevelText(String cls, int classLevel, int maxLv) {
        for (int l = classLevel + 1; l <= FeatData.MAX_LEVEL; l++) {
            if (SpellData.maxSpellLevel(cls, l) > maxLv) return "   (level " + (maxLv + 1) + " spells at " + cls + " level " + l + ")";
        }
        return "";
    }

    // Checklist of spells: click a row to select it (and read it in the info panel), or Preview for the full card.
    private static class SpellPicker {
        final JPanel panel = column();
        private final LinkedHashSet<String> chosen = new LinkedHashSet<>();
        private final List<Spell> options;
        private final List<Runnable> visuals = new ArrayList<>();
        private final JLabel counter = new JLabel();
        private final int max;
        Runnable onChange;

        SpellPicker(String cls, List<Spell> options, int max, JLabel infoTitle, JTextArea infoText, Runnable onChange) {
            this.options = options;
            this.max = max;
            this.onChange = onChange;

            if (options.isEmpty()) {
                add(panel, muted("No spells available."));
                return;
            }
            counter.setForeground(App.TEXT_MUTED);
            counter.setFont(new Font("SansSerif", Font.PLAIN, 13));

            JPanel rows = new JPanel();
            rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
            rows.setBackground(App.BG_DARK);
            List<JPanel> rowPanels = new ArrayList<>();

            for (Spell sp : options) {
                JPanel row = new JPanel(new BorderLayout(8, 0));
                row.setBackground(App.BG_DARK);
                row.setBorder(new EmptyBorder(5, 10, 5, 8));
                row.setCursor(new Cursor(Cursor.HAND_CURSOR));

                JLabel glyph = new JLabel("☐");
                glyph.setFont(new Font("SansSerif", Font.PLAIN, 18));
                JLabel name = new JLabel(sp.name);
                name.setFont(new Font("SansSerif", Font.PLAIN, 15));
                String meta = sp.school.isEmpty() ? "" : (sp.level == 0 ? "Cantrip" : "Lv " + sp.level) + " · " + sp.school
                        + (sp.concentration ? " · C" : "") + (sp.ritual ? " · R" : "");
                JLabel metaLbl = new JLabel(meta);
                metaLbl.setForeground(App.TEXT_MUTED);
                metaLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
                JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
                left.setOpaque(false);
                left.add(glyph);
                left.add(name);
                left.add(metaLbl);
                row.add(left, BorderLayout.CENTER);

                JButton preview = new JButton("Preview");
                preview.setFont(new Font("SansSerif", Font.BOLD, 11));
                preview.setForeground(App.BG_DARK);
                preview.setBackground(App.GOLD);
                preview.setOpaque(true);
                preview.setFocusPainted(false);
                preview.setBorder(new EmptyBorder(3, 8, 3, 8));
                preview.setCursor(new Cursor(Cursor.HAND_CURSOR));
                preview.setToolTipText("Open the full spell card for " + sp.name);
                preview.addActionListener(e -> App.showSpellOverlay(preview, SpellData.lookup(sp.name), sp.name, cls + " spell list", false));
                JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
                right.setOpaque(false);
                right.add(preview);
                row.add(right, BorderLayout.EAST);

                Runnable visual = () -> {
                    boolean sel = chosen.contains(sp.name);
                    glyph.setText(sel ? "☑" : "☐");
                    glyph.setForeground(sel ? App.GOLD : App.TEXT_MUTED);
                    name.setForeground(sel ? App.GOLD : App.TEXT_LIGHT);
                    row.setBackground(sel ? new Color(70, 60, 38) : App.BG_DARK);
                };
                visuals.add(visual);
                row.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mouseReleased(java.awt.event.MouseEvent e) {
                        if (!App.isClickRelease(e)) return;
                        if (chosen.contains(sp.name)) {
                            chosen.remove(sp.name);
                        } else {
                            if (max == 1) chosen.clear();
                            if (chosen.size() < max) chosen.add(sp.name);
                        }
                        infoTitle.setText(sp.name);
                        infoText.setText(sp.school.isEmpty() ? "No details in the spell registry." : sp.infoText());
                        infoText.setCaretPosition(0);
                        refresh();
                    }

                    @Override
                    public void mouseEntered(java.awt.event.MouseEvent e) {
                        if (!chosen.contains(sp.name)) row.setBackground(new Color(52, 48, 64));
                    }

                    @Override
                    public void mouseExited(java.awt.event.MouseEvent e) {
                        if (!chosen.contains(sp.name)) row.setBackground(App.BG_DARK);
                    }
                });
                rows.add(row);
                rowPanels.add(row);
            }

            if (options.size() > 10) {
                JTextField search = new JTextField();
                App.styleTextField(search);
                search.setMaximumSize(new Dimension(360, 36));
                search.setToolTipText("Type to filter spells");
                onTextChange(search, () -> {
                    String q = search.getText().trim().toLowerCase();
                    for (int i = 0; i < options.size(); i++) {
                        Spell s = options.get(i);
                        rowPanels.get(i).setVisible(q.isEmpty() || s.name.toLowerCase().contains(q) || s.school.toLowerCase().contains(q));
                    }
                    rows.revalidate();
                    rows.repaint();
                });
                add(panel, search);
                add(panel, gap(6));
            }
            if (max > 1) {
                add(panel, counter);
                add(panel, gap(4));
            }
            JScrollPane scroll = new JScrollPane(rows);
            App.applyDarkScrollBar(scroll);
            scroll.setBorder(new LineBorder(App.BG_FIELD, 1, true));
            int rowH = 36;
            int visible = Math.min(options.size(), 8);
            scroll.setPreferredSize(new Dimension(560, visible * rowH + 6));
            scroll.setMaximumSize(new Dimension(720, visible * rowH + 6));
            add(panel, scroll);
            refresh();
        }

        private void refresh() {
            for (Runnable v : visuals) v.run();
            counter.setText("Selected: " + chosen.size() + " / " + max);
            if (onChange != null) onChange.run();
        }

        List<String> selected() {
            return new ArrayList<>(chosen);
        }

        int optionCount() {
            return options.size();
        }

        void setSelected(List<String> names) {
            chosen.clear();
            for (String n : names) {
                for (Spell s : options) {
                    if (s.name.equals(n) && chosen.size() < max) chosen.add(n);
                }
            }
            for (Runnable v : visuals) v.run();
            counter.setText("Selected: " + chosen.size() + " / " + max);
        }
    }

    private void applyLevelUp(String cls, LinkedHashMap<String, Integer> newClassLevels, int newClassLevel,
                              int newLevel, int roll, FeatPick pick, SpellChoice spells, String subclass) {
        Map<String, Object> out = new LinkedHashMap<>(rec);
        if (CharacterStorage.recStr(out, "characterClass").isEmpty()) out.put("characterClass", cls);
        if (subclass != null) {
            Map<String, Object> subs = new LinkedHashMap<>(CustomAssets.recSubclasses(rec));
            subs.put(cls, subclass);
            out.put("subclasses", subs);
        }

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

        if (spells != null) {
            LinkedHashMap<String, Map<String, List<String>>> all = CharacterStorage.recClassSpells(rec);
            Map<String, List<String>> lists = all.computeIfAbsent(cls, k -> CharacterStorage.emptyClassSpells());
            List<String> cantrips = lists.get("cantrips"), prepared = lists.get("prepared");
            if (spells.cantripOut != null) {
                int i = cantrips.indexOf(spells.cantripOut);
                if (i >= 0) cantrips.set(i, spells.cantripIn);
            }
            if (spells.spellOut != null) {
                int i = prepared.indexOf(spells.spellOut);
                if (i >= 0) prepared.set(i, spells.spellIn);
            }
            addAllNew(cantrips, spells.cantrips);
            addAllNew(lists.get("spellbook"), spells.spellbook);
            addAllNew(prepared, spells.prepared);
            addAllNew(lists.get("arcanum"), spells.arcanum);
            out.put("classSpells", CharacterStorage.classSpellsToJson(all));
        }
        CharacterStorage.refreshDerived(out);

        List<Object> all = CharacterStorage.loadSavedCharacters();
        if (charIndex >= 0 && charIndex < all.size()) {
            all.set(charIndex, out);
            CharacterStorage.saveAllCharacters(all);
        }
        close();
    }

    // ---------------------------------------------------------------
    // Level Down: pick the class that loses a level, review what goes, confirm
    // ---------------------------------------------------------------
    private void openDown(int index) {
        List<Object> all = CharacterStorage.loadSavedCharacters();
        if (index < 0 || index >= all.size() || !(all.get(index) instanceof Map)) return;
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> en : ((Map<?, ?>) all.get(index)).entrySet()) copy.put(String.valueOf(en.getKey()), en.getValue());
        if (CharacterStorage.recLevel(copy) <= 1) return;
        rec = copy;
        charIndex = index;
        showDownStep();
        outerLayout.show(outerCards, "LEVEL_UP");
    }

    private void showDownStep() {
        int level = CharacterStorage.recLevel(rec);
        LinkedHashMap<String, Integer> classLevels = CharacterStorage.recClassLevels(rec);
        String primary = CharacterStorage.recStr(rec, "characterClass");

        JPanel content = column();
        add(content, heading(displayName() + ": Level " + level + " → " + (level - 1)));
        add(content, gap(6));
        add(content, muted("Classes: " + classSummary(classLevels) + "     •     Hit Dice: " + FeatData.hitDiceString(classLevels)));
        add(content, gap(28));
        add(content, subheading("Which class loses a level?"));
        add(content, gap(12));
        for (Map.Entry<String, Integer> en : classLevels.entrySet()) {
            String cls = en.getKey();
            int lv = en.getValue();
            boolean blocked = lv <= 1 && (cls.equals(primary) || classLevels.size() == 1);
            JButton b = new JButton("Level down " + cls + "   (" + lv + " → " + (lv - 1) + ")");
            App.styleAccentButton(b);
            b.setFont(new Font("SansSerif", Font.BOLD, 16));
            b.setEnabled(!blocked);
            if (blocked) b.setToolTipText("Your starting class can't drop below level 1");
            b.addActionListener(e -> showDownConfirm(cls));
            add(content, b);
            add(content, gap(10));
        }
        setStep(content, null, cancelButton());
    }

    private void showDownConfirm(String cls) {
        int level = CharacterStorage.recLevel(rec);
        int cl = CharacterStorage.recClassLevels(rec).getOrDefault(cls, 1);
        JPanel content = column();
        add(content, heading("Level down " + cls + " (" + cl + " → " + (cl - 1) + ")"));
        add(content, gap(14));
        add(content, subheading("This removes:"));
        add(content, gap(8));
        List<Integer> rolls = CharacterStorage.recHpRolls(rec);
        if (!rolls.isEmpty()) add(content, bullet("Your last hit point roll (" + rolls.get(rolls.size() - 1) + ")"));
        for (Map<String, Object> f : levelFeats(rec, cls, cl, level)) {
            String inc = describeIncreases(CharacterStorage.featStatIncreases(f));
            add(content, bullet("Feat: " + CharacterStorage.recStr(f, "name") + (inc.isEmpty() ? "" : " (" + inc + " undone)")));
        }
        if (cl - 1 <= 0) {
            add(content, bullet("The " + cls + " class, with its spells and subclass"));
        } else {
            Object sub = CustomAssets.recSubclasses(rec).get(cls);
            Map<String, Object> subAsset = sub == null ? null : CustomAssets.subclassAsset(cls, String.valueOf(sub));
            if (subAsset != null && CustomAssets.intVal(subAsset, "subclassLevel", 3) > cl - 1) add(content, bullet("Subclass: " + sub));
            if (SpellData.isCaster(cls)) add(content, bullet("Spells beyond what " + cls + " level " + (cl - 1) + " allows (the most recently added go first)"));
        }
        JButton back = new JButton("← Back");
        App.styleSecondaryButton(back);
        pad(back);
        back.addActionListener(e -> showDownStep());
        JButton confirm = new JButton("Confirm Level Down");
        App.styleAccentButton(confirm);
        confirm.addActionListener(e -> applyLevelDown(cls));
        setStep(content, null, back, cancelButton(), confirm);
    }

    // Feats gained at this level: matched by character level, falling back to class + class level for old records
    private static List<Map<String, Object>> levelFeats(Map<String, Object> rec, String cls, int classLevel, int level) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> f : CharacterStorage.recFeats(rec)) {
            Integer charLv = CharacterStorage.recInt(f, "characterLevel");
            Integer clLv = CharacterStorage.recInt(f, "classLevel");
            boolean match = charLv != null ? charLv == level
                    : cls.equals(CharacterStorage.recStr(f, "className")) && clLv != null && clLv == classLevel;
            if (match) out.add(f);
        }
        return out;
    }

    private void applyLevelDown(String cls) {
        Map<String, Object> out = new LinkedHashMap<>(rec);
        int level = CharacterStorage.recLevel(rec);
        LinkedHashMap<String, Integer> classLevels = CharacterStorage.recClassLevels(rec);
        int cl = classLevels.getOrDefault(cls, 1), newCl = cl - 1;

        Map<String, Object> clOut = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> en : classLevels.entrySet()) {
            int v = en.getKey().equals(cls) ? newCl : en.getValue();
            if (v > 0) clOut.put(en.getKey(), v);
        }
        out.put("classLevels", clOut);

        List<Object> rolls = new ArrayList<>(CharacterStorage.recHpRolls(rec));
        if (!rolls.isEmpty()) rolls.remove(rolls.size() - 1);
        out.put("hpRolls", rolls);

        List<Map<String, Object>> gone = levelFeats(rec, cls, cl, level);
        List<Object> feats = new ArrayList<>();
        Map<String, Integer> stats = CharacterStorage.recStatsMap(rec);
        for (Map<String, Object> f : CharacterStorage.recFeats(rec)) {
            if (!gone.contains(f)) {
                feats.add(f);
                continue;
            }
            for (Map.Entry<String, Integer> en : CharacterStorage.featStatIncreases(f).entrySet()) stats.merge(en.getKey(), -en.getValue(), Integer::sum);
        }
        out.put("feats", feats);
        Map<String, Object> statsOut = new LinkedHashMap<>();
        for (String s : GameData.STAT_ORDER) if (stats.containsKey(s)) statsOut.put(s, stats.get(s));
        out.put("stats", statsOut);

        Map<String, Object> subs = new LinkedHashMap<>(CustomAssets.recSubclasses(rec));
        Object sub = subs.get(cls);
        Map<String, Object> subAsset = sub == null ? null : CustomAssets.subclassAsset(cls, String.valueOf(sub));
        if (newCl <= 0 || (subAsset != null && CustomAssets.intVal(subAsset, "subclassLevel", 3) > newCl)) subs.remove(cls);
        out.put("subclasses", subs);

        LinkedHashMap<String, Map<String, List<String>>> spells = CharacterStorage.recClassSpells(rec);
        if (newCl <= 0) {
            spells.remove(cls);
        } else if (spells.containsKey(cls)) {
            Map<String, List<String>> lists = spells.get(cls);
            trimNewest(lists.get("cantrips"), SpellData.cantripsKnown(cls, cl), SpellData.cantripsKnown(cls, newCl));
            trimNewest(lists.get("prepared"), SpellData.preparedCount(cls, cl), SpellData.preparedCount(cls, newCl));
            if (SpellData.isPreparedFromBook(cls)) trimNewest(lists.get("spellbook"), 6 + 2 * (cl - 1), 6 + 2 * (newCl - 1));
            if ("Warlock".equals(cls)) trimNewest(lists.get("arcanum"), arcanumCount(cl), arcanumCount(newCl));
        }
        out.put("classSpells", CharacterStorage.classSpellsToJson(spells));

        CharacterStorage.refreshDerived(out);
        List<Object> all = CharacterStorage.loadSavedCharacters();
        if (charIndex >= 0 && charIndex < all.size()) {
            all.set(charIndex, out);
            CharacterStorage.saveAllCharacters(all);
        }
        close();
    }

    // Drops the most recently added entries: as many as the level gave, but never below the new level's allowance
    private static void trimNewest(List<String> list, int oldMax, int newMax) {
        int drop = Math.min(list.size() - newMax, Math.max(0, oldMax - newMax));
        for (int i = 0; i < drop; i++) list.remove(list.size() - 1);
    }

    private static int arcanumCount(int warlockLevel) {
        int n = 0;
        for (int l : new int[]{11, 13, 15, 17}) if (warlockLevel >= l) n++;
        return n;
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------
    private static void addAllNew(List<String> target, List<String> items) {
        for (String s : items) if (!target.contains(s)) target.add(s);
    }

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
        for (String c : FeatData.multiclassOptions()) {
            if (!classLevels.containsKey(c) && Tags.allowed(CustomAssets.CLASSES, c)) out.add(c);
        }
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

    // A homebrew feature line: click it to read the full text in the info panel
    private static JLabel featureBullet(Map<String, Object> f, String source, JLabel infoTitle, JTextArea infoText) {
        String name = CustomAssets.str(f, "name");
        JLabel l = bullet("Feature: " + name + "   (" + source + ")");
        l.setForeground(App.GOLD);
        l.setToolTipText("Click to read this feature");
        App.clickable(l, () -> {
            infoTitle.setText(name);
            String d = CustomAssets.str(f, "description");
            List<String> mech = ClassFeatures.mechanics(ClassFeatures.parse(f), null, CustomAssets.intVal(f, "level", 1), null, "");
            infoText.setText(source + "\n\n" + (d.isEmpty() ? "No description." : d)
                    + (mech.isEmpty() ? "" : "\n\n• " + String.join("\n• ", mech))
                    + "\n\nUses, choices (like Expertise) and details are on the character sheet's Features & Traits.");
        });
        return l;
    }

    private static JLabel bullet(String text) {
        JLabel l = label("•  " + text);
        l.setBorder(new EmptyBorder(3, 4, 3, 0));
        return l;
    }
}
