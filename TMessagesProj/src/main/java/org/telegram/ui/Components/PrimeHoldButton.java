package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;

import org.telegram.messenger.AndroidUtilities;

/**
 * PrimeGram: a reusable "hold to confirm" pill button - press and hold fills a bar
 * behind the label over {@link #holdDurationMs}; releasing early animates the fill back
 * to zero and does nothing; reaching 100% fires {@link #onConfirmed} once plus a
 * confirm-style haptic tick. Not tied to any one feature - any
 * {@code PrimeInteractiveActionCell} subclass can use this for a deliberate,
 * hard-to-trigger-by-accident action.
 *
 * <p>{@code setClickable(true)}/{@code setLongClickable(true)} (set in the constructor)
 * is what makes {@code RecyclerListView}'s own touch-interception back off for this
 * view - see {@code PrimeInteractiveActionCell}'s class doc for why that's sufficient on
 * its own, with no cell-level touch handling required.
 */
public class PrimeHoldButton extends View {

    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final String label;

    private float progress; // 0..1
    private ValueAnimator animator;
    private Runnable onConfirmed;
    public long holdDurationMs = 700;

    public PrimeHoldButton(Context context, String label, int accentColor, int textColor) {
        super(context);
        this.label = label;
        setClickable(true);
        setLongClickable(true);

        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(dp(1.5f));
        outlinePaint.setColor(accentColor);

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(accentColor);

        textPaint.setColor(textColor);
        textPaint.setTextSize(dp(14));
        textPaint.setTypeface(AndroidUtilities.bold());
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setOnConfirmed(Runnable onConfirmed) {
        this.onConfirmed = onConfirmed;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int textWidth = (int) Math.ceil(textPaint.measureText(label));
        setMeasuredDimension(textWidth + dp(32), dp(40));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startFilling();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (progress < 1f) {
                    cancelFilling();
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void startFilling() {
        cancelAnimator();
        animator = ValueAnimator.ofFloat(progress, 1f);
        animator.setDuration((long) (holdDurationMs * (1f - progress)));
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animator == animation && progress >= 0.999f) {
                    fireConfirmed();
                }
            }
        });
        animator.start();
    }

    private void cancelFilling() {
        cancelAnimator();
        animator = ValueAnimator.ofFloat(progress, 0f);
        animator.setDuration((long) (150 * progress));
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private void fireConfirmed() {
        try {
            performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        } catch (Throwable ignored) {
        }
        if (onConfirmed != null) {
            onConfirmed.run();
        }
    }

    private void cancelAnimator() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // A recycled/scrolled-off row must never leave a running fill animation ticking
        // into whatever unrelated cell gets bound to this view next - the one concrete
        // leak risk this component owns, guarded exactly here.
        cancelAnimator();
        progress = 0f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float radius = getHeight() / 2f;
        rect.set(outlinePaint.getStrokeWidth() / 2f, outlinePaint.getStrokeWidth() / 2f,
                getWidth() - outlinePaint.getStrokeWidth() / 2f, getHeight() - outlinePaint.getStrokeWidth() / 2f);
        canvas.drawRoundRect(rect, radius, radius, outlinePaint);

        if (progress > 0f) {
            int saveCount = canvas.save();
            canvas.clipRect(0, 0, getWidth() * progress, getHeight());
            canvas.drawRoundRect(rect, radius, radius, fillPaint);
            canvas.restoreToCount(saveCount);
        }

        // Text color crossfades from the accent-on-transparent state to
        // background-on-fill as the pill fills, so the label stays legible either way.
        int textColor = textPaint.getColor();
        textPaint.setColor(AndroidUtilities.getOffsetColor(outlinePaint.getColor(), textColor, progress, 1f));
        canvas.drawText(label, getWidth() / 2f, getHeight() / 2f - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint);
        textPaint.setColor(textColor);
    }
}
