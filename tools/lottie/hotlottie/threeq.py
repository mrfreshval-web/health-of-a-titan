"""The Titan in three-quarter view (v3): the mark's own frontal drawing, turned.

A figure turned from the front towards the viewer's right shows its near half almost as wide as
from the front and its far half narrowed — the classic way an illustrator turns a frontal study.
The frontal cut pieces (cutout.py) are narrowed that way along their local x: the near half (−x)
by rig.Q_NEAR, the far half by rig.Q_FAR, one smooth curve between them, and shifted by Q_SHIFT so
the silhouette stays on the spine line. Every muscle line of the mark stays the mark's own.

Limbs keep the frontal cuts (in 3/4 a limb shows its front: the kneecap, the shin, the biceps):
the near side (viewer's left) the mark's own, the far side mirrored.
"""
from __future__ import annotations

import math

from . import cutout
from .cutout import Piece
from .rig import Q_FAR, Q_NEAR, Q_SHIFT


def turn(kn=Q_NEAR, kf=Q_FAR, shift=Q_SHIFT, soft=1.6):
    """x → kn·x on the near side (x < 0), kf·x on the far side, smooth over ±soft, then shifted."""
    a, b = (kn + kf) / 2, (kf - kn) / 2

    def f(p):
        u = p[0]
        return (a * u + b * (math.sqrt(u * u + soft * soft) - soft) + shift, p[1])
    return f


def _warp_path(c, f):
    v = [f(q) for q in c["v"]]
    i = [(f((q[0] + d[0], q[1] + d[1]))[0] - w[0], d[1]) for q, d, w in zip(c["v"], c["i"], v)]
    o = [(f((q[0] + d[0], q[1] + d[1]))[0] - w[0], d[1]) for q, d, w in zip(c["v"], c["o"], v)]
    return dict(c, v=[list(q) for q in v], i=[list(q) for q in i], o=[list(q) for q in o])


def warp(pc: Piece, f) -> Piece:
    return Piece([_warp_path(c, f) for c in pc.line], [_warp_path(c, f) for c in pc.knock], pc.ring)


def chest():
    # no waist ring: in 3/4 the wrap covers the waist, and the frontal ring would show past the narrowed flank
    pc = warp(cutout.chest_front(), turn())
    pc.ring = None
    return pc


def pelvis():
    return warp(cutout.pelvis_front(), turn())


def head():
    # the face sits ahead of the neck: the head turns a little more than the chest
    return warp(cutout.head_front(), turn(0.95, 0.5, 1.5))
