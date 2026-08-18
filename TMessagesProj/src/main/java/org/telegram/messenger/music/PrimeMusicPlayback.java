package org.telegram.messenger.music;

import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PrimeGram Music: plays {@link PrimeTrack}s through the existing, unmodified
 * {@code MediaController} pipeline instead of a parallel player - a locally-downloaded
 * track (no backing Telegram message) is wrapped in a synthetic {@code TL_message}/
 * {@code MessageObject}, exactly the precedent {@code SelectAudioAlert.java} already
 * uses for its own on-device-music picker (negative synthetic id, {@code attachPath}
 * pointing straight at the local file, a {@code TL_documentAttributeAudio} carrying
 * title/performer/duration, {@code dc_id=0} so nothing tries to fetch it from a
 * server). Doing this means the pinned mini-bar, lockscreen/Bluetooth controls, and
 * the existing "stop whatever's playing when a video/voice message starts" behavior
 * (already unconditional in {@code MediaController.playMessage()}) all keep working
 * for local-library tracks with zero new code.
 */
public final class PrimeMusicPlayback {

    /** A local track's synthetic message stashes its {@link PrimeTrack#id} in the
     *  message text (never rendered anywhere - audio messages don't show `message` as
     *  a caption in the mini-player/lockscreen, only the audio attributes' title/
     *  performer) so {@link #resolveTrackId} can recover it without a path lookup. */
    private static final String LOCAL_TRACK_MARKER = "prime_music_track:";

    /** Stays well clear of the {@code -2000000000}-ish range {@code SelectAudioAlert}
     *  already uses for its own throwaway per-picker-session ids, and of real message
     *  ids (always positive) - avoids any chance of two different synthetic-message
     *  producers colliding if both happen to be alive at once. */
    private static final AtomicInteger syntheticIdCounter = new AtomicInteger(-2100000000);

    private PrimeMusicPlayback() {
    }

    public static MessageObject buildMessageObject(int account, PrimeTrack track) {
        TLRPC.TL_message message = new TLRPC.TL_message();
        message.out = true;
        message.id = syntheticIdCounter.decrementAndGet();
        message.peer_id = new TLRPC.TL_peerUser();
        message.from_id = new TLRPC.TL_peerUser();
        message.peer_id.user_id = message.from_id.user_id = UserConfig.getInstance(account).getClientUserId();
        message.date = (int) (System.currentTimeMillis() / 1000);
        message.message = LOCAL_TRACK_MARKER + track.id;
        message.attachPath = track.localPath;
        message.flags |= TLRPC.MESSAGE_FLAG_HAS_MEDIA | TLRPC.MESSAGE_FLAG_HAS_FROM_ID;

        message.media = new TLRPC.TL_messageMediaDocument();
        message.media.flags |= 3;
        message.media.document = new TLRPC.TL_document();
        message.media.document.id = 0;
        message.media.document.access_hash = 0;
        message.media.document.file_reference = new byte[0];
        message.media.document.date = message.date;
        message.media.document.mime_type = "audio/mpeg";
        message.media.document.dc_id = 0;
        File file = track.localPath != null ? new File(track.localPath) : null;
        message.media.document.size = file != null ? (int) file.length() : 0;

        TLRPC.TL_documentAttributeAudio attributeAudio = new TLRPC.TL_documentAttributeAudio();
        attributeAudio.duration = track.duration;
        attributeAudio.title = track.title != null ? track.title : "";
        attributeAudio.performer = track.artist != null ? track.artist : "";
        attributeAudio.flags |= 3;
        message.media.document.attributes.add(attributeAudio);

        TLRPC.TL_documentAttributeFilename fileName = new TLRPC.TL_documentAttributeFilename();
        fileName.file_name = file != null ? file.getName() : (track.title + ".mp3");
        message.media.document.attributes.add(fileName);

        return new MessageObject(account, message, false, true);
    }

    /** @return the {@link PrimeTrack#id} this synthetic message wraps, or null if
     *  {@code obj} isn't one of ours (a real Telegram audio message, most commonly). */
    public static Long resolveLocalTrackId(MessageObject obj) {
        if (obj == null || obj.messageOwner == null || obj.messageOwner.message == null) {
            return null;
        }
        String text = obj.messageOwner.message;
        if (!text.startsWith(LOCAL_TRACK_MARKER)) {
            return null;
        }
        try {
            return Long.parseLong(text.substring(LOCAL_TRACK_MARKER.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Builds a queue from {@code tracks} and starts playback at {@code startIndex} -
     *  the one entry point the player UI (phase 4/5) calls into. {@code loadMusic=false}
     *  in the underlying {@code setPlaylist} call is deliberate: that flag normally
     *  triggers a server fetch for "more music in this dialog," which makes no sense
     *  for a synthetic queue that was never really a dialog's music history. */
    public static void playQueue(int account, List<PrimeTrack> tracks, int startIndex) {
        if (tracks == null || tracks.isEmpty() || startIndex < 0 || startIndex >= tracks.size()) {
            return;
        }
        ArrayList<MessageObject> messageObjects = new ArrayList<>(tracks.size());
        for (PrimeTrack track : tracks) {
            messageObjects.add(buildMessageObject(account, track));
        }
        MediaController.getInstance().setPlaylist(messageObjects, messageObjects.get(startIndex), 0, false, null);
    }

    public static void playSingle(int account, PrimeTrack track) {
        List<PrimeTrack> single = new ArrayList<>(1);
        single.add(track);
        playQueue(account, single, 0);
    }
}
