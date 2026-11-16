import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;

public class App {

    // Shared color palette
    static final Color BG_DARK = new Color(30, 27, 38);
    static final Color BG_PANEL = new Color(40, 36, 51);
    static final Color BG_FIELD = new Color(50, 46, 61);
    static final Color ACCENT = new Color(198, 40, 40);   // deep red
    static final Color ACCENT_HOVER = new Color(220, 60, 60);
    static final Color GOLD = new Color(212, 175, 55);
    static final Color TEXT_LIGHT = new Color(235, 230, 220);
    static final Color TEXT_MUTED = new Color(170, 165, 160);

    public static void main(String[] args) throws Exception {
        SpellData.SPELL_DB = SpellData.loadSpellDatabase();
        WeaponData.WEAPON_DB = WeaponData.loadWeaponDatabase();
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("D&D Engine");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            frame.setSize(900, 600);
            frame.getContentPane().setBackground(BG_DARK);

            CardLayout cardLayout = new CardLayout();
            JPanel cards = new JPanel(cardLayout);
            cards.setBackground(BG_DARK);

            cards.add(buildMainMenu(cardLayout, cards), "MENU");
            cards.add(buildCombatPanel(cardLayout, cards), "COMBAT");
            cards.add(buildAssetsPanel(cardLayout, cards), "ASSETS");

            WizardResult wizardResult = buildNewPCWizardPanel(cardLayout, cards);
            cards.add(wizardResult.panel, "NEW_PC_WIZARD");

            CharacterDetailResult detailResult = buildCharacterDetailPanel(cardLayout, cards);
            cards.add(detailResult.panel, "CHARACTER_DETAIL");

            LevelUpPanel levelUpPanel = new LevelUpPanel(cardLayout, cards);
            cards.add(levelUpPanel.panel, "LEVEL_UP");

            cards.add(buildCharactersPanel(cardLayout, cards, wizardResult.resetForEdit, detailResult.show, levelUpPanel.show), "CHARACTERS");
            cards.add(buildAddCharacterTypePanel(cardLayout, cards, wizardResult.reset), "ADD_CHAR_TYPE");
            cards.add(buildNewNPCPanel(cardLayout, cards), "NEW_NPC");

            frame.add(cards);
            cardLayout.show(cards, "MENU");

            frame.setVisible(true);
        });
    }

    // ---------------------------------------------------------------
    // MAIN MENU
    // ---------------------------------------------------------------
    private static JPanel buildMainMenu(CardLayout cardLayout, JPanel cards) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(BG_DARK);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;

        JLabel titleLabel = new JLabel("D&D Engine", JLabel.CENTER);
        titleLabel.setForeground(GOLD);
        titleLabel.setFont(new Font("Serif", Font.BOLD, 48));

        JLabel subtitleLabel = new JLabel("Choose a section to begin", JLabel.CENTER);
        subtitleLabel.setForeground(TEXT_MUTED);
        subtitleLabel.setFont(new Font("SansSerif", Font.PLAIN, 16));

        JPanel titleBox = new JPanel();
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.setBackground(BG_DARK);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        titleBox.add(titleLabel);
        titleBox.add(Box.createRigidArea(new Dimension(0, 10)));
        titleBox.add(subtitleLabel);

        gbc.gridy = 0;
        gbc.weighty = 0.4;
        gbc.anchor = GridBagConstraints.SOUTH;
        gbc.insets = new Insets(50, 0, 0, 0);
        panel.add(titleBox, gbc);

        JButton btnCharacters = makeMenuButton("Characters");
        JButton btnCombat = makeMenuButton("Combat");
        JButton btnAssets = makeMenuButton("Assets");

        btnCharacters.addActionListener(e -> cardLayout.show(cards, "CHARACTERS"));
        btnCombat.addActionListener(e -> cardLayout.show(cards, "COMBAT"));
        btnAssets.addActionListener(e -> cardLayout.show(cards, "ASSETS"));

        JPanel buttonPanel = new JPanel(new GridBagLayout());
        buttonPanel.setBackground(BG_DARK);
        GridBagConstraints pGbc = new GridBagConstraints();
        pGbc.gridy = 0;
        pGbc.insets = new Insets(0, 15, 0, 15);

        pGbc.gridx = 0; buttonPanel.add(btnCharacters, pGbc);
        pGbc.gridx = 1; buttonPanel.add(btnCombat, pGbc);
        pGbc.gridx = 2; buttonPanel.add(btnAssets, pGbc);

        gbc.gridy = 1;
        gbc.weighty = 0.6;
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.insets = new Insets(40, 0, 0, 0);
        panel.add(buttonPanel, gbc);

        panel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                double width = panel.getWidth();
                double height = panel.getHeight();

                int titleFontSize = (int) Math.max(28, Math.min(width * 0.05, height * 0.08));
                int subtitleFontSize = (int) Math.max(12, Math.min(width * 0.015, height * 0.025));
                int buttonFontSize = (int) Math.max(14, Math.min(width * 0.02, height * 0.04));
                int btnWidth = (int) (width * 0.18);
                int btnHeight = (int) (height * 0.08);

                titleLabel.setFont(new Font("Serif", Font.BOLD, titleFontSize));
                subtitleLabel.setFont(new Font("SansSerif", Font.PLAIN, subtitleFontSize));

                Font btnFont = new Font("SansSerif", Font.BOLD, buttonFontSize);
                Dimension btnSize = new Dimension(btnWidth, btnHeight);

                for (JButton btn : new JButton[]{btnCharacters, btnCombat, btnAssets}) {
                    btn.setFont(btnFont);
                    btn.setPreferredSize(btnSize);
                    btn.setMinimumSize(btnSize);
                }

                panel.revalidate();
            }
        });

        return panel;
    }

    private static JButton makeMenuButton(String text) {
        JButton btn = new JButton(text);
        styleAccentButton(btn);
        btn.setPreferredSize(new Dimension(160, 50));
        return btn;
    }

    static void styleAccentButton(JButton btn) {
        btn.setForeground(TEXT_LIGHT);
        btn.setBackground(ACCENT);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setOpaque(true);
        btn.setBorderPainted(false);
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(ACCENT_HOVER);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                btn.setBackground(ACCENT);
            }
        });
    }

    static void styleSecondaryButton(JButton btn) {
        btn.setForeground(TEXT_LIGHT);
        btn.setBackground(BG_FIELD);
        btn.setFocusPainted(false);
        btn.setBorder(new LineBorder(TEXT_MUTED, 1, true));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setOpaque(true);
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(BG_PANEL);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                btn.setBackground(BG_FIELD);
            }
        });
    }

    static void styleTextField(JTextField tf) {
        tf.setFont(new Font("SansSerif", Font.PLAIN, 16));
        tf.setBackground(BG_FIELD);
        tf.setForeground(TEXT_LIGHT);
        tf.setCaretColor(TEXT_LIGHT);
        tf.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(TEXT_MUTED, 1, true),
                new EmptyBorder(8, 10, 8, 10)));
    }

    private static void styleSpinner(JSpinner sp) {
        sp.setFont(new Font("SansSerif", Font.PLAIN, 15));
        sp.setPreferredSize(new Dimension(65, 28));
        JComponent editor = sp.getEditor();
        if (editor instanceof JSpinner.DefaultEditor) {
            JFormattedTextField tf = ((JSpinner.DefaultEditor) editor).getTextField();
            tf.setBackground(BG_FIELD);
            tf.setForeground(TEXT_LIGHT);
            tf.setCaretColor(TEXT_LIGHT);
        }
    }

    static void styleInfoTextArea(JTextArea ta) {
        ta.setEditable(false);
        ta.setOpaque(false);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        ta.setForeground(TEXT_LIGHT);
        ta.setFont(new Font("SansSerif", Font.PLAIN, 15));
        ta.setBorder(new EmptyBorder(15, 0, 0, 0));
    }

    private static void styleCheckBox(JCheckBox cb) {
        cb.setForeground(TEXT_LIGHT);
        cb.setBackground(BG_DARK);
        cb.setFont(new Font("SansSerif", Font.PLAIN, 15));
        cb.setFocusPainted(false);
    }

    private static JButton makeInfoButton(String title, JLabel infoTitleLabel, JTextArea infoTextArea, String description) {
        JButton infoBtn = new JButton("i");
        infoBtn.setToolTipText("Show details for " + title);
        infoBtn.setFont(new Font("Serif", Font.ITALIC | Font.BOLD, 14));
        infoBtn.setForeground(BG_DARK);
        infoBtn.setBackground(GOLD);
        infoBtn.setFocusPainted(false);
        infoBtn.setBorder(BorderFactory.createEmptyBorder());
        infoBtn.setPreferredSize(new Dimension(24, 24));
        infoBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        infoBtn.addActionListener(e -> {
            infoTitleLabel.setText(title);
            infoTextArea.setText(description);
        });
        return infoBtn;
    }

    // Slim dark-themed scrollbar so scroll areas match the rest of the UI
    static class DarkScrollBarUI extends javax.swing.plaf.basic.BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            this.thumbColor = new Color(95, 88, 110);
            this.trackColor = BG_DARK;
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return zeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return zeroButton();
        }

        private JButton zeroButton() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            return b;
        }
    }

    static void applyDarkScrollBar(JScrollPane sp) {
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(BG_DARK);
        sp.setBackground(BG_DARK);
        sp.getVerticalScrollBar().setUI(new DarkScrollBarUI());
        sp.getVerticalScrollBar().setBackground(BG_DARK);
        sp.getVerticalScrollBar().setUnitIncrement(18);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(true);
    }

    // Result of buildCheckListPanel: the component to add, a way to read the current
    // selections, a reset() to clear it, and a setEnabled() to gray it out (e.g. while skipped).
    static class CheckListResult {
        JPanel component;
        Supplier<List<String>> getSelected;
        Runnable reset;
        java.util.function.Consumer<Boolean> setEnabled;
        java.util.function.Consumer<List<String>> setSelected;
    }

    /**
     * Builds a scrollable, checkbox-style vertical list. Clicking anywhere on a row toggles it.
     * maxSelect == 1 behaves like a radio group (selecting one clears any other selection).
     * infoMap (nullable) adds a gold (i) button per row wired to infoTitle/infoText.
     * withSearch adds a live filter field above the list.
     */
    static CheckListResult buildCheckListPanel(
            List<String> options,
            int maxSelect,
            Map<String, String> infoMap,
            JLabel infoTitle,
            JTextArea infoText,
            boolean withSearch,
            int viewportRows,
            java.util.function.Consumer<List<String>> onChange) {

        JPanel wrap = new JPanel();
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
        wrap.setBackground(BG_DARK);
        wrap.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextField searchField = null;
        if (withSearch) {
            searchField = new JTextField();
            styleTextField(searchField);
            searchField.setAlignmentX(Component.LEFT_ALIGNMENT);
            searchField.setMaximumSize(new Dimension(360, 36));
            searchField.setToolTipText("Type to filter this list");
            wrap.add(searchField);
            wrap.add(Box.createRigidArea(new Dimension(0, 8)));
        }
        final JTextField searchFieldRef = searchField;

        JLabel counterLabel = new JLabel();
        counterLabel.setForeground(TEXT_MUTED);
        counterLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        counterLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (maxSelect > 1) {
            wrap.add(counterLabel);
            wrap.add(Box.createRigidArea(new Dimension(0, 6)));
        }

        JPanel rowsPanel = new JPanel();
        rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
        rowsPanel.setBackground(BG_DARK);

        JScrollPane scroll = new JScrollPane(rowsPanel);
        applyDarkScrollBar(scroll);
        scroll.setBorder(new LineBorder(BG_FIELD, 1, true));
        int rowH = 40;
        int visible = Math.max(1, Math.min(options.size(), viewportRows));
        scroll.setPreferredSize(new Dimension(380, visible * rowH + 6));
        scroll.setMaximumSize(new Dimension(600, viewportRows * rowH + 6));
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrap.add(scroll);

        LinkedHashSet<String> selected = new LinkedHashSet<>();
        boolean[] enabledFlag = {true};
        List<JPanel> rowPanels = new ArrayList<>();
        List<JLabel> glyphLabels = new ArrayList<>();
        List<JLabel> textLabels = new ArrayList<>();
        List<Runnable> visualUpdaters = new ArrayList<>();
        Runnable[] refreshHolder = new Runnable[1];

        for (String option : options) {
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setBackground(BG_DARK);
            row.setBorder(new EmptyBorder(7, 10, 7, 10));
            row.setCursor(new Cursor(Cursor.HAND_CURSOR));

            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            left.setOpaque(false);
            JLabel glyph = new JLabel("\u2610");
            glyph.setForeground(TEXT_MUTED);
            glyph.setFont(new Font("SansSerif", Font.PLAIN, 18));
            JLabel textLbl = new JLabel(option);
            textLbl.setForeground(TEXT_LIGHT);
            textLbl.setFont(new Font("SansSerif", Font.PLAIN, 15));
            left.add(glyph);
            left.add(textLbl);
            row.add(left, BorderLayout.CENTER);

            if (infoMap != null) {
                JButton infoBtn = makeInfoButton(option, infoTitle, infoText, infoMap.getOrDefault(option, ""));
                infoBtn.setPreferredSize(new Dimension(24, 24));
                JPanel infoWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
                infoWrap.setOpaque(false);
                infoWrap.add(infoBtn);
                row.add(infoWrap, BorderLayout.EAST);
            }

            Runnable applyVisual = () -> {
                boolean sel = selected.contains(option);
                glyph.setText(sel ? "\u2611" : "\u2610");
                glyph.setForeground(sel ? GOLD : TEXT_MUTED);
                textLbl.setForeground(sel ? GOLD : TEXT_LIGHT);
                row.setBackground(sel ? new Color(70, 60, 38) : BG_DARK);
            };
            visualUpdaters.add(applyVisual);

            row.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (!enabledFlag[0]) return;
                    if (selected.contains(option)) {
                        selected.remove(option);
                    } else {
                        if (maxSelect == 1) selected.clear();
                        if (selected.size() < maxSelect) selected.add(option);
                    }
                    if (refreshHolder[0] != null) refreshHolder[0].run();
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    if (!enabledFlag[0] || selected.contains(option)) return;
                    row.setBackground(new Color(52, 48, 64));
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (!enabledFlag[0] || selected.contains(option)) return;
                    row.setBackground(BG_DARK);
                }
            });

            rowsPanel.add(row);
            rowPanels.add(row);
            glyphLabels.add(glyph);
            textLabels.add(textLbl);
        }

        refreshHolder[0] = () -> {
            for (Runnable u : visualUpdaters) u.run();
            counterLabel.setText("Selected: " + selected.size() + " / " + maxSelect);
            if (onChange != null) onChange.accept(new ArrayList<>(selected));
        };
        refreshHolder[0].run();

        if (searchFieldRef != null) {
            searchFieldRef.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                void filter() {
                    String q = searchFieldRef.getText().trim().toLowerCase();
                    for (int i = 0; i < options.size(); i++) {
                        rowPanels.get(i).setVisible(q.isEmpty() || options.get(i).toLowerCase().contains(q));
                    }
                    rowsPanel.revalidate();
                    rowsPanel.repaint();
                }

                public void insertUpdate(javax.swing.event.DocumentEvent e) { filter(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { filter(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { filter(); }
            });
        }

        CheckListResult result = new CheckListResult();
        result.component = wrap;
        result.getSelected = () -> new ArrayList<>(selected);
        result.reset = () -> {
            selected.clear();
            if (searchFieldRef != null) searchFieldRef.setText("");
            refreshHolder[0].run();
        };
        result.setEnabled = (en) -> {
            enabledFlag[0] = en;
            for (JPanel row : rowPanels) row.setCursor(new Cursor(en ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            if (en) {
                refreshHolder[0].run();
            } else {
                for (int i = 0; i < rowPanels.size(); i++) {
                    glyphLabels.get(i).setForeground(new Color(90, 85, 95));
                    textLabels.get(i).setForeground(new Color(110, 105, 115));
                    rowPanels.get(i).setBackground(BG_DARK);
                }
            }
        };
        result.setSelected = (vals) -> {
            selected.clear();
            if (vals != null) {
                for (String v : vals) {
                    if (options.contains(v) && selected.size() < maxSelect) selected.add(v);
                }
            }
            refreshHolder[0].run();
        };
        return result;
    }

    private static int roll4d6DropLowest(Random rnd) {
        int[] dice = new int[4];
        for (int i = 0; i < 4; i++) dice[i] = rnd.nextInt(6) + 1;
        Arrays.sort(dice);
        return dice[1] + dice[2] + dice[3];
    }

    // Builds a Back / Cancel / Save Draft top bar used across wizard steps.
    // editModeHolder[0]==true means we're editing an existing character: Cancel asks for
    // confirmation before leaving (Back just navigates), and the save button is collected
    // so its label can say "Save Changes" instead of "Save Draft".
    private static JPanel buildWizardTopBar(Runnable onBack, CardLayout outerCardLayout, JPanel outerCards,
                                             boolean[] editModeHolder, List<JButton> saveButtonsOut) {
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 15));
        topBar.setBackground(BG_PANEL);

        JButton backButton = new JButton("\u2190 Back");
        styleSecondaryButton(backButton);
        backButton.addActionListener(e -> onBack.run());

        JButton cancelButton = new JButton("Cancel");
        styleSecondaryButton(cancelButton);
        cancelButton.addActionListener(e -> {
            if (editModeHolder[0]) {
                int confirm = JOptionPane.showConfirmDialog(topBar, "Leave without saving changes?",
                        "Unsaved Changes", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (confirm == JOptionPane.YES_OPTION) outerCardLayout.show(outerCards, "CHARACTERS");
            } else {
                outerCardLayout.show(outerCards, "CHARACTERS");
            }
        });

        JButton saveDraftButton = new JButton(editModeHolder[0] ? "Save Changes" : "Save Draft");
        styleSecondaryButton(saveDraftButton);
        if (saveButtonsOut != null) saveButtonsOut.add(saveDraftButton);

        topBar.add(backButton);
        topBar.add(cancelButton);
        topBar.add(saveDraftButton);
        return topBar;
    }

    // Builds N distinct-selection stat dropdowns for the ability score increase step.
    private static JPanel buildIncreasePickerPanel(String mode, String[] availableStats, List<JComboBox<String>> combosOut) {
        combosOut.clear();
        int comboCount = mode.equals("PLUS2PLUS1") ? 2 : 3;

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG_DARK);

        List<JComboBox<String>> combos = new ArrayList<>();
        for (int i = 0; i < comboCount; i++) {
            JComboBox<String> combo = new JComboBox<>(availableStats);
            combo.setSelectedIndex(Math.min(i, availableStats.length - 1));
            combos.add(combo);
        }

        boolean[] updating = {false};
        Runnable refresh = () -> {
            if (updating[0]) return;
            updating[0] = true;
            String[] current = new String[combos.size()];
            for (int i = 0; i < combos.size(); i++) current[i] = (String) combos.get(i).getSelectedItem();
            for (int i = 0; i < combos.size(); i++) {
                List<String> options = new ArrayList<>(Arrays.asList(availableStats));
                for (int j = 0; j < combos.size(); j++) {
                    if (j != i && current[j] != null) options.remove(current[j]);
                }
                String keep = current[i];
                if (keep != null && !options.contains(keep)) options.add(0, keep);
                combos.get(i).setModel(new DefaultComboBoxModel<>(options.toArray(new String[0])));
                if (keep != null) combos.get(i).setSelectedItem(keep);
            }
            updating[0] = false;
        };

        for (JComboBox<String> combo : combos) {
            styleCombo(combo);
            combo.addItemListener(e -> {
                if (e.getStateChange() == ItemEvent.SELECTED) refresh.run();
            });
        }
        refresh.run();

        String[] labels = mode.equals("PLUS2PLUS1")
                ? new String[]{"+2 to:", "+1 to:"}
                : new String[]{"+1 to:", "+1 to:", "+1 to:"};

        for (int i = 0; i < combos.size(); i++) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
            row.setBackground(BG_DARK);
            JLabel lbl = new JLabel(labels[i]);
            lbl.setForeground(TEXT_LIGHT);
            lbl.setFont(new Font("SansSerif", Font.BOLD, 15));
            row.add(lbl);
            row.add(combos.get(i));
            panel.add(row);
        }

        combosOut.addAll(combos);
        return panel;
    }

    static void styleCombo(JComboBox<String> combo) {
        combo.setFont(new Font("SansSerif", Font.PLAIN, 15));
        combo.setBackground(BG_FIELD);
        combo.setForeground(TEXT_LIGHT);
    }

    // ---------------------------------------------------------------
    // Minimal JSON writer (mirrors what MiniJsonParser reads) + saved character storage
    // ---------------------------------------------------------------
    // Renders one decision block (skills / fixed choice / spell checklist / free text entries).
    // Adds a RenderedItem to renderedOut so its selections can be read and validated later.
    private static JPanel buildDecisionItemUI(DecisionItem item, String className, JLabel infoTitle, JTextArea infoText, List<RenderedItem> renderedOut) {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBackground(BG_DARK);
        section.setBorder(new EmptyBorder(8, 0, 16, 0));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel sectionLabel = new JLabel(item.label);
        sectionLabel.setForeground(GOLD);
        sectionLabel.setFont(new Font("SansSerif", Font.BOLD, 17));
        sectionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(sectionLabel);
        section.add(Box.createRigidArea(new Dimension(0, 6)));

        if ("TEXT".equals(item.type)) {
            JTextField[] fields = new JTextField[item.count];
            for (int i = 0; i < item.count; i++) {
                JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
                row.setBackground(BG_DARK);
                row.setAlignmentX(Component.LEFT_ALIGNMENT);
                JLabel numLbl = new JLabel((i + 1) + ".");
                numLbl.setForeground(TEXT_LIGHT);
                JTextField tf = new JTextField(22);
                styleTextField(tf);
                row.add(numLbl);
                row.add(tf);
                section.add(row);
                fields[i] = tf;
            }
            RenderedItem textRi = new RenderedItem(item, () -> {
                List<String> vals = new ArrayList<>();
                for (JTextField f : fields) {
                    String v = f.getText().trim();
                    if (!v.isEmpty()) vals.add(v);
                }
                return vals;
            });
            textRi.setSelections = (vals) -> {
                for (int i = 0; i < fields.length; i++) {
                    fields[i].setText(vals != null && i < vals.size() ? vals.get(i) : "");
                }
            };
            renderedOut.add(textRi);
        } else {
            int maxCount = item.count;
            List<String> optionLabels;
            Map<String, String> optionInfo;
            boolean withInfo;

            if ("SKILLS".equals(item.type)) {
                optionLabels = Arrays.asList(item.options);
                optionInfo = GameData.SKILL_INFO;
                withInfo = true;
            } else if ("CHOICE".equals(item.type)) {
                optionLabels = Arrays.asList(item.options);
                optionInfo = Collections.emptyMap();
                withInfo = false;
            } else {
                List<SpellEntry> pool = "CANTRIPS".equals(item.type) ? SpellData.SPELL_DB.cantrips : SpellData.SPELL_DB.level1;
                List<String> names = new ArrayList<>();
                Map<String, String> infoMap = new LinkedHashMap<>();
                for (SpellEntry se : pool) {
                    if (se.classes.contains(className)) {
                        names.add(se.name);
                        infoMap.put(se.name, "School: " + se.school
                                + "\nLevel: " + se.level
                                + "\nCasting Time: " + se.castingTime
                                + "\nRange: " + SpellData.formatSpellRange(se.range)
                                + "\nComponents: " + String.join(", ", se.components)
                                + "\nDuration: " + SpellData.formatSpellDuration(se.duration, se.concentration));
                    }
                }
                optionLabels = names;
                optionInfo = infoMap;
                withInfo = true;
            }

            if (optionLabels.isEmpty()) {
                JLabel noneLbl = new JLabel("No options available.");
                noneLbl.setForeground(TEXT_MUTED);
                noneLbl.setFont(new Font("SansSerif", Font.PLAIN, 14));
                noneLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
                section.add(noneLbl);
                renderedOut.add(new RenderedItem(item, ArrayList::new));
                return section;
            }

            CheckListResult clr = buildCheckListPanel(
                    optionLabels, maxCount, withInfo ? optionInfo : null, infoTitle, infoText,
                    optionLabels.size() > 8, Math.min(6, Math.max(2, optionLabels.size())), null);
            clr.component.setAlignmentX(Component.LEFT_ALIGNMENT);
            section.add(clr.component);
            RenderedItem ri = new RenderedItem(item, clr.getSelected);
            ri.setSelections = clr.setSelected;
            renderedOut.add(ri);
        }

        return section;
    }

    // ---------------------------------------------------------------
    // SECTION HEADER (back arrow + title), shared by sub-panels
    // ---------------------------------------------------------------
    private static JPanel buildHeader(String title, Runnable onBack) {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_PANEL);
        header.setBorder(new EmptyBorder(15, 20, 15, 20));

        JButton backButton = new JButton("\u2190 Back");
        backButton.setForeground(TEXT_LIGHT);
        backButton.setBackground(BG_PANEL);
        backButton.setFocusPainted(false);
        backButton.setBorderPainted(false);
        backButton.setContentAreaFilled(false);
        backButton.setFont(new Font("SansSerif", Font.BOLD, 16));
        backButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        backButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                backButton.setForeground(GOLD);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                backButton.setForeground(TEXT_LIGHT);
            }
        });
        backButton.addActionListener(e -> onBack.run());

        JLabel titleLabel = new JLabel(title, JLabel.CENTER);
        titleLabel.setForeground(GOLD);
        titleLabel.setFont(new Font("Serif", Font.BOLD, 28));

        header.add(backButton, BorderLayout.WEST);
        header.add(titleLabel, BorderLayout.CENTER);
        JLabel spacer = new JLabel("        ");
        header.add(spacer, BorderLayout.EAST);

        return header;
    }

    static JPanel buildSectionShell(String title, CardLayout cardLayout, JPanel cards, String backTarget) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_DARK);
        panel.add(buildHeader(title, () -> cardLayout.show(cards, backTarget)), BorderLayout.NORTH);
        return panel;
    }

    // ---------------------------------------------------------------
    // CHARACTERS PANEL
    // ---------------------------------------------------------------
    private static JPanel buildCharactersPanel(CardLayout cardLayout, JPanel cards, java.util.function.IntConsumer resetForEdit,
                                               java.util.function.IntConsumer showDetail, java.util.function.IntConsumer showLevelUp) {
        JPanel panel = buildSectionShell("Characters", cardLayout, cards, "MENU");

        Runnable[] rebuildCharacterListHolder = new Runnable[1];

        JPanel listWrap = new JPanel();
        listWrap.setLayout(new BoxLayout(listWrap, BoxLayout.Y_AXIS));
        listWrap.setBackground(BG_DARK);
        listWrap.setBorder(new EmptyBorder(20, 25, 10, 25));

        JScrollPane scrollPane = new JScrollPane(listWrap);
        applyDarkScrollBar(scrollPane);
        panel.add(scrollPane, BorderLayout.CENTER);

        Runnable rebuildCharacterList = () -> {
            listWrap.removeAll();
            List<Object> characters = CharacterStorage.loadSavedCharacters();

            if (characters.isEmpty()) {
                JLabel emptyLabel = new JLabel("No characters yet.", JLabel.CENTER);
                emptyLabel.setForeground(TEXT_MUTED);
                emptyLabel.setFont(new Font("SansSerif", Font.PLAIN, 18));
                emptyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                emptyLabel.setBorder(new EmptyBorder(40, 0, 0, 0));
                listWrap.add(emptyLabel);
            } else {
                for (int i = 0; i < characters.size(); i++) {
                    if (!(characters.get(i) instanceof Map)) continue;
                    Map<?, ?> c = (Map<?, ?>) characters.get(i);
                    final int idx = i;

                    JPanel row = new JPanel(new BorderLayout(12, 0));
                    row.setBackground(BG_PANEL);
                    row.setBorder(BorderFactory.createCompoundBorder(
                            new LineBorder(BG_FIELD, 1, true),
                            new EmptyBorder(10, 14, 10, 10)));
                    row.setMaximumSize(new Dimension(470, 74));
                    row.setPreferredSize(new Dimension(470, 74));
                    row.setAlignmentX(Component.LEFT_ALIGNMENT);

                    CircleIcon circle = new CircleIcon(26);
                    circle.setCursor(new Cursor(Cursor.HAND_CURSOR));
                    circle.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent e) {
                            showDetail.accept(idx);
                        }
                    });
                    JPanel circleWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
                    circleWrap.setOpaque(false);
                    circleWrap.add(circle);
                    row.add(circleWrap, BorderLayout.WEST);

                    JPanel infoCol = new JPanel();
                    infoCol.setLayout(new BoxLayout(infoCol, BoxLayout.Y_AXIS));
                    infoCol.setOpaque(false);
                    infoCol.setCursor(new Cursor(Cursor.HAND_CURSOR));

                    String name = String.valueOf(c.get("name"));
                    JLabel nameLabel = new JLabel(name == null || name.isEmpty() || "null".equals(name) ? "Unnamed Character" : name);
                    nameLabel.setForeground(GOLD);
                    nameLabel.setFont(new Font("Serif", Font.BOLD, 17));
                    nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

                    String species = safeStr(c.get("species"));
                    int charLevel = CharacterStorage.recLevel(c);
                    Map<String, Integer> classLevels = CharacterStorage.recClassLevels(c);
                    String charClass = classLevels.size() > 1
                            ? String.join(" / ", classLevels.keySet())
                            : safeStr(c.get("characterClass"));
                    String background = safeStr(c.get("background"));
                    JLabel subLabel = new JLabel("<html>Lv " + charLevel + " " + charClass + " \u2022 " + species + " \u2022 " + background + "</html>");
                    subLabel.setForeground(TEXT_MUTED);
                    subLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
                    subLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

                    infoCol.add(nameLabel);
                    infoCol.add(Box.createRigidArea(new Dimension(0, 3)));
                    infoCol.add(subLabel);
                    infoCol.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent e) {
                            showDetail.accept(idx);
                        }
                    });
                    row.add(infoCol, BorderLayout.CENTER);

                    JPanel actionsWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
                    actionsWrap.setOpaque(false);

                    if (charLevel < FeatData.MAX_LEVEL) {
                        JButton levelUpButton = new JButton("Level Up");
                        styleAccentButton(levelUpButton);
                        levelUpButton.setFont(new Font("SansSerif", Font.BOLD, 12));
                        levelUpButton.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
                        levelUpButton.setToolTipText("Level up to " + (charLevel + 1));
                        levelUpButton.addActionListener(e -> showLevelUp.accept(idx));
                        actionsWrap.add(levelUpButton);
                    }

                    JButton editButton = new JButton("\uD83D\uDCDD");
                    editButton.setFont(new Font("SansSerif", Font.PLAIN, 16));
                    editButton.setForeground(TEXT_MUTED);
                    editButton.setBackground(BG_PANEL);
                    editButton.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                    editButton.setFocusPainted(false);
                    editButton.setContentAreaFilled(false);
                    editButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
                    editButton.setToolTipText("Edit this character");
                    editButton.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseEntered(MouseEvent e) {
                            editButton.setForeground(GOLD);
                        }
                        @Override
                        public void mouseExited(MouseEvent e) {
                            editButton.setForeground(TEXT_MUTED);
                        }
                    });
                    editButton.addActionListener(e -> {
                        resetForEdit.accept(idx);
                        cardLayout.show(cards, "NEW_PC_WIZARD");
                    });
                    actionsWrap.add(editButton);

                    JButton trashButton = new JButton("\uD83D\uDDD1");
                    trashButton.setFont(new Font("SansSerif", Font.PLAIN, 16));
                    trashButton.setForeground(TEXT_MUTED);
                    trashButton.setBackground(BG_PANEL);
                    trashButton.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                    trashButton.setFocusPainted(false);
                    trashButton.setContentAreaFilled(false);
                    trashButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
                    trashButton.setToolTipText("Delete this character");
                    trashButton.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseEntered(MouseEvent e) {
                            trashButton.setForeground(ACCENT_HOVER);
                        }
                        @Override
                        public void mouseExited(MouseEvent e) {
                            trashButton.setForeground(TEXT_MUTED);
                        }
                    });
                    trashButton.addActionListener(e -> {
                        int confirm = JOptionPane.showConfirmDialog(panel,
                                "Delete \"" + nameLabel.getText() + "\"? This cannot be undone.",
                                "Confirm Deletion", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                        if (confirm == JOptionPane.YES_OPTION) {
                            List<Object> current = CharacterStorage.loadSavedCharacters();
                            if (idx >= 0 && idx < current.size()) {
                                current.remove(idx);
                                CharacterStorage.saveAllCharacters(current);
                            }
                            rebuildCharacterListHolder[0].run();
                        }
                    });
                    actionsWrap.add(trashButton);

                    row.add(actionsWrap, BorderLayout.EAST);

                    listWrap.add(row);
                    listWrap.add(Box.createRigidArea(new Dimension(0, 10)));
                }
            }
            listWrap.revalidate();
            listWrap.repaint();
        };
        rebuildCharacterListHolder[0] = rebuildCharacterList;

        panel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                rebuildCharacterListHolder[0].run();
            }
        });

        JButton addButton = new JButton("+ Add New Character");
        styleAccentButton(addButton);
        addButton.addActionListener(e -> cardLayout.show(cards, "ADD_CHAR_TYPE"));

        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bottomBar.setBackground(BG_DARK);
        bottomBar.setBorder(new EmptyBorder(20, 0, 30, 0));
        bottomBar.add(addButton);
        panel.add(bottomBar, BorderLayout.SOUTH);

        return panel;
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String safeStr(Object o) {
        return (o == null || "null".equals(String.valueOf(o))) ? "\u2014" : String.valueOf(o);
    }

    // ---------------------------------------------------------------
    // ASSETS PANEL
    // ---------------------------------------------------------------
    private static JPanel buildAssetsPanel(CardLayout cardLayout, JPanel cards) {
        JPanel panel = buildSectionShell("Assets", cardLayout, cards, "MENU");

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(BG_DARK);
        content.setBorder(new EmptyBorder(30, 30, 30, 30));

        JLabel emptyLabel = new JLabel("No assets yet.", JLabel.CENTER);
        emptyLabel.setForeground(TEXT_MUTED);
        emptyLabel.setFont(new Font("SansSerif", Font.PLAIN, 18));
        content.add(emptyLabel, BorderLayout.CENTER);

        JButton addButton = new JButton("+ Add New Asset");
        styleAccentButton(addButton);
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bottomBar.setBackground(BG_DARK);
        bottomBar.setBorder(new EmptyBorder(20, 0, 30, 0));
        bottomBar.add(addButton);

        panel.add(content, BorderLayout.CENTER);
        panel.add(bottomBar, BorderLayout.SOUTH);

        return panel;
    }

    // ---------------------------------------------------------------
    // COMBAT PANEL
    // ---------------------------------------------------------------
    private static JPanel buildCombatPanel(CardLayout cardLayout, JPanel cards) {
        JPanel panel = buildSectionShell("Combat", cardLayout, cards, "MENU");

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(BG_DARK);
        content.setBorder(new EmptyBorder(40, 30, 30, 30));

        JLabel selectMapLabel = new JLabel("Select a map");
        selectMapLabel.setForeground(TEXT_LIGHT);
        selectMapLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        selectMapLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel noneAvailableLabel = new JLabel("None available.");
        noneAvailableLabel.setForeground(TEXT_MUTED);
        noneAvailableLabel.setFont(new Font("SansSerif", Font.PLAIN, 16));
        noneAvailableLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        content.add(selectMapLabel);
        content.add(Box.createRigidArea(new Dimension(0, 10)));
        content.add(noneAvailableLabel);

        panel.add(content, BorderLayout.CENTER);

        return panel;
    }

    // ---------------------------------------------------------------
    // ADD CHARACTER: choose Playable Character or NPC
    // ---------------------------------------------------------------
    private static JPanel buildAddCharacterTypePanel(CardLayout cardLayout, JPanel cards, Runnable resetWizard) {
        JPanel panel = buildSectionShell("Add Character", cardLayout, cards, "CHARACTERS");

        JPanel content = new JPanel(new GridBagLayout());
        content.setBackground(BG_DARK);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(15, 15, 15, 15);
        gbc.gridy = 0;

        JButton btnPlayable = new JButton("Playable Character");
        JButton btnNPC = new JButton("NPC");
        for (JButton b : new JButton[]{btnPlayable, btnNPC}) {
            styleAccentButton(b);
            b.setPreferredSize(new Dimension(220, 55));
            b.setFont(new Font("SansSerif", Font.BOLD, 16));
        }

        btnPlayable.addActionListener(e -> {
            resetWizard.run();
            cardLayout.show(cards, "NEW_PC_WIZARD");
        });
        btnNPC.addActionListener(e -> cardLayout.show(cards, "NEW_NPC"));

        gbc.gridx = 0; content.add(btnPlayable, gbc);
        gbc.gridx = 1; content.add(btnNPC, gbc);

        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    // ---------------------------------------------------------------
    // NEW NPC (placeholder)
    // ---------------------------------------------------------------
    private static JPanel buildNewNPCPanel(CardLayout cardLayout, JPanel cards) {
        JPanel panel = buildSectionShell("New NPC", cardLayout, cards, "ADD_CHAR_TYPE");

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(BG_DARK);
        content.setBorder(new EmptyBorder(30, 30, 30, 30));

        JLabel placeholder = new JLabel("NPC creation coming soon.", JLabel.CENTER);
        placeholder.setForeground(TEXT_MUTED);
        placeholder.setFont(new Font("SansSerif", Font.PLAIN, 18));
        content.add(placeholder, BorderLayout.CENTER);

        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    // ---------------------------------------------------------------
    // CHARACTER DETAIL (blank placeholder page reached by clicking a character's icon)
    // ---------------------------------------------------------------
    private static CharacterDetailResult buildCharacterDetailPanel(CardLayout cardLayout, JPanel cards) {
        JPanel panel = buildSectionShell("Character", cardLayout, cards, "CHARACTERS");

        CardLayout innerCardLayout = new CardLayout();
        JPanel innerCards = new JPanel(innerCardLayout);
        innerCards.setBackground(BG_DARK);

        ScrollableContentPanel sheetContent = new ScrollableContentPanel();
        sheetContent.setBackground(BG_DARK);
        JScrollPane sheetScroll = new JScrollPane(sheetContent);
        applyDarkScrollBar(sheetScroll);

        JPanel spellsContent = new JPanel();
        spellsContent.setBackground(BG_DARK);
        JScrollPane spellsScroll = new JScrollPane(spellsContent);
        applyDarkScrollBar(spellsScroll);

        innerCards.add(sheetScroll, "SHEET");
        innerCards.add(spellsScroll, "SPELLS");

        JButton sheetTabButton = new JButton("Character Sheet");
        JButton spellsTabButton = new JButton("Spells");
        for (JButton b : new JButton[]{sheetTabButton, spellsTabButton}) {
            b.setFocusPainted(false);
            b.setBorderPainted(false);
            b.setOpaque(true);
            b.setForeground(TEXT_LIGHT);
            b.setFont(new Font("SansSerif", Font.BOLD, 14));
            b.setCursor(new Cursor(Cursor.HAND_CURSOR));
            b.setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 18));
        }

        java.util.function.Consumer<String> showTab = tab -> {
            innerCardLayout.show(innerCards, tab);
            sheetTabButton.setBackground("SHEET".equals(tab) ? ACCENT : BG_FIELD);
            spellsTabButton.setBackground("SPELLS".equals(tab) ? ACCENT : BG_FIELD);
        };
        sheetTabButton.addActionListener(e -> showTab.accept("SHEET"));
        spellsTabButton.addActionListener(e -> showTab.accept("SPELLS"));

        JPanel tabBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 12));
        tabBar.setBackground(BG_DARK);
        tabBar.add(sheetTabButton);
        tabBar.add(spellsTabButton);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(BG_DARK);
        wrap.add(tabBar, BorderLayout.NORTH);
        wrap.add(innerCards, BorderLayout.CENTER);
        panel.add(wrap, BorderLayout.CENTER);

        java.util.function.IntConsumer show = idx -> {
            List<Object> characters = CharacterStorage.loadSavedCharacters();
            if (idx < 0 || idx >= characters.size() || !(characters.get(idx) instanceof Map)) return;
            Map<?, ?> rec = (Map<?, ?>) characters.get(idx);
            populateCharacterSheet(sheetContent, rec);
            populateCharacterSpells(spellsContent, rec);
            showTab.accept("SHEET");
            cardLayout.show(cards, "CHARACTER_DETAIL");
        };

        return new CharacterDetailResult(panel, show);
    }

    private static void populateCharacterSheet(JPanel container, Map<?, ?> rec) {
        container.removeAll();
        container.setLayout(new BorderLayout());
        container.setBorder(new EmptyBorder(24, 28, 24, 28));

        String name = CharacterStorage.recStr(rec, "name");
        String characterClass = CharacterStorage.recStr(rec, "characterClass");
        String background = CharacterStorage.recStr(rec, "background");
        String species = CharacterStorage.recStr(rec, "species");
        String lineage = CharacterStorage.recStr(rec, "lineage");
        Integer levelObj = CharacterStorage.recInt(rec, "level");
        int level = levelObj != null ? levelObj : 1;
        Map<String, Integer> stats = CharacterStorage.recStatsMap(rec);
        int profBonus = GameData.proficiencyBonus(level);
        Set<String> skillProfs = CharacterStorage.recSkillProficiencies(rec);
        String[] saves = GameData.CLASS_SAVING_THROWS.get(characterClass);
        Set<String> saveProfs = saves != null ? new LinkedHashSet<>(Arrays.asList(saves)) : new LinkedHashSet<>();
        List<Map<String, Object>> feats = CharacterStorage.recFeats(rec);
        for (Map<String, Object> f : feats) {
            // Resilient grants saving throw proficiency in the ability it increased
            if ("Resilient".equals(f.get("name"))) saveProfs.addAll(CharacterStorage.featStatIncreases(f).keySet());
        }
        Map<String, Integer> classLevels = CharacterStorage.recClassLevels(rec);
        String hitDice = FeatData.hitDiceString(classLevels);

        int dexMod = GameData.abilityModifier(stats.getOrDefault("DEX", 10));
        int wisMod = GameData.abilityModifier(stats.getOrDefault("WIS", 10));
        int passivePerception = 10 + wisMod + (skillProfs.contains("Perception") ? profBonus : 0);
        Integer hp = CharacterStorage.recInt(rec, "hp");
        Integer speed = CharacterStorage.recInt(rec, "speed");
        Integer gold = CharacterStorage.recInt(rec, "gold");
        int ac = GameData.computeAC(CharacterStorage.recList(rec, "equipment"), dexMod);

        // ---- Identity bar: name + labeled fields, laid out horizontally ----
        JPanel identityBar = new JPanel();
        identityBar.setLayout(new BoxLayout(identityBar, BoxLayout.X_AXIS));
        identityBar.setOpaque(false);
        identityBar.setBorder(new EmptyBorder(0, 0, 24, 0));
        identityBar.setAlignmentX(Component.LEFT_ALIGNMENT);

        identityBar.add(buildIdentityField("NAME", name.isEmpty() ? "Unnamed Character" : name, true));
        identityBar.add(Box.createHorizontalGlue());

        JPanel fieldsCluster = new JPanel();
        fieldsCluster.setLayout(new BoxLayout(fieldsCluster, BoxLayout.X_AXIS));
        fieldsCluster.setOpaque(false);
        String lineageSuffix = lineage.isEmpty() ? "" : " (" + lineage + ")";
        String classText;
        if (classLevels.size() > 1) {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<String, Integer> en : classLevels.entrySet()) parts.add(en.getKey() + " " + en.getValue());
            classText = String.join(" / ", parts);
        } else {
            classText = characterClass.isEmpty() ? "—" : characterClass;
        }
        fieldsCluster.add(buildIdentityField("CLASS", classText, false));
        fieldsCluster.add(Box.createRigidArea(new Dimension(28, 0)));
        fieldsCluster.add(buildIdentityField("LEVEL", "Level " + level, false));
        fieldsCluster.add(Box.createRigidArea(new Dimension(28, 0)));
        fieldsCluster.add(buildIdentityField("BACKGROUND", background.isEmpty() ? "—" : background, false));
        fieldsCluster.add(Box.createRigidArea(new Dimension(28, 0)));
        fieldsCluster.add(buildIdentityField("SPECIES", (species.isEmpty() ? "—" : species) + lineageSuffix, false));
        identityBar.add(fieldsCluster);

        container.add(identityBar, BorderLayout.NORTH);

        // ---- Body: 3 weighted columns via GridBagLayout so the sheet rebalances on resize ----
        JPanel bodyPanel = new JPanel(new GridBagLayout());
        bodyPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.NORTHWEST;

        // ---- Left column: ability scores + combat stats (saves moved out, AC promoted) ----
        JPanel leftCol = new JPanel();
        leftCol.setLayout(new BoxLayout(leftCol, BoxLayout.Y_AXIS));
        leftCol.setOpaque(false);

        JPanel abilityGrid = new JPanel(new GridLayout(3, 2, 12, 12));
        abilityGrid.setOpaque(false);
        abilityGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (String stat : GameData.STAT_ORDER) {
            abilityGrid.add(buildAbilityBox(stat, stats.getOrDefault(stat, 10)));
        }
        Dimension abilityGridPref = abilityGrid.getPreferredSize();
        abilityGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, abilityGridPref.height));
        leftCol.add(abilityGrid);
        leftCol.add(Box.createRigidArea(new Dimension(0, 18)));

        leftCol.add(statLine("Proficiency Bonus", signed(profBonus)));
        leftCol.add(Box.createRigidArea(new Dimension(0, 10)));
        leftCol.add(statLine("Hit Point Maximum", hp != null ? String.valueOf(hp) : "—"));
        leftCol.add(statLine("Hit Dice", hitDice));
        leftCol.add(statLine("Initiative", signed(dexMod)));
        leftCol.add(statLine("Passive Perception", String.valueOf(passivePerception)));
        leftCol.add(Box.createRigidArea(new Dimension(0, 16)));

        ShieldPanel shield = new ShieldPanel(ac);
        shield.setAlignmentX(Component.CENTER_ALIGNMENT);
        leftCol.add(shield);
        leftCol.add(Box.createRigidArea(new Dimension(0, 16)));

        leftCol.add(statLine("Speed", speed != null ? speed + " ft." : "—"));
        leftCol.add(statLine("Gold", (gold != null ? gold : 0) + " gp"));

        gbc.gridx = 0;
        gbc.weightx = 0.30;
        gbc.insets = new Insets(0, 0, 0, 18);
        bodyPanel.add(leftCol, gbc);

        // ---- Center column: Saving Throws directly above Skills ----
        JPanel centerCol = new JPanel();
        centerCol.setLayout(new BoxLayout(centerCol, BoxLayout.Y_AXIS));
        centerCol.setOpaque(false);

        centerCol.add(sectionTitle("Saving Throws"));
        centerCol.add(Box.createRigidArea(new Dimension(0, 10)));
        for (String stat : GameData.STAT_ORDER) {
            boolean prof = saveProfs.contains(stat);
            int mod = GameData.abilityModifier(stats.getOrDefault(stat, 10)) + (prof ? profBonus : 0);
            centerCol.add(proficiencyRow(prof, stat, signed(mod)));
        }
        centerCol.add(Box.createRigidArea(new Dimension(0, 20)));

        JPanel divider = new JPanel();
        divider.setBackground(BG_FIELD);
        divider.setAlignmentX(Component.LEFT_ALIGNMENT);
        divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        divider.setPreferredSize(new Dimension(10, 1));
        centerCol.add(divider);
        centerCol.add(Box.createRigidArea(new Dimension(0, 20)));

        centerCol.add(sectionTitle("Skills"));
        centerCol.add(Box.createRigidArea(new Dimension(0, 10)));
        for (String skill : GameData.ALL_SKILLS) {
            boolean prof = skillProfs.contains(skill);
            String ability = GameData.SKILL_ABILITY.get(skill);
            int mod = GameData.abilityModifier(stats.getOrDefault(ability, 10)) + (prof ? profBonus : 0);
            centerCol.add(proficiencyRow(prof, skill + " (" + ability + ")", signed(mod)));
        }

        gbc.gridx = 1;
        gbc.weightx = 0.42;
        gbc.insets = new Insets(0, 0, 0, 18);
        bodyPanel.add(centerCol, gbc);

        // ---- Right column: features (left blank for now) ----
        JPanel featuresPanel = new JPanel(new BorderLayout());
        featuresPanel.setBackground(BG_PANEL);
        featuresPanel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BG_FIELD, 1, true),
                new EmptyBorder(14, 16, 14, 16)));

        JLabel featuresTitle = new JLabel("Features");
        featuresTitle.setForeground(GOLD);
        featuresTitle.setFont(new Font("SansSerif", Font.BOLD, 18));
        featuresPanel.add(featuresTitle, BorderLayout.NORTH);

        JPanel featuresList = new JPanel();
        featuresList.setLayout(new BoxLayout(featuresList, BoxLayout.Y_AXIS));
        featuresList.setOpaque(false);
        featuresList.setBorder(new EmptyBorder(10, 0, 0, 0));
        if (feats.isEmpty()) {
            JLabel none = new JLabel("No features yet.");
            none.setForeground(TEXT_MUTED);
            none.setFont(new Font("SansSerif", Font.PLAIN, 14));
            featuresList.add(none);
        }
        for (Map<String, Object> f : feats) {
            String category = String.valueOf(f.get("category"));
            String kind = FeatData.CATEGORY_EPIC.equals(category) ? "Epic Boon"
                    : FeatData.CATEGORY_CUSTOM.equals(category) ? "Custom Feat" : "Feat";
            Object fClass = f.get("className");
            Object fLevel = f.get("classLevel");
            String source = (fClass != null && fLevel instanceof Number) ? " • " + fClass + " " + ((Number) fLevel).intValue() : "";
            List<String> incParts = new ArrayList<>();
            for (Map.Entry<String, Integer> en : CharacterStorage.featStatIncreases(f).entrySet()) {
                incParts.add(en.getKey() + " +" + en.getValue());
            }
            String desc = f.get("description") == null ? "" : String.valueOf(f.get("description"));
            if (FeatData.CATEGORY_ASI.equals(category)) desc = ""; // the increases line already says it all

            StringBuilder html = new StringBuilder("<html><div style='width:210px'>");
            html.append("<b style='color:#d4af37'>").append(escapeHtml(String.valueOf(f.get("name")))).append("</b><br>");
            html.append("<span style='color:#aaa5a0'>").append(kind).append(escapeHtml(source));
            if (!incParts.isEmpty()) html.append(" • ").append(String.join(", ", incParts));
            html.append("</span>");
            if (!desc.isEmpty()) html.append("<br>").append(escapeHtml(desc));
            html.append("</div></html>");

            JLabel featLbl = new JLabel(html.toString());
            featLbl.setForeground(TEXT_LIGHT);
            featLbl.setFont(new Font("SansSerif", Font.PLAIN, 13));
            featLbl.setBorder(new EmptyBorder(0, 0, 12, 0));
            featLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
            featuresList.add(featLbl);
        }
        JPanel featuresListWrap = new JPanel(new BorderLayout());
        featuresListWrap.setOpaque(false);
        featuresListWrap.add(featuresList, BorderLayout.NORTH);
        featuresPanel.add(featuresListWrap, BorderLayout.CENTER);

        gbc.gridx = 2;
        gbc.weightx = 0.28;
        gbc.insets = new Insets(0, 0, 0, 0);
        bodyPanel.add(featuresPanel, gbc);

        container.add(bodyPanel, BorderLayout.CENTER);

        container.revalidate();
        container.repaint();
    }

    private static JPanel buildIdentityField(String captionText, String value, boolean hero) {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setOpaque(false);
        box.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel caption = new JLabel(captionText);
        caption.setForeground(TEXT_MUTED);
        caption.setFont(new Font("SansSerif", Font.BOLD, 11));
        caption.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel val = new JLabel(value);
        val.setForeground(hero ? GOLD : TEXT_LIGHT);
        val.setFont(new Font(hero ? "Serif" : "SansSerif", Font.BOLD, hero ? 34 : 18));
        val.setAlignmentX(Component.LEFT_ALIGNMENT);

        box.add(caption);
        box.add(Box.createRigidArea(new Dimension(0, 2)));
        box.add(val);
        return box;
    }

    private static JLabel sectionTitle(String text) {
        JLabel title = new JLabel(text);
        title.setForeground(GOLD);
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        return title;
    }

    private static JPanel buildAbilityBox(String stat, int score) {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBackground(BG_PANEL);
        box.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BG_FIELD, 1, true),
                new EmptyBorder(14, 14, 12, 14)));

        JLabel statLabel = new JLabel(stat);
        statLabel.setForeground(TEXT_MUTED);
        statLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        statLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel modLabel = new JLabel(signed(GameData.abilityModifier(score)));
        modLabel.setForeground(GOLD);
        modLabel.setFont(new Font("Serif", Font.BOLD, 26));
        modLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel scoreLabel = new JLabel(String.valueOf(score));
        scoreLabel.setForeground(TEXT_LIGHT);
        scoreLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        scoreLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        box.add(statLabel);
        box.add(Box.createRigidArea(new Dimension(0, 3)));
        box.add(modLabel);
        box.add(Box.createRigidArea(new Dimension(0, 3)));
        box.add(scoreLabel);
        return box;
    }

    static String signed(int n) {
        return n >= 0 ? "+" + n : String.valueOf(n);
    }

    private static JPanel statLine(String label, String value) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        row.setBorder(new EmptyBorder(5, 2, 5, 2));

        JLabel labelLbl = new JLabel(label);
        labelLbl.setForeground(TEXT_MUTED);
        labelLbl.setFont(new Font("SansSerif", Font.PLAIN, 14));

        JLabel valueLbl = new JLabel(value);
        valueLbl.setForeground(TEXT_LIGHT);
        valueLbl.setFont(new Font("SansSerif", Font.BOLD, 15));

        row.add(labelLbl, BorderLayout.WEST);
        row.add(valueLbl, BorderLayout.EAST);
        return row;
    }

    private static JPanel proficiencyRow(boolean proficient, String label, String value) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        row.setBorder(new EmptyBorder(2, 0, 2, 0));

        JLabel checkLbl = new JLabel(proficient ? "✓" : " ");
        checkLbl.setForeground(proficient ? GOLD : TEXT_MUTED);
        checkLbl.setFont(new Font("SansSerif", Font.BOLD, 15));
        checkLbl.setPreferredSize(new Dimension(18, 18));

        JLabel labelLbl = new JLabel(label);
        labelLbl.setForeground(TEXT_LIGHT);
        labelLbl.setFont(new Font("SansSerif", Font.PLAIN, 14));

        JLabel valueLbl = new JLabel(value);
        valueLbl.setForeground(TEXT_LIGHT);
        valueLbl.setFont(new Font("SansSerif", Font.BOLD, 15));

        JPanel leftWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        leftWrap.setOpaque(false);
        leftWrap.add(checkLbl);
        leftWrap.add(labelLbl);

        row.add(leftWrap, BorderLayout.WEST);
        row.add(valueLbl, BorderLayout.EAST);
        return row;
    }

    private static void populateCharacterSpells(JPanel container, Map<?, ?> rec) {
        container.removeAll();
        container.setLayout(new BorderLayout());
        container.setBorder(new EmptyBorder(20, 25, 20, 25));

        Integer spellSlots = CharacterStorage.recInt(rec, "spellSlots");
        List<String> cantrips = CharacterStorage.recList(rec, "cantripsKnown");
        List<String> spells = CharacterStorage.recList(rec, "spellsKnown");

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);

        if (spellSlots == null && cantrips.isEmpty() && spells.isEmpty()) {
            JLabel none = new JLabel("No spells.");
            none.setForeground(TEXT_MUTED);
            none.setFont(new Font("SansSerif", Font.PLAIN, 18));
            none.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(none);
        } else {
            if (spellSlots != null) {
                JLabel slotsLbl = new JLabel("Level 1 Spell Slots: " + spellSlots);
                slotsLbl.setForeground(TEXT_LIGHT);
                slotsLbl.setFont(new Font("SansSerif", Font.BOLD, 16));
                slotsLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
                body.add(slotsLbl);
                body.add(Box.createRigidArea(new Dimension(0, 16)));
            }
            body.add(spellListSection("Cantrips Known", cantrips));
            body.add(Box.createRigidArea(new Dimension(0, 16)));
            body.add(spellListSection("Spells Known", spells));
        }

        container.add(body, BorderLayout.NORTH);
        container.revalidate();
        container.repaint();
    }

    private static JPanel spellListSection(String title, List<String> spells) {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setOpaque(false);
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel titleLbl = new JLabel(title);
        titleLbl.setForeground(GOLD);
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 16));
        titleLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(titleLbl);
        section.add(Box.createRigidArea(new Dimension(0, 6)));

        if (spells.isEmpty()) {
            JLabel none = new JLabel("None");
            none.setForeground(TEXT_MUTED);
            none.setFont(new Font("SansSerif", Font.PLAIN, 14));
            none.setAlignmentX(Component.LEFT_ALIGNMENT);
            section.add(none);
        } else {
            for (String spell : spells) {
                JLabel spellLbl = new JLabel("• " + spell);
                spellLbl.setForeground(TEXT_LIGHT);
                spellLbl.setFont(new Font("SansSerif", Font.PLAIN, 14));
                spellLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
                section.add(spellLbl);
            }
        }
        return section;
    }

    // ---------------------------------------------------------------
    // NEW PLAYABLE CHARACTER WIZARD
    // Name -> Class -> Ability Scores -> Background -> Ability Score Increase
    // -> Background Skills -> Class Details
    // ---------------------------------------------------------------
    private static WizardResult buildNewPCWizardPanel(CardLayout outerCardLayout, JPanel outerCards) {
        CharacterDraft draft = new CharacterDraft();
        boolean[] editModeHolder = new boolean[1];
        List<JButton> saveChangeButtons = new ArrayList<>();
        Runnable updateSaveButtonLabels = () -> {
            String label = editModeHolder[0] ? "Save Changes" : "Save Draft";
            for (JButton b : saveChangeButtons) b.setText(label);
        };

        // Which saved character (index into characters.json) we're editing, if any
        Integer[] editIndexHolder = new Integer[1];

        // Tracks which sections have been (re)confirmed during this edit session, so
        // "Save Changes" can tell untouched (kept as-is) sections apart from touched ones
        // that need their dependents finished before saving is allowed.
        boolean[] touchedSpecies = {false};
        boolean[] touchedLineage = {false};
        boolean[] touchedClass = {false};
        boolean[] touchedASI = {false};
        boolean[] touchedStats = {false};
        boolean[] touchedBackground = {false};
        boolean[] touchedBgSkills = {false};
        boolean[] touchedClassDetails = {false};
        boolean[] touchedEquipment = {false};

        // Progress-bar segment completeness: "could I press Next from here right now?"
        // Starts all-false for a brand new character, all-true when editing an existing one
        // (see reset/resetForEdit below), and individual segments go false again the moment
        // something upstream actually changes in a way that invalidates them.
        boolean[] segNameOk = {false};
        boolean[] segSpeciesOk = {false};
        boolean[] segClassOk = {false};
        boolean[] segStatsOk = {false};
        boolean[] segBackgroundOk = {false};
        boolean[] segDetailsOk = {false};
        boolean[] segEquipmentOk = {false};

        JPanel wizard = new JPanel(new BorderLayout());
        wizard.setBackground(BG_DARK);

        // Attempts to save the character being edited. Untouched sections keep their
        // original saved values; touched sections that leave a dependent section unfinished
        // block the save with an explanation instead of silently saving stale data.
        // The real implementation is assigned further down, once every step's widgets exist,
        // so it can pull whatever is currently on screen into draft before validating/saving.
        Runnable[] performSaveChangesHolder = new Runnable[1];

        // ---------------- Persistent character sidebar ----------------
        TranslucentPanel sidebar = new TranslucentPanel(new Color(0, 0, 0, 140));
        sidebar.setLayout(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(300, 100));
        sidebar.setBorder(new EmptyBorder(25, 20, 25, 20));

        JLabel sidebarTitle = new JLabel("Character");
        sidebarTitle.setForeground(GOLD);
        sidebarTitle.setFont(new Font("Serif", Font.BOLD, 22));
        sidebarTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel nameValueLabel = new JLabel("Name: \u2014");
        nameValueLabel.setForeground(TEXT_LIGHT);
        nameValueLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        nameValueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel classValueLabel = new JLabel("Class: \u2014");
        classValueLabel.setForeground(TEXT_LIGHT);
        classValueLabel.setFont(new Font("SansSerif", Font.PLAIN, 15));
        classValueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel speciesValueLabel = new JLabel("Species: \u2014");
        speciesValueLabel.setForeground(TEXT_LIGHT);
        speciesValueLabel.setFont(new Font("SansSerif", Font.PLAIN, 15));
        speciesValueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextArea movementValueLabel = new JTextArea("Speed: \u2014");
        movementValueLabel.setEditable(false);
        movementValueLabel.setOpaque(false);
        movementValueLabel.setForeground(TEXT_LIGHT);
        movementValueLabel.setFont(new Font("SansSerif", Font.PLAIN, 15));
        movementValueLabel.setLineWrap(true);
        movementValueLabel.setWrapStyleWord(true);
        movementValueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel hpValueLabel = new JLabel("HP: \u2014");
        hpValueLabel.setForeground(TEXT_LIGHT);
        hpValueLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        hpValueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel backgroundValueLabel = new JLabel("Background: \u2014");
        backgroundValueLabel.setForeground(TEXT_LIGHT);
        backgroundValueLabel.setFont(new Font("SansSerif", Font.PLAIN, 15));
        backgroundValueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextArea skillsValueArea = new JTextArea("Skills: \u2014");
        skillsValueArea.setEditable(false);
        skillsValueArea.setOpaque(false);
        skillsValueArea.setForeground(TEXT_LIGHT);
        skillsValueArea.setFont(new Font("SansSerif", Font.PLAIN, 15));
        skillsValueArea.setLineWrap(true);
        skillsValueArea.setWrapStyleWord(true);
        skillsValueArea.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextArea statsValueArea = new JTextArea("Ability Scores:\n\u2014");
        statsValueArea.setEditable(false);
        statsValueArea.setOpaque(false);
        statsValueArea.setForeground(TEXT_LIGHT);
        statsValueArea.setFont(new Font("SansSerif", Font.PLAIN, 15));
        statsValueArea.setLineWrap(true);
        statsValueArea.setWrapStyleWord(true);
        statsValueArea.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel spellSlotsValueLabel = new JLabel("Spell Slots (1st): \u2014");
        spellSlotsValueLabel.setForeground(TEXT_LIGHT);
        spellSlotsValueLabel.setFont(new Font("SansSerif", Font.PLAIN, 15));
        spellSlotsValueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextArea cantripsValueArea = new JTextArea("Cantrips: \u2014");
        cantripsValueArea.setEditable(false);
        cantripsValueArea.setOpaque(false);
        cantripsValueArea.setForeground(TEXT_LIGHT);
        cantripsValueArea.setFont(new Font("SansSerif", Font.PLAIN, 15));
        cantripsValueArea.setLineWrap(true);
        cantripsValueArea.setWrapStyleWord(true);
        cantripsValueArea.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextArea spellsValueArea = new JTextArea("Spells Known: \u2014");
        spellsValueArea.setEditable(false);
        spellsValueArea.setOpaque(false);
        spellsValueArea.setForeground(TEXT_LIGHT);
        spellsValueArea.setFont(new Font("SansSerif", Font.PLAIN, 15));
        spellsValueArea.setLineWrap(true);
        spellsValueArea.setWrapStyleWord(true);
        spellsValueArea.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel goldValueLabel = new JLabel("Gold: \u2014");
        goldValueLabel.setForeground(TEXT_LIGHT);
        goldValueLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        goldValueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextArea equipmentValueArea = new JTextArea("Equipment: \u2014");
        equipmentValueArea.setEditable(false);
        equipmentValueArea.setOpaque(false);
        equipmentValueArea.setForeground(TEXT_LIGHT);
        equipmentValueArea.setFont(new Font("SansSerif", Font.PLAIN, 15));
        equipmentValueArea.setLineWrap(true);
        equipmentValueArea.setWrapStyleWord(true);
        equipmentValueArea.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel sidebarInner = new JPanel();
        sidebarInner.setOpaque(false);
        sidebarInner.setLayout(new BoxLayout(sidebarInner, BoxLayout.Y_AXIS));
        sidebarInner.add(sidebarTitle);
        sidebarInner.add(Box.createRigidArea(new Dimension(0, 15)));
        sidebarInner.add(nameValueLabel);
        sidebarInner.add(Box.createRigidArea(new Dimension(0, 8)));
        sidebarInner.add(speciesValueLabel);
        sidebarInner.add(Box.createRigidArea(new Dimension(0, 4)));
        sidebarInner.add(movementValueLabel);
        sidebarInner.add(Box.createRigidArea(new Dimension(0, 8)));
        sidebarInner.add(classValueLabel);
        sidebarInner.add(Box.createRigidArea(new Dimension(0, 4)));
        sidebarInner.add(hpValueLabel);
        sidebarInner.add(Box.createRigidArea(new Dimension(0, 8)));
        sidebarInner.add(backgroundValueLabel);
        sidebarInner.add(Box.createRigidArea(new Dimension(0, 8)));
        sidebarInner.add(skillsValueArea);
        sidebarInner.add(Box.createRigidArea(new Dimension(0, 15)));
        sidebarInner.add(statsValueArea);
        sidebarInner.add(Box.createRigidArea(new Dimension(0, 15)));
        sidebarInner.add(spellSlotsValueLabel);
        sidebarInner.add(Box.createRigidArea(new Dimension(0, 8)));
        sidebarInner.add(cantripsValueArea);
        sidebarInner.add(Box.createRigidArea(new Dimension(0, 8)));
        sidebarInner.add(spellsValueArea);
        sidebarInner.add(Box.createRigidArea(new Dimension(0, 15)));
        sidebarInner.add(goldValueLabel);
        sidebarInner.add(Box.createRigidArea(new Dimension(0, 8)));
        sidebarInner.add(equipmentValueArea);

        sidebar.add(sidebarInner, BorderLayout.NORTH);

        JPanel sidebarWrap = new JPanel(new BorderLayout());
        sidebarWrap.setBackground(BG_DARK);
        sidebarWrap.setBorder(new EmptyBorder(10, 10, 10, 20));
        sidebarWrap.add(sidebar, BorderLayout.CENTER);

        Runnable refreshSidebar = () -> {
            nameValueLabel.setText("Name: " + (draft.name.isEmpty() ? "\u2014" : draft.name));
            speciesValueLabel.setText("Species: " + (draft.species.isEmpty() ? "\u2014" : draft.species
                    + (draft.lineage.isEmpty() ? "" : " (" + draft.lineage + ")")));
            if (draft.species.isEmpty()) {
                movementValueLabel.setText("Speed: \u2014");
            } else {
                int speed = GameData.computeSpeed(draft.species, draft.lineage);
                String size = GameData.computeSize(draft.species);
                String type = GameData.computeCreatureType(draft.species);
                Integer climb = GameData.computeClimbSpeed(draft.species);
                String moveText = "Speed: " + speed + " ft  |  Size: " + size + "  |  Type: " + type;
                if (climb != null) moveText += "  |  Climb: " + climb + " ft";
                movementValueLabel.setText(moveText);
            }
            classValueLabel.setText("Class: " + (draft.characterClass.isEmpty() ? "\u2014" : draft.characterClass));

            Integer conScore = draft.stats.get("CON");
            int hd = GameData.hitDieMax(draft.characterClass);
            if (hd > 0 && conScore != null) {
                int hp = hd + GameData.abilityModifier(conScore);
                hpValueLabel.setText("HP: " + hp + "  (d" + hd + " + CON mod)");
            } else {
                hpValueLabel.setText("HP: \u2014");
            }

            backgroundValueLabel.setText("Background: " + (draft.background.isEmpty() ? "\u2014" : draft.background));
            skillsValueArea.setText("Skills: " + (draft.backgroundSkills.isEmpty() ? "\u2014" : String.join(", ", draft.backgroundSkills)));
            StringBuilder sb = new StringBuilder("Ability Scores:\n");
            boolean any = false;
            for (String s : GameData.STAT_ORDER) {
                Integer v = draft.stats.get(s);
                if (v != null) {
                    sb.append(s).append(": ").append(v).append("\n");
                    any = true;
                }
            }
            if (!any) sb.append("\u2014");
            statsValueArea.setText(sb.toString().trim());

            Integer slots = GameData.SPELL_SLOTS_LEVEL1.get(draft.characterClass);
            spellSlotsValueLabel.setText("Spell Slots (1st): " + (slots != null ? slots : "\u2014"));
            cantripsValueArea.setText("Cantrips: " + (draft.cantripsKnown.isEmpty() ? "\u2014" : String.join(", ", draft.cantripsKnown)));
            spellsValueArea.setText("Spells Known: " + (draft.spellsKnown.isEmpty() ? "\u2014" : String.join(", ", draft.spellsKnown)));
            goldValueLabel.setText("Gold: " + (draft.equipment.isEmpty() && draft.gold == 0 ? "\u2014" : draft.gold + " GP"));
            equipmentValueArea.setText("Equipment: " + (draft.equipment.isEmpty() ? "\u2014" : String.join(", ", draft.equipment)));
        };

        // ---------------- Inner step navigation ----------------
        CardLayout innerCardLayout = new CardLayout();
        JPanel innerCards = new JPanel(innerCardLayout);
        innerCards.setBackground(BG_DARK);

        // ===================== NAME STEP =====================
        JPanel namePanel = new JPanel(new BorderLayout());
        namePanel.setBackground(BG_DARK);
        namePanel.add(buildHeader("New Playable Character", () -> outerCardLayout.show(outerCards, "ADD_CHAR_TYPE")), BorderLayout.NORTH);

        JPanel nameContent = new JPanel(new GridBagLayout());
        nameContent.setBackground(BG_DARK);
        GridBagConstraints nGbc = new GridBagConstraints();
        nGbc.insets = new Insets(10, 10, 10, 10);

        JLabel nameLabel = new JLabel("Character Name:");
        nameLabel.setForeground(TEXT_LIGHT);
        nameLabel.setFont(new Font("SansSerif", Font.BOLD, 18));

        JTextField nameField = new JTextField(20);
        styleTextField(nameField);

        JButton nameNextButton = new JButton("Next \u2192");
        styleAccentButton(nameNextButton);

        nGbc.gridx = 0; nGbc.gridy = 0; nameContent.add(nameLabel, nGbc);
        nGbc.gridx = 0; nGbc.gridy = 1; nameContent.add(nameField, nGbc);
        nGbc.gridx = 0; nGbc.gridy = 2; nGbc.insets = new Insets(25, 10, 10, 10);
        nameContent.add(nameNextButton, nGbc);

        namePanel.add(nameContent, BorderLayout.CENTER);

        // ===================== SPECIES STEP =====================
        JPanel speciesPanel = new JPanel(new BorderLayout());
        speciesPanel.setBackground(BG_DARK);

        JPanel speciesHeaderWrap = new JPanel(new BorderLayout());
        speciesHeaderWrap.setBackground(BG_DARK);
        speciesHeaderWrap.add(buildWizardTopBar(() -> innerCardLayout.show(innerCards, "NAME"), outerCardLayout, outerCards, editModeHolder, saveChangeButtons), BorderLayout.NORTH);

        JLabel speciesTitleLabel = new JLabel("Species:");
        speciesTitleLabel.setForeground(GOLD);
        speciesTitleLabel.setFont(new Font("Serif", Font.BOLD, 24));
        speciesTitleLabel.setBorder(new EmptyBorder(0, 25, 10, 0));
        speciesHeaderWrap.add(speciesTitleLabel, BorderLayout.SOUTH);
        speciesPanel.add(speciesHeaderWrap, BorderLayout.NORTH);

        TranslucentPanel speciesInfoPanel = new TranslucentPanel(new Color(255, 255, 255, 25));
        speciesInfoPanel.setLayout(new BorderLayout());
        speciesInfoPanel.setPreferredSize(new Dimension(260, 100));
        speciesInfoPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel speciesInfoTitle = new JLabel("Select a species");
        speciesInfoTitle.setForeground(GOLD);
        speciesInfoTitle.setFont(new Font("Serif", Font.BOLD, 20));

        JTextArea speciesInfoText = new JTextArea("Click the (i) next to a species to see its details here.");
        styleInfoTextArea(speciesInfoText);

        JPanel speciesInfoTop = new JPanel(new BorderLayout());
        speciesInfoTop.setOpaque(false);
        speciesInfoTop.add(speciesInfoTitle, BorderLayout.NORTH);
        speciesInfoTop.add(speciesInfoText, BorderLayout.CENTER);
        speciesInfoPanel.add(speciesInfoTop, BorderLayout.NORTH);

        JPanel speciesInfoWrap = new JPanel(new BorderLayout());
        speciesInfoWrap.setBackground(BG_DARK);
        speciesInfoWrap.setBorder(new EmptyBorder(10, 10, 10, 15));
        speciesInfoWrap.add(speciesInfoPanel, BorderLayout.CENTER);
        speciesPanel.add(speciesInfoWrap, BorderLayout.EAST);

        CheckListResult speciesCheckList = buildCheckListPanel(
                new ArrayList<>(GameData.SPECIES_INFO.keySet()), 1, GameData.SPECIES_INFO,
                speciesInfoTitle, speciesInfoText, true, 8, null);
        JPanel speciesListWrap = new JPanel(new BorderLayout());
        speciesListWrap.setBackground(BG_DARK);
        speciesListWrap.setBorder(new EmptyBorder(5, 25, 10, 10));
        speciesListWrap.add(speciesCheckList.component, BorderLayout.NORTH);

        JScrollPane speciesScrollPane = new JScrollPane(speciesListWrap);
        applyDarkScrollBar(speciesScrollPane);
        speciesPanel.add(speciesScrollPane, BorderLayout.CENTER);

        JButton speciesSelectButton = new JButton("Select");
        styleAccentButton(speciesSelectButton);
        speciesSelectButton.setFont(new Font("SansSerif", Font.BOLD, 16));
        speciesSelectButton.setPreferredSize(new Dimension(160, 45));

        JPanel speciesBottomBar = new JPanel(new FlowLayout(FlowLayout.CENTER));
        speciesBottomBar.setBackground(BG_DARK);
        speciesBottomBar.setBorder(new EmptyBorder(10, 0, 20, 0));
        speciesBottomBar.add(speciesSelectButton);
        speciesPanel.add(speciesBottomBar, BorderLayout.SOUTH);

        // ===================== LINEAGE STEP (skipped if species has none) =====================
        JPanel lineagePanel = new JPanel(new BorderLayout());
        lineagePanel.setBackground(BG_DARK);

        JPanel lineageHeaderWrap = new JPanel(new BorderLayout());
        lineageHeaderWrap.setBackground(BG_DARK);
        lineageHeaderWrap.add(buildWizardTopBar(() -> innerCardLayout.show(innerCards, "SPECIES"), outerCardLayout, outerCards, editModeHolder, saveChangeButtons), BorderLayout.NORTH);

        JLabel lineageTitleLabel = new JLabel("Lineage:");
        lineageTitleLabel.setForeground(GOLD);
        lineageTitleLabel.setFont(new Font("Serif", Font.BOLD, 24));
        lineageTitleLabel.setBorder(new EmptyBorder(0, 25, 10, 0));
        lineageHeaderWrap.add(lineageTitleLabel, BorderLayout.SOUTH);
        lineagePanel.add(lineageHeaderWrap, BorderLayout.NORTH);

        JPanel lineageContentWrap = new JPanel(new BorderLayout());
        lineageContentWrap.setBackground(BG_DARK);
        lineageContentWrap.setBorder(new EmptyBorder(15, 25, 10, 10));
        lineagePanel.add(lineageContentWrap, BorderLayout.CENTER);

        List<Supplier<List<String>>> lineageSupplierHolder = new ArrayList<>();
        lineageSupplierHolder.add(() -> Collections.emptyList());

        JLabel lineageMessageLabel = new JLabel(" ");
        lineageMessageLabel.setForeground(ACCENT_HOVER);
        lineageMessageLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));

        Runnable rebuildLineage = () -> {
            lineageContentWrap.removeAll();
            String[] options = GameData.SPECIES_LINEAGES.get(draft.species);
            if (options != null) {
                CheckListResult lcr = buildCheckListPanel(
                        Arrays.asList(options), 1, null, null, null, false, Math.min(6, options.length), null);
                JPanel col = new JPanel();
                col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
                col.setBackground(BG_DARK);
                lcr.component.setAlignmentX(Component.LEFT_ALIGNMENT);
                lineageMessageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                col.add(lcr.component);
                col.add(Box.createRigidArea(new Dimension(0, 8)));
                col.add(lineageMessageLabel);
                lineageContentWrap.add(col, BorderLayout.NORTH);
                lineageSupplierHolder.set(0, lcr.getSelected);
                if (!draft.lineage.isEmpty() && Arrays.asList(options).contains(draft.lineage)) {
                    lcr.setSelected.accept(Collections.singletonList(draft.lineage));
                }
            }
            lineageMessageLabel.setText(" ");
            lineageContentWrap.revalidate();
            lineageContentWrap.repaint();
        };

        JButton lineageSelectButton = new JButton("Select");
        styleAccentButton(lineageSelectButton);
        lineageSelectButton.setFont(new Font("SansSerif", Font.BOLD, 16));
        lineageSelectButton.setPreferredSize(new Dimension(160, 45));

        JPanel lineageBottomBar = new JPanel(new FlowLayout(FlowLayout.CENTER));
        lineageBottomBar.setBackground(BG_DARK);
        lineageBottomBar.setBorder(new EmptyBorder(10, 0, 20, 0));
        lineageBottomBar.add(lineageSelectButton);
        lineagePanel.add(lineageBottomBar, BorderLayout.SOUTH);

        lineageSelectButton.addActionListener(e -> {
            List<String> selLineage = lineageSupplierHolder.get(0).get();
            if (selLineage.isEmpty()) {
                lineageMessageLabel.setText("Please select a lineage before continuing.");
            } else {
                draft.lineage = selLineage.get(0);
                touchedLineage[0] = true;
                segSpeciesOk[0] = true;
                refreshSidebar.run();
                innerCardLayout.show(innerCards, "CLASS");
            }
        });

        speciesSelectButton.addActionListener(e -> {
            List<String> selSpecies = speciesCheckList.getSelected.get();
            if (selSpecies.isEmpty()) {
                speciesInfoTitle.setText("No species selected");
                speciesInfoText.setText("Please select a species before continuing.");
            } else {
                String newSpecies = selSpecies.get(0);
                boolean speciesChanged = !newSpecies.equals(draft.species);
                if (speciesChanged) draft.lineage = "";
                draft.species = newSpecies;
                touchedSpecies[0] = true;
                refreshSidebar.run();
                if (GameData.SPECIES_LINEAGES.containsKey(draft.species)) {
                    if (speciesChanged) segSpeciesOk[0] = false;
                    rebuildLineage.run();
                    innerCardLayout.show(innerCards, "LINEAGE");
                } else {
                    segSpeciesOk[0] = true;
                    innerCardLayout.show(innerCards, "CLASS");
                }
            }
        });

        // ===================== CLASS STEP =====================
        JPanel classPanel = new JPanel(new BorderLayout());
        classPanel.setBackground(BG_DARK);

        JPanel classHeaderWrap = new JPanel(new BorderLayout());
        classHeaderWrap.setBackground(BG_DARK);
        classHeaderWrap.add(buildWizardTopBar(() -> innerCardLayout.show(innerCards,
                GameData.SPECIES_LINEAGES.containsKey(draft.species) ? "LINEAGE" : "SPECIES"), outerCardLayout, outerCards, editModeHolder, saveChangeButtons), BorderLayout.NORTH);

        JLabel classesTitleLabel = new JLabel("Classes:");
        classesTitleLabel.setForeground(GOLD);
        classesTitleLabel.setFont(new Font("Serif", Font.BOLD, 24));
        classesTitleLabel.setBorder(new EmptyBorder(0, 25, 10, 0));
        classHeaderWrap.add(classesTitleLabel, BorderLayout.SOUTH);
        classPanel.add(classHeaderWrap, BorderLayout.NORTH);

        TranslucentPanel classInfoPanel = new TranslucentPanel(new Color(255, 255, 255, 25));
        classInfoPanel.setLayout(new BorderLayout());
        classInfoPanel.setPreferredSize(new Dimension(260, 100));
        classInfoPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel classInfoTitle = new JLabel("Select a class");
        classInfoTitle.setForeground(GOLD);
        classInfoTitle.setFont(new Font("Serif", Font.BOLD, 20));

        JTextArea classInfoText = new JTextArea("Click the (i) next to a class to see its details here.");
        styleInfoTextArea(classInfoText);

        JPanel classInfoTop = new JPanel(new BorderLayout());
        classInfoTop.setOpaque(false);
        classInfoTop.add(classInfoTitle, BorderLayout.NORTH);
        classInfoTop.add(classInfoText, BorderLayout.CENTER);
        classInfoPanel.add(classInfoTop, BorderLayout.NORTH);

        JPanel classInfoWrap = new JPanel(new BorderLayout());
        classInfoWrap.setBackground(BG_DARK);
        classInfoWrap.setBorder(new EmptyBorder(10, 10, 10, 15));
        classInfoWrap.add(classInfoPanel, BorderLayout.CENTER);
        classPanel.add(classInfoWrap, BorderLayout.EAST);

        Map<String, String> classInfoMap = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> entry : GameData.CLASS_INFO.entrySet()) {
            String hitDice = entry.getValue()[0];
            String description = entry.getValue()[1];
            classInfoMap.put(entry.getKey(), ("N/A".equals(hitDice) ? "" : "Hit Dice: " + hitDice + "\n\n") + description);
        }
        List<String> classNames = new ArrayList<>(GameData.CLASS_INFO.keySet());

        CheckListResult classCheckList = buildCheckListPanel(
                classNames, 1, classInfoMap, classInfoTitle, classInfoText, true, 8, null);
        JPanel classListWrap = new JPanel(new BorderLayout());
        classListWrap.setBackground(BG_DARK);
        classListWrap.setBorder(new EmptyBorder(5, 25, 10, 10));
        classListWrap.add(classCheckList.component, BorderLayout.NORTH);

        JScrollPane classScrollPane = new JScrollPane(classListWrap);
        applyDarkScrollBar(classScrollPane);
        classPanel.add(classScrollPane, BorderLayout.CENTER);

        JButton classSelectButton = new JButton("Select");
        styleAccentButton(classSelectButton);
        classSelectButton.setFont(new Font("SansSerif", Font.BOLD, 16));
        classSelectButton.setPreferredSize(new Dimension(160, 45));

        JPanel classBottomBar = new JPanel(new FlowLayout(FlowLayout.CENTER));
        classBottomBar.setBackground(BG_DARK);
        classBottomBar.setBorder(new EmptyBorder(10, 0, 20, 0));
        classBottomBar.add(classSelectButton);
        classPanel.add(classBottomBar, BorderLayout.SOUTH);

        // ===================== ABILITY SCORES STEP =====================
        JPanel statsPanel = new JPanel(new BorderLayout());
        statsPanel.setBackground(BG_DARK);

        JPanel statsHeaderWrap = new JPanel(new BorderLayout());
        statsHeaderWrap.setBackground(BG_DARK);
        statsHeaderWrap.add(buildWizardTopBar(() -> innerCardLayout.show(innerCards, "CLASS"), outerCardLayout, outerCards, editModeHolder, saveChangeButtons), BorderLayout.NORTH);

        JLabel statsTitleLabel = new JLabel("Ability Scores:");
        statsTitleLabel.setForeground(GOLD);
        statsTitleLabel.setFont(new Font("Serif", Font.BOLD, 24));
        statsTitleLabel.setBorder(new EmptyBorder(0, 25, 10, 0));
        statsHeaderWrap.add(statsTitleLabel, BorderLayout.SOUTH);
        statsPanel.add(statsHeaderWrap, BorderLayout.NORTH);

        TranslucentPanel statsInfoPanel = new TranslucentPanel(new Color(255, 255, 255, 25));
        statsInfoPanel.setLayout(new BorderLayout());
        statsInfoPanel.setPreferredSize(new Dimension(260, 100));
        statsInfoPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel statsInfoTitle = new JLabel("Ability Scores");
        statsInfoTitle.setForeground(GOLD);
        statsInfoTitle.setFont(new Font("Serif", Font.BOLD, 20));

        JTextArea statsInfoText = new JTextArea("Click the (i) next to a stat or method to see its details here.");
        styleInfoTextArea(statsInfoText);

        JPanel statsInfoTop = new JPanel(new BorderLayout());
        statsInfoTop.setOpaque(false);
        statsInfoTop.add(statsInfoTitle, BorderLayout.NORTH);
        statsInfoTop.add(statsInfoText, BorderLayout.CENTER);
        statsInfoPanel.add(statsInfoTop, BorderLayout.NORTH);

        JPanel statsInfoWrap = new JPanel(new BorderLayout());
        statsInfoWrap.setBackground(BG_DARK);
        statsInfoWrap.setBorder(new EmptyBorder(10, 10, 10, 15));
        statsInfoWrap.add(statsInfoPanel, BorderLayout.CENTER);
        statsPanel.add(statsInfoWrap, BorderLayout.EAST);

        JPanel statsCenter = new JPanel(new BorderLayout());
        statsCenter.setBackground(BG_DARK);
        statsCenter.setBorder(new EmptyBorder(10, 25, 10, 10));

        JPanel methodRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        methodRow.setBackground(BG_DARK);

        String[] selectedMethodHolder = new String[1];

        CardLayout methodCardLayout = new CardLayout();
        JPanel methodCards = new JPanel(methodCardLayout);
        methodCards.setBackground(BG_DARK);

        JPanel promptPanel = new JPanel(new BorderLayout());
        promptPanel.setBackground(BG_DARK);
        JLabel promptLabel = new JLabel("Select a method above to generate your ability scores.");
        promptLabel.setForeground(TEXT_MUTED);
        promptLabel.setFont(new Font("SansSerif", Font.PLAIN, 16));
        promptLabel.setBorder(new EmptyBorder(15, 0, 0, 0));
        promptPanel.add(promptLabel, BorderLayout.NORTH);
        methodCards.add(promptPanel, "PROMPT");

        // ---- Point Buy sub-panel ----
        JPanel pointBuyPanel = new JPanel();
        pointBuyPanel.setLayout(new BoxLayout(pointBuyPanel, BoxLayout.Y_AXIS));
        pointBuyPanel.setBackground(BG_DARK);
        pointBuyPanel.setBorder(new EmptyBorder(10, 0, 0, 0));

        JLabel pointsRemainingLabel = new JLabel("Points Remaining: 27");
        pointsRemainingLabel.setForeground(GOLD);
        pointsRemainingLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        pointsRemainingLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        pointBuyPanel.add(pointsRemainingLabel);
        pointBuyPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        JSpinner[] pointBuySpinners = new JSpinner[6];
        JLabel[] pointBuyCostLabels = new JLabel[6];
        int[] pointBuyPrevValues = new int[6];
        Arrays.fill(pointBuyPrevValues, 8);

        for (int i = 0; i < 6; i++) {
            String stat = GameData.STAT_ORDER[i];
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
            row.setBackground(BG_DARK);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel statLbl = new JLabel(stat);
            statLbl.setForeground(TEXT_LIGHT);
            statLbl.setFont(new Font("SansSerif", Font.BOLD, 15));
            statLbl.setPreferredSize(new Dimension(45, 20));

            JButton infoBtn = makeInfoButton(stat, statsInfoTitle, statsInfoText, GameData.STAT_INFO.get(stat));
            infoBtn.setPreferredSize(new Dimension(24, 24));

            JSpinner spinner = new JSpinner(new SpinnerNumberModel(8, 8, 15, 1));
            styleSpinner(spinner);

            JLabel costLbl = new JLabel("Cost: 0");
            costLbl.setForeground(TEXT_MUTED);
            costLbl.setFont(new Font("SansSerif", Font.PLAIN, 14));

            final int idx = i;
            spinner.addChangeListener(ev -> {
                int newVal = (Integer) spinner.getValue();
                int[] trial = pointBuyPrevValues.clone();
                trial[idx] = newVal;
                int totalCost = 0;
                for (int v : trial) totalCost += GameData.POINT_BUY_COST[v - 8];
                if (totalCost > 27) {
                    spinner.setValue(pointBuyPrevValues[idx]);
                    return;
                }
                pointBuyPrevValues[idx] = newVal;
                costLbl.setText("Cost: " + GameData.POINT_BUY_COST[newVal - 8]);
                pointsRemainingLabel.setText("Points Remaining: " + (27 - totalCost));
            });

            row.add(statLbl);
            row.add(infoBtn);
            row.add(spinner);
            row.add(costLbl);
            pointBuyPanel.add(row);

            pointBuySpinners[i] = spinner;
            pointBuyCostLabels[i] = costLbl;
        }

        JPanel pointBuyWrap = new JPanel(new BorderLayout());
        pointBuyWrap.setBackground(BG_DARK);
        pointBuyWrap.add(pointBuyPanel, BorderLayout.NORTH);
        methodCards.add(pointBuyWrap, "POINT_BUY");

        // ---- Random Stat Generation sub-panel ----
        JPanel randomPanel = new JPanel();
        randomPanel.setLayout(new BoxLayout(randomPanel, BoxLayout.Y_AXIS));
        randomPanel.setBackground(BG_DARK);
        randomPanel.setBorder(new EmptyBorder(10, 0, 0, 0));

        JCheckBox fullyRandomCheck = new JCheckBox("Fully Random (ignore priority)");
        styleCheckBox(fullyRandomCheck);
        fullyRandomCheck.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel priorityRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        priorityRow.setBackground(BG_DARK);
        priorityRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel highLabel = new JLabel("Highest Priority:");
        highLabel.setForeground(TEXT_LIGHT);
        JComboBox<String> highestCombo = new JComboBox<>(GameData.STAT_ORDER);
        styleCombo(highestCombo);

        JLabel lowLabel = new JLabel("Lowest Priority:");
        lowLabel.setForeground(TEXT_LIGHT);
        JComboBox<String> lowestCombo = new JComboBox<>(GameData.STAT_ORDER);
        styleCombo(lowestCombo);
        lowestCombo.setSelectedIndex(GameData.STAT_ORDER.length - 1);

        priorityRow.add(highLabel);
        priorityRow.add(highestCombo);
        priorityRow.add(Box.createRigidArea(new Dimension(20, 0)));
        priorityRow.add(lowLabel);
        priorityRow.add(lowestCombo);

        fullyRandomCheck.addActionListener(e -> {
            boolean disable = fullyRandomCheck.isSelected();
            highestCombo.setEnabled(!disable);
            lowestCombo.setEnabled(!disable);
        });

        JButton rollButton = new JButton("Roll");
        styleAccentButton(rollButton);
        rollButton.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel randomMessageLabel = new JLabel(" ");
        randomMessageLabel.setFont(new Font("SansSerif", Font.ITALIC, 14));
        randomMessageLabel.setForeground(TEXT_MUTED);
        randomMessageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel randomSumLabel = new JLabel("Total: \u2014");
        randomSumLabel.setForeground(TEXT_LIGHT);
        randomSumLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        randomSumLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel randomResultsPanel = new JPanel();
        randomResultsPanel.setLayout(new BoxLayout(randomResultsPanel, BoxLayout.Y_AXIS));
        randomResultsPanel.setBackground(BG_DARK);
        randomResultsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        Map<String, JLabel> randomValueLabels = new LinkedHashMap<>();
        Map<String, Integer> randomCurrentValues = new LinkedHashMap<>();

        for (String stat : GameData.STAT_ORDER) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
            row.setBackground(BG_DARK);
            JLabel statLbl = new JLabel(stat);
            statLbl.setForeground(TEXT_LIGHT);
            statLbl.setFont(new Font("SansSerif", Font.BOLD, 15));
            statLbl.setPreferredSize(new Dimension(45, 20));
            JButton infoBtn = makeInfoButton(stat, statsInfoTitle, statsInfoText, GameData.STAT_INFO.get(stat));
            infoBtn.setPreferredSize(new Dimension(24, 24));
            JLabel valueLbl = new JLabel("\u2014");
            valueLbl.setForeground(GOLD);
            valueLbl.setFont(new Font("SansSerif", Font.BOLD, 16));
            row.add(statLbl);
            row.add(infoBtn);
            row.add(valueLbl);
            randomResultsPanel.add(row);
            randomValueLabels.put(stat, valueLbl);
        }

        rollButton.addActionListener(e -> {
            if (!fullyRandomCheck.isSelected() && highestCombo.getSelectedItem().equals(lowestCombo.getSelectedItem())) {
                randomMessageLabel.setForeground(ACCENT_HOVER);
                randomMessageLabel.setText("Highest and lowest priority must be different stats.");
                return;
            }
            Random rnd = new Random();
            List<Integer> rolls = new ArrayList<>();
            for (int i = 0; i < 6; i++) rolls.add(roll4d6DropLowest(rnd));

            Map<String, Integer> assignment = new LinkedHashMap<>();
            if (fullyRandomCheck.isSelected()) {
                List<Integer> shuffled = new ArrayList<>(rolls);
                Collections.shuffle(shuffled, rnd);
                for (int i = 0; i < 6; i++) assignment.put(GameData.STAT_ORDER[i], shuffled.get(i));
            } else {
                String highStat = (String) highestCombo.getSelectedItem();
                String lowStat = (String) lowestCombo.getSelectedItem();
                List<Integer> remaining = new ArrayList<>(rolls);
                int maxVal = Collections.max(remaining);
                remaining.remove(Integer.valueOf(maxVal));
                int minVal = Collections.min(remaining);
                remaining.remove(Integer.valueOf(minVal));
                Collections.shuffle(remaining, rnd);
                assignment.put(highStat, maxVal);
                assignment.put(lowStat, minVal);
                int ri = 0;
                for (String stat : GameData.STAT_ORDER) {
                    if (!assignment.containsKey(stat)) {
                        assignment.put(stat, remaining.get(ri++));
                    }
                }
            }

            int sum = 0;
            for (String stat : GameData.STAT_ORDER) {
                int v = assignment.get(stat);
                randomValueLabels.get(stat).setText(String.valueOf(v));
                randomCurrentValues.put(stat, v);
                sum += v;
            }
            randomSumLabel.setText("Total: " + sum);
            if (sum <= 65) {
                randomMessageLabel.setForeground(GOLD);
                randomMessageLabel.setText("Total is low \u2014 you may reroll.");
                rollButton.setText("Reroll");
                rollButton.setEnabled(true);
            } else {
                randomMessageLabel.setForeground(TEXT_MUTED);
                randomMessageLabel.setText("Scores locked in.");
                rollButton.setEnabled(false);
            }
        });

        randomPanel.add(fullyRandomCheck);
        randomPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        randomPanel.add(priorityRow);
        randomPanel.add(Box.createRigidArea(new Dimension(0, 12)));
        randomPanel.add(rollButton);
        randomPanel.add(Box.createRigidArea(new Dimension(0, 6)));
        randomPanel.add(randomMessageLabel);
        randomPanel.add(Box.createRigidArea(new Dimension(0, 6)));
        randomPanel.add(randomSumLabel);
        randomPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        randomPanel.add(randomResultsPanel);

        JPanel randomWrap = new JPanel(new BorderLayout());
        randomWrap.setBackground(BG_DARK);
        randomWrap.add(randomPanel, BorderLayout.NORTH);
        methodCards.add(randomWrap, "RANDOM");

        // ---- Custom sub-panel ----
        JPanel customPanel = new JPanel();
        customPanel.setLayout(new BoxLayout(customPanel, BoxLayout.Y_AXIS));
        customPanel.setBackground(BG_DARK);
        customPanel.setBorder(new EmptyBorder(10, 0, 0, 0));

        JSpinner[] customSpinners = new JSpinner[6];
        for (int i = 0; i < 6; i++) {
            String stat = GameData.STAT_ORDER[i];
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
            row.setBackground(BG_DARK);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel statLbl = new JLabel(stat);
            statLbl.setForeground(TEXT_LIGHT);
            statLbl.setFont(new Font("SansSerif", Font.BOLD, 15));
            statLbl.setPreferredSize(new Dimension(45, 20));

            JButton infoBtn = makeInfoButton(stat, statsInfoTitle, statsInfoText, GameData.STAT_INFO.get(stat));
            infoBtn.setPreferredSize(new Dimension(24, 24));

            JSpinner spinner = new JSpinner(new SpinnerNumberModel(10, 1, 20, 1));
            styleSpinner(spinner);

            row.add(statLbl);
            row.add(infoBtn);
            row.add(spinner);
            customPanel.add(row);

            customSpinners[i] = spinner;
        }

        JPanel customWrap = new JPanel(new BorderLayout());
        customWrap.setBackground(BG_DARK);
        customWrap.add(customPanel, BorderLayout.NORTH);
        methodCards.add(customWrap, "CUSTOM");

        Map<String, String> methodInfoMap = new LinkedHashMap<>(GameData.GEN_TYPE_INFO);
        CheckListResult methodCheckList = buildCheckListPanel(
                Arrays.asList("Point Buy", "Random Stat Generation", "Custom"), 1, methodInfoMap,
                statsInfoTitle, statsInfoText, false, 3,
                (selList) -> {
                    if (selList.isEmpty()) return;
                    String method = selList.get(0);
                    selectedMethodHolder[0] = method;
                    String key = method.equals("Point Buy") ? "POINT_BUY" : (method.equals("Custom") ? "CUSTOM" : "RANDOM");
                    methodCardLayout.show(methodCards, key);
                });
        methodRow.add(methodCheckList.component);

        statsCenter.add(methodRow, BorderLayout.NORTH);
        statsCenter.add(methodCards, BorderLayout.CENTER);
        statsPanel.add(statsCenter, BorderLayout.CENTER);

        JButton statsNextButton = new JButton("Next \u2192");
        styleAccentButton(statsNextButton);

        JPanel statsBottomBar = new JPanel(new FlowLayout(FlowLayout.CENTER));
        statsBottomBar.setBackground(BG_DARK);
        statsBottomBar.setBorder(new EmptyBorder(10, 0, 20, 0));
        statsBottomBar.add(statsNextButton);
        statsPanel.add(statsBottomBar, BorderLayout.SOUTH);

        // ===================== BACKGROUND STEP =====================
        JPanel backgroundPanel = new JPanel(new BorderLayout());
        backgroundPanel.setBackground(BG_DARK);

        JPanel backgroundHeaderWrap = new JPanel(new BorderLayout());
        backgroundHeaderWrap.setBackground(BG_DARK);
        backgroundHeaderWrap.add(buildWizardTopBar(() -> innerCardLayout.show(innerCards, "STATS"), outerCardLayout, outerCards, editModeHolder, saveChangeButtons), BorderLayout.NORTH);

        JLabel backgroundsTitleLabel = new JLabel("Backgrounds:");
        backgroundsTitleLabel.setForeground(GOLD);
        backgroundsTitleLabel.setFont(new Font("Serif", Font.BOLD, 24));
        backgroundsTitleLabel.setBorder(new EmptyBorder(0, 25, 10, 0));
        backgroundHeaderWrap.add(backgroundsTitleLabel, BorderLayout.SOUTH);
        backgroundPanel.add(backgroundHeaderWrap, BorderLayout.NORTH);

        TranslucentPanel backgroundInfoPanel = new TranslucentPanel(new Color(255, 255, 255, 25));
        backgroundInfoPanel.setLayout(new BorderLayout());
        backgroundInfoPanel.setPreferredSize(new Dimension(260, 100));
        backgroundInfoPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel backgroundInfoTitle = new JLabel("Select a background");
        backgroundInfoTitle.setForeground(GOLD);
        backgroundInfoTitle.setFont(new Font("Serif", Font.BOLD, 20));

        JTextArea backgroundInfoText = new JTextArea("Click the (i) next to a background to see its details here.");
        styleInfoTextArea(backgroundInfoText);

        JPanel backgroundInfoTop = new JPanel(new BorderLayout());
        backgroundInfoTop.setOpaque(false);
        backgroundInfoTop.add(backgroundInfoTitle, BorderLayout.NORTH);
        backgroundInfoTop.add(backgroundInfoText, BorderLayout.CENTER);
        backgroundInfoPanel.add(backgroundInfoTop, BorderLayout.NORTH);

        JPanel backgroundInfoWrap = new JPanel(new BorderLayout());
        backgroundInfoWrap.setBackground(BG_DARK);
        backgroundInfoWrap.setBorder(new EmptyBorder(10, 10, 10, 15));
        backgroundInfoWrap.add(backgroundInfoPanel, BorderLayout.CENTER);
        backgroundPanel.add(backgroundInfoWrap, BorderLayout.EAST);

        CheckListResult backgroundCheckList = buildCheckListPanel(
                new ArrayList<>(GameData.BACKGROUND_INFO.keySet()), 1, GameData.BACKGROUND_INFO,
                backgroundInfoTitle, backgroundInfoText, true, 8, null);
        JPanel backgroundListWrap = new JPanel(new BorderLayout());
        backgroundListWrap.setBackground(BG_DARK);
        backgroundListWrap.setBorder(new EmptyBorder(5, 25, 10, 10));
        backgroundListWrap.add(backgroundCheckList.component, BorderLayout.NORTH);

        JScrollPane backgroundScrollPane = new JScrollPane(backgroundListWrap);
        applyDarkScrollBar(backgroundScrollPane);
        backgroundPanel.add(backgroundScrollPane, BorderLayout.CENTER);

        JButton backgroundSelectButton = new JButton("Select");
        styleAccentButton(backgroundSelectButton);
        backgroundSelectButton.setFont(new Font("SansSerif", Font.BOLD, 16));
        backgroundSelectButton.setPreferredSize(new Dimension(160, 45));

        JPanel backgroundBottomBar = new JPanel(new FlowLayout(FlowLayout.CENTER));
        backgroundBottomBar.setBackground(BG_DARK);
        backgroundBottomBar.setBorder(new EmptyBorder(10, 0, 20, 0));
        backgroundBottomBar.add(backgroundSelectButton);
        backgroundPanel.add(backgroundBottomBar, BorderLayout.SOUTH);

        // ===================== ABILITY SCORE INCREASE STEP =====================
        JPanel asiPanel = new JPanel(new BorderLayout());
        asiPanel.setBackground(BG_DARK);

        JPanel asiHeaderWrap = new JPanel(new BorderLayout());
        asiHeaderWrap.setBackground(BG_DARK);
        asiHeaderWrap.add(buildWizardTopBar(() -> innerCardLayout.show(innerCards, "BACKGROUND"), outerCardLayout, outerCards, editModeHolder, saveChangeButtons), BorderLayout.NORTH);

        JLabel asiTitleLabel = new JLabel("Ability Score Increase:");
        asiTitleLabel.setForeground(GOLD);
        asiTitleLabel.setFont(new Font("Serif", Font.BOLD, 24));
        asiTitleLabel.setBorder(new EmptyBorder(0, 25, 10, 0));
        asiHeaderWrap.add(asiTitleLabel, BorderLayout.SOUTH);
        asiPanel.add(asiHeaderWrap, BorderLayout.NORTH);

        TranslucentPanel asiInfoPanel = new TranslucentPanel(new Color(255, 255, 255, 25));
        asiInfoPanel.setLayout(new BorderLayout());
        asiInfoPanel.setPreferredSize(new Dimension(260, 100));
        asiInfoPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel asiInfoTitle = new JLabel("Ability Score Increase");
        asiInfoTitle.setForeground(GOLD);
        asiInfoTitle.setFont(new Font("Serif", Font.BOLD, 20));

        JTextArea asiInfoText = new JTextArea("Choose how to boost your background's key stats, or skip entirely.");
        styleInfoTextArea(asiInfoText);

        JPanel asiInfoTop = new JPanel(new BorderLayout());
        asiInfoTop.setOpaque(false);
        asiInfoTop.add(asiInfoTitle, BorderLayout.NORTH);
        asiInfoTop.add(asiInfoText, BorderLayout.CENTER);
        asiInfoPanel.add(asiInfoTop, BorderLayout.NORTH);

        JPanel asiInfoWrap = new JPanel(new BorderLayout());
        asiInfoWrap.setBackground(BG_DARK);
        asiInfoWrap.setBorder(new EmptyBorder(10, 10, 10, 15));
        asiInfoWrap.add(asiInfoPanel, BorderLayout.CENTER);
        asiPanel.add(asiInfoWrap, BorderLayout.EAST);

        JPanel asiCenter = new JPanel(new BorderLayout());
        asiCenter.setBackground(BG_DARK);
        asiCenter.setBorder(new EmptyBorder(10, 25, 10, 10));

        JCheckBox skipCheck = new JCheckBox("Skip ability score increases");
        styleCheckBox(skipCheck);

        JPanel asiModeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        asiModeRow.setBackground(BG_DARK);

        String[][] availableStatsHolder = new String[1][];
        List<JComboBox<String>> activeCombos = new ArrayList<>();
        String[] selectedIncreaseHolder = new String[1];

        JPanel asiDynamicContentWrap = new JPanel(new BorderLayout());
        asiDynamicContentWrap.setBackground(BG_DARK);

        JLabel asiPromptLabel = new JLabel("Select an increase option above, or check skip.");
        asiPromptLabel.setForeground(TEXT_MUTED);
        asiPromptLabel.setFont(new Font("SansSerif", Font.PLAIN, 16));
        asiDynamicContentWrap.add(asiPromptLabel, BorderLayout.NORTH);

        Map<String, String> increaseModeInfo = new LinkedHashMap<>();
        increaseModeInfo.put("+2 / +1", "Increase one of your background's key stats by 2 and a different one by 1.");
        increaseModeInfo.put("+1 / +1 / +1", "Increase three of your background's key stats by 1 each.");

        CheckListResult increaseModeCheckList = buildCheckListPanel(
                Arrays.asList("+2 / +1", "+1 / +1 / +1"), 1, increaseModeInfo,
                asiInfoTitle, asiInfoText, false, 2,
                (selList) -> {
                    if (skipCheck.isSelected() || selList.isEmpty()) return;
                    String modeName = selList.get(0);
                    String modeKey = modeName.equals("+2 / +1") ? "PLUS2PLUS1" : "PLUS1X3";
                    selectedIncreaseHolder[0] = modeKey;

                    String[] avail = availableStatsHolder[0] != null ? availableStatsHolder[0] : GameData.STAT_ORDER;
                    JPanel built = buildIncreasePickerPanel(modeKey, avail, activeCombos);
                    asiDynamicContentWrap.removeAll();
                    asiDynamicContentWrap.add(built, BorderLayout.NORTH);
                    asiDynamicContentWrap.revalidate();
                    asiDynamicContentWrap.repaint();
                });
        asiModeRow.add(increaseModeCheckList.component);

        skipCheck.addActionListener(e -> {
            boolean skip = skipCheck.isSelected();
            increaseModeCheckList.setEnabled.accept(!skip);
            activeCombos.clear();
            asiDynamicContentWrap.removeAll();
            if (skip) {
                increaseModeCheckList.reset.run();
                selectedIncreaseHolder[0] = "SKIP";
                JLabel skipLbl = new JLabel("No ability score increases will be applied.");
                skipLbl.setForeground(TEXT_MUTED);
                skipLbl.setFont(new Font("SansSerif", Font.PLAIN, 16));
                asiDynamicContentWrap.add(skipLbl, BorderLayout.NORTH);
            } else {
                selectedIncreaseHolder[0] = null;
                asiDynamicContentWrap.add(asiPromptLabel, BorderLayout.NORTH);
            }
            asiDynamicContentWrap.revalidate();
            asiDynamicContentWrap.repaint();
        });

        JPanel asiTop = new JPanel();
        asiTop.setLayout(new BoxLayout(asiTop, BoxLayout.Y_AXIS));
        asiTop.setBackground(BG_DARK);
        skipCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        asiModeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        asiTop.add(skipCheck);
        asiTop.add(Box.createRigidArea(new Dimension(0, 8)));
        asiTop.add(asiModeRow);

        asiCenter.add(asiTop, BorderLayout.NORTH);
        asiCenter.add(asiDynamicContentWrap, BorderLayout.CENTER);
        asiPanel.add(asiCenter, BorderLayout.CENTER);

        JButton asiNextButton = new JButton("Next \u2192");
        styleAccentButton(asiNextButton);

        JPanel asiBottomBar = new JPanel(new FlowLayout(FlowLayout.CENTER));
        asiBottomBar.setBackground(BG_DARK);
        asiBottomBar.setBorder(new EmptyBorder(10, 0, 20, 0));
        asiBottomBar.add(asiNextButton);
        asiPanel.add(asiBottomBar, BorderLayout.SOUTH);

        // ===================== BACKGROUND SKILLS STEP =====================
        JPanel bgSkillsPanel = new JPanel(new BorderLayout());
        bgSkillsPanel.setBackground(BG_DARK);

        JPanel bgSkillsHeaderWrap = new JPanel(new BorderLayout());
        bgSkillsHeaderWrap.setBackground(BG_DARK);
        bgSkillsHeaderWrap.add(buildWizardTopBar(() -> innerCardLayout.show(innerCards, "ASI"), outerCardLayout, outerCards, editModeHolder, saveChangeButtons), BorderLayout.NORTH);

        JLabel bgSkillsTitleLabel = new JLabel("Background Skill Proficiencies:");
        bgSkillsTitleLabel.setForeground(GOLD);
        bgSkillsTitleLabel.setFont(new Font("Serif", Font.BOLD, 24));
        bgSkillsTitleLabel.setBorder(new EmptyBorder(0, 25, 10, 0));
        bgSkillsHeaderWrap.add(bgSkillsTitleLabel, BorderLayout.SOUTH);
        bgSkillsPanel.add(bgSkillsHeaderWrap, BorderLayout.NORTH);

        TranslucentPanel bgSkillsInfoPanel = new TranslucentPanel(new Color(255, 255, 255, 25));
        bgSkillsInfoPanel.setLayout(new BorderLayout());
        bgSkillsInfoPanel.setPreferredSize(new Dimension(260, 100));
        bgSkillsInfoPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel bgSkillsInfoTitle = new JLabel("Skill Proficiencies");
        bgSkillsInfoTitle.setForeground(GOLD);
        bgSkillsInfoTitle.setFont(new Font("Serif", Font.BOLD, 20));

        JTextArea bgSkillsInfoText = new JTextArea("Click the (i) next to a skill to see what it means.");
        styleInfoTextArea(bgSkillsInfoText);

        JPanel bgSkillsInfoTop = new JPanel(new BorderLayout());
        bgSkillsInfoTop.setOpaque(false);
        bgSkillsInfoTop.add(bgSkillsInfoTitle, BorderLayout.NORTH);
        bgSkillsInfoTop.add(bgSkillsInfoText, BorderLayout.CENTER);
        bgSkillsInfoPanel.add(bgSkillsInfoTop, BorderLayout.NORTH);

        JPanel bgSkillsInfoWrap = new JPanel(new BorderLayout());
        bgSkillsInfoWrap.setBackground(BG_DARK);
        bgSkillsInfoWrap.setBorder(new EmptyBorder(10, 10, 10, 15));
        bgSkillsInfoWrap.add(bgSkillsInfoPanel, BorderLayout.CENTER);
        bgSkillsPanel.add(bgSkillsInfoWrap, BorderLayout.EAST);

        JPanel bgSkillsContentWrap = new JPanel(new BorderLayout());
        bgSkillsContentWrap.setBackground(BG_DARK);
        bgSkillsContentWrap.setBorder(new EmptyBorder(15, 25, 10, 10));

        JScrollPane bgSkillsScrollPane = new JScrollPane(bgSkillsContentWrap);
        bgSkillsScrollPane.setBorder(BorderFactory.createEmptyBorder());
        bgSkillsScrollPane.getViewport().setBackground(BG_DARK);
        bgSkillsScrollPane.setBackground(BG_DARK);
        bgSkillsScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        bgSkillsPanel.add(bgSkillsScrollPane, BorderLayout.CENTER);

        List<Supplier<List<String>>> bgSkillsSupplierHolder = new ArrayList<>();
        bgSkillsSupplierHolder.add(() -> Collections.emptyList());

        JButton bgSkillsNextButton = new JButton("Next \u2192");
        styleAccentButton(bgSkillsNextButton);

        JPanel bgSkillsBottomBar = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bgSkillsBottomBar.setBackground(BG_DARK);
        bgSkillsBottomBar.setBorder(new EmptyBorder(10, 0, 20, 0));
        bgSkillsBottomBar.add(bgSkillsNextButton);
        bgSkillsPanel.add(bgSkillsBottomBar, BorderLayout.SOUTH);

        Runnable rebuildBackgroundSkills = () -> {
            bgSkillsContentWrap.removeAll();
            bgSkillsInfoTitle.setText("Skill Proficiencies");
            bgSkillsInfoText.setText("Click the (i) next to a skill to see what it means.");

            if ("Custom".equals(draft.background)) {
                List<RenderedItem> tempList = new ArrayList<>();
                DecisionItem synth = new DecisionItem("Choose two skills:", "SKILLS", 2, GameData.ALL_SKILLS, null);
                JPanel p = buildDecisionItemUI(synth, draft.characterClass, bgSkillsInfoTitle, bgSkillsInfoText, tempList);
                bgSkillsContentWrap.add(p, BorderLayout.NORTH);
                bgSkillsSupplierHolder.set(0, tempList.get(0).getSelections);
                if (!draft.backgroundSkills.isEmpty() && tempList.get(0).setSelections != null) {
                    tempList.get(0).setSelections.accept(draft.backgroundSkills);
                }
            } else {
                String[] fixed = GameData.BACKGROUND_SKILL_PROFICIENCIES.getOrDefault(draft.background, new String[0]);
                JPanel p = new JPanel();
                p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
                p.setBackground(BG_DARK);

                JLabel introLbl = new JLabel("Your background grants proficiency in:");
                introLbl.setForeground(TEXT_LIGHT);
                introLbl.setFont(new Font("SansSerif", Font.PLAIN, 16));
                introLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
                p.add(introLbl);
                p.add(Box.createRigidArea(new Dimension(0, 10)));

                for (String skill : fixed) {
                    JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
                    row.setBackground(BG_DARK);
                    row.setAlignmentX(Component.LEFT_ALIGNMENT);
                    JLabel skillLbl = new JLabel(skill);
                    skillLbl.setForeground(GOLD);
                    skillLbl.setFont(new Font("SansSerif", Font.BOLD, 16));
                    JButton infoBtn = makeInfoButton(skill, bgSkillsInfoTitle, bgSkillsInfoText, GameData.SKILL_INFO.getOrDefault(skill, ""));
                    infoBtn.setPreferredSize(new Dimension(24, 24));
                    row.add(skillLbl);
                    row.add(infoBtn);
                    p.add(row);
                }
                bgSkillsContentWrap.add(p, BorderLayout.NORTH);
                bgSkillsSupplierHolder.set(0, () -> Arrays.asList(fixed));
            }
            bgSkillsContentWrap.revalidate();
            bgSkillsContentWrap.repaint();
        };

        // ===================== CLASS DETAILS STEP =====================
        JPanel classDecisionsPanel = new JPanel(new BorderLayout());
        classDecisionsPanel.setBackground(BG_DARK);

        JPanel classDecisionsHeaderWrap = new JPanel(new BorderLayout());
        classDecisionsHeaderWrap.setBackground(BG_DARK);
        classDecisionsHeaderWrap.add(buildWizardTopBar(() -> innerCardLayout.show(innerCards, "BACKGROUND_SKILLS"), outerCardLayout, outerCards, editModeHolder, saveChangeButtons), BorderLayout.NORTH);

        JLabel classDecisionsTitleLabel = new JLabel("Class Details:");
        classDecisionsTitleLabel.setForeground(GOLD);
        classDecisionsTitleLabel.setFont(new Font("Serif", Font.BOLD, 24));
        classDecisionsTitleLabel.setBorder(new EmptyBorder(0, 25, 10, 0));
        classDecisionsHeaderWrap.add(classDecisionsTitleLabel, BorderLayout.SOUTH);
        classDecisionsPanel.add(classDecisionsHeaderWrap, BorderLayout.NORTH);

        TranslucentPanel classDecisionsInfoPanel = new TranslucentPanel(new Color(255, 255, 255, 25));
        classDecisionsInfoPanel.setLayout(new BorderLayout());
        classDecisionsInfoPanel.setPreferredSize(new Dimension(260, 100));
        classDecisionsInfoPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel classDecisionsInfoTitle = new JLabel("Class Details");
        classDecisionsInfoTitle.setForeground(GOLD);
        classDecisionsInfoTitle.setFont(new Font("Serif", Font.BOLD, 20));

        JTextArea classDecisionsInfoText = new JTextArea("Click the (i) next to a skill to see what it means.");
        styleInfoTextArea(classDecisionsInfoText);

        JPanel classDecisionsInfoTop = new JPanel(new BorderLayout());
        classDecisionsInfoTop.setOpaque(false);
        classDecisionsInfoTop.add(classDecisionsInfoTitle, BorderLayout.NORTH);
        classDecisionsInfoTop.add(classDecisionsInfoText, BorderLayout.CENTER);
        classDecisionsInfoPanel.add(classDecisionsInfoTop, BorderLayout.NORTH);

        JPanel classDecisionsInfoWrap = new JPanel(new BorderLayout());
        classDecisionsInfoWrap.setBackground(BG_DARK);
        classDecisionsInfoWrap.setBorder(new EmptyBorder(10, 10, 10, 15));
        classDecisionsInfoWrap.add(classDecisionsInfoPanel, BorderLayout.CENTER);
        classDecisionsPanel.add(classDecisionsInfoWrap, BorderLayout.EAST);

        JPanel classDecisionsContentWrap = new JPanel();
        classDecisionsContentWrap.setLayout(new BoxLayout(classDecisionsContentWrap, BoxLayout.Y_AXIS));
        classDecisionsContentWrap.setBackground(BG_DARK);
        classDecisionsContentWrap.setBorder(new EmptyBorder(15, 25, 10, 10));

        JScrollPane classDecisionsScrollPane = new JScrollPane(classDecisionsContentWrap);
        classDecisionsScrollPane.setBorder(BorderFactory.createEmptyBorder());
        classDecisionsScrollPane.getViewport().setBackground(BG_DARK);
        classDecisionsScrollPane.setBackground(BG_DARK);
        classDecisionsScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        classDecisionsPanel.add(classDecisionsScrollPane, BorderLayout.CENTER);

        List<RenderedItem> classDecisionsRendered = new ArrayList<>();

        JButton classDecisionsNextButton = new JButton("Next \u2192");
        styleAccentButton(classDecisionsNextButton);

        JPanel classDecisionsBottomBar = new JPanel(new FlowLayout(FlowLayout.CENTER));
        classDecisionsBottomBar.setBackground(BG_DARK);
        classDecisionsBottomBar.setBorder(new EmptyBorder(10, 0, 20, 0));
        classDecisionsBottomBar.add(classDecisionsNextButton);
        classDecisionsPanel.add(classDecisionsBottomBar, BorderLayout.SOUTH);

        Runnable rebuildClassDecisions = () -> {
            classDecisionsContentWrap.removeAll();
            classDecisionsRendered.clear();
            classDecisionsInfoTitle.setText("Class Details");
            classDecisionsInfoText.setText("Click the (i) next to a skill to see what it means.");

            List<DecisionItem> items = GameData.CLASS_DECISIONS.get(draft.characterClass);
            if (items == null || items.isEmpty()) {
                JLabel none = new JLabel("No additional choices for this class.");
                none.setForeground(TEXT_MUTED);
                none.setFont(new Font("SansSerif", Font.PLAIN, 16));
                none.setAlignmentX(Component.LEFT_ALIGNMENT);
                classDecisionsContentWrap.add(none);
            } else {
                for (DecisionItem item : items) {
                    JPanel itemPanel = buildDecisionItemUI(item, draft.characterClass, classDecisionsInfoTitle, classDecisionsInfoText, classDecisionsRendered);
                    itemPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
                    classDecisionsContentWrap.add(itemPanel);

                    RenderedItem justAdded = classDecisionsRendered.get(classDecisionsRendered.size() - 1);
                    List<String> prevVals = draft.classChoices.get(item.label);
                    if (prevVals != null && !prevVals.isEmpty() && justAdded.setSelections != null) {
                        justAdded.setSelections.accept(prevVals);
                    }
                }
            }
            classDecisionsContentWrap.revalidate();
            classDecisionsContentWrap.repaint();
        };

        // ===================== EQUIPMENT STEPS =====================
        List<String> bgEquipItems = new ArrayList<>();
        int[] bgGoldHolder = {0};
        List<String> classEquipItems = new ArrayList<>();
        int[] classGoldHolder = {0};

        // ---- Background Equipment: Package choice (A vs B) ----
        JPanel equipBgChoicePanel = new JPanel(new BorderLayout());
        equipBgChoicePanel.setBackground(BG_DARK);
        JPanel equipBgChoiceHeaderWrap = new JPanel(new BorderLayout());
        equipBgChoiceHeaderWrap.setBackground(BG_DARK);
        equipBgChoiceHeaderWrap.add(buildWizardTopBar(() -> innerCardLayout.show(innerCards, "CLASS_DECISIONS"), outerCardLayout, outerCards, editModeHolder, saveChangeButtons), BorderLayout.NORTH);
        JLabel equipBgChoiceTitleLabel = new JLabel("Background Equipment:");
        equipBgChoiceTitleLabel.setForeground(GOLD);
        equipBgChoiceTitleLabel.setFont(new Font("Serif", Font.BOLD, 24));
        equipBgChoiceTitleLabel.setBorder(new EmptyBorder(0, 25, 10, 0));
        equipBgChoiceHeaderWrap.add(equipBgChoiceTitleLabel, BorderLayout.SOUTH);
        equipBgChoicePanel.add(equipBgChoiceHeaderWrap, BorderLayout.NORTH);

        TranslucentPanel equipBgInfoPanel = new TranslucentPanel(new Color(255, 255, 255, 25));
        equipBgInfoPanel.setLayout(new BorderLayout());
        equipBgInfoPanel.setPreferredSize(new Dimension(280, 100));
        equipBgInfoPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        JLabel equipBgInfoTitle = new JLabel("Equipment");
        equipBgInfoTitle.setForeground(GOLD);
        equipBgInfoTitle.setFont(new Font("Serif", Font.BOLD, 20));
        JTextArea equipBgInfoText = new JTextArea("Click the (i) next to a package to see what it contains.");
        styleInfoTextArea(equipBgInfoText);
        JPanel equipBgInfoTop = new JPanel(new BorderLayout());
        equipBgInfoTop.setOpaque(false);
        equipBgInfoTop.add(equipBgInfoTitle, BorderLayout.NORTH);
        equipBgInfoTop.add(equipBgInfoText, BorderLayout.CENTER);
        equipBgInfoPanel.add(equipBgInfoTop, BorderLayout.NORTH);
        JPanel equipBgInfoWrap = new JPanel(new BorderLayout());
        equipBgInfoWrap.setBackground(BG_DARK);
        equipBgInfoWrap.setBorder(new EmptyBorder(10, 10, 10, 15));
        equipBgInfoWrap.add(equipBgInfoPanel, BorderLayout.CENTER);
        equipBgChoicePanel.add(equipBgInfoWrap, BorderLayout.EAST);

        JPanel equipBgChoiceContentWrap = new JPanel(new BorderLayout());
        equipBgChoiceContentWrap.setBackground(BG_DARK);
        equipBgChoiceContentWrap.setBorder(new EmptyBorder(15, 25, 10, 10));
        equipBgChoicePanel.add(equipBgChoiceContentWrap, BorderLayout.CENTER);

        List<Supplier<List<String>>> bgPackageSupplierHolder = new ArrayList<>();
        bgPackageSupplierHolder.add(ArrayList::new);

        JButton equipBgChoiceNextButton = new JButton("Next \u2192");
        styleAccentButton(equipBgChoiceNextButton);
        JPanel equipBgChoiceBottomBar = new JPanel(new FlowLayout(FlowLayout.CENTER));
        equipBgChoiceBottomBar.setBackground(BG_DARK);
        equipBgChoiceBottomBar.setBorder(new EmptyBorder(10, 0, 20, 0));
        equipBgChoiceBottomBar.add(equipBgChoiceNextButton);
        equipBgChoicePanel.add(equipBgChoiceBottomBar, BorderLayout.SOUTH);

        Runnable rebuildEquipBgChoice = () -> {
            equipBgChoiceContentWrap.removeAll();
            equipBgInfoTitle.setText("Equipment");
            equipBgInfoText.setText("Click the (i) next to a package to see what it contains.");
            EquipmentPackage pkg = GameData.BACKGROUND_EQUIPMENT.get(draft.background);
            Map<String, String> infoMap = new LinkedHashMap<>();
            infoMap.put("Package A", pkg != null ? WeaponData.describeEquipmentPackageA(pkg) : "No package available.");
            infoMap.put("Package B", "Skip the equipment list and take " + GameData.BACKGROUND_PACKAGE_B_GOLD + " GP instead.");
            CheckListResult cl = buildCheckListPanel(
                    Arrays.asList("Package A", "Package B (" + GameData.BACKGROUND_PACKAGE_B_GOLD + " GP)"), 1,
                    infoMap, equipBgInfoTitle, equipBgInfoText, false, 2, null);
            cl.component.setAlignmentX(Component.LEFT_ALIGNMENT);
            equipBgChoiceContentWrap.add(cl.component, BorderLayout.NORTH);
            bgPackageSupplierHolder.set(0, cl.getSelected);
            if ("A".equals(draft.backgroundPackage)) {
                cl.setSelected.accept(Collections.singletonList("Package A"));
            } else if ("B".equals(draft.backgroundPackage)) {
                cl.setSelected.accept(Collections.singletonList("Package B (" + GameData.BACKGROUND_PACKAGE_B_GOLD + " GP)"));
            }
            equipBgChoiceContentWrap.revalidate();
            equipBgChoiceContentWrap.repaint();
        };

        // ---- Background Equipment: Detail (sub-choices), skipped if package has none ----
        JPanel equipBgDetailPanel = new JPanel(new BorderLayout());
        equipBgDetailPanel.setBackground(BG_DARK);
        JPanel equipBgDetailHeaderWrap = new JPanel(new BorderLayout());
        equipBgDetailHeaderWrap.setBackground(BG_DARK);
        equipBgDetailHeaderWrap.add(buildWizardTopBar(() -> innerCardLayout.show(innerCards, "EQUIP_BG_CHOICE"), outerCardLayout, outerCards, editModeHolder, saveChangeButtons), BorderLayout.NORTH);
        JLabel equipBgDetailTitleLabel = new JLabel("Background Equipment Choices:");
        equipBgDetailTitleLabel.setForeground(GOLD);
        equipBgDetailTitleLabel.setFont(new Font("Serif", Font.BOLD, 24));
        equipBgDetailTitleLabel.setBorder(new EmptyBorder(0, 25, 10, 0));
        equipBgDetailHeaderWrap.add(equipBgDetailTitleLabel, BorderLayout.SOUTH);
        equipBgDetailPanel.add(equipBgDetailHeaderWrap, BorderLayout.NORTH);

        TranslucentPanel equipBgDetailInfoPanel = new TranslucentPanel(new Color(255, 255, 255, 25));
        equipBgDetailInfoPanel.setLayout(new BorderLayout());
        equipBgDetailInfoPanel.setPreferredSize(new Dimension(280, 100));
        equipBgDetailInfoPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        JLabel equipBgDetailInfoTitle = new JLabel("Item Details");
        equipBgDetailInfoTitle.setForeground(GOLD);
        equipBgDetailInfoTitle.setFont(new Font("Serif", Font.BOLD, 20));
        JTextArea equipBgDetailInfoText = new JTextArea("Click the (i) next to an option to see its details here.");
        styleInfoTextArea(equipBgDetailInfoText);
        JPanel equipBgDetailInfoTop = new JPanel(new BorderLayout());
        equipBgDetailInfoTop.setOpaque(false);
        equipBgDetailInfoTop.add(equipBgDetailInfoTitle, BorderLayout.NORTH);
        equipBgDetailInfoTop.add(equipBgDetailInfoText, BorderLayout.CENTER);
        equipBgDetailInfoPanel.add(equipBgDetailInfoTop, BorderLayout.NORTH);
        JPanel equipBgDetailInfoWrap = new JPanel(new BorderLayout());
        equipBgDetailInfoWrap.setBackground(BG_DARK);
        equipBgDetailInfoWrap.setBorder(new EmptyBorder(10, 10, 10, 15));
        equipBgDetailInfoWrap.add(equipBgDetailInfoPanel, BorderLayout.CENTER);
        equipBgDetailPanel.add(equipBgDetailInfoWrap, BorderLayout.EAST);

        JPanel equipBgDetailContentWrap = new JPanel();
        equipBgDetailContentWrap.setLayout(new BoxLayout(equipBgDetailContentWrap, BoxLayout.Y_AXIS));
        equipBgDetailContentWrap.setBackground(BG_DARK);
        equipBgDetailContentWrap.setBorder(new EmptyBorder(15, 25, 10, 10));
        JScrollPane equipBgDetailScroll = new JScrollPane(equipBgDetailContentWrap);
        applyDarkScrollBar(equipBgDetailScroll);
        equipBgDetailPanel.add(equipBgDetailScroll, BorderLayout.CENTER);

        List<RenderedItem> equipBgDetailRendered = new ArrayList<>();

        Runnable rebuildEquipBgDetail = () -> {
            equipBgDetailContentWrap.removeAll();
            equipBgDetailRendered.clear();
            equipBgDetailInfoTitle.setText("Item Details");
            equipBgDetailInfoText.setText("Click the (i) next to an option to see its details here.");
            EquipmentPackage pkg = GameData.BACKGROUND_EQUIPMENT.get(draft.background);
            if (pkg != null) {
                for (EquipmentChoice c : pkg.choices) {
                    List<String> options = c.weaponPool != null ? WeaponData.weaponsInPool(c.weaponPool) : Arrays.asList(c.options);
                    Map<String, String> info = c.weaponPool != null ? WeaponData.weaponInfoMap(options) : null;

                    JPanel section = new JPanel();
                    section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
                    section.setBackground(BG_DARK);
                    section.setBorder(new EmptyBorder(8, 0, 16, 0));
                    section.setAlignmentX(Component.LEFT_ALIGNMENT);
                    JLabel secLbl = new JLabel(c.label + " (choose " + c.count + ")");
                    secLbl.setForeground(GOLD);
                    secLbl.setFont(new Font("SansSerif", Font.BOLD, 17));
                    secLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
                    section.add(secLbl);
                    section.add(Box.createRigidArea(new Dimension(0, 6)));

                    CheckListResult clr = buildCheckListPanel(
                            options, c.count, info, equipBgDetailInfoTitle, equipBgDetailInfoText,
                            options.size() > 8, Math.min(6, Math.max(2, options.size())), null);
                    clr.component.setAlignmentX(Component.LEFT_ALIGNMENT);
                    section.add(clr.component);

                    equipBgDetailContentWrap.add(section);
                    DecisionItem synthItem = new DecisionItem(c.label, "CHOICE", c.count, options.toArray(new String[0]), null);
                    equipBgDetailRendered.add(new RenderedItem(synthItem, clr.getSelected));
                }
            }
            equipBgDetailContentWrap.revalidate();
            equipBgDetailContentWrap.repaint();
        };

        JButton equipBgDetailNextButton = new JButton("Next \u2192");
        styleAccentButton(equipBgDetailNextButton);
        JPanel equipBgDetailBottomBar = new JPanel(new FlowLayout(FlowLayout.CENTER));
        equipBgDetailBottomBar.setBackground(BG_DARK);
        equipBgDetailBottomBar.setBorder(new EmptyBorder(10, 0, 20, 0));
        equipBgDetailBottomBar.add(equipBgDetailNextButton);
        equipBgDetailPanel.add(equipBgDetailBottomBar, BorderLayout.SOUTH);

        // ---- Class Equipment: Package choice (A vs B) ----
        JPanel equipClassChoicePanel = new JPanel(new BorderLayout());
        equipClassChoicePanel.setBackground(BG_DARK);
        JPanel equipClassChoiceHeaderWrap = new JPanel(new BorderLayout());
        equipClassChoiceHeaderWrap.setBackground(BG_DARK);
        equipClassChoiceHeaderWrap.add(buildWizardTopBar(() -> innerCardLayout.show(innerCards,
                (GameData.BACKGROUND_EQUIPMENT.containsKey(draft.background) && !GameData.BACKGROUND_EQUIPMENT.get(draft.background).choices.isEmpty())
                        ? "EQUIP_BG_DETAIL" : "EQUIP_BG_CHOICE"), outerCardLayout, outerCards, editModeHolder, saveChangeButtons), BorderLayout.NORTH);
        JLabel equipClassChoiceTitleLabel = new JLabel("Class Equipment:");
        equipClassChoiceTitleLabel.setForeground(GOLD);
        equipClassChoiceTitleLabel.setFont(new Font("Serif", Font.BOLD, 24));
        equipClassChoiceTitleLabel.setBorder(new EmptyBorder(0, 25, 10, 0));
        equipClassChoiceHeaderWrap.add(equipClassChoiceTitleLabel, BorderLayout.SOUTH);
        equipClassChoicePanel.add(equipClassChoiceHeaderWrap, BorderLayout.NORTH);

        TranslucentPanel equipClassInfoPanel = new TranslucentPanel(new Color(255, 255, 255, 25));
        equipClassInfoPanel.setLayout(new BorderLayout());
        equipClassInfoPanel.setPreferredSize(new Dimension(280, 100));
        equipClassInfoPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        JLabel equipClassInfoTitle = new JLabel("Equipment");
        equipClassInfoTitle.setForeground(GOLD);
        equipClassInfoTitle.setFont(new Font("Serif", Font.BOLD, 20));
        JTextArea equipClassInfoText = new JTextArea("Click the (i) next to a package to see what it contains.");
        styleInfoTextArea(equipClassInfoText);
        JPanel equipClassInfoTop = new JPanel(new BorderLayout());
        equipClassInfoTop.setOpaque(false);
        equipClassInfoTop.add(equipClassInfoTitle, BorderLayout.NORTH);
        equipClassInfoTop.add(equipClassInfoText, BorderLayout.CENTER);
        equipClassInfoPanel.add(equipClassInfoTop, BorderLayout.NORTH);
        JPanel equipClassInfoWrap = new JPanel(new BorderLayout());
        equipClassInfoWrap.setBackground(BG_DARK);
        equipClassInfoWrap.setBorder(new EmptyBorder(10, 10, 10, 15));
        equipClassInfoWrap.add(equipClassInfoPanel, BorderLayout.CENTER);
        equipClassChoicePanel.add(equipClassInfoWrap, BorderLayout.EAST);

        JPanel equipClassChoiceContentWrap = new JPanel(new BorderLayout());
        equipClassChoiceContentWrap.setBackground(BG_DARK);
        equipClassChoiceContentWrap.setBorder(new EmptyBorder(15, 25, 10, 10));
        equipClassChoicePanel.add(equipClassChoiceContentWrap, BorderLayout.CENTER);

        List<Supplier<List<String>>> classPackageSupplierHolder = new ArrayList<>();
        classPackageSupplierHolder.add(ArrayList::new);

        JButton equipClassChoiceNextButton = new JButton("Next \u2192");
        styleAccentButton(equipClassChoiceNextButton);
        JPanel equipClassChoiceBottomBar = new JPanel(new FlowLayout(FlowLayout.CENTER));
        equipClassChoiceBottomBar.setBackground(BG_DARK);
        equipClassChoiceBottomBar.setBorder(new EmptyBorder(10, 0, 20, 0));
        equipClassChoiceBottomBar.add(equipClassChoiceNextButton);
        equipClassChoicePanel.add(equipClassChoiceBottomBar, BorderLayout.SOUTH);

        Runnable rebuildEquipClassChoice = () -> {
            equipClassChoiceContentWrap.removeAll();
            equipClassInfoTitle.setText("Equipment");
            equipClassInfoText.setText("Click the (i) next to a package to see what it contains.");
            EquipmentPackage pkg = GameData.CLASS_EQUIPMENT.get(draft.characterClass);
            Integer bGold = GameData.CLASS_PACKAGE_B_GOLD.get(draft.characterClass);
            int bGoldVal = bGold != null ? bGold : 0;
            Map<String, String> infoMap = new LinkedHashMap<>();
            infoMap.put("Package A", pkg != null ? WeaponData.describeEquipmentPackageA(pkg) : "No package available.");
            infoMap.put("Package B", "Skip the equipment list and take " + bGoldVal + " GP instead.");
            CheckListResult cl = buildCheckListPanel(
                    Arrays.asList("Package A", "Package B (" + bGoldVal + " GP)"), 1,
                    infoMap, equipClassInfoTitle, equipClassInfoText, false, 2, null);
            cl.component.setAlignmentX(Component.LEFT_ALIGNMENT);
            equipClassChoiceContentWrap.add(cl.component, BorderLayout.NORTH);
            classPackageSupplierHolder.set(0, cl.getSelected);
            if ("A".equals(draft.classPackage)) {
                cl.setSelected.accept(Collections.singletonList("Package A"));
            } else if ("B".equals(draft.classPackage)) {
                cl.setSelected.accept(Collections.singletonList("Package B (" + bGoldVal + " GP)"));
            }
            equipClassChoiceContentWrap.revalidate();
            equipClassChoiceContentWrap.repaint();
        };

        // ---- Class Equipment: Detail (sub-choices), skipped if package has none ----
        JPanel equipClassDetailPanel = new JPanel(new BorderLayout());
        equipClassDetailPanel.setBackground(BG_DARK);
        JPanel equipClassDetailHeaderWrap = new JPanel(new BorderLayout());
        equipClassDetailHeaderWrap.setBackground(BG_DARK);
        equipClassDetailHeaderWrap.add(buildWizardTopBar(() -> innerCardLayout.show(innerCards, "EQUIP_CLASS_CHOICE"), outerCardLayout, outerCards, editModeHolder, saveChangeButtons), BorderLayout.NORTH);
        JLabel equipClassDetailTitleLabel = new JLabel("Class Equipment Choices:");
        equipClassDetailTitleLabel.setForeground(GOLD);
        equipClassDetailTitleLabel.setFont(new Font("Serif", Font.BOLD, 24));
        equipClassDetailTitleLabel.setBorder(new EmptyBorder(0, 25, 10, 0));
        equipClassDetailHeaderWrap.add(equipClassDetailTitleLabel, BorderLayout.SOUTH);
        equipClassDetailPanel.add(equipClassDetailHeaderWrap, BorderLayout.NORTH);

        TranslucentPanel equipClassDetailInfoPanel = new TranslucentPanel(new Color(255, 255, 255, 25));
        equipClassDetailInfoPanel.setLayout(new BorderLayout());
        equipClassDetailInfoPanel.setPreferredSize(new Dimension(280, 100));
        equipClassDetailInfoPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        JLabel equipClassDetailInfoTitle = new JLabel("Item Details");
        equipClassDetailInfoTitle.setForeground(GOLD);
        equipClassDetailInfoTitle.setFont(new Font("Serif", Font.BOLD, 20));
        JTextArea equipClassDetailInfoText = new JTextArea("Click the (i) next to an option to see its details here.");
        styleInfoTextArea(equipClassDetailInfoText);
        JPanel equipClassDetailInfoTop = new JPanel(new BorderLayout());
        equipClassDetailInfoTop.setOpaque(false);
        equipClassDetailInfoTop.add(equipClassDetailInfoTitle, BorderLayout.NORTH);
        equipClassDetailInfoTop.add(equipClassDetailInfoText, BorderLayout.CENTER);
        equipClassDetailInfoPanel.add(equipClassDetailInfoTop, BorderLayout.NORTH);
        JPanel equipClassDetailInfoWrap = new JPanel(new BorderLayout());
        equipClassDetailInfoWrap.setBackground(BG_DARK);
        equipClassDetailInfoWrap.setBorder(new EmptyBorder(10, 10, 10, 15));
        equipClassDetailInfoWrap.add(equipClassDetailInfoPanel, BorderLayout.CENTER);
        equipClassDetailPanel.add(equipClassDetailInfoWrap, BorderLayout.EAST);

        JPanel equipClassDetailContentWrap = new JPanel();
        equipClassDetailContentWrap.setLayout(new BoxLayout(equipClassDetailContentWrap, BoxLayout.Y_AXIS));
        equipClassDetailContentWrap.setBackground(BG_DARK);
        equipClassDetailContentWrap.setBorder(new EmptyBorder(15, 25, 10, 10));
        JScrollPane equipClassDetailScroll = new JScrollPane(equipClassDetailContentWrap);
        applyDarkScrollBar(equipClassDetailScroll);
        equipClassDetailPanel.add(equipClassDetailScroll, BorderLayout.CENTER);

        List<RenderedItem> equipClassDetailRendered = new ArrayList<>();

        Runnable rebuildEquipClassDetail = () -> {
            equipClassDetailContentWrap.removeAll();
            equipClassDetailRendered.clear();
            equipClassDetailInfoTitle.setText("Item Details");
            equipClassDetailInfoText.setText("Click the (i) next to an option to see its details here.");
            EquipmentPackage pkg = GameData.CLASS_EQUIPMENT.get(draft.characterClass);
            if (pkg != null) {
                for (EquipmentChoice c : pkg.choices) {
                    List<String> options = c.weaponPool != null ? WeaponData.weaponsInPool(c.weaponPool) : Arrays.asList(c.options);
                    Map<String, String> info = c.weaponPool != null ? WeaponData.weaponInfoMap(options) : null;

                    JPanel section = new JPanel();
                    section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
                    section.setBackground(BG_DARK);
                    section.setBorder(new EmptyBorder(8, 0, 16, 0));
                    section.setAlignmentX(Component.LEFT_ALIGNMENT);
                    JLabel secLbl = new JLabel(c.label + " (choose " + c.count + ")");
                    secLbl.setForeground(GOLD);
                    secLbl.setFont(new Font("SansSerif", Font.BOLD, 17));
                    secLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
                    section.add(secLbl);
                    section.add(Box.createRigidArea(new Dimension(0, 6)));

                    CheckListResult clr = buildCheckListPanel(
                            options, c.count, info, equipClassDetailInfoTitle, equipClassDetailInfoText,
                            options.size() > 8, Math.min(6, Math.max(2, options.size())), null);
                    clr.component.setAlignmentX(Component.LEFT_ALIGNMENT);
                    section.add(clr.component);

                    equipClassDetailContentWrap.add(section);
                    DecisionItem synthItem = new DecisionItem(c.label, "CHOICE", c.count, options.toArray(new String[0]), null);
                    equipClassDetailRendered.add(new RenderedItem(synthItem, clr.getSelected));
                }
            }
            equipClassDetailContentWrap.revalidate();
            equipClassDetailContentWrap.repaint();
        };

        JButton equipClassDetailNextButton = new JButton("Finish \u2192");
        styleAccentButton(equipClassDetailNextButton);
        JPanel equipClassDetailBottomBar = new JPanel(new FlowLayout(FlowLayout.CENTER));
        equipClassDetailBottomBar.setBackground(BG_DARK);
        equipClassDetailBottomBar.setBorder(new EmptyBorder(10, 0, 20, 0));
        equipClassDetailBottomBar.add(equipClassDetailNextButton);
        equipClassDetailPanel.add(equipClassDetailBottomBar, BorderLayout.SOUTH);

        // ---- Wire equipment button logic ----
        equipBgChoiceNextButton.addActionListener(e -> {
            List<String> sel = bgPackageSupplierHolder.get(0).get();
            if (sel.isEmpty()) {
                equipBgInfoTitle.setText("Choose a package");
                equipBgInfoText.setText("Please select Package A or Package B before continuing.");
                return;
            }
            boolean isA = sel.get(0).startsWith("Package A");
            draft.backgroundPackage = isA ? "A" : "B";
            EquipmentPackage pkg = GameData.BACKGROUND_EQUIPMENT.get(draft.background);
            if (isA) {
                if (pkg != null && !pkg.choices.isEmpty()) {
                    rebuildEquipBgDetail.run();
                    innerCardLayout.show(innerCards, "EQUIP_BG_DETAIL");
                    return;
                }
                bgEquipItems.clear();
                if (pkg != null) bgEquipItems.addAll(pkg.fixedItems);
                bgGoldHolder[0] = pkg != null ? pkg.gold : 0;
            } else {
                bgEquipItems.clear();
                bgGoldHolder[0] = GameData.BACKGROUND_PACKAGE_B_GOLD;
            }
            rebuildEquipClassChoice.run();
            innerCardLayout.show(innerCards, "EQUIP_CLASS_CHOICE");
        });

        equipBgDetailNextButton.addActionListener(e -> {
            for (RenderedItem ri : equipBgDetailRendered) {
                if (ri.getSelections.get().size() != ri.item.count) {
                    equipBgDetailInfoTitle.setText("Incomplete");
                    equipBgDetailInfoText.setText("Please finish \"" + ri.item.label + "\" before continuing.");
                    return;
                }
            }
            draft.backgroundPackage = "A";
            EquipmentPackage pkg = GameData.BACKGROUND_EQUIPMENT.get(draft.background);
            bgEquipItems.clear();
            if (pkg != null) bgEquipItems.addAll(pkg.fixedItems);
            for (RenderedItem ri : equipBgDetailRendered) bgEquipItems.addAll(ri.getSelections.get());
            bgGoldHolder[0] = pkg != null ? pkg.gold : 0;
            rebuildEquipClassChoice.run();
            innerCardLayout.show(innerCards, "EQUIP_CLASS_CHOICE");
        });

        equipClassChoiceNextButton.addActionListener(e -> {
            List<String> sel = classPackageSupplierHolder.get(0).get();
            if (sel.isEmpty()) {
                equipClassInfoTitle.setText("Choose a package");
                equipClassInfoText.setText("Please select Package A or Package B before continuing.");
                return;
            }
            boolean isA = sel.get(0).startsWith("Package A");
            draft.classPackage = isA ? "A" : "B";
            EquipmentPackage pkg = GameData.CLASS_EQUIPMENT.get(draft.characterClass);
            Integer bGold = GameData.CLASS_PACKAGE_B_GOLD.get(draft.characterClass);
            if (isA) {
                if (pkg != null && !pkg.choices.isEmpty()) {
                    rebuildEquipClassDetail.run();
                    innerCardLayout.show(innerCards, "EQUIP_CLASS_DETAIL");
                    return;
                }
                classEquipItems.clear();
                if (pkg != null) classEquipItems.addAll(pkg.fixedItems);
                classGoldHolder[0] = pkg != null ? pkg.gold : 0;
            } else {
                classEquipItems.clear();
                classGoldHolder[0] = bGold != null ? bGold : 0;
            }
            draft.equipment.clear();
            draft.equipment.addAll(bgEquipItems);
            draft.equipment.addAll(classEquipItems);
            draft.gold = bgGoldHolder[0] + classGoldHolder[0];
            touchedEquipment[0] = true;
            segEquipmentOk[0] = true;
            refreshSidebar.run();
            if (editModeHolder[0]) {
                performSaveChangesHolder[0].run();
            } else {
                CharacterStorage.saveNewCharacter(draft);
                outerCardLayout.show(outerCards, "CHARACTERS");
            }
        });

        equipClassDetailNextButton.addActionListener(e -> {
            for (RenderedItem ri : equipClassDetailRendered) {
                if (ri.getSelections.get().size() != ri.item.count) {
                    equipClassDetailInfoTitle.setText("Incomplete");
                    equipClassDetailInfoText.setText("Please finish \"" + ri.item.label + "\" before continuing.");
                    return;
                }
            }
            draft.classPackage = "A";
            EquipmentPackage pkg = GameData.CLASS_EQUIPMENT.get(draft.characterClass);
            classEquipItems.clear();
            if (pkg != null) classEquipItems.addAll(pkg.fixedItems);
            for (RenderedItem ri : equipClassDetailRendered) classEquipItems.addAll(ri.getSelections.get());
            classGoldHolder[0] = pkg != null ? pkg.gold : 0;
            draft.equipment.clear();
            draft.equipment.addAll(bgEquipItems);
            draft.equipment.addAll(classEquipItems);
            draft.gold = bgGoldHolder[0] + classGoldHolder[0];
            touchedEquipment[0] = true;
            segEquipmentOk[0] = true;
            refreshSidebar.run();
            if (editModeHolder[0]) {
                performSaveChangesHolder[0].run();
            } else {
                CharacterStorage.saveNewCharacter(draft);
                outerCardLayout.show(outerCards, "CHARACTERS");
            }
        });

        classDecisionsNextButton.addActionListener(e -> {
            for (RenderedItem ri : classDecisionsRendered) {
                boolean countMatters = "SKILLS".equals(ri.item.type) || "CHOICE".equals(ri.item.type)
                        || "CANTRIPS".equals(ri.item.type) || "SPELLS1".equals(ri.item.type);
                if (countMatters && ri.getSelections.get().size() != ri.item.count) {
                    classDecisionsInfoTitle.setText("Incomplete");
                    classDecisionsInfoText.setText("Please finish \"" + ri.item.label + "\" before continuing.");
                    return;
                }
            }
            draft.classChoices.clear();
            LinkedHashSet<String> cantripSet = new LinkedHashSet<>();
            LinkedHashSet<String> spellSet = new LinkedHashSet<>();
            for (RenderedItem ri : classDecisionsRendered) {
                List<String> sel = ri.getSelections.get();
                draft.classChoices.put(ri.item.label, sel);
                if ("CANTRIPS".equals(ri.item.type)) {
                    cantripSet.addAll(sel);
                } else if ("SPELLS1".equals(ri.item.type)) {
                    spellSet.addAll(sel);
                }
            }
            draft.cantripsKnown = new ArrayList<>(cantripSet);
            draft.spellsKnown = new ArrayList<>(spellSet);
            touchedClassDetails[0] = true;
            segDetailsOk[0] = true;
            refreshSidebar.run();
            rebuildEquipBgChoice.run();
            innerCardLayout.show(innerCards, "EQUIP_BG_CHOICE");
        });

        // ---- Wire Background Skills Next button ----
        bgSkillsNextButton.addActionListener(e -> {
            List<String> chosen = bgSkillsSupplierHolder.get(0).get();
            if ("Custom".equals(draft.background) && chosen.size() != 2) {
                bgSkillsInfoTitle.setText("Choose two skills");
                bgSkillsInfoText.setText("Please select exactly two skills before continuing.");
                return;
            }
            draft.backgroundSkills = new ArrayList<>(chosen);
            touchedBgSkills[0] = true;
            segBackgroundOk[0] = true;
            refreshSidebar.run();
            rebuildClassDecisions.run();
            innerCardLayout.show(innerCards, "CLASS_DECISIONS");
        });

        // ---- Wire ASI Next button (now leads to Background Skills) ----
        asiNextButton.addActionListener(e -> {
            if (selectedIncreaseHolder[0] == null) {
                asiInfoTitle.setText("Choose an option");
                asiInfoText.setText("Select +2/+1, +1/+1/+1, or check skip before continuing.");
                return;
            }

            // Undo any previously-applied increase from this session (or from the original
            // saved character, when editing) so re-running this step never double-applies.
            for (Map.Entry<String, Integer> en : draft.asiIncreases.entrySet()) {
                draft.stats.merge(en.getKey(), -en.getValue(), Integer::sum);
            }
            draft.asiIncreases.clear();
            draft.asiMode = selectedIncreaseHolder[0];

            if (!selectedIncreaseHolder[0].equals("SKIP")) {
                Map<String, Integer> increases = new LinkedHashMap<>();
                if (selectedIncreaseHolder[0].equals("PLUS2PLUS1")) {
                    String s2 = (String) activeCombos.get(0).getSelectedItem();
                    String s1 = (String) activeCombos.get(1).getSelectedItem();
                    if (s2 == null || s1 == null || s2.equals(s1)) {
                        asiInfoTitle.setText("Invalid selection");
                        asiInfoText.setText("The +2 and +1 stats must be different.");
                        // Nothing was applied yet this run, so state is already consistent.
                        return;
                    }
                    increases.merge(s2, 2, Integer::sum);
                    increases.merge(s1, 1, Integer::sum);
                } else {
                    LinkedHashSet<String> chosen = new LinkedHashSet<>();
                    for (JComboBox<String> combo : activeCombos) {
                        Object val = combo.getSelectedItem();
                        if (val != null) chosen.add((String) val);
                    }
                    if (chosen.size() < activeCombos.size()) {
                        asiInfoTitle.setText("Invalid selection");
                        asiInfoText.setText("Choose three different stats.");
                        return;
                    }
                    for (String s : chosen) increases.merge(s, 1, Integer::sum);
                }

                for (Map.Entry<String, Integer> en : increases.entrySet()) {
                    int current = draft.stats.getOrDefault(en.getKey(), 8);
                    if (current + en.getValue() > 20) {
                        asiInfoTitle.setText("Score too high");
                        asiInfoText.setText(en.getKey() + " cannot be increased past 20.");
                        return;
                    }
                }

                for (Map.Entry<String, Integer> en : increases.entrySet()) {
                    draft.stats.merge(en.getKey(), en.getValue(), Integer::sum);
                }
                draft.asiIncreases = increases;
            }
            touchedASI[0] = true;
            segStatsOk[0] = true;
            refreshSidebar.run();
            rebuildBackgroundSkills.run();
            innerCardLayout.show(innerCards, "BACKGROUND_SKILLS");
        });

        // Reset logic for the ability score generation step
        Runnable statsReset = () -> {
            for (int i = 0; i < 6; i++) {
                pointBuySpinners[i].setValue(8);
                pointBuyPrevValues[i] = 8;
                pointBuyCostLabels[i].setText("Cost: 0");
            }
            pointsRemainingLabel.setText("Points Remaining: 27");

            fullyRandomCheck.setSelected(false);
            highestCombo.setEnabled(true);
            lowestCombo.setEnabled(true);
            highestCombo.setSelectedIndex(0);
            lowestCombo.setSelectedIndex(GameData.STAT_ORDER.length - 1);
            for (String stat : GameData.STAT_ORDER) randomValueLabels.get(stat).setText("\u2014");
            randomCurrentValues.clear();
            randomSumLabel.setText("Total: \u2014");
            randomMessageLabel.setText(" ");
            rollButton.setText("Roll");
            rollButton.setEnabled(true);

            for (JSpinner sp : customSpinners) sp.setValue(10);

            methodCheckList.reset.run();
            selectedMethodHolder[0] = null;
            methodCardLayout.show(methodCards, "PROMPT");

            statsInfoTitle.setText("Ability Scores");
            statsInfoText.setText("Click the (i) next to a stat or method to see its details here.");
        };

        // Reset logic for background + ability score increase steps
        Runnable backgroundAsiReset = () -> {
            backgroundCheckList.reset.run();
            backgroundInfoTitle.setText("Select a background");
            backgroundInfoText.setText("Click the (i) next to a background to see its details here.");

            availableStatsHolder[0] = null;
            skipCheck.setSelected(false);
            increaseModeCheckList.setEnabled.accept(true);
            increaseModeCheckList.reset.run();
            selectedIncreaseHolder[0] = null;
            activeCombos.clear();
            asiDynamicContentWrap.removeAll();
            asiDynamicContentWrap.add(asiPromptLabel, BorderLayout.NORTH);
            asiDynamicContentWrap.revalidate();
            asiDynamicContentWrap.repaint();
            asiInfoTitle.setText("Ability Score Increase");
            asiInfoText.setText("Choose how to boost your background's key stats, or skip entirely.");
        };

        // Reset logic for background skills + class details steps
        Runnable skillsAndClassDetailsReset = () -> {
            bgSkillsContentWrap.removeAll();
            bgSkillsSupplierHolder.set(0, () -> Collections.emptyList());
            bgSkillsInfoTitle.setText("Skill Proficiencies");
            bgSkillsInfoText.setText("Click the (i) next to a skill to see what it means.");
            bgSkillsContentWrap.revalidate();
            bgSkillsContentWrap.repaint();

            classDecisionsContentWrap.removeAll();
            classDecisionsRendered.clear();
            classDecisionsInfoTitle.setText("Class Details");
            classDecisionsInfoText.setText("Click the (i) next to a skill to see what it means.");
            classDecisionsContentWrap.revalidate();
            classDecisionsContentWrap.repaint();
        };

        // ---- Wire NAME step Next button ----
        nameNextButton.addActionListener(e -> {
            String nm = nameField.getText().trim();
            draft.name = nm.isEmpty() ? "Unnamed Character" : nm;
            segNameOk[0] = true;
            refreshSidebar.run();
            innerCardLayout.show(innerCards, "SPECIES");
        });

        // ---- Wire CLASS step Select button ----
        classSelectButton.addActionListener(e -> {
            List<String> sel = classCheckList.getSelected.get();
            if (sel.isEmpty()) {
                classInfoTitle.setText("No class selected");
                classInfoText.setText("Please select a class before continuing.");
            } else {
                String newClass = sel.get(0);
                boolean classChanged = !newClass.equals(draft.characterClass);
                draft.characterClass = newClass;
                touchedClass[0] = true;
                segClassOk[0] = true;
                if (classChanged) {
                    segDetailsOk[0] = false;
                    segEquipmentOk[0] = false;
                    draft.classChoices.clear();
                    draft.cantripsKnown.clear();
                    draft.spellsKnown.clear();
                }
                refreshSidebar.run();
                innerCardLayout.show(innerCards, "STATS");
            }
        });

        // ---- Wire ABILITY SCORES step Next button ----
        statsNextButton.addActionListener(e -> {
            if (selectedMethodHolder[0] == null) {
                statsInfoTitle.setText("Choose a method");
                statsInfoText.setText("Please select Point Buy, Random Stat Generation, or Custom before continuing.");
                return;
            }
            draft.stats.clear();
            if (selectedMethodHolder[0].equals("Point Buy")) {
                for (int i = 0; i < 6; i++) draft.stats.put(GameData.STAT_ORDER[i], (Integer) pointBuySpinners[i].getValue());
            } else if (selectedMethodHolder[0].equals("Custom")) {
                for (int i = 0; i < 6; i++) draft.stats.put(GameData.STAT_ORDER[i], (Integer) customSpinners[i].getValue());
            } else {
                if (randomCurrentValues.size() < 6) {
                    statsInfoTitle.setText("Roll required");
                    statsInfoText.setText("Please roll your ability scores before continuing.");
                    return;
                }
                draft.stats.putAll(randomCurrentValues);
            }
            // Base stats just changed, so any previously-applied ability score increase no
            // longer applies to them; the ASI step (always visited next) will re-derive it.
            draft.asiIncreases.clear();
            draft.asiMode = "";
            touchedStats[0] = true;
            segStatsOk[0] = false;
            refreshSidebar.run();
            innerCardLayout.show(innerCards, "BACKGROUND");
        });

        // ---- Wire BACKGROUND step Select button ----
        backgroundSelectButton.addActionListener(e -> {
            List<String> selBg = backgroundCheckList.getSelected.get();
            if (selBg.isEmpty()) {
                backgroundInfoTitle.setText("No background selected");
                backgroundInfoText.setText("Please select a background before continuing.");
            } else {
                String newBackground = selBg.get(0);
                boolean backgroundChanged = !newBackground.equals(draft.background);
                draft.background = newBackground;
                touchedBackground[0] = true;
                availableStatsHolder[0] = GameData.BACKGROUND_STATS.get(newBackground);

                if (backgroundChanged) {
                    // Background actually changed, so its key stats differ - the previous
                    // ability score increase choice no longer applies, so reset it. If the
                    // background is unchanged (e.g. just clicking through while editing),
                    // keep whatever ASI selection is already showing (including a prefill).
                    skipCheck.setSelected(false);
                    increaseModeCheckList.setEnabled.accept(true);
                    increaseModeCheckList.reset.run();
                    selectedIncreaseHolder[0] = null;
                    activeCombos.clear();
                    asiDynamicContentWrap.removeAll();
                    asiDynamicContentWrap.add(asiPromptLabel, BorderLayout.NORTH);
                    asiDynamicContentWrap.revalidate();
                    asiDynamicContentWrap.repaint();
                    asiInfoTitle.setText("Ability Score Increase");
                    asiInfoText.setText("Choose how to boost your background's key stats, or skip entirely.");
                    segStatsOk[0] = false;
                    segBackgroundOk[0] = false;
                    segEquipmentOk[0] = false;
                    draft.backgroundSkills.clear();
                }

                refreshSidebar.run();
                innerCardLayout.show(innerCards, "ASI");
            }
        });

        innerCards.add(namePanel, "NAME");
        innerCards.add(speciesPanel, "SPECIES");
        innerCards.add(lineagePanel, "LINEAGE");
        innerCards.add(classPanel, "CLASS");
        innerCards.add(statsPanel, "STATS");
        innerCards.add(backgroundPanel, "BACKGROUND");
        innerCards.add(asiPanel, "ASI");
        innerCards.add(bgSkillsPanel, "BACKGROUND_SKILLS");
        innerCards.add(classDecisionsPanel, "CLASS_DECISIONS");
        innerCards.add(equipBgChoicePanel, "EQUIP_BG_CHOICE");
        innerCards.add(equipBgDetailPanel, "EQUIP_BG_DETAIL");
        innerCards.add(equipClassChoicePanel, "EQUIP_CLASS_CHOICE");
        innerCards.add(equipClassDetailPanel, "EQUIP_CLASS_DETAIL");

        // ===================== PROGRESS BAR =====================
        String[] segmentNames = {"Name", "Species", "Class", "Stats", "Background", "Details", "Equipment"};
        String[] segmentEntryCards = {"NAME", "SPECIES", "CLASS", "STATS", "BACKGROUND", "CLASS_DECISIONS", "EQUIP_BG_CHOICE"};
        boolean[][] segmentOkFlags = {segNameOk, segSpeciesOk, segClassOk, segStatsOk, segBackgroundOk, segDetailsOk, segEquipmentOk};
        int[] currentSegmentHolder = {0};
        JPanel[] segBars = new JPanel[7];
        JLabel[] segLabels = new JLabel[7];
        Runnable[] updateProgressBarHolder = new Runnable[1];

        JPanel progressBarPanel = new JPanel(new GridLayout(1, 7, 6, 0));
        progressBarPanel.setBackground(BG_DARK);
        progressBarPanel.setBorder(new EmptyBorder(18, 30, 12, 30));

        for (int i = 0; i < 7; i++) {
            final int segIdx = i;
            JPanel segCol = new JPanel();
            segCol.setLayout(new BoxLayout(segCol, BoxLayout.Y_AXIS));
            segCol.setBackground(BG_DARK);
            segCol.setCursor(new Cursor(Cursor.HAND_CURSOR));

            JPanel bar = new JPanel();
            bar.setBackground(TEXT_MUTED);
            bar.setPreferredSize(new Dimension(10, 6));
            bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
            bar.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel lbl = new JLabel(segmentNames[i], JLabel.CENTER);
            lbl.setForeground(TEXT_MUTED);
            lbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
            lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
            lbl.setBorder(new EmptyBorder(5, 0, 0, 0));

            segCol.add(bar);
            segCol.add(lbl);

            segCol.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    int cur = currentSegmentHolder[0];
                    if (segIdx == cur) return;
                    int lo = Math.min(cur, segIdx);
                    int hi = Math.max(cur, segIdx);
                    boolean allowed = true;
                    for (int k = lo + 1; k < hi; k++) {
                        if (!segmentOkFlags[k][0]) {
                            allowed = false;
                            break;
                        }
                    }
                    if (!allowed) return;
                    if (segIdx == 5) rebuildClassDecisions.run();
                    if (segIdx == 6) rebuildEquipBgChoice.run();
                    innerCardLayout.show(innerCards, segmentEntryCards[segIdx]);
                }
            });

            progressBarPanel.add(segCol);
            segBars[i] = bar;
            segLabels[i] = lbl;
        }

        Runnable updateProgressBar = () -> {
            for (int i = 0; i < 7; i++) {
                boolean complete = segmentOkFlags[i][0];
                boolean isCurrent = (i == currentSegmentHolder[0]);
                segBars[i].setBackground(complete ? Color.WHITE : TEXT_MUTED);
                segLabels[i].setForeground(isCurrent ? GOLD : (complete ? TEXT_LIGHT : TEXT_MUTED));
                segLabels[i].setFont(new Font("SansSerif", isCurrent ? Font.BOLD : Font.PLAIN, isCurrent ? 14 : 12));
            }
        };
        updateProgressBarHolder[0] = updateProgressBar;

        int[][] segmentPanelMap = {
                {0}, {1}, {1}, {2}, {3}, {4}, {3}, {4}, {5}, {6}, {6}, {6}, {6}
        };
        JPanel[] allStepPanels = {
                namePanel, speciesPanel, lineagePanel, classPanel, statsPanel, backgroundPanel,
                asiPanel, bgSkillsPanel, classDecisionsPanel, equipBgChoicePanel, equipBgDetailPanel,
                equipClassChoicePanel, equipClassDetailPanel
        };
        for (int i = 0; i < allStepPanels.length; i++) {
            final int segForPanel = segmentPanelMap[i][0];
            allStepPanels[i].addComponentListener(new ComponentAdapter() {
                @Override
                public void componentShown(ComponentEvent e) {
                    currentSegmentHolder[0] = segForPanel;
                    updateProgressBarHolder[0].run();
                }
            });
        }

        wizard.add(progressBarPanel, BorderLayout.NORTH);
        wizard.add(innerCards, BorderLayout.CENTER);
        wizard.add(sidebarWrap, BorderLayout.EAST);

        Runnable reset = () -> {
            editIndexHolder[0] = null;
            touchedSpecies[0] = false;
            touchedLineage[0] = false;
            touchedClass[0] = false;
            touchedASI[0] = false;
            touchedStats[0] = false;
            touchedBackground[0] = false;
            touchedBgSkills[0] = false;
            touchedClassDetails[0] = false;
            touchedEquipment[0] = false;

            segNameOk[0] = false;
            segSpeciesOk[0] = false;
            segClassOk[0] = false;
            segStatsOk[0] = false;
            segBackgroundOk[0] = false;
            segDetailsOk[0] = false;
            segEquipmentOk[0] = false;

            draft.name = "";
            draft.species = "";
            draft.lineage = "";
            draft.characterClass = "";
            draft.background = "";
            draft.stats.clear();
            draft.backgroundSkills.clear();
            draft.classChoices.clear();
            draft.cantripsKnown.clear();
            draft.spellsKnown.clear();
            draft.equipment.clear();
            draft.gold = 0;
            bgEquipItems.clear();
            bgGoldHolder[0] = 0;
            classEquipItems.clear();
            classGoldHolder[0] = 0;
            equipBgChoiceContentWrap.removeAll();
            equipBgChoiceContentWrap.revalidate();
            equipBgChoiceContentWrap.repaint();
            equipBgDetailContentWrap.removeAll();
            equipBgDetailRendered.clear();
            equipBgDetailContentWrap.revalidate();
            equipBgDetailContentWrap.repaint();
            equipClassChoiceContentWrap.removeAll();
            equipClassChoiceContentWrap.revalidate();
            equipClassChoiceContentWrap.repaint();
            equipClassDetailContentWrap.removeAll();
            equipClassDetailRendered.clear();
            equipClassDetailContentWrap.revalidate();
            equipClassDetailContentWrap.repaint();

            nameField.setText("");

            speciesCheckList.reset.run();
            speciesInfoTitle.setText("Select a species");
            speciesInfoText.setText("Click the (i) next to a species to see its details here.");

            lineageContentWrap.removeAll();
            lineageContentWrap.revalidate();
            lineageContentWrap.repaint();
            lineageSupplierHolder.set(0, () -> Collections.emptyList());

            classCheckList.reset.run();
            classInfoTitle.setText("Select a class");
            classInfoText.setText("Click the (i) next to a class to see its details here.");

            statsReset.run();
            backgroundAsiReset.run();
            skillsAndClassDetailsReset.run();

            editModeHolder[0] = false;
            updateSaveButtonLabels.run();

            currentSegmentHolder[0] = 0;
            updateProgressBar.run();

            refreshSidebar.run();
            innerCardLayout.show(innerCards, "NAME");
        };

        // Wire every step's Save Draft/Save Changes button. It's a no-op unless in edit mode.
        for (JButton b : saveChangeButtons) {
            b.addActionListener(e -> performSaveChangesHolder[0].run());
        }

        // Pulls whatever is currently showing in the simple, single-value steps into draft,
        // even if the user hasn't clicked that step's own Next/Select button yet. This is what
        // lets "Save Changes" save an edit made on the screen you're currently looking at.
        // The multi-part steps (Ability Score Increase, Background Skills, Class Details,
        // Equipment) are intentionally left alone here - they're captured normally the moment
        // their own Next/Select button is clicked, and re-deriving them safely without
        // duplicating that validation isn't worth the risk of silently corrupting saved data.
        Runnable syncDraftFromCurrentUI = () -> {
            String nm = nameField.getText().trim();
            if (!nm.isEmpty()) draft.name = nm;

            List<String> selSpecies = speciesCheckList.getSelected.get();
            if (!selSpecies.isEmpty()) {
                String newSpecies = selSpecies.get(0);
                if (!newSpecies.equals(draft.species)) draft.lineage = "";
                draft.species = newSpecies;
            }
            if (GameData.SPECIES_LINEAGES.containsKey(draft.species)) {
                List<String> selLineage = lineageSupplierHolder.get(0).get();
                if (!selLineage.isEmpty()) draft.lineage = selLineage.get(0);
            }

            List<String> selClass = classCheckList.getSelected.get();
            if (!selClass.isEmpty()) draft.characterClass = selClass.get(0);

            if ("Point Buy".equals(selectedMethodHolder[0])) {
                Map<String, Integer> vals = new LinkedHashMap<>();
                for (int i = 0; i < 6; i++) vals.put(GameData.STAT_ORDER[i], (Integer) pointBuySpinners[i].getValue());
                draft.stats = vals;
            } else if ("Custom".equals(selectedMethodHolder[0])) {
                Map<String, Integer> vals = new LinkedHashMap<>();
                for (int i = 0; i < 6; i++) vals.put(GameData.STAT_ORDER[i], (Integer) customSpinners[i].getValue());
                draft.stats = vals;
            } else if ("Random Stat Generation".equals(selectedMethodHolder[0]) && randomCurrentValues.size() == 6) {
                draft.stats = new LinkedHashMap<>(randomCurrentValues);
            }

            List<String> selBg = backgroundCheckList.getSelected.get();
            if (!selBg.isEmpty()) draft.background = selBg.get(0);
        };

        performSaveChangesHolder[0] = () -> {
            if (!editModeHolder[0]) return;
            syncDraftFromCurrentUI.run();

            List<String> problems = new ArrayList<>();
            if (touchedSpecies[0] && GameData.SPECIES_LINEAGES.containsKey(draft.species) && !touchedLineage[0]) {
                problems.add("a lineage for your new species");
            }
            if (touchedStats[0] && !touchedASI[0]) {
                problems.add("an ability score increase choice (or skip) for your new base stats");
            }
            if (touchedClass[0] && !touchedClassDetails[0]) {
                problems.add("class details (skills, cantrips, spells) for your new class");
            }
            if (touchedBackground[0] && !touchedBgSkills[0]) {
                problems.add("skill proficiencies for your new background");
            }
            if ((touchedClass[0] || touchedBackground[0]) && !touchedEquipment[0]) {
                problems.add("starting equipment for your new class/background");
            }

            if (!problems.isEmpty()) {
                JOptionPane.showMessageDialog(wizard,
                        "You can't save yet \u2014 please finish choosing:\n\u2022 " + String.join("\n\u2022 ", problems),
                        "Incomplete Changes", JOptionPane.WARNING_MESSAGE);
                return;
            }

            List<Object> all = CharacterStorage.loadSavedCharacters();
            int idx = editIndexHolder[0] != null ? editIndexHolder[0] : -1;
            Map<String, Object> record = CharacterStorage.buildCharacterRecord(draft);
            if (idx >= 0 && idx < all.size()) {
                if (all.get(idx) instanceof Map) CharacterStorage.carryOverProgression((Map<?, ?>) all.get(idx), record);
                all.set(idx, record);
            } else {
                all.add(record);
            }
            CharacterStorage.saveAllCharacters(all);
            refreshSidebar.run();
            outerCardLayout.show(outerCards, "CHARACTERS");
        };

        java.util.function.IntConsumer resetForEdit = (index) -> {
            reset.run();

            List<Object> all = CharacterStorage.loadSavedCharacters();
            if (index < 0 || index >= all.size() || !(all.get(index) instanceof Map)) return;
            Map<?, ?> rec = (Map<?, ?>) all.get(index);

            editIndexHolder[0] = index;

            // A saved character is, by definition, already fully filled in - every segment
            // starts complete and only goes gray again once something is actually changed.
            segNameOk[0] = true;
            segSpeciesOk[0] = true;
            segClassOk[0] = true;
            segStatsOk[0] = true;
            segBackgroundOk[0] = true;
            segDetailsOk[0] = true;
            segEquipmentOk[0] = true;

            draft.name = CharacterStorage.recStr(rec, "name");
            draft.species = CharacterStorage.recStr(rec, "species");
            draft.lineage = CharacterStorage.recStr(rec, "lineage");
            draft.characterClass = CharacterStorage.recStr(rec, "characterClass");
            draft.background = CharacterStorage.recStr(rec, "background");
            draft.backgroundSkills = CharacterStorage.recList(rec, "backgroundSkills");
            draft.stats = CharacterStorage.recStatsMap(rec);
            // The wizard only edits level-1 data: strip level-up feat increases here, and
            // carryOverProgression() re-applies them when the edit is saved.
            for (Map.Entry<String, Integer> en : CharacterStorage.recFeatStatIncreases(rec).entrySet()) {
                draft.stats.merge(en.getKey(), -en.getValue(), Integer::sum);
            }
            draft.cantripsKnown = CharacterStorage.recList(rec, "cantripsKnown");
            draft.spellsKnown = CharacterStorage.recList(rec, "spellsKnown");
            draft.equipment = CharacterStorage.recList(rec, "equipment");
            Integer g = CharacterStorage.recInt(rec, "gold");
            draft.gold = g != null ? g : 0;
            draft.classChoices = CharacterStorage.recChoicesMap(rec);
            draft.asiMode = CharacterStorage.recStr(rec, "asiMode");
            draft.asiIncreases = CharacterStorage.recAsiIncreases(rec);
            draft.backgroundPackage = CharacterStorage.recStr(rec, "backgroundPackage");
            draft.classPackage = CharacterStorage.recStr(rec, "classPackage");

            // Prefill the persistent UI widgets that don't get rebuilt on every visit
            nameField.setText(draft.name);

            if (!draft.species.isEmpty()) {
                speciesCheckList.setSelected.accept(Collections.singletonList(draft.species));
            }
            if (!draft.characterClass.isEmpty()) {
                classCheckList.setSelected.accept(Collections.singletonList(draft.characterClass));
            }
            if (!draft.background.isEmpty()) {
                backgroundCheckList.setSelected.accept(Collections.singletonList(draft.background));
                availableStatsHolder[0] = GameData.BACKGROUND_STATS.get(draft.background);
            }
            if (!draft.stats.isEmpty()) {
                // Prefill the Custom spinners with the ability scores BEFORE the saved ability
                // score increase, since the ASI step re-applies draft.asiIncreases on top of
                // whatever the Stats step produces (this avoids double-counting the bonus).
                Map<String, Integer> baseStats = new LinkedHashMap<>(draft.stats);
                for (Map.Entry<String, Integer> en : draft.asiIncreases.entrySet()) {
                    baseStats.merge(en.getKey(), -en.getValue(), Integer::sum);
                }
                for (int i = 0; i < GameData.STAT_ORDER.length; i++) {
                    Integer v = baseStats.get(GameData.STAT_ORDER[i]);
                    if (v != null) customSpinners[i].setValue(Math.max(1, Math.min(20, v)));
                }
                methodCheckList.setSelected.accept(Collections.singletonList("Custom"));
            }

            if (("PLUS2PLUS1".equals(draft.asiMode) || "PLUS1X3".equals(draft.asiMode)) && !draft.asiIncreases.isEmpty()) {
                String modeLabel = draft.asiMode.equals("PLUS2PLUS1") ? "+2 / +1" : "+1 / +1 / +1";
                increaseModeCheckList.setSelected.accept(Collections.singletonList(modeLabel));
                if (draft.asiMode.equals("PLUS2PLUS1")) {
                    String twoStat = null;
                    String oneStat = null;
                    for (Map.Entry<String, Integer> en : draft.asiIncreases.entrySet()) {
                        if (en.getValue() >= 2) twoStat = en.getKey();
                        else oneStat = en.getKey();
                    }
                    if (twoStat != null && activeCombos.size() > 0) activeCombos.get(0).setSelectedItem(twoStat);
                    if (oneStat != null && activeCombos.size() > 1) activeCombos.get(1).setSelectedItem(oneStat);
                } else {
                    List<String> statList = new ArrayList<>(draft.asiIncreases.keySet());
                    for (int i = 0; i < activeCombos.size() && i < statList.size(); i++) {
                        activeCombos.get(i).setSelectedItem(statList.get(i));
                    }
                }
            } else if (!skipCheck.isSelected()) {
                skipCheck.doClick();
            }

            editModeHolder[0] = true;
            updateSaveButtonLabels.run();
            currentSegmentHolder[0] = 0;
            updateProgressBar.run();
            refreshSidebar.run();
            innerCardLayout.show(innerCards, "NAME");
        };

        return new WizardResult(wizard, reset, resetForEdit);
    }

    // ---------------------------------------------------------------
    // Semi-transparent panel used for translucent info/sidebar surfaces
    // ---------------------------------------------------------------
    static class TranslucentPanel extends JPanel {
        private final Color fill;

        TranslucentPanel(Color fill) {
            super();
            this.fill = fill;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // Simple filled black circle icon, used as a placeholder character portrait
    static class CircleIcon extends JPanel {
        CircleIcon(int size) {
            setPreferredSize(new Dimension(size, size));
            setMinimumSize(new Dimension(size, size));
            setMaximumSize(new Dimension(size, size));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Color.BLACK);
            g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
            g2.dispose();
        }
    }

    // Custom-painted shield displaying Armor Class prominently; nominal fixed box size like
    // CircleIcon, but every coordinate drawn is a fraction of the box's own width/height.
    static class ShieldPanel extends JPanel {
        private final int ac;

        ShieldPanel(int ac) {
            this.ac = ac;
            Dimension d = new Dimension(150, 168);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(d);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            double pad = 4;
            double dw = w - pad * 2, dh = h - pad * 2;

            Path2D.Double shield = new Path2D.Double();
            shield.moveTo(pad + dw * 0.06, pad + dh * 0.08);
            shield.lineTo(pad + dw * 0.94, pad + dh * 0.08);
            shield.quadTo(pad + dw * 1.02, pad + dh * 0.45, pad + dw * 0.5, pad + dh * 1.0);
            shield.quadTo(pad + dw * -0.02, pad + dh * 0.45, pad + dw * 0.06, pad + dh * 0.08);
            shield.closePath();

            GradientPaint fill = new GradientPaint(0, (float) pad, BG_FIELD, 0, h - (float) pad, BG_PANEL);
            g2.setPaint(fill);
            g2.fill(shield);

            g2.setColor(GOLD);
            g2.setStroke(new BasicStroke(2.5f));
            g2.draw(shield);

            g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(11, h / 14)));
            g2.setColor(TEXT_MUTED);
            FontMetrics fmCap = g2.getFontMetrics();
            String cap = "ARMOR CLASS";
            g2.drawString(cap, (w - fmCap.stringWidth(cap)) / 2f, (float) (pad + dh * 0.30));

            Font numFont = new Font("Serif", Font.BOLD, Math.max(28, h / 3));
            g2.setFont(numFont);
            FontMetrics fm = g2.getFontMetrics();
            String s = String.valueOf(ac);
            int tx = (w - fm.stringWidth(s)) / 2;
            int ty = (int) (pad + dh * 0.72);
            g2.setColor(BG_DARK);
            g2.drawString(s, tx + 1, ty + 1);
            g2.setColor(TEXT_LIGHT);
            g2.drawString(s, tx, ty);

            g2.dispose();
        }
    }

    // A JPanel that reports itself Scrollable so a JScrollPane stretches it to the viewport's
    // width (plain JPanel is not Scrollable, so JViewport never grows it past its preferred
    // size, which breaks GridBagLayout weightx-based resizing inside it).
    static class ScrollableContentPanel extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 100;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    // ---------------------------------------------------------------
    // Wizard panel + its reset callbacks
    // ---------------------------------------------------------------
    static class WizardResult {
        final JPanel panel;
        final Runnable reset;
        final java.util.function.IntConsumer resetForEdit;

        WizardResult(JPanel panel, Runnable reset, java.util.function.IntConsumer resetForEdit) {
            this.panel = panel;
            this.reset = reset;
            this.resetForEdit = resetForEdit;
        }
    }

    // ---------------------------------------------------------------
    // Character detail panel + its "show this character" callback
    // ---------------------------------------------------------------
    static class CharacterDetailResult {
        final JPanel panel;
        final java.util.function.IntConsumer show;

        CharacterDetailResult(JPanel panel, java.util.function.IntConsumer show) {
            this.panel = panel;
            this.show = show;
        }
    }
}