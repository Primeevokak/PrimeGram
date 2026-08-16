package org.telegram.messenger;

/** PrimeGram: best-effort zeroing of secret material before it's dropped. The JIT is free
 *  to elide a dead store, so this is a mitigation, not a guarantee - the real security
 *  boundary is the process's address space and the OS, not this loop. Still worth doing:
 *  it shrinks the window a heap dump could catch a PIN or derived key in. */
final class PrimeSecretWiper {

    private PrimeSecretWiper() {
    }

    static void wipe(byte[] data) {
        if (data != null) {
            java.util.Arrays.fill(data, (byte) 0);
        }
    }

    static void wipe(char[] data) {
        if (data != null) {
            java.util.Arrays.fill(data, '\0');
        }
    }
}
