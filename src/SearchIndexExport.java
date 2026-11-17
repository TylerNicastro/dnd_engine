import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

// Build-time tool (run by web/build.sh): writes the built-in rules to a JSON search index for the
// website's search bar, so the site and the app always describe the same rules.
//   java -cp <classes> SearchIndexExport web/search-index.json
// Entries: {"t": title, "c": category, "s": short subtitle, "d": description}. Homebrew is not included
// (it lives in each user's own browser storage).
class SearchIndexExport {

private static final List<Map<String, Object>> OUT = new ArrayList<>();

private static void add(String category, String title, String subtitle, String description) {
    if (title == null || title.isEmpty() || "Custom".equals(title)) return;
    Map<String, Object> e = new LinkedHashMap<>();
    e.put("t", title);
    e.put("c", category);
    e.put("s", subtitle == null ? "" : subtitle);
    e.put("d", description == null ? "" : description);
    OUT.add(e);
}

public static void main(String[] args) throws Exception {
    String out = args.length > 0 ? args[0] : "search-index.json";
    SpellData.loadExtraSpells();
    WeaponData.WEAPON_DB = WeaponData.loadWeaponDatabase();

    String[] statNames = {"Strength", "Dexterity", "Constitution", "Intelligence", "Wisdom", "Charisma"};
    for (int i = 0; i < GameData.STAT_ORDER.length; i++) {
        String s = GameData.STAT_ORDER[i];
        add("Rules", statNames[i] + " (" + s + ")", "Ability score", GameData.STAT_INFO.get(s));
    }
    for (Map.Entry<String, String> en : GameData.GEN_TYPE_INFO.entrySet()) {
        add("Rules", en.getKey(), "Ability score generation", en.getValue());
    }
    add("Rules", "Proficiency Bonus", "Character level", "Added to attack rolls, saving throws, and skill checks you're proficient in: +2 at levels 1-4, +3 at 5-8, +4 at 9-12, +5 at 13-16, +6 at 17-20.");
    add("Rules", FeatData.ASI_NAME, "Feat", FeatData.ASI_DESCRIPTION);

    for (String skill : GameData.ALL_SKILLS) {
        add("Skills", skill, GameData.SKILL_ABILITY.get(skill) + " skill", GameData.SKILL_INFO.get(skill));
    }
    for (Map.Entry<String, String> en : GameData.CONDITION_INFO.entrySet()) {
        add("Conditions", en.getKey(), "Conscious".equals(en.getKey()) || "Unconscious".equals(en.getKey()) ? "State" : "Condition", en.getValue());
    }
    for (Map.Entry<String, String[]> en : GameData.CLASS_INFO.entrySet()) {
        String[] saves = GameData.CLASS_SAVING_THROWS.get(en.getKey());
        String sub = "Class · Hit die " + en.getValue()[0] + (saves != null ? " · Saves " + String.join(", ", saves) : "");
        String caster = SpellData.CASTER_TYPE.get(en.getKey());
        String desc = en.getValue()[1] + (caster != null ? "\n\nSpellcasting: " + caster.charAt(0) + caster.substring(1).toLowerCase()
                + " caster (" + SpellData.CASTING_ABILITY.get(en.getKey()) + ")." : "");
        add("Classes", en.getKey(), sub, desc);
    }
    for (Map.Entry<String, String> en : GameData.SPECIES_INFO.entrySet()) {
        String[] lin = GameData.SPECIES_LINEAGES.get(en.getKey());
        String sub = "Species · " + GameData.computeSize(en.getKey()) + " · Speed " + GameData.computeSpeed(en.getKey(), "") + " ft.";
        add("Species", en.getKey(), sub, en.getValue() + (lin != null ? "\n\nLineages: " + String.join(", ", lin) : ""));
    }
    for (Map.Entry<String, String> en : GameData.BACKGROUND_INFO.entrySet()) {
        String[] skills = GameData.BACKGROUND_SKILL_PROFICIENCIES.get(en.getKey());
        String[] stats = GameData.BACKGROUND_STATS.get(en.getKey());
        String sub = "Background" + (skills != null ? " · " + String.join(", ", skills) : "");
        add("Backgrounds", en.getKey(), sub, en.getValue() + (stats != null ? "\n\nAbility scores: " + String.join(", ", stats) : ""));
    }
    for (Map.Entry<String, String> en : GameData.LANGUAGE_INFO.entrySet()) add("Languages", en.getKey(), "Language", en.getValue());

    for (Spell s : SpellData.SPELLS.values()) {
        StringBuilder d = new StringBuilder();
        d.append("Casting Time: ").append(s.castingTime).append("\nRange: ").append(s.range)
                .append("\nComponents: ").append(s.components).append("\nDuration: ").append(s.duration);
        if (!s.description.isEmpty()) d.append("\n\n").append(s.description);
        if (!s.higherLevels.isEmpty()) d.append("\n\n").append(s.level == 0 ? "Cantrip Upgrade: " : "Using a Higher-Level Spell Slot: ").append(s.higherLevels);
        if (!s.classes.isEmpty()) d.append("\n\nClasses: ").append(String.join(", ", s.classes));
        add("Spells", s.name, s.levelLine() + (s.ritual ? " (Ritual)" : "") + (s.concentration ? " · Concentration" : ""), d.toString());
    }

    for (Map.Entry<String, String> en : CustomAssets.ORIGIN_FEATS.entrySet()) add("Feats", en.getKey(), "Origin feat", en.getValue());
    for (Feat f : FeatData.GENERAL_FEATS.values()) add("Feats", f.name, "General feat", f.infoText());
    for (Feat f : FeatData.EPIC_BOONS.values()) add("Feats", f.name, "Epic boon", f.infoText());

    for (WeaponEntry w : WeaponData.WEAPON_DB.weapons) {
        add("Items", w.name, w.category + " weapon · " + w.damage,
                "Damage: " + w.damage + "\nProperties: " + (w.properties.isEmpty() ? "—" : String.join(", ", w.properties))
                        + "\nMastery: " + w.mastery + "\nWeight: " + w.weight + "   Cost: " + w.cost);
    }
    for (Map.Entry<String, GameData.ArmorInfo> en : GameData.ARMOR_AC.entrySet()) {
        GameData.ArmorInfo a = en.getValue();
        String ac = a.isShield ? "+" + GameData.shieldBonus(a) + " AC"
                : "AC " + a.baseAC + (a.maxDexBonus <= 0 ? "" : a.maxDexBonus == Integer.MAX_VALUE ? " + DEX" : " + DEX (max " + a.maxDexBonus + ")");
        add("Items", en.getKey(), (a.isShield ? "Shield" : "Armor") + " · " + ac, (a.isShield ? "Shield" : "Armor") + ". " + ac + ".");
    }
    Set<String> seen = new HashSet<>();
    for (Map<String, Object> e : OUT) seen.add(String.valueOf(e.get("t")).toLowerCase());
    for (Map.Entry<String, String> en : App.ITEM_PRESETS.entrySet()) {
        if (seen.contains(en.getKey().toLowerCase())) continue; // weapons/armor already listed with full stats
        add("Items", en.getKey(), "Equipment", en.getValue().replace(" Placeholder description.", ""));
    }

    Map<String, Object> root = new LinkedHashMap<>();
    root.put("generated", "Built from the D&D Engine rules data");
    root.put("count", OUT.size());
    root.put("entries", OUT);
    Files.write(Paths.get(out), JsonUtil.toJson(root).getBytes(StandardCharsets.UTF_8));
    System.out.println("Wrote " + OUT.size() + " search entries to " + out);
}
}
