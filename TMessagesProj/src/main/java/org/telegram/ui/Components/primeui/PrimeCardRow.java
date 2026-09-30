package org.telegram.ui.Components.primeui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

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
 * <p>Rows with several targets (segments, chips, tiles) get the tap from the list as an ordinary
 * row click with the touch point, and {@link #clickAt} works out which target it hit; the pressed
 * state is drawn on that target, and the page background painted here hides the list's own
 * whole-row highlight.
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
    protected static final int CONTENT_INSET = 14;

    protected static final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    protected final Theme.ResourcesProvider resourcesProvider;

    private int position = POS_SINGLE;
    private final Path cardPath = new Path();
    private final RectF cardRect = new RectF();
    private final float[] radii = new float[8];
    private boolean cardPathDirty = true;
    private int drawnDesignMode = Integer.MIN_VALUE;

    // Touch: which target is under the finger, if any.
    protected int pressedTarget = -1;

    protected PrimeCardRow(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
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

    /** Corner radius for a role in the current design system, in px. {@code pillHeight} for PILL. */
    protected static float radius(org.telegram.ui.Components.design.DesignSystem.Role role, float pillHeight) {
        final float r = org.telegram.ui.Components.design.DesignSystem.current().cornerRadius(role);
        return r == org.telegram.ui.Components.design.DesignSystem.PILL ? pillHeight / 2f : dp(r);
    }

    protected static float radius(org.telegram.ui.Components.design.DesignSystem.Role role) {
        return radius(role, 0);
    }

    /**
     * The design system changed since this row last drew: geometry that depends on it (corner
     * radii, anything cached from them) has to be rebuilt. Called from {@link #onDraw}.
     */
    protected void onDesignChanged() {
        onGeometryChanged();
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
        final float r = radius(org.telegram.ui.Components.design.DesignSystem.Role.LIST_CARD);
        final float top = position == POS_SINGLE || position == POS_TOP ? r : 0;
        final float bottom = position == POS_SINGLE || position == POS_BOTTOM ? r : 0;
        radii[0] = radii[1] = radii[2] = radii[3] = top;
        radii[4] = radii[5] = radii[6] = radii[7] = bottom;
        cardRect.set(cardLeft(), 0, cardRight(), getMeasuredHeight());
        cardPath.addRoundRect(cardRect, radii, Path.Direction.CW);
    }

    @Override
    protected final void onDraw(Canvas canvas) {
        // One int compare per frame: switching the design system rebuilds nothing until it happens.
        final int mode = org.telegram.messenger.PrimeTweaks.designMode();
        if (mode != drawnDesignMode) {
            final boolean first = drawnDesignMode == Integer.MIN_VALUE;
            drawnDesignMode = mode;
            cardPathDirty = true;
            if (!first) {
                onDesignChanged();
            }
        }
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
    //
    // The list owns the gesture. RecyclerListView takes the tap and reports it as a click on the
    // whole row with the touch point; the row then works out which of its targets was hit. Handling
    // touches here as well made two handlers race for the same tap - and the list's one, which only
    // knows the row, won: rows of tiles did nothing, and on a hub page opened a section that does
    // not exist. So this view never consumes a touch; it only remembers where the finger went down,
    // to show the pressed state on the right target.

    private float lastDownX, lastDownY;

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

    /**
     * A click the list delivered at (x, y), in this view's coordinates.
     * @return whether it landed on a target
     */
    public boolean clickAt(float x, float y, boolean longPress) {
        final int target = hitTest(x, y);
        if (target < 0 || longPress && !supportsLongPress(target)) {
            return false;
        }
        setPressedTarget(-1);
        onTargetClick(target, longPress);
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            lastDownX = event.getX();
            lastDownY = event.getY();
        }
        return false;
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        setPressedTarget(pressed ? hitTest(lastDownX, lastDownY) : -1);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        pressedTarget = -1;
    }

    // ---- helpers ---------------------------------------------------------------------------

    protected static int dpi(float v) {
        return AndroidUtilities.dp(v);
    }
}
