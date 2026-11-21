import java.util.*;
import java.util.List;

// Class features as modular building blocks (2024 PHB, levels 1-2 built in; homebrew classes and subclasses build
// their own from the same parts in the Asset Library).
//
// A feature is plain JSON, the same shape for built-in and homebrew features (homebrew stores it in the class /
// subclass asset's "features" list; FeatureEditor edits it). Only "name" and "description" are free text, and they're
// cosmetic: everything the rules use is a number, a dropdown or a checkbox.
//   level, name, description, activation (ACTIVATIONS)
//   uses        : Amount (how many uses the feature's own pool has), recharge (RECHARGES)
//   shareWith   : name of another feature of the same class whose uses this one spends ("" = its own); cost: uses per activation
//   spendAny    : the user picks how many uses to spend (Lay on Hands: 1 use = 1 Hit Point healed)
//   target, range (ft.), onlyTypes [creature types]; dcAbility: the ability its save DC uses (8 + PB + modifier)
//   heal, damage: Dice; damageType, damageType2 (choose one when used); save (ability, "" = none), halfOnSave
//   effect  : {duration, sustain, endOnDamage, endOnIncap, noSpells, advStr, recklessAtk, advAgainst, advSpellAtk,
//              conds[], resist[], advSaves[], meleeDamage: Amount, ac, speed, dcBonus, inspiration: Dice, tempHp: Dice}
//   economy : {extraAction, actions[] (Dash / Disengage / Dodge / Hide), strikes, needsAttack, beforeAttack,
//              restore (RESTORES), restorePool (feature name), removeConds[], removeCost}
//   onHit   : {dice: Dice, finesseOrRanged, needsAdvantage, meleeOnly}   (once per turn, added to a weapon hit)
//   passive : {unarmoredAc (ability or ""), unarmoredShield, unarmoredSpeed: Amount, jackOfAllTrades, advSaves[],
//              martialArts: Amount (die size), bonusActions[], weaponTraining[], armorTraining[], weaponMastery: Amount}
//   choice  : {kind (CHOICE_KINDS), count: Amount, options[] (optional subset of the kind's list)}
//   Amount  : {kind (AMOUNTS), value, ability, mult, table[20]}
//   Dice    : {count: Amount, die: Amount, ability ("" / STR..CHA), addLevel, bonus}
//
// Saved on the character record (CharacterStorage.carryOverProgression keeps both on wizard edits):
//   featureUses    : {"Barbarian:Rage": 1}            uses SPENT per pool (pool key = class + ":" + owning feature)
//   featureChoices : {"Rogue:Expertise": [skills]}    picks made on the sheet (Expertise, Fighting Style, Metamagic...)
// Combat copies the uses left when a token is placed and at Start Combat (Tok.featureUses) and never writes back.
class ClassFeatures {

// ---- activation ----
static final String PASSIVE = "Passive", ACTION = "Action", MAGIC = "Magic action", BONUS = "Bonus Action", REACTION = "Reaction",
        FREE = "No action (on your turn)", ON_HIT = "When you hit (once per turn)", INITIATIVE = "When you roll Initiative",
        FAILED_CHECK = "When you fail an ability check", REST = "Outside combat";
static final String[] ACTIVATIONS = {PASSIVE, ACTION, MAGIC, BONUS, REACTION, FREE, ON_HIT, INITIATIVE, FAILED_CHECK, REST};

// ---- amounts ----
static final String A_NONE = "None", A_FIXED = "Fixed number", A_PB = "Proficiency Bonus", A_MOD = "Ability modifier (min 1)",
        A_LEVEL = "Class level", A_HALF = "Half class level (round up)", A_TABLE = "By class level";
static final String[] AMOUNTS = {A_FIXED, A_PB, A_MOD, A_LEVEL, A_HALF, A_TABLE};

// ---- recharge ----
static final String R_LONG = "Long Rest", R_SHORT = "Short or Long Rest", R_SHORT_ONE = "Long Rest (one back on a Short Rest)";
static final String[] RECHARGES = {R_LONG, R_SHORT, R_SHORT_ONE};

// ---- targets ----
static final String T_SELF = "Self", T_ALLY = "One ally or yourself", T_OTHER_ALLY = "One other ally", T_ENEMY = "One enemy",
        T_ANY = "One creature", T_ALLIES = "Allies in range", T_ENEMIES = "Enemies in range";
static final String[] TARGETS = {T_SELF, T_ALLY, T_OTHER_ALLY, T_ENEMY, T_ANY, T_ALLIES, T_ENEMIES};

// ---- durations ----
static final String D_NONE = "Instant", D_TURN = "Until the end of this turn", D_START = "Until the start of your next turn",
        D_END_NEXT = "Until the end of your next turn", D_MINUTE = "1 minute", D_10MIN = "10 minutes", D_HOUR = "1 hour";
static final String[] DURATIONS = {D_NONE, D_TURN, D_START, D_END_NEXT, D_MINUTE, D_10MIN, D_HOUR};

// ---- immediate effects on resources ----
static final String RS_NONE = "None", RS_POOL = "Refill another feature's uses", RS_PACT = "Regain half your Pact Magic slots",
        RS_SLOTS = "Recover spell slots (total levels up to half class level)", RS_CREATE = "Spend uses to create a spell slot",
        RS_CONVERT = "Expend a spell slot to gain uses";
static final String[] RESTORES = {RS_NONE, RS_POOL, RS_PACT, RS_SLOTS, RS_CREATE, RS_CONVERT};

static final String[] ACTION_GRANTS = {"Dash", "Disengage", "Dodge", "Hide"};
static final String[] ABILITIES = {"STR", "DEX", "CON", "INT", "WIS", "CHA"};
static final String[] DICE_ABILITIES = {"", "STR", "DEX", "CON", "INT", "WIS", "CHA"};
static final int[] DIE_SIZES = {4, 6, 8, 10, 12, 20};

// ---- choices made on the sheet ----
static final String C_NONE = "None", C_EXPERTISE = "Expertise", C_SKILL = "Skill proficiency", C_LANGUAGE = "Language",
        C_STYLE = "Fighting Style", C_METAMAGIC = "Metamagic", C_INVOCATION = "Eldritch Invocation",
        C_DIVINE_ORDER = "Divine Order", C_PRIMAL_ORDER = "Primal Order", C_FORM = "Creature form";
static final String[] CHOICE_KINDS = {C_NONE, C_EXPERTISE, C_SKILL, C_LANGUAGE, C_STYLE, C_METAMAGIC, C_INVOCATION, C_DIVINE_ORDER, C_PRIMAL_ORDER, C_FORM};

static final String[] FIGHTING_STYLES = {"Archery", "Blind Fighting", "Defense", "Dueling", "Great Weapon Fighting", "Interception",
        "Protection", "Thrown Weapon Fighting", "Two-Weapon Fighting", "Unarmed Fighting", "Blessed Warrior", "Druidic Warrior"};
static final Map<String, Integer> METAMAGIC = new LinkedHashMap<>();
static {
    METAMAGIC.put("Careful Spell", 1);
    METAMAGIC.put("Distant Spell", 1);
    METAMAGIC.put("Empowered Spell", 1);
    METAMAGIC.put("Extended Spell", 1);
    METAMAGIC.put("Heightened Spell", 2);
    METAMAGIC.put("Quickened Spell", 2);
    METAMAGIC.put("Seeking Spell", 1);
    METAMAGIC.put("Subtle Spell", 1);
    METAMAGIC.put("Transmuted Spell", 1);
    METAMAGIC.put("Twinned Spell", 1);
}
static final String[] INVOCATIONS = {"Agonizing Blast", "Armor of Shadows", "Devil's Sight", "Eldritch Mind", "Eldritch Spear",
        "Fiendish Vigor", "Lessons of the First Ones", "Mask of Many Faces", "Misty Visions", "Otherworldly Leap",
        "Pact of the Blade", "Pact of the Chain", "Pact of the Tome", "Repelling Blast"};

// Rules text for each option a choice offers (shown when a pick is clicked)
static final Map<String, String> OPTION_INFO = new LinkedHashMap<>();
static {
    OPTION_INFO.put("Archery", "+2 bonus to attack rolls you make with Ranged weapons (applied to your attacks).");
    OPTION_INFO.put("Blind Fighting", "You have Blindsight with a range of 10 feet.");
    OPTION_INFO.put("Defense", "While you're wearing Light, Medium or Heavy armor, you gain a +1 bonus to Armor Class (applied to your AC).");
    OPTION_INFO.put("Dueling", "When you're holding a Melee weapon in one hand and no other weapons, you gain a +2 bonus to damage rolls with that weapon (applied to your attacks).");
    OPTION_INFO.put("Great Weapon Fighting", "When you roll damage for a melee attack with a weapon you hold with two hands, treat any 1 or 2 on a damage die as a 3 (applied to your attacks).");
    OPTION_INFO.put("Interception", "When a creature you can see hits another creature within 5 feet of you with an attack roll, you can take a Reaction to reduce the damage by 1d10 + your Proficiency Bonus. You must be holding a Shield or a Simple or Martial weapon.");
    OPTION_INFO.put("Protection", "When a creature you can see attacks a target other than you within 5 feet of you, you can take a Reaction to impose Disadvantage on that attack roll and all others against the target until the start of your next turn. You must be holding a Shield.");
    OPTION_INFO.put("Thrown Weapon Fighting", "When you hit with a ranged attack roll using a weapon that has the Thrown property, you gain a +2 bonus to the damage roll.");
    OPTION_INFO.put("Two-Weapon Fighting", "When you make an extra attack as a result of using a weapon that has the Light property, you can add your ability modifier to the damage of that attack (applied to your attacks).");
    OPTION_INFO.put("Unarmed Fighting", "Your Unarmed Strikes deal 1d6 + Strength modifier Bludgeoning damage (1d8 if you aren't holding any weapons or a Shield). At the start of each of your turns, you can deal 1d4 Bludgeoning damage to one creature Grappled by you.");
    OPTION_INFO.put("Blessed Warrior", "Paladin only. You learn two Cleric cantrips of your choice; they count as Paladin spells for you, using Charisma.");
    OPTION_INFO.put("Druidic Warrior", "Ranger only. You learn two Druid cantrips of your choice; they count as Ranger spells for you, using Wisdom.");
    OPTION_INFO.put("Careful Spell", "1 Sorcery Point. When you cast a spell that forces other creatures to make a saving throw, choose up to your Charisma modifier of them: they automatically succeed and take no damage if they'd normally take half on a success.");
    OPTION_INFO.put("Distant Spell", "1 Sorcery Point. Double a spell's range of 5 feet or more, or make a Touch spell's range 30 feet.");
    OPTION_INFO.put("Empowered Spell", "1 Sorcery Point. When you roll damage for a spell, reroll up to your Charisma modifier of the damage dice and use the new rolls.");
    OPTION_INFO.put("Extended Spell", "1 Sorcery Point. Double a spell's duration of 1 minute or longer (to a maximum of 24 hours), and you have Advantage on Concentration saves for it.");
    OPTION_INFO.put("Heightened Spell", "2 Sorcery Points. One target of your spell has Disadvantage on saves against it.");
    OPTION_INFO.put("Quickened Spell", "2 Sorcery Points. Change a spell's casting time from an action to a Bonus Action (not if you've already cast a level 1+ spell this turn).");
    OPTION_INFO.put("Seeking Spell", "1 Sorcery Point. If you miss with a spell attack roll, reroll the d20 and use the new roll.");
    OPTION_INFO.put("Subtle Spell", "1 Sorcery Point. Cast a spell without Verbal, Somatic or Material components (except costly ones).");
    OPTION_INFO.put("Transmuted Spell", "1 Sorcery Point. Change a spell's damage type (Acid, Cold, Fire, Lightning, Poison or Thunder) to another of those types.");
    OPTION_INFO.put("Twinned Spell", "1 Sorcery Point. A spell that can be cast with a higher slot to target one more creature counts as one level higher for that.");
    OPTION_INFO.put("Agonizing Blast", "Add your Charisma modifier to the damage of a Warlock damage cantrip you choose (usually Eldritch Blast).");
    OPTION_INFO.put("Armor of Shadows", "You can cast Mage Armor on yourself without expending a spell slot.");
    OPTION_INFO.put("Devil's Sight", "You can see normally in Dim Light and Darkness, both magical and nonmagical, within 120 feet.");
    OPTION_INFO.put("Eldritch Mind", "You have Advantage on Constitution saving throws to maintain Concentration.");
    OPTION_INFO.put("Eldritch Spear", "A Warlock damage cantrip with a range of 10+ feet you choose gains range equal to 30 × your Warlock level.");
    OPTION_INFO.put("Fiendish Vigor", "You can cast False Life on yourself without a slot, getting the maximum Temporary Hit Points.");
    OPTION_INFO.put("Lessons of the First Ones", "You gain one Origin feat of your choice.");
    OPTION_INFO.put("Mask of Many Faces", "You can cast Disguise Self without expending a spell slot.");
    OPTION_INFO.put("Misty Visions", "You can cast Silent Image without expending a spell slot.");
    OPTION_INFO.put("Otherworldly Leap", "You can cast Jump on yourself without expending a spell slot.");
    OPTION_INFO.put("Pact of the Blade", "As a Bonus Action, conjure a pact weapon or bond with a magic weapon; you can use Charisma for its attack and damage rolls and deal Necrotic, Psychic or Radiant damage.");
    OPTION_INFO.put("Pact of the Chain", "You learn Find Familiar and can cast it as a Magic action without a slot; your familiar can take special forms.");
    OPTION_INFO.put("Pact of the Tome", "A Book of Shadows holds three cantrips and two level 1 Ritual spells of your choice, always prepared.");
    OPTION_INFO.put("Repelling Blast", "When you hit a Large or smaller creature with a Warlock damage cantrip you choose, you can push it up to 10 feet straight away.");
    OPTION_INFO.put("Protector", "Trained for battle: proficiency with Martial weapons and training with Heavy armor (applied to your attacks and the sheet).");
    OPTION_INFO.put("Thaumaturge", "You know one extra cantrip from the Cleric spell list, and you add your Wisdom modifier (minimum +1) to Intelligence (Arcana or Religion) checks.");
    OPTION_INFO.put("Magician", "You know one extra cantrip from the Druid spell list, and you add your Wisdom modifier (minimum +1) to Intelligence (Arcana or Nature) checks.");
    OPTION_INFO.put("Warden", "Trained for battle: proficiency with Martial weapons and training with Medium armor (applied to your attacks and the sheet).");
}

// Options a choice kind offers (before the feature narrows them down)
static List<String> optionsFor(String kind, Map<?, ?> rec) {
    switch (kind) {
        case C_EXPERTISE: {
            if (rec == null) return Arrays.asList(GameData.ALL_SKILLS);
            return new ArrayList<>(CharacterStorage.recSkillProficiencies(rec));
        }
        case C_SKILL: return Arrays.asList(GameData.ALL_SKILLS);
        case C_LANGUAGE: return new ArrayList<>(GameData.LANGUAGE_INFO.keySet());
        case C_STYLE: return Arrays.asList(FIGHTING_STYLES);
        case C_METAMAGIC: return new ArrayList<>(METAMAGIC.keySet());
        case C_INVOCATION: return Arrays.asList(INVOCATIONS);
        case C_DIVINE_ORDER: return Arrays.asList("Protector", "Thaumaturge");
        case C_PRIMAL_ORDER: return Arrays.asList("Magician", "Warden");
        case C_FORM: { // every NPC (built-in beasts + homebrew); ClassFeatures.choiceOptions narrows it by type, CR and Fly Speed
            List<String> out = new ArrayList<>();
            for (Map<String, Object> n : CustomAssets.placeableNpcs()) if (!out.contains(CustomAssets.str(n, "name"))) out.add(CustomAssets.str(n, "name"));
            return out;
        }
        default: return Collections.emptyList();
    }
}

// Every option a kind could ever offer (the editor's "limit to" list)
static List<String> allOptionsFor(String kind) {
    return optionsFor(kind, null);
}

static String optionInfo(String kind, String option) {
    if (C_EXPERTISE.equals(kind)) return "Expertise in " + option + ": your Proficiency Bonus is doubled for checks with it. "
            + GameData.SKILL_INFO.getOrDefault(option, "");
    if (C_SKILL.equals(kind)) return "Proficiency in " + option + ". " + GameData.SKILL_INFO.getOrDefault(option, "");
    if (C_LANGUAGE.equals(kind)) return GameData.LANGUAGE_INFO.getOrDefault(option, "A language you know.");
    if (C_FORM.equals(kind)) {
        Map<String, Object> n = formNpc(option);
        return n == null ? "A creature form." : CustomAssets.npcStatText(n);
    }
    return OPTION_INFO.getOrDefault(option, option + ".");
}

// The NPC a form name refers to: a homebrew NPC of that name first, then a built-in beast
static Map<String, Object> formNpc(String name) {
    for (Map<String, Object> n : CustomAssets.placeableNpcs()) if (CustomAssets.str(n, "name").equalsIgnoreCase(name)) return n;
    return null;
}

// Does creature `n` fit a feature's form limits at this class level (creature types, max CR, Fly Speed)?
static boolean formAllowed(Feature f, Map<?, ?> n, int clsLevel) {
    if (n == null) return false;
    String type = CustomAssets.str(n, "creatureType").replaceAll("\\s*\\(.*\\)", ""); // "Beast (Dinosaur)" counts as a Beast
    if (!f.formTypes.isEmpty() && !f.formTypes.contains(type)) return false;
    if (f.maxCr != null) {
        int idx = Math.max(0, Math.min(CustomAssets.CHALLENGE_RATINGS.length - 1, f.maxCr.eval(null, clsLevel)));
        if (BeastData.crValue(CustomAssets.str(n, "cr")) > BeastData.crValue(CustomAssets.CHALLENGE_RATINGS[idx]) + 1e-9) return false;
    }
    return !BeastData.flies(n) || clsLevel >= f.flyFrom;
}

static String maxCrText(Feature f, int clsLevel) {
    if (f.maxCr == null) return "any";
    int idx = Math.max(0, Math.min(CustomAssets.CHALLENGE_RATINGS.length - 1, f.maxCr.eval(null, clsLevel)));
    return CustomAssets.CHALLENGE_RATINGS[idx];
}

// ===============================================================
// Amount: a number that may scale with the character
// ===============================================================
static final class Amount {
    String kind = A_FIXED, ability = "CHA";
    int value, mult = 1;
    int[] table = new int[20];

