package org.telegram.messenger.blocks;

/**
 * PrimeGram Blocks: entry point that registers the whole built-in palette. Content itself lives
 * in one file per {@link BlockType.Category} ({@link BuiltinTriggers}, {@link BuiltinConditions},
 * {@link BuiltinActions}, {@link BuiltinWaits}) so a single-file list doesn't become unreadable
 * as the "hundreds of conditions/actions" target grows.
 */
final class BuiltinBlocks {

    private BuiltinBlocks() {
    }

    static void registerAll() {
        BuiltinTriggers.registerAll();
        BuiltinConditions.registerAll();
        BuiltinActions.registerAll();
        BuiltinWaits.registerAll();
    }
}
