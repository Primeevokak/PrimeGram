package org.telegram.messenger.music;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PrimeGram Music: the single facade the player UI and playback integration call into
 * for anything backed by {@link PrimeMusicDatabase} - tracks, mood tags, playlists,
 * likes, play history. Mirrors how {@code PrimeE2EManager} was the one facade for the
 * E2E feature; UI code should never touch {@link PrimeMusicDatabase}'s SQL directly.
 */
public final class PrimeMusicLibrary {

    private PrimeMusicLibrary() {
    }

    private static PrimeMusicDatabase db() {
        return PrimeMusicDatabase.getInstance();
    }

    // ---- Tracks ----

    /** Finds the existing row for a Telegram message (by dialogId+messageId) or
     *  inserts a new one - a track already sent/received in a chat should only ever
     *  have one library row no matter how many times it's played/tagged/liked. */
    public static long findOrAddTelegramTrack(long dialogId, int messageId, String title, String artist, int duration) {
        Long existing = db().queryOne(
                "SELECT id FROM tracks WHERE source = ? AND dialog_id = ? AND message_id = ?",
                new Object[]{PrimeTrack.SOURCE_TELEGRAM, dialogId, (long) messageId},
                cursor -> cursor.longValue(0));
        if (existing != null) {
            return existing;
        }
        return db().write(
                "INSERT INTO tracks (source, dialog_id, message_id, local_path, title, artist, duration, cover_path, liked, lyrics, added_at) " +
                        "VALUES (?, ?, ?, NULL, ?, ?, ?, NULL, 0, NULL, ?)",
                PrimeTrack.SOURCE_TELEGRAM, dialogId, (long) messageId, title, artist, duration, System.currentTimeMillis());
    }

    /** Adds a downloaded track that lives only in the local library, no backing
     *  Telegram message - see {@code SelectAudioAlert.java}'s synthetic-MessageObject
     *  precedent for how this plays back through the same `MediaController` pipeline
     *  as a real chat track. */
    public static long addLocalTrack(String localPath, String title, String artist, int duration, String coverPath) {
        return db().write(
                "INSERT INTO tracks (source, dialog_id, message_id, local_path, title, artist, duration, cover_path, liked, lyrics, added_at) " +
                        "VALUES (?, 0, 0, ?, ?, ?, ?, ?, 0, NULL, ?)",
                PrimeTrack.SOURCE_LOCAL, localPath, title, artist, duration, coverPath, System.currentTimeMillis());
    }

    /** Indexes a track found on-device via {@code PrimeDeviceMusicScanner} - dedups by
     *  {@code local_path} (unlike {@link #addLocalTrack}, which always inserts a fresh
     *  row - correct for a one-off explicit download, wrong for a rescan of the same
     *  device library re-finding the same files). Updates metadata on the existing row
     *  if the file was already indexed, in case tags changed since the last scan. */
    public static long findOrAddDeviceTrack(String localPath, String title, String artist,
                                             String album, String genre, int duration, String coverPath) {
        Long existing = db().queryOne("SELECT id FROM tracks WHERE source = ? AND local_path = ?",
                new Object[]{PrimeTrack.SOURCE_DEVICE, localPath}, cursor -> cursor.longValue(0));
        if (existing != null) {
            db().write("UPDATE tracks SET title = ?, artist = ?, album = ?, genre = ?, duration = ?, cover_path = COALESCE(?, cover_path) WHERE id = ?",
                    title, artist, album, genre, duration, coverPath, existing);
            return existing;
        }
        return db().write(
                "INSERT INTO tracks (source, dialog_id, message_id, local_path, title, artist, duration, cover_path, liked, lyrics, added_at, album, genre, date_added) " +
                        "VALUES (?, 0, 0, ?, ?, ?, ?, ?, 0, NULL, ?, ?, ?, ?)",
                PrimeTrack.SOURCE_DEVICE, localPath, title, artist, duration, coverPath,
                System.currentTimeMillis(), album, genre, System.currentTimeMillis());
    }

    public static PrimeTrack getTrack(long trackId) {
        return db().queryOne("SELECT * FROM tracks WHERE id = ?", new Object[]{trackId}, PrimeMusicLibrary::readTrack);
    }

    private static final String NOT_EXCLUDED = "(excluded IS NULL OR excluded = 0)";

    public static List<PrimeTrack> getAllTracks() {
        List<PrimeTrack> tracks = new ArrayList<>();
        db().queryEach("SELECT * FROM tracks WHERE " + NOT_EXCLUDED + " ORDER BY added_at DESC", new Object[]{},
                cursor -> tracks.add(readTrack(cursor)));
        return tracks;
    }

