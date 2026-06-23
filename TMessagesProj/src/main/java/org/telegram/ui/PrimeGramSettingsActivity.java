package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import vpn.sdk.VpnSDK;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import java.util.ArrayList;

public class PrimeGramSettingsActivity extends UniversalFragment {

    private static final int ID_LIMIT_FOLDERS = 1;
    private static final int ID_LIMIT_PINNED_FOLDER = 2;
    private static final int ID_LIMIT_PINNED_SAVED = 3;
    private static final int ID_LIMIT_CHANNELS = 4;
    private static final int ID_LIMIT_GIFS = 5;
    private static final int ID_LIMIT_STICKERS = 6;
    private static final int ID_LIMIT_CHATS_IN_FOLDER = 7;
    private static final int ID_LIMIT_PUBLIC_LINKS = 8;
    private static final int ID_LIMIT_CAPTION = 9;
    private static final int ID_LIMIT_ABOUT = 10;
    private static final int ID_SIDEBAR_ENABLED = 11;
    private static final int ID_SUPPORT_PROJECT = 12;
    private static final int ID_GRANT_PERMISSIONS = 13;
    private static final int ID_AUTO_UPDATES = 14;
    private static final int ID_CHECK_UPDATES = 15;
    private static final int ID_FEED_EXCLUDE_MUTED = 16;
    private static final int ID_FEED_EXCLUDE_ARCHIVED = 17;
    private static final int ID_EMERGENCY_PROXY = 18;
    private static final int ID_HW_ACCEL = 19;
    @Override
    protected CharSequence getTitle() {
        return "Настройки PrimeGram";
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader("Интерфейс"));
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        boolean sidebarEnabled = preferences.getBoolean("primegram_sidebar_enabled", true);
        UItem checkItem = UItem.asCheck(ID_SIDEBAR_ENABLED, "Боковая панель на основном экране");
        checkItem.checked = sidebarEnabled;
        items.add(checkItem);
        items.add(UItem.asShadow("Отображает стильную вертикальную боковую панель на главном экране списка чатов для быстрого доступа к переключению аккаунтов, кошельку, прокси и настройкам."));

        items.add(UItem.asHeader("Соединение"));
        UItem proxyItem = UItem.asCheck(ID_EMERGENCY_PROXY, "Аварийный VLESS-прокси");
        proxyItem.checked = VpnSDK.isProxyRunning();
        items.add(proxyItem);
        items.add(UItem.asShadow("В случае проблем с основным прокси, вы можете включить аварийный VLESS-прокси (AmneziaWG) для обхода блокировок."));

        items.add(UItem.asHeader("Экспериментальные настройки"));
        boolean hwAccel = preferences.getBoolean("primegram_hw_accel", false);
        UItem hwAccelItem = UItem.asCheck(ID_HW_ACCEL, "Аппаратное ускорение (MediaCodec/OpenGL)");
        hwAccelItem.checked = hwAccel;
        items.add(hwAccelItem);
        items.add(UItem.asShadow("Включает обработку видео, стикеров и эффектов размытия на видеоядре вместо центрального процессора. Заметно экономит батарею, но может вызвать артефакты на несовместимых устройствах."));

        items.add(UItem.asHeader("Лента"));
        boolean feedExcludeMuted = preferences.getBoolean("primegram_feed_exclude_muted", false);
        boolean feedExcludeArchived = preferences.getBoolean("primegram_feed_exclude_archived", false);
        UItem feedExcludeMutedItem = UItem.asCheck(ID_FEED_EXCLUDE_MUTED, "Скрывать чаты без уведомлений");
        feedExcludeMutedItem.checked = feedExcludeMuted;
        items.add(feedExcludeMutedItem);
        UItem feedExcludeArchivedItem = UItem.asCheck(ID_FEED_EXCLUDE_ARCHIVED, "Скрывать чаты из архива");
        feedExcludeArchivedItem.checked = feedExcludeArchived;
        items.add(feedExcludeArchivedItem);
        items.add(UItem.asShadow("Настройки отображения каналов и групп во вкладке Лента."));

        items.add(UItem.asHeader("Обновления приложения"));
        boolean autoUpdates = preferences.getBoolean("primegram_auto_updates", false);
        UItem autoUpdatesItem = UItem.asCheck(ID_AUTO_UPDATES, "Автоматически скачивать обновления");
        autoUpdatesItem.checked = autoUpdates;
        items.add(autoUpdatesItem);
        items.add(UItem.asButton(ID_CHECK_UPDATES, "Проверить обновления", ""));
        items.add(UItem.asShadow("PrimeGram может автоматически проверять релизы на GitHub и скачивать новые версии."));

