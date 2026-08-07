package vpn.sdk

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import vpn.tunnel.VpnTunnelState
import vpn.tunnel.VpnStatistics
import vpn.network.NetworkResult
import vpn.network.VpnNetworkFactory
import vpn.tunnel.TunnelFactory
import vpn.proxy.XrayProxy

/**
 * Единая точка входа для работы с VPN.
 *
 * Как юзать:
 * 1. [setup] — инициализация (один раз, при старте приложения).
 * 2. [updateConfig] — фоновое обновление конфига.
 * 3. [toggleConnection] — подключение / отключение VPN.
 * 4. [addStateListener] / [removeStateListener] — callback-наблюдение (Java).
 * 5. [fetchAppUpdate] - проверка доступности обновления
 */
object VpnSDK {

    private const val TAG = "VpnSDK"

    fun interface StateListener {
        fun onStateChanged(state: VpnTunnelState)
    }

    fun interface AppUpdateCallback {
        fun onResult(updateInfo: AppUpdateResult?)
    }

    fun interface RegisterCallback {
        /** Invoked on Main thread after [registerOrAuth] finishes (with all retries). */
        fun onResult(success: Boolean)
    }

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        VpnSDK.logE(TAG, "Unhandled coroutine exception", throwable)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
    private val listeners = mutableSetOf<StateListener>()
    private val registerMutex = Mutex()

    @Volatile
    private var isInitialized = false

    @JvmStatic
    var logListener: ((String) -> Unit)? = null
        set(value) {
            field = value
            XrayProxy.proxyLogger = value
        }

    @JvmStatic
    fun logD(tag: String, msg: String) {
        Log.d(tag, msg)
        logListener?.invoke("D/$tag: $msg")
    }

    @JvmStatic
    fun logE(tag: String, msg: String, t: Throwable? = null) {
        Log.e(tag, msg, t)
        logListener?.invoke("E/$tag: $msg" + (if (t != null) " - ${t.message}" else ""))
    }

    @JvmStatic
    fun logW(tag: String, msg: String) {
        Log.w(tag, msg)
        logListener?.invoke("W/$tag: $msg")
    }

    @JvmStatic
    val tunnelStateFlow: StateFlow<VpnTunnelState>
        get() {
            checkInitialized()
            return TunnelFactory.getTunnelManager().tunnelState
        }

    @JvmStatic
    fun getTunnelState(): VpnTunnelState {
        checkInitialized()
        return TunnelFactory.getTunnelManager().tunnelState.value
    }

    @JvmStatic
    fun addStateListener(listener: StateListener) {
        synchronized(listeners) { listeners.add(listener) }
    }

    @JvmStatic
    fun removeStateListener(listener: StateListener) {
        synchronized(listeners) { listeners.remove(listener) }
    }

    @Synchronized
    @JvmStatic
    @JvmOverloads
    fun setup(context: Context, debug: Boolean = false) {
        if (isInitialized) {
            VpnSDK.logD(TAG, "Already initialized")
            return
        }
        val appContext = context.applicationContext
        VpnNetworkFactory.setup(appContext, debug)
        TunnelFactory.setup(appContext)
        isInitialized = true
        VpnSDK.logD(TAG, "Initialized")

        // Off the startup path on purpose: touching the tunnel manager builds the AmneziaWG
        // Go backend, and this runs inside Application.onCreate. Subscribing a moment later
        // costs nothing — there is no tunnel to report on until the user starts one.
        scope.launch(Dispatchers.IO) {
            try {
                TunnelFactory.getTunnelManager().tunnelState
                    .onEach { state -> notifyListeners(state) }
                    .flowOn(Dispatchers.Main.immediate)
                    .launchIn(scope)
            } catch (t: Throwable) {
                VpnSDK.logE(TAG, "Tunnel state subscription failed: ${t.message}")
            }
        }
    }

    @JvmStatic
    fun updateConfig() {
        checkInitialized()
        scope.launch {
            VpnSDK.logD(TAG, "Fetching remote config…")
            when (val result = VpnNetworkFactory.getAppConfigRepository().fetchRemoteConfig()) {
                is NetworkResult.Success -> VpnSDK.logD(TAG, "Config updated")
                is NetworkResult.Error   -> VpnSDK.logE(TAG, "Config error: ${result.message}")
                is NetworkResult.Failure -> VpnSDK.logE(TAG, "Config failure, code=${result.code}")
            }
        }
    }

