# Combat Spells vs. Rules as Written

This file lists every way the Combat screen's spells differ from the rules as written: cantrips and level 1 spells (2024 Player's Handbook, plus the four Heroes of Faerûn spells the app lists), NPC spellcasting, and homebrew spells. If a spell isn't in a table below, Combat runs it as written.

Built-in spells of level 2 and up have no combat automation yet. Casting one spends the slot and action, starts Concentration if the spell has it, and tracks its duration; you apply its effects by hand.

Code: per-spell mechanics are in `src/CombatSpells.java` (`DEFS`, plus `fromAsset` for homebrew). The casting flow, effects, Concentration and reactions are in the "Spellcasting" section of `src/CombatPanel.java`.

**Status key**
- **Simplified**: the rule is in play, but Combat handles part of it by hand or approximately.
- **Not modeled**: Combat ignores this part. The DM tracks it.
- **Interpretation**: the rules are unclear, and Combat picks one reading.
- **Unverified**: no official text could be found to check against.

---

## Open questions (waiting for your call)

These would be big changes, so they're on hold until you decide:

1. **Spell slots aren't written back to the character sheet.** Following the project rule "keep combat state out of character sheets," each token gets a copy of the slots the sheet has left. The copy is made when the token is placed and again at Start Combat. Slots spent in a fight only change that copy. End Combat reminds you to mark used slots on the sheet's Spells tab, and the next Start Combat re-copies from the sheet. *Option:* End Combat could offer "Apply spent slots to character sheets." That would break the current rule, so it needs your approval.
2. **Magic Missile** rolls each dart separately. The 2014 Sage Advice ruling was to roll once and use that number for every dart. Which do you want? (See its row below.)
3. **Buzzing Bee and Insidious Rhythm** couldn't be found in any official source. Keep them, or remove them? (See their rows below.)

Done since the last version: NPCs can cast spells, cantrips are castable, and homebrew spells get combat rules from Asset Library fields.

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
| Hunter's Mark | Not modeled | Advantage on WIS (Perception or Survival) checks to find the target. You pick the enemy when casting. The mark shows as a red crosshair on the board and as "Hunter's Mark → *target*" in the caster's side panel. The 1d6 Force is added to the caster's attack-roll hits against it. |
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

## Smites (Divine Smite, Searing / Thunderous / Wrathful Smite, Ensnaring Strike, Hail of Thorns)

| Rule | Status | What Combat does |
|---|---|---|
| Casting time "Bonus Action, immediately after you hit" | App feature | Two ways in. You can **ready** a smite from Cast a Spell before attacking: nothing is spent until a hit lands, and then pressing Done on the hit casts it. Or you can click a smite button in the attack roll after a hit. Either way it uses the Bonus Action and a slot (or a free cast) at the moment of the hit, as written. A readied smite that hasn't fired ends with the turn. |
| Why a smite can't be used | App feature | The attack roll lists every smite with the reason it's unavailable: wrong weapon type, Bonus Action already used (for example, by Hunter's Mark that turn), no slot, or a slot already spent this turn. |

---

## Cantrips

Cantrip damage grows at character levels 5, 11 and 17. NPCs use their "Spellcaster level" field instead.

| Cantrip | Status | Difference |
|---|---|---|
| Blade Ward, Chill Touch, Fire Bolt, Mind Sliver, Poison Spray, Ray of Frost, Sacred Flame, Shocking Grasp, Thunderclap, Toll the Dead, Vicious Mockery, Word of Radiance, Acid Splash | As written | Rider effects (can't heal, Speed −10, no opportunity attacks, −1d4 on the next save, Disadvantage on the next attack, d12s against a wounded target) are applied and tracked. |
| Eldritch Blast | Not modeled | Warlock invocations (Agonizing Blast, Repelling Blast...). The beam count and separate targets are handled. |
| Friends | Simplified | It succeeds automatically if the target is on another team (you're fighting it). "Already cast on it in the past 24 hours" isn't tracked. |
| Guidance, Resistance | Not modeled | Tracked only: add the 1d4 to the check, or subtract it from the damage, by hand. |
| Light | Simplified | Cast on a creature, meaning an object it carries: that creature sheds torch-like light (20 ft. Bright plus 20 ft. Dim) for 1 hour. Objects lying on the map can't be targeted. |
| Produce Flame | Simplified | Casting it (a Bonus Action) lights the caster like a torch. **Hurl Produce Flame** in the side panel is the Magic action: a ranged spell attack, after which the flame is gone. |
| Shillelagh | Interpretation | The damage type (Bludgeoning or Force) is picked once when casting, not on each hit. The Club or Quarterstaff attack switches to the spellcasting modifier and the bigger die while the spell lasts. |
| Sorcerous Burst | As written | Each 8 rolled adds a d8, up to your spellcasting modifier in extra dice. |
| Spare the Dying | Not modeled | Death saving throws (the app has none). The target gets a "Stable" condition. |
| Starry Wisp | Not modeled | The Dim Light the target sheds. Its "can't benefit from Invisible" is applied. |
| Thorn Whip | Simplified | The pull goes in a straight line toward you and stops at obstacles. |
| True Strike | Simplified | You pick the weapon and the damage type (normal or Radiant) before attacking. The attack works like a normal weapon attack (smites and Divine Favor apply; ammunition is used), and the extra Radiant dice are added at levels 5, 11 and 17. |
| Dancing Lights, Druidcraft, Elementalism, Mage Hand, Message, Minor Illusion, Prestidigitation, Thaumaturgy | Not modeled | Tracked only. Mending (1 minute) can't be cast in combat. |

---

## NPC spellcasting

| Rule | Status | What Combat does |
|---|---|---|
| Spell lists | App feature | The NPC editor's Spellcasting section has At will, 1/day each, 2/day each, 3/day each, and Spells cast with slots (with a "Spell slots" line such as 4, 3, 2). |
| Save DC and attack bonus | Simplified | Taken from the stat block. If left at 0, Combat works them out: 8 + Proficiency Bonus (from CR) + ability modifier, and Proficiency Bonus + ability modifier. |
| Per-day uses and slots | Simplified | Refill at every Start Combat. |
| "Cast at a higher level" notes in stat blocks | Not modeled | At-will and per-day spells are cast at their own level. Slot spells can be upcast with higher slots. |

---

## Homebrew spells

A homebrew spell's **Combat** section in the Asset Library (How it resolves, Targets, save, dice, conditions, lasting effect) is turned into combat rules automatically. If How it resolves is set to None, there's no automation; a built-in spell with the same name keeps its rules.

| Rule | Status | What Combat does |
|---|---|---|
| A lasting effect on an instantaneous spell | Interpretation | If "Effect lasts" is "Spell's duration" but the spell is Instantaneous, the effect lasts until the end of your next turn. |
| Homebrew smites | Simplified | A smite with a save and half damage deals all its damage through the save. Otherwise its dice are added to the hit and the save applies only the effect. |
| Effects the fields can't express | Not modeled | Anything beyond the listed fields (forced movement, summoning, terrain) is tracked by duration only. |

---

## Spell text corrected in SpellData

These descriptions now match the 2024 text:

- **Sanctuary:** ends if the warded creature makes an attack roll, casts any spell, or deals damage, and it doesn't protect against areas of effect. The old text said "casts a harmful spell."
- **Wardaway:** can't affect Constructs or Undead.
- **Wrathful Smite:** works with Unarmed Strikes too, and the target repeats the save at the end of each of its turns.
