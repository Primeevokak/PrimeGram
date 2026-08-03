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

Это `SendMessagesHelper.SendMessageParams` — **живой объект клиента**, а не копия. Что в нём
поменяете, то и уйдёт; копировать обратно нечего.

Что там лежит, по группам:

| Поле | Тип | Что это |
|---|---|---|
| `message` | `str` | Текст. `None`, когда отправляется медиа |
| `caption` | `str` | Подпись к медиа |
| `entities` | `ArrayList<MessageEntity>` | Разметка: жирный, ссылки, спойлеры |
| `peer` | `long` | Куда отправляем |
| `path` | `str` | Путь к файлу для медиа |
| `document` / `photo` | `TL_document` / `TL_photo` | Уже готовое вложение, если оно пересылается |
| `replyToMsg` | `MessageObject` | Сообщение, на которое отвечают |
| `replyToTopMsg` | `MessageObject` | Тема в форуме |
| `webPage` | `WebPage` | Превью ссылки |
| `searchLinks` | `bool` | Искать ли ссылку для превью. Поставьте `False`, чтобы превью не было |
| `notify` | `bool` | `False` — отправить без звука |
| `scheduleDate` | `int` | Unix-время отложенной отправки; `0` — сразу |
| `ttl` | `int` | Таймер самоуничтожения, секунды |
| `hasMediaSpoilers` | `bool` | Медиа под спойлером |
| `updateStickersOrder` | `bool` | Поднимать ли стикер в недавние |
| `replyMarkup` | `ReplyMarkup` | Клавиатура, для ботов |
| `params` | `HashMap<String,String>` | Служебные пары ключ-значение самого клиента |

Проверяйте тип перед работой с текстом: `params.message` для фотографии равен `None`, и
`params.message.strip()` на этом месте уронит хук.

```python
if not isinstance(params.message, str):
    return HookResult()
```

### Когда плагинов несколько

Хуки отправки сортируются по приоритету, **больший идёт первым**. Дальше:

| Что вернул плагин | Что происходит |
|---|---|
| `DEFAULT` или `None` | Идём к следующему плагину |
| `MODIFY` | Правки уже в объекте; следующий плагин тоже получит свой ход |
| `MODIFY_FINAL` | Цепочка обрывается, сообщение уходит изменённым |
| `CANCEL` | Цепочка обрывается, сообщение не уходит |

Исключение внутри плагина не роняет цепочку: оно пишется в лог, этот плагин пропускается,
остальные работают. То есть сломанный плагин не мешает исправным и не мешает вам отправлять
сообщения.

Отсюда практическое правило: если ваш плагин **дополняет** текст — возвращайте `MODIFY` и дайте
другим доработать. `MODIFY_FINAL` берите, только когда чужое вмешательство после вас всё испортит.

### Хуки запросов и обновлений

```python
def pre_request_hook(self, request_name, account, request):
    return HookResult()

def post_request_hook(self, request_name, account, response, error):
    return HookResult()

def on_update_hook(self, update_name, account, update):
    return HookResult()

def on_updates_hook(self, container_name, account, updates):
    return HookResult()
```

**Достаточно переопределить метод** — регистрировать ничего не нужно, плагин начнёт получать всё.
Если событий слишком много, сузьте выборку:

```python
self.add_hook("TL_messages_sendMessage")              # точное имя
self.add_hook("messages.", match_substring=True)      # всё семейство
```

Имя приходит как имя класса — `TL_messages_sendMessage`. Но сравнивается и с точечной формой
`messages.sendMessage`, потому что именно так метод называется в документации Telegram API, и
угадывать, какую из двух мы ждём, вы не должны.

Что можно вернуть:

| Хук | Стратегия | Что произойдёт |
|---|---|---|
| `pre_request_hook` | `CANCEL` | Запрос не уйдёт. Вызывающему придёт ошибка `-2000 CANCELED_BY_PLUGIN` |
| `pre_request_hook` | `MODIFY` + `request=` | На сервер уйдёт ваш объект вместо исходного |
| `post_request_hook` | `MODIFY` + `response=` | Вызывающий получит ваш ответ вместо настоящего |
| любой | `MODIFY_FINAL` | Остальные плагины про это событие не узнают |

Про отмену стоит знать одну вещь: вызывающий **всегда** получает ответ. Просто выбросить запрос
означало бы оставить крутиться спиннер, который никогда не остановится, поэтому вместо тишины
приходит ошибка.

