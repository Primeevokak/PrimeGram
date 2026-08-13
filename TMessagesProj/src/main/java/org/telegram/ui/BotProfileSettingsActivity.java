package org.telegram.ui;

import android.text.InputType;
import android.widget.EditText;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_bots;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/**
 * Bot profile fields the Bot API exposes but stock/PrimeGram screens have no room for: the long
 * "description" shown in the empty chat before /start (short "about"/name live in the normal
 * ChangeBioActivity/ChangeNameActivity, routed to {@code bots.setBotInfo} there), and the menu
 * button. Only reachable when {@link UserConfig#isBot()} - see {@link SettingsActivity}'s row 53.
 *
 * <p>All calls target {@code bots.setBotInfo}/{@code getBotInfo} and
 * {@code bots.setBotMenuButton}/{@code getBotMenuButton} with no explicit {@code bot}/{@code
 * user_id} target beyond {@link TLRPC.TL_inputUserSelf} - both act on the calling bot itself,
 * confirmed from the request classes in {@code TL_bots.java} rather than assumed.
 */
public class BotProfileSettingsActivity extends UniversalFragment {

    private static final int ID_DESCRIPTION = 1;
    private static final int ID_MENU_DEFAULT = 10;
    private static final int ID_MENU_COMMANDS = 11;
    private static final int ID_MENU_CUSTOM = 12;
    private static final int ID_MENU_CUSTOM_TEXT = 20;
    private static final int ID_MENU_CUSTOM_URL = 21;

    private static final int MENU_DEFAULT = 0;
    private static final int MENU_COMMANDS = 1;
    private static final int MENU_CUSTOM = 2;

    private boolean loaded;
    private String description = "";
    private int menuButtonType = MENU_DEFAULT;
    private String menuButtonText = "";
    private String menuButtonUrl = "";

    @Override
    protected CharSequence getTitle() {
        return "Управление ботом";
    }

    @Override
    public boolean onFragmentCreate() {
        boolean result = super.onFragmentCreate();
        loadBotInfo();
        loadMenuButton();
        return result;
    }

