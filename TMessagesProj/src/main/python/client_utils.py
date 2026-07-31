"""Talking to the client: accounts, controllers, requests, sending.

Almost everything here is a one-line bridge to a Java singleton, and the only real idea in the
module is the *current account*. Telegram for Android is multi-account, every controller is
per-account, and a plugin that hardcodes account 0 works perfectly until the user adds a second
one. So each call takes an optional account, and when it is left out the answer comes from a
context variable: whichever account the hook we are running inside belongs to, falling back to the
one the user is looking at. A context variable rather than a global because hooks on different
accounts can be in flight at the same time.
"""

import contextlib
from contextvars import ContextVar

from java import dynamic_proxy, jclass
from java.util import ArrayList

from org.telegram.messenger import AccountInstance, MediaController, NotificationCenter, Utilities, UserConfig
from org.telegram.messenger.plugins import PrimePluginSend
from org.telegram.tgnet import ConnectionsManager, RequestDelegate

STAGE_QUEUE = "stageQueue"
GLOBAL_QUEUE = "globalQueue"
CACHE_CLEAR_QUEUE = "cacheClearQueue"
SEARCH_QUEUE = "searchQueue"
PHONE_BOOK_QUEUE = "phoneBookQueue"
THEME_QUEUE = "themeQueue"
EXTERNAL_NETWORK_QUEUE = "externalNetworkQueue"
PLUGINS_QUEUE = "pluginsQueue"

_hook_account = ContextVar("prime_hook_account", default=None)


def get_selected_account():
    """The account the user is currently looking at."""
    return UserConfig.selectedAccount


def get_hook_account():
    """The account whose hook we are inside, or None when we are not inside one."""
    return _hook_account.get()


@contextlib.contextmanager
def account_scope(account):
    token = _hook_account.set(account)
    try:
        yield
    finally:
        _hook_account.reset(token)


def _account(account=None):
    if account is not None:
        return int(account)
    hooked = _hook_account.get()
    return int(hooked) if hooked is not None else get_selected_account()


def get_queue_by_name(queue_name):
    if queue_name == PLUGINS_QUEUE:
        from org.telegram.messenger.plugins import PrimePythonEngine
        return PrimePythonEngine.getInstance().queue()
    try:
        return getattr(Utilities, queue_name)
    except AttributeError:
        return None


def run_on_queue(fn, queue_name=GLOBAL_QUEUE, delay=0):
    from android_utils import _Runnable
    queue = get_queue_by_name(queue_name)
    if queue is None:
        return None
    return queue.postRunnable(_Runnable(fn), int(delay))


class RequestCallback(dynamic_proxy(RequestDelegate)):
    """Carries the account across the network boundary, so the reply runs in the right scope."""

    def __init__(self, fn, account=None):
        super().__init__()
        self._fn = fn
        self._account = _account(account)

    def run(self, response, error):
        from android_utils import log
        try:
            with account_scope(self._account):
                self._fn(response, error)
        except Exception as e:
            log("request callback failed: %r" % (e,))


def send_request(request, fn, *, account=None):
    acc = _account(account)
    return ConnectionsManager.getInstance(acc).sendRequest(request, RequestCallback(fn, acc))


def get_last_fragment():
    """The screen on top, or None when the app has no window - a plugin may run before it does."""
    LaunchActivity = jclass("org.telegram.ui.LaunchActivity")
    return LaunchActivity.getLastFragment()


def get_account_instance(account=None):
    return AccountInstance.getInstance(_account(account))


def get_messages_controller(account=None):
    return get_account_instance(account).getMessagesController()


def get_contacts_controller(account=None):
    return get_account_instance(account).getContactsController()


def get_media_data_controller(account=None):
    return get_account_instance(account).getMediaDataController()


def get_connections_manager(account=None):
    return get_account_instance(account).getConnectionsManager()


def get_location_controller(account=None):
    return get_account_instance(account).getLocationController()


def get_notifications_controller(account=None):
    return get_account_instance(account).getNotificationsController()


def get_messages_storage(account=None):
    return get_account_instance(account).getMessagesStorage()


def get_send_messages_helper(account=None):
    return get_account_instance(account).getSendMessagesHelper()


def get_file_loader(account=None):
    return get_account_instance(account).getFileLoader()


def get_secret_chat_helper(account=None):
    return get_account_instance(account).getSecretChatHelper()


def get_download_controller(account=None):
    return get_account_instance(account).getDownloadController()


def get_notifications_settings(account=None):
    return get_account_instance(account).getNotificationsSettings()


def get_notification_center(account=None):
    return get_account_instance(account).getNotificationCenter()


def get_media_controller():
    return MediaController.getInstance()


def get_user_config(account=None):
    return get_account_instance(account).getUserConfig()


def _parse(text, parse_mode):
    """Turns marked-up text into plain text plus entities. Unknown modes are left alone."""
    if not parse_mode or not text:
        return text, None
    try:
        import markdown_utils
        parsed = markdown_utils.parse_text(text, parse_mode)
    except Exception:
        return text, None
    entities = ArrayList()
    for entity in parsed.get("entities") or ():
        java_entity = entity.to_tlrpc_object() if hasattr(entity, "to_tlrpc_object") else entity
        if java_entity is not None:
            entities.add(java_entity)
    return parsed.get("text", text), (entities if entities.size() else None)


def send_text(peer, text, *, account=None, parse_mode=None, **kwargs):
    message, entities = _parse(text, parse_mode)
    PrimePluginSend.sendText(_account(account), int(peer), message, entities)


def send_photo(peer, file_path, caption="", high_quality=False, *, account=None, parse_mode=None, **kwargs):
    text, entities = _parse(caption, parse_mode)
    PrimePluginSend.sendPhoto(_account(account), int(peer), file_path, text or "", entities)


