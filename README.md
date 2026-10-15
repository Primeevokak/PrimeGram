# PrimeGram

Неофициальный клиент Telegram для Android на основе исходного кода
[Telegram for Android](https://github.com/DrKLO/Telegram). Сетевой уровень дополнен механизмом обхода
блокировок по схеме [TgWs](https://github.com/Flowseal/tg-ws-proxy). Поддерживаются плагины на Python,
совместимые с exteraGram.

- Загрузки: [GitHub Releases](https://github.com/Primeevokak/PrimeGram/releases)
- Документация: [primeevokak.github.io/PrimeGram](https://primeevokak.github.io/PrimeGram/)
- Разработка плагинов: [руководство](https://primeevokak.github.io/PrimeGram/plugins/)

## Сведения о сборке

| Параметр | Значение |
|---|---|
| Имя пакета | `org.telegram.messenger.prime` |
| Минимальная версия Android | 7.0 (API 24) |
| Целевая версия Android | 16 (API 36) |
| Архитектуры | `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` |
| Основа | Telegram for Android 12.10.4 |
| Лицензия | GNU GPL v2 |

## Возможности

- **Подключение.** Встроенный механизм доступа к серверам Telegram при блокировках, не требующий
  стороннего ПО. Поддерживается подключение собственного туннеля на Cloudflare.
- **Передача файлов до 8 ГБ.** Файлы сверх ограничения Telegram (2 ГБ) передаются частями и собираются
  у получателя с PrimeGram.
- **Распознавание голосовых сообщений на устройстве.** Работает без Telegram Premium и без доступа к
  сети. Доступно пять моделей с разным соотношением скорости и точности.
- **Плагины на Python.** Форматы `.plugin`, `.elyx` и `.eaf`. API совместим с exteraGram; в состав
  входит набор предустановленных библиотек, перечень приведён в
  [документации](https://primeevokak.github.io/PrimeGram/plugins/).
- **Встроенный браузер** с вкладками, блокировкой рекламы и шифрованным DNS.
- **Лента каналов** — непрочитанные публикации подписок в едином списке.
- **Расширенный поиск** — по числовому идентификатору, номеру телефона или ссылке `t.me`.
- **Временные подписки** — автоматическая отписка от канала по истечении выбранного срока.
- **Диагностика** — журнал сбоев (включая нативные сбои и зависания приложения по данным системы),
  монитор производительности, трассировка холодного запуска.

## Сборка

### Требования

- JDK 17
- Android SDK: платформа 36, Build Tools 36.0.0
- Android NDK 27.2.12479018
- Python 3.11 (используется Chaquopy при сборке SDK плагинов)

### Команда сборки

```bash
git clone --recursive https://github.com/Primeevokak/PrimeGram.git
cd PrimeGram
./gradlew :TMessagesProj_AppStandalone:assembleAfatRelease
```

Результат: `TMessagesProj_AppStandalone/build/outputs/apk/afat/release/`.

При локальной сборке собирается только `arm64-v8a`. Все четыре архитектуры собираются при
`CI=true` (так работает сборка на GitHub Actions).

### Конфигурация

Параметры читаются из переменных окружения, а при их отсутствии — из файла `local.properties` в
корне проекта. Файл исключён из системы контроля версий.

| Параметр | Назначение |
|---|---|
| `PRIME_APP_ID`, `PRIME_APP_HASH` | Ключи Telegram API. Выдаются на [my.telegram.org](https://my.telegram.org). Без них используется общедоступная пара из репозитория. |
| `PRIME_KEYSTORE_FILE`, `PRIME_KEYSTORE_PASSWORD`, `PRIME_KEY_ALIAS`, `PRIME_KEY_PASSWORD` | Ключ подписи PrimeGram (см. раздел «Подпись»). |
| `PRIME_LINEAGE_FILE` | Файл цепочки ротации ключей подписи. |

Для работы push-уведомлений требуется собственный `google-services.json`: файл в репозитории
относится к другому проекту Firebase.

## Подпись

Релизы подписываются двумя ключами с ротацией по схеме APK Signature Scheme v3.1:

| Android | Сертификат | SHA-256 |
|---|---|---|
| 7.0 – 12 | исходный ключ | `A0:8D:7D:C3:23:DD:F7:1E:F3:20:19:44:39:7E:0D:3C:CE:7D:40:84:72:63:E1:1F:32:8B:68:BB:E1:92:29:AB` |
| 13 и выше | ключ PrimeGram | `24:8E:BA:BF:62:F2:38:7A:25:BA:7C:0F:00:BE:95:19:01:4D:24:4C:FD:84:2B:F6:7B:E4:8E:11:08:52:B2:5D` |

Подлинность загруженного APK проверяется командой:

```bash
apksigner verify --print-certs PrimeGram.apk
```

APK с иными сертификатами не является официальной сборкой PrimeGram.

## Выпуск и обновления

Сборка и публикация выполняются GitHub Actions (`.github/workflows/build-release.yml`) при создании
тега вида `v*`.

| Тег | Тип релиза | Имя файла |
|---|---|---|
| `v12.10.4.1` | стабильный | `PrimeGram-<abi>.apk` |
| `v12.10.5.0-beta1` | бета (GitHub pre-release) | `PrimeGram-<abi>-beta.apk` |

Приложение проверяет наличие обновлений через GitHub API. Бета-версии предлагаются только при
включённом бета-канале. Параметры задаются в разделе «Настройки PrimeGram → Обновления».

При выпуске бета-версии `APP_VERSION_NAME` должен содержать суффикс `-betaN`, а `APP_VERSION_CODE`
должен превышать код предыдущего релиза.

## Структура репозитория

| Каталог | Содержимое |
|---|---|
| `TMessagesProj/` | Основной модуль: Java-код, ресурсы, SDK плагинов (`src/main/python`) |
| `TMessagesProj/jni/` | Нативный код: сетевой уровень, медиа, сторонние библиотеки |
| `TMessagesProj_AppStandalone/` | Модуль приложения, конфигурация сборки и подписи |
| `plugins-sdk-reference/` | Описания интерфейсов SDK плагинов (`.pyi`) |
| `Tools/plugin_decompiler/` | Анализ плагинов: распаковка, байт-код, проверка совместимости |
| `Tools/plugin_compat/` | Проверка совместимости на наборе плагинов |
| `docs/` | Исходные файлы сайта документации |

## Благодарности

- [Telegram for Android](https://github.com/DrKLO/Telegram) — исходный код клиента.
- [Flowseal/tg-ws-proxy](https://github.com/Flowseal/tg-ws-proxy) — подход, положенный в основу
  сетевого уровня.

## Лицензия

Распространяется на условиях GNU General Public License v2, как и исходный клиент Telegram.
См. файл [LICENSE](LICENSE).
