"""Text formatting, re-exported.

The SDK puts ``parse_text`` here and ``parse_markdown`` in :mod:`markdown_utils`, and plugins
import from both. One implementation lives in ``markdown_utils`` and this module is the other name
for it - two parsers with the same job would eventually round-trip text differently, which shows up
as a message the user did not write.
"""

from markdown_utils import ParsedMessage, RawEntity, TLEntityType, parse_html, parse_markdown, parse_text

__all__ = ["add_surrogates", "remove_surrogates", "parse_text", "parse_markdown",
           "TLEntityType", "RawEntity", "ParsedMessage", "Markdown", "HTML"]


def add_surrogates(text):
    """Text as UTF-16 sees it, which is how Telegram counts entity offsets."""
    return text.encode("utf-16-le", "surrogatepass").decode("utf-16-le", "surrogatepass")


def remove_surrogates(text):
    return text.encode("utf-16", "surrogatepass").decode("utf-16", "surrogatepass")


class Markdown:
    @classmethod
    def parse(cls, text, strict=False):
        parsed = parse_markdown(text)
        return {"text": parsed.text, "entities": list(parsed.entities)}

    @staticmethod
    def unparse(text, entities):
        # Going the other way needs the entity list the client is holding, and no plugin in the
        # reference set does it. Left out rather than half-done.
        raise NotImplementedError("unparse is not available in this build")


class HTML:
    """Telegram's HTML dialect - the same tags the bot API documents, which is what exteraGram's
    own ``HTML.parse`` reads too."""

    @staticmethod
    def parse(text):
        parsed = parse_html(text)
        return {"text": parsed.text, "entities": list(parsed.entities)}

    @staticmethod
    def unparse(text, entities):
        raise NotImplementedError("unparse is not available in this build")
