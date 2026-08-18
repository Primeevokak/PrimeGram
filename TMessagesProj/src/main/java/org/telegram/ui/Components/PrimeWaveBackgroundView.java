package org.telegram.ui.Components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * PrimeGram Music: "Моя волна"'s animated background - not a real fragment shader (this
 * fork has no GLSL/EGL pipeline to build on), but a genuine Canvas animation rather than
 * the static gradient it replaced: 3 soft radial blobs drift on independent slow orbits
 * and the whole palette itself hue-shifts continuously between curated color triads, so
 * both the shapes AND the colors are actually moving, not just a picture that looks like
 * they might.
 */
public class PrimeWaveBackgroundView extends View {

    /** Each row is a 3-color triad (as HSV hue degrees) the palette slowly rotates
     *  through - all warm/glowing families, matching the reference's "sun" look, not a
     *  random hue wheel spin (that would read as gaudy, not "liquid"). */
    private static final float[][] HUE_PALETTES = {
            {350f, 30f, 45f},   // magenta -> orange -> gold (the reference screenshot)
            {180f, 200f, 260f}, // teal -> cyan -> violet
            {20f, 45f, 320f},   // orange -> gold -> pink
    };

    private static final long PALETTE_DURATION_MS = 9000;
    private static final long ORBIT_DURATION_MS = 14000;

    private final Paint blobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint();
    private final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
    private float globalPhase;

    public PrimeWaveBackgroundView(Context context) {
        super(context);
        bgPaint.setColor(0xFF0A0A0E);
        blobPaint.setStyle(Paint.Style.FILL);
        animator.setDuration(ORBIT_DURATION_MS);
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(a -> {
            globalPhase = (float) a.getAnimatedValue();
            invalidate();
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        animator.cancel();
    }

    private static int hsv(float hue, float sat, float value, int alpha) {
        return Color.HSVToColor(alpha, new float[]{hue % 360f, sat, value});
    }

    /** Interpolates hue between the current palette and the next one over
     *  {@link #PALETTE_DURATION_MS}, driven off wall-clock time (not the orbit animator,
     *  a different period) so the color rotation and the shape motion are visibly
     *  independent rhythms, not locked to the same loop. */
    private float[] currentHues() {
        long now = android.os.SystemClock.uptimeMillis();
        long cycle = now % (PALETTE_DURATION_MS * HUE_PALETTES.length);
        int index = (int) (cycle / PALETTE_DURATION_MS);
        float t = (cycle % PALETTE_DURATION_MS) / (float) PALETTE_DURATION_MS;
        float[] from = HUE_PALETTES[index];
        float[] to = HUE_PALETTES[(index + 1) % HUE_PALETTES.length];
        float[] result = new float[3];
        for (int i = 0; i < 3; i++) {
            result[i] = lerpHue(from[i], to[i], t);
        }
        return result;
    }

    private static float lerpHue(float a, float b, float t) {
        float diff = ((b - a + 540f) % 360f) - 180f;
        return (a + diff * t + 360f) % 360f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        canvas.drawRect(0, 0, w, h, bgPaint);
        if (w == 0 || h == 0) {
            return;
        }

        float[] hues = currentHues();
        float maxRadius = Math.max(w, h) * 0.62f;

        drawBlob(canvas, hues[0], w * 0.5f, h * 0.28f, maxRadius, 0f);
        drawBlob(canvas, hues[1], w * 0.5f, h * 0.5f, maxRadius * 0.85f, 0.33f);
        drawBlob(canvas, hues[2], w * 0.5f, h * 0.72f, maxRadius * 0.75f, 0.66f);
    }

    /** One drifting glow - orbits a small ellipse around its own anchor point so it
     *  never leaves the visible area, phase-offset per blob so all three don't move in
     *  lockstep. */
    private void drawBlob(Canvas canvas, float hue, float anchorX, float anchorY, float radius, float phaseOffset) {
        float phase = (globalPhase + phaseOffset) % 1f;
        double angle = phase * Math.PI * 2;
        float driftX = (float) Math.cos(angle) * radius * 0.22f;
        float driftY = (float) Math.sin(angle * 1.3) * radius * 0.16f;
        float cx = anchorX + driftX;
        float cy = anchorY + driftY;

        blobPaint.setShader(new RadialGradient(cx, cy, radius,
                new int[]{hsv(hue, 0.55f, 1f, 235), hsv(hue, 0.7f, 0.9f, 90), hsv(hue, 0.8f, 0.7f, 0)},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, blobPaint);
    }
}
