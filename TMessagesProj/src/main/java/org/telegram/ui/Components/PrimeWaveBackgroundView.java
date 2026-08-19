package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

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
 * PrimeGram Music: "Моя волна" - high-fidelity procedural liquid gradient background.
 * Uses 5 harmonic orbital nodes with multi-frequency Lissajous paths and dynamic
 * palette interpolation to create an organic, living "neural glow" on pure Canvas
 * without requiring OpenGL/GLSL pipeline dependencies.
 */
public class PrimeWaveBackgroundView extends View {

    /** Curated 4-color palettes (HSV hues: primary, secondary, accent, deep glow) */
    private static final float[][] HUE_PALETTES = {
            {338f, 24f, 48f, 280f},  // Solar Plasma: Magenta -> Electric Orange -> Amber Gold -> Deep Violet
            {165f, 195f, 235f, 280f}, // Deep Aurora: Emerald -> Cyan -> Ultramarine -> Purple
            {310f, 260f, 210f, 345f}, // Cyber Vibe: Neon Fuchsia -> Indigo -> Electric Blue -> Hot Pink
            {15f, 350f, 290f, 45f}    // Sunset Glow: Peach Orange -> Crimson -> Deep Plum -> Warm Gold
    };

    private static final long PALETTE_DURATION_MS = 10000;
    private static final long ORBIT_DURATION_MS = 16000;

    private final Paint blobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint();
    private final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);

    private float globalPhase;
    private boolean isPlaying = true;
    private float pulsePhase = 0f;

    public PrimeWaveBackgroundView(Context context) {
        super(context);
        bgPaint.setColor(0xFF0C0C14);
        blobPaint.setStyle(Paint.Style.FILL);

        animator.setDuration(ORBIT_DURATION_MS);
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(a -> {
            globalPhase = (float) a.getAnimatedValue();
            if (isPlaying) {
                pulsePhase = (pulsePhase + 0.025f) % 1f;
            }
            invalidate();
        });
    }

    public void setPlaying(boolean playing) {
        this.isPlaying = playing;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!animator.isRunning()) {
            animator.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        animator.cancel();
    }

    private static int hsv(float hue, float sat, float value, int alpha) {
        return Color.HSVToColor(alpha, new float[]{(hue % 360f + 360f) % 360f, sat, value});
    }

    private float[] currentHues() {
        long now = android.os.SystemClock.uptimeMillis();
        long cycle = now % (PALETTE_DURATION_MS * HUE_PALETTES.length);
        int index = (int) (cycle / PALETTE_DURATION_MS);
        float t = (cycle % PALETTE_DURATION_MS) / (float) PALETTE_DURATION_MS;
        // Smooth hermite interpolation for color transitions
        float smoothT = t * t * (3f - 2f * t);

        float[] from = HUE_PALETTES[index];
        float[] to = HUE_PALETTES[(index + 1) % HUE_PALETTES.length];
        float[] result = new float[4];
        for (int i = 0; i < 4; i++) {
            result[i] = lerpHue(from[i], to[i], smoothT);
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
        if (w == 0 || h == 0) {
            return;
        }

        canvas.drawRect(0, 0, w, h, bgPaint);

        float[] hues = currentHues();
        float baseRadius = Math.max(w, h) * 0.72f;

        // Breathing pulse calculation
        float pulse = isPlaying ? (float) (Math.sin(pulsePhase * Math.PI * 2) * 0.06f) : 0f;
        float dynamicRadius = baseRadius * (1f + pulse);

        // 1. Central radiant energy core
        drawFluidBlob(canvas, hues[0], w * 0.5f, h * 0.5f, dynamicRadius * 0.95f, 0f, 1.0f, 1.2f, 240);

        // 2. Upper flowing orbital node
        drawFluidBlob(canvas, hues[1], w * 0.35f, h * 0.30f, dynamicRadius * 0.78f, 0.22f, 1.7f, 2.1f, 210);

        // 3. Right harmonic orbital node
        drawFluidBlob(canvas, hues[2], w * 0.68f, h * 0.45f, dynamicRadius * 0.82f, 0.48f, 1.3f, 1.9f, 200);

        // 4. Lower deep accent node
        drawFluidBlob(canvas, hues[3], w * 0.45f, h * 0.72f, dynamicRadius * 0.74f, 0.73f, 2.3f, 1.5f, 190);

        // 5. Ambient secondary glow for extra liquid dispersion
        drawFluidBlob(canvas, (hues[0] + 30f) % 360f, w * 0.55f, h * 0.58f, dynamicRadius * 0.60f, 0.88f, 0.9f, 1.4f, 160);
    }

    private void drawFluidBlob(Canvas canvas, float hue, float anchorX, float anchorY, float radius,
                               float phaseOffset, float freqX, float freqY, int maxAlpha) {
        float phase = (globalPhase + phaseOffset) % 1f;
        double angle = phase * Math.PI * 2;

        float driftX = (float) Math.sin(angle * freqX) * radius * 0.26f;
        float driftY = (float) Math.cos(angle * freqY) * radius * 0.20f;
        float cx = anchorX + driftX;
        float cy = anchorY + driftY;

        int c1 = hsv(hue, 0.68f, 1.0f, maxAlpha);
        int c2 = hsv(hue, 0.80f, 0.85f, (int) (maxAlpha * 0.55f));
        int c3 = hsv((hue + 20f) % 360f, 0.90f, 0.60f, (int) (maxAlpha * 0.18f));
        int c4 = 0x00000000;

        blobPaint.setShader(new RadialGradient(cx, cy, radius,
                new int[]{c1, c2, c3, c4},
                new float[]{0f, 0.35f, 0.70f, 1.0f}, Shader.TileMode.CLAMP));

        canvas.drawCircle(cx, cy, radius, blobPaint);
    }
}
