package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * PrimeGram: one candidate domain, with how fast it answered.
 *
 * <p>The bar is the point. Ten domains and ten numbers is a table nobody reads; ten bars is a
 * glance. Length is speed rather than latency - longer is better - because the row is answering
 * "which of these should I pick", and a chart where the winner is the shortest bar makes the reader
 * do the inversion themselves.
 *
 * <p>Colour carries the same information a second time, for the same reason every other status in
 * this app does: length alone is unreadable for someone comparing two rows that are nearly equal.
 */
public class PrimeTgWsDomainCell extends View {

    /** Not measured yet. */
    public static final long LATENCY_UNKNOWN = -2;
    /** Being measured right now. */
    public static final long LATENCY_MEASURING = -3;
    /** Measured and did not answer. */
    public static final long LATENCY_FAILED = -1;

    /** Anything at or above this is drawn as a full bar. */
    private static final float BEST_MS = 150f;
    private static final float WORST_MS = 2000f;

    private final Theme.ResourcesProvider resourcesProvider;

    private final TextPaint namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint valuePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final android.graphics.Path check = new android.graphics.Path();

    private String name = "";
    private String caption;
    private long latency = LATENCY_UNKNOWN;
    private boolean selected;

    /** Animated so a fresh measurement grows into place instead of snapping. */
    private float drawnFill;
    private float targetFill;
    private long lastFrame;

    public PrimeTgWsDomainCell(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        namePaint.setTextSize(AndroidUtilities.dp(15));
        valuePaint.setTextSize(AndroidUtilities.dp(13));
        valuePaint.setTextAlign(Paint.Align.RIGHT);
        checkPaint.setStyle(Paint.Style.STROKE);
        checkPaint.setStrokeCap(Paint.Cap.ROUND);
        checkPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    /**
     * @param caption shown instead of a latency, for the "measure and choose" row which has no
     *                latency of its own.
     */
    public void set(String name, String caption, long latency, boolean selected) {
        this.name = name;
        this.caption = caption;
        this.latency = latency;
        this.selected = selected;
        this.targetFill = fillFor(latency);
        if (drawnFill == 0f && targetFill > 0f) {
            lastFrame = 0;
        }
        namePaint.setTypeface(selected ? AndroidUtilities.bold() : null);
        invalidate();
    }

    private static float fillFor(long latency) {
        if (latency < 0) {
            return latency == LATENCY_FAILED ? 0.12f : 0f;
        }
        if (latency <= BEST_MS) {
            return 1f;
        }
        if (latency >= WORST_MS) {
            return 0.18f;
        }
        return 1f - 0.82f * ((latency - BEST_MS) / (WORST_MS - BEST_MS));
    }

    private int barColor() {
        if (latency == LATENCY_FAILED) {
            return 0xFFE0574F;
        }
        if (latency < 0) {
            return Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider);
        }
        if (latency <= 400) {
            return 0xFF4EBE5F;
        }
        return latency <= 900 ? 0xFFE8A33D : 0xFFE0574F;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(56));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final int left = AndroidUtilities.dp(21);
        final int right = getWidth() - AndroidUtilities.dp(21);
        final int checkSpace = selected ? AndroidUtilities.dp(26) : 0;

        namePaint.setColor(Theme.getColor(selected
                ? Theme.key_windowBackgroundWhiteBlueText
                : Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        canvas.drawText(name, left, AndroidUtilities.dp(22), namePaint);

        valuePaint.setColor(latency >= 0
                ? barColor()
                : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        canvas.drawText(valueText(), right - checkSpace, AndroidUtilities.dp(22), valuePaint);

        if (selected) {
            final float cx = right - AndroidUtilities.dp(7);
            final float cy = AndroidUtilities.dp(17);
            checkPaint.setStrokeWidth(AndroidUtilities.dp(2));
            checkPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourcesProvider));
            check.reset();
            check.moveTo(cx - AndroidUtilities.dp(6), cy);
            check.lineTo(cx - AndroidUtilities.dp(2), cy + AndroidUtilities.dp(4));
            check.lineTo(cx + AndroidUtilities.dp(6), cy - AndroidUtilities.dp(5));
            canvas.drawPath(check, checkPaint);
        }

        if (caption != null) {
            return;
        }

        // The bar itself, on its own line so a long domain name never squeezes it.
        final int barTop = AndroidUtilities.dp(34);
        final int barHeight = AndroidUtilities.dp(5);
        trackPaint.setColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
        rect.set(left, barTop, right, barTop + barHeight);
        canvas.drawRoundRect(rect, barHeight / 2f, barHeight / 2f, trackPaint);

        final long now = System.currentTimeMillis();
        if (lastFrame == 0) {
            lastFrame = now;
        }
        final float step = Math.min(1f, (now - lastFrame) / 220f);
        lastFrame = now;
        drawnFill += (targetFill - drawnFill) * step;
        if (Math.abs(targetFill - drawnFill) > 0.002f) {
            invalidate();
        } else {
            drawnFill = targetFill;
        }

        if (drawnFill > 0f) {
            barPaint.setColor(barColor());
            rect.set(left, barTop, left + (right - left) * drawnFill, barTop + barHeight);
            canvas.drawRoundRect(rect, barHeight / 2f, barHeight / 2f, barPaint);
        }
    }

    private String valueText() {
        if (caption != null) {
            return caption;
        }
        if (latency == LATENCY_MEASURING) {
            return "проверка…";
        }
        if (latency == LATENCY_FAILED) {
            return "нет ответа";
        }
        if (latency == LATENCY_UNKNOWN) {
            return "—";
        }
        return latency + " мс";
    }
}
