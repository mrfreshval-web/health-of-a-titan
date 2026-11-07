"""Timing and staging of the top-20 exercises (docs/design/training/animator-brief.md, Part 1).

Each entry: start pose, steps (move / hold / mark), contacts that stay planted, springs (overlap
and follow-through), props, the poster pose («Удалить анимацию») and hold markers for stretches.
Key poses come from content/training/poses.yaml; `poses` here only adds in-between poses (a
lifted foot for a step, breathing) derived from them. Names starting with «_» get no marker.

Timing follows `tempo` / the brief's cycle column; the player stretches the cycle to the user's
tempo, so the file carries the default one.
"""
from __future__ import annotations

import math

from . import props as pr
from .rig import side_offsets

Q = "3q"                 # three-quarter view (threeq.py): the mark's own anatomy, turned


def q_grip(x, y, width=1.6):
    """Both hands on a bar in the 3/4 view, a grip wider than the shoulders (the pull-up of the
    brief): the arms open in a V and the face stays clear between them."""
    return [("armN", "hand", (x + width * side_offsets(Q, "N")[0], y)), ("armF", "hand", (x + width * side_offsets(Q, "F")[0], y))]


FEET = [("legN", "ankle"), ("legF", "ankle")]
HANDS = [("armN", "hand"), ("armF", "hand")]
KNEES = [("legN", "knee"), ("legF", "knee")]
HEAD = {"head": (3.0, 0.6)}
FLAT = {"N": ("open", ("world", 90.0)), "F": ("open", ("world", 90.0))}      # palms on the floor, fingers forward
WALL = {"N": ("open", ("world", 0.0)), "F": ("open", ("world", 0.0))}       # palms on a wall, fingers up
FISTS = {"N": "fist", "F": "fist"}


def breathe(period=5.0, chest=1.0, belly=0.0):
    """Calm breathing in holds (brief: «едва заметное дыхание грудной клеткой 4–6 с»): only in holds
    of whole breaths, so the loop has no seam."""
    def extra(t, p, seg):
        t0, t1, a, b_ = seg
        if a != b_ or (t1 - t0) < period * 0.99:
            return
        x = ((t - t0) % period) / period
        # inhale 40 %, exhale 60 %
        w = 0.5 - 0.5 * math.cos(math.pi * x / 0.4) if x < 0.4 else 0.5 + 0.5 * math.cos(math.pi * (x - 0.4) / 0.6)
        p["breath"] = p.get("breath", 0.0) + chest * w
        p["belly"] = p.get("belly", 0.0) + belly * w
    return extra


def hold(start, stretch, enter=1.5, breaths=2, period=5.0, exit_=1.5, rest=0.5, ease="soft", via=None):
    """Stretch / hold: enter → hold loop (whole breaths) → exit → short rest. Markers per brief:
    enter, hold_loop_start, hold_loop_end, exit (+ the pose keys)."""
    go = [("move", via, enter / 2, "in"), ("move", stretch, enter / 2, "out")] if via else [("move", stretch, enter, ease)]
    back = [("move", via, exit_ / 2, "in"), ("move", start, exit_ / 2, "out")] if via else [("move", start, exit_, ease)]
    return [("mark", "enter")] + go + [("mark", "hold_loop_start"), ("hold", breaths * period), ("mark", "hold_loop_end"),
                                       ("mark", "exit")] + back + [("hold", rest)]


def arm_circles(period=2.0, up=64.0, down=62.0):
    """Big arm circles seen from the front: the hands rise over the head and sink below the
    shoulders on one smooth sine; the arm shortens a little on the forward half (foreshortening),
    so the eye reads a circle, not a flap."""
    def extra(t, p, seg):
        ph = 2 * math.pi * t / period
        s = math.sin(ph)
        a = 90.0 - (up * s if s > 0 else down * s)
        # the forearm trails the upper arm a little (overlap)
        lag = 2 * math.pi * (t - 0.06) / period
        s2 = math.sin(lag)
        f = 90.0 - (up * s2 if s2 > 0 else down * s2)
        p["uaN"], p["faN"] = a, f - 3.0
        p["uaF"], p["faF"] = 360.0 - a, 360.0 - f + 3.0
        ln = 0.95 + 0.05 * math.cos(ph)
        p["armLenN"] = p["armLenF"] = ln
        p["breath"] = 0.25 + 0.25 * s
    return extra


def both(*fns):
    def extra(t, p, seg):
        for f in fns:
            f(t, p, seg)
    return extra


