package org.telegram.messenger;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * PrimeGram: local-only storage for user-authored chat themes (pattern + gradient), built by
 * {@code ui.PrimeCustomThemeActivity}.
 *
 * <p>Deliberately independent of Telegram's own wallpaper sync (`TL_account.saveWallPaper`
 * et al.) - these are device-local presets, not account-level state, so they work offline and
 * survive without ever touching the server. Stored as a JSON array in its own prefs file rather
 * than piggybacking on `mainconfig`, so this one growing list never collides with anything else
 * that reads/writes that file wholesale.
 */
public final class PrimeCustomWallpapers {

    public static final int PATTERN_NONE = 0;
    public static final int PATTERN_BUILTIN = 1;
    public static final int PATTERN_CUSTOM_IMAGE = 2;

    public static final class Entry {
        public String id;
        public String name;
        public int color1;
        public int color2;
        public int color3;
        public int rotation;
        public float intensity = 1.0f;
        public int patternKind = PATTERN_NONE;
        /** For PATTERN_BUILTIN: the server pattern's slug. For PATTERN_CUSTOM_IMAGE: the local
         *  cached file path {@link org.telegram.ui.Components.WallpaperUpdater} already wrote. */
        public String patternRef;
        public long createdAt;
    }

    private static final String PREFS = "primegram_custom_wallpapers";
    private static final String KEY_LIST = "list";

    private PrimeCustomWallpapers() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);
    }

    public static List<Entry> list() {
        List<Entry> result = new ArrayList<>();
        try {
            String raw = prefs().getString(KEY_LIST, null);
            if (raw == null) {
                return result;
            }
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Entry e = new Entry();
                e.id = o.optString("id");
                e.name = o.optString("name");
                e.color1 = o.optInt("color1");
                e.color2 = o.optInt("color2");
                e.color3 = o.optInt("color3");
                e.rotation = o.optInt("rotation");
                e.intensity = (float) o.optDouble("intensity", 1.0);
                e.patternKind = o.optInt("patternKind", PATTERN_NONE);
                e.patternRef = o.optString("patternRef", null);
                e.createdAt = o.optLong("createdAt");
                result.add(e);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        return result;
    }

    /** Inserts a new entry, or - if {@code entry.id} already matches a saved one (editing an
     *  existing theme rather than creating a fresh one) - replaces it in place, same id and
     *  original creation time, so re-saving an edit doesn't duplicate it in the list. */
    public static Entry save(Entry entry) {
        List<Entry> all = list();
        boolean isEdit = entry.id != null;
        if (entry.id == null) {
            entry.id = Utilities.MD5(entry.name + "_" + System.nanoTime() + "_" + Math.random());
        }
        if (isEdit) {
            for (int i = 0; i < all.size(); i++) {
                if (entry.id.equals(all.get(i).id)) {
                    if (entry.createdAt == 0) {
                        entry.createdAt = all.get(i).createdAt;
                    }
                    all.set(i, entry);
                    persist(all);
                    return entry;
                }
            }
        }
        if (entry.createdAt == 0) {
            entry.createdAt = System.currentTimeMillis();
        }
        all.add(entry);
        persist(all);
        return entry;
    }

    public static void delete(String id) {
        if (id == null) {
            return;
        }
        List<Entry> all = list();
        for (int i = all.size() - 1; i >= 0; i--) {
            if (id.equals(all.get(i).id)) {
                all.remove(i);
            }
        }
        persist(all);
    }

    private static void persist(List<Entry> all) {
        try {
            JSONArray arr = new JSONArray();
            for (Entry e : all) {
                JSONObject o = new JSONObject();
                o.put("id", e.id);
                o.put("name", e.name);
                o.put("color1", e.color1);
                o.put("color2", e.color2);
                o.put("color3", e.color3);
                o.put("rotation", e.rotation);
                o.put("intensity", e.intensity);
                o.put("patternKind", e.patternKind);
                if (e.patternRef != null) {
                    o.put("patternRef", e.patternRef);
                }
                o.put("createdAt", e.createdAt);
                arr.put(o);
            }
            prefs().edit().putString(KEY_LIST, arr.toString()).apply();
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }
}
