/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Parcelable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeColors;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.BottomPagesView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.SimpleThemeDescription;
import org.telegram.ui.Components.voip.CellFlickerDrawable;

import java.util.ArrayList;

public class IntroActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private final static int ICON_WIDTH_DP = 200, ICON_HEIGHT_DP = 150;

    private final Object pagerHeaderTag = new Object(),
            pagerMessageTag = new Object();

    private final int currentAccount = UserConfig.selectedAccount;

    private ViewPager viewPager;
    private BottomPagesView bottomPages;
    private TextView switchLanguageTextView;
    private GradientDrawable startMessagingButtonBackground;
    private TextView startMessagingButton;
    private FrameLayout frameLayout2;
    private FrameLayout frameContainerView;

    private RLottieDrawable darkThemeDrawable;

    private int lastPage = 0;
    private boolean justCreated = false;
    private boolean startPressed = false;
    private CharSequence[] titles;
    private String[] messages;
    private int currentViewPagerPage;
    private IntroIconView[] iconViews;
    private int currentIconIndex;
    private boolean justEndDragging;
    private boolean dragging;
    private int startDragX;

    private LocaleController.LocaleInfo localeInfo;

    private boolean destroyed;

    private boolean isOnLogout;

    @Override
    public boolean onFragmentCreate() {
        MessagesController.getGlobalMainSettings().edit().putLong("intro_crashed_time", System.currentTimeMillis()).apply();

        titles = new CharSequence[]{
                null,
                LocaleController.getString(R.string.Page2Title),
                LocaleController.getString(R.string.Page3Title),
                LocaleController.getString(R.string.Page5Title),
                LocaleController.getString(R.string.Page4Title),
                LocaleController.getString(R.string.Page6Title)
        };
        messages = new String[]{
                LocaleController.getString(R.string.Page1Message),
                LocaleController.getString(R.string.Page2Message),
                LocaleController.getString(R.string.Page3Message),
                LocaleController.getString(R.string.Page5Message),
                LocaleController.getString(R.string.Page4Message),
                LocaleController.getString(R.string.Page6Message)
        };
        return true;
    }

    @Override
    public View createView(Context context) {
        titles[0] = LocaleController.getString(R.string.Page1Title);


        actionBar.setAddToContainer(false);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);

        RLottieImageView themeIconView = new RLottieImageView(context);
        FrameLayout themeFrameLayout = new FrameLayout(context);
        themeFrameLayout.addView(themeIconView, LayoutHelper.createFrame(28, 28, Gravity.CENTER));

        int themeMargin = 4;
        frameContainerView = new FrameLayout(context) {

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);

                int oneFourth = (bottom - top) / 4;

                int y = (oneFourth * 3 - dp(275)) / 2;
                frameLayout2.layout(0, y, frameLayout2.getMeasuredWidth(), y + frameLayout2.getMeasuredHeight());
                y += dp(ICON_HEIGHT_DP);
                y += dp(122 + 17);
                int x = (getMeasuredWidth() - bottomPages.getMeasuredWidth()) / 2;
                bottomPages.layout(x, y, x + bottomPages.getMeasuredWidth(), y + bottomPages.getMeasuredHeight());
                viewPager.layout(0, 0, viewPager.getMeasuredWidth(), viewPager.getMeasuredHeight());

                y = oneFourth * 3 + (oneFourth - startMessagingButton.getMeasuredHeight()) / 2;
                x = (getMeasuredWidth() - startMessagingButton.getMeasuredWidth()) / 2;
                startMessagingButton.layout(x, y, x + startMessagingButton.getMeasuredWidth(), y + startMessagingButton.getMeasuredHeight());
                y -= dp(30);
                x = (getMeasuredWidth() - switchLanguageTextView.getMeasuredWidth()) / 2;
                switchLanguageTextView.layout(x, y - switchLanguageTextView.getMeasuredHeight(), x + switchLanguageTextView.getMeasuredWidth(), y);

                MarginLayoutParams marginLayoutParams = (MarginLayoutParams) themeFrameLayout.getLayoutParams();
                int newTopMargin = dp(themeMargin) + (AndroidUtilities.isTablet() ? 0 : AndroidUtilities.statusBarHeight);
                if (marginLayoutParams.topMargin != newTopMargin) {
                    marginLayoutParams.topMargin = newTopMargin;
                    themeFrameLayout.requestLayout();
                }
            }
        };
        scrollView.addView(frameContainerView, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));

        darkThemeDrawable = new RLottieDrawable(R.raw.sun, String.valueOf(R.raw.sun), dp(28), dp(28), true, null);
        darkThemeDrawable.setPlayInDirectionOfCustomEndFrame(true);
        darkThemeDrawable.beginApplyLayerColors();
        darkThemeDrawable.commitApplyLayerColors();

        darkThemeDrawable.setCustomEndFrame(Theme.getCurrentTheme().isDark() ? darkThemeDrawable.getFramesCount() - 1 : 0);
        darkThemeDrawable.setCurrentFrame(Theme.getCurrentTheme().isDark() ? darkThemeDrawable.getFramesCount() - 1 : 0, false);
        themeIconView.setContentDescription(LocaleController.getString(Theme.getCurrentTheme().isDark() ? R.string.AccDescrSwitchToDayTheme : R.string.AccDescrSwitchToNightTheme));

        themeIconView.setAnimation(darkThemeDrawable);
        themeFrameLayout.setOnClickListener(v -> {
            if (DialogsActivity.switchingTheme) return;
            DialogsActivity.switchingTheme = true;

            // TODO: Generify this part, currently it's a clone of another theme switch toggle
            String dayThemeName = "Blue";
            String nightThemeName = "Night";

            Theme.ThemeInfo themeInfo;
            boolean toDark;
            if (toDark = !Theme.isCurrentThemeDark()) {
                themeInfo = Theme.getTheme(nightThemeName);
            } else {
                themeInfo = Theme.getTheme(dayThemeName);
            }

            Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_NONE;
            Theme.saveAutoNightThemeConfig();
            Theme.cancelAutoNightThemeCallbacks();

            darkThemeDrawable.setCustomEndFrame(toDark ? darkThemeDrawable.getFramesCount() - 1 : 0);
            themeIconView.playAnimation();

            int[] pos = new int[2];
            themeIconView.getLocationInWindow(pos);
            pos[0] += themeIconView.getMeasuredWidth() / 2;
            pos[1] += themeIconView.getMeasuredHeight() / 2;
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, themeInfo, false, pos, -1, toDark, themeIconView);
            themeIconView.setContentDescription(LocaleController.getString(toDark ? R.string.AccDescrSwitchToDayTheme : R.string.AccDescrSwitchToNightTheme));
        });

        frameLayout2 = new FrameLayout(context);
        frameContainerView.addView(frameLayout2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 78, 0, 0));

        // PrimeGram: order matches titles[]/messages[] exactly - Brand, Fast, Free, Powerful,
        // Secure, Cloud - so pageIndex from the pager can index straight into this array.
        iconViews = new IntroIconView[]{
                new AtomIconView(context), new FastIconView(context), new FreeIconView(context),
                new PowerfulIconView(context), new SecureIconView(context), new CloudIconView(context)
        };
        for (int i = 0; i < iconViews.length; i++) {
            iconViews[i].setAlpha(i == 0 ? 1f : 0f);
            frameLayout2.addView(iconViews[i], LayoutHelper.createFrame(ICON_WIDTH_DP, ICON_HEIGHT_DP, Gravity.CENTER));
        }

        viewPager = new ViewPager(context);
        viewPager.setAdapter(new IntroAdapter());
        viewPager.setPageMargin(0);
        viewPager.setOffscreenPageLimit(1);
        frameContainerView.addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                bottomPages.setPageOffset(position, positionOffset);
            }

            @Override
            public void onPageSelected(int i) {
                currentViewPagerPage = i;
                crossfadeIconTo(i);
            }

            @Override
            public void onPageScrollStateChanged(int i) {
                if (i == ViewPager.SCROLL_STATE_DRAGGING) {
                    dragging = true;
                    startDragX = viewPager.getCurrentItem() * viewPager.getMeasuredWidth();
                } else if (i == ViewPager.SCROLL_STATE_IDLE || i == ViewPager.SCROLL_STATE_SETTLING) {
                    if (dragging) {
                        justEndDragging = true;
                        dragging = false;
                    }
                    if (lastPage != viewPager.getCurrentItem()) {
                        lastPage = viewPager.getCurrentItem();
                    }
                }
            }
        });

        startMessagingButtonBackground = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, null);
        startMessagingButton = new TextView(context) {
            private final CellFlickerDrawable cellFlickerDrawable = new CellFlickerDrawable(); {
                cellFlickerDrawable.drawFrame = false;
                cellFlickerDrawable.repeatProgress = 2f;
            }

            @Override
            protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                super.onSizeChanged(w, h, oldw, oldh);
                startMessagingButtonBackground.setBounds(0, 0, w, h);
                startMessagingButtonBackground.setCornerRadius(Math.min(w, h) / 2f);
                cellFlickerDrawable.setParentWidth(w);
            }

            @Override
            public void draw(@NonNull Canvas canvas) {
                startMessagingButtonBackground.draw(canvas);
                super.draw(canvas);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                AndroidUtilities.rectTmp.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                cellFlickerDrawable.draw(canvas, AndroidUtilities.rectTmp, getMeasuredHeight() / 2f, null);
                invalidate();
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int size = MeasureSpec.getSize(widthMeasureSpec);
                if (size > dp(260)) {
                    super.onMeasure(MeasureSpec.makeMeasureSpec(dp(320), MeasureSpec.EXACTLY), heightMeasureSpec);
                } else {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }
            }
        };
        ScaleStateListAnimator.apply(startMessagingButton, .02f, 1.2f);
        startMessagingButton.setText(LocaleController.getString(R.string.StartMessaging));
        startMessagingButton.setGravity(Gravity.CENTER);
        startMessagingButton.setTypeface(AndroidUtilities.bold());
        startMessagingButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        startMessagingButton.setPadding(dp(34), 0, dp(34), 0);
        frameContainerView.addView(startMessagingButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 16, 0, 16, 76));
        startMessagingButton.setOnClickListener(view -> {
            if (startPressed) {
                return;
            }
            startPressed = true;

            presentFragment(new ProxySetupActivity(), true);
            destroyed = true;
        });

        bottomPages = new BottomPagesView(context, viewPager, 6);
        frameContainerView.addView(bottomPages, LayoutHelper.createFrame(66, 5, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, ICON_HEIGHT_DP + 200, 0, 0));

        switchLanguageTextView = new TextView(context);
        switchLanguageTextView.setGravity(Gravity.CENTER);
        switchLanguageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        frameContainerView.addView(switchLanguageTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 30, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 20));
        switchLanguageTextView.setOnClickListener(v -> {
            if (startPressed || localeInfo == null) {
                return;
            }
            startPressed = true;

            AlertDialog loaderDialog = new AlertDialog(v.getContext(), AlertDialog.ALERT_TYPE_SPINNER);
            loaderDialog.setCanCancel(false);
            loaderDialog.showDelayed(1000);

            NotificationCenter.getGlobalInstance().addObserver(new NotificationCenter.NotificationCenterDelegate() {
                @Override
                public void didReceivedNotification(int id, int account, Object... args) {
                    if (id == NotificationCenter.reloadInterface) {
                        loaderDialog.dismiss();

                        NotificationCenter.getGlobalInstance().removeObserver(this, id);
                         AndroidUtilities.runOnUIThread(()->{
                            presentFragment(new ProxySetupActivity(), true);
                            destroyed = true;
                        }, 100);
                    }
                }
            }, NotificationCenter.reloadInterface);
            LocaleController.getInstance().applyLanguage(localeInfo, true, false, currentAccount);
        });

        frameContainerView.addView(themeFrameLayout, LayoutHelper.createFrame(64, 64, Gravity.TOP | Gravity.RIGHT, 0, themeMargin, themeMargin, 0));

        fragmentView = scrollView;

        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.suggestedLangpack);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.configLoaded);
        ConnectionsManager.getInstance(currentAccount).updateDcSettings();
        LocaleController.getInstance().loadRemoteLanguages(currentAccount);
        checkContinueText();
        justCreated = true;

        updateColors(false);

        return fragmentView;
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    public void onResume() {
        super.onResume();
        if (justCreated) {
            if (LocaleController.isRTL) {
                viewPager.setCurrentItem(6);
                lastPage = 6;
            } else {
                viewPager.setCurrentItem(0);
                lastPage = 0;
            }
            justCreated = false;
        }
        if (!AndroidUtilities.isTablet()) {
            Activity activity = getParentActivity();
            if (activity != null) {
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (!AndroidUtilities.isTablet()) {
            Activity activity = getParentActivity();
            if (activity != null) {
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            }
        }
    }

    @Override
    public boolean hasForceLightStatusBar() {
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        destroyed = true;
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.suggestedLangpack);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.configLoaded);
        MessagesController.getGlobalMainSettings().edit().putLong("intro_crashed_time", 0).apply();
    }

    /** Crossfades the icon area from whichever page was showing to {@code index} - a discrete
     *  per-page swap rather than a continuous scroll-linked morph, since each page's icon is now
     *  its own distinct animation (a plane, a shield, a cloud...) rather than one shared scene a
     *  single canvas could blend between. */
    private void crossfadeIconTo(int index) {
        if (iconViews == null || index == currentIconIndex || index < 0 || index >= iconViews.length) {
            return;
        }
        IntroIconView from = iconViews[currentIconIndex];
        IntroIconView to = iconViews[index];
        currentIconIndex = index;
        from.animate().cancel();
        to.animate().cancel();
        from.animate().alpha(0f).setDuration(260).start();
        to.setAlpha(0f);
        to.animate().alpha(1f).setDuration(260).start();
    }

    private void checkContinueText() {
        LocaleController.LocaleInfo englishInfo = null;
        LocaleController.LocaleInfo systemInfo = null;
        LocaleController.LocaleInfo currentLocaleInfo = LocaleController.getInstance().getCurrentLocaleInfo();
        String systemLang = MessagesController.getInstance(currentAccount).suggestedLangCode;
        if (systemLang == null || systemLang.equals("en") && LocaleController.getInstance().getSystemDefaultLocale().getLanguage() != null && !LocaleController.getInstance().getSystemDefaultLocale().getLanguage().equals("en")) {
            systemLang = LocaleController.getInstance().getSystemDefaultLocale().getLanguage();
            if (systemLang == null) {
                systemLang = "en";
            }
        }

        String arg = systemLang.contains("-") ? systemLang.split("-")[0] : systemLang;
        String alias = LocaleController.getLocaleAlias(arg);
        for (int a = 0; a < LocaleController.getInstance().languages.size(); a++) {
            LocaleController.LocaleInfo info = LocaleController.getInstance().languages.get(a);
            if (info.shortName.equals("en")) {
                englishInfo = info;
            }
            if (info.shortName.replace("_", "-").equals(systemLang) || info.shortName.equals(arg) || info.shortName.equals(alias)) {
                systemInfo = info;
            }
            if (englishInfo != null && systemInfo != null) {
                break;
            }
        }
        if (englishInfo == null || systemInfo == null || englishInfo == systemInfo) {
            return;
        }
        TLRPC.TL_langpack_getStrings req = new TLRPC.TL_langpack_getStrings();
        if (systemInfo != currentLocaleInfo) {
            req.lang_code = systemInfo.getLangCode();
            localeInfo = systemInfo;
        } else {
            req.lang_code = englishInfo.getLangCode();
            localeInfo = englishInfo;
        }
        req.keys.add("ContinueOnThisLanguage");
        String finalSystemLang = systemLang;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (response instanceof Vector) {
                Vector vector = (Vector) response;
                if (vector.objects.isEmpty()) {
                    return;
                }
                final TLRPC.LangPackString string = (TLRPC.LangPackString) vector.objects.get(0);
                if (string instanceof TLRPC.TL_langPackString) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (!destroyed) {
                            switchLanguageTextView.setText(string.value);
                            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                            preferences.edit().putString("language_showed2", finalSystemLang.toLowerCase()).apply();
                        }
                    });
                }
            }
        }, ConnectionsManager.RequestFlagWithoutLogin);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.suggestedLangpack || id == NotificationCenter.configLoaded) {
            checkContinueText();
        }
    }

    public IntroActivity setOnLogout() {
        isOnLogout = true;
        return this;
    }

    @Override
    public AnimatorSet onCustomTransitionAnimation(boolean isOpen, Runnable callback) {
        if (isOnLogout) {
            AnimatorSet set = new AnimatorSet().setDuration(50);
            set.playTogether(ValueAnimator.ofFloat());
            return set;
        }
        return null;
    }

    private class IntroAdapter extends PagerAdapter {
        @Override
        public int getCount() {
            return titles.length;
        }

        @NonNull
        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            TextView headerTextView = new TextView(container.getContext());
            headerTextView.setTag(pagerHeaderTag);
            TextView messageTextView = new TextView(container.getContext());
            messageTextView.setTag(pagerMessageTag);

            FrameLayout frameLayout = new FrameLayout(container.getContext()) {
                @Override
                protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                    int oneFourth = (bottom - top) / 4;
                    int y = (oneFourth * 3 - dp(275)) / 2;
                    y += dp(ICON_HEIGHT_DP);
                    y += dp(16 + 9);
                    int x = dp(18);
                    headerTextView.layout(x, y, x + headerTextView.getMeasuredWidth(), y + headerTextView.getMeasuredHeight());

                    y += (int) headerTextView.getTextSize();
                    y += dp(18);
                    x = dp(16);
                    messageTextView.layout(x, y, x + messageTextView.getMeasuredWidth(), y + messageTextView.getMeasuredHeight());
                }
            };

            headerTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            headerTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 26);
            headerTextView.setTypeface(AndroidUtilities.bold());
            headerTextView.setGravity(Gravity.CENTER);
            frameLayout.addView(headerTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 18, 244, 18, 0));

            messageTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            messageTextView.setLineSpacing(dpf2(2.33f), 1f);
            messageTextView.setGravity(Gravity.CENTER);
            frameLayout.addView(messageTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 16, 286, 16, 0));

            container.addView(frameLayout, 0);

            headerTextView.setText(titles[position]);
            messageTextView.setText(AndroidUtilities.replaceTags(messages[position]));

            return frameLayout;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, @NonNull Object object) {
            container.removeView((View) object);
        }

        @Override
        public void setPrimaryItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            super.setPrimaryItem(container, position, object);
            bottomPages.setCurrentPage(position);
            currentViewPagerPage = position;
        }

        @Override
        public boolean isViewFromObject(View view, @NonNull Object object) {
            return view.equals(object);
        }

        @Override
        public void restoreState(Parcelable arg0, ClassLoader arg1) {
        }

        @Override
        public Parcelable saveState() {
            return null;
        }

        @Override
        public void unregisterDataSetObserver(@NonNull DataSetObserver observer) {
            if (observer != null) {
                super.unregisterDataSetObserver(observer);
            }
        }
    }

    /** PrimeGram: base for the intro's six per-page animated icons, replacing the original
     *  OpenGL-rendered scenes (paper plane, bubble, lock...) which lived entirely in native C++
     *  code and aren't something this fork can meaningfully re-theme.
     *
     *  <p>Driven by a continuously-growing elapsed-time clock ({@code t}, seconds since attach),
     *  never a bounded/repeating {@link ValueAnimator} - a repeating animator that resets its
     *  value to 0 on every lap is only seamless if every motion it drives happens to complete a
     *  whole number of cycles by then, and any orbit/oscillation whose speed isn't an exact
     *  integer multiple visibly snaps at that reset. Feeding sin/cos (themselves perfectly
     *  periodic) with an ever-increasing {@code t} instead has no such seam to hit - there is
     *  simply no "restart" moment for a viewer to notice. */
    private static abstract class IntroIconView extends View {

        private long startNanos;
        private boolean running;
        private final Runnable frameTick = this::onFrameTick;

        protected int accent;
        protected int accentBright;
        protected int accentSoft;
        protected int accentFaint;

        IntroIconView(Context context) {
            super(context);
            // Deliberately NOT calling updateColors() here - it dispatches to the subclass's
            // overridden onColorsUpdated(), which touches Paint fields the subclass constructor
            // hasn't initialized yet at this point (Java runs the superclass constructor, including
            // any virtual call it makes, before the subclass's own field initializers). Each
            // subclass calls updateColors() itself once its fields are actually built.
        }

        void updateColors() {
            accent = Theme.getColor(Theme.key_featuredStickers_addButton);
            accentBright = ColorUtils.blendARGB(accent, Color.WHITE, 0.55f);
            accentSoft = ColorUtils.setAlphaComponent(accent, 150);
            accentFaint = ColorUtils.setAlphaComponent(accent, 60);
            onColorsUpdated();
            invalidate();
        }

        protected void onColorsUpdated() {
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (!running) {
                running = true;
                startNanos = System.nanoTime();
                postOnAnimation(frameTick);
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            running = false;
            removeCallbacks(frameTick);
        }

        private void onFrameTick() {
            if (!running) {
                return;
            }
            invalidate();
            postOnAnimation(frameTick);
        }

        @Override
        protected final void onDraw(Canvas canvas) {
            float t = (System.nanoTime() - startNanos) / 1_000_000_000f;
            drawIcon(canvas, getWidth() / 2f, getHeight() / 2f, t);
        }

        /** @param t seconds since this view was attached, monotonically increasing forever -
         *  build all motion from it via sin/cos/mod, never from a value that gets reset. */
        protected abstract void drawIcon(Canvas canvas, float cx, float cy, float t);
    }

    /** Page 1 (brand): a nucleus with three electrons on tilted elliptical orbits, each at its
     *  own constant angular speed - the classic "atom" silhouette, standing in for the brand mark
     *  itself rather than any one feature. */
    private static class AtomIconView extends IntroIconView {
        private final Paint nucleusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint nucleusGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint orbitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint electronPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint electronGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private static final float[] ORBIT_TILT_DEG = {-24f, 0f, 24f};
        private static final float[] ORBIT_SPEED = {0.62f, -0.47f, 0.38f};
        private static final float[] ORBIT_PHASE = {0f, 2.05f, 4.35f};

        AtomIconView(Context context) {
            super(context);
            orbitPaint.setStyle(Paint.Style.STROKE);
            orbitPaint.setStrokeWidth(dpf2(1.33f));
            updateColors();
        }

        @Override
        protected void onColorsUpdated() {
            nucleusPaint.setShader(new RadialGradient(0, 0, dpf2(11), Color.WHITE, accent, Shader.TileMode.CLAMP));
            nucleusGlowPaint.setColor(accentFaint);
            orbitPaint.setColor(accentSoft);
            electronPaint.setShader(new RadialGradient(0, 0, dpf2(4.4f), Color.WHITE, accent, Shader.TileMode.CLAMP));
            electronGlowPaint.setColor(accentSoft);
        }

        @Override
        protected void drawIcon(Canvas canvas, float cx, float cy, float t) {
            float orbitA = Math.min(getWidth() * 0.42f, dp(78));
            float orbitB = orbitA * 0.42f;
            float breathe = 1f + 0.06f * (float) Math.sin(t * 1.6);

            canvas.save();
            canvas.translate(cx, cy);

            for (int i = 0; i < ORBIT_TILT_DEG.length; i++) {
                canvas.save();
                canvas.rotate(ORBIT_TILT_DEG[i]);
                canvas.drawOval(-orbitA, -orbitB, orbitA, orbitB, orbitPaint);

                float angle = ORBIT_PHASE[i] + t * ORBIT_SPEED[i] * (float) (Math.PI * 2);
                float ex = orbitA * (float) Math.cos(angle);
                float ey = orbitB * (float) Math.sin(angle);

                canvas.save();
                canvas.translate(ex, ey);
                canvas.drawCircle(0, 0, dpf2(7.5f), electronGlowPaint);
                canvas.drawCircle(0, 0, dpf2(4.4f), electronPaint);
                canvas.restore();
                canvas.restore();
            }

            canvas.scale(breathe, breathe);
            canvas.drawCircle(0, 0, dpf2(17f), nucleusGlowPaint);
            canvas.drawCircle(0, 0, dpf2(11f), nucleusPaint);
            canvas.restore();
        }
    }

    /** Page 2 ("Fast"): a paper plane gliding left to right with a fading speed trail, wrapping
     *  back to the start well off-canvas so the wrap itself is never on screen to look abrupt. */
    private static class FastIconView extends IntroIconView {
        private final Paint planePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path planePath = new Path();

        FastIconView(Context context) {
            super(context);
            planePaint.setStyle(Paint.Style.FILL);
            trailPaint.setStyle(Paint.Style.STROKE);
            trailPaint.setStrokeWidth(dpf2(2.6f));
            trailPaint.setStrokeCap(Paint.Cap.ROUND);
            updateColors();
        }

        @Override
        protected void onColorsUpdated() {
            planePaint.setColor(accentBright);
            trailPaint.setColor(accent);
        }

        @Override
        protected void drawIcon(Canvas canvas, float cx, float cy, float t) {
            float w = getWidth();
            float span = w + dp(120);
            float speed = dp(58); // px/sec
            float progress = (t * speed) % span;
            float x = -dp(60) + progress;
            float y = cy + (float) Math.sin(t * 1.3) * dp(10);

            canvas.save();
            canvas.translate(x, y);
            canvas.rotate(-8 + (float) Math.sin(t * 1.3) * 4f);

            float trailX = -dp(14);
            for (int i = 0; i < 4; i++) {
                float len = dp(16) - i * dp(3.2f);
                int alpha = 140 - i * 32;
                trailPaint.setAlpha(Math.max(0, alpha));
                canvas.drawLine(trailX - i * dp(11), 0, trailX - i * dp(11) - len, 0, trailPaint);
            }

            float s = dp(15);
            planePath.reset();
            planePath.moveTo(s, 0);
            planePath.lineTo(-s, s * 0.62f);
            planePath.lineTo(-s * 0.35f, 0);
            planePath.lineTo(-s, -s * 0.62f);
            planePath.close();
            canvas.drawPath(planePath, planePaint);
            canvas.restore();
        }
    }

    /** Page 3 ("Free"): an infinity ribbon (lemniscate of Bernoulli) with a glowing dot and
     *  fading comet-tail travelling around it - trig-parametric, so it is exactly periodic with
     *  no seam to hide. */
    private static class FreeIconView extends IntroIconView {
        private final Paint ribbonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path ribbonPath = new Path();

        FreeIconView(Context context) {
            super(context);
            ribbonPaint.setStyle(Paint.Style.STROKE);
            ribbonPaint.setStrokeWidth(dpf2(2.6f));
            ribbonPaint.setStrokeCap(Paint.Cap.ROUND);
            dotPaint.setStyle(Paint.Style.FILL);
            updateColors();
        }

        @Override
        protected void onColorsUpdated() {
            ribbonPaint.setColor(accentFaint);
            dotPaint.setColor(accentBright);
        }

        private float lemX(float a, float angle) {
            float s = (float) Math.sin(angle);
            return (float) (a * Math.cos(angle) / (1 + s * s));
        }

        private float lemY(float a, float angle) {
            float s = (float) Math.sin(angle);
            return (float) (a * Math.sin(angle) * Math.cos(angle) / (1 + s * s));
        }

        @Override
        protected void drawIcon(Canvas canvas, float cx, float cy, float t) {
            float a = Math.min(getWidth() * 0.44f, dp(80));

            ribbonPath.reset();
            int steps = 96;
            for (int i = 0; i <= steps; i++) {
                float angle = (float) (i * Math.PI * 2 / steps);
                float x = cx + lemX(a, angle);
                float y = cy + lemY(a, angle);
                if (i == 0) ribbonPath.moveTo(x, y); else ribbonPath.lineTo(x, y);
            }
            canvas.drawPath(ribbonPath, ribbonPaint);

            float speed = 1.1f;
            for (int i = 6; i >= 0; i--) {
                float angle = t * speed - i * 0.09f;
                float x = cx + lemX(a, angle);
                float y = cy + lemY(a, angle);
                dotPaint.setAlpha(i == 0 ? 255 : Math.max(0, 140 - i * 22));
                canvas.drawCircle(x, y, i == 0 ? dpf2(6.2f) : dpf2(6.2f) - i * dpf2(0.6f), dotPaint);
            }
        }
    }

    /** Page 4 ("Powerful"): a slowly-rotating energy burst whose rays pulse in length with their
     *  own phase offsets, so the whole shape never freezes into a static star. */
    private static class PowerfulIconView extends IntroIconView {
        private final Paint rayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private static final int RAY_COUNT = 10;

        PowerfulIconView(Context context) {
            super(context);
            rayPaint.setStyle(Paint.Style.STROKE);
            rayPaint.setStrokeWidth(dpf2(3f));
            rayPaint.setStrokeCap(Paint.Cap.ROUND);
            updateColors();
        }

        @Override
        protected void onColorsUpdated() {
            rayPaint.setColor(accentSoft);
            corePaint.setShader(new RadialGradient(0, 0, dpf2(14), Color.WHITE, accent, Shader.TileMode.CLAMP));
        }

        @Override
        protected void drawIcon(Canvas canvas, float cx, float cy, float t) {
            float baseLen = dp(30);
            float amp = dp(14);
            float rotation = t * 14f; // deg/sec, slow drift

            canvas.save();
            canvas.translate(cx, cy);
            canvas.rotate(rotation);
            for (int i = 0; i < RAY_COUNT; i++) {
                float angle = (float) (i * Math.PI * 2 / RAY_COUNT);
                float pulse = 0.5f + 0.5f * (float) Math.sin(t * 2.4 + i * 0.9);
                float len = baseLen + amp * pulse;
                float innerR = dp(15);
                float dx = (float) Math.cos(angle), dy = (float) Math.sin(angle);
                rayPaint.setAlpha((int) (110 + 130 * pulse));
                canvas.drawLine(dx * innerR, dy * innerR, dx * (innerR + len), dy * (innerR + len), rayPaint);
            }
            float coreScale = 1f + 0.08f * (float) Math.sin(t * 2.4);
            canvas.scale(coreScale, coreScale);
            canvas.drawCircle(0, 0, dpf2(14f), corePaint);
            canvas.restore();
        }
    }

    /** Page 5 ("Secure"): a shield outline with a checkmark that materialises and dematerialises
     *  in a smooth, endless breathing cycle (a sine-shaped envelope driving how much of the
     *  checkmark's path is stroked in), plus a soft pulsing ring. */
    private static class SecureIconView extends IntroIconView {
        private final Paint shieldPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path shieldPath = new Path();
        private final Path checkPath = new Path();
        private final Path checkSegment = new Path();
        private final android.graphics.PathMeasure checkMeasure = new android.graphics.PathMeasure();
        private float checkLength;

        SecureIconView(Context context) {
            super(context);
            shieldPaint.setStyle(Paint.Style.STROKE);
            shieldPaint.setStrokeWidth(dpf2(2.6f));
            shieldPaint.setStrokeJoin(Paint.Join.ROUND);
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(dpf2(1.6f));
            checkPaint.setStyle(Paint.Style.STROKE);
            checkPaint.setStrokeWidth(dpf2(3.2f));
            checkPaint.setStrokeCap(Paint.Cap.ROUND);
            checkPaint.setStrokeJoin(Paint.Join.ROUND);
            updateColors();
        }

        @Override
        protected void onColorsUpdated() {
            shieldPaint.setColor(accentSoft);
            ringPaint.setColor(accentFaint);
            checkPaint.setColor(accentBright);
        }

        @Override
        protected void drawIcon(Canvas canvas, float cx, float cy, float t) {
            float w = dp(46), h = dp(56);
            shieldPath.reset();
            shieldPath.moveTo(0, -h / 2f);
            shieldPath.cubicTo(w * 0.55f, -h * 0.42f, w / 2f, -h * 0.15f, w / 2f, h * 0.05f);
            shieldPath.cubicTo(w / 2f, h * 0.38f, w * 0.22f, h * 0.46f, 0, h / 2f);
            shieldPath.cubicTo(-w * 0.22f, h * 0.46f, -w / 2f, h * 0.38f, -w / 2f, h * 0.05f);
            shieldPath.cubicTo(-w / 2f, -h * 0.15f, -w * 0.55f, -h * 0.42f, 0, -h / 2f);
            shieldPath.close();

            checkPath.reset();
            checkPath.moveTo(-w * 0.24f, 0);
            checkPath.lineTo(-w * 0.06f, h * 0.16f);
            checkPath.lineTo(w * 0.28f, -h * 0.14f);
            checkMeasure.setPath(checkPath, false);
            checkLength = checkMeasure.getLength();

            float envelope = 0.5f + 0.5f * (float) Math.sin(t * 1.15 - Math.PI / 2);
            float ringPulse = 0.5f + 0.5f * (float) Math.sin(t * 1.15);

            canvas.save();
            canvas.translate(cx, cy);

            ringPaint.setAlpha((int) (90 * (1f - ringPulse) + 20));
            float ringR = dp(34) + ringPulse * dp(14);
            canvas.drawCircle(0, 0, ringR, ringPaint);

            canvas.drawPath(shieldPath, shieldPaint);

            checkSegment.reset();
            checkMeasure.getSegment(0, checkLength * envelope, checkSegment, true);
            canvas.drawPath(checkSegment, checkPaint);

            canvas.restore();
        }
    }

    /** Page 6 ("Cloud-based"): a cloud silhouette bobbing gently, with small sync chevrons
     *  flowing upward through it - a quiet, continuous loop rather than a one-shot upload icon. */
    private static class CloudIconView extends IntroIconView {
        private final Paint cloudPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint chevronPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        CloudIconView(Context context) {
            super(context);
            cloudPaint.setStyle(Paint.Style.FILL);
            chevronPaint.setStyle(Paint.Style.STROKE);
            chevronPaint.setStrokeWidth(dpf2(2.6f));
            chevronPaint.setStrokeCap(Paint.Cap.ROUND);
            chevronPaint.setStrokeJoin(Paint.Join.ROUND);
            updateColors();
        }

        @Override
        protected void onColorsUpdated() {
            cloudPaint.setColor(accentFaint);
            chevronPaint.setColor(accentBright);
        }

        @Override
        protected void drawIcon(Canvas canvas, float cx, float cy, float t) {
            float bob = (float) Math.sin(t * 1.1) * dp(4);
            canvas.save();
            canvas.translate(cx, cy + bob);

            canvas.drawCircle(-dp(22), dp(4), dp(16), cloudPaint);
            canvas.drawCircle(dp(2), -dp(6), dp(20), cloudPaint);
            canvas.drawCircle(dp(24), dp(6), dp(15), cloudPaint);
            canvas.drawRoundRect(-dp(30), dp(0), dp(34), dp(20), dp(14), dp(14), cloudPaint);

            for (int i = 0; i < 2; i++) {
                float phase = ((t * 0.6f + i * 0.5f) % 1f);
                float y = dp(2) - phase * dp(34);
                float alpha = phase < 0.15f ? phase / 0.15f : phase > 0.8f ? (1f - phase) / 0.2f : 1f;
                chevronPaint.setAlpha((int) (220 * Math.max(0f, Math.min(1f, alpha))));
                float cx2 = i == 0 ? -dp(6) : dp(10);
                canvas.drawLine(cx2 - dp(6), y + dp(6), cx2, y, chevronPaint);
                canvas.drawLine(cx2 + dp(6), y + dp(6), cx2, y, chevronPaint);
            }

            canvas.restore();
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        return SimpleThemeDescription.createThemeDescriptions(() -> updateColors(true), Theme.key_windowBackgroundWhite,
                Theme.key_windowBackgroundWhiteBlueText4, Theme.key_chats_actionBackground, Theme.key_chats_actionPressedBackground,
                Theme.key_featuredStickers_buttonText, Theme.key_windowBackgroundWhiteBlackText);
    }

    private void updateColors(boolean fromTheme) {
        startMessagingButtonBackground.setColors(new int[]{getThemedColor(Theme.key_featuredStickers_addButton), getThemedColor(Theme.key_featuredStickers_addButton2)});
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        switchLanguageTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        startMessagingButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        startMessagingButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(24), Color.TRANSPARENT, Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        darkThemeDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_featuredStickers_addButton), PorterDuff.Mode.SRC_IN));
        bottomPages.invalidate();
        if (iconViews != null) {
            for (IntroIconView iconView : iconViews) {
                iconView.updateColors();
            }
        }
        if (fromTheme) {
            for (int i = 0; i < viewPager.getChildCount(); i++) {
                View ch = viewPager.getChildAt(i);
                TextView headerTextView = ch.findViewWithTag(pagerHeaderTag);
                headerTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                TextView messageTextView = ch.findViewWithTag(pagerMessageTag);
                messageTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            }
        }
    }

    @Override
    public boolean isLightStatusBar() {
        int color = Theme.getColor(Theme.key_windowBackgroundWhite, null, true);
        return ColorUtils.calculateLuminance(color) > 0.7f;
    }
}