    static Amount fixed(int v) {
        Amount a = new Amount();
        a.value = v;
        return a;
    }

    static Amount of(Object o) {
        Amount a = new Amount();
        if (o instanceof Number) {
            a.value = ((Number) o).intValue();
            return a;
        }
        if (!(o instanceof Map)) return null;
        Map<?, ?> m = (Map<?, ?>) o;
        String k = CustomAssets.str(m, "kind");
        if (A_NONE.equals(k)) return null;
        a.kind = Arrays.asList(AMOUNTS).contains(k) ? k : A_FIXED;
        a.value = CustomAssets.intVal(m, "value", 0);
        a.mult = Math.max(1, CustomAssets.intVal(m, "mult", 1));
        String ab = CustomAssets.str(m, "ability");
        if (Arrays.asList(ABILITIES).contains(ab)) a.ability = ab;
        Object t = m.get("table");
        if (t instanceof List) {
            List<?> l = (List<?>) t;
            int last = 0;
            for (int i = 0; i < 20; i++) {
                if (i < l.size() && l.get(i) instanceof Number) last = ((Number) l.get(i)).intValue();
                a.table[i] = last;
            }
        }
        return a;
    }

    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", kind);
        switch (kind) {
            case A_MOD: m.put("ability", ability); break;
            case A_LEVEL: m.put("mult", mult); break;
            case A_TABLE: {
                List<Object> l = new ArrayList<>();
                for (int v : table) l.add(v);
                m.put("table", l);
                break;
            }
            case A_FIXED: m.put("value", value); break;
            default:
        }
        return m;
    }

    int eval(Map<?, ?> rec, int clsLevel) {
        int lv = Math.max(1, Math.min(20, clsLevel));
        switch (kind) {
            case A_PB: return GameData.proficiencyBonus(rec == null ? lv : CharacterStorage.recLevel(rec));
            case A_MOD: {
                int mod = rec == null ? 0 : GameData.abilityModifier(CharacterStorage.recStatsMap(rec).getOrDefault(ability, 10));
                return Math.max(1, mod);
            }
            case A_LEVEL: return lv * mult;
            case A_HALF: return (lv + 1) / 2;
            case A_TABLE: return table[lv - 1];
            default: return value;
        }
    }

    // "2", "Proficiency Bonus", "CHA modifier (min 1)", "5 × class level", "2 / 3 / 4 by class level"
    String text() {
        switch (kind) {
            case A_PB: return "Proficiency Bonus";
            case A_MOD: return ability + " modifier (min 1)";
            case A_LEVEL: return mult > 1 ? mult + " × class level" : "class level";
            case A_HALF: return "half class level (round up)";
            case A_TABLE: {
                StringBuilder sb = new StringBuilder();
                int prev = Integer.MIN_VALUE;
                for (int i = 0; i < 20; i++) {
                    if (table[i] == prev) continue;
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(table[i]).append(i == 0 ? "" : " at " + (i + 1));
                    prev = table[i];
                }
                return sb + " (by class level)";
            }
            default: return String.valueOf(value);
        }
    }
}

static Amount table(int... perLevel) {
    Amount a = new Amount();
    a.kind = A_TABLE;
    int last = 0;
    for (int i = 0; i < 20; i++) {
        if (i < perLevel.length) last = perLevel[i];
        a.table[i] = last;
    }
    return a;
}

// Table from breakpoints: steps(2, 3, 3, 4) = 2 from level 1, 3 from level 3, 4 from level 4 (pairs: value, from-level)
static Amount steps(int... pairs) {
    int[] t = new int[20];
    for (int i = 0; i + 1 < pairs.length; i += 2) {
        for (int lv = Math.max(1, pairs[i + 1]); lv <= 20; lv++) t[lv - 1] = pairs[i];
    }
    return table(t);
}

static Amount kind(String kind) {
    Amount a = new Amount();
    a.kind = kind;
    return a;
}

