# D&D Engine

A fan-made character builder and campaign toolkit for Dungeons & Dragons 5th Edition (2024 rules), written in Java Swing. It runs as a desktop app or in the browser.

**Try it in the browser:** https://tylernicastro.github.io/dnd_engine/

## Features

- **Character creation wizard:** species, class, ability scores (point buy, rolled or custom), background, skills, languages, spells and starting equipment.
- **Character sheets:** stats, skills, spells and spell slots, equipment, coins and conditions. Click any value to see what it is and where it comes from.
- **Level up:** hit points, multiclassing, feats and epic boons, new spells and spell swaps.
- **Asset Library:** browse all built-in content and create your own homebrew items, weapons, armor, spells, feats, species, backgrounds, classes, subclasses, conditions, NPCs and maps. Homebrew can be shared as JSON packs.
- **Tags and content filters:** every asset has tags (source book, homebrew, spell school, level and class, plus your own). Turn a tag off to hide that content from character creation and level up, for example to run a core-rules-only campaign.
- **Maps and combat:** grid maps with walls, doors and clickable encounter pins.

## Running it

### Desktop

You need a JDK (version 11 or newer).

```bash
javac -encoding UTF-8 -d bin src/*.java
java -cp bin App
```

Run it from the project folder so it can find `spells.json` and `weapons.json`. Characters are saved to `characters.json` and homebrew to `custom_assets.json` in the same folder.

### Browser

The web version uses [CheerpJ](https://cheerpj.com/) to run the same Java code in the browser.

```bash
bash web/build.sh        # or: powershell web\build.ps1
cd web && python3 -m http.server 8000
```

Then open http://localhost:8000/. In the browser, your data is stored locally in your browser's storage. Pushes to `main` are deployed to GitHub Pages automatically (`.github/workflows/pages.yml`).

## Project layout

| Path | What it is |
| --- | --- |
| `src/App.java` | Entry point, main window and most screens |
| `src/GameData.java`, `SpellData.java`, `FeatData.java`, `WeaponData.java` | Built-in rules data |
| `src/CustomAssets.java`, `AssetLibraryPanel.java` | Homebrew data and the Asset Library |
| `src/Tags.java` | Tags and content filters |
| `src/LevelUpPanel.java`, `MapEditorPanel.java` | Level up and the map editor |
| `web/` | Browser build and website |

## Copyright and Wizards of the Coast

D&D Engine is an unofficial fan project. It is not affiliated with, endorsed, sponsored or approved by Wizards of the Coast.

Dungeons & Dragons, D&D, and the names of its products, worlds and characters are trademarks of Wizards of the Coast LLC. The game rules and rules content are © Wizards of the Coast LLC.

To be clear about what this project contains: the built-in data (spells, feats, classes, species, backgrounds, conditions and equipment) is condensed, paraphrased summaries of D&D rules. Some of it is covered by the System Reference Document (SRD 5.2), which Wizards of the Coast releases under the Creative Commons Attribution 4.0 license. Some of it is not: for example, content first published in *Xanathar's Guide to Everything*, *Tasha's Cauldron of Everything*, *Mordenkainen Presents: Monsters of the Multiverse* and *Eberron: Rising from the Last War*, along with names like the Artificer, Tabaxi and Warforged. That material is included for personal, non-commercial use, and I don't claim any rights to it. If you are Wizards of the Coast and want something removed, please open an issue and it will be taken down.

The summaries are not a replacement for the books. If you play D&D, please buy the official rulebooks.

Parts of this work include material from the System Reference Document 5.2 ("SRD 5.2") by Wizards of the Coast LLC, available at https://www.dndbeyond.com/srd. The SRD 5.2 is licensed under the Creative Commons Attribution 4.0 International License, available at https://creativecommons.org/licenses/by/4.0/legalcode.

## License

The source code is released under the [MIT License](LICENSE). The license covers the code only, not the Wizards of the Coast content described above.
