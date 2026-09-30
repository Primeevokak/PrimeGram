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
    /** Tracks skipped or disliked in this session - their artists count against a pick. */
    private static final Set<Long> skippedThisSession = new HashSet<>();
    private static final java.util.Random random = new java.util.Random();

    // Tuning of the pick (see pickNext).
    private static final int TOP_K = 12;
    private static final double TEMPERATURE = 0.55;
    private static final double EXPLORATION = 0.6;
    private static final double SAME_ARTIST_AS_KEPT = 0.45;
    private static final double SAME_GENRE_AS_KEPT = 0.25;
    private static final double SAME_ARTIST_AS_SKIPPED = -1.2;
    private static List<String> moodFilter = new ArrayList<>();

    /** Ordered play history for this session, separate from {@code playedThisSession}
     *  (which only tracks *what to exclude* from future recommendations). {@code
     *  historyIndex} points at {@code current} within {@code history} - {@link #previous()}
     *  steps it back without re-querying recommendations or touching the exclusion set,
     *  {@link #advance()} steps it forward, dropping any stale "future" beyond it first
     *  (going back then hitting next resumes live picking rather than replaying). */
    private static final List<PrimeTrack> history = new ArrayList<>();
    private static int historyIndex = -1;

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
        skippedThisSession.clear();
        history.clear();
        historyIndex = -1;
        return advance();
    }

    /** Whether {@link #previous()} has anything to step back to. */
    public static boolean hasPrevious() {
        return historyIndex > 0;
    }

    /** Steps back to the prior track in this session's history - unlike {@link #next()}
     *  this doesn't re-query recommendations, it's real "go back" over what already
     *  played, same as Namida's queue-position-minus-one. Does not touch
     *  {@code playedThisSession}: the track was already excluded from future picks when
     *  it was first played, and revisiting it doesn't un-exclude it. */
    public static PrimeTrack previous() {
        if (!hasPrevious()) {
            return null;
        }
        historyIndex--;
        current = history.get(historyIndex);
        return current;
    }

    /** Track finished naturally - same call as {@link #skip}, the distinction exists for
     *  callers that want to log the two differently, the wave itself treats both as
     *  "this one is done, get the next one." */
    public static PrimeTrack next() {
        return advance();
    }

    /** The user moved on before the track ended - unlike {@link #next}, this counts against
     *  more of the same artist for the rest of the session. */
    public static PrimeTrack skip() {
        if (current != null) {
            skippedThisSession.add(current.id);
        }
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
            skippedThisSession.add(current.id);
        }
        return advance();
    }

    private static PrimeTrack advance() {
        if (current != null) {
            playedThisSession.add(current.id);
        }
        current = pickNext();
        if (current != null) {
            while (history.size() > historyIndex + 1) {
                history.remove(history.size() - 1);
            }
            history.add(current);
            historyIndex = history.size() - 1;
        }
        return current;
    }

    /**
     * The next track. Scores come from the library's long-term model (what the user finishes,
     * skips, likes, keeps coming back to); on top of that, for this session only:
     * <ul>
     *   <li>more like what was kept (same artist or genre as tracks played through), less like
     *       what was skipped;</li>
     *   <li>a bonus for tracks played rarely, so the wave does not replay the same favourites;</li>
     *   <li>no artist twice in a row while there is anything else to play;</li>
     *   <li>and a weighted random choice among the best few rather than always the single top
     *       score, which made every wave start with the same track in the same order.</li>
     * </ul>
     */
    private static PrimeTrack pickNext() {
        final List<PrimeMusicLibrary.ScoredTrack> candidates = PrimeMusicLibrary.getWaveCandidates(playedThisSession, moodFilter);
        if (candidates.isEmpty()) {
            return null;
        }
        final Set<String> keptArtists = new HashSet<>();
        final Set<String> keptGenres = new HashSet<>();
        final Set<String> skippedArtists = new HashSet<>();
        for (PrimeTrack t : history) {
            if (skippedThisSession.contains(t.id)) {
                if (t.artist != null) skippedArtists.add(t.artist);
            } else {
                if (t.artist != null) keptArtists.add(t.artist);
                if (t.genre != null && !t.genre.isEmpty()) keptGenres.add(t.genre);
            }
        }
        final String lastArtist = current != null ? current.artist : null;

        final List<PrimeTrack> pool = new ArrayList<>();
        final List<Double> weights = new ArrayList<>();
        for (int pass = 0; pass < 2 && pool.isEmpty(); pass++) {
            for (PrimeMusicLibrary.ScoredTrack c : candidates) {
                final PrimeTrack t = c.track;
                // Pass 0 keeps the previous artist out; pass 1 lets it back in if that was all.
                if (pass == 0 && lastArtist != null && !PrimeMusicArtistLabel.isUnknown(lastArtist) && lastArtist.equals(t.artist)) {
                    continue;
                }
                double score = c.score + EXPLORATION / Math.sqrt(1 + PrimeMusicLibrary.lastPlayCount(t.id));
                if (t.artist != null && !PrimeMusicArtistLabel.isUnknown(t.artist)) {
                    if (skippedArtists.contains(t.artist)) {
                        score += SAME_ARTIST_AS_SKIPPED;
                    } else if (keptArtists.contains(t.artist)) {
                        score += SAME_ARTIST_AS_KEPT;
                    }
                }
                if (t.genre != null && keptGenres.contains(t.genre)) {
                    score += SAME_GENRE_AS_KEPT;
                }
                pool.add(t);
                weights.add(score);
            }
        }
        // Best TOP_K by adjusted score, then a softmax draw among them.
        final Integer[] order = new Integer[pool.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> Double.compare(weights.get(b), weights.get(a)));
        final int k = Math.min(TOP_K, order.length);
        final double best = weights.get(order[0]);
        final double[] p = new double[k];
        double sum = 0;
        for (int i = 0; i < k; i++) {
            p[i] = Math.exp((weights.get(order[i]) - best) / TEMPERATURE);
            sum += p[i];
        }
        double r = random.nextDouble() * sum;
        for (int i = 0; i < k; i++) {
            r -= p[i];
            if (r <= 0) {
                return pool.get(order[i]);
            }
        }
        return pool.get(order[0]);
    }

    /** Whether the mood filter leaves anything to play at all - for telling the user why the
     *  wave did not start instead of it silently doing nothing. */
    public static boolean hasCandidates(List<String> mood) {
        return !PrimeMusicLibrary.getWaveCandidates(null, mood).isEmpty();
    }

    public static void stop() {
        current = null;
        playedThisSession.clear();
        skippedThisSession.clear();
        history.clear();
        historyIndex = -1;
    }
}
