package org.telegram.messenger.browser;

import android.net.Uri;
import android.text.TextUtils;
import android.webkit.WebResourceResponse;

import org.telegram.messenger.MessagesController;

import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.Locale;

/**
 * PrimeGram: ad and tracker blocking for the built-in browser.
 *
 * <p>Two layers, borrowed from how AdGuard itself works:
 *
 * <ul>
 *   <li><b>DNS.</b> AdGuard's default resolver answers 0.0.0.0 (or NXDOMAIN) for ad and tracker
 *       domains. Since {@link PrimeDns} already asks it every question, that answer is a
 *       ready-made verdict — no filter list to ship, download or keep current, and it tracks
 *       AdGuard's own list automatically. Costs nothing extra: the lookup is cached and would
 *       have happened anyway.</li>
 *   <li><b>A small built-in host list.</b> Covers the obvious offenders instantly, works
 *       offline, and still works when the user picks a non-filtering resolver. Deliberately
 *       short: a long list belongs in a downloadable subscription, not in the APK.</li>
 * </ul>
 *
 * <p>What this is <i>not</i>: cosmetic filtering. AdGuard also hides leftover empty ad frames
 * with per-site CSS rules. Blocking the request is the part that saves traffic and privacy;
 * the empty boxes are a separate, much larger job.
 */
public class PrimeAdBlock {

    public static final String KEY_ENABLED = "primegram_adblock_enabled";
    public static final String KEY_USE_DNS = "primegram_adblock_use_dns";

    private static long blockedCount;

    /**
     * Hosts blocked outright. Matched on the host itself and on any subdomain of it, which is
     * how "||domain^" behaves in AdGuard/ABP syntax.
     */
    private static final HashSet<String> BUILT_IN = new HashSet<>();

    static {
        String[] hosts = {
                // Google ads / measurement
                "doubleclick.net", "googlesyndication.com", "googleadservices.com",
                "googletagservices.com", "googletagmanager.com", "google-analytics.com",
                "adservice.google.com", "pagead2.googlesyndication.com",
                // Other large ad networks
                "adnxs.com", "rubiconproject.com", "pubmatic.com", "criteo.com", "criteo.net",
                "outbrain.com", "taboola.com", "adform.net", "casalemedia.com", "openx.net",
                "smartadserver.com", "3lift.com", "sharethrough.com", "media.net",
                "advertising.com", "adsrvr.org", "bidswitch.net", "teads.tv", "moatads.com",
                // Analytics / tracking
                "scorecardresearch.com", "quantserve.com", "hotjar.com", "mixpanel.com",
                "segment.io", "segment.com", "amplitude.com", "fullstory.com", "mouseflow.com",
                "clarity.ms", "branch.io", "appsflyer.com", "adjust.com", "kochava.com",
                "crashlytics.com", "newrelic.com", "optimizely.com", "chartbeat.com",
                // Social trackers
                "connect.facebook.net", "graph.facebook.com", "analytics.tiktok.com",
                "ads-twitter.com", "analytics.twitter.com", "px.ads.linkedin.com",
                // RU/CIS
                "an.yandex.ru", "mc.yandex.ru", "yandexadexchange.net", "adfox.ru",
                "top-fwz1.mail.ru", "ad.mail.ru", "rb.mail.ru", "counter.rambler.ru",
                "smi2.ru", "smi2.net", "luckyads.pro", "propellerads.com", "adsterra.com",
        };
        for (String host : hosts) {
            BUILT_IN.add(host);
        }
    }

    public static boolean isEnabled() {
        try {
            return MessagesController.getGlobalMainSettings().getBoolean(KEY_ENABLED, true);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void setEnabled(boolean enabled) {
        MessagesController.getGlobalMainSettings().edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    /** Whether the resolver's own verdict counts. Off means built-in list only. */
    public static boolean isDnsBlockingEnabled() {
        try {
            return MessagesController.getGlobalMainSettings().getBoolean(KEY_USE_DNS, true);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void setDnsBlockingEnabled(boolean enabled) {
        MessagesController.getGlobalMainSettings().edit().putBoolean(KEY_USE_DNS, enabled).apply();
    }

    public static long getBlockedCount() {
        return blockedCount;
    }

    public static void resetBlockedCount() {
        blockedCount = 0;
    }

    /**
     * Called from {@code shouldInterceptRequest}, which WebView runs off the main thread — the
     * DNS lookup inside is allowed to block, and after the first request per host it is a cache
     * hit anyway.
     *
     * @return a response to serve instead of the real one, or null to let the request through.
     */
    public static WebResourceResponse maybeBlock(String url) {
        if (!isEnabled() || TextUtils.isEmpty(url)) {
            return null;
        }
        String host;
        try {
            host = Uri.parse(url).getHost();
        } catch (Throwable t) {
            return null;
        }
        if (TextUtils.isEmpty(host)) {
            return null;
        }
        host = host.toLowerCase(Locale.ROOT);
        if (!matchesBuiltIn(host) && !(isDnsBlockingEnabled() && PrimeDns.isRefusedByResolver(host))) {
            return null;
        }
        blockedCount++;
        // An empty 200 rather than an error: a blocked script that "loads" and does nothing
        // breaks far fewer pages than one that fails outright.
        return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
    }

    private static boolean matchesBuiltIn(String host) {
        if (BUILT_IN.contains(host)) {
            return true;
        }
        int dot = host.indexOf('.');
        while (dot >= 0 && dot + 1 < host.length()) {
            if (BUILT_IN.contains(host.substring(dot + 1))) {
                return true;
            }
            dot = host.indexOf('.', dot + 1);
        }
        return false;
    }
}
