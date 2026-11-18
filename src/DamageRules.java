import java.util.*;

// Damage vulnerability / resistance / immunity (2024 rules), used by the Combat screen.
//
// NPCs get them from their traits: a trait entry can carry
//   traitType  : "Trait" (plain text) | "Damage Vulnerability" | "Damage Resistance" | "Damage Immunity"
//   damageType : one of CustomAssets.DAMAGE_TYPES, or ALL_DAMAGE
// Combat can add per-token adjustments on top (saved with the map, never on the character sheet).
//
// A creature's modifiers are a bit mask per damage type: RESIST | VULN | IMMUNE.
// Applying damage: immunity -> 0; otherwise resistance halves (round down), then vulnerability doubles.
// Several sources of the same modifier count once.
class DamageRules {

static final String TRAIT = "Trait", VULNERABILITY = "Damage Vulnerability", RESISTANCE = "Damage Resistance", IMMUNITY = "Damage Immunity";
static final String[] TRAIT_TYPES = {TRAIT, VULNERABILITY, RESISTANCE, IMMUNITY};
static final String ALL_DAMAGE = "All damage";
static final int RESIST = 1, VULN = 2, IMMUNE = 4;

static final String INFO = "Resistance: the creature takes half damage of that type (rounded down).\n"
        + "Vulnerability: it takes double damage of that type.\n"
        + "Immunity: it takes no damage of that type.\n\n"
        + "Resistance is applied before Vulnerability, so a creature with both takes normal damage (minus rounding). "
        + "Several sources of the same modifier count only once.";

static String[] damageTypeOptions() {
    String[] out = new String[CustomAssets.DAMAGE_TYPES.length + 1];
    out[0] = ALL_DAMAGE;
    System.arraycopy(CustomAssets.DAMAGE_TYPES, 0, out, 1, CustomAssets.DAMAGE_TYPES.length);
    return out;
}

static int bitFor(String traitType) {
    switch (traitType == null ? "" : traitType) {
        case VULNERABILITY: return VULN;
        case RESISTANCE: return RESIST;
        case IMMUNITY: return IMMUNE;
        default: return 0;
    }
}

static String levelName(int mask) {
    if ((mask & IMMUNE) != 0) return "Immune";
    if ((mask & RESIST) != 0 && (mask & VULN) != 0) return "Resistant + Vulnerable";
    if ((mask & RESIST) != 0) return "Resistant";
    if ((mask & VULN) != 0) return "Vulnerable";
    return "Normal";
}

// {damage type -> mask} from an NPC's typed traits
static Map<String, Integer> fromTraits(Map<?, ?> npc) {
    Map<String, Integer> out = new LinkedHashMap<>();
    for (Map<String, Object> e : CustomAssets.entries(npc, "traits")) {
        int bit = bitFor(CustomAssets.str(e, "traitType"));
        if (bit == 0) continue;
        String type = CustomAssets.str(e, "damageType");
        if (type.isEmpty()) continue;
        if (ALL_DAMAGE.equals(type)) {
            for (String t : CustomAssets.DAMAGE_TYPES) out.merge(t, bit, (x, y) -> x | y);
        } else {
            out.merge(normalizeType(type), bit, (x, y) -> x | y);
        }
    }
    return out;
}

static String normalizeType(String type) {
    for (String t : CustomAssets.DAMAGE_TYPES) if (t.equalsIgnoreCase(type.trim())) return t;
    return type.trim();
}

// The effective modifiers: the source's (e.g. NPC traits), with per-token overrides replacing whole types
static Map<String, Integer> effective(Map<String, Integer> source, Map<String, Integer> overrides) {
    Map<String, Integer> out = new LinkedHashMap<>(source);
    for (Map.Entry<String, Integer> en : overrides.entrySet()) {
        if (en.getValue() == 0) out.remove(en.getKey());
        else out.put(en.getKey(), en.getValue());
    }
    return out;
}

// Damage actually taken. `type` may be empty (untyped damage ignores modifiers).
static int apply(int amount, String type, Map<String, Integer> mods) {
    if (amount <= 0) return 0;
    int mask = type == null || type.isEmpty() ? 0 : mods.getOrDefault(normalizeType(type), 0);
    if ((mask & IMMUNE) != 0) return 0;
    int out = amount;
    if ((mask & RESIST) != 0) out = out / 2;
    if ((mask & VULN) != 0) out = out * 2;
    return out;
}

// "12 fire → 6 (resistant)" style explanation, or "" when nothing changed
static String explain(int amount, String type, Map<String, Integer> mods) {
    int mask = type == null || type.isEmpty() ? 0 : mods.getOrDefault(normalizeType(type), 0);
    if (mask == 0) return "";
    return amount + " " + type.toLowerCase() + " → " + apply(amount, type, mods) + " (" + levelName(mask).toLowerCase() + ")";
}

// "Resistant: Fire, Cold · Immune: Poison" (empty if none)
static String summary(Map<String, Integer> mods) {
    Map<String, List<String>> byLevel = new LinkedHashMap<>();
    for (Map.Entry<String, Integer> en : mods.entrySet()) {
        if (en.getValue() == 0) continue;
        byLevel.computeIfAbsent(levelName(en.getValue()), k -> new ArrayList<>()).add(en.getKey());
    }
    List<String> parts = new ArrayList<>();
    for (Map.Entry<String, List<String>> en : byLevel.entrySet()) parts.add(en.getKey() + ": " + String.join(", ", en.getValue()));
    return String.join(" · ", parts);
}
}
