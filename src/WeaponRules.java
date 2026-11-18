import java.util.*;
import java.util.List;

// 2024 weapon rules shared by the character sheet and the Combat screen:
//  - weapon proficiency by class, Weapon Mastery (how many weapons, which ones a class may pick)
//  - the hand system: what each hand holds, and two-handed grips (Versatile / Two-Handed)
//  - building attacks (to-hit, damage dice and bonus, reach / range, ammunition) for characters and NPCs
//
// Saved on the character record (kept on wizard edits by CharacterStorage.carryOverProgression):
//   weaponMasteries : ["Longsword", "Shortbow", ...]   weapons whose Mastery property the character can use
//   hands           : {main: "Longsword", off: "Shield", twoHanded: false}   equipment names ("" = empty hand)
// Characters without "hands" get a default grip from their equipment (see hands()).
class WeaponRules {

static final String[] MASTERY_CLASSES = {"Barbarian", "Fighter", "Paladin", "Ranger", "Rogue"};

static final Map<String, String> MASTERY_INFO = new LinkedHashMap<>();
static {
    MASTERY_INFO.put("Cleave", "If you hit a creature with a melee attack roll using this weapon, you can make a melee attack roll with the weapon against a second creature within 5 feet of the first that is also within your reach. On a hit, the second creature takes the weapon's damage, but don't add your ability modifier to that damage unless that modifier is negative. You can make this extra attack only once per turn.");
    MASTERY_INFO.put("Graze", "If your attack roll with this weapon misses a creature, you can deal damage to that creature equal to the ability modifier you used to make the attack roll. This damage is the same type dealt by the weapon, and the damage can be increased only by increasing the ability modifier.");
    MASTERY_INFO.put("Nick", "When you make the extra attack of the Light property, you can make it as part of the Attack action instead of as a Bonus Action. You can make this extra attack only once per turn.");
    MASTERY_INFO.put("Push", "If you hit a creature with this weapon, you can push the creature up to 10 feet straight away from yourself if it is Large or smaller.");
    MASTERY_INFO.put("Sap", "If you hit a creature with this weapon, that creature has Disadvantage on its next attack roll before the start of your next turn.");
    MASTERY_INFO.put("Slow", "If you hit a creature with this weapon and deal damage to it, you can reduce its Speed by 10 feet until the start of your next turn. If the creature is hit more than once by weapons that have this property, the Speed reduction doesn't exceed 10 feet.");
    MASTERY_INFO.put("Topple", "If you hit a creature with this weapon, you can force the creature to make a Constitution saving throw (DC 8 plus the ability modifier used to make the attack roll and your Proficiency Bonus). On a failed save, the creature has the Prone condition.");
    MASTERY_INFO.put("Vex", "If you hit a creature with this weapon and deal damage to the creature, you have Advantage on your next attack roll against that creature before the end of your next turn.");
}

static final Map<String, String> PROPERTY_INFO = new LinkedHashMap<>();
static {
    PROPERTY_INFO.put("Ammunition", "You can use this weapon to make a ranged attack only if you have ammunition to fire from it. Each attack expends one piece. The weapon's entry lists the type of ammunition and its range (normal / long): beyond normal range you have Disadvantage, and you can't attack beyond long range.");
    PROPERTY_INFO.put("Finesse", "When making an attack with a Finesse weapon, use your choice of your Strength or Dexterity modifier for the attack and damage rolls. You must use the same modifier for both rolls.");
    PROPERTY_INFO.put("Heavy", "You have Disadvantage on attack rolls with a Heavy weapon if it's a Melee weapon and your Strength score isn't at least 13, or if it's a Ranged weapon and your Dexterity score isn't at least 13.");
    PROPERTY_INFO.put("Light", "When you take the Attack action on your turn and attack with a Light weapon, you can make one extra attack as a Bonus Action later on the same turn. That extra attack must be made with a different Light weapon, and you don't add your ability modifier to the extra attack's damage unless that modifier is negative.");
    PROPERTY_INFO.put("Loading", "You can fire only one piece of ammunition from a Loading weapon when you use an action, a Bonus Action, or a Reaction to fire it, regardless of the number of attacks you can normally make.");
    PROPERTY_INFO.put("Range", "A Range weapon has a range in parentheses after the Ammunition or Thrown property: normal range, then long range. Attacking beyond normal range has Disadvantage, and you can't attack beyond long range.");
    PROPERTY_INFO.put("Reach", "A Reach weapon adds 5 feet to your reach when you attack with it, as well as when determining your reach for Opportunity Attacks with it.");
    PROPERTY_INFO.put("Thrown", "If a weapon has the Thrown property, you can throw the weapon to make a ranged attack, and you can draw that weapon as part of the attack. If the weapon is a Melee weapon, use the same ability modifier for the attack and damage rolls that you use for a melee attack with that weapon.");
    PROPERTY_INFO.put("Two-Handed", "A Two-Handed weapon requires two hands when you attack with it.");
    PROPERTY_INFO.put("Versatile", "A Versatile weapon can be used with one or two hands. A damage value in parentheses appears with the property; the weapon deals that damage when used with two hands to make a melee attack.");
    PROPERTY_INFO.put("Reload", "A firearm with Reload can be fired that many times before you must use an action or a Bonus Action to reload it.");
    PROPERTY_INFO.put("Burst Fire", "As an action, you can spray a 10-foot cube within normal range: each creature there must succeed on a DC 15 Dexterity saving throw or take the weapon's normal damage. This uses ten pieces of ammunition.");
}

// ===============================================================
// Proficiency and class features
// ===============================================================
static List<Map<String, Object>> feats(Map<?, ?> rec) {
    return CharacterStorage.recFeats(rec);
}

static int featCount(Map<?, ?> rec, String name) {
    int n = 0;
    for (Map<String, Object> f : feats(rec)) if (name.equalsIgnoreCase(String.valueOf(f.get("name")))) n++;
    return n;
}

// Fighting styles come from the Fighter's level-1 choice or a Fighting Style feat with that name
static boolean hasStyle(Map<?, ?> rec, String style) {
    for (List<String> picks : CharacterStorage.recChoicesMap(rec).values()) {
        for (String p : picks) if (style.equalsIgnoreCase(p)) return true;
    }
    return featCount(rec, style) > 0;
}

static boolean proficient(Map<?, ?> rec, WeaponEntry w) {
    if (w == null) return false;
    for (String cls : CharacterStorage.recClassLevels(rec).keySet()) {
        Map<String, Object> ca = CustomAssets.classAsset(cls);
        if (ca != null) {
            List<String> profs = CustomAssets.list(ca, "weaponProficiencies");
            if (profs.contains("Martial") || (w.simple && profs.contains("Simple"))) return true;
            continue;
        }
        switch (cls) {
            case "Barbarian": case "Fighter": case "Paladin": case "Ranger": return true;
            case "Monk": if (w.simple || w.light()) return true; break;
            case "Rogue": if (w.simple || w.finesse() || w.light()) return true; break;
            case "Custom": return true;
            default: if (w.simple) return true; // Artificer, Bard, Cleric, Druid, Sorcerer, Warlock, Wizard
        }
    }
    return false;
}

// Weapon Mastery count for one class at a level (2024 class tables)
static int masteryForClass(String cls, int level) {
    switch (cls) {
        case "Fighter": return level >= 16 ? 6 : level >= 10 ? 5 : level >= 4 ? 4 : 3;
        case "Barbarian": return level >= 10 ? 4 : level >= 4 ? 3 : 2;
        case "Paladin": case "Ranger": case "Rogue": return 2;
        default: return 0;
    }
}

// Total weapons whose mastery the character can use: the best class count (multiclassing doesn't stack the
// feature) plus one per Weapon Master feat
static int masteryCount(Map<?, ?> rec) {
    int best = 0;
    for (Map.Entry<String, Integer> en : CharacterStorage.recClassLevels(rec).entrySet()) {
        best = Math.max(best, masteryForClass(en.getKey(), en.getValue()));
    }
    return best + featCount(rec, "Weapon Master");
}

static List<String> masterySources(Map<?, ?> rec) {
    List<String> out = new ArrayList<>();
    for (Map.Entry<String, Integer> en : CharacterStorage.recClassLevels(rec).entrySet()) {
        int n = masteryForClass(en.getKey(), en.getValue());
        if (n > 0) out.add(en.getKey() + " " + en.getValue() + ": " + n + " weapons" + masteryLimitText(en.getKey()));
    }
    int wm = featCount(rec, "Weapon Master");
    if (wm > 0) out.add("Weapon Master feat: +" + wm);
    return out;
}

static String masteryLimitText(String cls) {
    switch (cls) {
        case "Barbarian": return " (Simple or Martial Melee weapons)";
        case "Rogue": return " (weapons with Finesse or Light)";
        default: return " (Simple or Martial weapons)";
    }
}

// Can this character pick `w` for Weapon Mastery? (the class limits, and proficiency)
static boolean masteryAllowed(Map<?, ?> rec, WeaponEntry w) {
    if (w == null || "—".equals(w.mastery) || !proficient(rec, w)) return false;
    if (featCount(rec, "Weapon Master") > 0) return true;
    for (Map.Entry<String, Integer> en : CharacterStorage.recClassLevels(rec).entrySet()) {
        if (masteryForClass(en.getKey(), en.getValue()) == 0) continue;
        switch (en.getKey()) {
            case "Barbarian": if (w.melee) return true; break;
            case "Rogue": if (w.finesse() || w.light()) return true; break;
            default: return true;
        }
    }
    return false;
}

static List<String> masteries(Map<?, ?> rec) {
    return CharacterStorage.recList(rec, "weaponMasteries");
}

static boolean hasMastery(Map<?, ?> rec, WeaponEntry w) {
    if (w == null) return false;
    for (String m : masteries(rec)) if (m.equalsIgnoreCase(w.name)) return true;
    return false;
}

// Attacks per Attack action from Extra Attack (2024: Fighter 5 / 11 / 20; Barbarian, Monk, Paladin, Ranger 5)
static int attacksPerAction(Map<?, ?> rec) {
    int best = 1;
    for (Map.Entry<String, Integer> en : CharacterStorage.recClassLevels(rec).entrySet()) {
        int lv = en.getValue();
        switch (en.getKey()) {
            case "Fighter": best = Math.max(best, lv >= 20 ? 4 : lv >= 11 ? 3 : lv >= 5 ? 2 : 1); break;
            case "Barbarian": case "Monk": case "Paladin": case "Ranger": if (lv >= 5) best = Math.max(best, 2); break;
            default:
        }
    }
    return best;
}

// ===============================================================
// Hands
// ===============================================================
static final class Hands {
    String main = "", off = "";
    boolean twoHanded;
    boolean saved; // false = default grip derived from equipment
}

static boolean isShield(String item) {
    GameData.ArmorInfo a = GameData.ARMOR_AC.get(GameData.stripEquipmentCount(item));
    return a != null && a.isShield;
}

static int countOf(List<String> equipment, String base) {
    int n = 0;
    for (String e : equipment) if (GameData.stripEquipmentCount(e).equalsIgnoreCase(base)) n += App.itemCount(e);
    return n;
}

// What the character holds. A saved grip is checked against the equipment (items no longer owned are dropped);
// without one, the first weapon goes in the main hand and a shield (or a second Light weapon) in the off hand.
static Hands hands(Map<?, ?> rec) {
    List<String> equipment = CharacterStorage.recList(rec, "equipment");
    Hands h = new Hands();
    Object o = rec.get("hands");
    if (o instanceof Map) {
        Map<?, ?> m = (Map<?, ?>) o;
        h.saved = true;
        h.main = CustomAssets.str(m, "main");
        h.off = CustomAssets.str(m, "off");
        h.twoHanded = CustomAssets.bool(m, "twoHanded");
        if (!h.main.isEmpty() && countOf(equipment, h.main) == 0) h.main = "";
        if (!h.off.isEmpty() && countOf(equipment, h.off) < (h.off.equalsIgnoreCase(h.main) ? 2 : 1)) h.off = "";
    } else {
        for (String e : equipment) {
            String base = GameData.stripEquipmentCount(e);
            if (WeaponData.find(base) != null) {
                h.main = base;
                break;
            }
        }
        WeaponEntry mw = WeaponData.find(h.main);
        if (mw == null || !mw.twoHanded()) {
            for (String e : equipment) if (isShield(e)) { h.off = GameData.stripEquipmentCount(e); break; }
            if (h.off.isEmpty() && mw != null && mw.light()) {
                for (String e : equipment) {
                    String base = GameData.stripEquipmentCount(e);
                    WeaponEntry w = WeaponData.find(base);
                    if (w != null && w.light() && (!base.equalsIgnoreCase(h.main) || App.itemCount(e) > 1)) { h.off = base; break; }
                }
            }
        }
    }
    WeaponEntry mw = WeaponData.find(h.main);
    if (mw != null && mw.twoHanded()) {
        h.twoHanded = true;
        h.off = "";
    } else if (h.twoHanded && (mw == null || !mw.versatile() || !h.off.isEmpty())) {
        h.twoHanded = false;
    }
    return h;
}

static Map<String, Object> handsJson(Hands h) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("main", h.main);
    m.put("off", h.off);
    m.put("twoHanded", h.twoHanded);
    return m;
}

