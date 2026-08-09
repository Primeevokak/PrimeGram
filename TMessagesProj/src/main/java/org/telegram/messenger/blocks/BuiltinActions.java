package org.telegram.messenger.blocks;

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
    }
}
