import java.util.*;
import java.util.List;

// Combat-only rules for conditions and usable items (2024 rules). Nothing here touches character sheets:
// the Combat screen keeps its own per-token copies (see CombatPanel's saved token state).
class CombatConditions {

private static boolean has(List<String> conds, String c) {
    for (String s : conds) if (s.equalsIgnoreCase(c)) return true;
    return false;
}

// Can't take actions, Bonus Actions or Reactions
static boolean incapacitated(List<String> conds) {
    return has(conds, "Incapacitated") || has(conds, "Paralyzed") || has(conds, "Petrified")
            || has(conds, "Stunned") || has(conds, "Unconscious");
}

static String incapacitatedBy(List<String> conds) {
    for (String c : new String[]{"Unconscious", "Paralyzed", "Petrified", "Stunned", "Incapacitated"}) if (has(conds, c)) return c;
    return "";
}

// Speed is 0
static String speedZeroBy(List<String> conds) {
    for (String c : new String[]{"Grappled", "Restrained", "Paralyzed", "Petrified", "Stunned", "Unconscious"}) if (has(conds, c)) return c;
    return "";
}

// Attack roll effects. Adds explanations to `notes`; returns {advantage, disadvantage, autoCrit} as 0/1.
static int[] attackEffects(List<String> attacker, List<String> target, int distanceFt, List<String> notes) {
    boolean adv = false, dis = false, crit = false;
    if (has(attacker, "Invisible")) { adv = true; notes.add("You're Invisible: Advantage."); }
    if (has(attacker, "Blinded")) { dis = true; notes.add("You're Blinded: Disadvantage."); }
    if (has(attacker, "Frightened")) { dis = true; notes.add("You're Frightened: Disadvantage while the source of your fear is in sight."); }
    if (has(attacker, "Poisoned")) { dis = true; notes.add("You're Poisoned: Disadvantage."); }
    if (has(attacker, "Prone")) { dis = true; notes.add("You're Prone: Disadvantage."); }
    if (has(attacker, "Restrained")) { dis = true; notes.add("You're Restrained: Disadvantage."); }
    if (has(attacker, "Exhaustion")) notes.add("You have Exhaustion: subtract 2 × your Exhaustion level from the roll.");

    if (has(target, "Invisible")) { dis = true; notes.add("Target is Invisible: Disadvantage."); }
    for (String c : new String[]{"Blinded", "Paralyzed", "Petrified", "Restrained", "Stunned", "Unconscious"}) {
        if (has(target, c)) { adv = true; notes.add("Target is " + c + ": Advantage."); }
    }
    if (has(target, "Prone")) {
        if (distanceFt <= 5) { adv = true; notes.add("Target is Prone and within 5 ft.: Advantage."); }
        else { dis = true; notes.add("Target is Prone and more than 5 ft. away: Disadvantage."); }
    }
    if ((has(target, "Paralyzed") || has(target, "Unconscious")) && distanceFt <= 5) {
        crit = true;
        notes.add("Target is " + (has(target, "Paralyzed") ? "Paralyzed" : "Unconscious") + " and within 5 ft.: a hit is a Critical Hit.");
    }
    return new int[]{adv ? 1 : 0, dis ? 1 : 0, crit ? 1 : 0};
}

// Conditions offered in Combat: the sheet's presets plus homebrew conditions, respecting tag filters
static List<String> options() {
    List<String> out = new ArrayList<>(Arrays.asList(App.CONDITION_PRESETS));
    if (!out.contains("Unconscious")) out.add("Unconscious");
    for (String hb : CustomAssets.conditionNames()) if (!out.contains(hb)) out.add(hb);
    out.removeIf(c -> !Tags.allowed(CustomAssets.CONDITIONS, c));
    return out;
}

static String info(String c) {
    String s = GameData.CONDITION_INFO.get(c);
    return s == null || s.isEmpty() ? "A condition tracked for this encounter." : s;
}
}

// Items usable in Combat: healing potions (Bonus Action, roll to heal) and anything else (the Utilize action)
class CombatItems {

// Healing potions (2024): {count, die, bonus}
static final Map<String, int[]> POTIONS = new LinkedHashMap<>();
static {
    POTIONS.put("Potion of Healing", new int[]{2, 4, 2});
    POTIONS.put("Potion of Greater Healing", new int[]{4, 4, 4});
    POTIONS.put("Potion of Superior Healing", new int[]{8, 4, 8});
    POTIONS.put("Potion of Supreme Healing", new int[]{10, 4, 20});
    POTIONS.put("Goodberry", new int[]{0, 1, 1}); // Goodberry spell: eating one (Bonus Action) restores 1 HP
}

static int[] potion(String item) {
    String base = GameData.stripEquipmentCount(item);
    for (Map.Entry<String, int[]> en : POTIONS.entrySet()) {
        if (en.getKey().equalsIgnoreCase(base) || (en.getKey() + "s").equalsIgnoreCase(base)) return en.getValue();
    }
    return null;
}

// Single-use items that are used up when used
private static final String[] CONSUMABLE_WORDS = {"potion", "oil", "antitoxin", "holy water", "alchemist", "acid", "scroll",
        "ration", "torch", "caltrops", "ball bearings", "healer's kit", "candle", "tinderbox", "elixir", "philter", "dust", "bead"};

static boolean consumable(String item) {
    String l = item.toLowerCase();
    for (String w : CONSUMABLE_WORDS) if (l.contains(w)) return true;
    return false;
}

// Magic items (used with the Magic action; potions with a Bonus Action): healing potions, homebrew items with a
// rarity other than Mundane, items whose description gives a rarity ("Potion (Common)"), and well-known names
private static final String[] MAGIC_WORDS = {"potion", "scroll", "wand", "staff of", "rod of", "ring of", "amulet", "cloak of", "boots of",
        "bag of", "elixir", "philter", "oil of", "dust of", "bead of", "figurine", "horn of", "ioun", "necklace of", "gem of", "goodberry",
        "+1", "+2", "+3", "periapt", "brooch", "gloves of", "helm of", "bracers of", "belt of", "circlet", "talisman", "orb of", "crystal ball",
        "decanter", "immovable rod", "lantern of revealing", "feather token", "quiver of", "pearl of", "stone of", "deck of", "carpet of", "broom of"};
private static final java.util.regex.Pattern RARITY_TEXT = java.util.regex.Pattern.compile("\\((Common|Uncommon|Rare|Very Rare|Legendary|Artifact)\\b");

static boolean magic(String item) {
    if (potion(item) != null) return true;
    String base = GameData.stripEquipmentCount(item);
    Map<String, Object> hb = CustomAssets.findByName(CustomAssets.ITEMS, base);
    if (hb != null) {
        String r = CustomAssets.str(hb, "rarity");
        return !r.isEmpty() && !"Mundane".equals(r);
    }
    String desc = App.itemDescription(base);
    if (desc != null && RARITY_TEXT.matcher(desc).find()) return true;
    String l = base.toLowerCase();
    for (String w : MAGIC_WORDS) if (l.contains(w)) return true;
    return false;
}

// Equipment entries that are usable items in Combat (weapons, armor, shields and ammunition are handled elsewhere)
static Map<String, Integer> fromEquipment(List<String> equipment) {
    Map<String, Integer> out = new LinkedHashMap<>();
    for (String e : equipment) {
        String base = GameData.stripEquipmentCount(e);
        if (base.isEmpty() || WeaponData.find(base) != null || WeaponData.findAmmo(base) != null || GameData.ARMOR_AC.get(base) != null) continue;
        out.merge(base, App.itemCount(e), Integer::sum);
    }
    return out;
}
}
