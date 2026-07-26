package org.telegram.messenger;

import android.content.SharedPreferences;

/**
 * Crash-safe guard for experimental features that could crash the whole
 * process (new render backends, native codec paths, etc.) in ways ordinary
 * try/catch can't protect against.
 *
 * The pattern: before entering the risky code path, synchronously commit an
 * "attempting" flag to disk. If the process dies mid-attempt, that flag is
 * left set. On the next app start, {@link #checkAndArm} sees the leftover
 * flag, concludes the last attempt crashed, and auto-disables the feature —
 * so a bad render backend (or any other risky toggle) self-heals to the safe
 * state instead of crash-looping every launch.
 *
 * Uses {@link SharedPreferences.Editor#commit()} (not apply()) specifically
 * for the "attempting" write, because it must be durable on disk *before*
 * the risky code runs — apply()'s async write gives no such guarantee.
 */
public class CrashSafeToggle {

    /**
     * Deliberately a standalone prefs file rather than
     * {@code MessagesController.getGlobalMainSettings()}: that call constructs the whole
     * MessagesController for account 0, and {@link #checkAndArm} has to run at the very
     * start of {@code ApplicationLoader.onCreate()} — before native libs and UserConfig
     * are initialised — where doing so crashes the process on launch.
     */
    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("primegram_toggles", android.content.Context.MODE_PRIVATE);
    }

    /**
     * Call once per process lifetime, as early as possible (before the
     * guarded feature could run this session). Detects an unclean prior
     * attempt and auto-disables the feature if found.
     *
     * @return true if the feature is currently enabled by the user AND safe
     *         to arm this session (no crash detected from last attempt).
     */
    public static boolean checkAndArm(String key) {
        SharedPreferences p = prefs();
        boolean pendingFromLastRun = p.getBoolean(key + "_attempting", false);
        if (pendingFromLastRun) {
            // No FileLog here: this runs before FileLog is initialised.
            android.util.Log.e("CrashSafeToggle", "'" + key + "' left mid-attempt from a previous run — auto-disabling");
            p.edit()
                    .putBoolean(key + "_enabled", false)
                    .putBoolean(key + "_attempting", false)
                    .putBoolean(key + "_auto_disabled", true)
                    .commit();
            return false;
        }
        return p.getBoolean(key + "_enabled", false);
    }

    /** Call synchronously, immediately before entering the risky code path. */
    public static void beginAttempt(String key) {
        prefs().edit().putBoolean(key + "_attempting", true).commit();
    }

    /** Call after the risky code path has run at least once without crashing. */
    public static void confirmSuccess(String key) {
        prefs().edit().putBoolean(key + "_attempting", false).commit();
    }

    /** True if this feature was auto-disabled by {@link #checkAndArm} due to a detected crash. */
    public static boolean wasAutoDisabled(String key) {
        return prefs().getBoolean(key + "_auto_disabled", false);
    }

    /** Call after showing the user a "this was disabled due to a crash" notice. */
    public static void acknowledgeAutoDisabled(String key) {
        prefs().edit().putBoolean(key + "_auto_disabled", false).apply();
    }

    /** User-facing enable/disable — does not take effect until the next {@link #checkAndArm} (i.e. app restart). */
    public static void setEnabled(String key, boolean enabled) {
        prefs().edit().putBoolean(key + "_enabled", enabled).apply();
    }

    public static boolean isEnabled(String key) {
        return prefs().getBoolean(key + "_enabled", false);
    }
}
