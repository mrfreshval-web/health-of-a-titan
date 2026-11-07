"""The Titan's body as closed outlines in bone-local coordinates (A37 §3 «линия света», brief §Стиль).

Every bone points «up» in its own space: from (0, 0) at the joint to (0, -length). Lottie rotates
the layer by the bone angle (clockwise from up), so the local +x side is the front of a bone that
points up (torso, head) and the back of a bone that points down (arms, legs).

Limbs are width profiles (anatomy: deltoid, biceps, forearm, quadriceps, calf), turned into one
smooth closed Catmull-Rom outline. `open_top` drops the cap at the joint so the line of a near
limb stops at the body instead of drawing a «stick-figure» circle at the hip or shoulder.
Proportions: segment lengths of the rig (TitanPose), widths of a classical statue, not a
bodybuilder (brief, ethics: the Titan is a sculpture, muscles are not exaggerated).
"""
from __future__ import annotations

import math

from .rig import L


def _cr(points, closed=True, tension=1.0):
    """Catmull-Rom through points → Lottie path dict (vertices, relative in/out tangents)."""
    n = len(points)
    v, i, o = [], [], []
    for k in range(n):
        p = points[k]
        if closed:
            a, b = points[(k - 1) % n], points[(k + 1) % n]
        else:
            a = points[max(k - 1, 0)]
            b = points[min(k + 1, n - 1)]
        tx, ty = (b[0] - a[0]) / 2 * tension, (b[1] - a[1]) / 2 * tension
        v.append([p[0], p[1]])
        o.append([tx / 3, ty / 3])
        i.append([-tx / 3, -ty / 3])
    return {"c": closed, "v": v, "i": i, "o": o}


def limb(length, profile, fs, cap_start, cap_end, open_top=False):
    """profile: [(t, w_front, w_back)] with t from 0 (joint) to 1 (next joint); fs = +1 when the
    front is on local +x. Returns (outline_closed, line_path) where line_path may be open."""
    front = [(fs * wf, -t * length) for t, wf, wb in profile]
    back = [(-fs * wb, -t * length) for t, wf, wb in reversed(profile)]
    t1, wf1, wb1 = profile[-1]
    y1 = -t1 * length
    r1 = (wf1 + wb1) / 2
    cx1 = fs * (wf1 - wb1) / 2
    end = [(cx1 + fs * r1 * math.cos(math.radians(a)), y1 - cap_end * math.sin(math.radians(a))) for a in (45, 90, 135)]
    t0, wf0, wb0 = profile[0]
    y0 = -t0 * length
    r0 = (wf0 + wb0) / 2
    cx0 = fs * (wf0 - wb0) / 2
    start = [(cx0 - fs * r0 * math.cos(math.radians(a)), y0 + cap_start * math.sin(math.radians(a))) for a in (45, 90, 135)]
    closed = _cr(front + end + back + start, True)
    if not open_top:
        return closed, closed
    return closed, _cr(front + end + back, False)


# --- profiles (half widths, rig units) ----------------------------------------------------------

UA = [(0.0, 3.2, 3.4), (0.18, 3.5, 3.5), (0.42, 2.9, 3.1), (0.62, 3.15, 2.85), (0.86, 2.5, 2.4), (1.0, 2.25, 2.25)]
FA = [(0.0, 2.35, 2.4), (0.2, 2.75, 2.65), (0.5, 2.0, 1.95), (0.7, 1.6, 1.65), (0.8, 1.95, 1.85), (0.97, 1.75, 1.6)]
TH = [(0.0, 4.2, 4.6), (0.18, 4.6, 4.3), (0.42, 4.4, 3.9), (0.72, 3.55, 3.3), (0.92, 3.0, 2.9), (1.0, 2.9, 2.8)]
SH = [(0.0, 2.8, 2.9), (0.14, 2.7, 3.6), (0.32, 2.5, 3.5), (0.58, 2.15, 2.55), (0.82, 1.7, 1.8), (1.0, 1.6, 1.65)]


def foot():
    # foot bone from ankle (0,0) to toe (0,-6); sole on local +x, instep on local -x
    pts = [(-1.8, 0.7), (-0.7, 2.5), (1.0, 2.3), (1.5, 0.6), (1.45, -2.0), (1.35, -4.6), (0.8, -6.8),
           (-0.3, -6.7), (-1.0, -5.0), (-1.55, -2.6), (-1.95, -0.6)]
    p = _cr(pts, True)
    return p, p


def pelvis(front=False, h=13.0):
    if front:
        prof = [(-0.2, 5.0, 5.0), (0.0, 5.9, 5.9), (0.35, 5.5, 5.5), (0.75, 5.1, 5.1), (1.05, 5.4, 5.4)]
        c, _ = limb(h, prof, 1, 2.6, 1.0)
        return c, c
    prof = [(-0.18, 3.4, 4.6), (0.0, 4.2, 5.2), (0.32, 4.0, 4.4), (0.62, 4.3, 3.9), (1.05, 4.6, 4.2)]
    c, _ = limb(h, prof, 1, 2.4, 1.0)
    return c, c


def chest(front=False, h=13.0):
    if front:
        prof = [(-0.05, 5.3, 5.3), (0.35, 6.4, 6.4), (0.68, 7.4, 7.4), (0.9, 8.2, 8.2), (1.0, 7.6, 7.6)]
        c, _ = limb(h, prof, 1, 1.0, 1.6)
        return c, c
    prof = [(-0.05, 4.5, 4.2), (0.3, 5.0, 4.6), (0.58, 5.9, 5.3), (0.78, 6.1, 5.4), (0.93, 5.1, 5.0), (1.0, 3.8, 4.3)]
    c, _ = limb(h, prof, 1, 1.0, 2.2)
    return c, c


