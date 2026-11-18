import java.util.*;

// Species traits (2024 Player's Handbook; Tabaxi / Changeling from Monsters of the Multiverse, Warforged from Eberron).
//
// traits(...)       : the trait cards shown on the character sheet (gated by character level where the trait arrives later)
// darkvision(rec)   : Darkvision range in feet (0 = none), used by the sheet and by Combat lighting
// damageMods(rec)   : damage resistances as DamageRules masks, applied to the character's token in Combat
// hpBonus(rec)      : extra max HP (Dwarven Toughness: +1 per level)
// acBonus(rec)      : extra AC (Warforged Integrated Protection)
// Homebrew species (Asset Library) supply darkvision from their "darkvision" field and resistances from typed traits.
class SpeciesTraits {

static final String DARKVISION_INFO = "You can see in Dim Light within range as if it were Bright Light, and in Darkness as if it were Dim Light. "
        + "You discern colors in that Darkness only as shades of gray.\n\n"
        + "In Combat, Darkness normally means you can't see: attacks against creatures you can't see have Disadvantage, "
        + "and creatures that can't see you are attacked with Advantage. Darkvision removes that within its range.";

// Dragonborn ancestry -> damage type
static final Map<String, String> DRACONIC_DAMAGE = new LinkedHashMap<>();
static {
    DRACONIC_DAMAGE.put("Black", "Acid");
    DRACONIC_DAMAGE.put("Blue", "Lightning");
    DRACONIC_DAMAGE.put("Brass", "Fire");
    DRACONIC_DAMAGE.put("Bronze", "Lightning");
    DRACONIC_DAMAGE.put("Copper", "Acid");
    DRACONIC_DAMAGE.put("Gold", "Fire");
    DRACONIC_DAMAGE.put("Green", "Poison");
    DRACONIC_DAMAGE.put("Red", "Fire");
    DRACONIC_DAMAGE.put("Silver", "Cold");
    DRACONIC_DAMAGE.put("White", "Cold");
}

static final class Trait {
    final String name, description;
    final int level; // character level the trait arrives at
    Trait(String name, int level, String description) {
        this.name = name;
        this.level = level;
        this.description = description;
    }
}

// ===============================================================
// Darkvision / resistances / bonuses
// ===============================================================
static int darkvision(String species, String lineage) {
    Map<String, Object> hb = CustomAssets.speciesAsset(species);
    if (hb != null) return CustomAssets.intVal(hb, "darkvision", 0);
    switch (species == null ? "" : species) {
        case "Dwarf":
        case "Orc":
            return 120;
        case "Elf":
            return "Drow".equals(lineage) ? 120 : 60;
        case "Aasimar":
        case "Dragonborn":
        case "Gnome":
        case "Tiefling":
        case "Tabaxi":
            return 60;
        default:
            return 0;
    }
}

static int darkvision(Map<?, ?> rec) {
    return darkvision(CharacterStorage.recStr(rec, "species"), CharacterStorage.recStr(rec, "lineage"));
}

// Damage types the species resists (built-in species)
static List<String> resistances(String species, String lineage) {
    List<String> out = new ArrayList<>();
    switch (species == null ? "" : species) {
        case "Aasimar": out.add("Necrotic"); out.add("Radiant"); break;
        case "Dragonborn": if (DRACONIC_DAMAGE.containsKey(lineage)) out.add(DRACONIC_DAMAGE.get(lineage)); break;
        case "Dwarf": out.add("Poison"); break;
        case "Warforged": out.add("Poison"); break;
        case "Tiefling":
            if ("Abyssal".equals(lineage)) out.add("Poison");
            else if ("Cthonic".equals(lineage) || "Chthonic".equals(lineage)) out.add("Necrotic");
            else if ("Infernal".equals(lineage)) out.add("Fire");
            break;
        default:
    }
    return out;
}

// {damage type -> DamageRules mask} from the character's species (built-in resistances + homebrew typed traits)
static Map<String, Integer> damageMods(Map<?, ?> rec) {
    String species = CharacterStorage.recStr(rec, "species"), lineage = CharacterStorage.recStr(rec, "lineage");
    Map<String, Object> hb = CustomAssets.speciesAsset(species);
    if (hb != null) return DamageRules.fromTraits(hb);
    Map<String, Integer> out = new LinkedHashMap<>();
    for (String t : resistances(species, lineage)) out.merge(t, DamageRules.RESIST, (a, b) -> a | b);
    return out;
}

// Extra max HP from the species (Dwarven Toughness)
static int hpBonus(Map<?, ?> rec) {
    return "Dwarf".equals(CharacterStorage.recStr(rec, "species")) && CustomAssets.speciesAsset("Dwarf") == null
            ? Math.max(1, CharacterStorage.recLevel(rec)) : 0;
}

// Extra AC from the species (Warforged Integrated Protection)
static int acBonus(Map<?, ?> rec) {
    return "Warforged".equals(CharacterStorage.recStr(rec, "species")) && CustomAssets.speciesAsset("Warforged") == null ? 1 : 0;
}

// ===============================================================
// Trait text
// ===============================================================
private static Trait darkvisionTrait(int ft) {
    return new Trait("Darkvision", 1, "You have Darkvision with a range of " + ft + " feet. " + DARKVISION_INFO);
}

// Every trait of a built-in species (all levels); empty for unknown / homebrew species
static List<Trait> allTraits(String species, String lineage) {
    List<Trait> t = new ArrayList<>();
    String lin = lineage == null ? "" : lineage;
    switch (species == null ? "" : species) {
        case "Aasimar":
            t.add(new Trait("Celestial Resistance", 1, "You have Resistance to Necrotic damage and Radiant damage."));
            t.add(darkvisionTrait(60));
            t.add(new Trait("Healing Hands", 1, "As a Magic action, you touch a creature and roll a number of d4s equal to your Proficiency Bonus. "
                    + "The creature regains a number of Hit Points equal to the total rolled. Once you use this trait, you can't use it again until you finish a Long Rest."));
            t.add(new Trait("Light Bearer", 1, "You know the Light cantrip. Charisma is your spellcasting ability for it."));
            t.add(new Trait("Celestial Revelation", 3, "As a Bonus Action you transform for 1 minute (once per Long Rest): Heavenly Wings (Fly Speed equal to your Speed), "
                    + "Inner Radiance (Bright Light 10 ft. and radiant damage to nearby creatures) or Necrotic Shroud (nearby creatures make a CHA save or are Frightened). "
                    + "Once on each of your turns, one target you hit takes extra damage equal to your Proficiency Bonus."));
            break;
        case "Dragonborn": {
            String type = DRACONIC_DAMAGE.getOrDefault(lin, "");
            String typeText = type.isEmpty() ? "your ancestry's damage type" : type;
            t.add(new Trait("Draconic Ancestry", 1, "Your lineage stems from a dragon progenitor" + (lin.isEmpty() ? "" : ": " + lin + " dragon")
                    + ". It determines the damage type of your Breath Weapon and your Damage Resistance" + (type.isEmpty() ? "." : " (" + type + ").")));
            t.add(new Trait("Breath Weapon", 1, "When you take the Attack action, you can replace one of your attacks with an exhalation of magical energy "
                    + "in a 15-foot Cone or a 30-foot Line that is 5 feet wide. Each creature in that area makes a Dexterity saving throw "
                    + "(DC 8 + your Constitution modifier + your Proficiency Bonus). On a failed save it takes 1d10 " + typeText.toLowerCase()
                    + " damage, or half as much on a success. The damage increases to 2d10 at level 5, 3d10 at level 11 and 4d10 at level 17. "
                    + "You can use it a number of times equal to your Proficiency Bonus per Long Rest."));
            t.add(new Trait("Damage Resistance", 1, "You have Resistance to " + typeText + " damage."));
            t.add(darkvisionTrait(60));
            t.add(new Trait("Draconic Flight", 5, "As a Bonus Action, you sprout spectral wings for 10 minutes and gain a Fly Speed equal to your Speed. "
                    + "Once you use this trait, you can't use it again until you finish a Long Rest."));
            break;
        }
        case "Dwarf":
            t.add(darkvisionTrait(120));
            t.add(new Trait("Dwarven Resilience", 1, "You have Resistance to Poison damage. You also have Advantage on saving throws you make to avoid or end the Poisoned condition."));
            t.add(new Trait("Dwarven Toughness", 1, "Your Hit Point maximum increases by 1, and it increases by 1 again whenever you gain a level (already included in your Hit Points)."));
            t.add(new Trait("Stonecunning", 1, "As a Bonus Action, you gain Tremorsense with a range of 60 feet for 10 minutes. You must be on a stone surface or touching one to use it. "
                    + "You can use this Bonus Action a number of times equal to your Proficiency Bonus per Long Rest."));
            break;
        case "Elf": {
            t.add(darkvisionTrait("Drow".equals(lin) ? 120 : 60));
            String lineageText;
            if ("Drow".equals(lin)) lineageText = "Drow: your Darkvision has a range of 120 feet, and you know the Dancing Lights cantrip. At level 3 you can cast Faerie Fire, and at level 5 Darkness, once each per Long Rest without a spell slot.";
            else if ("High Elf".equals(lin)) lineageText = "High Elf: you know the Prestidigitation cantrip (you can swap it for another Wizard cantrip after a Long Rest). At level 3 you can cast Detect Magic, and at level 5 Misty Step, once each per Long Rest without a spell slot.";
            else if ("Wood Elf".equals(lin)) lineageText = "Wood Elf: your Speed is 35 feet, and you know the Druidcraft cantrip. At level 3 you can cast Longstrider, and at level 5 Pass without Trace, once each per Long Rest without a spell slot.";
            else lineageText = "You are part of a lineage (Drow, High Elf or Wood Elf) that grants spells and other benefits.";
            t.add(new Trait("Elven Lineage", 1, lineageText + " Intelligence, Wisdom or Charisma is your spellcasting ability for these spells (chosen with the lineage)."));
            t.add(new Trait("Fey Ancestry", 1, "You have Advantage on saving throws you make to avoid or end the Charmed condition."));
            t.add(new Trait("Keen Senses", 1, "You have proficiency in the Insight, Perception or Survival skill (your choice)."));
            t.add(new Trait("Trance", 1, "You don't need to sleep, and magic can't put you to sleep. You can finish a Long Rest in 4 hours if you spend them in a trancelike meditation, during which you retain consciousness."));
            break;
        }
        case "Gnome": {
            t.add(darkvisionTrait(60));
            t.add(new Trait("Gnomish Cunning", 1, "You have Advantage on Intelligence, Wisdom and Charisma saving throws."));
            String lineageText;
            if ("Forest Gnome".equals(lin)) lineageText = "Forest Gnome: you know the Minor Illusion cantrip. You also always have Speak with Animals prepared and can cast it without a spell slot a number of times equal to your Proficiency Bonus per Long Rest.";
            else if ("Rock Gnome".equals(lin)) lineageText = "Rock Gnome: you know the Mending and Prestidigitation cantrips. You can spend 10 minutes casting Prestidigitation to create a Tiny clockwork device (AC 5, 1 HP) that lights, plays sounds, or produces simple effects; you can have up to three at a time.";
            else lineageText = "You are part of a lineage (Forest Gnome or Rock Gnome) that grants spells.";
            t.add(new Trait("Gnomish Lineage", 1, lineageText + " Intelligence, Wisdom or Charisma is your spellcasting ability for these spells."));
            break;
        }
        case "Goliath": {
            String ancestry;
            switch (lin) {
                case "Cloud": ancestry = "Cloud's Jaunt: as a Bonus Action, you magically teleport up to 30 feet to an unoccupied space you can see."; break;
                case "Fire": ancestry = "Fire's Burn: when you hit a target with an attack roll and deal damage to it, you can also deal 1d10 Fire damage to that target."; break;
                case "Frost": ancestry = "Frost's Chill: when you hit a target with an attack roll and deal damage to it, you can also deal 1d6 Cold damage to it and reduce its Speed by 10 feet until the start of your next turn."; break;
                case "Hill": ancestry = "Hill's Tumble: when you hit a Large or smaller creature with an attack roll and deal damage to it, you can give that target the Prone condition."; break;
                case "Stone": ancestry = "Stone's Endurance: when you take damage, you can take a Reaction to roll 1d12, add your Constitution modifier, and reduce the damage by that total."; break;
                case "Storm": ancestry = "Storm's Thunder: when you take damage from a creature within 60 feet of you, you can take a Reaction to deal 1d8 Thunder damage to that creature."; break;
                default: ancestry = "You are descended from giants (Cloud, Fire, Frost, Hill, Stone or Storm), which grants a supernatural boon.";
            }
            t.add(new Trait("Giant Ancestry", 1, ancestry + " You can use this benefit a number of times equal to your Proficiency Bonus per Long Rest."));
            t.add(new Trait("Large Form", 5, "As a Bonus Action, you change your size to Large for 10 minutes (if you have room). You have Advantage on Strength checks and your Speed increases by 10 feet. Once per Long Rest."));
            t.add(new Trait("Powerful Build", 1, "You have Advantage on any ability check you make to end the Grappled condition. You also count as one size larger when determining your carrying capacity."));
            break;
        }
        case "Halfling":
            t.add(new Trait("Brave", 1, "You have Advantage on saving throws you make to avoid or end the Frightened condition."));
            t.add(new Trait("Halfling Nimbleness", 1, "You can move through the space of any creature that is a size larger than you, but you can't stop in the same space."));
            t.add(new Trait("Luck", 1, "When you roll a 1 on the d20 of a D20 Test, you can reroll the die, and you must use the new roll."));
            t.add(new Trait("Naturally Stealthy", 1, "You can take the Hide action even when you are obscured only by a creature that is at least one size larger than you."));
            break;
        case "Human":
            t.add(new Trait("Resourceful", 1, "You gain Heroic Inspiration whenever you finish a Long Rest."));
            t.add(new Trait("Skillful", 1, "You gain proficiency in one skill of your choice."));
            t.add(new Trait("Versatile", 1, "You gain an Origin feat of your choice (Skilled is recommended)."));
            break;
        case "Orc":
            t.add(new Trait("Adrenaline Rush", 1, "You can take the Dash action as a Bonus Action. When you do so, you gain Temporary Hit Points equal to your Proficiency Bonus. "
                    + "You can use this trait a number of times equal to your Proficiency Bonus per Short or Long Rest."));
            t.add(darkvisionTrait(120));
            t.add(new Trait("Relentless Endurance", 1, "When you are reduced to 0 Hit Points but not killed outright, you can drop to 1 Hit Point instead. Once per Long Rest."));
            break;
        case "Tiefling": {
            t.add(darkvisionTrait(60));
            String legacy;
            if ("Abyssal".equals(lin)) legacy = "Abyssal: you have Resistance to Poison damage and know the Poison Spray cantrip. At level 3 you can cast Ray of Sickness, and at level 5 Hold Person, once each per Long Rest without a spell slot.";
            else if ("Cthonic".equals(lin) || "Chthonic".equals(lin)) legacy = "Chthonic: you have Resistance to Necrotic damage and know the Chill Touch cantrip. At level 3 you can cast False Life, and at level 5 Ray of Enfeeblement, once each per Long Rest without a spell slot.";
            else if ("Infernal".equals(lin)) legacy = "Infernal: you have Resistance to Fire damage and know the Fire Bolt cantrip. At level 3 you can cast Hellish Rebuke, and at level 5 Darkness, once each per Long Rest without a spell slot.";
            else legacy = "You have a fiendish legacy (Abyssal, Chthonic or Infernal) that grants a damage resistance and spells.";
            t.add(new Trait("Fiendish Legacy", 1, legacy + " Intelligence, Wisdom or Charisma is your spellcasting ability for these spells."));
            t.add(new Trait("Otherworldly Presence", 1, "You know the Thaumaturgy cantrip, using the same spellcasting ability as your Fiendish Legacy."));
            break;
        }
        case "Tabaxi":
            t.add(new Trait("Cat's Claws", 1, "You have a Climb Speed equal to your walking Speed. Your claws are natural weapons: your Unarmed Strikes with them deal 1d6 + Strength modifier Slashing damage."));
            t.add(new Trait("Cat's Talent", 1, "You have proficiency in the Perception and Stealth skills."));
            t.add(darkvisionTrait(60));
            t.add(new Trait("Feline Agility", 1, "When you move on your turn in combat, you can double your Speed until the end of the turn. Once you use this trait, "
                    + "you can't use it again until you move 0 feet on one of your turns."));
            break;
        case "Changeling":
            t.add(new Trait("Changeling Instincts", 1, "You have proficiency in two of these skills of your choice: Deception, Insight, Intimidation, Performance or Persuasion."));
            t.add(new Trait("Shapechanger", 1, "As an action, you can change your appearance and voice: height, weight, facial features, hair, and coloring. "
                    + "You can appear as a member of another species, though your game statistics don't change. You stay in the new form until you take an action to revert or change again."));
            break;
        case "Warforged":
            t.add(new Trait("Constructed Resilience", 1, "You have Resistance to Poison damage and Advantage on saving throws against being Poisoned. "
                    + "You don't need to eat, drink or breathe, you are immune to disease, and magic can't put you to sleep."));
            t.add(new Trait("Integrated Protection", 1, "Your body has built-in defensive layers: you gain a +1 bonus to Armor Class (already included). "
                    + "Donning or doffing armor that is incorporated into your body takes 1 hour."));
            t.add(new Trait("Sentry's Rest", 1, "When you take a Long Rest, you spend at least 6 hours in an inactive, motionless state rather than sleeping. You appear inert but remain conscious and can see and hear as normal."));
            t.add(new Trait("Specialized Design", 1, "You gain proficiency in one skill and one tool of your choice."));
            break;
        default:
    }
    return t;
}

// Traits the character has at its current level
static List<Trait> traits(Map<?, ?> rec) {
    int level = Math.max(1, CharacterStorage.recLevel(rec));
    List<Trait> out = new ArrayList<>();
    for (Trait t : allTraits(CharacterStorage.recStr(rec, "species"), CharacterStorage.recStr(rec, "lineage"))) if (t.level <= level) out.add(t);
    return out;
}

// "Darkvision 60 ft. · Resistance: Poison" style summary of a species (wizard info panel); "" if nothing to say
static String summary(String species, String lineage) {
    List<String> parts = new ArrayList<>();
    List<String> names = new ArrayList<>();
    for (Trait t : allTraits(species, lineage)) if (t.level <= 1) names.add(t.name);
    if (!names.isEmpty()) parts.add("Traits: " + String.join(", ", names) + ".");
    int dv = darkvision(species, lineage);
    if (dv > 0) parts.add("Darkvision " + dv + " ft.");
    List<String> res = resistances(species, lineage);
    if ("Dragonborn".equals(species) && res.isEmpty()) parts.add("Resistance from your Draconic Ancestry.");
    else if ("Tiefling".equals(species) && res.isEmpty()) parts.add("Resistance from your Fiendish Legacy.");
    else if (!res.isEmpty()) parts.add("Resistance: " + String.join(", ", res) + ".");
    return String.join(" ", parts);
}
}
