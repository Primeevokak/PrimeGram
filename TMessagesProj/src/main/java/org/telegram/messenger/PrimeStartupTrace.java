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
                if (finished || marks.size() > 64) {
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
