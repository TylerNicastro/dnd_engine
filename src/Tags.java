import java.util.*;
import java.util.List;

// Tags on every asset (built-in rules data and homebrew), plus the campaign's content filters.
//
//  - Built-in assets get "OFFICIAL" + their sourcebook tag by default (sourceOf below). Users can change
//    those tags in the Asset Library; the changes are stored as overrides keyed "Category|Name".
//  - Homebrew assets keep their tags in their own "tags" field (CustomAssets.TAGS), "HOMEBREW" by default.
//  - Spells also get derived tags that are never stored: school ("Evocation"), level ("Cantrip",
//    "3rd-Level") and class list ("Wizard Spell").
//  - A disabled tag hides every asset carrying it from the character wizard, level-up pickers and the
//    equipment / condition pickers. Characters keep anything they already have (see OWNED).
//
// Settings are saved inside custom_assets.json under "tagSettings" (so web backups carry them).
class Tags {

static final String OFFICIAL = "OFFICIAL", HOMEBREW = "HOMEBREW", CORE = "CORE RULES",
        XGTE = "XANATHAR'S GUIDE", TCOE = "TASHA'S CAULDRON", MOTM = "MONSTERS OF THE MULTIVERSE", EBERRON = "EBERRON";
static final List<String> SOURCE_TAGS = Collections.unmodifiableList(Arrays.asList(OFFICIAL, HOMEBREW, CORE, XGTE, TCOE, MOTM, EBERRON));
static final List<String> BOOK_TAGS = Collections.unmodifiableList(Arrays.asList(CORE, XGTE, TCOE, MOTM, EBERRON));

static final String[] LEVEL_TAGS = {"Cantrip", "1st-Level", "2nd-Level", "3rd-Level", "4th-Level", "5th-Level",
        "6th-Level", "7th-Level", "8th-Level", "9th-Level"};
static final String CLASS_SPELL_SUFFIX = " Spell";

private static final Comparator<String> CI = String.CASE_INSENSITIVE_ORDER;
private static final Set<String> DISABLED = new TreeSet<>(CI);
private static final Set<String> CUSTOM = new TreeSet<>(CI);
private static final Map<String, List<String>> OVERRIDES = new LinkedHashMap<>(); // "cat|name" -> tags

// Names a character already owns while a picker is open: always shown even if their tags are disabled
static final Set<String> OWNED = new HashSet<>();

// ===============================================================
// Default sources for built-in content
// ===============================================================
// Built-in content is from the 2024 core rules unless listed here (by original sourcebook)
private static final Map<String, String> SOURCES = new HashMap<>();
static {
    for (String s : new String[]{"Thunderclap", "Toll the Dead", "Word of Radiance", "Ice Knife", "Synaptic Static",
            "Steel Wind Strike", "Charm Monster"}) SOURCES.put(key(CustomAssets.SPELLS, s), XGTE);
    SOURCES.put(key(CustomAssets.SPELLS, "Mind Sliver"), TCOE);
    for (String f : new String[]{"Chef", "Crusher", "Fey-Touched", "Piercer", "Poisoner", "Shadow-Touched", "Skill Expert",
            "Slasher", "Telekinetic", "Telepathic"}) SOURCES.put(key(CustomAssets.FEATS, f), TCOE);
    SOURCES.put(key(CustomAssets.CLASSES, "Artificer"), TCOE);
    SOURCES.put(key(CustomAssets.SPECIES, "Tabaxi"), MOTM);
    SOURCES.put(key(CustomAssets.SPECIES, "Changeling"), MOTM);
    SOURCES.put(key(CustomAssets.SPECIES, "Warforged"), EBERRON);
}

static String key(String cat, String name) {
    return cat + "|" + name;
}

static String sourceOf(String cat, String name) {
    return SOURCES.getOrDefault(key(cat, name), CORE);
}

static List<String> defaultBuiltinTags(String cat, String name) {
    return new ArrayList<>(Arrays.asList(OFFICIAL, sourceOf(cat, name)));
}

// ===============================================================
// Reading tags
// ===============================================================
// Editable tags of a built-in asset (defaults unless the user changed them)
static List<String> builtinTags(String cat, String name) {
    List<String> o = OVERRIDES.get(key(cat, name));
    return o != null ? new ArrayList<>(o) : defaultBuiltinTags(cat, name);
}

static boolean hasOverride(String cat, String name) {
    return OVERRIDES.containsKey(key(cat, name));
}

// Stored tags of a homebrew asset
static List<String> assetTags(Map<String, Object> asset) {
    if (asset == null) return new ArrayList<>();
    if (!(asset.get("tags") instanceof List)) return new ArrayList<>(Collections.singletonList(HOMEBREW));
    return dedupe(CustomAssets.list(asset, "tags"));
}

// Auto-derived tags (spells only): school, level, class lists
static List<String> derivedSpellTags(int level, String school, Collection<String> classes) {
    List<String> out = new ArrayList<>();
    if (school != null && !school.trim().isEmpty()) out.add(school.trim());
    out.add(LEVEL_TAGS[Math.max(0, Math.min(9, level))]);
    for (String c : classes) if (c != null && !c.trim().isEmpty()) out.add(c.trim() + CLASS_SPELL_SUFFIX);
    return out;
}

static List<String> derivedTags(String cat, String name, Map<String, Object> asset) {
    if (!CustomAssets.SPELLS.equals(cat)) return new ArrayList<>();
    if (asset != null) {
        return derivedSpellTags(CustomAssets.intVal(asset, "level", 1), CustomAssets.str(asset, "school"), CustomAssets.list(asset, "classes"));
    }
    Spell s = CustomAssets.builtinSpell(name);
    return s == null ? new ArrayList<>() : derivedSpellTags(s.level, s.school, s.classes);
}

// Every tag on a homebrew asset (stored + derived)
static List<String> tagsOfAsset(String cat, Map<String, Object> asset) {
    List<String> out = assetTags(asset);
    out.addAll(derivedTags(cat, CustomAssets.str(asset, "name"), asset));
    return dedupe(out);
}

// Every tag on a built-in asset (stored + derived)
static List<String> tagsOfBuiltin(String cat, String name) {
    List<String> out = builtinTags(cat, name);
    out.addAll(derivedTags(cat, name, null));
    return dedupe(out);
}

// Effective tags for a name as the rules tables see it: a homebrew asset with that name wins
static List<String> tagsOf(String cat, String name) {
    Map<String, Object> hb = CustomAssets.findByName(cat, name);
    return hb != null ? tagsOfAsset(cat, hb) : tagsOfBuiltin(cat, name);
}

// ===============================================================
// Content filters
// ===============================================================
static boolean isEnabled(String tag) {
    return !DISABLED.contains(tag);
}

static void setEnabled(String tag, boolean on) {
    if (on) DISABLED.remove(tag);
    else DISABLED.add(tag);
    CustomAssets.save();
}

static List<String> disabledTags() {
    return new ArrayList<>(DISABLED);
}

static boolean anyDisabled(Collection<String> tags) {
    if (DISABLED.isEmpty()) return false;
    for (String t : tags) if (DISABLED.contains(t)) return true;
    return false;
}

// Should a picker offer this asset? The wizard's manual "Custom" entries and owned picks always show.
static boolean allowed(String cat, String name) {
    if (name == null || DISABLED.isEmpty() || "Custom".equals(name) || OWNED.contains(name)) return true;
    return !anyDisabled(tagsOf(cat, name));
}

static boolean allowedAsset(String cat, Map<String, Object> asset) {
    if (DISABLED.isEmpty() || OWNED.contains(CustomAssets.str(asset, "name"))) return true;
    return !anyDisabled(tagsOfAsset(cat, asset));
}

static List<String> visible(String cat, Collection<String> names) {
    List<String> out = new ArrayList<>();
    for (String n : names) if (allowed(cat, n)) out.add(n);
    return out;
}

static List<Spell> visibleSpells(List<Spell> spells) {
    if (DISABLED.isEmpty()) return spells;
    List<Spell> out = new ArrayList<>();
    for (Spell s : spells) if (allowed(CustomAssets.SPELLS, s.name)) out.add(s);
    return out;
}

static void setOwned(Collection<String> names) {
    OWNED.clear();
    if (names != null) for (String n : names) if (n != null) OWNED.add(GameData.stripEquipmentCount(n));
}

// ===============================================================
// Editing tags
// ===============================================================
static void setBuiltinTags(String cat, String name, List<String> tags) {
    List<String> clean = dedupe(tags);
    clean.removeIf(Tags::isDerived);
    if (sameTags(clean, defaultBuiltinTags(cat, name))) OVERRIDES.remove(key(cat, name));
    else OVERRIDES.put(key(cat, name), clean);
    for (String t : clean) registerIfCustom(t);
    CustomAssets.save();
}

static void resetBuiltinTags(String cat, String name) {
    OVERRIDES.remove(key(cat, name));
    CustomAssets.save();
}

// Normalizes a tag typed by the user; returns null if it can't be used
static String cleanName(String raw) {
    if (raw == null) return null;
    String t = raw.trim().replaceAll("\\s+", " ");
    if (t.isEmpty() || t.length() > 40 || t.contains("|")) return null;
    String existing = canonical(t);
    return existing != null ? existing : t;
}

// Returns an error message, or null when the tag was created
static String createTag(String raw) {
    String t = cleanName(raw);
    if (t == null) return "Tag names need 1-40 characters (no \"|\").";
    if (isReserved(t)) return "\"" + t + "\" is a built-in tag; it already exists.";
    CUSTOM.add(t);
    CustomAssets.save();
    return null;
}

// Removes a custom tag everywhere: the tag list, homebrew assets, built-in overrides and the filters
static void deleteTag(String tag) {
    if (isReserved(tag)) return;
    CUSTOM.remove(tag);
    DISABLED.remove(tag);
    for (Map.Entry<String, List<String>> en : new ArrayList<>(OVERRIDES.entrySet())) {
        en.getValue().removeIf(t -> t.equalsIgnoreCase(tag));
        String[] parts = en.getKey().split("\\|", 2);
        if (parts.length == 2 && sameTags(en.getValue(), defaultBuiltinTags(parts[0], parts[1]))) OVERRIDES.remove(en.getKey());
    }
    CustomAssets.removeTagFromAssets(tag); // saves
}

private static void registerIfCustom(String t) {
    if (!isReserved(t)) CUSTOM.add(t);
}

// ===============================================================
// Tag universe & descriptions
// ===============================================================
static boolean isSource(String tag) {
    for (String s : SOURCE_TAGS) if (s.equalsIgnoreCase(tag)) return true;
    return false;
}

static boolean isSchool(String tag) {
    for (String s : CustomAssets.SCHOOLS) if (s.equalsIgnoreCase(tag)) return true;
    return false;
}

static int levelOfTag(String tag) {
    for (int i = 0; i < LEVEL_TAGS.length; i++) if (LEVEL_TAGS[i].equalsIgnoreCase(tag)) return i;
    return -1;
}

static String classOfTag(String tag) {
    if (tag == null || !tag.toLowerCase().endsWith(CLASS_SPELL_SUFFIX.toLowerCase())) return null;
    String c = tag.substring(0, tag.length() - CLASS_SPELL_SUFFIX.length()).trim();
    return c.isEmpty() ? null : c;
}

static boolean isDerived(String tag) {
    return isSchool(tag) || levelOfTag(tag) >= 0 || classOfTag(tag) != null;
}

static boolean isReserved(String tag) {
    return isSource(tag) || isDerived(tag);
}

static boolean isCustom(String tag) {
    return !isReserved(tag);
}

// Existing spelling of a tag (case-insensitive), or null
static String canonical(String t) {
    for (String s : allTags()) if (s.equalsIgnoreCase(t)) return s;
    return null;
}

static List<String> customTags() {
    return new ArrayList<>(CUSTOM);
}

// Source tags + custom tags + any tag found on an asset (imports can bring new ones). No derived tags.
static List<String> allTags() {
    LinkedHashSet<String> out = new LinkedHashSet<>(SOURCE_TAGS);
    TreeSet<String> extra = new TreeSet<>(CI);
    extra.addAll(CUSTOM);
    for (List<String> l : OVERRIDES.values()) extra.addAll(l);
    for (String cat : CustomAssets.CATEGORIES) for (Map<String, Object> a : CustomAssets.all(cat)) extra.addAll(assetTags(a));
    for (String t : extra) if (!containsCI(out, t) && !isDerived(t)) out.add(t);
    return new ArrayList<>(out);
}

static List<String> derivedTagUniverse() {
    List<String> out = new ArrayList<>(Arrays.asList(CustomAssets.SCHOOLS));
    out.addAll(Arrays.asList(LEVEL_TAGS));
    for (String c : spellClasses()) out.add(c + CLASS_SPELL_SUFFIX);
    return out;
}

// Classes that have at least one spell on their list
static List<String> spellClasses() {
    TreeSet<String> out = new TreeSet<>(CI);
    for (Spell s : SpellData.SPELLS.values()) out.addAll(s.classes);
    return new ArrayList<>(out);
}

// How many assets (built-in + homebrew) carry each tag, in one pass (case-insensitive keys)
static Map<String, Integer> counts() {
    Map<String, Integer> out = new TreeMap<>(CI);
    for (String cat : CustomAssets.CATEGORIES) {
        for (String name : CustomAssets.builtinNames(cat)) for (String t : tagsOfBuiltin(cat, name)) out.merge(t, 1, Integer::sum);
        for (Map<String, Object> a : CustomAssets.all(cat)) for (String t : tagsOfAsset(cat, a)) out.merge(t, 1, Integer::sum);
    }
    return out;
}

static String kind(String tag) {
    if (OFFICIAL.equalsIgnoreCase(tag) || HOMEBREW.equalsIgnoreCase(tag)) return "Source tag";
    if (isSource(tag)) return "Sourcebook tag";
    if (isSchool(tag)) return "Spell school tag";
    if (levelOfTag(tag) >= 0) return "Spell level tag";
    if (classOfTag(tag) != null) return "Class spell list tag";
    return "Custom tag";
}

static String describe(String tag) {
    String what;
    if (OFFICIAL.equalsIgnoreCase(tag)) {
        what = "Official D&D content. Every built-in spell, feat, class, species, background, condition and piece of equipment starts with this tag.";
    } else if (HOMEBREW.equalsIgnoreCase(tag)) {
        what = "Content created in the Asset Library (or imported from a homebrew pack). New homebrew assets get this tag by default.";
    } else if (CORE.equalsIgnoreCase(tag)) {
        what = "From the core rulebooks (2024 Player's Handbook rules). Built-in content that isn't credited to another book carries this tag.";
    } else if (XGTE.equalsIgnoreCase(tag)) {
        what = "First published in Xanathar's Guide to Everything (e.g. Toll the Dead, Ice Knife, Synaptic Static). Several were reprinted in the 2024 core rules.";
    } else if (TCOE.equalsIgnoreCase(tag)) {
        what = "First published in Tasha's Cauldron of Everything (e.g. the Artificer class, Mind Sliver, Fey-Touched and Telekinetic). Several were reprinted in the 2024 core rules.";
    } else if (MOTM.equalsIgnoreCase(tag)) {
        what = "From Mordenkainen Presents: Monsters of the Multiverse (e.g. the Tabaxi and Changeling species).";
    } else if (EBERRON.equalsIgnoreCase(tag)) {
        what = "From Eberron: Rising from the Last War (e.g. the Warforged species).";
    } else if (isSchool(tag)) {
        what = "Spells of the " + tag + " school. Added automatically from each spell's school.";
    } else if (levelOfTag(tag) >= 0) {
        what = (levelOfTag(tag) == 0 ? "Cantrips (level 0 spells)" : tag.replace("-Level", "") + "-level spells")
                + ". Added automatically from each spell's level.";
    } else if (classOfTag(tag) != null) {
        what = "Spells on the " + classOfTag(tag) + " spell list. Added automatically from each spell's class lists.";
    } else {
        what = "A custom tag you created. Add it to built-in or homebrew assets in the Asset Library to group them.";
    }
    String use = "Tags are used to search and sort the Asset Library and as campaign content filters: turn a tag off in "
            + "Tags & Filters to hide everything carrying it from character creation, level up and the equipment pickers. "
            + "Characters keep anything they already have.";
    String where = isDerived(tag) ? "Derived from the spell's own data (can't be removed by hand)."
            : isSource(tag) ? "A built-in tag (can't be deleted, but you can add or remove it on any asset)."
            : "Created by you; delete it in Tags & Filters to remove it from every asset.";
    return what + "\n\n" + use + "\n\n" + where;
}

// ===============================================================
// Persistence (stored in custom_assets.json)
// ===============================================================
static Map<String, Object> toJson() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("disabled", new ArrayList<Object>(DISABLED));
    m.put("customTags", new ArrayList<Object>(CUSTOM));
    Map<String, Object> o = new LinkedHashMap<>();
    for (Map.Entry<String, List<String>> en : OVERRIDES.entrySet()) o.put(en.getKey(), new ArrayList<Object>(en.getValue()));
    m.put("builtinTags", o);
    return m;
}

