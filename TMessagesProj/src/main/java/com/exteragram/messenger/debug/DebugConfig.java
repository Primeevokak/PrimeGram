package com.exteragram.messenger.debug;

/**
 * PrimeGram compatibility shim for exteraGram's {@code DebugConfig}.
 *
 * <p>Plugins size their rounded sections with {@link #getSectionRadiusDp()} (reSwaga builds its
 * whole settings UI from it). Without the class the import resolved to an inert placeholder whose
 * "result" was not a number, and the first {@code dp(...)} built from it threw - taking the
 * plugin's views down with it. Same value as {@code ExteraConfig.getSectionRadiusDp()}, which
 * plugins read as the fallback.
 */
public final class DebugConfig {

    private DebugConfig() {
    }

    public static int getSectionRadiusDp() {
        return com.exteragram.messenger.ExteraConfig.getSectionRadiusDp();
    }
}
