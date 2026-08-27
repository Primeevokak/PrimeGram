import 'dart:io';

import 'package:flutter/material.dart';

import '../models.dart';

/// PrimeGram Music design system - one place for color/type/spacing so every screen reads
/// as the same product. Direction: streaming-service energy (the "Моя волна" glow, the
/// endless-radio hero moment - Yandex Music/Spotify/YT Music DNA) fused with
/// offline-library practicality (source badges, dense scannable lists - Namida DNA,
/// studied via namida-repo as a *visual/UX* reference, not code reuse). Our tracks
/// genuinely come from three different places (Telegram chat audio, downloaded local
/// files, on-device scan) - a streaming app never needs to say where a track came from,
/// an offline player always does. MusicTokens.sourceColor/sourceLabel exist because of
/// that real difference, not decoration.
class MusicTokens {
  MusicTokens._();

  // ---- Surfaces -----------------------------------------------------------
  // Warm-black, not a cold neutral #000/#101010 - tinted a few degrees toward the accent
  // family so the whole app reads as one considered palette rather than "dark mode
  // default + a purple button".
  static const Color bg = Color(0xFF0E0B13);
  static const Color surface = Color(0xFF17131F);
  static const Color surfaceRaised = Color(0xFF201A2B);
  static const Color divider = Color(0x1AFFFFFF);

  // ---- Text -----------------------------------------------------------
  static const Color textPrimary = Color(0xFFF4EFEA); // warm cream, not pure white
  static const Color textSecondary = Color(0xFFA79DB3); // muted violet-gray
  static const Color textTertiary = Color(0xFF6E6579);

  // ---- Accent (the wave's own palette family - see shaders/wave_ring.frag) ---------
  static const Color accentViolet = Color(0xFF5226FE);
  static const Color accentPurple = Color(0xFFB221FF);
  static const Color accentMagenta = Color(0xFFFF1BD8);
  static const LinearGradient accentGradient = LinearGradient(
    colors: [accentViolet, accentPurple],
    begin: Alignment.centerLeft,
    end: Alignment.centerRight,
  );

  // ---- Track source badges - the offline-library-style detail streaming apps skip ---
  static const Color sourceTelegram = Color(0xFF5AC8FA); // soft cyan
  static const Color sourceLocal = Color(0xFFFFC24D); // warm gold
  static const Color sourceDevice = Color(0xFF7CE0A6); // soft mint

  static Color sourceColor(int source) {
    switch (source) {
      case 1:
        return sourceLocal;
      case 2:
        return sourceDevice;
      default:
        return sourceTelegram;
    }
  }

  static String sourceLabel(int source) {
    switch (source) {
      case 1:
        return 'Файл';
      case 2:
        return 'Устройство';
      default:
        return 'Telegram';
    }
  }

  // ---- Type -----------------------------------------------------------
  // Unbounded (bold, geometric, Cyrillic-native) for anything that needs to feel like a
  // hero moment; Golos Text (VK's own open grotesque) for everything read at length -
  // deliberately not Inter/Roboto/Space Grotesk.
  static const String displayFont = 'Unbounded';
  static const String bodyFont = 'GolosText';

  static const TextStyle hero = TextStyle(
    fontFamily: displayFont,
    fontSize: 26,
    fontWeight: FontWeight.w700,
    color: textPrimary,
    height: 1.15,
  );

  static const TextStyle title = TextStyle(
    fontFamily: bodyFont,
    fontSize: 16,
    fontWeight: FontWeight.w600,
    color: textPrimary,
    height: 1.3,
  );

  static const TextStyle body = TextStyle(
    fontFamily: bodyFont,
    fontSize: 14,
    fontWeight: FontWeight.w500,
    color: textSecondary,
    height: 1.3,
  );

  static const TextStyle caption = TextStyle(
    fontFamily: bodyFont,
    fontSize: 12,
    fontWeight: FontWeight.w500,
    color: textTertiary,
    height: 1.2,
    fontFeatures: [FontFeature.tabularFigures()],
  );

  static const TextStyle timecode = TextStyle(
    fontFamily: bodyFont,
    fontSize: 12,
    fontWeight: FontWeight.w600,
    color: textSecondary,
    fontFeatures: [FontFeature.tabularFigures()],
  );

  // ---- Spacing -----------------------------------------------------------
  static const double s4 = 4;
  static const double s8 = 8;
  static const double s12 = 12;
  static const double s16 = 16;
  static const double s24 = 24;
  static const double s32 = 32;

  static const double radiusM = 14;
  static const double radiusL = 24;

  /// mm:ss for a track duration in seconds - used everywhere a duration/position is shown.
  static String formatDuration(num seconds) {
    final s = seconds.round();
    final m = s ~/ 60;
    final r = s % 60;
    return '$m:${r.toString().padLeft(2, '0')}';
  }
}

/// Deterministic placeholder artwork for tracks without real cover art - a gradient tile
/// (hashed from title+artist so the same track always gets the same look) with the
/// track's initial. Namida-style offline players lean on this constantly since local
/// files often lack embedded art; a streaming catalog never has to.
class TrackArtPlaceholder extends StatelessWidget {
  final String title;
  final double size;
  final double radius;

  const TrackArtPlaceholder({super.key, required this.title, this.size = 48, this.radius = 10});

  @override
  Widget build(BuildContext context) {
    final seed = title.isEmpty ? 0 : title.codeUnits.fold<int>(0, (a, b) => a + b);
    final palettes = [
      [MusicTokens.accentViolet, MusicTokens.accentMagenta],
      [MusicTokens.sourceDevice, MusicTokens.accentPurple],
      [MusicTokens.sourceLocal, MusicTokens.accentMagenta],
      [MusicTokens.sourceTelegram, MusicTokens.accentViolet],
    ];
    final colors = palettes[seed % palettes.length];
    final letter = title.trim().isEmpty ? '?' : title.trim()[0].toUpperCase();
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(radius),
        gradient: LinearGradient(colors: colors, begin: Alignment.topLeft, end: Alignment.bottomRight),
      ),
      alignment: Alignment.center,
      child: Text(
        letter,
        style: TextStyle(
          fontFamily: MusicTokens.displayFont,
          fontWeight: FontWeight.w700,
          fontSize: size * 0.38,
          color: Colors.white.withValues(alpha: 0.92),
        ),
      ),
    );
  }
}

/// Real cover art when a track has one (Track.coverPath - a local filesystem path, valid
/// to read directly since the Flutter engine runs inside the same app process/sandbox as
/// the rest of PrimeGram, no network round trip involved), falling back to
/// [TrackArtPlaceholder] when there's no path or the file fails to load. Every place a
/// track's art shows should use this, not TrackArtPlaceholder directly.
class TrackArt extends StatelessWidget {
  final Track? track;
  final double size;
  final double radius;

  const TrackArt({super.key, required this.track, this.size = 48, this.radius = 10});

  @override
  Widget build(BuildContext context) {
    final coverPath = track?.coverPath;
    if (coverPath == null || coverPath.isEmpty) {
      return TrackArtPlaceholder(title: track?.title ?? '', size: size, radius: radius);
    }
    return ClipRRect(
      borderRadius: BorderRadius.circular(radius),
      child: Image.file(
        File(coverPath),
        width: size,
        height: size,
        fit: BoxFit.cover,
        errorBuilder: (context, error, stackTrace) =>
            TrackArtPlaceholder(title: track?.title ?? '', size: size, radius: radius),
      ),
    );
  }
}
