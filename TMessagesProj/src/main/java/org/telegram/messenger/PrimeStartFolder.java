package org.telegram.messenger;

import android.content.SharedPreferences;

import java.util.ArrayList;

/**
 * PrimeGram: which folder the chat list opens on.
 *
 * <p>Off by default - Telegram opens on the first tab and so does this, until asked otherwise.
 * "Last" reopens whichever folder was on screen when the app was last used; "chosen" always opens
 * one folder the user picked. Folders are remembered by their server id: the in-memory
 * {@code localId} is a counter that restarts with the process, so it cannot survive a restart.
 * The default "All chats" folder has no server id of its own and is stored as {@link #ALL_CHATS}.
 */
public final class PrimeStartFolder {

    public static final int MODE_OFF = 0;
    public static final int MODE_LAST = 1;
    public static final int MODE_CHOSEN = 2;

    public static final int ALL_CHATS = -1;

    private static final String KEY_MODE = "primegram_start_folder_mode";
    private static final String KEY_CHOSEN = "primegram_start_folder_chosen_";
    private static final String KEY_LAST = "primegram_start_folder_last_";

    private PrimeStartFolder() {
    }

    private static SharedPreferences prefs() {
        return MessagesController.getGlobalMainSettings();
    }

    public static int mode() {
        return prefs().getInt(KEY_MODE, MODE_OFF);
    }

    public static void setMode(int mode) {
        prefs().edit().putInt(KEY_MODE, mode).apply();
    }

    public static int chosen(int account) {
        return prefs().getInt(KEY_CHOSEN + account, ALL_CHATS);
    }

    public static void setChosen(int account, int folderId) {
        prefs().edit().putInt(KEY_CHOSEN + account, folderId).apply();
    }

    public static int keyOf(MessagesController.DialogFilter filter) {
        return filter == null || filter.isDefault() ? ALL_CHATS : filter.id;
    }

    /** The chat list switched to the folder at {@code index}; remembered for {@link #MODE_LAST}. */
    public static void rememberLast(int account, ArrayList<MessagesController.DialogFilter> filters, int index) {
        if (mode() != MODE_LAST || filters == null || index < 0 || index >= filters.size()) {
            return;
        }
        final int key = keyOf(filters.get(index));
        if (prefs().getInt(KEY_LAST + account, ALL_CHATS) != key) {
            prefs().edit().putInt(KEY_LAST + account, key).apply();
        }
    }

    /** Index into {@code filters} to open on, or -1 to keep Telegram's own choice. */
    public static int startIndex(int account, ArrayList<MessagesController.DialogFilter> filters) {
        final int mode = mode();
        if (mode == MODE_OFF || filters == null || filters.isEmpty()) {
            return -1;
        }
        final int key = mode == MODE_LAST ? prefs().getInt(KEY_LAST + account, ALL_CHATS) : chosen(account);
        for (int i = 0; i < filters.size(); i++) {
            final MessagesController.DialogFilter f = filters.get(i);
            if (keyOf(f) == key && !f.locked) {
                return i;
            }
        }
        return -1;
    }
}
