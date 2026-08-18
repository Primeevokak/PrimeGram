package org.telegram.messenger;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.provider.Settings;

/**
 * PrimeGram: the missing airplane-mode / mobile-data checks {@code isConnectedToWiFi()}'s
 * neighborhood never grew - used to drive {@link org.telegram.ui.PrimeNetworkBannerWrapper}'s
 * two nag banners. Each banner variant has its own independent "dismissed until" timestamp
 * in {@code "mainconfig"} prefs, mirroring {@code TempSubStore}'s own now-vs-expiresAt idiom.
 */
public final class PrimeConnectivityState {

    private static final String KEY_AIRPLANE_DISMISSED_UNTIL = "primegram_banner_airplane_until";
    private static final String KEY_MOBILE_DATA_DISMISSED_UNTIL = "primegram_banner_mobile_data_until";
    private static final long DISMISS_DURATION_MS = 8L * 60 * 60 * 1000;

    private PrimeConnectivityState() {
    }

    public static boolean isAirplaneModeOn() {
        try {
            return Settings.Global.getInt(ApplicationLoader.applicationContext.getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** True when the device has no active cellular data path - either mobile data is
     *  toggled off in settings, or there's simply no SIM/cellular network in range. Either
     *  way the practical effect for the user is the same: this device can't reach the
     *  network over cellular right now. */
    public static boolean isMobileDataOff() {
        try {
            ConnectivityManager cm = (ConnectivityManager) ApplicationLoader.applicationContext
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return false;
            }
            android.net.Network[] networks = cm.getAllNetworks();
            for (android.net.Network network : networks) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                        && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean hasAnyConnectivity() {
        try {
            ConnectivityManager cm = (ConnectivityManager) ApplicationLoader.applicationContext
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return true;
            }
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Exception e) {
            return true;
        }
    }

    public static boolean shouldShowAirplaneBanner() {
        if (!isAirplaneModeOn() || hasAnyConnectivity()) {
            return false;
        }
        return System.currentTimeMillis() >= prefs().getLong(KEY_AIRPLANE_DISMISSED_UNTIL, 0);
    }

    public static boolean shouldShowMobileDataBanner() {
        if (isAirplaneModeOn() || hasAnyConnectivity() || !isMobileDataOff() || ApplicationLoader.isConnectedToWiFi()) {
            return false;
        }
        return System.currentTimeMillis() >= prefs().getLong(KEY_MOBILE_DATA_DISMISSED_UNTIL, 0);
    }

    public static void dismissAirplaneBanner() {
        prefs().edit().putLong(KEY_AIRPLANE_DISMISSED_UNTIL, System.currentTimeMillis() + DISMISS_DURATION_MS).apply();
    }

    public static void dismissMobileDataBanner() {
        prefs().edit().putLong(KEY_MOBILE_DATA_DISMISSED_UNTIL, System.currentTimeMillis() + DISMISS_DURATION_MS).apply();
    }

    private static android.content.SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE);
    }
}
