import java.util.*;

// Built-in beasts (beasts.json): read-only NPC assets from the SRD 5.2 (2024) and, for five 2014 names, the SRD 5.1.
// Each entry has the same fields as a homebrew NPC (CustomAssets.SCHEMAS NPCs) plus "builtin": true, and a stable
// id "srd-<name>" that map tokens and Wild Shape forms refer to. CustomAssets.find(NPCS, id) falls back to these,
// the Asset Library lists them as official NPCs (Copy to Homebrew makes an editable copy), and Combat / map pins
// can place them. Wild Shape forms come from here (and from homebrew Beast NPCs): see ClassFeatures.C_FORM.
class BeastData {

private static List<Map<String, Object>> beasts;
private static final Map<String, Map<String, Object>> BY_ID = new LinkedHashMap<>();

static synchronized List<Map<String, Object>> all() {
    if (beasts == null) load();
    return beasts;
}

private static void load() {
    beasts = new ArrayList<>();
    BY_ID.clear();
    try {
        String content = new String(java.nio.file.Files.readAllBytes(DataPaths.resource("beasts.json")), java.nio.charset.StandardCharsets.UTF_8);
        Object parsed = new MiniJsonParser(content).parse();
        if (parsed instanceof Map && ((Map<?, ?>) parsed).get("beasts") instanceof List) {
            for (Object o : (List<?>) ((Map<?, ?>) parsed).get("beasts")) {
                if (!(o instanceof Map)) continue;
                Map<String, Object> a = new LinkedHashMap<>();
                for (Map.Entry<?, ?> en : ((Map<?, ?>) o).entrySet()) a.put(String.valueOf(en.getKey()), en.getValue());
                a.put("builtin", true);
                beasts.add(a);
                BY_ID.put(CustomAssets.str(a, "id"), a);
            }
        }
    } catch (Exception ex) {
        // beasts.json missing or unreadable: no built-in beasts
    }
    beasts = Collections.unmodifiableList(beasts);
}

static Map<String, Object> byId(String id) {
    all();
    return id == null ? null : BY_ID.get(id);
}

static Map<String, Object> byName(String name) {
    for (Map<String, Object> b : all()) if (CustomAssets.str(b, "name").equalsIgnoreCase(name)) return b;
    return null;
}

static List<String> names() {
    List<String> out = new ArrayList<>();
    for (Map<String, Object> b : all()) out.add(CustomAssets.str(b, "name"));
    return out;
}

static boolean isBuiltin(Map<?, ?> npc) {
    return npc != null && Boolean.TRUE.equals(npc.get("builtin"));
}

// Challenge rating as a number ("1/4" -> 0.25)
static double crValue(String cr) {
    if (cr == null || cr.trim().isEmpty()) return 0;
    String c = cr.trim();
    try {
        if (c.contains("/")) {
            String[] p = c.split("/");
            return Double.parseDouble(p[0].trim()) / Double.parseDouble(p[1].trim());
        }
        return Double.parseDouble(c);
    } catch (NumberFormatException ex) {
        return 0;
    }
}

static boolean flies(Map<?, ?> npc) {
    return CustomAssets.str(npc, "speed").toLowerCase().contains("fly");
}

// "Wolf · CR 1/4 · AC 12 · 11 HP · 40 ft." for pickers
static String shortLine(Map<?, ?> npc) {
    return CustomAssets.str(npc, "name") + " · CR " + CustomAssets.str(npc, "cr") + " · AC " + CustomAssets.intVal(npc, "ac", 10)
            + " · " + CustomAssets.str(npc, "hp").replaceAll("\\s*\\(.*\\)", "") + " HP · " + CustomAssets.str(npc, "speed");
}
}
