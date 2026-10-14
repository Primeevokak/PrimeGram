package com.exteragram.messenger.pillstack.core;

/**
 * exteraGram API: the ids its own built-in pills use. PrimeGram ships none of those pills (see
 * {@link PillRegistry}), but the ids stay reserved so a plugin choosing its own id never collides
 * with what an exteraGram user might have saved.
 */
public enum PillType {
    WEATHER(1),
    GRAM(2),
    BTC(3),
    USD(4),
    CACHE(5),
    PROXY(6);

    private final int id;

    PillType(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }
}
