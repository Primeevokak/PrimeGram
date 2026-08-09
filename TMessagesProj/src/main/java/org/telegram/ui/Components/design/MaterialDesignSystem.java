package org.telegram.ui.Components.design;

import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;

/**
 * PrimeGram: Stage 2's second design language, and the proof-of-concept that {@link DesignSystem}
 * actually changes rendering rather than just existing as an unused interface.
 *
 * <p>Chosen to go second (of Material/Fluent/Neumorphism/Glassmorphism) specifically because it
 * carries no accessibility red flag the way Glass (variable-contrast blur) and Neumorphism
 * (low-contrast-by-design) do - see the roadmap's Stage 2 notes - while still being visually
 * distinct enough from Flat to prove the token layer is real: bigger, M3-scale corner radii and
 * an actual elevation shadow where Flat draws none at all.
 *
 * <p>Piloted on the Settings screen only for now ({@link org.telegram.ui.Components.PrimeOptionCardsCell}
 * reads {@link Role#CARD} from whichever system is current) - chat bubble rendering is deliberately
 * not migrated yet, since that is the highest-traffic, highest-regression-risk surface in the app.
 */
public final class MaterialDesignSystem implements DesignSystem {

    public static final MaterialDesignSystem INSTANCE = new MaterialDesignSystem();

    private static final Interpolator EASING = new DecelerateInterpolator(1.5f);

    private MaterialDesignSystem() {
    }

    @Override
    public Drawable surfaceFill(Role role, Theme.ResourcesProvider provider) {
        final int key;
        switch (role) {
            case BUBBLE_OUTGOING:
                key = Theme.key_chat_outBubble;
                break;
            case BUBBLE_INCOMING:
                key = Theme.key_chat_inBubble;
                break;
            case DIALOG:
                key = Theme.key_dialogBackground;
                break;
            default:
                key = Theme.key_windowBackgroundWhite;
                break;
        }
        return new ColorDrawable(Theme.getColor(key, provider));
    }

    @Override
    public float cornerRadius(Role role) {
        // M3's shape scale: cards/containers sit at "medium" (12-16dp), FAB at "large" (28dp,
        // same value stock Material FABs use), chat bubbles keep the user's own bubbleRadius
        // setting rather than overriding a value people already tuned to their own taste.
        switch (role) {
            case BUBBLE_OUTGOING:
            case BUBBLE_INCOMING:
                return SharedConfig.bubbleRadius;
            case FAB:
                return 28f;
            case CARD:
                return 16f;
            case DIALOG:
                return 24f;
            case BOTTOM_BAR:
                return 16f;
            case INPUT_FIELD:
                return 24f;
            default:
                return 12f;
        }
    }

    @Override
    public ShadowSpec shadow(Role role) {
        switch (role) {
            case CARD:
                return new ShadowSpec(4f, 0f, 1f, 60);
            case FAB:
                return new ShadowSpec(6f, 0f, 3f, 90);
            case DIALOG:
                return new ShadowSpec(10f, 0f, 4f, 100);
            default:
                return ShadowSpec.NONE;
        }
    }

    @Override
    public int motionDurationMs(Role role) {
        return 200;
    }

    @Override
    public Interpolator motionEasing(Role role) {
        return EASING;
    }

    @Override
    public float surfaceBlur(Role role) {
        return 0f;
    }
}
