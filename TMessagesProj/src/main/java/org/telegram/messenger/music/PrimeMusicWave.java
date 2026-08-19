package org.telegram.messenger.music;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * PrimeGram Music: "Моя волна" - a live, adapting session over
 * {@link PrimeMusicLibrary#getWaveRecommendations}, modeled on Yandex Music's own
 * rotor/wave protocol (reverse-engineered from a real client this session: the wave
 * isn't a precomputed list, it's re-queried after every track with the outcome of the
 * previous one folded in - dislike/skip/finish all feed back before the next pick).
 *
 * <p>A process-local singleton on purpose (mirrors {@code PrimeMusicPlayback}'s own
 * "one thing is ever playing" state) - there is only ever one wave running at a time,
 * same as there's only ever one now-playing track.
 */
public final class PrimeMusicWave {

    private static PrimeTrack current;
    private static final Set<Long> playedThisSession = new HashSet<>();
    private static List<String> moodFilter = new ArrayList<>();

    private PrimeMusicWave() {
    }

    public static List<String> getMoodFilter() {
        return new ArrayList<>(moodFilter);
    }

    /** Changing the filter mid-wave doesn't rewind the already-played history - it only
     *  narrows what {@link #next()} considers from here on, same as switching mood-energy
     *  on a live Yandex station affects the next track, not the ones already played. */
    public static void setMoodFilter(List<String> tags) {
        moodFilter = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
    }

    public static PrimeTrack current() {
        return current;
    }

    /** Whether a wave is actually running right now - used to gate auto-advance-on-end
     *  (PrimeMusicHomeView) and the "next" behavior (PrimeMusicNowPlayingActivity) so
     *  neither hijacks ordinary, non-wave playback (e.g. a track played from a
     *  playlist/liked-songs screen). */
    public static boolean isActive() {
        return current != null;
    }

    /** Starts a fresh wave - clears session history, picks a first track. Returns
     *  {@code null} if the library has nothing eligible (e.g. everything is disliked or
     *  nothing matches the active mood filter). */
    public static PrimeTrack start() {
        playedThisSession.clear();
        return advance();
    }

    /** Track finished naturally - same call as {@link #skip}, the distinction exists for
     *  callers that want to log the two differently, the wave itself treats both as
     *  "this one is done, get the next one." */
    public static PrimeTrack next() {
        return advance();
    }

    public static PrimeTrack skip() {
        return advance();
    }

    /** Marks the current track "не нравится" and immediately advances - the disliked
     *  track is skipped, not left playing, and {@link PrimeMusicLibrary#getWaveRecommendations}
     *  will never offer it again in any future wave until the user un-dislikes it from
     *  the review screen. Liking a track does NOT skip it - only a dislike forces the
     *  track to move on, since disliking it and continuing to listen to it makes no
     *  sense. */
    public static PrimeTrack dislikeCurrentAndAdvance() {
        if (current != null) {
            PrimeMusicLibrary.setDisliked(current.id, true);
        }
        return advance();
    }

    private static PrimeTrack advance() {
        if (current != null) {
            playedThisSession.add(current.id);
        }
        List<PrimeTrack> candidates = PrimeMusicLibrary.getWaveRecommendations(playedThisSession, moodFilter, 1);
        current = candidates.isEmpty() ? null : candidates.get(0);
        return current;
    }

    public static void stop() {
        current = null;
        playedThisSession.clear();
    }
}
