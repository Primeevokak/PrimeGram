package org.telegram.messenger.music;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLiteException;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.File;

/**
 * PrimeGram Music: a standalone SQLite database for the custom music player's own
 * library data (tracks, mood tags, playlists, play history) - deliberately its own
 * file, never touching stock Telegram's `cache4.db`/`messages_v2`/`media_v4`.
 *
 * <p>This is the first Prime-authored SQLite table in this fork - every other
 * Prime-added structured store ({@code MessageTagsStore}, {@code TempSubStore},
 * {@code PrimeAutoDelete}) is a JSON blob in {@code SharedPreferences}, explicitly
 * scoped by {@code MessageTagsStore}'s own doc comment to "dozens of items, not
 * thousands." A play-history log that grows by one row per play, backing a
 * personalized recommendation score, is exactly the access pattern that convention
 * was never meant to carry - rewriting one JSON array on every single track play would
 * be real, needless overhead. Uses the same vendored {@code org.telegram.SQLite.*}
 * native wrapper {@code MessagesStorage} itself uses (see that class's own
 * {@code new SQLiteDatabase(cacheFile.getPath())} for the convention this mirrors),
 * just a genuinely separate `.db` file.
 */
public final class PrimeMusicDatabase {

    private static volatile PrimeMusicDatabase instance;

    public static PrimeMusicDatabase getInstance() {
        PrimeMusicDatabase local = instance;
        if (local == null) {
            synchronized (PrimeMusicDatabase.class) {
                local = instance;
                if (local == null) {
                    instance = local = new PrimeMusicDatabase();
                }
            }
        }
        return local;
    }

    private SQLiteDatabase database;
    private final Object lock = new Object();

    private PrimeMusicDatabase() {
        open();
    }

    private void open() {
        try {
            File file = new File(ApplicationLoader.getFilesDirFixed(), "primegram_music.db");
            database = new SQLiteDatabase(file.getPath());
            database.executeFast("PRAGMA journal_mode = WAL").stepThis().dispose();
            database.executeFast("PRAGMA synchronous = NORMAL").stepThis().dispose();
            createSchema();
        } catch (SQLiteException e) {
            FileLog.e(e);
            database = null;
        }
    }

