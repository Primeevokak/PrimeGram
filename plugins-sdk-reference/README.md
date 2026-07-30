# exteraGram plugin SDK — reference stubs

Type stubs for the Python API that exteraGram plugins are written against. PrimeGram implements
the same API so that plugins published for exteraGram run here unchanged, and these files are the
contract that implementation is checked against.

**Nothing here is built or shipped.** The directory sits outside `TMessagesProj/` precisely so it
cannot end up in an APK. It is documentation that happens to be machine-readable.

## Where these came from

- Source: <https://github.com/exteraSquad/plugins-pysdk-builds/releases>, asset `stubs.zip`
  (which contains `pyi-files.zip`).
- Version: SDK **1.4.5.0-beta**, release `sdk-v1.4.5.0-beta-30151697627-1`, published 2026-07-25.
- Licence: exteraGram is GPL-2.0, as is this project, being a fork of Telegram for Android.

## Why not the documentation

<https://plugins.exteragram.app/docs> lags behind the code, and the difference is not cosmetic. The
site documents a metadata field `__app_version__`; the shipped SDK has no such thing and reads
`min_version`. Both plugins in the reference repository declare `__min_version__`, so an
implementation written from the documentation would have rejected them as incompatible.

The stubs also carry a good deal the docs never mention — file and intent hooks, `AccountClient`,
`hook_all_methods`, the `HookFilter` factories, the per-queue helpers. Writing the SDK from prose
would have produced something plugins fail to import halfway through.

## Keeping them current

Match the version to the SDK inside the exteraGram build being targeted; the APK carries it as
`assets/plugins_pysdk/v.txt`. When these are refreshed, the Python modules under
`TMessagesProj/src/main/python/` are what has to move to match — not the other way around.
