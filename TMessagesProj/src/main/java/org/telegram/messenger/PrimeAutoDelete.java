package org.telegram.messenger;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

/**
 * PrimeGram: auto-deletes the user's OWN outgoing messages a fixed period after sending, in
 * ordinary (non-secret) chats - secret chats already have their own, separate TTL system this
 * doesn't touch.
 *
 * <p>Deliberately built on the stock scheduled-deletion queue ({@code enc_tasks_v4}, driven by
 * {@link MessagesController#checkDeletingTask()}) via {@link MessagesStorage#createTaskForOutgoingAutoDelete}
 * rather than a parallel engine: that queue is already restart-surviving and batched by due time,
 * and {@code checkDeletingTask()}'s own delete call already passes {@code forAll=true} for a
 * normal dialogId, so nothing here needs to re-implement "reliably fires later, even across app
 * restarts."
 */
public final class PrimeAutoDelete {

    private static final String PREFS = "primegram_autodelete";
    private static final String KEY_GLOBAL_ENABLED = "enabled";
    private static final String KEY_GLOBAL_PERIOD_HOURS = "period_hours";
    private static final String KEY_DIALOG_PREFIX = "dlg_";

    /** 24/48/72/120/168h - matches the plan's own picker; 168 (one week) is the default so an
     *  opted-in user gets a generous grace period, not messages vanishing the same day. */
    public static final int[] PERIOD_OPTIONS_HOURS = {24, 48, 72, 120, 168};
    private static final int DEFAULT_PERIOD_HOURS = 168;

    private PrimeAutoDelete() {
    }

    private static volatile boolean registered;

