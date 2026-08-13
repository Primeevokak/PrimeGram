package org.telegram.messenger.music.providers;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.music.MusicHttp;
import org.telegram.messenger.music.Track;
import org.telegram.messenger.music.TrackProvider;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * YouTube Music, via the same private "innertube" API the music.youtube.com web player itself
 * calls (there is no public API for "what is this account currently playing/recently played") -
 * ported from reSwaga's own implementation (disassembled from its compiled bytecode, since it
 * ships no source: {@code utils/platform_utils.pyc}'s {@code build_api_link}/{@code build_headers}
 * /{@code build_params}/{@code decode_api_response} for this platform).
 *
 * <p>Auth is a cookie captured from a real Google sign-in (see {@link
 * org.telegram.ui.MusicYtmLoginActivity}), the same way reSwaga's own {@code create_auth_alert}
 * does it - not a login PrimeGram performs itself, only a cookie jar it stores. Every request signs
 * itself with {@code SAPISIDHASH}, exactly how the browser does for any Google endpoint using
 * {@code SAPISID}/{@code __Secure-3PAPISID} cookie auth: {@code SHA1(timestamp + " " + sapisid +
 * " " + origin)}.
 *
 * <p>Reads the account's "history" shelf (browseId {@code FEmusic_history}) and takes its first
 * entry - YouTube Music's internal API has no true real-time "now playing" endpoint a third party
 * can call, so the most recent history entry is the closest available signal, same approximation
 * reSwaga itself makes.
 */
public class YoutubeMusicProvider implements TrackProvider {

    private static final String BROWSE_URL = "https://music.youtube.com/youtubei/v1/browse?prettyPrint=false";
    private static final String ORIGIN = "https://music.youtube.com";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";
    private static final Pattern SAPISID_PATTERN = Pattern.compile("(?:^|;\\s*)(?:SAPISID|__Secure-3PAPISID)=([^;]+)");

    private final String cookie;

    public YoutubeMusicProvider(String cookie) {
        this.cookie = cookie == null ? "" : cookie.trim();
    }

    @Override
    public boolean isConfigured() {
        return !cookie.isEmpty() && SAPISID_PATTERN.matcher(cookie).find();
    }

    @Override
    public boolean canDownloadTrack() {
        // No song.link code assigned for this platform (see MusicPlatform) rather than guess at
        // one and risk resolving to the wrong track - "unavailable" is an honest default.
        return false;
    }

    @Override
    public Track getTrack() {
        if (!isConfigured()) {
            return Track.inactive();
        }
        final Map<String, String> headers = buildHeaders();
        if (headers == null) {
            return Track.inactive();
        }
        final JSONObject body = new JSONObject();
        try {
            final JSONObject context = new JSONObject();
            final JSONObject client = new JSONObject();
            client.put("clientName", "WEB_REMIX");
            client.put("clientVersion", "1.20231212.01.00");
            context.put("client", client);
            body.put("context", context);
            body.put("browseId", "FEmusic_history");
        } catch (Exception e) {
            return Track.inactive();
        }
        final JSONObject response = MusicHttp.postJson(BROWSE_URL, body, headers);
        if (response == null) {
            return Track.inactive();
        }
        return decode(response);
    }

    private Map<String, String> buildHeaders() {
        final Matcher m = SAPISID_PATTERN.matcher(cookie);
        if (!m.find()) {
            return null;
        }
        final String sapisid = m.group(1);
        final long timestamp = System.currentTimeMillis() / 1000L;
        final String toHash = timestamp + " " + sapisid + " " + ORIGIN;
        final String hashHex = sha1Hex(toHash);
        if (hashHex == null) {
            return null;
        }
        final Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Origin", ORIGIN);
        headers.put("Cookie", cookie);
        headers.put("Authorization", "SAPISIDHASH " + timestamp + "_" + hashHex);
        return headers;
    }

