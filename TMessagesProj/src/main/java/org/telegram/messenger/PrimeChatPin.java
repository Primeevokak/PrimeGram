package org.telegram.messenger;

import android.content.Context;

/**
 * PrimeGram: per-chat PIN lock - lets a user PIN-protect one specific chat/group/channel,
 * completely independent of the app-wide startup PIN ({@link PrimePinSession}). Each
 * locked dialog gets its own {@link PrimePinVault} (own sealed file, own Keystore alias,
 * own AAD - see that class's own doc on scoping), reached here via a scope string of
 * {@code "dlg" + dialogId}.
 *
 * <p>{@link #isLocked} is a cheap presence-only flag check (same convention
 * {@link GreyZone} uses for its own per-dialog settings) so a screen that needs to know
 * "is this dialog gated at all" doesn't have to touch the Keystore/vault file just to find
 * out - only actually entering a PIN opens the real vault.
 */
public final class PrimeChatPin {

    private static final String FLAG_PREFIX = "prime_chat_pin_";

    private PrimeChatPin() {
    }

    private static String scopeFor(long dialogId) {
        return "dlg" + dialogId;
    }

    // Asked for every row of the chat list on every bind; a prefs read plus a string concat each
    // time. The set of locked ids is read once and kept in step by setLockedFlag, its only writer.
    private static volatile java.util.Set<Long> lockedIds;

    private static java.util.Set<Long> lockedIds() {
        java.util.Set<Long> ids = lockedIds;
        if (ids == null) {
            synchronized (PrimeChatPin.class) {
                ids = lockedIds;
                if (ids == null) {
                    ids = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
                    for (java.util.Map.Entry<String, ?> entry : MessagesController.getGlobalMainSettings().getAll().entrySet()) {
                        if (entry.getKey().startsWith(FLAG_PREFIX) && Boolean.TRUE.equals(entry.getValue())) {
                            try {
                                ids.add(Long.parseLong(entry.getKey().substring(FLAG_PREFIX.length())));
                            } catch (NumberFormatException ignore) {
                            }
                        }
                    }
                    lockedIds = ids;
                }
            }
        }
        return ids;
    }

    public static boolean isLocked(long dialogId) {
        final java.util.Set<Long> ids = lockedIds();
        return !ids.isEmpty() && ids.contains(dialogId);
    }

    private static void setLockedFlag(long dialogId, boolean locked) {
        if (locked) {
            lockedIds().add(dialogId);
        } else {
            lockedIds().remove(dialogId);
        }
        android.content.SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
        if (locked) {
            editor.putBoolean(FLAG_PREFIX + dialogId, true).apply();
        } else {
            editor.remove(FLAG_PREFIX + dialogId).apply();
        }
    }

    /** {@code acceptSoftwareProtection} mirrors {@link PrimePinVault#enroll} exactly -
     *  call with {@code false} first; a {@code SOFTWARE_CONSENT_REQUIRED} result means
     *  this device has no StrongBox/TEE, and the caller must show the same kind of
     *  explicit warning {@link org.telegram.ui.PrimePinGateActivity} shows for the
     *  app-wide PIN before retrying with {@code true}. */
    public static PrimePinVault.EnrollmentResult enroll(Context context, long dialogId, char[] pin, boolean acceptSoftwareProtection) {
        PrimePinVault vault = new PrimePinVault(context, scopeFor(dialogId));
        PrimePinVault.EnrollmentResult result = vault.enroll(pin, acceptSoftwareProtection);
        if (result == PrimePinVault.EnrollmentResult.ENROLLED) {
            setLockedFlag(dialogId, true);
        }
        return result;
    }

    public static PrimePinVault.VerificationResult verify(Context context, long dialogId, char[] pin) {
        return new PrimePinVault(context, scopeFor(dialogId)).verify(pin);
    }

    /** Verifies {@code pin} first - removing a chat's PIN requires knowing it, mirroring
     *  the app-wide PIN's own disable-requires-PIN precedent ({@link PrimePinVault#disable}).
     *  Deletes that dialog's sealed file and Keystore alias only - every other dialog's
     *  vault (and the app-wide one) is untouched. */
    public static PrimePinVault.VerificationResult remove(Context context, long dialogId, char[] pin) {
        PrimePinVault vault = new PrimePinVault(context, scopeFor(dialogId));
        PrimePinVault.VerificationResult result = vault.disable(pin);
        if (result.status == PrimePinVault.VerificationStatus.ACCEPTED
                || result.status == PrimePinVault.VerificationStatus.EMERGENCY) {
            setLockedFlag(dialogId, false);
        }
        return result;
    }
}
