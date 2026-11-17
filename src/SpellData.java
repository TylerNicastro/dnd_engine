import java.util.*;
import java.util.List;

// Spell registry (2024 PHB) + spellcasting progression rules.
//
// To add a spell, add one line to the matching level block below:
//     add(2, "Spell Name", EVO, A, "60 ft.", "V, S, M (a pinch of salt)", "C 1 minute", "Sor Wiz",
//         "What it does.").higher("What it does with a higher slot.");
// Duration "C <time>" means Concentration, up to <time>. Class codes: Art Brd Clr Dru Pal Rgr Sor Wlk Wiz.
// Chain .ritual() for ritual spells and .image("art/spells/fireball.png") to give the spell card art.
// Conditions named in the description (Frightened, Prone, ...) are detected automatically and become
// clickable links on the sheet; call .inflicts(...) to set them explicitly instead.
class SpellData {

static final String ABJ = "Abjuration", CON = "Conjuration", DIV = "Divination", ENC = "Enchantment",
        EVO = "Evocation", ILL = "Illusion", NEC = "Necromancy", TRA = "Transmutation";
private static final String A = "Action", BA = "Bonus Action", R = "Reaction";
private static final String INST = "Instantaneous";

static final Map<String, Spell> SPELLS = new LinkedHashMap<>();
private static final Map<String, String> CLASS_CODES = new LinkedHashMap<>();
static {
    CLASS_CODES.put("Art", "Artificer");
    CLASS_CODES.put("Brd", "Bard");
    CLASS_CODES.put("Clr", "Cleric");
    CLASS_CODES.put("Dru", "Druid");
    CLASS_CODES.put("Pal", "Paladin");
    CLASS_CODES.put("Rgr", "Ranger");
    CLASS_CODES.put("Sor", "Sorcerer");
    CLASS_CODES.put("Wlk", "Warlock");
    CLASS_CODES.put("Wiz", "Wizard");
}

// ---------------------------------------------------------------
// Cantrips
// ---------------------------------------------------------------
static {
    add(0, "Acid Splash", EVO, A, "60 ft.", "V, S", INST, "Art Sor Wiz",
            "Create an acidic bubble that bursts in a 5-ft.-radius Sphere. Each creature there must succeed on a DEX save or take 1d6 Acid damage.")
            .higher("Damage increases by 1d6 at levels 5 (2d6), 11 (3d6), and 17 (4d6).");
    add(0, "Blade Ward", ABJ, A, "Self", "V, S", "C 1 minute", "Brd Sor Wlk Wiz",
            "Whenever a creature makes an attack roll against you before the spell ends, the attacker subtracts 1d4 from the roll.");
    add(0, "Chill Touch", NEC, A, "Touch", "V, S", INST, "Sor Wlk Wiz",
            "Make a melee spell attack. On a hit, the target takes 1d10 Necrotic damage and can't regain Hit Points until the end of your next turn.")
            .higher("Damage increases by 1d10 at levels 5, 11, and 17.");
    add(0, "Dancing Lights", ILL, A, "120 ft.", "V, S, M (a bit of phosphorus)", "C 1 minute", "Art Brd Sor Wiz",
            "Create up to four torch-sized lights (or one Medium glowing form) that shed Dim Light in a 10-ft. radius. Move them up to 60 ft. as a Bonus Action.");
    add(0, "Druidcraft", TRA, A, "30 ft.", "V, S", INST, "Dru",
            "Whisper to the spirits of nature: predict the weather, make a flower bloom, create a harmless sensory effect, or light or snuff a small flame.");
    add(0, "Eldritch Blast", EVO, A, "120 ft.", "V, S", INST, "Wlk",
            "Hurl a beam of crackling energy. Make a ranged spell attack; on a hit the target takes 1d10 Force damage.")
            .higher("Creates two beams at level 5, three at level 11, and four at level 17. Each beam can target a different creature.");
    add(0, "Elementalism", TRA, A, "30 ft.", "V, S", INST, "Art Dru Sor Wiz",
            "Exert minor control over the elements: a puff of wind, a spray of water, a harmless ember, a small earthen trinket, or shifting dust.");
    add(0, "Fire Bolt", EVO, A, "120 ft.", "V, S", INST, "Art Sor Wiz",
            "Hurl a mote of fire. Make a ranged spell attack; on a hit the target takes 1d10 Fire damage. Flammable objects that aren't worn or carried start burning.")
            .higher("Damage increases by 1d10 at levels 5, 11, and 17.");
    add(0, "Friends", ENC, A, "10 ft.", "S, M (some makeup)", "C 1 minute", "Brd Sor Wlk Wiz",
            "One creature you can see must succeed on a WIS save or have the Charmed condition for the duration. It succeeds automatically if it isn't a Humanoid or you're fighting it, and it knows it was charmed when the spell ends.");
    add(0, "Guidance", DIV, A, "Touch", "V, S", "C 1 minute", "Art Clr Dru",
            "Choose a skill. Until the spell ends, one willing creature adds 1d4 to any ability check using that skill.");
    add(0, "Light", EVO, A, "Touch", "V, M (a firefly or phosphorescent moss)", "1 hour", "Art Brd Clr Sor Wiz",
            "Touch an object no larger than 10 ft.; it sheds Bright Light in a 20-ft. radius and Dim Light for another 20 ft.");
    add(0, "Mage Hand", CON, A, "30 ft.", "V, S", "1 minute", "Art Brd Sor Wlk Wiz",
            "Summon a spectral floating hand that can manipulate objects, open containers, or carry up to 10 lb. Control it with a Magic action.");
    add(0, "Mending", TRA, "1 minute", "Touch", "V, S, M (two lodestones)", INST, "Art Brd Clr Dru Sor Wiz",
            "Repair a single break or tear in an object you touch, such as a broken chain link or a torn cloak, no larger than 1 ft.");
    add(0, "Message", TRA, A, "120 ft.", "S, M (a copper wire)", "1 round", "Art Brd Dru Sor Wiz",
            "Whisper a message to a creature in range; only it hears, and it can reply in a whisper that only you hear.");
    add(0, "Mind Sliver", ENC, A, "60 ft.", "V", "1 round", "Sor Wlk Wiz",
            "Drive a spike of psychic energy into a creature. On a failed INT save it takes 1d6 Psychic damage and subtracts 1d4 from its next saving throw before the end of your next turn.")
            .higher("Damage increases by 1d6 at levels 5, 11, and 17.");
    add(0, "Minor Illusion", ILL, A, "30 ft.", "S, M (a bit of fleece)", "1 minute", "Brd Sor Wlk Wiz",
            "Create a sound or an image of an object no larger than a 5-ft. Cube. A creature that uses its action to study it can see through it with an INT (Investigation) check.");
    add(0, "Poison Spray", NEC, A, "30 ft.", "V, S", INST, "Art Dru Sor Wlk Wiz",
            "Spray toxic mist at a creature. Make a ranged spell attack; on a hit the target takes 1d12 Poison damage.")
            .higher("Damage increases by 1d12 at levels 5, 11, and 17.");
    add(0, "Prestidigitation", TRA, A, "10 ft.", "V, S", "Up to 1 hour", "Art Brd Sor Wlk Wiz",
            "Perform a minor magical trick: a harmless sensory effect, light or snuff a candle, clean or soil an object, chill or warm food, or make a small mark or trinket.");
    add(0, "Produce Flame", CON, BA, "Self", "V, S", "10 minutes", "Dru",
            "A flickering flame appears in your hand, shedding light in a 20-ft. radius. As a Magic action you can hurl it at a creature within 60 ft. (ranged spell attack, 1d8 Fire damage).")
            .higher("Damage increases by 1d8 at levels 5, 11, and 17.");
    add(0, "Ray of Frost", EVO, A, "60 ft.", "V, S", INST, "Art Sor Wiz",
            "A frigid beam streaks toward a creature. Make a ranged spell attack; on a hit it takes 1d8 Cold damage and its Speed is reduced by 10 ft. until the start of your next turn.")
            .higher("Damage increases by 1d8 at levels 5, 11, and 17.");
    add(0, "Resistance", ABJ, A, "Touch", "V, S", "C 1 minute", "Art Clr Dru",
            "Choose a damage type. Once per turn when the willing creature you touch takes damage of that type, it reduces the damage by 1d4.");
    add(0, "Sacred Flame", EVO, A, "60 ft.", "V, S", INST, "Clr",
            "Flame-like radiance descends on a creature. It must succeed on a DEX save or take 1d8 Radiant damage; it gains no benefit from Half or Three-Quarters Cover.")
            .higher("Damage increases by 1d8 at levels 5, 11, and 17.");
    add(0, "Shillelagh", TRA, BA, "Self", "V, S, M (mistletoe)", "1 minute", "Dru",
            "A Club or Quarterstaff you hold uses your spellcasting ability for attack and damage rolls, its damage die becomes a d8, and it can deal Force damage.")
            .higher("The damage die becomes a d10 at level 5, a d12 at level 11, and 2d6 at level 17.");
    add(0, "Shocking Grasp", EVO, A, "Touch", "V, S", INST, "Art Sor Wiz",
            "Lightning springs from your hand. Make a melee spell attack; on a hit the target takes 1d8 Lightning damage and can't make Opportunity Attacks until the start of its next turn.")
            .higher("Damage increases by 1d8 at levels 5, 11, and 17.");
    add(0, "Sorcerous Burst", EVO, A, "120 ft.", "V, S", INST, "Sor",
            "Cast sorcerous energy at a creature. Make a ranged spell attack; on a hit it takes 1d8 damage of a type you choose (Acid, Cold, Fire, Lightning, Poison, Psychic, or Thunder). Each 8 rolled adds another d8, up to your spellcasting modifier.")
            .higher("Damage increases by 1d8 at levels 5, 11, and 17.");
    add(0, "Spare the Dying", NEC, A, "15 ft.", "V, S", INST, "Art Clr Dru",
            "Choose a creature that has 0 Hit Points and isn't dead. It becomes Stable.")
            .higher("Range doubles at levels 5, 11, and 17.");
    add(0, "Starry Wisp", EVO, A, "60 ft.", "V, S", INST, "Brd Dru",
            "Launch a mote of light. Make a ranged spell attack; on a hit the target takes 1d8 Radiant damage and, until the end of your next turn, it emits Dim Light and can't benefit from the Invisible condition.")
            .higher("Damage increases by 1d8 at levels 5, 11, and 17.");
    add(0, "Thaumaturgy", TRA, A, "30 ft.", "V", "Up to 1 minute", "Clr",
            "Manifest a minor wonder: boom your voice, flicker flames, cause tremors, make doors fly open, or alter the appearance of your eyes.");
    add(0, "Thorn Whip", TRA, A, "30 ft.", "V, S, M (the stem of a thorny plant)", INST, "Art Dru",
            "Lash out with a vine-like whip. Make a melee spell attack; on a hit the target takes 1d6 Piercing damage and, if Large or smaller, is pulled up to 10 ft. closer to you.")
            .higher("Damage increases by 1d6 at levels 5, 11, and 17.");
    add(0, "Thunderclap", EVO, A, "Self", "S", INST, "Art Brd Dru Sor Wlk Wiz",
            "Each creature in a 5-ft. Emanation around you must succeed on a CON save or take 1d6 Thunder damage. The sound is audible 100 ft. away.")
            .higher("Damage increases by 1d6 at levels 5, 11, and 17.");
    add(0, "Toll the Dead", NEC, A, "60 ft.", "V, S", INST, "Clr Wlk Wiz",
            "A dolorous bell rings near a creature. On a failed WIS save it takes 1d8 Necrotic damage, or 1d12 if it's missing any Hit Points.")
            .higher("Damage increases by one die at levels 5, 11, and 17.");
    add(0, "True Strike", DIV, A, "Self", "S, M (a weapon with which you have proficiency)", INST, "Art Brd Sor Wlk Wiz",
            "Make one attack with the weapon used in the casting, using your spellcasting ability instead of STR or DEX. It can deal Radiant damage instead of its normal type.")
            .higher("Adds 1d6 Radiant damage at level 5, 2d6 at level 11, and 3d6 at level 17.");
    add(0, "Vicious Mockery", ENC, A, "60 ft.", "V", INST, "Brd",
            "Unleash a string of insults. On a failed WIS save the target takes 1d6 Psychic damage and has Disadvantage on its next attack roll before the end of its next turn.")
            .higher("Damage increases by 1d6 at levels 5, 11, and 17.");
    add(0, "Word of Radiance", EVO, A, "Self", "V, M (a sunburst token)", INST, "Clr",
            "Burning radiance erupts from you in a 5-ft. Emanation. Each creature you choose there must succeed on a CON save or take 1d6 Radiant damage.")
            .higher("Damage increases by 1d6 at levels 5, 11, and 17.");
}

// ---------------------------------------------------------------
// Level 1
// ---------------------------------------------------------------
static {
    add(1, "Alarm", ABJ, "1 minute", "30 ft.", "V, S, M (a bell and silver wire)", "8 hours", "Art Rgr Wiz",
            "Set a ward on a door, window, or area no larger than a 20-ft. Cube. Until the spell ends, you're alerted (mentally or audibly) whenever a creature touches or enters it.").ritual();
    add(1, "Animal Friendship", ENC, A, "30 ft.", "V, S, M (a morsel of food)", "24 hours", "Brd Dru Rgr",
            "Target a Beast you can see. It must succeed on a WIS save or have the Charmed condition for the duration.")
            .higher("You can target one additional Beast for each slot level above 1.");
    add(1, "Armor of Agathys", ABJ, BA, "Self", "V, S, M (a shard of blue glass)", "1 hour", "Wlk",
            "You gain 5 Temporary Hit Points. While you have them, any creature that hits you with a melee attack takes 5 Cold damage.")
            .higher("Temporary Hit Points and Cold damage increase by 5 for each slot level above 1.");
    add(1, "Arms of Hadar", CON, A, "Self", "V, S", INST, "Wlk",
            "Dark tendrils erupt in a 10-ft. Emanation. Each creature there makes a STR save, taking 2d6 Necrotic damage and losing its Reaction on a failure, or half damage on a success.")
            .higher("Damage increases by 1d6 for each slot level above 1.");
    add(1, "Bane", ENC, A, "30 ft.", "V, S, M (a drop of blood)", "C 1 minute", "Brd Clr Wlk",
            "Up to three creatures must make a CHA save. On a failure, a target subtracts 1d4 from attack rolls and saving throws for the duration.")
            .higher("You can target one additional creature for each slot level above 1.");
    add(1, "Bless", ENC, A, "30 ft.", "V, S, M (a Holy Symbol worth 5+ GP)", "C 1 minute", "Clr Pal",
            "Up to three creatures add 1d4 to their attack rolls and saving throws for the duration.")
            .higher("You can target one additional creature for each slot level above 1.");
    add(1, "Burning Hands", EVO, A, "Self", "V, S", INST, "Sor Wiz",
            "Flames shoot out in a 15-ft. Cone. Each creature there makes a DEX save, taking 3d6 Fire damage on a failure or half on a success.")
            .higher("Damage increases by 1d6 for each slot level above 1.");
    add(1, "Buzzing Bee", CON, A, "120 ft.", "V, S", "C 1 minute", "Dru Rgr Sor Wiz",
            "A spectral bee appears and buzzes around a creature you can see, with no saving throw. While the spell lasts, the target can't benefit from the Invisible condition and has Disadvantage on CON saves to maintain Concentration.");
    add(1, "Insidious Rhythm", ENC, A, "60 ft.", "V", "C 1 minute", "Brd",
            "A maddening rhythm lodges in a creature's mind. On a failed WIS save it has Disadvantage on CON saves to maintain Concentration for the duration. It repeats the save at the end of each of its turns, ending the spell on a success.");
    add(1, "Spellfire Flare", EVO, A, "60 ft.", "V, S", INST, "Sor Wiz",
            "Hurl a blast of raw spellfire. Make a ranged spell attack that ignores Half and Three-Quarters Cover; on a hit the target takes 2d10 Radiant damage.")
            .higher("Creates one additional blast for each slot level above 1; each blast can target a different creature.");
    add(1, "Wardaway", ABJ, A, "60 ft.", "V, S, M (a miniature clay hand)", INST, "Brd Clr Pal Wiz",
            "A warding force strikes a creature. It makes a CON save, taking 2d4 Force damage on a failure or half on a success. On a failure its Speed is halved and it can take only an action or a Bonus Action (not both) on its next turn.")
            .higher("Damage increases by 2d4 for each slot level above 1.");
    add(1, "Charm Person", ENC, A, "30 ft.", "V, S", "1 hour", "Brd Dru Sor Wlk Wiz",
            "A Humanoid must succeed on a WIS save (with Advantage if you or allies are fighting it) or have the Charmed condition until the spell ends or you or your allies damage it.")
            .higher("You can target one additional creature for each slot level above 1.");
    add(1, "Chromatic Orb", EVO, A, "90 ft.", "V, S, M (a diamond worth 50+ GP)", INST, "Sor Wiz",
            "Hurl an orb of energy. Make a ranged spell attack; on a hit the target takes 3d8 damage of a type you choose (Acid, Cold, Fire, Lightning, Poison, or Thunder). If two d8s match, the orb leaps to another target.")
            .higher("Damage increases by 1d8 for each slot level above 1.");
    add(1, "Color Spray", ILL, A, "Self", "V, S, M (a pinch of colorful sand)", INST, "Brd Sor Wiz",
            "A dazzling array of light flashes in a 15-ft. Cone. Each creature there must succeed on a CON save or have the Blinded condition until the end of your next turn.");
    add(1, "Command", ENC, A, "60 ft.", "V", INST, "Brd Clr Pal",
            "Speak a one-word command to a creature, which must succeed on a WIS save or follow it on its next turn: Approach, Drop, Flee, Grovel (falls Prone), or Halt.")
            .higher("You can target one additional creature for each slot level above 1.");
    add(1, "Compelled Duel", ENC, BA, "30 ft.", "V", "C 1 minute", "Pal",
            "A creature must succeed on a WIS save or have Disadvantage on attack rolls against creatures other than you, and it can't willingly move more than 30 ft. away from you.");
    add(1, "Comprehend Languages", DIV, A, "Self", "V, S, M (a pinch of soot and salt)", "1 hour", "Brd Sor Wlk Wiz",
            "You understand the literal meaning of any language you hear or sign, and of any written language you touch.").ritual();
    add(1, "Create or Destroy Water", TRA, A, "30 ft.", "V, S, M (a mix of water and sand)", INST, "Clr Dru",
            "Create up to 10 gallons of clean water or rain in a 30-ft. Cube, or destroy that much water or fog.")
            .higher("Create or destroy 10 more gallons, or the Cube grows by 5 ft., for each slot level above 1.");
    add(1, "Cure Wounds", ABJ, A, "Touch", "V, S", INST, "Art Brd Clr Dru Pal Rgr",
            "A creature you touch regains Hit Points equal to 2d8 plus your spellcasting ability modifier.")
            .higher("Healing increases by 2d8 for each slot level above 1.");
    add(1, "Detect Evil and Good", DIV, A, "Self", "V, S", "C 10 minutes", "Clr Pal",
            "You sense the location and type of any Aberration, Celestial, Elemental, Fey, Fiend, or Undead within 30 ft. of you, and any consecrated or desecrated place.");
    add(1, "Detect Magic", DIV, A, "Self", "V, S", "C 10 minutes", "Art Brd Clr Dru Pal Rgr Sor Wlk Wiz",
            "You sense the presence of magic within 30 ft. of you, and can use your action to see a faint aura around visible magical creatures or objects and learn their school of magic.").ritual();
    add(1, "Detect Poison and Disease", DIV, A, "Self", "V, S, M (a yew leaf)", "C 10 minutes", "Clr Dru Pal Rgr",
            "You sense the location of poisons, poisonous or venomous creatures, and magical contagions within 30 ft. of you, and identify their kind.").ritual();
    add(1, "Disguise Self", ILL, A, "Self", "V, S", "1 hour", "Art Brd Sor Wiz",
            "Make yourself, including your clothing and gear, look different. A creature can discern the disguise with a successful INT (Investigation) check against your spell save DC.");
    add(1, "Dissonant Whispers", ENC, A, "60 ft.", "V", INST, "Brd",
            "A creature makes a WIS save. On a failure it takes 3d6 Psychic damage and must use its Reaction to move as far from you as it can; on a success it takes half damage only.")
            .higher("Damage increases by 1d6 for each slot level above 1.");
    add(1, "Divine Favor", TRA, BA, "Self", "V, S", "1 minute", "Pal",
            "Until the spell ends, your attacks with weapons deal an extra 1d4 Radiant damage on a hit.");
    add(1, "Divine Smite", EVO, BA, "Self", "V", INST, "Pal",
            "Cast immediately after hitting a creature with a Melee weapon or Unarmed Strike: the target takes an extra 2d8 Radiant damage, plus 1d8 more if it's a Fiend or Undead.")
            .higher("Damage increases by 1d8 for each slot level above 1.");
    add(1, "Ensnaring Strike", CON, BA, "Self", "V", "C 1 minute", "Rgr",
            "Cast after hitting with a weapon: the target must succeed on a STR save or have the Restrained condition, taking 1d6 Piercing damage at the start of each of its turns. It can repeat the check as an action.")
            .higher("Damage increases by 1d6 for each slot level above 1.");
    add(1, "Entangle", CON, A, "90 ft.", "V, S", "C 1 minute", "Dru Rgr",
            "Grasping plants fill a 20-ft. square, which becomes Difficult Terrain. Each creature there (other than you) must succeed on a STR save or have the Restrained condition.");
    add(1, "Expeditious Retreat", TRA, BA, "Self", "V, S", "C 10 minutes", "Art Sor Wlk Wiz",
            "You take the Dash action, and until the spell ends you can take that action again as a Bonus Action.");
    add(1, "Faerie Fire", EVO, A, "60 ft.", "V", "C 1 minute", "Art Brd Dru",
            "Objects and creatures in a 20-ft. Cube are outlined in light if they fail a DEX save. Attacks against them have Advantage, and they can't benefit from the Invisible condition.");
    add(1, "False Life", NEC, A, "Self", "V, S, M (a drop of alcohol)", INST, "Art Sor Wiz",
            "You gain 2d4 + 4 Temporary Hit Points.")
            .higher("You gain 5 additional Temporary Hit Points for each slot level above 1.");
    add(1, "Feather Fall", TRA, R, "60 ft.", "V, M (a small feather or piece of down)", "1 minute", "Art Brd Sor Wiz",
            "Cast when you or a creature within 60 ft. falls. Up to five falling creatures descend at 60 ft. per round and take no falling damage.");
    add(1, "Find Familiar", CON, "1 hour", "10 ft.", "V, S, M (burning incense worth 10+ GP)", INST, "Wiz",
            "Gain the service of a familiar, a spirit that takes an animal form you choose. You can communicate telepathically, see through its senses, and cast touch spells through it.").ritual();
    add(1, "Fog Cloud", CON, A, "120 ft.", "V, S", "C 1 hour", "Dru Rgr Sor Wiz",
            "Create a 20-ft.-radius Sphere of fog. Its area is Heavily Obscured.")
            .higher("The radius increases by 20 ft. for each slot level above 1.");
    add(1, "Goodberry", CON, BA, "Self", "V, S, M (a sprig of mistletoe)", "24 hours", "Dru Rgr",
            "Ten magic berries appear in your hand. Eating one as a Bonus Action restores 1 Hit Point and provides a day's nourishment.");
    add(1, "Grease", CON, A, "60 ft.", "V, S, M (a bit of pork rind or butter)", "1 minute", "Art Sor Wiz",
            "Grease covers a 10-ft. square, turning it into Difficult Terrain. Each creature there must succeed on a DEX save or have the Prone condition.");
    add(1, "Guiding Bolt", EVO, A, "120 ft.", "V, S", "1 round", "Clr",
            "Make a ranged spell attack. On a hit the target takes 4d6 Radiant damage, and the next attack roll against it before the end of your next turn has Advantage.")
            .higher("Damage increases by 1d6 for each slot level above 1.");
    add(1, "Hail of Thorns", CON, BA, "Self", "V, M (a withered vine)", INST, "Rgr",
            "Cast after hitting with a Ranged weapon: the target and each creature within 5 ft. of it make a DEX save, taking 1d10 Piercing damage on a failure or half on a success.")
            .higher("Damage increases by 1d10 for each slot level above 1.");
    add(1, "Healing Word", ABJ, BA, "60 ft.", "V", INST, "Brd Clr Dru",
            "A creature you can see regains Hit Points equal to 2d4 plus your spellcasting ability modifier.")
            .higher("Healing increases by 2d4 for each slot level above 1.");
    add(1, "Hellish Rebuke", EVO, R, "60 ft.", "V, S", INST, "Wlk",
            "Cast when a creature damages you. It makes a DEX save, taking 2d10 Fire damage on a failure or half on a success.")
            .higher("Damage increases by 1d10 for each slot level above 1.");
    add(1, "Heroism", ENC, A, "Touch", "V, S", "C 1 minute", "Brd Pal",
            "A willing creature is immune to the Frightened condition and gains Temporary Hit Points equal to your spellcasting modifier at the start of each of its turns.")
            .higher("You can target one additional creature for each slot level above 1.");
    add(1, "Hex", ENC, BA, "90 ft.", "V, S, M (the petrified eye of a newt)", "C 1 hour", "Wlk",
            "Curse a creature. Your attacks deal an extra 1d6 Necrotic damage to it, and it has Disadvantage on ability checks with one ability you choose. Move the curse with a Bonus Action if it drops to 0 HP.")
            .higher("Duration extends at higher slot levels (up to 24 hours).");
    add(1, "Hunter's Mark", DIV, BA, "90 ft.", "V", "C 1 hour", "Rgr",
            "Mark a creature as your quarry. Your attacks deal an extra 1d6 Force damage to it, and you have Advantage on WIS (Perception or Survival) checks to find it.")
            .higher("Duration extends at higher slot levels (up to 24 hours).");
    add(1, "Ice Knife", CON, A, "60 ft.", "S, M (a drop of water or a piece of ice)", INST, "Dru Sor Wiz",
            "Make a ranged spell attack; on a hit the target takes 1d10 Piercing damage. Hit or miss, the shard explodes: the target and creatures within 5 ft. must succeed on a DEX save or take 2d6 Cold damage.")
            .higher("Cold damage increases by 1d6 for each slot level above 1.");
    add(1, "Identify", DIV, "1 minute", "Touch", "V, S, M (a pearl worth 100+ GP)", INST, "Art Brd Wiz",
            "Learn the properties of a magic item you touch, including how to use it and any spells affecting it.").ritual();
    add(1, "Illusory Script", ILL, "1 minute", "Touch", "S, M (ink worth 10+ GP, consumed)", "10 days", "Brd Wlk Wiz",
            "Write a message that appears as gibberish (or a different message) to everyone but the creatures you designate.").ritual();
    add(1, "Inflict Wounds", NEC, A, "Touch", "V, S", INST, "Clr",
            "A creature you touch makes a CON save, taking 2d10 Necrotic damage on a failure or half on a success.")
            .higher("Damage increases by 1d10 for each slot level above 1.");
    add(1, "Jump", TRA, BA, "Touch", "V, S, M (a grasshopper's hind leg)", "1 minute", "Art Dru Rgr Sor Wiz",
            "Once on each of its turns, a willing creature can jump up to 30 ft. by spending 10 ft. of movement.");
    add(1, "Longstrider", TRA, A, "Touch", "V, S, M (a pinch of dirt)", "1 hour", "Art Brd Dru Rgr Wiz",
            "A creature's Speed increases by 10 ft. for the duration.")
            .higher("You can target one additional creature for each slot level above 1.");
    add(1, "Mage Armor", ABJ, A, "Touch", "V, S, M (a piece of cured leather)", "8 hours", "Sor Wiz",
            "A willing creature not wearing armor has a base AC of 13 plus its DEX modifier for the duration.");
    add(1, "Magic Missile", EVO, A, "120 ft.", "V, S", INST, "Sor Wiz",
            "Create three glowing darts that automatically hit; each deals 1d4 + 1 Force damage to a target you choose.")
            .higher("Creates one more dart for each slot level above 1.");
    add(1, "Protection from Evil and Good", ABJ, A, "Touch", "V, S, M (a flask of Holy Water worth 25+ GP, consumed)", "C 10 minutes", "Clr Dru Pal Wlk Wiz",
            "Aberrations, Celestials, Elementals, Fey, Fiends, and Undead have Disadvantage on attacks against the target, and it can't be Charmed, Frightened, or possessed by them.");
    add(1, "Purify Food and Drink", TRA, A, "10 ft.", "V, S", INST, "Art Clr Dru Pal",
            "Remove poison and rot from nonmagical food and drink in a 5-ft.-radius Sphere.").ritual();
    add(1, "Ray of Sickness", NEC, A, "60 ft.", "V, S", INST, "Sor Wiz",
            "Make a ranged spell attack. On a hit the target takes 2d8 Poison damage and has the Poisoned condition until the end of your next turn.")
            .higher("Damage increases by 1d8 for each slot level above 1.");
    add(1, "Sanctuary", ABJ, BA, "30 ft.", "V, S, M (a shard of glass from a mirror)", "1 minute", "Art Clr",
            "Ward a creature. Anyone who targets it with an attack or harmful spell must first succeed on a WIS save or choose a new target. The ward ends if the warded creature attacks or casts a harmful spell.");
    add(1, "Searing Smite", EVO, BA, "Self", "V", "1 minute", "Pal",
            "Cast after hitting with a Melee weapon: the target takes an extra 1d6 Fire damage and burns, taking 1d6 Fire damage at the start of each of its turns until it succeeds on a CON save.")
            .higher("Both damage rolls increase by 1d6 for each slot level above 1.");
    add(1, "Shield", ABJ, R, "Self", "V, S", "1 round", "Sor Wiz",
            "Cast when you're hit by an attack or targeted by Magic Missile: you gain +5 AC until the start of your next turn (including against the triggering attack) and take no damage from Magic Missile.");
    add(1, "Shield of Faith", ABJ, BA, "60 ft.", "V, S, M (a prayer scroll)", "C 10 minutes", "Clr Pal",
            "A shimmering field surrounds a creature, granting it +2 AC for the duration.");
    add(1, "Silent Image", ILL, A, "60 ft.", "V, S, M (a bit of fleece)", "C 10 minutes", "Brd Sor Wiz",
            "Create a visual illusion no larger than a 15-ft. Cube. You can move it with a Magic action. A creature can see through it with an INT (Investigation) check.");
    add(1, "Sleep", ENC, A, "60 ft.", "V, S, M (a pinch of sand or rose petals)", "C 1 minute", "Brd Sor Wiz",
            "Each creature of your choice in a 5-ft.-radius Sphere must succeed on a WIS save or have the Incapacitated condition until the end of its next turn, then repeat the save or have the Unconscious condition for the duration.");
    add(1, "Speak with Animals", DIV, A, "Self", "V, S", "10 minutes", "Brd Dru Rgr Wlk",
            "You can comprehend and verbally communicate with Beasts for the duration.").ritual();
    add(1, "Tasha's Hideous Laughter", ENC, A, "30 ft.", "V, S, M (a tart and a feather)", "C 1 minute", "Brd Wlk Wiz",
            "A creature must succeed on a WIS save or have the Prone and Incapacitated conditions as it's overcome by laughter. It repeats the save at the end of each of its turns and when it takes damage.")
            .higher("You can target one additional creature for each slot level above 1.");
    add(1, "Tenser's Floating Disk", CON, A, "30 ft.", "V, S, M (a drop of mercury)", "1 hour", "Wiz",
            "Create a 3-ft.-diameter floating disk of force that holds up to 500 lb. and follows you.").ritual();
    add(1, "Thunderous Smite", EVO, BA, "Self", "V", INST, "Pal",
            "Cast after hitting with a Melee weapon: the target takes an extra 2d6 Thunder damage and must succeed on a STR save or be pushed 10 ft. and have the Prone condition.")
            .higher("Damage increases by 1d6 for each slot level above 1.");
    add(1, "Thunderwave", EVO, A, "Self", "V, S", INST, "Brd Dru Sor Wiz",
            "A wave of thunderous force sweeps out in a 15-ft. Cube. Each creature there makes a CON save, taking 2d8 Thunder damage and being pushed 10 ft. on a failure, or half damage on a success.")
            .higher("Damage increases by 1d8 for each slot level above 1.");
    add(1, "Unseen Servant", CON, A, "60 ft.", "V, S, M (a bit of string and of wood)", "1 hour", "Brd Wlk Wiz",
            "Create an invisible, mindless force that performs simple tasks at your command.").ritual();
    add(1, "Witch Bolt", EVO, A, "60 ft.", "V, S, M (a twig struck by lightning)", "C 1 minute", "Sor Wlk Wiz",
            "Make a ranged spell attack; on a hit the target takes 2d12 Lightning damage. On later turns you can deal 1d12 Lightning damage to it automatically as a Bonus Action.")
            .higher("The initial damage increases by 1d12 for each slot level above 1.");
    add(1, "Wrathful Smite", NEC, BA, "Self", "V", "1 minute", "Pal",
            "Cast after hitting with a Melee weapon: the target takes an extra 1d6 Necrotic damage and must succeed on a WIS save or have the Frightened condition for the duration.")
            .higher("Damage increases by 1d6 for each slot level above 1.");
}

// ---------------------------------------------------------------
// Level 2
// ---------------------------------------------------------------
static {
    add(2, "Aid", ABJ, A, "30 ft.", "V, S, M (a strip of white cloth)", "8 hours", "Art Brd Clr Dru Pal Rgr",
            "Up to three creatures each increase their Hit Point maximum and current Hit Points by 5 for the duration.")
            .higher("Hit Points increase by 5 more for each slot level above 2.");
    add(2, "Alter Self", TRA, A, "Self", "V, S", "C 1 hour", "Sor Wiz",
            "Alter your form: adapt to water (swim speed, breathe underwater), change your appearance, or grow natural weapons.");
    add(2, "Arcane Lock", ABJ, A, "Touch", "V, S, M (gold dust worth 25+ GP, consumed)", "Until dispelled", "Art Wiz",
            "Magically lock a door, window, chest, or other entryway. You and creatures you designate can open it normally.");
    add(2, "Augury", DIV, "1 minute", "Self", "V, S, M (specially marked sticks worth 25+ GP)", INST, "Clr Dru Wiz",
            "Receive an omen about the results of a course of action you plan within the next 30 minutes: Weal, Woe, Weal and woe, or Indifference.").ritual();
    add(2, "Barkskin", TRA, BA, "Touch", "V, S, M (a handful of bark)", "1 hour", "Dru Rgr",
            "A willing creature's skin becomes bark-like; its AC can't be less than 17 for the duration.");
    add(2, "Blindness/Deafness", TRA, A, "120 ft.", "V", "1 minute", "Brd Clr Sor Wiz",
            "A creature must succeed on a CON save or have the Blinded or Deafened condition (your choice). It repeats the save at the end of each of its turns.")
            .higher("You can target one additional creature for each slot level above 2.");
    add(2, "Blur", ILL, A, "Self", "V", "C 1 minute", "Art Sor Wiz",
            "Your body becomes blurred. Creatures have Disadvantage on attack rolls against you unless they perceive you with Blindsight or Truesight.");
    add(2, "Calm Emotions", ENC, A, "60 ft.", "V, S", "C 1 minute", "Brd Clr",
            "Humanoids in a 20-ft.-radius Sphere that fail a CHA save are either freed from the Charmed and Frightened conditions or made indifferent toward chosen creatures.");
    add(2, "Cloud of Daggers", CON, A, "60 ft.", "V, S, M (a sliver of glass)", "C 1 minute", "Brd Sor Wlk Wiz",
            "Spinning daggers fill a 5-ft. Cube. A creature takes 4d4 Slashing damage when it enters the area or ends its turn there.")
            .higher("Damage increases by 2d4 for each slot level above 2.");
    add(2, "Darkness", EVO, A, "60 ft.", "V, M (bat fur and a piece of coal)", "C 10 minutes", "Sor Wlk Wiz",
            "Magical Darkness fills a 15-ft.-radius Sphere. Darkvision can't see through it and nonmagical light can't illuminate it.");
    add(2, "Darkvision", TRA, A, "Touch", "V, S, M (a dried carrot)", "8 hours", "Art Dru Rgr Sor Wiz",
            "A willing creature gains Darkvision with a range of 150 ft.");
    add(2, "Detect Thoughts", DIV, A, "Self", "V, S, M (1 Copper Piece)", "C 1 minute", "Brd Sor Wlk Wiz",
            "Sense creatures within 30 ft. that have minds, and read the surface thoughts of one. You can probe deeper; it makes a WIS save to resist.");
    add(2, "Enhance Ability", TRA, A, "Touch", "V, S, M (fur or a feather)", "C 1 hour", "Art Brd Clr Dru Rgr Sor Wiz",
            "Choose an ability. A willing creature has Advantage on ability checks using that ability for the duration.")
            .higher("You can target one additional creature for each slot level above 2.");
    add(2, "Enlarge/Reduce", TRA, A, "30 ft.", "V, S, M (a pinch of powdered iron)", "C 1 minute", "Art Brd Dru Sor Wiz",
            "A creature or object doubles in size (Advantage on STR checks and saves, +1d4 weapon damage) or shrinks to half size (Disadvantage on STR checks and saves, -1d4 weapon damage). Unwilling targets make a CON save.");
    add(2, "Find Steed", CON, A, "30 ft.", "V, S", INST, "Pal",
            "Summon an otherworldly steed that serves as your mount. It shares your initiative and acts on your command.");
    add(2, "Flaming Sphere", CON, A, "60 ft.", "V, S, M (a ball of wax)", "C 1 minute", "Dru Sor Wiz",
            "Create a 5-ft. ball of fire. Creatures ending their turn within 5 ft. make a DEX save, taking 2d6 Fire damage on a failure or half on a success. Ram it into creatures as a Bonus Action.")
            .higher("Damage increases by 1d6 for each slot level above 2.");
    add(2, "Gust of Wind", EVO, A, "Self", "V, S, M (a legume seed)", "C 1 minute", "Dru Rgr Sor Wiz",
            "A 60-ft.-long, 10-ft.-wide Line of strong wind blasts from you. Each creature in it must succeed on a STR save or be pushed 15 ft. away.");
    add(2, "Heat Metal", TRA, BA, "60 ft.", "V, S, M (a piece of iron and a flame)", "C 1 minute", "Art Brd Dru",
            "Make a metal object glow red-hot. A creature touching it takes 2d8 Fire damage, and has Disadvantage on attacks and checks unless it drops the object.")
            .higher("Damage increases by 1d8 for each slot level above 2.");
    add(2, "Hold Person", ENC, A, "60 ft.", "V, S, M (a straight piece of iron)", "C 1 minute", "Brd Clr Dru Sor Wlk Wiz",
            "A Humanoid must succeed on a WIS save or have the Paralyzed condition. It repeats the save at the end of each of its turns.")
            .higher("You can target one additional Humanoid for each slot level above 2.");
    add(2, "Invisibility", ILL, A, "Touch", "V, S, M (an eyelash in gum arabic)", "C 1 hour", "Art Brd Sor Wlk Wiz",
            "A creature you touch has the Invisible condition until the spell ends, or until it attacks, deals damage, or casts a spell.")
            .higher("You can target one additional creature for each slot level above 2.");
    add(2, "Knock", TRA, A, "60 ft.", "V", INST, "Brd Sor Wiz",
            "Unlock a locked or barred object, or suppress an Arcane Lock for 10 minutes. A loud knock is audible up to 300 ft. away.");
    add(2, "Lesser Restoration", ABJ, BA, "Touch", "V, S", INST, "Art Brd Clr Dru Pal Rgr",
            "End one condition on a creature you touch: Blinded, Deafened, Paralyzed, or Poisoned.");
    add(2, "Levitate", TRA, A, "60 ft.", "V, S, M (a metal spring)", "C 10 minutes", "Art Sor Wiz",
            "A creature or object of up to 500 lb. rises up to 20 ft. and hangs in the air. Unwilling targets make a CON save.");
    add(2, "Locate Object", DIV, A, "Self", "V, S, M (a forked twig)", "C 10 minutes", "Brd Clr Dru Pal Rgr Wiz",
            "Sense the direction of a familiar object (or the nearest object of a kind) within 1,000 ft.");
    add(2, "Magic Weapon", TRA, BA, "Touch", "V, S", "1 hour", "Art Pal Rgr Sor Wiz",
            "A nonmagical weapon becomes a magic weapon with a +1 bonus to attack and damage rolls.")
            .higher("The bonus becomes +2 with a level 4 slot and +3 with a level 6+ slot.");
    add(2, "Mirror Image", ILL, A, "Self", "V, S", "1 minute", "Brd Sor Wlk Wiz",
            "Three illusory duplicates of you appear. When a creature hits you, roll a d6 for each duplicate; on a 3+, a duplicate is hit and destroyed instead.");
    add(2, "Misty Step", CON, BA, "Self", "V", INST, "Sor Wlk Wiz",
            "Briefly surrounded by silvery mist, you teleport up to 30 ft. to an unoccupied space you can see.");
    add(2, "Moonbeam", EVO, A, "120 ft.", "V, S, M (a moonseed leaf)", "C 1 minute", "Dru",
            "A 5-ft.-radius Cylinder of moonlight. Creatures entering or starting there make a CON save, taking 2d10 Radiant damage on a failure or half on a success; shapechangers revert.")
            .higher("Damage increases by 1d10 for each slot level above 2.");
    add(2, "Pass without Trace", ABJ, A, "Self", "V, S, M (ashes from burned mistletoe)", "C 1 hour", "Dru Rgr",
            "You radiate a 30-ft. Emanation of concealment. You and your allies within it have a +10 bonus to DEX (Stealth) checks and leave no tracks.");
    add(2, "Phantasmal Force", ILL, A, "60 ft.", "V, S, M (a bit of fleece)", "C 1 minute", "Brd Sor Wlk Wiz",
            "A creature that fails an INT save perceives a phantasm you create. It can take 2d8 Psychic damage each round from the illusion.");
    add(2, "Prayer of Healing", ABJ, "10 minutes", "30 ft.", "V", INST, "Clr Pal",
            "Up to five creatures gain the benefits of a Short Rest and regain 2d8 Hit Points. A creature can't be affected again until it finishes a Long Rest.")
            .higher("Healing increases by 1d8 for each slot level above 2.");
    add(2, "Protection from Poison", ABJ, A, "Touch", "V, S", "1 hour", "Art Clr Dru Pal Rgr",
            "End the Poisoned condition on a creature. For the duration, it has Advantage on saves to avoid or end the Poisoned condition and Resistance to Poison damage.");
    add(2, "Ray of Enfeeblement", NEC, A, "60 ft.", "V, S", "C 1 minute", "Wlk Wiz",
            "A creature must succeed on a CON save or have Disadvantage on STR-based D20 Tests and deal 1d8 less damage with attacks.");
    add(2, "Scorching Ray", EVO, A, "120 ft.", "V, S", INST, "Sor Wiz",
            "Hurl three fiery rays. Make a ranged spell attack for each; on a hit a ray deals 2d6 Fire damage.")
            .higher("Creates one more ray for each slot level above 2.");
    add(2, "See Invisibility", DIV, A, "Self", "V, S, M (a pinch of talc)", "1 hour", "Art Brd Sor Wlk Wiz",
            "You see creatures and objects that have the Invisible condition as well as into the Ethereal Plane.");
    add(2, "Shatter", EVO, A, "60 ft.", "V, S, M (a chip of mica)", INST, "Brd Sor Wlk Wiz",
            "A painfully loud noise erupts in a 10-ft.-radius Sphere. Each creature there makes a CON save, taking 3d8 Thunder damage on a failure or half on a success.")
            .higher("Damage increases by 1d8 for each slot level above 2.");
    add(2, "Shining Smite", TRA, BA, "Self", "V", "C 1 minute", "Pal",
            "Cast after hitting with a Melee weapon: the target takes an extra 2d6 Radiant damage, sheds light, can't benefit from the Invisible condition, and attacks against it have Advantage.")
            .higher("Damage increases by 1d6 for each slot level above 2.");
    add(2, "Silence", ILL, A, "120 ft.", "V, S", "C 10 minutes", "Brd Clr Rgr",
            "No sound can be created within or pass through a 20-ft.-radius Sphere. Creatures inside have the Deafened condition and can't cast spells with Verbal components.").ritual();
    add(2, "Spider Climb", TRA, A, "Touch", "V, S, M (a drop of bitumen and a spider)", "C 1 hour", "Art Sor Wlk Wiz",
            "A willing creature can move up, down, and across vertical surfaces and ceilings while leaving its hands free, and gains a Climb Speed equal to its Speed.");
    add(2, "Spike Growth", TRA, A, "150 ft.", "V, S, M (seven thorns)", "C 10 minutes", "Dru Rgr",
            "The ground in a 20-ft.-radius Sphere sprouts hidden spikes. It's Difficult Terrain, and a creature takes 2d4 Piercing damage for every 5 ft. it moves through it.");
    add(2, "Spiritual Weapon", EVO, BA, "60 ft.", "V, S", "C 1 minute", "Clr",
            "Create a floating spectral weapon. Make a melee spell attack against a creature within 5 ft. of it, dealing 1d8 + your spellcasting modifier Force damage. Move and attack again as a Bonus Action.")
            .higher("Damage increases by 1d8 for each slot level above 2.");
    add(2, "Suggestion", ENC, A, "30 ft.", "V, M (a drop of honey)", "C 8 hours", "Brd Sor Wlk Wiz",
            "Suggest a reasonable course of activity to a creature. It must succeed on a WIS save or have the Charmed condition and pursue the suggestion.");
    add(2, "Warding Bond", ABJ, A, "Touch", "V, S, M (a pair of platinum rings worth 50+ GP each)", "1 hour", "Clr Pal",
            "A willing creature gains +1 AC, +1 to saving throws, and Resistance to all damage, but you take the same damage it does.");
    add(2, "Web", CON, A, "60 ft.", "V, S, M (a bit of spiderweb)", "C 1 hour", "Art Sor Wiz",
            "Sticky webs fill a 20-ft. Cube, which is Difficult Terrain and Lightly Obscured. Creatures there must succeed on a DEX save or have the Restrained condition.");
    add(2, "Zone of Truth", ENC, A, "60 ft.", "V, S", "10 minutes", "Brd Clr Pal",
            "Create a 15-ft.-radius zone. A creature that fails a CHA save there can't speak a deliberate lie while inside it.");
}

// ---------------------------------------------------------------
// Level 3
// ---------------------------------------------------------------
static {
    add(3, "Animate Dead", NEC, "1 minute", "10 ft.", "V, S, M (a drop of blood, a piece of flesh, and a pinch of bone dust)", INST, "Clr Wiz",
            "Turn a pile of bones or a corpse into an undead Skeleton or Zombie under your control for 24 hours.")
            .higher("Animate or reassert control over two more undead for each slot level above 3.");
    add(3, "Aura of Vitality", ABJ, A, "Self", "V", "C 1 minute", "Clr Dru Pal",
            "Healing energy radiates from you in a 30-ft. Emanation. As a Bonus Action, a creature in it regains 2d6 Hit Points.");
    add(3, "Beacon of Hope", ABJ, A, "30 ft.", "V, S", "C 1 minute", "Clr",
            "Any number of creatures have Advantage on WIS saves and Death Saving Throws, and regain the maximum possible Hit Points from healing.");
    add(3, "Bestow Curse", NEC, A, "Touch", "V, S", "C 1 minute", "Brd Clr Wiz",
            "A creature must succeed on a WIS save or be cursed: Disadvantage on checks and saves with one ability, Disadvantage on attacks against you, lost actions, or extra Necrotic damage from you.")
            .higher("Longer durations at higher levels; with a level 5+ slot no Concentration is required.");
    add(3, "Blinding Smite", EVO, BA, "Self", "V", "C 1 minute", "Pal",
            "Cast after hitting with a Melee weapon: the target takes an extra 3d8 Radiant damage and has the Blinded condition until the spell ends. It repeats a CON save at the end of each of its turns.")
            .higher("Damage increases by 1d8 for each slot level above 3.");
    add(3, "Call Lightning", CON, A, "120 ft.", "V, S", "C 10 minutes", "Dru",
            "A storm cloud appears. Call down a bolt on a point below it: each creature within 5 ft. makes a DEX save, taking 3d10 Lightning damage on a failure or half on a success. Repeat with a Magic action.")
            .higher("Damage increases by 1d10 for each slot level above 3.");
    add(3, "Clairvoyance", DIV, "10 minutes", "1 mile", "V, S, M (a focus worth 100+ GP)", "C 10 minutes", "Brd Clr Sor Wiz",
            "Create an invisible sensor at a location you know within range, through which you can see or hear.");
    add(3, "Conjure Animals", CON, A, "60 ft.", "V, S", "C 10 minutes", "Dru Rgr",
            "Summon a pack of spectral animals in a 10-ft. Cube. Creatures that come within 10 ft. of the pack make a DEX save or take 3d10 Slashing damage.")
            .higher("Damage increases by 1d10 for each slot level above 3.");
    add(3, "Conjure Barrage", CON, A, "Self", "V, S, M (a Melee or Ranged weapon worth 1+ CP)", INST, "Rgr",
            "Brandish your weapon to unleash a 60-ft. Cone of spectral copies. Each creature there makes a DEX save, taking 5d8 Force damage on a failure or half on a success.")
            .higher("Damage increases by 1d8 for each slot level above 3.");
    add(3, "Counterspell", ABJ, R, "60 ft.", "S", INST, "Sor Wlk Wiz",
            "Cast when you see a creature casting a spell. It makes a CON save; on a failure the spell dissipates with no effect and the slot isn't expended.");
    add(3, "Crusader's Mantle", EVO, A, "Self", "V", "C 1 minute", "Pal",
            "Holy power radiates in a 30-ft. Emanation. You and allies in it deal an extra 1d4 Radiant damage with weapon and Unarmed Strike hits.");
    add(3, "Daylight", EVO, A, "60 ft.", "V, S", "1 hour", "Clr Dru Pal Rgr Sor",
            "A 60-ft.-radius Sphere of Bright Light spreads from a point, with Dim Light for another 60 ft. It dispels magical Darkness of level 3 or lower.");
    add(3, "Dispel Magic", ABJ, A, "120 ft.", "V, S", INST, "Art Brd Clr Dru Pal Sor Wlk Wiz",
            "End spells of level 3 or lower on a creature, object, or effect. For each higher-level spell, make an ability check (DC 10 + its level).")
            .higher("Automatically end spells of a level equal to or less than the slot used.");
    add(3, "Elemental Weapon", TRA, A, "Touch", "V, S", "C 1 hour", "Art Dru Pal Rgr",
            "A nonmagical weapon becomes magical: +1 to attack rolls and an extra 1d4 Acid, Cold, Fire, Lightning, or Thunder damage on a hit.")
            .higher("Bonus and damage increase with level 5 and level 7 slots.");
    add(3, "Fear", ILL, A, "Self", "V, S, M (a white feather)", "C 1 minute", "Brd Sor Wlk Wiz",
            "Each creature in a 30-ft. Cone must succeed on a WIS save or drop what it's holding and have the Frightened condition, Dashing away from you on its turns.");
    add(3, "Fireball", EVO, A, "150 ft.", "V, S, M (a ball of bat guano and sulfur)", INST, "Sor Wiz",
            "A bright streak blossoms into a 20-ft.-radius Sphere of flame. Each creature there makes a DEX save, taking 8d6 Fire damage on a failure or half on a success.")
            .higher("Damage increases by 1d6 for each slot level above 3.");
    add(3, "Fly", TRA, A, "Touch", "V, S, M (a feather)", "C 10 minutes", "Art Sor Wlk Wiz",
            "A willing creature gains a Fly Speed of 60 ft. and can hover.")
            .higher("You can target one additional creature for each slot level above 3.");
    add(3, "Gaseous Form", TRA, A, "Touch", "V, S, M (a bit of gauze)", "C 1 hour", "Sor Wlk Wiz",
            "A willing creature becomes a misty cloud: Fly Speed 10 ft., Resistance to nonmagical damage, and Advantage on STR, DEX, and CON saves. It can't attack or cast spells.");
    add(3, "Glyph of Warding", ABJ, "1 hour", "Touch", "V, S, M (powdered diamond worth 200+ GP, consumed)", "Until dispelled or triggered", "Art Brd Clr Wiz",
            "Inscribe a glyph that triggers an explosive rune (5d8 damage, DEX save) or a stored spell when its trigger is met.")
            .higher("Explosive damage increases by 1d8 for each slot level above 3.");
    add(3, "Haste", TRA, A, "30 ft.", "V, S, M (a shaving of licorice root)", "C 1 minute", "Art Sor Wiz",
            "A willing creature's Speed doubles, it gains +2 AC and Advantage on DEX saves, and it gets an extra limited action each turn. When the spell ends it loses its next turn to lethargy.");
    add(3, "Hunger of Hadar", CON, A, "150 ft.", "V, S, M (a pickled tentacle)", "C 1 minute", "Wlk",
            "Open a 20-ft.-radius Sphere of void. Creatures inside have the Blinded condition, take 2d6 Cold damage at the start of their turns, and take 2d6 Acid damage if they end a turn there (DEX save).")
            .higher("Cold or Acid damage increases by 1d6 for each slot level above 3.");
    add(3, "Hypnotic Pattern", ILL, A, "120 ft.", "S, M (a pinch of confetti)", "C 1 minute", "Brd Sor Wlk Wiz",
            "A twisting pattern of colors fills a 30-ft. Cube. Each creature that sees it must succeed on a WIS save or have the Charmed condition; while charmed it is Incapacitated and has a Speed of 0.");
    add(3, "Lightning Arrow", TRA, BA, "Self", "V, S, M (a Ranged weapon)", INST, "Rgr",
            "Your next ranged weapon attack becomes a bolt of lightning: 4d8 Lightning damage to the target (half on a miss), and creatures within 10 ft. make a DEX save or take 2d8 Lightning damage.")
            .higher("Both damage rolls increase by 1d8 for each slot level above 3.");
    add(3, "Lightning Bolt", EVO, A, "Self", "V, S, M (a bit of fur and a crystal rod)", INST, "Sor Wiz",
            "A stroke of lightning forms a 100-ft.-long, 5-ft.-wide Line. Each creature in it makes a DEX save, taking 8d6 Lightning damage on a failure or half on a success.")
            .higher("Damage increases by 1d6 for each slot level above 3.");
    add(3, "Magic Circle", ABJ, "1 minute", "10 ft.", "V, S, M (salt and powdered silver worth 100+ GP, consumed)", "1 hour", "Clr Pal Wlk Wiz",
            "Create a 10-ft.-radius Cylinder that Celestials, Elementals, Fey, Fiends, or Undead can't willingly enter, and inside which they can't Charm or Frighten others.")
            .higher("Duration increases by 1 hour for each slot level above 3.");
    add(3, "Major Image", ILL, A, "120 ft.", "V, S, M (a bit of fleece)", "C 10 minutes", "Brd Sor Wlk Wiz",
            "Create an illusion of an object, creature, or phenomenon no larger than a 20-ft. Cube, complete with sound, smell, and temperature.")
            .higher("With a level 4+ slot, the spell lasts until dispelled without Concentration.");
    add(3, "Mass Healing Word", ABJ, BA, "60 ft.", "V", INST, "Brd Clr",
            "Up to six creatures you can see regain Hit Points equal to 2d4 plus your spellcasting ability modifier.")
            .higher("Healing increases by 1d4 for each slot level above 3.");
    add(3, "Plant Growth", TRA, A, "150 ft.", "V, S", INST, "Brd Dru Rgr",
            "Overgrowth: plants in a 100-ft. radius become thick, costing 4 ft. of movement per foot. Or, over 8 hours, enrich land within half a mile for a year.");
    add(3, "Protection from Energy", ABJ, A, "Touch", "V, S", "C 1 hour", "Art Clr Dru Rgr Sor Wiz",
            "A willing creature has Resistance to one damage type you choose: Acid, Cold, Fire, Lightning, or Thunder.");
    add(3, "Remove Curse", ABJ, A, "Touch", "V, S", INST, "Clr Pal Wlk Wiz",
            "All curses affecting one creature or object end. A cursed magic item's curse remains, but the attunement breaks.");
    add(3, "Revivify", NEC, A, "Touch", "V, S, M (a diamond worth 300+ GP, consumed)", INST, "Art Clr Dru Pal Rgr",
            "A creature that died within the last minute returns to life with 1 Hit Point.");
    add(3, "Sending", DIV, A, "Unlimited", "V, S, M (a copper wire)", INST, "Brd Clr Wiz",
            "Send a message of 25 words or fewer to a creature you're familiar with, anywhere on the same plane. It can reply immediately.");
    add(3, "Sleet Storm", CON, A, "150 ft.", "V, S, M (a miniature umbrella)", "C 1 minute", "Dru Sor Wiz",
            "Freezing rain and sleet fill a 40-ft.-radius Cylinder. It's Heavily Obscured and Difficult Terrain; creatures there must succeed on a DEX save or have the Prone condition, and casters must save to maintain Concentration.");
    add(3, "Slow", TRA, A, "120 ft.", "V, S, M (a drop of molasses)", "C 1 minute", "Brd Sor Wiz",
            "Up to six creatures in a 40-ft. Cube that fail a WIS save have half Speed, -2 AC and DEX saves, no Reactions, and can take only an action or a Bonus Action on their turn.");
    add(3, "Speak with Dead", NEC, A, "10 ft.", "V, S, M (burning incense)", "10 minutes", "Brd Clr Wiz",
            "Grant a corpse the semblance of life so it can answer up to five questions using what it knew in life.");
    add(3, "Spirit Guardians", CON, A, "Self", "V, S, M (a prayer scroll)", "C 10 minutes", "Clr",
            "Protective spirits flit around you in a 15-ft. Emanation. Enemies there have half Speed, and when one enters or ends its turn there it makes a WIS save, taking 3d8 Radiant or Necrotic damage on a failure or half on a success.")
            .higher("Damage increases by 1d8 for each slot level above 3.");
    add(3, "Stinking Cloud", CON, A, "90 ft.", "V, S, M (a rotten egg)", "C 1 minute", "Brd Sor Wiz",
            "A 20-ft.-radius Sphere of nauseating gas. It's Heavily Obscured, and creatures that start their turn there must succeed on a CON save or have the Poisoned condition and lose their action and Bonus Action.");
    add(3, "Tongues", DIV, A, "Touch", "V, M (a miniature ziggurat)", "1 hour", "Brd Clr Sor Wlk Wiz",
            "A creature understands any spoken or signed language it hears, and its speech is understood by any creature that knows a language.");
    add(3, "Vampiric Touch", NEC, A, "Self", "V, S", "C 1 minute", "Sor Wlk Wiz",
            "Make a melee spell attack. On a hit the target takes 3d6 Necrotic damage and you regain Hit Points equal to half the damage. Repeat as a Magic action.")
            .higher("Damage increases by 1d6 for each slot level above 3.");
    add(3, "Water Breathing", TRA, A, "30 ft.", "V, S, M (a short reed)", "24 hours", "Art Dru Rgr Sor Wiz",
            "Up to ten willing creatures can breathe underwater for the duration.").ritual();
    add(3, "Wind Wall", EVO, A, "120 ft.", "V, S, M (a fan and a feather)", "C 1 minute", "Dru Rgr",
            "A wall of strong wind rises from the ground. Creatures in it when it appears make a STR save, taking 4d8 Bludgeoning damage. Arrows and other small projectiles can't pass through.");
}

// ---------------------------------------------------------------
// Level 4
// ---------------------------------------------------------------
static {
    add(4, "Arcane Eye", DIV, A, "30 ft.", "V, S, M (a bit of bat fur)", "C 1 hour", "Art Wiz",
            "Create an Invisible, floating eye with Darkvision. You can see through it and move it up to 30 ft. each turn.");
    add(4, "Aura of Life", ABJ, A, "Self", "V", "C 10 minutes", "Clr Pal",
            "Life-preserving energy radiates in a 30-ft. Emanation. Allies there have Resistance to Necrotic damage, can't have their HP maximum reduced, and regain 1 HP if they start a turn at 0.");
    add(4, "Aura of Purity", ABJ, A, "Self", "V", "C 10 minutes", "Clr Pal",
            "Allies in a 30-ft. Emanation have Resistance to Poison damage and Advantage on saves to avoid or end the Blinded, Charmed, Deafened, Frightened, Paralyzed, Poisoned, and Stunned conditions.");
    add(4, "Banishment", ABJ, A, "30 ft.", "V, S, M (a pentacle)", "C 1 minute", "Clr Pal Sor Wlk Wiz",
            "A creature must succeed on a CHA save or be transported to a harmless demiplane, where it has the Incapacitated condition. If it's from another plane and the spell lasts 1 minute, it doesn't return.")
            .higher("You can target one additional creature for each slot level above 4.");
    add(4, "Blight", NEC, A, "30 ft.", "V, S", INST, "Dru Sor Wlk Wiz",
            "Necromantic energy washes over a creature. It makes a CON save, taking 8d8 Necrotic damage on a failure or half on a success. Plants have Disadvantage and take maximum damage.")
            .higher("Damage increases by 1d8 for each slot level above 4.");
    add(4, "Charm Monster", ENC, A, "30 ft.", "V, S", "1 hour", "Brd Dru Sor Wlk Wiz",
            "A creature must succeed on a WIS save (with Advantage if you're fighting it) or have the Charmed condition until the spell ends or you or your allies damage it.")
            .higher("You can target one additional creature for each slot level above 4.");
    add(4, "Compulsion", ENC, A, "30 ft.", "V, S", "C 1 minute", "Brd",
            "Creatures of your choice that fail a WIS save have the Charmed condition; as a Bonus Action you can direct them to move in a direction you choose.");
    add(4, "Confusion", ENC, A, "90 ft.", "V, S, M (three nut shells)", "C 1 minute", "Brd Dru Sor Wiz",
            "Each creature in a 10-ft.-radius Sphere must succeed on a WIS save or behave randomly: it can't take Reactions and rolls each turn to see what it does.")
            .higher("The Sphere's radius increases by 5 ft. for each slot level above 4.");
    add(4, "Conjure Woodland Beings", CON, A, "Self", "V, S", "C 10 minutes", "Dru Rgr",
            "Nature spirits flit around you in a 10-ft. Emanation. Creatures entering it or ending their turn there make a WIS save, taking 5d8 Force damage on a failure or half on a success. You can Disengage as a Bonus Action.")
            .higher("Damage increases by 1d8 for each slot level above 4.");
    add(4, "Death Ward", ABJ, A, "Touch", "V, S", "8 hours", "Clr Pal",
            "The first time the target would drop to 0 Hit Points, it drops to 1 instead; or the first instant-death effect against it is negated.");
    add(4, "Dimension Door", CON, A, "500 ft.", "V", INST, "Brd Sor Wlk Wiz",
            "Teleport to a spot within range that you can see, picture, or describe. You can bring one willing creature within 5 ft.");
    add(4, "Divination", DIV, A, "Self", "V, S, M (incense worth 25+ GP, consumed)", INST, "Clr Dru Wiz",
            "Ask one question about a goal, event, or activity within the next 7 days and receive a truthful reply.").ritual();
    add(4, "Dominate Beast", ENC, A, "60 ft.", "V, S", "C 1 minute", "Dru Rgr Sor",
            "A Beast must succeed on a WIS save or have the Charmed condition; you can issue it commands telepathically.")
            .higher("Duration increases with level 5 (10 min), 6 (1 hour), and 7+ (8 hours) slots.");
    add(4, "Evard's Black Tentacles", CON, A, "90 ft.", "V, S, M (a tentacle)", "C 1 minute", "Wiz",
            "Writhing tentacles fill a 20-ft. square of Difficult Terrain. Creatures that enter or start there must succeed on a STR save or take 3d6 Bludgeoning damage and have the Restrained condition.")
            .higher("Damage increases by 1d6 for each slot level above 4.");
    add(4, "Fire Shield", EVO, A, "Self", "V, S, M (a bit of phosphorus or a firefly)", "10 minutes", "Dru Sor Wiz",
            "Wispy flames grant Resistance to Cold (warm shield) or Fire (chill shield). Creatures that hit you with a melee attack take 2d8 Fire or Cold damage.");
    add(4, "Freedom of Movement", ABJ, A, "Touch", "V, S, M (a leather strap)", "1 hour", "Art Brd Clr Dru Rgr",
            "A willing creature isn't affected by Difficult Terrain, and spells can't reduce its Speed or cause the Paralyzed or Restrained conditions. It can escape a grapple with 5 ft. of movement.");
    add(4, "Grasping Vine", CON, BA, "30 ft.", "V, S", "C 1 minute", "Dru Rgr",
            "A vine sprouts and lashes at a creature within 30 ft. of it. The target must succeed on a DEX save or take 4d8 Bludgeoning damage, be pulled 30 ft., and have the Grappled condition.")
            .higher("You can make one additional vine attack for each slot level above 4.");
    add(4, "Greater Invisibility", ILL, A, "Touch", "V, S", "C 1 minute", "Brd Sor Wiz",
            "A creature you touch has the Invisible condition for the duration, even while attacking or casting spells.");
    add(4, "Guardian of Faith", CON, A, "30 ft.", "V", "8 hours", "Clr",
            "A spectral guardian appears. Enemies that come within 10 ft. of it make a DEX save, taking 20 Radiant damage on a failure or half on a success. It vanishes after dealing 60 damage.");
    add(4, "Ice Storm", EVO, A, "300 ft.", "V, S, M (a mitten)", INST, "Dru Sor Wiz",
            "Hailstones pound a 20-ft.-radius, 40-ft.-high Cylinder. Each creature there makes a DEX save, taking 2d10 Bludgeoning and 4d6 Cold damage on a failure or half on a success. The area becomes Difficult Terrain.")
            .higher("Bludgeoning damage increases by 1d10 for each slot level above 4.");
    add(4, "Locate Creature", DIV, A, "Self", "V, S, M (fur from a bloodhound)", "C 1 hour", "Brd Clr Dru Pal Rgr Wiz",
            "Sense the direction of a creature familiar to you (or the nearest of a kind you've seen) within 1,000 ft.");
    add(4, "Phantasmal Killer", ILL, A, "120 ft.", "V, S", "C 1 minute", "Brd Wiz",
            "Tap into a creature's nightmares. It makes a WIS save, taking 4d10 Psychic damage and having Disadvantage on checks and attacks on a failure, repeating the save each turn.")
            .higher("Damage increases by 1d10 for each slot level above 4.");
    add(4, "Polymorph", TRA, A, "60 ft.", "V, S, M (a caterpillar cocoon)", "C 1 hour", "Brd Dru Sor Wiz",
            "Transform a creature into a Beast with a Challenge Rating no higher than its level or CR. Unwilling targets make a WIS save. It gains Temporary Hit Points equal to the Beast's HP.");
    add(4, "Staggering Smite", ENC, BA, "Self", "V", INST, "Pal",
            "Cast after hitting with a Melee weapon: the target takes an extra 4d6 Psychic damage and must succeed on a WIS save or have the Stunned condition until the end of your next turn.")
            .higher("Damage increases by 1d6 for each slot level above 4.");
    add(4, "Stone Shape", TRA, A, "Touch", "V, S, M (soft clay)", INST, "Art Clr Dru Wiz",
            "Form a stone object of Medium size or smaller into any shape: a passage, a coffer, a crude weapon, or a sealed door.");
    add(4, "Stoneskin", TRA, A, "Touch", "V, S, M (diamond dust worth 100+ GP, consumed)", "C 1 hour", "Art Dru Rgr Sor Wiz",
            "A willing creature's flesh turns hard as stone, giving it Resistance to Bludgeoning, Piercing, and Slashing damage.");
    add(4, "Wall of Fire", EVO, A, "120 ft.", "V, S, M (a piece of charcoal)", "C 1 minute", "Dru Sor Wiz",
            "Create a wall of fire up to 60 ft. long. Creatures in its area make a DEX save, taking 5d8 Fire damage on a failure or half on a success; one side burns creatures that end their turn near it.")
            .higher("Damage increases by 1d8 for each slot level above 4.");
}

// ---------------------------------------------------------------
// Level 5
// ---------------------------------------------------------------
static {
    add(5, "Animate Objects", TRA, A, "120 ft.", "V, S", "C 1 minute", "Art Brd Sor Wiz",
            "Objects come to life as your allies. Animate up to your spellcasting modifier in Medium-or-smaller objects, which attack at your command as a Bonus Action.");
    add(5, "Banishing Smite", CON, BA, "Self", "V", "C 1 minute", "Pal",
            "Cast after hitting with a weapon: the target takes an extra 5d10 Force damage. If that leaves it with 50 HP or fewer, it must succeed on a CHA save or be banished to a harmless demiplane, where it has the Incapacitated condition.");
    add(5, "Circle of Power", ABJ, A, "Self", "V", "C 10 minutes", "Clr Pal Wiz",
            "Allies in a 30-ft. Emanation have Advantage on saves against spells and magical effects, and take no damage on a successful save that would deal half.");
    add(5, "Cloudkill", CON, A, "120 ft.", "V, S", "C 10 minutes", "Sor Wiz",
            "A 20-ft.-radius Sphere of poisonous fog drifts 10 ft. each turn. Creatures in it make a CON save, taking 5d8 Poison damage on a failure or half on a success.")
            .higher("Damage increases by 1d8 for each slot level above 5.");
    add(5, "Commune", DIV, "1 minute", "Self", "V, S, M (incense)", "1 minute", "Clr",
            "Contact your deity or a divine proxy and ask up to three questions that can be answered with yes or no.").ritual();
    add(5, "Cone of Cold", EVO, A, "Self", "V, S, M (a small crystal or glass cone)", INST, "Dru Sor Wiz",
            "A blast of cold air erupts in a 60-ft. Cone. Each creature there makes a CON save, taking 8d8 Cold damage on a failure or half on a success.")
            .higher("Damage increases by 1d8 for each slot level above 5.");
    add(5, "Conjure Elemental", CON, A, "60 ft.", "V, S", "C 10 minutes", "Dru Wiz",
            "Conjure a Large elemental spirit. Creatures that enter its space or start their turn there must succeed on a DEX save or take 8d8 damage of its element and have the Restrained condition.")
            .higher("Damage increases by 2d8 for each slot level above 5.");
    add(5, "Contact Other Plane", DIV, "1 minute", "Self", "V", "1 minute", "Wlk Wiz",
            "Mentally contact a demigod or similar being and ask up to five questions. You must succeed on a DC 15 INT save or take 6d6 Psychic damage and have the Incapacitated condition until you finish a Long Rest.").ritual();
    add(5, "Dispel Evil and Good", ABJ, A, "Self", "V, S, M (powdered silver and iron)", "C 1 minute", "Clr Pal",
            "Celestials, Elementals, Fey, Fiends, and Undead have Disadvantage on attacks against you. You can end their Charmed, Frightened, or possession effects on a creature, or banish one with a melee spell attack.");
    add(5, "Dominate Person", ENC, A, "60 ft.", "V, S", "C 1 minute", "Brd Sor Wiz",
            "A Humanoid must succeed on a WIS save or have the Charmed condition; you can issue it commands telepathically.")
            .higher("Duration increases with level 6 (10 min), 7 (1 hour), and 8+ (8 hours) slots.");
    add(5, "Flame Strike", EVO, A, "60 ft.", "V, S, M (a pinch of sulfur)", INST, "Clr",
            "A column of divine fire 10 ft. in radius and 40 ft. high roars down. Each creature there makes a DEX save, taking 5d6 Fire and 5d6 Radiant damage on a failure or half on a success.")
            .higher("Fire and Radiant damage each increase by 1d6 for each slot level above 5.");
    add(5, "Geas", ENC, "1 minute", "60 ft.", "V", "30 days", "Brd Clr Dru Pal Wiz",
            "A creature must succeed on a WIS save or have the Charmed condition and follow a command you give. It takes 5d10 Psychic damage (once per day) whenever it acts against the command.")
            .higher("With a level 7+ slot the spell lasts up to a year; with a level 9 slot it lasts until dispelled.");
    add(5, "Greater Restoration", ABJ, A, "Touch", "V, S, M (diamond dust worth 100+ GP, consumed)", INST, "Art Brd Clr Dru Pal Rgr",
            "Remove one level of Exhaustion or end one effect on a creature: the Charmed or Petrified condition, a curse, an ability score reduction, or a reduction to its HP maximum.");
    add(5, "Hold Monster", ENC, A, "90 ft.", "V, S, M (a straight piece of iron)", "C 1 minute", "Brd Sor Wlk Wiz",
            "A creature must succeed on a WIS save or have the Paralyzed condition. It repeats the save at the end of each of its turns.")
            .higher("You can target one additional creature for each slot level above 5.");
    add(5, "Insect Plague", CON, A, "300 ft.", "V, S, M (a locust)", "C 10 minutes", "Clr Dru Sor",
            "Swarming locusts fill a 20-ft.-radius Sphere, which is Lightly Obscured and Difficult Terrain. Creatures there make a CON save, taking 4d10 Piercing damage on a failure or half on a success.")
            .higher("Damage increases by 1d10 for each slot level above 5.");
    add(5, "Legend Lore", DIV, "10 minutes", "Self", "V, S, M (incense worth 250+ GP, consumed)", INST, "Brd Clr Wiz",
            "Name or describe a famous person, place, or object, and learn significant lore about it.");
    add(5, "Mass Cure Wounds", ABJ, A, "60 ft.", "V, S", INST, "Brd Clr Dru",
            "Up to six creatures in a 30-ft.-radius Sphere regain Hit Points equal to 5d8 plus your spellcasting ability modifier.")
            .higher("Healing increases by 1d8 for each slot level above 5.");
    add(5, "Mislead", ILL, A, "Self", "S", "C 1 hour", "Brd Wlk Wiz",
            "You gain the Invisible condition while an illusory double of you appears. You can see and hear through the double and move it.");
    add(5, "Raise Dead", NEC, "1 hour", "Touch", "V, S, M (a diamond worth 500+ GP, consumed)", INST, "Brd Clr Pal",
            "Return a creature that died within the last 10 days to life with 1 HP. It takes a -4 penalty to D20 Tests that decreases by 1 each Long Rest.");
    add(5, "Scrying", DIV, "10 minutes", "Self", "V, S, M (a focus worth 1,000+ GP)", "C 10 minutes", "Brd Clr Dru Wlk Wiz",
            "See and hear a creature on the same plane. It makes a WIS save modified by how well you know it; on a failure an invisible sensor follows it.");
    add(5, "Seeming", ILL, A, "30 ft.", "V, S", "8 hours", "Brd Sor Wiz",
            "Give any number of creatures you can see an illusory appearance. Unwilling creatures make a CHA save to resist.");
    add(5, "Steel Wind Strike", CON, A, "30 ft.", "S, M (a Melee weapon worth 1+ SP)", INST, "Rgr Wiz",
            "Flash between up to five creatures, making a melee spell attack against each; a hit deals 6d10 Force damage. You can then teleport next to one of them.");
    add(5, "Swift Quiver", TRA, BA, "Self", "V, S, M (a quiver worth 1+ GP)", "C 1 minute", "Rgr",
            "Your quiver produces endless nonmagical ammunition, and as a Bonus Action you can make two attacks with a Ranged weapon.");
    add(5, "Synaptic Static", ENC, A, "120 ft.", "V, S", INST, "Brd Sor Wlk Wiz",
            "Psychic energy erupts in a 20-ft.-radius Sphere. Each creature makes an INT save, taking 8d6 Psychic damage on a failure or half on a success; on a failure it also subtracts 1d6 from attacks, checks, and Concentration saves.");
    add(5, "Telekinesis", TRA, A, "60 ft.", "V, S", "C 10 minutes", "Sor Wiz",
            "Move a creature or object with your mind. A creature must succeed on a STR save or be moved up to 30 ft. and have the Restrained condition until the end of your next turn.");
    add(5, "Teleportation Circle", CON, "1 minute", "10 ft.", "V, M (inks worth 50+ GP, consumed)", "1 round", "Brd Sor Wlk Wiz",
            "Draw a 5-ft.-radius circle linked to a permanent teleportation circle whose sigil sequence you know. Creatures that enter it appear at the destination.");
    add(5, "Tree Stride", CON, A, "Self", "V, S", "C 1 minute", "Dru Rgr",
            "Step into a tree and out of another tree of the same kind within 500 ft., once per turn.");
    add(5, "Wall of Force", EVO, A, "120 ft.", "V, S, M (a shard of glass)", "C 10 minutes", "Wiz",
            "An invisible wall of force springs into existence. Nothing can physically pass through it, and it's immune to all damage.");
    add(5, "Wall of Stone", EVO, A, "120 ft.", "V, S, M (a cube of granite)", "C 10 minutes", "Art Dru Sor Wiz",
            "A nonmagical stone wall made of ten 10-ft. panels springs up. If you maintain Concentration for the full duration, it becomes permanent.");
}

// ---------------------------------------------------------------
// Levels 6-9
// ---------------------------------------------------------------
static {
    add(6, "Blade Barrier", EVO, A, "90 ft.", "V, S", "C 10 minutes", "Clr",
            "A wall of whirling blades provides Three-Quarters Cover. Creatures entering it or ending their turn there make a DEX save, taking 6d10 Force damage on a failure or half on a success.");
    add(6, "Chain Lightning", EVO, A, "150 ft.", "V, S, M (three silver pins)", INST, "Sor Wiz",
            "A bolt of lightning arcs to a target and up to three others within 30 ft. Each makes a DEX save, taking 10d8 Lightning damage on a failure or half on a success.")
            .higher("One additional bolt leaps for each slot level above 6.");
    add(6, "Circle of Death", NEC, A, "150 ft.", "V, S, M (the powder of a crushed black pearl worth 500+ GP)", INST, "Sor Wlk Wiz",
            "Negative energy ripples out in a 60-ft.-radius Sphere. Each creature there makes a CON save, taking 8d8 Necrotic damage on a failure or half on a success.")
            .higher("Damage increases by 2d8 for each slot level above 6.");
    add(6, "Disintegrate", TRA, A, "60 ft.", "V, S, M (a lodestone and dust)", INST, "Sor Wiz",
            "A green ray shoots at a target. It must succeed on a DEX save or take 10d6 + 40 Force damage; a creature reduced to 0 HP is disintegrated.")
            .higher("Damage increases by 3d6 for each slot level above 6.");
    add(6, "Eyebite", NEC, A, "Self", "V, S", "C 1 minute", "Brd Sor Wlk Wiz",
            "Each turn, target a creature that fails a WIS save: Asleep (the Unconscious condition), Panicked (the Frightened condition), or Sickened (the Poisoned condition).");
    add(6, "Find the Path", DIV, "1 minute", "Self", "V, S, M (divinatory tools worth 100+ GP)", "C 1 day", "Brd Clr Dru",
            "Sense the most direct physical route to a fixed location you name on the same plane.");
    add(6, "Globe of Invulnerability", ABJ, A, "Self", "V, S, M (a glass bead)", "C 1 minute", "Sor Wiz",
            "An immobile 10-ft.-radius barrier blocks spells of level 5 or lower cast from outside it.")
            .higher("Blocks spells one level higher for each slot level above 6.");
    add(6, "Harm", NEC, A, "60 ft.", "V, S", INST, "Clr",
            "A creature makes a CON save, taking 14d6 Necrotic damage on a failure or half on a success. Its HP maximum is reduced by the damage taken.");
    add(6, "Heal", ABJ, A, "60 ft.", "V, S", INST, "Clr Dru",
            "A creature regains 70 Hit Points, and the Blinded, Deafened, and Poisoned conditions end on it.")
            .higher("Healing increases by 10 for each slot level above 6.");
    add(6, "Heroes' Feast", CON, "10 minutes", "Self", "V, S, M (a gem-encrusted bowl worth 1,000+ GP, consumed)", INST, "Brd Clr Dru",
            "Up to twelve creatures partake in a feast: for 24 hours they have Resistance to Poison damage, immunity to the Frightened and Poisoned conditions, and +2d10 HP maximum.");
    add(6, "Mass Suggestion", ENC, A, "60 ft.", "V, M (a snake's tongue)", "24 hours", "Brd Sor Wlk Wiz",
            "Suggest a course of activity to up to twelve creatures. Each must succeed on a WIS save or have the Charmed condition and pursue the suggestion.")
            .higher("Duration increases with higher slots, up to a year and a day.");
    add(6, "Otto's Irresistible Dance", ENC, A, "30 ft.", "V", "C 1 minute", "Brd Wiz",
            "A creature begins a comic dance. It must use all its movement to dance in place, has Disadvantage on DEX saves and attack rolls, and others have Advantage on attacks against it.");
    add(6, "Planar Ally", CON, "10 minutes", "60 ft.", "V, S", INST, "Clr",
            "Beseech an otherworldly entity for aid; it sends a Celestial, Elemental, or Fiend that bargains for its service.");
    add(6, "Sunbeam", EVO, A, "Self", "V, S, M (a magnifying glass)", "C 1 minute", "Dru Sor Wiz",
            "A beam of brilliant light flashes out in a 60-ft. Line. Each creature makes a CON save, taking 6d8 Radiant damage and having the Blinded condition on a failure, or half damage on a success.");
    add(6, "True Seeing", DIV, A, "Touch", "V, S, M (mushroom powder worth 25+ GP, consumed)", "1 hour", "Brd Clr Sor Wlk Wiz",
            "A willing creature gains Truesight with a range of 120 ft.");
    add(6, "Wall of Ice", EVO, A, "120 ft.", "V, S, M (a piece of quartz)", "C 10 minutes", "Wiz",
            "Create a wall of ice. Creatures in its area make a DEX save, taking 10d6 Cold damage on a failure or half on a success.")
            .higher("Damage increases by 2d6 for each slot level above 6.");

    add(7, "Divine Word", EVO, BA, "30 ft.", "V", INST, "Clr",
            "Utter a word of power. Each creature of your choice that fails a CHA save suffers by HP: Deafened, Blinded, Stunned, or killed outright. Celestials, Elementals, Fey, and Fiends are sent home.");
    add(7, "Etherealness", CON, A, "Self", "V, S", "8 hours", "Brd Clr Sor Wlk Wiz",
            "Step into the border regions of the Ethereal Plane, where you can move through objects and creatures on the plane you left.")
            .higher("You can bring up to three willing creatures for each slot level above 7.");
    add(7, "Finger of Death", NEC, A, "60 ft.", "V, S", INST, "Sor Wlk Wiz",
            "A creature makes a CON save, taking 7d8 + 30 Necrotic damage on a failure or half on a success. A Humanoid killed by it rises as a Zombie under your command.");
    add(7, "Fire Storm", EVO, A, "150 ft.", "V, S", INST, "Clr Dru Sor",
            "A storm of fire fills up to ten 10-ft. Cubes. Each creature there makes a DEX save, taking 7d10 Fire damage on a failure or half on a success.");
    add(7, "Forcecage", EVO, A, "100 ft.", "V, S, M (ruby dust worth 1,500+ GP, consumed)", "C 1 hour", "Brd Wlk Wiz",
            "An immobile, invisible cage or box of force traps creatures inside. A creature trying to teleport out must succeed on a CHA save.");
    add(7, "Plane Shift", CON, A, "Touch", "V, S, M (a forked metal rod worth 250+ GP)", INST, "Clr Dru Sor Wlk Wiz",
            "Transport yourself and up to eight willing creatures to another plane, or banish an unwilling creature with a melee spell attack and a failed CHA save.");
    add(7, "Power Word Fortify", ENC, A, "60 ft.", "V", INST, "Brd Clr",
            "Fortify up to six creatures, dividing 120 Temporary Hit Points among them.");
    add(7, "Prismatic Spray", EVO, A, "Self", "V, S", INST, "Brd Sor Wiz",
            "Rays of light flash in a 60-ft. Cone. Each creature makes a DEX save and is struck by a random ray: elemental damage, the Restrained condition leading to Petrified, the Blinded condition, or banishment.");
    add(7, "Regenerate", TRA, "1 minute", "Touch", "V, S, M (a prayer wheel)", "1 hour", "Brd Clr Dru",
            "A creature regains 4d8 + 15 Hit Points and 1 HP at the start of each of its turns. Severed body parts regrow.");
    add(7, "Resurrection", NEC, "1 hour", "Touch", "V, S, M (a diamond worth 1,000+ GP, consumed)", INST, "Brd Clr",
            "Return a creature that has been dead for no more than a century to life with all its Hit Points.");
    add(7, "Reverse Gravity", TRA, A, "100 ft.", "V, S, M (a lodestone and iron filings)", "C 1 minute", "Dru Sor Wiz",
            "Gravity reverses in a 50-ft.-radius, 100-ft.-high Cylinder. Creatures and objects fall upward unless they succeed on a DEX save to grab something.");
    add(7, "Teleport", CON, A, "10 ft.", "V", INST, "Brd Sor Wiz",
            "Instantly transport yourself and up to eight willing creatures to a destination you select on the same plane. Familiarity determines whether you arrive on target.");

    add(8, "Antimagic Field", ABJ, A, "Self", "V, S, M (iron filings)", "C 1 hour", "Clr Wiz",
            "A 10-ft. Emanation of antimagic surrounds you. Spells can't be cast inside, magic items become mundane, and magical effects are suppressed.");
    add(8, "Befuddlement", ENC, A, "150 ft.", "V, S, M (a key ring with no keys)", INST, "Brd Dru Wlk Wiz",
            "Blast a creature's mind. It makes an INT save, taking 10d12 Psychic damage on a failure (or half on a success); on a failure it also can't cast spells or take the Magic action.");
    add(8, "Dominate Monster", ENC, A, "60 ft.", "V, S", "C 1 hour", "Brd Sor Wlk Wiz",
            "A creature must succeed on a WIS save or have the Charmed condition; you can issue it commands telepathically.")
            .higher("With a level 9 slot, Concentration lasts up to 8 hours.");
    add(8, "Earthquake", TRA, A, "500 ft.", "V, S, M (a fractured rock)", "C 1 minute", "Clr Dru Sor",
            "A seismic disturbance shakes a 100-ft.-radius circle. Creatures on the ground must succeed on a DEX save or have the Prone condition; fissures open and structures take damage.");
    add(8, "Holy Aura", ABJ, A, "Self", "V, S, M (a reliquary worth 1,000+ GP)", "C 1 minute", "Clr",
            "Allies in a 30-ft. Emanation have Advantage on saves, and attacks against them have Disadvantage. A Fiend or Undead that hits one must succeed on a CON save or have the Blinded condition.");
    add(8, "Incendiary Cloud", CON, A, "150 ft.", "V, S", "C 1 minute", "Dru Sor Wiz",
            "A 20-ft.-radius Sphere of embers drifts 10 ft. each turn. Creatures in it make a DEX save, taking 10d8 Fire damage on a failure or half on a success.");
    add(8, "Maze", CON, A, "60 ft.", "V, S", "C 10 minutes", "Wiz",
            "Banish a creature into a labyrinthine demiplane. It can escape with a DC 20 INT (Investigation) check as its action.");
    add(8, "Mind Blank", ABJ, A, "Touch", "V, S", "24 hours", "Brd Wiz",
            "A willing creature is immune to Psychic damage, the Charmed condition, and any effect that would sense its emotions, read its thoughts, or divine its location.");
    add(8, "Power Word Stun", ENC, A, "60 ft.", "V", INST, "Brd Sor Wlk Wiz",
            "If the target has 150 HP or fewer, it has the Stunned condition and repeats a CON save each turn to end it. Otherwise its Speed is 0 until your next turn.");
    add(8, "Sunburst", EVO, A, "150 ft.", "V, S, M (a piece of sunstone)", INST, "Clr Dru Sor Wiz",
            "Brilliant sunlight flashes in a 60-ft.-radius Sphere. Each creature makes a CON save, taking 12d6 Radiant damage and having the Blinded condition for 1 minute on a failure, or half damage on a success.");

    add(9, "Foresight", DIV, "1 minute", "Touch", "V, S, M (a hummingbird feather)", "8 hours", "Brd Dru Wlk Wiz",
            "A willing creature has Advantage on D20 Tests, and other creatures have Disadvantage on attack rolls against it.");
    add(9, "Gate", CON, A, "60 ft.", "V, S, M (a diamond worth 5,000+ GP)", "C 1 minute", "Clr Sor Wlk Wiz",
            "Open a portal to another plane of existence. Speaking a creature's name pulls it through the gate.");
    add(9, "Mass Heal", ABJ, A, "60 ft.", "V, S", INST, "Clr",
            "Restore up to 700 Hit Points divided among creatures you can see, and end the Blinded, Deafened, and Poisoned conditions on them.");
    add(9, "Meteor Swarm", EVO, A, "1 mile", "V, S", INST, "Sor Wiz",
            "Four blazing orbs crash down, each exploding in a 40-ft.-radius Sphere. Each creature makes a DEX save, taking 20d6 Fire and 20d6 Bludgeoning damage on a failure or half on a success.");
    add(9, "Power Word Heal", ENC, A, "Touch", "V", INST, "Brd Clr",
            "A creature regains all its Hit Points, and the Charmed, Frightened, Paralyzed, Poisoned, and Stunned conditions end. A Prone creature can stand up using its Reaction.");
    add(9, "Power Word Kill", ENC, A, "60 ft.", "V", INST, "Brd Sor Wlk Wiz",
            "A creature with 100 HP or fewer dies. Otherwise it takes 12d12 Psychic damage.");
    add(9, "Prismatic Wall", ABJ, A, "60 ft.", "V, S", "10 minutes", "Brd Wiz",
            "A shimmering, multicolored wall of seven layers appears, each with a different effect, including the Blinded condition for those who see it.");
    add(9, "Shapechange", TRA, A, "Self", "V, S, M (a jade circlet worth 1,500+ GP)", "C 1 hour", "Dru Wiz",
            "Assume the form of a creature with a Challenge Rating up to your level, gaining its statistics while keeping your mind and spellcasting.");
    add(9, "Storm of Vengeance", CON, A, "1 mile", "V, S", "C 1 minute", "Dru",
            "A churning storm cloud 300 ft. in radius forms. Creatures under it make a CON save or take 2d6 Thunder damage and have the Deafened condition, and later rounds bring acid, lightning, and hail.");
    add(9, "Time Stop", TRA, A, "Self", "V", INST, "Sor Wiz",
            "Time stops for everyone but you. You take 1d4 + 1 turns in a row; the spell ends if you affect another creature.");
    add(9, "True Polymorph", TRA, A, "30 ft.", "V, S, M (a drop of mercury, gum arabic, and smoke)", "C 1 hour", "Brd Wlk Wiz",
            "Transform a creature into another creature or an object, or an object into a creature. If held for the full duration, it lasts until dispelled.");
    add(9, "True Resurrection", NEC, "1 hour", "Touch", "V, S, M (diamonds worth 25,000+ GP, consumed)", INST, "Clr Dru",
            "Return a creature that has been dead no longer than 200 years to life with all its Hit Points, even without a body.");
    add(9, "Wish", CON, A, "Self", "V", INST, "Sor Wiz",
            "The mightiest spell a mortal can cast: duplicate any spell of level 8 or lower without components, or attempt another effect at great risk.");
}

private static Spell add(int level, String name, String school, String castingTime, String range,
                         String components, String duration, String classCodes, String description) {
    Spell s = new Spell();
    s.name = name;
    s.level = level;
    s.school = school;
    s.castingTime = castingTime;
    s.range = range;
    s.components = components;
    if (duration.startsWith("C ")) {
        s.concentration = true;
        s.duration = "Concentration, up to " + duration.substring(2);
    } else {
        s.duration = duration;
    }
    for (String code : classCodes.trim().split("\\s+")) {
        String cls = CLASS_CODES.get(code);
        if (cls != null) s.classes.add(cls);
    }
    s.description = description;
    s.inflictedConditions = detectConditions(description);
    SPELLS.put(name, s);
    return s;
}

// Condition names (from GameData.CONDITION_INFO) mentioned in a block of text, in reading order
static List<String> detectConditions(String text) {
    List<String> out = new ArrayList<>();
    if (text == null) return out;
    TreeMap<Integer, String> byPos = new TreeMap<>();
    for (String cond : GameData.CONDITION_INFO.keySet()) {
        if ("Conscious".equals(cond)) continue;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b" + cond + "\\b").matcher(text);
        if (m.find()) byPos.put(m.start(), cond);
    }
    out.addAll(byPos.values());
    return out;
}

// Older spells.json entries that aren't in the registry are added with basic info (no description),
// so homebrew lists keep working. Registry entries always win.
static void loadExtraSpells() {
    try {
        String content = new String(
                java.nio.file.Files.readAllBytes(DataPaths.resource("spells.json")),
                java.nio.charset.StandardCharsets.UTF_8);
        Object parsed = new MiniJsonParser(content).parse();
        if (!(parsed instanceof Map)) return;
        for (Object arr : ((Map<?, ?>) parsed).values()) {
            if (!(arr instanceof List)) continue;
            for (Object o : (List<?>) arr) {
                if (!(o instanceof Map)) continue;
                Map<?, ?> m = (Map<?, ?>) o;
                String name = String.valueOf(m.get("name"));
                if (SPELLS.containsKey(name) || "null".equals(name)) continue;
                Spell s = new Spell();
                s.name = name;
                s.level = JsonUtil.toInt(m.get("level"));
                s.school = String.valueOf(m.get("school"));
                s.castingTime = String.valueOf(m.get("castingTime"));
                Object range = m.get("range");
                s.range = range instanceof Number ? formatLegacyRange(((Number) range).intValue()) : String.valueOf(range);
                s.concentration = Boolean.TRUE.equals(m.get("concentration"));
                s.ritual = Boolean.TRUE.equals(m.get("ritual"));
                Object dur = m.get("duration");
                s.duration = dur instanceof Number ? formatLegacyDuration(((Number) dur).intValue(), s.concentration) : String.valueOf(dur);
                Object comp = m.get("components");
                if (comp instanceof List) {
                    List<String> parts = new ArrayList<>();
                    for (Object c : (List<?>) comp) parts.add(String.valueOf(c));
                    s.components = String.join(", ", parts);
                }
                Object cls = m.get("classes");
                if (cls instanceof List) for (Object c : (List<?>) cls) s.classes.add(String.valueOf(c));
                Object desc = m.get("description");
                s.description = desc == null ? "" : String.valueOf(desc);
                s.inflictedConditions = detectConditions(s.description);
                SPELLS.put(name, s);
            }
        }
    } catch (Exception ex) {
        // spells.json missing or unreadable; the built-in registry is enough
    }
}

static String formatLegacyRange(int r) {
    if (r == 0) return "Self / Touch";
    if (r == -1) return "Unlimited";
    if (r == -2) return "Special";
    return r + " ft.";
}

static String formatLegacyDuration(int minutes, boolean concentration) {
    String base;
    if (minutes == 0) base = INST;
    else if (minutes % 1440 == 0) base = (minutes / 1440) + (minutes == 1440 ? " day" : " days");
    else if (minutes % 60 == 0) base = (minutes / 60) + (minutes == 60 ? " hour" : " hours");
    else base = minutes + (minutes == 1 ? " minute" : " minutes");
    return concentration ? "Concentration, up to " + base : base;
}

static Spell lookup(String name) {
    return name == null ? null : SPELLS.get(name);
}

// Spells on a class's list from minLevel to maxLevel (inclusive), sorted by level then name
static List<Spell> classSpells(String cls, int minLevel, int maxLevel) {
    List<Spell> out = new ArrayList<>();
    String listCls = CustomAssets.spellListFor(cls); // homebrew classes can borrow another class's list
    for (Spell s : SPELLS.values()) {
        if (s.level >= minLevel && s.level <= maxLevel && (s.classes.contains(cls) || s.classes.contains(listCls))) out.add(s);
    }
    out.sort(Comparator.comparingInt((Spell s) -> s.level).thenComparing(s -> s.name));
    return out;
}

static String ordinal(int n) {
    if (n == 1) return "1st";
    if (n == 2) return "2nd";
    if (n == 3) return "3rd";
    return n + "th";
}

// ===============================================================
// Spellcasting progression (2024 PHB)
// ===============================================================
static final String FULL = "FULL", HALF = "HALF", PACT = "PACT";

static final Map<String, String> CASTING_ABILITY = new LinkedHashMap<>();
static final Map<String, String> CASTER_TYPE = new LinkedHashMap<>();
static final Map<String, int[]> CANTRIPS_BY_LEVEL = new LinkedHashMap<>();
static final Map<String, int[]> PREPARED_BY_LEVEL = new LinkedHashMap<>();
static {
    caster("Artificer", "INT", HALF,
            new int[]{2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4},
            new int[]{2, 3, 4, 5, 6, 6, 7, 7, 9, 9, 10, 10, 11, 11, 12, 12, 14, 14, 15, 15});
    caster("Bard", "CHA", FULL,
            new int[]{2, 2, 2, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4},
            new int[]{4, 5, 6, 7, 9, 10, 11, 12, 14, 15, 16, 16, 17, 17, 18, 18, 19, 20, 21, 22});
    caster("Cleric", "WIS", FULL,
            new int[]{3, 3, 3, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5},
            new int[]{4, 5, 6, 7, 9, 10, 11, 12, 14, 15, 16, 16, 17, 17, 18, 18, 19, 20, 21, 22});
    caster("Druid", "WIS", FULL,
            new int[]{2, 2, 2, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4},
            new int[]{4, 5, 6, 7, 9, 10, 11, 12, 14, 15, 16, 16, 17, 17, 18, 18, 19, 20, 21, 22});
    caster("Paladin", "CHA", HALF,
            null,
            new int[]{2, 3, 4, 5, 6, 6, 7, 7, 9, 9, 10, 10, 11, 11, 12, 12, 14, 14, 15, 15});
    caster("Ranger", "WIS", HALF,
            null,
            new int[]{2, 3, 4, 5, 6, 6, 7, 7, 9, 9, 10, 10, 11, 11, 12, 12, 14, 14, 15, 15});
    caster("Sorcerer", "CHA", FULL,
            new int[]{4, 4, 4, 5, 5, 5, 5, 5, 5, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6},
            new int[]{2, 4, 6, 7, 9, 10, 11, 12, 14, 15, 16, 16, 17, 17, 18, 18, 19, 20, 21, 22});
    caster("Warlock", "CHA", PACT,
            new int[]{2, 2, 2, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4},
            new int[]{2, 3, 4, 5, 6, 7, 8, 9, 10, 10, 11, 11, 12, 12, 13, 13, 14, 14, 15, 15});
    caster("Wizard", "INT", FULL,
            new int[]{3, 3, 3, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5},
            new int[]{4, 5, 6, 7, 9, 10, 11, 12, 14, 15, 16, 16, 17, 18, 19, 21, 22, 23, 24, 25});
}

// Package-private so CustomAssets can register homebrew casting classes
static void caster(String cls, String ability, String type, int[] cantrips, int[] prepared) {
    CASTING_ABILITY.put(cls, ability);
    CASTER_TYPE.put(cls, type);
    CANTRIPS_BY_LEVEL.put(cls, cantrips == null ? new int[20] : cantrips);
    PREPARED_BY_LEVEL.put(cls, prepared);
}

// Spell slots by caster level (index 0 = caster level 1); each row lists slots for spell levels 1-9
static final int[][] FULL_CASTER_SLOTS = {
        {2}, {3}, {4, 2}, {4, 3}, {4, 3, 2}, {4, 3, 3}, {4, 3, 3, 1}, {4, 3, 3, 2}, {4, 3, 3, 3, 1},
        {4, 3, 3, 3, 2}, {4, 3, 3, 3, 2, 1}, {4, 3, 3, 3, 2, 1}, {4, 3, 3, 3, 2, 1, 1}, {4, 3, 3, 3, 2, 1, 1},
        {4, 3, 3, 3, 2, 1, 1, 1}, {4, 3, 3, 3, 2, 1, 1, 1}, {4, 3, 3, 3, 2, 1, 1, 1, 1},
        {4, 3, 3, 3, 3, 1, 1, 1, 1}, {4, 3, 3, 3, 3, 2, 1, 1, 1}, {4, 3, 3, 3, 3, 2, 2, 1, 1}
};

// Mystic Arcanum: Warlock level -> spell level it unlocks (one spell, cast once per Long Rest without a slot)
static final int[][] MYSTIC_ARCANUM = {{11, 6}, {13, 7}, {15, 8}, {17, 9}};

static boolean isCaster(String cls) {
    return CASTER_TYPE.containsKey(cls);
}

static boolean isPreparedFromBook(String cls) {
    return "Wizard".equals(cls);
}

private static int idx(int lvl) {
    return Math.max(0, Math.min(19, lvl - 1));
}

static int cantripsKnown(String cls, int classLevel) {
    int[] t = CANTRIPS_BY_LEVEL.get(cls);
    return (t == null || classLevel < 1) ? 0 : t[idx(classLevel)];
}

static int preparedCount(String cls, int classLevel) {
    int[] t = PREPARED_BY_LEVEL.get(cls);
    return (t == null || classLevel < 1) ? 0 : t[idx(classLevel)];
}

static int pactSlotCount(int warlockLevel) {
    if (warlockLevel < 1) return 0;
    if (warlockLevel == 1) return 1;
    if (warlockLevel <= 10) return 2;
    if (warlockLevel <= 16) return 3;
    return 4;
}

static int pactSlotLevel(int warlockLevel) {
    if (warlockLevel < 1) return 0;
    return Math.min(5, (warlockLevel + 1) / 2);
}

// Highest spell level this class can prepare at the given class level, using its own
// single-class table (the 2024 multiclass rule for choosing spells).
static int maxSpellLevel(String cls, int classLevel) {
    String type = CASTER_TYPE.get(cls);
    if (type == null || classLevel < 1) return 0;
    if (PACT.equals(type)) return pactSlotLevel(classLevel);
    int casterLevel = HALF.equals(type) ? (classLevel + 1) / 2 : classLevel;
    return FULL_CASTER_SLOTS[idx(casterLevel)].length;
}

// Combined caster level for the shared Spellcasting slot table (Warlock Pact Magic is separate):
// full levels in Bard/Cleric/Druid/Sorcerer/Wizard + half (rounded up) in Artificer/Paladin/Ranger.
static int casterLevel(Map<String, Integer> classLevels) {
    int total = 0;
    for (Map.Entry<String, Integer> en : classLevels.entrySet()) {
        String type = CASTER_TYPE.get(en.getKey());
        if (FULL.equals(type)) total += en.getValue();
        else if (HALF.equals(type)) total += (en.getValue() + 1) / 2;
    }
    return total;
}

// Spell slots per level (index 0 = level 1 slots) from the shared Spellcasting table
static int[] spellSlots(Map<String, Integer> classLevels) {
    int[] out = new int[9];
    int cl = casterLevel(classLevels);
    if (cl < 1) return out;
    int[] row = FULL_CASTER_SLOTS[idx(cl)];
    System.arraycopy(row, 0, out, 0, row.length);
    return out;
}

static String slotSummary(int[] slots) {
    List<String> parts = new ArrayList<>();
    for (int i = 0; i < slots.length; i++) if (slots[i] > 0) parts.add(slots[i] + "×" + ordinal(i + 1));
    return parts.isEmpty() ? "none" : String.join(", ", parts);
}

// Spells a character always has prepared from species, lineage, feats, and class features:
// spell name -> source. These don't count against the prepared-spell limit.
static LinkedHashMap<String, String> alwaysPrepared(Map<?, ?> rec) {
    LinkedHashMap<String, String> out = new LinkedHashMap<>();
    String species = CharacterStorage.recStr(rec, "species");
    String lineage = CharacterStorage.recStr(rec, "lineage");
    int level = CharacterStorage.recLevel(rec);
    String lin = lineage.isEmpty() ? species : species + " (" + lineage + ")";

    String[][] lineageSpells = null; // {cantrip, level-3 spell, level-5 spell}
    if ("Elf".equals(species)) {
        if ("Drow".equals(lineage)) lineageSpells = new String[][]{{"Dancing Lights", "Faerie Fire", "Darkness"}};
        else if ("High Elf".equals(lineage)) lineageSpells = new String[][]{{"Prestidigitation", "Detect Magic", "Misty Step"}};
        else if ("Wood Elf".equals(lineage)) lineageSpells = new String[][]{{"Druidcraft", "Longstrider", "Pass without Trace"}};
    } else if ("Tiefling".equals(species)) {
        out.put("Thaumaturgy", "Tiefling (Otherworldly Presence)");
        if ("Abyssal".equals(lineage)) lineageSpells = new String[][]{{"Poison Spray", "Ray of Sickness", "Hold Person"}};
        else if ("Cthonic".equals(lineage) || "Chthonic".equals(lineage)) lineageSpells = new String[][]{{"Chill Touch", "False Life", "Ray of Enfeeblement"}};
        else if ("Infernal".equals(lineage)) lineageSpells = new String[][]{{"Fire Bolt", "Hellish Rebuke", "Darkness"}};
    } else if ("Gnome".equals(species)) {
        if ("Forest Gnome".equals(lineage)) {
            out.put("Minor Illusion", lin);
            out.put("Speak with Animals", lin);
        } else if ("Rock Gnome".equals(lineage)) {
            out.put("Mending", lin);
            out.put("Prestidigitation", lin);
        }
    } else if ("Aasimar".equals(species)) {
        out.put("Light", "Aasimar (Light Bearer)");
    }
    if (lineageSpells != null) {
        String[] row = lineageSpells[0];
        out.putIfAbsent(row[0], lin);
        if (level >= 3) out.putIfAbsent(row[1], lin + ", character level 3");
        if (level >= 5) out.putIfAbsent(row[2], lin + ", character level 5");
    }

    for (Map<String, Object> f : CharacterStorage.recFeats(rec)) {
        String name = String.valueOf(f.get("name"));
        if ("Fey-Touched".equals(name)) out.putIfAbsent("Misty Step", "Fey-Touched feat");
        else if ("Shadow-Touched".equals(name)) out.putIfAbsent("Invisibility", "Shadow-Touched feat");
        else if ("Telekinetic".equals(name)) out.putIfAbsent("Mage Hand", "Telekinetic feat");
        else if ("Telepathic".equals(name)) out.putIfAbsent("Detect Thoughts", "Telepathic feat");
    }

    Map<String, Integer> classLevels = CharacterStorage.recClassLevels(rec);
    if (classLevels.getOrDefault("Ranger", 0) >= 1) out.putIfAbsent("Hunter's Mark", "Ranger (Favored Enemy)");
    if (classLevels.getOrDefault("Paladin", 0) >= 2) out.putIfAbsent("Divine Smite", "Paladin (Paladin's Smite)");
    return out;
}
}

