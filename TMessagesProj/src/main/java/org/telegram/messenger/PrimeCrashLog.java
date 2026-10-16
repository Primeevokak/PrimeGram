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
        // Off the startup path: a binder call plus, for each new entry, reading a trace file.
        Utilities.globalQueue.postRunnable(PrimeCrashLog::importSystemExitReasons, 5000);
    }

    // ---- deaths the handler above never sees ----
    //
    // A native crash (tgnet, media codecs, Python, hook trampolines) kills the process without any
    // Java exception, and an ANR has none at all - both left this log empty while Android itself
    // offered to send a report. The system keeps why each process of this app died
    // (ApplicationExitInfo, Android 11+); read the new entries on the next launch and file them here.

    private static final String KEY_LAST_EXIT_TS = "last_exit_ts";

    private static void importSystemExitReasons() {
        if (android.os.Build.VERSION.SDK_INT < 30 || !isEnabled()) {
            return;
        }
        try {
            final android.app.ActivityManager am = (android.app.ActivityManager) ApplicationLoader.applicationContext
                    .getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) {
                return;
            }
            final String pkg = ApplicationLoader.applicationContext.getPackageName();
            final java.util.List<android.app.ApplicationExitInfo> infos = am.getHistoricalProcessExitReasons(pkg, 0, 32);
            final SharedPreferences p = prefs();
            final long lastSeen = p.getLong(KEY_LAST_EXIT_TS, 0);
            long newest = lastSeen;
            // The list is newest first; file them oldest first so the log stays chronological.
            for (int i = infos.size() - 1; i >= 0; i--) {
                final android.app.ApplicationExitInfo info = infos.get(i);
                if (info.getTimestamp() <= lastSeen) {
                    continue;
                }
                newest = Math.max(newest, info.getTimestamp());
                final String kind = describeReason(info, pkg);
                if (kind == null) {
                    continue;
                }
                final StringBuilder sb = new StringBuilder();
                sb.append("== ").append(timestamp(info.getTimestamp())).append("  система: ").append(kind)
                        .append("  [").append(info.getProcessName()).append("] ==\n");
                if (info.getDescription() != null) {
                    sb.append(info.getDescription()).append('\n');
                }
                sb.append("PSS ").append(info.getPss() / 1024).append(" МБ, RSS ").append(info.getRss() / 1024).append(" МБ\n");
                final String trace = readTrace(info);
                if (trace != null) {
                    sb.append(trace);
                }
                appendEntry(sb.toString());
            }
            if (newest != lastSeen) {
                p.edit().putLong(KEY_LAST_EXIT_TS, newest).apply();
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /** What to call this death, or null when it is not worth a crash-log entry. */
    @android.annotation.TargetApi(30)
    private static String describeReason(android.app.ApplicationExitInfo info, String pkg) {
        switch (info.getReason()) {
            case android.app.ApplicationExitInfo.REASON_CRASH_NATIVE:
                return "нативный краш";
            case android.app.ApplicationExitInfo.REASON_ANR:
                return "ANR (приложение зависло)";
            case android.app.ApplicationExitInfo.REASON_INITIALIZATION_FAILURE:
                return "сбой запуска";
            case android.app.ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE:
                return "убит за перерасход ресурсов";
            case android.app.ApplicationExitInfo.REASON_CRASH:
                // The main process's Java crashes are already here from the handler; other
                // processes (push, plugin helpers) never ran it.
                return pkg.equals(info.getProcessName()) ? null : "Java-краш в другом процессе";
            default:
                return null;
        }
    }

    /**
     * ANR: the thread dump, cut to the main thread's stack. Native crash: the tombstone, which on
     * Android 12+ is a protobuf - its readable strings (signal, abort message, frame symbols and
     * libraries) are enough to see where it died.
     */
    @android.annotation.TargetApi(30)
    private static String readTrace(android.app.ApplicationExitInfo info) {
        final byte[] data;
        try (java.io.InputStream in = info.getTraceInputStream()) {
            if (in == null) {
                return null;
            }
            final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            final byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0 && out.size() < 2 * 1024 * 1024) {
                out.write(buf, 0, n);
            }
            data = out.toByteArray();
        } catch (Throwable e) {
            return null;
        }
        if (info.getReason() == android.app.ApplicationExitInfo.REASON_ANR) {
            final String text = new String(data, java.nio.charset.StandardCharsets.UTF_8);
            final int main = text.indexOf("\"main\"");
            if (main >= 0) {
                final int end = text.indexOf("\n\n", main);
                return clip(text.substring(main, end > main ? end : text.length()), 8000);
            }
            return clip(text, 8000);
        }
        return clip(printableStrings(data), 8000);
    }

    private static String printableStrings(byte[] data) {
        final StringBuilder sb = new StringBuilder();
        int start = -1;
        for (int i = 0; i <= data.length; i++) {
            final boolean printable = i < data.length && data[i] >= 0x20 && data[i] < 0x7f;
            if (printable) {
                if (start < 0) {
                    start = i;
                }
            } else if (start >= 0) {
                if (i - start >= 6) {
                    sb.append(new String(data, start, i - start, java.nio.charset.StandardCharsets.US_ASCII)).append('\n');
                }
                start = -1;
            }
        }
        return sb.toString();
    }

    private static String clip(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "\n… (обрезано)";
    }

    private static String timestamp(long ms) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new java.util.Date(ms));
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
        appendEntry(entry);
    }

    private static final java.util.Set<String> recordedStalls = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    /**
     * A non-fatal stall worth a report: what {@code busy} was doing while the caller gave up
     * waiting on it. Recorded once per reason per process, written off the calling thread.
     */
    public static void recordStall(String reason, Thread busy) {
        if (!isEnabled() || !recordedStalls.add(reason)) {
            return;
        }
        final StringBuilder sb = new StringBuilder();
        sb.append("== ").append(timestamp()).append("  ").append(currentScreenName())
                .append("  зависание: ").append(reason).append(" ==\n")
                .append("поток ").append(busy.getName()).append(" (").append(busy.getState()).append("):\n");
        for (StackTraceElement element : busy.getStackTrace()) {
            sb.append("  at ").append(element).append('\n');
        }
        final String entry = sb.toString();
        Utilities.globalQueue.postRunnable(() -> appendEntry(entry));
    }

    private static synchronized void appendEntry(String entry) {
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
