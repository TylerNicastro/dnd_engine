import java.util.*;
import java.util.List;

// Spell data, loaded from spells.json
class SpellData {

// Loaded from spells.json at startup
static SpellDatabase SPELL_DB = new SpellDatabase();

static SpellDatabase loadSpellDatabase() {
    SpellDatabase db = new SpellDatabase();
    try {
        String content = new String(
                java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("spells.json")),
                java.nio.charset.StandardCharsets.UTF_8);
        Object parsed = new MiniJsonParser(content).parse();
        if (parsed instanceof Map) {
            Map<?, ?> root = (Map<?, ?>) parsed;
            db.cantrips = parseSpellList(root.get("cantrips"));
            db.level1 = parseSpellList(root.get("level1"));
        }
    } catch (Exception ex) {
        // spells.json missing or unreadable; app continues with empty spell lists
    }
    return db;
}

static List<SpellEntry> parseSpellList(Object arr) {
    List<SpellEntry> list = new ArrayList<>();
    if (!(arr instanceof List)) return list;
    for (Object o : (List<?>) arr) {
        if (!(o instanceof Map)) continue;
        Map<?, ?> m = (Map<?, ?>) o;
        SpellEntry se = new SpellEntry();
        se.name = String.valueOf(m.get("name"));
        se.school = String.valueOf(m.get("school"));
        se.castingTime = String.valueOf(m.get("castingTime"));
        se.level = JsonUtil.toInt(m.get("level"));
        se.range = JsonUtil.toInt(m.get("range"));
        se.duration = JsonUtil.toInt(m.get("duration"));
        se.concentration = Boolean.TRUE.equals(m.get("concentration"));
        se.components = new ArrayList<>();
        Object comp = m.get("components");
        if (comp instanceof List) {
            for (Object c : (List<?>) comp) se.components.add(String.valueOf(c));
        }
        se.classes = new ArrayList<>();
        Object cls = m.get("classes");
        if (cls instanceof List) {
            for (Object c : (List<?>) cls) se.classes.add(String.valueOf(c));
        }
        list.add(se);
    }
    return list;
}



static String formatSpellRange(int r) {
    if (r == 0) return "Self / Touch";
    if (r == -1) return "Unlimited";
    if (r == -2) return "Special";
    return r + " feet";
}

static String formatSpellDuration(int minutes, boolean concentration) {
    String base;
    if (minutes == 0) {
        base = "Instantaneous";
    } else if (minutes % 1440 == 0) {
        int days = minutes / 1440;
        base = days + (days == 1 ? " day" : " days");
    } else if (minutes % 60 == 0) {
        int hours = minutes / 60;
        base = hours + (hours == 1 ? " hour" : " hours");
    } else {
        base = minutes + (minutes == 1 ? " minute" : " minutes");
    }
    return concentration ? "Concentration, up to " + base : base;
}


}

class SpellEntry {
    String name, school, castingTime;
    int level;
    int range;
    List<String> components = new ArrayList<>();
    int duration;
    boolean concentration;
    List<String> classes = new ArrayList<>();
}


class SpellDatabase {
    List<SpellEntry> cantrips = new ArrayList<>();
    List<SpellEntry> level1 = new ArrayList<>();
}

