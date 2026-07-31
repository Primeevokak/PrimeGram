"""Markdown in, plain text plus entities out.

Telegram does not send styled text; it sends flat text with a list of ranges. So a plugin that
writes ``**hello**`` needs someone to turn that into ``hello`` and one bold entity covering it, and
that is all this module does.

The one subtlety worth stating: offsets are counted in **UTF-16 code units**, not characters,
because that is what the protocol and the Java client both use. Every emoji outside the basic plane
counts as two. Getting this wrong is invisible in testing with Latin text and mangles every message
containing an emoji before the formatting, which is most of them.
"""

from dataclasses import dataclass
from enum import Enum
from typing import List, Optional, Tuple

__all__ = ["parse_markdown", "parse_text", "TLEntityType", "RawEntity", "ParsedMessage"]


class TLEntityType(Enum):
    CODE = "code"
    PRE = "pre"
    STRIKETHROUGH = "strikethrough"
    TEXT_LINK = "text_link"
    BOLD = "bold"
    ITALIC = "italic"
    UNDERLINE = "underline"
    SPOILER = "spoiler"
    CUSTOM_EMOJI = "custom_emoji"
    BLOCKQUOTE = "blockquote"


@dataclass
class RawEntity:
    type: TLEntityType
    offset: int
    length: int
    language: Optional[str] = None
    url: Optional[str] = None
    document_id: Optional[int] = None
    collapsed: Optional[bool] = None

    def to_tlrpc_object(self):
        """The Java entity the client actually sends. Built here so plugins never touch TLRPC."""
        from org.telegram.tgnet import TLRPC

        if self.type is TLEntityType.BOLD:
            entity = TLRPC.TL_messageEntityBold()
        elif self.type is TLEntityType.ITALIC:
            entity = TLRPC.TL_messageEntityItalic()
        elif self.type is TLEntityType.UNDERLINE:
            entity = TLRPC.TL_messageEntityUnderline()
        elif self.type is TLEntityType.STRIKETHROUGH:
            entity = TLRPC.TL_messageEntityStrike()
        elif self.type is TLEntityType.SPOILER:
            entity = TLRPC.TL_messageEntitySpoiler()
        elif self.type is TLEntityType.CODE:
            entity = TLRPC.TL_messageEntityCode()
        elif self.type is TLEntityType.PRE:
            entity = TLRPC.TL_messageEntityPre()
            entity.language = self.language or ""
        elif self.type is TLEntityType.BLOCKQUOTE:
            entity = TLRPC.TL_messageEntityBlockquote()
        elif self.type is TLEntityType.TEXT_LINK:
            entity = TLRPC.TL_messageEntityTextUrl()
            entity.url = self.url or ""
        elif self.type is TLEntityType.CUSTOM_EMOJI:
            entity = TLRPC.TL_messageEntityCustomEmoji()
            entity.document_id = int(self.document_id or 0)
        else:
            return None
        entity.offset = self.offset
        entity.length = self.length
        return entity


@dataclass
class ParsedMessage:
    text: str
    entities: Tuple[RawEntity, ...]


def _utf16_len(text):
    return sum(2 if ord(char) > 0xFFFF else 1 for char in text)


#: Longest first, so ``**`` is never read as two ``*``.
_DELIMITERS = (
    ("```", TLEntityType.PRE),
    ("**", TLEntityType.BOLD),
    ("__", TLEntityType.ITALIC),
    ("~~", TLEntityType.STRIKETHROUGH),
    ("||", TLEntityType.SPOILER),
    ("`", TLEntityType.CODE),
)


def parse_markdown(markdown):
    text = []
    entities = []
    offset = 0
    i = 0
    length = len(markdown)

    while i < length:
        char = markdown[i]

        if char == "\\" and i + 1 < length:
            # An escaped delimiter is a literal one. Without this there is no way to send a plain
            # asterisk, and plugins that quote user text hit that immediately.
            text.append(markdown[i + 1])
            offset += _utf16_len(markdown[i + 1])
            i += 2
            continue

        if char == "[":
            close = markdown.find("](", i)
            end = markdown.find(")", close + 2) if close != -1 else -1
            if close != -1 and end != -1:
                label = markdown[i + 1:close]
                url = markdown[close + 2:end]
                inner = parse_markdown(label)
                entities.append(RawEntity(TLEntityType.TEXT_LINK, offset,
                                          _utf16_len(inner.text), url=url))
                for entity in inner.entities:
                    entity.offset += offset
                    entities.append(entity)
                text.append(inner.text)
                offset += _utf16_len(inner.text)
                i = end + 1
                continue

        matched = None
        for delimiter, kind in _DELIMITERS:
            if markdown.startswith(delimiter, i):
                matched = (delimiter, kind)
                break

        if matched is not None:
            delimiter, kind = matched
            end = markdown.find(delimiter, i + len(delimiter))
            if end != -1:
                body = markdown[i + len(delimiter):end]
                language = None
                if kind is TLEntityType.PRE and "\n" in body:
                    first, rest = body.split("\n", 1)
                    if first.strip() and " " not in first.strip():
                        language, body = first.strip(), rest
                if kind in (TLEntityType.CODE, TLEntityType.PRE):
                    # Nothing inside code is markup - that is the point of code.
                    inner_text, inner_entities = body, ()
                else:
                    inner = parse_markdown(body)
                    inner_text, inner_entities = inner.text, inner.entities
                entities.append(RawEntity(kind, offset, _utf16_len(inner_text), language=language))
                for entity in inner_entities:
                    entity.offset += offset
                    entities.append(entity)
                text.append(inner_text)
                offset += _utf16_len(inner_text)
                i = end + len(delimiter)
                continue

        text.append(char)
        offset += _utf16_len(char)
        i += 1

    return ParsedMessage("".join(text), tuple(entities))


def parse_text(text, parse_mode="markdown", is_caption=False):
    """The shape ``client_utils`` expects: a dict with ``text`` and ``entities``.

    An unknown mode returns the text untouched rather than guessing. A plugin that asked for HTML
    and silently got markdown would produce messages full of visible tags.
    """
    mode = (parse_mode or "").lower()
    if mode in ("md", "markdown", "markdownv2"):
        parsed = parse_markdown(text)
        return {"text": parsed.text, "entities": list(parsed.entities)}
    return {"text": text, "entities": []}
