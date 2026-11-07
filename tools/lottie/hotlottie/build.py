"""Build the Lottie files: python3 -m hotlottie.build [ids…] (run from tools/lottie).

Reads content/training/poses.yaml (key poses, the same data as the Compose rig), the timing in
exercises.py / heroes.py, and writes android/core/ui/src/main/assets/lottie/<id>.json
plus manifest.json (file, markers, frames, poster) for the player.
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

import yaml

from . import figure, motion, rig, titan
from . import lottie as lt
from . import props as pr

ROOT = Path(__file__).resolve().parents[3]
POSES = ROOT / "content/training/poses.yaml"
OUT = ROOT / "android/core/ui/src/main/assets/lottie"


def load_poses():
    data = yaml.safe_load(POSES.read_text())
    return {ex: {k: rig.from_yaml(v) for k, v in frames.items()} for ex, frames in data["poses"].items()}


def resolve_poses(lib, ex_id, spec):
    poses = dict(lib.get(ex_id, {}))
    for k, v in (spec.get("poses") or {}).items():
        if "base" in v:
            base = dict(poses[v["base"]])
            for ch, val in v.items():
                if ch != "base":
                    base[ch] = val
            poses[k] = base
        elif "root" in v:
            poses[k] = rig.from_yaml(v)
        else:
            poses[k] = v
    return poses


def markers_json(script, extra=()):
    out = []
    for name, t in list(script.markers) + list(extra):
        out.append({"tm": int(round(t * motion.FPS)), "cm": name, "dr": 0})
    out.sort(key=lambda m: m["tm"])
    return out


def build_exercise(ex_id, spec, lib):
    poses = resolve_poses(lib, ex_id, spec)
    front = spec.get("front", False)
    sc = motion.Script(poses, spec["steps"], spec["start"], spec.get("contacts", ()), spec.get("springs"), spec.get("extra"), front)
    frames = sc.frames()
    n = sc.n_frames
    breaks = sorted({int(round(t0 * motion.FPS)) for t0, *_ in sc.segs} | {n})
    extra_pts = spec.get("frame_pts", [])
    fr = figure.fit_frame(figure.motion_points(frames, front, extra_pts), smax=spec.get("smax", 9.2))
    b = figure.Builder(ex_id, n, breaks, fr, front)
    if spec.get("floor", True):
        pr.floor(b)
    titan.build_titan(b, frames, front, spec.get("props_behind", ()), spec.get("props_held", ()), spec.get("props_front", ()),
                      breath_scale=spec.get("breath_scale", 0.035), belly_scale=spec.get("belly_scale", 0.07), hands=spec.get("hands"), fold=spec.get("fold", ()),
                      arms_over=spec.get("arms_over", ()))
    mk = list(sc.markers)
    poster = spec.get("poster")
    if poster:
        pt = dict(sc.markers).get(poster)
        if pt is None:
            raise KeyError(f"{ex_id}: poster {poster}")
        mk.append(("poster", pt + spec.get("poster_offset", 0.0)))
    mk += spec.get("markers", [])
    markers = markers_json(sc, [m for m in mk if m not in sc.markers])
    meta = {"g": "hot-lottie 3 (tools/lottie)", "a": "Agent 17", "k": "Health of a Titan", "d": ex_id}
    doc = b.json(markers, meta)
    return doc, sc, frames, fr


def write(doc, name):
    OUT.mkdir(parents=True, exist_ok=True)
    path = OUT / f"{name}.json"
    txt = json.dumps(doc, separators=(",", ":"), ensure_ascii=False)
    path.write_text(txt)
    return path, len(txt.encode())


def main(argv):
    from .exercises import EXERCISES
    from . import heroes, paths
    lib = load_poses()
    only = set(argv)
    manifest_path = OUT / "manifest.json"
    manifest = json.loads(manifest_path.read_text()) if manifest_path.exists() else {"version": 1, "fps": motion.FPS, "files": {}}
    for ex_id, spec in EXERCISES.items():
        if only and ex_id not in only:
            continue
        doc, sc, frames, fr = build_exercise(ex_id, spec, lib)
        path, size = write(doc, ex_id)
        manifest["files"][ex_id] = {
            "file": f"lottie/{ex_id}.json", "kind": spec.get("kind", "reps"), "frames": sc.n_frames,
            "markers": {m["cm"]: m["tm"] for m in doc["markers"]}, "bytes": size,
        }
        print(f"{ex_id:28s} {sc.n_frames:4d} fr  {size / 1024:6.1f} KB  layers {len(doc['layers'])}  s={fr.s:.2f}")
    for hid, fn in heroes.HEROES.items():
        if only and hid not in only:
            continue
        doc = fn(lib)
        path, size = write(doc, hid)
        manifest["files"][hid] = {"file": f"lottie/{hid}.json", "kind": "hero", "frames": doc["op"],
                                  "markers": {m["cm"]: m["tm"] for m in doc["markers"]}, "bytes": size}
        print(f"{hid:28s} {doc['op']:4d} fr  {size / 1024:6.1f} KB  layers {len(doc['layers'])}")
    for pid, fn in paths.PATHS.items():
        if only and pid not in only:
            continue
        doc = fn()
        path, size = write(doc, pid)
        manifest["files"][pid] = {"file": f"lottie/{pid}.json", "kind": "path", "frames": doc["op"],
                                  "markers": {m["cm"]: m["tm"] for m in doc["markers"]}, "bytes": size}
        print(f"{pid:28s} {doc['op']:4d} fr  {size / 1024:6.1f} KB  layers {len(doc['layers'])}")
    manifest["files"] = dict(sorted(manifest["files"].items()))
    manifest_path.write_text(json.dumps(manifest, indent=1, ensure_ascii=False) + "\n")


if __name__ == "__main__":
    main(sys.argv[1:])
