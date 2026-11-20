import java.util.*;
import java.util.List;

// Combat mechanics for spells (2024 PHB level 1 spells, plus the level 1 spells the app lists from Heroes of Faerûn).
// The spell text itself (range, duration, description) lives in SpellData; this file says how Combat resolves it:
//   DEFS     : one Def per spell: resolution mode, targeting shape, save, damage dice and scaling.
//   Effect   : an ongoing spell effect on one token (Bless, Hex, Shield, Searing Smite...). Saved with the map's tokens.
//   Zone     : a lasting area on the board (Entangle, Grease, Fog Cloud). Saved with the map.
//   castable : the level 1 spells a character can cast in combat, with the class / ability they're cast through.
//   saveBonus, slots, area geometry helpers.
// CombatPanel runs the casting flow (pick a spell, a slot, targets, then the attack / save / heal overlay) and ticks
// effects at the start and end of turns. Where Combat differs from the rules as written, it's listed in SPELLS_RAW.md.
class CombatSpells {

// ---- how a spell resolves ----
static final String ATTACK = "ATTACK";   // spell attack roll(s)
static final String SAVE = "SAVE";       // targets make a saving throw (damage and / or an effect)
static final String DARTS = "DARTS";     // Magic Missile: automatic hits
static final String HEAL = "HEAL";       // restores Hit Points
static final String BUFF = "BUFF";       // no roll: applies an effect (or temp HP) to the targets
static final String SMITE = "SMITE";     // cast from the attack roll after a weapon hit
static final String UTILITY = "UTILITY"; // no combat mechanics: spends the slot / action and tracks the duration
static final String LONG = "LONG";       // casting time too long for a combat turn

// ---- targeting ----
static final String SELF = "SELF", ONE = "ONE", MULTI = "MULTI", CONE = "CONE", CUBE_SELF = "CUBE_SELF",
        EMANATION = "EMANATION", SPHERE = "SPHERE", SQUARE = "SQUARE";

static final class Def {
    final String name, mode, target;
    int range;              // ft. (0 = self, 5 = touch)
    int size, sizePerLevel; // area: cone length, cube side, radius or square side (ft.)
    int targets = 1, targetsPerLevel; // MULTI creature count; DARTS dart count; ATTACK blasts
    String save = "";       // STR DEX CON INT WIS CHA
    boolean half;           // half damage on a successful save
    int dice, sides, dicePerLevel, bonus, bonusPerLevel;
    boolean addMod;         // add the spellcasting ability modifier (healing)
    String type = "";       // damage type ("" with choices = pick one when casting)
    String[] choices;       // damage types / command words / abilities offered when casting
    String choiceLabel = "";
    boolean sight = true;   // the target must be a creature you can see
    boolean choose;         // area: only creatures of your choice (enemies preselected)
    boolean hostile = true; // false: buffs and healing (allies preselected, caster can target itself)
    String smiteWith = "";  // SMITE trigger: MELEE (melee weapon or Unarmed Strike), RANGED (ranged weapon), ANY (any weapon)
    String[] onlyTypes, notTypes; // creature types the spell can / can't affect
    String how = "";        // what Combat does with it (shown in the picker)

    Def(String name, String mode, String target) {
        this.name = name;
        this.mode = mode;
        this.target = target;
    }

    Def range(int ft) { range = ft; return this; }
    Def size(int ft) { size = ft; return this; }
    Def size(int ft, int perLevel) { size = ft; sizePerLevel = perLevel; return this; }
    Def targets(int n, int perLevel) { targets = n; targetsPerLevel = perLevel; return this; }
    Def save(String ability, boolean halfOnSuccess) { save = ability; half = halfOnSuccess; return this; }
    Def dmg(int count, int sides, int perLevel, String type) { dice = count; this.sides = sides; dicePerLevel = perLevel; this.type = type; return this; }
    Def bonus(int b, int perLevel) { bonus = b; bonusPerLevel = perLevel; return this; }
    Def mod() { addMod = true; return this; }
    Def choices(String label, String... c) { choiceLabel = label; choices = c; return this; }
    Def noSight() { sight = false; return this; }
    Def choose() { choose = true; return this; }
    Def friendly() { hostile = false; return this; }
    Def smite(String with) { smiteWith = with; return this; }
    Def only(String... types) { onlyTypes = types; return this; }
    Def not(String... types) { notTypes = types; return this; }
    Def how(String text) { how = text; return this; }

