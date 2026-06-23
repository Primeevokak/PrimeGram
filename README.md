# PrimeGram

*🇺🇸 Scroll down for English | 🇷🇺 Русская версия ниже*

---

## 🇺🇸 English

**PrimeGram** is an advanced, highly optimized fork of the official [Telegram App for Android](https://play.google.com/store/apps/details?id=org.telegram.messenger). 
Our primary focus is delivering maximum performance, reducing battery consumption, seamlessly bypassing censorship, and providing a clean, extended user experience without aggressive tracking.

### 🔥 Key Features

* **Built-in Anti-Censorship:** Includes an integrated proxy service that allows you to connect automatically, even in heavily restricted networks.
* **Smart Channel Feed ("The Wall"):** A custom feed that combines all unread posts from your favorite channels into a single, unified timeline.
* **In-app Browser:** A fast, integrated browser with customizable search engines (Google, etc.) so you don't have to leave the app to read articles or open links.
* **Battery & Performance Optimization:** Video decoding and heavy UI blur effects are fully hardware-accelerated. This drastically lowers CPU usage, saves battery life, and prevents your device from overheating.
* **Super-Fast Networking:** Massive file downloads and heavy media loading will no longer freeze the app thanks to our optimized multi-threaded network engine.
* **120fps Smooth Scrolling:** We've patched various layout bottlenecks from the original Telegram app to ensure buttery-smooth scrolling in chats.
* **Enhanced Privacy:** We removed aggressive permission requests on startup. Furthermore, your authorization tokens are **no longer** backed up to the Google Cloud.
* **Built-in Auto-Updater:** Automatically fetches and installs the latest PrimeGram updates directly from GitHub.
* **Custom Sidebar:** A tailored navigation sidebar for quicker access to your favorite features.

### 🛠 Compilation Guide
You will require Android Studio, Android NDK, and Android SDK.
1. Clone the repository.
2. Setup your `release.keystore`, `google-services.json`, and `BuildVars.java`.
3. Run the custom build script (`.\build_primegram.bat`) or compile manually via Gradle.

---

## 🇷🇺 Русский

**PrimeGram** — это продвинутый и глубоко оптимизированный форк официального [клиента Telegram для Android](https://play.google.com/store/apps/details?id=org.telegram.messenger).
Наша главная цель: максимальная производительность, экономия заряда батареи, бесшовный обход блокировок и расширенный функционал без слежки и назойливых уведомлений.

### 🔥 Главные фичи

* **Встроенный обход блокировок:** Интегрированный прокси-сервис позволяет приложению автоматически обходить цензуру и подключаться даже в сетях с жесткими ограничениями.
* **Умная лента ("Стена"):** Удобная лента, объединяющая все непрочитанные посты из ваших подписок в один общий скролл. Больше не нужно прыгать по десяткам каналов!
* **Встроенный браузер:** Быстрый внутренний браузер с возможностью выбора поисковика. Открывайте ссылки и читайте статьи, не покидая мессенджер.
* **Оптимизация батареи и видео:** Воспроизведение тяжелых видеороликов и эффекты размытия интерфейса теперь обрабатываются напрямую видеочипом (аппаратное ускорение). Телефон больше не греется, а заряд батареи экономится.
* **Молниеносная загрузка файлов:** Благодаря переписанному сетевому движку, скачивание сотен файлов и тяжелого кэша больше не заставляет приложение зависать.
* **Идеально плавный интерфейс:** Мы исправили системные баги оригинального Telegram, вызывавшие просадки кадров. Теперь скроллинг в чатах работает стабильно и плавно (вплоть до 120fps).
* **Улучшенная приватность:** Отключены агрессивные запросы разрешений при старте приложения. Токены авторизации **больше не копируются** в облако Google (Cloud Backup полностью вырезан).
* **Встроенное автообновление:** Приложение само проверяет, скачивает и устанавливает новые версии PrimeGram напрямую с GitHub.
* **Кастомное боковое меню:** Переработанная навигация для самого быстрого доступа ко всем важным разделам.

### 🛠 Инструкция по сборке
Вам понадобится Android Studio, Android NDK и Android SDK.
1. Склонируйте репозиторий.
2. Настройте `release.keystore`, `google-services.json` и `BuildVars.java`.
3. Запустите скрипт сборки (`.\build_primegram.bat`) или скомпилируйте проект вручную через Gradle.
