package org.telegram.ui;

import android.view.View;

import org.telegram.messenger.DeletedMessagesStore;
import org.telegram.messenger.GreyZone;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/**
 * Opt-in features that override protections the *other* party chose, or hide the user's
 * own activity from their contacts. Everything is off until the warning is accepted.
 */
public class GreyZoneActivity extends UniversalFragment {

    private static final int ID_ACCEPT = 1;
    private static final int ID_REVOKE = 2;
    private static final int ID_SCREENSHOTS = 10;
    private static final int ID_NOFORWARDS = 11;
    private static final int ID_GHOST_READ = 20;
    private static final int ID_GHOST_TYPING = 21;
    private static final int ID_GHOST_ONLINE = 22;
    private static final int ID_SAVE_DELETED = 30;
    private static final int ID_OPEN_DELETED = 31;
    private static final int ID_ACTIVITY_PEEK = 40;
    private static final int ID_LOCAL_PREMIUM = 50;
    private static final int ID_GHOST_STORIES = 60;
    private static final int ID_GHOST_IMMEDIATE_OFFLINE = 61;
    private static final int ID_GHOST_SCHEDULE = 62;
    private static final int ID_GHOST_SCHEDULE_RANGE = 63;
    private static final int ID_GHOST_READ_STRANGERS = 64;

    @Override
    protected CharSequence getTitle() {
        return "Серая зона";
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (!GreyZone.isAccepted()) {
            items.add(UItem.asShadow("Здесь собраны функции, которые снимают ограничения, выбранные вашим собеседником, или скрывают вашу активность от других.\n\n" +
                    "Разработчик PrimeGram не одобряет их использование и не несёт ответственности за последствия. Вы включаете их на свой страх и риск.\n\n" +
                    "Что важно понимать:\n" +
                    "• Одноразовые фото, секретные чаты и запрет пересылки — это выбор второй стороны, а не техническое ограничение вашего клиента.\n" +
                    "• Использование этих функций нарушает условия Telegram и может привести к блокировке аккаунта.\n" +
                    "• Скрытие прочтения и статуса «онлайн» — это ввод собеседников в заблуждение."));
            items.add(UItem.asButton(ID_ACCEPT, "Понимаю и принимаю риск"));
            return;
        }

        items.add(UItem.asHeader("Ограничения собеседника"));
        items.add(check(ID_SCREENSHOTS, "Разрешить скриншоты", GreyZone.ALLOW_SCREENSHOTS));
        items.add(UItem.asShadow("Снимает блокировку скриншотов в секретных чатах, одноразовых медиа и чатах с запретом копирования."));

        items.add(check(ID_NOFORWARDS, "Обходить запрет пересылки", GreyZone.BYPASS_NOFORWARDS));
        items.add(UItem.asShadow("Возвращает сохранение, копирование и пересылку там, где владелец чата их отключил. Учтите: сохранение и копирование работают, потому что содержимое уже на устройстве, а настоящую пересылку защищённого сообщения сервер Telegram всё равно отклонит."));

        items.add(UItem.asHeader("Режим призрака"));
        items.add(check(ID_GHOST_READ, "Не отправлять прочтение", GreyZone.GHOST_DONT_READ));
        items.add(check(ID_GHOST_READ_STRANGERS, "Невидимость для незнакомцев", GreyZone.GHOST_DONT_READ_STRANGERS));
        items.add(check(ID_GHOST_TYPING, "Не отправлять «печатает»", GreyZone.GHOST_DONT_TYPING));
        items.add(check(ID_GHOST_ONLINE, "Не показывать «в сети»", GreyZone.GHOST_DONT_ONLINE));
        items.add(check(ID_GHOST_STORIES, "Смотреть истории незаметно", GreyZone.GHOST_NO_READ_STORIES));
        items.add(UItem.asShadow("Чаты по-прежнему помечаются прочитанными у вас — наружу уходит только меньше информации. «Не показывать в сети» не мешает уйти в оффлайн."));

        UItem immediateOfflineItem = UItem.asCheck(ID_GHOST_IMMEDIATE_OFFLINE, "Активно возвращаться в оффлайн");
        immediateOfflineItem.checked = GreyZone.immediateOfflineEnabled();
        items.add(immediateOfflineItem);
        items.add(UItem.asShadow("Отправка сообщения, реакции и другие действия сами по себе выдают «в сети», даже если это скрыто. Эта опция сразу отправляет статус «не в сети» вслед за таким действием, перекрывая сигнал серверу."));

        UItem scheduleItem = UItem.asCheck(ID_GHOST_SCHEDULE, "Только по расписанию");
        scheduleItem.checked = GreyZone.isScheduleEnabled();
        items.add(scheduleItem);
        if (GreyZone.isScheduleEnabled()) {
            items.add(UItem.asButton(ID_GHOST_SCHEDULE_RANGE, "Период",
                    formatMinutes(GreyZone.getScheduleStartMinute()) + " — " + formatMinutes(GreyZone.getScheduleEndMinute())));
        }
        items.add(UItem.asShadow("Режим призрака и «не показывать в сети» действуют только в указанное время суток. Вне этого окна всё работает как обычно."));

        items.add(UItem.asHeader("Удалённые сообщения"));
        items.add(check(ID_SAVE_DELETED, "Сохранять удалённые собеседниками", GreyZone.SAVE_DELETED));
        if (GreyZone.isEnabled(GreyZone.SAVE_DELETED)) {
            DeletedMessagesStore store = DeletedMessagesStore.getInstance();
            items.add(UItem.asButton(ID_OPEN_DELETED, "Архив и очистка",
                    store.count() + " шт., " + (store.sizeOnDisk() / 1024) + " КБ"));
        }
        items.add(UItem.asShadow("Копия сохраняется локально, до лимита записей — старые вытесняются новыми. Свои удалённые сообщения не сохраняются."));

        items.add(UItem.asHeader("Активность"));
        items.add(check(ID_ACTIVITY_PEEK, "Видеть активность в общих группах", GreyZone.ACTIVITY_PEEK));
        items.add(UItem.asShadow("Если собеседник скрыл от вас «был(а) в сети», но сейчас печатает или отправляет что-то в группе, где вы оба состоите, — это будет видно в его профиле вместо скрытого статуса."));

        items.add(UItem.asHeader("Подписка"));
        items.add(check(ID_LOCAL_PREMIUM, "Локальный Premium", GreyZone.LOCAL_PREMIUM));
        items.add(UItem.asShadow("Расширение функционала на этом устройстве: бесконечные реакции, эмодзи-статусы, значок в профиле и увеличенные лимиты. Сервер по-прежнему не считает вас Premium-пользователем. Настройка лимитов — в «Дополнительно → Локальный Premium»."));

        items.add(UItem.asShadow(""));
        items.add(UItem.asButton(ID_REVOKE, "Отключить всё и скрыть раздел"));
    }

