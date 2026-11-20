import java.util.*;
import java.util.List;

// Combat mechanics for spells (2024 PHB cantrips and level 1 spells, the Heroes of Faerûn spells the app lists, and
// homebrew spells built in the Asset Library). The spell text itself (range, duration, description) lives in SpellData;
// this file says how Combat resolves it:
//   DEFS     : one Def per spell: resolution mode, targeting shape, save, damage dice and scaling, and a lasting-effect
//              template (conditions, duration, AC / Speed changes, d4 bonuses, marks, ongoing damage...). A homebrew
//              spell's Combat fields become a Def through fromAsset (CustomAssets.applyToRuntime registers it; DEFS is
//              snapshotted there like the other rules tables). Spells without a Def can still be cast: they spend the
//              slot and are tracked for their duration (utilityFor).
//   Effect   : an ongoing spell effect on one token, copied from the template. Saved with the map's tokens.
//   Zone     : a lasting area on the board (Entangle, Grease, Fog Cloud). Saved with the map.
//   castable : the spells a character or NPC can cast in combat, with the ability / DC / uses they're cast with.
//   saveBonus, slots, area geometry helpers.
// CombatPanel runs the casting flow and ticks effects. Differences from the rules as written: SPELLS_RAW.md.
class CombatSpells {

// ---- how a spell resolves ----
static final String ATTACK = "ATTACK";   // spell attack roll(s)
static final String SAVE = "SAVE";       // targets make a saving throw (damage and / or an effect)
static final String AUTO = "AUTO";       // damage / effect with no roll (homebrew "Automatic damage")
static final String DARTS = "DARTS";     // Magic Missile: automatic hits split between targets
static final String HEAL = "HEAL";       // restores Hit Points
static final String BUFF = "BUFF";       // no roll: applies the effect (or temp HP) to the targets
static final String SMITE = "SMITE";     // cast from the attack roll after a weapon hit
static final String UTILITY = "UTILITY"; // no combat mechanics: spends the slot / action and tracks the duration
static final String LONG = "LONG";       // casting time too long for a combat turn

// ---- targeting ----
static final String SELF = "SELF", ONE = "ONE", MULTI = "MULTI", CONE = "CONE", CUBE_SELF = "CUBE_SELF",
        EMANATION = "EMANATION", SPHERE = "SPHERE", SQUARE = "SQUARE";

// ---- how long a template effect lasts ----
static final String DUR_SPELL = "SPELL";            // the spell's duration (Concentration if the spell has it)
static final String DUR_CASTER_END = "CASTER_END";  // until the end of the caster's next turn
static final String DUR_CASTER_START = "CASTER_START"; // until the start of the caster's next turn
static final String DUR_OWN_START = "OWN_START";    // until the start of the target's next turn
static final String DUR_OWN_END = "OWN_END";        // until the end of the target's next turn

static final class Def {
    final String name, mode, target;
    int level = 1;          // spell level (0 = cantrip), from SpellData
    int range;              // ft. (0 = self, 5 = touch)
    int size, sizePerLevel; // area: cone length, cube side, radius or square side (ft.)
    int targets = 1, targetsPerLevel; // MULTI creature count; DARTS dart count; ATTACK beams / blasts
    String save = "";       // STR DEX CON INT WIS CHA
    boolean half;           // half damage on a successful save
    int dice, sides, dicePerLevel, bonus, bonusPerLevel;
    boolean addMod;         // add the spellcasting ability modifier (healing, homebrew damage)
    String type = "";       // damage type ("" with choices = pick one when casting)
    String[] choices;       // damage types / command words / abilities offered when casting
    String choiceLabel = "";
    boolean sight = true;   // the target must be a creature you can see
    boolean choose;         // area: only creatures of your choice (enemies preselected)
    boolean hostile = true; // false: buffs and healing (allies preselected, caster can target itself)
    boolean meleeAttack;    // spell attack: melee (reach `reach`) instead of ranged
    int reach = 5;
    boolean ignoreCover;    // Sacred Flame, Spellfire Flare
    String smiteWith = "";  // SMITE trigger: MELEE (melee weapon or Unarmed Strike), RANGED (ranged weapon), ANY (any weapon)
    String[] onlyTypes, notTypes; // creature types the spell can / can't affect
    String how = "";        // what Combat does with it (shown in the picker)
    boolean custom;         // built from a homebrew asset
    // ---- lasting-effect template: applied to a failed save, a hit, or each BUFF target ----
    List<String> conds = new ArrayList<>();
    String dur = DUR_SPELL;
    boolean endSave;        // the target repeats the save at the end of each of its turns (success ends it)
    int dotDice, dotSides;  // damage at the start of each of the target's turns
    String dotType = "";
    boolean dotSave;        // ...then it repeats the save (success ends it)
    int ac, speed;          // AC / Speed change while it lasts
    int rollDie;            // +N: add 1dN to its attack rolls and saves; -N: subtract 1dN (Bless / Bane)
    int againstDie;         // subtract 1dN from attack rolls against it (Blade Ward)
    int markDice, markSides; String markType = ""; // extra damage on the caster's attack-roll hits against it
    boolean advAgainst;     // attack rolls against it have Advantage (Faerie Fire)
    boolean reveal;         // it can't benefit from Invisible (Faerie Fire, Starry Wisp, Buzzing Bee)
    boolean noReactions;    // it can't take Reactions
    boolean noOA;           // it can't make opportunity attacks
    boolean nextAttackDis;  // Disadvantage on its next attack roll (used up by it)
    boolean nextSaveDie;    // subtracts 1d4 from its next saving throw (used up by it)
    boolean noHeal;         // it can't regain Hit Points
    int tempDice, tempSides, tempBonus, tempPerLevel; boolean tempMod; // Temporary Hit Points granted

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
    Def melee(int reachFt) { meleeAttack = true; reach = reachFt; return this; }
    Def noCover() { ignoreCover = true; return this; }
    Def smite(String with) { smiteWith = with; return this; }
    Def only(String... types) { onlyTypes = types; return this; }
    Def not(String... types) { notTypes = types; return this; }
    Def how(String text) { how = text; return this; }
    Def cond(String... c) { conds.addAll(Arrays.asList(c)); return this; }
    Def dur(String d) { dur = d; return this; }
    Def endSave() { endSave = true; return this; }
    Def dot(int count, int sides, String type, boolean thenSave) { dotDice = count; dotSides = sides; dotType = type; dotSave = thenSave; return this; }
    Def ac(int n) { ac = n; return this; }
    Def speed(int n) { speed = n; return this; }
    Def roll(int die) { rollDie = die; return this; }
    Def against(int die) { againstDie = die; return this; }
    Def mark(int count, int sides, String type) { markDice = count; markSides = sides; markType = type; return this; }
    Def advAgainst() { advAgainst = true; reveal = true; return this; }
    Def reveal() { reveal = true; return this; }
    Def noReactions() { noReactions = true; return this; }
    Def noOA() { noOA = true; return this; }
    Def nextAttackDis() { nextAttackDis = true; return this; }
    Def nextSaveDie() { nextSaveDie = true; return this; }
    Def noHeal() { noHeal = true; return this; }
    Def temp(int count, int sides, int bonus, int perLevel) { tempDice = count; tempSides = sides; tempBonus = bonus; tempPerLevel = perLevel; return this; }

