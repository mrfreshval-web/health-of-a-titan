"""Timing of an animation: key poses on a cyclic timeline, easing, contacts (IK) and springs.

A script is a list of steps that starts and ends in the same pose (seamless loop):
    ("move", pose, seconds, ease)   — go to pose
    ("hold", seconds)               — stay (breathing and springs keep it alive)
Every pose arrival gets a marker named after its key (first arrival only).

Principles (brief §Стиль): arcs come from rotating joints (FK), contacts stay planted (two-bone
IK on feet / hands / knees), distal parts lag and settle with a damped spring (overlap and
follow-through, ≤ 2–3 frames at 30 fps), optional small anticipation before a move.
"""
from __future__ import annotations

import math

from . import rig

FPS = 60


# Path v1 motion tokens (visual-production.md §4.0, PathMotion in core:ui): cubic Béziers
BEZIER = {"standard": (0.2, 0.0, 0.0, 1.0), "decel": (0.05, 0.7, 0.1, 1.0), "accel": (0.3, 0.0, 0.8, 0.15),
          "flight": (0.2, 0.7, 0.2, 1.0)}


def bezier(x1, y1, x2, y2, u):
    """CSS / Compose CubicBezierEasing: y at x = u (bisection on the x curve)."""
    bx = lambda t: 3 * (1 - t) ** 2 * t * x1 + 3 * (1 - t) * t * t * x2 + t ** 3
    by = lambda t: 3 * (1 - t) ** 2 * t * y1 + 3 * (1 - t) * t * t * y2 + t ** 3
    lo, hi = 0.0, 1.0
    for _ in range(40):
        mid = (lo + hi) / 2
        if bx(mid) < u:
            lo = mid
        else:
            hi = mid
    return by((lo + hi) / 2)


def ease(name, u):
    u = min(max(u, 0.0), 1.0)
    if name in BEZIER:
        return bezier(*BEZIER[name], u)
    if name == "linear":
        return u
    if name == "sine":
        return 0.5 - 0.5 * math.cos(math.pi * u)
    if name == "out":
        return 1 - (1 - u) ** 3
    if name == "in":
        return u ** 3
    if name == "soft":  # quintic-ish smootherstep: slow in and out for holds and stretches
        return u * u * u * (u * (u * 6 - 15) + 10)
    if name.startswith("back"):  # anticipation: a small move the other way first, e.g. back0.06
        a = float(name[4:] or 0.06)
        base = 0.5 - 0.5 * math.cos(math.pi * u)
        return base - a * math.sin(math.pi * u) * (1 - u) * 2.2
    # default: cubic in-out, like TitanPose.kt
    return 4 * u ** 3 if u < 0.5 else 1 - (-2 * u + 2) ** 3 / 2


