import 'package:flutter/material.dart';

import '../bridge.dart';
import '../design/tokens.dart';
import '../models.dart';
import '../pages/now_playing_page.dart';

/// PrimeGram: bottom playback bar - cover, title/artist, source badge, play/pause, a thin
/// progress line along the top edge (mirrors the reference's yellow progress sliver,
/// recolored to our own accent). Tapping it opens [NowPlayingPage].
class MiniPlayer extends StatefulWidget {
  const MiniPlayer({super.key});

  @override
  State<MiniPlayer> createState() => _MiniPlayerState();
}

class _MiniPlayerState extends State<MiniPlayer> {
  Track? _track;
  int? _trackId;

  Future<void> _loadTrackIfNeeded(int? trackId) async {
    if (trackId == null || trackId == _trackId) return;
    _trackId = trackId;
    final track = await MusicBridge.instance.getTrack(trackId);
    if (mounted) setState(() => _track = track);
  }

  @override
  Widget build(BuildContext context) {
    return StreamBuilder<PlaybackState>(
      stream: MusicBridge.instance.playbackState,
      builder: (context, snapshot) {
        final state = snapshot.data ?? PlaybackState.empty;
        if (state.trackId == null) {
          _trackId = null;
          return const SizedBox.shrink();
        }
        _loadTrackIfNeeded(state.trackId);
        final track = _track;

        return Material(
          color: MusicTokens.surfaceRaised,
          child: InkWell(
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute(builder: (_) => const NowPlayingPage()),
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                LinearProgressIndicator(
                  value: state.progress.clamp(0, 1),
                  minHeight: 2,
                  backgroundColor: MusicTokens.divider,
                  valueColor: const AlwaysStoppedAnimation(MusicTokens.accentPurple),
                ),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: MusicTokens.s12, vertical: MusicTokens.s8),
                  child: Row(
                    children: [
                      TrackArtPlaceholder(title: track?.title ?? '', size: 40, radius: 8),
                      const SizedBox(width: MusicTokens.s12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Row(
                              children: [
                                if (track != null) ...[
                                  Container(
                                    width: 6,
                                    height: 6,
                                    margin: const EdgeInsets.only(right: 6),
                                    decoration: BoxDecoration(
                                      color: MusicTokens.sourceColor(track.source),
                                      shape: BoxShape.circle,
                                    ),
                                  ),
                                ],
                                Expanded(
                                  child: Text(
                                    track == null ? '...' : (track.title.isEmpty ? 'Без названия' : track.title),
                                    style: MusicTokens.title.copyWith(fontSize: 14),
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                ),
                              ],
                            ),
                            Text(
                              track?.artist ?? '',
                              style: MusicTokens.caption,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ],
                        ),
                      ),
                      IconButton(
                        icon: Icon(
                          state.paused ? Icons.play_arrow : Icons.pause,
                          color: MusicTokens.textPrimary,
                        ),
                        onPressed: () => MusicBridge.instance.pauseResume(),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}
