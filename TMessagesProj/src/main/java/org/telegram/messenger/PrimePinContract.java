package org.telegram.messenger;

/** PrimeGram: shared PIN validation rules + the retry-delay curve, kept in one place so the
 *  vault, the gate UI, and any future caller agree on the same numbers. */
public final class PrimePinContract {

    public static final int MIN_PIN_LENGTH = 4;
    public static final int MAX_PIN_LENGTH = 6;

    private PrimePinContract() {
    }

    public static boolean isValidPin(char[] pin) {
        if (pin == null || pin.length < MIN_PIN_LENGTH || pin.length > MAX_PIN_LENGTH) {
            return false;
        }
        for (char c : pin) {
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}
