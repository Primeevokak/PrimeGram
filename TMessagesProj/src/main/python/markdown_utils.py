"""Markdown in, plain text plus entities out.

Telegram does not send styled text; it sends flat text with a list of ranges. So a plugin that
writes ``**hello**`` needs someone to turn that into ``hello`` and one bold entity covering it, and
that is all this module does.

The one subtlety worth stating: offsets are counted in **UTF-16 code units**, not characters,
because that is what the protocol and the Java client both use. Every emoji outside the basic plane
counts as two. Getting this wrong is invisible in testing with Latin text and mangles every message
containing an emoji before the formatting, which is most of them.
"""

import html as _html
import re as _re
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


#: Telegram's own MarkdownV2, not GitHub's - single ``*`` is bold here, not double, and ``_``/``__``
#: split italic from underline instead of one meaning the other doubled. Longest prefix first,
#: because the match loop below takes the first one that fits at each position: ``__`` has to be
#: tried before ``_`` finds it first and reads two empty italics, and ``` ``` ``` before a lone
#: backtick for the same reason.
_DELIMITERS = (
    ("```", TLEntityType.PRE),
    ("__", TLEntityType.UNDERLINE),
    ("||", TLEntityType.SPOILER),
    ("*", TLEntityType.BOLD),
    ("_", TLEntityType.ITALIC),
    ("~", TLEntityType.STRIKETHROUGH),
    ("`", TLEntityType.CODE),
)

_EMOJI_URL_RE = _re.compile(r"tg://emoji\?id=(\d+)")


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

        if char == "!" and i + 1 < length and markdown[i + 1] == "[":
            close = markdown.find("](", i + 1)
            end = markdown.find(")", close + 2) if close != -1 else -1
            if close != -1 and end != -1:
                match = _EMOJI_URL_RE.match(markdown[close + 2:end])
                if match:
                    label = markdown[i + 2:close]
                    entities.append(RawEntity(TLEntityType.CUSTOM_EMOJI, offset,
                                              _utf16_len(label), document_id=int(match.group(1))))
                    text.append(label)
                    offset += _utf16_len(label)
                    i = end + 1
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


#: Telegram's own HTML tags (bot API conventions - the same ones exteraGram's parser reads),
#: mapped to what each one means.
_HTML_TAGS = {
    "b": TLEntityType.BOLD, "strong": TLEntityType.BOLD,
    "i": TLEntityType.ITALIC, "em": TLEntityType.ITALIC,
    "u": TLEntityType.UNDERLINE, "ins": TLEntityType.UNDERLINE,
    "s": TLEntityType.STRIKETHROUGH, "strike": TLEntityType.STRIKETHROUGH, "del": TLEntityType.STRIKETHROUGH,
    "code": TLEntityType.CODE,
    "pre": TLEntityType.PRE,
    "a": TLEntityType.TEXT_LINK,
    "span": TLEntityType.SPOILER,       # only when class="tg-spoiler"
    "tg-spoiler": TLEntityType.SPOILER,
    "spoiler": TLEntityType.SPOILER,
    "blockquote": TLEntityType.BLOCKQUOTE,
    "tg-emoji": TLEntityType.CUSTOM_EMOJI,
    "emoji": TLEntityType.CUSTOM_EMOJI,
}


