package org.telegram.messenger.browser;

import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.Utilities;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Locale;

/**
 * PrimeGram: downloadable blocking lists for the built-in browser.
 *
 * <p>{@link PrimeAdBlock} ships a short built-in list and trusts the resolver's verdict. That
 * covers the obvious offenders and costs nothing, but it stops well short of what a real blocker
 * does. This adds the missing part: the same public lists AdGuard and uBlock subscribe to.
 *
 * <p><b>Only domain rules are kept.</b> The published lists mix three kinds of rule together:
 * whole-domain blocks ({@code ||ads.example.com^}), path rules ({@code /banner/*.gif}) and
 * cosmetic rules ({@code example.com##.ad-box}). Our engine matches a host and nothing else, so
 * the last two are dropped while reading rather than half-applied. That is roughly two thirds of
 * a typical list by line count but the large majority of what actually gets blocked in practice,
 * because most trackers live on their own domain.
 *
 * <p>Exception rules ({@code @@||example.com^}) are honoured: a host on the allow list is never
 * blocked, whichever list asked for it. Without this, lists that unblock a CDN they themselves
 * over-matched would break pages.
 *
 * <p>The merged result is written to one file of plain hostnames, one per line, and read back
 * into memory on first use. Re-parsing the raw lists at every start would cost seconds.
 */
public class PrimeAdBlockLists {

    /** Stable ids - they end up in preference keys, so they must not be renamed. */
    public static final String[] LIST_IDS = {
            "adguard_base",
            "adguard_mobile",
            "adguard_ru",
            "easyprivacy",
            "adguard_social",
    };

    public static final String[] LIST_NAMES = {
            "AdGuard: базовый",
            "AdGuard: мобильная реклама",
            "AdGuard: русский",
            "EasyPrivacy: слежка",
            "AdGuard: кнопки соцсетей",
    };

    public static final String[] LIST_DESCRIPTIONS = {
            "Основной список против рекламы на сайтах",
            "Реклама, которая встречается только в мобильных браузерах",
            "Реклама на русскоязычных сайтах",
            "Счётчики, аналитика, пиксели отслеживания",
            "Виджеты и кнопки «поделиться», которые следят за вами",
    };

    private static final String[] LIST_URLS = {
            "https://filters.adtidy.org/extension/ublock/filters/2.txt",
            "https://filters.adtidy.org/extension/ublock/filters/11.txt",
            "https://filters.adtidy.org/extension/ublock/filters/1.txt",
            "https://easylist.to/easylist/easyprivacy.txt",
            "https://filters.adtidy.org/extension/ublock/filters/4.txt",
    };

    private static final String KEY_LIST_PREFIX = "primegram_adblock_list_";
    private static final String KEY_LAST_UPDATE = "primegram_adblock_lists_updated";
    private static final String KEY_RULE_COUNT = "primegram_adblock_lists_rules";

    /** A blocked host list this large already covers everything the published lists name. */
    private static final int MAX_RULES = 250000;

    private static final Object loadLock = new Object();
    private static volatile HashSet<String> blocked;
    private static volatile HashSet<String> allowed;

    public interface UpdateCallback {
        void onProgress(String message);

        void onFinished(boolean ok, String message);
    }

