"""A plugin's settings screen, described rather than drawn.

A plugin returns a list of these from ``create_settings`` and the app builds the rows. That is the
whole design: a plugin cannot make its settings look wrong, cannot miss a theme change, and cannot
get the card corners subtly different from every other screen in the app. It also means a settings
screen can be shown for a plugin that is currently switched off, because the values behind these
rows live in Java.

``icon`` is the name of a drawable in the app - ``"msg_delete"``, ``"msg_settings"`` - resolved when
the row is built. A name that does not exist simply draws no icon.
"""

from dataclasses import dataclass, field
from typing import Any, Callable, List, Optional

TYPE_SWITCH = "switch"
TYPE_SELECTOR = "selector"
TYPE_INPUT = "input"
TYPE_TEXT = "text"
TYPE_HEADER = "header"
TYPE_DIVIDER = "divider"
TYPE_EDIT_TEXT = "edit_text"
TYPE_CUSTOM = "custom"


@dataclass
class Switch:
    key: str
    text: str
    default: bool
    subtext: str = ""
    icon: str = ""
    on_change: Callable[[bool], None] = field(default=None, compare=False, repr=False)
    type: str = field(default=TYPE_SWITCH, init=False)
    on_long_click: Callable[[Any], None] = field(default=None, compare=False, repr=False)
    link_alias: str = ""


@dataclass
class Selector:
    key: str
    text: str
    default: int
    items: List[str]
    icon: str = ""
    on_change: Callable[[int], None] = field(default=None, compare=False, repr=False)
    type: str = field(default=TYPE_SELECTOR, init=False)
    on_long_click: Callable[[Any], None] = field(default=None, compare=False, repr=False)
    link_alias: str = ""


@dataclass
class Input:
    key: str
    text: str
    default: str = ""
    subtext: str = ""
    icon: str = ""
    on_change: Callable[[str], None] = field(default=None, compare=False, repr=False)
    type: str = field(default=TYPE_INPUT, init=False)
    on_long_click: Callable[[Any], None] = field(default=None, compare=False, repr=False)
    link_alias: str = ""


@dataclass
class Text:
    text: str
    subtext: str = ""
    icon: str = ""
    accent: bool = False
    red: bool = False
    on_click: Callable[[Any], None] = field(default=None, compare=False, repr=False)
    create_sub_fragment: Callable[[], List[Any]] = field(default=None, compare=False, repr=False)
    type: str = field(default=TYPE_TEXT, init=False)
    on_long_click: Callable[[Any], None] = field(default=None, compare=False, repr=False)
    link_alias: str = ""


@dataclass
class Header:
    text: str
    type: str = field(default=TYPE_HEADER, init=False)


@dataclass
class Divider:
    text: str = ""
    type: str = field(default=TYPE_DIVIDER, init=False)


@dataclass
class EditText:
    key: str
    hint: str
    default: str = ""
    multiline: bool = False
    max_length: int = 0
    mask: str = ""
    on_change: Callable[[str], None] = field(default=None, compare=False, repr=False)
    type: str = field(default=TYPE_EDIT_TEXT, init=False)


@dataclass
class Custom:
    """An escape hatch: a plugin that supplies its own View gets it placed as a row.

    Nothing about it is themed or laid out by us, which is the trade being made. Plugins reaching
    for this are usually after a preview or a chart, and there is no honest way to describe those.

    ``view`` for a view already built. ``factory`` for one that should be built lazily or from
    ``factory_args`` - called as ``factory(context, factory_args)``, or ``factory.create(context,
    factory_args)`` if it is an object rather than a bare function - right before the row is drawn.
    """

    type: str = field(default=TYPE_CUSTOM, init=False)
    item: Any = field(default=None, compare=False, repr=False)
    view: Any = field(default=None, compare=False, repr=False)
    factory: Any = field(default=None, compare=False, repr=False)
    factory_args: Any = field(default=None, compare=False, repr=False)
    is_shadow: bool = False
    on_click: Callable[[Any], None] = field(default=None, compare=False, repr=False)
    on_long_click: Callable[[Any], None] = field(default=None, compare=False, repr=False)
    create_sub_fragment: Callable[[], List[Any]] = field(default=None, compare=False, repr=False)
    link_alias: str = ""


class SimpleSettingFactory:
    """exteraGram's documented way to build a ``Custom`` row's view without naming a Java class -
    call this, get back a ``Custom`` already carrying it as ``factory``.

    exteraGram's own version instantiates a real Java ``Factory`` subclass and can rebind an
    existing view against a new item as the list recycles it - this build has no class generator
    for that (the same gap ``ClassBuilder`` has), so ``create_view`` is called once, fresh, right
    before the row is drawn, and ``bind_view`` - accepted for the same signature real plugins
    already write, so a factory copied from one does not fail to construct - is never called;
    there is no second pass here to call it on. ``list_view``, ``class_guid`` and
    ``resources_provider`` are always ``None`` for the same reason: nothing meaningful to hand
    them exists in a single-shot fetch.

    ``on_click``/``on_long_click`` are called as ``(None, None, view)`` rather than exteraGram's
    ``(plugin, item, view)`` - the plugin and the row object are not reachable from here, only the
    view is real. A factory that only uses the ``view`` argument works as documented; one that
    reads ``plugin`` or ``item`` will find them ``None``.
    """

    def __init__(self, create_view, bind_view=None, is_clickable=False, is_shadow=False,
                 create_item=None, on_click=None, on_long_click=None, attached_view=None,
                 equals=None, content_equals=None):
        self.create_view = create_view
        self.bind_view = bind_view
        self.is_clickable = is_clickable
        self.is_shadow = is_shadow
        self.create_item = create_item
        self.on_click = on_click
        self.on_long_click = on_long_click
        self.attached_view = attached_view
        self.equals = equals
        self.content_equals = content_equals
        self._last_view = None

    def create(self, context, factory_args):
        self._last_view = self.create_view(context, None, None, None, None)
        return self._last_view

    def _click(self, _ignored):
        if self.on_click is not None:
            self.on_click(None, None, self._last_view)

    def _long_click(self, _ignored):
        if self.on_long_click is not None:
            self.on_long_click(None, None, self._last_view)

    def __call__(self, *args, **kwargs):
        factory_args = args[0] if args else kwargs.get("factory_args")
        return Custom(
            factory=self,
            factory_args=factory_args,
            is_shadow=self.is_shadow,
            on_click=self._click if self.on_click is not None else None,
            on_long_click=self._long_click if self.on_long_click is not None else None,
        )
