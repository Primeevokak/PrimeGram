package org.telegram.messenger;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Local archive of messages that were deleted by their sender.
 *
 * <p>Deliberately its own SQLite file rather than a table inside Telegram's
 * {@code MessagesStorage}: touching that schema would drag in a migration and put the
 * whole message database at risk for a purely optional feature.
 *
 * <p>Growth is bounded by {@link #getMaxEntries()} — once the cap is reached the oldest
 * rows are dropped to make room, so the archive can never grow without limit.
 */
public class DeletedMessagesStore {

    private static final String DB_NAME = "primegram_deleted.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "deleted_messages";

    public static final int DEFAULT_MAX_ENTRIES = 2000;
    private static final String KEY_MAX_ENTRIES = "primegram_deleted_max";

    /** Rows are pruned in batches so we aren't running a DELETE on every single insert. */
    private static final int PRUNE_SLACK = 100;

    public static class Entry {
        public long dialogId;
        public int messageId;
        public long fromId;
        public int date;
        public long deletedAt;
        public String text;
    }

    private static DeletedMessagesStore instance;
    private final Helper helper;

    private DeletedMessagesStore(Context context) {
        helper = new Helper(context.getApplicationContext());
    }

    public static synchronized DeletedMessagesStore getInstance() {
        if (instance == null) {
            instance = new DeletedMessagesStore(ApplicationLoader.applicationContext);
        }
        return instance;
    }

    private static class Helper extends SQLiteOpenHelper {
        Helper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + TABLE + " ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "dialog_id INTEGER,"
                    + "message_id INTEGER,"
                    + "from_id INTEGER,"
                    + "date INTEGER,"
                    + "deleted_at INTEGER,"
                    + "text TEXT)");
            db.execSQL("CREATE INDEX idx_dialog ON " + TABLE + " (dialog_id)");
            db.execSQL("CREATE UNIQUE INDEX idx_msg ON " + TABLE + " (dialog_id, message_id)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE);
            onCreate(db);
        }
    }

    public static int getMaxEntries() {
        try {
            return MessagesController.getGlobalMainSettings().getInt(KEY_MAX_ENTRIES, DEFAULT_MAX_ENTRIES);
        } catch (Throwable t) {
            return DEFAULT_MAX_ENTRIES;
        }
    }

    public static void setMaxEntries(int value) {
        MessagesController.getGlobalMainSettings().edit().putInt(KEY_MAX_ENTRIES, value).apply();
    }

    /** Stores one deleted message. Never throws — archiving must not break deletion. */
    public void save(long dialogId, int messageId, long fromId, int date, String text) {
        if (TextUtils.isEmpty(text)) {
            // Media-only messages carry nothing readable to archive; the file itself is
            // handled by Telegram's own cache and may already be gone.
            text = "";
        }
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put("dialog_id", dialogId);
            values.put("message_id", messageId);
            values.put("from_id", fromId);
            values.put("date", date);
            values.put("deleted_at", System.currentTimeMillis());
            values.put("text", text);
            db.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            pruneIfNeeded(db);
        } catch (Throwable t) {
            FileLog.e("DeletedMessagesStore.save", t);
        }
    }

    private void pruneIfNeeded(SQLiteDatabase db) {
        try {
            int max = getMaxEntries();
            if (max <= 0) {
                return;
            }
            long count = android.database.DatabaseUtils.queryNumEntries(db, TABLE);
            if (count <= max + PRUNE_SLACK) {
                return;
            }
            // Drop the oldest rows so newest ones always fit — "auto-clear old for new".
            long toDelete = count - max;
            db.execSQL("DELETE FROM " + TABLE + " WHERE id IN (SELECT id FROM " + TABLE
                    + " ORDER BY deleted_at ASC LIMIT " + toDelete + ")");
        } catch (Throwable t) {
            FileLog.e("DeletedMessagesStore.prune", t);
        }
    }

    /** Newest first. {@code dialogId == 0} means "every chat". */
    public List<Entry> query(long dialogId, int limit) {
        ArrayList<Entry> result = new ArrayList<>();
        Cursor cursor = null;
        try {
            SQLiteDatabase db = helper.getReadableDatabase();
            String where = dialogId != 0 ? "dialog_id = ?" : null;
            String[] args = dialogId != 0 ? new String[]{String.valueOf(dialogId)} : null;
            cursor = db.query(TABLE, null, where, args, null, null, "deleted_at DESC", String.valueOf(limit));
            while (cursor.moveToNext()) {
                Entry e = new Entry();
                e.dialogId = cursor.getLong(cursor.getColumnIndexOrThrow("dialog_id"));
                e.messageId = cursor.getInt(cursor.getColumnIndexOrThrow("message_id"));
                e.fromId = cursor.getLong(cursor.getColumnIndexOrThrow("from_id"));
                e.date = cursor.getInt(cursor.getColumnIndexOrThrow("date"));
                e.deletedAt = cursor.getLong(cursor.getColumnIndexOrThrow("deleted_at"));
                e.text = cursor.getString(cursor.getColumnIndexOrThrow("text"));
                result.add(e);
            }
        } catch (Throwable t) {
            FileLog.e("DeletedMessagesStore.query", t);
        } finally {
            if (cursor != null) {
                try { cursor.close(); } catch (Throwable ignore) {}
            }
        }
        return result;
    }

    public long count() {
        try {
            return android.database.DatabaseUtils.queryNumEntries(helper.getReadableDatabase(), TABLE);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Approximate on-disk size of the archive, in bytes. */
    public long sizeOnDisk() {
        try {
            java.io.File file = ApplicationLoader.applicationContext.getDatabasePath(DB_NAME);
            return file != null && file.exists() ? file.length() : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    public void clear() {
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.execSQL("DELETE FROM " + TABLE);
            db.execSQL("VACUUM");
        } catch (Throwable t) {
            FileLog.e("DeletedMessagesStore.clear", t);
        }
    }
}
