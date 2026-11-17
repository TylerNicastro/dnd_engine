import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

// Web-only hand-off between the page (web/index.html) and the app.
//
// Import: when the user picks a backup file, the page validates it, keeps it in sessionStorage and
// reloads. On the next start it writes it to /str/dnd_import.json (JS-writable, Java-readable) before
// launching the jar; applyPendingImport() then copies it into /files/ before any data is loaded.
// Export needs no Java: the page reads /files/characters.json and /files/custom_assets.json directly.
//
// Accepted formats: a full backup {"format":"dnd-engine-backup","characters":{...},"customAssets":{...}},
// a bare characters.json ({"characters":[...]}), or a homebrew pack (anything else with asset lists).
class WebBridge {

static final String PENDING_IMPORT = "/str/dnd_import.json";
static final String BACKUP_FORMAT = "dnd-engine-backup";

private static String pendingPack;

// Call after CustomAssets.load(): merges an imported homebrew pack (name clashes are kept, renamed)
static void applyPendingPack() {
    if (pendingPack == null) return;
    CustomAssets.importPack(pendingPack, CustomAssets.CONFLICT_KEEP);
    pendingPack = null;
}

static void applyPendingImport() {
    Path p = Paths.get(PENDING_IMPORT);
    try {
        if (!Files.exists(p)) return;
        String json = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        Object parsed = new MiniJsonParser(json.trim()).parse();
        if (!(parsed instanceof Map)) return;
        Map<?, ?> root = (Map<?, ?>) parsed;
        Object characters = null, assets = null;
        if (BACKUP_FORMAT.equals(root.get("format"))) {
            characters = root.get("characters");
            assets = root.get("customAssets");
        } else if (root.get("characters") instanceof List) {
            characters = root;
        } else {
            pendingPack = json; // a homebrew pack merges into existing homebrew once it's loaded
        }
        if (characters instanceof Map && ((Map<?, ?>) characters).get("characters") instanceof List) {
            Files.write(DataPaths.userFile("characters.json"), JsonUtil.toJson(characters).getBytes(StandardCharsets.UTF_8));
        }
        if (assets instanceof Map) {
            Files.write(DataPaths.userFile(CustomAssets.FILE), JsonUtil.toPrettyJson(assets).getBytes(StandardCharsets.UTF_8));
        }
    } catch (Exception ex) {
        // A bad backup must never stop the app from starting; the page validated it before reloading
    }
}
}
