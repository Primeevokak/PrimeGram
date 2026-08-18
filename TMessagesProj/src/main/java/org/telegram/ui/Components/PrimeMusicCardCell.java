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
import org.telegram.messenger.music.PrimeMusicTheme;
import org.telegram.ui.ActionBar.Theme;

/**
 * PrimeGram Music: a square-cover card for carousels, matching namida's real
 * {@code album_card.dart} composition - card color/elevation/radius from
 * {@link PrimeMusicTheme}, the same bottom-right quick-play badge every namida album
 * card has. namida itself has no synthetic-cover system for missing artwork (just a
 * plain icon on the card color) - tried faithfully once, the user asked for the
 * colorful per-track synthetic cover back instead of a flat square, so the fallback
 * here is a deliberate deviation from namida: a deterministic multi-stop gradient
 * keyed on the track/playlist's own name, so a track with no embedded art still reads
 * as "this track" rather than every missing-cover item looking identical.
 */
public class PrimeMusicCardCell extends LinearLayout {

    public static final int CARD_SIZE_DP = 132;
    public static final int CARD_RADIUS_DP = PrimeMusicTheme.CARD_RADIUS_DP;

    // Curated high-vibrancy gradients for the synthetic-cover fallback, picked by a
    // deterministic hash of the track/playlist's own seed string - restored at the
    // user's explicit request after a faithful-to-namida flat placeholder read as an
    // empty square rather than a real "cover."
    private static final int[][] GRADIENT_PALETTES = new int[][]{
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
    private final ImageView playBadge;
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

        // Bottom-right quick-play badge - namida's own AlbumCard puts one on every
        // artwork tile (a circular translucent button that plays the item directly,
        // distinct from tapping the card itself which opens it). Hidden unless a
        // caller wires a click handler via bind()'s onPlayBadgeClick.
        playBadge = new ImageView(context);
        playBadge.setImageResource(R.drawable.ic_action_play);
        playBadge.setScaleType(ImageView.ScaleType.CENTER);
        playBadge.setPadding(dp(4), dp(4), dp(4), dp(4));
        playBadge.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
        playBadge.setBackground(Theme.createCircleDrawable(dp(26), 0x99000000));
        playBadge.setClickable(true);
        playBadge.setVisibility(GONE);
        coverFrame.addView(playBadge, LayoutHelper.createFrame(26, 26, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 6, 6));

        title = new TextView(context);
        title.setTextColor(PrimeMusicTheme.TEXT_PRIMARY);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, PrimeMusicTheme.SP_MEDIUM);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        addView(title, LayoutHelper.createLinear(CARD_SIZE_DP, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));