    private void createSchema() throws SQLiteException {
        // source: 0 = a real Telegram message (dialog_id/message_id populated),
        // 1 = a downloaded local-library track (local_path populated) - see
        // SelectAudioAlert.java's synthetic-MessageObject precedent this mirrors for
        // actually playing either kind through the same MediaController pipeline.
        database.executeFast("CREATE TABLE IF NOT EXISTS tracks (" +
                "id INTEGER PRIMARY KEY, " +
                "source INTEGER, " +
                "dialog_id INTEGER, " +
                "message_id INTEGER, " +
                "local_path TEXT, " +
                "title TEXT, " +
                "artist TEXT, " +
                "duration INTEGER, " +
                "cover_path TEXT, " +
                "liked INTEGER, " +
                "lyrics TEXT, " +
                "added_at INTEGER)").stepThis().dispose();

        // Added after `tracks` first shipped - SQLite has no `ADD COLUMN IF NOT EXISTS`,
        // so each runs once successfully and fails harmlessly ("duplicate column") on
        // every later launch. No real migration/versioning framework needed: this DB
        // never shipped to a real user before these columns existed.
        addColumnIfMissing("tracks", "album", "TEXT");
        addColumnIfMissing("tracks", "genre", "TEXT");
        addColumnIfMissing("tracks", "date_added", "INTEGER");
        // A device-scanned file wrongly categorized as music (a voice memo, a game sound
        // effect, whatever MediaStore's own IS_MUSIC flag got wrong) needs a way to be
        // hidden that a rescan won't silently undo - a hard DELETE doesn't survive the
        // next sync, since findOrAddDeviceTrack would just re-insert the same file. This
        // flag does: findOrAddDeviceTrack's UPDATE never touches it, so once set it stays
        // set across rescans, and every read query below filters it out.
        addColumnIfMissing("tracks", "excluded", "INTEGER");
        // Separate from `excluded` on purpose - "не нравится" is a taste signal the
        // recommendation/wave scoring reads, while `excluded` means "not real music,
        // hide it everywhere." A disliked track still shows up in the library/search,
        // it just stops being offered by the wave/recommendations.
        addColumnIfMissing("tracks", "disliked", "INTEGER");
        // How a play ended: the furthest point reached (0..1) and a coarse outcome (see
        // PrimeMusicLibrary.OUTCOME_*). Without it a skip after two seconds counted exactly
        // like listening to the end, and the wave kept offering what the user kept skipping.
        addColumnIfMissing("play_history", "progress", "REAL");
        addColumnIfMissing("play_history", "outcome", "INTEGER");
        // Moods are tags the user promoted to a category: shown as chips on the home screen,
        // with an emoji, in the user's own order.
        addColumnIfMissing("tags", "mood", "INTEGER");
        addColumnIfMissing("tags", "emoji", "TEXT");
        addColumnIfMissing("tags", "position", "INTEGER");

        database.executeFast("CREATE TABLE IF NOT EXISTS tags (" +
                "id INTEGER PRIMARY KEY, " +
                "name TEXT UNIQUE)").stepThis().dispose();

        database.executeFast("CREATE TABLE IF NOT EXISTS track_tags (" +
                "track_id INTEGER, " +
                "tag_id INTEGER, " +
                "PRIMARY KEY(track_id, tag_id))").stepThis().dispose();

        database.executeFast("CREATE TABLE IF NOT EXISTS playlists (" +
                "id INTEGER PRIMARY KEY, " +
                "name TEXT, " +
                "created_at INTEGER)").stepThis().dispose();

        database.executeFast("CREATE TABLE IF NOT EXISTS playlist_tracks (" +
                "playlist_id INTEGER, " +
                "track_id INTEGER, " +
                "position INTEGER, " +
                "PRIMARY KEY(playlist_id, track_id))").stepThis().dispose();

        // One row per play, not an aggregate counter - play_count/last_played_at per
        // track are cheap queries over this table (see PrimeMusicLibrary), which keeps
        // recency-weighted recommendation scoring possible without a second
        // update-on-every-play write path to keep in sync.
        database.executeFast("CREATE TABLE IF NOT EXISTS play_history (" +
                "track_id INTEGER, " +
                "played_at INTEGER)").stepThis().dispose();

        database.executeFast("CREATE INDEX IF NOT EXISTS idx_play_history_track ON play_history(track_id)").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS idx_track_tags_track ON track_tags(track_id)").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS idx_track_tags_tag ON track_tags(tag_id)").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS idx_playlist_tracks_playlist ON playlist_tracks(playlist_id)").stepThis().dispose();
    }

    private void addColumnIfMissing(String table, String column, String type) {
        try {
            database.executeFast("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type).stepThis().dispose();
        } catch (SQLiteException ignored) {
            // Column already exists from a previous launch - expected on every run after the first.
        }
    }

    // ---- Low-level access for PrimeMusicLibrary - kept package-private, callers should
    //      go through the facade rather than writing SQL directly all over the UI code. ----

    interface CursorReader<T> {
        T read(SQLiteCursor cursor) throws SQLiteException;
    }

    interface CursorConsumer {
        void accept(SQLiteCursor cursor) throws SQLiteException;
    }

    /** Runs {@code query} and returns the first row read by {@code reader}, or null if
     *  no rows matched or the database isn't available. */
    <T> T queryOne(String query, Object[] args, CursorReader<T> reader) {
        synchronized (lock) {
            if (database == null) {
                return null;
            }
            SQLiteCursor cursor = null;
            try {
                cursor = database.queryFinalized(query, args);
                if (cursor.next()) {
                    return reader.read(cursor);
                }
                return null;
            } catch (SQLiteException e) {
                FileLog.e(e);
                return null;
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
        }
    }

    /** Runs {@code query}, calling {@code consumer} once per row. */
    void queryEach(String query, Object[] args, CursorConsumer consumer) {
        synchronized (lock) {
            if (database == null) {
                return;
            }
            SQLiteCursor cursor = null;
            try {
                cursor = database.queryFinalized(query, args);
                while (cursor.next()) {
                    consumer.accept(cursor);
                }
            } catch (SQLiteException e) {
                FileLog.e(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
        }
    }

    /** Runs an INSERT/UPDATE/DELETE with the given bound args (positional, `?`
     *  placeholders) - mirrors {@code MessagesStorage}'s own
     *  {@code executeFast(sql).bindLong(1, ...).step().dispose()} convention exactly,
     *  just wrapped so callers don't repeat the bind-by-type dispatch every time.
     *  Returns {@code last_insert_rowid()} - only meaningful right after an INSERT,
     *  ignore it for UPDATE/DELETE. -1 on any failure (including "no database"). */
    long write(String sql, Object... args) {
        synchronized (lock) {
            if (database == null) {
                return -1;
            }
            try {
                SQLitePreparedStatement statement = database.executeFast(sql);
                for (int i = 0; i < args.length; i++) {
                    bindArg(statement, i + 1, args[i]);
                }
                statement.step();
                statement.dispose();
                return lastInsertRowId();
            } catch (SQLiteException e) {
                FileLog.e(e);
                return -1;
            }
        }
    }

    private static void bindArg(SQLitePreparedStatement statement, int index, Object value) throws SQLiteException {
        if (value == null) {
            statement.bindNull(index);
        } else if (value instanceof Integer) {
            statement.bindInteger(index, (Integer) value);
        } else if (value instanceof Long) {
            statement.bindLong(index, (Long) value);
        } else if (value instanceof Double) {
            statement.bindDouble(index, (Double) value);
        } else if (value instanceof String) {
            statement.bindString(index, (String) value);
        } else {
            throw new IllegalArgumentException("PrimeMusicDatabase: unsupported bind type " + value.getClass());
        }
    }

    private long lastInsertRowId() {
        try {
            SQLiteCursor cursor = database.queryFinalized("SELECT last_insert_rowid()");
            try {
                return cursor.next() ? cursor.longValue(0) : -1;
            } finally {
                cursor.dispose();
            }
        } catch (SQLiteException e) {
            return -1;
        }
    }
}
