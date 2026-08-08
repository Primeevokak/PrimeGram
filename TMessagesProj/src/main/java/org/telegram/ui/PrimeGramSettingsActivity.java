package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import vpn.sdk.VpnSDK;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import java.util.ArrayList;

public class PrimeGramSettingsActivity extends UniversalFragment {

    private static final int ID_LIMIT_FOLDERS = 1;
    private static final int ID_LIMIT_PINNED_FOLDER = 2;
    private static final int ID_LIMIT_PINNED_SAVED = 3;
    private static final int ID_LIMIT_CHANNELS = 4;
    private static final int ID_LIMIT_GIFS = 5;
    private static final int ID_LIMIT_STICKERS = 6;
    private static final int ID_LIMIT_CHATS_IN_FOLDER = 7;
    private static final int ID_LIMIT_PUBLIC_LINKS = 8;
    private static final int ID_LIMIT_CAPTION = 9;
    private static final int ID_LIMIT_ABOUT = 10;
    private static final int ID_SIDEBAR_ENABLED = 11;
    private static final int ID_SUPPORT_PROJECT = 12;
    private static final int ID_GRANT_PERMISSIONS = 13;
    private static final int ID_AUTO_UPDATES = 14;
    private static final int ID_CHECK_UPDATES = 15;
    private static final int ID_FEED_EXCLUDE_MUTED = 16;
    private static final int ID_FEED_EXCLUDE_ARCHIVED = 17;
    private static final int ID_EMERGENCY_PROXY = 18;
    private static final int ID_TGWS_PROXY = 20;
    private static final int ID_HW_ACCEL = 19;
    private static final int ID_BATTERY_OPTIMIZATION = 21;
    private static final int ID_MUSIC_SETTINGS = 22;
    private static final int ID_HIDE_PHONE = 23;
    private static final int ID_FAKE_PHONE = 24;
    private static final int ID_GREY_ZONE = 25;
    private static final int ID_MESSAGE_TAGS = 26;
    private static final int ID_SEARCH_PLUS = 27;
    private static final int ID_TEMP_SUBS = 28;
    private static final int ID_TEXT_TOOLBAR = 29;
    private static final int ID_BOT_LOGIN = 30;
    private static final int ID_LINK_PREVIEW = 31;
    private static final int ID_STARTUP_TRACE = 33;
    private static final int ID_PUSH_STATUS = 32;
    private static final int ID_FEED_HIDDEN = 34;
    private static final int ID_ADBLOCK = 35;
    private static final int ID_ADBLOCK_DNS = 36;
    private static final int ID_DNS_ENABLED = 37;
    private static final int ID_DNS_PRESET = 38;
    private static final int ID_LIMIT_RECENT_STICKERS = 39;
    private static final int ID_ONLINE_DOTS = 40;
    private static final int ID_STT_ENABLED = 41;
    private static final int ID_STT_SMART_DNS = 138;
    private static final int ID_STT_TOKEN = 42;
    private static final int ID_STT_ENDPOINT = 43;
    private static final int ID_STT_MODEL = 44;
    private static final int ID_RELATIVE_LAST_SEEN = 45;
    private static final int ID_NO_NUMBER_ROUNDING = 46;
    private static final int ID_TIME_WITH_SECONDS = 47;
    private static final int ID_HIDE_STORIES = 48;
    private static final int ID_HIDE_FAB = 49;
    private static final int ID_ARCHIVE_ON_PULL = 50;
    private static final int ID_DISABLE_UNARCHIVE_SWIPE = 51;
    private static final int ID_HIDE_SHARE_BUTTON = 52;
    private static final int ID_EDITED_AS_ICON = 53;
    private static final int ID_COMMA_AFTER_MENTION = 54;
    private static final int ID_HIDE_KEYBOARD_ON_SCROLL = 55;
    private static final int ID_STICKER_SIZE = 56;
    private static final int ID_HIDE_REACTIONS_CHANNELS = 57;
    private static final int ID_HIDE_REACTIONS_GROUPS = 58;
    private static final int ID_HIDE_REACTIONS_PRIVATE = 59;
    private static final int ID_HIDE_SEND_AS_PEER = 60;
    private static final int ID_SQUARE_FAB = 61;
    private static final int ID_HW_BENCHMARK = 62;
    private static final int ID_HIDE_STICKER_TIME = 63;
    private static final int ID_SHOW_ID_AND_DC = 64;
    private static final int ID_FORCE_SNOW = 65;
    private static final int ID_CENTER_TITLE = 66;
    private static final int ID_REMOVE_TAIL = 67;
    private static final int ID_DOUBLE_TAP = 68;
    private static final int ID_HIDE_ARCHIVE_FOLDER = 69;
    private static final int ID_HIDE_ALL_CHATS = 70;
    private static final int ID_AVATAR_CORNERS = 71;
    private static final int ID_ICON_PACKS = 139;
    private static final int ID_NON_ISLAND_UI = 140;
    private static final int ID_NAVIGATION_DRAWER = 141;
    private static final int ID_MAIN_TABS_COMPACT = 142;
    private static final int ID_MAIN_TABS_HIDE = 143;
    private static final int ID_VPN_GUARD = 144;
    private static final int ID_VPN_GUARD_WHITELIST = 145;
    private static final int ID_WHATS_NEW = 146;
    private static final int ID_VLESS_CUSTOM_KEY = 147;
    private static final int ID_BATTERY_DIAG = 148;
    private static final int ID_ADBLOCK_UPDATE = 72;
    private static final int ID_LOCKSCREEN_CALLS = 73;
    private static final int ID_MENU_SAVE = 74;
    private static final int ID_MENU_DETAILS = 75;
    private static final int ID_SENDER_MINI_AVATARS = 76;
    private static final int ID_ADMIN_SHORTCUTS = 77;
    private static final int ID_TRANSLATE_PROVIDER = 78;
    private static final int ID_SAVE_ROUND_VOICE = 79;
    private static final int ID_SEND_UNCOMPRESSED = 80;
    private static final int ID_CACHE = 81;
    private static final int ID_VIDEO_QUALITY = 82;
    private static final int ID_PRELOAD_VIDEO_MOBILE = 83;
    private static final int ID_AUTODOWNLOAD = 84;
    private static final int ID_WHISPER_ENABLED = 85;
    private static final int ID_WHISPER_MODEL = 86;
    private static final int ID_WHISPER_DOWNLOAD = 87;
    private static final int ID_WHISPER_LANGUAGE = 88;
    private static final int ID_WHISPER_PREFER = 111;
    private static final int ID_ROUND_VIDEO_REAR = 89;
    private static final int ID_CAMERA2 = 90;
    private static final int ID_LIVE_PREVIEW = 91;
    private static final int ID_CHAT_PREVIEW = 106;
    private static final int ID_DOUBLE_TAP_CARDS = 92;
    private static final int ID_STICKER_SIZE_CARDS = 93;
    private static final int ID_TRANSLATOR_CARDS = 94;
    private static final int ID_VIDEO_QUALITY_CARDS = 95;
    /** Ids for the explanation cells, kept clear of everything else. */
    private static final int ID_LOGS_ENABLED = 96;
    private static final int ID_HIDE_SETTINGS_HEADER = 97;
    private static final int ID_MAIN_TITLE_USERNAME = 98;
    private static final int ID_HIDE_EMOJI_STATUS = 99;
    private static final int ID_OPTIMIZATIONS = 100;
    private static final int ID_DOUBLE_TAP_REACTION = 101;
    private static final int ID_STICKER_SIZE_SLIDER = 102;
    private static final int ID_SIDEBAR_ZONE = 103;
    private static final int ID_PLUGINS = 104;
    private static final int ID_TGWS_SETTINGS = 105;
    private static final int ID_TOOLBAR_BUTTONS = 107;
    private static final int ID_GUIDE = 108;
    private static final int ID_BIGFILE = 109;
    private static final int ID_BIGFILE_EXPERIMENTAL = 110;

    // ── The guided tour ────────────────────────────────────────────────────────────────────
    //
    // The script lives here, next to the section and row ids it points at, so a renumbered row
    // breaks the compiler rather than quietly aiming the tour at the wrong switch.
    //
    // Eleven stops, chosen by one rule: does the row's own name tell you what it does? "Снег
    // круглый год" needs no explanation and is not here. "Зона активации", "TgWs-сервер" and
    // "Плагины" are here because a user who has not read this conversation has no way to guess.

    private static org.telegram.messenger.PrimeGuide.Step[] primeGuideSteps() {
        final java.util.ArrayList<org.telegram.messenger.PrimeGuide.Step> steps = new java.util.ArrayList<>();
        steps.add(org.telegram.messenger.PrimeGuide.step(SECTION_ROOT, 0,
                "Что здесь есть",
                "PrimeGram добавляет к Telegram несколько десятков настроек. Пробегусь по тем, которые сложно найти самому, — минута.\n\nЛюбой шаг можно пропустить, а весь гайд перезапустить снизу этого экрана."));
        steps.add(org.telegram.messenger.PrimeGuide.step(SECTION_ROOT, ID_PLUGINS,
                "Плагины",
                "Расширения на Python, совместимые с exteraGram. Плагин приходит файлом .plugin — нажмите на него в любом чате, и приложение предложит установить.\n\nПлагин выполняется внутри приложения и видит всё, к чему у него есть доступ. Ставьте только те, чьему автору доверяете."));
        steps.add(org.telegram.messenger.PrimeGuide.step(SECTION_ROOT, ID_SECTION_BASE + SECTION_INTERFACE,
                "Интерфейс",
                "Всё про внешний вид: список чатов, поведение в чатах, оформление, лента каналов. Заглянем внутрь."));
        steps.add(org.telegram.messenger.PrimeGuide.step(SECTION_INTERFACE, ID_SIDEBAR_ENABLED,
                "Боковая панель",
                "Свайп от левого края открывает панель с аккаунтами, кошельком, прокси и настройками — не нужно тянуться к бургеру наверху."));
        steps.add(org.telegram.messenger.PrimeGuide.step(SECTION_INTERFACE, ID_SIDEBAR_ZONE,
                "Зона активации",
                "Панель отзывается не на всю левую треть экрана, а на прямоугольник, который вы сами нарисуете пальцем.\n\nЭто нужно, если свайп панели спорит с листанием вкладок: сузьте зону и сдвиньте её туда, куда дотягивается большой палец."));
        steps.add(org.telegram.messenger.PrimeGuide.step(SECTION_CONNECTION, ID_TGWS_SETTINGS,
                "TgWs-сервер",
                "Локальный туннель, через который приложение ходит в сеть в обход блокировок.\n\nНа этом экране видно, работает ли он, через какой домен идёт трафик и с какой задержкой отвечают остальные. Домен можно закрепить вручную, если провайдер режет конкретные."));
        steps.add(org.telegram.messenger.PrimeGuide.step(SECTION_TOOLS_MAIN, ID_TEXT_TOOLBAR,
                "Панель форматирования",
                "Выделите текст в поле ввода — над ним появится ряд кнопок: жирный, курсив, моноширинный, спойлер, цитата, ссылка.\n\nСостав и порядок кнопок настраиваются: перетащите их прямо на изображении панели."));
        steps.add(org.telegram.messenger.PrimeGuide.step(SECTION_TOOLS_MAIN, ID_SEARCH_PLUS,
                "Поиск+",
                "Находит собеседника по числовому ID, номеру телефона или ссылке — то, чего обычный поиск не умеет.\n\nПоиск по ID встроен и в обычный поиск: наберите там одни цифры, и ответ появится отдельным разделом внизу."));
        steps.add(org.telegram.messenger.PrimeGuide.step(SECTION_TOOLS_STT, ID_STT_ENABLED,
                "Расшифровка голосовых",
                "Превращает голосовое сообщение в текст. Работает и без подписки Telegram: через ваш ключ к внешнему сервису или полностью на устройстве, без интернета."));
        steps.add(org.telegram.messenger.PrimeGuide.step(SECTION_PREMIUM, 0,
                "Локальный Premium",
                "Лимиты Telegram — количество папок, закреплённых чатов, длина подписи — сняты на этом устройстве.\n\nЭто только внешний вид: сервер о них не знает, и другие люди изменений не увидят."));
        steps.add(org.telegram.messenger.PrimeGuide.step(SECTION_ROOT, ID_GUIDE,
                "Это всё",
                "Остальное подписано понятнее и ждёт вас в разделах. Гайд всегда можно запустить заново отсюда."));
        return steps.toArray(new org.telegram.messenger.PrimeGuide.Step[0]);
    }

    private org.telegram.ui.Components.PrimeGuideOverlay guideOverlay;

    private void primeStartGuide() {
        org.telegram.messenger.PrimeGuide.setSteps(primeGuideSteps());
        org.telegram.messenger.PrimeGuide.start();
        primeShowGuideStep();
    }

