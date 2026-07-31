package org.telegram.messenger;

import android.content.SharedPreferences;

/**
 * PrimeGram: the small interface tweaks, in one place.
 *
 * <p>Every one of these is a single boolean or int that some drawing or formatting site reads.
 * They live together rather than scattered across feature classes for one practical reason:
 * they are read from draw and layout paths that run on every frame, so they must be cheap and
 * they must never throw. The cache below means a toggle costs a field read rather than a
 * SharedPreferences lookup, and {@link #reload()} is the single place that invalidates it.
 *
 * <p>Defaults always reproduce stock Telegram behaviour. Someone who never opens the settings
 * must not be able to tell this class exists.
 */
public class PrimeTweaks {

    // ---- dialog list ----
    public static final String HIDE_STORIES = "prime_hide_stories";
    public static final String HIDE_FLOATING_BUTTON = "prime_hide_fab";
    public static final String HIDE_DIALOGS_SEARCH_BAR = "prime_hide_dialogs_search";
    public static final String HIDE_ALL_CHATS = "prime_hide_all_chats";
    public static final String HIDE_ARCHIVE_FOLDER = "prime_hide_archive";
    public static final String ARCHIVE_ON_PULL = "prime_archive_on_pull";
    public static final String DISABLE_UNARCHIVE_SWIPE = "prime_disable_unarchive_swipe";
    public static final String TAB_COUNTER = "prime_tab_counter";

    // ---- formatting ----
    public static final String DISABLE_NUMBER_ROUNDING = "prime_no_number_rounding";
    public static final String TIME_WITH_SECONDS = "prime_time_with_seconds";
    public static final String RELATIVE_LAST_SEEN = "prime_relative_last_seen";

    // ---- profile ----
    public static final String SHOW_ID_AND_DC = "prime_show_id_dc";

    /** What a double tap on a message does: 0 reaction (stock), 1 reply, 2 nothing. */
    public static final String DOUBLE_TAP_ACTION = "prime_double_tap_action";
    public static final int DOUBLE_TAP_REACTION = 0;
    public static final int DOUBLE_TAP_REPLY = 1;
    public static final int DOUBLE_TAP_NOTHING = 2;

    // ---- appearance ----
    public static final String FORCE_SNOW = "prime_force_snow";
    public static final String SQUARE_FAB = "prime_square_fab";
    public static final String CENTER_TITLE = "prime_center_title";
    public static final String REMOVE_MESSAGE_TAIL = "prime_remove_message_tail";
    public static final String EDITED_AS_ICON = "prime_edited_as_icon";
    public static final String SENDER_MINI_AVATARS = "prime_sender_mini_avatars";
    /**
     * Avatar rounding, as a fraction of the radius that would draw a full circle:
     * {@link #AVATAR_CORNERS_MAX} is a circle, 0 a square, and every value between is a real
     * shape rather than one of a handful of presets.
     * <p>
     * The first version of this setting used a 0-50 scale under {@link #AVATAR_CORNERS_OLD}.
     * That is ten times coarser, so it is read once and multiplied up; the old key is left alone
     * in case someone downgrades.
     */
    public static final String AVATAR_CORNERS = "prime_avatar_corners_fine";
    private static final String AVATAR_CORNERS_OLD = "prime_avatar_corners";
    public static final int AVATAR_CORNERS_MAX = 500;
    public static final int AVATAR_CORNERS_DEFAULT = AVATAR_CORNERS_MAX;

    // ---- message menu ----
    public static final String MENU_SAVE_TO_SAVED = "prime_menu_save_to_saved";
    public static final String MENU_DETAILS = "prime_menu_details";
    public static final String ADMIN_SHORTCUTS = "prime_admin_shortcuts";

    // ---- media ----
    public static final String SAVE_ROUND_AND_VOICE = "prime_save_round_voice";
    public static final String SEND_UNCOMPRESSED = "prime_send_uncompressed";

    /** Shorter side, in pixels, that a downloaded video may not exceed. 0 means no ceiling. */
    public static final String MAX_VIDEO_HEIGHT = "prime_max_video_height";

    /** Round videos start on the rear camera instead of the front one. */
    public static final String ROUND_VIDEO_REAR = "prime_round_video_rear";

