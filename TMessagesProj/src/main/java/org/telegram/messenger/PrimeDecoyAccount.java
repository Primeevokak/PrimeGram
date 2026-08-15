package org.telegram.messenger;

/**
 * PrimeGram: makes account slot 0 look signed-in with the decoy persona, using
 * {@link UserConfig}'s own save path rather than hand-writing its SharedPreferences keys -
 * {@code saveConfig()}/{@code loadConfig()} are already each other's contract, so round-tripping
 * through them is what keeps this from silently breaking the next time that format changes.
 *
 * <p>Must run after {@link PrimeEmergencyWipe} has already cleared real storage, and the write
 * only has to survive until this process exits - the app's own ordinary startup sequence is what
 * actually reads it back (via the real {@code loadConfig()}) on the next cold start, which is
 * also what sets {@code configLoaded}, a field this class has no access to set directly.
 */
public final class PrimeDecoyAccount {

    private PrimeDecoyAccount() {
    }

    public static void seed(PrimeDecoyPersona persona) {
        try {
            UserConfig config = UserConfig.getInstance(0);
            config.setCurrentUser(persona.self);
            config.registeredForPush = false;
            config.saveConfig(true);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }
}
