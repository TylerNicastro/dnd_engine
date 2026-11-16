import java.util.*;
import java.util.List;

class MiniJsonParser {
    private final String s;
    private int pos;

    MiniJsonParser(String s) {
        this.s = s;
    }

    Object parse() {
        skipWs();
        return parseValue();
    }

    private void skipWs() {
        while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
    }

    private Object parseValue() {
        skipWs();
        char c = s.charAt(pos);
        if (c == '{') return parseObject();
        if (c == '[') return parseArray();
        if (c == '"') return parseString();
        if (c == 't') { pos += 4; return Boolean.TRUE; }
        if (c == 'f') { pos += 5; return Boolean.FALSE; }
        if (c == 'n') { pos += 4; return null; }
        return parseNumber();
    }

    private Map<String, Object> parseObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++;
        skipWs();
        if (s.charAt(pos) == '}') { pos++; return map; }
        while (true) {
            skipWs();
            String key = parseString();
            skipWs();
            pos++; // colon
            Object val = parseValue();
            map.put(key, val);
            skipWs();
            char c = s.charAt(pos);
            if (c == ',') { pos++; continue; }
            if (c == '}') { pos++; break; }
        }
        return map;
    }

    private List<Object> parseArray() {
        List<Object> list = new ArrayList<>();
        pos++;
        skipWs();
        if (s.charAt(pos) == ']') { pos++; return list; }
        while (true) {
            Object val = parseValue();
            list.add(val);
            skipWs();
            char c = s.charAt(pos);
            if (c == ',') { pos++; continue; }
            if (c == ']') { pos++; break; }
        }
        return list;
    }

    private String parseString() {
        skipWs();
        pos++; // opening quote
        StringBuilder sb = new StringBuilder();
        while (s.charAt(pos) != '"') {
            char c = s.charAt(pos);
            if (c == '\\') {
                pos++;
                char esc = s.charAt(pos);
                switch (esc) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    default: sb.append(esc);
                }
            } else {
                sb.append(c);
            }
            pos++;
        }
        pos++; // closing quote
        return sb.toString();
    }

    private Object parseNumber() {
        int start = pos;
        while (pos < s.length() && "-+.eE0123456789".indexOf(s.charAt(pos)) >= 0) pos++;
        return Double.parseDouble(s.substring(start, pos));
    }
}