    int diceAt(int slot) { return dice + dicePerLevel * Math.max(0, slot - 1); }
    int bonusAt(int slot) { return bonus + bonusPerLevel * Math.max(0, slot - 1); }
    int targetsAt(int slot) { return targets + targetsPerLevel * Math.max(0, slot - 1); }
    int sizeAt(int slot) { return size + sizePerLevel * Math.max(0, slot - 1); }
    boolean area() { return CONE.equals(target) || CUBE_SELF.equals(target) || EMANATION.equals(target) || SPHERE.equals(target) || SQUARE.equals(target); }
}

static final Map<String, Def> DEFS = new LinkedHashMap<>();

private static Def d(String name, String mode, String target) {
    Def x = new Def(name, mode, target);
    DEFS.put(name, x);
    return x;
}

static final String[] CHROMATIC_TYPES = {"Acid", "Cold", "Fire", "Lightning", "Poison", "Thunder"};
static final String[] ABILITIES = {"STR", "DEX", "CON", "INT", "WIS", "CHA"};
static final String[] COMMANDS = {"Approach", "Drop", "Flee", "Grovel", "Halt"};
static final String[] EXTRAPLANAR = {"Aberration", "Celestial", "Elemental", "Fey", "Fiend", "Undead"};

static {
    d("Alarm", LONG, SELF).how("Casting time 1 minute: cast it before the fight.");
    d("Animal Friendship", SAVE, MULTI).range(30).targets(1, 1).save("WIS", false).only("Beast")
            .how("Each Beast target makes a WIS save; on a failure it's Charmed for the spell's 24 hours.");
    d("Armor of Agathys", BUFF, SELF).bonus(5, 5).friendly()
            .how("You gain the Temporary Hit Points. While you have them, a creature that hits you with a melee attack takes the Cold damage automatically.");
    d("Arms of Hadar", SAVE, EMANATION).size(10).save("STR", true).dmg(2, 6, 1, "Necrotic")
            .how("Every creature within 10 ft. makes a STR save. A failure also means it can't take Reactions until the start of its next turn.");
    d("Bane", SAVE, MULTI).range(30).targets(3, 1).save("CHA", false)
            .how("Targets that fail the CHA save subtract 1d4 from attack rolls and saving throws (rolled automatically) while you concentrate.");
    d("Bless", BUFF, MULTI).range(30).targets(3, 1).friendly()
            .how("Targets add 1d4 to attack rolls and saving throws (rolled automatically) while you concentrate.");
    d("Burning Hands", SAVE, CONE).size(15).save("DEX", true).dmg(3, 6, 1, "Fire")
            .how("Aim the 15-ft. Cone on the board. Creatures in it make a DEX save.");
    d("Buzzing Bee", BUFF, ONE).range(120)
            .how("No save. The target can't benefit from Invisible and has Disadvantage on Concentration saves while you concentrate.");
    d("Charm Person", SAVE, MULTI).range(30).targets(1, 1).save("WIS", false).only("Humanoid")
            .how("Humanoid targets make a WIS save (with Advantage if they're on another team). On a failure they're Charmed until the spell ends or you or an ally damages them.");
    d("Chromatic Orb", ATTACK, ONE).range(90).dmg(3, 8, 1, "").choices("Damage type", CHROMATIC_TYPES)
            .how("Ranged spell attack. If two d8s match on a hit, the orb can leap to another creature within 30 ft. (up to your slot level in leaps).");
    d("Color Spray", SAVE, CONE).size(15).save("CON", false)
            .how("Creatures in the 15-ft. Cone make a CON save or are Blinded until the end of your next turn.");
    d("Command", SAVE, MULTI).range(60).targets(1, 1).save("WIS", false).choices("Command", COMMANDS)
            .how("Targets make a WIS save or follow the command on their next turn (Grovel also makes them Prone; Halt stops them moving).");
    d("Compelled Duel", SAVE, ONE).range(30).save("WIS", false)
            .how("On a failed WIS save the target has Disadvantage on attacks against anyone but you while you concentrate. Ends if you attack someone else.");
    d("Comprehend Languages", UTILITY, SELF).friendly().how("No combat effect: tracked for its duration.");
    d("Create or Destroy Water", UTILITY, SELF).friendly().how("No combat effect.");
    d("Cure Wounds", HEAL, ONE).range(5).dmg(2, 8, 2, "").mod().noSight().friendly()
            .how("Touch: the target regains 2d8 + your spellcasting modifier Hit Points (+2d8 per slot level above 1).");
    d("Detect Evil and Good", UTILITY, SELF).friendly().how("No combat effect: tracked while you concentrate.");
    d("Detect Magic", UTILITY, SELF).friendly().how("No combat effect: tracked while you concentrate.");
    d("Detect Poison and Disease", UTILITY, SELF).friendly().how("No combat effect: tracked while you concentrate.");
    d("Disguise Self", UTILITY, SELF).friendly().how("No combat effect: tracked for its duration.");
    d("Dissonant Whispers", SAVE, ONE).range(60).save("WIS", true).dmg(3, 6, 1, "Psychic")
            .how("WIS save. On a failure the target spends its Reaction (if it has one) to flee: drag its token away from you.");
    d("Divine Favor", BUFF, SELF).friendly()
            .how("For 1 minute your weapon attacks add 1d4 Radiant damage on a hit (rolled with the attack's damage).");
    d("Divine Smite", SMITE, SELF).dmg(2, 8, 1, "Radiant").smite("MELEE").friendly()
            .how("Cast from the attack roll after hitting with a melee weapon or Unarmed Strike: +2d8 Radiant, +1d8 against Fiends and Undead.");
    d("Ensnaring Strike", SMITE, SELF).dmg(1, 6, 1, "Piercing").save("STR", false).smite("ANY").friendly()
            .how("Cast from the attack roll after a weapon hit. STR save (Large or bigger: Advantage) or Restrained, taking 1d6 Piercing at the start of each of its turns.");
    d("Entangle", SAVE, SQUARE).range(90).size(20).save("STR", false)
            .how("Place the 20-ft. square: it becomes Difficult Terrain. Creatures in it make a STR save or are Restrained while you concentrate.");
    d("Expeditious Retreat", BUFF, SELF).friendly()
            .how("You Dash now, and can Dash as a Bonus Action on later turns while you concentrate.");
    d("Faerie Fire", SAVE, SQUARE).range(60).size(20).save("DEX", false)
            .how("Place the 20-ft. Cube. Creatures that fail the DEX save are outlined: attacks against them have Advantage and they can't benefit from Invisible.");
    d("False Life", BUFF, SELF).dmg(2, 4, 0, "").bonus(4, 5).friendly()
            .how("You gain 2d4 + 4 Temporary Hit Points (+5 per slot level above 1).");
    d("Feather Fall", UTILITY, SELF).friendly().how("Reaction to a fall: no falling in Combat, so it's only tracked.");
    d("Find Familiar", LONG, SELF).how("Casting time 1 hour: cast it before the fight.");
    d("Fog Cloud", BUFF, SPHERE).range(120).size(20, 20).friendly()
            .how("Place the 20-ft.-radius Sphere. It's Heavily Obscured: creatures can't see into, out of or through it.");
    d("Goodberry", BUFF, SELF).friendly()
            .how("Ten Goodberries appear in your items. Eating one (Use Item) is a Bonus Action that restores 1 Hit Point.");
    d("Grease", SAVE, SQUARE).range(60).size(10).save("DEX", false)
            .how("Place the 10-ft. square: Difficult Terrain for 1 minute. Creatures in it save or fall Prone, and so does a creature that enters it or ends its turn there.");
    d("Guiding Bolt", ATTACK, ONE).range(120).dmg(4, 6, 1, "Radiant")
            .how("Ranged spell attack. On a hit, the next attack roll against the target before the end of your next turn has Advantage.");
    d("Hail of Thorns", SMITE, SELF).dmg(1, 10, 1, "Piercing").save("DEX", true).smite("RANGED").friendly()
            .how("Cast from the attack roll after hitting with a ranged weapon: the target and each creature within 5 ft. of it make a DEX save.");
    d("Healing Word", HEAL, ONE).range(60).dmg(2, 4, 2, "").mod().friendly()
            .how("A creature you can see within 60 ft. regains 2d4 + your spellcasting modifier Hit Points (+2d4 per slot level above 1).");
    d("Hellish Rebuke", SAVE, ONE).range(60).save("DEX", true).dmg(2, 10, 1, "Fire")
            .how("Offered automatically as a Reaction when a creature you can see within 60 ft. damages you. DEX save.");
    d("Heroism", BUFF, MULTI).range(5).targets(1, 1).noSight().friendly()
            .how("Touch: targets are immune to Frightened and gain Temporary Hit Points equal to your spellcasting modifier at the start of each of their turns.");
    d("Hex", BUFF, ONE).range(90).choices("Disadvantage on ability checks with", ABILITIES)
            .how("Your attack-roll hits against the target add 1d6 Necrotic. If it drops to 0 HP, move the curse with a Bonus Action.");
    d("Hunter's Mark", BUFF, ONE).range(90)
            .how("Your attack-roll hits against the target add 1d6 Force. If it drops to 0 HP, move the mark with a Bonus Action.");
    d("Ice Knife", ATTACK, ONE).range(60).dmg(1, 10, 0, "Piercing").save("DEX", false)
            .how("Ranged spell attack for 1d10 Piercing. Hit or miss, the target and creatures within 5 ft. of it make a DEX save or take 2d6 Cold (+1d6 per slot level above 1).");
    d("Identify", LONG, SELF).how("Casting time 1 minute: cast it outside combat.");
    d("Illusory Script", LONG, SELF).how("Casting time 1 minute: cast it outside combat.");
    d("Insidious Rhythm", SAVE, ONE).range(60).save("WIS", false)
            .how("WIS save. On a failure the target has Disadvantage on Concentration saves while you concentrate; it repeats the save at the end of each of its turns.");
    d("Inflict Wounds", SAVE, ONE).range(5).save("CON", true).dmg(2, 10, 1, "Necrotic").noSight()
            .how("Touch: CON save, 2d10 Necrotic (half on a success).");
    d("Jump", BUFF, MULTI).range(5).targets(1, 1).noSight().friendly()
            .how("Touch: tracked for 1 minute. Jumping isn't modeled on the board (drag the token).");
    d("Longstrider", BUFF, MULTI).range(5).targets(1, 1).noSight().friendly()
            .how("Touch: each target's Speed increases by 10 ft. for 1 hour.");
    d("Mage Armor", BUFF, ONE).range(5).noSight().friendly()
            .how("Touch: a target wearing no armor has a base AC of 13 + its DEX modifier for 8 hours.");
    d("Magic Missile", DARTS, MULTI).range(120).targets(3, 1)
            .how("Pick targets, split the darts between them; each dart hits automatically for 1d4 + 1 Force. Shield blocks them.");
    d("Protection from Evil and Good", BUFF, ONE).range(5).noSight().friendly()
            .how("Touch: Aberrations, Celestials, Elementals, Fey, Fiends and Undead have Disadvantage on attacks against the target.");
    d("Purify Food and Drink", UTILITY, SELF).friendly().how("No combat effect.");
    d("Ray of Sickness", ATTACK, ONE).range(60).dmg(2, 8, 1, "Poison")
            .how("Ranged spell attack. On a hit the target is also Poisoned until the end of your next turn.");
    d("Sanctuary", BUFF, ONE).range(30).friendly()
            .how("Anyone who attacks the warded creature first makes a WIS save; on a failure the attack is lost. Ends if the warded creature attacks, casts a spell or deals damage.");
    d("Searing Smite", SMITE, SELF).dmg(1, 6, 1, "Fire").save("CON", false).smite("MELEE").friendly()
            .how("Cast from the attack roll after a melee hit: +1d6 Fire now, then 1d6 Fire at the start of each of the target's turns until it succeeds on a CON save.");
    d("Shield", BUFF, SELF).friendly()
            .how("Offered automatically as a Reaction when you're hit by an attack or Magic Missile: +5 AC until the start of your next turn.");
    d("Shield of Faith", BUFF, ONE).range(60).friendly()
            .how("The target gets +2 AC while you concentrate.");
    d("Silent Image", UTILITY, SELF).friendly().how("Illusions aren't modeled on the board: tracked while you concentrate.");
    d("Sleep", SAVE, SPHERE).range(60).size(5).save("WIS", false).choose()
            .how("Place the 5-ft.-radius Sphere. Chosen creatures that fail the WIS save are Incapacitated; at the end of their next turn they save again or fall Unconscious. Damage wakes them.");
    d("Speak with Animals", UTILITY, SELF).friendly().how("No combat effect: tracked for its duration.");
    d("Spellfire Flare", ATTACK, ONE).range(60).dmg(2, 10, 0, "Radiant").targets(1, 1)
            .how("Ranged spell attack that ignores Half Cover. One blast per slot level; each can target a different creature.");
    d("Tasha's Hideous Laughter", SAVE, MULTI).range(30).targets(1, 1).save("WIS", false)
            .how("WIS save or Prone and Incapacitated. The target saves again at the end of each of its turns and whenever it takes damage (with Advantage).");
    d("Tenser's Floating Disk", UTILITY, SELF).friendly().how("No combat effect: tracked for its duration.");
    d("Thunderous Smite", SMITE, SELF).dmg(2, 6, 1, "Thunder").save("STR", false).smite("MELEE").friendly()
            .how("Cast from the attack roll after a melee hit: +2d6 Thunder, and a STR save or pushed 10 ft. and Prone.");
    d("Thunderwave", SAVE, CUBE_SELF).size(15).save("CON", true).dmg(2, 8, 1, "Thunder")
            .how("Aim the 15-ft. Cube from your side. CON save; a failure also pushes the creature 10 ft. away.");
    d("Unseen Servant", UTILITY, SELF).friendly().how("No combat effect: tracked for its duration.");
    d("Wardaway", SAVE, ONE).range(60).save("CON", true).dmg(2, 4, 2, "Force").not("Construct", "Undead")
            .how("CON save. On a failure the target's Speed is halved on its next turn and it can take only an action or a Bonus Action, not both.");
    d("Witch Bolt", ATTACK, ONE).range(60).dmg(2, 12, 1, "Lightning")
            .how("Ranged spell attack. On later turns, while you concentrate, a Bonus Action deals 1d12 Lightning to the target automatically.");
    d("Wrathful Smite", SMITE, SELF).dmg(1, 6, 1, "Necrotic").save("WIS", false).smite("MELEE").friendly()
            .how("Cast from the attack roll after a melee hit: +1d6 Necrotic, and a WIS save or Frightened (it saves again at the end of each of its turns).");
}

static Def def(String spell) {
    return spell == null ? null : DEFS.get(spell);
}

// ===============================================================
// Ongoing effects
// ===============================================================
// One spell's effect on one token. Durations count turn boundaries: casterStarts = how many of the caster's turn
// starts until it ends (10 = 1 minute), casterEnds / ownStarts / ownEnds likewise; -1 = not counted that way.
static final class Effect {
    String spell = "", caster = "", choice = "";
    int slot = 1, dc, value, castRound;
    boolean conc;
    int casterStarts = -1, casterEnds = -1, ownStarts = -1, ownEnds = -1;
    String endSave = "";    // repeat save at the end of each of the target's turns (success ends it)
    String startSave = "";  // save right after the start-of-turn damage (Searing Smite)
    int dotDice, dotSides;
    String dotType = "";
    int phase;              // Sleep: 0 = Incapacitated, 1 = Unconscious
    List<String> conds = new ArrayList<>(); // conditions this effect put on the token (removed when it ends)

