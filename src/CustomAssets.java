import java.util.*;
import java.util.List;
import java.util.function.Supplier;

// Homebrew content created in the Asset Library, saved to custom_assets.json.
//
// Every asset is a plain JSON object ({"id", "name", ...fields}) stored under its category key.
// The editor form, detail overlay, validation and import/export are all driven by SCHEMAS below,
// so adding a field to a category is one line in its schema block.
//
// applyToRuntime() merges the rules-relevant categories (items, weapons, armor, conditions, spells,
// backgrounds, species, classes, subclasses) into GameData / SpellData / WeaponData. The built-in
// tables are snapshotted the first time and restored before every re-apply, so editing or deleting
// an asset never leaves stale entries behind (and overriding a built-in, e.g. a custom "Poisoned",
// is undone cleanly when the asset is deleted).
class CustomAssets {

static final String FILE = "custom_assets.json";
static final String PACK_FORMAT = "dnd-engine-homebrew";

static final String ITEMS = "Items", WEAPONS = "Weapons", ARMOR = "Armor", NPCS = "NPCs", MAPS = "Maps",
        CONDITIONS = "Conditions", SPELLS = "Spells", BACKGROUNDS = "Backgrounds", SPECIES = "Species",
        CLASSES = "Classes", SUBCLASSES = "Subclasses", FEATS = "Feats";
static final String[] CATEGORIES = {ITEMS, WEAPONS, ARMOR, NPCS, MAPS, CONDITIONS, SPELLS, FEATS, BACKGROUNDS, SPECIES, CLASSES, SUBCLASSES};

static final Map<String, String> JSON_KEYS = new LinkedHashMap<>();
static final Map<String, String> SINGULAR = new LinkedHashMap<>();
static {
    String[][] rows = {
            {ITEMS, "items", "Item"}, {WEAPONS, "weapons", "Weapon"}, {ARMOR, "armor", "Armor"},
            {NPCS, "npcs", "NPC"}, {MAPS, "maps", "Map"}, {CONDITIONS, "conditions", "Condition"},
            {SPELLS, "spells", "Spell"}, {FEATS, "feats", "Feat"}, {BACKGROUNDS, "backgrounds", "Background"}, {SPECIES, "species", "Species"},
            {CLASSES, "classes", "Class"}, {SUBCLASSES, "subclasses", "Subclass"}};
    for (String[] r : rows) {
        JSON_KEYS.put(r[0], r[1]);
        SINGULAR.put(r[0], r[2]);
    }
}

// category -> assets, in creation order (live lists; mutate only through the methods below)
private static final Map<String, List<Map<String, Object>>> DATA = new LinkedHashMap<>();
static {
    for (String c : CATEGORIES) DATA.put(c, new ArrayList<>());
}

// Run after every applyToRuntime() (e.g. App rebuilds its condition-link pattern)
static final List<Runnable> CHANGE_LISTENERS = new ArrayList<>();

// ===============================================================
// Option lists
// ===============================================================
static final String[] RARITIES = {"Mundane", "Common", "Uncommon", "Rare", "Very Rare", "Legendary", "Artifact"};
static final String[] ITEM_CATEGORIES = {"Gear", "Consumable", "Wondrous", "Tool"};
static final String[] DAMAGE_TYPES = {"Acid", "Bludgeoning", "Cold", "Fire", "Force", "Lightning", "Necrotic",
        "Piercing", "Poison", "Psychic", "Radiant", "Slashing", "Thunder"};
static final String[] MASTERIES = {"None", "Cleave", "Graze", "Nick", "Push", "Sap", "Slow", "Topple", "Vex"};
static final String[] WEAPON_PROPERTIES = {"Ammunition", "Finesse", "Heavy", "Light", "Loading", "Range", "Reach",
        "Thrown", "Two-Handed", "Versatile"};
static final String[] ARMOR_CATEGORIES = {"Light", "Medium", "Heavy", "Shield"};
static final String[] DEX_CAPS = {"Category default", "Uncapped", "None", "+1", "+2", "+3", "+4"};
static final String[] SIZES = {"Tiny", "Small", "Medium", "Large", "Huge", "Gargantuan"};
static final String[] CREATURE_TYPES = {"Aberration", "Beast", "Celestial", "Construct", "Dragon", "Elemental",
        "Fey", "Fiend", "Giant", "Humanoid", "Monstrosity", "Ooze", "Plant", "Undead"};
static final String[] ALIGNMENTS = {"Unaligned", "Lawful Good", "Neutral Good", "Chaotic Good", "Lawful Neutral",
        "Neutral", "Chaotic Neutral", "Lawful Evil", "Neutral Evil", "Chaotic Evil"};
static final String[] CHALLENGE_RATINGS;
static {
    List<String> crs = new ArrayList<>(Arrays.asList("0", "1/8", "1/4", "1/2"));
    for (int i = 1; i <= 30; i++) crs.add(String.valueOf(i));
    CHALLENGE_RATINGS = crs.toArray(new String[0]);
}
static final String[] SCHOOLS = {SpellData.ABJ, SpellData.CON, SpellData.DIV, SpellData.ENC, SpellData.EVO,
        SpellData.ILL, SpellData.NEC, SpellData.TRA};
static final String[] CASTING_TIMES = {"Action", "Bonus Action", "Reaction", "1 minute", "10 minutes", "1 hour",
        "8 hours", "12 hours", "24 hours"};
static final String[] HIT_DICE = {"d6", "d8", "d10", "d12"};
static final String[] SPELLCASTING = {"None", "Half", "Full", "Pact"};
static final String[] ARMOR_TRAINING = {"Light", "Medium", "Heavy", "Shields"};
static final String[] WEAPON_TRAINING = {"Simple", "Martial"};
static final String OWN_SPELL_LIST = "(Its own list)";
static final String FEAT_GENERAL = "General", FEAT_ORIGIN = "Origin", FEAT_EPIC = "Epic Boon";
static final String[] FEAT_TYPES = {FEAT_GENERAL, FEAT_ORIGIN, FEAT_EPIC};

// 2024 Origin feats (short summaries) offered for backgrounds alongside the general feats
static final Map<String, String> ORIGIN_FEATS = new LinkedHashMap<>();
static {
    ORIGIN_FEATS.put("Alert", "Add your Proficiency Bonus to Initiative rolls, and you can swap Initiative with a willing ally at the start of combat.");
    ORIGIN_FEATS.put("Crafter", "Gain proficiency with three Artisan's Tools, a 20% discount on nonmagical gear, and craft simple items during a Long Rest.");
    ORIGIN_FEATS.put("Healer", "Use a Healer's Kit to let a creature spend a Hit Die and regain Hit Points, and reroll 1s on your healing dice.");
    ORIGIN_FEATS.put("Lucky", "Gain Luck Points equal to your Proficiency Bonus each Long Rest; spend one for Advantage on a D20 Test or to impose Disadvantage on an attack against you.");
    ORIGIN_FEATS.put("Magic Initiate", "Learn two cantrips and one level 1 spell from the Cleric, Druid, or Wizard list; cast that spell once per Long Rest without a slot.");
    ORIGIN_FEATS.put("Musician", "Gain proficiency with three Musical Instruments, and after a rest play a song that grants allies Heroic Inspiration.");
    ORIGIN_FEATS.put("Savage Attacker", "Once per turn when you hit with a weapon, roll its damage dice twice and use either total.");
    ORIGIN_FEATS.put("Skilled", "Gain proficiency in any combination of three skills or tools.");
    ORIGIN_FEATS.put("Tavern Brawler", "Unarmed Strikes deal 1d4 + STR damage and reroll 1s, you're proficient with improvised weapons, and you can push a target 5 ft. once per turn.");
    ORIGIN_FEATS.put("Tough", "Your Hit Point maximum increases by twice your character level, and by 2 more every time you gain a level.");
}

static List<String> classOptions() {
    List<String> out = new ArrayList<>();
    for (String c : GameData.CLASS_INFO.keySet()) if (GameData.hitDieMax(c) > 0) out.add(c);
    for (Map<String, Object> a : DATA.get(CLASSES)) if (!out.contains(str(a, "name"))) out.add(str(a, "name"));
    return out;
}

static List<String> conditionOptions() {
    List<String> out = new ArrayList<>();
    for (String c : GameData.CONDITION_INFO.keySet()) {
        if (!"Conscious".equals(c) && !"Unconscious".equals(c)) out.add(c);
    }
    return out;
}

static List<String> featOptions() {
    List<String> out = new ArrayList<>(ORIGIN_FEATS.keySet());
    for (String f : FeatData.GENERAL_FEATS.keySet()) if (!out.contains(f)) out.add(f);
    return out;
}

static String featDescription(String name) {
    if (name == null || name.isEmpty()) return "";
    if (ORIGIN_FEATS.containsKey(name)) return ORIGIN_FEATS.get(name);
    Feat f = FeatData.lookup(name);
    return f != null ? f.description : "";
}

// ===============================================================
// Field schema
// ===============================================================
static final String TEXT = "TEXT", AREA = "AREA", INT = "INT", BOOL = "BOOL", CHOICE = "CHOICE",
        MULTI = "MULTI", LIST = "LIST", ABILITIES = "ABILITIES", ENTRIES = "ENTRIES", TAGS = "TAGS",
        TOKEN = "TOKEN"; // {shape, color, image}: a map token (see Tokens)
// Link kinds: what clicking a value in the detail overlay opens
static final String LINK_SKILL = "skill", LINK_CONDITION = "condition", LINK_CLASS = "class",
        LINK_LANGUAGE = "language", LINK_STAT = "stat", LINK_FEAT = "feat", LINK_TAG = "tag";

static class Field {
    final String key, label, type;
    String help = "";
    String placeholder = "";
    Supplier<List<String>> options;
    Object def;
    int min = 0, max = 999;
    boolean editable;   // CHOICE: free text allowed
    boolean withLevel;  // ENTRIES: each entry has a level (class / subclass features)
    String link;        // MULTI / CHOICE: link kind for the detail overlay
    String section;     // optional heading shown above this field in the editor

    Field(String key, String label, String type) {
        this.key = key;
        this.label = label;
        this.type = type;
    }

    Field help(String h) { help = h; return this; }
    Field hint(String p) { placeholder = p; return this; }
    Field def(Object d) { def = d; return this; }
    Field range(int lo, int hi) { min = lo; max = hi; return this; }
    Field editable() { editable = true; return this; }
    Field levels() { withLevel = true; return this; }
    Field link(String l) { link = l; return this; }
    Field section(String s) { section = s; return this; }
    Field opts(String... o) { List<String> l = Arrays.asList(o); options = () -> l; return this; }
    Field opts(Supplier<List<String>> s) { options = s; return this; }