static Amount mod(String ability) {
    Amount a = kind(A_MOD);
    a.ability = ability;
    return a;
}

static Amount levelTimes(int mult) {
    Amount a = kind(A_LEVEL);
    a.mult = mult;
    return a;
}

// ===============================================================
// Dice: NdM + ability modifier + class level + flat bonus
// ===============================================================
static class Dice {
    Amount count = Amount.fixed(1), die = Amount.fixed(6);
    String ability = "";
    boolean addLevel;
    int bonus;

    static Dice of(Object o) {
        if (!(o instanceof Map)) return null;
        Map<?, ?> m = (Map<?, ?>) o;
        Dice d = new Dice();
        Amount c = Amount.of(m.get("count"));
        Amount s = Amount.of(m.get("die"));
        d.count = c != null ? c : Amount.fixed(0);
        d.die = s != null ? s : Amount.fixed(6);
        String ab = CustomAssets.str(m, "ability");
        d.ability = Arrays.asList(ABILITIES).contains(ab) ? ab : "";
        d.addLevel = CustomAssets.bool(m, "addLevel");
        d.bonus = CustomAssets.intVal(m, "bonus", 0);
        return d;
    }

    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("count", count.toJson());
        m.put("die", die.toJson());
        if (!ability.isEmpty()) m.put("ability", ability);
        if (addLevel) m.put("addLevel", true);
        if (bonus != 0) m.put("bonus", bonus);
        return m;
    }

    // builder helpers (built-in features)
    Dice ability(String ab) { ability = ab; return this; }
    Dice count(int n, int sides) { count = Amount.fixed(n); die = Amount.fixed(sides); return this; }
    Dice die(Amount sides) { count = Amount.fixed(1); die = sides; return this; }

    int count(Map<?, ?> rec, int lv) { return Math.max(0, count.eval(rec, lv)); }
    int sides(Map<?, ?> rec, int lv) { return Math.max(1, die.eval(rec, lv)); }

    int flat(Map<?, ?> rec, int lv) {
        int f = bonus + (addLevel ? Math.max(1, lv) : 0);
        if (!ability.isEmpty() && rec != null) f += GameData.abilityModifier(CharacterStorage.recStatsMap(rec).getOrDefault(ability, 10));
        return f;
    }

    // "1d10 + 3" for a character, or "1d10 + class level" in the abstract
    String text(Map<?, ?> rec, int lv) {
        if (rec == null) {
            StringBuilder sb = new StringBuilder();
            String c = A_FIXED.equals(count.kind) ? String.valueOf(count.value) : "(" + count.text() + ")";
            String s = A_FIXED.equals(die.kind) ? String.valueOf(die.value) : "(" + die.text() + ")";
            if (!(A_FIXED.equals(count.kind) && count.value == 0)) sb.append(c).append("d").append(s);
            if (!ability.isEmpty()) sb.append(sb.length() > 0 ? " + " : "").append(ability).append(" modifier");
            if (addLevel) sb.append(sb.length() > 0 ? " + " : "").append("class level");
            if (bonus != 0) sb.append(sb.length() > 0 ? (bonus > 0 ? " + " : " − ") : "").append(Math.abs(bonus));
            return sb.length() == 0 ? "0" : sb.toString();
        }
        return WeaponData.diceText(count(rec, lv), sides(rec, lv), flat(rec, lv));
    }

    int roll(Random rng, Map<?, ?> rec, int lv) {
        int total = flat(rec, lv);
        int n = count(rec, lv), s = sides(rec, lv);
        for (int i = 0; i < n; i++) total += rng.nextInt(s) + 1;
        return Math.max(0, total);
    }
}

static Dice dice(int count, int sides) {
    Dice d = new Dice();
    d.count = Amount.fixed(count);
    d.die = Amount.fixed(sides);
    return d;
}

// ===============================================================
// Feature
// ===============================================================
static final class Feature {
    Map<String, Object> json;
    String name = "", description = "", activation = PASSIVE;
    int level = 1;
    // uses
    Amount uses;
    String recharge = R_LONG, shareWith = "";
    int cost = 1;
    boolean spendAny;
    // targets & rolls
    String target = T_SELF, dcAbility = "";
    int range;
    List<String> onlyTypes = new ArrayList<>();
    Dice heal, damage;
    String damageType = "", damageType2 = "", save = "";
    boolean half;
    // lasting effect
    String duration = D_NONE;
    boolean sustain, endOnDamage, endOnIncap, noSpells, advStr, recklessAtk, advAgainst, advSpellAtk;
    boolean shape;          // the target becomes one of the creature forms chosen for this feature (Wild Shape)
    List<String> conds = new ArrayList<>(), resist = new ArrayList<>(), advSaves = new ArrayList<>();
    Amount meleeDamage;
    int ac, speed, dcBonus;
    Dice inspiration, tempHp;
    // economy
    boolean extraAction, needsAttack, beforeAttack;
    List<String> actions = new ArrayList<>(), removeConds = new ArrayList<>();
    int strikes, removeCost;
    String restore = RS_NONE, restorePool = "";
    // on a hit
    Dice hitDice;
    boolean hitFinesseOrRanged, hitNeedsAdvantage, hitMeleeOnly;
    // always on
    String unarmoredAc = "";
    boolean unarmoredShield, jackOfAllTrades;
    Amount unarmoredSpeed, martialArts, weaponMastery;
    List<String> passiveAdvSaves = new ArrayList<>(), bonusActions = new ArrayList<>(), weaponTraining = new ArrayList<>(), armorTraining = new ArrayList<>();
    // choice
    String choiceKind = C_NONE;
    Amount choiceCount;
    List<String> choiceOptions = new ArrayList<>();
    // creature form choices (C_FORM): allowed creature types, max CR (an Amount of indexes into CHALLENGE_RATINGS), Fly Speed from this level
    List<String> formTypes = new ArrayList<>();
    Amount maxCr;
    int flyFrom;

    boolean hasEffect() {
        return !D_NONE.equals(duration);
    }

    boolean usable() {
        return !PASSIVE.equals(activation) && !ON_HIT.equals(activation) && !FAILED_CHECK.equals(activation);
    }

    boolean hasUses() {
        return uses != null && shareWith.isEmpty();
    }

    boolean hostile() {
        return T_ENEMY.equals(target) || T_ENEMIES.equals(target);
    }
}

static Feature parse(Map<?, ?> m) {
    Feature f = new Feature();
    Map<String, Object> copy = new LinkedHashMap<>();
    for (Map.Entry<?, ?> en : m.entrySet()) copy.put(String.valueOf(en.getKey()), en.getValue());
    f.json = copy;
    f.name = CustomAssets.str(m, "name").trim();
    f.description = CustomAssets.str(m, "description");
    f.level = Math.max(1, Math.min(20, CustomAssets.intVal(m, "level", 1)));
    String act = CustomAssets.str(m, "activation");
    f.activation = Arrays.asList(ACTIVATIONS).contains(act) ? act : PASSIVE;
    f.uses = Amount.of(m.get("uses"));
    String rc = CustomAssets.str(m, "recharge");
    f.recharge = Arrays.asList(RECHARGES).contains(rc) ? rc : R_LONG;
    f.shareWith = CustomAssets.str(m, "shareWith").trim();
    f.cost = Math.max(0, CustomAssets.intVal(m, "cost", 1));
    f.spendAny = CustomAssets.bool(m, "spendAny");
    String tg = CustomAssets.str(m, "target");
    f.target = Arrays.asList(TARGETS).contains(tg) ? tg : T_SELF;
    f.range = Math.max(0, CustomAssets.intVal(m, "range", 0));
    f.onlyTypes = CustomAssets.list(m, "onlyTypes");
    String dca = CustomAssets.str(m, "dcAbility");
    f.dcAbility = Arrays.asList(ABILITIES).contains(dca) ? dca : "";
    f.heal = Dice.of(m.get("heal"));
    f.damage = Dice.of(m.get("damage"));
    f.damageType = CustomAssets.str(m, "damageType");
    f.damageType2 = CustomAssets.str(m, "damageType2");
    if ("None".equals(f.damageType2)) f.damageType2 = "";
    String sv = CustomAssets.str(m, "save");
    f.save = Arrays.asList(ABILITIES).contains(sv) ? sv : "";
    f.half = CustomAssets.bool(m, "halfOnSave");
    if (m.get("effect") instanceof Map) {
        Map<?, ?> e = (Map<?, ?>) m.get("effect");
        String d = CustomAssets.str(e, "duration");
        f.duration = Arrays.asList(DURATIONS).contains(d) ? d : D_NONE;
        f.sustain = CustomAssets.bool(e, "sustain");
        f.endOnDamage = CustomAssets.bool(e, "endOnDamage");
        f.endOnIncap = CustomAssets.bool(e, "endOnIncap");
        f.noSpells = CustomAssets.bool(e, "noSpells");
        f.advStr = CustomAssets.bool(e, "advStr");
        f.recklessAtk = CustomAssets.bool(e, "recklessAtk");
        f.advAgainst = CustomAssets.bool(e, "advAgainst");
        f.advSpellAtk = CustomAssets.bool(e, "advSpellAtk");
        f.shape = CustomAssets.bool(e, "shape");
        f.conds = CustomAssets.list(e, "conds");
        f.resist = CustomAssets.list(e, "resist");
        f.advSaves = CustomAssets.list(e, "advSaves");
        f.meleeDamage = Amount.of(e.get("meleeDamage"));
        f.ac = CustomAssets.intVal(e, "ac", 0);
        f.speed = CustomAssets.intVal(e, "speed", 0);
        f.dcBonus = CustomAssets.intVal(e, "dcBonus", 0);
        f.inspiration = Dice.of(e.get("inspiration"));
        f.tempHp = Dice.of(e.get("tempHp"));
    }
    if (m.get("economy") instanceof Map) {
        Map<?, ?> e = (Map<?, ?>) m.get("economy");
        f.extraAction = CustomAssets.bool(e, "extraAction");
        f.needsAttack = CustomAssets.bool(e, "needsAttack");
        f.beforeAttack = CustomAssets.bool(e, "beforeAttack");
        f.actions = CustomAssets.list(e, "actions");
        f.strikes = Math.max(0, CustomAssets.intVal(e, "strikes", 0));
        String rs = CustomAssets.str(e, "restore");
        f.restore = Arrays.asList(RESTORES).contains(rs) ? rs : RS_NONE;
        f.restorePool = CustomAssets.str(e, "restorePool");
        f.removeConds = CustomAssets.list(e, "removeConds");
        f.removeCost = Math.max(0, CustomAssets.intVal(e, "removeCost", 0));
    }
    if (m.get("onHit") instanceof Map) {
        Map<?, ?> e = (Map<?, ?>) m.get("onHit");
        f.hitDice = Dice.of(e.get("dice"));
        f.hitFinesseOrRanged = CustomAssets.bool(e, "finesseOrRanged");
        f.hitNeedsAdvantage = CustomAssets.bool(e, "needsAdvantage");
        f.hitMeleeOnly = CustomAssets.bool(e, "meleeOnly");
    }
    if (m.get("passive") instanceof Map) {
        Map<?, ?> e = (Map<?, ?>) m.get("passive");
        String ua = CustomAssets.str(e, "unarmoredAc");
        f.unarmoredAc = Arrays.asList(ABILITIES).contains(ua) ? ua : "";
        f.unarmoredShield = CustomAssets.bool(e, "unarmoredShield");
        f.unarmoredSpeed = Amount.of(e.get("unarmoredSpeed"));
        f.jackOfAllTrades = CustomAssets.bool(e, "jackOfAllTrades");
        f.passiveAdvSaves = CustomAssets.list(e, "advSaves");
        f.martialArts = Amount.of(e.get("martialArts"));
        f.bonusActions = CustomAssets.list(e, "bonusActions");
        f.weaponTraining = CustomAssets.list(e, "weaponTraining");
        f.armorTraining = CustomAssets.list(e, "armorTraining");
        f.weaponMastery = Amount.of(e.get("weaponMastery"));
    }
    if (m.get("choice") instanceof Map) {
        Map<?, ?> e = (Map<?, ?>) m.get("choice");
        String k = CustomAssets.str(e, "kind");
        f.choiceKind = Arrays.asList(CHOICE_KINDS).contains(k) ? k : C_NONE;
        f.choiceCount = Amount.of(e.get("count"));
        if (f.choiceCount == null) f.choiceCount = Amount.fixed(1);
        f.choiceOptions = CustomAssets.list(e, "options");
        f.formTypes = CustomAssets.list(e, "formTypes");
        f.maxCr = Amount.of(e.get("maxCr"));
        f.flyFrom = Math.max(0, CustomAssets.intVal(e, "flyFrom", 0));
    }
    return f;
}

