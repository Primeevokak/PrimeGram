package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.music.PrimeMusicLibrary;
import org.telegram.messenger.music.PrimeMusicPlayback;
import org.telegram.messenger.music.PrimePlaylist;
import org.telegram.messenger.music.PrimeTrack;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.PrimeMusicNowPlayingActivity;
import org.telegram.ui.PrimeMusicPlayerSettingsActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * PrimeGram Music: main home screen for the Music tab with Spotify/Apple Music-grade
 * visual hierarchy, procedural gradient cards, dedicated empty states, and a modern floating mini-player.
 */
public class PrimeMusicHomeView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    // Real namida colors (PrimeMusicTheme) replaced the invented near-black/neon-green
    // palette that used to live here - see PrimeMusicTheme's own doc for where every
    // value below traces back to in namida's actual source.
    public static final int COLOR_TEXT_PRIMARY = org.telegram.messenger.music.PrimeMusicTheme.TEXT_PRIMARY;
    public static final int COLOR_TEXT_SECONDARY = org.telegram.messenger.music.PrimeMusicTheme.TEXT_TERTIARY;

    private final BaseFragment fragment;
    private final Theme.ResourcesProvider resourcesProvider;

    private final LinearLayout content;
    private final RecyclerView recommendedList;
    private final FrameLayout recommendedEmptyContainer;
    private final RecyclerView playlistsList;
    private final LinearLayout libraryContainer;
    private final TextView likedCountSubtitle;
    private TextView hiddenTracksRow;
    private TextView dislikedTracksRow;

    private final PrimeMusicSearchPanel searchPanel;

    private FrameLayout miniPlayer;
    private FrameLayout miniPlayerCoverFrame;
    private ImageView miniCoverIcon;
    private TextView miniPlayerTitle;
    private TextView miniPlayerArtist;
    private ImageView miniPlayerPlayPause;
    private View miniPlayerProgressFill;
    private FrameLayout wavePlayCircle;

    private List<PrimeTrack> recommendedTracks = new ArrayList<>();
    private List<PrimePlaylist> playlists = new ArrayList<>();

    public PrimeMusicHomeView(Context context, BaseFragment fragment) {
        super(context);
        this.fragment = fragment;
        this.resourcesProvider = fragment.getResourceProvider();
        setBackgroundColor(org.telegram.messenger.music.PrimeMusicTheme.bg(org.telegram.messenger.music.PrimeMusicTheme.currentAccent));

        ScrollView scrollView = new ScrollView(context);
        scrollView.setClipToPadding(false);
        scrollView.setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
        addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(14), 0, dp(96));
        scrollView.addView(content, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        // Constructed here (before the header below, whose search button needs a
        // reference) but not added to the view tree until the very end of this
        // constructor - see that addView call's own doc for why.
        searchPanel = new PrimeMusicSearchPanel(context, fragment);

        // ---- Top Header: Title + Settings ----
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(4), dp(16), dp(12));

        TextView headerTitle = new TextView(context);
        headerTitle.setText("Музыка");
        headerTitle.setTextColor(COLOR_TEXT_PRIMARY);
        headerTitle.setTypeface(AndroidUtilities.bold());
        headerTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 28);
        header.addView(headerTitle, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        ImageView searchButton = new ImageView(context);
        searchButton.setImageResource(R.drawable.msg_search);
        searchButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        searchButton.setPadding(dp(8), dp(8), dp(8), dp(8));
        searchButton.setColorFilter(new PorterDuffColorFilter(COLOR_TEXT_SECONDARY, PorterDuff.Mode.SRC_IN));
        searchButton.setClickable(true);
        searchButton.setBackground(Theme.createSelectorDrawable(0x22FFFFFF, Theme.RIPPLE_MASK_CIRCLE_20DP));
        searchButton.setOnClickListener(v -> searchPanel.show());
        header.addView(searchButton, LayoutHelper.createLinear(36, 36, 0, 0, 2, 0));

        ImageView syncButton = new ImageView(context);
        // A refresh/retry glyph, not a download-tray one - this triggers a device
        // library rescan, not a download, and a download-shaped icon here was being
        // read as a leftover "download" button.
        syncButton.setImageResource(R.drawable.msg_retry);
        syncButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        syncButton.setPadding(dp(8), dp(8), dp(8), dp(8));
        syncButton.setColorFilter(new PorterDuffColorFilter(COLOR_TEXT_SECONDARY, PorterDuff.Mode.SRC_IN));
        syncButton.setClickable(true);
        syncButton.setBackground(Theme.createSelectorDrawable(0x22FFFFFF, Theme.RIPPLE_MASK_CIRCLE_20DP));
        syncButton.setOnClickListener(v -> syncDeviceLibrary());
        header.addView(syncButton, LayoutHelper.createLinear(36, 36, 0, 0, 2, 0));

        ImageView settingsButton = new ImageView(context);
        settingsButton.setImageResource(R.drawable.msg_settings);
        settingsButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        settingsButton.setPadding(dp(8), dp(8), dp(8), dp(8));
        settingsButton.setColorFilter(new PorterDuffColorFilter(COLOR_TEXT_SECONDARY, PorterDuff.Mode.SRC_IN));
        settingsButton.setClickable(true);
        settingsButton.setBackground(Theme.createSelectorDrawable(0x22FFFFFF, Theme.RIPPLE_MASK_CIRCLE_20DP));
        settingsButton.setOnClickListener(v -> fragment.presentFragment(new PrimeMusicPlayerSettingsActivity()));
        header.addView(settingsButton, LayoutHelper.createLinear(36, 36));
        content.addView(header);

        // ---- Моя волна ----
        // First thing the user sees below the header, per the feature's own spec - a
        // real animated liquid-gradient background (GLSL/shader, matching the reference
        // design) is a separate, later pass; this is a static gradient placeholder so
        // the entry point and layout position are real and usable now.
        content.addView(buildWaveCard(context), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 240, 0, 16, 0, 28));

        // ---- Recommended Section ----
        addSectionHeader("Рекомендуем для вас");

        recommendedEmptyContainer = new FrameLayout(context);
        recommendedEmptyContainer.setPadding(dp(16), 0, dp(16), 0);
        recommendedEmptyContainer.setVisibility(View.GONE);
        recommendedEmptyContainer.addView(createEmptyBanner(context,
                "Персональные рекомендации",
                "Слушайте больше треков, чтобы здесь появились рекомендации",
                "Синхронизировать медиатеку", this::syncDeviceLibrary),
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        content.addView(recommendedEmptyContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        recommendedList = new RecyclerView(context);
        recommendedList.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
        recommendedList.setPadding(dp(16), 0, dp(16), 0);
        recommendedList.setClipToPadding(false);
        recommendedList.setAdapter(new RecyclerView.Adapter<CardHolder>() {
            @Override
            public CardHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
                return new CardHolder(new PrimeMusicCardCell(context, resourcesProvider));
            }

            @Override
            public void onBindViewHolder(CardHolder holder, int position) {
                PrimeTrack track = recommendedTracks.get(position);
                ((PrimeMusicCardCell) holder.itemView).bind(track.title, track.artist, track.coverPath);
                holder.itemView.setOnClickListener(v -> playTrack(recommendedTracks, position));
                holder.itemView.setOnLongClickListener(v -> {
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    PrimeMusicTrackMenu.show(fragment.getParentActivity(), v, track, resourcesProvider, PrimeMusicHomeView.this::reloadData);
                    return true;
                });
            }

            @Override
            public int getItemCount() {
                return recommendedTracks.size();
            }
        });
        content.addView(recommendedList, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 182));

        // ---- Playlists Section ----
        addSectionHeader("Плейлисты");
        playlistsList = new RecyclerView(context);
        playlistsList.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
        playlistsList.setPadding(dp(16), 0, dp(16), 0);
        playlistsList.setClipToPadding(false);
        playlistsList.setAdapter(new RecyclerView.Adapter<CardHolder>() {
            @Override
            public CardHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
                return new CardHolder(new PrimeMusicCardCell(context, resourcesProvider));
            }

            @Override
            public void onBindViewHolder(CardHolder holder, int position) {
                if (position == 0) {
                    ((PrimeMusicCardCell) holder.itemView).bind("+ Новый плейлист", "");
                    holder.itemView.setOnClickListener(v -> createPlaylistPrompt());
                    holder.itemView.setOnLongClickListener(null);
                    return;
                }
                PrimePlaylist playlist = playlists.get(position - 1);
                List<PrimeTrack> playlistTracks = PrimeMusicLibrary.getPlaylistTracks(playlist.id);
                ((PrimeMusicCardCell) holder.itemView).bind(playlist.name, playlistTracks.size() + " треков", null,
                        playlistTracks.isEmpty() ? null : () -> playTrack(playlistTracks, 0));
                holder.itemView.setOnClickListener(v -> fragment.presentFragment(new org.telegram.ui.PrimeMusicPlaylistActivity(playlist.id)));
                holder.itemView.setOnLongClickListener(v -> {
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    PrimeMusicPlaylistMenu.show(fragment.getParentActivity(), v, playlist, resourcesProvider, PrimeMusicHomeView.this::reloadData);
                    return true;
                });
            }

            @Override
            public int getItemCount() {
                return playlists.size() + 1;
            }
        });
        content.addView(playlistsList, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 182));

        // ---- Library Header + Liked Shortcut Hero Card ----
        addSectionHeader("Моя медиатека");

        FrameLayout libraryHeroCard = new FrameLayout(context);
        libraryHeroCard.setClickable(true);
        libraryHeroCard.setBackground(Theme.createSelectorDrawable(0x20FFFFFF, Theme.RIPPLE_MASK_ROUNDRECT_6DP));
        libraryHeroCard.setPadding(dp(16), dp(8), dp(16), dp(8));

        LinearLayout libraryCardContent = new LinearLayout(context);
        libraryCardContent.setOrientation(LinearLayout.HORIZONTAL);
        libraryCardContent.setGravity(Gravity.CENTER_VERTICAL);
        libraryHeroCard.addView(libraryCardContent, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        FrameLayout libraryCover = new FrameLayout(context);
        GradientDrawable libraryCoverBg = new GradientDrawable();
        libraryCoverBg.setColor(org.telegram.messenger.music.PrimeMusicTheme.card(org.telegram.messenger.music.PrimeMusicTheme.currentAccent));
        libraryCoverBg.setCornerRadius(dp(10));
        libraryCover.setBackground(libraryCoverBg);
        ImageView libraryIcon = new ImageView(context);
        libraryIcon.setImageResource(R.drawable.msg_folders);
        libraryIcon.setColorFilter(new PorterDuffColorFilter(org.telegram.messenger.music.PrimeMusicTheme.currentAccent | 0xFF000000, PorterDuff.Mode.SRC_IN));
        libraryCover.addView(libraryIcon, LayoutHelper.createFrame(24, 24, Gravity.CENTER));
        libraryCardContent.addView(libraryCover, LayoutHelper.createLinear(48, 48, 0, 0, 14, 0));

        LinearLayout libraryTexts = new LinearLayout(context);
        libraryTexts.setOrientation(LinearLayout.VERTICAL);
        libraryCardContent.addView(libraryTexts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        TextView libraryTitle = new TextView(context);
        libraryTitle.setText("Вся библиотека");
        libraryTitle.setTextColor(COLOR_TEXT_PRIMARY);
        libraryTitle.setTypeface(AndroidUtilities.bold());
        libraryTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, org.telegram.messenger.music.PrimeMusicTheme.SP_MEDIUM);
        libraryTexts.addView(libraryTitle);

        TextView librarySubtitle = new TextView(context);
        librarySubtitle.setText("Треки, альбомы, артисты, жанры, папки");
        librarySubtitle.setTextColor(COLOR_TEXT_SECONDARY);
        librarySubtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, org.telegram.messenger.music.PrimeMusicTheme.SP_SMALL);
        libraryTexts.addView(librarySubtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        ImageView libraryChevron = new ImageView(context);
        libraryChevron.setImageResource(R.drawable.arrow_more);
        libraryChevron.setColorFilter(new PorterDuffColorFilter(0x66FFFFFF, PorterDuff.Mode.SRC_IN));
        libraryCardContent.addView(libraryChevron, LayoutHelper.createLinear(18, 18));

        libraryHeroCard.setOnClickListener(v -> fragment.presentFragment(new org.telegram.ui.PrimeMusicLibraryActivity()));
        content.addView(libraryHeroCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 8));

        FrameLayout likedHeroCard = new FrameLayout(context);
        likedHeroCard.setClickable(true);
        likedHeroCard.setBackground(Theme.createSelectorDrawable(0x20FFFFFF, Theme.RIPPLE_MASK_ROUNDRECT_6DP));
        likedHeroCard.setPadding(dp(16), dp(8), dp(16), dp(8));

        LinearLayout likedCardContent = new LinearLayout(context);
        likedCardContent.setOrientation(LinearLayout.HORIZONTAL);
        likedCardContent.setGravity(Gravity.CENTER_VERTICAL);
        likedHeroCard.addView(likedCardContent, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        FrameLayout likedCover = new FrameLayout(context);
        GradientDrawable likedCoverBg = new GradientDrawable();
        likedCoverBg.setColor(org.telegram.messenger.music.PrimeMusicTheme.card(org.telegram.messenger.music.PrimeMusicTheme.currentAccent));
        likedCoverBg.setCornerRadius(dp(10));
        likedCover.setBackground(likedCoverBg);
        ImageView likedHeart = new ImageView(context);
        likedHeart.setImageResource(R.drawable.media_like_active);
        likedHeart.setColorFilter(new PorterDuffColorFilter(0xFFFF3B5C, PorterDuff.Mode.SRC_IN));
        likedCover.addView(likedHeart, LayoutHelper.createFrame(24, 24, Gravity.CENTER));
        likedCardContent.addView(likedCover, LayoutHelper.createLinear(48, 48, 0, 0, 14, 0));

        LinearLayout likedTexts = new LinearLayout(context);
        likedTexts.setOrientation(LinearLayout.VERTICAL);
        likedCardContent.addView(likedTexts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        TextView likedTitle = new TextView(context);
        likedTitle.setText("Понравившиеся треки");
        likedTitle.setTextColor(COLOR_TEXT_PRIMARY);
        likedTitle.setTypeface(AndroidUtilities.bold());
        likedTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, org.telegram.messenger.music.PrimeMusicTheme.SP_MEDIUM);
        likedTexts.addView(likedTitle);

        likedCountSubtitle = new TextView(context);
        likedCountSubtitle.setText("Любимые треки");
        likedCountSubtitle.setTextColor(COLOR_TEXT_SECONDARY);
        likedCountSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, org.telegram.messenger.music.PrimeMusicTheme.SP_SMALL);
        likedTexts.addView(likedCountSubtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        ImageView chevron = new ImageView(context);
        chevron.setImageResource(R.drawable.arrow_more);
        chevron.setColorFilter(new PorterDuffColorFilter(0x66FFFFFF, PorterDuff.Mode.SRC_IN));
        likedCardContent.addView(chevron, LayoutHelper.createLinear(18, 18));

        likedHeroCard.setOnClickListener(v -> fragment.presentFragment(new org.telegram.ui.PrimeMusicLikedActivity()));
        content.addView(likedHeroCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 8));

        // ---- Full Library List Container ----
        libraryContainer = new LinearLayout(context);
        libraryContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(libraryContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // ---- Hidden tracks entry - only shown once something is actually hidden, so
        //      it doesn't clutter Home for the common case of nobody having used
        //      "Удалить из библиотеки" yet. See PrimeMusicHiddenTracksActivity's own
        //      doc for why this screen exists at all (a hide is one popup-menu tap
        //      away from hitting the wrong row). ----
        hiddenTracksRow = new TextView(context);
        hiddenTracksRow.setTypeface(AndroidUtilities.bold());
        hiddenTracksRow.setTextSize(TypedValue.COMPLEX_UNIT_DIP, org.telegram.messenger.music.PrimeMusicTheme.SP_SMALL);
        hiddenTracksRow.setGravity(Gravity.CENTER);
        hiddenTracksRow.setPadding(dp(16), dp(10), dp(16), dp(10));
        hiddenTracksRow.setClickable(true);
        hiddenTracksRow.setBackground(Theme.createSelectorDrawable(0x18FFFFFF, Theme.RIPPLE_MASK_ALL));
        hiddenTracksRow.setOnClickListener(v -> fragment.presentFragment(new org.telegram.ui.PrimeMusicHiddenTracksActivity()));
        hiddenTracksRow.setVisibility(View.GONE);
        content.addView(hiddenTracksRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

        dislikedTracksRow = new TextView(context);
        dislikedTracksRow.setTypeface(AndroidUtilities.bold());
        dislikedTracksRow.setTextSize(TypedValue.COMPLEX_UNIT_DIP, org.telegram.messenger.music.PrimeMusicTheme.SP_SMALL);
        dislikedTracksRow.setGravity(Gravity.CENTER);
        dislikedTracksRow.setPadding(dp(16), dp(10), dp(16), dp(10));
        dislikedTracksRow.setClickable(true);
        dislikedTracksRow.setBackground(Theme.createSelectorDrawable(0x18FFFFFF, Theme.RIPPLE_MASK_ALL));
        dislikedTracksRow.setOnClickListener(v -> fragment.presentFragment(new org.telegram.ui.PrimeMusicDislikedActivity()));
        dislikedTracksRow.setVisibility(View.GONE);
        content.addView(dislikedTracksRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

        // Actually added to the view tree here, on top of everything else built above
        // (carousels, hero cards, mini-player) - the field itself was constructed near
        // the top of this constructor (before the header's search button, which needs
        // a reference to it) since a blank-final field can't be read anywhere in the
        // constructor before its assignment; only the addView placement (which
        // determines z-order/draw-on-top) needed to happen down here.
        addView(searchPanel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        reloadData();
        updateMiniPlayer();

        if (!org.telegram.messenger.music.PrimeDeviceMusicScanner.hasScannedOnce()
                && org.telegram.messenger.music.PrimeDeviceMusicScanner.hasPermission()) {
            syncDeviceLibrary();
        }
    }

    /** PrimeGram: whether a touch at (x, y) - in this view's own local coordinates,
     *  same space {@code MotionEvent}s reach {@link org.telegram.ui.PrimeMusicActivity
     *  #canParentTabsSlide} in - lands over one of the horizontal carousels. The
     *  Music tab's own swipe-away-to-another-tab gesture must not claim touches here:
     *  it would fight the carousel's own horizontal scroll for the same drag, and
     *  since the outer tab-swipe used to always lose that fight by never trying
     *  (canParentTabsSlide returning false everywhere), "swipe away from Music" and
     *  "swipe the carousels" only work together once the tab-swipe backs off inside
     *  these two zones specifically, rather than claiming the whole page. */
    public boolean isTouchOverHorizontalCarousel(float x, float y) {
        return isTouchInside(recommendedList, x, y) || isTouchInside(playlistsList, x, y);
    }

    private boolean isTouchInside(View child, float x, float y) {
        if (child == null || child.getVisibility() != VISIBLE) {
            return false;
        }
        int[] childLocation = new int[2];
        int[] selfLocation = new int[2];
        child.getLocationInWindow(childLocation);
        getLocationInWindow(selfLocation);
        float left = childLocation[0] - selfLocation[0];
        float top = childLocation[1] - selfLocation[1];
        return x >= left && x <= left + child.getWidth() && y >= top && y <= top + child.getHeight();
    }

    /** "Моя волна" card - a real animated background
     *  ({@link org.telegram.ui.Components.PrimeWaveBackgroundView}, drifting blobs +
     *  continuous hue shift), not the static gradient this used to be. Pressing play
     *  starts the wave right here on Home - no separate full-screen destination; the
     *  relocated mini-player below shows what's playing and still opens
     *  {@link org.telegram.ui.PrimeMusicNowPlayingActivity} on tap, same as it always
     *  did when it was docked at the bottom of the screen instead of living in this
     *  card. */
    private FrameLayout buildWaveCard(Context context) {
        FrameLayout card = new FrameLayout(context);
        card.setClipToOutline(true);
        card.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(20));
            }
        });

        org.telegram.ui.Components.PrimeWaveBackgroundView waveBg = new org.telegram.ui.Components.PrimeWaveBackgroundView(context);
        card.addView(waveBg, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        TextView waveTitle = new TextView(context);
        waveTitle.setText("Моя волна");
        waveTitle.setTextColor(0xFFFFFFFF);
        waveTitle.setTypeface(AndroidUtilities.bold());
        waveTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        card.addView(waveTitle, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 18, 16, 0, 0));

        wavePlayCircle = new FrameLayout(context);
        wavePlayCircle.setBackground(Theme.createCircleDrawable(dp(56), 0xFFFFFFFF));
        wavePlayCircle.setClickable(true);
        ImageView playIcon = new ImageView(context);
        playIcon.setImageResource(R.drawable.ic_action_play);
        playIcon.setColorFilter(new PorterDuffColorFilter(0xFF0E0E12, PorterDuff.Mode.SRC_IN));
        wavePlayCircle.addView(playIcon, LayoutHelper.createFrame(26, 26, Gravity.CENTER));
        wavePlayCircle.setOnClickListener(v -> startWave());
        card.addView(wavePlayCircle, LayoutHelper.createFrame(56, 56, Gravity.CENTER));

        // ---- The relocated mini-player - lives here now instead of a separate bar
        // docked at the bottom of the whole screen, semi-transparent over the wave's
        // own animated background. Shows whatever is actually playing (any source, not
        // only wave tracks) - same as the bar it replaced. ----
        miniPlayer = new FrameLayout(context);
        GradientDrawable miniBg = new GradientDrawable();
        miniBg.setColor(0x66000000);
        miniBg.setCornerRadius(dp(16));
        miniPlayer.setBackground(miniBg);
        miniPlayer.setClickable(true);
        miniPlayer.setOnClickListener(v -> {
            if (MediaController.getInstance().getPlayingMessageObject() != null) {
                fragment.presentFragment(new PrimeMusicNowPlayingActivity());
            }
        });
        card.addView(miniPlayer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 60, Gravity.BOTTOM, 14, 0, 14, 14));

        // Progress fill - scaleX'd (not re-measured) on every progress tick, same cheap
        // GPU-only update the equalizer/timer animations in this feature already use;
        // a left-pivoted transform behind the title, filling as the track approaches its
        // end, the passive version of the E2E hold-button's own fill idea.
        miniPlayerProgressFill = new View(context);
        miniPlayerProgressFill.setBackgroundColor(0x33FFFFFF);
        miniPlayerProgressFill.setPivotX(0f);
        miniPlayerProgressFill.setScaleX(0f);
        miniPlayer.addView(miniPlayerProgressFill, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        miniPlayerCoverFrame = new FrameLayout(context);
        miniPlayer.addView(miniPlayerCoverFrame, LayoutHelper.createFrame(40, 40, Gravity.CENTER_VERTICAL | Gravity.LEFT, 10, 0, 0, 0));
        miniCoverIcon = new ImageView(context);
        miniCoverIcon.setImageResource(R.drawable.msg_filled_data_music);
        miniCoverIcon.setColorFilter(new PorterDuffColorFilter(0xEEFFFFFF, PorterDuff.Mode.SRC_IN));
        miniPlayerCoverFrame.addView(miniCoverIcon, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout miniTexts = new LinearLayout(context);
        miniTexts.setOrientation(LinearLayout.VERTICAL);
        miniTexts.setGravity(Gravity.CENTER_VERTICAL);
        miniPlayer.addView(miniTexts, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT, 60, 0, 56, 0));

        miniPlayerTitle = new TextView(context);
        miniPlayerTitle.setTextColor(0xFFFFFFFF);
        miniPlayerTitle.setTypeface(AndroidUtilities.bold());
        miniPlayerTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, org.telegram.messenger.music.PrimeMusicTheme.SP_MEDIUM);
        miniPlayerTitle.setMaxLines(1);
        miniPlayerTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        miniTexts.addView(miniPlayerTitle);

        miniPlayerArtist = new TextView(context);
        miniPlayerArtist.setTextColor(0xCCFFFFFF);
        miniPlayerArtist.setTextSize(TypedValue.COMPLEX_UNIT_DIP, org.telegram.messenger.music.PrimeMusicTheme.SP_SMALL);
        miniPlayerArtist.setMaxLines(1);
        miniPlayerArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        miniTexts.addView(miniPlayerArtist, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        FrameLayout playPauseCircle = new FrameLayout(context);
        playPauseCircle.setBackground(Theme.createCircleDrawable(dp(34), 0xFFFFFFFF));
        playPauseCircle.setClickable(true);
        miniPlayerPlayPause = new ImageView(context);
        miniPlayerPlayPause.setColorFilter(new PorterDuffColorFilter(0xFF0E0E12, PorterDuff.Mode.SRC_IN));
        playPauseCircle.addView(miniPlayerPlayPause, LayoutHelper.createFrame(18, 18, Gravity.CENTER));
        playPauseCircle.setOnClickListener(v -> {
            if (MediaController.getInstance().isMessagePaused()) {
                MediaController.getInstance().playMessage(MediaController.getInstance().getPlayingMessageObject());
            } else {
                MediaController.getInstance().pauseMessage(MediaController.getInstance().getPlayingMessageObject());
            }
            updateMiniPlayer();
        });
        miniPlayer.addView(playPauseCircle, LayoutHelper.createFrame(34, 34, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 10, 0));

        return card;
    }

    private void startWave() {
        org.telegram.messenger.music.PrimeTrack track = org.telegram.messenger.music.PrimeMusicWave.current() == null
                ? org.telegram.messenger.music.PrimeMusicWave.start()
                : org.telegram.messenger.music.PrimeMusicWave.current();
        if (track == null) {
            return;
        }
        org.telegram.messenger.music.PrimeMusicPlayback.playSingle(UserConfig.selectedAccount, track);
    }

    private void addSectionHeader(String text) {
        // namida's real section-header text (_HorizontalList's own header,
        // home_page.dart:1474) is displayLarge - 17sp/weight 700 - not the 18sp/oversized
        // guess this used to be.
        TextView header = new TextView(getContext());
        header.setText(text);
        header.setTextColor(COLOR_TEXT_PRIMARY);
        header.setTypeface(AndroidUtilities.bold());
        header.setTextSize(TypedValue.COMPLEX_UNIT_DIP, org.telegram.messenger.music.PrimeMusicTheme.SP_LARGE);
        header.setPadding(dp(16), dp(18), dp(16), dp(10));
        content.addView(header);
    }

    private View createEmptyBanner(Context context, String title, String description) {
        return createEmptyBanner(context, title, description, null, null);
    }

    private View createEmptyBanner(Context context, String title, String description, String actionLabel, Runnable onAction) {
        LinearLayout banner = new LinearLayout(context);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setGravity(Gravity.CENTER);
        banner.setPadding(dp(18), dp(20), dp(18), dp(20));

        int accent = org.telegram.messenger.music.PrimeMusicTheme.currentAccent;
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(org.telegram.messenger.music.PrimeMusicTheme.card(accent));
        bg.setCornerRadius(dp(org.telegram.messenger.music.PrimeMusicTheme.CARD_RADIUS_DP));
        bg.setStroke(dp(1), 0x1AFFFFFF);
        banner.setBackground(bg);

        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.msg_filled_data_music);
        icon.setColorFilter(new PorterDuffColorFilter(0x66FFFFFF, PorterDuff.Mode.SRC_IN));
        banner.addView(icon, LayoutHelper.createLinear(32, 32, Gravity.CENTER, 0, 0, 0, 8));

        TextView tvTitle = new TextView(context);
        tvTitle.setText(title);
        tvTitle.setTextColor(COLOR_TEXT_PRIMARY);
        tvTitle.setTypeface(AndroidUtilities.bold());
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, org.telegram.messenger.music.PrimeMusicTheme.SP_MEDIUM);
        tvTitle.setGravity(Gravity.CENTER);
        banner.addView(tvTitle);

        TextView tvDesc = new TextView(context);
        tvDesc.setText(description);
        tvDesc.setTextColor(COLOR_TEXT_SECONDARY);
        tvDesc.setTextSize(TypedValue.COMPLEX_UNIT_DIP, org.telegram.messenger.music.PrimeMusicTheme.SP_SMALL);
        tvDesc.setGravity(Gravity.CENTER);
        banner.addView(tvDesc, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

        if (actionLabel != null && onAction != null) {
            TextView action = new TextView(context);
            action.setText(actionLabel);
            action.setTextColor(0xFF000000);
            action.setTypeface(AndroidUtilities.bold());
            action.setTextSize(TypedValue.COMPLEX_UNIT_DIP, org.telegram.messenger.music.PrimeMusicTheme.SP_SMALL);
            action.setGravity(Gravity.CENTER);
            action.setPadding(dp(18), dp(9), dp(18), dp(9));
            action.setBackground(Theme.createRoundRectDrawable(dp(18), accent));
            action.setClickable(true);
            action.setOnClickListener(v -> onAction.run());
            banner.addView(action, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 14, 0, 0));
        }

        return banner;
    }

    private void syncDeviceLibrary() {
        org.telegram.messenger.music.PrimeDeviceMusicScanner.scan(count -> reloadData());
    }

    private void createPlaylistPrompt() {
        PrimeMusicPlaylistDialog.show(fragment.getParentActivity(), playlistId -> reloadData());
    }

    private List<PrimeTrack> lastAllTracks = new ArrayList<>();

    private void playTrack(List<PrimeTrack> queue, int index) {
        int account = UserConfig.selectedAccount;
        PrimeMusicPlayback.playQueue(account, queue, index);
        updateMiniPlayer();
        refreshPlayingHighlights();
    }

    /** Re-binds the already-built library rows against the cached track list - no DB
     *  read, just repaints each row's "currently playing" highlight (see
     *  PrimeMusicTrackRow.bind's own doc) after a play/pause/track-change. Cheap enough
     *  to call on every such event, unlike reloadData()'s full async DB re-fetch. */
    private void refreshPlayingHighlights() {
        int childIndex = 0;
        for (PrimeTrack track : lastAllTracks) {
            if (childIndex >= libraryContainer.getChildCount()) {
                break;
            }
            View child = libraryContainer.getChildAt(childIndex);
            if (child instanceof PrimeMusicTrackRow) {
                ((PrimeMusicTrackRow) child).bind(track);
            }
            childIndex++;
        }
    }

    /** Reads the (potentially large) library off the main thread - the DB scans and the
     *  in-memory recommendation scoring were previously run synchronously right here on
     *  every tab open, which is the real cause behind "opening the player lags" once a
     *  device sync has populated a real-sized library. Building the actual row Views
     *  still happens on the UI thread afterward (it has to - they're Views), but that
     *  alone is far cheaper than DB reads + scoring blocking the main thread first. */
    public void reloadData() {
        org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
            List<PrimeTrack> recommendations = PrimeMusicLibrary.getRecommendations(10);
            List<PrimePlaylist> loadedPlaylists = PrimeMusicLibrary.getPlaylists();
            int likedCount = PrimeMusicLibrary.getLikedTracks().size();
            List<PrimeTrack> allTracks = PrimeMusicLibrary.getAllTracks();
            int hiddenCount = PrimeMusicLibrary.getExcludedTracks().size();
            int dislikedCount = PrimeMusicLibrary.getDislikedTracks().size();
            AndroidUtilities.runOnUIThread(() -> {
                if (isAttachedToWindow()) {
                    applyReloadedData(recommendations, loadedPlaylists, likedCount, allTracks, hiddenCount, dislikedCount);
                }
            });
        });
    }

    private void applyReloadedData(List<PrimeTrack> recommendations, List<PrimePlaylist> loadedPlaylists, int likedCount, List<PrimeTrack> allTracks, int hiddenCount, int dislikedCount) {
        if (hiddenCount > 0) {
            hiddenTracksRow.setText("Скрытые треки (" + hiddenCount + ")");
            hiddenTracksRow.setTextColor(org.telegram.messenger.music.PrimeMusicTheme.TEXT_TERTIARY);
            hiddenTracksRow.setVisibility(View.VISIBLE);
        } else {
            hiddenTracksRow.setVisibility(View.GONE);
        }
        if (dislikedCount > 0) {
            dislikedTracksRow.setText("Не нравится (" + dislikedCount + ")");
            dislikedTracksRow.setTextColor(org.telegram.messenger.music.PrimeMusicTheme.TEXT_TERTIARY);
            dislikedTracksRow.setVisibility(View.VISIBLE);
        } else {
            dislikedTracksRow.setVisibility(View.GONE);
        }
        recommendedTracks = recommendations;
        if (recommendedTracks.isEmpty()) {
            recommendedList.setVisibility(View.GONE);
            recommendedEmptyContainer.setVisibility(View.VISIBLE);
        } else {
            recommendedList.setVisibility(View.VISIBLE);
            recommendedEmptyContainer.setVisibility(View.GONE);
            recommendedList.getAdapter().notifyDataSetChanged();
        }

        playlists = loadedPlaylists;
        playlistsList.getAdapter().notifyDataSetChanged();

        likedCountSubtitle.setText(likedCount > 0 ? (likedCount + " " + (likedCount == 1 ? "трек" : "треков")) : "Любимые треки");

        lastAllTracks = allTracks;
        libraryContainer.removeAllViews();
        if (allTracks.isEmpty()) {
            FrameLayout emptyFrame = new FrameLayout(getContext());
            emptyFrame.setPadding(dp(16), dp(8), dp(16), dp(8));
            emptyFrame.addView(createEmptyBanner(getContext(),
                    "Медиатека пуста",
                    "Добавляйте треки из чатов или синхронизируйте музыку с устройства",
                    "Синхронизировать медиатеку", this::syncDeviceLibrary),
                    LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            libraryContainer.addView(emptyFrame, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        } else {
            for (int i = 0; i < allTracks.size(); i++) {
                PrimeTrack track = allTracks.get(i);
                PrimeMusicTrackRow row = new PrimeMusicTrackRow(getContext(), resourcesProvider);
                row.bind(track);
                row.setOnClickListener(v -> playTrack(allTracks, allTracks.indexOf(track)));
                row.getLikeIcon().setOnClickListener(v -> {
                    PrimeMusicLibrary.setLiked(track.id, !track.liked);
                    reloadData();
                });
                row.bindDefaultMenu(fragment.getParentActivity(), track, this::reloadData);
                libraryContainer.addView(row, PrimeMusicTrackRow.createRowLayoutParams());
            }
        }
    }

    private String safeCoverPath(long trackId) {
        PrimeTrack track = PrimeMusicLibrary.getTrack(trackId);
        return track != null ? track.coverPath : null;
    }

    private void updateMiniPlayer() {
        MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
        if (playing == null) {
            miniPlayer.setVisibility(View.GONE);
            wavePlayCircle.setVisibility(View.VISIBLE);
            return;
        }
        miniPlayer.setVisibility(View.VISIBLE);
        wavePlayCircle.setVisibility(View.GONE);
        String title = playing.getMusicTitle();
        String artist = playing.getMusicAuthor();
        miniPlayerTitle.setText(title != null && !title.isEmpty() ? title : "Без названия");
        miniPlayerArtist.setText(artist != null ? artist : "");

        String seed = (title != null ? title : "") + "_" + (artist != null ? artist : "");
        Long localId = PrimeMusicPlayback.resolveLocalTrackId(playing);
        String coverPath = localId != null ? safeCoverPath(localId) : null;
        org.telegram.messenger.music.PrimeAlbumArt.applyCover(miniPlayerCoverFrame, miniCoverIcon, coverPath, seed, 10);

        boolean paused = MediaController.getInstance().isMessagePaused();
        miniPlayerPlayPause.setImageResource(paused ? R.drawable.ic_action_play : R.drawable.ic_action_pause);
        updateMiniPlayerProgress();
    }

    /** Cheap on purpose - called on every progress tick (many times a second while
     *  playing), so this only ever touches a scaleX transform, never re-measures or
     *  re-binds anything else. See PrimeMusicNowPlayingActivity's own doc on why a
     *  heavier handler on this exact notification is what "the player runs at 1.5 fps"
     *  turned out to mean. */
    private void updateMiniPlayerProgress() {
        MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
        if (playing == null) {
            return;
        }
        miniPlayerProgressFill.setScaleX(Math.max(0f, Math.min(1f, playing.audioProgress)));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // These are posted per-account by MediaController (NotificationCenter.getInstance
        // (messageObject.currentAccount)), never on the global instance - registering
        // there only meant this view's own repaint handlers were never actually invoked
        // by ordinary playback. Loop every account slot, mirroring the working pattern
        // already used for these same notifications elsewhere (FragmentContextView etc.).
        for (int a = 0; a < org.telegram.messenger.UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.messagePlayingDidStart);
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.messagePlayingDidReset);
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        }
        org.telegram.messenger.music.PrimeMusicTheme.addAccentListener(accentListener);
        updateMiniPlayer();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        for (int a = 0; a < org.telegram.messenger.UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.messagePlayingDidStart);
            NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
            NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.messagePlayingDidReset);
            NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        }
        org.telegram.messenger.music.PrimeMusicTheme.removeAccentListener(accentListener);
    }

    /** Fires once the currently-playing track's real accent color finishes resolving
     *  (see PrimeTrackColor's own doc for why this can't just piggyback on the
     *  synchronous messagePlayingDidStart handler below - that one fires too early,
     *  before the async Palette extraction has a result). */
    private final Runnable accentListener = () -> {
        applyAccentTint();
        refreshPlayingHighlights();
    };

    /** Re-tints the chrome that was painted once at construction time with whatever
     *  the accent was then - just the screen background now; the mini-player living
     *  inside the wave card is deliberately a flat semi-transparent black regardless of
     *  accent, since it sits on top of the wave's own animated color-shifting
     *  background and an accent-tinted bar would fight it for attention. */
    private void applyAccentTint() {
        int accent = org.telegram.messenger.music.PrimeMusicTheme.currentAccent;
        setBackgroundColor(org.telegram.messenger.music.PrimeMusicTheme.bg(accent));
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagePlayingProgressDidChanged) {
            updateMiniPlayerProgress();
            return;
        }
        // The wave's own auto-advance: messagePlayingDidReset only fires for a genuine
        // stop (natural end, explicit stop) - MediaController's own playMessage() path
        // that switches to a *different* track passes notify=false and never posts this
        // at all (confirmed by reading cleanupPlayer's call site inside playMessage), so
        // this can't misfire just because the user opened some unrelated track elsewhere.
        if (id == NotificationCenter.messagePlayingDidReset && org.telegram.messenger.music.PrimeMusicWave.isActive()) {
            org.telegram.messenger.music.PrimeTrack next = org.telegram.messenger.music.PrimeMusicWave.next();
            if (next != null) {
                org.telegram.messenger.music.PrimeMusicPlayback.playSingle(UserConfig.selectedAccount, next);
            }
        }
        updateMiniPlayer();
        refreshPlayingHighlights();
    }

    private static class CardHolder extends RecyclerView.ViewHolder {
        CardHolder(View itemView) {
            super(itemView);
        }
    }
}
