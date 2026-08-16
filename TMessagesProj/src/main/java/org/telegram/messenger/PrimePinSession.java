package org.telegram.messenger;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.WindowManager;

/**
 * PrimeGram: process-local PIN session gate. All-static, mirrors the shape of the vault's
 * own static-utility style since there is exactly one session per process.
 *
 * <p>{@link #guardAfterSuper(Activity)} must be called by every secondary entry-point
 * Activity (bubble, share, popup notification, external action, VoIP permission) right
 * after {@code super.onCreate()} - {@link org.telegram.ui.LaunchActivity} is handled
 * separately since it's also where the app resumes after unlock.
 */
public final class PrimePinSession {

    private static final long BACKGROUND_LOCK_DELAY_MS = 500L; // debounces transient config-change teardown/rebuild

    private static volatile boolean unlocked;
    private static volatile long unlockDeadlineElapsedRealtime;
    private static volatile long backgroundedAtElapsedRealtime;
    private static Intent pendingLaunch;
    private static final Object LOCK = new Object();

    private static final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private static final Runnable backgroundLockRunnable = PrimePinSession::onBackgroundLockFired;

    private PrimePinSession() {
    }

    public static boolean isEnabled() {
        return prefs().getBoolean("pin_enabled", false);
    }

    public static void setEnabled(boolean enabled) {
        prefs().edit().putBoolean("pin_enabled", enabled).apply();
    }

    /** Settings-screen query only - does real file I/O (opens and decrypts the vault state), so
     *  callers should not use this on a hot path. */
    public static boolean hasEmergencyPin() {
        if (!isEnabled()) {
            return false;
        }
        try {
            PrimePinVault vault = new PrimePinVault(ApplicationLoader.applicationContext);
            PrimePinVault.VaultInspection inspection = vault.inspect();
            return inspection.state == PrimePinVault.VaultState.ENROLLED && inspection.hasEmergency;
        } catch (Throwable t) {
            return false;
        }
    }

    public static PrimePinLockPolicy getLockPolicy() {
        return PrimePinLockPolicy.fromStorageKey(prefs().getString("pin_lock_policy", null), PrimePinLockPolicy.getDefault());
    }

    public static void setLockPolicy(PrimePinLockPolicy policy) {
        prefs().edit().putString("pin_lock_policy", policy.storageKey).apply();
    }

    public static void applyPolicyChange() {
        reevaluatePolicy();
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("primegram_pin", Context.MODE_PRIVATE);
    }

    public static boolean isUnlocked() {
        if (!isEnabled()) {
            return true;
        }
        if (unlocked && expired(SystemClock.elapsedRealtime())) {
            // The deadline is asked, not trusted: a posted callback can be starved while the
            // process is frozen (Doze, background restriction), so re-check on every call
            // rather than relying solely on the scheduled lock.
            lock();
        }
        return isAccessAllowed(hasAuthenticatedAccount(), unlocked);
    }

    static boolean isAccessAllowed(boolean hasAuthenticatedAccount, boolean sessionUnlocked) {
        return !hasAuthenticatedAccount || sessionUnlocked;
    }

    private static boolean hasAuthenticatedAccount() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            try {
                UserConfig config = UserConfig.getInstance(a);
                if (config.isConfigLoaded() && config.isClientActivated()) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        // Fallback for cold start, before UserConfig.loadConfig() has run: read the raw prefs
        // the same way UserConfig itself will, since the gate must decide before that class
        // has had a chance to.
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            String prefsName = a == 0 ? "userconfing" : "userconfig" + a;
            try {
                SharedPreferences p = ApplicationLoader.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
                String user = p.getString("user", null);
                if (user != null && !user.isEmpty()) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    public static void unlock() {
        unlocked = true;
        armExpiry();
    }

    static void lock() {
        unlocked = false;
        unlockDeadlineElapsedRealtime = 0L;
    }

    public static void noteUserInteraction() {
        if (unlocked) {
            armExpiry();
        }
    }

    static void reevaluatePolicy() {
        if (unlocked) {
            armExpiry();
        }
    }

    public static void onScreenOff() {
        if (getLockPolicy().locksOnScreenOff()) {
            lock();
        }
    }

    public static void scheduleBackgroundLock() {
        if (!isEnabled() || !unlocked) {
            return;
        }
        backgroundedAtElapsedRealtime = SystemClock.elapsedRealtime();
        handler.removeCallbacks(backgroundLockRunnable);
        if (getLockPolicy().locksOnBackground()) {
            handler.postDelayed(backgroundLockRunnable, BACKGROUND_LOCK_DELAY_MS);
        }
    }

    public static void cancelBackgroundLock() {
        handler.removeCallbacks(backgroundLockRunnable);
    }

    private static void onBackgroundLockFired() {
        if (getLockPolicy().locksOnBackground()) {
            lock();
        }
    }

    private static void armExpiry() {
        long inactivity = getLockPolicy().inactivityMillis();
        long session = getLockPolicy().sessionMillis();
        long now = SystemClock.elapsedRealtime();
        long deadline = Long.MAX_VALUE;
        if (inactivity > 0) {
            deadline = Math.min(deadline, now + inactivity);
        }
        if (session > 0) {
            deadline = Math.min(deadline, now + session);
        }
        unlockDeadlineElapsedRealtime = deadline == Long.MAX_VALUE ? 0L : deadline;
    }

    private static boolean expired(long now) {
        return unlockDeadlineElapsedRealtime != 0L && now >= unlockDeadlineElapsedRealtime;
    }

    /** @return true if the caller must {@code return} immediately after this call - the
     *  activity is being redirected and finished. */
    public static boolean guardAfterSuper(Activity activity) {
        if (isUnlocked()) {
            return false;
        }
        try {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } catch (Throwable ignored) {
        }
        redirectToGate(activity);
        activity.finish();
        return true;
    }

    public static synchronized void redirectToGate(Activity activity) {
        synchronized (LOCK) {
            pendingLaunch = new Intent(activity.getIntent());
            pendingLaunch.setClass(activity, org.telegram.ui.LaunchActivity.class);
        }
        Intent gate = new Intent(activity, org.telegram.ui.PrimePinGateActivity.class);
        gate.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(gate);
        activity.overridePendingTransition(0, 0);
    }

    public static Intent takePendingLaunch(Activity activity) {
        synchronized (LOCK) {
            Intent intent = pendingLaunch;
            pendingLaunch = null;
            if (intent != null) {
                return intent;
            }
        }
        return new Intent(activity, org.telegram.ui.LaunchActivity.class);
    }
}
