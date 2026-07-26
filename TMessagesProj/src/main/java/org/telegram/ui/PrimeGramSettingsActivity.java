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
import org.telegram.messenger.SharedConfig;
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
    private static final int ID_TGWS_PROXY = 20;
    private static final int ID_HW_ACCEL = 19;
    private static final int ID_BATTERY_OPTIMIZATION = 21;
    private static final int ID_MUSIC_SETTINGS = 22;
    private static final int ID_HIDE_PHONE = 23;
    private static final int ID_FAKE_PHONE = 24;
    private static final int ID_GREY_ZONE = 25;
    private static final int ID_MESSAGE_TAGS = 26;
    private static final int ID_SEARCH_PLUS = 27;
    private static final int ID_TEMP_SUBS = 28;
    private static final int ID_TEXT_TOOLBAR = 29;
    private static final int ID_BOT_LOGIN = 30;
    private static final int ID_LINK_PREVIEW = 31;
    private static final int ID_SESSION_NAME = 32;
    @Override
    protected CharSequence getTitle() {
        return "Настройки PrimeGram";
    }

    private String vlessKeyStatusText(boolean running) {
        if (running) {
            return "Статус: активен, трафик защищён.";
        }
        if (!VpnSDK.hasCachedXrayConfig()) {
            return "Статус: ключ ещё не получен от сервера.";
        }
        String lastError = VpnSDK.getProxyLastError();
        if (lastError != null && !lastError.isEmpty()) {
            return "Статус: ключ есть, но прокси не запустился (" + lastError + ").";
        }
        return "Статус: ключ получен, прокси выключен.";
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
        UItem proxyItem = UItem.asCheck(ID_EMERGENCY_PROXY, "Включить VLESS-сервер");
        boolean vlessRunning = VpnSDK.isProxyRunning();
        proxyItem.checked = vlessRunning;
        items.add(proxyItem);
        items.add(UItem.asShadow(vlessKeyStatusText(vlessRunning) + " В случае проблем с основным прокси, вы можете включить аварийный VLESS-прокси для обхода блокировок. Сервер работает локально на 127.0.0.2:17808"));

        UItem tgwsItem = UItem.asCheck(ID_TGWS_PROXY, "Включить TgWs-сервер");
        tgwsItem.checked = preferences.getBoolean("primegram_tgws_enabled", true);
        items.add(tgwsItem);
        items.add(UItem.asShadow("Включает локальный сервер TgWsProxy. Сервер работает локально на 127.0.0.1:1080"));

        boolean batteryOptOk = AndroidUtilities.isIgnoringBatteryOptimizations();
        items.add(UItem.asButton(ID_BATTERY_OPTIMIZATION, "Отключить оптимизацию батареи",
                batteryOptOk ? "Разрешено" : "Не разрешено — нажмите, чтобы включить"));
        items.add(UItem.asShadow("На некоторых прошивках (MIUI, OneUI и т.п.) система агрессивно закрывает фоновые процессы, из-за чего прокси отключается и уведомления приходят с задержкой. Разрешение \"Без ограничений\" для батареи устраняет эту проблему."));

        items.add(UItem.asHeader("Приватность"));
        UItem hidePhoneItem = UItem.asCheck(ID_HIDE_PHONE, "Скрывать свой номер в профиле");
        hidePhoneItem.checked = org.telegram.messenger.PrimeGramPrivacy.isHidePhoneEnabled();
        items.add(hidePhoneItem);
        String fake = org.telegram.messenger.PrimeGramPrivacy.getFakePhone();
        items.add(UItem.asButton(ID_FAKE_PHONE, "Свой номер для показа",
                fake.isEmpty() ? "Нажмите, чтобы ввести" : fake));
        items.add(UItem.asShadow("Меняет только то, что показано на вашем экране — удобно для скриншотов. Номер на сервере и у собеседников не меняется."));

        items.add(UItem.asButton(ID_GREY_ZONE, "Серая зона",
                org.telegram.messenger.GreyZone.isAccepted() ? "Включена" : "Требует подтверждения"));
        items.add(UItem.asShadow("Функции, снимающие ограничения собеседника, и режим призрака. Разработчик их не одобряет — используются на ваш страх и риск."));

        items.add(UItem.asHeader("Теги сообщений"));
        items.add(UItem.asButton(ID_MESSAGE_TAGS, "Помеченные сообщения",
                String.valueOf(org.telegram.messenger.MessageTagsStore.count())));
        items.add(UItem.asShadow("Задержите сообщение в чате и выберите «Пометить тегом». Тег виден прямо на сообщении в чате и хранится только на этом устройстве — собеседник его не видит."));

        items.add(UItem.asHeader("Инструменты"));
        items.add(UItem.asCheck(ID_TEXT_TOOLBAR, "Панель форматирования")
                .setChecked(org.telegram.messenger.PrimeToolbarSettings.isEnabled()));
        items.add(UItem.asShadow("Ряд кнопок над полем ввода: жирный, курсив, моноширинный, зачёркнутый, подчёркнутый, спойлер, ссылка, цитата, сброс форматирования и копирование. Работает по выделенному тексту. Панель занимает место над полем ввода — поэтому выключена по умолчанию."));
        items.add(UItem.asCheck(ID_LINK_PREVIEW, "Предпросмотр ссылок")
                .setChecked(org.telegram.messenger.PrimeLinkPreviewSettings.isEnabled()));
        items.add(UItem.asShadow("Задержите ссылку в чате — страница откроется в маленьком окне. Тап по окну открывает её во встроенном браузере. Учтите: страница загружается по-настоящему, то есть тратит трафик и сайт узнаёт о посещении."));
        items.add(UItem.asButton(ID_SEARCH_PLUS, "Поиск+", "ID, телефон, ссылка"));
        items.add(UItem.asShadow("Находит профиль по числовому ID, номеру телефона, @username или ссылке t.me — там, где обычный поиск отказывается искать."));
        items.add(UItem.asButton(ID_TEMP_SUBS, "Временные подписки",
                String.valueOf(org.telegram.messenger.TempSubStore.getAll().size())));
        items.add(UItem.asShadow("Подпишитесь на канал на срок от часа до месяца — клиент отпишется сам. Включается в меню самого канала."));
        items.add(UItem.asButton(ID_SESSION_NAME, "Имя клиента в сессиях",
                org.telegram.messenger.PrimeClientIdentity.getSessionName()));
        items.add(UItem.asShadow("Заголовок строки в списке активных сессий. Подпись «Telegram Web» под ним приходит от сервера по api_id и не меняется. Сервер ждёт здесь имя браузера — если его не узнать, пишет «Unknown Browser», поэтому в значении стоит оставить Chrome, Safari, Firefox, Edge или Opera. Применяется после перезапуска."));
        items.add(UItem.asButton(ID_BOT_LOGIN, "Вход в бота", "по токену BotFather"));
        items.add(UItem.asShadow("Вход в аккаунт бота по токену. Бот занимает отдельный слот аккаунта — сессия бота отдельна от вашей, это устройство протокола Telegram."));

        items.add(UItem.asHeader("Музыка"));
        items.add(UItem.asButton(ID_MUSIC_SETTINGS, "Настройки вкладки «Музыка»",
                org.telegram.messenger.music.MusicSettingsStore.isTabEnabled() ? "Включена" : "Выключена"));
        items.add(UItem.asShadow("Отправка текущего трека (Spotify, Яндекс Музыка, SoundCloud, VK, Last.fm, Telegram) карточкой, аудиофайлом или текстом. Вкладка появляется в панели эмодзи."));

        items.add(UItem.asHeader("Экспериментальные настройки"));
        boolean hwAccel = org.telegram.messenger.CrashSafeToggle.isEnabled("primegram_hw_accel");
        UItem hwAccelItem = UItem.asCheck(ID_HW_ACCEL, "Аппаратное ускорение видео (MediaCodec)");
        hwAccelItem.checked = hwAccel;
        items.add(hwAccelItem);
        if (org.telegram.messenger.CrashSafeToggle.wasAutoDisabled("primegram_hw_accel")) {
            items.add(UItem.asShadow("Отключено автоматически: при последнем запуске с этой опцией приложение аварийно завершилось. Попробуйте включить снова — если проблема повторится на этом устройстве, лучше оставить выключенным."));
            org.telegram.messenger.CrashSafeToggle.acknowledgeAutoDisabled("primegram_hw_accel");
        } else {
            items.add(UItem.asShadow("Включает аппаратное декодирование видео/GIF/кружочков вместо программного. Может немного сэкономить батарею, но на некоторых устройствах декодер бывает нестабилен — приложение автоматически откатит настройку, если из-за неё случится сбой. Изменения применяются после перезапуска приложения."));
        }

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
                android.widget.Toast.makeText(getContext(), "Получаем ключ...", android.widget.Toast.LENGTH_SHORT).show();
                VpnSDK.registerOrAuth(2, success -> {
                    if (listView != null && listView.adapter != null) {
                        listView.adapter.update(true);
                    }
                    if (!success) {
                        android.widget.Toast.makeText(getContext(), "Не удалось получить ключ. Проверьте соединение и попробуйте ещё раз.", android.widget.Toast.LENGTH_LONG).show();
                    }
                });
            }
        } else if (item.id == ID_TGWS_PROXY) {
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            boolean enabled = preferences.getBoolean("primegram_tgws_enabled", true);
            preferences.edit().putBoolean("primegram_tgws_enabled", !enabled).apply();

            if (org.telegram.messenger.TgWsProxyService.isRunning()) {
                org.telegram.messenger.TgWsProxyService.stopService(getParentActivity());
            } else {
                org.telegram.messenger.TgWsProxyService.startService(getParentActivity());
            }
            listView.adapter.update(true);
        } else if (item.id == ID_AUTO_UPDATES) {
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            boolean enabled = preferences.getBoolean("primegram_auto_updates", false);
            preferences.edit().putBoolean("primegram_auto_updates", !enabled).apply();
            listView.adapter.update(true);
        } else if (item.id == ID_HW_ACCEL) {
            boolean newValue = !org.telegram.messenger.CrashSafeToggle.isEnabled("primegram_hw_accel");
            org.telegram.messenger.CrashSafeToggle.setEnabled("primegram_hw_accel", newValue);
            listView.adapter.update(true);
            if (getParentActivity() != null) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle("Требуется перезапуск");
                builder.setMessage("Изменение вступит в силу после перезапуска PrimeGram.");
                builder.setPositiveButton("Перезапустить сейчас", (dialog, which) -> restartApp());
                builder.setNegativeButton("Позже", null);
                showDialog(builder.create());
            }
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
        } else if (item.id == ID_BATTERY_OPTIMIZATION) {
            AndroidUtilities.requestIgnoreBatteryOptimizations(getParentActivity());
        } else if (item.id == ID_MUSIC_SETTINGS) {
            presentFragment(new MusicSettingsActivity());
        } else if (item.id == ID_HIDE_PHONE) {
            org.telegram.messenger.PrimeGramPrivacy.setHidePhoneEnabled(!org.telegram.messenger.PrimeGramPrivacy.isHidePhoneEnabled());
            listView.adapter.update(true);
        } else if (item.id == ID_FAKE_PHONE) {
            showPhoneInputDialog();
        } else if (item.id == ID_GREY_ZONE) {
            presentFragment(new GreyZoneActivity());
        } else if (item.id == ID_MESSAGE_TAGS) {
            presentFragment(new MessageTagsActivity());
        } else if (item.id == ID_TEXT_TOOLBAR) {
            org.telegram.messenger.PrimeToolbarSettings.setEnabled(!org.telegram.messenger.PrimeToolbarSettings.isEnabled());
            listView.adapter.update(true);
        } else if (item.id == ID_LINK_PREVIEW) {
            org.telegram.messenger.PrimeLinkPreviewSettings.setEnabled(!org.telegram.messenger.PrimeLinkPreviewSettings.isEnabled());
            listView.adapter.update(true);
        } else if (item.id == ID_SEARCH_PLUS) {
            presentFragment(new SearchPlusActivity());
        } else if (item.id == ID_TEMP_SUBS) {
            presentFragment(new TempSubActivity());
        } else if (item.id == ID_SESSION_NAME) {
            showSessionNameDialog();
        } else if (item.id == ID_BOT_LOGIN) {
            presentFragment(new BotLoginActivity());
        }
    }

    private void showSessionNameDialog() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Имя клиента в сессиях");
        builder.setMessage("Заголовок строки в списке активных сессий. Сервер ждёт здесь имя браузера, поэтому оставьте в значении Chrome, Safari, Firefox, Edge или Opera — иначе получится «Unknown Browser». Пустое поле вернёт настоящую модель устройства.");

        final EditTextBoldCursor editText = new EditTextBoldCursor(getParentActivity());
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        editText.setHint(org.telegram.messenger.PrimeClientIdentity.getDefaultName());
        editText.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setCursorSize(AndroidUtilities.dp(20));
        editText.setCursorWidth(1.5f);
        editText.setSingleLine(true);
        editText.setBackgroundDrawable(Theme.createEditTextDrawable(getParentActivity(), true));
        editText.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));
        editText.setText(org.telegram.messenger.PrimeClientIdentity.getSessionName());
        editText.setSelection(editText.getText().length());

        LinearLayout container = new LinearLayout(getParentActivity());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);
        container.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        builder.setView(container);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> {
            org.telegram.messenger.PrimeClientIdentity.setSessionName(editText.getText().toString());
            listView.adapter.update(true);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showPhoneInputDialog() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Свой номер для показа");
        builder.setMessage("Введите текст, который будет показан вместо вашего номера. Оставьте пустым — тогда цифры просто скроются.");

        final EditTextBoldCursor editText = new EditTextBoldCursor(getParentActivity());
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        editText.setHint("+7 900 000-00-00");
        editText.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setCursorSize(AndroidUtilities.dp(20));
        editText.setCursorWidth(1.5f);
        editText.setSingleLine(true);
        // A visible underline: without a background the field reads as empty space.
        editText.setBackgroundDrawable(Theme.createEditTextDrawable(getParentActivity(), true));
        editText.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));
        editText.setText(org.telegram.messenger.PrimeGramPrivacy.getFakePhone());
        editText.setSelection(editText.getText().length());

        LinearLayout container = new LinearLayout(getParentActivity());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);
        container.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        builder.setView(container);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> {
            org.telegram.messenger.PrimeGramPrivacy.setFakePhone(editText.getText().toString());
            listView.adapter.update(true);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void restartApp() {
        Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        android.content.Intent intent = activity.getPackageManager().getLaunchIntentForPackage(activity.getPackageName());
        if (intent == null) {
            return;
        }
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        Runtime.getRuntime().exit(0);
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
