import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

// PrimeGram: this import is load-bearing, not decoration - the Dart compiler only
// includes a library in the compiled program if it's reachable via import from the root
// script (lib/main.dart, the default build target), regardless of @pragma('vm:entry-point')
// on anything inside it. Without this line, music_main.dart's musicMain() entry point
// (used by PrimeMusicFlutterEngineHolder for the Music tab) doesn't exist in the compiled
// app at all - confirmed on device: "Dart_LookupLibrary: library '...music_main.dart' not
// found" - not a missing-symbol error, the whole library was absent from the build.
// ignore: unused_import
import 'music_main.dart';

// PrimeGram: minimal proof-of-life screen for the embedded Flutter engine -
// confirms the engine boots, the activity opens, and closes back to the host
// app cleanly. No MethodChannel wiring yet; that is the natural next step
// once this is confirmed working, not part of this pass.
void main() => runApp(const PrimeFlutterApp());

class PrimeFlutterApp extends StatelessWidget {
  const PrimeFlutterApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'PrimeGram Flutter',
      theme: ThemeData(useMaterial3: true, colorSchemeSeed: Colors.deepPurple),
      home: const HelloFlutterPage(),
    );
  }
}

class HelloFlutterPage extends StatelessWidget {
  const HelloFlutterPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Hello Flutter')),
      body: const Center(
        child: Text(
          'Двигатель Flutter встроен в PrimeGram и работает.',
          textAlign: TextAlign.center,
          style: TextStyle(fontSize: 18),
        ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        // There is no Android activity stack above this engine to pop back
        // into - PrimeFlutterActivity is the only Activity in play, so this
        // closes it directly rather than calling Navigator.pop.
        onPressed: () => SystemNavigator.pop(),
        icon: const Icon(Icons.arrow_back),
        label: const Text('Назад'),
      ),
    );
  }
}
