import 'package:flutter/material.dart';

import '../bridge.dart';
import '../design/tokens.dart';
import '../models.dart';
import '../widgets/waveform_seek_bar.dart';

/// PrimeGram: full-screen "Сейчас играет" - composition studied from the real Yandex
/// Music Now Playing screen (Figma reference: big cover, centered play/pause, dislike +
/// next + like row) - our own layout/tokens, not a pixel clone. Previous/next both real:
/// PrimeMusicWave keeps an ordered session history (see PrimeMusicWave.previous()), so
/// "previous" genuinely steps back rather than faking a no-op button.
///
/// Swipe-down-to-dismiss (Namida-style "flick it away") - a vertical drag on the body
/// translates + fades the page and pops once it's dragged/flung far enough.
class NowPlayingPage extends StatefulWidget {
  const NowPlayingPage({super.key});

  @override
  State<NowPlayingPage> createState() => _NowPlayingPageState();
}

class _NowPlayingPageState extends State<NowPlayingPage> {
  Track? _track;
  int? _trackId;
  bool _waveActive = false;
  bool _hasPrevious = false;
  double _dragOffset = 0;

  @override
  void initState() {
    super.initState();
    _refreshWaveState();
  }

  Future<void> _refreshWaveState() async {
    final active = await MusicBridge.instance.waveIsActive();
    final hasPrevious = active ? await MusicBridge.instance.waveHasPrevious() : false;
    if (mounted) {
      setState(() {
        _waveActive = active;
        _hasPrevious = hasPrevious;
      });
    }
  }

  Future<void> _loadTrackIfNeeded(int? trackId) async {
    if (trackId == null || trackId == _trackId) return;
    _trackId = trackId;
    final track = await MusicBridge.instance.getTrack(trackId);
    if (mounted) setState(() => _track = track);
  }

  Future<void> _dislike() async {
    if (_waveActive) {
      final next = await MusicBridge.instance.waveDislikeAndAdvance();
      if (next != null) await MusicBridge.instance.playSingle(next.id);
    } else if (_track != null) {
      await MusicBridge.instance.setDisliked(_track!.id, true);
    }
  }

  Future<void> _next() async {
    if (!_waveActive) return; // no generic "skip" outside a wave session (see class doc)
    final next = await MusicBridge.instance.waveNext();
    if (next != null) await MusicBridge.instance.playSingle(next.id);
    await _refreshWaveState();
  }

  Future<void> _previous() async {
    if (!_waveActive || !_hasPrevious) return;
    final prev = await MusicBridge.instance.wavePrevious();
    if (prev != null) await MusicBridge.instance.playSingle(prev.id);
    await _refreshWaveState();
  }

  Future<void> _toggleLike() async {
    final track = _track;
    if (track == null) return;
    await MusicBridge.instance.setLiked(track.id, !track.liked);
    setState(() {}); // optimistic-ish: re-fetch on next state tick, not critical to be instant
  }

  void _onDragUpdate(DragUpdateDetails details) {
    setState(() => _dragOffset = (_dragOffset + details.delta.dy).clamp(0, 400));
  }

  void _onDragEnd(DragEndDetails details) {
    final flungDown = details.velocity.pixelsPerSecond.dy > 600;
    if (_dragOffset > 120 || flungDown) {
      Navigator.of(context).maybePop();
      return;
    }
    setState(() => _dragOffset = 0);
  }

