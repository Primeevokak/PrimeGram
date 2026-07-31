package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.PrimeToolbarSettings;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.List;

/**
 * PrimeGram: the formatting toolbar, edited by moving the actual buttons.
 *
 * <p>The bar at the top is drawn the way the real one is drawn - same pill, same order, same
 * glyphs - so the thing being arranged and the thing being previewed are one object rather than a
 * list of names standing in for it. Below it sit the buttons that have been taken off.
 *
 * <p>Two gestures, and no mode to be in: drag a button anywhere to place it, or tap it to send it
 * to the other half. Tapping is there because for eight of the ten buttons the only decision is
 * "on or off", and making somebody drag for that would be ceremony.
 */
public class PrimeToolbarEditor extends View {

    private static final int BUTTON = 40;
    private static final int BAR_HEIGHT = 44;
    private static final int GAP = 6;

    private final Theme.ResourcesProvider resourcesProvider;

    private final TextPaint letterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint captionPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint slotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private final List<String> shown = new ArrayList<>();
    private final List<String> hidden = new ArrayList<>();
    private final java.util.HashMap<String, Drawable> icons = new java.util.HashMap<>();

    /** Where each button is drawn, recomputed on every layout and every change. */
    private final java.util.HashMap<String, float[]> positions = new java.util.HashMap<>();

    private String dragging;
    private float dragX, dragY, downX, downY;
    private boolean dragStarted;
    private int barRows = 1, hiddenRows = 1;
    private Runnable onChange;

    public PrimeToolbarEditor(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        letterPaint.setTextSize(AndroidUtilities.dp(17));
        letterPaint.setTextAlign(Paint.Align.CENTER);
        captionPaint.setTextSize(AndroidUtilities.dp(13));
        slotPaint.setStyle(Paint.Style.STROKE);
        slotPaint.setStrokeWidth(AndroidUtilities.dp(1.5f));
        slotPaint.setPathEffect(new android.graphics.DashPathEffect(
                new float[]{AndroidUtilities.dp(4), AndroidUtilities.dp(4)}, 0));
        shadowPaint.setColor(0x22000000);

        shown.addAll(PrimeToolbarSettings.items());
        hidden.addAll(PrimeToolbarSettings.hiddenItems());
        for (String id : PrimeToolbarSettings.ALL) {
            final int res = PrimeToolbarSettings.icon(id);
            if (res != 0) {
                final Drawable drawable = context.getResources().getDrawable(res).mutate();
                icons.put(id, drawable);
            }
        }
    }

    public void setOnChange(Runnable listener) {
        onChange = listener;
    }

    public List<String> getItems() {
        return new ArrayList<>(shown);
    }

    public void resetToDefaults() {
        shown.clear();
        hidden.clear();
        shown.addAll(java.util.Arrays.asList(PrimeToolbarSettings.ALL));
        layoutButtons();
        notifyChanged();
        invalidate();
    }

    private void notifyChanged() {
        if (onChange != null) {
            onChange.run();
        }
    }

    private int color(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    // ── layout ────────────────────────────────────────────────────────────────

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(width, AndroidUtilities.dp(
                BAR_HEIGHT * barRows + 34 + BUTTON * hiddenRows + 32));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        layoutButtons();
    }

    private int perRow() {
        final int usable = getWidth() - AndroidUtilities.dp(32);
        return Math.max(1, (usable + AndroidUtilities.dp(GAP)) / AndroidUtilities.dp(BUTTON + GAP));
    }

    /**
     * Works out where every button sits. Both halves wrap rather than scroll: the real toolbar
     * scrolls sideways, but an editor whose contents can be off-screen is an editor where a button
     * can be lost, and the whole set is only ten.
     */
    private void layoutButtons() {
        if (getWidth() == 0) {
            return;
        }
        positions.clear();
        final int columns = perRow();
        final float step = AndroidUtilities.dp(BUTTON + GAP);

        barRows = Math.max(1, (shown.size() + columns - 1) / columns);
        for (int i = 0; i < shown.size(); i++) {
            final int row = i / columns, column = i % columns;
            final int inRow = Math.min(columns, shown.size() - row * columns);
            final float rowWidth = inRow * step - AndroidUtilities.dp(GAP);
            final float startX = (getWidth() - rowWidth) / 2f;
            positions.put(shown.get(i), new float[]{
                    startX + column * step,
                    AndroidUtilities.dp(2 + BAR_HEIGHT * row + (BAR_HEIGHT - BUTTON) / 2f)});
        }

        final float hiddenTop = AndroidUtilities.dp(BAR_HEIGHT * barRows + 34);
        hiddenRows = Math.max(1, (hidden.size() + columns - 1) / columns);
        for (int i = 0; i < hidden.size(); i++) {
            final int row = i / columns, column = i % columns;
            final int inRow = Math.min(columns, hidden.size() - row * columns);
            final float rowWidth = inRow * step - AndroidUtilities.dp(GAP);
            final float startX = (getWidth() - rowWidth) / 2f;
            positions.put(hidden.get(i), new float[]{
                    startX + column * step, hiddenTop + row * step});
        }
    }

