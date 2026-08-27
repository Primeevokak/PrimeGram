import 'package:flutter/material.dart';

import '../bridge.dart';
import '../design/tokens.dart';
import '../models.dart';
import '../widgets/track_row.dart';
import 'now_playing_page.dart';

/// PrimeGram: one playlist's track list - opened from the Плейлисты tab.
class PlaylistDetailPage extends StatefulWidget {
  final Playlist playlist;
  const PlaylistDetailPage({super.key, required this.playlist});

  @override
  State<PlaylistDetailPage> createState() => _PlaylistDetailPageState();
}

class _PlaylistDetailPageState extends State<PlaylistDetailPage> {
  late Future<List<Track>> _tracksFuture;

  @override
  void initState() {
    super.initState();
    _tracksFuture = MusicBridge.instance.getPlaylistTracks(widget.playlist.id);
  }

  Future<void> _reload() async {
    setState(() => _tracksFuture = MusicBridge.instance.getPlaylistTracks(widget.playlist.id));
    await _tracksFuture;
  }

  Future<void> _play(Track track) async {
    await MusicBridge.instance.playSingle(track.id);
    if (!mounted) return;
    Navigator.of(context).push(MaterialPageRoute(builder: (_) => const NowPlayingPage()));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: MusicTokens.bg,
      appBar: AppBar(
        backgroundColor: MusicTokens.bg,
        elevation: 0,
        title: Text(widget.playlist.name, style: MusicTokens.title.copyWith(fontSize: 18)),
      ),
      body: FutureBuilder<List<Track>>(
        future: _tracksFuture,
        builder: (context, snapshot) {
          final tracks = snapshot.data;
          if (tracks == null) {
            return const Center(child: CircularProgressIndicator(color: MusicTokens.accentPurple));
          }
          if (tracks.isEmpty) {
            return Center(child: Text('Плейлист пуст', style: MusicTokens.body));
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
                trailing: IconButton(
                  icon: const Icon(Icons.remove_circle_outline, color: MusicTokens.textTertiary, size: 20),
                  onPressed: () async {
                    await MusicBridge.instance.removeFromPlaylist(widget.playlist.id, track.id);
                    _reload();
                  },
                ),
              );
            },
          );
        },
      ),
    );
  }
}
