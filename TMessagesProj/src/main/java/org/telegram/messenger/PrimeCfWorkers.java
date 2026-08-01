package org.telegram.messenger;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * PrimeGram: the user's own Cloudflare Workers, used as a way through when ours are blocked.
 *
 * <p>A Worker is twenty lines of JavaScript running on Cloudflare's edge, free and without a
 * domain of your own. It accepts a WebSocket at {@code /apiws?dst=<ip>&dc=<n>}, opens a plain TCP
 * connection to that address and passes bytes between the two. Nothing about Telegram is in it -
 * it is a pipe, and what travels through the pipe is the same obfuscated MTProto the client would
 * have sent to the data centre directly.
 *
 * <p>Why it helps: the address the client reaches is {@code something.workers.dev}, which belongs
 * to Cloudflare and is used by an enormous amount of unrelated traffic. Blocking it costs whoever
 * does the blocking a great deal more than it costs us.
 *
 * <p>Why it is worth having several: a Worker is tied to one Cloudflare account and has a request
 * budget. Several of them spread the load and, more to the point, mean one going away is not the
 * end of it.
 */
public final class PrimeCfWorkers {

    private static final String KEY_ENABLED = "primegram_cf_workers_enabled";
    private static final String KEY_DOMAINS = "primegram_cf_worker_domains";

    private PrimeCfWorkers() {
    }

    public static boolean isEnabled() {
        try {
            return MessagesController.getGlobalMainSettings().getBoolean(KEY_ENABLED, false);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void setEnabled(boolean enabled) {
        MessagesController.getGlobalMainSettings().edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    /** In the order the user added them; the connector walks the list from the top. */
    public static List<String> getDomains() {
        final List<String> domains = new ArrayList<>();
        try {
            final String stored = MessagesController.getGlobalMainSettings().getString(KEY_DOMAINS, "");
            if (TextUtils.isEmpty(stored)) {
                return domains;
            }
            for (String part : stored.split(",")) {
                final String domain = part.trim();
                if (!domain.isEmpty()) {
                    domains.add(domain);
                }
            }
        } catch (Throwable ignore) {
        }
        return domains;
    }

    private static void save(List<String> domains) {
        MessagesController.getGlobalMainSettings().edit()
                .putString(KEY_DOMAINS, TextUtils.join(",", domains)).apply();
    }

    /**
     * Adds one, however it was written down.
     *
     * @return null on success, otherwise why it was not added
     */
    public static String add(String value) {
        final String domain = normalize(value);
        if (domain == null) {
            return "Это не похоже на адрес воркера. Ожидается что-то вроде name-1234.username.workers.dev";
        }
        final List<String> domains = getDomains();
        if (domains.contains(domain)) {
            return "Этот воркер уже добавлен";
        }
        domains.add(domain);
        save(domains);
        return null;
    }

    public static void remove(String domain) {
        final List<String> domains = getDomains();
        if (domains.remove(domain)) {
            save(domains);
        }
    }

    /**
     * Accepts what a person actually copies: a bare host, a full URL, with or without a path, with
     * stray spaces. Anything that does not end up looking like a host name is refused rather than
     * quietly stored, because a typo here shows up much later as "the tunnel does not work".
     */
    public static String normalize(String value) {
        if (TextUtils.isEmpty(value)) {
            return null;
        }
        String domain = value.trim().toLowerCase(Locale.ROOT);
        final int scheme = domain.indexOf("://");
        if (scheme >= 0) {
            domain = domain.substring(scheme + 3);
        }
        final int slash = domain.indexOf('/');
        if (slash >= 0) {
            domain = domain.substring(0, slash);
        }
        final int at = domain.indexOf('@');
        if (at >= 0) {
            domain = domain.substring(at + 1);
        }
        final int colon = domain.indexOf(':');
        if (colon >= 0) {
            domain = domain.substring(0, colon);
        }
        if (domain.isEmpty() || !domain.contains(".") || domain.contains(" ")) {
            return null;
        }
        for (int i = 0; i < domain.length(); i++) {
            final char c = domain.charAt(i);
            if (!(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9') && c != '.' && c != '-') {
                return null;
            }
        }
        return domain;
    }
}