    /**
     * Shows the current step if it belongs to this page, and otherwise leaves it alone - the
     * fragment for its own section will pick it up when it opens.
     */
    private void primeShowGuideStep() {
        final org.telegram.messenger.PrimeGuide.Step step = org.telegram.messenger.PrimeGuide.currentStep();
        if (step == null || step.section != section || getContext() == null
                || !(fragmentView instanceof android.widget.FrameLayout)) {
            return;
        }
        final android.widget.FrameLayout container = (android.widget.FrameLayout) fragmentView;
        if (guideOverlay == null) {
            guideOverlay = new org.telegram.ui.Components.PrimeGuideOverlay(getContext());
            container.addView(guideOverlay, LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }
        primeBindGuideStep(step);
    }

    private void primeBindGuideStep(org.telegram.messenger.PrimeGuide.Step step) {
        final Runnable bind = () -> {
            if (guideOverlay == null) {
                return;
            }
            guideOverlay.setStep(primeGuideTargetBounds(step.itemId), step.title, step.text,
                    org.telegram.messenger.PrimeGuide.currentIndex() + 1,
                    org.telegram.messenger.PrimeGuide.total(),
                    this::primeGuideNext, this::primeGuideStop);
        };
        if (step.itemId == 0) {
            bind.run();
            return;
        }
        if (primeScrollToItem(step.itemId)) {
            // One frame for the scroll to land, otherwise the hole is cut where the row used to be.
            AndroidUtilities.runOnUIThread(bind, 220);
            return;
        }
        // The row is not on this page at all - "Зона активации" only exists while the sidebar is
        // switched on, and there are others like it. Explaining a control the reader cannot see is
        // worse than saying nothing, so the step is skipped rather than shown pointing at air.
        primeGuideNext();
    }

    /** Scrolls the row into view. Returns false when this page has no such row. */
    private boolean primeScrollToItem(int itemId) {
        if (listView == null || listView.adapter == null) {
            return false;
        }
        for (int i = 0; i < listView.adapter.getItemCount(); i++) {
            final UItem item = listView.adapter.getItem(i);
            if (item != null && item.id == itemId) {
                if (listView.getLayoutManager() instanceof androidx.recyclerview.widget.LinearLayoutManager) {
                    ((androidx.recyclerview.widget.LinearLayoutManager) listView.getLayoutManager())
                            .scrollToPositionWithOffset(i, AndroidUtilities.dp(120));
                }
                return true;
            }
        }
        return false;
    }

    /** Where the row sits in the overlay's coordinates, or null when it is not on screen. */
    private android.graphics.RectF primeGuideTargetBounds(int itemId) {
        if (itemId == 0 || listView == null || listView.adapter == null || guideOverlay == null) {
            return null;
        }
        for (int i = 0; i < listView.getChildCount(); i++) {
            final View child = listView.getChildAt(i);
            final int position = listView.getChildAdapterPosition(child);
            final UItem item = position >= 0 ? listView.adapter.getItem(position) : null;
            if (item != null && item.id == itemId) {
                final int[] childLocation = new int[2];
                final int[] overlayLocation = new int[2];
                child.getLocationInWindow(childLocation);
                guideOverlay.getLocationInWindow(overlayLocation);
                final float left = childLocation[0] - overlayLocation[0];
                final float top = childLocation[1] - overlayLocation[1];
                return new android.graphics.RectF(left, top,
                        left + child.getWidth(), top + child.getHeight());
            }
        }
        return null;
    }

    private void primeGuideNext() {
        final org.telegram.messenger.PrimeGuide.Step next = org.telegram.messenger.PrimeGuide.next();
        if (next == null) {
            primeGuideStop();
            return;
        }
        if (next.section == section) {
            primeBindGuideStep(next);
            return;
        }
        // The step lives elsewhere. Close the sheet here and open that page; its own fragment
        // finds the tour still running and picks up where this one left off.
        primeDismissGuide(() -> presentFragment(new PrimeGramSettingsActivity(next.section)));
    }

    private void primeGuideStop() {
        org.telegram.messenger.PrimeGuide.stop();
        primeDismissGuide(null);
    }

    private void primeDismissGuide(Runnable after) {
        if (guideOverlay == null) {
            if (after != null) {
                after.run();
            }
            return;
        }
        final org.telegram.ui.Components.PrimeGuideOverlay overlay = guideOverlay;
        guideOverlay = null;
        overlay.dismiss(after);
    }
    private static final int ID_INFO_BASE = 600;
    /** One id per blocking list, taken from a range nothing else uses. */
    private static final int ID_ADBLOCK_LIST_BASE = 200;

    /**
     * Plain on/off tweaks all behave identically, so they share one handler. Returns the
     * preference key for such an item, or null if the item needs its own handling.
     */
    private static String primeTweakKeyFor(int id) {
        if (id == ID_HIDE_STORIES) return org.telegram.messenger.PrimeTweaks.HIDE_STORIES;
        if (id == ID_HIDE_FAB) return org.telegram.messenger.PrimeTweaks.HIDE_FLOATING_BUTTON;
        if (id == ID_DISABLE_UNARCHIVE_SWIPE) return org.telegram.messenger.PrimeTweaks.DISABLE_UNARCHIVE_SWIPE;
        if (id == ID_HIDE_SHARE_BUTTON) return org.telegram.messenger.PrimeTweaks.HIDE_SHARE_BUTTON;
        if (id == ID_EDITED_AS_ICON) return org.telegram.messenger.PrimeTweaks.EDITED_AS_ICON;
        if (id == ID_COMMA_AFTER_MENTION) return org.telegram.messenger.PrimeTweaks.COMMA_AFTER_MENTION;
        if (id == ID_HIDE_KEYBOARD_ON_SCROLL) return org.telegram.messenger.PrimeTweaks.HIDE_KEYBOARD_ON_SCROLL;
        if (id == ID_HIDE_REACTIONS_CHANNELS) return org.telegram.messenger.PrimeTweaks.HIDE_REACTIONS_CHANNELS;
        if (id == ID_HIDE_REACTIONS_GROUPS) return org.telegram.messenger.PrimeTweaks.HIDE_REACTIONS_GROUPS;
        if (id == ID_HIDE_REACTIONS_PRIVATE) return org.telegram.messenger.PrimeTweaks.HIDE_REACTIONS_PRIVATE;
        if (id == ID_HIDE_SEND_AS_PEER) return org.telegram.messenger.PrimeTweaks.HIDE_SEND_AS_PEER;
        if (id == ID_SQUARE_FAB) return org.telegram.messenger.PrimeTweaks.SQUARE_FAB;
        if (id == ID_HIDE_STICKER_TIME) return org.telegram.messenger.PrimeTweaks.HIDE_STICKER_TIME;
        if (id == ID_SHOW_ID_AND_DC) return org.telegram.messenger.PrimeTweaks.SHOW_ID_AND_DC;
        if (id == ID_FORCE_SNOW) return org.telegram.messenger.PrimeTweaks.FORCE_SNOW;
        if (id == ID_CENTER_TITLE) return org.telegram.messenger.PrimeTweaks.CENTER_TITLE;
        if (id == ID_REMOVE_TAIL) return org.telegram.messenger.PrimeTweaks.REMOVE_MESSAGE_TAIL;
        if (id == ID_HIDE_ARCHIVE_FOLDER) return org.telegram.messenger.PrimeTweaks.HIDE_ARCHIVE_FOLDER;
        if (id == ID_HIDE_ALL_CHATS) return org.telegram.messenger.PrimeTweaks.HIDE_ALL_CHATS;
        if (id == ID_MENU_SAVE) return org.telegram.messenger.PrimeTweaks.MENU_SAVE_TO_SAVED;
        if (id == ID_MENU_DETAILS) return org.telegram.messenger.PrimeTweaks.MENU_DETAILS;
        if (id == ID_SENDER_MINI_AVATARS) return org.telegram.messenger.PrimeTweaks.SENDER_MINI_AVATARS;
        if (id == ID_ROUND_VIDEO_REAR) return org.telegram.messenger.PrimeTweaks.ROUND_VIDEO_REAR;
        if (id == ID_HIDE_SETTINGS_HEADER) return org.telegram.messenger.PrimeTweaks.HIDE_SETTINGS_HEADER;
        if (id == ID_MAIN_TITLE_USERNAME) return org.telegram.messenger.PrimeTweaks.MAIN_TITLE_USERNAME;
        if (id == ID_HIDE_EMOJI_STATUS) return org.telegram.messenger.PrimeTweaks.HIDE_EMOJI_STATUS;
        if (id == ID_OPTIMIZATIONS) return org.telegram.messenger.PrimeTweaks.OPTIMIZATIONS;
        if (id == ID_ADMIN_SHORTCUTS) return org.telegram.messenger.PrimeTweaks.ADMIN_SHORTCUTS;
        if (id == ID_SAVE_ROUND_VOICE) return org.telegram.messenger.PrimeTweaks.SAVE_ROUND_AND_VOICE;
        if (id == ID_SEND_UNCOMPRESSED) return org.telegram.messenger.PrimeTweaks.SEND_UNCOMPRESSED;
        return null;
    }

    /**
     * Which page this instance shows. The screen had grown to a single scroll of roughly
     * forty items, where finding anything meant reading everything; it is now a short hub of
     * categories, each opening this same fragment with a different section.
     */
    private static final int SECTION_ROOT = 0;
    private static final int SECTION_INTERFACE = 1;
    private static final int SECTION_CONNECTION = 2;
    private static final int SECTION_PRIVACY = 3;
    private static final int SECTION_TOOLS = 4;
    private static final int SECTION_MEDIA = 5;
    private static final int SECTION_ADVANCED = 6;
    private static final int SECTION_PREMIUM = 7;
    private static final int SECTION_ABOUT = 8;

    /**
     * Sub-pages.
     *
     * <p>Three of the categories above had themselves grown past a screenful — "Интерфейс" alone
     * held eleven headers and most of the media settings, because that is where they happened to
     * be written rather than where anyone would look for them. A category that needs scrolling to
     * be read is a category that has stopped helping, so those three became hubs of their own and
     * their contents moved here, one page per idea.
     */
    private static final int SECTION_UI_DIALOGS = 10;
    private static final int SECTION_UI_CHAT = 11;
    private static final int SECTION_UI_APPEARANCE = 12;
    private static final int SECTION_UI_FEED = 13;
    private static final int SECTION_UI_REACTIONS = 14;
    private static final int SECTION_UI_FORMAT = 15;
    private static final int SECTION_UI_PROFILE = 16;

    private static final int SECTION_TOOLS_MAIN = 20;
    private static final int SECTION_TOOLS_TAGS = 21;
    private static final int SECTION_TOOLS_STT = 22;
    private static final int SECTION_TOOLS_BROWSER = 23;

    private static final int SECTION_MEDIA_SEND = 30;
    private static final int SECTION_MEDIA_QUALITY = 31;
    private static final int SECTION_MEDIA_CAMERA = 32;
    private static final int SECTION_MEDIA_TRANSLATE = 33;
    private static final int SECTION_MEDIA_MUSIC = 34;

    /** Category rows on the hub. Offset well past the setting ids so they cannot collide. */
    private static final int ID_SECTION_BASE = 900;

    private final int section;

    public PrimeGramSettingsActivity() {
        this(SECTION_ROOT);
    }

    private PrimeGramSettingsActivity(int section) {
        this.section = section;
    }

    @Override
    public View createView(Context context) {
        final View view = super.createView(context);
        // Our card rows cover the highlight themselves; drawing it behind them keeps it from
        // spilling into the margins between a card and the edge of the screen. Rows that are not
        // cards - the category rows on a hub - are transparent, so it still shows through them.
        if (listView != null) {
            listView.setDrawSelectorBehind(true);
        }
        if (section == SECTION_UI_APPEARANCE && view instanceof android.widget.FrameLayout) {
            attachPinnedPreview(context, (android.widget.FrameLayout) view);
        }
        return view;
    }

    /**
     * Pins the appearance preview above the list instead of scrolling it away.
     *
     * <p>This section has more switches than fit on a screen, and every one of them changes what
     * the preview shows. A preview that scrolls off is a preview you cannot see while using the
     * bottom half of the section - which is where the bubble and avatar settings are, the ones with
     * the most to look at.
     *
     * <p>The list gets top padding equal to the preview's height, applied whenever that height
     * changes, because the preview grows and shrinks with the sticker size the user is dragging.
     */
    private void attachPinnedPreview(Context context, android.widget.FrameLayout contentView) {
        final org.telegram.ui.Components.PrimeLivePreviewCell preview = livePreviewCell();
        if (preview == null) {
            return;
        }
        final android.widget.FrameLayout holder = new android.widget.FrameLayout(context);
        holder.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        holder.addView(preview, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // A hairline under it, so the pinned block reads as a header rather than as the first row
        // of the list sitting oddly still.
        final View divider = new View(context);
        divider.setBackgroundColor(getThemedColor(Theme.key_divider));
        holder.addView(divider, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, 1f / AndroidUtilities.density, Gravity.BOTTOM));

        contentView.addView(holder, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        holder.addOnLayoutChangeListener((v, left, top, right, bottom, ol, ot, or, ob) -> {
            final int height = bottom - top;
            if (listView == null || height <= 0 || listView.getPaddingTop() == height) {
                return;
            }
            final boolean first = listView.getPaddingTop() == 0;
            listView.setClipToPadding(false);
            listView.setPadding(listView.getPaddingLeft(), height,
                    listView.getPaddingRight(), listView.getPaddingBottom());
            // Only the first time. The preview changes height as settings change, and scrolling
            // to the top on every such change threw the list back to the start under the user's
            // finger - while they were dragging a slider halfway down the page.
            if (first) {
                listView.scrollToPosition(0);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (org.telegram.messenger.PrimeGuide.isRunning()) {
            // The list is rebuilt just below, and the row the tour points at has to exist before
            // it can be measured, so this waits a beat rather than racing the adapter.
            AndroidUtilities.runOnUIThread(this::primeShowGuideStep, 120);
        }
        // Values here are edited on other screens (grey zone, music, tags), so the list has to
        // be rebuilt on return — otherwise it keeps showing what was true when it was opened.
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    /**
     * PrimeGram: the hub.
     *
     * <p>Built from {@link SettingsActivity.SettingCell}, the same rows Telegram's own settings
     * screen uses - a coloured icon, a title and a description underneath. The previous version
     * used {@code UItem.asButton(id, title, value)}, which puts the second string on the *right*
     * as blue text: at this length it truncated to "Боковая панель, л…", so the one place a
     * description was supposed to help was the one place it could not be read.
     */
    private void fillRoot(ArrayList<UItem> items) {
        items.add(primeHeaderCell());
        items.add(UItem.asShadow(null));
        items.add(section(SECTION_INTERFACE, IconBackgroundColors.BLUE, R.drawable.settings_folders,
                "Интерфейс", "Список чатов, чаты, оформление, лента"));
        items.add(section(SECTION_CONNECTION, IconBackgroundColors.GREEN, R.drawable.settings_data,
                "Соединение", "Прокси, VLESS, работа в фоне"));
        items.add(section(SECTION_PRIVACY, IconBackgroundColors.RED, R.drawable.settings_privacy,
                "Приватность", "Номер, серая зона"));
        items.add(section(SECTION_TOOLS, IconBackgroundColors.ORANGE, R.drawable.settings_features,
                "Инструменты", "Теги, ссылки, расшифровка, браузер"));
        items.add(section(SECTION_MEDIA, IconBackgroundColors.CYAN, R.drawable.settings_sounds,
                "Медиа и музыка", "Качество, кэш, камера, перевод"));
        if (org.telegram.messenger.GreyZone.isAccepted()) {
            items.add(section(SECTION_PREMIUM, IconBackgroundColors.PURPLE, R.drawable.settings_premium,
                    "Локальный Premium", "Лимиты на этом устройстве"));
        }
        items.add(section(SECTION_ADVANCED, IconBackgroundColors.BLUE_DEEP, R.drawable.settings_power,
                "Дополнительно", "Обновления, эксперименты, диагностика"));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_PLUGINS,
                IconBackgroundColors.ORANGE_DEEP.top, IconBackgroundColors.ORANGE_DEEP.bottom,
                R.drawable.msg_puzzle, "Плагины", primePluginsSubtitle()));
        items.add(section(SECTION_ABOUT, IconBackgroundColors.GRAY, R.drawable.settings_ask,
                "Разрешения и поддержка", "Доступы приложения и связь с автором"));
        items.add(UItem.asShadow(null));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_GUIDE,
                IconBackgroundColors.PURPLE.top, IconBackgroundColors.PURPLE.bottom,
                R.drawable.msg_info, "Гайд по PrimeGram",
                org.telegram.messenger.PrimeGuide.wasShown()
                        ? "Пройти ещё раз" : "Показать, что здесь настраивается"));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_WHATS_NEW,
                IconBackgroundColors.GREEN.top, IconBackgroundColors.GREEN.bottom,
                R.drawable.msg_notifications, "Что нового в этой версии",
                org.telegram.messenger.PrimeWhatsNew.currentVersion()));
        items.add(UItem.asShadow(null));
    }

    /**
     * Short explanation in the list, full one behind "Подробнее".
     *
     * <p>Cached by id because {@link UItem#asCustom} hands the list a view, and a new view on every
     * rebuild would drop the scroll position and rebuild the span every time a switch is flipped.
     */
    private final java.util.HashMap<Integer, org.telegram.ui.Cells.PrimeInfoCell> infoCells = new java.util.HashMap<>();

    private UItem info(int id, String title, CharSequence summary, CharSequence details) {
        org.telegram.ui.Cells.PrimeInfoCell cell = infoCells.get(id);
        if (cell == null && getContext() != null) {
            cell = new org.telegram.ui.Cells.PrimeInfoCell(getContext(), title, summary, details);
            infoCells.put(id, cell);
        }
        return cell == null ? UItem.asShadow(summary) : UItem.asCustom(ID_INFO_BASE + id, cell);
    }

    private org.telegram.ui.Components.PrimeLivePreviewCell livePreviewCell;

    private org.telegram.ui.Components.PrimeLivePreviewCell livePreviewCell() {
        if (livePreviewCell == null && getContext() != null && getParentLayout() != null) {
            livePreviewCell = new org.telegram.ui.Components.PrimeLivePreviewCell(
                    getContext(), getParentLayout(), 0, null);
        }
        return livePreviewCell;
    }

    private org.telegram.ui.Components.PrimeStickerPreviewCell stickerPreviewCell;

    /**
     * The sticker preview. Not the theme preview cell, which draws text bubbles and no sticker at
     * all - it sat above the sticker size controls showing nothing that they changed.
     */
    private org.telegram.ui.Components.PrimeStickerPreviewCell stickerPreviewCell() {
        if (stickerPreviewCell == null && getContext() != null) {
            stickerPreviewCell = new org.telegram.ui.Components.PrimeStickerPreviewCell(
                    getContext(), getResourceProvider());
        }
        return stickerPreviewCell;
    }

    /** Pushes a settings change into whichever previews are on screen. */
    private void updateLivePreview() {
        if (livePreviewCell != null) {
            livePreviewCell.update();
        }
        if (stickerPreviewCell != null) {
            stickerPreviewCell.update();
        }
    }

    private View primeHeaderView;

    /**
     * The masthead: icon, name, build.
     *
     * <p>Costs one screenful of scroll and earns it back - this screen is reached from a row
     * inside Telegram's own settings, and without a header there is nothing to tell you which
     * application's settings you are now in, nor which build to quote when something breaks.
     */
    private UItem primeHeaderCell() {
        if (primeHeaderView == null && getContext() != null) {
            final Context context = getContext();
            final LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setGravity(Gravity.CENTER_HORIZONTAL);
            layout.setPadding(0, AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18));

            final ImageView logo = new ImageView(context);
            logo.setImageResource(R.mipmap.ic_launcher_round);
            layout.addView(logo, org.telegram.ui.Components.LayoutHelper.createLinear(72, 72));

            final TextView name = new TextView(context);
            name.setText("PrimeGram");
            name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 20);
            name.setTypeface(AndroidUtilities.bold());
            name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            name.setGravity(Gravity.CENTER);
            layout.addView(name, org.telegram.ui.Components.LayoutHelper.createLinear(
                    org.telegram.ui.Components.LayoutHelper.MATCH_PARENT,
                    org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));

