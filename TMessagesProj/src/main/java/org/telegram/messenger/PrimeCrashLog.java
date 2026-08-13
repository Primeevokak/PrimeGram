package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * PrimeGram: "Диагностика" crash log - independent of {@link BuildVars#LOGS_ENABLED} (the verbose
 * trace switch), this only ever writes something when the process is actually about to die from
 * an uncaught exception, never on ordinary app/session events. That distinction matters: a past
 * bug in this fork copied the verbose log on every logout, which looked like "logs" but had
 * nothing to do with crashes - conflating the two is exactly what this class is built not to do.
 *
 * <p>Crashes are written synchronously to {@link SharedPreferences} the instant they're caught,
 * since the process may not survive long enough for anything deferred to run - unlike
 * {@link PrimePerfMonitor}'s in-memory log, this one has to survive the death of the process that
 * wrote it.
 */
public final class PrimeCrashLog {

    private static final String PREFS = "primegram_crash_log";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_LOG = "log";
    private static final int MAX_CRASHES = 10;

    private static Boolean enabledCache;
    private static boolean installed;
    private static Thread.UncaughtExceptionHandler wrappedHandler;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled() {
        if (enabledCache == null) {
            final SharedPreferences p = prefs();
            if (!p.contains(KEY_ENABLED)) {
                // First read of this preference ever: default ON for a genuinely fresh install,
                // default OFF for an existing install that's just upgrading to a build with this
                // feature - silently turning it on for people already running the app is exactly
                // the kind of surprise the old always-on-log-copy-at-logout bug was.
                p.edit().putBoolean(KEY_ENABLED, isFreshInstall()).apply();
            }
            enabledCache = p.getBoolean(KEY_ENABLED, false);
        }
        return enabledCache;
    }

    public static void setEnabled(boolean enabled) {
        if (isEnabled() == enabled) {
            return;
        }
        enabledCache = enabled;
        prefs().edit().putBoolean(KEY_ENABLED, enabled).apply();
        applyState();
    }

    private static boolean isFreshInstall() {
        try {
            final PackageInfo info = ApplicationLoader.applicationContext.getPackageManager()
                    .getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
            return info.firstInstallTime == info.lastUpdateTime;
        } catch (Throwable t) {
            return false;
        }
    }

    // ---- lifecycle ----

    /** Call once, early in the process's life (e.g. ApplicationLoader.onCreate). Chains onto
     *  whatever default handler is already installed rather than replacing it. */
    public static void install() {
        if (installed) {
            return;
        }
        installed = true;
        applyState();
    }

    private static void applyState() {
        if (!installed || !isEnabled() || wrappedHandler != null) {
            return;
        }
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        wrappedHandler = (thread, throwable) -> {
            try {
                recordCrash(thread, throwable);
            } catch (Throwable ignored) {
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            }
        };
        Thread.setDefaultUncaughtExceptionHandler(wrappedHandler);
        // Deliberately never removes the handler again even if the setting is later turned off -
        // a crash can happen the instant after a toggle flips, costs nothing to keep listening,
        // and recordCrash() re-checks isEnabled() before writing anything.
    }

    // ---- capture ----

    private static void recordCrash(Thread thread, Throwable throwable) {
        if (!isEnabled()) {
            return;
        }
        final StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        final String entry = "== " + timestamp() + "  " + currentScreenName() + "  [" + thread.getName() + "] ==\n" + sw;

        final SharedPreferences p = prefs();
        final JSONArray entries = new JSONArray();
        try {
            final JSONArray existing = new JSONArray(p.getString(KEY_LOG, "[]"));
            final int start = Math.max(0, existing.length() - (MAX_CRASHES - 1));
            for (int i = start; i < existing.length(); i++) {
                entries.put(existing.getString(i));
            }
        } catch (JSONException ignored) {
        }
        entries.put(entry);
        // commit(), not apply(): the process may not survive long enough for an async write to
        // land - this needs to be on disk before uncaughtException() returns.
        p.edit().putString(KEY_LOG, entries.toString()).commit();
    }

    private static String currentScreenName() {
        try {
            final org.telegram.ui.ActionBar.BaseFragment fragment = org.telegram.ui.LaunchActivity.getLastFragment();
            return fragment != null ? fragment.getClass().getSimpleName() : "?";
        } catch (Throwable t) {
            return "?";
        }
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new java.util.Date());
    }

    // ---- reading from the settings screen ----

    public static synchronized String dump() {
        try {
            final JSONArray entries = new JSONArray(prefs().getString(KEY_LOG, "[]"));
            if (entries.length() == 0) {
                return "Крашей не зафиксировано.";
            }
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < entries.length(); i++) {
                if (i > 0) {
                    sb.append("\n\n");
                }
                sb.append(entries.getString(i));
            }
            return sb.toString();
        } catch (JSONException e) {
            return "Крашей не зафиксировано.";
        }
    }

    public static synchronized void clear() {
        prefs().edit().remove(KEY_LOG).apply();
    }

    private PrimeCrashLog() {
    }
}
