import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

// Character sheet side of class features (ClassFeatures): one card per class / subclass feature with what it does,
// its uses (click a pip to spend / regain), the picks it asks for (Expertise, Fighting Style, Metamagic...), and a Use
// button for features used outside combat (Arcane Recovery, Magical Cunning). Short Rest / Long Rest restore uses.
// Everything is saved on the record through `onSave` (featureUses, featureChoices, spell slot usage).
class SheetFeatures {

private static final Color PIP_ON = App.GOLD, PIP_OFF = new Color(0x55, 0x5A, 0x70);

// Short Rest / Long Rest buttons shown above the feature cards
static JComponent restBar(Map<?, ?> rec, Consumer<Map<String, Object>> onSave) {
    JPanel bar = new JPanel(new GridLayout(1, 2, 8, 0));
    bar.setOpaque(false);
    JButton shortRest = Tokens.smallButton("☾ Short Rest", "Recover Short Rest uses and Pact Magic slots");
    JButton longRest = Tokens.smallButton("☀ Long Rest", "Recover every feature use and spell slot");
    shortRest.addActionListener(e -> App.showConfirmOverlay(shortRest, "Short Rest",
            "Take a Short Rest (1 hour)? Features that recharge on a Short Rest regain all their uses, features that regain one use on a "
                    + "Short Rest get one back, and Pact Magic slots return. Spend Hit Dice at the table to heal."
                    + restPreview(rec, false), "Short Rest", () -> {
                Map<String, Object> work = App.recCopy(rec);
                ClassFeatures.rest(work, false);
                onSave.accept(work);
            }));
    longRest.addActionListener(e -> App.showConfirmOverlay(longRest, "Long Rest",
            "Take a Long Rest (8 hours)? Every feature use and every spell slot comes back." + restPreview(rec, true), "Long Rest", () -> {
                Map<String, Object> work = App.recCopy(rec);
                ClassFeatures.rest(work, true);
                onSave.accept(work);
            }));
    bar.add(shortRest);
    bar.add(longRest);
    return bar;
}

// "\n\nRecovers: Rage 1, Second Wind 2" (uses that would come back)
private static String restPreview(Map<?, ?> rec, boolean longRest) {
    List<String> parts = new ArrayList<>();
    Map<String, Integer> used = ClassFeatures.usedMap(rec);
    for (ClassFeatures.Active a : ClassFeatures.active(rec)) {
        if (!a.f.hasUses()) continue;
        int u = used.getOrDefault(a.key(), 0);
        if (u <= 0) continue;
        int back = longRest || ClassFeatures.R_SHORT.equals(a.f.recharge) ? u : ClassFeatures.R_SHORT_ONE.equals(a.f.recharge) ? 1 : 0;
        if (back > 0) parts.add(a.f.name + " +" + back);
    }
    return parts.isEmpty() ? "" : "\n\nRecovers: " + String.join(", ", parts) + ".";
}

// One card per class / subclass feature the character has
static List<JComponent> cards(Map<?, ?> rec, Consumer<Map<String, Object>> onSave) {
    List<JComponent> out = new ArrayList<>();
    List<ClassFeatures.Active> all = ClassFeatures.active(rec);
    for (ClassFeatures.Active a : all) {
        if (ClassFeatures.hidden(a.f)) continue;
        out.add(card(rec, all, a, onSave));
    }
    return out;
}

private static JComponent card(Map<?, ?> rec, List<ClassFeatures.Active> all, ClassFeatures.Active a, Consumer<Map<String, Object>> onSave) {
    ClassFeatures.Feature f = a.f;
    List<String> mech = ClassFeatures.mechanics(f, rec, a.clsLevel, all, a.cls);
    String subtitle = a.source + (ClassFeatures.PASSIVE.equals(f.activation) ? "" : " · " + ClassFeatures.activationShort(f.activation));

    JPanel card = new JPanel();
    card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
    card.setBackground(App.SHEET_ROW_ALT);
    card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, App.GOLD), new EmptyBorder(8, 10, 8, 10)));

    StringBuilder html = new StringBuilder("<html><div style='width:290px'>");
    html.append("<b style='color:#d4af37; font-size:12px'>").append(App.escapeHtml(f.name)).append("</b>");
    html.append("<br><span style='color:#aaa5a0; font-size:9px'>").append(App.escapeHtml(subtitle.toUpperCase())).append("</span>");
    if (!f.description.isEmpty()) html.append("<div style='margin-top:4px'>").append(App.escapeHtml(f.description)).append("</div>");
    List<String> shown = new ArrayList<>();
    for (String m : mech) if (!m.startsWith("Uses:") && !m.startsWith("Pool:") && !m.startsWith("Choose ")) shown.add(m);
    if (!shown.isEmpty()) {
        html.append("<div style='margin-top:4px; color:#b8d0ff; font-size:10px'>").append(App.escapeHtml(String.join(" · ", shown))).append("</div>");
    }
    html.append("</div></html>");
    JLabel lbl = new JLabel(html.toString());
    lbl.setForeground(App.TEXT_LIGHT);
    lbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
    lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
    card.add(lbl);

    // uses
    ClassFeatures.Active owner = f.hasUses() ? a : null;
    if (owner != null) {
        int max = ClassFeatures.maxUses(owner, rec);
        int left = ClassFeatures.usesLeft(rec, owner);
        card.add(Box.createRigidArea(new Dimension(0, 6)));
        card.add(usesRow(rec, owner, max, left, onSave));
    } else if (!f.shareWith.isEmpty()) {
        ClassFeatures.Active po = ClassFeatures.poolOwner(all, a);
        if (po != null) {
            JLabel sl = new JLabel((f.cost > 0 ? "Spends " + f.cost + " of " : "Uses ") + po.f.name + " (" + ClassFeatures.usesLeft(rec, po) + " left)");
            sl.setForeground(App.TEXT_MUTED);
            sl.setFont(new Font("SansSerif", Font.PLAIN, 11));
            sl.setAlignmentX(Component.LEFT_ALIGNMENT);
            sl.setBorder(new EmptyBorder(4, 0, 0, 0));
            card.add(sl);
        }
    }

    // choices
    if (!ClassFeatures.C_NONE.equals(f.choiceKind)) {
        card.add(Box.createRigidArea(new Dimension(0, 6)));
        card.add(choiceRow(rec, a, onSave));
    }

    // used outside combat
    if (ClassFeatures.REST.equals(f.activation) && !ClassFeatures.RS_NONE.equals(f.restore)) {
        int left = ClassFeatures.usesLeft(rec, ClassFeatures.poolOwner(all, a));
        boolean needsUse = ClassFeatures.poolOwner(all, a) != null;
        JButton use = Tokens.smallButton("Use " + f.name, needsUse && left <= 0 ? "No uses left: recharges on a " + f.recharge : "Use it now");
        use.setEnabled(!needsUse || left > 0);
        use.setAlignmentX(Component.LEFT_ALIGNMENT);
        use.addActionListener(e -> useOutOfCombat(use, rec, all, a, onSave));
        card.add(Box.createRigidArea(new Dimension(0, 6)));
        card.add(use);
    }

    // the whole card: full details
    List<String[]> rows = new ArrayList<>();
    for (String m : mech) {
        int i = m.indexOf(": ");
        rows.add(i > 0 && i < 30 ? App.row(m.substring(0, i), m.substring(i + 2)) : App.row("•", m));
    }
    rows.add(App.row("From", a.source + (a.homebrew ? " (homebrew)" : " (2024 PHB)")));
    if (owner != null) rows.add(App.row("Uses left", ClassFeatures.usesLeft(rec, owner) + " of " + ClassFeatures.maxUses(owner, rec)));
    if (!ClassFeatures.C_NONE.equals(f.choiceKind)) {
        List<String> picks = ClassFeatures.choices(rec, a);
        rows.add(App.row("Chosen", picks.isEmpty() ? "nothing yet" : String.join(", ", picks)));
    }
    String desc = f.description.isEmpty() ? "No description." : f.description;
    if (f.usable() && !ClassFeatures.REST.equals(f.activation)) desc += "\n\nUse it in Combat from the Current Turn tab (Class Features).";
    final String fullDesc = desc;
    App.clickable(card, () -> App.showInfoOverlay(card, f.name, subtitle, fullDesc, rows));
    return card;
}

