# Библиотеки

Всё перечисленное здесь уже лежит внутри приложения. Ставить не нужно, качать не нужно, работает
без интернета — просто `import`.

Список заметно шире, чем в exteraGram, и это одна из причин, по которой стоит писать плагины
именно здесь.

## Сеть и разбор

`requests` · `httpx` · `aiohttp` · `beautifulsoup4` · `lxml` · `regex` · `brotli` ·
`python-dateutil` · `packaging` · `pyyaml`

```python
import requests

rate = requests.get("https://api.exchangerate-api.com/v4/latest/USD", timeout=10).json()
self.log("доллар: %s" % rate["rates"]["RUB"])
```

Сетевые вызовы блокирующие — не делайте их в хуке отправки, иначе интерфейс замрёт до ответа
сервера. Уводите в фон через `client_utils.run_on_queue`.

## Изображения

`pillow` · `pyzbar` · `matplotlib` · `wordcloud`

```python
from PIL import Image, ImageDraw

image = Image.new("RGB", (400, 200), "white")
ImageDraw.Draw(image).text((20, 90), "Привет", fill="black")
image.save(file_utils.get_documents_dir() + "/hello.png")
```

`pyzbar` читает QR-коды и штрихкоды с картинки — удобно для плагина, который распознаёт код на
присланном фото.

## Данные и вычисления

`numpy` · `pandas` · `pywavelets` · `editdistance` · `bitarray` · `lru-dict` · `cytoolz`

`editdistance` считает расстояние Левенштейна — на нём удобно делать поиск с опечатками или
угадывание команды, которую пользователь написал неточно.

## Звук

`miniaudio` · `soxr` · `lameenc`

Декодирование, передискретизация и кодирование в MP3. Этого хватает, чтобы обработать голосовое
сообщение целиком, не выходя из Python.

## Криптография

`cryptography` · `pycryptodome` · `pynacl` · `bcrypt` · `tgcrypto` · `argon2-cffi`

## Сжатие и система

`zstandard` · `lz4` · `psutil` · `netifaces` · `ephem` · `markupsafe` · `greenlet`

`ephem` — астрономия: фазы луны, восходы, положения планет. Попало сюда, потому что плагины с
гороскопами и напоминаниями о закате пишут чаще, чем можно предположить.

---

## График прямо в чат

Частая задача: плагин что-то посчитал и хочет это показать. Показать в чате можно только
картинкой.

```python
import matplotlib
matplotlib.use("Agg")           # обязательно: экрана у нас нет
import matplotlib.pyplot as plt
import os, file_utils, client_utils


def send_chart(peer, values):
    plt.figure(figsize=(6, 3))
    plt.plot(values, linewidth=2)
    plt.grid(alpha=0.3)

    path = os.path.join(file_utils.get_documents_dir(), "chart.png")
    plt.savefig(path, dpi=140, bbox_inches="tight")
    plt.close()

    client_utils.send_photo(peer, path, caption="Готово")
```

Строка `matplotlib.use("Agg")` не украшение: без неё matplotlib попытается найти оконную систему,
не найдёт и уронит плагин.

---

## Свои библиотеки

Чего нет внутри — плагин может доставить сам:

```python
__requirements__ = ["emoji", "humanize>=4.0"]
```

Пакеты скачиваются с PyPI при установке плагина, кладутся в каталог плагинов и не трогают само
приложение. Список установленного и кнопка «очистить» — в разделе плагинов в настройках.

**Работает это только для чистого Python.** Пакет, который нужно компилировать, так поставить
нельзя: компилятора на телефоне нет. Если библиотека нужна, а компилируется — просите добавить её
в сборку.

## Чего нет и не будет

`scipy` · `opencv-python` · `ujson` · `soundfile` · `coincurve` · `ta-lib` · `shapely` ·
`marisa-trie` · `tflite-runtime`

Причина одна на всех: готовых сборок под Android для Python 3.11 не существует — они
останавливаются на 3.10 или 3.8. Собирать их самим означало бы поддерживать собственный форк
каждой, а уходить на старый Python — сломать совместимость с плагинами exteraGram, которые пишут
под 3.11.

Для части задач есть замены. Вместо `opencv` — `pillow` плюс `numpy`, если нужна обработка
изображений без компьютерного зрения. Вместо `ujson` — встроенный `json`, он давно быстрый.
Вместо `soundfile` — `miniaudio`.
