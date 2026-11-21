# TODO: Road to Fully Playable

A high-level overview of what's still missing for a complete D&D session. Rough priority order within each section.

## Combat
- **Spells of level 2 to 9**: combat casting only covers cantrips and level 1. The Test Archmage can already cast everything, so it's a good test bed.
- **Ready with spells**: a readied spell is only tracked as text. Casting it with the Reaction when the trigger happens (slot spent on readying, held with Concentration) isn't automated yet.
- **Ability checks & saving throws on demand**: contested checks and DM-called saves during combat.
- **NPC automation (optional)**: suggested or automatic NPC turns (move, then attack the nearest target).
- **Encounter tools**: encounter difficulty / XP budget, quick-add groups of NPCs, reinforcements mid-fight.
- **More built-in monsters**: only the 91 core beasts are built in (beasts.json). Add the rest of the SRD 5.2 monsters, including the animal swarms (Swarm of Bats, Swarm of Rats...).
- **Wild Shape details**: a form's traits (Pack Tactics, Keen Smell...) and Bonus Actions / Reactions are shown but not automated; Circle of the Moon's bigger forms come with subclasses.
- **Mounted & underwater combat, falling, and terrain hazards.**

## Class & Subclass Features
- **Class features at levels 3-20**: levels 1 and 2 are built in (ClassFeatures). Add the rest (Extra Attack, Evasion, Stunning Strike, Font of Inspiration...), using the same parts.
- **Deeper feature automation**: Metamagic changes the spell being cast (Quickened, Twinned...), Eldritch Invocations apply their effects (Agonizing Blast, Armor of Shadows), Interception / Protection reactions.
- **Built-in subclasses**: only homebrew subclasses exist right now. Add the 2024 PHB subclasses with their features.
- **Feat effects**: most feats are recorded but their mechanics aren't applied automatically.

## Characters & Progression
- **Short & Long Rests**: the sheet's rest buttons restore feature uses and spell slots. Still missing: spending Hit Dice, current HP outside combat, and resetting conditions / Exhaustion.
- **Experience / milestone tracking**, with a prompt when a level is earned.
- **Spell management on the sheet**: swap prepared spells after a Long Rest, and ritual casting.
- **Inventory depth**: carrying capacity / encumbrance, attunement, containers, buying and selling.
- **Magic items**: data, attunement, charges and their effects on stats and attacks.
- **Level Down history**: record which class each HP roll and spell came from, so leveling down is exact for multiclass characters.

## Campaign & Session
- **Campaigns**: group characters, maps and NPCs into a campaign with session notes.
- **Exploration mode**: out-of-combat movement on maps, fog of war, doors, traps and secret areas.
- **Dice roller**: a general-purpose roller with history, outside of attacks and spells.
- **Initiative-free scenes**: social / exploration turns and travel pace.

## Multiplayer & Sharing
- **Player view**: a separate view (or shared screen) that hides DM-only info such as NPC HP and hidden tokens.
- **Online play (long-term)**: players control their own tokens and sheets from their own devices.
- **Share homebrew packs**: export / import individual Asset Library items or bundles, not only full backups.

## Polish & Quality
- **Wider undo**: attacks and spells can already be undone from the roll overlay. Extend undo to movement, manual damage and ended turns.
- **Automated tests** for rules logic (damage, AC, spell slots, level up / down).
- **Accessibility**: keyboard navigation, scalable fonts, colour-blind-friendly team colours.
- **Tutorial / first-run walkthrough** and in-app help.
- **Performance** on large maps and in the browser build.
