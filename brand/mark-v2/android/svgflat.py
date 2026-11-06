"""Flatten a (subset of) SVG into filled outlines grouped by paint, for VectorDrawable output.

Supports: g, use, path, line, circle, ellipse, rect; transform (matrix/translate/scale);
fill / stroke (converted to outlines with skia-pathops), clip-path, mask (white adds, black
subtracts), linear gradients (userSpaceOnUse). Elements with a filter are skipped (VectorDrawable
has no filters; the brand SVGs always draw an unfiltered copy of the same art).
"""
import math
import re
import xml.etree.ElementTree as ET

import pathops
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.svgLib.path import parse_path

SVG = '{http://www.w3.org/2000/svg}'
XLINK = '{http://www.w3.org/1999/xlink}href'
INHERIT = ('fill', 'stroke', 'stroke-width', 'stroke-linecap', 'stroke-linejoin', 'fill-rule', 'stroke-miterlimit')
CAPS = {'butt': pathops.LineCap.BUTT_CAP, 'round': pathops.LineCap.ROUND_CAP, 'square': pathops.LineCap.SQUARE_CAP}
JOINS = {'miter': pathops.LineJoin.MITER_JOIN, 'round': pathops.LineJoin.ROUND_JOIN, 'bevel': pathops.LineJoin.BEVEL_JOIN}


def tag(e):
    return e.tag.replace(SVG, '')


def mat_mul(a, b):
    a0, a1, a2, a3, a4, a5 = a
    b0, b1, b2, b3, b4, b5 = b
    return (a0 * b0 + a2 * b1, a1 * b0 + a3 * b1, a0 * b2 + a2 * b3, a1 * b2 + a3 * b3,
            a0 * b4 + a2 * b5 + a4, a1 * b4 + a3 * b5 + a5)


IDENT = (1, 0, 0, 1, 0, 0)


def parse_transform(s):
    m = IDENT
    if not s:
        return m
    for name, args in re.findall(r'(\w+)\s*\(([^)]*)\)', s):
        v = [float(x) for x in re.split(r'[\s,]+', args.strip()) if x]
        if name == 'translate':
            t = (1, 0, 0, 1, v[0], v[1] if len(v) > 1 else 0)
        elif name == 'scale':
            t = (v[0], 0, 0, v[1] if len(v) > 1 else v[0], 0, 0)
        elif name == 'matrix':
            t = tuple(v)
        elif name == 'rotate':
            r = math.radians(v[0])
            t = (math.cos(r), math.sin(r), -math.sin(r), math.cos(r), 0, 0)
            if len(v) == 3:
                t = mat_mul(mat_mul((1, 0, 0, 1, v[1], v[2]), t), (1, 0, 0, 1, -v[1], -v[2]))
        else:
            raise ValueError(name)
        m = mat_mul(m, t)
    return m


def raw_path(e):
    t = tag(e)
    p = pathops.Path()
    pen = p.getPen()
    if t == 'path':
        parse_path(e.get('d'), pen)
    elif t == 'line':
        x1, y1, x2, y2 = (float(e.get(k, 0)) for k in ('x1', 'y1', 'x2', 'y2'))
        p.moveTo(x1, y1)
        p.lineTo(x2, y2)
    elif t in ('circle', 'ellipse'):
        cx, cy = float(e.get('cx', 0)), float(e.get('cy', 0))
        rx = float(e.get('r') or e.get('rx'))
        ry = float(e.get('r') or e.get('ry'))
        parse_path(f'M{cx - rx},{cy} A{rx},{ry} 0 1,0 {cx + rx},{cy} A{rx},{ry} 0 1,0 {cx - rx},{cy} Z', pen)
    elif t == 'rect':
        x, y, w, h = (float(e.get(k, 0)) for k in ('x', 'y', 'width', 'height'))
        p.moveTo(x, y)
        p.lineTo(x + w, y)
        p.lineTo(x + w, y + h)
        p.lineTo(x, y + h)
        p.close()
    else:
        return None
    return p


