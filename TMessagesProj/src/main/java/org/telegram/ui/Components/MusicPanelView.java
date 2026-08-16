package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.R;
import org.telegram.messenger.music.MusicMessenger;
import org.telegram.messenger.music.MusicPlatform;
import org.telegram.messenger.music.MusicSettingsStore;
import org.telegram.messenger.music.Track;
import org.telegram.messenger.music.TrackProvider;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.MusicSettingsActivity;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.LongSupplier;

/**
 * "Музыка" tab content: polls the configured platform for the current track and sends it
 * into the chat as a card, an audio file or plain text.
 */
public class MusicPanelView extends LinearLayout {

    /** How often to re-check what's playing while the panel is on screen. */
    private static final long POLL_INTERVAL_MS = 5000;
    /** Clears the bottom tab strip of the emoji panel so the buttons aren't covered. */
    private static final int BOTTOM_INSET_DP = 68;

    private final int currentAccount;
    private final BaseFragment fragment;
    private final LongSupplier dialogIdSupplier;
    private final Theme.ResourcesProvider resourcesProvider;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final BackupImageView artworkView;
    private final ImageView artworkDownloadBadge;
    private final TextView trackTitleView;
    private final TextView trackSubtitleView;
    private final LinearLayout actionsRow;
    private final ActionButton cardButton;
    private final ActionButton audioButton;
    private final ActionButton textButton;
    private final ActionButton shareButton;

