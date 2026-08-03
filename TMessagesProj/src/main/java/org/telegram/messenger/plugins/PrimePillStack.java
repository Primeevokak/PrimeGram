package org.telegram.messenger.plugins;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * PrimeGram: the small pill-shaped widgets that can sit above the chat list - exteraGram calls
 * the same idea a "pill stack". There is no built-in pill here on purpose: a weather chip or a
 * price ticker is exactly the kind of thing one plugin wants and the next finds noise, and
 * hardcoding one forecloses whatever a plugin author would rather show in that spot (cache size,
 * proxy latency, an unread count from their own feature - anything). This is the empty stage;
 * plugins are the acts.
 *
 * <p>Same shape as {@link PrimePluginMenuItems}: Python owns the data, publishes the whole set
 * here on every change via {@link #setPills}, and a tap routes back through {@link
 * PrimePluginHooks#onPillClick}.
 */
public final class PrimePillStack {

    public static final class Pill {
        public final String id;
        public final String pluginId;
        public final String text;
        public final String icon;
        public final String colorHex;
        public final int priority;

        Pill(String id, String pluginId, String text, String icon, String colorHex, int priority) {
            this.id = id;
            this.pluginId = pluginId;
            this.text = text;
            this.icon = icon;
            this.colorHex = colorHex;
            this.priority = priority;
        }
    }

    private static volatile List<Pill> pills = Collections.emptyList();
    private static volatile Runnable onChanged;

    private PrimePillStack() {
    }

    /** The view listens here so it can redraw the moment a plugin's set changes, instead of
     *  polling on some interval that would either lag a fast-changing pill or waste battery on a
     *  slow one. */
    public static void setOnChangedListener(Runnable listener) {
        onChanged = listener;
    }

    public static void setPills(String json) {
        final List<Pill> parsed = new ArrayList<>();
        try {
            final JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                final JSONObject row = array.optJSONObject(i);
                if (row == null) {
                    continue;
                }
                final String id = row.optString("id");
                final String text = row.optString("text");
                if (id.isEmpty() || text.isEmpty()) {
                    continue;
                }
                parsed.add(new Pill(id, row.optString("plugin_id"), text,
                        row.optString("icon", null), row.optString("color", null),
                        row.optInt("priority", 0)));
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        parsed.sort(Comparator.comparingInt((Pill p) -> p.priority).reversed());
        pills = parsed;
        final Runnable listener = onChanged;
        if (listener != null) {
            ApplicationLoader.applicationHandler.post(listener);
        }
    }

    public static List<Pill> getAll() {
        return pills;
    }

    public static void click(Pill pill) {
        PrimePluginHooks.onPillClick(pill.id);
    }
}
