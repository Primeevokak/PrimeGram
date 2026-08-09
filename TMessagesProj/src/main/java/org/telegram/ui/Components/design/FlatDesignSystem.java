package org.telegram.ui.Components.design;

import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.animation.Interpolator;

import org.telegram.messenger.PrimeTweaks;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;

/**
 * PrimeGram: the design system that reproduces today's rendering exactly - existing values read
 * from the same places every call site already reads them from ({@link SharedConfig#bubbleRadius},
 * the {@link PrimeTweaks#SQUARE_FAB} toggle), not a new opinion about what they should be.
 *
 * <p>This is deliberately the only implementation that ships in Stage 2 Phase 2.0: nothing calls
 * into it yet, so its correctness is provable by inspection against the values it was copied from,
 * rather than by a visual diff against a screen nothing here draws to.
 */
public final class FlatDesignSystem implements DesignSystem {

    public static final FlatDesignSystem INSTANCE = new FlatDesignSystem();

    private FlatDesignSystem() {
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
        switch (role) {
            case BUBBLE_OUTGOING:
            case BUBBLE_INCOMING:
                return SharedConfig.bubbleRadius;
            case FAB:
                return PrimeTweaks.get(PrimeTweaks.SQUARE_FAB) ? 6f : 28f;
            case CARD:
                return 8f;
            case DIALOG:
                return 12f;
            case BOTTOM_BAR:
            case INPUT_FIELD:
            default:
                // Flat/classic mode's whole point: panels sit flush, no rounding.
                return 0f;
        }
    }

    @Override
    public ShadowSpec shadow(Role role) {
        // Flat mode draws no elevation anywhere - that is what makes it flat.
        return ShadowSpec.NONE;
    }

    @Override
    public int motionDurationMs(Role role) {
        return 150;
    }

    @Override
    public Interpolator motionEasing(Role role) {
        return CubicBezierInterpolator.EASE_OUT_QUINT;
    }

    @Override
    public float surfaceBlur(Role role) {
        return 0f;
    }
}
