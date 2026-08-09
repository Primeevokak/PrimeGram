package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.PrimeOptionCardsCell;
import org.telegram.ui.Components.PrimeSettingsUi;

/**
 * PrimeGram: the concrete option selectors, each drawing its own choice.
 *
 * <p>One class with a mode rather than one class per selector: they differ only in what they
 * paint inside a card, and a file per drawing routine would bury three small paint calls under
 * three sets of layout boilerplate.
 */
public class PrimeShapeOptionsCell extends PrimeOptionCardsCell {

    /** What a double tap on a message does: reaction, reply, nothing. */
    public static final int MODE_DOUBLE_TAP = 0;
    /** Sticker size, drawn as a sticker of that size beside a bubble for scale. */
    public static final int MODE_STICKER_SIZE = 1;
    /** Which service translates: Telegram, Google, Yandex. */
    public static final int MODE_TRANSLATOR = 2;
    /** Ceiling for downloaded video quality. */
    public static final int MODE_VIDEO_QUALITY = 3;
    /** The "Write" FAB: round or square. */
    public static final int MODE_FAB_SHAPE = 4;
    /** Outgoing/incoming bubble corner: with the little tail, or without. */
    public static final int MODE_BUBBLE_TAIL = 5;
    /** Sender's mini avatar next to their name in a group dialog preview: shown or not. */
    public static final int MODE_SENDER_AVATAR = 6;
    /** Chat header title alignment: left (with the logo) or centered. */
    public static final int MODE_HEADER_ALIGN = 7;
    /** Timestamp painted over a sticker/round video: shown or hidden. */
    public static final int MODE_STICKER_TIME = 8;
    /** Which {@link org.telegram.ui.Components.design.DesignSystem} renders the app. */
    public static final int MODE_DESIGN_SYSTEM = 9;

    private final int mode;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmp = new RectF();
    private final int[] values;

    public PrimeShapeOptionsCell(Context context, int mode, CharSequence[] labels, int[] values,
                                 int selected, Theme.ResourcesProvider resourcesProvider) {
        super(context, labels, selected, resourcesProvider);
        this.mode = mode;
        this.values = values;
    }

    /** The stored value behind the selected card, which is rarely the card's index. */
    public int getSelectedValue() {
        final int index = getSelected();
        return values != null && index >= 0 && index < values.length ? values[index] : index;
    }

    @Override
    protected void drawOption(Canvas canvas, RectF bounds, int index, float selection) {
        switch (mode) {
            case MODE_DOUBLE_TAP:
                drawDoubleTap(canvas, bounds, index);
                break;
            case MODE_STICKER_SIZE:
                drawStickerSize(canvas, bounds, index);
                break;
            case MODE_TRANSLATOR:
                drawTranslator(canvas, bounds, index);
                break;
            case MODE_VIDEO_QUALITY:
                drawVideoQuality(canvas, bounds, index);
                break;
            case MODE_FAB_SHAPE:
                drawFabShape(canvas, bounds, index);
                break;
            case MODE_BUBBLE_TAIL:
                drawBubbleTail(canvas, bounds, index);
                break;
            case MODE_SENDER_AVATAR:
                drawSenderAvatar(canvas, bounds, index);
                break;
            case MODE_HEADER_ALIGN:
                drawHeaderAlign(canvas, bounds, index);
                break;
            case MODE_STICKER_TIME:
                drawStickerTime(canvas, bounds, index);
                break;
            case MODE_DESIGN_SYSTEM:
                drawDesignSystem(canvas, bounds, index);
                break;
        }
    }

    /** A little card of its own, drawn with each system's real corner radius - the setting shown by example. */
    private void drawDesignSystem(Canvas canvas, RectF bounds, int index) {
        final org.telegram.ui.Components.design.DesignSystem system = index == 1
                ? org.telegram.ui.Components.design.MaterialDesignSystem.INSTANCE
                : org.telegram.ui.Components.design.FlatDesignSystem.INSTANCE;
        final float r = dp(system.cornerRadius(org.telegram.ui.Components.design.DesignSystem.Role.CARD) * 0.6f);
        final float size = Math.min(bounds.width(), bounds.height()) * 0.7f;
        tmp.set(bounds.centerX() - size / 2, bounds.centerY() - size / 2,
                bounds.centerX() + size / 2, bounds.centerY() + size / 2);
        paint.setColor(PrimeSettingsUi.mockColor(true));
        canvas.drawRoundRect(tmp, r, r, paint);
    }

