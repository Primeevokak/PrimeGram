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
import org.telegram.messenger.music.PrimeTrack;
import org.telegram.ui.ActionBar.Theme;

/**
 * PrimeGram Music: one track row - procedural gradient cover tile, crisp title/artist,
 * active red-pink heart icon, and standard "..." options menu.
 */
public class PrimeMusicTrackRow extends FrameLayout {

    private final FrameLayout coverFrame;
    private final ImageView coverIcon;
    private final TextView title;
    private final TextView artist;
    private final TextView duration;
    private final ImageView likeIcon;
    private final ImageView moreIcon;
    private final Theme.ResourcesProvider resourcesProvider;

    public PrimeMusicTrackRow(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        int thumb = org.telegram.messenger.music.PrimeMusicTheme.TILE_THUMBNAIL_DP;
        setPadding(dp(16), dp(8), dp(12), dp(8));

        coverFrame = new FrameLayout(context);
        addView(coverFrame, LayoutHelper.createFrame(thumb, thumb, Gravity.CENTER_VERTICAL | Gravity.LEFT));

        coverIcon = new ImageView(context);
        coverIcon.setScaleType(ImageView.ScaleType.CENTER);
        coverIcon.setImageResource(R.drawable.msg_filled_data_music);
        coverIcon.setColorFilter(new PorterDuffColorFilter(0xEEFFFFFF, PorterDuff.Mode.SRC_IN));
        coverFrame.addView(coverIcon, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(Gravity.CENTER_VERTICAL);
        addView(texts, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL | Gravity.LEFT, thumb + 16, 0, 128, 0));

        // Text sizes/colors here are namida's real type scale (themes.dart:243-260) -
        // displayMedium (15sp/weight 600) for the title, displaySmall (13sp/weight 400)
        // for artist/duration, and "white" text that's never actually pure white.
        title = new TextView(context);
        title.setTextColor(org.telegram.messenger.music.PrimeMusicTheme.TEXT_PRIMARY);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, org.telegram.messenger.music.PrimeMusicTheme.SP_MEDIUM);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(title);

        artist = new TextView(context);
        artist.setTextColor(org.telegram.messenger.music.PrimeMusicTheme.TEXT_TERTIARY);
        artist.setTextSize(TypedValue.COMPLEX_UNIT_DIP, org.telegram.messenger.music.PrimeMusicTheme.SP_SMALL);
        artist.setMaxLines(1);
        artist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(artist, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        duration = new TextView(context);
        duration.setTextColor(org.telegram.messenger.music.PrimeMusicTheme.TEXT_TERTIARY);
        duration.setTextSize(TypedValue.COMPLEX_UNIT_DIP, org.telegram.messenger.music.PrimeMusicTheme.SP_SMALL);
        duration.setGravity(Gravity.RIGHT);
        addView(duration, LayoutHelper.createFrame(40, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 86, 0));

        likeIcon = new ImageView(context);
        likeIcon.setScaleType(ImageView.ScaleType.CENTER);
        likeIcon.setClickable(true);
        likeIcon.setBackground(Theme.createSelectorDrawable(0x20FFFFFF, Theme.RIPPLE_MASK_CIRCLE_20DP));
        addView(likeIcon, LayoutHelper.createFrame(40, 40, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 42, 0));

        moreIcon = new ImageView(context);
        moreIcon.setScaleType(ImageView.ScaleType.CENTER);
        moreIcon.setImageResource(R.drawable.ic_ab_other);
        moreIcon.setColorFilter(new PorterDuffColorFilter(0xFF8E8E93, PorterDuff.Mode.SRC_IN));
        moreIcon.setClickable(true);
        moreIcon.setBackground(Theme.createSelectorDrawable(0x20FFFFFF, Theme.RIPPLE_MASK_CIRCLE_20DP));
        addView(moreIcon, LayoutHelper.createFrame(40, 40, Gravity.CENTER_VERTICAL | Gravity.RIGHT));

        setClickable(true);
        setLongClickable(true);
        setMinimumHeight(dp(org.telegram.messenger.music.PrimeMusicTheme.TILE_CONTENT_HEIGHT_DP));
    }

