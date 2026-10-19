package hospital;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A small JSON reader and writer, so the project needs no extra libraries. */
final class Json {
    private final String s;
    private int i;

    private Json(String s) { this.s = s; }

    static Object parse(String s) {
        Json j = new Json(s);
        Object v = j.value();
        j.ws();
        if (j.i != s.length()) throw j.err();
        return v;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> obj(String s) {
        Object o = parse(s);
        if (o instanceof Map) return (Map<String, Object>) o;
        throw new IllegalArgumentException("Expected a JSON object");
    }

    private IllegalArgumentException err() { return new IllegalArgumentException("Invalid JSON"); }

    private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

    private boolean take(char c) {
        ws();
        if (i < s.length() && s.charAt(i) == c) { i++; return true; }
        return false;
    }

    private void expect(char c) { if (!take(c)) throw err(); }

    private Object value() {
        ws();
        if (i >= s.length()) throw err();
        char c = s.charAt(i);
        if (c == '{') {
            i++;
            Map<String, Object> m = new LinkedHashMap<>();
            if (take('}')) return m;
            do { ws(); String k = string(); expect(':'); m.put(k, value()); } while (take(','));
            expect('}');
            return m;
        }
        if (c == '[') {
            i++;
            List<Object> l = new ArrayList<>();
            if (take(']')) return l;
            do { l.add(value()); } while (take(','));
            expect(']');
            return l;
        }
        if (c == '"') return string();
        if (s.startsWith("true", i)) { i += 4; return true; }
        if (s.startsWith("false", i)) { i += 5; return false; }
        if (s.startsWith("null", i)) { i += 4; return null; }
        int st = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        if (st == i) throw err();
        return Double.parseDouble(s.substring(st, i));
    }

    private String string() {
        ws();
        if (i >= s.length() || s.charAt(i) != '"') throw err();
        i++;
        StringBuilder b = new StringBuilder();
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '"') return b.toString();
            if (c != '\\') { b.append(c); continue; }
            if (i >= s.length()) break;
            char e = s.charAt(i++);
            switch (e) {
                case 'n': b.append('\n'); break;
                case 't': b.append('\t'); break;
                case 'r': b.append('\r'); break;
                case 'b': b.append('\b'); break;
                case 'f': b.append('\f'); break;
                case 'u': b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                default: b.append(e);
            }
        }
        throw err();
    }

    static String str(Object o) {
        StringBuilder b = new StringBuilder();
        write(b, o);
        return b.toString();
    }

    private static void write(StringBuilder b, Object o) {
        if (o == null) b.append("null");
        else if (o instanceof Number || o instanceof Boolean) b.append(o);
        else if (o instanceof JsonItem) write(b, ((JsonItem) o).toMap());
        else if (o instanceof Map) {
            b.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
                if (!first) b.append(',');
                first = false;
                quote(b, String.valueOf(e.getKey()));
                b.append(':');
                write(b, e.getValue());
            }
            b.append('}');
        } else if (o instanceof Iterable) {
            b.append('[');
            boolean first = true;
            for (Object x : (Iterable<?>) o) {
                if (!first) b.append(',');
                first = false;
                write(b, x);
            }
            b.append(']');
        } else quote(b, o.toString());
    }

    private static void quote(StringBuilder b, String s) {
        b.append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        b.append('"');
    }
}
