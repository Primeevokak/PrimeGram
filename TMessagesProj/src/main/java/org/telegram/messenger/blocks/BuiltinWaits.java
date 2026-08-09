package org.telegram.messenger.blocks;

import java.util.Collections;

/** PrimeGram Blocks: the wait palette - small on purpose, "how long" only has so many shapes. */
final class BuiltinWaits {

    private BuiltinWaits() {
    }

    static void registerAll() {
        BlockRegistry.register(new BlockType(
                "wait.duration", BlockType.Category.WAIT,
                "Подождать",
                "Приостанавливает скрипт на заданное время",
                "", Collections.singletonList(
                        new ParamSpec("hours", ParamSpec.Kind.NUMBER, "Часов", 3, null, 0, 72)
                )));
    }
}
