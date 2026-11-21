import java.util.*;
import java.util.List;

// Rules for the Combat screen's other actions (2024 PHB) and death saving throws. The flows (overlays, rolls, turn
// economy) are in CombatPanel's "Other actions" and "Death & dying" sections; this file only holds numbers and text.
//
// Effects these actions put on tokens reuse CombatSpells.Effect (saved with the map like spell effects), keyed by name:
//   "Grappled"     : on the grappled creature; caster = grappler uid, dc = escape DC, conds [Grappled]
//   "Hidden"       : on the hider; value = its Dexterity (Stealth) total (the DC to find it), conds [Invisible]
//   "Help"         : on an enemy; the next attack roll against it by one of the helper's allies has Advantage
//   "Help (check)" : on an ally; its next ability check has Advantage
// The two Help effects end at the start of the helper's next turn (casterStarts = 1).
class CombatActions {

static final String GRAPPLED = "Grappled", HIDDEN = "Hidden", HELP = "Help", HELP_CHECK = "Help (check)";

static boolean isActionEffect(String name) {
    return GRAPPLED.equals(name) || HIDDEN.equals(name) || HELP.equals(name) || HELP_CHECK.equals(name);
}

// Rules text shown in an effect's detail overlay
static String effectHow(String name) {
    switch (name) {
        case GRAPPLED:
            return "Grappled: Speed 0, and Disadvantage on attack rolls against any target other than the grappler. "
                    + "The grappler can drag it along (each foot costs 1 extra foot unless it's Tiny or two or more sizes smaller). "
                    + "It ends if the grappler is Incapacitated or the two are ever more than 5 ft. apart, if the grappler lets go, "
                    + "or if the grappled creature escapes: an action for a Strength (Athletics) or Dexterity (Acrobatics) check against the escape DC.";
        case HIDDEN:
            return "Hidden (the Hide action): it has the Invisible condition. Its Dexterity (Stealth) total is the DC for a Wisdom (Perception) "
                    + "check to find it. It stops being hidden when it attacks, casts a spell, makes a sound louder than a whisper, or is found.";
        case HELP:
            return "Help (assist an attack): the next attack roll one of the helper's allies makes against this creature before the start "
                    + "of the helper's next turn has Advantage.";
        case HELP_CHECK:
            return "Help (assist an ability check): this creature's next ability check before the start of the helper's next turn has Advantage. "
                    + "Checks rolled on the Combat screen (Hide, Search, Study, Influence, escaping a grapple) use it up automatically.";
        default:
            return "";
    }
}

// ---------------- sizes ----------------
static int sizeRank(String size) {
    if (size == null) return 2;
    switch (size.trim()) {
        case "Tiny": return 0;
        case "Small": return 1;
        case "Large": return 3;
        case "Huge": return 4;
        case "Gargantuan": return 5;
        default: return 2;
    }
}

// Grapple / Shove: the target can be no more than one size larger than you
static boolean sizeAllows(String attacker, String target) {
    return sizeRank(target) <= sizeRank(attacker) + 1;
}

// Dragging a grappled creature costs extra movement unless it's Tiny or two or more sizes smaller
static boolean dragCostsExtra(String grappler, String grappled) {
    return sizeRank(grappled) > 0 && sizeRank(grappled) > sizeRank(grappler) - 2;
}

// ---------------- numbers ----------------
static int profBonus(Map<?, ?> source, boolean pc) {
    if (source == null) return 2;
    return pc ? CombatSpells.profBonus(source) : CombatSpells.crProficiency(CustomAssets.str(source, "cr"));
}

// Unarmed Strike (Grapple / Shove) save DC: 8 + Strength modifier + Proficiency Bonus
static int unarmedDc(Map<?, ?> source, boolean pc) {
    return 8 + CombatSpells.abilityMod(source, pc, "STR") + profBonus(source, pc);
}

// Skill check bonus: characters use ClassFeatures.skillBonus (proficiency, Expertise, Jack of All Trades);
// NPCs use their stat block's "Skills" line ("Stealth +6"), or the plain ability modifier
static int skillBonus(Map<?, ?> source, boolean pc, String skill) {
    if (source == null) return 0;
    String ability = GameData.SKILL_ABILITY.getOrDefault(skill, "WIS");
    if (pc) return ClassFeatures.skillBonus(source, skill); // proficiency, Expertise, Jack of All Trades, Thaumaturge / Magician
    java.util.regex.Matcher m = java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(skill) + "\\s*([+-−]\\s*\\d+)",
            java.util.regex.Pattern.CASE_INSENSITIVE).matcher(CustomAssets.str(source, "skills"));
    if (m.find()) return Integer.parseInt(m.group(1).replace(" ", "").replace("+", "").replace("−", "-"));
    return CombatSpells.abilityMod(source, false, ability);
}

static String abilityOf(String skill) {
    return GameData.SKILL_ABILITY.getOrDefault(skill, "WIS");
}

// Influence: DC 15 or the monster's Intelligence score, whichever is higher
static int influenceDc(Map<?, ?> target, boolean pc) {
    if (target == null || pc) return 15;
    Integer intel = CustomAssets.abilities(target).get("INT");
    return Math.max(15, intel == null ? 10 : intel);
}

