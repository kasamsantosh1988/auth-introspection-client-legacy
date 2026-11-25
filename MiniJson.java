package com.example.authclient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader, enough for the introspection answer (objects, arrays, strings, numbers, true, false, null).
 * It exists so this library needs no JSON dependency that could clash with the application's own.
 * Numbers are returned as Long when whole, else Double. Nesting is limited to 20 levels.
 */
final class MiniJson {

    private static final int MAX_DEPTH = 20;

    private final String text;
    private int pos;

    private MiniJson(String text) {
        this.text = text;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> parseObject(String json) {
        MiniJson parser = new MiniJson(json);
        parser.skipSpace();
        Object value = parser.value(0);
        parser.skipSpace();
        if (parser.pos != json.length()) {
            throw new IllegalArgumentException("Unexpected data after JSON value");
        }
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("JSON object expected");
        }
        return (Map<String, Object>) value;
    }

    private Object value(int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("JSON nested too deeply");
        }
        if (this.pos >= this.text.length()) {
            throw new IllegalArgumentException("Unexpected end of JSON");
        }
        char c = this.text.charAt(this.pos);
        switch (c) {
            case '{':
                return object(depth);
            case '[':
                return array(depth);
            case '"':
                return string();
            case 't':
                literal("true");
                return Boolean.TRUE;
            case 'f':
                literal("false");
                return Boolean.FALSE;
            case 'n':
                literal("null");
                return null;
            default:
                return number();
        }
    }

    private Map<String, Object> object(int depth) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        this.pos++;
        skipSpace();
        if (peek() == '}') {
            this.pos++;
            return map;
        }
        while (true) {
            skipSpace();
            if (peek() != '"') {
                throw new IllegalArgumentException("String key expected");
            }
            String key = string();
            skipSpace();
            expect(':');
            skipSpace();
            map.put(key, value(depth + 1));
            skipSpace();
            char c = next();
            if (c == '}') {
                return map;
            }
            if (c != ',') {
                throw new IllegalArgumentException("',' or '}' expected");
            }
        }
    }

    private List<Object> array(int depth) {
        List<Object> list = new ArrayList<Object>();
        this.pos++;
        skipSpace();
        if (peek() == ']') {
            this.pos++;
            return list;
        }
        while (true) {
            skipSpace();
            list.add(value(depth + 1));
            skipSpace();
            char c = next();
            if (c == ']') {
                return list;
            }
            if (c != ',') {
                throw new IllegalArgumentException("',' or ']' expected");
            }
        }
    }

    private String string() {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') {
                return out.toString();
            }
            if (c == '\\') {
                char e = next();
                switch (e) {
                    case '"': out.append('"'); break;
                    case '\\': out.append('\\'); break;
                    case '/': out.append('/'); break;
                    case 'b': out.append('\b'); break;
                    case 'f': out.append('\f'); break;
                    case 'n': out.append('\n'); break;
                    case 'r': out.append('\r'); break;
                    case 't': out.append('\t'); break;
                    case 'u':
                        if (this.pos + 4 > this.text.length()) {
                            throw new IllegalArgumentException("Bad unicode escape");
                        }
                        out.append((char) Integer.parseInt(this.text.substring(this.pos, this.pos + 4), 16));
                        this.pos += 4;
                        break;
                    default:
                        throw new IllegalArgumentException("Bad escape");
                }
            }
            else {
                out.append(c);
            }
        }
    }

    private Object number() {
        int start = this.pos;
        while (this.pos < this.text.length() && "+-0123456789.eE".indexOf(this.text.charAt(this.pos)) >= 0) {
            this.pos++;
        }
        String token = this.text.substring(start, this.pos);
        if (token.isEmpty()) {
            throw new IllegalArgumentException("Unexpected character");
        }
        try {
            if (token.indexOf('.') < 0 && token.indexOf('e') < 0 && token.indexOf('E') < 0) {
                return Long.valueOf(token);
            }
            return Double.valueOf(token);
        }
        catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Bad number");
        }
    }

    private void literal(String word) {
        if (!this.text.startsWith(word, this.pos)) {
            throw new IllegalArgumentException("Unexpected token");
        }
        this.pos += word.length();
    }

    private void skipSpace() {
        while (this.pos < this.text.length() && Character.isWhitespace(this.text.charAt(this.pos))) {
            this.pos++;
        }
    }

    private char peek() {
        if (this.pos >= this.text.length()) {
            throw new IllegalArgumentException("Unexpected end of JSON");
        }
        return this.text.charAt(this.pos);
    }

    private char next() {
        char c = peek();
        this.pos++;
        return c;
    }

    private void expect(char c) {
        if (next() != c) {
            throw new IllegalArgumentException("'" + c + "' expected");
        }
    }
}
