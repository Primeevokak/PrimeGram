package com.exteragram.messenger;

import android.content.SharedPreferences;

import org.telegram.messenger.MessagesController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

/**
 * PrimeGram: compatibility shim for {@code com.exteragram.messenger.ExteraConfig} - the central
 * static config facade real exteraGram plugins read constantly for feature-parity checks
 * ("if hideStories: ..."). The real class has around 150 properties across a Kotlin
 * {@code object}; PrimeGram has no equivalent for almost all of them, since they configure
 * exteraGram's own UI features rather than anything this fork does the same way.
 *
 * <p>Every property here is backed by one shared preferences file, defaulting to {@code false}
 * for anything PrimeGram doesn't actually implement - a plugin reading one of these is asking
 * "is this exteraGram feature on", and the honest answer for a feature that doesn't exist here is
 * "no", not a crash. The handful of properties wired to something real (see the block below the
 * generated ones) reflect PrimeGram's actual state instead.
 */
public final class ExteraConfig {

    private static final String PREF_NAME = "extera_compat";

    private ExteraConfig() {
    }

    private static SharedPreferences prefs() {
        return MessagesController.getGlobalMainSettings();
    }

    private static boolean b(String key, boolean def) {
        try {
            return prefs().getBoolean(PREF_NAME + "_" + key, def);
        } catch (Throwable t) {
            return def;
        }
    }

    private static void s(String key, boolean v) {
        try {
            prefs().edit().putBoolean(PREF_NAME + "_" + key, v).apply();
        } catch (Throwable ignore) {
        }
    }

    private static float f(String key, float def) {
        try {
            return prefs().getFloat(PREF_NAME + "_" + key, def);
        } catch (Throwable t) {
            return def;
        }
    }

    private static void sf(String key, float v) {
        try {
            prefs().edit().putFloat(PREF_NAME + "_" + key, v).apply();
        } catch (Throwable ignore) {
        }
    }

    private static int i(String key, int def) {
        try {
            return prefs().getInt(PREF_NAME + "_" + key, def);
        } catch (Throwable t) {
            return def;
        }
    }

    private static void si(String key, int v) {
        try {
            prefs().edit().putInt(PREF_NAME + "_" + key, v).apply();
        } catch (Throwable ignore) {
        }
    }

    private static String str(String key, String def) {
        try {
            return prefs().getString(PREF_NAME + "_" + key, def);
        } catch (Throwable t) {
            return def;
        }
    }

    private static void sstr(String key, String v) {
        try {
            prefs().edit().putString(PREF_NAME + "_" + key, v).apply();
        } catch (Throwable ignore) {
        }
    }

    // region plugin-relevant toggles (real defaults, since plugin code branches on these)

    public static boolean getPluginsEngine() {
        return true;
    }

    public static void setPluginsEngine(boolean v) {
    }

    public static boolean getPluginsDevMode() {
        return b("pluginsDevMode", false);
    }

    public static void setPluginsDevMode(boolean v) {
        s("pluginsDevMode", v);
    }

    public static boolean getPluginsSafeMode() {
        return false;
    }

    public static void setPluginsSafeMode(boolean v) {
    }

    public static boolean getPluginsCompactView() {
        return b("pluginsCompactView", false);
    }

    public static void setPluginsCompactView(boolean v) {
        s("pluginsCompactView", v);
    }

    public static boolean getPluginsPySdkAutoUpdate() {
        return b("pluginsPySdkAutoUpdate", true);
    }

    public static void setPluginsPySdkAutoUpdate(boolean v) {
        s("pluginsPySdkAutoUpdate", v);
    }

    public static boolean getPluginsPySdkBetaVersions() {
        return b("pluginsPySdkBetaVersions", false);
    }

    public static void setPluginsPySdkBetaVersions(boolean v) {
        s("pluginsPySdkBetaVersions", v);
    }

    public static boolean getPluginsDisableArtOpts() {
        return b("pluginsDisableArtOpts", false);
    }