    public static List<PrimeTrack> getLikedTracks() {
        List<PrimeTrack> tracks = new ArrayList<>();
        db().queryEach("SELECT * FROM tracks WHERE liked = 1 AND " + NOT_EXCLUDED + " ORDER BY added_at DESC", new Object[]{},
                cursor -> tracks.add(readTrack(cursor)));
        return tracks;
    }

    public static void setLiked(long trackId, boolean liked) {
        db().write("UPDATE tracks SET liked = ? WHERE id = ?", liked ? 1 : 0, trackId);
    }

    /** Hides a device-scanned file that got miscategorized as music (a voice memo, a
     *  notification sound, whatever tripped MediaStore's own IS_MUSIC flag) - unlike
     *  {@link #deleteTrack}, this survives the next device sync: findOrAddDeviceTrack's
     *  UPDATE never clears this flag, so the file stays hidden instead of quietly
     *  reappearing on the next "Синхронизировать медиатеку". */
    public static void setExcluded(long trackId, boolean excluded) {
        db().write("UPDATE tracks SET excluded = ? WHERE id = ?", excluded ? 1 : 0, trackId);
    }

    /** Every track currently hidden via {@link #setExcluded} - backs a management
     *  screen so a track hidden by mistake (a real risk: the action is one tap in a
     *  popup menu, easy to hit on the wrong row) can be found and restored, instead of
     *  the exclude flag being a one-way trip with no way back short of re-adding the
     *  file to the device. */
    public static List<PrimeTrack> getExcludedTracks() {
        List<PrimeTrack> tracks = new ArrayList<>();
        db().queryEach("SELECT * FROM tracks WHERE excluded = 1 ORDER BY added_at DESC", new Object[]{},
                cursor -> tracks.add(readTrack(cursor)));
        return tracks;
    }

    /** "Не нравится" - unlike {@link #setExcluded}, this never hides the track from the
     *  library/search, it only stops {@link #getWaveRecommendations} from offering it.
     *  Reversible from a dedicated review screen, same reasoning as excluded tracks:
     *  the action is one tap away in a menu, easy to hit on the wrong row. */
    public static void setDisliked(long trackId, boolean disliked) {
        db().write("UPDATE tracks SET disliked = ? WHERE id = ?", disliked ? 1 : 0, trackId);
    }

    public static List<PrimeTrack> getDislikedTracks() {
        List<PrimeTrack> tracks = new ArrayList<>();
        db().queryEach("SELECT * FROM tracks WHERE disliked = 1 ORDER BY added_at DESC", new Object[]{},
                cursor -> tracks.add(readTrack(cursor)));
        return tracks;
    }

    public static void deleteTrack(long trackId) {
        db().write("DELETE FROM tracks WHERE id = ?", trackId);
        db().write("DELETE FROM track_tags WHERE track_id = ?", trackId);
        db().write("DELETE FROM playlist_tracks WHERE track_id = ?", trackId);
        db().write("DELETE FROM play_history WHERE track_id = ?", trackId);
    }

    private static PrimeTrack readTrack(SQLiteCursor cursor) throws SQLiteException {
        PrimeTrack track = new PrimeTrack();
        track.id = cursor.longValue(0);
        track.source = cursor.intValue(1);
        track.dialogId = cursor.longValue(2);
        track.messageId = cursor.intValue(3);
        track.localPath = cursor.isNull(4) ? null : cursor.stringValue(4);
        track.title = cursor.isNull(5) ? null : cursor.stringValue(5);
        track.artist = cursor.isNull(6) ? null : cursor.stringValue(6);
        track.duration = cursor.intValue(7);
        track.coverPath = cursor.isNull(8) ? null : cursor.stringValue(8);
        track.liked = cursor.intValue(9) != 0;
        track.lyrics = cursor.isNull(10) ? null : cursor.stringValue(10);
        track.addedAt = cursor.longValue(11);
        // Added later via ALTER TABLE (see PrimeMusicDatabase.addColumnIfMissing) -
        // guard against a stale cursor shape defensively even though `SELECT *` always
        // reflects the live schema by the time this runs.
        track.album = cursor.getColumnCount() > 12 && !cursor.isNull(12) ? cursor.stringValue(12) : null;
        track.genre = cursor.getColumnCount() > 13 && !cursor.isNull(13) ? cursor.stringValue(13) : null;
        track.dateAdded = cursor.getColumnCount() > 14 && !cursor.isNull(14) ? cursor.longValue(14) : 0L;
        // Column 15 is `excluded` (read directly by name-filtered queries, never needed
        // on the PrimeTrack object itself), 16 is `disliked`.
        track.disliked = cursor.getColumnCount() > 16 && !cursor.isNull(16) && cursor.intValue(16) != 0;
        return track;
    }

    public static void setLyrics(long trackId, String lyrics) {
        db().write("UPDATE tracks SET lyrics = ? WHERE id = ?", lyrics, trackId);
    }

    // ---- Mood tags ----

