package org.telegram.messenger.music;

import androidx.core.graphics.ColorUtils;

/**
 * PrimeGram Music: every color/type-scale/geometry constant used across the Music tab,
 * transcribed directly from namida's real source (not eyeballed from screenshots) - see
 * {@code lib/core/themes.dart} and {@code lib/core/dimensions.dart} in the cloned
 * reference checkout ({@code D:\Telegram-P\namida-repo}, gitignored, read-only).
 *
 * <p>Two earlier visual passes on this tab both invented their own palette (a
 * near-black background, a neon-green accent, a 10-gradient rainbow card fallback) -
 * none of that exists in namida. namida's real default accent is a warm brown
 * ({@code kMainColorDark = 0xFF43362B}, {@code constants.dart:142}), its
 * background/card colors are a dark neutral grey blended with a small percentage of
 * whatever the *currently playing track's own album art* extracts as its dominant
 * color ({@code themes.dart:19-47}) - the whole UI recolors itself per track. This
 * class is the shared home for that behavior in this fork.
 */
public final class PrimeMusicTheme {

    private PrimeMusicTheme() {
    }

    // ---- Colors (namida: themes.dart, constants.dart) ----

    /** namida's {@code kMainColorDark} - the seed accent before any track has a real
     *  extracted color yet (no track playing, or the current track has no embedded
     *  art). Used as-is, not run through Material3's HCT tonal generation namida itself
     *  wraps it in - {@link #accentForUi(int)} is this fork's equivalent nudge. */
    public static final int DEFAULT_ACCENT_SEED = 0xFF43362B;

    /** namida's {@code pitchGrey} - the neutral base both background and card colors
     *  blend the current accent into. */
    public static final int BASE_NEUTRAL = 0xFF232323;

    /** Never pure white - namida's real {@code displayLarge}/{@code displayMedium}/
     *  {@code displaySmall} text colors are white at 82%/71%/67% alpha
     *  ({@code themes.dart:246,252,258}), for a softer look than harsh pure white. */
    public static final int TEXT_PRIMARY = 0xD2FFFFFF;
    public static final int TEXT_SECONDARY = 0xB5FFFFFF;
    public static final int TEXT_TERTIARY = 0xAAFFFFFF;

    // ---- Type scale (namida: themes.dart:243-260) ----

    public static final float SP_LARGE = 17f;   // displayLarge, weight 700
    public static final float SP_MEDIUM = 15f;  // displayMedium, weight 600
    public static final float SP_SMALL = 13f;   // displaySmall, weight 400

    // ---- Geometry (namida: dimensions.dart, themes.dart) ----

    public static final int CARD_RADIUS_DP = 12;
    public static final float CARD_ELEVATION_DP = 12f;
    public static final int DIVIDER_DP = 4;
    public static final int TILE_MARGIN_DP = 4;
    /** namida's own default is 70dp - scaled down for this fork's tighter list
     *  density, the one deliberately-not-1:1 number in this class. */
    public static final int TILE_THUMBNAIL_DP = 64;
    public static final int TILE_CONTENT_HEIGHT_DP = 64;

    // ---- Live per-track accent state ----

    /** The current accent color - {@link #DEFAULT_ACCENT_SEED} run through
     *  {@link #accentForUi} until a real track color is extracted, then whatever
     *  {@code PrimeTrackColor} last resolved. Read synchronously by every bind() call
     *  that wants a live accent color; writers post through
     *  {@code PrimeTrackColor.getAccentAsync}'s callback. */
    public static volatile int currentAccent = accentForUi(DEFAULT_ACCENT_SEED);

    /** The cover path {@link #currentAccent} was extracted from (or null for the
     *  default seed) - lets a caller tell whether a freshly-resolved accent actually
     *  belongs to the track that's playing right now, or is a stale result for a track
     *  that's since changed again. */
    public static volatile String currentAccentKey;

    // ---- Accent-change listeners ----
    //
    // Resolving a new track's accent is async (decode + Palette off a background
    // queue), so a screen's own synchronous messagePlayingDidStart handler always runs
    // before the real color is ready - it would repaint with the previous track's
    // stale accent otherwise. A screen registers here (attach/create) and unregisters
    // (detach/destroy) to get a second, correctly-timed repaint once the real color
    // lands - a small local pub/sub rather than a new NotificationCenter event, since
    // this is purely an in-process "the accent int changed" signal.

    private static final java.util.List<Runnable> accentListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void addAccentListener(Runnable listener) {
        accentListeners.add(listener);
    }

    public static void removeAccentListener(Runnable listener) {
        accentListeners.remove(listener);
    }

    static void notifyAccentChanged() {
        for (Runnable listener : accentListeners) {
            listener.run();
        }
    }

    /** namida wraps its raw seed color through Material3's {@code ColorScheme.fromSeed}
     *  before using it as UI chrome, since a raw extracted swatch (especially a muted
     *  one like the default brown) reads flat/illegible against a dark background.
     *  This fork has no Material3 HCT tonal generator wired up, so this mirrors the
     *  same *intent* - raise value, tame extreme saturation - via the plain HSV nudge
     *  {@code org.telegram.ui.Stories.recorder.DominantColors.adapt} already uses
     *  elsewhere in this fork for the identical "make an extracted color usable as UI
     *  chrome on a dark background" problem. */
    public static int accentForUi(int color) {
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(color, hsv);
        hsv[2] = clamp(hsv[2] + 0.18f, 0.95f, 0.35f);
        if (hsv[1] > 0.1f && hsv[1] <= 0.95f) {
            if (hsv[1] <= 0.5f) {
                hsv[1] = clamp(hsv[1] + 0.2f, 1f, 0f);
            } else if (hsv[1] > 0.8f) {
                hsv[1] = clamp(hsv[1] - 0.3f, 1f, 0f);
            }
        }
        return android.graphics.Color.HSVToColor(hsv);
    }

    private static float clamp(float value, float max, float min) {
        if (Float.isNaN(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    /** namida: {@code cardColor = alphaBlend(getMainColorWithAlpha(35), pitchGrey)},
     *  {@code mainColorMultiplier = 0.8} for the non-AMOLED case - effective blend
     *  ≈ 35*0.8/255 ≈ 11%. Background is the app-shell base tone; cards sit a shade
     *  lighter/warmer on top of it, same relationship namida's own bg-vs-card pairing
     *  has (the exact float matters less than "card is always a bit warmer than bg"). */
    public static int bg(int accent) {
        return ColorUtils.blendARGB(0xFF000000 | BASE_NEUTRAL, accent, 0.11f);
    }

    /** namida: {@code getMainColorWithAlpha(45)} ≈ 45*0.8/255 ≈ 14%. */
    public static int card(int accent) {
        return ColorUtils.blendARGB(0xFF000000 | BASE_NEUTRAL, accent, 0.14f);
    }
}
