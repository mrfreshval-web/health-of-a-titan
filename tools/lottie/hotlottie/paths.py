"""The Titan of the day on the Path (docs/design/path-v1/visual-production.md §3–§4): 7 poses in 5 files.

    path-hold   — holds the vault: breathing loop 6 s, look_sky / look_earth / look_water (400 ms each),
                  to_one_hand (800 ms), the one-hand breathing loop, to_two_hands (800 ms)
    path-palms  — palms at the chest (rite Э1, Э4): offer (600 ms), release (300 ms)
    path-sky    — soars (front · air): loop 4 s, bob ±1.5 units, wings ±4°
    path-earth  — runs (side): cycle 733 ms (Agent 12; the package had 1000 ms), the second half is mirror_sides()
    path-water  — swims, breaststroke (side · water): cycle 2400 ms, glide · pull · recovery · kick

Contract (§3.2, as the heroes): 60 fps, 1024 × 1024, ≤ 150 KB, ≤ 40 layers, no expressions, no
floor; titan_line / titan_knockout / titan_inner; marker `poster` (the still of «Уменьшить
анимацию»). The front view is the mark's cut-out with open hands (heroes.MARK_HANDS).

Placement: the canvas holds the rig box with a margin of BOX_MARGIN units on every side (the held
vault's hands reach y ≈ −6, the swimmer x ≈ −7…102), so 1 rig unit is 1024 / 120 px. On the slice
(§1.2: 1 rig unit = 1.3 у.е., rig floor y 92 → horizon 262) the view is 156 × 156 у.е. at
(116; 129.4) instead of the 130-square at (129; 142.4).

The key poses are the table of §3.1 (yaml format of content/training/poses.yaml plus `nod`);
mirror_sides() is TitanPose.mirrorSides(): near and far limbs swap.
"""
from __future__ import annotations

import math

from . import figure, motion, rig, titan
from .heroes import MARK_HANDS

BOX_MARGIN = 10.0
S = figure.CANVAS / (100.0 + 2 * BOX_MARGIN)
FR = figure.Frame(S, BOX_MARGIN * S, BOX_MARGIN * S)
META = {"g": "hot-lottie 3 (tools/lottie)", "a": "Agent 17", "k": "Health of a Titan"}


def Y(root, torso, head, arm_n, arm_f, leg_n, leg_f, nod=0.0, spine=0.0):
    """A pose in the yaml format of §3.1 (`root` · `torso` · `head` · `nod` · arms · legs)."""
    p = rig.from_yaml(dict(root=root, torso=torso, head=head, arm_n=arm_n, arm_f=arm_f, leg_n=leg_n, leg_f=leg_f, spine=spine))
    p["nod"] = float(nod)
    return p


def mirror_sides(p):
    """TitanPose.mirrorSides(): the near limbs take the far ones' angles and back."""
    q = dict(p)
    for a, b in (("uaN", "uaF"), ("faN", "faF"), ("thN", "thF"), ("shN", "shF"), ("ftN", "ftF"), ("armLenN", "armLenF"),
                 ("uaLenN", "uaLenF")):
        q[a], q[b] = p.get(b, 1.0 if a in rig.ONE else 0.0), p.get(a, 1.0 if a in rig.ONE else 0.0)
    return q


def _with(p, **kw):
    q = dict(p)
    q.update({k: float(v) for k, v in kw.items()})
    return q


def _arms(p, arm_n=None, arm_f=None):
    q = dict(p)
    if arm_n:
        q["uaN"], q["faN"] = map(float, arm_n)
    if arm_f:
        q["uaF"], q["faF"] = map(float, arm_f)
    return q


def _legs(p, leg_n=None, leg_f=None):
    q = dict(p)
    if leg_n:
        q["thN"], q["shN"], q["ftN"] = map(float, leg_n)
    if leg_f:
        q["thF"], q["shF"], q["ftF"] = map(float, leg_f)
    return q


def _reach(p, side, target, front=True):
    """Two-bone IK of an arm to a point (the elbow stays on the side the table's angles bend it)."""
    j = rig.joints(p, front)
    a1, a2 = rig.ik2(j["shoulder" + side], target, rig.L["ua"], rig.L["fa"], j["elbow" + side])
    q = dict(p)
    q["ua" + side] = p["ua" + side] + rig.shortest(p["ua" + side], a1)
    q["fa" + side] = p["fa" + side] + rig.shortest(p["fa" + side], a2)
    return q


# --- key poses, §3.1 ----------------------------------------------------------------------------

