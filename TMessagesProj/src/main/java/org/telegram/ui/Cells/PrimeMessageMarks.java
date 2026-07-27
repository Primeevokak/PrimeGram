package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;

import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessageTagsStore;
import org.telegram.ui.ActionBar.Theme;

/**
 * PrimeGram: the two client-only marks drawn on top of a message bubble — a local tag chip
 * and a "deleted by sender" chip.
 *
 * <p>Deliberately kept out of {@link ChatMessageCell}'s layout: these marks are painted over
 * the finished cell and take part in no measurement, so they cannot shift a bubble, break a
 * grouped-media position, or interfere with the cell's transition animations. The cost is
 * that they can overlap content on very narrow bubbles, which is why they are anchored to the
 * free margin beside the bubble whenever one exists.
 */
public class PrimeMessageMarks {

    /** How much of the original message shows through once the sender deleted it. */
    public static final float DELETED_ALPHA = 0.55f;

    private static final int CHIP_HEIGHT_DP = 16;
    private static final int CHIP_PADDING_DP = 6;
    private static final int GAP_DP = 4;
    /** Chips wider than this get ellipsized rather than run across the screen. */
    private static final int MAX_CHIP_WIDTH_DP = 120;

    private static TextPaint textPaint;
    private static Paint backgroundPaint;
    private static final RectF rect = new RectF();

    private static void ensurePaints() {
        if (textPaint == null) {
            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(dp(10));
            textPaint.setTypeface(org.telegram.messenger.AndroidUtilities.bold());
        }
        if (backgroundPaint == null) {
            backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        }
    }

    /** True when the whole cell should be painted faded because the sender deleted it. */
    public static boolean shouldDim(MessageObject messageObject) {
        return messageObject != null && messageObject.primeDeleted;
    }

