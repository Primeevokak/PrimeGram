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
        entries.add(new Entry("msg_settings", "Плагины теперь могут всё",
                "Расширения на Python, совместимые с exteraGram: пришлите себе файл .plugin и нажмите на него. Плагин умеет менять поведение самого клиента, открывать свои форматы файлов и ловить ссылки — а библиотек внутри больше сорока."));
        entries.add(new Entry("msg_sendfile", "Файлы до 8 ГБ",
                "Файл больше лимита Telegram уходит частями, а PrimeGram на другой стороне собирает его обратно. Включается в настройках медиа."));
        entries.add(new Entry("msg_notifications", "Уведомления приходят сразу",
                "Push-уведомления заработали по-настоящему: сообщение приходит за секунду, даже когда приложение закрыто."));
        entries.add(new Entry("msg_tabs_mic1", "Расшифровка голосовых на устройстве",
                "Без Premium и без интернета. Текст появляется по мере распознавания, модель выбираете сами — от 31 МБ до качества уровня large."));
        entries.add(new Entry("msg_usersearch", "Поиск по ID в обычном поиске",
                "Наберите числовой ID, номер телефона или ссылку t.me — ответ появится под обычными результатами."));
        entries.add(new Entry("msg_select", "Зона активации боковой панели",
                "Панель отзывается на свайп там, где вы сами нарисуете, а не на всей левой трети экрана."));
        entries.add(new Entry("msg_photo_text2", "Настройка панели форматирования",
                "Порядок и состав кнопок над полем ввода теперь перетаскиваются."));
        entries.add(new Entry("msg_info", "Гайд по настройкам",
                "Короткая экскурсия по тому, что здесь вообще настраивается. Внизу экрана настроек PrimeGram."));
        entries.add(new Entry("msg_language", "Документация для авторов плагинов",
                "primeevokak.github.io/PrimeGram — как написать свой, справочник по API, список библиотек и готовые примеры."));
        return entries;
    }
}
