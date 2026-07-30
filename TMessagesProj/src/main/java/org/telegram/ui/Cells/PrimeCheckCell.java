package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PrimeSwitch;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.SettingsActivity;

/**
 * PrimeGram: one row of a settings card.
 *
 * <p>The screen used to be a flat run of full-width rows separated by grey gaps, which is what
 * {@code UItem.asCheck} gives you. That reads as a list of forty unrelated switches: everything is
 * equally loud, and the only way to find one is to read them all. Here a run of related rows is
 * drawn as a single rounded card - the group becomes the unit the eye picks up, and a row is
 * something you find inside a group rather than in the whole screen.
 *
 * <p>The coloured icon is not decoration either: it is the fastest thing on the row to recognise
 * on a second visit, well before the label has been read.
 *
 * <p>Rounding is a property of the row, not of the card, because a RecyclerView has no notion of
 * a group - it only ever hands out rows. So each row is told whether it is the first, the last,
 * both or neither, and draws its own corners accordingly.
 */
public class PrimeCheckCell extends FrameLayout implements Theme.Colorable {

    public static final int POS_SINGLE = 0;
    public static final int POS_TOP = 1;
    public static final int POS_MIDDLE = 2;
    public static final int POS_BOTTOM = 3;

    /** Encoded into {@link UItem#flags} alongside the position. */
    private static final int FLAG_SWITCH = 1 << 4;
    private static final int FLAG_ARROW = 1 << 5;
    private static final int POS_MASK = 0xF;

    private static final Paint paint = new Paint();

    private final Theme.ResourcesProvider resourcesProvider;

    private final LinearLayout content;
    private final FrameLayout iconLayout;
    private final SettingsActivity.SettingCell.Background iconBackground;
    private final ImageView iconView;
    private final LinearLayout textLayout;
    private final TextView titleView;
    private final TextView subtitleView;
    private final TextView valueView;
    private final PrimeSwitch switchView;
    private final ImageView arrowView;

    private int position = POS_SINGLE;
    private boolean twoLines;