    /** A mood: a tag the user made into a category. */
    public static final class Mood {
        public long id;
        public String name;
        public String emoji;
        public int trackCount;

        public String label() {
            return emoji != null && !emoji.isEmpty() ? emoji + " " + name : name;
        }
    }

    private static final String PREF_MOODS_SEEDED = "prime_music_moods_seeded";

    /** The first time moods are asked for, the three the home screen always offered become real
     *  categories - reusing a lower-case tag of the same name if the user had typed one. */
    private static void seedMoodsOnce() {
        final android.content.SharedPreferences prefs = org.telegram.messenger.MessagesController.getGlobalMainSettings();
        if (prefs.getBoolean(PREF_MOODS_SEEDED, false)) {
            return;
        }
        final String[][] seeds = {{"Бодрое", "⚡", "бодрое"}, {"Спокойное", "🌿", "спокойное"}, {"Вечер", "🌙", "вечер"}};
        int position = 0;
        for (String[] seed : seeds) {
            Long existing = db().queryOne("SELECT id FROM tags WHERE name = ?", new Object[]{seed[2]}, c -> c.longValue(0));
            long id = existing != null ? existing : findOrCreateTag(seed[0]);
            if (existing != null) {
                db().write("UPDATE tags SET name = ? WHERE id = ?", seed[0], id);
            }
            db().write("UPDATE tags SET mood = 1, emoji = ?, position = ? WHERE id = ?", seed[1], position++, id);
        }
        prefs.edit().putBoolean(PREF_MOODS_SEEDED, true).apply();
    }

    public static List<Mood> getMoods() {
        seedMoodsOnce();
        List<Mood> moods = new ArrayList<>();
        db().queryEach("SELECT tags.id, tags.name, tags.emoji, " +
                        "(SELECT COUNT(*) FROM track_tags JOIN tracks ON tracks.id = track_tags.track_id " +
                        "WHERE track_tags.tag_id = tags.id AND (tracks.excluded IS NULL OR tracks.excluded = 0)) " +
                        "FROM tags WHERE mood = 1 ORDER BY position, id",
                new Object[]{}, cursor -> {
                    Mood m = new Mood();
                    m.id = cursor.longValue(0);
                    m.name = cursor.stringValue(1);
                    m.emoji = cursor.isNull(2) ? null : cursor.stringValue(2);
                    m.trackCount = cursor.intValue(3);
                    moods.add(m);
                });
        return moods;
    }

    public static long createMood(String name, String emoji) {
        final long id = findOrCreateTag(name.trim());
        Integer max = db().queryOne("SELECT MAX(position) FROM tags WHERE mood = 1", new Object[]{}, c -> c.isNull(0) ? -1 : c.intValue(0));
        db().write("UPDATE tags SET mood = 1, emoji = ?, position = ? WHERE id = ?", emoji, (max == null ? -1 : max) + 1, id);
        return id;
    }

    /** @return false when another tag already has that name */
    public static boolean renameMood(long id, String name, String emoji) {
        Long clash = db().queryOne("SELECT id FROM tags WHERE name = ? AND id != ?", new Object[]{name.trim(), id}, c -> c.longValue(0));
        if (clash != null) {
            return false;
        }
        db().write("UPDATE tags SET name = ?, emoji = ? WHERE id = ?", name.trim(), emoji, id);
        return true;
    }

    public static void deleteMood(long id) {
        db().write("DELETE FROM track_tags WHERE tag_id = ?", id);
        db().write("DELETE FROM tags WHERE id = ?", id);
    }

    public static java.util.Set<Long> getTagIds(long trackId) {
        java.util.Set<Long> ids = new java.util.HashSet<>();
        db().queryEach("SELECT tag_id FROM track_tags WHERE track_id = ?", new Object[]{trackId}, c -> ids.add(c.longValue(0)));
        return ids;
    }

    public static void setTrackTag(long trackId, long tagId, boolean on) {
        if (on) {
            db().write("INSERT OR IGNORE INTO track_tags (track_id, tag_id) VALUES (?, ?)", trackId, tagId);
        } else {
            db().write("DELETE FROM track_tags WHERE track_id = ? AND tag_id = ?", trackId, tagId);
        }
    }

    /** The track's tags that are not moods - the free-form ones. */
    public static List<String> getPlainTags(long trackId) {
        List<String> tags = new ArrayList<>();
        db().queryEach(
                "SELECT tags.name FROM tags JOIN track_tags ON tags.id = track_tags.tag_id " +
                        "WHERE track_tags.track_id = ? AND (tags.mood IS NULL OR tags.mood = 0) ORDER BY tags.name",
                new Object[]{trackId}, cursor -> tags.add(cursor.stringValue(0)));
        return tags;
    }

