package com.example.authclient;

/** Minimal Ant-style path matching: "?" one character, "*" within a segment, "**" any number of segments. */
final class AntPaths {

    private AntPaths() {
    }

    static boolean match(String pattern, String path) {
        return matchSegments(split(pattern), 0, split(path), 0);
    }

    private static String[] split(String value) {
        String trimmed = value.startsWith("/") ? value.substring(1) : value;
        return trimmed.isEmpty() ? new String[0] : trimmed.split("/", -1);
    }

    private static boolean matchSegments(String[] pat, int p, String[] path, int s) {
        while (p < pat.length) {
            if ("**".equals(pat[p])) {
                if (p == pat.length - 1) {
                    return true;
                }
                for (int i = s; i <= path.length; i++) {
                    if (matchSegments(pat, p + 1, path, i)) {
                        return true;
                    }
                }
                return false;
            }
            if (s >= path.length || !matchSegment(pat[p], path[s])) {
                return false;
            }
            p++;
            s++;
        }
        return s == path.length;
    }

    private static boolean matchSegment(String pattern, String text) {
        return matchChars(pattern, 0, text, 0);
    }

    private static boolean matchChars(String pat, int p, String text, int t) {
        while (p < pat.length()) {
            char c = pat.charAt(p);
            if (c == '*') {
                for (int i = t; i <= text.length(); i++) {
                    if (matchChars(pat, p + 1, text, i)) {
                        return true;
                    }
                }
                return false;
            }
            if (t >= text.length() || (c != '?' && c != text.charAt(t))) {
                return false;
            }
            p++;
            t++;
        }
        return t == text.length();
    }
}
