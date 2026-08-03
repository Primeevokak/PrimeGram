package org.telegram.ui.Cells;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.DrawerLayoutContainer;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.Reactions.HwEmojis;
import org.telegram.ui.Components.SnowflakesEffect;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.ThemeActivity;

/** PrimeGram: restored from Telegram-Android pre-redesign history (profile header row atop the
 *  drawer - avatar, name, phone, day/night toggle), adapted from inugram's updated Kotlin variant
 *  (icon-color crossfade, HwEmojis hook) which itself builds on the same stock source. */
public class DrawerProfileCell extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private final BackupImageView avatarImageView;
    private final SimpleTextView nameTextView;
    private final TextView phoneTextView;
    private final ImageView shadowView;
    private final ImageView arrowView;
    private final RLottieImageView darkThemeView;

    private final Rect srcRect = new Rect();
    private final Rect destRect = new Rect();
    private final Paint paint = new Paint();
    private Integer currentColor;
    private Integer currentIconColor;
    private ValueAnimator iconColorAnimator;
    private Integer pendingCrossfadeFrom;
    private SnowflakesEffect snowflakesEffect;
    private boolean accountsShown;

    public final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable status;

    private int lastAccount = -1;
    private TLRPC.User lastUser;
    private Drawable premiumStar;

    private static RLottieDrawable sunDrawable;

    private final DrawerLayoutContainer drawerLayoutContainer;

    public DrawerProfileCell(Context context, DrawerLayoutContainer drawerLayoutContainer) {
        super(context);
        this.drawerLayoutContainer = drawerLayoutContainer;

        shadowView = new ImageView(context);
        shadowView.setVisibility(INVISIBLE);
        shadowView.setScaleType(ImageView.ScaleType.FIT_XY);
        shadowView.setImageResource(R.drawable.compose_panel_shadow);
        addView(shadowView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 70, Gravity.LEFT | Gravity.BOTTOM));

        avatarImageView = new BackupImageView(context);
        avatarImageView.getImageReceiver().setRoundRadius(AndroidUtilities.dp(32));
        addView(avatarImageView, LayoutHelper.createFrame(64, 64, Gravity.LEFT | Gravity.BOTTOM, 16, 0, 0, 67));

        nameTextView = new SimpleTextView(context) {
            @Override
            public void invalidate() {
                if (HwEmojis.grab(this)) return;
                super.invalidate();
            }

            @Override
            public void invalidate(int l, int t, int r, int b) {
                if (HwEmojis.grab(this)) return;
                super.invalidate(l, t, r, b);
            }

            @Override
            public void invalidateDrawable(Drawable who) {
                if (HwEmojis.grab(this)) return;
                super.invalidateDrawable(who);
            }

            @Override
            public void invalidate(Rect dirty) {
                if (HwEmojis.grab(this)) return;
                super.invalidate(dirty);
            }
        };
        nameTextView.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));
        nameTextView.setTextSize(15);
        nameTextView.setTypeface(AndroidUtilities.bold());
        nameTextView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        nameTextView.setEllipsizeByGradient(true);
        nameTextView.setRightDrawableOutside(true);
        addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, 16, 0, 52, 28));

        phoneTextView = new TextView(context);
        phoneTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        phoneTextView.setLines(1);
        phoneTextView.setMaxLines(1);
        phoneTextView.setSingleLine(true);
        phoneTextView.setGravity(Gravity.LEFT);
        addView(phoneTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, 16, 0, 52, 9));

        arrowView = new ImageView(context);
        arrowView.setScaleType(ImageView.ScaleType.CENTER);
        arrowView.setImageResource(R.drawable.msg_expand);
        arrowView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_menuName), PorterDuff.Mode.SRC_IN));
        addView(arrowView, LayoutHelper.createFrame(59, 59, Gravity.RIGHT | Gravity.BOTTOM));
        setArrowState(false);

        boolean playDrawable = sunDrawable == null;
        if (playDrawable) {
            sunDrawable = new RLottieDrawable(R.raw.sun, "" + R.raw.sun, AndroidUtilities.dp(28), AndroidUtilities.dp(28), true, null);
            sunDrawable.setPlayInDirectionOfCustomEndFrame(true);
            if (Theme.isCurrentThemeDay()) {
                sunDrawable.setCustomEndFrame(0);
                sunDrawable.setCurrentFrame(0);
            } else {
                sunDrawable.setCurrentFrame(35);
                sunDrawable.setCustomEndFrame(36);
            }
        }

        darkThemeView = new RLottieImageView(context) {
            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                info.setText(Theme.isCurrentThemeDark()
                    ? LocaleController.getString(R.string.AccDescrSwitchToDayTheme)
                    : LocaleController.getString(R.string.AccDescrSwitchToNightTheme));
            }
        };
        darkThemeView.setFocusable(true);
        darkThemeView.setBackground(Theme.createCircleSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), 0, 0));
        sunDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_menuName), PorterDuff.Mode.SRC_IN));
        darkThemeView.setScaleType(ImageView.ScaleType.CENTER);
        darkThemeView.setAnimation(sunDrawable);
        darkThemeView.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 1, AndroidUtilities.dp(17)));
        Theme.setRippleDrawableForceSoftware((RippleDrawable) darkThemeView.getBackground());
        if (!playDrawable && sunDrawable.getCustomEndFrame() != sunDrawable.getCurrentFrame()) {
            darkThemeView.playAnimation();
        }

        darkThemeView.setOnClickListener(v -> {
            if (DialogsActivity.switchingTheme) return;
            DialogsActivity.switchingTheme = true;
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE);
            String dayThemeName = preferences.getString("lastDayTheme", "Blue");
            if (Theme.getTheme(dayThemeName) == null || Theme.getTheme(dayThemeName).isDark()) {
                dayThemeName = "Blue";
            }
            String nightThemeName = preferences.getString("lastDarkTheme", "Dark Blue");
            if (Theme.getTheme(nightThemeName) == null || !Theme.getTheme(nightThemeName).isDark()) {
                nightThemeName = "Dark Blue";
            }
            Theme.ThemeInfo themeInfo = Theme.getActiveTheme();
            if (dayThemeName.equals(nightThemeName)) {
                if (themeInfo.isDark() || dayThemeName.equals("Dark Blue") || dayThemeName.equals("Night")) {
                    dayThemeName = "Blue";
                } else {
                    nightThemeName = "Dark Blue";
                }
            }

            boolean toDark = dayThemeName.equals(themeInfo.getKey());
            if (toDark) {
                themeInfo = Theme.getTheme(nightThemeName);
                sunDrawable.setCustomEndFrame(36);
            } else {
                themeInfo = Theme.getTheme(dayThemeName);
                sunDrawable.setCustomEndFrame(0);
            }
            final Theme.ThemeInfo finalThemeInfo = themeInfo;
            final boolean finalToDark = toDark;
            if (!toDark) {
                Integer fromColor = currentIconColor;
                pendingCrossfadeFrom = fromColor;
                if (fromColor != null) {
                    sunDrawable.setColorFilter(new PorterDuffColorFilter(fromColor, PorterDuff.Mode.SRC_IN));
                }
                darkThemeView.setVisibility(INVISIBLE);
                Choreographer.getInstance().postFrameCallback(f1 ->
                    Choreographer.getInstance().postFrameCallback(f2 -> {
                        darkThemeView.playAnimation();
                        switchTheme(finalThemeInfo, finalToDark);
                    }));
            } else {
                darkThemeView.playAnimation();
                switchTheme(finalThemeInfo, finalToDark);
            }

            Runnable openSettings = () -> {
                if (this.drawerLayoutContainer.inu_drawer != null) {
                    this.drawerLayoutContainer.inu_drawer.closeDrawer(false);
                }
                this.drawerLayoutContainer.parentActionBarLayout.presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_NIGHT));
            };
            BaseFragment lastFragment = this.drawerLayoutContainer.parentActionBarLayout != null ? this.drawerLayoutContainer.parentActionBarLayout.getLastFragment() : null;
            BulletinFactory bf = lastFragment != null ? BulletinFactory.of(lastFragment) : null;
            Theme.turnOffAutoNight(bf, openSettings);
        });
        darkThemeView.setOnLongClickListener(v -> {
            if (this.drawerLayoutContainer.inu_drawer != null) {
                this.drawerLayoutContainer.inu_drawer.closeDrawer(false);
            }
            this.drawerLayoutContainer.parentActionBarLayout.presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
            return true;
        });
        addView(darkThemeView, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 6, 90));

        if (Theme.getEventType() == 0) {
            snowflakesEffect = new SnowflakesEffect(0);
            snowflakesEffect.setColorKey(Theme.key_chats_menuName);
        }

        status = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(this, AndroidUtilities.dp(20));
        nameTextView.setRightDrawable(status);
    }

    private void startIconColorCrossfade(int from, int to) {
        if (iconColorAnimator != null) iconColorAnimator.cancel();
        sunDrawable.setColorFilter(new PorterDuffColorFilter(from, PorterDuff.Mode.SRC_IN));
        iconColorAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), from, to);
        iconColorAnimator.setDuration(400);
        iconColorAnimator.addUpdateListener(a -> {
            int c = (int) a.getAnimatedValue();
            sunDrawable.setColorFilter(new PorterDuffColorFilter(c, PorterDuff.Mode.SRC_IN));
        });
        final ValueAnimator startedAnimator = iconColorAnimator;
        iconColorAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                if (iconColorAnimator == startedAnimator) iconColorAnimator = null;
                sunDrawable.setColorFilter(new PorterDuffColorFilter(to, PorterDuff.Mode.SRC_IN));
            }
        });
        iconColorAnimator.start();
    }

    private void switchTheme(Theme.ThemeInfo themeInfo, boolean toDark) {
        int[] pos = new int[2];
        darkThemeView.getLocationInWindow(pos);
        pos[0] += darkThemeView.getMeasuredWidth() / 2;
        pos[1] += darkThemeView.getMeasuredHeight() / 2;
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, themeInfo, false, pos, -1, toDark, darkThemeView);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        status.attach();
        updateColors();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.needSetDayNightTheme);
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            NotificationCenter.getInstance(i).addObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        status.detach();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.needSetDayNightTheme);
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            NotificationCenter.getInstance(i).removeObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
        }
        if (lastAccount >= 0) {
            NotificationCenter.getInstance(lastAccount).removeObserver(this, NotificationCenter.userEmojiStatusUpdated);
            NotificationCenter.getInstance(lastAccount).removeObserver(this, NotificationCenter.updateInterfaces);
            lastAccount = -1;
        }
        if (nameTextView.getRightDrawable() instanceof AnimatedEmojiDrawable.WrapSizeDrawable) {
            Drawable inner = ((AnimatedEmojiDrawable.WrapSizeDrawable) nameTextView.getRightDrawable()).getDrawable();
            if (inner instanceof AnimatedEmojiDrawable) {
                ((AnimatedEmojiDrawable) inner).removeView(nameTextView);
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        try {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(148) + AndroidUtilities.statusBarHeight, MeasureSpec.EXACTLY)
            );
        } catch (Exception e) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(148));
            FileLog.e(e);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Drawable backgroundDrawable = Theme.getCachedWallpaper();
        int backgroundKey = applyBackground(false);
        boolean useImageBackground = backgroundKey != Theme.key_chats_menuTopBackground &&
            Theme.isCustomTheme() && !Theme.isPatternWallpaper() &&
            backgroundDrawable != null &&
            !(backgroundDrawable instanceof ColorDrawable) &&
            !(backgroundDrawable instanceof GradientDrawable);
        boolean drawCatsShadow = false;
        int shadowColor;
        if (!useImageBackground && Theme.hasThemeKey(Theme.key_chats_menuTopShadowCats)) {
            shadowColor = Theme.getColor(Theme.key_chats_menuTopShadowCats);
            drawCatsShadow = true;
        } else {
            shadowColor = Theme.hasThemeKey(Theme.key_chats_menuTopShadow)
                ? Theme.getColor(Theme.key_chats_menuTopShadow)
                : (Theme.getServiceMessageColor() | 0xff000000);
        }
        if (currentColor == null || currentColor != shadowColor) {
            currentColor = shadowColor;
            shadowView.getDrawable().setColorFilter(new PorterDuffColorFilter(shadowColor, PorterDuff.Mode.MULTIPLY));
        }
        int iconColor = pickIconColor(backgroundKey, useImageBackground);
        if (currentIconColor == null || currentIconColor != iconColor) {
            Integer prev = currentIconColor;
            currentIconColor = iconColor;
            Integer crossfadeFrom = pendingCrossfadeFrom != null ? pendingCrossfadeFrom : prev;
            if (pendingCrossfadeFrom != null && crossfadeFrom != null && crossfadeFrom != iconColor) {
                pendingCrossfadeFrom = null;
                startIconColorCrossfade(crossfadeFrom, iconColor);
            } else if (iconColorAnimator == null && pendingCrossfadeFrom == null) {
                sunDrawable.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN));
            }
            arrowView.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN));
        }
        nameTextView.setTextColor(Theme.hasThemeKey(Theme.key_chats_menuName) ? Theme.getColor(Theme.key_chats_menuName)
            : (Theme.isCurrentThemeDark() ? Theme.getColor(Theme.key_windowBackgroundWhiteBlackText) : Theme.getColor(Theme.key_chats_menuName)));
        if (useImageBackground) {
            phoneTextView.setTextColor(Theme.hasThemeKey(Theme.key_chats_menuPhone) ? Theme.getColor(Theme.key_chats_menuPhone)
                : (Theme.isCurrentThemeDark() ? Theme.getColor(Theme.key_windowBackgroundWhiteGrayText) : Theme.getColor(Theme.key_chats_menuPhone)));
            if (shadowView.getVisibility() != VISIBLE) shadowView.setVisibility(VISIBLE);
            if (backgroundDrawable instanceof ColorDrawable || backgroundDrawable instanceof GradientDrawable) {
                backgroundDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
                backgroundDrawable.draw(canvas);
            } else if (backgroundDrawable instanceof BitmapDrawable) {
                Bitmap bitmap = ((BitmapDrawable) backgroundDrawable).getBitmap();
                float scaleX = (float) getMeasuredWidth() / (float) bitmap.getWidth();
                float scaleY = (float) getMeasuredHeight() / (float) bitmap.getHeight();
                float scale = Math.max(scaleX, scaleY);
                int width = (int) (getMeasuredWidth() / scale);
                int height = (int) (getMeasuredHeight() / scale);
                int x = (bitmap.getWidth() - width) / 2;
                int y = (bitmap.getHeight() - height) / 2;
                srcRect.set(x, y, x + width, y + height);
                destRect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                try {
                    canvas.drawBitmap(bitmap, srcRect, destRect, paint);
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
        } else {
            int visibility = drawCatsShadow ? VISIBLE : INVISIBLE;
            if (shadowView.getVisibility() != visibility) shadowView.setVisibility(visibility);
            phoneTextView.setTextColor(Theme.hasThemeKey(Theme.key_chats_menuPhoneCats) ? Theme.getColor(Theme.key_chats_menuPhoneCats)
                : (Theme.isCurrentThemeDark() ? Theme.getColor(Theme.key_windowBackgroundWhiteGrayText) : Theme.getColor(Theme.key_chats_menuPhoneCats)));
            super.onDraw(canvas);
        }

        if (snowflakesEffect != null) snowflakesEffect.onDraw(this, canvas);
    }

    public boolean isAccountsShown() {
        return accountsShown;
    }

    public void setAccountsShown(boolean value, boolean animated) {
        if (accountsShown == value) return;
        accountsShown = value;
        setArrowState(animated);
    }

    public void setUser(TLRPC.User user, boolean accounts) {
        int account = UserConfig.selectedAccount;
        if (account != lastAccount) {
            if (lastAccount >= 0) {
                NotificationCenter.getInstance(lastAccount).removeObserver(this, NotificationCenter.userEmojiStatusUpdated);
                NotificationCenter.getInstance(lastAccount).removeObserver(this, NotificationCenter.updateInterfaces);
            }
            lastAccount = account;
            NotificationCenter.getInstance(lastAccount).addObserver(this, NotificationCenter.userEmojiStatusUpdated);
            NotificationCenter.getInstance(lastAccount).addObserver(this, NotificationCenter.updateInterfaces);
        }
        lastUser = user;
        if (user == null) return;
        accountsShown = accounts;
        setArrowState(false);
        CharSequence text = UserObject.getUserName(user);
        try {
            text = Emoji.replaceEmoji(text, nameTextView.getPaint().getFontMetricsInt(), false);
        } catch (Exception ignore) {
        }

        nameTextView.setText(text);
        Long emojiStatusId = UserObject.getEmojiStatusDocumentId(user);
        if (emojiStatusId != null) {
            boolean isCollectible = user.emoji_status instanceof TLRPC.TL_emojiStatusCollectible;
            nameTextView.setDrawablePadding(AndroidUtilities.dp(4));
            status.set(emojiStatusId, true);
            status.setParticles(isCollectible, true);
        } else if (user.premium) {
            nameTextView.setDrawablePadding(AndroidUtilities.dp(4));
            if (premiumStar == null) {
                premiumStar = getResources().getDrawable(R.drawable.msg_premium_liststar).mutate();
            }
            premiumStar.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_menuPhoneCats), PorterDuff.Mode.MULTIPLY));
            status.set(premiumStar, true);
            status.setParticles(false, true);
        } else {
            status.set((Drawable) null, true);
            status.setParticles(false, true);
        }
        status.setColor(Theme.getColor(Theme.isCurrentThemeDark() ? Theme.key_chats_verifiedBackground : Theme.key_chats_menuPhoneCats));
        phoneTextView.setText(org.telegram.messenger.PrimeGramPrivacy.maskPhoneForDisplay(PhoneFormat.getInstance().format("+" + user.phone), true));
        AvatarDrawable avatarDrawable = new AvatarDrawable(user);
        avatarDrawable.setColor(Theme.getColor(Theme.key_avatar_backgroundInProfileBlue));
        avatarImageView.setForUserOrChat(user, avatarDrawable);
        applyBackground(true);
    }

    private int pickIconColor(int backgroundKey, boolean useImageBackground) {
        int nameColor = Theme.getColor(Theme.key_chats_menuName);
        if (useImageBackground) return nameColor;
        int bgColor = Theme.getColor(backgroundKey);
        boolean bgLight = ColorUtils.calculateLuminance(bgColor) > 0.5;
        boolean nameLight = ColorUtils.calculateLuminance(nameColor) > 0.5;
        return (bgLight && nameLight) ? Theme.getColor(Theme.key_chats_menuItemIcon) : nameColor;
    }

    public int applyBackground(boolean force) {
        Integer currentTag = getTag() instanceof Integer ? (Integer) getTag() : null;
        int backgroundKey = (Theme.hasThemeKey(Theme.key_chats_menuTopBackground) && Theme.getColor(Theme.key_chats_menuTopBackground) != 0)
            ? Theme.key_chats_menuTopBackground
            : Theme.key_chats_menuTopBackgroundCats;
        if (force || currentTag == null || backgroundKey != currentTag) {
            // Most themes - including the built-in dark ones - define their own value for this
            // key, and that value is trusted as-is. Only a theme that never defined it at all
            // falls through to getDefaultColor(), which is a compiled-in light-blue constant with
            // no dark variant - that's the one case worth overriding, not "dark theme in general".
            int color = Theme.hasThemeKey(backgroundKey)
                ? Theme.getColor(backgroundKey)
                : (Theme.isCurrentThemeDark() ? Theme.getColor(Theme.key_windowBackgroundWhite) : Theme.getColor(backgroundKey));
            setBackgroundColor(color);
            setTag(backgroundKey);
        }
        return backgroundKey;
    }

    public void updateColors() {
        if (snowflakesEffect != null) snowflakesEffect.updateColors();
        status.setColor(Theme.getColor(Theme.isCurrentThemeDark() ? Theme.key_chats_verifiedBackground : Theme.key_chats_menuPhoneCats));
    }

    private void setArrowState(boolean animated) {
        float rotation = accountsShown ? 180.0f : 0.0f;
        if (animated) {
            arrowView.animate().rotation(rotation).setDuration(220).setInterpolator(CubicBezierInterpolator.EASE_OUT).start();
        } else {
            arrowView.animate().cancel();
            arrowView.setRotation(rotation);
        }
        arrowView.setContentDescription(accountsShown
            ? LocaleController.getString(R.string.AccDescrHideAccounts)
            : LocaleController.getString(R.string.AccDescrShowAccounts));
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded) {
            nameTextView.invalidate();
        } else if (id == NotificationCenter.needSetDayNightTheme) {
            currentColor = null;
            currentIconColor = null;
            applyBackground(true);
            updateColors();
            invalidate();
        } else if (id == NotificationCenter.userEmojiStatusUpdated) {
            setUser((TLRPC.User) args[0], accountsShown);
        } else if (id == NotificationCenter.currentUserPremiumStatusChanged) {
            setUser(UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser(), accountsShown);
        } else if (id == NotificationCenter.updateInterfaces) {
            int flags = (int) args[0];
            if ((flags & MessagesController.UPDATE_MASK_NAME) != 0 ||
                (flags & MessagesController.UPDATE_MASK_AVATAR) != 0 ||
                (flags & MessagesController.UPDATE_MASK_STATUS) != 0 ||
                (flags & MessagesController.UPDATE_MASK_PHONE) != 0 ||
                (flags & MessagesController.UPDATE_MASK_EMOJI_STATUS) != 0) {
                setUser(UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser(), accountsShown);
            }
        }
    }

    public void updateSunDrawable(boolean toDark) {
        if (sunDrawable != null) sunDrawable.setCustomEndFrame(toDark ? 36 : 0);
        darkThemeView.playAnimation();
    }
}
