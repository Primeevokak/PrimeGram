package org.telegram.messenger.music;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** Resolves a track/album link to a direct audio URL via a Cobalt instance and downloads it. */
public class CobaltDownloader {

    public static final String DEFAULT_API_URL = "https://cobalt.255x.ru";

    /** Downloads the audio at {@code sourceUrl} into {@code destDir}/{@code fileNameNoExt}.*, returns the resulting file or null on failure. */
    public static File download(String apiUrl, String sourceUrl, File destDir, String fileNameNoExt) {
        String api = (apiUrl == null || apiUrl.isEmpty()) ? DEFAULT_API_URL : apiUrl;
        try {
            JSONObject payload = new JSONObject();
            payload.put("url", sourceUrl);
            payload.put("downloadMode", "audio");
            payload.put("audioBitrate", "320");
            payload.put("audioFormat", "best");

            Map<String, String> headers = new HashMap<>();
            headers.put("Accept", "application/json");

            JSONObject resp = MusicHttp.postJson(api + "/", payload, headers);
            if (resp == null) {
                return null;
            }
            String status = resp.optString("status", "");
            String directUrl;
            String originalFilename;

            if ("error".equals(status)) {
                JSONObject err = resp.optJSONObject("error");
                FileLog.e("CobaltDownloader: " + (err != null ? err.optString("code") : "unknown error"));
                return null;
            } else if ("picker".equals(status)) {
                JSONArray items = resp.optJSONArray("picker");
                if (items == null || items.length() == 0) {
                    return null;
                }
                JSONObject item = items.optJSONObject(0);
                directUrl = item.optString("url", null);
                originalFilename = item.optString("filename", null);
            } else if ("stream".equals(status) || "redirect".equals(status) || "success".equals(status) || "tunnel".equals(status)) {
                directUrl = resp.optString("url", null);
                originalFilename = resp.optString("filename", null);
            } else {
                FileLog.e("CobaltDownloader: unhandled status " + status);
                return null;
            }

            if (directUrl == null || directUrl.isEmpty()) {
                return null;
            }

            String ext = "mp3";
            if (originalFilename != null && originalFilename.contains(".")) {
                ext = originalFilename.substring(originalFilename.lastIndexOf('.') + 1);
            }
            if (!destDir.exists()) {
                destDir.mkdirs();
            }
            File dest = new File(destDir, fileNameNoExt + "." + ext);
            return MusicHttp.downloadToFile(directUrl, dest) ? dest : null;
        } catch (Exception e) {
            FileLog.e("CobaltDownloader.download", e);
            return null;
        }
    }
}