LEGS_HOLD = dict(leg_n=[174, 180, 135], leg_f=[186, 180, 225])
HOLD = Y([50, 48.8], 0, 0, [40, 10], [320, 350], nod=4, **LEGS_HOLD)
HOLD_BREATH = _arms(_with(HOLD, ry=48.4), [42, 10], [318, 350])
# the right hand holds the vault where `hold` has it; the left palm comes to the heart (§3.1 check)
HEART = (51.5, 33.7)
ONE_HAND = _reach(_reach(Y([49, 48.8], 3, -4, [40, 10], [200, 80], nod=-3, **LEGS_HOLD), "N", rig.joints(HOLD, True)["handN"]), "F", HEART)
# the one-hand breath: as hold_breath (pelvis −0.4, the holding arm +2°), the right hand on the vault
ONE_HAND_BREATH = _arms(_with(ONE_HAND, ry=48.4), [ONE_HAND["uaN"] + 2, ONE_HAND["faN"]])
LOOK = {"sky": _with(HOLD, nod=10, head=0), "earth": _with(HOLD, nod=-6, head=0), "water": _with(HOLD, nod=-12, head=0)}
PALMS = Y([50, 48.8], 0, 0, [150, 300], [210, 60], nod=-4, **LEGS_HOLD)
# the package gives no pose for `release` («ладони раскрываются»): the palms part and open to the
# sides at chest height, the head comes up to level
RELEASE = Y([50, 48.8], 0, 0, [140, 70], [220, 290], nod=0, **LEGS_HOLD)
# before `offer`: the Titan stands, arms hanging (the stand of the heroes)
STAND = Y([50, 48.8], 0, 0, [172, 176], [188, 184], nod=0, **LEGS_HOLD)
SOAR = Y([50, 40], 0, 0, [80, 95], [280, 265], [178, 182, 170], [182, 178, 190], nod=6)

# v2 (Agent 12, PR #45): a bigger arm swing — the back elbow goes past the torso line (torso 8°),
# the front hand comes up to the chest; the package had [200; 115] · [150; 60]
RUN_CONTACT = Y([50, 49.5], 8, 4, [220, 118], [130, 45], [150, 165, 95], [205, 250, 200])
RUN_DOWN = _legs(_with(RUN_CONTACT, ry=51.5), [165, 195, 100], [190, 260, 220])
# the swing leg's heel a little higher than the package's 225 (Agent 12)
RUN_PASS = _legs(_with(RUN_CONTACT, ry=50), [180, 190, 95], [130, 236, 160])
RUN_UP = _legs(_with(RUN_CONTACT, ry=47.5), [200, 215, 135], [125, 175, 100])

SWIM_GLIDE = Y([42, 60], 92, 100, [92, 92], [94, 94], [268, 270, 300], [272, 272, 300])
# v2 of the stroke (Agent 12, PR #45): a consistent breaststroke. In profile the sideways parts
# (out, toes out) cannot show; what shows is the depth and the order.
# pull: both arms out and a little down, elbows high (just under the body line), the hands a
# forearm deep and ahead of the shoulder line; the head comes up to breathe
SWIM_PULL = _arms(_with(SWIM_GLIDE, head=85), [105, 175], [107, 177])
# the hands meet under the chin (IK), the legs start to recover
SWIM_INSWEEP = _legs(_arms(_with(SWIM_GLIDE, head=80), [150, 30], [152, 32]), [246, 290, 300], [248, 292, 300])
SWIM_INSWEEP = _reach(_reach(SWIM_INSWEEP, "N", (76.0, 67.0), front=False), "F", (76.5, 67.5), front=False)
# recovery (was `kick`): the hands shoot forward near the surface; the hips flex a little, the
# knees drop moderately, the heels come towards the buttocks at the body line (never above it)
SWIM_RECOVERY = _legs(_arms(_with(SWIM_GLIDE, head=95), [96, 110], [98, 112]), [232, 308, 262], [235, 310, 265])
# kick: the legs drive back (and out), the feet flexed; then they close into the glide
SWIM_KICK = _legs(SWIM_GLIDE, [256, 266, 180], [260, 268, 184])

POSES = {
    "hold": HOLD, "hold_breath": HOLD_BREATH, "one_hand": ONE_HAND, "look_sky": LOOK["sky"], "look_earth": LOOK["earth"],
    "look_water": LOOK["water"], "palms": PALMS, "soar": SOAR, "run_contact": RUN_CONTACT, "run_down": RUN_DOWN,
    "run_pass": RUN_PASS, "run_up": RUN_UP, "swim_glide": SWIM_GLIDE, "swim_pull": SWIM_PULL, "swim_recovery": SWIM_RECOVERY, "swim_kick": SWIM_KICK,
}


# --- timelines ----------------------------------------------------------------------------------

def _section(poses, steps, start, contacts=(), extra=None, loop=False):
    sc = motion.Script(poses, steps, start, contacts, {}, extra, front=True, loop=loop)
    return sc.frames()


def _breath_steps(a, b, dur=6.0):
    """EaseBreath: a sine over the whole period (out and back, 3 s each)."""
    return [("move", b, dur / 2, "sine"), ("move", a, dur / 2, "sine")]


