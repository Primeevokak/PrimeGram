import 'package:flutter/material.dart';

import '../bridge.dart';
import '../models.dart';
import '../widgets/mini_player.dart';
import '../widgets/wave_card.dart';

/// PrimeGram: Music tab home screen - the wave card up top (see wave_card.dart), a plain
/// recommendations list below it, tap to play. Cover art, Now Playing screen, equalizer,
/// hidden/disliked management etc. are Phase 3 (see the plan) - not this pass.
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

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Музыка')),
      body: Column(
        children: [
          Expanded(
            child: RefreshIndicator(
              onRefresh: _reload,
              child: FutureBuilder<List<Track>>(
                future: _tracksFuture,
                builder: (context, snapshot) {
                  final tracks = snapshot.data;
                  return ListView(
                    padding: const EdgeInsets.all(16),
                    children: [
                      const WaveCard(),
                      const SizedBox(height: 20),
                      const Padding(
                        padding: EdgeInsets.only(bottom: 8),
                        child: Text(
                          'Рекомендуем для вас',
                          style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                        ),
                      ),
                      if (tracks == null)
                        const Padding(
                          padding: EdgeInsets.symmetric(vertical: 24),
                          child: Center(child: CircularProgressIndicator()),
                        )
                      else if (tracks.isEmpty)
                        const Padding(
                          padding: EdgeInsets.symmetric(vertical: 24),
                          child: Center(child: Text('Треков пока нет')),
                        )
                      else
                        ...tracks.map((track) => ListTile(
                              leading: const CircleAvatar(child: Icon(Icons.music_note)),
                              title: Text(track.title.isEmpty ? 'Без названия' : track.title),
                              subtitle: Text(track.artist),
                              trailing: IconButton(
                                icon: Icon(
                                  track.liked ? Icons.favorite : Icons.favorite_border,
                                  color: track.liked ? Colors.redAccent : null,
                                ),
                                onPressed: () {
                                  MusicBridge.instance.setLiked(track.id, !track.liked);
                                  _reload();
                                },
                              ),
                              onTap: () => MusicBridge.instance.playSingle(track.id),
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
