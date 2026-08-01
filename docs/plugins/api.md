# Справочник API

Всё, что доступно плагину. Если вы здесь впервые — начните с [обзорной страницы](README.md), там
то же самое, но по порядку и с объяснениями.

- [BasePlugin](#baseplugin)
- [Хуки](#хуки)
- [client_utils](#client_utils)
- [Настройки](#настройки)
- [Интерфейс](#интерфейс)
- [Файлы](#файлы)
- [Прочее](#прочее)

---

## BasePlugin

Базовый класс. Наследуйтесь от него, и клиент подхватит плагин сам — регистрировать ничего не
нужно.

### Что переопределяют

| Метод | Когда вызывается |
|---|---|
| `on_plugin_load()` | Плагин включили |
| `on_plugin_unload()` | Выключили или удалили |
| `create_settings()` | Открыли настройки плагина; вернуть список виджетов |
| `on_app_event(event)` | `AppEvent.START` / `STOP` / `PAUSE` / `RESUME` |
| `on_send_message_hook(account, params)` | Сообщение собирается уйти |
| `pre_request_hook(name, account, request)` | Перед запросом к серверу |
| `post_request_hook(name, account, response, error)` | После ответа |
| `on_update_hook(name, account, update)` | Пришло обновление |
| `on_updates_hook(name, account, updates)` | Пришла пачка обновлений |

### Что вызывают

```python
self.add_on_send_message_hook(priority=0)
self.add_hook(name, match_substring=False, priority=0)
self.remove_hook(name)

self.get_setting(key, default=None)
self.set_setting(key, value, reload_settings=False)
self.export_settings()
self.import_settings(settings)

self.add_menu_item(menu_item_data)      # вернёт item_id
self.remove_menu_item(item_id)

self.log(message)
self.client(account=None)
```

Приоритет решает порядок, если хуков несколько: меньше число — раньше очередь.

---

## Хуки

### HookResult

```python
HookResult(strategy=HookStrategy.DEFAULT, message=None, params=None)
```

| Стратегия | Что произойдёт |
|---|---|
| `DEFAULT` | Ничего не меняем |
| `MODIFY` | Отправляем изменённое, остальные плагины ещё спросим |
| `MODIFY_FINAL` | Отправляем изменённое, дальше никого не спрашиваем |
| `CANCEL` | Не отправляем |

### params в on_send_message_hook

Это живой объект клиента — что в нём поменяете, то и уйдёт.

| Поле | Что это |
|---|---|
| `message` | Текст. `None`, если отправляется медиа |
| `peer` | Идентификатор диалога |
| `path` | Путь к файлу для медиа |
| `replyToMsg` | Сообщение, на которое отвечают |
| `caption` | Подпись к медиа |

### Хуки запросов

`add_hook("messages.sendMessage")` — точное имя. `add_hook("account.", match_substring=True)` —
всё семейство сразу.

### Пункты меню

```python
from base_plugin import MenuItemData, MenuItemType

self.add_menu_item(MenuItemData(
    menu_type=MenuItemType.MESSAGE_CONTEXT_MENU,
    text="Моё действие",
    on_click=lambda ctx: ...,
    icon=None,
    priority=0,
))
```

`MenuItemType`: `MESSAGE_CONTEXT_MENU`, `CHAT_ACTION_MENU`, `PROFILE_ACTION_MENU`, `DRAWER_MENU`,
`MAIN_MENU`.

---

## client_utils

### Отправка

```python
send_text(peer, text, *, account=None, parse_mode=None)
send_photo(peer, file_path, caption="", high_quality=False, *, account=None, parse_mode=None)
send_video(peer, file_path, caption="", *, account=None, parse_mode=None)
send_document(peer, file_path, caption="", *, account=None, parse_mode=None)
send_audio(peer, file_path, caption="", *, account=None, parse_mode=None)
send_message({"peer": ..., "message": ...})     # словарная форма из exteraGram
```

`parse_mode` — `"markdown"` или `"html"`.

### Запросы

```python
send_request(request, callback, *, account=None)
```

Колбэк получает `(response, error)` и вызывается из сетевого потока — если оттуда нужно тронуть
интерфейс, оборачивайте в `run_on_ui_thread`.

### Контроллеры

Все с необязательным `account`; без него берётся текущий.

```python
get_messages_controller()      get_send_messages_helper()
get_connections_manager()      get_messages_storage()
get_user_config()              get_account_instance()
get_contacts_controller()      get_media_data_controller()
get_file_loader()              get_download_controller()
get_notifications_controller() get_notifications_settings()
get_notification_center()      get_location_controller()
get_secret_chat_helper()       get_media_controller()
get_last_fragment()            get_selected_account()
```

### Потоки

```python
run_on_queue(fn, queue_name=GLOBAL_QUEUE, delay=0)
get_queue_by_name(queue_name)
account_scope(account)          # контекстный менеджер: временно другой аккаунт
```

---

## Настройки

Из `ui.settings`. Все виджеты — датаклассы, поля задаются по имени.

```python
Switch(key, text, default, subtext="", icon="", on_change=None)
Selector(key, text, default, items, icon="", on_change=None)
Input(key, text, default="", subtext="", icon="", on_change=None)
EditText(key, hint, default="", multiline=False, max_length=0, mask="", on_change=None)
Text(text, subtext="", icon="", accent=False, red=False, on_click=None,
     create_sub_fragment=None)
Header(text)
Divider(text="")
Custom(view)
```

`Text` с `create_sub_fragment` открывает вложенный экран — верните из него список виджетов.

`Custom` принимает вашу собственную `View`. Мы её не оформляем и не выравниваем — это осознанный
размен: за такое обычно берутся ради превью или графика, а их честно описать нечем.

Значения хранятся отдельно от Java-настроек клиента, в каталоге плагинов, и переживают
переустановку плагина.

---

## Интерфейс

### BulletinHelper

Всплывающая плашка внизу экрана.

```python
BulletinHelper.show_info(message)
BulletinHelper.show_error(message)
BulletinHelper.show_success(message)
BulletinHelper.show_simple(text, icon_res_id)
BulletinHelper.show_two_line(title, subtitle, icon_res_id)
BulletinHelper.show_with_button(text, icon_res_id, button_text, on_click)
BulletinHelper.show_undo(text, on_undo, on_action=None, subtitle=None)
BulletinHelper.show_copied_to_clipboard()
```

### AlertDialogBuilder

```python
builder = AlertDialogBuilder(activity)
builder.set_title("Заголовок")
builder.set_message("Текст")
builder.set_positive_button("ОК", lambda dlg, which: ...)
builder.set_negative_button("Отмена", None)
builder.set_items(["Раз", "Два"], lambda dlg, which: ...)
builder.set_view(view)
builder.make_button_red(AlertDialogBuilder.BUTTON_POSITIVE)
builder.show()
```

Активность берут из `client_utils.get_last_fragment().getParentActivity()`.

### android_utils

```python
run_on_ui_thread(func, delay=0)
copy_to_clipboard(text)
log(data)
OnClickListener(func)        # готовые прокси для Java-слушателей
OnLongClickListener(func)
```

---

## Файлы

```python
get_plugins_dir()     get_cache_dir()      get_files_dir()
get_images_dir()      get_videos_dir()     get_audios_dir()     get_documents_dir()

read_file(path)                write_file(path, content)
read_file_bytes(path)          write_file_bytes(path, content)
delete_file(path)              ensure_dir_exists(path)
list_dir(path, recursive=False, include_files=True, include_dirs=False, extensions=None)
```

Файлы, которые собираетесь отправлять, кладите в `get_documents_dir()` — из приватного каталога
приложения Telegram отправлять отказывается.

---

## Прочее

### markdown_utils

```python
parse_markdown(text)                      # → ParsedMessage(text, entities)
parse_text(text, parse_mode="markdown", is_caption=False)
```

Пригодится, если собираете сущности вручную для прямого запроса.

### hook_utils

Доступ к приватным полям Java-объектов через рефлексию.

```python
find_class(name)
get_private_field(obj, field_name)         set_private_field(obj, field_name, value)
get_static_private_field(clazz, name)      set_static_private_field(clazz, name, value)
```

Инструмент острый: имена полей меняются между версиями клиента, и молчаливая поломка после
обновления — обычное дело. Оборачивайте в `try`.

### Чего нет

`hook_method`, `hook_all_methods`, `hook_all_constructors`, `add_file_hook`, `add_intent_hook`.
Вызовы существуют, чтобы плагин не падал, но ничего не делают и пишут строчку в лог.
