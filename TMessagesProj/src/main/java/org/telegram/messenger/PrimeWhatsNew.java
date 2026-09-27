package org.telegram.messenger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * PrimeGram: what changed, shown once after an update.
 *
 * <p>A fork adds things nobody asked for and nobody is told about. The release notes on a GitHub
 * page are read by the handful of people who go looking; everyone else updates and never learns
 * that the thing they wanted has been there for three versions.
 *
 * <p>The notes live here rather than being fetched. A panel that depends on the network is a panel
 * that fails to appear on exactly the launch it was written for - the first one after an update,
 * which on this fork frequently happens on a connection that is being blocked.
 *
 * <p>Shown once per version and never again: the marker is the version it was last shown for, so a
 * user who skips three releases sees the newest notes once, not three panels in a row.
 *
 * <h3>Content</h3>
 * Grouped into {@link Section}s, each with its own badge and accent colour, so a long release still
 * reads at a glance: what is new, what got faster, what got fixed. An {@link Entry} may carry an
 * {@link Entry#action} - a screen the row opens - because "go and look" is the whole point of the
 * panel, and making the reader hunt through settings for it defeats that. Older notes stay
 * available in a collapsed section instead of being deleted, for whoever wants the history.
 */
public final class PrimeWhatsNew {

    private static final String KEY_LAST_SEEN = "primegram_whats_new_version";

    /** Screens an entry can open. Resolved by the sheet, which owns navigation. */
    public static final String ACTION_PLUGINS = "plugins";
    public static final String ACTION_SETTINGS = "settings";
    public static final String ACTION_PROXY = "proxy";

    /** One line in the panel. */
    public static final class Entry {
        public final String icon;
        public final String title;
        public final String text;
        /** Screen to open on tap, or null. */
        public final String action;

        Entry(String icon, String title, String text) {
            this(icon, title, text, null);
        }

        Entry(String icon, String title, String text, String action) {
            this.icon = icon;
            this.title = title;
            this.text = text;
            this.action = action;
        }
    }

    /** A group of entries under one heading and badge. */
    public static final class Section {
        public final String title;
        /** Short label in the pill next to the title, or null for none. */
        public final String badge;
        /** Accent for the badge and the section's icon tiles, ARGB. */
        public final int color;
        /** Starts folded; the heading unfolds it. */
        public final boolean collapsed;
        public final List<Entry> entries;

        Section(String title, String badge, int color, boolean collapsed, Entry... entries) {
            this.title = title;
            this.badge = badge;
            this.color = color;
            this.collapsed = collapsed;
            final List<Entry> list = new ArrayList<>();
            Collections.addAll(list, entries);
            this.entries = list;
        }
    }

    private static final int GREEN = 0xFF2FBF71;
    private static final int VIOLET = 0xFF8B5CF6;
    private static final int BLUE = 0xFF3B8EF0;
    private static final int ORANGE = 0xFFF59E0B;
    private static final int TEAL = 0xFF14B8A6;
    private static final int GRAY = 0xFF8E8E93;

    private PrimeWhatsNew() {
    }

    private static android.content.SharedPreferences prefs() {
        return MessagesController.getGlobalMainSettings();
    }

    public static String currentVersion() {
        return BuildVars.BUILD_VERSION_STRING;
    }

    /**
     * Whether the panel is due.
     *
     * <p>Shown on a fresh install too, at the user's own request - someone who just found the
     * fork should see what it actually offers over stock Telegram, not just people upgrading
     * from a previous version.
     */
    public static boolean shouldShow() {
        final String seen = prefs().getString(KEY_LAST_SEEN, null);
        if (seen == null) {
            return !sections().isEmpty();
        }
        return !seen.equals(currentVersion()) && !sections().isEmpty();
    }

    public static void markSeen() {
        prefs().edit().putString(KEY_LAST_SEEN, currentVersion()).apply();
    }

    /**
     * What this version brought, newest first, then the history.
     *
     * <p>Written from the user's side: what they can now do, not what was refactored. Anything
     * that cannot be described that way does not belong in this list - a person reading it is
     * deciding whether to go and look for something, not reviewing a changelog.
     */
    public static List<Section> sections() {
        final List<Section> sections = new ArrayList<>();
        sections.add(new Section("Плагины", "НОВОЕ", GREEN, false,
                new Entry("msg_bots", "Плагины exteraGram работают как родные",
                        "Прогнали сотни настоящих плагинов из каталогов и закрыли всё, чего им не хватало: классы и настройки exteraGram, ползунки в настройках, экраны плагинов и их кнопки.",
                        ACTION_PLUGINS),
                new Entry("msg_download", "Установка из магазинов плагинов",
                        "Плагины-каталоги и менеджеры теперь могут ставить другие плагины — через обычное окно установки с проверкой, к чему плагин получает доступ."),
                new Entry("msg_secret", "Проверка перед установкой",
                        "Окно установки показывает, что плагин умеет — сеть, сообщения, файлы, перехват функций — и предупреждает о запутанном коде.")));
        sections.add(new Section("Скорость", "БЫСТРЕЕ", VIOLET, false,
                new Entry("msg_premium_speed", "Плавнее листается чат",
                        "Сообщения больше не перерисовываются целиком без причины: ни пока грузятся фото и стикеры, ни при прокрутке. Кружок загрузки и переливающаяся заглушка теперь рисуются отдельно от сообщения."),
                new Entry("msg_speed", "Быстрее при потоке сообщений",
                        "Когда приходит много сообщений сразу, список чатов пересобирается один раз, а не по разу на каждый чат."),
                new Entry("msg2_battery", "Бережнее к памяти",
                        "На устройствах с малым объёмом памяти приложение освобождает кэш заранее и держит базу компактнее — реже выгружается из памяти в фоне."),
                new Entry("msg_stats", "Мониторинг нагрузки",
                        "Встроенный профилировщик показывает, чем занят интерфейс и что именно перерисовывается — лог можно скопировать и прислать нам.",
                        ACTION_SETTINGS)));
        sections.add(new Section("Сеть и перевод", "УЛУЧШЕНО", BLUE, false,
                new Entry("msg_translate", "Google-перевод не пропадает",
                        "Перевод длинных постов не обрезается, при ограничениях Google — повтор и запасной путь через встроенный прокси, уже переведённое больше не запрашивается повторно."),
                new Entry("proxy_check", "TgWs обновлён",
                        "Новый способ обхода блокировок из свежей версии tg-ws-proxy. По умолчанию прокси работает, только пока открыто приложение: уведомления приходят через push.",
                        ACTION_PROXY)));
        sections.add(new Section("Telegram 12.10.4", "БАЗА", TEAL, false,
                new Entry("msg_home", "Обновились до свежего Telegram",
                        "Всё новое из официального клиента на месте, а функции PrimeGram сохранены. Иконка приложения перерисована в векторе — чёткая на любом экране.")));
        sections.add(new Section("Исправлено", "ФИКС", ORANGE, false,
                new Entry("msg_openprofile", "Больше не разлогинивает",
                        "Аккаунт не сбрасывается при восстановлении базы и при быстром переключении между аккаунтами после запуска."),
                new Entry("msg_channel", "Старые посты не вклиниваются в ленту",
                        "Если в кэше оказался пост не на своём месте, он удаляется и перезагружается с сервера.")));
        sections.add(new Section("Ранее", null, GRAY, true,
                new Entry("proxy_check", "Прокси стал заметно стабильнее",
                        "Переработали внутреннюю логику прокси-соединений: меньше разрывов, быстрее и незаметнее переподключение, увереннее грузятся фото и видео."),
                new Entry("msg_pin_code", "PIN-код при запуске",
                        "Отдельный код-пароль на вход в приложение — с выбором, когда его спрашивать, и аварийным PIN на крайний случай. Настройки → Приватность."),
                new Entry("msg_block", "Аварийный режим",
                        "Ввод аварийного PIN вместо основного стирает данные на устройстве и показывает правдоподобную имитацию обычного клиента — вместо экрана входа, который бы выдал ситуацию."),
                new Entry("msg_delete", "Автоудаление своих сообщений",
                        "Свои сообщения в обычных чатах можно удалять автоматически через заданное время, плюс мгновенная очистка всей своей истории в конкретном чате."),
                new Entry("msg_secret", "Невидимость для незнакомцев",
                        "Статус прочтения не уходит тем, кто пишет вам впервые, — пока вы сами не ответите."),
                new Entry("msg_mask", "Обезличивание файлов",
                        "Скачанные и отправляемые файлы можно лишить оригинального имени и метаданных съёмки — даты, модели камеры, координат GPS."),
                new Entry("msg_notifications", "Скрытие текста в уведомлениях",
                        "Push можно настроить так, чтобы показывалось только имя отправителя — без текста сообщения, медиа и подписей кнопок.")));
        return sections;
    }

    /** Every entry, flattened - for anything that only needs the lines. */
    public static List<Entry> entries() {
        final List<Entry> all = new ArrayList<>();
        for (Section section : sections()) {
            all.addAll(section.entries);
        }
        return all;
    }
}
