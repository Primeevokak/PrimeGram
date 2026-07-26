package org.telegram.ui;

import android.os.Bundle;
import android.view.View;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageTagsStore;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * Browses locally tagged messages, grouped by tag.
 *
 * <p>Can be scoped to a single chat, which is how it is opened from a chat's own menu — the
 * global list is for finding something across every conversation, the scoped one for working
 * inside the conversation you are already in.
 */
public class MessageTagsActivity extends UniversalFragment {

    private static final int ID_CLEAR = 1;
    private static final int ID_TAG_BASE = 1000;
    private static final int ID_ENTRY_BASE = 5000;

    /** 0 = every chat. */
    private final long filterDialogId;

    /** null = showing the tag list; otherwise showing messages under this tag. */
    private String selectedTag;
    private List<MessageTagsStore.Entry> entries = new ArrayList<>();
    private List<String> tags = new ArrayList<>();

    public MessageTagsActivity() {
        this(0);
    }

    public MessageTagsActivity(long filterDialogId) {
        this.filterDialogId = filterDialogId;
    }

    @Override
    protected CharSequence getTitle() {
        if (selectedTag != null) {
            return selectedTag;
        }
        return filterDialogId != 0 ? "Теги в этом чате" : "Теги сообщений";
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (selectedTag == null) {
            tags = filterDialogId != 0 ? MessageTagsStore.getTagsInDialog(filterDialogId) : MessageTagsStore.getAllTags();
            if (tags.isEmpty()) {
                items.add(UItem.asShadow(filterDialogId != 0
                        ? "В этом чате ничего не помечено.\n\nЗадержите сообщение и выберите «Пометить тегом»."
                        : "Пока нет ни одного тега.\n\nЧтобы пометить сообщение, задержите его в чате и выберите «Пометить тегом». Теги хранятся только на этом устройстве и не видны собеседнику."));
                return;
            }
            items.add(UItem.asHeader("Теги"));
            for (int i = 0; i < tags.size(); i++) {
                String tag = tags.get(i);
                int count = MessageTagsStore.query(tag, filterDialogId).size();
                items.add(UItem.asButton(ID_TAG_BASE + i, MessageTagsStore.getEmoji(tag) + "  " + tag, String.valueOf(count)));
            }
            items.add(UItem.asShadow("Задержите тег, чтобы сменить иконку или удалить его целиком."));
            if (filterDialogId == 0) {
                items.add(UItem.asButton(ID_CLEAR, "Удалить все теги"));
            }
            return;
        }

        entries = MessageTagsStore.query(selectedTag, filterDialogId);
        if (entries.isEmpty()) {
            items.add(UItem.asShadow("Под этим тегом ничего нет."));
            return;
        }
        items.add(UItem.asHeader("Сообщений: " + entries.size()));
        for (int i = 0; i < entries.size(); i++) {
            MessageTagsStore.Entry entry = entries.get(i);
            items.add(UItem.asButton(ID_ENTRY_BASE + i, describeChat(entry.dialogId), preview(entry)));
        }
        items.add(UItem.asShadow("Нажмите, чтобы перейти к сообщению. Задержите, чтобы снять с него этот тег."));
    }

