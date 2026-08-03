package org.telegram.ui.Components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLObject;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

import java.io.ByteArrayOutputStream;
import java.io.File;

/**
 * PrimeGram: reverse image search - long-press a photo, pick a provider, and it uploads the
 * image inside a WebView the same way a person dragging a file onto the provider's own search
 * page would. Ported from exteraGram's {@code ReverseImageSearchSheet}; the ad-block cosmetic-hide
 * wiring (their own {@code AdBlockClient}) was dropped since it's not something this fork carries
 * and isn't needed for the search itself to work.
 */
public class PrimeReverseImageSearchSheet extends BottomSheet {

    public enum Provider {
        YANDEX("Yandex", "https://yandex.com/images/"),
        GOOGLE("Google", "https://www.google.com/"),
        BING("Bing", "https://www.bing.com/images"),
        TINEYE("TinEye", "https://tineye.com/");

        public final String title;
        public final String landingUrl;

        Provider(String title, String landingUrl) {
            this.title = title;
            this.landingUrl = landingUrl;
        }
    }

    private WebView webView;
    private final ProgressBar spinner;
    private final Provider provider;
    private volatile String currentUrl;
    private String pendingScript;
    private boolean uploadInjected;
    private int pageStartCount;
    private int injectedAtStartCount = -1;
    private boolean revealed;
    private Runnable revealTimeout;

