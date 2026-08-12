package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.PrimeUiInspector;

import java.util.ArrayList;
import java.util.List;

/**
 * PrimeGram: "Диагностика" debug overlay - draws a frame around every View currently laid out on
 * screen, with its class name (and resource id, if it has one) written next to it. Meant to be
 * flipped on from Settings only while actively hunting a stray/misplaced background or ghost
 * element, so the exact View painting it can be read off directly instead of guessed from a
 * screenshot. Add as the LAST child of the activity's root FrameLayout (so it draws on top of
 * everything, including other overlays) with MATCH_PARENT bounds; it walks its own parent's child
 * tree (skipping itself) rather than needing an explicit root reference.
 *
 * <p>Two things this exists specifically to solve:
 * <ul>
 *   <li>Label overlap: in a busy screen dozens of labels land on top of each other and become
 *   unreadable. Every label's placement is nudged against every label already placed (not just
 *   against "crowded" ones), so no two labels ever visually overlap - see {@link
 *   #findFreeLabelSpot}. A view whose frame itself sits in a crowd of {@link #CROWD_THRESHOLD}+
 *   others additionally gets a distinct, stable (per-View, not flickering) color and its FRAME
 *   nudged too, with a thin leader line back to its real corner, so a single element can be
 *   picked out of an overlapping stack of boxes.</li>
 *   <li>Backgrounds that are not a View: some code (e.g. {@code ChatActivityChannelButtonsLayout})
 *   paints a Drawable straight into {@code drawChild()}/{@code dispatchDraw()}, entirely outside
 *   the View tree - no amount of walking the tree will ever find one of those. Such call sites can
 *   self-report via {@link PrimeUiInspector#recordManualDraw}; this overlay draws them in a fixed,
 *   distinct color (not green, not the crowd palette) so "this one isn't a View" reads at a
 *   glance.</li>
 * </ul>
 *
 * <p>Walking the whole tree, running {@code getGlobalVisibleRect()} (a Matrix.mapRect per
 * ancestor) on every node, and running the label/frame placement search is real work - cheap once,
 * expensive at 60-120 times a second. Confirmed by this exact overlay showing up as the worst
 * frame in {@code PrimePerfMonitor}'s own log on a busy chat screen - the diagnostic tool was
 * measurably distorting the measurement. The full tree walk + placement search only runs every
 * {@link #RECOMPUTE_INTERVAL_MS}; every other frame just redraws the geometry already computed
 * last time, which is cheap (canvas draw calls only, no tree walk, no search).
 */
public class PrimeUiInspectorOverlay extends View implements NotificationCenter.NotificationCenterDelegate {

    /** Colors handed to crowded views. Green is reserved for "not crowded, exactly where it looks";
     *  MANUAL_DRAW_COLOR is reserved for non-View marks - any other color means "this View's frame
     *  got nudged, follow the leader line to its real spot". */
    private static final int[] PALETTE = {
        0xFFFF5555, 0xFFFFAA00, 0xFFFFEE33, 0xFF33CFFF, 0xFFFF66CC, 0xFF66FFCC, 0xFFCC88FF, 0xFFFFFFFF
    };
    private static final int MANUAL_DRAW_COLOR = 0xFFFF00FF;

    private static final float CROWD_RADIUS_DP = 40;
    private static final int CROWD_THRESHOLD = 2;
    /** O(n^2) proximity check below this size is cheap; above it, skip crowd analysis entirely
     *  rather than let one enormous screen tank the frame rate of the very tool measuring it. */
    private static final int MAX_ITEMS_FOR_CROWD_CHECK = 900;
    /** How often the expensive pass (tree walk + placement search) actually runs. A human reading
     *  labels doesn't need 120Hz; ~6/sec is still instant-feeling and cuts the real cost by 10-20x. */
    private static final long RECOMPUTE_INTERVAL_MS = 150;