    List<String> optionList() {
        return options == null ? Collections.emptyList() : options.get();
    }
}

private static Field text(String k, String l) { return new Field(k, l, TEXT); }
private static Field area(String k, String l) { return new Field(k, l, AREA); }
private static Field num(String k, String l, int lo, int hi, int d) { return new Field(k, l, INT).range(lo, hi).def(d); }
private static Field bool(String k, String l) { return new Field(k, l, BOOL).def(false); }
private static Field choice(String k, String l, String... o) { return new Field(k, l, CHOICE).opts(o).def(o.length > 0 ? o[0] : ""); }
private static Field multi(String k, String l, String... o) { return new Field(k, l, MULTI).opts(o); }
private static Field list(String k, String l) { return new Field(k, l, LIST); }
private static Field entries(String k, String l) { return new Field(k, l, ENTRIES); }
private static Field tagsField() {
    return new Field("tags", "Tags", TAGS).link(LINK_TAG).section("Tags")
            .help("Used for searching, sorting and campaign content filters. New homebrew starts with HOMEBREW.");
}

static final Map<String, List<Field>> SCHEMAS = new LinkedHashMap<>();
static {
    SCHEMAS.put(ITEMS, Arrays.asList(
            text("name", "Name").hint("e.g. Lantern of Whispers"),
            choice("category", "Category", ITEM_CATEGORIES),
            choice("rarity", "Rarity", RARITIES).def("Common"),
            bool("attunement", "Requires attunement"),
            text("weight", "Weight").hint("e.g. 2 lb."),
            text("value", "Value").hint("e.g. 50 gp"),
            area("description", "Description")));

    SCHEMAS.put(WEAPONS, Arrays.asList(
            text("name", "Name").hint("e.g. Stormglass Saber"),
            choice("weaponCategory", "Weapon category", "Simple", "Martial").def("Martial"),
            choice("weaponType", "Melee or ranged", "Melee", "Ranged"),
            text("damageDice", "Damage dice").hint("e.g. 1d8").def("1d6").section("Damage"),
            choice("damageType", "Damage type", DAMAGE_TYPES).def("Slashing"),
            choice("mastery", "Weapon mastery", MASTERIES).help("The 2024 mastery property a trained wielder can use."),
            multi("properties", "Properties", WEAPON_PROPERTIES).section("Properties"),
            text("range", "Range").hint("e.g. 80/320").help("Used for Ammunition, Range and Thrown."),
            text("versatileDamage", "Versatile damage").hint("e.g. 1d10").help("Used when the weapon is Versatile."),
            choice("rarity", "Rarity", RARITIES).section("Details"),
            bool("attunement", "Requires attunement"),
            text("weight", "Weight").hint("e.g. 3 lb."),
            text("value", "Value").hint("e.g. 15 gp"),
            area("description", "Description")));

    SCHEMAS.put(ARMOR, Arrays.asList(
            text("name", "Name").hint("e.g. Dragonscale Mail"),
            choice("armorCategory", "Armor category", ARMOR_CATEGORIES).def("Medium"),
            num("baseAC", "Base AC", 0, 30, 14).help("For a Shield, this is the AC bonus it grants (usually 2)."),
            choice("dexCap", "DEX bonus cap", DEX_CAPS).help("Category default: Light uncapped, Medium +2, Heavy none."),
            num("strRequirement", "STR requirement", 0, 30, 0).help("0 means no requirement."),
            bool("stealthDisadvantage", "Disadvantage on Stealth checks"),
            choice("rarity", "Rarity", RARITIES).section("Details"),
            bool("attunement", "Requires attunement"),
            text("weight", "Weight").hint("e.g. 45 lb."),
            text("value", "Value").hint("e.g. 500 gp"),
            area("description", "Description")));

    SCHEMAS.put(NPCS, Arrays.asList(
            text("name", "Name").hint("e.g. Captain Mara Voss"),
            text("role", "Role / title").hint("e.g. Captain of the Watch"),
            text("species", "Species").hint("e.g. Human"),
            choice("size", "Size", SIZES).def("Medium"),
            choice("creatureType", "Creature type", CREATURE_TYPES).def("Humanoid"),
            choice("alignment", "Alignment", ALIGNMENTS).def("Neutral"),
            choice("cr", "Challenge rating", CHALLENGE_RATINGS).def("1").section("Combat"),
            num("ac", "Armor Class", 0, 40, 12),
            text("hp", "Hit Points").hint("e.g. 45 (6d10 + 12)"),
            text("speed", "Speed").hint("e.g. 30 ft., climb 20 ft.").def("30 ft."),
            new Field("abilities", "Ability scores", ABILITIES),
            text("saves", "Saving throws").hint("e.g. STR +5, CON +4"),
            text("skills", "Skills").hint("e.g. Perception +4, Stealth +6"),
            entries("traits", "Traits").section("Traits & actions"),
            entries("actions", "Actions"),
            new Field("token", "Map token", TOKEN).section("Token")
                    .help("How this NPC looks on the Combat map. Its footprint follows its size (Large 2 × 2, Huge 3 × 3, Gargantuan 4 × 4)."),
            area("bio", "Bio / notes").section("Notes")));

    SCHEMAS.put(MAPS, Arrays.asList(
            text("name", "Name").hint("e.g. Sunken Crypt"),
            num("width", "Width (squares)", 4, 120, 20).help("Each square is 5 ft. You can resize later in the map editor."),
            num("height", "Height (squares)", 4, 120, 15),
            area("notes", "Notes")));

    SCHEMAS.put(CONDITIONS, Arrays.asList(
            text("name", "Name").hint("e.g. Bleeding"),
            area("description", "Rules").help("What the condition does. It becomes clickable on character sheets and in spell text.")));

    SCHEMAS.put(SPELLS, Arrays.asList(
            text("name", "Name").hint("e.g. Ember Lance"),
            num("level", "Level", 0, 9, 1).help("0 = cantrip."),
            choice("school", "School", SCHOOLS).def(SpellData.EVO),
            choice("castingTime", "Casting time", CASTING_TIMES).editable(),
            text("range", "Range").hint("e.g. 60 ft.").def("60 ft."),
            multi("components", "Components", "V", "S", "M").section("Components & duration"),
            text("material", "Material component").hint("e.g. a pinch of sulfur").help("Used when M is selected."),
            text("duration", "Duration").hint("e.g. 1 minute").def("Instantaneous"),
            bool("concentration", "Concentration"),
            bool("ritual", "Ritual"),
            new Field("classes", "Class spell lists", MULTI).opts(CustomAssets::classOptions).link(LINK_CLASS).section("Who can cast it"),
            area("description", "Description").section("Effect"),
            area("higherLevels", "At higher levels").help("Cantrip upgrades or higher-slot scaling."),
            new Field("conditions", "Inflicted conditions", MULTI).opts(CustomAssets::conditionOptions).link(LINK_CONDITION)
                    .help("Leave empty to detect conditions named in the description automatically."),
            text("imagePath", "Card art (optional)").hint("e.g. art/spells/ember_lance.png")));

    SCHEMAS.put(FEATS, Arrays.asList(
            text("name", "Name").hint("e.g. Stormborn Resolve"),
            choice("featType", "Feat type", FEAT_TYPES)
                    .help("General feats are offered at level up, Epic Boons at level 19, and Origin feats can be given to backgrounds."),
            area("description", "Description"),
            new Field("abilityOptions", "Ability score increase", MULTI).opts(GameData.STAT_ORDER).link(LINK_STAT)
                    .help("+1 to one of these (max 20, or 30 for an Epic Boon). Leave empty for none."),
            bool("repeatable", "Can be taken more than once")));

    SCHEMAS.put(BACKGROUNDS, Arrays.asList(
            text("name", "Name").hint("e.g. Lighthouse Keeper"),
            area("description", "Description"),
            new Field("abilityScores", "Ability scores", MULTI).opts(GameData.STAT_ORDER).link(LINK_STAT)
                    .help("Pick 3 (the background's ability score increase options), or none to allow any."),
            new Field("skillProficiencies", "Skill proficiencies", MULTI).opts(GameData.ALL_SKILLS).link(LINK_SKILL),
            list("toolProficiencies", "Tool proficiencies").help("One per line."),
            new Field("originFeat", "Origin feat", CHOICE).opts(CustomAssets::featOptions).editable().def("").link(LINK_FEAT),
            area("originFeatDescription", "Origin feat text (optional)").help("Only needed for a homebrew feat."),
            num("startingGold", "Starting gold (Package A)", 0, 10000, 10).section("Starting equipment"),
            list("startingEquipment", "Starting equipment (Package A)").help("One item per line, e.g. Lantern or Rope (2).")));

    SCHEMAS.put(SPECIES, Arrays.asList(
            text("name", "Name").hint("e.g. Mossborn"),
            area("description", "Description"),
            choice("size", "Size", SIZES).def("Medium"),
            choice("creatureType", "Creature type", CREATURE_TYPES).def("Humanoid"),
            num("speed", "Speed (ft.)", 0, 120, 30),
            num("darkvision", "Darkvision (ft.)", 0, 300, 0).help("0 = none."),
            new Field("languages", "Languages", MULTI).opts(() -> new ArrayList<>(GameData.LANGUAGE_INFO.keySet())).link(LINK_LANGUAGE),
            list("extraLanguages", "Other languages").help("Homebrew languages, one per line."),
            entries("traits", "Species traits").section("Traits")));

    SCHEMAS.put(CLASSES, Arrays.asList(
            text("name", "Name").hint("e.g. Runesmith"),
            area("description", "Description"),
            choice("hitDie", "Hit die", HIT_DICE).def("d8"),
            new Field("primaryAbilities", "Primary abilities", MULTI).opts(GameData.STAT_ORDER).link(LINK_STAT),
            new Field("savingThrows", "Saving throw proficiencies", MULTI).opts(GameData.STAT_ORDER).link(LINK_STAT),
            new Field("skillChoices", "Skill options", MULTI).opts(GameData.ALL_SKILLS).link(LINK_SKILL).section("Proficiencies"),
            num("skillChoiceCount", "Skills to choose", 0, 18, 2),
            multi("armorTraining", "Armor training", ARMOR_TRAINING),
            multi("weaponProficiencies", "Weapon proficiencies", WEAPON_TRAINING),
            list("toolProficiencies", "Tool & other proficiencies").help("One per line."),
            choice("spellcasting", "Spellcasting progression", SPELLCASTING).section("Spellcasting")
                    .help("Full = Wizard-style slots, Half = Paladin-style, Pact = Warlock-style."),
            choice("spellcastingAbility", "Spellcasting ability", "INT", "WIS", "CHA"),
            new Field("spellListFrom", "Spell list", CHOICE).opts(() -> {
                List<String> o = new ArrayList<>();
                o.add(OWN_SPELL_LIST);
                o.addAll(classOptions());
                return o;
            }).def(OWN_SPELL_LIST).help("Use another class's spell list, or its own (spells that list this class)."),
            entries("features", "Class features (levels 1-20)").levels().section("Features"),
            num("startingGold", "Starting gold (Package A)", 0, 10000, 10).section("Starting equipment"),
            num("packageBGold", "Package B gold", 0, 10000, 100),
            list("startingEquipment", "Starting equipment (Package A)").help("One item per line.")));

    SCHEMAS.put(SUBCLASSES, Arrays.asList(
            new Field("parentClass", "Parent class", CHOICE).opts(CustomAssets::classOptions).def("Fighter").link(LINK_CLASS),
            text("name", "Subclass name").hint("e.g. Path of the Tide"),
            area("description", "Description"),
            num("subclassLevel", "Chosen at class level", 1, 20, 3),
            entries("features", "Subclass features").levels().section("Features")));

    // Every category carries tags (stored in the asset's "tags" list)
    for (Map.Entry<String, List<Field>> en : SCHEMAS.entrySet()) {
        List<Field> withTags = new ArrayList<>(en.getValue());
        withTags.add(tagsField());
        en.setValue(Collections.unmodifiableList(withTags));
    }
}

// Field whose value sorts/filters a category in the library ("All" + each distinct value)
static final Map<String, String> FILTER_KEYS = new LinkedHashMap<>();
static {
    FILTER_KEYS.put(ITEMS, "category");
    FILTER_KEYS.put(WEAPONS, "weaponCategory");
    FILTER_KEYS.put(ARMOR, "armorCategory");
    FILTER_KEYS.put(NPCS, "creatureType");
    FILTER_KEYS.put(SPELLS, "level");
    FILTER_KEYS.put(FEATS, "featType");
    FILTER_KEYS.put(SPECIES, "size");
    FILTER_KEYS.put(CLASSES, "spellcasting");
    FILTER_KEYS.put(SUBCLASSES, "parentClass");
}

static String filterValue(String cat, Map<String, Object> a) {
    String key = FILTER_KEYS.get(cat);
    if (key == null) return null;
    if (SPELLS.equals(cat)) {
        int lv = intVal(a, "level", 0);
        return lv == 0 ? "Cantrip" : "Level " + lv;
    }
    String v = str(a, key);
    return v.isEmpty() ? "—" : v;
}

static Field field(String cat, String key) {
    for (Field f : SCHEMAS.get(cat)) if (f.key.equals(key)) return f;
    return null;
}

// ===============================================================
// Value helpers (JSON numbers come back as Double)
// ===============================================================
static String str(Map<?, ?> a, String key) {
    Object v = a == null ? null : a.get(key);
    return (v == null || "null".equals(String.valueOf(v))) ? "" : String.valueOf(v);
}

static int intVal(Map<?, ?> a, String key, int def) {
    Object v = a == null ? null : a.get(key);
    if (v instanceof Number) return ((Number) v).intValue();
    try {
        return v == null ? def : Integer.parseInt(String.valueOf(v).trim());
    } catch (NumberFormatException ex) {
        return def;
    }
}

static boolean bool(Map<?, ?> a, String key) {
    return a != null && Boolean.TRUE.equals(a.get(key));
}

static List<String> list(Map<?, ?> a, String key) {
    List<String> out = new ArrayList<>();
    Object v = a == null ? null : a.get(key);
    if (v instanceof List) {
        for (Object o : (List<?>) v) {
            String s = String.valueOf(o).trim();
            if (o != null && !s.isEmpty()) out.add(s);
        }
    }
    return out;
}

// Feature / trait / action entries: [{level?, name, description}]
static List<Map<String, Object>> entries(Map<?, ?> a, String key) {
    List<Map<String, Object>> out = new ArrayList<>();
    Object v = a == null ? null : a.get(key);
    if (v instanceof List) {
        for (Object o : (List<?>) v) {
            if (!(o instanceof Map)) continue;
            Map<String, Object> e = new LinkedHashMap<>();
            for (Map.Entry<?, ?> en : ((Map<?, ?>) o).entrySet()) e.put(String.valueOf(en.getKey()), en.getValue());
            out.add(e);
        }
    }
    return out;
}

static Map<String, Integer> abilities(Map<?, ?> a) {
    Map<String, Integer> out = new LinkedHashMap<>();
    Object v = a == null ? null : a.get("abilities");
    for (String s : GameData.STAT_ORDER) {
        int score = 10;
        if (v instanceof Map && ((Map<?, ?>) v).get(s) instanceof Number) score = ((Number) ((Map<?, ?>) v).get(s)).intValue();
        out.put(s, score);
    }
    return out;
}

// ===============================================================
// CRUD
// ===============================================================
static List<Map<String, Object>> all(String cat) {
    return Collections.unmodifiableList(DATA.get(cat));
}

static int count(String cat) {
    return DATA.get(cat).size();
}

static Map<String, Object> find(String cat, String id) {
    if (id == null) return null;
    for (Map<String, Object> a : DATA.get(cat)) if (id.equals(str(a, "id"))) return a;
    return null;
}

static Map<String, Object> findByName(String cat, String name) {
    if (name == null || name.isEmpty()) return null;
    for (Map<String, Object> a : DATA.get(cat)) if (name.equals(str(a, "name"))) return a;
    return null;
}

static String newId() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
}

