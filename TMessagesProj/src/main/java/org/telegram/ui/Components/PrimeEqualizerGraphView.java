package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Shader;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.music.PrimeMusicTheme;

/**
 * PrimeGram Music: a real parametric-EQ style graph - a smooth gain curve through
 * draggable band points, replacing the plain stack of horizontal {@code SeekBar}s the
 * equalizer screen used before. Vertical drag on (or near) a band's dot changes that
 * band's gain; the curve between bands is drawn for readability only, dragging always
 * snaps to the nearest band rather than interpolating a new one.
 */
public class PrimeEqualizerGraphView extends View {

    public interface OnBandChangeListener {
        void onBandChanged(int bandIndex, short level);
    }

    /** Asked when the user grabs the graph while the equalizer is switched off. */
    public interface OnEnableRequest {
        void requestEnable();
    }

    private OnEnableRequest enableRequest;

    public void setOnEnableRequest(OnEnableRequest enableRequest) {
        this.enableRequest = enableRequest;
    }

    /** Whether a touch at (x, y) is on the graph area - the host keeps swipe-back off it. */
    public boolean isTouchOnGraph(float x, float y) {
        return freqHz.length > 0 && x >= 0 && y >= 0 && x <= getWidth() && y <= getHeight();
    }

    private int[] freqHz = new int[0];
    private short[] levels = new short[0];
    private short minLevel;
    private short maxLevel;
    private boolean graphEnabled = true;

    private OnBandChangeListener listener;

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint zeroLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint curveFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint curveStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint idleValuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path curvePath = new Path();
    private final Path fillPath = new Path();

    private int draggingBand = -1;
    private float dotRadius = dp(6);
    private float dotRadiusActive = dp(9);
    private final ValueAnimator activeAnim = ValueAnimator.ofFloat(0f, 1f);
    private float activeFactor;

    private static final int GRAPH_TOP_PAD_DP = 22;
    private static final int GRAPH_BOTTOM_PAD_DP = 28;
    private static final int SIDE_PAD_DP = 12;

    public PrimeEqualizerGraphView(Context context) {
        super(context);
        setClickable(true);
        setFocusable(true);

        gridPaint.setStrokeWidth(dp(0.75f));
        gridPaint.setColor(0x14FFFFFF);

        zeroLinePaint.setStrokeWidth(dp(1));
        zeroLinePaint.setColor(0x33FFFFFF);

        curveStrokePaint.setStyle(Paint.Style.STROKE);
        curveStrokePaint.setStrokeWidth(dp(2.5f));
        curveStrokePaint.setStrokeCap(Paint.Cap.ROUND);
        curveStrokePaint.setStrokeJoin(Paint.Join.ROUND);

        curveFillPaint.setStyle(Paint.Style.FILL);

        dotPaint.setStyle(Paint.Style.FILL);
        dotRingPaint.setStyle(Paint.Style.STROKE);
        dotRingPaint.setStrokeWidth(dp(2));
        dotRingPaint.setColor(0xFF000000);

        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(dp(PrimeMusicTheme.SP_SMALL));
        labelPaint.setColor(PrimeMusicTheme.TEXT_TERTIARY);

        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTextSize(dp(PrimeMusicTheme.SP_SMALL));
        valuePaint.setTypeface(AndroidUtilities.bold());

        idleValuePaint.setTextAlign(Paint.Align.CENTER);
        idleValuePaint.setTextSize(dp(PrimeMusicTheme.SP_SMALL) * 0.85f);
        idleValuePaint.setColor(PrimeMusicTheme.TEXT_TERTIARY);

        activeAnim.setDuration(140);
        activeAnim.addUpdateListener(a -> {
            activeFactor = (float) a.getAnimatedValue();
            invalidate();
        });

        applyAccentColors();
    }

    private void applyAccentColors() {
        int accent = PrimeMusicTheme.currentAccent | 0xFF000000;
        curveStrokePaint.setColor(accent);
        dotPaint.setColor(accent);
        valuePaint.setColor(accent);
        curveFillPaint.setShader(new LinearGradient(0, 0, 0, dp(160),
                (accent & 0x00FFFFFF) | 0x55000000, (accent & 0x00FFFFFF) | 0x00000000, Shader.TileMode.CLAMP));
    }

    /** (Re)initializes the graph from scratch - called once band metadata is known. */
    public void setBands(int[] freqHz, short min, short max, short[] initialLevels) {
        this.freqHz = freqHz != null ? freqHz : new int[0];
        this.minLevel = min;
        this.maxLevel = max;
        this.levels = new short[this.freqHz.length];
        if (initialLevels != null) {
            System.arraycopy(initialLevels, 0, levels, 0, Math.min(initialLevels.length, levels.length));
        }
        requestLayout();
        invalidate();
    }

    public void setBandLevel(int index, short level) {
        if (index < 0 || index >= levels.length) {
            return;
        }
        levels[index] = level;
        invalidate();
    }

    public void setListener(OnBandChangeListener listener) {
        this.listener = listener;
    }

    public void setGraphEnabled(boolean enabled) {
        this.graphEnabled = enabled;
        setAlpha(enabled ? 1f : 0.45f);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = dp(180);
        setMeasuredDimension(width, height);
    }

