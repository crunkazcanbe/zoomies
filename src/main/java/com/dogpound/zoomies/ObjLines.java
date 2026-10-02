package com.dogpound.zoomies;

/**
 * Hand-written twins of the OBJ-line regexes that GVCLib (the gun mods' model loader) runs on every line of every
 * model — run 16 profile: ~20 s of load in java.util.regex. Same answers (checks/ObjLinesCheck compares them against
 * the original patterns on thousands of lines), no regex engine.
 *   vertex:   v( -?\d+\.\d+){3,4} *\n?     normal: vn(...){3,4}     texture: vt(...){2,3}
 *   faces:    f( \d+/\d+/\d+){3,4}  f( \d+/\d+){3,4}  f( \d+//\d+){3,4}  f( \d+){3,4}
 *   group:    [go]( [\w\d]+) *\n?
 */
public final class ObjLines {
    private ObjLines() {}

    public static boolean vertex(String s) { return numbers(s, "v", 3, 4); }
    public static boolean normal(String s) { return numbers(s, "vn", 3, 4); }
    public static boolean texture(String s) { return numbers(s, "vt", 2, 3); }
    public static boolean faceVtVn(String s) { return face(s, 3, false); }
    public static boolean faceVt(String s) { return face(s, 2, false); }
    public static boolean faceVn(String s) { return face(s, 2, true); }
    public static boolean faceV(String s) { return face(s, 1, false); }

    public static boolean group(String s) {
        int end = end(s);
        if (end < 3 || (s.charAt(0) != 'g' && s.charAt(0) != 'o') || s.charAt(1) != ' ') return false;
        int i = 2, start = i;
        while (i < end && word(s.charAt(i))) i++;
        return i > start && i == end;
    }

    /** where the matched text stops: trailing spaces and one optional final newline are allowed */
    private static int end(String s) {
        int e = s.length();
        if (e > 0 && s.charAt(e - 1) == '\n') e--;
        while (e > 0 && s.charAt(e - 1) == ' ') e--;
        return e;
    }

    private static boolean numbers(String s, String tag, int min, int max) {
        int end = end(s), n = tag.length();
        if (!s.startsWith(tag) || end < n) return false;
        int i = n, count = 0;
        while (i < end) {
            if (s.charAt(i) != ' ') return false;
            i++;
            if (i < end && s.charAt(i) == '-') i++;
            int d = i;
            while (i < end && digit(s.charAt(i))) i++;
            if (i == d || i >= end || s.charAt(i) != '.') return false;
            i++;
            d = i;
            while (i < end && digit(s.charAt(i))) i++;
            if (i == d) return false;
            count++;
        }
        return count >= min && count <= max;
    }

    /** parts per point: 1 = v, 2 = v/vt (or v//vn when doubleSlash), 3 = v/vt/vn */
    private static boolean face(String s, int parts, boolean doubleSlash) {
        int end = end(s);
        if (end < 1 || s.charAt(0) != 'f') return false;
        int i = 1, count = 0;
        while (i < end) {
            if (s.charAt(i) != ' ') return false;
            i++;
            for (int p = 0; p < parts; p++) {
                if (p > 0) {
                    if (i >= end || s.charAt(i) != '/') return false;
                    i++;
                    if (doubleSlash) { if (i >= end || s.charAt(i) != '/') return false; i++; }
                }
                int d = i;
                while (i < end && digit(s.charAt(i))) i++;
                if (i == d) return false;
            }
            count++;
        }
        return count >= 3 && count <= 4;
    }

    private static boolean digit(char c) { return c >= '0' && c <= '9'; }
    private static boolean word(char c) { return c == '_' || digit(c) || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'); }
}
