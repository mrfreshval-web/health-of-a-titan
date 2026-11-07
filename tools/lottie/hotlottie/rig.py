"""TitanPose rig in Python: the same forward kinematics as TitanPose.kt and pose.js (A40 §6.2).

Box 100 × 100, y grows down, floor at y = 92; angles absolute, degrees, clockwise from «up».
A pose is a flat dict of channels (see CHANNELS); poses.yaml entries convert with `from_yaml`.
"""
from __future__ import annotations

import math

L = dict(torso=26.0, neck=3.6, head=5.4, ua=14.0, fa=13.0, th=21.0, sh=21.0, ft=6.0)
FLOOR = 92.0
FRONT_SHOULDER = 6.5
FRONT_HIP = 4.0

ANGLES = ["torso", "head", "uaN", "faN", "uaF", "faF", "thN", "shN", "ftN", "thF", "shF", "ftF"]
# Non-angle channels: hip position, spine bend, breath (0…1 chest/belly), arm foreshortening.
LINEAR = ["rx", "ry", "spine", "breath", "belly", "armLenN", "armLenF", "nod", "uaLenN", "uaLenF"]
ONE = {"armLenN", "armLenF", "uaLenN", "uaLenF"}       # channels whose neutral value is 1
CHANNELS = ANGLES + LINEAR

# 3/4 view (v3): the figure faces right and has turned a little towards the viewer, so the near
# shoulder and hip sit behind the spine line (−x) and the far ones ahead of it, half hidden by the
# chest. The frontal drawing of the mark is narrowed the same way (threeq.py): the near half by
# Q_NEAR, the far half by Q_FAR, the whole shifted by Q_SHIFT so the silhouette stays centred.
Q_NEAR, Q_FAR, Q_SHIFT = 0.88, 0.62, 1.1


def side_offsets(front, side: str) -> tuple[float, float]:
    """(shoulder, hip) x offsets of a side ("N" or "F") from the spine line for a view —
    front: True = the frontal view, False = profile, "3q" = three-quarter."""
    sgn = 1 if side == "N" else -1
    if front == "3q":
        k = Q_NEAR if side == "N" else Q_FAR
        return -sgn * FRONT_SHOULDER * k + Q_SHIFT, -sgn * FRONT_HIP * k + Q_SHIFT
    return (sgn * FRONT_SHOULDER, sgn * FRONT_HIP) if front else (0.0, 0.0)


def step(o, a, length):
    r = math.radians(a)
    return (o[0] + math.sin(r) * length, o[1] - math.cos(r) * length)


def shortest(a, b):
    d = (b - a) % 360.0
    if d > 180.0:
        d -= 360.0
    return d


def from_yaml(p: dict) -> dict:
    arm_n = p["arm_n"]
    arm_f = p.get("arm_f", arm_n)
    leg_n = p["leg_n"]
    leg_f = p.get("leg_f", leg_n)
    return dict(
        rx=float(p["root"][0]), ry=float(p["root"][1]),
        torso=float(p["torso"]), head=float(p.get("head", p["torso"])),
        uaN=float(arm_n[0]), faN=float(arm_n[1]), uaF=float(arm_f[0]), faF=float(arm_f[1]),
        thN=float(leg_n[0]), shN=float(leg_n[1]), ftN=float(leg_n[2]),
        thF=float(leg_f[0]), shF=float(leg_f[1]), ftF=float(leg_f[2]),
        spine=float(p.get("spine", 0.0)), breath=0.0, belly=0.0, armLenN=1.0, armLenF=1.0, nod=0.0,
    )


def to_yaml(p: dict) -> dict:
    r = lambda v: round(v, 1)
    out = {
        "root": [r(p["rx"]), r(p["ry"])], "torso": r(p["torso"] % 360), "head": r(p["head"] % 360),
        "arm_n": [r(p["uaN"] % 360), r(p["faN"] % 360)], "arm_f": [r(p["uaF"] % 360), r(p["faF"] % 360)],
        "leg_n": [r(p["thN"] % 360), r(p["shN"] % 360), r(p["ftN"] % 360)],
        "leg_f": [r(p["thF"] % 360), r(p["shF"] % 360), r(p["ftF"] % 360)],
    }
    if abs(p.get("spine", 0)) > 1e-6:
        out["spine"] = r(p["spine"])
    return out


def joints(p: dict, front: bool = False) -> dict:
    hip = (p["rx"], p["ry"])
    sh = step(hip, p["torso"], L["torso"])
    neck = step(sh, p["head"], L["neck"])
    head = step(neck, p["head"], L["head"])
    out = dict(hip=hip, shoulder=sh, neck=neck, head=head)
    for side in "NF":
        sw, hw = side_offsets(front, side)
        s0 = (sh[0] + sw, sh[1])
        e = step(s0, p["ua" + side], L["ua"] * p.get("armLen" + side, 1.0) * p.get("uaLen" + side, 1.0))
        out["shoulder" + side] = s0
        out["elbow" + side] = e
        out["hand" + side] = step(e, p["fa" + side], L["fa"] * p.get("armLen" + side, 1.0))
        h0 = (hip[0] + hw, hip[1])
        k = step(h0, p["th" + side], L["th"])
        a = step(k, p["sh" + side], L["sh"])
        out["hip" + side] = h0
        out["knee" + side] = k
        out["ankle" + side] = a
        out["toe" + side] = step(a, p["ft" + side], L["ft"])
    return out


def lerp_pose(a: dict, b: dict, t: float) -> dict:
    out = {}
    for k in CHANNELS:
        d = 1.0 if k in ONE else 0.0
        x, y = a.get(k, d), b.get(k, d)
        out[k] = x + shortest(x, y) * t if k in ANGLES else x + (y - x) * t
    return out


def ik2(origin, target, l1, l2, hint_mid):
    """Two-bone IK: absolute angles (deg) of both bones from origin to target; of the two
    solutions, the one whose middle joint is nearer to hint_mid."""
    dx, dy = target[0] - origin[0], target[1] - origin[1]
    d = math.hypot(dx, dy)
    d = max(min(d, (l1 + l2) * 0.9995), abs(l1 - l2) + 1e-6)
    base = math.degrees(math.atan2(dx, -dy))  # angle clockwise from up
    c = (l1 * l1 + d * d - l2 * l2) / (2 * l1 * d)
    off = math.degrees(math.acos(max(-1.0, min(1.0, c))))
    best = None
    for sgn in (1, -1):
        a1 = base + sgn * off
        m = step(origin, a1, l1)
        end = (origin[0] + dx / math.hypot(dx, dy) * d, origin[1] + dy / math.hypot(dx, dy) * d) if math.hypot(dx, dy) > 0 else target
        a2 = math.degrees(math.atan2(end[0] - m[0], -(end[1] - m[1])))
        dist = math.hypot(m[0] - hint_mid[0], m[1] - hint_mid[1])
        if best is None or dist < best[0]:
            best = (dist, a1, a2)
    return best[1], best[2]
