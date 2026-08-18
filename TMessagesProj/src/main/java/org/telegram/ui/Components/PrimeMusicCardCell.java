package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

/**
 * PrimeGram Music: a premium Spotify-style square-cover card for carousels.
 * Uses procedural multi-stop gradients matching the seed hash when real artwork is absent,
 * with a distinct outlined/dashed style for the "+ Новый плейлист" action card.
 */
public class PrimeMusicCardCell extends LinearLayout {

    public static final int CARD_SIZE_DP = 132;
    public static final int CARD_RADIUS_DP = 14;

    // Curated high-vibrancy music gradients (Spotify / Apple Music style)
    public static final int[][] GRADIENT_PALETTES = new int[][]{
            {0xFF8A2387, 0xFFE94057, 0xFFF27121}, // Sunset Punch
            {0xFF00C6FF, 0xFF0072FF},             // Cyber Blue
            {0xFF11998E, 0xFF38EF7D},             // Neon Mint
            {0xFF7F00FF, 0xFFE100FF},             // Electric Violet
            {0xFFFF416C, 0xFFFF4B2B},             // Passion Red
            {0xFF3A1C71, 0xFFD76D77, 0xFFFFAF7B}, // Twilight
            {0xFF0575E6, 0xFF00F260},             // Aurora Cyan
            {0xFF4A00E0, 0xFF8E2DE2},             // Deep Indigo
            {0xFFFF512F, 0xFFDD2476},             // Blood Orange / Magenta
            {0xFF1F4037, 0xFF99F2C8}              // Emerald Glade
    };

    private final FrameLayout coverFrame;
    private final ImageView artView;
    private final ImageView coverIcon;
    private final FrameLayout iconGlowDisc;
    private final TextView title;
    private final TextView subtitle;

    public PrimeMusicCardCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(0, 0, dp(14), 0);

        coverFrame = new FrameLayout(context);
        addView(coverFrame, LayoutHelper.createLinear(CARD_SIZE_DP, CARD_SIZE_DP));

        // Full-tile real artwork, shown only when a real embedded cover was found -
        // see PrimeAlbumArt.applyCover. Hidden (no drawable) otherwise, letting the
        // placeholder glow disc below show through.
        artView = new ImageView(context);
        artView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        coverFrame.addView(artView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // Subtle glow disc behind icon - the placeholder look, shown when there's no
        // real artwork for this card.
        iconGlowDisc = new FrameLayout(context);
        coverFrame.addView(iconGlowDisc, LayoutHelper.createFrame(44, 44, Gravity.CENTER));

        coverIcon = new ImageView(context);
        coverIcon.setScaleType(ImageView.ScaleType.CENTER);
        iconGlowDisc.addView(coverIcon, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        title = new TextView(context);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        addView(title, LayoutHelper.createLinear(CARD_SIZE_DP, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));

        subtitle = new TextView(context);
        subtitle.setTextColor(0xFF8E8E93);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        subtitle.setMaxLines(1);
        subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        addView(subtitle, LayoutHelper.createLinear(CARD_SIZE_DP, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        setClickable(true);
        setBackground(Theme.createSelectorDrawable(0x20FFFFFF, Theme.RIPPLE_MASK_ALL));
    }

    public static GradientDrawable createProceduralCoverDrawable(String seed, int cornerRadiusDp) {
        int index = 0;
        if (seed != null && !seed.isEmpty()) {
            index = Math.abs(seed.hashCode()) % GRADIENT_PALETTES.length;
        }
        int[] colors = GRADIENT_PALETTES[index];
        GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        gd.setCornerRadius(dp(cornerRadiusDp));
        return gd;
    }

    public void bind(String titleText, String subtitleText) {
        bind(titleText, subtitleText, null);
    }

    public void bind(String titleText, String subtitleText, String coverPath) {
        boolean isAddAction = "+ Новый плейлист".equals(titleText);

        if (isAddAction) {
            // Distinct sleek outlined/dashed card for "Add Playlist"
            artView.setImageDrawable(null);
            GradientDrawable outline = new GradientDrawable();
            outline.setColor(0x18FFFFFF);
            outline.setCornerRadius(dp(CARD_RADIUS_DP));
            outline.setStroke(dp(1.5f), 0x4DFFFFFF, dp(4), dp(3));
            coverFrame.setBackground(outline);

            iconGlowDisc.setVisibility(VISIBLE);
            iconGlowDisc.setBackground(Theme.createCircleDrawable(dp(44), 0x221DB954));
            coverIcon.setImageResource(R.drawable.msg_add);
            coverIcon.setColorFilter(new PorterDuffColorFilter(0xFF1DB954, PorterDuff.Mode.SRC_IN));

            title.setText("Новый плейлист");
            title.setTextColor(0xFF1DB954);
            subtitle.setText("Создать");
            subtitle.setTextColor(0x881DB954);
        } else {
            String seed = (titleText != null ? titleText : "") + (subtitleText != null ? subtitleText : "");

            // Paint the placeholder immediately - the real cover (if any) decodes off the
            // main thread and swaps in once ready, see PrimeAlbumArt's own doc for why a
            // synchronous decode per card here was the "opening the player lags" bug.
            artView.setImageDrawable(null);
            coverFrame.setBackground(createProceduralCoverDrawable(seed, CARD_RADIUS_DP));
            iconGlowDisc.setVisibility(VISIBLE);
            GradientDrawable discBg = new GradientDrawable();
            discBg.setShape(GradientDrawable.OVAL);
            discBg.setColor(0x33000000);
            discBg.setStroke(dp(1), 0x33FFFFFF);
            iconGlowDisc.setBackground(discBg);
            coverIcon.setImageResource(R.drawable.msg_filled_data_music);
            coverIcon.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
            artView.setTag(coverPath);

            if (coverPath != null) {
                org.telegram.messenger.music.PrimeAlbumArt.decodeAsync(coverPath, dp(CARD_SIZE_DP), bitmap -> {
                    if (bitmap == null || !coverPath.equals(artView.getTag())) {
                        return;
                    }
                    coverFrame.setBackground(Theme.createRoundRectDrawable(dp(CARD_RADIUS_DP), 0xFF000000));
                    artView.setImageDrawable(new android.graphics.drawable.BitmapDrawable(getResources(), bitmap));
                    iconGlowDisc.setVisibility(GONE);
                });
            }

            title.setText(titleText != null ? titleText : "");
            title.setTextColor(0xFFFFFFFF);
            subtitle.setText(subtitleText != null ? subtitleText : "");
            subtitle.setTextColor(0xFF8E8E93);
        }
    }
}
