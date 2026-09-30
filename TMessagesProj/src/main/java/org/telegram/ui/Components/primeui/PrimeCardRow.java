package org.telegram.ui.Components.primeui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;

/**
 * PrimeGram settings design system: the base of every new settings row.
 *
 * <p>One plain {@link View} per row, drawing everything on its own canvas - no nested layouts, no
 * child views to measure. That is what keeps a screen of these as cheap to scroll as a list of
 * text rows: a bind only stores values and recomputes geometry when something that affects it
 * changed, and {@link #onDraw} only reads what was computed.
 *
 * <p>Rows sit in the same rounded cards as {@code PrimeCheckCell}: a run of rows is one card, and
 * each row is told whether it is the top, middle, bottom or only row so it can round its own
 * corners (a RecyclerView has no notion of a group). The position values are the same ones, so
 * {@code endCard()} places these rows next to the existing ones without knowing the difference.
 *
 * <p>Rows with several targets (segments, chips, tiles) handle their own touches and draw the
 * pressed state on the target itself; their factories report themselves as not clickable, so the
 * list neither highlights the whole row nor sends it a click.
 */
public abstract class PrimeCardRow extends View {

    public static final int POS_SINGLE = 0;
    public static final int POS_TOP = 1;
    public static final int POS_MIDDLE = 2;
    public static final int POS_BOTTOM = 3;
    /** Not part of a card: the row draws on the page background (tiles, banners). */
    public static final int POS_NONE = 4;
    public static final int POS_MASK = 0xF;

    protected static final int CARD_MARGIN = 12;
    protected static final int CARD_RADIUS = 12;
    protected static final int CONTENT_INSET = 14;

    protected static final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    protected final Theme.ResourcesProvider resourcesProvider;

    private int position = POS_SINGLE;
    private final Path cardPath = new Path();
    private final RectF cardRect = new RectF();
    private final float[] radii = new float[8];
    private boolean cardPathDirty = true;

    // Touch: which target is under the finger, if any.
    protected int pressedTarget = -1;
    private float downX, downY;
    private boolean longPressFired;
    private final int touchSlop;
    private final Runnable longPressRunnable = () -> {
        if (pressedTarget >= 0 && supportsLongPress(pressedTarget)) {
            longPressFired = true;
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            final int target = pressedTarget;
            setPressedTarget(-1);
            onTargetClick(target, true);
        }
    };

    protected PrimeCardRow(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setPosition(int position) {
        if (this.position != position) {
            this.position = position;
            cardPathDirty = true;
            invalidate();
        }
    }

    public int getPosition() {
        return position;
    }

    protected int color(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    protected int accentColor() {
        return color(Theme.key_featuredStickers_addButton);
    }

    protected int onAccentColor() {
        return color(Theme.key_featuredStickers_buttonText);
    }

    protected boolean isDark() {
        return resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
    }

    protected int cardLeft() {
        return dp(CARD_MARGIN);
    }

    protected int cardRight() {
        return getMeasuredWidth() - dp(CARD_MARGIN);
    }

    /** Where content starts/ends inside the card. */
    protected int contentLeft() {
        return cardLeft() + dp(CONTENT_INSET);
    }

    protected int contentRight() {
        return cardRight() - dp(CONTENT_INSET);
    }

    protected static boolean rtl() {
        return LocaleController.isRTL;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        cardPathDirty = true;
        onGeometryChanged();
    }

    /** Size changed: recompute whatever depends on it. Called before the next draw. */
    protected void onGeometryChanged() {
    }

    private void updateCardPath() {
        cardPathDirty = false;
        cardPath.rewind();
        if (position == POS_NONE) {
            return;
        }
        final float r = dp(CARD_RADIUS);
        final float top = position == POS_SINGLE || position == POS_TOP ? r : 0;
        final float bottom = position == POS_SINGLE || position == POS_BOTTOM ? r : 0;
        radii[0] = radii[1] = radii[2] = radii[3] = top;
        radii[4] = radii[5] = radii[6] = radii[7] = bottom;
        cardRect.set(cardLeft(), 0, cardRight(), getMeasuredHeight());
        cardPath.addRoundRect(cardRect, radii, Path.Direction.CW);
    }

    @Override
    protected final void onDraw(Canvas canvas) {
        // The page behind the card: covers the list's own highlight, which is sized to the whole
        // row and would otherwise show in the margins (same reason as PrimeCheckCell).
        fillPaint.setShader(null);
        fillPaint.setColor(color(Theme.key_windowBackgroundGray));
        canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), fillPaint);
        if (position != POS_NONE) {
            if (cardPathDirty) {
                updateCardPath();
            }
            fillPaint.setColor(color(Theme.key_windowBackgroundWhite));
            canvas.drawPath(cardPath, fillPaint);
            if (position == POS_TOP || position == POS_MIDDLE) {
                fillPaint.setColor(color(Theme.key_divider));
                canvas.drawRect(contentLeft(), getMeasuredHeight() - 1, cardRight(), getMeasuredHeight(), fillPaint);
            }
        }
        drawContent(canvas);
    }

    protected abstract void drawContent(Canvas canvas);

    // ---- touch ----------------------------------------------------------------------------

    /** Index of the target at (x, y), or -1. */
    protected int hitTest(float x, float y) {
        return -1;
    }

    protected boolean supportsLongPress(int target) {
        return false;
    }

    protected void onTargetClick(int target, boolean longPress) {
    }

    /** The pressed target changed; animate from here. */
    protected void onPressedChanged(int target) {
        invalidate();
    }

    private void setPressedTarget(int target) {
        if (pressedTarget != target) {
            pressedTarget = target;
            onPressedChanged(target);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                final int target = hitTest(event.getX(), event.getY());
                if (target < 0) {
                    return false;
                }
                downX = event.getX();
                downY = event.getY();
                longPressFired = false;
                setPressedTarget(target);
                if (supportsLongPress(target)) {
                    postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                if (pressedTarget >= 0 && (Math.abs(event.getX() - downX) > touchSlop || Math.abs(event.getY() - downY) > touchSlop)) {
                    removeCallbacks(longPressRunnable);
                    setPressedTarget(-1);
                }
                return pressedTarget >= 0;
            case MotionEvent.ACTION_UP: {
                removeCallbacks(longPressRunnable);
                final int target = pressedTarget;
                setPressedTarget(-1);
                if (target >= 0 && !longPressFired) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    onTargetClick(target, false);
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPressRunnable);
                setPressedTarget(-1);
                return true;
        }
        return false;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(longPressRunnable);
        pressedTarget = -1;
    }

    // ---- helpers ---------------------------------------------------------------------------

    protected static int dpi(float v) {
        return AndroidUtilities.dp(v);
    }
}