    public static void setPluginsDisableArtOpts(boolean v) {
        s("pluginsDisableArtOpts", v);
    }

    public static Set<String> getPinnedPlugins() {
        return new java.util.HashSet<>();
    }

    public static void setPinnedPlugins(Set<String> v) {
    }

    public static SharedPreferences getPreferences() {
        return prefs();
    }

    public static SharedPreferences.Editor getEditor() {
        return prefs().edit();
    }

    public static boolean getLogging() {
        return org.telegram.messenger.BuildVars.LOGS_ENABLED;
    }

    public static void setLogging(boolean v) {
    }

    public static void toggleLogging() {
    }

    // endregion

    // region generic UI/feature toggles - PrimeGram has no equivalent, default false

    public static boolean getAddCommaAfterMention() { return b("addCommaAfterMention", false); }
    public static void setAddCommaAfterMention(boolean v) { s("addCommaAfterMention", v); }
    public static boolean getAlwaysSendInHD() { return b("alwaysSendInHD", false); }
    public static void setAlwaysSendInHD(boolean v) { s("alwaysSendInHD", v); }
    public static boolean getArchiveOnPull() { return b("archiveOnPull", false); }
    public static void setArchiveOnPull(boolean v) { s("archiveOnPull", v); }
    public static boolean getCameraMirrorMode() { return b("cameraMirrorMode", false); }
    public static void setCameraMirrorMode(boolean v) { s("cameraMirrorMode", v); }
    public static boolean getCameraStabilization() { return b("cameraStabilization", false); }
    public static void setCameraStabilization(boolean v) { s("cameraStabilization", v); }
    public static boolean getCenterTitle() { return b("centerTitle", false); }
    public static void setCenterTitle(boolean v) { s("centerTitle", v); }
    public static boolean getCustomThemes() { return b("customThemes", false); }
    public static void setCustomThemes(boolean v) { s("customThemes", v); }
    public static boolean getDisableGreetingSticker() { return b("disableGreetingSticker", false); }
    public static void setDisableGreetingSticker(boolean v) { s("disableGreetingSticker", v); }
    public static boolean getDisableNumberRounding() { return b("disableNumberRounding", false); }
    public static void setDisableNumberRounding(boolean v) { s("disableNumberRounding", v); }
    public static boolean getDisableUnarchiveSwipe() { return b("disableUnarchiveSwipe", false); }
    public static void setDisableUnarchiveSwipe(boolean v) { s("disableUnarchiveSwipe", v); }
    public static boolean getDoNotUseProxyWithVpn() { return b("doNotUseProxyWithVpn", false); }
    public static void setDoNotUseProxyWithVpn(boolean v) { s("doNotUseProxyWithVpn", v); }
    public static boolean getEnableAdBlock() { return b("enableAdBlock", false); }
    public static void setEnableAdBlock(boolean v) { s("enableAdBlock", v); }
    public static boolean getExtendedFramesPerSecond() { return b("extendedFramesPerSecond", false); }
    public static void setExtendedFramesPerSecond(boolean v) { s("extendedFramesPerSecond", v); }
    public static boolean getFilterZalgo() { return b("filterZalgo", false); }
    public static void setFilterZalgo(boolean v) { s("filterZalgo", v); }
    public static boolean getForceBlur() { return b("forceBlur", false); }
    public static void setForceBlur(boolean v) { s("forceBlur", v); }
    public static boolean getForceSnow() { return b("forceSnow", false); }
    public static void setForceSnow(boolean v) { s("forceSnow", v); }
    public static boolean getFormatTimeWithSeconds() { return b("formatTimeWithSeconds", false); }
    public static void setFormatTimeWithSeconds(boolean v) { s("formatTimeWithSeconds", v); }
    public static boolean getGlassMessageMenu() { return b("glassMessageMenu", false); }
    public static void setGlassMessageMenu(boolean v) { s("glassMessageMenu", v); }
    public static boolean getGooeyAvatarAnimation() { return b("gooeyAvatarAnimation", false); }
    public static void setGooeyAvatarAnimation(boolean v) { s("gooeyAvatarAnimation", v); }
    public static boolean getGroupMessageMenu() { return b("groupMessageMenu", false); }
    public static void setGroupMessageMenu(boolean v) { s("groupMessageMenu", v); }
    public static boolean getHideActionBarStatus() { return b("hideActionBarStatus", false); }
    public static void setHideActionBarStatus(boolean v) { s("hideActionBarStatus", v); }
    public static boolean getHideAllChats() { return b("hideAllChats", false); }
    public static void setHideAllChats(boolean v) { s("hideAllChats", v); }
    public static boolean getHideArchiveFolder() { return b("hideArchiveFolder", false); }
    public static void setHideArchiveFolder(boolean v) { s("hideArchiveFolder", v); }
    public static boolean getHideCameraTile() { return b("hideCameraTile", false); }
    public static void setHideCameraTile(boolean v) { s("hideCameraTile", v); }
    public static boolean getHideDialogsSearchBar() { return b("hideDialogsSearchBar", false); }
    public static void setHideDialogsSearchBar(boolean v) { s("hideDialogsSearchBar", v); }
    public static boolean getHideFloatingButton() { return b("hideFloatingButton", false); }
    public static void setHideFloatingButton(boolean v) { s("hideFloatingButton", v); }
    public static boolean getHideKeyboardOnScroll() { return b("hideKeyboardOnScroll", false); }
    public static void setHideKeyboardOnScroll(boolean v) { s("hideKeyboardOnScroll", v); }
    public static boolean getHidePhoneNumber() { return b("hidePhoneNumber", false); }
    public static void setHidePhoneNumber(boolean v) { s("hidePhoneNumber", v); }
    public static boolean getHideReactionsInChannels() { return b("hideReactionsInChannels", false); }
    public static void setHideReactionsInChannels(boolean v) { s("hideReactionsInChannels", v); }
    public static boolean getHideReactionsInGroups() { return b("hideReactionsInGroups", false); }
    public static void setHideReactionsInGroups(boolean v) { s("hideReactionsInGroups", v); }
    public static boolean getHideReactionsInPrivateChats() { return b("hideReactionsInPrivateChats", false); }
    public static void setHideReactionsInPrivateChats(boolean v) { s("hideReactionsInPrivateChats", v); }
    public static boolean getHideSendAsPeer() { return b("hideSendAsPeer", false); }
    public static void setHideSendAsPeer(boolean v) { s("hideSendAsPeer", v); }
    public static boolean getHideShareButton() { return b("hideShareButton", false); }
    public static void setHideShareButton(boolean v) { s("hideShareButton", v); }
    public static boolean getHideStickerTime() { return b("hideStickerTime", false); }
    public static void setHideStickerTime(boolean v) { s("hideStickerTime", v); }
    public static boolean getHideStories() { return b("hideStories", false); }
    public static void setHideStories(boolean v) { s("hideStories", v); }
    public static boolean getImmersiveDrawerAnimation() { return b("immersiveDrawerAnimation", false); }
    public static void setImmersiveDrawerAnimation(boolean v) { s("immersiveDrawerAnimation", v); }
    public static boolean getInAppVibration() { return b("inAppVibration", false); }
    public static void setInAppVibration(boolean v) { s("inAppVibration", v); }
    public static boolean getNavigationDrawer() { return org.telegram.messenger.DrawerHelper.isEnabled(); }
    public static void setNavigationDrawer(boolean v) { }
    public static boolean getNewChatHeaderStyle() { return b("newChatHeaderStyle", false); }
    public static void setNewChatHeaderStyle(boolean v) { s("newChatHeaderStyle", v); }
    public static boolean getNewLoadingStyle() { return b("newLoadingStyle", false); }
    public static void setNewLoadingStyle(boolean v) { s("newLoadingStyle", v); }
    public static boolean getNewNavigationBarStyle() { return b("newNavigationBarStyle", false); }
    public static void setNewNavigationBarStyle(boolean v) { s("newNavigationBarStyle", v); }
    public static boolean getNewSliderStyle() { return b("newSliderStyle", false); }
    public static void setNewSliderStyle(boolean v) { s("newSliderStyle", v); }
    public static boolean getNewSwitchStyle() { return b("newSwitchStyle", false); }
    public static void setNewSwitchStyle(boolean v) { s("newSwitchStyle", v); }
    public static boolean getPauseOnMinimizeRound() { return b("pauseOnMinimizeRound", false); }
    public static void setPauseOnMinimizeRound(boolean v) { s("pauseOnMinimizeRound", v); }
    public static boolean getPauseOnMinimizeVideo() { return b("pauseOnMinimizeVideo", false); }
    public static void setPauseOnMinimizeVideo(boolean v) { s("pauseOnMinimizeVideo", v); }
    public static boolean getPauseOnMinimizeVoice() { return b("pauseOnMinimizeVoice", false); }
    public static void setPauseOnMinimizeVoice(boolean v) { s("pauseOnMinimizeVoice", v); }
    public static boolean getPostprocessingWithAi() { return b("postprocessingWithAi", false); }
    public static void setPostprocessingWithAi(boolean v) { s("postprocessingWithAi", v); }
    public static boolean getPreferOriginalQuality() { return b("preferOriginalQuality", false); }
    public static void setPreferOriginalQuality(boolean v) { s("preferOriginalQuality", v); }
    public static boolean getQuickAdminShortcuts() { return b("quickAdminShortcuts", false); }
    public static void setQuickAdminShortcuts(boolean v) { s("quickAdminShortcuts", v); }
    public static boolean getQuickTransitionForChannels() { return b("quickTransitionForChannels", false); }
    public static void setQuickTransitionForChannels(boolean v) { s("quickTransitionForChannels", v); }
    public static boolean getQuickTransitionForTopics() { return b("quickTransitionForTopics", false); }
    public static void setQuickTransitionForTopics(boolean v) { s("quickTransitionForTopics", v); }
    public static boolean getRelativeLastSeen() { return b("relativeLastSeen", false); }
    public static void setRelativeLastSeen(boolean v) { s("relativeLastSeen", v); }
    public static boolean getRememberLastUsedCamera() { return b("rememberLastUsedCamera", false); }
    public static void setRememberLastUsedCamera(boolean v) { s("rememberLastUsedCamera", v); }
    public static boolean getRemoveMessageTail() { return b("removeMessageTail", false); }
    public static void setRemoveMessageTail(boolean v) { s("removeMessageTail", v); }
    public static boolean getReplaceEditedWithIcon() { return b("replaceEditedWithIcon", false); }
    public static void setReplaceEditedWithIcon(boolean v) { s("replaceEditedWithIcon", v); }
    public static boolean getReplyBackground() { return b("replyBackground", false); }
    public static void setReplyBackground(boolean v) { s("replyBackground", v); }
    public static boolean getReplyColors() { return b("replyColors", false); }
    public static void setReplyColors(boolean v) { s("replyColors", v); }
    public static boolean getReplyEmoji() { return b("replyEmoji", false); }
    public static void setReplyEmoji(boolean v) { s("replyEmoji", v); }
    public static boolean getSectionsSeparatedHeaders() { return b("sectionsSeparatedHeaders", false); }
    public static void setSectionsSeparatedHeaders(boolean v) { s("sectionsSeparatedHeaders", v); }
    public static boolean getSectionsSeparatedHeadersPreference() { return b("sectionsSeparatedHeadersPreference", false); }
    public static void setSectionsSeparatedHeadersPreference(boolean v) { s("sectionsSeparatedHeadersPreference", v); }
    public static boolean getSenderMiniAvatars() { return b("senderMiniAvatars", false); }
    public static void setSenderMiniAvatars(boolean v) { s("senderMiniAvatars", v); }
    public static boolean getShowClearButton() { return b("showClearButton", false); }
    public static void setShowClearButton(boolean v) { s("showClearButton", v); }
    public static boolean getShowCopyPhotoButton() { return b("showCopyPhotoButton", false); }
    public static void setShowCopyPhotoButton(boolean v) { s("showCopyPhotoButton", v); }
    public static boolean getShowDetailsButton() { return b("showDetailsButton", false); }
    public static void setShowDetailsButton(boolean v) { s("showDetailsButton", v); }
    public static boolean getShowFeedTab() { return b("showFeedTab", false); }
    public static void setShowFeedTab(boolean v) { s("showFeedTab", v); }
    public static boolean getShowGenerateButton() { return b("showGenerateButton", false); }
    public static void setShowGenerateButton(boolean v) { s("showGenerateButton", v); }
    public static boolean getShowHistoryButton() { return b("showHistoryButton", false); }
    public static void setShowHistoryButton(boolean v) { s("showHistoryButton", v); }
    public static boolean getShowOnlineStatus() { return b("showOnlineStatus", false); }
    public static void setShowOnlineStatus(boolean v) { s("showOnlineStatus", v); }
    public static boolean getShowRepeatMessageButton() { return b("showRepeatMessageButton", false); }
    public static void setShowRepeatMessageButton(boolean v) { s("showRepeatMessageButton", v); }
    public static boolean getShowReportButton() { return b("showReportButton", false); }
    public static void setShowReportButton(boolean v) { s("showReportButton", v); }
    public static boolean getShowResultsBeforeVoting() { return b("showResultsBeforeVoting", false); }
    public static void setShowResultsBeforeVoting(boolean v) { s("showResultsBeforeVoting", v); }
    public static boolean getShowSaveMessageButton() { return b("showSaveMessageButton", false); }
    public static void setShowSaveMessageButton(boolean v) { s("showSaveMessageButton", v); }
    public static boolean getSingleCornerRadius() { return b("singleCornerRadius", false); }
    public static void setSingleCornerRadius(boolean v) { s("singleCornerRadius", v); }
    public static boolean getSpringAnimations() { return b("springAnimations", false); }
    public static void setSpringAnimations(boolean v) { s("springAnimations", v); }
    public static boolean getSquareFab() { return b("squareFab", false); }
    public static void setSquareFab(boolean v) { s("squareFab", v); }
    public static boolean getStartWithWideAngleCamera() { return b("startWithWideAngleCamera", false); }
    public static void setStartWithWideAngleCamera(boolean v) { s("startWithWideAngleCamera", v); }
    public static boolean getStaticZoom() { return b("staticZoom", false); }
    public static void setStaticZoom(boolean v) { s("staticZoom", v); }
    public static boolean getSwipeToPip() { return b("swipeToPip", false); }
    public static void setSwipeToPip(boolean v) { s("swipeToPip", v); }
    public static boolean getTabCounter() { return b("tabCounter", false); }
    public static void setTabCounter(boolean v) { s("tabCounter", v); }
    public static boolean getUnlimitedRecentStickers() { return b("unlimitedRecentStickers", false); }
    public static void setUnlimitedRecentStickers(boolean v) { s("unlimitedRecentStickers", v); }
    public static boolean getUnmuteWithVolumeButtons() { return b("unmuteWithVolumeButtons", false); }
    public static void setUnmuteWithVolumeButtons(boolean v) { s("unmuteWithVolumeButtons", v); }
    public static boolean getUploadSpeedBoost() { return b("uploadSpeedBoost", false); }
    public static void setUploadSpeedBoost(boolean v) { s("uploadSpeedBoost", v); }
    public static boolean getDownloadSpeedBoost() { return b("downloadSpeedBoost", false); }
    public static void setDownloadSpeedBoost(boolean v) { s("downloadSpeedBoost", v); }
    public static boolean getUseGoogleAnalytics() { return false; }
    public static void setUseGoogleAnalytics(boolean v) { }
    public static boolean getUseGoogleCrashlytics() { return false; }
    public static void setUseGoogleCrashlytics(boolean v) { }
    public static boolean getUseSystemFonts() { return b("useSystemFonts", false); }
    public static void setUseSystemFonts(boolean v) { s("useSystemFonts", v); }
    public static boolean getUseSystemIconShape() { return b("useSystemIconShape", false); }
    public static void setUseSystemIconShape(boolean v) { s("useSystemIconShape", v); }
    public static boolean getUseYandexMaps() { return b("useYandexMaps", false); }
    public static void setUseYandexMaps(boolean v) { s("useYandexMaps", v); }
    public static boolean canUseYandexMaps() { return getUseYandexMaps(); }