// ---------------- uses ----------------
private static JComponent usesRow(Map<?, ?> rec, ClassFeatures.Active owner, int max, int left, Consumer<Map<String, Object>> onSave) {
    JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
    row.setOpaque(false);
    row.setAlignmentX(Component.LEFT_ALIGNMENT);
    JLabel cap = new JLabel((owner.f.spendAny ? "POOL" : "USES") + "  ");
    cap.setForeground(App.TEXT_MUTED);
    cap.setFont(new Font("SansSerif", Font.BOLD, 10));
    row.add(cap);
    Consumer<Integer> setLeft = n -> {
        Map<String, Object> work = App.recCopy(rec);
        ClassFeatures.setUsed(work, owner.key(), max - Math.max(0, Math.min(max, n)));
        onSave.accept(work);
    };
    if (max <= 12 && !owner.f.spendAny) {
        for (int i = 0; i < max; i++) {
            boolean filled = i < left;
            JLabel pip = new JLabel(filled ? "●" : "○");
            pip.setForeground(filled ? PIP_ON : PIP_OFF);
            pip.setFont(new Font("SansSerif", Font.PLAIN, 16));
            pip.setToolTipText(filled ? "Click to spend a use" : "Click to regain a use");
            pip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            pip.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    setLeft.accept(filled ? left - 1 : left + 1);
                }
                @Override
                public void mouseEntered(MouseEvent e) { pip.setForeground(App.TEXT_LIGHT); }
                @Override
                public void mouseExited(MouseEvent e) { pip.setForeground(filled ? PIP_ON : PIP_OFF); }
            });
            row.add(pip);
        }
    } else {
        JButton minus = Tokens.smallButton("−", "Spend one");
        minus.setEnabled(left > 0);
        minus.addActionListener(e -> setLeft.accept(left - 1));
        JLabel val = new JLabel(" " + left + " / " + max + " ");
        val.setForeground(App.GOLD);
        val.setFont(new Font("SansSerif", Font.BOLD, 13));
        JButton plus = Tokens.smallButton("+", "Regain one");
        plus.setEnabled(left < max);
        plus.addActionListener(e -> setLeft.accept(left + 1));
        row.add(minus);
        row.add(val);
        row.add(plus);
    }
    JLabel rc = new JLabel("  " + rechargeShort(owner.f.recharge));
    rc.setForeground(App.TEXT_MUTED);
    rc.setFont(new Font("SansSerif", Font.PLAIN, 10));
    row.add(rc);
    return row;
}