// AC with the hand system: a shield only counts while it's held
static int armorClass(Map<?, ?> rec) {
    List<String> equipment = new ArrayList<>(CharacterStorage.recList(rec, "equipment"));
    Hands h = hands(rec);
    equipment.removeIf(e -> isShield(e) && !GameData.stripEquipmentCount(e).equalsIgnoreCase(h.off)
            && !GameData.stripEquipmentCount(e).equalsIgnoreCase(h.main));
    int dexMod = GameData.abilityModifier(CharacterStorage.recStatsMap(rec).getOrDefault("DEX", 10));
    return GameData.computeAC(equipment, dexMod);
}

// ===============================================================
// Attacks
// ===============================================================
static final class Attack {
    String name = "", ability = "", damageType = "", source = "", notes = "", mastery = "", ammo = "";
    WeaponEntry weapon;
    int toHit, diceCount, diceSides, damageBonus, reach = 5, rangeNormal, rangeLong, abilityMod;
    boolean melee = true, ranged, offHand, greatWeaponFighting, twoHands, proficient = true, heavyPenalty;
    List<String[]> hitBreakdown = new ArrayList<>(), damageBreakdown = new ArrayList<>();

    String damageText() {
        return WeaponData.diceText(diceCount, diceSides, damageBonus) + " " + damageType.toLowerCase();
    }

