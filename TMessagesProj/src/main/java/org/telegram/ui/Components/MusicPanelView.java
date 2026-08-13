package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
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

    private final TextView trackTitleView;
    private final TextView trackSubtitleView;
    private final LinearLayout actionsRow;
    private final TextView cardButton;
    private final TextView audioButton;
    private final TextView textButton;
    private final TextView shareButton;

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

        FrameLayout header = new FrameLayout(context);
        TextView headerTitle = new TextView(context);
        headerTitle.setText("Музыка");
        headerTitle.setTextSize(18);
        headerTitle.setTypeface(Typeface.DEFAULT_BOLD);
        headerTitle.setTextColor(Theme.getColor(Theme.key_chat_emojiPanelIcon, resourcesProvider));
        header.addView(headerTitle, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT));

        ImageView settingsButton = new ImageView(context);
        settingsButton.setImageResource(R.drawable.msg_settings_old);
        settingsButton.setColorFilter(Theme.getColor(Theme.key_chat_emojiPanelIcon, resourcesProvider));
        settingsButton.setOnClickListener(v -> {
            if (fragment != null) {
                fragment.presentFragment(new MusicSettingsActivity());
            }
        });
        header.addView(settingsButton, LayoutHelper.createFrame(28, 28, Gravity.CENTER_VERTICAL | Gravity.RIGHT));
        addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        trackTitleView = new TextView(context);
        trackTitleView.setTextSize(16);
        trackTitleView.setTypeface(Typeface.DEFAULT_BOLD);
        trackTitleView.setTextColor(Theme.getColor(Theme.key_chat_emojiPanelIcon, resourcesProvider));
        trackTitleView.setSingleLine(true);
        trackTitleView.setText("Проверяем, что играет…");
        addView(trackTitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        trackSubtitleView = new TextView(context);
        trackSubtitleView.setTextSize(14);
        trackSubtitleView.setTextColor(Theme.getColor(Theme.key_chat_emojiPanelIcon, resourcesProvider));
        trackSubtitleView.setAlpha(0.7f);
        trackSubtitleView.setSingleLine(true);
        addView(trackSubtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 14));

        actionsRow = new LinearLayout(context);
        actionsRow.setOrientation(HORIZONTAL);
        actionsRow.setVisibility(GONE);

        cardButton = makeButton(context, "Карточка");
        cardButton.setOnClickListener(v -> sendCard());
        actionsRow.addView(cardButton, LayoutHelper.createLinear(0, 44, 1f, 0, 0, 4, 0));

        audioButton = makeButton(context, "Аудио");
        audioButton.setOnClickListener(v -> sendAudio());
        actionsRow.addView(audioButton, LayoutHelper.createLinear(0, 44, 1f, 4, 0, 4, 0));

        textButton = makeButton(context, "Текст");
        textButton.setOnClickListener(v -> sendText());
        actionsRow.addView(textButton, LayoutHelper.createLinear(0, 44, 1f, 4, 0, 4, 0));

        shareButton = makeButton(context, "Поделиться");
        shareButton.setOnClickListener(v -> sendShare());
        actionsRow.addView(shareButton, LayoutHelper.createLinear(0, 44, 1f, 4, 0, 0, 0));

        addView(actionsRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44));
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

    private TextView makeButton(Context context, String text) {
        TextView button = new TextView(context);
        button.setText(text);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(14);
        button.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider));
        button.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(8), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider)));
        return button;
    }

    private void refreshTrack() {
        final TrackProvider provider = MusicSettingsStore.createCurrentProvider();
        if (provider == null || !provider.isConfigured()) {
            trackTitleView.setText("Платформа не настроена");
            trackSubtitleView.setText("Откройте настройки (⚙) и выберите платформу");
            actionsRow.setVisibility(GONE);
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
        lastTrack = track;
        if (track == null || !track.active) {
            trackTitleView.setText("Сейчас ничего не играет");
            trackSubtitleView.setText("");
            actionsRow.setVisibility(GONE);
            return;
        }
        trackTitleView.setText(track.title == null ? "" : track.title);
        trackSubtitleView.setText(buildSubtitle(track));
        actionsRow.setVisibility(VISIBLE);
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
    private boolean beginSend(TextView button) {
        if (sending || lastTrack == null || !lastTrack.active) {
            return false;
        }
        sending = true;
        setButtonsEnabled(false);
        button.setText("Отправляем…");
        return true;
    }

    private void endSend(TextView button, String label, boolean success, String error) {
        sending = false;
        setButtonsEnabled(true);
        button.setText(label);
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
                    shareButton.setText(String.format(Locale.US, "%d / %d", done, total));
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
}
