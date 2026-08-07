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
 * PrimeGram: a Happ-style list of VLESS/VMess/Trojan/Shadowsocks servers, sitting on top of
 * {@link VpnSDK} the way {@link PrimeIconPacksActivity} sits on top of {@link
 * org.telegram.messenger.PrimeIconPacks} - VpnSDK itself only ever remembers one active config at
 * a time ({@code setCustomVlessConfig}), it has no notion of a list; {@link PrimeVpnServerStore}
 * is what supplies that, and this screen is what lets a person build one by hand instead of
 * juggling links in a notes app.
 */
public class PrimeVpnServersActivity extends UniversalFragment {

    private static final int ID_ADD = 1;
    private static final int ID_FREE_KEY = 2;
    private static final int ID_PING_ALL = 3;
    private static final int ID_SERVER_BASE = 100;

    private static final int PING_TIMEOUT_MS = 3000;

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
        final boolean autoActive = activeId == null && VpnSDK.hasCachedXrayConfig() && VpnSDK.isProxyRunning();

        items.add(UItem.asButton(ID_ADD, "Добавить сервер", "vless / vmess / trojan / ss / socks"));
        items.add(UItem.asButton(ID_FREE_KEY, "Попробовать бесплатный ключ", freeKeySubtitle()));
        items.add(UItem.asShadow("Бесплатный ключ выдаётся автоматически и не хранится в этом списке отдельной строкой - "
                + "он либо активен, либо нет, без адреса, который можно посмотреть или скопировать."));

        if (!servers.isEmpty()) {
            items.add(UItem.asHeader("Мои серверы"));
            items.add(UItem.asButton(ID_PING_ALL, pinging ? "Проверяем..." : "Проверить все", servers.size() + " серверов"));
            for (int i = 0; i < servers.size(); i++) {
                final PrimeVpnServerStore.Server s = servers.get(i);
                final String subtitle = s.protocol.toUpperCase(java.util.Locale.ROOT)
                        + (s.lastPingMs >= 0 ? " · " + s.lastPingMs + " мс"
                           : s.lastPingMs == -2 ? " · недоступен" : "");
                items.add(UItem.asCheck(ID_SERVER_BASE + i, s.name + "\n" + subtitle)
                        .setChecked(!autoActive && s.id.equals(activeId)));
            }
            items.add(UItem.asShadow("Тап — подключиться. Долгий тап — удалить."));
        }
    }

    private String freeKeySubtitle() {
        final String activeId = PrimeVpnServerStore.getActiveServerId();
        if (activeId != null) {
            return "выключен, активен свой сервер";
        }
        if (VpnSDK.isProxyRunning()) {
            return "активен";
        }
        return VpnSDK.hasCachedXrayConfig() ? "ключ есть, прокси выключен" : "ещё не запрашивался";
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_ADD) {
            showAddServerDialog();
        } else if (item.id == ID_FREE_KEY) {
            requestFreeKey();
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

    private void requestFreeKey() {
        if (getParentActivity() == null) {
            return;
        }
        PrimeVpnServerStore.setActiveServerId(null);
        android.widget.Toast.makeText(getContext(), "Получаем ключ...", android.widget.Toast.LENGTH_SHORT).show();
        VpnSDK.registerOrAuth(2, success -> {
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
            if (!success) {
                android.widget.Toast.makeText(getContext(), "Не удалось получить ключ. Проверьте соединение и попробуйте ещё раз.", android.widget.Toast.LENGTH_LONG).show();
            }
        });
    }

    private void connectTo(PrimeVpnServerStore.Server server) {
        if (getParentActivity() == null) {
            return;
        }
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
                        VpnSDK.stopProxy();
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
        urlInput.setHint("vless:// / vmess:// / trojan:// / ss:// / socks://");
        urlInput.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        urlInput.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        urlInput.setHintTextColor(getThemedColor(Theme.key_dialogTextHint));
        urlInput.setBackground(Theme.createEditTextDrawable(getParentActivity(), true));
        urlInput.setSingleLine(true);
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
                            && !"ss".equals(protocol) && !"socks".equals(protocol)) {
                        android.widget.Toast.makeText(getContext(),
                                "Не похоже на vless/vmess/trojan/ss/socks ссылку",
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