    boolean lasting() {
        return casterStarts < 0 && casterEnds < 0 && ownStarts < 0 && ownEnds < 0;
    }

    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("spell", spell);
        m.put("caster", caster);
        if (!choice.isEmpty()) m.put("choice", choice);
        m.put("slot", slot);
        m.put("dc", dc);
        m.put("value", value);
        m.put("castRound", castRound);
        m.put("conc", conc);
        m.put("casterStarts", casterStarts);
        m.put("casterEnds", casterEnds);
        m.put("ownStarts", ownStarts);
        m.put("ownEnds", ownEnds);
        if (!endSave.isEmpty()) m.put("endSave", endSave);
        if (!startSave.isEmpty()) m.put("startSave", startSave);
        if (dotDice > 0) {
            m.put("dotDice", dotDice);
            m.put("dotSides", dotSides);
            m.put("dotType", dotType);
        }
        m.put("phase", phase);
        m.put("conds", new ArrayList<Object>(conds));
        return m;
    }

    static Effect fromJson(Map<?, ?> m) {
        Effect e = new Effect();
        e.spell = CustomAssets.str(m, "spell");
        e.caster = CustomAssets.str(m, "caster");
        e.choice = CustomAssets.str(m, "choice");
        e.slot = CustomAssets.intVal(m, "slot", 1);
        e.dc = CustomAssets.intVal(m, "dc", 10);
        e.value = CustomAssets.intVal(m, "value", 0);
        e.castRound = CustomAssets.intVal(m, "castRound", 1);
        e.conc = CustomAssets.bool(m, "conc");
        e.casterStarts = CustomAssets.intVal(m, "casterStarts", -1);
        e.casterEnds = CustomAssets.intVal(m, "casterEnds", -1);
        e.ownStarts = CustomAssets.intVal(m, "ownStarts", -1);
        e.ownEnds = CustomAssets.intVal(m, "ownEnds", -1);
        e.endSave = CustomAssets.str(m, "endSave");
        e.startSave = CustomAssets.str(m, "startSave");
        e.dotDice = CustomAssets.intVal(m, "dotDice", 0);
        e.dotSides = CustomAssets.intVal(m, "dotSides", 0);
        e.dotType = CustomAssets.str(m, "dotType");
        e.phase = CustomAssets.intVal(m, "phase", 0);
        e.conds = CustomAssets.list(m, "conds");
        return e;
    }
}

