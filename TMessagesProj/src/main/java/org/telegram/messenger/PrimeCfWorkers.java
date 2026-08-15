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

    /**
     * Workers volunteered by subscribers (collected via the Cloudflare-worker-collector bot,
     * verified live before being added here), shipped with the app so every install benefits
     * from all of them without each person configuring anything. Grows with app updates as more
     * come in - see cf-worker-bot/collected_domains.txt for the live collection.
     *
     * <p>A single shared worker (or the single shared {@code kwsN.<domain>.co.uk} balancer, or
     * even the single shared {@code 149.154.167.220} redirect) all turned out to be one account's
     * worth of free-tier Cloudflare capacity serving every user of this app at once - this list
     * exists to spread that same load across as many separate free-tier accounts as subscribers
     * are willing to spin up, since each one only costs its owner twenty lines of JavaScript.
     */
    private static final String[] BUNDLED_DOMAINS = new String[]{
            // populated as subscribers submit and the bot verifies their workers
            "aged-smoke-43f9.niellon2.workers.dev",
            "aged-snow-0e00.yumakayev14.workers.dev",
            "broken-cell-3480.luna-f94.workers.dev",
            "divine-sound-f867.primeevolutionzero.workers.dev",
            "floral-art-45f8.klukvamorsov-lol.workers.dev",
            "fragrant-field-ee6c.klukvamorsov.workers.dev",
            "little-star-2ae0.ramil14415.workers.dev",
            "nameless-sun-37d7.klukvamorsov.workers.dev",
            "raspy-lake-fbd0.rematchclient.workers.dev",
            "rough-term-d9f6.luna-f94.workers.dev",
            "royal-tooth-e689.klukvamorsov.workers.dev",
            "shiny-star-222a.goghog688.workers.dev",
            "small-morning-1f6b.klukvalab.workers.dev",
            "sparkling-wildflower-e9c0.niellon1.workers.dev",
            "white-scene-eba6.niellon.workers.dev",
            "yellow-bread-af76.luna-f94.workers.dev",
            "yellow-flower-a227.alekspolejaev13.workers.dev",
            "young-grass-fd67.klukvamorsov.workers.dev",
            "broken-math-a86c.swet302003.workers.dev",
            "wandering-dawn-9800.artem-ponkratov-2000.workers.dev",
    };

    /** How long a worker that just failed sits out before being tried again - short, because a
     *  Worker hitting its free daily quota recovers on its own, and a transient network blip
     *  clears in seconds; long enough that a genuinely dead one does not eat every connection's
     *  first attempt. */
    private static final long SICK_COOLDOWN_MS = 5 * 60_000L;
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> sickUntil = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Random RANDOM = new java.util.Random();

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

    /** In the order the user added them; kept for the settings screen's own list display. */
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

    /**
     * The user's own configured workers plus every bundled one, deduplicated, shuffled, and with
     * anything currently sick filtered out (or kept in if that would leave nothing to try at
     * all). Shuffling per call is what actually spreads load across the whole pool - a fixed
     * order means every device's first attempt lands on the same worker until it falls over,
     * exactly the failure mode this list exists to avoid.
     */
    public static List<String> getShuffledHealthyDomains() {
        final java.util.LinkedHashSet<String> all = new java.util.LinkedHashSet<>(getDomains());
        for (String d : BUNDLED_DOMAINS) {
            all.add(d);
        }
        final List<String> healthy = new ArrayList<>();
        final long now = System.currentTimeMillis();
        for (String d : all) {
            final Long until = sickUntil.get(d);
            if (until == null || until <= now) {
                healthy.add(d);
            }
        }
        final List<String> pool = healthy.isEmpty() ? new ArrayList<>(all) : healthy;
        java.util.Collections.shuffle(pool, RANDOM);
        return pool;
    }

    public static void markSick(String domain) {
        sickUntil.put(domain, System.currentTimeMillis() + SICK_COOLDOWN_MS);
    }

    public static void markHealthy(String domain) {
        sickUntil.remove(domain);
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
