"""Cut-out («марионетка») pieces of the brand Titan.

The brand has one drawing of the Titan, frontal, in brand/mark-v2/master (traced figure R1). The
limbs are cut from it along the 12 joints of the rig and laid into bone-local space; what the
frontal drawing does not have (a profile head, a torso, hips and wrap seen from the side, a foot
in profile, a gripping hand) is drawn here in the same manner: a heavy outer line and tapered inner
lines of muscle and cloth, as fills, so every piece reads like the mark.

A piece is (line, knockout): Lottie paths in bone-local rig units. A bone points «up» in its own
space: from (0, 0) at its joint to (0, -length).
"""
from __future__ import annotations

import math
from functools import lru_cache

import pathops

from . import brand
from .rig import L

K = 15.0                     # master px per rig unit (hip → neck base = 390 px = 26 units)

# Landmarks of the master drawing (2048 space): the figure's right arm and leg (viewer's left),
# which are drawn whole; the left leg in the master is crossed behind the wrap.
SHOULDER = (905.0, 772.0)
ELBOW = (715.0, 795.0)
WRIST = (562.0, 782.0)
HIP = (955.0, 1110.0)
KNEE = (963.0, 1395.0)
ANKLE = (972.0, 1738.0)
NECK = (1024.0, 720.0)
WAIST = (1024.0, 915.0)
PELVIS = (1024.0, 1110.0)

FOREARM = 11.0               # elbow → wrist in rig units; the hand carries on to the grip point


# --- geometry helpers ---------------------------------------------------------------------------

def poly(pts):
    p = pathops.Path()
    p.moveTo(*pts[0])
    for q in pts[1:]:
        p.lineTo(*q)
    p.close()
    return p


def circle(c, r, n=None):
    k = 0.5523 * r
    x, y = c
    p = pathops.Path()
    p.moveTo(x + r, y)
    p.cubicTo(x + r, y + k, x + k, y + r, x, y + r)
    p.cubicTo(x - k, y + r, x - r, y + k, x - r, y)
    p.cubicTo(x - r, y - k, x - k, y - r, x, y - r)
    p.cubicTo(x + k, y - r, x + r, y - k, x + r, y)
    p.close()
    return p


def union(*ps):
    out = pathops.Path()
    for p in ps:
        out = pathops.op(out, p, pathops.PathOp.UNION)
    return out


def inter(a, b):
    return pathops.op(a, b, pathops.PathOp.INTERSECTION)


def diff(a, b):
    return pathops.op(a, b, pathops.PathOp.DIFFERENCE)


def band(a, b, ra, rb, ext_a=0.0, ext_b=0.0):
    """A quad strip from a to b (half widths ra, rb), extended along the axis."""
    dx, dy = b[0] - a[0], b[1] - a[1]
    ln = math.hypot(dx, dy)
    ux, uy = dx / ln, dy / ln
    nx, ny = -uy, ux
    a2 = (a[0] - ux * ext_a, a[1] - uy * ext_a)
    b2 = (b[0] + ux * ext_b, b[1] + uy * ext_b)
    return poly([(a2[0] + nx * ra, a2[1] + ny * ra), (b2[0] + nx * rb, b2[1] + ny * rb),
                 (b2[0] - nx * rb, b2[1] - ny * rb), (a2[0] - nx * ra, a2[1] - ny * ra)])


@lru_cache(maxsize=1)
def master():
    return brand.titan()


# --- master path → Lottie paths ---------------------------------------------------------------------

