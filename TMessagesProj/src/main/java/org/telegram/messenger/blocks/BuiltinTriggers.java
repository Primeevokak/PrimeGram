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

        BlockRegistry.register(new BlockType(
                "trigger.message_read", BlockType.Category.TRIGGER,
                "Сообщение прочитано",
                "Срабатывает, когда прочитанные сообщения в чате продвигаются вперёд",
                "", Collections.emptyList()));

        BlockRegistry.register(new BlockType(
                "trigger.poll_answered", BlockType.Category.TRIGGER,
                "Ответ на опрос",
                "Срабатывает, когда кто-то отвечает на опрос",
                "", Collections.emptyList()));

        BlockRegistry.register(new BlockType(
                "trigger.call_started", BlockType.Category.TRIGGER,
                "Звонок начался",
                "Срабатывает при начале звонка",
                "", Collections.emptyList()));

        BlockRegistry.register(new BlockType(
                "trigger.call_ended", BlockType.Category.TRIGGER,
                "Звонок завершён",
                "Срабатывает при завершении звонка",
                "", Collections.emptyList()));

        BlockRegistry.register(new BlockType(
                "trigger.chat_history_cleared", BlockType.Category.TRIGGER,
                "История чата очищена",
                "Срабатывает, когда история чата очищается",
                "", Collections.emptyList()));

        BlockRegistry.register(new BlockType(
                "trigger.channel_rights_updated", BlockType.Category.TRIGGER,
                "Права администратора изменены",
                "Срабатывает, когда меняются права в канале или группе",
                "", Collections.emptyList()));

        BlockRegistry.register(new BlockType(
                "trigger.app_started", BlockType.Category.TRIGGER,
                "Запуск приложения",
                "Срабатывает при каждом запуске PrimeGram - используется для скриптов, настраивающих интерфейс",
                "", Collections.emptyList()));
    }
}
