import java.util.*;
import java.util.List;

// Weapon and ammunition data, loaded from weapons.json (2024 rules; the file's "format" note documents the fields).
// Numbers are stored as numbers: damage dice {count, die}, range {normal, long}, reach in feet, cost {amount, unit}.
// The display strings on WeaponEntry (damage, cost, weight, property labels) are derived from those fields.
// Ammunition (Arrow, Bolt, ...) is an Item, not a weapon: one unit per definition, registered in App.ITEM_PRESETS.
class WeaponData {

// Loaded from weapons.json at startup
static WeaponDatabase WEAPON_DB = new WeaponDatabase();
static final List<AmmoItem> AMMO = new ArrayList<>();

static WeaponDatabase loadWeaponDatabase() {
    WeaponDatabase db = new WeaponDatabase();
    AMMO.clear();
    try {
        String content = new String(
                java.nio.file.Files.readAllBytes(DataPaths.resource("weapons.json")),
                java.nio.charset.StandardCharsets.UTF_8);
        Object parsed = new MiniJsonParser(content).parse();
        if (parsed instanceof Map) {
            Map<?, ?> root = (Map<?, ?>) parsed;
            Object arr = root.get("weapons");
            if (arr instanceof List) {
                for (Object o : (List<?>) arr) if (o instanceof Map) db.weapons.add(fromJson((Map<?, ?>) o));
            }
            Object ammo = root.get("ammunition");
            if (ammo instanceof List) {
                for (Object o : (List<?>) ammo) {
                    if (!(o instanceof Map)) continue;
                    Map<?, ?> m = (Map<?, ?>) o;
                    AmmoItem a = new AmmoItem();
                    a.name = str(m, "name");
                    a.usedBy = str(m, "usedBy");
                    a.storage = str(m, "storage");
                    a.soldAs = str(m, "soldAs");
                    a.source = str(m, "source");
                    a.weightLb = num(m.get("weight"), 0);
                    a.cost = costText(m.get("cost"));
                    AMMO.add(a);
                }
            }
        }
    } catch (Exception ex) {
        // weapons.json missing or unreadable; app continues with empty weapon list
    }
    App.ITEM_PRESETS.remove("Arrows");
    for (AmmoItem a : AMMO) App.ITEM_PRESETS.put(a.name, a.description());
    return db;
}

private static WeaponEntry fromJson(Map<?, ?> m) {
    WeaponEntry we = new WeaponEntry();
    we.name = str(m, "name");
    we.simple = !"Martial".equalsIgnoreCase(str(m, "category"));
    we.melee = !"Ranged".equalsIgnoreCase(str(m, "type"));
    we.category = (we.simple ? "Simple" : "Martial") + " " + (we.melee ? "Melee" : "Ranged");
    if (m.get("damage") instanceof Map) {
        Map<?, ?> d = (Map<?, ?>) m.get("damage");
        we.diceCount = (int) num(d.get("count"), 0);
        we.diceSides = (int) num(d.get("die"), 0);
        we.damageBonus = (int) num(d.get("bonus"), 0);
        we.damageType = str(d, "type");
    }
    if (m.get("versatile") instanceof Map) {
        Map<?, ?> v = (Map<?, ?>) m.get("versatile");
        we.versCount = (int) num(v.get("count"), 0);
        we.versSides = (int) num(v.get("die"), 0);
    }
    if (m.get("range") instanceof Map) {
        Map<?, ?> r = (Map<?, ?>) m.get("range");
        we.rangeNormal = (int) num(r.get("normal"), 0);
        we.rangeLong = (int) num(r.get("long"), 0);
    }
    we.reach = we.melee ? (int) num(m.get("reach"), 5) : 0;
    we.ammunition = str(m, "ammunition");
    we.reload = (int) num(m.get("reload"), 0);
    we.group = str(m, "group");
    we.source = str(m, "source");
    we.note = str(m, "note");
    we.weightLb = num(m.get("weight"), 0);
    we.costCp = costCp(m.get("cost"));
    we.cost = costText(m.get("cost"));
    String mastery = str(m, "mastery");
    we.mastery = mastery.isEmpty() ? "—" : mastery;
    Object props = m.get("properties");
    if (props instanceof List) for (Object p : (List<?>) props) we.rawProperties.add(String.valueOf(p));
    we.refreshDisplay();
    return we;
}

static String str(Map<?, ?> m, String k) {
    Object v = m.get(k);
    return v == null || "null".equals(String.valueOf(v)) ? "" : String.valueOf(v);
}

static double num(Object v, double def) {
    if (v instanceof Number) return ((Number) v).doubleValue();
    try {
        return v == null ? def : Double.parseDouble(String.valueOf(v).trim());
    } catch (NumberFormatException ex) {
        return def;
    }
}

private static final String[] COIN_UNITS = {"cp", "sp", "ep", "gp", "pp"};
private static final int[] COIN_CP = {1, 10, 50, 100, 1000};

static String costText(Object cost) {
    if (!(cost instanceof Map)) return "—";
    Map<?, ?> c = (Map<?, ?>) cost;
    double amt = num(c.get("amount"), 0);
    String a = amt == Math.rint(amt) ? String.valueOf((long) amt) : String.valueOf(amt);
    return a + " " + str(c, "unit").toUpperCase();
}

static double costCp(Object cost) {
    if (!(cost instanceof Map)) return -1;
    Map<?, ?> c = (Map<?, ?>) cost;
    int i = Arrays.asList(COIN_UNITS).indexOf(str(c, "unit").toLowerCase());
    return num(c.get("amount"), 0) * (i >= 0 ? COIN_CP[i] : 1);
}

static String weightText(double lb) {
    if (lb <= 0) return "—";
    if (lb == 0.25) return "1/4 lb.";
    if (lb == 0.5) return "1/2 lb.";
    if (lb == Math.rint(lb)) return (long) lb + " lb.";
    return lb + " lb.";
}

static String diceText(int count, int sides, int bonus) {
    if (count <= 0 || sides <= 0) return String.valueOf(bonus);
    return count + "d" + sides + (bonus > 0 ? " + " + bonus : bonus < 0 ? " - " + (-bonus) : "");
}

// "Crossbow, Light" (old name) -> "Light Crossbow"; "Daggers" -> "Dagger"
private static List<String> nameVariants(String name) {
    List<String> out = new ArrayList<>();
    String n = GameData.stripEquipmentCount(name == null ? "" : name).trim();
    out.add(n);
    int comma = n.indexOf(',');
    if (comma > 0) out.add(n.substring(comma + 1).trim() + " " + n.substring(0, comma).trim());
    for (String s : new ArrayList<>(out)) if (s.endsWith("s")) out.add(s.substring(0, s.length() - 1));
    return out;
}

static WeaponEntry find(String name) {
    for (String v : nameVariants(name)) {
        for (WeaponEntry we : WEAPON_DB.weapons) if (we.name.equalsIgnoreCase(v)) return we;
    }
    return null;
}

// Ammunition item for an equipment entry ("Arrow (20)", old "Arrows (20)" or "Crossbow bolts (20)"), or null
static AmmoItem findAmmo(String name) {
    for (String v : nameVariants(name)) {
        String s = v.replaceFirst("(?i)^crossbow\\s+", "");
        for (AmmoItem a : AMMO) if (a.name.equalsIgnoreCase(s)) return a;
    }
    return null;
}

static String describeEquipmentPackageA(EquipmentPackage pkg) {
    StringBuilder sb = new StringBuilder();
    for (String item : pkg.fixedItems) sb.append("• ").append(item).append("\n");
    for (EquipmentChoice c : pkg.choices) sb.append("• ").append(c.label).append(" (choose ").append(c.count).append(")\n");
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
        WeaponEntry we = find(n);
        if (we != null) info.put(n, we.infoText());
    }
    return info;
}

}

