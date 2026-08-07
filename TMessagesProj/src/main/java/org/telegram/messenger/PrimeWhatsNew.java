package org.telegram.messenger;

import java.util.ArrayList;
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
 */
public final class PrimeWhatsNew {

    private static final String KEY_LAST_SEEN = "primegram_whats_new_version";

    /** One line in the panel. */
    public static final class Entry {
        public final String icon;
        public final String title;
        public final String text;

        Entry(String icon, String title, String text) {
            this.icon = icon;
            this.title = title;
            this.text = text;
        }
    }

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
     * <p>A fresh install is not an update, and someone who has just found the app has enough to
     * look at without a list of things that changed since a version they never ran. They are
     * marked as having seen the current notes instead, so the first panel they get is the next
     * real one.
     */
    public static boolean shouldShow() {
        final String seen = prefs().getString(KEY_LAST_SEEN, null);
        if (seen == null) {
            final boolean firstEverLaunch = prefs().getInt("primegram_app_launch_count", 0) <= 1;
            if (firstEverLaunch) {
                markSeen();
                return false;
            }
            return !entries().isEmpty();
        }
        return !seen.equals(currentVersion()) && !entries().isEmpty();
    }

    public static void markSeen() {
        prefs().edit().putString(KEY_LAST_SEEN, currentVersion()).apply();
    }

    /**
     * What this version brought.
     *
     * <p>Written from the user's side: what they can now do, not what was refactored. Anything
     * that cannot be described that way does not belong in this list - a person reading it is
     * deciding whether to go and look for something, not reviewing a changelog.
     */
    public static List<Entry> entries() {
        final List<Entry> entries = new ArrayList<>();
        entries.add(new Entry("msg_translate", "Перевод изображений",
                "Долгое нажатие на фото — текст на картинке распознаётся и переводится прямо поверх оригинала, без Premium."));
        entries.add(new Entry("msg_language", "Перевод перед отправкой",
                "Для каждого чата свой язык: сообщение переводится автоматически перед отправкой, ещё до того как собеседник его увидит."));
        entries.add(new Entry("msg_search", "Поиск по картинке",
                "Прямо из просмотра фото — Yandex, Google, Bing или TinEye, без сохранения и пересылки куда-то ещё."));
        entries.add(new Entry("msg_secret", "Пересылка без подписи автора",
                "При пересылке можно скрыть \"Переслано от\" — стандартный флаг Telegram, до которого просто не доходили руки."));
        entries.add(new Entry("msg_settings_old", "Классическая боковая панель",
                "Кто соскучился по выезжающему меню слева — оно вернулось, вместе с плоским \"неостровным\" стилем чата и списка каналов."));
        entries.add(new Entry("list_reorder", "Гибкие нижние вкладки",
                "Компактный режим или полное скрытие — на выбор, вместо одного фиксированного вида панели снизу."));
        entries.add(new Entry("msg_settings", "Плагины теперь и в боковых панелях",
                "Раздел плагинов открывается прямо из бокового меню, а не только из настроек — в любом варианте панели."));
        entries.add(new Entry("msg_info", "Настройки PrimeGram — сразу наверху",
                "О приложении и настройки PrimeGram больше не нужно искать в общем списке — они первым делом после профиля."));
        entries.add(new Entry("msg_stats", "Свои бейджи и иконки",
                "Кастомные значки рядом с именем и собственные наборы иконок интерфейса — устанавливаются прямо в приложении. Тап по значку показывает, за что он выдан."));
        entries.add(new Entry("outline_shield_check", "Прокси и VPN больше не мешают друг другу",
                "Свой прокси теперь можно отключать автоматически, пока включён сторонний VPN — со списком исключений для тех VPN, которые трогать не нужно."));
        entries.add(new Entry("msg_online", "Видно, кто сейчас активен в общих группах",
                "Если собеседник скрыл от вас \"был(а) в сети\", но пишет в группе, где вы оба состоите, — это будет видно в его профиле. В Серой зоне, по желанию."));
        entries.add(new Entry("msg_fave", "Теги в Избранном работают без Premium",
                "Помечайте сообщения тегами и ищите по ним даже без подписки — хранятся и ищутся прямо на устройстве."));
        entries.add(new Entry("msg_camera", "Широкоугольная камера в кружочках",
                "Кнопка 0.5x/1x при записи видеосообщений — на устройствах, где для этого есть второй объектив."));
        entries.add(new Entry("msg_settings", "PillStack для плагинов",
                "Площадка над списком чатов, куда плагины могут добавлять свои виджеты-таблетки — счётчики, статусы, что угодно."));
        entries.add(new Entry("msg_language", "Лучше совместимость с плагинами экстеры",
                "Плагины, написанные для настоящей exteraGram, реже падают с ошибками совместимости — добавлена поддержка их внутренних библиотек."));
        entries.add(new Entry("msg_search", "Поиск по всем пользователям в упоминаниях",
                "Набирая @имя в чате, теперь ищутся все пользователи Telegram, а не только те, кто уже писал в этом чате."));
        entries.add(new Entry("msg_online", "Серая зона: исключения по чату и расписание",
                "Для конкретного чата можно выставить «всегда»/«никогда» отдельно для прочтения и «печатает». Режим призрака можно ограничить временем суток, а истории теперь тоже можно смотреть незаметно."));
        entries.add(new Entry("msg_channel", "Лента: настоящие реакции и комментарии",
                "Тап по реакции или комментарию в ленте теперь делает реальное действие, а не просто открывает исходный чат. Плюс подгрузка старых постов при прокрутке и лента запоминает, где вы остановились."));
        entries.add(new Entry("msg_settings", "Новый экран установки плагинов",
                "Установка и обновление плагина теперь показывают версию, изменения и статус загрузки на отдельном экране, а не просто диалогом с текстом."));
        entries.add(new Entry("outline_header_search", "Поиск по плагинам",
                "В разделе «Плагины» появился поиск по названию — полезно, если их накопилось много."));
        entries.add(new Entry("msg_language", "Перевод чата — всегда под рукой",
                "Кнопка перевода в меню «⋮» теперь видна всегда, а не только когда Telegram сам решит, что язык подходящий."));
        entries.add(new Entry("msg_translate", "Перевод стал быстрее и честнее",
                "Несколько сообщений переводятся параллельно, а не по одному. Добавлен режим Multiplay — Google и Yandex делят нагрузку пополам. Если перевод не удался, теперь об этом сообщат, а не промолчат."));
        entries.add(new Entry("msg_stats", "Наборы иконок ставятся тапом",
                "Файл .icons в чате предлагает установку сразу, как плагин — без похода в настройки."));
        entries.add(new Entry("outline_shield_check", "Свои серверы — списком",
                "vless, vmess, trojan, shadowsocks, socks — добавляйте несколько серверов, переключайтесь между ними и проверяйте одной кнопкой, какие ещё живы."));
        entries.add(new Entry("msg2_battery", "Диагностика энергопотребления",
                "Собирает состояние батареи и фоновых процессов приложения в файл — и сразу предлагает отправить его в чат."));
        entries.add(new Entry("msg_channel", "Лента — большое обновление",
                "Посты теперь настоящие сообщения из живого чата, а не имитация: полноценные реакции, рабочие комментарии, аватарки авторов, подгрузка старых постов при прокрутке. Лента запоминает, где вы остановились, стартует с самого старого непрочитанного, а не с низа, и больше не дёргает обратно при скролле вверх."));
        return entries;
    }
}
