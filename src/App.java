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
        DataPaths.init(args); // no args on desktop; "--web --data=..." when run in the browser via CheerpJ
        SpellData.loadExtraSpells();
        WeaponData.WEAPON_DB = WeaponData.loadWeaponDatabase();
        // Homebrew from the Asset Library merges into the rules tables after the built-in data loads
        CustomAssets.CHANGE_LISTENERS.add(App::refreshConditionPattern);
        CustomAssets.load();
        CustomAssets.applyToRuntime();
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("D&D Engine");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            // In the browser the window fills the page, so drop CheerpJ's window title bar
            if (DataPaths.web) frame.setUndecorated(true);
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            frame.setSize(900, 600);
            frame.getContentPane().setBackground(BG_DARK);

            CardLayout cardLayout = new CardLayout();
            JPanel cards = new JPanel(cardLayout);
            cards.setBackground(BG_DARK);

            cards.add(buildMainMenu(cardLayout, cards), "MENU");
            MapEditorPanel mapEditor = new MapEditorPanel(cardLayout, cards);
            cards.add(mapEditor.panel, "MAP_EDITOR");
            cards.add(new CombatPanel(cardLayout, cards, mapEditor).panel, "COMBAT");
            AssetLibraryPanel assetLibrary = new AssetLibraryPanel(cardLayout, cards, mapEditor);
            cards.add(assetLibrary.panel, "ASSETS");

            WizardResult wizardResult = buildNewPCWizardPanel(cardLayout, cards);
            cards.add(wizardResult.panel, "NEW_PC_WIZARD");

            LevelUpPanel levelUpPanel = new LevelUpPanel(cardLayout, cards);
            cards.add(levelUpPanel.panel, "LEVEL_UP");

            CharacterDetailResult detailResult = buildCharacterDetailPanel(cardLayout, cards, levelUpPanel.show);
            cards.add(detailResult.panel, "CHARACTER_DETAIL");

            cards.add(buildCharactersPanel(cardLayout, cards, wizardResult.resetForEdit, detailResult.show, levelUpPanel.show, levelUpPanel.showDown), "CHARACTERS");
            // NPCs are Asset Library assets: the NPC button opens the library's NPC editor (stat block + map token)
            cards.add(buildAddCharacterTypePanel(cardLayout, cards, wizardResult.reset, () -> {
                cardLayout.show(cards, "ASSETS");
                // after the library's own componentShown reset to its list
                SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() -> assetLibrary.openEditor(CustomAssets.NPCS, null)));
            }), "ADD_CHAR_TYPE");

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

        // Backup & Restore: Export / Import JSON of all characters and homebrew (browser download/upload on the web)
        JButton btnBackup = overlayButton("Backup & Restore (Export / Import JSON)", false);
        btnBackup.setToolTipText("Save all characters and homebrew to a .json file, or restore one");
        btnBackup.addActionListener(e -> Backup.showOverlay(btnBackup));
        JPanel backupRow = new JPanel(new FlowLayout(FlowLayout.CENTER));
        backupRow.setBackground(BG_DARK);
        backupRow.add(btnBackup);

        JPanel lower = new JPanel();
        lower.setLayout(new BoxLayout(lower, BoxLayout.Y_AXIS));
        lower.setBackground(BG_DARK);
        buttonPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        backupRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        lower.add(buttonPanel);
        lower.add(Box.createRigidArea(new Dimension(0, 28)));
        lower.add(backupRow);

        gbc.gridy = 1;
        gbc.weighty = 0.6;
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.insets = new Insets(40, 0, 0, 0);
        panel.add(lower, gbc);

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
        // Replaces the option list (and info map), keeping any still-valid selection
        java.util.function.BiConsumer<List<String>, Map<String, String>> setOptions;
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
        List<String> opts = new ArrayList<>(options);
        java.util.concurrent.atomic.AtomicReference<Map<String, String>> infoRef = new java.util.concurrent.atomic.AtomicReference<>(infoMap);

        Runnable buildRows = () -> {
          rowsPanel.removeAll();
          rowPanels.clear();
          glyphLabels.clear();
          textLabels.clear();
          visualUpdaters.clear();
          for (String option : opts) {
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

            if (infoRef.get() != null) {
                JButton infoBtn = makeInfoButton(option, infoTitle, infoText, infoRef.get().getOrDefault(option, ""));
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
                public void mouseReleased(MouseEvent e) {
                    if (!App.isClickRelease(e)) return;
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
        };
        buildRows.run();

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
                    for (int i = 0; i < opts.size() && i < rowPanels.size(); i++) {
                        rowPanels.get(i).setVisible(q.isEmpty() || opts.get(i).toLowerCase().contains(q));
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
                    if (opts.contains(v) && selected.size() < maxSelect) selected.add(v);
                }
            }
            refreshHolder[0].run();
        };
        result.setOptions = (newOpts, newInfo) -> {
            opts.clear();
            opts.addAll(newOpts);
            infoRef.set(newInfo);
            selected.retainAll(opts);
            buildRows.run();
            int vis = Math.max(1, Math.min(opts.size(), viewportRows));
            scroll.setPreferredSize(new Dimension(380, vis * rowH + 6));
            if (searchFieldRef != null) searchFieldRef.setText("");
            refreshHolder[0].run();
            if (!enabledFlag[0]) result.setEnabled.accept(false);
            rowsPanel.revalidate();
            rowsPanel.repaint();
            wrap.revalidate();
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
                showConfirmOverlay(topBar, "Unsaved Changes", "Leave without saving changes?", "Leave",
                        () -> outerCardLayout.show(outerCards, "CHARACTERS"));
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
                int spellLevel = "CANTRIPS".equals(item.type) ? 0 : 1;
                List<String> names = new ArrayList<>();
                Map<String, String> infoMap = new LinkedHashMap<>();
                for (Spell sp : Tags.visibleSpells(SpellData.classSpells(className, spellLevel, spellLevel))) {
                    names.add(sp.name);
                    infoMap.put(sp.name, sp.infoText());
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
                                               java.util.function.IntConsumer showDetail, java.util.function.IntConsumer showLevelUp,
                                               java.util.function.IntConsumer showLevelDown) {
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
            List<Object> characters = CharacterStorage.loadWithIds(); // also applies one-time rule updates (2024 species speeds / HP)

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
                    row.setMaximumSize(new Dimension(560, 74));
                    row.setPreferredSize(new Dimension(560, 74));
                    row.setAlignmentX(Component.LEFT_ALIGNMENT);

                    Tokens.Preview circle = new Tokens.Preview(Tokens.read(c.get("token"), Tokens.PC_COLOR),
                            CharacterStorage.recStr(c, "name"), 44, null);
                    circle.setToolTipText("Open character sheet");
                    circle.setCursor(new Cursor(Cursor.HAND_CURSOR));
                    circle.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseReleased(MouseEvent e) {
                            if (!App.isClickRelease(e)) return;
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
                        public void mouseReleased(MouseEvent e) {
                            if (!App.isClickRelease(e)) return;
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
                    if (charLevel > 1) {
                        JButton levelDownButton = new JButton("−");
                        styleSecondaryButton(levelDownButton);
                        levelDownButton.setFont(new Font("SansSerif", Font.BOLD, 14));
                        levelDownButton.setBorder(BorderFactory.createCompoundBorder(
                                new LineBorder(TEXT_MUTED, 1, true), new EmptyBorder(4, 9, 4, 9)));
                        levelDownButton.setToolTipText("Level down to " + (charLevel - 1));
                        levelDownButton.addActionListener(e -> showLevelDown.accept(idx));
                        actionsWrap.add(levelDownButton);
                    }

                    JButton duplicateButton = new JButton("📋");
                    duplicateButton.setFont(new Font("SansSerif", Font.PLAIN, 16));
                    duplicateButton.setForeground(TEXT_MUTED);
                    duplicateButton.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                    duplicateButton.setFocusPainted(false);
                    duplicateButton.setContentAreaFilled(false);
                    duplicateButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
                    duplicateButton.setToolTipText("Duplicate this character");
                    duplicateButton.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseEntered(MouseEvent e) {
                            duplicateButton.setForeground(GOLD);
                        }
                        @Override
                        public void mouseExited(MouseEvent e) {
                            duplicateButton.setForeground(TEXT_MUTED);
                        }
                    });
                    duplicateButton.addActionListener(e -> {
                        CharacterStorage.duplicate(idx);
                        rebuildCharacterListHolder[0].run();
                    });
                    actionsWrap.add(duplicateButton);

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
                        showConfirmOverlay(panel, "Confirm Deletion",
                                "Delete \"" + nameLabel.getText() + "\"? This cannot be undone.", "Delete", () -> {
                                    List<Object> current = CharacterStorage.loadSavedCharacters();
                                    if (idx >= 0 && idx < current.size()) {
                                        current.remove(idx);
                                        CharacterStorage.saveAllCharacters(current);
                                    }
                                    rebuildCharacterListHolder[0].run();
                                });
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

        // Debug / testing: adds a few ready-made characters (skips any that already exist by name)
        JButton testButton = new JButton("+ Add Test Characters");
        styleSecondaryButton(testButton);
        testButton.setToolTipText("Adds " + String.join(", ", TestCharacters.NAMES) + " (any already in the list are skipped)");
        testButton.addActionListener(e -> {
            TestCharacters.addMissing();
            rebuildCharacterListHolder[0].run();
        });
        bottomBar.add(testButton);
        panel.add(bottomBar, BorderLayout.SOUTH);

        return panel;
    }

    static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String safeStr(Object o) {
        return (o == null || "null".equals(String.valueOf(o))) ? "\u2014" : String.valueOf(o);
    }

    // ---------------------------------------------------------------
    // ADD CHARACTER: choose Playable Character or NPC
    // ---------------------------------------------------------------
    private static JPanel buildAddCharacterTypePanel(CardLayout cardLayout, JPanel cards, Runnable resetWizard, Runnable newNpc) {
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
        btnNPC.addActionListener(e -> newNpc.run());
        btnNPC.setToolTipText("Create an NPC (stat block, token and notes) in the Asset Library");

        gbc.gridx = 0; content.add(btnPlayable, gbc);
        gbc.gridx = 1; content.add(btnNPC, gbc);

        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    // ---------------------------------------------------------------
    // CHARACTER DETAIL (blank placeholder page reached by clicking a character's icon)
    // ---------------------------------------------------------------
    private static CharacterDetailResult buildCharacterDetailPanel(CardLayout cardLayout, JPanel cards,
                                                                   java.util.function.IntConsumer showLevelUp) {
        JPanel panel = buildSectionShell("Character", cardLayout, cards, "CHARACTERS");

        CardLayout innerCardLayout = new CardLayout();
        JPanel innerCards = new JPanel(innerCardLayout);
        innerCards.setBackground(BG_DARK);

        ScrollableContentPanel sheetContent = new ScrollableContentPanel();
        sheetContent.setBackground(SHEET_BG);
        JScrollPane sheetScroll = new JScrollPane(sheetContent);
        applyDarkScrollBar(sheetScroll);
        sheetScroll.getViewport().setBackground(SHEET_BG);
        sheetScroll.setBorder(BorderFactory.createEmptyBorder());
        sheetScroll.getVerticalScrollBar().setUnitIncrement(16);

        ScrollableContentPanel spellsContent = new ScrollableContentPanel();
        spellsContent.setBackground(SHEET_BG);
        JScrollPane spellsScroll = new JScrollPane(spellsContent);
        applyDarkScrollBar(spellsScroll);
        spellsScroll.getViewport().setBackground(SHEET_BG);
        spellsScroll.setBorder(BorderFactory.createEmptyBorder());
        spellsScroll.getVerticalScrollBar().setUnitIncrement(16);

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

        int[] currentIdx = {-1};
        java.util.function.IntConsumer[] renderSheet = new java.util.function.IntConsumer[1];
        java.util.function.IntConsumer[] renderSpells = new java.util.function.IntConsumer[1];

        // Each tab saves its own copy of the record, so re-render the tab being shown from disk
        // (otherwise an edit on one tab could overwrite changes made on the other).
        java.util.function.Consumer<String> showTab = tab -> {
            if (currentIdx[0] >= 0) {
                if ("SHEET".equals(tab)) renderSheet[0].accept(currentIdx[0]);
                else renderSpells[0].accept(currentIdx[0]);
            }
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

        // Re-renders only the spells tab (used after slot/cast changes so the tab/scroll stay put)
        renderSpells[0] = idx -> {
            List<Object> characters = CharacterStorage.loadSavedCharacters();
            if (idx < 0 || idx >= characters.size() || !(characters.get(idx) instanceof Map)) return;
            populateCharacterSpells(spellsContent, (Map<?, ?>) characters.get(idx), updated -> {
                List<Object> all = CharacterStorage.loadSavedCharacters();
                if (idx < all.size()) {
                    all.set(idx, updated);
                    CharacterStorage.saveAllCharacters(all);
                }
                renderSpells[0].accept(idx);
            });
        };

        // Re-renders only the sheet tab (used after in-sheet edits so the tab/scroll stay put)
        renderSheet[0] = idx -> {
            List<Object> characters = CharacterStorage.loadSavedCharacters();
            if (idx < 0 || idx >= characters.size() || !(characters.get(idx) instanceof Map)) return;
            Map<?, ?> rec = (Map<?, ?>) characters.get(idx);
            populateCharacterSheet(sheetContent, rec, () -> showLevelUp.accept(idx), updated -> {
                List<Object> all = CharacterStorage.loadSavedCharacters();
                if (idx < all.size()) {
                    all.set(idx, updated);
                    CharacterStorage.saveAllCharacters(all);
                }
                renderSheet[0].accept(idx);
            });
        };

        java.util.function.IntConsumer show = idx -> {
            List<Object> characters = CharacterStorage.loadSavedCharacters();
            if (idx < 0 || idx >= characters.size() || !(characters.get(idx) instanceof Map)) return;
            currentIdx[0] = idx;
            showTab.accept("SHEET");
            cardLayout.show(cards, "CHARACTER_DETAIL");
        };

        return new CharacterDetailResult(panel, show);
    }

    // ---------------------------------------------------------------
    // CHARACTER SHEET (classic 3-column 5e layout, centered at SHEET_MAX_WIDTH)
    // ---------------------------------------------------------------
    static final Color SHEET_BG = new Color(0x1E, 0x20, 0x2C);
    static final Color SHEET_CARD = new Color(0x25, 0x28, 0x36);
    static final Color SHEET_ROW_ALT = new Color(0x2A, 0x2E, 0x3F);
    static final Color SHEET_BORDER = new Color(0x3A, 0x3F, 0x55);
    static final Color GOLD_SOFT = new Color(212, 175, 55, 130);
    static final int SHEET_MAX_WIDTH = 1100;
    private static final String[] STAT_FULL_NAMES = {"Strength", "Dexterity", "Constitution", "Intelligence", "Wisdom", "Charisma"};

    private static void populateCharacterSheet(JPanel container, Map<?, ?> rec, Runnable onLevelUp,
                                               java.util.function.Consumer<Map<String, Object>> onSave) {
        container.removeAll();
        container.setLayout(new MaxWidthCenterLayout(SHEET_MAX_WIDTH));
        container.setBackground(SHEET_BG);
        container.setBorder(new EmptyBorder(20, 16, 28, 16));

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
        List<String> equipment = CharacterStorage.recList(rec, "equipment");
        int ac = WeaponRules.armorClass(rec); // a shield only counts while held (hand system)

        String lineageSuffix = lineage.isEmpty() ? "" : " (" + lineage + ")";
        String classText;
        if (!classLevels.isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<String, Integer> en : classLevels.entrySet()) parts.add(en.getKey() + " " + en.getValue());
            classText = String.join(" / ", parts);
        } else {
            classText = characterClass.isEmpty() ? "—" : characterClass + " " + level;
        }

        // ================= HEADER =================
        JPanel nameBox = new JPanel(new BorderLayout(0, 4));
        nameBox.setBackground(SHEET_CARD);
        nameBox.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(GOLD_SOFT, 1, true), new EmptyBorder(12, 18, 10, 18)));
        JLabel nameLbl = new JLabel(name.isEmpty() ? "Unnamed Character" : name);
        nameLbl.setForeground(GOLD);
        nameLbl.setFont(new Font("Serif", Font.BOLD, 32));
        nameBox.add(nameLbl, BorderLayout.CENTER);
        nameBox.add(sheetCaption("CHARACTER NAME", SwingConstants.LEFT), BorderLayout.SOUTH);

        // Map token: click to change its shape, color or picture
        Map<String, Object> tokenLook = Tokens.read(rec.get("token"), Tokens.PC_COLOR);
        Tokens.Preview tokenPreview = new Tokens.Preview(tokenLook, name, 56, GOLD);
        tokenPreview.setToolTipText("Map token: click to change it");
        clickable(tokenPreview, () -> {
            JPanel body = new JPanel(new BorderLayout(0, 14));
            body.setOpaque(false);
            Tokens.Editor ed = Tokens.editor(tokenLook, () -> name, Tokens.PC_COLOR, null);
            body.add(ed.panel, BorderLayout.CENTER);
            JLabel where = new JLabel("<html><div style='width:440px'>Shown on the Combat map. Size " + escapeHtml(
                    CharacterStorage.recStr(rec, "size").isEmpty() ? "Medium" : CharacterStorage.recStr(rec, "size"))
                    + ": " + CombatPanel.footprintText(CharacterStorage.recStr(rec, "size")) + ".</div></html>");
            where.setForeground(TEXT_MUTED);
            where.setFont(new Font("SansSerif", Font.PLAIN, 12));
            body.add(where, BorderLayout.NORTH);
            Runnable[] close = new Runnable[1];
            JButton cancel = overlayButton("Cancel", false);
            JButton save = overlayButton("Save Token", true);
            cancel.addActionListener(e -> close[0].run());
            save.addActionListener(e -> {
                Map<String, Object> work = recCopy(rec);
                work.put("token", ed.value());
                close[0].run();
                onSave.accept(work);
            });
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            buttons.setOpaque(false);
            buttons.add(cancel);
            buttons.add(save);
            body.add(buttons, BorderLayout.SOUTH);
            close[0] = showSheetOverlay(tokenPreview, "Map Token", body, null);
        });
        JPanel tokenWrap = new JPanel(new GridBagLayout());
        tokenWrap.setOpaque(false);
        tokenWrap.setBorder(new EmptyBorder(0, 0, 0, 12));
        tokenWrap.add(tokenPreview);
        nameBox.add(tokenWrap, BorderLayout.WEST);

        JPanel idGrid = new JPanel(new GridLayout(2, 2, 8, 8));
        idGrid.setOpaque(false);
        idGrid.add(clickable(headerField("CLASS & LEVEL", classText), () -> {
            List<String[]> rows = new ArrayList<>();
            StringBuilder desc = new StringBuilder();
            for (Map.Entry<String, Integer> en : classLevels.entrySet()) {
                String[] info = GameData.CLASS_INFO.get(en.getKey());
                rows.add(row(en.getKey(), "Level " + en.getValue() + (info != null ? " · Hit die " + info[0] : "")));
                if (info != null) desc.append(en.getKey()).append(": ").append(info[1]).append("\n");
            }
            rows.add(row("Total level", String.valueOf(level)));
            rows.add(row("Proficiency bonus", signed(profBonus)));
            showInfoOverlay(idGrid, classText, "Class & Level", desc.toString().trim(), rows);
        }));
        idGrid.add(clickable(headerField("BACKGROUND", background.isEmpty() ? "—" : background), () -> {
            List<String[]> rows = new ArrayList<>();
            List<String> bgSkills = CharacterStorage.recList(rec, "backgroundSkills");
            rows.add(row("Skill proficiencies", bgSkills.isEmpty() ? "—" : String.join(", ", bgSkills)));
            List<String> incs = new ArrayList<>();
            for (Map.Entry<String, Integer> en : CharacterStorage.recAsiIncreases(rec).entrySet()) incs.add(en.getKey() + " +" + en.getValue());
            rows.add(row("Ability increases", incs.isEmpty() ? "—" : String.join(", ", incs)));
            showInfoOverlay(idGrid, background.isEmpty() ? "Background" : background, "Background",
                    GameData.BACKGROUND_INFO.getOrDefault(background, ""), rows);
        }));
        idGrid.add(clickable(headerField("SPECIES", (species.isEmpty() ? "—" : species) + lineageSuffix), () -> {
            List<String[]> rows = new ArrayList<>();
            if (!lineage.isEmpty()) rows.add(row("Lineage", lineage));
            String size = CharacterStorage.recStr(rec, "size");
            String type = CharacterStorage.recStr(rec, "creatureType");
            if (!size.isEmpty()) rows.add(row("Size", size));
            if (!type.isEmpty()) rows.add(row("Creature type", type));
            rows.add(row("Speed", speed != null ? speed + " ft." : "—"));
            int dv = SpeciesTraits.darkvision(rec);
            rows.add(row("Darkvision", dv > 0 ? dv + " ft." : "—"));
            String defenses = DamageRules.summary(SpeciesTraits.damageMods(rec));
            if (!defenses.isEmpty()) rows.add(row("Damage", defenses));
            List<String> traitNames = new ArrayList<>();
            for (SpeciesTraits.Trait t : SpeciesTraits.traits(rec)) traitNames.add(t.name);
            if (!traitNames.isEmpty()) rows.add(row("Traits", String.join(", ", traitNames)));
            showInfoOverlay(idGrid, species.isEmpty() ? "Species" : species + lineageSuffix, "Species",
                    GameData.SPECIES_INFO.getOrDefault(species, "")
                            + (traitNames.isEmpty() ? "" : "\n\nEach trait is listed under Features & Traits; click one for its full rules."), rows);
        }));
        if (level < FeatData.MAX_LEVEL && onLevelUp != null) {
            idGrid.add(levelUpButton(level, onLevelUp));
        } else {
            idGrid.add(headerField("TOTAL LEVEL", String.valueOf(level)));
        }

        JPanel header = new JPanel(new GridBagLayout());
        header.setOpaque(false);
        GridBagConstraints hc = new GridBagConstraints();
        hc.gridy = 0;
        hc.fill = GridBagConstraints.BOTH;
        hc.weighty = 1.0;
        hc.gridx = 0;
        hc.weightx = 0.35;
        hc.insets = new Insets(0, 0, 0, 12);
        header.add(fixedWidth(nameBox, 380), hc);
        hc.gridx = 1;
        hc.weightx = 0.65;
        hc.insets = new Insets(0, 0, 0, 0);
        header.add(fixedWidth(idGrid, 708), hc);

        // ================= COLUMN 1 =================
        JPanel abilityStrip = new JPanel(new GridLayout(6, 1, 0, 8));
        abilityStrip.setOpaque(false);
        Map<String, Integer> bgIncreases = CharacterStorage.recAsiIncreases(rec);
        for (int i = 0; i < GameData.STAT_ORDER.length; i++) {
            String stat = GameData.STAT_ORDER[i];
            String fullName = STAT_FULL_NAMES[i];
            int score = stats.getOrDefault(stat, 10);
            abilityStrip.add(clickable(buildAbilityBox(stat, score), () -> {
                List<String[]> rows = new ArrayList<>();
                rows.add(row("Score", String.valueOf(score)));
                rows.add(row("Modifier", signed(GameData.abilityModifier(score)) + "  (score − 10, ÷ 2, rounded down)"));
                Integer bgInc = bgIncreases.get(stat);
                if (bgInc != null) rows.add(row("Includes background increase", "+" + bgInc + (background.isEmpty() ? "" : " (" + background + ")")));
                for (Map<String, Object> f : feats) {
                    Integer inc = CharacterStorage.featStatIncreases(f).get(stat);
                    if (inc != null) rows.add(row("Includes " + f.get("name"), "+" + inc));
                }
                showInfoOverlay(abilityStrip, fullName + " (" + stat + ")", "Ability score", GameData.STAT_INFO.get(stat), rows);
            }));
        }

        String[] saveUses = {
                "Resist being shoved, held, or physically overpowered.",
                "Dodge out of the way of area effects like fireballs and traps.",
                "Endure poison, disease, and maintain concentration on spells.",
                "Resist mental assaults and illusions that target your reasoning.",
                "Resist charm, fear, and effects that target your willpower.",
                "Resist effects that would banish you or overwhelm your sense of self."};
        JPanel saveRows = new JPanel(new GridLayout(0, 1, 0, 0));
        saveRows.setOpaque(false);
        for (int i = 0; i < GameData.STAT_ORDER.length; i++) {
            String stat = GameData.STAT_ORDER[i];
            String fullName = STAT_FULL_NAMES[i];
            String use = saveUses[i];
            boolean prof = saveProfs.contains(stat);
            int abilityMod = GameData.abilityModifier(stats.getOrDefault(stat, 10));
            int mod = abilityMod + (prof ? profBonus : 0);
            saveRows.add(clickable(sheetProfRow(prof, signed(mod), fullName, null, false), () -> {
                String source = null;
                if (saves != null && Arrays.asList(saves).contains(stat)) source = characterClass + " class";
                for (Map<String, Object> f : feats) {
                    if ("Resilient".equals(f.get("name")) && CharacterStorage.featStatIncreases(f).containsKey(stat)) {
                        source = source == null ? "Resilient feat" : source + ", Resilient feat";
                    }
                }
                List<String[]> rows = new ArrayList<>();
                rows.add(row(stat + " modifier", signed(abilityMod)));
                rows.add(row("Proficiency", prof ? signed(profBonus) : "—"));
                rows.add(row("Total", signed(mod)));
                rows.add(row("Proficient from", source != null ? source : "Not proficient"));
                showInfoOverlay(saveRows, fullName + " Saving Throw", stat + " save",
                        "Roll d20 + this bonus when an effect calls for a " + fullName + " save. " + use, rows);
            }));
        }

        Map<String, String> skillSources = CharacterStorage.recSkillSources(rec);
        JPanel skillRows = new JPanel(new GridLayout(0, 1, 0, 0));
        skillRows.setOpaque(false);
        int skillIdx = 0;
        for (String skill : GameData.ALL_SKILLS) {
            boolean prof = skillProfs.contains(skill);
            String ability = GameData.SKILL_ABILITY.get(skill);
            int abilityMod = GameData.abilityModifier(stats.getOrDefault(ability, 10));
            int mod = abilityMod + (prof ? profBonus : 0);
            skillRows.add(clickable(sheetProfRow(prof, signed(mod), skill, ability, skillIdx++ % 2 == 1), () -> {
                List<String[]> rows = new ArrayList<>();
                rows.add(row(ability + " modifier", signed(abilityMod)));
                rows.add(row("Proficiency", prof ? signed(profBonus) : "—"));
                rows.add(row("Total", signed(mod)));
                rows.add(row("Proficient from", prof ? skillSources.getOrDefault(skill, "—") : "Not proficient"));
                showInfoOverlay(skillRows, skill, ability + " skill",
                        GameData.SKILL_INFO.getOrDefault(skill, "") + "\nRoll d20 + this bonus for " + skill + " checks.", rows);
            }));
        }

        JPanel profBadge = clickable(horizontalBadge(signed(profBonus), "PROFICIENCY BONUS"), () -> showInfoOverlay(
                skillRows, "Proficiency Bonus", "Based on total level",
                "Added to attack rolls, saving throws, and skill checks you are proficient in. It goes up at levels 5, 9, 13 and 17.",
                Arrays.asList(row("Total level", String.valueOf(level)), row("Bonus", signed(profBonus)))));

        JPanel innerLeft = sheetStack(10, false,
                profBadge,
                sheetCard("Saving Throws", saveRows),
                sheetCard("Skills", skillRows));

        JPanel col1 = new JPanel(new GridBagLayout());
        col1.setOpaque(false);
        GridBagConstraints c1 = new GridBagConstraints();
        c1.gridy = 0;
        c1.gridx = 0;
        c1.fill = GridBagConstraints.BOTH;
        c1.anchor = GridBagConstraints.NORTH;
        c1.insets = new Insets(0, 0, 0, 10);
        col1.add(fixedWidth(abilityStrip, 105), c1);
        c1.gridx = 1;
        c1.weightx = 1.0;
        c1.insets = new Insets(0, 0, 0, 0);
        col1.add(innerLeft, c1);
        c1.gridx = 0;
        c1.gridy = 1;
        c1.gridwidth = 2;
        c1.fill = GridBagConstraints.HORIZONTAL;
        c1.insets = new Insets(10, 0, 0, 0);
        boolean perceptionProf = skillProfs.contains("Perception");
        col1.add(clickable(horizontalBadge(String.valueOf(passivePerception), "PASSIVE WISDOM (PERCEPTION)"), () -> showInfoOverlay(
                col1, "Passive Perception", "10 + Perception bonus",
                "Your awareness when you aren't actively searching. The DM compares it against hidden creatures' Stealth and against concealed objects.",
                Arrays.asList(row("Base", "10"), row("WIS modifier", signed(wisMod)),
                        row("Proficiency", perceptionProf ? signed(profBonus) + " (" + skillSources.getOrDefault("Perception", "") + ")" : "—"),
                        row("Total", String.valueOf(passivePerception))))), c1);
        c1.gridy = 2;
        c1.weighty = 1.0;
        c1.fill = GridBagConstraints.BOTH;
        col1.add(sheetCard("Other Proficiencies & Languages", otherProficienciesContent(rec, characterClass, species)), c1);
        c1.gridy = 3;
        c1.weighty = 0.0;
        c1.fill = GridBagConstraints.HORIZONTAL;
        col1.add(conditionsCard(rec, onSave), c1);

        // ================= COLUMN 2 =================
        JPanel combatRow = new JPanel(new GridLayout(1, 3, 8, 0));
        combatRow.setOpaque(false);
        combatRow.add(clickable(new ShieldPanel(ac, 108, 116), () -> {
            // Display-only breakdown mirroring GameData.computeAC
            List<String[]> rows = new ArrayList<>();
            String bodyLabel = "Unarmored";
            String bodyValue = "10 + DEX (" + signed(dexMod) + ")";
            int bestBody = 10 + dexMod;
            int shield = 0;
            String shieldName = "Shield";
            WeaponRules.Hands heldNow = WeaponRules.hands(rec);
            boolean unheldShield = false;
            for (String item : equipment) {
                String base = GameData.stripEquipmentCount(item);
                GameData.ArmorInfo info = GameData.ARMOR_AC.get(base);
                if (info == null) continue;
                if (info.isShield && !base.equalsIgnoreCase(heldNow.off) && !base.equalsIgnoreCase(heldNow.main)) {
                    unheldShield = true; // carried but not held: no AC (hand system)
                    continue;
                }
                if (info.isShield) {
                    if (GameData.shieldBonus(info) > shield) {
                        shield = GameData.shieldBonus(info);
                        shieldName = base;
                    }
                    continue;
                }
                int dexPart = info.maxDexBonus <= 0 ? 0 : Math.min(dexMod, info.maxDexBonus);
                if (info.baseAC + dexPart > bestBody) {
                    bestBody = info.baseAC + dexPart;
                    bodyLabel = base;
                    bodyValue = info.maxDexBonus <= 0 ? info.baseAC + " (no DEX)"
                            : info.baseAC + " + DEX (" + signed(dexPart) + (info.maxDexBonus == Integer.MAX_VALUE ? "" : ", max " + info.maxDexBonus) + ")";
                }
            }
            rows.add(row(bodyLabel, bodyValue));
            if (shield > 0) rows.add(row(shieldName + " (held)", "+" + shield));
            else if (unheldShield) rows.add(row("Shield (not held)", "+0: put it in a hand under Hands & Attacks"));
            if (SpeciesTraits.acBonus(rec) > 0) rows.add(row("Integrated Protection (Warforged)", "+" + SpeciesTraits.acBonus(rec)));
            rows.add(row("Armor Class", String.valueOf(ac)));
            showInfoOverlay(combatRow, "Armor Class", "Defense",
                    "How hard you are to hit. An attack hits if its roll equals or beats your AC. Comes from your best armor in Equipment (or 10 + DEX unarmored), plus a shield.", rows);
        }));
        combatRow.add(clickable(statTile(signed(dexMod), "INITIATIVE"), () -> showInfoOverlay(
                combatRow, "Initiative", "Combat order",
                "At the start of combat, everyone rolls d20 + Initiative. Higher results act first.",
                Arrays.asList(row("DEX modifier", signed(dexMod)), row("Initiative", signed(dexMod))))));
        combatRow.add(clickable(statTile(speed != null ? speed + " ft." : "—", "SPEED"), () -> showInfoOverlay(
                combatRow, "Speed", "Movement",
                "How far you can move on your turn. Difficult terrain costs double, and you can split your movement before and after your action.",
                Arrays.asList(row("From species", (species.isEmpty() ? "—" : species + lineageSuffix)),
                        row("Walking speed", speed != null ? speed + " ft." : "—")))));

        JPanel hdDsRow = new JPanel(new GridLayout(1, 2, 8, 0));
        hdDsRow.setOpaque(false);
        hdDsRow.add(clickable(hitDiceCard(hitDice), () -> {
            List<String[]> rows = new ArrayList<>();
            for (Map.Entry<String, Integer> en : classLevels.entrySet()) {
                String[] info = GameData.CLASS_INFO.get(en.getKey());
                rows.add(row(en.getKey() + " " + en.getValue(), en.getValue() + (info != null ? info[0] : "")));
            }
            rows.add(row("Total", hitDice));
            showInfoOverlay(hdDsRow, "Hit Dice", "One per class level",
                    "During a Short Rest you can spend Hit Dice: roll each one and add your CON modifier to regain that many Hit Points. A Long Rest restores all spent Hit Dice.", rows);
        }));
        hdDsRow.add(clickable(deathSavesCard(), () -> showInfoOverlay(
                hdDsRow, "Death Saves", "At 0 Hit Points",
                "When you start your turn at 0 HP, roll a d20 with no modifiers. 10 or higher is a success, 9 or lower a failure. Three successes and you stabilise; three failures and you die. A 20 restores 1 HP; a 1 counts as two failures. Click the pips to track them (they aren't saved).",
                null)));

        int conMod = GameData.abilityModifier(stats.getOrDefault("CON", 10));
        JPanel hpCard = clickable(hitPointsCard(hp), () -> {
            List<String[]> rows = new ArrayList<>();
            int die = GameData.hitDieMax(characterClass);
            if (die > 0) rows.add(row("Level 1 (max d" + die + " + CON)", "+" + Math.max(1, die + conMod)));
            List<Integer> rolls = CharacterStorage.recHpRolls(rec);
            for (int i = 0; i < rolls.size(); i++) {
                rows.add(row("Level " + (i + 2) + " (rolled " + rolls.get(i) + " + CON)", "+" + CharacterStorage.hpGainForRoll(rolls.get(i), conMod)));
            }
            int speciesHp = SpeciesTraits.hpBonus(rec);
            if (speciesHp > 0) rows.add(row("Dwarven Toughness (+1 per level)", "+" + speciesHp));
            rows.add(row("Hit Point Maximum", hp != null ? String.valueOf(hp) : "—"));
            showInfoOverlay(combatRow, "Hit Points", "CON modifier " + signed(conMod),
                    "How much damage you can take. At 0 HP you fall Unconscious and start making Death Saves. Your maximum grows by a hit die roll + CON modifier each level.", rows);
        });

        JPanel col2 = sheetStack(10, true,
                combatRow,
                hpCard,
                hdDsRow,
                SheetWeapons.handsAttacksCard(rec, onSave),
                SheetWeapons.masteryCard(rec, onSave),
                equipmentCard(rec, equipment, onSave));

        // ================= COLUMN 3 =================
        JPanel col3 = sheetCard("Features & Traits", featuresContent(rec, characterClass, species, lineageSuffix, feats));

        // ================= BODY =================
        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.gridy = 0;
        g.fill = GridBagConstraints.BOTH;
        g.weighty = 1.0;
        g.anchor = GridBagConstraints.NORTH;
        g.gridx = 0;
        g.weightx = 0.30;
        g.insets = new Insets(0, 0, 0, 14);
        body.add(fixedWidth(col1, 330), g);
        g.gridx = 1;
        g.weightx = 0.34;
        body.add(fixedWidth(col2, 360), g);
        g.gridx = 2;
        g.weightx = 0.36;
        g.insets = new Insets(0, 0, 0, 0);
        body.add(fixedWidth(col3, 382), g);

        JPanel sheet = new JPanel(new BorderLayout(0, 14));
        sheet.setOpaque(false);
        sheet.add(header, BorderLayout.NORTH);
        sheet.add(body, BorderLayout.CENTER);
        container.add(sheet);

        container.revalidate();
        container.repaint();
    }

    // Wraps a component so its preferred width is fixed (height still comes from its content)
    private static JPanel fixedWidth(JComponent c, int width) {
        JPanel w = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(width, super.getPreferredSize().height);
            }

            @Override
            public Dimension getMinimumSize() {
                return new Dimension(width, super.getMinimumSize().height);
            }
        };
        w.setOpaque(false);
        w.add(c, BorderLayout.CENTER);
        return w;
    }

    // Vertical stack of full-width items; either the last item or trailing glue absorbs extra height
    private static JPanel sheetStack(int gap, boolean stretchLast, JComponent... items) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1.0;
        c.anchor = GridBagConstraints.NORTH;
        for (int i = 0; i < items.length; i++) {
            boolean last = i == items.length - 1;
            c.gridy = i;
            c.insets = new Insets(i == 0 ? 0 : gap, 0, 0, 0);
            c.fill = (last && stretchLast) ? GridBagConstraints.BOTH : GridBagConstraints.HORIZONTAL;
            c.weighty = (last && stretchLast) ? 1.0 : 0.0;
            p.add(items[i], c);
        }
        if (!stretchLast) {
            c.gridy = items.length;
            c.weighty = 1.0;
            c.fill = GridBagConstraints.BOTH;
            c.insets = new Insets(0, 0, 0, 0);
            p.add(Box.createGlue(), c);
        }
        return p;
    }

    static JPanel sheetCard(String title, JComponent content) {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setBackground(SHEET_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(SHEET_BORDER, 1, true), new EmptyBorder(10, 10, 10, 10)));
        if (title != null) {
            JLabel t = new JLabel(title.toUpperCase());
            t.setForeground(GOLD);
            t.setFont(new Font("SansSerif", Font.BOLD, 12));
            card.add(t, BorderLayout.NORTH);
        }
        if (content != null) card.add(content, BorderLayout.CENTER);
        return card;
    }

    private static JLabel sheetCaption(String text, int align) {
        JLabel l = new JLabel(text, align);
        l.setForeground(TEXT_MUTED);
        l.setFont(new Font("SansSerif", Font.BOLD, 10));
        return l;
    }

    private static JPanel headerField(String caption, String value) {
        JPanel box = new JPanel(new BorderLayout(0, 2));
        box.setBackground(SHEET_CARD);
        box.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(SHEET_BORDER, 1, true), new EmptyBorder(7, 12, 6, 12)));
        JLabel val = new JLabel(value);
        val.setForeground(TEXT_LIGHT);
        val.setFont(new Font("SansSerif", Font.BOLD, 16));
        box.add(val, BorderLayout.CENTER);
        box.add(sheetCaption(caption, SwingConstants.LEFT), BorderLayout.SOUTH);
        return box;
    }

    private static JButton levelUpButton(int level, Runnable onLevelUp) {
        JButton b = new JButton("LEVEL UP  →  " + (level + 1));
        b.setFont(new Font("SansSerif", Font.BOLD, 14));
        b.setForeground(GOLD);
        b.setBackground(SHEET_CARD);
        b.setOpaque(true);
        b.setFocusPainted(false);
        b.setContentAreaFilled(true);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.setBorder(new LineBorder(GOLD, 1, true));
        b.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                b.setBackground(GOLD);
                b.setForeground(SHEET_BG);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                b.setBackground(SHEET_CARD);
                b.setForeground(GOLD);
            }
        });
        b.addActionListener(e -> onLevelUp.run());
        return b;
    }

    private static JPanel buildAbilityBox(String stat, int score) {
        JPanel box = new JPanel(new BorderLayout(0, 0));
        box.setBackground(SHEET_CARD);
        box.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(SHEET_BORDER, 1, true), new EmptyBorder(6, 6, 7, 6)));

        JLabel statLabel = new JLabel(stat, SwingConstants.CENTER);
        statLabel.setForeground(TEXT_MUTED);
        statLabel.setFont(new Font("SansSerif", Font.BOLD, 12));

        JLabel modLabel = new JLabel(signed(GameData.abilityModifier(score)), SwingConstants.CENTER);
        modLabel.setForeground(GOLD);
        modLabel.setFont(new Font("Serif", Font.BOLD, 28));

        JLabel scoreLabel = new JLabel(String.valueOf(score), SwingConstants.CENTER);
        scoreLabel.setForeground(TEXT_LIGHT);
        scoreLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        scoreLabel.setOpaque(true);
        scoreLabel.setBackground(SHEET_ROW_ALT);
        scoreLabel.setBorder(new LineBorder(GOLD_SOFT, 1, true));
        Dimension pill = new Dimension(40, 20);
        scoreLabel.setPreferredSize(pill);
        JPanel pillWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        pillWrap.setOpaque(false);
        pillWrap.add(scoreLabel);

        box.add(statLabel, BorderLayout.NORTH);
        box.add(modLabel, BorderLayout.CENTER);
        box.add(pillWrap, BorderLayout.SOUTH);
        return box;
    }

    static String signed(int n) {
        return n >= 0 ? "+" + n : String.valueOf(n);
    }

    // Small value box on the left, caption on the right (Proficiency Bonus, Passive Perception)
    private static JPanel horizontalBadge(String value, String label) {
        JPanel badge = new JPanel(new BorderLayout(10, 0));
        badge.setBackground(SHEET_CARD);
        badge.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(SHEET_BORDER, 1, true), new EmptyBorder(5, 5, 5, 10)));
        JLabel v = new JLabel(value, SwingConstants.CENTER);
        v.setForeground(GOLD);
        v.setFont(new Font("Serif", Font.BOLD, 20));
        v.setOpaque(true);
        v.setBackground(SHEET_ROW_ALT);
        v.setBorder(new LineBorder(GOLD_SOFT, 1, true));
        v.setPreferredSize(new Dimension(46, 32));
        JLabel l = new JLabel(label);
        l.setForeground(TEXT_LIGHT);
        l.setFont(new Font("SansSerif", Font.BOLD, 11));
        badge.add(v, BorderLayout.WEST);
        badge.add(l, BorderLayout.CENTER);
        return badge;
    }

    // One save/skill row: proficiency pip, modifier, then name right beside it
    private static JPanel sheetProfRow(boolean prof, String mod, String name, String abilityTag, boolean tinted) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(tinted);
        row.setBackground(SHEET_ROW_ALT);
        row.setBorder(new EmptyBorder(3, 6, 3, 6));

        JLabel pip = new JLabel(prof ? "●" : "○");
        pip.setForeground(prof ? GOLD : TEXT_MUTED);
        pip.setFont(new Font("SansSerif", Font.PLAIN, 11));
        lockSize(pip, 12, 18);

        JLabel modL = new JLabel(mod, SwingConstants.RIGHT);
        modL.setForeground(prof ? GOLD : TEXT_LIGHT);
        modL.setFont(new Font("SansSerif", Font.BOLD, 14));
        lockSize(modL, 26, 18);

        JLabel nameL = new JLabel(name);
        nameL.setForeground(TEXT_LIGHT);
        nameL.setFont(new Font("SansSerif", Font.PLAIN, 13));

        row.add(pip);
        row.add(Box.createRigidArea(new Dimension(4, 0)));
        row.add(modL);
        row.add(Box.createRigidArea(new Dimension(8, 0)));
        row.add(nameL);
        row.add(Box.createHorizontalGlue());
        if (abilityTag != null) {
            JLabel tag = new JLabel(abilityTag);
            tag.setForeground(TEXT_MUTED);
            tag.setFont(new Font("SansSerif", Font.PLAIN, 10));
            row.add(tag);
        }
        return row;
    }

    private static void lockSize(JComponent c, int w, int h) {
        Dimension d = new Dimension(w, h);
        c.setPreferredSize(d);
        c.setMinimumSize(d);
        c.setMaximumSize(d);
    }

    private static JPanel statTile(String value, String caption) {
        JPanel tile = new JPanel(new BorderLayout(0, 2));
        tile.setBackground(SHEET_CARD);
        tile.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(SHEET_BORDER, 1, true), new EmptyBorder(8, 4, 8, 4)));
        JLabel v = new JLabel(value, SwingConstants.CENTER);
        v.setForeground(TEXT_LIGHT);
        v.setFont(new Font("Serif", Font.BOLD, 26));
        tile.add(v, BorderLayout.CENTER);
        tile.add(sheetCaption(caption, SwingConstants.CENTER), BorderLayout.SOUTH);
        return tile;
    }

    private static JPanel hitPointsCard(Integer hp) {
        String max = hp != null ? String.valueOf(hp) : "—";

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel maxCap = new JLabel("Hit Point Maximum");
        maxCap.setForeground(TEXT_MUTED);
        maxCap.setFont(new Font("SansSerif", Font.PLAIN, 12));
        JLabel maxVal = new JLabel(max);
        maxVal.setForeground(TEXT_LIGHT);
        maxVal.setFont(new Font("SansSerif", Font.BOLD, 14));
        top.add(maxCap, BorderLayout.WEST);
        top.add(maxVal, BorderLayout.EAST);
        top.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, SHEET_BORDER), new EmptyBorder(0, 2, 6, 2)));

        JPanel mid = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4));
        mid.setOpaque(false);
        JLabel cur = new JLabel(max);
        cur.setForeground(TEXT_LIGHT);
        cur.setFont(new Font("Serif", Font.BOLD, 44));
        JLabel ofMax = new JLabel("/ " + max);
        ofMax.setForeground(TEXT_MUTED);
        ofMax.setFont(new Font("Serif", Font.BOLD, 22));
        mid.add(cur);
        mid.add(ofMax);

        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBackground(SHEET_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(SHEET_BORDER, 1, true), new EmptyBorder(10, 12, 8, 12)));
        card.add(top, BorderLayout.NORTH);
        card.add(mid, BorderLayout.CENTER);
        card.add(sheetCaption("CURRENT HIT POINTS", SwingConstants.CENTER), BorderLayout.SOUTH);
        return card;
    }

    private static JPanel hitDiceCard(String hitDice) {
        JLabel total = new JLabel("<html><span style='color:#aaa5a0'>Total:</span> <b>" + escapeHtml(hitDice) + "</b></html>");
        total.setForeground(TEXT_LIGHT);
        total.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JLabel big = new JLabel(hitDice, SwingConstants.CENTER);
        big.setForeground(TEXT_LIGHT);
        big.setFont(new Font("Serif", Font.BOLD, hitDice.length() > 6 ? 16 : 24));

        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBackground(SHEET_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(SHEET_BORDER, 1, true), new EmptyBorder(8, 10, 8, 10)));
        card.add(total, BorderLayout.NORTH);
        card.add(big, BorderLayout.CENTER);
        card.add(sheetCaption("HIT DICE", SwingConstants.CENTER), BorderLayout.SOUTH);
        return card;
    }

    // Death save pips are in-session trackers only; they are not saved to the character file
    private static JPanel deathSavesCard() {
        JPanel rows = new JPanel(new GridLayout(2, 1, 0, 4));
        rows.setOpaque(false);
        rows.add(deathSaveRow("Success", GOLD));
        rows.add(deathSaveRow("Failure", ACCENT_HOVER));

        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBackground(SHEET_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(SHEET_BORDER, 1, true), new EmptyBorder(8, 10, 8, 10)));
        card.add(rows, BorderLayout.CENTER);
        card.add(sheetCaption("DEATH SAVES", SwingConstants.CENTER), BorderLayout.SOUTH);
        return card;
    }

    private static JPanel deathSaveRow(String label, Color fill) {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setOpaque(false);
        JLabel l = new JLabel(label);
        l.setForeground(TEXT_MUTED);
        l.setFont(new Font("SansSerif", Font.PLAIN, 11));
        JPanel pips = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        pips.setOpaque(false);
        for (int i = 0; i < 3; i++) {
            JCheckBox cb = new JCheckBox();
            cb.setIcon(new PipIcon(false, fill));
            cb.setSelectedIcon(new PipIcon(true, fill));
            cb.setOpaque(false);
            cb.setFocusPainted(false);
            cb.setBorder(new EmptyBorder(0, 1, 0, 1));
            cb.setCursor(new Cursor(Cursor.HAND_CURSOR));
            pips.add(cb);
        }
        row.add(l, BorderLayout.WEST);
        row.add(pips, BorderLayout.EAST);
        return row;
    }

    // ---------------- Coins ----------------
    // GP stays in the record's existing "gold" field; CP/SP/EP/PP live in a "coins" map.
    static final String[] COIN_KEYS = {"CP", "SP", "EP", "GP", "PP"};
    static final String[] COIN_NAMES = {"Copper", "Silver", "Electrum", "Gold", "Platinum"};

    static int recCoin(Map<?, ?> rec, String coin) {
        if ("GP".equals(coin)) {
            Integer g = CharacterStorage.recInt(rec, "gold");
            return g != null ? g : 0;
        }
        Object coins = rec.get("coins");
        if (coins instanceof Map) {
            Object v = ((Map<?, ?>) coins).get(coin);
            if (v instanceof Number) return ((Number) v).intValue();
        }
        return 0;
    }

    static void setCoin(Map<String, Object> rec, String coin, int value) {
        value = Math.max(0, value);
        if ("GP".equals(coin)) {
            rec.put("gold", value);
            return;
        }
        Map<String, Object> coins = new LinkedHashMap<>();
        Object existing = rec.get("coins");
        if (existing instanceof Map) {
            for (Map.Entry<?, ?> en : ((Map<?, ?>) existing).entrySet()) coins.put(String.valueOf(en.getKey()), en.getValue());
        }
        coins.put(coin, value);
        rec.put("coins", coins);
    }

    static Map<String, Object> recCopy(Map<?, ?> rec) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> en : rec.entrySet()) copy.put(String.valueOf(en.getKey()), en.getValue());
        return copy;
    }

    // ---------------- Equipment ----------------
    // Placeholder preset items (name -> description) for the "add item" picker.
    static final Map<String, String> ITEM_PRESETS = new LinkedHashMap<>();
    static {
        ITEM_PRESETS.put("Dagger", "Simple melee weapon. 1d4 piercing; Finesse, Light, Thrown (20/60).");
        ITEM_PRESETS.put("Handaxe", "Simple melee weapon. 1d6 slashing; Light, Thrown (20/60).");
        ITEM_PRESETS.put("Shortsword", "Martial melee weapon. 1d6 piercing; Finesse, Light.");
        ITEM_PRESETS.put("Longsword", "Martial melee weapon. 1d8 slashing; Versatile (1d10).");
        ITEM_PRESETS.put("Shortbow", "Simple ranged weapon. 1d6 piercing; Ammunition (80/320), Two-Handed.");
        ITEM_PRESETS.put("Arrows", "Ammunition for bows. Placeholder description.");
        ITEM_PRESETS.put("Shield", "Grants +2 to Armor Class while wielded.");
        ITEM_PRESETS.put("Leather armor", "Light armor. AC 11 + Dex modifier.");
        ITEM_PRESETS.put("Potion of Healing", "Potion (Common). As a Bonus Action, you can drink it or administer it to another creature within 5 feet of yourself. The creature that drinks the magical red fluid regains 2d4 + 2 Hit Points.");
        ITEM_PRESETS.put("Potion of Greater Healing", "Potion (Uncommon). As a Bonus Action, drink it or administer it to a creature within 5 feet. The drinker regains 4d4 + 4 Hit Points.");
        ITEM_PRESETS.put("Potion of Superior Healing", "Potion (Rare). As a Bonus Action, drink it or administer it to a creature within 5 feet. The drinker regains 8d4 + 8 Hit Points.");
        ITEM_PRESETS.put("Potion of Supreme Healing", "Potion (Very Rare). As a Bonus Action, drink it or administer it to a creature within 5 feet. The drinker regains 10d4 + 20 Hit Points.");
        ITEM_PRESETS.put("Backpack", "Holds up to 30 lb. of gear. Placeholder description.");
        ITEM_PRESETS.put("Bedroll", "For sleeping in the wild. Placeholder description.");
        ITEM_PRESETS.put("Rations", "One day of travel food. Placeholder description.");
        ITEM_PRESETS.put("Torch", "A Torch burns for 1 hour, casting Bright Light in a 20-foot radius and Dim Light for an additional 20 feet. "
                + "When you take the Attack action, you can attack with the Torch, using it as a Simple Melee weapon; on a hit, the target takes 1 Fire damage. "
                + "In Combat, hold it in a hand (Use Item → Hands) and it lights the area around you.");
        ITEM_PRESETS.put("Lamp", "A Lamp burns Oil as fuel, casting Bright Light in a 15-foot radius and Dim Light for an additional 30 feet. "
                + "In Combat, hold it in a hand and it lights the area around you.");
        ITEM_PRESETS.put("Hooded Lantern", "A Hooded Lantern burns Oil as fuel, casting Bright Light in a 30-foot radius and Dim Light for an additional 30 feet. "
                + "As a Bonus Action, you can lower the hood, reducing the light to Dim Light in a 5-foot radius. In Combat, hold it in a hand and it lights the area around you.");
        ITEM_PRESETS.put("Candle", "For 1 hour, a lit Candle sheds Bright Light in a 5-foot radius and Dim Light for an additional 5 feet.");
        ITEM_PRESETS.put("Rope (50 ft.)", "Hempen rope. Placeholder description.");
        ITEM_PRESETS.put("Waterskin", "Holds 4 pints of liquid. Placeholder description.");
    }

    static int itemCount(String item) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\((\\d+)\\)\\s*$").matcher(item);
        return m.find() ? Integer.parseInt(m.group(1)) : 1;
    }

    static String itemString(String base, int count) {
        return count > 1 ? base + " (" + count + ")" : base;
    }

    // Adds `delta` of the item at `index` (removes the entry when the count reaches 0)
    static void changeItemCount(List<String> equipment, int index, int delta) {
        String base = GameData.stripEquipmentCount(equipment.get(index));
        int n = itemCount(equipment.get(index)) + delta;
        if (n <= 0) equipment.remove(index);
        else equipment.set(index, itemString(base, n));
    }

    // Combines repeated entries ("Dagger (2)" twice -> "Dagger (4)", "Dagger" + "Daggers (2)" -> "Dagger (3)"),
    // keeping the first entry's position and spelling. Returns a new list.
    static List<String> mergeItems(List<?> equipment) {
        List<String> out = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        if (equipment == null) return out;
        for (Object o : equipment) {
            if (o == null) continue;
            String item = String.valueOf(o).trim();
            if (item.isEmpty()) continue;
            String base = GameData.stripEquipmentCount(item);
            int found = -1;
            for (int i = 0; i < out.size() && found < 0; i++) {
                String existing = out.get(i);
                if (existing.equalsIgnoreCase(base) || existing.equalsIgnoreCase(base + "s") || base.equalsIgnoreCase(existing + "s")) found = i;
            }
            if (found >= 0) counts.set(found, counts.get(found) + itemCount(item));
            else {
                out.add(base);
                counts.add(itemCount(item));
            }
        }
        for (int i = 0; i < out.size(); i++) out.set(i, itemString(out.get(i), counts.get(i)));
        return out;
    }

    static void addItem(List<String> equipment, String base) {
        for (int i = 0; i < equipment.size(); i++) {
            String existing = GameData.stripEquipmentCount(equipment.get(i));
            // "Dagger" merges into an existing "Daggers (2)" (and vice versa)
            if (existing.equalsIgnoreCase(base) || existing.equalsIgnoreCase(base + "s") || base.equalsIgnoreCase(existing + "s")) {
                changeItemCount(equipment, i, 1);
                return;
            }
        }
        equipment.add(base);
    }

    static String itemDescription(String base) {
        String homebrew = CustomAssets.itemDescription(base);
        if (homebrew != null) return homebrew;
        WeaponEntry weapon = WeaponData.find(base);
        if (weapon != null) return weapon.infoText();
        AmmoItem ammo = WeaponData.findAmmo(base);
        if (ammo != null) return ammo.description();
        List<String> candidates = new ArrayList<>();
        candidates.add(base);
        if (base.endsWith("s")) candidates.add(base.substring(0, base.length() - 1));
        for (String cand : candidates) {
            GameData.ArmorInfo armor = GameData.ARMOR_AC.get(cand);
            if (armor != null) {
                if (armor.isShield) return "Shield. +" + GameData.shieldBonus(armor) + " to Armor Class while wielded.";
                String dex = armor.maxDexBonus <= 0 ? "" : armor.maxDexBonus == Integer.MAX_VALUE
                        ? " + Dex modifier" : " + Dex modifier (max " + armor.maxDexBonus + ")";
                return "Armor. AC " + armor.baseAC + dex + ".";
            }
            for (Map.Entry<String, String> en : ITEM_PRESETS.entrySet()) {
                if (en.getKey().equalsIgnoreCase(cand)) return en.getValue();
            }
        }
        return "No description yet.";
    }

    private static JPanel equipmentCard(Map<?, ?> rec, List<String> equipment,
                                        java.util.function.Consumer<Map<String, Object>> onSave) {
        // Coin purse badge: all five denominations, click to edit
        JPanel purse = new JPanel(new GridLayout(0, 1, 0, 1));
        purse.setBackground(SHEET_ROW_ALT);
        purse.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(GOLD_SOFT, 1, true), new EmptyBorder(6, 10, 6, 10)));
        purse.setToolTipText("Click to edit coins");
        purse.setCursor(new Cursor(Cursor.HAND_CURSOR));
        for (String coin : COIN_KEYS) {
            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.setOpaque(false);
            JLabel k = new JLabel(coin);
            k.setForeground("GP".equals(coin) ? GOLD : TEXT_MUTED);
            k.setFont(new Font("SansSerif", Font.BOLD, 11));
            JLabel v = new JLabel(String.valueOf(recCoin(rec, coin)), SwingConstants.RIGHT);
            v.setForeground("GP".equals(coin) ? GOLD : TEXT_LIGHT);
            v.setFont(new Font("SansSerif", Font.BOLD, "GP".equals(coin) ? 15 : 13));
            row.add(k, BorderLayout.WEST);
            row.add(v, BorderLayout.EAST);
            purse.add(row);
        }
        purse.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (!App.isClickRelease(e)) return;
                showCoinEditor(purse, rec, onSave);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                purse.setBorder(BorderFactory.createCompoundBorder(
                        new LineBorder(GOLD, 1, true), new EmptyBorder(6, 10, 6, 10)));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                purse.setBorder(BorderFactory.createCompoundBorder(
                        new LineBorder(GOLD_SOFT, 1, true), new EmptyBorder(6, 10, 6, 10)));
            }
        });
        JPanel purseWrap = new JPanel(new BorderLayout());
        purseWrap.setOpaque(false);
        purseWrap.add(purse, BorderLayout.NORTH);

        // Item rows: click for description / quantity controls
        JPanel itemList = new JPanel(new GridLayout(0, 1, 0, 0));
        itemList.setOpaque(false);
        if (equipment.isEmpty()) {
            JLabel none = new JLabel("No equipment.");
            none.setForeground(TEXT_MUTED);
            none.setFont(new Font("SansSerif", Font.PLAIN, 13));
            itemList.add(none);
        }
        for (int i = 0; i < equipment.size(); i++) {
            final int index = i;
            JLabel item = new JLabel("• " + equipment.get(i));
            item.setForeground(TEXT_LIGHT);
            item.setFont(new Font("SansSerif", Font.PLAIN, 13));
            item.setOpaque(false);
            item.setBackground(SHEET_ROW_ALT);
            item.setBorder(new EmptyBorder(1, 4, 1, 4));
            item.setCursor(new Cursor(Cursor.HAND_CURSOR));
            item.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseReleased(MouseEvent e) {
                    if (!App.isClickRelease(e)) return;
                    showItemDetail(item, rec, index, onSave);
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    item.setOpaque(true);
                    item.setForeground(GOLD);
                    item.repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    item.setOpaque(false);
                    item.setForeground(TEXT_LIGHT);
                    item.repaint();
                }
            });
            itemList.add(item);
        }
        JPanel itemsWrap = new JPanel(new BorderLayout());
        itemsWrap.setOpaque(false);
        itemsWrap.add(itemList, BorderLayout.NORTH);

        JPanel content = new JPanel(new BorderLayout(12, 0));
        content.setOpaque(false);
        content.add(purseWrap, BorderLayout.WEST);
        content.add(itemsWrap, BorderLayout.CENTER);

        JButton add = sheetIconButton("+", "Add an item");
        add.addActionListener(e -> showAddItem(add, rec, onSave));
        return sheetCardWithAction("Equipment", add, content);
    }

    // Card whose title row has a small action button on the right
    static JPanel sheetCardWithAction(String title, JComponent action, JComponent content) {
        JPanel card = sheetCard(null, content);
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        JLabel t = new JLabel(title.toUpperCase());
        t.setForeground(GOLD);
        t.setFont(new Font("SansSerif", Font.BOLD, 12));
        head.add(t, BorderLayout.WEST);
        head.add(action, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);
        return card;
    }

    static JButton sheetIconButton(String text, String tooltip) {
        JButton b = new JButton(text);
        b.setToolTipText(tooltip);
        b.setFont(new Font("SansSerif", Font.BOLD, 14));
        b.setForeground(GOLD);
        b.setBackground(SHEET_ROW_ALT);
        b.setOpaque(true);
        b.setFocusPainted(false);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createCompoundBorder(new LineBorder(GOLD_SOFT, 1, true), new EmptyBorder(0, 7, 1, 7)));
        return b;
    }

    static JButton overlayButton(String text, boolean primary) {
        JButton b = new JButton(text);
        b.setFont(new Font("SansSerif", Font.BOLD, 13));
        b.setForeground(primary ? SHEET_BG : TEXT_LIGHT);
        b.setBackground(primary ? GOLD : SHEET_ROW_ALT);
        b.setOpaque(true);
        b.setFocusPainted(false);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(primary ? GOLD : SHEET_BORDER, 1, true), new EmptyBorder(6, 12, 6, 12)));
        return b;
    }

    static JTextField overlayField(String text, int columns) {
        JTextField tf = new JTextField(text, columns);
        tf.setFont(new Font("SansSerif", Font.PLAIN, 14));
        tf.setBackground(SHEET_BG);
        tf.setForeground(TEXT_LIGHT);
        tf.setCaretColor(TEXT_LIGHT);
        tf.setBorder(BorderFactory.createCompoundBorder(new LineBorder(SHEET_BORDER, 1, true), new EmptyBorder(4, 6, 4, 6)));
        return tf;
    }

    private static int parseNonNegative(String s, int fallback) {
        try {
            return Math.max(0, Integer.parseInt(s.trim()));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    // Coin editor: an amount box for +/- steps, and each coin's total can be typed directly
    private static void showCoinEditor(Component anchor, Map<?, ?> rec, java.util.function.Consumer<Map<String, Object>> onSave) {
        Map<String, Object> work = recCopy(rec);

        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;

        JLabel amtLbl = new JLabel("Amount to add / remove");
        amtLbl.setForeground(TEXT_MUTED);
        amtLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        JTextField amount = overlayField("1", 5);
        amount.setHorizontalAlignment(JTextField.CENTER);
        c.gridy = 0;
        c.gridx = 0;
        c.gridwidth = 2;
        body.add(amtLbl, c);
        c.gridx = 2;
        c.gridwidth = 2;
        body.add(amount, c);
        c.gridwidth = 1;

        for (int i = 0; i < COIN_KEYS.length; i++) {
            String coin = COIN_KEYS[i];
            JLabel name = new JLabel(COIN_NAMES[i] + " (" + coin + ")");
            name.setForeground("GP".equals(coin) ? GOLD : TEXT_LIGHT);
            name.setFont(new Font("SansSerif", Font.BOLD, 13));
            JButton minus = overlayButton("−", false);
            JTextField value = overlayField(String.valueOf(recCoin(work, coin)), 6);
            value.setHorizontalAlignment(JTextField.CENTER);
            JButton plus = overlayButton("+", false);

            Runnable commitTyped = () -> {
                int v = parseNonNegative(value.getText(), recCoin(work, coin));
                if (v != recCoin(work, coin)) {
                    setCoin(work, coin, v);
                    onSave.accept(recCopy(work));
                }
                value.setText(String.valueOf(recCoin(work, coin)));
            };
            java.util.function.IntConsumer step = sign -> {
                commitTyped.run();
                int amt = parseNonNegative(amount.getText(), 1);
                setCoin(work, coin, recCoin(work, coin) + sign * amt);
                value.setText(String.valueOf(recCoin(work, coin)));
                onSave.accept(recCopy(work));
            };
            minus.addActionListener(e -> step.accept(-1));
            plus.addActionListener(e -> step.accept(1));
            value.addActionListener(e -> commitTyped.run());
            value.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    commitTyped.run();
                }
            });

            c.gridy = i + 1;
            c.gridx = 0;
            c.weightx = 1.0;
            body.add(name, c);
            c.weightx = 0.0;
            c.gridx = 1;
            body.add(minus, c);
            c.gridx = 2;
            body.add(value, c);
            c.gridx = 3;
            body.add(plus, c);
        }

        JLabel hint = new JLabel("Type a total and press Enter, or use − / + with the amount above.");
        hint.setForeground(TEXT_MUTED);
        hint.setFont(new Font("SansSerif", Font.PLAIN, 11));
        c.gridy = COIN_KEYS.length + 1;
        c.gridx = 0;
        c.gridwidth = 4;
        c.insets = new Insets(10, 4, 0, 4);
        body.add(hint, c);

        showSheetOverlay(anchor, "Coin Purse", body, null);
    }

    private static void showItemDetail(Component anchor, Map<?, ?> rec, int index,
                                       java.util.function.Consumer<Map<String, Object>> onSave) {
        Map<String, Object> work = recCopy(rec);
        List<String> equipment = CharacterStorage.recList(work, "equipment");
        if (index < 0 || index >= equipment.size()) return;
        String base = GameData.stripEquipmentCount(equipment.get(index));

        JTextArea desc = new JTextArea(itemDescription(base));
        styleInfoTextArea(desc);
        desc.setFont(new Font("SansSerif", Font.PLAIN, 13));
        desc.setBorder(new EmptyBorder(0, 0, 0, 0));
        desc.setColumns(28);

        JLabel qty = new JLabel("Quantity: " + itemCount(equipment.get(index)));
        qty.setForeground(GOLD);
        qty.setFont(new Font("SansSerif", Font.BOLD, 14));

        JButton removeOne = overlayButton("− Remove one", false);
        JButton addOne = overlayButton("+ Add one", false);
        JButton removeAll = overlayButton("Remove item", false);
        removeAll.setForeground(ACCENT_HOVER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttons.setOpaque(false);
        buttons.add(removeOne);
        buttons.add(Box.createRigidArea(new Dimension(6, 0)));
        buttons.add(addOne);
        buttons.add(Box.createRigidArea(new Dimension(6, 0)));
        buttons.add(removeAll);

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(desc, BorderLayout.NORTH);
        body.add(qty, BorderLayout.CENTER);
        body.add(buttons, BorderLayout.SOUTH);

        Runnable[] close = new Runnable[1];
        java.util.function.IntConsumer change = delta -> {
            if (delta == Integer.MIN_VALUE) equipment.remove(index);
            else changeItemCount(equipment, index, delta);
            work.put("equipment", new ArrayList<Object>(equipment));
            onSave.accept(recCopy(work));
            if (index >= equipment.size() || !GameData.stripEquipmentCount(equipment.get(index)).equals(base)) {
                close[0].run();
            } else {
                qty.setText("Quantity: " + itemCount(equipment.get(index)));
            }
        };
        removeOne.addActionListener(e -> change.accept(-1));
        addOne.addActionListener(e -> change.accept(1));
        removeAll.addActionListener(e -> change.accept(Integer.MIN_VALUE));

        close[0] = showSheetOverlay(anchor, base, body, null);
    }

    private static void showAddItem(Component anchor, Map<?, ?> rec, java.util.function.Consumer<Map<String, Object>> onSave) {
        Runnable[] close = new Runnable[1];
        java.util.function.Consumer<String> add = name -> {
            if (name == null || name.trim().isEmpty()) return;
            Map<String, Object> work = recCopy(rec);
            List<String> equipment = CharacterStorage.recList(work, "equipment");
            addItem(equipment, name.trim());
            work.put("equipment", new ArrayList<Object>(equipment));
            onSave.accept(work);
            close[0].run();
        };

        JPanel custom = new JPanel(new BorderLayout(6, 0));
        custom.setOpaque(false);
        JTextField customName = overlayField("", 16);
        JButton customAdd = overlayButton("Add", true);
        customAdd.addActionListener(e -> add.accept(customName.getText()));
        customName.addActionListener(e -> add.accept(customName.getText()));
        JLabel customLbl = new JLabel("Custom:");
        customLbl.setForeground(TEXT_MUTED);
        custom.add(customLbl, BorderLayout.WEST);
        custom.add(customName, BorderLayout.CENTER);
        custom.add(customAdd, BorderLayout.EAST);

        JPanel list = new JPanel(new GridLayout(0, 1, 0, 4));
        list.setOpaque(false);
        // Homebrew items / weapons / armor from the Asset Library first, then the standard presets
        // (minus anything whose tags are turned off in the Asset Library's Tags & Filters)
        List<String[]> presets = new ArrayList<>();
        for (String[] hb : CustomAssets.equipmentPresets()) presets.add(new String[]{hb[0], "Homebrew · " + hb[1]});
        for (Map.Entry<String, String> en : ITEM_PRESETS.entrySet()) {
            String cat = CustomAssets.builtinWeapon(en.getKey()) != null ? CustomAssets.WEAPONS
                    : GameData.ARMOR_AC.containsKey(en.getKey()) ? CustomAssets.ARMOR : CustomAssets.ITEMS;
            if (Tags.allowed(cat, en.getKey())) presets.add(new String[]{en.getKey(), en.getValue()});
        }
        presets.removeIf(p -> p[1].startsWith("Homebrew · ") && !homebrewEquipmentAllowed(p[0]));
        for (String[] en : presets) {
            JButton row = new JButton("<html><div style='width:250px'><b>" + escapeHtml(en[0])
                    + "</b><br><span style='color:#aaa5a0; font-size:9px'>" + escapeHtml(en[1]) + "</span></div></html>");
            row.setHorizontalAlignment(SwingConstants.LEFT);
            row.setForeground(TEXT_LIGHT);
            row.setBackground(SHEET_ROW_ALT);
            row.setOpaque(true);
            row.setFocusPainted(false);
            row.setCursor(new Cursor(Cursor.HAND_CURSOR));
            row.setBorder(new EmptyBorder(6, 10, 6, 10));
            row.addActionListener(e -> add.accept(en[0]));
            list.add(row);
        }
        JPanel listWrap = new JPanel(new BorderLayout());
        listWrap.setOpaque(false);
        listWrap.add(list, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(listWrap);
        applyDarkScrollBar(scroll);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getViewport().setBackground(SHEET_CARD);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setPreferredSize(new Dimension(380, 300));

        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setOpaque(false);
        body.add(custom, BorderLayout.NORTH);
        body.add(scroll, BorderLayout.CENTER);

        close[0] = showSheetOverlay(anchor, "Add Equipment", body, customName);
    }

    private static boolean homebrewEquipmentAllowed(String name) {
        for (String cat : new String[]{CustomAssets.ITEMS, CustomAssets.WEAPONS, CustomAssets.ARMOR}) {
            Map<String, Object> a = CustomAssets.findByName(cat, name);
            if (a != null) return Tags.allowedAsset(cat, a);
        }
        return true;
    }

    // ---------------- Conditions ----------------
    static final String[] CONDITION_PRESETS = {"Blinded", "Charmed", "Deafened", "Exhaustion", "Frightened", "Grappled",
            "Incapacitated", "Invisible", "Paralyzed", "Petrified", "Poisoned", "Prone", "Restrained", "Stunned"};

    // Saved as a "conditions" list; "Conscious"/"Unconscious" is always the first entry.
    static List<String> recConditions(Map<?, ?> rec) {
        List<String> raw = CharacterStorage.recList(rec, "conditions");
        List<String> out = new ArrayList<>();
        out.add(raw.contains("Unconscious") ? "Unconscious" : "Conscious");
        for (String s : raw) if (!"Conscious".equals(s) && !"Unconscious".equals(s) && !out.contains(s)) out.add(s);
        return out;
    }

    private static JPanel conditionsCard(Map<?, ?> rec, java.util.function.Consumer<Map<String, Object>> onSave) {
        List<String> conditions = recConditions(rec);
        java.util.function.Consumer<List<String>> save = list -> {
            Map<String, Object> work = recCopy(rec);
            work.put("conditions", new ArrayList<Object>(list));
            onSave.accept(work);
        };

        JPanel chips = new JPanel(new GridLayout(0, 2, 6, 6));
        chips.setOpaque(false);
        for (int i = 0; i < conditions.size(); i++) {
            String cond = conditions.get(i);
            boolean isState = i == 0;
            boolean down = "Unconscious".equals(cond);
            Color tone = isState ? (down ? ACCENT_HOVER : new Color(110, 190, 120)) : GOLD;

            JPanel chip = new JPanel(new BorderLayout(4, 0));
            chip.setBackground(SHEET_ROW_ALT);
            chip.setBorder(BorderFactory.createCompoundBorder(new LineBorder(tone, 1, true), new EmptyBorder(3, 8, 3, 4)));
            JLabel name = new JLabel(cond);
            name.setForeground(tone);
            name.setFont(new Font("SansSerif", Font.BOLD, 12));
            chip.add(name, BorderLayout.CENTER);

            JButton action = new JButton(isState ? "⇄" : "✕");
            action.setToolTipText(isState ? "Switch to " + (down ? "Conscious" : "Unconscious") : "Remove " + cond);
            action.setFont(new Font("SansSerif", Font.BOLD, 12));
            action.setForeground(TEXT_MUTED);
            action.setContentAreaFilled(false);
            action.setBorderPainted(false);
            action.setFocusPainted(false);
            action.setBorder(new EmptyBorder(0, 4, 0, 4));
            action.setCursor(new Cursor(Cursor.HAND_CURSOR));
            final int idx = i;
            action.addActionListener(e -> {
                List<String> next = new ArrayList<>(conditions);
                if (isState) next.set(0, down ? "Conscious" : "Unconscious");
                else next.remove(idx);
                save.accept(next);
            });
            chip.add(action, BorderLayout.EAST);
            clickable(chip, () -> showInfoOverlay(chips, cond, isState ? "State" : "Condition",
                    GameData.CONDITION_INFO.getOrDefault(cond, ""), null));
            chips.add(chip);
        }

        JButton add = sheetIconButton("+", "Add a condition");
        add.addActionListener(e -> {
            JPanel grid = new JPanel(new GridLayout(0, 2, 6, 6));
            grid.setOpaque(false);
            Runnable[] close = new Runnable[1];
            List<String> options = new ArrayList<>(Arrays.asList(CONDITION_PRESETS));
            for (String hb : CustomAssets.conditionNames()) if (!options.contains(hb)) options.add(hb); // homebrew conditions
            for (String cond : options) {
                if (conditions.contains(cond) || !Tags.allowed(CustomAssets.CONDITIONS, cond)) continue;
                JButton b = overlayButton(cond, false);
                b.addActionListener(ev -> {
                    List<String> next = new ArrayList<>(conditions);
                    next.add(cond);
                    save.accept(next);
                    close[0].run();
                });
                grid.add(b);
            }
            if (grid.getComponentCount() == 0) {
                JLabel all = new JLabel("All conditions are already applied.");
                all.setForeground(TEXT_MUTED);
                grid.add(all);
            }
            close[0] = showSheetOverlay(add, "Add Condition", grid, null);
        });
        return sheetCardWithAction("Conditions", add, chips);
    }

    // ---------------- Click-for-detail helpers ----------------
    static final Color SHEET_HOVER = new Color(0x34, 0x39, 0x4F);

    // True for a left-button release that ends over the component that was pressed.
    // Click handlers use mouseReleased + this instead of mouseClicked: Swing drops mouseClicked
    // whenever the pointer moves even a pixel between press and release, which happens all the
    // time with quick clicking, so fast clicks were silently lost.
    static boolean isClickRelease(MouseEvent e) {
        Component c = e.getComponent();
        return SwingUtilities.isLeftMouseButton(e) && c != null && c.contains(e.getPoint());
    }

    // Makes a component clickable: hand cursor, hover tint, and runs onClick on left-click
    static <T extends JComponent> T clickable(T c, Runnable onClick) {
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        boolean[] origOpaque = {c.isOpaque()};
        Color[] origBg = {c.getBackground()};
        c.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (!App.isClickRelease(e)) return;
                if (SwingUtilities.isLeftMouseButton(e)) onClick.run();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                origOpaque[0] = c.isOpaque();
                origBg[0] = c.getBackground();
                c.setOpaque(true);
                c.setBackground(SHEET_HOVER);
                c.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                c.setOpaque(origOpaque[0]);
                c.setBackground(origBg[0]);
                c.repaint();
            }
        });
        return c;
    }

    static String[] row(String label, String value) {
        return new String[]{label, value};
    }

    // Standard detail overlay: subtitle tag, wrapped description, and an optional label/value breakdown
    static void showInfoOverlay(Component anchor, String title, String subtitle, String description, List<String[]> rows) {
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        if (subtitle != null && !subtitle.isEmpty()) {
            JLabel sub = new JLabel(subtitle.toUpperCase());
            sub.setForeground(TEXT_MUTED);
            sub.setFont(new Font("SansSerif", Font.BOLD, 11));
            sub.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(sub);
            body.add(Box.createRigidArea(new Dimension(0, 8)));
        }
        if (description != null && !description.isEmpty()) {
            JLabel desc = new JLabel("<html><div style='width:340px'>" + escapeHtml(description).replace("\n", "<br>") + "</div></html>");
            desc.setForeground(TEXT_LIGHT);
            desc.setFont(new Font("SansSerif", Font.PLAIN, 13));
            desc.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(desc);
        }
        if (rows != null && !rows.isEmpty()) {
            JPanel table = new JPanel(new GridBagLayout());
            table.setOpaque(false);
            table.setAlignmentX(Component.LEFT_ALIGNMENT);
            table.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, SHEET_BORDER), new EmptyBorder(8, 0, 0, 0)));
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(2, 0, 2, 0);
            c.anchor = GridBagConstraints.WEST;
            for (int i = 0; i < rows.size(); i++) {
                c.gridy = i;
                c.gridx = 0;
                c.weightx = 1.0;
                JLabel l = new JLabel(rows.get(i)[0]);
                l.setForeground(TEXT_MUTED);
                l.setFont(new Font("SansSerif", Font.PLAIN, 13));
                table.add(l, c);
                c.gridx = 1;
                c.weightx = 0.0;
                c.anchor = GridBagConstraints.EAST;
                JLabel v = new JLabel(rows.get(i)[1]);
                v.setForeground(TEXT_LIGHT);
                v.setFont(new Font("SansSerif", Font.BOLD, 13));
                v.setBorder(new EmptyBorder(0, 16, 0, 0));
                table.add(v, c);
                c.anchor = GridBagConstraints.WEST;
            }
            body.add(Box.createRigidArea(new Dimension(0, 10)));
            body.add(table);
        }
        showSheetOverlay(anchor, title, body, null);
    }

    // In-window yes/no prompt (replaces JOptionPane confirm dialogs)
    static void showConfirmOverlay(Component anchor, String title, String message, String confirmLabel, Runnable onConfirm) {
        Runnable[] close = new Runnable[1];
        JLabel msg = new JLabel("<html><div style='width:320px'>" + escapeHtml(message).replace("\n", "<br>") + "</div></html>");
        msg.setForeground(TEXT_LIGHT);
        msg.setFont(new Font("SansSerif", Font.PLAIN, 14));
        JButton cancel = overlayButton("Cancel", false);
        JButton ok = overlayButton(confirmLabel, true);
        cancel.addActionListener(e -> close[0].run());
        ok.addActionListener(e -> {
            close[0].run();
            onConfirm.run();
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(cancel);
        buttons.add(ok);
        JPanel body = new JPanel(new BorderLayout(0, 16));
        body.setOpaque(false);
        body.add(msg, BorderLayout.CENTER);
        body.add(buttons, BorderLayout.SOUTH);
        close[0] = showSheetOverlay(anchor, title, body, ok);
    }

    // In-window message with an OK button (replaces JOptionPane message dialogs)
    static void showNoticeOverlay(Component anchor, String title, String message) {
        Runnable[] close = new Runnable[1];
        JLabel msg = new JLabel("<html><div style='width:320px'>" + escapeHtml(message).replace("\n", "<br>") + "</div></html>");
        msg.setForeground(TEXT_LIGHT);
        msg.setFont(new Font("SansSerif", Font.PLAIN, 14));
        JButton ok = overlayButton("OK", true);
        ok.addActionListener(e -> close[0].run());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.setOpaque(false);
        buttons.add(ok);
        JPanel body = new JPanel(new BorderLayout(0, 16));
        body.setOpaque(false);
        body.add(msg, BorderLayout.CENTER);
        body.add(buttons, BorderLayout.SOUTH);
        close[0] = showSheetOverlay(anchor, title, body, ok);
    }

    // ---------------- In-window overlay ----------------
    // Shows `body` in a card centered over the frame, on the root pane's layered pane (no new
    // window). Clicking the dimmed backdrop, the close button, or pressing Esc dismisses it.
    // Returns a Runnable that closes the overlay.
    static Runnable showSheetOverlay(Component anchor, String title, JComponent body, JComponent focus) {
        return showSheetOverlay(anchor, title, body, focus, null);
    }

    // Same, and runs `onClose` once when the overlay closes, however it was closed
    static Runnable showSheetOverlay(Component anchor, String title, JComponent body, JComponent focus, Runnable onClose) {
        JRootPane root = SwingUtilities.getRootPane(anchor);
        if (root == null) return () -> { };
        JLayeredPane layers = root.getLayeredPane();

        JPanel scrim = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(new Color(0, 0, 0, 150));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        scrim.setOpaque(false);

        JPanel card = new JPanel(new BorderLayout(0, 12));
        card.setBackground(SHEET_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(new LineBorder(GOLD, 1, true), new EmptyBorder(14, 16, 16, 16)));
        JPanel head = new JPanel(new BorderLayout(12, 0));
        head.setOpaque(false);
        JLabel t = new JLabel(title);
        t.setForeground(GOLD);
        t.setFont(new Font("Serif", Font.BOLD, 20));
        JButton x = new JButton("✕");
        x.setForeground(TEXT_MUTED);
        x.setFont(new Font("SansSerif", Font.BOLD, 14));
        x.setContentAreaFilled(false);
        x.setBorderPainted(false);
        x.setFocusPainted(false);
        x.setCursor(new Cursor(Cursor.HAND_CURSOR));
        head.add(t, BorderLayout.CENTER);
        head.add(x, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);
        card.addMouseListener(new MouseAdapter() { }); // swallow clicks so they don't reach the backdrop
        scrim.add(card);

        ComponentAdapter resize = new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                scrim.setBounds(0, 0, layers.getWidth(), layers.getHeight());
                scrim.revalidate();
            }
        };
        Runnable close = () -> {
            if (scrim.getParent() == null) return;
            layers.remove(scrim);
            layers.removeComponentListener(resize);
            layers.repaint();
            if (onClose != null) onClose.run(); // closed by any means: a button, the backdrop, the close button or Esc
        };
        x.addActionListener(e -> close.run());
        scrim.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (!App.isClickRelease(e)) return;
                close.run();
            }
        });
        scrim.addMouseWheelListener(e -> { }); // keep the sheet behind from scrolling
        scrim.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeOverlay");
        scrim.getActionMap().put("closeOverlay", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                close.run();
            }

            // Only the topmost overlay answers Esc when overlays are stacked
            @Override
            public boolean isEnabled() {
                return scrim.getParent() == layers && layers.getLayer(scrim) == JLayeredPane.MODAL_LAYER
                        && layers.getPosition(scrim) == 0;
            }
        });

        scrim.setBounds(0, 0, layers.getWidth(), layers.getHeight());
        layers.add(scrim, JLayeredPane.MODAL_LAYER);
        layers.moveToFront(scrim); // stacked overlays (e.g. a condition opened from a spell) go on top
        layers.addComponentListener(resize);
        layers.revalidate();
        layers.repaint();
        if (focus != null) SwingUtilities.invokeLater(focus::requestFocusInWindow);
        return close;
    }

    private static JComponent otherProficienciesContent(Map<?, ?> rec, String characterClass, String species) {
        List<String[]> lines = new ArrayList<>();
        String size = CharacterStorage.recStr(rec, "size");
        String type = CharacterStorage.recStr(rec, "creatureType");
        Integer climb = CharacterStorage.recInt(rec, "climbSpeed");
        if (!size.isEmpty()) lines.add(new String[]{"Size", size});
        if (!type.isEmpty()) lines.add(new String[]{"Creature Type", type});
        if (climb != null && climb > 0) lines.add(new String[]{"Climb Speed", climb + " ft."});
        boolean homebrewSpecies = CustomAssets.speciesAsset(species) != null; // homebrew lines come from sheetProficiencies
        int darkvision = SpeciesTraits.darkvision(rec);
        if (darkvision > 0 && !homebrewSpecies) lines.add(new String[]{"Darkvision", darkvision + " ft."});
        Map<String, Integer> speciesMods = SpeciesTraits.damageMods(rec);
        String defenses = DamageRules.summary(speciesMods);
        if (!defenses.isEmpty()) lines.add(new String[]{"Damage", defenses});
        List<DecisionItem> decisions = GameData.CLASS_DECISIONS.get(characterClass);
        if (decisions != null) {
            Map<String, List<String>> choices = CharacterStorage.recChoicesMap(rec);
            for (DecisionItem item : decisions) {
                if (!"TEXT".equals(item.type)) continue;
                List<String> chosen = choices.get(item.label);
                if (chosen != null && !chosen.isEmpty()) lines.add(new String[]{cleanDecisionLabel(item.label), String.join(", ", chosen)});
            }
        }

        Map<String, String> lineInfo = new LinkedHashMap<>();
        for (String[] hb : CustomAssets.sheetProficiencies(rec)) { // homebrew class / background / species
            lines.add(new String[]{hb[0], hb[1]});
            lineInfo.put(hb[0], hb[2]);
        }
        lineInfo.put("Size", "Your size category, set by your species. Small and Medium creatures both occupy a 5-ft. space; size matters for grappling, squeezing and some spells and effects.");
        lineInfo.put("Creature Type", "Your creature type matters for spells and effects that target specific types (for example, spells that only affect Humanoids).");
        lineInfo.put("Climb Speed", "You can climb at this speed without spending extra movement.");
        lineInfo.put("Darkvision", "Granted by your " + species + " species. " + SpeciesTraits.DARKVISION_INFO);
        lineInfo.put("Damage", "From your " + species + " species.\n\n" + DamageRules.INFO
                + "\n\nIn Combat these are applied automatically to damage your token takes.");

        JPanel list = new JPanel(new GridLayout(0, 1, 0, 2));
        list.setOpaque(false);
        for (String[] ln : lines) {
            JLabel l = new JLabel("<html><span style='color:#aaa5a0'>" + escapeHtml(ln[0]) + ":</span> " + escapeHtml(ln[1]) + "</html>");
            l.setForeground(TEXT_LIGHT);
            l.setFont(new Font("SansSerif", Font.PLAIN, 13));
            l.setBorder(new EmptyBorder(1, 4, 1, 4));
            clickable(l, () -> showInfoOverlay(list, ln[0] + ": " + ln[1], "Trait",
                    lineInfo.getOrDefault(ln[0], "Chosen during character creation."), null));
            list.add(l);
        }

        // Languages: Common + chosen + class-granted, each clickable for detail
        JLabel langHeader = new JLabel("LANGUAGES");
        langHeader.setForeground(TEXT_MUTED);
        langHeader.setFont(new Font("SansSerif", Font.BOLD, 10));
        langHeader.setBorder(new EmptyBorder(lines.isEmpty() ? 0 : 6, 4, 2, 4));
        list.add(langHeader);
        JPanel langGrid = new JPanel(new GridLayout(0, 2, 4, 2));
        langGrid.setOpaque(false);
        for (Map.Entry<String, String> en : CharacterStorage.recLanguageSources(rec).entrySet()) {
            String lang = en.getKey();
            String source = en.getValue();
            JLabel l = new JLabel("• " + lang);
            l.setForeground(TEXT_LIGHT);
            l.setFont(new Font("SansSerif", Font.PLAIN, 13));
            l.setBorder(new EmptyBorder(1, 4, 1, 4));
            clickable(l, () -> showInfoOverlay(list, lang, "Language",
                    GameData.LANGUAGE_INFO.getOrDefault(lang, ""), Collections.singletonList(row("Known from", source))));
            langGrid.add(l);
        }

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        JPanel stack = new JPanel(new BorderLayout());
        stack.setOpaque(false);
        stack.add(list, BorderLayout.NORTH);
        stack.add(langGrid, BorderLayout.CENTER);
        wrap.add(stack, BorderLayout.NORTH);
        return wrap;
    }

    private static JComponent featuresContent(Map<?, ?> rec, String characterClass, String species,
                                              String lineageSuffix, List<Map<String, Object>> feats) {
        List<JComponent> cards = new ArrayList<>();

        if (!species.isEmpty()) {
            List<String> sub = new ArrayList<>();
            sub.add("Species");
            String size = CharacterStorage.recStr(rec, "size");
            String type = CharacterStorage.recStr(rec, "creatureType");
            if (!size.isEmpty()) sub.add(size);
            if (!type.isEmpty()) sub.add(type);
            String info = GameData.SPECIES_INFO.get(species);
            cards.add(featureCard(species + lineageSuffix, String.join(" · ", sub), info == null ? "" : info));
            for (SpeciesTraits.Trait t : SpeciesTraits.traits(rec)) {
                cards.add(featureCard(t.name, species + lineageSuffix + " trait" + (t.level > 1 ? " · level " + t.level : ""), t.description));
            }
        }

        // Homebrew: species traits, background origin feat, class + subclass features (Asset Library)
        for (String[] f : CustomAssets.sheetFeatures(rec)) cards.add(featureCard(f[0], f[1], f[2]));

        List<DecisionItem> decisions = GameData.CLASS_DECISIONS.get(characterClass);
        if (decisions != null) {
            Map<String, List<String>> choices = CharacterStorage.recChoicesMap(rec);
            for (DecisionItem item : decisions) {
                if (!"CHOICE".equals(item.type)) continue;
                List<String> chosen = choices.get(item.label);
                if (chosen == null || chosen.isEmpty()) continue;
                cards.add(featureCard(String.join(", ", chosen),
                        cleanDecisionLabel(item.label) + " · " + characterClass + " 1", ""));
            }
        }

        for (Map<String, Object> f : feats) {
            String category = String.valueOf(f.get("category"));
            String kind = FeatData.CATEGORY_EPIC.equals(category) ? "Epic Boon"
                    : FeatData.CATEGORY_CUSTOM.equals(category) ? "Custom Feat" : "Feat";
            List<String> sub = new ArrayList<>();
            if (!FeatData.CATEGORY_ASI.equals(category)) sub.add(kind);
            Object fClass = f.get("className");
            Object fLevel = f.get("classLevel");
            if (fClass != null && fLevel instanceof Number) sub.add(fClass + " " + ((Number) fLevel).intValue());
            List<String> incParts = new ArrayList<>();
            for (Map.Entry<String, Integer> en : CharacterStorage.featStatIncreases(f).entrySet()) {
                incParts.add(en.getKey() + " +" + en.getValue());
            }
            if (!incParts.isEmpty()) sub.add(String.join(", ", incParts));
            String desc = f.get("description") == null ? "" : String.valueOf(f.get("description"));
            if (FeatData.CATEGORY_ASI.equals(category)) desc = ""; // the increases tag already says it all
            cards.add(featureCard(String.valueOf(f.get("name")), String.join(" · ", sub), desc));
        }

        if (cards.isEmpty()) {
            JLabel none = new JLabel("No features yet.");
            none.setForeground(TEXT_MUTED);
            none.setFont(new Font("SansSerif", Font.PLAIN, 13));
            cards.add(none);
        }
        return sheetStack(8, false, cards.toArray(new JComponent[0]));
    }

    private static JPanel featureCard(String title, String subtitle, String description) {
        StringBuilder html = new StringBuilder("<html><div style='width:290px'>");
        html.append("<b style='color:#d4af37; font-size:12px'>").append(escapeHtml(title)).append("</b>");
        if (!subtitle.isEmpty()) {
            html.append("<br><span style='color:#aaa5a0; font-size:9px'>").append(escapeHtml(subtitle.toUpperCase())).append("</span>");
        }
        if (!description.isEmpty()) {
            html.append("<div style='margin-top:4px'>").append(escapeHtml(description)).append("</div>");
        }
        html.append("</div></html>");
        JLabel lbl = new JLabel(html.toString());
        lbl.setForeground(TEXT_LIGHT);
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(SHEET_ROW_ALT);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, GOLD), new EmptyBorder(8, 10, 8, 10)));
        card.add(lbl, BorderLayout.CENTER);
        return clickable(card, () -> showInfoOverlay(card, title, subtitle,
                description.isEmpty() ? "No further description." : description, null));
    }

    // Species descriptions for the wizard's info panel, with each species' traits, darkvision and resistances appended
    private static Map<String, String> speciesInfoWithTraits() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> en : GameData.SPECIES_INFO.entrySet()) {
            String traits = SpeciesTraits.summary(en.getKey(), "");
            out.put(en.getKey(), en.getValue() + (traits.isEmpty() ? "" : "\n\n" + traits));
        }
        return out;
    }

    // "Fighting Style?" -> "Fighting Style", "Skills? (2)" -> "Skills"
    private static String cleanDecisionLabel(String label) {
        return label.replaceAll("\\s*\\(\\d+\\)\\s*$", "").replace("?", "").trim();
    }

    // ===============================================================
    // SPELLS TAB
    //   header (ability / DC / attack / prepared per casting class) -> spell slots with clickable
    //   pips -> spell cards grouped by Cantrips and Levels 1-9.
    //   Slot usage is saved as spellSlotsUsed {"1": n, ...} and pactSlotsUsed (Warlock).
    // ===============================================================
    static final Color SPELL_ALWAYS = new Color(110, 190, 120);
    private static final int SPELL_CARD_GAP = 10;
    private static final int SPELL_ART_SIZE = 72;

    // One spell as listed on the sheet: registry data (null if unknown) + where it comes from
    private static class SheetSpell {
        String name;
        Spell spell;
        int level;
        List<String> sources = new ArrayList<>();
        boolean always;    // species / feat / class feature: doesn't count against the prepared limit
        boolean bookOnly;  // in a Wizard's spellbook but not prepared
        boolean arcanum;   // Warlock Mystic Arcanum (once per Long Rest, no slot)
    }

    // Spell slot totals and usage. Shared Spellcasting slots + separate Warlock Pact Magic slots.
    static class SlotState {
        int[] max = new int[9];
        int[] used = new int[9];
        int pactMax, pactLevel, pactUsed;

        int remaining(int level) {
            return level < 1 || level > 9 ? 0 : Math.max(0, max[level - 1] - used[level - 1]);
        }

        int pactRemaining() {
            return Math.max(0, pactMax - pactUsed);
        }

        boolean any() {
            if (pactMax > 0) return true;
            for (int m : max) if (m > 0) return true;
            return false;
        }

        SlotState copy() {
            SlotState s = new SlotState();
            s.max = max.clone();
            s.used = used.clone();
            s.pactMax = pactMax;
            s.pactLevel = pactLevel;
            s.pactUsed = pactUsed;
            return s;
        }
    }

    static SlotState recSlotState(Map<?, ?> rec) {
        SlotState s = new SlotState();
        Map<String, Integer> classLevels = CharacterStorage.recClassLevels(rec);
        s.max = SpellData.spellSlots(classLevels);
        Object u = rec.get("spellSlotsUsed");
        if (u instanceof Map) {
            for (int i = 1; i <= 9; i++) {
                Object v = ((Map<?, ?>) u).get(String.valueOf(i));
                if (v instanceof Number) s.used[i - 1] = Math.max(0, Math.min(s.max[i - 1], ((Number) v).intValue()));
            }
        }
        int warlock = classLevels.getOrDefault("Warlock", 0);
        s.pactMax = SpellData.pactSlotCount(warlock);
        s.pactLevel = SpellData.pactSlotLevel(warlock);
        Integer pu = CharacterStorage.recInt(rec, "pactSlotsUsed");
        s.pactUsed = pu == null ? 0 : Math.max(0, Math.min(s.pactMax, pu));
        return s;
    }

    static void writeSlotState(Map<String, Object> rec, SlotState s) {
        Map<String, Object> used = new LinkedHashMap<>();
        for (int i = 0; i < 9; i++) if (s.used[i] > 0) used.put(String.valueOf(i + 1), s.used[i]);
        rec.put("spellSlotsUsed", used);
        rec.put("pactSlotsUsed", s.pactUsed);
    }

    private static void populateCharacterSpells(JPanel container, Map<?, ?> rec,
                                                java.util.function.Consumer<Map<String, Object>> onSave) {
        container.removeAll();
        container.setLayout(new MaxWidthCenterLayout(SHEET_MAX_WIDTH));
        container.setBackground(SHEET_BG);
        container.setBorder(new EmptyBorder(20, 16, 28, 16));

        Map<String, Integer> classLevels = CharacterStorage.recClassLevels(rec);
        int level = CharacterStorage.recLevel(rec);
        int pb = GameData.proficiencyBonus(level);
        Map<String, Integer> stats = CharacterStorage.recStatsMap(rec);
        LinkedHashMap<String, Map<String, List<String>>> classSpells = CharacterStorage.recClassSpells(rec);
        LinkedHashMap<String, String> always = SpellData.alwaysPrepared(rec);
        SlotState slots = recSlotState(rec);
        java.util.function.Consumer<SlotState> saveSlots = st -> {
            Map<String, Object> work = recCopy(rec);
            writeSlotState(work, st);
            onSave.accept(work);
        };

        List<String> casters = new ArrayList<>();
        for (String cls : classLevels.keySet()) if (SpellData.isCaster(cls)) casters.add(cls);

        // ---- Collect every spell the character has, merged by name ----
        LinkedHashMap<String, SheetSpell> entries = new LinkedHashMap<>();
        java.util.function.BiFunction<String, String, SheetSpell> entry = (name, source) -> {
            SheetSpell ss = entries.get(name);
            if (ss == null) {
                ss = new SheetSpell();
                ss.name = name;
                ss.spell = SpellData.lookup(name);
                ss.level = ss.spell != null ? ss.spell.level : 1;
                ss.bookOnly = true;
                entries.put(name, ss);
            }
            if (!ss.sources.contains(source)) ss.sources.add(source);
            return ss;
        };
        for (Map.Entry<String, Map<String, List<String>>> en : classSpells.entrySet()) {
            String cls = en.getKey();
            for (String n : en.getValue().get("cantrips")) {
                SheetSpell ss = entry.apply(n, cls);
                ss.bookOnly = false;
                if (ss.spell == null) ss.level = 0;
            }
            for (String n : en.getValue().get("prepared")) entry.apply(n, cls).bookOnly = false;
            for (String n : en.getValue().get("spellbook")) {
                if (!en.getValue().get("prepared").contains(n)) entry.apply(n, cls + " spellbook");
            }
            for (String n : en.getValue().get("arcanum")) {
                SheetSpell ss = entry.apply(n, cls + " (Mystic Arcanum)");
                ss.arcanum = true;
                ss.bookOnly = false;
            }
        }
        for (Map.Entry<String, String> en : always.entrySet()) {
            SheetSpell ss = entry.apply(en.getKey(), en.getValue());
            ss.always = true;
            ss.bookOnly = false;
        }

        List<JComponent> blocks = new ArrayList<>();
        blocks.add(spellHeaderCard(rec, casters, classLevels, classSpells, always, stats, pb));
        if (slots.any()) blocks.add(spellSlotsCard(slots, classLevels, saveSlots));

        if (entries.isEmpty()) {
            JLabel none = new JLabel("<html><div style='width:600px'>No spells yet. Spellcasting classes choose spells "
                    + "when the character is created and when they level up; some species and feats also grant spells.</div></html>");
            none.setForeground(TEXT_MUTED);
            none.setFont(new Font("SansSerif", Font.PLAIN, 14));
            blocks.add(sheetCard("Spells", none));
        } else {
            TreeMap<Integer, List<SheetSpell>> byLevel = new TreeMap<>();
            for (SheetSpell ss : entries.values()) byLevel.computeIfAbsent(ss.level, k -> new ArrayList<>()).add(ss);
            for (Map.Entry<Integer, List<SheetSpell>> en : byLevel.entrySet()) {
                List<SheetSpell> list = en.getValue();
                list.sort(Comparator.comparing((SheetSpell s) -> s.bookOnly).thenComparing(s -> s.name));
                blocks.add(spellLevelSection(en.getKey(), list, slots, saveSlots));
            }
        }

        container.add(sheetStack(14, false, blocks.toArray(new JComponent[0])));
        container.revalidate();
        container.repaint();
    }

    // ---------------- Header: ability / save DC / attack / prepared, one row per casting class ----------------
    private static JPanel spellHeaderCard(Map<?, ?> rec, List<String> casters, Map<String, Integer> classLevels,
                                          Map<String, Map<String, List<String>>> classSpells, Map<String, String> always,
                                          Map<String, Integer> stats, int pb) {
        JPanel rows = new JPanel();
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setOpaque(false);

        List<String[]> rowSpecs = new ArrayList<>(); // {class, ability}
        for (String cls : casters) rowSpecs.add(new String[]{cls, SpellData.CASTING_ABILITY.get(cls)});
        if (casters.isEmpty() && !always.isEmpty()) {
            // Species / feat spells: the ability is chosen from INT, WIS, or CHA; assume the highest.
            String best = "INT";
            for (String s : new String[]{"WIS", "CHA"}) if (stats.getOrDefault(s, 10) > stats.getOrDefault(best, 10)) best = s;
            rowSpecs.add(new String[]{null, best});
        }
        if (rowSpecs.isEmpty()) {
            JLabel none = new JLabel("This character has no spellcasting class.");
            none.setForeground(TEXT_MUTED);
            none.setFont(new Font("SansSerif", Font.PLAIN, 13));
            return sheetCard("Spellcasting", none);
        }

        for (int r = 0; r < rowSpecs.size(); r++) {
            String cls = rowSpecs.get(r)[0];
            String ab = rowSpecs.get(r)[1];
            int score = stats.getOrDefault(ab, 10);
            int mod = GameData.abilityModifier(score);
            int dc = 8 + pb + mod;
            int atk = pb + mod;
            String abName = STAT_FULL_NAMES[Arrays.asList(GameData.STAT_ORDER).indexOf(ab)];
            String who = cls == null ? "Innate spells" : cls;
            int clsLevel = cls == null ? 0 : classLevels.getOrDefault(cls, 0);

            JPanel tiles = new JPanel(new GridLayout(1, 0, 8, 0));
            tiles.setOpaque(false);

            String classSub = cls == null ? "From species & feats" : "Level " + clsLevel
                    + (SpellData.PACT.equals(SpellData.CASTER_TYPE.get(cls)) ? " · Pact Magic" : "");
            tiles.add(clickable(spellTile(cls == null ? "Innate" : cls, classSub.toUpperCase()), () -> {
                if (cls == null) {
                    showInfoOverlay(tiles, "Innate Spellcasting", "Species & feats",
                            "Spells granted by your species, lineage, or feats are always prepared. When you gain them you choose "
                                    + "Intelligence, Wisdom, or Charisma as their spellcasting ability; the sheet uses your highest.", null);
                } else {
                    String type = SpellData.CASTER_TYPE.get(cls);
                    String kind = SpellData.FULL.equals(type) ? "Full caster: every class level counts toward your spell slots."
                            : SpellData.HALF.equals(type) ? "Half caster: half your class levels (rounded up) count toward your spell slots."
                            : "Pact Magic: a few slots that are always your highest slot level and return on a Short or Long Rest.";
                    String[] info = GameData.CLASS_INFO.get(cls);
                    showInfoOverlay(tiles, cls + " Spellcasting", "Level " + clsLevel,
                            (info != null ? info[1] + "\n\n" : "") + kind,
                            Arrays.asList(row("Spellcasting ability", abName),
                                    row("Highest spell level", SpellData.maxSpellLevel(cls, clsLevel) == 0 ? "—" : String.valueOf(SpellData.maxSpellLevel(cls, clsLevel)))));
                }
            }));
            tiles.add(clickable(spellTile(ab + " " + signed(mod), "SPELLCASTING ABILITY"), () ->
                    showInfoOverlay(tiles, "Spellcasting Ability", who,
                            (cls == null ? "Your innate spells" : who + " spells") + " use " + abName
                                    + ". Its modifier sets your spell save DC and spell attack bonus, and adds to many spells' healing and damage.",
                            Arrays.asList(row(abName + " score", String.valueOf(score)), row("Modifier", signed(mod)),
                                    row("Source", cls == null ? "Species / feat (your choice of INT, WIS, CHA)" : cls + " class")))));
            tiles.add(clickable(spellTile(String.valueOf(dc), "SPELL SAVE DC"), () ->
                    showInfoOverlay(tiles, "Spell Save DC", who,
                            "When one of your spells forces a saving throw, the target must meet or beat this number.",
                            Arrays.asList(row("Base", "8"), row("Proficiency Bonus", signed(pb)),
                                    row(ab + " modifier", signed(mod)), row("Spell save DC", String.valueOf(dc))))));
            tiles.add(clickable(spellTile(signed(atk), "SPELL ATTACK"), () ->
                    showInfoOverlay(tiles, "Spell Attack Bonus", who,
                            "Add this to the d20 when a spell calls for a melee or ranged spell attack.",
                            Arrays.asList(row("Proficiency Bonus", signed(pb)), row(ab + " modifier", signed(mod)),
                                    row("Spell attack", signed(atk))))));

            if (cls != null) {
                Map<String, List<String>> lists = classSpells.getOrDefault(cls, CharacterStorage.emptyClassSpells());
                int preparedCount = 0;
                for (String n : lists.get("prepared")) if (!always.containsKey(n)) preparedCount++;
                final int prepared = preparedCount;
                int maxPrepared = SpellData.preparedCount(cls, clsLevel);
                tiles.add(clickable(spellTile(prepared + " / " + maxPrepared, "PREPARED SPELLS"), () -> {
                    String rule = "Bard".equals(cls) || "Sorcerer".equals(cls) || "Warlock".equals(cls)
                            ? "When you gain a " + cls + " level you can replace one of these spells with another " + cls + " spell."
                            : "Wizard".equals(cls) ? "After a Long Rest you can change your prepared spells to any spells in your spellbook."
                            : "Paladin".equals(cls) || "Ranger".equals(cls) || "Artificer".equals(cls)
                            ? "After a Long Rest you can replace one prepared spell with another " + cls + " spell."
                            : "After a Long Rest you can change your prepared spells to any " + cls + " spells.";
                    showInfoOverlay(tiles, "Prepared Spells", cls + " level " + clsLevel,
                            "The leveled spells you can cast. The number grows as you gain " + cls + " levels (2024 table). "
                                    + "Always-prepared spells from species, feats, and class features don't count against it.\n\n" + rule,
                            Arrays.asList(row("Prepared", String.valueOf(prepared)), row("Allowed at this level", String.valueOf(maxPrepared))));
                }));
                int maxCantrips = SpellData.cantripsKnown(cls, clsLevel);
                if (maxCantrips > 0 || !lists.get("cantrips").isEmpty()) {
                    int known = lists.get("cantrips").size();
                    tiles.add(clickable(spellTile(known + " / " + maxCantrips, "CANTRIPS"), () ->
                            showInfoOverlay(tiles, "Cantrips", cls + " level " + clsLevel,
                                    "Cantrips can be cast at will, without a spell slot. You learn more at higher " + cls + " levels.",
                                    Arrays.asList(row("Known", String.valueOf(known)), row("Allowed at this level", String.valueOf(maxCantrips))))));
                }
                if (SpellData.isPreparedFromBook(cls)) {
                    int book = lists.get("spellbook").size();
                    tiles.add(clickable(spellTile(String.valueOf(book), "SPELLBOOK"), () ->
                            showInfoOverlay(tiles, "Spellbook", "Wizard",
                                    "Your spellbook holds the Wizard spells you've learned: six at level 1, plus two more each Wizard level. "
                                            + "You prepare spells from it, and can cast its Ritual spells without preparing them.",
                                    Collections.singletonList(row("Spells in book", String.valueOf(book))))));
                }
            }
            if (r > 0) rows.add(Box.createRigidArea(new Dimension(0, 8)));
            tiles.setAlignmentX(Component.LEFT_ALIGNMENT);
            rows.add(tiles);
        }
        return sheetCard("Spellcasting", rows);
    }

    private static JPanel spellTile(String value, String caption) {
        JPanel tile = new JPanel(new BorderLayout(0, 2));
        tile.setBackground(SHEET_ROW_ALT);
        tile.setBorder(BorderFactory.createCompoundBorder(new LineBorder(SHEET_BORDER, 1, true), new EmptyBorder(8, 8, 6, 8)));
        JLabel v = new JLabel(value, SwingConstants.CENTER);
        v.setForeground(GOLD);
        v.setFont(new Font("SansSerif", Font.BOLD, 19));
        tile.add(v, BorderLayout.CENTER);
        tile.add(sheetCaption(caption, SwingConstants.CENTER), BorderLayout.SOUTH);
        return tile;
    }

    // ---------------- Spell slots: one row of pips per level, plus Pact Magic ----------------
    private static JPanel spellSlotsCard(SlotState slots, Map<String, Integer> classLevels,
                                         java.util.function.Consumer<SlotState> saveSlots) {
        JPanel rows = new JPanel();
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setOpaque(false);

        int casterLevel = SpellData.casterLevel(classLevels);
        for (int lvl = 1; lvl <= 9; lvl++) {
            int max = slots.max[lvl - 1];
            if (max == 0) continue;
            final int L = lvl;
            JLabel name = slotRowLabel(SpellData.ordinal(lvl) + " level");
            clickable(name, () -> showInfoOverlay(name, "Level " + L + " Spell Slots", "Spellcasting",
                    "Casting a spell of level " + L + " or lower can expend one of these slots (a lower-level spell cast with a higher slot is "
                            + "\"upcast\" for a stronger effect). Click a gold pip to expend a slot, or an empty pip to restore one. "
                            + "All spell slots return when you finish a Long Rest.",
                    Arrays.asList(row("Total", String.valueOf(max)), row("Remaining", String.valueOf(slots.remaining(L))),
                            row("Caster level", String.valueOf(casterLevel)))));
            rows.add(slotRow(name, max, slots.remaining(L), GOLD, () -> {
                SlotState s = slots.copy();
                s.used[L - 1] = Math.min(max, s.used[L - 1] + 1);
                saveSlots.accept(s);
            }, () -> {
                SlotState s = slots.copy();
                s.used[L - 1] = Math.max(0, s.used[L - 1] - 1);
                saveSlots.accept(s);
            }));
        }
        if (slots.pactMax > 0) {
            int warlock = classLevels.getOrDefault("Warlock", 0);
            JLabel name = slotRowLabel("Pact (" + SpellData.ordinal(slots.pactLevel) + ")");
            clickable(name, () -> showInfoOverlay(name, "Pact Magic Slots", "Warlock level " + warlock,
                    "Warlock slots are always level " + slots.pactLevel + " at your Warlock level, so every spell you cast with one is cast at that level. "
                            + "They return when you finish a Short or Long Rest. They can also power spells from your other classes.",
                    Arrays.asList(row("Slots", String.valueOf(slots.pactMax)), row("Slot level", String.valueOf(slots.pactLevel)),
                            row("Remaining", String.valueOf(slots.pactRemaining())))));
            rows.add(slotRow(name, slots.pactMax, slots.pactRemaining(), new Color(170, 120, 220), () -> {
                SlotState s = slots.copy();
                s.pactUsed = Math.min(s.pactMax, s.pactUsed + 1);
                saveSlots.accept(s);
            }, () -> {
                SlotState s = slots.copy();
                s.pactUsed = Math.max(0, s.pactUsed - 1);
                saveSlots.accept(s);
            }));
        }

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        if (slots.pactMax > 0) {
            JButton shortRest = sheetIconButton("Short Rest", "Restore Pact Magic slots");
            shortRest.setFont(new Font("SansSerif", Font.BOLD, 11));
            shortRest.setEnabled(slots.pactUsed > 0);
            shortRest.addActionListener(e -> {
                SlotState s = slots.copy();
                s.pactUsed = 0;
                saveSlots.accept(s);
            });
            actions.add(shortRest);
        }
        JButton longRest = sheetIconButton("Long Rest", "Restore all spell slots");
        longRest.setFont(new Font("SansSerif", Font.BOLD, 11));
        boolean anyUsed = slots.pactUsed > 0;
        for (int u : slots.used) anyUsed |= u > 0;
        longRest.setEnabled(anyUsed);
        longRest.addActionListener(e -> {
            SlotState s = slots.copy();
            s.used = new int[9];
            s.pactUsed = 0;
            saveSlots.accept(s);
        });
        actions.add(longRest);
        return sheetCardWithAction("Spell Slots", actions, rows);
    }

    private static JLabel slotRowLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT_LIGHT);
        l.setFont(new Font("SansSerif", Font.BOLD, 13));
        l.setBorder(new EmptyBorder(2, 4, 2, 4));
        return l;
    }

    // "1st level  ● ● ○ ○   2 / 4": clicking a filled pip expends a slot, an empty one restores it
    private static JPanel slotRow(JLabel name, int max, int remaining, Color color, Runnable expend, Runnable restore) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(new EmptyBorder(3, 0, 3, 0));
        JPanel nameWrap = fixedWidth(name, 96);
        row.add(nameWrap, BorderLayout.WEST);

        JPanel pips = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        pips.setOpaque(false);
        for (int i = 0; i < max; i++) {
            boolean available = i < remaining;
            JLabel pip = new JLabel(new SlotPipIcon(available, color));
            pip.setBorder(new EmptyBorder(2, 2, 2, 2));
            pip.setToolTipText(available ? "Expend a slot" : "Restore a slot");
            pips.add(clickable(pip, available ? expend : restore));
        }
        JLabel count = new JLabel(remaining + " / " + max);
        count.setForeground(remaining == 0 ? TEXT_MUTED : TEXT_LIGHT);
        count.setFont(new Font("SansSerif", Font.PLAIN, 12));
        count.setBorder(new EmptyBorder(0, 8, 0, 0));
        pips.add(count);
        row.add(pips, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    // ---------------- Spell sections and cards ----------------
    private static JPanel spellLevelSection(int level, List<SheetSpell> spells, SlotState slots,
                                            java.util.function.Consumer<SlotState> saveSlots) {
        JPanel head = new JPanel(new BorderLayout(10, 0));
        head.setOpaque(false);
        head.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, GOLD_SOFT), new EmptyBorder(0, 2, 4, 2)));
        JLabel title = new JLabel(level == 0 ? "CANTRIPS" : "LEVEL " + level);
        title.setForeground(GOLD);
        title.setFont(new Font("Serif", Font.BOLD, 18));
        head.add(clickable(title, () -> showInfoOverlay(title, level == 0 ? "Cantrips" : "Level " + level + " Spells", "Spell level",
                level == 0 ? "Cantrips are cast at will without spell slots. Damaging cantrips grow stronger at character levels 5, 11, and 17."
                        : "Casting a level " + level + " spell expends a spell slot of level " + level + " or higher. "
                        + "Spells with the Ritual tag can instead be cast as a Ritual (10 minutes longer, no slot).",
                null)), BorderLayout.WEST);
        String right = level == 0 ? "At will" : "";
        if (level > 0) {
            List<String> parts = new ArrayList<>();
            if (slots.max[level - 1] > 0) parts.add(slots.remaining(level) + " / " + slots.max[level - 1] + " slots");
            if (slots.pactMax > 0 && slots.pactLevel >= level) parts.add(slots.pactRemaining() + " pact");
            right = parts.isEmpty() ? "No slots of this level" : String.join("  ·  ", parts);
        }
        JLabel rightLbl = new JLabel(right);
        rightLbl.setForeground(TEXT_MUTED);
        rightLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        head.add(rightLbl, BorderLayout.EAST);

        int cardWidth = (SHEET_MAX_WIDTH - SPELL_CARD_GAP) / 2;
        JPanel grid = new JPanel(new GridLayout(0, 2, SPELL_CARD_GAP, SPELL_CARD_GAP));
        grid.setOpaque(false);
        for (SheetSpell ss : spells) grid.add(spellCard(ss, cardWidth, slots, saveSlots));
        if (spells.size() % 2 == 1) {
            JPanel filler = new JPanel();
            filler.setOpaque(false);
            grid.add(filler);
        }
        return sheetStack(8, false, head, grid);
    }

    private static JPanel spellCard(SheetSpell ss, int cardWidth, SlotState slots,
                                    java.util.function.Consumer<SlotState> saveSlots) {
        Spell sp = ss.spell;
        String sourceText = String.join(", ", ss.sources);
        JPanel card = new JPanel(new BorderLayout(12, 0));
        card.setBackground(SHEET_CARD);
        Color edge = ss.always ? new Color(110, 190, 120, 150) : ss.bookOnly ? SHEET_BORDER : GOLD_SOFT;
        card.setBorder(BorderFactory.createCompoundBorder(new LineBorder(edge, 1, true), new EmptyBorder(10, 10, 10, 10)));
        Runnable openDetail = () -> showSpellOverlay(card, sp, ss.name, sourceText, ss.always);
        clickableCard(card, openDetail);

        JPanel artWrap = new JPanel(new BorderLayout());
        artWrap.setOpaque(false);
        artWrap.add(new SpellArtFrame(sp, SPELL_ART_SIZE), BorderLayout.NORTH);
        card.add(artWrap, BorderLayout.WEST);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        int textWidth = cardWidth - 2 - 20 - SPELL_ART_SIZE - 12 - 4;

        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.setOpaque(false);
        JLabel name = new JLabel(ss.name);
        name.setForeground(ss.bookOnly ? TEXT_MUTED : GOLD);
        name.setFont(new Font("SansSerif", Font.BOLD, 15));
        top.add(name, BorderLayout.CENTER);
        JButton cast = castButton(ss, slots, saveSlots);
        if (cast != null) {
            followCardHover(card, cast);
            top.add(cast, BorderLayout.EAST);
        }
        top.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.setMaximumSize(new Dimension(textWidth, top.getPreferredSize().height));
        body.add(top);

        String sub = (sp != null ? sp.levelLine() : (ss.level == 0 ? "Cantrip" : "Spell")) + "  ·  " + sourceText;
        JLabel subLbl = new JLabel(sub.toUpperCase());
        subLbl.setForeground(TEXT_MUTED);
        subLbl.setFont(new Font("SansSerif", Font.BOLD, 10));
        subLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        subLbl.setBorder(new EmptyBorder(1, 0, 5, 0));
        body.add(subLbl);

        JPanel badges = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 3));
        badges.setOpaque(false);
        List<JComponent> badgeList = new ArrayList<>();
        if (ss.always) badgeList.add(spellBadge("ALWAYS PREPARED", SPELL_ALWAYS, () -> showInfoOverlay(card, "Always Prepared", sourceText,
                "This spell is granted by " + sourceText + ". It's always prepared and doesn't count against the number of spells you can prepare."
                        + " Species and feat spells can usually also be cast once per Long Rest without a slot.", null)));
        if (ss.bookOnly) badgeList.add(spellBadge("SPELLBOOK", TEXT_MUTED, () -> showInfoOverlay(card, "In Spellbook (Not Prepared)", "Wizard",
                "This spell is in your spellbook but isn't prepared, so you can't cast it with a slot today. Change your prepared spells after a Long Rest. "
                        + "Ritual spells in your spellbook can still be cast as Rituals.", null)));
        if (ss.arcanum) badgeList.add(spellBadge("MYSTIC ARCANUM · 1/LONG REST", new Color(170, 120, 220), () -> showInfoOverlay(card, "Mystic Arcanum", "Warlock",
                "Your patron grants one spell of this level. You can cast it once without a spell slot, and regain the use when you finish a Long Rest.", null)));
        if (sp != null) {
            if (sp.concentration) badgeList.add(spellBadge("C", new Color(120, 170, 230), () -> showConcentrationInfo(card)));
            if (sp.ritual) badgeList.add(spellBadge("R", new Color(200, 160, 90), () -> showRitualInfo(card)));
            if (!sp.componentLetters().isEmpty()) badgeList.add(spellBadge(sp.componentLetters().replace(", ", " "), TEXT_LIGHT, () -> showComponentsInfo(card, sp)));
            badgeList.add(spellBadge(sp.castingTime, TEXT_LIGHT, () -> showInfoOverlay(card, "Casting Time", sp.name, castingTimeInfo(sp.castingTime), null)));
            badgeList.add(spellBadge(sp.range, TEXT_LIGHT, () -> showInfoOverlay(card, "Range", sp.name,
                    "Range: " + sp.range + ". Self means the spell originates from you (often as a Cone, Line, or Emanation); Touch means you must touch the target.", null)));
        }
        if (ss.level == 0) badgeList.add(spellBadge("AT WILL", TEXT_MUTED, () -> showInfoOverlay(card, "At Will", "Cantrip",
                "Cantrips don't use spell slots; you can cast them as often as you like.", null)));
        for (JComponent b : badgeList) {
            followCardHover(card, b);
            badges.add(b);
        }
        badges.setAlignmentX(Component.LEFT_ALIGNMENT);
        // FlowLayout doesn't report a wrapped height, so size the badge row for the card's text width
        badges.setSize(textWidth, 200);
        badges.doLayout();
        int badgeHeight = 0;
        for (Component c : badges.getComponents()) badgeHeight = Math.max(badgeHeight, c.getY() + c.getHeight());
        Dimension bd = new Dimension(textWidth, badgeHeight + 3);
        badges.setPreferredSize(bd);
        badges.setMaximumSize(bd);
        body.add(badges);

        String desc = sp != null && !sp.description.isEmpty() ? snippet(sp.description, 150)
                : "No description in the spell registry yet (add it to SpellData.java).";
        JEditorPane text = conditionLinkedText(desc, textWidth, 12, sp != null ? TEXT_LIGHT : TEXT_MUTED, openDetail);
        text.setBorder(new EmptyBorder(4, 0, 0, 0));
        Dimension td = text.getPreferredSize();
        text.setPreferredSize(new Dimension(td.width, td.height + 4));
        text.setMaximumSize(text.getPreferredSize());
        text.setAlignmentX(Component.LEFT_ALIGNMENT);
        followCardHover(card, text);
        body.add(text);

        // Pin the text column's width so long subtitles truncate instead of widening the grid
        card.add(fixedWidth(body, textWidth), BorderLayout.CENTER);
        return card;
    }

    private static String snippet(String text, int max) {
        if (text.length() <= max) return text;
        int cut = text.lastIndexOf(' ', max);
        return text.substring(0, cut > 40 ? cut : max).replaceAll("[,.;:]$", "") + "…";
    }

    private static JLabel spellBadge(String text, Color color, Runnable onClick) {
        JLabel b = new JLabel(text);
        b.setForeground(color);
        b.setFont(new Font("SansSerif", Font.BOLD, 10));
        b.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(color.getRed(), color.getGreen(), color.getBlue(), 120), 1, true), new EmptyBorder(1, 5, 1, 5)));
        return clickable(b, onClick);
    }

    private static String castingTimeInfo(String time) {
        if ("Action".equals(time)) return "Casting this spell takes your action (the Magic action) on your turn.";
        if ("Bonus Action".equals(time)) return "Cast with your Bonus Action. On a turn you cast a spell this way, "
                + "you can't cast another spell that uses a spell slot.";
        if ("Reaction".equals(time)) return "Cast with your Reaction when the spell's trigger happens, even on another creature's turn.";
        return "Casting this spell takes " + time + " of uninterrupted effort; you must maintain Concentration while casting.";
    }

    private static void showConcentrationInfo(Component anchor) {
        showInfoOverlay(anchor, "Concentration", "Spell tag",
                "You can concentrate on only one spell at a time; casting another Concentration spell ends the first. "
                        + "When you take damage, make a CON save (DC 10 or half the damage, max DC 30) or the spell ends. "
                        + "It also ends if you have the Incapacitated condition or die.", null);
    }

    private static void showRitualInfo(Component anchor) {
        showInfoOverlay(anchor, "Ritual", "Spell tag",
                "You can cast this spell as a Ritual if you have it prepared: it takes 10 minutes longer to cast and doesn't expend a spell slot. "
                        + "Wizards can ritual-cast any Ritual spell in their spellbook.", null);
    }

    private static void showComponentsInfo(Component anchor, Spell sp) {
        List<String[]> rows = new ArrayList<>();
        if (sp.hasComponent("V")) rows.add(row("V (Verbal)", "Chanted words"));
        if (sp.hasComponent("S")) rows.add(row("S (Somatic)", "A free hand to gesture"));
        if (sp.hasComponent("M")) rows.add(row("M (Material)", "Component pouch or focus"));
        String mat = sp.materialText();
        showInfoOverlay(anchor, "Components", sp.name,
                "What casting " + sp.name + " requires." + (mat.isEmpty() ? "" : "\n\nMaterial: " + mat
                        + (mat.contains("consumed") ? "" : mat.contains("GP") ? "\nA material with a cost must be on hand; a focus can't replace it." : "\nA spellcasting focus or component pouch can stand in for it."))
                , rows);
    }

    // Cast button: expends a slot (asking which one when there's a choice); null for cantrips/arcanum
    private static JButton castButton(SheetSpell ss, SlotState slots, java.util.function.Consumer<SlotState> saveSlots) {
        if (ss.level == 0 || ss.arcanum) return null;
        Spell sp = ss.spell;
        boolean ritual = sp != null && sp.ritual;
        if (ss.bookOnly && !ritual) return null;

        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (!ss.bookOnly) {
            for (int lvl = Math.max(1, ss.level); lvl <= 9; lvl++) {
                int rem = slots.remaining(lvl);
                if (rem <= 0) continue;
                final int L = lvl;
                labels.add("Level " + L + " slot" + (L > ss.level ? " (upcast)" : "") + "  —  " + rem + " left");
                actions.add(() -> {
                    SlotState s = slots.copy();
                    s.used[L - 1]++;
                    saveSlots.accept(s);
                });
            }
            if (slots.pactMax > 0 && slots.pactLevel >= ss.level && slots.pactRemaining() > 0) {
                labels.add("Pact slot (level " + slots.pactLevel + ")  —  " + slots.pactRemaining() + " left");
                actions.add(() -> {
                    SlotState s = slots.copy();
                    s.pactUsed++;
                    saveSlots.accept(s);
                });
            }
        }
        if (ritual) {
            labels.add("Cast as a Ritual  —  no slot, +10 minutes");
            actions.add(() -> { });
        }

        JButton b = new JButton(ss.bookOnly ? "Ritual" : "Cast");
        b.setFont(new Font("SansSerif", Font.BOLD, 11));
        b.setFocusPainted(false);
        b.setOpaque(true);
        boolean enabled = !labels.isEmpty();
        b.setEnabled(enabled);
        b.setForeground(enabled ? SHEET_BG : TEXT_MUTED);
        b.setBackground(enabled ? GOLD : SHEET_ROW_ALT);
        b.setBorder(BorderFactory.createCompoundBorder(new LineBorder(enabled ? GOLD : SHEET_BORDER, 1, true), new EmptyBorder(3, 10, 3, 10)));
        b.setCursor(new Cursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        b.setToolTipText(enabled ? "Cast " + ss.name : "No spell slots of level " + ss.level + " or higher left. Take a Long Rest to recover them.");
        b.addActionListener(e -> {
            if (labels.size() == 1 && !ritual) {
                actions.get(0).run();
                return;
            }
            JPanel list = new JPanel(new GridLayout(0, 1, 0, 6));
            list.setOpaque(false);
            Runnable[] close = new Runnable[1];
            for (int i = 0; i < labels.size(); i++) {
                JButton opt = overlayButton(labels.get(i), i == 0);
                opt.setHorizontalAlignment(SwingConstants.LEFT);
                Runnable act = actions.get(i);
                opt.addActionListener(ev -> {
                    close[0].run();
                    act.run();
                });
                list.add(opt);
            }
            JLabel hint = new JLabel("Choose how to cast " + ss.name + ":");
            hint.setForeground(TEXT_MUTED);
            hint.setFont(new Font("SansSerif", Font.PLAIN, 13));
            JPanel body = new JPanel(new BorderLayout(0, 10));
            body.setOpaque(false);
            body.add(hint, BorderLayout.NORTH);
            body.add(list, BorderLayout.CENTER);
            close[0] = showSheetOverlay(b, "Cast " + ss.name, body, null);
        });
        return b;
    }

    // ---------------- Spell detail overlay (also used by the Level Up spell previews) ----------------
    static void showSpellOverlay(Component anchor, Spell sp, String name, String source, boolean always) {
        if (sp == null) {
            showInfoOverlay(anchor, name, "Spell", "This spell isn't in the spell registry yet, so there are no details to show. "
                    + "Add it to SpellData.java to give it a full card.", source == null ? null : Collections.singletonList(row("Source", source)));
            return;
        }
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);

        JPanel top = new JPanel(new BorderLayout(14, 0));
        top.setOpaque(false);
        top.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel artWrap = new JPanel(new BorderLayout());
        artWrap.setOpaque(false);
        artWrap.add(new SpellArtFrame(sp, 96), BorderLayout.NORTH);
        top.add(artWrap, BorderLayout.WEST);

        List<String[]> facts = new ArrayList<>();
        facts.add(row("Level", sp.level == 0 ? "Cantrip" : String.valueOf(sp.level)));
        facts.add(row("School", sp.school));
        facts.add(row("Casting Time", sp.castingTime + (sp.ritual ? " or Ritual" : "")));
        facts.add(row("Range", sp.range));
        facts.add(row("Components", sp.componentLetters()));
        facts.add(row("Duration", sp.duration));
        if (source != null && !source.isEmpty()) facts.add(row("Source", source + (always ? " (always prepared)" : "")));
        JPanel table = new JPanel(new GridBagLayout());
        table.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(1, 0, 1, 12);
        for (int i = 0; i < facts.size(); i++) {
            c.gridy = i;
            c.gridx = 0;
            JLabel k = new JLabel(facts.get(i)[0]);
            k.setForeground(TEXT_MUTED);
            k.setFont(new Font("SansSerif", Font.PLAIN, 12));
            table.add(k, c);
            c.gridx = 1;
            JLabel v = new JLabel(facts.get(i)[1]);
            v.setForeground(TEXT_LIGHT);
            v.setFont(new Font("SansSerif", Font.BOLD, 12));
            table.add(v, c);
        }
        top.add(table, BorderLayout.CENTER);
        body.add(top);

        int width = 420;
        if (!sp.materialText().isEmpty()) {
            body.add(Box.createRigidArea(new Dimension(0, 8)));
            JLabel mat = new JLabel("<html><div style='width:" + width + "px'><i>Material: " + escapeHtml(sp.materialText()) + "</i></div></html>");
            mat.setForeground(TEXT_MUTED);
            mat.setFont(new Font("SansSerif", Font.PLAIN, 12));
            mat.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(mat);
        }

        JPanel tags = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        tags.setOpaque(false);
        tags.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (always) tags.add(spellBadge("ALWAYS PREPARED", SPELL_ALWAYS, () -> showInfoOverlay(tags, "Always Prepared", source,
                "Granted by " + source + "; it doesn't count against the number of spells you can prepare.", null)));
        if (sp.concentration) tags.add(spellBadge("CONCENTRATION", new Color(120, 170, 230), () -> showConcentrationInfo(tags)));
        if (sp.ritual) tags.add(spellBadge("RITUAL", new Color(200, 160, 90), () -> showRitualInfo(tags)));
        if (!sp.componentLetters().isEmpty()) tags.add(spellBadge(sp.componentLetters(), TEXT_LIGHT, () -> showComponentsInfo(tags, sp)));
        if (tags.getComponentCount() > 0) {
            body.add(Box.createRigidArea(new Dimension(0, 10)));
            tags.setMaximumSize(new Dimension(Integer.MAX_VALUE, tags.getPreferredSize().height));
            body.add(tags);
        }

        if (!sp.description.isEmpty()) {
            body.add(Box.createRigidArea(new Dimension(0, 10)));
            JEditorPane desc = conditionLinkedText(sp.description, width, 13, TEXT_LIGHT, null);
            desc.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(desc);
        }
        if (!sp.higherLevels.isEmpty()) {
            body.add(Box.createRigidArea(new Dimension(0, 8)));
            JEditorPane hl = conditionLinkedText((sp.level == 0 ? "Cantrip Upgrade. " : "Using a Higher-Level Spell Slot. ") + sp.higherLevels,
                    width, 12, TEXT_MUTED, null);
            hl.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(hl);
        }
        if (!sp.inflictedConditions.isEmpty()) {
            body.add(Box.createRigidArea(new Dimension(0, 10)));
            JPanel conds = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            conds.setOpaque(false);
            conds.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel lbl = new JLabel("CONDITIONS  ");
            lbl.setForeground(TEXT_MUTED);
            lbl.setFont(new Font("SansSerif", Font.BOLD, 10));
            conds.add(lbl);
            for (String cond : sp.inflictedConditions) conds.add(spellBadge(cond, GOLD, () -> showConditionOverlay(conds, cond)));
            conds.setMaximumSize(new Dimension(Integer.MAX_VALUE, conds.getPreferredSize().height));
            body.add(conds);
        }
        if (!sp.classes.isEmpty()) {
            body.add(Box.createRigidArea(new Dimension(0, 10)));
            JLabel cls = new JLabel("<html><div style='width:" + width + "px'>Spell list: " + escapeHtml(String.join(", ", sp.classes)) + "</div></html>");
            cls.setForeground(TEXT_MUTED);
            cls.setFont(new Font("SansSerif", Font.PLAIN, 11));
            cls.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(cls);
        }
        showSheetOverlay(anchor, sp.name, body, null);
    }

    static void showConditionOverlay(Component anchor, String condition) {
        showInfoOverlay(anchor, condition, "Condition", GameData.CONDITION_INFO.getOrDefault(condition, ""), null);
    }

    // ---------------- Text with clickable condition links ----------------
    // Rebuilt whenever homebrew conditions change (CustomAssets.CHANGE_LISTENERS)
    private static java.util.regex.Pattern CONDITION_PATTERN = buildConditionPattern();

    static void refreshConditionPattern() {
        CONDITION_PATTERN = buildConditionPattern();
    }

    private static java.util.regex.Pattern buildConditionPattern() {
        List<String> raw = new ArrayList<>();
        for (String c : GameData.CONDITION_INFO.keySet()) if (!"Conscious".equals(c)) raw.add(c);
        raw.sort((a, b) -> b.length() - a.length()); // longest first, so "Badly Poisoned" beats "Poisoned"
        List<String> names = new ArrayList<>();
        for (String c : raw) names.add(java.util.regex.Pattern.quote(escapeHtml(c))); // matched against escaped HTML
        return java.util.regex.Pattern.compile("\\b(" + String.join("|", names) + ")\\b");
    }

    // Wraps every condition name (Prone, Frightened, ...) in an HTML link to "cond:<name>"
    static String linkConditions(String escapedHtml) {
        java.util.regex.Matcher m = CONDITION_PATTERN.matcher(escapedHtml);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String name = m.group(1);
            String raw = name.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(
                    "<a href=\"cond:" + java.net.URLEncoder.encode(raw, java.nio.charset.StandardCharsets.UTF_8) + "\">" + name + "</a>"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // Fixed-width wrapped text in which condition names are links that open the condition's explanation.
    // onBackgroundClick (nullable) runs when the text is clicked anywhere except a link.
    static JEditorPane conditionLinkedText(String text, int width, int fontSize, Color color, Runnable onBackgroundClick) {
        javax.swing.text.html.HTMLEditorKit kit = new javax.swing.text.html.HTMLEditorKit();
        kit.setDefaultCursor(Cursor.getPredefinedCursor(onBackgroundClick != null ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        JEditorPane pane = new JEditorPane();
        pane.setEditorKit(kit);
        pane.setEditable(false);
        pane.setOpaque(false);
        pane.setBorder(null);
        pane.setHighlighter(null);
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        pane.setFont(new Font("SansSerif", Font.PLAIN, fontSize));
        pane.setForeground(color);
        javax.swing.text.html.HTMLDocument doc = (javax.swing.text.html.HTMLDocument) kit.createDefaultDocument();
        doc.getStyleSheet().addRule("a { color: #d4af37; text-decoration: underline; }");
        doc.getStyleSheet().addRule("body { margin: 0; }");
        pane.setDocument(doc);
        pane.setText("<html><body>" + linkConditions(escapeHtml(text).replace("\n", "<br>")) + "</body></html>");
        pane.setSize(width, Short.MAX_VALUE);
        Dimension fixed = new Dimension(width, pane.getPreferredSize().height);
        pane.setPreferredSize(fixed);
        pane.setMaximumSize(fixed);
        pane.setMinimumSize(fixed);
        // Links are handled here on release (not via HyperlinkListener, which relies on mouseClicked)
        pane.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (!isClickRelease(e)) return;
                String href = linkAt(pane, e.getPoint());
                if (href != null && href.startsWith("cond:")) {
                    showConditionOverlay(pane, java.net.URLDecoder.decode(href.substring(5), java.nio.charset.StandardCharsets.UTF_8));
                }
                else if (href == null && onBackgroundClick != null) onBackgroundClick.run();
            }
        });
        return pane;
    }

    // href of the link under p, or null
    private static String linkAt(JEditorPane pane, Point p) {
        int pos = pane.viewToModel2D(p);
        if (pos < 0 || !(pane.getDocument() instanceof javax.swing.text.html.HTMLDocument)) return null;
        javax.swing.text.Element el = ((javax.swing.text.html.HTMLDocument) pane.getDocument()).getCharacterElement(pos);
        if (el == null) return null;
        Object a = el.getAttributes().getAttribute(javax.swing.text.html.HTML.Tag.A);
        if (!(a instanceof javax.swing.text.AttributeSet)) return null;
        Object href = ((javax.swing.text.AttributeSet) a).getAttribute(javax.swing.text.html.HTML.Attribute.HREF);
        return href == null ? null : href.toString();
    }

    // Like clickable(), for cards with their own clickable children (badges, links, buttons):
    // the hover tint stays on while the pointer is over those children. Register them with followCardHover.
    static <T extends JComponent> T clickableCard(T card, Runnable onClick) {
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        Color normal = card.getBackground();
        Runnable update = () -> {
            boolean inside = card.isShowing() && card.getMousePosition(true) != null;
            card.setBackground(inside ? SHEET_HOVER : normal);
            card.repaint();
        };
        MouseAdapter hover = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                update.run();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                SwingUtilities.invokeLater(update);
            }
        };
        card.putClientProperty("cardHover", hover);
        card.addMouseListener(hover);
        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (!App.isClickRelease(e)) return;
                if (SwingUtilities.isLeftMouseButton(e)) onClick.run();
            }
        });
        return card;
    }

    static void followCardHover(JComponent card, JComponent child) {
        Object h = card.getClientProperty("cardHover");
        if (h instanceof MouseAdapter) child.addMouseListener((MouseAdapter) h);
    }

    // ---------------- Spell art: image if Spell.imagePath is set, otherwise a school-colored placeholder ----------------
    private static final Map<String, java.awt.image.BufferedImage> SPELL_ART_CACHE = new HashMap<>();

    static java.awt.image.BufferedImage spellArt(Spell sp) {
        if (sp == null || sp.imagePath == null || sp.imagePath.isEmpty()) return null;
        if (!SPELL_ART_CACHE.containsKey(sp.imagePath)) {
            java.awt.image.BufferedImage img = null;
            try {
                img = javax.imageio.ImageIO.read(DataPaths.resource(sp.imagePath).toFile());
            } catch (Exception ex) {
                // missing or unreadable art; fall back to the placeholder frame
            }
            SPELL_ART_CACHE.put(sp.imagePath, img);
        }
        return SPELL_ART_CACHE.get(sp.imagePath);
    }

    static Color schoolColor(String school) {
        if (school == null) return GOLD;
        switch (school) {
            case "Abjuration": return new Color(70, 130, 200);
            case "Conjuration": return new Color(210, 160, 60);
            case "Divination": return new Color(140, 185, 210);
            case "Enchantment": return new Color(200, 100, 170);
            case "Evocation": return new Color(205, 75, 55);
            case "Illusion": return new Color(140, 100, 200);
            case "Necromancy": return new Color(100, 150, 90);
            case "Transmutation": return new Color(190, 130, 80);
            default: return GOLD;
        }
    }

    static class SpellArtFrame extends JComponent {
        private final Spell spell;
        private final int size;

        SpellArtFrame(Spell spell, int size) {
            this.spell = spell;
            this.size = size;
            Dimension d = new Dimension(size, size);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(d);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            java.awt.geom.RoundRectangle2D frame = new java.awt.geom.RoundRectangle2D.Float(0.5f, 0.5f, size - 1, size - 1, 12, 12);
            java.awt.image.BufferedImage img = spellArt(spell);
            Color tone = schoolColor(spell != null ? spell.school : null);
            g2.setClip(frame);
            if (img != null) {
                double scale = Math.max(size / (double) img.getWidth(), size / (double) img.getHeight());
                int w = (int) Math.ceil(img.getWidth() * scale), h = (int) Math.ceil(img.getHeight() * scale);
                g2.drawImage(img, (size - w) / 2, (size - h) / 2, w, h, null);
            } else {
                g2.setPaint(new GradientPaint(0, 0, new Color(tone.getRed(), tone.getGreen(), tone.getBlue(), 150), size, size, SHEET_BG));
                g2.fill(frame);
                // inner placeholder frame (where art will go)
                g2.setColor(new Color(255, 255, 255, 35));
                g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{3f, 3f}, 0f));
                int inset = Math.max(6, size / 10);
                g2.drawRoundRect(inset, inset, size - inset * 2 - 1, size - inset * 2 - 1, 8, 8);
                String glyph = spell == null ? "?" : spell.school.length() >= 3 ? spell.school.substring(0, 3).toUpperCase() : spell.school.toUpperCase();
                g2.setFont(new Font("Serif", Font.BOLD, Math.max(12, size / 4)));
                FontMetrics fm = g2.getFontMetrics();
                g2.setColor(new Color(255, 255, 255, 210));
                g2.drawString(glyph, (size - fm.stringWidth(glyph)) / 2, size / 2 + fm.getAscent() / 2 - 3);
                String lvl = spell == null ? "" : spell.level == 0 ? "CANTRIP" : "LEVEL " + spell.level;
                g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(8, size / 9)));
                fm = g2.getFontMetrics();
                g2.setColor(new Color(255, 255, 255, 150));
                g2.drawString(lvl, (size - fm.stringWidth(lvl)) / 2, size - inset - 4);
            }
            g2.setClip(null);
            g2.setColor(new Color(tone.getRed(), tone.getGreen(), tone.getBlue(), 200));
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(frame);
            g2.dispose();
        }
    }

    // Larger pip for spell slots: filled = available, outlined = expended
    static class SlotPipIcon implements Icon {
        private final boolean filled;
        private final Color color;

        SlotPipIcon(boolean filled, Color color) {
            this.filled = filled;
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Path2D diamond = new Path2D.Float();
            diamond.moveTo(x + 9, y + 1);
            diamond.lineTo(x + 17, y + 9);
            diamond.lineTo(x + 9, y + 17);
            diamond.lineTo(x + 1, y + 9);
            diamond.closePath();
            if (filled) {
                g2.setColor(color);
                g2.fill(diamond);
            } else {
                g2.setColor(TEXT_MUTED);
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(diamond);
            }
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 18;
        }

        @Override
        public int getIconHeight() {
            return 18;
        }
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
                int dv = SpeciesTraits.darkvision(draft.species, draft.lineage);
                if (dv > 0) moveText += "  |  Darkvision: " + dv + " ft";
                movementValueLabel.setText(moveText);
            }
            classValueLabel.setText("Class: " + (draft.characterClass.isEmpty() ? "\u2014" : draft.characterClass));

            Integer conScore = draft.stats.get("CON");
            int hd = GameData.hitDieMax(draft.characterClass);
            if (hd > 0 && conScore != null) {
                boolean tough = "Dwarf".equals(draft.species) && CustomAssets.speciesAsset("Dwarf") == null;
                int hp = Math.max(1, hd + GameData.abilityModifier(conScore)) + (tough ? 1 : 0);
                hpValueLabel.setText("HP: " + hp + "  (d" + hd + " + CON mod" + (tough ? " + 1 Dwarven Toughness" : "") + ")");
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

        // Map token look (shape / color / picture); rebuilt from draft.token on reset and edit
        JPanel tokenHolder = new JPanel(new BorderLayout(0, 6));
        tokenHolder.setOpaque(false);
        Runnable rebuildTokenEditor = () -> {
            tokenHolder.removeAll();
            JLabel tokenLabel = new JLabel("Map Token:");
            tokenLabel.setForeground(TEXT_LIGHT);
            tokenLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
            tokenHolder.add(tokenLabel, BorderLayout.NORTH);
            Tokens.Editor[] ed = new Tokens.Editor[1];
            ed[0] = Tokens.editor(draft.token, nameField::getText, Tokens.PC_COLOR, () -> {
                if (ed[0] != null) draft.token = ed[0].value();
            });
            tokenHolder.add(ed[0].panel, BorderLayout.CENTER);
            tokenHolder.revalidate();
            tokenHolder.repaint();
        };
        rebuildTokenEditor.run();
        nameField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { tokenHolder.repaint(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { tokenHolder.repaint(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { tokenHolder.repaint(); }
        });

        nGbc.gridx = 0; nGbc.gridy = 0; nameContent.add(nameLabel, nGbc);
        nGbc.gridx = 0; nGbc.gridy = 1; nameContent.add(nameField, nGbc);
        nGbc.gridx = 0; nGbc.gridy = 2; nGbc.insets = new Insets(22, 10, 10, 10);
        nameContent.add(tokenHolder, nGbc);
        nGbc.gridx = 0; nGbc.gridy = 3; nGbc.insets = new Insets(25, 10, 10, 10);
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
                new ArrayList<>(GameData.SPECIES_INFO.keySet()), 1, speciesInfoWithTraits(),
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
        Runnable rebuildClassInfoMap = () -> { // re-run on reset so homebrew classes show up
            classInfoMap.clear();
            for (Map.Entry<String, String[]> entry : GameData.CLASS_INFO.entrySet()) {
                String hitDice = entry.getValue()[0];
                String description = entry.getValue()[1];
                classInfoMap.put(entry.getKey(), ("N/A".equals(hitDice) ? "" : "Hit Dice: " + hitDice + "\n\n") + description);
            }
        };
        rebuildClassInfoMap.run();
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

        // ===================== LANGUAGES STEP =====================
        // 2024 rules: Common plus two standard languages of your choice.
        JPanel languagesPanel = new JPanel(new BorderLayout());
        languagesPanel.setBackground(BG_DARK);

        JPanel languagesHeaderWrap = new JPanel(new BorderLayout());
        languagesHeaderWrap.setBackground(BG_DARK);
        languagesHeaderWrap.add(buildWizardTopBar(() -> innerCardLayout.show(innerCards, "BACKGROUND_SKILLS"), outerCardLayout, outerCards, editModeHolder, saveChangeButtons), BorderLayout.NORTH);
        JLabel languagesTitleLabel = new JLabel("Languages:");
        languagesTitleLabel.setForeground(GOLD);
        languagesTitleLabel.setFont(new Font("Serif", Font.BOLD, 24));
        languagesTitleLabel.setBorder(new EmptyBorder(0, 25, 10, 0));
        languagesHeaderWrap.add(languagesTitleLabel, BorderLayout.SOUTH);
        languagesPanel.add(languagesHeaderWrap, BorderLayout.NORTH);

        TranslucentPanel languagesInfoPanel = new TranslucentPanel(new Color(255, 255, 255, 25));
        languagesInfoPanel.setLayout(new BorderLayout());
        languagesInfoPanel.setPreferredSize(new Dimension(260, 100));
        languagesInfoPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        JLabel languagesInfoTitle = new JLabel("Languages");
        languagesInfoTitle.setForeground(GOLD);
        languagesInfoTitle.setFont(new Font("Serif", Font.BOLD, 20));
        JTextArea languagesInfoText = new JTextArea("You know Common. Choose two more standard languages. Click the (i) next to a language to learn about it.");
        styleInfoTextArea(languagesInfoText);
        JPanel languagesInfoTop = new JPanel(new BorderLayout());
        languagesInfoTop.setOpaque(false);
        languagesInfoTop.add(languagesInfoTitle, BorderLayout.NORTH);
        languagesInfoTop.add(languagesInfoText, BorderLayout.CENTER);
        languagesInfoPanel.add(languagesInfoTop, BorderLayout.NORTH);
        JPanel languagesInfoWrap = new JPanel(new BorderLayout());
        languagesInfoWrap.setBackground(BG_DARK);
        languagesInfoWrap.setBorder(new EmptyBorder(10, 10, 10, 15));
        languagesInfoWrap.add(languagesInfoPanel, BorderLayout.CENTER);
        languagesPanel.add(languagesInfoWrap, BorderLayout.EAST);

        JPanel languagesContent = new JPanel();
        languagesContent.setLayout(new BoxLayout(languagesContent, BoxLayout.Y_AXIS));
        languagesContent.setBackground(BG_DARK);
        languagesContent.setBorder(new EmptyBorder(15, 25, 10, 10));
        JLabel languagesIntro = new JLabel("You know Common. Choose two more:");
        languagesIntro.setForeground(TEXT_LIGHT);
        languagesIntro.setFont(new Font("SansSerif", Font.PLAIN, 16));
        languagesIntro.setAlignmentX(Component.LEFT_ALIGNMENT);
        languagesContent.add(languagesIntro);
        languagesContent.add(Box.createRigidArea(new Dimension(0, 10)));
        CheckListResult languagesCheckList = buildCheckListPanel(
                new ArrayList<>(GameData.STANDARD_LANGUAGES.keySet()), 2, GameData.STANDARD_LANGUAGES,
                languagesInfoTitle, languagesInfoText, false, 9, null);
        languagesContent.add(languagesCheckList.component);
        JPanel languagesContentWrap = new JPanel(new BorderLayout());
        languagesContentWrap.setBackground(BG_DARK);
        languagesContentWrap.add(languagesContent, BorderLayout.NORTH);
        languagesPanel.add(languagesContentWrap, BorderLayout.CENTER);

        JButton languagesNextButton = new JButton("Next →");
        styleAccentButton(languagesNextButton);
        JPanel languagesBottomBar = new JPanel(new FlowLayout(FlowLayout.CENTER));
        languagesBottomBar.setBackground(BG_DARK);
        languagesBottomBar.setBorder(new EmptyBorder(10, 0, 20, 0));
        languagesBottomBar.add(languagesNextButton);
        languagesPanel.add(languagesBottomBar, BorderLayout.SOUTH);

        // ===================== CLASS DETAILS STEP =====================
        JPanel classDecisionsPanel = new JPanel(new BorderLayout());
        classDecisionsPanel.setBackground(BG_DARK);

        JPanel classDecisionsHeaderWrap = new JPanel(new BorderLayout());
        classDecisionsHeaderWrap.setBackground(BG_DARK);
        classDecisionsHeaderWrap.add(buildWizardTopBar(() -> innerCardLayout.show(innerCards, "LANGUAGES"), outerCardLayout, outerCards, editModeHolder, saveChangeButtons), BorderLayout.NORTH);

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
                JLabel none = new JLabel(draft.characterClass.isEmpty()
                        ? "Choose a class first; its skills, spells, and other choices will appear here."
                        : "No additional choices for this class.");
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
                    // Cantrip labels changed with the 2024 counts; fall back to the saved cantrip list
                    if ((prevVals == null || prevVals.isEmpty()) && "CANTRIPS".equals(item.type)) prevVals = draft.cantripsKnown;
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
                    List<String> options = c.weaponPool != null ? Tags.visible(CustomAssets.WEAPONS, WeaponData.weaponsInPool(c.weaponPool)) : Arrays.asList(c.options);
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
                    List<String> options = c.weaponPool != null ? Tags.visible(CustomAssets.WEAPONS, WeaponData.weaponsInPool(c.weaponPool)) : Arrays.asList(c.options);
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
            List<String> mergedEquip = mergeItems(draft.equipment);
            draft.equipment.clear();
            draft.equipment.addAll(mergedEquip);
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
            List<String> mergedEquip = mergeItems(draft.equipment);
            draft.equipment.clear();
            draft.equipment.addAll(mergedEquip);
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
            refreshSidebar.run();
            languagesCheckList.setSelected.accept(draft.languages);
            innerCardLayout.show(innerCards, "LANGUAGES");
        });

        // ---- Wire Languages Next button ----
        languagesNextButton.addActionListener(e -> {
            List<String> chosen = languagesCheckList.getSelected.get();
            if (chosen.size() != 2) {
                languagesInfoTitle.setText("Choose two languages");
                languagesInfoText.setText("Please select exactly two languages before continuing.");
                return;
            }
            draft.languages = new ArrayList<>(chosen);
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
        innerCards.add(languagesPanel, "LANGUAGES");
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
                public void mouseReleased(MouseEvent e) {
                    if (!App.isClickRelease(e)) return;
                    // Any step can be opened at any time; unfinished steps are listed when saving.
                    if (segIdx == currentSegmentHolder[0]) return;
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
                {0}, {1}, {1}, {2}, {3}, {4}, {3}, {4}, {4}, {5}, {6}, {6}, {6}, {6}
        };
        JPanel[] allStepPanels = {
                namePanel, speciesPanel, lineagePanel, classPanel, statsPanel, backgroundPanel,
                asiPanel, bgSkillsPanel, languagesPanel, classDecisionsPanel, equipBgChoicePanel, equipBgDetailPanel,
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
            // Pick up homebrew species / classes / backgrounds added or changed in the Asset Library,
            // minus anything whose tags are turned off in Tags & Filters
            Tags.setOwned(null);
            speciesCheckList.setOptions.accept(Tags.visible(CustomAssets.SPECIES, GameData.SPECIES_INFO.keySet()), speciesInfoWithTraits());
            rebuildClassInfoMap.run();
            classCheckList.setOptions.accept(Tags.visible(CustomAssets.CLASSES, GameData.CLASS_INFO.keySet()), classInfoMap);
            backgroundCheckList.setOptions.accept(Tags.visible(CustomAssets.BACKGROUNDS, GameData.BACKGROUND_INFO.keySet()), GameData.BACKGROUND_INFO);

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
            draft.languages.clear();
            languagesCheckList.reset.run();
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
            draft.token = Tokens.defaults(Tokens.PC_COLOR);
            rebuildTokenEditor.run();

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

            List<String> selLanguages = languagesCheckList.getSelected.get();
            if (selLanguages.size() == 2) draft.languages = selLanguages;
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
                showNoticeOverlay(wizard, "Incomplete Changes",
                        "You can't save yet \u2014 please finish choosing:\n\u2022 " + String.join("\n\u2022 ", problems));
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
            draft.languages = CharacterStorage.recList(rec, "languages");
            languagesCheckList.setSelected.accept(draft.languages);
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

            // Content filters never take anything away from an existing character: its own picks stay
            // visible in the pickers even if their tags are turned off
            List<String> owned = new ArrayList<>(Arrays.asList(draft.species, draft.characterClass, draft.background));
            owned.addAll(draft.cantripsKnown);
            owned.addAll(draft.spellsKnown);
            owned.addAll(draft.equipment);
            Tags.setOwned(owned);
            speciesCheckList.setOptions.accept(Tags.visible(CustomAssets.SPECIES, GameData.SPECIES_INFO.keySet()), speciesInfoWithTraits());
            classCheckList.setOptions.accept(Tags.visible(CustomAssets.CLASSES, GameData.CLASS_INFO.keySet()), classInfoMap);
            backgroundCheckList.setOptions.accept(Tags.visible(CustomAssets.BACKGROUNDS, GameData.BACKGROUND_INFO.keySet()), GameData.BACKGROUND_INFO);

            // Prefill the persistent UI widgets that don't get rebuilt on every visit
            nameField.setText(draft.name);
            draft.token = Tokens.read(rec.get("token"), Tokens.PC_COLOR);
            rebuildTokenEditor.run();

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
            this(ac, 150, 168);
        }

        ShieldPanel(int ac, int width, int height) {
            this.ac = ac;
            Dimension d = new Dimension(width, height);
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

            GradientPaint fill = new GradientPaint(0, (float) pad, SHEET_ROW_ALT, 0, h - (float) pad, SHEET_CARD);
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

        // Track the viewport width only while it is wide enough for the content's minimum
        // width; below that, fall back to horizontal scrolling instead of crushing the layout.
        @Override
        public boolean getScrollableTracksViewportWidth() {
            Container parent = getParent();
            return !(parent instanceof JViewport) || parent.getWidth() >= getMinimumSize().width;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    // Lays out a single child at a fixed content width, horizontally centered in the
    // container (used to cap the character sheet at SHEET_MAX_WIDTH on wide monitors).
    static class MaxWidthCenterLayout implements LayoutManager {
        private final int maxWidth;

        MaxWidthCenterLayout(int maxWidth) {
            this.maxWidth = maxWidth;
        }

        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            Insets in = parent.getInsets();
            int h = 0;
            for (Component c : parent.getComponents()) h = Math.max(h, c.getPreferredSize().height);
            return new Dimension(maxWidth + in.left + in.right, h + in.top + in.bottom);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return preferredLayoutSize(parent);
        }

        @Override
        public void layoutContainer(Container parent) {
            Insets in = parent.getInsets();
            int avail = parent.getWidth() - in.left - in.right;
            int x = in.left + Math.max(0, (avail - maxWidth) / 2);
            int availH = parent.getHeight() - in.top - in.bottom;
            for (Component c : parent.getComponents()) {
                c.setBounds(x, in.top, maxWidth, Math.max(availH, c.getPreferredSize().height));
            }
        }
    }

    // Round pip used for the death-save checkboxes (outline when empty, filled when checked)
    static class PipIcon implements Icon {
        private final boolean filled;
        private final Color color;

        PipIcon(boolean filled, Color color) {
            this.filled = filled;
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (filled) {
                g2.setColor(color);
                g2.fillOval(x + 1, y + 1, 12, 12);
            } else {
                g2.setColor(TEXT_MUTED);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawOval(x + 1, y + 1, 12, 12);
            }
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 14;
        }

        @Override
        public int getIconHeight() {
            return 14;
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
