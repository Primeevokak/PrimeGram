import 'dart:async';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';

import '../bridge.dart';
import '../models.dart';

/// PrimeGram: "Моя волна" card. Composition studied from the real Yandex Music home
/// screen via a Figma community reference (glowing ring, centered "▶ Моя волна", track
/// info + controls once something's playing) - not copied asset-for-asset, our own shader
/// and layout reproducing the same idea. See shaders/wave_ring.frag for the actual glow -
/// a real GLSL fragment shader (dart:ui.FragmentProgram), which is the whole reason this
/// screen moved to Flutter: the native Android side has no GLSL/EGL pipeline (the old
/// PrimeWaveBackgroundView.java's own doc comment says as much), so it could only ever be
/// a Canvas approximation there.
class WaveCard extends StatefulWidget {
  const WaveCard({super.key});

  @override
  State<WaveCard> createState() => _WaveCardState();
}

class _MoodOption {
  final String? tag;
  final String label;
  const _MoodOption(this.tag, this.label);
}

const _moods = [
  _MoodOption(null, 'Всё подряд'),
  _MoodOption('бодрое', '⚡ Бодрое'),
  _MoodOption('спокойное', '🌿 Спокойное'),
  _MoodOption('любимое', '❤️ Любимое'),
  _MoodOption('вечер', '🌙 Вечер'),
];

class _WaveCardState extends State<WaveCard> with SingleTickerProviderStateMixin {
  ui.FragmentShader? _shader;
  late final Ticker _ticker;
  double _elapsed = 0;

  Track? _current;
  String? _activeMood;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _ticker = createTicker((elapsed) {
      setState(() => _elapsed = elapsed.inMicroseconds / 1e6);
    })..start();
    _loadShader();
    _bootstrap();
  }

  Future<void> _loadShader() async {
    try {
      final program = await ui.FragmentProgram.fromAsset('shaders/wave_ring.frag');
      if (!mounted) return;
      setState(() => _shader = program.fragmentShader());
    } catch (_) {
      // No shader support (or asset missing on a design-preview run without the real
      // Android build's asset bundling) - _WaveRingPainter's gradient fallback covers it.
    }
  }

  Future<void> _bootstrap() async {
    final active = await MusicBridge.instance.waveIsActive();
    final track = active ? await MusicBridge.instance.waveCurrent() : null;
    if (!mounted) return;
    setState(() {
      _current = track;
      _loading = false;
    });
  }

  Future<void> _play() async {
    setState(() => _loading = true);
    final track = _current ?? await MusicBridge.instance.waveStart();
    if (track != null) {
      await MusicBridge.instance.playSingle(track.id);
    }
    if (!mounted) return;
    setState(() {
      _current = track;
      _loading = false;
    });
  }

  Future<void> _next() async {
    final track = await MusicBridge.instance.waveNext();
    if (track != null) {
      await MusicBridge.instance.playSingle(track.id);
    }
    if (!mounted) return;
    setState(() => _current = track);
  }

  Future<void> _dislike() async {
    final track = await MusicBridge.instance.waveDislikeAndAdvance();
    if (track != null) {
      await MusicBridge.instance.playSingle(track.id);
    }
    if (!mounted) return;
    setState(() => _current = track);
  }

  Future<void> _selectMood(_MoodOption mood) async {
    setState(() => _activeMood = mood.tag);
    await MusicBridge.instance.waveSetMoodFilter(mood.tag);
  }

  @override
  void dispose() {
    _ticker.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(24),
      child: SizedBox(
        height: 340,
        child: Stack(
          fit: StackFit.expand,
          children: [
            CustomPaint(
              painter: _shader != null
                  ? _WaveRingShaderPainter(_shader!, _elapsed, energy: _current != null ? 1 : 0.4)
                  : _WaveRingFallbackPainter(_elapsed),
            ),
            // Mood chips - a thin, unobtrusive strip up top. Not part of the reference
            // screen (Yandex's own home doesn't filter by mood inline like this), but the
            // backend already exposes it (PrimeMusicWave.setMoodFilter), so it stays,
            // styled to not fight the ring visually.
            Positioned(
              top: 12,
              left: 12,
              right: 12,
              child: SizedBox(
                height: 28,
                child: ListView.separated(
                  scrollDirection: Axis.horizontal,
                  itemCount: _moods.length,
                  separatorBuilder: (_, _) => const SizedBox(width: 6),
                  itemBuilder: (context, index) {
                    final mood = _moods[index];
                    final selected = mood.tag == _activeMood;
                    return ChoiceChip(
                      label: Text(mood.label, style: const TextStyle(fontSize: 11)),
                      selected: selected,
                      onSelected: (_) => _selectMood(mood),
                      selectedColor: Colors.white,
                      backgroundColor: const Color(0x1AFFFFFF),
                      labelStyle: TextStyle(color: selected ? Colors.black : const Color(0xCCFFFFFF)),
                      side: BorderSide.none,
                      visualDensity: VisualDensity.compact,
                      materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                      padding: const EdgeInsets.symmetric(horizontal: 6),
                    );
                  },
                ),
              ),
            ),
            if (_current == null)
              Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    if (_loading)
                      const CircularProgressIndicator(color: Colors.white)
                    else
                      InkWell(
                        onTap: _play,
                        borderRadius: BorderRadius.circular(28),
                        child: const Padding(
                          padding: EdgeInsets.symmetric(horizontal: 8, vertical: 8),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Icon(Icons.play_arrow, color: Color(0xCCFFFFFF), size: 28),
                              SizedBox(width: 8),
                              Text(
                                'Моя волна',
                                style: TextStyle(color: Color(0xCCFFFFFF), fontSize: 22, fontWeight: FontWeight.w700),
                              ),
                            ],
                          ),
                        ),
                      ),
                  ],
                ),
              )
            else
              Positioned(
                left: 16,
                right: 16,
                bottom: 16,
                child: Row(
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text(
                            _current!.title.isEmpty ? 'Без названия' : _current!.title,
                            style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                          Text(
                            _current!.artist,
                            style: const TextStyle(color: Color(0xCCFFFFFF), fontSize: 12),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ],
                      ),
                    ),
                    _circleButton(Icons.thumb_down_alt_outlined, _dislike),
                    const SizedBox(width: 8),
                    _circleButton(Icons.skip_next, _next),
                  ],
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _circleButton(IconData icon, VoidCallback onTap) {
    return InkWell(
      customBorder: const CircleBorder(),
      onTap: onTap,
      child: Container(
        width: 40,
        height: 40,
        decoration: const BoxDecoration(color: Color(0x22FFFFFF), shape: BoxShape.circle),
        child: Icon(icon, color: Colors.white, size: 20),
      ),
    );
  }
}