    @JvmStatic
    fun fetchAppUpdate(callback: AppUpdateCallback) {
        checkInitialized()
        scope.launch {
            VpnSDK.logD(TAG, "Fetching app update info…")
            when (val result = VpnNetworkFactory.getAppUpdateRepository().fetchUpdateInfo()) {
                is NetworkResult.Success -> {
                    val info = result.data
                    VpnSDK.logD(TAG, "App update info fetched: v${info.version}")
                    callback.onResult(
                        AppUpdateResult(
                            version = info.version,
                            versionCode = info.versionCode,
                            fileUrl = info.fileUrl,
                            changelog = info.changelog,
                            isRequired = info.isRequired,
                        )
                    )
                }
                is NetworkResult.Error -> {
                    VpnSDK.logE(TAG, "App update fetch error: ${result.message}")
                    callback.onResult(null)
                }
                is NetworkResult.Failure -> {
                    VpnSDK.logE(TAG, "App update fetch failure, code=${result.code}")
                    callback.onResult(null)
                }
            }
        }
    }

    @JvmStatic
    fun toggleConnection() {
        checkInitialized()
        when (tunnelStateFlow.value) {
            VpnTunnelState.DOWN -> scope.launch { connect() }
            VpnTunnelState.UP -> disconnect()
            VpnTunnelState.CONNECTING -> Unit
        }
    }

    @JvmStatic
    fun getStatistics(): VpnStatistics {
        checkInitialized()
        return TunnelFactory.getTunnelManager().getStatistics()
    }

    @JvmStatic
    fun getDebugInfo(): String {
        checkInitialized()
        val deviceId = VpnNetworkFactory.getDeviceIdRepository().getDeviceId()
        val clientIP = TunnelFactory.lastClientIP
        val serverIP = TunnelFactory.lastServerIP
        return buildString {
            append("deviceId = $deviceId")
            clientIP?.let { append("\nclientIP = $it") }
            serverIP?.let { append("\nserverIP = $it") }
            append("\n--- xray proxy ---")
            append("\nxray running = ${XrayProxy.isRunning()}")
            if (XrayProxy.isRunning()) {
                append("\nxray addr = ${XrayProxy.socksHost}:${XrayProxy.socksPort}")
            }
            XrayProxy.lastError?.let { append("\nxray error = $it") }
        }
    }

    private suspend fun connect() {
        VpnSDK.logD(TAG, "Connecting…")
        TunnelFactory.getTunnelManager().setState(VpnTunnelState.CONNECTING, null)

        val keyResult = VpnNetworkFactory.getVpnApi().getAnonymousKey()
        if (keyResult !is NetworkResult.Success) {
            VpnSDK.logW(TAG, "Failed to get anonymous key: $keyResult")
            TunnelFactory.getTunnelManager().setState(VpnTunnelState.DOWN, null)
            return
        }

        TunnelFactory.connectWithRawConfig(keyResult.data)
    }

    @JvmStatic
    fun disconnect() {
        if (!isInitialized) return
        VpnSDK.logD(TAG, "Disconnecting…")
        TunnelFactory.disconnect()
    }

    // ---- Proxy (VLESS+Reality via libxray) ----

    /**
     * Starts the local SOCKS5 proxy (xray-core) using the cached server-issued
     * config (from the last successful /auth/register).
     *
     * Returns `true` if xray is now running (or was already running). Returns
     * `false` if no cached config exists yet — the caller should kick off
     * [registerOrAuth] and retry once the callback fires.
     *
     * If a cached config exists but libxray rejects it (corrupt cache, schema
     * drift, expired credentials in the JSON, etc.) the cache is cleared and
     * `false` is returned, so the next register cycle will replace it.
     */
    @JvmStatic
    fun startProxy(): Boolean {
        checkInitialized()
        val cached = VpnNetworkFactory.getRegistrationRepository().getCachedConfigJson()
        if (cached == null) {
            VpnSDK.logD(TAG, "startProxy: no cached config; xray not started")
            return false
        }
        VpnSDK.logD(TAG, "Starting xray with cached server config (size=${cached.length})")
        if (XrayProxy.start(cached)) {
            startProxyWatchdog()
            return true
        }
        VpnSDK.logW(TAG, "Cached xray config failed to start, clearing cache: ${XrayProxy.lastError}")
        VpnNetworkFactory.getRegistrationRepository().clearCachedConfig()
        return false
    }