    /**
     * PrimeGram's own performance work: idle preloading of tabs, warming the tunnel's connection
     * pool, the deeper pool for media. On by default; a switch exists because every one of these
     * trades memory or background work for speed, and a device where that trade goes badly needs
     * a way out that is not "reinstall the old build".
     */
    public static final String OPTIMIZATIONS = "prime_optimizations";

    /** Drops the avatar, name, number and username block from Telegram's own settings screen. */
    public static final String HIDE_SETTINGS_HEADER = "prime_hide_settings_header";
    /** Shows your username in place of the logo on the chat list. */
    public static final String MAIN_TITLE_USERNAME = "prime_main_title_username";
    /** Leaves the emoji status out of the chat list title. */
    public static final String HIDE_EMOJI_STATUS = "prime_hide_emoji_status";

    /** Which service translates messages. See PrimeTranslator for the values. */
    public static final String TRANSLATE_PROVIDER = "prime_translate_provider";

    // ---- chats ----
    public static final String HIDE_REACTIONS_CHANNELS = "prime_hide_reactions_channels";
    public static final String HIDE_REACTIONS_GROUPS = "prime_hide_reactions_groups";
    public static final String HIDE_REACTIONS_PRIVATE = "prime_hide_reactions_private";
    public static final String HIDE_SEND_AS_PEER = "prime_hide_send_as";
    public static final String HIDE_SHARE_BUTTON = "prime_hide_share_button";
    public static final String HIDE_STICKER_TIME = "prime_hide_sticker_time";
    public static final String HIDE_KEYBOARD_ON_SCROLL = "prime_hide_keyboard_on_scroll";
    public static final String SHOW_RESULTS_BEFORE_VOTING = "prime_poll_peek";
    public static final String COMMA_AFTER_MENTION = "prime_comma_after_mention";
    public static final String STICKER_SIZE = "prime_sticker_size";
    public static final int STICKER_SIZE_DEFAULT = 14;

    private static SharedPreferences prefs;
    private static boolean loaded;

    // Cached hot values. Anything read from onDraw belongs here.
    private static boolean hideStories;
    private static boolean hideFloatingButton;
    private static boolean hideDialogsSearchBar;
    private static boolean hideAllChats;
    private static boolean hideArchiveFolder;
    private static boolean archiveOnPull;
    private static boolean disableUnarchiveSwipe;
    private static boolean squareFab;
    private static boolean removeMessageTail;
    private static boolean editedAsIcon;
    private static boolean hideStickerTime;
    private static boolean hideShareButton;
    private static boolean hideReactionsChannels;
    private static boolean hideReactionsGroups;
    private static boolean hideReactionsPrivate;
    private static boolean hideKeyboardOnScroll;
    private static boolean commaAfterMention;
    private static boolean hideSendAsPeer;
    private static boolean disableNumberRounding;
    private static boolean timeWithSeconds;
    private static boolean relativeLastSeen;
    private static boolean showPeerId;
    private static boolean forceSnow;
    private static boolean centerTitle;
    private static int doubleTapAction = DOUBLE_TAP_REACTION;
    private static int avatarCorners = AVATAR_CORNERS_DEFAULT;
    private static boolean menuSaveToSaved;
    private static boolean menuDetails;
    private static int stickerSize = STICKER_SIZE_DEFAULT;
    private static boolean senderMiniAvatars;
    private static boolean adminShortcuts;
    private static int translateProvider = PrimeTranslator.PROVIDER_TELEGRAM;
    private static boolean saveRoundAndVoice;
    private static boolean sendUncompressed;
    private static int maxVideoHeight;
    private static boolean roundVideoRearCamera;
    private static boolean optimizations = true;
    private static boolean hideSettingsHeader;
    private static boolean mainTitleUsername;
    private static boolean hideEmojiStatus;

    /**
     * The store is opened straight from the application context, deliberately not through
     * {@code MessagesController.getGlobalMainSettings()}. That helper is
     * {@code getInstance(0).mainPreferences}, so asking it for a value constructs the whole
     * MessagesController. These tweaks are read from LocaleController and from message cells,
     * both of which run while the app is still coming up - forcing that construction from there
     * is a startup deadlock waiting to happen. Same file name, so the values are the same ones.
     *
     * <p>Returns null before the application context exists; callers must cope.
     */
    private static SharedPreferences prefs() {
        if (prefs == null) {
            android.content.Context context = ApplicationLoader.applicationContext;
            if (context == null) {
                return null;
            }
            prefs = context.getSharedPreferences("mainconfig", android.content.Context.MODE_PRIVATE);
        }
        return prefs;
    }

