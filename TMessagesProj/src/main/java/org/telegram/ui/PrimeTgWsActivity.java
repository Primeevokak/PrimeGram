package org.telegram.ui;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.TgWsProxyService;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.PrimeTgWsDomainCell;
import org.telegram.ui.Components.PrimeTgWsStatusCell;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * PrimeGram: the tunnel's own screen.
 *
 * <p>It used to be one switch and a line of text saying the port was 1080. Everything the service
 * decides on the user's behalf - which of ten domains carries the traffic, which port it managed to
 * bind - was invisible, and when it went wrong the only evidence was a log with nowhere to show it.
 *
 * <p>So: state at the top, the domains laid out with measured latency so a choice can be made
 * rather than guessed, the port as a field, and the log one tap away. The defaults are unchanged -
 * automatic domain, port 1080 - because the screen is for the times when the automatic answer is
 * the wrong one, not a chore to complete before the tunnel works.
 */
public class PrimeTgWsActivity extends UniversalFragment {

    private static final int ID_ENABLED = 1;
    private static final int ID_PORT = 2;
    private static final int ID_MEASURE = 3;
    private static final int ID_LOG = 4;
    private static final int ID_WORKERS_ENABLED = 5;
    private static final int ID_WORKER_ADD = 6;
    private static final int ID_WORKER_HELP = 7;
    private static final int ID_DOMAIN_AUTO = 100;
    private static final int ID_DOMAIN_BASE = 200;
    private static final int ID_WORKER_BASE = 300;

    private final String[] domains = TgWsProxyService.baseDomains();
    private final HashMap<String, Long> latencies = new HashMap<>();
    private final HashMap<Integer, PrimeTgWsDomainCell> cells = new HashMap<>();
    private PrimeTgWsStatusCell statusCell;

    private boolean measuring;
    /** Ticks the status card while the screen is open, so "работает" is never a stale claim. */
    private final Runnable statusTick = new Runnable() {
        @Override
        public void run() {
            if (statusCell != null) {
                statusCell.invalidate();
            }
            AndroidUtilities.runOnUIThread(this, 1000);
        }
    };

    @Override
    public boolean onFragmentCreate() {
        AndroidUtilities.runOnUIThread(statusTick, 1000);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        AndroidUtilities.cancelRunOnUIThread(statusTick);
        super.onFragmentDestroy();
    }

    @Override
    protected CharSequence getTitle() {
        return "TgWs-сервер";
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final Context context = getContext();
        if (context == null) {
            return;
        }
        if (statusCell == null) {
            statusCell = new PrimeTgWsStatusCell(context, getResourceProvider());
        }
        items.add(UItem.asCustom(statusCell));

        final boolean enabled = MessagesController.getGlobalMainSettings()
                .getBoolean("primegram_tgws_enabled", true);
        items.add(UItem.asCheck(ID_ENABLED, "Включить сервер").setChecked(enabled));
        items.add(UItem.asButton(ID_PORT, "Порт", String.valueOf(TgWsProxyService.configuredPort())));
        items.add(UItem.asShadow("Локальный SOCKS5-сервер, через который приложение ходит в сеть. Порт занят другой программой — сервер сам возьмёт следующий свободный."));

        items.add(UItem.asHeader("Домен подключения"));
        items.add(domainRow(ID_DOMAIN_AUTO, context, null));
        for (int i = 0; i < domains.length; i++) {
            items.add(domainRow(ID_DOMAIN_BASE + i, context, domains[i]));
        }
        items.add(UItem.asButton(ID_MEASURE, measuring ? "Проверяем…" : "Проверить задержку"));
        items.add(UItem.asShadow("Обычно домен выбирается сам — по тому, кто первым ответит. Закрепите один, если конкретный провайдер режет остальные: тогда сервер не будет перебирать их при каждом обрыве.\n\nЗамер идёт с этого устройства и через тот же DNS, что и сам туннель, поэтому цифры здесь — то же самое, что видит сервер."));

        items.add(UItem.asHeader("Свои Cloudflare Workers"));
        items.add(UItem.asCheck(ID_WORKERS_ENABLED, "Использовать свои воркеры")
                .setChecked(org.telegram.messenger.PrimeCfWorkers.isEnabled()));
        if (org.telegram.messenger.PrimeCfWorkers.isEnabled()) {
            final java.util.List<String> workers = org.telegram.messenger.PrimeCfWorkers.getDomains();
            for (int i = 0; i < workers.size(); i++) {
                items.add(UItem.asButton(ID_WORKER_BASE + i, workers.get(i), "удалить"));
            }
            items.add(UItem.asButton(ID_WORKER_ADD, "Добавить воркер"));
            items.add(UItem.asButton(ID_WORKER_HELP, "Как развернуть", "инструкция"));
        }
        items.add(UItem.asShadow("Свой бесплатный туннель на серверах Cloudflare — запасной путь, когда наши домены не отвечают. Добавляйте сколько угодно."));

        items.add(UItem.asButton(ID_LOG, "Журнал сервера"));
        items.add(UItem.asShadow("Последние 200 строк: выбор домена, переподключения, ошибки."));
    }