        MessagesController messagesController = MessagesController.getInstance(currentAccount);

        items.add(UItem.asHeader("Telegram Premium (Локальный)"));
        items.add(UItem.asShadow("На этом устройстве полностью эмулируется подписка Telegram Premium: разблокированы Saved Messages теги, кастомные обои, расшифровка голосовых сообщений, перевод чатов и каналов, бесконечные реакции, эмодзи-статусы, значок в профиле и отсутствие рекламы. Ниже вы можете настроить локальные лимиты."));

        items.add(UItem.asHeader("Лимиты чатов и папок"));
        items.add(UItem.asButton(ID_LIMIT_FOLDERS, "Максимальное количество папок", String.valueOf(messagesController.dialogFiltersLimitPremium)));
        items.add(UItem.asButton(ID_LIMIT_PINNED_FOLDER, "Закрепленные чаты в папке", String.valueOf(messagesController.dialogFiltersPinnedLimitPremium)));
        items.add(UItem.asButton(ID_LIMIT_PINNED_SAVED, "Закрепленные чаты в Избранном", String.valueOf(messagesController.savedDialogsPinnedLimitPremium)));
        items.add(UItem.asButton(ID_LIMIT_CHATS_IN_FOLDER, "Максимально чатов в папке", String.valueOf(messagesController.dialogFiltersChatsLimitPremium)));
        items.add(UItem.asButton(ID_LIMIT_CHANNELS, "Лимит каналов и супергрупп", String.valueOf(messagesController.channelsLimitPremium)));
        items.add(UItem.asShadow("Увеличенные лимиты для структуры ваших переписок и папок."));

        items.add(UItem.asHeader("Лимиты медиа и стикеров"));
        items.add(UItem.asButton(ID_LIMIT_GIFS, "Лимит сохраненных GIF", String.valueOf(messagesController.savedGifsLimitPremium)));
        items.add(UItem.asButton(ID_LIMIT_STICKERS, "Лимит избранных стикеров", String.valueOf(messagesController.stickersFavedLimitPremium)));
        items.add(UItem.asShadow("Лимиты на количество гифок в панели отправки и избранных стикеров."));

        items.add(UItem.asHeader("Лимиты профиля и текста"));
        items.add(UItem.asButton(ID_LIMIT_PUBLIC_LINKS, "Лимит публичных ссылок", String.valueOf(messagesController.publicLinksLimitPremium)));
        items.add(UItem.asButton(ID_LIMIT_CAPTION, "Лимит символов в описании медиа", String.valueOf(messagesController.captionLengthLimitPremium)));
        items.add(UItem.asButton(ID_LIMIT_ABOUT, "Лимит символов в разделе «О себе»", String.valueOf(messagesController.aboutLengthLimitPremium)));
        items.add(UItem.asShadow("Символьные ограничения для описания медиафайлов и био вашего аккаунта."));
        items.add(UItem.asHeader("Разрешения и поддержка"));
        items.add(UItem.asButton(ID_GRANT_PERMISSIONS, "Выдать системные разрешения", "Контакты, Звонки, Память"));
        items.add(UItem.asShadow("Нажмите, чтобы вручную выдать приложению базовые разрешения (если отключили их запрос при старте)."));
        