    /** Replaces only the free-form tags, leaving the track's moods as they are. */
    public static void setPlainTags(long trackId, List<String> tagNames) {
        db().write("DELETE FROM track_tags WHERE track_id = ? AND tag_id IN (SELECT id FROM tags WHERE mood IS NULL OR mood = 0)", trackId);
        for (String name : tagNames) {
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            long tagId = findOrCreateTag(name.trim());
            db().write("INSERT OR IGNORE INTO track_tags (track_id, tag_id) VALUES (?, ?)", trackId, tagId);
        }
    }

    private static long findOrCreateTag(String name) {
        Long existing = db().queryOne("SELECT id FROM tags WHERE name = ?", new Object[]{name}, cursor -> cursor.longValue(0));
        if (existing != null) {
            return existing;
        }
        return db().write("INSERT INTO tags (name) VALUES (?)", name);
    }

    /** Replaces every tag currently on {@code trackId} with {@code tagNames} - the tag
     *  picker UI (phase 7) always submits the full desired set, not a diff. */
    public static void setTags(long trackId, List<String> tagNames) {
        db().write("DELETE FROM track_tags WHERE track_id = ?", trackId);
        for (String name : tagNames) {
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            long tagId = findOrCreateTag(name.trim());
            db().write("INSERT OR IGNORE INTO track_tags (track_id, tag_id) VALUES (?, ?)", trackId, tagId);
        }
    }

    public static List<String> getTags(long trackId) {
        List<String> tags = new ArrayList<>();
        db().queryEach(
                "SELECT tags.name FROM tags JOIN track_tags ON tags.id = track_tags.tag_id WHERE track_tags.track_id = ? ORDER BY tags.name",
                new Object[]{trackId}, cursor -> tags.add(cursor.stringValue(0)));
        return tags;
    }

    public static List<PrimeTrack> getTracksWithTag(String tagName) {
        List<PrimeTrack> tracks = new ArrayList<>();
        db().queryEach(
                "SELECT tracks.* FROM tracks " +
                        "JOIN track_tags ON tracks.id = track_tags.track_id " +
                        "JOIN tags ON tags.id = track_tags.tag_id " +
                        "WHERE tags.name = ? ORDER BY tracks.added_at DESC",
                new Object[]{tagName}, cursor -> tracks.add(readTrack(cursor)));
        return tracks;
    }

    // ---- Playlists ----

    public static long createPlaylist(String name) {
        return db().write("INSERT INTO playlists (name, created_at) VALUES (?, ?)", name, System.currentTimeMillis());
    }

    public static void renamePlaylist(long playlistId, String name) {
        db().write("UPDATE playlists SET name = ? WHERE id = ?", name, playlistId);
    }

    public static void deletePlaylist(long playlistId) {
        db().write("DELETE FROM playlists WHERE id = ?", playlistId);
        db().write("DELETE FROM playlist_tracks WHERE playlist_id = ?", playlistId);
    }

    public static List<PrimePlaylist> getPlaylists() {
        List<PrimePlaylist> playlists = new ArrayList<>();
        db().queryEach("SELECT id, name, created_at FROM playlists ORDER BY created_at DESC", new Object[]{}, cursor -> {
            PrimePlaylist playlist = new PrimePlaylist();
            playlist.id = cursor.longValue(0);
            playlist.name = cursor.stringValue(1);
            playlist.createdAt = cursor.longValue(2);
            playlists.add(playlist);
        });
        return playlists;
    }

    public static void addToPlaylist(long playlistId, long trackId) {
        Integer maxPosition = db().queryOne("SELECT MAX(position) FROM playlist_tracks WHERE playlist_id = ?",
                new Object[]{playlistId}, cursor -> cursor.isNull(0) ? -1 : cursor.intValue(0));
        int position = (maxPosition == null ? -1 : maxPosition) + 1;
        db().write("INSERT OR IGNORE INTO playlist_tracks (playlist_id, track_id, position) VALUES (?, ?, ?)",
                playlistId, trackId, position);
    }

    public static void removeFromPlaylist(long playlistId, long trackId) {
        db().write("DELETE FROM playlist_tracks WHERE playlist_id = ? AND track_id = ?", playlistId, trackId);
    }

    public static List<PrimeTrack> getPlaylistTracks(long playlistId) {
        List<PrimeTrack> tracks = new ArrayList<>();
        db().queryEach(
                "SELECT tracks.* FROM tracks JOIN playlist_tracks ON tracks.id = playlist_tracks.track_id " +
                        "WHERE playlist_tracks.playlist_id = ? ORDER BY playlist_tracks.position",
                new Object[]{playlistId}, cursor -> tracks.add(readTrack(cursor)));
        return tracks;
    }

    // ---- Grouped library browsing (Альбомы/Артисты/Жанры/Папки) ----
    //
    // In-memory grouping over getAllTracks(), same "library is hundreds of tracks, not
    // thousands" scale assumption the recommendation scorer already uses - simpler and
    // safer than hand-written SQL GROUP BY across a table that keeps growing new
    // nullable columns.