// ===============================================================
// Built-in features (2024 PHB levels 1-2; Artificer from the 2024 Eberron rules)
// ===============================================================
static final Map<String, List<Feature>> BUILTIN = new LinkedHashMap<>();

// Fluent builder writing the same JSON a homebrew feature has
private static final class B {
    final Map<String, Object> m = new LinkedHashMap<>();
    final String cls;

    B(String cls, int level, String name, String desc) {
        this.cls = cls;
        m.put("level", level);
        m.put("name", name);
        m.put("description", desc);
        m.put("activation", PASSIVE);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sub(String key) {
        Object o = m.get(key);
        if (!(o instanceof Map)) {
            o = new LinkedHashMap<String, Object>();
            m.put(key, o);
        }
        return (Map<String, Object>) o;
    }

    B act(String a) { m.put("activation", a); return this; }
    B uses(Amount a, String recharge) { m.put("uses", a.toJson()); m.put("recharge", recharge); return this; }
    B uses(int n, String recharge) { return uses(Amount.fixed(n), recharge); }
    B share(String feature, int cost) { m.put("shareWith", feature); m.put("cost", cost); return this; }
    B spendAny() { m.put("spendAny", true); return this; }
    B target(String t, int range) { m.put("target", t); m.put("range", range); return this; }
    B only(String... types) { m.put("onlyTypes", new ArrayList<Object>(Arrays.asList(types))); return this; }
    B dc(String ability) { m.put("dcAbility", ability); return this; }
    B heal(Dice d) { m.put("heal", d.toJson()); return this; }
    B damage(Dice d, String type, String type2) { m.put("damage", d.toJson()); m.put("damageType", type); m.put("damageType2", type2); return this; }
    B save(String ability, boolean half) { m.put("save", ability); m.put("halfOnSave", half); return this; }
    B dur(String d) { sub("effect").put("duration", d); return this; }
    B fx(String flag) { sub("effect").put(flag, true); return this; }
    B fxList(String key, String... v) { sub("effect").put(key, new ArrayList<Object>(Arrays.asList(v))); return this; }
    B fxAmount(String key, Amount a) { sub("effect").put(key, a.toJson()); return this; }
    B fxDice(String key, Dice d) { sub("effect").put(key, d.toJson()); return this; }
    B fxInt(String key, int v) { sub("effect").put(key, v); return this; }
    B eco(String flag) { sub("economy").put(flag, true); return this; }
    B ecoList(String key, String... v) { sub("economy").put(key, new ArrayList<Object>(Arrays.asList(v))); return this; }
    B ecoVal(String key, Object v) { sub("economy").put(key, v); return this; }
    B onHit(Dice d, boolean finesseOrRanged, boolean needsAdvantage) {
        Map<String, Object> h = sub("onHit");
        h.put("dice", d.toJson());
        h.put("finesseOrRanged", finesseOrRanged);
        h.put("needsAdvantage", needsAdvantage);
        return this;
    }
    B pas(String key, Object v) { sub("passive").put(key, v instanceof Amount ? ((Amount) v).toJson() : v); return this; }
    B pasList(String key, String... v) { sub("passive").put(key, new ArrayList<Object>(Arrays.asList(v))); return this; }
    B choice(String kind, Amount count, String... options) {
        Map<String, Object> c = sub("choice");
        c.put("kind", kind);
        c.put("count", count.toJson());
        if (options.length > 0) c.put("options", new ArrayList<Object>(Arrays.asList(options)));
        return this;
    }
    // creature form limits: max CR per class level (CR text), Fly Speed allowed from `flyFrom`, creature types
    B forms(Amount maxCrIndex, int flyFrom, String... types) {
        Map<String, Object> c = sub("choice");
        c.put("maxCr", maxCrIndex.toJson());
        c.put("flyFrom", flyFrom);
        c.put("formTypes", new ArrayList<Object>(Arrays.asList(types)));
        return this;
    }

    void add() {
        BUILTIN.computeIfAbsent(cls, k -> new ArrayList<>()).add(parse(m));
    }
}

private static B f(String cls, int level, String name, String desc) {
    return new B(cls, level, name, desc);
}

private static void spellcasting(String cls, int level, String ability, String extra) {
    f(cls, level, "Spellcasting", "You cast spells from the " + cls + " spell list using " + ability
            + ". Your spells, slots and save DC are on the Spells tab." + (extra.isEmpty() ? "" : " " + extra)).add();
}

private static void weaponMastery(String cls, Amount count, String limit) {
    String n = A_FIXED.equals(count.kind) ? String.valueOf(count.value) : count.table[0] + " (more at higher levels)";
    f(cls, 1, "Weapon Mastery", "You can use the mastery properties of " + n + " kinds of weapons" + limit
            + " you're proficient with. Pick them in the Weapon Mastery card; you can change one each Long Rest.")
            .pas("weaponMastery", count).add();
}

static {
    // ---------------- Artificer ----------------
    spellcasting("Artificer", 1, "Intelligence", "You use tools as your spellcasting focus.");
    f("Artificer", 1, "Tinker's Magic", "As a Magic action while holding Tinker's Tools, you create one item from the Tinker's Magic list "
            + "(Ball Bearings, Basket, Bedroll, Bell, Blanket, Block and Tackle, Bucket, Caltrops, Candle, Crowbar, Flask, Jug, Lamp, Net, Oil, "
            + "Paper, Parchment, Pole, Pouch, Rope, Sack, Shovel, Spikes, String, Tinderbox, Torch, Vial) in an unoccupied space within 5 feet. "
            + "It lasts until you finish a Long Rest.")
            .act(MAGIC).uses(mod("INT"), R_LONG).add();
    f("Artificer", 2, "Replicate Magic Item", "You know plans for magic items you can make with Tinker's Tools during a Long Rest. "
            + "You know four plans at level 2 and can have two of the items at once.").add();

    // ---------------- Barbarian ----------------
    f("Barbarian", 1, "Rage", "As a Bonus Action, enter a Rage if you aren't wearing Heavy armor. While it lasts you have Resistance to "
            + "Bludgeoning, Piercing and Slashing damage, a bonus to damage on attacks using Strength, and Advantage on Strength checks and "
            + "saving throws, but you can't cast spells or maintain Concentration. It lasts until the end of your next turn and ends early if "
            + "you're Incapacitated; it's extended each turn you make an attack roll against an enemy or force an enemy to make a saving throw "
            + "(or spend a Bonus Action to extend it), for up to 10 minutes.")
            .act(BONUS).uses(steps(2, 1, 3, 3, 4, 6, 5, 12, 6, 17), R_SHORT_ONE).target(T_SELF, 0)
            .dur(D_END_NEXT).fx("sustain").fx("endOnIncap").fx("noSpells").fx("advStr")
            .fxList("resist", "Bludgeoning", "Piercing", "Slashing").fxAmount("meleeDamage", steps(2, 1, 3, 9, 4, 16)).add();
    f("Barbarian", 1, "Unarmored Defense", "While you aren't wearing armor, your base Armor Class equals 10 + your Dexterity and Constitution "
            + "modifiers. You can use a Shield and still gain this benefit.")
            .pas("unarmoredAc", "CON").pas("unarmoredShield", true).add();
    weaponMastery("Barbarian", steps(2, 1, 3, 4, 4, 10), " (Simple or Martial Melee weapons)");
    f("Barbarian", 2, "Danger Sense", "You have Advantage on Dexterity saving throws unless you have the Incapacitated condition.")
            .pasList("advSaves", "DEX").add();
    f("Barbarian", 2, "Reckless Attack", "When you make your first attack roll on your turn, you can attack recklessly: you have Advantage "
            + "on attack rolls using Strength until the start of your next turn, but attack rolls against you have Advantage during that time.")
            .act(FREE).target(T_SELF, 0).eco("beforeAttack").dur(D_START).fx("recklessAtk").fx("advAgainst").add();

    // ---------------- Bard ----------------
    f("Bard", 1, "Bardic Inspiration", "As a Bonus Action, inspire another creature within 60 feet who can see or hear you. Once within the "
            + "next hour, when it fails a D20 Test (attack roll, saving throw or ability check), it can roll the Bardic Inspiration die and add "
            + "the number rolled, possibly turning the failure into a success. The die is a d6, becoming a d8 at level 5, d10 at 10 and d12 at 15.")
            .act(BONUS).uses(mod("CHA"), R_LONG).target(T_OTHER_ALLY, 60).dur(D_HOUR)
            .fxDice("inspiration", diceTable(1, steps(6, 1, 8, 5, 10, 10, 12, 15))).add();
    spellcasting("Bard", 1, "Charisma", "You can use a Musical Instrument as your spellcasting focus.");
    f("Bard", 2, "Expertise", "Choose two of your skill proficiencies. Your Proficiency Bonus is doubled for any ability check you make with them.")
            .choice(C_EXPERTISE, Amount.fixed(2)).add();
    f("Bard", 2, "Jack of All Trades", "You can add half your Proficiency Bonus (round down) to any ability check you make that uses a skill "
            + "proficiency you lack and that doesn't otherwise use your Proficiency Bonus.")
            .pas("jackOfAllTrades", true).add();

    // ---------------- Cleric ----------------
    spellcasting("Cleric", 1, "Wisdom", "You can use a Holy Symbol as your spellcasting focus.");
    f("Cleric", 1, "Divine Order", "You dedicate yourself to one of these sacred roles: Protector (Martial weapons and Heavy armor training) "
            + "or Thaumaturge (an extra Cleric cantrip, and your Wisdom modifier added to Arcana and Religion checks).")
            .choice(C_DIVINE_ORDER, Amount.fixed(1)).add();
    f("Cleric", 2, "Channel Divinity", "You can channel divine energy for magical effects: Divine Spark and Turn Undead. You can use it twice; "
            + "you regain one use on a Short Rest and all uses on a Long Rest. You gain more uses at levels 6 and 18.")
            .uses(steps(2, 1, 3, 6, 4, 18), R_SHORT_ONE).add();
    f("Cleric", 2, "Divine Spark", "As a Magic action, point your Holy Symbol at another creature you can see within 30 feet. Roll 1d8 and add "
            + "your Wisdom modifier: restore that many Hit Points, or force it to make a Constitution saving throw, taking that much Necrotic or "
            + "Radiant damage on a failure and half as much on a success. The dice increase to 2d8 at level 7, 3d8 at 13 and 4d8 at 18. "
            + "Uses Channel Divinity.")
            .act(MAGIC).share("Channel Divinity", 1).target(T_ANY, 30).dc("WIS")
            .heal(diceTable(steps(1, 1, 2, 7, 3, 13, 4, 18), Amount.fixed(8)).ability("WIS"))
            .damage(diceTable(steps(1, 1, 2, 7, 3, 13, 4, 18), Amount.fixed(8)).ability("WIS"), "Radiant", "Necrotic").save("CON", true).add();
    f("Cleric", 2, "Turn Undead", "As a Magic action, present your Holy Symbol: each Undead of your choice within 30 feet must succeed on a "
            + "Wisdom saving throw or have the Frightened and Incapacitated conditions for 1 minute. It ends early if the creature takes any "
            + "damage. While turned it tries to move as far from you as it can. Uses Channel Divinity.")
            .act(MAGIC).share("Channel Divinity", 1).target(T_ENEMIES, 30).only("Undead").dc("WIS").save("WIS", false)
            .dur(D_MINUTE).fxList("conds", "Frightened", "Incapacitated").fx("endOnDamage").add();

    // ---------------- Druid ----------------
    spellcasting("Druid", 1, "Wisdom", "You can use a Druidic Focus as your spellcasting focus.");
    f("Druid", 1, "Druidic", "You know Druidic, the secret language of druids, and you always have Speak with Animals prepared.").add();
    f("Druid", 1, "Primal Order", "You dedicate yourself to one of these sacred roles: Magician (an extra Druid cantrip, and your Wisdom "
            + "modifier added to Arcana and Nature checks) or Warden (Martial weapons and Medium armor training).")
            .choice(C_PRIMAL_ORDER, Amount.fixed(1)).add();
    f("Druid", 2, "Wild Shape", "As a Bonus Action, shape-shift into a Beast form you know for hours equal to half your Druid level. "
            + "You know four forms at level 2 (CR 1/4 or lower, no Fly Speed), six at level 4 (CR 1/2) and eight at level 8 (CR 1, Fly Speed "
            + "allowed); change one when you finish a Long Rest. In the form you use the beast's size, Speed, AC, attacks and Strength, "
            + "Dexterity and Constitution, but keep your Hit Points, Hit Dice, Intelligence, Wisdom, Charisma, class features, and skill and "
            + "save proficiencies (using the better bonus). You gain Temporary Hit Points equal to your Druid level and can't cast spells. "
            + "It ends early if you're Incapacitated, use Wild Shape again, or leave the form as a Bonus Action.")
            .act(BONUS).uses(steps(2, 1, 3, 6, 4, 17), R_SHORT_ONE).target(T_SELF, 0).dur(D_HOUR).fx("shape").fx("endOnIncap")
            .fxDice("tempHp", levelDice())
            .choice(C_FORM, steps(4, 1, 6, 4, 8, 8)).forms(steps(crIndex("1/4"), 1, crIndex("1/2"), 4, crIndex("1"), 8), 8, "Beast").add();
    f("Druid", 2, "Wild Companion", "As a Magic action, expend a spell slot or a use of Wild Shape to cast Find Familiar without Material "
            + "components. The familiar is a Fey and disappears when you finish a Long Rest.")
            .act(MAGIC).share("Wild Shape", 1).add();

    // ---------------- Fighter ----------------
    f("Fighter", 1, "Fighting Style", "You gain a Fighting Style feat of your choice. Its benefits are applied to your attacks and AC.")
            .choice(C_STYLE, Amount.fixed(1), "Archery", "Blind Fighting", "Defense", "Dueling", "Great Weapon Fighting", "Interception",
                    "Protection", "Thrown Weapon Fighting", "Two-Weapon Fighting", "Unarmed Fighting").add();
    f("Fighter", 1, "Second Wind", "As a Bonus Action, regain Hit Points equal to 1d10 + your Fighter level. You can use it twice; you regain "
            + "one use on a Short Rest and all uses on a Long Rest (more uses at levels 4 and 10).")
            .act(BONUS).uses(steps(2, 1, 3, 4, 4, 10), R_SHORT_ONE).target(T_SELF, 0).heal(levelDice().count(1, 10)).add();
    weaponMastery("Fighter", steps(3, 1, 4, 4, 5, 10, 6, 16), "");
    f("Fighter", 2, "Action Surge", "On your turn, you can take one additional action, except the Magic action. Once used, you can't do so "
            + "again until you finish a Short or Long Rest (two uses at level 17, but only one per turn). In Combat it gives your action back "
            + "after you've used it.")
            .act(FREE).uses(steps(1, 1, 2, 17), R_SHORT).target(T_SELF, 0).eco("extraAction").add();
    f("Fighter", 2, "Tactical Mind", "When you fail an ability check, you can expend a use of Second Wind to roll 1d10 and add it to the "
            + "check, possibly turning it into a success. If it still fails, the use isn't expended. Offered on failed checks in Combat.")
            .act(FAILED_CHECK).share("Second Wind", 1).fxDice("inspiration", dice(1, 10)).add();

    // ---------------- Monk ----------------
    f("Monk", 1, "Martial Arts", "While you're unarmored and not wielding a Shield, your Unarmed Strikes and Monk weapons (Simple Melee "
            + "weapons and Martial Melee weapons with the Light property) can use Dexterity instead of Strength and roll your Martial Arts die "
            + "(d6, d8 at 5, d10 at 11, d12 at 17) in place of their normal damage. When you take the Attack action, you can make one Unarmed "
            + "Strike as a Bonus Action (see Bonus Unarmed Strike).")
            .pas("martialArts", steps(6, 1, 8, 5, 10, 11, 12, 17)).add();
    f("Monk", 1, "Bonus Unarmed Strike", "Martial Arts: when you take the Attack action on your turn, you can make one Unarmed Strike as a Bonus Action.")
            .act(BONUS).target(T_SELF, 0).ecoVal("strikes", 1).eco("needsAttack").add();
    f("Monk", 1, "Unarmored Defense", "While you aren't wearing armor or wielding a Shield, your base Armor Class equals 10 + your Dexterity "
            + "and Wisdom modifiers.")
            .pas("unarmoredAc", "WIS").add();
    f("Monk", 2, "Monk's Focus", "You have Focus Points equal to your Monk level, spent on Flurry of Blows, Patient Defense and Step of the "
            + "Wind. You regain all of them when you finish a Short or Long Rest.")
            .uses(kind(A_LEVEL), R_SHORT).add();
    f("Monk", 2, "Flurry of Blows", "Expend 1 Focus Point to make two Unarmed Strikes as a Bonus Action.")
            .act(BONUS).share("Monk's Focus", 1).target(T_SELF, 0).ecoVal("strikes", 2).add();
    f("Monk", 2, "Patient Defense", "You can take the Disengage action as a Bonus Action. (Spend 1 Focus Point to also Dodge: see Patient Defense (Focus).)")
            .pasList("bonusActions", "Disengage").add();
    f("Monk", 2, "Patient Defense (Focus)", "Expend 1 Focus Point to take both the Disengage and the Dodge actions as a Bonus Action.")
            .act(BONUS).share("Monk's Focus", 1).target(T_SELF, 0).ecoList("actions", "Disengage", "Dodge").add();
    f("Monk", 2, "Step of the Wind", "You can take the Dash action as a Bonus Action. (Spend 1 Focus Point to also Disengage: see Step of the Wind (Focus).)")
            .pasList("bonusActions", "Dash").add();
    f("Monk", 2, "Step of the Wind (Focus)", "Expend 1 Focus Point to take both the Disengage and the Dash actions as a Bonus Action, and "
            + "your jump distance is doubled for the turn.")
            .act(BONUS).share("Monk's Focus", 1).target(T_SELF, 0).ecoList("actions", "Dash", "Disengage").add();
    f("Monk", 2, "Unarmored Movement", "Your Speed increases while you aren't wearing armor or wielding a Shield: +10 feet, rising to +15 at "
            + "level 6, +20 at 10, +25 at 14 and +30 at 18.")
            .pas("unarmoredSpeed", steps(10, 1, 15, 6, 20, 10, 25, 14, 30, 18)).add();
    f("Monk", 2, "Uncanny Metabolism", "When you roll Initiative, you can regain all expended Focus Points and Hit Points equal to a roll of "
            + "your Martial Arts die + your Monk level. Once per Long Rest. In Combat it's offered during the first round.")
            .act(INITIATIVE).uses(1, R_LONG).target(T_SELF, 0).ecoVal("restore", RS_POOL).ecoVal("restorePool", "Monk's Focus")
            .heal(levelDice().die(steps(6, 1, 8, 5, 10, 11, 12, 17))).add();

    // ---------------- Paladin ----------------
    f("Paladin", 1, "Lay on Hands", "Your blessed touch heals. You have a pool of healing equal to 5 × your Paladin level, restored on a Long "
            + "Rest. As a Bonus Action, touch a creature (or yourself) and spend any number of points to restore that many Hit Points; you can "
            + "also spend 5 points to remove the Poisoned condition.")
            .act(BONUS).uses(levelTimes(5), R_LONG).spendAny().target(T_ALLY, 5).heal(dice(0, 6))
            .ecoList("removeConds", "Poisoned").ecoVal("removeCost", 5).add();
    spellcasting("Paladin", 1, "Charisma", "You can use a Holy Symbol as your spellcasting focus.");
    weaponMastery("Paladin", Amount.fixed(2), "");
    f("Paladin", 2, "Fighting Style", "You gain a Fighting Style feat of your choice, or Blessed Warrior (two Cleric cantrips).")
            .choice(C_STYLE, Amount.fixed(1), "Archery", "Blind Fighting", "Defense", "Dueling", "Great Weapon Fighting",
                    "Interception", "Protection", "Thrown Weapon Fighting", "Two-Weapon Fighting", "Unarmed Fighting", "Blessed Warrior").add();
    f("Paladin", 2, "Paladin's Smite", "You always have Divine Smite prepared, and you can cast it once per Long Rest without expending a "
            + "spell slot. Ready it from the Magic picker or cast it from the attack roll after a hit.").add();

    // ---------------- Ranger ----------------
    spellcasting("Ranger", 1, "Wisdom", "You can use a Druidic Focus as your spellcasting focus.");
    f("Ranger", 1, "Favored Enemy", "You always have Hunter's Mark prepared, and you can cast it twice per Long Rest without a spell slot "
            + "(more at higher levels). Combat tracks those free casts.").add();
    weaponMastery("Ranger", Amount.fixed(2), "");
    f("Ranger", 2, "Deft Explorer", "Expertise: choose one of your skill proficiencies; your Proficiency Bonus is doubled for checks with it.")
            .choice(C_EXPERTISE, Amount.fixed(1)).add();
    f("Ranger", 2, "Deft Explorer: Languages", "You know two languages of your choice.")
            .choice(C_LANGUAGE, Amount.fixed(2)).add();
    f("Ranger", 2, "Fighting Style", "You gain a Fighting Style feat of your choice, or Druidic Warrior (two Druid cantrips).")
            .choice(C_STYLE, Amount.fixed(1), "Archery", "Blind Fighting", "Defense", "Dueling", "Great Weapon Fighting",
                    "Interception", "Protection", "Thrown Weapon Fighting", "Two-Weapon Fighting", "Unarmed Fighting", "Druidic Warrior").add();

    // ---------------- Rogue ----------------
    f("Rogue", 1, "Expertise", "Choose two of your skill proficiencies (Sleight of Hand and Stealth are popular). Your Proficiency Bonus is "
            + "doubled for any ability check you make with them.")
            .choice(C_EXPERTISE, Amount.fixed(2)).add();
    f("Rogue", 1, "Sneak Attack", "Once per turn, you can deal extra damage to one creature you hit with an attack roll if you have Advantage "
            + "on the roll and the attack uses a Finesse or Ranged weapon. You don't need Advantage if an ally is within 5 feet of the target, "
            + "isn't Incapacitated, and you don't have Disadvantage. The extra damage is 1d6 per two Rogue levels (round up). "
            + "Combat adds it to qualifying hits automatically.")
            .act(ON_HIT).onHit(diceTable(kind(A_HALF), Amount.fixed(6)), true, true).add();
    f("Rogue", 1, "Thieves' Cant", "You know Thieves' Cant and one other language of your choice.")
            .choice(C_LANGUAGE, Amount.fixed(1)).add();
    weaponMastery("Rogue", Amount.fixed(2), " (weapons with Finesse or Light)");
    f("Rogue", 2, "Cunning Action", "On your turn, you can take one of these actions as a Bonus Action: Dash, Disengage or Hide.")
            .pasList("bonusActions", "Dash", "Disengage", "Hide").add();

    // ---------------- Sorcerer ----------------
    spellcasting("Sorcerer", 1, "Charisma", "You can use an Arcane Focus as your spellcasting focus.");
    f("Sorcerer", 1, "Innate Sorcery", "As a Bonus Action, unleash your innate magic for 1 minute: the spell save DC of your Sorcerer spells "
            + "increases by 1, and you have Advantage on the attack rolls of Sorcerer spells you cast. Twice per Long Rest.")
            .act(BONUS).uses(2, R_LONG).target(T_SELF, 0).dur(D_MINUTE).fxInt("dcBonus", 1).fx("advSpellAtk").add();
    f("Sorcerer", 2, "Font of Magic", "You have Sorcery Points equal to your Sorcerer level, regained on a Long Rest. Spend them on Metamagic, "
            + "or convert between them and spell slots (see Create Spell Slot and Convert Spell Slot).")
            .uses(kind(A_LEVEL), R_LONG).add();
    f("Sorcerer", 2, "Font of Magic: Create Spell Slot", "As a Bonus Action, spend Sorcery Points to create a spell slot: 2 points for a level 1 "
            + "slot, 3 for level 2, 5 for level 3, 6 for level 4, 7 for level 5 (your Sorcerer level must be at least that). It vanishes on a Long Rest.")
            .act(BONUS).share("Font of Magic", 0).ecoVal("restore", RS_CREATE).ecoVal("restorePool", "Font of Magic").add();
    f("Sorcerer", 2, "Font of Magic: Convert Spell Slot", "Without an action on your turn, expend a spell slot to gain Sorcery Points equal to its level.")
            .act(FREE).share("Font of Magic", 0).ecoVal("restore", RS_CONVERT).ecoVal("restorePool", "Font of Magic").add();
    f("Sorcerer", 2, "Metamagic", "You gain two Metamagic options, fueled by Sorcery Points, to alter your spells as you cast them. "
            + "Using one in Combat spends its points; apply its effect to the spell you cast.")
            .act(FREE).share("Font of Magic", 1).choice(C_METAMAGIC, Amount.fixed(2)).add();

    // ---------------- Warlock ----------------
    f("Warlock", 1, "Eldritch Invocations", "You gain one Eldritch Invocation of your choice (three at level 2, more later). "
            + "Click a pick for its rules.")
            .choice(C_INVOCATION, steps(1, 1, 3, 2, 5, 5, 6, 7, 7, 9, 8, 12, 9, 15, 10, 18)).add();
    f("Warlock", 1, "Pact Magic", "You cast Warlock spells using Charisma. Your Pact Magic slots are all the same level and come back on a "
            + "Short or Long Rest. Your slots are on the Spells tab.").add();
    f("Warlock", 2, "Magical Cunning", "You can perform an esoteric rite for 1 minute. At the end of it, you regain expended Pact Magic spell "
            + "slots but no more than half your maximum (round up). Once per Long Rest. Use it from this card.")
            .act(REST).uses(1, R_LONG).ecoVal("restore", RS_PACT).add();

    // ---------------- Wizard ----------------
    spellcasting("Wizard", 1, "Intelligence", "Your spellbook holds the spells you know; you prepare from it after a Long Rest.");
    f("Wizard", 1, "Ritual Adept", "You can cast any spell as a Ritual if it has the Ritual tag and it's in your spellbook. You needn't have it prepared.").add();
    f("Wizard", 1, "Arcane Recovery", "When you finish a Short Rest, you can choose expended spell slots to recover with a combined level no "
            + "more than half your Wizard level (round up), and none of level 6 or higher. Once per Long Rest. Use it from this card.")
            .act(REST).uses(1, R_LONG).ecoVal("restore", RS_SLOTS).add();
    f("Wizard", 2, "Scholar", "Choose one of these skills you're proficient in: Arcana, History, Investigation, Medicine, Nature or Religion. "
            + "You have Expertise in it.")
            .choice(C_EXPERTISE, Amount.fixed(1), "Arcana", "History", "Investigation", "Medicine", "Nature", "Religion").add();
}

static int crIndex(String cr) {
    return Math.max(0, Arrays.asList(CustomAssets.CHALLENGE_RATINGS).indexOf(cr));
}

// Dice whose count / die come from amounts
private static Dice diceTable(Amount count, Amount die) {
    Dice d = new Dice();
    d.count = count;
    d.die = die;
    return d;
}

private static Dice diceTable(int count, Amount die) {
    return diceTable(Amount.fixed(count), die);
}

// "class level" with no dice (Wild Shape temp HP); chain .count() / .die() to add dice
private static Dice levelDice() {
    Dice d = new Dice();
    d.count = Amount.fixed(0);
    d.addLevel = true;
    return d;
}

// ===============================================================
// A character's features
// ===============================================================
static final class Active {
    Feature f;
    String cls = "", source = "";   // class the feature belongs to; "Barbarian 1" / "Path of the Tide 3"
    int clsLevel = 1;
    boolean homebrew;