        items.add(UItem.asButton(ID_SUPPORT_PROJECT, "Поддержать проект (USDT TON)", "Отправить донат через @wallet"));
        items.add(UItem.asShadow("Спасибо за вашу поддержку! Это помогает развивать PrimeGram."));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        MessagesController messagesController = MessagesController.getInstance(currentAccount);
        if (item.id == ID_SIDEBAR_ENABLED) {
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            boolean enabled = preferences.getBoolean("primegram_sidebar_enabled", false);
            preferences.edit().putBoolean("primegram_sidebar_enabled", !enabled).apply();
            
            if (LaunchActivity.instance != null) {
                LaunchActivity.instance.updateSidebarVisibility();
            }
            listView.adapter.update(true);
        } else if (item.id == ID_EMERGENCY_PROXY) {
            if (VpnSDK.isProxyRunning()) {
                VpnSDK.stopProxy();
                listView.adapter.update(true);
            } else {
                VpnSDK.registerOrAuth(2, success -> {
                    if (success) {
                        ApplicationLoader.applyXrayProxyToConnectionsManager();
                        if (listView != null && listView.adapter != null) {
                            listView.adapter.update(true);
                        }
                    }
                });
            }
            listView.adapter.update(true);
        } else if (item.id == ID_AUTO_UPDATES) {
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            boolean enabled = preferences.getBoolean("primegram_auto_updates", false);
            preferences.edit().putBoolean("primegram_auto_updates", !enabled).apply();
            listView.adapter.update(true);
        } else if (item.id == ID_HW_ACCEL) {
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            boolean enabled = preferences.getBoolean("primegram_hw_accel", false);
            preferences.edit().putBoolean("primegram_hw_accel", !enabled).apply();
            listView.adapter.update(true);
        } else if (item.id == ID_FEED_EXCLUDE_MUTED) {
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            boolean enabled = preferences.getBoolean("primegram_feed_exclude_muted", false);
            preferences.edit().putBoolean("primegram_feed_exclude_muted", !enabled).apply();
            listView.adapter.update(true);
        } else if (item.id == ID_FEED_EXCLUDE_ARCHIVED) {
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            boolean enabled = preferences.getBoolean("primegram_feed_exclude_archived", false);
            preferences.edit().putBoolean("primegram_feed_exclude_archived", !enabled).apply();
            listView.adapter.update(true);
        } else if (item.id == ID_CHECK_UPDATES) {
            org.telegram.messenger.PrimeUpdater.checkUpdate(getContext(), true);
        } else if (item.id == ID_LIMIT_FOLDERS) {
            showNumberInputDialog(item.id, "Количество папок", "Укажите максимальное число папок с чатами:", messagesController.dialogFiltersLimitPremium, 20, 1, 100);
        } else if (item.id == ID_LIMIT_PINNED_FOLDER) {
            showNumberInputDialog(item.id, "Закрепы в папке", "Укажите максимальное количество закрепленных чатов внутри одной папки:", messagesController.dialogFiltersPinnedLimitPremium, 10, 1, 100);
        } else if (item.id == ID_LIMIT_PINNED_SAVED) {
            showNumberInputDialog(item.id, "Закрепы в Избранном", "Укажите максимальное количество закрепов внутри Избранного:", messagesController.savedDialogsPinnedLimitPremium, 6, 1, 100);
        } else if (item.id == ID_LIMIT_CHATS_IN_FOLDER) {
            showNumberInputDialog(item.id, "Чатов в папке", "Укажите максимальное число чатов в одной папке:", messagesController.dialogFiltersChatsLimitPremium, 200, 1, 1000);
        } else if (item.id == ID_LIMIT_CHANNELS) {
            showNumberInputDialog(item.id, "Лимит каналов", "Укажите максимальное число каналов и супергрупп, в которых вы можете состоять:", messagesController.channelsLimitPremium, 1000, 10, 10000);
        } else if (item.id == ID_LIMIT_GIFS) {
            showNumberInputDialog(item.id, "Сохраненные GIF", "Укажите максимальный лимит сохраненных гифок:", messagesController.savedGifsLimitPremium, 400, 10, 5000);
        } else if (item.id == ID_LIMIT_STICKERS) {
            showNumberInputDialog(item.id, "Избранные стикеры", "Укажите максимальный лимит избранных стикеров:", messagesController.stickersFavedLimitPremium, 200, 5, 2000);
        } else if (item.id == ID_LIMIT_PUBLIC_LINKS) {
            showNumberInputDialog(item.id, "Публичные ссылки", "Укажите максимальный лимит публичных ссылок / юзернеймов:", messagesController.publicLinksLimitPremium, 20, 1, 200);
        } else if (item.id == ID_LIMIT_CAPTION) {
            showNumberInputDialog(item.id, "Символов в описании", "Укажите лимит символов в описании к медиа:", messagesController.captionLengthLimitPremium, 4096, 100, 50000);
        } else if (item.id == ID_LIMIT_ABOUT) {
            showNumberInputDialog(item.id, "Символов в «О себе»", "Укажите лимит символов в описании профиля «О себе»:", messagesController.aboutLengthLimitPremium, 140, 20, 2000);
        } else if (item.id == ID_SUPPORT_PROJECT) {
            try {
                org.telegram.messenger.browser.Browser.openUrl(getContext(), "http://t.me/send?start=IVqCWWqPk6AA");
            } catch (Exception e) {
                org.telegram.messenger.FileLog.e(e);
            }
        } else if (item.id == ID_GRANT_PERMISSIONS) {
            if (getParentActivity() != null && android.os.Build.VERSION.SDK_INT >= 23) {
                ArrayList<String> perms = new ArrayList<>();
                perms.add(android.Manifest.permission.READ_CONTACTS);
                perms.add(android.Manifest.permission.WRITE_CONTACTS);
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    perms.add(android.Manifest.permission.READ_MEDIA_IMAGES);
                    perms.add(android.Manifest.permission.READ_MEDIA_VIDEO);
                } else {
                    perms.add(android.Manifest.permission.READ_EXTERNAL_STORAGE);
                    perms.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
                }
                perms.add(android.Manifest.permission.READ_PHONE_STATE);
                perms.add(android.Manifest.permission.CALL_PHONE);
                getParentActivity().requestPermissions(perms.toArray(new String[0]), 100);
            }
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    private void showNumberInputDialog(final int id, String title, String message, final int currentValue, final int defaultValue, final int min, final int max) {
        AlertDialog.Builder b = new AlertDialog.Builder(getContext(), getResourceProvider());
        b.setTitle(title);

        LinearLayout container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.VERTICAL);

