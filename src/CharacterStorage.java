import java.util.*;
import java.util.List;

// Saved-character JSON storage: load/save characters.json and build character records.
class CharacterStorage {

static List<Object> loadSavedCharacters() {
    try {
        String content = new String(
                java.nio.file.Files.readAllBytes(DataPaths.userFile("characters.json")),
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
        java.nio.file.Files.write(DataPaths.userFile("characters.json"),
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
    m.put("languages", new ArrayList<Object>(draft.languages));

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
    m.put("token", Tokens.read(draft.token, Tokens.PC_COLOR));
    return m;
}

// Gives every saved character a stable "id" (the Combat map refers to characters by it) and returns the list.
// Records saved before ids existed get one the first time this runs.
static List<Object> loadWithIds() {
    List<Object> all = loadSavedCharacters();
    boolean changed = false;
    for (Object o : all) {
        if (!(o instanceof Map)) continue;
        @SuppressWarnings("unchecked")
        Map<String, Object> rec = (Map<String, Object>) o;
        if (recStr(rec, "id").isEmpty()) {
            rec.put("id", CustomAssets.newId());
            changed = true;
        }
    }
    if (changed) saveAllCharacters(all);
    return all;
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

    // Character-sheet trackers (non-GP coins, conditions, expended spell slots) aren't part of the wizard draft
    if (oldRec.get("coins") != null) newRec.put("coins", oldRec.get("coins"));
    if (oldRec.get("conditions") != null) newRec.put("conditions", oldRec.get("conditions"));
    if (oldRec.get("spellSlotsUsed") != null) newRec.put("spellSlotsUsed", oldRec.get("spellSlotsUsed"));
    if (oldRec.get("pactSlotsUsed") != null) newRec.put("pactSlotsUsed", oldRec.get("pactSlotsUsed"));
    // Stable id used by Combat map tokens; the map token look if the wizard didn't set one
    if (!recStr(oldRec, "id").isEmpty()) newRec.put("id", recStr(oldRec, "id"));
    if (newRec.get("token") == null && oldRec.get("token") != null) newRec.put("token", oldRec.get("token"));
    // Homebrew subclass picks made on level up ({className: subclassName})
    if (oldRec.get("subclasses") instanceof Map) {
        Map<String, Object> subs = new LinkedHashMap<>();
        for (Map.Entry<?, ?> en : ((Map<?, ?>) oldRec.get("subclasses")).entrySet()) {
            String key = String.valueOf(en.getKey());
            if (key.equals(oldClass) && !oldClass.equals(newClass)) continue; // subclass doesn't follow a class change
            subs.put(key, en.getValue());
        }
        newRec.put("subclasses", subs);
    }
    carryOverClassSpells(oldRec, newRec, oldClass, newClass);

    Map<String, Integer> stats = recStatsMap(newRec);
    for (Map.Entry<String, Integer> en : recFeatStatIncreases(oldRec).entrySet()) stats.merge(en.getKey(), en.getValue(), Integer::sum);
    Map<String, Object> statsOut = new LinkedHashMap<>();
    for (String s : GameData.STAT_ORDER) if (stats.containsKey(s)) statsOut.put(s, stats.get(s));
    newRec.put("stats", statsOut);

    refreshDerived(newRec);
}

// ---------------------------------------------------------------
// Per-class spell lists
//   classSpells : {className: {cantrips: [...], prepared: [...], spellbook: [...], arcanum: [...]}}
//   spellbook is Wizard-only; arcanum holds Warlock Mystic Arcanum spells.
// Level-up writes this map. Characters saved before it existed fall back to the level-1 wizard
// picks (cantripsKnown / spellsKnown / classChoices) for their primary class.
// ---------------------------------------------------------------
static final String[] CLASS_SPELL_KEYS = {"cantrips", "prepared", "spellbook", "arcanum"};

static LinkedHashMap<String, Map<String, List<String>>> recClassSpells(Map<?, ?> rec) {
    LinkedHashMap<String, Map<String, List<String>>> out = new LinkedHashMap<>();
    Object v = rec.get("classSpells");
    if (v instanceof Map) {
        for (Map.Entry<?, ?> en : ((Map<?, ?>) v).entrySet()) {
            if (!(en.getValue() instanceof Map)) continue;
            Map<String, List<String>> lists = emptyClassSpells();
            for (String key : CLASS_SPELL_KEYS) lists.put(key, recList((Map<?, ?>) en.getValue(), key));
            out.put(String.valueOf(en.getKey()), lists);
        }
    }
    String primary = recStr(rec, "characterClass");
    if (!primary.isEmpty() && !out.containsKey(primary)) {
        Map<String, List<String>> legacy = legacyClassSpells(rec, primary);
        if (legacy != null) out.put(primary, legacy);
    }
    return out;
}

static Map<String, List<String>> emptyClassSpells() {
    Map<String, List<String>> m = new LinkedHashMap<>();
    for (String key : CLASS_SPELL_KEYS) m.put(key, new ArrayList<>());
    return m;
}

// The primary class's spells as picked in the creation wizard
static Map<String, List<String>> legacyClassSpells(Map<?, ?> rec, String primary) {
    List<String> cantrips = recList(rec, "cantripsKnown");
    List<String> spells = recList(rec, "spellsKnown");
    if (cantrips.isEmpty() && spells.isEmpty()) return null;
    Map<String, List<String>> m = emptyClassSpells();
    m.get("cantrips").addAll(cantrips);
    if (SpellData.isPreparedFromBook(primary)) {
        List<String> book = new ArrayList<>(), prepared = new ArrayList<>();
        for (Map.Entry<String, List<String>> en : recChoicesMap(rec).entrySet()) {
            String label = en.getKey().toLowerCase();
            if (label.startsWith("spells known")) book.addAll(en.getValue());
            else if (label.startsWith("prepared")) prepared.addAll(en.getValue());
        }
        if (book.isEmpty()) book.addAll(spells);
        for (String s : prepared) if (!book.contains(s)) book.add(s);
        if (prepared.isEmpty()) prepared.addAll(book);
        m.get("spellbook").addAll(book);
        m.get("prepared").addAll(prepared);
    } else {
        m.get("prepared").addAll(spells);
    }
    return m;
}

static Map<String, Object> classSpellsToJson(Map<String, Map<String, List<String>>> classSpells) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<String, Map<String, List<String>>> en : classSpells.entrySet()) {
        Map<String, Object> lists = new LinkedHashMap<>();
        for (String key : CLASS_SPELL_KEYS) {
            List<String> l = en.getValue().get(key);
            if (l != null && !l.isEmpty()) lists.put(key, new ArrayList<Object>(l));
        }
        out.put(en.getKey(), lists);
    }
    return out;
}

// Keeps level-up spell picks through a wizard edit. For the primary class, the wizard's new
// level-1 picks replace the old ones, while spells gained (or swapped out) on level-up are kept.
private static void carryOverClassSpells(Map<?, ?> oldRec, Map<String, Object> newRec, String oldClass, String newClass) {
    if (!(oldRec.get("classSpells") instanceof Map)) return;
    LinkedHashMap<String, Map<String, List<String>>> old = recClassSpells(oldRec);
    LinkedHashMap<String, Map<String, List<String>>> out = new LinkedHashMap<>();
    for (Map.Entry<String, Map<String, List<String>>> en : old.entrySet()) {
        if (!en.getKey().equals(oldClass)) out.put(en.getKey(), en.getValue());
    }
    if (oldClass.equals(newClass) && old.containsKey(oldClass)) {
        Map<String, List<String>> oldLists = old.get(oldClass);
        Map<String, List<String>> oldBase = legacyClassSpells(oldRec, oldClass);
        Map<String, List<String>> newBase = legacyClassSpells(newRec, newClass);
        Map<String, List<String>> merged = emptyClassSpells();
        for (String key : CLASS_SPELL_KEYS) {
            List<String> was = oldBase != null ? oldBase.get(key) : Collections.emptyList();
            List<String> now = oldLists.get(key);
            List<String> base = newBase != null ? newBase.get(key) : Collections.emptyList();
            LinkedHashSet<String> result = new LinkedHashSet<>();
            for (String s : base) if (!was.contains(s) || now.contains(s)) result.add(s); // drop swapped-out picks
            for (String s : now) if (!was.contains(s)) result.add(s);                     // keep level-up gains
            merged.get(key).addAll(result);
        }
        out.put(newClass, merged);
    }
    newRec.put("classSpells", classSpellsToJson(out));
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

// Every language the character knows, mapped to where it comes from
static LinkedHashMap<String, String> recLanguageSources(Map<?, ?> rec) {
    LinkedHashMap<String, String> out = new LinkedHashMap<>();
    out.put("Common", "Every character");
    for (String l : recList(rec, "languages")) out.putIfAbsent(l, "Chosen at character creation");
    for (String cls : recClassLevels(rec).keySet()) {
        String granted = GameData.CLASS_LANGUAGES.get(cls);
        if (granted != null) out.putIfAbsent(granted, cls + " class feature");
    }
    String primary = recStr(rec, "characterClass");
    String granted = GameData.CLASS_LANGUAGES.get(primary);
    if (granted != null) out.putIfAbsent(granted, primary + " class feature");
    String species = recStr(rec, "species");
    Map<String, Object> homebrew = CustomAssets.speciesAsset(species);
    if (homebrew != null) {
        for (String l : CustomAssets.speciesLanguageList(homebrew)) out.putIfAbsent(l, species + " species");
    }
    return out;
}

// Where each skill proficiency comes from ("Criminal background", "Fighter class choice")
static LinkedHashMap<String, String> recSkillSources(Map<?, ?> rec) {
    LinkedHashMap<String, String> out = new LinkedHashMap<>();
    String background = recStr(rec, "background");
    for (String s : recList(rec, "backgroundSkills")) {
        out.putIfAbsent(s, (background.isEmpty() ? "Background" : background + " background"));
    }
    String characterClass = recStr(rec, "characterClass");
    List<DecisionItem> decisions = GameData.CLASS_DECISIONS.get(characterClass);
    if (decisions != null) {
        Map<String, List<String>> choices = recChoicesMap(rec);
        for (DecisionItem item : decisions) {
            if (!"SKILLS".equals(item.type)) continue;
            List<String> chosen = choices.get(item.label);
            if (chosen != null) for (String s : chosen) out.putIfAbsent(s, characterClass + " class skill choice");
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
    List<String> languages = new ArrayList<>(); // the two chosen standard languages (Common is implied)
    Map<String, List<String>> classChoices = new LinkedHashMap<>();
    List<String> cantripsKnown = new ArrayList<>();
    List<String> spellsKnown = new ArrayList<>();
    List<String> equipment = new ArrayList<>();
    int gold = 0;
    String asiMode = "";               // "PLUS2PLUS1", "PLUS1X3", or "SKIP"
    Map<String, Integer> asiIncreases = new LinkedHashMap<>(); // stat -> amount applied
    String backgroundPackage = "";     // "A" or "B"
    String classPackage = "";          // "A" or "B"
    Map<String, Object> token = Tokens.defaults(Tokens.PC_COLOR); // map token look {shape, color, image}
}

