"""Pieces the frontal mark does not have, drawn in its manner, and the piece set of a view.

The mark's line is a filled band: a heavy outer contour (≈ 1 rig unit at the rig's scale) and
inner lines of muscle and cloth that swell in the middle and taper to points. The same here:
`outline` lays a band along a smooth closed or open contour, `taper` a pointed inner line. Bones
point «up» in their own space, +x is the front for the torso and the head.
"""
from __future__ import annotations

import math
from functools import lru_cache

from . import cutout
from .cutout import Piece, R_ANKLE
from .rig import L

W = 1.0          # outer line
WI = 0.55        # inner line at its widest


def _cr(points, closed=True):
    n = len(points)
    v, i, o = [], [], []
    for k in range(n):
        p = points[k]
        if closed:
            a, b = points[(k - 1) % n], points[(k + 1) % n]
        else:
            a, b = points[max(k - 1, 0)], points[min(k + 1, n - 1)]
        tx, ty = (b[0] - a[0]) / 2, (b[1] - a[1]) / 2
        v.append([p[0], p[1]])
        o.append([tx / 3, ty / 3])
        i.append([-tx / 3, -ty / 3])
    return {"c": closed, "v": v, "i": i, "o": o}


def _normals(pts, closed):
    n = len(pts)
    out = []
    for k in range(n):
        if closed:
            a, b = pts[(k - 1) % n], pts[(k + 1) % n]
        else:
            a, b = pts[max(k - 1, 0)], pts[min(k + 1, n - 1)]
        tx, ty = b[0] - a[0], b[1] - a[1]
        ln = math.hypot(tx, ty) or 1.0
        out.append((ty / ln, -tx / ln))
    return out


def _off(pts, ws, closed, sgn):
    ns = _normals(pts, closed)
    return [(p[0] + sgn * nx * w / 2, p[1] + sgn * ny * w / 2) for p, (nx, ny), w in zip(pts, ns, ws)]


def _widths(w, n):
    return list(w) if isinstance(w, (list, tuple)) else [w] * n


def outline(pts, w=W):
    """A band along a closed contour → [outer, inner reversed] (non-zero fill)."""
    ws = _widths(w, len(pts))
    a = _cr(_off(pts, ws, True, 1), True)
    b = _cr(list(reversed(_off(pts, ws, True, -1))), True)
    return [a, b]


def stroke_open(pts, w=W, taper_ends=(False, False)):
    """A band along an open contour, as one closed path; ends blunt (hidden under a neighbour)
    unless tapered."""
    ws = _widths(w, len(pts))
    if taper_ends[0]:
        ws[0] = 0.05
    if taper_ends[1]:
        ws[-1] = 0.05
    left = _off(pts, ws, False, 1)
    right = _off(pts, ws, False, -1)
    return _cr(left + list(reversed(right)), True)


def taper(pts, w=WI):
    """An inner line: widest in the middle, pointed at both ends."""
    n = len(pts)
    ws = [max(0.06, w * math.sin(math.pi * (k / (n - 1))) ** 0.6) for k in range(n)]
    ws[0] = ws[-1] = 0.05
    left = _off(pts, ws, False, 1)
    right = _off(pts, ws, False, -1)
    return _cr(left + list(reversed(right)), True)


def closed(pts):
    return _cr(pts, True)


# --- side view: torso, head, foot, hand, cloth ----------------------------------------------------------

def chest_side():
    # back (bottom) → top → front (bottom); the bottom is open, it lies under the pelvis
    c = [(-4.5, 1.8), (-4.4, -1.0), (-4.9, -4.0), (-5.7, -7.0), (-6.0, -9.6), (-5.5, -11.7), (-4.0, -13.1), (-1.9, -13.8),
         (0.4, -14.0), (2.5, -13.5), (4.9, -12.7), (6.1, -11.4), (6.45, -9.9), (6.35, -8.6), (5.5, -8.0), (5.0, -6.6),
         (4.75, -4.2), (4.5, -1.2), (4.45, 1.8)]
    line = [stroke_open(c, W),
            taper([(6.2, -8.4), (4.8, -7.7), (3.2, -7.7), (1.8, -8.4)], 0.6),                  # under the pec
            taper([(4.1, -12.0), (2.9, -11.7), (1.6, -11.9)], 0.4),                            # clavicle
            taper([(2.3, -5.4), (2.9, -5.1), (3.5, -4.8)], 0.38),                              # serratus
            taper([(2.5, -4.2), (3.1, -3.9), (3.7, -3.6)], 0.38),
            taper([(2.7, -3.0), (3.3, -2.75), (3.85, -2.5)], 0.34),
            taper([(-3.6, -9.6), (-3.2, -7.4), (-2.9, -5.2)], 0.4)]                            # lat
    return Piece(line, [closed(c + [(0.0, 2.6)])], ring=4.4)


