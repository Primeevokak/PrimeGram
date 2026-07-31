package org.telegram.messenger;

import android.content.SharedPreferences;

/**
 * PrimeGram: where on the screen a swipe opens the sidebar.
 *
 * <p>The panel used to answer to any swipe starting in the left 40% of the screen, over the full
 * height. That is a lot of screen to give to one gesture: it fights the swipe that moves between
 * chat-list tabs, and on a tall phone the useful part of it is nowhere near the top anyway. So the
 * zone is a rectangle the user places themselves - a width measured from the left edge, and a top
 * and bottom as fractions of the screen height.
 *
 * <p>Fractions rather than pixels, because the same account is opened on a phone and on a tablet,
 * and because the screen changes height when it rotates. The defaults reproduce the old behaviour
 * exactly, so nobody who never opens this screen notices that it exists.
 */
public class PrimeSidebarZone {

    private static final String KEY_WIDTH = "primegram_sidebar_zone_width";
    private static final String KEY_TOP = "primegram_sidebar_zone_top";
    private static final String KEY_BOTTOM = "primegram_sidebar_zone_bottom";

    public static final float DEFAULT_WIDTH = 0.40f;
    public static final float DEFAULT_TOP = 0f;
    public static final float DEFAULT_BOTTOM = 1f;

    /**
     * A sliver at one end, most of the screen at the other. The narrow end is for someone who wants
     * the panel out of the way of everything else; the wide end is for someone who wants it to
     * answer wherever their thumb lands. Neither is the whole screen: a zone with no outside is a
     * zone that swallows every other horizontal gesture on the chat list.
     */
    public static final float MIN_WIDTH = 0.05f;
    public static final float MAX_WIDTH = 0.80f;
    /** A band shorter than this is harder to hit than it is worth. */
    public static final float MIN_HEIGHT = 0.10f;

    private static SharedPreferences prefs() {
        return MessagesController.getGlobalMainSettings();
    }

    public static float width() {
        return clamp(prefs().getFloat(KEY_WIDTH, DEFAULT_WIDTH), MIN_WIDTH, MAX_WIDTH);
    }

    public static float top() {
        final float top = clamp(prefs().getFloat(KEY_TOP, DEFAULT_TOP), 0f, 1f - MIN_HEIGHT);
        return top;
    }

    public static float bottom() {
        return clamp(prefs().getFloat(KEY_BOTTOM, DEFAULT_BOTTOM), top() + MIN_HEIGHT, 1f);
    }

    public static void set(float width, float top, float bottom) {
        prefs().edit()
                .putFloat(KEY_WIDTH, clamp(width, MIN_WIDTH, MAX_WIDTH))
                .putFloat(KEY_TOP, clamp(top, 0f, 1f - MIN_HEIGHT))
                .putFloat(KEY_BOTTOM, clamp(bottom, MIN_HEIGHT, 1f))
                .apply();
    }

    public static void reset() {
        prefs().edit().remove(KEY_WIDTH).remove(KEY_TOP).remove(KEY_BOTTOM).apply();
    }

    public static boolean isDefault() {
        return width() == DEFAULT_WIDTH && top() == DEFAULT_TOP && bottom() == DEFAULT_BOTTOM;
    }

    /**
     * Whether a touch that started at this point should be allowed to drag the panel open.
     *
     * <p>Takes the size of the view the coordinates came from rather than the display size: with a
     * split screen or a floating window those are not the same, and the zone is drawn against what
     * the user actually sees.
     */
    public static boolean contains(float x, float y, int viewWidth, int viewHeight) {
        if (viewWidth <= 0 || viewHeight <= 0) {
            return false;
        }
        return x < viewWidth * width()
                && y >= viewHeight * top()
                && y <= viewHeight * bottom();
    }

    /** A short "40% ширины · вся высота" for the settings row. */
    public static String describe() {
        final int w = Math.round(width() * 100);
        final float top = top(), bottom = bottom();
        final String vertical;
        if (top <= 0.01f && bottom >= 0.99f) {
            vertical = "вся высота";
        } else {
            vertical = Math.round(top * 100) + "–" + Math.round(bottom * 100) + "%";
        }
        return w + "% · " + vertical;
    }

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value)) {
            return min;
        }
        return value < min ? min : Math.min(value, max);
    }
}
