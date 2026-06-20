package org.telegram.ui.Components;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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

    public static void checkForUpdates(Context context, String currentVersion) {
        new Thread(() -> {
            try {
                URL url = new URL("https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");

                if (conn.getResponseCode() == 200) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }
                    in.close();

                    JSONObject json = new JSONObject(response.toString());
                    String latestVersion = json.getString("tag_name");

                    if (isNewerVersion(currentVersion, latestVersion)) {
                        JSONArray assets = json.getJSONArray("assets");
                        if (assets.length() > 0) {
                            String downloadUrl = assets.getJSONObject(0).getString("browser_download_url");
                            Log.d(TAG, "Update available: " + latestVersion + ". URL: " + downloadUrl);
                            
                            org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
                                try {
                                    org.telegram.ui.ActionBar.AlertDialog.Builder builder = new org.telegram.ui.ActionBar.AlertDialog.Builder(context);
                                    builder.setTitle("Доступно обновление");
                                    builder.setMessage("Вышла новая версия PrimeGram (" + latestVersion + "). Хотите скачать и установить её сейчас?");
                                    builder.setPositiveButton("Обновить", (dialogInterface, i) -> {
                                        downloadAndInstallUpdate(context, downloadUrl);
                                    });
                                    builder.setNegativeButton("Позже", null);
                                    builder.show();
                                } catch (Exception e) {
                                    Log.e(TAG, "Failed to show update dialog", e);
                                }
                            });
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to check for updates", e);
            }
        }).start();
    }

    public static void downloadAndInstallUpdate(Context context, String downloadUrl) {
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
        request.setTitle("Обновление PrimeGram");
        request.setDescription("Скачивание новой версии...");
        request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "PrimeGram-update.apk");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        long downloadId = manager.enqueue(request);

        BroadcastReceiver onComplete = new BroadcastReceiver() {
            public void onReceive(Context ctxt, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (downloadId == id) {
                    installApk(context, downloadId);
                    context.unregisterReceiver(this);
                }
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        }
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
            // Требуется настроенный FileProvider в AndroidManifest.xml
            return FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file);
        } else {
            return Uri.fromFile(file);
        }
    }

    private static boolean isNewerVersion(String current, String latest) {
        // Упрощенная логика сравнения: удаляем 'v' и сравниваем
        current = current.replace("v", "").replace("-beta", "");
        latest = latest.replace("v", "").replace("-beta", "");
        return latest.compareTo(current) > 0;
    }
}