    private float barBottom() {
        return AndroidUtilities.dp(BAR_HEIGHT * barRows + 4);
    }

    // ── drawing ───────────────────────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        if (positions.isEmpty()) {
            layoutButtons();
        }
        final int size = AndroidUtilities.dp(BUTTON);

        // The bar itself: the same pill the chat draws, so what is being arranged looks like what
        // will appear above the keyboard.
        barPaint.setColor(color(Theme.key_chat_messagePanelBackground));
        rect.set(AndroidUtilities.dp(12), AndroidUtilities.dp(2),
                getWidth() - AndroidUtilities.dp(12), barBottom());
        canvas.drawRoundRect(rect, AndroidUtilities.dp(BAR_HEIGHT / 2f),
                AndroidUtilities.dp(BAR_HEIGHT / 2f), barPaint);
        barPaint.setColor(color(Theme.key_chat_messagePanelShadow));
        barPaint.setStyle(Paint.Style.STROKE);
        barPaint.setStrokeWidth(1);
        canvas.drawRoundRect(rect, AndroidUtilities.dp(BAR_HEIGHT / 2f),
                AndroidUtilities.dp(BAR_HEIGHT / 2f), barPaint);
        barPaint.setStyle(Paint.Style.FILL);

        if (shown.isEmpty()) {
            captionPaint.setColor(color(Theme.key_windowBackgroundWhiteGrayText));
            captionPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("Перетащите кнопки сюда", getWidth() / 2f,
                    barBottom() / 2f + AndroidUtilities.dp(5), captionPaint);
            captionPaint.setTextAlign(Paint.Align.LEFT);
        }

        captionPaint.setColor(color(Theme.key_windowBackgroundWhiteGrayText));
        canvas.drawText(hidden.isEmpty() ? "Все кнопки на панели" : "Скрытые",
                AndroidUtilities.dp(21), AndroidUtilities.dp(BAR_HEIGHT * barRows + 24), captionPaint);

        for (String id : hidden) {
            if (!id.equals(dragging)) {
                drawButton(canvas, id, positions.get(id), size, false, 1f);
            }
        }
        for (String id : shown) {
            if (!id.equals(dragging)) {
                drawButton(canvas, id, positions.get(id), size, true, 1f);
            }
        }

        if (dragging != null && dragStarted) {
            // Drawn last and slightly larger, so it is unambiguously the thing under the finger.
            final float half = size * 0.6f;
            canvas.drawCircle(dragX, dragY + AndroidUtilities.dp(2), half + AndroidUtilities.dp(1), shadowPaint);
            drawButtonAt(canvas, dragging, dragX - half, dragY - half, (int) (half * 2),
                    dragY < barBottom(), 1f);
        }
    }

    private void drawButton(Canvas canvas, String id, float[] position, int size, boolean active, float alpha) {
        if (position == null) {
            return;
        }
        drawButtonAt(canvas, id, position[0], position[1], size, active, alpha);
    }

    private void drawButtonAt(Canvas canvas, String id, float x, float y, int size, boolean active, float alpha) {
        final int tint = color(active
                ? Theme.key_chat_messagePanelIcons : Theme.key_windowBackgroundWhiteGrayText);

        if (!active) {
            // A dashed outline for the shelf below, so a hidden button reads as a slot to pick
            // from rather than as a button that simply looks paler today.
            slotPaint.setColor(color(Theme.key_windowBackgroundWhiteGrayText));
            slotPaint.setAlpha(70);
            rect.set(x, y, x + size, y + size);
            canvas.drawRoundRect(rect, size / 2f, size / 2f, slotPaint);
        }

        final String letter = PrimeToolbarSettings.letter(id);
        if (letter != null) {
            letterPaint.setColor(tint);
            letterPaint.setAlpha((int) (255 * alpha));
            letterPaint.setUnderlineText("U".equals(letter));
            letterPaint.setStrikeThruText("S".equals(letter));
            if ("B".equals(letter)) {
                letterPaint.setTypeface(AndroidUtilities.bold());
            } else if ("I".equals(letter)) {
                letterPaint.setTypeface(Typeface.defaultFromStyle(Typeface.ITALIC));
            } else if ("M".equals(letter)) {
                letterPaint.setTypeface(Typeface.MONOSPACE);
            } else {
                letterPaint.setTypeface(null);
            }
            canvas.drawText(letter, x + size / 2f, y + size / 2f + AndroidUtilities.dp(6), letterPaint);
            letterPaint.setUnderlineText(false);
            letterPaint.setStrikeThruText(false);
        } else {
            final Drawable icon = icons.get(id);
            if (icon != null) {
                icon.setColorFilter(new PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN));
                icon.setAlpha((int) (255 * alpha));
                final int inset = (int) (size * 0.24f);
                icon.setBounds((int) x + inset, (int) y + inset,
                        (int) x + size - inset, (int) y + size - inset);
                icon.draw(canvas);
            }
        }
    }

    // ── touch ─────────────────────────────────────────────────────────────────

    private String buttonAt(float x, float y) {
        final int size = AndroidUtilities.dp(BUTTON);
        for (java.util.Map.Entry<String, float[]> entry : positions.entrySet()) {
            final float[] position = entry.getValue();
            if (x >= position[0] - AndroidUtilities.dp(3) && x <= position[0] + size + AndroidUtilities.dp(3)
                    && y >= position[1] - AndroidUtilities.dp(3) && y <= position[1] + size + AndroidUtilities.dp(3)) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final float x = event.getX(), y = event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                dragging = buttonAt(x, y);
                downX = x;
                downY = y;
                dragX = x;
                dragY = y;
                dragStarted = false;
                if (dragging != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return dragging != null;

            case MotionEvent.ACTION_MOVE:
                if (dragging == null) {
                    return false;
                }
                dragX = x;
                dragY = y;
                if (!dragStarted && Math.hypot(x - downX, y - downY) > AndroidUtilities.dp(8)) {
                    dragStarted = true;
                    AndroidUtilities.vibrateCursor(this);
                }
                if (dragStarted) {
                    moveDragged(x, y);
                }
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging != null) {
                    if (!dragStarted) {
                        toggle(dragging);
                    }
                    dragging = null;
                    dragStarted = false;
                    layoutButtons();
                    notifyChanged();
                    invalidate();
                }
                return true;
        }
        return false;
    }

    /** Places the dragged button where the finger is, live, so the row parts around it. */
    private void moveDragged(float x, float y) {
        final boolean intoBar = y < barBottom();
        shown.remove(dragging);
        hidden.remove(dragging);
        // Clamped on both sides: the index is worked out from a finger position that can be past
        // the end of the row, and an out-of-range insert would take the whole editor down.
        final int index = insertionIndex(x, y);
        if (intoBar) {
            shown.add(Math.min(shown.size(), index), dragging);
        } else {
            hidden.add(Math.min(hidden.size(), index), dragging);
        }
        layoutButtons();
    }

    private int insertionIndex(float x, float y) {
        final int columns = perRow();
        final float step = AndroidUtilities.dp(BUTTON + GAP);
        final float top = y < barBottom() ? 0 : AndroidUtilities.dp(BAR_HEIGHT * barRows + 34);
        final int row = Math.max(0, (int) ((y - top) / (y < barBottom() ? AndroidUtilities.dp(BAR_HEIGHT) : step)));
        final int column = Math.max(0, Math.round((x - AndroidUtilities.dp(16)) / step));
        return Math.max(0, row * columns + column);
    }

    private void toggle(String id) {
        if (shown.remove(id)) {
            hidden.add(id);
        } else {
            hidden.remove(id);
            shown.add(id);
        }
        AndroidUtilities.vibrateCursor(this);
    }
}
