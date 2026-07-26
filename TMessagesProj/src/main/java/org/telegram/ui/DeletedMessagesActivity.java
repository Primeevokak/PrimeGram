package org.telegram.ui;

import android.view.View;

import org.telegram.messenger.DeletedMessagesStore;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Browses, limits and clears the local archive of messages other people deleted. */
public class DeletedMessagesActivity extends UniversalFragment {

    private static final int ID_CLEAR = 1;
    private static final int ID_LIMIT = 2;
    private static final int ID_ENTRY_BASE = 1000;

    /** Reading the whole archive into a list would defeat the point of capping it. */
    private static final int PAGE_SIZE = 200;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault());
    private List<DeletedMessagesStore.Entry> entries = new ArrayList<>();

    @Override
    protected CharSequence getTitle() {
        return "Удалённые сообщения";
    }

    @Override
    public boolean onFragmentCreate() {
        entries = DeletedMessagesStore.getInstance().query(0, PAGE_SIZE);
        return super.onFragmentCreate();
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        DeletedMessagesStore store = DeletedMessagesStore.getInstance();
        long count = store.count();
        long sizeKb = store.sizeOnDisk() / 1024;

        items.add(UItem.asHeader("Хранилище"));
        items.add(UItem.asButton(ID_LIMIT, "Максимум записей", String.valueOf(DeletedMessagesStore.getMaxEntries())));
        items.add(UItem.asButton(ID_CLEAR, "Очистить архив", count + " шт., " + sizeKb + " КБ"));
        items.add(UItem.asShadow("Когда записей становится больше лимита, самые старые удаляются автоматически. " +
                "Сохраняются только чужие удалённые сообщения — свои не архивируются. " +
                "Telegram не всегда сообщает клиенту об удалении, поэтому часть сообщений сюда не попадёт."));

        if (entries.isEmpty()) {
            items.add(UItem.asShadow("Пока ничего не сохранено."));
            return;
        }

        items.add(UItem.asHeader("Последние " + entries.size()));
        for (int i = 0; i < entries.size(); i++) {
            DeletedMessagesStore.Entry entry = entries.get(i);
            items.add(UItem.asButton(ID_ENTRY_BASE + i, describeAuthor(entry), buildPreview(entry)));
        }
    }

    private String describeAuthor(DeletedMessagesStore.Entry entry) {
        String who = null;
        if (entry.fromId > 0) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(entry.fromId);
            if (user != null) {
                who = UserObject.getUserName(user);
            }
        } else if (entry.fromId < 0) {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-entry.fromId);
            if (chat != null) {
                who = chat.title;
            }
        }
        if (who == null || who.isEmpty()) {
            who = "ID " + entry.fromId;
        }
        return who + " · " + dateFormat.format(new Date(entry.deletedAt));
    }

    private String buildPreview(DeletedMessagesStore.Entry entry) {
        if (entry.text == null || entry.text.isEmpty()) {
            return "(без текста)";
        }
        String text = entry.text.replace('\n', ' ');
        return text.length() > 120 ? text.substring(0, 120) + "…" : text;
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_CLEAR) {
            confirmClear();
        } else if (item.id == ID_LIMIT) {
            showLimitDialog();
        } else if (item.id >= ID_ENTRY_BASE) {
            int index = item.id - ID_ENTRY_BASE;
            if (index >= 0 && index < entries.size()) {
                showEntry(entries.get(index));
            }
        }
    }

    private void showEntry(DeletedMessagesStore.Entry entry) {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(describeAuthor(entry));
        builder.setMessage(entry.text == null || entry.text.isEmpty() ? "(без текста)" : entry.text);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
        builder.setNeutralButton(LocaleController.getString(R.string.Copy), (dialog, which) -> {
            org.telegram.messenger.AndroidUtilities.addToClipboard(entry.text == null ? "" : entry.text);
        });
        showDialog(builder.create());
    }

    private void confirmClear() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Очистить архив");
        builder.setMessage("Все сохранённые удалённые сообщения будут стёрты без возможности восстановления.");
        builder.setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) -> {
            DeletedMessagesStore.getInstance().clear();
            entries = new ArrayList<>();
            listView.adapter.update(true);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog alertDialog = builder.create();
        showDialog(alertDialog);
        View button = alertDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
        if (button instanceof android.widget.TextView) {
            ((android.widget.TextView) button).setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }

    private void showLimitDialog() {
        if (getParentActivity() == null) {
            return;
        }
        final int[] options = new int[]{500, 1000, 2000, 5000, 10000};
        String[] labels = new String[options.length];
        for (int i = 0; i < options.length; i++) {
            labels[i] = String.valueOf(options[i]);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Максимум записей");
        builder.setItems(labels, (dialog, which) -> {
            if (which >= 0 && which < options.length) {
                DeletedMessagesStore.setMaxEntries(options[which]);
                listView.adapter.update(true);
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
