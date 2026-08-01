# Плагины PrimeGram

Плагины — это файлы `.plugin` на Python, которые расширяют клиент изнутри: перехватывают отправку
сообщений, отвечают на события, добавляют пункты в меню и свои экраны настроек.

API совместим с exteraGram: плагины, написанные под него, обычно работают без изменений. Ниже —
то, что доступно в PrimeGram, и чем он отличается.

- [Быстрый старт](#быстрый-старт)
- [Манифест](#манифест)
- [Жизненный цикл](#жизненный-цикл)
- [Хуки](#хуки)
- [Настройки плагина](#настройки-плагина)
- [Отправка сообщений](#отправка-сообщений)
- [Интерфейс](#интерфейс)
- [Файлы и хранилище](#файлы-и-хранилище)
- [Встроенные библиотеки](#встроенные-библиотеки)
- [Установка своих библиотек](#установка-своих-библиотек)
- [Чего в PrimeGram нет](#чего-в-primegram-нет)

---

## Быстрый старт

Плагин — это один файл. Сохраните его с расширением `.plugin`, отправьте себе в «Избранное» и
нажмите на него в чате.

```python
__id__ = "hello_world"
__name__ = "Hello World"
__description__ = "Отвечает на команду .hello"
__author__ = "@username"
__version__ = "1.0.0"
__min_version__ = "12.9.0"

from base_plugin import BasePlugin, HookResult, HookStrategy


class HelloPlugin(BasePlugin):

    def on_plugin_load(self):
        self.log("загрузился")
        self.add_on_send_message_hook()

    def on_send_message_hook(self, account, params):
        if not isinstance(params.message, str):
            return HookResult()
        if params.message.strip() == ".hello":
            params.message = "Привет из плагина 👋"
            return HookResult(strategy=HookStrategy.MODIFY)
        return HookResult()
```

**После установки плагин выключен.** Это отличие от exteraGram и сделано намеренно: код, который
пришёл файлом, не должен начинать работать до того, как его разрешили. Включается переключателем в
разделе плагинов.

---

## Манифест

Метаданные читаются из литералов верхнего уровня — до того, как файл будет выполнен. Поэтому они
должны быть простыми строками, а не выражениями.

| Поле | Обязательно | Что это |
|---|---|---|
| `__id__` | да | Идентификатор, латиница, цифры и `_`. По нему хранятся настройки |
| `__name__` | да | Название в списке |
| `__description__` | нет | Одна строка описания |
| `__author__` | нет | Автор |
| `__version__` | нет | Версия плагина |
| `__min_version__` | нет | Минимальная версия клиента; ниже — установка будет отклонена |
| `__icon__` | нет | `имя_пака/индекс`, например `PrimeGramPlugins/5` |
| `__requirements__` | нет | Список библиотек с PyPI, ставятся при установке |

```python
__requirements__ = ["emoji", "humanize>=4.0"]
```

---

## Жизненный цикл

```python
class MyPlugin(BasePlugin):

    def on_plugin_load(self):
        """Вызывается при включении. Здесь регистрируют хуки."""

    def on_plugin_unload(self):
        """Вызывается при выключении или удалении. Здесь всё за собой убирают."""

    def on_app_event(self, event_type):
        """AppEvent.START, AppEvent.RESUME, AppEvent.PAUSE, AppEvent.STOP."""
```

Плагин — синглтон: второй экземпляр того же класса не создаётся, потому что хуки и настройки
привязаны к идентификатору, а не к объекту.

---

## Хуки

### Отправка сообщения

Самый частый хук. Вызывается до того, как сообщение уйдёт на сервер.

```python
def on_plugin_load(self):
    self.add_on_send_message_hook(priority=0)

def on_send_message_hook(self, account, params):
    # params.message      — текст (str) или None для медиа
    # params.peer         — id диалога
    # params.path         — путь к файлу, если отправляется медиа
    # params.replyToMsg   — сообщение, на которое отвечают
    return HookResult()
```

`HookResult` управляет тем, что будет дальше:

| Стратегия | Что делает |
|---|---|
| `HookStrategy.DEFAULT` | Ничего не менять, отправить как есть |
| `HookStrategy.MODIFY` | Отправить с изменённым `params` |
| `HookStrategy.MODIFY_FINAL` | То же, но следующие плагины уже не спросят |
| `HookStrategy.CANCEL` | Отменить отправку |

### Запросы к серверу

```python
def on_plugin_load(self):
    self.add_hook("messages.sendMessage")
    self.add_hook("account.", match_substring=True)  # всё семейство сразу

def pre_request_hook(self, request_name, account, request):
    return HookResult()

def post_request_hook(self, request_name, account, response, error):
    return HookResult()
```

### Обновления от сервера

```python
def on_update_hook(self, update_name, account, update):
    return HookResult()

def on_updates_hook(self, container_name, account, updates):
    return HookResult()
```

### Пункты меню

```python
from base_plugin import MenuItemData, MenuItemType

def on_plugin_load(self):
    self.add_menu_item(MenuItemData(
        menu_type=MenuItemType.CHAT_ACTION_MENU,
        text="Моё действие",
        on_click=lambda ctx: self.log("нажали"),
    ))
```

`MenuItemType`: `MESSAGE_CONTEXT_MENU`, `CHAT_ACTION_MENU`, `PROFILE_ACTION_MENU`,
`DRAWER_MENU`, `MAIN_MENU`.

---

## Настройки плагина

Экран настроек описывается декларативно, а рисует его клиент — темой, отступами и всем прочим
занимаемся мы.

```python
from ui.settings import Header, Switch, Selector, Input, Text, Divider


class MyPlugin(BasePlugin):

    def create_settings(self):
        return [
            Header(text="Основное"),
            Switch(
                key="enabled",
                text="Включить обработку",
                default=True,
                subtext="Описание под строкой",
                on_change=lambda value: self.log("теперь %s" % value),
            ),
            Selector(
                key="mode",
                text="Режим",
                default=0,
                items=["Быстрый", "Точный"],
            ),
            Input(key="token", text="Ключ API", default=""),
            Divider(text="Пояснение внизу карточки"),
            Text(text="Сбросить", red=True, on_click=lambda ctx: self.reset()),
        ]
```

Значения читаются и пишутся откуда угодно:

```python
self.get_setting("mode", 0)
self.set_setting("mode", 1)
self.export_settings()          # весь словарь
self.import_settings({...})
```

Доступные виджеты: `Header`, `Switch`, `Selector`, `Input`, `EditText`, `Text`, `Divider`,
`Custom` (своя `View`, если ничего из перечисленного не подходит).

---

## Отправка сообщений

```python
import client_utils

client_utils.send_text(peer, "**жирный** и `моноширинный`", parse_mode="markdown")
client_utils.send_photo(peer, "/path/to/image.png", caption="подпись")
client_utils.send_video(peer, "/path/to/video.mp4")
client_utils.send_document(peer, "/path/to/file.zip")
client_utils.send_audio(peer, "/path/to/track.mp3")
```

Словарная форма, как в exteraGram, тоже работает:

```python
client_utils.send_message({"peer": peer, "message": "текст"})
```

Прямые запросы к серверу:

```python
from org.telegram.tgnet import TLRPC

req = TLRPC.TL_messages_getHistory()
req.peer = client_utils.get_messages_controller().getInputPeer(peer)
req.limit = 20
client_utils.send_request(req, lambda response, error: self.log(str(response)))
```

Контроллеры клиента доступны как есть: `get_messages_controller()`,
`get_send_messages_helper()`, `get_connections_manager()`, `get_user_config()`,
`get_media_data_controller()`, `get_file_loader()`, `get_notification_center()` и остальные —
все с необязательным аргументом `account`.

---

## Интерфейс

```python
from ui.bulletin import BulletinHelper
from ui.alert import AlertDialogBuilder
from android_utils import run_on_ui_thread

BulletinHelper.show_info("Готово")
BulletinHelper.show_error("Не вышло")

def ask():
    builder = AlertDialogBuilder(client_utils.get_last_fragment().getParentActivity())
    builder.set_title("Вопрос")
    builder.set_message("Продолжить?")
    builder.set_positive_button("Да", lambda dlg, which: self.go())
    builder.set_negative_button("Отмена", None)
    builder.show()

run_on_ui_thread(ask)
```

**Всё, что трогает интерфейс, должно идти через `run_on_ui_thread`.** Хуки вызываются из потоков
сети, и попытка нарисовать что-то оттуда роняет приложение.

---

## Файлы и хранилище

```python
import file_utils

file_utils.get_plugins_dir()
file_utils.get_cache_dir()
file_utils.get_files_dir()
file_utils.get_images_dir()      # и get_videos_dir / get_audios_dir / get_documents_dir

file_utils.write_file(path, "текст")
file_utils.read_file(path)
file_utils.write_file_bytes(path, b"\x00")
file_utils.list_dir(path, recursive=True, extensions=[".png"])
```

Одно ограничение, о которое легко споткнуться: Telegram отказывается отправлять файлы из
приватного каталога приложения. Если плагин что-то сгенерировал и хочет это отправить — кладите в
`get_documents_dir()`, а не в `get_cache_dir()`.

---

## Встроенные библиотеки

Всё перечисленное уже внутри APK, устанавливать не нужно — просто `import`.

**Сеть и разбор**
`requests` · `httpx` · `aiohttp` · `beautifulsoup4` · `lxml` · `regex` · `brotli` ·
`python-dateutil` · `packaging` · `pyyaml`

**Изображения и графика**
`pillow` · `pyzbar` (чтение QR) · `matplotlib` · `wordcloud`

**Данные и вычисления**
`numpy` · `pandas` · `pywavelets` · `editdistance` · `bitarray` · `lru-dict` · `cytoolz`

**Аудио**
`miniaudio` · `soxr` (передискретизация) · `lameenc` (кодирование MP3)

**Криптография**
`cryptography` · `pycryptodome` · `pynacl` · `bcrypt` · `tgcrypto` · `argon2-cffi`

**Сжатие и система**
`zstandard` · `lz4` · `psutil` · `netifaces` · `ephem` (астрономия) · `markupsafe` · `greenlet`

Пример — график прямо в чат:

```python
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import file_utils, client_utils, os

def send_chart(peer, values):
    plt.figure(figsize=(6, 3))
    plt.plot(values)
    path = os.path.join(file_utils.get_documents_dir(), "chart.png")
    plt.savefig(path, dpi=140, bbox_inches="tight")
    plt.close()
    client_utils.send_photo(peer, path, caption="График")
```

---

## Установка своих библиотек

Чистые Python-пакеты ставятся с PyPI при установке плагина:

```python
__requirements__ = ["emoji", "humanize>=4.0"]
```

Скачивание идёт в каталог плагинов и не трогает APK. Пакеты, которые тянут за собой компиляцию,
таким образом установить нельзя — их нужно просить добавить в сборку.

Список установленного и очистка — в разделе плагинов в настройках.

---

## Чего в PrimeGram нет

Честно, чтобы не искать несуществующее:

| Возможность | Состояние |
|---|---|
| `hook_method`, `hook_all_methods`, `hook_all_constructors` | Нет. Требуют Xposed-подобной подмены байткода; вызовы не падают, а пишут в лог |
| `add_file_hook` | Нет |
| `add_intent_hook` | Нет |
| `scipy`, `opencv-python`, `ujson`, `soundfile`, `shapely` | Недоступны: сборок под Android для Python 3.11 не существует |

Python — 3.11, тот же, что в exteraGram. Это осознанный выбор: плагины пишут под него.

---

## Отладка

`self.log("...")` пишет в логи приложения. Включить их: **Настройки PrimeGram → Диагностика →
Подробные логи**. Ошибка при загрузке плагина показывается прямо в списке плагинов, вместе с
именем исключения.
