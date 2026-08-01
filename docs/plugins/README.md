# Плагины

Плагин — это один файл на Python, который живёт внутри клиента. Он может перехватить сообщение до
отправки, ответить на событие, добавить пункт в меню и завести себе экран настроек.

API совместим с exteraGram, так что плагины оттуда обычно работают без правок. Отличия собраны
[в конце этой страницы](#чем-отличается-от-exteragram).

## Первый плагин

Сохраните это как `hello.plugin`, отправьте себе в «Избранное» и нажмите на файл в чате.

```python
__id__ = "hello_world"
__name__ = "Hello World"
__description__ = "Отвечает на команду .hello"
__author__ = "@username"
__version__ = "1.0.0"

from base_plugin import BasePlugin, HookResult, HookStrategy


class HelloPlugin(BasePlugin):

    def on_plugin_load(self):
        self.add_on_send_message_hook()

    def on_send_message_hook(self, account, params):
        if isinstance(params.message, str) and params.message.strip() == ".hello":
            params.message = "Привет из плагина 👋"
            return HookResult(strategy=HookStrategy.MODIFY)
        return HookResult()
```

Теперь напишите в любом чате `.hello` — уйдёт уже другой текст.

После установки плагин выключен — включите его переключателем в списке. В exteraGram так же, только
там окно установки сразу предлагает включить; у нас пока просто переключатель.

## Как устроен файл

Верхние строки с двойными подчёркиваниями — это манифест. Клиент читает их **до** того, как
выполнит файл, поэтому там должны быть обычные строки, а не выражения.

Обязательных всего два: `__id__` и `__name__`. Идентификатор — латиница, цифры и подчёркивания; по
нему хранятся настройки, так что менять его после выпуска плагина не стоит — пользователи потеряют
свои.

Остальное по вкусу: `__description__`, `__author__`, `__version__`, `__icon__`, а ещё
`__min_version__` — если плагину нужны возможности свежего клиента, укажите минимальную версию, и
на старом установка честно откажется вместо загадочной ошибки при запуске.

Если нужны библиотеки с PyPI, которых нет внутри:

```python
__requirements__ = ["emoji", "humanize>=4.0"]
```

Они скачаются при установке. Подробности и ограничения — на странице
[про библиотеки](libraries.md).

## Жизненный цикл

```python
class MyPlugin(BasePlugin):

    def on_plugin_load(self):
        # Плагин включили. Здесь регистрируют хуки.

    def on_plugin_unload(self):
        # Выключили или удалили. Здесь убирают за собой.
```

Плагин — синглтон. Второго экземпляра того же класса не появится, даже если очень постараться:
хуки и настройки привязаны к идентификатору, а не к объекту.

## Перехват отправки

Самый ходовой хук. Вызывается перед тем, как сообщение уйдёт на сервер, и может его изменить или
отменить.

```python
def on_plugin_load(self):
    self.add_on_send_message_hook()

def on_send_message_hook(self, account, params):
    return HookResult()
```

В `params` лежит то, что пользователь собрался отправить: `message` — текст (или `None`, если это
медиа), `peer` — куда, `path` — путь к файлу для медиа, `replyToMsg` — сообщение, на которое
отвечают.

Возвращаемый `HookResult` решает судьбу отправки. `DEFAULT` — не вмешиваться. `MODIFY` — отправить
изменённое. `CANCEL` — не отправлять вовсе. Есть ещё `MODIFY_FINAL`: то же, что `MODIFY`, но
остальные плагины уже не спросят.

Важно: **возвращайте `HookResult()` во всех остальных случаях.** Забытый `return` превращается в
`None`, и сообщение отправляется, но в логах остаётся мусор.

## Настройки

Экран настроек описывается списком, а рисует его клиент — темой, отступами и всем прочим
занимаемся мы, чтобы плагин выглядел частью приложения, а не гостем.

```python
from ui.settings import Header, Switch, Selector, Input, Text, Divider


def create_settings(self):
    return [
        Header(text="Основное"),
        Switch(
            key="enabled",
            text="Включить обработку",
            default=True,
            subtext="Появится под строкой мелким шрифтом",
        ),
        Selector(key="mode", text="Режим", default=0, items=["Быстрый", "Точный"]),
        Input(key="token", text="Ключ API", default=""),
        Divider(text="Пояснение внизу карточки"),
        Text(text="Сбросить всё", red=True, on_click=lambda ctx: self.reset()),
    ]
```

Значения читаются откуда угодно и когда угодно:

```python
mode = self.get_setting("mode", 0)
self.set_setting("mode", 1)
```

Ключи с виджетами связаны автоматически: переключатель сам пишет в `enabled`, вам остаётся только
это прочитать. Если нужно среагировать сразу, у каждого виджета есть `on_change`.

## Отправка

```python
import client_utils

client_utils.send_text(peer, "**жирный** и `моноширинный`", parse_mode="markdown")
client_utils.send_photo(peer, "/path/to/image.png", caption="подпись")
client_utils.send_document(peer, "/path/to/file.zip")
```

Если нужного помощника нет — можно отправить запрос напрямую, как это делает сам клиент:

```python
from org.telegram.tgnet import TLRPC

req = TLRPC.TL_messages_getHistory()
req.peer = client_utils.get_messages_controller().getInputPeer(peer)
req.limit = 20
client_utils.send_request(req, lambda response, error: self.log(str(response)))
```

Все контроллеры приложения доступны как есть: `get_messages_controller()`,
`get_send_messages_helper()`, `get_user_config()`, `get_file_loader()` и остальные. Это настоящие
Java-объекты клиента, а не обёртки, — то есть вам доступно всё, что умеет сам Telegram.

## Показать что-то пользователю

```python
from ui.bulletin import BulletinHelper
from android_utils import run_on_ui_thread

run_on_ui_thread(lambda: BulletinHelper.show_info("Готово"))
```

**Всё, что трогает интерфейс, оборачивайте в `run_on_ui_thread`.** Хуки приходят из сетевых
потоков, и попытка нарисовать что-нибудь оттуда роняет приложение целиком — не плагин, а всё
приложение. Это самая частая причина падений у начинающих.

Кроме `BulletinHelper` есть `AlertDialogBuilder` для диалогов — оба описаны
[в справочнике](api.md#интерфейс).

## Файлы

```python
import file_utils

path = file_utils.get_documents_dir() + "/report.txt"
file_utils.write_file(path, "содержимое")
```

Тут есть подвох, на который натыкаются все. Telegram **отказывается отправлять файлы из приватного
каталога приложения** — это защита от того, чтобы приложение случайно не отправило свои внутренние
данные. Поэтому если плагин что-то сгенерировал и собирается это отправить, кладите в
`get_documents_dir()`, а не в `get_cache_dir()`. Иначе получите «вложение не поддерживается» и
полдня поисков.

## Отладка

`self.log("что-то")` пишет в логи приложения. Включаются в **Настройки PrimeGram → Диагностика →
Подробные логи**.

Если плагин не загрузился, ошибка видна прямо в списке плагинов — с именем исключения. Обычно
этого хватает, чтобы понять, что случилось.

## Подмена методов

Всё, что описано выше, работает через двери, которые мы сделали заранее. Хуки методов снимают это
ограничение: плагин указывает на **любой** метод клиента и получает управление до него, после или
вместо него.

```python
from java import jclass

def on_plugin_load(self):
    cls = jclass("org.telegram.messenger.AndroidUtilities").getClass()
    self.hook_all_methods(cls, "formatFileSize",
                          after=lambda param: param.setResult("много"))
```

`before` видит аргументы и может их поменять; если выставить результат — оригинал не выполнится
вовсе. `after` видит и подменяет возвращённое. `MethodReplacement` заменяет метод целиком.

Работает без рута: клиент переписывает точки входа собственных методов внутри своего процесса.
Наружу не выходит ничего, ставить ничего не нужно.

Единственная оговорка — устройство. Библиотека умеет не все версии ART, и на редких прошивках
подмена может быть недоступна. Проверяется одной строкой:

```python
if not self.hooking_available:
    self.log("на этом устройстве хуки не работают")
```

Снимать хуки при выключении плагина не нужно, это делается за вас.

## Файлы и ссылки

Плагин может занять расширение — тогда нажатие на такой файл в чате уходит ему, а не системному
просмотрщику:

```python
from file_utils import FilesController

def on_plugin_load(self):
    self.add_file_hook(FilesController.FileInfo(
        ext="epub",
        on_click=lambda args: self.open_book(args.file, args.message),
    ))
```

И может ловить входящие intent'ы — то, чем поделились в приложение, или ссылку своей схемы:

```python
from base_plugin import IntentHookType
from intents import IntentsManager

def on_plugin_load(self):
    self.add_intent_hook(
        IntentsManager.HandlerInfo(
            callback=self.handle,
            scheme="myplugin",
        ),
        IntentHookType.BEFORE,
    )
```

`BEFORE` означает «до того, как клиент сам посмотрит на intent» — это то, что нужно, если вы
занимаете свою схему ссылок. `AFTER` достанется только то, что клиент не разобрал сам.

Одно расширение занимает один плагин: у файла не может быть двух владельцев. Регистрации снимаются
автоматически, когда плагин выключают.

## Чем отличается от exteraGram

Питон тот же — 3.11. Это осознанный выбор: плагины пишут под него, и уходить с него означало бы
сломать половину существующих.

По возможностям разницы больше нет: подмена методов, файловые и intent-хуки — всё на месте и с тем
же API. Единственное исключение — `HookFilter.Condition` с выражением на MVEL: интерпретатора у нас
нет, и такой фильтр не совпадает никогда. Остальные полтора десятка фильтров работают.

**Что есть сверх.** Библиотек заметно больше — сорок с лишним против нескольких, включая `pandas`,
`matplotlib`, `pillow`, полный набор криптографии и работу со звуком. Список
[здесь](libraries.md).

---

Дальше: [справочник API](api.md) · [библиотеки](libraries.md) · [рецепты](recipes.md)