private static String rechargeShort(String r) {
    switch (r) {
        case ClassFeatures.R_SHORT: return "Short Rest";
        case ClassFeatures.R_SHORT_ONE: return "Long Rest (+1 on Short)";
        default: return "Long Rest";
    }
}

// ---------------- choices ----------------
private static JComponent choiceRow(Map<?, ?> rec, ClassFeatures.Active a, Consumer<Map<String, Object>> onSave) {
    JPanel row = new JPanel(new AssetLibraryPanel.WrapLayout(FlowLayout.LEFT, 4, 3));
    row.setOpaque(false);
    row.setAlignmentX(Component.LEFT_ALIGNMENT);
    List<String> picks = ClassFeatures.choices(rec, a);
    int count = ClassFeatures.choiceCount(rec, a);
    for (String p : picks) {
        JLabel chip = new JLabel(p);
        chip.setOpaque(true);
        chip.setBackground(App.SHEET_CARD);
        chip.setForeground(App.GOLD);
        chip.setFont(new Font("SansSerif", Font.BOLD, 11));
        chip.setBorder(BorderFactory.createCompoundBorder(new LineBorder(App.SHEET_BORDER, 1, true), new EmptyBorder(2, 7, 2, 7)));
        App.clickable(chip, () -> App.showInfoOverlay(chip, p, a.f.choiceKind + " · " + a.f.name, ClassFeatures.optionInfo(a.f.choiceKind, p), null));
        row.add(chip);
    }
    JButton pick = Tokens.smallButton(picks.size() < count ? "Choose (" + picks.size() + " / " + count + ")" : "Change",
            "Pick " + count + " × " + a.f.choiceKind);
    if (picks.size() < count) pick.setForeground(App.GOLD);
    pick.addActionListener(e -> showChoicePicker(pick, rec, a, onSave));
    row.add(pick);
    return row;
}