def pelvis_side():
    # front (top) → bottom → back (top); open at the top, it lies over the chest's bottom
    c = [(4.5, -14.4), (4.5, -11.0), (4.35, -8.0), (4.65, -5.0), (4.45, -2.2), (3.6, 0.5), (2.0, 2.3), (-0.5, 3.4),
         (-3.4, 3.1), (-5.35, 1.0), (-5.8, -1.8), (-5.05, -5.0), (-4.2, -8.0), (-4.3, -11.0), (-4.45, -14.4)]
    line = [stroke_open(c, W),
            taper([(3.3, -13.6), (3.6, -11.6), (3.7, -9.6)], 0.42),                           # the abs' edge
            taper([(-2.4, -4.5), (-3.4, -2.6), (-3.6, -0.4)], 0.42)]                          # glute
    knock = [closed(c + [(0.0, -14.9)])]
    # the glass also fills a disc at the waist, so a bent spine keeps the outline closed
    return Piece(line, knock + [closed([(4.4 * math.cos(a), -13.0 + 4.4 * math.sin(a)) for a in [k * math.pi / 6 for k in range(12)]])])


HS = 0.88       # the mark's head is small for its body (a heroic canon of 8 heads)


def _hs(pts):
    return [(x * HS, y * HS) for x, y in pts]


def head_side():
    # throat → chin → face → brow → hair (waved, swept back) → nape → back of the neck
    c = _hs([(2.7, 1.2), (2.3, -1.0), (2.2, -2.6), (2.2, -4.1), (3.6, -4.4), (4.6, -5.4), (4.55, -6.25), (4.95, -6.85),
             (4.75, -7.55), (5.55, -8.0), (5.0, -9.1), (4.45, -10.3), (4.55, -11.2), (4.1, -12.4), (4.6, -13.4), (3.9, -14.7),
             (2.4, -15.5), (0.6, -15.6), (-1.0, -15.9), (-2.8, -15.4), (-4.3, -14.3), (-5.3, -12.6), (-5.6, -10.6), (-5.0, -8.6),
             (-4.0, -7.2), (-3.0, -5.6), (-2.8, -2.4), (-2.9, 1.2)])
    ws = [W, W, 0.9, 0.85, 0.8, 0.75, 0.7, 0.7, 0.7, 0.7, 0.75, 0.8, 0.85, 0.9, W, W, W, W, W, W, W, W, W, W, W, W, W, W]
    line = [stroke_open(c, ws),
            taper(_hs([(4.0, -13.6), (2.4, -14.5), (0.2, -14.6), (-2.2, -14.0), (-3.8, -12.6)]), 0.5),   # strands, swept back
            taper(_hs([(3.5, -12.5), (1.8, -13.2), (-0.4, -13.1), (-2.6, -12.0), (-4.0, -10.4)]), 0.48),
            taper(_hs([(2.6, -11.6), (0.8, -11.9), (-1.2, -11.3), (-2.8, -9.9)]), 0.42),
            taper(_hs([(-3.6, -13.8), (-4.6, -12.0), (-4.7, -10.0)]), 0.4),
            taper(_hs([(-1.1, -10.0), (-0.1, -9.6), (0.2, -8.5), (-0.4, -7.5), (-1.0, -7.4)]), 0.42),       # ear
            taper(_hs([(3.0, -10.3), (3.8, -10.55), (4.5, -10.35)]), 0.38),                               # brow
            taper(_hs([(3.4, -9.45), (3.9, -9.55), (4.35, -9.4)]), 0.3),                                  # eye
            taper(_hs([(4.55, -6.75), (4.2, -6.7), (3.9, -6.8)]), 0.26),                                  # mouth
            taper(_hs([(-0.4, -6.9), (1.0, -5.1), (2.4, -4.3)]), 0.35),                                   # jaw
            taper(_hs([(1.4, -4.6), (1.5, -2.6), (2.0, -0.6)]), 0.38)]                                    # neck
    return Piece(line, [closed(c + [(0.0, 1.8)])])


