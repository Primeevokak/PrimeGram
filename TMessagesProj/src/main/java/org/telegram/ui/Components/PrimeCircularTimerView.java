package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.music.PrimeMusicTheme;

import java.util.Locale;

/**
 * PrimeGram Music: a rotary sleep-timer dial - drag clockwise around the circle to add
 * minutes, same gesture as a real kitchen timer. One full lap is 60 minutes; going past
 * 60 starts a new, slightly smaller ring nested inside the first, which stays fully lit
 * while the new ring fills up in turn - exactly the layered-rings look the user asked
 * for (matches a real reference screenshot of the desired behaviour, not a guess).
 * Dragging back past 0 on the innermost active ring un-winds it and drops back to the
 * previous ring, symmetric with how it filled up.
 */
public class PrimeCircularTimerView extends View {

    public interface OnMinutesChangeListener {
        void onMinutesChanged(int minutes);
    }

    private static final int MAX_MINUTES = 600; // 10h - a sane ceiling for a sleep timer
    private static final int MINUTES_PER_LAP = 60;
    private static final int[] TICK_MINUTES = {0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55};

    private float continuousMinutes;
    private boolean dragging;
    private float lastAngleDeg;

    private OnMinutesChangeListener listener;

    private final Paint ringBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerTimePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();

    public PrimeCircularTimerView(Context context) {
        super(context);

        ringBgPaint.setStyle(Paint.Style.STROKE);
        ringBgPaint.setStrokeWidth(dp(6));
        ringBgPaint.setColor(0x1FFFFFFF);

        ringFillPaint.setStyle(Paint.Style.STROKE);
        ringFillPaint.setStrokeWidth(dp(6));
        ringFillPaint.setStrokeCap(Paint.Cap.ROUND);

        handlePaint.setStyle(Paint.Style.FILL);
        handlePaint.setColor(0xFFFFFFFF);

        tickPaint.setTextAlign(Paint.Align.CENTER);
        tickPaint.setTextSize(dp(13));
        tickPaint.setColor(PrimeMusicTheme.TEXT_TERTIARY);

        centerTimePaint.setTextAlign(Paint.Align.CENTER);
        centerTimePaint.setTypeface(AndroidUtilities.bold());
        centerTimePaint.setColor(PrimeMusicTheme.TEXT_PRIMARY);

        applyAccent();
    }

    private void applyAccent() {
        int accent = PrimeMusicTheme.currentAccent | 0xFF000000;
        ringFillPaint.setColor(accent);
    }

    public void setMinutes(int minutes) {
        continuousMinutes = Math.max(0, Math.min(MAX_MINUTES, minutes));
        invalidate();
    }

    public int getMinutes() {
        return Math.round(continuousMinutes);
    }

    public void setListener(OnMinutesChangeListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int size = Math.min(width, dp(280));
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float outerRadius = Math.min(getWidth(), getHeight()) / 2f - dp(28);

        int totalMinutes = Math.round(continuousMinutes);
        int fullLaps = totalMinutes / MINUTES_PER_LAP;
        int remainder = totalMinutes % MINUTES_PER_LAP;
        int activeLapIndex = remainder == 0 && fullLaps > 0 ? fullLaps - 1 : fullLaps;
        int lapCount = Math.max(1, activeLapIndex + 1);
        float activeLapProgress = (remainder == 0 && fullLaps > 0) ? 1f : remainder / (float) MINUTES_PER_LAP;

        float ringSpacing = dp(11);

        // Outermost ring also carries the minute tick labels.
        drawTickLabels(canvas, cx, cy, outerRadius);

        for (int lap = 0; lap < lapCount; lap++) {
            float radius = outerRadius - lap * ringSpacing;
            if (radius < dp(24)) {
                break;
            }
            canvas.drawCircle(cx, cy, radius, ringBgPaint);

            float sweep;
            if (lap < activeLapIndex) {
                sweep = 360f;
            } else {
                sweep = 360f * activeLapProgress;
            }
            if (sweep <= 0.01f) {
                continue;
            }
            arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius);
            canvas.drawArc(arcRect, -90f, sweep, false, ringFillPaint);

            if (lap == activeLapIndex) {
                double angleRad = Math.toRadians(-90 + sweep);
                float hx = cx + radius * (float) Math.cos(angleRad);
                float hy = cy + radius * (float) Math.sin(angleRad);
                canvas.drawCircle(hx, hy, dp(7), handlePaint);
                canvas.drawCircle(hx, hy, dp(7), ringOutline());
            }
        }

        String timeText = String.format(Locale.US, "%d:00", totalMinutes);
        centerTimePaint.setTextSize(dp(totalMinutes >= 1000 ? 30 : 40));
        Paint.FontMetrics fm = centerTimePaint.getFontMetrics();
        float textY = cy - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(timeText, cx, textY, centerTimePaint);
    }

    private Paint outlinePaintCache;

    private Paint ringOutline() {
        if (outlinePaintCache == null) {
            outlinePaintCache = new Paint(Paint.ANTI_ALIAS_FLAG);
            outlinePaintCache.setStyle(Paint.Style.STROKE);
            outlinePaintCache.setStrokeWidth(dp(1.5f));
            outlinePaintCache.setColor(0xFF000000);
        }
        return outlinePaintCache;
    }

    private void drawTickLabels(Canvas canvas, float cx, float cy, float outerRadius) {
        float labelRadius = outerRadius + dp(18);
        for (int m : TICK_MINUTES) {
            double angleRad = Math.toRadians(-90 + m / (float) MINUTES_PER_LAP * 360f);
            float lx = cx + labelRadius * (float) Math.cos(angleRad);
            float ly = cy + labelRadius * (float) Math.sin(angleRad);
            Paint.FontMetrics fm = tickPaint.getFontMetrics();
            canvas.drawText(String.valueOf(m), lx, ly - (fm.ascent + fm.descent) / 2f, tickPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                dragging = true;
                lastAngleDeg = angleAt(event.getX() - cx, event.getY() - cy);
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (!dragging) {
                    return false;
                }
                float angle = angleAt(event.getX() - cx, event.getY() - cy);
                float delta = angle - lastAngleDeg;
                if (delta > 180f) {
                    delta -= 360f;
                } else if (delta < -180f) {
                    delta += 360f;
                }
                lastAngleDeg = angle;
                continuousMinutes += delta / 360f * MINUTES_PER_LAP;
                continuousMinutes = Math.max(0, Math.min(MAX_MINUTES, continuousMinutes));
                invalidate();
                if (listener != null) {
                    listener.onMinutesChanged(getMinutes());
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                dragging = false;
                return true;
            }
        }
        return false;
    }

    /** Clock-face angle in degrees, 0 at the top, increasing clockwise. */
    private float angleAt(float dx, float dy) {
        float deg = (float) Math.toDegrees(Math.atan2(dy, dx));
        deg = (deg + 90 + 360) % 360;
        return deg;
    }
}
