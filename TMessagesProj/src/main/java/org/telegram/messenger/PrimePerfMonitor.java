package org.telegram.messenger;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.view.FrameMetrics;
import android.view.Window;

import androidx.annotation.RequiresApi;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Locale;

/**
 * PrimeGram: "Диагностика" live performance monitor - while enabled, samples real per-frame
 * timings off the foreground Activity's Window (the same {@link FrameMetrics} API
 * {@code RefreshRateController}/{@code FrameMetricsOverlayView} already use elsewhere in this
 * fork) and appends one summary line every couple of seconds - fps, jank frames, a breakdown of
 * which pipeline phase (layout, draw, animation, GPU...) is actually eating the frame budget, the
 * current screen, and a stack trace of the main thread sampled at the worst frame of the window -
 * to a rolling in-memory log. The user reproduces whatever feels laggy, then opens Settings and
 * copies the log out; the goal is a log that names the offending call, not just the symptom. Off
 * by default; {@link #bind}/{@link #unbind} are cheap when disabled, so nothing runs unless
 * someone is actively diagnosing a problem.
 */
public final class PrimePerfMonitor {

    private static final String PREFS = "primegram_perf_monitor";
    private static final long SAMPLE_WINDOW_MS = 2000;
    /** A frame worse than one 60Hz vsync - simple and device-independent enough for a log line. */
    private static final long JANK_THRESHOLD_NS = 16_700_000L;
    private static final int MAX_LOG_LINES = 500;
    private static final int STACK_DEPTH = 10;

