package org.telegram.messenger;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * PrimeGram: purely local labels attached to individual messages.
 *
 * <p>Nothing is sent anywhere — tags exist only on this device and are invisible to the
 * other side of the chat. Kept in a single JSON blob in preferences: the volume is small
 * (a user tags dozens of messages, not thousands) and this avoids another database file.
 */
public class MessageTagsStore {

    private static final String KEY = "primegram_message_tags";
    /** Guards against the JSON blob growing unbounded if someone tags everything. */
    private static final int MAX_ENTRIES = 5000;

    public static class Entry {
        public String tag;
        public long dialogId;
        public int messageId;
        public String preview;
        public long date;
    }

    /**
     * dialogId -> (messageId -> comma-joined tag names).
     *
     * <p>Built once and kept in sync by every mutation below. The chat cell asks "does this
     * message have a tag?" on every single draw pass, so that question has to be answered
     * from memory — re-parsing the JSON blob there would stall scrolling.
     */
    private static java.util.HashMap<Long, android.util.SparseArray<String>> index;

    private static SharedPreferences prefs() {
        return MessagesController.getGlobalMainSettings();
    }

    private static synchronized java.util.HashMap<Long, android.util.SparseArray<String>> index() {
        if (index == null) {
            java.util.HashMap<Long, android.util.SparseArray<String>> built = new java.util.HashMap<>();
            JSONArray array = readArray();
            for (int i = 0; i < array.length(); i++) {
                try {
                    JSONObject o = array.getJSONObject(i);
                    String tag = o.optString("tag");
                    if (TextUtils.isEmpty(tag)) {
                        continue;
                    }
                    long dialogId = o.optLong("dialog_id");
                    int messageId = o.optInt("message_id");
                    android.util.SparseArray<String> byMessage = built.get(dialogId);
                    if (byMessage == null) {
                        byMessage = new android.util.SparseArray<>();
                        built.put(dialogId, byMessage);
                    }
                    String existing = byMessage.get(messageId);
                    byMessage.put(messageId, existing == null ? tag : existing + ", " + tag);
                } catch (Exception ignore) {}
            }
            index = built;
            hasAnyCached = !built.isEmpty();
            hasAnyKnown = true;
        }
        return index;
    }

    private static synchronized void invalidateIndex() {
        index = null;
        hasAnyKnown = false;
    }

