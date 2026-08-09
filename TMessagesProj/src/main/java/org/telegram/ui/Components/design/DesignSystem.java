package org.telegram.ui.Components.design;

import android.graphics.drawable.Drawable;
import android.view.animation.Interpolator;

import org.telegram.ui.ActionBar.Theme;

/**
 * PrimeGram: the token layer a switchable visual design language (Material, Fluent, Neumorphism,
 * Glassmorphism, the existing Flat mode) is built from.
 *
 * <p>Sits alongside {@link Theme.ResourcesProvider}, not instead of it - the 777 {@code key_*}
 * colors Theme already resolves stay Theme's job, day/night and custom themes included. This
 * interface only covers the token categories Theme has never had a shared answer for: shape,
 * elevation and motion, which today are hardcoded per call site everywhere in the app (over 1,700
 * raw {@code dp(...)} calls in {@code ChatMessageCell} alone). {@link NonIslandHelper} proved why
 * a single boolean does not scale to this: even one alternate mode, aliased four ways, already
 * touches 33 files - five genuinely different renderers need a real value per {@link Role}, not
 * five more booleans.
 *
 * <p>{@link #current()} resolves from {@link org.telegram.messenger.PrimeTweaks#designMode()},
 * mirroring how {@code Theme.getColor} falls back to a global when no scoped provider is given -
 * most call sites don't need to think about scoping and can just ask the current system directly.
 */
public interface DesignSystem {

    /** A semantic role a component asks this system to render, not a raw value. */
    enum Role {
        CARD,
        BOTTOM_BAR,
        BUBBLE_OUTGOING,
        BUBBLE_INCOMING,
        FAB,
        DIALOG,
        INPUT_FIELD,
    }

    /** The fill behind a role's surface - a plain color today, a blur/gradient in later systems. */
    Drawable surfaceFill(Role role, Theme.ResourcesProvider provider);

    /** Corner radius for a role, in dp (not px - callers apply their own {@code dp()}). */
    float cornerRadius(Role role);

    /** Drop-shadow spec for a role. {@link ShadowSpec#NONE} for flat, no-elevation roles. */
    ShadowSpec shadow(Role role);

    /** How long a role's own transitions should run, honoring the 120-200ms motion budget. */
    int motionDurationMs(Role role);

    Interpolator motionEasing(Role role);

    /** Background blur radius in dp for a role. Zero everywhere except a Glass-style system. */
    float surfaceBlur(Role role);

    static DesignSystem current() {
        // Only FLAT exists so far (Stage 2 Phase 2.0) - this switch is where Material, Fluent,
        // Neumorphism and Glassmorphism plug in as their own phases land, each behind the same
        // org.telegram.messenger.PrimeTweaks.DESIGN_MODE value the settings screen will expose.
        switch (org.telegram.messenger.PrimeTweaks.designMode()) {
            case 1:
                return MaterialDesignSystem.INSTANCE;
            case 0:
            default:
                return FlatDesignSystem.INSTANCE;
        }
    }
}
