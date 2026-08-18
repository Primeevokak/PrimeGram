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
        setPadding(dp(16), dp(8), dp(12), dp(8));

        coverFrame = new FrameLayout(context);
        addView(coverFrame, LayoutHelper.createFrame(48, 48, Gravity.CENTER_VERTICAL | Gravity.LEFT));

        coverIcon = new ImageView(context);
        coverIcon.setScaleType(ImageView.ScaleType.CENTER);
        coverIcon.setImageResource(R.drawable.msg_filled_data_music);
        coverIcon.setColorFilter(new PorterDuffColorFilter(0xEEFFFFFF, PorterDuff.Mode.SRC_IN));
        coverFrame.addView(coverIcon, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(Gravity.CENTER_VERTICAL);
        addView(texts, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL | Gravity.LEFT, 64, 0, 128, 0));

        title = new TextView(context);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(title);

        artist = new TextView(context);
        artist.setTextColor(0xFF8E8E93);
        artist.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        artist.setMaxLines(1);
        artist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(artist, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        duration = new TextView(context);
        duration.setTextColor(0xFF6E6E77);
        duration.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
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
        setBackground(Theme.createSelectorDrawable(0x18FFFFFF, Theme.RIPPLE_MASK_ALL));
        setMinimumHeight(dp(64));
    }

    public void bind(PrimeTrack track) {
        String trackTitle = track.title != null && !track.title.isEmpty() ? track.title : "Без названия";
        String trackArtist = track.artist != null ? track.artist : "";

        title.setText(trackTitle);
        artist.setText(trackArtist);
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
        moreIcon.setOnClickListener(v -> PrimeMusicTrackMenu.show(activity, moreIcon, track, resourcesProvider, () -> {
            if (onChanged != null) {
                onChanged.run();
            }
        }));
    }
}
