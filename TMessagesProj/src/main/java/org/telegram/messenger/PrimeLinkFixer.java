package org.telegram.messenger;

import android.net.Uri;
import android.text.TextUtils;

import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * PrimeGram: "embed fixers" for link previews. Telegram builds a link preview from what the site
 * itself serves to a crawler, and several large sites serve next to nothing - an X post comes out
 * as a bare title, Instagram and TikTok as a login wall. Community services (fixvx, fxtwitter,
 * kkinstagram, rxddit...) answer the same path on their own domain with a proper card (text,
 * images, playable video) and redirect a human visitor to the original.
 *
 * <p>Only the preview changes: the message text keeps the link exactly as typed, and the preview
 * is requested for a fixed URL instead ({@code inputMediaWebPage} carries its own URL). The
 * composer tries each candidate in order and falls back to the original link when none produces a
 * usable card - a dead service costs a slower preview, never a worse one than plain Telegram.
 */
public final class PrimeLinkFixer {

    private static final class Site {
        final String[] hosts;
        final Pattern path;
        final String[] fixerHosts;

        Site(String[] hosts, String pathRegex, String... fixerHosts) {
            this.hosts = hosts;
            this.path = Pattern.compile(pathRegex);
            this.fixerHosts = fixerHosts;
        }
    }

    // Only links to a single post: a profile or home page gains nothing from a fixer.
    private static final Site[] SITES = {
            new Site(new String[]{"x.com", "twitter.com", "mobile.twitter.com", "mobile.x.com"},
                    "^/[^/]+/status(es)?/\\d+.*", "fixvx.com", "fixupx.com", "vxtwitter.com", "fxtwitter.com"),
            new Site(new String[]{"instagram.com", "m.instagram.com"},
                    "^/(p|reel|reels|tv)/[^/]+.*", "kkinstagram.com", "instagramez.com", "ddinstagram.com"),
            new Site(new String[]{"tiktok.com", "m.tiktok.com", "vm.tiktok.com", "vt.tiktok.com"},
                    "^/(@[^/]+/(video|photo)/\\d+|t/[^/]+|[A-Za-z0-9]+/?$).*", "tnktok.com", "vxtiktok.com", "tiktxk.com"),
            new Site(new String[]{"reddit.com", "old.reddit.com", "new.reddit.com", "m.reddit.com"},
                    "^/(r|u|user)/[^/]+/(comments|s)/.+", "rxddit.com", "vxreddit.com"),
            new Site(new String[]{"bsky.app"},
                    "^/profile/[^/]+/post/[^/]+.*", "fxbsky.app", "bskx.app"),
            new Site(new String[]{"threads.net", "threads.com"},
                    "^/@[^/]+/post/[^/]+.*", "fixthreads.net", "vxthreads.net"),
            new Site(new String[]{"pixiv.net"},
                    "^/(en/)?artworks/\\d+.*", "phixiv.net"),
            new Site(new String[]{"deviantart.com"},
                    "^/[^/]+/art/.+", "fixdeviantart.com"),
            new Site(new String[]{"tumblr.com"},
                    "^/[^/]+/\\d+.*", "tpmblr.com"),
    };

    private PrimeLinkFixer() {
    }

    /**
     * Fixed URLs to try for {@code url}, best first; empty when the link is not a post on a
     * supported site (then the original goes through untouched, as in plain Telegram).
     */
    public static List<String> candidates(String url) {
        if (TextUtils.isEmpty(url)) {
            return Collections.emptyList();
        }
        try {
            String withScheme = url.trim();
            if (!withScheme.regionMatches(true, 0, "http://", 0, 7) && !withScheme.regionMatches(true, 0, "https://", 0, 8)) {
                withScheme = "https://" + withScheme;
            }
            final Uri uri = Uri.parse(withScheme);
            String host = uri.getHost();
            final String path = uri.getEncodedPath();
            if (host == null || path == null) {
                return Collections.emptyList();
            }
            host = host.toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            for (Site site : SITES) {
                if (!matchesHost(host, site.hosts) || !site.path.matcher(path).matches()) {
                    continue;
                }
                final String query = uri.getEncodedQuery();
                final String tail = path + (TextUtils.isEmpty(query) ? "" : "?" + query);
                final ArrayList<String> out = new ArrayList<>(site.fixerHosts.length);
                for (String fixer : site.fixerHosts) {
                    out.add("https://" + fixer + tail);
                }
                return out;
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return Collections.emptyList();
    }

    private static boolean matchesHost(String host, String[] hosts) {
        for (String h : hosts) {
            if (host.equals(h)) {
                return true;
            }
        }
        return false;
    }

    /** A preview worth showing instead of the original: an actual card, not an empty shell. */
    public static boolean isUsable(TLRPC.WebPage webPage) {
        if (!(webPage instanceof TLRPC.TL_webPage)) {
            return false;
        }
        final TLRPC.TL_webPage page = (TLRPC.TL_webPage) webPage;
        return page.photo != null || page.document != null || !TextUtils.isEmpty(page.embed_url)
                || !TextUtils.isEmpty(page.description);
    }
}
