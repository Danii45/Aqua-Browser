#!/usr/bin/env python3
"""Builds the website (GitHub Pages serves docs/) from site/index.html, putting the icon in as a data URI.
    python3 build/site.py            -> docs/index.html
    python3 build/site.py --fragment OUT.html   -> the same page without the <html>/<head>/<body> wrapper
"""
import base64, re, sys
from pathlib import Path
ROOT = Path(__file__).resolve().parent.parent
icon = "data:image/svg+xml;base64," + base64.b64encode((ROOT / "branding" / "icon.svg").read_bytes()).decode()
page = (ROOT / "site" / "index.html").read_text().replace("__ICON__", icon)
if len(sys.argv) == 3 and sys.argv[1] == "--fragment":
    frag = re.sub(r'<!doctype html>\s*<html[^>]*>\s*<head>\s*<meta charset="utf-8">\s*<meta name="viewport"[^>]*>\s*', "", page, flags=re.I)
    frag = re.sub(r"</head>\s*<body>\s*", "\n", frag).replace("</body>\n</html>", "")
    Path(sys.argv[2]).write_text(frag)
else:
    (ROOT / "docs").mkdir(exist_ok=True)
    (ROOT / "docs" / "index.html").write_text(page)
    (ROOT / "docs" / ".nojekyll").write_text("")
    (ROOT / "docs" / "icon.svg").write_bytes((ROOT / "branding" / "icon.svg").read_bytes())
    print("built docs/index.html")