**Где именно это перехватывается.** Запросы — в `ConnectionsManager.sendRequest`, до сериализации,
и в момент доставки ответа. Обновления — в `processUpdates` (весь контейнер, `on_updates_hook`) и
в `processUpdateArray` (каждое по отдельности, `on_update_hook`). Последняя точка ловит всё сразу:
и то, что пришло по сокету, и то, что приехало пушем, и то, что догрузилось после офлайна.

Оба пути горячие: пока ни один плагин их не переопределил, они стоят одно чтение поля и в Python
не заходят вовсе.

### Пункты меню

```python
from base_plugin import MenuItemData, MenuItemType

self.add_menu_item(MenuItemData(
    menu_type=MenuItemType.MESSAGE_CONTEXT_MENU,
    text="Моё действие",
    on_click=lambda ctx: ...,
    icon=None,        # имя drawable-ресурса приложения, как у обычных строк настроек
    condition=None,    # выражение на MVEL — тот же движок, что у HookFilter.Condition
    priority=0,
))
```

`MenuItemType`: `MESSAGE_CONTEXT_MENU` (меню сообщения по долгому тапу), `CHAT_ACTION_MENU`
(шапка чата, «...»), `PROFILE_ACTION_MENU` (меню профиля, «...»), `DRAWER_MENU` (классическое
боковое меню — пункт добавится, только если пользователь включил его в настройках интерфейса
вместо вкладок снизу; проверить это можно через `android_utils.is_navigation_drawer()`, чтобы не
удивляться, почему пункт «не появляется» у тестировщика с настройками по умолчанию). `MAIN_MENU`
пока никуда не подключён — добавленные туда пункты не показываются нигде.

`on_click` и `condition` получают контекст — `dict`-подобный объект с ключами вроде `account`,
`dialog_id`, `chat`/`chat_id`, `user`/`user_id`, `message` (только у `MESSAGE_CONTEXT_MENU`) —
какие есть, зависит от типа меню и от того, открыт ли сейчас личный чат или групповой.

### Пилюли над списком чатов

```python
from base_plugin import PillData

pill_id = self.add_pill(PillData(
    text="Кэш: 128 МБ",
    on_click=lambda: ...,
    icon=None,          # имя drawable-ресурса, необязательно
    color="#ff2196f3",  # ARGB-hex фона пилюли, необязательно — по умолчанию цвет темы
    priority=0,
))

self.update_pill(pill_id, text="Кэш: 130 МБ")   # поменять значение на месте, без мигания
self.remove_pill(pill_id)
```

Своих встроенных пилюль в PrimeGram нет — ни погоды, ни курса чего-либо: это чистая площадка для
плагинов, а не готовый набор виджетов. Строка целиком скрыта (нулевой высоты), пока хотя бы один
плагин не добавит первую пилюлю. `on_click` вызывается без аргументов — в отличие от пунктов меню,
у пилюли нет своего «текущего чата», который стоило бы передавать.

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
Custom(view=None, factory=None, factory_args=None, on_click=None)
```

`create_sub_fragment` открывает вложенный экран: верните из него список тех же виджетов, и по
клику на строку появится новый экран с этим списком (заголовок — `text` строки). Работает для
любой строки, у которой есть `create_sub_fragment`, сколько угодно уровней вложенности.

`Custom` — строка, которую рисует сам плагин. `view` — самый прямой путь: постройте
`android.view.View` через Chaquopy и передайте готовый объект, мы разместим его как есть, без
оформления и выравнивания (осознанный размен — за такое обычно берутся ради превью или графика, а
их честно описать нечем). `factory` — для view, который нужно построить лениво или параметрически:
функция вида `factory(context, factory_args)`, вызывается прямо перед показом строки, должна
вернуть `View`; либо объект с методом `.create(context, factory_args)` вместо голой функции. Это
наш собственный аналог `Factory` из exteraGram — тот у них Java-класс, который приложение
инстанцирует само, а мы просто вызываем ваш код. Из-за этого плагин, портированный из exteraGram
с подклассом их `CustomSetting.Factory`, работать не будет — этот подкласс сам по себе не может
быть создан без генерации Java-класса из Python в рантайме (то же самое, что нужно
`ClassBuilder`). Плагин, написанный под `factory=...` в этом виде — будет.

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

Режим интерфейса — PrimeGram даёт пользователю переключаться между современным видом и
классическим (плоским, с боковым меню вместо вкладок), так что отступы, скругления и сам набор
экранов на устройстве могут отличаться от того, что видно у вас при разработке. Официальные хуки
и меню этого не замечают, но если плагин трогает Java-экраны напрямую — стоит спросить сначала:

```python
is_classic_ui()             # включён плоский интерфейс (до редизайна)
is_navigation_drawer()      # боковое меню вместо вкладок снизу
is_bottom_tabs_hidden()     # вкладок снизу вообще нет (включает случай бокового меню)
is_bottom_tabs_compact()    # вкладки снизу — только иконки, без подписей
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

