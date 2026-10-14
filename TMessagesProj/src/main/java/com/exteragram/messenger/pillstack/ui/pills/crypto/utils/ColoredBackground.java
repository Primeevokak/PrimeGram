package com.exteragram.messenger.pillstack.ui.pills.crypto.utils;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * exteraGram API: the rounded vertical-gradient plate behind a pill's icon (plugins reuse it for
 * their own icons). In dark themes a faint top-lit stroke outlines it, as in exteraGram.
 */
public class ColoredBackground extends Drawable {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ColoredBackground() {
        this(0xFF1BA4ED, 0xFF1488E1);
    }

    public ColoredBackground(int colorTop, int colorBottom) {
        paint.setShader(new LinearGradient(0, 0, 0, AndroidUtilities.dp(28), new int[]{colorTop, colorBottom}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(AndroidUtilities.dp(1));
        strokePaint.setShader(new LinearGradient(0, 0, 0, AndroidUtilities.dp(28), new int[]{0x4DFFFFFF, 0, 0x1AFFFFFF}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final float radius = AndroidUtilities.dp(14);
        final RectF rect = AndroidUtilities.rectTmp;
        rect.set(getBounds());
        canvas.drawRoundRect(rect, radius, radius, paint);
        if (!Theme.isCurrentThemeDark() || Theme.isCurrentThemeMonet()) {
            return;
        }
        final float stroke = AndroidUtilities.dp(1);
        strokePaint.setStrokeWidth(stroke);
        rect.inset(stroke / 2f, stroke / 2f);
        canvas.drawRoundRect(rect, radius, radius, strokePaint);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        strokePaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        strokePaint.setColorFilter(colorFilter);
    }
}
