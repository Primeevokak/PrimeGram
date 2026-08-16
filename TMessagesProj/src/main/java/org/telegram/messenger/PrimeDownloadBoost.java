package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

/**
 * PrimeGram: how aggressively {@link FileLoadOperation} pulls a file - chunk size and how many
 * chunk requests are in flight at once. Ported from exteraGram's "download speed boost" idea
 * (bigger chunks, more parallel requests), extended with two more static tiers and a
 * measurement-driven dynamic mode instead of a single fixed pick.
 *
 * <p>Stock/PrimeGram baseline is 128 KB / 4 requests; PrimeGram already has one conditional bump
 * to 512 KB / 8 (gated on the server's own "file experimental params" flag or a preload prefix) -
 * that bump is left untouched and still applies when this feature is OFF. What this class adds is
 * a user-controlled ceiling above and below that stock behavior.
 *
 * <p>Bigger chunks and more concurrent requests only help when the connection is actually a
 * direct one - through PrimeGram's own local proxy tunnel (or a stock SOCKS/MTProxy), pushing
 * more parallel large requests down one already-constrained tunnel just adds contention and
 * makes the proxy connection itself less stable, which is a strictly worse trade than the
 * possible download speed gain. {@link #currentParams()} forces stock parameters whenever a proxy
 * is active, regardless of what mode the user picked.
 */
public final class PrimeDownloadBoost {

    /** Ordinal doubles as the slider position (0-4) shown in Settings. */
    public enum Mode {
        OFF, FASTER, HYPER, ULTRA, DYNAMIC;

        public static Mode fromOrdinal(int i) {
            Mode[] values = values();
            return values[Math.max(0, Math.min(values.length - 1, i))];
        }
    }

    private static final String PREFS = "mainconfig";
    private static final String KEY_MODE = "primegram_download_boost_mode";

    private PrimeDownloadBoost() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static Mode getMode() {
        return Mode.fromOrdinal(prefs().getInt(KEY_MODE, 0));
    }

    public static void setMode(Mode mode) {
        prefs().edit().putInt(KEY_MODE, mode.ordinal()).apply();
        resetDynamicState();
    }

    private static final class Tier {
        final int chunkSize;
        final int maxRequests;
        Tier(int chunkSize, int maxRequests) {
            this.chunkSize = chunkSize;
            this.maxRequests = maxRequests;
        }
    }

    private static final Tier STOCK = new Tier(128 * 1024, 4);
    // FASTER/HYPER mirror what PrimeGram already conditionally allows (the 512 KB/8 experimental
    // bump); ULTRA is the deliberately aggressive ceiling the user asked for.
    private static final Tier[] STATIC_TIERS = {
            new Tier(256 * 1024, 6),
            new Tier(512 * 1024, 8),
            new Tier(5 * 1024 * 1024, 24),
    };
    // Dynamic mode's ladder: stock at the bottom, same ceiling as the static tiers at the top -
    // it never reaches for anything the user couldn't already pick by hand.
    private static final Tier[] LADDER;
    static {
        LADDER = new Tier[STATIC_TIERS.length + 1];
        LADDER[0] = STOCK;
        System.arraycopy(STATIC_TIERS, 0, LADDER, 1, STATIC_TIERS.length);
    }

    /** @return {chunkSize, maxRequests} - what {@link FileLoadOperation#updateParams()} should
     *  apply right now. */
    public static int[] currentParams() {
        if (isProxyActive()) {
            return new int[]{STOCK.chunkSize, STOCK.maxRequests};
        }
        switch (getMode()) {
            case FASTER:
                return pack(STATIC_TIERS[0]);
            case HYPER:
                return pack(STATIC_TIERS[1]);
            case ULTRA:
                return pack(STATIC_TIERS[2]);
            case DYNAMIC:
                return pack(LADDER[dynamicStep]);
            case OFF:
            default:
                return pack(STOCK);
        }
    }

    private static int[] pack(Tier t) {
        return new int[]{t.chunkSize, t.maxRequests};
    }