    /**
     * Cells are kept and re-bound rather than rebuilt, because a new view on every list update
     * would restart the bar animation each time a switch elsewhere on the screen is flipped.
     */
    private UItem domainRow(int id, Context context, String domain) {
        PrimeTgWsDomainCell cell = cells.get(id);
        if (cell == null) {
            cell = new PrimeTgWsDomainCell(context, getResourceProvider());
            cells.put(id, cell);
        }
        final String pinned = TgWsProxyService.forcedDomain();
        if (domain == null) {
            cell.set("Автоматически", "по задержке", PrimeTgWsDomainCell.LATENCY_UNKNOWN, pinned.isEmpty());
        } else {
            final Long measured = latencies.get(domain);
            cell.set(domain, null,
                    measured == null ? PrimeTgWsDomainCell.LATENCY_UNKNOWN : measured,
                    domain.equals(pinned));
        }
        return UItem.asCustom(id, cell);
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_ENABLED) {
            // Act on what the switch now says, not on whether the service happens to be up. Those
            // two can disagree - the service is restarted from a few places when a proxy error
            // comes in - and keying off the running state meant that in exactly that case the
            // switch did the opposite of what it had just been set to.
            final boolean enabled = !MessagesController.getGlobalMainSettings()
                    .getBoolean("primegram_tgws_enabled", true);
            MessagesController.getGlobalMainSettings().edit()
                    .putBoolean("primegram_tgws_enabled", enabled).apply();
            if (enabled) {
                TgWsProxyService.startService(getParentActivity());
            } else {
                TgWsProxyService.stopService(getParentActivity());
            }
            listView.adapter.update(true);
        } else if (item.id == ID_PORT) {
            showPortDialog();
        } else if (item.id == ID_MEASURE) {
            measureAll();
        } else if (item.id == ID_LOG) {
            presentFragment(new PrimeTgWsLogActivity());
        } else if (item.id == ID_DOMAIN_AUTO) {
            pin("");
        } else if (item.id >= ID_DOMAIN_BASE && item.id < ID_DOMAIN_BASE + domains.length) {
            pin(domains[item.id - ID_DOMAIN_BASE]);
        } else if (item.id == ID_WORKERS_ENABLED) {
            org.telegram.messenger.PrimeCfWorkers.setEnabled(
                    !org.telegram.messenger.PrimeCfWorkers.isEnabled());
            listView.adapter.update(true);
        } else if (item.id == ID_WORKER_ADD) {
            showWorkerDialog();
        } else if (item.id == ID_WORKER_HELP) {
            org.telegram.messenger.browser.Browser.openUrl(getParentActivity(),
                    "https://github.com/Flowseal/tg-ws-proxy/blob/main/docs/CfWorker.md");
        } else if (item.id >= ID_WORKER_BASE) {
            final java.util.List<String> workers = org.telegram.messenger.PrimeCfWorkers.getDomains();
            final int index = item.id - ID_WORKER_BASE;
            if (index >= 0 && index < workers.size()) {
                confirmWorkerRemoval(workers.get(index));
            }
        }
    }

    private void confirmWorkerRemoval(String domain) {
        if (getParentActivity() == null) {
            return;
        }
        new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity())
                .setTitle("Удалить воркер")
                .setMessage(domain)
                .setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) -> {
                    org.telegram.messenger.PrimeCfWorkers.remove(domain);
                    listView.adapter.update(true);
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    private void showWorkerDialog() {
        if (getParentActivity() == null) {
            return;
        }
        final org.telegram.ui.Components.EditTextBoldCursor editText =
                new org.telegram.ui.Components.EditTextBoldCursor(getParentActivity());
        editText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setSingleLine(true);
        editText.setBackgroundDrawable(null);
        editText.setTextColor(getThemedColor(org.telegram.ui.ActionBar.Theme.key_dialogTextBlack));
        editText.setHintTextColor(getThemedColor(org.telegram.ui.ActionBar.Theme.key_dialogTextHint));
        editText.setCursorColor(getThemedColor(org.telegram.ui.ActionBar.Theme.key_dialogTextBlack));
        editText.setHint("name-1234.username.workers.dev");

        final android.widget.FrameLayout container = new android.widget.FrameLayout(getParentActivity());
        container.addView(editText, org.telegram.ui.Components.LayoutHelper.createFrame(
                org.telegram.ui.Components.LayoutHelper.MATCH_PARENT,
                org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT,
                android.view.Gravity.LEFT | android.view.Gravity.TOP, 24, 4, 24, 4));

        new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity())
                .setTitle("Адрес воркера")
                .setMessage("Скопируйте домен из панели Cloudflare после развёртывания. Адрес целиком, ссылкой или без неё — разберём.")
                .setView(container)
                .setPositiveButton(LocaleController.getString(R.string.Add), (dialog, which) -> {
                    final String error = org.telegram.messenger.PrimeCfWorkers
                            .add(editText.getText().toString());
                    if (error != null) {
                        BulletinFactory.of(this).createErrorBulletin(error).show();
                        return;
                    }
                    listView.adapter.update(true);
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    private void pin(String domain) {
        TgWsProxyService.setForcedDomain(domain);
        listView.adapter.update(true);
        if (TgWsProxyService.isRunning()) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info, domain.isEmpty()
                    ? "Домен снова выбирается автоматически"
                    : "Подключения пойдут через " + domain).show();
        }
    }

    private void showPortDialog() {
        if (getParentActivity() == null) {
            return;
        }
        final org.telegram.ui.Components.EditTextBoldCursor editText =
                new org.telegram.ui.Components.EditTextBoldCursor(getParentActivity());
        editText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        editText.setSingleLine(true);
        editText.setBackgroundDrawable(null);
        editText.setTextColor(getThemedColor(org.telegram.ui.ActionBar.Theme.key_dialogTextBlack));
        editText.setCursorColor(getThemedColor(org.telegram.ui.ActionBar.Theme.key_dialogTextBlack));
        editText.setText(String.valueOf(TgWsProxyService.configuredPort()));
        editText.setSelection(editText.getText().length());

        final android.widget.FrameLayout container = new android.widget.FrameLayout(getParentActivity());
        container.addView(editText, org.telegram.ui.Components.LayoutHelper.createFrame(
                org.telegram.ui.Components.LayoutHelper.MATCH_PARENT,
                org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT,
                android.view.Gravity.LEFT | android.view.Gravity.TOP, 24, 4, 24, 4));

        final AlertDialog dialog = new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle("Порт сервера")
                .setMessage("От 1024 до 65535. Значение по умолчанию — 1080.")
                .setView(container)
                .setPositiveButton(LocaleController.getString(R.string.Save), (d, which) -> {
                    int port;
                    try {
                        port = Integer.parseInt(editText.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        port = TgWsProxyService.PROXY_PORT;
                    }
                    if (port < 1024 || port > 65535) {
                        BulletinFactory.of(this).createErrorBulletin("Порт должен быть от 1024 до 65535").show();
                        return;
                    }
                    TgWsProxyService.setConfiguredPort(port);
                    listView.adapter.update(true);
                    if (TgWsProxyService.isRunning()) {
                        // The listener is bound for the life of the server, so a new port means a
                        // restart. Doing it here rather than asking is the honest reading of
                        // "Save": nobody sets a port and then wants the old one to stay live.
                        TgWsProxyService.stopService(getParentActivity());
                        AndroidUtilities.runOnUIThread(() ->
                                TgWsProxyService.startService(getParentActivity()), 300);
                    }
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .create();
        showDialog(dialog);
        editText.requestFocus();
        AndroidUtilities.runOnUIThread(() -> AndroidUtilities.showKeyboard(editText), 80);
    }

    /**
     * Probes every domain at once. All ten together rather than one after another: they are
     * independent, each costs a couple of seconds at worst, and a queue would make the screen sit
     * there for half a minute filling in one row at a time.
     */
    private void measureAll() {
        if (measuring) {
            return;
        }
        measuring = true;
        for (String domain : domains) {
            latencies.put(domain, PrimeTgWsDomainCell.LATENCY_MEASURING);
        }
        listView.adapter.update(true);

        final int[] remaining = {domains.length};
        for (String domain : domains) {
            Utilities.globalQueue.postRunnable(() -> {
                final long result = TgWsProxyService.probeDomain(domain, 2);
                AndroidUtilities.runOnUIThread(() -> {
                    latencies.put(domain, result < 0 ? PrimeTgWsDomainCell.LATENCY_FAILED : result);
                    if (--remaining[0] == 0) {
                        measuring = false;
                    }
                    if (listView != null && listView.adapter != null) {
                        listView.adapter.update(true);
                    }
                });
            });
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
