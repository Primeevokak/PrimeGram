package org.telegram.ui.Components;

import android.graphics.Color;
import android.webkit.WebView;

import org.telegram.ui.ActionBar.Theme;

/**
 * PrimeGram: the page shown when a web view fails to load something.
 *
 * <p>Replaces the system's built-in "webpage not available" screen, which is unstyled, in the
 * device language rather than the app's, and shows the raw URL and error code. Rendered as a
 * data URL so it needs no assets and inherits the current theme's colours.
 */
public class PrimeWebErrorPage {

    /** Loads the error page into a web view, replacing whatever it was showing. */
    public static void show(WebView webView, String url, String reason) {
        if (webView == null) {
            return;
        }
        try {
            webView.loadDataWithBaseURL(null, build(url, reason), "text/html; charset=utf-8", "utf-8", null);
        } catch (Throwable ignore) {}
    }

    public static String build(String url, String reason) {
        final String background = hex(Theme.getColor(Theme.key_windowBackgroundWhite));
        final String text = hex(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        final String subtext = hex(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
        final String accent = hex(Theme.getColor(Theme.key_featuredStickers_addButton));

        return "<!doctype html><html><head>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1'>"
                + "<style>"
                + "html,body{margin:0;height:100%;background:" + background + ";}"
                + "body{display:flex;align-items:center;justify-content:center;"
                + "font-family:-apple-system,Roboto,sans-serif;padding:24px;box-sizing:border-box;}"
                + ".wrap{text-align:center;max-width:420px;}"
                + ".mark{width:64px;height:64px;margin:0 auto 20px;border-radius:50%;"
                + "background:" + accent + "1f;display:flex;align-items:center;justify-content:center;"
                + "font-size:30px;color:" + accent + ";}"
                + "h1{font-size:19px;font-weight:600;color:" + text + ";margin:0 0 10px;}"
                + "p{font-size:14px;line-height:1.5;color:" + subtext + ";margin:0 0 6px;word-break:break-all;}"
                + "</style></head><body><div class='wrap'>"
                + "<div class='mark'>!</div>"
                + "<h1>Страница не открылась</h1>"
                + "<p>" + escape(reason) + "</p>"
                + (url == null ? "" : "<p>" + escape(url) + "</p>")
                + "</div></body></html>";
    }

    private static String hex(int color) {
        return String.format("#%06X", 0xFFFFFF & color);
    }

    /** The URL and the reason both come from outside; neither may inject markup. */
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