static final String[] INFLUENCE_SKILLS = {"Persuasion", "Deception", "Intimidation", "Performance", "Animal Handling"};
static final String[] SEARCH_SKILLS = {"Perception", "Insight", "Medicine", "Survival"};
static final String[] STUDY_SKILLS = {"Arcana", "History", "Investigation", "Nature", "Religion"};
static final int HIDE_DC = 15, STABILIZE_DC = 10, DEATH_SAVE_DC = 10;

// ---------------- action descriptions (tooltips and info overlays) ----------------
static final Map<String, String> ACTION_INFO = new LinkedHashMap<>();
static {
    ACTION_INFO.put("Attack", "Make an attack with a weapon or an Unarmed Strike. With Extra Attack (or Multiattack) you make several. "
            + "Grapple and Shove are Unarmed Strike options that replace one of those attacks.");
    ACTION_INFO.put("Grapple", "Replaces one attack of the Attack action. A creature within 5 ft., no more than one size larger than you, "
            + "makes a Strength or Dexterity saving throw (its choice) against DC 8 + your Strength modifier + Proficiency Bonus. "
            + "On a failure it has the Grappled condition. You need a free hand.");
    ACTION_INFO.put("Shove", "Replaces one attack of the Attack action. A creature within 5 ft., no more than one size larger than you, "
            + "makes a Strength or Dexterity saving throw (its choice) against DC 8 + your Strength modifier + Proficiency Bonus. "
            + "On a failure you push it 5 ft. away or knock it Prone.");
    ACTION_INFO.put("Magic", "Cast a spell, use a magic item, or use a magical feature. Spells with a Bonus Action or Reaction casting time "
            + "use that instead, and drinking a potion is a Bonus Action.");
    ACTION_INFO.put("Use Item", "Utilize: use a nonmagical object, or draw, swap and stow weapons and shields. Magic items are under Magic.");
    ACTION_INFO.put("Dash", "Gain extra movement equal to your Speed for the rest of the turn.");
    ACTION_INFO.put("Disengage", "Your movement doesn't provoke opportunity attacks for the rest of the turn.");
    ACTION_INFO.put("Dodge", "Until the start of your next turn, attack rolls against you have Disadvantage (if you can see the attacker) "
            + "and you have Advantage on Dexterity saving throws. You lose this if you're Incapacitated or your Speed is 0.");
    ACTION_INFO.put("Help", "Assist an attack: an enemy within 5 ft. of you; the next attack roll one of your allies makes against it before "
            + "your next turn has Advantage. Assist an ability check: an ally's next check has Advantage. "
            + "First aid: a Wisdom (Medicine) check (DC 10) stabilizes a creature at 0 Hit Points within 5 ft.");
    ACTION_INFO.put("Hide", "A Dexterity (Stealth) check (DC 15) while Heavily Obscured or behind Three-Quarters or Total Cover, out of every "
            + "enemy's line of sight. On a success you have the Invisible condition until you attack, cast a spell, make noise, or are found; "
            + "your total is the DC to find you.");
    ACTION_INFO.put("Search", "A Wisdom check: Perception to spot hidden creatures and objects, Insight to read a creature, Medicine to "
            + "diagnose, or Survival to find tracks. A Perception total equal to or higher than a hidden creature's Stealth total finds it.");
    ACTION_INFO.put("Study", "An Intelligence check to recall or work out something: Arcana, History, Investigation, Nature or Religion.");
    ACTION_INFO.put("Influence", "Try to influence a creature with a Charisma check (Persuasion, Deception, Intimidation or Performance) "
            + "or Wisdom (Animal Handling) for a beast. The DC is 15 or the monster's Intelligence score, whichever is higher; "
            + "the DM decides what it's willing to do.");
    ACTION_INFO.put("Ready", "Choose a trigger you can perceive and the response: an attack, a move up to your Speed, or something else. "
            + "When the trigger happens before your next turn, you can take the response with your Reaction.");
}

static String info(String action) {
    return ACTION_INFO.getOrDefault(action, "");
}

// ---------------- death & dying ----------------
static final String DEATH_INFO = "A character who drops to 0 Hit Points falls Unconscious (and Prone, dropping what it holds) unless the "
        + "damage left over equals or exceeds its Hit Point maximum, which kills it outright.\n\n"
        + "At the start of each of its turns it makes a death saving throw: a d20 with no modifiers. 10 or higher is a success, 9 or lower a "
        + "failure. A natural 1 counts as two failures; a natural 20 means it regains 1 Hit Point. Three successes make it Stable; three "
        + "failures and it dies.\n\n"
        + "Taking damage at 0 Hit Points is a failure (two from a Critical Hit), and damage equal to its Hit Point maximum kills it. "
        + "A Stable creature that takes damage starts dying again.\n\n"
        + "First aid (the Help action, a DC 10 Wisdom (Medicine) check), a Healer's Kit, or Spare the Dying stabilizes it. "
        + "Any healing ends Unconscious and resets the tallies. Monsters usually die at 0 Hit Points.";
}