    private void loadBotInfo() {
        TL_bots.getBotInfo req = new TL_bots.getBotInfo();
        req.flags = 0;
        req.lang_code = "";
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TL_bots.BotInfo) {
                description = ((TL_bots.BotInfo) response).description;
                if (description == null) description = "";
            }
            loaded = true;
            if (listView != null && listView.adapter != null) listView.adapter.update(true);
        }), ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    private void loadMenuButton() {
        TL_bots.getBotMenuButton req = new TL_bots.getBotMenuButton();
        req.user_id = new TLRPC.TL_inputUserSelf();
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TL_bots.TL_botMenuButtonCommands) {
                menuButtonType = MENU_COMMANDS;
            } else if (response instanceof TL_bots.TL_botMenuButton) {
                menuButtonType = MENU_CUSTOM;
                menuButtonText = ((TL_bots.TL_botMenuButton) response).text;
                menuButtonUrl = ((TL_bots.TL_botMenuButton) response).url;
            } else {
                menuButtonType = MENU_DEFAULT;
            }
            if (listView != null && listView.adapter != null) listView.adapter.update(true);
        }), ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader("Описание"));
        items.add(UItem.asButton(ID_DESCRIPTION, "Описание", description.isEmpty() ? "Не задано" : description));
        items.add(UItem.asShadow("Показывается в пустом чате с ботом, до нажатия /start. До 512 символов."));

        items.add(UItem.asHeader("Кнопка меню"));
        UItem def = UItem.asCheck(ID_MENU_DEFAULT, "Нет");
        def.checked = menuButtonType == MENU_DEFAULT;
        items.add(def);
        UItem commands = UItem.asCheck(ID_MENU_COMMANDS, "Список команд");
        commands.checked = menuButtonType == MENU_COMMANDS;
        items.add(commands);
        UItem custom = UItem.asCheck(ID_MENU_CUSTOM, "Своя (Web App)");
        custom.checked = menuButtonType == MENU_CUSTOM;
        items.add(custom);
        if (menuButtonType == MENU_CUSTOM) {
            items.add(UItem.asButton(ID_MENU_CUSTOM_TEXT, "Текст кнопки", menuButtonText.isEmpty() ? "Не задан" : menuButtonText));
            items.add(UItem.asButton(ID_MENU_CUSTOM_URL, "Ссылка", menuButtonUrl.isEmpty() ? "Не задана" : menuButtonUrl));
        }
        items.add(UItem.asShadow("«Список команд» показывает список команд бота вместо клавиатуры. «Своя» открывает веб-приложение по ссылке — нужны и текст, и ссылка."));
    }

    @Override
    protected void onClick(UItem item, android.view.View view, int position, float x, float y) {
        if (item.id == ID_DESCRIPTION) {
            showTextInput("Описание", description, newValue -> {
                description = newValue;
                saveDescription();
                if (listView != null && listView.adapter != null) listView.adapter.update(true);
            });
        } else if (item.id == ID_MENU_DEFAULT) {
            menuButtonType = MENU_DEFAULT;
            saveMenuButton();
            if (listView != null && listView.adapter != null) listView.adapter.update(true);
        } else if (item.id == ID_MENU_COMMANDS) {
            menuButtonType = MENU_COMMANDS;
            saveMenuButton();
            if (listView != null && listView.adapter != null) listView.adapter.update(true);
        } else if (item.id == ID_MENU_CUSTOM) {
            menuButtonType = MENU_CUSTOM;
            if (listView != null && listView.adapter != null) listView.adapter.update(true);
            if (!menuButtonText.isEmpty() && !menuButtonUrl.isEmpty()) {
                saveMenuButton();
            }
        } else if (item.id == ID_MENU_CUSTOM_TEXT) {
            showTextInput("Текст кнопки", menuButtonText, newValue -> {
                menuButtonText = newValue;
                if (!menuButtonText.isEmpty() && !menuButtonUrl.isEmpty()) saveMenuButton();
                if (listView != null && listView.adapter != null) listView.adapter.update(true);
            });
        } else if (item.id == ID_MENU_CUSTOM_URL) {
            showTextInput("Ссылка (https://...)", menuButtonUrl, newValue -> {
                menuButtonUrl = newValue;
                if (!menuButtonText.isEmpty() && !menuButtonUrl.isEmpty()) saveMenuButton();
                if (listView != null && listView.adapter != null) listView.adapter.update(true);
            });
        }
    }

    @Override
    protected boolean onLongClick(UItem item, android.view.View view, int position, float x, float y) {
        return false;
    }

    private void saveDescription() {
        TL_bots.setBotInfo req = new TL_bots.setBotInfo();
        req.lang_code = "";
        req.description = description;
        req.flags |= 2;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (error != null) {
                FileLog.e("BotProfileSettingsActivity.saveDescription: " + error.text);
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    private void saveMenuButton() {
        TL_bots.setBotMenuButton req = new TL_bots.setBotMenuButton();
        req.user_id = new TLRPC.TL_inputUserSelf();
        switch (menuButtonType) {
            case MENU_COMMANDS:
                req.button = new TL_bots.TL_botMenuButtonCommands();
                break;
            case MENU_CUSTOM: {
                TL_bots.TL_botMenuButton button = new TL_bots.TL_botMenuButton();
                button.text = menuButtonText;
                button.url = menuButtonUrl;
                req.button = button;
                break;
            }
            default:
                req.button = new TL_bots.TL_botMenuButtonDefault();
                break;
        }
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (error != null) {
                FileLog.e("BotProfileSettingsActivity.saveMenuButton: " + error.text);
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    private interface TextInputCallback {
        void onResult(String value);
    }

    private void showTextInput(String title, String currentValue, TextInputCallback callback) {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(title);
        EditText editText = new EditText(getParentActivity());
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editText.setMinLines(1);
        editText.setMaxLines(6);
        editText.setText(currentValue);
        editText.setSelection(editText.getText().length());
        builder.setView(editText);
        builder.setPositiveButton(org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.OK),
                (dialog, which) -> callback.onResult(editText.getText().toString().trim()));
        builder.setNegativeButton(org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.Cancel), null);
        showDialog(builder.create());
    }
}
