package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.PrimeSidebarZone;
import org.telegram.ui.ActionBar.Theme;

/**
 * PrimeGram: the little phone you drag the sidebar's activation zone around on.
 *
 * <p>The setting is spatial - "start the swipe about here" - and a spatial setting explained in
 * percentages is a setting nobody touches. So it is shown as what it is: a phone with a faint chat
 * list on it and a coloured band over the part of the screen that listens. Drag the band to move
 * it, drag the handle on an edge to resize.
 *
 * <p>Nothing is written to preferences from here - the view only holds the numbers and reports that
 * they moved. Saving is the sheet's business, once, when it closes: a drag is dozens of updates and
 * none of them is worth a write.
 */
public class PrimeSidebarZoneEditor extends View {

    /** Roughly a modern phone; the exact number matters less than not looking like a tablet. */
    private static final float ASPECT = 19.5f / 9f;

    private static final int MODE_NONE = 0;
    private static final int MODE_MOVE = 1;
    private static final int MODE_TOP = 2;
    private static final int MODE_BOTTOM = 3;
    private static final int MODE_WIDTH = 4;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF phone = new RectF();

    private final Theme.ResourcesProvider resourcesProvider;

    private float width = PrimeSidebarZone.width();
    private float top = PrimeSidebarZone.top();
    private float bottom = PrimeSidebarZone.bottom();

    private int mode = MODE_NONE;
    private float grabOffset;
    private Runnable onChange;

    public PrimeSidebarZoneEditor(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        strokePaint.setStyle(Paint.Style.STROKE);
    }

    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    public float getZoneWidth() {
        return width;
    }

    public float getZoneTop() {
        return top;
    }

    public float getZoneBottom() {
        return bottom;
    }

    public void resetToDefaults() {
        width = PrimeSidebarZone.DEFAULT_WIDTH;
        top = PrimeSidebarZone.DEFAULT_TOP;
        bottom = PrimeSidebarZone.DEFAULT_BOTTOM;
        invalidate();
        if (onChange != null) {
            onChange.run();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        final int h = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h);
    }

    private void layoutPhone() {
        // Insets leave room for the handles, which sit on the outline and would otherwise be
        // clipped by the view's own bounds.
        final float pad = dp(14);
        final float availableH = getMeasuredHeight() - pad * 2;
        final float availableW = getMeasuredWidth() - pad * 2;
        float h = availableH;
        float w = h / ASPECT;
        if (w > availableW) {
            w = availableW;
            h = w * ASPECT;
        }
        final float cx = getMeasuredWidth() / 2f;
        final float cy = getMeasuredHeight() / 2f;
        phone.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        layoutPhone();

        final int accent = Theme.getColor(Theme.key_windowBackgroundWhiteValueText, resourcesProvider);
        final int screen = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
        final int faint = Theme.getColor(Theme.key_windowBackgroundGrayShadow, resourcesProvider);
        final float radius = dp(16);

        paint.setColor(screen);
        canvas.drawRoundRect(phone, radius, radius, paint);

        canvas.save();
        rect.set(phone);
        canvas.clipRect(rect);
        drawFauxChatList(canvas, faint);
        canvas.restore();

        // The zone. Flush to the left edge because that is where it really starts; only the far
        // side is rounded, which is also the side you are allowed to drag.
        final float zoneRight = phone.left + phone.width() * width;
        final float zoneTop = phone.top + phone.height() * top;
        final float zoneBottom = phone.top + phone.height() * bottom;
        rect.set(phone.left, zoneTop, zoneRight, zoneBottom);
        paint.setColor(ColorUtils.setAlphaComponent(accent, 60));
        canvas.save();
        canvas.clipRect(phone);
        canvas.drawRoundRect(rect, dp(8), dp(8), paint);
        strokePaint.setStrokeWidth(dp(1.5f));
        strokePaint.setColor(ColorUtils.setAlphaComponent(accent, 200));
        canvas.drawRoundRect(rect, dp(8), dp(8), strokePaint);
        canvas.restore();

        // Outline last, over the zone, so the phone still reads as one object.
        strokePaint.setStrokeWidth(dp(1.5f));
        strokePaint.setColor(Theme.getColor(Theme.key_divider, resourcesProvider));
        rect.set(phone);
        rect.inset(dp(0.75f), dp(0.75f));
        canvas.drawRoundRect(rect, radius, radius, strokePaint);

        final float zoneCenterX = (phone.left + zoneRight) / 2f;
        drawHandle(canvas, zoneCenterX, zoneTop, true, accent, screen);
        drawHandle(canvas, zoneCenterX, zoneBottom, true, accent, screen);
        drawHandle(canvas, zoneRight, (zoneTop + zoneBottom) / 2f, false, accent, screen);
    }

