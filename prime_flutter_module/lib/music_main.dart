import 'package:flutter/material.dart';

import 'music/design/tokens.dart';
import 'music/pages/home_page.dart';

// PrimeGram: second Dart entry point in this module, alongside the original main() (the
// "Hello Flutter" infra proof-of-life screen in main.dart, left untouched) - this is the
// real Music tab UI, run on its own dedicated FlutterEngine
// (org.telegram.messenger.music.PrimeMusicFlutterEngineHolder) embedded inside
// PrimeMusicActivity. Two independent entry points in one module is standard Flutter
// add-to-app practice.
@pragma('vm:entry-point')
void musicMain() => runApp(const PrimeMusicApp());

class PrimeMusicApp extends StatelessWidget {
  const PrimeMusicApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'PrimeGram Music',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        brightness: Brightness.dark,
        fontFamily: MusicTokens.bodyFont,
        scaffoldBackgroundColor: MusicTokens.bg,
        colorScheme: ColorScheme.fromSeed(
          seedColor: MusicTokens.accentPurple,
          brightness: Brightness.dark,
          surface: MusicTokens.surface,
        ),
        splashFactory: InkRipple.splashFactory,
      ),
      home: const MusicHomePage(),
    );
  }
}
