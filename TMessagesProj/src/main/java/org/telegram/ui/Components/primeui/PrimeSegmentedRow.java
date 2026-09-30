package org.telegram.ui.Components.primeui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
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
 * PrimeGram settings: choose one of a few - a connected button group.
 *
 * <p>Replaces "tap a row, get a dialog of radio buttons" for short choices: every option is on
 * screen, the current one is obvious, and changing it is one tap with no dialog in between. The
 * selection slides between segments and the text colour follows it, so the change is seen, not
 * just the result.
 */
public class PrimeSegmentedRow extends PrimeCardRow {

    private static final int TRACK_HEIGHT = 40;
    private static final int TRACK_RADIUS = 12;
    private static final int INDICATOR_INSET = 3;

    private static TextPaint titlePaint, subtitlePaint, segmentPaint;

    private CharSequence title, subtitle;
    private String[] options = new String[0];
    private String[] shownOptions = new String[0];
    private float[] optionWidths = new float[0];
    private String shownTitle, shownSubtitle;
    private int selected;
    private Utilities.Callback<Integer> listener;

    private final AnimatedFloat indicator = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedFloat pressScale = new AnimatedFloat(this, 0, 160, CubicBezierInterpolator.EASE_OUT);
    private int lastPressed = -1;

    private final RectF track = new RectF();
    private final RectF tmp = new RectF();

    public PrimeSegmentedRow(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
        if (titlePaint == null) {
            titlePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            titlePaint.setTextSize(dp(15));
            subtitlePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            subtitlePaint.setTextSize(dp(12));
            segmentPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            segmentPaint.setTextSize(dp(14));
            segmentPaint.setTypeface(AndroidUtilities.bold());
        }
    }

    public void set(CharSequence title, CharSequence subtitle, String[] options, int selected,
                    Utilities.Callback<Integer> listener, boolean animated) {
        this.listener = listener;
        final boolean layoutChanged = !TextUtils.equals(this.title, title)
                || !TextUtils.equals(this.subtitle, subtitle)
                || !Arrays.equals(this.options, options);
        this.title = title;
        this.subtitle = subtitle;
        this.options = options == null ? new String[0] : options;
        final int clamped = Math.max(0, Math.min(selected, this.options.length - 1));
        if (clamped != this.selected || !animated) {
            this.selected = clamped;
            indicator.set(clamped, !animated);
        }
        if (layoutChanged) {
            requestLayout();
            onGeometryChanged();
        }
        invalidate();
    }

    private boolean hasTitle() {
        return !TextUtils.isEmpty(title);
    }