static Effect find(List<Effect> effects, String spell) {
    for (Effect e : effects) if (e.spell.equals(spell)) return e;
    return null;
}

static boolean has(List<Effect> effects, String spell) {
    return find(effects, spell) != null;
}

static List<Effect> readEffects(Object o) {
    List<Effect> out = new ArrayList<>();
    if (o instanceof List) for (Object x : (List<?>) o) if (x instanceof Map) out.add(Effect.fromJson((Map<?, ?>) x));
    return out;
}

static List<Object> writeEffects(List<Effect> effects) {
    List<Object> out = new ArrayList<>();
    for (Effect e : effects) out.add(e.toJson());
    return out;
}

// Short text for chips and the board label
static String effectLabel(Effect e) {
    switch (e.spell) {
        case "Bless": return "Blessed (+1d4)";
        case "Bane": return "Baned (−1d4)";
        case "Shield": return "Shield (+5 AC)";
        case "Shield of Faith": return "Shield of Faith (+2 AC)";
        case "Mage Armor": return "Mage Armor";
        case "Hex": return "Hexed" + (e.choice.isEmpty() ? "" : " (" + e.choice + ")");
        case "Hunter's Mark": return "Hunter's Mark";
        case "Guiding Bolt": return "Guiding Bolt (next attack: Advantage)";
        case "Faerie Fire": return "Faerie Fire (attacks: Advantage)";
        case "Arms of Hadar": return "No Reactions";
        case "Command": return "Commanded: " + e.choice;
        case "Sleep": return e.phase == 0 ? "Sleep (drowsy)" : "Sleep (asleep)";
        case "Searing Smite": return "Burning (Searing Smite)";
        case "Ensnaring Strike": return "Ensnared";
        case "Witch Bolt": return "Witch Bolt";
        case "Armor of Agathys": return "Armor of Agathys (" + e.value + " Cold)";
        case "Heroism": return "Heroism";
        case "Longstrider": return "Longstrider (+10 ft.)";
        case "Wardaway": return "Wardaway (½ Speed, action or Bonus Action)";
        case "Compelled Duel": return "Compelled Duel";
        case "Sanctuary": return "Sanctuary";
        default: return e.spell;
    }
}