static Map<String, Object> newAsset(String cat) {
    Map<String, Object> a = new LinkedHashMap<>();
    a.put("id", newId());
    for (Field f : SCHEMAS.get(cat)) {
        if (f.def != null) a.put(f.key, f.def);
        else if (TAGS.equals(f.type)) a.put(f.key, new ArrayList<Object>(Collections.singletonList(Tags.HOMEBREW)));
        else if (TOKEN.equals(f.type)) a.put(f.key, Tokens.defaults(Tokens.NPC_COLOR));
        else if (MULTI.equals(f.type) || LIST.equals(f.type) || ENTRIES.equals(f.type)) a.put(f.key, new ArrayList<>());
        else if (ABILITIES.equals(f.type)) {
            Map<String, Object> ab = new LinkedHashMap<>();
            for (String s : GameData.STAT_ORDER) ab.put(s, 10);
            a.put(f.key, ab);
        } else a.put(f.key, "");
    }
    if (SPELLS.equals(cat)) a.put("components", new ArrayList<Object>(Arrays.asList("V", "S")));
    if (MAPS.equals(cat)) a.put("grid", MapEditorPanel.MapModel.blank(20, 15).toJson());
    return a;
}

@SuppressWarnings("unchecked")
static Map<String, Object> deepCopy(Map<String, Object> a) {
    return (Map<String, Object>) new MiniJsonParser(JsonUtil.toJson(a)).parse();
}

// Returns an error message, or null if the asset can be saved
static String validate(String cat, Map<String, Object> a) {
    String name = str(a, "name").trim();
    if (name.isEmpty()) return "Give this " + noun(cat, false) + " a name.";
    for (Map<String, Object> other : DATA.get(cat)) {
        if (str(other, "id").equals(str(a, "id"))) continue;
        boolean sameParent = !SUBCLASSES.equals(cat) || str(other, "parentClass").equals(str(a, "parentClass"));
        if (sameParent && str(other, "name").equalsIgnoreCase(name)) {
            return "Another " + noun(cat, false) + " is already named \"" + str(other, "name") + "\".";
        }
    }
    if (WEAPONS.equals(cat)) {
        if (!str(a, "damageDice").trim().matches("\\d*d\\d+(\\s*[+-]\\s*\\d+)?|\\d+")) {
            return "Damage dice should look like 1d8, 2d6 or 1d10 + 2.";
        }
        if (list(a, "properties").contains("Versatile") && str(a, "versatileDamage").trim().isEmpty()) {
            return "Versatile weapons need their two-handed damage (e.g. 1d10).";
        }
    }
    if (BACKGROUNDS.equals(cat)) {
        int n = list(a, "abilityScores").size();
        if (n != 0 && n != 3) return "Pick exactly 3 ability scores for the background (or none to allow any).";
    }
    if (SUBCLASSES.equals(cat) && str(a, "parentClass").trim().isEmpty()) return "Choose the subclass's parent class.";
    if (CLASSES.equals(cat) && ("Custom".equalsIgnoreCase(name))) return "\"Custom\" is reserved for the wizard's manual class.";
    if ((SPECIES.equals(cat) || BACKGROUNDS.equals(cat)) && "Custom".equalsIgnoreCase(name)) {
        return "\"Custom\" is reserved for the wizard's manual option.";
    }
    if (CONDITIONS.equals(cat) && ("Conscious".equalsIgnoreCase(name) || "Unconscious".equalsIgnoreCase(name))) {
        return "Conscious and Unconscious are built-in states and can't be replaced.";
    }
    return null;
}

// Name of a built-in rule this asset replaces (for an inline note in the editor), or null
static String overridesBuiltIn(String cat, String name) {
    if (name == null || name.trim().isEmpty()) return null;
    name = name.trim();
    Map<?, ?> table = null;
    if (CLASSES.equals(cat)) table = BUILTIN_CLASS_INFO();
    else if (SPECIES.equals(cat)) table = snapshotOf(GameData.SPECIES_INFO);
    else if (BACKGROUNDS.equals(cat)) table = snapshotOf(GameData.BACKGROUND_INFO);
    else if (CONDITIONS.equals(cat)) table = snapshotOf(GameData.CONDITION_INFO);
    else if (SPELLS.equals(cat)) table = snapshotOf(SpellData.SPELLS);
    else if (ARMOR.equals(cat)) table = snapshotOf(GameData.ARMOR_AC);
    if (table != null && table.containsKey(name)) return name;
    if (WEAPONS.equals(cat) && weaponSnapshot != null) {
        for (WeaponEntry we : weaponSnapshot) if (we.name.equalsIgnoreCase(name)) return we.name;
    }
    if (FEATS.equals(cat)) {
        for (String f : builtinNames(FEATS)) if (f.equalsIgnoreCase(name)) return f;
    }
    return null;
}

private static Map<?, ?> BUILTIN_CLASS_INFO() {
    return snapshotOf(GameData.CLASS_INFO);
}

