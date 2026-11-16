import java.util.*;
import java.util.List;

// Saved-character JSON storage: load/save characters.json and build character records.
class CharacterStorage {

static List<Object> loadSavedCharacters() {
    try {
        String content = new String(
                java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("characters.json")),
                java.nio.charset.StandardCharsets.UTF_8);
        Object parsed = new MiniJsonParser(content).parse();
        if (parsed instanceof Map) {
            Object arr = ((Map<?, ?>) parsed).get("characters");
            if (arr instanceof List) return new ArrayList<>((List<?>) arr);
        }
    } catch (Exception ex) {
        // characters.json missing or unreadable; treat as no saved characters
    }
    return new ArrayList<>();
}

static void saveAllCharacters(List<Object> characters) {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("characters", characters);
    try {
        java.nio.file.Files.write(java.nio.file.Paths.get("characters.json"),
                JsonUtil.toJson(root).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    } catch (Exception ex) {
        // best-effort save; ignore failures (e.g. read-only filesystem)
    }
}

static Map<String, Object> buildCharacterRecord(CharacterDraft draft) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("name", draft.name);
    m.put("species", draft.species);
    m.put("lineage", draft.lineage);
    m.put("characterClass", draft.characterClass);
    m.put("background", draft.background);
    m.put("backgroundSkills", new ArrayList<Object>(draft.backgroundSkills));

    Map<String, Object> statsMap = new LinkedHashMap<>();
    for (String s : GameData.STAT_ORDER) {
        Integer v = draft.stats.get(s);
        if (v != null) statsMap.put(s, v);
    }
    m.put("stats", statsMap);

    int hd = GameData.hitDieMax(draft.characterClass);
    Integer con = draft.stats.get("CON");
    m.put("hp", (hd > 0 && con != null) ? (Integer) (hd + GameData.abilityModifier(con)) : null);

    boolean hasSpecies = !draft.species.isEmpty();
    m.put("speed", hasSpecies ? GameData.computeSpeed(draft.species, draft.lineage) : null);
    m.put("size", hasSpecies ? GameData.computeSize(draft.species) : null);
    m.put("creatureType", hasSpecies ? GameData.computeCreatureType(draft.species) : null);
    m.put("climbSpeed", hasSpecies ? GameData.computeClimbSpeed(draft.species) : null);

    m.put("spellSlots", GameData.SPELL_SLOTS_LEVEL1.get(draft.characterClass));
    m.put("cantripsKnown", new ArrayList<Object>(draft.cantripsKnown));
    m.put("spellsKnown", new ArrayList<Object>(draft.spellsKnown));
    m.put("gold", draft.gold);
    m.put("equipment", new ArrayList<Object>(draft.equipment));

    Map<String, Object> choicesMap = new LinkedHashMap<>();
    for (Map.Entry<String, List<String>> en : draft.classChoices.entrySet()) {
        choicesMap.put(en.getKey(), new ArrayList<Object>(en.getValue()));
    }
    m.put("classChoices", choicesMap);

    m.put("asiMode", draft.asiMode);
    Map<String, Object> asiMap = new LinkedHashMap<>();
    for (Map.Entry<String, Integer> en : draft.asiIncreases.entrySet()) asiMap.put(en.getKey(), en.getValue());
    m.put("asiIncreases", asiMap);
    m.put("backgroundPackage", draft.backgroundPackage);
    m.put("classPackage", draft.classPackage);

    m.put("level", 1);
    Map<String, Object> classLevels = new LinkedHashMap<>();
    if (!draft.characterClass.isEmpty()) classLevels.put(draft.characterClass, 1);
    m.put("classLevels", classLevels);
    m.put("hitDice", FeatData.hitDiceString(recClassLevels(m)));
    m.put("hpRolls", new ArrayList<Object>());
    m.put("feats", new ArrayList<Object>());
    return m;
}

// ---------------------------------------------------------------
// Level-up progression
//   classLevels : {className: levels}   (primary class = characterClass)
//   hpRolls     : raw hit die results for every level after 1 (CON is added at compute time)
//   feats       : [{name, category, className, classLevel, characterLevel, description, statIncreases}]
//   stats       : final scores, INCLUDING feat increases
// ---------------------------------------------------------------

static int recLevel(Map<?, ?> rec) {
    Integer lv = recInt(rec, "level");
    return lv != null && lv > 0 ? lv : 1;
}

