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


# --- profile limbs (v3) ---------------------------------------------------------------------------------
# The mark has its limbs only from the front; seen from the side a limb shows other muscles: the
# deltoid's cap and the biceps / triceps, the quadriceps and the kneecap in front of the thigh, the
# hamstrings behind it, the shin's straight tibia and the calf. Local space as everywhere: the bone
# points «up» (−y) from its joint; for a limb hanging down local −x is the body's front.
# A joint end is an arc of the joint's disc radius (cutout.R_*); its line is the child's ring, so the
# side lines stop at the arc.

def arc(c, r, a0, a1, n=None):
    """Points on a circle (degrees, x right, y down), a0 → a1."""
    n = n or max(3, int(abs(a1 - a0) / 22.5) + 1)
    return [(c[0] + r * math.cos(math.radians(a0 + (a1 - a0) * k / (n - 1))),
             c[1] + r * math.sin(math.radians(a0 + (a1 - a0) * k / (n - 1)))) for k in range(n)]


def limb(front, back, length, r_top, r_end, inner, ring=None, cap=None, w=W):
    """A limb piece: front and back contours run from the top joint down to the end joint (both
    given top → bottom, without the arcs). The end is the end joint's disc; the top is the top
    joint's disc (a child under its parent: no line) or `cap`, a drawn contour over the top
    (from the front's first point to the back's first point)."""
    end = arc((0.0, -length), r_end, 180.0, 360.0)[1:-1]                 # front → (tip) → back
    if cap is None:
        top = arc((0.0, 0.0), r_top, 0.0, 180.0)[1:-1]                    # back → (over) → front
        sil = list(front) + end + list(reversed(back)) + top
        line = [stroke_open(front, w), stroke_open(back, w)]
    else:
        over = list(reversed(cap))[1:-1]                                  # back → over → front
        sil = list(front) + end + list(reversed(back)) + over
        line = [stroke_open(list(reversed(back)) + over + list(front), w)]
    return Piece(line + inner, [closed(sil)], ring=ring)


def thigh_side():
    L_ = L["th"]
    rk = cutout.R_KNEE
    front = [(-4.9, 0.0), (-5.35, -3.6), (-5.55, -7.6), (-5.15, -11.6), (-4.35, -15.0), (-3.55, -17.4), (-3.75, -18.9),
             (-3.5, -20.2), (-rk, -L_)]
    back = [(4.9, 0.0), (4.95, -2.8), (4.7, -6.6), (4.25, -10.8), (3.55, -14.6), (3.05, -17.6), (rk, -L_)]
    inner = [taper([(-3.6, -3.0), (-2.9, -7.6), (-2.7, -12.2), (-3.0, -15.4)], 0.5),                     # rectus | vastus
             taper([(-4.2, -13.2), (-3.3, -15.6), (-2.2, -17.0), (-1.2, -17.4)], 0.42),                  # vastus medialis drop
             taper([(-3.6, -17.6), (-2.7, -18.5), (-2.4, -19.6), (-2.9, -20.5)], 0.4),                   # kneecap
             taper([(2.2, -4.0), (1.9, -8.6), (1.6, -13.0), (1.9, -16.6)], 0.42),                        # hamstrings | vastus
             taper([(3.4, -15.4), (2.8, -17.6), (2.5, -19.4)], 0.32)]                                     # tendon
    return limb(front, back, L_, 4.9, rk, inner)


