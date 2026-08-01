# Рецепты

Готовые куски на задачи, которые встречаются чаще всего. Копируйте и правьте под себя.

## Команда в чате

Классика: пользователь пишет `.что-то`, плагин подменяет это на результат.

```python
from base_plugin import BasePlugin, HookResult, HookStrategy
import datetime


class TimePlugin(BasePlugin):

    def on_plugin_load(self):
        self.add_on_send_message_hook()

    def on_send_message_hook(self, account, params):
        if not isinstance(params.message, str):
            return HookResult()

        if params.message.strip() == ".time":
            params.message = datetime.datetime.now().strftime("Сейчас %H:%M:%S")
            return HookResult(strategy=HookStrategy.MODIFY)

        return HookResult()
```

## Команда с аргументом

```python
def on_send_message_hook(self, account, params):
    if not isinstance(params.message, str):
        return HookResult()

    text = params.message.strip()
    if not text.startswith(".calc "):
        return HookResult()

    expression = text[6:]
    try:
        # eval здесь только для примера; в настоящем плагине берите ast.literal_eval
        # или полноценный парсер, иначе плагин исполнит всё, что пользователь напишет.
        params.message = "%s = %s" % (expression, eval(expression))
    except Exception as error:
        params.message = "Не считается: %s" % error

    return HookResult(strategy=HookStrategy.MODIFY)
```

## Отменить отправку

```python
def on_send_message_hook(self, account, params):
    if isinstance(params.message, str) and "секрет" in params.message.lower():
        from ui.bulletin import BulletinHelper
        from android_utils import run_on_ui_thread

        run_on_ui_thread(lambda: BulletinHelper.show_error("Отправка отменена плагином"))
        return HookResult(strategy=HookStrategy.CANCEL)

    return HookResult()
```

## Долгая работа без заморозки интерфейса

Сеть и тяжёлые вычисления нельзя делать прямо в хуке: пока плагин думает, приложение стоит.
Правильный способ — отпустить сообщение, посчитать в фоне и дослать результат.

```python
import client_utils
from base_plugin import BasePlugin, HookResult, HookStrategy


class WeatherPlugin(BasePlugin):

    def on_plugin_load(self):
        self.add_on_send_message_hook()

    def on_send_message_hook(self, account, params):
        if not isinstance(params.message, str) or params.message.strip() != ".weather":
            return HookResult()

        peer = params.peer
        params.message = "Смотрю погоду…"
        client_utils.run_on_queue(lambda: self._fetch(peer))
        return HookResult(strategy=HookStrategy.MODIFY)

    def _fetch(self, peer):
        import requests
        try:
            data = requests.get("https://wttr.in/Moscow?format=3", timeout=10).text.strip()
        except Exception as error:
            data = "Не вышло: %s" % error
        client_utils.send_text(peer, data)
```

## Реакция на события приложения

```python
from base_plugin import BasePlugin, AppEvent


class StartupPlugin(BasePlugin):

    def on_app_event(self, event_type):
        if event_type == AppEvent.RESUME:
            self.log("вернулись в приложение")
```

## Пункт в меню сообщения

```python
from base_plugin import BasePlugin, MenuItemData, MenuItemType
from android_utils import copy_to_clipboard, run_on_ui_thread
from ui.bulletin import BulletinHelper


class CopyIdPlugin(BasePlugin):

    def on_plugin_load(self):
        self.add_menu_item(MenuItemData(
            menu_type=MenuItemType.MESSAGE_CONTEXT_MENU,
            text="Скопировать ID",
            on_click=self._copy,
        ))

    def _copy(self, context):
        message = context.get("message")
        if message is None:
            return
        copy_to_clipboard(str(message.getId()))
        run_on_ui_thread(lambda: BulletinHelper.show_copied_to_clipboard())
```

## Настройки, которые на что-то влияют

```python
from ui.settings import Header, Switch, Input


class ConfigurablePlugin(BasePlugin):

    def create_settings(self):
        return [
            Header(text="Поведение"),
            Switch(key="active", text="Обрабатывать сообщения", default=True),
            Input(key="prefix", text="Префикс команд", default="."),
        ]

    def on_send_message_hook(self, account, params):
        if not self.get_setting("active", True):
            return HookResult()

        prefix = self.get_setting("prefix", ".")
        if isinstance(params.message, str) and params.message.startswith(prefix + "ping"):
            params.message = "pong"
            return HookResult(strategy=HookStrategy.MODIFY)

        return HookResult()
```

