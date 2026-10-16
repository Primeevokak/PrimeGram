package vpn.sdk

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import vpn.tunnel.VpnTunnelState
import vpn.tunnel.VpnStatistics
import vpn.tunnel.TunnelFactory
import vpn.proxy.XrayProxy

/**
 * Единая точка входа для работы с VPN.
 *
 * Как юзать:
 * 1. [setup] — инициализация (один раз, при старте приложения).
 * 3. [setCustomVlessConfig] / [connectAwg] — подключение к серверу пользователя.
 * 4. [addStateListener] / [removeStateListener] — callback-наблюдение (Java).
 */
object VpnSDK {

    private const val TAG = "VpnSDK"

    fun interface StateListener {
        fun onStateChanged(state: VpnTunnelState)
    }

    fun interface AwgCallback {
        /** On the main thread: whether the tunnel came up. */
        fun onResult(ok: Boolean)
    }


    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        VpnSDK.logE(TAG, "Unhandled coroutine exception", throwable)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
    private val listeners = mutableSetOf<StateListener>()

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
        ConfigStore.init(appContext)
        TunnelFactory.setup(appContext)
        isInitialized = true
        VpnSDK.logD(TAG, "Initialized")
    }

    // ---- AmneziaWG (user's own config only) ----

    /** Set while a tunnel subscription is live; the backend itself is built on first use. */
    @Volatile
    private var tunnelSubscribed = false

    private fun ensureTunnelSubscription() {
        if (tunnelSubscribed) return
        tunnelSubscribed = true
        TunnelFactory.getTunnelManager().tunnelState
            .onEach { state -> notifyListeners(state) }
            .flowOn(Dispatchers.Main.immediate)
            .launchIn(scope)
    }

    /**
     * Last reason [connectAwg] failed (`invalid_config`, or the backend's message), null after a
     * successful start.
     */
    @Volatile
    @JvmStatic
    var lastAwgError: String? = null
        private set

    /**
     * Brings up an AmneziaWG tunnel from a config the user supplied (the `[Interface]`/`[Peer]`
     * text of a .conf). The tunnel is limited to [packageName] unless the config itself already
     * names included/excluded apps, so it carries this app's traffic and nothing else on the
     * device. The caller must already hold VPN consent (VpnService.prepare returned null).
     * Runs off the calling thread; [callback] gets the outcome on the main thread.
     */
    @JvmStatic
    fun connectAwg(rawConfig: String, packageName: String, callback: AwgCallback?) {
        checkInitialized()
        scope.launch {
            val ok = try {
                ensureTunnelSubscription()
                TunnelFactory.connectWithRawConfig(restrictToApp(rawConfig, packageName))
                // The backend retries starting its VpnService for up to ~10 s before giving up.
                val up = kotlinx.coroutines.withTimeoutOrNull(15_000L) {
                    TunnelFactory.getTunnelManager().tunnelState.first { it == VpnTunnelState.UP }
                } != null
                lastAwgError = if (up) null else "tunnel_not_up"
                up
            } catch (t: Throwable) {
                lastAwgError = t.message ?: t.javaClass.simpleName
                VpnSDK.logE(TAG, "AmneziaWG connect failed", t)
                false
            }
            withContext(Dispatchers.Main) { callback?.onResult(ok) }
        }
    }

    /** True for text that looks like a WireGuard/AmneziaWG config rather than a proxy link. */
    @JvmStatic
    fun isAwgConfig(text: String): Boolean =
        text.contains("[Interface]") && text.contains("[Peer]") && text.contains("PrivateKey")

    @JvmStatic
    fun disconnectAwg() {
        if (!isInitialized || !tunnelSubscribed) return
        scope.launch {
            try {
                TunnelFactory.disconnect()
            } catch (t: Throwable) {
                VpnSDK.logE(TAG, "AmneziaWG disconnect failed", t)
            }
        }
    }

    @JvmStatic
    fun isAwgUp(): Boolean = tunnelSubscribed && TunnelFactory.getTunnelManager().tunnelState.value == VpnTunnelState.UP

    private fun restrictToApp(rawConfig: String, packageName: String): String {
        if (Regex("""(?im)^\s*(IncludedApplications|ExcludedApplications)\s*=""").containsMatchIn(rawConfig)) {
            return rawConfig
        }
        return rawConfig.replaceFirst(Regex("""(?im)^\s*\[Interface\]\s*$"""), "[Interface]\nIncludedApplications = $packageName")
    }


    // ---- Proxy (VLESS+Reality via libxray) ----

    /**
     * Starts the local SOCKS5 proxy (xray-core) from the stored config - the one built from the
     * user's own link by [setCustomVlessConfig]. Nothing here ever fetches a config.
     *
     * Returns `true` if xray is now running (or was already running). Returns
     * `false` if no config is stored.
     *
     * If a cached config exists but libxray rejects it (corrupt cache, schema
     * drift, expired credentials in the JSON, etc.) the cache is cleared and
     * `false` is returned.
     */
    @JvmStatic
    fun startProxy(): Boolean {
        checkInitialized()
        val cached = ConfigStore.getXrayConfig()
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
        ConfigStore.clearXrayConfig()
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
                    val cached = ConfigStore.getXrayConfig()
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
                    val cached = ConfigStore.getXrayConfig()
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
        ConfigStore.setXrayConfig(json)
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
          "log": {"loglevel": "none", "access": "none"},
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
            // Only ever a real default for raw tcp - xray-core rejects `flow` outright on any
            // other transport (ws/grpc/xhttp/etc.), so a link that didn't specify one (this
            // server's xhttp link among them) must get an empty flow, not silently inherit tcp's.
            // Resolved once `type` is known, below.
            var flow: String? = null
            var spx = ""
            var wsHost = ""
            var wsPath = ""
            var grpcServiceName = ""
            var xhttpMode = ""
            var xhttpPath = ""
            var xhttpExtraRaw: String? = null

            if (questionIndex != -1) {
                val queryEnd = if (hashIndex != -1 && hashIndex > questionIndex) hashIndex else hostPortRest.length
                val query = hostPortRest.substring(questionIndex + 1, queryEnd)
                val params = query.split("&")
                for (param in params) {
                    // Splitting on every "=" (the old code's split("=") with a size==2 check)
                    // silently dropped any param whose own value contains one - not a risk for
                    // pbk/sid here, but exactly the shape "extra"'s URL-encoded JSON blob takes.
                    // indexOf finds only the first "=", which is the real key/value delimiter.
                    val eq = param.indexOf('=')
                    if (eq == -1) continue
                    val key = param.substring(0, eq)
                    // None of these were ever URL-decoded before - harmless for a plain host name
                    // like sni, but path=%2F and extra={...} arrive as literal percent-escapes
                    // that xray then tries to use as-is.
                    val value = try {
                        java.net.URLDecoder.decode(param.substring(eq + 1), "UTF-8")
                    } catch (ignored: Exception) {
                        param.substring(eq + 1)
                    }
                    when (key) {
                        "type" -> type = value
                        "security" -> security = value
                        "pbk" -> pbk = value
                        "sni" -> sni = value
                        "sid" -> sid = value
                        "fp" -> fp = value
                        "flow" -> flow = value
                        "spx" -> spx = value
                        "host" -> wsHost = value
                        "path" -> { wsPath = value; xhttpPath = value }
                        "serviceName" -> grpcServiceName = value
                        "mode" -> xhttpMode = value
                        "extra" -> xhttpExtraRaw = value
                    }
                }
            }

            val resolvedFlow = flow ?: if (type == "tcp") "xtls-rprx-vision" else ""

            // realitySettings used to be emitted unconditionally here regardless of what
            // `security` in the link actually said - a server on plain TLS (or none) got a
            // Reality handshake attempted against it anyway, with an empty publicKey/shortId to
            // boot, which fails instantly and looks from the outside like "connects, but no
            // traffic ever gets through" - ConnectionsManager retries forever against a config
            // that can never succeed. Only Reality gets realitySettings now; TLS gets a plain
            // tlsSettings block (still needs the SNI/fingerprint), and "none" gets neither.
            val securityBlock = when (security) {
                "reality" -> """, "realitySettings": {"publicKey": "$pbk", "shortId": "$sid", "serverName": "$sni", "fingerprint": "$fp"${if (spx.isNotEmpty()) """, "spiderX": "$spx"""" else ""}}"""
                "tls" -> """, "tlsSettings": {"serverName": "$sni", "fingerprint": "$fp"}"""
                else -> ""
            }

            // The transport (`type`) picks how the TCP stream itself is framed, independently of
            // TLS/Reality above - a network field of "ws"/"grpc"/"xhttp" with no matching settings
            // block used to be silently accepted by the JSON parser and then rejected (or, worse,
            // just never negotiated) by xray at connect time, the same "starts, never carries
            // traffic" failure the missing realitySettings branch caused. Only tcp truly needs
            // nothing else here.
            val transportBlock = when (type) {
                "ws" -> """, "wsSettings": {"path": "$wsPath", "headers": {"Host": "$wsHost"}}"""
                "grpc" -> """, "grpcSettings": {"serviceName": "$grpcServiceName"}"""
                "xhttp" -> {
                    val extraJson = xhttpExtraRaw?.let {
                        try {
                            org.json.JSONObject(it).toString()
                        } catch (ignored: Exception) {
                            null
                        }
                    }
                    """, "xhttpSettings": {"path": "$xhttpPath", "mode": "${xhttpMode.ifEmpty { "auto" }}"${if (extraJson != null) ""","extra": $extraJson""" else ""}}"""
                }
                else -> ""
            }

            return """
            {
              "log": {"loglevel": "none", "access": "none"},
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
                      "flow": "$resolvedFlow"
                    }]
                  }]
                },
                "streamSettings": {
                  "network": "$type",
                  "security": "$security"$securityBlock$transportBlock
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

    /** Drops the stored xray config (e.g. a key left over from the removed backend). */
    @JvmStatic
    fun forgetConfig() {
        checkInitialized()
        ConfigStore.clearXrayConfig()
    }

    /** True iff a config from the user's own link is stored. */
    @JvmStatic
    fun hasCachedXrayConfig(): Boolean {
        checkInitialized()
        return ConfigStore.getXrayConfig() != null
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

    private fun notifyListeners(state: VpnTunnelState) {
        val snapshot: List<StateListener>
        synchronized(listeners) { snapshot = listeners.toList() }
        snapshot.forEach { it.onStateChanged(state) }
    }

    private fun checkInitialized() {
        check(isInitialized) { "VpnSDK is not initialized. Call VpnSDK.setup(context) first." }
    }
}