    String reachText() {
        List<String> p = new ArrayList<>();
        if (melee) p.add("reach " + reach + " ft.");
        if (rangeNormal > 0) p.add("range " + rangeNormal + "/" + rangeLong + " ft.");
        return String.join(", ", p);
    }

    // Longest distance (ft.) this attack can reach a target at
    int maxDistance() {
        return Math.max(melee ? reach : 0, rangeLong);
    }
}

private static int mod(Map<String, Integer> stats, String key) {
    return GameData.abilityModifier(stats.getOrDefault(key, 10));
}

static String signed(int n) {
    return n >= 0 ? "+" + n : String.valueOf(n);
}

// Every attack the character can make with what's in their hands (plus an Unarmed Strike)
static List<Attack> characterAttacks(Map<?, ?> rec) {
    List<Attack> out = new ArrayList<>();
    Map<String, Integer> stats = CharacterStorage.recStatsMap(rec);
    int pb = GameData.proficiencyBonus(CharacterStorage.recLevel(rec));
    Hands h = hands(rec);
    WeaponEntry main = WeaponData.find(h.main), off = WeaponData.find(h.off);
    boolean dualWielder = featCount(rec, "Dual Wielder") > 0;

    if (main != null) out.add(weaponAttack(rec, stats, pb, main, h.twoHanded, false, off == null && !isShield(h.off) && h.off.isEmpty()));
    if (off != null) {
        // Light property: extra attack with a different Light weapon as a Bonus Action (Dual Wielder: any one-handed melee weapon)
        boolean canBonus = main != null && main.light() && off.light() || dualWielder && main != null && main.light() && off.melee && !off.twoHanded();
        Attack a = weaponAttack(rec, stats, pb, off, false, canBonus, false);
        if (main == null) a.offHand = false; // the only weapon held: a normal attack
        out.add(a);
    }
    // Unarmed Strike: 1 + STR, proficient for everyone
    Attack u = new Attack();
    u.name = "Unarmed Strike";
    u.ability = "STR";
    int str = mod(stats, "STR");
    u.toHit = str + pb;
    u.diceCount = 0;
    u.damageBonus = Math.max(0, 1 + str);
    u.damageType = "Bludgeoning";
    u.source = "Everyone";
    u.hitBreakdown.add(new String[]{"STR modifier", signed(str)});
    u.hitBreakdown.add(new String[]{"Proficiency", signed(pb)});
    u.damageBreakdown.add(new String[]{"Base", "1"});
    u.damageBreakdown.add(new String[]{"STR modifier", signed(str)});
    u.notes = "A punch, kick or headbutt. You can also Grapple or Shove instead of dealing damage.";
    out.add(u);
    return out;
}

