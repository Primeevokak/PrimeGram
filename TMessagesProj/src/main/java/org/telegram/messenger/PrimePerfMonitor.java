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
    /** How often the background sampler snapshots the main thread. See the class doc on
     *  {@link #hotSpots} for why this exists alongside the single worst-frame stack below. */
    private static final long HOTSPOT_SAMPLE_INTERVAL_MS = 6;
    /** How many top frames go into a hot-spot bucket's key - deep enough to tell two different
     *  call sites apart, shallow enough that the same call site always hashes the same way. */
    private static final int HOTSPOT_KEY_DEPTH = 6;
    /** Below this share of the window's samples, a bucket is noise, not a lead. */
    private static final int HOTSPOT_MIN_PERCENT = 8;
    private static final int HOTSPOT_MAX_REPORTED = 3;

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

    /** A one-off timing (e.g. how long a chat took to show its messages), logged next to the
     *  frame summaries so a slow moment and its cause read in order. No-op while disabled. */
    public static void event(String line) {
        if (!isEnabled()) {
            return;
        }
        appendLine(new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(new java.util.Date()) + " " + line);
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

    // ---- hot-spot sampling ----
    //
    // The single worst-frame stack above is a lottery ticket, not a profile: it's one asynchronous
    // snapshot taken after the FrameMetrics callback fires, which lands some unknown, possibly
    // large delay after the frame it's blamed on - on a screen with many bad frames in a row (a
    // dragged animation, a busy chat) it's just as likely to catch the main thread mid-idle
    // (Looper.loop, HardwareRenderer.nSyncAndDrawFrame) as it is to catch the actual expensive
    // call, which is exactly the noise seen in real logs. A background thread that repeatedly
    // samples the main thread's stack while monitoring runs turns that lottery into a real
    // statistical profile: whichever call chain the thread is "caught in" most often across many
    // samples is overwhelmingly likely to be where the time is actually going, the same principle
    // every sampling profiler (including Android Studio's own CPU profiler) relies on - and it
    // finds the offending call inside ANY file, including ones like ChatMessageCell that are too
    // large and sensitive to instrument by hand one call site at a time.
    private static Thread samplerThread;
    private static volatile boolean samplerRunning;
    private static final java.util.Map<String, Integer> hotSpots = new java.util.HashMap<>();
    private static int hotSpotSamples;

    // What the main thread was doing in each sample: nothing (parked in the looper), waiting on
    // something else (a lock, a latch, disk, SQLite, a Binder call into the system), or actually
    // computing. The split answers "is the UI slow, or is it stuck behind someone" directly.
    // STATE_RENDER is its own bucket: in HardwareRenderer.nSyncAndDrawFrame the main thread is
    // blocked on the RenderThread still issuing the previous frame's GPU commands - the "cmd"
    // phase - so a big share there means frames are too heavy to draw, not to build.
    private static final int STATE_IDLE = 0, STATE_WAITING = 1, STATE_WORKING = 2, STATE_RENDER = 3;
    private static final int[] stateSamples = new int[4];

    private static int classify(Thread thread, StackTraceElement[] trace) {
        final String top = trace[0].getClassName() + '.' + trace[0].getMethodName();
        if (top.equals("android.os.MessageQueue.nativePollOnce")) {
            return STATE_IDLE;
        }
        if (top.equals("android.graphics.HardwareRenderer.nSyncAndDrawFrame")) {
            return STATE_RENDER;
        }
        final Thread.State state = thread.getState();
        if (state == Thread.State.BLOCKED || state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING) {
            return STATE_WAITING;
        }
        // Native blocking calls report RUNNABLE, so the stack has to say it.
        final int depth = Math.min(trace.length, 4);
        for (int i = 0; i < depth; i++) {
            final String cls = trace[i].getClassName();
            final String method = trace[i].getMethodName();
            if (cls.startsWith("org.telegram.SQLite.") || cls.startsWith("libcore.io.")
                    || cls.equals("android.os.BinderProxy") || cls.equals("java.io.FileDescriptor")
                    || cls.equals("sun.misc.Unsafe") || cls.equals("jdk.internal.misc.Unsafe")
                    || method.equals("park") || method.equals("sleep") || method.equals("wait")) {
                return STATE_WAITING;
            }
        }
        return STATE_WORKING;
    }

    // Inclusive time per app method: in how many busy samples the method was anywhere on the
    // stack. Hot spots above key on the top frames, and drawing a chat spreads its time over
    // hundreds of distinct leaves (a text run, a clip, an image) - no single leaf clears the bar,
    // so the report came back empty exactly when it mattered. Counting every app frame answers
    // "which of our methods is the time spent under", the way a profiler's call tree does.
    private static final java.util.Map<String, Integer> inclusive = new java.util.HashMap<>();
    private static final java.util.HashSet<String> sampleMethods = new java.util.HashSet<>();
    private static final int INCLUSIVE_MIN_PERCENT = 5;
    private static final int INCLUSIVE_MAX_REPORTED = 10;

    private static void recordInclusive(StackTraceElement[] trace) {
        synchronized (PrimePerfMonitor.class) {
            sampleMethods.clear();
            for (StackTraceElement frame : trace) {
                final String cls = frame.getClassName();
                if (!cls.startsWith("org.telegram.") || cls.startsWith("org.telegram.messenger.PrimePerfMonitor")) {
                    continue;
                }
                // Lambdas and synthetic classes are renamed by R8; the enclosing class is enough.
                final int synthetic = cls.indexOf("$$");
                final String name = (synthetic > 0 ? cls.substring(13, synthetic) : cls.substring(13)) + '.' + frame.getMethodName();
                if (sampleMethods.add(name)) {
                    inclusive.merge(name, 1, Integer::sum);
                }
            }
        }
    }

    // ---- who redraws message cells ----
    //
    // Scrolling a hardware-rendered list only moves recorded display lists; a cell's onDraw runs
    // again only if something invalidated it. When onDraw dominates a scroll, the question is
    // who keeps invalidating - so every few invalidates the caller's stack is kept and tallied.
    public static volatile boolean trackingInvalidates;
    private static int cellInvalidates;
    private static int cellDraws;
    private static final java.util.Map<String, Integer> invalidators = new java.util.HashMap<>();
    private static final int INVALIDATE_SAMPLE_EVERY = 8;
    private static final int INVALIDATOR_DEPTH = 4;

    public static void noteCellDraw() {
        synchronized (PrimePerfMonitor.class) {
            cellDraws++;
        }
    }

    public static void noteCellInvalidate() {
        final boolean sample;
        synchronized (PrimePerfMonitor.class) {
            sample = cellInvalidates++ % INVALIDATE_SAMPLE_EVERY == 0;
        }
        if (!sample) {
            return;
        }
        final StackTraceElement[] trace = new Throwable().getStackTrace();
        // Skip this method and the cell's own invalidate(); start at whoever called it.
        int i = 0;
        while (i < trace.length && (trace[i].getClassName().equals("org.telegram.messenger.PrimePerfMonitor")
                || trace[i].getMethodName().equals("invalidate"))) {
            i++;
        }
        final StringBuilder key = new StringBuilder();
        for (int n = 0; i < trace.length && n < INVALIDATOR_DEPTH; i++, n++) {
            if (n > 0) {
                key.append(" < ");
            }
            String cls = trace[i].getClassName();
            if (cls.startsWith("org.telegram.")) {
                cls = cls.substring(13);
            }
            key.append(cls).append('.').append(trace[i].getMethodName());
        }
        synchronized (PrimePerfMonitor.class) {
            invalidators.merge(key.toString(), 1, Integer::sum);
        }
    }

    // ---- who re-lays out the whole window ----
    //
    // Measured scroll logs kept showing DrawerLayoutContainer.onLayout / LaunchActivity.onLayout
    // near the top: a requestLayout from some view deep in the tree climbed to the window root and
    // re-laid out every screen in it. The chain of requestLayout frames starts at the view that
    // asked; the first frame under it is the code that made it ask (a setText, a setVisibility...).
    private static final java.util.Map<String, Integer> rootLayoutRequesters = new java.util.HashMap<>();
    private static int rootLayoutRequests;
    private static long lastRootLayoutSampleMs;

    public static void noteRootLayoutRequest() {
        final long now = android.os.SystemClock.uptimeMillis();
        synchronized (PrimePerfMonitor.class) {
            rootLayoutRequests++;
            if (now - lastRootLayoutSampleMs < 50) {
                return;
            }
            lastRootLayoutSampleMs = now;
        }
        final StackTraceElement[] trace = new Throwable().getStackTrace();
        int i = 0;
        String origin = null;
        while (i < trace.length && (trace[i].getClassName().equals("org.telegram.messenger.PrimePerfMonitor")
                || trace[i].getMethodName().equals("requestLayout"))) {
            if (trace[i].getMethodName().equals("requestLayout")) {
                origin = trace[i].getClassName();
            }
            i++;
        }
        final StringBuilder key = new StringBuilder();
        if (origin != null) {
            key.append(shortName(origin)).append(" ← ");
        }
        // The first app frame is the one to fix; the platform frames above it only say how.
        int shown = 0;
        boolean platformShown = false;
        for (; i < trace.length && shown < 3; i++) {
            final String cls = trace[i].getClassName();
            if (shown == 0 && !cls.startsWith("org.telegram.") && i + 1 < trace.length) {
                // Only the platform call closest to the request (setText, setVisibility...).
                if (!platformShown) {
                    platformShown = true;
                    key.append(shortName(cls)).append('.').append(trace[i].getMethodName()).append(" < ");
                }
                continue;
            }
            if (shown > 0) {
                key.append(" < ");
            }
            key.append(shortName(cls)).append('.').append(trace[i].getMethodName());
            shown++;
        }
        synchronized (PrimePerfMonitor.class) {
            rootLayoutRequesters.merge(key.toString(), 1, Integer::sum);
        }
    }

    private static String shortName(String name) {
        return name.startsWith("org.telegram.") ? name.substring(13) : name.substring(name.lastIndexOf('.') + 1);
    }

    private static void appendRootLayoutLines(long elapsedMs) {
        if (rootLayoutRequests == 0) {
            return;
        }
        final double seconds = elapsedMs / 1000.0;
        final StringBuilder sb = new StringBuilder(String.format(Locale.US,
                "    перекладка всего окна: %.1f/сек", rootLayoutRequests / seconds));
        if (!rootLayoutRequesters.isEmpty()) {
            final java.util.List<java.util.Map.Entry<String, Integer>> sorted = new java.util.ArrayList<>(rootLayoutRequesters.entrySet());
            sorted.sort((a, b) -> b.getValue() - a.getValue());
            sb.append(", кто:");
            for (int k = 0; k < Math.min(3, sorted.size()); k++) {
                sb.append(k == 0 ? " " : "; ").append(sorted.get(k).getKey()).append(" x").append(sorted.get(k).getValue());
            }
        }
        appendLine(sb.toString());
    }

    private static void appendInvalidateLines(long elapsedMs) {
        if (cellInvalidates == 0 && cellDraws == 0) {
            return;
        }
        final double seconds = elapsedMs / 1000.0;
        appendLine(String.format(Locale.US, "    сообщения: перерисовано %.0f/сек, invalidate %.0f/сек",
                cellDraws / seconds, cellInvalidates / seconds));
        if (invalidators.isEmpty()) {
            return;
        }
        int sampled = 0;
        for (int v : invalidators.values()) {
            sampled += v;
        }
        final java.util.List<java.util.Map.Entry<String, Integer>> sorted = new java.util.ArrayList<>(invalidators.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());
        for (int k = 0; k < Math.min(4, sorted.size()); k++) {
            final int pct = Math.round(100f * sorted.get(k).getValue() / sampled);
            if (pct < 5) {
                break;
            }
            appendLine(String.format(Locale.US, "    кто перерисовывает %d%%: %s", pct, sorted.get(k).getKey()));
        }
    }

    // ---- notification storms and long UI-thread tasks ----
    //
    // The UI should only ever draw; everything it waits on or computes for someone else is a leak
    // across the UI / processing boundary. These two tallies name the leaks: which notifications
    // cost the main thread the most (and in which observer), and which posted tasks ran longer
    // than a frame's budget.
    private static final java.util.Map<String, long[]> notificationCost = new java.util.HashMap<>(); // {count, ns}
    private static final java.util.Map<Integer, Integer> notificationCount = new java.util.HashMap<>();
    private static android.util.SparseArray<String> notificationNames;
    private static final java.util.Map<String, long[]> longTasks = new java.util.HashMap<>(); // {count, ns, maxNs}
    private static final long LONG_TASK_NS = 8_000_000L;
    private static long taskStartNs;
    private static String taskLabel;

    private static synchronized String notificationName(int id) {
        if (notificationNames == null) {
            notificationNames = new android.util.SparseArray<>();
            for (java.lang.reflect.Field f : NotificationCenter.class.getFields()) {
                try {
                    if (f.getType() == int.class && java.lang.reflect.Modifier.isStatic(f.getModifiers())
                            && notificationNames.get(f.getInt(null)) == null && !f.getName().equals("totalEvents")) {
                        notificationNames.put(f.getInt(null), f.getName());
                    }
                } catch (Throwable ignore) {
                }
            }
        }
        final String name = notificationNames.get(id);
        return name != null ? name : ("#" + id);
    }

    private static String shortClassName(Object o) {
        if (o == null) {
            return "?";
        }
        final String name = o.getClass().getName();
        final String trimmed = name.startsWith("org.telegram.") ? name.substring(13) : name.substring(name.lastIndexOf('.') + 1);
        final int synthetic = trimmed.indexOf("$$");
        return synthetic > 0 ? trimmed.substring(0, synthetic) : trimmed;
    }

    private static final java.util.Map<String, long[]> updateSections = new java.util.HashMap<>(); // {count, ns, max}

    /** Time spent applying one kind of incoming update on the UI thread. */
    public static void noteUpdateSection(String key, long ns) {
        synchronized (PrimePerfMonitor.class) {
            final long[] c = updateSections.get(key);
            if (c == null) {
                updateSections.put(key, new long[]{1, ns, ns});
            } else {
                c[0]++;
                c[1] += ns;
                c[2] = Math.max(c[2], ns);
            }
        }
    }

    /** (observer class, notification id) -> report key. Built once: the key used to be
     *  concatenated (and the class name trimmed with three indexOf calls) on every single
     *  delivery, and startAllHeavyOperations alone is delivered to 150 observers per post. */
    private static final java.util.HashMap<Class<?>, android.util.SparseArray<String>> notificationKeys = new java.util.HashMap<>();

    public static void noteNotification(int id, Object observer, long ns) {
        if (ns < 500_000L) {
            return;
        }
        synchronized (PrimePerfMonitor.class) {
            final Class<?> cls = observer == null ? Void.class : observer.getClass();
            android.util.SparseArray<String> byId = notificationKeys.get(cls);
            if (byId == null) {
                byId = new android.util.SparseArray<>();
                notificationKeys.put(cls, byId);
            }
            String key = byId.get(id);
            if (key == null) {
                key = notificationName(id) + " -> " + shortClassName(observer);
                byId.put(id, key);
            }
            final long[] c = notificationCost.get(key);
            if (c == null) {
                notificationCost.put(key, new long[]{1, ns});
            } else {
                c[0]++;
                c[1] += ns;
            }
            notificationCount.merge(id, 1, Integer::sum);
        }
    }

    /** Looper's own message log, parsed only for its start/end markers. Installed on the main
     *  looper while monitoring and removed after; costs one string per message meanwhile. */
    private static final android.util.Printer looperPrinter = line -> {
        if (line == null || line.length() == 0) {
            return;
        }
        final char ch = line.charAt(0);
        if (ch == '>') {
            taskStartNs = System.nanoTime();
            taskLabel = line;
        } else if (ch == '<' && taskLabel != null) {
            final long ns = System.nanoTime() - taskStartNs;
            if (ns >= LONG_TASK_NS) {
                final String key = taskKey(taskLabel);
                synchronized (PrimePerfMonitor.class) {
                    final long[] c = longTasks.get(key);
                    if (c == null) {
                        longTasks.put(key, new long[]{1, ns, ns});
                    } else {
                        c[0]++;
                        c[1] += ns;
                        c[2] = Math.max(c[2], ns);
                    }
                }
            }
            taskLabel = null;
        }
    };

    /** ">>>>> Dispatching to Handler (x.y.H) {hash} x.y.Callback@1f: 0" -> "H: Callback". */
    private static String taskKey(String line) {
        if (line.contains("FrameDisplayEventReceiver")) {
            return "кадр (измерение/раскладка/отрисовка)";
        }
        String handler = "?";
        final int open = line.indexOf('(');
        final int close = open < 0 ? -1 : line.indexOf(')', open + 1);
        if (close > open) {
            handler = line.substring(open + 1, close);
            handler = handler.substring(handler.lastIndexOf('.') + 1);
        }
        String callback = "";
        final int brace = line.indexOf('}', Math.max(close, 0));
        if (brace > 0) {
            String rest = line.substring(brace + 1).trim();
            final int at = rest.indexOf('@');
            if (at > 0) {
                rest = rest.substring(0, at);
            } else {
                final int colon = rest.lastIndexOf(':');
                if (colon > 0) {
                    rest = rest.substring(0, colon);
                }
            }
            if (!rest.isEmpty() && !rest.equals("null")) {
                callback = rest.startsWith("org.telegram.") ? rest.substring(13) : rest.substring(rest.lastIndexOf('.') + 1);
                final int synthetic = callback.indexOf("$$");
                if (synthetic > 0) {
                    callback = callback.substring(0, synthetic) + " (лямбда)";
                }
            }
        }
        if (callback.contains("FrameDisplayEventReceiver")) {
            return "кадр (измерение/раскладка/отрисовка)";
        }
        return callback.isEmpty() ? handler : handler + ": " + callback;
    }

    private static void appendBoundaryLines(long elapsedMs) {
        final double seconds = elapsedMs / 1000.0;
        if (!longTasks.isEmpty()) {
            final java.util.List<java.util.Map.Entry<String, long[]>> sorted = new java.util.ArrayList<>(longTasks.entrySet());
            sorted.sort((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1]));
            long totalNs = 0;
            long total = 0;
            for (java.util.Map.Entry<String, long[]> e : sorted) {
                totalNs += e.getValue()[1];
                total += e.getValue()[0];
            }
            appendLine(String.format(Locale.US, "    долгие задачи UI (>8мс): %d шт, %.0fмс", total, totalNs / 1e6));
            for (int i = 0; i < Math.min(4, sorted.size()); i++) {
                final long[] c = sorted.get(i).getValue();
                appendLine(String.format(Locale.US, "      %s x%d, всего %.0fмс, макс %.0fмс", sorted.get(i).getKey(), c[0], c[1] / 1e6, c[2] / 1e6));
            }
        }
        if (!updateSections.isEmpty()) {
            final java.util.List<java.util.Map.Entry<String, long[]>> sorted = new java.util.ArrayList<>(updateSections.entrySet());
            sorted.sort((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1]));
            final StringBuilder sb = new StringBuilder("    обновления на UI, дороже всего:");
            int shown = 0;
            for (java.util.Map.Entry<String, long[]> e : sorted) {
                final double ms = e.getValue()[1] / 1e6;
                if (ms < 0.5 || shown >= 6) {
                    break;
                }
                sb.append(shown == 0 ? " " : "; ").append(e.getKey()).append(String.format(Locale.US, " x%d %.1fмс (макс %.1f)", e.getValue()[0], ms, e.getValue()[2] / 1e6));
                shown++;
            }
            if (shown > 0) {
                appendLine(sb.toString());
            }
        }
        if (!notificationCost.isEmpty()) {
            final java.util.List<java.util.Map.Entry<String, long[]>> sorted = new java.util.ArrayList<>(notificationCost.entrySet());
            sorted.sort((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1]));
            final StringBuilder sb = new StringBuilder("    уведомления, дороже всего:");
            int shown = 0;
            for (java.util.Map.Entry<String, long[]> e : sorted) {
                final double ms = e.getValue()[1] / 1e6;
                if (ms < 1 || shown >= 5) {
                    break;
                }
                sb.append(shown == 0 ? " " : "; ").append(e.getKey()).append(String.format(Locale.US, " x%d %.1fмс", e.getValue()[0], ms));
                shown++;
            }
            if (shown > 0) {
                appendLine(sb.toString());
            }
            final java.util.List<java.util.Map.Entry<Integer, Integer>> counts = new java.util.ArrayList<>(notificationCount.entrySet());
            counts.sort((a, b) -> b.getValue() - a.getValue());
            final StringBuilder cb = new StringBuilder("    уведомления, чаще всего (/сек):");
            for (int i = 0; i < Math.min(5, counts.size()); i++) {
                cb.append(i == 0 ? " " : ", ").append(notificationName(counts.get(i).getKey()))
                        .append(String.format(Locale.US, " %.0f", counts.get(i).getValue() / seconds));
            }
            appendLine(cb.toString());
        }
    }

    private static void appendInclusiveLines() {
        if (hotSpotSamples <= 0 || inclusive.isEmpty()) {
            return;
        }
        final java.util.List<java.util.Map.Entry<String, Integer>> sorted = new java.util.ArrayList<>(inclusive.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());
        final StringBuilder sb = new StringBuilder("    время внутри:");
        int reported = 0;
        for (java.util.Map.Entry<String, Integer> entry : sorted) {
            final int pct = Math.round(100f * entry.getValue() / hotSpotSamples);
            if (pct < INCLUSIVE_MIN_PERCENT || reported >= INCLUSIVE_MAX_REPORTED) {
                break;
            }
            sb.append(reported == 0 ? " " : ", ").append(entry.getKey()).append(' ').append(pct).append('%');
            reported++;
        }
        if (reported > 0) {
            appendLine(sb.toString());
        }
    }

    private static void recordHotSpotSample() {
        try {
            final Thread main = Looper.getMainLooper().getThread();
            final StackTraceElement[] trace = main.getStackTrace();
            if (trace.length == 0) {
                return;
            }
            final int kind = classify(main, trace);
            synchronized (PrimePerfMonitor.class) {
                stateSamples[kind]++;
            }
            if (kind == STATE_IDLE) {
                return; // an idle looper is not a hot spot
            }
            recordInclusive(trace);
            final int depth = Math.min(trace.length, HOTSPOT_KEY_DEPTH);
            final StringBuilder key = new StringBuilder(kind == STATE_WAITING ? "[ждёт] " : kind == STATE_RENDER ? "[ждёт отрисовку] " : "[считает] ");
            for (int i = 0; i < depth; i++) {
                if (i > 0) {
                    key.append(" < ");
                }
                key.append(trace[i].getClassName()).append('.').append(trace[i].getMethodName());
            }
            final String k = key.toString();
            synchronized (PrimePerfMonitor.class) {
                hotSpots.merge(k, 1, Integer::sum);
                hotSpotSamples++;
            }
        } catch (Throwable ignored) {
        }
    }

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
        samplerRunning = true;
        trackingInvalidates = true;
        Looper.getMainLooper().setMessageLogging(looperPrinter);
        samplerThread = new Thread(() -> {
            while (samplerRunning) {
                try {
                    Thread.sleep(HOTSPOT_SAMPLE_INTERVAL_MS);
                } catch (InterruptedException e) {
                    break;
                }
                if (samplerRunning) {
                    recordHotSpotSample();
                }
            }
        }, "PrimePerfMonitor-Sampler");
        samplerThread.setDaemon(true);
        samplerThread.start();

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
        samplerRunning = false;
        trackingInvalidates = false;
        Looper.getMainLooper().setMessageLogging(null);
        if (samplerThread != null) {
            samplerThread.interrupt();
            samplerThread = null;
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
        hotSpots.clear();
        hotSpotSamples = 0;
        stateSamples[0] = stateSamples[1] = stateSamples[2] = stateSamples[3] = 0;
        inclusive.clear();
        cellInvalidates = 0;
        cellDraws = 0;
        invalidators.clear();
        rootLayoutRequesters.clear();
        rootLayoutRequests = 0;
        notificationCost.clear();
        updateSections.clear();
        notificationCount.clear();
        longTasks.clear();
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
        final int totalStates = stateSamples[0] + stateSamples[1] + stateSamples[2] + stateSamples[3];
        if (totalStates > 0) {
            appendLine(String.format(Locale.US, "    главный поток: считает %d%%  ждёт отрисовку %d%%  ждёт прочее %d%%  простаивает %d%%",
                    Math.round(100f * stateSamples[STATE_WORKING] / totalStates),
                    Math.round(100f * stateSamples[STATE_RENDER] / totalStates),
                    Math.round(100f * stateSamples[STATE_WAITING] / totalStates),
                    Math.round(100f * stateSamples[STATE_IDLE] / totalStates)));
        }
        appendHotSpotLines();
        appendInclusiveLines();
        appendInvalidateLines(elapsedMs);
        appendRootLayoutLines(elapsedMs);
        appendBoundaryLines(elapsedMs);

        resetWindow();
    }

    /** The statistically meaningful counterpart to the single worst-frame line above - see the
     *  class doc on {@link #hotSpots}. Reports the call chains the sampler caught the main thread
     *  in most often this window, each as a share of all samples taken. */
    private static void appendHotSpotLines() {
        if (hotSpotSamples <= 0 || hotSpots.isEmpty()) {
            return;
        }
        final java.util.List<java.util.Map.Entry<String, Integer>> sorted = new java.util.ArrayList<>(hotSpots.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());
        final int reported = Math.min(HOTSPOT_MAX_REPORTED, sorted.size());
        for (int i = 0; i < reported; i++) {
            final java.util.Map.Entry<String, Integer> entry = sorted.get(i);
            final int pct = Math.round(100f * entry.getValue() / hotSpotSamples);
            if (pct < HOTSPOT_MIN_PERCENT) {
                break; // sorted descending - nothing after this clears the bar either
            }
            appendLine(String.format(Locale.US, "    горячая точка %d%% (%d/%d сэмплов): %s",
                    pct, entry.getValue(), hotSpotSamples, entry.getKey()));
        }
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
