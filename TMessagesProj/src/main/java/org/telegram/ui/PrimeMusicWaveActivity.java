package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.music.PrimeMusicLibrary;
import org.telegram.messenger.music.PrimeMusicPlayback;
import org.telegram.messenger.music.PrimeMusicWave;
import org.telegram.messenger.music.PrimeTrack;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PrimeDislikeIconView;
import org.telegram.ui.Components.PrimeWaveBackgroundView;

/**
 * PrimeGram Music: "Моя волна" full-screen - the immersive presentation the small entry
 * card on Home hands off to once the wave is actually running, matching the scale of a
 * real reference screenshot the user supplied (an animated full-bleed background, huge
 * artist name, small cover, a bottom control pill) rather than the tiny static card that
 * used to be the whole experience.
 */
public class PrimeMusicWaveActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private TextView artistLabel;
    private TextView trackLabel;
    private FrameLayout coverFrame;
    private ImageView coverIcon;
    private ImageView playPauseIcon;
    private ImageView likeIcon;

    @Override
    public View createView(Context context) {
        hasOwnBackground = true;

        FrameLayout root = new FrameLayout(context);

        PrimeWaveBackgroundView background = new PrimeWaveBackgroundView(context);
        root.addView(background, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        ImageView back = new ImageView(context);
        back.setImageResource(R.drawable.ic_ab_back);
        back.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
        back.setBackground(Theme.createSelectorDrawable(0x33FFFFFF, Theme.RIPPLE_MASK_CIRCLE_20DP));
        back.setClickable(true);
        back.setOnClickListener(v -> finishFragment());
        root.addView(back, LayoutHelper.createFrame(44, 44, Gravity.TOP | Gravity.LEFT, 8, 24, 0, 0));

        artistLabel = new TextView(context);
        artistLabel.setTextColor(0xFFFFFFFF);
        artistLabel.setTypeface(AndroidUtilities.bold());
        artistLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 40);
        artistLabel.setMaxLines(2);
        artistLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        root.addView(artistLabel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.TOP | Gravity.LEFT, 24, 96, 24, 0));

        coverFrame = new FrameLayout(context);
        GradientDrawable coverBg = new GradientDrawable();
        coverBg.setColor(0x33000000);
        coverBg.setCornerRadius(dp(14));
        coverFrame.setBackground(coverBg);
        coverFrame.setClipToOutline(true);
        root.addView(coverFrame, LayoutHelper.createFrame(140, 140, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 320, 0, 0));
        coverIcon = new ImageView(context);
        coverIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        coverFrame.addView(coverIcon, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // ---- Bottom control pill: play/pause | track name | like ----
        FrameLayout pill = new FrameLayout(context);
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(0x66000000);
        pillBg.setCornerRadius(dp(30));
        pill.setBackground(pillBg);
        root.addView(pill, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 58, Gravity.BOTTOM | Gravity.LEFT, 20, 0, 20, 96));

        FrameLayout playPauseCircle = new FrameLayout(context);
        playPauseCircle.setClickable(true);
        playPauseCircle.setBackground(Theme.createSelectorDrawable(0x22FFFFFF, Theme.RIPPLE_MASK_CIRCLE_20DP));
        playPauseIcon = new ImageView(context);
        playPauseIcon.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
        playPauseCircle.addView(playPauseIcon, LayoutHelper.createFrame(22, 22, Gravity.CENTER));
        playPauseCircle.setOnClickListener(v -> togglePlayPause());
        pill.addView(playPauseCircle, LayoutHelper.createFrame(58, 58, Gravity.CENTER_VERTICAL | Gravity.LEFT));

        trackLabel = new TextView(context);
        trackLabel.setTextColor(0xFFFFFFFF);
        trackLabel.setTypeface(AndroidUtilities.bold());
        trackLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        trackLabel.setGravity(Gravity.CENTER);
        trackLabel.setMaxLines(1);
        trackLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        pill.addView(trackLabel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL, 66, 0, 66, 0));

        likeIcon = new ImageView(context);
        likeIcon.setClickable(true);
        likeIcon.setBackground(Theme.createSelectorDrawable(0x22FFFFFF, Theme.RIPPLE_MASK_CIRCLE_20DP));
        likeIcon.setOnClickListener(v -> toggleLike());
        pill.addView(likeIcon, LayoutHelper.createFrame(58, 58, Gravity.CENTER_VERTICAL | Gravity.RIGHT));

        // A separate small pill below the main one, not crammed inside it - the
        // reference the user supplied only shows like on the main bar; dislike is a
        // real, explicitly requested action but deserves its own uncluttered spot
        // rather than fighting the main pill for space.
        FrameLayout dislikeCircle = new FrameLayout(context);
        GradientDrawable dislikeBg = new GradientDrawable();
        dislikeBg.setColor(0x55000000);
        dislikeBg.setShape(GradientDrawable.OVAL);
        dislikeCircle.setBackground(dislikeBg);
        dislikeCircle.setClickable(true);
        PrimeDislikeIconView dislikeGlyph = new PrimeDislikeIconView(context);
        dislikeCircle.addView(dislikeGlyph, LayoutHelper.createFrame(24, 24, Gravity.CENTER));
        dislikeCircle.setOnClickListener(v -> dislikeCurrent());
        root.addView(dislikeCircle, LayoutHelper.createFrame(44, 44, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 20, 34));

        fragmentView = root;
        refreshFromCurrent();
        return root;
    }

    private void togglePlayPause() {
        if (MediaController.getInstance().isMessagePaused()) {
            MediaController.getInstance().playMessage(MediaController.getInstance().getPlayingMessageObject());
        } else {
            MediaController.getInstance().pauseMessage(MediaController.getInstance().getPlayingMessageObject());
        }
        refreshFromCurrent();
    }

    private void toggleLike() {
        PrimeTrack track = PrimeMusicWave.current();
        if (track == null) {
            return;
        }
        PrimeMusicLibrary.setLiked(track.id, !track.liked);
        track.liked = !track.liked;
        refreshFromCurrent();
    }

    private void dislikeCurrent() {
        PrimeTrack next = PrimeMusicWave.dislikeCurrentAndAdvance();
        if (next != null) {
            PrimeMusicPlayback.playSingle(UserConfig.selectedAccount, next);
        }
        refreshFromCurrent();
    }

    private void refreshFromCurrent() {
        PrimeTrack track = PrimeMusicWave.current();
        if (track == null) {
            artistLabel.setText("Волна закончилась");
            trackLabel.setText("");
            return;
        }
        artistLabel.setText(org.telegram.messenger.music.PrimeMusicArtistLabel.isUnknown(track.artist) ? (track.title != null ? track.title : "") : track.artist);
        trackLabel.setText(track.title != null ? track.title : "");
        String seed = (track.title != null ? track.title : "") + "_" + (track.artist != null ? track.artist : "");
        org.telegram.messenger.music.PrimeAlbumArt.applyCover(coverFrame, coverIcon, track.coverPath, seed, 14);

        boolean paused = MediaController.getInstance().isMessagePaused();
        playPauseIcon.setImageResource(paused ? R.drawable.ic_action_play : R.drawable.ic_action_pause);

        likeIcon.setImageResource(track.liked ? R.drawable.media_like_active : R.drawable.media_like);
        likeIcon.setColorFilter(new PorterDuffColorFilter(track.liked ? 0xFFFF3B5C : 0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
    }

    @Override
    public boolean onFragmentCreate() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.messagePlayingDidStart);
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        }
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.messagePlayingDidStart);
            NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        }
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        refreshFromCurrent();
    }

    @Override
    public boolean isLightStatusBar() {
        return false;
    }
}