            final TextView version = new TextView(context);
            version.setText(org.telegram.messenger.BuildVars.BUILD_VERSION_STRING);
            version.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 13);
            version.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            version.setGravity(Gravity.CENTER);
            layout.addView(version, org.telegram.ui.Components.LayoutHelper.createLinear(
                    org.telegram.ui.Components.LayoutHelper.MATCH_PARENT,
                    org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT, 0, 3, 0, 0));

            primeHeaderView = layout;
        }
        return UItem.asCustom(ID_SECTION_BASE + 99, primeHeaderView);
    }

    /**
     * The plugins row's second line. Deliberately says how many are installed rather than
     * describing what plugins are: someone who has three wants to know they still have three, and
     * someone who has none is told what the screen is for once they open it.
     */
    private CharSequence primePluginsSubtitle() {
        final int count = org.telegram.messenger.plugins.PrimePluginsController.getInstance().count();
        if (count == 0) {
            return "Расширения на Python, совместимые с exteraGram";
        }
        final int tens = count % 100, ones = count % 10;
        final String word;
        if (tens >= 11 && tens <= 14) {
            word = "плагинов";
        } else if (ones == 1) {
            word = "плагин";
        } else if (ones >= 2 && ones <= 4) {
            word = "плагина";
        } else {
            word = "плагинов";
        }
        // The active count only means something once the interpreter has actually run every
        // plugin at least once - before that it is just "0", which would read as every plugin
        // being broken rather than as "hasn't started yet".
        final int active = org.telegram.messenger.plugins.PrimePluginHooks.activeCount();
        if (active > 0 && active < count) {
            return count + " " + word + " · " + active + " активно";
        }
        return count + " " + word;
    }

    private CharSequence toolbarSummary() {
        final int count = org.telegram.messenger.PrimeToolbarSettings.items().size();
        return org.telegram.messenger.PrimeToolbarSettings.isDefaultOrder()
                ? "все, по умолчанию" : count + " из " + org.telegram.messenger.PrimeToolbarSettings.ALL.length;
    }

    /**
     * The toolbar's buttons, arranged by dragging them on a drawing of the toolbar itself.
     *
     * <p>Saved on every movement, like the sidebar zone and for the same reason: the obvious place
     * to save - the sheet's dismiss listener - is replaced by {@code showDialog}, so anything left
     * there is silently thrown away.
     */
    private void showToolbarButtonsSheet() {
        if (getParentActivity() == null) {
            return;
        }
        final Context context = getParentActivity();
        final LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);

        final org.telegram.ui.Components.PrimeToolbarEditor editor =
                new org.telegram.ui.Components.PrimeToolbarEditor(context, getResourceProvider());
        content.addView(editor, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));

        final TextView hintView = new TextView(context);
        hintView.setGravity(Gravity.CENTER);
        hintView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        hintView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
        hintView.setText("Перетащите кнопку, чтобы поменять порядок или убрать её с панели. Короткое нажатие делает то же самое одним движением.");
        content.addView(hintView, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 21, 4, 21, 4));

        editor.setOnChange(() -> {
            org.telegram.messenger.PrimeToolbarSettings.setItems(editor.getItems());
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        });

        final LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(buttons, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 12, 16, 8));

        final TextView resetView = new TextView(context);
        resetView.setGravity(Gravity.CENTER);
        resetView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        resetView.setTypeface(AndroidUtilities.bold());
        resetView.setText("Сбросить");
        resetView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
        resetView.setBackground(Theme.createRadSelectorDrawable(
                getThemedColor(Theme.key_listSelector), 8, 8));
        resetView.setOnClickListener(v -> editor.resetToDefaults());
        buttons.addView(resetView, LayoutHelper.createLinear(0, 44, 1f));

        final TextView doneView = new TextView(context);
        doneView.setGravity(Gravity.CENTER);
        doneView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        doneView.setTypeface(AndroidUtilities.bold());
        doneView.setText(LocaleController.getString(R.string.Done));
        doneView.setTextColor(getThemedColor(Theme.key_featuredStickers_buttonText));
        doneView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(8),
                getThemedColor(Theme.key_featuredStickers_addButton),
                getThemedColor(Theme.key_featuredStickers_addButtonPressed)));
        buttons.addView(doneView, LayoutHelper.createLinear(0, 44, 1f, 8, 0, 0, 0));

        final BottomSheet sheet = new BottomSheet.Builder(context, false, getResourceProvider())
                .setTitle("Кнопки панели", true)
                .setCustomView(content)
                .create();
        doneView.setOnClickListener(v -> sheet.dismiss());
        showDialog(sheet, dialog -> {
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        });
    }

    /** Port and domain on one line - the two things the sub-screen can change. */
    private CharSequence tgWsSummary() {
        final String pinned = org.telegram.messenger.TgWsProxyService.forcedDomain();
        return "порт " + org.telegram.messenger.TgWsProxyService.configuredPort()
                + " · " + (pinned.isEmpty() ? "домен авто" : pinned);
    }

    private CharSequence iconPackSummary() {
        final String activeId = org.telegram.messenger.PrimeIconPacks.getActivePackId();
        if (activeId == null) {
            return "родные иконки";
        }
        for (org.telegram.messenger.PrimeIconPacks.Pack pack : org.telegram.messenger.PrimeIconPacks.listPacks()) {
            if (pack.id.equals(activeId)) {
                return pack.name;
            }
        }
        return "родные иконки";
    }

    private UItem section(int section, IconBackgroundColors colors, int icon, CharSequence title, CharSequence subtitle) {
        return SettingsActivity.SettingCell.Factory.of(
                ID_SECTION_BASE + section, colors.top, colors.bottom, icon, title, subtitle);
    }

    // ---------------------------------------------------------------------------------------
    // Cards.
    //
    // A card is a run of related rows drawn as one rounded block. The rows are collected here
    // first and only handed to the list by endCard(), because a row cannot know whether it is
    // the last one in its card until the card is finished — and the corners depend on that.
    // ---------------------------------------------------------------------------------------

    private final ArrayList<UItem> cardRows = new ArrayList<>();

    private void row(UItem item) {
        cardRows.add(item);
    }

    private void endCard(ArrayList<UItem> items) {
        final int count = cardRows.size();
        for (int i = 0; i < count; i++) {
            final int position = count == 1
                    ? org.telegram.ui.Cells.PrimeCheckCell.POS_SINGLE
                    : i == 0 ? org.telegram.ui.Cells.PrimeCheckCell.POS_TOP
                    : i == count - 1 ? org.telegram.ui.Cells.PrimeCheckCell.POS_BOTTOM
                    : org.telegram.ui.Cells.PrimeCheckCell.POS_MIDDLE;
            org.telegram.ui.Cells.PrimeCheckCell.Factory.position(cardRows.get(i), position);
        }
        items.addAll(cardRows);
        cardRows.clear();
    }

    /** A switch row. */
    private UItem check(int id, IconBackgroundColors colors, int icon, CharSequence title, boolean checked) {
        return check(id, colors, icon, title, null, checked);
    }

    private UItem check(int id, IconBackgroundColors colors, int icon, CharSequence title,
                        CharSequence subtitle, boolean checked) {
        return org.telegram.ui.Cells.PrimeCheckCell.Factory.check(
                id, 0, colors.top, colors.bottom, icon, title, subtitle, checked);
    }

    /** A switch row wired straight to a {@link org.telegram.messenger.PrimeTweaks} key. */
    private UItem tweak(int id, IconBackgroundColors colors, int icon, CharSequence title, String key) {
        return tweak(id, colors, icon, title, null, key);
    }

    private UItem tweak(int id, IconBackgroundColors colors, int icon, CharSequence title,
                        CharSequence subtitle, String key) {
        return check(id, colors, icon, title, subtitle, org.telegram.messenger.PrimeTweaks.get(key));
    }

    /** A row that opens something. */
    private UItem button(int id, IconBackgroundColors colors, int icon, CharSequence title, CharSequence value) {
        return button(id, colors, icon, title, null, value);
    }

    private UItem button(int id, IconBackgroundColors colors, int icon, CharSequence title,
                         CharSequence subtitle, CharSequence value) {
        return org.telegram.ui.Cells.PrimeCheckCell.Factory.button(
                id, 0, colors.top, colors.bottom, icon, title, subtitle, value);
    }

    private CharSequence sectionTitle() {

        switch (section) {

            case SECTION_INTERFACE: return "Интерфейс";

            case SECTION_CONNECTION: return "Соединение";

            case SECTION_PRIVACY: return "Приватность";

            case SECTION_TOOLS: return "Инструменты";

            case SECTION_MEDIA: return "Медиа и музыка";

            case SECTION_ADVANCED: return "Дополнительно";

            case SECTION_PREMIUM: return "Локальный Premium";

            case SECTION_ABOUT: return "Разрешения и поддержка";

            case SECTION_UI_DIALOGS: return "Список чатов";

            case SECTION_UI_CHAT: return "В чатах";

            case SECTION_UI_APPEARANCE: return "Оформление";

            case SECTION_UI_FEED: return "Лента";

            case SECTION_UI_REACTIONS: return "Реакции";

            case SECTION_UI_FORMAT: return "Даты и числа";

            case SECTION_UI_PROFILE: return "Профиль";

            case SECTION_TOOLS_MAIN: return "Инструменты";

            case SECTION_TOOLS_TAGS: return "Теги сообщений";

            case SECTION_TOOLS_STT: return "Расшифровка голосовых";

            case SECTION_TOOLS_BROWSER: return "Встроенный браузер";

            case SECTION_MEDIA_SEND: return "Отправка и сохранение";

            case SECTION_MEDIA_QUALITY: return "Качество и загрузка";

            case SECTION_MEDIA_CAMERA: return "Камера";

            case SECTION_MEDIA_TRANSLATE: return "Перевод";

            case SECTION_MEDIA_MUSIC: return "Музыка";

            default: return "Настройки PrimeGram";

        }

    }

    @Override

    protected CharSequence getTitle() {

        return sectionTitle();

    }

    private String vlessKeyStatusText(boolean running) {

        if (running) {

            return "Статус: активен, трафик защищён.";

        }

        if (!VpnSDK.hasCachedXrayConfig()) {

            return "Статус: ключ ещё не получен от сервера.";

        }

        String lastError = VpnSDK.getProxyLastError();

        if (lastError != null && !lastError.isEmpty()) {

            return "Статус: ключ есть, но прокси не запустился (" + lastError + ").";

        }

        return "Статус: ключ получен, прокси выключен.";

    }

    @Override

    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {

        if (section == SECTION_ROOT) {
            fillRoot(items);
            return;
        }

        final SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        final MessagesController messagesController = MessagesController.getInstance(currentAccount);

        if (section == SECTION_INTERFACE) {
            row(check(ID_SIDEBAR_ENABLED, IconBackgroundColors.BLUE, R.drawable.msg_list,
                    "Боковая панель", preferences.getBoolean("primegram_sidebar_enabled", true)));
            if (preferences.getBoolean("primegram_sidebar_enabled", true)) {
                row(button(ID_SIDEBAR_ZONE, IconBackgroundColors.BLUE_LIGHT, R.drawable.msg_select,
                        "Зона активации", org.telegram.messenger.PrimeSidebarZone.describe()));
            }
            endCard(items);
            items.add(UItem.asShadow("Вертикальная панель на главном экране: переключение аккаунтов, кошелёк, прокси и настройки в одно движение от края экрана.\n\nЗона активации — часть экрана, где панель отзывается на свайп. По умолчанию это вся левая треть, и она спорит с листанием вкладок; её можно сузить и сдвинуть туда, куда дотягивается палец."));

            items.add(UItem.asHeader("Разделы"));
            items.add(section(SECTION_UI_DIALOGS, IconBackgroundColors.BLUE, R.drawable.msg_folders,
                    "Список чатов", "Истории, архив, вкладки, кнопка «Написать»"));
            items.add(section(SECTION_UI_CHAT, IconBackgroundColors.GREEN, R.drawable.msg_msgbubble3,
                    "В чатах", "Сообщения, стикеры, двойное нажатие, меню"));
            items.add(section(SECTION_UI_APPEARANCE, IconBackgroundColors.PURPLE, R.drawable.msg_colors,
                    "Оформление", "Заголовок, пузыри, аватарки, снег"));
            items.add(section(SECTION_UI_REACTIONS, IconBackgroundColors.ORANGE, R.drawable.msg_reactions,
                    "Реакции", "Где их показывать, а где нет"));
            items.add(section(SECTION_UI_FORMAT, IconBackgroundColors.CYAN, R.drawable.msg_calendar2,
                    "Даты и числа", "Время, секунды, округление"));
            items.add(section(SECTION_UI_PROFILE, IconBackgroundColors.BLUE_DEEP, R.drawable.msg_info,
                    "Профиль", "ID и дата-центр собеседника"));
            items.add(section(SECTION_UI_FEED, IconBackgroundColors.GRAY, R.drawable.msg_menu_stories,
                    "Лента", "Отдельная вкладка с каналами"));
            items.add(UItem.asShadow(null));
        }

        if (section == SECTION_UI_DIALOGS) {
            row(tweak(ID_HIDE_STORIES, IconBackgroundColors.PURPLE, R.drawable.msg_stories_myhide,
                    "Скрыть истории", org.telegram.messenger.PrimeTweaks.HIDE_STORIES));
            row(tweak(ID_HIDE_FAB, IconBackgroundColors.BLUE, R.drawable.msg_message,
                    "Скрыть кнопку «Написать»", org.telegram.messenger.PrimeTweaks.HIDE_FLOATING_BUTTON));
            row(tweak(ID_SQUARE_FAB, IconBackgroundColors.BLUE_DEEP, R.drawable.msg_msgbubble3,
                    "Квадратная кнопка «Написать»", org.telegram.messenger.PrimeTweaks.SQUARE_FAB));
            row(tweak(ID_SENDER_MINI_AVATARS, IconBackgroundColors.GREEN, R.drawable.msg_contacts,
                    "Аватарка отправителя в превью", org.telegram.messenger.PrimeTweaks.SENDER_MINI_AVATARS));
            endCard(items);

            items.add(UItem.asHeader("Архив и вкладки"));
            row(check(ID_ARCHIVE_ON_PULL, IconBackgroundColors.ORANGE, R.drawable.msg_archive_hide,
                    "Архив открывается потягиванием", org.telegram.messenger.SharedConfig.archiveHidden));
            row(tweak(ID_DISABLE_UNARCHIVE_SWIPE, IconBackgroundColors.ORANGE_DEEP, R.drawable.msg_unarchive,
                    "Не разархивировать свайпом", org.telegram.messenger.PrimeTweaks.DISABLE_UNARCHIVE_SWIPE));
            row(tweak(ID_HIDE_ARCHIVE_FOLDER, IconBackgroundColors.GRAY, R.drawable.msg_archive,
                    "Убрать строку «Архив»", org.telegram.messenger.PrimeTweaks.HIDE_ARCHIVE_FOLDER));
            row(tweak(ID_HIDE_ALL_CHATS, IconBackgroundColors.CYAN, R.drawable.msg_folders,
                    "Убрать вкладку «Все чаты»", org.telegram.messenger.PrimeTweaks.HIDE_ALL_CHATS));
            endCard(items);
            items.add(info(4, "Список чатов",
                    "Ничего не удаляется — только убирается с глаз.",
                    "Истории убираются там же, где принимается решение о их показе, поэтому пустого места не остаётся.\n\nКнопка «Написать» прячется только в списке чатов — при выборе чата для пересылки она остаётся, иначе подтвердить отправку было бы нечем.\n\nСвайп внутри архива блокируется только для действия «Архивировать»; если у вас на свайп назначено «Прочитать» или «Закрепить», оно продолжит работать.\n\nСтрока «Архив» пропадает только из списка — сам архив и всё, что в нём лежит, остаётся на месте и открывается из бокового меню.\n\nВкладка «Все чаты» убирается, если у вас есть хотя бы одна папка: без папок убирать нечего, иначе не осталось бы ни одной вкладки.\n\nАватарка отправителя показывается перед текстом последнего сообщения и только в группах: в личной переписке она бы повторяла аватарку самого чата, стоящую в паре пикселей левее. Свои сообщения остаются без значка."));
        }

        if (section == SECTION_UI_CHAT) {
            row(check(ID_ONLINE_DOTS, IconBackgroundColors.GREEN, R.drawable.msg_online,
                    "Точка «в сети» у аватарок в группах",
                    org.telegram.ui.Cells.PrimeMessageMarks.isOnlineDotsEnabled()));
            row(tweak(ID_HIDE_SHARE_BUTTON, IconBackgroundColors.BLUE, R.drawable.msg_share,
                    "Скрыть кнопку «Поделиться»", org.telegram.messenger.PrimeTweaks.HIDE_SHARE_BUTTON));
            row(tweak(ID_EDITED_AS_ICON, IconBackgroundColors.ORANGE, R.drawable.msg_edit,
                    "«Изменено» значком", org.telegram.messenger.PrimeTweaks.EDITED_AS_ICON));
            row(tweak(ID_HIDE_STICKER_TIME, IconBackgroundColors.PURPLE, R.drawable.msg_sticker,
                    "Скрыть время на стикерах и кружочках", org.telegram.messenger.PrimeTweaks.HIDE_STICKER_TIME));
            row(tweak(ID_HIDE_SEND_AS_PEER, IconBackgroundColors.GRAY, R.drawable.msg_channel,
                    "Скрыть выбор «отправить от имени»", org.telegram.messenger.PrimeTweaks.HIDE_SEND_AS_PEER));
            row(tweak(ID_COMMA_AFTER_MENTION, IconBackgroundColors.CYAN, R.drawable.msg_mention,
                    "Запятая после упоминания", org.telegram.messenger.PrimeTweaks.COMMA_AFTER_MENTION));
            row(tweak(ID_HIDE_KEYBOARD_ON_SCROLL, IconBackgroundColors.BLUE_DEEP, R.drawable.msg_go_down,
                    "Прятать клавиатуру при прокрутке", org.telegram.messenger.PrimeTweaks.HIDE_KEYBOARD_ON_SCROLL));
            endCard(items);

            items.add(UItem.asHeader("Размер стикеров"));
            // The preview sits between the header and the controls, so the sticker being resized
            // is on screen at the same time as the thing resizing it. Below the slider it would be
            // pushed off by the keyboard-height of card rows underneath.
            if (stickerPreviewCell() != null) {
                items.add(UItem.asCustom(ID_CHAT_PREVIEW, stickerPreviewCell()));
            }
            if (stickerSizeCards() != null) {
                items.add(UItem.asCustom(ID_STICKER_SIZE_CARDS, stickerSizeCards()));
            }
            if (stickerSizeSlider() != null) {
                items.add(UItem.asCustom(ID_STICKER_SIZE_SLIDER, stickerSizeSlider()));
            }

            items.add(UItem.asHeader("Двойное нажатие по сообщению"));
            if (doubleTapCards() != null) {
                items.add(UItem.asCustom(ID_DOUBLE_TAP_CARDS, doubleTapCards()));
            }
            // Only meaningful when a double tap actually sets a reaction. Straight to Telegram's own
            // picker rather than our own: it already handles the whole emoji set, custom emoji and
            // the Premium rules around them, and a second picker would be a second set of rules to
            // keep in step with the first.
            if (org.telegram.messenger.PrimeTweaks.doubleTapAction() == org.telegram.messenger.PrimeTweaks.DOUBLE_TAP_REACTION) {
                row(button(ID_DOUBLE_TAP_REACTION, IconBackgroundColors.ORANGE, R.drawable.msg_reactions,
                        "Какая реакция", doubleTapReactionName()));
                endCard(items);
            }

            items.add(UItem.asHeader("Меню сообщения"));
            row(tweak(ID_MENU_SAVE, IconBackgroundColors.ORANGE, R.drawable.msg_saved,
                    "Пункт «В избранное»", org.telegram.messenger.PrimeTweaks.MENU_SAVE_TO_SAVED));
            row(tweak(ID_MENU_DETAILS, IconBackgroundColors.BLUE, R.drawable.msg_info,
                    "Пункт «Подробности»", org.telegram.messenger.PrimeTweaks.MENU_DETAILS));
            row(tweak(ID_ADMIN_SHORTCUTS, IconBackgroundColors.RED, R.drawable.msg_admins,
                    "Админ-действия", org.telegram.messenger.PrimeTweaks.ADMIN_SHORTCUTS));
            endCard(items);
            items.add(info(2, "В чатах",
                    "Новые пункты появляются внизу меню долгого нажатия.",
                    "Клавиатура закрывается только при прокрутке пальцем — переход к ответу или новое сообщение её не тронут.\n\n«В избранное» пересылает сообщение в «Избранное» без выбора чата, альбом целиком.\n\n«Подробности» показывает ID сообщения, отправителя и время отправки и правки — всё копируется одной кнопкой.\n\nАдмин-действия — «Забанить» и «Удалить все сообщения» — появляются только в группах, где у вас есть право блокировать участников, и только на чужих сообщениях. Оба спрашивают подтверждение. Автора-канал они не трогают: это другой запрос, и он остаётся в профиле."));
        }

        if (section == SECTION_UI_APPEARANCE) {
            // The preview is not a row here - attachPinnedPreview() holds it above the list, so it
            // stays visible for every switch in the section rather than only the first few.
            row(tweak(ID_CENTER_TITLE, IconBackgroundColors.BLUE, R.drawable.msg_photo_text_regular,
                    "Заголовок по центру", org.telegram.messenger.PrimeTweaks.CENTER_TITLE));
            row(tweak(ID_MAIN_TITLE_USERNAME, IconBackgroundColors.BLUE_DEEP, R.drawable.msg_contacts_name,
                    "Вместо логотипа — своё имя", org.telegram.messenger.PrimeTweaks.MAIN_TITLE_USERNAME));
            row(tweak(ID_HIDE_EMOJI_STATUS, IconBackgroundColors.ORANGE, R.drawable.msg_smile_status,
                    "Скрыть свой эмодзи-статус", org.telegram.messenger.PrimeTweaks.HIDE_EMOJI_STATUS));
            row(tweak(ID_HIDE_SETTINGS_HEADER, IconBackgroundColors.GRAY, R.drawable.msg_settings,
                    "Убрать шапку профиля в настройках", org.telegram.messenger.PrimeTweaks.HIDE_SETTINGS_HEADER));
            row(tweak(ID_REMOVE_TAIL, IconBackgroundColors.PURPLE, R.drawable.msg_msgbubble3,
                    "Пузыри без хвостика", org.telegram.messenger.PrimeTweaks.REMOVE_MESSAGE_TAIL));
            row(tweak(ID_FORCE_SNOW, IconBackgroundColors.CYAN, R.drawable.msg_colors,
                    "Снег круглый год", org.telegram.messenger.PrimeTweaks.FORCE_SNOW));
            row(button(ID_ICON_PACKS, IconBackgroundColors.RED, R.drawable.msg_photos,
                    "Наборы иконок", iconPackSummary()));
            row(check(ID_NON_ISLAND_UI, IconBackgroundColors.GRAY, R.drawable.msg_colors,
                    "Классический плоский вид", org.telegram.messenger.NonIslandHelper.isEnabled()));
            row(check(ID_NAVIGATION_DRAWER, IconBackgroundColors.GRAY, R.drawable.menu_sidebar_left,
                    "Боковое меню вместо вкладок снизу", org.telegram.messenger.DrawerHelper.isEnabled()));
            if (!org.telegram.messenger.DrawerHelper.isEnabled()) {
                row(check(ID_MAIN_TABS_COMPACT, IconBackgroundColors.GRAY, R.drawable.msg_list,
                        "Компактные вкладки снизу", org.telegram.messenger.MainTabsHelper.isCompact()));
                row(check(ID_MAIN_TABS_HIDE, IconBackgroundColors.GRAY, R.drawable.msg_archive_hide,
                        "Скрыть вкладки снизу", org.telegram.messenger.MainTabsHelper.isHidden()));
            }
            endCard(items);
            if (avatarCornersCell() != null) {
                items.add(UItem.asCustom(ID_AVATAR_CORNERS, avatarCornersCell()));
            }
            items.add(info(1, "Оформление",
                    "Форма аватарок меняется сразу и везде.",
                    "Снегопад и новогодняя шапка у заголовка — те же, что Telegram показывает 31 декабря, только без привязки к дате.\n\nЗаголовок центрируется лишь когда для этого есть место: если название длинное и наехало бы на кнопки, оно остаётся слева.\n\nФорма аватарок меняется прямо во время перетаскивания и сразу везде — в списке чатов, в шапке чата, в профиле. Круги, которые рисует не аватарка, а что-то другое — кружочки-видео, значки — остаются кругами.\n\nСкругление задаётся долей, а не числом точек: поэтому на маленькой аватарке оно выглядит так же, как на большой, и в примере показаны сразу четыре размера.\n\n«Классический плоский вид» откатывает недавний «island»-редизайн (скруглённые плавающие панели, стеклянные эффекты) обратно к плоскому виду прежних версий Telegram — панель ввода, вкладки, шапки чатов и списка чатов. Открытые экраны обновляются при следующем открытии."));
        }

        if (section == SECTION_UI_REACTIONS) {
            row(tweak(ID_HIDE_REACTIONS_CHANNELS, IconBackgroundColors.BLUE, R.drawable.msg_channel,
                    "Скрыть в каналах", org.telegram.messenger.PrimeTweaks.HIDE_REACTIONS_CHANNELS));
            row(tweak(ID_HIDE_REACTIONS_GROUPS, IconBackgroundColors.GREEN, R.drawable.msg_groups,
                    "Скрыть в группах", org.telegram.messenger.PrimeTweaks.HIDE_REACTIONS_GROUPS));
            row(tweak(ID_HIDE_REACTIONS_PRIVATE, IconBackgroundColors.ORANGE, R.drawable.msg_contacts,
                    "Скрыть в личных чатах", org.telegram.messenger.PrimeTweaks.HIDE_REACTIONS_PRIVATE));
            endCard(items);
            items.add(UItem.asShadow("Реакции перестают рисоваться под сообщениями выбранного типа чатов. Ставить свои реакции через меню сообщения по-прежнему можно."));
        }

        if (section == SECTION_UI_FORMAT) {
            row(check(ID_RELATIVE_LAST_SEEN, IconBackgroundColors.BLUE, R.drawable.msg_contacts_time,
                    "«5 минут назад» вместо времени",
                    org.telegram.messenger.PrimeTweaks.get(org.telegram.messenger.PrimeTweaks.RELATIVE_LAST_SEEN)));
            row(check(ID_NO_NUMBER_ROUNDING, IconBackgroundColors.GREEN, R.drawable.msg_stats,
                    "Не округлять числа",
                    org.telegram.messenger.PrimeTweaks.get(org.telegram.messenger.PrimeTweaks.DISABLE_NUMBER_ROUNDING)));
            row(check(ID_TIME_WITH_SECONDS, IconBackgroundColors.CYAN, R.drawable.msg_calendar2,
                    "Показывать секунды во времени",
                    org.telegram.messenger.PrimeTweaks.get(org.telegram.messenger.PrimeTweaks.TIME_WITH_SECONDS)));
            endCard(items);
            items.add(UItem.asShadow("«Был(а) 5 минут назад» вместо метки времени — только для последних суток, дальше точная дата понятнее. Числа подписчиков и просмотров показываются полностью: 1 234 567 вместо 1M. Секунды добавляются везде, где показывается время, с сохранением 12- или 24-часового формата вашей локали."));
        }

        if (section == SECTION_UI_PROFILE) {
            row(tweak(ID_SHOW_ID_AND_DC, IconBackgroundColors.BLUE, R.drawable.msg_info,
                    "Показывать ID и дата-центр", org.telegram.messenger.PrimeTweaks.SHOW_ID_AND_DC));
            endCard(items);
            items.add(UItem.asShadow("Строка с числовым ID собеседника, канала или группы — по нажатию копируется. Дата-центр показывается только когда у собеседника есть аватар: узнать его больше неоткуда."));
        }

        if (section == SECTION_UI_FEED) {
            row(check(ID_FEED_HIDDEN, IconBackgroundColors.GRAY, R.drawable.msg_menu_stories,
                    "Скрыть вкладку «Лента»", preferences.getBoolean("primegram_feed_hidden", false)));
            endCard(items);
            items.add(UItem.asShadow("Убирает вкладку из нижней панели целиком. Остальные настройки ниже действуют, только пока лента показана."));

            row(check(ID_FEED_EXCLUDE_MUTED, IconBackgroundColors.ORANGE, R.drawable.msg_mute,
                    "Скрывать чаты без уведомлений",
                    preferences.getBoolean("primegram_feed_exclude_muted", false)));
            row(check(ID_FEED_EXCLUDE_ARCHIVED, IconBackgroundColors.GRAY, R.drawable.msg_archive,
                    "Скрывать чаты из архива",
                    preferences.getBoolean("primegram_feed_exclude_archived", false)));
            endCard(items);
            items.add(UItem.asShadow("Настройки отображения каналов и групп во вкладке Лента."));
        }

        if (section == SECTION_CONNECTION) {
            boolean vlessRunning = VpnSDK.isProxyRunning();
            row(check(ID_EMERGENCY_PROXY, IconBackgroundColors.PURPLE, R.drawable.msg_secret,
                    "Включить VLESS-сервер", vlessRunning));
            row(button(ID_VLESS_CUSTOM_KEY, IconBackgroundColors.PURPLE, R.drawable.msg_link2,
                    "Серверы", org.telegram.messenger.PrimeVpnServerStore.getServers().size() + " добавлено"));
            endCard(items);
            items.add(UItem.asShadow(vlessKeyStatusText(vlessRunning) + " В случае проблем с основным прокси, вы можете включить аварийный VLESS-прокси для обхода блокировок. Сервер работает локально на 127.0.0.2:17808"));

            row(check(ID_TGWS_PROXY, IconBackgroundColors.GREEN, R.drawable.msg_satellite,
                    "Включить TgWs-сервер", preferences.getBoolean("primegram_tgws_enabled", true)));
            row(button(ID_TGWS_SETTINGS, IconBackgroundColors.CYAN, R.drawable.msg_settings,
                    "Настройки сервера", tgWsSummary()));
            endCard(items);
            items.add(UItem.asShadow("Локальный SOCKS5-сервер, через который приложение ходит в сеть в обход блокировок. Домен подключения, порт и журнал — на отдельном экране."));

            boolean batteryOptOk = AndroidUtilities.isIgnoringBatteryOptimizations();
            row(button(ID_BATTERY_OPTIMIZATION, IconBackgroundColors.ORANGE, R.drawable.msg_speed,
                    "Отключить оптимизацию батареи",
                    batteryOptOk ? "Разрешено" : "Не разрешено"));
            endCard(items);
            items.add(UItem.asShadow("На некоторых прошивках (MIUI, OneUI и т.п.) система агрессивно закрывает фоновые процессы, из-за чего прокси отключается и уведомления приходят с задержкой. Разрешение \"Без ограничений\" для батареи устраняет эту проблему."));

            final boolean vpnGuardOn = org.telegram.messenger.PrimeVpnGuard.isEnabled();
            row(check(ID_VPN_GUARD, IconBackgroundColors.CYAN, R.drawable.msg_secret,
                    "Отключать прокси при включённом VPN", vpnGuardOn));
            if (vpnGuardOn) {
                final int count = org.telegram.messenger.PrimeVpnGuard.getWhitelist().size();
                row(button(ID_VPN_GUARD_WHITELIST, IconBackgroundColors.CYAN, R.drawable.msg_contacts,
                        "Не трогать для...", count == 0 ? "не выбрано" : count + " прилож."));
            }
            endCard(items);
            items.add(UItem.asShadow("Прокси выключается сам, пока активен системный VPN, и включается обратно, когда VPN пропадает — держать оба сразу обычно бессмысленно. В списке исключений можно отметить VPN-приложения, при которых прокси трогать не нужно; Android по соображениям приватности не всегда сообщает, какое именно VPN-приложение сейчас активно — если он не сказал, решает общий переключатель выше."));
        }

        if (section == SECTION_PRIVACY) {
            String fake = org.telegram.messenger.PrimeGramPrivacy.getFakePhone();
            row(check(ID_HIDE_PHONE, IconBackgroundColors.RED, R.drawable.msg_secret,
                    "Скрывать свой номер в профиле",
                    org.telegram.messenger.PrimeGramPrivacy.isHidePhoneEnabled()));
            row(button(ID_FAKE_PHONE, IconBackgroundColors.ORANGE, R.drawable.msg_newphone,
                    "Свой номер для показа", fake.isEmpty() ? "не задан" : fake));
            endCard(items);
            items.add(UItem.asShadow("Меняет только то, что показано на вашем экране — удобно для скриншотов. Номер на сервере и у собеседников не меняется."));

            if (org.telegram.messenger.GreyZone.isDebugVisible()) {
                row(button(ID_GREY_ZONE, IconBackgroundColors.GRAY, R.drawable.msg_warning, "Серая зона",
                        org.telegram.messenger.GreyZone.isAccepted() ? "Включена" : "Требует подтверждения"));
                endCard(items);
                items.add(UItem.asShadow("Функции, снимающие ограничения собеседника, и режим призрака. Разработчик их не одобряет — используются на ваш страх и риск."));
            }
        }

        if (section == SECTION_TOOLS) {
            items.add(section(SECTION_TOOLS_MAIN, IconBackgroundColors.BLUE, R.drawable.msg_customize,
                    "Инструменты", "Форматирование, ссылки, поиск, боты"));
            items.add(section(SECTION_TOOLS_TAGS, IconBackgroundColors.ORANGE, R.drawable.msg_pin,
                    "Теги сообщений", "Пометки, видимые только вам"));
            items.add(section(SECTION_TOOLS_STT, IconBackgroundColors.GREEN, R.drawable.msg_tabs_mic1,
                    "Расшифровка голосовых", "Без Premium: на устройстве или через сервис"));
            items.add(section(SECTION_TOOLS_BROWSER, IconBackgroundColors.CYAN, R.drawable.msg_instant,
                    "Встроенный браузер", "Блокировка рекламы, свой DNS"));
            items.add(UItem.asShadow(null));
        }

        if (section == SECTION_TOOLS_TAGS) {
            row(button(ID_MESSAGE_TAGS, IconBackgroundColors.ORANGE, R.drawable.msg_pin,
                    "Помеченные сообщения", String.valueOf(org.telegram.messenger.MessageTagsStore.count())));
            endCard(items);
            items.add(UItem.asShadow("Задержите сообщение в чате и выберите «Пометить тегом». Тег виден прямо на сообщении в чате и хранится только на этом устройстве — собеседник его не видит."));
        }

        if (section == SECTION_TOOLS_MAIN) {
            row(check(ID_TEXT_TOOLBAR, IconBackgroundColors.BLUE, R.drawable.msg_photo_text2,
                    "Панель форматирования", org.telegram.messenger.PrimeToolbarSettings.isEnabled()));
            if (org.telegram.messenger.PrimeToolbarSettings.isEnabled()) {
                row(button(ID_TOOLBAR_BUTTONS, IconBackgroundColors.BLUE_LIGHT, R.drawable.msg_select,
                        "Кнопки панели", toolbarSummary()));
            }
            endCard(items);
            items.add(UItem.asShadow("Ряд кнопок над полем ввода: жирный, курсив, моноширинный, зачёркнутый, подчёркнутый, спойлер, ссылка, цитата, сброс форматирования и копирование. Появляется, когда в поле ввода что-то выделено, и заменяет собой системное меню выделения — иначе два ряда кнопок спорили бы за одно и то же место."));

            row(check(ID_LINK_PREVIEW, IconBackgroundColors.CYAN, R.drawable.msg_link,
                    "Предпросмотр ссылок", org.telegram.messenger.PrimeLinkPreviewSettings.isEnabled()));
            endCard(items);
            items.add(UItem.asShadow("Задержите ссылку в чате — страница откроется в маленьком окне. Тап по окну открывает её во встроенном браузере. Учтите: страница загружается по-настоящему, то есть тратит трафик и сайт узнаёт о посещении."));

            row(button(ID_SEARCH_PLUS, IconBackgroundColors.GREEN, R.drawable.msg_usersearch,
                    "Поиск+", "ID, телефон, ссылка"));
            row(button(ID_TEMP_SUBS, IconBackgroundColors.ORANGE, R.drawable.msg_autodelete,
                    "Временные подписки",
                    String.valueOf(org.telegram.messenger.TempSubStore.getAll().size())));
            endCard(items);
            items.add(UItem.asShadow("Поиск+ находит профиль по числовому ID, номеру телефона, @username или ссылке t.me — там, где обычный поиск отказывается искать.\n\nВременная подписка отписывает от канала сама, через выбранный срок от часа до месяца. Включается в меню самого канала."));

            row(button(ID_BOT_LOGIN, IconBackgroundColors.PURPLE, R.drawable.msg_bot,
                    "Вход в бота", "по токену"));
            endCard(items);
            items.add(UItem.asShadow("Вход в аккаунт бота по токену BotFather. Бот занимает отдельный слот аккаунта — сессия бота отдельна от вашей, это устройство протокола Telegram."));
        }

        if (section == SECTION_TOOLS_STT) {
            boolean hasPremium = org.telegram.messenger.UserConfig.getInstance(currentAccount).hasRealPremium();
            {
                row(check(ID_WHISPER_ENABLED, IconBackgroundColors.GREEN, R.drawable.msg_tabs_mic1,
                        "Расшифровывать на устройстве", org.telegram.messenger.PrimeWhisper.isEnabled()));
                // Offered to Premium accounts too. The assumption used to be that Telegram's own
                // transcription makes this pointless for them; in practice the local model reads
                // some voices better, and someone who has noticed that should be able to choose
                // without giving up their subscription.
                if (hasPremium && org.telegram.messenger.PrimeWhisper.isEnabled()) {
                    row(check(ID_WHISPER_PREFER, IconBackgroundColors.PURPLE, R.drawable.msg_tabs_mic1,
                            "Вместо расшифровки Telegram",
                            org.telegram.messenger.PrimeWhisper.preferOverPremium()));
                }
                if (org.telegram.messenger.PrimeWhisper.isEnabled()) {
                    final int model = org.telegram.messenger.PrimeWhisper.getModel();
                    row(button(ID_WHISPER_MODEL, IconBackgroundColors.BLUE, R.drawable.msg_download_settings,
                            "Модель", org.telegram.messenger.PrimeWhisper.MODEL_NAMES[model]));
                    row(button(ID_WHISPER_DOWNLOAD, IconBackgroundColors.CYAN, R.drawable.msg_download,
                            org.telegram.messenger.PrimeWhisper.isModelDownloaded(model) ? "Удалить модель" : "Загрузить модель",
                            org.telegram.messenger.PrimeWhisper.MODEL_DESCRIPTIONS[model], null));
                    row(button(ID_WHISPER_LANGUAGE, IconBackgroundColors.ORANGE, R.drawable.msg_language,
                            "Язык записи", whisperLanguageName()));
                }
                endCard(items);
                items.add(UItem.asShadow("Распознавание идёт прямо на телефоне: запись никуда не отправляется и работает без сети. Взамен нужно один раз скачать модель и подождать — на слабом телефоне минута речи разбирается заметно дольше, чем на сервере.\n\nЭтот переключатель и внешний сервис исключают друг друга: включение одного выключает другой."
                        + (hasPremium ? "\n\nУ вас есть Telegram Premium, поэтому расшифровка и так работает родными средствами — мгновенно и без скачивания модели. Переключатель выше нужен, если качество на устройстве вас устраивает больше: на некоторых голосах локальная модель разбирает речь точнее." : "")));

                row(check(ID_STT_ENABLED, IconBackgroundColors.PURPLE, R.drawable.msg_satellite,
                        "Расшифровывать через внешний сервис", org.telegram.messenger.PrimeTranscription.isEnabled()));
                if (org.telegram.messenger.PrimeTranscription.isEnabled()) {
                    String token = org.telegram.messenger.PrimeTranscription.getToken();
                    row(button(ID_STT_TOKEN, IconBackgroundColors.RED, R.drawable.msg_secret,
                            "Ключ сервиса", android.text.TextUtils.isEmpty(token) ? "не задан" : "задан"));
                    row(button(ID_STT_ENDPOINT, IconBackgroundColors.BLUE, R.drawable.msg_link,
                            "Адрес сервиса", org.telegram.messenger.PrimeTranscription.getEndpoint()));
                    row(button(ID_STT_MODEL, IconBackgroundColors.GRAY, R.drawable.msg_download_settings,
                            "Модель", org.telegram.messenger.PrimeTranscription.getModel()));
                    row(check(ID_STT_SMART_DNS, IconBackgroundColors.GREEN, R.drawable.msg_language,
                            "Обход блокировки по стране",
                            org.telegram.messenger.PrimeTranscription.isSmartDnsEnabled()));
                }
                endCard(items);
                items.add(UItem.asShadow("Telegram отдаёт расшифровку только по Premium. Эта настройка отправляет голосовое во внешний сервис и подставляет ответ на место родной расшифровки.\n\nПо умолчанию — Groq: бесплатный тариф без карты, около 2000 расшифровок в сутки, ключ берётся на console.groq.com. Подойдёт любой сервис с совместимым API (OpenAI, Cloudflare, свой сервер) — впишите его адрес и модель.\n\nПонимайте, на что соглашаетесь: голосовое уходит на сервер, который не принадлежит ни Telegram, ни нам. Поэтому выключено по умолчанию и включается руками.\n\n«Обход блокировки по стране» нужен, если сервис отвечает отказом всем адресам вашей страны. Тогда адрес сервиса ищется через сторонний резолвер (xbox-dns.ru), который отвечает адресом своего шлюза, и запрос идёт через него. Соединение остаётся зашифрованным от начала до конца — шлюз видит только поток байтов, — но в пути появляется ещё один посредник, и знать об этом стоит. Затрагивается ровно этот запрос: весь остальной трафик клиента идёт как шёл."));
            }
        }

        if (section == SECTION_TOOLS_BROWSER) {
            row(check(ID_ADBLOCK, IconBackgroundColors.RED, R.drawable.msg_block2,
                    "Блокировать рекламу и трекеры", org.telegram.messenger.browser.PrimeAdBlock.isEnabled()));
            endCard(items);
            items.add(UItem.asShadow("Режет запросы к рекламным и следящим доменам во встроенном браузере. Главная страница сайта не блокируется никогда — только её содержимое, поэтому ошибка в списке может стоить картинки, но не самого сайта. Заблокировано за сеанс: "
                    + org.telegram.messenger.browser.PrimeAdBlock.getBlockedCount() + "."));

            if (org.telegram.messenger.browser.PrimeAdBlock.isEnabled()) {
                items.add(UItem.asHeader("Списки блокировки"));
                for (int i = 0; i < org.telegram.messenger.browser.PrimeAdBlockLists.LIST_IDS.length; i++) {
                    row(check(ID_ADBLOCK_LIST_BASE + i, IconBackgroundColors.GRAY, R.drawable.msg_list,
                            org.telegram.messenger.browser.PrimeAdBlockLists.LIST_NAMES[i],
                            org.telegram.messenger.browser.PrimeAdBlockLists.LIST_DESCRIPTIONS[i],
                            org.telegram.messenger.browser.PrimeAdBlockLists.isListEnabled(i)));
                }
                row(button(ID_ADBLOCK_UPDATE, IconBackgroundColors.BLUE, R.drawable.msg_download,
                        "Обновить списки", adBlockListsStatus()));
                endCard(items);
                items.add(UItem.asShadow("Те же списки, на которые подписаны AdGuard и uBlock Origin. Из них берутся только правила вида «весь домен целиком» — наш блокировщик видит имя хоста и ничего больше, поэтому правила по адресу страницы и правила, прячущие пустые блоки, пропускаются, а не применяются наполовину.\n\nСписки скачиваются напрямую, мимо нашего туннеля: туннель возит протокол Telegram и только его. Если до серверов списков не достучаться — так и будет написано."));
            }

            items.add(UItem.asHeader("DNS"));
            row(check(ID_DNS_ENABLED, IconBackgroundColors.CYAN, R.drawable.msg_satellite,
                    "Свой DNS (DNS-over-HTTPS)", org.telegram.messenger.browser.PrimeDns.isEnabled()));
            if (org.telegram.messenger.browser.PrimeDns.isEnabled()) {
                row(button(ID_DNS_PRESET, IconBackgroundColors.BLUE, R.drawable.msg_link,
                        "DNS-сервер", org.telegram.messenger.browser.PrimeDns.currentName()));
                row(check(ID_ADBLOCK_DNS, IconBackgroundColors.GREEN, R.drawable.msg_policy,
                        "Доверять вердикту DNS-сервера",
                        org.telegram.messenger.browser.PrimeAdBlock.isDnsBlockingEnabled()));
            }
            endCard(items);
            items.add(UItem.asShadow("Запросы имён идут в зашифрованном виде мимо DNS провайдера — это самый дешёвый способ блокировки, и он так обходится. По умолчанию стоит AdGuard DNS: он сам отвечает «никуда» на рекламные домены, поэтому служит ещё и списком блокировки, который не надо обновлять вручную. Можно указать свой адрес — только https."));
        }

        if (section == SECTION_MEDIA) {
            items.add(section(SECTION_MEDIA_SEND, IconBackgroundColors.BLUE, R.drawable.msg_send,
                    "Отправка и сохранение", "Без сжатия, кружочки и голосовые"));
            items.add(section(SECTION_MEDIA_QUALITY, IconBackgroundColors.GREEN, R.drawable.msg_video,
                    "Качество и загрузка", "Лимит качества видео, автозагрузка, кэш"));
            items.add(section(SECTION_MEDIA_CAMERA, IconBackgroundColors.ORANGE, R.drawable.msg_camera,
                    "Камера", "Кружочки и Camera2 API"));
            items.add(section(SECTION_MEDIA_TRANSLATE, IconBackgroundColors.PURPLE, R.drawable.msg_translate,
                    "Перевод", "Чем переводить сообщения"));
            items.add(section(SECTION_MEDIA_MUSIC, IconBackgroundColors.CYAN, R.drawable.msg_filled_data_music,
                    "Музыка", "Отправка текущего трека"));
            items.add(UItem.asShadow(null));
        }

        if (section == SECTION_MEDIA_SEND) {
            row(tweak(ID_SEND_UNCOMPRESSED, IconBackgroundColors.BLUE, R.drawable.msg_filehq,
                    "Отправлять без сжатия", org.telegram.messenger.PrimeTweaks.SEND_UNCOMPRESSED));
            row(tweak(ID_SAVE_ROUND_VOICE, IconBackgroundColors.GREEN, R.drawable.msg_saved,
                    "Сохранять кружочки и голосовые", org.telegram.messenger.PrimeTweaks.SAVE_ROUND_AND_VOICE));
            endCard(items);
            items.add(UItem.asShadow("Отправка без сжатия переключает главную кнопку в режим «файлом» — тот же, что в меню вложений. На контакты, музыку и геопозицию это не влияет: для них «файлом» ничего не значит.\n\nСохранение кружочков и голосовых добавляет пункт в меню долгого нажатия: кружочек уходит в галерею, голосовое — в загрузки. Одноразовые сообщения не сохраняются: отправитель выбрал исчезающее сообщение, и обходить это мы не будем."));

            row(check(ID_BIGFILE, IconBackgroundColors.ORANGE, R.drawable.msg_sendfile,
                    "Отправка больших файлов",
                    org.telegram.messenger.PrimeBigFile.isSendingEnabled()));
            if (org.telegram.messenger.PrimeBigFile.isSendingEnabled()) {
                row(check(ID_BIGFILE_EXPERIMENTAL, IconBackgroundColors.RED, R.drawable.msg_limit_links,
                        "До 50 ГБ (эксперимент)",
                        org.telegram.messenger.PrimeBigFile.isExperimentalEnabled()));
            }
            endCard(items);
            items.add(UItem.asShadow("Файл больше лимита Telegram отправляется частями, а PrimeGram на другой стороне собирает его обратно — получатель видит один файл с обычным именем и прогрессом.\n\nПолучать такие файлы могут все и всегда, разрешение нужно только чтобы отправлять. Части уходят с паузами, поэтому восемь гигабайт — это надолго, и на мобильной сети лучше не начинать.\n\nУ кого нет PrimeGram, увидит несколько файлов с пометкой в имени: «часть 3 из 17». Собрать их можно вручную любым архиватором."));
            if (org.telegram.messenger.PrimeBigFile.isSendingEnabled()
                    && org.telegram.messenger.PrimeBigFile.isExperimentalEnabled()) {
                items.add(UItem.asShadow("Пятьдесят гигабайт — это больше сотни частей и часы отправки. Telegram может ограничить аккаунт за объём, а файловая система телефона может не принять такой файл на приёме. Включайте, если понимаете, зачем."));
            }
        }

        if (section == SECTION_MEDIA_QUALITY) {
            items.add(UItem.asHeader("Качество видео"));
            if (videoQualityCards() != null) {
                items.add(UItem.asCustom(ID_VIDEO_QUALITY_CARDS, videoQualityCards()));
            }
            row(check(ID_PRELOAD_VIDEO_MOBILE, IconBackgroundColors.ORANGE, R.drawable.msg_download,
                    "Догружать видео на мобильной сети",
                    org.telegram.messenger.DownloadController.getInstance(currentAccount).primeMobilePreloadVideo()));
            row(button(ID_AUTODOWNLOAD, IconBackgroundColors.BLUE, R.drawable.msg_download_settings,
                    "Автозагрузка медиа", null));
            row(button(ID_CACHE, IconBackgroundColors.GRAY, R.drawable.msg_clearcache,
                    "Кэш медиа", cacheSizeText()));
            endCard(items);
            items.add(info(5, "Качество и загрузка",
                    "Ограничение качества экономит трафик, а не только пиксели.",
                    "Качество видео ограничивает то, что скачивается, а не только то, что играет: скачивается ровно та дорожка, которую выбирает плеер. Если ни одна не помещается в лимит, берётся обычная — лимит не должен оставить видео непроигрываемым. Уже скачанное не перекачивается заново, даже если оно крупнее лимита. Настройка применяется к сообщениям, открытым после её изменения.\n\nВыключенная догрузка на мобильной сети переводит автозагрузку в режим «Свой» — иначе правка задела бы заодно Wi-Fi и роуминг, у которых с готовыми пресетами общий объект. Остальные значения при этом переносятся как были.\n\nКэш — только скачанное для просмотра. Файлы, которые вы сами сохранили в загрузки или галерею, кнопка не трогает."));
        }

        if (section == SECTION_MEDIA_CAMERA) {
            row(tweak(ID_ROUND_VIDEO_REAR, IconBackgroundColors.ORANGE, R.drawable.msg_camera,
                    "Кружочки с основной камеры", org.telegram.messenger.PrimeTweaks.ROUND_VIDEO_REAR));
            row(check(ID_CAMERA2, IconBackgroundColors.BLUE, R.drawable.msg_photo_settings,
                    "Camera2 API", org.telegram.messenger.SharedConfig.isUsingCamera2(currentAccount)));
            endCard(items);
            items.add(UItem.asShadow("Обычно кружочки всегда начинаются с фронтальной камеры, и переключение стоит нажатия и заметного перезапуска картинки.\n\nCamera2 — более новый интерфейс камеры Android: лучше автофокус и экспозиция, но на части прошивок он работает хуже старого. Переключатель есть и в отладочном меню Telegram, здесь он просто на виду. Важно: на съёмку фото и видео он сейчас не влияет — в самом Telegram Camera2 для основной камеры отключён в коде, — так что меняет он поведение только кружочков."));
        }

        if (section == SECTION_MEDIA_TRANSLATE) {
            if (translatorCards() != null) {
                items.add(UItem.asCustom(ID_TRANSLATOR_CARDS, translatorCards()));
            }
            items.add(info(3, "Перевод",
                    "Google и Yandex не требуют Premium, но теряют форматирование.",
                    "Перевод через Telegram идёт по тому же соединению, что и всё остальное, и подчиняется ограничениям аккаунта.\n\nGoogle и Yandex работают по обычной сети — это выручает, когда туннель тормозит, и не требует Premium. Сети у них разные, так что если один недоступен, стоит попробовать другой.\n\nВзамен они теряют форматирование: жирный шрифт, ссылки и упоминания в переведённом тексте пропадут. Поэтому по умолчанию стоит Telegram.\n\nСтатьи Instant View переводятся через Telegram в любом случае — там перевод возвращает не текст, а свёрстанную страницу."));
        }

        if (section == SECTION_MEDIA_MUSIC) {
            row(button(ID_MUSIC_SETTINGS, IconBackgroundColors.CYAN, R.drawable.msg_filled_data_music,
                    "Настройки вкладки «Музыка»",
                    org.telegram.messenger.music.MusicSettingsStore.isTabEnabled() ? "Включена" : "Выключена"));
            endCard(items);
            items.add(UItem.asShadow("Отправка текущего трека (Spotify, Яндекс Музыка, SoundCloud, VK, Last.fm, Telegram) карточкой, аудиофайлом или текстом. Вкладка появляется в панели эмодзи."));
        }

        if (section == SECTION_ADVANCED) {
            items.add(UItem.asHeader("Обновления приложения"));
            row(check(ID_AUTO_UPDATES, IconBackgroundColors.GREEN, R.drawable.msg_download,
                    "Автоматически скачивать обновления",
                    preferences.getBoolean("primegram_auto_updates", true)));
            row(button(ID_CHECK_UPDATES, IconBackgroundColors.BLUE, R.drawable.msg_retry,
                    "Проверить обновления", null));
            endCard(items);
            items.add(UItem.asShadow("PrimeGram может автоматически проверять релизы на GitHub и скачивать новые версии."));

            items.add(UItem.asHeader("Быстродействие"));
            row(tweak(ID_OPTIMIZATIONS, IconBackgroundColors.ORANGE, R.drawable.msg_speed,
                    "Оптимизации PrimeGram", org.telegram.messenger.PrimeTweaks.OPTIMIZATIONS));
            endCard(items);
            items.add(info(7, "Оптимизации PrimeGram",
                    "Ускоряют работу ценой памяти и фоновых действий.",
                    "Сюда входят: подготовка вкладок «Профиль» и «Настройки» заранее, чтобы переход к ним был мгновенным; прогрев соединений туннеля при возврате в приложение, чтобы не ждать рукопожатие; увеличенный запас соединений для медиа, чтобы лента историй не открывалась по одной картинке.\n\nКаждая из них меняет память или фоновую работу на скорость. На большинстве устройств это выгодный обмен, но если приложение стало нестабильным или телефон греется — выключите и посмотрите, станет ли лучше. Это честнее, чем откатываться на старую сборку.\n\nК оптимизации батареи Android эта настройка отношения не имеет: та живёт в системных разрешениях и включается кнопкой в разделе «Соединение»."));

            items.add(UItem.asHeader("Диагностика"));
            row(check(ID_LOGS_ENABLED, IconBackgroundColors.GRAY, R.drawable.msg_log,
                    "Подробные логи", org.telegram.messenger.BuildVars.LOGS_ENABLED));
            row(button(ID_STARTUP_TRACE, IconBackgroundColors.BLUE_DEEP, R.drawable.msg_stats,
                    "Трасса запуска", "диагностика"));
            row(button(ID_BATTERY_DIAG, IconBackgroundColors.GREEN, R.drawable.msg2_battery,
                    "Энергопотребление", "экспорт и отправка"));
            row(button(ID_PUSH_STATUS, IconBackgroundColors.ORANGE, R.drawable.msg_notifications,
                    "Состояние уведомлений", primePushSummary()));
            endCard(items);
            items.add(info(6, "Подробные логи",
                    "Нужны только когда мы просим трассировку запуска.",
                    "Telegram пишет в лог очень много, и каждая строка форматируется в том потоке, который её отправил, — включая главный. Постоянно включённые логи заметно замедляют работу и занимают место.\n\nВключайте, когда нужно снять трассировку запуска или разобраться с ошибкой, и выключайте после. Трассировка PrimeGram пишется в тот же лог, поэтому без этой настройки её не будет.\n\nСама трасса показывает, сколько миллисекунд занял каждый этап последнего холодного старта: загрузка нативных библиотек, открытие базы, появление списка чатов."));

            items.add(UItem.asHeader("Экспериментальные настройки"));
            row(check(ID_HW_ACCEL, IconBackgroundColors.RED, R.drawable.msg_maxvideo,
                    "Аппаратное ускорение видео (MediaCodec)",
                    org.telegram.messenger.CrashSafeToggle.isEnabled("primegram_hw_accel")));
            row(button(ID_HW_BENCHMARK, IconBackgroundColors.PURPLE, R.drawable.msg_stats,
                    "Стресс-тест аппаратного ускорения", null));
            endCard(items);
            if (org.telegram.messenger.CrashSafeToggle.wasAutoDisabled("primegram_hw_accel")) {
                items.add(UItem.asShadow("Отключено автоматически: при последнем запуске с этой опцией приложение аварийно завершилось. Попробуйте включить снова — если проблема повторится на этом устройстве, лучше оставить выключенным."));
                org.telegram.messenger.CrashSafeToggle.acknowledgeAutoDisabled("primegram_hw_accel");
            } else {
                items.add(UItem.asShadow("Включает аппаратное декодирование видео/GIF/кружочков вместо программного. Может немного сэкономить батарею, но на некоторых устройствах декодер бывает нестабилен — приложение автоматически откатит настройку, если из-за неё случится сбой. Изменения применяются после перезапуска приложения.\n\nСтресс-тест декодирует одно и то же видео из кэша двумя путями подряд и показывает, сколько времени и процессора ушло на каждый. Работает независимо от настройки выше."));
            }
        }

        if (section == SECTION_PREMIUM) {
            items.add(UItem.asShadow((org.telegram.messenger.GreyZone.localPremiumEnabled()
                    ? "Сейчас включено: расширение функционала на этом устройстве — бесконечные реакции, эмодзи-статусы, значок в профиле и увеличенные лимиты ниже. "
                    : "Сейчас выключено — сервер по-прежнему не считает вас Premium-пользователем. ")
                    + "Включается в «Серой зоне»."));

            items.add(UItem.asHeader("Лимиты чатов и папок"));
            row(button(ID_LIMIT_FOLDERS, IconBackgroundColors.BLUE, R.drawable.msg_limit_folder,
                    "Максимальное количество папок", String.valueOf(messagesController.dialogFiltersLimitPremium)));
            row(button(ID_LIMIT_PINNED_FOLDER, IconBackgroundColors.CYAN, R.drawable.msg_limit_pin,
                    "Закрепленные чаты в папке", String.valueOf(messagesController.dialogFiltersPinnedLimitPremium)));
            row(button(ID_LIMIT_PINNED_SAVED, IconBackgroundColors.ORANGE, R.drawable.msg_saved,
                    "Закрепленные чаты в Избранном", String.valueOf(messagesController.savedDialogsPinnedLimitPremium)));
            row(button(ID_LIMIT_CHATS_IN_FOLDER, IconBackgroundColors.GREEN, R.drawable.msg_limit_chats,
                    "Максимально чатов в папке", String.valueOf(messagesController.dialogFiltersChatsLimitPremium)));
            row(button(ID_LIMIT_CHANNELS, IconBackgroundColors.PURPLE, R.drawable.msg_limit_groups,
                    "Лимит каналов и супергрупп", String.valueOf(messagesController.channelsLimitPremium)));
            endCard(items);
            items.add(UItem.asShadow("Увеличенные лимиты для структуры ваших переписок и папок."));

            items.add(UItem.asHeader("Лимиты медиа и стикеров"));
            row(button(ID_LIMIT_GIFS, IconBackgroundColors.BLUE, R.drawable.msg_gif,
                    "Лимит сохраненных GIF", String.valueOf(messagesController.savedGifsLimitPremium)));
            row(button(ID_LIMIT_STICKERS, IconBackgroundColors.ORANGE, R.drawable.msg_fave,
                    "Лимит избранных стикеров", String.valueOf(messagesController.stickersFavedLimitPremium)));
            row(button(ID_LIMIT_RECENT_STICKERS, IconBackgroundColors.CYAN, R.drawable.msg_recent,
                    "Лимит недавних стикеров", String.valueOf(messagesController.maxRecentStickersCount)));
            endCard(items);
            items.add(UItem.asShadow("Лимиты на количество гифок в панели отправки, избранных и недавних стикеров. Недавние стикеры обрезает сам клиент, поэтому это ограничение снимается полностью и без участия сервера."));

            items.add(UItem.asHeader("Лимиты профиля и текста"));
            row(button(ID_LIMIT_PUBLIC_LINKS, IconBackgroundColors.GREEN, R.drawable.msg_limit_links,
                    "Лимит публичных ссылок", String.valueOf(messagesController.publicLinksLimitPremium)));
            row(button(ID_LIMIT_CAPTION, IconBackgroundColors.BLUE_DEEP, R.drawable.msg_photo_text2,
                    "Лимит символов в описании медиа", String.valueOf(messagesController.captionLengthLimitPremium)));
            row(button(ID_LIMIT_ABOUT, IconBackgroundColors.GRAY, R.drawable.msg_addbio,
                    "Лимит символов в разделе «О себе»", String.valueOf(messagesController.aboutLengthLimitPremium)));
            endCard(items);
            items.add(UItem.asShadow("Символьные ограничения для описания медиафайлов и био вашего аккаунта."));
        }

        if (section == SECTION_ABOUT) {
            row(button(ID_GRANT_PERMISSIONS, IconBackgroundColors.BLUE, R.drawable.msg_permissions,
                    "Выдать системные разрешения", "Контакты, звонки, память, уведомления", null));
            row(button(ID_LOCKSCREEN_CALLS, IconBackgroundColors.GREEN, R.drawable.msg_calls,
                    "Звонки на заблокированном экране", primeLockScreenCallsStatus()));
            endCard(items);
            items.add(UItem.asShadow("PrimeGram не спрашивает разрешения сам: у оригинала они вываливаются на список чатов друг поверх друга и поверх системных окон, и их закрывают не читая. Здесь их выдаёте вы, когда сами этого захотели.\n\nБез второго пункта входящий звонок не покажет экран вызова, пока телефон заблокирован — придёт только уведомление."));

            row(button(ID_SUPPORT_PROJECT, IconBackgroundColors.PURPLE, R.drawable.msg_gift_premium,
                    "Поддержать проект", "USDT TON через @wallet", null));
            endCard(items);
            items.add(UItem.asShadow("Спасибо за вашу поддержку! Это помогает развивать PrimeGram."));
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id >= ID_SECTION_BASE) {
            presentFragment(new PrimeGramSettingsActivity(item.id - ID_SECTION_BASE));
            return;
        }
        if (item.id >= ID_ADBLOCK_LIST_BASE
                && item.id < ID_ADBLOCK_LIST_BASE + org.telegram.messenger.browser.PrimeAdBlockLists.LIST_IDS.length) {
            int index = item.id - ID_ADBLOCK_LIST_BASE;
            boolean nowEnabled = !org.telegram.messenger.browser.PrimeAdBlockLists.isListEnabled(index);
            org.telegram.messenger.browser.PrimeAdBlockLists.setListEnabled(index, nowEnabled);
            listView.adapter.update(true);
            if (nowEnabled) {
                // a list nobody downloaded blocks nothing, and that reads as a broken switch
                updateAdBlockLists();
            }
            return;
        }
        MessagesController messagesController = MessagesController.getInstance(currentAccount);
        String tweakKey = primeTweakKeyFor(item.id);
        if (tweakKey != null) {
            org.telegram.messenger.PrimeTweaks.set(tweakKey, !org.telegram.messenger.PrimeTweaks.get(tweakKey));
            // Every tweak, not a chosen few: the preview is a picture of the app, and a switch
            // that changes nothing in it simply redraws the same picture.
            updateLivePreview();
            if (item.id == ID_FORCE_SNOW) {
                // The holiday check only re-runs once a minute; without this the toggle looks
                // like it did nothing until the user waits it out.
                org.telegram.ui.ActionBar.Theme.primeInvalidateHoliday();
            }
            if (item.id == ID_SENDER_MINI_AVATARS) {
                // Previews are built once per bind and cached, so nothing changes until the
                // list is told to rebuild them.
                org.telegram.messenger.NotificationCenter.getGlobalInstance()
                        .postNotificationName(org.telegram.messenger.NotificationCenter.dialogsNeedReload, true);
            }
            if (item.id == ID_HIDE_ARCHIVE_FOLDER) {
                // Mutually exclusive with "archive opens by pull": that feature relies on the
                // archive folder staying the first row so it has something to pull, which this
                // one removes outright - together they left an empty placeholder row, or hid a
                // real chat, standing in for whichever the archive row wasn't found where expected.
                if (org.telegram.messenger.PrimeTweaks.get(org.telegram.messenger.PrimeTweaks.HIDE_ARCHIVE_FOLDER)
                        && org.telegram.messenger.SharedConfig.archiveHidden) {
                    org.telegram.messenger.SharedConfig.toggleArchiveHidden();
                }
                org.telegram.messenger.NotificationCenter.getGlobalInstance()
                        .postNotificationName(org.telegram.messenger.NotificationCenter.dialogsNeedReload, true);
            }
            if (item.id == ID_HIDE_ALL_CHATS) {
                // the tab strip is only rebuilt when the folder set changes, so say it did
                for (int a = 0; a < org.telegram.messenger.UserConfig.MAX_ACCOUNT_COUNT; a++) {
                    if (org.telegram.messenger.UserConfig.getInstance(a).isClientActivated()) {
                        org.telegram.messenger.NotificationCenter.getInstance(a)
                                .postNotificationName(org.telegram.messenger.NotificationCenter.dialogFiltersUpdated);
                    }
                }
            }
            listView.adapter.update(true);
        } else if (item.id == ID_STICKER_SIZE) {
            showStickerSizePicker();
        } else if (item.id == ID_DOUBLE_TAP) {
            showDoubleTapPicker();
        } else if (item.id == ID_TRANSLATE_PROVIDER) {
            showTranslateProviderPicker();
        } else if (item.id == ID_CACHE) {
            showCacheDialog();
        } else if (item.id == ID_VIDEO_QUALITY) {
            showVideoQualityPicker();
        } else if (item.id == ID_PRELOAD_VIDEO_MOBILE) {
            togglePreloadVideoOnMobile();
        } else if (item.id == ID_AUTODOWNLOAD) {
            // Straight to the stock screen rather than a copy of it here: the presets are one
            // stored value, and two screens writing it would eventually disagree.
            presentFragment(new org.telegram.ui.DataSettingsActivity());
        } else if (item.id == ID_WHISPER_PREFER) {
            org.telegram.messenger.PrimeWhisper.setPreferOverPremium(
                    !org.telegram.messenger.PrimeWhisper.preferOverPremium());
            listView.adapter.update(true);
        } else if (item.id == ID_WHISPER_ENABLED) {
            final boolean enabled = !org.telegram.messenger.PrimeWhisper.isEnabled();
            org.telegram.messenger.PrimeWhisper.setEnabled(enabled);
            if (!enabled) {
                // The loaded model is tens of megabytes of resident memory; switching the feature
                // off has to actually give it back.
                org.telegram.messenger.PrimeWhisper.release();
            } else if (org.telegram.messenger.PrimeTranscription.isEnabled()) {
                // Two transcribers at once is a state with no useful meaning: only one of them
                // can answer, so the other is a switch that is on and does nothing.
                org.telegram.messenger.PrimeTranscription.setEnabled(false);
                org.telegram.ui.Components.BulletinFactory.of(this)
                        .createSimpleBulletin(R.raw.info, "Внешний сервис выключен",
                                "Расшифровка работает в одном месте: на устройстве или на сервере").show();
            }
            listView.adapter.update(true);
        } else if (item.id == ID_WHISPER_MODEL) {
            showWhisperModelPicker();
        } else if (item.id == ID_WHISPER_DOWNLOAD) {
            toggleWhisperModel();
        } else if (item.id == ID_WHISPER_LANGUAGE) {
            showWhisperLanguagePicker();
        } else if (item.id == ID_DOUBLE_TAP_REACTION) {
            presentFragment(new org.telegram.ui.ReactionsDoubleTapManageActivity());
        } else if (item.id == ID_LOGS_ENABLED) {
            // The same preference the debug menu writes, so the two can never disagree.
            org.telegram.messenger.ApplicationLoader.applicationContext
                    .getSharedPreferences("systemConfig", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("logsEnabled", org.telegram.messenger.BuildVars.LOGS_ENABLED = !org.telegram.messenger.BuildVars.LOGS_ENABLED)
                    .commit();
            listView.adapter.update(true);
        } else if (item.id == ID_CAMERA2) {
            // Upstream state, not ours: the same value the debug menu toggles, so the two screens
            // can never disagree about it.
            org.telegram.messenger.SharedConfig.toggleUseCamera2(currentAccount);
            listView.adapter.update(true);
        } else if (item.id == ID_ADBLOCK_UPDATE) {
            updateAdBlockLists();
        } else if (item.id == ID_HW_BENCHMARK) {
            runHwBenchmark();
        } else if (item.id == ID_ARCHIVE_ON_PULL) {
            // Upstream already has this state - it is what the "swipe the archive row up"
            // gesture toggles. We only surface it as a setting.
            org.telegram.messenger.SharedConfig.toggleArchiveHidden();
            // Mutually exclusive with "Убрать строку «Архив»" - see the note over there.
            if (org.telegram.messenger.SharedConfig.archiveHidden
                    && org.telegram.messenger.PrimeTweaks.get(org.telegram.messenger.PrimeTweaks.HIDE_ARCHIVE_FOLDER)) {
                org.telegram.messenger.PrimeTweaks.set(org.telegram.messenger.PrimeTweaks.HIDE_ARCHIVE_FOLDER, false);
            }
            org.telegram.messenger.NotificationCenter.getGlobalInstance()
                    .postNotificationName(org.telegram.messenger.NotificationCenter.dialogsNeedReload, true);
            listView.adapter.update(true);
        } else if (item.id == ID_RELATIVE_LAST_SEEN || item.id == ID_NO_NUMBER_ROUNDING || item.id == ID_TIME_WITH_SECONDS) {
            String key = item.id == ID_RELATIVE_LAST_SEEN ? org.telegram.messenger.PrimeTweaks.RELATIVE_LAST_SEEN
                    : item.id == ID_NO_NUMBER_ROUNDING ? org.telegram.messenger.PrimeTweaks.DISABLE_NUMBER_ROUNDING
                    : org.telegram.messenger.PrimeTweaks.TIME_WITH_SECONDS;
            org.telegram.messenger.PrimeTweaks.set(key, !org.telegram.messenger.PrimeTweaks.get(key));
            if (item.id == ID_TIME_WITH_SECONDS) {
                // The time formatters are built once and cached; they have to be thrown away
                // or the new pattern only appears after a restart.
                LocaleController.getInstance().recreateFormatters();
            }
            listView.adapter.update(true);
        } else if (item.id == ID_STT_ENABLED) {
            final boolean enabled = !org.telegram.messenger.PrimeTranscription.isEnabled();
            org.telegram.messenger.PrimeTranscription.setEnabled(enabled);
            if (enabled && org.telegram.messenger.PrimeWhisper.isEnabled()) {
                org.telegram.messenger.PrimeWhisper.setEnabled(false);
                org.telegram.messenger.PrimeWhisper.release();
                org.telegram.ui.Components.BulletinFactory.of(this)
                        .createSimpleBulletin(R.raw.info, "Расшифровка на устройстве выключена",
                                "Расшифровка работает в одном месте: на устройстве или на сервере").show();
            }
            listView.adapter.update(true);
        } else if (item.id == ID_STT_SMART_DNS) {
            org.telegram.messenger.PrimeTranscription.setSmartDnsEnabled(
                    !org.telegram.messenger.PrimeTranscription.isSmartDnsEnabled());
            listView.adapter.update(true);
        } else if (item.id == ID_STT_TOKEN) {
            showTextInputDialog("Ключ сервиса",
                    "Ключ доступа к сервису расшифровки. Для Groq — бесплатно и без карты на console.groq.com.",
                    org.telegram.messenger.PrimeTranscription.getToken(), "gsk_…",
                    value -> org.telegram.messenger.PrimeTranscription.setToken(value));
        } else if (item.id == ID_STT_ENDPOINT) {
            showTextInputDialog("Адрес сервиса",
                    "Полный URL метода расшифровки, совместимого с OpenAI. Пустое поле вернёт адрес Groq.",
                    org.telegram.messenger.PrimeTranscription.getEndpoint(),
                    org.telegram.messenger.PrimeTranscription.DEFAULT_ENDPOINT,
                    value -> org.telegram.messenger.PrimeTranscription.setEndpoint(value));
        } else if (item.id == ID_STT_MODEL) {
            showTextInputDialog("Модель",
                    "Имя модели распознавания у выбранного сервиса. Пустое поле вернёт модель по умолчанию.",
                    org.telegram.messenger.PrimeTranscription.getModel(),
                    org.telegram.messenger.PrimeTranscription.DEFAULT_MODEL,
                    value -> org.telegram.messenger.PrimeTranscription.setModel(value));
        } else if (item.id == ID_ONLINE_DOTS) {
            SharedPreferences prefs = MessagesController.getGlobalMainSettings();
            boolean on = prefs.getBoolean(org.telegram.ui.Cells.PrimeMessageMarks.ONLINE_DOTS_KEY, true);
            prefs.edit().putBoolean(org.telegram.ui.Cells.PrimeMessageMarks.ONLINE_DOTS_KEY, !on).apply();
            org.telegram.ui.Cells.PrimeMessageMarks.invalidateOnlineDots();
            listView.adapter.update(true);
        } else if (item.id == ID_ADBLOCK) {
            org.telegram.messenger.browser.PrimeAdBlock.setEnabled(!org.telegram.messenger.browser.PrimeAdBlock.isEnabled());
            listView.adapter.update(true);
        } else if (item.id == ID_ADBLOCK_DNS) {
            org.telegram.messenger.browser.PrimeAdBlock.setDnsBlockingEnabled(!org.telegram.messenger.browser.PrimeAdBlock.isDnsBlockingEnabled());
            listView.adapter.update(true);
        } else if (item.id == ID_DNS_ENABLED) {
            org.telegram.messenger.browser.PrimeDns.setEnabled(!org.telegram.messenger.browser.PrimeDns.isEnabled());
            listView.adapter.update(true);
        } else if (item.id == ID_WHATS_NEW) {
            org.telegram.ui.Components.PrimeWhatsNewSheet.show(this);
        } else if (item.id == ID_PLUGINS) {
            presentFragment(new PrimePluginsActivity());
        } else if (item.id == ID_TGWS_SETTINGS) {
            presentFragment(new PrimeTgWsActivity());
        } else if (item.id == ID_ICON_PACKS) {
            presentFragment(new PrimeIconPacksActivity());
        } else if (item.id == ID_TOOLBAR_BUTTONS) {
            showToolbarButtonsSheet();
        } else if (item.id == ID_GUIDE) {
            primeStartGuide();
        } else if (item.id == ID_BIGFILE) {
            final boolean enabled = !org.telegram.messenger.PrimeBigFile.isSendingEnabled();
            org.telegram.messenger.PrimeBigFile.setSendingEnabled(enabled);
            if (!enabled) {
                // Turning the feature off takes the experiment with it; leaving a 50 GB switch
                // set behind a disabled feature is a trap for the next time it is turned on.
                org.telegram.messenger.PrimeBigFile.setExperimentalEnabled(false);
            }
            listView.adapter.update(true);
        } else if (item.id == ID_BIGFILE_EXPERIMENTAL) {
            org.telegram.messenger.PrimeBigFile.setExperimentalEnabled(
                    !org.telegram.messenger.PrimeBigFile.isExperimentalEnabled());
            listView.adapter.update(true);
        } else if (item.id == ID_DNS_PRESET) {
            showDnsPicker();
        } else if (item.id == ID_LIMIT_RECENT_STICKERS) {
            showRecentStickersPicker(messagesController);
        } else if (item.id == ID_SIDEBAR_ENABLED) {
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            boolean enabled = preferences.getBoolean("primegram_sidebar_enabled", false);
            preferences.edit().putBoolean("primegram_sidebar_enabled", !enabled).apply();
            
            if (LaunchActivity.instance != null) {
                LaunchActivity.instance.updateSidebarVisibility();
            }
            listView.adapter.update(true);
        } else if (item.id == ID_NON_ISLAND_UI) {
            org.telegram.messenger.NonIslandHelper.setEnabled(!org.telegram.messenger.NonIslandHelper.isEnabled());
            listView.adapter.update(true);
        } else if (item.id == ID_NAVIGATION_DRAWER) {
            boolean enablingDrawer = !org.telegram.messenger.DrawerHelper.isEnabled();
            org.telegram.messenger.DrawerHelper.setEnabled(enablingDrawer);
            if (!enablingDrawer) {
                // Turning the drawer off is the user asking for the standard bottom-tabs view
                // back. "Скрыть вкладки снизу" is a separate flag that doesn't get cleared just
                // because the drawer (which OR's into the same isHidden() check) is gone - left
                // alone, it would keep the tab bar hidden with no drawer to reach it through
                // either, which is exactly the stuck state this is meant to prevent.
                org.telegram.messenger.MainTabsHelper.setHidden(false);
            }
            // Whichever way this switches, the PrimeGram side panel is the only way back to
            // settings/profile from the chat list (the classic drawer's own hamburger covers it
            // too, but the panel is what people are used to reaching for) - so every navigation
            // mode switch turns it back on rather than risk leaving someone stranded with it off.
            MessagesController.getGlobalMainSettings().edit().putBoolean("primegram_sidebar_enabled", true).apply();
            if (LaunchActivity.instance != null) {
                LaunchActivity.instance.updateSidebarVisibility();
            }
            listView.adapter.update(true);
            if (getParentActivity() != null) {
                // Half-switched navigation mode (hamburger drawn but no drawer wired, or vice
                // versa) leaves the user with no way back into settings until the process
                // actually restarts - "later" is not a safe default here.
                new AlertDialog.Builder(getParentActivity())
                    .setTitle("Требуется перезапуск")
                    .setMessage("Смена типа навигации применится после перезапуска приложения.")
                    .setPositiveButton("Перезапустить сейчас", (dialog, which) -> restartApp())
                    .setNegativeButton("Позже", null)
                    .show();
            }
        } else if (item.id == ID_MAIN_TABS_COMPACT) {
            org.telegram.messenger.MainTabsHelper.setCompact(!org.telegram.messenger.MainTabsHelper.isCompact());
            listView.adapter.update(true);
            if (getParentActivity() != null) {
                new AlertDialog.Builder(getParentActivity())
                    .setTitle("Требуется перезапуск")
                    .setMessage("Компактные вкладки применятся после перезапуска приложения.")
                    .setPositiveButton("Перезапустить сейчас", (dialog, which) -> restartApp())
                    .setNegativeButton("Позже", null)
                    .show();
            }
        } else if (item.id == ID_MAIN_TABS_HIDE) {
            org.telegram.messenger.MainTabsHelper.setHidden(!org.telegram.messenger.MainTabsHelper.isHidden());
            MessagesController.getGlobalMainSettings().edit().putBoolean("primegram_sidebar_enabled", true).apply();
            if (LaunchActivity.instance != null) {
                LaunchActivity.instance.updateSidebarVisibility();
            }
            listView.adapter.update(true);
            if (getParentActivity() != null) {
                new AlertDialog.Builder(getParentActivity())
                    .setTitle("Требуется перезапуск")
                    .setMessage("Скрытие вкладок применится после перезапуска приложения.")
                    .setPositiveButton("Перезапустить сейчас", (dialog, which) -> restartApp())
                    .setNegativeButton("Позже", null)
                    .show();
            }
        } else if (item.id == ID_SIDEBAR_ZONE) {
            showSidebarZoneSheet();
        } else if (item.id == ID_EMERGENCY_PROXY) {
            if (VpnSDK.isProxyRunning()) {
                VpnSDK.stopProxy();
                listView.adapter.update(true);
            } else {
                android.widget.Toast.makeText(getContext(), "Получаем ключ...", android.widget.Toast.LENGTH_SHORT).show();
                VpnSDK.registerOrAuth(2, success -> {
                    if (listView != null && listView.adapter != null) {
                        listView.adapter.update(true);
                    }
                    if (!success) {
                        android.widget.Toast.makeText(getContext(), "Не удалось получить ключ. Проверьте соединение и попробуйте ещё раз.", android.widget.Toast.LENGTH_LONG).show();
                    }
                });
            }
        } else if (item.id == ID_VLESS_CUSTOM_KEY) {
            presentFragment(new PrimeVpnServersActivity());
        } else if (item.id == ID_TGWS_PROXY) {
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            // Follows the switch, not the service's current state: those disagree whenever the
            // service has been restarted from elsewhere, and then this did the opposite of what
            // the user had just asked for.
            boolean enabled = !preferences.getBoolean("primegram_tgws_enabled", true);
            preferences.edit().putBoolean("primegram_tgws_enabled", enabled).apply();

            if (enabled) {
                org.telegram.messenger.TgWsProxyService.startService(getParentActivity());
            } else {
                org.telegram.messenger.TgWsProxyService.stopService(getParentActivity());
            }
            listView.adapter.update(true);
        } else if (item.id == ID_VPN_GUARD) {
            final boolean enabled = !org.telegram.messenger.PrimeVpnGuard.isEnabled();
            org.telegram.messenger.PrimeVpnGuard.setEnabled(enabled);
            listView.adapter.update(true);
        } else if (item.id == ID_VPN_GUARD_WHITELIST) {
            showVpnWhitelistPicker();
        } else if (item.id == ID_AUTO_UPDATES) {
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            boolean enabled = preferences.getBoolean("primegram_auto_updates", true);
            preferences.edit().putBoolean("primegram_auto_updates", !enabled).apply();
            listView.adapter.update(true);
        } else if (item.id == ID_HW_ACCEL) {
            boolean newValue = !org.telegram.messenger.CrashSafeToggle.isEnabled("primegram_hw_accel");
            org.telegram.messenger.CrashSafeToggle.setEnabled("primegram_hw_accel", newValue);
            listView.adapter.update(true);
            if (getParentActivity() != null) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle("Требуется перезапуск");
                builder.setMessage("Изменение вступит в силу после перезапуска PrimeGram.");
                builder.setPositiveButton("Перезапустить сейчас", (dialog, which) -> restartApp());
                builder.setNegativeButton("Позже", null);
                showDialog(builder.create());
            }
        } else if (item.id == ID_FEED_EXCLUDE_MUTED) {
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            boolean enabled = preferences.getBoolean("primegram_feed_exclude_muted", false);
            preferences.edit().putBoolean("primegram_feed_exclude_muted", !enabled).apply();
            listView.adapter.update(true);
        } else if (item.id == ID_FEED_EXCLUDE_ARCHIVED) {
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            boolean enabled = preferences.getBoolean("primegram_feed_exclude_archived", false);
            preferences.edit().putBoolean("primegram_feed_exclude_archived", !enabled).apply();
            listView.adapter.update(true);
        } else if (item.id == ID_CHECK_UPDATES) {
            org.telegram.messenger.PrimeUpdater.checkUpdate(getContext(), true);
        } else if (item.id == ID_LIMIT_FOLDERS) {
            showNumberInputDialog(item.id, "Количество папок", "Укажите максимальное число папок с чатами:", messagesController.dialogFiltersLimitPremium, 20, 1, 100);
        } else if (item.id == ID_LIMIT_PINNED_FOLDER) {
            showNumberInputDialog(item.id, "Закрепы в папке", "Укажите максимальное количество закрепленных чатов внутри одной папки:", messagesController.dialogFiltersPinnedLimitPremium, 10, 1, 100);
        } else if (item.id == ID_LIMIT_PINNED_SAVED) {
            showNumberInputDialog(item.id, "Закрепы в Избранном", "Укажите максимальное количество закрепов внутри Избранного:", messagesController.savedDialogsPinnedLimitPremium, 6, 1, 100);
        } else if (item.id == ID_LIMIT_CHATS_IN_FOLDER) {
            showNumberInputDialog(item.id, "Чатов в папке", "Укажите максимальное число чатов в одной папке:", messagesController.dialogFiltersChatsLimitPremium, 200, 1, 1000);
        } else if (item.id == ID_LIMIT_CHANNELS) {
            showNumberInputDialog(item.id, "Лимит каналов", "Укажите максимальное число каналов и супергрупп, в которых вы можете состоять:", messagesController.channelsLimitPremium, 1000, 10, 10000);
        } else if (item.id == ID_LIMIT_GIFS) {
            showNumberInputDialog(item.id, "Сохраненные GIF", "Укажите максимальный лимит сохраненных гифок:", messagesController.savedGifsLimitPremium, 400, 10, 5000);
        } else if (item.id == ID_LIMIT_STICKERS) {
            showNumberInputDialog(item.id, "Избранные стикеры", "Укажите максимальный лимит избранных стикеров:", messagesController.stickersFavedLimitPremium, 200, 5, 2000);
        } else if (item.id == ID_LIMIT_PUBLIC_LINKS) {
            showNumberInputDialog(item.id, "Публичные ссылки", "Укажите максимальный лимит публичных ссылок / юзернеймов:", messagesController.publicLinksLimitPremium, 20, 1, 200);
        } else if (item.id == ID_LIMIT_CAPTION) {
            showNumberInputDialog(item.id, "Символов в описании", "Укажите лимит символов в описании к медиа:", messagesController.captionLengthLimitPremium, 4096, 100, 50000);
        } else if (item.id == ID_LIMIT_ABOUT) {
            showNumberInputDialog(item.id, "Символов в «О себе»", "Укажите лимит символов в описании профиля «О себе»:", messagesController.aboutLengthLimitPremium, 140, 20, 2000);
        } else if (item.id == ID_SUPPORT_PROJECT) {
            try {
                org.telegram.messenger.browser.Browser.openUrl(getContext(), "http://t.me/send?start=IVqCWWqPk6AA");
            } catch (Exception e) {
                org.telegram.messenger.FileLog.e(e);
            }
        } else if (item.id == ID_GRANT_PERMISSIONS) {
            if (getParentActivity() != null && android.os.Build.VERSION.SDK_INT >= 23) {
                ArrayList<String> perms = new ArrayList<>();
                perms.add(android.Manifest.permission.READ_CONTACTS);
                perms.add(android.Manifest.permission.WRITE_CONTACTS);
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    perms.add(android.Manifest.permission.READ_MEDIA_IMAGES);
                    perms.add(android.Manifest.permission.READ_MEDIA_VIDEO);
                    // asked here rather than at the chat list, where it landed on top of everything
                    perms.add(android.Manifest.permission.POST_NOTIFICATIONS);
                } else {
                    perms.add(android.Manifest.permission.READ_EXTERNAL_STORAGE);
                    perms.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
                }
                perms.add(android.Manifest.permission.READ_PHONE_STATE);
                perms.add(android.Manifest.permission.CALL_PHONE);
                getParentActivity().requestPermissions(perms.toArray(new String[0]), 100);
            }
        } else if (item.id == ID_LOCKSCREEN_CALLS) {
            openLockScreenCallSettings();
        } else if (item.id == ID_BATTERY_OPTIMIZATION) {
            AndroidUtilities.requestIgnoreBatteryOptimizations(getParentActivity());
        } else if (item.id == ID_MUSIC_SETTINGS) {
            presentFragment(new MusicSettingsActivity());
        } else if (item.id == ID_HIDE_PHONE) {
            org.telegram.messenger.PrimeGramPrivacy.setHidePhoneEnabled(!org.telegram.messenger.PrimeGramPrivacy.isHidePhoneEnabled());
            listView.adapter.update(true);
        } else if (item.id == ID_FAKE_PHONE) {
            showPhoneInputDialog();
        } else if (item.id == ID_GREY_ZONE) {
            presentFragment(new GreyZoneActivity());
        } else if (item.id == ID_MESSAGE_TAGS) {
            presentFragment(new MessageTagsActivity());
        } else if (item.id == ID_TEXT_TOOLBAR) {
            org.telegram.messenger.PrimeToolbarSettings.setEnabled(!org.telegram.messenger.PrimeToolbarSettings.isEnabled());
            listView.adapter.update(true);
        } else if (item.id == ID_LINK_PREVIEW) {
            org.telegram.messenger.PrimeLinkPreviewSettings.setEnabled(!org.telegram.messenger.PrimeLinkPreviewSettings.isEnabled());
            listView.adapter.update(true);
        } else if (item.id == ID_SEARCH_PLUS) {
            presentFragment(new SearchPlusActivity());
        } else if (item.id == ID_TEMP_SUBS) {
            presentFragment(new TempSubActivity());
        } else if (item.id == ID_FEED_HIDDEN) {
            SharedPreferences prefs = MessagesController.getGlobalMainSettings();
            boolean hidden = prefs.getBoolean("primegram_feed_hidden", false);
            prefs.edit().putBoolean("primegram_feed_hidden", !hidden).apply();
            listView.adapter.update(true);
            MainTabsActivity.refreshFeedTabVisibility();
        } else if (item.id == ID_STARTUP_TRACE) {
            showStartupTrace();
        } else if (item.id == ID_BATTERY_DIAG) {
            showBatteryDiagnostics();
        } else if (item.id == ID_PUSH_STATUS) {
            showPushStatus();
        } else if (item.id == ID_BOT_LOGIN) {
            presentFragment(new BotLoginActivity());
        }
    }

    /**
     * PrimeGram: whether push actually works, in one line.
     *
     * <p>Written because "уведомления не приходят" has four completely different causes - no token
     * from Firebase, a token never sent to Telegram, the background connection off, or the system
     * withholding notifications - and none of them are visible from the outside.
     */
    private String primePushSummary() {
        if (org.telegram.messenger.SharedConfig.pushString == null
                || org.telegram.messenger.SharedConfig.pushString.isEmpty()) {
            return "нет токена";
        }
        return org.telegram.messenger.UserConfig.getInstance(currentAccount).registeredForPush
                ? "работают" : "токен не отправлен";
    }

    private void showPushStatus() {
        if (getParentActivity() == null) {
            return;
        }
        final String token = org.telegram.messenger.SharedConfig.pushString;
        final boolean hasToken = token != null && !token.isEmpty();
        final StringBuilder text = new StringBuilder();

        text.append("Токен Firebase: ").append(hasToken ? "получен" : "нет");
        if (hasToken) {
            // Enough to tell one token from another when re-registering; never the whole thing.
            text.append(" (").append(token.substring(0, Math.min(12, token.length()))).append("…, ")
                .append(token.length()).append(" симв.)");
        } else if (org.telegram.messenger.SharedConfig.pushStringStatus != null
                && !org.telegram.messenger.SharedConfig.pushStringStatus.isEmpty()) {
            text.append("\nСостояние: ").append(org.telegram.messenger.SharedConfig.pushStringStatus);
        }
        text.append("\nТип: ").append(org.telegram.messenger.SharedConfig.pushType
                == org.telegram.messenger.PushListenerController.PUSH_TYPE_FIREBASE ? "FCM" : "Huawei");
        text.append("\nОтправлен в Telegram: ")
            .append(org.telegram.messenger.UserConfig.getInstance(currentAccount).registeredForPush ? "да" : "нет");
        text.append("\nФоновое соединение: ")
            .append(org.telegram.tgnet.ConnectionsManager.getInstance(currentAccount)
                    .isPushConnectionEnabled() ? "включено" : "выключено");
        text.append("\nСистема разрешила уведомления: ")
            .append(androidx.core.app.NotificationManagerCompat.from(getParentActivity()).areNotificationsEnabled()
                    ? "да" : "нет");

        text.append("\n\nЕсли токена нет — приложение не смогло получить его у Firebase: проверьте, что установлена сборка с вашим google-services.json и что на устройстве есть сервисы Google.\n\nЕсли токен есть, но не отправлен — Telegram его не принял; помогает переустановка и повторный вход.");

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Состояние уведомлений");
        builder.setMessage(text.toString());
        builder.setPositiveButton("Скопировать", (dialog, which) -> {
            AndroidUtilities.addToClipboard(text.toString());
            org.telegram.ui.Components.BulletinFactory.of(this)
                    .createSimpleBulletin(R.raw.copy, "Скопировано").show();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showStartupTrace() {
        if (getParentActivity() == null) {
            return;
        }
        final String trace = org.telegram.messenger.PrimeStartupTrace.dump();
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Трасса запуска");
        builder.setMessage(trace);
        builder.setPositiveButton("Скопировать", (dialog, which) -> {
            AndroidUtilities.addToClipboard(trace);
            org.telegram.ui.Components.BulletinFactory.of(PrimeGramSettingsActivity.this).createCopyBulletin("Скопировано").show();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showBatteryDiagnostics() {
        if (getParentActivity() == null) {
            return;
        }
        final boolean hasUsageAccess = org.telegram.messenger.PrimeBatteryDiagnostics.hasUsageAccess(getParentActivity());
        final String report = org.telegram.messenger.PrimeBatteryDiagnostics.dump(getParentActivity());

        final AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Энергопотребление");
        builder.setMessage(report);
        builder.setPositiveButton("Отправить файлом", (dialog, which) -> exportAndShareBatteryDiagnostics(report));
        builder.setNeutralButton("Скопировать", (dialog, which) -> {
            AndroidUtilities.addToClipboard(report);
            org.telegram.ui.Components.BulletinFactory.of(PrimeGramSettingsActivity.this).createCopyBulletin("Скопировано").show();
        });
        if (!hasUsageAccess) {
            builder.setNegativeButton("Выдать доступ к использованию", (dialog, which) -> {
                try {
                    startActivityForResult(new android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS), 0);
                } catch (Throwable t) {
                    org.telegram.ui.Components.BulletinFactory.of(PrimeGramSettingsActivity.this).createErrorBulletin("Экран настроек недоступен на этом устройстве").show();
                }
            });
        }
        showDialog(builder.create());
    }

    /** Writes the report to a cache file and hands it to the system share sheet - PrimeGram is
     *  itself a valid target there the same way it is for any other file shared into it from
     *  outside, so "send to a chat" is just picking this app from that same sheet, not a separate
     *  chat-picker screen to build and maintain. */
    private void exportAndShareBatteryDiagnostics(String report) {
        if (getParentActivity() == null) {
            return;
        }
        try {
            final java.io.File dir = new java.io.File(getParentActivity().getCacheDir(), "battery_diag");
            if (!dir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
            }
            final String fileName = "primegram_battery_"
                    + new java.text.SimpleDateFormat("yyyy-MM-dd_HHmmss", java.util.Locale.US).format(new java.util.Date())
                    + ".txt";
            final java.io.File file = new java.io.File(dir, fileName);
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
                out.write(report.getBytes("UTF-8"));
            }
            final android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    getParentActivity(), org.telegram.messenger.ApplicationLoader.getApplicationId() + ".provider", file);
            final android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(android.content.Intent.EXTRA_STREAM, uri);
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getParentActivity().startActivity(android.content.Intent.createChooser(intent, "Поделиться диагностикой"));
        } catch (Throwable t) {
            org.telegram.ui.Components.BulletinFactory.of(this).createErrorBulletin("Не удалось создать файл: " + t.getMessage()).show();
        }
    }

    private interface TextInputCallback {
        void onValue(String value);
    }

    /** Shared one-line text prompt, so every string setting looks and behaves the same. */
    private void showTextInputDialog(String title, String message, String current, String hint, TextInputCallback callback) {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(title);
        builder.setMessage(message);

        final EditTextBoldCursor editText = new EditTextBoldCursor(getParentActivity());
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        editText.setHint(hint);
        editText.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setCursorSize(AndroidUtilities.dp(20));
        editText.setCursorWidth(1.5f);
        editText.setSingleLine(true);
        editText.setBackgroundDrawable(Theme.createEditTextDrawable(getParentActivity(), true));
        editText.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));
        editText.setText(current == null ? "" : current);
        editText.setSelection(editText.getText().length());

        LinearLayout container = new LinearLayout(getParentActivity());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);
        container.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        builder.setView(container);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> {
            callback.onValue(editText.getText().toString());
            listView.adapter.update(true);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showRecentStickersPicker(MessagesController messagesController) {
        showNumberInputDialog(ID_LIMIT_RECENT_STICKERS, "Лимит недавних стикеров",
                "Сколько недавно использованных стикеров помнить. Список обрезает сам клиент, так что значение работает без оглядки на сервер — но чем оно больше, тем больше стикеров хранится в базе.",
                messagesController.maxRecentStickersCount, 30, 30, 500);
    }

    /**
     * The sheet where the sidebar's activation zone is placed.
     *
     * <p>Built here rather than as its own screen because it is one control: a settings page around
     * a single phone-shaped diagram would be mostly empty. The values are written when the sheet
     * goes away, however it goes away - dismissing a sheet you have been dragging things around in
     * reads as "done", not as "cancel".
     */
    private void showSidebarZoneSheet() {
        if (getParentActivity() == null) {
            return;
        }
        final Context context = getParentActivity();
        final LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);

        final org.telegram.ui.Components.PrimeSidebarZoneEditor editor =
                new org.telegram.ui.Components.PrimeSidebarZoneEditor(context, getResourceProvider());
        content.addView(editor, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 300, 0, 12, 0, 4));

        final TextView valueView = new TextView(context);
        valueView.setGravity(Gravity.CENTER);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        valueView.setTypeface(AndroidUtilities.bold());
        valueView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        content.addView(valueView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 21, 0, 21, 0));

        final TextView hintView = new TextView(context);
        hintView.setGravity(Gravity.CENTER);
        hintView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        hintView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
        hintView.setText("Потяните область, чтобы передвинуть её, и кружки на краях — чтобы изменить размер. Свайп внутри неё открывает боковую панель.");
        content.addView(hintView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 21, 8, 21, 4));

        // Saved on every movement rather than on dismiss. The dismiss listener was the obvious
        // place and it is the one place that cannot work: showDialog() replaces it with its own,
        // so the zone the user dragged out was thrown away the moment the sheet closed. Saving as
        // they drag also means "Сбросить" takes effect immediately, with nothing to confirm.
        final Runnable updateValue = () -> {
            valueView.setText("Ширина " + Math.round(editor.getZoneWidth() * 100) + "%"
                    + " · по вертикали " + Math.round(editor.getZoneTop() * 100)
                    + "–" + Math.round(editor.getZoneBottom() * 100) + "%");
            org.telegram.messenger.PrimeSidebarZone.set(
                    editor.getZoneWidth(), editor.getZoneTop(), editor.getZoneBottom());
        };
        editor.setOnChange(updateValue);
        updateValue.run();

        final LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(buttons, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 12, 16, 8));

        final TextView resetView = new TextView(context);
        resetView.setGravity(Gravity.CENTER);
        resetView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        resetView.setTypeface(AndroidUtilities.bold());
        resetView.setText("Сбросить");
        resetView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
        resetView.setBackground(Theme.createRadSelectorDrawable(
                getThemedColor(Theme.key_listSelector), 8, 8));
        resetView.setOnClickListener(v -> editor.resetToDefaults());
        buttons.addView(resetView, LayoutHelper.createLinear(0, 44, 1f));

        final TextView doneView = new TextView(context);
        doneView.setGravity(Gravity.CENTER);
        doneView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        doneView.setTypeface(AndroidUtilities.bold());
        doneView.setText(LocaleController.getString(R.string.Done));
        doneView.setTextColor(getThemedColor(Theme.key_featuredStickers_buttonText));
        doneView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(8),
                getThemedColor(Theme.key_featuredStickers_addButton),
                getThemedColor(Theme.key_featuredStickers_addButtonPressed)));
        buttons.addView(doneView, LayoutHelper.createLinear(0, 44, 1f, 8, 0, 0, 0));

        final BottomSheet sheet = new BottomSheet.Builder(context, false, getResourceProvider())
                .setTitle("Зона активации", true)
                .setCustomView(content)
                .create();
        doneView.setOnClickListener(v -> sheet.dismiss());
        showDialog(sheet, dialog -> {
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        });
    }

    private void runHwBenchmark() {
        if (getParentActivity() == null) {
            return;
        }
        final AlertDialog progress = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(false);
        progress.show();
        org.telegram.messenger.PrimeHwBenchmark.run(result -> {
            try {
                progress.dismiss();
            } catch (Throwable ignore) {
            }
            if (getParentActivity() == null) {
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle("Стресс-тест ускорения");
            builder.setMessage(org.telegram.messenger.PrimeHwBenchmark.format(result));
            builder.setPositiveButton("Закрыть", null);
            if (result.ok) {
                builder.setNeutralButton("Скопировать", (di, w) ->
                        AndroidUtilities.addToClipboard(org.telegram.messenger.PrimeHwBenchmark.format(result)));
            }
            showDialog(builder.create());
        });
    }

    private void showStickerSizePicker() {
        if (getParentActivity() == null) {
            return;
        }
        final int min = 6, max = 20;
        final CharSequence[] options = new CharSequence[max - min + 1];
        for (int i = 0; i < options.length; i++) {
            int value = min + i;
            options[i] = value == org.telegram.messenger.PrimeTweaks.STICKER_SIZE_DEFAULT
                    ? value + " — как в оригинале"
                    : String.valueOf(value);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Размер стикеров");
        builder.setItems(options, (dialog, which) -> {
            org.telegram.messenger.PrimeTweaks.setInt(org.telegram.messenger.PrimeTweaks.STICKER_SIZE, min + which);
            listView.adapter.update(true);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    /**
     * Whether the system will let an incoming call take over a locked screen. Two different
     * permissions answer that question depending on the phone: Android 14 introduced its own
     * full-screen-intent switch, and MIUI has had a separate one of its own for years.
     */
    private static String primeLockScreenCallsStatus() {
        try {
            if (org.telegram.messenger.XiaomiUtilities.isMIUI()
                    && !org.telegram.messenger.XiaomiUtilities.isCustomPermissionGranted(org.telegram.messenger.XiaomiUtilities.OP_SHOW_WHEN_LOCKED)) {
                return "Не разрешено";
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                android.app.NotificationManager nm = (android.app.NotificationManager)
                        ApplicationLoader.applicationContext.getSystemService(android.content.Context.NOTIFICATION_SERVICE);
                if (nm != null && !nm.canUseFullScreenIntent()) {
                    return "Не разрешено";
                }
            }
        } catch (Throwable ignore) {
            return "";
        }
        return "Разрешено";
    }

    private void openLockScreenCallSettings() {
        if (getParentActivity() == null) {
            return;
        }
        try {
            if (org.telegram.messenger.XiaomiUtilities.isMIUI()) {
                android.content.Intent intent = org.telegram.messenger.XiaomiUtilities.getPermissionManagerIntent();
                if (intent != null) {
                    getParentActivity().startActivity(intent);
                    return;
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT);
                intent.setData(android.net.Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                getParentActivity().startActivity(intent);
                return;
            }
            // older Android has no separate switch - the app settings page is the closest thing
            android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(android.net.Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
            getParentActivity().startActivity(intent);
        } catch (Throwable t) {
            org.telegram.messenger.FileLog.e(t);
        }
    }

    /** What the "Обновить списки" row says on its right-hand side. */
    private static String adBlockListsStatus() {
        if (!org.telegram.messenger.browser.PrimeAdBlockLists.hasAnyListEnabled()) {
            return "Список не выбран";
        }
        long updated = org.telegram.messenger.browser.PrimeAdBlockLists.lastUpdateTime();
        if (updated <= 0) {
            return "Ещё не загружено";
        }
        int rules = org.telegram.messenger.browser.PrimeAdBlockLists.ruleCount();
        long ageMs = System.currentTimeMillis() - updated;
        String age;
        if (ageMs < 60 * 60 * 1000L) {
            age = "только что";
        } else if (ageMs < 24 * 60 * 60 * 1000L) {
            age = (ageMs / (60 * 60 * 1000L)) + " ч назад";
        } else {
            age = (ageMs / (24 * 60 * 60 * 1000L)) + " дн назад";
        }
        return rules + " доменов, " + age;
    }

    /**
     * Cache sizes by kind, or null until the first measurement lands.
     *
     * <p>Measuring walks every cache directory, so it runs on a background thread and the row
     * shows a placeholder until it finishes rather than blocking the screen from opening.
     */
    private long[] cacheSizes;
    private boolean measuringCache;

    private CharSequence cacheSizeText() {
        if (cacheSizes == null) {
            measureCache();
            return "…";
        }
        long total = 0;
        for (long size : cacheSizes) {
            total += size;
        }
        return AndroidUtilities.formatFileSize(total);
    }

    private void measureCache() {
        if (measuringCache) {
            return;
        }
        measuringCache = true;
        org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
            final long[] measured = org.telegram.messenger.PrimeCache.measure();
            AndroidUtilities.runOnUIThread(() -> {
                measuringCache = false;
                cacheSizes = measured;
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
            });
        });
    }

    private void showCacheDialog() {
        if (getParentActivity() == null) {
            return;
        }
        if (cacheSizes == null) {
            measureCache();
            org.telegram.ui.Components.BulletinFactory.of(this)
                    .createErrorBulletin("Считаю размер, секунду…").show();
            return;
        }
        final long[] sizes = cacheSizes;
        long total = 0;
        for (long size : sizes) {
            total += size;
        }
        if (total <= 0) {
            org.telegram.ui.Components.BulletinFactory.of(this)
                    .createSimpleBulletin(R.raw.done, "Кэш уже пуст").show();
            return;
        }
        // One entry per kind plus a clear-everything row, rather than checkboxes: this dialog
        // has no multi-choice variant, and picking one kind at a time is what people actually do.
        final CharSequence[] options = new CharSequence[org.telegram.messenger.PrimeCache.KIND_COUNT + 1];
        for (int i = 0; i < org.telegram.messenger.PrimeCache.KIND_COUNT; i++) {
            options[i] = org.telegram.messenger.PrimeCache.KIND_NAMES[i] + " — " + AndroidUtilities.formatFileSize(sizes[i]);
        }
        options[org.telegram.messenger.PrimeCache.KIND_COUNT] = "Очистить всё — " + AndroidUtilities.formatFileSize(total);

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Кэш медиа");
        builder.setItems(options, (dialog, which) -> {
            final boolean[] kinds = new boolean[org.telegram.messenger.PrimeCache.KIND_COUNT];
            if (which >= org.telegram.messenger.PrimeCache.KIND_COUNT) {
                java.util.Arrays.fill(kinds, true);
            } else {
                if (sizes[which] <= 0) {
                    return;
                }
                kinds[which] = true;
            }
            clearCache(kinds);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void clearCache(boolean[] kinds) {
        if (getParentActivity() == null) {
            return;
        }
        final AlertDialog progress = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(false);
        progress.show();
        org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
            org.telegram.messenger.PrimeCache.clear(kinds);
            final long[] measured = org.telegram.messenger.PrimeCache.measure();
            AndroidUtilities.runOnUIThread(() -> {
                progress.dismiss();
                cacheSizes = measured;
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
                if (getParentActivity() != null) {
                    org.telegram.ui.Components.BulletinFactory.of(this)
                            .createSimpleBulletin(R.raw.done, "Кэш очищен").show();
                }
            });
        });
    }

    private AlertDialog adBlockProgressDialog;

    private void updateAdBlockLists() {
        if (getParentActivity() == null) {
            return;
        }
        if (!org.telegram.messenger.browser.PrimeAdBlockLists.hasAnyListEnabled()) {
            org.telegram.ui.Components.BulletinFactory.of(this)
                    .createErrorBulletin("Сначала выберите хотя бы один список.").show();
            return;
        }
        if (adBlockProgressDialog != null) {
            return;
        }
        adBlockProgressDialog = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        adBlockProgressDialog.setCanCancel(false);
        adBlockProgressDialog.show();
        org.telegram.messenger.browser.PrimeAdBlockLists.update(new org.telegram.messenger.browser.PrimeAdBlockLists.UpdateCallback() {
            @Override
            public void onProgress(String message) {
                if (adBlockProgressDialog != null) {
                    adBlockProgressDialog.setMessage(message);
                }
            }

            @Override
            public void onFinished(boolean ok, String message) {
                if (adBlockProgressDialog != null) {
                    adBlockProgressDialog.dismiss();
                    adBlockProgressDialog = null;
                }
                if (getParentActivity() == null) {
                    return;
                }
                listView.adapter.update(true);
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(ok ? "Списки обновлены" : "Не получилось");
                builder.setMessage(message);
                builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
                showDialog(builder.create());
            }
        });
    }

    /** Built once and reused: it carries the slider's position, so rebuilding it would reset it. */
    private org.telegram.ui.Cells.PrimeAvatarCornersCell avatarCornersCell;

    private org.telegram.ui.Cells.PrimeAvatarCornersCell avatarCornersCell() {
        if (avatarCornersCell == null && getContext() != null) {
            avatarCornersCell = new org.telegram.ui.Cells.PrimeAvatarCornersCell(getContext(), "Форма аватарок", null);
        }
        return avatarCornersCell;
    }

    /** Codes whisper understands, plus an empty one meaning auto-detect. */
    private static final String[] WHISPER_LANGUAGE_CODES = {"", "ru", "en", "uk", "de", "fr", "es"};
    private static final String[] WHISPER_LANGUAGE_NAMES = {
        "Определять сам", "Русский", "English", "Українська", "Deutsch", "Français", "Español"
    };

    private CharSequence whisperLanguageName() {
        final String current = org.telegram.messenger.PrimeWhisper.getLanguage();
        for (int i = 0; i < WHISPER_LANGUAGE_CODES.length; i++) {
            if (WHISPER_LANGUAGE_CODES[i].equals(current)) {
                return WHISPER_LANGUAGE_NAMES[i];
            }
        }
        return WHISPER_LANGUAGE_NAMES[0];
    }

    private void showWhisperLanguagePicker() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Язык записи");
        builder.setItems(WHISPER_LANGUAGE_NAMES, (dialog, which) -> {
            org.telegram.messenger.PrimeWhisper.setLanguage(WHISPER_LANGUAGE_CODES[which]);
            listView.adapter.update(true);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showWhisperModelPicker() {
        if (getParentActivity() == null) {
            return;
        }
        final String[] names = org.telegram.messenger.PrimeWhisper.MODEL_NAMES;
        final CharSequence[] options = new CharSequence[names.length];
        for (int i = 0; i < names.length; i++) {
            options[i] = names[i] + " — " + org.telegram.messenger.PrimeWhisper.MODEL_DESCRIPTIONS[i]
                    + (org.telegram.messenger.PrimeWhisper.isModelDownloaded(i) ? " · загружена" : "");
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Модель распознавания");
        builder.setItems(options, (dialog, which) -> {
            final String warning = org.telegram.messenger.PrimeWhisper.capabilityWarning(which);
            if (warning != null) {
                confirmHeavyWhisperModel(which, warning);
                return;
            }
            org.telegram.messenger.PrimeWhisper.setModel(which);
            listView.adapter.update(true);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    /**
     * The heavy models are half a gigabyte each and can be too much for the phone holding them.
     * Asked before the choice is made rather than after the download, which is the point at which
     * finding out is expensive.
     */
    private void confirmHeavyWhisperModel(int model, String warning) {
        if (getParentActivity() == null) {
            return;
        }
        final boolean risky = !org.telegram.messenger.PrimeWhisper.deviceCanHandle(model);
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(org.telegram.messenger.PrimeWhisper.MODEL_NAMES[model]);
        builder.setMessage(warning);
        builder.setPositiveButton(risky ? "Всё равно выбрать" : "Выбрать", (dialog, which) -> {
            org.telegram.messenger.PrimeWhisper.setModel(model);
            listView.adapter.update(true);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        final AlertDialog alert = builder.create();
        showDialog(alert);
        if (risky) {
            final android.view.View button = alert.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
            if (button instanceof android.widget.TextView) {
                ((android.widget.TextView) button).setTextColor(Theme.getColor(Theme.key_text_RedBold));
            }
        }
    }

    private AlertDialog whisperProgressDialog;

    private void toggleWhisperModel() {
        if (getParentActivity() == null) {
            return;
        }
        final int model = org.telegram.messenger.PrimeWhisper.getModel();
        if (org.telegram.messenger.PrimeWhisper.isModelDownloaded(model)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle("Удалить модель");
            builder.setMessage("Файл модели будет удалён. Распознавание на устройстве перестанет работать, пока вы не скачаете её снова.");
            builder.setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) -> {
                org.telegram.messenger.PrimeWhisper.deleteModel(model);
                listView.adapter.update(true);
            });
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            showDialog(builder.create());
            return;
        }
        if (whisperProgressDialog != null) {
            return;
        }
        whisperProgressDialog = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        whisperProgressDialog.setCanCancel(false);
        whisperProgressDialog.setMessage("Загрузка модели…");
        whisperProgressDialog.show();
        org.telegram.messenger.PrimeWhisper.download(model, new org.telegram.messenger.PrimeWhisper.DownloadCallback() {
            @Override
            public void onProgress(int percent) {
                if (whisperProgressDialog != null) {
                    whisperProgressDialog.setMessage("Загрузка модели… " + percent + "%");
                }
            }

            @Override
            public void onFinished(boolean ok, String message) {
                if (whisperProgressDialog != null) {
                    whisperProgressDialog.dismiss();
                    whisperProgressDialog = null;
                }
                if (getParentActivity() == null) {
                    return;
                }
                if (ok) {
                    org.telegram.ui.Components.BulletinFactory.of(PrimeGramSettingsActivity.this)
                            .createSimpleBulletin(R.raw.done, "Модель загружена").show();
                } else {
                    org.telegram.ui.Components.BulletinFactory.of(PrimeGramSettingsActivity.this)
                            .createErrorBulletin(message == null ? "Ошибка загрузки" : message).show();
                }
                listView.adapter.update(true);
            }
        });
    }

    private static final String[] TRANSLATE_PROVIDER_NAMES = {"Telegram", "Google", "Yandex", "Multiplay"};

    /** 0 means no ceiling; the rest are the shorter side in pixels. */
    private static final int[] VIDEO_QUALITY_VALUES = {0, 1080, 720, 480, 360};
    private static final String[] VIDEO_QUALITY_NAMES = {"Максимальное", "До 1080p", "До 720p", "До 480p", "До 360p"};

    private int videoQualityIndex() {
        final int current = org.telegram.messenger.PrimeTweaks.maxVideoHeight();
        for (int i = 0; i < VIDEO_QUALITY_VALUES.length; i++) {
            if (VIDEO_QUALITY_VALUES[i] == current) {
                return i;
            }
        }
        return 0;
    }

    private void showVideoQualityPicker() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Качество видео");
        builder.setItems(VIDEO_QUALITY_NAMES, (dialog, which) -> {
            org.telegram.messenger.PrimeTweaks.setInt(org.telegram.messenger.PrimeTweaks.MAX_VIDEO_HEIGHT,
                    VIDEO_QUALITY_VALUES[which]);
            listView.adapter.update(true);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void togglePreloadVideoOnMobile() {
        final org.telegram.messenger.DownloadController controller =
                org.telegram.messenger.DownloadController.getInstance(currentAccount);
        controller.primeSetMobilePreloadVideo(!controller.primeMobilePreloadVideo());
        listView.adapter.update(true);
    }

    private void showTranslateProviderPicker() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Чем переводить");
        builder.setItems(TRANSLATE_PROVIDER_NAMES, (dialog, which) -> {
            org.telegram.messenger.PrimeTweaks.setInt(org.telegram.messenger.PrimeTweaks.TRANSLATE_PROVIDER, which);
            listView.adapter.update(true);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private static final String[] DOUBLE_TAP_NAMES = {"Реакция", "Ответить", "Ничего"};

    /**
     * The three sticker sizes offered as cards.
     *
     * <p>A slider would give more values and be worse: the number is stored as a text height that
     * means nothing to anyone, and the only question people actually have is small, normal or big.
     */
    private static final int[] STICKER_SIZE_VALUES = {10, org.telegram.messenger.PrimeTweaks.STICKER_SIZE_DEFAULT, 20};
    private static final String[] STICKER_SIZE_NAMES = {"Компактно", "Как в оригинале", "Крупно"};

    private org.telegram.ui.Cells.PrimeShapeOptionsCell stickerSizeCards;
    private org.telegram.ui.Cells.PrimeShapeOptionsCell doubleTapCards;

    private org.telegram.ui.Cells.PrimeShapeOptionsCell translatorCards;
    private org.telegram.ui.Cells.PrimeShapeOptionsCell videoQualityCards;

    private org.telegram.ui.Cells.PrimeShapeOptionsCell translatorCards() {
        if (translatorCards == null && getContext() != null) {
            final int current = Math.max(0, Math.min(TRANSLATE_PROVIDER_NAMES.length - 1,
                    org.telegram.messenger.PrimeTweaks.translateProvider()));
            translatorCards = new org.telegram.ui.Cells.PrimeShapeOptionsCell(getContext(),
                    org.telegram.ui.Cells.PrimeShapeOptionsCell.MODE_TRANSLATOR,
                    TRANSLATE_PROVIDER_NAMES, null, current, null);
            translatorCards.setOnSelected(index ->
                    org.telegram.messenger.PrimeTweaks.setInt(
                            org.telegram.messenger.PrimeTweaks.TRANSLATE_PROVIDER, index));
        }
        return translatorCards;
    }

    private org.telegram.ui.Cells.PrimeShapeOptionsCell videoQualityCards() {
        if (videoQualityCards == null && getContext() != null) {
            videoQualityCards = new org.telegram.ui.Cells.PrimeShapeOptionsCell(getContext(),
                    org.telegram.ui.Cells.PrimeShapeOptionsCell.MODE_VIDEO_QUALITY,
                    VIDEO_QUALITY_NAMES, VIDEO_QUALITY_VALUES, videoQualityIndex(), null);
            videoQualityCards.setOnSelected(index ->
                    org.telegram.messenger.PrimeTweaks.setInt(
                            org.telegram.messenger.PrimeTweaks.MAX_VIDEO_HEIGHT, VIDEO_QUALITY_VALUES[index]));
        }
        return videoQualityCards;
    }

    private org.telegram.ui.Cells.PrimeSliderCell stickerSizeSlider;

    private org.telegram.ui.Cells.PrimeSliderCell stickerSizeSlider() {
        if (stickerSizeSlider == null && getContext() != null) {
            stickerSizeSlider = new org.telegram.ui.Cells.PrimeSliderCell(getContext(),
                    "Точный размер", 8, 24, org.telegram.messenger.PrimeTweaks.stickerSize(), null);
            stickerSizeSlider.setFormatter(value ->
                    value == org.telegram.messenger.PrimeTweaks.STICKER_SIZE_DEFAULT
                            ? "как в оригинале" : String.valueOf(value));
            stickerSizeSlider.setListener((value, stop) -> {
                org.telegram.messenger.PrimeTweaks.setInt(
                        org.telegram.messenger.PrimeTweaks.STICKER_SIZE, value);
                updateLivePreview();
                if (stop && stickerSizeCards != null) {
                    // Keep the cards honest: dragging to a value that is not one of the three
                    // should leave none of them looking chosen.
                    int match = -1;
                    for (int i = 0; i < STICKER_SIZE_VALUES.length; i++) {
                        if (STICKER_SIZE_VALUES[i] == value) {
                            match = i;
                            break;
                        }
                    }
                    if (match >= 0) {
                        stickerSizeCards.select(match, true);
                    }
                }
            });
        }
        return stickerSizeSlider;
    }

    /** The emoji currently set as the double-tap reaction, or a hint when it is a custom one. */
    private CharSequence doubleTapReactionName() {
        try {
            final String reaction = org.telegram.messenger.MediaDataController.getInstance(currentAccount)
                    .getDoubleTapReaction();
            if (android.text.TextUtils.isEmpty(reaction)) {
                return "не выбрана";
            }
            // A custom emoji is stored as its document id, which is a number nobody can read.
            return reaction.startsWith("animated_") ? "своя эмодзи" : reaction;
        } catch (Throwable t) {
            return "";
        }
    }

    private org.telegram.ui.Cells.PrimeShapeOptionsCell stickerSizeCards() {
        if (stickerSizeCards == null && getContext() != null) {
            int selected = 1;
            final int current = org.telegram.messenger.PrimeTweaks.stickerSize();
            for (int i = 0; i < STICKER_SIZE_VALUES.length; i++) {
                if (STICKER_SIZE_VALUES[i] == current) {
                    selected = i;
                    break;
                }
            }
            stickerSizeCards = new org.telegram.ui.Cells.PrimeShapeOptionsCell(getContext(),
                    org.telegram.ui.Cells.PrimeShapeOptionsCell.MODE_STICKER_SIZE,
                    STICKER_SIZE_NAMES, STICKER_SIZE_VALUES, selected, null);
            stickerSizeCards.setOnSelected(index -> {
                org.telegram.messenger.PrimeTweaks.setInt(
                        org.telegram.messenger.PrimeTweaks.STICKER_SIZE, STICKER_SIZE_VALUES[index]);
                // The slider is the same setting seen another way, so it has to follow.
                if (stickerSizeSlider != null) {
                    stickerSizeSlider.setValue(STICKER_SIZE_VALUES[index]);
                }
                updateLivePreview();
            });
        }
        return stickerSizeCards;
    }

    private org.telegram.ui.Cells.PrimeShapeOptionsCell doubleTapCards() {
        if (doubleTapCards == null && getContext() != null) {
            final int current = Math.max(0, Math.min(DOUBLE_TAP_NAMES.length - 1,
                    org.telegram.messenger.PrimeTweaks.doubleTapAction()));
            doubleTapCards = new org.telegram.ui.Cells.PrimeShapeOptionsCell(getContext(),
                    org.telegram.ui.Cells.PrimeShapeOptionsCell.MODE_DOUBLE_TAP,
                    DOUBLE_TAP_NAMES, null, current, null);
            doubleTapCards.setOnSelected(index ->
                    org.telegram.messenger.PrimeTweaks.setInt(
                            org.telegram.messenger.PrimeTweaks.DOUBLE_TAP_ACTION, index));
        }
        return doubleTapCards;
    }

    private void showDoubleTapPicker() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Двойное нажатие по сообщению");
        builder.setItems(DOUBLE_TAP_NAMES, (dialog, which) -> {
            org.telegram.messenger.PrimeTweaks.setInt(org.telegram.messenger.PrimeTweaks.DOUBLE_TAP_ACTION, which);
            listView.adapter.update(true);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showVpnWhitelistPicker() {
        if (getParentActivity() == null) {
            return;
        }
        // listInstalledVpnApps only finds an app that exports a real android.net.VpnService
        // component with that action declared - plenty of proprietary VPN clients don't, and the
        // picker used to come up "no VPN apps found" for someone who genuinely had one running.
        // Falling back to every launchable app, searchable, means picking the right one no longer
        // depends on that app's own manifest cooperating.
        java.util.List<android.content.pm.ApplicationInfo> apps =
                org.telegram.messenger.PrimeVpnGuard.listInstalledVpnApps(getParentActivity());
        if (apps.isEmpty()) {
            apps = org.telegram.messenger.PrimeVpnGuard.listAllInstalledApps(getParentActivity());
        }
        if (apps.isEmpty()) {
            android.widget.Toast.makeText(getContext(), "Не удалось получить список приложений", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        final android.content.pm.PackageManager pm = getParentActivity().getPackageManager();

        final LinearLayout root = new LinearLayout(getParentActivity());
        root.setOrientation(LinearLayout.VERTICAL);

        final org.telegram.ui.Components.EditTextBoldCursor search = new org.telegram.ui.Components.EditTextBoldCursor(getParentActivity());
        search.setHint("Поиск приложения");
        search.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        search.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        search.setHintTextColor(getThemedColor(Theme.key_dialogTextHint));
        search.setBackground(null);
        search.setSingleLine(true);
        search.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(8));
        root.addView(search, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final LinearLayout list = new LinearLayout(getParentActivity());
        list.setOrientation(LinearLayout.VERTICAL);
        final android.widget.ScrollView scroll = new android.widget.ScrollView(getParentActivity());
        scroll.addView(list, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        // Hundreds of apps on a stock ROM would otherwise make this dialog taller than the
        // screen with no way to reach the buttons below it.
        root.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(360)));

        final java.util.List<String> labels = new java.util.ArrayList<>();
        for (android.content.pm.ApplicationInfo app : apps) {
            final String packageName = app.packageName;
            final String label = String.valueOf(app.loadLabel(pm));
            labels.add(label.toLowerCase(java.util.Locale.ROOT));
            org.telegram.ui.Cells.CheckBoxCell cell = new org.telegram.ui.Cells.CheckBoxCell(getParentActivity(), 1, getResourceProvider());
            cell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
            cell.setText(label, "", org.telegram.messenger.PrimeVpnGuard.isWhitelisted(packageName), false);
            cell.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(16) : AndroidUtilities.dp(8), 0, LocaleController.isRTL ? AndroidUtilities.dp(8) : AndroidUtilities.dp(16), 0);
            cell.setOnClickListener(v -> {
                org.telegram.ui.Cells.CheckBoxCell c = (org.telegram.ui.Cells.CheckBoxCell) v;
                org.telegram.messenger.PrimeVpnGuard.toggleWhitelist(packageName);
                c.setChecked(org.telegram.messenger.PrimeVpnGuard.isWhitelisted(packageName), true);
            });
            list.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
        }

        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                final String query = s.toString().toLowerCase(java.util.Locale.ROOT).trim();
                for (int i = 0; i < list.getChildCount(); i++) {
                    list.getChildAt(i).setVisibility(
                            query.isEmpty() || labels.get(i).contains(query) ? View.VISIBLE : View.GONE);
                }
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Не выключать прокси при этих приложениях");
        builder.setView(root);
        builder.setPositiveButton(LocaleController.getString(R.string.Done), (dialog, which) -> listView.adapter.update(true));
        showDialog(builder.create());
    }

    private void showDnsPicker() {
        if (getParentActivity() == null) {
            return;
        }
        final String[] presets = org.telegram.messenger.browser.PrimeDns.PRESET_NAMES;
        final CharSequence[] options = new CharSequence[presets.length + 1];
        System.arraycopy(presets, 0, options, 0, presets.length);
        options[presets.length] = "Свой адрес…";

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("DNS-сервер");
        builder.setItems(options, (dialog, which) -> {
            if (which == presets.length) {
                showCustomDnsDialog();
            } else {
                org.telegram.messenger.browser.PrimeDns.setPreset(which);
                listView.adapter.update(true);
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showCustomDnsDialog() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Свой DNS-сервер");
        builder.setMessage("Адрес DNS-over-HTTPS. Можно указать только имя хоста — «/dns-query» подставится само. Обычный DNS без шифрования не принимается: он свёл бы на нет весь смысл настройки.");

        final EditTextBoldCursor editText = new EditTextBoldCursor(getParentActivity());
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        editText.setHint("dns.example.com");
        editText.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setCursorSize(AndroidUtilities.dp(20));
        editText.setCursorWidth(1.5f);
        editText.setSingleLine(true);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        editText.setBackgroundDrawable(Theme.createEditTextDrawable(getParentActivity(), true));
        editText.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));
        editText.setText(org.telegram.messenger.browser.PrimeDns.getCustomEndpoint());
        editText.setSelection(editText.getText().length());

        LinearLayout container = new LinearLayout(getParentActivity());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);
        container.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        builder.setView(container);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> {
            String value = editText.getText().toString();
            if (org.telegram.messenger.browser.PrimeDns.normalizeEndpoint(value) == null) {
                org.telegram.ui.Components.BulletinFactory.of(this)
                        .createErrorBulletin("Нужен адрес https://").show();
                return;
            }
            org.telegram.messenger.browser.PrimeDns.setCustomEndpoint(value);
            org.telegram.messenger.browser.PrimeDns.setPreset(org.telegram.messenger.browser.PrimeDns.PRESET_CUSTOM);
            listView.adapter.update(true);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showPhoneInputDialog() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Свой номер для показа");
        builder.setMessage("Введите текст, который будет показан вместо вашего номера. Оставьте пустым — тогда цифры просто скроются.");

        final EditTextBoldCursor editText = new EditTextBoldCursor(getParentActivity());
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        editText.setHint("+7 900 000-00-00");
        editText.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setCursorSize(AndroidUtilities.dp(20));
        editText.setCursorWidth(1.5f);
        editText.setSingleLine(true);
        // A visible underline: without a background the field reads as empty space.
        editText.setBackgroundDrawable(Theme.createEditTextDrawable(getParentActivity(), true));
        editText.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));
        editText.setText(org.telegram.messenger.PrimeGramPrivacy.getFakePhone());
        editText.setSelection(editText.getText().length());

        LinearLayout container = new LinearLayout(getParentActivity());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);
        container.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        builder.setView(container);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> {
            org.telegram.messenger.PrimeGramPrivacy.setFakePhone(editText.getText().toString());
            listView.adapter.update(true);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void restartApp() {
        Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        android.content.Intent intent = activity.getPackageManager().getLaunchIntentForPackage(activity.getPackageName());
        if (intent == null) {
            return;
        }
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        Runtime.getRuntime().exit(0);
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    private void showNumberInputDialog(final int id, String title, String message, final int currentValue, final int defaultValue, final int min, final int max) {
        AlertDialog.Builder b = new AlertDialog.Builder(getContext(), getResourceProvider());
        b.setTitle(title);

        LinearLayout container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.VERTICAL);

        if (message != null) {
            TextView textView = new TextView(getContext());
            textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, getResourceProvider()));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            textView.setText(message);
            container.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 8, 24, 12));
        }

        final EditTextBoldCursor editText = new EditTextBoldCursor(getContext()) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(36), MeasureSpec.EXACTLY));
            }
        };
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setText(String.valueOf(currentValue));
        editText.setSelection(editText.getText().length());
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, getResourceProvider()));
        editText.setHintColor(Theme.getColor(Theme.key_groupcreate_hintText, getResourceProvider()));
        editText.setSingleLine(true);
        editText.setFocusable(true);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setLineColors(Theme.getColor(Theme.key_windowBackgroundWhiteInputField, getResourceProvider()), Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, getResourceProvider()), Theme.getColor(Theme.key_text_RedRegular, getResourceProvider()));
        editText.setBackgroundDrawable(null);
        editText.setPadding(0, 0, AndroidUtilities.dp(4), 0);

        container.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 16));
        b.setView(container);
        b.setWidth(AndroidUtilities.dp(292));

        b.setPositiveButton("Сохранить", (di, w) -> {
            try {
                int val = Integer.parseInt(editText.getText().toString());
                if (val < min || val > max) {
                    AndroidUtilities.shakeView(editText);
                    return;
                }
                saveLimit(id, val);
            } catch (Exception e) {
                AndroidUtilities.shakeView(editText);
            }
        });

        b.setNegativeButton("Отмена", null);
        b.setNeutralButton("Сбросить", (di, w) -> {
            saveLimit(id, defaultValue);
        });

        AlertDialog dialog = b.create();
        showDialog(dialog);
        editText.requestFocus();
        AndroidUtilities.showKeyboard(editText);
    }

    private void saveLimit(int id, int value) {
        MessagesController messagesController = MessagesController.getInstance(currentAccount);
        SharedPreferences.Editor editor = messagesController.getMainSettings().edit();

        if (id == ID_LIMIT_FOLDERS) {
            messagesController.dialogFiltersLimitPremium = value;
            editor.putInt("dialogFiltersLimitPremium", value);
        } else if (id == ID_LIMIT_PINNED_FOLDER) {
            messagesController.dialogFiltersPinnedLimitPremium = value;
            editor.putInt("dialogFiltersPinnedLimitPremium", value);
        } else if (id == ID_LIMIT_PINNED_SAVED) {
            messagesController.savedDialogsPinnedLimitPremium = value;
            editor.putInt("savedDialogsPinnedLimitPremium", value);
        } else if (id == ID_LIMIT_CHANNELS) {
            messagesController.channelsLimitPremium = value;
            editor.putInt("channelsLimitPremium", value);
        } else if (id == ID_LIMIT_GIFS) {
            messagesController.savedGifsLimitPremium = value;
            editor.putInt("savedGifsLimitPremium", value);
        } else if (id == ID_LIMIT_STICKERS) {
            messagesController.stickersFavedLimitPremium = value;
            editor.putInt("stickersFavedLimitPremium", value);
        } else if (id == ID_LIMIT_RECENT_STICKERS) {
            messagesController.maxRecentStickersCount = value;
            // Kept in the global settings, not the per-account ones: the trimming happens in
            // MediaDataController for every account, and MessagesController re-reads it there.
            MessagesController.getGlobalMainSettings().edit()
                    .putInt(MessagesController.PRIME_RECENT_STICKERS_KEY, value).apply();
        } else if (id == ID_LIMIT_CHATS_IN_FOLDER) {
            messagesController.dialogFiltersChatsLimitPremium = value;
            editor.putInt("dialogFiltersChatsLimitPremium", value);
        } else if (id == ID_LIMIT_PUBLIC_LINKS) {
            messagesController.publicLinksLimitPremium = value;
            editor.putInt("publicLinksLimitPremium", value);
        } else if (id == ID_LIMIT_CAPTION) {
            messagesController.captionLengthLimitPremium = value;
            editor.putInt("captionLengthLimitPremium", value);
        } else if (id == ID_LIMIT_ABOUT) {
            messagesController.aboutLengthLimitPremium = value;
            editor.putInt("aboutLengthLimitPremium", value);
        }

        editor.apply();
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }
}
