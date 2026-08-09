package org.telegram.messenger.blocks;

import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

/**
 * PrimeGram Blocks: where a `.pr` script's on/off state lives.
 *
 * <p>Deliberately a separate class from {@code PrimePluginStore}, not a shared/prefixed reuse of
 * it, even though the shape is identical - see the "PrimeGram Blocks" plan §5.3. The plugin store
 * also carries plugin-specific concepts (import-dependency tracking for crash attribution) that
 * have no `.pr` equivalent, and the two systems are intentionally kept at different trust levels,
 * so sharing storage code would blur a boundary the rest of this system is carefully drawing.
 */
public final class PrimeBlockStore {

    private static final String PREFS = "primegram_blocks";
    private static final String ENABLED_PREFIX = "enabled_";

    private PrimeBlockStore() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);
    }

    /** Off until the user says so, including right after installing it - same rule as `.plugin`. */
    public static boolean isEnabled(String scriptId) {
        return prefs().getBoolean(ENABLED_PREFIX + scriptId, false);
    }

    public static void setEnabled(String scriptId, boolean enabled) {
        prefs().edit().putBoolean(ENABLED_PREFIX + scriptId, enabled).apply();
    }

    public static void forget(String scriptId) {
        prefs().edit().remove(ENABLED_PREFIX + scriptId).apply();
    }
}
