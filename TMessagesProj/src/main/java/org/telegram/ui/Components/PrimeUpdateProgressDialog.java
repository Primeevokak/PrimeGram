package org.telegram.ui.Components;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;

/**
 * PrimeGram: in-app progress UI for the auto-downloaded update apk.
 *
 * <p>Before this, {@code GithubUpdater} started the {@link DownloadManager} job and left the
 * user with nothing but a two-second toast - the only feedback afterwards was Android's own
 * download notification, and the "now install it" step depended on a {@code BroadcastReceiver}
 * registered on the activity, which dies with the app if the user leaves before the download
 * finishes. This dialog polls {@link DownloadManager} directly so it shows real progress and
 * survives being reopened - {@link #resume} rebuilds the same UI from the download's current
 * state instead of assuming the original download call is still on the stack.
 */
public class PrimeUpdateProgressDialog {

    private static final long POLL_INTERVAL_MS = 500;

    public interface Callback {
        void onInstallRequested(long downloadId);
        void onCancelled(long downloadId);
    }

    private final Context context;
    private final long downloadId;
    private final Callback callback;

    private AlertDialog dialog;
    private TextView statusText;
    private ProgressBar progressBar;
    private boolean polling;

    private PrimeUpdateProgressDialog(Context context, long downloadId, Callback callback) {
        this.context = context;
        this.downloadId = downloadId;
        this.callback = callback;
    }

    /** Starts a fresh download's progress UI. */
    public static PrimeUpdateProgressDialog show(Context context, long downloadId, String version, Callback callback) {
        PrimeUpdateProgressDialog d = new PrimeUpdateProgressDialog(context, downloadId, callback);
        d.build(version);
        d.poll();
        return d;
    }

    /** Rebuilds the UI for a download that's already in progress or finished, e.g. after the app was reopened. */
    public static PrimeUpdateProgressDialog resume(Context context, long downloadId, String version, Callback callback) {
        return show(context, downloadId, version, callback);
    }

    private void build(String version) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(24);
        layout.setPadding(pad, AndroidUtilities.dp(8), pad, 0);

        statusText = new TextView(context);
        statusText.setTextSize(15);
        statusText.setText("Загрузка...");
        layout.addView(statusText);

        progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setIndeterminate(true);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(4));
        progressParams.topMargin = AndroidUtilities.dp(16);
        progressParams.bottomMargin = AndroidUtilities.dp(8);
        layout.addView(progressBar, progressParams);

        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle("Обновление PrimeGram" + (version != null && !version.isEmpty() ? " " + version : ""))
                .setView(layout)
                .setNegativeButton(LocaleController.getString(R.string.Cancel), (dialogInterface, which) -> cancel());

        dialog = builder.create();
        dialog.setCancelable(false);
        dialog.setOnDismissListener(d -> polling = false);
        dialog.show();
    }

    private void poll() {
        polling = true;
        pollOnce();
    }

    private void pollOnce() {
        if (!polling || dialog == null || !dialog.isShowing()) {
            return;
        }
        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) {
            return;
        }
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(downloadId);
        try (Cursor cursor = manager.query(query)) {
            if (cursor == null || !cursor.moveToFirst()) {
                fail("Загрузка отменена системой.");
                return;
            }
            int statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            int status = statusIdx >= 0 ? cursor.getInt(statusIdx) : DownloadManager.STATUS_FAILED;
            switch (status) {
                case DownloadManager.STATUS_SUCCESSFUL:
                    onDownloadComplete();
                    return;
                case DownloadManager.STATUS_FAILED:
                    fail("Не удалось скачать обновление.");
                    return;
                default: {
                    int soFarIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                    int totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                    long soFar = soFarIdx >= 0 ? cursor.getLong(soFarIdx) : 0;
                    long total = totalIdx >= 0 ? cursor.getLong(totalIdx) : 0;
                    updateProgress(soFar, total);
                }
            }
        }
        AndroidUtilities.runOnUIThread(this::pollOnce, POLL_INTERVAL_MS);
    }

    private void updateProgress(long soFar, long total) {
        if (total > 0) {
            progressBar.setIndeterminate(false);
            int percent = (int) Math.min(100, soFar * 100L / total);
            progressBar.setProgress(percent);
            statusText.setText(percent + "% (" + formatMb(soFar) + " / " + formatMb(total) + " МБ)");
        } else {
            statusText.setText(formatMb(soFar) + " МБ");
        }
    }

    private String formatMb(long bytes) {
        return String.format(java.util.Locale.US, "%.1f", bytes / 1024.0 / 1024.0);
    }

    private void onDownloadComplete() {
        polling = false;
        progressBar.setIndeterminate(false);
        progressBar.setProgress(100);
        statusText.setText("Обновление скачано. Готово к установке.");

        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle("Обновление готово")
                .setMessage("Новая версия PrimeGram скачана. Установить сейчас?")
                .setPositiveButton("Установить", (dialogInterface, which) -> {
                    if (callback != null) {
                        callback.onInstallRequested(downloadId);
                    }
                })
                .setNegativeButton("Позже", null);

        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
        dialog = builder.create();
        // PrimeGram: was dismissable by tapping outside it, same as no answer at all - the update
        // was already downloaded at this point, but nothing in this class (or the caller that
        // re-triggers a full re-download the next time "Обновить" is pressed) knew that, so a
        // stray tap outside this dialog silently threw the finished download away and the user
        // had to sit through the whole download again just to see this same dialog once more.
        // Forcing an explicit choice ("Установить" / "Позже") is cheap and makes that state
        // unreachable, without needing to also fix the caller's re-download logic.
        dialog.setCancelable(false);
        dialog.show();
    }

    private void fail(String message) {
        polling = false;
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
        new AlertDialog.Builder(context)
                .setTitle("Обновление")
                .setMessage(message)
                .setPositiveButton(LocaleController.getString(R.string.OK), null)
                .show();
        if (callback != null) {
            callback.onCancelled(downloadId);
        }
    }

    private void cancel() {
        polling = false;
        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager != null) {
            manager.remove(downloadId);
        }
        if (callback != null) {
            callback.onCancelled(downloadId);
        }
    }
}