### Подмена методов

```python
hook_method(method_or_constructor, xposed_hook=None, priority=None, *,
            before=None, after=None, before_filters=None, after_filters=None)
hook_all_methods(hook_class, method_name, xposed_hook=None, priority=None, *, ...)
hook_all_constructors(hook_class, xposed_hook=None, priority=None, *, ...)
unhook_method(unhook)
unhook_all()
hooking_available          # свойство: умеет ли устройство
```

Три формы вызова, все равноправны:

```python
# 1. Голые функции
self.hook_all_methods(cls, "name", before=lambda p: ..., after=lambda p: ...)

# 2. Объект-хук
class MyHook(MethodHook):
    def before_hooked_method(self, param): ...
    def after_hooked_method(self, param): ...

self.hook_all_methods(cls, "name", MyHook())

# 3. Замена метода целиком
class MyReplacement(MethodReplacement):
    def replace_hooked_method(self, param):
        return "вместо оригинала"

self.hook_method(member, MyReplacement())
```

В `param` приходит `XC_MethodHook.MethodHookParam`:

| Что | Зачем |
|---|---|
| `param.args` | Аргументы вызова, можно менять |
| `param.thisObject` | Объект, у которого вызвали метод |
| `param.getResult()` / `param.setResult(v)` | Возвращаемое значение |
| `param.getThrowable()` / `param.setThrowable(t)` | Исключение |
| `param.method` | Сам метод |

Выставленный в `before` результат отменяет выполнение оригинала.

### Несколько хуков на одном методе

Порядок задаёт `priority`, и правила тут не наши, а Xposed — мы их не меняли:

1. Все `before` выполняются по убыванию приоритета.
2. Если хоть один выставил результат или исключение, **оригинальный метод не выполняется**. Но
   остальные `before` всё равно отработают: они увидят уже выставленный результат через
   `param.getResult()` и могут его переписать.
3. Затем все `after` — **в обратном порядке**, от низкого приоритета к высокому. То есть тот, кто
   вошёл первым, выходит последним.

Практический вывод: если хотите быть уверены, что видите окончательный результат, — берите высокий
приоритет и работайте в `after`.

Хуки плагина снимаются автоматически при выключении — звать `unhook_all` вручную не нужно.

### Что делать нельзя

Хук выполняется **внутри чужого метода и на его потоке** — часто на главном. Отсюда:

- никакой сети и файлов в хуке горячего метода: вы держите кадр;
- ничего рисующего напрямую — только через `run_on_ui_thread`;
- не хукайте то, что вызывается тысячи раз в секунду (`onDraw`, `onMeasure`), если внутри что-то
  тяжелее сравнения.

Исключение из вашего кода наружу не выпускается: оно попадёт в лог, а метод отработает как обычно.
Приложение из-за ошибки в плагине не упадёт — упасть оно может от того, **что** хук делает, а не от
того, что он ошибся.

### Фильтры

Ограничивают срабатывание хука, чтобы не проверять условие внутри каждого вызова.

```python
from base_plugin import HookFilter

self.hook_all_methods(cls, "process",
                      after=self.handle,
                      after_filters=[HookFilter.RESULT_NOT_NULL,
                                     HookFilter.ArgumentEqual(0, "нужное")])
```

Готовые: `RESULT_IS_NULL`, `RESULT_NOT_NULL`, `RESULT_IS_TRUE`, `RESULT_IS_FALSE`.
Строятся вызовом: `ResultEqual(v)`, `ResultNotEqual(v)`, `ResultIsInstanceOf(cls)`,
`ArgumentIsNull(i)`, `ArgumentNotNull(i)`, `ArgumentIsTrue(i)`, `ArgumentIsFalse(i)`,
`ArgumentEqual(i, v)`, `ArgumentNotEqual(i, v)`, `ArgumentIsInstanceOf(i, cls)`, `Or(*filters)`.

