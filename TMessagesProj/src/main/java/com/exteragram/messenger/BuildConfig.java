package com.exteragram.messenger;

import org.telegram.messenger.BuildVars;

/**
 * PrimeGram: the version constants plugins read from exteraGram's BuildConfig to report the
 * client version or gate features. Values are PrimeGram's own - a plugin comparing them against
 * exteraGram releases learns it is not on exteraGram, which is true.
 */
public final class BuildConfig {
    public static final boolean DEBUG = false;
    public static final String APPLICATION_ID = "org.telegram.messenger.prime";
    public static final String BUILD_TYPE = "release";
    public static final String VERSION_NAME = BuildVars.BUILD_VERSION_STRING;
    public static final int VERSION_CODE = BuildVars.BUILD_VERSION;

    private BuildConfig() {
    }
}