    private static void ensureLoaded() {
        if (!loaded) {
            reload();
        }
    }

    /** Re-reads everything cached. Call after any of these settings changes. */
    public static void reload() {
        try {
            SharedPreferences p = prefs();
            if (p == null) {
                // Too early. Leave `loaded` false so the real values are picked up later
                // instead of latching the defaults in for the whole process lifetime.
                return;
            }
            hideStories = p.getBoolean(HIDE_STORIES, false);
            hideFloatingButton = p.getBoolean(HIDE_FLOATING_BUTTON, false);
            hideDialogsSearchBar = p.getBoolean(HIDE_DIALOGS_SEARCH_BAR, false);
            hideAllChats = p.getBoolean(HIDE_ALL_CHATS, false);
            hideArchiveFolder = p.getBoolean(HIDE_ARCHIVE_FOLDER, false);
            archiveOnPull = p.getBoolean(ARCHIVE_ON_PULL, false);
            disableUnarchiveSwipe = p.getBoolean(DISABLE_UNARCHIVE_SWIPE, false);
            squareFab = p.getBoolean(SQUARE_FAB, false);
            removeMessageTail = p.getBoolean(REMOVE_MESSAGE_TAIL, false);
            editedAsIcon = p.getBoolean(EDITED_AS_ICON, false);
            hideStickerTime = p.getBoolean(HIDE_STICKER_TIME, false);
            hideShareButton = p.getBoolean(HIDE_SHARE_BUTTON, false);
            hideReactionsChannels = p.getBoolean(HIDE_REACTIONS_CHANNELS, false);
            hideReactionsGroups = p.getBoolean(HIDE_REACTIONS_GROUPS, false);
            hideReactionsPrivate = p.getBoolean(HIDE_REACTIONS_PRIVATE, false);
            hideKeyboardOnScroll = p.getBoolean(HIDE_KEYBOARD_ON_SCROLL, false);
            commaAfterMention = p.getBoolean(COMMA_AFTER_MENTION, false);
            hideSendAsPeer = p.getBoolean(HIDE_SEND_AS_PEER, false);
            disableNumberRounding = p.getBoolean(DISABLE_NUMBER_ROUNDING, false);
            timeWithSeconds = p.getBoolean(TIME_WITH_SECONDS, false);
            relativeLastSeen = p.getBoolean(RELATIVE_LAST_SEEN, false);
            showPeerId = p.getBoolean(SHOW_ID_AND_DC, false);
            forceSnow = p.getBoolean(FORCE_SNOW, false);
            centerTitle = p.getBoolean(CENTER_TITLE, false);
            doubleTapAction = p.getInt(DOUBLE_TAP_ACTION, DOUBLE_TAP_REACTION);
            avatarCorners = p.getInt(AVATAR_CORNERS, p.getInt(AVATAR_CORNERS_OLD, 50) * 10);
            menuSaveToSaved = p.getBoolean(MENU_SAVE_TO_SAVED, false);
            menuDetails = p.getBoolean(MENU_DETAILS, false);
            stickerSize = p.getInt(STICKER_SIZE, STICKER_SIZE_DEFAULT);
            senderMiniAvatars = p.getBoolean(SENDER_MINI_AVATARS, false);
            adminShortcuts = p.getBoolean(ADMIN_SHORTCUTS, false);
            translateProvider = p.getInt(TRANSLATE_PROVIDER, PrimeTranslator.PROVIDER_TELEGRAM);
            saveRoundAndVoice = p.getBoolean(SAVE_ROUND_AND_VOICE, false);
            sendUncompressed = p.getBoolean(SEND_UNCOMPRESSED, false);
            maxVideoHeight = p.getInt(MAX_VIDEO_HEIGHT, 0);
            roundVideoRearCamera = p.getBoolean(ROUND_VIDEO_REAR, false);
            optimizations = p.getBoolean(OPTIMIZATIONS, true);
            hideSettingsHeader = p.getBoolean(HIDE_SETTINGS_HEADER, false);
            mainTitleUsername = p.getBoolean(MAIN_TITLE_USERNAME, false);
            hideEmojiStatus = p.getBoolean(HIDE_EMOJI_STATUS, false);
            loaded = true;
        } catch (Throwable t) {
            // Called from UI-critical paths; stock behaviour is the only safe fallback.
            loaded = true;
        }
    }