    // endregion

    // region non-boolean properties

    public static float getAvatarCorners() { return f("avatarCorners", 0.16f); }
    public static void setAvatarCorners(float v) { sf("avatarCorners", v); }
    public static int getAvatarCorners(float px) { return (int) (px * getAvatarCorners()); }
    public static int getAvatarCorners(float px, boolean forum) { return getAvatarCorners(px); }
    public static int getAvatarCorners(float px, boolean forum, boolean withStory) { return getAvatarCorners(px); }
    public static int getAvatarCorners(float px, boolean forum, Object type) { return getAvatarCorners(px); }
    public static int getAvatarCorners(float px, boolean forum, Object type, boolean withStory) { return getAvatarCorners(px); }
    public static float getAvatarSquareness() { return f("avatarSquareness", 0f); }
    public static void setAvatarSquareness(float v) { sf("avatarSquareness", v); }
    public static int getOnlineDotOuterRadius() { return i("onlineDotOuterRadius", 0); }
    public static int getOnlineDotInnerRadius() { return i("onlineDotInnerRadius", 0); }
    public static float getOnlineDotOffset(float baseOffset, float outerRadius) { return baseOffset; }
    public static int getSectionRadiusDp() { return i("sectionRadiusDp", 12); }

    public static int getTranslationProvider() { return i("translationProvider", 0); }
    public static void setTranslationProvider(int v) { si("translationProvider", v); }
    public static String getTranslationFormality() { return str("translationFormality", "DEFAULT"); }
    public static void setTranslationFormality(String v) { sstr("translationFormality", v); }

