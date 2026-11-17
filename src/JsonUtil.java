import java.util.*;
import java.util.List;

// Minimal hand-rolled JSON reader/writer with no external dependencies.
class JsonUtil {

static int toInt(Object o) {
    if (o instanceof Number) return ((Number) o).intValue();
    return 0;
}


static String jsonEscape(String s) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        switch (c) {
            case '"': sb.append("\\\""); break;
            case '\\': sb.append("\\\\"); break;
            case '\n': sb.append("\\n"); break;
            case '\r': sb.append("\\r"); break;
            case '\t': sb.append("\\t"); break;
            default:
                if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                else sb.append(c);
        }
    }
    return sb.toString();
}

static String toJson(Object o) {
    if (o == null) return "null";
    if (o instanceof String) return "\"" + jsonEscape((String) o) + "\"";
    if (o instanceof Number || o instanceof Boolean) return String.valueOf(o);
    if (o instanceof Map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<?, ?> en : ((Map<?, ?>) o).entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(jsonEscape(String.valueOf(en.getKey()))).append("\":").append(toJson(en.getValue()));
        }
        sb.append("}");
        return sb.toString();
    }
    if (o instanceof List) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Object item : (List<?>) o) {
            if (!first) sb.append(",");
            first = false;
            sb.append(toJson(item));
        }
        sb.append("]");
        return sb.toString();
    }
    return "\"" + jsonEscape(String.valueOf(o)) + "\"";
}

// Indented JSON for files people read or share (homebrew packs). Short lists of plain
// values stay on one line so map rows and tag lists don't explode vertically.
static String toPrettyJson(Object o) {
    StringBuilder sb = new StringBuilder();
    writePretty(sb, o, 0);
    return sb.toString();
}

private static void writePretty(StringBuilder sb, Object o, int indent) {
    if (o instanceof Map) {
        Map<?, ?> m = (Map<?, ?>) o;
        if (m.isEmpty()) { sb.append("{}"); return; }
        sb.append("{\n");
        int i = 0;
        for (Map.Entry<?, ?> en : m.entrySet()) {
            pad(sb, indent + 1);
            sb.append("\"").append(jsonEscape(String.valueOf(en.getKey()))).append("\": ");
            writePretty(sb, en.getValue(), indent + 1);
            if (++i < m.size()) sb.append(",");
            sb.append("\n");
        }
        pad(sb, indent);
        sb.append("}");
    } else if (o instanceof List) {
        List<?> l = (List<?>) o;
        boolean flat = true;
        for (Object item : l) if (item instanceof Map || item instanceof List) { flat = false; break; }
        if (l.isEmpty() || flat) { sb.append(toJson(o)); return; }
        sb.append("[\n");
        for (int i = 0; i < l.size(); i++) {
            pad(sb, indent + 1);
            writePretty(sb, l.get(i), indent + 1);
            if (i < l.size() - 1) sb.append(",");
            sb.append("\n");
        }
        pad(sb, indent);
        sb.append("]");
    } else {
        sb.append(toJson(o));
    }
}

private static void pad(StringBuilder sb, int indent) {
    for (int i = 0; i < indent; i++) sb.append("  ");
}

}
