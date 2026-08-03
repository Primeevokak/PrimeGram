package org.telegram.ui.Adapters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Keep;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.DrawerLayoutContainer;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DividerCell;
import org.telegram.ui.Cells.DrawerActionCell;
import org.telegram.ui.Cells.DrawerAddCell;
import org.telegram.ui.Cells.DrawerProfileCell;
import org.telegram.ui.Cells.DrawerProxyCell;
import org.telegram.ui.Cells.DrawerUserCell;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SideMenultItemAnimator;

import java.util.ArrayList;
import java.util.Collections;

/** PrimeGram: restored from Telegram-Android pre-redesign history (side drawer's list adapter),
 *  adds inugram's quick proxy-toggle row (view type 7 / {@link DrawerProxyCell}). */
public class DrawerLayoutAdapter extends RecyclerListView.SelectionAdapter {

    public static final int ITEM_PROXY = 9;
    public static final int ITEM_GHOST = 12;
    public static final int ITEM_BROWSER = 20;
    public static final int ITEM_WALLET = 21;
    public static final int ITEM_PARTNER = 22;

    private Context mContext;
    private DrawerLayoutContainer mDrawerLayoutContainer;
    private ArrayList<Item> items = new ArrayList<>(11);
    private ArrayList<Integer> accountNumbers = new ArrayList<>();
    private boolean accountsShown;
    public DrawerProfileCell profileCell;
    private SideMenultItemAnimator itemAnimator;
    public DrawerProxyCell.OnSwitchToggled onProxySwitchToggled;
    public DrawerProxyCell.OnSwitchToggled onGhostSwitchToggled;

    public DrawerLayoutAdapter(Context context, SideMenultItemAnimator animator, DrawerLayoutContainer drawerLayoutContainer) {
        mContext = context;
        mDrawerLayoutContainer = drawerLayoutContainer;
        itemAnimator = animator;
        accountsShown = UserConfig.getActivatedAccountsCount() > 1 && MessagesController.getGlobalMainSettings().getBoolean("accountsShown", true);
        Theme.createCommonDialogResources(context);
        resetItems();
    }

    private int getAccountRowsCount() {
        int count = accountNumbers.size() + 1;
        if (accountNumbers.size() < UserConfig.MAX_ACCOUNT_COUNT) {
            count++;
        }
        return count;
    }

    @Override
    public int getItemCount() {
        int count = items.size() + 2;
        if (accountsShown) {
            count += getAccountRowsCount();
        }
        return count;
    }

    public void setAccountsShown(boolean value, boolean animated) {
        if (accountsShown == value || itemAnimator.isRunning()) {
            return;
        }
        accountsShown = value;
        if (profileCell != null) {
            profileCell.setAccountsShown(accountsShown, animated);
        }
        MessagesController.getGlobalMainSettings().edit().putBoolean("accountsShown", accountsShown).commit();
        if (animated) {
            itemAnimator.setShouldClipChildren(false);
            if (accountsShown) {
                notifyItemRangeInserted(2, getAccountRowsCount());
            } else {
                notifyItemRangeRemoved(2, getAccountRowsCount());
            }
        } else {
            notifyDataSetChanged();
        }
    }

    public boolean isAccountsShown() {
        return accountsShown;
    }