    @SuppressLint("SetJavaScriptEnabled")
    public PrimeReverseImageSearchSheet(Context context, final File file, final Provider provider, Theme.ResourcesProvider resourcesProvider) {
        super(context, false, resourcesProvider);
        this.provider = provider;
        setApplyTopPadding(false);
        setApplyBottomPadding(false);
        useBackgroundTopPadding = false;
        setCanDismissWithSwipe(false);

        int actionBarHeight = ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight;

        FrameLayout frameLayout = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(View.MeasureSpec.getSize(heightMeasureSpec), TLObject.FLAG_30));
            }
        };
        frameLayout.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));

        webView = new WebView(context);
        webView.setVisibility(View.INVISIBLE);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setVerticalScrollBarEnabled(false);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        webView.getSettings().setCacheMode(android.webkit.WebSettings.LOAD_DEFAULT);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        File dbDir = new File(ApplicationLoader.getFilesDirFixed(), "webview_database");
        if ((dbDir.exists() && dbDir.isDirectory()) || dbDir.mkdirs()) {
            webView.getSettings().setDatabasePath(dbDir.getAbsolutePath());
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                super.doUpdateVisitedHistory(view, url, isReload);
                onUrlChanged(url);
                if (provider == Provider.TINEYE && uploadInjected && url != null) {
                    String path;
                    try {
                        path = Uri.parse(url).getPath();
                    } catch (Exception e) {
                        path = null;
                    }
                    if (path != null && path.startsWith("/search")) {
                        reveal();
                    }
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                onUrlChanged(url);
                if (uploadInjected) {
                    if (pageStartCount > injectedAtStartCount) {
                        reveal();
                    }
                } else if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                    uploadInjected = true;
                    injectedAtStartCount = pageStartCount;
                    if (pendingScript != null) {
                        view.evaluateJavascript(pendingScript, null);
                        pendingScript = null;
                    }
                }
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                pageStartCount++;
                onUrlChanged(url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request != null && request.isForMainFrame()) {
                    Uri url = request.getUrl();
                    String host = url != null ? url.getHost() : null;
                    if (host != null && !isProviderHost(provider, host)) {
                        Browser.openUrlInSystemBrowser(getContext(), url.toString());
                        return true;
                    }
                }
                return false;
            }
        });

        float density = AndroidUtilities.density;
        frameLayout.addView(webView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, android.view.Gravity.LEFT | android.view.Gravity.TOP, 0, actionBarHeight / density, 0, 0));

        View divider = new View(context);
        divider.setBackgroundColor(getThemedColor(Theme.key_divider));
        frameLayout.addView(divider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1f / density, android.view.Gravity.LEFT | android.view.Gravity.TOP, 0, actionBarHeight / density, 0, 0));

        ActionBar actionBar = new ActionBar(context, resourcesProvider);
        actionBar.setOccupyStatusBar(true);
        actionBar.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        actionBar.setTitleColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setItemsColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), false);
        actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_actionBarWhiteSelector), false);
        actionBar.setBackButtonImage(R.drawable.ic_close_white);
        actionBar.setTitle(provider.title);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    dismiss();
                } else if (id == 1 && !TextUtils.isEmpty(currentUrl)) {
                    Browser.openUrlInSystemBrowser(getContext(), currentUrl);
                }
            }
        });
        if (provider == Provider.YANDEX) {
            actionBar.createMenu().addItem(1, R.drawable.msg_openin);
        }
        frameLayout.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, actionBarHeight / density));

        spinner = new ProgressBar(context);
        spinner.setIndeterminate(true);
        frameLayout.addView(spinner, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, android.view.Gravity.CENTER));

        setCustomView(frameLayout);

        Utilities.globalQueue.postRunnable(() -> {
            final String encoded = encodeImage(file);
            AndroidUtilities.runOnUIThread(() -> startUpload(encoded, provider));
        });
    }

    private void startUpload(String base64Image, Provider provider) {
        if (webView == null) {
            return;
        }
        if (base64Image == null) {
            dismiss();
            return;
        }
        seedConsentCookies(provider);
        pendingScript = buildUploadScript(provider, base64Image);
        webView.loadUrl(provider.landingUrl);
        revealTimeout = this::reveal;
        AndroidUtilities.runOnUIThread(revealTimeout, 30000);
    }

    private static String buildUploadScript(Provider provider, String base64) {
        switch (provider) {
            case GOOGLE:
                return "(function(){try{" + bytesFromBase64(base64) + "var file=new File([a],'image.jpg',{type:'image/jpeg'});var f=document.createElement('form');f.method='POST';f.enctype='multipart/form-data';f.action='https://www.google.com/searchbyimage/upload';var i=document.createElement('input');i.type='file';i.name='encoded_image';f.appendChild(i);document.body.appendChild(f);var dt=new DataTransfer();dt.items.add(file);i.files=dt.files;f.submit();}catch(e){}})();";
            case BING:
                return "(function(){try{var f=document.createElement('form');f.method='POST';f.enctype='multipart/form-data';f.action='https://www.bing.com/images/search?view=detailv2&iss=sbiupload&FORM=SBIHMP&sbifnm=image.jpg';var i=document.createElement('input');i.type='hidden';i.name='imageBin';i.value='" + base64 + "';f.appendChild(i);document.body.appendChild(f);f.submit();}catch(e){}})();";
            case YANDEX:
                return "(function(){try{" + bytesFromBase64(base64) + "var o=location.protocol+'//'+location.host;var blob=new Blob([a],{type:'image/jpeg'});var d=new FormData();d.append('upfile',blob,'image.jpg');var u=o+'/images/touch/search?rpt=imageview&format=json&request='+encodeURIComponent('{\"blocks\":[{\"block\":\"cbir-uploader__get-cbir-id\"}]}');fetch(u,{method:'POST',credentials:'include',headers:{'X-Requested-With':'XMLHttpRequest','Accept':'application/json, text/javascript, */*; q=0.01'},body:d}).then(function(r){return r.json();}).then(function(j){var p=j.blocks[0].params;if(p&&p.cbirId){location.href=o+'/images/search?cbir_id='+encodeURIComponent(p.cbirId)+'&rpt=imageview&tabInt=1&url='+encodeURIComponent(p.originalImageUrl||'');}}).catch(function(e){});}catch(e){}})();";
            default:
                return "(function(){try{" + bytesFromBase64(base64) + "var file=new File([a],'image.jpg',{type:'image/jpeg'});var n=0;var t=setInterval(function(){var i=document.querySelector('input#upload-box');if(i){clearInterval(t);try{var dt=new DataTransfer();dt.items.add(file);i.files=dt.files;i.dispatchEvent(new Event('change',{bubbles:true}));}catch(e){}}else if(++n>24){clearInterval(t);}},250);}catch(e){}})();";
        }
    }

    private static String bytesFromBase64(String base64) {
        return "var b='" + base64 + "';var bin=atob(b);var a=new Uint8Array(bin.length);for(var k=0;k<bin.length;k++)a[k]=bin.charCodeAt(k);";
    }

    /** Downscaled to fit within 1280px on the long side - plenty for a similarity search, and
     *  keeps the base64 payload small enough that evaluateJavascript doesn't choke on it. */
    private static String encodeImage(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / sample > 2560) {
                sample *= 2;
            }
            opts.inSampleSize = sample;
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            if (bitmap == null) {
                return null;
            }
            int maxSide = Math.max(bitmap.getWidth(), bitmap.getHeight());
            if (maxSide > 1280) {
                float scale = 1280f / maxSide;
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, Math.round(bitmap.getWidth() * scale), Math.round(bitmap.getHeight() * scale), true);
                if (scaled != bitmap) {
                    bitmap.recycle();
                    bitmap = scaled;
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            bitmap.recycle();
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    private static boolean isProviderHost(Provider provider, String host) {
        String lower = host.toLowerCase();
        switch (provider) {
            case GOOGLE:
                return lower.contains("google.") || lower.endsWith("gstatic.com") || lower.endsWith("googleusercontent.com");
            case BING:
                return lower.endsWith("bing.com") || lower.endsWith("bingapis.com") || lower.endsWith("live.com") || lower.endsWith("microsoft.com");
            case YANDEX:
                return lower.contains("yandex.") || lower.endsWith("ya.ru") || lower.contains("yastatic.");
            default:
                return lower.endsWith("tineye.com");
        }
    }

    private static void seedConsentCookies(Provider provider) {
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        switch (provider) {
            case BING:
                cookieManager.setCookie("https://www.bing.com", "BCP=AD=1&AL=1&SM=1; Domain=.bing.com; Path=/; Secure");
                break;
            case TINEYE:
                cookieManager.setCookie("https://tineye.com", "cookie_consent=accepted; Path=/");
                break;
            case YANDEX:
                cookieManager.setCookie("https://yandex.com", "gdpr=0; Domain=.yandex.com; Path=/; Secure");
                cookieManager.setCookie("https://yandex.ru", "gdpr=0; Domain=.yandex.ru; Path=/; Secure");
                break;
        }
        cookieManager.flush();
    }

    private void onUrlChanged(String url) {
        currentUrl = url;
    }

    private void reveal() {
        if (revealed || webView == null) {
            return;
        }
        revealed = true;
        if (revealTimeout != null) {
            AndroidUtilities.cancelRunOnUIThread(revealTimeout);
            revealTimeout = null;
        }
        webView.setAlpha(0f);
        webView.setVisibility(View.VISIBLE);
        webView.animate().alpha(1f).setDuration(150).start();
        spinner.animate().alpha(0f).setDuration(150).withEndAction(() -> spinner.setVisibility(View.GONE)).start();
    }

    @Override
    public void dismiss() {
        if (revealTimeout != null) {
            AndroidUtilities.cancelRunOnUIThread(revealTimeout);
            revealTimeout = null;
        }
        if (webView != null) {
            try {
                webView.stopLoading();
                webView.loadUrl("about:blank");
                webView.destroy();
            } catch (Exception e) {
                FileLog.e(e);
            }
            webView = null;
        }
        super.dismiss();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