// ===============================================================
// Lasting areas on the board
// ===============================================================
static final class Zone {
    String spell = "", caster = "";
    Set<Integer> cells = new LinkedHashSet<>(); // y * width + x
    int casterStarts = -1; // turn starts of the caster until it ends (-1: until removed / concentration ends)
    boolean conc;

    boolean difficult() { return "Entangle".equals(spell) || "Grease".equals(spell); }
    boolean obscured() { return "Fog Cloud".equals(spell); }

    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("spell", spell);
        m.put("caster", caster);
        m.put("cells", new ArrayList<Object>(cells));
        m.put("casterStarts", casterStarts);
        m.put("conc", conc);
        return m;
    }

    static Zone fromJson(Map<?, ?> m) {
        Zone z = new Zone();
        z.spell = CustomAssets.str(m, "spell");
        z.caster = CustomAssets.str(m, "caster");
        Object c = m.get("cells");
        if (c instanceof List) for (Object o : (List<?>) c) if (o instanceof Number) z.cells.add(((Number) o).intValue());
        z.casterStarts = CustomAssets.intVal(m, "casterStarts", -1);
        z.conc = CustomAssets.bool(m, "conc");
        return z;
    }
}

// ===============================================================
// What a character can cast
// ===============================================================
static final class Known {
    String name, source, ability;
    Spell spell;
    String freeKey = ""; // key in the token's freeCasts pool when the spell can be cast without a slot
}

