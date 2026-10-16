package org.telegram.ui;

import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.PrimeVpnServerStore;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;
import java.util.List;

import vpn.sdk.VpnSDK;

/**
 * PrimeGram: a Happ-style list of the user's own VLESS/VMess/Trojan/Shadowsocks/SOCKS servers and
 * AmneziaWG configs, sitting on top of
 * {@link VpnSDK} the way {@link PrimeIconPacksActivity} sits on top of {@link
 * org.telegram.messenger.PrimeIconPacks} - VpnSDK itself only ever remembers one active config at
 * a time ({@code setCustomVlessConfig}), it has no notion of a list; {@link PrimeVpnServerStore}
 * is what supplies that, and this screen is what lets a person build one by hand instead of
 * juggling links in a notes app.
 *
 * <p>Only keys the user adds: nothing on this screen asks any server for a key or a config.
 * An AmneziaWG config runs as a system VPN limited to this app, so it needs the VPN consent
 * dialog once; proxy links run in-process through xray.
 */
public class PrimeVpnServersActivity extends UniversalFragment {

    private static final int ID_ADD = 1;
    private static final int ID_PING_ALL = 3;
    private static final int ID_SERVER_BASE = 100;

    private static final int PING_TIMEOUT_MS = 3000;
    private static final int REQUEST_VPN_CONSENT = 7741;

    /** Waiting for the VPN consent dialog to come back. */
    private PrimeVpnServerStore.Server pendingAwgServer;

    private List<PrimeVpnServerStore.Server> servers = new ArrayList<>();
    private boolean pinging;

    @Override
    public boolean onFragmentCreate() {
        reload();
        return super.onFragmentCreate();
    }

    private void reload() {
        servers = PrimeVpnServerStore.getServers();
    }

