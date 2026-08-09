package org.telegram.messenger.blocks;

import java.util.Collections;

/**
 * PrimeGram Blocks: the trigger palette - deliberately kept to genuinely distinct Telegram
 * events, not combinatorial variants of them (see the "Editor Redesign v2" plan's "keep triggers
 * few" decision). Filtering by sender/chat is a {@link BuiltinConditions} job.
 */
final class BuiltinTriggers {

    private BuiltinTriggers() {
    }

    static void registerAll() {
        BlockRegistry.register(new BlockType(
                "trigger.message_from_non_contact", BlockType.Category.TRIGGER,
                "Сообщение не от контакта",
                "Срабатывает, когда пишет человек, которого нет в контактах",
                "", Collections.emptyList()));

        BlockRegistry.register(new BlockType(
                "trigger.message_received", BlockType.Category.TRIGGER,
                "Получено сообщение",
                "Срабатывает на любое входящее сообщение",
                "", Collections.emptyList()));

        BlockRegistry.register(new BlockType(
                "trigger.message_edited", BlockType.Category.TRIGGER,
                "Сообщение отредактировано",
                "Срабатывает, когда собеседник изменяет уже отправленное сообщение",
                "", Collections.emptyList()));

        BlockRegistry.register(new BlockType(
                "trigger.message_deleted", BlockType.Category.TRIGGER,
                "Сообщение удалено",
                "Срабатывает, когда собеседник удаляет своё сообщение",
                "", Collections.emptyList()));

        BlockRegistry.register(new BlockType(
                "trigger.reaction_added", BlockType.Category.TRIGGER,
                "Поставлена реакция",
                "Срабатывает, когда на сообщение ставят реакцию",
                "", Collections.emptyList()));

        BlockRegistry.register(new BlockType(
                "trigger.user_joined_chat", BlockType.Category.TRIGGER,
                "Пользователь вступил в чат",
                "Срабатывает, когда кто-то присоединяется к группе",
                "", Collections.emptyList()));
    }
}