def union(paths):
    out = pathops.Path()
    paths = [p for p in paths if p is not None]
    if not paths:
        return out
    return pathops.op(paths[0], pathops.Path(), pathops.PathOp.UNION) if len(paths) == 1 else _union_many(paths)


def _union_many(paths):
    acc = pathops.Path()
    for p in paths:
        acc = pathops.op(acc, p, pathops.PathOp.UNION)
    return acc


def op(a, b, kind):
    return pathops.op(a, b, kind)


class Doc:
    def __init__(self, path):
        self.root = ET.parse(path).getroot()
        self.ids = {e.get('id'): e for e in self.root.iter() if e.get('id')}
        vb = [float(x) for x in self.root.get('viewBox').split()]
        self.viewbox = vb

    # ---- geometry of an element subtree, ignoring paint (used for clipPath and mask) ----
    def shape(self, e, ctm, style, want=None):
        """Returns list of (pathops.Path in root user space, paint, opacity-ignored)."""
        out = []
        self._walk(e, ctm, style, None, out)
        return out

    def _style(self, e, style):
        s = dict(style)
        for k in INHERIT:
            if e.get(k) is not None:
                s[k] = e.get(k)
        st = e.get('style')
        if st:
            for kv in st.split(';'):
                if ':' in kv:
                    k, v = kv.split(':', 1)
                    if k.strip() in INHERIT:
                        s[k.strip()] = v.strip()
        return s

    def clip_geom(self, ref, ctm):
        cid = re.match(r'url\(#([^)]+)\)', ref).group(1)
        cp = self.ids[cid]
        parts = []
        for c in cp:
            items = []
            self._walk(c, ctm, {'fill': '#000', 'stroke': 'none'}, None, items)
            parts += [g for g, _ in items]
        return union(parts)

    def mask_geom(self, ref, ctm):
        mid = re.match(r'url\(#([^)]+)\)', ref).group(1)
        m = self.ids[mid]
        acc = pathops.Path()
        for c in m:
            items = []
            self._walk(c, ctm, {'fill': '#000', 'stroke': 'none'}, None, items)
            for g, paint in items:
                white = paint.lower() in ('#fff', '#ffffff', 'white')
                acc = op(acc, g, pathops.PathOp.UNION if white else pathops.PathOp.DIFFERENCE)
        return acc

    def _walk(self, e, ctm, style, clip, out):
        t = tag(e)
        if t in ('defs', 'title', 'clipPath', 'mask', 'filter', 'linearGradient', 'radialGradient', 'symbol', 'animate'):
            return
        if e.get('filter'):
            return
        ctm = mat_mul(ctm, parse_transform(e.get('transform')))
        style = self._style(e, style)
        if e.get('clip-path'):
            g = self.clip_geom(e.get('clip-path'), ctm)
            clip = g if clip is None else op(clip, g, pathops.PathOp.INTERSECTION)
        if e.get('mask'):
            g = self.mask_geom(e.get('mask'), ctm)
            clip = g if clip is None else op(clip, g, pathops.PathOp.INTERSECTION)
        if t in ('g', 'svg'):
            for c in e:
                self._walk(c, ctm, style, clip, out)
            return
        if t == 'use':
            ref = (e.get('href') or e.get(XLINK))[1:]
            self._walk_ref(self.ids[ref], ctm, style, clip, out)
            return
        p = raw_path(e)
        if p is None:
            return
        fill = style.get('fill', '#000')
        stroke = style.get('stroke', 'none')
        sw = float(style.get('stroke-width', 1))
        if fill != 'none' and t != 'line':
            f = pathops.Path(p)
            if style.get('fill-rule') == 'evenodd':
                f.fillType = pathops.FillType.EVEN_ODD
            f.simplify()
            self._emit(f, ctm, clip, fill, out)
        if stroke != 'none' and sw > 0:
            s = pathops.Path(p)
            s.stroke(sw, CAPS[style.get('stroke-linecap', 'butt')], JOINS[style.get('stroke-linejoin', 'miter')],
                     float(style.get('stroke-miterlimit', 4)))
            s.convertConicsToQuads()
            s.simplify()
            self._emit(s, ctm, clip, stroke, out)

    def _walk_ref(self, ref, ctm, style, clip, out):
        # referenced <g>/<symbol>: its own attributes win, the <use> attributes are inherited
        t = tag(ref)
        if t in ('g', 'symbol'):
            ctm2 = mat_mul(ctm, parse_transform(ref.get('transform')))
            st = self._style(ref, style)
            if ref.get('clip-path'):
                g = self.clip_geom(ref.get('clip-path'), ctm2)
                clip = g if clip is None else op(clip, g, pathops.PathOp.INTERSECTION)
            for c in ref:
                self._walk(c, ctm2, st, clip, out)
        else:
            self._walk(ref, ctm, style, clip, out)

    def _emit(self, p, ctm, clip, paint, out):
        p = p.transform(*ctm)
        if clip is not None:
            p = op(p, clip, pathops.PathOp.INTERSECTION)
        out.append((p, paint))

    def collect(self, e, style=None):
        out = []
        self._walk(e, IDENT, style or {'fill': '#000', 'stroke': 'none'}, None, out)
        return out

    def gradient(self, ref):
        gid = re.match(r'url\(#([^)]+)\)', ref).group(1)
        g = self.ids[gid]
        stops = []
        for s in g:
            if tag(s) != 'stop':
                continue
            off = float(s.get('offset').rstrip('%')) / (100 if s.get('offset').endswith('%') else 1)
            col = s.get('stop-color')
            a = float(s.get('stop-opacity', 1))
            stops.append((off, col, a))
        if tag(g) == 'linearGradient':
            return dict(type='linear', x1=float(g.get('x1')), y1=float(g.get('y1')), x2=float(g.get('x2')),
                        y2=float(g.get('y2')), stops=stops)
        return dict(type='radial', cx=float(g.get('cx')), cy=float(g.get('cy')), r=float(g.get('r')), stops=stops)


