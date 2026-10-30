package com.oogata.radioplugin.radio;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minecraft の SNBT を読む小さなパーサー。BDEngine が出す summon のデータを読める範囲だけ対応する。
 * コンパウンドは Map、リストは List、文字列は String、数は Byte/Short/Integer/Long/Float/Double、
 * true/false は Boolean で返す。
 */
public final class Snbt {

    private final String s;
    private int pos;

    private Snbt(String s) {
        this.s = s;
    }

    public static Object parse(String text) {
        Snbt p = new Snbt(text);
        Object v = p.value();
        p.skipWs();
        if (p.pos != p.s.length()) {
            throw p.error("余分な文字があります");
        }
        return v;
    }

    private IllegalArgumentException error(String msg) {
        return new IllegalArgumentException("SNBT: " + msg + "（" + pos + " 文字目）");
    }

    private void skipWs() {
        while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
            pos++;
        }
    }

    private Object value() {
        skipWs();
        if (pos >= s.length()) {
            throw error("途中で終わっています");
        }
        char c = s.charAt(pos);
        if (c == '{') {
            return compound();
        }
        if (c == '[') {
            return list();
        }
        if (c == '"' || c == '\'') {
            return quoted();
        }
        return bare();
    }

    private Map<String, Object> compound() {
        pos++; // {
        Map<String, Object> out = new LinkedHashMap<>();
        skipWs();
        if (peek() == '}') {
            pos++;
            return out;
        }
        while (true) {
            skipWs();
            String key = (peek() == '"' || peek() == '\'') ? quoted() : token(":");
            skipWs();
            expect(':');
            out.put(key, value());
            skipWs();
            char c = next();
            if (c == '}') {
                return out;
            }
            if (c != ',') {
                throw error("',' か '}' が必要です");
            }
        }
    }

    private List<Object> list() {
        pos++; // [
        List<Object> out = new ArrayList<>();
        skipWs();
        // 型付き配列（[I; 1, 2] など）は、型の指定を読み飛ばして普通のリストとして扱う
        if (pos + 1 < s.length() && s.charAt(pos + 1) == ';' && "BIL".indexOf(s.charAt(pos)) >= 0) {
            pos += 2;
            skipWs();
        }
        if (peek() == ']') {
            pos++;
            return out;
        }
        while (true) {
            out.add(value());
            skipWs();
            char c = next();
            if (c == ']') {
                return out;
            }
            if (c != ',') {
                throw error("',' か ']' が必要です");
            }
        }
    }

    private String quoted() {
        char quote = s.charAt(pos++);
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= s.length()) {
                throw error("文字列が閉じていません");
            }
            char c = s.charAt(pos++);
            if (c == quote) {
                return sb.toString();
            }
            if (c == '\\') {
                if (pos >= s.length()) {
                    throw error("文字列が閉じていません");
                }
                sb.append(s.charAt(pos++));
            } else {
                sb.append(c);
            }
        }
    }

    private String token(String stops) {
        int start = pos;
        while (pos < s.length() && stops.indexOf(s.charAt(pos)) < 0
                && !Character.isWhitespace(s.charAt(pos))) {
            pos++;
        }
        if (start == pos) {
            throw error("値がありません");
        }
        return s.substring(start, pos);
    }

    private Object bare() {
        String t = token(",]}");
        if (t.equals("true")) {
            return Boolean.TRUE;
        }
        if (t.equals("false")) {
            return Boolean.FALSE;
        }
        Object n = number(t);
        return n != null ? n : t;
    }

    private static Object number(String t) {
        char last = Character.toLowerCase(t.charAt(t.length() - 1));
        String body = t;
        char kind = 0;
        if ("bslfd".indexOf(last) >= 0 && t.length() > 1) {
            kind = last;
            body = t.substring(0, t.length() - 1);
        }
        if (!body.matches("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?")) {
            return null;
        }
        try {
            switch (kind) {
                case 'b':
                    return Byte.valueOf(body);
                case 's':
                    return Short.valueOf(body);
                case 'l':
                    return Long.valueOf(body);
                case 'f':
                    return Float.valueOf(body);
                case 'd':
                    return Double.valueOf(body);
                default:
                    if (body.matches("[+-]?\\d+")) {
                        return Integer.valueOf(body);
                    }
                    return Double.valueOf(body);
            }
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private char peek() {
        return pos < s.length() ? s.charAt(pos) : '\0';
    }

    private char next() {
        if (pos >= s.length()) {
            throw error("途中で終わっています");
        }
        return s.charAt(pos++);
    }

    private void expect(char c) {
        if (next() != c) {
            pos--;
            throw error("'" + c + "' が必要です");
        }
    }
}