    String key() { return cls + ":" + f.name; }
}

// Built-in features of a class (empty for homebrew classes or classes without data)
static List<Feature> builtin(String cls) {
    List<Feature> l = BUILTIN.get(cls);
    return l == null ? Collections.emptyList() : l;
}

// Features from a homebrew class / subclass asset's "features" entries
static List<Feature> fromAsset(Map<String, Object> asset) {
    List<Feature> out = new ArrayList<>();
    if (asset == null) return out;
    for (Map<String, Object> e : CustomAssets.entries(asset, "features")) {
        Feature f = parse(e);
        if (!f.name.isEmpty()) out.add(f);
    }
    out.sort(Comparator.comparingInt(x -> x.level));
    return out;
}

// Every feature the character has right now, in class order (primary first), then level
static List<Active> active(Map<?, ?> rec) {
    List<Active> out = new ArrayList<>();
    if (rec == null) return out;
    Map<String, String> subs = CustomAssets.recSubclasses(rec);
    for (Map.Entry<String, Integer> en : CharacterStorage.recClassLevels(rec).entrySet()) {
        String cls = en.getKey();
        int lv = en.getValue();
        Map<String, Object> ca = CustomAssets.classAsset(cls);
        List<Feature> base = ca != null ? fromAsset(ca) : builtin(cls);
        for (Feature f : base) {
            if (f.level > lv) continue;
            Active a = new Active();
            a.f = f;
            a.cls = cls;
            a.clsLevel = lv;
            a.homebrew = ca != null;
            a.source = cls + " " + f.level;
            out.add(a);
        }
        String subName = subs.get(cls);
        Map<String, Object> sa = subName == null ? null : CustomAssets.subclassAsset(cls, subName);
        if (sa != null) {
            for (Feature f : fromAsset(sa)) {
                if (f.level > lv) continue;
                Active a = new Active();
                a.f = f;
                a.cls = cls;
                a.clsLevel = lv;
                a.homebrew = true;
                a.source = subName + " " + f.level;
                out.add(a);
            }
        }
    }
    return out;
}

static Active find(List<Active> all, String cls, String name) {
    for (Active a : all) if (a.cls.equals(cls) && a.f.name.equalsIgnoreCase(name)) return a;
    return null;
}

// The feature whose uses `a` spends (itself, or the one it shares with); null if that one isn't available
static Active poolOwner(List<Active> all, Active a) {
    if (a.f.shareWith.isEmpty()) return a.f.uses != null ? a : null;
    Active o = find(all, a.cls, a.f.shareWith);
    return o != null && o.f.uses != null ? o : null;
}

static String poolKey(List<Active> all, Active a) {
    Active o = poolOwner(all, a);
    return o == null ? "" : o.key();
}

static int maxUses(Active owner, Map<?, ?> rec) {
    return owner == null || owner.f.uses == null ? 0 : Math.max(0, owner.f.uses.eval(rec, owner.clsLevel));
}

// ---------------- uses on the sheet ----------------
static Map<String, Integer> usedMap(Map<?, ?> rec) {
    return CombatPanel.intMap(rec.get("featureUses"));
}

static int usesLeft(Map<?, ?> rec, Active owner) {
    if (owner == null) return 0;
    return Math.max(0, maxUses(owner, rec) - usedMap(rec).getOrDefault(owner.key(), 0));
}

// Uses left in every pool (combat copies this when a token is placed and at Start Combat)
static Map<String, Integer> poolsLeft(Map<?, ?> rec) {
    Map<String, Integer> out = new LinkedHashMap<>();
    for (Active a : active(rec)) if (a.f.hasUses()) out.put(a.key(), usesLeft(rec, a));
    return out;
}

static void setUsed(Map<String, Object> rec, String key, int used) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (Map.Entry<String, Integer> en : usedMap(rec).entrySet()) m.put(en.getKey(), en.getValue());
    if (used <= 0) m.remove(key);
    else m.put(key, used);
    rec.put("featureUses", m);
}

