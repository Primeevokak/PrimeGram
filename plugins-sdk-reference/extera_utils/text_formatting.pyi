from _typeshed import Incomplete
from dataclasses import dataclass
from enum import Enum
from html.parser import HTMLParser
from typing import Any

def add_surrogates(text: str) -> str: ...
def remove_surrogates(text: str) -> str: ...

class TLEntityType(Enum):
    CODE = 'code'
    PRE = 'pre'
    STRIKETHROUGH = 'strikethrough'
    TEXT_LINK = 'text_link'
    BOLD = 'bold'
    ITALIC = 'italic'
    UNDERLINE = 'underline'
    SPOILER = 'spoiler'
    CUSTOM_EMOJI = 'custom_emoji'
    BLOCKQUOTE = 'blockquote'
    @classmethod
    def from_(cls, entity): ...

@dataclass
class RawEntity:
    @property
    def TLRPC_ENTITIES_MAP(self): ...
    type: TLEntityType
    offset: int
    length: int
    language: str | None = ...
    url: str | None = ...
    document_id: int | None = ...
    collapsed: bool | None = ...
    def to_tlrpc_object(self): ...

class Parser(HTMLParser):
    text: str
    entities: Incomplete
    tag_entities: Incomplete
    def __init__(self) -> None: ...
    def handle_starttag(self, tag, attrs) -> None: ...
    def handle_data(self, data) -> None: ...
    def handle_endtag(self, tag) -> None: ...

class HTML:
    @staticmethod
    def parse(text: str) -> dict: ...
    @staticmethod
    def unparse(text: str, entities: list) -> str: ...

BOLD_DELIM: str
ITALIC_DELIM: str
UNDERLINE_DELIM: str
STRIKE_DELIM: str
SPOILER_DELIM: str
CODE_DELIM: str
PRE_DELIM: str
BLOCKQUOTE_DELIM: str
BLOCKQUOTE_EXPANDABLE_DELIM: str
BLOCKQUOTE_EXPANDABLE_END_DELIM: str
MARKDOWN_RE: Incomplete
OPENING_TAG: str
CLOSING_TAG: str
URL_MARKUP: str
EMOJI_MARKUP: str
FIXED_WIDTH_DELIMS: Incomplete
MARKDOWN_ESCAPABLE_CHARS: str
ESCAPED_MARKDOWN_RE: Incomplete

def replace_once(source: str, old: str, new: str, start: int): ...

class Markdown:
    @staticmethod
    def protect_escaped_chars(text: str) -> tuple[str, dict[str, str]]: ...
    @staticmethod
    def restore_escaped_chars(text: str, escaped_chars: dict[str, str]) -> str: ...
    @staticmethod
    def escape_and_create_quotes(text: str, strict: bool): ...
    @classmethod
    def parse(cls, text: str, strict: bool = False): ...
    @staticmethod
    def unparse(text: str, entities: list): ...

def parse_text(text: str, parse_mode: str | None = 'HTML', is_caption: bool = False) -> dict[str, Any]: ...
