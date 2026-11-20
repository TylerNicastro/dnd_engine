# Combat Spells vs. Rules as Written

This file lists every way the Combat screen's level 1 spells differ from the rules as written (2024 Player's Handbook, plus the four Heroes of Faerûn spells the app lists). If a spell isn't in a table below, Combat runs it as written.

Code: per-spell mechanics are in `src/CombatSpells.java` (`DEFS`). The casting flow, effects, Concentration and reactions are in the "Spellcasting" section of `src/CombatPanel.java`.

**Status key**
- **Simplified**: the rule is in play, but Combat handles part of it by hand or approximately.
- **Not modeled**: Combat ignores this part. The DM tracks it.
- **Interpretation**: the rules are unclear, and Combat picks one reading.
- **Unverified**: no official text could be found to check against.

---

## Open questions (waiting for your call)

These would be big changes, so they're on hold until you decide:

1. **Spell slots aren't written back to the character sheet.** Following the project rule "keep combat state out of character sheets," each token gets a copy of the slots the sheet has left. The copy is made when the token is placed and again at Start Combat. Slots spent in a fight only change that copy. End Combat reminds you to mark used slots on the sheet's Spells tab, and the next Start Combat re-copies from the sheet. *Option:* End Combat could offer "Apply spent slots to character sheets." That would break the current rule, so it needs your approval.
2. **NPCs can't cast spells.** NPC stat blocks have no spell list (only free-text actions), so Cast a Spell is for characters only. *Option:* add a "Spells" field to the NPC schema (spell list, save DC, attack bonus, slots or uses per day).
3. **Cantrips aren't castable in Combat yet.** You asked for level 1 spells. Most cantrips would reuse the same machinery (spell attack, save, area), so adding them is mainly a table of definitions.

---

## Rules that apply to every spell