    private static final String UNKNOWN_ALBUM = "Неизвестный альбом";
    private static final String UNKNOWN_ARTIST = "Неизвестный исполнитель";
    private static final String UNKNOWN_GENRE = "Без жанра";
    private static final String TELEGRAM_PSEUDO_FOLDER = "PrimeGram";

    public static List<PrimeMusicGroup> getAlbums() {
        return groupBy(track -> nonEmpty(track.album, UNKNOWN_ALBUM), (group, track) -> {
            if (group.subtitle == null || group.subtitle.isEmpty()) {
                group.subtitle = nonEmpty(track.artist, UNKNOWN_ARTIST);
            }
        });
    }

    public static List<PrimeMusicGroup> getArtists() {
        return groupBy(track -> nonEmpty(track.artist, UNKNOWN_ARTIST), (group, track) -> {
        });
    }

    public static List<PrimeMusicGroup> getGenres() {
        return groupBy(track -> nonEmpty(track.genre, UNKNOWN_GENRE), (group, track) -> {
        });
    }

    public static List<PrimeMusicGroup> getFolders() {
        return groupBy(PrimeMusicLibrary::folderKeyFor, (group, track) -> {
        });
    }

    private static String folderKeyFor(PrimeTrack track) {
        if (track.localPath == null || track.localPath.isEmpty()) {
            return TELEGRAM_PSEUDO_FOLDER;
        }
        int slash = track.localPath.lastIndexOf('/');
        return slash > 0 ? track.localPath.substring(0, slash) : TELEGRAM_PSEUDO_FOLDER;
    }

    private static String nonEmpty(String value, String fallback) {
        return value != null && !value.trim().isEmpty() ? value : fallback;
    }

    private interface KeyOf {
        String key(PrimeTrack track);
    }

    private interface OnGroup {
        void accept(PrimeMusicGroup group, PrimeTrack track);
    }

    private static List<PrimeMusicGroup> groupBy(KeyOf keyOf, OnGroup onGroup) {
        Map<String, PrimeMusicGroup> byKey = new java.util.LinkedHashMap<>();
        for (PrimeTrack track : getAllTracks()) {
            String key = keyOf.key(track);
            PrimeMusicGroup group = byKey.get(key);
            if (group == null) {
                group = new PrimeMusicGroup();
                group.key = key;
                group.title = key;
                byKey.put(key, group);
            }
            group.trackCount++;
            if (group.coverPath == null && track.coverPath != null) {
                group.coverPath = track.coverPath;
            }
            onGroup.accept(group, track);
        }
        List<PrimeMusicGroup> groups = new ArrayList<>(byKey.values());
        for (PrimeMusicGroup group : groups) {
            if (group.subtitle == null) {
                group.subtitle = group.trackCount + " " + trackWord(group.trackCount);
            } else {
                group.subtitle = group.subtitle + " • " + group.trackCount + " " + trackWord(group.trackCount);
            }
        }
        Collections.sort(groups, (a, b) -> a.title.compareToIgnoreCase(b.title));
        return groups;
    }

    private static String trackWord(int count) {
        int mod100 = count % 100;
        int mod10 = count % 10;
        if (mod100 >= 11 && mod100 <= 14) {
            return "треков";
        }
        if (mod10 == 1) {
            return "трек";
        }
        if (mod10 >= 2 && mod10 <= 4) {
            return "трека";
        }
        return "треков";
    }

    /** Fetches every track belonging to the given group - {@code groupType} is one of
     *  {@link PrimeMusicGroup}'s {@code TYPE_*} constants, {@code key} is the group's
     *  own {@code PrimeMusicGroup.key}. */
    public static List<PrimeTrack> getTracksForGroup(int groupType, String key) {
        List<PrimeTrack> result = new ArrayList<>();
        for (PrimeTrack track : getAllTracks()) {
            String trackKey;
            switch (groupType) {
                case PrimeMusicGroup.TYPE_ALBUM:
                    trackKey = nonEmpty(track.album, UNKNOWN_ALBUM);
                    break;
                case PrimeMusicGroup.TYPE_ARTIST:
                    trackKey = nonEmpty(track.artist, UNKNOWN_ARTIST);
                    break;
                case PrimeMusicGroup.TYPE_GENRE:
                    trackKey = nonEmpty(track.genre, UNKNOWN_GENRE);
                    break;
                case PrimeMusicGroup.TYPE_FOLDER:
                    trackKey = folderKeyFor(track);
                    break;
                default:
                    trackKey = null;
            }
            if (key.equals(trackKey)) {
                result.add(track);
            }
        }
        return result;
    }

    // ---- Play history ----

