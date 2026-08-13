package org.telegram.ui;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.TL_auth_importBotAuthorization;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

/**
 * PrimeGram: signs in to a bot account with its BotFather token.
 *
 * <p>A bot authorization is a full account session, not an add-on to the current one, so it
 * lands in one of Telegram's four account slots and is switched to like any other account.
 * That is a protocol fact, not a UI choice: the bot's dialogs live on the bot's session and
 * are unreachable from the user's.
 */
public class BotLoginActivity extends BaseFragment {

    private EditTextBoldCursor tokenField;
    private TextView statusView;
    private AlertDialog progressDialog;
    private boolean requesting;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Вход в бота");
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

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        card.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(16), AndroidUtilities.dp(20), AndroidUtilities.dp(16));

        tokenField = new EditTextBoldCursor(context);
        tokenField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        tokenField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        tokenField.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        tokenField.setHint("123456789:ABCdef...");
        tokenField.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        tokenField.setCursorSize(AndroidUtilities.dp(20));
        tokenField.setSingleLine(true);
        tokenField.setBackgroundDrawable(Theme.createEditTextDrawable(context, true));
        card.addView(tokenField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView loginButton = new TextView(context);
        loginButton.setText("Войти");
        loginButton.setGravity(Gravity.CENTER);
        loginButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        loginButton.setTypeface(AndroidUtilities.bold());
        loginButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        loginButton.setBackground(Theme.AdaptiveRipple.filledRect(Theme.getColor(Theme.key_featuredStickers_addButton), 6));
        loginButton.setOnClickListener(v -> login());
        card.addView(loginButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 0, 16, 0, 0));

        root.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        statusView = new TextView(context);
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        statusView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
        statusView.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(14), AndroidUtilities.dp(20), AndroidUtilities.dp(14));
        statusView.setText(describeSlots());
        root.addView(statusView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        fragmentView = root;
        return fragmentView;
    }

    private String describeSlots() {
        int free = 0;
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (!UserConfig.getInstance(a).isClientActivated()) {
                free++;
            }
        }
        return "Токен выдаёт @BotFather командой /token.\n\n"
                + "Бот входит как отдельный аккаунт — так устроен Telegram, сессия бота отдельна от вашей. "
                + "После входа переключайтесь на бота через боковую панель, как между обычными аккаунтами.\n\n"
                + "Свободных слотов аккаунтов: " + free + " из " + UserConfig.MAX_ACCOUNT_COUNT + ".";
    }

    /** @return a free account slot, or -1 when all four are taken */
    private int findFreeAccount() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (!UserConfig.getInstance(a).isClientActivated()) {
                return a;
            }
        }
        return -1;
    }

    private void login() {
        if (requesting || getParentActivity() == null) {
            return;
        }
        final String token = tokenField.getText().toString().trim();
        if (TextUtils.isEmpty(token) || !token.contains(":")) {
            statusView.setText("Это не похоже на токен. Он выглядит так: 123456789:ABCdef…");
            return;
        }
        final int account = findFreeAccount();
        if (account < 0) {
            statusView.setText("Все " + UserConfig.MAX_ACCOUNT_COUNT + " слота аккаунтов заняты. Выйдите из одного, чтобы войти в бота.");
            return;
        }

        AndroidUtilities.hideKeyboard(tokenField);
        requesting = true;
        progressDialog = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progressDialog.setCanCancel(false);
        progressDialog.show();

        TL_auth_importBotAuthorization req = new TL_auth_importBotAuthorization();
        req.flags = 0;
        req.api_id = BuildVars.APP_ID;
        req.api_hash = BuildVars.APP_HASH;
        req.bot_auth_token = token;

        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            requesting = false;
            dismissProgress();
            if (error != null || !(response instanceof TLRPC.TL_auth_authorization)) {
                statusView.setText("Не удалось войти: " + (error != null ? error.text : "неизвестная ошибка")
                        + "\n\nПроверьте, что токен свежий — /revoke в @BotFather делает старый недействительным.");
                return;
            }
            applyAuthorization(account, (TLRPC.TL_auth_authorization) response);
        }), ConnectionsManager.RequestFlagWithoutLogin | ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    /**
     * Installs a fresh authorization into an empty account slot. Mirrors what the phone-number
     * login does after the code check — the slot is wiped first so no remnant of a previous
     * account in that slot can leak into the bot's session.
     */
    private void applyAuthorization(int account, TLRPC.TL_auth_authorization res) {
        try {
            MessagesController.getInstance(account).cleanup();
            ConnectionsManager.getInstance(account).setUserId(res.user.id);
            UserConfig.getInstance(account).clearConfig();
            MessagesController.getInstance(account).cleanup();
            UserConfig.getInstance(account).syncContacts = false;
            UserConfig.getInstance(account).setCurrentUser(res.user);
            UserConfig.getInstance(account).saveConfig(true);
            MessagesStorage.getInstance(account).cleanup(true);
            ArrayList<TLRPC.User> users = new ArrayList<>();
            users.add(res.user);
            MessagesStorage.getInstance(account).putUsersAndChats(users, null, true, true);
            MessagesController.getInstance(account).putUser(res.user, false);
            ContactsController.getInstance(account).checkAppAccount();
            ConnectionsManager.getInstance(account).updateDcSettings();
            MessagesController.getInstance(account).loadAppConfig();
            MediaDataController.getInstance(account);

            if (getParentActivity() instanceof LaunchActivity) {
                // Otherwise the new account sits fully authorized on disk but is invisible in
                // both account-list UIs until the app restarts - switchToAccount() is normally
                // the only thing that refreshes them, and this flow deliberately doesn't call it
                // (unlike a normal login, this one shouldn't yank the user onto the bot's chat
                // list the moment a token is entered).
                ((LaunchActivity) getParentActivity()).refreshAccountsUi();
            }

            statusView.setText("Готово: вошли как " + UserObject.getUserName(res.user)
                    + ".\n\nПереключиться на бота можно в боковой панели — он теперь один из аккаунтов.");
            tokenField.setText("");
        } catch (Throwable t) {
            org.telegram.messenger.FileLog.e("BotLoginActivity.applyAuthorization", t);
            statusView.setText("Вход прошёл, но настроить аккаунт не удалось. Перезапустите приложение.");
        }
    }

    private void dismissProgress() {
        if (progressDialog != null) {
            try {
                progressDialog.dismiss();
            } catch (Throwable ignore) {}
            progressDialog = null;
        }
    }

    @Override
    public void onFragmentDestroy() {
        dismissProgress();
        super.onFragmentDestroy();
    }
}
