import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../design/tokens.dart';

/// PrimeGram: a tap/drag-to-seek bar drawn as vertical bars (Namida-style "you can see the
/// track's shape" look) rather than a plain line. Explicitly NOT real audio amplitude data -
/// that needs decoding the actual file (native MediaExtractor/MediaCodec peak sampling,
/// cached per track) which is a real separate project, not bundled into this pass. This is
/// a deterministic procedural pattern seeded by the track id, so a given track always
/// looks the same rather than reshuffling every rebuild - purely a visual upgrade over a
/// flat Slider until real waveform extraction exists.
class WaveformSeekBar extends StatelessWidget {
  final int trackId;
  final double progress;
  final ValueChanged<double> onSeek;

  const WaveformSeekBar({super.key, required this.trackId, required this.progress, required this.onSeek});

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final width = constraints.maxWidth;
        void seekAt(double dx) => onSeek((dx / width).clamp(0.0, 1.0));
        return GestureDetector(
          onTapDown: (d) => seekAt(d.localPosition.dx),
          onHorizontalDragUpdate: (d) => seekAt(d.localPosition.dx),
          child: SizedBox(
            width: width,
            height: 36,
            child: CustomPaint(painter: _WaveformPainter(trackId: trackId, progress: progress)),
          ),
        );
      },
    );
  }
}

class _WaveformPainter extends CustomPainter {
  final int trackId;
  final double progress;
  _WaveformPainter({required this.trackId, required this.progress});

  @override
  void paint(Canvas canvas, Size size) {
    final barWidth = 3.0;
    final gap = 2.0;
    final barCount = (size.width / (barWidth + gap)).floor().clamp(10, 200);
    final random = math.Random(trackId);
    final playedPaint = Paint()..color = MusicTokens.accentPurple;
    final unplayedPaint = Paint()..color = MusicTokens.divider;

    for (var i = 0; i < barCount; i++) {
      final heightFactor = 0.2 + random.nextDouble() * 0.8;
      final barHeight = size.height * heightFactor;
      final x = i * (barWidth + gap);
      final rect = Rect.fromLTWH(x, (size.height - barHeight) / 2, barWidth, barHeight);
      final played = (i / barCount) <= progress;
      canvas.drawRRect(
        RRect.fromRectAndRadius(rect, const Radius.circular(1.5)),
        played ? playedPaint : unplayedPaint,
      );
    }
  }

  @override
  bool shouldRepaint(covariant _WaveformPainter oldDelegate) =>
      oldDelegate.progress != progress || oldDelegate.trackId != trackId;
}