// Short Rest: Short-Rest pools refill, "one back" pools regain one, Pact Magic slots return. Long Rest: everything.
static void rest(Map<String, Object> rec, boolean longRest) {
    List<Active> all = active(rec);
    Map<String, Object> m = new LinkedHashMap<>();
    for (Map.Entry<String, Integer> en : usedMap(rec).entrySet()) {
        Active owner = null;
        for (Active a : all) if (a.key().equals(en.getKey())) owner = a;
        int used = en.getValue();
        if (longRest || owner == null) used = 0;
        else if (R_SHORT.equals(owner.f.recharge)) used = 0;
        else if (R_SHORT_ONE.equals(owner.f.recharge)) used = Math.max(0, used - 1);
        if (used > 0) m.put(en.getKey(), used);
    }
    rec.put("featureUses", m);
    rec.remove("pactSlotsUsed");
    if (longRest) {
        rec.remove("spellSlotsUsed");
        rec.remove("freeCastsUsed");
    }
}

// ---------------- choices ----------------
static List<String> choices(Map<?, ?> rec, Active a) {
    Object v = rec.get("featureChoices");
    if (v instanceof Map && ((Map<?, ?>) v).get(a.key()) instanceof List) return CustomAssets.list((Map<?, ?>) v, a.key());
    // Built-in classes: the level-1 wizard decision of the same name ("Divine Order?", "Fighting Style?", "Eldritch Invocation?")
    List<DecisionItem> decisions = GameData.CLASS_DECISIONS.get(a.cls);
    if (decisions != null && !a.homebrew) {
        Map<String, List<String>> picks = CharacterStorage.recChoicesMap(rec);
        for (DecisionItem item : decisions) {
            if (!"CHOICE".equals(item.type)) continue;
            String label = item.label.replace("?", "").trim();
            if (label.equalsIgnoreCase(a.f.choiceKind) || label.equalsIgnoreCase(a.f.name)
                    || (C_INVOCATION.equals(a.f.choiceKind) && label.startsWith("Eldritch Invocation"))
                    || (C_STYLE.equals(a.f.choiceKind) && label.startsWith("Fighting Style") && a.f.level == 1)) {
                List<String> p = picks.get(item.label);
                if (p != null) return new ArrayList<>(p);
            }
        }
    }
    return new ArrayList<>();
}

