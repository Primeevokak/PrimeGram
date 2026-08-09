package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;

/**
 * PrimeGram: drag the corner of a mock bubble to set {@link SharedConfig#bubbleRadius} (0-17),
 * instead of reading a plain number off a generic slider.
 *
 * <p>Same shape as {@link PrimeSidebarZoneEditor}: the value lives in a local field and this view
 * never touches preferences itself, only reporting through {@link #setOnChange} on every frame of
 * the drag - persisting is the caller's call, same as it already is for every other card/slider
 * in this settings screen.
 */
public class PrimeBubbleRadiusEditor extends View {

    private static final int MAX_RADIUS = 17;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bubble = new RectF();

    private final Theme.ResourcesProvider resourcesProvider;

    private int radius = SharedConfig.bubbleRadius;
    private boolean dragging;
    private Runnable onChange;

    public PrimeBubbleRadiusEditor(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        textPaint.setTextSize(dp(13));
        textPaint.setTypeface(AndroidUtilities.bold());
        textPaint.setTextAlign(android.graphics.Paint.Align.CENTER);
    }

    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    public int getRadius() {
        return radius;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(96));
    }

    private void layoutBubble() {
        final float w = Math.min(getMeasuredWidth() * 0.55f, dp(180));
        final float h = dp(48);
        final float cx = getMeasuredWidth() / 2f;
        final float cy = getMeasuredHeight() / 2f;
        bubble.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
    }

    /** Where the drag handle sits: the outer edge of the rounded top-right corner. */
    private float handleX() {
        return bubble.right - dp(radius) * 0.3f;
    }

    private float handleY() {
        return bubble.top + dp(radius) * 0.3f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        layoutBubble();

        final int accent = Theme.getColor(Theme.key_windowBackgroundWhiteValueText, resourcesProvider);
        final int bubbleColor = Theme.getColor(Theme.key_chat_outBubble, resourcesProvider);
        final int screen = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);

        paint.setColor(bubbleColor);
        canvas.drawRoundRect(bubble, dp(radius), dp(radius), paint);

        final float hx = handleX();
        final float hy = handleY();
        handlePaint.setStyle(Paint.Style.FILL);
        handlePaint.setColor(screen);
        canvas.drawCircle(hx, hy, dp(dragging ? 11 : 9), handlePaint);
        handlePaint.setStyle(Paint.Style.STROKE);
        handlePaint.setStrokeWidth(dp(1.5f));
        handlePaint.setColor(accent);
        canvas.drawCircle(hx, hy, dp(dragging ? 11 : 9), handlePaint);

        textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        canvas.drawText(String.valueOf(radius), bubble.centerX(), bubble.bottom + dp(20), textPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        layoutBubble();
        final float x = event.getX(), y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                final float hx = handleX(), hy = handleY();
                final float slop = dp(28);
                if (Math.abs(x - hx) > slop || Math.abs(y - hy) > slop) {
                    return false;
                }
                dragging = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                AndroidUtilities.vibrateCursor(this);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (!dragging) {
                    return false;
                }
                // Distance from the sharp (unrounded) corner drives the value: right at the
                // corner is 0, MAX_RADIUS worth of dp diagonally out is the ceiling. Only one
                // axis is tracked (vertical) because it reads the same as "drag the corner down
                // to round it off" without needing the finger to trace a precise arc.
                final float fromCorner = bubble.top - y + (bubble.right - x);
                final int value = Math.round(fromCorner / 2f / AndroidUtilities.density);
                final int clamped = Math.max(0, Math.min(MAX_RADIUS, value));
                if (clamped != radius) {
                    radius = clamped;
                    invalidate();
                    if (onChange != null) {
                        onChange.run();
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                invalidate();
                return true;
        }
        return dragging;
    }
}