static LinkedHashMap<String, Integer> recClassLevels(Map<?, ?> rec) {
    LinkedHashMap<String, Integer> out = new LinkedHashMap<>();
    Object v = rec.get("classLevels");
    if (v instanceof Map) {
        for (Map.Entry<?, ?> en : ((Map<?, ?>) v).entrySet()) {
            if (en.getValue() instanceof Number) out.put(String.valueOf(en.getKey()), ((Number) en.getValue()).intValue());
        }
    }
    if (out.isEmpty()) {
        // Legacy record (pre level-up support): everything is in the primary class
        String cls = recStr(rec, "characterClass");
        if (!cls.isEmpty()) out.put(cls, recLevel(rec));
    }
    return out;
}

static List<Integer> recHpRolls(Map<?, ?> rec) {
    List<Integer> out = new ArrayList<>();
    Object v = rec.get("hpRolls");
    if (v instanceof List) {
        for (Object o : (List<?>) v) if (o instanceof Number) out.add(((Number) o).intValue());
    }
    return out;
}

static List<Map<String, Object>> recFeats(Map<?, ?> rec) {
    List<Map<String, Object>> out = new ArrayList<>();
    Object v = rec.get("feats");
    if (v instanceof List) {
        for (Object o : (List<?>) v) {
            if (o instanceof Map) {
                Map<String, Object> copy = new LinkedHashMap<>();
                for (Map.Entry<?, ?> en : ((Map<?, ?>) o).entrySet()) copy.put(String.valueOf(en.getKey()), en.getValue());
                out.add(copy);
            }
        }
    }
    return out;
}

static Map<String, Integer> featStatIncreases(Map<String, Object> feat) {
    Map<String, Integer> out = new LinkedHashMap<>();
    Object v = feat.get("statIncreases");
    if (v instanceof Map) {
        for (Map.Entry<?, ?> en : ((Map<?, ?>) v).entrySet()) {
            if (en.getValue() instanceof Number) out.put(String.valueOf(en.getKey()), ((Number) en.getValue()).intValue());
        }
    }
    return out;
}

// Sum of every stat increase granted by level-up feats
static Map<String, Integer> recFeatStatIncreases(Map<?, ?> rec) {
    Map<String, Integer> out = new LinkedHashMap<>();
    for (Map<String, Object> feat : recFeats(rec)) {
        for (Map.Entry<String, Integer> en : featStatIncreases(feat).entrySet()) out.merge(en.getKey(), en.getValue(), Integer::sum);
    }
    return out;
}

// HP gained for one level after 1st: die result + CON modifier, minimum 1
static int hpGainForRoll(int roll, int conMod) {
    return Math.max(1, roll + conMod);
}

// Max HP = (primary hit die max + CON) at level 1 + (roll + CON, min 1) for each later level.
// CON is applied retroactively, so a CON increase from a feat raises HP for every level.
static Integer computeMaxHp(String primaryClass, List<Integer> hpRolls, int con) {
    int conMod = GameData.abilityModifier(con);
    int hd = GameData.hitDieMax(primaryClass);
    if (hd <= 0 && hpRolls.isEmpty()) return null;
    int total = hd > 0 ? Math.max(1, hd + conMod) : 0;
    for (int r : hpRolls) total += hpGainForRoll(r, conMod);
    return total;
}

// Recomputes the derived fields (hp, hitDice, level) from the progression data in the record
static void refreshDerived(Map<String, Object> rec) {
    LinkedHashMap<String, Integer> classLevels = recClassLevels(rec);
    int total = 0;
    for (int v : classLevels.values()) total += v;
    if (total > 0) rec.put("level", total);
    rec.put("hitDice", FeatData.hitDiceString(classLevels));
    Integer con = recStatsMap(rec).get("CON");
    if (con != null) rec.put("hp", computeMaxHp(recStr(rec, "characterClass"), recHpRolls(rec), con));
}

// When a leveled character is re-saved through the edit wizard (which only knows about
// level-1 data), keep its level-up progression and re-apply feat stat increases on top.
static void carryOverProgression(Map<?, ?> oldRec, Map<String, Object> newRec) {
    if (oldRec == null) return;
    String oldClass = recStr(oldRec, "characterClass");
    String newClass = recStr(newRec, "characterClass");

    LinkedHashMap<String, Integer> classLevels = new LinkedHashMap<>();
    for (Map.Entry<String, Integer> en : recClassLevels(oldRec).entrySet()) {
        String key = en.getKey().equals(oldClass) ? newClass : en.getKey();
        if (!key.isEmpty()) classLevels.merge(key, en.getValue(), Integer::sum);
    }
    if (classLevels.isEmpty() && !newClass.isEmpty()) classLevels.put(newClass, 1);
    // Keep the primary class first
    LinkedHashMap<String, Object> ordered = new LinkedHashMap<>();
    if (classLevels.containsKey(newClass)) ordered.put(newClass, classLevels.get(newClass));
    for (Map.Entry<String, Integer> en : classLevels.entrySet()) ordered.putIfAbsent(en.getKey(), en.getValue());
    newRec.put("classLevels", ordered);

    newRec.put("hpRolls", new ArrayList<Object>(recHpRolls(oldRec)));

    newRec.put("feats", new ArrayList<Object>(recFeats(oldRec)));

    Map<String, Integer> stats = recStatsMap(newRec);
    for (Map.Entry<String, Integer> en : recFeatStatIncreases(oldRec).entrySet()) stats.merge(en.getKey(), en.getValue(), Integer::sum);
    Map<String, Object> statsOut = new LinkedHashMap<>();
    for (String s : GameData.STAT_ORDER) if (stats.containsKey(s)) statsOut.put(s, stats.get(s));
    newRec.put("stats", statsOut);

    refreshDerived(newRec);
}