class Script:
    def __init__(self, poses: dict, steps: list, start: str, contacts=(), springs=None, extra=None, front=False, loop=True):
        self.poses = poses
        self.loop = loop
        self.front = front
        self.contacts = []
        for c in contacts:
            if len(c) > 2 and isinstance(c[2], str):  # "pose:<key>": stay where that key pose has it
                j = rig.joints(poses[c[2].split(":", 1)[1]], front)
                eff = ("hand" if c[0].startswith("arm") else c[1]) + c[0][-1]
                c = (c[0], c[1], j[eff])
            self.contacts.append(tuple(c))
        self.springs = springs or {}
        self.extra = extra
        # flatten to segments
        self.segs = []  # (t0, t1, from_pose, to_pose, ease, label_to)
        t = 0.0
        cur = start
        self.markers = []
        seen = set()
        self.marker_frames = {}
        if start not in seen:
            seen.add(start)
            self.markers.append((start, 0.0))
        for s in steps:
            if s[0] == "move":
                _, to, dur, *rest = s
                e = rest[0] if rest else "inout"
                self.segs.append((t, t + dur, cur, to, e))
                t += dur
                cur = to
                if to not in seen and not to.startswith("_"):
                    seen.add(to)
                    self.markers.append((to, t))
            elif s[0] == "hold":
                self.segs.append((t, t + s[1], cur, cur, "linear"))
                t += s[1]
            elif s[0] == "mark":
                self.markers.append((s[1], t))
        assert cur == start or not loop, "the loop must end in the start pose"
        self.period = t
        self.n_frames = int(round(t * FPS))

    def pose(self, name):
        p = self.poses[name]
        return p

    def effectors(self, p):
        j = rig.joints(p, self.front)
        return j

    def at(self, t):
        t = t % self.period
        for t0, t1, a, b, e in self.segs:
            if t0 <= t < t1 or (t1 == self.period and t >= t1 - 1e-9):
                u = 0.0 if t1 == t0 else (t - t0) / (t1 - t0)
                k = ease(e, u)
                pa, pb = self.pose(a), self.pose(b)
                p = rig.lerp_pose(pa, pb, k)
                if self.contacts:
                    ja, jb = self.effectors(pa), self.effectors(pb)
                    self._solve(p, ja, jb, k)
                return p, (t - t0), (t0, t1, a, b)
        raise ValueError(t)

    def _solve(self, p, ja, jb, k):
        lerp = lambda A, B: (A[0] + (B[0] - A[0]) * k, A[1] + (B[1] - A[1]) * k)
        j = rig.joints(p, self.front)
        for c in self.contacts:
            limb, kind = c[0], c[1]
            fixed = c[2] if len(c) > 2 else None
            side = limb[-1]
            if limb.startswith("leg"):
                o = j["hip" + side]
                if kind == "ankle":
                    tgt = fixed or lerp(ja["ankle" + side], jb["ankle" + side])
                    a1, a2 = rig.ik2(o, tgt, rig.L["th"], rig.L["sh"], j["knee" + side])
                    p["th" + side] = _near(a1, p["th" + side])
                    p["sh" + side] = _near(a2, p["sh" + side])
                elif kind == "knee":
                    tgt = fixed or lerp(ja["knee" + side], jb["knee" + side])
                    a1 = math.degrees(math.atan2(tgt[0] - o[0], -(tgt[1] - o[1])))
                    p["th" + side] = _near(a1, p["th" + side])
            else:
                o = j["shoulder" + side]
                tgt = fixed or lerp(ja["hand" + side], jb["hand" + side])
                ln = p.get("armLen" + side, 1.0)
                ul = ln * p.get("uaLen" + side, 1.0)
                a1, a2 = rig.ik2(o, tgt, rig.L["ua"] * ul, rig.L["fa"] * ln, j["elbow" + side])
                p["ua" + side] = _near(a1, p["ua" + side])
                p["fa" + side] = _near(a2, p["fa" + side])
            j = rig.joints(p, self.front)

    def frames(self):
        n = self.n_frames
        out = []
        for f in range(n + 1):
            t = (f / FPS) % self.period if self.loop else min(f / FPS, self.period - 1e-9)
            p, _, seg = self.at(t)
            p = dict(p)
            if self.extra:
                self.extra(t, p, seg)
            out.append(p)
        if self.loop:
            out[n] = dict(out[0])
        # make angles continuous
        for ch in rig.ANGLES:
            prev = None
            for p in out:
                if prev is not None:
                    p[ch] = prev + rig.shortest(prev, p[ch])
                prev = p[ch]
            d = out[n][ch] - out[0][ch]
            if self.loop and abs(d) > 1e-6:
                # the loop came back a full turn away: shift every value so the start matches
                assert abs(abs(d) - 360) < 1e-3 or abs(d) < 1e-3, (ch, d)
        if self.loop:
            out[n] = dict(out[0])
        for ch, (freq, zeta) in self.springs.items():
            self._spring(out, ch, freq, zeta)
        return out

    def _spring(self, frames, ch, freq, zeta):
        """Damped follower x'' = w²(c − x) − 2ζw x', run three cycles to its periodic state."""
        n = len(frames) - 1
        w = 2 * math.pi * freq
        dt = 1.0 / FPS
        if not self.loop:
            n += 1
        target = [frames[f][ch] for f in range(n)]
        x, v = target[0], 0.0
        res = [0.0] * n
        for cycle in range(4 if self.loop else 1):
            for f in range(n):
                c = target[f]
                for _ in range(4):
                    h = dt / 4
                    a = w * w * (c - x) - 2 * zeta * w * v
                    v += a * h
                    x += v * h
                if cycle == (3 if self.loop else 0):
                    res[f] = x
        # keep keyposes exact in holds: the follower settles there anyway
        for f in range(n):
            frames[f][ch] = res[f]
        if self.loop:
            frames[n][ch] = res[0]


def _near(a, ref):
    return ref + rig.shortest(ref, a)