    /** Called once per real playback start - see the playback-integration hook (phase
     *  2) for where this gets wired to actual `MediaController` playback events. */
    public static final int OUTCOME_UNKNOWN = 0;
    public static final int OUTCOME_COMPLETED = 1;
    public static final int OUTCOME_SKIPPED = 2;
    public static final int OUTCOME_PARTIAL = 3;

    /** @return the play's row id, for {@link #finishPlay} */
    public static long recordPlay(long trackId) {
        return db().write("INSERT INTO play_history (track_id, played_at) VALUES (?, ?)", trackId, System.currentTimeMillis());
    }

    /** Records how far the play at {@code rowId} got; the outcome follows from it. */
    public static void finishPlay(long rowId, float progress) {
        if (rowId <= 0) {
            return;
        }
        final float p = Math.max(0f, Math.min(1f, progress));
        final int outcome = p >= 0.85f ? OUTCOME_COMPLETED : p < 0.3f ? OUTCOME_SKIPPED : OUTCOME_PARTIAL;
        db().write("UPDATE play_history SET progress = ?, outcome = ? WHERE rowid = ?", (double) p, outcome, rowId);
    }

    /** How much one past play says the user wants this track: a play listened through is a
     *  vote for it, one skipped early is a vote against. Legacy rows without an outcome count
     *  as a moderate "for", which is what they were treated as before outcomes existed. */
    private static double playSignal(int outcome, double progress) {
        switch (outcome) {
            case OUTCOME_COMPLETED:
                return 1.0;
            case OUTCOME_PARTIAL:
                return Math.max(0.3, progress);
            case OUTCOME_SKIPPED:
                return progress < 0.15 ? -1.0 : -0.5;
            default:
                return 0.7;
        }
    }

    public static int getPlayCount(long trackId) {
        Integer count = db().queryOne("SELECT COUNT(*) FROM play_history WHERE track_id = ?",
                new Object[]{trackId}, cursor -> cursor.intValue(0));
        return count != null ? count : 0;
    }

    public static long getLastPlayedAt(long trackId) {
        Long lastPlayed = db().queryOne("SELECT MAX(played_at) FROM play_history WHERE track_id = ?",
                new Object[]{trackId}, cursor -> cursor.isNull(0) ? 0L : cursor.longValue(0));
        return lastPlayed != null ? lastPlayed : 0L;
    }

    // ---- Recommendations ----
    //
    // A weighted-sum heuristic over recency-decayed play frequency, a liked-track
    // boost, a tag-affinity term (tracks sharing mood tags with the user's own
    // most-played/liked tracks score higher), and an artist/genre-affinity term -
    // genuinely personalized, updates as listening habits change, without needing an
    // ML model this app has no infrastructure for. All done in-memory: library size is
    // a personal collection (hundreds of tracks at most), not worth round-tripping SQL
    // window functions for.
    //
    // Tags require the user to manually tag tracks, so a library with zero tags (the
    // common case - tagging is opt-in) previously fell back to nothing beyond raw play
    // count/likes, which barely reads as "recommendations" for a fresh or lightly-tagged
    // library. Artist and genre are metadata nearly every track already has (from ID3
    // tags or the Telegram message itself), so affinity computed the same way over
    // those two fields gives a real second signal - "more from artists/genres you
    // already listen to" - that works without the user doing anything extra.

    private static final double LIKED_BOOST = 3.0;
    /** Plays per track from the last scoring pass - the wave's exploration bonus reads it. */
    private static volatile Map<Long, Integer> lastPlayCounts = new HashMap<>();

    public static int lastPlayCount(long trackId) {
        final Integer c = lastPlayCounts.get(trackId);
        return c == null ? 0 : c;
    }
    private static final double HALF_LIFE_DAYS = 7.0;
    private static final double TAG_AFFINITY_WEIGHT = 0.5;
    private static final double ARTIST_AFFINITY_WEIGHT = 0.35;
    private static final double GENRE_AFFINITY_WEIGHT = 0.15;
    // Deliberately the smallest weight of the bunch (below even genre) - this is a mild
    // nudge toward the length of track the user actually tends to listen to/like, not a
    // hard constraint. A single dislike on one long track must not be enough to tilt the
    // whole wave toward only short ones - the weight is small specifically so one data
    // point can't dominate; it only shows up once a real pattern exists across several
    // plays/likes.
    private static final double DURATION_AFFINITY_WEIGHT = 0.08;

    public static List<PrimeTrack> getRecommendations(int limit) {
        List<PrimeTrack> allTracks = getAllTracks();
        if (allTracks.isEmpty()) {
            return allTracks;
        }
        Map<Long, Double> finalScore = computeTrackScores(allTracks);
        // Recommending a track the user explicitly marked "не нравится" would directly
        // contradict that signal - excluded from the candidate pool here too, same as
        // the wave, even though this method has no session/mood-filter concept of its own.
        List<PrimeTrack> eligible = new ArrayList<>();
        for (PrimeTrack track : allTracks) {
            if (!track.disliked) {
                eligible.add(track);
            }
        }
        Collections.sort(eligible, (a, b) -> Double.compare(
                finalScore.getOrDefault(b.id, 0.0), finalScore.getOrDefault(a.id, 0.0)));
        if (eligible.size() > limit) {
            return eligible.subList(0, limit);
        }
        return eligible;
    }

