package org.telegram.ui.Components.primeui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

/**
 * PrimeGram settings: a state banner - "needs a restart", "turned off after a crash", "this
 * works only with X on". Things the user has to notice, set apart from the grey footnotes under a
 * card that nobody reads. Tinted by kind, optionally with one action.
 */
public class PrimeBannerRow extends PrimeCardRow {

    public static final int KIND_INFO = 0;
    public static final int KIND_WARNING = 1;
    public static final int KIND_SUCCESS = 2;
    public static final int KIND_DANGER = 3;

    private static final int PAD = 14;
    private static final int ICON = 22;

    private static TextPaint textPaint, actionPaint;

    private CharSequence text, action;
    private int kind;
    private int iconRes;
    private Drawable icon;
    private View.OnClickListener onAction;
    private StaticLayout layout;
    private String shownAction;
    private int laidOutWidth = -1;
    private final RectF box = new RectF();
    private int iconFilterColor;
    private Drawable iconFilterFor;

    public PrimeBannerRow(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
        setPosition(POS_NONE);
        if (textPaint == null) {
            textPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(dp(14));
            actionPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            actionPaint.setTextSize(dp(14));
            actionPaint.setTypeface(AndroidUtilities.bold());
        }
    }

    public void set(int kind, int iconRes, CharSequence text, CharSequence action, View.OnClickListener onAction) {
        final boolean changed = this.kind != kind || !TextUtils.equals(this.text, text) || !TextUtils.equals(this.action, action);
        this.kind = kind;
        this.text = text;
        this.action = action;
        this.onAction = onAction;
        if (this.iconRes != iconRes || icon == null && iconRes != 0) {
            this.iconRes = iconRes;
            final Drawable d = iconRes != 0 ? ContextCompat.getDrawable(getContext(), iconRes) : null;
            icon = d == null ? null : d.mutate();
        }
        if (changed) {
            laidOutWidth = -1;
            requestLayout();
        }
        invalidate();
    }

    private int kindColor() {
        switch (kind) {
            case KIND_WARNING: return 0xFFF09F1B;
            case KIND_SUCCESS: return 0xFF34B64A;
            case KIND_DANGER: return 0xFFE5484D;
            default: return accentColor();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width != laidOutWidth) {
            laidOutWidth = width;
            final int textWidth = Math.max(dp(40), width - dp(CARD_MARGIN) * 2 - dp(PAD) * 3 - dp(ICON));
            layout = new StaticLayout(text == null ? "" : text, textPaint, textWidth,
                    rtl() ? Layout.Alignment.ALIGN_OPPOSITE : Layout.Alignment.ALIGN_NORMAL, 1f, dp(2), false);
            shownAction = TextUtils.isEmpty(action) ? null
                    : TextUtils.ellipsize(action, actionPaint, textWidth, TextUtils.TruncateAt.END).toString();
        }
        int h = dp(PAD) * 2 + Math.max(dp(ICON), layout.getHeight());
        if (shownAction != null) {
            h += dp(10) + dp(18);
        }
        setMeasuredDimension(width, h + dp(8));
    }

    @Override
    protected void drawContent(Canvas canvas) {
        final int c = kindColor();
        box.set(dp(CARD_MARGIN), 0, getMeasuredWidth() - dp(CARD_MARGIN), getMeasuredHeight() - dp(8));
        final boolean pressed = pressedTarget == 0;
        fillPaint.setColor(Theme.multAlpha(c, (isDark() ? 0.20f : 0.12f) + (pressed ? 0.06f : 0f)));
        final float r = radius(org.telegram.ui.Components.design.DesignSystem.Role.LIST_CARD);
        canvas.drawRoundRect(box, r, r, fillPaint);

        final float iconLeft = rtl() ? box.right - dp(PAD) - dp(ICON) : box.left + dp(PAD);
        if (icon != null) {
            if (iconFilterColor != c || iconFilterFor != icon) {
                iconFilterColor = c;
                iconFilterFor = icon;
                icon.setColorFilter(new PorterDuffColorFilter(c, PorterDuff.Mode.SRC_IN));
            }
            icon.setBounds((int) iconLeft, (int) box.top + dp(PAD), (int) iconLeft + dp(ICON), (int) box.top + dp(PAD) + dp(ICON));
            icon.draw(canvas);
        }
        final float textLeft = rtl() ? box.left + dp(PAD) : iconLeft + dp(ICON) + dp(PAD);
        textPaint.setColor(color(Theme.key_windowBackgroundWhiteBlackText));
        canvas.save();
        canvas.translate(textLeft, box.top + dp(PAD) + Math.max(0, (dp(ICON) - layout.getHeight()) / 2f));
        layout.draw(canvas);
        canvas.restore();
        if (shownAction != null) {
            actionPaint.setColor(c);
            final float ax = rtl() ? box.right - dp(PAD) * 2 - dp(ICON) - actionPaint.measureText(shownAction) : textLeft;
            canvas.drawText(shownAction, ax, box.bottom - dp(PAD), actionPaint);
        }
    }

    @Override
    protected int hitTest(float x, float y) {
        return onAction != null && box.contains(x, y) ? 0 : -1;
    }

    @Override
    protected void onTargetClick(int target, boolean longPress) {
        if (onAction != null) {
            onAction.onClick(this);
        }
    }

    public static class Factory extends UItem.UItemFactory<PrimeBannerRow> {
        static { setup(new Factory()); }

        @Override
        public PrimeBannerRow createView(Context context, RecyclerListView listView, int currentAccount,
                                         int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new PrimeBannerRow(context, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            ((PrimeBannerRow) view).set(item.intValue, item.iconResId, item.text, item.textValue, item.clickCallback);
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
            return a.id == b.id && a.intValue == b.intValue && a.iconResId == b.iconResId
                    && TextUtils.equals(a.text, b.text) && TextUtils.equals(a.textValue, b.textValue);
        }

        public static UItem of(int id, int kind, int icon, CharSequence text, CharSequence action, View.OnClickListener onAction) {
            final UItem item = UItem.ofFactory(Factory.class);
            item.id = id;
            item.intValue = kind;
            item.iconResId = icon;
            item.text = text;
            item.textValue = action;
            item.clickCallback = onAction;
            return item;
        }
    }
}
