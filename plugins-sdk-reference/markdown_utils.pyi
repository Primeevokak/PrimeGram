from dataclasses import dataclass
from enum import Enum

__all__ = ['parse_markdown']

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

@dataclass
class RawEntity:
    type: TLEntityType
    offset: int
    length: int
    language: str | None = ...
    url: str | None = ...
    document_id: int | None = ...
    def to_tlrpc_object(self): ...

@dataclass
class ParsedMessage:
    text: str
    entities: tuple[RawEntity, ...]

def parse_markdown(markdown: str) -> ParsedMessage: ...
