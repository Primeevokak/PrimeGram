package org.telegram.messenger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * PrimeGram: badges next to a name - "supported PrimeGram development", a channel we vouch for,
 * whatever a future list wants to say. exteraGram runs the same idea against their own backend;
 * this runs it against one JSON file, because we have no backend and do not want one for something
 * this small - a plain HTTPS GET is one fewer service to keep online.
 *
 * <p>The file lives on the docs site this project already publishes to GitHub Pages
 * ({@code https://primeevokak.github.io/PrimeGram/badges.json}), so awarding a badge is editing and
 * pushing one file, not touching the app. Each entry names a Telegram custom emoji ({@code
 * custom_emoji_id}) to draw as the badge and an optional {@code text} for the bulletin a tap on it
 * shows - matching exactly what {@code AnimatedEmojiDrawable.findDocument(account, id)} already
 * knows how to draw, since that is the same mechanism Telegram Premium's own name badges use.
 *
 * <p>This is the data layer; drawing lives per screen. {@code ProfileActivity} wires both user and
 * chat/channel badges into the name row's {@code rightDrawable3} slot, with a tap showing the
 * badge's {@code text} - a message sender's name elsewhere is not done yet.
 */
public final class PrimeBadges {

    private static final String REMOTE_URL = "https://primeevokak.github.io/PrimeGram/badges.json";
    private static final String KEY_LAST_UPDATE = "primegram_badges_updated";
    private static final long REFRESH_INTERVAL = 6 * 60 * 60 * 1000L;

    public static final class Badge {
        public final long id;
        public final boolean isChat;
        public final long customEmojiId;
        public final String text;

        Badge(long id, boolean isChat, long customEmojiId, String text) {
            this.id = id;
            this.isChat = isChat;
            this.customEmojiId = customEmojiId;
            this.text = text;
        }
    }

    private static volatile Map<Long, Badge> userBadges = Collections.emptyMap();
    private static volatile Map<Long, Badge> chatBadges = Collections.emptyMap();
    private static volatile boolean loaded = false;
    private static final Object loadLock = new Object();

    private PrimeBadges() {
    }

    public static Badge getUserBadge(long userId) {
        ensureLoaded();
        return userBadges.get(userId);
    }

    public static Badge getChatBadge(long chatId) {
        ensureLoaded();
        return chatBadges.get(chatId);
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (loadLock) {
            if (!loaded) {
                loadFromDisk();
                loaded = true;
            }
        }
        maybeRefresh();
    }

    private static File storageFile() {
        return new File(ApplicationLoader.getFilesDirFixed(), "primegram_badges.json");
    }

    private static void loadFromDisk() {
        File file = storageFile();
        if (!file.exists()) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new java.io.FileInputStream(file), StandardCharsets.UTF_8))) {
            final StringBuilder sb = new StringBuilder();
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, read);
            }
            parse(sb.toString());
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    /** Parses and swaps the in-memory maps only once parsing succeeds whole, so a truncated or
     *  malformed download never replaces a working list with an empty one. */
    private static void parse(String json) throws Exception {
        final JSONArray array = new JSONArray(json);
        final HashMap<Long, Badge> users = new HashMap<>();
        final HashMap<Long, Badge> chats = new HashMap<>();
        for (int i = 0; i < array.length(); i++) {
            final JSONObject row = array.optJSONObject(i);
            if (row == null) {
                continue;
            }
            final long id = row.optLong("id");
            final long customEmojiId = row.optLong("custom_emoji_id");
            if (id == 0 || customEmojiId == 0) {
                continue;
            }
            final boolean isChat = "chat".equals(row.optString("type"));
            final Badge badge = new Badge(id, isChat, customEmojiId, row.optString("text", null));
            (isChat ? chats : users).put(id, badge);
        }
        userBadges = users;
        chatBadges = chats;
    }

    /** Refreshes at most once every {@link #REFRESH_INTERVAL} - a badge list changes rarely enough
     *  that checking on every app start would be pure waste. */
    public static void maybeRefresh() {
        final long last = MessagesController.getGlobalMainSettings().getLong(KEY_LAST_UPDATE, 0);
        if (System.currentTimeMillis() - last < REFRESH_INTERVAL) {
            return;
        }
        refresh(null);
    }

    public static void refresh(Runnable done) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                final String json = fetch(REMOTE_URL);
                parse(json);
                writeToDisk(json);
                MessagesController.getGlobalMainSettings().edit()
                        .putLong(KEY_LAST_UPDATE, System.currentTimeMillis()).apply();
            } catch (Throwable t) {
                FileLog.e(t);
            }
            if (done != null) {
                AndroidUtilities.runOnUIThread(done);
            }
        });
    }

    private static String fetch(String url) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "PrimeGram");
            final int code = connection.getResponseCode();
            if (code != 200) {
                throw new Exception("HTTP " + code);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                final StringBuilder sb = new StringBuilder();
                char[] buffer = new char[8192];
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    sb.append(buffer, 0, read);
                }
                return sb.toString();
            }
        } finally {
            if (connection != null) {
                try {
                    connection.disconnect();
                } catch (Throwable ignore) {
                }
            }
        }
    }

    private static void writeToDisk(String json) {
        final File file = storageFile();
        final File temp = new File(file.getAbsolutePath() + ".tmp");
        try (FileWriter writer = new FileWriter(temp)) {
            writer.write(json);
        } catch (Throwable t) {
            FileLog.e(t);
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
        //noinspection ResultOfMethodCallIgnored
        temp.renameTo(file);
    }
}
