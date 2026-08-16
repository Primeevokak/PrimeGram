package org.telegram.messenger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * PrimeGram: tails this process's own logcat output into an in-memory ring buffer, so a live log
 * view can be shown ON DEVICE without a computer/ADB attached - the actual point of this class.
 * A plain {@code logcat} invocation with no {@code READ_LOGS} permission is restricted by Android
 * (since 4.1, for any non-system app) to only the calling app's own process anyway, so no manifest
 * permission is needed here - this reads exactly what a developer plugging in a USB cable and
 * running {@code adb logcat} would see for this app, nothing more.
 *
 * <p>Started/stopped by {@link PrimeLogOverlayState} alongside the overlay's own on/off switch -
 * this costs a background thread and a small buffer while running, so it is not started
 * unconditionally at process boot.
 */
public final class PrimeLogCollector {

    private static final int MAX_LINES = 2000;

    private static Process process;
    private static Thread readerThread;
    private static final ArrayDeque<String> ring = new ArrayDeque<>();
    private static final Object LOCK = new Object();

    private PrimeLogCollector() {
    }

    public static void start() {
        synchronized (LOCK) {
            if (process != null) {
                return;
            }
            try {
                // PrimeGram: a handful of vendor/system tags on some devices (gralloc's per-frame
                // dataspace notice being the worst offender - thousands of lines/second) drown out
                // everything else, including this app's own tagged lines, in the tiny few seconds
                // it takes to fill this collector's whole ring buffer. Silencing exactly those
                // known-noisy tags (":S") while leaving every other tag at its normal verbosity
                // ("*:V") keeps the buffer meaningful without needing an allow-list of every tag
                // worth seeing.
                process = Runtime.getRuntime().exec(new String[]{
                        "logcat", "-v", "time",
                        "gralloc4:S", "skia:S", "MIUIInput:S", "ScrollerOptimizationManager:S",
                        "View:S", "libjpeg-alpha:S", "FileUtils:S",
                        "*:V"
                });
                readerThread = new Thread(PrimeLogCollector::readLoop, "PrimeLogCollector");
                readerThread.setDaemon(true);
                readerThread.start();
            } catch (Throwable t) {
                process = null;
            }
        }
    }

    public static void stop() {
        synchronized (LOCK) {
            if (process != null) {
                try {
                    process.destroy();
                } catch (Throwable ignored) {
                }
                process = null;
            }
            readerThread = null;
        }
    }

    private static void readLoop() {
        final Process p = process;
        if (p == null) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                synchronized (LOCK) {
                    if (process != p) {
                        // stop() was called while we were blocked in readLine() - drop this line,
                        // a stale reader must not keep writing into a buffer that's supposed to be
                        // whatever the (possibly not-yet-started-again) new collector owns.
                        return;
                    }
                    ring.addLast(line);
                    while (ring.size() > MAX_LINES) {
                        ring.removeFirst();
                    }
                }
            }
        } catch (Throwable ignored) {
            // Process died/was destroyed - nothing to recover, start() creates a fresh one.
        }
    }

    public static boolean isRunning() {
        synchronized (LOCK) {
            return process != null;
        }
    }

    public static int lineCount() {
        synchronized (LOCK) {
            return ring.size();
        }
    }

    /** Snapshot of the last {@code maxLines} lines (or all of them, if fewer), oldest first. */
    public static List<String> tail(int maxLines) {
        synchronized (LOCK) {
            final int size = ring.size();
            final int skip = Math.max(0, size - maxLines);
            final List<String> result = new ArrayList<>(Math.min(size, maxLines));
            int i = 0;
            for (String line : ring) {
                if (i++ >= skip) {
                    result.add(line);
                }
            }
            return result;
        }
    }

    public static String dumpAll() {
        synchronized (LOCK) {
            return String.join("\n", ring);
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            ring.clear();
        }
    }
}