static Attack weaponAttack(Map<?, ?> rec, Map<String, Integer> stats, int pb, WeaponEntry w, boolean twoHands, boolean offHand, boolean dueling) {
    Attack a = new Attack();
    a.weapon = w;
    a.name = w.name + (twoHands && w.versatile() ? " (two hands)" : "") + (offHand ? " (off hand)" : "");
    a.melee = w.melee;
    a.ranged = !w.melee || w.thrown();
    a.reach = w.melee ? w.reach : 0;
    a.rangeNormal = w.rangeNormal;
    a.rangeLong = w.rangeLong;
    a.twoHands = twoHands;
    a.offHand = offHand;
    a.damageType = w.damageType;
    a.ammo = w.usesAmmo() ? w.ammunition : "";
    int str = mod(stats, "STR"), dex = mod(stats, "DEX");
    int abil;
    if (w.finesse()) {
        abil = Math.max(str, dex);
        a.ability = dex > str ? "DEX" : "STR";
    } else if (w.melee) {
        abil = str;
        a.ability = "STR";
    } else {
        abil = dex;
        a.ability = "DEX";
    }
    a.proficient = proficient(rec, w);
    a.abilityMod = abil;
    a.toHit = abil + (a.proficient ? pb : 0);
    a.hitBreakdown.add(new String[]{a.ability + " modifier" + (w.finesse() ? " (Finesse: best of STR/DEX)" : ""), signed(abil)});
    a.hitBreakdown.add(new String[]{"Proficiency", a.proficient ? signed(pb) : "not proficient"});
    if (!w.melee && hasStyle(rec, "Archery")) {
        a.toHit += 2;
        a.hitBreakdown.add(new String[]{"Archery fighting style", "+2"});
    }
    if (w.heavy()) {
        int score = stats.getOrDefault(w.melee ? "STR" : "DEX", 10);
        if (score < 13) {
            a.heavyPenalty = true;
            a.hitBreakdown.add(new String[]{"Heavy (" + (w.melee ? "STR" : "DEX") + " below 13)", "Disadvantage"});
        }
    }
    boolean versatileGrip = twoHands && w.versatile();
    a.diceCount = versatileGrip ? w.versCount : w.diceCount;
    a.diceSides = versatileGrip ? w.versSides : w.diceSides;
    int base = a.diceCount > 0 ? 0 : w.damageBonus;
    a.damageBreakdown.add(new String[]{"Weapon dice" + (versatileGrip ? " (Versatile, two hands)" : ""), w.damageText(versatileGrip)});
    int dmgMod = abil;
    if (offHand && abil > 0 && !hasStyle(rec, "Two-Weapon Fighting")) {
        dmgMod = 0;
        a.damageBreakdown.add(new String[]{a.ability + " modifier", "not added (Light extra attack)"});
    } else {
        a.damageBreakdown.add(new String[]{a.ability + " modifier" + (offHand ? " (Two-Weapon Fighting)" : ""), signed(abil)});
    }
    a.damageBonus = base + dmgMod;
    if (dueling && w.melee && !twoHands && !w.twoHanded() && hasStyle(rec, "Dueling")) {
        a.damageBonus += 2;
        a.damageBreakdown.add(new String[]{"Dueling fighting style", "+2"});
    }
    if (w.heavy() && featCount(rec, "Great Weapon Master") > 0) {
        a.damageBonus += pb;
        a.damageBreakdown.add(new String[]{"Great Weapon Master (Heavy weapon)", signed(pb)});
    }
    a.greatWeaponFighting = w.melee && (w.twoHanded() || versatileGrip) && hasStyle(rec, "Great Weapon Fighting");
    if (a.greatWeaponFighting) a.damageBreakdown.add(new String[]{"Great Weapon Fighting", "1s and 2s on damage dice count as 3"});
    if (hasMastery(rec, w)) a.mastery = w.mastery;
    List<String> notes = new ArrayList<>();
    if (offHand) notes.add("Bonus Action extra attack (Light property" + (featCount(rec, "Dual Wielder") > 0 ? " / Dual Wielder" : "") + ").");
    if (w.thrown() && w.melee) notes.add("Can be thrown " + w.rangeNormal + "/" + w.rangeLong + " ft.");
    if (w.has("Loading")) notes.add("Loading: one shot per action.");
    if (!a.proficient) notes.add("Not proficient: no Proficiency Bonus, and no Weapon Mastery.");
    a.notes = String.join(" ", notes);
    a.source = "Held in " + (twoHands ? "both hands" : offHand ? "off hand" : "hand");
    return a;
}