    /** A little circle standing for the FAB itself, round or square. */
    private void drawFabShape(Canvas canvas, RectF bounds, int index) {
        final float size = Math.min(bounds.width(), bounds.height()) * 0.5f;
        tmp.set(bounds.centerX() - size / 2, bounds.centerY() - size / 2,
                bounds.centerX() + size / 2, bounds.centerY() + size / 2);
        paint.setColor(PrimeSettingsUi.mockColor(true));
        final float radius = index == 0 ? size / 2 : dp(6);
        canvas.drawRoundRect(tmp, radius, radius, paint);
    }

    /** A bubble with or without the little corner tail, the actual shape this setting controls. */
    private void drawBubbleTail(Canvas canvas, RectF bounds, int index) {
        final float bubbleHeight = dp(24);
        final float left = bounds.left + (index == 1 ? 0 : dp(6));
        tmp.set(left, bounds.centerY() - bubbleHeight / 2, bounds.right - dp(6), bounds.centerY() + bubbleHeight / 2);
        paint.setColor(PrimeSettingsUi.mockColor(false));
        canvas.drawRoundRect(tmp, dp(10), dp(10), paint);
        if (index == 0) {
            // The tail: a small triangle at the bottom-left corner of the bubble.
            final android.graphics.Path path = new android.graphics.Path();
            path.moveTo(tmp.left, tmp.bottom - dp(8));
            path.lineTo(tmp.left - dp(6), tmp.bottom);
            path.lineTo(tmp.left, tmp.bottom);
            path.close();
            canvas.drawPath(path, paint);
        }
    }

    /** A small circle (the avatar) beside two lines of "text", present or absent. */
    private void drawSenderAvatar(Canvas canvas, RectF bounds, int index) {
        float textLeft = bounds.left + dp(4);
        if (index == 1) {
            final float r = dp(9);
            paint.setColor(PrimeSettingsUi.mockColor(true));
            canvas.drawCircle(bounds.left + dp(4) + r, bounds.centerY(), r, paint);
            textLeft = bounds.left + dp(4) + r * 2 + dp(6);
        }
        paint.setColor(PrimeSettingsUi.mockColor(false));
        canvas.drawRoundRect(textLeft, bounds.centerY() - dp(7), bounds.right - dp(4), bounds.centerY() - dp(2), dp(2), dp(2), paint);
        canvas.drawRoundRect(textLeft, bounds.centerY() + dp(2), bounds.right - dp(4) - dp(14), bounds.centerY() + dp(7), dp(2), dp(2), paint);
    }

    /** A "logo" square plus a line of "title" text, left-anchored or centered as a group. */
    private void drawHeaderAlign(Canvas canvas, RectF bounds, int index) {
        final float titleWidth = bounds.width() * 0.5f;
        final float logoSize = dp(14);
        final float groupWidth = index == 1 ? titleWidth : logoSize + dp(6) + titleWidth;
        float left = index == 1 ? bounds.centerX() - groupWidth / 2 : bounds.left;
        if (index == 0) {
            paint.setColor(PrimeSettingsUi.mockColor(true));
            canvas.drawRoundRect(left, bounds.centerY() - logoSize / 2, left + logoSize, bounds.centerY() + logoSize / 2, dp(4), dp(4), paint);
            left += logoSize + dp(6);
        }
        paint.setColor(PrimeSettingsUi.mockColor(false));
        canvas.drawRoundRect(left, bounds.centerY() - dp(4), left + titleWidth, bounds.centerY() + dp(4), dp(3), dp(3), paint);
    }

