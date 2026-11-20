import java.util.*;
import java.util.List;

// Debug / testing: ready-made characters added from the Characters screen ("+ Add Test Characters").
// "Test Archmage" is a level 20 Wizard with every spell in SpellData (built-in + homebrew) prepared, so any
// spell can be tried in combat. Characters already in the list (matched by name) are skipped.
class TestCharacters {

static final String[] NAMES = {"Test Archmage", "Test Fighter", "Test Cleric", "Test Rogue", "Test Barbarian"};

static void addMissing() {
    List<Object> all = CharacterStorage.loadSavedCharacters();
    Set<String> existing = new HashSet<>();
    for (Object o : all) if (o instanceof Map) existing.add(CharacterStorage.recStr((Map<?, ?>) o, "name"));
    for (Map<String, Object> rec : build()) {
        if (existing.contains(CharacterStorage.recStr(rec, "name"))) continue;
        all.add(rec);
    }
    CharacterStorage.saveAllCharacters(all);
}

private static List<Map<String, Object>> build() {
    List<Map<String, Object>> out = new ArrayList<>();

    // Every spell: all cantrips + all levelled spells, prepared and in the spellbook
    List<String> cantrips = new ArrayList<>(), leveled = new ArrayList<>();
    List<Spell> spells = new ArrayList<>(SpellData.SPELLS.values());
    spells.sort(Comparator.comparingInt((Spell s) -> s.level).thenComparing(s -> s.name));
    for (Spell s : spells) (s.level == 0 ? cantrips : leveled).add(s.name);
    Map<String, Object> archmage = base("Test Archmage", "Human", "", "Sage", "Wizard", 20,
            stats(10, 16, 16, 20, 14, 12), 4,
            Arrays.asList("Arcana", "History"), "Skills? (2)", Arrays.asList("Investigation", "Medicine"),
            Arrays.asList("Quarterstaff", "Dagger (2)", "Spellbook", "Arcane focus", "Robe", "Backpack"));
    archmage.put("classSpells", classSpells("Wizard", cantrips, leveled, leveled));
    out.add(archmage);

    Map<String, Object> fighter = base("Test Fighter", "Human", "", "Soldier", "Fighter", 5,
            stats(18, 14, 16, 10, 12, 8), 6,
            Arrays.asList("Athletics", "Intimidation"), "Which skills? (2)", Arrays.asList("Perception", "Survival"),
            Arrays.asList("Longsword", "Shield", "Chain mail", "Longbow", "Arrow (20)", "Quiver", "Handaxe (2)", "Backpack"));
    choices(fighter).put("Fighting Style?", new ArrayList<Object>(Collections.singletonList("Defense")));
    out.add(fighter);

    Map<String, Object> cleric = base("Test Cleric", "Dwarf", "", "Acolyte", "Cleric", 3,
            stats(14, 10, 14, 10, 17, 12), 5,
            Arrays.asList("Insight", "Religion"), "Which skills? (2)", Arrays.asList("Medicine", "History"),
            Arrays.asList("Mace", "Shield", "Chain mail", "Holy symbol", "Healer's kit", "Backpack"));
    choices(cleric).put("Divine Order?", new ArrayList<Object>(Collections.singletonList("Protector")));
    cleric.put("classSpells", classSpells("Cleric",
            existing("Sacred Flame", "Guidance", "Toll the Dead"),
            existing("Cure Wounds", "Bless", "Healing Word", "Guiding Bolt", "Shield of Faith", "Inflict Wounds"),
            new ArrayList<>()));
    out.add(cleric);

    Map<String, Object> rogue = base("Test Rogue", "Halfling", "", "Criminal", "Rogue", 3,
            stats(10, 17, 14, 12, 12, 14), 4,
            Arrays.asList("Sleight of Hand", "Stealth"), "Skills? (4)", Arrays.asList("Acrobatics", "Deception", "Perception", "Investigation"),
            Arrays.asList("Shortsword", "Dagger (2)", "Shortbow", "Arrow (20)", "Quiver", "Leather armor", "Thieves' tools", "Backpack"));
    out.add(rogue);

    Map<String, Object> barbarian = base("Test Barbarian", "Orc", "", "Soldier", "Barbarian", 1,
            stats(17, 14, 16, 8, 12, 10), 0,
            Arrays.asList("Athletics", "Intimidation"), "Which skills? (2)", Arrays.asList("Perception", "Survival"),
            Arrays.asList("Greataxe", "Handaxe (4)", "Javelin (4)", "Backpack"));
    out.add(barbarian);

    return out;
}

// hpRoll: the hit die result used for every level after 1 (0 for a level 1 character)
private static Map<String, Object> base(String name, String species, String lineage, String background, String cls, int level,
                                        Map<String, Object> stats, int hpRoll, List<String> bgSkills,
                                        String skillLabel, List<String> classSkills, List<String> equipment) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("name", name);
    m.put("species", species);
    m.put("lineage", lineage);
    m.put("characterClass", cls);
    m.put("background", background);
    m.put("backgroundSkills", new ArrayList<Object>(bgSkills));
    m.put("languages", new ArrayList<Object>(Arrays.asList("Common")));
    m.put("stats", stats);
    m.put("speed", GameData.computeSpeed(species, lineage));
    m.put("size", GameData.computeSize(species));
    m.put("creatureType", GameData.computeCreatureType(species));
    m.put("climbSpeed", GameData.computeClimbSpeed(species));
    m.put("cantripsKnown", new ArrayList<Object>());
    m.put("spellsKnown", new ArrayList<Object>());
    m.put("gold", 100);
    m.put("equipment", new ArrayList<Object>(equipment));
    Map<String, Object> choices = new LinkedHashMap<>();
    choices.put(skillLabel, new ArrayList<Object>(classSkills));
    m.put("classChoices", choices);
    m.put("asiMode", "PLUS2PLUS1");
    m.put("asiIncreases", new LinkedHashMap<String, Object>());
    m.put("level", level);
    Map<String, Object> classLevels = new LinkedHashMap<>();
    classLevels.put(cls, level);
    m.put("classLevels", classLevels);
    List<Object> rolls = new ArrayList<>();
    for (int i = 1; i < level; i++) rolls.add(hpRoll);
    m.put("hpRolls", rolls);
    m.put("feats", new ArrayList<Object>());
    m.put("conditions", new ArrayList<Object>(Arrays.asList("Conscious")));
    m.put("token", Tokens.read(null, Tokens.PC_COLOR));
    m.put("id", CustomAssets.newId());
    m.put("speciesRules", 2024);
    CharacterStorage.refreshDerived(m); // hp, hitDice
    return m;
}