    private PointF bandPoint(int index, int width, int height) {
        float top = dp(GRAPH_TOP_PAD_DP);
        float bottom = height - dp(GRAPH_BOTTOM_PAD_DP);
        float left = dp(SIDE_PAD_DP);
        float right = width - dp(SIDE_PAD_DP);
        int count = freqHz.length;
        float x = count <= 1 ? (left + right) / 2f : left + (right - left) * index / (float) (count - 1);
        int span = Math.max(1, maxLevel - minLevel);
        float ratio = (levels[index] - minLevel) / (float) span; // 0..1, 0=min gain, 1=max gain
        float y = bottom - ratio * (bottom - top);
        return new PointF(x, y);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        float top = dp(GRAPH_TOP_PAD_DP);
        float bottom = height - dp(GRAPH_BOTTOM_PAD_DP);
        float left = dp(SIDE_PAD_DP);
        float right = width - dp(SIDE_PAD_DP);

        // Gridlines at +max/0/min gain.
        canvas.drawLine(left, top, right, top, gridPaint);
        canvas.drawLine(left, bottom, right, bottom, gridPaint);
        float zeroY = bottom - ((0 - minLevel) / (float) Math.max(1, maxLevel - minLevel)) * (bottom - top);
        canvas.drawLine(left, zeroY, right, zeroY, zeroLinePaint);

        if (freqHz.length == 0) {
            return;
        }

        PointF[] points = new PointF[freqHz.length];
        for (int i = 0; i < freqHz.length; i++) {
            points[i] = bandPoint(i, width, height);
        }

        // Smooth curve through band points - a quadratic bezier per segment, control
        // point at the midpoint between consecutive bands, so the line reads as a
        // continuous response curve rather than a jagged connect-the-dots.
        curvePath.reset();
        fillPath.reset();
        curvePath.moveTo(points[0].x, points[0].y);
        fillPath.moveTo(points[0].x, bottom);
        fillPath.lineTo(points[0].x, points[0].y);
        for (int i = 0; i < points.length - 1; i++) {
            float midX = (points[i].x + points[i + 1].x) / 2f;
            float midY = (points[i].y + points[i + 1].y) / 2f;
            curvePath.quadTo(points[i].x, points[i].y, midX, midY);
            curvePath.quadTo(midX, midY, points[i + 1].x, points[i + 1].y);
            fillPath.quadTo(points[i].x, points[i].y, midX, midY);
            fillPath.quadTo(midX, midY, points[i + 1].x, points[i + 1].y);
        }
        fillPath.lineTo(points[points.length - 1].x, bottom);
        fillPath.close();

        canvas.drawPath(fillPath, curveFillPaint);
        canvas.drawPath(curvePath, curveStrokePaint);

        for (int i = 0; i < points.length; i++) {
            boolean active = i == draggingBand;
            float radius = active ? AndroidUtilities.lerp(dotRadius, dotRadiusActive, activeFactor) : dotRadius;
            canvas.drawCircle(points[i].x, points[i].y, radius, dotPaint);
            canvas.drawCircle(points[i].x, points[i].y, radius, dotRingPaint);

            int hz = freqHz[i];
            String label = hz >= 1000 ? (hz / 1000) + "к" : String.valueOf(hz);
            canvas.drawText(label, points[i].x, height - dp(8), labelPaint);

            // Always shown, not just while dragging - the user needs to see how strong
            // (or weak) a band is set to without having to touch it first. The dragged
            // band still gets a bigger/bolder value so it stands out mid-adjustment.
            String value = (levels[i] >= 0 ? "+" : "") + (levels[i] / 100) + " дБ";
            Paint valuePaintForThis = active ? valuePaint : idleValuePaint;
            canvas.drawText(value, points[i].x, Math.max(dp(14), points[i].y - radius - dp(8)), valuePaintForThis);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (freqHz.length == 0) {
            return false;
        }
        if (!graphEnabled) {
            // The equalizer starts switched off, and a dimmed graph that silently ignored the
            // finger read as "the points can't be grabbed". Grabbing one now switches it on.
            if (event.getActionMasked() != MotionEvent.ACTION_DOWN || enableRequest == null) {
                return false;
            }
            enableRequest.requestEnable();
            if (!graphEnabled) {
                return false;
            }
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                int nearest = nearestBand(event.getX());
                if (nearest < 0) {
                    return false;
                }
                draggingBand = nearest;
                startActive(true);
                updateFromTouch(event.getY());
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (draggingBand >= 0) {
                    // Re-asserted every move, not just on down - the screen this view
                    // lives on is a plain ScrollView, and a belt-and-suspenders repeat
                    // here costs nothing if the flag was never actually lost.
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    updateFromTouch(event.getY());
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                startActive(false);
                draggingBand = -1;
                invalidate();
                return true;
            }
        }
        return false;
    }

    private void startActive(boolean active) {
        activeAnim.cancel();
        activeAnim.setFloatValues(activeFactor, active ? 1f : 0f);
        activeAnim.start();
    }

    private int nearestBand(float touchX) {
        int width = getWidth();
        float best = Float.MAX_VALUE;
        int bestIndex = -1;
        for (int i = 0; i < freqHz.length; i++) {
            float x = bandPoint(i, width, getHeight()).x;
            float d = Math.abs(x - touchX);
            if (d < best) {
                best = d;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private void updateFromTouch(float touchY) {
        float top = dp(GRAPH_TOP_PAD_DP);
        float bottom = getHeight() - dp(GRAPH_BOTTOM_PAD_DP);
        float clamped = Math.max(top, Math.min(bottom, touchY));
        float ratio = 1f - (clamped - top) / (bottom - top);
        short level = (short) Math.round(minLevel + ratio * (maxLevel - minLevel));
        levels[draggingBand] = level;
        invalidate();
        if (listener != null) {
            listener.onBandChanged(draggingBand, level);
        }
    }
}