    /**
     * Something for the zone to sit on top of. Without it the band floats in an empty rectangle and
     * there is no telling how big it is.
     */
    private void drawFauxChatList(Canvas canvas, int color) {
        paint.setColor(ColorUtils.setAlphaComponent(color, 90));
        final float rowH = phone.height() * 0.082f;
        final float avatarR = rowH * 0.30f;
        final float left = phone.left + phone.width() * 0.09f;
        float y = phone.top + phone.height() * 0.12f;
        while (y < phone.bottom) {
            canvas.drawCircle(left + avatarR, y + rowH / 2f, avatarR, paint);
            final float textLeft = left + avatarR * 2 + phone.width() * 0.06f;
            rect.set(textLeft, y + rowH * 0.28f, phone.right - phone.width() * 0.14f, y + rowH * 0.40f);
            canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, paint);
            rect.set(textLeft, y + rowH * 0.52f, phone.right - phone.width() * 0.30f, y + rowH * 0.62f);
            canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, paint);
            y += rowH;
        }
    }

    private void drawHandle(Canvas canvas, float cx, float cy, boolean horizontal, int accent, int screen) {
        final float r = dp(9);
        paint.setColor(screen);
        canvas.drawCircle(cx, cy, r, paint);
        strokePaint.setStrokeWidth(dp(1.5f));
        strokePaint.setColor(accent);
        canvas.drawCircle(cx, cy, r, strokePaint);
        paint.setColor(accent);
        final float halfLong = dp(3.5f), halfShort = dp(0.9f);
        if (horizontal) {
            rect.set(cx - halfLong, cy - halfShort, cx + halfLong, cy + halfShort);
        } else {
            rect.set(cx - halfShort, cy - halfLong, cx + halfShort, cy + halfLong);
        }
        canvas.drawRoundRect(rect, halfShort, halfShort, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        layoutPhone();
        final float x = event.getX(), y = event.getY();
        final float zoneRight = phone.left + phone.width() * width;
        final float zoneTop = phone.top + phone.height() * top;
        final float zoneBottom = phone.top + phone.height() * bottom;
        final float zoneCenterX = (phone.left + zoneRight) / 2f;
        final float slop = dp(26);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                if (near(x, y, zoneCenterX, zoneTop, slop)) {
                    mode = MODE_TOP;
                    grabOffset = y - zoneTop;
                } else if (near(x, y, zoneCenterX, zoneBottom, slop)) {
                    mode = MODE_BOTTOM;
                    grabOffset = y - zoneBottom;
                } else if (near(x, y, zoneRight, (zoneTop + zoneBottom) / 2f, slop)) {
                    mode = MODE_WIDTH;
                    grabOffset = x - zoneRight;
                } else if (x >= phone.left && x <= zoneRight && y >= zoneTop && y <= zoneBottom) {
                    mode = MODE_MOVE;
                    grabOffset = y - zoneTop;
                } else {
                    mode = MODE_NONE;
                    return false;
                }
                getParent().requestDisallowInterceptTouchEvent(true);
                AndroidUtilities.vibrateCursor(this);
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (mode == MODE_NONE) {
                    return false;
                }
                final float fy = (y - grabOffset - phone.top) / phone.height();
                switch (mode) {
                    case MODE_TOP:
                        top = clamp(fy, 0f, bottom - PrimeSidebarZone.MIN_HEIGHT);
                        break;
                    case MODE_BOTTOM:
                        bottom = clamp((y - grabOffset - phone.top) / phone.height(),
                                top + PrimeSidebarZone.MIN_HEIGHT, 1f);
                        break;
                    case MODE_WIDTH:
                        width = clamp((x - grabOffset - phone.left) / phone.width(),
                                PrimeSidebarZone.MIN_WIDTH, PrimeSidebarZone.MAX_WIDTH);
                        break;
                    case MODE_MOVE: {
                        final float height = bottom - top;
                        top = clamp(fy, 0f, 1f - height);
                        bottom = top + height;
                        break;
                    }
                }
                invalidate();
                if (onChange != null) {
                    onChange.run();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mode = MODE_NONE;
                return true;
        }
        return mode != MODE_NONE;
    }

    private static boolean near(float x, float y, float cx, float cy, float slop) {
        return Math.abs(x - cx) < slop && Math.abs(y - cy) < slop;
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }
}
