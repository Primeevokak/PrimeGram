from _typeshed import Incomplete
from android.view import View
from com.exteragram.messenger.plugins import PluginsConstants
from com.exteragram.messenger.plugins.models import CustomSetting
from dataclasses import dataclass, field
from org.telegram.ui.Components import UItem
from typing import Any, Callable

@dataclass
class Switch:
    key: str
    text: str
    default: bool
    subtext: str = ...
    icon: str = ...
    on_change: Callable[[bool], None] = field(default=None, compare=False, repr=False)
    type: str = field(default=PluginsConstants.Settings.TYPE_SWITCH, init=False)
    on_long_click: Callable[[View], None] = field(default=None, compare=False, repr=False)
    link_alias: str = ...

@dataclass
class Selector:
    key: str
    text: str
    default: int
    items: list[str]
    icon: str = ...
    on_change: Callable[[int], None] = field(default=None, compare=False, repr=False)
    type: str = field(default=PluginsConstants.Settings.TYPE_SELECTOR, init=False)
    on_long_click: Callable[[View], None] = field(default=None, compare=False, repr=False)
    link_alias: str = ...

@dataclass
class Input:
    key: str
    text: str
    default: str = ...
    subtext: str = ...
    icon: str = ...
    on_change: Callable[[str], None] = field(default=None, compare=False, repr=False)
    type: str = field(default=PluginsConstants.Settings.TYPE_INPUT, init=False)
    on_long_click: Callable[[View], None] = field(default=None, compare=False, repr=False)
    link_alias: str = ...

@dataclass
class Text:
    text: str
    subtext: str = ...
    icon: str = ...
    accent: bool = ...
    red: bool = ...
    on_click: Callable[[View], None] = field(default=None, compare=False, repr=False)
    create_sub_fragment: Callable[[], list[Any]] = field(default=None, compare=False, repr=False)
    type: str = field(default=PluginsConstants.Settings.TYPE_TEXT, init=False)
    on_long_click: Callable[[View], None] = field(default=None, compare=False, repr=False)
    link_alias: str = ...

@dataclass
class Header:
    text: str
    type: str = field(default=PluginsConstants.Settings.TYPE_HEADER, init=False)

@dataclass
class Divider:
    text: str = ...
    type: str = field(default=PluginsConstants.Settings.TYPE_DIVIDER, init=False)

@dataclass
class EditText:
    key: str
    hint: str
    default: str = ...
    multiline: bool = ...
    max_length: int = ...
    mask: str = ...
    on_change: Callable[[str], None] = field(default=None, compare=False, repr=False)
    type: str = field(default=PluginsConstants.Settings.TYPE_EDIT_TEXT, init=False)

@dataclass
class Custom:
    type: str = field(default=PluginsConstants.Settings.TYPE_CUSTOM, init=False)
    item: UItem = field(default=None, compare=False, repr=False)
    view: View = field(default=None, compare=False, repr=False)
    factory: CustomSetting.Factory = field(default=None, compare=False, repr=False)
    factory_args: Any = field(default=None, compare=False, repr=False)
    on_click: Callable[[View], None] = field(default=None, compare=False, repr=False)
    on_long_click: Callable[[View], None] = field(default=None, compare=False, repr=False)
    create_sub_fragment: Callable[[], list[Any]] = field(default=None, compare=False, repr=False)
    link_alias: str = ...
    def __post_init__(self) -> None: ...

pyobject_type: Incomplete
object_array_type: Incomplete
typehelper: Incomplete
pyobject_call_method: Incomplete

class SimpleSettingFactory:
    create_view: Incomplete
    bind_view: Incomplete
    is_clickable: Incomplete
    is_shadow: Incomplete
    create_item: Incomplete
    on_click: Incomplete
    on_long_click: Incomplete
    attached_view: Incomplete
    equals: Incomplete
    content_equals: Incomplete
    instance: Incomplete
    def __init__(self, create_view: callable, bind_view: callable, *, is_clickable: bool = False, is_shadow: bool = False, create_item: callable | None = None, on_click: callable | None = None, on_long_click: callable | None = None, attached_view: callable | None = None, equals: callable | None = None, content_equals: callable | None = None) -> None: ...
    def __call__(self, *args, create_sub_fragment=None, link_alias=None): ...