    private final Paint framePaint = new Paint();
    private final Paint connectorPaint = new Paint();
    private final Paint labelBackgroundPaint = new Paint();
    private final Paint labelTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] selfLoc = new int[2];
    private final Rect tmpVisibleRect = new Rect();
    private final Rect tmpTextBounds = new Rect();
    private long lastRecomputeMs;

    // PrimeGram: a small always-on-top "copy" chip, drawn and hit-tested directly by this overlay
    // rather than routed through Settings - a Settings row can only ever dump whatever screen is
    // on top AT THE MOMENT IT'S PRESSED, which by definition is the Settings screen itself, not the
    // buggy screen the user navigated away from to get there. This chip lets the dump be grabbed
    // without ever leaving the screen that has the actual problem.
    private static final String COPY_CHIP_LABEL = "Скопировать элементы";
    private final Paint chipBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF chipRect = new RectF();
    private boolean chipPressed;

    /** One collected View, plus the geometry the last placement pass decided on - drawn as-is on
     *  frames that don't recompute. */
    private static final class Item {
        View view;
        RectF frame;
        String label;
        float labelWidth, labelHeight;
        RectF drawFrame;
        RectF labelRect;
        int color;
        boolean moved;
        int depth;
    }

    /** Same idea for a non-View manual mark. */
    private static final class ManualItem {
        RectF frame;
        String label;
        RectF labelRect;
    }

    private final List<Item> items = new ArrayList<>();
    private final List<ManualItem> manualItems = new ArrayList<>();
    /** Scratch collision lists, only alive during a recompute pass. */
    private final List<RectF> placedFrames = new ArrayList<>();
    private final List<RectF> placedLabels = new ArrayList<>();

    public PrimeUiInspectorOverlay(Context context) {
        super(context);

        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setStrokeWidth(1);

        connectorPaint.setStyle(Paint.Style.STROKE);
        connectorPaint.setStrokeWidth(1);
        connectorPaint.setColor(0x99FFFFFF);

        labelBackgroundPaint.setStyle(Paint.Style.FILL);
        labelBackgroundPaint.setColor(0xCC000000);

        labelTextPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 9, getResources().getDisplayMetrics()));

        chipBackgroundPaint.setStyle(Paint.Style.FILL);
        chipBackgroundPaint.setColor(0xEE1565C0);
        chipTextPaint.setColor(Color.WHITE);
        chipTextPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13, getResources().getDisplayMetrics()));

        setWillNotDraw(false);
        updateVisibility();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.primeUiInspectorChanged);
        updateVisibility();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.primeUiInspectorChanged);
    }

    /** Plain-text dump of every element the overlay currently sees - real (unnudged) screen rects,
     *  in tree-walk order with indentation so nesting is still legible in text form. Reflects
     *  whatever the last {@link #recompute} pass saw, which is at most {@link #RECOMPUTE_INTERVAL_MS}
     *  stale - fine for a manual "grab it now" action, not meant for anything time-sensitive. */
    private String dumpElements() {
        final StringBuilder sb = new StringBuilder();
        sb.append("PrimeGram UI Inspector — ").append(items.size() + manualItems.size()).append(" элементов\n\n");
        if (!manualItems.isEmpty()) {
            sb.append("-- canvas-painted (не View) --\n");
            for (final ManualItem m : manualItems) {
                appendRect(sb, m.label, m.frame);
            }
            sb.append('\n');
        }
        sb.append("-- дерево View --\n");
        for (final Item item : items) {
            for (int i = 0; i < item.depth; i++) {
                sb.append("  ");
            }
            appendRect(sb, item.label, item.frame);
        }
        return sb.toString();
    }

    private void appendRect(StringBuilder sb, String label, RectF frame) {
        sb.append(label).append("  [")
            .append(Math.round(frame.left)).append(',').append(Math.round(frame.top))
            .append(" - ")
            .append(Math.round(frame.right)).append(',').append(Math.round(frame.bottom))
            .append(']').append('\n');
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.primeUiInspectorChanged) {
            updateVisibility();
        }
    }

    private void updateVisibility() {
        final boolean enabled = PrimeUiInspector.isEnabled();
        setVisibility(enabled ? VISIBLE : GONE);
        if (enabled) {
            lastRecomputeMs = 0;
            invalidate();
        }
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onDraw(Canvas canvas) {
        final Object parent = getParent();
        if (!(parent instanceof ViewGroup)) {
            return;
        }

        final long now = SystemClock.elapsedRealtime();
        if (now - lastRecomputeMs >= RECOMPUTE_INTERVAL_MS) {
            recompute((ViewGroup) parent);
            lastRecomputeMs = now;
        }

        for (final ManualItem m : manualItems) {
            framePaint.setColor(MANUAL_DRAW_COLOR);
            framePaint.setStrokeWidth(dp(2));
            canvas.drawRect(m.frame, framePaint);
            framePaint.setStrokeWidth(1);
            canvas.drawRect(m.labelRect, labelBackgroundPaint);
            labelTextPaint.setColor(MANUAL_DRAW_COLOR);
            canvas.drawText(m.label, m.labelRect.left + 3, m.labelRect.bottom - 4, labelTextPaint);
        }

        for (final Item item : items) {
            framePaint.setColor(item.color);
            canvas.drawRect(item.drawFrame, framePaint);
            if (item.moved) {
                canvas.drawLine(item.frame.left, item.frame.top, item.drawFrame.left, item.drawFrame.top, connectorPaint);
            }
            canvas.drawRect(item.labelRect, labelBackgroundPaint);
            labelTextPaint.setColor(item.color);
            canvas.drawText(item.label, item.labelRect.left + 3, item.labelRect.bottom - 4, labelTextPaint);
        }

        drawCopyChip(canvas);

        // Cheap now (just the draw calls above) - keeps the overlay tracking scroll/animation
        // smoothly between recompute passes without paying the recompute cost every time.
        postInvalidateOnAnimation();
    }

    private void drawCopyChip(Canvas canvas) {
        chipTextPaint.getTextBounds(COPY_CHIP_LABEL, 0, COPY_CHIP_LABEL.length(), tmpTextBounds);
        final float paddingH = dp(14), paddingV = dp(10);
        final float w = tmpTextBounds.width() + paddingH * 2, h = tmpTextBounds.height() + paddingV * 2;
        final float right = getWidth() - dp(16), bottom = getHeight() - dp(120);
        chipRect.set(right - w, bottom - h, right, bottom);

        canvas.drawRoundRect(chipRect, dp(10), dp(10), chipBackgroundPaint);
        canvas.drawText(COPY_CHIP_LABEL, chipRect.left + paddingH, chipRect.bottom - paddingV - tmpTextBounds.bottom, chipTextPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (chipRect.contains(event.getX(), event.getY())) {
                    chipPressed = true;
                    return true;
                }
                return false;
            case MotionEvent.ACTION_UP:
                if (chipPressed) {
                    chipPressed = false;
                    if (chipRect.contains(event.getX(), event.getY())) {
                        copyDumpToClipboard();
                    }
                    return true;
                }
                return false;
            case MotionEvent.ACTION_CANCEL:
                chipPressed = false;
                return false;
            default:
                return chipPressed;
        }
    }

    private void copyDumpToClipboard() {
        final String dump = dumpElements();
        AndroidUtilities.addToClipboard(dump);
        final int lines = items.size() + manualItems.size();
        Toast.makeText(getContext(), "Скопировано: " + lines + " элементов", Toast.LENGTH_SHORT).show();
    }

    /** The expensive pass: walk the View tree, pull in manual marks, and run the placement
     *  search. Only called every {@link #RECOMPUTE_INTERVAL_MS}. */
    @SuppressLint("DrawAllocation")
    private void recompute(ViewGroup root) {
        // getGlobalVisibleRect() below reports screen-absolute coordinates, not window-relative -
        // this has to match, or every rect would be off by the status bar/window inset.
        getLocationOnScreen(selfLoc);

        items.clear();
        placedFrames.clear();
        placedLabels.clear();

        for (int i = 0; i < root.getChildCount(); i++) {
            collect(root.getChildAt(i), 0);
        }

        placeManualMarks();
        placeItems();
    }

    private void collect(View view, int depth) {
        if (view == this || view.getVisibility() == GONE || view.getWidth() <= 0 || view.getHeight() <= 0
                || view.getAlpha() <= 0.01f) {
            return;
        }

        // PrimeGram: getLocationInWindow() + getWidth()/getHeight() gave the view's full,
        // UNCLIPPED bounds - a RecyclerView keeps off-screen children attached (recycling,
        // prefetch), and any view clipped by a scrolling/clipped ancestor still reports its full
        // size that way. That drew boxes for things nobody can see, and for partly-clipped views
        // the box didn't match what was actually visible on screen (reported as a frame that
        // looked bigger/smaller than the real element). getGlobalVisibleRect() returns false for
        // a view with no visible pixels at all (skip it outright) and, when true, the actual
        // visible portion after every ancestor's clip/scroll - exactly what's on screen.
        if (!view.getGlobalVisibleRect(tmpVisibleRect)) {
            if (view instanceof ViewGroup) {
                final ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    collect(group.getChildAt(i), depth + 1);
                }
            }
            return;
        }
        final float left = tmpVisibleRect.left - selfLoc[0];
        final float top = tmpVisibleRect.top - selfLoc[1];

        final Item item = new Item();
        item.view = view;
        item.depth = depth;
        item.frame = new RectF(left, top, left + tmpVisibleRect.width(), top + tmpVisibleRect.height());
        item.label = describe(view);
        labelTextPaint.getTextBounds(item.label, 0, item.label.length(), tmpTextBounds);
        item.labelWidth = tmpTextBounds.width() + 6;
        item.labelHeight = tmpTextBounds.height() + 6;
        items.add(item);

        if (view instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collect(group.getChildAt(i), depth + 1);
            }
        }
    }

    private void placeManualMarks() {
        manualItems.clear();
        for (final PrimeUiInspector.ManualMark mark : PrimeUiInspector.currentManualMarks()) {
            final ManualItem m = new ManualItem();
            m.frame = new RectF(
                mark.screenRect.left - selfLoc[0], mark.screenRect.top - selfLoc[1],
                mark.screenRect.right - selfLoc[0], mark.screenRect.bottom - selfLoc[1]
            );
            m.label = mark.label;

            labelTextPaint.getTextBounds(m.label, 0, m.label.length(), tmpTextBounds);
            final float labelWidth = tmpTextBounds.width() + 6, labelHeight = tmpTextBounds.height() + 6;
            m.labelRect = findFreeLabelSpot(new RectF(m.frame.left, m.frame.top - labelHeight, m.frame.left + labelWidth, m.frame.top), labelWidth, labelHeight);

            placedFrames.add(m.frame);
            placedLabels.add(m.labelRect);
            manualItems.add(m);
        }
    }

    private void placeItems() {
        final boolean checkCrowding = items.size() <= MAX_ITEMS_FOR_CROWD_CHECK;
        final float crowdRadiusPx = dp((int) CROWD_RADIUS_DP);

        for (final Item item : items) {
            final boolean crowded = checkCrowding && countNearby(item, crowdRadiusPx) >= CROWD_THRESHOLD;
            item.color = crowded ? PALETTE[Math.abs(System.identityHashCode(item.view)) % PALETTE.length] : Color.GREEN;
            item.drawFrame = crowded ? findFreeFrameSpot(item) : item.frame;
            item.moved = item.drawFrame != item.frame;

            final float defaultLabelTop = item.drawFrame.top >= item.labelHeight ? item.drawFrame.top - item.labelHeight : item.drawFrame.top;
            final RectF defaultLabelRect = new RectF(item.drawFrame.left, defaultLabelTop, item.drawFrame.left + item.labelWidth, defaultLabelTop + item.labelHeight);
            item.labelRect = findFreeLabelSpot(defaultLabelRect, item.labelWidth, item.labelHeight);

            placedFrames.add(item.drawFrame);
            placedLabels.add(item.labelRect);
        }
    }

    private int countNearby(Item item, float radiusPx) {
        int count = 0;
        final float ax = item.frame.left, ay = item.frame.top;
        for (final Item other : items) {
            if (other == item) {
                continue;
            }
            final float dx = other.frame.left - ax;
            final float dy = other.frame.top - ay;
            if (dx * dx + dy * dy <= radiusPx * radiusPx) {
                count++;
            }
        }
        return count;
    }

    /** Nudges a crowded item's frame toward whichever direction (up/down/left/right, growing
     *  steps) doesn't already collide with a frame placed earlier this pass. Falls back to the
     *  original spot if nothing opens up within a reasonable search. */
    private RectF findFreeFrameSpot(Item item) {
        final float w = item.frame.width(), h = item.frame.height();
        final float step = dp(14);
        final float[][] directions = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
        for (int s = 1; s <= 18; s++) {
            for (final float[] dir : directions) {
                final float dx = dir[0] * step * s;
                final float dy = dir[1] * step * s;
                final RectF candidate = new RectF(
                    item.frame.left + dx, item.frame.top + dy,
                    item.frame.left + dx + w, item.frame.top + dy + h
                );
                if (!collides(candidate, placedFrames)) {
                    return candidate;
                }
            }
        }
        return item.frame;
    }

    /** Same idea as {@link #findFreeFrameSpot} but for a label, checked ONLY against other
     *  labels placed earlier this pass - every label goes through this, not just crowded ones,
     *  which is what actually keeps text from stacking into an unreadable pile. Searches upward
     *  first (labels default to sitting above their frame), then down, then sideways. */
    private RectF findFreeLabelSpot(RectF desired, float w, float h) {
        if (!collides(desired, placedLabels)) {
            return desired;
        }
        final float step = dp(12);
        final float[][] directions = {{0, -1}, {0, 1}, {1, 0}, {-1, 0}};
        for (int s = 1; s <= 24; s++) {
            for (final float[] dir : directions) {
                final float dx = dir[0] * step * s;
                final float dy = dir[1] * step * s;
                final RectF candidate = new RectF(
                    desired.left + dx, desired.top + dy,
                    desired.left + dx + w, desired.top + dy + h
                );
                if (!collides(candidate, placedLabels)) {
                    return candidate;
                }
            }
        }
        return desired;
    }

    private boolean collides(RectF candidate, List<RectF> against) {
        for (final RectF r : against) {
            if (RectF.intersects(r, candidate)) {
                return true;
            }
        }
        return false;
    }

    private String describe(View view) {
        final StringBuilder sb = new StringBuilder(view.getClass().getSimpleName());
        final int id = view.getId();
        if (id != View.NO_ID) {
            try {
                final Resources res = view.getResources();
                sb.append(" #").append(res.getResourceEntryName(id));
            } catch (Resources.NotFoundException ignored) {
                sb.append(" #").append(id);
            }
        }
        return sb.toString();
    }
}