    public PrimeCheckCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setDuplicateParentStateEnabled(true);
        addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT,
                Gravity.FILL, 12, 0, 12, 0));

        iconLayout = new FrameLayout(context);
        iconLayout.setBackground(iconBackground = new SettingsActivity.SettingCell.Background());
        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iconLayout.addView(iconView, LayoutHelper.createFrame(20, 20, Gravity.CENTER));

        textLayout = new LinearLayout(context);
        textLayout.setOrientation(LinearLayout.VERTICAL);

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleView.setMaxLines(2);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        textLayout.addView(titleView, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        subtitleView.setMaxLines(2);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        textLayout.addView(subtitleView, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        valueView = new TextView(context);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        valueView.setMaxLines(1);
        valueView.setEllipsize(TextUtils.TruncateAt.END);

        switchView = new PrimeSwitch(context);
        switchView.setResourcesProvider(resourcesProvider);

        arrowView = new ImageView(context);
        arrowView.setImageResource(org.telegram.messenger.R.drawable.msg_arrowright);
        arrowView.setScaleType(ImageView.ScaleType.CENTER);

        final boolean rtl = LocaleController.isRTL;
        if (rtl) {
            content.addView(switchView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 14, 0, 0, 0));
            content.addView(arrowView, LayoutHelper.createLinear(20, 20, Gravity.CENTER_VERTICAL, 14, 0, 0, 0));
            content.addView(valueView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 14, 0, 0, 0));
            content.addView(textLayout, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f,
                    Gravity.CENTER_VERTICAL, 12, 0, 12, 0));
            content.addView(iconLayout, LayoutHelper.createLinear(28, 28, Gravity.CENTER_VERTICAL, 0, 0, 14, 0));
        } else {
            content.addView(iconLayout, LayoutHelper.createLinear(28, 28, Gravity.CENTER_VERTICAL, 14, 0, 0, 0));
            content.addView(textLayout, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f,
                    Gravity.CENTER_VERTICAL, 12, 0, 12, 0));
            content.addView(valueView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 14, 0));
            content.addView(arrowView, LayoutHelper.createLinear(20, 20, Gravity.CENTER_VERTICAL, 0, 0, 14, 0));
            content.addView(switchView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 14, 0));
        }

        setWillNotDraw(false);
        updateColors();
    }

    @Override
    public void updateColors() {
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        valueView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText, resourcesProvider));
        arrowView.setColorFilter(new android.graphics.PorterDuffColorFilter(
                Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider), 0.7f),
                android.graphics.PorterDuff.Mode.SRC_IN));
        iconBackground.setDrawBorder(resourcesProvider != null
                ? resourcesProvider.isDark() : Theme.isCurrentThemeDark());
        applyBackground();
    }

    private void applyBackground() {
        // createRadSelectorDrawable takes its radii in dp, not pixels.
        final int top = position == POS_SINGLE || position == POS_TOP ? 12 : 0;
        final int bottom = position == POS_SINGLE || position == POS_BOTTOM ? 12 : 0;
        content.setBackground(Theme.createRadSelectorDrawable(
                Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider),
                Theme.getColor(Theme.key_listSelector, resourcesProvider),
                top, bottom));
    }

    public void set(int position, int iconColorTop, int iconColorBottom, int icon,
                    CharSequence title, CharSequence subtitle, CharSequence value,
                    boolean hasSwitch, boolean hasArrow, boolean checked, boolean animated) {
        if (this.position != position) {
            this.position = position;
            applyBackground();
        }

        iconLayout.setVisibility(icon != 0 ? VISIBLE : GONE);
        if (icon != 0) {
            iconBackground.setColor(iconColorTop, iconColorBottom);
            iconView.setImageResource(icon);
        }

        titleView.setText(title);
        twoLines = !TextUtils.isEmpty(subtitle);
        subtitleView.setVisibility(twoLines ? VISIBLE : GONE);
        subtitleView.setText(subtitle);

        valueView.setVisibility(TextUtils.isEmpty(value) ? GONE : VISIBLE);
        valueView.setText(value);

        switchView.setVisibility(hasSwitch ? VISIBLE : GONE);
        if (hasSwitch) {
            switchView.setChecked(checked, animated);
        }
        arrowView.setVisibility(hasArrow ? VISIBLE : GONE);

        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(twoLines ? 64 : 52), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // The list draws its own pressed highlight behind the row, sized to the whole row - which
        // would show as a grey band in the margins on either side of the card. Painting the page
        // background here covers it, leaving the card's own ripple as the only feedback. The card
        // is opaque anyway, so this costs one rectangle and no overdraw that was not already there.
        paint.setColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
        canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), paint);

        // Inside a card the rows need something between them, but a full-width line would run
        // past the rounded corners; it starts where the text starts, the way a list of contacts
        // separates entries without separating them from the card.
        if (position != POS_SINGLE && position != POS_BOTTOM) {
            paint.setColor(Theme.getColor(Theme.key_divider, resourcesProvider));
            final int inset = dp(iconLayout.getVisibility() == VISIBLE ? 66 : 26);
            final int left = LocaleController.isRTL ? dp(12) : inset;
            final int right = getMeasuredWidth() - (LocaleController.isRTL ? inset : dp(12));
            canvas.drawRect(left, getMeasuredHeight() - 1, right, getMeasuredHeight(), paint);
        }
    }

    public static class Factory extends UItem.UItemFactory<PrimeCheckCell> {
        static { setup(new Factory()); }

        @Override
        public PrimeCheckCell createView(Context context, RecyclerListView listView, int currentAccount,
                                         int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new PrimeCheckCell(context, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter,
                             UniversalRecyclerView listView) {
            final PrimeCheckCell cell = (PrimeCheckCell) view;
            final int iconColorTop = (int) item.longValue;
            final int iconColorBottom = (int) (item.longValue >>> 32);
            // Animate only a switch that is already on screen showing the other value: a cell
            // fresh out of the recycler has no previous state worth animating from.
            final boolean animated = cell.switchView.getVisibility() == VISIBLE
                    && cell.switchView.isChecked() != item.checked;
            cell.set(item.flags & POS_MASK, iconColorTop, iconColorBottom, item.iconResId,
                    item.text, item.subtext, item.textValue,
                    (item.flags & FLAG_SWITCH) != 0, (item.flags & FLAG_ARROW) != 0,
                    item.checked, animated);
        }

        /**
         * Two rows are the same row when they carry the same id — that is what lets the list
         * animate a change rather than replace the view.
         */
        @Override
        public boolean equals(UItem a, UItem b) {
            return a.id == b.id;
        }

        /**
         * ...and they show the same thing when everything drawn on them matches. The default
         * comparison for a factory row ignores {@code checked} and {@code flags}, which is exactly
         * the state a switch lives in: without this, flipping one would rebuild the list and
         * nothing on screen would change.
         */
        @Override
        public boolean contentsEquals(UItem a, UItem b) {
            return a.id == b.id
                    && a.checked == b.checked
                    && a.flags == b.flags
                    && a.iconResId == b.iconResId
                    && a.longValue == b.longValue
                    && TextUtils.equals(a.text, b.text)
                    && TextUtils.equals(a.subtext, b.subtext)
                    && TextUtils.equals(a.textValue, b.textValue);
        }

        private static UItem base(int id, int position, int iconColorTop, int iconColorBottom,
                                  int icon, CharSequence title, CharSequence subtitle) {
            final UItem item = UItem.ofFactory(Factory.class);
            item.id = id;
            item.iconResId = icon;
            item.text = title;
            item.subtext = subtitle;
            item.flags = position;
            item.longValue = ((long) iconColorBottom << 32) | (iconColorTop & 0xFFFFFFFFL);
            return item;
        }

        /**
         * Where this row sits in its card. Set after the fact because a card is built by adding
         * rows to it, and only the last one knows how many there turned out to be.
         */
        public static void position(UItem item, int position) {
            item.flags = (item.flags & ~POS_MASK) | position;
        }

        /** A row that toggles something. */
        public static UItem check(int id, int position, int iconColorTop, int iconColorBottom,
                                  int icon, CharSequence title, CharSequence subtitle, boolean checked) {
            final UItem item = base(id, position, iconColorTop, iconColorBottom, icon, title, subtitle);
            item.flags |= FLAG_SWITCH;
            item.checked = checked;
            return item;
        }

        /** A row that opens something: value on the right, chevron after it. */
        public static UItem button(int id, int position, int iconColorTop, int iconColorBottom,
                                   int icon, CharSequence title, CharSequence subtitle, CharSequence value) {
            final UItem item = base(id, position, iconColorTop, iconColorBottom, icon, title, subtitle);
            item.flags |= FLAG_ARROW;
            item.textValue = value;
            return item;
        }
    }
}
