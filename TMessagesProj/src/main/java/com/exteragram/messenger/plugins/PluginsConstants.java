package com.exteragram.messenger.plugins;

/**
 * PrimeGram: compatibility shim for {@code com.exteragram.messenger.plugins.PluginsConstants}.
 *
 * <p>The real class's string values are obfuscated in the exteraGram APK and unrecoverable from
 * decompilation; nothing here needs to match them byte for byte, because the documented Python
 * SDK (which is what almost every plugin actually goes through - {@code base_plugin.pyi}'s
 * {@code HookStrategy}, {@code HookFilter}, {@code AppEvent}, {@code MenuItemType} enums) never
 * reads these fields directly. What matters is that every name a plugin might reference exists
 * and holds a distinct, non-null value, so nothing here breaks a comparison or a dict lookup a
 * plugin does against its own constant, not against exteraGram's real backend protocol.
 */
public final class PluginsConstants {

    private PluginsConstants() {
    }

    public static final String PYTHON = "python";
    public static final String SEND_MESSAGE_HOOK = "send_message_hook";
    public static final String STRATEGY = "strategy";
    public static final String PARAMS = "params";
    public static final String UPDATE = "update";
    public static final String UPDATES = "updates";
    public static final String REQUEST = "request";
    public static final String RESPONSE = "response";
    public static final String ERROR = "error";
    public static final String PLUGINS = "plugins";
    public static final String PLUGINS_EXT = ".plugin";
    public static final String PLUGINS_SDK = "plugins_sdk";
    public static final String CREATE_SETTINGS = "create_settings";
    public static final String APP_START = "app_start";
    public static final String APP_STOP = "app_stop";
    public static final String APP_PAUSE = "app_pause";
    public static final String APP_RESUME = "app_resume";
    public static final String ON_APP_EVENT = "on_app_event";
    public static final String ON_PLUGIN_LOAD = "on_plugin_load";
    public static final String ON_PLUGIN_UNLOAD = "on_plugin_unload";

    public static final class DevServer {
        private DevServer() {
        }

        public static final String MODULE = "module";
        public static final String CLASS = "class";
        public static final String START_SERVER = "start_server";
        public static final String STOP_SERVER = "stop_server";
    }

    public static final class HookFilterTypes {
        private HookFilterTypes() {
        }

        public static final String RESULT_IS_NULL = "RESULT_IS_NULL";
        public static final String RESULT_IS_TRUE = "RESULT_IS_TRUE";
        public static final String RESULT_IS_FALSE = "RESULT_IS_FALSE";
        public static final String RESULT_NOT_NULL = "RESULT_NOT_NULL";
        public static final String RESULT_IS_INSTANCE_OF = "RESULT_IS_INSTANCE_OF";
        public static final String RESULT_EQUAL = "RESULT_EQUAL";
        public static final String RESULT_NOT_EQUAL = "RESULT_NOT_EQUAL";
        public static final String ARGUMENT_IS_NULL = "ARGUMENT_IS_NULL";
        public static final String ARGUMENT_IS_TRUE = "ARGUMENT_IS_TRUE";
        public static final String ARGUMENT_IS_FALSE = "ARGUMENT_IS_FALSE";
        public static final String ARGUMENT_NOT_NULL = "ARGUMENT_NOT_NULL";
        public static final String ARGUMENT_IS_INSTANCE_OF = "ARGUMENT_IS_INSTANCE_OF";
        public static final String ARGUMENT_EQUAL = "ARGUMENT_EQUAL";
        public static final String ARGUMENT_NOT_EQUAL = "ARGUMENT_NOT_EQUAL";
        public static final String CONDITION = "CONDITION";
        public static final String OR = "OR";
    }

    public static final class MenuItemProperties {
        private MenuItemProperties() {
        }

        public static final String MENU_TYPE = "menu_type";
        public static final String ITEM_ID = "item_id";
        public static final String TEXT = "text";
        public static final String SUBTEXT = "subtext";
        public static final String ICON = "icon";
        public static final String ON_CLICK = "on_click";
        public static final String CONDITION = "condition";
        public static final String PRIORITY = "priority";
    }

    public static final class MenuItemTypes {
        private MenuItemTypes() {
        }

        public static final String MESSAGE_CONTEXT_MENU = "message_context_menu";
        public static final String DRAWER_MENU = "drawer_menu";
        public static final String MAIN_MENU = "main_menu";
        public static final String CHAT_ACTION_MENU = "chat_action_menu";
        public static final String PROFILE_ACTION_MENU = "profile_action_menu";
    }

    public static final class Settings {
        private Settings() {
        }

        public static final String TYPE = "type";
        public static final String KEY = "key";
        public static final String TEXT = "text";
        public static final String SUBTEXT = "subtext";
        public static final String ICON = "icon";
        public static final String ACCENT = "accent";
        public static final String RED = "red";
        public static final String ON_CLICK = "on_click";
        public static final String DEFAULT = "default";
        public static final String ITEMS = "items";
        public static final String HINT = "hint";
        public static final String MULTILINE = "multiline";
        public static final String MAX_LENGTH = "max_length";
        public static final String MASK = "mask";
        public static final String ON_CHANGE = "on_change";
        public static final String TYPE_SWITCH = "switch";
        public static final String TYPE_INPUT = "input";
        public static final String TYPE_SELECTOR = "selector";
        public static final String TYPE_HEADER = "header";
        public static final String TYPE_DIVIDER = "divider";
        public static final String TYPE_TEXT = "text";
        public static final String TYPE_EDIT_TEXT = "edit_text";
        public static final String TYPE_CUSTOM = "custom";
        public static final String VIEW = "view";
        public static final String ITEM = "item";
        public static final String FACTORY = "factory";
        public static final String FACTORY_ARGS = "factory_args";
        public static final String CREATE_SUB_FRAGMENT = "create_sub_fragment";
        public static final String ON_LONG_CLICK = "on_long_click";
        public static final String LINK_ALIAS = "link_alias";
    }

    public static final class Strategy {
        private Strategy() {
        }

        public static final String MODIFY = "MODIFY";
        public static final String CANCEL = "CANCEL";
        public static final String DEFAULT = "DEFAULT";
        public static final String MODIFY_FINAL = "MODIFY_FINAL";
    }

    public static final class Xposed {
        private Xposed() {
        }

        public static final String REPLACE_HOOKED_METHOD = "replace_hooked_method";
        public static final String BEFORE_HOOKED_METHOD = "before_hooked_method";
        public static final String AFTER_HOOKED_METHOD = "after_hooked_method";
        public static final String HOOK_FILTERS = "hook_filters";
    }
}
