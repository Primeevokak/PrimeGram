package org.telegram.ui.Components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.PrimeBrowserActivity;

/**
 * PrimeGram: renders the page behind a link in a small window, right where the user long-pressed.
 *
 * <p>Configured the same way as the in-app browser's web view — the first version diverged
 * from it and broke in two ways worth recording. It blocked every navigation in
 * {@code shouldOverrideUrlLoading}, which also blocks redirects, so any site that redirects
 * (most of them) never rendered. And it painted its background from the theme, which on a
 * dark theme is a black rectangle indistinguishable from a failure.
 *
 * <p>The page is deliberately not interactive: a transparent layer above it turns any touch
 * into "open this properly" and hands the URL to the in-app browser.
 */
public class PrimeLinkPreviewSheet extends BottomSheet {

    private static final int PREVIEW_HEIGHT_DP = 320;
    /** Give up and show the error page after this long — some hosts never answer at all. */
    private static final long LOAD_TIMEOUT_MS = 15_000L;

    private final String url;
    private final BaseFragment fragment;
    private WebView webView;
    private TextView statusView;
    private boolean loaded;
    private boolean failed;
    private Runnable timeoutRunnable;

    @SuppressLint("SetJavaScriptEnabled")
    public PrimeLinkPreviewSheet(Context context, BaseFragment fragment, String url, Theme.ResourcesProvider resourcesProvider) {
        super(context, false, resourcesProvider);
        this.url = url;
        this.fragment = fragment;

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));

        TextView urlView = new TextView(context);
        urlView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        urlView.setTextColor(Theme.getColor(Theme.key_dialogTextGray2, resourcesProvider));
        urlView.setText(prettyUrl(url));
        urlView.setMaxLines(2);
        urlView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        urlView.setPadding(AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6), AndroidUtilities.dp(10));
        container.addView(urlView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        FrameLayout previewFrame = new FrameLayout(context);
        // Web pages assume they are drawn on white; theming this surface produced the black
        // rectangle users reported on dark themes.
        previewFrame.setBackgroundColor(Color.WHITE);
        previewFrame.setClipToOutline(true);
        previewFrame.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), AndroidUtilities.dp(10));
            }
        });

        try {
            webView = new WebView(context);
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
            settings.setSupportZoom(false);
            settings.setBuiltInZoomControls(false);
            settings.setDisplayZoomControls(false);
            settings.setJavaScriptCanOpenWindowsAutomatically(false);
            settings.setSupportMultipleWindows(false);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(true);
            settings.setGeolocationEnabled(false);
            settings.setMediaPlaybackRequiresUserGesture(true);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
            try {
                CookieManager cookieManager = CookieManager.getInstance();
                cookieManager.setAcceptCookie(true);
                cookieManager.setAcceptThirdPartyCookies(webView, true);
            } catch (Throwable ignore) {}

            webView.setBackgroundColor(Color.WHITE);
            webView.setVerticalScrollBarEnabled(false);
            webView.setHorizontalScrollBarEnabled(false);

            webView.setWebChromeClient(new android.webkit.WebChromeClient() {
                @Override
                public void onProgressChanged(WebView view, int newProgress) {
                    // Heavy pages keep loading trackers long after they are readable.
                    if (newProgress >= 60) {
                        markLoaded();
                    }
                }
            });
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageCommitVisible(WebView view, String url) {
                    markLoaded();
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    markLoaded();
                }

                @Override
                public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
                    // Subresource failures are normal on any real page; only the document counts.
                    if (request != null && request.isForMainFrame()) {
                        fail("Сайт не отвечает или недоступен");
                    }
                }

                @Override
                public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                    if (failingUrl != null && failingUrl.equals(PrimeLinkPreviewSheet.this.url)) {
                        fail("Сайт не отвечает или недоступен");
                    }
                }

                // Navigation is deliberately NOT blocked here: redirects go through this
                // callback too, and refusing them leaves the view permanently blank.
            });
            previewFrame.addView(webView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            webView.loadUrl(url);

            timeoutRunnable = () -> {
                if (!loaded && !failed) {
                    try {
                        webView.stopLoading();
                    } catch (Throwable ignore) {}
                    fail("Превышено время ожидания");
                }
            };
            AndroidUtilities.runOnUIThread(timeoutRunnable, LOAD_TIMEOUT_MS);
        } catch (Throwable t) {
            FileLog.e("PrimeLinkPreviewSheet.webview", t);
            webView = null;
        }

        statusView = new TextView(context);
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        statusView.setTextColor(Color.parseColor("#8A8A8E"));
        statusView.setGravity(Gravity.CENTER);
        statusView.setText(webView == null ? "Просмотр недоступен на этом устройстве." : "Загружаю страницу…");
        previewFrame.addView(statusView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // Swallows every touch on the preview and turns it into "open properly".
        View tapCatcher = new View(context) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                return true;
            }
        };
        tapCatcher.setOnClickListener(v -> openInBrowser());
        previewFrame.addView(tapCatcher, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        container.addView(previewFrame, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, PREVIEW_HEIGHT_DP));

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        addButton(context, buttons, "Открыть", resourcesProvider, true, this::openInBrowser);
        addButton(context, buttons, LocaleController.getString(R.string.Copy), resourcesProvider, false, () -> {
            AndroidUtilities.addToClipboard(url);
            BulletinFactory.of(fragment).createCopyBulletin(LocaleController.getString(R.string.LinkCopied)).show();
            dismiss();
        });
        container.addView(buttons, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 0, 12, 0, 0));

        setCustomView(container);
    }

    private void markLoaded() {
        if (loaded || failed) {
            return;
        }
        loaded = true;
        if (statusView != null) {
            statusView.setVisibility(View.GONE);
        }
    }

    private void fail(String reason) {
        if (failed || loaded) {
            return;
        }
        failed = true;
        if (statusView != null) {
            statusView.setVisibility(View.GONE);
        }
        PrimeWebErrorPage.show(webView, url, reason);
    }

    private void addButton(Context context, LinearLayout parent, String text, Theme.ResourcesProvider resourcesProvider, boolean filled, Runnable onClick) {
        TextView button = new TextView(context);
        button.setText(text);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        button.setTypeface(AndroidUtilities.bold());
        if (filled) {
            button.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider));
            button.setBackground(Theme.AdaptiveRipple.filledRect(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), 6));
        } else {
            button.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
            button.setBackground(Theme.AdaptiveRipple.filledRect(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider), 6));
        }
        button.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams params = LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f);
        params.leftMargin = parent.getChildCount() > 0 ? AndroidUtilities.dp(8) : 0;
        parent.addView(button, params);
    }

    private void openInBrowser() {
        dismiss();
        try {
            if (fragment != null) {
                fragment.presentFragment(new PrimeBrowserActivity(url));
            }
        } catch (Throwable t) {
            FileLog.e("PrimeLinkPreviewSheet.open", t);
        }
    }

    private String prettyUrl(String url) {
        if (url == null) {
            return "";
        }
        if (url.startsWith("https://")) {
            return url.substring(8);
        }
        if (url.startsWith("http://")) {
            return url.substring(7);
        }
        return url;
    }

    @Override
    public void dismissInternal() {
        if (timeoutRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(timeoutRunnable);
            timeoutRunnable = null;
        }
        destroyWebView();
        super.dismissInternal();
    }

    private void destroyWebView() {
        if (webView == null) {
            return;
        }
        try {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            if (webView.getParent() instanceof android.view.ViewGroup) {
                ((android.view.ViewGroup) webView.getParent()).removeView(webView);
            }
            webView.destroy();
        } catch (Throwable ignore) {
        } finally {
            webView = null;
        }
    }
}
