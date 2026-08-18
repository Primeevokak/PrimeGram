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

    public static final int COLOR_BG = 0xFF0B0B0F;
    public static final int COLOR_SURFACE_CARD = 0xFF161622;
    public static final int COLOR_TEXT_PRIMARY = 0xFFFFFFFF;
    public static final int COLOR_TEXT_SECONDARY = 0xFF8E8E93;
    public static final int COLOR_ACCENT_GREEN = 0xFF1DB954;
    public static final int COLOR_ACCENT_PINK = 0xFFFF3B5C;

    private final BaseFragment fragment;
    private final Theme.ResourcesProvider resourcesProvider;

    private final LinearLayout content;
    private final RecyclerView recommendedList;
    private final FrameLayout recommendedEmptyContainer;
    private final RecyclerView playlistsList;
    private final LinearLayout libraryContainer;
    private final TextView likedCountSubtitle;

    private final FrameLayout miniPlayer;
    private final FrameLayout miniPlayerCoverFrame;
    private final ImageView miniCoverIcon;
    private final TextView miniPlayerTitle;
    private final TextView miniPlayerArtist;
    private final ImageView miniPlayerPlayPause;
    private final View miniPlayerProgressBar;

    private List<PrimeTrack> recommendedTracks = new ArrayList<>();
    private List<PrimePlaylist> playlists = new ArrayList<>();

    public PrimeMusicHomeView(Context context, BaseFragment fragment) {
        super(context);
        this.fragment = fragment;
        this.resourcesProvider = fragment.getResourceProvider();
        setBackgroundColor(COLOR_BG);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setClipToPadding(false);
        scrollView.setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
        addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(14), 0, dp(96));
        scrollView.addView(content, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

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
        searchButton.setOnClickListener(v -> fragment.presentFragment(new org.telegram.ui.PrimeMusicSearchActivity()));
        header.addView(searchButton, LayoutHelper.createLinear(36, 36, 0, 0, 2, 0));

        ImageView syncButton = new ImageView(context);
        syncButton.setImageResource(R.drawable.msg_download);
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
                    return;
                }
                PrimePlaylist playlist = playlists.get(position - 1);
                int count = PrimeMusicLibrary.getPlaylistTracks(playlist.id).size();
                ((PrimeMusicCardCell) holder.itemView).bind(playlist.name, count + " треков");
                holder.itemView.setOnClickListener(v -> fragment.presentFragment(new org.telegram.ui.PrimeMusicPlaylistActivity(playlist.id)));
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
        GradientDrawable libraryCoverBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF00C6FF, 0xFF0072FF});
        libraryCoverBg.setCornerRadius(dp(10));
        libraryCover.setBackground(libraryCoverBg);
        ImageView libraryIcon = new ImageView(context);
        libraryIcon.setImageResource(R.drawable.msg_folders);
        libraryIcon.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
        libraryCover.addView(libraryIcon, LayoutHelper.createFrame(24, 24, Gravity.CENTER));
        libraryCardContent.addView(libraryCover, LayoutHelper.createLinear(48, 48, 0, 0, 14, 0));

        LinearLayout libraryTexts = new LinearLayout(context);
        libraryTexts.setOrientation(LinearLayout.VERTICAL);
        libraryCardContent.addView(libraryTexts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        TextView libraryTitle = new TextView(context);
        libraryTitle.setText("Вся библиотека");
        libraryTitle.setTextColor(COLOR_TEXT_PRIMARY);
        libraryTitle.setTypeface(AndroidUtilities.bold());
        libraryTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        libraryTexts.addView(libraryTitle);

        TextView librarySubtitle = new TextView(context);
        librarySubtitle.setText("Треки, альбомы, артисты, жанры, папки");
        librarySubtitle.setTextColor(COLOR_TEXT_SECONDARY);
        librarySubtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
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

        // Vibrant Gradient Thumbnail for Liked Songs
        FrameLayout likedCover = new FrameLayout(context);
        GradientDrawable likedCoverBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFFFF2A6D, 0xFF9100FF});
        likedCoverBg.setCornerRadius(dp(10));
        likedCover.setBackground(likedCoverBg);
        ImageView likedHeart = new ImageView(context);
        likedHeart.setImageResource(R.drawable.media_like_active);
        likedHeart.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
        likedCover.addView(likedHeart, LayoutHelper.createFrame(24, 24, Gravity.CENTER));
        likedCardContent.addView(likedCover, LayoutHelper.createLinear(48, 48, 0, 0, 14, 0));

        LinearLayout likedTexts = new LinearLayout(context);
        likedTexts.setOrientation(LinearLayout.VERTICAL);
        likedCardContent.addView(likedTexts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        TextView likedTitle = new TextView(context);
        likedTitle.setText("Понравившиеся треки");
        likedTitle.setTextColor(COLOR_TEXT_PRIMARY);
        likedTitle.setTypeface(AndroidUtilities.bold());
        likedTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        likedTexts.addView(likedTitle);

        likedCountSubtitle = new TextView(context);
        likedCountSubtitle.setText("Любимые треки");
        likedCountSubtitle.setTextColor(COLOR_TEXT_SECONDARY);
        likedCountSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
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

        // ---- Docked Floating Mini-Player ----
        miniPlayer = new FrameLayout(context);
        GradientDrawable miniBg = new GradientDrawable();
        miniBg.setColor(0xF0181824);
        miniBg.setCornerRadii(new float[]{dp(16), dp(16), dp(16), dp(16), 0, 0, 0, 0});
        miniBg.setStroke(dp(1), 0x22FFFFFF);
        miniPlayer.setBackground(miniBg);
        miniPlayer.setClickable(true);
        miniPlayer.setOnClickListener(v -> {
            if (MediaController.getInstance().getPlayingMessageObject() != null) {
                fragment.presentFragment(new PrimeMusicNowPlayingActivity());
            }
        });
        addView(miniPlayer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 68, Gravity.BOTTOM));

        // Mini player Cover thumbnail
        miniPlayerCoverFrame = new FrameLayout(context);
        miniPlayer.addView(miniPlayerCoverFrame, LayoutHelper.createFrame(46, 46, Gravity.CENTER_VERTICAL | Gravity.LEFT, 12, 0, 0, 0));
        miniCoverIcon = new ImageView(context);
        miniCoverIcon.setImageResource(R.drawable.msg_filled_data_music);
        miniCoverIcon.setColorFilter(new PorterDuffColorFilter(0xEEFFFFFF, PorterDuff.Mode.SRC_IN));
        miniPlayerCoverFrame.addView(miniCoverIcon, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout miniTexts = new LinearLayout(context);
        miniTexts.setOrientation(LinearLayout.VERTICAL);
        miniTexts.setGravity(Gravity.CENTER_VERTICAL);
        miniPlayer.addView(miniTexts, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT, 68, 0, 68, 0));

        miniPlayerTitle = new TextView(context);
        miniPlayerTitle.setTextColor(COLOR_TEXT_PRIMARY);
        miniPlayerTitle.setTypeface(AndroidUtilities.bold());
        miniPlayerTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        miniPlayerTitle.setMaxLines(1);
        miniPlayerTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        miniTexts.addView(miniPlayerTitle);

        miniPlayerArtist = new TextView(context);
        miniPlayerArtist.setTextColor(COLOR_TEXT_SECONDARY);
        miniPlayerArtist.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        miniPlayerArtist.setMaxLines(1);
        miniPlayerArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        miniTexts.addView(miniPlayerArtist, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        // Circular high-contrast Play/Pause button
        FrameLayout playPauseCircle = new FrameLayout(context);
        playPauseCircle.setBackground(Theme.createCircleDrawable(dp(38), 0xFFFFFFFF));
        playPauseCircle.setClickable(true);
        miniPlayerPlayPause = new ImageView(context);
        miniPlayerPlayPause.setColorFilter(new PorterDuffColorFilter(0xFF0E0E12, PorterDuff.Mode.SRC_IN));
        playPauseCircle.addView(miniPlayerPlayPause, LayoutHelper.createFrame(20, 20, Gravity.CENTER));
        playPauseCircle.setOnClickListener(v -> {
            if (MediaController.getInstance().isMessagePaused()) {
                MediaController.getInstance().playMessage(MediaController.getInstance().getPlayingMessageObject());
            } else {
                MediaController.getInstance().pauseMessage(MediaController.getInstance().getPlayingMessageObject());
            }
            updateMiniPlayer();
        });
        miniPlayer.addView(playPauseCircle, LayoutHelper.createFrame(38, 38, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 14, 0));

        // Slim bottom progress line
        miniPlayerProgressBar = new View(context);
        miniPlayerProgressBar.setBackgroundColor(COLOR_ACCENT_GREEN);
        miniPlayer.addView(miniPlayerProgressBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 2, Gravity.BOTTOM));

        reloadData();
        updateMiniPlayer();

        if (!org.telegram.messenger.music.PrimeDeviceMusicScanner.hasScannedOnce()
                && org.telegram.messenger.music.PrimeDeviceMusicScanner.hasPermission()) {
            syncDeviceLibrary();
        }
    }

    private void addSectionHeader(String text) {
        TextView header = new TextView(getContext());
        header.setText(text);
        header.setTextColor(COLOR_TEXT_PRIMARY);
        header.setTypeface(AndroidUtilities.bold());
        header.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
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

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(COLOR_SURFACE_CARD);
        bg.setCornerRadius(dp(14));
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
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        tvTitle.setGravity(Gravity.CENTER);
        banner.addView(tvTitle);

        TextView tvDesc = new TextView(context);
        tvDesc.setText(description);
        tvDesc.setTextColor(COLOR_TEXT_SECONDARY);
        tvDesc.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        tvDesc.setGravity(Gravity.CENTER);
        banner.addView(tvDesc, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

        if (actionLabel != null && onAction != null) {
            TextView action = new TextView(context);
            action.setText(actionLabel);
            action.setTextColor(0xFF0B0B0F);
            action.setTypeface(AndroidUtilities.bold());
            action.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            action.setGravity(Gravity.CENTER);
            action.setPadding(dp(18), dp(9), dp(18), dp(9));
            action.setBackground(Theme.createRoundRectDrawable(dp(18), COLOR_ACCENT_GREEN));
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
        if (fragment.getParentActivity() == null) {
            return;
        }
        org.telegram.ui.Components.EditTextBoldCursor input = new org.telegram.ui.Components.EditTextBoldCursor(getContext());
        input.setTextColor(0xFF000000);
        input.setPadding(dp(16), dp(12), dp(16), dp(12));
        new android.app.AlertDialog.Builder(fragment.getParentActivity())
                .setTitle("Новый плейлист")
                .setView(input)
                .setPositiveButton("Создать", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        PrimeMusicLibrary.createPlaylist(name);
                        reloadData();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void playTrack(List<PrimeTrack> queue, int index) {
        int account = UserConfig.selectedAccount;
        PrimeMusicPlayback.playQueue(account, queue, index);
        updateMiniPlayer();
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
            AndroidUtilities.runOnUIThread(() -> {
                if (isAttachedToWindow()) {
                    applyReloadedData(recommendations, loadedPlaylists, likedCount, allTracks);
                }
            });
        });
    }

    private void applyReloadedData(List<PrimeTrack> recommendations, List<PrimePlaylist> loadedPlaylists, int likedCount, List<PrimeTrack> allTracks) {
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
                libraryContainer.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
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
            return;
        }
        miniPlayer.setVisibility(View.VISIBLE);
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
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.messagePlayingDidStart);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.messagePlayingDidReset);
        updateMiniPlayer();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.messagePlayingDidStart);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.messagePlayingDidReset);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        updateMiniPlayer();
    }

    private static class CardHolder extends RecyclerView.ViewHolder {
        CardHolder(View itemView) {
            super(itemView);
        }
    }
}