// Adds or replaces (by id), then saves and re-applies
static void put(String cat, Map<String, Object> asset) {
    List<Map<String, Object>> l = DATA.get(cat);
    String id = str(asset, "id");
    if (id.isEmpty()) asset.put("id", id = newId());
    for (int i = 0; i < l.size(); i++) {
        if (id.equals(str(l.get(i), "id"))) {
            l.set(i, asset);
            commit();
            return;
        }
    }
    l.add(asset);
    commit();
}

// Replace-and-save without re-applying rules (map edits are frequent and don't touch GameData)
static void putQuiet(String cat, Map<String, Object> asset) {
    List<Map<String, Object>> l = DATA.get(cat);
    String id = str(asset, "id");
    boolean found = false;
    for (int i = 0; i < l.size(); i++) {
        if (id.equals(str(l.get(i), "id"))) {
            l.set(i, asset);
            found = true;
            break;
        }
    }
    if (!found) l.add(asset);
    save();
}

static void delete(String cat, String id) {
    DATA.get(cat).removeIf(a -> id.equals(str(a, "id")));
    if (NPCS.equals(cat)) unlinkNpcFromMaps(id);
    commit();
}

static Map<String, Object> duplicate(String cat, String id) {
    Map<String, Object> src = find(cat, id);
    if (src == null) return null;
    Map<String, Object> copy = deepCopy(src);
    copy.put("id", newId());
    copy.put("name", uniqueName(cat, str(src, "name") + " (Copy)", str(src, "parentClass")));
    put(cat, copy);
    return copy;
}

static String uniqueName(String cat, String base, String parent) {
    String name = base;
    int n = 2;
    while (nameTaken(cat, name, parent)) name = base + " " + n++;
    return name;
}

private static boolean nameTaken(String cat, String name, String parent) {
    for (Map<String, Object> a : DATA.get(cat)) {
        boolean sameParent = !SUBCLASSES.equals(cat) || str(a, "parentClass").equals(parent);
        if (sameParent && str(a, "name").equalsIgnoreCase(name)) return true;
    }
    return false;
}

// Deleted NPCs disappear from map pins that linked them
@SuppressWarnings("unchecked")
private static void unlinkNpcFromMaps(String npcId) {
    for (Map<String, Object> m : DATA.get(MAPS)) {
        Object grid = m.get("grid");
        if (!(grid instanceof Map)) continue;
        Object pins = ((Map<String, Object>) grid).get("pins");
        if (!(pins instanceof List)) continue;
        for (Object p : (List<?>) pins) {
            if (!(p instanceof Map)) continue;
            Object ids = ((Map<String, Object>) p).get("npcIds");
            if (ids instanceof List) ((List<?>) ids).removeIf(x -> npcId.equals(String.valueOf(x)));
        }
    }
}

private static void commit() {
    save();
    applyToRuntime();
}

// ===============================================================
// Persistence
// ===============================================================
static void load() {
    for (String c : CATEGORIES) DATA.get(c).clear();
    Tags.read(null);
    try {
        java.nio.file.Path p = DataPaths.userFile(FILE);
        if (!java.nio.file.Files.exists(p)) return;
        String content = new String(java.nio.file.Files.readAllBytes(p), java.nio.charset.StandardCharsets.UTF_8);
        Object parsed = new MiniJsonParser(content).parse();
        if (parsed instanceof Map) {
            readCategories((Map<?, ?>) parsed, (cat, asset) -> DATA.get(cat).add(asset));
            Tags.read(((Map<?, ?>) parsed).get("tagSettings"));
        }
    } catch (Exception ex) {
        // custom_assets.json unreadable: start empty rather than crash (the file is left untouched until the next save)
    }
}