class Timeline:
    """Sections played back to back; a marker at each section's first frame. A section may start
    from another pose than the last one ended in (the player plays one section at a time): the
    jump is a cut for the key fitter."""

    def __init__(self):
        self.frames, self.markers, self.cuts, self.breaks = [], [], [], []

    def add(self, frames, markers=(), cut=False, share=False):
        """share: the section starts on the last frame of the previous one (same pose)."""
        if share and self.frames:
            frames = frames[1:]
            f0 = len(self.frames) - 1
        else:
            if cut and self.frames:
                self.cuts.append(len(self.frames) - 1)
            f0 = len(self.frames)
        self.breaks += [f0, f0 + len(frames) - (0 if share else 1)]
        for m in markers:
            self.markers.append((m, f0))
        self.frames += frames
        return f0

    def mark(self, name, frame):
        self.markers.append((name, frame))


def _build(name, tl, front, hands, breaks_extra=(), loop=False):
    if not loop:
        # Lottie shows frames ip…op − 1: a file of sections keeps its last pose one frame more
        tl.frames.append(dict(tl.frames[-1]))
    n = len(tl.frames) - 1
    breaks = sorted({0, n} | set(tl.breaks) | {c for c in tl.cuts} | {c + 1 for c in tl.cuts} | set(breaks_extra))
    b = figure.Builder(name, n, breaks, FR, front)
    b.cuts = tuple(tl.cuts)
    titan.build_titan(b, tl.frames, front, hands=hands)
    mk = sorted(({"tm": int(f), "cm": m, "dr": 0} for m, f in tl.markers), key=lambda m: (m["tm"], m["cm"]))
    return b.json(mk, dict(META, d=name))


def path_hold():
    """Holds the vault (§3.1 hold, hold_breath, look_*, one_hand; §4.7 pause). Sections:
    hold_loop_start…hold_loop_end the breath (6 s, sine; poster = its first frame, `hold`);
    look_<tier> 400 ms hold → look (EaseStandard; back = the same section reversed);
    to_one_hand 800 ms, one_hand_loop_start…one_hand_loop_end its breath, to_two_hands 800 ms."""
    tl = Timeline()
    P = dict(POSES, one_hand_breath=ONE_HAND_BREATH)
    tl.add(_section(P, _breath_steps("hold", "hold_breath"), "hold", loop=True), ["poster", "hold_loop_start"])
    tl.mark("hold_loop_end", len(tl.frames) - 1)
    for tier in ("sky", "earth", "water"):
        tl.add(_section(P, [("move", f"look_{tier}", 0.4, "standard")], "hold"), [f"look_{tier}"], share=tier == "sky", cut=tier != "sky")
    # the right hand stays planted on the vault through the whole one-hand part
    planted = [("armN", "hand", rig.joints(HOLD, True)["handN"])]
    tl.add(_section(P, [("move", "one_hand", 0.8, "standard")], "hold", planted), ["to_one_hand"], cut=True)
    tl.add(_section(P, _breath_steps("one_hand", "one_hand_breath"), "one_hand", planted, loop=True), ["one_hand_loop_start"], share=True)
    tl.mark("one_hand_loop_end", len(tl.frames) - 1)
    tl.add(_section(P, [("move", "hold", 0.8, "standard")], "one_hand", planted), ["to_two_hands"], share=True)
    return _build("path-hold", tl, True, MARK_HANDS)


def path_palms():
    """Palms at the chest (§3.1 palms; rite Э1, Э4 §4.1 K2): offer 600 ms stand → palms
    (EaseStandard), poster = palms, release 300 ms palms → open hands (EaseStandard)."""
    tl = Timeline()
    P = {"stand": STAND, "palms": PALMS, "release": RELEASE}
    tl.add(_section(P, [("move", "palms", 0.6, "standard")], "stand"), ["offer"])
    end = len(tl.frames) - 1
    tl.mark("poster", end)
    tl.add(_section(P, [("move", "release", 0.3, "standard")], "palms"), ["release"], share=True)
    return _build("path-palms", tl, True, MARK_HANDS)


def path_sky():
    """Soars (§3.1 soar): loop 4 s — the body bobs ±1.5 units, the wings beat ±4°; the beat leads
    the bob by a quarter (the wings press down, the body rises). Poster = the soar pose."""
    period = 4.0

    def extra(t, p, seg):
        w = 2 * math.pi * t / period
        beat = 4.0 * math.sin(w)               # + = the wings press down
        p["ry"] = SOAR["ry"] - 1.5 * math.sin(w - math.pi / 2) - 1.5 * 0
        p["uaN"] = SOAR["uaN"] + beat
        p["faN"] = SOAR["faN"] + beat * 1.25    # the hands follow through a little more
        p["uaF"] = SOAR["uaF"] - beat
        p["faF"] = SOAR["faF"] - beat * 1.25
    tl = Timeline()
    tl.add(_section({"soar": SOAR}, [("hold", period)], "soar", extra=extra, loop=True), ["poster"])
    open_hands = {"N": ("open", 0.0), "F": ("open", 0.0)}   # the hands go on in the wings' line
    q = int(period * motion.FPS / 4)
    return _build("path-sky", tl, True, open_hands, breaks_extra=[q, 2 * q, 3 * q], loop=True)