    private var watchdogJob: kotlinx.coroutines.Job? = null
    private const val WATCHDOG_INTERVAL_MS = 20_000L

    /**
     * Periodically verifies the local xray SOCKS5 port is actually accepting
     * connections (not just that libxray's internal flag says "started") and
     * restarts it from the cached config if it wedged. Without this, a dead
     * xray runtime looks identical to a healthy one to every caller of
     * [isProxyRunning], and nothing else in the app ever notices or recovers.
     */
    private fun startProxyWatchdog() {
        if (watchdogJob?.isActive == true) return
        watchdogJob = scope.launch {
            // Seed the cached health flag immediately: callers read the cache, and until the
            // first real check runs it would otherwise report a freshly started proxy as dead.
            XrayProxy.refreshHealth()
            while (true) {
                delay(WATCHDOG_INTERVAL_MS)
                if (!XrayProxy.isRunning()) {
                    // stopProxy() was called, or xray reported itself stopped — nothing to heal.
                    break
                }
                if (!XrayProxy.refreshHealth()) {
                    VpnSDK.logW(TAG, "Watchdog: xray unhealthy, restarting from cached config")
                    val cached = VpnNetworkFactory.getRegistrationRepository().getCachedConfigJson()
                    if (cached != null) {
                        XrayProxy.stop()
                        if (!XrayProxy.start(cached)) {
                            VpnSDK.logE(TAG, "Watchdog: restart failed: ${XrayProxy.lastError}")
                        }
                    } else {
                        VpnSDK.logW(TAG, "Watchdog: no cached config to restart from, stopping watchdog")
                        break
                    }
                }
            }
        }
    }

