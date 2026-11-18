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
  - Tags and campaign content filters live in `Tags.java` (settings are saved inside `custom_assets.json` under `tagSettings`). Every schema gets a `tags` field automatically. When you add a picker that offers rules content (classes, spells, feats, equipment and so on), filter it with `Tags.allowed(category, name)` or `Tags.visibleSpells(...)` so disabled tags are respected. Anything a character already owns stays visible through `Tags.OWNED`. To list new built-in content in the Asset Library, add it to `CustomAssets.builtinNames/builtinSummary/builtinDescription`.
  - Maps are edited in `MapEditorPanel` (card `MAP_EDITOR`). Its grid model is documented at the top of that file. `MapEditorPanel.MapPainter` draws a map (floors, grid, edges, pins, legend) and is shared with the Combat board.
- Combat lives in `CombatPanel` (card `COMBAT`): map picker, token placement, Start Combat setup, and the Current Turn / Initiative Order / Teams / Combat Settings side panel. Placed tokens, team names and the running combat are saved in the Maps asset (`tokens`, `teamNames`, `combat`). The format is documented at the top of `CombatPanel.java`. Tokens refer to characters by their `id` (assigned by `CharacterStorage.loadWithIds`) and to NPCs by asset id. Stats and looks are re-read from the source every time.
- Token looks (`{shape, color, image}`, image = 128×128 Base64 PNG) live in `Tokens.java`, which also has the shared `Tokens.editor(...)` used by the wizard's Name step, the sheet (click the token next to the name) and the NPC schema's `TOKEN` field. Characters store it as `token` in their record, and NPCs as `token` in their asset.
- File paths: always go through `DataPaths`. Use `DataPaths.userFile(...)` for anything the app writes (saves, exports) and `DataPaths.resource(...)` for bundled read-only data. Never call `Paths.get("file.json")` directly. On desktop these resolve exactly as before; in the browser build they map to CheerpJ's `/files/` (persistent IndexedDB) and the site's `/app/` folder.
- Backup & Restore (Export / Import JSON of all characters + homebrew) lives in `Backup.java` and opens from the main menu. In the browser it calls `WebBridge`'s native methods, which `web/index.html` implements in JavaScript (CheerpJ `natives` in `cheerpjInit`). If you change a native's signature, update both files. Desktop uses in-window path fields, never file dialogs. Token pictures use the same bridge (`WebBridge.pickImageFile` / `readClipboardImage`, called through `WebBridge.callAsync`). Those natives downscale in JavaScript first.
- Website: `web/` has six static pages (`index`, `about`, `download`, `privacy`, `terms`, `disclaimer`) sharing `site.css`, `site.js` and a copy of the same header/footer markup. When you add a page or section, add it to the `SITE` list in `site.js` (header search) and to `sitemap.xml`.
- Web version: `web/build.sh` (or `web\build.ps1`) compiles `src/` for Java 11 into `web/dnd_engine.jar` and packages the desktop download `web/dnd_engine-desktop.zip`. `.github/workflows/pages.yml` runs it and deploys `web/` on every push to `main`. Keep `src/` compatible with Java 11 (`javac --release 11`), because CheerpJ runs it on a Java 11 runtime.
