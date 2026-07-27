package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.PrimeTweaks;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SeekBarView;

/**
 * PrimeGram: the avatar shape control - a continuous slider from square to circle with a live
 * preview of what it is doing.
 *
 * <p>It started as a list of six presets, which is the wrong shape of control for this: the value
 * is a number on a range, and a range wants a slider. Dragging changes the shape everywhere in the
 * app immediately, because the value is applied in memory as it moves; it is only written to disk
 * when the finger comes off, so a drag costs one preference write rather than sixty.
 */
public class PrimeAvatarCornersCell extends FrameLayout {

    private final SeekBarView seekBar;
    private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint valuePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint previewPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private final String title;
    private final Runnable onCommitted;

    /** Sizes of the sample avatars, chosen to match the real ones: chat list, chat header, reply. */
    private static final int[] PREVIEW_SIZES_DP = {48, 36, 28, 20};

    public PrimeAvatarCornersCell(Context context, String title, Runnable onCommitted) {
        super(context);
        this.title = title;
        this.onCommitted = onCommitted;
        setWillNotDraw(false);

        titlePaint.setTextSize(AndroidUtilities.dp(16));
        valuePaint.setTextSize(AndroidUtilities.dp(14));
        valuePaint.setTypeface(AndroidUtilities.bold());
        valuePaint.setTextAlign(Paint.Align.RIGHT);

        seekBar = new SeekBarView(context);
        seekBar.setReportChanges(true);
        seekBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
            @Override
            public void onSeekBarDrag(boolean stop, float progress) {
                final int value = Math.round(progress * PrimeTweaks.AVATAR_CORNERS_MAX);
                // in memory first: every avatar on screen picks this up on its next frame
                PrimeTweaks.setAvatarCornersLive(value);
                invalidate();
                // every avatar in the window reshapes as the finger moves, not only afterwards
                View root = getRootView();
                if (root != null) {
                    AndroidUtilities.forEachViews(root, View::invalidate);
                }
                if (stop) {
                    PrimeTweaks.setInt(PrimeTweaks.AVATAR_CORNERS, value);
                    if (onCommitted != null) {
                        onCommitted.run();
                    }
                }
            }

            @Override
            public CharSequence getContentDescription() {
                return percentText();
            }
        });
        addView(seekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.TOP | Gravity.LEFT, 6, 78, 6, 0));

        updateFromSettings();
    }

    /** Re-reads the stored value; call after something else changed it. */
    public void updateFromSettings() {
        seekBar.setProgress(PrimeTweaks.avatarCorners() / (float) PrimeTweaks.AVATAR_CORNERS_MAX);
        invalidate();
    }

    private String percentText() {
        int percent = Math.round(PrimeTweaks.avatarCorners() * 100f / PrimeTweaks.AVATAR_CORNERS_MAX);
        if (percent >= 100) {
            return "круг";
        }
        if (percent <= 0) {
            return "квадрат";
        }
        return percent + "%";
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(128), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final boolean rtl = org.telegram.messenger.LocaleController.isRTL;
        titlePaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        valuePaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));

        final int padding = AndroidUtilities.dp(21);
        canvas.drawText(title, padding, AndroidUtilities.dp(26), titlePaint);
        canvas.drawText(percentText(), getMeasuredWidth() - padding, AndroidUtilities.dp(26), valuePaint);

        // The preview shows several sizes at once on purpose: the corner is a proportion, so a
        // value that looks right on a big avatar can look square on a small one, and the only
        // honest way to show that is to show both.
        int x = rtl ? getMeasuredWidth() - padding : padding;
        final int baseline = AndroidUtilities.dp(42);
        for (int i = 0; i < PREVIEW_SIZES_DP.length; i++) {
            final int size = AndroidUtilities.dp(PREVIEW_SIZES_DP[i]);
            final int radius = PrimeTweaks.avatarRadius(size / 2);
            previewPaint.setColor(Theme.getColor(Theme.keys_avatar_background[
                    Utilities.clamp(i, Theme.keys_avatar_background.length - 1, 0)]));
            if (rtl) {
                x -= size;
            }
            rect.set(x, baseline, x + size, baseline + size);
            if (radius * 2 >= size) {
                canvas.drawCircle(rect.centerX(), rect.centerY(), size / 2f, previewPaint);
            } else {
                canvas.drawRoundRect(rect, radius, radius, previewPaint);
            }
            if (rtl) {
                x -= AndroidUtilities.dp(10);
            } else {
                x += size + AndroidUtilities.dp(10);
            }
        }
    }
}
