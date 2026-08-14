package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * PrimeGram: an N-label sliding switch - "pick one of these named states", drawn as a single pill
 * with an accent-filled segment that slides to whichever one is selected, in the same visual
 * language as {@link PrimeSwitch} (same easing, same "the moving part isn't perfectly rigid"
 * stretch trick) generalized from a plain on/off to any number of equal-width segments.
 */
public class PrimeSegmentedSwitch extends View {

    private static final int HEIGHT = 44;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private Theme.ResourcesProvider resourcesProvider;
    private String[] labels = new String[0];

    private int selectedIndex;
    /** Segment-space position of the pill, e.g. 1.7 while animating from segment 1 to 2 - the
     *  same "one float drives everything drawn" convention as PrimeSwitch's progress field, just
     *  over [0, labels.length - 1] instead of [0, 1]. */
    private float progress;
    private ValueAnimator animator;

    private OnSelectionChangedListener listener;

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int index);
    }

    public PrimeSegmentedSwitch(Context context) {
        super(context);
        textPaint.setTextSize(dp(13));
        textPaint.setTypeface(AndroidUtilities.bold());
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setResourcesProvider(Theme.ResourcesProvider resourcesProvider) {
        this.resourcesProvider = resourcesProvider;
        invalidate();
    }

    public void setLabels(String... labels) {
        this.labels = labels != null ? labels : new String[0];
        invalidate();
    }

    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        this.listener = listener;
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public void setSelected(int index, boolean animated) {
        if (labels.length == 0) {
            return;
        }
        index = Math.max(0, Math.min(labels.length - 1, index));
        if (this.selectedIndex == index && animator == null) {
            return;
        }
        this.selectedIndex = index;
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        if (!animated) {
            progress = index;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(progress, index);
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
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(HEIGHT));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP && labels.length > 0) {
            final float segmentWidth = getMeasuredWidth() / (float) labels.length;
            final int tapped = Math.max(0, Math.min(labels.length - 1, (int) (event.getX() / segmentWidth)));
            if (tapped != selectedIndex) {
                setSelected(tapped, true);
                if (listener != null) {
                    listener.onSelectionChanged(tapped);
                }
            }
            performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (labels.length == 0) {
            return;
        }
        final int accent = Theme.getColor(Theme.key_windowBackgroundWhiteValueText, resourcesProvider);
        final int track = Theme.multAlpha(
                Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider), 0.14f);
        final int textOff = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider);
        final int textOn = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);

        final float w = getMeasuredWidth();
        final float h = getMeasuredHeight();
        final float r = h / 2f;
        final int n = labels.length;

        trackPaint.setColor(track);
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, r, r, trackPaint);

        final float pad = dp(3);
        final float segment = (w - pad * 2) / n;
        // Deforms slightly while crossing a boundary between two segments (same trick as
        // PrimeSwitch's stretch), settling back to a clean rounded rect once it lands.
        final float distToBoundary = Math.abs(progress - Math.round(progress));
        final float stretch = dp(4) * Math.max(0f, 1f - distToBoundary * 2f);

        final float left = pad + segment * progress - stretch / 2f;
        final float right = left + segment + stretch;

        pillPaint.setColor(accent);
        rect.set(left, pad, right, h - pad);
        canvas.drawRoundRect(rect, r - pad, r - pad, pillPaint);

        final float cy = h / 2f - (textPaint.ascent() + textPaint.descent()) / 2f;
        for (int i = 0; i < n; i++) {
            final float center = pad + segment * i + segment / 2f;
            // How covered THIS segment's label currently is by the pill, 0..1 - mirrors the pill
            // geometry above (including the stretch term) rather than re-deriving it from
            // progress alone.
            final float coverage = Math.max(0f, 1f - Math.abs(progress - i));
            textPaint.setColor(ColorUtils.blendARGB(textOff, textOn, coverage));
            canvas.drawText(labels[i], center, cy, textPaint);
        }
    }
}
