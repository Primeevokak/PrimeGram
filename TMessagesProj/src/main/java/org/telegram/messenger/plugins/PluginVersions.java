package org.telegram.messenger.plugins;

/**
 * PrimeGram: version constraints from plugin headers, like {@code __app_version__ = ">=11.4.0"}.
 *
 * <p>Plugins are written against whatever fork the author had, and the constraint is how they say
 * so. It is checked before loading rather than after: a plugin that needs a method we do not have
 * fails somewhere deep inside Python, and "requires 11.4.0, you have 12.9.0" is a better thing to
 * show than that traceback.
 *
 * <p>Comparison is numeric per dotted part, with missing parts read as zero, so 12.9 and 12.9.0 are
 * the same version. Anything non-numeric in a part is ignored - build suffixes are not ordering
 * information, and guessing at them is how "12.9.0-beta" ends up newer than "12.9.0".
 */
public final class PluginVersions {

    private PluginVersions() {
    }

    /** Negative, zero or positive, in the manner of {@link Comparable}. */
    public static int compare(String a, String b) {
        final String[] left = split(a);
        final String[] right = split(b);
        final int count = Math.max(left.length, right.length);
        for (int i = 0; i < count; i++) {
            final long l = i < left.length ? number(left[i]) : 0;
            final long r = i < right.length ? number(right[i]) : 0;
            if (l != r) {
                return l < r ? -1 : 1;
            }
        }
        return 0;
    }

    /**
     * Whether {@code current} satisfies a constraint such as {@code ">=11.4.0"}, {@code "<13"} or
     * a bare {@code "12.9.0"} (which means exactly that version).
     *
     * <p>An empty or unreadable constraint passes. A header is a claim, not a gate we get to make
     * up: refusing to load over a typo in a field the author may not even have meant to write is
     * worse than letting the plugin run and fail on its own terms.
     */
    public static boolean satisfies(String current, String constraint) {
        if (constraint == null) {
            return true;
        }
        final String trimmed = constraint.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        String operator = "==";
        String wanted = trimmed;
        for (String candidate : new String[]{">=", "<=", "==", "!=", ">", "<"}) {
            if (trimmed.startsWith(candidate)) {
                operator = candidate;
                wanted = trimmed.substring(candidate.length()).trim();
                break;
            }
        }
        if (wanted.isEmpty()) {
            return true;
        }
        final int result = compare(current, wanted);
        switch (operator) {
            case ">=": return result >= 0;
            case "<=": return result <= 0;
            case ">": return result > 0;
            case "<": return result < 0;
            case "!=": return result != 0;
            default: return result == 0;
        }
    }

    private static String[] split(String version) {
        if (version == null) {
            return new String[0];
        }
        return version.trim().split("[._\\-+]");
    }

    private static long number(String part) {
        long value = 0;
        boolean any = false;
        for (int i = 0; i < part.length(); i++) {
            final char c = part.charAt(i);
            if (c < '0' || c > '9') {
                break;
            }
            value = value * 10 + (c - '0');
            any = true;
            if (value > 1_000_000_000L) {
                break;
            }
        }
        return any ? value : 0;
    }
}
