package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * PrimeGram: makes the very first frame (drawn from {@code Theme.TMessages.Start} before any
 * app UI exists) match whichever theme - dark or light - the user actually has selected inside
 * Telegram, instead of the OS's own light/dark setting.
 *
 * <p>{@code values-night/styles.xml} already gives that starting theme a dark variant, but it
 * only ever activates from the system's day/night switch - a user on a light-mode phone with a
 * dark Telegram theme still got a plain white flash on every cold start, since this app's own
 * theme system ({@code Theme.java}) is entirely custom and was never wired to
 * {@code Configuration.uiMode} at all.
 *
 * <p>Deliberately a raw {@link SharedPreferences} read, not {@code MessagesController}'s -
 * {@code MessagesController.getGlobalMainSettings()} constructs a full controller instance on
 * first touch, and this has to be readable from {@code ApplicationLoader.onCreate()} before
 * anything else in the app has had a chance to initialize.
 */
public final class PrimeLaunchTheme {

    private static final String PREFS = "mainconfig";
    private static final String KEY = "primegram_launch_theme_dark";

    private PrimeLaunchTheme() {
    }

    /** Called from {@link org.telegram.ui.ActionBar.Theme} every time the active theme changes,
     *  so this always reflects the most recently applied one by the next cold start. */
    public static void cacheDark(boolean dark) {
        try {
            prefs().edit().putBoolean(KEY, dark).apply();
        } catch (Throwable ignored) {
        }
    }

    public static boolean isDark() {
        try {
            return prefs().getBoolean(KEY, false);
        } catch (Throwable t) {
            return false;
        }
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Call once, as early as possible in {@code Application.onCreate()} - forcing the app's
     *  process-wide night-mode resource resolution before any Activity's starting window is
     *  requested is what actually changes which of the two {@code styles.xml} variants gets
     *  picked for that very first frame. */
    public static void applyEarly() {
        try {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                    isDark() ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                            : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        } catch (Throwable ignored) {
        }
    }
}
