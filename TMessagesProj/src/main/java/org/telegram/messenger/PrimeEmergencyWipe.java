package org.telegram.messenger;

import android.content.Context;

/**
 * PrimeGram: emergency-PIN destruction sequence.
 *
 * <p>STUB for Phase 1 (the PIN gate needs a call target for the EMERGENCY verification
 * result, but the actual wipe + decoy machinery is Phase 2 work). Currently just disables
 * the local PIN gate so a real emergency-PIN entry doesn't leave the app stuck unable to
 * unlock - it does NOT yet log out accounts, wipe storage, or arm a decoy. Do not treat
 * this as the finished feature; {@link PrimePinGateActivity}'s emergency-PIN codepath
 * silently under-delivers until Phase 2 replaces this body.
 */
public final class PrimeEmergencyWipe {

    private PrimeEmergencyWipe() {
    }

    /** Must be called off the UI thread - Phase 2 will do real file/network/keystore work here. */
    public static void run(Context context) {
        try {
            PrimePinSession.setEnabled(false);
        } catch (Throwable ignored) {
        }
    }
}
