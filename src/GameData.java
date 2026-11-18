import java.util.*;
import java.util.List;
import java.util.function.Supplier;

// Static D&D rules data: classes, backgrounds, species, skills, equipment tables.
class GameData {

// Class metadata: name -> {hit dice, description}
static final Map<String, String[]> CLASS_INFO = new LinkedHashMap<>();
static {
    CLASS_INFO.put("Artificer", new String[]{"d8", "Masters of magical invention who imbue objects with arcane power to create tools, weapons, and constructs."});
    CLASS_INFO.put("Barbarian", new String[]{"d12", "Fierce warriors who channel primal rage to fuel incredible feats of strength and resilience in battle."});
    CLASS_INFO.put("Bard", new String[]{"d8", "Versatile performers who weave magic through music and words to inspire allies and confound foes."});
    CLASS_INFO.put("Cleric", new String[]{"d8", "Divine agents who channel the power of a deity to heal, protect, and smite in the name of their faith."});
    CLASS_INFO.put("Druid", new String[]{"d8", "Guardians of the natural world who draw on primal magic and can shape-shift into beasts."});
    CLASS_INFO.put("Fighter", new String[]{"d10", "Disciplined combatants trained in a wide range of weapons and tactics, excelling in nearly any battle."});
    CLASS_INFO.put("Monk", new String[]{"d8", "Martial artists who harness inner energy to strike with supernatural speed and precision."});
    CLASS_INFO.put("Paladin", new String[]{"d10", "Holy warriors bound by a sacred oath, combining martial prowess with divine magic."});
    CLASS_INFO.put("Ranger", new String[]{"d10", "Skilled hunters and trackers at home in the wilderness, blending martial skill with nature magic."});
    CLASS_INFO.put("Rogue", new String[]{"d8", "Cunning and stealthy adventurers who rely on skill, subtlety, and precision strikes."});
    CLASS_INFO.put("Sorcerer", new String[]{"d6", "Spellcasters who wield innate magic born from a mysterious bloodline or event."});
    CLASS_INFO.put("Warlock", new String[]{"d8", "Spellcasters who draw power from a pact made with a mysterious otherworldly patron."});
    CLASS_INFO.put("Wizard", new String[]{"d6", "Scholarly spellcasters who master arcane magic through rigorous study of ancient tomes."});
    // Homebrew classes from the Asset Library are inserted here by CustomAssets.applyToRuntime()
    CLASS_INFO.put("Custom", new String[]{"N/A", "Enter all stats and abilities manually, level-by-level."});
}

// Ability score metadata
static final String[] STAT_ORDER = {"STR", "DEX", "CON", "INT", "WIS", "CHA"};
static final Map<String, String> STAT_INFO = new LinkedHashMap<>();
static {
    STAT_INFO.put("STR", "Strength measures physical power. It governs melee attacks, carrying capacity, and athletic feats.");
    STAT_INFO.put("DEX", "Dexterity measures agility and reflexes. It governs armor class, ranged attacks, and stealth.");
    STAT_INFO.put("CON", "Constitution measures endurance and health. It governs hit points and resistance to physical strain.");
    STAT_INFO.put("INT", "Intelligence measures reasoning and memory. It governs knowledge skills and some spellcasting.");
    STAT_INFO.put("WIS", "Wisdom measures perception and insight. It governs awareness and some spellcasting.");
    STAT_INFO.put("CHA", "Charisma measures force of personality. It governs social skills and some spellcasting.");
}

static final Map<String, String> GEN_TYPE_INFO = new LinkedHashMap<>();
static {
    GEN_TYPE_INFO.put("Point Buy", "Spend a pool of 27 points to purchase your six ability scores. Higher scores cost more points, from 8 (free) up to 15 (9 points).");
    GEN_TYPE_INFO.put("Random Stat Generation", "Roll four six-sided dice for each stat and drop the lowest die, six times, to generate six scores. Your highest roll is assigned to your highest-priority stat and your lowest roll to your lowest-priority stat. If your total is 65 or lower, you may reroll.");
    GEN_TYPE_INFO.put("Custom", "Manually choose each of your six ability scores without restriction.");
}

// Point buy cost table, index = score - 8, for scores 8..15
static final int[] POINT_BUY_COST = {0, 1, 2, 3, 4, 5, 7, 9};

// Level-1 spell slots by class (simplified for this app's level-1 character creation)
static final Map<String, Integer> SPELL_SLOTS_LEVEL1 = new LinkedHashMap<>();
static {
    SPELL_SLOTS_LEVEL1.put("Artificer", 2);
    SPELL_SLOTS_LEVEL1.put("Bard", 2);
    SPELL_SLOTS_LEVEL1.put("Cleric", 2);
    SPELL_SLOTS_LEVEL1.put("Druid", 2);
    SPELL_SLOTS_LEVEL1.put("Paladin", 2);
    SPELL_SLOTS_LEVEL1.put("Ranger", 2);
    SPELL_SLOTS_LEVEL1.put("Sorcerer", 2);
    SPELL_SLOTS_LEVEL1.put("Warlock", 1);
    SPELL_SLOTS_LEVEL1.put("Wizard", 2);
}

// ---------------------------------------------------------------
// Starting equipment data
// ---------------------------------------------------------------
static EquipmentChoice equipChoice(String label, String... options) {
    return new EquipmentChoice(label, 1, options, null);
}

static EquipmentChoice weaponChoice(String label, int count, String weaponPool) {
    return new EquipmentChoice(label, count, null, weaponPool);
}

static final String[] ARTISAN_TOOLS = {
        "Alchemist's Supplies", "Brewer's Supplies", "Calligrapher's Supplies", "Carpenter's Tools",
        "Cartographer's Tools", "Cobbler's Tools", "Cook's Utensils", "Glassblower's Tools",
        "Jeweler's Tools", "Leatherworker's Tools", "Mason's Tools", "Painter's Supplies",
        "Potter's Tools", "Smith's Tools", "Tinker's Tools", "Weaver's Tools", "Woodcarver's Tools"
};
static final String[] MUSICAL_INSTRUMENTS = {
        "Bagpipes", "Drum", "Dulcimer", "Flute", "Lute", "Lyre", "Horn", "Pan Flute", "Shawm", "Viol"
};

static final int BACKGROUND_PACKAGE_B_GOLD = 50;
static final Map<String, EquipmentPackage> BACKGROUND_EQUIPMENT = new LinkedHashMap<>();
static {
    BACKGROUND_EQUIPMENT.put("Acolyte", new EquipmentPackage(8, new String[]{
            "Calligrapher's supplies", "Book", "Holy symbol", "Parchment (10)", "Robe"
    }));

    BACKGROUND_EQUIPMENT.put("Artisan", new EquipmentPackage(25, new String[]{
            "Abacus", "Merchant's scale", "Pouch", "Traveler's clothes"
    }, equipChoice("Artisan's Tools", ARTISAN_TOOLS)));

    BACKGROUND_EQUIPMENT.put("Charlatan", new EquipmentPackage(15, new String[]{
            "Disguise kit", "Fine clothes", "Forged letter", "Gaming set", "Pouch"
    }));

    BACKGROUND_EQUIPMENT.put("Criminal", new EquipmentPackage(16, new String[]{
            "Daggers (2)", "Thieves' tools", "Crowbar", "Pouch (2)", "Traveler's clothes"
    }));

    BACKGROUND_EQUIPMENT.put("Entertainer", new EquipmentPackage(14, new String[]{
            "Costume", "Mirror", "Perfume", "Pouch", "Traveler's clothes"
    }, equipChoice("Musical Instrument", MUSICAL_INSTRUMENTS)));

    BACKGROUND_EQUIPMENT.put("Farmer", new EquipmentPackage(30, new String[]{
            "Sickle", "Carpenter's tools", "Healer's kit", "Iron pot", "Shovel", "Traveler's clothes"
    }));

    BACKGROUND_EQUIPMENT.put("Guard", new EquipmentPackage(12, new String[]{
            "Light Crossbow", "Bolt (20)", "Spear", "Hooded lantern", "Manacles", "Quiver", "Traveler's clothes"
    }));

    BACKGROUND_EQUIPMENT.put("Guide", new EquipmentPackage(13, new String[]{
            "Shortbow", "Arrow (20)", "Bedroll", "Cartographer's tools", "Mess kit", "Tent", "Traveler's clothes"
    }));

    BACKGROUND_EQUIPMENT.put("Hermit", new EquipmentPackage(16, new String[]{
            "Quarterstaff", "Herbalism kit", "Bedroll", "Book", "Lamp", "Oil (3)", "Traveler's clothes"
    }));

    BACKGROUND_EQUIPMENT.put("Merchant", new EquipmentPackage(22, new String[]{
            "Navigator's tools", "Pouch (2)", "Traveler's clothes"
    }));

    BACKGROUND_EQUIPMENT.put("Noble", new EquipmentPackage(26, new String[]{
            "Fine clothes", "Gaming set", "Perfume", "Pouch"
    }));

    BACKGROUND_EQUIPMENT.put("Sage", new EquipmentPackage(10, new String[]{
            "Book", "Calligrapher's supplies", "Common clothes", "Ink (1)", "Ink pen", "Parchment (10)"
    }));

    BACKGROUND_EQUIPMENT.put("Sailor", new EquipmentPackage(19, new String[]{
            "Dagger", "Navigator's tools", "Net", "Hempen rope (50)", "Traveler's clothes"
    }));

    BACKGROUND_EQUIPMENT.put("Scribe", new EquipmentPackage(23, new String[]{
            "Calligrapher's supplies", "Fine clothes", "Lamp", "Oil (3)", "Parchment (12)"
    }));

    BACKGROUND_EQUIPMENT.put("Soldier", new EquipmentPackage(14, new String[]{
            "Shortbow", "Arrow (20)", "Spear", "Gaming set", "Healer's kit", "Traveler's clothes"
    }));

    BACKGROUND_EQUIPMENT.put("Wayfarer", new EquipmentPackage(16, new String[]{
            "Dagger (2)", "Thieves' tools", "Bedroll", "Gaming set", "Pouch", "Traveler's clothes"
    }));
}

static final Map<String, Integer> CLASS_PACKAGE_B_GOLD = new LinkedHashMap<>();
static final Map<String, EquipmentPackage> CLASS_EQUIPMENT = new LinkedHashMap<>();
static
{
    CLASS_PACKAGE_B_GOLD.put("Artificer", 130);
    CLASS_EQUIPMENT.put("Artificer", new EquipmentPackage(6, new String[]{
            "Scale mail armor", "Mace", "Handaxe", "Light Crossbow", "Bolt (20)", "Crossbow bolt case",
            "Thieves' tools", "Backpack", "Bedroll", "Mess kit", "Tinderbox", "Torch (10)", "Rations (10)", "Hempen rope (50)", "Waterskin"
    }));

    CLASS_PACKAGE_B_GOLD.put("Barbarian", 75);
    CLASS_EQUIPMENT.put("Barbarian", new EquipmentPackage(15, new String[]{
            "Greataxe", "Handaxe (4)", "Backpack", "Bedroll", "Mess kit", "Tinderbox", "Torch (10)", "Rations (10)", "Hempen rope (50)", "Waterskin"
    }));

    CLASS_PACKAGE_B_GOLD.put("Bard", 110);
    CLASS_EQUIPMENT.put("Bard", new EquipmentPackage(10, new String[]{
            "Leather armor", "Dagger", "Flute", "Shortsword", "Backpack", "Bedroll", "Costume (2)", "Rations (5)", "Waterskin"
    }));

    CLASS_PACKAGE_B_GOLD.put("Cleric", 140);
    CLASS_EQUIPMENT.put("Cleric", new EquipmentPackage(5, new String[]{
            "Chain shirt", "Shield", "Mace", "Holy symbol", "Backpack", "Blanket", "Candle (10)", "Tinderbox", "Alms box", "Censer", "Incense (2)", "Vestments", "Rations (2)", "Waterskin"
    }));

    CLASS_PACKAGE_B_GOLD.put("Druid", 50);
    CLASS_EQUIPMENT.put("Druid", new EquipmentPackage(14, new String[]{
            "Leather armor", "Shield", "Quarterstaff", "Druidic focus", "Backpack", "Bedroll", "Mess kit", "Tinderbox", "Torch (10)", "Rations (10)", "Hempen rope (50)", "Waterskin"
    }));

    CLASS_PACKAGE_B_GOLD.put("Fighter", 155);
    CLASS_EQUIPMENT.put("Fighter", new EquipmentPackage(4, new String[]{
            "Chain mail", "Greatsword", "Flail", "Javelin (8)", "Backpack", "Bedroll", "Mess kit", "Tinderbox", "Torch (10)", "Rations (10)", "Hempen rope (50)", "Waterskin"
    }));

    CLASS_PACKAGE_B_GOLD.put("Monk", 15);
    CLASS_EQUIPMENT.put("Monk", new EquipmentPackage(11, new String[]{
            "Quarterstaff", "Dagger (5)", "Backpack", "Bedroll", "Mess kit", "Tinderbox", "Torch (10)", "Rations (10)", "Hempen rope (50)", "Waterskin"
    }));

    CLASS_PACKAGE_B_GOLD.put("Paladin", 150);
    CLASS_EQUIPMENT.put("Paladin", new EquipmentPackage(9, new String[]{
            "Chain mail", "Shield", "Longsword", "Javelin (6)", "Holy symbol", "Backpack", "Blanket", "Candle (10)", "Tinderbox", "Alms box", "Censer", "Incense (2)", "Vestments", "Rations (2)", "Waterskin"
    }));

    CLASS_PACKAGE_B_GOLD.put("Ranger", 150);
    CLASS_EQUIPMENT.put("Ranger", new EquipmentPackage(6, new String[]{
            "Studded leather armor", "Longbow", "Arrow (20)", "Quiver", "Shortsword (2)", "Backpack", "Bedroll", "Mess kit", "Tinderbox", "Torch (10)", "Rations (10)", "Hempen rope (50)", "Waterskin"
    }));

    CLASS_PACKAGE_B_GOLD.put("Rogue", 110);
    CLASS_EQUIPMENT.put("Rogue", new EquipmentPackage(11, new String[]{
            "Leather armor", "Shortbow", "Arrow (20)", "Quiver", "Rapier", "Dagger (2)", "Thieves' tools", "Backpack", "Crowbar", "Hammer", "Piton (10)", "Torch (10)", "Tinderbox", "Rations (5)", "Waterskin", "Hempen rope (50)"
    }));

    CLASS_PACKAGE_B_GOLD.put("Sorcerer", 50);
    CLASS_EQUIPMENT.put("Sorcerer", new EquipmentPackage(23, new String[]{
            "Dagger (2)", "Arcane focus", "Backpack", "Bedroll", "Mess kit", "Tinderbox", "Torch (10)", "Rations (10)", "Hempen rope (50)", "Waterskin"
    }));

    CLASS_PACKAGE_B_GOLD.put("Warlock", 100);
    CLASS_EQUIPMENT.put("Warlock", new EquipmentPackage(13, new String[]{
            "Studded leather armor", "Quarterstaff", "Dagger (2)", "Arcane focus", "Backpack", "Book", "Ink (1)", "Ink pen", "Parchment (10)", "Little bag of sand", "Small knife"
    }));

    CLASS_PACKAGE_B_GOLD.put("Wizard", 75);
    CLASS_EQUIPMENT.put("Wizard", new EquipmentPackage(6, new String[]{
            "Quarterstaff", "Dagger (2)", "Arcane focus", "Backpack", "Book", "Ink (1)", "Ink pen", "Parchment (10)", "Little bag of sand", "Small knife"
    }));
}

static int hitDieMax(String className) {
    String[] info = CLASS_INFO.get(className);
    if (info == null) return 0;
    String hd = info[0];
    if (hd != null && hd.startsWith("d")) {
        try {
            return Integer.parseInt(hd.substring(1));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
    return 0;
}

static int abilityModifier(int score) {
    return (int) Math.floor((score - 10) / 2.0);
}

// Species metadata
static final Map<String, String> SPECIES_INFO = new LinkedHashMap<>();
static {
    SPECIES_INFO.put("Human", "Adaptable and ambitious, humans are found in nearly every corner of the world in great numbers.");
    SPECIES_INFO.put("Elf", "Graceful and long-lived, elves carry a deep connection to magic and the natural world.");
    SPECIES_INFO.put("Dwarf", "Sturdy and resilient, dwarves are known for their craftsmanship and unshakable resolve.");
    SPECIES_INFO.put("Halfling", "Small, nimble, and famously lucky, halflings value comfort, community, and courage.");
    SPECIES_INFO.put("Gnome", "Curious and inventive, gnomes bring boundless energy and cleverness to everything they do.");
    SPECIES_INFO.put("Dragonborn", "Proud and honorable, dragonborn carry draconic ancestry and a breath weapon in their blood.");
    SPECIES_INFO.put("Aasimar", "Touched by celestial power, aasimar carry a spark of the heavens within them.");
    SPECIES_INFO.put("Goliath", "Towering and hardy, goliaths hail from unforgiving mountain peaks and prize competition.");
    SPECIES_INFO.put("Orc", "Powerful and driven, orcs channel relentless endurance and strength into everything they do.");
    SPECIES_INFO.put("Tiefling", "Bearing an infernal heritage, tieflings are marked by their otherworldly appearance and resilience.");
    SPECIES_INFO.put("Tabaxi", "Feline and restless, tabaxi are driven by insatiable curiosity and a love of adventure.");
    SPECIES_INFO.put("Changeling", "Natural shapeshifters, changelings can alter their appearance and voice at will.");
    SPECIES_INFO.put("Warforged", "Living constructs built for war, warforged are tireless bodies animated with a spark of sentience.");
    // Homebrew species from the Asset Library are inserted here by CustomAssets.applyToRuntime()
    SPECIES_INFO.put("Custom", "Create your own species with traits of your choosing.");
}

// Species that have a lineage sub-choice; others skip that screen entirely
static final Map<String, String[]> SPECIES_LINEAGES = new LinkedHashMap<>();
static {
    SPECIES_LINEAGES.put("Elf", new String[]{"High Elf", "Wood Elf", "Drow"});
    SPECIES_LINEAGES.put("Gnome", new String[]{"Forest Gnome", "Rock Gnome"});
    SPECIES_LINEAGES.put("Tiefling", new String[]{"Abyssal", "Cthonic", "Infernal"});
    SPECIES_LINEAGES.put("Goliath", new String[]{"Cloud", "Fire", "Frost", "Hill", "Stone", "Storm"});
    SPECIES_LINEAGES.put("Dragonborn", new String[]{"Black", "Blue", "Brass", "Bronze", "Copper", "Gold", "Green", "Red", "Silver", "White"});
}

static int computeSpeed(String species, String lineage) {
    Map<String, Object> homebrew = CustomAssets.speciesAsset(species);
    if (homebrew != null) return CustomAssets.intVal(homebrew, "speed", 30);
    if ("Goliath".equals(species)) return 35;
    if ("Elf".equals(species) && "Wood Elf".equals(lineage)) return 35;
    if ("Dwarf".equals(species) || "Halfling".equals(species) || "Gnome".equals(species)) return 25;
    return 30;
}

static Integer computeClimbSpeed(String species) {
    return "Tabaxi".equals(species) ? 30 : null;
}

static String computeSize(String species) {
    Map<String, Object> homebrew = CustomAssets.speciesAsset(species);
    if (homebrew != null && !CustomAssets.str(homebrew, "size").isEmpty()) return CustomAssets.str(homebrew, "size");
    if ("Halfling".equals(species) || "Gnome".equals(species)) return "Small";
    return "Medium";
}

static String computeCreatureType(String species) {
    Map<String, Object> homebrew = CustomAssets.speciesAsset(species);
    if (homebrew != null && !CustomAssets.str(homebrew, "creatureType").isEmpty()) return CustomAssets.str(homebrew, "creatureType");
    if ("Changeling".equals(species)) return "Fey";
    if ("Warforged".equals(species)) return "Construct";
    return "Humanoid";
}

// Background metadata
static final Map<String, String> BACKGROUND_INFO = new LinkedHashMap<>();
static final Map<String, String[]> BACKGROUND_STATS = new LinkedHashMap<>();
static {
    BACKGROUND_INFO.put("Acolyte", "Devoted to a temple or faith, skilled in religious rites and lore.");
    BACKGROUND_STATS.put("Acolyte", new String[]{"INT", "WIS", "CHA"});

    BACKGROUND_INFO.put("Artisan", "A skilled crafter who has honed a trade, from smithing to carpentry.");
    BACKGROUND_STATS.put("Artisan", new String[]{"STR", "DEX", "INT"});

    BACKGROUND_INFO.put("Charlatan", "A smooth-talking trickster skilled in deception and disguise.");
    BACKGROUND_STATS.put("Charlatan", new String[]{"DEX", "CON", "CHA"});

    BACKGROUND_INFO.put("Criminal", "Someone versed in the underworld, skilled in stealth and subterfuge.");
    BACKGROUND_STATS.put("Criminal", new String[]{"DEX", "CON", "INT"});

    BACKGROUND_INFO.put("Entertainer", "A performer who thrives on captivating audiences with music, dance, or story.");
    BACKGROUND_STATS.put("Entertainer", new String[]{"DEX", "CON", "CHA"});

    BACKGROUND_INFO.put("Farmer", "Raised working the land, hardy and practical with an understanding of nature's rhythms.");
    BACKGROUND_STATS.put("Farmer", new String[]{"STR", "CON", "WIS"});

    BACKGROUND_INFO.put("Guard", "Trained to protect people and places, disciplined and alert.");
    BACKGROUND_STATS.put("Guard", new String[]{"STR", "INT", "WIS"});

    BACKGROUND_INFO.put("Guide", "An experienced wilderness traveler skilled at navigation and survival.");
    BACKGROUND_STATS.put("Guide", new String[]{"DEX", "CON", "WIS"});

    BACKGROUND_INFO.put("Hermit", "One who lived in seclusion, gaining insight through solitude and study.");
    BACKGROUND_STATS.put("Hermit", new String[]{"CON", "WIS", "CHA"});

    BACKGROUND_INFO.put("Merchant", "A trader skilled in negotiation, commerce, and appraising goods.");
    BACKGROUND_STATS.put("Merchant", new String[]{"CON", "INT", "CHA"});

    BACKGROUND_INFO.put("Noble", "Born into privilege, well-versed in etiquette, politics, and leadership.");
    BACKGROUND_STATS.put("Noble", new String[]{"STR", "INT", "CHA"});

    BACKGROUND_INFO.put("Sage", "A scholar devoted to research and the pursuit of knowledge.");
    BACKGROUND_STATS.put("Sage", new String[]{"CON", "INT", "WIS"});

    BACKGROUND_INFO.put("Sailor", "A seasoned mariner familiar with ships, storms, and life at sea.");
    BACKGROUND_STATS.put("Sailor", new String[]{"STR", "DEX", "WIS"});

    BACKGROUND_INFO.put("Scribe", "Trained in writing, record-keeping, and the transcription of important texts.");
    BACKGROUND_STATS.put("Scribe", new String[]{"DEX", "INT", "WIS"});

    BACKGROUND_INFO.put("Soldier", "A trained warrior experienced in military discipline and tactics.");
    BACKGROUND_STATS.put("Soldier", new String[]{"STR", "DEX", "CON"});

    BACKGROUND_INFO.put("Wayfarer", "A wanderer who has lived by wit and instinct on the road.");
    BACKGROUND_STATS.put("Wayfarer", new String[]{"DEX", "WIS", "CHA"});

    BACKGROUND_INFO.put("Custom", "Create your own background with a story and traits of your choosing.");
    BACKGROUND_STATS.put("Custom", STAT_ORDER);
}

// All 18 skills, with generic descriptions
static final String[] ALL_SKILLS = {
        "Acrobatics", "Animal Handling", "Arcana", "Athletics", "Deception", "History",
        "Insight", "Intimidation", "Investigation", "Medicine", "Nature", "Perception",
        "Performance", "Persuasion", "Religion", "Sleight of Hand", "Stealth", "Survival"
};
static final Map<String, String> SKILL_INFO = new LinkedHashMap<>();
static {
    SKILL_INFO.put("Acrobatics", "Balance, tumble, and stay on your feet in tricky situations.");
    SKILL_INFO.put("Animal Handling", "Calm, control, or read the intentions of animals.");
    SKILL_INFO.put("Arcana", "Recall lore about spells, magic items, and magical phenomena.");
    SKILL_INFO.put("Athletics", "Climb, jump, swim, and perform other feats of physical exertion.");
    SKILL_INFO.put("Deception", "Convincingly hide the truth through lies or misdirection.");
    SKILL_INFO.put("History", "Recall lore about past events, civilizations, and lore.");
    SKILL_INFO.put("Insight", "Read body language and tone to discern true intentions.");
    SKILL_INFO.put("Intimidation", "Influence others through threats, hostility, or physical presence.");
    SKILL_INFO.put("Investigation", "Search for clues and deduce logical conclusions from evidence.");
    SKILL_INFO.put("Medicine", "Diagnose illness, stabilize the dying, and treat injuries.");
    SKILL_INFO.put("Nature", "Recall lore about terrain, plants, animals, and weather.");
    SKILL_INFO.put("Perception", "Notice details and spot hidden things using your senses.");
    SKILL_INFO.put("Performance", "Entertain an audience through music, dance, acting, or storytelling.");
    SKILL_INFO.put("Persuasion", "Influence others through tact, social grace, or good arguments.");
    SKILL_INFO.put("Religion", "Recall lore about deities, rites, and religious practices.");
    SKILL_INFO.put("Sleight of Hand", "Perform manual tricks like pickpocketing or concealing objects.");
    SKILL_INFO.put("Stealth", "Move silently and stay hidden from view.");
    SKILL_INFO.put("Survival", "Track quarry, navigate the wild, and endure harsh conditions.");
}

// Governing ability score for each skill
static final Map<String, String> SKILL_ABILITY = new LinkedHashMap<>();
static {
    SKILL_ABILITY.put("Acrobatics", "DEX");
    SKILL_ABILITY.put("Animal Handling", "WIS");
    SKILL_ABILITY.put("Arcana", "INT");
    SKILL_ABILITY.put("Athletics", "STR");
    SKILL_ABILITY.put("Deception", "CHA");
    SKILL_ABILITY.put("History", "INT");
    SKILL_ABILITY.put("Insight", "WIS");
    SKILL_ABILITY.put("Intimidation", "CHA");
    SKILL_ABILITY.put("Investigation", "INT");
    SKILL_ABILITY.put("Medicine", "WIS");
    SKILL_ABILITY.put("Nature", "INT");
    SKILL_ABILITY.put("Perception", "WIS");
    SKILL_ABILITY.put("Performance", "CHA");
    SKILL_ABILITY.put("Persuasion", "CHA");
    SKILL_ABILITY.put("Religion", "INT");
    SKILL_ABILITY.put("Sleight of Hand", "DEX");
    SKILL_ABILITY.put("Stealth", "DEX");
    SKILL_ABILITY.put("Survival", "WIS");
}

// Languages (2024 rules): every character knows Common plus two standard languages
static final Map<String, String> STANDARD_LANGUAGES = new LinkedHashMap<>();
static {
    STANDARD_LANGUAGES.put("Common Sign Language", "A sign language understood across most of the realms. Typical speakers: anyone.");
    STANDARD_LANGUAGES.put("Draconic", "The language of dragons and dragonborn; also used in arcane writing. Typical speakers: dragons, dragonborn.");
    STANDARD_LANGUAGES.put("Dwarvish", "A hard, guttural tongue full of consonants. Typical speakers: dwarves.");
    STANDARD_LANGUAGES.put("Elvish", "A fluid language with subtle intonations. Typical speakers: elves.");
    STANDARD_LANGUAGES.put("Giant", "The language of giants and their kin. Typical speakers: giants, ogres.");
    STANDARD_LANGUAGES.put("Gnomish", "A technical language rich in specialised terms. Typical speakers: gnomes.");
    STANDARD_LANGUAGES.put("Goblin", "The language of goblinoids. Typical speakers: goblinoids.");
    STANDARD_LANGUAGES.put("Halfling", "A friendly language rarely shared with outsiders. Typical speakers: halflings.");
    STANDARD_LANGUAGES.put("Orc", "A harsh, grating language. Typical speakers: orcs.");
}
static final Map<String, String> LANGUAGE_INFO = new LinkedHashMap<>();
static {
    LANGUAGE_INFO.put("Common", "The language most people in the realms speak. Every character knows Common.");
    LANGUAGE_INFO.putAll(STANDARD_LANGUAGES);
    LANGUAGE_INFO.put("Thieves' Cant", "A secret mix of dialect, jargon and code that lets rogues hide messages in normal-seeming conversation.");
    LANGUAGE_INFO.put("Druidic", "The secret language of druids. You can leave hidden messages in it that only other druids notice.");
}
// Languages a class grants automatically at level 1
static final Map<String, String> CLASS_LANGUAGES = new LinkedHashMap<>();
static {
    CLASS_LANGUAGES.put("Rogue", "Thieves' Cant");
    CLASS_LANGUAGES.put("Druid", "Druidic");
}

// Condition rules summaries (2024 rules, condensed)
static final Map<String, String> CONDITION_INFO = new LinkedHashMap<>();
static {
    CONDITION_INFO.put("Conscious", "You're awake and able to act normally.");
    CONDITION_INFO.put("Unconscious", "You're Incapacitated and Prone, drop what you're holding, and can't move or speak. You're unaware of your surroundings and automatically fail STR and DEX saves. Attacks against you have Advantage, and melee hits within 5 ft. are critical hits.");
    CONDITION_INFO.put("Blinded", "You can't see. Attacks against you have Advantage, and your attacks have Disadvantage.");
    CONDITION_INFO.put("Charmed", "You can't attack the charmer, and the charmer has Advantage on social checks against you.");
    CONDITION_INFO.put("Deafened", "You can't hear and automatically fail any check that requires hearing.");
    CONDITION_INFO.put("Exhaustion", "Each level gives -2 to D20 Tests and -5 ft. Speed. Level 6 is death. A Long Rest removes one level.");
    CONDITION_INFO.put("Frightened", "Disadvantage on ability checks and attacks while the source of fear is in sight; you can't willingly move closer to it.");
    CONDITION_INFO.put("Grappled", "Speed 0; Disadvantage on attacks against anyone other than the grappler. The grappler can drag you.");
    CONDITION_INFO.put("Incapacitated", "You can't take actions, Bonus Actions or Reactions, and you can't concentrate.");
    CONDITION_INFO.put("Invisible", "You can't be seen. You have Advantage on attacks, and attacks against you have Disadvantage.");
    CONDITION_INFO.put("Paralyzed", "Incapacitated, Speed 0, automatically fail STR and DEX saves. Attacks against you have Advantage, and melee hits within 5 ft. are critical hits.");
    CONDITION_INFO.put("Petrified", "Turned to stone: Incapacitated, Speed 0, resistant to all damage, immune to poison. Attacks against you have Advantage.");
    CONDITION_INFO.put("Poisoned", "Disadvantage on attack rolls and ability checks.");
    CONDITION_INFO.put("Prone", "You can only crawl or stand up. Disadvantage on attacks. Attacks against you have Advantage within 5 ft. and Disadvantage otherwise.");
    CONDITION_INFO.put("Restrained", "Speed 0; Disadvantage on attacks and DEX saves. Attacks against you have Advantage.");
    CONDITION_INFO.put("Stunned", "Incapacitated, automatically fail STR and DEX saves. Attacks against you have Advantage.");
}

// Saving throw proficiencies granted by each class
static final Map<String, String[]> CLASS_SAVING_THROWS = new LinkedHashMap<>();
static {
    CLASS_SAVING_THROWS.put("Artificer", new String[]{"CON", "INT"});
    CLASS_SAVING_THROWS.put("Barbarian", new String[]{"STR", "CON"});
    CLASS_SAVING_THROWS.put("Bard", new String[]{"DEX", "CHA"});
    CLASS_SAVING_THROWS.put("Cleric", new String[]{"WIS", "CHA"});
    CLASS_SAVING_THROWS.put("Druid", new String[]{"INT", "WIS"});
    CLASS_SAVING_THROWS.put("Fighter", new String[]{"STR", "CON"});
    CLASS_SAVING_THROWS.put("Monk", new String[]{"STR", "DEX"});
    CLASS_SAVING_THROWS.put("Paladin", new String[]{"WIS", "CHA"});
    CLASS_SAVING_THROWS.put("Ranger", new String[]{"STR", "DEX"});
    CLASS_SAVING_THROWS.put("Rogue", new String[]{"DEX", "INT"});
    CLASS_SAVING_THROWS.put("Sorcerer", new String[]{"CON", "CHA"});
    CLASS_SAVING_THROWS.put("Warlock", new String[]{"WIS", "CHA"});
    CLASS_SAVING_THROWS.put("Wizard", new String[]{"INT", "WIS"});
}

static int proficiencyBonus(int level) {
    return 2 + (int) Math.floor((level - 1) / 4.0);
}

// Armor data for AC inference from an equipment list
static class ArmorInfo {
    int baseAC;
    int maxDexBonus; // Integer.MAX_VALUE = uncapped
    boolean isShield;

    ArmorInfo(int baseAC, int maxDexBonus, boolean isShield) {
        this.baseAC = baseAC;
        this.maxDexBonus = maxDexBonus;
        this.isShield = isShield;
    }
}

static final Map<String, ArmorInfo> ARMOR_AC = new LinkedHashMap<>();
static {
    ARMOR_AC.put("Leather armor", new ArmorInfo(11, Integer.MAX_VALUE, false));
    ARMOR_AC.put("Studded leather armor", new ArmorInfo(12, Integer.MAX_VALUE, false));
    ARMOR_AC.put("Chain shirt", new ArmorInfo(13, 2, false));
    ARMOR_AC.put("Scale mail armor", new ArmorInfo(14, 2, false));
    ARMOR_AC.put("Chain mail", new ArmorInfo(16, 0, false));
    ARMOR_AC.put("Shield", new ArmorInfo(2, 0, true)); // for shields, baseAC is the AC bonus
}

static int shieldBonus(ArmorInfo info) {
    return info.baseAC > 0 ? info.baseAC : 2;
}

// Strips a trailing count like " (2)" so equipment strings match the ARMOR_AC keys
static String stripEquipmentCount(String item) {
    return item == null ? "" : item.replaceAll("\\s*\\(\\d+\\)\\s*$", "").trim();
}

static int computeAC(List<String> equipment, int dexMod) {
    int bodyAC = 10 + dexMod; // unarmored default
    int shield = 0; // best shield bonus (shields don't stack)
    if (equipment != null) {
        for (String item : equipment) {
            ArmorInfo info = ARMOR_AC.get(stripEquipmentCount(item));
            if (info == null) continue;
            if (info.isShield) {
                shield = Math.max(shield, shieldBonus(info));
            } else {
                int dexContribution = info.maxDexBonus <= 0 ? 0 : Math.min(dexMod, info.maxDexBonus);
                int candidate = info.baseAC + dexContribution;
                if (candidate > bodyAC) bodyAC = candidate;
            }
        }
    }
    return bodyAC + shield;
}

// Fixed background skill proficiencies (Custom is handled separately as a free pick)
static final Map<String, String[]> BACKGROUND_SKILL_PROFICIENCIES = new LinkedHashMap<>();
static {
    BACKGROUND_SKILL_PROFICIENCIES.put("Acolyte", new String[]{"Insight", "Religion"});
    BACKGROUND_SKILL_PROFICIENCIES.put("Artisan", new String[]{"Investigation", "Persuasion"});
    BACKGROUND_SKILL_PROFICIENCIES.put("Charlatan", new String[]{"Deception", "Sleight of Hand"});
    BACKGROUND_SKILL_PROFICIENCIES.put("Criminal", new String[]{"Deception", "Stealth"});
    BACKGROUND_SKILL_PROFICIENCIES.put("Entertainer", new String[]{"Acrobatics", "Performance"});
    BACKGROUND_SKILL_PROFICIENCIES.put("Farmer", new String[]{"Animal Handling", "Nature"});
    BACKGROUND_SKILL_PROFICIENCIES.put("Guard", new String[]{"Athletics", "Perception"});
    BACKGROUND_SKILL_PROFICIENCIES.put("Guide", new String[]{"Stealth", "Survival"});
    BACKGROUND_SKILL_PROFICIENCIES.put("Hermit", new String[]{"Medicine", "Religion"});
    BACKGROUND_SKILL_PROFICIENCIES.put("Merchant", new String[]{"Animal Handling", "Persuasion"});
    BACKGROUND_SKILL_PROFICIENCIES.put("Noble", new String[]{"History", "Persuasion"});
    BACKGROUND_SKILL_PROFICIENCIES.put("Sage", new String[]{"Arcana", "History"});
    BACKGROUND_SKILL_PROFICIENCIES.put("Sailor", new String[]{"Athletics", "Perception"});
    BACKGROUND_SKILL_PROFICIENCIES.put("Scribe", new String[]{"Investigation", "History"});
    BACKGROUND_SKILL_PROFICIENCIES.put("Soldier", new String[]{"Athletics", "Intimidation"});
    BACKGROUND_SKILL_PROFICIENCIES.put("Wayfarer", new String[]{"Insight", "Stealth"});
}

// Per-class decisions
static final Map<String, List<DecisionItem>> CLASS_DECISIONS = new LinkedHashMap<>();
static {
    CLASS_DECISIONS.put("Artificer", decisions(
            skills("Which skills? (2)", 2, "Arcana", "History", "Investigation", "Medicine", "Nature", "Perception", "Sleight of Hand"),
            cantrips("Cantrips? (2)", 2),
            spellsLevel1("Prepared spells? (2)", 2)
    ));
    CLASS_DECISIONS.put("Barbarian", decisions(
            skills("Which skills? (2)", 2, "Animal Handling", "Athletics", "Intimidation", "Nature", "Perception", "Survival")
    ));
    CLASS_DECISIONS.put("Bard", decisions(
            skills("Which skills? (3)", 3, ALL_SKILLS),
            text("Musical instruments? (3)", 3, "Enter an instrument"),
            cantrips("Cantrips? (2)", 2),
            spellsLevel1("Spells? (4)", 4)
    ));
    CLASS_DECISIONS.put("Cleric", decisions(
            skills("Which skills? (2)", 2, "History", "Insight", "Medicine", "Persuasion", "Religion"),
            choice("Divine Order?", 1, "Protector", "Thaumaturge"),
            cantrips("Cantrips? (3)", 3),
            spellsLevel1("Prepared spells? (4)", 4)
    ));
    CLASS_DECISIONS.put("Druid", decisions(
            skills("Which skills? (2)", 2, "Animal Handling", "Arcana", "Insight", "Medicine", "Nature", "Perception", "Religion", "Survival"),
            choice("Primal Order?", 1, "Magician", "Warden"),
            cantrips("Cantrips? (2)", 2),
            spellsLevel1("Prepared spells? (4)", 4)
    ));
    CLASS_DECISIONS.put("Fighter", decisions(
            skills("Which skills? (2)", 2, "Acrobatics", "Animal Handling", "Athletics", "History", "Insight", "Intimidation", "Perception", "Survival"),
            choice("Fighting Style?", 1, "Archery", "Blind Fighting", "Defense", "Dueling", "Great Weapon Fighting", "Interception", "Protection", "Thrown Weapon Fighting", "Two-Weapon Fighting", "Unarmed Fighting")
    ));
    CLASS_DECISIONS.put("Monk", decisions(
            skills("Skills? (2)", 2, "Acrobatics", "Athletics", "History", "Insight", "Religion", "Stealth")
    ));
    CLASS_DECISIONS.put("Paladin", decisions(
            skills("Skills? (2)", 2, "Athletics", "Insight", "Intimidation", "Medicine", "Persuasion", "Religion"),
            spellsLevel1("Prepared spells? (2)", 2)
    ));
    CLASS_DECISIONS.put("Ranger", decisions(
            skills("Skills? (3)", 3, "Animal Handling", "Athletics", "Insight", "Investigation", "Nature", "Perception", "Stealth", "Survival"),
            spellsLevel1("Prepared spells? (2)", 2)
    ));
    CLASS_DECISIONS.put("Rogue", decisions(
            skills("Skills? (4)", 4, "Acrobatics", "Athletics", "Deception", "Insight", "Investigation", "Perception", "Performance", "Persuasion", "Sleight of Hand", "Stealth")
    ));
    CLASS_DECISIONS.put("Sorcerer", decisions(
            skills("Skills? (2)", 2, "Arcana", "Deception", "Insight", "Intimidation", "Perception", "Religion"),
            cantrips("Cantrips? (4)", 4),
            spellsLevel1("Spells prepared? (2)", 2)
    ));
    CLASS_DECISIONS.put("Warlock", decisions(
            skills("Skills? (2)", 2, "Arcana", "Deception", "History", "Intimidation", "Investigation", "Nature", "Religion"),
            cantrips("Cantrips? (2)", 2),
            spellsLevel1("Spells prepared? (2)", 2),
            choice("Eldritch Invocation?", 1, "Armor of Shadows", "Eldritch Mind", "Pact of the Blade", "Pact of the Chain", "Pact of the Tome")
    ));
    CLASS_DECISIONS.put("Wizard", decisions(
            skills("Skills? (2)", 2, "Arcana", "History", "Insight", "Investigation", "Medicine", "Religion"),
            cantrips("Cantrips? (3)", 3),
            spellsLevel1("Spells known? (6)", 6),
            spellsLevel1("Prepared spells? (4)", 4)
    ));
}

static DecisionItem skills(String label, int count, String... options) {
    return new DecisionItem(label, "SKILLS", count, options, null);
}

static DecisionItem choice(String label, int count, String... options) {
    return new DecisionItem(label, "CHOICE", count, options, null);
}

static DecisionItem text(String label, int count, String placeholder) {
    return new DecisionItem(label, "TEXT", count, null, placeholder);
}

static DecisionItem cantrips(String label, int count) {
    return new DecisionItem(label, "CANTRIPS", count, null, null);
}

static DecisionItem spellsLevel1(String label, int count) {
    return new DecisionItem(label, "SPELLS1", count, null, null);
}

static List<DecisionItem> decisions(DecisionItem... items) {
    return Arrays.asList(items);
}

}

class EquipmentChoice {
    String label;
    int count;
    String[] options; // used unless weaponPool is set
    String weaponPool; // non-null = pull options dynamically from WEAPON_DB by category prefix

    EquipmentChoice(String label, int count, String[] options, String weaponPool) {
        this.label = label;
        this.count = count;
        this.options = options;
        this.weaponPool = weaponPool;
    }
}

class EquipmentPackage {
    List<String> fixedItems;
    List<EquipmentChoice> choices;
    int gold;

    EquipmentPackage(int gold, String[] fixedItems, EquipmentChoice... choices) {
        this.gold = gold;
        this.fixedItems = Arrays.asList(fixedItems);
        this.choices = Arrays.asList(choices);
    }
}

// A single decision block within a class's "Class Details" step
class DecisionItem {
    String label;
    String type; // "SKILLS", "CHOICE", or "TEXT"
    int count;
    String[] options; // null for TEXT
    String placeholder; // used for TEXT type only

    DecisionItem(String label, String type, int count, String[] options, String placeholder) {
        this.label = label;
        this.type = type;
        this.count = count;
        this.options = options;
        this.placeholder = placeholder;
    }
}

// Wraps a rendered DecisionItem with a way to read its current selections
class RenderedItem {
    DecisionItem item;
    Supplier<List<String>> getSelections;
    java.util.function.Consumer<List<String>> setSelections; // nullable

    RenderedItem(DecisionItem item, Supplier<List<String>> getSelections) {
        this.item = item;
        this.getSelections = getSelections;
    }
}