// Level 1 spells the character has prepared (per class) or always prepared (species, feats, class features)
static List<Known> castable(Map<?, ?> rec) {
    List<Known> out = new ArrayList<>();
    if (rec == null) return out;
    Map<String, Integer> stats = CharacterStorage.recStatsMap(rec);
    Set<String> seen = new HashSet<>();
    for (Map.Entry<String, Map<String, List<String>>> en : CharacterStorage.recClassSpells(rec).entrySet()) {
        String cls = en.getKey();
        String ab = SpellData.CASTING_ABILITY.getOrDefault(cls, "INT");
        for (String n : en.getValue().get("prepared")) {
            Spell sp = SpellData.lookup(n);
            if (sp == null || sp.level != 1 || !seen.add(n)) continue;
            Known k = new Known();
            k.name = n;
            k.spell = sp;
            k.source = cls;
            k.ability = ab;
            out.add(k);
        }
    }
    for (Map.Entry<String, String> en : SpellData.alwaysPrepared(rec).entrySet()) {
        Spell sp = SpellData.lookup(en.getKey());
        if (sp == null || sp.level != 1) continue;
        String src = en.getValue();
        Known k = null;
        for (Known x : out) if (x.name.equals(en.getKey())) k = x;
        if (k == null) {
            k = new Known();
            k.name = en.getKey();
            k.spell = sp;
            k.source = src;
            out.add(k);
        }
        if (src.startsWith("Ranger")) {
            k.ability = "WIS";
            k.freeKey = "Favored Enemy";
        } else if (src.startsWith("Paladin")) {
            k.ability = "CHA";
            k.freeKey = "Paladin's Smite";
        } else {
            if (k.ability == null) k.ability = bestMental(stats); // the sheet uses the highest of INT / WIS / CHA
            k.freeKey = k.name; // species / feat spells: once per Long Rest without a slot
        }
    }
    out.sort(Comparator.comparing(k -> k.name));
    return out;
}

