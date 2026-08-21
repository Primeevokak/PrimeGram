import 'package:flutter/material.dart';

import '../bridge.dart';
import '../design/tokens.dart';
import '../models.dart';

/// PrimeGram: full-screen "Сейчас играет" - composition studied from the real Yandex
/// Music Now Playing screen (Figma reference: big cover, centered play/pause, dislike +
/// next + like row) - our own layout/tokens, not a pixel clone. Honest to what the
/// backend actually supports: there is no "previous track" concept in
/// PrimeMusicWave (a session only ever moves forward), so this doesn't fake a previous
/// button that would do nothing - dislike/play-pause/next/like is the real, working set.
class NowPlayingPage extends StatefulWidget {
  const NowPlayingPage({super.key});

  @override
  State<NowPlayingPage> createState() => _NowPlayingPageState();
}

class _NowPlayingPageState extends State<NowPlayingPage> {
  Track? _track;
  int? _trackId;
  bool _waveActive = false;

  @override
  void initState() {
    super.initState();
    MusicBridge.instance.waveIsActive().then((active) {
      if (mounted) setState(() => _waveActive = active);
    });
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
  }

  Future<void> _toggleLike() async {
    final track = _track;
    if (track == null) return;
    await MusicBridge.instance.setLiked(track.id, !track.liked);
    setState(() {}); // optimistic-ish: re-fetch on next state tick, not critical to be instant
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
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
                          : TrackArtPlaceholder(title: track.title, size: 280, radius: MusicTokens.radiusL),
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
          SliderTheme(
            data: SliderTheme.of(context).copyWith(
              trackHeight: 4,
              thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 6),
              activeTrackColor: MusicTokens.accentPurple,
              inactiveTrackColor: MusicTokens.divider,
              thumbColor: MusicTokens.textPrimary,
              overlayShape: SliderComponentShape.noOverlay,
            ),
            child: Slider(
              value: state.progress.clamp(0, 1),
              onChanged: (v) => MusicBridge.instance.seekTo(v),
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: MusicTokens.s8),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(MusicTokens.formatDuration(positionSeconds), style: MusicTokens.timecode),
                Text(MusicTokens.formatDuration(duration), style: MusicTokens.timecode),
              ],
            ),
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