    /** Моя волна: the same scoring {@link #getRecommendations} uses, but session-aware -
     *  a track already played earlier in the current wave ({@code excludeTrackIds}) or
     *  marked "не нравится" is never offered again, and when a mood filter is active,
     *  only tracks carrying at least one of those tags are eligible at all. The mood
     *  filter reuses the same tags {@code PrimeMusicTagEditor} already lets a user put on
     *  a track - no separate mood taxonomy, "мы используем теги как аналог настроения"
     *  per the feature's own request. */
    /** Mood filter value meaning "tracks I liked" rather than a tag. */
    public static final String MOOD_LIKED = "__prime_liked__";

    /** A wave candidate with its score, for {@code PrimeMusicWave}'s own weighted pick. */
    public static final class ScoredTrack {
        public final PrimeTrack track;
        public final double score;

        ScoredTrack(PrimeTrack track, double score) {
            this.track = track;
            this.score = score;
        }
    }

    /** Every eligible wave track, best first, with its score. */
    public static List<ScoredTrack> getWaveCandidates(java.util.Set<Long> excludeTrackIds, java.util.List<String> moodTagFilter) {
        List<ScoredTrack> result = new ArrayList<>();
        List<PrimeTrack> allTracks = getAllTracks();
        if (allTracks.isEmpty()) {
            return result;
        }
        Map<Long, Double> finalScore = computeTrackScores(allTracks);
        final boolean likedOnly = moodTagFilter != null && moodTagFilter.contains(MOOD_LIKED);
        java.util.Set<Long> moodTrackIds = null;
        if (moodTagFilter != null && !moodTagFilter.isEmpty()) {
            for (String tagName : moodTagFilter) {
                if (MOOD_LIKED.equals(tagName)) {
                    continue;
                }
                if (moodTrackIds == null) {
                    moodTrackIds = new java.util.HashSet<>();
                }
                for (PrimeTrack tagged : getTracksWithTag(tagName)) {
                    moodTrackIds.add(tagged.id);
                }
            }
        }
        for (PrimeTrack track : allTracks) {
            if (track.disliked || excludeTrackIds != null && excludeTrackIds.contains(track.id)) {
                continue;
            }
            if (likedOnly && !track.liked) {
                continue;
            }
            if (moodTrackIds != null && !moodTrackIds.contains(track.id)) {
                continue;
            }
            result.add(new ScoredTrack(track, finalScore.getOrDefault(track.id, 0.0)));
        }
        Collections.sort(result, (a, b) -> Double.compare(b.score, a.score));
        return result;
    }

    public static List<PrimeTrack> getWaveRecommendations(java.util.Set<Long> excludeTrackIds, java.util.List<String> moodTagFilter, int limit) {
        List<PrimeTrack> allTracks = getAllTracks();
        if (allTracks.isEmpty()) {
            return allTracks;
        }
        Map<Long, Double> finalScore = computeTrackScores(allTracks);

        java.util.Set<Long> moodTrackIds = null;
        if (moodTagFilter != null && !moodTagFilter.isEmpty()) {
            moodTrackIds = new java.util.HashSet<>();
            for (String tagName : moodTagFilter) {
                for (PrimeTrack tagged : getTracksWithTag(tagName)) {
                    moodTrackIds.add(tagged.id);
                }
            }
        }

        List<PrimeTrack> candidates = new ArrayList<>();
        for (PrimeTrack track : allTracks) {
            if (track.disliked) {
                continue;
            }
            if (excludeTrackIds != null && excludeTrackIds.contains(track.id)) {
                continue;
            }
            if (moodTrackIds != null && !moodTrackIds.contains(track.id)) {
                continue;
            }
            candidates.add(track);
        }
        Collections.sort(candidates, (a, b) -> Double.compare(
                finalScore.getOrDefault(b.id, 0.0), finalScore.getOrDefault(a.id, 0.0)));
        if (candidates.size() > limit) {
            return candidates.subList(0, limit);
        }
        return candidates;
    }

