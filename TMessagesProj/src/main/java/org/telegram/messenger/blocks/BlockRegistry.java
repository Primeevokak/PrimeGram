package org.telegram.messenger.blocks;

import org.telegram.messenger.plugins.PluginVersions;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * PrimeGram Blocks: the closed table of every {@link BlockType} this build knows about.
 *
 * <p>Static, hand-populated, no dynamic discovery - this is the concrete form of the security
 * boundary the whole `.pr` system is built around (see the roadmap's "PrimeGram Blocks" plan,
 * §2.1): a `.pr` file cannot make the app do anything beyond what a class already in this
 * registry, already shipped in this APK, implements.
 *
 * <p>{@link #checkCompatibility} is the fix for a real gap found in the existing `.plugin`
 * system: {@code PluginManifest} parses an {@code __sdk_version__} field that is never actually
 * read back anywhere, so a plugin using a hook an old build doesn't have just fails deep inside
 * Python with no useful message. Blocks check every individual block-type id a script uses
 * against this registry, so the failure is always "this script uses blocks X and Y this version
 * doesn't have" - specific, before anything runs.
 */
public final class BlockRegistry {

    private BlockRegistry() {
    }

    private static final Map<String, BlockType> TYPES = new LinkedHashMap<>();

    static {
        BuiltinBlocks.registerAll();
    }

    static void register(BlockType type) {
        TYPES.put(type.id, type);
    }

    public static BlockType get(String id) {
        return TYPES.get(id);
    }

    public static Iterable<BlockType> all() {
        return Collections.unmodifiableCollection(TYPES.values());
    }

    /** Result of checking a script's block-type ids against this build's registry. */
    public static final class CompatibilityResult {
        /** Block ids the script uses that this build has never heard of at all. */
        public final Set<String> unknownTypeIds;
        /** Block ids this build knows, but whose own {@code minAppVersion} isn't satisfied. */
        public final Set<String> tooNewTypeIds;

        CompatibilityResult(Set<String> unknownTypeIds, Set<String> tooNewTypeIds) {
            this.unknownTypeIds = unknownTypeIds;
            this.tooNewTypeIds = tooNewTypeIds;
        }

        public boolean isCompatible() {
            return unknownTypeIds.isEmpty() && tooNewTypeIds.isEmpty();
        }
    }

    public static CompatibilityResult checkCompatibility(Set<String> usedBlockTypeIds, String currentAppVersion) {
        final Set<String> unknown = new LinkedHashSet<>();
        final Set<String> tooNew = new LinkedHashSet<>();
        for (String id : usedBlockTypeIds) {
            final BlockType type = TYPES.get(id);
            if (type == null) {
                unknown.add(id);
            } else if (!PluginVersions.satisfies(currentAppVersion, type.minAppVersion)) {
                tooNew.add(id);
            }
        }
        return new CompatibilityResult(unknown, tooNew);
    }

    /**
     * A readable label for a block-type id even when it's genuinely unknown to this build (no
     * {@link BlockType} to ask) - strips the category prefix and turns underscores into spaces,
     * e.g. {@code "condition.language_is"} -> {@code "language is"}. Used only as the fallback in
     * the "unsupported blocks" message; a known id always prefers {@link BlockType#label}.
     */
    public static String fallbackLabel(String typeId) {
        final int dot = typeId.indexOf('.');
        final String withoutPrefix = dot >= 0 ? typeId.substring(dot + 1) : typeId;
        return withoutPrefix.replace('_', ' ');
    }

    public static String labelFor(String typeId) {
        final BlockType type = TYPES.get(typeId);
        return type != null ? type.label : fallbackLabel(typeId);
    }
}