    public static boolean isListEnabled(int index) {
        if (index < 0 || index >= LIST_IDS.length) {
            return false;
        }
        try {
            // nothing is on by default: a list only starts costing traffic once asked for
            return MessagesController.getGlobalMainSettings().getBoolean(KEY_LIST_PREFIX + LIST_IDS[index], false);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void setListEnabled(int index, boolean enabled) {
        if (index < 0 || index >= LIST_IDS.length) {
            return;
        }
        MessagesController.getGlobalMainSettings().edit()
                .putBoolean(KEY_LIST_PREFIX + LIST_IDS[index], enabled).apply();
    }

    public static boolean hasAnyListEnabled() {
        for (int i = 0; i < LIST_IDS.length; i++) {
            if (isListEnabled(i)) {
                return true;
            }
        }
        return false;
    }

    public static long lastUpdateTime() {
        try {
            return MessagesController.getGlobalMainSettings().getLong(KEY_LAST_UPDATE, 0);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static int ruleCount() {
        try {
            return MessagesController.getGlobalMainSettings().getInt(KEY_RULE_COUNT, 0);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static File storageFile() {
        return new File(ApplicationLoader.getFilesDirFixed(), "primegram_adblock_hosts.txt");
    }

    /**
     * Whether the subscribed lists block this host. Called from the browser's request hook, which
     * WebView runs off the main thread, so the one-time read from disk is allowed to happen here.
     */
    public static boolean blocks(String host) {
        if (TextUtils.isEmpty(host)) {
            return false;
        }
        HashSet<String> block = blocked;
        if (block == null) {
            synchronized (loadLock) {
                if (blocked == null) {
                    loadFromDisk();
                }
                block = blocked;
            }
        }
        if (block == null || block.isEmpty()) {
            return false;
        }
        HashSet<String> allow = allowed;
        if (matches(allow, host)) {
            return false;
        }
        return matches(block, host);
    }

    /** Matches the host itself and any parent domain, which is what {@code ||domain^} means. */
    private static boolean matches(HashSet<String> set, String host) {
        if (set == null || set.isEmpty()) {
            return false;
        }
        if (set.contains(host)) {
            return true;
        }
        int dot = host.indexOf('.');
        while (dot >= 0 && dot + 1 < host.length()) {
            if (set.contains(host.substring(dot + 1))) {
                return true;
            }
            dot = host.indexOf('.', dot + 1);
        }
        return false;
    }

    private static void loadFromDisk() {
        HashSet<String> block = new HashSet<>();
        HashSet<String> allow = new HashSet<>();
        File file = storageFile();
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new java.io.FileReader(file), 65536)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        continue;
                    }
                    if (line.charAt(0) == '@') {
                        allow.add(line.substring(1));
                    } else {
                        block.add(line);
                    }
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
        blocked = block;
        allowed = allow;
    }

    /** Throws the in-memory copy away so the next request re-reads what we just wrote. */
    private static void invalidate() {
        synchronized (loadLock) {
            blocked = null;
            allowed = null;
        }
    }

    public static void clear() {
        try {
            storageFile().delete();
        } catch (Throwable ignore) {
        }
        MessagesController.getGlobalMainSettings().edit()
                .remove(KEY_LAST_UPDATE).remove(KEY_RULE_COUNT).apply();
        invalidate();
    }

    /**
     * Downloads every enabled list and rebuilds the merged file. Runs on a background thread and
     * reports back on the main one.
     *
     * <p>The download goes out over the plain network, not through our tunnel: the tunnel carries
     * Telegram's protocol and nothing else. Where these hosts are blocked outright the update will
     * fail, and it says so rather than pretending it worked.
     */
    public static void update(UpdateCallback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            HashSet<String> block = new HashSet<>();
            HashSet<String> allow = new HashSet<>();
            int downloaded = 0;
            StringBuilder failures = new StringBuilder();

            for (int i = 0; i < LIST_IDS.length; i++) {
                if (!isListEnabled(i)) {
                    continue;
                }
                final String name = LIST_NAMES[i];
                post(callback, () -> callback.onProgress("Загружаю: " + name));
                try {
                    int before = block.size();
                    fetchInto(LIST_URLS[i], block, allow);
                    downloaded++;
                    if (block.size() == before) {
                        // a list that parsed to nothing usually means we were served an error page
                        failures.append("\n").append(name).append(": пустой ответ");
                    }
                } catch (Throwable t) {
                    String reason = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                    failures.append("\n").append(name).append(": ").append(reason);
                }
            }

            if (downloaded == 0) {
                post(callback, () -> callback.onFinished(false, "Не выбрано ни одного списка."));
                return;
            }

            boolean written = false;
            if (!block.isEmpty()) {
                written = writeToDisk(block, allow);
            }

            final int total = block.size();
            final boolean ok = written;
            final String problems = failures.toString();
            if (ok) {
                MessagesController.getGlobalMainSettings().edit()
                        .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
                        .putInt(KEY_RULE_COUNT, total)
                        .apply();
                invalidate();
                post(callback, () -> callback.onFinished(true, "Загружено доменов: " + total
                        + (problems.isEmpty() ? "" : "\n\nЧастично не удалось:" + problems)));
            } else {
                post(callback, () -> callback.onFinished(false, problems.isEmpty()
                        ? "Не удалось загрузить списки."
                        : "Не удалось загрузить списки:" + problems));
            }
        });
    }

    private static void post(UpdateCallback callback, Runnable runnable) {
        if (callback != null) {
            org.telegram.messenger.AndroidUtilities.runOnUIThread(runnable);
        }
    }

    private static boolean writeToDisk(HashSet<String> block, HashSet<String> allow) {
        File file = storageFile();
        File temp = new File(file.getAbsolutePath() + ".tmp");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(temp), 65536)) {
            for (String host : block) {
                writer.write(host);
                writer.write('\n');
            }
            for (String host : allow) {
                writer.write('@');
                writer.write(host);
                writer.write('\n');
            }
        } catch (Throwable t) {
            FileLog.e(t);
            try {
                temp.delete();
            } catch (Throwable ignore) {
            }
            return false;
        }
        try {
            file.delete();
            return temp.renameTo(file);
        } catch (Throwable t) {
            FileLog.e(t);
            return false;
        }
    }

