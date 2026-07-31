package org.telegram.messenger.plugins;

import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

/**
 * PrimeGram: where a plugin's settings live.
 *
 * <p>Java owns the storage rather than Python, for one reason: the settings screen has to work when
 * the plugin is switched off. A disabled plugin has no Python side at all - its module is gone and
 * its objects collected - and yet the user must still be able to see what they had set, and turn it
 * back on to the state they left it in. Storage that outlives the code it belongs to is the only
 * arrangement in which that is true.
 *
 * <p>The shape is one JSON object per plugin. Not one preference per key: plugins invent their own
 * key names, and a shared preferences file that anyone can write any name into is a file where one
 * badly named key can shadow another plugin's. A whole object keyed by plugin id cannot collide.
 */
public final class PrimePluginStore {

    private static final String PREFS = "primegram_plugins";
    private static final String SETTINGS_PREFIX = "settings_";
    private static final String ENABLED_PREFIX = "enabled_";

    private PrimePluginStore() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);
    }

    /** The plugin's settings as a JSON object, {@code "{}"} when it has never saved any. */
    public static String getSettingsJson(String pluginId) {
        return prefs().getString(SETTINGS_PREFIX + pluginId, "{}");
    }

    public static void setSettingsJson(String pluginId, String json) {
        prefs().edit().putString(SETTINGS_PREFIX + pluginId, json == null ? "{}" : json).apply();
    }

    /**
     * One value out of a plugin's settings object.
     *
     * <p>Typed getters rather than a parsed map, because the caller is a settings row that knows
     * exactly what it expects: a switch wants a boolean and a value of any other shape is a value
     * it should ignore rather than crash on. Reparsing the object each time is affordable - this
     * runs while drawing a settings screen, not on any hot path.
     */
    public static boolean getBoolean(String pluginId, String key, boolean fallback) {
        try {
            final org.json.JSONObject json = new org.json.JSONObject(getSettingsJson(pluginId));
            return json.has(key) ? json.getBoolean(key) : fallback;
        } catch (Throwable e) {
            return fallback;
        }
    }

    public static int getInt(String pluginId, String key, int fallback) {
        try {
            final org.json.JSONObject json = new org.json.JSONObject(getSettingsJson(pluginId));
            return json.has(key) ? json.getInt(key) : fallback;
        } catch (Throwable e) {
            return fallback;
        }
    }

    public static String getString(String pluginId, String key, String fallback) {
        try {
            final org.json.JSONObject json = new org.json.JSONObject(getSettingsJson(pluginId));
            return json.has(key) ? json.getString(key) : fallback;
        } catch (Throwable e) {
            return fallback;
        }
    }

    /** Writes one value back, leaving the rest of the object alone. */
    public static void put(String pluginId, String key, Object value) {
        try {
            final org.json.JSONObject json = new org.json.JSONObject(getSettingsJson(pluginId));
            json.put(key, value);
            setSettingsJson(pluginId, json.toString());
        } catch (Throwable e) {
            org.telegram.messenger.FileLog.e(e);
        }
    }

    /** Forgets a plugin entirely - settings and the on/off flag both. Used when it is deleted. */
    public static void forget(String pluginId) {
        prefs().edit()
                .remove(SETTINGS_PREFIX + pluginId)
                .remove(ENABLED_PREFIX + pluginId)
                .apply();
    }

    /**
     * Whether the user wants this plugin running. Newly installed plugins default to on: the user
     * has just chosen to install this specific file, and asking them a second time in a second
     * place is asking the same question twice.
     */
    public static boolean isEnabled(String pluginId) {
        return prefs().getBoolean(ENABLED_PREFIX + pluginId, true);
    }

    public static void setEnabled(String pluginId, boolean enabled) {
        prefs().edit().putBoolean(ENABLED_PREFIX + pluginId, enabled).apply();
    }
}