class Spell {
    String name;
    int level;                // 0 = cantrip
    String school = "";
    String castingTime = "";
    String range = "";
    String components = "";   // e.g. "V, S, M (a pinch of sulfur)"
    String duration = "";
    boolean concentration;
    boolean ritual;
    List<String> classes = new ArrayList<>();
    String description = "";
    String higherLevels = "";
    List<String> inflictedConditions = new ArrayList<>();
    String imagePath = null;  // optional card art (relative path, e.g. "art/spells/fireball.png")

    Spell higher(String text) {
        this.higherLevels = text;
        return this;
    }

    Spell ritual() {
        this.ritual = true;
        return this;
    }

    Spell inflicts(String... conditions) {
        this.inflictedConditions = new ArrayList<>(Arrays.asList(conditions));
        return this;
    }

    Spell image(String path) {
        this.imagePath = path;
        return this;
    }

    boolean hasComponent(String c) {
        for (String part : components.split(",")) {
            if (part.trim().startsWith(c)) return true;
        }
        return false;
    }

    // "V, S, M" without the material description
    String componentLetters() {
        List<String> parts = new ArrayList<>();
        for (String c : new String[]{"V", "S", "M"}) if (hasComponent(c)) parts.add(c);
        return String.join(", ", parts);
    }

    // "(a pinch of sulfur)" part of the components, or ""
    String materialText() {
        int i = components.indexOf('(');
        int j = components.lastIndexOf(')');
        return (i >= 0 && j > i) ? components.substring(i + 1, j) : "";
    }

    String levelLine() {
        return level == 0 ? school + " Cantrip" : "Level " + level + " " + school;
    }

    // Plain-text summary for list info panels
    String infoText() {
        StringBuilder sb = new StringBuilder();
        sb.append(levelLine());
        if (ritual) sb.append(" (Ritual)");
        sb.append("\nCasting Time: ").append(castingTime);
        sb.append("\nRange: ").append(range);
        sb.append("\nComponents: ").append(components);
        sb.append("\nDuration: ").append(duration);
        if (!description.isEmpty()) sb.append("\n\n").append(description);
        if (!higherLevels.isEmpty()) sb.append("\n\n").append(level == 0 ? "Cantrip Upgrade: " : "Using a Higher-Level Spell Slot: ").append(higherLevels);
        if (!classes.isEmpty()) sb.append("\n\nClasses: ").append(String.join(", ", classes));
        return sb.toString();
    }
}