class _Pen:
    def __init__(self, f):
        self.f = f
        self.contours = []
        self.cur = None

    def moveTo(self, p):
        self.cur = {"v": [self.f(p)], "i": [(0.0, 0.0)], "o": [(0.0, 0.0)]}

    def lineTo(self, p):
        self.cur["v"].append(self.f(p))
        self.cur["i"].append((0.0, 0.0))
        self.cur["o"].append((0.0, 0.0))

    def curveTo(self, *pts):
        c1, c2, p = (self.f(q) for q in pts)
        last = self.cur["v"][-1]
        self.cur["o"][-1] = (c1[0] - last[0], c1[1] - last[1])
        self.cur["v"].append(p)
        self.cur["i"].append((c2[0] - p[0], c2[1] - p[1]))
        self.cur["o"].append((0.0, 0.0))

    def qCurveTo(self, *pts):
        # one quadratic (fontTools pens may pass implied on-curve points; pathops passes 2)
        start = self.cur["v"][-1]
        for k in range(len(pts) - 1):
            q = pts[k]
            end = pts[k + 1] if k == len(pts) - 2 else ((pts[k][0] + pts[k + 1][0]) / 2, (pts[k][1] + pts[k + 1][1]) / 2)
            qq, ee = self.f(q), self.f(end)
            c1 = (start[0] + 2 / 3 * (qq[0] - start[0]), start[1] + 2 / 3 * (qq[1] - start[1]))
            c2 = (ee[0] + 2 / 3 * (qq[0] - ee[0]), ee[1] + 2 / 3 * (qq[1] - ee[1]))
            self.cur["o"][-1] = (c1[0] - start[0], c1[1] - start[1])
            self.cur["v"].append(ee)
            self.cur["i"].append((c2[0] - ee[0], c2[1] - ee[1]))
            self.cur["o"].append((0.0, 0.0))
            start = ee

    def closePath(self):
        c = self.cur
        if len(c["v"]) > 1 and math.dist(c["v"][0], c["v"][-1]) < 1e-6:
            c["i"][0] = c["i"][-1]
            c["v"].pop()
            c["i"].pop()
            c["o"].pop()
        c["c"] = True
        if len(c["v"]) >= 3:
            self.contours.append(c)
        self.cur = None

    endPath = closePath


SIMPLIFY_TOL = 0.05          # rig units (≈ 0.75 px of the master, under half a pixel on screen)


def to_lottie(path, f, tol=SIMPLIFY_TOL):
    pen = _Pen(f)
    path.draw(pen)
    return [simplify(c, tol) for c in pen.contours] if tol else pen.contours


def _bez(p0, p1, p2, p3, t):
    u = 1 - t
    a, b, c, d = u * u * u, 3 * u * u * t, 3 * u * t * t, t * t * t
    return (a * p0[0] + b * p1[0] + c * p2[0] + d * p3[0], a * p0[1] + b * p1[1] + c * p2[1] + d * p3[1])


def _unit(v):
    ln = math.hypot(*v)
    return (v[0] / ln, v[1] / ln) if ln > 1e-9 else None


def _smooth(c, k):
    a, b = _unit(c["i"][k]), _unit(c["o"][k])
    return a is not None and b is not None and a[0] * b[0] + a[1] * b[1] < -0.985


def _fit(c, a, b, n):
    """One cubic for the segments a…b of contour c (indices mod n), tangents kept; None if off."""
    v, i, o = c["v"], c["i"], c["o"]
    pts = []
    for s in range(a, b):
        k, k1 = s % n, (s + 1) % n
        p0, p3 = v[k], v[k1]
        p1 = (p0[0] + o[k][0], p0[1] + o[k][1])
        p2 = (p3[0] + i[k1][0], p3[1] + i[k1][1])
        for j in range(8 if s < b - 1 else 9):
            pts.append(_bez(p0, p1, p2, p3, j / 8))
    p0, p3 = v[a % n], v[b % n]
    t0, t1 = _unit(o[a % n]), _unit(i[b % n])
    if t0 is None or t1 is None:
        return None
    d = [0.0]
    for q0, q1 in zip(pts, pts[1:]):
        d.append(d[-1] + math.dist(q0, q1))
    if d[-1] < 1e-9:
        return None
    us = [x / d[-1] for x in d]
    c11 = c12 = c22 = x1 = x2 = 0.0
    for u, s_ in zip(us, pts):
        b0, b1, b2, b3 = (1 - u) ** 3, 3 * (1 - u) ** 2 * u, 3 * (1 - u) * u * u, u ** 3
        a1 = (t0[0] * b1, t0[1] * b1)
        a2 = (t1[0] * b2, t1[1] * b2)
        r = (s_[0] - (b0 + b1) * p0[0] - (b2 + b3) * p3[0], s_[1] - (b0 + b1) * p0[1] - (b2 + b3) * p3[1])
        c11 += a1[0] * a1[0] + a1[1] * a1[1]
        c12 += a1[0] * a2[0] + a1[1] * a2[1]
        c22 += a2[0] * a2[0] + a2[1] * a2[1]
        x1 += a1[0] * r[0] + a1[1] * r[1]
        x2 += a2[0] * r[0] + a2[1] * r[1]
    det = c11 * c22 - c12 * c12
    if abs(det) < 1e-12:
        return None
    al, be = (x1 * c22 - x2 * c12) / det, (c11 * x2 - c12 * x1) / det
    if al <= 0 or be <= 0:
        return None
    q1 = (p0[0] + t0[0] * al, p0[1] + t0[1] * al)
    q2 = (p3[0] + t1[0] * be, p3[1] + t1[1] * be)
    return (q1, q2), [(u, s_) for u, s_ in zip(us, pts)], (p0, p3)


