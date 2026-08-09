package org.telegram.messenger.blocks;

import java.util.Arrays;
import java.util.Collections;

/**
 * PrimeGram Blocks: the condition palette - where most of the "hundreds" scope lives, since a
 * condition is cheap to add (one check, no new trigger wiring) and composes with the existing
 * {@code condition.if_else} AND/OR mechanism instead of multiplying trigger count.
 */
final class BuiltinConditions {

    private BuiltinConditions() {
    }

    static void registerAll() {
        BlockRegistry.register(new BlockType(
                "condition.not_replied_since_trigger", BlockType.Category.CONDITION,
                "Я ещё не ответил(а)",
                "Верно, если в этом чате нет исходящих сообщений после срабатывания",
                "", Collections.emptyList()));

        BlockRegistry.register(new BlockType(
                "condition.language_is", BlockType.Category.CONDITION,
                "Язык сообщения",
                "Проверяет, на каком языке написано сообщение",
                "", Arrays.asList(
                        new ParamSpec("language", ParamSpec.Kind.ENUM, "Язык", "en",
                                new String[]{"en", "ru", "ar", "es", "de", "fr", "zh", "ja"}, 0, 0),
                        new ParamSpec("negate", ParamSpec.Kind.BOOLEAN, "Инвертировать (не этот язык)", false)
                )));

        BlockRegistry.register(new BlockType(
                "condition.chat_type_is", BlockType.Category.CONDITION,
                "Тип чата",
                "Проверяет, личный это чат, группа или канал",
                "", Collections.singletonList(
                        new ParamSpec("chat_type", ParamSpec.Kind.ENUM, "Тип чата", "private",
                                new String[]{"private", "group", "channel"}, 0, 0)
                )));

        BlockRegistry.register(new BlockType(
                "condition.sender_is_bot", BlockType.Category.CONDITION,
                "Отправитель — бот",
                "Верно, если сообщение отправил бот",
                "", Collections.emptyList()));

        BlockRegistry.register(new BlockType(
                "condition.chat_is_muted", BlockType.Category.CONDITION,
                "Чат заглушён",
                "Верно, если у этого чата отключены уведомления",
                "", Collections.emptyList()));

        BlockRegistry.register(new BlockType(
                "condition.message_contains_text", BlockType.Category.CONDITION,
                "Сообщение содержит текст",
                "Проверяет вхождение подстроки в текст сообщения",
                "", Arrays.asList(
                        new ParamSpec("text", ParamSpec.Kind.TEXT, "Текст", ""),
                        new ParamSpec("negate", ParamSpec.Kind.BOOLEAN, "Инвертировать (не содержит)", false)
                )));
    }
}