def send_video(peer, file_path, caption="", *, account=None, parse_mode=None, **kwargs):
    text, entities = _parse(caption, parse_mode)
    PrimePluginSend.sendVideo(_account(account), int(peer), file_path, text or "", entities)


def send_document(peer, file_path, caption="", *, account=None, parse_mode=None, **kwargs):
    text, _ = _parse(caption, parse_mode)
    PrimePluginSend.sendDocument(_account(account), int(peer), file_path, text or "")


def send_audio(peer, file_path, caption="", *, account=None, parse_mode=None, **kwargs):
    # Audio goes out as a document on purpose: the sender reads the file's own metadata and
    # attaches audio attributes when it finds them, which is how the app itself sends music.
    send_document(peer, file_path, caption, account=account, parse_mode=parse_mode)


def send_message(params, parse_mode=None, *, account=None):
    """The dict form. ``peer`` plus one of ``message`` / ``path``, as the SDK has always had it."""
    if not isinstance(params, dict):
        raise TypeError("send_message expects a dict")
    peer = params.get("peer")
    if peer is None:
        raise ValueError("send_message needs a peer")
    mode = params.get("parse_mode", parse_mode)
    path = params.get("path") or params.get("file")
    caption = params.get("caption", "")
    if path:
        kind = (params.get("type") or "").lower()
        if kind == "photo" or params.get("photo"):
            return send_photo(peer, path, caption, account=account, parse_mode=mode)
        if kind == "video" or params.get("video"):
            return send_video(peer, path, caption, account=account, parse_mode=mode)
        if kind == "audio" or params.get("audio"):
            return send_audio(peer, path, caption, account=account, parse_mode=mode)
        return send_document(peer, path, caption, account=account, parse_mode=mode)
    return send_text(peer, params.get("message", ""), account=account, parse_mode=mode)


def edit_message(message_obj, text=None, file_path=None, with_spoiler=False, *,
                 account=None, parse_mode=None, **kwargs):
    if file_path:
        raise NotImplementedError("editing media is not supported; send a new message instead")
    message, entities = _parse(text, parse_mode)
    PrimePluginSend.editText(
        _account(account), message_obj.getDialogId(), message_obj.getId(), message or "", entities)


class AccountClient:
    """Every function above, bound to one account. Handed to plugins as ``self.client()``."""

    def __init__(self, account):
        self.account = int(account)

    def __eq__(self, other):
        return isinstance(other, AccountClient) and other.account == self.account

    def __hash__(self):
        return hash(self.account)

    def get_account_instance(self):
        return get_account_instance(self.account)

    def get_messages_controller(self):
        return get_messages_controller(self.account)

    def get_contacts_controller(self):
        return get_contacts_controller(self.account)

    def get_media_data_controller(self):
        return get_media_data_controller(self.account)

    def get_connections_manager(self):
        return get_connections_manager(self.account)

    def get_location_controller(self):
        return get_location_controller(self.account)

    def get_notifications_controller(self):
        return get_notifications_controller(self.account)

    def get_messages_storage(self):
        return get_messages_storage(self.account)

    def get_send_messages_helper(self):
        return get_send_messages_helper(self.account)

    def get_file_loader(self):
        return get_file_loader(self.account)

    def get_secret_chat_helper(self):
        return get_secret_chat_helper(self.account)

    def get_download_controller(self):
        return get_download_controller(self.account)

    def get_notifications_settings(self):
        return get_notifications_settings(self.account)

    def get_notification_center(self):
        return get_notification_center(self.account)

    def get_user_config(self):
        return get_user_config(self.account)

    def send_request(self, request, fn):
        return send_request(request, fn, account=self.account)

    def send_message(self, params, parse_mode=None):
        return send_message(params, parse_mode, account=self.account)

    def send_text(self, peer, text, *, parse_mode=None, **kwargs):
        return send_text(peer, text, account=self.account, parse_mode=parse_mode, **kwargs)

    def send_photo(self, peer, file_path, caption="", high_quality=False, *, parse_mode=None, **kwargs):
        return send_photo(peer, file_path, caption, high_quality, account=self.account,
                          parse_mode=parse_mode, **kwargs)

    def send_document(self, peer, file_path, caption="", *, parse_mode=None, **kwargs):
        return send_document(peer, file_path, caption, account=self.account,
                             parse_mode=parse_mode, **kwargs)

    def send_video(self, peer, file_path, caption="", *, parse_mode=None, **kwargs):
        return send_video(peer, file_path, caption, account=self.account,
                          parse_mode=parse_mode, **kwargs)

    def send_audio(self, peer, file_path, caption="", *, parse_mode=None, **kwargs):
        return send_audio(peer, file_path, caption, account=self.account,
                          parse_mode=parse_mode, **kwargs)

    def edit_message(self, message_obj, text=None, file_path=None, with_spoiler=False, *,
                     parse_mode=None, **kwargs):
        return edit_message(message_obj, text, file_path, with_spoiler, account=self.account,
                            parse_mode=parse_mode, **kwargs)


class NotificationCenterDelegate(dynamic_proxy(NotificationCenter.NotificationCenterDelegate)):
    """Subclass this and override ``didReceivedNotification`` to listen to the app's own events.

    Kept as a class rather than a decorator because the app hands out and takes back delegates by
    identity: a plugin has to hold the same object to stop listening that it used to start.
    """

    def __init__(self):
        super().__init__()

    def didReceivedNotification(self, id, account, args):
        pass


_clients = {}


def get_client(account=None):
    acc = _account(account)
    client = _clients.get(acc)
    if client is None:
        client = _clients[acc] = AccountClient(acc)
    return client
