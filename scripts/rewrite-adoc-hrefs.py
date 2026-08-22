#!/usr/bin/env python3
#
# Copyright 2026 i-Bridge bv
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
"""Optional local helper. Maven prepare-package uses RewriteAdocHrefs (Java), not this file.

Rewrite generated Asciidoctor HTML so published docs navigate as HTML.

AsciiDoc ``link:page.adoc[page.adoc]`` is a hard URL, so HTML keeps the .adoc
suffix on both the href and the visible label. This pass:

* href ``page.adoc`` / ``page.adoc#id`` → ``page.html`` / ``page.html#id``
* link text ``page.adoc`` → ``page`` (``architecture.adoc D1`` → ``architecture D1``)
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

HREF_ADOC = re.compile(
    r"""(?P<prefix>href\s*=\s*(?P<q>['"]))(?P<path>[^'"]+?)\.adoc(?P<frag>#[^'"]*)?(?P=q)""",
    re.IGNORECASE,
)
# Visible label of an <a>…</a> that still names a .adoc file.
LINK_TEXT_ADOC = re.compile(
    r"(?P<open><a\b[^>]*>)(?P<text>[^<]*?)\.adoc(?P<rest>[^<]*)(?P<close></a>)",
    re.IGNORECASE,
)


def rewrite(html: str) -> tuple[str, int]:
    def repl_href(match: re.Match[str]) -> str:
        frag = match.group("frag") or ""
        return f'{match.group("prefix")}{match.group("path")}.html{frag}{match.group("q")}'

    def repl_text(match: re.Match[str]) -> str:
        return f'{match.group("open")}{match.group("text")}{match.group("rest")}{match.group("close")}'

    html, n_href = HREF_ADOC.subn(repl_href, html)
    html, n_text = LINK_TEXT_ADOC.subn(repl_text, html)
    return html, n_href + n_text


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "root",
        type=Path,
        help="Directory of generated HTML (for example target/generated-docs)",
    )
    args = parser.parse_args(argv)
    root: Path = args.root
    if not root.is_dir():
        print(f"Not a directory: {root}", file=sys.stderr)
        return 1

    files = 0
    total = 0
    for path in sorted(root.rglob("*.html")):
        original = path.read_text(encoding="utf-8")
        updated, n = rewrite(original)
        if n:
            path.write_text(updated, encoding="utf-8")
            files += 1
            total += n
    print(f"Rewrote {total} .adoc href/label(s) in {files} HTML file(s) under {root}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