    private static boolean isProxyActive() {
        try {
            return SharedConfig.isProxyEnabled() || TgWsProxyService.isRunning();
        } catch (Throwable t) {
            return false;
        }
    }

    // ---- dynamic mode: probe-and-back-off, similar in spirit to TCP congestion control ----
    //
    // Every WINDOW_MS, look at how many bytes actually arrived and what fraction of chunk
    // requests failed at the CURRENT step. Too many failures -> the step is too aggressive for
    // this connection right now, back off one. Otherwise, if this step's throughput beat the
    // best one seen so far, remember it and try climbing one more step to see if that's even
    // better; if a climb doesn't pay off, retreat toward the best-known step instead of staying
    // pinned somewhere that measurably regressed. The occasional re-probe upward even while
    // already at the best-known step is deliberate - conditions genuinely improve mid-session
    // (switching from mobile data to Wi-Fi, a congested cell tower clearing up), and a purely
    // one-shot hill-climb would never notice that and adapt back up.

    private static final long WINDOW_MS = 4000;
    private static final double FAILURE_RATE_BACKOFF_THRESHOLD = 0.08;
    private static final double IMPROVEMENT_MARGIN = 1.05; // require >5% to count as "better", not noise

    private static final Object DYNAMIC_LOCK = new Object();
    private static volatile int dynamicStep = 0;
    private static double dynamicBestThroughputBps = 0;
    private static int dynamicBestStep = 0;
    private static long dynamicWindowStartMs = 0;
    private static long dynamicBytesInWindow = 0;
    private static int dynamicChunksInWindow = 0;
    private static int dynamicFailuresInWindow = 0;

    /** Called by {@link FileLoadOperation} after each chunk request resolves. Only does anything
     *  while dynamic mode is actually selected and no proxy is active - cheap to call
     *  unconditionally otherwise. */
    public static void reportChunkResult(int requestedBytes, boolean failed) {
        if (getMode() != Mode.DYNAMIC || isProxyActive()) {
            return;
        }
        synchronized (DYNAMIC_LOCK) {
            final long now = SystemClock.elapsedRealtime();
            if (dynamicWindowStartMs == 0) {
                dynamicWindowStartMs = now;
            }
            dynamicChunksInWindow++;
            if (failed) {
                dynamicFailuresInWindow++;
            } else {
                dynamicBytesInWindow += requestedBytes;
            }

            final long elapsed = now - dynamicWindowStartMs;
            if (elapsed < WINDOW_MS || dynamicChunksInWindow == 0) {
                return;
            }

            final double failureRate = (double) dynamicFailuresInWindow / dynamicChunksInWindow;
            final double throughputBps = dynamicBytesInWindow / (elapsed / 1000.0);

            if (failureRate > FAILURE_RATE_BACKOFF_THRESHOLD) {
                dynamicStep = Math.max(0, dynamicStep - 1);
            } else if (throughputBps > dynamicBestThroughputBps * IMPROVEMENT_MARGIN) {
                dynamicBestThroughputBps = throughputBps;
                dynamicBestStep = dynamicStep;
                dynamicStep = Math.min(LADDER.length - 1, dynamicStep + 1);
            } else if (dynamicStep > dynamicBestStep) {
                // Climbed past the best-known step and it didn't pay off - retreat.
                dynamicStep--;
            } else if (dynamicStep < LADDER.length - 1) {
                // At or below the best-known step - nudge up once in a while to re-probe in case
                // conditions improved.
                dynamicStep++;
            }

            dynamicWindowStartMs = now;
            dynamicBytesInWindow = 0;
            dynamicChunksInWindow = 0;
            dynamicFailuresInWindow = 0;
        }
    }

    private static void resetDynamicState() {
        synchronized (DYNAMIC_LOCK) {
            dynamicStep = 0;
            dynamicBestThroughputBps = 0;
            dynamicBestStep = 0;
            dynamicWindowStartMs = 0;
            dynamicBytesInWindow = 0;
            dynamicChunksInWindow = 0;
            dynamicFailuresInWindow = 0;
        }
    }
}
