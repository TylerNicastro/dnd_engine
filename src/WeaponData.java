import java.util.*;
import java.util.List;

// Weapon data, loaded from weapons.json
class WeaponData {

// Loaded from weapons.json at startup
static WeaponDatabase WEAPON_DB = new WeaponDatabase();

static WeaponDatabase loadWeaponDatabase() {
    WeaponDatabase db = new WeaponDatabase();
    try {
        String content = new String(
                java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("weapons.json")),
                java.nio.charset.StandardCharsets.UTF_8);
        Object parsed = new MiniJsonParser(content).parse();
        if (parsed instanceof Map) {
            Map<?, ?> root = (Map<?, ?>) parsed;
            Object arr = root.get("weapons");
            if (arr instanceof List) {
                for (Object o : (List<?>) arr) {
                    if (!(o instanceof Map)) continue;
                    Map<?, ?> m = (Map<?, ?>) o;
                    WeaponEntry we = new WeaponEntry();
                    we.name = String.valueOf(m.get("name"));
                    we.category = String.valueOf(m.get("category"));
                    we.cost = m.get("cost") == null ? "\u2014" : String.valueOf(m.get("cost"));
                    we.damage = String.valueOf(m.get("damage"));
                    we.weight = String.valueOf(m.get("weight"));
                    we.mastery = m.get("mastery") == null ? "\u2014" : String.valueOf(m.get("mastery"));
                    we.properties = new ArrayList<>();
                    Object props = m.get("properties");
                    if (props instanceof List) {
                        for (Object p : (List<?>) props) we.properties.add(String.valueOf(p));
                    }
                    db.weapons.add(we);
                }
            }
        }
    } catch (Exception ex) {
        // weapons.json missing or unreadable; app continues with empty weapon list
    }
    return db;
}


static String describeEquipmentPackageA(EquipmentPackage pkg) {
    StringBuilder sb = new StringBuilder();
    for (String item : pkg.fixedItems) sb.append("\u2022 ").append(item).append("\n");
    for (EquipmentChoice c : pkg.choices) sb.append("\u2022 ").append(c.label).append(" (choose ").append(c.count).append(")\n");
    sb.append("\nPlus ").append(pkg.gold).append(" GP");
    return sb.toString().trim();
}

static List<String> weaponsInPool(String poolPrefix) {
    List<String> names = new ArrayList<>();
    for (WeaponEntry we : WEAPON_DB.weapons) {
        if (we.category != null && we.category.startsWith(poolPrefix)) names.add(we.name);
    }
    return names;
}

static Map<String, String> weaponInfoMap(List<String> names) {
    Map<String, String> info = new LinkedHashMap<>();
    for (String n : names) {
        for (WeaponEntry we : WEAPON_DB.weapons) {
            if (we.name.equals(n)) {
                info.put(n, "Cost: " + we.cost + "\nDamage: " + we.damage + "\nWeight: " + we.weight
                        + "\nProperties: " + (we.properties.isEmpty() ? "\u2014" : String.join(", ", we.properties))
                        + "\nMastery: " + we.mastery);
                break;
            }
        }
    }
    return info;
}

}

class WeaponEntry {
    String name, category, cost, damage, weight, mastery;
    List<String> properties = new ArrayList<>();
}


class WeaponDatabase {
    List<WeaponEntry> weapons = new ArrayList<>();
}

