"""PrimeGram: the runtime surface a packaged (.elyx) plugin's own code imports from, mirroring
the ``elyx`` module elyxbuilder's real toolchain provides. There is no source-level documentation
for elyxbuilder's own ``elyx`` module - every piece here (``strings``, ``settings``, ``metainfo``,
``assets``, ``gen2``) was reverse-engineered by disassembling a real, popular .elyx's compiled
bytecode (a third-party plugin that kept surfacing each gap one ImportError at a time) with a
matching CPython 3.11 interpreter, not copied from a spec.
"""