    public static ArrayList<String> getDoNotMarkAsNew() { return new ArrayList<>(); }
    public static void setDoNotMarkAsNew(ArrayList<String> v) { }
    public static HashMap<String, Long> getNewFeaturesShowedAt() { return new HashMap<>(); }
    public static void setNewFeaturesShowedAt(HashMap<String, Long> v) { }

    public static String getIconPack() { return str("iconPack", "default"); }
    public static void setIconPack(String v) { sstr("iconPack", v); }
    public static String getEditingIconPackId() { return str("editingIconPackId", null); }
    public static void setEditingIconPackId(String v) { sstr("editingIconPackId", v); }
    public static ArrayList<String> getIconPacksLayout() { return new ArrayList<>(); }
    public static void setIconPacksLayout(ArrayList<String> v) { }
    public static ArrayList<String> getIconPacksHidden() { return new ArrayList<>(); }
    public static void setIconPacksHidden(ArrayList<String> v) { }

    public static String getDividerStyle() { return str("dividerStyle", "DEFAULT"); }
    public static void setDividerStyle(String v) { sstr("dividerStyle", v); }
    public static String getGlassOutlineStyle() { return str("glassOutlineStyle", "DEFAULT"); }
    public static void setGlassOutlineStyle(String v) { sstr("glassOutlineStyle", v); }
    public static String getCameraType() { return str("cameraType", "DEFAULT"); }
    public static void setCameraType(String v) { sstr("cameraType", v); }
    public static String getVideoMessagesCamera() { return str("videoMessagesCamera", "DEFAULT"); }
    public static void setVideoMessagesCamera(String v) { sstr("videoMessagesCamera", v); }
    public static String getTabIcons() { return str("tabIcons", "DEFAULT"); }
    public static void setTabIcons(String v) { sstr("tabIcons", v); }

