package org.telegram.messenger;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * PrimeGram: supplies the "Лента" tab's message list to a real {@link ChatActivity} in
 * {@link ChatActivity#MODE_FEED}, the same way {@code MessagesController.loadMessages} supplies
 * one for a normal chat - by firing {@link NotificationCenter#messagesDidLoad} with the fragment's
 * own {@code classGuid}/{@code queryLoadIndex}, which is the only thing {@code ChatActivity}
 * actually checks to accept a load. Everything else about the notification (dialog id 0, every
 * message carrying its own real peer identity) mirrors exteraGram's own feed, which uses this
 * exact mechanism - not a coincidence, {@code MODE_FEED} was numbered to line up with it.
 *
 * <p>What actually goes into the list is unchanged from before this file existed: only unread
 * posts from subscribed broadcast channels, aggregated straight out of {@code messages_v2}
 * locally - no server request of our own, no "everything ever posted" like a real search would
 * return.
 */
public class PrimeFeedController {

    private static final PrimeFeedController[] instances = new PrimeFeedController[UserConfig.MAX_ACCOUNT_COUNT];

    private static final int PER_DIALOG_LIMIT = 100;

    private final int currentAccount;
    /** Every dialog the last delivered batch touched, and the newest message id/date seen for
     *  it - read back by {@link #markDeliveredAsRead()} when the feed tab is left, since a real
     *  ChatActivity's own scroll-based read-marking is single-dialog and does nothing useful with
     *  dialog_id 0. Coarser than marking exactly what scrolled past, but honest: everything that
     *  was delivered was shown on screen at some point while the tab was open. */
    private final java.util.HashMap<Long, int[]> lastDelivered = new java.util.HashMap<>();

    private PrimeFeedController(int account) {
        currentAccount = account;
    }

    public static PrimeFeedController getInstance(int account) {
        PrimeFeedController instance = instances[account];
        if (instance == null) {
            synchronized (PrimeFeedController.class) {
                instance = instances[account];
                if (instance == null) {
                    instance = instances[account] = new PrimeFeedController(account);
                }
            }
        }
        return instance;
    }

    /**
     * Fetches and delivers the whole current unread set in one shot. Older-post pagination is not
     * wired up yet - a channel with more unread than {@link #PER_DIALOG_LIMIT} just shows the
     * newest {@value #PER_DIALOG_LIMIT} of them for now, the same cap the previous, simpler feed
     * view used before growing it on scroll.
     */
    public void loadInitial(int classGuid, int queryLoadIndex) {
        final MessagesController mc = MessagesController.getInstance(currentAccount);
        final boolean excludeMuted = MessagesController.getGlobalMainSettings().getBoolean("primegram_feed_exclude_muted", false);
        final boolean excludeArchived = MessagesController.getGlobalMainSettings().getBoolean("primegram_feed_exclude_archived", false);

        final ArrayList<TLRPC.Dialog> unreadDialogs = new ArrayList<>();
        if (mc.dialogs_dict != null) {
            for (int i = 0; i < mc.dialogs_dict.size(); i++) {
                TLRPC.Dialog d = mc.dialogs_dict.valueAt(i);
                if (d == null) {
                    continue;
                }
                if (d.folder_id == 1 && excludeArchived) {
                    continue;
                }
                final boolean stillTracked = !PrimeFeedReadState.stillVisibleIds(d.id).isEmpty();
                if (d.unread_count <= 0 && !stillTracked) {
                    continue;
                }
                if (!DialogObject.isChatDialog(d.id)) {
                    continue;
                }
                TLRPC.Chat chat = mc.getChat(-d.id);
                if (chat != null && chat.broadcast && !chat.megagroup) {
                    if (excludeMuted && mc.isDialogMuted(d.id, 0)) {
                        continue;
                    }
                    unreadDialogs.add(d);
                }
            }
        }

        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            final ArrayList<MessageObject> msgs = new ArrayList<>();
            try {
                final SQLiteDatabase database = MessagesStorage.getInstance(currentAccount).getDatabase();
                for (TLRPC.Dialog d : unreadDialogs) {
                    final List<Integer> tracked = PrimeFeedReadState.stillVisibleIds(d.id);
                    final StringBuilder trackedIds = new StringBuilder();
                    if (tracked.isEmpty()) {
                        trackedIds.append("-1");
                    } else {
                        for (int i = 0; i < tracked.size(); i++) {
                            if (i > 0) {
                                trackedIds.append(",");
                            }
                            trackedIds.append(tracked.get(i));
                        }
                    }
                    final SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US,
                            "SELECT data, mid, date FROM messages_v2 WHERE uid = %d AND (mid > %d OR mid IN (%s)) ORDER BY mid DESC LIMIT %d",
                            d.id, d.read_inbox_max_id, trackedIds, PER_DIALOG_LIMIT));
                    while (cursor.next()) {
                        final NativeByteBuffer data = cursor.byteBufferValue(0);
                        if (data != null) {
                            final TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                            message.readAttachPath(data, UserConfig.getInstance(currentAccount).clientUserId);
                            data.reuse();
                            message.id = cursor.intValue(1);
                            message.date = cursor.intValue(2);
                            message.dialog_id = d.id;
                            message.unread = message.id > d.read_inbox_max_id;
                            message.out = false;
                            message.post = false;
                            message.from_id = new TLRPC.TL_peerChannel();
                            message.from_id.channel_id = -d.id;

                            final MessageObject obj = new MessageObject(currentAccount, message, true, false);
                            obj.forceAvatar = true;
                            msgs.add(obj);
                            PrimeFeedReadState.markSeen(d.id, message.id);
                        }
                    }
                    cursor.dispose();
                }

                Collections.sort(msgs, (a, b) -> {
                    TLRPC.Dialog dA = mc.dialogs_dict.get(a.getDialogId());
                    TLRPC.Dialog dB = mc.dialogs_dict.get(b.getDialogId());
                    boolean aArchived = dA != null && dA.folder_id == 1;
                    boolean bArchived = dB != null && dB.folder_id == 1;
                    if (aArchived != bArchived) {
                        return aArchived ? 1 : -1;
                    }
                    return Integer.compare(b.messageOwner.date, a.messageOwner.date);
                });
            } catch (Throwable e) {
                FileLog.e(e);
            }
            synchronized (lastDelivered) {
                lastDelivered.clear();
                for (MessageObject obj : msgs) {
                    final long did = obj.getDialogId();
                    final int[] existing = lastDelivered.get(did);
                    if (existing == null || obj.getId() > existing[0]) {
                        lastDelivered.put(did, new int[]{obj.getId(), obj.messageOwner.date});
                    }
                }
            }
            deliver(classGuid, queryLoadIndex, msgs);
        });
    }

    /** Marks every dialog the last delivered batch touched as read up to the newest post shown
     *  from it. Called when the feed tab is left, not on a timer or per-scroll - see the field
     *  doc on {@link #lastDelivered} for why. */
    public void markDeliveredAsRead() {
        final java.util.HashMap<Long, int[]> snapshot;
        synchronized (lastDelivered) {
            if (lastDelivered.isEmpty()) {
                return;
            }
            snapshot = new java.util.HashMap<>(lastDelivered);
            lastDelivered.clear();
        }
        final MessagesController mc = MessagesController.getInstance(currentAccount);
        for (java.util.Map.Entry<Long, int[]> entry : snapshot.entrySet()) {
            final int maxId = entry.getValue()[0];
            final int maxDate = entry.getValue()[1];
            mc.markDialogAsRead(entry.getKey(), maxId, 0, maxDate, false, 0, 0, true, 0);
        }
    }

    private void deliver(int classGuid, int queryLoadIndex, ArrayList<MessageObject> msgs) {
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(currentAccount).postNotificationName(
                NotificationCenter.messagesDidLoad,
                0L,                     // dialogId - none; every message carries its own
                msgs.size(),            // count
                msgs,                   // objects
                false,                  // isCache
                0,                      // first_unread_final
                0,                      // last_message_id
                0,                      // unread_count
                0,                      // last_date
                0,                      // load_type
                true,                   // isEnd - no "load older" yet
                classGuid,
                queryLoadIndex,
                0,                      // max_id
                0,                      // mentionsCount
                ChatActivity.MODE_FEED  // mode
        ));
    }
}