    /**
     * Label to draw on a message, or null when it carries no tags. Safe to call from draw code.
     */
    public static String getLabelFor(long dialogId, int messageId) {
        if (messageId == 0) {
            return null;
        }
        try {
            android.util.SparseArray<String> byMessage = index().get(dialogId);
            return byMessage == null ? null : byMessage.get(messageId);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * True when at least one message anywhere is tagged — lets callers skip work entirely.
     *
     * <p>Answered from a plain volatile field rather than through {@link #index()}, because the
     * draw path calls this for every visible cell on every frame and index() is synchronized:
     * entering and leaving that monitor a thousand times a second buys nothing when almost
     * nobody has tagged a message at all.
     */
    private static volatile boolean hasAnyCached;
    private static volatile boolean hasAnyKnown;

    public static boolean hasAny() {
        if (!hasAnyKnown) {
            // Once per process, and once more after any edit: builds the index and sets the flag.
            index();
        }
        return hasAnyCached;
    }

    private static JSONArray readArray() {
        try {
            String raw = prefs().getString(KEY, null);
            if (TextUtils.isEmpty(raw)) {
                return new JSONArray();
            }
            return new JSONArray(raw);
        } catch (Exception e) {
            FileLog.e("MessageTagsStore.read", e);
            return new JSONArray();
        }
    }

    private static void writeArray(JSONArray array) {
        try {
            prefs().edit().putString(KEY, array.toString()).apply();
        } catch (Exception e) {
            FileLog.e("MessageTagsStore.write", e);
        } finally {
            invalidateIndex();
        }
    }

    /** @return false if this message already carries this tag */
    public static boolean addTag(String tag, long dialogId, int messageId, String preview) {
        if (TextUtils.isEmpty(tag)) {
            return false;
        }
        tag = tag.trim();
        JSONArray array = readArray();
        try {
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                if (tag.equals(o.optString("tag")) && o.optLong("dialog_id") == dialogId && o.optInt("message_id") == messageId) {
                    return false;
                }
            }
            JSONObject entry = new JSONObject();
            entry.put("tag", tag);
            entry.put("dialog_id", dialogId);
            entry.put("message_id", messageId);
            entry.put("preview", preview == null ? "" : preview);
            entry.put("date", System.currentTimeMillis());
            array.put(entry);

            // Trim from the front (oldest) when the cap is hit.
            while (array.length() > MAX_ENTRIES) {
                array.remove(0);
            }
            writeArray(array);
            return true;
        } catch (Exception e) {
            FileLog.e("MessageTagsStore.addTag", e);
            return false;
        }
    }

    /** All distinct tag names, most recently used first. */
    public static List<String> getAllTags() {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        JSONArray array = readArray();
        for (int i = array.length() - 1; i >= 0; i--) {
            try {
                String tag = array.getJSONObject(i).optString("tag");
                if (!TextUtils.isEmpty(tag)) {
                    tags.add(tag);
                }
            } catch (Exception ignore) {}
        }
        return new ArrayList<>(tags);
    }

    /**
     * @param tag      null for every tag
     * @param dialogId 0 for every chat
     */
    public static List<Entry> query(String tag, long dialogId) {
        ArrayList<Entry> result = new ArrayList<>();
        JSONArray array = readArray();
        for (int i = 0; i < array.length(); i++) {
            try {
                JSONObject o = array.getJSONObject(i);
                if (tag != null && !tag.equals(o.optString("tag"))) {
                    continue;
                }
                if (dialogId != 0 && o.optLong("dialog_id") != dialogId) {
                    continue;
                }
                Entry e = new Entry();
                e.tag = o.optString("tag");
                e.dialogId = o.optLong("dialog_id");
                e.messageId = o.optInt("message_id");
                e.preview = o.optString("preview");
                e.date = o.optLong("date");
                result.add(e);
            } catch (Exception ignore) {}
        }
        Collections.reverse(result); // newest first
        return result;
    }

    /** Tags attached to one specific message. */
    public static Set<String> getTagsFor(long dialogId, int messageId) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        JSONArray array = readArray();
        for (int i = 0; i < array.length(); i++) {
            try {
                JSONObject o = array.getJSONObject(i);
                if (o.optLong("dialog_id") == dialogId && o.optInt("message_id") == messageId) {
                    tags.add(o.optString("tag"));
                }
            } catch (Exception ignore) {}
        }
        return tags;
    }

    public static void removeTag(String tag, long dialogId, int messageId) {
        JSONArray array = readArray();
        JSONArray out = new JSONArray();
        for (int i = 0; i < array.length(); i++) {
            try {
                JSONObject o = array.getJSONObject(i);
                if (tag.equals(o.optString("tag")) && o.optLong("dialog_id") == dialogId && o.optInt("message_id") == messageId) {
                    continue;
                }
                out.put(o);
            } catch (Exception ignore) {}
        }
        writeArray(out);
    }

    private static final String KEY_EMOJI = "primegram_message_tag_emoji";
    /** Used for tags created before emoji were a thing, and when the user picks none. */
    public static final String DEFAULT_EMOJI = "🏷";

    /**
     * Emoji shown on a tag's chip.
     *
     * <p>Every tag carries one because the search chips reuse Telegram's own saved-tag
     * button, which is built around a reaction — an emoji is what gives it something to
     * draw. It doubles as the "pick an icon for your tag" feature.
     */
    public static String getEmoji(String tag) {
        if (TextUtils.isEmpty(tag)) {
            return DEFAULT_EMOJI;
        }
        try {
            JSONObject map = new JSONObject(prefs().getString(KEY_EMOJI, "{}"));
            String emoji = map.optString(tag);
            return TextUtils.isEmpty(emoji) ? DEFAULT_EMOJI : emoji;
        } catch (Exception e) {
            return DEFAULT_EMOJI;
        }
    }

    public static void setEmoji(String tag, String emoji) {
        if (TextUtils.isEmpty(tag)) {
            return;
        }
        try {
            JSONObject map = new JSONObject(prefs().getString(KEY_EMOJI, "{}"));
            if (TextUtils.isEmpty(emoji)) {
                map.remove(tag);
            } else {
                map.put(tag, emoji);
            }
            prefs().edit().putString(KEY_EMOJI, map.toString()).apply();
        } catch (Exception e) {
            FileLog.e("MessageTagsStore.setEmoji", e);
        }
    }

    /** Message ids carrying a tag in one chat, newest first. */
    public static ArrayList<Integer> getMessageIds(String tag, long dialogId) {
        ArrayList<Integer> ids = new ArrayList<>();
        for (Entry entry : query(tag, dialogId)) {
            ids.add(entry.messageId);
        }
        return ids;
    }

    /** Drops a tag everywhere it is used. */
    public static void removeWholeTag(String tag) {
        if (TextUtils.isEmpty(tag)) {
            return;
        }
        JSONArray array = readArray();
        JSONArray out = new JSONArray();
        for (int i = 0; i < array.length(); i++) {
            try {
                JSONObject o = array.getJSONObject(i);
                if (tag.equals(o.optString("tag"))) {
                    continue;
                }
                out.put(o);
            } catch (Exception ignore) {}
        }
        writeArray(out);
    }

    /** Distinct tag names used inside one chat, most recent first. */
    public static List<String> getTagsInDialog(long dialogId) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        JSONArray array = readArray();
        for (int i = array.length() - 1; i >= 0; i--) {
            try {
                JSONObject o = array.getJSONObject(i);
                if (o.optLong("dialog_id") != dialogId) {
                    continue;
                }
                String tag = o.optString("tag");
                if (!TextUtils.isEmpty(tag)) {
                    tags.add(tag);
                }
            } catch (Exception ignore) {}
        }
        return new ArrayList<>(tags);
    }

    public static void clearAll() {
        prefs().edit().remove(KEY).apply();
        invalidateIndex();
    }

    public static int count() {
        return readArray().length();
    }
}