// ---------------- NPC attacks, read from the stat block's Actions ----------------
private static final java.util.regex.Pattern REACH = java.util.regex.Pattern.compile("(?i)reach\\s+(\\d+)\\s*ft");
private static final java.util.regex.Pattern RANGE = java.util.regex.Pattern.compile("(?i)range\\s+(\\d+)(?:\\s*/\\s*(\\d+))?\\s*ft");
private static final java.util.regex.Pattern HIT = java.util.regex.Pattern.compile("(?i)hit:\\s*(\\d+)?\\s*\\(?\\s*(\\d+)d(\\d+)\\s*(?:([+-])\\s*(\\d+))?\\s*\\)?\\s*([a-z]+)\\s+damage");
private static final java.util.regex.Pattern HIT_FLAT = java.util.regex.Pattern.compile("(?i)hit:\\s*(\\d+)\\s+([a-z]+)\\s+damage");
private static final String[] NUMBER_WORDS = {"zero", "one", "two", "three", "four", "five", "six"};

static int crProficiency(String cr) {
    double v;
    try {
        v = cr.contains("/") ? 0 : Double.parseDouble(cr.trim());
    } catch (NumberFormatException ex) {
        v = 0;
    }
    return v >= 29 ? 9 : v >= 25 ? 8 : v >= 21 ? 7 : v >= 17 ? 6 : v >= 13 ? 5 : v >= 9 ? 4 : v >= 5 ? 3 : 2;
}