    /** Idempotent; call once at startup (see ApplicationLoader.postInitApplication()) so a
     *  message sent seconds after cold start is already covered, not just ones sent after the
     *  user happens to open a PrimeGram settings screen and lazily triggers this class's first
     *  real use. Registers on every account slot, not just the active one - NotificationCenter
     *  instances are lazy singletons regardless of whether that slot is actually signed in yet,
     *  so this is cheap and covers an account added later in the same session for free. */
    public static void ensureRegistered() {
        if (registered) {
            return;
        }
        registered = true;
        ensurePoller(); // picks up edits left over from a previous session, not just new ones
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            final int account = a;
            NotificationCenter.getInstance(account).addObserver((id, acc, args) -> {
                // args: (int oldId, int newId, TLObject message, long/Long dialogId, long
                // groupedId, int existFlags, boolean scheduled) - see every
                // messageReceivedByServer post site in SendMessagesHelper.java, all of which
                // share this shape.
                if (args.length < 7 || !(args[2] instanceof TLRPC.Message) || Boolean.TRUE.equals(args[6])) {
                    return; // no message object (some channel-update paths pass null), or scheduled - not sent yet
                }
                onOwnMessageAcked(account, (TLRPC.Message) args[2]);
            }, NotificationCenter.messageReceivedByServer);
        }
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);
    }

    public static boolean isGloballyEnabled() {
        return prefs().getBoolean(KEY_GLOBAL_ENABLED, false);
    }

    public static void setGloballyEnabled(boolean enabled) {
        prefs().edit().putBoolean(KEY_GLOBAL_ENABLED, enabled).apply();
    }

    public static int getPeriodHours() {
        return prefs().getInt(KEY_GLOBAL_PERIOD_HOURS, DEFAULT_PERIOD_HOURS);
    }

    public static void setPeriodHours(int hours) {
        prefs().edit().putInt(KEY_GLOBAL_PERIOD_HOURS, hours).apply();
    }

    /** Per-dialog override: {@code null} means "inherit the global toggle," not "off" - so
     *  turning the global toggle on later still picks up dialogs nobody has ever touched. */
    public static Boolean getDialogOverride(long dialogId) {
        String key = KEY_DIALOG_PREFIX + dialogId;
        if (!prefs().contains(key)) {
            return null;
        }
        return prefs().getBoolean(key, false);
    }

    public static void setDialogOverride(long dialogId, Boolean enabled) {
        SharedPreferences.Editor editor = prefs().edit();
        String key = KEY_DIALOG_PREFIX + dialogId;
        if (enabled == null) {
            editor.remove(key);
        } else {
            editor.putBoolean(key, enabled);
        }
        editor.apply();
    }

    public static boolean isEnabledForDialog(long dialogId) {
        Boolean override = getDialogOverride(dialogId);
        return override != null ? override : isGloballyEnabled();
    }

    /** Called once a freshly-sent message has its real, final server-assigned id - never for a
     *  still-local/negative pending id, since {@code enc_tasks_v4}'s primary key is that final
     *  mid and a task queued against the local id would never match anything once the real one
     *  replaces it. */
    public static void onOwnMessageAcked(int account, TLRPC.Message message) {
        if (message == null || message.id <= 0) {
            return;
        }
        long dialogId = MessageObject.getDialogId(message);
        if (!isEnabledForDialog(dialogId) || !isEligible(message)) {
            return;
        }
        int periodSeconds = getPeriodHours() * 3600;
        int deleteAt = message.date + periodSeconds;
        int editAt = deleteAt - (int) EDIT_GRACE_SECONDS;
        if (editAt > message.date) {
            schedulePendingEdit(account, dialogId, message.id, editAt);
        }
        MessagesStorage.getInstance(account).createTaskForOutgoingAutoDelete(dialogId, message.id, deleteAt);
    }

    // ─── "Replace with a dot" grace stage ─────────────────────────────────────────────
    //
    // The actual delete is guaranteed - it rides the same restart-surviving enc_tasks_v4 queue
    // as everything else in this class. This edit step is deliberately NOT built the same way:
    // enc_tasks_v4/checkDeletingTask only ever does one thing (delete) at a fixed time, and this
    // needs a second, earlier action first. Rather than teach the stock queue a new action type,
    // this keeps its own small pending-edits list and a lightweight in-process poller - if the
    // app isn't running when an edit's time comes, that specific "." courtesy is just skipped
    // (the poller catches up on next launch for anything still due, but doesn't retroactively
    // edit a message that's already past its delete time by then). The delete itself never
    // depends on this succeeding.

    private static final long EDIT_GRACE_SECONDS = 60;
    private static final long EDIT_POLL_INTERVAL_MS = 30_000;
    private static final String KEY_PENDING_EDITS = "pending_edits";

    private static volatile boolean pollerStarted;

    private static void ensurePoller() {
        if (pollerStarted) {
            return;
        }
        pollerStarted = true;
        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable[] tick = new Runnable[1];
        tick[0] = () -> {
            processPendingEdits();
            handler.postDelayed(tick[0], EDIT_POLL_INTERVAL_MS);
        };
        handler.postDelayed(tick[0], EDIT_POLL_INTERVAL_MS);
    }

    private static synchronized JSONArray loadPendingEdits() {
        try {
            String raw = prefs().getString(KEY_PENDING_EDITS, null);
            if (raw == null) {
                return new JSONArray();
            }
            return new JSONArray(raw);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static synchronized void savePendingEdits(JSONArray arr) {
        prefs().edit().putString(KEY_PENDING_EDITS, arr.toString()).apply();
    }

    private static void schedulePendingEdit(int account, long dialogId, int messageId, int editAtServerSeconds) {
        try {
            JSONArray arr = loadPendingEdits();
            JSONObject o = new JSONObject();
            o.put("acc", account);
            o.put("dlg", dialogId);
            o.put("mid", messageId);
            o.put("at", editAtServerSeconds);
            arr.put(o);
            savePendingEdits(arr);
            ensurePoller();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static void processPendingEdits() {
        JSONArray arr = loadPendingEdits();
        if (arr.length() == 0) {
            return;
        }
        JSONArray remaining = new JSONArray();
        boolean changed = false;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) {
                continue;
            }
            int account = o.optInt("acc");
            long dialogId = o.optLong("dlg");
            int messageId = o.optInt("mid");
            int at = o.optInt("at");
            int now;
            try {
                now = ConnectionsManager.getInstance(account).getCurrentTime();
            } catch (Throwable t) {
                remaining.put(o);
                continue;
            }
            if (now >= at) {
                fireEditToDot(account, dialogId, messageId);
                changed = true;
            } else {
                remaining.put(o);
            }
        }
        if (changed) {
            savePendingEdits(remaining);
        }
    }

    private static void fireEditToDot(int account, long dialogId, int messageId) {
        try {
            TLRPC.TL_messages_editMessage req = new TLRPC.TL_messages_editMessage();
            req.peer = MessagesController.getInstance(account).getInputPeer(dialogId);
            req.id = messageId;
            req.message = ".";
            req.flags |= 2048; // has message text
            req.no_webpage = true;
            ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
                // Best-effort courtesy only - the real deletion is already queued independently
                // of whether this lands, so nothing here needs to react to the result.
            });
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static boolean isEligible(TLRPC.Message message) {
        if (!message.out) {
            return false;
        }
        if (message.action != null) {
            return false;
        }
        if (message.fwd_from != null) {
            return false;
        }
        long dialogId = MessageObject.getDialogId(message);
        // Secret chats run their own, separate TTL system - piggybacking this on top of it would
        // mean two independent timers racing to delete the same message.
        return !DialogObject.isEncryptedDialog(dialogId);
    }
}