    @Override
    public void notifyDataSetChanged() {
        resetItems();
        super.notifyDataSetChanged();
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        int itemType = holder.getItemViewType();
        return itemType == 3 || itemType == 4 || itemType == 5 || itemType == 6 || itemType == 7;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view;
        switch (viewType) {
            case 0:
                view = profileCell = new DrawerProfileCell(mContext, mDrawerLayoutContainer);
                break;
            case 2:
                view = new DividerCell(mContext);
                break;
            case 3:
                view = new DrawerActionCell(mContext);
                break;
            case 4:
                view = new DrawerUserCell(mContext);
                break;
            case 5:
                view = new DrawerAddCell(mContext);
                break;
            case 7:
            case 8:
                view = new DrawerProxyCell(mContext);
                break;
            case 1:
            default:
                view = new EmptyCell(mContext, AndroidUtilities.dp(8));
                break;
        }
        view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new RecyclerListView.Holder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        switch (holder.getItemViewType()) {
            case 0: {
                DrawerProfileCell profileCell = (DrawerProfileCell) holder.itemView;
                profileCell.setUser(MessagesController.getInstance(UserConfig.selectedAccount).getUser(UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId()), accountsShown);
                break;
            }
            case 3: {
                DrawerActionCell drawerActionCell = (DrawerActionCell) holder.itemView;
                int pos = position - 2;
                if (accountsShown) {
                    pos -= getAccountRowsCount();
                }
                items.get(pos).bind(drawerActionCell);
                drawerActionCell.setPadding(0, 0, 0, 0);
                break;
            }
            case 4: {
                DrawerUserCell drawerUserCell = (DrawerUserCell) holder.itemView;
                drawerUserCell.setAccount(accountNumbers.get(position - 2));
                break;
            }
            case 7: {
                DrawerProxyCell drawerProxyCell = (DrawerProxyCell) holder.itemView;
                int pos = position - 2;
                if (accountsShown) {
                    pos -= getAccountRowsCount();
                }
                Item item = items.get(pos);
                drawerProxyCell.bind(item.text, item.icon);
                boolean hasProxies = !SharedConfig.proxyList.isEmpty();
                drawerProxyCell.setSwitchVisible(hasProxies);
                drawerProxyCell.setChecked(SharedConfig.isProxyEnabled());
                drawerProxyCell.onSwitchToggled = onProxySwitchToggled;
                break;
            }
            case 8: {
                DrawerProxyCell ghostCell = (DrawerProxyCell) holder.itemView;
                int pos = position - 2;
                if (accountsShown) {
                    pos -= getAccountRowsCount();
                }
                Item item = items.get(pos);
                ghostCell.bind(item.text, item.icon);
                ghostCell.setSwitchVisible(true);
                ghostCell.setChecked(org.telegram.messenger.GreyZone.isGhostModeOn());
                ghostCell.onSwitchToggled = onGhostSwitchToggled;
                break;
            }
        }
    }

    @Override
    public int getItemViewType(int i) {
        if (i == 0) {
            return 0;
        } else if (i == 1) {
            return 1;
        }
        i -= 2;
        if (accountsShown) {
            if (i < accountNumbers.size()) {
                return 4;
            } else {
                if (accountNumbers.size() < UserConfig.MAX_ACCOUNT_COUNT) {
                    if (i == accountNumbers.size()) {
                        return 5;
                    } else if (i == accountNumbers.size() + 1) {
                        return 2;
                    }
                } else {
                    if (i == accountNumbers.size()) {
                        return 2;
                    }
                }
            }
            i -= getAccountRowsCount();
        }
        if (i < 0 || i >= items.size() || items.get(i) == null) {
            return 2;
        }
        if (items.get(i).id == ITEM_PROXY) {
            return 7;
        }
        if (items.get(i).id == ITEM_GHOST) {
            return 8;
        }
        return 3;
    }

    public void swapElements(int fromIndex, int toIndex) {
        int idx1 = fromIndex - 2;
        int idx2 = toIndex - 2;
        if (idx1 < 0 || idx2 < 0 || idx1 >= accountNumbers.size() || idx2 >= accountNumbers.size()) {
            return;
        }
        final UserConfig userConfig1 = UserConfig.getInstance(accountNumbers.get(idx1));
        final UserConfig userConfig2 = UserConfig.getInstance(accountNumbers.get(idx2));
        final int tempLoginTime = userConfig1.loginTime;
        userConfig1.loginTime = userConfig2.loginTime;
        userConfig2.loginTime = tempLoginTime;
        userConfig1.saveConfig(false);
        userConfig2.saveConfig(false);
        Collections.swap(accountNumbers, idx1, idx2);
        notifyItemMoved(fromIndex, toIndex);
    }

