# D&D Engine — Project Guidelines

Java Swing app (`src/`). Main entry point is `App.java`; the main screens are cards in one `CardLayout` inside a single `JFrame`.

## Priority rules

1. **One window only.** Never open a second window. That means no `JOptionPane`, `JDialog`, extra `JFrame`/`JWindow`, or heavyweight popups.
   - Show extra detail, confirmations and pickers as panels or in-window overlays inside the main frame. Use `App.showSheetOverlay(...)` for overlays and `App.showConfirmOverlay(...)` for yes/no prompts.
   - Show validation messages inline, for example in the step's info panel or an error label next to the control.
   - Don't use a popup to report success. For example, after leveling up, return to the previous screen without a "You are now level 5!" message; the updated sheet speaks for itself.

2. **Everything is clickable for more detail.** Any stat, skill, item, feature, condition, language or other value shown to the user should open more information when clicked: what it is, what it's used for, and where it comes from (class, background, species, feat, etc.).
   - Use a hand cursor and a hover highlight so it's clear that something can be clicked.

## Conventions

- Colors: use the palette constants at the top of `App.java`. The character sheet uses `SHEET_BG` (#1E202C), `SHEET_CARD` (#252836), `SHEET_ROW_ALT` (#2A2E3F) and `GOLD` (#D4AF37).
- Characters are saved to `characters.json` through `CharacterStorage`. Fields the wizard doesn't edit (coins, conditions, level-up progression) are kept on edit by `CharacterStorage.carryOverProgression`, so add new sheet-only fields there.
- Rules data (classes, skills, languages, conditions, equipment) lives in `GameData.java`.
- Homebrew content lives in `CustomAssets.java` and is saved to `custom_assets.json`. Each category's fields are one schema block in `CustomAssets.SCHEMAS`; the Asset Library's editor form, detail overlay, validation and import/export are all built from it.
  - `CustomAssets.applyToRuntime()` merges homebrew into the GameData / SpellData / WeaponData tables. It snapshots the built-in tables on first run and restores them before every re-apply. If you add a new rules table that homebrew writes to, add it to the `snapshot(...)` call, or deleted assets will leave stale entries behind.
  - Maps are edited in `MapEditorPanel` (card `MAP_EDITOR`). Its grid model is documented at the top of that file.
- File paths: always go through `DataPaths`. Use `DataPaths.userFile(...)` for anything the app writes (saves, exports) and `DataPaths.resource(...)` for bundled read-only data. Never call `Paths.get("file.json")` directly. On desktop these resolve exactly as before; in the browser build they map to CheerpJ's `/files/` (persistent IndexedDB) and the site's `/app/` folder.
- Web version: `web/build.sh` (or `web\build.ps1`) compiles `src/` for Java 11 into `web/dnd_engine.jar` and exports `web/search-index.json`. `.github/workflows/pages.yml` runs it and deploys `web/` on every push to `main`. Keep `src/` compatible with Java 11 (`javac --release 11`), because CheerpJ runs it on a Java 11 runtime.
