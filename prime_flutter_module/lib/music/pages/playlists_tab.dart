import 'package:flutter/material.dart';

import '../bridge.dart';
import '../design/tokens.dart';
import '../models.dart';
import 'playlist_detail_page.dart';

/// PrimeGram: "Плейлисты" tab - create/browse. All CRUD already existed natively
/// (PrimeMusicLibrary.createPlaylist/getPlaylists/etc) - this tab is the first UI that
/// actually exposes it.
class PlaylistsTab extends StatefulWidget {
  const PlaylistsTab({super.key});

  @override
  State<PlaylistsTab> createState() => _PlaylistsTabState();
}

class _PlaylistsTabState extends State<PlaylistsTab> with AutomaticKeepAliveClientMixin {
  late Future<List<Playlist>> _playlistsFuture;

  @override
  bool get wantKeepAlive => true;

  @override
  void initState() {
    super.initState();
    _playlistsFuture = MusicBridge.instance.getPlaylists();
  }

  Future<void> _reload() async {
    setState(() => _playlistsFuture = MusicBridge.instance.getPlaylists());
    await _playlistsFuture;
  }

  Future<void> _createPlaylist() async {
    final controller = TextEditingController();
    final name = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: MusicTokens.surfaceRaised,
        title: const Text('Новый плейлист', style: MusicTokens.title),
        content: TextField(
          controller: controller,
          autofocus: true,
          style: MusicTokens.body.copyWith(color: MusicTokens.textPrimary),
          decoration: const InputDecoration(hintText: 'Название'),
          onSubmitted: (v) => Navigator.of(context).pop(v),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Отмена')),
          TextButton(
            onPressed: () => Navigator.of(context).pop(controller.text),
            child: const Text('Создать'),
          ),
        ],
      ),
    );
    if (name == null || name.trim().isEmpty) return;
    await MusicBridge.instance.createPlaylist(name.trim());
    _reload();
  }

  Future<void> _openPlaylist(Playlist playlist) async {
    await Navigator.of(context).push(
      MaterialPageRoute(builder: (_) => PlaylistDetailPage(playlist: playlist)),
    );
    _reload(); // track counts may have changed while inside
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    return FutureBuilder<List<Playlist>>(
      future: _playlistsFuture,
      builder: (context, snapshot) {
        final playlists = snapshot.data ?? const [];
        return RefreshIndicator(
          onRefresh: _reload,
          color: MusicTokens.accentPurple,
          backgroundColor: MusicTokens.surfaceRaised,
          child: ListView(
            padding: const EdgeInsets.fromLTRB(MusicTokens.s16, MusicTokens.s8, MusicTokens.s16, MusicTokens.s16),
            children: [
              InkWell(
                onTap: _createPlaylist,
                borderRadius: BorderRadius.circular(MusicTokens.radiusM),
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: MusicTokens.s12),
                  child: Row(
                    children: [
                      Container(
                        width: 48,
                        height: 48,
                        decoration: BoxDecoration(
                          borderRadius: BorderRadius.circular(10),
                          gradient: MusicTokens.accentGradient,
                        ),
                        child: const Icon(Icons.add, color: Colors.white),
                      ),
                      const SizedBox(width: MusicTokens.s12),
                      Text('Новый плейлист', style: MusicTokens.title),
                    ],
                  ),
                ),
              ),
              if (snapshot.connectionState == ConnectionState.waiting)
                const Padding(
                  padding: EdgeInsets.symmetric(vertical: 24),
                  child: Center(child: CircularProgressIndicator(color: MusicTokens.accentPurple)),
                )
              else if (playlists.isEmpty)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 24),
                  child: Center(child: Text('Плейлистов пока нет', style: MusicTokens.body)),
                )
              else
                ...playlists.map((playlist) => InkWell(
                      onTap: () => _openPlaylist(playlist),
                      borderRadius: BorderRadius.circular(MusicTokens.radiusM),
                      child: Padding(
                        padding: const EdgeInsets.symmetric(vertical: MusicTokens.s8),
                        child: Row(
                          children: [
                            Container(
                              width: 48,
                              height: 48,
                              decoration: BoxDecoration(
                                borderRadius: BorderRadius.circular(10),
                                color: MusicTokens.surfaceRaised,
                              ),
                              child: const Icon(Icons.queue_music, color: MusicTokens.textSecondary),
                            ),
                            const SizedBox(width: MusicTokens.s12),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Text(playlist.name, style: MusicTokens.title, maxLines: 1, overflow: TextOverflow.ellipsis),
                                  Text('${playlist.trackCount} треков', style: MusicTokens.caption),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ),
                    )),
            ],
          ),
        );
      },
    );
  }
}
