package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
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
import org.telegram.ui.Components.IconBackgroundColors;
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
    private static final int ID_STARTUP_TRACE = 33;
    private static final int ID_FEED_HIDDEN = 34;
    private static final int ID_ADBLOCK = 35;
    private static final int ID_ADBLOCK_DNS = 36;
    private static final int ID_DNS_ENABLED = 37;
    private static final int ID_DNS_PRESET = 38;
    private static final int ID_LIMIT_RECENT_STICKERS = 39;
    private static final int ID_ONLINE_DOTS = 40;
    private static final int ID_STT_ENABLED = 41;
    private static final int ID_STT_TOKEN = 42;
    private static final int ID_STT_ENDPOINT = 43;
    private static final int ID_STT_MODEL = 44;
    private static final int ID_RELATIVE_LAST_SEEN = 45;
    private static final int ID_NO_NUMBER_ROUNDING = 46;
    private static final int ID_TIME_WITH_SECONDS = 47;
    private static final int ID_HIDE_STORIES = 48;
    private static final int ID_HIDE_FAB = 49;
    private static final int ID_ARCHIVE_ON_PULL = 50;
    private static final int ID_DISABLE_UNARCHIVE_SWIPE = 51;
    private static final int ID_HIDE_SHARE_BUTTON = 52;
    private static final int ID_EDITED_AS_ICON = 53;
    private static final int ID_COMMA_AFTER_MENTION = 54;
    private static final int ID_HIDE_KEYBOARD_ON_SCROLL = 55;
    private static final int ID_STICKER_SIZE = 56;
    private static final int ID_HIDE_REACTIONS_CHANNELS = 57;
    private static final int ID_HIDE_REACTIONS_GROUPS = 58;
    private static final int ID_HIDE_REACTIONS_PRIVATE = 59;
    private static final int ID_HIDE_SEND_AS_PEER = 60;
    private static final int ID_SQUARE_FAB = 61;
    private static final int ID_HW_BENCHMARK = 62;
    private static final int ID_HIDE_STICKER_TIME = 63;
    private static final int ID_SHOW_ID_AND_DC = 64;
    private static final int ID_FORCE_SNOW = 65;
    private static final int ID_CENTER_TITLE = 66;
    private static final int ID_REMOVE_TAIL = 67;
    private static final int ID_DOUBLE_TAP = 68;
    private static final int ID_HIDE_ARCHIVE_FOLDER = 69;
    private static final int ID_HIDE_ALL_CHATS = 70;
    private static final int ID_AVATAR_CORNERS = 71;
    private static final int ID_ADBLOCK_UPDATE = 72;
    private static final int ID_LOCKSCREEN_CALLS = 73;
    private static final int ID_MENU_SAVE = 74;
    private static final int ID_MENU_DETAILS = 75;
    private static final int ID_SENDER_MINI_AVATARS = 76;
    private static final int ID_ADMIN_SHORTCUTS = 77;
    private static final int ID_TRANSLATE_PROVIDER = 78;
    private static final int ID_SAVE_ROUND_VOICE = 79;
    private static final int ID_SEND_UNCOMPRESSED = 80;
    private static final int ID_CACHE = 81;
    private static final int ID_VIDEO_QUALITY = 82;
    private static final int ID_PRELOAD_VIDEO_MOBILE = 83;
    private static final int ID_AUTODOWNLOAD = 84;
    private static final int ID_WHISPER_ENABLED = 85;
    private static final int ID_WHISPER_MODEL = 86;
    private static final int ID_WHISPER_DOWNLOAD = 87;
    private static final int ID_WHISPER_LANGUAGE = 88;
    private static final int ID_ROUND_VIDEO_REAR = 89;
    private static final int ID_CAMERA2 = 90;
    private static final int ID_LIVE_PREVIEW = 91;
    private static final int ID_DOUBLE_TAP_CARDS = 92;
    private static final int ID_STICKER_SIZE_CARDS = 93;
    private static final int ID_TRANSLATOR_CARDS = 94;
    private static final int ID_VIDEO_QUALITY_CARDS = 95;
    /** Ids for the explanation cells, kept clear of everything else. */
    private static final int ID_LOGS_ENABLED = 96;
    private static final int ID_HIDE_SETTINGS_HEADER = 97;
    private static final int ID_MAIN_TITLE_USERNAME = 98;
    private static final int ID_HIDE_EMOJI_STATUS = 99;
    private static final int ID_OPTIMIZATIONS = 100;
    private static final int ID_DOUBLE_TAP_REACTION = 101;
    private static final int ID_STICKER_SIZE_SLIDER = 102;
    private static final int ID_INFO_BASE = 600;
    /** One id per blocking list, taken from a range nothing else uses. */
    private static final int ID_ADBLOCK_LIST_BASE = 200;

    /**
     * Plain on/off tweaks all behave identically, so they share one handler. Returns the
     * preference key for such an item, or null if the item needs its own handling.
     */
    private static String primeTweakKeyFor(int id) {
        if (id == ID_HIDE_STORIES) return org.telegram.messenger.PrimeTweaks.HIDE_STORIES;
        if (id == ID_HIDE_FAB) return org.telegram.messenger.PrimeTweaks.HIDE_FLOATING_BUTTON;
        if (id == ID_DISABLE_UNARCHIVE_SWIPE) return org.telegram.messenger.PrimeTweaks.DISABLE_UNARCHIVE_SWIPE;
        if (id == ID_HIDE_SHARE_BUTTON) return org.telegram.messenger.PrimeTweaks.HIDE_SHARE_BUTTON;
        if (id == ID_EDITED_AS_ICON) return org.telegram.messenger.PrimeTweaks.EDITED_AS_ICON;
        if (id == ID_COMMA_AFTER_MENTION) return org.telegram.messenger.PrimeTweaks.COMMA_AFTER_MENTION;
        if (id == ID_HIDE_KEYBOARD_ON_SCROLL) return org.telegram.messenger.PrimeTweaks.HIDE_KEYBOARD_ON_SCROLL;
        if (id == ID_HIDE_REACTIONS_CHANNELS) return org.telegram.messenger.PrimeTweaks.HIDE_REACTIONS_CHANNELS;
        if (id == ID_HIDE_REACTIONS_GROUPS) return org.telegram.messenger.PrimeTweaks.HIDE_REACTIONS_GROUPS;
        if (id == ID_HIDE_REACTIONS_PRIVATE) return org.telegram.messenger.PrimeTweaks.HIDE_REACTIONS_PRIVATE;
        if (id == ID_HIDE_SEND_AS_PEER) return org.telegram.messenger.PrimeTweaks.HIDE_SEND_AS_PEER;
        if (id == ID_SQUARE_FAB) return org.telegram.messenger.PrimeTweaks.SQUARE_FAB;
        if (id == ID_HIDE_STICKER_TIME) return org.telegram.messenger.PrimeTweaks.HIDE_STICKER_TIME;
        if (id == ID_SHOW_ID_AND_DC) return org.telegram.messenger.PrimeTweaks.SHOW_ID_AND_DC;
        if (id == ID_FORCE_SNOW) return org.telegram.messenger.PrimeTweaks.FORCE_SNOW;
        if (id == ID_CENTER_TITLE) return org.telegram.messenger.PrimeTweaks.CENTER_TITLE;
        if (id == ID_REMOVE_TAIL) return org.telegram.messenger.PrimeTweaks.REMOVE_MESSAGE_TAIL;
        if (id == ID_HIDE_ARCHIVE_FOLDER) return org.telegram.messenger.PrimeTweaks.HIDE_ARCHIVE_FOLDER;
        if (id == ID_HIDE_ALL_CHATS) return org.telegram.messenger.PrimeTweaks.HIDE_ALL_CHATS;
        if (id == ID_MENU_SAVE) return org.telegram.messenger.PrimeTweaks.MENU_SAVE_TO_SAVED;
        if (id == ID_MENU_DETAILS) return org.telegram.messenger.PrimeTweaks.MENU_DETAILS;
        if (id == ID_SENDER_MINI_AVATARS) return org.telegram.messenger.PrimeTweaks.SENDER_MINI_AVATARS;
        if (id == ID_ROUND_VIDEO_REAR) return org.telegram.messenger.PrimeTweaks.ROUND_VIDEO_REAR;
        if (id == ID_HIDE_SETTINGS_HEADER) return org.telegram.messenger.PrimeTweaks.HIDE_SETTINGS_HEADER;
        if (id == ID_MAIN_TITLE_USERNAME) return org.telegram.messenger.PrimeTweaks.MAIN_TITLE_USERNAME;
        if (id == ID_HIDE_EMOJI_STATUS) return org.telegram.messenger.PrimeTweaks.HIDE_EMOJI_STATUS;
        if (id == ID_OPTIMIZATIONS) return org.telegram.messenger.PrimeTweaks.OPTIMIZATIONS;
        if (id == ID_ADMIN_SHORTCUTS) return org.telegram.messenger.PrimeTweaks.ADMIN_SHORTCUTS;
        if (id == ID_SAVE_ROUND_VOICE) return org.telegram.messenger.PrimeTweaks.SAVE_ROUND_AND_VOICE;
        if (id == ID_SEND_UNCOMPRESSED) return org.telegram.messenger.PrimeTweaks.SEND_UNCOMPRESSED;
        return null;
    }

    /**
     * Which page this instance shows. The screen had grown to a single scroll of roughly
     * forty items, where finding anything meant reading everything; it is now a short hub of
     * categories, each opening this same fragment with a different section.
     */
    private static final int SECTION_ROOT = 0;
    private static final int SECTION_INTERFACE = 1;
    private static final int SECTION_CONNECTION = 2;
    private static final int SECTION_PRIVACY = 3;
    private static final int SECTION_TOOLS = 4;
    private static final int SECTION_MEDIA = 5;
    private static final int SECTION_ADVANCED = 6;
    private static final int SECTION_PREMIUM = 7;
    private static final int SECTION_ABOUT = 8;

    /** Category rows on the hub. Offset well past the setting ids so they cannot collide. */
    private static final int ID_SECTION_BASE = 900;

    private final int section;

    public PrimeGramSettingsActivity() {
        this(SECTION_ROOT);
    }

    private PrimeGramSettingsActivity(int section) {
        this.section = section;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Values here are edited on other screens (grey zone, music, tags), so the list has to
        // be rebuilt on return — otherwise it keeps showing what was true when it was opened.
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    /**
     * PrimeGram: the hub.
     *
     * <p>Built from {@link SettingsActivity.SettingCell}, the same rows Telegram's own settings
     * screen uses - a coloured icon, a title and a description underneath. The previous version
     * used {@code UItem.asButton(id, title, value)}, which puts the second string on the *right*
     * as blue text: at this length it truncated to "Боковая панель, л…", so the one place a
     * description was supposed to help was the one place it could not be read.
     */
    private void fillRoot(ArrayList<UItem> items) {
        items.add(primeHeaderCell());
        items.add(UItem.asShadow(null));
        items.add(section(SECTION_INTERFACE, IconBackgroundColors.BLUE, R.drawable.settings_folders,
                "Интерфейс", "Боковая панель, лента, вкладки"));
        items.add(section(SECTION_CONNECTION, IconBackgroundColors.GREEN, R.drawable.settings_data,
                "Соединение", "Прокси, VLESS, работа в фоне"));
        items.add(section(SECTION_PRIVACY, IconBackgroundColors.RED, R.drawable.settings_privacy,
                "Приватность", "Номер, серая зона"));
        items.add(section(SECTION_TOOLS, IconBackgroundColors.ORANGE, R.drawable.settings_features,
                "Инструменты", "Теги, ссылки, боты"));
        items.add(section(SECTION_MEDIA, IconBackgroundColors.CYAN, R.drawable.settings_sounds,
                "Медиа и музыка", "Качество, кэш, расшифровка, камера"));
        items.add(section(SECTION_PREMIUM, IconBackgroundColors.PURPLE, R.drawable.settings_premium,
                "Локальный Premium", "Лимиты на этом устройстве"));
        items.add(section(SECTION_ADVANCED, IconBackgroundColors.BLUE_DEEP, R.drawable.settings_power,
                "Дополнительно", "Обновления, эксперименты, диагностика"));
        items.add(section(SECTION_ABOUT, IconBackgroundColors.GRAY, R.drawable.settings_ask,
                "Разрешения и поддержка", "Доступы приложения и связь с автором"));
        items.add(UItem.asShadow(null));
    }

    /**
     * Short explanation in the list, full one behind "Подробнее".
     *
     * <p>Cached by id because {@link UItem#asCustom} hands the list a view, and a new view on every
     * rebuild would drop the scroll position and rebuild the span every time a switch is flipped.
     */
    private final java.util.HashMap<Integer, org.telegram.ui.Cells.PrimeInfoCell> infoCells = new java.util.HashMap<>();

    private UItem info(int id, String title, CharSequence summary, CharSequence details) {
        org.telegram.ui.Cells.PrimeInfoCell cell = infoCells.get(id);
        if (cell == null && getContext() != null) {
            cell = new org.telegram.ui.Cells.PrimeInfoCell(getContext(), title, summary, details);
            infoCells.put(id, cell);
        }
        return cell == null ? UItem.asShadow(summary) : UItem.asCustom(ID_INFO_BASE + id, cell);
    }

    private org.telegram.ui.Components.PrimeLivePreviewCell livePreviewCell;

    private org.telegram.ui.Components.PrimeLivePreviewCell livePreviewCell() {
        if (livePreviewCell == null && getContext() != null && getParentLayout() != null) {
            livePreviewCell = new org.telegram.ui.Components.PrimeLivePreviewCell(
                    getContext(), getParentLayout(), 0, null);
        }
        return livePreviewCell;
    }

    /** Pushes a settings change into the preview without rebuilding the whole list. */
    private void updateLivePreview() {
        if (livePreviewCell != null) {
            livePreviewCell.update();
        }
    }

    private View primeHeaderView;

    /**
     * The masthead: icon, name, build.
     *
     * <p>Costs one screenful of scroll and earns it back - this screen is reached from a row
     * inside Telegram's own settings, and without a header there is nothing to tell you which
     * application's settings you are now in, nor which build to quote when something breaks.
     */
    private UItem primeHeaderCell() {
        if (primeHeaderView == null && getContext() != null) {
            final Context context = getContext();
            final LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setGravity(Gravity.CENTER_HORIZONTAL);
            layout.setPadding(0, AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18));

            final ImageView logo = new ImageView(context);
            logo.setImageResource(R.mipmap.ic_launcher_round);
            layout.addView(logo, org.telegram.ui.Components.LayoutHelper.createLinear(72, 72));

            final TextView name = new TextView(context);
            name.setText("PrimeGram");
            name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 20);
            name.setTypeface(AndroidUtilities.bold());
            name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            name.setGravity(Gravity.CENTER);
            layout.addView(name, org.telegram.ui.Components.LayoutHelper.createLinear(
                    org.telegram.ui.Components.LayoutHelper.MATCH_PARENT,
                    org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));

            final TextView version = new TextView(context);
            version.setText(org.telegram.messenger.BuildVars.BUILD_VERSION_STRING);
            version.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 13);
            version.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            version.setGravity(Gravity.CENTER);
            layout.addView(version, org.telegram.ui.Components.LayoutHelper.createLinear(
                    org.telegram.ui.Components.LayoutHelper.MATCH_PARENT,
                    org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT, 0, 3, 0, 0));

            primeHeaderView = layout;
        }
        return UItem.asCustom(ID_SECTION_BASE + 99, primeHeaderView);
    }

    private UItem section(int section, IconBackgroundColors colors, int icon, CharSequence title, CharSequence subtitle) {
        return SettingsActivity.SettingCell.Factory.of(
                ID_SECTION_BASE + section, colors.top, colors.bottom, icon, title, subtitle);
    }

    private CharSequence sectionTitle() {

        switch (section) {

            case SECTION_INTERFACE: return "Интерфейс";

            case SECTION_CONNECTION: return "Соединение";

            case SECTION_PRIVACY: return "Приватность";

            case SECTION_TOOLS: return "Инструменты";

            case SECTION_MEDIA: return "Медиа и музыка";

            case SECTION_ADVANCED: return "Дополнительно";

            case SECTION_PREMIUM: return "Локальный Premium";

            case SECTION_ABOUT: return "Разрешения и поддержка";

            default: return "Настройки PrimeGram";

        }

    }

    @Override

    protected CharSequence getTitle() {

        return sectionTitle();

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

        if (section == SECTION_ROOT) {

            fillRoot(items);

            return;

        }

        final SharedPreferences preferences = MessagesController.getGlobalMainSettings();

        final MessagesController messagesController = MessagesController.getInstance(currentAccount);

        if (section == SECTION_INTERFACE) {

        items.add(UItem.asHeader("Интерфейс"));
        boolean sidebarEnabled = preferences.getBoolean("primegram_sidebar_enabled", true);
        UItem checkItem = UItem.asCheck(ID_SIDEBAR_ENABLED, "Боковая панель на основном экране");
        checkItem.checked = sidebarEnabled;
        items.add(checkItem);
        items.add(UItem.asShadow("Отображает стильную вертикальную боковую панель на главном экране списка чатов для быстрого доступа к переключению аккаунтов, кошельку, прокси и настройкам."));

        }

        if (section == SECTION_CONNECTION) {
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

        }

        if (section == SECTION_PRIVACY) {
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

        }

        if (section == SECTION_TOOLS) {
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

        items.add(UItem.asHeader("Расшифровка голосовых"));
        boolean hasPremium = org.telegram.messenger.UserConfig.getInstance(currentAccount).isPremium();
        if (hasPremium) {
            items.add(UItem.asShadow("У этого аккаунта есть Telegram Premium — расшифровка работает родными средствами Telegram и лучше интегрирована, поэтому подменять её нечем и незачем."));
        } else {
            UItem sttItem = UItem.asCheck(ID_STT_ENABLED, "Расшифровывать через внешний сервис");
            sttItem.checked = org.telegram.messenger.PrimeTranscription.isEnabled();
            items.add(sttItem);
            if (org.telegram.messenger.PrimeTranscription.isEnabled()) {
                String token = org.telegram.messenger.PrimeTranscription.getToken();
                items.add(UItem.asButton(ID_STT_TOKEN, "Ключ сервиса",
                        android.text.TextUtils.isEmpty(token) ? "не задан" : "задан"));
                items.add(UItem.asButton(ID_STT_ENDPOINT, "Адрес сервиса",
                        org.telegram.messenger.PrimeTranscription.getEndpoint()));
                items.add(UItem.asButton(ID_STT_MODEL, "Модель",
                        org.telegram.messenger.PrimeTranscription.getModel()));
            }
            UItem whisperItem = UItem.asCheck(ID_WHISPER_ENABLED, "Расшифровывать на устройстве");
            whisperItem.checked = org.telegram.messenger.PrimeWhisper.isEnabled();
            items.add(whisperItem);
            if (org.telegram.messenger.PrimeWhisper.isEnabled()) {
                final int model = org.telegram.messenger.PrimeWhisper.getModel();
                items.add(UItem.asButton(ID_WHISPER_MODEL, "Модель",
                        org.telegram.messenger.PrimeWhisper.MODEL_NAMES[model]));
                items.add(UItem.asButton(ID_WHISPER_DOWNLOAD,
                        org.telegram.messenger.PrimeWhisper.isModelDownloaded(model) ? "Удалить модель" : "Загрузить модель",
                        org.telegram.messenger.PrimeWhisper.MODEL_DESCRIPTIONS[model]));
                items.add(UItem.asButton(ID_WHISPER_LANGUAGE, "Язык записи",
                        whisperLanguageName()));
            }
            items.add(UItem.asShadow("Распознавание идёт прямо на телефоне: запись никуда не отправляется и работает без сети. Взамен нужно один раз скачать модель и подождать — на слабом телефоне минута речи разбирается заметно дольше, чем на сервере.\n\nЕсли скачана модель и включён внешний сервис одновременно, используется устройство: бесплатно и ничего не уходит наружу."));

            items.add(UItem.asShadow("Telegram отдаёт расшифровку только по Premium. Эта настройка отправляет голосовое во внешний сервис и подставляет ответ на место родной расшифровки.\n\nПо умолчанию — Groq: бесплатный тариф без карты, около 2000 расшифровок в сутки, ключ берётся на console.groq.com. Подойдёт любой сервис с совместимым API (OpenAI, Cloudflare, свой сервер) — впишите его адрес и модель.\n\nПонимайте, на что соглашаетесь: голосовое уходит на сервер, который не принадлежит ни Telegram, ни нам. Поэтому выключено по умолчанию и включается руками."));
        }

        items.add(UItem.asHeader("Встроенный браузер"));
        UItem adBlockItem = UItem.asCheck(ID_ADBLOCK, "Блокировать рекламу и трекеры");
        adBlockItem.checked = org.telegram.messenger.browser.PrimeAdBlock.isEnabled();
        items.add(adBlockItem);
        items.add(UItem.asShadow("Режет запросы к рекламным и следящим доменам во встроенном браузере. Главная страница сайта не блокируется никогда — только её содержимое, поэтому ошибка в списке может стоить картинки, но не самого сайта. Заблокировано за сеанс: "

                + org.telegram.messenger.browser.PrimeAdBlock.getBlockedCount() + "."));

        if (org.telegram.messenger.browser.PrimeAdBlock.isEnabled()) {
            items.add(UItem.asHeader("Списки блокировки"));
            for (int i = 0; i < org.telegram.messenger.browser.PrimeAdBlockLists.LIST_IDS.length; i++) {
                UItem listItem = UItem.asCheck(ID_ADBLOCK_LIST_BASE + i,
                        org.telegram.messenger.browser.PrimeAdBlockLists.LIST_NAMES[i]);
                listItem.subtext = org.telegram.messenger.browser.PrimeAdBlockLists.LIST_DESCRIPTIONS[i];
                listItem.checked = org.telegram.messenger.browser.PrimeAdBlockLists.isListEnabled(i);
                items.add(listItem);
            }
            items.add(UItem.asButton(ID_ADBLOCK_UPDATE, "Обновить списки", adBlockListsStatus()));
            items.add(UItem.asShadow("Те же списки, на которые подписаны AdGuard и uBlock Origin. Из них берутся только правила вида «весь домен целиком» — наш блокировщик видит имя хоста и ничего больше, поэтому правила по адресу страницы и правила, прячущие пустые блоки, пропускаются, а не применяются наполовину.\n\nСписки скачиваются напрямую, мимо нашего туннеля: туннель возит протокол Telegram и только его. Если до серверов списков не достучаться — так и будет написано."));
        }

        UItem dnsItem = UItem.asCheck(ID_DNS_ENABLED, "Свой DNS (DNS-over-HTTPS)");
        dnsItem.checked = org.telegram.messenger.browser.PrimeDns.isEnabled();
        items.add(dnsItem);
        if (org.telegram.messenger.browser.PrimeDns.isEnabled()) {
            items.add(UItem.asButton(ID_DNS_PRESET, "DNS-сервер",
                    org.telegram.messenger.browser.PrimeDns.currentName()));
            UItem dnsBlockItem = UItem.asCheck(ID_ADBLOCK_DNS, "Доверять вердикту DNS-сервера");
            dnsBlockItem.checked = org.telegram.messenger.browser.PrimeAdBlock.isDnsBlockingEnabled();
            items.add(dnsBlockItem);
        }
        items.add(UItem.asShadow("Запросы имён идут в зашифрованном виде мимо DNS провайдера — это самый дешёвый способ блокировки, и он так обходится. По умолчанию стоит AdGuard DNS: он сам отвечает «никуда» на рекламные домены, поэтому служит ещё и списком блокировки, который не надо обновлять вручную. Можно указать свой адрес — только https.\n\nВажно: пока это влияет на решение «блокировать или нет». Само соединение WebView всё ещё резолвит системным DNS — чтобы увести и его, нужен локальный прокси, он в работе."));

        }

        if (section == SECTION_MEDIA) {
        items.add(UItem.asHeader("Музыка"));
        items.add(UItem.asButton(ID_MUSIC_SETTINGS, "Настройки вкладки «Музыка»",
                org.telegram.messenger.music.MusicSettingsStore.isTabEnabled() ? "Включена" : "Выключена"));
        items.add(UItem.asShadow("Отправка текущего трека (Spotify, Яндекс Музыка, SoundCloud, VK, Last.fm, Telegram) карточкой, аудиофайлом или текстом. Вкладка появляется в панели эмодзи."));

        }

        if (section == SECTION_ADVANCED) {
        items.add(UItem.asHeader("Быстродействие"));
        UItem optimizationsItem = UItem.asCheck(ID_OPTIMIZATIONS, "Оптимизации PrimeGram");
        optimizationsItem.checked = org.telegram.messenger.PrimeTweaks.optimizations();
        items.add(optimizationsItem);
        items.add(info(7, "Оптимизации PrimeGram",
                "Ускоряют работу ценой памяти и фоновых действий.",
                "Сюда входят: подготовка вкладок «Профиль» и «Настройки» заранее, чтобы переход к ним был мгновенным; прогрев соединений туннеля при возврате в приложение, чтобы не ждать рукопожатие; увеличенный запас соединений для медиа, чтобы лента историй не открывалась по одной картинке.\n\nКаждая из них меняет память или фоновую работу на скорость. На большинстве устройств это выгодный обмен, но если приложение стало нестабильным или телефон греется — выключите и посмотрите, станет ли лучше. Это честнее, чем откатываться на старую сборку.\n\nК оптимизации батареи Android эта настройка отношения не имеет: та живёт в системных разрешениях и включается кнопкой выше."));

        items.add(UItem.asHeader("Диагностика"));
        UItem logsItem = UItem.asCheck(ID_LOGS_ENABLED, "Подробные логи");
        logsItem.checked = org.telegram.messenger.BuildVars.LOGS_ENABLED;
        items.add(logsItem);
        items.add(info(6, "Подробные логи",
                "Нужны только когда мы просим трассировку запуска.",
                "Telegram пишет в лог очень много, и каждая строка форматируется в том потоке, который её отправил, — включая главный. Постоянно включённые логи заметно замедляют работу и занимают место.\n\nВключайте, когда нужно снять трассировку запуска или разобраться с ошибкой, и выключайте после. Трассировка PrimeGram пишется в тот же лог, поэтому без этой настройки её не будет."));

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

        items.add(UItem.asButton(ID_HW_BENCHMARK, "Стресс-тест аппаратного ускорения"));
        items.add(UItem.asShadow("Декодирует одно и то же видео из кэша двумя путями подряд и показывает, сколько времени и процессора ушло на каждый. Работает независимо от настройки выше — тест сам включает и выключает аппаратный путь. Занимает несколько секунд, экран в это время лучше не гасить."));

        }

        if (section == SECTION_INTERFACE) {
        items.add(UItem.asHeader("Лента"));
        UItem feedHiddenItem = UItem.asCheck(ID_FEED_HIDDEN, "Скрыть вкладку «Лента»");
        feedHiddenItem.checked = preferences.getBoolean("primegram_feed_hidden", false);
        items.add(feedHiddenItem);
        items.add(UItem.asShadow("Убирает вкладку из нижней панели целиком. Остальные настройки ниже действуют, только пока лента показана."));

        items.add(UItem.asHeader("Сообщения"));
        UItem onlineDotsItem = UItem.asCheck(ID_ONLINE_DOTS, "Точка «в сети» у аватарок в группах");
        onlineDotsItem.checked = org.telegram.ui.Cells.PrimeMessageMarks.isOnlineDotsEnabled();
        items.add(onlineDotsItem);
        items.add(UItem.asShadow("Зелёная точка на аватарке отправителя в группах и каналах с обсуждением — видно, кто сейчас на связи, не открывая профиль. В списке чатов такие точки есть и без этой настройки."));

        items.add(UItem.asHeader("Список чатов"));
        UItem hideStoriesItem = UItem.asCheck(ID_HIDE_STORIES, "Скрыть истории");
        hideStoriesItem.checked = org.telegram.messenger.PrimeTweaks.get(org.telegram.messenger.PrimeTweaks.HIDE_STORIES);
        items.add(hideStoriesItem);
        UItem hideFabItem = UItem.asCheck(ID_HIDE_FAB, "Скрыть кнопку «Написать»");
        hideFabItem.checked = org.telegram.messenger.PrimeTweaks.get(org.telegram.messenger.PrimeTweaks.HIDE_FLOATING_BUTTON);
        items.add(hideFabItem);
        UItem archiveOnPullItem = UItem.asCheck(ID_ARCHIVE_ON_PULL, "Архив открывается потягиванием");
        archiveOnPullItem.checked = org.telegram.messenger.SharedConfig.archiveHidden;
        items.add(archiveOnPullItem);
        UItem noUnarchiveSwipeItem = UItem.asCheck(ID_DISABLE_UNARCHIVE_SWIPE, "Не разархивировать свайпом");
        noUnarchiveSwipeItem.checked = org.telegram.messenger.PrimeTweaks.get(org.telegram.messenger.PrimeTweaks.DISABLE_UNARCHIVE_SWIPE);
        items.add(noUnarchiveSwipeItem);
        UItem hideArchiveItem = UItem.asCheck(ID_HIDE_ARCHIVE_FOLDER, "Убрать строку «Архив»");
        hideArchiveItem.checked = org.telegram.messenger.PrimeTweaks.get(org.telegram.messenger.PrimeTweaks.HIDE_ARCHIVE_FOLDER);
        items.add(hideArchiveItem);
        UItem hideAllChatsItem = UItem.asCheck(ID_HIDE_ALL_CHATS, "Убрать вкладку «Все чаты»");
        hideAllChatsItem.checked = org.telegram.messenger.PrimeTweaks.get(org.telegram.messenger.PrimeTweaks.HIDE_ALL_CHATS);
        items.add(hideAllChatsItem);
        UItem miniAvatarsItem = UItem.asCheck(ID_SENDER_MINI_AVATARS, "Аватарка отправителя в превью");
        miniAvatarsItem.checked = org.telegram.messenger.PrimeTweaks.get(org.telegram.messenger.PrimeTweaks.SENDER_MINI_AVATARS);
        items.add(miniAvatarsItem);
        items.add(info(4, "Список чатов",
                "Ничего не удаляется — только убирается с глаз.",
                "Истории убираются там же, где принимается решение о их показе, поэтому пустого места не остаётся.\n\nКнопка «Написать» прячется только в списке чатов — при выборе чата для пересылки она остаётся, иначе подтвердить отправку было бы нечем.\n\nСвайп внутри архива блокируется только для действия «Архивировать»; если у вас на свайп назначено «Прочитать» или «Закрепить», оно продолжит работать.\n\nСтрока «Архив» пропадает только из списка — сам архив и всё, что в нём лежит, остаётся на месте и открывается из бокового меню.\n\nВкладка «Все чаты» убирается, если у вас есть хотя бы одна папка: без папок убирать нечего, иначе не осталось бы ни одной вкладки.\n\nАватарка отправителя показывается перед текстом последнего сообщения и только в группах: в личной переписке она бы повторяла аватарку самого чата, стоящую в паре пикселей левее. Свои сообщения остаются без значка."));

        items.add(UItem.asHeader("В чатах"));
        UItem hideShareItem = UItem.asCheck(ID_HIDE_SHARE_BUTTON, "Скрыть кнопку «Поделиться»");
        hideShareItem.checked = org.telegram.messenger.PrimeTweaks.get(org.telegram.messenger.PrimeTweaks.HIDE_SHARE_BUTTON);
        items.add(hideShareItem);
        UItem editedIconItem = UItem.asCheck(ID_EDITED_AS_ICON, "«Изменено» значком");
        editedIconItem.checked = org.telegram.messenger.PrimeTweaks.get(org.telegram.messenger.PrimeTweaks.EDITED_AS_ICON);
        items.add(editedIconItem);
        UItem commaItem = UItem.asCheck(ID_COMMA_AFTER_MENTION, "Запятая после упоминания");
        commaItem.checked = org.telegram.messenger.PrimeTweaks.get(org.telegram.messenger.PrimeTweaks.COMMA_AFTER_MENTION);
        items.add(commaItem);
        UItem hideSendAsItem = UItem.asCheck(ID_HIDE_SEND_AS_PEER, "Скрыть выбор «отправить от имени»");
        hideSendAsItem.checked = org.telegram.messenger.PrimeTweaks.get(org.telegram.messenger.PrimeTweaks.HIDE_SEND_AS_PEER);
        items.add(hideSendAsItem);
        UItem squareFabItem = UItem.asCheck(ID_SQUARE_FAB, "Квадратная кнопка «Написать»");
        squareFabItem.checked = org.telegram.messenger.PrimeTweaks.get(org.telegram.messenger.PrimeTweaks.SQUARE_FAB);
        items.add(squareFabItem);
        UItem hideKeyboardItem = UItem.asCheck(ID_HIDE_KEYBOARD_ON_SCROLL, "Прятать клавиатуру при прокрутке");
        hideKeyboardItem.checked = org.telegram.messenger.PrimeTweaks.get(org.telegram.messenger.PrimeTweaks.HIDE_KEYBOARD_ON_SCROLL);
        items.add(hideKeyboardItem);
        UItem hideStickerTimeItem = UItem.asCheck(ID_HIDE_STICKER_TIME, "Скрыть время на стикерах и кружочках");
        hideStickerTimeItem.checked = org.telegram.messenger.PrimeTweaks.get(org.telegram.messenger.PrimeTweaks.HIDE_STICKER_TIME);
        items.add(hideStickerTimeItem);
        items.add(UItem.asHeader("Размер стикеров"));
        if (stickerSizeCards() != null) {
            items.add(UItem.asCustom(ID_STICKER_SIZE_CARDS, stickerSizeCards()));
        }
        if (stickerSizeSlider() != null) {
            items.add(UItem.asCustom(ID_STICKER_SIZE_SLIDER, stickerSizeSlider()));
        }
        items.add(UItem.asHeader("Двойное нажатие по сообщению"));
        if (doubleTapCards() != null) {
            items.add(UItem.asCustom(ID_DOUBLE_TAP_CARDS, doubleTapCards()));
        }
        // Only meaningful when a double tap actually sets a reaction. Straight to Telegram's own
        // picker rather than our own: it already handles the whole emoji set, custom emoji and
        // the Premium rules around them, and a second picker would be a second set of rules to
        // keep in step with the first.
        if (org.telegram.messenger.PrimeTweaks.doubleTapAction() == org.telegram.messenger.PrimeTweaks.DOUBLE_TAP_REACTION) {
            items.add(UItem.asButton(ID_DOUBLE_TAP_REACTION, "Какая реакция", doubleTapReactionName()));
        }
        UItem menuSaveItem = UItem.asCheck(ID_MENU_SAVE, "Пункт «В избранное»");
        menuSaveItem.checked = org.telegram.messenger.PrimeTweaks.get(org.telegram.messenger.PrimeTweaks.MENU_SAVE_TO_SAVED);
        items.add(menuSaveItem);
        UItem menuDetailsItem = UItem.asCheck(ID_MENU_DETAILS, "Пункт «Подробности»");
        menuDetailsItem.checked = org.telegram.messenger.PrimeTweaks.get(org.telegram.messenger.PrimeTweaks.MENU_DETAILS);
        items.add(menuDetailsItem);
        UItem adminItem = UItem.asCheck(ID_ADMIN_SHORTCUTS, "Админ-действия в меню сообщения");
        adminItem.checked = org.telegram.messenger.PrimeTweaks.get(org.telegram.messenger.PrimeTweaks.ADMIN_SHORTCUTS);
        items.add(adminItem);
        items.add(info(2, "В чатах",
                "Новые пункты появляются внизу меню долгого нажатия.",
                "Клавиатура закрывается только при прокрутке пальцем — переход к ответу или новое сообщение её не тронут.\n\n«В избранное» пересылает сообщение в «Избранное» без выбора чата, альбом целиком.\n\n«Подробности» показывает ID сообщения, отправителя и время отправки и правки — всё копируется одной кнопкой.\n\nАдмин-действия — «Забанить» и «Удалить все сообщения» — появляются только в группах, где у вас есть право блокировать участников, и только на чужих сообщениях. Оба спрашивают подтверждение. Автора-канал они не трогают: это другой запрос, и он остаётся в профиле."));

        items.add(UItem.asHeader("Медиа"));
        UItem uncompressedItem = UItem.asCheck(ID_SEND_UNCOMPRESSED, "Отправлять без сжатия");
        uncompressedItem.checked = org.telegram.messenger.PrimeTweaks.get(org.telegram.messenger.PrimeTweaks.SEND_UNCOMPRESSED);
        items.add(uncompressedItem);
        UItem saveRoundItem = UItem.asCheck(ID_SAVE_ROUND_VOICE, "Сохранять кружочки и голосовые");
        saveRoundItem.checked = org.telegram.messenger.PrimeTweaks.get(org.telegram.messenger.PrimeTweaks.SAVE_ROUND_AND_VOICE);
        items.add(saveRoundItem);
        items.add(UItem.asHeader("Качество видео"));
        if (videoQualityCards() != null) {
            items.add(UItem.asCustom(ID_VIDEO_QUALITY_CARDS, videoQualityCards()));
        }
        UItem preloadItem = UItem.asCheck(ID_PRELOAD_VIDEO_MOBILE, "Догружать видео на мобильной сети");
        preloadItem.checked = org.telegram.messenger.DownloadController.getInstance(currentAccount).primeMobilePreloadVideo();
        items.add(preloadItem);
        items.add(UItem.asButton(ID_AUTODOWNLOAD, "Автозагрузка медиа"));
        items.add(UItem.asButton(ID_CACHE, "Кэш медиа", cacheSizeText()));
        items.add(info(5, "Медиа",
                "Ограничение качества экономит трафик, а не только пиксели.",
                "Отправка без сжатия переключает главную кнопку в режим «файлом» — тот же, что в меню вложений. На контакты, музыку и геопозицию это не влияет: для них «файлом» ничего не значит.\n\nСохранение кружочков и голосовых добавляет пункт в меню долгого нажатия: кружочек уходит в галерею, голосовое — в загрузки. Одноразовые сообщения не сохраняются: отправитель выбрал исчезающее сообщение, и обходить это мы не будем.\n\nКачество видео ограничивает то, что скачивается, а не только то, что играет: скачивается ровно та дорожка, которую выбирает плеер. Если ни одна не помещается в лимит, берётся обычная — лимит не должен оставить видео непроигрываемым. Уже скачанное не перекачивается заново, даже если оно крупнее лимита. Настройка применяется к сообщениям, открытым после её изменения.\n\nВыключенная догрузка на мобильной сети переводит автозагрузку в режим «Свой» — иначе правка задела бы заодно Wi-Fi и роуминг, у которых с готовыми пресетами общий объект. Остальные значения при этом переносятся как были.\n\nКэш — только скачанное для просмотра. Файлы, которые вы сами сохранили в загрузки или галерею, кнопка не трогает."));

        items.add(UItem.asHeader("Камера"));
        UItem rearRoundItem = UItem.asCheck(ID_ROUND_VIDEO_REAR, "Кружочки с основной камеры");
        rearRoundItem.checked = org.telegram.messenger.PrimeTweaks.get(org.telegram.messenger.PrimeTweaks.ROUND_VIDEO_REAR);
        items.add(rearRoundItem);
        UItem camera2Item = UItem.asCheck(ID_CAMERA2, "Camera2 API");
        camera2Item.checked = org.telegram.messenger.SharedConfig.isUsingCamera2(currentAccount);
        items.add(camera2Item);
        items.add(UItem.asShadow("Обычно кружочки всегда начинаются с фронтальной камеры, и переключение стоит нажатия и заметного перезапуска картинки.\n\nCamera2 — более новый интерфейс камеры Android: лучше автофокус и экспозиция, но на части прошивок он работает хуже старого. Переключатель есть и в отладочном меню Telegram, здесь он просто на виду. Важно: на съёмку фото и видео он сейчас не влияет — в самом Telegram Camera2 для основной камеры отключён в коде, — так что меняет он поведение только кружочков."));

        items.add(UItem.asHeader("Чем переводить"));
        if (translatorCards() != null) {
            items.add(UItem.asCustom(ID_TRANSLATOR_CARDS, translatorCards()));
        }
        items.add(info(3, "Перевод",
                "Google и Yandex не требуют Premium, но теряют форматирование.",
                "Перевод через Telegram идёт по тому же соединению, что и всё остальное, и подчиняется ограничениям аккаунта.\n\nGoogle и Yandex работают по обычной сети — это выручает, когда туннель тормозит, и не требует Premium. Сети у них разные, так что если один недоступен, стоит попробовать другой.\n\nВзамен они теряют форматирование: жирный шрифт, ссылки и упоминания в переведённом тексте пропадут. Поэтому по умолчанию стоит Telegram.\n\nСтатьи Instant View переводятся через Telegram в любом случае — там перевод возвращает не текст, а свёрстанную страницу."));

        items.add(UItem.asHeader("Профиль"));
        UItem showIdItem = UItem.asCheck(ID_SHOW_ID_AND_DC, "Показывать ID и дата-центр");
        showIdItem.checked = org.telegram.messenger.PrimeTweaks.get(org.telegram.messenger.PrimeTweaks.SHOW_ID_AND_DC);
        items.add(showIdItem);
        items.add(UItem.asShadow("Строка с числовым ID собеседника, канала или группы — по нажатию копируется. Дата-центр показывается только когда у собеседника есть аватар: узнать его больше неоткуда."));

        items.add(UItem.asHeader("Оформление"));
        // The preview goes above the switches that change it: reaching for a switch and watching
        // the result appear in the same glance is the whole point of having one.
        if (livePreviewCell() != null) {
            items.add(UItem.asCustom(ID_LIVE_PREVIEW, livePreviewCell()));
        }
        UItem snowItem = UItem.asCheck(ID_FORCE_SNOW, "Снег круглый год");
        snowItem.checked = org.telegram.messenger.PrimeTweaks.get(org.telegram.messenger.PrimeTweaks.FORCE_SNOW);
        items.add(snowItem);
        UItem centerTitleItem = UItem.asCheck(ID_CENTER_TITLE, "Заголовок по центру");
        centerTitleItem.checked = org.telegram.messenger.PrimeTweaks.get(org.telegram.messenger.PrimeTweaks.CENTER_TITLE);
        items.add(centerTitleItem);
        UItem titleUsernameItem = UItem.asCheck(ID_MAIN_TITLE_USERNAME, "Вместо логотипа — свой ник");
        titleUsernameItem.checked = org.telegram.messenger.PrimeTweaks.get(org.telegram.messenger.PrimeTweaks.MAIN_TITLE_USERNAME);
        items.add(titleUsernameItem);
        UItem hideStatusItem = UItem.asCheck(ID_HIDE_EMOJI_STATUS, "Скрыть свой эмодзи-статус");
        hideStatusItem.checked = org.telegram.messenger.PrimeTweaks.get(org.telegram.messenger.PrimeTweaks.HIDE_EMOJI_STATUS);
        items.add(hideStatusItem);
        UItem hideHeaderItem = UItem.asCheck(ID_HIDE_SETTINGS_HEADER, "Убрать шапку профиля в настройках");
        hideHeaderItem.checked = org.telegram.messenger.PrimeTweaks.get(org.telegram.messenger.PrimeTweaks.HIDE_SETTINGS_HEADER);
        items.add(hideHeaderItem);
        UItem noTailItem = UItem.asCheck(ID_REMOVE_TAIL, "Пузыри без хвостика");
        noTailItem.checked = org.telegram.messenger.PrimeTweaks.get(org.telegram.messenger.PrimeTweaks.REMOVE_MESSAGE_TAIL);
        items.add(noTailItem);
        if (avatarCornersCell() != null) {
            items.add(UItem.asCustom(ID_AVATAR_CORNERS, avatarCornersCell()));
        }
        items.add(info(1, "Оформление",
                "Форма аватарок меняется сразу и везде.",
                "Снегопад и новогодняя шапка у заголовка — те же, что Telegram показывает 31 декабря, только без привязки к дате.\n\nЗаголовок центрируется лишь когда для этого есть место: если название длинное и наехало бы на кнопки, оно остаётся слева.\n\nФорма аватарок меняется прямо во время перетаскивания и сразу везде — в списке чатов, в шапке чата, в профиле. Круги, которые рисует не аватарка, а что-то другое — кружочки-видео, значки — остаются кругами.\n\nСкругление задаётся долей, а не числом точек: поэтому на маленькой аватарке оно выглядит так же, как на большой, и в примере показаны сразу четыре размера."));

        items.add(UItem.asHeader("Реакции"));
        UItem reactChannelsItem = UItem.asCheck(ID_HIDE_REACTIONS_CHANNELS, "Скрыть в каналах");
        reactChannelsItem.checked = org.telegram.messenger.PrimeTweaks.get(org.telegram.messenger.PrimeTweaks.HIDE_REACTIONS_CHANNELS);
        items.add(reactChannelsItem);
        UItem reactGroupsItem = UItem.asCheck(ID_HIDE_REACTIONS_GROUPS, "Скрыть в группах");
        reactGroupsItem.checked = org.telegram.messenger.PrimeTweaks.get(org.telegram.messenger.PrimeTweaks.HIDE_REACTIONS_GROUPS);
        items.add(reactGroupsItem);
        UItem reactPrivateItem = UItem.asCheck(ID_HIDE_REACTIONS_PRIVATE, "Скрыть в личных чатах");
        reactPrivateItem.checked = org.telegram.messenger.PrimeTweaks.get(org.telegram.messenger.PrimeTweaks.HIDE_REACTIONS_PRIVATE);
        items.add(reactPrivateItem);
        items.add(UItem.asShadow("Реакции перестают рисоваться под сообщениями выбранного типа чатов. Ставить свои реакции через меню сообщения по-прежнему можно."));

        items.add(UItem.asHeader("Форматирование"));
        UItem relativeSeenItem = UItem.asCheck(ID_RELATIVE_LAST_SEEN, "«5 минут назад» вместо времени");
        relativeSeenItem.checked = org.telegram.messenger.PrimeTweaks.get(org.telegram.messenger.PrimeTweaks.RELATIVE_LAST_SEEN);
        items.add(relativeSeenItem);
        UItem noRoundingItem = UItem.asCheck(ID_NO_NUMBER_ROUNDING, "Не округлять числа");
        noRoundingItem.checked = org.telegram.messenger.PrimeTweaks.get(org.telegram.messenger.PrimeTweaks.DISABLE_NUMBER_ROUNDING);
        items.add(noRoundingItem);
        UItem secondsItem = UItem.asCheck(ID_TIME_WITH_SECONDS, "Показывать секунды во времени");
        secondsItem.checked = org.telegram.messenger.PrimeTweaks.get(org.telegram.messenger.PrimeTweaks.TIME_WITH_SECONDS);
        items.add(secondsItem);
        items.add(UItem.asShadow("«Был(а) 5 минут назад» вместо метки времени — только для последних суток, дальше точная дата понятнее. Числа подписчиков и просмотров показываются полностью: 1 234 567 вместо 1M. Секунды добавляются везде, где показывается время, с сохранением 12- или 24-часового формата вашей локали."));
        boolean feedExcludeMuted = preferences.getBoolean("primegram_feed_exclude_muted", false);
        boolean feedExcludeArchived = preferences.getBoolean("primegram_feed_exclude_archived", false);
        UItem feedExcludeMutedItem = UItem.asCheck(ID_FEED_EXCLUDE_MUTED, "Скрывать чаты без уведомлений");
        feedExcludeMutedItem.checked = feedExcludeMuted;
        items.add(feedExcludeMutedItem);
        UItem feedExcludeArchivedItem = UItem.asCheck(ID_FEED_EXCLUDE_ARCHIVED, "Скрывать чаты из архива");
        feedExcludeArchivedItem.checked = feedExcludeArchived;
        items.add(feedExcludeArchivedItem);
        items.add(UItem.asShadow("Настройки отображения каналов и групп во вкладке Лента."));

        }

        if (section == SECTION_ADVANCED) {
        items.add(UItem.asHeader("Обновления приложения"));
        boolean autoUpdates = preferences.getBoolean("primegram_auto_updates", true);
        UItem autoUpdatesItem = UItem.asCheck(ID_AUTO_UPDATES, "Автоматически скачивать обновления");
        autoUpdatesItem.checked = autoUpdates;
        items.add(autoUpdatesItem);
        items.add(UItem.asButton(ID_CHECK_UPDATES, "Проверить обновления", ""));
        items.add(UItem.asShadow("PrimeGram может автоматически проверять релизы на GitHub и скачивать новые версии."));

        }

        if (section == SECTION_PREMIUM) {
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
        items.add(UItem.asButton(ID_LIMIT_RECENT_STICKERS, "Лимит недавних стикеров",
                String.valueOf(messagesController.maxRecentStickersCount)));
        items.add(UItem.asShadow("Лимиты на количество гифок в панели отправки, избранных и недавних стикеров. Недавние стикеры обрезает сам клиент, поэтому это ограничение снимается полностью и без участия сервера."));

        items.add(UItem.asHeader("Лимиты профиля и текста"));
        items.add(UItem.asButton(ID_LIMIT_PUBLIC_LINKS, "Лимит публичных ссылок", String.valueOf(messagesController.publicLinksLimitPremium)));
        items.add(UItem.asButton(ID_LIMIT_CAPTION, "Лимит символов в описании медиа", String.valueOf(messagesController.captionLengthLimitPremium)));
        items.add(UItem.asButton(ID_LIMIT_ABOUT, "Лимит символов в разделе «О себе»", String.valueOf(messagesController.aboutLengthLimitPremium)));
        items.add(UItem.asShadow("Символьные ограничения для описания медиафайлов и био вашего аккаунта."));
        }

        if (section == SECTION_ADVANCED) {
        items.add(UItem.asButton(ID_STARTUP_TRACE, "Трасса запуска", "диагностика"));
        items.add(UItem.asShadow("Сколько миллисекунд занял каждый этап последнего холодного старта: загрузка нативных библиотек, открытие базы, появление списка чатов. Нужна, чтобы оптимизировать по замерам, а не по догадкам."));

        }

        if (section == SECTION_ABOUT) {
        items.add(UItem.asHeader("Разрешения и поддержка"));
        items.add(UItem.asButton(ID_GRANT_PERMISSIONS, "Выдать системные разрешения", "Контакты, звонки, память, уведомления"));
        items.add(UItem.asButton(ID_LOCKSCREEN_CALLS, "Звонки на заблокированном экране", primeLockScreenCallsStatus()));
        items.add(UItem.asShadow("PrimeGram не спрашивает разрешения сам: у оригинала они вываливаются на список чатов друг поверх друга и поверх системных окон, и их закрывают не читая. Здесь их выдаёте вы, когда сами этого захотели.\n\nБез второго пункта входящий звонок не покажет экран вызова, пока телефон заблокирован — придёт только уведомление."));
        
        items.add(UItem.asButton(ID_SUPPORT_PROJECT, "Поддержать проект (USDT TON)", "Отправить донат через @wallet"));
        items.add(UItem.asShadow("Спасибо за вашу поддержку! Это помогает развивать PrimeGram."));
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id >= ID_SECTION_BASE) {
            presentFragment(new PrimeGramSettingsActivity(item.id - ID_SECTION_BASE));
            return;
        }
        if (item.id >= ID_ADBLOCK_LIST_BASE
                && item.id < ID_ADBLOCK_LIST_BASE + org.telegram.messenger.browser.PrimeAdBlockLists.LIST_IDS.length) {
            int index = item.id - ID_ADBLOCK_LIST_BASE;
            boolean nowEnabled = !org.telegram.messenger.browser.PrimeAdBlockLists.isListEnabled(index);
            org.telegram.messenger.browser.PrimeAdBlockLists.setListEnabled(index, nowEnabled);
            listView.adapter.update(true);
            if (nowEnabled) {
                // a list nobody downloaded blocks nothing, and that reads as a broken switch
                updateAdBlockLists();
            }
            return;
        }
        MessagesController messagesController = MessagesController.getInstance(currentAccount);
        String tweakKey = primeTweakKeyFor(item.id);
        if (tweakKey != null) {
            org.telegram.messenger.PrimeTweaks.set(tweakKey, !org.telegram.messenger.PrimeTweaks.get(tweakKey));
            // Every tweak, not a chosen few: the preview is a picture of the app, and a switch
            // that changes nothing in it simply redraws the same picture.
            updateLivePreview();
            if (item.id == ID_FORCE_SNOW) {
                // The holiday check only re-runs once a minute; without this the toggle looks
                // like it did nothing until the user waits it out.
                org.telegram.ui.ActionBar.Theme.primeInvalidateHoliday();
            }
            if (item.id == ID_SENDER_MINI_AVATARS) {
                // Previews are built once per bind and cached, so nothing changes until the
                // list is told to rebuild them.
                org.telegram.messenger.NotificationCenter.getGlobalInstance()
                        .postNotificationName(org.telegram.messenger.NotificationCenter.dialogsNeedReload, true);
            }
            if (item.id == ID_HIDE_ARCHIVE_FOLDER) {
                org.telegram.messenger.NotificationCenter.getGlobalInstance()
                        .postNotificationName(org.telegram.messenger.NotificationCenter.dialogsNeedReload, true);
            }
            if (item.id == ID_HIDE_ALL_CHATS) {
                // the tab strip is only rebuilt when the folder set changes, so say it did
                for (int a = 0; a < org.telegram.messenger.UserConfig.MAX_ACCOUNT_COUNT; a++) {
                    if (org.telegram.messenger.UserConfig.getInstance(a).isClientActivated()) {
                        org.telegram.messenger.NotificationCenter.getInstance(a)
                                .postNotificationName(org.telegram.messenger.NotificationCenter.dialogFiltersUpdated);
                    }
                }
            }
            listView.adapter.update(true);
        } else if (item.id == ID_STICKER_SIZE) {
            showStickerSizePicker();
        } else if (item.id == ID_DOUBLE_TAP) {
            showDoubleTapPicker();
        } else if (item.id == ID_TRANSLATE_PROVIDER) {
            showTranslateProviderPicker();
        } else if (item.id == ID_CACHE) {
            showCacheDialog();
        } else if (item.id == ID_VIDEO_QUALITY) {
            showVideoQualityPicker();
        } else if (item.id == ID_PRELOAD_VIDEO_MOBILE) {
            togglePreloadVideoOnMobile();
        } else if (item.id == ID_AUTODOWNLOAD) {
            // Straight to the stock screen rather than a copy of it here: the presets are one
            // stored value, and two screens writing it would eventually disagree.
            presentFragment(new org.telegram.ui.DataSettingsActivity());
        } else if (item.id == ID_WHISPER_ENABLED) {
            final boolean enabled = !org.telegram.messenger.PrimeWhisper.isEnabled();
            org.telegram.messenger.PrimeWhisper.setEnabled(enabled);
            if (!enabled) {
                // The loaded model is tens of megabytes of resident memory; switching the feature
                // off has to actually give it back.
                org.telegram.messenger.PrimeWhisper.release();
            }
            listView.adapter.update(true);
        } else if (item.id == ID_WHISPER_MODEL) {
            showWhisperModelPicker();
        } else if (item.id == ID_WHISPER_DOWNLOAD) {
            toggleWhisperModel();
        } else if (item.id == ID_WHISPER_LANGUAGE) {
            showWhisperLanguagePicker();
        } else if (item.id == ID_DOUBLE_TAP_REACTION) {
            presentFragment(new org.telegram.ui.ReactionsDoubleTapManageActivity());
        } else if (item.id == ID_LOGS_ENABLED) {
            // The same preference the debug menu writes, so the two can never disagree.
            org.telegram.messenger.ApplicationLoader.applicationContext
                    .getSharedPreferences("systemConfig", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("logsEnabled", org.telegram.messenger.BuildVars.LOGS_ENABLED = !org.telegram.messenger.BuildVars.LOGS_ENABLED)
                    .commit();
            listView.adapter.update(true);
        } else if (item.id == ID_CAMERA2) {
            // Upstream state, not ours: the same value the debug menu toggles, so the two screens
            // can never disagree about it.
            org.telegram.messenger.SharedConfig.toggleUseCamera2(currentAccount);
            listView.adapter.update(true);
        } else if (item.id == ID_ADBLOCK_UPDATE) {
            updateAdBlockLists();
        } else if (item.id == ID_HW_BENCHMARK) {
            runHwBenchmark();
        } else if (item.id == ID_ARCHIVE_ON_PULL) {
            // Upstream already has this state - it is what the "swipe the archive row up"
            // gesture toggles. We only surface it as a setting.
            org.telegram.messenger.SharedConfig.toggleArchiveHidden();
            org.telegram.messenger.NotificationCenter.getGlobalInstance()
                    .postNotificationName(org.telegram.messenger.NotificationCenter.dialogsNeedReload, true);
            listView.adapter.update(true);
        } else if (item.id == ID_RELATIVE_LAST_SEEN || item.id == ID_NO_NUMBER_ROUNDING || item.id == ID_TIME_WITH_SECONDS) {
            String key = item.id == ID_RELATIVE_LAST_SEEN ? org.telegram.messenger.PrimeTweaks.RELATIVE_LAST_SEEN
                    : item.id == ID_NO_NUMBER_ROUNDING ? org.telegram.messenger.PrimeTweaks.DISABLE_NUMBER_ROUNDING
                    : org.telegram.messenger.PrimeTweaks.TIME_WITH_SECONDS;
            org.telegram.messenger.PrimeTweaks.set(key, !org.telegram.messenger.PrimeTweaks.get(key));
            if (item.id == ID_TIME_WITH_SECONDS) {
                // The time formatters are built once and cached; they have to be thrown away
                // or the new pattern only appears after a restart.
                LocaleController.getInstance().recreateFormatters();
            }
            listView.adapter.update(true);
        } else if (item.id == ID_STT_ENABLED) {
            org.telegram.messenger.PrimeTranscription.setEnabled(!org.telegram.messenger.PrimeTranscription.isEnabled());
            listView.adapter.update(true);
        } else if (item.id == ID_STT_TOKEN) {
            showTextInputDialog("Ключ сервиса",
                    "Ключ доступа к сервису расшифровки. Для Groq — бесплатно и без карты на console.groq.com.",
                    org.telegram.messenger.PrimeTranscription.getToken(), "gsk_…",
                    value -> org.telegram.messenger.PrimeTranscription.setToken(value));
        } else if (item.id == ID_STT_ENDPOINT) {
            showTextInputDialog("Адрес сервиса",
                    "Полный URL метода расшифровки, совместимого с OpenAI. Пустое поле вернёт адрес Groq.",
                    org.telegram.messenger.PrimeTranscription.getEndpoint(),
                    org.telegram.messenger.PrimeTranscription.DEFAULT_ENDPOINT,
                    value -> org.telegram.messenger.PrimeTranscription.setEndpoint(value));
        } else if (item.id == ID_STT_MODEL) {
            showTextInputDialog("Модель",
                    "Имя модели распознавания у выбранного сервиса. Пустое поле вернёт модель по умолчанию.",
                    org.telegram.messenger.PrimeTranscription.getModel(),
                    org.telegram.messenger.PrimeTranscription.DEFAULT_MODEL,
                    value -> org.telegram.messenger.PrimeTranscription.setModel(value));
        } else if (item.id == ID_ONLINE_DOTS) {
            SharedPreferences prefs = MessagesController.getGlobalMainSettings();
            boolean on = prefs.getBoolean(org.telegram.ui.Cells.PrimeMessageMarks.ONLINE_DOTS_KEY, true);
            prefs.edit().putBoolean(org.telegram.ui.Cells.PrimeMessageMarks.ONLINE_DOTS_KEY, !on).apply();
            org.telegram.ui.Cells.PrimeMessageMarks.invalidateOnlineDots();
            listView.adapter.update(true);
        } else if (item.id == ID_ADBLOCK) {
            org.telegram.messenger.browser.PrimeAdBlock.setEnabled(!org.telegram.messenger.browser.PrimeAdBlock.isEnabled());
            listView.adapter.update(true);
        } else if (item.id == ID_ADBLOCK_DNS) {
            org.telegram.messenger.browser.PrimeAdBlock.setDnsBlockingEnabled(!org.telegram.messenger.browser.PrimeAdBlock.isDnsBlockingEnabled());
            listView.adapter.update(true);
        } else if (item.id == ID_DNS_ENABLED) {
            org.telegram.messenger.browser.PrimeDns.setEnabled(!org.telegram.messenger.browser.PrimeDns.isEnabled());
            listView.adapter.update(true);
        } else if (item.id == ID_DNS_PRESET) {
            showDnsPicker();
        } else if (item.id == ID_LIMIT_RECENT_STICKERS) {
            showRecentStickersPicker(messagesController);
        } else if (item.id == ID_SIDEBAR_ENABLED) {
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
            boolean enabled = preferences.getBoolean("primegram_auto_updates", true);
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
                    // asked here rather than at the chat list, where it landed on top of everything
                    perms.add(android.Manifest.permission.POST_NOTIFICATIONS);
                } else {
                    perms.add(android.Manifest.permission.READ_EXTERNAL_STORAGE);
                    perms.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
                }
                perms.add(android.Manifest.permission.READ_PHONE_STATE);
                perms.add(android.Manifest.permission.CALL_PHONE);
                getParentActivity().requestPermissions(perms.toArray(new String[0]), 100);
            }
        } else if (item.id == ID_LOCKSCREEN_CALLS) {
            openLockScreenCallSettings();
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
        } else if (item.id == ID_FEED_HIDDEN) {
            SharedPreferences prefs = MessagesController.getGlobalMainSettings();
            boolean hidden = prefs.getBoolean("primegram_feed_hidden", false);
            prefs.edit().putBoolean("primegram_feed_hidden", !hidden).apply();
            listView.adapter.update(true);
            MainTabsActivity.refreshFeedTabVisibility();
        } else if (item.id == ID_STARTUP_TRACE) {
            showStartupTrace();
        } else if (item.id == ID_SESSION_NAME) {
            showSessionNameDialog();
        } else if (item.id == ID_BOT_LOGIN) {
            presentFragment(new BotLoginActivity());
        }
    }

    private void showStartupTrace() {
        if (getParentActivity() == null) {
            return;
        }
        final String trace = org.telegram.messenger.PrimeStartupTrace.dump();
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Трасса запуска");
        builder.setMessage(trace);
        builder.setPositiveButton("Скопировать", (dialog, which) -> {
            AndroidUtilities.addToClipboard(trace);
            org.telegram.ui.Components.BulletinFactory.of(PrimeGramSettingsActivity.this).createCopyBulletin("Скопировано").show();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private interface TextInputCallback {
        void onValue(String value);
    }

    /** Shared one-line text prompt, so every string setting looks and behaves the same. */
    private void showTextInputDialog(String title, String message, String current, String hint, TextInputCallback callback) {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(title);
        builder.setMessage(message);

        final EditTextBoldCursor editText = new EditTextBoldCursor(getParentActivity());
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        editText.setHint(hint);
        editText.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setCursorSize(AndroidUtilities.dp(20));
        editText.setCursorWidth(1.5f);
        editText.setSingleLine(true);
        editText.setBackgroundDrawable(Theme.createEditTextDrawable(getParentActivity(), true));
        editText.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));
        editText.setText(current == null ? "" : current);
        editText.setSelection(editText.getText().length());

        LinearLayout container = new LinearLayout(getParentActivity());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);
        container.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        builder.setView(container);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> {
            callback.onValue(editText.getText().toString());
            listView.adapter.update(true);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showRecentStickersPicker(MessagesController messagesController) {
        showNumberInputDialog(ID_LIMIT_RECENT_STICKERS, "Лимит недавних стикеров",
                "Сколько недавно использованных стикеров помнить. Список обрезает сам клиент, так что значение работает без оглядки на сервер — но чем оно больше, тем больше стикеров хранится в базе.",
                messagesController.maxRecentStickersCount, 30, 30, 500);
    }

    private void runHwBenchmark() {
        if (getParentActivity() == null) {
            return;
        }
        final AlertDialog progress = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(false);
        progress.show();
        org.telegram.messenger.PrimeHwBenchmark.run(result -> {
            try {
                progress.dismiss();
            } catch (Throwable ignore) {
            }
            if (getParentActivity() == null) {
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle("Стресс-тест ускорения");
            builder.setMessage(org.telegram.messenger.PrimeHwBenchmark.format(result));
            builder.setPositiveButton("Закрыть", null);
            if (result.ok) {
                builder.setNeutralButton("Скопировать", (di, w) ->
                        AndroidUtilities.addToClipboard(org.telegram.messenger.PrimeHwBenchmark.format(result)));
            }
            showDialog(builder.create());
        });
    }

    private void showStickerSizePicker() {
        if (getParentActivity() == null) {
            return;
        }
        final int min = 6, max = 20;
        final CharSequence[] options = new CharSequence[max - min + 1];
        for (int i = 0; i < options.length; i++) {
            int value = min + i;
            options[i] = value == org.telegram.messenger.PrimeTweaks.STICKER_SIZE_DEFAULT
                    ? value + " — как в оригинале"
                    : String.valueOf(value);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Размер стикеров");
        builder.setItems(options, (dialog, which) -> {
            org.telegram.messenger.PrimeTweaks.setInt(org.telegram.messenger.PrimeTweaks.STICKER_SIZE, min + which);
            listView.adapter.update(true);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    /**
     * Whether the system will let an incoming call take over a locked screen. Two different
     * permissions answer that question depending on the phone: Android 14 introduced its own
     * full-screen-intent switch, and MIUI has had a separate one of its own for years.
     */
    private static String primeLockScreenCallsStatus() {
        try {
            if (org.telegram.messenger.XiaomiUtilities.isMIUI()
                    && !org.telegram.messenger.XiaomiUtilities.isCustomPermissionGranted(org.telegram.messenger.XiaomiUtilities.OP_SHOW_WHEN_LOCKED)) {
                return "Не разрешено";
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                android.app.NotificationManager nm = (android.app.NotificationManager)
                        ApplicationLoader.applicationContext.getSystemService(android.content.Context.NOTIFICATION_SERVICE);
                if (nm != null && !nm.canUseFullScreenIntent()) {
                    return "Не разрешено";
                }
            }
        } catch (Throwable ignore) {
            return "";
        }
        return "Разрешено";
    }

    private void openLockScreenCallSettings() {
        if (getParentActivity() == null) {
            return;
        }
        try {
            if (org.telegram.messenger.XiaomiUtilities.isMIUI()) {
                android.content.Intent intent = org.telegram.messenger.XiaomiUtilities.getPermissionManagerIntent();
                if (intent != null) {
                    getParentActivity().startActivity(intent);
                    return;
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT);
                intent.setData(android.net.Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                getParentActivity().startActivity(intent);
                return;
            }
            // older Android has no separate switch - the app settings page is the closest thing
            android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(android.net.Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
            getParentActivity().startActivity(intent);
        } catch (Throwable t) {
            org.telegram.messenger.FileLog.e(t);
        }
    }

    /** What the "Обновить списки" row says on its right-hand side. */
    private static String adBlockListsStatus() {
        if (!org.telegram.messenger.browser.PrimeAdBlockLists.hasAnyListEnabled()) {
            return "Список не выбран";
        }
        long updated = org.telegram.messenger.browser.PrimeAdBlockLists.lastUpdateTime();
        if (updated <= 0) {
            return "Ещё не загружено";
        }
        int rules = org.telegram.messenger.browser.PrimeAdBlockLists.ruleCount();
        long ageMs = System.currentTimeMillis() - updated;
        String age;
        if (ageMs < 60 * 60 * 1000L) {
            age = "только что";
        } else if (ageMs < 24 * 60 * 60 * 1000L) {
            age = (ageMs / (60 * 60 * 1000L)) + " ч назад";
        } else {
            age = (ageMs / (24 * 60 * 60 * 1000L)) + " дн назад";
        }
        return rules + " доменов, " + age;
    }

    /**
     * Cache sizes by kind, or null until the first measurement lands.
     *
     * <p>Measuring walks every cache directory, so it runs on a background thread and the row
     * shows a placeholder until it finishes rather than blocking the screen from opening.
     */
    private long[] cacheSizes;
    private boolean measuringCache;

    private CharSequence cacheSizeText() {
        if (cacheSizes == null) {
            measureCache();
            return "…";
        }
        long total = 0;
        for (long size : cacheSizes) {
            total += size;
        }
        return AndroidUtilities.formatFileSize(total);
    }

    private void measureCache() {
        if (measuringCache) {
            return;
        }
        measuringCache = true;
        org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
            final long[] measured = org.telegram.messenger.PrimeCache.measure();
            AndroidUtilities.runOnUIThread(() -> {
                measuringCache = false;
                cacheSizes = measured;
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
            });
        });
    }

    private void showCacheDialog() {
        if (getParentActivity() == null) {
            return;
        }
        if (cacheSizes == null) {
            measureCache();
            org.telegram.ui.Components.BulletinFactory.of(this)
                    .createErrorBulletin("Считаю размер, секунду…").show();
            return;
        }
        final long[] sizes = cacheSizes;
        long total = 0;
        for (long size : sizes) {
            total += size;
        }
        if (total <= 0) {
            org.telegram.ui.Components.BulletinFactory.of(this)
                    .createSimpleBulletin(R.raw.done, "Кэш уже пуст").show();
            return;
        }
        // One entry per kind plus a clear-everything row, rather than checkboxes: this dialog
        // has no multi-choice variant, and picking one kind at a time is what people actually do.
        final CharSequence[] options = new CharSequence[org.telegram.messenger.PrimeCache.KIND_COUNT + 1];
        for (int i = 0; i < org.telegram.messenger.PrimeCache.KIND_COUNT; i++) {
            options[i] = org.telegram.messenger.PrimeCache.KIND_NAMES[i] + " — " + AndroidUtilities.formatFileSize(sizes[i]);
        }
        options[org.telegram.messenger.PrimeCache.KIND_COUNT] = "Очистить всё — " + AndroidUtilities.formatFileSize(total);

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Кэш медиа");
        builder.setItems(options, (dialog, which) -> {
            final boolean[] kinds = new boolean[org.telegram.messenger.PrimeCache.KIND_COUNT];
            if (which >= org.telegram.messenger.PrimeCache.KIND_COUNT) {
                java.util.Arrays.fill(kinds, true);
            } else {
                if (sizes[which] <= 0) {
                    return;
                }
                kinds[which] = true;
            }
            clearCache(kinds);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void clearCache(boolean[] kinds) {
        if (getParentActivity() == null) {
            return;
        }
        final AlertDialog progress = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(false);
        progress.show();
        org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
            org.telegram.messenger.PrimeCache.clear(kinds);
            final long[] measured = org.telegram.messenger.PrimeCache.measure();
            AndroidUtilities.runOnUIThread(() -> {
                progress.dismiss();
                cacheSizes = measured;
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
                if (getParentActivity() != null) {
                    org.telegram.ui.Components.BulletinFactory.of(this)
                            .createSimpleBulletin(R.raw.done, "Кэш очищен").show();
                }
            });
        });
    }

    private AlertDialog adBlockProgressDialog;

    private void updateAdBlockLists() {
        if (getParentActivity() == null) {
            return;
        }
        if (!org.telegram.messenger.browser.PrimeAdBlockLists.hasAnyListEnabled()) {
            org.telegram.ui.Components.BulletinFactory.of(this)
                    .createErrorBulletin("Сначала выберите хотя бы один список.").show();
            return;
        }
        if (adBlockProgressDialog != null) {
            return;
        }
        adBlockProgressDialog = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        adBlockProgressDialog.setCanCancel(false);
        adBlockProgressDialog.show();
        org.telegram.messenger.browser.PrimeAdBlockLists.update(new org.telegram.messenger.browser.PrimeAdBlockLists.UpdateCallback() {
            @Override
            public void onProgress(String message) {
                if (adBlockProgressDialog != null) {
                    adBlockProgressDialog.setMessage(message);
                }
            }

            @Override
            public void onFinished(boolean ok, String message) {
                if (adBlockProgressDialog != null) {
                    adBlockProgressDialog.dismiss();
                    adBlockProgressDialog = null;
                }
                if (getParentActivity() == null) {
                    return;
                }
                listView.adapter.update(true);
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(ok ? "Списки обновлены" : "Не получилось");
                builder.setMessage(message);
                builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
                showDialog(builder.create());
            }
        });
    }

    /** Built once and reused: it carries the slider's position, so rebuilding it would reset it. */
    private org.telegram.ui.Cells.PrimeAvatarCornersCell avatarCornersCell;

    private org.telegram.ui.Cells.PrimeAvatarCornersCell avatarCornersCell() {
        if (avatarCornersCell == null && getContext() != null) {
            avatarCornersCell = new org.telegram.ui.Cells.PrimeAvatarCornersCell(getContext(), "Форма аватарок", null);
        }
        return avatarCornersCell;
    }

    /** Codes whisper understands, plus an empty one meaning auto-detect. */
    private static final String[] WHISPER_LANGUAGE_CODES = {"", "ru", "en", "uk", "de", "fr", "es"};
    private static final String[] WHISPER_LANGUAGE_NAMES = {
        "Определять сам", "Русский", "English", "Українська", "Deutsch", "Français", "Español"
    };

    private CharSequence whisperLanguageName() {
        final String current = org.telegram.messenger.PrimeWhisper.getLanguage();
        for (int i = 0; i < WHISPER_LANGUAGE_CODES.length; i++) {
            if (WHISPER_LANGUAGE_CODES[i].equals(current)) {
                return WHISPER_LANGUAGE_NAMES[i];
            }
        }
        return WHISPER_LANGUAGE_NAMES[0];
    }

    private void showWhisperLanguagePicker() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Язык записи");
        builder.setItems(WHISPER_LANGUAGE_NAMES, (dialog, which) -> {
            org.telegram.messenger.PrimeWhisper.setLanguage(WHISPER_LANGUAGE_CODES[which]);
            listView.adapter.update(true);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showWhisperModelPicker() {
        if (getParentActivity() == null) {
            return;
        }
        final String[] names = org.telegram.messenger.PrimeWhisper.MODEL_NAMES;
        final CharSequence[] options = new CharSequence[names.length];
        for (int i = 0; i < names.length; i++) {
            options[i] = names[i] + " — " + org.telegram.messenger.PrimeWhisper.MODEL_DESCRIPTIONS[i]
                    + (org.telegram.messenger.PrimeWhisper.isModelDownloaded(i) ? " · загружена" : "");
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Модель распознавания");
        builder.setItems(options, (dialog, which) -> {
            org.telegram.messenger.PrimeWhisper.setModel(which);
            listView.adapter.update(true);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private AlertDialog whisperProgressDialog;

    private void toggleWhisperModel() {
        if (getParentActivity() == null) {
            return;
        }
        final int model = org.telegram.messenger.PrimeWhisper.getModel();
        if (org.telegram.messenger.PrimeWhisper.isModelDownloaded(model)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle("Удалить модель");
            builder.setMessage("Файл модели будет удалён. Распознавание на устройстве перестанет работать, пока вы не скачаете её снова.");
            builder.setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) -> {
                org.telegram.messenger.PrimeWhisper.deleteModel(model);
                listView.adapter.update(true);
            });
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            showDialog(builder.create());
            return;
        }
        if (whisperProgressDialog != null) {
            return;
        }
        whisperProgressDialog = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        whisperProgressDialog.setCanCancel(false);
        whisperProgressDialog.setMessage("Загрузка модели…");
        whisperProgressDialog.show();
        org.telegram.messenger.PrimeWhisper.download(model, new org.telegram.messenger.PrimeWhisper.DownloadCallback() {
            @Override
            public void onProgress(int percent) {
                if (whisperProgressDialog != null) {
                    whisperProgressDialog.setMessage("Загрузка модели… " + percent + "%");
                }
            }

            @Override
            public void onFinished(boolean ok, String message) {
                if (whisperProgressDialog != null) {
                    whisperProgressDialog.dismiss();
                    whisperProgressDialog = null;
                }
                if (getParentActivity() == null) {
                    return;
                }
                if (ok) {
                    org.telegram.ui.Components.BulletinFactory.of(PrimeGramSettingsActivity.this)
                            .createSimpleBulletin(R.raw.done, "Модель загружена").show();
                } else {
                    org.telegram.ui.Components.BulletinFactory.of(PrimeGramSettingsActivity.this)
                            .createErrorBulletin(message == null ? "Ошибка загрузки" : message).show();
                }
                listView.adapter.update(true);
            }
        });
    }

    private static final String[] TRANSLATE_PROVIDER_NAMES = {"Telegram", "Google", "Yandex"};

    /** 0 means no ceiling; the rest are the shorter side in pixels. */
    private static final int[] VIDEO_QUALITY_VALUES = {0, 1080, 720, 480, 360};
    private static final String[] VIDEO_QUALITY_NAMES = {"Максимальное", "До 1080p", "До 720p", "До 480p", "До 360p"};

    private int videoQualityIndex() {
        final int current = org.telegram.messenger.PrimeTweaks.maxVideoHeight();
        for (int i = 0; i < VIDEO_QUALITY_VALUES.length; i++) {
            if (VIDEO_QUALITY_VALUES[i] == current) {
                return i;
            }
        }
        return 0;
    }

    private void showVideoQualityPicker() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Качество видео");
        builder.setItems(VIDEO_QUALITY_NAMES, (dialog, which) -> {
            org.telegram.messenger.PrimeTweaks.setInt(org.telegram.messenger.PrimeTweaks.MAX_VIDEO_HEIGHT,
                    VIDEO_QUALITY_VALUES[which]);
            listView.adapter.update(true);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void togglePreloadVideoOnMobile() {
        final org.telegram.messenger.DownloadController controller =
                org.telegram.messenger.DownloadController.getInstance(currentAccount);
        controller.primeSetMobilePreloadVideo(!controller.primeMobilePreloadVideo());
        listView.adapter.update(true);
    }

    private void showTranslateProviderPicker() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Чем переводить");
        builder.setItems(TRANSLATE_PROVIDER_NAMES, (dialog, which) -> {
            org.telegram.messenger.PrimeTweaks.setInt(org.telegram.messenger.PrimeTweaks.TRANSLATE_PROVIDER, which);
            listView.adapter.update(true);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private static final String[] DOUBLE_TAP_NAMES = {"Реакция", "Ответить", "Ничего"};

    /**
     * The three sticker sizes offered as cards.
     *
     * <p>A slider would give more values and be worse: the number is stored as a text height that
     * means nothing to anyone, and the only question people actually have is small, normal or big.
     */
    private static final int[] STICKER_SIZE_VALUES = {10, org.telegram.messenger.PrimeTweaks.STICKER_SIZE_DEFAULT, 20};
    private static final String[] STICKER_SIZE_NAMES = {"Компактно", "Как в оригинале", "Крупно"};

    private org.telegram.ui.Cells.PrimeShapeOptionsCell stickerSizeCards;
    private org.telegram.ui.Cells.PrimeShapeOptionsCell doubleTapCards;

    private org.telegram.ui.Cells.PrimeShapeOptionsCell translatorCards;
    private org.telegram.ui.Cells.PrimeShapeOptionsCell videoQualityCards;

    private org.telegram.ui.Cells.PrimeShapeOptionsCell translatorCards() {
        if (translatorCards == null && getContext() != null) {
            final int current = Math.max(0, Math.min(TRANSLATE_PROVIDER_NAMES.length - 1,
                    org.telegram.messenger.PrimeTweaks.translateProvider()));
            translatorCards = new org.telegram.ui.Cells.PrimeShapeOptionsCell(getContext(),
                    org.telegram.ui.Cells.PrimeShapeOptionsCell.MODE_TRANSLATOR,
                    TRANSLATE_PROVIDER_NAMES, null, current, null);
            translatorCards.setOnSelected(index ->
                    org.telegram.messenger.PrimeTweaks.setInt(
                            org.telegram.messenger.PrimeTweaks.TRANSLATE_PROVIDER, index));
        }
        return translatorCards;
    }

    private org.telegram.ui.Cells.PrimeShapeOptionsCell videoQualityCards() {
        if (videoQualityCards == null && getContext() != null) {
            videoQualityCards = new org.telegram.ui.Cells.PrimeShapeOptionsCell(getContext(),
                    org.telegram.ui.Cells.PrimeShapeOptionsCell.MODE_VIDEO_QUALITY,
                    VIDEO_QUALITY_NAMES, VIDEO_QUALITY_VALUES, videoQualityIndex(), null);
            videoQualityCards.setOnSelected(index ->
                    org.telegram.messenger.PrimeTweaks.setInt(
                            org.telegram.messenger.PrimeTweaks.MAX_VIDEO_HEIGHT, VIDEO_QUALITY_VALUES[index]));
        }
        return videoQualityCards;
    }

    private org.telegram.ui.Cells.PrimeSliderCell stickerSizeSlider;

    private org.telegram.ui.Cells.PrimeSliderCell stickerSizeSlider() {
        if (stickerSizeSlider == null && getContext() != null) {
            stickerSizeSlider = new org.telegram.ui.Cells.PrimeSliderCell(getContext(),
                    "Точный размер", 8, 24, org.telegram.messenger.PrimeTweaks.stickerSize(), null);
            stickerSizeSlider.setFormatter(value ->
                    value == org.telegram.messenger.PrimeTweaks.STICKER_SIZE_DEFAULT
                            ? "как в оригинале" : String.valueOf(value));
            stickerSizeSlider.setListener((value, stop) -> {
                org.telegram.messenger.PrimeTweaks.setInt(
                        org.telegram.messenger.PrimeTweaks.STICKER_SIZE, value);
                updateLivePreview();
                if (stop && stickerSizeCards != null) {
                    // Keep the cards honest: dragging to a value that is not one of the three
                    // should leave none of them looking chosen.
                    int match = -1;
                    for (int i = 0; i < STICKER_SIZE_VALUES.length; i++) {
                        if (STICKER_SIZE_VALUES[i] == value) {
                            match = i;
                            break;
                        }
                    }
                    if (match >= 0) {
                        stickerSizeCards.select(match, true);
                    }
                }
            });
        }
        return stickerSizeSlider;
    }

    /** The emoji currently set as the double-tap reaction, or a hint when it is a custom one. */
    private CharSequence doubleTapReactionName() {
        try {
            final String reaction = org.telegram.messenger.MediaDataController.getInstance(currentAccount)
                    .getDoubleTapReaction();
            if (android.text.TextUtils.isEmpty(reaction)) {
                return "не выбрана";
            }
            // A custom emoji is stored as its document id, which is a number nobody can read.
            return reaction.startsWith("animated_") ? "своя эмодзи" : reaction;
        } catch (Throwable t) {
            return "";
        }
    }

    private org.telegram.ui.Cells.PrimeShapeOptionsCell stickerSizeCards() {
        if (stickerSizeCards == null && getContext() != null) {
            int selected = 1;
            final int current = org.telegram.messenger.PrimeTweaks.stickerSize();
            for (int i = 0; i < STICKER_SIZE_VALUES.length; i++) {
                if (STICKER_SIZE_VALUES[i] == current) {
                    selected = i;
                    break;
                }
            }
            stickerSizeCards = new org.telegram.ui.Cells.PrimeShapeOptionsCell(getContext(),
                    org.telegram.ui.Cells.PrimeShapeOptionsCell.MODE_STICKER_SIZE,
                    STICKER_SIZE_NAMES, STICKER_SIZE_VALUES, selected, null);
            stickerSizeCards.setOnSelected(index -> {
                org.telegram.messenger.PrimeTweaks.setInt(
                        org.telegram.messenger.PrimeTweaks.STICKER_SIZE, STICKER_SIZE_VALUES[index]);
                // The slider is the same setting seen another way, so it has to follow.
                if (stickerSizeSlider != null) {
                    stickerSizeSlider.setValue(STICKER_SIZE_VALUES[index]);
                }
                updateLivePreview();
            });
        }
        return stickerSizeCards;
    }

    private org.telegram.ui.Cells.PrimeShapeOptionsCell doubleTapCards() {
        if (doubleTapCards == null && getContext() != null) {
            final int current = Math.max(0, Math.min(DOUBLE_TAP_NAMES.length - 1,
                    org.telegram.messenger.PrimeTweaks.doubleTapAction()));
            doubleTapCards = new org.telegram.ui.Cells.PrimeShapeOptionsCell(getContext(),
                    org.telegram.ui.Cells.PrimeShapeOptionsCell.MODE_DOUBLE_TAP,
                    DOUBLE_TAP_NAMES, null, current, null);
            doubleTapCards.setOnSelected(index ->
                    org.telegram.messenger.PrimeTweaks.setInt(
                            org.telegram.messenger.PrimeTweaks.DOUBLE_TAP_ACTION, index));
        }
        return doubleTapCards;
    }

    private void showDoubleTapPicker() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Двойное нажатие по сообщению");
        builder.setItems(DOUBLE_TAP_NAMES, (dialog, which) -> {
            org.telegram.messenger.PrimeTweaks.setInt(org.telegram.messenger.PrimeTweaks.DOUBLE_TAP_ACTION, which);
            listView.adapter.update(true);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showDnsPicker() {
        if (getParentActivity() == null) {
            return;
        }
        final String[] presets = org.telegram.messenger.browser.PrimeDns.PRESET_NAMES;
        final CharSequence[] options = new CharSequence[presets.length + 1];
        System.arraycopy(presets, 0, options, 0, presets.length);
        options[presets.length] = "Свой адрес…";

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("DNS-сервер");
        builder.setItems(options, (dialog, which) -> {
            if (which == presets.length) {
                showCustomDnsDialog();
            } else {
                org.telegram.messenger.browser.PrimeDns.setPreset(which);
                listView.adapter.update(true);
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showCustomDnsDialog() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Свой DNS-сервер");
        builder.setMessage("Адрес DNS-over-HTTPS. Можно указать только имя хоста — «/dns-query» подставится само. Обычный DNS без шифрования не принимается: он свёл бы на нет весь смысл настройки.");

        final EditTextBoldCursor editText = new EditTextBoldCursor(getParentActivity());
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        editText.setHint("dns.example.com");
        editText.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setCursorSize(AndroidUtilities.dp(20));
        editText.setCursorWidth(1.5f);
        editText.setSingleLine(true);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        editText.setBackgroundDrawable(Theme.createEditTextDrawable(getParentActivity(), true));
        editText.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));
        editText.setText(org.telegram.messenger.browser.PrimeDns.getCustomEndpoint());
        editText.setSelection(editText.getText().length());

        LinearLayout container = new LinearLayout(getParentActivity());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);
        container.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        builder.setView(container);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> {
            String value = editText.getText().toString();
            if (org.telegram.messenger.browser.PrimeDns.normalizeEndpoint(value) == null) {
                org.telegram.ui.Components.BulletinFactory.of(this)
                        .createErrorBulletin("Нужен адрес https://").show();
                return;
            }
            org.telegram.messenger.browser.PrimeDns.setCustomEndpoint(value);
            org.telegram.messenger.browser.PrimeDns.setPreset(org.telegram.messenger.browser.PrimeDns.PRESET_CUSTOM);
            listView.adapter.update(true);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
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
        } else if (id == ID_LIMIT_RECENT_STICKERS) {
            messagesController.maxRecentStickersCount = value;
            // Kept in the global settings, not the per-account ones: the trimming happens in
            // MediaDataController for every account, and MessagesController re-reads it there.
            MessagesController.getGlobalMainSettings().edit()
                    .putInt(MessagesController.PRIME_RECENT_STICKERS_KEY, value).apply();
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
