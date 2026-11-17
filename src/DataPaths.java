import java.nio.file.Path;
import java.nio.file.Paths;

// Where the app reads its bundled data and writes user data.
//
// Desktop (no arguments): both are the working directory (characters.json, custom_assets.json,
//   weapons.json, spells.json next to the app). If spells.json isn't there, the jar's own folder is used.
// Web (CheerpJ, started by web/index.html with "--web --data=/app/<site path>/"):
//   - bundled read-only data (weapons.json, spells.json, spell art) comes from the site folder under /app/
//   - user data (characters.json, custom_assets.json, exported packs) goes to /files/, which CheerpJ
//     persists in the browser's IndexedDB, so saves survive page reloads on that browser only.
class DataPaths {

static boolean web = false;
private static String dataDir = "";
private static String userDir = "";

static void init(String[] args) {
    if (args == null) return;
    for (String a : args) {
        if ("--web".equals(a)) {
            web = true;
            userDir = "/files/";
        } else if (a.startsWith("--data=")) {
            dataDir = withSlash(a.substring("--data=".length()));
        } else if (a.startsWith("--user-data=")) {
            userDir = withSlash(a.substring("--user-data=".length()));
        }
    }
    if (web && dataDir.isEmpty()) dataDir = "/app/";
    // Desktop jar started from another folder (e.g. double-clicked on macOS / Linux): use the jar's folder
    if (!web && dataDir.isEmpty() && userDir.isEmpty() && !java.nio.file.Files.exists(Paths.get("spells.json"))) {
        Path dir = appDirectory();
        if (dir != null && java.nio.file.Files.exists(dir.resolve("spells.json"))) {
            dataDir = userDir = withSlash(dir.toAbsolutePath().toString().replace('\\', '/'));
        }
    }
}

// Folder holding the running jar (or the folder above bin/ when run from classes); null if unknown
private static Path appDirectory() {
    try {
        Path p = Paths.get(DataPaths.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return p.getParent();
    } catch (Exception ex) {
        return null;
    }
}

private static String withSlash(String d) {
    return d.isEmpty() || d.endsWith("/") ? d : d + "/";
}

// Bundled read-only data file (weapons.json, spells.json, art/...)
static Path resource(String name) {
    return isAbsolute(name) ? Paths.get(name) : Paths.get(dataDir + name);
}

// User-writable file (characters.json, custom_assets.json, exported packs)
static Path userFile(String name) {
    return isAbsolute(name) ? Paths.get(name) : Paths.get(userDir + name);
}

static Path userDirectory() {
    return Paths.get(userDir.isEmpty() ? "." : userDir);
}

private static boolean isAbsolute(String name) {
    return name.startsWith("/") || name.matches("^[A-Za-z]:[\\\\/].*");
}
}
