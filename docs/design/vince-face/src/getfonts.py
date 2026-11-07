"""Download the OFL fonts used by the concept-v3 mockups into ./fonts (not committed).

Usage: python3 docs/design/concept-v3/src/getfonts.py
Families: Geist + Geist Mono (Vercel, SIL OFL 1.1, Latin + Cyrillic) for the UI,
Cormorant SC (SIL OFL 1.1) only for the wordmark anchor.
"""
import os
import re
import subprocess

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "fonts")
UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36"
URL = ("https://fonts.googleapis.com/css2?family=Geist:wght@200;300;400;500;600"
       "&family=Geist+Mono:wght@400;500&family=Cormorant+SC:wght@500;600&display=swap")

os.makedirs(OUT, exist_ok=True)
css = subprocess.run(["curl", "-sS", "-A", UA, URL], check=True, capture_output=True, text=True).stdout
urls = sorted(set(re.findall(r"https://fonts\.gstatic\.com[^)]*", css)))
for i, u in enumerate(urls):
    name = f"f{i}.woff2"
    path = os.path.join(OUT, name)
    if not os.path.exists(path):
        subprocess.run(["curl", "-sS", "-o", path, u], check=True)
    css = css.replace(u, name)
with open(os.path.join(OUT, "local.css"), "w") as fh:
    fh.write(css)
print(f"{len(urls)} font files in {OUT}")
