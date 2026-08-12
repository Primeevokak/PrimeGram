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
     * <p>Shown on a fresh install too, at the user's own request - someone who just found the
     * fork should see what it actually offers over stock Telegram, not just people upgrading
     * from a previous version.
     */
    public static boolean shouldShow() {
        final String seen = prefs().getString(KEY_LAST_SEEN, null);
        if (seen == null) {
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
        entries.add(new Entry("msg_theme", "Своё лицо",
                "Приложение называется и выглядит как PrimeGram — своя иконка и имя в списке установленных."));
        entries.add(new Entry("msg_settings_old", "Интерфейс почище",
                "Убрали лишние фантомные зоны в чате и под кнопками каналов."));
        entries.add(new Entry("msg_archive", "Архив и потягивание не спорят",
                "«Скрыть архив» и «Открывать архив потягиванием» больше не прячут друг от друга закреплённые чаты."));
        entries.add(new Entry("msg_speed", "Запускается быстрее",
                "Убрали лишнюю двойную пересборку главного экрана при холодном старте."));
        entries.add(new Entry("proxy_check", "Прокси стабильнее",
                "Меньше разрывов соединения и повторных попыток подключиться."));
        entries.add(new Entry("msg2_battery", "Меньше жрёт батарею и память",
                "Ограничили число фоновых потоков и соединений — на слабых устройствах должно стать заметно легче."));
        entries.add(new Entry("msg_download", "Счётчики грузятся плавно",
                "Загрузка и отправка файлов показывают мегабайты плавно, а не скачками."));
        entries.add(new Entry("msg_pin_code", "Меньше лишней работы под капотом",
                "Приложение реже пересчитывает то, что не менялось, — общая отзывчивость выше."));
        entries.add(new Entry("msg_folders", "Папки теперь и в архиве",
                "Можно раскладывать архивные чаты по своим папкам — так же, как на главном экране, только эти папки отдельные и никак с обычными не связаны."));
        entries.add(new Entry("msg_msgbubble3", "Чат стал плавнее",
                "Прокрутка и переходы в переписке идут заметно ровнее, особенно там, где есть ответы и цитаты."));
        entries.add(new Entry("checkbig", "Заодно почистили и то, что тормозило не по нашей вине",
                "Нашли и поправили несколько мест, где подтормаживал даже оригинальный Telegram."));
        return entries;
    }
}