def head(front=False):
    """Neck from the shoulder (0,0) to (0,-3.6), head centre at (0,-9). Profile faces +x: brow,
    straight Greek nose, lips, firm chin; short curly hair as a slightly waved crown."""
    cy = -9.0
    if front:
        pts = [(0.0, cy - 5.7), (2.6, cy - 5.2), (4.1, cy - 3.4), (4.4, cy - 0.8), (4.0, cy + 1.8), (3.0, cy + 3.8),
               (1.4, cy + 5.2), (2.4, -2.2), (2.8, 0.8), (-2.8, 0.8), (-2.4, -2.2), (-1.4, cy + 5.2), (-3.0, cy + 3.8),
               (-4.0, cy + 1.8), (-4.4, cy - 0.8), (-4.1, cy - 3.4), (-2.6, cy - 5.2)]
        p = _cr(pts, True)
        return p, p
    pts = [(0.4, cy - 5.75), (2.4, cy - 5.25), (3.9, cy - 3.9), (4.55, cy - 2.2), (4.45, cy - 1.3), (5.0, cy - 0.1),
           (5.55, cy + 1.0), (4.75, cy + 1.45), (4.95, cy + 2.15), (4.55, cy + 2.75), (4.6, cy + 3.6), (3.6, cy + 4.6),
           (2.2, cy + 4.9), (2.2, -2.6), (2.6, 0.6), (-2.8, 0.6), (-2.8, -2.4), (-3.0, cy + 3.6), (-4.4, cy + 1.6),
           (-5.25, cy - 0.8), (-5.0, cy - 3.0), (-3.9, cy - 4.7), (-1.9, cy - 5.6)]
    p = _cr(pts, True)
    return p, p


def wrap(front=False):
    """The short wrap below the waist (brief: no drapery over the shoulder, a short wrap on the hips)."""
    if front:
        pts = [(-5.2, -3.6), (0.0, -3.3), (5.2, -3.6), (5.6, 0.4), (5.3, 3.0), (3.0, 3.8), (0.0, 5.4), (-3.0, 3.8),
               (-5.3, 3.0), (-5.6, 0.4)]
        p = _cr(pts, True)
        return p, p
    pts = [(3.95, -4.0), (4.05, -1.0), (3.75, 1.8), (2.7, 3.7), (0.4, 3.3), (-2.4, 3.7), (-4.4, 3.1), (-4.95, 0.8),
           (-4.65, -2.0), (-4.25, -4.2), (-1.0, -4.45), (2.0, -4.35)]
    p = _cr(pts, True)
    return p, p


def bone_shapes(front: bool):
    """Shapes per bone: (fill outline, line path). Near limbs open at the body joint (side view)."""
    s = {"pelvis": pelvis(front), "chest": chest(front), "head": head(front), "wrap": wrap(front)}
    for side in ("N", "F"):
        op_arm = (side == "N") or front
        op_leg = (side == "N") and not front
        s["ua" + side] = limb(L["ua"], UA, -1, 2.9, 1.9, open_top=op_arm)
        s["fa" + side] = limb(L["fa"], FA, -1, 2.0, 1.4)
        s["th" + side] = limb(L["th"], TH, -1, 3.8, 2.4, open_top=op_leg)
        s["sh" + side] = limb(L["sh"], SH, -1, 2.4, 1.3)
        s["ft" + side] = foot()
    return s


def inner_lines(front: bool):
    """Interior strokes (brief: clavicle, abs line and knee — one stroke each), per bone, open paths."""
    if front:
        return {
            "chest": [_cr([(-6.2, -11.6), (-3.4, -11.1), (-0.9, -11.7)], False), _cr([(6.2, -11.6), (3.4, -11.1), (0.9, -11.7)], False),
                      _cr([(-6.0, -6.0), (-3.0, -4.6), (-0.5, -5.2)], False), _cr([(6.0, -6.0), (3.0, -4.6), (0.5, -5.2)], False)],
            "pelvis": [_cr([(0.0, -12.4), (0.0, -9.0), (0.0, -5.6)], False)],
            "wrap": [_cr([(-3.5, -2.6), (-1.6, 1.0), (-0.4, 4.6)], False), _cr([(3.5, -2.6), (1.6, 1.0), (0.4, 4.6)], False)],
            "shN": [_cr([(-1.2, -0.4), (0.0, 0.5), (1.2, -0.4)], False)],
            "shF": [_cr([(-1.2, -0.4), (0.0, 0.5), (1.2, -0.4)], False)],
        }
    return {
        "chest": [_cr([(4.0, -11.5), (1.8, -11.9), (-0.6, -12.3)], False), _cr([(5.7, -6.4), (4.0, -4.7), (2.0, -4.6)], False)],
        "pelvis": [_cr([(2.9, -11.4), (3.2, -8.5), (2.9, -5.6)], False)],
        "wrap": [_cr([(3.4, -3.4), (1.8, 0.2), (0.8, 3.6)], False), _cr([(-1.2, -3.9), (-2.0, -0.4), (-2.8, 3.0)], False)],
        "shN": [_cr([(-2.5, -1.3), (-1.5, 0.2), (-1.8, 1.4)], False)],
        "head": [_cr([(-0.6, -9.6), (0.3, -8.8), (-0.2, -7.6), (-0.9, -7.3)], False),
                 _cr([(3.0, -13.6), (1.2, -12.6), (-1.0, -13.0), (-3.0, -11.8)], False)],
    }