    public static ArrayList<Integer> getMainMenuLayout() { return getDefaultMainMenuLayout(); }
    public static void setMainMenuLayout(ArrayList<Integer> v) { }
    public static ArrayList<Integer> getMainMenuHiddenItems() { return new ArrayList<>(); }
    public static void setMainMenuHiddenItems(ArrayList<Integer> v) { }
    public static ArrayList<Integer> getDefaultMainMenuLayout() { return new ArrayList<>(); }
    public static void saveMainMenuLayout() { }
    public static void saveIconPacksLayout() { }
    public static void ensureSettingsVisibility() { }

    public static long getUpdateScheduleTimestamp() { return 0L; }
    public static void setUpdateScheduleTimestamp(long v) { }
    public static long getSdkUpdateScheduleTimestamp() { return 0L; }
    public static void setSdkUpdateScheduleTimestamp(long v) { }

    public static String getCustomSavePath() { return str("customSavePath", null); }
    public static void setCustomSavePath(String v) { sstr("customSavePath", v); }
    public static String getTargetLang() { return str("targetLang", "en"); }
    public static void setTargetLang(String v) { sstr("targetLang", v); }
    public static String getRecognitionLanguage() { return str("recognitionLanguage", "en"); }
    public static void setRecognitionLanguage(String v) { sstr("recognitionLanguage", v); }

