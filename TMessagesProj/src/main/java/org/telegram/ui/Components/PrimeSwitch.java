package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * PrimeGram: the switch used on our settings rows.
 *
 * <p>Telegram's own {@code Switch} is a fine control, but it is drawn from bitmaps for the icons
 * and carries a decade of special cases (drag, half-checked, icon sets). This one exists to do a
 * single thing well and to look like it belongs to this fork: the track fills with the accent
 * colour from the side the thumb came from rather than cross-fading, and the thumb stretches
 * slightly while it travels and settles back — the same trick a physical toggle plays on the eye,
 * where something that moves fast should not have perfectly rigid edges.
 *
 * <p>The check mark inside the thumb is drawn, not an asset: at this size a bitmap tick is either
 * soft or aliased depending on the device, and a two-segment path is neither.
 */
public class PrimeSwitch extends View {

    private static final int WIDTH = 42;
    private static final int HEIGHT = 24;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path tick = new Path();

    private Theme.ResourcesProvider resourcesProvider;

    private boolean checked;
    /** 0 while off, 1 while on; everything drawn here is a function of this one number. */
    private float progress;
    private ValueAnimator animator;

    public PrimeSwitch(Context context) {
        super(context);
        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeCap(Paint.Cap.ROUND);
        tickPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setResourcesProvider(Theme.ResourcesProvider resourcesProvider) {
        this.resourcesProvider = resourcesProvider;
        invalidate();
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean checked, boolean animated) {
        if (this.checked == checked && animator == null) {
            return;
        }
        this.checked = checked;
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        if (!animated) {
            progress = checked ? 1f : 0f;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(progress, checked ? 1f : 0f);
        animator.setDuration(280);
        animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(dp(WIDTH), dp(HEIGHT));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final int accent = Theme.getColor(Theme.key_windowBackgroundWhiteValueText, resourcesProvider);
        final int offTrack = Theme.multAlpha(
                Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider), 0.32f);

        final float w = getMeasuredWidth();
        final float h = getMeasuredHeight();
        final float r = h / 2f;

        trackPaint.setColor(ColorUtils.blendARGB(offTrack, accent, progress));
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, r, r, trackPaint);

        // Stretch peaks halfway through the travel and is gone at both ends, so a switch at rest
        // is always a perfect circle and only motion is allowed to deform it.
        final float stretch = dp(5) * (1f - Math.abs(progress * 2f - 1f));
        final float pad = dp(2.5f);
        final float thumbR = r - pad;
        final float left = pad + (w - pad * 2 - thumbR * 2) * progress;
        final float right = left + thumbR * 2;

        thumbPaint.setColor(ColorUtils.blendARGB(
                Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider), 0xFFFFFFFF, progress));
        rect.set(left - stretch * progress, pad, right + stretch * (1f - progress), h - pad);
        canvas.drawRoundRect(rect, thumbR, thumbR, thumbPaint);

        if (progress > 0.01f) {
            // Drawn last and only once the thumb is mostly there, so the tick appears to be
            // revealed by the movement rather than to ride along with it.
            final float alpha = Math.max(0, (progress - 0.35f) / 0.65f);
            tickPaint.setColor(ColorUtils.setAlphaComponent(accent, (int) (0xFF * alpha)));
            tickPaint.setStrokeWidth(dp(1.8f));
            final float cx = (left + right) / 2f;
            final float cy = h / 2f;
            tick.rewind();
            tick.moveTo(cx - dp(3.5f), cy);
            tick.lineTo(cx - dp(1f), cy + dp(2.5f));
            tick.lineTo(cx + dp(3.5f), cy - dp(2.5f));
            canvas.save();
            canvas.scale(AndroidUtilities.lerp(0.7f, 1f, alpha), AndroidUtilities.lerp(0.7f, 1f, alpha), cx, cy);
            canvas.drawPath(tick, tickPaint);
            canvas.restore();
        }
    }
}
