import 'package:flutter/material.dart';

import '../bridge.dart';
import '../design/tokens.dart';
import '../models.dart';
import '../widgets/mini_player.dart';
import '../widgets/wave_card.dart';
import 'now_playing_page.dart';

/// PrimeGram: Music tab home screen - the wave card up top (see wave_card.dart), a
/// recommendations list below it, mini player docked at the bottom.
class MusicHomePage extends StatefulWidget {
  const MusicHomePage({super.key});

  @override
  State<MusicHomePage> createState() => _MusicHomePageState();
}

class _MusicHomePageState extends State<MusicHomePage> {
  late Future<List<Track>> _tracksFuture;

  @override
  void initState() {
    super.initState();
    _tracksFuture = MusicBridge.instance.getRecommendations();
  }

  Future<void> _reload() async {
    setState(() {
      _tracksFuture = MusicBridge.instance.getRecommendations();
    });
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
        title: const Text('Музыка', style: MusicTokens.hero),
      ),
      body: Column(
        children: [
          Expanded(
            child: RefreshIndicator(
              onRefresh: _reload,
              color: MusicTokens.accentPurple,
              backgroundColor: MusicTokens.surfaceRaised,
              child: FutureBuilder<List<Track>>(
                future: _tracksFuture,
                builder: (context, snapshot) {
                  final tracks = snapshot.data;
                  return ListView(
                    padding: const EdgeInsets.fromLTRB(MusicTokens.s16, MusicTokens.s8, MusicTokens.s16, MusicTokens.s16),
                    children: [
                      const WaveCard(),
                      const SizedBox(height: MusicTokens.s24),
                      Padding(
                        padding: const EdgeInsets.only(bottom: MusicTokens.s12, left: 4),
                        child: Text('Рекомендуем для вас', style: MusicTokens.title.copyWith(fontSize: 17)),
                      ),
                      if (tracks == null)
                        const Padding(
                          padding: EdgeInsets.symmetric(vertical: 24),
                          child: Center(child: CircularProgressIndicator(color: MusicTokens.accentPurple)),
                        )
                      else if (tracks.isEmpty)
                        Padding(
                          padding: const EdgeInsets.symmetric(vertical: 24),
                          child: Center(child: Text('Треков пока нет', style: MusicTokens.body)),
                        )
                      else
                        ...tracks.map((track) => _TrackRow(
                              track: track,
                              onTap: () => _play(track),
                              onToggleLike: () async {
                                await MusicBridge.instance.setLiked(track.id, !track.liked);
                                _reload();
                              },
                            )),
                    ],
                  );
                },
              ),
            ),
          ),
          const MiniPlayer(),
        ],
      ),
    );
  }
}

class _TrackRow extends StatelessWidget {
  final Track track;
  final VoidCallback onTap;
  final VoidCallback onToggleLike;

  const _TrackRow({required this.track, required this.onTap, required this.onToggleLike});

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(MusicTokens.radiusM),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: MusicTokens.s8),
        child: Row(
          children: [
            TrackArtPlaceholder(title: track.title, size: 48),
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
          ],
        ),
      ),
    );
  }
}
