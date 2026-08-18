package org.telegram.messenger.music;

import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;

/**
 * PrimeGram Music: records one {@link PrimeMusicLibrary#recordPlay} per real playback
 * start, for every music track played anywhere in the app - not just tracks explicitly
 * added to the custom library. This is what lets "Recommended for you" (phase 8) draw
 * on the user's whole Telegram listening history, not only a small curated library;
 * a real Telegram audio message that was never manually added gets a lightweight
 * library row auto-created for it via {@link PrimeMusicLibrary#findOrAddTelegramTrack}
 * (idempotent - replaying the same track again reuses the same row).
 *
 * <p>Registered on the global {@code NotificationCenter} instance, mirroring
 * {@code MusicSongsScreen.java}'s own {@code messagePlayingDidStart} observer - that's
 * the existing, working precedent for "track what's playing regardless of which
 * account/chat," not a new pattern invented here.
 */
public final class PrimeMusicPlaybackTracker implements NotificationCenter.NotificationCenterDelegate {

    private static final PrimeMusicPlaybackTracker instance = new PrimeMusicPlaybackTracker();

    public static void register() {
        NotificationCenter.getGlobalInstance().addObserver(instance, NotificationCenter.messagePlayingDidStart);
    }

    private PrimeMusicPlaybackTracker() {
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id != NotificationCenter.messagePlayingDidStart) {
            return;
        }
        MessageObject messageObject = (MessageObject) args[0];
        if (messageObject == null || !messageObject.isMusic()) {
            return;
        }
        long trackId;
        Long localTrackId = PrimeMusicPlayback.resolveLocalTrackId(messageObject);
        if (localTrackId != null) {
            trackId = localTrackId;
        } else {
            trackId = PrimeMusicLibrary.findOrAddTelegramTrack(
                    messageObject.getDialogId(),
                    messageObject.getId(),
                    messageObject.getMusicTitle(),
                    messageObject.getMusicAuthor(),
                    (int) messageObject.getDuration());
        }
        PrimeMusicLibrary.recordPlay(trackId);
    }
}