static List<Attack> npcAttacks(Map<String, Object> npc) {
    List<Attack> out = new ArrayList<>();
    Map<String, Integer> stats = CustomAssets.abilities(npc);
    int pb = crProficiency(CustomAssets.str(npc, "cr"));
    for (Map<String, Object> e : CustomAssets.entries(npc, "actions")) {
        String name = CustomAssets.str(e, "name").trim();
        String desc = CustomAssets.str(e, "description");
        if (name.isEmpty() || name.toLowerCase().startsWith("multiattack")) continue;
        Attack a = parseNpcAction(name, desc);
        WeaponEntry w = WeaponData.find(name);
        if (a == null && w != null) {
            // "Longsword" with no numbers: build it from the NPC's ability scores and CR proficiency
            a = weaponAttack(Collections.singletonMap("classLevels", Collections.singletonMap("Fighter", 1)), stats, pb, w, false, false, false);
            a.name = name;
            a.source = "Stat block (" + w.name + ", CR proficiency " + signed(pb) + ")";
        }
        if (a == null) continue;
        a.weapon = w;
        if (w != null && a.damageType.isEmpty()) a.damageType = w.damageType;
        if (w != null && w.usesAmmo() && a.rangeNormal > 0) a.ammo = w.ammunition; // tracked per token in Combat
        a.notes = desc;
        out.add(a);
    }
    if (out.isEmpty()) {
        Attack u = new Attack();
        u.name = "Unarmed Strike";
        int str = mod(stats, "STR");
        u.ability = "STR";
        u.toHit = str + pb;
        u.damageBonus = Math.max(0, 1 + str);
        u.damageType = "Bludgeoning";
        u.source = "No attacks in the stat block";
        u.hitBreakdown.add(new String[]{"STR modifier", signed(str)});
        u.hitBreakdown.add(new String[]{"Proficiency (CR)", signed(pb)});
        u.damageBreakdown.add(new String[]{"1 + STR modifier", String.valueOf(u.damageBonus)});
        out.add(u);
    }
    return out;
}

