package org.telegram.ui.Components.primeui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

/**
 * PrimeGram settings: the link to the author's channel - a card of its own rather than one more
 * grey row, since it is the one place in settings that leads outside the app on purpose.
 *
 * <p>Motion is kept quiet and cheap: a light band sweeps across the card for ~1.1 s every ~4 s,
 * the plane in the badge drifts on a slow sine, and a press sinks the card a little. Between
 * sweeps nothing invalidates on every frame - one delayed invalidate wakes the next sweep. All of it
 * stops off screen and with animations off (LiteMode).
 */
public class PrimeChannelRow extends PrimeCardRow {

    private static final long SWEEP_MS = 1100;
    private static final long PAUSE_MS = 2900;
    private static final int HEIGHT = 76;
    private static final int BADGE = 44;

    private static final int COLOR_START = 0xFF2AABEE;
    private static final int COLOR_END = 0xFF7B5CF5;

    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint titlePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final TextPaint subtitlePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final TextPaint pillTextPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final LinearGradient shine;
    private final Matrix shineMatrix = new Matrix();
    private final RectF box = new RectF();
    private final RectF pill = new RectF();
    private final Drawable plane;

    private CharSequence title, subtitle;
    private String shownTitle, shownSubtitle;
    private int laidOutWidth = -1;
    private View.OnClickListener onClick;

    private final long startTime = SystemClock.elapsedRealtime();
    private float pressProgress;
    private long pressAnimLast;

    private final Runnable wake = this::invalidate;

    public PrimeChannelRow(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
        setPosition(POS_NONE);
        titlePaint.setTextSize(dp(16));
        titlePaint.setTypeface(AndroidUtilities.bold());
        titlePaint.setColor(0xFFFFFFFF);
        subtitlePaint.setTextSize(dp(13));
        subtitlePaint.setColor(0xD9FFFFFF);
        pillTextPaint.setTextSize(dp(13));
        pillTextPaint.setTypeface(AndroidUtilities.bold());
        pillTextPaint.setColor(COLOR_START);
        pillPaint.setColor(0xFFFFFFFF);
        badgePaint.setColor(0x33FFFFFF);
        shine = new LinearGradient(0, 0, dp(90), 0,
                new int[]{0x00FFFFFF, 0x40FFFFFF, 0x00FFFFFF}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        shinePaint.setShader(shine);
        final Drawable d = ContextCompat.getDrawable(context, R.drawable.msg_send);
        plane = d == null ? null : d.mutate();
        if (plane != null) {
            plane.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
        }
    }

    public void set(CharSequence title, CharSequence subtitle, View.OnClickListener onClick) {
        final boolean changed = !TextUtils.equals(this.title, title) || !TextUtils.equals(this.subtitle, subtitle);
        this.title = title;
        this.subtitle = subtitle;
        this.onClick = onClick;
        if (changed) {
            laidOutWidth = -1;
            requestLayout();
        }
        invalidate();
    }

    private static boolean animationsOn() {
        return LiteMode.isEnabled(LiteMode.FLAGS_ANIMATED_STICKERS);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width != laidOutWidth) {
            laidOutWidth = width;
            final float textWidth = Math.max(dp(40), width - dp(CARD_MARGIN) * 2 - dp(16) * 3 - dp(BADGE) - pillWidth() - dp(10));
            shownTitle = title == null ? "" : TextUtils.ellipsize(title, titlePaint, textWidth, TextUtils.TruncateAt.END).toString();
            shownSubtitle = subtitle == null ? "" : TextUtils.ellipsize(subtitle, subtitlePaint, textWidth, TextUtils.TruncateAt.END).toString();
        }
        setMeasuredDimension(width, dp(HEIGHT) + dp(8));
    }

    private float pillWidth() {
        return pillTextPaint.measureText("Открыть") + dp(14) * 2;
    }

    @Override
    protected void onPressedChanged(int target) {
        pressAnimLast = SystemClock.elapsedRealtime();
        invalidate();
    }

