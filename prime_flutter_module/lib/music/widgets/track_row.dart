import 'package:flutter/material.dart';

import '../design/tokens.dart';
import '../models.dart';

/// PrimeGram: one track row - cover, title, source badge dot, artist, duration, like
/// toggle, optional trailing action (playlists use this slot for "add"/"remove"). Shared
/// by the Треки tab, playlist detail, and anywhere else a flat track list shows up.
class TrackRow extends StatelessWidget {
  final Track track;
  final VoidCallback onTap;
  final VoidCallback onToggleLike;
  final Widget? trailing;

  const TrackRow({
    super.key,
    required this.track,
    required this.onTap,
    required this.onToggleLike,
    this.trailing,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(MusicTokens.radiusM),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: MusicTokens.s8),
        child: Row(
          children: [
            TrackArt(track: track, size: 48),
            const SizedBox(width: MusicTokens.s12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    track.title.isEmpty ? 'Без названия' : track.title,
                    style: MusicTokens.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const SizedBox(height: 2),
                  Row(
                    children: [
                      Container(
                        width: 6,
                        height: 6,
                        margin: const EdgeInsets.only(right: 6),
                        decoration: BoxDecoration(color: MusicTokens.sourceColor(track.source), shape: BoxShape.circle),
                      ),
                      Expanded(
                        child: Text(
                          track.artist,
                          style: MusicTokens.body,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
            Text(MusicTokens.formatDuration(track.duration), style: MusicTokens.timecode),
            IconButton(
              icon: Icon(
                track.liked ? Icons.favorite : Icons.favorite_border,
                color: track.liked ? MusicTokens.accentMagenta : MusicTokens.textTertiary,
                size: 20,
              ),
              onPressed: onToggleLike,
            ),
            ?trailing,
          ],
        ),
      ),
    );
  }
}
