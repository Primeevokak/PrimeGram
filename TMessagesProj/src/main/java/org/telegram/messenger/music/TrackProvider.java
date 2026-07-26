package org.telegram.messenger.music;

/**
 * A single music-platform backend: knows how to fetch the user's currently
 * playing track. Implementations do blocking network I/O — always call
 * {@link #getTrack()} off the UI thread.
 */
public interface TrackProvider {

    /** Fetches the current track, or {@link Track#inactive()} if nothing is playing / the call failed. */
    Track getTrack();

    /** Whether {@link #getTrack()}'s result can plausibly be downloaded as audio (directly or via song.link + Cobalt). */
    default boolean canDownloadTrack() {
        return true;
    }

    /** True if this provider is configured (has a token/username) and ready to be queried. */
    boolean isConfigured();
}
