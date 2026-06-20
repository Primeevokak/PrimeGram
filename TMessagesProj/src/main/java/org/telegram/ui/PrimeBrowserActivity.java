package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.net.Uri;
import android.os.Build;
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

    private class BrowserTab {
        WebView webView;
        String currentUrl = "";
        String title = "Главная";

        void init(Context context) {
            if (webView != null) return;
            webView = new WebView(context);
            webView.getSettings().setJavaScriptEnabled(true);
            webView.getSettings().setDomStorageEnabled(true);
            webView.getSettings().setDatabaseEnabled(true);
            webView.getSettings().setUseWideViewPort(true);
            webView.getSettings().setLoadWithOverviewMode(true);
            webView.getSettings().setSupportZoom(true);
            webView.getSettings().setBuiltInZoomControls(true);
            webView.getSettings().setDisplayZoomControls(false);

            if (Build.VERSION.SDK_INT >= 19) {
                webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            }
            if (Build.VERSION.SDK_INT >= 21) {
                webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
                CookieManager cookieManager = CookieManager.getInstance();
                cookieManager.setAcceptThirdPartyCookies(webView, true);
            }

            webView.setWebViewClient(new PrimeWebViewClient(this));
            webView.setWebChromeClient(new PrimeWebChromeClient(this));
        }

        void destroy() {
            if (webView != null) {
                ViewGroup parent = (ViewGroup) webView.getParent();
                if (parent != null) parent.removeView(webView);
                webView.stopLoading();
                webView.loadUrl("about:blank");
                webView.destroy();
                webView = null;
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
        for (BrowserTab tab : tabs) {
            tab.destroy();
        }
        tabs.clear();
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
            BrowserTab tab = tabs.get(currentTabIndex);
            if (tab.webView != null && tab.webView.canGoBack() && tab.webView.getVisibility() == View.VISIBLE) {
                tab.webView.goBack();
            }
        });
        
        forwardButton = createBottomButton(context, R.drawable.ic_ab_back);
        forwardButton.setRotation(180);
        forwardButton.setOnClickListener(v -> {
            BrowserTab tab = tabs.get(currentTabIndex);
            if (tab.webView != null && tab.webView.canGoForward() && tab.webView.getVisibility() == View.VISIBLE) {
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

        ImageView homeButton = createBottomButton(context, R.drawable.msg_home);
        homeButton.setOnClickListener(v -> loadUrl(""));

        bottomBar.addView(backButton, LayoutHelper.createLinear(0, 48, 1.0f));
        bottomBar.addView(forwardButton, LayoutHelper.createLinear(0, 48, 1.0f));
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
        
        BrowserTab oldTab = tabs.get(currentTabIndex);
        if (oldTab.webView != null) {
            oldTab.webView.setVisibility(View.GONE);
        }
        
        currentTabIndex = index;
        BrowserTab currentTab = tabs.get(currentTabIndex);
        
        if (!TextUtils.isEmpty(currentTab.currentUrl)) {
            currentTab.init(context);
            if (currentTab.webView.getParent() == null) {
                middleContainer.addView(currentTab.webView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
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
        
        if (currentTabIndex >= tabs.size()) {
            currentTabIndex = tabs.size() - 1;
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
        
        if (!input.contains(".") || input.contains(" ")) {
            String engine = MessagesController.getGlobalMainSettings().getString("primegram_search_engine", "Google");
            if (engine.equals("Yandex")) {
                input = "https://yandex.ru/search/?text=" + Uri.encode(input);
            } else if (engine.equals("DuckDuckGo")) {
                input = "https://duckduckgo.com/?q=" + Uri.encode(input);
            } else if (engine.equals("Perplexity")) {
                input = "https://www.perplexity.ai/search?q=" + Uri.encode(input);
            } else {
                input = "https://google.com/search?q=" + Uri.encode(input);
            }
        } else if (!input.startsWith("http://") && !input.startsWith("https://")) {
            input = "https://" + input;
        }
        loadUrl(input);
        
        BrowserTab currentTab = tabs.get(currentTabIndex);
        if (currentTab.webView == null) {
            currentTab.init(context);
            middleContainer.addView(currentTab.webView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }
        currentTab.webView.setVisibility(View.VISIBLE);
        homeContainer.setVisibility(View.GONE);
    }

    private void loadUrl(String url) {
        BrowserTab tab = tabs.get(currentTabIndex);
        tab.currentUrl = url;
        if (TextUtils.isEmpty(url)) {
            if (tab.webView != null) tab.webView.setVisibility(View.GONE);
            homeContainer.setVisibility(View.VISIBLE);
            addressEditText.setText("");
            lockIcon.setImageResource(R.drawable.msg_search);
            tab.title = "Главная";
        } else {
            if (tab.webView != null) {
                tab.webView.setVisibility(View.VISIBLE);
                tab.webView.loadUrl(url);
            }
            homeContainer.setVisibility(View.GONE);
            if (!addressEditText.isFocused()) {
                addressEditText.setText(url);
            }
        }
        addressEditText.clearFocus();
        updateNavButtons();
    }

    private void updateNavButtons() {
        if (tabsCountText != null) {
            tabsCountText.setText(String.valueOf(tabs.size()));
        }
        BrowserTab tab = tabs.get(currentTabIndex);
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

    private void showBrowserMenu(View view) {
        BrowserTab tab = tabs.get(currentTabIndex);
        if (TextUtils.isEmpty(tab.currentUrl)) return;
        ItemOptions.makeOptions((ViewGroup) fragmentView, view)
            .add(R.drawable.msg_copy, "Копировать ссылку", () -> {
                AndroidUtilities.addToClipboard(tab.currentUrl);
            })
            .add(R.drawable.msg_openin, "Открыть в системном браузере", () -> {
                Browser.openUrlInSystemBrowser(getParentActivity(), tab.currentUrl);
            })
            .show();
    }

    private class PrimeWebViewClient extends WebViewClient {
        private BrowserTab tab;
        PrimeWebViewClient(BrowserTab tab) { this.tab = tab; }
        
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                tab.currentUrl = url;
                if (tabs.indexOf(tab) == currentTabIndex) {
                    addressEditText.setText(url);
                }
                return false;
            } else if (url.startsWith("tg://") || url.startsWith("intent://")) {
                Browser.openUrl(getParentActivity(), url, false);
                return true;
            }
            return false;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            tab.currentUrl = url;
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
            tab.title = view.getTitle();
            if (tabs.indexOf(tab) == currentTabIndex) {
                progressBar.setVisibility(View.GONE);
                updateNavButtons();
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
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        return themeDescriptions;
    }
}
