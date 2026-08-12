package org.telegram.messenger.blocks;

import java.util.Arrays;
import java.util.Collections;

/**
 * PrimeGram Blocks: the action palette - the other place "hundreds" of entries belongs, one per
 * meaningful thing a chat/message/user can be acted on.
 */
final class BuiltinActions {

    private BuiltinActions() {
    }

    static void registerAll() {
        BlockRegistry.register(new BlockType(
                "action.send_reply", BlockType.Category.ACTION,
                "Отправить ответ",
                "Отправляет текстовое сообщение в чат",
                "", Collections.singletonList(
                        new ParamSpec("text", ParamSpec.Kind.TEXT, "Текст сообщения", "")
                )));

        BlockRegistry.register(new BlockType(
                "action.mark_as_read", BlockType.Category.ACTION,
                "Отметить прочитанным",
                "Отмечает чат прочитанным до сообщения-триггера",
                "", Collections.emptyList()));

        BlockRegistry.register(new BlockType(
                "action.archive_chat", BlockType.Category.ACTION,
                "Архивировать чат",
                "Перемещает чат в архив",
                "", Collections.emptyList()));

        BlockRegistry.register(new BlockType(
                "action.pin_message", BlockType.Category.ACTION,
                "Закрепить сообщение",
                "Закрепляет сообщение-триггер в чате",
                "", Collections.emptyList()));

        BlockRegistry.register(new BlockType(
                "action.mute_chat", BlockType.Category.ACTION,
                "Заглушить чат",
                "Отключает уведомления для этого чата",
                "", Collections.emptyList()));

        BlockRegistry.register(new BlockType(
                "action.forward_to_saved", BlockType.Category.ACTION,
                "Переслать в избранное",
                "Пересылает сообщение-триггер в «Избранное»",
                "", Collections.emptyList()));

        BlockRegistry.register(new BlockType(
                "action.block_user", BlockType.Category.ACTION,
                "Заблокировать пользователя",
                "Блокирует отправителя сообщения-триггера",
                "", Collections.emptyList()));

        // UI-editing actions - configure PrimeGram's own interface rather than act on a chat.
        // Meant to run from a script whose trigger is trigger.app_started (see BuiltinTriggers),
        // so the desired UI state gets (re)applied on every launch.
        BlockRegistry.register(new BlockType(
                "action.ui.set_sidebar_enabled", BlockType.Category.ACTION,
                "Боковая панель PrimeGram",
                "Включает или выключает свайп-панель сбоку экрана",
                "", Collections.singletonList(
                        new ParamSpec("enabled", ParamSpec.Kind.BOOLEAN, "Включена", true)
                )));

        BlockRegistry.register(new BlockType(
                "action.ui.set_channel_button_visible", BlockType.Category.ACTION,
                "Кнопка в шапке канала",
                "Показывает или скрывает одну из круглых кнопок над полем ввода в канале",
                "", Arrays.asList(
                        new ParamSpec("button", ParamSpec.Kind.ENUM, "Кнопка", "search",
                                new String[]{"search", "gift", "direct"}, 0, 0),
                        new ParamSpec("visible", ParamSpec.Kind.BOOLEAN, "Показана", true)
                )));
    }
}
