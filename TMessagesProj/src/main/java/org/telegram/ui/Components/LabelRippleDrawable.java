package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.widget.TextView;

import androidx.annotation.NonNull;

/**
 * PrimeGram: a ripple mask sized to the label's actual text width instead of its full layout
 * bounds - ported from inugram's {@code LabelRippleDrawable.kt}, used by the flat tab-strip's
 * classic-mode tab background so the selector pill hugs the text rather than the whole cell.
 */
public class LabelRippleDrawable extends Drawable implements Drawable.Callback {

    private final TextView label;
    private final Drawable ripple;
    private final int horizontalPadding;
    private final int verticalInset;

    public LabelRippleDrawable(TextView label, Drawable ripple, int horizontalPadding, int verticalInset) {
        this.label = label;
        this.ripple = ripple;
        this.horizontalPadding = horizontalPadding;
        this.verticalInset = verticalInset;
        ripple.setCallback(this);
    }

    private void layoutRipple() {
        Rect b = getBounds();
        Layout layout = label.getLayout();
        int textWidth = (layout != null && layout.getLineCount() > 0)
                ? (int) Math.ceil(layout.getLineWidth(0))
                : b.width();
        int pillWidth = Math.min(b.width(), textWidth + horizontalPadding * 2);
        int cx = b.centerX();
        ripple.setBounds(cx - pillWidth / 2, b.top + verticalInset, cx + pillWidth / 2, b.bottom - verticalInset);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        layoutRipple();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        layoutRipple();
        ripple.draw(canvas);
    }

    @Override
    public boolean isStateful() {
        return ripple.isStateful();
    }

    @Override
    public boolean onStateChange(int[] state) {
        return ripple.setState(state);
    }

    @Override
    public void setHotspot(float x, float y) {
        ripple.setHotspot(x, y);
    }

    @Override
    public void jumpToCurrentState() {
        ripple.jumpToCurrentState();
    }

    @Override
    public void setAlpha(int alpha) {
        ripple.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        ripple.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return ripple.getOpacity();
    }

    @Override
    public void invalidateDrawable(Drawable who) {
        invalidateSelf();
    }

    @Override
    public void scheduleDrawable(Drawable who, Runnable what, long when) {
        scheduleSelf(what, when);
    }

    @Override
    public void unscheduleDrawable(Drawable who, Runnable what) {
        unscheduleSelf(what);
    }
}