    /** A sticker-shaped square with, or without, a little timestamp pill in its corner. */
    private void drawStickerTime(Canvas canvas, RectF bounds, int index) {
        final float size = Math.min(bounds.width(), bounds.height()) * 0.6f;
        tmp.set(bounds.centerX() - size / 2, bounds.centerY() - size / 2,
                bounds.centerX() + size / 2, bounds.centerY() + size / 2);
        paint.setColor(PrimeSettingsUi.mockColor(false));
        canvas.drawRoundRect(tmp, dp(8), dp(8), paint);
        if (index == 0) {
            paint.setColor(PrimeSettingsUi.mockColor(true));
            canvas.drawRoundRect(tmp.right - dp(20), tmp.bottom - dp(10), tmp.right - dp(2), tmp.bottom - dp(2), dp(4), dp(4), paint);
        }
    }

    /** A bubble, and what appears on it: a heart, a reply arrow, or nothing at all. */
    private void drawDoubleTap(Canvas canvas, RectF bounds, int index) {
        final float bubbleHeight = dp(22);
        tmp.set(bounds.left, bounds.centerY() - bubbleHeight / 2, bounds.right - dp(6), bounds.centerY() + bubbleHeight / 2);
        paint.setColor(PrimeSettingsUi.mockColor(false));
        canvas.drawRoundRect(tmp, dp(8), dp(8), paint);

        paint.setColor(PrimeSettingsUi.mockColor(true));
        if (index == 0) {
            // A reaction sits on the bubble's lower edge, the way a real one does.
            canvas.drawCircle(tmp.right - dp(6), tmp.bottom, dp(6), paint);
        } else if (index == 1) {
            // A reply quote: a vertical bar and two short lines above the bubble.
            canvas.drawRect(bounds.left, bounds.top + dp(2), bounds.left + dp(2), bounds.top + dp(16), paint);
            canvas.drawRect(bounds.left + dp(6), bounds.top + dp(4), bounds.left + dp(26), bounds.top + dp(7), paint);
            canvas.drawRect(bounds.left + dp(6), bounds.top + dp(11), bounds.left + dp(20), bounds.top + dp(14), paint);
        }
    }

    /** A square sticker whose size follows the option, with a bubble beside it for scale. */
    private void drawStickerSize(Canvas canvas, RectF bounds, int index) {
        final float[] fractions = {0.45f, 0.7f, 1f};
        final float fraction = fractions[Math.max(0, Math.min(fractions.length - 1, index))];
        final float size = Math.min(bounds.width(), bounds.height()) * fraction;
        tmp.set(bounds.centerX() - size / 2, bounds.centerY() - size / 2,
                bounds.centerX() + size / 2, bounds.centerY() + size / 2);
        paint.setColor(PrimeSettingsUi.mockColor(true));
        canvas.drawRoundRect(tmp, dp(6), dp(6), paint);
    }

    /** Initial letters, because a translator has no shape to show. */
    private void drawTranslator(Canvas canvas, RectF bounds, int index) {
        final String[] marks = {"TG", "G", "Y"};
        final android.text.TextPaint textPaint = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(dp(22));
        textPaint.setTypeface(AndroidUtilities.bold());
        textPaint.setColor(PrimeSettingsUi.mockColor(true));
        final String mark = marks[Math.max(0, Math.min(marks.length - 1, index))];
        final float width = textPaint.measureText(mark);
        canvas.drawText(mark, bounds.centerX() - width / 2,
                bounds.centerY() + textPaint.getTextSize() / 3, textPaint);
    }

    /** A frame whose height follows the ceiling, so the options read as sizes and not as words. */
    private void drawVideoQuality(Canvas canvas, RectF bounds, int index) {
        final float[] fractions = {1f, 0.82f, 0.64f, 0.46f, 0.32f};
        final float fraction = fractions[Math.max(0, Math.min(fractions.length - 1, index))];
        final float height = bounds.height() * fraction;
        final float width = height * 16f / 9f;
        final float clamped = Math.min(width, bounds.width());
        tmp.set(bounds.centerX() - clamped / 2, bounds.centerY() - height / 2,
                bounds.centerX() + clamped / 2, bounds.centerY() + height / 2);
        paint.setColor(PrimeSettingsUi.mockColor(true));
        canvas.drawRoundRect(tmp, dp(4), dp(4), paint);
    }
}