static void save() {
    try {
        java.nio.file.Files.write(DataPaths.userFile(FILE),
                JsonUtil.toPrettyJson(saveData()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    } catch (Exception ex) {
        // best-effort save (e.g. read-only folder); the session keeps working from memory
    }
}

// Everything custom_assets.json holds: all homebrew plus tag settings
static Map<String, Object> saveData() {
    Map<String, Object> root = packOf(Arrays.asList(CATEGORIES));
    root.put("tagSettings", Tags.toJson()); // campaign content filters, custom tags, built-in tag edits
    return root;
}

// Replaces all homebrew and tag settings (restoring a backup), then re-applies the rules
static void replaceAll(Map<?, ?> data) throws java.io.IOException {
    java.nio.file.Files.write(DataPaths.userFile(FILE),
            JsonUtil.toPrettyJson(data).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    load();
    applyToRuntime();
}

static int totalCount() {
    int n = 0;
    for (String c : CATEGORIES) n += count(c);
    return n;
}

static Map<String, Object> packOf(Collection<String> cats) {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("format", PACK_FORMAT);
    root.put("version", 1);
    for (String c : CATEGORIES) {
        if (!cats.contains(c)) continue;
        root.put(JSON_KEYS.get(c), new ArrayList<Object>(DATA.get(c)));
    }
    return root;
}

// Calls sink for every asset found in a pack object; accepts "items" or "Items" style keys
private static void readCategories(Map<?, ?> root, java.util.function.BiConsumer<String, Map<String, Object>> sink) {
    for (String c : CATEGORIES) {
        Object arr = null;
        for (Map.Entry<?, ?> en : root.entrySet()) {
            String k = String.valueOf(en.getKey());
            if (k.equalsIgnoreCase(JSON_KEYS.get(c)) || k.equalsIgnoreCase(c)) { arr = en.getValue(); break; }
        }
        if (!(arr instanceof List)) continue;
        for (Object o : (List<?>) arr) {
            if (!(o instanceof Map)) continue;
            Map<String, Object> asset = new LinkedHashMap<>();
            for (Map.Entry<?, ?> en : ((Map<?, ?>) o).entrySet()) asset.put(String.valueOf(en.getKey()), en.getValue());
            if (str(asset, "id").isEmpty()) asset.put("id", newId());
            if (str(asset, "name").trim().isEmpty()) continue;
            if (!(asset.get("tags") instanceof List)) asset.put("tags", new ArrayList<Object>(Collections.singletonList(Tags.HOMEBREW)));
            if (MAPS.equals(c) && !(asset.get("grid") instanceof Map)) {
                asset.put("grid", MapEditorPanel.MapModel.blank(intVal(asset, "width", 20), intVal(asset, "height", 15)).toJson());
            }
            sink.accept(c, asset);
        }
    }
}

// ===============================================================
// Import / Export (homebrew packs)
// ===============================================================
static final String CONFLICT_REPLACE = "Replace existing", CONFLICT_SKIP = "Skip duplicates", CONFLICT_KEEP = "Keep both (rename)";

static class ImportResult {
    int added, replaced, skipped;
    String error;

    String summary() {
        if (error != null) return error;
        List<String> parts = new ArrayList<>();
        parts.add(added + " added");
        if (replaced > 0) parts.add(replaced + " replaced");
        if (skipped > 0) parts.add(skipped + " skipped");
        return "Imported: " + String.join(", ", parts) + ".";
    }
}

static int exportPack(Collection<String> cats, String path) throws java.io.IOException {
    Map<String, Object> pack = packOf(cats);
    int n = 0;
    for (String c : cats) n += DATA.get(c).size();
    java.nio.file.Files.write(DataPaths.userFile(path),
            JsonUtil.toPrettyJson(pack).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    return n;
}

static ImportResult importPack(String json, String conflictMode) {
    ImportResult r = new ImportResult();
    Object parsed;
    try {
        parsed = new MiniJsonParser(json.trim()).parse();
    } catch (Exception ex) {
        r.error = "That isn't valid JSON (" + ex.getClass().getSimpleName() + ").";
        return r;
    }
    if (!(parsed instanceof Map)) {
        r.error = "Expected a homebrew pack: a JSON object with lists like \"items\", \"spells\" or \"classes\".";
        return r;
    }
    List<String[]> found = new ArrayList<>();
    List<Map<String, Object>> incoming = new ArrayList<>();
    readCategories((Map<?, ?>) parsed, (cat, asset) -> {
        found.add(new String[]{cat});
        incoming.add(asset);
    });
    if (incoming.isEmpty()) {
        r.error = "No assets found. A pack looks like {\"items\": [{\"name\": \"...\"}], \"spells\": [...]}.";
        return r;
    }
    for (int i = 0; i < incoming.size(); i++) {
        String cat = found.get(i)[0];
        Map<String, Object> asset = incoming.get(i);
        Map<String, Object> existing = null;
        for (Map<String, Object> a : DATA.get(cat)) {
            boolean sameParent = !SUBCLASSES.equals(cat) || str(a, "parentClass").equals(str(asset, "parentClass"));
            if (sameParent && str(a, "name").equalsIgnoreCase(str(asset, "name"))) { existing = a; break; }
        }
        if (existing != null) {
            if (CONFLICT_SKIP.equals(conflictMode)) {
                r.skipped++;
                continue;
            }
            if (CONFLICT_REPLACE.equals(conflictMode)) {
                asset.put("id", str(existing, "id")); // keep links (map pins) pointing at it
                List<Map<String, Object>> l = DATA.get(cat);
                l.set(l.indexOf(existing), asset);
                r.replaced++;
                continue;
            }
            asset.put("name", uniqueName(cat, str(asset, "name"), str(asset, "parentClass")));
        }
        if (findAnyById(str(asset, "id")) != null) asset.put("id", newId());
        DATA.get(cat).add(asset);
        r.added++;
    }
    commit();
    return r;
}

private static Map<String, Object> findAnyById(String id) {
    for (String c : CATEGORIES) {
        Map<String, Object> a = find(c, id);
        if (a != null) return a;
    }
    return null;
}

// ===============================================================
// Runtime integration
// ===============================================================
private static final IdentityHashMap<Map<?, ?>, Map<Object, Object>> SNAPSHOTS = new IdentityHashMap<>();
private static List<WeaponEntry> weaponSnapshot;
private static WeaponDatabase weaponSnapshotDb;

@SuppressWarnings("unchecked")
private static void snapshot(Map<?, ?>... maps) {
    for (Map<?, ?> m : maps) {
        if (!SNAPSHOTS.containsKey(m)) SNAPSHOTS.put(m, new LinkedHashMap<>((Map<Object, Object>) m));
    }
}

private static Map<?, ?> snapshotOf(Map<?, ?> live) {
    Map<?, ?> s = SNAPSHOTS.get(live);
    return s != null ? s : live;
}

@SuppressWarnings("unchecked")
private static void restoreSnapshots() {
    for (Map.Entry<Map<?, ?>, Map<Object, Object>> en : SNAPSHOTS.entrySet()) {
        Map<Object, Object> live = (Map<Object, Object>) en.getKey();
        live.clear();
        live.putAll(en.getValue());
    }
    if (weaponSnapshotDb != WeaponData.WEAPON_DB) { // weapons.json was (re)loaded: that's the new baseline
        weaponSnapshotDb = WeaponData.WEAPON_DB;
        weaponSnapshot = new ArrayList<>(WeaponData.WEAPON_DB.weapons);
    }
    WeaponData.WEAPON_DB.weapons.clear();
    WeaponData.WEAPON_DB.weapons.addAll(weaponSnapshot);
}

// Inserts new keys just before the wizard's "Custom" entry (or at the end); keys that already
// exist (homebrew overriding a built-in) are replaced in place.
private static <V> void insertBeforeCustom(Map<String, V> map, Map<String, V> additions) {
    if (additions.isEmpty()) return;
    LinkedHashMap<String, V> rebuilt = new LinkedHashMap<>();
    boolean inserted = false;
    for (Map.Entry<String, V> en : map.entrySet()) {
        if (!inserted && "Custom".equals(en.getKey())) {
            for (Map.Entry<String, V> a : additions.entrySet()) if (!map.containsKey(a.getKey())) rebuilt.put(a.getKey(), a.getValue());
            inserted = true;
        }
        rebuilt.put(en.getKey(), additions.containsKey(en.getKey()) ? additions.get(en.getKey()) : en.getValue());
    }
    if (!inserted) for (Map.Entry<String, V> a : additions.entrySet()) if (!map.containsKey(a.getKey())) rebuilt.put(a.getKey(), a.getValue());
    map.clear();
    map.putAll(rebuilt);
}

private static final int[] FULL_CANTRIPS = {3, 3, 3, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5};
private static final int[] FULL_PREPARED = {4, 5, 6, 7, 9, 10, 11, 12, 14, 15, 16, 16, 17, 17, 18, 18, 19, 20, 21, 22};
private static final int[] HALF_PREPARED = {2, 3, 4, 5, 6, 6, 7, 7, 9, 9, 10, 10, 11, 11, 12, 12, 14, 14, 15, 15};
private static final int[] PACT_CANTRIPS = {2, 2, 2, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4};
private static final int[] PACT_PREPARED = {2, 3, 4, 5, 6, 7, 8, 9, 10, 10, 11, 11, 12, 12, 13, 13, 14, 14, 15, 15};

static void applyToRuntime() {
    snapshot(GameData.CLASS_INFO, GameData.SPECIES_INFO, GameData.BACKGROUND_INFO, GameData.BACKGROUND_STATS,
            GameData.BACKGROUND_SKILL_PROFICIENCIES, GameData.BACKGROUND_EQUIPMENT, GameData.CLASS_EQUIPMENT,
            GameData.CLASS_PACKAGE_B_GOLD, GameData.CONDITION_INFO, GameData.CLASS_SAVING_THROWS,
            GameData.CLASS_DECISIONS, GameData.LANGUAGE_INFO, GameData.ARMOR_AC, GameData.SPELL_SLOTS_LEVEL1,
            SpellData.SPELLS, SpellData.CASTING_ABILITY, SpellData.CASTER_TYPE, SpellData.CANTRIPS_BY_LEVEL,
            SpellData.PREPARED_BY_LEVEL, FeatData.GENERAL_FEATS, FeatData.EPIC_BOONS, ORIGIN_FEATS);
    restoreSnapshots();

    // Conditions first: spell descriptions are scanned for condition names
    for (Map<String, Object> a : DATA.get(CONDITIONS)) {
        GameData.CONDITION_INFO.put(str(a, "name"), str(a, "description"));
    }

    for (Map<String, Object> a : DATA.get(ITEMS)) { /* items are looked up by name (itemDescription / presets) */ }

    for (Map<String, Object> a : DATA.get(WEAPONS)) {
        String name = str(a, "name");
        WeaponData.WEAPON_DB.weapons.removeIf(w -> w.name.equalsIgnoreCase(name));
        WeaponData.WEAPON_DB.weapons.add(toWeaponEntry(a));
    }

    for (Map<String, Object> a : DATA.get(ARMOR)) {
        GameData.ARMOR_AC.put(str(a, "name"), toArmorInfo(a));
    }

    // Species (+ their homebrew languages, so those are clickable too)
    Map<String, String> species = new LinkedHashMap<>();
    for (Map<String, Object> a : DATA.get(SPECIES)) {
        species.put(str(a, "name"), speciesInfoText(a));
        for (String lang : list(a, "extraLanguages")) {
            GameData.LANGUAGE_INFO.putIfAbsent(lang, "A homebrew language spoken by the " + str(a, "name") + " species.");
        }
    }
    insertBeforeCustom(GameData.SPECIES_INFO, species);

    // Backgrounds
    Map<String, String> backgrounds = new LinkedHashMap<>();
    for (Map<String, Object> a : DATA.get(BACKGROUNDS)) {
        String name = str(a, "name");
        backgrounds.put(name, backgroundInfoText(a));
        List<String> stats = list(a, "abilityScores");
        GameData.BACKGROUND_STATS.put(name, stats.size() >= 3 ? stats.toArray(new String[0]) : GameData.STAT_ORDER);
        GameData.BACKGROUND_SKILL_PROFICIENCIES.put(name, list(a, "skillProficiencies").toArray(new String[0]));
        List<String> equip = list(a, "startingEquipment");
        int gold = intVal(a, "startingGold", 0);
        if (!equip.isEmpty() || gold > 0) {
            GameData.BACKGROUND_EQUIPMENT.put(name, new EquipmentPackage(gold, equip.toArray(new String[0])));
        } else {
            GameData.BACKGROUND_EQUIPMENT.remove(name);
        }
    }
    insertBeforeCustom(GameData.BACKGROUND_INFO, backgrounds);

    // Classes: rules tables first (spells need CASTER_TYPE to exist before decisions are counted)
    Map<String, String[]> classes = new LinkedHashMap<>();
    for (Map<String, Object> a : DATA.get(CLASSES)) {
        String name = str(a, "name");
        classes.put(name, new String[]{str(a, "hitDie").isEmpty() ? "d8" : str(a, "hitDie"), classInfoText(a)});
        GameData.CLASS_SAVING_THROWS.put(name, list(a, "savingThrows").toArray(new String[0]));
        SpellData.CASTING_ABILITY.remove(name);
        SpellData.CASTER_TYPE.remove(name);
        SpellData.CANTRIPS_BY_LEVEL.remove(name);
        SpellData.PREPARED_BY_LEVEL.remove(name);
        GameData.SPELL_SLOTS_LEVEL1.remove(name);
        String casting = str(a, "spellcasting");
        String ability = str(a, "spellcastingAbility").isEmpty() ? "INT" : str(a, "spellcastingAbility");
        if ("Full".equals(casting)) {
            SpellData.caster(name, ability, SpellData.FULL, FULL_CANTRIPS, FULL_PREPARED);
            GameData.SPELL_SLOTS_LEVEL1.put(name, 2);
        } else if ("Half".equals(casting)) {
            SpellData.caster(name, ability, SpellData.HALF, null, HALF_PREPARED);
            GameData.SPELL_SLOTS_LEVEL1.put(name, 2);
        } else if ("Pact".equals(casting)) {
            SpellData.caster(name, ability, SpellData.PACT, PACT_CANTRIPS, PACT_PREPARED);
            GameData.SPELL_SLOTS_LEVEL1.put(name, 1);
        }
        List<String> equip = list(a, "startingEquipment");
        if (!equip.isEmpty()) {
            GameData.CLASS_EQUIPMENT.put(name, new EquipmentPackage(intVal(a, "startingGold", 0), equip.toArray(new String[0])));
        } else {
            GameData.CLASS_EQUIPMENT.remove(name);
        }
        GameData.CLASS_PACKAGE_B_GOLD.put(name, intVal(a, "packageBGold", 0));
    }
    insertBeforeCustom(GameData.CLASS_INFO, classes);

    // Feats (a homebrew feat replaces a built-in of the same name, whatever its type)
    for (Map<String, Object> a : DATA.get(FEATS)) {
        String name = str(a, "name");
        FeatData.GENERAL_FEATS.remove(name);
        FeatData.EPIC_BOONS.remove(name);
        ORIGIN_FEATS.remove(name);
        String type = str(a, "featType");
        if (FEAT_ORIGIN.equals(type)) {
            ORIGIN_FEATS.put(name, str(a, "description"));
            continue;
        }
        boolean epic = FEAT_EPIC.equals(type);
        Feat f = new Feat(name, epic ? FeatData.CATEGORY_EPIC : FeatData.CATEGORY_GENERAL, str(a, "description"),
                list(a, "abilityOptions").toArray(new String[0]), epic ? FeatData.EPIC_STAT_CAP : FeatData.NORMAL_STAT_CAP);
        if (bool(a, "repeatable")) f.repeatable();
        (epic ? FeatData.EPIC_BOONS : FeatData.GENERAL_FEATS).put(name, f);
    }

    // Spells (after conditions and classes)
    for (Map<String, Object> a : DATA.get(SPELLS)) {
        Spell s = toSpell(a);
        SpellData.SPELLS.put(s.name, s);
    }

    // Level-1 wizard choices for homebrew classes (counts capped by what's actually available)
    for (Map<String, Object> a : DATA.get(CLASSES)) {
        String name = str(a, "name");
        List<DecisionItem> items = new ArrayList<>();
        List<String> skillOpts = list(a, "skillChoices");
        int n = Math.min(intVal(a, "skillChoiceCount", 2), skillOpts.size());
        if (n > 0) items.add(GameData.skills("Which skills? (" + n + ")", n, skillOpts.toArray(new String[0])));
        if (SpellData.isCaster(name)) {
            int c = Math.min(SpellData.cantripsKnown(name, 1), SpellData.classSpells(name, 0, 0).size());
            if (c > 0) items.add(GameData.cantrips("Cantrips? (" + c + ")", c));
            int p = Math.min(SpellData.preparedCount(name, 1), SpellData.classSpells(name, 1, 1).size());
            if (p > 0) items.add(GameData.spellsLevel1("Prepared spells? (" + p + ")", p));
        }
        GameData.CLASS_DECISIONS.put(name, items);
    }

    for (Runnable r : CHANGE_LISTENERS) r.run();
}

static WeaponEntry toWeaponEntry(Map<String, Object> a) {
    WeaponEntry we = new WeaponEntry();
    we.name = str(a, "name");
    we.category = (str(a, "weaponCategory").isEmpty() ? "Simple" : str(a, "weaponCategory")) + " "
            + (str(a, "weaponType").isEmpty() ? "Melee" : str(a, "weaponType"));
    we.cost = str(a, "value").isEmpty() ? "—" : str(a, "value");
    we.damage = (str(a, "damageDice").trim() + " " + str(a, "damageType").toLowerCase()).trim();
    we.weight = str(a, "weight").isEmpty() ? "—" : str(a, "weight");
    String mastery = str(a, "mastery");
    we.mastery = mastery.isEmpty() || "None".equals(mastery) ? "—" : mastery;
    we.properties = weaponPropertyLabels(a);
    return we;
}

static List<String> weaponPropertyLabels(Map<String, Object> a) {
    List<String> out = new ArrayList<>();
    String range = str(a, "range").trim();
    for (String p : list(a, "properties")) {
        if (("Thrown".equals(p) || "Ammunition".equals(p) || "Range".equals(p)) && !range.isEmpty()) out.add(p + " (range " + range + ")");
        else if ("Versatile".equals(p) && !str(a, "versatileDamage").trim().isEmpty()) out.add("Versatile (" + str(a, "versatileDamage").trim() + ")");
        else out.add(p);
    }
    return out;
}

static GameData.ArmorInfo toArmorInfo(Map<String, Object> a) {
    String cat = str(a, "armorCategory");
    boolean shield = "Shield".equals(cat);
    return new GameData.ArmorInfo(intVal(a, "baseAC", shield ? 2 : 10), dexCap(a), shield);
}

// Integer.MAX_VALUE = uncapped, 0 = no DEX (GameData.ArmorInfo convention)
static int dexCap(Map<String, Object> a) {
    String cap = str(a, "dexCap");
    switch (cap) {
        case "Uncapped": return Integer.MAX_VALUE;
        case "None": return 0;
        case "+1": return 1;
        case "+2": return 2;
        case "+3": return 3;
        case "+4": return 4;
        default:
            String cat = str(a, "armorCategory");
            if ("Light".equals(cat)) return Integer.MAX_VALUE;
            if ("Medium".equals(cat)) return 2;
            return 0;
    }
}

static String acText(Map<String, Object> a) {
    if ("Shield".equals(str(a, "armorCategory"))) return "+" + intVal(a, "baseAC", 2) + " AC";
    int cap = dexCap(a);
    int base = intVal(a, "baseAC", 10);
    if (cap == 0) return "AC " + base;
    return "AC " + base + " + DEX" + (cap == Integer.MAX_VALUE ? "" : " (max " + cap + ")");
}

static Spell toSpell(Map<String, Object> a) {
    Spell s = new Spell();
    s.name = str(a, "name");
    s.level = Math.max(0, Math.min(9, intVal(a, "level", 1)));
    s.school = str(a, "school");
    s.castingTime = str(a, "castingTime");
    s.range = str(a, "range");
    List<String> comps = list(a, "components");
    List<String> parts = new ArrayList<>();
    for (String c : new String[]{"V", "S", "M"}) {
        if (!comps.contains(c)) continue;
        String material = str(a, "material").trim();
        parts.add("M".equals(c) && !material.isEmpty() ? "M (" + material + ")" : c);
    }
    s.components = String.join(", ", parts);
    s.concentration = bool(a, "concentration");
    s.ritual = bool(a, "ritual");
    String duration = str(a, "duration").trim();
    if (duration.isEmpty()) duration = "Instantaneous";
    s.duration = s.concentration && !duration.toLowerCase().startsWith("concentration") ? "Concentration, up to " + duration : duration;
    s.classes.addAll(list(a, "classes"));
    s.description = str(a, "description");
    s.higherLevels = str(a, "higherLevels");
    List<String> conds = list(a, "conditions");
    s.inflictedConditions = conds.isEmpty() ? SpellData.detectConditions(s.description) : conds;
    String img = str(a, "imagePath").trim();
    s.imagePath = img.isEmpty() ? null : img;
    return s;
}

private static String speciesInfoText(Map<String, Object> a) {
    StringBuilder sb = new StringBuilder(str(a, "description"));
    List<String> facts = new ArrayList<>();
    facts.add(str(a, "size") + " " + str(a, "creatureType"));
    facts.add("Speed " + intVal(a, "speed", 30) + " ft.");
    int dv = intVal(a, "darkvision", 0);
    if (dv > 0) facts.add("Darkvision " + dv + " ft.");
    List<String> langs = speciesLanguageList(a);
    if (!langs.isEmpty()) facts.add("Languages: " + String.join(", ", langs));
    List<String> traits = new ArrayList<>();
    for (Map<String, Object> t : entries(a, "traits")) traits.add(str(t, "name"));
    if (!traits.isEmpty()) facts.add("Traits: " + String.join(", ", traits));
    if (sb.length() > 0) sb.append("\n\n");
    sb.append(String.join("\n", facts)).append("\n\nHomebrew species.");
    return sb.toString();
}

private static String backgroundInfoText(Map<String, Object> a) {
    StringBuilder sb = new StringBuilder(str(a, "description"));
    List<String> facts = new ArrayList<>();
    List<String> stats = list(a, "abilityScores");
    if (!stats.isEmpty()) facts.add("Ability scores: " + String.join(", ", stats));
    List<String> skills = list(a, "skillProficiencies");
    if (!skills.isEmpty()) facts.add("Skills: " + String.join(", ", skills));
    List<String> tools = list(a, "toolProficiencies");
    if (!tools.isEmpty()) facts.add("Tools: " + String.join(", ", tools));
    if (!str(a, "originFeat").isEmpty()) facts.add("Origin feat: " + str(a, "originFeat"));
    if (sb.length() > 0 && !facts.isEmpty()) sb.append("\n\n");
    sb.append(String.join("\n", facts)).append(sb.length() > 0 ? "\n\n" : "").append("Homebrew background.");
    return sb.toString();
}

private static String classInfoText(Map<String, Object> a) {
    StringBuilder sb = new StringBuilder(str(a, "description"));
    List<String> facts = new ArrayList<>();
    List<String> prim = list(a, "primaryAbilities");
    if (!prim.isEmpty()) facts.add("Primary abilities: " + String.join(", ", prim));
    List<String> saves = list(a, "savingThrows");
    if (!saves.isEmpty()) facts.add("Saving throws: " + String.join(", ", saves));
    String casting = str(a, "spellcasting");
    if (!casting.isEmpty() && !"None".equals(casting)) facts.add("Spellcasting: " + casting + " (" + str(a, "spellcastingAbility") + ")");
    if (sb.length() > 0 && !facts.isEmpty()) sb.append("\n\n");
    sb.append(String.join("\n", facts)).append(sb.length() > 0 ? "\n\n" : "").append("Homebrew class.");
    return sb.toString();
}

// ===============================================================
// Built-in (official) content, as it was before homebrew was merged in
// ===============================================================
private static void addKeys(List<String> out, Map<?, ?> table, String... skip) {
    List<String> skipList = Arrays.asList(skip);
    for (Object k : table.keySet()) {
        String n = String.valueOf(k);
        if (!skipList.contains(n) && !out.contains(n)) out.add(n);
    }
}

// Names of the built-in assets in a category (empty for NPCs, Maps and Subclasses)
static List<String> builtinNames(String cat) {
    List<String> out = new ArrayList<>();
    switch (cat) {
        case SPELLS: addKeys(out, snapshotOf(SpellData.SPELLS)); break;
        case FEATS:
            addKeys(out, snapshotOf(ORIGIN_FEATS));
            addKeys(out, snapshotOf(FeatData.GENERAL_FEATS));
            addKeys(out, snapshotOf(FeatData.EPIC_BOONS));
            break;
        case CLASSES: addKeys(out, snapshotOf(GameData.CLASS_INFO), "Custom"); break;
        case SPECIES: addKeys(out, snapshotOf(GameData.SPECIES_INFO), "Custom"); break;
        case BACKGROUNDS: addKeys(out, snapshotOf(GameData.BACKGROUND_INFO), "Custom"); break;
        case CONDITIONS: addKeys(out, snapshotOf(GameData.CONDITION_INFO), "Conscious", "Unconscious"); break;
        case ARMOR: addKeys(out, snapshotOf(GameData.ARMOR_AC)); break;
        case WEAPONS: for (WeaponEntry w : builtinWeapons()) if (!out.contains(w.name)) out.add(w.name); break;
        case ITEMS:
            for (String n : App.ITEM_PRESETS.keySet()) {
                if (builtinWeapon(n) == null && !snapshotOf(GameData.ARMOR_AC).containsKey(n)) out.add(n);
            }
            break;
        default:
    }
    return out;
}

private static List<WeaponEntry> builtinWeapons() {
    return weaponSnapshot != null ? weaponSnapshot : WeaponData.WEAPON_DB.weapons;
}

static WeaponEntry builtinWeapon(String name) {
    for (WeaponEntry w : builtinWeapons()) if (w.name.equalsIgnoreCase(name)) return w;
    return null;
}

static Spell builtinSpell(String name) {
    Object s = snapshotOf(SpellData.SPELLS).get(name);
    return s instanceof Spell ? (Spell) s : null;
}

// General feat or epic boon (null for an origin feat, whose text is builtinDescription)
static Feat builtinFeat(String name) {
    Object f = snapshotOf(FeatData.GENERAL_FEATS).get(name);
    if (f == null) f = snapshotOf(FeatData.EPIC_BOONS).get(name);
    return f instanceof Feat ? (Feat) f : null;
}

static String builtinFeatType(String name) {
    if (snapshotOf(ORIGIN_FEATS).containsKey(name)) return FEAT_ORIGIN;
    Feat f = builtinFeat(name);
    return f != null && FeatData.CATEGORY_EPIC.equals(f.category) ? FEAT_EPIC : FEAT_GENERAL;
}

private static String casterLabel(String cls) {
    Object t = snapshotOf(SpellData.CASTER_TYPE).get(cls);
    if (SpellData.FULL.equals(t)) return "Full";
    if (SpellData.HALF.equals(t)) return "Half";
    if (SpellData.PACT.equals(t)) return "Pact";
    return "None";
}

private static String armorCategory(GameData.ArmorInfo a) {
    if (a.isShield) return "Shield";
    if (a.maxDexBonus == Integer.MAX_VALUE) return "Light";
    return a.maxDexBonus > 0 ? "Medium" : "Heavy";
}

private static String armorAcText(GameData.ArmorInfo a) {
    if (a.isShield) return "+" + GameData.shieldBonus(a) + " AC";
    return "AC " + a.baseAC + (a.maxDexBonus <= 0 ? "" : a.maxDexBonus == Integer.MAX_VALUE ? " + DEX" : " + DEX (max " + a.maxDexBonus + ")");
}

// Value for the library's per-category filter (same buckets as filterValue for homebrew), or null
static String builtinFilterValue(String cat, String name) {
    switch (cat) {
        case SPELLS: {
            Spell s = builtinSpell(name);
            return s == null ? null : s.level == 0 ? "Cantrip" : "Level " + s.level;
        }
        case FEATS: return builtinFeatType(name);
        case ITEMS: return "Gear";
        case WEAPONS: {
            WeaponEntry w = builtinWeapon(name);
            return w == null || w.category == null ? "—" : w.category.split(" ")[0];
        }
        case ARMOR: {
            Object a = snapshotOf(GameData.ARMOR_AC).get(name);
            return a instanceof GameData.ArmorInfo ? armorCategory((GameData.ArmorInfo) a) : "—";
        }
        case SPECIES: return GameData.computeSize(name);
        case CLASSES: return casterLabel(name);
        default: return null;
    }
}

// One-line summary for a built-in asset's list row
static String builtinSummary(String cat, String name) {
    List<String> p = new ArrayList<>();
    switch (cat) {
        case SPELLS: {
            Spell s = builtinSpell(name);
            if (s == null) break;
            p.add(s.levelLine());
            if (s.concentration) p.add("Concentration");
            if (s.ritual) p.add("Ritual");
            if (!s.classes.isEmpty()) p.add(String.join(", ", s.classes));
            break;
        }
        case FEATS: {
            String type = builtinFeatType(name);
            p.add(FEAT_EPIC.equals(type) ? "Epic Boon" : type + " feat");
            Feat f = builtinFeat(name);
            if (f != null && f.statOptions.length > 0) p.add("+1 " + String.join("/", f.statOptions));
            if (f != null && f.repeatable) p.add("Repeatable");
            break;
        }
        case CLASSES: {
            Object info = snapshotOf(GameData.CLASS_INFO).get(name);
            if (info instanceof String[]) p.add("Hit die " + ((String[]) info)[0]);
            String c = casterLabel(name);
            p.add("None".equals(c) ? "No spellcasting" : c + " caster");
            break;
        }
        case SPECIES:
            p.add(GameData.computeSize(name));
            p.add(GameData.computeSpeed(name, "") + " ft.");
            if (GameData.SPECIES_LINEAGES.containsKey(name)) p.add(GameData.SPECIES_LINEAGES.get(name).length + " lineages");
            break;
        case BACKGROUNDS: {
            Object skills = snapshotOf(GameData.BACKGROUND_SKILL_PROFICIENCIES).get(name);
            if (skills instanceof String[]) p.add("Skills: " + String.join(", ", (String[]) skills));
            Object stats = snapshotOf(GameData.BACKGROUND_STATS).get(name);
            if (stats instanceof String[]) p.add(String.join(", ", (String[]) stats));
            break;
        }
        case CONDITIONS:
            p.add(snippet(String.valueOf(snapshotOf(GameData.CONDITION_INFO).get(name)), 90));
            break;
        case ARMOR: {
            Object a = snapshotOf(GameData.ARMOR_AC).get(name);
            if (a instanceof GameData.ArmorInfo) {
                GameData.ArmorInfo ai = (GameData.ArmorInfo) a;
                p.add(ai.isShield ? "Shield" : armorCategory(ai) + " armor");
                p.add(armorAcText(ai));
            }
            break;
        }
        case WEAPONS: {
            WeaponEntry w = builtinWeapon(name);
            if (w != null) {
                p.add(w.category);
                p.add(w.damage);
                if (w.mastery != null && !"—".equals(w.mastery)) p.add("Mastery: " + w.mastery);
            }
            break;
        }
        case ITEMS:
            p.add(snippet(App.ITEM_PRESETS.getOrDefault(name, "").replace(" Placeholder description.", ""), 90));
            break;
        default:
    }
    p.removeIf(s -> s == null || s.trim().isEmpty() || "null".equals(s));
    return String.join(" · ", p);
}

// Full rules text for a built-in asset's detail overlay
static String builtinDescription(String cat, String name) {
    switch (cat) {
        case SPELLS: {
            Spell s = builtinSpell(name);
            return s == null ? "" : s.infoText();
        }
        case FEATS: {
            Feat f = builtinFeat(name);
            if (f != null) return f.infoText();
            Object d = snapshotOf(ORIGIN_FEATS).get(name);
            return d == null ? "" : d + "\n\nOrigin feat (granted by a background).";
        }
        case CLASSES: {
            Object info = snapshotOf(GameData.CLASS_INFO).get(name);
            if (!(info instanceof String[])) return "";
            StringBuilder sb = new StringBuilder(((String[]) info)[1]).append("\n\nHit die: ").append(((String[]) info)[0]);
            Object saves = snapshotOf(GameData.CLASS_SAVING_THROWS).get(name);
            if (saves instanceof String[]) sb.append("\nSaving throws: ").append(String.join(", ", (String[]) saves));
            String c = casterLabel(name);
            if (!"None".equals(c)) sb.append("\nSpellcasting: ").append(c).append(" caster (").append(snapshotOf(SpellData.CASTING_ABILITY).get(name)).append(")");
            return sb.toString();
        }
        case SPECIES: {
            StringBuilder sb = new StringBuilder(String.valueOf(snapshotOf(GameData.SPECIES_INFO).get(name)));
            sb.append("\n\nSize: ").append(GameData.computeSize(name)).append("\nSpeed: ").append(GameData.computeSpeed(name, "")).append(" ft.");
            String[] lin = GameData.SPECIES_LINEAGES.get(name);
            if (lin != null) sb.append("\nLineages: ").append(String.join(", ", lin));
            return sb.toString();
        }
        case BACKGROUNDS: {
            StringBuilder sb = new StringBuilder(String.valueOf(snapshotOf(GameData.BACKGROUND_INFO).get(name)));
            Object skills = snapshotOf(GameData.BACKGROUND_SKILL_PROFICIENCIES).get(name);
            if (skills instanceof String[]) sb.append("\n\nSkill proficiencies: ").append(String.join(", ", (String[]) skills));
            Object stats = snapshotOf(GameData.BACKGROUND_STATS).get(name);
            if (stats instanceof String[]) sb.append("\nAbility scores: ").append(String.join(", ", (String[]) stats));
            return sb.toString();
        }
        case CONDITIONS:
            return String.valueOf(snapshotOf(GameData.CONDITION_INFO).get(name));
        case ARMOR: {
            Object a = snapshotOf(GameData.ARMOR_AC).get(name);
            if (!(a instanceof GameData.ArmorInfo)) return "";
            GameData.ArmorInfo ai = (GameData.ArmorInfo) a;
            return (ai.isShield ? "Shield" : armorCategory(ai) + " armor") + ". " + armorAcText(ai) + ".";
        }
        case WEAPONS: {
            WeaponEntry w = builtinWeapon(name);
            if (w == null) return "";
            return w.category + " weapon.\nDamage: " + w.damage
                    + "\nProperties: " + (w.properties.isEmpty() ? "—" : String.join(", ", w.properties))
                    + "\nMastery: " + w.mastery + "\nWeight: " + w.weight + "   Cost: " + w.cost;
        }
        case ITEMS:
            return App.ITEM_PRESETS.getOrDefault(name, "").replace(" Placeholder description.", "");
        default:
            return "";
    }
}

// Deleting a custom tag strips it from every homebrew asset
static void removeTagFromAssets(String tag) {
    for (String c : CATEGORIES) {
        for (Map<String, Object> a : DATA.get(c)) {
            Object t = a.get("tags");
            if (t instanceof List) ((List<?>) t).removeIf(x -> String.valueOf(x).equalsIgnoreCase(tag));
        }
    }
    save();
}

// ===============================================================
// Lookups used by GameData / SpellData / App / LevelUpPanel
// ===============================================================
static Map<String, Object> speciesAsset(String name) { return findByName(SPECIES, name); }
static Map<String, Object> classAsset(String name) { return findByName(CLASSES, name); }
static Map<String, Object> backgroundAsset(String name) { return findByName(BACKGROUNDS, name); }

static List<Map<String, Object>> subclassesFor(String cls) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Map<String, Object> a : DATA.get(SUBCLASSES)) if (cls.equals(str(a, "parentClass"))) out.add(a);
    return out;
}

static Map<String, Object> subclassAsset(String cls, String name) {
    for (Map<String, Object> a : subclassesFor(cls)) if (str(a, "name").equals(name)) return a;
    return null;
}

// Feature entries gained at exactly `level` (class or subclass asset)
static List<Map<String, Object>> featuresAt(Map<String, Object> asset, int level) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Map<String, Object> f : entries(asset, "features")) if (intVal(f, "level", 1) == level) out.add(f);
    return out;
}

