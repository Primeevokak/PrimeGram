# Python libraries available to plugins

Two mechanisms, and the line between them is not a policy choice.

**Compiled into the APK** — every package with native code. Their `.so` is built for one ABI and
one interpreter build, and Android will not load a native library out of a directory the app can
write to, so these can only come from the APK. The list lives in the `pip` block of
`TMessagesProj/build.gradle`.

**Downloaded on demand** — every pure-Python package on PyPI. `_prime_pip.py` fetches the wheel,
unpacks it into `files/plugins/pylibs` and puts that directory on `sys.path` *after* the built-in
packages, so a downloaded copy can never shadow a compiled one. This happens when a plugin declares
`__requirements__`, and again as a one-shot retry when a plugin raises `ModuleNotFoundError` for
something it never declared - which plugins do constantly.

## Bundled

### Networking
| Package | What it does |
| --- | --- |
| `requests` | HTTP requests; the one most plugins reach for |
| `httpx` | the same, with async and HTTP/2 |
| `aiohttp` | async HTTP client and server |
| `brotli` | the compression most sites answer with |
| `netifaces` | the device's network interfaces and addresses |

### Parsing
| Package | What it does |
| --- | --- |
| `beautifulsoup4` | pulling data out of HTML; the basis of any scraper |
| `lxml` | fast XML and HTML parsing, and BeautifulSoup's engine |
| `regex` | regular expressions beyond the standard module - recursion, Unicode properties |
| `pyyaml` | reading and writing YAML |

### Cryptography
| Package | What it does |
| --- | --- |
| `cryptography` | TLS, certificates, symmetric and asymmetric encryption |
| `pycryptodome` | the classic cipher set: AES, RSA, hashes |
| `pynacl` | modern cryptography on libsodium |
| `tgcrypto` | MTProto acceleration, written for Telegram |
| `bcrypt`, `argon2-cffi` | password hashing |

### Images
| Package | What it does |
| --- | --- |
| `pillow` | open, alter, compose, save an image |
| `pyzbar` | reading QR codes and barcodes from a photo |
| `wordcloud` | a word cloud from text, as a picture |

### Audio
| Package | What it does |
| --- | --- |
| `miniaudio` | decoding and playback |
| `soxr` | resampling |
| `lameenc` | MP3 encoding |

### Numbers and charts
| Package | What it does |
| --- | --- |
| `numpy` | arrays and maths; the foundation everything else stands on |
| `pandas` | tables, grouping, time series |
| `matplotlib` | charts as a picture, which is the only way to show one in a chat |
| `pywavelets` | wavelet transforms for signal work |
| `ephem` | sunrise, sunset, moon phase, planetary positions |

### Text and structures
| Package | What it does |
| --- | --- |
| `editdistance` | Levenshtein distance, i.e. fuzzy matching |
| `bitarray` | efficient bit arrays |
| `lru-dict` | a dictionary that evicts; a cache |
| `cytoolz` | fast functional operations over collections |
| `python-dateutil` | parsing dates in whatever shape they arrive |
| `packaging` | comparing versions by PEP 440 |

### Utility
| Package | What it does |
| --- | --- |
| `zstandard`, `lz4` | fast compression |
| `psutil` | processes, memory, battery |
| `markupsafe`, `greenlet` | bundled not for themselves but so that `jinja2` and `sqlalchemy` can be downloaded |

Plus whatever arrives as a dependency of the above - `certifi`, `urllib3`, `idna`,
`charset-normalizer`, `soupsieve`, `cffi`, `multidict`, `yarl`, `frozenlist`, `attrs`, `pytz`,
`fonttools`, `contourpy`, `kiwisolver` and the rest - and the whole Python 3.11 standard library,
including `sqlite3`, `asyncio`, `hashlib`, `zipfile` and `csv`.

## Pinning

Most of the bundled packages are pinned to an exact version, and the pins are load-bearing.

Chaquopy resolves against its own Android repository first and PyPI second. Where its Android build
of a package is older than the newest release on PyPI, an unpinned request resolves to the PyPI
**source** release, and pip then tries to compile a native extension on the build machine - which
fails outright on Windows, and would produce a desktop binary anywhere else.

To check what is available for a package: <https://chaquo.com/pypi-13.1/> + the package name, and
look for a `cp311` tag in the wheel filenames.

## Not available, and why

These have Android builds that stop at Python 3.10 or 3.8, so on 3.11 they cannot be installed at
any version:

`scipy`, `opencv-python`, `opencv-python-headless`, `opencv-contrib-python-headless`,
`tflite-runtime`, `ujson`, `soundfile`, `coincurve`, `ta-lib`, `shapely`, `marisa-trie`.

Staying on 3.11 is a deliberate trade: plugins written for exteraGram target it, and running those
unchanged is the point of the whole feature. A plugin using 3.11 syntax on a 3.10 interpreter fails
to load with an error its user cannot act on.

Getting any of them back means building the wheels ourselves with Chaquopy's `build-wheel.py`,
which needs a Linux x86-64 machine, the Android SDK, and a recipe per package (`meta.yaml`, often
`build.sh` and patches). The results would go in a directory referenced by
`options "--find-links", "..."`. The cheap ones to try first are the small C extensions -
`soundfile`, `marisa-trie`, `ujson` - because they prove the pipeline without a multi-hour C++
build; `opencv` is the valuable one and already has a recipe in Chaquopy's repository.

There is also one gap no build can close: `pydantic` v2 stands on `pydantic-core`, which is written
in Rust and has no Android build in the repository. Everything depending on pydantic v2 is out of
reach. Pydantic v1 is pure Python and downloads normally.
