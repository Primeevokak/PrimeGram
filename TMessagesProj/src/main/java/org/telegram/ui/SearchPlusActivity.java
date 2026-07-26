package org.telegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.Locale;

/**
 * PrimeGram "Поиск+": resolves a peer from whatever identifier the user has — a numeric id,
 * a phone number, an @username or a t.me link — including the cases the stock search box
 * refuses to handle.
 *
 * <p>Numeric ids are the interesting one: MTProto cannot fetch an arbitrary user by id alone,
 * it needs an access_hash the client only owns for peers it has already met. So an id lookup
 * is answered from the local cache first, and only then attempted over the network with a
 * zero hash — which succeeds for peers the account has any relationship with and fails
 * cleanly for the rest. That limit is the protocol's, not ours, so it is spelled out in the UI
 * instead of being reported as an error.
 */
public class SearchPlusActivity extends BaseFragment {

    private EditTextBoldCursor inputField;
    private LinearLayout resultsContainer;
    private TextView statusView;
    private boolean searching;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Поиск+");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        LinearLayout inputRow = new LinearLayout(context);
        inputRow.setOrientation(LinearLayout.VERTICAL);
        inputRow.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        inputRow.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(16), AndroidUtilities.dp(20), AndroidUtilities.dp(16));

        inputField = new EditTextBoldCursor(context);
        inputField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        inputField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        inputField.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        inputField.setHint("ID, телефон, @username или ссылка");
        inputField.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        inputField.setCursorSize(AndroidUtilities.dp(20));
        inputField.setSingleLine(true);
        inputField.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        inputField.setBackgroundDrawable(Theme.createEditTextDrawable(context, true));
        inputField.setOnEditorActionListener((v, actionId, event) -> {
            startSearch();
            return true;
        });
        inputRow.addView(inputField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView searchButton = new TextView(context);
        searchButton.setText("Найти");
        searchButton.setGravity(Gravity.CENTER);
        searchButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        searchButton.setTypeface(AndroidUtilities.bold());
        searchButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        searchButton.setBackground(Theme.AdaptiveRipple.filledRect(Theme.getColor(Theme.key_featuredStickers_addButton), 6));
        searchButton.setOnClickListener(v -> startSearch());
        inputRow.addView(searchButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 0, 16, 0, 0));

        root.addView(inputRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        statusView = new TextView(context);
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        statusView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
        statusView.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(12), AndroidUtilities.dp(20), AndroidUtilities.dp(12));
        statusView.setText("Работает там, где обычный поиск бессилен: по числовому ID, по номеру телефона, по @username и по ссылке t.me.\n\nПоиск по ID возможен только для тех, кого ваш аккаунт уже встречал — этого требует протокол Telegram, а не клиент.");
        root.addView(statusView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        resultsContainer = new LinearLayout(context);
        resultsContainer.setOrientation(LinearLayout.VERTICAL);

        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(resultsContainer, new android.widget.FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        root.addView(scrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        fragmentView = root;
        return fragmentView;
    }

    private void setStatus(String text) {
        if (statusView != null) {
            statusView.setText(text);
        }
    }

    private void startSearch() {
        if (searching) {
            return;
        }
        String query = inputField == null ? null : inputField.getText().toString().trim();
        if (TextUtils.isEmpty(query)) {
            return;
        }
        AndroidUtilities.hideKeyboard(inputField);
        resultsContainer.removeAllViews();
        searching = true;
        setStatus("Ищу…");

        String username = extractUsername(query);
        if (username != null) {
            resolveUsername(username);
            return;
        }
        String phone = extractPhone(query);
        if (phone != null) {
            resolvePhone(phone);
            return;
        }
        Long id = extractId(query);
        if (id != null) {
            resolveId(id);
            return;
        }
        searching = false;
        setStatus("Не удалось понять, что это. Введите числовой ID, номер телефона, @username или ссылку t.me.");
    }

    /** @return the bare username, or null when the query is not a username/link */
    private String extractUsername(String query) {
        String value = query;
        if (value.startsWith("@")) {
            value = value.substring(1);
        } else {
            int index = value.indexOf("t.me/");
            if (index >= 0) {
                value = value.substring(index + 5);
            } else if (value.startsWith("tg://resolve?domain=")) {
                value = value.substring("tg://resolve?domain=".length());
            } else {
                return null;
            }
            int cut = value.indexOf('?');
            if (cut >= 0) {
                value = value.substring(0, cut);
            }
            cut = value.indexOf('/');
            if (cut >= 0) {
                value = value.substring(0, cut);
            }
            // A "+"-prefixed link is a private invite, not a username — a different request.
            if (value.startsWith("+")) {
                return null;
            }
        }
        value = value.trim();
        return value.isEmpty() ? null : value;
    }

    private String extractPhone(String query) {
        String digits = query.replaceAll("[^0-9]", "");
        // Only treat it as a phone when it actually looked like one: a leading + or spacing.
        if (digits.length() < 7 || digits.length() > 15) {
            return null;
        }
        if (!query.startsWith("+") && !query.contains(" ") && !query.contains("-") && !query.contains("(")) {
            return null;
        }
        return digits;
    }

    private Long extractId(String query) {
        try {
            return Long.parseLong(query.replace(" ", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void resolveUsername(String username) {
        TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = username;
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            searching = false;
            if (error != null || !(response instanceof TLRPC.TL_contacts_resolvedPeer)) {
                setStatus("@" + username + " — ничего не найдено.");
                return;
            }
            TLRPC.TL_contacts_resolvedPeer resolved = (TLRPC.TL_contacts_resolvedPeer) response;
            getMessagesController().putUsers(resolved.users, false);
            getMessagesController().putChats(resolved.chats, false);
            getMessagesStorage().putUsersAndChats(resolved.users, resolved.chats, true, true);
            showResults(resolved.users, resolved.chats);
        }));
    }

    private void resolvePhone(String phone) {
        TLRPC.TL_contacts_resolvePhone req = new TLRPC.TL_contacts_resolvePhone();
        req.phone = phone;
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            searching = false;
            if (error != null || !(response instanceof TLRPC.TL_contacts_resolvedPeer)) {
                setStatus("+" + phone + " — не найдено. Номер виден только если владелец не скрыл его от посторонних.");
                return;
            }
            TLRPC.TL_contacts_resolvedPeer resolved = (TLRPC.TL_contacts_resolvedPeer) response;
            getMessagesController().putUsers(resolved.users, false);
            getMessagesController().putChats(resolved.chats, false);
            getMessagesStorage().putUsersAndChats(resolved.users, resolved.chats, true, true);
            showResults(resolved.users, resolved.chats);
        }));
    }

    private void resolveId(long id) {
        // Cache first: no request needed for anyone we have already loaded.
        TLRPC.User cachedUser = getMessagesController().getUser(id > 0 ? id : -id);
        if (cachedUser != null) {
            searching = false;
            ArrayList<TLRPC.User> users = new ArrayList<>();
            users.add(cachedUser);
            showResults(users, null);
            return;
        }
        long chatId = id < 0 ? -id : id;
        if (chatId > 1000000000000L) {
            chatId -= 1000000000000L; // -100xxxxxxxxxx channel form
        }
        TLRPC.Chat cachedChat = getMessagesController().getChat(chatId);
        if (cachedChat != null) {
            searching = false;
            ArrayList<TLRPC.Chat> chats = new ArrayList<>();
            chats.add(cachedChat);
            showResults(null, chats);
            return;
        }

        // Not cached: ask the server with an empty access_hash. Works for peers the account
        // has some relationship with, fails for strangers — which is the protocol's rule.
        TLRPC.TL_users_getUsers req = new TLRPC.TL_users_getUsers();
        TLRPC.TL_inputUser inputUser = new TLRPC.TL_inputUser();
        inputUser.user_id = id > 0 ? id : -id;
        inputUser.access_hash = 0;
        req.id.add(inputUser);
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            searching = false;
            if (error != null || !(response instanceof org.telegram.tgnet.Vector) || ((org.telegram.tgnet.Vector<?>) response).objects.isEmpty()) {
                setStatus("ID " + id + " — не найдено.\n\nTelegram отдаёт профиль по ID только тем, у кого уже есть с ним общая точка: переписка, общая группа, контакт или пересланное сообщение. Если её нет, ID сам по себе бесполезен — это ограничение протокола.");
                return;
            }
            ArrayList<TLRPC.User> users = new ArrayList<>();
            for (Object object : ((org.telegram.tgnet.Vector<?>) response).objects) {
                if (object instanceof TLRPC.User) {
                    users.add((TLRPC.User) object);
                }
            }
            if (users.isEmpty()) {
                setStatus("ID " + id + " — не найдено.");
                return;
            }
            getMessagesController().putUsers(users, false);
            getMessagesStorage().putUsersAndChats(users, null, true, true);
            showResults(users, null);
        }));
    }

    private void showResults(ArrayList<TLRPC.User> users, ArrayList<TLRPC.Chat> chats) {
        resultsContainer.removeAllViews();
        int count = 0;
        if (users != null) {
            for (TLRPC.User user : users) {
                if (user == null) {
                    continue;
                }
                addRow(UserObject.getUserName(user),
                        (TextUtils.isEmpty(UserObject.getPublicUsername(user)) ? "" : "@" + UserObject.getPublicUsername(user) + "  •  ") + "ID " + user.id,
                        user.id);
                count++;
            }
        }
        if (chats != null) {
            for (TLRPC.Chat chat : chats) {
                if (chat == null) {
                    continue;
                }
                addRow(chat.title,
                        (TextUtils.isEmpty(ChatObject.getPublicUsername(chat)) ? "" : "@" + ChatObject.getPublicUsername(chat) + "  •  ") + "ID " + (-chat.id),
                        -chat.id);
                count++;
            }
        }
        setStatus(count == 0 ? "Ничего не найдено." : "Найдено: " + count + ". Нажмите, чтобы открыть.");
    }

    private void addRow(String title, String subtitle, long dialogId) {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackground(Theme.getSelectorDrawable(false));
        row.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(12), AndroidUtilities.dp(20), AndroidUtilities.dp(12));
        row.setOnClickListener(v -> openDialog(dialogId));

        TextView titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setText(TextUtils.isEmpty(title) ? "(без имени)" : title);
        row.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
        subtitleView.setText(subtitle);
        row.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        resultsContainer.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    private void openDialog(long dialogId) {
        Bundle args = new Bundle();
        if (dialogId > 0) {
            args.putLong("user_id", dialogId);
        } else {
            args.putLong("chat_id", -dialogId);
        }
        presentFragment(new ChatActivity(args));
    }
}