    @Override
    protected void drawContent(Canvas canvas) {
        final long now = SystemClock.elapsedRealtime();
        final boolean animate = animationsOn();

        // press: ease towards the target over ~150 ms
        final float pressTarget = pressedTarget == 0 ? 1f : 0f;
        if (pressProgress != pressTarget) {
            final float step = Math.min(1f, (now - pressAnimLast) / 150f);
            pressAnimLast = now;
            pressProgress = pressTarget > pressProgress ? Math.min(pressTarget, pressProgress + step) : Math.max(pressTarget, pressProgress - step);
        }
        boolean needFrame = pressProgress != pressTarget;

        box.set(dp(CARD_MARGIN), 0, getMeasuredWidth() - dp(CARD_MARGIN), getMeasuredHeight() - dp(8));
        final float scale = 1f - 0.03f * CubicBezierInterpolator.EASE_OUT.getInterpolation(pressProgress);
        canvas.save();
        canvas.scale(scale, scale, box.centerX(), box.centerY());

        if (cardPaint.getShader() == null || laidOutShaderWidth != box.width()) {
            laidOutShaderWidth = box.width();
            cardPaint.setShader(new LinearGradient(box.left, box.top, box.right, box.bottom,
                    COLOR_START, COLOR_END, Shader.TileMode.CLAMP));
        }
        final float r = radius(org.telegram.ui.Components.design.DesignSystem.Role.LIST_CARD);
        canvas.drawRoundRect(box, r, r, cardPaint);

        // sweep
        if (animate) {
            final long cycle = (now - startTime) % (SWEEP_MS + PAUSE_MS);
            if (cycle < SWEEP_MS) {
                final float t = CubicBezierInterpolator.EASE_BOTH.getInterpolation(cycle / (float) SWEEP_MS);
                final float x = box.left - dp(90) + (box.width() + dp(180)) * t;
                shineMatrix.reset();
                shineMatrix.setTranslate(x, 0);
                shineMatrix.postSkew(-0.35f, 0, x, box.centerY());
                shine.setLocalMatrix(shineMatrix);
                canvas.drawRoundRect(box, r, r, shinePaint);
                needFrame = true;
            } else if (!needFrame && isAttachedToWindow()) {
                removeCallbacks(wake);
                postDelayed(wake, SWEEP_MS + PAUSE_MS - cycle);
            }
        }

        // badge with the plane
        final float badgeCx = rtl() ? box.right - dp(16) - dp(BADGE) / 2f : box.left + dp(16) + dp(BADGE) / 2f;
        final float cy = box.centerY();
        canvas.drawCircle(badgeCx, cy, dp(BADGE) / 2f, badgePaint);
        if (plane != null) {
            // One full sine per sweep, so it starts and ends at rest and never jumps between sweeps.
            final long cycle = (now - startTime) % (SWEEP_MS + PAUSE_MS);
            final float drift = animate && cycle < SWEEP_MS ? (float) Math.sin(cycle / (double) SWEEP_MS * Math.PI * 2) * dp(2) : 0f;
            final int s = dp(24);
            plane.setBounds((int) (badgeCx - s / 2f + drift), (int) (cy - s / 2f - drift), (int) (badgeCx + s / 2f + drift), (int) (cy + s / 2f - drift));
            plane.draw(canvas);
            if (animate) {
                needFrame |= cycleIsSweeping(now);
            }
        }

        // texts
        final float textLeft = rtl() ? box.left + dp(16) + pillWidth() + dp(10) : badgeCx + dp(BADGE) / 2f + dp(14);
        canvas.drawText(shownTitle == null ? "" : shownTitle, textLeft, cy - dp(3), titlePaint);
        canvas.drawText(shownSubtitle == null ? "" : shownSubtitle, textLeft, cy + dp(16), subtitlePaint);

        // "open" pill
        final float pw = pillWidth();
        final float pl = rtl() ? box.left + dp(14) : box.right - dp(14) - pw;
        pill.set(pl, cy - dp(15), pl + pw, cy + dp(15));
        canvas.drawRoundRect(pill, dp(15), dp(15), pillPaint);
        canvas.drawText("Открыть", pill.left + dp(14), cy + dp(4.5f), pillTextPaint);

        canvas.restore();

        if (needFrame) {
            postInvalidateOnAnimation();
        }
    }

    private float laidOutShaderWidth = -1;

    private boolean cycleIsSweeping(long now) {
        return (now - startTime) % (SWEEP_MS + PAUSE_MS) < SWEEP_MS;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(wake);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        invalidate();
    }

    @Override
    protected int hitTest(float x, float y) {
        return onClick != null && box.contains(x, y) ? 0 : -1;
    }

    @Override
    protected void onTargetClick(int target, boolean longPress) {
        if (onClick != null) {
            onClick.onClick(this);
        }
    }

    public static class Factory extends UItem.UItemFactory<PrimeChannelRow> {
        static { setup(new Factory()); }

        @Override
        public PrimeChannelRow createView(Context context, RecyclerListView listView, int currentAccount,
                                          int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new PrimeChannelRow(context, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            ((PrimeChannelRow) view).set(item.text, item.textValue, item.clickCallback);
        }

        @Override
        public boolean isClickable() {
            return true;
        }

        @Override
        public boolean equals(UItem a, UItem b) {
            return a.id == b.id;
        }

        @Override
        public boolean contentsEquals(UItem a, UItem b) {
            return a.id == b.id && TextUtils.equals(a.text, b.text) && TextUtils.equals(a.textValue, b.textValue);
        }

        public static UItem of(int id, CharSequence title, CharSequence subtitle, View.OnClickListener onClick) {
            final UItem item = UItem.ofFactory(Factory.class);
            item.id = id;
            item.text = title;
            item.textValue = subtitle;
            item.clickCallback = onClick;
            return item;
        }
    }
}
