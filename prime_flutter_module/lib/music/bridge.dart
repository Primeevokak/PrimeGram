import 'dart:async';

import 'package:flutter/services.dart';

import 'models.dart';

/// PrimeGram: the only file in the Dart music UI that talks to platform channels -
/// everything else consumes [Track]/[PlaybackState] and calls the methods below. Mirrors
/// PrimeMusicChannel.java on the native side 1:1; see that file for what each method
/// actually does (this is pure marshalling on both ends, no business logic here either).
///
/// Every method falls back to mock data on [MissingPluginException] - that's what happens
/// when this same Dart code runs somewhere with no PrimeMusicChannel host at all (desktop/
/// Chrome, via the flutter_scratch design-preview project - see
/// [[gitignore-flutter-scratch-projects]]), not a real error to surface to the UI. On a
/// real Android device with PrimeMusicActivity attached, the channel always answers and
/// this fallback never triggers.
class MusicBridge {
  MusicBridge._();
  static final MusicBridge instance = MusicBridge._();

  static const MethodChannel _methods = MethodChannel('primegram/music');
  static const EventChannel _events = EventChannel('primegram/music/events');

  Stream<PlaybackState>? _stateStream;

  /// Live playback state - progress/pause/track id - pushed from
  /// NotificationCenter.messagePlayingProgressDidChanged/DidStart/PlayStateChanged/DidReset
  /// on the native side. A broadcast stream so both the mini player and any future
  /// now-playing screen can listen independently.
  Stream<PlaybackState> get playbackState {
    return _stateStream ??= _playbackStateStream().asBroadcastStream();
  }

  Stream<PlaybackState> _playbackStateStream() async* {
    try {
      await for (final event in _events.receiveBroadcastStream()) {
        yield PlaybackState.fromMap(event as Map);
      }
    } on MissingPluginException {
      yield* _mockPlaybackStream();
    }
  }

  Future<List<Track>> getRecommendations({int limit = 30}) async {
    try {
      final result = await _methods.invokeMethod<List<dynamic>>(
        'getRecommendations',
        {'limit': limit},
      );
      return (result ?? const []).map((e) => Track.fromMap(e as Map)).toList();
    } on MissingPluginException {
      return _mockTracks;
    }
  }

  Future<PlaybackState> getCurrentState() async {
    try {
      final result = await _methods.invokeMethod<Map<dynamic, dynamic>>('getCurrentState');
      return result == null ? PlaybackState.empty : PlaybackState.fromMap(result);
    } on MissingPluginException {
      return const PlaybackState(trackId: 1, paused: true, progress: 0.35);
    }
  }

  Future<void> playSingle(int trackId) async {
    try {
      await _methods.invokeMethod('playSingle', {'trackId': trackId});
    } on MissingPluginException {
      // No host to play anything on a design preview - nothing to do.
    }
  }

  Future<void> pauseResume() async {
    try {
      await _methods.invokeMethod('pauseResume');
    } on MissingPluginException {
      // Preview-only no-op.
    }
  }

  Future<void> seekTo(double progress) async {
    try {
      await _methods.invokeMethod('seekTo', {'progress': progress});
    } on MissingPluginException {
      // Preview-only no-op.
    }
  }

  Future<void> setLiked(int trackId, bool liked) async {
    try {
      await _methods.invokeMethod('setLiked', {'trackId': trackId, 'liked': liked});
    } on MissingPluginException {
      // Preview-only no-op.
    }
  }

  /// Starts a fresh wave (or resumes the current one if already running) - mirrors
  /// PrimeMusicWave.start()/current(). Returns the track now playing, or null if nothing
  /// was eligible (empty library / everything disliked / mood filter too narrow).
  Future<Track?> waveStart() async {
    try {
      final result = await _methods.invokeMethod<Map<dynamic, dynamic>>('waveStart');
      return result == null ? null : Track.fromMap(result);
    } on MissingPluginException {
      return _mockTracks.first;
    }
  }

  Future<Track?> waveNext() async {
    try {
      final result = await _methods.invokeMethod<Map<dynamic, dynamic>>('waveNext');
      return result == null ? null : Track.fromMap(result);
    } on MissingPluginException {
      return _mockTracks[(_mockTracks.indexWhere((t) => t.id == 1) + 1) % _mockTracks.length];
    }
  }

  /// "Не нравится" - marks the current wave track disliked and immediately advances,
  /// same call PrimeMusicWave.dislikeCurrentAndAdvance() makes on the native side.
  Future<Track?> waveDislikeAndAdvance() async {
    try {
      final result = await _methods.invokeMethod<Map<dynamic, dynamic>>('waveDislikeAndAdvance');
      return result == null ? null : Track.fromMap(result);
    } on MissingPluginException {
      return _mockTracks[1];
    }
  }

  Future<Track?> waveCurrent() async {
    try {
      final result = await _methods.invokeMethod<Map<dynamic, dynamic>>('waveCurrent');
      return result == null ? null : Track.fromMap(result);
    } on MissingPluginException {
      return _mockTracks.first;
    }
  }

  Future<void> waveStop() async {
    try {
      await _methods.invokeMethod('waveStop');
    } on MissingPluginException {
      // Preview-only no-op.
    }
  }

  Future<bool> waveIsActive() async {
    try {
      return await _methods.invokeMethod<bool>('waveIsActive') ?? false;
    } on MissingPluginException {
      return true;
    }
  }

  /// null tag = "Всё подряд" (no filter) - mirrors PrimeMusicWave.setMoodFilter(null) vs a
  /// single-tag list; this UI only ever offers picking one mood at a time.
  Future<void> waveSetMoodFilter(String? tag) async {
    try {
      await _methods.invokeMethod('waveSetMoodFilter', {'tag': tag});
    } on MissingPluginException {
      // Preview-only no-op.
    }
  }

  static final List<Track> _mockTracks = [
    const Track(id: 1, source: 0, dialogId: 0, messageId: 0, title: 'Neon Dreams', artist: 'AI Genesis', duration: 214, liked: true, addedAt: 0, dateAdded: 0, disliked: false),
    const Track(id: 2, source: 1, dialogId: 0, messageId: 0, title: 'Волна', artist: 'Prime Sound', duration: 187, liked: false, addedAt: 0, dateAdded: 0, disliked: false),
    const Track(id: 3, source: 0, dialogId: 0, messageId: 0, title: 'Полночь в Москве', artist: 'Синтетика', duration: 256, liked: false, addedAt: 0, dateAdded: 0, disliked: false),
    const Track(id: 4, source: 2, dialogId: 0, messageId: 0, title: 'Deep Focus', artist: 'Ambient Lab', duration: 301, liked: true, addedAt: 0, dateAdded: 0, disliked: false),
  ];

  /// Fake ticking progress for track id 1, so the mini player has something to animate on
  /// a design preview instead of sitting frozen.
  Stream<PlaybackState> _mockPlaybackStream() async* {
    var tick = 0;
    while (true) {
      await Future<void>.delayed(const Duration(milliseconds: 500));
      yield PlaybackState(trackId: 1, paused: false, progress: (tick % 40) / 40.0);
      tick++;
    }
  }
}