def shin_side():
    L_ = L["sh"]
    rk, ra = cutout.R_KNEE, R_ANKLE
    front = [(-rk, 0.0), (-2.75, -2.6), (-2.45, -6.6), (-2.15, -11.0), (-1.85, -15.2), (-1.6, -18.6), (-ra, -L_)]
    back = [(rk, 0.0), (3.7, -2.4), (4.45, -5.2), (4.3, -8.0), (3.45, -11.0), (2.45, -13.8), (1.75, -16.6), (1.55, -18.8),
            (ra, -L_)]
    inner = [taper([(2.6, -2.8), (3.1, -5.6), (2.8, -8.6), (2.0, -11.4)], 0.45),                       # the calf's two heads
             taper([(-1.3, -3.2), (-1.1, -7.0), (-0.9, -11.0), (-0.8, -14.4)], 0.38),                   # tibialis
             taper([(1.2, -14.6), (0.9, -17.0), (0.8, -19.0)], 0.3)]                                     # Achilles
    return limb(front, back, L_, rk, ra, inner, ring=rk)


def upper_arm_side():
    L_ = L["ua"]
    re = cutout.R_ELBOW
    # the deltoid's cap over the shoulder joint, then the biceps in front, the triceps behind
    cap = [(-3.7, -2.6), (-3.95, -0.4), (-3.3, 1.7), (-1.5, 2.85), (0.8, 2.9), (2.8, 2.05), (3.85, 0.3), (3.95, -1.9)]
    front = [(-3.7, -2.6), (-3.55, -4.6), (-3.2, -6.4), (-3.5, -8.4), (-3.4, -10.6), (-2.95, -12.4), (-re, -L_)]
    back = [(3.95, -1.9), (3.8, -4.6), (3.65, -7.4), (3.3, -10.2), (2.8, -12.3), (re, -L_)]
    inner = [taper([(-3.2, -5.3), (-1.7, -4.5), (0.2, -4.7), (1.9, -5.6), (3.2, -6.8)], 0.48),           # the deltoid's edge
             taper([(-0.7, 2.0), (-0.4, -0.2), (-0.6, -2.8)], 0.32),                                       # front | rear deltoid
             taper([(-1.2, -7.4), (-0.6, -9.6), (-0.6, -11.8)], 0.38),                                    # biceps | triceps
             taper([(2.4, -7.4), (1.9, -9.6), (1.5, -11.4)], 0.32)]                                       # triceps' heads
    return limb(front, back, L_, None, re, inner, cap=cap)


def forearm_side():
    L_ = cutout.FOREARM
    re, rw = cutout.R_ELBOW, cutout.R_WRIST
    front = [(-re, 0.0), (-3.0, -2.2), (-2.85, -4.4), (-2.25, -7.0), (-1.7, -9.2), (-rw, -L_)]
    back = [(re, 0.0), (2.75, -2.0), (2.55, -4.4), (2.0, -7.0), (1.65, -9.2), (rw, -L_)]
    inner = [taper([(-1.7, -1.4), (-1.0, -4.0), (-0.5, -6.6), (-0.4, -8.6)], 0.4),
             taper([(1.4, -2.2), (1.1, -4.6), (0.9, -6.8)], 0.3)]
    return limb(front, back, L_, re, rw, inner, ring=re)


# --- side view: torso, head, foot, hand, cloth ----------------------------------------------------------

def chest_side():
    # back (bottom) → traps → (under the neck) → clavicle → pec → front (bottom); the bottom is open,
    # it lies under the pelvis. v3: the mark's mass — a deep ribcage, a full pec, a broad upper back.
    c = [(-4.5, 1.8), (-4.3, -1.0), (-4.9, -4.0), (-6.0, -7.0), (-6.75, -9.8), (-6.55, -12.0), (-5.4, -13.6), (-3.7, -14.6),
         (-1.9, -15.2), (0.4, -15.0), (2.4, -14.6), (4.6, -13.8), (6.5, -12.6), (7.5, -10.8), (7.75, -9.0), (7.25, -7.5),
         (6.1, -6.8), (5.65, -5.6), (5.35, -3.0), (5.05, -0.6), (4.95, 1.8)]
    ws = [W] * len(c)
    line = [stroke_open(c, ws),
            taper([(7.3, -7.4), (6.0, -7.3), (4.4, -7.6), (3.0, -8.4)], 0.7),                  # under the pec
            taper([(4.3, -13.1), (2.9, -13.0), (1.4, -13.4)], 0.42),                           # clavicle
            taper([(5.95, -9.35), (6.3, -9.1), (6.65, -9.3)], 0.42),                                          # nipple
            taper([(3.0, -5.6), (3.7, -5.3), (4.3, -5.0)], 0.4),                              # serratus
            taper([(3.2, -4.3), (3.9, -4.0), (4.5, -3.7)], 0.4),
            taper([(3.4, -3.0), (4.0, -2.75), (4.6, -2.5)], 0.36),
            taper([(-4.4, -10.4), (-3.8, -7.6), (-3.3, -4.8)], 0.45),                         # lat
            taper([(-5.4, -12.4), (-4.2, -11.6), (-3.2, -11.8)], 0.36)]                       # the shoulder blade
    return Piece(line, [closed(c + [(0.0, 2.6)])], ring=4.4)


