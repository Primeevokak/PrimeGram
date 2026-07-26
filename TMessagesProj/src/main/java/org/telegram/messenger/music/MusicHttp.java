package org.telegram.messenger.music;

import org.json.JSONObject;
import org.telegram.messenger.FileLog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Small shared HTTP client for {@link org.telegram.messenger.music.providers} — blocking, call off the UI thread. */
public class MusicHttp {

    private static final int TIMEOUT_MS = 12000;

    public static JSONObject getJson(String url, Map<String, String> headers) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    conn.setRequestProperty(e.getKey(), e.getValue());
                }
            }
            int code = conn.getResponseCode();
            if (code != 200) {
                return null;
            }
            return new JSONObject(readAll(conn.getInputStream()));
        } catch (Exception e) {
            FileLog.e("MusicHttp.getJson " + url, e);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public static JSONObject postJson(String url, JSONObject body, Map<String, String> headers) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(30000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    conn.setRequestProperty(e.getKey(), e.getValue());
                }
            }
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            if (stream == null) {
                return null;
            }
            return new JSONObject(readAll(stream));
        } catch (Exception e) {
            FileLog.e("MusicHttp.postJson " + url, e);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Downloads a URL straight to a file on disk. Returns true on success. */
    public static boolean downloadToFile(String url, java.io.File dest) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(60000);
            int code = conn.getResponseCode();
            if (code != 200) {
                return false;
            }
            try (InputStream in = conn.getInputStream();
                 java.io.FileOutputStream out = new java.io.FileOutputStream(dest)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
            }
            return true;
        } catch (Exception e) {
            FileLog.e("MusicHttp.downloadToFile " + url, e);
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readAll(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}