class WeaponEntry {
    // display strings (derived by refreshDisplay)
    String name, category, cost = "—", damage = "", weight = "—", mastery = "—";
    List<String> properties = new ArrayList<>();
    // structured values
    boolean simple = true, melee = true;
    int diceCount, diceSides, damageBonus, versCount, versSides, rangeNormal, rangeLong, reach = 5, reload;
    String damageType = "", ammunition = "", group = "", source = "", note = "";
    double weightLb, costCp = -1;
    List<String> rawProperties = new ArrayList<>(); // bare property names: "Thrown", "Versatile", "Two-Handed"...

    boolean has(String prop) {
        for (String p : rawProperties) if (p.equalsIgnoreCase(prop)) return true;
        return false;
    }

    boolean twoHanded() { return has("Two-Handed"); }
    boolean versatile() { return has("Versatile") && versSides > 0; }
    boolean light() { return has("Light"); }
    boolean finesse() { return has("Finesse"); }
    boolean heavy() { return has("Heavy"); }
    boolean thrown() { return has("Thrown") && rangeNormal > 0; }
    boolean usesAmmo() { return has("Ammunition") && !ammunition.isEmpty(); }

    String damageText(boolean twoHands) {
        if (twoHands && versatile()) return WeaponData.diceText(versCount, versSides, 0);
        return WeaponData.diceText(diceCount, diceSides, diceCount > 0 ? 0 : damageBonus);
    }

