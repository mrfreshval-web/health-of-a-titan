"""Key-pose helpers: ground contact of the drawn body, IK for new poses, writing poses.yaml.

    python3 -m hotlottie.posetool report [ids…]   — lowest point of the body outline per pose
"""
from __future__ import annotations

import math
import re
import sys

import yaml

from . import rig, shapes
from .build import POSES, load_poses
from .figure import bone_values

BONES = ["pelvis", "chest", "head", "uaN", "faN", "uaF", "faF", "thN", "shN", "ftN", "thF", "shF", "ftF"]


def outline_world(p, front=False):
    """World points of every bone outline (rig units, without the line)."""
    v = bone_values(p, front)
    shp = shapes.bone_shapes(front)
    pts = {}
    for b in BONES:
        o, a = v[b]["o"], v[b]["abs"]
        c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
        sy = v[b].get("sy", 1.0)
        out = []
        for x, y in shp[b][0]["v"]:
            y *= sy
            out.append((o[0] + c * x - s * y, o[1] + s * x + c * y))
        pts[b] = out
    return pts


def lowest(p, front=False):
    pts = outline_world(p, front)
    best = max(((y, b) for b, ps in pts.items() for _, y in ps))
    return best


def ground(p, front=False, floor=rig.FLOOR, rim=-0.3):
    """Shift the pose vertically so the lowest outline point sits on the floor (the line half
    width counted)."""
    y, _ = lowest(p, front)
    q = dict(p)
    q["ry"] = p["ry"] + (floor - rim) - y
    return q


def solve_arm(p, side, hand, elbow_hint, front=False):
    j = rig.joints(p, front)
    a1, a2 = rig.ik2(j["shoulder" + side], hand, rig.L["ua"], rig.L["fa"], elbow_hint)
    p["ua" + side], p["fa" + side] = a1 % 360, a2 % 360
    return p


def solve_leg(p, side, ankle, knee_hint, front=False):
    j = rig.joints(p, front)
    a1, a2 = rig.ik2(j["hip" + side], ankle, rig.L["th"], rig.L["sh"], knee_hint)
    p["th" + side], p["sh" + side] = a1 % 360, a2 % 360
    return p


def fmt(p):
    y = rig.to_yaml(p)
    parts = [f"root: [{y['root'][0]:g}, {y['root'][1]:g}]", f"torso: {y['torso']:g}", f"head: {y['head']:g}"]
    if "spine" in y:
        parts.append(f"spine: {y['spine']:g}")
    for k in ("arm_n", "arm_f", "leg_n", "leg_f"):
        parts.append(f"{k}: [{', '.join(f'{v:g}' for v in y[k])}]")
    return "{" + ", ".join(parts) + "}"


def write_poses(updates: dict):
    """updates: {exercise_id: {key: pose}} — replaces or appends those entries in poses.yaml,
    keeping the comments and the order of the file."""
    text = POSES.read_text()
    lines = text.split("\n")
    for ex, frames in updates.items():
        block = [f"  {ex}:"] + [f"    {k}: {fmt(p)}" for k, p in frames.items()]
        try:
            start = lines.index(f"  {ex}:")
            end = start + 1
            while end < len(lines) and lines[end].startswith("    "):
                end += 1
            lines[start:end] = block
        except ValueError:
            while lines and lines[-1] == "":
                lines.pop()
            lines += block
    POSES.write_text("\n".join(lines).rstrip("\n") + "\n")


def report(ids):
    lib = load_poses()
    for ex, frames in lib.items():
        if ids and ex not in ids:
            continue
        for k, p in frames.items():
            y, b = lowest(p)
            print(f"{ex:28s} {k:12s} lowest {y:6.2f} ({b})  gap {rig.FLOOR - y:+.2f}")


if __name__ == "__main__":
    if sys.argv[1] == "report":
        report(set(sys.argv[2:]))
