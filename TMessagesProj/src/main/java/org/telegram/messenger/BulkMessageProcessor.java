package org.telegram.messenger;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

/**
 * Walks a chat / topic / search result server-side, page by page, collecting only message
 * <b>ids</b> and handing them to an action in API-sized batches.
 *
 * <p>Selecting "everything" cannot be done by materialising {@link MessageObject}s: a chat
 * may hold hundreds of thousands of messages while the client keeps only a small loaded
 * window in memory. Bulk operations are also capped server-side at {@link #BATCH_SIZE} ids
 * per request. This class bridges both constraints — it never holds more than one page of
 * ids at a time and never issues an over-sized request.
 */
public class BulkMessageProcessor {

    /** Server-side cap on ids per delete/forward request, and the page size we fetch with. */
    public static final int BATCH_SIZE = 100;

    public interface BatchAction {
        /**
         * Performs the operation on one batch of ids (at most {@link #BATCH_SIZE}).
         * The next page is only fetched once {@code onDone} runs, so operations that hit
         * the network (forwarding) stay ordered and don't trip flood limits.
         */
        void run(ArrayList<Integer> ids, Runnable onDone);
    }

    public interface Callback {
        /** Reported after every processed batch, on the UI thread. */
        void onProgress(int processed);
        /** Reported once, on the UI thread. {@code error} is null on success. */
        void onFinished(int processed, boolean cancelled, String error);
    }

    private final int currentAccount;
    private final long dialogId;
    /** Forum topic / thread id to restrict to, or 0 for the whole chat. */
    private final int topicId;
    /** Search query to restrict to, or null to walk the whole history. */
    private final String query;
    /** Saved-messages tag to restrict to, or null. */
    private final TLRPC.Reaction savedReaction;

    private final BatchAction action;
    private final Callback callback;

    /**
     * Restricts the walk to the user's own messages. Set where the account has no right to
     * delete anyone else's: sending those ids anyway makes the client hide messages locally
     * that the server then refuses to touch.
     */
    private boolean ownOnly;

    private volatile boolean cancelled;
    private int processed;
    private int offsetId;

    private BulkMessageProcessor(int currentAccount, long dialogId, int topicId, String query, TLRPC.Reaction savedReaction, BatchAction action, Callback callback) {
        this.currentAccount = currentAccount;
        this.dialogId = dialogId;
        this.topicId = topicId;
        this.query = query;
        this.savedReaction = savedReaction;
        this.action = action;
        this.callback = callback;
    }

    /** Every message in a chat, or in one forum topic when {@code topicId != 0}. */
    public static BulkMessageProcessor forChat(int currentAccount, long dialogId, int topicId, BatchAction action, Callback callback) {
        return new BulkMessageProcessor(currentAccount, dialogId, topicId, null, null, action, callback);
    }

    /** Every message matching a search query and/or a Saved Messages tag. */
    public static BulkMessageProcessor forSearch(int currentAccount, long dialogId, int topicId, String query, TLRPC.Reaction savedReaction, BatchAction action, Callback callback) {
        return new BulkMessageProcessor(currentAccount, dialogId, topicId, query == null ? "" : query, savedReaction, action, callback);
    }

    /** Deletes every message of a chat/topic, in batches, optionally for all participants. */
    public static BulkMessageProcessor deleteAll(int currentAccount, long dialogId, int topicId, boolean forAll, Callback callback) {
        return forChat(currentAccount, dialogId, topicId, deleteAction(currentAccount, dialogId, topicId, forAll), callback);
    }

    /** Limits this run to messages the user sent. */
    public BulkMessageProcessor setOwnOnly(boolean ownOnly) {
        this.ownOnly = ownOnly;
        return this;
    }

    /** Deletes every message matching a search query / Saved Messages tag, in batches. */
    public static BulkMessageProcessor deleteSearchResults(int currentAccount, long dialogId, int topicId, String query, TLRPC.Reaction savedReaction, boolean forAll, Callback callback) {
        return forSearch(currentAccount, dialogId, topicId, query, savedReaction, deleteAction(currentAccount, dialogId, topicId, forAll), callback);
    }

    private static BatchAction deleteAction(int currentAccount, long dialogId, int topicId, boolean forAll) {
        return (ids, onDone) -> {
            // Deletion is applied locally and queued for the server per batch, so there is
            // nothing to wait for before moving on to the next page.
            MessagesController.getInstance(currentAccount)
                    .deleteMessages(ids, null, null, dialogId, topicId, forAll, MODE_DEFAULT);
            onDone.run();
        };
    }

    /**
     * Forwards every message of a chat/topic to another dialog, oldest first, one batch at
     * a time — there is no server-side "forward everything" primitive, so this is the only
     * way to move a large history.
     */
    public static BulkMessageProcessor forwardAll(int currentAccount, long fromDialogId, int topicId, long toDialogId, Callback callback) {
        return forChat(currentAccount, fromDialogId, topicId, forwardAction(currentAccount, fromDialogId, toDialogId), callback);
    }