    private void resetItems() {
        accountNumbers.clear();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                accountNumbers.add(a);
            }
        }
        Collections.sort(accountNumbers, (o1, o2) -> {
            long l1 = UserConfig.getInstance(o1).loginTime;
            long l2 = UserConfig.getInstance(o2).loginTime;
            if (l1 > l2) {
                return 1;
            } else if (l1 < l2) {
                return -1;
            }
            return 0;
        });

        items.clear();
        if (!UserConfig.getInstance(UserConfig.selectedAccount).isClientActivated()) {
            return;
        }

        items.add(new Item(16, LocaleController.getString(R.string.MyProfile), R.drawable.left_status_profile));
        TLRPC.TL_attachMenuBots menuBots = MediaDataController.getInstance(UserConfig.selectedAccount).getAttachMenuBots();
        boolean showDivider = false;
        if (menuBots != null && menuBots.bots != null) {
            for (int i = 0; i < menuBots.bots.size(); i++) {
                TLRPC.TL_attachMenuBot bot = menuBots.bots.get(i);
                if (bot.show_in_side_menu) {
                    items.add(new Item(bot));
                    showDivider = true;
                }
            }
        }
        if (showDivider) {
            items.add(null); // divider
        }
        // PrimeGram: New Group and Calls dropped here - this menu carries the same curated set
        // as the PrimeGram side panel it replaces, not the stock Telegram drawer's full list.
        items.add(new Item(6, LocaleController.getString(R.string.Contacts), R.drawable.msg_contacts));
        items.add(new Item(11, LocaleController.getString(R.string.SavedMessages), R.drawable.msg_saved));
        items.add(new Item(ITEM_PROXY, LocaleController.getString(R.string.ProxySettings), R.drawable.outline_shield_check));
        items.add(new Item(8, LocaleController.getString(R.string.Settings), R.drawable.msg_settings_old));
        items.add(null); // divider
        items.add(new Item(ITEM_BROWSER, "Браузер", R.drawable.msg_language));
        items.add(new Item(ITEM_WALLET, "Кошелёк", R.drawable.settings_wallet));
        if (org.telegram.messenger.GreyZone.isAccepted()) {
            items.add(new Item(ITEM_GHOST, "Режим призрака", R.drawable.msg_ghost_24));
        }
        items.add(new Item(ITEM_PARTNER, "Наш партнёр", R.drawable.msg_channel));
    }

    public boolean click(View view, int position) {
        position -= 2;
        if (accountsShown) {
            position -= getAccountRowsCount();
        }
        if (position < 0 || position >= items.size()) {
            return false;
        }
        Item item = items.get(position);
        if (item != null && item.listener != null) {
            item.listener.onClick(view);
            return true;
        }
        return false;
    }

    public int getId(int position) {
        position -= 2;
        if (accountsShown) {
            position -= getAccountRowsCount();
        }
        if (position < 0 || position >= items.size()) {
            return -1;
        }
        Item item = items.get(position);
        return item != null ? item.id : -1;
    }

    public int getFirstAccountPosition() {
        if (!accountsShown) {
            return RecyclerView.NO_POSITION;
        }
        return 2;
    }

    public int getLastAccountPosition() {
        if (!accountsShown) {
            return RecyclerView.NO_POSITION;
        }
        return 1 + accountNumbers.size();
    }

    public TLRPC.TL_attachMenuBot getAttachMenuBot(int position) {
        position -= 2;
        if (accountsShown) {
            position -= getAccountRowsCount();
        }
        if (position < 0 || position >= items.size()) {
            return null;
        }
        Item item = items.get(position);
        return item != null ? item.bot : null;
    }

    public static class Item {
        public int icon;
        public CharSequence text;
        public int id;
        TLRPC.TL_attachMenuBot bot;
        View.OnClickListener listener;
        public boolean error;

        public Item(int id, CharSequence text, int icon) {
            this.icon = icon;
            this.id = id;
            this.text = text;
        }

        public Item(TLRPC.TL_attachMenuBot bot) {
            this.bot = bot;
            this.id = (int) (100 + (bot.bot_id >> 16));
        }

        public void bind(DrawerActionCell actionCell) {
            if (this.bot != null) {
                actionCell.setBot(bot);
            } else {
                actionCell.setTextAndIcon(id, text, icon);
            }
            actionCell.setError(error);
        }

        @Keep
        public Item onClick(View.OnClickListener listener) {
            this.listener = listener;
            return this;
        }

        @Keep
        public Item withError() {
            this.error = true;
            return this;
        }
    }
}
