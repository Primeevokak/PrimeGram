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
        String currentVersion = "11.6.0";
        try {
            currentVersion = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception e) {}
        org.telegram.ui.Components.GithubUpdater.checkForUpdates(context, currentVersion, isManual);
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
