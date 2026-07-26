package org.telegram.ui;

import android.os.Bundle;
import android.view.View;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.TempSubStore;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Lists the channels scheduled to be left automatically, with the time each has left. */
public class TempSubActivity extends UniversalFragment {

    private static final int ID_ENTRY_BASE = 1000;

    private List<TempSubStore.Entry> entries = new ArrayList<>();

    @Override
    protected CharSequence getTitle() {
        return "Временные подписки";
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        entries = TempSubStore.getAll();
        if (entries.isEmpty()) {
            items.add(UItem.asShadow("Активных временных подписок нет.\n\nЧтобы подписаться на канал на время, откройте его и выберите «Временная подписка» в меню. Клиент отпишется сам, когда срок выйдет."));
            return;
        }
        items.add(UItem.asHeader("Отпишемся автоматически"));
        for (int i = 0; i < entries.size(); i++) {
            TempSubStore.Entry entry = entries.get(i);
            items.add(UItem.asButton(ID_ENTRY_BASE + i, describe(entry), remaining(entry)));
        }
        items.add(UItem.asShadow("Нажмите на подписку, чтобы отменить автоотписку и оставить канал навсегда.\n\nПроверка выполняется, пока приложение запущено: если срок вышел, когда клиент был закрыт, отписка произойдёт при следующем запуске."));
    }

    private String describe(TempSubStore.Entry entry) {
        if (entry.title != null && !entry.title.isEmpty()) {
            return entry.title;
        }
        if (entry.dialogId < 0) {
            TLRPC.Chat chat = getMessagesController().getChat(-entry.dialogId);
            if (chat != null) {
                return chat.title;
            }
        } else {
            TLRPC.User user = getMessagesController().getUser(entry.dialogId);
            if (user != null) {
                return UserObject.getUserName(user);
            }
        }
        return "Чат " + entry.dialogId;
    }

    private String remaining(TempSubStore.Entry entry) {
        long left = entry.expiresAt - System.currentTimeMillis();
        if (left <= 0) {
            return "истекла";
        }
        long minutes = left / 60_000L;
        if (minutes < 60) {
            return minutes + " мин";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + " ч";
        }
        return String.format(Locale.US, "%d дн", hours / 24);
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        int index = item.id - ID_ENTRY_BASE;
        if (index < 0 || index >= entries.size() || getParentActivity() == null) {
            return;
        }
        TempSubStore.Entry entry = entries.get(index);
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(describe(entry));
        builder.setMessage("Отменить автоотписку? Канал останется у вас навсегда.");
        builder.setPositiveButton("Отменить автоотписку", (dialog, which) -> {
            TempSubStore.cancel(entry.dialogId);
            listView.adapter.update(true);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
