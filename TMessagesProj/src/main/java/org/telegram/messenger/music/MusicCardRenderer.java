package org.telegram.messenger.music;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;

import org.telegram.messenger.FileLog;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Renders the "now playing" card as a Bitmap via Android's Canvas — a native
 * equivalent of reSwaga's PIL-based card, at the same 1440x600 layout.
 * Unlike the original, this uses the system Typeface instead of bundled/
 * downloaded custom fonts (Onest/Circular/etc.) to avoid a whole runtime
 * font-download subsystem for a purely cosmetic difference.
 */
public class MusicCardRenderer {

    public static final int WIDTH = 1440;
    public static final int HEIGHT = 600;

    private static final float MIN_LIGHTNESS_FOR_TEXT = 0.725f;
    private static final float MAX_LIGHTNESS_FOR_TEXT = 0.25f;
    private static final float LIGHTNESS_THRESHOLD = 0.5f;

    public static class Style {
        public int backgroundColor = Color.BLACK;
        public int titleColor = Color.WHITE;
        public int subtextColor = Color.parseColor("#A0A0A0");
        /** 0 = blurred cover background, 1 = solid accent color extracted from cover. */
        public int backgroundMode = 1;
        public String brandText = "Музыка";
        /** One of {@link MusicResources#FONT_FAMILIES}. Falls back to the system font if not downloaded. */
        public String fontFamily = "System";
    }

    public static Bitmap renderHorizontalCard(Track track, Style style) {
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        if (track == null || !track.active) {
            canvas.drawColor(style.backgroundColor);
            drawInactiveState(canvas, style);
            return bitmap;
        }

        Bitmap cover = downloadCover(track.thumbUrl);

        if (style.backgroundMode == 0 && cover != null) {
            drawBlurredBackground(canvas, cover, style.backgroundColor);
        } else if (cover != null) {
            canvas.drawColor(getCoverAccentColor(cover));
        } else {
            canvas.drawColor(style.backgroundColor);
        }

        if (cover != null) {
            drawRoundedCover(canvas, cover, 75, 75, 450, 30);
        }

        Typeface bold = MusicResources.getTypeface(style.fontFamily, true);
        Typeface regular = MusicResources.getTypeface(style.fontFamily, false);

        TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setTypeface(bold);
        titlePaint.setTextSize(60);
        titlePaint.setColor(style.titleColor);

        float x = 590, y = 85 + 50; // baseline offset for a 60px font
        String[] titleLines = wrapToLines(track.title == null ? "" : track.title, titlePaint, WIDTH - (int) x - 40, 2);
        float artistsExtraY = titleLines.length > 1 ? 70 : 0;
        for (String line : titleLines) {
            canvas.drawText(line, x, y, titlePaint);
            y += 70;
        }

        TextPaint artistPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        artistPaint.setTypeface(regular);
        artistPaint.setTextSize(40);
        artistPaint.setColor(style.subtextColor);
        String artists = TextUtils.join(" • ", track.artists);
        String artistLine = TextUtils.ellipsize(artists, artistPaint, WIDTH - x - 40, TextUtils.TruncateAt.END).toString();
        canvas.drawText(artistLine, x, 170 + artistsExtraY + 30, artistPaint);

        if (track.progressSec > 0 && track.durationSec > 0) {
            drawProgressBar(canvas, track, style, regular);
        } else {
            drawBrandFooter(canvas, style, regular, bold);
        }

        return bitmap;
    }

    private static void drawInactiveState(Canvas canvas, Style style) {
        TextPaint brandPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        brandPaint.setTypeface(MusicResources.getTypeface(style.fontFamily, false));
        brandPaint.setTextSize(40);
        brandPaint.setColor(style.titleColor);
        brandPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(style.brandText, WIDTH / 2f, 45 + 15, brandPaint);

        TextPaint bigPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        bigPaint.setTypeface(MusicResources.getTypeface(style.fontFamily, true));
        bigPaint.setTextSize(80);
        bigPaint.setColor(style.titleColor);
        bigPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("Ничего не играет", WIDTH / 2f, HEIGHT / 2f + 25, bigPaint);
    }

    private static void drawProgressBar(Canvas canvas, Track track, Style style, Typeface regular) {
        float barLeft = 590, barTop = 460, barWidth = WIDTH - 665 - barLeft + 590, barHeight = 10;
        RectF full = new RectF(barLeft, barTop, barLeft + (WIDTH - 665), barTop + barHeight);
        Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        emptyPaint.setColor(style.subtextColor);
        canvas.drawRoundRect(full, 7, 7, emptyPaint);

        float progressFraction = Math.min(1f, track.progressSec / (float) track.durationSec);
        RectF filled = new RectF(barLeft, barTop, barLeft + (WIDTH - 665) * progressFraction, barTop + barHeight);
        Paint filledPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        filledPaint.setColor(style.titleColor);
        canvas.drawRoundRect(filled, 7, 7, filledPaint);

        TextPaint timePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        timePaint.setTypeface(regular);
        timePaint.setTextSize(30);
        timePaint.setColor(style.subtextColor);
        canvas.drawText(formatTime(track.progressSec), barLeft, 490 + 20, timePaint);
        timePaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(formatTime(track.durationSec), 1365, 490 + 20, timePaint);
    }