    /** Uncached read, for settings screens and anything outside a draw path. */
    public static boolean get(String key) {
        try {
            return prefs().getBoolean(key, false);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void set(String key, boolean value) {
        SharedPreferences p = prefs();
        if (p == null) {
            return;
        }
        p.edit().putBoolean(key, value).apply();
        reload();
    }

    public static int getInt(String key, int def) {
        try {
            return prefs().getInt(key, def);
        } catch (Throwable t) {
            return def;
        }
    }

    /**
     * Updates the one cached field a key maps to. Anything not listed here is not held in a field,
     * so nothing needs refreshing for it.
     */
    private static void primeApplyInt(String key, int value) {
        switch (key) {
            case AVATAR_CORNERS:
                avatarCorners = Math.max(0, Math.min(AVATAR_CORNERS_MAX, value));
                break;
            case STICKER_SIZE:
                stickerSize = value;
                break;
            case TRANSLATE_PROVIDER:
                translateProvider = value;
                break;
            case MAX_VIDEO_HEIGHT:
                maxVideoHeight = value;
                break;
            default:
                // An unfamiliar key may still be one the cache holds under another name; falling
                // back to a full reload keeps this correct as keys are added.
                reload();
                break;
        }
    }

    public static void setInt(String key, int value) {
        SharedPreferences p = prefs();
        if (p == null) {
            return;
        }
        p.edit().putInt(key, value).apply();
        // A full reload re-reads every key this class holds. That is fine once, and wrong on a
        // slider: setInt runs on each frame of the drag, and each frame re-read forty values to
        // change one. Refreshing the single field keeps the cache honest at a fixed cost.
        primeApplyInt(key, value);
    }

    public static boolean hideStories() {
        ensureLoaded();
        return hideStories;
    }

    public static boolean hideFloatingButton() {
        ensureLoaded();
        return hideFloatingButton;
    }

    public static boolean hideDialogsSearchBar() {
        ensureLoaded();
        return hideDialogsSearchBar;
    }

    public static boolean hideAllChats() {
        ensureLoaded();
        return hideAllChats;
    }

    public static boolean hideArchiveFolder() {
        ensureLoaded();
        return hideArchiveFolder;
    }

    public static boolean archiveOnPull() {
        ensureLoaded();
        return archiveOnPull;
    }

    public static boolean disableUnarchiveSwipe() {
        ensureLoaded();
        return disableUnarchiveSwipe;
    }

    public static boolean squareFab() {
        ensureLoaded();
        return squareFab;
    }

    public static boolean removeMessageTail() {
        ensureLoaded();
        return removeMessageTail;
    }

    public static boolean editedAsIcon() {
        ensureLoaded();
        return editedAsIcon;
    }

    public static boolean hideStickerTime() {
        ensureLoaded();
        return hideStickerTime;
    }

    /** Adds a copyable "ID · DCn" row to profiles. Off by default - it is a power-user thing. */
    public static boolean showPeerId() {
        ensureLoaded();
        return showPeerId;
    }

    /** Lifts the new-year date gate so the snow and the decorated title work all year. */
    public static boolean forceSnow() {
        ensureLoaded();
        return forceSnow;
    }

    /** Centres the action bar title and subtitle when there is room for it. */
    public static boolean centerTitle() {
        ensureLoaded();
        return centerTitle;
    }

    public static int doubleTapAction() {
        ensureLoaded();
        return doubleTapAction;
    }

    public static boolean hideShareButton() {
        ensureLoaded();
        return hideShareButton;
    }

    public static boolean hideReactionsChannels() {
        ensureLoaded();
        return hideReactionsChannels;
    }

    public static boolean hideReactionsGroups() {
        ensureLoaded();
        return hideReactionsGroups;
    }

    public static boolean hideReactionsPrivate() {
        ensureLoaded();
        return hideReactionsPrivate;
    }

    public static boolean hideKeyboardOnScroll() {
        ensureLoaded();
        return hideKeyboardOnScroll;
    }

    public static boolean commaAfterMention() {
        ensureLoaded();
        return commaAfterMention;
    }

    public static boolean hideSendAsPeer() {
        ensureLoaded();
        return hideSendAsPeer;
    }

    public static boolean disableNumberRounding() {
        ensureLoaded();
        return disableNumberRounding;
    }

    public static boolean timeWithSeconds() {
        ensureLoaded();
        return timeWithSeconds;
    }

    public static boolean relativeLastSeen() {
        ensureLoaded();
        return relativeLastSeen;
    }

    /** Adds "В избранное" to the message menu - a one-tap forward to Saved Messages. */
    public static boolean menuSaveToSaved() {
        ensureLoaded();
        return menuSaveToSaved;
    }

    /** Adds "Подробности" to the message menu - ids and timestamps, copyable. */
    public static boolean menuDetails() {
        ensureLoaded();
        return menuDetails;
    }

    /** Offers a save entry for round videos and voice messages, which stock never saves. */
    public static boolean saveRoundAndVoice() {
        ensureLoaded();
        return saveRoundAndVoice;
    }

    /** Master switch for the speed-ups this fork adds. See {@link #OPTIMIZATIONS}. */
    public static boolean optimizations() {
        ensureLoaded();
        return optimizations;
    }

    /** Read while Telegram's settings list is built, so it takes effect on the next open. */
    public static boolean hideSettingsHeader() {
        ensureLoaded();
        return hideSettingsHeader;
    }

    /** Read once while the chat list action bar is built. */
    public static boolean mainTitleUsername() {
        ensureLoaded();
        return mainTitleUsername;
    }

    /** Read once while the chat list action bar is built. */
    public static boolean hideEmojiStatus() {
        ensureLoaded();
        return hideEmojiStatus;
    }

    /** Read by the round-video recorder every time it starts, so a change applies at once. */
    public static boolean roundVideoRearCamera() {
        ensureLoaded();
        return roundVideoRearCamera;
    }

    /**
     * Ceiling for the shorter side of a downloaded video, or 0 for none.
     *
     * <p>Read from VideoPlayer's quality picker, which also decides what gets fetched and saved -
     * so this caps traffic, not just playback.
     */
    public static int maxVideoHeight() {
        ensureLoaded();
        return maxVideoHeight;
    }

    /** Preselects "send as file" in the attachment sheet, so media keeps its original quality. */
    public static boolean sendUncompressed() {
        ensureLoaded();
        return sendUncompressed;
    }

    /** {@link PrimeTranslator#PROVIDER_TELEGRAM} or {@link PrimeTranslator#PROVIDER_GOOGLE}. */
    public static int translateProvider() {
        ensureLoaded();
        return translateProvider;
    }

    /** Adds ban and purge straight to the message menu, for groups you moderate. */
    public static boolean adminShortcuts() {
        ensureLoaded();
        return adminShortcuts;
    }

    /** Prefixes the chat-list message preview with the sender's avatar. Group chats only. */
    public static boolean senderMiniAvatars() {
        ensureLoaded();
        return senderMiniAvatars;
    }

    /** {@link #AVATAR_CORNERS_MAX} means a circle; smaller values square the avatar off. */
    public static int avatarCorners() {
        ensureLoaded();
        return avatarCorners;
    }

    /**
     * Applies a corner value without writing it down, so dragging a slider reshapes every avatar
     * on screen at once. The value still has to be stored afterwards to survive a restart.
     */
    public static void setAvatarCornersLive(int value) {
        ensureLoaded();
        avatarCorners = Math.max(0, Math.min(AVATAR_CORNERS_MAX, value));
    }

    /**
     * Takes the radius that would draw a full circle - half the avatar's size - and returns the
     * radius the user actually asked for. At 50 it hands the value straight back, so avatars stay
     * round and the drawing code takes its usual circle path.
     */
    public static int avatarRadius(int circleRadiusPx) {
        ensureLoaded();
        if (avatarCorners >= AVATAR_CORNERS_MAX || circleRadiusPx <= 0) {
            return circleRadiusPx;
        }
        if (avatarCorners <= 0) {
            return 0;
        }
        return Math.max(1, Math.round(circleRadiusPx * avatarCorners / (float) AVATAR_CORNERS_MAX));
    }

    public static int stickerSize() {
        ensureLoaded();
        return stickerSize;
    }
}
