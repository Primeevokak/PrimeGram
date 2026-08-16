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
            // PrimeGram: NOT config.saveConfig(true) - that method defers its actual write
            // through NotificationCenter.doOnIdle(), which silently queues the runnable instead
            // of running it whenever an animation happens to be mid-flight (routine right after
            // navigating away from the PIN screen), and nothing ever drains that queue before
            // the caller (PrimeEmergencyWipe) calls System.exit(0) a few lines later. That is
            // exactly what produced "kicked to the real login screen instead of the decoy" -
            // setCurrentUser() took effect in memory, but it never reached disk. Writing the
            // same "user"/"registeredForPush" keys loadConfig() reads back, directly and with
            // commit() (blocking, unlike apply()) instead of going through that queue, is what
            // actually guarantees the seed survives the process exit that follows immediately.
            org.telegram.tgnet.SerializedData data = new org.telegram.tgnet.SerializedData(persona.self.getObjectSize());
            persona.self.serializeToStream(data);
            String encoded = android.util.Base64.encodeToString(data.toByteArray(), android.util.Base64.DEFAULT);
            data.cleanup();
            config.getPreferences().edit()
                    .putString("user", encoded)
                    .putBoolean("registeredForPush", false)
                    .commit();
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }
}
