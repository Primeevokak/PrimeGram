# vpn/proxy — xray-core как локальный SOCKS5

Модуль запускает [xray-core](https://github.com/XTLS/Xray-core) внутри приложения как локальный SOCKS5-прокси (`127.0.0.1:17808`) и маршрутизирует через него **весь трафик Telegram — сообщения и звонки**. Без `VpnService`, параллельно с встроенным AWG-туннелем или вместо него.

## Зачем

Пользователи не хотят занимать VPN-слот Android ради Telegram. MTProto proxy умеет только TCP, звонки (WebRTC) требуют UDP — нужен единый прокси, который умеет и то и другое. VLESS+XTLS-Vision через xray-core закрывает оба канала.

## Как устроен

```
┌──────────────────────────┐
│  Telegram (TMessagesProj)│
│                          │
│  MTProto  ┐              │
│  (TCP)    │              │
│           ▼              │
│  ConnectionsManager ─────┼───► SOCKS5 CONNECT (xray)
│                          │           │
│  VoIP (tgcalls v2)       │           │  VLESS+XTLS
│    TURN/Reflector (UDP)  │           │  ▼
│    ↓                     │      ┌────────────┐
│  AsyncSocksProxyUdp ─────┼───► │ xray-core  │──► internet
│  (SOCKS5 UDP ASSOCIATE)  │      │ VPS (SE)   │
└──────────────────────────┘      └────────────┘
```

Одна и та же TCP-сессия до VPS — и для MTProto (sendMessage/sendFiles), и для UDP-звонков (через UDP-in-VLESS encapsulation). Никакого VPN-слота, весь трафик Telegram виден провайдеру как TLS к VLESS-серверу (адрес и SNI выдаются бэкендом per-device).

## Компоненты

### Android-сторона

| Файл | Назначение |
|------|------------|
| `vpn/proxy/src/main/kotlin/vpn/proxy/XrayProxy.kt` | Kotlin-обёртка над libxray (gomobile AAR). Запуск/остановка, `isRunning()`. Конфиг приходит снаружи — захардкоженного fallback больше нет. |
| `vpn/proxy/libs/libXray.aar` | Скомпилированный xray-core (gomobile bind). Хранится в git LFS. |
| `vpn/sdk/src/main/kotlin/vpn/sdk/VpnSDK.kt` | Публичный API: `startProxy()` (cache-only), `hasCachedXrayConfig()`, `registerOrAuth()`, `isProxyRunning()`, `getProxySocksHost/Port()`. |

### Интеграция с Telegram

| Файл | Что делает |
|------|-----------|
| `TMessagesProj/src/main/java/org/telegram/messenger/ApplicationLoader.java` | При старте приложения: `VpnSDK.startProxy()` синхронно, затем пишет `proxy_ip=127.0.0.1, proxy_port=17808, proxy_enabled=true` в `mainconfig` SharedPrefs до `ConnectionsManager.init()`. |
| `TMessagesProj/src/main/java/org/telegram/messenger/voip/VoIPService.java` | При `initiateActualEncryptedCall()`: гарантирует запуск xray, подставляет `Instance.Proxy("127.0.0.1", 17808)` в `tgcalls`-дескриптор вместо пользовательского MTProto-прокси. |

### Патч WebRTC для UDP-звонков

Звонки Telegram используют UDP reflector/TURN на `91.108.0.0/16:598/599/1400`. Эти эндпоинты **не принимают TCP** — только UDP. В штатном WebRTC есть `PROXY_SOCKS5` enum, но реализации SOCKS5-адаптера нет (только HTTPS). Мы её добавили:

| Файл | Что делает |
|------|-----------|
| `TMessagesProj/jni/voip/webrtc/rtc_base/socket_adapters.h/.cc` | Класс `AsyncSocksProxyUdpSocket` — реализация [RFC 1928 §7 UDP ASSOCIATE](https://www.rfc-editor.org/rfc/rfc1928). |
| `TMessagesProj/jni/voip/tgcalls/v2/NativeNetworkingImpl.cpp` | `WrappedBasicPacketSocketFactory::CreateUdpSocket` — если задан SOCKS-прокси, возвращает `AsyncSocksProxyUdpSocket` вместо обычного `AsyncUDPSocket`. |

#### Как работает `AsyncSocksProxyUdpSocket`

Наследуется от `rtc::AsyncPacketSocket`, снаружи неотличим от обычного UDP-сокета. Внутри:

1. **Инициализация** (в конструкторе):
   - Создаёт реальный UDP-сокет и биндит к локальному адресу.
   - Открывает TCP control-канал к xray (`127.0.0.1:17808`).
2. **Handshake** (асинхронно, state machine):
   - `SS_HELLO` — шлёт `05 01 00` (VER=5, NMETHODS=1, NO-AUTH), ждёт `05 00`.
   - `SS_ASSOCIATE` — шлёт `05 03 00 01 0.0.0.0:0` (UDP ASSOCIATE с wildcard dst), получает `05 00 00 ATYP BND.ADDR BND.PORT` — адрес UDP-relay на xray.
   - `SS_READY` — эмитит `SignalAddressReady`, сокет готов к использованию.
3. **`SendTo(addr, data)`**: prepend SOCKS5 UDP header (`RSV FRAG ATYP ADDR PORT`), шлёт на `BND.ADDR:BND.PORT` через реальный UDP-сокет.
4. **Incoming packet**: парсит SOCKS5 UDP header пришедшего пакета (приходит только от relay endpoint), достаёт истинный источник, передаёт наверх через `NotifyPacketReceived` с реальным `source_address`.
5. **Закрытие control TCP** → relay инвалидируется, сокет переходит в ST_ERROR.

Упрощения:
- Только no-auth (`xray` настроен `"auth":"noauth"` на inbound).
- Только ATYP=0x01 (IPv4) и 0x04 (IPv6); domain-ATYP от сервера не принимается (ICE отдаёт уже разрешённые адреса).
- Фрагментированные UDP-датаграммы (`FRAG != 0`) дропаются.

## MTProto через прокси

`ApplicationLoader.java` записывает стандартные Telegram-настройки прокси ещё до инициализации `ConnectionsManager`:

```java
proxyPrefs.putString("proxy_ip", VpnSDK.getProxySocksHost());
proxyPrefs.putInt("proxy_port", VpnSDK.getProxySocksPort());
proxyPrefs.putBoolean("proxy_enabled", true);
proxyPrefs.putBoolean("proxy_enabled_calls", true);
proxyPrefs.commit();  // синхронно!
```

Далее `ConnectionsManager` сам подключается к `127.0.0.1:17808` через стандартный механизм SOCKS5 — ничего в нативном слое Telegram менять не нужно.

## Конфигурация

VLESS-конфиг выдаётся бэкендом через `POST /auth/register` (см. `vpn/network/RegisterApi.kt`), кэшируется в `vpn_sdk_prefs` и используется до следующего успешного `register`. Если кэшированный JSON роняет libxray — он чистится, и SDK заново идёт за свежим конфигом.

Поток на старте приложения (`ApplicationLoader.onCreate`):

1. `VpnSDK.startProxy()` — пробует поднять xray из кэша. На успех — пишет prefs и возвращает true.
2. Если кэша нет (первый запуск или кэш сброшен) — `proxy_enabled=false` пишется в `mainconfig`, MTProto уходит напрямую, в фоне идёт `registerOrAuth(3)`. На успех колбэк делает `applyXrayProxyToConnectionsManager()` который и пишет prefs, и хотсвопит `setProxySettings`.
3. На экране ввода телефона `LoginActivity` гейтит «Continue» если кэш так и не появился — синхронно с прогрессом дожимает `registerOrAuth(2)`.

Локальный SOCKS5: `127.0.0.1:17808` с `"udp": true` (нужно для UDP ASSOCIATE). Порт фиксирован в `XrayProxy.SOCKS_PORT`.

## Сборка libXray.aar

`libXray.aar` — это [xtls/libxray](https://github.com/xtls/libxray) собранный через gomobile. Сборка описана в корневом `Makefile`:

```bash
make aar                    # собрать и положить в vpn/proxy/libs/libXray.aar
make apk-debug              # собрать debug APK (зависит от aar)
```

Нужно: Go 1.22+, gomobile, Android SDK/NDK. Полный цикл — несколько минут.

Файл коммитится в репозиторий через **git LFS** (95 МБ); см. `.gitattributes`.

## Известные ограничения

- **VLESS outbound на сервере должен быть в локации, откуда проходит трафик к Telegram** — у нас Stockholm (Play2Go) ok.
- **Flow `xtls-rprx-vision`** оптимизирован для TLS-трафика и работает корректно для наших кейсов (UDP-in-VLESS и MTProto TLS), но теоретически может конфликтовать с произвольным бинарным TCP.
- Звонки всё равно используют **host candidates** (локальный IP устройства) через обёртку — пакеты уходят на нашу локальную UDP-обёртку, пересылаются через SOCKS5 UDP в xray. Peer-to-peer пакеты (если бы Telegram не запрещал P2P с не-контактами) также шли бы через xray.
- Control TCP-канал на каждый UDP-сокет: при звонке открывается ~6–10 TCP-сессий к xray. Для xray это дёшево, но нагрузка учитывается.

## Проверка работы

1. В VoIP-логе (Settings → Debug → Share VoIP logs) ищите:
   ```
   AsyncSocksProxyUdpSocket: relay ready at 127.0.0.1:17808
   Channel writable for the first time
   ```
2. В общем логе:
   ```
   xray proxy started on 127.0.0.1:17808
   VoIP: using xray SOCKS proxy at 127.0.0.1:17808, forceTcp=false
   ```
3. На VPS в `/var/log/xray/access.log`:
   ```
   from tcp:<your-ip>:NNNN accepted udp:91.108.x.x:1400 [direct]
   ```
   — это TURN-трафик звонка через ваш прокси. Если `udp:` нет, а только `tcp:` — WebRTC ушёл мимо.