EXERCISES = {
    # 1 · breath: 4 s in, 6 s out; the belly rises, the hands ride on it
    "ex-breath-long-exhale": dict(
        start="exhale", kind="time", poster="inhale",
        poses={"inhale": {"base": "inhale", "belly": 1.0, "breath": 0.6}},
        steps=[("move", "inhale", 4.0, "sine"), ("move", "exhale", 6.0, "sine")],
        contacts=FEET, props_behind=[pr.mat(12, 96)], belly_scale=0.12,
    ),
    # 2 · cat-cow, tempo 3-1-3-1
    "ex-cat-cow": dict(
        start="neutral", kind="reps", poster="cat",
        poses={"cow": {"base": "cow", "breath": 1.0}},
        steps=[("move", "cat", 1.5, "inout"), ("hold", 1.0), ("move", "cow", 3.0, "inout"), ("hold", 1.0), ("move", "neutral", 1.5, "inout")],
        contacts=HANDS + KNEES, springs=HEAD, props_behind=[pr.mat(10, 82)], hands=FLAT,
    ),
    # 3 · chest stretch in a doorway: step through, the hand stays on the jamb
    # v2: front view — the forearm up the jamb reads; stepping through takes the upper arm back
    "ex-stretch-chest-doorway": dict(
        start="set", kind="time", poster="stretch", front=True,
        poses={"stretch": {"base": "stretch", "armLenN": 0.88}},
        steps=hold("set", "stretch", enter=1.6, exit_=1.4),
        contacts=FEET, springs=HEAD, props_behind=[pr.door(68.2, top=2.0, depth=2.2)], frame_pts=[(70, 2), (30, 92)], extra=breathe(),
        hands={"N": ("open", ("world", 0.0))},
    ),
    # 4 · world's greatest stretch, tempo 2-2-2-0
    "ex-worlds-greatest": dict(
        start="lunge", kind="reps", poster="reach_up",
        steps=[("move", "elbow_down", 2.0, "inout"), ("move", "reach_up", 2.0, "inout"), ("move", "lunge", 2.0, "inout")],
        contacts=FEET + [("armF", "hand")], springs=HEAD, hands={"F": ("open", ("world", 90.0))},
    ),
    # 5 · pigeon: settle onto the forearms, breathe
    "ex-stretch-pigeon": dict(
        start="set", kind="time", poster="fold",
        steps=hold("set", "fold", enter=2.0, exit_=1.6),
        springs=HEAD, props_behind=[pr.mat(8, 92)], extra=breathe(), hands=FLAT,
    ),
    # 6 · knee to wall, tempo 1-1-1-0: heel stays down, knee over the second toe
    "ex-ankle-knee-wall": dict(
        start="start", kind="reps", poster="touch",
        steps=[("move", "touch", 1.0, "inout"), ("hold", 0.9), ("move", "start", 1.0, "inout"), ("hold", 0.1)],
        contacts=[("legN", "ankle"), ("legF", "knee")] + HANDS, springs=HEAD,
        props_behind=[pr.wall(79.5, top=16.0), pr.mat(20, 60)], frame_pts=[(79.5, 16)], hands=WALL,
    ),
    # 7 · arm circles (front view), 2 s per circle
    "ex-arm-circles": dict(
        start="side", kind="reps", poster="side", front=True,
        steps=[("hold", 0.5), ("mark", "up"), ("hold", 1.5)],
        contacts=FEET, extra=arm_circles(),
        frame_pts=[(50, 4)],
    ),
    # 8 · hip flexor stretch in half-kneeling: pelvis tucks and glides forward
    "ex-stretch-hip-flexor": dict(
        start="start", kind="time", poster="stretch",
        steps=hold("start", "stretch"),
        contacts=[("legF", "ankle"), ("legN", "knee")], springs=HEAD, props_behind=[pr.mat(14, 80)], extra=breathe(),
    ),
    # 9 · lat stretch at the wall: hands stay, hips go back
    "ex-stretch-lat-wall": dict(
        start="start", kind="time", poster="stretch",
        steps=hold("start", "stretch"),
        contacts=FEET + HANDS, springs=HEAD, props_behind=[pr.wall(81.6, top=16.0)], frame_pts=[(81.6, 16)], extra=breathe(), hands=WALL,
    ),
    # 10 · deep squat hold
    "ex-deep-squat-hold": dict(
        start="stand", kind="time", poster="hold",
        steps=hold("stand", "hold", enter=2.0, exit_=1.6),
        contacts=FEET, springs=HEAD, extra=breathe(),
    ),
    # 11 · leg swings: a pendulum, the torso stays still, the hand on the wall
    "ex-leg-swings": dict(
        start="back", kind="reps", poster="front",
        steps=[("move", "front", 0.8, "sine"), ("move", "back", 0.8, "sine")],
        contacts=[("legF", "ankle"), ("armN", "hand", (76.9, 26.3))], springs={"shN": (3.2, 0.55), "ftN": (4.0, 0.5), "head": (3.0, 0.7)},
        props_behind=[pr.wall(78.3, top=16.0)], frame_pts=[(78.3, 16)], hands={"N": ("open", ("world", 0.0))},
    ),
    # 12 · child's pose: hips to the heels, arms long, breathe into the back
    "ex-stretch-child": dict(
        start="kneel", kind="time", poster="rest",
        steps=hold("kneel", "rest", enter=2.0, exit_=1.6),
        contacts=KNEES, springs=HEAD, props_behind=[pr.mat(6, 80)], extra=breathe(), hands=FLAT,
    ),
    # 13 · standing quad stretch: the far hand on the wall, the near hand brings the heel up
    "ex-stretch-quad": dict(
        start="start", kind="time", poster="stretch",
        steps=hold("start", "stretch"),
        contacts=[("legF", "ankle"), ("armF", "hand")], springs=HEAD, props_behind=[pr.wall(78.4, top=16.0)],
        frame_pts=[(78.4, 16)], extra=breathe(), hands={"N": "fist", "F": ("open", ("world", 0.0))}, fold="N",
    ),
    # 14 · wrist prep: weight forward over the palms, then back to the heels
    "ex-wrist-prep": dict(
        start="center", kind="reps", poster="forward",
        steps=[("move", "forward", 0.8, "inout"), ("hold", 0.2), ("move", "back", 1.2, "inout"), ("hold", 0.2), ("move", "center", 0.6, "inout")],
        contacts=HANDS + KNEES, springs=HEAD, props_behind=[pr.mat(8, 82)], hands=FLAT,
    ),
    # 15 · scapular push-ups, tempo 1-1-1-1: arms straight, only the shoulder blades move
    "ex-scap-pushup": dict(
        start="retract", kind="reps", poster="protract",
        # v2: bigger and readable — retracted, the chest sinks between the shoulder blades (the arms
        # look longer); protracted, the upper back rounds up and the arms look shorter
        poses={"retract": {"base": "retract", "ry": 70.2, "armLenN": 1.055, "armLenF": 1.055, "spine": -0.06, "head": 72.0},
               "protract": {"base": "protract", "ry": 67.9, "armLenN": 0.975, "armLenF": 0.975, "spine": 0.34, "head": 82.0}},
        steps=[("move", "protract", 1.0, "inout"), ("hold", 1.0), ("move", "retract", 1.0, "inout"), ("hold", 1.0)],
        contacts=HANDS + FEET, springs=HEAD, hands=FLAT,
    ),
    # 16 · calf stretch at the wall: hips to the wall, rear heel down
    "ex-stretch-calf": dict(
        start="set", kind="time", poster="stretch",
        steps=hold("set", "stretch"),
        contacts=FEET + HANDS, springs=HEAD, props_behind=[pr.wall(79.4, top=10.0)], frame_pts=[(79.4, 10)], extra=breathe(), hands=WALL,
    ),
    # 17 · hamstring stretch lying with a strap
    "ex-stretch-hamstring": dict(
        start="start", kind="time", poster="stretch",
        steps=hold("start", "stretch", enter=2.0, exit_=1.6),
        contacts=[("legF", "ankle")], springs=HEAD, props_behind=[pr.mat(12, 98)], props_front=[pr.strap("N")], extra=breathe(), hands=FISTS,
    ),
    # 18 · band dislocates, tempo 2-0-2-0: one continuous arc over the head and back
    "ex-band-dislocate": dict(
        start="front", kind="reps", poster="overhead",
        steps=[("move", "overhead", 1.0, "in"), ("move", "behind", 1.0, "out"), ("move", "overhead", 1.0, "in"), ("move", "front", 1.0, "out")],
        contacts=FEET, springs=HEAD, props_held=[pr.stick("N")], hands=FISTS,
    ),
    # 19 · pull-ups: up 1 s, a short pause over the bar, 2 s down, no swing
    "ex-pullup": dict(
        start="hang", kind="reps", poster="top", floor=False, front=Q,
        steps=[("move", "mid", 0.55, "in"), ("move", "top", 0.65, "out"), ("hold", 0.5), ("move", "hang", 2.0, "inout"), ("hold", 0.3)],
        contacts=q_grip(50.0, 8.0),
        springs={"head": (3.0, 0.7), "shN": (2.2, 0.8), "shF": (2.2, 0.8)},
        props_behind=[pr.bar(8.0, 28, 72, grip_x=50.0)], frame_pts=[(28, 6), (72, 6)], hands={"N": "fist", "F": "fist"},
    ),
    # 20 · goblet squat, tempo 3-0-1-0
    "ex-goblet-squat": dict(
        start="stand", kind="reps", poster="bottom", front=Q, arms_over="F",
        steps=[("move", "bottom", 3.0, "inout"), ("hold", 0.15), ("move", "stand", 1.0, "inout"), ("hold", 0.35)],
        contacts=FEET, springs=HEAD, props_front=[pr.kettlebell("N")], hands={"N": "fist", "F": "fist"},
    ),
}
