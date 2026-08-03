package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * PrimeGram: turns off the app's own proxy while a system VPN is up, and back on when it drops -
 * running both at once usually means double-tunneling for no reason, and on some networks the
 * combination is what breaks the connection rather than fixing it.
 *
 * <p>Identifying *which* VPN app is active - so a chosen few can be exempt from the auto-off,
 * the way a firewall's allowlist works - is best-effort: {@code VpnTransportInfo.getPackageName()}
 * only discloses the real package name to the VPN app itself or to a caller holding the
 * privileged {@code NETWORK_SETTINGS} permission, neither of which a normal install of this app
 * is. When the name cannot be read, the VPN is treated as unidentified and the global toggle
 * decides - the whitelist is a bonus for the cases where the platform does share it, not the
 * mechanism the feature depends on.
 */
public final class PrimeVpnGuard {

    private static final String KEY_ENABLED = "primegram_vpn_guard_enabled";
    private static final String KEY_WHITELIST = "primegram_vpn_guard_whitelist";
    private static final String KEY_WAS_AUTO_DISABLED = "primegram_vpn_guard_auto_disabled";

    private static boolean registered;
    private static ConnectivityManager.NetworkCallback callback;

    private PrimeVpnGuard() {
    }

    private static SharedPreferences prefs() {
        return MessagesController.getGlobalMainSettings();
    }

    public static boolean isEnabled() {
        return prefs().getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(boolean enabled) {
        prefs().edit().putBoolean(KEY_ENABLED, enabled).apply();
        if (enabled) {
            start(ApplicationLoader.applicationContext);
        }
    }

    public static Set<String> getWhitelist() {
        return new HashSet<>(prefs().getStringSet(KEY_WHITELIST, new HashSet<>()));
    }

    public static boolean isWhitelisted(String packageName) {
        return getWhitelist().contains(packageName);
    }

    public static void toggleWhitelist(String packageName) {
        final Set<String> set = getWhitelist();
        if (!set.remove(packageName)) {
            set.add(packageName);
        }
        prefs().edit().putStringSet(KEY_WHITELIST, set).apply();
    }

    /** Apps that request Android's VPN service permission - what a person would actually pick
     *  from when asked "which VPN apps should be left alone". */
    public static List<android.content.pm.ApplicationInfo> listInstalledVpnApps(Context context) {
        final List<android.content.pm.ApplicationInfo> result = new ArrayList<>();
        final PackageManager pm = context.getPackageManager();
        final Intent probe = new Intent("android.net.VpnService");
        final List<ResolveInfo> resolved;
        try {
            resolved = pm.queryIntentServices(probe, PackageManager.GET_META_DATA);
        } catch (Throwable t) {
            return result;
        }
        final Set<String> seen = new HashSet<>();
        for (ResolveInfo info : resolved) {
            if (info.serviceInfo == null || info.serviceInfo.applicationInfo == null) {
                continue;
            }
            final String pkg = info.serviceInfo.packageName;
            if (pkg == null || !seen.add(pkg) || pkg.equals(ApplicationLoader.applicationContext.getPackageName())) {
                continue;
            }
            result.add(info.serviceInfo.applicationInfo);
        }
        return result;
    }

    public static synchronized void start(Context context) {
        if (registered || context == null || !isEnabled()) {
            return;
        }
        final ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return;
        }
        callback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    onVpnStateChanged(true, resolveVpnPackage(capabilities));
                }
            }

            @Override
            public void onLost(Network network) {
                onVpnStateChanged(false, null);
            }
        };
        try {
            final NetworkRequest request = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                    .build();
            cm.registerNetworkCallback(request, callback);
            registered = true;
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    public static synchronized void stop(Context context) {
        if (!registered || context == null || callback == null) {
            return;
        }
        final ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            try {
                cm.unregisterNetworkCallback(callback);
            } catch (Throwable ignore) {
            }
        }
        registered = false;
        callback = null;
    }

    private static String resolveVpnPackage(NetworkCapabilities capabilities) {
        if (Build.VERSION.SDK_INT < 33) {
            return null;
        }
        try {
            final Object transportInfo = capabilities.getTransportInfo();
            if (transportInfo == null) {
                return null;
            }
            // Reflection: android.net.VpnTransportInfo#getPackageName() is API 33+ and only ever
            // returns a real value to the VPN app itself or a NETWORK_SETTINGS holder - neither of
            // which this app is, in the common case. A hard compile-time reference would still be
            // fine on minSdk 24 via the SDK_INT guard above, but reflection keeps this call from
            // being the one place that needs its own lint suppression for an API that, for us,
            // realistically always returns null anyway.
            final java.lang.reflect.Method m = transportInfo.getClass().getMethod("getPackageName");
            final Object name = m.invoke(transportInfo);
            return name instanceof String ? (String) name : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void onVpnStateChanged(boolean vpnActive, String vpnPackage) {
        if (!isEnabled()) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            final SharedPreferences.Editor editor = prefs().edit();
            if (vpnActive) {
                if (vpnPackage != null && isWhitelisted(vpnPackage)) {
                    return;
                }
                final SharedPreferences globalPrefs = MessagesController.getGlobalMainSettings();
                final boolean proxyOn = globalPrefs.getBoolean("proxy_enabled", false);
                if (proxyOn) {
                    globalPrefs.edit().putBoolean("proxy_enabled", false).apply();
                    org.telegram.tgnet.ConnectionsManager.setProxySettings(false, "", 1080, "", "", "");
                    editor.putBoolean(KEY_WAS_AUTO_DISABLED, true).apply();
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
                }
            } else {
                if (prefs().getBoolean(KEY_WAS_AUTO_DISABLED, false)) {
                    editor.putBoolean(KEY_WAS_AUTO_DISABLED, false).apply();
                    if (SharedConfig.currentProxy != null) {
                        final SharedPreferences globalPrefs = MessagesController.getGlobalMainSettings();
                        globalPrefs.edit().putBoolean("proxy_enabled", true).apply();
                        final SharedConfig.ProxyInfo p = SharedConfig.currentProxy;
                        org.telegram.tgnet.ConnectionsManager.setProxySettings(true, p.address, p.port, p.username, p.password, p.secret);
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
                    }
                }
            }
        });
    }
}
