package org.telegram.ui.Components.primeui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.Arrays;

/**
 * PrimeGram settings: pick from a set - chips that wrap onto as many lines as they need.
 *
 * <p>For choices with more options than fit a segmented row (folders, languages, reactions), and
 * for choices where several can be on at once. Selection is a bitmask in {@link UItem#longValue},
 * so a row supports up to 64 chips - far more than a settings screen should ever show.
 */
public class PrimeChipsRow extends PrimeCardRow {

    private static final int CHIP_HEIGHT = 34;
    private static final int CHIP_PAD = 14;
    private static final int GAP = 8;

    private static TextPaint titlePaint, chipPaint;
    private static Paint strokePaint;

    private CharSequence title;
    private String shownTitle;
    private String[] labels = new String[0];
    private String[] shownLabels = new String[0];
    private float[] labelWidths = new float[0];
    private RectF[] chips = new RectF[0];
    private AnimatedFloat[] selection = new AnimatedFloat[0];
    private long selectedMask;
    private Utilities.Callback<Integer> listener;
    private int laidOutWidth = -1;
    private int contentHeight;

    public PrimeChipsRow(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
        if (titlePaint == null) {
            titlePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            titlePaint.setTextSize(dp(15));
            chipPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            chipPaint.setTextSize(dp(14));
            chipPaint.setTypeface(AndroidUtilities.bold());
            strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(Math.max(1, dp(1)));
        }
    }

    public void set(CharSequence title, String[] labels, long selectedMask, Utilities.Callback<Integer> listener, boolean animated) {
        this.listener = listener;
        final String[] newLabels = labels == null ? new String[0] : labels;
        final boolean labelsChanged = !Arrays.equals(this.labels, newLabels);
        final boolean titleChanged = !TextUtils.equals(this.title, title);
        this.title = title;
        this.labels = newLabels;
        if (labelsChanged) {
            chips = new RectF[newLabels.length];
            selection = new AnimatedFloat[newLabels.length];
            for (int i = 0; i < newLabels.length; i++) {
                chips[i] = new RectF();
                selection[i] = new AnimatedFloat(this, 0, 220, CubicBezierInterpolator.EASE_OUT_QUINT);
            }
            shownLabels = new String[newLabels.length];
            labelWidths = new float[newLabels.length];
            animated = false;
        }
        this.selectedMask = selectedMask;
        for (int i = 0; i < selection.length; i++) {
            selection[i].set(isSelected(i) ? 1f : 0f, !animated);
        }
        if (labelsChanged || titleChanged) {
            laidOutWidth = -1;
            requestLayout();
        }
        invalidate();
    }

