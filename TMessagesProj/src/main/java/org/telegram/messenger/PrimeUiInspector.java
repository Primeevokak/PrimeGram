package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PrimeGram: persisted on/off switch for the "Диагностика" UI-inspector overlay (see
 * PrimeUiInspectorOverlay) - outlines every view on screen in green with its class/id name, to
 * make it possible to point at a stray/misplaced background or ghost element and read off exactly
 * which View is painting it, instead of guessing from a screenshot. Off by default; only ever
 * meant to be flipped on from Settings while actively diagnosing a UI bug.
 *
 * <p>Some backgrounds are not a View at all - e.g. {@code ChatActivityChannelButtonsLayout} paints
 * its pill background as a raw {@code Drawable} directly inside {@code drawChild()}, entirely
 * outside the View tree. Walking the View tree can never find one of those, no matter how
 * thorough the search - there is nothing to find. {@link #recordManualDraw} is the escape hatch:
 * any custom {@code drawChild()}/{@code dispatchDraw()} override that paints something manually
 * can call it (only when {@link #isEnabled()} costs anything), and the overlay draws it alongside
 * real Views in a distinct color so "this one isn't a View" is visible at a glance.
 */
public final class PrimeUiInspector {

    private static final String PREFS = "primegram_ui_inspector";
    /** A mark not refreshed within this long is assumed stale (its owner stopped drawing - screen
     *  closed, scrolled off) and is dropped rather than shown forever. */
    private static final long MARK_TTL_MS = 500;

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
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.primeUiInspectorChanged);
        if (!enabled) {
            manualMarks.clear();
        }
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---- manual (non-View) draw marks ----

    public static final class ManualMark {
        public final RectF screenRect;
        public final String label;

        private ManualMark(RectF screenRect, String label) {
            this.screenRect = screenRect;
            this.label = label;
        }
    }

    private static final class TimedMark {
        RectF screenRect;
        String label;
        long timeMs;
    }

    private static final Map<String, TimedMark> manualMarks = new ConcurrentHashMap<>();
    private static final int[] tmpLoc = new int[2];

    /**
     * Call this from inside a custom drawChild()/dispatchDraw() at the point something is painted
     * straight onto the Canvas with no backing View. {@code left/top/right/bottom} are in
     * {@code owner}'s own local coordinate space - the same numbers already being passed to
     * whatever Drawable.setBounds()/Canvas.drawRect() call is doing the real painting.
     */
    public static void recordManualDraw(View owner, String label, float left, float top, float right, float bottom) {
        if (!isEnabled()) {
            return;
        }
        owner.getLocationOnScreen(tmpLoc);
        final TimedMark mark = new TimedMark();
        mark.screenRect = new RectF(tmpLoc[0] + left, tmpLoc[1] + top, tmpLoc[0] + right, tmpLoc[1] + bottom);
        mark.label = "[canvas] " + label;
        mark.timeMs = SystemClock.elapsedRealtime();
        manualMarks.put(owner.getClass().getName() + "#" + label, mark);
    }

    /** Snapshot of marks refreshed recently enough to still be on screen. */
    public static List<ManualMark> currentManualMarks() {
        final List<ManualMark> result = new ArrayList<>();
        if (manualMarks.isEmpty()) {
            return result;
        }
        final long now = SystemClock.elapsedRealtime();
        for (final Map.Entry<String, TimedMark> entry : manualMarks.entrySet()) {
            final TimedMark mark = entry.getValue();
            if (now - mark.timeMs <= MARK_TTL_MS) {
                result.add(new ManualMark(mark.screenRect, mark.label));
            }
        }
        return result;
    }

    private PrimeUiInspector() {
    }
}