    private static void drawBrandFooter(Canvas canvas, Style style, Typeface regular, Typeface bold) {
        TextPaint subtextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        subtextPaint.setTypeface(regular);
        subtextPaint.setTextSize(42);
        subtextPaint.setColor(style.subtextColor);
        canvas.drawText("проигрывается через", 590, 415, subtextPaint);

        TextPaint brandPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        brandPaint.setTypeface(bold);
        brandPaint.setTextSize(52);
        brandPaint.setColor(style.titleColor);
        canvas.drawText(style.brandText, 590, 485, brandPaint);
    }

    private static String formatTime(int totalSeconds) {
        int m = totalSeconds / 60;
        int s = totalSeconds % 60;
        return String.format(java.util.Locale.US, "%02d:%02d", m, s);
    }

    private static String[] wrapToLines(String text, TextPaint paint, int maxWidthPx, int maxLines) {
        android.text.StaticLayout layout = android.text.StaticLayout.Builder
                .obtain(text, 0, text.length(), paint, maxWidthPx)
                .setMaxLines(maxLines + 1)
                .build();
        int lineCount = Math.min(layout.getLineCount(), maxLines);
        String[] lines = new String[lineCount];
        for (int i = 0; i < lineCount; i++) {
            int start = layout.getLineStart(i);
            int end = layout.getLineEnd(i);
            String line = text.substring(start, Math.min(end, text.length()));
            if (i == lineCount - 1 && layout.getLineCount() > maxLines) {
                line = TextUtils.ellipsize(line, paint, maxWidthPx, TextUtils.TruncateAt.END).toString();
            }
            lines[i] = line.trim();
        }
        return lines;
    }

    private static void drawRoundedCover(Canvas canvas, Bitmap cover, int left, int top, int size, int radius) {
        Bitmap scaled = Bitmap.createScaledBitmap(cover, size, size, true);
        Path clip = new Path();
        clip.addRoundRect(new RectF(left, top, left + size, top + size), radius, radius, Path.Direction.CW);
        int saveCount = canvas.save();
        canvas.clipPath(clip);
        canvas.drawBitmap(scaled, left, top, new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
        canvas.restoreToCount(saveCount);
    }

    /** Cheap "blur": downscale hard, then upscale with bilinear filtering — no RenderEffect/version gating needed. */
    private static void drawBlurredBackground(Canvas canvas, Bitmap cover, int fallbackColor) {
        try {
            Bitmap tiny = Bitmap.createScaledBitmap(cover, 24, (int) (24f * HEIGHT / WIDTH), true);
            Bitmap upscaled = Bitmap.createScaledBitmap(tiny, WIDTH, HEIGHT, true);
            Paint darken = new Paint();
            canvas.drawBitmap(upscaled, 0, 0, new Paint(Paint.FILTER_BITMAP_FLAG));
            darken.setColor(Color.argb(140, 0, 0, 0));
            canvas.drawRect(0, 0, WIDTH, HEIGHT, darken);
        } catch (Exception e) {
            canvas.drawColor(fallbackColor);
        }
    }

    /** Port of reSwaga's get_cover_accent_color: gamma-correct downscale + darkened average. */
    public static int getCoverAccentColor(Bitmap cover) {
        Bitmap small = Bitmap.createScaledBitmap(cover, 16, 16, true);
        long totalR = 0, totalG = 0, totalB = 0;
        int count = small.getWidth() * small.getHeight();
        double darknessIndex = 2.5;
        for (int py = 0; py < small.getHeight(); py++) {
            for (int px = 0; px < small.getWidth(); px++) {
                int pixel = small.getPixel(px, py);
                double r = Math.pow(Color.red(pixel) / 255.0, 1 / 2.2) * 255.0;
                double g = Math.pow(Color.green(pixel) / 255.0, 1 / 2.2) * 255.0;
                double b = Math.pow(Color.blue(pixel) / 255.0, 1 / 2.2) * 255.0;
                totalR += (long) (r / darknessIndex);
                totalG += (long) (g / darknessIndex);
                totalB += (long) (b / darknessIndex);
            }
        }
        return Color.rgb((int) (totalR / count), (int) (totalG / count), (int) (totalB / count));
    }

    /** Port of reSwaga's adjust_color_for_readability. */
    public static int adjustColorForReadability(int rgbColor) {
        float[] hsl = new float[3];
        android.graphics.Color.colorToHSV(rgbColor, hsl); // hue, sat, value — approximate HSL via HSV here is fine for this heuristic
        float lightness = hsl[2] * (1 - hsl[1] / 2f);
        float newLightness = lightness < LIGHTNESS_THRESHOLD ? MIN_LIGHTNESS_FOR_TEXT : MAX_LIGHTNESS_FOR_TEXT;
        float[] out = new float[3];
        android.graphics.Color.RGBToHSV(Color.red(rgbColor), Color.green(rgbColor), Color.blue(rgbColor), out);
        out[2] = newLightness;
        return android.graphics.Color.HSVToColor(out);
    }

    private static Bitmap downloadCover(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            if (conn.getResponseCode() != 200) {
                return null;
            }
            try (InputStream is = conn.getInputStream()) {
                return android.graphics.BitmapFactory.decodeStream(is);
            }
        } catch (Exception e) {
            FileLog.e("MusicCardRenderer.downloadCover " + url, e);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
