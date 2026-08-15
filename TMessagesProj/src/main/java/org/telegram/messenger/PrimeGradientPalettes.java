package org.telegram.messenger;

import java.util.ArrayList;
import java.util.List;

/**
 * PrimeGram: curated gradient palettes for the custom chat-theme creator
 * ({@code ui.PrimeCustomThemeActivity}) - the right-hand carousel's built-in options,
 * alongside the user's own "+ Свой цвет" combo picked live.
 *
 * <p>Named and colored in the spirit of the moody, mesh-gradient swatch-card references the
 * palette carousel's visual design is built around: dark ground, a named card, a soft gradient
 * fill, 2-3 hex chips underneath.
 */
public final class PrimeGradientPalettes {

    public static final class Palette {
        public final String name;
        public final int color1;
        public final int color2;
        public final int color3; // 0 if this palette is only 2 colors

        public Palette(String name, int color1, int color2, int color3) {
            this.name = name;
            this.color1 = color1;
            this.color2 = color2;
            this.color3 = color3;
        }
    }

    private PrimeGradientPalettes() {
    }

    public static List<Palette> all() {
        List<Palette> list = new ArrayList<>();
        list.add(new Palette("Аура", 0xFFFF9CE3, 0xFF7DD3FC, 0xFFB388FF));
        list.add(new Palette("Солнечная вспышка", 0xFFFFB800, 0xFFFF6B00, 0xFFFF2E92));
        list.add(new Palette("Голограмма", 0xFFC3B8F5, 0xFF9BE8E0, 0xFFF5C6E8));
        list.add(new Palette("Зерно и дымка", 0xFFFF9E64, 0xFFD6549F, 0xFF6E3FD1));
        list.add(new Palette("Глубина океана", 0xFF0F2C4C, 0xFF1E5A8A, 0xFF39C0C8));
        list.add(new Palette("Полночь", 0xFF0B0F2A, 0xFF3A1C6E, 0xFF9B2FAE));
        list.add(new Palette("Мятная свежесть", 0xFF7BE0AD, 0xFF3FB3C4, 0xFF2E7FBF));
        list.add(new Palette("Розовый кварц", 0xFFFFD1E3, 0xFFF6A6C1, 0xFFD98FC7));
        list.add(new Palette("Графит", 0xFF23272E, 0xFF3B424D, 0xFF5A6472));
        list.add(new Palette("Закат Сахары", 0xFFF6B26B, 0xFFE0644C, 0xFF8E3B46));
        return list;
    }
}
