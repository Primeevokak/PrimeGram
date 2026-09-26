"""Build a corpus of real exteraGram plugins for check_corpus.py.

Searches GitHub for plugin repositories, shallow-clones them, and copies out every file that is a
plugin (a .plugin/.py containing both ``__id__`` and ``BasePlugin``) plus .elyx archives,
de-duplicated by content. The corpus is third-party code under assorted licenses, so it is built
locally and never committed.

    python tools/plugin_compat/fetch_corpus.py <out dir>
    python tools/plugin_compat/check_corpus.py <out dir>/plugins

Unauthenticated GitHub search allows ~10 requests a minute; set GITHUB_TOKEN to raise that.
"""

import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import urllib.request

QUERIES = ["exteragram plugin", "exteragram plugins", "extera plugin", "extera plugins", "ayugram plugin", "exteragram"]
SKIP = re.compile(r"^(DrKLO/|.*/Telegram-Android|.*/exteraGram$)", re.I)


def search(query, page):
    url = "https://api.github.com/search/repositories?q=%s&per_page=100&page=%d" % (urllib.request.quote(query), page)
    req = urllib.request.Request(url, headers={"Accept": "application/vnd.github+json"})
    if os.environ.get("GITHUB_TOKEN"):
        req.add_header("Authorization", "Bearer " + os.environ["GITHUB_TOKEN"])
    with urllib.request.urlopen(req, timeout=30) as r:
        return [item["full_name"] for item in json.load(r).get("items", [])]


def main():
    out = sys.argv[1]
    repos_dir = os.path.join(out, "repos")
    plugins_dir = os.path.join(out, "plugins")
    os.makedirs(repos_dir, exist_ok=True)
    os.makedirs(plugins_dir, exist_ok=True)

    names = set()
    for q in QUERIES:
        for page in (1, 2, 3):
            try:
                names |= set(search(q, page))
            except Exception as e:
                print("search failed:", q, page, e)
    names = sorted(n for n in names if not SKIP.match(n))
    print("repositories:", len(names))

    for name in names:
        dest = os.path.join(repos_dir, name.replace("/", "_"))
        if os.path.isdir(dest):
            continue
        subprocess.run(["git", "clone", "-q", "--depth", "1", "--filter=blob:limit=20m",
                        "https://github.com/" + name, dest],
                       env=dict(os.environ, GIT_TERMINAL_PROMPT="0"), timeout=180, check=False)

    seen = set()
    count = 0
    for dp, _, files in os.walk(repos_dir):
        if os.sep + ".git" in dp:
            continue
        for f in files:
            p = os.path.join(dp, f)
            if f.endswith(".elyx"):
                data = open(p, "rb").read()
            elif f.endswith((".plugin", ".py")) and os.path.getsize(p) < 3_000_000:
                data = open(p, "rb").read()
                if b"__id__" not in data or b"BasePlugin" not in data:
                    continue
            else:
                continue
            digest = hashlib.sha256(data).hexdigest()
            if digest in seen:
                continue
            seen.add(digest)
            m = re.search(rb'__id__\s*=\s*["\']([^"\']+)', data)
            ident = re.sub(r"[^A-Za-z0-9_.-]", "_", (m.group(1).decode("utf-8", "replace") if m else f)[:60])
            ext = ".elyx" if f.endswith(".elyx") else ".plugin"
            shutil.copy(p, os.path.join(plugins_dir, "%s__%s%s" % (ident, digest[:8], ext)))
            count += 1
    print("plugins:", count)


if __name__ == "__main__":
    main()
