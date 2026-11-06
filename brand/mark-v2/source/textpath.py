"""Outline text to SVG path data with HarfBuzz shaping (kerning) + tracking."""
import os
import uharfbuzz as hb
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.varLib import instancer

FD = '/tmp/claude-0/-home-user-health-of-a-titan/ad7eab95-b6de-5a45-addc-3e2eb7b7055e/scratchpad/fonts'
_cache = {}


def font(name, wght=None):
    key = (name, wght)
    if key in _cache:
        return _cache[key]
    path = os.path.join(FD, name)
    if wght is not None:
        out = os.path.join(FD, f'_inst_{wght}_{name}')
        if not os.path.exists(out):
            vf = TTFont(path)
            inst = instancer.instantiateVariableFont(vf, {'wght': wght})
            inst.save(out)
        path = out
    tt = TTFont(path)
    blob = hb.Blob.from_file_path(path)
    face = hb.Face(blob)
    f = hb.Font(face)
    _cache[key] = (tt, f, face.upem)
    return _cache[key]


def text_path(text, fontname, size, x=0, y=0, tracking=0.0, wght=None, anchor='start', features=None):
    """returns (path_d, width). tracking in em. y = baseline."""
    tt, f, upem = font(fontname, wght)
    buf = hb.Buffer()
    buf.add_str(text)
    buf.guess_segment_properties()
    hb.shape(f, buf, features or {'kern': True, 'liga': False})
    gs = tt.getGlyphSet()
    order = tt.getGlyphOrder()
    sc = size / upem
    pen_x = 0
    items = []
    n = len(buf.glyph_infos)
    for i, (info, pos) in enumerate(zip(buf.glyph_infos, buf.glyph_positions)):
        gname = order[info.codepoint]
        items.append((gname, pen_x + pos.x_offset, pos.y_offset))
        pen_x += pos.x_advance + (tracking * upem if i < n-1 else 0)
    width = pen_x * sc
    if anchor == 'middle':
        x -= width/2
    elif anchor == 'end':
        x -= width
    d = []
    for gname, gx, gy in items:
        sp = SVGPathPen(gs)
        tp = TransformPen(sp, (sc, 0, 0, -sc, x + gx*sc, y - gy*sc))
        gs[gname].draw(tp)
        d.append(sp.getCommands())
    return ''.join(d), width


def cap_height(fontname, size, wght=None):
    tt, f, upem = font(fontname, wght)
    os2 = tt['OS/2']
    return getattr(os2, 'sCapHeight', 0.7*upem) * size / upem