    private static Boolean enabledCache;

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
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.primePerfMonitorChanged);
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---- rolling log ----

    private static final ArrayDeque<String> log = new ArrayDeque<>();

    public static synchronized String dump() {
        if (log.isEmpty()) {
            return "Лог пуст. Включите мониторинг, попользуйтесь приложением (особенно тем местом, где что-то дёргается), затем откройте этот экран снова.";
        }
        final StringBuilder sb = new StringBuilder();
        for (String line : log) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    public static synchronized void clear() {
        log.clear();
    }

    private static synchronized void appendLine(String line) {
        log.addLast(line);
        while (log.size() > MAX_LOG_LINES) {
            log.removeFirst();
        }
    }

    // ---- lifecycle: one Activity window at a time ----

    private static Activity boundActivity;
    private static boolean observing;

    private static final NotificationCenter.NotificationCenterDelegate observer = new NotificationCenter.NotificationCenterDelegate() {
        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.primePerfMonitorChanged) {
                applyState();
            }
        }
    };

    /** Call once the Activity's window exists (e.g. LaunchActivity.onCreate). */
    public static void bind(Activity activity) {
        boundActivity = activity;
        if (!observing) {
            NotificationCenter.getGlobalInstance().addObserver(observer, NotificationCenter.primePerfMonitorChanged);
            observing = true;
        }
        applyState();
    }

    /** Call from the same Activity's onDestroy. */
    public static void unbind(Activity activity) {
        if (boundActivity == activity) {
            stop();
            boundActivity = null;
        }
    }

    private static void applyState() {
        if (boundActivity == null) {
            return;
        }
        if (isEnabled()) {
            start(boundActivity);
        } else {
            stop();
        }
    }

    // ---- sampling ----

    private static HandlerThread metricsThread;
    private static Handler metricsHandler;
    private static Window.OnFrameMetricsAvailableListener listener;
    private static final Handler uiHandler = new Handler(Looper.getMainLooper());

    private static long windowStartMs;
    private static int frameCount;
    private static int jankCount;
    private static long worstFrameNs;
    private static long sumFrameNs;

    // Per-phase sums, so a window's log line says WHICH part of the frame is expensive rather
    // than just that the frame as a whole was slow.
    private static long sumInputNs, sumAnimNs, sumLayoutNs, sumDrawNs, sumSyncNs, sumCmdNs, sumSwapNs, sumGpuNs;

    // Captured at the moment a frame becomes the worst-in-window - approximate (the callback
    // fires slightly after the frame it describes), but a sustained slow operation shows up in
    // consecutive frames, so it reliably catches the same offending call.
    private static String worstFrameScreen = "?";
    private static String worstFrameStack = "";

    private static final Runnable flushTick = new Runnable() {
        @Override
        public void run() {
            flush();
            uiHandler.postDelayed(this, SAMPLE_WINDOW_MS);
        }
    };

    @RequiresApi(Build.VERSION_CODES.N)
    private static void start(Activity activity) {
        if (listener != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return;
        }
        resetWindow();
        metricsThread = new HandlerThread("PrimePerfMonitor");
        metricsThread.start();
        metricsHandler = new Handler(metricsThread.getLooper());
        listener = (window, fm, dropCount) -> {
            final long totalNs = fm.getMetric(FrameMetrics.TOTAL_DURATION);
            if (totalNs <= 0) {
                return;
            }
            final boolean isNewWorst;
            synchronized (PrimePerfMonitor.class) {
                frameCount++;
                sumFrameNs += totalNs;
                sumInputNs += metric(fm, FrameMetrics.INPUT_HANDLING_DURATION);
                sumAnimNs += metric(fm, FrameMetrics.ANIMATION_DURATION);
                sumLayoutNs += metric(fm, FrameMetrics.LAYOUT_MEASURE_DURATION);
                sumDrawNs += metric(fm, FrameMetrics.DRAW_DURATION);
                sumSyncNs += metric(fm, FrameMetrics.SYNC_DURATION);
                sumCmdNs += metric(fm, FrameMetrics.COMMAND_ISSUE_DURATION);
                sumSwapNs += metric(fm, FrameMetrics.SWAP_BUFFERS_DURATION);
                if (Build.VERSION.SDK_INT >= 31) {
                    sumGpuNs += metric(fm, FrameMetrics.GPU_DURATION);
                }
                isNewWorst = totalNs > worstFrameNs;
                if (isNewWorst) {
                    worstFrameNs = totalNs;
                }
                if (totalNs > JANK_THRESHOLD_NS) {
                    jankCount++;
                }
            }
            // Outside the lock: touches the main thread's live stack and walks fragments, both
            // more expensive than the counters above and pointless to do for every frame.
            if (isNewWorst) {
                final String screen = currentScreenName();
                final String stack = captureMainThreadStack();
                synchronized (PrimePerfMonitor.class) {
                    worstFrameScreen = screen;
                    worstFrameStack = stack;
                }
            }
        };
        try {
            activity.getWindow().addOnFrameMetricsAvailableListener(listener, metricsHandler);
        } catch (Throwable t) {
            listener = null;
            return;
        }
        appendLine(header("Мониторинг запущен"));
        uiHandler.removeCallbacks(flushTick);
        uiHandler.postDelayed(flushTick, SAMPLE_WINDOW_MS);
    }

    private static long metric(FrameMetrics fm, int key) {
        final long v = fm.getMetric(key);
        return v > 0 ? v : 0;
    }

    private static void stop() {
        uiHandler.removeCallbacks(flushTick);
        if (listener != null && boundActivity != null) {
            try {
                boundActivity.getWindow().removeOnFrameMetricsAvailableListener(listener);
            } catch (Throwable ignored) {
            }
        }
        if (listener != null) {
            listener = null;
            appendLine(header("Мониторинг остановлен"));
        }
        if (metricsThread != null) {
            metricsThread.quitSafely();
            metricsThread = null;
        }
    }

    private static void resetWindow() {
        windowStartMs = SystemClock.elapsedRealtime();
        frameCount = 0;
        jankCount = 0;
        worstFrameNs = 0;
        sumFrameNs = 0;
        sumInputNs = sumAnimNs = sumLayoutNs = sumDrawNs = sumSyncNs = sumCmdNs = sumSwapNs = sumGpuNs = 0;
        worstFrameScreen = "?";
        worstFrameStack = "";
    }

    private static synchronized void flush() {
        if (frameCount == 0) {
            resetWindow();
            return;
        }
        final long elapsedMs = Math.max(1, SystemClock.elapsedRealtime() - windowStartMs);
        final double fps = frameCount / (elapsedMs / 1000.0);
        final double avgMs = (sumFrameNs / (double) frameCount) / 1_000_000.0;
        final double worstMs = worstFrameNs / 1_000_000.0;
        final int jankPct = Math.round(100f * jankCount / frameCount);
        final Runtime rt = Runtime.getRuntime();
        final long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        final long maxMb = rt.maxMemory() / (1024 * 1024);

        final String[] phaseNames = {"input", "anim", "layout", "draw", "sync", "cmd", "swap", "gpu"};
        final long[] phaseSums = {sumInputNs, sumAnimNs, sumLayoutNs, sumDrawNs, sumSyncNs, sumCmdNs, sumSwapNs, sumGpuNs};
        int dominant = 0;
        for (int i = 1; i < phaseSums.length; i++) {
            if (phaseSums[i] > phaseSums[dominant]) {
                dominant = i;
            }
        }
        final StringBuilder phases = new StringBuilder();
        for (int i = 0; i < phaseSums.length; i++) {
            if (phaseSums[i] <= 0) {
                continue;
            }
            if (phases.length() > 0) {
                phases.append(' ');
            }
            phases.append(phaseNames[i]).append('=').append(String.format(Locale.US, "%.1f", phaseSums[i] / (double) frameCount / 1_000_000.0));
        }

        appendLine(String.format(Locale.US,
                "%s  экран=%s  fps=%.1f  кадров=%d  джанк=%d(%d%%)  ср=%.1fмс  худший=%.1fмс  доминирует=%s  |%s|  память=%dМБ/%dМБ  потоки=%d",
                timestamp(), worstFrameScreen, fps, frameCount, jankCount, jankPct, avgMs, worstMs,
                phaseSums[dominant] > 0 ? phaseNames[dominant] : "-", phases, usedMb, maxMb, Thread.activeCount()));

        if (!worstFrameStack.isEmpty() && worstMs > JANK_THRESHOLD_NS / 1_000_000.0) {
            appendLine("    худший кадр (" + worstFrameScreen + "): " + worstFrameStack);
        }

        resetWindow();
    }

    /** Top of the main thread's stack at the moment of the worst frame this window - the actual
     *  call chain, not a guess from aggregate numbers. Best-effort: the JVM/ART pauses the target
     *  thread briefly to snapshot it, which is why this is only done once per window. */
    private static String captureMainThreadStack() {
        try {
            final StackTraceElement[] trace = Looper.getMainLooper().getThread().getStackTrace();
            final StringBuilder sb = new StringBuilder();
            final int limit = Math.min(trace.length, STACK_DEPTH);
            for (int i = 0; i < limit; i++) {
                if (i > 0) {
                    sb.append(" < ");
                }
                sb.append(trace[i].getClassName()).append('.').append(trace[i].getMethodName());
            }
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static String currentScreenName() {
        try {
            final org.telegram.ui.ActionBar.BaseFragment fragment = org.telegram.ui.LaunchActivity.getLastFragment();
            return fragment != null ? fragment.getClass().getSimpleName() : "?";
        } catch (Throwable t) {
            return "?";
        }
    }

    private static String header(String what) {
        return "== " + timestamp() + "  " + what + " ==";
    }

    private static String timestamp() {
        return new SimpleDateFormat("HH:mm:ss", Locale.US).format(new java.util.Date());
    }

    private PrimePerfMonitor() {
    }
}