@SuppressWarnings("unchecked")
static void setChoices(Map<String, Object> rec, Active a, List<String> picks) {
    Map<String, Object> m = new LinkedHashMap<>();
    if (rec.get("featureChoices") instanceof Map) m.putAll((Map<String, Object>) rec.get("featureChoices"));
    m.put(a.key(), new ArrayList<Object>(picks));
    rec.put("featureChoices", m);
}

static int choiceCount(Map<?, ?> rec, Active a) {
    return a.f.choiceCount == null ? 0 : Math.max(0, a.f.choiceCount.eval(rec, a.clsLevel));
}

// Options the feature offers this character (its own subset of the kind's list, if it has one)
static List<String> choiceOptions(Map<?, ?> rec, Active a) {
    List<String> base = optionsFor(a.f.choiceKind, rec);
    if (C_FORM.equals(a.f.choiceKind)) { // creature type, max CR and Fly Speed limits at this class level
        List<String> fit = new ArrayList<>();
        for (String n : base) if (formAllowed(a.f, formNpc(n), a.clsLevel)) fit.add(n);
        base = fit;
    }
    if (!a.f.choiceOptions.isEmpty()) {
        List<String> out = new ArrayList<>();
        for (String o : a.f.choiceOptions) if (base.contains(o)) out.add(o);
        base = out;
    }
    if (C_EXPERTISE.equals(a.f.choiceKind)) {
        // a skill can't have Expertise twice
        Set<String> elsewhere = new HashSet<>();
        for (Active o : active(rec)) if (o != a && C_EXPERTISE.equals(o.f.choiceKind) && !o.key().equals(a.key())) elsewhere.addAll(choices(rec, o));
        List<String> out = new ArrayList<>();
        for (String s : base) if (!elsewhere.contains(s)) out.add(s);
        return out;
    }
    return base;
}

// Every pick of one kind across the character's features
static Set<String> picksOfKind(Map<?, ?> rec, String kind) {
    Set<String> out = new LinkedHashSet<>();
    if (rec == null) return out;
    for (Active a : active(rec)) if (kind.equals(a.f.choiceKind)) out.addAll(choices(rec, a));
    return out;
}

// Where each pick of one kind comes from: {pick: "Rogue 1 (Expertise)"}
static Map<String, String> pickSources(Map<?, ?> rec, String kind) {
    Map<String, String> out = new LinkedHashMap<>();
    if (rec == null) return out;
    for (Active a : active(rec)) {
        if (!kind.equals(a.f.choiceKind)) continue;
        for (String p : choices(rec, a)) out.putIfAbsent(p, a.f.name + " (" + a.source + ")");
    }
    return out;
}

// ===============================================================
// Passive rules (sheet + combat)
// ===============================================================
static Set<String> expertise(Map<?, ?> rec) {
    return picksOfKind(rec, C_EXPERTISE);
}

static boolean jackOfAllTrades(Map<?, ?> rec) {
    for (Active a : active(rec)) if (a.f.jackOfAllTrades) return true;
    return false;
}

// Ability check bonus for a skill: modifier + PB (proficient) or 2 × PB (Expertise) or PB / 2 (Jack of All Trades),
// plus Thaumaturge / Magician's Wisdom modifier on Arcana and Religion / Nature
static int skillBonus(Map<?, ?> rec, String skill) {
    String ability = GameData.SKILL_ABILITY.getOrDefault(skill, "WIS");
    Map<String, Integer> stats = CharacterStorage.recStatsMap(rec);
    int mod = GameData.abilityModifier(stats.getOrDefault(ability, 10));
    int pb = GameData.proficiencyBonus(CharacterStorage.recLevel(rec));
    boolean prof = CharacterStorage.recSkillProficiencies(rec).contains(skill);
    int total = mod;
    if (prof) total += expertise(rec).contains(skill) ? 2 * pb : pb;
    else if (jackOfAllTrades(rec)) total += pb / 2;
    total += orderSkillBonus(rec, skill);
    return total;
}

// Thaumaturge (Arcana, Religion) / Magician (Arcana, Nature): + Wisdom modifier (min +1)
static int orderSkillBonus(Map<?, ?> rec, String skill) {
    Set<String> orders = new HashSet<>(picksOfKind(rec, C_DIVINE_ORDER));
    orders.addAll(picksOfKind(rec, C_PRIMAL_ORDER));
    boolean t = orders.contains("Thaumaturge") && ("Arcana".equals(skill) || "Religion".equals(skill));
    boolean m = orders.contains("Magician") && ("Arcana".equals(skill) || "Nature".equals(skill));
    if (!t && !m) return 0;
    return Math.max(1, GameData.abilityModifier(CharacterStorage.recStatsMap(rec).getOrDefault("WIS", 10)));
}

static String orderSkillSource(Map<?, ?> rec, String skill) {
    if (orderSkillBonus(rec, skill) == 0) return "";
    Set<String> orders = new HashSet<>(picksOfKind(rec, C_DIVINE_ORDER));
    orders.addAll(picksOfKind(rec, C_PRIMAL_ORDER));
    return orders.contains("Thaumaturge") && !"Nature".equals(skill) ? "Thaumaturge (Divine Order)" : "Magician (Primal Order)";
}

// Saving throws with Advantage from always-on features (Danger Sense)
static Map<String, String> advantageSaves(Map<?, ?> rec) {
    Map<String, String> out = new LinkedHashMap<>();
    for (Active a : active(rec)) for (String s : a.f.passiveAdvSaves) out.putIfAbsent(s, a.f.name);
    return out;
}

// Actions the character can take as a Bonus Action for free (Cunning Action, Patient Defense, Step of the Wind)
static Map<String, String> bonusActions(Map<?, ?> rec) {
    Map<String, String> out = new LinkedHashMap<>();
    for (Active a : active(rec)) for (String s : a.f.bonusActions) out.putIfAbsent(s, a.f.name);
    return out;
}

// Weapon / armor training from features (Divine Order: Protector, Primal Order: Warden, homebrew)
static Set<String> weaponTraining(Map<?, ?> rec) {
    Set<String> out = new LinkedHashSet<>();
    for (Active a : active(rec)) out.addAll(a.f.weaponTraining);
    Set<String> orders = new HashSet<>(picksOfKind(rec, C_DIVINE_ORDER));
    orders.addAll(picksOfKind(rec, C_PRIMAL_ORDER));
    if (orders.contains("Protector") || orders.contains("Warden")) out.add("Martial");
    return out;
}

static Set<String> armorTraining(Map<?, ?> rec) {
    Set<String> out = new LinkedHashSet<>();
    for (Active a : active(rec)) out.addAll(a.f.armorTraining);
    Set<String> orders = new HashSet<>(picksOfKind(rec, C_DIVINE_ORDER));
    orders.addAll(picksOfKind(rec, C_PRIMAL_ORDER));
    if (orders.contains("Protector")) out.add("Heavy");
    if (orders.contains("Warden")) out.add("Medium");
    return out;
}

// Weapon Mastery count a class's features give at a class level (0 if none)
static int masteryCount(String cls, int clsLevel) {
    Map<String, Object> ca = CustomAssets.classAsset(cls);
    List<Feature> fs = ca != null ? fromAsset(ca) : builtin(cls);
    int best = 0;
    for (Feature f : fs) if (f.level <= clsLevel && f.weaponMastery != null) best = Math.max(best, f.weaponMastery.eval(null, clsLevel));
    return best;
}

static boolean wearsArmor(Map<?, ?> rec) {
    for (String e : CharacterStorage.recList(rec, "equipment")) {
        GameData.ArmorInfo info = GameData.ARMOR_AC.get(GameData.stripEquipmentCount(e));
        if (info != null && !info.isShield) return true;
    }
    return false;
}

static boolean holdsShield(Map<?, ?> rec) {
    WeaponRules.Hands h = WeaponRules.hands(rec);
    return WeaponRules.isShield(h.main) || WeaponRules.isShield(h.off);
}

// Unarmored Defense: the best "10 + DEX + ability" a feature offers while unarmored (0 = none applies)
static int unarmoredAc(Map<?, ?> rec) {
    if (rec == null || wearsArmor(rec)) return 0;
    boolean shield = holdsShield(rec);
    Map<String, Integer> stats = CharacterStorage.recStatsMap(rec);
    int dex = GameData.abilityModifier(stats.getOrDefault("DEX", 10));
    int best = 0;
    for (Active a : active(rec)) {
        if (a.f.unarmoredAc.isEmpty() || (shield && !a.f.unarmoredShield)) continue;
        best = Math.max(best, 10 + dex + GameData.abilityModifier(stats.getOrDefault(a.f.unarmoredAc, 10)));
    }
    return best;
}

static String unarmoredAcSource(Map<?, ?> rec) {
    if (rec == null || wearsArmor(rec)) return "";
    boolean shield = holdsShield(rec);
    Map<String, Integer> stats = CharacterStorage.recStatsMap(rec);
    int dex = GameData.abilityModifier(stats.getOrDefault("DEX", 10));
    int best = 0;
    String src = "";
    for (Active a : active(rec)) {
        if (a.f.unarmoredAc.isEmpty() || (shield && !a.f.unarmoredShield)) continue;
        int v = 10 + dex + GameData.abilityModifier(stats.getOrDefault(a.f.unarmoredAc, 10));
        if (v > best) {
            best = v;
            src = a.f.name + " (" + a.cls + "): 10 + DEX + " + a.f.unarmoredAc;
        }
    }
    return src;
}

