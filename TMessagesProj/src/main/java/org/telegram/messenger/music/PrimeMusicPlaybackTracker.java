package org.telegram.messenger.music;

import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;

/**
 * PrimeGram Music: records one {@link PrimeMusicLibrary#recordPlay} per real playback
 * start, for every music track played anywhere in the app - not just tracks explicitly
 * added to the custom library. This is what lets "Recommended for you" (phase 8) draw
 * on the user's whole Telegram listening history, not only a small curated library;
 * a real Telegram audio message that was never manually added gets a lightweight
 * library row auto-created for it via {@link PrimeMusicLibrary#findOrAddTelegramTrack}
 * (idempotent - replaying the same track again reuses the same row).
 *
 * <p>{@code messagePlayingDidStart} is posted per-account by {@code MediaController}
 * ({@code NotificationCenter.getInstance(messageObject.currentAccount)}), never on the
 * global instance - this class originally registered on the global instance only,
 * mirroring a stale, actually-wrong "precedent" ({@code MusicSongsScreen.java}'s own
 * global registration, itself an outlier among every other real observer of this same
 * notification). That meant this tracker's {@code didReceivedNotification} was never
 * actually invoked by ordinary playback, so play history was never recorded and every
 * feature reading it (recommendations, per-track accent resolution) silently had
 * nothing to work with. Fixed to loop every account slot, the pattern already used
 * correctly elsewhere for this same notification (FragmentContextView, AudioPlayerAlert,
 * SharedAudioCell, etc.).
 */
public final class PrimeMusicPlaybackTracker implements NotificationCenter.NotificationCenterDelegate {

    private static final PrimeMusicPlaybackTracker instance = new PrimeMusicPlaybackTracker();

    public static void register() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter.getInstance(a).addObserver(instance, NotificationCenter.messagePlayingDidStart);
        }
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

        // PrimeGram Music: the single choke point every real playback start already
        // passes through - the right place to resolve the new track's accent color too,
        // rather than duplicating this per open screen (Home, Now Playing). Screens
        // just repaint from PrimeMusicTheme.currentAccent on their own existing
        // messagePlayingDidStart observers once this lands.
        PrimeTrack track = PrimeMusicLibrary.getTrack(trackId);
        PrimeTrackColor.resolveForCurrentTrack(track != null ? track.coverPath : null, null);
    }
}