@SuppressWarnings("unchecked")
private static Map<String, Object> choices(Map<String, Object> rec) {
    return (Map<String, Object>) rec.get("classChoices");
}

private static Map<String, Object> stats(int str, int dex, int con, int intel, int wis, int cha) {
    Map<String, Object> s = new LinkedHashMap<>();
    int[] v = {str, dex, con, intel, wis, cha};
    for (int i = 0; i < GameData.STAT_ORDER.length; i++) s.put(GameData.STAT_ORDER[i], v[i]);
    return s;
}

private static Map<String, Object> classSpells(String cls, List<String> cantrips, List<String> prepared, List<String> spellbook) {
    Map<String, List<String>> lists = CharacterStorage.emptyClassSpells();
    lists.get("cantrips").addAll(cantrips);
    lists.get("prepared").addAll(prepared);
    lists.get("spellbook").addAll(spellbook);
    Map<String, Map<String, List<String>>> all = new LinkedHashMap<>();
    all.put(cls, lists);
    return CharacterStorage.classSpellsToJson(all);
}

// The names that exist in SpellData (skips any a homebrew edit removed)
private static List<String> existing(String... names) {
    List<String> out = new ArrayList<>();
    for (String n : names) if (SpellData.lookup(n) != null) out.add(n);
    return out;
}
}