static void saveNewCharacter(CharacterDraft draft) {
    List<Object> characters = loadSavedCharacters();
    characters.add(buildCharacterRecord(draft));
    saveAllCharacters(characters);
}

// Helpers for reading a saved character record (as parsed from JSON) back into edit state
static String recStr(Map<?, ?> rec, String key) {
    Object v = rec.get(key);
    return (v == null || "null".equals(String.valueOf(v))) ? "" : String.valueOf(v);
}

static List<String> recList(Map<?, ?> rec, String key) {
    List<String> out = new ArrayList<>();
    Object v = rec.get(key);
    if (v instanceof List) {
        for (Object o : (List<?>) v) out.add(String.valueOf(o));
    }
    return out;
}

static Integer recInt(Map<?, ?> rec, String key) {
    Object v = rec.get(key);
    return (v instanceof Number) ? ((Number) v).intValue() : null;
}

static Map<String, Integer> recStatsMap(Map<?, ?> rec) {
    Map<String, Integer> out = new LinkedHashMap<>();
    Object v = rec.get("stats");
    if (v instanceof Map) {
        for (Map.Entry<?, ?> en : ((Map<?, ?>) v).entrySet()) {
            if (en.getValue() instanceof Number) out.put(String.valueOf(en.getKey()), ((Number) en.getValue()).intValue());
        }
    }
    return out;
}

static Map<String, List<String>> recChoicesMap(Map<?, ?> rec) {
    Map<String, List<String>> out = new LinkedHashMap<>();
    Object v = rec.get("classChoices");
    if (v instanceof Map) {
        for (Map.Entry<?, ?> en : ((Map<?, ?>) v).entrySet()) {
            List<String> vals = new ArrayList<>();
            if (en.getValue() instanceof List) {
                for (Object o : (List<?>) en.getValue()) vals.add(String.valueOf(o));
            }
            out.put(String.valueOf(en.getKey()), vals);
        }
    }
    return out;
}

static Set<String> recSkillProficiencies(Map<?, ?> rec) {
    Set<String> profs = new LinkedHashSet<>(recList(rec, "backgroundSkills"));
    String characterClass = recStr(rec, "characterClass");
    List<DecisionItem> decisions = GameData.CLASS_DECISIONS.get(characterClass);
    if (decisions != null) {
        Map<String, List<String>> choices = recChoicesMap(rec);
        for (DecisionItem item : decisions) {
            if ("SKILLS".equals(item.type)) {
                List<String> chosen = choices.get(item.label);
                if (chosen != null) profs.addAll(chosen);
            }
        }
    }
    return profs;
}

static Map<String, Integer> recAsiIncreases(Map<?, ?> rec) {
    Map<String, Integer> out = new LinkedHashMap<>();
    Object v = rec.get("asiIncreases");
    if (v instanceof Map) {
        for (Map.Entry<?, ?> en : ((Map<?, ?>) v).entrySet()) {
            if (en.getValue() instanceof Number) out.put(String.valueOf(en.getKey()), ((Number) en.getValue()).intValue());
        }
    }
    return out;
}

}

class CharacterDraft {
    String name = "";
    String species = "";
    String lineage = "";
    String characterClass = "";
    String background = "";
    Map<String, Integer> stats = new LinkedHashMap<>();
    List<String> backgroundSkills = new ArrayList<>();
    Map<String, List<String>> classChoices = new LinkedHashMap<>();
    List<String> cantripsKnown = new ArrayList<>();
    List<String> spellsKnown = new ArrayList<>();
    List<String> equipment = new ArrayList<>();
    int gold = 0;
    String asiMode = "";               // "PLUS2PLUS1", "PLUS1X3", or "SKIP"
    Map<String, Integer> asiIncreases = new LinkedHashMap<>(); // stat -> amount applied
    String backgroundPackage = "";     // "A" or "B"
    String classPackage = "";          // "A" or "B"
}