class _HTMLParser:
    """A small hand-written HTML walker, not :class:`html.parser.HTMLParser`.

    The stdlib parser hands tags to a callback one at a time and expects the caller to track
    nesting; entities need to know each tag's *matching* close before they know their length,
    which is naturally recursive. Telegram's own HTML dialect is a handful of flat, non-overlapping
    tags with no attributes worth generalising for, so a small recursive-descent reader is less
    code than reimplementing a stack on top of the callback API and is easier to trust.
    """

    def __init__(self, source):
        self.source = source
        self.pos = 0
        self.length = len(source)

    def parse(self):
        text, entities = self._run(until=None)
        return ParsedMessage("".join(text), tuple(_merge_pre_code(entities)))

    def _run(self, until):
        text = []
        entities = []
        offset = 0
        while self.pos < self.length:
            char = self.source[self.pos]
            if char == "<":
                if self.source.startswith("</", self.pos):
                    end_tag = self._read_tag_name(self.pos + 2)
                    if until is not None and end_tag == until:
                        self.pos = self.source.index(">", self.pos) + 1
                        return text, entities
                    # A close tag that does not match what we are waiting for: not ours, skip it
                    # rather than losing the rest of the message to a mismatched </b>.
                    self.pos = self.source.index(">", self.pos) + 1
                    continue
                tag, attrs, self_closing = self._read_open_tag()
                if tag == "br":
                    text.append("\n")
                    offset += 1
                    continue
                kind = self._kind_for(tag, attrs)
                if self_closing or kind is None:
                    continue
                inner_text, inner_entities = self._run(until=tag)
                inner = "".join(inner_text)
                length = _utf16_len(inner)
                entity = self._make_entity(kind, tag, attrs, offset, length)
                if entity is not None:
                    entities.append(entity)
                for child in inner_entities:
                    child.offset += offset
                    entities.append(child)
                text.append(inner)
                offset += length
            else:
                next_tag = self.source.find("<", self.pos)
                chunk = self.source[self.pos:next_tag if next_tag != -1 else self.length]
                chunk = _unescape_html(chunk)
                text.append(chunk)
                offset += _utf16_len(chunk)
                self.pos = next_tag if next_tag != -1 else self.length
        return text, entities

    def _read_tag_name(self, start):
        end = start
        while end < self.length and (self.source[end].isalnum() or self.source[end] in "-_"):
            end += 1
        return self.source[start:end].lower()

    def _read_open_tag(self):
        assert self.source[self.pos] == "<"
        close = self.source.index(">", self.pos)
        raw = self.source[self.pos + 1:close]
        self_closing = raw.endswith("/")
        if self_closing:
            raw = raw[:-1]
        parts = raw.split(None, 1)
        tag = parts[0].lower() if parts else ""
        attrs = _parse_attrs(parts[1]) if len(parts) > 1 else {}
        self.pos = close + 1
        return tag, attrs, self_closing

    @staticmethod
    def _kind_for(tag, attrs):
        if tag == "span":
            return TLEntityType.SPOILER if "tg-spoiler" in (attrs.get("class") or "") else None
        return _HTML_TAGS.get(tag)

    @staticmethod
    def _make_entity(kind, tag, attrs, offset, length):
        if length == 0:
            return None
        if kind is TLEntityType.TEXT_LINK:
            return RawEntity(kind, offset, length, url=attrs.get("href") or "")
        if kind is TLEntityType.PRE:
            return RawEntity(kind, offset, length, language=attrs.get("data-language") or attrs.get("language"))
        if kind is TLEntityType.CODE:
            # Not meaningful for a plain <code> - TL_messageEntityCode carries no language field -
            # but stashed here so a <pre><code class="language-x"> pair can hand it up to the PRE
            # entity that wraps it, which is where Telegram actually puts it.
            match = _LANGUAGE_CLASS_RE.search(attrs.get("class") or "")
            return RawEntity(kind, offset, length, language=match.group(1) if match else None)
        if kind is TLEntityType.CUSTOM_EMOJI:
            raw_id = attrs.get("emoji-id") or attrs.get("id") or attrs.get("data-document-id")
            try:
                document_id = int(raw_id) if raw_id else None
            except ValueError:
                document_id = None
            return RawEntity(kind, offset, length, document_id=document_id)
        if kind is TLEntityType.BLOCKQUOTE:
            return RawEntity(kind, offset, length, collapsed="expandable" in attrs or "collapsed" in attrs)
        return RawEntity(kind, offset, length)


def _parse_attrs(raw):
    attrs = {}
    for match in _ATTR_RE.finditer(raw):
        name = match.group(1).lower()
        value = match.group(2)
        if value is None:
            value = match.group(3)
        if value is None:
            value = name  # bare attribute, e.g. <blockquote expandable>
        attrs[name] = _unescape_html(value)
    return attrs


_ATTR_RE = _re.compile(r'([\w-]+)(?:\s*=\s*(?:"([^"]*)"|\'([^\']*)\'|([^\s>]+)))?')
_LANGUAGE_CLASS_RE = _re.compile(r"language-(\S+)")


def _merge_pre_code(entities):
    """``<pre><code class="language-x">...`` is one block of code, not a block inside a block.

    Parsed generically it produces two entities covering the exact same range - a PRE and a CODE -
    because that is what the tags literally are. Telegram's protocol has no way to say "these two
    are really one", so one of them has to go: the PRE stays, wearing the language the CODE tag
    was carrying, because a bare <pre> block is legitimate on its own and a bare <code> block is
    what everything that is not this special case still needs to produce.
    """
    pre_ranges = {(e.offset, e.length): e for e in entities if e.type is TLEntityType.PRE}
    merged = []
    for entity in entities:
        key = (entity.offset, entity.length)
        if entity.type is TLEntityType.CODE and key in pre_ranges:
            if pre_ranges[key].language is None:
                pre_ranges[key].language = entity.language
            continue
        merged.append(entity)
    return merged


def _unescape_html(text):
    return _html.unescape(text)


def parse_html(text):
    """Telegram's HTML dialect: ``<b>``, ``<i>``, ``<a href>``, ``<pre><code class="language-x">``,
    ``<span class="tg-spoiler">`` and the rest of the bot API's tag set, turned into the same
    ``(text, entities)`` shape :func:`parse_markdown` produces.
    """
    return _HTMLParser(text).parse()


def parse_text(text, parse_mode="HTML", is_caption=False):
    """The documented shape: a dict with ``entities`` and, keyed by whether this text is going out
    as a caption or a message body, ``message`` or ``caption`` - the same two fields a send request
    itself carries one of, which is the point: a plugin building a request payload can drop this
    dict's non-entities key straight in under its own name.

    An unknown mode returns the text untouched rather than guessing - a plugin that asked for a
    mode we silently do not support would otherwise produce messages full of visible markup.
    """
    mode = (parse_mode or "").lower()
    if mode in ("md", "markdown", "markdownv2"):
        parsed = parse_markdown(text)
        result_text, entities = parsed.text, list(parsed.entities)
    elif mode == "html":
        parsed = parse_html(text)
        result_text, entities = parsed.text, list(parsed.entities)
    else:
        result_text, entities = text, []
    return {("caption" if is_caption else "message"): result_text, "entities": entities}
