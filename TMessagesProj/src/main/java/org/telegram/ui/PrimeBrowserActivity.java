package org.telegram.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public class PrimeBrowserActivity extends BaseFragment {

    private static final String MOBILE_USER_AGENT = null; // null = keep the WebView default
    private static final String DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private class BrowserTab {
        WebView webView;
        String currentUrl = "";
        String title = "Главная";
        /** URL actually handed to the WebView, so switching tabs doesn't needlessly reload. */
        String loadedUrl = "";
        boolean desktopMode;

        void init(Context context) {
            if (webView != null) return;
            webView = new WebView(context);
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
            settings.setSupportZoom(true);
            settings.setBuiltInZoomControls(true);
            settings.setDisplayZoomControls(false);
            // Without these, many sites silently fail to open popups/logins or render
            // at a broken zoom level — the previous build shipped none of them.
            settings.setJavaScriptCanOpenWindowsAutomatically(true);
            settings.setSupportMultipleWindows(false);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(true);
            settings.setGeolocationEnabled(false);
            settings.setMediaPlaybackRequiresUserGesture(true);
            settings.setTextZoom(100);

            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptCookie(true);
            cookieManager.setAcceptThirdPartyCookies(webView, true);

            applyUserAgent();

            webView.setWebViewClient(new PrimeWebViewClient(this));
            webView.setWebChromeClient(new PrimeWebChromeClient(this));
            webView.setDownloadListener(new PrimeDownloadListener());
            webView.setOnLongClickListener(v -> showLinkContextMenu(webView, v));
        }

        void applyUserAgent() {
            if (webView == null) return;
            webView.getSettings().setUserAgentString(desktopMode ? DESKTOP_USER_AGENT : MOBILE_USER_AGENT);
        }

        /** Creates the WebView if needed and actually loads the URL. */
        void load(Context context, String url) {
            init(context);
            currentUrl = url;
            loadedUrl = url;
            webView.loadUrl(url);
        }

        void destroy() {
            if (webView != null) {
                ViewGroup parent = (ViewGroup) webView.getParent();
                if (parent != null) parent.removeView(webView);
                webView.stopLoading();
                webView.loadUrl("about:blank");
                webView.destroy();
                webView = null;
                loadedUrl = "";
            }
        }
    }

    private ArrayList<BrowserTab> tabs = new ArrayList<>();
    private int currentTabIndex = 0;

    private ProgressBar progressBar;
    private EditText addressEditText;
    private ImageView backButton;
    private ImageView forwardButton;
    private ImageView refreshButton;
    private ImageView lockIcon;
    private ImageView clearIcon;
    private TextView tabsCountText;

    private FrameLayout middleContainer;
    private LinearLayout homeContainer;
    private LinearLayout enginesLayout;

    private FrameLayout tabSwitcherContainer;
    private LinearLayout tabSwitcherList;
    private boolean isTabSwitcherOpen = false;

    private static final int REQUEST_CODE_FILE_CHOOSER = 4001;
    private static final int REQUEST_CODE_WEB_PERMISSION = 4002;

    /** Pending <input type="file"> callback while the system picker is open. */
    private ValueCallback<Uri[]> filePathCallback;
    /** Pending getUserMedia() request while the runtime permission dialog is open. */
    private PermissionRequest pendingPermissionRequest;
    private String[] pendingPermissionResources;

    public PrimeBrowserActivity(String url) {
        super();
        BrowserTab tab = new BrowserTab();
        tab.currentUrl = url == null ? "" : url;
        tabs.add(tab);
    }

    @Override
    public boolean onFragmentCreate() {
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
        for (BrowserTab tab : tabs) {
            tab.destroy();
        }
        tabs.clear();
    }

    @Override
    public void onPause() {
        super.onPause();
        // Otherwise page audio/video keeps playing after you leave the browser.
        BrowserTab tab = getCurrentTab();
        if (tab != null && tab.webView != null) {
            tab.webView.onPause();
            tab.webView.pauseTimers();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        BrowserTab tab = getCurrentTab();
        if (tab != null && tab.webView != null) {
            tab.webView.onResume();
            tab.webView.resumeTimers();
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public View createView(Context context) {
        actionBar.setAddToContainer(false);

        fragmentView = new LinearLayout(context);
        LinearLayout mainLayout = (LinearLayout) fragmentView;
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        // 1. Top Bar
        LinearLayout topBar = new LinearLayout(context);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefault));
        topBar.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(8), AndroidUtilities.dp(4));
        topBar.setPadding(topBar.getPaddingLeft(), topBar.getPaddingTop() + AndroidUtilities.statusBarHeight, topBar.getPaddingRight(), topBar.getPaddingBottom());

        ImageView closeButton = new ImageView(context);
        closeButton.setImageResource(R.drawable.ic_ab_back);
        closeButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultIcon), PorterDuff.Mode.MULTIPLY));
        closeButton.setScaleType(ImageView.ScaleType.CENTER);
        closeButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_actionBarDefaultSelector), 1));
        closeButton.setOnClickListener(v -> finishFragment());
        topBar.addView(closeButton, LayoutHelper.createLinear(48, 48));

        LinearLayout addressContainer = new LinearLayout(context);
        addressContainer.setOrientation(LinearLayout.HORIZONTAL);
        addressContainer.setGravity(Gravity.CENTER_VERTICAL);
        addressContainer.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(18), Theme.getColor(Theme.key_dialogBackground)));
        addressContainer.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(8), 0);

        lockIcon = new ImageView(context);
        lockIcon.setImageResource(R.drawable.ic_lock_white);
        lockIcon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
        addressContainer.addView(lockIcon, LayoutHelper.createLinear(16, 16, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));

        addressEditText = new EditText(context);
        addressEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        addressEditText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        addressEditText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        addressEditText.setHint(LocaleController.getString(R.string.Search));
        addressEditText.setSingleLine(true);
        addressEditText.setBackground(null);
        addressEditText.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressEditText.setInputType(EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_URI);
        addressContainer.addView(addressEditText, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f));

        clearIcon = new ImageView(context);
        clearIcon.setImageResource(R.drawable.ic_close_white);
        clearIcon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
        clearIcon.setVisibility(View.GONE);
        clearIcon.setOnClickListener(v -> addressEditText.setText(""));
        addressContainer.addView(clearIcon, LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL));

        topBar.addView(addressContainer, LayoutHelper.createLinear(0, 36, 1.0f, 4, 0, 8, 0));

        ImageView menuButton = new ImageView(context);
        menuButton.setImageResource(R.drawable.ic_ab_other);
        menuButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultIcon), PorterDuff.Mode.MULTIPLY));
        menuButton.setScaleType(ImageView.ScaleType.CENTER);
        menuButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_actionBarDefaultSelector), 1));
        menuButton.setOnClickListener(this::showBrowserMenu);
        topBar.addView(menuButton, LayoutHelper.createLinear(48, 48));

        mainLayout.addView(topBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // 2. Progress Bar
        progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgressDrawable(Theme.createRoundRectDrawable(AndroidUtilities.dp(2), Theme.getColor(Theme.key_featuredStickers_addButton)));
        mainLayout.addView(progressBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 2));

        // 3. Middle Container (WebView + Home + TabSwitcher)
        FrameLayout contentContainer = new FrameLayout(context);
        
        middleContainer = new FrameLayout(context);
        homeContainer = new LinearLayout(context);
        homeContainer.setOrientation(LinearLayout.VERTICAL);
        homeContainer.setGravity(Gravity.CENTER);
        homeContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        
        TextView title = new TextView(context);
        title.setText("PrimeBrowser");
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 28);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        homeContainer.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 32));

        TextView engineLabel = new TextView(context);
        engineLabel.setText("Поисковая система:");
        engineLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        engineLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        homeContainer.addView(engineLabel, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        enginesLayout = new LinearLayout(context);
        enginesLayout.setOrientation(LinearLayout.VERTICAL);
        enginesLayout.setGravity(Gravity.CENTER);
        homeContainer.addView(enginesLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        updateHomeEngines(context);

        middleContainer.addView(homeContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        contentContainer.addView(middleContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // Tab Switcher UI
        tabSwitcherContainer = new FrameLayout(context);
        tabSwitcherContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        tabSwitcherContainer.setVisibility(View.GONE);
        
        ScrollView tabScroll = new ScrollView(context);
        tabSwitcherList = new LinearLayout(context);
        tabSwitcherList.setOrientation(LinearLayout.VERTICAL);
        tabSwitcherList.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(100));
        tabScroll.addView(tabSwitcherList, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        tabSwitcherContainer.addView(tabScroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        TextView addTabBtn = new TextView(context);
        addTabBtn.setText("+ Новая вкладка");
        addTabBtn.setGravity(Gravity.CENTER);
        addTabBtn.setTextColor(0xffffffff);
        addTabBtn.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(8), Theme.getColor(Theme.key_featuredStickers_addButton)));
        addTabBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        addTabBtn.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        addTabBtn.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));
        addTabBtn.setOnClickListener(v -> addNewTab(context));
        
        FrameLayout addTabFrame = new FrameLayout(context);
        addTabFrame.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
        addTabFrame.addView(addTabBtn, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));
        tabSwitcherContainer.addView(addTabFrame, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        contentContainer.addView(tabSwitcherContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        mainLayout.addView(contentContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1.0f));

        // 4. Bottom Navigation Bar
        LinearLayout bottomBar = new LinearLayout(context);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        bottomBar.setElevation(AndroidUtilities.dp(4));

        backButton = createBottomButton(context, R.drawable.ic_ab_back);
        backButton.setOnClickListener(v -> {
            BrowserTab tab = getCurrentTab();
            if (tab != null && tab.webView != null && tab.webView.canGoBack() && tab.webView.getVisibility() == View.VISIBLE) {
                tab.webView.goBack();
            }
        });
        
        forwardButton = createBottomButton(context, R.drawable.ic_ab_back);
        forwardButton.setRotation(180);
        forwardButton.setOnClickListener(v -> {
            BrowserTab tab = getCurrentTab();
            if (tab != null && tab.webView != null && tab.webView.canGoForward() && tab.webView.getVisibility() == View.VISIBLE) {
                tab.webView.goForward();
            }
        });

        FrameLayout tabsCountLayout = new FrameLayout(context);
        tabsCountLayout.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 0));
        tabsCountText = new TextView(context);
        tabsCountText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        tabsCountText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon));
        tabsCountText.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        tabsCountText.setGravity(Gravity.CENTER);
        tabsCountText.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(6), 0x20888888));
        tabsCountLayout.addView(tabsCountText, LayoutHelper.createFrame(24, 24, Gravity.CENTER));
        tabsCountLayout.setOnClickListener(v -> toggleTabSwitcher(context));

        // refreshButton was declared and referenced by updateNavButtons(), but never actually
        // created or added to the bar — the browser simply had no way to reload a page.
        refreshButton = createBottomButton(context, R.drawable.msg_reset);
        refreshButton.setOnClickListener(v -> reloadCurrentPage());

        ImageView homeButton = createBottomButton(context, R.drawable.msg_home);
        homeButton.setOnClickListener(v -> loadUrl(""));

        bottomBar.addView(backButton, LayoutHelper.createLinear(0, 48, 1.0f));
        bottomBar.addView(forwardButton, LayoutHelper.createLinear(0, 48, 1.0f));
        bottomBar.addView(refreshButton, LayoutHelper.createLinear(0, 48, 1.0f));
        bottomBar.addView(tabsCountLayout, LayoutHelper.createLinear(0, 48, 1.0f));
        bottomBar.addView(homeButton, LayoutHelper.createLinear(0, 48, 1.0f));

        mainLayout.addView(bottomBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

        // Setup Actions
        addressEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                loadUrlFromInput(context);
                AndroidUtilities.hideKeyboard(addressEditText);
                return true;
            }
            return false;
        });

        // Tapping the address bar should let you type a new query straight away instead of
        // making you clear a long URL by hand first.
        addressEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                addressEditText.post(addressEditText::selectAll);
            } else {
                BrowserTab tab = getCurrentTab();
                if (tab != null && !TextUtils.isEmpty(tab.currentUrl)) {
                    addressEditText.setText(tab.currentUrl);
                }
            }
        });

        addressEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                clearIcon.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }
        });

        switchTab(context, 0);

        return fragmentView;
    }

    private void updateHomeEngines(Context context) {
        enginesLayout.removeAllViews();
        String[] engines = {"Google", "Perplexity", "DuckDuckGo", "Yandex"};
        String selected = MessagesController.getGlobalMainSettings().getString("primegram_search_engine", "Google");

        for (String engine : engines) {
            TextView btn = new TextView(context);
            btn.setText(engine);
            btn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            if (engine.equals(selected)) {
                btn.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
                btn.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            } else {
                btn.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            }
            btn.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(8));
            btn.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2));
            btn.setOnClickListener(v -> {
                MessagesController.getGlobalMainSettings().edit().putString("primegram_search_engine", engine).apply();
                updateHomeEngines(context);
                addressEditText.requestFocus();
                AndroidUtilities.showKeyboard(addressEditText);
            });
            enginesLayout.addView(btn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
        }
    }

    private ImageView createBottomButton(Context context, int iconRes) {
        ImageView btn = new ImageView(context);
        btn.setImageResource(iconRes);
        btn.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
        btn.setScaleType(ImageView.ScaleType.CENTER);
        btn.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 0));
        return btn;
    }

    private void addNewTab(Context context) {
        BrowserTab newTab = new BrowserTab();
        tabs.add(newTab);
        switchTab(context, tabs.size() - 1);
        if (isTabSwitcherOpen) toggleTabSwitcher(context);
    }

    private void switchTab(Context context, int index) {
        if (index < 0 || index >= tabs.size()) return;

        // currentTabIndex can be stale right after a tab was removed.
        if (currentTabIndex >= 0 && currentTabIndex < tabs.size()) {
            BrowserTab oldTab = tabs.get(currentTabIndex);
            if (oldTab.webView != null) {
                oldTab.webView.setVisibility(View.GONE);
            }
        }
        
        currentTabIndex = index;
        BrowserTab currentTab = tabs.get(currentTabIndex);
        
        if (!TextUtils.isEmpty(currentTab.currentUrl)) {
            currentTab.init(context);
            attachWebView(currentTab);
            // A tab can carry a URL that was never handed to a WebView (opened from a link,
            // or restored after its WebView was destroyed) — load it now instead of showing
            // a blank page with a filled-in address bar.
            if (!currentTab.currentUrl.equals(currentTab.loadedUrl)) {
                currentTab.load(context, currentTab.currentUrl);
            }
            currentTab.webView.setVisibility(View.VISIBLE);
            homeContainer.setVisibility(View.GONE);
            addressEditText.setText(currentTab.currentUrl);
            lockIcon.setImageResource(currentTab.currentUrl.startsWith("https://") ? R.drawable.ic_lock_white : R.drawable.msg_search);
        } else {
            homeContainer.setVisibility(View.VISIBLE);
            addressEditText.setText("");
            lockIcon.setImageResource(R.drawable.msg_search);
        }
        
        addressEditText.clearFocus();
        AndroidUtilities.hideKeyboard(addressEditText);
        updateNavButtons();
    }

    private void closeTab(Context context, int index) {
        if (tabs.size() <= 1) {
            tabs.get(0).destroy();
            tabs.get(0).currentUrl = "";
            tabs.get(0).title = "Главная";
            switchTab(context, 0);
            toggleTabSwitcher(context);
            return;
        }
        BrowserTab tab = tabs.remove(index);
        tab.destroy();

        // Keep pointing at the same tab the user was on: removing an entry before it
        // shifts every later index down by one, which used to silently switch tabs.
        if (index < currentTabIndex) {
            currentTabIndex--;
        } else if (index == currentTabIndex) {
            currentTabIndex = Math.min(index, tabs.size() - 1);
        }
        if (currentTabIndex < 0) {
            currentTabIndex = 0;
        }
        updateTabSwitcher(context);
    }

    private void toggleTabSwitcher(Context context) {
        isTabSwitcherOpen = !isTabSwitcherOpen;
        if (isTabSwitcherOpen) {
            updateTabSwitcher(context);
            tabSwitcherContainer.setVisibility(View.VISIBLE);
            addressEditText.setEnabled(false);
        } else {
            tabSwitcherContainer.setVisibility(View.GONE);
            addressEditText.setEnabled(true);
            switchTab(context, currentTabIndex);
        }
    }

    private void updateTabSwitcher(Context context) {
        tabSwitcherList.removeAllViews();
        tabsCountText.setText(String.valueOf(tabs.size()));
        
        for (int i = 0; i < tabs.size(); i++) {
            final int index = i;
            BrowserTab tab = tabs.get(i);
            
            FrameLayout card = new FrameLayout(context);
            card.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), Theme.getColor(Theme.key_windowBackgroundWhite)));
            card.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12));
            card.setOnClickListener(v -> {
                switchTab(context, index);
                toggleTabSwitcher(context);
            });
            
            LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            
            TextView title = new TextView(context);
            title.setText(tab.title);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            title.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            title.setSingleLine(true);
            content.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));
            
            TextView url = new TextView(context);
            url.setText(TextUtils.isEmpty(tab.currentUrl) ? "PrimeBrowser" : tab.currentUrl);
            url.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            url.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            url.setSingleLine(true);
            content.addView(url, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            
            card.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 0, 0, 48, 0));
            
            ImageView closeBtn = new ImageView(context);
            closeBtn.setImageResource(R.drawable.ic_close_white);
            closeBtn.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
            closeBtn.setOnClickListener(v -> closeTab(context, index));
            closeBtn.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 1));
            card.addView(closeBtn, LayoutHelper.createFrame(24, 24, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
            
            if (i == currentTabIndex) {
                View border = new View(context);
                border.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), 0x2055a4f9)); // Light blue highlight
                card.addView(border, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            }
            
            tabSwitcherList.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));
        }
    }

    private void loadUrlFromInput(Context context) {
        String input = addressEditText.getText().toString().trim();
        if (TextUtils.isEmpty(input)) {
            loadUrl("");
            return;
        }
        loadUrl(normalizeInput(input));
    }

    /** Turns raw address-bar text into either a URL to open or a search-engine query. */
    private String normalizeInput(String input) {
        if (input.startsWith("http://") || input.startsWith("https://") || input.startsWith("about:")) {
            return input;
        }
        // Anything with a scheme we don't render ourselves (mailto:, tel:, magnet:, …)
        // is passed through untouched — shouldOverrideUrlLoading hands it to the system.
        int schemeEnd = input.indexOf(':');
        if (schemeEnd > 1 && !input.contains(" ") && input.indexOf('.') > schemeEnd) {
            return input;
        }
        if (looksLikeDomain(input)) {
            return "https://" + input;
        }
        String engine = MessagesController.getGlobalMainSettings().getString("primegram_search_engine", "Google");
        String query = Uri.encode(input);
        switch (engine) {
            case "Yandex":
                return "https://yandex.ru/search/?text=" + query;
            case "DuckDuckGo":
                return "https://duckduckgo.com/?q=" + query;
            case "Perplexity":
                return "https://www.perplexity.ai/search?q=" + query;
            default:
                return "https://www.google.com/search?q=" + query;
        }
    }

    private boolean looksLikeDomain(String input) {
        if (input.contains(" ")) {
            return false;
        }
        if (input.equals("localhost") || input.startsWith("localhost:")) {
            return true;
        }
        String host = input;
        int slash = host.indexOf('/');
        if (slash != -1) {
            host = host.substring(0, slash);
        }
        int colon = host.indexOf(':');
        if (colon != -1) {
            host = host.substring(0, colon);
        }
        int lastDot = host.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == host.length() - 1) {
            return false;
        }
        // Require a plausible TLD so "1.5" or "версия 2.0" go to search, not to a URL.
        String tld = host.substring(lastDot + 1);
        if (tld.length() < 2) {
            return false;
        }
        for (int i = 0; i < tld.length(); i++) {
            if (!Character.isLetter(tld.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private void loadUrl(String url) {
        BrowserTab tab = tabs.get(currentTabIndex);
        if (TextUtils.isEmpty(url)) {
            tab.currentUrl = "";
            tab.loadedUrl = "";
            if (tab.webView != null) tab.webView.setVisibility(View.GONE);
            homeContainer.setVisibility(View.VISIBLE);
            addressEditText.setText("");
            lockIcon.setImageResource(R.drawable.msg_search);
            tab.title = "Главная";
        } else {
            // The old code only called loadUrl() when a WebView already existed, so opening
            // a link into a fresh tab (or the very first search from the home screen) just
            // put the URL in the address bar and never navigated anywhere.
            Context context = getParentActivity() != null ? getParentActivity() : ApplicationLoader.applicationContext;
            tab.load(context, url);
            attachWebView(tab);
            tab.webView.setVisibility(View.VISIBLE);
            homeContainer.setVisibility(View.GONE);
            if (!addressEditText.isFocused()) {
                addressEditText.setText(url);
            }
        }
        addressEditText.clearFocus();
        AndroidUtilities.hideKeyboard(addressEditText);
        updateNavButtons();
    }

    /** Makes sure the tab's WebView is in the view hierarchy exactly once. */
    private void attachWebView(BrowserTab tab) {
        if (tab.webView == null || middleContainer == null) {
            return;
        }
        if (tab.webView.getParent() == null) {
            middleContainer.addView(tab.webView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }
    }

    private void updateNavButtons() {
        if (tabsCountText != null) {
            tabsCountText.setText(String.valueOf(tabs.size()));
        }
        BrowserTab tab = getCurrentTab();
        if (tab == null) return;

        boolean isWebVisible = !TextUtils.isEmpty(tab.currentUrl);
        
        if (backButton != null) {
            boolean canGoBack = tab.webView != null && tab.webView.canGoBack();
            backButton.setAlpha(isWebVisible && canGoBack ? 1.0f : 0.3f);
            backButton.setEnabled(isWebVisible && canGoBack);
        }
        
        if (forwardButton != null) {
            boolean canGoForward = tab.webView != null && tab.webView.canGoForward();
            forwardButton.setAlpha(isWebVisible && canGoForward ? 1.0f : 0.3f);
            forwardButton.setEnabled(isWebVisible && canGoForward);
        }
        
        if (refreshButton != null) {
            refreshButton.setAlpha(isWebVisible ? 1.0f : 0.3f);
            refreshButton.setEnabled(isWebVisible);
        }
    }

    private BrowserTab getCurrentTab() {
        if (currentTabIndex < 0 || currentTabIndex >= tabs.size()) {
            return null;
        }
        return tabs.get(currentTabIndex);
    }

    private void reloadCurrentPage() {
        BrowserTab tab = getCurrentTab();
        if (tab == null || TextUtils.isEmpty(tab.currentUrl)) {
            return;
        }
        if (tab.webView == null) {
            loadUrl(tab.currentUrl);
        } else {
            tab.webView.reload();
        }
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        // Previously the system back button closed the whole browser even mid-navigation,
        // losing the page stack. Consume it for in-page history and open overlays first.
        if (isTabSwitcherOpen) {
            if (invoked && getParentActivity() != null) {
                toggleTabSwitcher(getParentActivity());
            }
            return false;
        }
        BrowserTab tab = getCurrentTab();
        if (tab != null && tab.webView != null && tab.webView.getVisibility() == View.VISIBLE && tab.webView.canGoBack()) {
            if (invoked) {
                tab.webView.goBack();
            }
            return false;
        }
        return super.onBackPressed(invoked);
    }

    private void showBrowserMenu(View view) {
        BrowserTab tab = getCurrentTab();
        if (tab == null) return;
        boolean hasPage = !TextUtils.isEmpty(tab.currentUrl);

        ItemOptions options = ItemOptions.makeOptions((ViewGroup) fragmentView, view);
        options.add(R.drawable.msg_search, "Новая вкладка", () -> {
            if (getParentActivity() != null) {
                addNewTab(getParentActivity());
            }
        });

        if (hasPage) {
            options.add(R.drawable.msg_reset, LocaleController.getString(R.string.Refresh), this::reloadCurrentPage);
            options.add(R.drawable.msg_copy, "Копировать ссылку", () -> {
                AndroidUtilities.addToClipboard(tab.currentUrl);
                showToast("Ссылка скопирована");
            });
            options.add(R.drawable.msg_share, "Поделиться", () -> shareUrl(tab.currentUrl));
            options.add(R.drawable.msg_language, tab.desktopMode ? "Мобильная версия" : "Версия для ПК", () -> {
                tab.desktopMode = !tab.desktopMode;
                tab.applyUserAgent();
                reloadCurrentPage();
            });
            options.add(R.drawable.msg_openin, "Открыть в системном браузере", () -> {
                Browser.openUrlInSystemBrowser(getParentActivity(), tab.currentUrl);
            });
        }
        options.show();
    }

    /** Long-pressing a link/image offers open-in-new-tab, copy and share, like a real browser. */
    private boolean showLinkContextMenu(WebView webView, View anchor) {
        WebView.HitTestResult result = webView.getHitTestResult();
        if (result == null) {
            return false;
        }
        int type = result.getType();
        String extra = result.getExtra();
        if (TextUtils.isEmpty(extra)) {
            return false;
        }
        boolean isLink = type == WebView.HitTestResult.SRC_ANCHOR_TYPE
                || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE;
        boolean isImage = type == WebView.HitTestResult.IMAGE_TYPE
                || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE;
        if (!isLink && !isImage) {
            return false;
        }

        ItemOptions options = ItemOptions.makeOptions((ViewGroup) fragmentView, anchor);
        if (isLink) {
            options.add(R.drawable.msg_search, "Открыть в новой вкладке", () -> openInNewTab(extra));
        }
        options.add(R.drawable.msg_copy, isImage && !isLink ? "Копировать адрес картинки" : "Копировать ссылку", () -> {
            AndroidUtilities.addToClipboard(extra);
            showToast("Ссылка скопирована");
        });
        options.add(R.drawable.msg_share, "Поделиться", () -> shareUrl(extra));
        options.show();
        return true;
    }

    private void openInNewTab(String url) {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        BrowserTab newTab = new BrowserTab();
        newTab.currentUrl = url;
        tabs.add(newTab);
        switchTab(context, tabs.size() - 1);
    }

    private void shareUrl(String url) {
        if (getParentActivity() == null || TextUtils.isEmpty(url)) {
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, url);
            getParentActivity().startActivity(Intent.createChooser(intent, "Поделиться ссылкой"));
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void showToast(String text) {
        if (getParentActivity() != null) {
            Toast.makeText(getParentActivity(), text, Toast.LENGTH_SHORT).show();
        }
    }

    private class PrimeWebViewClient extends WebViewClient {
        private BrowserTab tab;
        PrimeWebViewClient(BrowserTab tab) { this.tab = tab; }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return handleUrl(request.getUrl() != null ? request.getUrl().toString() : null);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleUrl(url);
        }

        private boolean handleUrl(String url) {
            if (TextUtils.isEmpty(url)) {
                return false;
            }
            if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("about:")) {
                tab.currentUrl = url;
                tab.loadedUrl = url;
                if (tabs.indexOf(tab) == currentTabIndex && !addressEditText.isFocused()) {
                    addressEditText.setText(url);
                }
                return false;
            }
            if (url.startsWith("tg://") || url.startsWith("tg:")) {
                Browser.openUrl(getParentActivity(), url, false);
                return true;
            }
            // Everything else (mailto:, tel:, sms:, geo:, market:, intent:, custom app
            // schemes…) used to fall through and leave the page stuck on a blank frame.
            return openExternally(url);
        }

        private boolean openExternally(String url) {
            Activity activity = getParentActivity();
            if (activity == null) {
                return true;
            }
            try {
                Intent intent;
                if (url.startsWith("intent:")) {
                    intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                    if (intent != null && activity.getPackageManager().resolveActivity(intent, 0) == null) {
                        String fallback = intent.getStringExtra("browser_fallback_url");
                        if (!TextUtils.isEmpty(fallback)) {
                            loadUrl(fallback);
                            return true;
                        }
                        showToast("Приложение для этой ссылки не найдено");
                        return true;
                    }
                } else {
                    intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
            } catch (Exception e) {
                FileLog.e(e);
                showToast("Не удалось открыть ссылку");
            }
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            tab.currentUrl = url;
            tab.loadedUrl = url;
            if (tabs.indexOf(tab) == currentTabIndex) {
                if (!addressEditText.isFocused()) addressEditText.setText(url);
                lockIcon.setImageResource(url.startsWith("https://") ? R.drawable.ic_lock_white : R.drawable.msg_search);
                progressBar.setVisibility(View.VISIBLE);
                updateNavButtons();
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            String pageTitle = view.getTitle();
            tab.title = TextUtils.isEmpty(pageTitle) ? url : pageTitle;
            tab.currentUrl = url;
            tab.loadedUrl = url;
            if (tabs.indexOf(tab) == currentTabIndex) {
                progressBar.setVisibility(View.GONE);
                if (!addressEditText.isFocused()) addressEditText.setText(url);
                updateNavButtons();
            }
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
            super.onReceivedError(view, request, error);
            // Only report failures of the main document — subresource errors are noise.
            if (request != null && request.isForMainFrame()) {
                if (tabs.indexOf(tab) == currentTabIndex) {
                    progressBar.setVisibility(View.GONE);
                }
                // A themed page instead of the system's stock "webpage not available",
                // which is unstyled and shows the raw error code.
                String reason = error != null && error.getDescription() != null
                        ? error.getDescription().toString()
                        : "Сайт не отвечает или недоступен";
                org.telegram.ui.Components.PrimeWebErrorPage.show(view, request.getUrl() != null ? request.getUrl().toString() : tab.currentUrl, reason);
            }
        }
    }

    private class PrimeWebChromeClient extends WebChromeClient {
        private BrowserTab tab;
        PrimeWebChromeClient(BrowserTab tab) { this.tab = tab; }

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            if (tabs.indexOf(tab) == currentTabIndex) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress == 100 ? View.GONE : View.VISIBLE);
            }
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            super.onReceivedTitle(view, title);
            tab.title = title;
        }

        /** Without this, every <input type="file"> on the web is a dead button. */
        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
            Activity activity = getParentActivity();
            if (activity == null) {
                return false;
            }
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(null);
            }
            filePathCallback = callback;
            try {
                Intent intent = params.createIntent();
                if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                }
                activity.startActivityForResult(intent, REQUEST_CODE_FILE_CHOOSER);
                return true;
            } catch (Exception e) {
                FileLog.e(e);
                filePathCallback = null;
                return false;
            }
        }

        /** Camera/microphone access for sites (video calls, voice input, QR scanners). */
        @Override
        public void onPermissionRequest(PermissionRequest request) {
            AndroidUtilities.runOnUIThread(() -> {
                Activity activity = getParentActivity();
                if (activity == null) {
                    request.deny();
                    return;
                }
                ArrayList<String> androidPermissions = new ArrayList<>();
                for (String resource : request.getResources()) {
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                        androidPermissions.add(Manifest.permission.RECORD_AUDIO);
                    } else if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                        androidPermissions.add(Manifest.permission.CAMERA);
                    }
                }
                if (androidPermissions.isEmpty()) {
                    request.deny();
                    return;
                }
                ArrayList<String> missing = new ArrayList<>();
                for (String permission : androidPermissions) {
                    if (activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                        missing.add(permission);
                    }
                }
                if (missing.isEmpty()) {
                    request.grant(request.getResources());
                    return;
                }
                pendingPermissionRequest = request;
                pendingPermissionResources = request.getResources();
                activity.requestPermissions(missing.toArray(new String[0]), REQUEST_CODE_WEB_PERMISSION);
            });
        }

        @Override
        public void onPermissionRequestCanceled(PermissionRequest request) {
            if (pendingPermissionRequest == request) {
                pendingPermissionRequest = null;
                pendingPermissionResources = null;
            }
        }
    }

    /** Routes file downloads to the system DownloadManager instead of silently doing nothing. */
    private class PrimeDownloadListener implements DownloadListener {
        @Override
        public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
            if (getParentActivity() == null) {
                return;
            }
            if (url == null || url.startsWith("blob:") || url.startsWith("data:")) {
                // DownloadManager can't fetch these; hand them to the system instead.
                showToast("Этот файл нельзя скачать напрямую");
                return;
            }
            try {
                String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);
                request.addRequestHeader("User-Agent", userAgent);
                String cookies = CookieManager.getInstance().getCookie(url);
                if (!TextUtils.isEmpty(cookies)) {
                    request.addRequestHeader("Cookie", cookies);
                }
                request.setTitle(fileName);
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

                DownloadManager manager = (DownloadManager) getParentActivity().getSystemService(Context.DOWNLOAD_SERVICE);
                if (manager != null) {
                    manager.enqueue(request);
                    showToast("Загрузка: " + fileName);
                }
            } catch (Exception e) {
                FileLog.e(e);
                showToast("Не удалось начать загрузку");
            }
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_FILE_CHOOSER) {
            if (filePathCallback == null) {
                return;
            }
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) {
                        results[i] = data.getClipData().getItemAt(i).getUri();
                    }
                } else if (data.getData() != null) {
                    results = new Uri[]{data.getData()};
                }
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CODE_WEB_PERMISSION && pendingPermissionRequest != null) {
            boolean granted = grantResults.length > 0;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }
            if (granted) {
                pendingPermissionRequest.grant(pendingPermissionResources);
            } else {
                pendingPermissionRequest.deny();
            }
            pendingPermissionRequest = null;
            pendingPermissionResources = null;
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        return themeDescriptions;
    }
}
