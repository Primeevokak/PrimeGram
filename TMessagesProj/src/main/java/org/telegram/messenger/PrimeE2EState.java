package org.telegram.messenger;

import android.content.SharedPreferences;

/**
 * PrimeGram E2E: per-dialog opt-in state, following {@link GreyZone}'s own storage
 * convention exactly (flat keys in {@link MessagesController#getGlobalMainSettings()},
 * default state stored as key *absence*, not a literal value).
 */
public final class PrimeE2EState {

    /** No session for this dialog - the default, unstated state. */
    static final int MODE_NONE = 0;
    /** This device sent a bundle-request and is waiting for the other side's reply. */
    static final int MODE_REQUESTED = 1;
    /** This device received a bundle-request and is waiting for the local user to
     *  confirm/cancel - see {@link PrimeE2ESession.Mode#INCOMING_PENDING}. */
    static final int MODE_INCOMING_PENDING = 3;
    /** Handshake complete - messages to/from this dialog are encrypted. */
    static final int MODE_ESTABLISHED = 2;

    private static final String MODE_PREFIX = "prime_e2e_dlg_mode_";
    private static final String VERIFIED_PREFIX = "prime_e2e_dlg_verified_";

    private PrimeE2EState() {
    }

    private static SharedPreferences prefs() {
        return MessagesController.getGlobalMainSettings();
    }

    static int getMode(long dialogId) {
        return prefs().getInt(MODE_PREFIX + dialogId, MODE_NONE);
    }

    static void setMode(long dialogId, int mode) {
        if (mode == MODE_NONE) {
            prefs().edit().remove(MODE_PREFIX + dialogId).remove(VERIFIED_PREFIX + dialogId).apply();
        } else {
            prefs().edit().putInt(MODE_PREFIX + dialogId, mode).apply();
        }
    }

    /** Advisory only - see {@link PrimeE2EManager}'s safety-number flow. Never gates
     *  send/receive, matches how Signal's own safety-number UX works. */
    public static boolean isVerified(long dialogId) {
        return prefs().getBoolean(VERIFIED_PREFIX + dialogId, false);
    }

    public static void setVerified(long dialogId, boolean verified) {
        if (verified) {
            prefs().edit().putBoolean(VERIFIED_PREFIX + dialogId, true).apply();
        } else {
            prefs().edit().remove(VERIFIED_PREFIX + dialogId).apply();
        }
    }
}
