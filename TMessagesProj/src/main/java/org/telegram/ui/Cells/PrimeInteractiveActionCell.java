package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * PrimeGram: reusable base type for a custom, interactive message row rendered inline
 * in the chat feed - a rounded card (built on the same
 * {@code key_chat_serviceBackground}/{@code key_chat_serviceText} colors Telegram's own
 * system messages use, so it reads as "this chat's own chrome," not a bolted-on
 * overlay) with an icon, title, body text, and a horizontal row subclasses populate with
 * any number of real button {@link View}s via {@link #addButton}.
 *
 * <p>Built as a genuine {@link ViewGroup} with real child {@code View}s (not the
 * {@code Canvas}-drawn/manual-hit-testing approach {@code ChatActionCell} uses for its
 * own single inline button) specifically so
 * {@code RecyclerListView.RecyclerListViewItemClickListener}'s existing
 * {@code findClickableDescendantAt(...)} touch-interception already exempts every
 * clickable/long-clickable button here from the outer message list's own tap/long-press/
 * multi-select handling - no per-cell touch plumbing needed.
 *
 * <p>Subclasses: override {@link #bind(MessageObject)} to set the icon/title/body/
 * buttons for the current state, and {@link #onCellAttached()}/{@link #onCellDetached()}
 * if they own anything (an animation, a listener) that must start/stop with the view's
 * own attach lifecycle - {@code RecyclerView} recycles these aggressively and there is
 * no adapter-side recycle hook in this codebase to fall back on, only
 * {@code View.onDetachedFromWindow()} (see {@code ChatActionCell}'s own
 * attach/detach-paired sub-layouts for the precedent this mirrors).
 */
public abstract class PrimeInteractiveActionCell extends BaseCell implements IMessageCell {

    protected final Theme.ResourcesProvider resourcesProvider;
    protected MessageObject currentMessageObject;

    private final FrameLayout card;
    protected final ImageView iconView;
    protected final TextView titleView;
    protected final TextView bodyView;
    protected final LinearLayout buttonRow;

    private static final int CARD_SIDE_MARGIN_DP = 28;
    private static final int CARD_PADDING_DP = 16;
    private static final int CORNER_RADIUS_DP = 18;

    protected PrimeInteractiveActionCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setWillNotDraw(true);

        card = new FrameLayout(context);
        card.setBackground(Theme.createRoundRectDrawable(dp(CORNER_RADIUS_DP), cardBackgroundColor()));
        card.setPadding(dp(CARD_PADDING_DP), dp(CARD_PADDING_DP), dp(CARD_PADDING_DP), dp(CARD_PADDING_DP));
        addView(card);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        card.addView(content, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        iconView = new ImageView(context);
        iconView.setColorFilter(new PorterDuffColorFilter(accentColor(), PorterDuff.Mode.SRC_IN));
        content.addView(iconView, LayoutHelper.createLinear(26, 26, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 8));

        titleView = new TextView(context);
        titleView.setGravity(Gravity.CENTER);
        titleView.setTextColor(textColor());
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        content.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        bodyView = new TextView(context);
        bodyView.setGravity(Gravity.CENTER);
        bodyView.setTextColor(textColor());
        bodyView.setAlpha(0.75f);
        bodyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        content.addView(bodyView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 2, 0, 14));

        buttonRow = new LinearLayout(context);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.CENTER);
        content.addView(buttonRow, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
    }

    protected int cardBackgroundColor() {
        return Theme.getColor(Theme.key_chat_serviceBackground, resourcesProvider);
    }

    protected int textColor() {
        return Theme.getColor(Theme.key_chat_serviceText, resourcesProvider);
    }

    protected int accentColor() {
        return Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
    }

    /** Removes every previously-added button so {@link #bind} can rebuild the row from
     *  scratch for whatever state the message is in now - a rebind (row scrolled back
     *  into view, or a state refresh after a cancel notification) must never stack
     *  stale buttons behind new ones. */
    protected void clearButtons() {
        buttonRow.removeAllViews();
    }

    protected void addButton(View button) {
        int margin = buttonRow.getChildCount() == 0 ? 0 : dp(8);
        buttonRow.addView(button, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, margin, 0, 0, 0));
    }

    public abstract void bind(MessageObject obj);

    protected void onCellAttached() {
    }

    protected void onCellDetached() {
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        onCellAttached();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        onCellDetached();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int cardWidthSpec = MeasureSpec.makeMeasureSpec(width - dp(CARD_SIDE_MARGIN_DP) * 2, MeasureSpec.AT_MOST);
        int cardHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        card.measure(cardWidthSpec, cardHeightSpec);
        setMeasuredDimension(width, card.getMeasuredHeight() + dp(12));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l;
        int cardWidth = card.getMeasuredWidth();
        int cardLeft = (width - cardWidth) / 2;
        int top = dp(6);
        card.layout(cardLeft, top, cardLeft + cardWidth, top + card.getMeasuredHeight());
    }

    @Override
    public MessageObject getMessageObject() {
        return currentMessageObject;
    }

    @Override
    public float getDeltaTop() {
        return 0;
    }

    @Override
    public float getDeltaLeft() {
        return 0;
    }

    @Override
    public float getDeltaBottom() {
        return 0;
    }

    @Override
    public float getDeltaRight() {
        return 0;
    }
}
