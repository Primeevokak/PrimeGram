import 'package:flutter/material.dart';

import '../bridge.dart';
import '../models.dart';

/// PrimeGram: bottom playback bar - Phase 1 keeps this deliberately plain (no cover art,
/// no track title lookup) since its only job right now is proving the EventChannel really
/// carries live state back from native MediaController playback, not being pretty yet.
class MiniPlayer extends StatelessWidget {
  const MiniPlayer({super.key});

  @override
  Widget build(BuildContext context) {
    return StreamBuilder<PlaybackState>(
      stream: MusicBridge.instance.playbackState,
      builder: (context, snapshot) {
        final state = snapshot.data ?? PlaybackState.empty;
        if (state.trackId == null) {
          return const SizedBox.shrink();
        }
        return Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
          decoration: const BoxDecoration(
            color: Color(0xFF1A1A1F),
            border: Border(top: BorderSide(color: Color(0x22FFFFFF))),
          ),
          child: Row(
            children: [
              Expanded(
                child: LinearProgressIndicator(
                  value: state.progress.clamp(0, 1),
                  backgroundColor: const Color(0x22FFFFFF),
                ),
              ),
              const SizedBox(width: 12),
              IconButton(
                icon: Icon(state.paused ? Icons.play_arrow : Icons.pause, color: Colors.white),
                onPressed: () => MusicBridge.instance.pauseResume(),
              ),
            ],
          ),
        );
      },
    );
  }
}