    private Track lastTrack;
    private boolean polling;
    private boolean sending;

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            refreshTrack();
            AndroidUtilities.runOnUIThread(this, POLL_INTERVAL_MS);
        }
    };

    public MusicPanelView(Context context, int currentAccount, BaseFragment fragment, LongSupplier dialogIdSupplier, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.currentAccount = currentAccount;
        this.fragment = fragment;
        this.dialogIdSupplier = dialogIdSupplier;
        this.resourcesProvider = resourcesProvider;

        setOrientation(VERTICAL);
        setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(BOTTOM_INSET_DP));

        // A single card holding everything - header, now-playing row, action row - rather than
        // three loose blocks directly on the panel's own background. Gives the whole tab a
        // deliberate shape instead of reading as an unstyled debug screen.
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(VERTICAL);
        card.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(18),
                Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)));
        card.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(14), AndroidUtilities.dp(14), AndroidUtilities.dp(14));
        addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        FrameLayout header = new FrameLayout(context);
        TextView headerTitle = new TextView(context);
        headerTitle.setText("Музыка");
        headerTitle.setTextSize(15);
        headerTitle.setTypeface(Typeface.DEFAULT_BOLD);
        headerTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        header.addView(headerTitle, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT));

        ImageView settingsButton = new ImageView(context);
        settingsButton.setImageResource(R.drawable.msg_settings_old);
        settingsButton.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        settingsButton.setScaleType(ImageView.ScaleType.CENTER);
        settingsButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), Theme.RIPPLE_MASK_CIRCLE_20DP));
        settingsButton.setOnClickListener(v -> {
            if (fragment != null) {
                fragment.presentFragment(new MusicSettingsActivity());
            }
        });
        header.addView(settingsButton, LayoutHelper.createFrame(32, 32, Gravity.CENTER_VERTICAL | Gravity.RIGHT));
        card.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        // Now-playing row: artwork on the left (with its own small "save this image" badge in
        // its corner), title/subtitle stacked to the right - the actual content, not just text.
        FrameLayout nowPlayingRow = new FrameLayout(context);

        FrameLayout artworkFrame = new FrameLayout(context);
        artworkView = new BackupImageView(context);
        artworkView.setRoundRadius(AndroidUtilities.dp(12));
        GradientDrawable placeholder = new GradientDrawable();
        placeholder.setShape(GradientDrawable.RECTANGLE);
        placeholder.setCornerRadius(AndroidUtilities.dp(12));
        placeholder.setColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
        artworkView.getImageReceiver().setImageBitmap(placeholder);
        artworkFrame.addView(artworkView, LayoutHelper.createFrame(56, 56));

        artworkDownloadBadge = new ImageView(context);
        artworkDownloadBadge.setImageResource(R.drawable.msg_download);
        artworkDownloadBadge.setScaleType(ImageView.ScaleType.CENTER);
        artworkDownloadBadge.setColorFilter(0xFFFFFFFF);
        artworkDownloadBadge.setPadding(AndroidUtilities.dp(3), AndroidUtilities.dp(3), AndroidUtilities.dp(3), AndroidUtilities.dp(3));
        artworkDownloadBadge.setBackground(Theme.createCircleDrawable(AndroidUtilities.dp(18),
                Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider)));
        artworkDownloadBadge.setVisibility(GONE);
        artworkDownloadBadge.setOnClickListener(v -> downloadArtwork());
        artworkFrame.addView(artworkDownloadBadge, LayoutHelper.createFrame(18, 18, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, -3, -3));
        nowPlayingRow.addView(artworkFrame, LayoutHelper.createFrame(56, 56, Gravity.CENTER_VERTICAL | Gravity.LEFT));

        LinearLayout textColumn = new LinearLayout(context);
        textColumn.setOrientation(VERTICAL);
        trackTitleView = new TextView(context);
        trackTitleView.setTextSize(16);
        trackTitleView.setTypeface(Typeface.DEFAULT_BOLD);
        trackTitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        trackTitleView.setSingleLine(true);
        trackTitleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        trackTitleView.setText("Проверяем, что играет…");
        textColumn.addView(trackTitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        trackSubtitleView = new TextView(context);
        trackSubtitleView.setTextSize(13.5f);
        trackSubtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        trackSubtitleView.setSingleLine(true);
        trackSubtitleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textColumn.addView(trackSubtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 0));
        nowPlayingRow.addView(textColumn, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 68, 0, 0, 0));

        card.addView(nowPlayingRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 56, 0, 0, 0, 14));

        actionsRow = new LinearLayout(context);
        actionsRow.setOrientation(HORIZONTAL);
        actionsRow.setVisibility(GONE);
        actionsRow.setWeightSum(4f);

        cardButton = new ActionButton(context, R.drawable.msg_media, "Карточка");
        cardButton.setOnClickListener(v -> sendCard());
        actionsRow.addView(cardButton, LayoutHelper.createLinear(0, 62, 1f, 0, 0, 3, 0));

        audioButton = new ActionButton(context, R.drawable.files_music, "Аудио");
        audioButton.setOnClickListener(v -> sendAudio());
        actionsRow.addView(audioButton, LayoutHelper.createLinear(0, 62, 1f, 3, 0, 3, 0));

        textButton = new ActionButton(context, R.drawable.msg_message, "Текст");
        textButton.setOnClickListener(v -> sendText());
        actionsRow.addView(textButton, LayoutHelper.createLinear(0, 62, 1f, 3, 0, 3, 0));

        shareButton = new ActionButton(context, R.drawable.msg_share, "Поделиться");
        shareButton.setOnClickListener(v -> sendShare());
        actionsRow.addView(shareButton, LayoutHelper.createLinear(0, 62, 1f, 3, 0, 0, 0));

        card.addView(actionsRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 62));
    }

    /** Icon-over-label pill, sized and centered identically for all four actions - the old plain
     *  {@code TextView} "buttons" had no shared layout contract, which is what let one of them
     *  (the share button, whose label also changes to a "n / total" progress string mid-send)
     *  drift out of alignment with the others instead of it being impossible by construction. */
    private class ActionButton extends LinearLayout {
        private final ImageView icon;
        private final TextView label;

        ActionButton(Context context, int iconRes, String text) {
            super(context);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER);
            setBackground(Theme.createSelectorDrawable(
                    Theme.getColor(Theme.key_listSelector, resourcesProvider),
                    Theme.RIPPLE_MASK_ALL,
                    AndroidUtilities.dp(14)));

            icon = new ImageView(context);
            icon.setImageResource(iconRes);
            icon.setColorFilter(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
            addView(icon, LayoutHelper.createLinear(22, 22, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 4));

            label = new TextView(context);
            label.setText(text);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setTextSize(11.5f);
            label.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            addView(label, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
        }

        void setLabel(String text) {
            label.setText(text);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startPolling();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopPolling();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        // Don't keep hitting the network while the tab is off screen.
        if (visibility == VISIBLE) {
            startPolling();
        } else {
            stopPolling();
        }
    }

    private void startPolling() {
        if (polling) {
            return;
        }
        polling = true;
        AndroidUtilities.cancelRunOnUIThread(pollRunnable);
        AndroidUtilities.runOnUIThread(pollRunnable);
    }

    private void stopPolling() {
        polling = false;
        AndroidUtilities.cancelRunOnUIThread(pollRunnable);
    }

    private void refreshTrack() {
        final TrackProvider provider = MusicSettingsStore.createCurrentProvider();
        if (provider == null || !provider.isConfigured()) {
            trackTitleView.setText("Платформа не настроена");
            trackSubtitleView.setText("Откройте настройки (⚙) и выберите платформу");
            actionsRow.setVisibility(GONE);
            artworkDownloadBadge.setVisibility(GONE);
            return;
        }
        executor.submit(() -> {
            Track track;
            try {
                track = provider.getTrack();
            } catch (Exception e) {
                FileLog.e("MusicPanelView.refreshTrack", e);
                track = Track.inactive();
            }
            final Track finalTrack = track;
            AndroidUtilities.runOnUIThread(() -> applyTrack(finalTrack));
        });
    }

    private void applyTrack(Track track) {
        // Never yank the buttons out from under a send in progress.
        if (sending) {
            return;
        }
        boolean sameTrack = lastTrack != null && track != null && java.util.Objects.equals(lastTrack.id, track.id) && track.id != null;
        lastTrack = track;
        if (track == null || !track.active) {
            trackTitleView.setText("Сейчас ничего не играет");
            trackSubtitleView.setText("");
            actionsRow.setVisibility(GONE);
            artworkDownloadBadge.setVisibility(GONE);
            artworkView.setImageDrawable(null);
            return;
        }
        trackTitleView.setText(track.title == null ? "" : track.title);
        trackSubtitleView.setText(buildSubtitle(track));
        actionsRow.setVisibility(VISIBLE);
        if (!sameTrack) {
            if (track.thumbUrl != null && !track.thumbUrl.isEmpty()) {
                artworkView.setImage(ImageLocation.getForPath(track.thumbUrl), "100_100", (android.graphics.drawable.Drawable) null, null);
                artworkDownloadBadge.setVisibility(VISIBLE);
            } else {
                artworkView.setImageDrawable(null);
                artworkDownloadBadge.setVisibility(GONE);
            }
        }
    }

    private String buildSubtitle(Track track) {
        StringBuilder sb = new StringBuilder(track.artistsJoined());
        String time = formatTime(track);
        if (!time.isEmpty()) {
            if (sb.length() > 0) {
                sb.append("  ·  ");
            }
            sb.append(time);
        }
        return sb.toString();
    }

    private String formatTime(Track track) {
        if (track.durationSec <= 0) {
            return "";
        }
        if (track.progressSec > 0) {
            return formatSeconds(track.progressSec) + " / " + formatSeconds(track.durationSec);
        }
        return formatSeconds(track.durationSec);
    }

    private String formatSeconds(int totalSeconds) {
        return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    /** Blocks repeat taps and shows that something is happening. */
    private boolean beginSend(ActionButton button) {
        if (sending || lastTrack == null || !lastTrack.active) {
            return false;
        }
        sending = true;
        setButtonsEnabled(false);
        button.setLabel("Отправляем…");
        return true;
    }

    private void endSend(ActionButton button, String label, boolean success, String error) {
        sending = false;
        setButtonsEnabled(true);
        button.setLabel(label);
        if (!success) {
            Toast.makeText(getContext(), error == null ? "Не удалось отправить" : error, Toast.LENGTH_LONG).show();
        }
    }

    private void setButtonsEnabled(boolean enabled) {
        cardButton.setEnabled(enabled);
        audioButton.setEnabled(enabled);
        textButton.setEnabled(enabled);
        shareButton.setEnabled(enabled);
        float alpha = enabled ? 1f : 0.6f;
        cardButton.setAlpha(alpha);
        audioButton.setAlpha(alpha);
        textButton.setAlpha(alpha);
        shareButton.setAlpha(alpha);
    }

    private void sendCard() {
        if (!beginSend(cardButton)) return;
        final Track track = lastTrack;
        final long dialogId = dialogIdSupplier.getAsLong();
        final MusicMessenger messenger = new MusicMessenger(currentAccount, MusicSettingsStore.buildCardStyle());
        executor.submit(() -> messenger.sendCard(dialogId, track,
                (success, error) -> endSend(cardButton, "Карточка", success, error)));
    }

    private void sendAudio() {
        if (!beginSend(audioButton)) return;
        final Track track = lastTrack;
        final long dialogId = dialogIdSupplier.getAsLong();
        final TrackProvider provider = MusicSettingsStore.createCurrentProvider();
        final MusicPlatform platform = MusicSettingsStore.getSelectedPlatform();
        final MusicMessenger messenger = new MusicMessenger(currentAccount, MusicSettingsStore.buildCardStyle());
        executor.submit(() -> messenger.sendAudio(dialogId, track, provider, platform, MusicSettingsStore.getCobaltApiUrl(),
                (success, error) -> endSend(audioButton, "Аудио", success, error)));
    }

    private void sendText() {
        if (!beginSend(textButton)) return;
        final Track track = lastTrack;
        final long dialogId = dialogIdSupplier.getAsLong();
        new MusicMessenger(currentAccount, MusicSettingsStore.buildCardStyle())
                .sendText(dialogId, track, (success, error) -> endSend(textButton, "Текст", success, error));
    }

    /** reSwaga's "Share with..." - pick several chats, send the card to all of them, reporting
     *  progress instead of going quiet until the whole batch finishes. Reuses
     *  {@link org.telegram.ui.UsersSelectActivity}, the same generic multi-chat picker the
     *  folder-creation screens already use, rather than building a chat picker from scratch. */
    private void sendShare() {
        if (sending || lastTrack == null || !lastTrack.active || fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        final org.telegram.ui.UsersSelectActivity picker =
                new org.telegram.ui.UsersSelectActivity(true, new java.util.ArrayList<>(), 0);
        picker.noChatTypes = true;
        picker.setDelegate((ids, flags) -> {
            if (ids == null || ids.isEmpty()) {
                return;
            }
            if (!beginSend(shareButton)) {
                return;
            }
            final Track track = lastTrack;
            final MusicMessenger messenger = new MusicMessenger(currentAccount, MusicSettingsStore.buildCardStyle());
            executor.submit(() -> messenger.sendCardToMultiple(ids, track, new MusicMessenger.MultiCallback() {
                @Override
                public void onProgress(long dialogId, boolean success, int done, int total) {
                    shareButton.setLabel(String.format(Locale.US, "%d / %d", done, total));
                }

                @Override
                public void onFinished(int succeeded, int total) {
                    endSend(shareButton, "Поделиться", succeeded > 0,
                            succeeded == total ? null : String.format(Locale.US, "Отправлено %d из %d", succeeded, total));
                }
            }));
        });
        fragment.presentFragment(picker);
    }

    /** The little badge on the artwork itself - saves the platform's own cover art as a real
     *  file (Downloads/Telegram), independent of "Карточка" which sends a rendered now-playing
     *  card, not the raw artwork. */
    private void downloadArtwork() {
        if (lastTrack == null || !lastTrack.active) {
            return;
        }
        final Track track = lastTrack;
        artworkDownloadBadge.setEnabled(false);
        final MusicMessenger messenger = new MusicMessenger(currentAccount, MusicSettingsStore.buildCardStyle());
        executor.submit(() -> messenger.downloadArtwork(track, (success, error) -> {
            artworkDownloadBadge.setEnabled(true);
            Toast.makeText(getContext(), success ? "Обложка сохранена" : (error == null ? "Не удалось сохранить" : error), Toast.LENGTH_SHORT).show();
        }));
    }
}
