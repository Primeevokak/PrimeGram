package org.telegram.messenger;

import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Locale;

/**
 * PrimeGram: milestone timings for a cold start.
 *
 * <p>Exists because the cold-start delay users reported could not be diagnosed by reading
 * code — the candidates (native library loads, database open, first network round trip) all
 * looked plausible and none could be ruled out without numbers. Marks are cheap enough to
 * leave in permanently: one timestamp and one string per milestone.
 */
public class PrimeStartupTrace {

    private static final long START = SystemClock.elapsedRealtime();
    private static final ArrayList<String> marks = new ArrayList<>();
    private static volatile boolean finished;

    /** Records a milestone. Safe from any thread. */
    public static void mark(String name) {
        try {
            long elapsed = SystemClock.elapsedRealtime() - START;
            synchronized (marks) {
                if (finished || marks.size() > 200) {
                    return;
                }
                marks.add(String.format(Locale.US, "%5d ms  %s", elapsed, name));
            }
            FileLog.d("[startup] " + elapsed + " ms — " + name);
        } catch (Throwable ignore) {}
    }

    /** Marks the moment the user can actually see their chats, and dumps the whole trace. */
    public static void finish(String name) {
        synchronized (marks) {
            if (finished) {
                return;
            }
            mark(name);
            finished = true;
        }
        FileLog.d("[startup] ---- cold start trace ----");
        synchronized (marks) {
            for (String mark : marks) {
                FileLog.d("[startup] " + mark);
            }
        }
    }

    /**
     * Watches the main thread and records who is holding it.
     *
     * <p>Added because the trace kept showing multi-second gaps with no mark in them — the app was
     * doing something on the UI thread that no milestone covered, and adding marks one at a time
     * was guesswork. This works the other way round: a background thread pings the main looper
     * every 200 ms, and whenever a ping comes back late it grabs the main thread's stack. The mark
     * therefore names the blocking call itself, wherever it happens to be.
     */
    public static void startMainThreadWatchdog() {
        final Thread mainThread = android.os.Looper.getMainLooper().getThread();
        final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        Thread watchdog = new Thread(() -> {
            final boolean[] answered = {true};
            while (!finished) {
                try {
                    Thread.sleep(200);
                    synchronized (answered) {
                        if (!answered[0]) {
                            continue; // still stuck from the previous round; wait for it to finish
                        }
                        answered[0] = false;
                    }
                    final long sentAt = SystemClock.elapsedRealtime();
                    handler.post(() -> {
                        synchronized (answered) {
                            long waited = SystemClock.elapsedRealtime() - sentAt;
                            answered[0] = true;
                            if (waited >= 700) {
                                mark("!! main thread blocked " + waited + " ms");
                            }
                        }
                    });
                    Thread.sleep(700);
                    synchronized (answered) {
                        if (!answered[0]) {
                            // Still not run: sample the stack while it is actually stuck, which is
                            // the only moment the culprit is visible.
                            mark("!! main thread stuck in " + topAppFrames(mainThread.getStackTrace()));
                        }
                    }
                } catch (Throwable ignore) {
                    return;
                }
            }
        }, "prime-startup-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    /** The first few frames that belong to this app, so the mark points at our code, not the VM. */
    private static String topAppFrames(StackTraceElement[] stack) {
        StringBuilder sb = new StringBuilder();
        int printed = 0;
        for (StackTraceElement e : stack) {
            String cls = e.getClassName();
            if (!cls.startsWith("org.telegram") && !cls.startsWith("android.database") && !cls.startsWith("android.app.SharedPreferences")) {
                continue;
            }
            if (printed > 0) {
                sb.append(" < ");
            }
            sb.append(cls.substring(cls.lastIndexOf('.') + 1)).append('.').append(e.getMethodName()).append(':').append(e.getLineNumber());
            if (++printed >= 5) {
                break;
            }
        }
        return sb.length() == 0 ? (stack.length > 0 ? stack[0].toString() : "?") : sb.toString();
    }

    /** The collected trace, for showing in the debug UI. */
    public static String dump() {
        StringBuilder builder = new StringBuilder();
        synchronized (marks) {
            for (String mark : marks) {
                builder.append(mark).append('\n');
            }
        }
        return builder.length() == 0 ? "Трасса пуста." : builder.toString();
    }
}
