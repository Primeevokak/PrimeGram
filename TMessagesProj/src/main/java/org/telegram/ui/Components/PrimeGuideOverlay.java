package org.telegram.ui.Components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * PrimeGram: the dimmed sheet the guided tour speaks through.
 *
 * <p>A hole is cut over whatever the step is about, so the thing being explained stays lit while
 * everything else recedes. That is the whole reason not to use a plain dialog: a description of a
 * switch is far less useful than a description next to the switch, and a tour that only ever
 * centres a card leaves the reader hunting for what it just named.
 *
 * <p>Every touch is swallowed, including on the highlighted row. The row is lit to be looked at,
 * not operated - letting a tap through would flip a setting the user was only being shown.
 */
public class PrimeGuideOverlay extends FrameLayout {

    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint holePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF target = new RectF();
    private boolean hasTarget;

    private final LinearLayout card;
    private final TextView titleView;
    private final TextView textView;
    private final TextView counterView;
    private final TextView skipView;
    private final TextView nextView;

    private Runnable onNext;
    private Runnable onSkip;
    private float appear;

    public PrimeGuideOverlay(@NonNull Context context) {
        super(context);
        setWillNotDraw(false);

        dimPaint.setColor(0xCC000000);
        holePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(AndroidUtilities.dp(2));
        outlinePaint.setColor(0x66FFFFFF);

        card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(14),
                Theme.getColor(Theme.key_dialogBackground)));
        card.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(16),
                AndroidUtilities.dp(18), AndroidUtilities.dp(10));
        card.setElevation(AndroidUtilities.dp(6));

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        card.addView(titleView, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        textView.setLineSpacing(AndroidUtilities.dp(2), 1f);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        card.addView(textView, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));

        final LinearLayout bottom = new LinearLayout(context);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(bottom, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));

        counterView = new TextView(context);
        counterView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        counterView.setTextColor(Theme.getColor(Theme.key_dialogTextGray3));
        bottom.addView(counterView, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        final View spacer = new View(context);
        bottom.addView(spacer, LayoutHelper.createLinear(0, 1, 1f));

        skipView = new TextView(context);
        skipView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        skipView.setTypeface(AndroidUtilities.bold());
        skipView.setText("Пропустить");
        skipView.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(8),
                AndroidUtilities.dp(10), AndroidUtilities.dp(8));
        skipView.setTextColor(Theme.getColor(Theme.key_dialogTextGray3));
        skipView.setBackground(Theme.createRadSelectorDrawable(
                Theme.getColor(Theme.key_listSelector), 8, 8));
        skipView.setOnClickListener(v -> {
            if (onSkip != null) {
                onSkip.run();
            }
        });
        bottom.addView(skipView, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        nextView = new TextView(context);
        nextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        nextView.setTypeface(AndroidUtilities.bold());
        nextView.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(8),
                AndroidUtilities.dp(14), AndroidUtilities.dp(8));
        nextView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        nextView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(8),
                Theme.getColor(Theme.key_featuredStickers_addButton),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        nextView.setOnClickListener(v -> {
            if (onNext != null) {
                onNext.run();
            }
        });
        bottom.addView(nextView, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));

        addView(card, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 16, 0, 16, 0));

        setAlpha(0f);
        animate().alpha(1f).setDuration(180).start();
    }

    /**
     * Shows one step.
     *
     * @param bounds where the row sits in this overlay's own coordinates, or null when the step is
     *               about the page rather than a row - then the card is simply centred.
     */
    public void setStep(RectF bounds, String title, String text, int index, int total,
                        Runnable next, Runnable skip) {
        hasTarget = bounds != null;
        if (hasTarget) {
            target.set(bounds);
            target.inset(-AndroidUtilities.dp(4), -AndroidUtilities.dp(4));
        }
        titleView.setText(title);
        textView.setText(text);
        counterView.setText(index + " / " + total);
        nextView.setText(index >= total ? "Готово" : "Далее");
        skipView.setVisibility(index >= total ? GONE : VISIBLE);
        onNext = next;
        onSkip = skip;
        requestLayout();
        invalidate();

        // A short fade on the card only, so moving between steps reads as the same sheet turning
        // a page rather than as a new dialog each time.
        card.setAlpha(0f);
        card.animate().alpha(1f).setDuration(160).start();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        final int cardHeight = card.getMeasuredHeight();
        final int margin = AndroidUtilities.dp(12);
        int cardTop;
        if (!hasTarget) {
            cardTop = (getHeight() - cardHeight) / 2;
        } else if (target.bottom + margin + cardHeight < getHeight() - margin) {
            cardTop = (int) target.bottom + margin;
        } else if (target.top - margin - cardHeight > margin) {
            cardTop = (int) target.top - margin - cardHeight;
        } else {
            // Neither side fits: the row is tall or the screen is short. Centring keeps the card
            // readable, and the hole still says which row is meant.
            cardTop = (getHeight() - cardHeight) / 2;
        }
        card.layout(card.getLeft(), cardTop, card.getRight(), cardTop + cardHeight);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
        canvas.drawRect(0, 0, getWidth(), getHeight(), dimPaint);
        if (hasTarget) {
            final float radius = AndroidUtilities.dp(10);
            canvas.drawRoundRect(target, radius, radius, holePaint);
        }
        canvas.restore();
        if (hasTarget) {
            final float radius = AndroidUtilities.dp(10);
            canvas.drawRoundRect(target, radius, radius, outlinePaint);
        }
        super.dispatchDraw(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Everything not on a button belongs to the overlay: the screen underneath is being
        // described, not used.
        return true;
    }

    public void dismiss(Runnable after) {
        animate().alpha(0f).setDuration(150).withEndAction(() -> {
            if (getParent() instanceof android.view.ViewGroup) {
                ((android.view.ViewGroup) getParent()).removeView(this);
            }
            if (after != null) {
                after.run();
            }
        }).start();
    }
}