    void refreshDisplay() {
        damage = (damageText(false) + " " + damageType.toLowerCase()).trim();
        weight = WeaponData.weightText(weightLb);
        properties = new ArrayList<>();
        for (String p : rawProperties) {
            if (("Thrown".equalsIgnoreCase(p) || "Range".equalsIgnoreCase(p)) && rangeNormal > 0) properties.add(p + " (range " + rangeNormal + "/" + rangeLong + ")");
            else if ("Ammunition".equalsIgnoreCase(p) && rangeNormal > 0) properties.add(p + " (range " + rangeNormal + "/" + rangeLong + (ammunition.isEmpty() ? "" : "; " + ammunition) + ")");
            else if ("Versatile".equalsIgnoreCase(p) && versSides > 0) properties.add(p + " (" + WeaponData.diceText(versCount, versSides, 0) + ")");
            else if ("Reload".equalsIgnoreCase(p) && reload > 0) properties.add(p + " (" + reload + " shots)");
            else if ("Reach".equalsIgnoreCase(p)) properties.add(p + " (" + reach + " ft.)");
            else properties.add(p);
        }
    }

    String infoText() {
        return category + " weapon" + (group.isEmpty() ? "" : " (" + group + ")") + ".\nDamage: " + damage
                + (versatile() ? " (" + damageText(true) + " with two hands)" : "")
                + "\nProperties: " + (properties.isEmpty() ? "—" : String.join(", ", properties))
                + "\nMastery: " + mastery + "\nWeight: " + weight + "   Cost: " + cost
                + (note.isEmpty() ? "" : "\n" + note) + (source.isEmpty() ? "" : "\nSource: " + source);
    }
}

// One unit of ammunition (an Item). Equipment lists hold the quantity, e.g. "Arrow (20)".
class AmmoItem {
    String name = "", usedBy = "", storage = "", soldAs = "", source = "", cost = "—";
    double weightLb;

    String description() {
        return "Ammunition (one " + name.toLowerCase() + "). Used by: " + usedBy + ". "
                + "A weapon with the Ammunition property uses one each time you attack with it; after a fight you can "
                + "recover half the expended ammunition (round down) with a minute of searching."
                + "\nWeight: " + (weightLb > 0 ? weightLb + " lb." : "—") + "   Cost: " + cost + " each"
                + (soldAs.isEmpty() ? "" : "\nUsually sold as " + soldAs)
                + (storage.isEmpty() ? "" : "\nStored in a " + storage.toLowerCase() + ".")
                + (source.isEmpty() ? "" : "\nSource: " + source);
    }
}

class WeaponDatabase {
    List<WeaponEntry> weapons = new ArrayList<>();
}