    private String describeChat(long dialogId) {
        if (dialogId > 0) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            if (user != null) {
                return UserObject.getUserName(user);
            }
        } else if (dialogId < 0) {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            if (chat != null) {
                return chat.title;
            }
        }
        return "Чат " + dialogId;
    }

    private String preview(MessageTagsStore.Entry entry) {
        if (entry.preview == null || entry.preview.isEmpty()) {
            return "(без текста)";
        }
        return entry.preview.replace('\n', ' ');
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_CLEAR) {
            confirmClear();
        } else if (item.id >= ID_ENTRY_BASE) {
            int index = item.id - ID_ENTRY_BASE;
            if (index >= 0 && index < entries.size()) {
                openMessage(entries.get(index));
            }
        } else if (item.id >= ID_TAG_BASE) {
            int index = item.id - ID_TAG_BASE;
            if (index >= 0 && index < tags.size()) {
                selectedTag = tags.get(index);
                actionBar.setTitle(getTitle());
                listView.adapter.update(true);
            }
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (getParentActivity() == null) {
            return false;
        }
        if (item.id >= ID_ENTRY_BASE) {
            int index = item.id - ID_ENTRY_BASE;
            if (index < 0 || index >= entries.size()) {
                return false;
            }
            MessageTagsStore.Entry entry = entries.get(index);
            confirm("Снять тег", "Снять тег «" + selectedTag + "» с этого сообщения?", "Снять", () -> {
                MessageTagsStore.removeTag(selectedTag, entry.dialogId, entry.messageId);
                if (MessageTagsStore.query(selectedTag, filterDialogId).isEmpty()) {
                    selectedTag = null;
                    actionBar.setTitle(getTitle());
                }
                listView.adapter.update(true);
            });
            return true;
        }
        if (item.id >= ID_TAG_BASE) {
            int index = item.id - ID_TAG_BASE;
            if (index < 0 || index >= tags.size()) {
                return false;
            }
            String tag = tags.get(index);
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(MessageTagsStore.getEmoji(tag) + "  " + tag);
            builder.setItems(new String[]{"Изменить иконку", "Удалить тег"}, (dialog, which) -> {
                if (which == 0) {
                    showIconDialog(tag);
                } else {
                    confirm("Удалить тег", "Тег «" + tag + "» будет снят со всех сообщений. Сами сообщения не пострадают.", LocaleController.getString(R.string.Delete), () -> {
                        MessageTagsStore.removeWholeTag(tag);
                        listView.adapter.update(true);
                    });
                }
            });
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            showDialog(builder.create());
            return true;
        }
        return false;
    }

    /** Jumps straight to the tagged message in its chat. */
    private void openMessage(MessageTagsStore.Entry entry) {
        Bundle args = new Bundle();
        if (entry.dialogId > 0) {
            args.putLong("user_id", entry.dialogId);
        } else {
            args.putLong("chat_id", -entry.dialogId);
        }
        args.putInt("message_id", entry.messageId);
        presentFragment(new ChatActivity(args));
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        // Back from a tag's message list returns to the tag list, not out of the screen.
        if (selectedTag != null) {
            if (invoked) {
                selectedTag = null;
                actionBar.setTitle(getTitle());
                listView.adapter.update(true);
            }
            return false;
        }
        return super.onBackPressed(invoked);
    }

    private void confirmClear() {
        confirm("Удалить все теги", "Все локальные пометки будут стёрты. Сами сообщения не пострадают.", LocaleController.getString(R.string.Delete), () -> {
            MessageTagsStore.clearAll();
            selectedTag = null;
            listView.adapter.update(true);
        });
    }

    /** Free-form icon input: whatever the keyboard can type is a valid tag icon. */
    private void showIconDialog(String tag) {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Иконка тега");
        builder.setMessage("Вставьте любой эмодзи или символ. Пустое поле вернёт значок по умолчанию.");

        final org.telegram.ui.Components.EditTextBoldCursor editText = new org.telegram.ui.Components.EditTextBoldCursor(getParentActivity());
        editText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 20);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        editText.setHint(MessageTagsStore.DEFAULT_EMOJI);
        editText.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setCursorSize(org.telegram.messenger.AndroidUtilities.dp(20));
        editText.setSingleLine(true);
        editText.setBackgroundDrawable(Theme.createEditTextDrawable(getParentActivity(), true));
        editText.setText(MessageTagsStore.getEmoji(tag));
        editText.setSelection(editText.getText().length());

        android.widget.LinearLayout container = new android.widget.LinearLayout(getParentActivity());
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        container.setPadding(org.telegram.messenger.AndroidUtilities.dp(24), org.telegram.messenger.AndroidUtilities.dp(4), org.telegram.messenger.AndroidUtilities.dp(24), 0);
        container.addView(editText, org.telegram.ui.Components.LayoutHelper.createLinear(
                org.telegram.ui.Components.LayoutHelper.MATCH_PARENT, org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT));
        builder.setView(container);

        builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> {
            MessageTagsStore.setEmoji(tag, editText.getText().toString().trim());
            listView.adapter.update(true);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void confirm(String title, String message, String positive, Runnable action) {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(positive, (dialog, which) -> action.run());
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog alertDialog = builder.create();
        showDialog(alertDialog);
        View button = alertDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
        if (button instanceof android.widget.TextView) {
            ((android.widget.TextView) button).setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }
}
