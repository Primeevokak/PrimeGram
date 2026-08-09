package org.telegram.messenger.blocks;

/**
 * PrimeGram Blocks: describes one parameter a {@link BlockType} takes, so the editor
 * (Phase C) knows which inline widget to render without any block-specific UI code.
 */
public final class ParamSpec {

    public enum Kind {
        ENUM, NUMBER, TEXT, BOOLEAN, CHAT_REFERENCE
    }

    public final String key;
    public final Kind kind;
    /** Only meaningful for {@link Kind#ENUM}: the choices, in display order. */
    public final String[] enumValues;
    public final Object defaultValue;
    public final String label;
    /** Only meaningful for {@link Kind#NUMBER}. */
    public final double min;
    public final double max;

    public ParamSpec(String key, Kind kind, String label, Object defaultValue) {
        this(key, kind, label, defaultValue, null, 0, 0);
    }

    public ParamSpec(String key, Kind kind, String label, Object defaultValue,
                      String[] enumValues, double min, double max) {
        this.key = key;
        this.kind = kind;
        this.label = label;
        this.defaultValue = defaultValue;
        this.enumValues = enumValues;
        this.min = min;
        this.max = max;
    }
}
