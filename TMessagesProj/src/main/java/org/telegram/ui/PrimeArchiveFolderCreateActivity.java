package org.telegram.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.PrimeArchiveFolders;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.EditTextSettingsCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ListView.AdapterWithDiffUtils;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Objects;

/**
 * PrimeGram: full-screen create/edit UI for a local archive folder (see
 * {@link org.telegram.messenger.PrimeArchiveFolders}), built to visually match Telegram's own
 * folder-creation screen ({@link FilterCreateActivity}) - name field + list of chats, same cell
 * classes and save/discard flow - without cloning its icon picker or include/exclude rule engine,
 * neither of which apply to a plain local id-set folder.
 */
public class PrimeArchiveFolderCreateActivity extends BaseFragment {

    private static final int BUTTON_ADD_CHATS = 1;
    private static final int BUTTON_DELETE_FOLDER = 2;

    private static final int VIEW_TYPE_NAME = 0;
    private static final int VIEW_TYPE_SHADOW = 1;
    private static final int VIEW_TYPE_HEADER = 2;
    private static final int VIEW_TYPE_BUTTON = 3;
    private static final int VIEW_TYPE_CHAT = 4;

    private final int editingFolderId;
    private final ArrayList<Long> chatIds = new ArrayList<>();
    private final ArrayList<Long> originalChatIds = new ArrayList<>();
    private String originalName = "";

    private RecyclerListView listView;
    private ListAdapter adapter;
    private EditTextSettingsCell nameCell;
    private ActionBarMenuItem doneItem;

    private final ArrayList<ItemInner> oldItems = new ArrayList<>();
    private final ArrayList<ItemInner> items = new ArrayList<>();

    /** New folder, optionally pre-seeded with chats already selected elsewhere (e.g. the chat's
     *  own long-press menu, or a multi-select "add to new folder" action). */
    public PrimeArchiveFolderCreateActivity(ArrayList<Long> seedChatIds) {
        super();
        editingFolderId = 0;
        if (seedChatIds != null) {
            chatIds.addAll(seedChatIds);
        }
        originalChatIds.addAll(chatIds);
    }

    /** Edit an existing local archive folder. */
    public PrimeArchiveFolderCreateActivity(int folderId) {
        super();
        editingFolderId = folderId;
        final PrimeArchiveFolders.Folder folder = PrimeArchiveFolders.getInstance(currentAccount).getFolder(folderId);
        if (folder != null) {
            originalName = folder.name;
            chatIds.addAll(folder.dialogIds);
        }
        originalChatIds.addAll(chatIds);
    }