    private UItem check(int id, String text, String key) {
        UItem item = UItem.asCheck(id, text);
        item.checked = GreyZone.isEnabled(key);
        return item;
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_ACCEPT) {
            showAcceptDialog();
        } else if (item.id == ID_REVOKE) {
            GreyZone.setAccepted(false);
            listView.adapter.update(true);
            LaunchActivity.refreshGreyZoneUi();
        } else if (item.id == ID_SCREENSHOTS) {
            toggle(GreyZone.ALLOW_SCREENSHOTS);
        } else if (item.id == ID_NOFORWARDS) {
            toggle(GreyZone.BYPASS_NOFORWARDS);
        } else if (item.id == ID_GHOST_READ) {
            toggle(GreyZone.GHOST_DONT_READ);
        } else if (item.id == ID_GHOST_READ_STRANGERS) {
            toggle(GreyZone.GHOST_DONT_READ_STRANGERS);
        } else if (item.id == ID_GHOST_TYPING) {
            toggle(GreyZone.GHOST_DONT_TYPING);
        } else if (item.id == ID_GHOST_ONLINE) {
            toggle(GreyZone.GHOST_DONT_ONLINE);
        } else if (item.id == ID_SAVE_DELETED) {
            toggle(GreyZone.SAVE_DELETED);
        } else if (item.id == ID_OPEN_DELETED) {
            presentFragment(new DeletedMessagesActivity());
        } else if (item.id == ID_ACTIVITY_PEEK) {
            toggle(GreyZone.ACTIVITY_PEEK);
        } else if (item.id == ID_LOCAL_PREMIUM) {
            toggle(GreyZone.LOCAL_PREMIUM);
        } else if (item.id == ID_GHOST_STORIES) {
            toggle(GreyZone.GHOST_NO_READ_STORIES);
        } else if (item.id == ID_GHOST_IMMEDIATE_OFFLINE) {
            GreyZone.setEnabled(GreyZone.GHOST_IMMEDIATE_OFFLINE, !GreyZone.immediateOfflineEnabled());
            listView.adapter.update(true);
        } else if (item.id == ID_GHOST_SCHEDULE) {
            GreyZone.setScheduleEnabled(!GreyZone.isScheduleEnabled());
            listView.adapter.update(true);
            LaunchActivity.refreshGreyZoneUi();
        } else if (item.id == ID_GHOST_SCHEDULE_RANGE) {
            showScheduleRangeDialog();
        }
    }

    private String formatMinutes(int minutesSinceMidnight) {
        return String.format(java.util.Locale.getDefault(), "%02d:%02d", minutesSinceMidnight / 60, minutesSinceMidnight % 60);
    }

    private void showScheduleRangeDialog() {
        if (getParentActivity() == null) {
            return;
        }
        int startMinute = GreyZone.getScheduleStartMinute();
        new android.app.TimePickerDialog(getParentActivity(), (view, startHour, startMin) -> {
            int newStart = startHour * 60 + startMin;
            int endMinute = GreyZone.getScheduleEndMinute();
            new android.app.TimePickerDialog(getParentActivity(), (view2, endHour, endMin) -> {
                GreyZone.setSchedule(newStart, endHour * 60 + endMin);
                listView.adapter.update(true);
            }, endMinute / 60, endMinute % 60, true).show();
        }, startMinute / 60, startMinute % 60, true).show();
    }

    private void toggle(String key) {
        GreyZone.setEnabled(key, !GreyZone.isEnabled(key));
        listView.adapter.update(true);
        LaunchActivity.refreshGreyZoneUi();
    }

    private void showAcceptDialog() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Вы уверены?");
        builder.setMessage("Эти функции снимают защиту, которую выбрал ваш собеседник, и могут привести к блокировке аккаунта. " +
                "Разработчик их не одобряет — вся ответственность на вас.");
        builder.setPositiveButton("Принимаю", (dialog, which) -> {
            GreyZone.setAccepted(true);
            listView.adapter.update(true);
            LaunchActivity.refreshGreyZoneUi();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        showDialog(dialog);
        View button = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
        if (button instanceof android.widget.TextView) {
            ((android.widget.TextView) button).setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
