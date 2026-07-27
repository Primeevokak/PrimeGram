package org.telegram.messenger;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.List;

/**
 * PrimeGram "Временные подписки": channels the user joined with an expiry date, left
 * automatically once it passes.
 *
 * <p>The schedule lives in preferences rather than an alarm: an exact alarm per subscription
 * would be denied on modern Android anyway, and leaving a channel a few minutes late is
 * harmless. {@link #checkExpired} is simply run whenever the app is in a position to act.
 */
public class TempSubStore {

    private static final String KEY = "primegram_temp_subs";
    /** Don't re-check more often than this — the sweep walks every entry and sends requests. */
    private static final long MIN_CHECK_INTERVAL_MS = 60_000L;

    private static long lastCheck;

    public static class Entry {
        public long dialogId;
        public long expiresAt;
        public String title;
        /**
         * The account that joined. Entries written before this field existed report -1, and the
         * sweep then falls back to the selected account - the same guess the old code made
         * implicitly, only now it is visible.
         */
        public int account = -1;
    }

    /**
     * The store, opened straight from the application context.
     *
     * <p>Deliberately not {@code MessagesController.getGlobalMainSettings()}, which is
     * {@code getInstance(0).mainPreferences} and therefore builds a whole MessagesController -
     * a thread and a SQLite database - just to read a string. That is merely wasteful from the
     * interface, but this is now read from the proxy service's fifteen-second tick, where it
     * would mean standing an account up in a process that may not need one.
     *
     * <p>"mainconfig" with no suffix is account 0's file, so the values are the same ones.
     */
    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext
                .getSharedPreferences("mainconfig", android.content.Context.MODE_PRIVATE);
    }

    private static JSONArray readArray() {
        try {
            String raw = prefs().getString(KEY, null);
            if (TextUtils.isEmpty(raw)) {
                return new JSONArray();
            }
            return new JSONArray(raw);
        } catch (Exception e) {
            FileLog.e("TempSubStore.read", e);
            return new JSONArray();
        }
    }

    private static void writeArray(JSONArray array) {
        try {
            prefs().edit().putString(KEY, array.toString()).apply();
        } catch (Exception e) {
            FileLog.e("TempSubStore.write", e);
        }
    }

    /** Adds or replaces the subscription for one chat, on the account that is joining it. */
    public static void schedule(long dialogId, long expiresAt, String title, int account) {
        JSONArray array = readArray();
        JSONArray out = new JSONArray();
        for (int i = 0; i < array.length(); i++) {
            try {
                JSONObject o = array.getJSONObject(i);
                if (o.optLong("dialog_id") != dialogId) {
                    out.put(o);
                }
            } catch (Exception ignore) {}
        }
        try {
            JSONObject entry = new JSONObject();
            entry.put("dialog_id", dialogId);
            entry.put("expires_at", expiresAt);
            entry.put("title", title == null ? "" : title);
            entry.put("account", account);
            out.put(entry);
            writeArray(out);
        } catch (Exception e) {
            FileLog.e("TempSubStore.schedule", e);
        }
    }

    public static void cancel(long dialogId) {
        JSONArray array = readArray();
        JSONArray out = new JSONArray();
        for (int i = 0; i < array.length(); i++) {
            try {
                JSONObject o = array.getJSONObject(i);
                if (o.optLong("dialog_id") != dialogId) {
                    out.put(o);
                }
            } catch (Exception ignore) {}
        }
        writeArray(out);
    }

    public static List<Entry> getAll() {
        ArrayList<Entry> result = new ArrayList<>();
        JSONArray array = readArray();
        for (int i = 0; i < array.length(); i++) {
            try {
                JSONObject o = array.getJSONObject(i);
                Entry e = new Entry();
                e.dialogId = o.optLong("dialog_id");
                e.expiresAt = o.optLong("expires_at");
                e.title = o.optString("title");
                e.account = o.optInt("account", -1);
                result.add(e);
            } catch (Exception ignore) {}
        }
        return result;
    }

    public static Entry get(long dialogId) {
        for (Entry entry : getAll()) {
            if (entry.dialogId == dialogId) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Leaves every chat whose time is up. Cheap to call often — it throttles itself and does
     * nothing at all when no subscription is pending.
     *
     * <p>Each entry is handled on the account that created it. That used to be whichever account
     * happened to be selected when the sweep ran, which was wrong in a way that hid itself: the
     * entry was dropped before the request went out, so a subscription made on a second account
     * was silently forgotten instead of being acted on.
     *
     * <p>Safe to call with no interface running - see {@link #checkExpiredInBackground()}.
     */
    public static void checkExpired() {
        try {
            long now = System.currentTimeMillis();
            if (now - lastCheck < MIN_CHECK_INTERVAL_MS) {
                return;
            }
            lastCheck = now;
            List<Entry> entries = getAll();
            if (entries.isEmpty()) {
                return;
            }
            for (Entry entry : entries) {
                if (entry.expiresAt > now || entry.dialogId >= 0) {
                    continue;
                }
                final int account = entry.account >= 0 && entry.account < UserConfig.MAX_ACCOUNT_COUNT
                        ? entry.account
                        : UserConfig.selectedAccount;
                if (!UserConfig.getInstance(account).isClientActivated()) {
                    // The account was logged out. Nothing to leave, and nothing to leave it with.
                    cancel(entry.dialogId);
                    continue;
                }
                // getInstanceIfCreated, never getInstance: this runs on the main thread, and the
                // background sweep can reach it in a process where an account was never touched.
                // Building a controller there would start a thread and open a SQLite database on
                // the main thread - the exact shape of freeze this fork has already paid for once.
                // A missing controller simply leaves the entry for the next sweep.
                MessagesController controller = MessagesController.getInstanceIfCreated(account);
                if (controller == null) {
                    continue;
                }
                TLRPC.User self = UserConfig.getInstance(account).getCurrentUser();
                if (self == null) {
                    continue; // not loaded yet; try again on the next sweep rather than forget it
                }
                long chatId = -entry.dialogId;
                TLRPC.Chat chat = controller.getChat(chatId);
                cancel(entry.dialogId);
                if (chat == null || ChatObject.isNotInChat(chat)) {
                    continue; // already gone; nothing to do but drop the entry
                }
                controller.deleteParticipantFromChat(chatId, self, null, false, false);
            }
        } catch (Throwable t) {
            FileLog.e("TempSubStore.checkExpired", t);
        }
    }

    /**
     * The sweep as run from the proxy service, with no interface on screen.
     *
     * <p>Two differences from the foreground path, both about not paying for nothing. It returns
     * immediately unless something has actually expired, because {@link #checkExpired} may have to
     * build a {@link MessagesController} - a thread and a SQLite database - and that is far too
     * much to spend on a fifteen-second tick that usually has no work. And it hops to the main
     * thread, because everything it touches afterwards expects to be there.
     *
     * <p>Limitation worth knowing: this keeps working while the app is swiped away, since the
     * proxy is a foreground service and survives that. A force-stop kills the process outright,
     * and then nothing runs until the app is opened again - at which point the sweep catches up.
     */
    public static void checkExpiredInBackground() {
        try {
            final long now = System.currentTimeMillis();
            boolean anyDue = false;
            for (Entry entry : getAll()) {
                if (entry.dialogId < 0 && entry.expiresAt <= now) {
                    anyDue = true;
                    break;
                }
            }
            if (!anyDue) {
                return;
            }
            AndroidUtilities.runOnUIThread(TempSubStore::checkExpired);
        } catch (Throwable t) {
            FileLog.e("TempSubStore.checkExpiredInBackground", t);
        }
    }
}
