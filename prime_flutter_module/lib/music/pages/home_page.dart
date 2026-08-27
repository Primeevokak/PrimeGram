import 'package:flutter/material.dart';

import '../bridge.dart';
import '../design/tokens.dart';
import '../widgets/mini_player.dart';
import '../widgets/wave_card.dart';
import 'playlists_tab.dart';
import 'tracks_tab.dart';

/// PrimeGram: Music tab root - three swipeable tabs (standard Flutter TabBar/TabBarView,
/// not Namida's own GetX-based nav - same user-facing outcome, no extra dependency):
/// Волна (the streaming-style endless radio, see wave_card.dart), Треки (every track,
/// flat - the offline-library-style browse Namida has and pure streaming apps don't need),
/// Плейлисты. Mini player stays docked under all three.
class MusicHomePage extends StatelessWidget {
  const MusicHomePage({super.key});

  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: 3,
      child: Scaffold(
        backgroundColor: MusicTokens.bg,
        appBar: AppBar(
          backgroundColor: MusicTokens.bg,
          elevation: 0,
          title: const Text('Музыка', style: MusicTokens.hero),
          bottom: TabBar(
            indicatorColor: MusicTokens.accentPurple,
            labelColor: MusicTokens.textPrimary,
            unselectedLabelColor: MusicTokens.textTertiary,
            labelStyle: const TextStyle(fontFamily: MusicTokens.bodyFont, fontWeight: FontWeight.w700, fontSize: 14),
            unselectedLabelStyle: const TextStyle(fontFamily: MusicTokens.bodyFont, fontWeight: FontWeight.w500, fontSize: 14),
            tabs: const [
              Tab(text: 'Волна'),
              Tab(text: 'Треки'),
              Tab(text: 'Плейлисты'),
            ],
          ),
        ),
        body: Column(
          children: [
            Expanded(
              // Same reasoning as the mood chip strip (see wave_card.dart) - swiping
              // between Волна/Треки/Плейлисты is itself a horizontal drag, and without
              // this the outer tab ViewPager (Chats/Music/Settings) fights it for the
              // same gesture instead of the Music tabs actually swiping.
              child: NotificationListener<ScrollNotification>(
                // Axis-filtered on purpose: this wraps the whole TabBarView, and its
                // children (Треки/Плейлисты) scroll vertically - those notifications
                // bubble up through this same listener. Without the axis check, plain
                // list scrolling anywhere in the Music tab was flagged as "horizontal
                // scroll active" and never cleanly reset, which is what left the outer
                // Chats/Music/Settings swipe blocked almost everywhere except the very
                // top of the screen.
                onNotification: (notification) {
                  if (notification.metrics.axis != Axis.horizontal) return false;
                  if (notification is ScrollStartNotification) {
                    MusicBridge.instance.setHorizontalScrollActive(true);
                  } else if (notification is ScrollEndNotification) {
                    MusicBridge.instance.setHorizontalScrollActive(false);
                  }
                  return false;
                },
                child: const TabBarView(
                  children: [
                    _WaveTab(),
                    TracksTab(),
                    PlaylistsTab(),
                  ],
                ),
              ),
            ),
            const MiniPlayer(),
          ],
        ),
      ),
    );
  }
}

class _WaveTab extends StatelessWidget {
  const _WaveTab();

  @override
  Widget build(BuildContext context) {
    return const SingleChildScrollView(
      padding: EdgeInsets.all(MusicTokens.s16),
      child: WaveCard(),
    );
  }
}
