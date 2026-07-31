package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.TgWsProxyService;
import org.telegram.ui.ActionBar.Theme;

/**
 * PrimeGram: the state of the tunnel, as one card at the top of its settings screen.
 *
 * <p>A switch tells you what you asked for; this tells you what is actually happening. The three
 * facts that matter - is it up, which of the ten domains is carrying the traffic, which port it
 * ended up on - are exactly the three that used to be invisible, findable only by reading a log
 * that had no screen.
 *
 * <p>The ring pulses only while the server is running and the view is attached. An idle animation
 * on a settings screen is a battery cost with nothing to say, and a stopped server has nothing to
 * say.
 */
public class PrimeTgWsStatusCell extends View {

    private final Theme.ResourcesProvider resourcesProvider;

    private final Paint discPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boltPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint subtitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint detailPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private final RectF rect = new RectF();
    private final android.graphics.Path bolt = new android.graphics.Path();

    private long pulseStart;
    private boolean running;

    public PrimeTgWsStatusCell(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        titlePaint.setTextSize(AndroidUtilities.dp(17));
        titlePaint.setTypeface(AndroidUtilities.bold());
        subtitlePaint.setTextSize(AndroidUtilities.dp(14));
        detailPaint.setTextSize(AndroidUtilities.dp(13));

        ringPaint.setStyle(Paint.Style.STROKE);
        boltPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(112));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        pulseStart = System.currentTimeMillis();
        invalidate();
    }

    private int color(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        running = TgWsProxyService.isRunning() && TgWsProxyService.isSocketBound;

        final int padding = AndroidUtilities.dp(16);
        rect.set(padding, AndroidUtilities.dp(4), getWidth() - padding, getHeight() - AndroidUtilities.dp(8));
        cardPaint.setColor(color(Theme.key_windowBackgroundWhite));
        canvas.drawRoundRect(rect, AndroidUtilities.dp(14), AndroidUtilities.dp(14), cardPaint);

        final float cx = rect.left + AndroidUtilities.dp(44);
        final float cy = rect.centerY();
        final float radius = AndroidUtilities.dp(26);

        final int top = running ? 0xFF52C755 : 0xFF9AA5AF;
        final int bottom = running ? 0xFF2FA35C : 0xFF7A8791;
        discPaint.setShader(new LinearGradient(cx, cy - radius, cx, cy + radius, top, bottom, Shader.TileMode.CLAMP));

        if (running) {
            // One ring at a time, expanding and fading. Two would read as a loading spinner; this
            // has to read as "alive", which is a slower and quieter thing.
            final float phase = ((System.currentTimeMillis() - pulseStart) % 2200L) / 2200f;
            final float ringRadius = radius + AndroidUtilities.dp(20) * phase;
            ringPaint.setStrokeWidth(AndroidUtilities.dp(2));
            ringPaint.setColor(top);
            ringPaint.setAlpha((int) (90 * (1f - phase)));
            canvas.drawCircle(cx, cy, ringRadius, ringPaint);
            invalidate();
        }

        canvas.drawCircle(cx, cy, radius, discPaint);

        // A lightning bolt, drawn rather than loaded: it is six points, and a drawable for it would
        // be one more asset to keep in step with the colours around it.
        final float unit = radius / 3.2f;
        bolt.reset();
        bolt.moveTo(cx + unit * 0.35f, cy - unit * 1.7f);
        bolt.lineTo(cx - unit * 1.05f, cy + unit * 0.25f);
        bolt.lineTo(cx - unit * 0.1f, cy + unit * 0.25f);
        bolt.lineTo(cx - unit * 0.45f, cy + unit * 1.7f);
        bolt.lineTo(cx + unit * 1.05f, cy - unit * 0.35f);
        bolt.lineTo(cx + unit * 0.1f, cy - unit * 0.35f);
        bolt.close();
        boltPaint.setColor(0xFFFFFFFF);
        canvas.drawPath(bolt, boltPaint);

        final float textLeft = cx + radius + AndroidUtilities.dp(18);
        final float textRight = rect.right - AndroidUtilities.dp(14);
        final int available = (int) Math.max(0, textRight - textLeft);

        titlePaint.setColor(color(Theme.key_windowBackgroundWhiteBlackText));
        canvas.drawText(running ? "Сервер работает" : "Сервер остановлен",
                textLeft, cy - AndroidUtilities.dp(12), titlePaint);

        subtitlePaint.setColor(color(running
                ? Theme.key_windowBackgroundWhiteGrayText2 : Theme.key_windowBackgroundWhiteGrayText));
        final String domain = TgWsProxyService.currentDomain();
        final String line = running
                ? (domain != null ? domain : "выбирается домен…")
                : "Включите, чтобы пустить трафик через туннель";
        canvas.drawText(TextUtils.ellipsize(line, subtitlePaint, available, TextUtils.TruncateAt.END).toString(),
                textLeft, cy + AndroidUtilities.dp(7), subtitlePaint);

        if (running) {
            detailPaint.setColor(color(Theme.key_windowBackgroundWhiteGrayText));
            canvas.drawText("127.0.0.1 : " + TgWsProxyService.activeProxyPort,
                    textLeft, cy + AndroidUtilities.dp(26), detailPaint);
        }
    }
}