static void showChoicePicker(Component anchor, Map<?, ?> rec, ClassFeatures.Active a, Consumer<Map<String, Object>> onSave) {
    List<String> options = ClassFeatures.choiceOptions(rec, a);
    List<String> current = ClassFeatures.choices(rec, a);
    int count = ClassFeatures.choiceCount(rec, a);
    JPanel body = new JPanel();
    body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
    body.setOpaque(false);
    JLabel intro = new JLabel("<html><div style='width:440px'>Choose " + count + " × " + App.escapeHtml(a.f.choiceKind)
            + (C_EXP(a) ? " from the skills you're proficient in." : ".") + " Click ⓘ for what an option does.</div></html>");
    intro.setForeground(App.TEXT_LIGHT);
    intro.setFont(new Font("SansSerif", Font.PLAIN, 13));
    intro.setAlignmentX(Component.LEFT_ALIGNMENT);
    body.add(intro);
    body.add(Box.createRigidArea(new Dimension(0, 8)));
    JLabel err = new JLabel(" ");
    err.setForeground(App.ACCENT_HOVER);
    err.setFont(new Font("SansSerif", Font.BOLD, 12));
    JPanel grid = new JPanel(new GridLayout(0, 2, 8, 2));
    grid.setOpaque(false);
    grid.setAlignmentX(Component.LEFT_ALIGNMENT);
    Map<String, JCheckBox> boxes = new LinkedHashMap<>();
    Set<String> all = new LinkedHashSet<>(options);
    all.addAll(current); // keep earlier picks visible even if no longer offered
    for (String o : all) {
        JPanel cell = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        cell.setOpaque(false);
        JCheckBox b = new JCheckBox(o, current.contains(o));
        b.setOpaque(false);
        b.setForeground(App.TEXT_LIGHT);
        b.setFont(new Font("SansSerif", Font.PLAIN, 13));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> {
            int n = 0;
            for (JCheckBox x : boxes.values()) if (x.isSelected()) n++;
            if (n > count) {
                b.setSelected(false);
                err.setText("Pick at most " + count + ".");
            } else err.setText(" ");
        });
        boxes.put(o, b);
        JLabel info = new JLabel("ⓘ");
        info.setForeground(App.GOLD);
        App.clickable(info, () -> App.showInfoOverlay(info, o, a.f.choiceKind, ClassFeatures.optionInfo(a.f.choiceKind, o), null));
        cell.add(b);
        cell.add(info);
        grid.add(cell);
    }
    if (all.isEmpty()) {
        JLabel none = new JLabel(C_EXP(a) ? "No skill proficiencies to choose from yet." : "Nothing to choose from.");
        none.setForeground(App.TEXT_MUTED);
        grid.add(none);
    }
    JScrollPane sp = new JScrollPane(grid);
    App.applyDarkScrollBar(sp);
    sp.setBorder(null);
    sp.setOpaque(false);
    sp.getViewport().setOpaque(false);
    sp.setAlignmentX(Component.LEFT_ALIGNMENT);
    sp.setPreferredSize(new Dimension(480, Math.min(320, grid.getPreferredSize().height + 10)));
    body.add(sp);
    body.add(err);
    Runnable[] close = new Runnable[1];
    JButton cancel = App.overlayButton("Cancel", false);
    cancel.addActionListener(e -> close[0].run());
    JButton save = App.overlayButton("Save", true);
    save.addActionListener(e -> {
        List<String> picks = new ArrayList<>();
        for (Map.Entry<String, JCheckBox> en : boxes.entrySet()) if (en.getValue().isSelected()) picks.add(en.getKey());
        Map<String, Object> work = App.recCopy(rec);
        ClassFeatures.setChoices(work, a, picks);
        close[0].run();
        onSave.accept(work);
    });
    JPanel foot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    foot.setOpaque(false);
    foot.setAlignmentX(Component.LEFT_ALIGNMENT);
    foot.add(cancel);
    foot.add(save);
    body.add(Box.createRigidArea(new Dimension(0, 8)));
    body.add(foot);
    close[0] = App.showSheetOverlay(anchor, a.f.name, body, null);
}

private static boolean C_EXP(ClassFeatures.Active a) {
    return ClassFeatures.C_EXPERTISE.equals(a.f.choiceKind);
}

