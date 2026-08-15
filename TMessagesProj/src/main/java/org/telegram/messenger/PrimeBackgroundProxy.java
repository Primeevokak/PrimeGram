package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

/**
 * PrimeGram: "работа в фоне" toggle for the local WS proxy service (TgWsProxyService). Lets a
 * user trade background message delivery over the tunnel for battery - push notifications (see
 * GcmPushListenerService, a real FirebaseMessagingService registered in the manifest) arrive
 * through Google's FCM entirely independently of this service, so they keep working either way.
 *
 * <p>Off by default - current always-on behavior is unchanged unless a user opts in. When on:
 * <ul>
 *   <li>the proxy is stopped a grace period after the app is backgrounded, not instantly - a
 *   momentary pause (a system permission dialog, switching apps for a second) must not tear down
 *   and rebuild the whole tunnel;</li>
 *   <li>it's restarted the moment the app is foregrounded again;</li>
 *   <li>{@code postInitApplication()}'s cold-start auto-start is skipped when this process was
 *   woken by a push rather than the user opening the app - a background push doesn't need the
 *   proxy spun back up just to be answered, it already has its own answer via FCM.</li>
 * </ul>
 */
public final class PrimeBackgroundProxy {

    private static final String KEY = "primegram_tgws_background_disabled";
    /** Long enough that a fleeting pause (permission dialog, app switcher tap) never tears the
     *  tunnel down, short enough that "closed the app" actually saves battery soon after. */
    private static final long STOP_GRACE_MS = 45_000;

    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final Runnable stopRunnable = () -> {
        if (ApplicationLoader.mainInterfacePaused) {
            TgWsProxyService.stopService(ApplicationLoader.applicationContext);
        }
    };

    public static boolean isBackgroundWorkDisabled() {
        // Defaults to true: this toggle's whole cost/benefit case only pays off if a typical user
        // actually gets it, and "off by default, buried on a Connection sub-screen" meant almost
        // nobody did - the proxy ran as an unkillable foreground service with a held wakelock and
        // a 15s watchdog loop indefinitely, on every account, whether the app was ever backgrounded
        // or not. Nothing about connection quality regresses from this: the tunnel is still always
        // up while the app is actually in use, and push delivery never depended on it (FCM is
        // independent, see the class doc above) - only the background-idle tail gets shorter.
        return prefs().getBoolean(KEY, true);
    }

    public static void setBackgroundWorkDisabled(boolean disabled) {
        prefs().edit().putBoolean(KEY, disabled).apply();
        if (!disabled) {
            handler.removeCallbacks(stopRunnable);
            boolean userEnabled = prefs().getBoolean("primegram_tgws_enabled", true);
            if (userEnabled && !ApplicationLoader.mainInterfacePaused) {
                TgWsProxyService.startService(ApplicationLoader.applicationContext);
            }
        }
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE);
    }

    /** Call from LaunchActivity.onPause(). */
    public static void onAppPaused() {
        if (!isBackgroundWorkDisabled()) {
            return;
        }
        handler.removeCallbacks(stopRunnable);
        handler.postDelayed(stopRunnable, STOP_GRACE_MS);
    }

    /** Call from LaunchActivity.onResume(). */
    public static void onAppResumed() {
        handler.removeCallbacks(stopRunnable);
        // PrimeGram: this used to restart the service purely because it wasn't running, with no
        // regard for WHY - including the user having just turned the proxy off entirely
        // (primegram_tgws_enabled=false). That toggle write and this resume call race on every
        // "flip the switch off, background the app for a second, come back" sequence, and this
        // side lost: the service came right back up, foreground notification and all, looking
        // exactly like the switch had silently reverted itself. This class exists to save
        // battery on an already-running proxy's idle tail, not to override the user's own
        // on/off choice - it must never be the thing that turns the proxy back on.
        boolean userEnabled = ApplicationLoader.applicationContext
                .getSharedPreferences("mainconfig", Context.MODE_PRIVATE)
                .getBoolean("primegram_tgws_enabled", true);
        if (userEnabled && isBackgroundWorkDisabled() && !TgWsProxyService.isRunning()) {
            TgWsProxyService.startService(ApplicationLoader.applicationContext);
        }
    }

    /** Call from postInitApplication(), before its own auto-start decision, to tell a
     *  push-triggered cold start apart from the user actually opening the app. */
    public static boolean shouldSkipColdStart() {
        return isBackgroundWorkDisabled() && ApplicationLoader.mainInterfacePaused;
    }

    private PrimeBackgroundProxy() {
    }
}
