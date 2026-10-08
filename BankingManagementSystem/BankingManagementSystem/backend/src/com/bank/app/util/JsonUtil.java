package com.bank.app.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny JSON helper (no libraries allowed). It can parse a FLAT object such as
 * {"name":"Asha","amount":50} into a Map, and turn Maps/Lists into JSON text.
 */
public class JsonUtil {

    /** Parses a flat JSON object. All values are returned as Strings. */
    public static Map<String, String> parseObject(String json) {
        Map<String, String> result = new LinkedHashMap<>();
        if (json == null) return result;
        int i = skipSpaces(json, 0);
        if (i >= json.length() || json.charAt(i) != '{') {
            throw new IllegalArgumentException("Invalid JSON body");
        }
        i++;
        while (true) {
            i = skipSpaces(json, i);
            if (i >= json.length()) throw new IllegalArgumentException("Invalid JSON body");
            if (json.charAt(i) == '}') break;
            if (json.charAt(i) == ',') { i++; continue; }
            int[] end = new int[1];
            String key = readString(json, i, end);
            i = skipSpaces(json, end[0]);
            if (i >= json.length() || json.charAt(i) != ':') {
                throw new IllegalArgumentException("Invalid JSON body");
            }
            i = skipSpaces(json, i + 1);
            String value;
            if (i < json.length() && json.charAt(i) == '"') {
                value = readString(json, i, end);
                i = end[0];
            } else {                       // number, true, false or null
                int start = i;
                while (i < json.length() && ",}".indexOf(json.charAt(i)) < 0) i++;
                value = json.substring(start, i).trim();
                if (value.equals("null")) value = null;
            }
            result.put(key, value);
        }
        return result;
    }

    private static int skipSpaces(String s, int i) {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        return i;
    }

    /** Reads a quoted string starting at index i; end[0] = index after closing quote. */
    private static String readString(String s, int i, int[] end) {
        if (i >= s.length() || s.charAt(i) != '"') throw new IllegalArgumentException("Invalid JSON body");
        StringBuilder sb = new StringBuilder();
        i++;
        while (i < s.length() && s.charAt(i) != '"') {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                switch (n) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (i + 4 >= s.length()) throw new IllegalArgumentException("Invalid JSON body");
                        sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                        i += 4;
                        break;
                    default: sb.append(n);
                }
            } else {
                sb.append(c);
            }
            i++;
        }
        if (i >= s.length()) throw new IllegalArgumentException("Invalid JSON body");
        end[0] = i + 1;
        return sb.toString();
    }

    /** Converts String/Number/Boolean/null/Map/List into JSON text. */
    public static String toJson(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append(quote(String.valueOf(e.getKey()))).append(":").append(toJson(e.getValue()));
            }
            return sb.append("}").toString();
        }
        if (value instanceof List) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object o : (List<?>) value) {
                if (!first) sb.append(",");
                first = false;
                sb.append(toJson(o));
            }
            return sb.append("]").toString();
        }
        return quote(value.toString());
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
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
        return sb.append("\"").toString();
    }
}