static String bestMental(Map<String, Integer> stats) {
    String best = "INT";
    for (String a : new String[]{"WIS", "CHA"}) if (stats.getOrDefault(a, 10) > stats.getOrDefault(best, 10)) best = a;
    return best;
}

// Free (no slot) casts per Long Rest, copied into the token when combat starts
static Map<String, Integer> freeCasts(Map<?, ?> rec) {
    Map<String, Integer> out = new LinkedHashMap<>();
    if (rec == null) return out;
    Map<String, Integer> lv = CharacterStorage.recClassLevels(rec);
    int ranger = lv.getOrDefault("Ranger", 0);
    if (ranger >= 1) out.put("Favored Enemy", ranger >= 17 ? 6 : ranger >= 13 ? 5 : ranger >= 9 ? 4 : ranger >= 5 ? 3 : 2);
    if (lv.getOrDefault("Paladin", 0) >= 2) out.put("Paladin's Smite", 1);
    for (Map.Entry<String, String> en : SpellData.alwaysPrepared(rec).entrySet()) {
        Spell sp = SpellData.lookup(en.getKey());
        if (sp == null || sp.level != 1 || en.getValue().startsWith("Ranger") || en.getValue().startsWith("Paladin")) continue;
        int uses = en.getValue().contains("Forest Gnome") ? GameData.proficiencyBonus(CharacterStorage.recLevel(rec)) : 1;
        out.put(en.getKey(), uses);
    }
    return out;
}

static int abilityMod(Map<?, ?> rec, String ability) {
    return GameData.abilityModifier(CharacterStorage.recStatsMap(rec).getOrDefault(ability, 10));
}

static int profBonus(Map<?, ?> rec) {
    return GameData.proficiencyBonus(CharacterStorage.recLevel(rec));
}