    private boolean isSelected(int i) {
        return i < 64 && (selectedMask & (1L << i)) != 0;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width != laidOutWidth) {
            layoutChips(width);
        }
        setMeasuredDimension(width, contentHeight);
    }

    /** Flows the chips into lines for this width. Only on a width or label change, never per frame. */
    private void layoutChips(int width) {
        laidOutWidth = width;
        final float left = dp(CARD_MARGIN + CONTENT_INSET);
        final float right = width - dp(CARD_MARGIN + CONTENT_INSET);
        float y = dp(14);
        if (!TextUtils.isEmpty(title)) {
            shownTitle = TextUtils.ellipsize(title, titlePaint, right - left, TextUtils.TruncateAt.END).toString();
            y += dp(20) + dp(10);
        } else {
            shownTitle = null;
        }
        final float maxChip = right - left;
        float x = left;
        for (int i = 0; i < labels.length; i++) {
            shownLabels[i] = TextUtils.ellipsize(labels[i], chipPaint, maxChip - dp(CHIP_PAD) * 2, TextUtils.TruncateAt.END).toString();
            labelWidths[i] = chipPaint.measureText(shownLabels[i]);
            final float w = labelWidths[i] + dp(CHIP_PAD) * 2;
            if (x > left && x + w > right) {
                x = left;
                y += dp(CHIP_HEIGHT) + dp(GAP);
            }
            chips[i].set(x, y, x + w, y + dp(CHIP_HEIGHT));
            x += w + dp(GAP);
        }
        if (rtl()) {
            // Mirror horizontally: lines fill from the right, in reading order.
            for (RectF r : chips) {
                final float l = width - r.right, rr = width - r.left;
                r.left = l;
                r.right = rr;
            }
        }
        contentHeight = (int) (labels.length > 0 ? y + dp(CHIP_HEIGHT) : y) + dp(14);
    }

    @Override
    protected void drawContent(Canvas canvas) {
        if (shownTitle != null) {
            titlePaint.setColor(color(Theme.key_windowBackgroundWhiteBlackText));
            final float x = rtl() ? contentRight() - titlePaint.measureText(shownTitle) : contentLeft();
            canvas.drawText(shownTitle, x, dp(14) + dp(15), titlePaint);
        }
        final int accent = accentColor();
        final int onAccent = onAccentColor();
        final int textColor = color(Theme.key_windowBackgroundWhiteBlackText);
        final int outline = Theme.multAlpha(color(Theme.key_windowBackgroundWhiteGrayText2), 0.35f);
        final float radius = dp(CHIP_HEIGHT) / 2f;
        for (int i = 0; i < labels.length; i++) {
            final RectF r = chips[i];
            final float s = selection[i].set(isSelected(i) ? 1f : 0f);
            final boolean pressed = pressedTarget == i;
            if (pressed) {
                canvas.save();
                canvas.scale(0.95f, 0.95f, r.centerX(), r.centerY());
            }
            if (s < 1f) {
                fillPaint.setColor(Theme.multAlpha(accent, pressed ? 0.12f : 0.0f));
                if (pressed) {
                    canvas.drawRoundRect(r, radius, radius, fillPaint);
                }
                strokePaint.setColor(Theme.multAlpha(outline, 1f - s));
                canvas.drawRoundRect(r.left + 0.5f, r.top + 0.5f, r.right - 0.5f, r.bottom - 0.5f, radius, radius, strokePaint);
            }
            if (s > 0f) {
                fillPaint.setColor(Theme.multAlpha(accent, s));
                canvas.drawRoundRect(r, radius, radius, fillPaint);
            }
            chipPaint.setColor(ColorUtils.blendARGB(textColor, onAccent, s));
            final float baseline = r.centerY() - (chipPaint.descent() + chipPaint.ascent()) / 2f;
            canvas.drawText(shownLabels[i], r.centerX() - labelWidths[i] / 2f, baseline, chipPaint);
            if (pressed) {
                canvas.restore();
            }
        }
    }

    @Override
    protected int hitTest(float x, float y) {
        for (int i = 0; i < chips.length; i++) {
            final RectF r = chips[i];
            if (x >= r.left - dp(3) && x <= r.right + dp(3) && y >= r.top - dp(3) && y <= r.bottom + dp(3)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected void onTargetClick(int target, boolean longPress) {
        if (listener != null) {
            listener.run(target);
        }
    }

    public static class Factory extends UItem.UItemFactory<PrimeChipsRow> {
        static { setup(new Factory()); }

        @Override
        public PrimeChipsRow createView(Context context, RecyclerListView listView, int currentAccount,
                                        int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new PrimeChipsRow(context, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            final PrimeChipsRow row = (PrimeChipsRow) view;
            final boolean same = row.getTag() instanceof Integer && (Integer) row.getTag() == item.id;
            row.setTag(item.id);
            row.setPosition(item.flags & POS_MASK);
            row.set(item.text, item.texts, item.longValue, item.intCallback, same);
        }

        @Override
        public boolean isClickable() {
            return false;
        }

        @Override
        public boolean equals(UItem a, UItem b) {
            return a.id == b.id;
        }

        @Override
        public boolean contentsEquals(UItem a, UItem b) {
            return a.id == b.id && a.longValue == b.longValue && a.flags == b.flags
                    && TextUtils.equals(a.text, b.text) && Arrays.equals(a.texts, b.texts);
        }

        /**
         * @param selectedMask bit i set = chip i selected
         * @param onTap called with the tapped chip's index; the caller decides single or multi choice
         */
        public static UItem of(int id, CharSequence title, String[] labels, long selectedMask, Utilities.Callback<Integer> onTap) {
            final UItem item = UItem.ofFactory(Factory.class);
            item.id = id;
            item.text = title;
            item.texts = labels;
            item.longValue = selectedMask;
            item.intCallback = onTap;
            return item;
        }
    }
}
