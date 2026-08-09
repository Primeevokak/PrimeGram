package org.telegram.messenger.blocks;

import java.util.Collections;
import java.util.List;

/**
 * PrimeGram Blocks: one entry in the closed, built-in {@link BlockRegistry} - the app-shipped
 * definition of a single Scratch-like block a `.pr` script can use.
 *
 * <p>There is deliberately no way to register a {@code BlockType} from outside this APK (no
 * reflection, no classpath scanning, no plugin hook): every id a `.pr` file can reference is one
 * of the classes that shipped in this build. That closed-set property is what lets the install
 * sheet make the true claim "this script can't do anything beyond the blocks listed here" - see
 * {@code PrimeBlockInstallDialog}.
 */
public final class BlockType {

    public enum Category {
        TRIGGER, CONDITION, WAIT, ACTION
    }

    /** Stable, namespaced id, e.g. {@code "condition.language_is"}. Never renamed once shipped. */
    public final String id;
    public final Category category;
    /** Human label for the palette/editor, e.g. "Language is...". */
    public final String label;
    /** One-line description shown in the add-block palette. */
    public final String description;
    /**
     * Minimum PrimeGram version this exact block behavior requires, e.g. {@code ">=12.9.0"}.
     * Checked with {@link org.telegram.messenger.plugins.PluginVersions#satisfies}. A block whose
     * id has existed since v1 but grew a new required capability later bumps this, rather than
     * becoming a new id - that is the "too new" half of {@link BlockRegistry#checkCompatibility}.
     */
    public final String minAppVersion;
    public final List<ParamSpec> params;

    public BlockType(String id, Category category, String label, String description,
                      String minAppVersion, List<ParamSpec> params) {
        this.id = id;
        this.category = category;
        this.label = label;
        this.description = description;
        this.minAppVersion = minAppVersion;
        this.params = params == null ? Collections.emptyList() : params;
    }
}