## Диалог с подтверждением

```python
import client_utils
from ui.alert import AlertDialogBuilder
from android_utils import run_on_ui_thread


def confirm(self, question, on_yes):
    def show():
        fragment = client_utils.get_last_fragment()
        if fragment is None:
            return
        builder = AlertDialogBuilder(fragment.getParentActivity())
        builder.set_title("Подтверждение")
        builder.set_message(question)
        builder.set_positive_button("Да", lambda dialog, which: on_yes())
        builder.set_negative_button("Отмена", None)
        builder.show()

    run_on_ui_thread(show)
```

## Своё хранилище рядом с настройками

Настройки хороши для нескольких значений. Если данных больше — заведите файл.

```python
import json, os, file_utils


def _path(self):
    return os.path.join(file_utils.get_plugins_dir(), "%s_data.json" % self.id)


def load(self):
    path = self._path()
    if not os.path.exists(path):
        return {}
    try:
        return json.loads(file_utils.read_file(path))
    except Exception:
        return {}


def save(self, data):
    file_utils.write_file(self._path(), json.dumps(data, ensure_ascii=False))
```

## Картинка из данных

```python
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import os, file_utils, client_utils


def send_pie(peer, labels, values):
    plt.figure(figsize=(4, 4))
    plt.pie(values, labels=labels, autopct="%1.0f%%")
    path = os.path.join(file_utils.get_documents_dir(), "pie.png")
    plt.savefig(path, dpi=140, bbox_inches="tight", transparent=True)
    plt.close()
    client_utils.send_photo(peer, path)
```

## Подменить поведение клиента

Хук на метод — способ вмешаться там, где мы не сделали двери заранее.

```python
from base_plugin import BasePlugin
from java import jclass


class SizePlugin(BasePlugin):

    def on_plugin_load(self):
        if not self.hooking_available:
            self.log("на этом устройстве хуки не работают")
            return

        cls = jclass("org.telegram.messenger.AndroidUtilities").getClass()
        self.hook_all_methods(cls, "formatFileSize", after=self._round)

    def _round(self, param):
        # Всё, что меньше мегабайта, показываем как «меньше МБ»
        if len(param.args) > 0 and param.args[0] < 1024 * 1024:
            param.setResult("< 1 МБ")
```

## Свой формат файла

```python
from base_plugin import BasePlugin
from file_utils import FilesController
from ui.bulletin import BulletinHelper
from android_utils import run_on_ui_thread


class BookPlugin(BasePlugin):

    def on_plugin_load(self):
        self.add_file_hook(FilesController.FileInfo(
            ext="epub",
            on_click=self._open,
        ))

    def _open(self, args):
        run_on_ui_thread(lambda: BulletinHelper.show_info(
            "Открываю %s" % args.file_name))
```

## Своя схема ссылок

```python
from base_plugin import BasePlugin, IntentHookType
from intents import IntentsManager


class LinkPlugin(BasePlugin):

    def on_plugin_load(self):
        self.add_intent_hook(
            IntentsManager.HandlerInfo(callback=self._handle, scheme="myplugin"),
            IntentHookType.BEFORE,
        )

    def _handle(self, intent):
        parsed = IntentsManager.parse(intent.getDataString())
        self.log("пришло: %s" % parsed)
        return True          # обработали, дальше не пускаем
```

---

## Три ошибки, на которых спотыкаются все

**Интерфейс из фонового потока.** Любой `BulletinHelper`, диалог или обращение к `View` — только
через `run_on_ui_thread`. Иначе падает не плагин, а приложение целиком.

**Файл для отправки в кэше.** Telegram не отправляет ничего из приватного каталога приложения.
Генерируете файл, чтобы его отправить, — только `get_documents_dir()`.

**Забытый `return HookResult()`.** Если из хука ничего не вернуть, получится `None`. Сообщение
уйдёт, но в логе останется мусор, а поведение при нескольких плагинах станет непредсказуемым.
