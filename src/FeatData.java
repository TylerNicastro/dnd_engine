import java.util.*;
import java.util.List;

// Feat registry + level-up progression rules (hit dice, which class levels grant a feat).
//
// To add a new feat, add one line to the matching static block below:
//     general("Feat Name", "Short description.", STR, DEX);      // +1 to STR or DEX (cap 20)
//     epic("Boon of X", "Short description.", ANY);              // +1 to any stat (cap 30)
// Pass no stats for a feat that gives no ability score increase. Call .repeatable() on the
// returned Feat if it can be taken more than once.
class FeatData {

static final String CATEGORY_ASI = "ASI";
static final String CATEGORY_GENERAL = "GENERAL";
static final String CATEGORY_EPIC = "EPIC";
static final String CATEGORY_CUSTOM = "CUSTOM";

static final String ASI_NAME = "Ability Score Improvement (ASI)";
static final String CUSTOM_NAME = "Custom Feat...";

static final int NORMAL_STAT_CAP = 20;
static final int EPIC_STAT_CAP = 30;

private static final String STR = "STR", DEX = "DEX", CON = "CON", INT = "INT", WIS = "WIS", CHA = "CHA";
private static final String[] ANY = {STR, DEX, CON, INT, WIS, CHA};
private static final String[] MENTAL = {INT, WIS, CHA};

static final Map<String, Feat> GENERAL_FEATS = new LinkedHashMap<>();
static final Map<String, Feat> EPIC_BOONS = new LinkedHashMap<>();

static {
    general("Actor", "Advantage on Deception and Performance checks to pass yourself off as someone else; you can mimic voices and sounds you've heard.", CHA);
    general("Athlete", "Gain a Climb Speed equal to your Speed, stand up from prone using only 5 ft. of movement, and make running jumps after moving only 5 ft.", STR, DEX);
    general("Charger", "When you Dash, you gain +10 ft. of Speed; moving 10+ ft. in a straight line before hitting lets you deal +1d8 damage or push the target 10 ft. (once per turn).", STR, DEX);
    general("Chef", "Gain proficiency with Cook's Utensils. Cook food during a Short Rest that heals extra HP, and bake treats that grant Temporary Hit Points.", CON, WIS);
    general("Crossbow Expert", "Ignore the Loading property of crossbows, no Disadvantage firing in melee, and add your ability modifier to light crossbow off-hand attacks.", DEX);
    general("Crusher", "Once per turn when you hit with Bludgeoning damage, push the target 5 ft. On a Critical Hit, attacks against it have Advantage until your next turn.", STR, CON);
    general("Defensive Duelist", "While holding a Finesse weapon, use your Reaction to add your Proficiency Bonus to AC against a melee attack.", DEX);
    general("Dual Wielder", "Make an extra Light-style off-hand attack with non-Light weapons, and draw or stow two weapons at once.", STR, DEX);
    general("Durable", "Advantage on Death Saving Throws, and spend a Hit Die as a Bonus Action to heal.", CON);
    general("Elemental Adept", "Choose Acid, Cold, Fire, Lightning, or Thunder. Your spells ignore Resistance to it, and 1s on its damage dice count as 2s.", MENTAL).repeatable();
    general("Fey-Touched", "Learn Misty Step and one level 1 Divination or Enchantment spell; cast each once per Long Rest for free.", MENTAL);
    general("Grappler", "Hit and grapple in the same attack, gain Advantage on attacks against creatures you grapple, and move grappled creatures without extra cost.", STR, DEX);
    general("Great Weapon Master", "Heavy weapon hits deal extra damage equal to your Proficiency Bonus; a Critical Hit or kill lets you attack again as a Bonus Action.", STR);
    general("Heavily Armored", "Gain training with Heavy armor.", CON, STR);
    general("Heavy Armor Master", "While wearing Heavy armor, reduce Bludgeoning, Piercing, and Slashing damage you take by your Proficiency Bonus.", CON, STR);
    general("Inspiring Leader", "After a Short or Long Rest, give up to six allies Temporary Hit Points equal to your character level + your WIS or CHA modifier.", WIS, CHA);
    general("Keen Mind", "Gain proficiency (or Expertise) in one of Arcana, History, Investigation, Nature, or Religion, and Study as a Bonus Action.", INT);
    general("Lightly Armored", "Gain training with Light armor and Shields.", STR, DEX);
    general("Mage Slayer", "Concentration-breaking hits impose Disadvantage on the save, and once per Long Rest you can turn a failed INT/WIS/CHA save into a success.", STR, DEX);
    general("Martial Weapon Training", "Gain proficiency with Martial weapons.", STR, DEX);
    general("Medium Armor Master", "While wearing Medium armor, you can add up to +3 DEX to your AC instead of +2.", STR, DEX);
    general("Moderately Armored", "Gain training with Medium armor.", STR, DEX);
    general("Mounted Combatant", "Advantage on attacks against unmounted creatures smaller than your mount; protect your mount with Evasion and redirect attacks to yourself.", STR, DEX, WIS);
    general("Observant", "Gain proficiency (or Expertise) in Insight, Investigation, or Perception, and Search as a Bonus Action.", INT, WIS);
    general("Piercer", "Once per turn, reroll one Piercing damage die; Piercing Critical Hits deal one extra damage die.", STR, DEX);
    general("Poisoner", "Your poison damage ignores Resistance. Brew potent poison doses with a Poisoner's Kit.", DEX, INT);
    general("Polearm Master", "Make a Bonus Action attack with the butt of a Quarterstaff, Spear, or Heavy Polearm, and get a Reaction attack when creatures enter your reach.", STR, DEX);
    general("Resilient", "Gain proficiency in saving throws using the ability you increase with this feat.", ANY);
    general("Ritual Caster", "Learn level 1 Ritual spells (one per Proficiency Bonus) and cast one of them quickly once per Long Rest.", MENTAL);
    general("Sentinel", "Opportunity Attacks stop the target's movement, and you can attack creatures that hit your allies or Disengage near you.", STR, DEX);
    general("Shadow-Touched", "Learn Invisibility and one level 1 Illusion or Necromancy spell; cast each once per Long Rest for free.", MENTAL);
    general("Sharpshooter", "Ranged attacks ignore Half and Three-Quarters Cover, no Disadvantage in melee or at long range.", DEX);
    general("Shield Master", "Shield Bash to push or knock Prone, and use your Reaction to take no damage on a successful DEX save against an effect.", STR);
    general("Skill Expert", "Gain proficiency in one skill and Expertise in one skill you're proficient in.", ANY);
    general("Skulker", "Gain Blindsight 10 ft., hide in combat more easily, and missed attacks don't reveal your position.", DEX);
    general("Slasher", "Once per turn, Slashing hits reduce the target's Speed by 10 ft.; Slashing Critical Hits give the target Disadvantage on attacks.", STR, DEX);
    general("Speedy", "Your Speed increases by 10 ft., Dashing ignores Difficult Terrain, and Opportunity Attacks against you have Disadvantage.", DEX, CON);
    general("Spell Sniper", "Learn an attack-roll cantrip; your spell attacks ignore Cover, aren't hindered in melee, and have +60 ft. range.", MENTAL);
    general("Telekinetic", "Learn Mage Hand (invisible, +30 ft. range) and shove creatures 5 ft. with your mind as a Bonus Action.", MENTAL);
    general("Telepathic", "Speak telepathically to creatures within 60 ft. and cast Detect Thoughts once per Long Rest for free.", MENTAL);
    general("War Caster", "Advantage on Concentration saves, cast spells as Opportunity Attacks, and perform Somatic components with your hands full.", MENTAL);
    general("Weapon Master", "Gain the Mastery property of one weapon you're proficient with; you can swap it after a Long Rest.", STR, DEX);

    epic("Boon of Combat Prowess", "When you miss with an attack roll, you can hit instead (once per turn).", ANY);
    epic("Boon of Dimensional Travel", "After you take the Attack or Magic action, teleport up to 30 ft. to a space you can see.", ANY);
    epic("Boon of Energy Resistance", "Gain Resistance to two of Acid, Cold, Fire, Lightning, Necrotic, Poison, Psychic, Radiant, or Thunder; redirect that damage with your Reaction.", ANY);
    epic("Boon of Fate", "When a creature you see within 60 ft. makes a D20 Test, roll 2d4 and add or subtract it (once per initiative/rest).", ANY);
    epic("Boon of Fortitude", "Your Hit Point maximum increases by 40, and healing you receive can be boosted by your CON modifier.", ANY);
    epic("Boon of Irresistible Offense", "Your Bludgeoning, Piercing, and Slashing damage ignores Resistance; a natural 20 deals extra damage equal to the increased score.", STR, DEX);
    epic("Boon of Recovery", "Once per Long Rest, drop to 1 HP instead of 0 and regain half your max HP; also gain a pool of 10d10 healing dice.", ANY);
    epic("Boon of Skill", "Gain proficiency in all skills, and Expertise in one skill.", ANY);
    epic("Boon of Speed", "Your Speed increases by 30 ft., and you can Disengage as a Bonus Action (ending all grapples on you).", ANY);
    epic("Boon of Spell Recall", "Once per Long Rest, cast a level 1-4 spell without expending a spell slot.", MENTAL);
    epic("Boon of the Night Spirit", "While in Dim Light or Darkness, become Invisible as a Bonus Action and gain Resistance to all damage but Psychic and Radiant.", ANY);
    epic("Boon of Truesight", "Gain Truesight with a range of 60 ft.", ANY);
}

private static Feat general(String name, String description, String... stats) {
    Feat f = new Feat(name, CATEGORY_GENERAL, description, stats, NORMAL_STAT_CAP);
    GENERAL_FEATS.put(name, f);
    return f;
}

private static Feat epic(String name, String description, String... stats) {
    Feat f = new Feat(name, CATEGORY_EPIC, description, stats, EPIC_STAT_CAP);
    EPIC_BOONS.put(name, f);
    return f;
}

static final String ASI_DESCRIPTION = "Increase one ability score by 2, or two ability scores by 1 each. Scores can't go above 20.";
static final String CUSTOM_DESCRIPTION = "Enter your own feat: a name, an optional description, and an optional +1 to one ability score (max 20).";

static Feat lookup(String name) {
    Feat f = GENERAL_FEATS.get(name);
    return f != null ? f : EPIC_BOONS.get(name);
}

// ---------------------------------------------------------------
// Level-up rules
// ---------------------------------------------------------------

// Class levels (the level BEING REACHED in that class) that grant a feat choice.
static final int[] STANDARD_FEAT_LEVELS = {4, 8, 12, 16};
static final Map<String, int[]> EXTRA_FEAT_LEVELS = new LinkedHashMap<>();
static final int EPIC_BOON_LEVEL = 19;
static {
    EXTRA_FEAT_LEVELS.put("Fighter", new int[]{6, 14});
    EXTRA_FEAT_LEVELS.put("Rogue", new int[]{10});
}

static final int MAX_LEVEL = 20;

static boolean grantsFeat(String className, int newClassLevel) {
    for (int l : STANDARD_FEAT_LEVELS) if (l == newClassLevel) return true;
    int[] extra = EXTRA_FEAT_LEVELS.get(className);
    if (extra != null) for (int l : extra) if (l == newClassLevel) return true;
    return false;
}

static boolean grantsEpicBoon(int newClassLevel) {
    return newClassLevel == EPIC_BOON_LEVEL;
}

// Classes that can be picked when multiclassing (excludes homebrew/custom placeholders).
static List<String> multiclassOptions() {
    List<String> out = new ArrayList<>();
    for (String c : GameData.CLASS_INFO.keySet()) {
        if (GameData.hitDieMax(c) > 0) out.add(c);
    }
    return out;
}

// Groups class levels into a hit dice pool string, e.g. {Barbarian:2, Fighter:1, Paladin:1} -> "2d12 + 2d10"
static String hitDiceString(Map<String, Integer> classLevels) {
    TreeMap<Integer, Integer> byDie = new TreeMap<>(Collections.reverseOrder());
    for (Map.Entry<String, Integer> en : classLevels.entrySet()) {
        int die = GameData.hitDieMax(en.getKey());
        if (die > 0 && en.getValue() > 0) byDie.merge(die, en.getValue(), Integer::sum);
    }
    List<String> parts = new ArrayList<>();
    for (Map.Entry<Integer, Integer> en : byDie.entrySet()) parts.add(en.getValue() + "d" + en.getKey());
    return parts.isEmpty() ? "—" : String.join(" + ", parts);
}
}

class Feat {
    final String name;
    final String category;      // FeatData.CATEGORY_*
    final String description;
    final String[] statOptions; // abilities this feat may raise by 1 (empty = none)
    final int statCap;          // max score this feat's increase may reach
    boolean repeatable = false;

    Feat(String name, String category, String description, String[] statOptions, int statCap) {
        this.name = name;
        this.category = category;
        this.description = description;
        this.statOptions = statOptions;
        this.statCap = statCap;
    }

    Feat repeatable() {
        this.repeatable = true;
        return this;
    }

    String infoText() {
        String statText = statOptions.length == 0 ? "None"
                : "+1 to " + String.join(" or ", statOptions) + " (max " + statCap + ")";
        return description + "\n\nAbility Score Increase: " + statText
                + ("EPIC".equals(category) ? "\n\nEpic Boon" : "")
                + (repeatable ? "\n\nCan be taken more than once." : "");
    }
}