    private boolean hasSubtitle() {
        return !TextUtils.isEmpty(subtitle);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int h = dp(14);
        if (hasTitle()) {
            h += dp(20);
            if (hasSubtitle()) {
                h += dp(18);
            }
            h += dp(10);
        }
        h += dp(TRACK_HEIGHT) + dp(14);
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h);
    }

    @Override
    protected void onGeometryChanged() {
        if (getMeasuredWidth() <= 0) {
            return;
        }
        final int available = contentRight() - contentLeft();
        shownTitle = hasTitle() ? TextUtils.ellipsize(title, titlePaint, available, TextUtils.TruncateAt.END).toString() : null;
        shownSubtitle = hasSubtitle() ? TextUtils.ellipsize(subtitle, subtitlePaint, available, TextUtils.TruncateAt.END).toString() : null;

        final float top = getMeasuredHeight() - dp(14) - dp(TRACK_HEIGHT);
        track.set(contentLeft(), top, contentRight(), top + dp(TRACK_HEIGHT));

        final int n = options.length;
        if (shownOptions.length != n) {
            shownOptions = new String[n];
            optionWidths = new float[n];
        }
        final float segment = n == 0 ? 0 : track.width() / n - dp(12);
        for (int i = 0; i < n; i++) {
            shownOptions[i] = TextUtils.ellipsize(options[i], segmentPaint, segment, TextUtils.TruncateAt.END).toString();
            optionWidths[i] = segmentPaint.measureText(shownOptions[i]);
        }
    }

    private float segmentWidth() {
        return options.length == 0 ? 0 : track.width() / options.length;
    }

    /** Segment index in visual order; mirrored in RTL so the first option stays on the reading side. */
    private float segmentLeft(float index) {
        final float w = segmentWidth();
        return rtl() ? track.right - (index + 1) * w : track.left + index * w;
    }

    @Override
    protected void drawContent(Canvas canvas) {
        if (hasTitle() && shownTitle != null) {
            final float x = rtl() ? contentRight() - titlePaint.measureText(shownTitle) : contentLeft();
            titlePaint.setColor(color(Theme.key_windowBackgroundWhiteBlackText));
            canvas.drawText(shownTitle, x, dp(14) + dp(15), titlePaint);
            if (shownSubtitle != null) {
                final float sx = rtl() ? contentRight() - subtitlePaint.measureText(shownSubtitle) : contentLeft();
                subtitlePaint.setColor(color(Theme.key_windowBackgroundWhiteGrayText2));
                canvas.drawText(shownSubtitle, sx, dp(14) + dp(20) + dp(14), subtitlePaint);
            }
        }
        final int n = options.length;
        if (n == 0) {
            return;
        }
        final int accent = accentColor();
        fillPaint.setColor(Theme.multAlpha(accent, isDark() ? 0.16f : 0.10f));
        canvas.drawRoundRect(track, dp(TRACK_RADIUS), dp(TRACK_RADIUS), fillPaint);

        final float w = segmentWidth();
        final float index = indicator.set(selected);
        final float press = pressScale.set(pressedTarget >= 0 ? 1f : 0f);
        if (pressedTarget >= 0) {
            lastPressed = pressedTarget;
        }

        // A pressed segment that is not the selected one gets a soft fill, so the finger sees
        // which option it is on before letting go.
        if (lastPressed >= 0 && lastPressed != selected && press > 0) {
            final float l = segmentLeft(lastPressed);
            tmp.set(l + dp(INDICATOR_INSET), track.top + dp(INDICATOR_INSET), l + w - dp(INDICATOR_INSET), track.bottom - dp(INDICATOR_INSET));
            scale(tmp, 1f - 0.04f * press);
            fillPaint.setColor(Theme.multAlpha(accent, 0.12f * press));
            canvas.drawRoundRect(tmp, dp(TRACK_RADIUS - INDICATOR_INSET), dp(TRACK_RADIUS - INDICATOR_INSET), fillPaint);
        }

        final float il = segmentLeft(index);
        tmp.set(il + dp(INDICATOR_INSET), track.top + dp(INDICATOR_INSET), il + w - dp(INDICATOR_INSET), track.bottom - dp(INDICATOR_INSET));
        if (lastPressed == selected) {
            scale(tmp, 1f - 0.05f * press);
        }
        fillPaint.setColor(accent);
        canvas.drawRoundRect(tmp, dp(TRACK_RADIUS - INDICATOR_INSET), dp(TRACK_RADIUS - INDICATOR_INSET), fillPaint);

        final int textColor = color(Theme.key_windowBackgroundWhiteBlackText);
        final int onAccent = onAccentColor();
        final float baseline = track.centerY() - (segmentPaint.descent() + segmentPaint.ascent()) / 2f;
        for (int i = 0; i < n; i++) {
            // How much of this segment the moving indicator covers, 0..1: the text colour follows
            // the indicator as it slides instead of flipping when it lands.
            final float cover = Math.max(0f, 1f - Math.abs(i - index));
            segmentPaint.setColor(ColorUtils.blendARGB(textColor, onAccent, cover));
            final float cx = segmentLeft(i) + w / 2f;
            canvas.drawText(shownOptions[i], cx - optionWidths[i] / 2f, baseline, segmentPaint);
        }
    }

    private static void scale(RectF r, float s) {
        final float dx = r.width() * (1f - s) / 2f;
        final float dy = r.height() * (1f - s) / 2f;
        r.inset(dx, dy);
    }

    @Override
    protected int hitTest(float x, float y) {
        if (options.length == 0 || y < track.top - dp(6) || y > track.bottom + dp(6) || x < track.left || x > track.right) {
            return -1;
        }
        int i = (int) ((x - track.left) / segmentWidth());
        i = Math.max(0, Math.min(options.length - 1, i));
        return rtl() ? options.length - 1 - i : i;
    }

    @Override
    protected void onTargetClick(int target, boolean longPress) {
        if (target == selected) {
            return;
        }
        selected = target;
        invalidate();
        if (listener != null) {
            listener.run(target);
        }
    }

    public static class Factory extends UItem.UItemFactory<PrimeSegmentedRow> {
        static { setup(new Factory()); }

        @Override
        public PrimeSegmentedRow createView(Context context, RecyclerListView listView, int currentAccount,
                                            int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new PrimeSegmentedRow(context, resourcesProvider);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            final PrimeSegmentedRow row = (PrimeSegmentedRow) view;
            final boolean same = row.getTag() instanceof Integer && (Integer) row.getTag() == item.id;
            row.setTag(item.id);
            row.setPosition(item.flags & POS_MASK);
            row.set(item.text, item.subtext, item.texts, item.intValue, item.intCallback, same);
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
            return a.id == b.id && a.intValue == b.intValue && a.flags == b.flags
                    && TextUtils.equals(a.text, b.text) && TextUtils.equals(a.subtext, b.subtext)
                    && Arrays.equals(a.texts, b.texts);
        }

        /**
         * @param onSelect called with the chosen index when the user picks a different option.
         */
        public static UItem of(int id, CharSequence title, CharSequence subtitle, String[] options, int selected,
                               Utilities.Callback<Integer> onSelect) {
            final UItem item = UItem.ofFactory(Factory.class);
            item.id = id;
            item.text = title;
            item.subtext = subtitle;
            item.texts = options;
            item.intValue = selected;
            item.intCallback = onSelect;
            return item;
        }
    }
}