    // `steps`: slot levels above the spell's level, or (cantrips) how many of levels 5 / 11 / 17 the caster has reached
    int diceAt(int steps) { return dice + dicePerLevel * Math.max(0, steps); }
    int bonusAt(int steps) { return bonus + bonusPerLevel * Math.max(0, steps); }
    int targetsAt(int steps) { return targets + targetsPerLevel * Math.max(0, steps); }
    int sizeAt(int steps) { return size + sizePerLevel * Math.max(0, steps); }
    boolean area() { return CONE.equals(target) || CUBE_SELF.equals(target) || EMANATION.equals(target) || SPHERE.equals(target) || SQUARE.equals(target); }

    // Has a lasting part to put on a target
    boolean lasting() {
        return !conds.isEmpty() || ac != 0 || speed != 0 || rollDie != 0 || againstDie != 0 || markDice > 0 || advAgainst || reveal
                || noReactions || noOA || nextAttackDis || nextSaveDie || noHeal || dotDice > 0 || endSave;
    }
}

static final Map<String, Def> DEFS = new LinkedHashMap<>();

private static Def d(String name, String mode, String target) {
    Def x = new Def(name, mode, target);
    DEFS.put(name, x);
    return x;
}

static final String[] CHROMATIC_TYPES = {"Acid", "Cold", "Fire", "Lightning", "Poison", "Thunder"};
static final String[] BURST_TYPES = {"Acid", "Cold", "Fire", "Lightning", "Poison", "Psychic", "Thunder"};
static final String[] ABILITIES = {"STR", "DEX", "CON", "INT", "WIS", "CHA"};
static final String[] COMMANDS = {"Approach", "Drop", "Flee", "Grovel", "Halt"};
static final String[] EXTRAPLANAR = {"Aberration", "Celestial", "Elemental", "Fey", "Fiend", "Undead"};

// ---------------------------------------------------------------
// Cantrips (damage grows at character levels 5, 11 and 17)
// ---------------------------------------------------------------
static {
    d("Acid Splash", SAVE, SPHERE).range(60).size(5).save("DEX", false).dmg(1, 6, 1, "Acid")
            .how("Place the 5-ft.-radius Sphere. Creatures in it make a DEX save or take the Acid damage.");
    d("Blade Ward", BUFF, SELF).friendly().against(4)
            .how("While you concentrate, attack rolls against you subtract 1d4 (rolled automatically).");
    d("Chill Touch", ATTACK, ONE).melee(5).dmg(1, 10, 1, "Necrotic").noHeal().dur(DUR_CASTER_END)
            .how("Melee spell attack. On a hit the target also can't regain Hit Points until the end of your next turn.");
    d("Dancing Lights", UTILITY, SELF).friendly().how("Lights aren't placed on the board: tracked while you concentrate.");
    d("Druidcraft", UTILITY, SELF).friendly().how("No combat effect.");
    d("Eldritch Blast", ATTACK, ONE).range(120).dmg(1, 10, 0, "Force").targets(1, 1)
            .how("Ranged spell attack for 1d10 Force per beam: 2 beams at level 5, 3 at 11, 4 at 17. Each beam can target a different creature.");
    d("Elementalism", UTILITY, SELF).friendly().how("No combat effect.");
    d("Fire Bolt", ATTACK, ONE).range(120).dmg(1, 10, 1, "Fire").how("Ranged spell attack for the Fire damage.");
    d("Friends", SAVE, ONE).range(10).save("WIS", false).only("Humanoid").cond("Charmed")
            .how("WIS save or Charmed while you concentrate. It succeeds automatically if it's on another team (you're fighting it).");
    d("Guidance", BUFF, ONE).range(5).noSight().friendly()
            .how("Touch: the creature adds 1d4 to checks with one skill while you concentrate (tracked only).");
    d("Light", BUFF, ONE).range(5).noSight().friendly()
            .how("Touch an object a creature carries: it sheds Bright Light 20 ft. and Dim Light 20 ft. more on the board for 1 hour.");
    d("Mage Hand", UTILITY, SELF).friendly().how("No combat effect: tracked for 1 minute.");
    d("Mending", LONG, SELF).how("Casting time 1 minute: cast it outside combat.");
    d("Message", UTILITY, SELF).friendly().how("No combat effect.");
    d("Mind Sliver", SAVE, ONE).range(60).save("INT", false).dmg(1, 6, 1, "Psychic").nextSaveDie().dur(DUR_CASTER_END)
            .how("INT save or take the Psychic damage and subtract 1d4 from its next saving throw before the end of your next turn.");
    d("Minor Illusion", UTILITY, SELF).friendly().how("Illusions aren't modeled on the board.");
    d("Poison Spray", ATTACK, ONE).range(30).dmg(1, 12, 1, "Poison").how("Ranged spell attack for the Poison damage.");
    d("Prestidigitation", UTILITY, SELF).friendly().how("No combat effect.");
    d("Produce Flame", BUFF, SELF).friendly().dmg(1, 8, 1, "Fire")
            .how("A flame in your hand lights the area (20 ft.). Later, a Magic action hurls it: a ranged spell attack (60 ft.) for the Fire damage, ending the spell.");
    d("Ray of Frost", ATTACK, ONE).range(60).dmg(1, 8, 1, "Cold").speed(-10).dur(DUR_CASTER_START)
            .how("Ranged spell attack. On a hit its Speed is also reduced by 10 ft. until the start of your next turn.");
    d("Resistance", BUFF, ONE).range(5).noSight().friendly().choices("Damage type", "Acid", "Bludgeoning", "Cold", "Fire", "Lightning", "Necrotic", "Piercing", "Poison", "Radiant", "Slashing", "Thunder")
            .how("Touch: once per turn the creature reduces damage of the chosen type by 1d4 while you concentrate (subtract it by hand).");
    d("Sacred Flame", SAVE, ONE).range(60).save("DEX", false).dmg(1, 8, 1, "Radiant").noCover()
            .how("DEX save or take the Radiant damage. Cover doesn't help.");
    d("Shillelagh", BUFF, SELF).friendly()
            .how("For 1 minute your Club or Quarterstaff attacks use your spellcasting ability, deal 1d8 (1d10 at 5, 1d12 at 11, 2d6 at 17) and can deal Force damage.");
    d("Shocking Grasp", ATTACK, ONE).melee(5).dmg(1, 8, 1, "Lightning").noOA().dur(DUR_OWN_START)
            .how("Melee spell attack. On a hit the target also can't make opportunity attacks until the start of its next turn.");
    d("Sorcerous Burst", ATTACK, ONE).range(120).dmg(1, 8, 1, "").choices("Damage type", BURST_TYPES)
            .how("Ranged spell attack. Each 8 rolled adds another d8 (up to your spellcasting modifier in extra dice).");
    d("Spare the Dying", BUFF, ONE).range(15).noSight().friendly()
            .how("A creature at 0 HP within range (30 ft. at level 5, 60 at 11, 120 at 17) becomes Stable.");
    d("Starry Wisp", ATTACK, ONE).range(60).dmg(1, 8, 1, "Radiant").reveal().dur(DUR_CASTER_END)
            .how("Ranged spell attack. On a hit the target also can't benefit from Invisible until the end of your next turn.");
    d("Thaumaturgy", UTILITY, SELF).friendly().how("No combat effect.");
    d("Thorn Whip", ATTACK, ONE).melee(30).dmg(1, 6, 1, "Piercing")
            .how("Melee spell attack with 30 ft. reach. On a hit a Large or smaller target is pulled up to 10 ft. toward you.");
    d("Thunderclap", SAVE, EMANATION).size(5).save("CON", false).dmg(1, 6, 1, "Thunder")
            .how("Each creature within 5 ft. of you makes a CON save or takes the Thunder damage.");
    d("Toll the Dead", SAVE, ONE).range(60).save("WIS", false).dmg(1, 8, 1, "Necrotic")
            .how("WIS save or take the Necrotic damage: d8s, or d12s if the target is missing any Hit Points.");
    d("True Strike", ATTACK, ONE).friendly()
            .how("Make one attack with a weapon you hold, using your spellcasting ability. It can deal Radiant damage, and adds 1d6 Radiant at level 5 (2d6 at 11, 3d6 at 17).");
    d("Vicious Mockery", SAVE, ONE).range(60).save("WIS", false).dmg(1, 6, 1, "Psychic").nextAttackDis().dur(DUR_OWN_END)
            .how("WIS save or take the Psychic damage and have Disadvantage on its next attack roll before the end of its next turn.");
    d("Word of Radiance", SAVE, EMANATION).size(5).save("CON", false).dmg(1, 6, 1, "Radiant").choose()
            .how("Each creature you choose within 5 ft. of you makes a CON save or takes the Radiant damage.");
}

// ---------------------------------------------------------------
// Level 1
// ---------------------------------------------------------------
static {
    d("Alarm", LONG, SELF).how("Casting time 1 minute: cast it before the fight.");
    d("Animal Friendship", SAVE, MULTI).range(30).targets(1, 1).save("WIS", false).only("Beast").cond("Charmed")
            .how("Each Beast target makes a WIS save; on a failure it's Charmed for the spell's 24 hours.");
    d("Armor of Agathys", BUFF, SELF).bonus(5, 5).friendly()
            .how("You gain the Temporary Hit Points. While you have them, a creature that hits you with a melee attack takes the Cold damage automatically.");
    d("Arms of Hadar", SAVE, EMANATION).size(10).save("STR", true).dmg(2, 6, 1, "Necrotic").noReactions().dur(DUR_OWN_START)
            .how("Every creature within 10 ft. makes a STR save. A failure also means it can't take Reactions until the start of its next turn.");
    d("Bane", SAVE, MULTI).range(30).targets(3, 1).save("CHA", false).roll(-4)
            .how("Targets that fail the CHA save subtract 1d4 from attack rolls and saving throws (rolled automatically) while you concentrate.");
    d("Bless", BUFF, MULTI).range(30).targets(3, 1).friendly().roll(4)
            .how("Targets add 1d4 to attack rolls and saving throws (rolled automatically) while you concentrate.");
    d("Burning Hands", SAVE, CONE).size(15).save("DEX", true).dmg(3, 6, 1, "Fire")
            .how("Aim the 15-ft. Cone on the board. Creatures in it make a DEX save.");
    d("Buzzing Bee", BUFF, ONE).range(120).reveal()
            .how("No save. The target can't benefit from Invisible and has Disadvantage on Concentration saves while you concentrate.");
    d("Charm Person", SAVE, MULTI).range(30).targets(1, 1).save("WIS", false).only("Humanoid").cond("Charmed")
            .how("Humanoid targets make a WIS save (with Advantage if they're on another team). On a failure they're Charmed until the spell ends or you or an ally damages them.");
    d("Chromatic Orb", ATTACK, ONE).range(90).dmg(3, 8, 1, "").choices("Damage type", CHROMATIC_TYPES)
            .how("Ranged spell attack. If two d8s match on a hit, the orb can leap to another creature within 30 ft. (up to your slot level in leaps).");
    d("Color Spray", SAVE, CONE).size(15).save("CON", false).cond("Blinded").dur(DUR_CASTER_END)
            .how("Creatures in the 15-ft. Cone make a CON save or are Blinded until the end of your next turn.");
    d("Command", SAVE, MULTI).range(60).targets(1, 1).save("WIS", false).choices("Command", COMMANDS)
            .how("Targets make a WIS save or follow the command on their next turn (Grovel: Prone; Halt: no movement, action or Bonus Action).");
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
            .how("After a hit with a melee weapon or Unarmed Strike: +2d8 Radiant, +1d8 against Fiends and Undead.");
    d("Ensnaring Strike", SMITE, SELF).dmg(1, 6, 1, "Piercing").save("STR", false).smite("ANY").friendly()
            .how("After a weapon hit: STR save (Large or bigger: Advantage) or Restrained, taking 1d6 Piercing at the start of each of its turns.");
    d("Entangle", SAVE, SQUARE).range(90).size(20).save("STR", false).cond("Restrained")
            .how("Place the 20-ft. square: it becomes Difficult Terrain. Creatures in it make a STR save or are Restrained while you concentrate.");
    d("Expeditious Retreat", BUFF, SELF).friendly()
            .how("You Dash now, and can Dash as a Bonus Action on later turns while you concentrate.");
    d("Faerie Fire", SAVE, SQUARE).range(60).size(20).save("DEX", false).advAgainst()
            .how("Place the 20-ft. Cube. Creatures that fail the DEX save are outlined: attacks against them have Advantage and they can't benefit from Invisible.");
    d("False Life", BUFF, SELF).temp(2, 4, 4, 5).friendly()
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
            .how("After a hit with a ranged weapon: the target and each creature within 5 ft. of it make a DEX save.");
    d("Healing Word", HEAL, ONE).range(60).dmg(2, 4, 2, "").mod().friendly()
            .how("A creature you can see within 60 ft. regains 2d4 + your spellcasting modifier Hit Points (+2d4 per slot level above 1).");
    d("Hellish Rebuke", SAVE, ONE).range(60).save("DEX", true).dmg(2, 10, 1, "Fire")
            .how("Offered automatically as a Reaction when a creature you can see within 60 ft. damages you. DEX save.");
    d("Heroism", BUFF, MULTI).range(5).targets(1, 1).noSight().friendly()
            .how("Touch: targets are immune to Frightened and gain Temporary Hit Points equal to your spellcasting modifier at the start of each of their turns.");
    d("Hex", BUFF, ONE).range(90).mark(1, 6, "Necrotic").choices("Disadvantage on ability checks with", ABILITIES)
            .how("Pick an enemy: your attack-roll hits against it add 1d6 Necrotic. If it drops to 0 HP, move the curse with a Bonus Action.");
    d("Hunter's Mark", BUFF, ONE).range(90).mark(1, 6, "Force")
            .how("Pick an enemy to mark: your attack-roll hits against it add 1d6 Force. If it drops to 0 HP, move the mark with a Bonus Action.");
    d("Ice Knife", ATTACK, ONE).range(60).dmg(1, 10, 0, "Piercing").save("DEX", false)
            .how("Ranged spell attack for 1d10 Piercing. Hit or miss, the target and creatures within 5 ft. of it make a DEX save or take 2d6 Cold (+1d6 per slot level above 1).");
    d("Identify", LONG, SELF).how("Casting time 1 minute: cast it outside combat.");
    d("Illusory Script", LONG, SELF).how("Casting time 1 minute: cast it outside combat.");
    d("Insidious Rhythm", SAVE, ONE).range(60).save("WIS", false).endSave()
            .how("WIS save. On a failure the target has Disadvantage on Concentration saves while you concentrate; it repeats the save at the end of each of its turns.");
    d("Inflict Wounds", SAVE, ONE).range(5).save("CON", true).dmg(2, 10, 1, "Necrotic").noSight()
            .how("Touch: CON save, 2d10 Necrotic (half on a success).");
    d("Jump", BUFF, MULTI).range(5).targets(1, 1).noSight().friendly()
            .how("Touch: tracked for 1 minute. Jumping isn't modeled on the board (drag the token).");
    d("Longstrider", BUFF, MULTI).range(5).targets(1, 1).noSight().friendly().speed(10)
            .how("Touch: each target's Speed increases by 10 ft. for 1 hour.");
    d("Mage Armor", BUFF, ONE).range(5).noSight().friendly()
            .how("Touch: a target wearing no armor has a base AC of 13 + its DEX modifier for 8 hours.");
    d("Magic Missile", DARTS, MULTI).range(120).targets(3, 1)
            .how("Pick targets, split the darts between them; each dart hits automatically for 1d4 + 1 Force. Shield blocks them.");
    d("Protection from Evil and Good", BUFF, ONE).range(5).noSight().friendly()
            .how("Touch: Aberrations, Celestials, Elementals, Fey, Fiends and Undead have Disadvantage on attacks against the target.");
    d("Purify Food and Drink", UTILITY, SELF).friendly().how("No combat effect.");
    d("Ray of Sickness", ATTACK, ONE).range(60).dmg(2, 8, 1, "Poison").cond("Poisoned").dur(DUR_CASTER_END)
            .how("Ranged spell attack. On a hit the target is also Poisoned until the end of your next turn.");
    d("Sanctuary", BUFF, ONE).range(30).friendly()
            .how("Anyone who attacks the warded creature first makes a WIS save; on a failure the attack is lost. Ends if the warded creature attacks, casts a spell or deals damage.");
    d("Searing Smite", SMITE, SELF).dmg(1, 6, 1, "Fire").save("CON", false).smite("MELEE").friendly()
            .how("After a melee hit: +1d6 Fire now, then 1d6 Fire at the start of each of the target's turns until it succeeds on a CON save.");
    d("Shield", BUFF, SELF).friendly().ac(5)
            .how("Offered automatically as a Reaction when you're hit by an attack or Magic Missile: +5 AC until the start of your next turn.");
    d("Shield of Faith", BUFF, ONE).range(60).friendly().ac(2)
            .how("The target gets +2 AC while you concentrate.");
    d("Silent Image", UTILITY, SELF).friendly().how("Illusions aren't modeled on the board: tracked while you concentrate.");
    d("Sleep", SAVE, SPHERE).range(60).size(5).save("WIS", false).choose()
            .how("Place the 5-ft.-radius Sphere. Chosen creatures that fail the WIS save are Incapacitated; at the end of their next turn they save again or fall Unconscious. Damage wakes them.");
    d("Speak with Animals", UTILITY, SELF).friendly().how("No combat effect: tracked for its duration.");
    d("Spellfire Flare", ATTACK, ONE).range(60).dmg(2, 10, 0, "Radiant").targets(1, 1).noCover()
            .how("Ranged spell attack that ignores Half Cover. One blast per slot level; each can target a different creature.");
    d("Tasha's Hideous Laughter", SAVE, MULTI).range(30).targets(1, 1).save("WIS", false)
            .how("WIS save or Prone and Incapacitated. The target saves again at the end of each of its turns and whenever it takes damage (with Advantage).");
    d("Tenser's Floating Disk", UTILITY, SELF).friendly().how("No combat effect: tracked for its duration.");
    d("Thunderous Smite", SMITE, SELF).dmg(2, 6, 1, "Thunder").save("STR", false).smite("MELEE").friendly()
            .how("After a melee hit: +2d6 Thunder, and a STR save or pushed 10 ft. and Prone.");
    d("Thunderwave", SAVE, CUBE_SELF).size(15).save("CON", true).dmg(2, 8, 1, "Thunder")
            .how("Aim the 15-ft. Cube from your side. CON save; a failure also pushes the creature 10 ft. away.");
    d("Unseen Servant", UTILITY, SELF).friendly().how("No combat effect: tracked for its duration.");
    d("Wardaway", SAVE, ONE).range(60).save("CON", true).dmg(2, 4, 2, "Force").not("Construct", "Undead")
            .how("CON save. On a failure the target's Speed is halved on its next turn and it can take only an action or a Bonus Action, not both.");
    d("Witch Bolt", ATTACK, ONE).range(60).dmg(2, 12, 1, "Lightning")
            .how("Ranged spell attack. On later turns, while you concentrate, a Bonus Action deals 1d12 Lightning to the target automatically.");
    d("Wrathful Smite", SMITE, SELF).dmg(1, 6, 1, "Necrotic").save("WIS", false).smite("MELEE").friendly()
            .how("After a melee hit: +1d6 Necrotic, and a WIS save or Frightened (it saves again at the end of each of its turns).");
}

// The spell's Def (with its level filled in), or null if Combat has none
static Def def(String spell) {
    if (spell == null) return null;
    Def x = DEFS.get(spell);
    if (x != null) {
        Spell s = SpellData.lookup(spell);
        if (s != null) x.level = s.level;
    }
    return x;
}

// Spells without combat rules can still be cast: they spend the slot / action and are tracked for their duration
static Def utilityFor(Spell s) {
    String time = s.castingTime == null ? "" : s.castingTime;
    boolean long_ = !time.isEmpty() && !"Action".equals(time) && !"Bonus Action".equals(time) && !"Reaction".equals(time);
    Def x = new Def(s.name, long_ ? LONG : UTILITY, SELF).friendly();
    x.level = s.level;
    x.how = long_ ? "Casting time " + time + ": too long for a combat turn." : "No combat automation for this spell yet: casting it spends the slot and action and tracks its duration. Apply its effects by hand.";
    return x;
}

static Def defOrUtility(Spell s) {
    Def x = def(s.name);
    return x != null ? x : utilityFor(s);
}

// ===============================================================
// Homebrew spells: the Asset Library's Combat fields -> Def
// ===============================================================
static final String[] CUSTOM_MODES = {"None", "Spell attack", "Saving throw", "Automatic damage", "Healing", "Effect (no roll)", "Smite (after a weapon hit)"};
static final String[] CUSTOM_TARGETS = {"One creature", "Several creatures", "Self", "Cone from you", "Cube from you", "Emanation around you", "Sphere at a point", "Cube at a point"};
static final String[] CUSTOM_DURATIONS = {"Spell's duration", "Until the end of your next turn", "Until the start of your next turn", "Until the start of its next turn", "Until the end of its next turn"};
static final String[] CUSTOM_DICE = {"None", "+1d4", "+1d6", "+1d8", "−1d4", "−1d6", "−1d8"};
static final String[] CUSTOM_SMITE = {"Melee weapon or Unarmed Strike", "Ranged weapon", "Any weapon"};

// Built from a homebrew spell asset, or null when its Combat mode is None (a built-in rule of the same name, if any, stays)
static Def fromAsset(Map<String, Object> a, Spell s) {
    String modeName = CustomAssets.str(a, "combatMode");
    int mi = Arrays.asList(CUSTOM_MODES).indexOf(modeName);
    if (mi <= 0) return null;
    String mode = new String[]{UTILITY, ATTACK, SAVE, AUTO, HEAL, BUFF, SMITE}[mi];
    int ti = Math.max(0, Arrays.asList(CUSTOM_TARGETS).indexOf(CustomAssets.str(a, "combatTarget")));
    String target = new String[]{ONE, MULTI, SELF, CONE, CUBE_SELF, EMANATION, SPHERE, SQUARE}[ti];
    if (SMITE.equals(mode)) target = SELF;
    Def x = new Def(s.name, mode, target);
    x.custom = true;
    x.level = s.level;
    x.range = rangeFt(s.range);
    x.size = CustomAssets.intVal(a, "areaSize", 15);
    x.targets = Math.max(1, CustomAssets.intVal(a, "targetCount", 1));
    x.targetsPerLevel = Math.max(0, CustomAssets.intVal(a, "targetsPerLevel", 0));
    String save = CustomAssets.str(a, "saveAbility");
    x.save = Arrays.asList(ABILITIES).contains(save) ? save : "";
    if (SAVE.equals(mode) && x.save.isEmpty()) x.save = "DEX";
    x.half = CustomAssets.bool(a, "halfOnSave");
    int[] dmg = parseDice(CustomAssets.str(a, "damageDice"));
    int[] per = parseDice(CustomAssets.str(a, "damagePerLevel"));
    x.dice = dmg[0];
    x.sides = dmg[1] > 0 ? dmg[1] : per[1];
    x.bonus = dmg[2];
    x.dicePerLevel = per[1] == x.sides || x.sides == 0 ? per[0] : 0;
    String type = CustomAssets.str(a, "damageType");
    x.type = "None".equals(type) ? "" : type;
    x.addMod = CustomAssets.bool(a, "addModifier");
    x.meleeAttack = "Melee".equals(CustomAssets.str(a, "attackType"));
    x.reach = Math.max(5, x.range);
    int si = Math.max(0, Arrays.asList(CUSTOM_SMITE).indexOf(CustomAssets.str(a, "smiteWith")));
    x.smiteWith = new String[]{"MELEE", "RANGED", "ANY"}[si];
    x.hostile = !HEAL.equals(mode) && !(BUFF.equals(mode) && !CustomAssets.bool(a, "hostileEffect"));
    if (SMITE.equals(mode)) x.hostile = false;
    x.sight = !"Touch".equalsIgnoreCase(s.range.trim());
    // lasting effect
    x.conds.addAll(CustomAssets.list(a, "conditions"));
    int di = Math.max(0, Arrays.asList(CUSTOM_DURATIONS).indexOf(CustomAssets.str(a, "effectDuration")));
    x.dur = new String[]{DUR_SPELL, DUR_CASTER_END, DUR_CASTER_START, DUR_OWN_START, DUR_OWN_END}[di];
    x.endSave = CustomAssets.bool(a, "repeatSave") && !x.save.isEmpty();
    int[] dot = parseDice(CustomAssets.str(a, "ongoingDice"));
    if (dot[0] > 0) x.dot(dot[0], dot[1], noneToEmpty(CustomAssets.str(a, "ongoingType")), CustomAssets.bool(a, "ongoingSave") && !x.save.isEmpty());
    x.ac = CustomAssets.intVal(a, "acBonus", 0);
    x.speed = CustomAssets.intVal(a, "speedBonus", 0);
    x.rollDie = signedDie(CustomAssets.str(a, "rollDie"));
    x.againstDie = Math.abs(signedDie(CustomAssets.str(a, "againstDie")));
    int[] mark = parseDice(CustomAssets.str(a, "markDice"));
    if (mark[0] > 0) x.mark(mark[0], mark[1], noneToEmpty(CustomAssets.str(a, "markType")));
    x.advAgainst = CustomAssets.bool(a, "advantageAgainst");
    x.reveal = x.advAgainst || CustomAssets.bool(a, "revealInvisible");
    x.noReactions = CustomAssets.bool(a, "noReactions");
    int[] tmp = parseDice(CustomAssets.str(a, "tempHp"));
    x.tempDice = tmp[0];
    x.tempSides = tmp[1];
    x.tempBonus = tmp[2];
    x.tempMod = CustomAssets.bool(a, "addModifier") && tmp[0] + tmp[2] > 0 && x.dice == 0;
    x.how = customHow(x);
    return x;
}

private static String noneToEmpty(String s) {
    return "None".equals(s) ? "" : s;
}

private static int signedDie(String s) {
    int i = Arrays.asList(CUSTOM_DICE).indexOf(s);
    if (i <= 0) return 0;
    int die = Integer.parseInt(s.replaceAll("[^0-9d]", "").replace("1d", ""));
    return s.startsWith("+") ? die : -die;
}

// "2d6 + 3" -> {2, 6, 3}; "5" -> {0, 0, 5}; "" -> {0, 0, 0}
static int[] parseDice(String s) {
    int[] out = new int[3];
    if (s == null) return out;
    String t = s.replace(" ", "").toLowerCase();
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d*)d(\\d+)([+-]\\d+)?$").matcher(t);
    if (m.find()) {
        out[0] = m.group(1).isEmpty() ? 1 : Integer.parseInt(m.group(1));
        out[1] = Integer.parseInt(m.group(2));
        out[2] = m.group(3) == null ? 0 : Integer.parseInt(m.group(3));
    } else if (t.matches("[+-]?\\d+")) out[2] = Integer.parseInt(t);
    return out;
}