    @Override
    protected CharSequence getTitle() {
        return "Серверы";
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final String activeId = PrimeVpnServerStore.getActiveServerId();

        items.add(UItem.asButton(ID_ADD, "Добавить сервер", "vless / vmess / trojan / ss / socks / AmneziaWG"));
        items.add(UItem.asShadow("Работают только ваши ключи. AmneziaWG подключается как VPN только для PrimeGram: "
                + "остальные приложения на телефоне идут мимо туннеля."));

        if (!servers.isEmpty()) {
            items.add(UItem.asHeader("Мои серверы"));
            items.add(UItem.asButton(ID_PING_ALL, pinging ? "Проверяем..." : "Проверить все", servers.size() + " серверов"));
            for (int i = 0; i < servers.size(); i++) {
                final PrimeVpnServerStore.Server s = servers.get(i);
                final String subtitle = (PrimeVpnServerStore.PROTOCOL_AWG.equals(s.protocol) ? "AmneziaWG" : s.protocol.toUpperCase(java.util.Locale.ROOT))
                        + (s.lastPingMs >= 0 ? " · " + s.lastPingMs + " мс"
                           : s.lastPingMs == -2 ? " · недоступен" : "");
                items.add(UItem.asCheck(ID_SERVER_BASE + i, s.name + "\n" + subtitle)
                        .setChecked(s.id.equals(activeId)));
            }
            items.add(UItem.asShadow("Тап — подключиться. Долгий тап — удалить."));
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_ADD) {
            showAddServerDialog();
        } else if (item.id == ID_PING_ALL) {
            pingAll();
        } else if (item.id >= ID_SERVER_BASE && item.id < ID_SERVER_BASE + servers.size()) {
            connectTo(servers.get(item.id - ID_SERVER_BASE));
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (item.id >= ID_SERVER_BASE && item.id < ID_SERVER_BASE + servers.size()) {
            confirmDelete(servers.get(item.id - ID_SERVER_BASE));
            return true;
        }
        return false;
    }

    /**
     * A plain TCP connect to each server's host:port, timed - not a real xray handshake (that
     * would mean spinning up a full tunnel per entry just to throw it away), but enough to tell
     * "reachable, and roughly how far" from "dead or blocked" without disturbing whichever server
     * is currently connected.
     */
    private void pingAll() {
        if (pinging || servers.isEmpty()) {
            return;
        }
        pinging = true;
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
        final List<PrimeVpnServerStore.Server> snapshot = new ArrayList<>(servers);
        final java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(
                Math.min(8, snapshot.size()));
        final java.util.concurrent.atomic.AtomicInteger remaining = new java.util.concurrent.atomic.AtomicInteger(snapshot.size());
        for (PrimeVpnServerStore.Server server : snapshot) {
            pool.submit(() -> {
                server.lastPingMs = pingOne(server.rawUrl);
                if (remaining.decrementAndGet() == 0) {
                    pool.shutdown();
                    AndroidUtilities.runOnUIThread(() -> {
                        pinging = false;
                        if (listView != null && listView.adapter != null) {
                            listView.adapter.update(true);
                        }
                    });
                }
            });
        }
    }

    /** @return round-trip ms, or -2 if the connect failed/timed out, or -1 if the link's
     *  host:port couldn't even be parsed. */
    private static long pingOne(String rawUrl) {
        final vpn.sdk.HostPort hostPort = VpnSDK.extractHostPort(rawUrl);
        if (hostPort == null) {
            return -1;
        }
        final long start = android.os.SystemClock.elapsedRealtime();
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(hostPort.host, hostPort.port), PING_TIMEOUT_MS);
            return android.os.SystemClock.elapsedRealtime() - start;
        } catch (Throwable t) {
            return -2;
        }
    }

    private void connectTo(PrimeVpnServerStore.Server server) {
        if (getParentActivity() == null) {
            return;
        }
        if (PrimeVpnServerStore.PROTOCOL_AWG.equals(server.protocol)) {
            final android.content.Intent consent = android.net.VpnService.prepare(getParentActivity());
            if (consent != null) {
                pendingAwgServer = server;
                startActivityForResult(consent, REQUEST_VPN_CONSENT);
            } else {
                connectAwg(server);
            }
            return;
        }
        VpnSDK.disconnectAwg();
        android.widget.Toast.makeText(getContext(), "Подключаемся...", android.widget.Toast.LENGTH_SHORT).show();
        org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
            final boolean ok = VpnSDK.setCustomVlessConfig(server.rawUrl);
            AndroidUtilities.runOnUIThread(() -> {
                if (ok) {
                    PrimeVpnServerStore.setActiveServerId(server.id);
                    android.widget.Toast.makeText(getContext(), "«" + server.name + "» подключён", android.widget.Toast.LENGTH_SHORT).show();
                } else {
                    final String reason = VpnSDK.getLastCustomVlessError();
                    new AlertDialog.Builder(getParentActivity())
                            .setTitle("Не удалось подключиться")
                            .setMessage("«" + server.name + "» не отвечает" + (reason != null ? " (" + reason + ")" : "") + ".")
                            .setPositiveButton(LocaleController.getString(R.string.OK), null)
                            .show();
                }
                reload();
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
            });
        });
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, android.content.Intent data) {
        if (requestCode != REQUEST_VPN_CONSENT) {
            return;
        }
        final PrimeVpnServerStore.Server server = pendingAwgServer;
        pendingAwgServer = null;
        if (server == null) {
            return;
        }
        if (resultCode == android.app.Activity.RESULT_OK) {
            connectAwg(server);
        } else if (getContext() != null) {
            android.widget.Toast.makeText(getContext(), "Без разрешения на VPN AmneziaWG не подключить", android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void connectAwg(PrimeVpnServerStore.Server server) {
        android.widget.Toast.makeText(getContext(), "Подключаемся...", android.widget.Toast.LENGTH_SHORT).show();
        // One transport at a time: the xray proxy would otherwise keep Telegram on a SOCKS
        // endpoint that is about to stop.
        if (VpnSDK.isProxyRunning()) {
            VpnSDK.stopProxy();
            dropXrayProxySelection();
        }
        VpnSDK.connectAwg(server.rawUrl, org.telegram.messenger.ApplicationLoader.applicationContext.getPackageName(), ok -> {
            if (ok) {
                PrimeVpnServerStore.setActiveServerId(server.id);
                if (getContext() != null) {
                    android.widget.Toast.makeText(getContext(), "«" + server.name + "» подключён", android.widget.Toast.LENGTH_SHORT).show();
                }
            } else if (getParentActivity() != null) {
                final String reason = VpnSDK.getLastAwgError();
                new AlertDialog.Builder(getParentActivity())
                        .setTitle("Не удалось подключиться")
                        .setMessage("Туннель «" + server.name + "» не поднялся" + (reason != null ? " (" + reason + ")" : "") + ". Проверьте конфиг.")
                        .setPositiveButton(LocaleController.getString(R.string.OK), null)
                        .show();
            }
            reload();
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        });
    }

    /** If Telegram is pointed at the xray SOCKS endpoint, stop using it (direct or TgWs remains). */
    private static void dropXrayProxySelection() {
        final org.telegram.messenger.SharedConfig.ProxyInfo current = org.telegram.messenger.SharedConfig.currentProxy;
        if (current != null && VpnSDK.getProxySocksHost().equals(current.settings.getAddress())
                && current.settings.getPort() == VpnSDK.getProxySocksPort()) {
            MessagesController.getGlobalMainSettings().edit().putBoolean("proxy_enabled", false).apply();
            org.telegram.tgnet.ConnectionsManager.setProxySettings(false, null);
            org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(org.telegram.messenger.NotificationCenter.proxySettingsChanged);
        }
    }

    private void confirmDelete(PrimeVpnServerStore.Server server) {
        if (getParentActivity() == null) {
            return;
        }
        new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle("Удалить «" + server.name + "»?")
                .setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) -> {
                    final boolean wasActive = server.id.equals(PrimeVpnServerStore.getActiveServerId());
                    PrimeVpnServerStore.removeServer(server.id);
                    if (wasActive) {
                        if (PrimeVpnServerStore.PROTOCOL_AWG.equals(server.protocol)) {
                            VpnSDK.disconnectAwg();
                        } else {
                            VpnSDK.stopProxy();
                        }
                    }
                    reload();
                    listView.adapter.update(true);
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    private void showAddServerDialog() {
        if (getParentActivity() == null) {
            return;
        }
        final FrameLayout container = new FrameLayout(getParentActivity());
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(6), AndroidUtilities.dp(24), AndroidUtilities.dp(6));

        final EditTextBoldCursor nameInput = new EditTextBoldCursor(getParentActivity());
        nameInput.setHint("Название (необязательно)");
        nameInput.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        nameInput.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        nameInput.setHintTextColor(getThemedColor(Theme.key_dialogTextHint));
        nameInput.setBackground(Theme.createEditTextDrawable(getParentActivity(), true));
        nameInput.setSingleLine(true);
        nameInput.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));

        final EditTextBoldCursor urlInput = new EditTextBoldCursor(getParentActivity());
        urlInput.setHint("vless:// … или конфиг AmneziaWG целиком");
        urlInput.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        urlInput.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        urlInput.setHintTextColor(getThemedColor(Theme.key_dialogTextHint));
        urlInput.setBackground(Theme.createEditTextDrawable(getParentActivity(), true));
        urlInput.setSingleLine(false);
        urlInput.setMaxLines(8);
        urlInput.setHorizontallyScrolling(false);
        urlInput.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));

        final android.widget.LinearLayout column = new android.widget.LinearLayout(getParentActivity());
        column.setOrientation(android.widget.LinearLayout.VERTICAL);
        column.addView(nameInput, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));
        column.addView(urlInput, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        container.addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        new AlertDialog.Builder(getParentActivity())
                .setTitle("Новый сервер")
                .setView(container)
                .setPositiveButton("Добавить", (dialog, which) -> {
                    final String url = urlInput.getText() != null ? urlInput.getText().toString().trim() : "";
                    if (url.isEmpty()) {
                        return;
                    }
                    final String protocol = PrimeVpnServerStore.detectProtocol(url);
                    if (!"vless".equals(protocol) && !"vmess".equals(protocol) && !"trojan".equals(protocol)
                            && !"ss".equals(protocol) && !"socks".equals(protocol)
                            && !PrimeVpnServerStore.PROTOCOL_AWG.equals(protocol)) {
                        android.widget.Toast.makeText(getContext(),
                                "Не похоже ни на ссылку vless/vmess/trojan/ss/socks, ни на конфиг AmneziaWG",
                                android.widget.Toast.LENGTH_LONG).show();
                        return;
                    }
                    final String name = nameInput.getText() != null ? nameInput.getText().toString().trim() : "";
                    PrimeVpnServerStore.addServer(name, url);
                    reload();
                    if (listView != null && listView.adapter != null) {
                        listView.adapter.update(true);
                    }
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }
}
