package org.telegram.messenger.plugins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PrimeGram: the header of a {@code .plugin} file - everything the app knows about a plugin before
 * it agrees to run any of it.
 *
 * <p>A plugin is a Python source file whose first lines are plain assignments: {@code __id__},
 * {@code __name__} and friends. exteraGram reads them with Python's own {@code ast} module; we
 * cannot, because this has to work before the interpreter is started - the install sheet shows the
 * name, author and requirements of a file the user has only tapped on, and starting Python to find
 * out what a stranger's file claims to be is the wrong order of events.
 *
 * <p>So this is a small reader for the one corner of Python syntax that matters: top-level
 * assignments of literals. It is deliberately incapable of evaluating anything - a header that
 * computes its own name is simply a header with no name, not an invitation to run code.
 */
public final class PluginManifest {

    /** Ids are used as directory names and settings prefixes, so they are kept boring. */
    private static final int ID_MIN = 2;
    private static final int ID_MAX = 32;

    public final String id;
    public final String name;
    public final String description;
    public final String author;
    public final String version;
    /** Sticker pack short name, or null when the plugin ships no icon. */
    public final String iconPack;
    /** Index inside {@link #iconPack}, or -1. */
    public final int iconIndex;
    /** Version constraint on the app itself, e.g. {@code ">=11.4.0"}. Null means "any". */
    public final String appVersion;
    /**
     * The older spelling of the same idea, a bare minimum version. Kept because the plugins in the
     * wild use it: both files in the reference repository say {@code __min_version__} and neither
     * says {@code __app_version__}, so reading only the documented field would reject them.
     */
    public final String minVersion;
    /** Minimum SDK the plugin was written against. Null means "any". */
    public final String sdkVersion;
    /** PIP packages the plugin asks for, in the order it listed them. */
    public final List<String> requirements;

    private PluginManifest(String id, String name, String description, String author, String version,
                           String iconPack, int iconIndex, String appVersion, String minVersion,
                           String sdkVersion, List<String> requirements) {
        this.minVersion = minVersion;
        this.id = id;
        this.name = name;
        this.description = description;
        this.author = author;
        this.version = version;
        this.iconPack = iconPack;
        this.iconIndex = iconIndex;
        this.appVersion = appVersion;
        this.sdkVersion = sdkVersion;
        this.requirements = Collections.unmodifiableList(requirements);
    }

    /** Thrown when a file cannot be a plugin at all, with a message meant for the user. */
    public static class MalformedException extends Exception {
        public MalformedException(String message) {
            super(message);
        }
    }

    public String icon() {
        return iconPack == null || iconIndex < 0 ? null : iconPack + "/" + iconIndex;
    }

    public static PluginManifest parse(String source) throws MalformedException {
        final Map<String, Object> values = readTopLevelLiterals(source);

        final String id = asString(values.get("__id__"));
        if (id == null) {
            throw new MalformedException("no __id__");
        }
        if (!isValidId(id)) {
            throw new MalformedException("bad __id__: " + id);
        }
        final String name = asString(values.get("__name__"));
        if (name == null || name.trim().isEmpty()) {
            throw new MalformedException("no __name__");
        }

        String iconPack = null;
        int iconIndex = -1;
        final String icon = asString(values.get("__icon__"));
        if (icon != null) {
            final int slash = icon.lastIndexOf('/');
            if (slash > 0 && slash < icon.length() - 1) {
                try {
                    iconIndex = Integer.parseInt(icon.substring(slash + 1).trim());
                    iconPack = icon.substring(0, slash);
                } catch (NumberFormatException ignore) {
                    // A malformed icon is not worth refusing the plugin over; it just has none.
                    iconIndex = -1;
                }
            }
        }

        final List<String> requirements = new ArrayList<>();
        final Object rawRequirements = values.get("__requirements__");
        if (rawRequirements instanceof List) {
            for (Object item : (List<?>) rawRequirements) {
                final String requirement = asString(item);
                if (requirement != null && !requirement.trim().isEmpty()) {
                    requirements.add(requirement.trim());
                }
            }
        } else {
            final String single = asString(rawRequirements);
            if (single != null && !single.trim().isEmpty()) {
                requirements.add(single.trim());
            }
        }

        final String version = asString(values.get("__version__"));
        return new PluginManifest(
                id.trim(),
                name.trim(),
                asString(values.get("__description__")),
                asString(values.get("__author__")),
                version == null || version.trim().isEmpty() ? "1.0" : version.trim(),
                iconPack,
                iconIndex,
                asString(values.get("__app_version__")),
                asString(values.get("__min_version__")),
                asString(values.get("__sdk_version__")),
                requirements);
    }

