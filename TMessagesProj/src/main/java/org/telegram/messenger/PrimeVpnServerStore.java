package org.telegram.messenger;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * PrimeGram: the list of VLESS/VMess/Trojan/Shadowsocks/SOCKS servers and AmneziaWG configs a
 * person has added by hand -
 * what {@link VpnSDK}'s own cache (one config, last one wins) has no room for. Selecting an entry
 * here is what actually reaches VpnSDK.setCustomVlessConfig; this class only remembers what was
 * typed in and which one is meant to be active, the way a Happ-style client's server list does.
 */
public final class PrimeVpnServerStore {

    private static final String KEY_SERVERS = "primegram_vpn_servers";
    private static final String KEY_ACTIVE = "primegram_vpn_active_server";

    /** An AmneziaWG/WireGuard config: {@link Server#rawUrl} holds the whole .conf text. */
    public static final String PROTOCOL_AWG = "awg";

    public static final class Server {
        public final String id;
        public final String name;
        public final String protocol;
        public final String rawUrl;
        public final long addedAt;
        /** Set by a ping check, not persisted - -1 means "never checked", -2 means "unreachable". */
        public long lastPingMs = -1;

        Server(String id, String name, String protocol, String rawUrl, long addedAt) {
            this.id = id;
            this.name = name;
            this.protocol = protocol;
            this.rawUrl = rawUrl;
            this.addedAt = addedAt;
        }
    }

    private PrimeVpnServerStore() {
    }

    private static SharedPreferences prefs() {
        return MessagesController.getGlobalMainSettings();
    }

    public static List<Server> getServers() {
        final List<Server> result = new ArrayList<>();
        try {
            final JSONArray arr = new JSONArray(prefs().getString(KEY_SERVERS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                final JSONObject o = arr.getJSONObject(i);
                result.add(new Server(o.getString("id"), o.optString("name", ""),
                        o.optString("protocol", "vless"), o.getString("url"), o.optLong("addedAt", 0)));
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        return result;
    }

    private static void save(List<Server> servers) {
        final JSONArray arr = new JSONArray();
        try {
            for (Server s : servers) {
                final JSONObject o = new JSONObject();
                o.put("id", s.id);
                o.put("name", s.name);
                o.put("protocol", s.protocol);
                o.put("url", s.rawUrl);
                o.put("addedAt", s.addedAt);
                arr.put(o);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        prefs().edit().putString(KEY_SERVERS, arr.toString()).apply();
    }

    /** The scheme off the front of the link (`vless`, `vmess`, `trojan`, `ss`) - what's actually
     *  supported for connecting is narrower than what can be stored and shown in the list. */
    public static String detectProtocol(String url) {
        if (url == null) {
            return "unknown";
        }
        if (vpn.sdk.VpnSDK.isAwgConfig(url)) {
            return PROTOCOL_AWG;
        }
        final int idx = url.indexOf("://");
        return idx > 0 ? url.substring(0, idx) : "unknown";
    }

    public static Server addServer(String name, String rawUrl) {
        final List<Server> servers = getServers();
        final String protocol = detectProtocol(rawUrl);
        final String id = UUID.randomUUID().toString();
        final String finalName = (name == null || name.trim().isEmpty())
                ? (protocol.toUpperCase(java.util.Locale.ROOT) + " " + (servers.size() + 1))
                : name.trim();
        final Server s = new Server(id, finalName, protocol, rawUrl, System.currentTimeMillis());
        servers.add(s);
        save(servers);
        return s;
    }

    public static void removeServer(String id) {
        final List<Server> servers = getServers();
        for (int i = 0; i < servers.size(); i++) {
            if (servers.get(i).id.equals(id)) {
                servers.remove(i);
                break;
            }
        }
        save(servers);
        if (id.equals(getActiveServerId())) {
            setActiveServerId(null);
        }
    }

    /** The selected server, or null when none is (or the selected one was deleted). */
    private static final String KEY_LAST = "primegram_vpn_last_server";

    /** The server that was on before the switch was turned off - what turning it on again connects to. */
    public static Server getLastServer() {
        final String id = prefs().getString(KEY_LAST, null);
        final List<Server> servers = getServers();
        if (id != null) {
            for (Server s : servers) {
                if (s.id.equals(id)) {
                    return s;
                }
            }
        }
        return servers.isEmpty() ? null : servers.get(0);
    }

    /** Whether any of the user's own servers is connected right now (a proxy or an AmneziaWG tunnel). */
    public static boolean isConnected() {
        return vpn.sdk.VpnSDK.isProxyRunning() || vpn.sdk.VpnSDK.isAwgUp();
    }

    /** Switches the user's own server off and remembers which it was. Nothing resumes it at the next start. */
    public static void disconnectAll() {
        final String active = getActiveServerId();
        if (active != null) {
            prefs().edit().putString(KEY_LAST, active).apply();
        }
        vpn.sdk.VpnSDK.disconnectAwg();
        if (vpn.sdk.VpnSDK.isProxyRunning()) {
            vpn.sdk.VpnSDK.stopProxy();
        }
        dropXrayProxySelection();
        setActiveServerId(null);
    }

    /** If Telegram is pointed at the xray SOCKS endpoint, stop using it (direct or TgWs remains). */
    public static void dropXrayProxySelection() {
        final SharedConfig.ProxyInfo current = SharedConfig.currentProxy;
        if (current != null && vpn.sdk.VpnSDK.getProxySocksHost().equals(current.settings.getAddress())
                && current.settings.getPort() == vpn.sdk.VpnSDK.getProxySocksPort()) {
            MessagesController.getGlobalMainSettings().edit().putBoolean("proxy_enabled", false).apply();
            org.telegram.tgnet.ConnectionsManager.setProxySettings(false, null);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
        }
    }

    /** Connects a proxy-type server (not AmneziaWG, which needs the system's VPN consent from a screen). */
    public static void connectProxyAsync(Server server, Utilities.Callback<Boolean> done) {
        vpn.sdk.VpnSDK.disconnectAwg();
        Utilities.globalQueue.postRunnable(() -> {
            final boolean ok = vpn.sdk.VpnSDK.setCustomVlessConfig(server.rawUrl);
            AndroidUtilities.runOnUIThread(() -> {
                if (ok) {
                    setActiveServerId(server.id);
                }
                done.run(ok);
            });
        });
    }

    public static Server getActiveServer() {
        final String id = getActiveServerId();
        if (id == null) {
            return null;
        }
        for (Server s : getServers()) {
            if (s.id.equals(id)) {
                return s;
            }
        }
        return null;
    }

    public static String getActiveServerId() {
        return prefs().getString(KEY_ACTIVE, null);
    }

    public static void setActiveServerId(String id) {
        if (id == null) {
            prefs().edit().remove(KEY_ACTIVE).apply();
        } else {
            prefs().edit().putString(KEY_ACTIVE, id).apply();
        }
    }
}