/// Feeds the live GLSL ring shader its uniforms every frame - uSize, uTime, uEnergy (0 =
/// idle/ambient, 1 = a track is actually playing) in exactly that slot order, matching
/// wave_ring.frag's uniform declarations.
class _WaveRingShaderPainter extends CustomPainter {
  final ui.FragmentShader shader;
  final double time;
  final double energy;
  _WaveRingShaderPainter(this.shader, this.time, {required this.energy});

  @override
  void paint(Canvas canvas, Size size) {
    shader
      ..setFloat(0, size.width)
      ..setFloat(1, size.height)
      ..setFloat(2, time)
      ..setFloat(3, energy);
    canvas.drawRect(Offset.zero & size, Paint()..shader = shader);
  }

  @override
  bool shouldRepaint(covariant _WaveRingShaderPainter oldDelegate) =>
      oldDelegate.time != time || oldDelegate.energy != energy;
}

/// Plain-Dart stand-in for when the shader hasn't loaded yet (first frame) or genuinely
/// isn't available - a simple radial gradient ring, nowhere near as rich as the real
/// shader but keeps the card from ever showing nothing.
class _WaveRingFallbackPainter extends CustomPainter {
  final double time;
  _WaveRingFallbackPainter(this.time);

  @override
  void paint(Canvas canvas, Size size) {
    canvas.drawRect(Offset.zero & size, Paint()..color = const Color(0xFF0D0D10));
    final center = Offset(size.width / 2, size.height / 2 + size.height * 0.05);
    final radius = size.shortestSide * 0.46;
    // Filled sphere, not a ring outline - a rotating sweep gradient is the cheapest way to
    // get "colors flowing inside" without the real shader, while it loads.
    final paint = Paint()
      ..shader = SweepGradient(
        transform: GradientRotation(time * 0.6),
        colors: const [Color(0xFF5226FE), Color(0xFFB221FF), Color(0xFFFF1BD8), Color(0xFF5226FE)],
      ).createShader(Rect.fromCircle(center: center, radius: radius));
    canvas.drawCircle(center, radius, paint);
  }

  @override
  bool shouldRepaint(covariant _WaveRingFallbackPainter oldDelegate) => oldDelegate.time != time;
}