    public void bind(PrimeTrack track) {
        String trackTitle = track.title != null && !track.title.isEmpty() ? track.title : "Без названия";
        String trackArtist = track.artist != null ? track.artist : "";

        title.setText(trackTitle);
        org.telegram.messenger.music.PrimeMusicArtistLabel.bind(artist, trackArtist);
        int seconds = Math.max(0, track.duration);
        duration.setText(String.format(java.util.Locale.US, "%d:%02d", seconds / 60, seconds % 60));

        String seed = trackTitle + "_" + trackArtist;
        org.telegram.messenger.music.PrimeAlbumArt.applyCover(coverFrame, coverIcon, track.coverPath, seed, 10);

        if (track.liked) {
            likeIcon.setImageResource(R.drawable.media_like_active);
            likeIcon.setColorFilter(new PorterDuffColorFilter(0xFFFF3B5C, PorterDuff.Mode.SRC_IN));
        } else {
            likeIcon.setImageResource(R.drawable.media_like);
            likeIcon.setColorFilter(new PorterDuffColorFilter(0x66FFFFFF, PorterDuff.Mode.SRC_IN));
        }

        // Currently-playing highlight - namida's track_tile.dart does the same
        // (isTrackCurrentlyPlaying -> colored background + accent title), tinted with
        // the live per-track accent (PrimeTrackColor) rather than a fixed color, so the
        // highlight actually matches namida's real "whole UI recolors per track"
        // behavior, not a static brand color.
        boolean playing = org.telegram.messenger.music.PrimeMusicPlayback.isPlayingThisTrack(track);
        if (playing) {
            int accent = org.telegram.messenger.music.PrimeMusicTheme.currentAccent;
            GradientDrawable fill = new GradientDrawable();
            fill.setColor((accent & 0x00FFFFFF) | 0x33000000);
            fill.setCornerRadius(dp(org.telegram.messenger.music.PrimeMusicTheme.CARD_RADIUS_DP));
            setBackground(fill);
            title.setTextColor(accent | 0xFF000000);
        } else {
            setBackground(null);
            title.setTextColor(org.telegram.messenger.music.PrimeMusicTheme.TEXT_PRIMARY);
        }
    }

    /** namida's real tile geometry has a {@code 4dp} gap between every row
     *  ({@code tileBottomMargin}, {@code dimensions.dart:125}) - rows read as visually
     *  separated, not a flush continuous list. Every screen that adds a
     *  {@code PrimeMusicTrackRow} to a vertical container should use this instead of a
     *  bare {@code LayoutHelper.createLinear(MATCH_PARENT, WRAP_CONTENT)}. */
    public static LinearLayout.LayoutParams createRowLayoutParams() {
        LinearLayout.LayoutParams lp = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        lp.bottomMargin = dp(org.telegram.messenger.music.PrimeMusicTheme.TILE_MARGIN_DP);
        return lp;
    }

    public ImageView getLikeIcon() {
        return likeIcon;
    }

    public ImageView getMoreIcon() {
        return moreIcon;
    }

    /** Wires the "..." icon to the real anchored track menu (see
     *  {@link PrimeMusicTrackMenu}) - the one-line convenience every list screen should
     *  call instead of repeating the boilerplate; screens with their own bespoke
     *  overflow behavior (e.g. remove-from-playlist) can keep using
     *  {@link #getMoreIcon()} directly instead. */
    public void bindDefaultMenu(android.app.Activity activity, PrimeTrack track, Runnable onChanged) {
        PrimeMusicTrackMenu.Callback callback = () -> {
            if (onChanged != null) {
                onChanged.run();
            }
        };
        moreIcon.setOnClickListener(v -> PrimeMusicTrackMenu.show(activity, moreIcon, track, resourcesProvider, callback));
        // Long-press the whole row opens the same menu (add to liked/add to playlist/
        // etc.) instead of the stock white press-highlight sitting fully spread for the
        // entire hold - the user explicitly asked for this to replace that visual.
        setOnLongClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            PrimeMusicTrackMenu.show(activity, this, track, resourcesProvider, callback);
            return true;
        });
    }
}
