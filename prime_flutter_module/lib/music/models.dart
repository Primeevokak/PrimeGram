// PrimeGram: Dart mirror of org.telegram.messenger.music.PrimeTrack's fields. Kept as a
// plain typed class (not code-generated) since the field set is small and stable - the
// native side is the source of truth, this just gives the Dart UI something to bind to
// instead of raw Maps everywhere.
class Track {
  final int id;
  final int source;
  final int dialogId;
  final int messageId;
  final String? localPath;
  final String title;
  final String artist;
  final int duration;
  final String? coverPath;
  final bool liked;
  final String? lyrics;
  final int addedAt;
  final String? album;
  final String? genre;
  final int dateAdded;
  final bool disliked;

  const Track({
    required this.id,
    required this.source,
    required this.dialogId,
    required this.messageId,
    this.localPath,
    required this.title,
    required this.artist,
    required this.duration,
    this.coverPath,
    required this.liked,
    this.lyrics,
    required this.addedAt,
    this.album,
    this.genre,
    required this.dateAdded,
    required this.disliked,
  });

  factory Track.fromMap(Map<dynamic, dynamic> map) {
    return Track(
      id: map['id'] as int,
      source: map['source'] as int? ?? 0,
      dialogId: map['dialogId'] as int? ?? 0,
      messageId: map['messageId'] as int? ?? 0,
      localPath: map['localPath'] as String?,
      title: map['title'] as String? ?? '',
      artist: map['artist'] as String? ?? '',
      duration: map['duration'] as int? ?? 0,
      coverPath: map['coverPath'] as String?,
      liked: map['liked'] as bool? ?? false,
      lyrics: map['lyrics'] as String?,
      addedAt: map['addedAt'] as int? ?? 0,
      album: map['album'] as String?,
      genre: map['genre'] as String?,
      dateAdded: map['dateAdded'] as int? ?? 0,
      disliked: map['disliked'] as bool? ?? false,
    );
  }
}

/// Mirrors org.telegram.messenger.music.PrimePlaylist. trackCount is computed on the
/// native side (PrimeMusicChannel.playlistToMap) purely for display - not a real field on
/// PrimePlaylist itself.
class Playlist {
  final int id;
  final String name;
  final int createdAt;
  final int trackCount;

  const Playlist({required this.id, required this.name, required this.createdAt, this.trackCount = 0});

  factory Playlist.fromMap(Map<dynamic, dynamic> map) {
    return Playlist(
      id: map['id'] as int,
      name: map['name'] as String? ?? '',
      createdAt: map['createdAt'] as int? ?? 0,
      trackCount: map['trackCount'] as int? ?? 0,
    );
  }
}

/// Mirrors what the EventChannel streams and what getCurrentState() returns - "what's
/// playing right now", not the track list. trackId is null when nothing is playing.
class PlaybackState {
  final int? trackId;
  final bool paused;
  final double progress;

  const PlaybackState({this.trackId, this.paused = true, this.progress = 0});

  static const empty = PlaybackState();

  factory PlaybackState.fromMap(Map<dynamic, dynamic> map) {
    return PlaybackState(
      trackId: map['trackId'] as int?,
      paused: map['paused'] as bool? ?? true,
      progress: (map['progress'] as num?)?.toDouble() ?? 0,
    );
  }
}