static List<Map<String, Object>> featuresUpTo(Map<String, Object> asset, int level) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Map<String, Object> f : entries(asset, "features")) if (intVal(f, "level", 1) <= level) out.add(f);
    out.sort(Comparator.comparingInt(f -> intVal(f, "level", 1)));
    return out;
}

// Class whose spell list a homebrew class uses (itself unless it borrows one)
static String spellListFor(String cls) {
    Map<String, Object> a = classAsset(cls);
    if (a == null) return cls;
    String from = str(a, "spellListFrom");
    return from.isEmpty() || OWN_SPELL_LIST.equals(from) ? cls : from;
}

static List<String> speciesLanguageList(Map<String, Object> a) {
    List<String> out = new ArrayList<>(list(a, "languages"));
    for (String l : list(a, "extraLanguages")) if (!out.contains(l)) out.add(l);
    return out;
}

// The character's homebrew subclass picks: {className: subclassName}
static Map<String, String> recSubclasses(Map<?, ?> rec) {
    Map<String, String> out = new LinkedHashMap<>();
    Object v = rec.get("subclasses");
    if (v instanceof Map) {
        for (Map.Entry<?, ?> en : ((Map<?, ?>) v).entrySet()) out.put(String.valueOf(en.getKey()), String.valueOf(en.getValue()));
    }
    return out;
}