    /**
     * Whether this plugin says it can run on the app we are.
     *
     * <p>Both fields are checked because both exist in the wild, and a plugin that carries the two
     * of them has to satisfy the two of them - they are claims by the same author about the same
     * thing, and picking a favourite would only decide which typo goes unnoticed.
     */
    public boolean isCompatibleWithApp(String currentVersion) {
        if (minVersion != null && !minVersion.trim().isEmpty()
                && PluginVersions.compare(currentVersion, minVersion) < 0) {
            return false;
        }
        return PluginVersions.satisfies(currentVersion, appVersion);
    }

    public static boolean isValidId(String id) {
        if (id == null || id.length() < ID_MIN || id.length() > ID_MAX) {
            return false;
        }
        if (!Character.isLetter(id.charAt(0)) || id.charAt(0) > 0x7F) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            final char c = id.charAt(i);
            final boolean ok = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z'
                    || c >= '0' && c <= '9' || c == '_' || c == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    // ─── the reader ───

    /**
     * Every top-level {@code name = literal} in the file, in source order.
     *
     * <p>Top-level means column zero: an assignment inside a class or a function is indented, and
     * one inside a string or a comment is not an assignment at all. Values are {@link String},
     * {@link Boolean} or {@code List<Object>}; anything the reader does not understand is skipped
     * rather than guessed at.
     */
    public static Map<String, Object> readTopLevelLiterals(String source) {
        final Map<String, Object> result = new LinkedHashMap<>();
        if (source == null) {
            return result;
        }
        final Cursor cursor = new Cursor(source);
        while (!cursor.eof()) {
            // Only the very start of a line can begin a top-level statement.
            if (cursor.column() != 0) {
                cursor.skipToNextLine();
                continue;
            }
            final int nameStart = cursor.pos;
            final String name = cursor.readIdentifier();
            if (name == null || !name.startsWith("__") || !name.endsWith("__")) {
                cursor.pos = nameStart;
                cursor.skipToNextLine();
                continue;
            }
            cursor.skipInlineSpace();
            if (!cursor.consume('=') || cursor.peek() == '=') {
                cursor.skipToNextLine();
                continue;
            }
            final Object value = cursor.readLiteral();
            if (value != null) {
                result.put(name, value);
            }
            cursor.skipToNextLine();
        }
        return result;
    }

    private static String asString(Object value) {
        return value instanceof String ? (String) value : null;
    }

    /**
     * A position in the source with just enough Python awareness to get past strings and comments
     * without being fooled by what is inside them.
     */
    private static final class Cursor {
        final String s;
        int pos;

        Cursor(String s) {
            this.s = s;
        }

        boolean eof() {
            return pos >= s.length();
        }

        char peek() {
            return pos < s.length() ? s.charAt(pos) : '\0';
        }

        int column() {
            int line = s.lastIndexOf('\n', Math.max(0, pos - 1));
            return pos - (line + 1);
        }

        boolean consume(char c) {
            if (peek() == c) {
                pos++;
                return true;
            }
            return false;
        }

        void skipInlineSpace() {
            while (pos < s.length()) {
                final char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\r') {
                    pos++;
                } else if (c == '\\' && pos + 1 < s.length() && s.charAt(pos + 1) == '\n') {
                    pos += 2;
                } else {
                    return;
                }
            }
        }

        /** Whitespace, newlines and comments — used inside brackets, where lines may wrap. */
        void skipSpaceAndComments() {
            while (pos < s.length()) {
                final char c = s.charAt(pos);
                if (Character.isWhitespace(c)) {
                    pos++;
                } else if (c == '#') {
                    while (pos < s.length() && s.charAt(pos) != '\n') {
                        pos++;
                    }
                } else if (c == '\\' && pos + 1 < s.length() && s.charAt(pos + 1) == '\n') {
                    pos += 2;
                } else {
                    return;
                }
            }
        }

        /**
         * Moves past the end of the current logical line, stepping over strings so that a newline
         * inside a triple-quoted block does not look like the end of a statement.
         */
        void skipToNextLine() {
            while (pos < s.length()) {
                final char c = s.charAt(pos);
                if (c == '\n') {
                    pos++;
                    return;
                }
                if (c == '#') {
                    while (pos < s.length() && s.charAt(pos) != '\n') {
                        pos++;
                    }
                    continue;
                }
                if (c == '"' || c == '\'') {
                    if (readString() == null) {
                        pos++;
                    }
                    continue;
                }
                if (c == '\\' && pos + 1 < s.length() && s.charAt(pos + 1) == '\n') {
                    pos += 2;
                    continue;
                }
                pos++;
            }
        }

        String readIdentifier() {
            final int start = pos;
            while (pos < s.length()) {
                final char c = s.charAt(pos);
                if (Character.isLetterOrDigit(c) || c == '_') {
                    pos++;
                } else {
                    break;
                }
            }
            return pos == start ? null : s.substring(start, pos);
        }

        /** A string, a list/tuple of literals, a bare word, or null when it is an expression. */
        Object readLiteral() {
            skipSpaceAndComments();
            final char c = peek();
            if (c == '"' || c == '\'' || isStringPrefix()) {
                return readConcatenatedStrings();
            }
            if (c == '[' || c == '(') {
                return readSequence(c == '[' ? ']' : ')');
            }
            if (c == '{') {
                // A set or dict literal: read past it so the rest of the file still scans, but do
                // not pretend to understand it.
                readSequence('}');
                return null;
            }
            final int start = pos;
            final String word = readIdentifier();
            if (word != null) {
                if ("True".equals(word)) return Boolean.TRUE;
                if ("False".equals(word)) return Boolean.FALSE;
                if ("None".equals(word)) return null;
                pos = start;
                return null;
            }
            // Numbers are kept as text: versions like 1.0 must not become a float and come back
            // out as "1.0" by luck of formatting.
            while (pos < s.length()) {
                final char n = s.charAt(pos);
                if (n >= '0' && n <= '9' || n == '.' || n == '-' || n == '+') {
                    pos++;
                } else {
                    break;
                }
            }
            return pos == start ? null : s.substring(start, pos);
        }

        private boolean isStringPrefix() {
            int p = pos;
            int letters = 0;
            while (p < s.length() && letters < 2) {
                final char c = Character.toLowerCase(s.charAt(p));
                if (c == 'r' || c == 'b' || c == 'u' || c == 'f') {
                    p++;
                    letters++;
                } else {
                    break;
                }
            }
            return letters > 0 && p < s.length() && (s.charAt(p) == '"' || s.charAt(p) == '\'');
        }

        /** Python glues adjacent string literals together, and headers use that for long text. */
        private String readConcatenatedStrings() {
            final StringBuilder sb = new StringBuilder();
            boolean any = false;
            while (true) {
                final int mark = pos;
                skipSpaceAndComments();
                if (!(peek() == '"' || peek() == '\'' || isStringPrefix())) {
                    pos = mark;
                    break;
                }
                final String part = readString();
                if (part == null) {
                    pos = mark;
                    break;
                }
                sb.append(part);
                any = true;
            }
            return any ? sb.toString() : null;
        }

        private String readString() {
            boolean raw = false;
            while (pos < s.length()) {
                final char c = Character.toLowerCase(s.charAt(pos));
                if (c == 'r') {
                    raw = true;
                    pos++;
                } else if (c == 'b' || c == 'u' || c == 'f') {
                    pos++;
                } else {
                    break;
                }
            }
            final char quote = peek();
            if (quote != '"' && quote != '\'') {
                return null;
            }
            final boolean triple = pos + 2 < s.length()
                    && s.charAt(pos + 1) == quote && s.charAt(pos + 2) == quote;
            pos += triple ? 3 : 1;
            final StringBuilder sb = new StringBuilder();
            while (pos < s.length()) {
                final char c = s.charAt(pos);
                if (c == '\\' && !raw && pos + 1 < s.length()) {
                    pos++;
                    sb.append(unescape(s.charAt(pos)));
                    pos++;
                    continue;
                }
                if (c == '\\' && raw && pos + 1 < s.length()) {
                    // A raw string keeps the backslash but still cannot end on one.
                    sb.append(c).append(s.charAt(pos + 1));
                    pos += 2;
                    continue;
                }
                if (c == quote) {
                    if (!triple) {
                        pos++;
                        return sb.toString();
                    }
                    if (pos + 2 < s.length() && s.charAt(pos + 1) == quote && s.charAt(pos + 2) == quote) {
                        pos += 3;
                        return sb.toString();
                    }
                }
                if (c == '\n' && !triple) {
                    // An unterminated single-quoted string: give up rather than swallow the file.
                    return null;
                }
                sb.append(c);
                pos++;
            }
            return triple ? sb.toString() : null;
        }

        private char unescape(char c) {
            switch (c) {
                case 'n': return '\n';
                case 't': return '\t';
                case 'r': return '\r';
                case '0': return '\0';
                default: return c;
            }
        }

        /**
         * A bracketed value. Parentheses without a comma are not a sequence at all - Python reads
         * {@code ("a" "b")} as one string, and headers wrap long names that way to keep lines
         * short, so unwrapping is the difference between a name and a list of one.
         */
        private Object readSequence(char closing) {
            pos++;
            final List<Object> items = new ArrayList<>();
            boolean sawComma = false;
            while (true) {
                skipSpaceAndComments();
                if (eof()) {
                    break;
                }
                if (peek() == closing) {
                    pos++;
                    break;
                }
                if (peek() == ',') {
                    sawComma = true;
                    pos++;
                    continue;
                }
                final int before = pos;
                final Object item = readLiteral();
                if (item != null) {
                    items.add(item);
                }
                if (pos == before) {
                    // Something we cannot read; skip a character so the loop always advances.
                    pos++;
                }
            }
            if (closing == ')' && !sawComma) {
                return items.size() == 1 ? items.get(0) : null;
            }
            return items;
        }
    }
}
