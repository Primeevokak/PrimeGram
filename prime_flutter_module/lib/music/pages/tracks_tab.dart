import 'package:flutter/material.dart';

import '../bridge.dart';
import '../design/tokens.dart';
import '../models.dart';
import '../widgets/track_row.dart';
import 'now_playing_page.dart';

/// PrimeGram: "Треки" tab - every installed/downloaded/synced track, flat, no wave/mood
/// filtering. Namida-style ("all your tracks, scannable") next to the streaming-style
/// wave tab, rather than only ever surfacing recommendations.
class TracksTab extends StatefulWidget {
  const TracksTab({super.key});

  @override
  State<TracksTab> createState() => _TracksTabState();
}

class _TracksTabState extends State<TracksTab> with AutomaticKeepAliveClientMixin {
  late Future<List<Track>> _tracksFuture;

  @override
  bool get wantKeepAlive => true;

  @override
  void initState() {
    super.initState();
    _tracksFuture = MusicBridge.instance.getAllTracks();
  }

  Future<void> _reload() async {
    setState(() => _tracksFuture = MusicBridge.instance.getAllTracks());
    await _tracksFuture;
  }

  Future<void> _play(Track track) async {
    await MusicBridge.instance.playSingle(track.id);
    if (!mounted) return;
    Navigator.of(context).push(MaterialPageRoute(builder: (_) => const NowPlayingPage()));
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    return RefreshIndicator(
      onRefresh: _reload,
      color: MusicTokens.accentPurple,
      backgroundColor: MusicTokens.surfaceRaised,
      child: FutureBuilder<List<Track>>(
        future: _tracksFuture,
        builder: (context, snapshot) {
          final tracks = snapshot.data;
          if (tracks == null) {
            return const Center(child: CircularProgressIndicator(color: MusicTokens.accentPurple));
          }
          if (tracks.isEmpty) {
            return ListView(
              children: [
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 48),
                  child: Center(child: Text('Треков пока нет', style: MusicTokens.body)),
                ),
              ],
            );
          }
          return ListView.builder(
            padding: const EdgeInsets.symmetric(horizontal: MusicTokens.s16, vertical: MusicTokens.s8),
            itemCount: tracks.length,
            itemBuilder: (context, index) {
              final track = tracks[index];
              return TrackRow(
                track: track,
                onTap: () => _play(track),
                onToggleLike: () async {
                  await MusicBridge.instance.setLiked(track.id, !track.liked);
                  _reload();
                },
              );
            },
          );
        },
      ),
    );
  }
}
