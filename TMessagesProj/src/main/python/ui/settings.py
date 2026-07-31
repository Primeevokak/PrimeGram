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
    """

    type: str = field(default=TYPE_CUSTOM, init=False)
    item: Any = field(default=None, compare=False, repr=False)
    view: Any = field(default=None, compare=False, repr=False)
    factory: Any = field(default=None, compare=False, repr=False)
    factory_args: Any = field(default=None, compare=False, repr=False)
    on_click: Callable[[Any], None] = field(default=None, compare=False, repr=False)
    on_long_click: Callable[[Any], None] = field(default=None, compare=False, repr=False)
    create_sub_fragment: Callable[[], List[Any]] = field(default=None, compare=False, repr=False)
    link_alias: str = ""
