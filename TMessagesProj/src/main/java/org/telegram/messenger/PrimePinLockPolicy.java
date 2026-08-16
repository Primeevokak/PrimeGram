package org.telegram.messenger;

/** PrimeGram: when an unlocked PIN session expires again. Matched by storage key, not
 *  ordinal, so a future reorder of this enum can't silently change what a saved
 *  preference means. */
public enum PrimePinLockPolicy {

    ON_MINIMIZE("on_minimize"),
    ON_SCREEN_LOCK("on_screen_lock"),
    INACTIVITY_10M("inactivity_10m"),
    INACTIVITY_60M("inactivity_60m"),
    ON_START("on_start");

    public final String storageKey;

    PrimePinLockPolicy(String storageKey) {
        this.storageKey = storageKey;
    }

    public static PrimePinLockPolicy getDefault() {
        return ON_SCREEN_LOCK;
    }

    public static PrimePinLockPolicy fromStorageKey(String key, PrimePinLockPolicy fallback) {
        if (key != null) {
            for (PrimePinLockPolicy p : values()) {
                if (p.storageKey.equals(key)) {
                    return p;
                }
            }
        }
        return fallback;
    }

    boolean locksOnBackground() {
        return this == ON_MINIMIZE;
    }

    boolean locksOnScreenOff() {
        return this == ON_SCREEN_LOCK;
    }

    /** 0 = no inactivity-based lock for this policy. */
    long inactivityMillis() {
        switch (this) {
            case INACTIVITY_10M:
                return 10 * 60_000L;
            case INACTIVITY_60M:
                return 60 * 60_000L;
            default:
                return 0L;
        }
    }

    /** ON_START is the weakest policy: PIN is asked once per launch, but the unlocked
     *  session is still capped at 24h so it can't silently persist forever. 0 = no cap for
     *  the other policies (they're bounded by inactivity/background/screen-off instead). */
    long sessionMillis() {
        return this == ON_START ? 24 * 60 * 60_000L : 0L;
    }
}