static boolean validDice(String s) {
    String t = s == null ? "" : s.trim();
    return t.isEmpty() || t.replace(" ", "").toLowerCase().matches("\\d*d\\d+([+-]\\d+)?|[+-]?\\d+");
}

// "60 ft." -> 60, "Touch" -> 5, "Self" / "Self (15-foot cone)" -> 0
static int rangeFt(String range) {
    if (range == null) return 0;
    String r = range.toLowerCase();
    if (r.startsWith("touch")) return 5;
    if (r.startsWith("self")) return 0;
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s*(ft|feet|foot|mile)").matcher(r);
    if (m.find()) return Integer.parseInt(m.group(1)) * (m.group(2).startsWith("mile") ? 5280 : 1);
    return 0;
}

private static String customHow(Def x) {
    List<String> p = new ArrayList<>();
    String dice = x.dice > 0 || x.bonus != 0 ? WeaponData.diceText(x.dice, x.sides, x.bonus) + (x.addMod ? " + modifier" : "") + (x.type.isEmpty() ? "" : " " + x.type) : "";
    switch (x.mode) {
        case ATTACK: p.add((x.meleeAttack ? "Melee" : "Ranged") + " spell attack" + (dice.isEmpty() ? "" : " for " + dice)); break;
        case SAVE: p.add(x.save + " save" + (dice.isEmpty() ? "" : ", " + dice + (x.half ? " (half on a success)" : ""))); break;
        case AUTO: p.add("Hits automatically" + (dice.isEmpty() ? "" : " for " + dice)); break;
        case HEAL: p.add("Heals " + (dice.isEmpty() ? "nothing" : dice)); break;
        case SMITE: p.add("After a weapon hit" + (dice.isEmpty() ? "" : ": +" + dice) + (x.save.isEmpty() ? "" : ", then a " + x.save + " save")); break;
        default: p.add("No roll");
    }
    List<String> fx = new ArrayList<>();
    if (!x.conds.isEmpty()) fx.add(String.join(", ", x.conds));
    if (x.ac != 0) fx.add((x.ac > 0 ? "+" : "") + x.ac + " AC");
    if (x.speed != 0) fx.add((x.speed > 0 ? "+" : "") + x.speed + " ft. Speed");
    if (x.rollDie != 0) fx.add((x.rollDie > 0 ? "+" : "−") + "1d" + Math.abs(x.rollDie) + " to attacks and saves");
    if (x.againstDie != 0) fx.add("−1d" + x.againstDie + " to attacks against it");
    if (x.markDice > 0) fx.add("your hits deal +" + x.markDice + "d" + x.markSides + " " + x.markType);
    if (x.advAgainst) fx.add("attacks against it have Advantage");
    if (x.dotDice > 0) fx.add(x.dotDice + "d" + x.dotSides + " " + x.dotType + " at the start of its turns");
    if (x.tempDice > 0 || x.tempBonus > 0) fx.add("Temporary HP " + WeaponData.diceText(x.tempDice, x.tempSides, x.tempBonus));
    if (!fx.isEmpty()) p.add(SAVE.equals(x.mode) ? "on a failure: " + String.join("; ", fx) : String.join("; ", fx));
    if (x.endSave) p.add("it repeats the save at the end of each of its turns");
    return String.join(". ", p) + ". (Homebrew)";
}