        if (message != null) {
            TextView textView = new TextView(getContext());
            textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, getResourceProvider()));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            textView.setText(message);
            container.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 8, 24, 12));
        }

        final EditTextBoldCursor editText = new EditTextBoldCursor(getContext()) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(36), MeasureSpec.EXACTLY));
            }
        };
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setText(String.valueOf(currentValue));
        editText.setSelection(editText.getText().length());
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, getResourceProvider()));
        editText.setHintColor(Theme.getColor(Theme.key_groupcreate_hintText, getResourceProvider()));
        editText.setSingleLine(true);
        editText.setFocusable(true);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setLineColors(Theme.getColor(Theme.key_windowBackgroundWhiteInputField, getResourceProvider()), Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, getResourceProvider()), Theme.getColor(Theme.key_text_RedRegular, getResourceProvider()));
        editText.setBackgroundDrawable(null);
        editText.setPadding(0, 0, AndroidUtilities.dp(4), 0);

        container.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 16));
        b.setView(container);
        b.setWidth(AndroidUtilities.dp(292));

        b.setPositiveButton("Сохранить", (di, w) -> {
            try {
                int val = Integer.parseInt(editText.getText().toString());
                if (val < min || val > max) {
                    AndroidUtilities.shakeView(editText);
                    return;
                }
                saveLimit(id, val);
            } catch (Exception e) {
                AndroidUtilities.shakeView(editText);
            }
        });

        b.setNegativeButton("Отмена", null);
        b.setNeutralButton("Сбросить", (di, w) -> {
            saveLimit(id, defaultValue);
        });

        AlertDialog dialog = b.create();
        showDialog(dialog);
        editText.requestFocus();
        AndroidUtilities.showKeyboard(editText);
    }

    private void saveLimit(int id, int value) {
        MessagesController messagesController = MessagesController.getInstance(currentAccount);
        SharedPreferences.Editor editor = messagesController.getMainSettings().edit();

        if (id == ID_LIMIT_FOLDERS) {
            messagesController.dialogFiltersLimitPremium = value;
            editor.putInt("dialogFiltersLimitPremium", value);
        } else if (id == ID_LIMIT_PINNED_FOLDER) {
            messagesController.dialogFiltersPinnedLimitPremium = value;
            editor.putInt("dialogFiltersPinnedLimitPremium", value);
        } else if (id == ID_LIMIT_PINNED_SAVED) {
            messagesController.savedDialogsPinnedLimitPremium = value;
            editor.putInt("savedDialogsPinnedLimitPremium", value);
        } else if (id == ID_LIMIT_CHANNELS) {
            messagesController.channelsLimitPremium = value;
            editor.putInt("channelsLimitPremium", value);
        } else if (id == ID_LIMIT_GIFS) {
            messagesController.savedGifsLimitPremium = value;
            editor.putInt("savedGifsLimitPremium", value);
        } else if (id == ID_LIMIT_STICKERS) {
            messagesController.stickersFavedLimitPremium = value;
            editor.putInt("stickersFavedLimitPremium", value);
        } else if (id == ID_LIMIT_CHATS_IN_FOLDER) {
            messagesController.dialogFiltersChatsLimitPremium = value;
            editor.putInt("dialogFiltersChatsLimitPremium", value);
        } else if (id == ID_LIMIT_PUBLIC_LINKS) {
            messagesController.publicLinksLimitPremium = value;
            editor.putInt("publicLinksLimitPremium", value);
        } else if (id == ID_LIMIT_CAPTION) {
            messagesController.captionLengthLimitPremium = value;
            editor.putInt("captionLengthLimitPremium", value);
        } else if (id == ID_LIMIT_ABOUT) {
            messagesController.aboutLengthLimitPremium = value;
            editor.putInt("aboutLengthLimitPremium", value);
        }

        editor.apply();
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }
}
