"""The bar that slides up from the bottom - the app's own, not a lookalike.

Every method here ends up on the UI thread whether the plugin remembered or not. A plugin reporting
that something worked is almost always doing it from the thread that did the work, and making that
crash would be a trap rather than an API.
"""

from org.telegram.messenger import R
from org.telegram.ui.Components import Bulletin, BulletinFactory

from android_utils import run_on_ui_thread, log
import client_utils


class BulletinHelper:
    DURATION_SHORT = Bulletin.DURATION_SHORT
    DURATION_LONG = Bulletin.DURATION_LONG
    DURATION_PROLONG = Bulletin.DURATION_PROLONG

    @staticmethod
    def _factory(fragment):
        target = fragment or client_utils.get_last_fragment()
        if target is None:
            # No window means nobody to show it to. Losing the message is the right failure: the
            # alternative is a bulletin queued against a screen that will never exist.
            return None
        return BulletinFactory.of(target)

    @classmethod
    def _show(cls, fragment, build):
        def run():
            factory = cls._factory(fragment)
            if factory is None:
                return
            try:
                build(factory).show()
            except Exception as e:
                log("bulletin failed: %r" % (e,))

        run_on_ui_thread(run)

    @classmethod
    def show_info(cls, message, fragment=None):
        cls._show(fragment, lambda f: f.createSimpleBulletin(R.raw.info, str(message)))

    @classmethod
    def show_error(cls, message, fragment=None):
        cls._show(fragment, lambda f: f.createErrorBulletin(str(message)))

    @classmethod
    def show_success(cls, message, fragment=None):
        cls._show(fragment, lambda f: f.createSuccessBulletin(str(message)))

    @classmethod
    def show_simple(cls, text, icon_res_id, fragment=None):
        cls._show(fragment, lambda f: f.createSimpleBulletin(icon_res_id, str(text)))

    @classmethod
    def show_two_line(cls, title, subtitle, icon_res_id, fragment=None):
        cls._show(fragment, lambda f: f.createSimpleBulletin(icon_res_id, str(title), str(subtitle)))

    @classmethod
    def show_with_button(cls, text, icon_res_id, button_text, on_click,
                         fragment=None, duration=Bulletin.DURATION_SHORT):
        from android_utils import _Runnable
        action = _Runnable(on_click) if on_click is not None else None
        cls._show(fragment, lambda f: f.createSimpleBulletin(
            icon_res_id, str(text), str(button_text), int(duration), action))

    @classmethod
    def show_undo(cls, text, on_undo, on_action=None, subtitle=None, fragment=None):
        from android_utils import _Runnable
        undo = _Runnable(on_undo)
        if subtitle:
            cls._show(fragment, lambda f: f.createSimpleBulletin(
                R.raw.chats_infotip, str(text), str(subtitle), "Отменить", undo))
        else:
            cls._show(fragment, lambda f: f.createSimpleBulletin(
                R.raw.chats_infotip, str(text), "Отменить", undo))

    @classmethod
    def show_copied_to_clipboard(cls, message=None, fragment=None):
        cls.show_simple(message or "Скопировано в буфер обмена", R.raw.copy, fragment)

    @classmethod
    def show_link_copied(cls, is_private_link_info=False, fragment=None):
        cls.show_simple("Ссылка скопирована", R.raw.copy, fragment)

    @classmethod
    def show_file_saved_to_gallery(cls, is_video=False, amount=1, fragment=None):
        cls.show_simple("Видео сохранено в галерею" if is_video else "Фото сохранено в галерею",
                        R.raw.ic_save_to_gallery, fragment)

    @classmethod
    def show_file_saved_to_downloads(cls, file_type_enum_name="UNKNOWN", amount=1, fragment=None):
        cls.show_simple("Файл сохранён в загрузки", R.raw.ic_download, fragment)
