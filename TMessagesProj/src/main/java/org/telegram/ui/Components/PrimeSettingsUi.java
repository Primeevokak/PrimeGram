package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * PrimeGram: the small design system the settings screens are built from.
 *
 * <p>Every colour here is derived from the current theme rather than fixed, so these surfaces
 * follow a custom or user-made theme instead of fighting it. That is the difference between a
 * settings screen that looks part of the app and one that looks bolted on.
 */
public class PrimeSettingsUi {

    /** A surface a shade away from the background - enough to read as a card, not as a box. */
    public static int surfaceColor() {
        return Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText),
                Theme.isCurrentThemeDark() ? 0.05f : 0.035f);
    }

    /** The hairline around a card. Same hue as the surface, a touch stronger. */
    public static int outlineColor() {
        return Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText),
                (Theme.isCurrentThemeDark() ? 0.05f : 0.035f) + 0.085f);
    }

    /**
     * The grey of a mock element inside a preview - a stand-in avatar, a line of text.
     *
     * <p>{@code strong} for the thing the preview is actually about, so the eye lands on it
     * rather than on the scaffolding around it.
     */
    public static int mockColor(boolean strong) {
        return Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2), strong ? 0.4f : 0.2f);
    }

    /**
     * A rounded card that can show it is selected.
     *
     * <p>Selection is a float rather than a boolean so the outline can grow into the accent colour
     * instead of snapping: a card that changes under your finger reads as a response, a card that
     * jumps reads as a redraw.
     */
    public static class CardDrawable extends Drawable {

        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final float radius;

        private float selection;
        private ValueAnimator animator;
        private Runnable invalidateCallback;

        public CardDrawable() {
            this(12);
        }

        public CardDrawable(float radiusDp) {
            this.radius = dp(radiusDp);
            stroke.setStyle(Paint.Style.STROKE);
        }

        /** Where to send invalidations, since a Drawable inside a custom view has no host. */
        public void setInvalidateCallback(Runnable callback) {
            this.invalidateCallback = callback;
        }

        public void setSelected(boolean selected, boolean animated) {
            final float target = selected ? 1f : 0f;
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
            if (!animated) {
                selection = target;
                invalidateSelf();
                return;
            }
            animator = ValueAnimator.ofFloat(selection, target).setDuration(250);
            animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            animator.addUpdateListener(a -> {
                selection = (float) a.getAnimatedValue();
                invalidateSelf();
            });
            animator.start();
        }

        public float getSelection() {
            return selection;
        }

        @Override
        public void invalidateSelf() {
            super.invalidateSelf();
            if (invalidateCallback != null) {
                invalidateCallback.run();
            }
        }

        @Override
        public void draw(Canvas canvas) {
            fill.setColor(surfaceColor());
            stroke.setColor(ColorUtils.blendARGB(outlineColor(),
                    Theme.getColor(Theme.key_windowBackgroundWhiteValueText), selection));
            stroke.setStrokeWidth(dp(AndroidUtilities.lerp(0.5f, 2f, selection)));
            final float inset = stroke.getStrokeWidth() / 2f;
            rect.set(getBounds().left + inset, getBounds().top + inset,
                    getBounds().right - inset, getBounds().bottom - inset);
            canvas.drawRoundRect(rect, radius, radius, fill);
            canvas.drawRoundRect(rect, radius, radius, stroke);
        }

        @Override
        public void setAlpha(int alpha) {
            fill.setAlpha(alpha);
            stroke.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            fill.setColorFilter(colorFilter);
            stroke.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }
}
