package org.telegram.ui.Components;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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

    public static void checkForUpdates(Context context, String currentVersion, boolean isManual) {
        new Thread(() -> {
            try {
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                
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

                URL url = new URL("https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest");
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

                    JSONObject json = new JSONObject(response.toString());
                    String latestVersion = json.getString("tag_name");

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
                            boolean autoUpdate = mainPrefs.getBoolean("primegram_auto_updates", false);
                            
                            org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
                                if (autoUpdate && !isManual) {
                                    downloadAndInstallUpdate(context, downloadUrl, latestVersion);
                                    android.widget.Toast.makeText(context, "Скачивание обновления PrimeGram...", android.widget.Toast.LENGTH_SHORT).show();
                                } else {
                                    try {
                                        org.telegram.ui.ActionBar.AlertDialog.Builder builder = new org.telegram.ui.ActionBar.AlertDialog.Builder(context);
                                        builder.setTitle("Доступно обновление");
                                        builder.setMessage("Вышла новая версия PrimeGram (" + latestVersion + "). Хотите скачать и установить её?");
                                        builder.setPositiveButton("Обновить", (dialogInterface, i) -> {
                                            downloadAndInstallUpdate(context, downloadUrl, latestVersion);
                                        });
                                        builder.setNegativeButton("Позже", (dialogInterface, i) -> {
                                            prefs.edit().putLong(KEY_LATER_TIME, System.currentTimeMillis()).apply();
                                        });
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
        android.app.DownloadManager.Request request = new android.app.DownloadManager.Request(android.net.Uri.parse(downloadUrl));
        request.setTitle("Обновление PrimeGram");
        request.setDescription("Скачивание новой версии...");
        request.setDestinationInExternalFilesDir(context, android.os.Environment.DIRECTORY_DOWNLOADS, "PrimeGram-update.apk");
        request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        android.app.DownloadManager manager = (android.app.DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        long downloadId = manager.enqueue(request);

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("downloaded_version", latestVersion).apply();

        android.content.BroadcastReceiver onComplete = new android.content.BroadcastReceiver() {
            public void onReceive(Context ctxt, android.content.Intent intent) {
                long id = intent.getLongExtra(android.app.DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (downloadId == id) {
                    installApk(context, downloadId);
                    context.unregisterReceiver(this);
                }
            }
        };

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(onComplete, new android.content.IntentFilter(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(onComplete, new android.content.IntentFilter(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE));
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
            return FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file);
        } else {
            return Uri.fromFile(file);
        }
    }

    private static boolean isNewerVersion(String current, String latest) {
        try {
            current = current.replaceAll("[^0-9.]", "");
            latest = latest.replaceAll("[^0-9.]", "");
            
            if (latest.isEmpty()) return false;
            if (current.isEmpty()) return true;

            String[] cParts = current.split("\\.");
            String[] lParts = latest.split("\\.");
            
            int length = Math.max(cParts.length, lParts.length);
            for (int i = 0; i < length; i++) {
                int c = i < cParts.length && !cParts[i].isEmpty() ? Integer.parseInt(cParts[i]) : 0;
                int l = i < lParts.length && !lParts[i].isEmpty() ? Integer.parseInt(lParts[i]) : 0;
                if (l > c) return true;
                if (l < c) return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing version", e);
        }
        return false;
    }
}