// Unarmored Movement: extra Speed while wearing no armor and holding no Shield
static int speedBonus(Map<?, ?> rec) {
    if (rec == null || wearsArmor(rec) || holdsShield(rec)) return 0;
    int best = 0;
    for (Active a : active(rec)) if (a.f.unarmoredSpeed != null) best = Math.max(best, a.f.unarmoredSpeed.eval(rec, a.clsLevel));
    return best;
}

static String speedBonusSource(Map<?, ?> rec) {
    if (speedBonus(rec) <= 0) return "";
    for (Active a : active(rec)) if (a.f.unarmoredSpeed != null) return a.f.name + " (" + a.cls + ")";
    return "";
}

// Martial Arts die size (0 = no Martial Arts, or wearing armor / holding a Shield)
static int martialArtsDie(Map<?, ?> rec) {
    if (rec == null || wearsArmor(rec) || holdsShield(rec)) return 0;
    int best = 0;
    for (Active a : active(rec)) if (a.f.martialArts != null) best = Math.max(best, a.f.martialArts.eval(rec, a.clsLevel));
    return best;
}

// Fighting styles picked through features (Paladin / Ranger level 2, homebrew)
static Set<String> styles(Map<?, ?> rec) {
    return picksOfKind(rec, C_STYLE);
}

// ===============================================================
// Text: what a feature does, for the sheet card, the Asset Library and Combat tooltips
// ===============================================================
static String activationShort(String act) {
    switch (act) {
        case FREE: return "No action";
        case ON_HIT: return "On a hit";
        case INITIATIVE: return "On Initiative";
        case FAILED_CHECK: return "On a failed check";
        default: return act;
    }
}

// Mechanics lines; with `rec` the numbers are the character's (null: the formulas)
static List<String> mechanics(Feature f, Map<?, ?> rec, int lv, List<Active> all, String cls) {
    List<String> out = new ArrayList<>();
    if (!PASSIVE.equals(f.activation)) {
        String t = f.activation;
        if (!T_SELF.equals(f.target) && f.usable()) t += " · " + f.target + (f.range > 0 ? " within " + f.range + " ft." : "");
        if (!f.onlyTypes.isEmpty()) t += " (" + String.join(", ", f.onlyTypes) + " only)";
        out.add(t);
    }
    if (f.uses != null && f.shareWith.isEmpty()) {
        String n = rec != null ? String.valueOf(f.uses.eval(rec, lv)) : f.uses.text();
        out.add((f.spendAny ? "Pool: " + n + " points" : "Uses: " + n) + " · recharge on a " + f.recharge);
    } else if (!f.shareWith.isEmpty()) {
        out.add(f.cost > 0 ? "Costs " + f.cost + " use" + (f.cost == 1 ? "" : "s") + " of " + f.shareWith : "Uses " + f.shareWith);
    }
    if (f.heal != null) out.add(f.spendAny ? "Heals 1 Hit Point per point spent" : "Heals " + f.heal.text(rec, lv) + " Hit Points");
    if (f.damage != null) {
        String type = f.damageType + (f.damageType2.isEmpty() ? "" : " or " + f.damageType2);
        out.add("Damage " + f.damage.text(rec, lv) + " " + type.toLowerCase()
                + (f.save.isEmpty() ? "" : " · " + f.save + " save" + (f.half ? " for half" : " negates")
                + (rec != null ? " (DC " + saveDc(rec, f, cls) + ")" : "")));
    } else if (!f.save.isEmpty()) {
        out.add(f.save + " save" + (rec != null ? " (DC " + saveDc(rec, f, cls) + ")" : "") + " or suffer the effect");
    }
    if (f.hasEffect()) {
        List<String> fx = new ArrayList<>();
        if (!f.conds.isEmpty()) fx.add(String.join(" and ", f.conds));
        if (!f.resist.isEmpty()) fx.add("Resistance to " + String.join(", ", f.resist));
        if (f.meleeDamage != null) fx.add("+" + (rec != null ? String.valueOf(f.meleeDamage.eval(rec, lv)) : f.meleeDamage.text()) + " damage on Strength attacks");
        if (f.advStr) fx.add("Advantage on Strength checks and saves");
        if (!f.advSaves.isEmpty()) fx.add("Advantage on " + String.join(", ", f.advSaves) + " saves");
        if (f.recklessAtk) fx.add("Advantage on Strength attack rolls");
        if (f.advAgainst) fx.add("attack rolls against it have Advantage");
        if (f.ac != 0) fx.add((f.ac > 0 ? "+" : "") + f.ac + " AC");
        if (f.speed != 0) fx.add((f.speed > 0 ? "+" : "") + f.speed + " ft. Speed");
        if (f.dcBonus != 0) fx.add("+" + f.dcBonus + " spell save DC");
        if (f.advSpellAtk) fx.add("Advantage on spell attack rolls");
        if (f.noSpells) fx.add("can't cast spells or concentrate");
        if (f.shape) fx.add("becomes one of its chosen forms (the form's size, Speed, AC, attacks, STR, DEX and CON); can't cast spells");
        if (f.inspiration != null) fx.add("a " + f.inspiration.text(rec, lv) + " to add to one failed D20 Test");
        if (f.tempHp != null) fx.add(f.tempHp.text(rec, lv) + " Temporary Hit Points");
        String d = f.duration.toLowerCase();
        out.add("Effect (" + d + (f.sustain ? ", extended by attacking" : "") + (f.endOnDamage ? ", ends on damage" : "")
                + (f.endOnIncap ? ", ends if Incapacitated" : "") + "): " + (fx.isEmpty() ? "tracked" : String.join("; ", fx)));
    } else if (f.inspiration != null && FAILED_CHECK.equals(f.activation)) {
        out.add("Adds " + f.inspiration.text(rec, lv) + " to the failed check");
    }
    List<String> eco = new ArrayList<>();
    if (f.extraAction) eco.add("one additional action");
    if (!f.actions.isEmpty()) eco.add(String.join(" + ", f.actions));
    if (f.strikes > 0) eco.add(f.strikes + " Unarmed Strike" + (f.strikes == 1 ? "" : "s") + (f.needsAttack ? " (after the Attack action)" : ""));
    if (!RS_NONE.equals(f.restore)) eco.add(RS_POOL.equals(f.restore) ? "refills " + f.restorePool : f.restore.toLowerCase());
    if (!f.removeConds.isEmpty()) eco.add("remove " + String.join(", ", f.removeConds) + (f.removeCost > 0 ? " for " + f.removeCost + " points" : ""));
    if (f.beforeAttack) eco.add("before your first attack roll of the turn");
    if (!eco.isEmpty()) out.add("Gives " + String.join("; ", eco));
    if (f.hitDice != null) {
        List<String> req = new ArrayList<>();
        if (f.hitFinesseOrRanged) req.add("Finesse or Ranged weapon");
        if (f.hitMeleeOnly) req.add("melee attack");
        if (f.hitNeedsAdvantage) req.add("Advantage, or an ally within 5 ft. of the target");
        out.add("+" + f.hitDice.text(rec, lv) + " damage once per turn" + (req.isEmpty() ? "" : " (" + String.join("; ", req) + ")"));
    }
    if (!f.unarmoredAc.isEmpty()) out.add("Unarmored AC: 10 + DEX + " + f.unarmoredAc + (f.unarmoredShield ? " (Shield allowed)" : " (no Shield)"));
    if (f.unarmoredSpeed != null) out.add("+" + (rec != null ? String.valueOf(f.unarmoredSpeed.eval(rec, lv)) : f.unarmoredSpeed.text()) + " ft. Speed while unarmored");
    if (f.jackOfAllTrades) out.add("+half Proficiency Bonus to checks without proficiency");
    if (!f.passiveAdvSaves.isEmpty()) out.add("Advantage on " + String.join(", ", f.passiveAdvSaves) + " saving throws");
    if (f.martialArts != null) out.add("Martial Arts die: d" + (rec != null ? String.valueOf(f.martialArts.eval(rec, lv)) : f.martialArts.text()));
    if (!f.bonusActions.isEmpty()) out.add(String.join(", ", f.bonusActions) + " as a Bonus Action");
    if (!f.weaponTraining.isEmpty()) out.add(String.join(", ", f.weaponTraining) + " weapon proficiency");
    if (!f.armorTraining.isEmpty()) out.add(String.join(", ", f.armorTraining) + " armor training");
    if (f.weaponMastery != null) out.add("Weapon Mastery: " + (rec != null ? String.valueOf(f.weaponMastery.eval(rec, lv)) : f.weaponMastery.text()) + " weapons");
    if (!C_NONE.equals(f.choiceKind)) {
        String n = f.choiceCount == null ? "1" : rec != null ? String.valueOf(f.choiceCount.eval(rec, lv)) : f.choiceCount.text();
        out.add("Choose " + n + " × " + f.choiceKind + (f.choiceOptions.isEmpty() ? "" : " (" + String.join(", ", f.choiceOptions) + ")"));
        if (C_FORM.equals(f.choiceKind)) {
            String cr;
            if (rec != null || f.maxCr == null || A_FIXED.equals(f.maxCr.kind)) cr = maxCrText(f, lv);
            else { // per-level table, in CR terms
                StringBuilder sb = new StringBuilder();
                int prev = -1;
                for (int i = 0; i < 20; i++) {
                    if (f.maxCr.table[i] == prev) continue;
                    prev = f.maxCr.table[i];
                    sb.append(sb.length() > 0 ? ", " : "").append(CustomAssets.CHALLENGE_RATINGS[Math.max(0, Math.min(CustomAssets.CHALLENGE_RATINGS.length - 1, prev))])
                            .append(i == 0 ? "" : " at " + (i + 1));
                }
                cr = sb.toString();
            }
            out.add("Forms: " + (f.formTypes.isEmpty() ? "any creature" : String.join(" / ", f.formTypes)) + ", CR " + cr + " or lower"
                    + (f.flyFrom <= 1 ? "" : f.flyFrom > 20 ? ", no Fly Speed" : ", Fly Speed from level " + f.flyFrom));
        }
    }
    return out;
}

static String summary(Feature f) {
    List<String> m = mechanics(f, null, 1, null, "");
    return String.join(" · ", m);
}

// Save DC for a feature: 8 + Proficiency Bonus + its DC ability (or the class's spellcasting ability, else WIS)
static int saveDc(Map<?, ?> rec, Feature f, String cls) {
    String ab = f.dcAbility;
    if (ab.isEmpty()) ab = SpellData.CASTING_ABILITY.getOrDefault(cls, "WIS");
    int mod = GameData.abilityModifier(CharacterStorage.recStatsMap(rec).getOrDefault(ab, 10));
    return 8 + GameData.proficiencyBonus(CharacterStorage.recLevel(rec)) + mod;
}

// Font of Magic: Sorcery Points to create a spell slot of each level (index = slot level)
static final int[] SLOT_COST = {0, 2, 3, 5, 6, 7};

// Sheet-only features that need no card (none yet); kept for homebrew that wants to hide helpers
static boolean hidden(Feature f) {
    return false;
}
}