    /** Forwards every message matching a search query / Saved Messages tag. */
    public static BulkMessageProcessor forwardSearchResults(int currentAccount, long fromDialogId, int topicId, String query, TLRPC.Reaction savedReaction, long toDialogId, Callback callback) {
        return forSearch(currentAccount, fromDialogId, topicId, query, savedReaction, forwardAction(currentAccount, fromDialogId, toDialogId), callback);
    }

    private static BatchAction forwardAction(int currentAccount, long fromDialogId, long toDialogId) {
        return (ids, onDone) -> {
            final MessagesController controller = MessagesController.getInstance(currentAccount);
            TLRPC.InputPeer from = controller.getInputPeer(fromDialogId);
            TLRPC.InputPeer to = controller.getInputPeer(toDialogId);
            if (from == null || to == null) {
                onDone.run();
                return;
            }
            TLRPC.TL_messages_forwardMessages req = new TLRPC.TL_messages_forwardMessages();
            req.from_peer = from;
            req.to_peer = to;
            // Pages arrive newest-first; reverse so the copy keeps chronological order.
            for (int i = ids.size() - 1; i >= 0; i--) {
                req.id.add(ids.get(i));
                req.random_id.add(Utilities.random.nextLong());
            }
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                if (response instanceof TLRPC.Updates) {
                    controller.processUpdates((TLRPC.Updates) response, false);
                }
                AndroidUtilities.runOnUIThread(onDone);
            });
        };
    }

    /** ChatActivity.MODE_DEFAULT, duplicated to keep this class free of UI dependencies. */
    private static final int MODE_DEFAULT = 0;

    public void start() {
        cancelled = false;
        processed = 0;
        offsetId = 0;
        requestNextPage();
    }

    public void cancel() {
        cancelled = true;
    }

    private void requestNextPage() {
        if (cancelled) {
            finish(true, null);
            return;
        }
        final TLRPC.InputPeer peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
        if (peer == null) {
            finish(false, "no peer");
            return;
        }
        final TLObject request = buildRequest(peer);
        ConnectionsManager.getInstance(currentAccount).sendRequest(request, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (cancelled) {
                finish(true, null);
                return;
            }
            if (error != null || !(response instanceof TLRPC.messages_Messages)) {
                finish(false, error != null ? error.text : "bad response");
                return;
            }
            onPageLoaded((TLRPC.messages_Messages) response);
        }));
    }

    private TLObject buildRequest(TLRPC.InputPeer peer) {
        if (query != null || savedReaction != null) {
            TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
            req.peer = peer;
            req.q = query == null ? "" : query;
            req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
            req.limit = BATCH_SIZE;
            req.offset_id = offsetId;
            if (topicId != 0) {
                req.top_msg_id = topicId;
                req.flags |= 2;
            }
            if (savedReaction != null) {
                req.saved_reaction.add(savedReaction);
                req.flags |= 4;
            }
            return req;
        }
        if (topicId != 0) {
            TLRPC.TL_messages_getReplies req = new TLRPC.TL_messages_getReplies();
            req.peer = peer;
            req.msg_id = topicId;
            req.limit = BATCH_SIZE;
            req.offset_id = offsetId;
            return req;
        }
        TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
        req.peer = peer;
        req.limit = BATCH_SIZE;
        req.offset_id = offsetId;
        return req;
    }

    private void onPageLoaded(TLRPC.messages_Messages res) {
        if (res.messages.isEmpty()) {
            finish(false, null);
            return;
        }
        final ArrayList<Integer> ids = new ArrayList<>(res.messages.size());
        int minId = Integer.MAX_VALUE;
        for (int i = 0; i < res.messages.size(); i++) {
            TLRPC.Message message = res.messages.get(i);
            if (message == null || message instanceof TLRPC.TL_messageEmpty) {
                continue;
            }
            // The cursor must advance over every message we saw, not just the ones we act
            // on — otherwise a page of entirely skipped messages would ask for the same
            // page forever.
            minId = Math.min(minId, message.id);
            if (ownOnly && !message.out) {
                continue;
            }
            ids.add(message.id);
        }
        if (minId == Integer.MAX_VALUE) {
            finish(false, null);
            return;
        }

        // Page backwards through history: the next request starts below the oldest id
        // we have just handled.
        offsetId = minId;
        if (ids.isEmpty()) {
            requestNextPage();
            return;
        }
        final int batchSize = ids.size();
        try {
            action.run(ids, () -> {
                processed += batchSize;
                if (callback != null) {
                    callback.onProgress(processed);
                }
                requestNextPage();
            });
        } catch (Exception e) {
            FileLog.e(e);
            finish(false, e.getMessage());
        }
    }

    private void finish(boolean wasCancelled, String error) {
        if (callback != null) {
            callback.onFinished(processed, wasCancelled, error);
        }
    }
}