def foot_side():
    # ankle (0,0) → toe (0,-6); sole on local +x
    c = [(-1.9, 0.9), (-0.8, 2.6), (1.0, 2.4), (1.55, 0.7), (1.5, -2.0), (1.4, -4.6), (0.9, -6.8), (-0.3, -6.9), (-1.0, -5.1),
         (-1.6, -2.6), (-2.0, -0.6)]
    line = outline(c, 0.85) + [taper([(1.1, -4.9), (0.4, -5.3), (-0.3, -5.7)], 0.32), taper([(-0.6, 0.6), (-0.2, -0.2), (0.4, 0.2)], 0.3)]
    pc = Piece(line, [closed(c)], ring=R_ANKLE)
    return pc


def hand_fist():
    # wrist (0,0) → knuckles (0,-3.7); fingers curl to local +x
    c = [(-1.35, 0.4), (-1.6, -1.3), (-1.55, -2.9), (-0.7, -3.85), (0.8, -3.8), (1.7, -2.9), (1.85, -1.3), (1.5, 0.3)]
    line = outline(c, 0.8) + [taper([(1.75, -2.2), (0.9, -2.3), (0.2, -2.0)], 0.3),
                              taper([(1.8, -1.2), (1.0, -1.25), (0.3, -1.0)], 0.3),
                              taper([(-1.2, -0.3), (-0.3, -1.4), (0.6, -2.9)], 0.38)]              # thumb
    return Piece(line, [closed(c)], ring=cutout.R_WRIST)


BELT = [(-5.45, -7.6), (-2.0, -7.9), (1.5, -7.6), (4.9, -7.0), (5.6, -5.6), (5.1, -4.8), (1.6, -5.4), (-2.0, -5.7), (-5.45, -5.5)]
SKIRT_PIVOT = (0.0, -5.8)
FLAP_KNOT = (5.0, -5.4)


def belt():
    line = outline(BELT, 0.8) + [taper([(-4.8, -6.6), (-1.0, -6.8), (3.0, -6.4), (5.0, -5.9)], 0.38)]
    return Piece(line, [closed(BELT)])


def skirt():
    # relative to the pivot under the belt; open at the top (under the belt). Like the mark's wrap:
    # short at the back, falling to a point in front, folds running down and forward.
    c = [(-5.5, -0.6), (-5.7, 2.6), (-5.6, 4.6), (-3.6, 5.6), (-0.8, 7.0), (1.9, 8.8), (4.0, 10.8), (5.3, 9.4),
         (5.5, 6.4), (5.2, 2.6), (4.9, -0.8)]
    line = [stroke_open(c, W),
            taper([(-4.6, 0.2), (-2.4, 2.6), (0.2, 5.0), (2.8, 7.8)], 0.5),
            taper([(-1.6, 0.0), (0.6, 2.2), (2.8, 4.6), (4.2, 7.6)], 0.45),
            taper([(1.6, 0.0), (3.0, 1.8), (4.2, 4.0)], 0.4)]
    return Piece(line, [closed(c + [(0.0, -1.0)])])


def flap():
    # hangs from the knot (0,0) down to a pointed end
    c = [(-0.9, 0.2), (-1.3, 2.6), (-0.9, 5.2), (-0.2, 7.6), (0.2, 9.6), (1.0, 8.0), (1.3, 5.6), (1.6, 3.0), (1.2, 0.4)]
    line = outline(c, 0.75) + [taper([(0.2, 1.0), (0.4, 4.0), (0.3, 7.4)], 0.32)]
    return Piece(line, [closed(c)])


@lru_cache(maxsize=2)
def pieces(front):
    """The piece set of a view, by bone (uaN, faN, hdN … for both sides)."""
    if front:
        # the viewer's left limbs are the mark's own (rig side F, −x); the right ones mirror them
        out = {"chest": cutout.chest_front(), "pelvis": cutout.pelvis_front(), "head": cutout.head_front()}
        for s, flip in (("F", False), ("N", True)):
            out.update({"ua" + s: cutout.upper_arm(flip), "fa" + s: cutout.forearm(flip), "th" + s: cutout.thigh(flip),
                        "sh" + s: cutout.shin(flip), "ft" + s: cutout.foot_front(flip), "hand_open" + s: cutout.hand_open(flip),
                        "hand_fist" + s: hand_fist()})
        return out
    ua, fa, th, sh = cutout.upper_arm(), cutout.forearm(), cutout.thigh(), cutout.shin()
    body = {"chest": chest_side(), "pelvis": pelvis_side(), "head": head_side(), "belt": belt(), "skirt": skirt(), "flap": flap()}
    ft = foot_side()
    out = dict(body)
    for s in "NF":
        out.update({"ua" + s: ua, "fa" + s: fa, "th" + s: th, "sh" + s: sh, "ft" + s: ft})
    out["hand_open"] = cutout.hand_open()
    out["hand_fist"] = hand_fist()
    return out