def pelvis_side():
    # front (top) → bottom → back (top); open at the top, it lies over the chest's bottom
    c = [(4.95, -14.4), (4.85, -11.0), (4.6, -8.0), (4.85, -5.0), (4.6, -2.2), (3.7, 0.5), (2.0, 2.4), (-0.5, 3.6),
         (-3.6, 3.3), (-5.75, 1.0), (-6.25, -1.8), (-5.45, -5.0), (-4.35, -8.0), (-4.3, -11.0), (-4.45, -14.4)]
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


@lru_cache(maxsize=3)
def pieces(front):
    """The piece set of a view, by bone (uaN, faN, hdN … for both sides). front: True, False
    (profile) or "3q" (three-quarter)."""
    if front == "3q":
        from . import threeq
        out = {"chest": threeq.chest(), "pelvis": threeq.pelvis(), "head": threeq.head()}
        # the near side (viewer's left) is the mark's own drawing, the far side its mirror
        for s, flip in (("N", False), ("F", True)):
            out.update({"ua" + s: cutout.upper_arm(flip), "fa" + s: cutout.forearm(flip), "th" + s: cutout.thigh(flip),
                        "sh" + s: cutout.shin(flip), "ft" + s: cutout.foot_front(flip), "hand_open" + s: cutout.hand_open(flip),
                        "hand_fist" + s: hand_fist()})
        out["_mirror"] = {k + "F": k + "N" for k in ("ua", "fa", "th", "sh", "ft", "hand_open")}
        out["_same"] = {"hand_fistF": "hand_fistN"}
        return out
    if front:
        # the viewer's left limbs are the mark's own (rig side F, −x); the right ones mirror them
        out = {"chest": cutout.chest_front(), "pelvis": cutout.pelvis_front(), "head": cutout.head_front()}
        for s, flip in (("F", False), ("N", True)):
            out.update({"ua" + s: cutout.upper_arm(flip), "fa" + s: cutout.forearm(flip), "th" + s: cutout.thigh(flip),
                        "sh" + s: cutout.shin(flip), "ft" + s: cutout.foot_front(flip), "hand_open" + s: cutout.hand_open(flip),
                        "hand_fist" + s: hand_fist()})
        # the near side is the mirror of the mark's own (far) side: one drawing for both (size budget)
        out["_mirror"] = {k + "N": k + "F" for k in ("ua", "fa", "th", "sh", "ft", "hand_open")}
        out["_same"] = {"hand_fistN": "hand_fistF"}
        return out
    ua, fa, th, sh = upper_arm_side(), forearm_side(), thigh_side(), shin_side()
    body = {"chest": chest_side(), "pelvis": pelvis_side(), "head": head_side(), "belt": belt(), "skirt": skirt(), "flap": flap()}
    ft = foot_side()
    out = dict(body)
    for s in "NF":
        out.update({"ua" + s: ua, "fa" + s: fa, "th" + s: th, "sh" + s: sh, "ft" + s: ft})
    out["hand_open"] = cutout.hand_open()
    out["hand_fist"] = hand_fist()
    return out
