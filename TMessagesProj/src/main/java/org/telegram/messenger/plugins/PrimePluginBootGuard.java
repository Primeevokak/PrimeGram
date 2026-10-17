package org.telegram.messenger.plugins;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;

/**
 * PrimeGram: a way out of a crash loop caused by plugins.
 *
 * <p>Starting the plugin engine patches ART from inside the process (AliuHook/LSPlant), and on some
 * firmware that has killed the app with a native SIGSEGV seconds after launch, again and again. A
 * user cannot open the plugins screen to switch anything off when the app dies in 4 seconds, so the
 * app does it for them: if the previous launch never got as far as "alive and settled" and neither
 * did the one before it, plugins stay off until the user turns them back on from the plugins screen.
 *
 * <p>"Settled" means the process lived {@link #SETTLE_MS} or the app went to the background
 * normally. One unexplained death is tolerated - the crash is timing-dependent and a second launch
 * usually gets through.
 */
public final class PrimePluginBootGuard {

    private static final String PREFS = "prime_plugin_boot";
    private static final long SETTLE_MS = 20_000;
    private static final int MAX_FAILURES = 2;

    private static volatile boolean safeModeShownThisProcess;

    private PrimePluginBootGuard() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Whether plugins are held off because of repeated startup crashes. */
    public static boolean isSafeMode() {
        return prefs().getBoolean("safe", false);
    }

    /**
     * Called right before plugins start. Returns false to skip them this launch.
     * Records that a launch is in progress, to be cleared by {@link #markSettled()}.
     */
    public static synchronized boolean enterBoot() {
        final SharedPreferences prefs = prefs();
        if (prefs.getBoolean("safe", false)) {
            return false;
        }
        int failures = prefs.getInt("fails", 0);
        if (prefs.getLong("started", 0) != 0) {
            failures++;
            if (failures >= MAX_FAILURES) {
                prefs.edit().putBoolean("safe", true).putInt("fails", failures).putLong("started", 0).commit();
                return false;
            }
        }
        // commit(): the process may die within seconds, apply() would not have reached the disk.
        prefs.edit().putInt("fails", failures).putLong("started", System.currentTimeMillis()).commit();
        AndroidUtilities.runOnUIThread(PrimePluginBootGuard::markSettled, SETTLE_MS);
        return true;
    }

    /** The launch survived: forget any earlier failures. */
    public static void markSettled() {
        final SharedPreferences prefs = prefs();
        if (prefs.getLong("started", 0) != 0 || prefs.getInt("fails", 0) != 0) {
            prefs.edit().putLong("started", 0).putInt("fails", 0).apply();
        }
    }

    /** The user switched plugins back on. */
    public static void leaveSafeMode() {
        prefs().edit().putBoolean("safe", false).putInt("fails", 0).putLong("started", 0).apply();
    }

    /** True once per process, so the notice is not repeated on every screen. */
    public static boolean shouldAnnounce() {
        if (safeModeShownThisProcess || !isSafeMode()) {
            return false;
        }
        safeModeShownThisProcess = true;
        return true;
    }
}
