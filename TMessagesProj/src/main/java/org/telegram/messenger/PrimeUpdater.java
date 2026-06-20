package org.telegram.messenger;

import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class PrimeUpdater {
    private static final String GITHUB_API_URL = "https://api.github.com/repos/Primeevokak/PrimeGram/releases/latest";

    public static void checkUpdate(final Context context, final boolean isManual) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                URL url = new URL(GITHUB_API_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    String inputLine;
                    StringBuilder response = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();

                    JSONObject jsonResponse = new JSONObject(response.toString());
                    String tagName = jsonResponse.optString("tag_name", "").replace("v", "");
                    
                    String currentVersion = "";
                    try {
                        currentVersion = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
                    } catch (Exception e) {
                        FileLog.e(e);
                    }

                    // Compare version names instead of saved timestamps
                    boolean hasUpdate = false;
                    if (!tagName.isEmpty() && !currentVersion.isEmpty()) {
                        // Very simple check: if the tag doesn't match the current version, it's an update.
                        // (Ideally, parse and compare semver, but this stops the downgrade loop)
                        if (!tagName.equals(currentVersion)) {
                            // If the tag is something like "12.8.2" and current is "12.8.1", equals is false.
                            // To be safer, only update if tagName doesn't match currentVersion AND we actually want to update.
                            hasUpdate = true;
                        }
                    }

                    if (hasUpdate || isManual) {
                        JSONArray assets = jsonResponse.optJSONArray("assets");
                        if (assets != null && assets.length() > 0) {
                            String downloadUrl = null;
                            String fileName = null;
                            for (int i = 0; i < assets.length(); i++) {
                                JSONObject asset = assets.getJSONObject(i);
                                String name = asset.optString("name", "");
                                if (name.endsWith(".apk")) {
                                    downloadUrl = asset.optString("browser_download_url");
                                    fileName = name;
                                    break;
                                }
                            }

                            if (downloadUrl != null) {
                                final String finalDownloadUrl = downloadUrl;
                                final String finalFileName = fileName;
                                AndroidUtilities.runOnUIThread(() -> {
                                    if (isManual) {
                                        Toast.makeText(context, "Найдено обновление (" + tagName + ")! Начинаем загрузку...", Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(context, "Доступна новая версия PrimeGram (" + tagName + "). Скачиваем обновление...", Toast.LENGTH_LONG).show();
                                    }
                                    startDownload(context, finalDownloadUrl, finalFileName);
                                });
                            } else {
                                if (isManual) {
                                    AndroidUtilities.runOnUIThread(() -> Toast.makeText(context, "APK файл не найден в релизе GitHub.", Toast.LENGTH_SHORT).show());
                                }
                            }
                        } else {
                            if (isManual) {
                                AndroidUtilities.runOnUIThread(() -> Toast.makeText(context, "В релизе нет прикрепленных файлов.", Toast.LENGTH_SHORT).show());
                            }
                        }
                    } else {
                        if (isManual) {
                            AndroidUtilities.runOnUIThread(() -> Toast.makeText(context, "У вас установлена последняя версия.", Toast.LENGTH_SHORT).show());
                        }
                    }
                } else {
                    if (isManual) {
                        AndroidUtilities.runOnUIThread(() -> Toast.makeText(context, "Ошибка проверки обновлений. Код: " + responseCode, Toast.LENGTH_SHORT).show());
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
                if (isManual) {
                    AndroidUtilities.runOnUIThread(() -> Toast.makeText(context, "Ошибка сети при проверке обновлений GitHub.", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private static void startDownload(Context context, String url, String fileName) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle("Обновление PrimeGram");
            request.setDescription("Загрузка новой версии...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            request.setMimeType("application/vnd.android.package-archive");

            DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager != null) {
                manager.enqueue(request);
            }
        } catch (Exception e) {
            FileLog.e(e);
            Toast.makeText(context, "Ошибка запуска загрузки. Проверьте разрешения памяти.", Toast.LENGTH_SHORT).show();
        }
    }
}