// "Melee Attack Roll: +4, reach 5 ft. Hit: 5 (1d6 + 2) Slashing damage." (2024) or the 2014 "Melee Weapon Attack: +4 to hit"
static Attack parseNpcAction(String name, String desc) {
    String d = desc == null ? "" : desc;
    boolean isAttack = d.toLowerCase().contains("attack");
    java.util.regex.Matcher hit = HIT.matcher(d), flat = HIT_FLAT.matcher(d);
    boolean dice = hit.find();
    if (!isAttack || (!dice && !flat.find())) return null;
    java.util.regex.Matcher th = java.util.regex.Pattern.compile("(?i)attack(?: roll)?:?\\s*([+-]\\s*\\d+)").matcher(d);
    if (!th.find()) {
        th = java.util.regex.Pattern.compile("([+-]\\s*\\d+)\\s*to hit").matcher(d);
        if (!th.find()) return null;
    }
    Attack a = new Attack();
    a.name = name;
    a.toHit = Integer.parseInt(th.group(1).replace(" ", ""));
    a.hitBreakdown.add(new String[]{"Stat block", signed(a.toHit)});
    String lower = d.toLowerCase();
    a.melee = lower.contains("melee");
    a.ranged = lower.contains("ranged");
    java.util.regex.Matcher r = REACH.matcher(d);
    a.reach = r.find() ? Integer.parseInt(r.group(1)) : (a.melee ? 5 : 0);
    java.util.regex.Matcher rg = RANGE.matcher(d);
    if (rg.find()) {
        a.rangeNormal = Integer.parseInt(rg.group(1));
        a.rangeLong = rg.group(2) != null ? Integer.parseInt(rg.group(2)) : a.rangeNormal;
        a.ranged = true;
    }
    if (!a.melee && !a.ranged) a.melee = true;
    if (dice) {
        a.diceCount = Integer.parseInt(hit.group(2));
        a.diceSides = Integer.parseInt(hit.group(3));
        a.damageBonus = hit.group(5) == null ? 0 : Integer.parseInt(hit.group(5)) * ("-".equals(hit.group(4)) ? -1 : 1);
        a.damageType = cap(hit.group(6));
    } else {
        a.damageBonus = Integer.parseInt(flat.group(1));
        a.damageType = cap(flat.group(2));
    }
    a.damageBreakdown.add(new String[]{"Stat block", WeaponData.diceText(a.diceCount, a.diceSides, a.damageBonus)});
    a.source = "Stat block action";
    return a;
}

private static String cap(String s) {
    return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
}

// "Multiattack: The goblin makes two attacks" -> 2
static int npcAttacksPerAction(Map<String, Object> npc) {
    for (Map<String, Object> e : CustomAssets.entries(npc, "actions")) {
        String text = (CustomAssets.str(e, "name") + " " + CustomAssets.str(e, "description")).toLowerCase();
        if (!text.contains("multiattack")) continue;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("makes (\\w+)").matcher(text);
        if (m.find()) {
            String w = m.group(1);
            for (int i = 1; i < NUMBER_WORDS.length; i++) if (NUMBER_WORDS[i].equals(w)) return i;
            try {
                return Math.max(1, Integer.parseInt(w));
            } catch (NumberFormatException ignored) {
                return 2;
            }
        }
        return 2;
    }
    return 1;
}

// ===============================================================
// Dice
// ===============================================================
// Rolls `count` d`sides`; with Great Weapon Fighting a 1 or 2 counts as 3. Returns the kept values.
static int[] rollDice(Random rng, int count, int sides, boolean gwf) {
    int[] out = new int[Math.max(0, count)];
    for (int i = 0; i < out.length; i++) {
        int v = sides > 0 ? rng.nextInt(sides) + 1 : 0;
        out[i] = gwf && v < 3 ? 3 : v;
    }
    return out;
}
}
