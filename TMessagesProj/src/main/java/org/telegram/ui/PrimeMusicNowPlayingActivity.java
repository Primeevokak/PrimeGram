package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.music.PrimeLyricsFetcher;
import org.telegram.messenger.music.PrimeMusicLibrary;
import org.telegram.messenger.music.PrimeMusicPlayback;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PrimeMusicCardCell;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * PrimeGram Music: full "Now Playing" screen matching the premium Spotify/Apple Music aesthetic.
 * Large procedural gradient artwork, rounded card lyrics/queue panel, glowing controls,
 * and smooth swipe-down-to-dismiss gesture.
 */
public class PrimeMusicNowPlayingActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    // Real namida colors (PrimeMusicTheme) - see its own doc for where every value
    // traces back to in namida's actual source, replacing the invented near-black/
    // neon palette this screen used to hardcode.
    public static final int COLOR_TEXT_PRIMARY = org.telegram.messenger.music.PrimeMusicTheme.TEXT_PRIMARY;
    public static final int COLOR_TEXT_SECONDARY = org.telegram.messenger.music.PrimeMusicTheme.TEXT_TERTIARY;

    private FrameLayout root;
    private View scrim;
    private ImageView blurBackgroundView;
    private FrameLayout coverFrame;
    private ImageView artView;
    private FrameLayout coverGlassDisc;
    private ImageView coverView;
    private long lastCoverTrackId = Long.MIN_VALUE;
    private TextView titleView;
    private TextView artistView;
    private SeekBar seekBar;
    private TextView elapsedView;
    private TextView totalView;
    private ImageView likeButton;
    private ImageView shuffleButton;
    private ImageView repeatButton;
    private ImageView playPauseButton;
    private TextView lyricsToggle;
    private LinearLayout queueContainer;
    private TextView lyricsText;
    private ScrollView lyricsScroll;
    private FrameLayout panelContainer;

    private boolean showingLyrics;
    private boolean userIsSeeking;

    private float dragStartY;
    private boolean dragging;

    private android.os.CountDownTimer sleepTimer;
    private boolean sleepStopAtTrackEndArmed;
    private long sleepStopTargetMessageId;
    private int lastSleepTimerMinutes = 30;

    @Override
    public boolean hasForceLightStatusBar() {
        return false;
    }

    @Override
    public View createView(Context context) {
        actionBar.setAddToContainer(false);

        root = new FrameLayout(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                return handleTouch(ev) || super.onInterceptTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent ev) {
                handleTouch(ev);
                return true;
            }
        };
        root.setBackgroundColor(org.telegram.messenger.music.PrimeMusicTheme.bg(org.telegram.messenger.music.PrimeMusicTheme.currentAccent));

        // Full-bleed blurred cover behind everything - namida's single biggest visual
        // signature for this screen. Hidden until a real cover resolves (see
        // applyCoverForTrack); a dark scrim keeps text legible on top of it.
        blurBackgroundView = new ImageView(context);
        blurBackgroundView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        blurBackgroundView.setVisibility(View.GONE);
        root.addView(blurBackgroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        scrim = new View(context);
        scrim.setBackgroundColor(androidx.core.graphics.ColorUtils.setAlphaComponent(
                org.telegram.messenger.music.PrimeMusicTheme.bg(org.telegram.messenger.music.PrimeMusicTheme.currentAccent), 0xCC));
        root.addView(scrim, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        ScrollView scroll = new ScrollView(context);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        root.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(12), dp(22), dp(36));
        scroll.addView(content, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        // ---- Top bar ----
        LinearLayout topBar = new LinearLayout(context);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        ImageView collapse = new ImageView(context);
        collapse.setImageResource(R.drawable.ic_ab_back);
        collapse.setRotation(-90);
        collapse.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        collapse.setPadding(dp(8), dp(8), dp(8), dp(8));
        collapse.setColorFilter(new PorterDuffColorFilter(COLOR_TEXT_PRIMARY, PorterDuff.Mode.SRC_IN));
        collapse.setClickable(true);
        collapse.setBackground(Theme.createSelectorDrawable(0x22FFFFFF, Theme.RIPPLE_MASK_CIRCLE_20DP));
        collapse.setOnClickListener(v -> finishFragment());
        topBar.addView(collapse, LayoutHelper.createLinear(40, 40));

        TextView topTitle = new TextView(context);
        topTitle.setText("СЕЙЧАС ИГРАЕТ");
        topTitle.setTextColor(COLOR_TEXT_SECONDARY);
        topTitle.setTypeface(AndroidUtilities.bold());
        topTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        topTitle.setGravity(Gravity.CENTER);
        topBar.addView(topTitle, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        ImageView sleepTimerButton = new ImageView(context);
        sleepTimerButton.setImageResource(R.drawable.msg_mute);
        sleepTimerButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        sleepTimerButton.setPadding(dp(8), dp(8), dp(8), dp(8));
        sleepTimerButton.setColorFilter(new PorterDuffColorFilter(COLOR_TEXT_SECONDARY, PorterDuff.Mode.SRC_IN));
        sleepTimerButton.setClickable(true);
        sleepTimerButton.setBackground(Theme.createSelectorDrawable(0x22FFFFFF, Theme.RIPPLE_MASK_CIRCLE_20DP));
        sleepTimerButton.setOnClickListener(v -> showSleepTimerMenu());
        topBar.addView(sleepTimerButton, LayoutHelper.createLinear(40, 40));

        content.addView(topBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // ---- Cover: real embedded artwork when available, procedural gradient +
        //      glassmorphic disc placeholder otherwise (see applyCoverForTrack) ----
        coverFrame = new FrameLayout(context);
        coverFrame.setBackground(Theme.createRoundRectDrawable(dp(24),
                org.telegram.messenger.music.PrimeMusicTheme.card(org.telegram.messenger.music.PrimeMusicTheme.currentAccent)));
        coverFrame.setOutlineProvider(org.telegram.messenger.utils.ViewOutlineProviderImpl.boundsWithPaddingRoundRect(0, dp(24)));
        coverFrame.setClipToOutline(true);
        content.addView(coverFrame, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 320, 0, 16, 0, 0));

        artView = new ImageView(context);
        artView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        coverFrame.addView(artView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        coverGlassDisc = new FrameLayout(context);
        GradientDrawable glassDisc = new GradientDrawable();
        glassDisc.setShape(GradientDrawable.OVAL);
        glassDisc.setColor(0x2D000000);
        glassDisc.setStroke(dp(1.5f), 0x33FFFFFF);
        coverGlassDisc.setBackground(glassDisc);
        coverFrame.addView(coverGlassDisc, LayoutHelper.createFrame(96, 96, Gravity.CENTER));

        coverView = new ImageView(context);
        coverView.setScaleType(ImageView.ScaleType.CENTER);
        coverView.setImageResource(R.drawable.msg_filled_data_music);
        coverView.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
        coverGlassDisc.addView(coverView, LayoutHelper.createFrame(48, 48, Gravity.CENTER));

        // ---- Title and Artist ----
        titleView = new TextView(context);
        titleView.setTextColor(COLOR_TEXT_PRIMARY);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        titleView.setMaxLines(1);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        content.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 20, 0, 0));

        artistView = new TextView(context);
        artistView.setTextColor(COLOR_TEXT_SECONDARY);
        artistView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        artistView.setMaxLines(1);
        artistView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        content.addView(artistView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

        // ---- Modern Seek bar ----
        seekBar = new SeekBar(context);
        seekBar.setMax(1000);
        content.addView(seekBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 14, 0, 0));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    elapsedView.setText(formatTime((float) (progress / 1000f * currentDuration())));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                userIsSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                userIsSeeking = false;
                MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
                if (playing != null) {
                    MediaController.getInstance().seekToProgress(playing, seekBar.getProgress() / 1000f);
                }
            }
        });

        LinearLayout timeRow = new LinearLayout(context);
        elapsedView = new TextView(context);
        elapsedView.setTextColor(0xFF6E6E77);
        elapsedView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        timeRow.addView(elapsedView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        totalView = new TextView(context);
        totalView.setTextColor(0xFF6E6E77);
        totalView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        timeRow.addView(totalView);
        content.addView(timeRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        // ---- Primary Controls (Shuffle, Prev, Play/Pause, Next, Repeat) ----
        LinearLayout controls = new LinearLayout(context);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(controls, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 16, 0, 0));

        shuffleButton = new ImageView(context);
        shuffleButton.setClickable(true);
        shuffleButton.setBackground(Theme.createSelectorDrawable(0x22FFFFFF, Theme.RIPPLE_MASK_CIRCLE_20DP));
        shuffleButton.setOnClickListener(v -> {
            SharedConfig.setPlaybackOrderType(SharedConfig.shuffleMusic ? 0 : 2);
            updateShuffleRepeatIcons();
        });
        controls.addView(shuffleButton, LayoutHelper.createLinear(40, 40));

        View spacerLeft = new View(context);
        controls.addView(spacerLeft, LayoutHelper.createLinear(0, 0, 1f));

        ImageView prevButton = new ImageView(context);
        prevButton.setImageResource(R.drawable.ic_action_previous);
        prevButton.setColorFilter(new PorterDuffColorFilter(COLOR_TEXT_PRIMARY, PorterDuff.Mode.SRC_IN));
        prevButton.setClickable(true);
        prevButton.setBackground(Theme.createSelectorDrawable(0x22FFFFFF, Theme.RIPPLE_MASK_CIRCLE_20DP));
        prevButton.setOnClickListener(v -> MediaController.getInstance().playPreviousMessage());
        controls.addView(prevButton, LayoutHelper.createLinear(46, 46));

        // Large high-contrast Play/Pause circle button
        FrameLayout playPauseCircle = new FrameLayout(context);
        playPauseCircle.setBackground(Theme.createCircleDrawable(dp(62), 0xFFFFFFFF));
        playPauseCircle.setClickable(true);
        playPauseButton = new ImageView(context);
        playPauseButton.setImageResource(R.drawable.ic_action_play);
        playPauseButton.setColorFilter(new PorterDuffColorFilter(0xFF0B0B0F, PorterDuff.Mode.SRC_IN));
        playPauseCircle.addView(playPauseButton, LayoutHelper.createFrame(28, 28, Gravity.CENTER));
        playPauseCircle.setOnClickListener(v -> togglePlayPause());
        controls.addView(playPauseCircle, LayoutHelper.createLinear(62, 62, 20, 0, 20, 0));

        ImageView nextButton = new ImageView(context);
        nextButton.setImageResource(R.drawable.ic_action_next);
        nextButton.setColorFilter(new PorterDuffColorFilter(COLOR_TEXT_PRIMARY, PorterDuff.Mode.SRC_IN));
        nextButton.setClickable(true);
        nextButton.setBackground(Theme.createSelectorDrawable(0x22FFFFFF, Theme.RIPPLE_MASK_CIRCLE_20DP));
        nextButton.setOnClickListener(v -> MediaController.getInstance().playNextMessage());
        controls.addView(nextButton, LayoutHelper.createLinear(46, 46));

        View spacerRight = new View(context);
        controls.addView(spacerRight, LayoutHelper.createLinear(0, 0, 1f));

        repeatButton = new ImageView(context);
        repeatButton.setClickable(true);
        repeatButton.setBackground(Theme.createSelectorDrawable(0x22FFFFFF, Theme.RIPPLE_MASK_CIRCLE_20DP));
        repeatButton.setOnClickListener(v -> {
            SharedConfig.setRepeatMode((SharedConfig.repeatMode + 1) % 3);
            updateShuffleRepeatIcons();
        });
        controls.addView(repeatButton, LayoutHelper.createLinear(40, 40));

        // ---- Secondary Action Pills (Lyrics/Queue toggle, Tags, Like) ----
        LinearLayout actionsRow = new LinearLayout(context);
        actionsRow.setGravity(Gravity.CENTER_VERTICAL);

        lyricsToggle = new TextView(context);
        lyricsToggle.setText("♪ Текст песни");
        lyricsToggle.setTextColor(COLOR_TEXT_PRIMARY);
        lyricsToggle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        lyricsToggle.setTypeface(AndroidUtilities.bold());
        GradientDrawable pillBg1 = new GradientDrawable();
        pillBg1.setColor(0x22FFFFFF);
        pillBg1.setCornerRadius(dp(16));
        lyricsToggle.setBackground(pillBg1);
        lyricsToggle.setPadding(dp(14), dp(8), dp(14), dp(8));
        lyricsToggle.setClickable(true);
        lyricsToggle.setOnClickListener(v -> {
            showingLyrics = !showingLyrics;
            updateQueueOrLyricsVisibility();
        });
        actionsRow.addView(lyricsToggle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 10, 0));

        TextView tagsButton = new TextView(context);
        tagsButton.setText("# Теги");
        tagsButton.setTextColor(COLOR_TEXT_SECONDARY);
        tagsButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        GradientDrawable pillBg2 = new GradientDrawable();
        pillBg2.setColor(0x18FFFFFF);
        pillBg2.setCornerRadius(dp(16));
        tagsButton.setBackground(pillBg2);
        tagsButton.setPadding(dp(14), dp(8), dp(14), dp(8));
        tagsButton.setClickable(true);
        tagsButton.setOnClickListener(v -> showTagEditor());
        actionsRow.addView(tagsButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        View actionsSpacer = new View(context);
        actionsRow.addView(actionsSpacer, LayoutHelper.createLinear(0, 0, 1f));

        likeButton = new ImageView(context);
        likeButton.setClickable(true);
        likeButton.setBackground(Theme.createSelectorDrawable(0x22FFFFFF, Theme.RIPPLE_MASK_CIRCLE_20DP));
        likeButton.setOnClickListener(v -> toggleLike());
        actionsRow.addView(likeButton, LayoutHelper.createLinear(38, 38));

        // "Не нравится" - the like icon with a diagonal strikethrough, per the feature's
        // own spec ("лайк перечеркнуть просто"), not a separate thumbs-down glyph. Marks
        // the track disliked (PrimeMusicWave/getWaveRecommendations never offers it
        // again) and immediately skips it, same as a real dislike should behave - staying
        // on a track right after saying you don't want it makes no sense.
        FrameLayout dislikeButton = new FrameLayout(context);
        dislikeButton.setClickable(true);
        dislikeButton.setBackground(Theme.createSelectorDrawable(0x22FFFFFF, Theme.RIPPLE_MASK_CIRCLE_20DP));
        ImageView dislikeHeart = new ImageView(context);
        dislikeHeart.setImageResource(R.drawable.media_like);
        dislikeHeart.setColorFilter(new PorterDuffColorFilter(0x66FFFFFF, PorterDuff.Mode.SRC_IN));
        dislikeButton.addView(dislikeHeart, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        View strike = new View(context);
        strike.setBackgroundColor(0x99FFFFFF);
        strike.setRotation(-45f);
        dislikeButton.addView(strike, LayoutHelper.createFrame(26, 2, Gravity.CENTER));
        dislikeButton.setOnClickListener(v -> dislikeCurrentTrack());
        actionsRow.addView(dislikeButton, LayoutHelper.createLinear(38, 38, 0, 6, 0, 0));

        content.addView(actionsRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 24, 0, 0));

        // ---- Encased Card Container for Lyrics & Up Next Queue ----
        panelContainer = new FrameLayout(context);
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(org.telegram.messenger.music.PrimeMusicTheme.card(org.telegram.messenger.music.PrimeMusicTheme.currentAccent));
        panelBg.setCornerRadius(dp(16));
        panelBg.setStroke(dp(1), 0x1AFFFFFF);
        panelContainer.setBackground(panelBg);
        panelContainer.setPadding(dp(16), dp(16), dp(16), dp(16));
        content.addView(panelContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 18, 0, 0));

        // Queue content inside card
        LinearLayout queueBox = new LinearLayout(context);
        queueBox.setOrientation(LinearLayout.VERTICAL);
        panelContainer.addView(queueBox, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView upNextLabel = new TextView(context);
        upNextLabel.setText("ДАЛЕЕ В ОЧЕРЕДИ");
        upNextLabel.setTextColor(0xFF6E6E77);
        upNextLabel.setTypeface(AndroidUtilities.bold());
        upNextLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        queueBox.addView(upNextLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        queueContainer = new LinearLayout(context);
        queueContainer.setOrientation(LinearLayout.VERTICAL);
        queueBox.addView(queueContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Lyrics content inside card
        lyricsScroll = new ScrollView(context);
        lyricsScroll.setVisibility(View.GONE);
        lyricsText = new TextView(context);
        lyricsText.setTextColor(0xFFE0E0E8);
        lyricsText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        lyricsText.setLineSpacing(dp(8), 1f);
        lyricsScroll.addView(lyricsText, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        panelContainer.addView(lyricsScroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 260));

        fragmentView = root;
        updateQueueOrLyricsVisibility();
        refreshAll();
        return root;
    }

    private boolean handleTouch(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragStartY = ev.getRawY();
                dragging = false;
                return false;
            case MotionEvent.ACTION_MOVE:
                float dy = ev.getRawY() - dragStartY;
                if (dy > dp(24)) {
                    dragging = true;
                    root.setTranslationY(dy - dp(24));
                    return true;
                }
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    float finalDy = ev.getRawY() - dragStartY;
                    if (finalDy > dp(140)) {
                        finishFragment();
                    } else {
                        root.animate().translationY(0).setDuration(180).start();
                    }
                    dragging = false;
                    return true;
                }
                return false;
        }
        return false;
    }

    private void showSleepTimerMenu() {
        if (getParentActivity() == null) {
            return;
        }
        org.telegram.ui.Components.PrimeSleepTimerDialog.show(getParentActivity(), lastSleepTimerMinutes,
                sleepStopAtTrackEndArmed, (minutes, stopAtTrackEnd) -> {
            if (sleepTimer != null) {
                sleepTimer.cancel();
                sleepTimer = null;
            }
            sleepStopAtTrackEndArmed = false;
            if (stopAtTrackEnd) {
                MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
                sleepStopTargetMessageId = playing != null ? playing.getId() : 0;
                sleepStopAtTrackEndArmed = true;
                return;
            }
            if (minutes <= 0) {
                return;
            }
            lastSleepTimerMinutes = minutes;
            sleepTimer = new android.os.CountDownTimer(minutes * 60_000L, 60_000L) {
                @Override
                public void onTick(long millisUntilFinished) {
                }

                @Override
                public void onFinish() {
                    MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
                    if (playing != null) {
                        MediaController.getInstance().pauseMessage(playing);
                    }
                }
            }.start();
        });
    }

    private void showTagEditor() {
        MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
        if (playing == null || getParentActivity() == null) {
            return;
        }
        org.telegram.ui.Components.PrimeMusicTagEditor.show(getParentActivity(), resolveTrackId(playing));
    }

    private void toggleLike() {
        MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
        if (playing == null) {
            return;
        }
        long trackId = resolveTrackId(playing);
        boolean liked = !isTrackLiked(trackId);
        PrimeMusicLibrary.setLiked(trackId, liked);
        updateLikeIcon(liked);
    }

    private boolean isTrackLiked(long trackId) {
        org.telegram.messenger.music.PrimeTrack track = PrimeMusicLibrary.getTrack(trackId);
        return track != null && track.liked;
    }

    private void dislikeCurrentTrack() {
        MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
        if (playing == null) {
            return;
        }
        long trackId = resolveTrackId(playing);
        PrimeMusicLibrary.setDisliked(trackId, true);
        MediaController.getInstance().playNextMessage();
    }

    private long resolveTrackId(MessageObject playing) {
        Long localId = PrimeMusicPlayback.resolveLocalTrackId(playing);
        if (localId != null) {
            return localId;
        }
        return PrimeMusicLibrary.findOrAddTelegramTrack(playing.getDialogId(), playing.getId(),
                playing.getMusicTitle(), playing.getMusicAuthor(), (int) playing.getDuration());
    }

    private void updateLikeIcon(boolean liked) {
        if (liked) {
            likeButton.setImageResource(R.drawable.media_like_active);
            likeButton.setColorFilter(new PorterDuffColorFilter(0xFFFF3B5C, PorterDuff.Mode.SRC_IN));
        } else {
            likeButton.setImageResource(R.drawable.media_like);
            likeButton.setColorFilter(new PorterDuffColorFilter(0x66FFFFFF, PorterDuff.Mode.SRC_IN));
        }
    }

    private void updateQueueOrLyricsVisibility() {
        queueContainer.getParent().requestLayout();
        ((View) queueContainer.getParent()).setVisibility(showingLyrics ? View.GONE : View.VISIBLE);
        lyricsScroll.setVisibility(showingLyrics ? View.VISIBLE : View.GONE);
        lyricsToggle.setText(showingLyrics ? "☰ Очередь" : "♪ Текст песни");
        if (showingLyrics) {
            loadLyrics();
        } else {
            refreshQueue();
        }
    }

    private void loadLyrics() {
        MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
        if (playing == null) {
            lyricsText.setText("Ничего не играет");
            return;
        }
        long trackId = resolveTrackId(playing);
        org.telegram.messenger.music.PrimeTrack track = PrimeMusicLibrary.getTrack(trackId);
        if (track != null && track.lyrics != null) {
            lyricsText.setText(track.lyrics);
            return;
        }
        lyricsText.setText("Загрузка текста…");
        PrimeLyricsFetcher.fetch(playing.getMusicTitle(), playing.getMusicAuthor(), (int) playing.getDuration(), lyrics -> {
            AndroidUtilities.runOnUIThread(() -> {
                if (lyrics != null) {
                    PrimeMusicLibrary.setLyrics(trackId, lyrics);
                    if (showingLyrics) {
                        lyricsText.setText(lyrics);
                    }
                } else if (showingLyrics) {
                    lyricsText.setText("Текст песни не найден");
                }
            });
        });
    }

    private void refreshQueue() {
        queueContainer.removeAllViews();
        ArrayList<MessageObject> playlist = MediaController.getInstance().getPlaylist();
        MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
        int startIndex = playing != null ? playlist.indexOf(playing) : -1;
        int shown = 0;
        for (int i = startIndex + 1; i < playlist.size() && shown < 20; i++, shown++) {
            MessageObject obj = playlist.get(i);
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(8), 0, dp(8));
            row.setClickable(true);
            row.setBackground(Theme.createSelectorDrawable(0x18FFFFFF, Theme.RIPPLE_MASK_ALL));

            TextView indexTv = new TextView(getContext());
            indexTv.setText(String.valueOf(shown + 1));
            indexTv.setTextColor(0xFF6E6E77);
            indexTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            indexTv.setGravity(Gravity.CENTER);
            row.addView(indexTv, LayoutHelper.createLinear(24, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));

            LinearLayout texts = new LinearLayout(getContext());
            texts.setOrientation(LinearLayout.VERTICAL);
            row.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

            TextView t = new TextView(getContext());
            t.setText(obj.getMusicTitle() != null ? obj.getMusicTitle() : "Без названия");
            t.setTextColor(COLOR_TEXT_PRIMARY);
            t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            t.setTypeface(AndroidUtilities.bold());
            t.setMaxLines(1);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            texts.addView(t);

            TextView a = new TextView(getContext());
            a.setText(obj.getMusicAuthor() != null ? obj.getMusicAuthor() : "");
            a.setTextColor(COLOR_TEXT_SECONDARY);
            a.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            a.setMaxLines(1);
            a.setEllipsize(android.text.TextUtils.TruncateAt.END);
            texts.addView(a, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

            final int jumpIndex = i;
            row.setOnClickListener(v -> MediaController.getInstance().setPlaylist(playlist, playlist.get(jumpIndex), 0, false, null));
            queueContainer.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        if (shown == 0) {
            TextView empty = new TextView(getContext());
            empty.setText("Очередь пуста");
            empty.setTextColor(0xFF6E6E77);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            queueContainer.addView(empty, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 4));
        }
    }

    private void togglePlayPause() {
        MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
        if (playing == null) {
            return;
        }
        if (MediaController.getInstance().isMessagePaused()) {
            MediaController.getInstance().playMessage(playing);
        } else {
            MediaController.getInstance().pauseMessage(playing);
        }
        updatePlayPauseIcon();
    }

    private void updatePlayPauseIcon() {
        boolean paused = MediaController.getInstance().isMessagePaused();
        playPauseButton.setImageResource(paused ? R.drawable.ic_action_play : R.drawable.ic_action_pause);
    }

    private void updateShuffleRepeatIcons() {
        int accent = org.telegram.messenger.music.PrimeMusicTheme.currentAccent;
        shuffleButton.setImageResource(SharedConfig.shuffleMusic ? R.drawable.player_new_shuffle : R.drawable.player_new_shuffle_off);
        shuffleButton.setColorFilter(new PorterDuffColorFilter(SharedConfig.shuffleMusic ? accent : 0xFF6E6E77, PorterDuff.Mode.SRC_IN));
        int repeatIcon;
        switch (SharedConfig.repeatMode) {
            case 1:
                repeatIcon = R.drawable.player_new_repeatall;
                break;
            case 2:
                repeatIcon = R.drawable.player_new_repeatone;
                break;
            default:
                repeatIcon = R.drawable.player_new_repeat_off;
                break;
        }
        repeatButton.setImageResource(repeatIcon);
        repeatButton.setColorFilter(new PorterDuffColorFilter(SharedConfig.repeatMode != 0 ? accent : 0xFF6E6E77, PorterDuff.Mode.SRC_IN));
    }

    /** Resolves and applies the real cover (falls back to the procedural gradient) plus
     *  the full-screen blurred background, guarded by track identity so a track that's
     *  merely progressing (the common case - this runs off every progress tick via
     *  {@code refreshAll}) doesn't re-decode/re-blur a bitmap every second. Decoding and
     *  blurring both run off the main thread - {@code stackBlurBitmapMax} is cheap (it
     *  blurs a ~20dp copy internally) but the file decode isn't free. */
    private void applyCoverForTrack(long trackId, String seed) {
        if (trackId == lastCoverTrackId) {
            return;
        }
        lastCoverTrackId = trackId;
        org.telegram.messenger.music.PrimeTrack track = PrimeMusicLibrary.getTrack(trackId);
        String coverPath = track != null ? track.coverPath : null;
        org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
            android.graphics.Bitmap art = coverPath != null ? org.telegram.messenger.music.PrimeAlbumArt.decodeSampled(coverPath, dp(320)) : null;
            android.graphics.Bitmap blurred = art != null ? org.telegram.messenger.Utilities.stackBlurBitmapMax(art) : null;
            AndroidUtilities.runOnUIThread(() -> {
                if (getContext() == null || trackId != lastCoverTrackId) {
                    return;
                }
                if (art != null) {
                    artView.setImageDrawable(new android.graphics.drawable.BitmapDrawable(getContext().getResources(), art));
                    coverGlassDisc.setVisibility(View.GONE);
                } else {
                    artView.setImageDrawable(null);
                    coverGlassDisc.setVisibility(View.VISIBLE);
                    coverFrame.setBackground(PrimeMusicCardCell.createProceduralCoverDrawable(seed, 24));
                }
                if (blurred != null) {
                    blurBackgroundView.setImageDrawable(new android.graphics.drawable.BitmapDrawable(getContext().getResources(), blurred));
                    blurBackgroundView.setVisibility(View.VISIBLE);
                } else {
                    blurBackgroundView.setVisibility(View.GONE);
                }
            });
        });
    }

    private double currentDuration() {
        MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
        return playing != null ? playing.getDuration() : 0;
    }

    private String formatTime(float seconds) {
        int s = Math.max(0, (int) seconds);
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    private void refreshAll() {
        MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
        if (playing == null) {
            titleView.setText("Ничего не играет");
            artistView.setText("");
            return;
        }
        String title = playing.getMusicTitle();
        String artist = playing.getMusicAuthor();
        titleView.setText(title != null && !title.isEmpty() ? title : "Без названия");
        org.telegram.messenger.music.PrimeMusicArtistLabel.bind(artistView, artist);

        String seed = (title != null ? title : "") + "_" + (artist != null ? artist : "");
        long trackId = resolveTrackId(playing);
        applyCoverForTrack(trackId, seed);

        totalView.setText(formatTime((float) playing.getDuration()));
        updatePlayPauseIcon();
        updateShuffleRepeatIcons();
        updateLikeIcon(isTrackLiked(trackId));
        refreshQueue();
        if (!userIsSeeking) {
            seekBar.setProgress((int) (playing.audioProgress * 1000));
            elapsedView.setText(formatTime(playing.audioProgressSec));
        }
    }

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.messagePlayingDidStart);
        getNotificationCenter().addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        getNotificationCenter().addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        getNotificationCenter().addObserver(this, NotificationCenter.messagePlayingDidReset);
        org.telegram.messenger.music.PrimeMusicTheme.addAccentListener(accentListener);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        getNotificationCenter().removeObserver(this, NotificationCenter.messagePlayingDidStart);
        getNotificationCenter().removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        getNotificationCenter().removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        getNotificationCenter().removeObserver(this, NotificationCenter.messagePlayingDidReset);
        org.telegram.messenger.music.PrimeMusicTheme.removeAccentListener(accentListener);
        if (sleepTimer != null) {
            sleepTimer.cancel();
        }
        super.onFragmentDestroy();
    }

    /** See PrimeMusicHomeView's identical field for why this exists separately from
     *  the synchronous messagePlayingDidStart handler below. */
    private final Runnable accentListener = () -> {
        applyAccentTint();
        refreshAll();
    };

    /** Re-tints the chrome painted once at construction time with whatever the accent
     *  was then - the root/scrim background and the lyrics/queue panel card all need
     *  to follow the live per-track accent too (shuffle/repeat/like already read
     *  {@code PrimeMusicTheme.currentAccent} fresh on every {@code refreshAll()} call,
     *  no separate re-tint needed for those). */
    private void applyAccentTint() {
        int accent = org.telegram.messenger.music.PrimeMusicTheme.currentAccent;
        root.setBackgroundColor(org.telegram.messenger.music.PrimeMusicTheme.bg(accent));
        scrim.setBackgroundColor(androidx.core.graphics.ColorUtils.setAlphaComponent(
                org.telegram.messenger.music.PrimeMusicTheme.bg(accent), 0xCC));
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(org.telegram.messenger.music.PrimeMusicTheme.card(accent));
        panelBg.setCornerRadius(dp(16));
        panelBg.setStroke(dp(1), 0x1AFFFFFF);
        panelContainer.setBackground(panelBg);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        // messagePlayingProgressDidChanged fires many times a second during playback -
        // routing it through the full refreshAll() (which re-resolves the track id via
        // a synchronous DB write, re-triggers the cover decode+blur pipeline, and fully
        // rebuilds the up-next queue's views from scratch) turned every second of
        // playback into dozens of full screen rebuilds on the main thread, which is
        // exactly what read as "the player runs at 1.5 fps." Only the seek bar/elapsed
        // label actually need to move on a progress tick.
        if (id == NotificationCenter.messagePlayingProgressDidChanged) {
            updateProgressOnly();
            return;
        }
        // "Конец трека или выпуска" sleep-timer mode: a new track starting that isn't
        // the one playing when the timer was armed means the armed track just ended -
        // pause right there instead of letting the next one start.
        if (id == NotificationCenter.messagePlayingDidStart && sleepStopAtTrackEndArmed) {
            MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
            if (playing != null && playing.getId() != sleepStopTargetMessageId) {
                sleepStopAtTrackEndArmed = false;
                MediaController.getInstance().pauseMessage(playing);
            }
        }
        refreshAll();
    }

    private void updateProgressOnly() {
        MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
        if (playing == null || userIsSeeking) {
            return;
        }
        seekBar.setProgress((int) (playing.audioProgress * 1000));
        elapsedView.setText(formatTime(playing.audioProgressSec));
    }
}