// Feature cards for the sheet: {title, subtitle, description}
static List<String[]> sheetFeatures(Map<?, ?> rec) {
    List<String[]> out = new ArrayList<>();
    String speciesName = CharacterStorage.recStr(rec, "species");
    Map<String, Object> sp = speciesAsset(speciesName);
    if (sp != null) {
        int dv = intVal(sp, "darkvision", 0);
        if (dv > 0) out.add(new String[]{"Darkvision", speciesName + " trait · " + dv + " ft.",
                "You can see in Dim Light within " + dv + " feet as if it were Bright Light, and in Darkness as if it were Dim Light. You discern colors in Darkness only as shades of gray."});
        for (Map<String, Object> t : entries(sp, "traits")) {
            out.add(new String[]{str(t, "name"), speciesName + " trait", str(t, "description")});
        }
    }
    String bgName = CharacterStorage.recStr(rec, "background");
    Map<String, Object> bg = backgroundAsset(bgName);
    if (bg != null && !str(bg, "originFeat").isEmpty()) {
        String desc = str(bg, "originFeatDescription").trim();
        if (desc.isEmpty()) desc = featDescription(str(bg, "originFeat"));
        out.add(new String[]{str(bg, "originFeat"), "Origin feat · " + bgName + " background", desc});
    }
    Map<String, String> subs = recSubclasses(rec);
    for (Map.Entry<String, Integer> en : CharacterStorage.recClassLevels(rec).entrySet()) {
        Map<String, Object> ca = classAsset(en.getKey());
        if (ca != null) {
            for (Map<String, Object> f : featuresUpTo(ca, en.getValue())) {
                out.add(new String[]{str(f, "name"), en.getKey() + " " + intVal(f, "level", 1), str(f, "description")});
            }
        }
        String subName = subs.get(en.getKey());
        Map<String, Object> sa = subName == null ? null : subclassAsset(en.getKey(), subName);
        if (sa != null) {
            out.add(new String[]{subName, en.getKey() + " subclass", str(sa, "description")});
            for (Map<String, Object> f : featuresUpTo(sa, en.getValue())) {
                out.add(new String[]{str(f, "name"), subName + " " + intVal(f, "level", 1), str(f, "description")});
            }
        }
    }
    return out;
}

