"""Dialogs, built on the app's own AlertDialog rather than Android's.

Chained setters that return ``self``, so a plugin reads the way the SDK documents it. Every
listener a plugin passes is called with the *builder*, not the Java dialog: a plugin that wants to
close the thing calls ``builder.dismiss()``, and handing it a raw Java dialog would mean every
plugin re-implementing that lookup.
"""

from android.content import DialogInterface
from java import dynamic_proxy, jarray
from java.lang import CharSequence

from org.telegram.ui.ActionBar import AlertDialog

from android_utils import log


def _guard(fn, *args):
    try:
        if fn is not None:
            fn(*args)
    except Exception as e:
        log("dialog callback failed: %r" % (e,))


class _ButtonClickListenerProxy(dynamic_proxy(AlertDialog.OnButtonClickListener)):
    def __init__(self, py_callable, builder_instance):
        super().__init__()
        self._fn = py_callable
        self._builder = builder_instance

    def onClick(self, dialog_java_instance, which):
        _guard(self._fn, self._builder, which)


class _ItemsClickListenerProxy(dynamic_proxy(DialogInterface.OnClickListener)):
    def __init__(self, py_callable, builder_instance):
        super().__init__()
        self._fn = py_callable
        self._builder = builder_instance

    def onClick(self, dialog_java_instance, which):
        _guard(self._fn, self._builder, which)


class _DismissListenerProxy(dynamic_proxy(DialogInterface.OnDismissListener)):
    def __init__(self, py_callable, builder_instance):
        super().__init__()
        self._fn = py_callable
        self._builder = builder_instance

    def onDismiss(self, dialog_java_instance):
        _guard(self._fn, self._builder)


class _CancelListenerProxy(dynamic_proxy(DialogInterface.OnCancelListener)):
    def __init__(self, py_callable, builder_instance):
        super().__init__()
        self._fn = py_callable
        self._builder = builder_instance

    def onCancel(self, dialog_java_instance):
        _guard(self._fn, self._builder)


class AlertDialogBuilder:
    ALERT_TYPE_MESSAGE = 0
    ALERT_TYPE_LOADING = 1
    ALERT_TYPE_SPINNER = 3

    BUTTON_POSITIVE = -1
    BUTTON_NEGATIVE = -2
    BUTTON_NEUTRAL = -3

    def __init__(self, context, progress_style=ALERT_TYPE_MESSAGE, resources_provider=None):
        self._context = context
        self._builder = AlertDialog.Builder(context, int(progress_style), resources_provider)
        self._dialog = None

    def get_context(self):
        return self._context

    def set_title(self, title):
        self._builder.setTitle(str(title))
        return self

    def set_message(self, message):
        self._builder.setMessage(str(message))
        return self

    def set_message_text_view_clickable(self, clickable):
        self._builder.setMessageTextViewClickable(bool(clickable))
        return self

    def set_positive_button(self, text, listener=None):
        self._builder.setPositiveButton(str(text), _ButtonClickListenerProxy(listener, self))
        return self

    def set_negative_button(self, text, listener=None):
        self._builder.setNegativeButton(str(text), _ButtonClickListenerProxy(listener, self))
        return self

    def set_neutral_button(self, text, listener=None):
        self._builder.setNeutralButton(str(text), _ButtonClickListenerProxy(listener, self))
        return self

    def make_button_red(self, button_type):
        self._builder.makeRed(int(button_type))
        return self

    def set_on_back_button_listener(self, listener=None):
        self._builder.setOnBackButtonListener(_ButtonClickListenerProxy(listener, self))
        return self

    def set_view(self, view, height=-2):
        self._builder.setView(view, int(height))
        return self

    def set_items(self, items, listener=None, icons=None):
        texts = jarray(CharSequence)([str(item) for item in items])
        if icons:
            self._builder.setItems(texts, jarray(int)(list(icons)),
                                   _ItemsClickListenerProxy(listener, self))
        else:
            self._builder.setItems(texts, _ItemsClickListenerProxy(listener, self))
        return self

    def set_on_dismiss_listener(self, listener=None):
        self._builder.setOnDismissListener(_DismissListenerProxy(listener, self))
        return self

    def set_on_cancel_listener(self, listener=None):
        self._builder.setOnCancelListener(_CancelListenerProxy(listener, self))
        return self

    def set_top_image(self, res_id, background_color):
        self._builder.setTopImage(int(res_id), int(background_color))
        return self

    def set_top_drawable(self, drawable, background_color):
        self._builder.setTopImage(drawable, int(background_color))
        return self

    def set_top_animation(self, res_id, size, auto_repeat, background_color, layer_colors=None):
        if layer_colors:
            from java.util import HashMap
            colors = HashMap()
            for key, value in layer_colors.items():
                colors.put(str(key), int(value))
            self._builder.setTopAnimation(int(res_id), int(size), bool(auto_repeat),
                                          int(background_color), colors)
        else:
            self._builder.setTopAnimation(int(res_id), int(size), bool(auto_repeat),
                                          int(background_color))
        return self

    def set_top_animation_is_new(self, is_new):
        self._builder.setTopAnimationIsNew(bool(is_new))
        return self

    def set_dim_enabled(self, enabled):
        self._builder.setDimEnabled(bool(enabled))
        return self

    def set_dialog_button_color_key(self, theme_key):
        self._builder.setDialogButtonColorKey(int(theme_key))
        return self

    def set_blurred_background(self, blur, blur_behind_if_possible=True):
        self._builder.setBlurredBackground(bool(blur))
        return self

    def create(self):
        self._dialog = self._builder.create()
        return self

    def show(self):
        # Building without creating is the common shape in plugins, so create on the way through
        # rather than making show() the one call with an order requirement.
        if self._dialog is None:
            self._dialog = self._builder.create()
        self._dialog.show()
        return self

    def dismiss(self):
        if self._dialog is not None:
            self._dialog.dismiss()

    def get_dialog(self):
        return self._dialog

    def get_button(self, button_type):
        return self._dialog.getButton(int(button_type)) if self._dialog is not None else None

    def set_progress(self, progress):
        if self._dialog is not None:
            self._dialog.setProgress(int(progress))
        return self

    def set_cancelable(self, cancelable):
        if self._dialog is not None:
            self._dialog.setCancelable(bool(cancelable))
        return self

    def set_canceled_on_touch_outside(self, cancel):
        if self._dialog is not None:
            self._dialog.setCanceledOnTouchOutside(bool(cancel))
        return self
