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
        return track;
    }

    public static void setLyrics(long trackId, String lyrics) {
        db().write("UPDATE tracks SET lyrics = ? WHERE id = ?", lyrics, trackId);
    }

    // ---- Mood tags ----

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
    public static void recordPlay(long trackId) {
        db().write("INSERT INTO play_history (track_id, played_at) VALUES (?, ?)", trackId, System.currentTimeMillis());
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
    // boost, and a tag-affinity term (tracks sharing tags with the user's own
    // most-played/liked tracks score higher) - genuinely personalized, updates as
    // listening habits change, without needing an ML model this app has no
    // infrastructure for. All done in-memory: library size is a personal collection
    // (hundreds of tracks at most), not worth round-tripping SQL window functions for.

    private static final double LIKED_BOOST = 3.0;
    private static final double HALF_LIFE_DAYS = 7.0;
    private static final double TAG_AFFINITY_WEIGHT = 0.5;

    public static List<PrimeTrack> getRecommendations(int limit) {
        List<PrimeTrack> allTracks = getAllTracks();
        if (allTracks.isEmpty()) {
            return allTracks;
        }

        long now = System.currentTimeMillis();
        Map<Long, Double> playScore = new HashMap<>();
        db().queryEach("SELECT track_id, played_at FROM play_history", new Object[]{}, cursor -> {
            long trackId = cursor.longValue(0);
            long playedAt = cursor.longValue(1);
            double ageDays = Math.max(0, (now - playedAt) / 86400000.0);
            double weight = Math.pow(0.5, ageDays / HALF_LIFE_DAYS);
            playScore.merge(trackId, weight, Double::sum);
        });

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
            finalScore.put(track.id, score);
        }

        List<PrimeTrack> sorted = new ArrayList<>(allTracks);
        Collections.sort(sorted, (a, b) -> Double.compare(
                finalScore.getOrDefault(b.id, 0.0), finalScore.getOrDefault(a.id, 0.0)));
        if (sorted.size() > limit) {
            return sorted.subList(0, limit);
        }
        return sorted;
    }
}