| Rule | Status | What Combat does |
|---|---|---|
| Spell slots | Simplified | Uses a per-token copy of the sheet's remaining slots (see open question 1). You can adjust it by clicking the slot pips. |
| "Cast without a slot" uses (species and feat spells, Favored Enemy, Paladin's Smite) | Simplified | These refill at every Start Combat instead of on a Long Rest. Species and feat spells get 1 use (Forest Gnome's Speak with Animals gets its Proficiency Bonus in uses). |
| Magic Initiate's free level 1 spell | Not modeled | The sheet doesn't record which spell was picked, so Combat can't offer it. |
| One spell slot per turn | As written | Enforced on every turn, including Reactions cast on someone else's turn. Free casts don't count. |
| Components (V, S, M), a free hand or focus, costly materials | Not modeled | Not checked. A gagged, silenced or restrained caster can still cast. |
| Ritual casting | As written | Not offered. Rituals add 10 minutes, so they can't be cast in combat. |
| Casting time of 1 minute or longer | As written | Can't be cast in combat: Alarm, Find Familiar, Identify, Illusory Script. |
| "A creature you can see" | Simplified | The target can't be behind a wall, in Darkness beyond your Darkvision, in or behind Fog Cloud, or Invisible (unless Faerie Fire or Buzzing Bee is on it), and you can't be Blinded. Total Cover only counts walls and closed doors. |
| Touch range | Simplified | Touch means within 5 ft. A reach longer than 5 ft. doesn't extend Touch. |
| Areas of effect | Simplified | A square counts as inside the area if its center is (the half-covered-square method). Cones start at the edge of your space and aim freely with the mouse. Cubes that originate from you face one of 8 directions. Spheres center on a grid intersection. Total Cover from the area's origin protects a creature. Half cover from the origin gives +2 to DEX saves. Three-quarters cover doesn't exist on the board. |
| Saving throw bonuses | Simplified | For characters: the ability modifier, plus the Proficiency Bonus for the primary class's saves and Resilient. **Not counted:** Paladin Aura of Protection, Monk Disciplined Survivor, magic items, other features. For NPCs, Combat uses the stat block's "Saving throws" line, or the plain ability modifier if a save isn't listed there. |
| Save modifiers | Simplified | Applied automatically: Bless, Bane, Dodge (DEX Advantage), Restrained (DEX Disadvantage), auto-fail on STR and DEX saves when Paralyzed, Stunned, Unconscious or Petrified, and half cover. **Not applied:** Exhaustion's −2 per level, and conditions or features that grant Advantage on saves. Use Reroll or Flip. |
| Spell attack bonus and save DC | Simplified | Proficiency Bonus + the spellcasting ability modifier only. Magic items and features that add to them aren't counted. Species and feat spells use the highest of INT, WIS and CHA (the sheet does the same). |
| Durations | Simplified | Counted in rounds on the caster's turn starts (1 minute = 10 rounds). Effects that last an hour or more stay until removed. **End Combat ends every effect timed in rounds**, even if less than a minute has passed. |
| Concentration | Simplified | The CON save (DC 10 or half the damage, max 30) is prompted after damage dealt through Combat. War Caster gives Advantage. Casting another Concentration spell ends the first. Dropping to 0 HP ends it immediately. Becoming Incapacitated ends it only when a spell applies the condition, or at the caster's next turn start (adding the condition by hand doesn't end it at once). |
| Temporary Hit Points | As written | Damage comes off them first, they don't stack (the higher amount is kept), and healing doesn't restore them. |
| Damage for several targets | As written | Rolled once and applied to all targets (2024 rule). |
| Critical hits with extra dice (smites, Hex, Hunter's Mark, Divine Favor) | As written | The extra dice are doubled too. |
| Undo | App feature | Undo Spell refunds the slot, the action and Concentration. A previous Concentration spell that the new one ended does **not** come back. Closing a save or heal overlay applies what it shows. |

---

## Spell by spell

| Spell | Status | Difference |
|---|---|---|
| Animal Friendship | Not modeled | Charmed is applied for the duration. The Charmed condition's own effects (it can't attack you, and so on) aren't enforced by Combat. |
| Armor of Agathys | As written | The Cold damage triggers on melee attack-roll hits made through the attack roll. It ends when its Temporary Hit Points are gone. |
| Bane / Bless | As written | The d4 is rolled automatically on attack rolls and saving throws (Concentration saves included). |
| Burning Hands | Not modeled | Flammable objects catching fire. |
| **Buzzing Bee** | **Unverified** | No official source contains this spell: aidedd.org says it doesn't exist, and it isn't among Heroes of Faerûn's 19 spells. It works as the app describes it: the target can't benefit from Invisible and has Disadvantage on Concentration saves. Please confirm the source, or remove it. |
| Charm Person | Interpretation | "Advantage if you or your allies are fighting it" means the target is on a different team from you. It ends when you or someone on your team damages it. The Charmed condition's effects aren't enforced. |
| Chromatic Orb | Simplified | When two d8s match on a hit, a prompt offers the leap: a new attack against a creature within 30 ft. of the last target, up to your slot level in leaps, each creature once. Matching is checked on the d8s as last rolled. If you type in your own damage total, there are no dice to compare, so no leap is offered. |
| Command | Simplified | Halt (no movement, action or Bonus Action) and Grovel (Prone) happen automatically at the start of the target's next turn. Approach, Flee and Drop show as a status note, and you move the token yourself. Any automatic failure (for example, a target that doesn't understand your language) isn't checked. |
| Compelled Duel | Simplified | Disadvantage on attacks against anyone but you is applied, and the spell ends if you attack another creature. **Not modeled:** the target can't willingly move more than 30 ft. from you, and the spell ending when an ally attacks the target or you end your turn more than 30 ft. away. |
| Dissonant Whispers | Simplified | On a failed save the target's Reaction is spent. You drag its token away yourself, and opportunity attacks from that movement aren't prompted. |
| Divine Smite | As written | Offered in the attack roll after a hit with a Melee weapon (thrown ones included) or an Unarmed Strike. Fiends and Undead are detected by creature type. |
| Ensnaring Strike | Simplified | The escape check (an action, STR (Athletics)) is a button on the effect. Only the Restrained creature can use it, not another creature within reach. |
| Entangle | Simplified | The Difficult Terrain is real (movement costs double). The escape check is a button on the effect. |
| Faerie Fire | Not modeled | The Dim Light that outlined creatures shed. |
| False Life | Simplified | The Temporary HP are rolled automatically, with no reroll or set-your-own-roll option. |
| Feather Fall, Comprehend Languages, Create or Destroy Water, Detect Evil and Good, Detect Magic, Detect Poison and Disease, Disguise Self, Purify Food and Drink, Silent Image, Speak with Animals, Tenser's Floating Disk, Unseen Servant | Not modeled | Combat spends the slot and action and tracks the duration (and Concentration) only. These spells have no board mechanics here. |
| Fog Cloud | Simplified | Blocks sight in both directions for attacks, spell targeting and opportunity attacks. It doesn't change the light shading on the board, and a strong wind doesn't disperse it. |
| Goodberry | Simplified | The berries are combat items only, not added to the character sheet. |
| Grease | Simplified | The save for entering the grease happens once per Move. Dragging a token into it doesn't trigger a save. |
| Hellish Rebuke | Simplified | Offered only when the damage comes from a Combat attack or spell with a known attacker. Damage typed into the HP box has no source, so it isn't offered then. |
| Hex | Not modeled | Disadvantage on ability checks with the chosen ability (Combat makes almost no ability checks). |
| Hunter's Mark | Not modeled | Advantage on WIS (Perception or Survival) checks to find the target. |
| **Insidious Rhythm** | **Unverified** | Same as Buzzing Bee: no official source found. It works as the app describes it: a WIS save, then Disadvantage on Concentration saves, with a repeat save at the end of each of the target's turns. |
| Jump | Not modeled | Jumping isn't on the board. The duration is tracked only. |
| Mage Armor | Simplified | For characters, any body armor listed in Equipment counts as worn, as the sheet assumes. NPCs are assumed to be unarmored. |
| Magic Missile | Interpretation | **Each dart is rolled separately.** The 2014 Sage Advice ruling was to roll once and use that number for every dart. Should Combat roll once instead? |
| Protection from Evil and Good | Not modeled | Immunity to being Charmed, Frightened or possessed by those creature types. Disadvantage on their attacks is applied. |
| Sanctuary | Simplified | The WIS save happens for attack rolls against the warded creature. **Not modeled:** single-target damaging spells (such as Inflict Wounds or Magic Missile) should also need the save. On a failed save, the attack is lost; use Undo to pick another target. |
| Shield | Simplified | Offered in the attack roll when the hit would land, and in Magic Missile. It always uses your lowest available slot (a higher slot does nothing extra). |
| Sleep | Interpretation | Creatures that don't sleep succeed automatically. That covers characters of the Elf species, and NPCs whose traits mention not needing sleep, not being able to be put to sleep, or immunity to Exhaustion. Shaking a sleeper awake is a button on the effect. |
| **Spellfire Flare** | **Unverified details** | A real Heroes of Faerûn spell, but only a summary is public: "Ranged spell attack that ignores Half and Three-Quarters Cover, 2d10 Radiant, +1 blast per slot level." Combat follows that summary. |
| Tasha's Hideous Laughter | Interpretation | Prone stays after the spell ends (the target just can't stand up while laughing). Incapacitated ends with the spell. |
| Thunderous Smite / Thunderwave | Simplified | The 10-ft. push goes in a straight line away from you and stops at walls, closed doors and creatures. Thunderwave's unsecured objects and the audible boom aren't modeled. |
| **Wardaway** | **Unverified details** | A real Heroes of Faerûn spell, but only a summary is public: "creature other than a Construct or Undead, CON save, 2d4 Force (+2d4 per level), Speed halved and only an action or a Bonus Action on its next turn." Combat follows that summary. |
| Witch Bolt | As written | The Bonus Action damage is a button on later turns. The spell ends at your turn start if the target is out of range or behind Total Cover. |

---

## Spell text corrected in SpellData

These descriptions now match the 2024 text:

- **Sanctuary:** ends if the warded creature makes an attack roll, casts any spell, or deals damage, and it doesn't protect against areas of effect. The old text said "casts a harmful spell."
- **Wardaway:** can't affect Constructs or Undead.
- **Wrathful Smite:** works with Unarmed Strikes too, and the target repeats the save at the end of each of its turns.