static void read(Object settings) {
    DISABLED.clear();
    CUSTOM.clear();
    OVERRIDES.clear();
    if (!(settings instanceof Map)) return;
    Map<?, ?> m = (Map<?, ?>) settings;
    DISABLED.addAll(CustomAssets.list(m, "disabled"));
    CUSTOM.addAll(CustomAssets.list(m, "customTags"));
    Object o = m.get("builtinTags");
    if (o instanceof Map) {
        for (Map.Entry<?, ?> en : ((Map<?, ?>) o).entrySet()) {
            Map<String, Object> wrap = new HashMap<>();
            wrap.put("v", en.getValue());
            OVERRIDES.put(String.valueOf(en.getKey()), dedupe(CustomAssets.list(wrap, "v")));
        }
    }
    CUSTOM.removeIf(Tags::isReserved);
}

// ===============================================================
// Helpers
// ===============================================================
static List<String> dedupe(Collection<String> tags) {
    List<String> out = new ArrayList<>();
    for (String t : tags) {
        String c = t == null ? "" : t.trim();
        if (!c.isEmpty() && !containsCI(out, c)) out.add(c);
    }
    return out;
}

static boolean containsCI(Collection<String> c, String t) {
    for (String s : c) if (s.equalsIgnoreCase(t)) return true;
    return false;
}

private static boolean sameTags(List<String> a, List<String> b) {
    if (a.size() != b.size()) return false;
    for (String t : a) if (!containsCI(b, t)) return false;
    return true;
}

// The asset's "source" for sorting: HOMEBREW, a sourcebook, or OFFICIAL
static String primarySource(List<String> tags) {
    if (containsCI(tags, HOMEBREW)) return HOMEBREW;
    for (String b : BOOK_TAGS) if (containsCI(tags, b)) return b;
    if (containsCI(tags, OFFICIAL)) return OFFICIAL;
    for (String t : tags) if (!isDerived(t)) return t;
    return "~";
}
}
