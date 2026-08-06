package org.telegram.messenger;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
 *
 * <p>Pagination is per-dialog cursors, not a single global offset: each channel's own oldest
 * delivered {@code mid} is tracked separately, because "unread" is inherently bounded per channel
 * - once a channel's own unread range is exhausted there is nothing further to fetch for it,
 * regardless of how many other channels still have more.
 */
public class PrimeFeedController {

    private static final PrimeFeedController[] instances = new PrimeFeedController[UserConfig.MAX_ACCOUNT_COUNT];

    private static final int PAGE_SIZE = 50;

    private final int currentAccount;

    /** Every dialog the last delivered batch touched, and the newest message id/date seen for
     *  it - read back by {@link #markDeliveredAsRead()} when the feed tab is left, since a real
     *  ChatActivity's own scroll-based read-marking is single-dialog and does nothing useful with
     *  dialog_id 0. Coarser than marking exactly what scrolled past, but honest: everything that
     *  was delivered was shown on screen at some point while the tab was open. */
    private final HashMap<Long, int[]> lastDelivered = new HashMap<>();

    /** Pagination state, reset on every {@link #loadInitial}. */
    private final HashMap<Long, Integer> oldestDeliveredMid = new HashMap<>();
    private final HashSet<Long> exhaustedDialogs = new HashSet<>();
    private final ArrayList<Long> knownDialogIds = new ArrayList<>();

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

    /** The current unread set, freshest first, one page per channel. Resets pagination state. */
    public void loadInitial(int classGuid, int queryLoadIndex) {
        final ArrayList<TLRPC.Dialog> unreadDialogs = collectUnreadDialogs();
        synchronized (this) {
            oldestDeliveredMid.clear();
            exhaustedDialogs.clear();
            knownDialogIds.clear();
            for (TLRPC.Dialog d : unreadDialogs) {
                knownDialogIds.add(d.id);
            }
        }
        loadPage(classGuid, queryLoadIndex, unreadDialogs, true);
    }

    /** The next older page for every channel that still has more, from where {@link #loadInitial}
     *  (or the previous {@link #loadMore}) left off. Channels not seen by the last
     *  {@link #loadInitial} are not picked up here - a channel that goes from read to unread while
     *  the tab is open needs a fresh {@link #loadInitial}, same as opening the tab again would
     *  give it. */
    public void loadMore(int classGuid, int queryLoadIndex) {
        final MessagesController mc = MessagesController.getInstance(currentAccount);
        final ArrayList<TLRPC.Dialog> pending = new ArrayList<>();
        synchronized (this) {
            for (Long id : knownDialogIds) {
                if (exhaustedDialogs.contains(id)) {
                    continue;
                }
                TLRPC.Dialog d = mc.dialogs_dict != null ? mc.dialogs_dict.get(id) : null;
                if (d != null) {
                    pending.add(d);
                }
            }
        }
        if (pending.isEmpty()) {
            deliver(classGuid, queryLoadIndex, new ArrayList<>(), true);
            return;
        }
        loadPage(classGuid, queryLoadIndex, pending, false);
    }

    private ArrayList<TLRPC.Dialog> collectUnreadDialogs() {
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
        return unreadDialogs;
    }