// ---------------- features used outside combat ----------------
private static void useOutOfCombat(Component anchor, Map<?, ?> rec, List<ClassFeatures.Active> all, ClassFeatures.Active a, Consumer<Map<String, Object>> onSave) {
    ClassFeatures.Active owner = ClassFeatures.poolOwner(all, a);
    App.SlotState slots = App.recSlotState(rec);
    switch (a.f.restore) {
        case ClassFeatures.RS_PACT: {
            if (slots.pactMax <= 0) {
                App.showInfoOverlay(anchor, a.f.name, "Nothing to recover", "You have no Pact Magic slots.", null);
                return;
            }
            int back = Math.min(slots.pactUsed, (slots.pactMax + 1) / 2);
            App.showConfirmOverlay(anchor, a.f.name, back <= 0 ? "None of your Pact Magic slots are expended. Use it anyway?"
                    : "Regain " + back + " expended Pact Magic slot" + (back == 1 ? "" : "s") + " (at most half your maximum, rounded up)?", "Use", () -> {
                Map<String, Object> work = App.recCopy(rec);
                App.SlotState s = App.recSlotState(work);
                s.pactUsed = Math.max(0, s.pactUsed - back);
                App.writeSlotState(work, s);
                spend(work, owner, a.f.cost);
                onSave.accept(work);
            });
            return;
        }
        case ClassFeatures.RS_SLOTS: {
            int budget = (a.clsLevel + 1) / 2;
            JPanel body = new JPanel();
            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
            body.setOpaque(false);
            JLabel il = new JLabel("<html><div style='width:420px'>Choose expended spell slots to recover: a combined level of up to "
                    + budget + " (half your " + App.escapeHtml(a.cls) + " level, rounded up), none of level 6 or higher.</div></html>");
            il.setForeground(App.TEXT_LIGHT);
            il.setFont(new Font("SansSerif", Font.PLAIN, 13));
            il.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(il);
            body.add(Box.createRigidArea(new Dimension(0, 8)));
            JSpinner[] sps = new JSpinner[5];
            JLabel total = new JLabel();
            total.setFont(new Font("SansSerif", Font.BOLD, 13));
            Runnable sum = () -> {
                int t = 0;
                for (int i = 0; i < 5; i++) if (sps[i] != null) t += (i + 1) * (Integer) sps[i].getValue();
                total.setText("Total: " + t + " of " + budget);
                total.setForeground(t > budget ? App.ACCENT_HOVER : App.GOLD);
            };
            boolean any = false;
            for (int lv = 1; lv <= 5; lv++) {
                int used = slots.used[lv - 1];
                if (used <= 0) continue;
                any = true;
                JSpinner s = AssetLibraryPanel.spinner(0, 0, used);
                s.addChangeListener(e -> sum.run());
                sps[lv - 1] = s;
                JPanel r = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
                r.setOpaque(false);
                r.setAlignmentX(Component.LEFT_ALIGNMENT);
                JLabel l = new JLabel("Level " + lv + " (" + used + " expended)");
                l.setForeground(App.TEXT_LIGHT);
                r.add(l);
                r.add(s);
                body.add(r);
            }
            if (!any) {
                App.showInfoOverlay(anchor, a.f.name, "Nothing to recover", "You have no expended spell slots of level 1 to 5.", null);
                return;
            }
            sum.run();
            total.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(total);
            Runnable[] close = new Runnable[1];
            JButton cancel = App.overlayButton("Cancel", false);
            cancel.addActionListener(e -> close[0].run());
            JButton ok = App.overlayButton("Recover", true);
            ok.addActionListener(e -> {
                int t = 0;
                for (int i = 0; i < 5; i++) if (sps[i] != null) t += (i + 1) * (Integer) sps[i].getValue();
                if (t > budget || t == 0) {
                    total.setText(t == 0 ? "Pick at least one slot." : "Too many: a combined level of " + budget + " at most.");
                    total.setForeground(App.ACCENT_HOVER);
                    return;
                }
                Map<String, Object> work = App.recCopy(rec);
                App.SlotState s = App.recSlotState(work);
                for (int i = 0; i < 5; i++) if (sps[i] != null) s.used[i] = Math.max(0, s.used[i] - (Integer) sps[i].getValue());
                App.writeSlotState(work, s);
                spend(work, owner, a.f.cost);
                close[0].run();
                onSave.accept(work);
            });
            JPanel foot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            foot.setOpaque(false);
            foot.setAlignmentX(Component.LEFT_ALIGNMENT);
            foot.add(cancel);
            foot.add(ok);
            body.add(Box.createRigidArea(new Dimension(0, 8)));
            body.add(foot);
            close[0] = App.showSheetOverlay(anchor, a.f.name, body, null);
            return;
        }
        case ClassFeatures.RS_POOL: {
            ClassFeatures.Active target = ClassFeatures.find(all, a.cls, a.f.restorePool);
            if (target == null) return;
            App.showConfirmOverlay(anchor, a.f.name, "Regain every use of " + target.f.name + "?", "Use", () -> {
                Map<String, Object> work = App.recCopy(rec);
                ClassFeatures.setUsed(work, target.key(), 0);
                spend(work, owner, a.f.cost);
                onSave.accept(work);
            });
            return;
        }
        default:
            App.showConfirmOverlay(anchor, a.f.name, "Use " + a.f.name + " now?", "Use", () -> {
                Map<String, Object> work = App.recCopy(rec);
                spend(work, owner, a.f.cost);
                onSave.accept(work);
            });
    }
}

private static void spend(Map<String, Object> work, ClassFeatures.Active owner, int cost) {
    if (owner == null || cost <= 0) return;
    int used = ClassFeatures.usedMap(work).getOrDefault(owner.key(), 0);
    ClassFeatures.setUsed(work, owner.key(), Math.min(ClassFeatures.maxUses(owner, work), used + cost));
}
}
