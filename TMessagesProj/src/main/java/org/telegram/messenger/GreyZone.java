package org.telegram.messenger;

import android.content.SharedPreferences;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.tl.TL_account;

import java.util.Calendar;

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
    /** Don't mark stories as viewed / read. */
    public static final String GHOST_NO_READ_STORIES = "grey_ghost_no_read_stories";
    /** After any action that would let the server infer we're online anyway (sending a
     *  message, an "always read"/"always type" per-dialog exception, etc.), immediately
     *  fire a follow-up offline status to correct it - ported from exteraGram's re_extera
     *  ghost mode, which does this at the connection layer for every online-inferring request. */
    public static final String GHOST_IMMEDIATE_OFFLINE = "grey_ghost_immediate_offline";

    private static final String KEY_LOCAL_PREMIUM_MIGRATED = "grey_local_premium_migrated";

    private static final String KEY_SCHEDULE_ENABLED = "grey_schedule_enabled";
    private static final String KEY_SCHEDULE_START_MIN = "grey_schedule_start_min";
    private static final String KEY_SCHEDULE_END_MIN = "grey_schedule_end_min";

    private static final String DLG_READ_PREFIX = "grey_dlg_read_";
    private static final String DLG_TYPING_PREFIX = "grey_dlg_typing_";

    /** Per-dialog override: falls back to the global toggle. */
    public static final int MODE_DEFAULT = 0;
    public static final int MODE_ALWAYS = 1;
    public static final int MODE_NEVER = -1;

    private static final String KEY_ACCEPTED = "grey_zone_accepted";
    private static final String KEY_DEBUG_VISIBLE = "grey_zone_debug_visible";

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
                    .putBoolean(LOCAL_PREMIUM, false)
                    .putBoolean(GHOST_NO_READ_STORIES, false)
                    .putBoolean(GHOST_IMMEDIATE_OFFLINE, false)
                    .putBoolean(KEY_SCHEDULE_ENABLED, false);
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
            // The Grey Zone entry point itself just became hidden by default too. Someone who
            // already had it open and configured should not lose their way back into it - only a
            // fresh install gets the new "find it in the debug menu first" behavior.
            if (p.getBoolean(KEY_ACCEPTED, false)) {
                editor.putBoolean(KEY_DEBUG_VISIBLE, true);
            }
        }
        editor.apply();
    }

    /** Whether the Grey Zone entry point shows in settings at all - hidden by default so the
     *  screen isn't sitting in plain view of anyone glancing at the app; reachable through the
     *  debug menu (SettingsActivity, long-press the version number) as a deliberate extra step,
     *  not a discoverability accident. */
    public static boolean isDebugVisible() {
        return prefs().getBoolean(KEY_DEBUG_VISIBLE, false);
    }

    public static void setDebugVisible(boolean visible) {
        prefs().edit().putBoolean(KEY_DEBUG_VISIBLE, visible).apply();
    }

    // ---- Per-dialog reading/typing exceptions (ported from exteraGram's re_extera) ----
    // Each dialog can override the global ghost toggle: MODE_DEFAULT follows the global
    // switch, MODE_ALWAYS behaves as if ghost mode were off for that one dialog, MODE_NEVER
    // behaves as if it were on regardless of the global switch.

    public static int getDialogReadingMode(long dialogId) {
        return prefs().getInt(DLG_READ_PREFIX + dialogId, MODE_DEFAULT);
    }

    public static void setDialogReadingMode(long dialogId, int mode) {
        if (mode == MODE_DEFAULT) {
            prefs().edit().remove(DLG_READ_PREFIX + dialogId).apply();
        } else {
            prefs().edit().putInt(DLG_READ_PREFIX + dialogId, mode).apply();
        }
    }

    public static int getDialogTypingMode(long dialogId) {
        return prefs().getInt(DLG_TYPING_PREFIX + dialogId, MODE_DEFAULT);
    }

    public static void setDialogTypingMode(long dialogId, int mode) {
        if (mode == MODE_DEFAULT) {
            prefs().edit().remove(DLG_TYPING_PREFIX + dialogId).apply();
        } else {
            prefs().edit().putInt(DLG_TYPING_PREFIX + dialogId, mode).apply();
        }
    }

    /** True if the read receipt for this dialog should be suppressed right now. */
    public static boolean shouldGhostRead(long dialogId) {
        if (!isAccepted()) {
            return false;
        }
        int mode = getDialogReadingMode(dialogId);
        if (mode == MODE_ALWAYS) {
            return false;
        }
        if (mode == MODE_NEVER) {
            return true;
        }
        return isEnabled(GHOST_DONT_READ) && isScheduleActiveNow();
    }

    /** True if the "typing…" indicator for this dialog should be suppressed right now. */
    public static boolean shouldGhostTyping(long dialogId) {
        if (!isAccepted()) {
            return false;
        }
        int mode = getDialogTypingMode(dialogId);
        if (mode == MODE_ALWAYS) {
            return false;
        }
        if (mode == MODE_NEVER) {
            return true;
        }
        return isEnabled(GHOST_DONT_TYPING) && isScheduleActiveNow();
    }

    /** True if story views should not be reported right now. */
    public static boolean shouldGhostStories() {
        return isEnabled(GHOST_NO_READ_STORIES) && isScheduleActiveNow();
    }

    /** True if the "don't report online" toggle should apply right now. */
    public static boolean shouldGhostOnline() {
        return isEnabled(GHOST_DONT_ONLINE) && isScheduleActiveNow();
    }

    // ---- Schedule: restrict ghost mode to a time-of-day window ----

    public static boolean isScheduleEnabled() {
        return prefs().getBoolean(KEY_SCHEDULE_ENABLED, false);
    }

    public static void setScheduleEnabled(boolean enabled) {
        prefs().edit().putBoolean(KEY_SCHEDULE_ENABLED, enabled).apply();
    }

    /** Minutes since midnight, [0, 1439]. */
    public static int getScheduleStartMinute() {
        return prefs().getInt(KEY_SCHEDULE_START_MIN, 0);
    }

    /** Minutes since midnight, [0, 1439]. */
    public static int getScheduleEndMinute() {
        return prefs().getInt(KEY_SCHEDULE_END_MIN, 1439);
    }

    public static void setSchedule(int startMinute, int endMinute) {
        prefs().edit()
                .putInt(KEY_SCHEDULE_START_MIN, startMinute)
                .putInt(KEY_SCHEDULE_END_MIN, endMinute)
                .apply();
    }

    /** True if ghost behavior should be active right now given the schedule setting. Handles
     *  windows that wrap past midnight (e.g. 22:00-07:00). Always true when no schedule is set. */
    public static boolean isScheduleActiveNow() {
        if (!isScheduleEnabled()) {
            return true;
        }
        Calendar cal = Calendar.getInstance();
        int nowMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        int start = getScheduleStartMinute();
        int end = getScheduleEndMinute();
        if (start <= end) {
            return nowMinute >= start && nowMinute <= end;
        } else {
            return nowMinute >= start || nowMinute <= end;
        }
    }

    // ---- Immediate offline: after an action that leaks "online" anyway, correct it ----

    public static boolean immediateOfflineEnabled() {
        return isEnabled(GHOST_IMMEDIATE_OFFLINE);
    }

    /**
     * Fires a follow-up {@code account.updateStatus(offline=true)} to counter the server's
     * online-inference from a request we just sent (e.g. a message send, or a per-dialog
     * "always read/type" exception). No-op unless both the ghost-online toggle and this
     * specific option are on, and the schedule (if any) is currently active.
     */
    public static void triggerImmediateOfflineIfNeeded(int account) {
        if (!shouldGhostOnline() || !immediateOfflineEnabled()) {
            return;
        }
        TL_account.updateStatus req = new TL_account.updateStatus();
        req.offline = true;
        ConnectionsManager.getInstance(account).sendRequest(req, (RequestDelegate) (response, error) -> {
        });
    }
}
