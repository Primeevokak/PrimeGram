package org.telegram.messenger.plugins;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PrimeGram: a per-plugin journal - everything a plugin did that we can see: its own log lines,
 * every hook invocation (with how long it ran) and every error with its traceback. Shown from the
 * plugin's settings screen, so a problem can be read on the phone instead of dug out of the
 * system log.
 *
 * <p>Kept in memory only, bounded per plugin; a busy hook cannot grow it without limit.
 */
public final class PrimePluginLog {

    private static final int MAX_ENTRIES = 2500;
    private static final Pattern BRACKET_ID = Pattern.compile("^\\[([^\\] ]+)\\] ?");
    private static final Pattern PLUGIN_ID = Pattern.compile("^(?:elyx )?plugin ([^ :]+)(?:[ :]|$)");

    private static final Map<String, ArrayDeque<String>> buffers = new HashMap<>();
    private static final SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    private PrimePluginLog() {
    }

    public static void add(String pluginId, String text) {
        if (pluginId == null || text == null) {
            return;
        }
        synchronized (buffers) {
            ArrayDeque<String> buffer = buffers.get(pluginId);
            if (buffer == null) {
                buffer = new ArrayDeque<>();
                buffers.put(pluginId, buffer);
            }
            if (buffer.size() >= MAX_ENTRIES) {
                buffer.removeFirst();
            }
            buffer.addLast(format.format(new java.util.Date()) + "  " + text);
        }
    }

    /** A line that names its plugin only in the text ("[id] ..." or "plugin id ..."). */
    public static void addAuto(String text) {
        if (text == null) {
            return;
        }
        Matcher m = BRACKET_ID.matcher(text);
        if (m.find()) {
            add(m.group(1), text.substring(m.end()));
            return;
        }
        m = PLUGIN_ID.matcher(text);
        if (m.find()) {
            add(m.group(1), text);
        }
    }

    public static String dump(String pluginId) {
        final StringBuilder sb = new StringBuilder();
        synchronized (buffers) {
            final ArrayDeque<String> buffer = buffers.get(pluginId);
            if (buffer != null) {
                for (String line : buffer) {
                    sb.append(line).append('\n');
                }
            }
        }
        return sb.toString();
    }

    public static int size(String pluginId) {
        synchronized (buffers) {
            final ArrayDeque<String> buffer = buffers.get(pluginId);
            return buffer == null ? 0 : buffer.size();
        }
    }

    public static void clear(String pluginId) {
        synchronized (buffers) {
            buffers.remove(pluginId);
        }
    }
}
