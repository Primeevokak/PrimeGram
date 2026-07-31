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
    /**
     * How long the watchdog may run before it stops on its own.
     *
     * <p>It is meant to watch a startup, and it stops when {@link #finish} is called - which
     * happens when the chat list appears. But an app opened onto a deep link, a share sheet or the
     * login screen never reaches that call, and the watchdog then pings the main looper every
     * 200 ms for the life of the process. Posting to the main thread twice a second forever is not
     * what an instrument for measuring startup should do to a shipped build.
     */
    private static final long WATCHDOG_MAX_MS = 60_000;

    public static void startMainThreadWatchdog() {
        final Thread mainThread = android.os.Looper.getMainLooper().getThread();
        final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        final long watchdogUntil = SystemClock.elapsedRealtime() + WATCHDOG_MAX_MS;
        Thread watchdog = new Thread(() -> {
            final boolean[] answered = {true};
            while (!finished && SystemClock.elapsedRealtime() < watchdogUntil) {
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
                    // Still not run: sample the stack while it is actually stuck, which is the
                    // only moment the culprit is visible. Keep sampling for as long as it stays
                    // stuck - one sample at the start of a thirty-second freeze says where it
                    // began and nothing about where it spent the time.
                    String lastStack = null;
                    int repeats = 0;
                    while (!finished) {
                        synchronized (answered) {
                            if (answered[0]) {
                                break;
                            }
                        }
                        String stack = topAppFrames(mainThread.getStackTrace());
                        if (stack.equals(lastStack)) {
                            repeats++;
                        } else {
                            if (lastStack != null && repeats > 0) {
                                mark("!! ... still there " + repeats + " samples later");
                            }
                            mark("!! main thread stuck in " + stack);
                            lastStack = stack;
                            repeats = 0;
                        }
                        Thread.sleep(2000);
                    }
                    if (lastStack != null && repeats > 0) {
                        mark("!! ... stayed there for " + repeats + " more samples (~" + (repeats * 2) + " s)");
                    }
                } catch (Throwable ignore) {
                    return;
                }
            }
        }, "prime-startup-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    /**
     * Where the main thread actually is, in the shortest form that still answers the question.
     * <p>
     * The innermost frame goes first whatever it belongs to - that is what tells a lock apart from
     * a socket read from a database query, and it is the one frame we can never afford to filter
     * out. After it come the frames that belong to this app, which say who asked for it.
     */
    private static String topAppFrames(StackTraceElement[] stack) {
        if (stack == null || stack.length == 0) {
            return "?";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(shortFrame(stack[0]));
        int printed = 0;
        for (int i = 1; i < stack.length; i++) {
            String cls = stack[i].getClassName();
            if (!cls.startsWith("org.telegram") && !cls.startsWith("android.database")
                    && !cls.startsWith("android.app.SharedPreferences") && !cls.startsWith("android.content.res")) {
                continue;
            }
            sb.append(" < ").append(shortFrame(stack[i]));
            if (++printed >= 5) {
                break;
            }
        }
        if (printed == 0) {
            // nothing of ours on the stack at all - print raw frames so the mark is not a dead end
            for (int i = 1; i < stack.length && i <= 4; i++) {
                sb.append(" < ").append(shortFrame(stack[i]));
            }
        }
        return sb.toString();
    }

    private static String shortFrame(StackTraceElement e) {
        String cls = e.getClassName();
        return cls.substring(cls.lastIndexOf('.') + 1) + "." + e.getMethodName() + ":" + e.getLineNumber();
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