def _err(fit):
    (q1, q2), samples, (p0, p3) = fit
    # distance of each sample to the fitted curve (nearest of a dense sampling)
    dense = [_bez(p0, q1, q2, p3, j / 48) for j in range(49)]
    return max(min(math.dist(s_, q) for q in dense) for _, s_ in samples)


def simplify(c, tol):
    """Merge runs of segments through smooth vertices into single cubics within tol."""
    n = len(c["v"])
    if n < 4:
        return c
    smooth = [_smooth(c, k) for k in range(n)]
    if all(smooth):
        start = 0
    else:
        start = next(k for k in range(n) if not smooth[k])
    keep_v, keep_i, keep_o = [], [], []
    a = start
    end = start + n
    segs = []
    while a < end:
        b = a + 1
        best = None
        while b < end and smooth[b % n]:
            ft = _fit(c, a, b + 1, n)
            if ft is None or _err(ft) > tol:
                break
            best = (b + 1, ft)
            b += 1
        if best:
            segs.append((a, best[0], best[1][0]))
            a = best[0]
        else:
            k = a % n
            k1 = (a + 1) % n
            segs.append((a, a + 1, ((c["v"][k][0] + c["o"][k][0], c["v"][k][1] + c["o"][k][1]),
                                    (c["v"][k1][0] + c["i"][k1][0], c["v"][k1][1] + c["i"][k1][1]))))
            a += 1
    m = len(segs)
    v = [c["v"][s[0] % n] for s in segs]
    o = [(s[2][0][0] - v[j][0], s[2][0][1] - v[j][1]) for j, s in enumerate(segs)]
    i = [None] * m
    for j, s in enumerate(segs):
        nxt = (j + 1) % m
        p3 = v[nxt]
        i[nxt] = (s[2][1][0] - p3[0], s[2][1][1] - p3[1])
    return {"c": True, "v": v, "i": i, "o": o}


def fill_holes(path):
    """The same outline with every contour filled (the glass behind a hand: its line, no holes)."""
    out = pathops.Path()
    for cont in path.contours:
        p = pathops.Path()
        pen = p.getPen()
        cont.draw(pen)
        if not p.clockwise:
            p = p.reverse() or p
        out = union(out, p)
    return out


def affine(a, b, length, flip=False):
    """Master px → bone-local rig units for a bone drawn from a to b (rig length `length`): across
    the bone 1/K, along it stretched so |ab| becomes `length`. Returns (pathops affine, point fn)."""
    dx, dy = b[0] - a[0], b[1] - a[1]
    ln = math.hypot(dx, dy)
    ang = math.atan2(dx, -dy)
    return _affine(a, ang, (-1.0 if flip else 1.0) / K, length / ln)


def fixed(a, ang_deg=0.0, flip=False, scale=1.0):
    return _affine(a, math.radians(ang_deg), (-1.0 if flip else 1.0) * scale / K, scale / K)


def _affine(o, ang, sx, sy):
    c, s = math.cos(ang), math.sin(ang)
    m = (sx * c, -sy * s, sx * s, sy * c, -sx * (c * o[0] + s * o[1]), sy * (s * o[0] - c * o[1]))

    def f(p):
        return (m[0] * p[0] + m[2] * p[1] + m[4], m[1] * p[0] + m[3] * p[1] + m[5])
    return m, f


class Piece:
    def __init__(self, line, knock, ring=None):
        self.line, self.knock, self.ring = line, knock, ring


def ident(p):
    return (p[0], p[1])


def cut(m, f, master_mask, local_add=(), knock_add=(), line_sub=None):
    """The master line art and knockout in a piece's local space, inside master_mask (master px)
    ∪ local_add (local paths, e.g. the discs round the joints). knock_add widens the glass only."""
    line, knock = master()
    lm, km = line.transform(*m), knock.transform(*m)
    mask = poly([f(p) for p in master_mask]) if master_mask else pathops.Path()
    mask = union(mask, *local_add)
    lmask = diff(mask, line_sub) if line_sub is not None else mask
    return Piece(to_lottie(inter(lm, lmask), ident), to_lottie(inter(km, union(mask, *knock_add)), ident))


def lcircle(c, r, n=24):
    return circle(c, r, n)