def _cycle(keys, period, ease_arms=True):
    """A cyclic run through equally spaced key poses: Catmull-Rom on every channel (no stop at the
    keys). Arms are only given at contact (§3.1): they swing contact → mirrored contact over each
    half, EaseBreath-like (sine), instead of jumping in the last eighth."""
    n = int(round(period * motion.FPS))
    m = len(keys)
    out = []
    arms = ("uaN", "faN", "uaF", "faF")
    unwrap = [dict(k) for k in keys]
    for ch in rig.ANGLES:
        for i in range(1, m):
            unwrap[i][ch] = unwrap[i - 1][ch] + rig.shortest(unwrap[i - 1][ch], keys[i][ch])
    for f in range(n + 1):
        x = (f / n) * m
        i = int(math.floor(x)) % m
        u = x - math.floor(x)
        p = {}
        for ch in rig.CHANNELS:
            d = 1.0 if ch in rig.ONE else 0.0
            v = [unwrap[(i + k) % m].get(ch, d) for k in (-1, 0, 1, 2)]
            # keep the wrap continuous for angles
            if ch in rig.ANGLES:
                for k in range(1, 4):
                    v[k] = v[k - 1] + rig.shortest(v[k - 1], v[k])
            p[ch] = 0.5 * ((2 * v[1]) + (-v[0] + v[2]) * u + (2 * v[0] - 5 * v[1] + 4 * v[2] - v[3]) * u * u
                           + (-v[0] + 3 * v[1] - 3 * v[2] + v[3]) * u ** 3)
        if ease_arms:
            half = m // 2
            h = (f / n) * 2
            k = motion.ease("sine", h - math.floor(h))
            a, b = (keys[0], keys[half]) if h < 1 else (keys[half], keys[0])
            for ch in arms:
                p[ch] = a[ch] + rig.shortest(a[ch], b[ch]) * k
        out.append(p)
    out[n] = dict(out[0])
    return out


RUN_CYCLE = 44 / 60   # 733 ms: two steps, 164 steps/min (Agent 12: 160–170; the package had 1000 ms)


def path_earth():
    """Runs (§3.1 run_*): cycle 733 ms, 8 keys 92 ms apart — contact, down, pass, up, then the
    same with the sides mirrored. Poster = contact."""
    keys = [RUN_CONTACT, RUN_DOWN, RUN_PASS, RUN_UP]
    keys += [mirror_sides(k) for k in keys]
    tl = Timeline()
    frames = _cycle(keys, RUN_CYCLE)
    tl.add(frames, ["poster"])
    hands = {"N": ("fist", 0.0), "F": ("fist", 0.0)}
    return _build("path-earth", tl, False, hands, breaks_extra=[round(i * 44 / 8) for i in range(1, 8)], loop=True)


def path_water():
    """Swims, breaststroke (§3.1 swim_*, Agent 12's review of PR #45): cycle 2400 ms — out-sweep to
    `swim_pull` 450 ms (EaseStandard), the hands in under the chin 300 ms while the legs start to
    recover, the hands shoot forward into `swim_recovery` 300 ms, the kick back and out 250 ms
    (EaseDecel: a whip), the legs close into the glide 300 ms, glide 800 ms. Poster = glide."""
    P = {"swim_glide": SWIM_GLIDE, "swim_pull": SWIM_PULL, "_insweep": SWIM_INSWEEP, "swim_recovery": SWIM_RECOVERY,
         "swim_kick": SWIM_KICK}
    steps = [("move", "swim_pull", 0.45, "standard"), ("move", "_insweep", 0.3, "inout"), ("move", "swim_recovery", 0.3, "inout"),
             ("move", "swim_kick", 0.25, "decel"), ("move", "swim_glide", 0.3, "standard"), ("hold", 0.8)]
    sc = motion.Script(P, steps, "swim_glide", (), {}, None, front=False, loop=True)
    tl = Timeline()
    tl.add(sc.frames(), ["poster"])
    hands = {"N": ("open", 0.0), "F": ("open", 0.0)}
    return _build("path-water", tl, False, hands, breaks_extra=[27, 45, 63, 78, 96], loop=True)


PATHS = {
    "path-hold": path_hold,
    "path-palms": path_palms,
    "path-sky": path_sky,
    "path-earth": path_earth,
    "path-water": path_water,
}
