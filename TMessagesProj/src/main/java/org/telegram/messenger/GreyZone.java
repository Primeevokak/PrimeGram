package org.telegram.messenger;

import android.content.SharedPreferences;

/**
 * PrimeGram "grey zone": opt-in features that override protections chosen by the *other*
 * side of a conversation (screenshot blocking, forward/save restrictions) or that hide the
 * user's own activity signals from their contacts.
 *
 * <p>Every one of these is off by default and unreachable until the user has explicitly
 * accepted the warning ({@link #isAccepted()}). {@link #isEnabled} deliberately requires
 * both the acceptance flag and the individual toggle, so nothing here can be switched on
 * by accident, by a restored backup, or by a stale preference from an older build.
 */
public class GreyZone {

    /** Screenshot one-time media, secret chats and copy-protected chats. */
    public static final String ALLOW_SCREENSHOTS = "grey_allow_screenshots";
    /** Forward / save / copy from chats whose owner disabled it. */
    public static final String BYPASS_NOFORWARDS = "grey_bypass_noforwards";
    /** Don't send read receipts. */
    public static final String GHOST_DONT_READ = "grey_ghost_dont_read";
    /** Don't send the "typing…" indicator. */
    public static final String GHOST_DONT_TYPING = "grey_ghost_dont_typing";
    /** Don't report yourself as online. */
    public static final String GHOST_DONT_ONLINE = "grey_ghost_dont_online";
    /** Keep a local copy of messages other people delete. */
    public static final String SAVE_DELETED = "grey_save_deleted";
    /** Show that someone who hides their online status is typing in a group shared with us. */
    public static final String ACTIVITY_PEEK = "grey_activity_peek";
    /** Emulate Telegram Premium locally for this account - was an unconditional `return true`,
     *  moved here because it is exactly this feature's shape: something that makes the app behave
     *  as if a restriction (Telegram's own, this time, not a chat partner's) doesn't apply. */
    public static final String LOCAL_PREMIUM = "grey_local_premium";

    private static final String KEY_LOCAL_PREMIUM_MIGRATED = "grey_local_premium_migrated";

    private static final String KEY_ACCEPTED = "grey_zone_accepted";

    private static SharedPreferences prefs() {
        return MessagesController.getGlobalMainSettings();
    }

    /** True once the user has read and accepted the warning screen. */
    public static boolean isAccepted() {
        return prefs().getBoolean(KEY_ACCEPTED, false);
    }

    public static void setAccepted(boolean accepted) {
        SharedPreferences.Editor editor = prefs().edit().putBoolean(KEY_ACCEPTED, accepted);
        if (!accepted) {
            // Revoking consent must actually disable everything, not just hide the screen.
            editor.putBoolean(ALLOW_SCREENSHOTS, false)
                    .putBoolean(BYPASS_NOFORWARDS, false)
                    .putBoolean(GHOST_DONT_READ, false)
                    .putBoolean(GHOST_DONT_TYPING, false)
                    .putBoolean(GHOST_DONT_ONLINE, false)
                    .putBoolean(SAVE_DELETED, false)
                    .putBoolean(ACTIVITY_PEEK, false)
                    .putBoolean(LOCAL_PREMIUM, false);
        }
        editor.apply();
    }

    /**
     * The single check every feature site must use. Returns false unless the user both
     * accepted the warning and turned this specific feature on.
     */
    public static boolean isEnabled(String key) {
        try {
            SharedPreferences p = prefs();
            return p.getBoolean(KEY_ACCEPTED, false) && p.getBoolean(key, false);
        } catch (Throwable t) {
            // Called from UI-critical paths; failing closed is the only safe answer.
            return false;
        }
    }

    public static void setEnabled(String key, boolean enabled) {
        prefs().edit().putBoolean(key, enabled).apply();
        if (enabled && GHOST_DONT_ONLINE.equals(key)) {
            MessagesController.primeClampOwnOnlineStatus();
        }
    }

    /**
     * Ghost mode as a single switch: on means every ghost sub-option is on. Reported as on
     * only when all of them are, so the sidebar indicator can't claim you're hidden while
     * one of the signals is still leaking.
     */
    public static boolean isGhostModeOn() {
        return isEnabled(GHOST_DONT_READ) && isEnabled(GHOST_DONT_TYPING) && isEnabled(GHOST_DONT_ONLINE);
    }

    public static void setGhostMode(boolean on) {
        if (!isAccepted()) {
            return;
        }
        prefs().edit()
                .putBoolean(GHOST_DONT_READ, on)
                .putBoolean(GHOST_DONT_TYPING, on)
                .putBoolean(GHOST_DONT_ONLINE, on)
                .apply();
        if (on) {
            MessagesController.primeClampOwnOnlineStatus();
        }
    }

    /** Convenience for the most-used checks. */
    public static boolean allowScreenshots() {
        return isEnabled(ALLOW_SCREENSHOTS);
    }

    public static boolean bypassNoForwards() {
        return isEnabled(BYPASS_NOFORWARDS);
    }

    /**
     * Ghost mode stops us announcing ourselves as online, so everyone else sees a "last seen"
     * time. The client, however, used to keep printing a hardcoded "online" for our own user,
     * which made the mode look broken. When this is true the UI must render our own status the
     * same way it renders anybody else's — from {@code user.status}, which the server keeps in
     * sync via updateUserStatus — so what we see is what our contacts see.
     */
    public static boolean hideOwnOnline() {
        return isEnabled(GHOST_DONT_ONLINE);
    }

    public static boolean localPremiumEnabled() {
        return isEnabled(LOCAL_PREMIUM);
    }

    /**
     * One-time migration for the version that moved local Premium behind this toggle - it used to
     * be an unconditional {@code return true}, so someone who already had the app installed never
     * asked for it to turn off. Whoever is already running the app when this first executes keeps
     * exactly the behavior they had a moment ago; only an install that starts fresh from here on
     * gets the new off-by-default, ask-first behavior. Safe to call on every startup - the marker
     * makes every call after the first a no-op, including for someone who deliberately turns the
     * toggle back off afterward.
     */
    public static void migrateLocalPremiumIfNeeded() {
        final SharedPreferences p = prefs();
        if (p.getBoolean(KEY_LOCAL_PREMIUM_MIGRATED, false)) {
            return;
        }
        final boolean existingUser = p.getInt("primegram_app_launch_count", 0) > 1;
        final SharedPreferences.Editor editor = p.edit().putBoolean(KEY_LOCAL_PREMIUM_MIGRATED, true);
        if (existingUser) {
            editor.putBoolean(KEY_ACCEPTED, true).putBoolean(LOCAL_PREMIUM, true);
        }
        editor.apply();
    }
}
