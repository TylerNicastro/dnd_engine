import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.List;

// Backup & Restore (Export JSON / Import JSON) for all characters and homebrew, shown as an in-window
// overlay from the main menu.
//  - Browser: Export downloads a file and Import opens the browser's file picker (WebBridge natives).
//  - Desktop: files are read from / written to the app folder through path fields (no file dialogs,
//    per the one-window rule).
// Accepted imports: a full backup {"format":"dnd-engine-backup","characters":{...},"customAssets":{...}},
// a bare characters.json ({"characters":[...]}), or a homebrew pack (CustomAssets.importPack).
class Backup {

static final String FORMAT = "dnd-engine-backup";
static final String KIND_BACKUP = "backup", KIND_CHARACTERS = "characters", KIND_PACK = "pack";
private static final int MAX_BYTES = 8 * 1024 * 1024;
private static final Set<String> APP_FILES = new HashSet<>(Arrays.asList(
        "characters.json", CustomAssets.FILE, "spells.json", "weapons.json"));

static String defaultFileName() {
    return "dnd-engine-backup-" + java.time.LocalDate.now() + ".json";
}

static String exportJson() {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("format", FORMAT);
    root.put("version", 1);
    root.put("exportedAt", java.time.Instant.now().toString());
    Map<String, Object> chars = new LinkedHashMap<>();
    chars.put("characters", CharacterStorage.loadSavedCharacters());
    root.put("characters", chars);
    root.put("customAssets", CustomAssets.saveData());
    return JsonUtil.toPrettyJson(root);
}

// KIND_* for a parsed file, or null if it isn't a D&D Engine file
static String classify(Object parsed) {
    if (!(parsed instanceof Map)) return null;
    Map<?, ?> m = (Map<?, ?>) parsed;
    if (FORMAT.equals(m.get("format"))) return KIND_BACKUP;
    if (m.get("characters") instanceof List) return KIND_CHARACTERS;
    for (Map.Entry<?, ?> en : m.entrySet()) {
        String k = String.valueOf(en.getKey());
        for (Map.Entry<String, String> c : CustomAssets.JSON_KEYS.entrySet()) {
            if ((k.equalsIgnoreCase(c.getValue()) || k.equalsIgnoreCase(c.getKey())) && en.getValue() instanceof List) return KIND_PACK;
        }
    }
    return null;
}

static String describe(String kind) {
    switch (kind) {
        case KIND_BACKUP: return "This replaces ALL characters and homebrew (including tags and content filters) with the ones in the backup.";
        case KIND_CHARACTERS: return "This replaces your characters with the ones in this file. Your homebrew is kept.";
        default: return "This adds the homebrew in this pack to your Asset Library. Assets with the same name are kept and the imported copy is renamed.";
    }
}

// Applies an import; returns a one-line summary. Throws on a write failure.
@SuppressWarnings("unchecked")
static String apply(String json, Object parsed, String kind) throws java.io.IOException {
    Map<?, ?> root = (Map<?, ?>) parsed;
    if (KIND_PACK.equals(kind)) {
        CustomAssets.ImportResult r = CustomAssets.importPack(json, CustomAssets.CONFLICT_KEEP);
        if (r.error != null) throw new java.io.IOException(r.error);
        return r.summary();
    }
    Object chars = KIND_BACKUP.equals(kind) ? root.get("characters") : root;
    int n = 0;
    if (chars instanceof Map && ((Map<?, ?>) chars).get("characters") instanceof List) {
        List<Object> list = new ArrayList<>((List<Object>) ((Map<?, ?>) chars).get("characters"));
        CharacterStorage.saveAllCharacters(list);
        n = list.size();
    }
    String summary = "Restored " + n + (n == 1 ? " character" : " characters");
    if (KIND_BACKUP.equals(kind) && root.get("customAssets") instanceof Map) {
        CustomAssets.replaceAll((Map<?, ?>) root.get("customAssets"));
        summary += " and " + CustomAssets.totalCount() + " homebrew assets";
    }
    return summary + ".";
}

// ===============================================================
// Overlay
// ===============================================================
static void showOverlay(Component anchor) {
    JPanel body = new JPanel();
    body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
    body.setOpaque(false);

    JLabel summary = hint("");
    Runnable refreshSummary = () -> {
        int chars = CharacterStorage.loadSavedCharacters().size(), hb = CustomAssets.totalCount();
        summary.setText(html(chars + (chars == 1 ? " character" : " characters") + " and " + hb + " homebrew assets are saved "
                + (DataPaths.web ? "in this browser only. Export a backup to keep a copy you control, or to move to another browser or device."
                : "in " + DataPaths.userDirectory().toAbsolutePath().normalize() + ".")));
    };
    refreshSummary.run();
    add(body, summary);

    JLabel status = new JLabel(" ");
    status.setFont(new Font("SansSerif", Font.BOLD, 12));
    java.util.function.BiConsumer<String, Boolean> say = (text, ok) -> {
        status.setForeground(ok ? App.GOLD : App.ACCENT_HOVER);
        status.setText(html(text));
    };

    // ---- Export ----
    add(body, heading("Export JSON"));
    add(body, hint(html("One file with every character and all homebrew, including tags and content filters.")));
    if (DataPaths.web) {
        JButton export = App.overlayButton("Export JSON", true);
        export.addActionListener(e -> {
            String name = defaultFileName();
            try {
                WebBridge.downloadFile(name, exportJson());
                say.accept("Downloaded " + name + ". Check your browser's downloads.", true);
            } catch (Throwable ex) {
                say.accept("The browser couldn't start the download (" + ex.getClass().getSimpleName() + ").", false);
            }
        });
        add(body, row(export));
    } else {
        JTextField path = App.overlayField(defaultFileName(), 26);
        JButton export = App.overlayButton("Export JSON", true);
        boolean[] overwriteOk = {false};
        path.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { overwriteOk[0] = false; }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { overwriteOk[0] = false; }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { overwriteOk[0] = false; }
        });
        export.addActionListener(e -> {
            String p = path.getText().trim();
            if (p.isEmpty()) { say.accept("Enter a file name.", false); return; }
            if (!p.toLowerCase().endsWith(".json")) p += ".json";
            java.nio.file.Path target = DataPaths.userFile(p);
            if (APP_FILES.contains(target.getFileName().toString())) {
                say.accept("\"" + target.getFileName() + "\" is one of the app's own data files; pick another name.", false);
                return;
            }
            if (Files.exists(target) && !overwriteOk[0]) {
                overwriteOk[0] = true;
                say.accept("\"" + p + "\" already exists. Click Export JSON again to overwrite it.", false);
                return;
            }
            try {
                Files.write(target, exportJson().getBytes(StandardCharsets.UTF_8));
                say.accept("Saved to " + target.toAbsolutePath().normalize() + ".", true);
            } catch (Exception ex) {
                say.accept("Couldn't write \"" + p + "\" (" + ex.getClass().getSimpleName() + ").", false);
            }
        });
        add(body, row(path, export));
    }

    // ---- Import ----
    add(body, heading("Import JSON"));
    add(body, hint(html("Restore a backup, replace your characters with a characters.json file, or add a homebrew pack. You'll be asked to confirm first.")));
    java.util.function.Consumer<String> handle = new java.util.function.Consumer<String>() {
        public void accept(String json) {
            if (json == null) { say.accept("The browser couldn't open a file.", false); return; }
            if (json.isEmpty()) { say.accept("No file chosen.", false); return; }
            if (WebBridge.TOO_LARGE.equals(json) || json.length() > MAX_BYTES) { say.accept("That file is larger than 8 MB.", false); return; }
            Object parsed;
            try {
                parsed = new MiniJsonParser(json.trim()).parse();
            } catch (Exception ex) {
                say.accept("That file isn't valid JSON.", false);
                return;
            }
            String kind = classify(parsed);
            if (kind == null) {
                say.accept("Not a D&D Engine file. Choose a backup, a characters.json file, or a homebrew pack.", false);
                return;
            }
            say.accept(" ", true);
            String title = KIND_BACKUP.equals(kind) ? "Restore Backup" : KIND_CHARACTERS.equals(kind) ? "Replace Characters" : "Add Homebrew Pack";
            String tip = KIND_PACK.equals(kind) ? "" : "\n\nTip: export a backup first if you might want your current data back.";
            App.showConfirmOverlay(status, title, describe(kind) + tip, KIND_PACK.equals(kind) ? "Add Homebrew" : "Replace", () -> {
                try {
                    say.accept(apply(json, parsed, kind), true);
                    refreshSummary.run();
                } catch (Exception ex) {
                    say.accept("Import failed: " + ex.getMessage(), false);
                }
            });
        }
    };
    if (DataPaths.web) {
        JButton pick = App.overlayButton("Choose File & Import…", false);
        pick.addActionListener(e -> {
            say.accept("Choose a .json file in your browser's file picker…", true);
            WebBridge.pickTextFileAsync(handle);
        });
        add(body, row(pick));
    } else {
        JTextField path = App.overlayField("", 26);
        JButton imp = App.overlayButton("Import JSON", false);
        imp.addActionListener(e -> {
            String p = path.getText().trim();
            if (p.isEmpty()) { say.accept("Pick or type a file name.", false); return; }
            try {
                java.nio.file.Path src = DataPaths.userFile(p);
                if (Files.size(src) > MAX_BYTES) { say.accept("That file is larger than 8 MB.", false); return; }
                handle.accept(new String(Files.readAllBytes(src), StandardCharsets.UTF_8));
            } catch (Exception ex) {
                say.accept("Couldn't read \"" + p + "\" (" + ex.getClass().getSimpleName() + ").", false);
            }
        });
        add(body, row(path, imp));
        List<String> files = new ArrayList<>();
        try (java.nio.file.DirectoryStream<java.nio.file.Path> ds = Files.newDirectoryStream(DataPaths.userDirectory(), "*.json")) {
            for (java.nio.file.Path f : ds) if (!APP_FILES.contains(f.getFileName().toString())) files.add(f.getFileName().toString());
        } catch (Exception ignored) {
            // folder unreadable: the path field still works
        }
        Collections.sort(files);
        if (!files.isEmpty()) {
            add(body, hint("JSON files in the app folder (click to pick):"));
            for (String n : files) {
                JLabel l = new JLabel("• " + n);
                l.setForeground(App.GOLD);
                l.setFont(new Font("SansSerif", Font.PLAIN, 13));
                l.setBorder(new EmptyBorder(2, 6, 2, 6));
                App.clickable(l, () -> path.setText(n));
                add(body, l);
            }
        }
    }
    body.add(Box.createRigidArea(new Dimension(0, 10)));
    add(body, status);

    JPanel wrap = new JPanel(new BorderLayout(0, 12));
    wrap.setOpaque(false);
    wrap.add(body, BorderLayout.CENTER);
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    buttons.setOpaque(false);
    JButton done = App.overlayButton("Done", true);
    buttons.add(done);
    wrap.add(buttons, BorderLayout.SOUTH);
    Runnable close = App.showSheetOverlay(anchor, "Backup & Restore", wrap, done);
    done.addActionListener(e -> close.run());
}

private static String html(String text) {
    return "<html><div style='width:400px'>" + App.escapeHtml(text) + "</div></html>";
}

private static JLabel heading(String t) {
    JLabel l = new JLabel(t.toUpperCase());
    l.setForeground(App.GOLD);
    l.setFont(new Font("SansSerif", Font.BOLD, 12));
    l.setBorder(BorderFactory.createCompoundBorder(new EmptyBorder(14, 0, 4, 0),
            BorderFactory.createMatteBorder(0, 0, 1, 0, App.SHEET_BORDER)));
    return l;
}

private static JLabel hint(String t) {
    JLabel l = new JLabel(t);
    l.setForeground(App.TEXT_MUTED);
    l.setFont(new Font("SansSerif", Font.PLAIN, 12));
    l.setBorder(new EmptyBorder(4, 0, 6, 0));
    return l;
}

private static JPanel row(JComponent... cs) {
    JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    p.setOpaque(false);
    for (JComponent c : cs) p.add(c);
    return p;
}

private static void add(JPanel body, JComponent c) {
    c.setAlignmentX(Component.LEFT_ALIGNMENT);
    body.add(c);
}
}
