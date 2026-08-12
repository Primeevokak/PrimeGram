package org.telegram.ui;

import android.os.Bundle;
import android.view.View;

import androidx.collection.LongSparseArray;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessageTagsStore;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
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
    private static final int ID_FORWARD_ALL = 2;
    private static final int ID_TOGGLE_SELECT = 3;
    private static final int ID_SELECT_ALL_TOGGLE = 4;
    private static final int ID_TAG_BASE = 1000;
    private static final int ID_ENTRY_BASE = 5000;

    /** 0 = every chat. */
    private final long filterDialogId;

    /** null = showing the tag list; otherwise showing messages under this tag. */
    private String selectedTag;
    private List<MessageTagsStore.Entry> entries = new ArrayList<>();
    private List<String> tags = new ArrayList<>();

    /**
     * PrimeGram: "Переслать все" alone was not enough - sometimes the whole point is grabbing
     * just one or a handful of specific tagged messages out of many, not literally everything.
     * {@link #selecting} turns entries into checkboxes; with it off, tapping still jumps straight
     * to the message like before, so the common one-message case is unchanged.
     */
    private boolean selecting;
    private final java.util.LinkedHashSet<Integer> selectedIndices = new java.util.LinkedHashSet<>();

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
            selecting = false;
            selectedIndices.clear();
            items.add(UItem.asShadow("Под этим тегом ничего нет."));
            return;
        }
        // A tag can lose entries (message deleted, tag removed elsewhere) between rebuilds -
        // indices pointing past the new size would otherwise linger as phantom selections.
        selectedIndices.removeIf(index -> index >= entries.size());

        items.add(UItem.asHeader("Сообщений: " + entries.size()));
        if (!selecting) {
            items.add(UItem.asButton(ID_FORWARD_ALL, R.drawable.msg_forward, "Переслать все",
                    entries.size() == 1 ? "1 сообщение" : entries.size() + " сообщений"));
            items.add(UItem.asButton(ID_TOGGLE_SELECT, R.drawable.msg_select, "Выбрать сообщения",
                    "чтобы переслать не все"));
        } else {
            items.add(UItem.asButton(ID_FORWARD_ALL, R.drawable.msg_forward,
                    selectedIndices.isEmpty() ? "Переслать выбранное" : "Переслать выбранное",
                    selectedIndices.isEmpty() ? "ничего не выбрано" :
                            (selectedIndices.size() == 1 ? "1 сообщение" : selectedIndices.size() + " сообщений")));
            final boolean allSelected = selectedIndices.size() == entries.size();
            items.add(UItem.asButton(ID_SELECT_ALL_TOGGLE, allSelected ? R.drawable.msg_cancel : R.drawable.msg_select,
                    allSelected ? "Снять весь выбор" : "Выбрать все"));
            items.add(UItem.asButton(ID_TOGGLE_SELECT, R.drawable.msg_cancel, "Отменить выбор"));
        }
        items.add(UItem.asShadow(null));
        for (int i = 0; i < entries.size(); i++) {
            MessageTagsStore.Entry entry = entries.get(i);
            final String line = describeChat(entry.dialogId) + " — " + preview(entry);
            if (selecting) {
                final UItem item = UItem.asRoundCheckbox(ID_ENTRY_BASE + i, line);
                item.checked = selectedIndices.contains(i);
                items.add(item);
            } else {
                items.add(UItem.asButton(ID_ENTRY_BASE + i, describeChat(entry.dialogId), preview(entry)));
            }
        }
        items.add(UItem.asShadow(selecting
                ? "Нажмите на сообщение, чтобы выбрать или снять выбор."
                : "Нажмите, чтобы перейти к сообщению. Задержите, чтобы снять с него этот тег."));
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
        } else if (item.id == ID_FORWARD_ALL) {
            if (selecting && selectedIndices.isEmpty()) {
                return;
            }
            forwardTagged(selecting ? new ArrayList<>(selectedIndices) : null);
        } else if (item.id == ID_TOGGLE_SELECT) {
            selecting = !selecting;
            if (!selecting) {
                selectedIndices.clear();
            }
            listView.adapter.update(true);
        } else if (item.id == ID_SELECT_ALL_TOGGLE) {
            if (selectedIndices.size() == entries.size()) {
                selectedIndices.clear();
            } else {
                selectedIndices.clear();
                for (int i = 0; i < entries.size(); i++) {
                    selectedIndices.add(i);
                }
            }
            listView.adapter.update(true);
        } else if (item.id >= ID_ENTRY_BASE) {
            int index = item.id - ID_ENTRY_BASE;
            if (index < 0 || index >= entries.size()) {
                return;
            }
            if (selecting) {
                if (!selectedIndices.remove(index)) {
                    selectedIndices.add(index);
                }
                listView.adapter.update(true);
            } else {
                openMessage(entries.get(index));
            }
        } else if (item.id >= ID_TAG_BASE) {
            int index = item.id - ID_TAG_BASE;
            if (index >= 0 && index < tags.size()) {
                selectedTag = tags.get(index);
                selecting = false;
                selectedIndices.clear();
                actionBar.setTitle(getTitle());
                listView.adapter.update(true);
            }
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (getParentActivity() == null || selecting) {
            // Long-press means "untag this message" outside selection mode - ambiguous, and
            // easy to trigger by accident, while the user is trying to pick a set to forward.
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

    /**
     * The whole point of this screen over just searching: a tag can span every chat, and until
     * now the only thing you could do with the result was jump to messages one at a time. This
     * loads either every message under {@link #selectedTag} ({@code onlyIndices == null}) or just
     * the checked ones, and hands them to the standard forward-to picker in one go, same as
     * multi-selecting inside a chat.
     *
     * <p>Loads from the local message cache only (no network fetch) - a message someone tagged
     * was, by definition, on screen at some point, so it is almost always still there. A message
     * that fell out of the local cache is simply skipped and counted, rather than doing a
     * per-message network round trip that would make forwarding a large tag noticeably slow.
     *
     * <p>Grouped by source chat and fetched with {@link MessagesStorage#getMessagesByIds} - a tag
     * with a few thousand entries across a handful of chats costs a few bulk queries, not one
     * query per message. Requesting a large tag full of hundreds/thousands of entries is exactly
     * the case this exists for, not an edge case to leave slow.
     */
    private void forwardTagged(java.util.List<Integer> onlyIndices) {
        if (getParentActivity() == null || entries.isEmpty()) {
            return;
        }
        final java.util.List<MessageTagsStore.Entry> toForward = new ArrayList<>();
        if (onlyIndices == null) {
            toForward.addAll(entries);
        } else {
            for (int index : onlyIndices) {
                if (index >= 0 && index < entries.size()) {
                    toForward.add(entries.get(index));
                }
            }
        }
        if (toForward.isEmpty()) {
            return;
        }

        final AlertDialog progress = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(false);
        progress.show();

        final int account = currentAccount;
        new Thread(() -> {
            // Group requested ids by their source chat first, so each chat costs one bulk query
            // instead of one query per message - the actual fix for "a tag can have thousands of
            // entries", not just a detail of how getMessage() vs getMessagesByIds() is called.
            final LongSparseArray<ArrayList<Integer>> byDialog = new LongSparseArray<>();
            for (MessageTagsStore.Entry entry : toForward) {
                ArrayList<Integer> ids = byDialog.get(entry.dialogId);
                if (ids == null) {
                    ids = new ArrayList<>();
                    byDialog.put(entry.dialogId, ids);
                }
                ids.add(entry.messageId);
            }

            final ArrayList<MessageObject> loaded = new ArrayList<>();
            for (int i = 0; i < byDialog.size(); i++) {
                final long dialogId = byDialog.keyAt(i);
                try {
                    final ArrayList<TLRPC.Message> messages = MessagesStorage.getInstance(account).getMessagesByIds(dialogId, byDialog.valueAt(i));
                    for (TLRPC.Message message : messages) {
                        loaded.add(new MessageObject(account, message, false, false));
                    }
                } catch (Throwable ignore) {
                }
            }
            // messages.forwardMessages keeps the original order - the bulk SQL query does not
            // guarantee it back, and a forward that reshuffled the conversation would be worse
            // than one that is merely missing an offline message.
            final LongSparseArray<Integer> order = new LongSparseArray<>();
            for (int i = 0; i < toForward.size(); i++) {
                order.put(toForward.get(i).dialogId * 10_000_000_000L + toForward.get(i).messageId, i);
            }
            java.util.Collections.sort(loaded, (a, b) -> {
                Integer ia = order.get(a.getDialogId() * 10_000_000_000L + a.getId());
                Integer ib = order.get(b.getDialogId() * 10_000_000_000L + b.getId());
                return (ia == null ? 0 : ia) - (ib == null ? 0 : ib);
            });

            final int missingCount = toForward.size() - loaded.size();
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    progress.dismiss();
                } catch (Throwable ignore) {
                }
                if (getParentActivity() == null) {
                    return;
                }
                if (loaded.isEmpty()) {
                    BulletinFactory.of(this).createErrorBulletin("Не удалось найти ни одного сообщения — возможно, они удалены").show();
                    return;
                }
                openForwardPicker(loaded, missingCount);
            });
        }).start();
    }

    /** messages.forwardMessages has no documented hard cap, but a request built from a couple of
     *  thousand ids at once is not something to find out about for the first time in production -
     *  chunked, sequential sends keep each individual request a size the stock forward flow (a
     *  bounded on-screen selection) already exercises routinely. */
    private static final int FORWARD_CHUNK_SIZE = 100;

    /** Opens the same "choose a chat" sheet a normal multi-select forward uses. */
    private void openForwardPicker(ArrayList<MessageObject> messages, int missingCount) {
        final Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
        args.putBoolean("allowSwitchAccount", true);
        final DialogsActivity fragment = new DialogsActivity(args);
        fragment.setDelegate((fragment1, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            for (int i = 0; i < dids.size(); i++) {
                final long did = dids.get(i).dialogId;
                for (int start = 0; start < messages.size(); start += FORWARD_CHUNK_SIZE) {
                    final int end = Math.min(start + FORWARD_CHUNK_SIZE, messages.size());
                    getSendMessagesHelper().sendMessage(new ArrayList<>(messages.subList(start, end)), did, false, false, notify, scheduleDate, 0);
                }
            }
            fragment1.finishFragment();
            selecting = false;
            selectedIndices.clear();
            if (missingCount > 0) {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.info,
                        "Переслано " + messages.size() + " из " + (messages.size() + missingCount) + " — часть сообщений недоступна офлайн").show();
            } else {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.forward, "Переслано " + messages.size() + " сообщений").show();
            }
            return true;
        });
        presentFragment(fragment);
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
        // Back out of selection mode first, then out of a tag's message list, then out of the
        // screen - each press undoes exactly one level, same convention as normal chat selection.
        if (selecting) {
            if (invoked) {
                selecting = false;
                selectedIndices.clear();
                listView.adapter.update(true);
            }
            return false;
        }
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