// ===============================================================
// Ongoing effects
// ===============================================================
// One spell's effect on one token. Durations count turn boundaries: casterStarts = how many of the caster's turn
// starts until it ends (10 = 1 minute), casterEnds / ownStarts / ownEnds likewise; -1 = not counted that way.
// The template fields (ac, speed, rollDie...) are copied from the spell's Def when the effect is applied.
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
    // template
    int ac, speed, rollDie, againstDie, markDice, markSides;
    String markType = "";
    boolean advAgainst, reveal, noReactions, noOA, nextAttackDis, nextSaveDie, noHeal;

    boolean lasting() {
        return casterStarts < 0 && casterEnds < 0 && ownStarts < 0 && ownEnds < 0;
    }

    void copyTemplate(Def d) {
        if (d == null) return;
        ac = d.ac;
        speed = d.speed;
        rollDie = d.rollDie;
        againstDie = d.againstDie;
        markDice = d.markDice;
        markSides = d.markSides;
        markType = d.markType;
        advAgainst = d.advAgainst;
        reveal = d.reveal;
        noReactions = d.noReactions;
        noOA = d.noOA;
        nextAttackDis = d.nextAttackDis;
        nextSaveDie = d.nextSaveDie;
        noHeal = d.noHeal;
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
        Map<String, Object> t = new LinkedHashMap<>();
        if (ac != 0) t.put("ac", ac);
        if (speed != 0) t.put("speed", speed);
        if (rollDie != 0) t.put("rollDie", rollDie);
        if (againstDie != 0) t.put("againstDie", againstDie);
        if (markDice > 0) {
            t.put("markDice", markDice);
            t.put("markSides", markSides);
            t.put("markType", markType);
        }
        if (advAgainst) t.put("advAgainst", true);
        if (reveal) t.put("reveal", true);
        if (noReactions) t.put("noReactions", true);
        if (noOA) t.put("noOA", true);
        if (nextAttackDis) t.put("nextAttackDis", true);
        if (nextSaveDie) t.put("nextSaveDie", true);
        if (noHeal) t.put("noHeal", true);
        m.put("tpl", t);
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
        if (m.get("tpl") instanceof Map) {
            Map<?, ?> t = (Map<?, ?>) m.get("tpl");
            e.ac = CustomAssets.intVal(t, "ac", 0);
            e.speed = CustomAssets.intVal(t, "speed", 0);
            e.rollDie = CustomAssets.intVal(t, "rollDie", 0);
            e.againstDie = CustomAssets.intVal(t, "againstDie", 0);
            e.markDice = CustomAssets.intVal(t, "markDice", 0);
            e.markSides = CustomAssets.intVal(t, "markSides", 0);
            e.markType = CustomAssets.str(t, "markType");
            e.advAgainst = CustomAssets.bool(t, "advAgainst");
            e.reveal = CustomAssets.bool(t, "reveal");
            e.noReactions = CustomAssets.bool(t, "noReactions");
            e.noOA = CustomAssets.bool(t, "noOA");
            e.nextAttackDis = CustomAssets.bool(t, "nextAttackDis");
            e.nextSaveDie = CustomAssets.bool(t, "nextSaveDie");
            e.noHeal = CustomAssets.bool(t, "noHeal");
        } else {
            e.copyTemplate(def(e.spell)); // saved before templates existed
        }
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

static boolean any(List<Effect> effects, java.util.function.Predicate<Effect> p) {
    for (Effect e : effects) if (p.test(e)) return true;
    return false;
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

// Short text for chips
static String effectLabel(Effect e) {
    switch (e.spell) {
        case "Bless": return "Blessed (+1d4)";
        case "Bane": return "Baned (−1d4)";
        case "Hex": return "Hexed" + (e.choice.isEmpty() ? "" : " (" + e.choice + ")");
        case "Guiding Bolt": return "Guiding Bolt (next attack: Advantage)";
        case "Faerie Fire": return "Faerie Fire (attacks: Advantage)";
        case "Arms of Hadar": return "No Reactions";
        case "Command": return "Commanded: " + e.choice;
        case "Sleep": return e.phase == 0 ? "Sleep (drowsy)" : "Sleep (asleep)";
        case "Searing Smite": return "Burning (Searing Smite)";
        case "Ensnaring Strike": return "Ensnared";
        case "Armor of Agathys": return "Armor of Agathys (" + e.value + " Cold)";
        case "Wardaway": return "Wardaway (½ Speed, action or Bonus Action)";
        default:
    }
    List<String> p = new ArrayList<>();
    if (e.ac != 0) p.add((e.ac > 0 ? "+" : "") + e.ac + " AC");
    if (e.speed != 0) p.add((e.speed > 0 ? "+" : "") + e.speed + " ft.");
    if (e.markDice > 0) p.add("+" + e.markDice + "d" + e.markSides + " " + e.markType);
    return e.spell + (p.isEmpty() ? "" : " (" + String.join(", ", p) + ")");
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
// What a character or NPC can cast
// ===============================================================
static final class Known {
    String name, source, ability = "INT";
    Spell spell;
    String freeKey = "";    // key in the token's freeCasts pool when it can be cast without a slot
    boolean atWill;         // cantrips and NPC "at will" spells: no slot, no limit
    boolean slots = true;   // can be cast with a spell slot
    int dc = -1, atk = Integer.MIN_VALUE; // NPC stat-block values (characters: worked out)
}

// Every spell the character has prepared (per class, cantrips included) or always prepared (species, feats, class
// features), or that an NPC's stat block lists
static List<Known> castable(Map<?, ?> rec, boolean pc) {
    List<Known> out = new ArrayList<>();
    if (rec == null) return out;
    if (!pc) return npcCastable(rec);
    Map<String, Integer> stats = CharacterStorage.recStatsMap(rec);
    Map<String, Known> byName = new LinkedHashMap<>();
    for (Map.Entry<String, Map<String, List<String>>> en : CharacterStorage.recClassSpells(rec).entrySet()) {
        String cls = en.getKey();
        String ab = SpellData.CASTING_ABILITY.getOrDefault(cls, "INT");
        for (String key : new String[]{"cantrips", "prepared", "arcanum"}) {
            for (String n : en.getValue().get(key)) {
                Spell sp = SpellData.lookup(n);
                if (sp == null || byName.containsKey(n)) continue;
                Known k = new Known();
                k.name = n;
                k.spell = sp;
                k.source = cls + ("arcanum".equals(key) ? " (Mystic Arcanum)" : "");
                k.ability = ab;
                k.atWill = sp.level == 0;
                if ("arcanum".equals(key)) {
                    k.freeKey = n;
                    k.slots = false; // Mystic Arcanum: once per Long Rest, never with a slot
                }
                byName.put(n, k);
            }
        }
    }
    for (Map.Entry<String, String> en : SpellData.alwaysPrepared(rec).entrySet()) {
        Spell sp = SpellData.lookup(en.getKey());
        if (sp == null) continue;
        String src = en.getValue();
        Known k = byName.get(en.getKey());
        if (k == null) {
            k = new Known();
            k.name = en.getKey();
            k.spell = sp;
            k.source = src;
            k.ability = bestMental(stats); // the sheet uses the highest of INT / WIS / CHA
            k.atWill = sp.level == 0;
            byName.put(k.name, k);
        }
        if (sp.level == 0) continue;
        if (src.startsWith("Ranger")) {
            k.ability = "WIS";
            k.freeKey = "Favored Enemy";
        } else if (src.startsWith("Paladin")) {
            k.ability = "CHA";
            k.freeKey = "Paladin's Smite";
        } else {
            k.freeKey = k.name; // species / feat spells: once per Long Rest without a slot
        }
    }
    out.addAll(byName.values());
    out.sort(Comparator.comparingInt((Known k) -> k.spell.level).thenComparing(k -> k.name));
    return out;
}

static List<Known> castable(Map<?, ?> rec) {
    return castable(rec, true);
}

// NPC stat block: at will, with slots, and 1 / 2 / 3 per day
private static List<Known> npcCastable(Map<?, ?> npc) {
    Map<String, Known> byName = new LinkedHashMap<>();
    String ab = CustomAssets.str(npc, "spellcastingAbility");
    if (ab.isEmpty()) ab = "INT";
    int[] dcAtk = npcDcAtk(npc);
    String[][] lists = {{"spellsAtWill", "At will"}, {"spells", "Spell slots"}, {"spells1PerDay", "1/day"}, {"spells2PerDay", "2/day"}, {"spells3PerDay", "3/day"}};
    for (String[] l : lists) {
        for (String n : CustomAssets.list(npc, l[0])) {
            Spell sp = SpellData.lookup(n);
            if (sp == null || byName.containsKey(n)) continue;
            Known k = new Known();
            k.name = n;
            k.spell = sp;
            k.source = "Stat block (" + l[1] + ")";
            k.ability = ab;
            k.dc = dcAtk[0];
            k.atk = dcAtk[1];
            k.atWill = "spellsAtWill".equals(l[0]) || sp.level == 0;
            k.slots = "spells".equals(l[0]);
            if (l[0].endsWith("PerDay")) k.freeKey = n + " (" + l[1] + ")";
            byName.put(n, k);
        }
    }
    List<Known> out = new ArrayList<>(byName.values());
    out.sort(Comparator.comparingInt((Known k) -> k.spell.level).thenComparing(k -> k.name));
    return out;
}

// {save DC, spell attack bonus} for an NPC: its stat block's numbers, or 8 + PB + mod / PB + mod (PB from its CR)
static int[] npcDcAtk(Map<?, ?> npc) {
    String ab = CustomAssets.str(npc, "spellcastingAbility");
    if (ab.isEmpty()) ab = "INT";
    Integer score = CustomAssets.abilities(npc).get(ab);
    int mod = GameData.abilityModifier(score == null ? 10 : score);
    int pb = crProficiency(CustomAssets.str(npc, "cr"));
    int dc = CustomAssets.intVal(npc, "spellSaveDC", 0);
    int atk = CustomAssets.intVal(npc, "spellAttackBonus", 0);
    return new int[]{dc > 0 ? dc : 8 + pb + mod, atk != 0 ? atk : pb + mod};
}

static int crProficiency(String cr) {
    double v;
    try {
        v = cr.contains("/") ? Double.parseDouble(cr.split("/")[0]) / Double.parseDouble(cr.split("/")[1]) : Double.parseDouble(cr.trim());
    } catch (Exception e) {
        v = 1;
    }
    if (v < 5) return 2;
    return Math.min(9, 2 + (int) Math.ceil((v - 4) / 4.0));
}

static boolean npcCaster(Map<?, ?> npc) {
    return npc != null && !npcCastable(npc).isEmpty();
}

static String bestMental(Map<String, Integer> stats) {
    String best = "INT";
    for (String a : new String[]{"WIS", "CHA"}) if (stats.getOrDefault(a, 10) > stats.getOrDefault(best, 10)) best = a;
    return best;
}

// Free (no slot) casts per Long Rest / per day, copied into the token when combat starts
static Map<String, Integer> freeCasts(Map<?, ?> rec, boolean pc) {
    Map<String, Integer> out = new LinkedHashMap<>();
    if (rec == null) return out;
    if (!pc) {
        for (Known k : npcCastable(rec)) if (!k.freeKey.isEmpty()) out.put(k.freeKey, k.freeKey.contains("3/day") ? 3 : k.freeKey.contains("2/day") ? 2 : 1);
        return out;
    }
    Map<String, Integer> lv = CharacterStorage.recClassLevels(rec);
    int ranger = lv.getOrDefault("Ranger", 0);
    if (ranger >= 1) out.put("Favored Enemy", ranger >= 17 ? 6 : ranger >= 13 ? 5 : ranger >= 9 ? 4 : ranger >= 5 ? 3 : 2);
    if (lv.getOrDefault("Paladin", 0) >= 2) out.put("Paladin's Smite", 1);
    for (Map.Entry<String, String> en : SpellData.alwaysPrepared(rec).entrySet()) {
        Spell sp = SpellData.lookup(en.getKey());
        if (sp == null || sp.level == 0 || en.getValue().startsWith("Ranger") || en.getValue().startsWith("Paladin")) continue;
        int uses = en.getValue().contains("Forest Gnome") ? GameData.proficiencyBonus(CharacterStorage.recLevel(rec)) : 1;
        out.put(en.getKey(), uses);
    }
    for (Map<String, List<String>> lists : CharacterStorage.recClassSpells(rec).values()) {
        for (String n : lists.get("arcanum")) out.put(n, 1);
    }
    return out;
}

static Map<String, Integer> freeCasts(Map<?, ?> rec) {
    return freeCasts(rec, true);
}

static int abilityMod(Map<?, ?> rec, String ability) {
    return GameData.abilityModifier(CharacterStorage.recStatsMap(rec).getOrDefault(ability, 10));
}

static int abilityMod(Map<?, ?> source, boolean pc, String ability) {
    if (source == null) return 0;
    if (pc) return abilityMod(source, ability);
    Integer s = CustomAssets.abilities(source).get(ability);
    return GameData.abilityModifier(s == null ? 10 : s);
}

static int profBonus(Map<?, ?> rec) {
    return GameData.proficiencyBonus(CharacterStorage.recLevel(rec));
}

// Caster level for cantrip damage: a character's level, or the NPC's "Spellcaster level"
static int casterLevel(Map<?, ?> source, boolean pc) {
    if (source == null) return 1;
    if (pc) return CharacterStorage.recLevel(source);
    return Math.max(1, CustomAssets.intVal(source, "spellcasterLevel", 1));
}

// Cantrip tiers reached: levels 5, 11 and 17
static int cantripSteps(int level) {
    return (level >= 5 ? 1 : 0) + (level >= 11 ? 1 : 0) + (level >= 17 ? 1 : 0);
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
// Slots left as the source has them now: {levels 1-9, then pact slots}. NPCs: their stat block's "Spell slots" line.
static int[] remainingSlots(Map<?, ?> rec, boolean pc) {
    if (!pc) return maxSlots(rec, false);
    int[] out = new int[10];
    if (rec == null) return out;
    App.SlotState s = App.recSlotState(rec);
    for (int i = 1; i <= 9; i++) out[i - 1] = s.remaining(i);
    out[9] = s.pactRemaining();
    return out;
}

static int[] remainingSlots(Map<?, ?> rec) {
    return remainingSlots(rec, true);
}

static int pactLevel(Map<?, ?> rec) {
    return rec == null ? 0 : SpellData.pactSlotLevel(CharacterStorage.recClassLevels(rec).getOrDefault("Warlock", 0));
}

static int[] maxSlots(Map<?, ?> rec, boolean pc) {
    int[] out = new int[10];
    if (rec == null) return out;
    if (!pc) {
        String[] parts = CustomAssets.str(rec, "spellSlots").split("[,/ ]+");
        int i = 0;
        for (String p : parts) {
            if (p.trim().isEmpty()) continue;
            if (i >= 9) break;
            try {
                out[i++] = Math.max(0, Integer.parseInt(p.trim()));
            } catch (NumberFormatException e) {
                i++;
            }
        }
        return out;
    }
    App.SlotState s = App.recSlotState(rec);
    System.arraycopy(s.max, 0, out, 0, 9);
    out[9] = s.pactMax;
    return out;
}

static int[] maxSlots(Map<?, ?> rec) {
    return maxSlots(rec, true);
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