    private static String sha1Hex(String text) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-1");
            final byte[] bytes = digest.digest(text.getBytes("UTF-8"));
            final StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            FileLog.e("YoutubeMusicProvider.sha1Hex", e);
            return null;
        }
    }

    /** Walks {@code contents.singleColumnBrowseResultsRenderer.tabs[].tabRenderer.content
     *  .sectionListRenderer.contents[].musicShelfRenderer.contents[0]
     *  .musicResponsiveListItemRenderer} for the first (most recent) history entry, same path
     *  reSwaga's own {@code decode_api_response} reads. */
    private Track decode(JSONObject response) {
        try {
            JSONArray tabs = response
                    .optJSONObject("contents")
                    .optJSONObject("singleColumnBrowseResultsRenderer")
                    .optJSONArray("tabs");
            if (tabs == null) {
                return Track.inactive();
            }
            for (int i = 0; i < tabs.length(); i++) {
                JSONArray sections = tabs.optJSONObject(i)
                        .optJSONObject("tabRenderer")
                        .optJSONObject("content")
                        .optJSONObject("sectionListRenderer")
                        .optJSONArray("contents");
                if (sections == null) {
                    continue;
                }
                for (int s = 0; s < sections.length(); s++) {
                    JSONObject shelf = sections.optJSONObject(s).optJSONObject("musicShelfRenderer");
                    if (shelf == null) {
                        continue;
                    }
                    JSONArray items = shelf.optJSONArray("contents");
                    if (items == null || items.length() == 0) {
                        continue;
                    }
                    JSONObject item = items.optJSONObject(0).optJSONObject("musicResponsiveListItemRenderer");
                    if (item == null) {
                        continue;
                    }
                    Track track = trackFromItem(item);
                    if (track != null) {
                        return track;
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e("YoutubeMusicProvider.decode", e);
        }
        return Track.inactive();
    }

    private Track trackFromItem(JSONObject item) {
        JSONArray flexColumns = item.optJSONArray("flexColumns");
        if (flexColumns == null || flexColumns.length() == 0) {
            return null;
        }
        String title = textFromColumn(flexColumns.optJSONObject(0));
        String artist = flexColumns.length() > 1 ? textFromColumn(flexColumns.optJSONObject(1)) : null;
        String videoId = null;
        try {
            videoId = flexColumns.optJSONObject(0)
                    .optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    .optJSONObject("text")
                    .optJSONArray("runs")
                    .optJSONObject(0)
                    .optJSONObject("navigationEndpoint")
                    .optJSONObject("watchEndpoint")
                    .optString("videoId", null);
        } catch (Exception ignore) {
        }
        if (title == null || videoId == null) {
            return null;
        }
        Track track = new Track();
        track.active = true;
        track.id = videoId;
        track.title = title;
        if (artist != null && !artist.isEmpty()) {
            for (String a : artist.split("•|,")) {
                String trimmed = a.trim();
                if (!trimmed.isEmpty()) {
                    track.artists.add(trimmed);
                }
            }
        }
        track.link = "https://music.youtube.com/watch?v=" + videoId;
        track.thumbUrl = thumbFromItem(item);
        return track;
    }

    private String textFromColumn(JSONObject flexColumn) {
        try {
            JSONArray runs = flexColumn.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    .optJSONObject("text")
                    .optJSONArray("runs");
            if (runs == null) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < runs.length(); i++) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(runs.optJSONObject(i).optString("text", ""));
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return null;
        }
    }

    private String thumbFromItem(JSONObject item) {
        try {
            JSONArray thumbs = item.optJSONObject("thumbnail")
                    .optJSONObject("musicThumbnailRenderer")
                    .optJSONObject("thumbnail")
                    .optJSONArray("thumbnails");
            if (thumbs == null || thumbs.length() == 0) {
                return null;
            }
            // Largest thumbnail is listed last.
            return thumbs.optJSONObject(thumbs.length() - 1).optString("url", null);
        } catch (Exception e) {
            return null;
        }
    }
}
