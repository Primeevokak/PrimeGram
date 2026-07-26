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
    }

    private static SharedPreferences prefs() {
        return MessagesController.getGlobalMainSettings();
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

    /** Adds or replaces the subscription for one chat. */
    public static void schedule(long dialogId, long expiresAt, String title) {
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
     */
    public static void checkExpired(int accountNum) {
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
            MessagesController controller = MessagesController.getInstance(accountNum);
            TLRPC.User self = UserConfig.getInstance(accountNum).getCurrentUser();
            if (self == null) {
                return;
            }
            for (Entry entry : entries) {
                if (entry.expiresAt > now || entry.dialogId >= 0) {
                    continue;
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
}