    public static float getFlashWarmth() { return f("flashWarmth", 0f); }
    public static void setFlashWarmth(float v) { sf("flashWarmth", v); }
    public static float getFlashIntensity() { return f("flashIntensity", 1f); }
    public static void setFlashIntensity(float v) { sf("flashIntensity", v); }
    public static float getStickerSize() { return f("stickerSize", 1f); }
    public static void setStickerSize(float v) { sf("stickerSize", v); }
    public static float getPredictiveBackIntensity() { return f("predictiveBackIntensity", 1f); }
    public static void setPredictiveBackIntensity(float v) { sf("predictiveBackIntensity", v); }

    public static int getBottomButton() { return i("bottomButton", 0); }
    public static void setBottomButton(int v) { si("bottomButton", v); }
    public static int getDoubleTapAction() { return i("doubleTapAction", 0); }
    public static void setDoubleTapAction(int v) { si("doubleTapAction", v); }
    public static int getDoubleTapActionOutOwner() { return i("doubleTapActionOutOwner", 0); }
    public static void setDoubleTapActionOutOwner(int v) { si("doubleTapActionOutOwner", v); }
    public static int getDoubleTapSeekDuration() { return i("doubleTapSeekDuration", 10); }
    public static void setDoubleTapSeekDuration(int v) { si("doubleTapSeekDuration", v); }
    public static int getDoubleTapSeekDurationMillis() { return getDoubleTapSeekDuration() * 1000; }
    public static int getEventType() { return i("eventType", 0); }
    public static void setEventType(int v) { si("eventType", v); }
    public static int getShowIdAndDc() { return i("showIdAndDc", 0); }
    public static void setShowIdAndDc(int v) { si("showIdAndDc", v); }
    public static int getStickerShape() { return i("stickerShape", 0); }
    public static void setStickerShape(int v) { si("stickerShape", v); }
    public static int getTabletMode() { return i("tabletMode", 0); }
    public static void setTabletMode(int v) { si("tabletMode", v); }
    public static int getTitleText() { return i("titleText", 0); }
    public static void setTitleText(int v) { si("titleText", v); }

    public static String getCurrentLangName() {
        return org.telegram.messenger.LocaleController.getInstance().getCurrentLocaleInfo() != null
                ? org.telegram.messenger.LocaleController.getInstance().getCurrentLocaleInfo().name
                : "English";
    }

    public static void init() { }
    public static void loadConfig() { }
    public static void reloadConfig() { }

    // endregion
}
