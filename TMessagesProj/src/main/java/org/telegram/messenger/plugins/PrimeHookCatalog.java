package org.telegram.messenger.plugins;

import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PrimeGram: what the parts of Telegram a plugin reaches into actually are, in words.
 *
 * <p>"Встраиваться в работу приложения (перехват методов)" says nothing a user can weigh. This
 * catalogue turns a class or method into "Окно чата - отправка сообщений" and how much it can see:
 * {@link Sensitivity#UI} only changes how things look, {@link Sensitivity#CONTENT} sees or changes
 * messages and personal data, {@link Sensitivity#CORE} sits on all traffic or the account itself.
 *
 * <p>Two users: the install sheet (classes the code mentions, before anything runs) and the
 * plugin's settings screen (the exact methods hooked, recorded by {@link #record} as they go in).
 */
public final class PrimeHookCatalog {

    public enum Sensitivity {
        UI("интерфейс"),
        CONTENT("переписка и данные"),
        CORE("весь трафик и аккаунт");

        public final String title;

        Sensitivity(String title) {
            this.title = title;
        }
    }

    public static final class Entry {
        public final String className;
        public final String title;
        public final Sensitivity sensitivity;
        final Map<String, String> methods = new HashMap<>();

        Entry(String className, String title, Sensitivity sensitivity) {
            this.className = className;
            this.title = title;
            this.sensitivity = sensitivity;
        }

        /** What one method of this class is for, or null if the catalogue does not say. */
        public String method(String name) {
            return methods.get(name);
        }
    }

    private static final Map<String, Entry> BY_SIMPLE_NAME = new HashMap<>();

    private static Entry add(String className, String title, Sensitivity sensitivity, String... methods) {
        final Entry entry = new Entry(className, title, sensitivity);
        for (int i = 0; i + 1 < methods.length; i += 2) {
            entry.methods.put(methods[i], methods[i + 1]);
        }
        BY_SIMPLE_NAME.put(simpleName(className), entry);
        return entry;
    }

    static {
        final Sensitivity UI = Sensitivity.UI, CONTENT = Sensitivity.CONTENT, CORE = Sensitivity.CORE;

        // ── Network, session, account ─────────────────────────────────────────────────────────
        add("org.telegram.tgnet.ConnectionsManager", "Соединение с серверами Telegram — все запросы и ответы", CORE,
                "sendRequest", "отправка любого запроса к Telegram",
                "sendRequestInternal", "отправка любого запроса к Telegram",
                "onUnparsedMessageReceived", "приём сырых обновлений с сервера",
                "setProxySettings", "настройки прокси",
                "getCurrentTime", "время сервера");
        add("org.telegram.tgnet.NativeByteBuffer", "Сырые данные протокола MTProto", CORE);
        add("org.telegram.tgnet.TLRPC", "Объекты протокола Telegram (сообщения, пользователи, запросы)", CONTENT);
        add("org.telegram.messenger.UserConfig", "Данные текущего аккаунта (id, телефон, настройки сессии)", CORE,
                "getCurrentUser", "текущий пользователь",
                "getClientUserId", "id аккаунта",
                "saveConfig", "сохранение данных аккаунта");
        add("org.telegram.messenger.AccountInstance", "Доступ ко всем подсистемам аккаунта", CORE);
        add("org.telegram.ui.LoginActivity", "Экран входа и номер телефона", CORE);
        add("org.telegram.messenger.SharedConfig", "Общие настройки приложения (код-пароль, прокси)", CORE);
        add("org.telegram.messenger.BillingController", "Покупки внутри приложения", CORE);
        add("org.telegram.ui.Stars.StarsController", "Звёзды: баланс, транзакции", CONTENT);

        // ── Messages and data ─────────────────────────────────────────────────────────────────
        add("org.telegram.messenger.MessagesController", "Центр данных: чаты, сообщения, обновления", CORE,
                "processUpdates", "обработка всех входящих обновлений",
                "processUpdateArray", "обработка всех входящих обновлений",
                "deleteMessages", "удаление сообщений",
                "deleteDialog", "удаление чатов",
                "markDialogAsRead", "отметка прочитанным",
                "loadMessages", "загрузка истории",
                "loadDialogs", "загрузка списка чатов",
                "getUser", "данные пользователя",
                "getChat", "данные чата",
                "sortDialogs", "сортировка списка чатов",
                "isChatNoForwards", "запрет пересылки в чате",
                "isUserPremiumBlocked", "ограничения Premium",
                "getUserFull", "профиль пользователя",
                "processLoadedMessages", "загруженные сообщения");
        add("org.telegram.messenger.SendMessagesHelper", "Отправка сообщений и файлов", CORE,
                "sendMessage", "отправка сообщения",
                "prepareSendingMedia", "отправка медиа",
                "prepareSendingText", "отправка текста",
                "sendSticker", "отправка стикера",
                "processForwardFromMyName", "пересылка");
        add("org.telegram.messenger.MessagesStorage", "Локальная база: вся переписка на устройстве", CORE,
                "putMessages", "запись сообщений",
                "getMessages", "чтение истории из базы",
                "putDialogs", "запись списка чатов");
        add("org.telegram.messenger.MessageObject", "Сообщение: текст, медиа, отправитель", CONTENT,
                "generateLayout", "раскладка текста сообщения",
                "isOut", "своё / чужое",
                "getDialogId", "чат сообщения",
                "isSponsored", "рекламное сообщение",
                "canForwardMessage", "можно ли переслать",
                "needDrawShareButton", "кнопка «поделиться»");
        add("org.telegram.messenger.NotificationsController", "Уведомления о сообщениях (тексты уведомлений)", CONTENT,
                "processNewMessages", "новые сообщения в уведомлениях",
                "showOrUpdateNotification", "показ уведомления");
        add("org.telegram.messenger.MediaDataController", "Стикеры, эмодзи, черновики, поиск", CONTENT,
                "saveDraft", "черновики сообщений",
                "loadStickers", "загрузка стикеров");
        add("org.telegram.messenger.ContactsController", "Контакты аккаунта", CONTENT,
                "loadContacts", "загрузка контактов");
        add("org.telegram.messenger.FileLoader", "Загрузка файлов и медиа", CONTENT,
                "loadFile", "скачивание файла");
        add("org.telegram.messenger.FileUploadOperation", "Выгрузка файлов", CONTENT);
        add("org.telegram.messenger.ImageLoader", "Загрузка и кеш изображений", UI);
        add("org.telegram.messenger.MediaController", "Плеер музыки и голосовых, запись", CONTENT,
                "playMessage", "воспроизведение",
                "startRecording", "запись голосового");
        add("org.telegram.messenger.LocationController", "Трансляция геопозиции", CONTENT);
        add("org.telegram.messenger.SecretChatHelper", "Секретные чаты", CORE);
        add("org.telegram.messenger.DownloadController", "Автозагрузка медиа", UI);
        add("org.telegram.messenger.TranslateController", "Перевод сообщений", CONTENT);
        add("org.telegram.messenger.ChatObject", "Права и свойства чатов", CONTENT);
        add("org.telegram.messenger.UserObject", "Свойства пользователей (имя, статус)", CONTENT);
        add("org.telegram.messenger.DialogObject", "Свойства диалогов", CONTENT);
        add("org.telegram.messenger.NotificationCenter", "Внутренние события приложения", CONTENT,
                "postNotificationName", "рассылка внутренних событий");

        // ── Screens ───────────────────────────────────────────────────────────────────────────
        add("org.telegram.ui.ChatActivity", "Окно чата", CONTENT,
                "createView", "построение окна чата",
                "onFragmentCreate", "открытие чата",
                "onFragmentDestroy", "закрытие чата",
                "didReceivedNotification", "события в открытом чате",
                "createMenu", "меню чата",
                "fillMessageMenu", "меню долгого нажатия на сообщение",
                "processSelectedOption", "действия над сообщением",
                "onResume", "возврат в чат",
                "updateTitle", "заголовок чата",
                "sendMessage", "отправка из окна чата");
        add("org.telegram.ui.DialogsActivity", "Список чатов", UI,
                "createView", "построение списка чатов",
                "onFragmentCreate", "открытие списка",
                "didReceivedNotification", "обновления списка",
                "updateFilterTabs", "вкладки папок",
                "onResume", "возврат к списку");
        add("org.telegram.ui.ProfileActivity", "Профиль пользователя или чата", CONTENT,
                "createView", "построение профиля",
                "updateRowsIds", "строки профиля",
                "onItemClick", "нажатия в профиле");
        add("org.telegram.ui.LaunchActivity", "Главное окно приложения и переходы", UI,
                "onCreate", "запуск приложения",
                "handleIntent", "открытие ссылок и файлов",
                "onResume", "возврат в приложение");
        add("org.telegram.ui.SettingsActivity", "Экран настроек", UI);
        add("org.telegram.ui.MainTabsActivity", "Нижние вкладки главного экрана", UI);
        add("org.telegram.ui.PhotoViewer", "Просмотр фото и видео", CONTENT);
        add("org.telegram.ui.ArticleViewer", "Просмотр статей (Instant View)", UI);
        add("org.telegram.ui.ChatEditActivity", "Редактирование чата", CONTENT);
        add("org.telegram.ui.ChatUsersActivity", "Участники чата", CONTENT);
        add("org.telegram.ui.ThemeActivity", "Настройки оформления", UI);
        add("org.telegram.ui.PrivacySettingsActivity", "Настройки конфиденциальности", CONTENT);
        add("org.telegram.ui.PasscodeActivity", "Код-пароль", CORE);
        add("org.telegram.ui.TopicsFragment", "Темы в форумах", UI);
        add("org.telegram.ui.StoryViewer", "Просмотр историй", CONTENT);
        add("org.telegram.ui.Stories.StoriesController", "Истории", CONTENT);
        add("org.telegram.ui.Stories.recorder.StoryRecorder", "Создание историй", UI);
        add("org.telegram.ui.CameraScene", "Камера", CONTENT);
        add("org.telegram.ui.Components.InstantCameraView", "Видеосообщения (кружки)", CONTENT);
        add("org.telegram.ui.ActionBar.BaseFragment", "Основа всех экранов", UI);
        add("org.telegram.ui.ActionBar.ActionBarLayout", "Навигация между экранами", UI,
                "presentFragment", "открытие экранов");
        add("org.telegram.ui.ActionBar.ActionBar", "Верхняя панель экранов", UI);
        add("org.telegram.ui.ActionBar.Theme", "Цвета и темы", UI,
                "getColor", "цвета интерфейса");
        add("org.telegram.ui.ActionBar.AlertDialog", "Диалоговые окна", UI);
        add("org.telegram.ui.ActionBar.BottomSheet", "Нижние шторки", UI);
        add("org.telegram.ui.ActionBar.DrawerLayoutContainer", "Боковое меню", UI);

        // ── Chat components ───────────────────────────────────────────────────────────────────
        add("org.telegram.ui.Components.ChatActivityEnterView", "Поле ввода сообщения", CONTENT,
                "sendMessage", "отправка из поля ввода",
                "setFieldText", "текст в поле ввода",
                "onSendLongClick", "долгое нажатие «отправить»",
                "setEmojiButtonImage", "кнопка эмодзи",
                "createEmojiView", "панель эмодзи");
        add("org.telegram.ui.Cells.ChatMessageCell", "Пузырь сообщения в чате", CONTENT,
                "setMessageObject", "наполнение пузыря сообщением",
                "onDraw", "отрисовка сообщения",
                "drawTime", "время у сообщения");
        add("org.telegram.ui.Cells.ChatActionCell", "Служебные сообщения в чате", UI);
        add("org.telegram.ui.Cells.DialogCell", "Строка чата в списке", CONTENT,
                "buildLayout", "текст строки (превью сообщения)",
                "onDraw", "отрисовка строки");
        add("org.telegram.ui.Cells.ProfileSearchCell", "Строка в поиске", UI);
        add("org.telegram.ui.Cells.TextSettingsCell", "Строка настроек", UI);
        add("org.telegram.ui.Cells.DrawerProfileCell", "Шапка бокового меню", UI);
        add("org.telegram.ui.Components.EmojiView", "Панель эмодзи, стикеров и GIF", UI,
                "setAllow", "доступные вкладки",
                "onOpen", "открытие панели",
                "saveNewPage", "переключение вкладок");
        add("org.telegram.ui.Components.ChatAttachAlert", "Меню вложений (скрепка)", UI);
        add("org.telegram.ui.Components.ShareAlert", "Окно «Поделиться»", CONTENT);
        add("org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble", "Реакции под сообщением", UI);
        add("org.telegram.ui.Components.ReactionsContainerLayout", "Выбор реакции", UI);
        add("org.telegram.ui.Components.FilterTabsView", "Вкладки папок", UI);
        add("org.telegram.ui.Components.BlurredRecyclerView", "Список с размытием", UI);
        add("org.telegram.ui.Components.RecyclerListView", "Списки интерфейса", UI);
        add("org.telegram.ui.Components.UItem", "Строки списков настроек", UI);
        add("org.telegram.ui.Components.UniversalAdapter", "Списки настроек", UI);
        add("org.telegram.ui.Components.BulletinFactory", "Всплывающие подсказки", UI);
        add("org.telegram.ui.Components.Bulletin", "Всплывающие подсказки", UI);
        add("org.telegram.ui.Components.SnowflakesEffect", "Эффект снега", UI);
        add("org.telegram.ui.Components.AnimatedEmojiDrawable", "Анимированные эмодзи", UI);
        add("org.telegram.ui.Components.BackupImageView", "Картинки и аватары", UI);
        add("org.telegram.ui.Components.AvatarDrawable", "Аватары-заглушки", UI);
        add("org.telegram.ui.Components.SeekBarView", "Ползунки", UI);
        add("org.telegram.ui.Components.Paint.ColorPickerBottomSheet", "Выбор цвета", UI);
        add("org.telegram.ui.Components.ColorPicker", "Выбор цвета", UI);
        add("org.telegram.ui.Components.voip.VoIPHelper", "Звонки", CONTENT);
        add("org.telegram.messenger.voip.VoIPService", "Звонки (аудио и видео)", CONTENT);
        add("org.telegram.ui.Components.PasscodeView", "Экран код-пароля", CORE);
        add("org.telegram.messenger.LocaleController", "Язык и переводы интерфейса", UI);
        add("org.telegram.messenger.AndroidUtilities", "Служебные функции интерфейса", UI);
        add("org.telegram.messenger.ApplicationLoader", "Запуск приложения", UI);
        add("org.telegram.messenger.browser.Browser", "Открытие ссылок", UI,
                "openUrl", "открытие ссылки");
        add("org.telegram.messenger.MessagesController$DialogFilter", "Папки чатов", UI);
        add("org.telegram.messenger.Emoji", "Эмодзи", UI);
        add("org.telegram.messenger.LiteMode", "Режим энергосбережения", UI);
        add("org.telegram.messenger.FileLog", "Журнал приложения", CONTENT);
        add("androidx.viewpager.widget.ViewPager", "Пролистываемые страницы", UI,
                "dispatchOnPageSelected", "переключение страниц");
        add("android.app.Activity", "Окно Android", UI);
        add("android.view.View", "Любой элемент интерфейса", UI);
        add("android.content.ClipboardManager", "Буфер обмена", CONTENT,
                "getPrimaryClip", "чтение буфера обмена");
    }

    /** By simple class name - what a plugin's code mentions most of the time. */
    public static Entry find(String simpleOrQualifiedName) {
        if (simpleOrQualifiedName == null) {
            return null;
        }
        return BY_SIMPLE_NAME.get(simpleName(simpleOrQualifiedName));
    }

    private static final Set<String> INFRASTRUCTURE = new java.util.HashSet<>(java.util.Arrays.asList(
            "View", "Activity", "ApplicationLoader", "AndroidUtilities", "LocaleController", "Theme", "UItem",
            "UniversalAdapter", "ActionBar", "BackupImageView", "AvatarDrawable", "AlertDialog", "BottomSheet",
            "BaseFragment", "RecyclerListView", "Bulletin", "BulletinFactory", "TLRPC", "NotificationCenter",
            "LiteMode", "Emoji", "FileLog", "ImageLoader", "SeekBarView"));

    /**
     * Building blocks nearly every plugin names to draw anything at all - true but not worth a line
     * on the install sheet. Still listed when actually hooked: then the hook is what matters.
     */
    public static boolean isInfrastructure(String simpleName) {
        return INFRASTRUCTURE.contains(simpleName);
    }

    /** Simple names the catalogue knows, for matching a plugin's code against. */
    static Set<String> knownSimpleNames() {
        return BY_SIMPLE_NAME.keySet();
    }

    static String simpleName(String className) {
        int cut = Math.max(className.lastIndexOf('.'), className.lastIndexOf('/'));
        String name = cut >= 0 ? className.substring(cut + 1) : className;
        if (name.endsWith(";")) {
            name = name.substring(0, name.length() - 1);
        }
        // Inner classes by their outer class: SnowflakesEffect$Particle is the snow effect.
        final int dollar = name.indexOf('$');
        if (dollar > 0 && !BY_SIMPLE_NAME.containsKey(name)) {
            name = name.substring(0, dollar);
        }
        return name;
    }

    // ── Hooks actually in place, per plugin ─────────────────────────────────────────────────

    /** One method a plugin hooked: the catalogue's words, and the real name for the curious. */
    public static final class Hooked {
        public final String className;
        public final String methodName;
        public final Entry entry;

        Hooked(String className, String methodName) {
            this.className = className;
            this.methodName = methodName;
            this.entry = find(className);
        }

        public String title() {
            final String method = entry != null ? entry.method(methodName) : null;
            if (entry != null && method != null) {
                return entry.title + " — " + method;
            }
            if (entry != null) {
                return entry.title;
            }
            return simpleName(className);
        }

        public String technicalName() {
            return simpleName(className) + "." + methodName;
        }

        public Sensitivity sensitivity() {
            return entry != null ? entry.sensitivity : Sensitivity.UI;
        }
    }

    private static final Map<String, Set<String>> ACTIVE = new ConcurrentHashMap<>();

    /** Called from the plugin runtime as each hook goes in. */
    public static void record(String pluginId, Member member) {
        if (pluginId == null || member == null) {
            return;
        }
        final String name = member instanceof Constructor ? "<init>" : member.getName();
        ACTIVE.computeIfAbsent(pluginId, k -> Collections.synchronizedSet(new LinkedHashSet<>()))
                .add(member.getDeclaringClass().getName() + "#" + name);
    }

    public static void forget(String pluginId) {
        if (pluginId != null) {
            ACTIVE.remove(pluginId);
        }
    }

    /** The hooks a running plugin holds, most sensitive first. */
    public static List<Hooked> hooksOf(String pluginId) {
        final Set<String> set = pluginId == null ? null : ACTIVE.get(pluginId);
        final List<Hooked> result = new ArrayList<>();
        if (set == null) {
            return result;
        }
        synchronized (set) {
            for (String key : set) {
                final int hash = key.indexOf('#');
                result.add(new Hooked(key.substring(0, hash), key.substring(hash + 1)));
            }
        }
        Collections.sort(result, (a, b) -> b.sensitivity().ordinal() - a.sensitivity().ordinal());
        return result;
    }

    private PrimeHookCatalog() {
    }
}