    private void loadPage(int classGuid, int queryLoadIndex, ArrayList<TLRPC.Dialog> dialogs, boolean isFirstPage) {
        final MessagesController mc = MessagesController.getInstance(currentAccount);
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            final ArrayList<MessageObject> msgs = new ArrayList<>();
            final HashMap<Long, Integer> pageOldestMid = new HashMap<>();
            final HashMap<Long, Integer> pageRowCount = new HashMap<>();
            try {
                final SQLiteDatabase database = MessagesStorage.getInstance(currentAccount).getDatabase();
                for (TLRPC.Dialog d : dialogs) {
                    final String query;
                    if (isFirstPage) {
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
                        query = String.format(Locale.US,
                                "SELECT data, mid, date FROM messages_v2 WHERE uid = %d AND (mid > %d OR mid IN (%s)) ORDER BY mid DESC LIMIT %d",
                                d.id, d.read_inbox_max_id, trackedIds, PAGE_SIZE);
                    } else {
                        final int upperBoundExclusive;
                        synchronized (this) {
                            Integer cursor = oldestDeliveredMid.get(d.id);
                            upperBoundExclusive = cursor != null ? cursor : Integer.MAX_VALUE;
                        }
                        query = String.format(Locale.US,
                                "SELECT data, mid, date FROM messages_v2 WHERE uid = %d AND mid > %d AND mid < %d ORDER BY mid DESC LIMIT %d",
                                d.id, d.read_inbox_max_id, upperBoundExclusive, PAGE_SIZE);
                    }
                    final SQLiteCursor cursor = database.queryFinalized(query);
                    int rows = 0;
                    int smallestMidThisDialog = Integer.MAX_VALUE;
                    while (cursor.next()) {
                        rows++;
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
                            if (message.id < smallestMidThisDialog) {
                                smallestMidThisDialog = message.id;
                            }
                        }
                    }
                    cursor.dispose();
                    pageRowCount.put(d.id, rows);
                    if (smallestMidThisDialog != Integer.MAX_VALUE) {
                        pageOldestMid.put(d.id, smallestMidThisDialog);
                    }
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

            boolean allExhausted;
            synchronized (this) {
                if (isFirstPage) {
                    lastDelivered.clear();
                }
                for (MessageObject obj : msgs) {
                    final long did = obj.getDialogId();
                    final int[] existing = lastDelivered.get(did);
                    if (existing == null || obj.getId() > existing[0]) {
                        lastDelivered.put(did, new int[]{obj.getId(), obj.messageOwner.date});
                    }
                }
                for (TLRPC.Dialog d : dialogs) {
                    final Integer rows = pageRowCount.get(d.id);
                    if (rows == null || rows < PAGE_SIZE) {
                        exhaustedDialogs.add(d.id);
                    }
                    final Integer newOldest = pageOldestMid.get(d.id);
                    if (newOldest != null) {
                        oldestDeliveredMid.put(d.id, newOldest);
                    }
                }
                allExhausted = exhaustedDialogs.containsAll(knownDialogIds);
            }
            deliver(classGuid, queryLoadIndex, msgs, allExhausted);
            // The comment button under a post needs its channel's ChatFull (for linked_chat_id) -
            // ChatActivity only ever loads that for whichever single dialog_id it was opened on,
            // which is never the case here. Ask for every channel's own, same as opening it
            // normally would; loadFullChat no-ops on its own once a channel is already loaded.
            AndroidUtilities.runOnUIThread(() -> {
                final MessagesController controller = MessagesController.getInstance(currentAccount);
                for (TLRPC.Dialog d : dialogs) {
                    controller.loadFullChat(-d.id, 0, false);
                }
            });
        });
    }

    /** Marks every dialog the last delivered batch touched as read up to the newest post shown
     *  from it. Called when the feed tab is left, not on a timer or per-scroll - see the field
     *  doc on {@link #lastDelivered} for why. */
    public void markDeliveredAsRead() {
        final HashMap<Long, int[]> snapshot;
        synchronized (this) {
            if (lastDelivered.isEmpty()) {
                return;
            }
            snapshot = new HashMap<>(lastDelivered);
            lastDelivered.clear();
        }
        final MessagesController mc = MessagesController.getInstance(currentAccount);
        for (Map.Entry<Long, int[]> entry : snapshot.entrySet()) {
            final int maxId = entry.getValue()[0];
            final int maxDate = entry.getValue()[1];
            mc.markDialogAsRead(entry.getKey(), maxId, 0, maxDate, false, 0, 0, true, 0);
        }
    }

    private void deliver(int classGuid, int queryLoadIndex, ArrayList<MessageObject> msgs, boolean isEnd) {
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
                isEnd,
                classGuid,
                queryLoadIndex,
                0,                      // max_id
                0,                      // mentionsCount
                ChatActivity.MODE_FEED  // mode
        ));
    }
}
