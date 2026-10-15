package org.telegram.ui.Components;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class GithubUpdater {

    private static final String TAG = "GithubUpdater";
    private static final String GITHUB_REPO = "Primeevokak/PrimeGram";
    private static final String PREFS_NAME = "primegram_updater";
    private static final String KEY_LAST_CHECK = "last_check_time";
    private static final String KEY_LATER_TIME = "later_time";
    private static final String KEY_PENDING_DOWNLOAD_ID = "pending_download_id";
    private static final String KEY_PENDING_DOWNLOAD_VERSION = "pending_download_version";
    /** mainconfig: tell about new versions at all (off after "Обновлюсь сам"). */
    public static final String KEY_NOTIFY = "primegram_update_notify";
    /** mainconfig: also offer pre-releases (tags like v12.10.5.0-beta1). Off by default. */
    public static final String KEY_BETA_CHANNEL = "primegram_beta_channel";

    /**
     * Reattaches {@link PrimeUpdateProgressDialog} to a download started on a previous run of the
     * app - {@link #downloadAndInstallUpdate} only shows progress while its dialog is alive, so
     * without this a download finished (or still running) while the app was closed just sits in
     * the Downloads folder with nothing ever offering to install it.
     * Returns true when a pending download was found and handled - the caller should skip the
     * regular version check for this cycle so the user isn't shown two update prompts at once.
     */
    public static boolean checkPendingDownload(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long downloadId = prefs.getLong(KEY_PENDING_DOWNLOAD_ID, -1);
        if (downloadId == -1) {
            return false;
        }
        String version = prefs.getString(KEY_PENDING_DOWNLOAD_VERSION, "");
        if (!isNewerVersion(org.telegram.messenger.BuildVars.BUILD_VERSION_STRING, version)) {
            // Already updated! The pending download was for the version we are already running (or older).
            clearPendingDownload(context);
            org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> cleanupOldApks(context));
            return false;
        }
        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) {
            clearPendingDownload(context);
            return false;
        }
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(downloadId);
        try (Cursor cursor = manager.query(query)) {
            if (cursor == null || !cursor.moveToFirst()) {
                clearPendingDownload(context);
                return false;
            }
            int statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            int status = statusIdx >= 0 ? cursor.getInt(statusIdx) : DownloadManager.STATUS_FAILED;
            if (status == DownloadManager.STATUS_FAILED) {
                clearPendingDownload(context);
                return false;
            }
            PrimeUpdateProgressDialog.resume(context, downloadId, version, pendingDownloadCallback(context));
            return true;
        }
    }

    private static PrimeUpdateProgressDialog.Callback pendingDownloadCallback(Context context) {
        return new PrimeUpdateProgressDialog.Callback() {
            @Override
            public void onInstallRequested(long downloadId) {
                clearPendingDownload(context);
                installApk(context, downloadId);
            }

            @Override
            public void onCancelled(long downloadId) {
                clearPendingDownload(context);
                org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> cleanupOldApks(context));
            }
        };
    }

    private static void clearPendingDownload(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .remove(KEY_PENDING_DOWNLOAD_ID)
                .remove(KEY_PENDING_DOWNLOAD_VERSION)
                .apply();
    }

    /**
     * Deletes leftover update APK files from previous downloads and removes stale DownloadManager jobs.
     */
    public static void cleanupOldApks(Context context) {
        if (context == null) {
            return;
        }
        try {
            File downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (downloadsDir != null && downloadsDir.exists()) {
                File[] files = downloadsDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile() && file.getName().endsWith(".apk") && file.getName().startsWith("PrimeGram")) {
                            file.delete();
                        }
                    }
                }
            }

            File cacheDir = context.getExternalCacheDir();
            if (cacheDir != null && cacheDir.exists()) {
                File[] files = cacheDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile() && file.getName().endsWith(".apk") && file.getName().startsWith("PrimeGram")) {
                            file.delete();
                        }
                    }
                }
            }

            File publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (publicDownloads != null && publicDownloads.exists()) {
                File[] files = publicDownloads.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile() && file.getName().endsWith(".apk") && file.getName().startsWith("PrimeGram-update")) {
                            file.delete();
                        }
                    }
                }
            }

            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            long downloadId = prefs.getLong(KEY_PENDING_DOWNLOAD_ID, -1);
            if (downloadId != -1) {
                DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                if (manager != null) {
                    try {
                        manager.remove(downloadId);
                    } catch (Exception ignore) {}
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to cleanup old APK files", e);
        }
    }

    public static void checkForUpdates(Context context, String currentVersion, boolean isManual) {
        if (checkPendingDownload(context)) {
            return;
        }
        org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> cleanupOldApks(context));
        new Thread(() -> {
            try {
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

                SharedPreferences settings = context.getSharedPreferences("mainconfig", Context.MODE_PRIVATE);
                if (!isManual && !settings.getBoolean(KEY_NOTIFY, true)) {
                    // "Обновлюсь сам": no automatic checks at all until re-enabled in settings.
                    return;
                }
                final boolean betaChannel = settings.getBoolean(KEY_BETA_CHANNEL, false);

                if (!isManual) {
                    // If user pressed "Later", wait 24 hours
                    long laterTime = prefs.getLong(KEY_LATER_TIME, 0);
                    if (System.currentTimeMillis() - laterTime < 24 * 60 * 60 * 1000L) {
                        return;
                    }

                    // Throttle background checks to once every 4 hours to avoid API limits
                    long lastCheck = prefs.getLong(KEY_LAST_CHECK, 0);
                    if (System.currentTimeMillis() - lastCheck < 4 * 60 * 60 * 1000L) {
                        return;
                    }
                }
                prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply();

                // Stable channel: /releases/latest, which GitHub itself never answers with a
                // pre-release - beta tags stay invisible to everyone who has not opted in. Beta
                // channel: the recent releases list, newest version of either kind.
                URL url = new URL("https://api.github.com/repos/" + GITHUB_REPO
                        + (betaChannel ? "/releases?per_page=20" : "/releases/latest"));
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }
                    in.close();

                    JSONObject json = betaChannel
                            ? pickNewestRelease(new JSONArray(response.toString()))
                            : new JSONObject(response.toString());
                    if (json == null) {
                        return;
                    }
                    String latestVersion = json.getString("tag_name");
                    final boolean isBeta = json.optBoolean("prerelease", false) || isBetaVersion(latestVersion);

                    if (isNewerVersion(currentVersion, latestVersion)) {
                        String downloadedVersion = prefs.getString("downloaded_version", "");
                        if (!isManual && latestVersion.equals(downloadedVersion)) {
                            // Already downloaded, don't redownload or annoy on auto check
                            return;
                        }

                        JSONArray assets = json.getJSONArray("assets");
                        if (assets.length() > 0) {
                            String downloadUrl = null;
                            for (String abi : android.os.Build.SUPPORTED_ABIS) {
                                for (int i = 0; i < assets.length(); i++) {
                                    String name = assets.getJSONObject(i).getString("name");
                                    if (name.endsWith(".apk") && name.contains(abi)) {
                                        downloadUrl = assets.getJSONObject(i).getString("browser_download_url");
                                        break;
                                    }
                                }
                                if (downloadUrl != null) break;
                            }
                            if (downloadUrl == null) {
                                for (int i = 0; i < assets.length(); i++) {
                                    String name = assets.getJSONObject(i).getString("name");
                                    if (name.endsWith(".apk")) {
                                        downloadUrl = assets.getJSONObject(i).getString("browser_download_url");
                                        break;
                                    }
                                }
                            }
                            if (downloadUrl == null) return;
                            android.util.Log.d(TAG, "Update available: " + latestVersion + ". URL: " + downloadUrl);
                            
                            SharedPreferences mainPrefs = context.getSharedPreferences("mainconfig", Context.MODE_PRIVATE);
                            boolean autoUpdate = mainPrefs.getBoolean("primegram_auto_updates", true);
                            
                            final String finalDownloadUrl = downloadUrl;
                            org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
                                if (autoUpdate && !isManual) {
                                    downloadAndInstallUpdate(context, finalDownloadUrl, latestVersion);
                                } else {
                                    try {
                                        org.telegram.ui.ActionBar.AlertDialog.Builder builder = new org.telegram.ui.ActionBar.AlertDialog.Builder(context);
                                        builder.setTitle(isBeta ? "Доступна бета-версия" : "Доступно обновление");
                                        builder.setMessage((isBeta ? "Вышла бета-версия PrimeGram (" : "Вышла новая версия PrimeGram (")
                                                + latestVersion + "). Хотите скачать и установить её?");
                                        builder.setPositiveButton("Обновить", (dialogInterface, i) -> {
                                            downloadAndInstallUpdate(context, finalDownloadUrl, latestVersion);
                                        });
                                        builder.setNegativeButton("Позже", (dialogInterface, i) -> {
                                            prefs.edit().putLong(KEY_LATER_TIME, System.currentTimeMillis()).apply();
                                        });
                                        if (!isManual) {
                                            builder.setNeutralButton("Обновлюсь сам", (dialogInterface, i) -> confirmManualUpdates(context));
                                        }
                                        builder.show();
                                    } catch (Exception e) {
                                        android.util.Log.e(TAG, "Failed to show update dialog", e);
                                    }
                                }
                            });
                        }
                    } else {
                        prefs.edit().remove("downloaded_version").apply();
                        if (isManual) {
                            org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
                                android.widget.Toast.makeText(context, "У вас установлена актуальная версия.", android.widget.Toast.LENGTH_SHORT).show();
                            });
                        }
                    }
                } else {
                    if (isManual) {
                        org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
                            android.widget.Toast.makeText(context, "Ошибка проверки обновлений. Код: " + responseCode, android.widget.Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            } catch (Exception e) {
                android.util.Log.e(TAG, "Failed to check for updates", e);
                if (isManual) {
                    org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
                        android.widget.Toast.makeText(context, "Ошибка сети при проверке обновлений.", android.widget.Toast.LENGTH_SHORT).show();
                    });
                }
            }
        }).start();
    }

    public static void downloadAndInstallUpdate(Context context, String downloadUrl, String latestVersion) {
        cleanupOldApks(context);
        android.app.DownloadManager.Request request = new android.app.DownloadManager.Request(android.net.Uri.parse(downloadUrl));
        request.setTitle("Обновление PrimeGram");
        request.setDescription("Скачивание новой версии...");
        request.setDestinationInExternalFilesDir(context, android.os.Environment.DIRECTORY_DOWNLOADS, "PrimeGram-update.apk");
        request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        android.app.DownloadManager manager = (android.app.DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        long downloadId = manager.enqueue(request);

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString("downloaded_version", latestVersion)
                .putLong(KEY_PENDING_DOWNLOAD_ID, downloadId)
                .putString(KEY_PENDING_DOWNLOAD_VERSION, latestVersion)
                .apply();

        PrimeUpdateProgressDialog.show(context, downloadId, latestVersion, pendingDownloadCallback(context));
    }

    private static void installApk(Context context, long downloadId) {
        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(downloadId);
        Cursor cursor = manager.query(query);

        if (cursor.moveToFirst()) {
            int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            if (statusIndex >= 0 && DownloadManager.STATUS_SUCCESSFUL == cursor.getInt(statusIndex)) {
                int uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                if (uriIndex >= 0) {
                    String uriString = cursor.getString(uriIndex);
                    if (uriString != null) {
                        File apkFile = new File(Uri.parse(uriString).getPath());
                        if (apkFile.exists()) {
                            Intent installIntent = new Intent(Intent.ACTION_VIEW);
                            installIntent.setDataAndType(getUriFromFile(context, apkFile), "application/vnd.android.package-archive");
                            installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            context.startActivity(installIntent);
                        }
                    }
                }
            }
        }
        cursor.close();
    }

    private static Uri getUriFromFile(Context context, File file) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file);
        } else {
            return Uri.fromFile(file);
        }
    }

    /** "Обновлюсь сам" pressed: explain what it turns off before turning it off. */
    private static void confirmManualUpdates(Context context) {
        try {
            org.telegram.ui.ActionBar.AlertDialog.Builder builder = new org.telegram.ui.ActionBar.AlertDialog.Builder(context);
            builder.setTitle("Обновляться самостоятельно?");
            builder.setMessage("Уведомления о новых версиях PrimeGram больше не будут приходить.\n\n"
                    + "Проверить обновления вручную и включить уведомления снова можно в настройках PrimeGram → Обновления.");
            builder.setPositiveButton("Понятно", (d, w) -> context.getSharedPreferences("mainconfig", Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_NOTIFY, false).apply());
            builder.setNegativeButton("Отмена", null);
            builder.show();
        } catch (Exception e) {
            Log.e(TAG, "Failed to show manual-updates confirmation", e);
        }
    }

    /** The newest non-draft release in a /releases page, stable or pre-release. */
    private static JSONObject pickNewestRelease(JSONArray releases) {
        JSONObject best = null;
        for (int i = 0; i < releases.length(); i++) {
            JSONObject r = releases.optJSONObject(i);
            if (r == null || r.optBoolean("draft", false) || r.optString("tag_name", "").isEmpty()) {
                continue;
            }
            if (best == null || compareVersions(r.optString("tag_name"), best.optString("tag_name")) > 0) {
                best = r;
            }
        }
        return best;
    }

    static boolean isBetaVersion(String version) {
        return version != null && version.toLowerCase(java.util.Locale.ROOT).contains("beta");
    }

    private static boolean isNewerVersion(String current, String latest) {
        if (latest == null || latest.replaceAll("[^0-9]", "").isEmpty()) {
            return false;
        }
        if (current == null || current.replaceAll("[^0-9]", "").isEmpty()) {
            return true;
        }
        return compareVersions(latest, current) > 0;
    }

    /**
     * "v12.10.5.0-beta2" vs "12.10.5.0": numeric parts first, then the beta number - a beta comes
     * before the release it leads up to (beta1 < beta2 < release). The old comparison stripped
     * every non-digit, which turned "12.10.5.0-beta1" into "12.10.5.01".
     */
    static int compareVersions(String a, String b) {
        final long[] pa = parseVersion(a), pb = parseVersion(b);
        for (int i = 0; i < pa.length; i++) {
            if (pa[i] != pb[i]) {
                return pa[i] > pb[i] ? 1 : -1;
            }
        }
        return 0;
    }

    /** [major, minor, patch, build, betaNumber]; betaNumber is Long.MAX_VALUE for a stable version. */
    private static long[] parseVersion(String version) {
        final long[] out = new long[]{0, 0, 0, 0, Long.MAX_VALUE};
        if (version == null) {
            return out;
        }
        String v = version.trim().toLowerCase(java.util.Locale.ROOT);
        if (v.startsWith("v")) {
            v = v.substring(1);
        }
        String core = v;
        final int beta = v.indexOf("beta");
        if (beta >= 0) {
            core = v.substring(0, beta);
            final String n = v.substring(beta + 4).replaceAll("[^0-9]", "");
            out[4] = n.isEmpty() ? 0 : Long.parseLong(n);
        }
        final String[] parts = core.replaceAll("[^0-9.]", "").split("\\.");
        for (int i = 0, k = 0; i < parts.length && k < 4; i++) {
            if (!parts[i].isEmpty()) {
                try {
                    out[k] = Long.parseLong(parts[i]);
                } catch (NumberFormatException ignore) {
                }
                k++;
            }
        }
        return out;
    }
}