// Extra proficiency lines for the sheet: {label, value, explanation}
static List<String[]> sheetProficiencies(Map<?, ?> rec) {
    List<String[]> out = new ArrayList<>();
    Map<String, Object> sp = speciesAsset(CharacterStorage.recStr(rec, "species"));
    if (sp != null && intVal(sp, "darkvision", 0) > 0) {
        out.add(new String[]{"Darkvision", intVal(sp, "darkvision", 0) + " ft.",
                "Granted by your " + str(sp, "name") + " species. See in dim light as if bright, and in darkness as if dim (shades of gray only)."});
    }
    List<String> armor = new ArrayList<>(), weapons = new ArrayList<>(), tools = new ArrayList<>();
    List<String> armorSrc = new ArrayList<>(), weaponSrc = new ArrayList<>(), toolSrc = new ArrayList<>();
    String primary = CharacterStorage.recStr(rec, "characterClass");
    Map<String, Object> ca = classAsset(primary);
    if (ca != null) {
        addAllNew(armor, list(ca, "armorTraining"), armorSrc, primary + " class");
        addAllNew(weapons, list(ca, "weaponProficiencies"), weaponSrc, primary + " class");
        addAllNew(tools, list(ca, "toolProficiencies"), toolSrc, primary + " class");
    }
    String bgName = CharacterStorage.recStr(rec, "background");
    Map<String, Object> bg = backgroundAsset(bgName);
    if (bg != null) addAllNew(tools, list(bg, "toolProficiencies"), toolSrc, bgName + " background");
    if (!armor.isEmpty()) out.add(new String[]{"Armor Training", String.join(", ", armor),
            "You can wear this armor without penalty. From: " + String.join(", ", armorSrc) + "."});
    if (!weapons.isEmpty()) out.add(new String[]{"Weapons", String.join(", ", weapons),
            "Add your Proficiency Bonus to attack rolls with these weapons. From: " + String.join(", ", weaponSrc) + "."});
    if (!tools.isEmpty()) out.add(new String[]{"Tools", String.join(", ", tools),
            "Add your Proficiency Bonus to ability checks made with these tools. From: " + String.join(", ", toolSrc) + "."});
    return out;
}

private static void addAllNew(List<String> target, List<String> items, List<String> sources, String source) {
    boolean any = false;
    for (String s : items) if (!target.contains(s)) { target.add(s); any = true; }
    if (any && !sources.contains(source)) sources.add(source);
}

// Description for an equipment entry if it names a homebrew item, weapon or armor (else null)
static String itemDescription(String base) {
    for (String cat : new String[]{ITEMS, WEAPONS, ARMOR}) {
        for (Map<String, Object> a : DATA.get(cat)) {
            String n = str(a, "name");
            if (n.equalsIgnoreCase(base) || (n + "s").equalsIgnoreCase(base)) return equipmentText(cat, a);
        }
    }
    return null;
}

static String equipmentText(String cat, Map<String, Object> a) {
    StringBuilder sb = new StringBuilder(summary(cat, a)).append(".");
    if (WEAPONS.equals(cat)) {
        List<String> props = weaponPropertyLabels(a);
        sb.append("\nDamage: ").append(str(a, "damageDice")).append(" ").append(str(a, "damageType").toLowerCase());
        sb.append("\nProperties: ").append(props.isEmpty() ? "—" : String.join(", ", props));
        if (!str(a, "mastery").isEmpty() && !"None".equals(str(a, "mastery"))) sb.append("\nMastery: ").append(str(a, "mastery"));
    } else if (ARMOR.equals(cat)) {
        if (intVal(a, "strRequirement", 0) > 0) sb.append("\nRequires STR ").append(intVal(a, "strRequirement", 0));
        if (bool(a, "stealthDisadvantage")) sb.append("\nDisadvantage on Stealth checks");
    }
    List<String> wv = new ArrayList<>();
    if (!str(a, "weight").isEmpty()) wv.add("Weight: " + str(a, "weight"));
    if (!str(a, "value").isEmpty()) wv.add("Value: " + str(a, "value"));
    if (!wv.isEmpty()) sb.append("\n").append(String.join("   ", wv));
    if (!str(a, "description").isEmpty()) sb.append("\n\n").append(str(a, "description"));
    sb.append("\n\nHomebrew ").append(noun(cat, false)).append(".");
    return sb.toString();
}

// {name, one-line description} for every homebrew item/weapon/armor (Add Equipment picker)
static List<String[]> equipmentPresets() {
    List<String[]> out = new ArrayList<>();
    for (String cat : new String[]{ITEMS, WEAPONS, ARMOR}) {
        for (Map<String, Object> a : DATA.get(cat)) out.add(new String[]{str(a, "name"), summary(cat, a)});
    }
    return out;
}

static List<String> conditionNames() {
    List<String> out = new ArrayList<>();
    for (Map<String, Object> a : DATA.get(CONDITIONS)) out.add(str(a, "name"));
    return out;
}

// ===============================================================
// One-line summaries for list rows
// ===============================================================
static String summary(String cat, Map<String, Object> a) {
    List<String> p = new ArrayList<>();
    switch (cat) {
        case ITEMS:
            p.add(str(a, "category"));
            p.add(str(a, "rarity"));
            if (bool(a, "attunement")) p.add("Requires attunement");
            break;
        case WEAPONS:
            p.add(str(a, "weaponCategory") + " " + str(a, "weaponType"));
            p.add(str(a, "damageDice") + " " + str(a, "damageType").toLowerCase());
            if (!"Mundane".equals(str(a, "rarity"))) p.add(str(a, "rarity"));
            if (bool(a, "attunement")) p.add("Attunement");
            break;
        case ARMOR:
            p.add("Shield".equals(str(a, "armorCategory")) ? "Shield" : str(a, "armorCategory") + " armor");
            p.add(acText(a));
            if (!"Mundane".equals(str(a, "rarity"))) p.add(str(a, "rarity"));
            break;
        case NPCS:
            if (!str(a, "role").isEmpty()) p.add(str(a, "role"));
            p.add((str(a, "size") + " " + str(a, "creatureType")).trim());
            p.add("CR " + str(a, "cr"));
            break;
        case MAPS: {
            MapEditorPanel.MapModel m = MapEditorPanel.MapModel.fromJson(a.get("grid"));
            p.add(m.width + " × " + m.height + " squares (" + m.width * 5 + " × " + m.height * 5 + " ft.)");
            p.add(m.pins.size() + (m.pins.size() == 1 ? " pin" : " pins"));
            break;
        }
        case CONDITIONS:
            p.add(snippet(str(a, "description"), 90));
            break;
        case SPELLS: {
            int lv = intVal(a, "level", 1);
            p.add(lv == 0 ? str(a, "school") + " cantrip" : "Level " + lv + " " + str(a, "school"));
            if (bool(a, "concentration")) p.add("Concentration");
            if (bool(a, "ritual")) p.add("Ritual");
            List<String> cls = list(a, "classes");
            if (!cls.isEmpty()) p.add(String.join(", ", cls));
            break;
        }
        case BACKGROUNDS: {
            List<String> skills = list(a, "skillProficiencies");
            if (!skills.isEmpty()) p.add("Skills: " + String.join(", ", skills));
            if (!str(a, "originFeat").isEmpty()) p.add("Feat: " + str(a, "originFeat"));
            break;
        }
        case SPECIES:
            p.add(str(a, "size"));
            p.add(intVal(a, "speed", 30) + " ft.");
            if (intVal(a, "darkvision", 0) > 0) p.add("Darkvision " + intVal(a, "darkvision", 0) + " ft.");
            p.add(plural(entries(a, "traits").size(), "trait"));
            break;
        case CLASSES: {
            p.add("Hit die " + str(a, "hitDie"));
            String casting = str(a, "spellcasting");
            p.add("None".equals(casting) || casting.isEmpty() ? "No spellcasting" : casting + " caster");
            p.add(plural(entries(a, "features").size(), "feature"));
            break;
        }
        case FEATS: {
            String type = str(a, "featType").isEmpty() ? FEAT_GENERAL : str(a, "featType");
            p.add(FEAT_EPIC.equals(type) ? "Epic Boon" : type + " feat");
            List<String> st = list(a, "abilityOptions");
            if (!st.isEmpty()) p.add("+1 " + String.join("/", st));
            if (bool(a, "repeatable")) p.add("Repeatable");
            break;
        }
        case SUBCLASSES:
            p.add(str(a, "parentClass") + " subclass");
            p.add("from level " + intVal(a, "subclassLevel", 3));
            p.add(plural(entries(a, "features").size(), "feature"));
            break;
        default:
    }
    p.removeIf(s -> s == null || s.trim().isEmpty());
    return String.join(" · ", p);
}

// Category name for use mid-sentence ("weapon", "spells") without lowercasing the NPC acronym
static String noun(String cat, boolean pluralForm) {
    String s = pluralForm ? cat : SINGULAR.get(cat);
    return NPCS.equals(cat) ? s : s.toLowerCase();
}

static String plural(int n, String noun) {
    return n + " " + noun + (n == 1 ? "" : "s");
}

static String snippet(String text, int max) {
    String t = text.replace('\n', ' ').trim();
    return t.length() <= max ? t : t.substring(0, max - 1).trim() + "…";
}
}
