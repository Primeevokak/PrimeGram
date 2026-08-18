package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/**
 * PrimeGram Music: a clean, actually-antialiased "не нравится" glyph - a plain circle
 * with a diagonal slash, the universal "not this" symbol. Replaces an earlier attempt
 * that composited the like heart icon with a separately rotated line View on top of it
 * (misaligned, jagged edges where the two layers met) - drawn as one real Canvas path
 * instead, so there's nothing to misalign.
 */
public class PrimeDislikeIconView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public PrimeDislikeIconView(Context context) {
        super(context);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.8f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xCCFFFFFF);
    }

    public void setIconColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(getWidth(), getHeight()) / 2f - dp(4);
        canvas.drawCircle(cx, cy, radius, paint);
        double angle = Math.toRadians(45);
        float dx = (float) (Math.cos(angle) * radius);
        float dy = (float) (Math.sin(angle) * radius);
        canvas.drawLine(cx - dx, cy + dy, cx + dx, cy - dy, paint);
    }
}
