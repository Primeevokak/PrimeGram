package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * PrimeGram: persisted on/off switch for the on-screen "Логи" overlay (see
 * {@code PrimeLogOverlay}) - an in-app, on-device alternative to plugging in a computer and
 * running {@code adb logcat}, for diagnosing a bug on a device that isn't near one. Off by
 * default; flipping it on starts {@link PrimeLogCollector} tailing this process's own log into
 * memory, flipping it off stops that collector entirely so it costs nothing while unused.
 */
public final class PrimeLogOverlayState {

    private static final String PREFS = "primegram_log_overlay";

    private static Boolean enabledCache;

    private PrimeLogOverlayState() {
    }

    public static boolean isEnabled() {
        if (enabledCache == null) {
            enabledCache = prefs().getBoolean("enabled", false);
        }
        return enabledCache;
    }

    public static void setEnabled(boolean enabled) {
        if (isEnabled() == enabled) {
            return;
        }
        enabledCache = enabled;
        prefs().edit().putBoolean("enabled", enabled).apply();
        if (enabled) {
            PrimeLogCollector.start();
        } else {
            PrimeLogCollector.stop();
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.primeLogOverlayChanged);
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