  @override
  Widget build(BuildContext context) {
    final screenHeight = MediaQuery.of(context).size.height;
    final dragProgress = (_dragOffset / screenHeight).clamp(0.0, 1.0);
    return GestureDetector(
      onVerticalDragUpdate: _onDragUpdate,
      onVerticalDragEnd: _onDragEnd,
      child: Transform.translate(
        offset: Offset(0, _dragOffset),
        child: Opacity(
          opacity: 1 - dragProgress * 0.6,
          child: Scaffold(
            backgroundColor: MusicTokens.bg,
            body: StreamBuilder<PlaybackState>(
              stream: MusicBridge.instance.playbackState,
              builder: (context, snapshot) {
                final state = snapshot.data ?? const PlaybackState();
                _loadTrackIfNeeded(state.trackId);
                final track = _track;

                return SafeArea(
                  child: Column(
                    children: [
                      _buildHeader(context),
                      const SizedBox(height: MusicTokens.s24),
                      Expanded(
                        child: Center(
                          child: Hero(
                            tag: 'now-playing-art',
                            child: track == null
                                ? const SizedBox(width: 280, height: 280)
                                : TrackArt(track: track, size: 280, radius: MusicTokens.radiusL),
                          ),
                        ),
                      ),
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: MusicTokens.s24),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              track == null ? 'Ничего не играет' : (track.title.isEmpty ? 'Без названия' : track.title),
                              style: MusicTokens.title.copyWith(fontSize: 20),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                            const SizedBox(height: 4),
                            Text(
                              track?.artist ?? '',
                              style: MusicTokens.body,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: MusicTokens.s16),
                      _buildProgress(state, track),
                      const SizedBox(height: MusicTokens.s8),
                      _buildControls(state, track),
                      const SizedBox(height: MusicTokens.s32),
                    ],
                  ),
                );
              },
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildHeader(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: MusicTokens.s8),
      child: Row(
        children: [
          IconButton(
            icon: const Icon(Icons.keyboard_arrow_down, color: MusicTokens.textPrimary),
            onPressed: () => Navigator.of(context).maybePop(),
          ),
          Expanded(
            child: Column(
              children: [
                Text(_waveActive ? 'Моя волна' : 'Сейчас играет', style: MusicTokens.body.copyWith(color: MusicTokens.textTertiary, fontSize: 11)),
                const SizedBox(height: 2),
                Text('Плеер', style: MusicTokens.title.copyWith(fontSize: 13)),
              ],
            ),
          ),
          const SizedBox(width: 48), // balances the back button so the title stays centered
        ],
      ),
    );
  }

  Widget _buildProgress(PlaybackState state, Track? track) {
    final duration = track?.duration ?? 0;
    final positionSeconds = duration * state.progress;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: MusicTokens.s24),
      child: Column(
        children: [
          WaveformSeekBar(
            trackId: track?.id ?? 0,
            progress: state.progress.clamp(0, 1),
            onSeek: (v) => MusicBridge.instance.seekTo(v),
          ),
          const SizedBox(height: 4),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(MusicTokens.formatDuration(positionSeconds), style: MusicTokens.timecode),
              Text(MusicTokens.formatDuration(duration), style: MusicTokens.timecode),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildControls(PlaybackState state, Track? track) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
      children: [
        _iconButton(
          icon: Icons.thumb_down_alt_outlined,
          active: track?.disliked ?? false,
          onTap: _dislike,
          size: 24,
        ),
        _iconButton(
          icon: Icons.skip_previous,
          active: false,
          enabled: _waveActive && _hasPrevious,
          onTap: _previous,
          size: 28,
        ),
        _PlayPauseButton(paused: state.paused, onTap: () => MusicBridge.instance.pauseResume()),
        _iconButton(
          icon: Icons.skip_next,
          active: false,
          enabled: _waveActive,
          onTap: _next,
          size: 28,
        ),
        _iconButton(
          icon: (track?.liked ?? false) ? Icons.favorite : Icons.favorite_border,
          active: track?.liked ?? false,
          activeColor: MusicTokens.accentMagenta,
          onTap: _toggleLike,
          size: 24,
        ),
      ],
    );
  }

  Widget _iconButton({
    required IconData icon,
    required bool active,
    required VoidCallback onTap,
    Color activeColor = MusicTokens.textPrimary,
    bool enabled = true,
    double size = 24,
  }) {
    return IconButton(
      iconSize: size,
      onPressed: enabled ? onTap : null,
      icon: Icon(
        icon,
        color: !enabled
            ? MusicTokens.textTertiary.withValues(alpha: 0.4)
            : (active ? activeColor : MusicTokens.textSecondary),
      ),
    );
  }
}

class _PlayPauseButton extends StatelessWidget {
  final bool paused;
  final VoidCallback onTap;
  const _PlayPauseButton({required this.paused, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      customBorder: const CircleBorder(),
      child: Container(
        width: 64,
        height: 64,
        decoration: const BoxDecoration(color: MusicTokens.textPrimary, shape: BoxShape.circle),
        child: Icon(paused ? Icons.play_arrow : Icons.pause, color: MusicTokens.bg, size: 32),
      ),
    );
  }
}
