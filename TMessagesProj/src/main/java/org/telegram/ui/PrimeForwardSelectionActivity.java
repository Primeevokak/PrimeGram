package org.telegram.ui;

import android.os.Bundle;
import android.view.View;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * PrimeGram: pick some, several, or all of an already-loaded list of messages and forward them in
 * one go - same screen shape as {@link MessageTagsActivity}'s tag-forwarding, generalized to any
 * source of {@link MessageObject}s instead of {@link org.telegram.messenger.MessageTagsStore}
 * specifically. First user: hashtag search results, which needed the exact same "forward what I
 * found" gap filled across all three of its tabs (this chat / my chats / public posts) - none of
 * which previously offered anything past "jump to one message at a time".
 *
 * <p>Deliberately takes the messages already in memory rather than re-fetching anything: whatever
 * called this had them loaded to display them in the first place.
 */
public class PrimeForwardSelectionActivity extends UniversalFragment {

    private static final int ID_FORWARD_ALL = 1;
    private static final int ID_TOGGLE_SELECT = 2;
    private static final int ID_SELECT_ALL_TOGGLE = 3;
    private static final int ID_ENTRY_BASE = 100;
    private static final int FORWARD_CHUNK_SIZE = 100;

    private final CharSequence screenTitle;
    private final List<MessageObject> source;

    private boolean selecting;
    private final LinkedHashSet<Integer> selectedIndices = new LinkedHashSet<>();

    public PrimeForwardSelectionActivity(CharSequence title, List<MessageObject> messages) {
        this.screenTitle = title;
        this.source = new ArrayList<>(messages);
    }

    @Override
    protected CharSequence getTitle() {
        return screenTitle;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (source.isEmpty()) {
            items.add(UItem.asShadow("Список пуст."));
            return;
        }
        selectedIndices.removeIf(index -> index >= source.size());

        items.add(UItem.asHeader("Сообщений: " + source.size()));
        if (!selecting) {
            items.add(UItem.asButton(ID_FORWARD_ALL, R.drawable.msg_forward, "Переслать все",
                    source.size() == 1 ? "1 сообщение" : source.size() + " сообщений"));
            items.add(UItem.asButton(ID_TOGGLE_SELECT, R.drawable.msg_select, "Выбрать сообщения",
                    "чтобы переслать не все"));
        } else {
            items.add(UItem.asButton(ID_FORWARD_ALL, R.drawable.msg_forward, "Переслать выбранное",
                    selectedIndices.isEmpty() ? "ничего не выбрано" :
                            (selectedIndices.size() == 1 ? "1 сообщение" : selectedIndices.size() + " сообщений")));
            final boolean allSelected = selectedIndices.size() == source.size();
            items.add(UItem.asButton(ID_SELECT_ALL_TOGGLE, allSelected ? R.drawable.msg_cancel : R.drawable.msg_select,
                    allSelected ? "Снять весь выбор" : "Выбрать все"));
            items.add(UItem.asButton(ID_TOGGLE_SELECT, R.drawable.msg_cancel, "Отменить выбор"));
        }
        items.add(UItem.asShadow(null));
        for (int i = 0; i < source.size(); i++) {
            final MessageObject message = source.get(i);
            final String line = describeChat(message.getDialogId()) + " — " + preview(message);
            if (selecting) {
                final UItem item = UItem.asRoundCheckbox(ID_ENTRY_BASE + i, line);
                item.checked = selectedIndices.contains(i);
                items.add(item);
            } else {
                items.add(UItem.asButton(ID_ENTRY_BASE + i, describeChat(message.getDialogId()), preview(message)));
            }
        }
        items.add(UItem.asShadow(selecting
                ? "Нажмите на сообщение, чтобы выбрать или снять выбор."
                : "Нажмите, чтобы перейти к сообщению."));
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

    private String preview(MessageObject message) {
        CharSequence text = message.messageText;
        if (text == null || text.length() == 0) {
            return "(без текста)";
        }
        return text.toString().replace('\n', ' ');
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_FORWARD_ALL) {
            if (selecting && selectedIndices.isEmpty()) {
                return;
            }
            forward(selecting ? new ArrayList<>(selectedIndices) : null);
        } else if (item.id == ID_TOGGLE_SELECT) {
            selecting = !selecting;
            if (!selecting) {
                selectedIndices.clear();
            }
            listView.adapter.update(true);
        } else if (item.id == ID_SELECT_ALL_TOGGLE) {
            if (selectedIndices.size() == source.size()) {
                selectedIndices.clear();
            } else {
                selectedIndices.clear();
                for (int i = 0; i < source.size(); i++) {
                    selectedIndices.add(i);
                }
            }
            listView.adapter.update(true);
        } else if (item.id >= ID_ENTRY_BASE) {
            final int index = item.id - ID_ENTRY_BASE;
            if (index < 0 || index >= source.size()) {
                return;
            }
            if (selecting) {
                if (!selectedIndices.remove(index)) {
                    selectedIndices.add(index);
                }
                listView.adapter.update(true);
            } else {
                openMessage(source.get(index));
            }
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (selecting) {
            if (invoked) {
                selecting = false;
                selectedIndices.clear();
                listView.adapter.update(true);
            }
            return false;
        }
        return super.onBackPressed(invoked);
    }

    private void openMessage(MessageObject message) {
        final Bundle args = new Bundle();
        final long dialogId = message.getDialogId();
        if (dialogId > 0) {
            args.putLong("user_id", dialogId);
        } else {
            args.putLong("chat_id", -dialogId);
        }
        args.putInt("message_id", message.getId());
        presentFragment(new ChatActivity(args));
    }

    private void forward(List<Integer> onlyIndices) {
        if (getParentActivity() == null || source.isEmpty()) {
            return;
        }
        final ArrayList<MessageObject> messages = new ArrayList<>();
        if (onlyIndices == null) {
            messages.addAll(source);
        } else {
            for (int index : onlyIndices) {
                if (index >= 0 && index < source.size()) {
                    messages.add(source.get(index));
                }
            }
        }
        if (messages.isEmpty()) {
            return;
        }

        final Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
        args.putBoolean("allowSwitchAccount", true);
        final DialogsActivity fragment = new DialogsActivity(args);
        fragment.setDelegate((fragment1, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            for (int i = 0; i < dids.size(); i++) {
                final long did = dids.get(i).dialogId;
                // messages.forwardMessages has no documented hard cap, but a request built from a
                // couple of thousand ids at once is not something to find out about for the first
                // time in production - chunked sends keep each request a size the stock forward
                // flow (a bounded on-screen selection) already exercises routinely.
                for (int start = 0; start < messages.size(); start += FORWARD_CHUNK_SIZE) {
                    final int end = Math.min(start + FORWARD_CHUNK_SIZE, messages.size());
                    getSendMessagesHelper().sendMessage(new ArrayList<>(messages.subList(start, end)), did, false, false, notify, scheduleDate, 0);
                }
            }
            fragment1.finishFragment();
            selecting = false;
            selectedIndices.clear();
            BulletinFactory.of(this).createSimpleBulletin(R.raw.forward, "Переслано " + messages.size() + (messages.size() == 1 ? " сообщение" : " сообщений")).show();
            return true;
        });
        presentFragment(fragment);
    }
}