# joint discs, rig units (the same radius on both sides of a joint)
R_ELBOW, R_WRIST, R_KNEE, R_ANKLE = 2.45, 1.5, 2.95, 1.85


def upper_arm(flip=True):
    m, f = affine(SHOULDER, ELBOW, L["ua"], flip)
    return cut(m, f, [(712, 735), (850, 724), (893, 738), (912, 760), (912, 850), (712, 850)], [lcircle((0, -L["ua"]), R_ELBOW)])


def forearm(flip=True):
    m, f = affine(ELBOW, WRIST, FOREARM, flip)
    pc = cut(m, f, [(715, 735), (715, 850), (566, 835), (566, 740)], [lcircle((0, 0), R_ELBOW), lcircle((0, -FOREARM), R_WRIST)])
    pc.ring = R_ELBOW
    return pc


def hand_open(flip=True):
    m, f = fixed(WRIST, 0.0, flip)
    line, _ = master()
    mask = union(poly([f(p) for p in [(495, 628), (586, 628), (586, 748), (568, 760), (568, 842), (495, 842)]]), lcircle((0, 0), R_WRIST))
    hand = inter(line.transform(*m), mask)
    # the master's glass is a box round the hands (they lie on the capitals): the hand's own outline here
    glass = union(fill_holes(hand), lcircle((0, 0), R_WRIST))
    return Piece(to_lottie(hand, ident), to_lottie(glass, ident), ring=R_WRIST)


def thigh(flip=False):
    m, f = affine(HIP, KNEE, L["th"], flip)
    clean = [(858, 1076), (905, 1088), (1026, 1232), (1020, 1395), (905, 1395), (866, 1250)]
    full = [(858, 1050), (1026, 1050), (1020, 1395), (866, 1395)]
    pc = cut(m, f, clean, [lcircle((0, -L["th"]), R_KNEE)], knock_add=[poly([f(p) for p in full]), lcircle((0, 0), 4.9)])
    return pc


def shin(flip=False):
    m, f = affine(KNEE, ANKLE, L["sh"], flip)
    pc = cut(m, f, [(905, 1395), (1020, 1395), (1012, 1738), (935, 1738)], [lcircle((0, 0), R_KNEE), lcircle((0, -L["sh"]), R_ANKLE)])
    pc.ring = R_KNEE
    return pc


def foot_front(flip=False):
    m, f = fixed(ANKLE, 180.0, flip)
    pc = cut(m, f, [(892, 1742), (1018, 1742), (1018, 1815), (892, 1815)], [lcircle((0, 0), R_ANKLE)])
    pc.ring = R_ANKLE
    return pc


# --- the frontal body: head, chest, pelvis with the wrap, as the mark draws them ------------------

HEAD_MASK = [(930, 478), (1120, 478), (1120, 650), (1078, 700), (1066, 742), (982, 742), (970, 700), (930, 650)]


def head_front():
    m, f = fixed(NECK)
    return cut(m, f, HEAD_MASK)


def chest_front():
    m, f = fixed(WAIST)
    line, knock = master()
    lm, km = line.transform(*m), knock.transform(*m)
    side = [(985, 690), (1063, 690), (1133, 725), (1143, 760), (1122, 840), (1112, 915), (936, 915), (926, 840), (905, 760), (915, 725)]
    mask = union(poly([f(p) for p in side]), lcircle((0, 0), 7.0))
    mask = diff(mask, poly([f(p) for p in HEAD_MASK[:2] + [(1120, 650), (1078, 700), (1066, 735), (982, 735), (970, 700), (930, 650)]]))
    from .drawn import taper
    line = to_lottie(inter(lm, mask), ident)
    # under the raised arms the cut would show: the flank line of the drawing, carried up to the shoulder
    for sgn in (-1, 1):
        pts = [f((1024 + sgn * (1024 - x), y)) for x, y in [(919, 770), (924, 800), (930, 836), (934, 866)]]
        line.append(taper(pts, 0.75))
    return Piece(line, to_lottie(inter(km, mask), ident), ring=7.0)


def pelvis_front():
    m, f = fixed(PELVIS)
    pts = [(866, 905), (1182, 905), (1222, 1010), (1332, 1290), (1312, 1402), (1240, 1398), (1180, 1332), (1100, 1272),
           (1028, 1240), (905, 1090), (860, 1078)]
    return cut(m, f, pts, [lcircle((0, -13.0), 7.0)])


PIECES = {"ua": upper_arm, "fa": forearm, "hand_open": hand_open, "th": thigh, "sh": shin, "ft_front": foot_front}