    /**
     * Paints the marks for one cell. Called after the cell has drawn itself, so anything here
     * lands on top. Never throws: a failure to draw a decoration must not take down a chat.
     */
    public static void draw(Canvas canvas, ChatMessageCell cell, MessageObject messageObject) {
        if (messageObject == null) {
            return;
        }
        try {
            String tagLabel = MessageTagsStore.hasAny()
                    ? MessageTagsStore.getLabelFor(messageObject.getDialogId(), messageObject.getId())
                    : null;
            drawOnlineDot(canvas, cell, messageObject);

            boolean deleted = messageObject.primeDeleted;
            if (!deleted && TextUtils.isEmpty(tagLabel)) {
                return;
            }
            ensurePaints();

            final boolean out = messageObject.isOutOwner();
            final int bubbleLeft = cell.getBackgroundDrawableLeft();
            final int bubbleRight = cell.getBackgroundDrawableRight();
            final int chipHeight = dp(CHIP_HEIGHT_DP);
            float y = cell.getBackgroundDrawableTop() + dp(2);
            // Keep the chips on screen for bubbles that start at the very top of the cell.
            if (y < dp(1)) {
                y = dp(1);
            }

            if (deleted) {
                // Neutral grey, so it stays distinguishable from the red tag chip when a
                // deleted message also carries one.
                y = drawChip(canvas, cell, "удалено", Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), out, bubbleLeft, bubbleRight, y, chipHeight);
            }
            if (!TextUtils.isEmpty(tagLabel)) {
                drawChip(canvas, cell, tagLabel, Theme.getColor(Theme.key_text_RedBold), out, bubbleLeft, bubbleRight, y, chipHeight);
            }
        } catch (Throwable ignore) {
        }
    }

    public static final String ONLINE_DOTS_KEY = "primegram_online_dots";

    private static Paint onlineDotPaint;
    private static Paint onlineDotStrokePaint;

    public static boolean isOnlineDotsEnabled() {
        try {
            return org.telegram.messenger.MessagesController.getGlobalMainSettings()
                    .getBoolean(ONLINE_DOTS_KEY, true);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * The green dot next to a sender's avatar inside a group. The dialog list has had these
     * upstream for years; group chats never did, which is where they are actually useful —
     * you can see who is around before writing.
     *
     * <p>Drawn here rather than in the cell for the same reason as the chips: it must not
     * participate in measurement. The dot sits on the avatar's bottom-right corner with a thin
     * outline, so it reads on any wallpaper without needing to know the background colour.
     */
    private static void drawOnlineDot(Canvas canvas, ChatMessageCell cell, MessageObject messageObject) {
        if (!cell.isAvatarVisible || !isOnlineDotsEnabled()) {
            return;
        }
        org.telegram.tgnet.TLRPC.User user = senderUser(messageObject);
        if (user == null || user.bot || user.self || !isUserOnline(messageObject.currentAccount, user)) {
            return;
        }
        org.telegram.messenger.ImageReceiver avatar = cell.getAvatarImage();
        if (avatar == null || avatar.getImageWidth() <= 0) {
            return;
        }
        if (onlineDotPaint == null) {
            onlineDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            onlineDotStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            onlineDotStrokePaint.setStyle(Paint.Style.STROKE);
            onlineDotStrokePaint.setStrokeWidth(dp(1.5f));
        }
        final float radius = dp(3.5f);
        final float cx = avatar.getImageX() + avatar.getImageWidth() - radius - dp(0.5f);
        final float cy = avatar.getImageY() + avatar.getImageHeight() - radius - dp(0.5f);

        onlineDotStrokePaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        canvas.drawCircle(cx, cy, radius, onlineDotStrokePaint);
        onlineDotPaint.setColor(Theme.getColor(Theme.key_chats_onlineCircle));
        canvas.drawCircle(cx, cy, radius, onlineDotPaint);
    }

    private static org.telegram.tgnet.TLRPC.User senderUser(MessageObject messageObject) {
        if (messageObject.messageOwner == null || messageObject.messageOwner.from_id == null) {
            return null;
        }
        long userId = messageObject.messageOwner.from_id.user_id;
        if (userId == 0) {
            return null;
        }
        return org.telegram.messenger.MessagesController.getInstance(messageObject.currentAccount).getUser(userId);
    }

    /** Same rule the dialog list uses, so the two never disagree about who is online. */
    private static boolean isUserOnline(int account, org.telegram.tgnet.TLRPC.User user) {
        if (user.status == null) {
            return false;
        }
        if (user.status.expires <= 0) {
            return org.telegram.messenger.MessagesController.getInstance(account).onlinePrivacy.containsKey(user.id);
        }
        return user.status.expires > org.telegram.tgnet.ConnectionsManager.getInstance(account).getCurrentTime();
    }

    /** @return the y for the next chip below this one */
    private static float drawChip(Canvas canvas, ChatMessageCell cell, String label, int color, boolean out, int bubbleLeft, int bubbleRight, float y, int chipHeight) {
        final int padding = dp(CHIP_PADDING_DP);
        CharSequence text = TextUtils.ellipsize(label, textPaint, dp(MAX_CHIP_WIDTH_DP) - padding * 2, TextUtils.TruncateAt.END);
        float textWidth = textPaint.measureText(text, 0, text.length());
        float chipWidth = textWidth + padding * 2;

        // Preferred spot is the empty margin beside the bubble; fall back to inside its top
        // corner when the bubble is too wide to leave room.
        float x;
        if (out) {
            x = bubbleLeft - dp(GAP_DP) - chipWidth;
            if (x < dp(2)) {
                x = bubbleLeft + dp(GAP_DP);
            }
        } else {
            x = bubbleRight + dp(GAP_DP);
            if (x + chipWidth > cell.getMeasuredWidth() - dp(2)) {
                x = bubbleRight - dp(GAP_DP) - chipWidth;
            }
        }
        if (x < 0) {
            x = 0;
        }

        rect.set(x, y, x + chipWidth, y + chipHeight);
        backgroundPaint.setColor(color);
        backgroundPaint.setAlpha(0x3D);
        canvas.drawRoundRect(rect, chipHeight / 2f, chipHeight / 2f, backgroundPaint);

        textPaint.setColor(color);
        float baseline = y + chipHeight / 2f - (textPaint.descent() + textPaint.ascent()) / 2f;
        canvas.drawText(text, 0, text.length(), x + padding, baseline, textPaint);

        return y + chipHeight + dp(2);
    }
}
