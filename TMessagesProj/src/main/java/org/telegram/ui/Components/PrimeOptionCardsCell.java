package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * PrimeGram: a row of option cards, each drawing what the option actually looks like.
 *
 * <p>Replaces the pattern this screen leaned on far too heavily - a row whose value is a word, and
 * a dialog behind it listing more words. "Реакция / Ответить / Ничего" tells you what the setting
 * is called; a picture of a squared avatar tells you what it does. Anything with a small number of
 * choices and a visible result belongs here instead of behind a dialog.
 *
 * <p>Subclasses supply the drawing per option. The cell handles layout, selection, animation and
 * the label underneath, so a new selector is one {@code drawOption} method rather than a new view.
 */
public abstract class PrimeOptionCardsCell extends LinearLayout {

    /** Height of the drawn area inside a card, above its label. */
    private static final int PREVIEW_HEIGHT = 76;

    private final Card[] cards;
    private int selected;
    private final Theme.ResourcesProvider resourcesProvider;

    public interface OnSelected {
        void run(int index);
    }

    private OnSelected onSelected;

    public PrimeOptionCardsCell(Context context, CharSequence[] labels, int selected,
                                Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.selected = selected;
        setOrientation(HORIZONTAL);
        setPadding(dp(12), dp(4), dp(12), dp(10));

        cards = new Card[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            final Card card = new Card(context, labels[i], i);
            card.setOnClickListener(v -> select(index, true));
            cards[i] = card;
            // Equal weights rather than a fixed width: three options and five options both have
            // to fill the row without anyone measuring anything by hand.
            addView(card, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f,
                    i == 0 ? 0 : 5, 0, 0, 0));
        }
        updateSelection(false);
    }

    public void setOnSelected(OnSelected listener) {
        this.onSelected = listener;
    }

    public int getSelected() {
        return selected;
    }

    public void select(int index, boolean animated) {
        if (index < 0 || index >= cards.length) {
            return;
        }
        final boolean changed = selected != index;
        selected = index;
        updateSelection(animated);
        if (changed && onSelected != null) {
            onSelected.run(index);
        }
    }

    private void updateSelection(boolean animated) {
        for (int i = 0; i < cards.length; i++) {
            cards[i].setSelectedState(i == selected, animated);
        }
    }

    /** Redraws every card - call when something outside changed what they should show. */
    public void refresh() {
        for (Card card : cards) {
            card.invalidate();
        }
    }

    /**
     * Reapplies the current {@link org.telegram.ui.Components.design.DesignSystem}'s shape to
     * every card - the corner radius baked into each {@link PrimeSettingsUi.CardDrawable} at
     * construction is otherwise invisible to a later design-mode switch, since these cells are
     * cached and rebound rather than recreated (see {@code PrimeGramSettingsActivity}'s
     * "Дизайн-система (пилот)" card, which calls this on every already-built option-card cell
     * it can reach right after the mode changes).
     */
    public void refreshDesignSystem() {
        final org.telegram.ui.Components.design.DesignSystem system =
                org.telegram.ui.Components.design.DesignSystem.current();
        for (Card card : cards) {
            card.background.applyDesignSystem(system, org.telegram.ui.Components.design.DesignSystem.Role.CARD);
        }
    }

    /**
     * Draws option {@code index} inside {@code bounds}.
     *
     * <p>{@code selection} runs 0 to 1 during the selection animation, so an option may emphasise
     * itself as it is picked rather than only being outlined.
     */
    protected abstract void drawOption(Canvas canvas, RectF bounds, int index, float selection);

    private class Card extends FrameLayout {

        private final PrimeSettingsUi.CardDrawable background = new PrimeSettingsUi.CardDrawable(10);
        private final TextPaint textPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF();
        private final CharSequence label;
        private final int index;

        Card(Context context, CharSequence label, int index) {
            super(context);
            this.label = label;
            this.index = index;
            setWillNotDraw(false);
            textPaint.setTextSize(dp(12));
            background.setInvalidateCallback(this::invalidate);
            background.applyDesignSystem(org.telegram.ui.Components.design.DesignSystem.current(),
                    org.telegram.ui.Components.design.DesignSystem.Role.CARD);
        }

        void setSelectedState(boolean selected, boolean animated) {
            background.setSelected(selected, animated);
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec,
                    MeasureSpec.makeMeasureSpec(dp(PREVIEW_HEIGHT + 26), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final float selection = background.getSelection();
            background.setBounds(0, 0, getMeasuredWidth(), dp(PREVIEW_HEIGHT));
            background.draw(canvas);

            bounds.set(dp(8), dp(8), getMeasuredWidth() - dp(8), dp(PREVIEW_HEIGHT - 8));
            canvas.save();
            canvas.clipRect(bounds);
            drawOption(canvas, bounds, index, selection);
            canvas.restore();

            textPaint.setColor(ColorUtils.blendARGB(
                    Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider),
                    Theme.getColor(Theme.key_windowBackgroundWhiteValueText, resourcesProvider), selection));
            textPaint.setTypeface(selection >= 0.5f ? AndroidUtilities.bold() : null);
            final String text = label.toString();
            final float width = textPaint.measureText(text);
            canvas.drawText(text, (getMeasuredWidth() - width) / 2f, dp(PREVIEW_HEIGHT + 17), textPaint);
        }
    }
}