def to_d(p, nd=1):
    pen = SVGPathPen(None, ntos=lambda v: (('%.' + str(nd) + 'f') % v).rstrip('0').rstrip('.') if nd else str(round(v)))
    p.draw(pen)
    return pen.getCommands()


def translate(p, dx, dy):
    return p.transform(1, 0, 0, 1, dx, dy)


def scale(p, s, dx=0, dy=0):
    return p.transform(s, 0, 0, s, dx, dy)


def split(p, max_chars=24000, nd=1):
    """Split a simplified path into several paths (holes stay with their outer contour) so that
    each pathData stays well under aapt2's 32 KB limit for one UTF-8 string."""
    if len(to_d(p, nd)) <= max_chars:
        return [p]
    cs = list(p.contours)
    big = max(cs, key=lambda c: abs(c.area))  # the largest contour defines the "outer" orientation
    outers = [c for c in cs if c.clockwise == big.clockwise]
    holes = [c for c in cs if c.clockwise != big.clockwise]
    groups = {id(o): [o] for o in outers}

    def inside(h, o):
        hb, ob = h.bounds, o.bounds
        return ob[0] <= hb[0] and ob[1] <= hb[1] and ob[2] >= hb[2] and ob[3] >= hb[3]

    for h in holes:
        cand = [o for o in outers if inside(h, o)]
        o = min(cand, key=lambda c: abs(c.area)) if cand else big
        groups[id(o)].append(h)
    chunks, cur, size = [], pathops.Path(), 0
    for o in outers:
        g = pathops.Path()
        for c in groups[id(o)]:
            g.addPath(c)
        n = len(to_d(g, nd))
        if size and size + n > max_chars:
            chunks.append(cur)
            cur, size = pathops.Path(), 0
        cur.addPath(g)
        size += n
    if size:
        chunks.append(cur)
    return chunks
