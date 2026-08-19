package org.telegram.messenger;

import android.os.Build;

/**
 * PrimeGram: whether the embedded Flutter engine ({@link PrimeFlutterEngineHolder},
 * {@link org.telegram.ui.PrimeFlutterActivity}) can run on this device at all.
 *
 * <p>The Flutter SDK this app builds against no longer ships release native libraries for
 * 32-bit x86 (only armeabi-v7a, arm64-v8a, x86_64) - real 32-bit x86 Android hardware is
 * essentially extinct, but the app's own "afat" flavor still lists plain {@code x86} in its
 * ABI filters, so that specific combination has to be treated as "no Flutter" rather than
 * assumed away.
 */
public final class PrimeFlutterSupport {

    private PrimeFlutterSupport() {
    }

    public static boolean isSupported() {
        if (Build.SUPPORTED_ABIS == null || Build.SUPPORTED_ABIS.length == 0) {
            return false;
        }
        String primaryAbi = Build.SUPPORTED_ABIS[0];
        return "arm64-v8a".equals(primaryAbi) || "armeabi-v7a".equals(primaryAbi) || "x86_64".equals(primaryAbi);
    }
}