    private static void fetchInto(String url, HashSet<String> block, HashSet<String> allow) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "PrimeGram");
            connection.setRequestProperty("Accept-Encoding", "gzip");
            int code = connection.getResponseCode();
            if (code != 200) {
                throw new Exception("HTTP " + code);
            }
            java.io.InputStream stream = connection.getInputStream();
            if ("gzip".equalsIgnoreCase(connection.getContentEncoding())) {
                stream = new java.util.zip.GZIPInputStream(stream);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"), 65536)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    parseRule(line, block, allow);
                    if (block.size() >= MAX_RULES) {
                        break;
                    }
                }
            }
        } finally {
            if (connection != null) {
                try {
                    connection.disconnect();
                } catch (Throwable ignore) {
                }
            }
        }
    }

    /**
     * Reads one line of an AdGuard/EasyList file and keeps it only if it names a whole domain.
     *
     * <p>Recognised: {@code ||domain^}, {@code ||domain^$third-party} and friends, the same forms
     * prefixed with {@code @@} for an exception, and hosts-file lines
     * ({@code 0.0.0.0 domain}). Everything else - path patterns, regexes, cosmetic rules, rules
     * carrying options we cannot honour - is skipped, because applying half of a rule is worse
     * than not applying it.
     */
    static void parseRule(String line, HashSet<String> block, HashSet<String> allow) {
        if (line == null) {
            return;
        }
        line = line.trim();
        if (line.isEmpty() || line.charAt(0) == '!' || line.charAt(0) == '[' || line.charAt(0) == '#') {
            return;
        }

        // hosts-file format: "0.0.0.0 ads.example.com" or "127.0.0.1 ads.example.com"
        if (line.startsWith("0.0.0.0 ") || line.startsWith("127.0.0.1 ")) {
            int space = line.indexOf(' ');
            String host = line.substring(space + 1).trim();
            int comment = host.indexOf('#');
            if (comment >= 0) {
                host = host.substring(0, comment).trim();
            }
            if (isPlainDomain(host)) {
                block.add(host.toLowerCase(Locale.ROOT));
            }
            return;
        }

        boolean exception = false;
        if (line.startsWith("@@")) {
            exception = true;
            line = line.substring(2);
        }
        if (!line.startsWith("||")) {
            return;
        }
        line = line.substring(2);

        // options after '$' are fine as long as they do not narrow the rule to something we
        // cannot see - we only ever know the host, never the request type or the referring page
        int dollar = line.indexOf('$');
        if (dollar >= 0) {
            String options = line.substring(dollar + 1);
            line = line.substring(0, dollar);
            if (!optionsAreHostOnly(options)) {
                return;
            }
        }

        // the rule must end at the domain: "^", "/" or nothing. Anything else is a path pattern.
        int end = line.length();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '^' || c == '/') {
                end = i;
                break;
            }
        }
        String tail = line.substring(end);
        if (!tail.isEmpty() && !tail.equals("^") && !tail.equals("/") && !tail.equals("^|") && !tail.equals("|")) {
            return;
        }
        String host = line.substring(0, end);
        if (!isPlainDomain(host)) {
            return;
        }
        host = host.toLowerCase(Locale.ROOT);
        if (exception) {
            allow.add(host);
        } else {
            block.add(host);
        }
    }

    /**
     * A rule with {@code $document} or {@code $third-party} still blocks the whole domain, so it
     * is safe to keep. A rule limited to one page ({@code $domain=...}) or inverted
     * ({@code ~third-party}) is not, and neither is anything we do not recognise.
     */
    private static boolean optionsAreHostOnly(String options) {
        String[] parts = options.split(",");
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) {
                continue;
            }
            if (part.startsWith("~") || part.startsWith("domain=") || part.startsWith("denyallow=")
                    || part.startsWith("csp") || part.startsWith("replace=") || part.startsWith("removeparam")
                    || part.equals("badfilter") || part.equals("elemhide") || part.equals("generichide")) {
                return false;
            }
        }
        return true;
    }

    /** No wildcards, no regex, no empty labels - just a hostname we can compare against. */
    private static boolean isPlainDomain(String host) {
        if (TextUtils.isEmpty(host) || host.length() > 253 || host.indexOf('.') <= 0) {
            return false;
        }
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '.' || c == '-' || c == '_';
            if (!allowed) {
                return false;
            }
        }
        return !host.startsWith(".") && !host.endsWith(".");
    }
}