    /** Shared scoring pass behind both {@link #getRecommendations} and
     *  {@link #getWaveRecommendations} - recency-decayed play frequency + a liked-track
     *  boost + tag/artist/genre affinity, computed once over the whole library so the
     *  wave's session-level filtering (excluded/disliked/mood tag) never has to
     *  recompute historical weighting from scratch. */
    private static Map<Long, Double> computeTrackScores(List<PrimeTrack> allTracks) {
        long now = System.currentTimeMillis();
        Map<Long, Double> playScore = new HashMap<>();
        final Map<Long, Integer> playCounts = new HashMap<>();
        db().queryEach("SELECT track_id, played_at, outcome, progress FROM play_history", new Object[]{}, cursor -> {
            long trackId = cursor.longValue(0);
            long playedAt = cursor.longValue(1);
            final int outcome = cursor.isNull(2) ? OUTCOME_UNKNOWN : cursor.intValue(2);
            final double progress = cursor.isNull(3) ? 0 : cursor.doubleValue(3);
            double ageDays = Math.max(0, (now - playedAt) / 86400000.0);
            double weight = Math.pow(0.5, ageDays / HALF_LIFE_DAYS);
            playScore.merge(trackId, weight * playSignal(outcome, progress), Double::sum);
            playCounts.merge(trackId, 1, Integer::sum);
        });
        lastPlayCounts = playCounts;

        Map<Long, List<Long>> trackTags = new HashMap<>();
        db().queryEach("SELECT track_id, tag_id FROM track_tags", new Object[]{}, cursor -> {
            long trackId = cursor.longValue(0);
            long tagId = cursor.longValue(1);
            trackTags.computeIfAbsent(trackId, k -> new ArrayList<>()).add(tagId);
        });

        Map<Long, Double> baseScoreByTrack = new HashMap<>();
        for (PrimeTrack track : allTracks) {
            double score = playScore.getOrDefault(track.id, 0.0) + (track.liked ? LIKED_BOOST : 0.0);
            baseScoreByTrack.put(track.id, score);
        }

        Map<Long, Double> tagWeight = new HashMap<>();
        for (Map.Entry<Long, List<Long>> entry : trackTags.entrySet()) {
            double score = baseScoreByTrack.getOrDefault(entry.getKey(), 0.0);
            for (Long tagId : entry.getValue()) {
                tagWeight.merge(tagId, score, Double::sum);
            }
        }

        // Same affinity computation as tags above, keyed by artist/genre string instead
        // of a tag id - skips unknown/empty values so a device-scanned track with no
        // ID3 metadata never pollutes another track's score via a shared "<unknown>"
        // bucket.
        Map<String, Double> artistWeight = new HashMap<>();
        Map<String, Double> genreWeight = new HashMap<>();
        for (PrimeTrack track : allTracks) {
            double score = baseScoreByTrack.getOrDefault(track.id, 0.0);
            if (score <= 0) {
                continue;
            }
            if (!PrimeMusicArtistLabel.isUnknown(track.artist)) {
                artistWeight.merge(track.artist, score, Double::sum);
            }
            if (track.genre != null && !track.genre.isEmpty()) {
                genreWeight.merge(track.genre, score, Double::sum);
            }
        }

        // How long a track the user actually tends to play/like, weighted the same way
        // as everything else above - a plain average would let one 45-minute mix or one
        // disliked 10-minute track swing the whole preference; weighting by baseScore
        // means it only shifts once real listening behavior backs it up.
        double durationWeightSum = 0;
        double durationWeightedTotal = 0;
        for (PrimeTrack track : allTracks) {
            double score = baseScoreByTrack.getOrDefault(track.id, 0.0);
            if (score <= 0 || track.duration <= 0) {
                continue;
            }
            durationWeightSum += score;
            durationWeightedTotal += score * track.duration;
        }
        double preferredDurationSec = durationWeightSum > 0 ? durationWeightedTotal / durationWeightSum : 0;

        Map<Long, Double> finalScore = new HashMap<>();
        for (PrimeTrack track : allTracks) {
            double score = baseScoreByTrack.getOrDefault(track.id, 0.0);
            List<Long> tags = trackTags.get(track.id);
            if (tags != null) {
                for (Long tagId : tags) {
                    double affinity = tagWeight.getOrDefault(tagId, 0.0) - score;
                    score += affinity * TAG_AFFINITY_WEIGHT / Math.max(1, tags.size());
                }
            }
            if (!PrimeMusicArtistLabel.isUnknown(track.artist)) {
                double affinity = artistWeight.getOrDefault(track.artist, 0.0) - score;
                score += affinity * ARTIST_AFFINITY_WEIGHT;
            }
            if (track.genre != null && !track.genre.isEmpty()) {
                double affinity = genreWeight.getOrDefault(track.genre, 0.0) - score;
                score += affinity * GENRE_AFFINITY_WEIGHT;
            }
            if (preferredDurationSec > 0 && track.duration > 0) {
                double similarity = 1.0 - Math.min(1.0, Math.abs(track.duration - preferredDurationSec) / preferredDurationSec);
                score += similarity * DURATION_AFFINITY_WEIGHT;
            }
            finalScore.put(track.id, score);
        }
        return finalScore;
    }
}
