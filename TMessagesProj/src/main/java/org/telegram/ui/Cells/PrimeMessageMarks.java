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
            // The online dot is NOT drawn here - see drawOnlineDotFor. cell.getAvatarImage()
            // returns the same ImageReceiver ChatActivity positions and draws itself, from its
            // own RecyclerView-level override of the list's dispatchDraw (child.getY(), not
            // this cell's local canvas). Reading its coordinates from inside the cell's own
            // onDraw put the dot at that receiver's list-relative position while everything
            // else in this method draws in cell-local space - the two only agreed by accident
            // for a cell sitting at y=0, which is why the dot floated free of the avatar for
            // every other one.

            // Asked only when there is any tag at all: getLabelFor takes a lock and boxes the
            // dialog id, and this runs for every visible cell on every frame.
            String tagLabel = MessageTagsStore.hasAny()
                    ? MessageTagsStore.getLabelFor(messageObject.getDialogId(), messageObject.getId())
                    : null;

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

    /**
     * Cached, because this is read once per visible cell per frame from the draw path. Reaching
     * into SharedPreferences there means taking its lock roughly a thousand times a second while
     * scrolling, for a value that changes when the user opens settings. {@link #invalidateOnlineDots()}
     * is what makes a change take effect.
     */
    private static int onlineDotsEnabled = -1;

    public static boolean isOnlineDotsEnabled() {
        if (onlineDotsEnabled == -1) {
            try {
                onlineDotsEnabled = org.telegram.messenger.MessagesController.getGlobalMainSettings()
                        .getBoolean(ONLINE_DOTS_KEY, true) ? 1 : 0;
            } catch (Throwable t) {
                return false;
            }
        }
        return onlineDotsEnabled == 1;
    }

    public static void invalidateOnlineDots() {
        onlineDotsEnabled = -1;
    }

    /**
     * The green dot next to a sender's avatar inside a group. The dialog list has had these
     * upstream for years; group chats never did, which is where they are actually useful —
     * you can see who is around before writing.
     *
     * <p>Called from {@code ChatActivity}'s own avatar-drawing pass, right after it draws the
     * avatar {@code ImageReceiver} itself, on that same canvas - so the dot always shares
     * whatever position, scale and translation the avatar was just drawn with, including during
     * the forum side-menu transition. It must not participate in measurement, which rules out
     * drawing it as a real child view. The dot sits on the avatar's bottom-right corner with a
     * thin outline, so it reads on any wallpaper without needing to know the background colour.
     */
    public static void drawOnlineDotFor(Canvas canvas, org.telegram.messenger.ImageReceiver avatar, MessageObject messageObject) {
        if (avatar == null || messageObject == null || !isOnlineDotsEnabled()) {
            return;
        }
        if (messageObject.messageOwner == null || messageObject.messageOwner.from_id == null) {
            return;
        }
        final long userId = messageObject.messageOwner.from_id.user_id;
        if (userId == 0 || !isUserOnlineCached(messageObject.currentAccount, userId)) {
            return;
        }
        if (avatar.getImageWidth() <= 0) {
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

    /**
     * Is this sender online, answered from a short-lived cache.
     *
     * <p>The uncached version ran for every visible cell on every frame, and each run did a
     * {@code getUser} - whose parameter is a {@code Long}, so every call boxed one - plus
     * {@code getCurrentTime()}, which crosses into native code. On a 120 Hz scroll through a group
     * that is thousands of allocations and native transitions a second, to answer a question whose
     * answer changes at most once a minute.
     *
     * <p>Five seconds of staleness is the trade. The dialog list solves the same problem by
     * computing it once per bind; a message cell has no equivalent hook, so the cache is keyed by
     * user instead. {@link androidx.collection.LongSparseArray} keeps the lookup free of boxing.
     */
    private static final androidx.collection.LongSparseArray<long[]> onlineCache = new androidx.collection.LongSparseArray<>();
    private static final long ONLINE_CACHE_MS = 5000;

    private static boolean isUserOnlineCached(int account, long userId) {
        final long now = android.os.SystemClock.elapsedRealtime();
        long[] entry = onlineCache.get(userId);
        if (entry != null && now < entry[0]) {
            return entry[1] != 0;
        }
        final org.telegram.tgnet.TLRPC.User user =
                org.telegram.messenger.MessagesController.getInstance(account).getUser(userId);
        final boolean online = user != null && !user.bot && !user.self && isUserOnline(account, user);
        if (entry == null) {
            if (onlineCache.size() > 512) {
                // Bounded: a long-lived process scrolling through many groups would otherwise
                // accumulate an entry per person ever seen.
                onlineCache.clear();
            }
            entry = new long[2];
            onlineCache.put(userId, entry);
        }
        entry[0] = now + ONLINE_CACHE_MS;
        entry[1] = online ? 1 : 0;
        return online;
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
