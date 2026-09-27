package org.telegram.messenger.plugins;

import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PrimeGram: the {@code preferences} exteraGram's PluginsController hands out, as plugins use it.
 *
 * <p>exteraGram keeps every plugin setting as its own key, {@code plugin_setting_<id>_<key>}, in
 * one SharedPreferences file, and some plugins read and write those keys directly - through
 * {@code get_private_field(PluginsController.getInstance(), "preferences")} - rather than through
 * {@code get_setting}. PrimeGram stores a plugin's settings as one JSON object instead
 * ({@link PrimePluginStore}). This maps the exteraGram key space onto that store, so both views
 * see the same values; any other key passes through to the app's main settings untouched.
 *
 * <p>A key names a plugin id and a setting, joined by {@code _} - and both can contain {@code _}.
 * The split is resolved against the installed plugins, longest id first; a key that matches no
 * installed plugin is treated as an ordinary key.
 */
public final class PrimeExteraPreferences implements SharedPreferences {

    private static final String PREFIX = "plugin_setting_";

    private final SharedPreferences base;

    public PrimeExteraPreferences(SharedPreferences base) {
        this.base = base;
    }

    /** {pluginId, settingKey}, or null when the key is not one of ours. */
    private static String[] split(String key) {
        if (key == null || !key.startsWith(PREFIX)) {
            return null;
        }
        final String rest = key.substring(PREFIX.length());
        String best = null;
        for (PrimePlugin plugin : PrimePluginsController.getInstance().getPlugins()) {
            final String id = plugin.id();
            if (id != null && rest.length() > id.length() + 1 && rest.startsWith(id) && rest.charAt(id.length()) == '_'
                    && (best == null || id.length() > best.length())) {
                best = id;
            }
        }
        return best == null ? null : new String[]{best, rest.substring(best.length() + 1)};
    }

    private static JSONObject settings(String pluginId) {
        try {
            return new JSONObject(PrimePluginStore.getSettingsJson(pluginId));
        } catch (Throwable e) {
            return new JSONObject();
        }
    }

    private static Object value(String key) {
        final String[] parts = split(key);
        if (parts == null) {
            return null;
        }
        final JSONObject json = settings(parts[0]);
        return json.has(parts[1]) ? json.opt(parts[1]) : null;
    }

    @Override
    public Map<String, ?> getAll() {
        final Map<String, Object> all = new HashMap<>(base.getAll());
        for (PrimePlugin plugin : PrimePluginsController.getInstance().getPlugins()) {
            final JSONObject json = settings(plugin.id());
            final java.util.Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                final String k = keys.next();
                all.put(PREFIX + plugin.id() + "_" + k, json.opt(k));
            }
        }
        return all;
    }

    @Override
    public String getString(String key, String defValue) {
        if (split(key) == null) {
            return base.getString(key, defValue);
        }
        final Object v = value(key);
        return v == null || v == JSONObject.NULL ? defValue : String.valueOf(v);
    }

    @Override
    public Set<String> getStringSet(String key, Set<String> defValues) {
        if (split(key) == null) {
            return base.getStringSet(key, defValues);
        }
        final Object v = value(key);
        if (v instanceof org.json.JSONArray) {
            final Set<String> set = new HashSet<>();
            for (int i = 0; i < ((org.json.JSONArray) v).length(); i++) {
                set.add(((org.json.JSONArray) v).optString(i));
            }
            return set;
        }
        return defValues;
    }

    @Override
    public int getInt(String key, int defValue) {
        if (split(key) == null) {
            return base.getInt(key, defValue);
        }
        final Object v = value(key);
        return v instanceof Number ? ((Number) v).intValue() : defValue;
    }

    @Override
    public long getLong(String key, long defValue) {
        if (split(key) == null) {
            return base.getLong(key, defValue);
        }
        final Object v = value(key);
        return v instanceof Number ? ((Number) v).longValue() : defValue;
    }

    @Override
    public float getFloat(String key, float defValue) {
        if (split(key) == null) {
            return base.getFloat(key, defValue);
        }
        final Object v = value(key);
        return v instanceof Number ? ((Number) v).floatValue() : defValue;
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        if (split(key) == null) {
            return base.getBoolean(key, defValue);
        }
        final Object v = value(key);
        return v instanceof Boolean ? (Boolean) v : defValue;
    }

    @Override
    public boolean contains(String key) {
        return split(key) == null ? base.contains(key) : value(key) != null;
    }

    @Override
    public Editor edit() {
        return new PluginEditor(base.edit());
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        base.registerOnSharedPreferenceChangeListener(listener);
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        base.unregisterOnSharedPreferenceChangeListener(listener);
    }

    private static final class PluginEditor implements Editor {
        private final Editor base;
        private final List<Object[]> ops = new ArrayList<>(); // {pluginId, key, value|REMOVE}
        private static final Object REMOVE = new Object();

        PluginEditor(Editor base) {
            this.base = base;
        }

        private Editor put(String key, Object value, Runnable fallback) {
            final String[] parts = split(key);
            if (parts == null) {
                fallback.run();
            } else {
                ops.add(new Object[]{parts[0], parts[1], value});
            }
            return this;
        }

        @Override
        public Editor putString(String key, String value) {
            return put(key, value, () -> base.putString(key, value));
        }

        @Override
        public Editor putStringSet(String key, Set<String> values) {
            return put(key, values == null ? null : new org.json.JSONArray(values), () -> base.putStringSet(key, values));
        }

        @Override
        public Editor putInt(String key, int value) {
            return put(key, value, () -> base.putInt(key, value));
        }

        @Override
        public Editor putLong(String key, long value) {
            return put(key, value, () -> base.putLong(key, value));
        }

        @Override
        public Editor putFloat(String key, float value) {
            return put(key, (double) value, () -> base.putFloat(key, value));
        }

        @Override
        public Editor putBoolean(String key, boolean value) {
            return put(key, value, () -> base.putBoolean(key, value));
        }

        @Override
        public Editor remove(String key) {
            return put(key, REMOVE, () -> base.remove(key));
        }

        @Override
        public Editor clear() {
            // Clearing exteraGram's file would wipe every plugin's settings and the app's own
            // preferences with them; no plugin needs that, so only the pending edits are dropped.
            ops.clear();
            return this;
        }

        private void writePluginOps() {
            final Map<String, JSONObject> touched = new HashMap<>();
            for (Object[] op : ops) {
                final String id = (String) op[0];
                JSONObject json = touched.get(id);
                if (json == null) {
                    touched.put(id, json = settings(id));
                }
                try {
                    if (op[2] == REMOVE || op[2] == null) {
                        json.remove((String) op[1]);
                    } else {
                        json.put((String) op[1], op[2]);
                    }
                } catch (Throwable ignore) {
                }
            }
            for (Map.Entry<String, JSONObject> e : touched.entrySet()) {
                PrimePluginStore.setSettingsJson(e.getKey(), e.getValue().toString());
            }
            ops.clear();
        }

        @Override
        public boolean commit() {
            writePluginOps();
            return base.commit();
        }

        @Override
        public void apply() {
            writePluginOps();
            base.apply();
        }
    }
}
