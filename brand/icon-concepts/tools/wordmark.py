"""Outline text with HarfBuzz shaping (kerning, ligatures) into SVG path data.

Fonts are SIL OFL 1.1 families from Google Fonts. They are NOT committed:
download them into FONT_DIR (see brand/icon-concepts/README.md) before running.
"""
from __future__ import annotations

import os

import uharfbuzz as hb
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

FONT_DIR = os.environ.get("HOT_FONT_DIR", os.path.join(os.path.dirname(__file__), "fonts"))

_cache: dict[str, tuple] = {}


def _load(name):
    if name not in _cache:
        path = os.path.join(FONT_DIR, name)
        blob = hb.Blob.from_file_path(path)
        face = hb.Face(blob)
        font = hb.Font(face)
        tt = TTFont(path)
        _cache[name] = (font, tt, face.upem)
    return _cache[name]


def outline(font_name, text, size, x=0.0, y=0.0, tracking=0.0, features=None):
    """Return (path_d, advance_width, cap_height) for text set at `size`.

    (x, y) is the left end of the baseline. tracking is in em units (0.1 = 10 %).
    """
    font, tt, upem = _load(font_name)
    buf = hb.Buffer()
    buf.add_str(text)
    buf.guess_segment_properties()
    hb.shape(font, buf, features or {"kern": True, "liga": True})
    glyph_set = tt.getGlyphSet()
    order = tt.getGlyphOrder()
    s = size / upem
    pen = SVGPathPen(glyph_set, ntos=lambda v: ("%.2f" % v).rstrip("0").rstrip("."))
    cx = 0.0
    n = len(buf.glyph_infos)
    for i, (info, pos) in enumerate(zip(buf.glyph_infos, buf.glyph_positions)):
        gname = order[info.codepoint]
        gx = x + (cx + pos.x_offset) * s
        gy = y - pos.y_offset * s
        tpen = TransformPen(pen, (s, 0, 0, -s, gx, gy))
        glyph_set[gname].draw(tpen)
        cx += pos.x_advance
        if i < n - 1:
            cx += tracking * upem
    os2 = tt["OS/2"]
    cap = getattr(os2, "sCapHeight", 0) or int(upem * 0.7)
    return pen.getCommands(), cx * s, cap * s
