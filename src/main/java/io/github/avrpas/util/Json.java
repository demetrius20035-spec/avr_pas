package io.github.avrpas.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A tiny, dependency-free JSON reader/writer good enough for configuration
 * files and chip definitions. Supports objects, arrays, strings, numbers,
 * booleans and null. Numbers are returned as {@link Double} or {@link Long}.
 *
 * <p>This intentionally avoids pulling in an external JSON library so that the
 * project builds with no network access beyond the Maven plugins.</p>
 */
public final class Json {

    private Json() {}

    // ----------------------------------------------------------------- parse

    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWs();
        Object v = p.parseValue();
        p.skipWs();
        if (!p.atEnd()) {
            throw new JsonException("Trailing content at offset " + p.pos);
        }
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object v = parse(text);
        if (!(v instanceof Map)) {
            throw new JsonException("Expected a JSON object at top level");
        }
        return (Map<String, Object>) v;
    }

    public static final class JsonException extends RuntimeException {
        public JsonException(String m) { super(m); }
    }

    private static final class Parser {
        final String s;
        int pos;

        Parser(String s) { this.s = s; }

        boolean atEnd() { return pos >= s.length(); }

        void skipWs() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else if (c == '/' && pos + 1 < s.length() && s.charAt(pos + 1) == '/') {
                    // line comment (JSON5-ish convenience for config files)
                    while (pos < s.length() && s.charAt(pos) != '\n') pos++;
                } else {
                    break;
                }
            }
        }

        Object parseValue() {
            skipWs();
            if (atEnd()) throw new JsonException("Unexpected end of input");
            char c = s.charAt(pos);
            switch (c) {
                case '{': return parseObj();
                case '[': return parseArr();
                case '"': return parseString();
                case 't': case 'f': return parseBool();
                case 'n': return parseNull();
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) return parseNumber();
                    throw new JsonException("Unexpected character '" + c + "' at offset " + pos);
            }
        }

        Map<String, Object> parseObj() {
            Map<String, Object> map = new LinkedHashMap<>();
            expect('{');
            skipWs();
            if (peek() == '}') { pos++; return map; }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                expect(':');
                Object val = parseValue();
                map.put(key, val);
                skipWs();
                char c = next();
                if (c == '}') break;
                if (c != ',') throw new JsonException("Expected ',' or '}' at offset " + (pos - 1));
            }
            return map;
        }

        List<Object> parseArr() {
            List<Object> list = new ArrayList<>();
            expect('[');
            skipWs();
            if (peek() == ']') { pos++; return list; }
            while (true) {
                Object val = parseValue();
                list.add(val);
                skipWs();
                char c = next();
                if (c == ']') break;
                if (c != ',') throw new JsonException("Expected ',' or ']' at offset " + (pos - 1));
            }
            return list;
        }

        String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) throw new JsonException("Unterminated string");
                char c = s.charAt(pos++);
                if (c == '"') break;
                if (c == '\\') {
                    char e = s.charAt(pos++);
                    switch (e) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            String hex = s.substring(pos, pos + 4);
                            pos += 4;
                            sb.append((char) Integer.parseInt(hex, 16));
                            break;
                        default: throw new JsonException("Invalid escape \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        Object parseNumber() {
            int start = pos;
            boolean isFloat = false;
            while (!atEnd()) {
                char c = s.charAt(pos);
                if ((c >= '0' && c <= '9') || c == '-' || c == '+') {
                    pos++;
                } else if (c == '.' || c == 'e' || c == 'E') {
                    isFloat = true;
                    pos++;
                } else {
                    break;
                }
            }
            String num = s.substring(start, pos);
            if (isFloat) return Double.parseDouble(num);
            return Long.parseLong(num);
        }

        Boolean parseBool() {
            if (s.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
            if (s.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
            throw new JsonException("Invalid literal at offset " + pos);
        }

        Object parseNull() {
            if (s.startsWith("null", pos)) { pos += 4; return null; }
            throw new JsonException("Invalid literal at offset " + pos);
        }

        char peek() { skipWs(); return atEnd() ? '\0' : s.charAt(pos); }
        char next() { return s.charAt(pos++); }
        void expect(char c) {
            skipWs();
            if (atEnd() || s.charAt(pos) != c) {
                throw new JsonException("Expected '" + c + "' at offset " + pos);
            }
            pos++;
        }
    }

    // ----------------------------------------------------------------- write

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value, 0);
        return sb.toString();
    }

    private static void writeValue(StringBuilder sb, Object v, int indent) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof Map) {
            writeMap(sb, (Map<?, ?>) v, indent);
        } else if (v instanceof List) {
            writeList(sb, (List<?>) v, indent);
        } else if (v instanceof String) {
            writeString(sb, (String) v);
        } else if (v instanceof Boolean || v instanceof Number) {
            sb.append(v.toString());
        } else {
            writeString(sb, v.toString());
        }
    }

    private static void writeMap(StringBuilder sb, Map<?, ?> map, int indent) {
        sb.append("{\n");
        int i = 0;
        for (Map.Entry<?, ?> e : map.entrySet()) {
            indent(sb, indent + 1);
            writeString(sb, String.valueOf(e.getKey()));
            sb.append(": ");
            writeValue(sb, e.getValue(), indent + 1);
            if (++i < map.size()) sb.append(',');
            sb.append('\n');
        }
        indent(sb, indent);
        sb.append('}');
    }

    private static void writeList(StringBuilder sb, List<?> list, int indent) {
        sb.append("[\n");
        for (int i = 0; i < list.size(); i++) {
            indent(sb, indent + 1);
            writeValue(sb, list.get(i), indent + 1);
            if (i + 1 < list.size()) sb.append(',');
            sb.append('\n');
        }
        indent(sb, indent);
        sb.append(']');
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        sb.append('"');
    }

    private static void indent(StringBuilder sb, int n) {
        for (int i = 0; i < n; i++) sb.append("  ");
    }

    // -------------------------------------------------------------- helpers

    public static int asInt(Object o, int dflt) {
        if (o instanceof Number) return ((Number) o).intValue();
        if (o instanceof String) {
            try { return Integer.parseInt(((String) o).trim()); } catch (NumberFormatException e) { return dflt; }
        }
        return dflt;
    }

    public static long asLong(Object o, long dflt) {
        if (o instanceof Number) return ((Number) o).longValue();
        if (o instanceof String) {
            try { return Long.parseLong(((String) o).trim()); } catch (NumberFormatException e) { return dflt; }
        }
        return dflt;
    }

    public static boolean asBool(Object o, boolean dflt) {
        if (o instanceof Boolean) return (Boolean) o;
        if (o instanceof String) return Boolean.parseBoolean(((String) o).trim());
        return dflt;
    }

    public static String asString(Object o, String dflt) {
        return o == null ? dflt : o.toString();
    }
}