    private boolean isCreatingNew() {
        return editingFolderId == 0;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(isCreatingNew() ? R.string.Create : R.string.Edit));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    closeFragment();
                } else if (id == 1) {
                    processDone();
                }
            }
        });
        ActionBarMenu menu = actionBar.createMenu();
        doneItem = menu.addItem(1, LocaleController.getString(R.string.Save).toUpperCase());

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        });
        listView.setVerticalScrollBarEnabled(false);
        listView.setAdapter(adapter = new ListAdapter());
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setDurations(220);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setSupportsChangeAnimations(false);
        listView.setItemAnimator(itemAnimator);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= items.size()) {
                return;
            }
            ItemInner item = items.get(position);
            if (item.viewType == VIEW_TYPE_BUTTON && item.buttonId == BUTTON_ADD_CHATS) {
                openChatPicker();
            } else if (item.viewType == VIEW_TYPE_BUTTON && item.buttonId == BUTTON_DELETE_FOLDER) {
                confirmDeleteFolder();
            }
        });

        updateItems(false);
        return fragmentView;
    }

    private void openChatPicker() {
        UsersSelectActivity fragment = new UsersSelectActivity(true, new ArrayList<>(chatIds), 0);
        fragment.noChatTypes = true;
        fragment.setDelegate((ids, flags) -> {
            chatIds.clear();
            chatIds.addAll(ids);
            updateItems(true);
        });
        presentFragment(fragment);
    }

    private void confirmDeleteFolder() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
        builder.setTitle(LocaleController.getString(R.string.Delete));
        builder.setMessage("Папка «" + originalName + "» будет удалена. Чаты останутся в архиве.");
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) -> {
            PrimeArchiveFolders.getInstance(currentAccount).deleteFolder(editingFolderId);
            finishFragment();
        });
        AlertDialog alertDialog = builder.create();
        showDialog(alertDialog);
        TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(getThemedColor(Theme.key_text_RedBold));
        }
    }

    private String currentName() {
        return nameCell != null ? nameCell.getText().trim() : originalName;
    }

    private boolean hasChanges() {
        if (!TextUtils.equals(currentName(), originalName)) {
            return true;
        }
        return !new LinkedHashSet<>(chatIds).equals(new LinkedHashSet<>(originalChatIds));
    }

    private void processDone() {
        final String name = currentName();
        if (TextUtils.isEmpty(name)) {
            if (nameCell != null) {
                AndroidUtilities.shakeView(nameCell);
            }
            return;
        }
        final PrimeArchiveFolders instance = PrimeArchiveFolders.getInstance(currentAccount);
        if (isCreatingNew()) {
            final PrimeArchiveFolders.Folder folder = instance.createFolder(name);
            for (int a = 0, N = chatIds.size(); a < N; a++) {
                instance.addDialog(folder.id, chatIds.get(a));
            }
        } else {
            instance.renameFolder(editingFolderId, name);
            for (int a = 0, N = originalChatIds.size(); a < N; a++) {
                final long id = originalChatIds.get(a);
                if (!chatIds.contains(id)) {
                    instance.removeDialog(editingFolderId, id);
                }
            }
            for (int a = 0, N = chatIds.size(); a < N; a++) {
                final long id = chatIds.get(a);
                if (!originalChatIds.contains(id)) {
                    instance.addDialog(editingFolderId, id);
                }
            }
        }
        finishFragment();
    }

    private void closeFragment() {
        if (hasChanges()) {
            if (getParentActivity() == null) {
                finishFragment();
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
            builder.setTitle("Несохранённые изменения");
            builder.setMessage("Сохранить изменения в папке перед выходом?");
            builder.setPositiveButton(LocaleController.getString(R.string.Save), (dialog, which) -> processDone());
            builder.setNegativeButton("Не сохранять", (dialog, which) -> finishFragment());
            showDialog(builder.create());
        } else {
            finishFragment();
        }
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        closeFragment();
        return false;
    }

    private void updateItems(boolean animated) {
        oldItems.clear();
        oldItems.addAll(items);
        items.clear();

        items.add(ItemInner.name());
        items.add(ItemInner.shadow());
        ItemInner addChatsItem = ItemInner.button("Добавить чаты", BUTTON_ADD_CHATS, false);
        addChatsItem.iconResId = R.drawable.msg_addfolder;
        items.add(addChatsItem);

        if (!chatIds.isEmpty()) {
            items.add(ItemInner.header("В папке: " + chatIds.size()));
            for (int a = 0, N = chatIds.size(); a < N; a++) {
                items.add(ItemInner.chat(chatIds.get(a)));
            }
        }
        items.add(ItemInner.shadow());

        if (!isCreatingNew()) {
            ItemInner deleteItem = ItemInner.button(LocaleController.getString(R.string.Delete), BUTTON_DELETE_FOLDER, true);
            deleteItem.iconResId = R.drawable.msg_delete;
            items.add(deleteItem);
            items.add(ItemInner.shadow());
        }

        if (adapter == null) {
            return;
        }
        if (animated) {
            adapter.setItems(oldItems, items);
        } else {
            adapter.notifyDataSetChanged();
        }
    }

    private static class ItemInner extends AdapterWithDiffUtils.Item {
        CharSequence text;
        long chatId;
        int buttonId;
        int iconResId;
        boolean red;

        ItemInner(int viewType) {
            super(viewType, false);
        }

        static ItemInner name() {
            return new ItemInner(VIEW_TYPE_NAME);
        }

        static ItemInner shadow() {
            return new ItemInner(VIEW_TYPE_SHADOW);
        }

        static ItemInner header(CharSequence text) {
            ItemInner item = new ItemInner(VIEW_TYPE_HEADER);
            item.text = text;
            return item;
        }

        static ItemInner button(CharSequence text, int buttonId, boolean red) {
            ItemInner item = new ItemInner(VIEW_TYPE_BUTTON);
            item.text = text;
            item.buttonId = buttonId;
            item.red = red;
            return item;
        }

        static ItemInner chat(long chatId) {
            ItemInner item = new ItemInner(VIEW_TYPE_CHAT);
            item.chatId = chatId;
            return item;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ItemInner)) {
                return false;
            }
            ItemInner other = (ItemInner) o;
            return viewType == other.viewType && chatId == other.chatId && buttonId == other.buttonId
                    && red == other.red && Objects.equals(text, other.text);
        }
    }

    private class ListAdapter extends AdapterWithDiffUtils {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case VIEW_TYPE_NAME: {
                    EditTextSettingsCell cell = nameCell = new EditTextSettingsCell(getContext());
                    cell.getTextView().setHint("Название папки");
                    cell.setText(originalName, false);
                    view = cell;
                    break;
                }
                case VIEW_TYPE_HEADER:
                    view = new HeaderCell(getContext(), 22);
                    break;
                case VIEW_TYPE_BUTTON:
                    view = new TextCell(getContext());
                    break;
                case VIEW_TYPE_CHAT: {
                    UserCell cell = new UserCell(getContext(), 6, 0, false);
                    view = cell;
                    break;
                }
                case VIEW_TYPE_SHADOW:
                default:
                    view = new ShadowSectionCell(getContext());
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (position < 0 || position >= items.size()) {
                return;
            }
            ItemInner item = items.get(position);
            boolean divider = position + 1 < items.size() && items.get(position + 1).viewType == item.viewType;
            switch (holder.getItemViewType()) {
                case VIEW_TYPE_HEADER:
                    ((HeaderCell) holder.itemView).setText(item.text);
                    break;
                case VIEW_TYPE_BUTTON: {
                    TextCell cell = (TextCell) holder.itemView;
                    if (item.red) {
                        cell.setColors(Theme.key_text_RedRegular, Theme.key_text_RedRegular);
                    } else {
                        cell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlackText);
                    }
                    cell.setTextAndIcon(item.text, item.iconResId, divider);
                    break;
                }
                case VIEW_TYPE_CHAT: {
                    UserCell userCell = (UserCell) holder.itemView;
                    long id = item.chatId;
                    if (id > 0) {
                        TLRPC.User user = getMessagesController().getUser(id);
                        if (user != null) {
                            userCell.setData(user, null, null, 0, divider);
                        }
                    } else {
                        TLRPC.Chat chat = getMessagesController().getChat(-id);
                        if (chat != null) {
                            String status = chat.participants_count > 0
                                    ? LocaleController.formatPluralStringComma(chat.megagroup ? "Members" : "Subscribers", chat.participants_count)
                                    : null;
                            userCell.setData(chat, null, status, 0, divider);
                        }
                    }
                    break;
                }
                default:
                    break;
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == VIEW_TYPE_BUTTON || type == VIEW_TYPE_CHAT;
        }

        @Override
        public int getItemViewType(int position) {
            if (position < 0 || position >= items.size()) {
                return VIEW_TYPE_SHADOW;
            }
            return items.get(position).viewType;
        }
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        if (listView != null) {
            listView.setPadding(0, 0, 0, bottom);
            listView.setClipToPadding(false);
        }
    }
}
