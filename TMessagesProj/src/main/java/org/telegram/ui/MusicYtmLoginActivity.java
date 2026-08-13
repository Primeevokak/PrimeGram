package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * A real Google sign-in inside a WebView, for the one thing YouTube Music's private API needs
 * that none of this feature's other platforms do: a session cookie. Mirrors reSwaga's own
 * {@code create_auth_alert} flow (disassembled from its compiled bytecode, since it ships no
 * source) - open the actual login page, poll {@link CookieManager} for the {@code SAPISID}/
 * {@code __Secure-3PAPISID} cookie once signed in, hand the whole cookie string back and close.
 *
 * <p>Nothing but that cookie string is ever stored by this screen - no password, no verification
 * code, no account credential of any kind passes through app code; the login itself happens
 * entirely inside Google's own page, the same as opening it in a browser.
 */
public class MusicYtmLoginActivity extends BaseFragment {

    public interface Callback {
        void onCookieCaptured(String cookie);
    }

    private static final String LOGIN_URL =
            "https://accounts.google.com/ServiceLogin?service=youtube&continue=https://music.youtube.com/";
    private static final String TARGET_URL = "https://music.youtube.com";
    private static final long POLL_INTERVAL_MS = 1000;

    private final Callback callback;
    private WebView webView;
    private boolean captured;

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (captured || webView == null) {
                return;
            }
            pollCookie();
            if (!captured) {
                AndroidUtilities.runOnUIThread(this, POLL_INTERVAL_MS);
            }
        }
    };

    public MusicYtmLoginActivity(Callback callback) {
        super();
        this.callback = callback;
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Вход в YouTube Music");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        final FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        fragmentView = frameLayout;

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().removeAllCookies(null);

        webView = new WebView(context);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setUserAgentString(
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl(LOGIN_URL);
        frameLayout.addView(webView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        AndroidUtilities.runOnUIThread(pollRunnable, POLL_INTERVAL_MS);
        return fragmentView;
    }

    private void pollCookie() {
        final String cookie = CookieManager.getInstance().getCookie(TARGET_URL);
        if (cookie == null) {
            return;
        }
        if (cookie.contains("SAPISID=") || cookie.contains("__Secure-3PAPISID=")) {
            captured = true;
            AndroidUtilities.cancelRunOnUIThread(pollRunnable);
            if (callback != null) {
                callback.onCookieCaptured(cookie);
            }
            if (getParentActivity() != null) {
                Toast.makeText(getParentActivity(), "Вход выполнен", Toast.LENGTH_SHORT).show();
            }
            finishFragment();
        }
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        AndroidUtilities.cancelRunOnUIThread(pollRunnable);
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.destroy();
            webView = null;
        }
    }
}
