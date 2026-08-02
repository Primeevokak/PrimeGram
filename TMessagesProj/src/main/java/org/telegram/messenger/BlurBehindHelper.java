package org.telegram.messenger;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

/**
 * PrimeGram: an attachable blur-behind-plus-shadow drawer for views that cannot extend
 * {@code BlurredFrameLayout} - ported from inugram's {@code BlurBehindHelper.kt}, itself a
 * deliberate crutch on their side rather than routing everything through {@code blur3}: matching
 * the flat, non-island tab-bar look means drawing the same soft blur strip {@code blur3} draws,
 * without adopting the rest of what that pipeline assumes about its host view. {@link #view} is
 * whatever bar wants the effect; {@link #contentView} is the {@link SizeNotifierFrameLayout}
 * ancestor that actually knows how to blur behind it - already true in this codebase, since
 * {@code blurBehindViews}/{@code drawBlurRect} are stock methods on it.
 */
public final class BlurBehindHelper {

    private final View view;
    private final SizeNotifierFrameLayout contentView;
    private final int colorKey;
    private final boolean isTop;
    private final float topShadowDp;
    private final float bottomShadowDp;
    private final boolean drawBottomDivider;

    private final Rect rect = new Rect();
    private final Paint paint = new Paint();
    private GradientDrawable topShadow;
    private GradientDrawable bottomShadow;

    public BlurBehindHelper(View view, SizeNotifierFrameLayout contentView, int colorKey) {
        this(view, contentView, colorKey, true, 0f, 0f, false);
    }

    public BlurBehindHelper(View view, SizeNotifierFrameLayout contentView, int colorKey, boolean isTop,
                             float topShadowDp, float bottomShadowDp, boolean drawBottomDivider) {
        this.view = view;
        this.contentView = contentView;
        this.colorKey = colorKey;
        this.isTop = isTop;
        this.topShadowDp = topShadowDp;
        this.bottomShadowDp = bottomShadowDp;
        this.drawBottomDivider = drawBottomDivider;

        contentView.blurBehindViews.add(view);
        if (topShadowDp > 0f) {
            topShadow = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                    new int[]{Theme.getColor(Theme.key_dialogShadowLine), 0});
        }
        if (bottomShadowDp > 0f) {
            bottomShadow = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Theme.getColor(Theme.key_dialogShadowLine), 0});
        }
    }

    public static BlurBehindHelper create(View view, SizeNotifierFrameLayout contentView, int colorKey) {
        return new BlurBehindHelper(view, contentView, colorKey);
    }

    public static BlurBehindHelper create(View view, SizeNotifierFrameLayout contentView, int colorKey, boolean isTop,
                                           float topShadowDp, float bottomShadowDp) {
        return new BlurBehindHelper(view, contentView, colorKey, isTop, topShadowDp, bottomShadowDp, false);
    }

    public void draw(Canvas canvas) {
        draw(canvas, -1, -1);
    }

    public void draw(Canvas canvas, int heightOverride, int alphaOverride) {
        final int w = view.getMeasuredWidth();
        final int h = heightOverride >= 0 ? heightOverride : view.getMeasuredHeight();
        final int topShadowPx = AndroidUtilities.dp(topShadowDp);
        final int bottomShadowPx = AndroidUtilities.dp(bottomShadowDp);

        if (topShadow != null) {
            topShadow.setBounds(0, 0, w, topShadowPx);
            topShadow.draw(canvas);
        }
        if (bottomShadow != null) {
            bottomShadow.setBounds(0, h - bottomShadowPx, w, h);
            bottomShadow.draw(canvas);
        }

        if (h <= topShadowPx + bottomShadowPx) {
            return;
        }
        rect.set(0, topShadowPx, w, h - bottomShadowPx);
        paint.setColor(Theme.getColor(colorKey));
        paint.setAlpha(alphaOverride >= 0 ? alphaOverride : 255);
        final Float y = computeY();
        if (y != null) {
            contentView.drawBlurRect(canvas, y, rect, paint, isTop);
        }
        if (drawBottomDivider) {
            canvas.drawRect(0f, h - 1, w, h, Theme.dividerPaint);
        }
    }

    private Float computeY() {
        float y = 0f;
        View cur = view;
        while (cur != contentView) {
            y += cur.getY();
            final Object parent = cur.getParent();
            if (parent instanceof View) {
                cur = (View) parent;
            } else {
                return null;
            }
        }
        return y;
    }
}