    private fun stopProxyWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = null
    }

    /**
     * Call when the underlying network changes (wifi↔mobile, Doze exit,
     * airplane mode toggle) so a wedged xray socket is caught immediately
     * instead of waiting up to [WATCHDOG_INTERVAL_MS] for the next tick.
     */
    @JvmStatic
    fun onNetworkChanged() {
        if (!isInitialized) return
        if (XrayProxy.isRunning()) {
            scope.launch {
                if (!XrayProxy.refreshHealth()) {
                    VpnSDK.logD(TAG, "onNetworkChanged: xray unhealthy after network change, restarting")
                    val cached = VpnNetworkFactory.getRegistrationRepository().getCachedConfigJson()
                    if (cached != null) {
                        XrayProxy.stop()
                        XrayProxy.start(cached)
                    }
                }
            }
        }
    }

    /**
     * Reason [setCustomVlessConfig] last returned `false`, distinguishing a
     * malformed vless:// link from one that parsed fine but the key/server
     * itself didn't work (expired key, unreachable server, etc.) — those are
     * different problems and telling the user the wrong one is worse than
     * saying nothing. `null` after a successful call.
     */
    @Volatile
    @JvmStatic
    var lastCustomVlessError: String? = null
        private set

    /**
     * Despite the name (kept so the existing Java call sites and the error-reason contract on
     * [lastCustomVlessError] don't have to change), this now accepts any of the four link schemes
     * a Happ-style server list holds - vless://, vmess://, trojan://, ss:// - and dispatches to
     * the matching parser below by scheme.
     */
    @JvmStatic
    fun setCustomVlessConfig(serverUrl: String): Boolean {
        checkInitialized()
        val json = when {
            serverUrl.startsWith("vless://") -> parseVlessUrlToJson(serverUrl)
            serverUrl.startsWith("vmess://") -> parseVmessUrlToJson(serverUrl)
            serverUrl.startsWith("trojan://") -> parseTrojanUrlToJson(serverUrl)
            serverUrl.startsWith("ss://") -> parseShadowsocksUrlToJson(serverUrl)
            serverUrl.startsWith("socks://") -> parseSocksUrlToJson(serverUrl)
            else -> null
        }
        if (json == null) {
            lastCustomVlessError = "invalid_url"
            return false
        }
        VpnNetworkFactory.getRegistrationRepository().setCustomConfigJson(json)
        if (isProxyRunning()) {
            stopProxy()
        }
        val ok = startProxy()
        lastCustomVlessError = if (ok) null else (XrayProxy.lastError ?: "connect_failed")
        return ok
    }

    /** The one bit every protocol's config shares - a local SOCKS5 inbound at the fixed port the
     *  rest of the app already knows to route MTProto through - wrapped around whichever
     *  protocol-specific outbound JSON a parser below produced. */
    private fun wrapOutbound(outbound: String): String = """
        {
          "log": {"loglevel": "warning"},
          "inbounds": [{
            "listen": "127.0.0.2",
            "port": 17808,
            "protocol": "socks",
            "settings": {"udp": true}
          }],
          "outbounds": [$outbound]
        }
        """.trimIndent()

    private fun parseVlessUrlToJson(url: String): String? {
        try {
            if (!url.startsWith("vless://")) return null
            val withoutScheme = url.substring("vless://".length)
            val atIndex = withoutScheme.indexOf('@')
            if (atIndex == -1) return null
            val uuid = withoutScheme.substring(0, atIndex)
            val hostPortRest = withoutScheme.substring(atIndex + 1)
            
            val questionIndex = hostPortRest.indexOf('?')
            val hashIndex = hostPortRest.indexOf('#')
            val endOfHostPort = if (questionIndex != -1) questionIndex else if (hashIndex != -1) hashIndex else hostPortRest.length
            
            val hostPort = hostPortRest.substring(0, endOfHostPort)
            val colonIndex = hostPort.lastIndexOf(':')
            if (colonIndex == -1) return null
            val host = hostPort.substring(0, colonIndex)
            val port = hostPort.substring(colonIndex + 1).toIntOrNull() ?: 443

            var type = "tcp"
            var security = "reality"
            var pbk = ""
            var sni = ""
            var sid = ""
            var fp = "chrome"
            var flow = "xtls-rprx-vision"

            if (questionIndex != -1) {
                val queryEnd = if (hashIndex != -1 && hashIndex > questionIndex) hashIndex else hostPortRest.length
                val query = hostPortRest.substring(questionIndex + 1, queryEnd)
                val params = query.split("&")
                for (param in params) {
                    val kv = param.split("=")
                    if (kv.size == 2) {
                        when (kv[0]) {
                            "type" -> type = kv[1]
                            "security" -> security = kv[1]
                            "pbk" -> pbk = kv[1]
                            "sni" -> sni = kv[1]
                            "sid" -> sid = kv[1]
                            "fp" -> fp = kv[1]
                            "flow" -> flow = kv[1]
                        }
                    }
                }
            }

            // realitySettings used to be emitted unconditionally here regardless of what
            // `security` in the link actually said - a server on plain TLS (or none) got a
            // Reality handshake attempted against it anyway, with an empty publicKey/shortId to
            // boot, which fails instantly and looks from the outside like "connects, but no
            // traffic ever gets through" - ConnectionsManager retries forever against a config
            // that can never succeed. Only Reality gets realitySettings now; TLS gets a plain
            // tlsSettings block (still needs the SNI/fingerprint), and "none" gets neither.
            val securityBlock = when (security) {
                "reality" -> """, "realitySettings": {"publicKey": "$pbk", "shortId": "$sid", "serverName": "$sni", "fingerprint": "$fp"}"""
                "tls" -> """, "tlsSettings": {"serverName": "$sni", "fingerprint": "$fp"}"""
                else -> ""
            }

            return """
            {
              "log": {"loglevel": "warning"},
              "inbounds": [{
                "listen": "127.0.0.2",
                "port": 17808,
                "protocol": "socks",
                "settings": {"udp": true}
              }],
              "outbounds": [{
                "protocol": "vless",
                "settings": {
                  "vnext": [{
                    "address": "$host",
                    "port": $port,
                    "users": [{
                      "id": "$uuid",
                      "encryption": "none",
                      "flow": "$flow"
                    }]
                  }]
                },
                "streamSettings": {
                  "network": "$type",
                  "security": "$security"$securityBlock
                }
              }]
            }
            """.trimIndent()
        } catch (e: Exception) {
            logE(TAG, "Failed to parse vless URL", e)
            return null
        }
    }

    /** `vmess://` carries no query string at all - the whole link body past the scheme is one
     *  base64 blob of JSON (the "vmess share standard" most clients, including this format's
     *  origin, v2rayN, agree on): `{"add","port","id","aid","net","type","host","path","tls",...}`. */
    private fun parseVmessUrlToJson(url: String): String? {
        try {
            val payload = url.substring("vmess://".length).substringBefore('#')
            val decoded = String(android.util.Base64.decode(payload, android.util.Base64.DEFAULT))
            val obj = org.json.JSONObject(decoded)
            val address = obj.getString("add")
            val port = obj.getInt("port")
            val uuid = obj.getString("id")
            val alterId = obj.optInt("aid", 0)
            val network = obj.optString("net", "tcp").ifEmpty { "tcp" }
            val tls = obj.optString("tls", "")
            val host = obj.optString("host", "")
            val path = obj.optString("path", "")
            val sni = obj.optString("sni", host)

            val streamSettings = buildString {
                append("""{"network": "$network"""")
                append(""", "security": "${if (tls == "tls") "tls" else "none"}"""")
                if (tls == "tls") {
                    append(""", "tlsSettings": {"serverName": "$sni"}""")
                }
                when (network) {
                    "ws" -> append(""", "wsSettings": {"path": "$path", "headers": {"Host": "$host"}}""")
                    "grpc" -> append(""", "grpcSettings": {"serviceName": "$path"}""")
                    "h2" -> append(""", "httpSettings": {"path": "$path", "host": ["$host"]}""")
                }
                append("}")
            }

            return wrapOutbound(
                """
                {
                  "protocol": "vmess",
                  "settings": {
                    "vnext": [{
                      "address": "$address",
                      "port": $port,
                      "users": [{"id": "$uuid", "alterId": $alterId, "security": "auto"}]
                    }]
                  },
                  "streamSettings": $streamSettings
                }
                """.trimIndent()
            )
        } catch (e: Exception) {
            logE(TAG, "Failed to parse vmess URL", e)
            return null
        }
    }

    /** `trojan://password@host:port?params#name` - closer to vless in shape than vmess is, since
     *  it's a plain URL rather than a base64 JSON blob. */
    private fun parseTrojanUrlToJson(url: String): String? {
        try {
            val withoutScheme = url.substring("trojan://".length)
            val atIndex = withoutScheme.lastIndexOf('@')
            if (atIndex == -1) return null
            val password = java.net.URLDecoder.decode(withoutScheme.substring(0, atIndex), "UTF-8")
            val hostPortRest = withoutScheme.substring(atIndex + 1)

            val questionIndex = hostPortRest.indexOf('?')
            val hashIndex = hostPortRest.indexOf('#')
            val endOfHostPort = if (questionIndex != -1) questionIndex else if (hashIndex != -1) hashIndex else hostPortRest.length
            val hostPort = hostPortRest.substring(0, endOfHostPort)
            val colonIndex = hostPort.lastIndexOf(':')
            if (colonIndex == -1) return null
            val host = hostPort.substring(0, colonIndex)
            val port = hostPort.substring(colonIndex + 1).toIntOrNull() ?: 443

            var sni = host
            var network = "tcp"
            if (questionIndex != -1) {
                val queryEnd = if (hashIndex != -1 && hashIndex > questionIndex) hashIndex else hostPortRest.length
                val query = hostPortRest.substring(questionIndex + 1, queryEnd)
                for (param in query.split("&")) {
                    val kv = param.split("=")
                    if (kv.size == 2) {
                        when (kv[0]) {
                            "sni", "peer" -> sni = kv[1]
                            "type" -> network = kv[1]
                        }
                    }
                }
            }

            return wrapOutbound(
                """
                {
                  "protocol": "trojan",
                  "settings": {
                    "servers": [{"address": "$host", "port": $port, "password": "$password"}]
                  },
                  "streamSettings": {
                    "network": "$network",
                    "security": "tls",
                    "tlsSettings": {"serverName": "$sni"}
                  }
                }
                """.trimIndent()
            )
        } catch (e: Exception) {
            logE(TAG, "Failed to parse trojan URL", e)
            return null
        }
    }

    /** `ss://` comes in two shapes depending on which client generated it: the whole
     *  `method:password@host:port` base64-encoded together (the original SIP002 predecessor,
     *  still common), or just `method:password` encoded with the host:port left in cleartext
     *  after the `@` (SIP002 proper). Both are tried, since there is nothing in the link itself
     *  that says which one it is. */
    private fun parseShadowsocksUrlToJson(url: String): String? {
        try {
            val withoutScheme = url.substring("ss://".length).substringBefore('#')

            var method: String
            var password: String
            var host: String
            var port: Int

            val atIndex = withoutScheme.lastIndexOf('@')
            if (atIndex != -1) {
                // SIP002: base64(method:password)@host:port
                val userInfo = String(android.util.Base64.decode(withoutScheme.substring(0, atIndex), android.util.Base64.DEFAULT))
                val colonIndex = userInfo.indexOf(':')
                if (colonIndex == -1) return null
                method = userInfo.substring(0, colonIndex)
                password = userInfo.substring(colonIndex + 1)

                val hostPort = withoutScheme.substring(atIndex + 1).substringBefore('?')
                val hostPortColon = hostPort.lastIndexOf(':')
                if (hostPortColon == -1) return null
                host = hostPort.substring(0, hostPortColon)
                port = hostPort.substring(hostPortColon + 1).toIntOrNull() ?: return null
            } else {
                // Legacy: everything after the scheme is base64(method:password@host:port)
                val decoded = String(android.util.Base64.decode(withoutScheme, android.util.Base64.DEFAULT))
                val atIndex2 = decoded.lastIndexOf('@')
                if (atIndex2 == -1) return null
                val userInfo = decoded.substring(0, atIndex2)
                val colonIndex = userInfo.indexOf(':')
                if (colonIndex == -1) return null
                method = userInfo.substring(0, colonIndex)
                password = userInfo.substring(colonIndex + 1)

                val hostPort = decoded.substring(atIndex2 + 1)
                val hostPortColon = hostPort.lastIndexOf(':')
                if (hostPortColon == -1) return null
                host = hostPort.substring(0, hostPortColon)
                port = hostPort.substring(hostPortColon + 1).toIntOrNull() ?: return null
            }

            return wrapOutbound(
                """
                {
                  "protocol": "shadowsocks",
                  "settings": {
                    "servers": [{"address": "$host", "port": $port, "method": "$method", "password": "$password"}]
                  }
                }
                """.trimIndent()
            )
        } catch (e: Exception) {
            logE(TAG, "Failed to parse shadowsocks URL", e)
            return null
        }
    }

    /**
     * host:port for any of the four supported link schemes, for a plain TCP-connect ping - a
     * server list needs to know "is this thing even reachable" without spinning up the whole
     * xray runtime for each entry, and every one of these formats carries the address in the
     * clear (or one base64 hop away) regardless of what's encrypted once a connection opens.
     * `null` for a link this SDK doesn't recognize at all.
     */
    @JvmStatic
    fun extractHostPort(url: String): vpn.sdk.HostPort? {
        return try {
            when {
                url.startsWith("vless://") || url.startsWith("trojan://") -> {
                    val scheme = if (url.startsWith("vless://")) "vless://" else "trojan://"
                    val withoutScheme = url.substring(scheme.length)
                    val atIndex = withoutScheme.lastIndexOf('@')
                    if (atIndex == -1) return null
                    val hostPortRest = withoutScheme.substring(atIndex + 1)
                    val end = hostPortRest.indexOfFirst { it == '?' || it == '#' }.let { if (it == -1) hostPortRest.length else it }
                    val hostPort = hostPortRest.substring(0, end)
                    val colonIndex = hostPort.lastIndexOf(':')
                    if (colonIndex == -1) return null
                    val port = hostPort.substring(colonIndex + 1).toIntOrNull() ?: return null
                    vpn.sdk.HostPort(hostPort.substring(0, colonIndex), port)
                }
                url.startsWith("vmess://") -> {
                    val payload = url.substring("vmess://".length).substringBefore('#')
                    val decoded = String(android.util.Base64.decode(payload, android.util.Base64.DEFAULT))
                    val obj = org.json.JSONObject(decoded)
                    vpn.sdk.HostPort(obj.getString("add"), obj.getInt("port"))
                }
                url.startsWith("ss://") -> {
                    val withoutScheme = url.substring("ss://".length).substringBefore('#')
                    val atIndex = withoutScheme.lastIndexOf('@')
                    val hostPort = if (atIndex != -1) {
                        withoutScheme.substring(atIndex + 1).substringBefore('?')
                    } else {
                        val decoded = String(android.util.Base64.decode(withoutScheme, android.util.Base64.DEFAULT))
                        decoded.substringAfterLast('@')
                    }
                    val colonIndex = hostPort.lastIndexOf(':')
                    if (colonIndex == -1) return null
                    val port = hostPort.substring(colonIndex + 1).toIntOrNull() ?: return null
                    vpn.sdk.HostPort(hostPort.substring(0, colonIndex), port)
                }
                url.startsWith("socks://") -> {
                    val withoutScheme = url.substring("socks://".length).substringBefore('#')
                    val atIndex = withoutScheme.lastIndexOf('@')
                    val hostPort = if (atIndex != -1) withoutScheme.substring(atIndex + 1) else withoutScheme
                    val colonIndex = hostPort.lastIndexOf(':')
                    if (colonIndex == -1) return null
                    val port = hostPort.substring(colonIndex + 1).toIntOrNull() ?: return null
                    vpn.sdk.HostPort(hostPort.substring(0, colonIndex), port)
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** `socks://base64(user:pass)@host:port#name`, or just `socks://host:port#name` when the
     *  server takes no auth - both shapes appear in the wild since a plain SOCKS5 proxy often
     *  has none. */
    private fun parseSocksUrlToJson(url: String): String? {
        try {
            val withoutScheme = url.substring("socks://".length).substringBefore('#')
            val atIndex = withoutScheme.lastIndexOf('@')

            var user: String? = null
            var pass: String? = null
            val hostPort: String

            if (atIndex != -1) {
                val userInfoRaw = withoutScheme.substring(0, atIndex)
                hostPort = withoutScheme.substring(atIndex + 1)
                try {
                    val decoded = String(android.util.Base64.decode(userInfoRaw, android.util.Base64.DEFAULT))
                    val colonIndex = decoded.indexOf(':')
                    if (colonIndex != -1) {
                        user = decoded.substring(0, colonIndex)
                        pass = decoded.substring(colonIndex + 1)
                    }
                } catch (ignored: Exception) {
                    // Not base64 - some generators put user:pass in cleartext before the @.
                    val colonIndex = userInfoRaw.indexOf(':')
                    if (colonIndex != -1) {
                        user = userInfoRaw.substring(0, colonIndex)
                        pass = userInfoRaw.substring(colonIndex + 1)
                    }
                }
            } else {
                hostPort = withoutScheme
            }

            val colonIndex = hostPort.lastIndexOf(':')
            if (colonIndex == -1) return null
            val host = hostPort.substring(0, colonIndex)
            val port = hostPort.substring(colonIndex + 1).toIntOrNull() ?: return null

            val usersJson = if (user != null && pass != null) {
                """, "users": [{"user": "$user", "pass": "$pass"}]"""
            } else {
                ""
            }

            return wrapOutbound(
                """
                {
                  "protocol": "socks",
                  "settings": {
                    "servers": [{"address": "$host", "port": $port$usersJson}]
                  }
                }
                """.trimIndent()
            )
        } catch (e: Exception) {
            logE(TAG, "Failed to parse socks URL", e)
            return null
        }
    }

    /** True iff a server-issued xray config is cached locally. */
    @JvmStatic
    fun hasCachedXrayConfig(): Boolean {
        checkInitialized()
        return VpnNetworkFactory.getRegistrationRepository().getCachedConfigJson() != null
    }

    @JvmStatic
    fun stopProxy() {
        VpnSDK.logD(TAG, "Stopping xray proxy…")
        stopProxyWatchdog()
        XrayProxy.stop()
    }

    @JvmStatic
    fun isProxyRunning(): Boolean = XrayProxy.isRunning()

    /**
     * Real liveness check (actually connects to the local SOCKS5 port), unlike
     * [isProxyRunning] which only reflects libxray's internal "did I start" flag
     * and can stay true after the runtime wedged. Callers that gate a fallback
     * or rotation decision on "is the emergency proxy usable right now" should
     * use this, not [isProxyRunning].
     */
    @JvmStatic
    fun isProxyHealthy(): Boolean = XrayProxy.isHealthyCached()

    /** Port of the local SOCKS5 proxy. Valid only when [isProxyRunning] returns true. */
    @JvmStatic
    fun getProxySocksPort(): Int = XrayProxy.socksPort

    @JvmStatic
    fun getProxySocksHost(): String = XrayProxy.socksHost

    @JvmStatic
    fun getProxyLastError(): String? = XrayProxy.lastError

    /**
     * Calls POST /auth/register against the backend, with [maxAttempts] tries
     * spaced by exponential backoff (1s, 2s, 4s, …). On success, caches the
     * returned xray config and v2rayKey, restarts the local proxy with the new
     * config, and invokes [callback] on the Main thread with `true`.
     *
     * On exhaustion of all attempts (or if xray fails to restart with the
     * new config), invokes [callback] with `false`. The caller is responsible
     * for any UI feedback. After `success=true` callers should also push the
     * new proxy address into ConnectionsManager via `setProxySettings(...)`
     * to apply it across active accounts at runtime.
     *
     * Concurrent invocations are serialised: while one attempt cycle is in
     * flight, additional calls are dropped silently (no callback is invoked).
     * This protects both the backend (no duplicate findOrCreate races) and
     * the local libxray native state (no concurrent stop/start).
     */
    @JvmStatic
    @JvmOverloads
    fun registerOrAuth(maxAttempts: Int = 3, callback: RegisterCallback? = null) {
        checkInitialized()
        scope.launch {
            if (!registerMutex.tryLock()) {
                VpnSDK.logD(TAG, "registerOrAuth already in flight, dropping duplicate call")
                return@launch
            }
            var success = false
            try {
                success = runRegisterWithRetries(maxAttempts)
                withContext(Dispatchers.Main) { callback?.onResult(success) }
            } finally {
                registerMutex.unlock()
            }
            if (success) {
                backgroundRetryJob?.cancel()
                backgroundRetryJob = null
            } else {
                scheduleBackgroundRetry()
            }
        }
    }

    private var backgroundRetryJob: kotlinx.coroutines.Job? = null
    private const val BACKGROUND_RETRY_INITIAL_MS = 30_000L
    private const val BACKGROUND_RETRY_MAX_MS = 10 * 60_000L

    /**
     * [registerOrAuth]'s own retries are a short burst (seconds) meant to
     * ride out a blip while the caller is waiting on a callback. If the
     * backend is down/unreachable for longer than that, the previous
     * behaviour was to give up silently until the user manually reopened
     * settings and toggled something — leaving them without a working key
     * indefinitely. This keeps trying in the background with a slow backoff
     * until it succeeds or the user explicitly disables the proxy.
     */
    private fun scheduleBackgroundRetry() {
        if (backgroundRetryJob?.isActive == true) return
        backgroundRetryJob = scope.launch {
            var delayMs = BACKGROUND_RETRY_INITIAL_MS
            while (true) {
                delay(delayMs)
                VpnSDK.logD(TAG, "Background retry of registerOrAuth after earlier failure")
                val success = registerMutex.withLock { runRegisterWithRetries(2) }
                if (success) {
                    VpnSDK.logD(TAG, "Background retry succeeded")
                    return@launch
                }
                delayMs = (delayMs * 2).coerceAtMost(BACKGROUND_RETRY_MAX_MS)
            }
        }
    }

    private suspend fun runRegisterWithRetries(maxAttempts: Int): Boolean {
        var delayMs = 1000L
        var lastResult: NetworkResult<Unit>? = null
        for (attempt in 1..maxAttempts) {
            VpnSDK.logD(TAG, "registerOrAuth attempt $attempt/$maxAttempts")
            val result = VpnNetworkFactory.getRegistrationRepository().register()
            lastResult = result
            if (result is NetworkResult.Success) {
                // API success means a fresh config is in cache; we still
                // need libxray to accept it. If the restart fails, the whole
                // operation is a failure — don't pretend otherwise to the
                // caller, who will then advance MTProto onto a dead proxy.
                return applyCachedConfigToXray()
            }
            if (attempt < maxAttempts) {
                delay(delayMs)
                delayMs *= 2
            }
        }
        VpnSDK.logW(TAG, "registerOrAuth exhausted $maxAttempts attempts: $lastResult")
        return false
    }

    private fun applyCachedConfigToXray(): Boolean {
        val configJson = VpnNetworkFactory.getRegistrationRepository().getCachedConfigJson()
        if (configJson == null) {
            VpnSDK.logW(TAG, "applyCachedConfigToXray: no cached config (unexpected after success)")
            return false
        }
        XrayProxy.stop()
        val ok = XrayProxy.start(configJson)
        if (!ok) {
            VpnSDK.logW(TAG, "Failed to restart xray with new config: ${XrayProxy.lastError}")
        } else {
            VpnSDK.logD(TAG, "Xray restarted with fresh server config")
            startProxyWatchdog()
        }
        return ok
    }

    private fun notifyListeners(state: VpnTunnelState) {
        val snapshot: List<StateListener>
        synchronized(listeners) { snapshot = listeners.toList() }
        snapshot.forEach { it.onStateChanged(state) }
    }

    private fun checkInitialized() {
        check(isInitialized) { "VpnSDK is not initialized. Call VpnSDK.setup(context) first." }
    }
}
