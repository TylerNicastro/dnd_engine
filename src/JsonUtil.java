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

}
