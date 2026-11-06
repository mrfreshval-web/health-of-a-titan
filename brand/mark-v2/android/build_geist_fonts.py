"""Bundles Geist and Geist Mono (SIL OFL 1.1) into :core:ui for the «Титаниум» theme (A37 §9.3).

usage: python3 brand/mark-v2/android/build_geist_fonts.py <work dir> <android/core/ui/src/main dir>

Downloads the static TrueType instances from Google Fonts (the CSS API serves .ttf to a plain
user agent), subsets them to Latin + Cyrillic + the punctuation, arrows and symbols the UI uses
(keeps `tnum`, `lnum`, `case`, `frac`), writes res/font/*.ttf and the licence to
assets/licenses/OFL-Geist.txt. Weights follow the A37 type scale: 200 display/headline,
300 title, 400 subtitle/body, 500 label; Geist Mono 500 for telemetry.
"""
import os
import re
import subprocess
import sys

from fontTools import subset
from fontTools.ttLib import TTFont

WORK, OUT = sys.argv[1], sys.argv[2]
os.makedirs(WORK, exist_ok=True)
CSS = ("https://fonts.googleapis.com/css2?family=Geist:wght@200;300;400;500"
       "&family=Geist+Mono:wght@500")
OFL = "https://raw.githubusercontent.com/google/fonts/main/ofl/geist/OFL.txt"
NAMES = {
    ('Geist', '200'): 'geist_extralight', ('Geist', '300'): 'geist_light',
    ('Geist', '400'): 'geist_regular', ('Geist', '500'): 'geist_medium',
    ('Geist Mono', '500'): 'geist_mono_medium',
}
UNI = (list(range(0x20, 0x7F)) + list(range(0xA0, 0x180)) + list(range(0x400, 0x460)) + [0x490, 0x491]
       + list(range(0x2000, 0x2070)) + list(range(0x2190, 0x2200))
       + [0x20AC, 0x20BD, 0x2116, 0x2122, 0x2212, 0x2248, 0x2260, 0x2264, 0x2265, 0x2022, 0x25CF, 0x2713])

css = subprocess.run(['curl', '-sS', CSS], check=True, capture_output=True, text=True).stdout
blocks = re.findall(r"font-family: '([^']+)';\s*font-style: normal;\s*font-weight: (\d+);\s*src: url\(([^)]+)\)", css)
os.makedirs(os.path.join(OUT, 'res/font'), exist_ok=True)
total = 0
for fam, w, url in blocks:
    name = NAMES[(fam, w)]
    raw = os.path.join(WORK, f'raw_{name}.ttf')
    subprocess.run(['curl', '-sS', '-o', raw, url], check=True)
    font = TTFont(raw)
    opts = subset.Options()
    opts.layout_features = subset.Options().layout_features + ['tnum', 'lnum', 'pnum', 'case', 'frac']
    opts.name_IDs = ['*']
    opts.name_languages = ['*']
    opts.notdef_outline = True
    opts.hinting = False
    s = subset.Subsetter(opts)
    s.populate(unicodes=UNI)
    s.subset(font)
    dest = os.path.join(OUT, 'res/font', name + '.ttf')
    font.save(dest)
    total += os.path.getsize(dest)
    print(name, os.path.getsize(dest), 'Ж' in map(chr, font.getBestCmap()))
lic = os.path.join(OUT, 'assets/licenses/OFL-Geist.txt')
os.makedirs(os.path.dirname(lic), exist_ok=True)
subprocess.run(['curl', '-sS', '-o', lic, OFL], check=True)
print('total', total)
