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
        entries.add(new Entry("proxy_check", "Прокси стал заметно стабильнее",
                "Переработали внутреннюю логику прокси-соединений: меньше разрывов, быстрее и незаметнее переподключение, увереннее грузятся фото и видео."));
        entries.add(new Entry("msg_pin_code", "PIN-код при запуске",
                "Отдельный код-пароль на вход в приложение — с выбором, когда его спрашивать, и аварийным PIN на крайний случай. Настройки → Приватность."));
        entries.add(new Entry("msg_block", "Аварийный режим",
                "Ввод аварийного PIN вместо основного стирает данные на устройстве и показывает правдоподобную имитацию обычного клиента — вместо экрана входа, который бы выдал ситуацию."));
        entries.add(new Entry("msg_delete", "Автоудаление своих сообщений",
                "Свои сообщения в обычных чатах можно удалять автоматически через заданное время, плюс мгновенная очистка всей своей истории в конкретном чате."));
        entries.add(new Entry("msg_secret", "Невидимость для незнакомцев",
                "Статус прочтения не уходит тем, кто пишет вам впервые, — пока вы сами не ответите."));
        entries.add(new Entry("msg_secret", "Обезличивание файлов",
                "Скачанные и отправляемые файлы можно лишить оригинального имени и метаданных съёмки — даты, модели камеры, координат GPS."));
        entries.add(new Entry("msg_notifications", "Скрытие текста в уведомлениях",
                "Push можно настроить так, чтобы показывалось только имя отправителя — без текста сообщения, медиа и подписей кнопок."));
        return entries;
    }
}