// ===============================================================
// Saving throws
// ===============================================================
private static final java.util.regex.Pattern SAVE_TEXT = java.util.regex.Pattern.compile("(STR|DEX|CON|INT|WIS|CHA)[a-z]*\\.?\\s*([+-]\\s*\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE);

// Saving throw bonus: characters add their Proficiency Bonus for their class's (and Resilient) saves; NPCs use
// their stat block's "Saving throws" line, or the plain ability modifier
static int saveBonus(Map<?, ?> source, boolean pc, String ability) {
    if (source == null) return 0;
    if (pc) {
        int mod = abilityMod(source, ability);
        Set<String> profs = new HashSet<>();
        String[] cls = GameData.CLASS_SAVING_THROWS.get(CharacterStorage.recStr(source, "characterClass"));
        if (cls != null) profs.addAll(Arrays.asList(cls));
        for (Map<String, Object> f : CharacterStorage.recFeats(source)) {
            if ("Resilient".equals(f.get("name"))) profs.addAll(CharacterStorage.featStatIncreases(f).keySet());
        }
        return mod + (profs.contains(ability) ? profBonus(source) : 0);
    }
    java.util.regex.Matcher m = SAVE_TEXT.matcher(CustomAssets.str(source, "saves"));
    while (m.find()) {
        if (m.group(1).equalsIgnoreCase(ability)) return Integer.parseInt(m.group(2).replace(" ", "").replace("+", ""));
    }
    Integer score = CustomAssets.abilities(source).get(ability);
    return GameData.abilityModifier(score == null ? 10 : score);
}

static String creatureType(Map<?, ?> source, boolean pc) {
    if (source == null) return "";
    String t = pc ? CharacterStorage.recStr(source, "creatureType") : CustomAssets.str(source, "creatureType");
    return t.isEmpty() && pc ? "Humanoid" : t;
}

// Creatures that don't sleep (2024 Sleep: e.g. Elves) or are immune to Exhaustion succeed on Sleep's saves automatically
static boolean sleepless(Map<?, ?> source, boolean pc) {
    if (source == null) return false;
    if (pc) return "Elf".equals(CharacterStorage.recStr(source, "species"));
    String all = (String.valueOf(source.get("traits")) + " " + String.valueOf(source.get("bio"))).toLowerCase();
    return all.contains("doesn't need to sleep") || all.contains("does not need to sleep") || all.contains("doesn't sleep")
            || all.contains("can't be put to sleep") || all.contains("immune to exhaustion");
}

static boolean isAny(String value, String[] options) {
    if (options == null) return false;
    for (String o : options) if (o.equalsIgnoreCase(value.trim())) return true;
    return false;
}

// ===============================================================
// Spell slots
// ===============================================================
// A character's remaining slots, as the sheet has them now: {slots for levels 1-9, then pact slots left}
static int[] remainingSlots(Map<?, ?> rec) {
    int[] out = new int[10];
    if (rec == null) return out;
    App.SlotState s = App.recSlotState(rec);
    for (int i = 1; i <= 9; i++) out[i - 1] = s.remaining(i);
    out[9] = s.pactRemaining();
    return out;
}

static int pactLevel(Map<?, ?> rec) {
    return rec == null ? 0 : SpellData.pactSlotLevel(CharacterStorage.recClassLevels(rec).getOrDefault("Warlock", 0));
}

static int[] maxSlots(Map<?, ?> rec) {
    int[] out = new int[10];
    if (rec == null) return out;
    App.SlotState s = App.recSlotState(rec);
    System.arraycopy(s.max, 0, out, 0, 9);
    out[9] = s.pactMax;
    return out;
}

// ===============================================================
// Area geometry (squares; 1 square = 5 ft.)
// ===============================================================
// Squares a Cone covers: from the point where the direction leaves the caster's space, out to `lengthFt`,
// as wide as it is far (half-angle atan 1/2). A square counts when its center is inside.
static Set<Integer> cone(int cx, int cy, int csize, double angle, int lengthFt, int w, int h) {
    Set<Integer> out = new LinkedHashSet<>();
    double mx = cx + csize / 2.0, my = cy + csize / 2.0;
    double dx = Math.cos(angle), dy = Math.sin(angle);
    double t = Math.min(dx == 0 ? Double.MAX_VALUE : (csize / 2.0) / Math.abs(dx), dy == 0 ? Double.MAX_VALUE : (csize / 2.0) / Math.abs(dy));
    double ox = mx + dx * t, oy = my + dy * t;
    double len = lengthFt / 5.0, half = Math.atan(0.5) + 0.03;
    for (int y = Math.max(0, (int) Math.floor(oy - len - 1)); y < Math.min(h, (int) Math.ceil(oy + len + 1)); y++) {
        for (int x = Math.max(0, (int) Math.floor(ox - len - 1)); x < Math.min(w, (int) Math.ceil(ox + len + 1)); x++) {
            if (x >= cx && x < cx + csize && y >= cy && y < cy + csize) continue;
            double vx = x + 0.5 - ox, vy = y + 0.5 - oy, dist = Math.hypot(vx, vy);
            if (dist > len + 1e-6 || dist < 1e-6) continue;
            double a = Math.acos(Math.max(-1, Math.min(1, (vx * dx + vy * dy) / dist)));
            if (a <= half) out.add(y * w + x);
        }
    }
    return out;
}

// Cube with one face against the caster's space, on the side `dir` points to (0 = east, then clockwise in 45° steps)
static Set<Integer> cubeFromSelf(int cx, int cy, int csize, int dir, int sideFt, int w, int h) {
    Set<Integer> out = new LinkedHashSet<>();
    int n = Math.max(1, sideFt / 5);
    int[][] steps = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
    int sx = steps[dir & 7][0], sy = steps[dir & 7][1];
    // along the facing axis the cube starts right outside the caster; across it, it's centered on the caster
    int x0 = sx > 0 ? cx + csize : sx < 0 ? cx - n : cx + (int) Math.floor((csize - n) / 2.0);
    int y0 = sy > 0 ? cy + csize : sy < 0 ? cy - n : cy + (int) Math.floor((csize - n) / 2.0);
    for (int y = y0; y < y0 + n; y++) for (int x = x0; x < x0 + n; x++) if (x >= 0 && y >= 0 && x < w && y < h) out.add(y * w + x);
    return out;
}

// Sphere centered on a grid intersection (px, py in squares): squares whose center is within the radius
static Set<Integer> sphere(double px, double py, int radiusFt, int w, int h) {
    Set<Integer> out = new LinkedHashSet<>();
    double r = radiusFt / 5.0;
    for (int y = Math.max(0, (int) Math.floor(py - r)); y < Math.min(h, (int) Math.ceil(py + r)); y++) {
        for (int x = Math.max(0, (int) Math.floor(px - r)); x < Math.min(w, (int) Math.ceil(px + r)); x++) {
            if (Math.hypot(x + 0.5 - px, y + 0.5 - py) <= r + 1e-6) out.add(y * w + x);
        }
    }
    return out;
}

static Set<Integer> square(int x0, int y0, int sideFt, int w, int h) {
    Set<Integer> out = new LinkedHashSet<>();
    int n = Math.max(1, sideFt / 5);
    for (int y = y0; y < y0 + n; y++) for (int x = x0; x < x0 + n; x++) if (x >= 0 && y >= 0 && x < w && y < h) out.add(y * w + x);
    return out;
}

// Direction index (0 = east, clockwise, 45° steps) nearest to an angle in radians (screen coordinates: y down)
static int dir8(double angle) {
    int d = (int) Math.round(angle / (Math.PI / 4));
    return ((d % 8) + 8) % 8;
}
}