`Condition(expression, object=None)` — выражение на MVEL, вычисляется тем же движком
(`org.mvel:mvel2`), что и в самом exteraGram, так что строка, написанная под exteraGram, значит
то же самое и здесь. Внутри выражения доступны `param` (аргументы метода), `result` (после хука —
результат оригинала, до хука — `None`) и `object` (то, что передали в `Condition`); контекст
выражения (`this`) — экземпляр, на котором вызван метод:

```python
HookFilter.Condition("param.args[0] == object || this instanceof android.view.View", object=42)
```

### Файловые хуки

```python
from file_utils import FilesController

secret = self.add_file_hook(FilesController.FileInfo(
    ext="epub",
    on_click=callback,
    whitelist_places=[],        # необязательно
    blacklist_places=[],        # необязательно
))
self.remove_file_hook("epub", secret)
```

В `on_click` приходит `OnClickArgs`:

| Поле | Тип | Что внутри |
|---|---|---|
| `file` | `java.io.File` | Файл на диске. Уже скачан — по нескачанному нажатие не сработает |
| `file_name` | `str` | Имя с расширением, как его видит пользователь |
| `message` | `MessageObject` или `None` | Сообщение, в котором лежит файл. `None`, если открывали не из чата |
| `activity` | `Activity` | Текущая активность — нужна для диалогов |
| `place` | `FilesController.Place` | Откуда нажали |
| `parent_fragment` | `BaseFragment` или `None` | Экран, если он известен |

`Place`: `ChatActivity`, `SharedMediaLayout`, `FilteredSearchView`, `SearchDownloadsContainer`,
`ChannelAdminLogActivity`, `UNKNOWN`. Ограничить срабатывание можно списками `whitelist_places` и
`blacklist_places` в `FileInfo`.

Расширение сравнивается без учёта регистра и без точки: `"epub"` и `".EPUB"` — одно и то же.

Одно расширение — один плагин; повторная регистрация поднимет `ExtensionAlreadyRegistered`.

**Что происходит после вашего колбэка.** Файл считается обработанным: системный просмотрщик не
откроется. Если ваш колбэк бросил исключение, оно уйдёт в лог, а нажатие просто ничего не сделает —
открывать файл «обычным способом» задним числом было бы неожиданностью для пользователя, который
уже увидел, что за файл взялся плагин.

### Intent-хуки

```python
from base_plugin import IntentHookType
from intents import IntentsManager

handle = self.add_intent_hook(
    IntentsManager.HandlerInfo(
        callback=fn,
        scheme=None, host=None, path=None,
        action=None, type=None, categories=None,
        required_path_args_names=None,
        whitelist_flags=None, blacklist_flags=None,
        priority=0,
    ),
    IntentHookType.BEFORE,
)
self.remove_intent_hook(handle)
```

Колбэк получает Java-объект `Intent`. **Верните `True`, если обработали** — тогда ни клиент, ни
следующие обработчики его не увидят. Любой другой ответ, включая `None`, означает «не моё».

`BEFORE` — до собственной обработки клиента; это то, что нужно, если вы заняли свою схему ссылок.
`AFTER` достанется только то, что клиент не разобрал сам.

Заполненные поля `HandlerInfo` работают как фильтр, пустые не проверяются:

| Поле | С чем сравнивается |
|---|---|
| `scheme`, `host`, `path` | Разобранный `intent.getDataString()` |
| `required_path_args_names` | Имена, которые обязаны быть в параметрах ссылки |
| `action` | `intent.getAction()`, например `android.intent.action.SEND` |
| `type` | `intent.getType()` — MIME, например `text/plain` |
| `categories` | Все перечисленные должны быть в `intent.getCategories()` |
| `whitelist_flags` | Каждый флаг обязан стоять в `intent.getFlags()` |
| `blacklist_flags` | Ни один не должен стоять |
| `priority` | Больший идёт первым |

`IntentsManager.parse(url)` разбирает ссылку в один плоский словарь: `scheme`, `host`, `path` и все
параметры запроса.

```python
IntentsManager.parse("myplugin://open/chat?id=42&mode=fast")
# {'scheme': 'myplugin', 'host': 'open', 'path': '/chat', 'id': '42', 'mode': 'fast'}
```

Исключение в обработчике попадает в лог, а intent идёт дальше по цепочке — в отличие от файлов,
здесь молча съесть его было бы хуже.
