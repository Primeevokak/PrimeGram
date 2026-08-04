package org.telegram.messenger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * PrimeGram: keeps a feed post visible for a while after it gets marked read, instead of letting
 * it vanish from the list the instant {@code read_inbox_max_id} moves past it.
 *
 * <p>The feed re-queries "unread" from the database on every reload, and marking a post read is
 * exactly what happens while someone is scrolling past it - the read receipt and the next reload
 * race each other, and the post loses: it drops out of the query window mid-scroll, and every row
 * below it jumps up to fill the gap. Tracking "first seen in the feed" here and keeping a post in
 * the query for a while after that, independent of its real read state, is what keeps the list
 * from moving under the reader's thumb.
 *
 * <p>In-memory only, process lifetime. A restart is already a natural reset point for "what's
 * still worth showing a few minutes longer" - nothing here needs to survive it.
 */
public final class PrimeFeedReadState {

    private static final long STAY_VISIBLE_MS = 18 * 60 * 1000L;

    private static final HashMap<Long, HashMap<Integer, Long>> seenAt = new HashMap<>();

    private PrimeFeedReadState() {
    }

    /** Called the first time a post is loaded into the feed - a no-op for a post already tracked,
     *  so scrolling past it again doesn't push its expiry back out. */
    public static synchronized void markSeen(long dialogId, int messageId) {
        HashMap<Integer, Long> byMessage = seenAt.get(dialogId);
        if (byMessage == null) {
            byMessage = new HashMap<>();
            seenAt.put(dialogId, byMessage);
        }
        if (!byMessage.containsKey(messageId)) {
            byMessage.put(messageId, System.currentTimeMillis());
        }
    }

    /** Message ids in this dialog that should stay in the feed query even if no longer unread -
     *  still inside their stay-visible window. Expired entries are dropped as a side effect. */
    public static synchronized List<Integer> stillVisibleIds(long dialogId) {
        final HashMap<Integer, Long> byMessage = seenAt.get(dialogId);
        if (byMessage == null || byMessage.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        final long now = System.currentTimeMillis();
        final List<Integer> result = new ArrayList<>();
        final Iterator<Map.Entry<Integer, Long>> it = byMessage.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<Integer, Long> entry = it.next();
            if (now - entry.getValue() > STAY_VISIBLE_MS) {
                it.remove();
            } else {
                result.add(entry.getKey());
            }
        }
        if (byMessage.isEmpty()) {
            seenAt.remove(dialogId);
        }
        return result;
    }

    /** Every dialog id currently holding a tracked post - checked alongside "genuinely unread"
     *  dialogs so a dialog that just got fully read doesn't lose its still-visible posts along
     *  with its unread badge. */
    public static synchronized List<Long> trackedDialogIds() {
        return new ArrayList<>(seenAt.keySet());
    }
}