        subtitle = new TextView(context);
        subtitle.setTextColor(PrimeMusicTheme.TEXT_TERTIARY);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, PrimeMusicTheme.SP_SMALL);
        subtitle.setMaxLines(1);
        subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        addView(subtitle, LayoutHelper.createLinear(CARD_SIZE_DP, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        setClickable(true);
        setLongClickable(true);
        // namida's real card (themes.dart:33-42) is one elevated surface behind both
        // the artwork and the text block below it, not just a ripple over nothing -
        // artwork sits flush against the card edges (namida's own composition), only
        // the text below gets horizontal breathing room (already applied to
        // title/subtitle above via their own left/right layout, not this padding).
        //
        // No ripple/press-highlight layer here on purpose - a long press now opens a
        // real action menu (see HomeView's long-click wiring), and the stock ripple
        // used to sit fully spread for the entire hold since nothing consumed the
        // long-press before, reading as a stuck white outline - the user explicitly
        // asked for that to go.
        //
        // The 14dp on the right is this cell's own inter-card gap (setPadding above),
        // not part of the visible card - a background/outline drawn to the View's full
        // bounds (the normal Android behavior, ignoring padding) would paint straight
        // across that gap and make adjacent cards read as touching. Insetting the
        // background/outline by that same 14dp keeps the fill exactly where the
        // artwork+text actually are.
        GradientDrawable cardFill = new GradientDrawable();
        cardFill.setColor(PrimeMusicTheme.card(PrimeMusicTheme.currentAccent));
        cardFill.setCornerRadius(dp(CARD_RADIUS_DP));
        setBackground(new android.graphics.drawable.InsetDrawable(cardFill, 0, 0, dp(14), 0));
        setElevation(dp(3));
        setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(android.view.View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth() - dp(14), view.getHeight(), dp(CARD_RADIUS_DP));
            }
        });
    }

    /** A deterministic synthetic cover for a track/playlist with no real artwork -
     *  same palette on repeat views of the same item (hashed from {@code seed}, not
     *  random), so it reads as a stable "identity" rather than a placeholder. */
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
        bind(titleText, subtitleText, null, null);
    }

    public void bind(String titleText, String subtitleText, String coverPath) {
        bind(titleText, subtitleText, coverPath, null);
    }

    public void bind(String titleText, String subtitleText, String coverPath, Runnable onPlayBadgeClick) {
        boolean isAddAction = "+ Новый плейлист".equals(titleText);

        if (onPlayBadgeClick != null) {
            playBadge.setVisibility(VISIBLE);
            playBadge.setOnClickListener(v -> onPlayBadgeClick.run());
        } else {
            playBadge.setVisibility(GONE);
            playBadge.setOnClickListener(null);
        }

        int accent = PrimeMusicTheme.currentAccent;

        // Re-tint the card surface for the live accent - it may have changed since
        // this cell was constructed (a different track is now playing). Inset by the
        // same 14dp inter-card gap the constructor's own background uses - see its
        // doc for why the fill can't just paint to the View's full bounds.
        GradientDrawable cardFill = new GradientDrawable();
        cardFill.setColor(PrimeMusicTheme.card(accent));
        cardFill.setCornerRadius(dp(CARD_RADIUS_DP));
        setBackground(new android.graphics.drawable.InsetDrawable(cardFill, 0, 0, dp(14), 0));

        if (isAddAction) {
            // Distinct sleek outlined/dashed card for "Add Playlist"
            artView.setImageDrawable(null);
            GradientDrawable outline = new GradientDrawable();
            outline.setColor(0x18FFFFFF);
            outline.setCornerRadius(dp(CARD_RADIUS_DP));
            outline.setStroke(dp(1.5f), 0x4DFFFFFF, dp(4), dp(3));
            coverFrame.setBackground(outline);

            iconGlowDisc.setVisibility(VISIBLE);
            iconGlowDisc.setBackground(Theme.createCircleDrawable(dp(44), (accent & 0x00FFFFFF) | 0x22000000));
            coverIcon.setImageResource(R.drawable.msg_add);
            coverIcon.setColorFilter(new PorterDuffColorFilter(accent | 0xFF000000, PorterDuff.Mode.SRC_IN));

            title.setText("Новый плейлист");
            title.setTextColor(accent | 0xFF000000);
            subtitle.setText("");
        } else {
            // Must match the "title_artist" seed convention every other screen builds
            // (PrimeMusicTrackRow, PrimeMusicNowPlayingActivity, PrimeMusicHomeView's
            // mini-player) - this one used to concatenate without the separator, so
            // the exact same track hashed to a different palette entry depending on
            // which screen rendered its placeholder cover.
            String seed = (titleText != null ? titleText : "") + "_" + (subtitleText != null ? subtitleText : "");

            // Paint the placeholder immediately - the real cover (if any) decodes off the
            // main thread and swaps in once ready, see PrimeAlbumArt's own doc for why a
            // synchronous decode per card here was the "opening the player lags" bug.
            // The placeholder itself is plain (card-colored tile + note icon) - namida
            // has no per-item gradient/color-variety system for missing artwork.
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
            title.setTextColor(PrimeMusicTheme.TEXT_PRIMARY);
            org.telegram.messenger.music.PrimeMusicArtistLabel.bind(subtitle, subtitleText);
            subtitle.setTextColor(PrimeMusicTheme.TEXT_TERTIARY);
        }
    }
}
