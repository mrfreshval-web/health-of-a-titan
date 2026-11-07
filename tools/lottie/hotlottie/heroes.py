"""Heroic moments (brief Part 2): seal, level up, record, first skill, test passed.

1024 × 1024, 60 fps, played once; the last frame is the still for «Удалить анимацию» (marker
`poster`). The columns are the mark's columns (HotMarkGeometry, mark v2) at scale 0.5, the ring
is the mark's ring. Layer contract (brief): titan_line / titan_knockout / titan_inner,
columns_line, columns_knockout, ring (and ring_next for the level colour), flash.
Ethics (Agent 10): dignity, no fist in the air, no roar — the Titan straightens and holds.
"""
from __future__ import annotations

import math
import re
from pathlib import Path

from . import figure, titan, motion, rig
from . import lottie as lt
from . import props as pr

GEOM = Path(__file__).resolve().parents[3] / "android/core/ui/src/main/kotlin/com/healthofatitan/app/core/ui/component/HotMarkGeometry.kt"
RING_C = (1024.2 / 2, 1023.0 / 2)
RING_R = 940.0 / 2
S = 8.3                       # rig units → px in the hero canvas (Titan of the mark: ~84 units = 700 px)
FLOOR_PX = 893.0              # column bases (1786 / 2)
FR = figure.Frame(S, 512.0 - 50.0 * S, FLOOR_PX - rig.FLOOR * S)
WHITE = (1.0, 1.0, 1.0)


def _mark_path(name):
    src = GEOM.read_text()
    m = re.search(rf"const val {name} =\s*((?:\"[^\"]*\"\s*\+?\s*)+)", src)
    return "".join(re.findall(r"\"([^\"]*)\"", m.group(1)))


def svg_to_lottie(d, scale=0.5):
    """Absolute M/L/H/V/C/Q/Z path → list of Lottie closed paths (scaled)."""
    toks = re.findall(r"[MLHVCQZ]|-?\d+(?:\.\d+)?", d)
    paths, v, ii, oo = [], [], [], []
    cur = (0.0, 0.0)
    i = 0
    cmd = None

    def num():
        nonlocal i
        x = float(toks[i]); i += 1
        return x

    def close():
        nonlocal v, ii, oo
        if v:
            if len(v) > 1 and abs(v[-1][0] - v[0][0]) < 1e-6 and abs(v[-1][1] - v[0][1]) < 1e-6:
                ii[0] = ii[-1]
                v.pop(); ii.pop(); oo.pop()
            paths.append({"c": True, "v": [[x * scale, y * scale] for x, y in v], "i": [[x * scale, y * scale] for x, y in ii],
                          "o": [[x * scale, y * scale] for x, y in oo]})
        v, ii, oo = [], [], []

    while i < len(toks):
        t = toks[i]
        if re.match(r"[MLHVCQZ]", t):
            cmd = t
            i += 1
            if cmd == "Z":
                close()
                continue
        if cmd == "M":
            close()
            cur = (num(), num()); v.append(cur); ii.append((0, 0)); oo.append((0, 0)); cmd = "L"
        elif cmd == "L":
            cur = (num(), num()); v.append(cur); ii.append((0, 0)); oo.append((0, 0))
        elif cmd == "H":
            cur = (num(), cur[1]); v.append(cur); ii.append((0, 0)); oo.append((0, 0))
        elif cmd == "V":
            cur = (cur[0], num()); v.append(cur); ii.append((0, 0)); oo.append((0, 0))
        elif cmd == "C":
            c1 = (num(), num()); c2 = (num(), num()); p = (num(), num())
            oo[-1] = (c1[0] - cur[0], c1[1] - cur[1])
            v.append(p); ii.append((c2[0] - p[0], c2[1] - p[1])); oo.append((0, 0)); cur = p
        elif cmd == "Q":
            q = (num(), num()); p = (num(), num())
            c1 = (cur[0] + 2 / 3 * (q[0] - cur[0]), cur[1] + 2 / 3 * (q[1] - cur[1]))
            c2 = (p[0] + 2 / 3 * (q[0] - p[0]), p[1] + 2 / 3 * (q[1] - p[1]))
            oo[-1] = (c1[0] - cur[0], c1[1] - cur[1])
            v.append(p); ii.append((c2[0] - p[0], c2[1] - p[1])); oo.append((0, 0)); cur = p
    close()
    return paths


def _rnd(path):
    return {"c": path["c"], "v": [[round(a, 1), round(b, 1)] for a, b in path["v"]],
            "i": [[round(a, 1), round(b, 1)] for a, b in path["i"]], "o": [[round(a, 1), round(b, 1)] for a, b in path["o"]]}


def columns(b, n, spread=None, light_run=None):
    """Both columns: knockout, line, flutes; optional sideways spread (px keys) and a light run."""
    for side, name, sgn in (("left", "LEFT_COLUMN", -1), ("right", "RIGHT_COLUMN", 1)):
        paths = [_rnd(p) for p in svg_to_lottie(_mark_path(name))]
        xs = [p["v"][i][0] for p in paths for i in range(len(p["v"]))]
        cx = (min(xs) + max(xs)) / 2
        flutes = []
        for dx in (-9, -3, 3, 9):
            flutes.append({"c": False, "v": [[cx + dx, 262], [cx + dx * 1.04, 832]], "i": [[0, 0], [0, 0]], "o": [[0, 0], [0, 0]]})
        items = [lt.group("columns_flutes", [lt.shape_path(f, f"flute{i}") for i, f in enumerate(flutes)] +
                          [lt.stroke(figure.INNER, 2.2, "flute_stroke", opacity=45)]),
                 lt.group("columns_line", [lt.shape_path(p, f"outline{i}") for i, p in enumerate(paths)] +
                          [lt.stroke(figure.INNER, 4.0, "columns_stroke", opacity=90)])]
        if light_run:
            f0, f1 = light_run
            off = {"a": 1, "k": [{"t": f0, "s": [0], "o": {"x": [0.4], "y": [0]}, "i": {"x": [0.6], "y": [1]}}, {"t": f1, "s": [360]}]}
            op = {"a": 1, "k": [{"t": f0, "s": [0], "o": {"x": [0.3], "y": [0]}, "i": {"x": [0.7], "y": [1]}},
                                {"t": f0 + 8, "s": [100], "o": {"x": [0.3], "y": [0]}, "i": {"x": [0.7], "y": [1]}},
                                {"t": f1 - 8, "s": [100], "o": {"x": [0.3], "y": [0]}, "i": {"x": [0.7], "y": [1]}}, {"t": f1, "s": [0]}]}
            items.insert(0, lt.group("columns_light", [lt.shape_path(p, f"run{i}") for i, p in enumerate(paths)] +
                                     [lt.trim(0, 14, off), lt.stroke(WHITE, 7.0, "run_stroke")], lt.group_tr(o=op)))
        items.append(lt.group("columns_knockout", [lt.shape_path(p, f"fill{i}") for i, p in enumerate(paths)] + [lt.fill(figure.KNOCKOUT)]))
        p = lt.static([0, 0, 0])
        if spread:
            (f0, f1, px) = spread
            p = {"a": 1, "k": [{"t": f0, "s": [0, 0, 0], "o": {"x": 0.45, "y": 0}, "i": {"x": 0.2, "y": 1}}, {"t": f1, "s": [sgn * px, 0, 0]}]}
        b.add(f"columns_{side}", 4, lt.transform(p=p), items)


def ring(b, name="ring", trim_keys=None, width=5.0, opacity=100, width_keys=None, op_keys=None):
    el = {"ty": "el", "nm": "ring_circle", "p": lt.static([round(RING_C[0], 1), round(RING_C[1], 1)]), "s": lt.static([RING_R * 2, RING_R * 2]), "d": 1}
    items = [el]
    if trim_keys:
        items.append(lt.trim(lt.static(0), trim_keys))
    items.append(lt.stroke(figure.PROPS, width_keys or width, "ring_stroke", opacity=opacity))
    b.add(name, 4, lt.transform(o=op_keys) if op_keys else lt.transform(), [lt.group(name, items)])


def flash(b, f0, peak=40, dur=36, r0=60, r1=RING_R):
    """A soft light circle: grows from the centre and fades (the seal's «вспышка»)."""
    el = {"ty": "el", "nm": "flash_circle", "p": lt.static([round(RING_C[0], 1), round(RING_C[1], 1)]), "s": lt.static([2 * r1, 2 * r1]), "d": 1}
    k = lambda t, v, last=False: {"t": t, "s": v} if last else {"t": t, "s": v, "o": {"x": [0.2], "y": [0]}, "i": {"x": [0.4], "y": [1]}}
    sc = {"a": 1, "k": [{"t": f0, "s": [100 * r0 / r1] * 2, "o": {"x": 0.15, "y": 0}, "i": {"x": 0.35, "y": 1}}, {"t": f0 + dur, "s": [100, 100]}]}
    op = {"a": 1, "k": [k(f0, [0]), k(f0 + 6, [peak]), k(f0 + dur, [0], True)]}
    grad = {"ty": "gf", "nm": "flash_fill", "o": lt.static(100), "r": 1, "t": 2,
            "g": {"p": 3, "k": lt.static([0, 1, 1, 1, 0.55, 0.62, 0.92, 0.86, 1, 0.62, 0.92, 0.86, 0, 0.9, 0.55, 0.35, 1, 0])},
            "s": lt.static([round(RING_C[0], 1), round(RING_C[1], 1)]), "e": lt.static([round(RING_C[0] + r1, 1), round(RING_C[1], 1)]), "h": lt.static(0), "a": lt.static(0)}
    b.add("flash", 4, lt.transform(o=op), [lt.group("flash", [el, grad], lt.group_tr(p=RING_C, a=RING_C, s=sc))])


# --- poses (front view, rig units) -------------------------------------------------------------

def _P(**kw):
    base = dict(rx=50.0, ry=48.8, torso=0.0, head=0.0, uaN=172.0, faN=176.0, uaF=188.0, faF=184.0, thN=176.0, shN=180.0, ftN=135.0,
                thF=184.0, shF=180.0, ftF=225.0, spine=0.0, breath=0.0, belly=0.0, armLenN=1.0, armLenF=1.0, nod=0.0)
    base.update(kw)
    return base


def _hands_to(p, right, left):
    j = rig.joints(p, True)
    a1, a2 = rig.ik2(j["shoulderN"], right, rig.L["ua"], rig.L["fa"], (j["shoulderN"][0] + 8, j["shoulderN"][1] + 12))
    p["uaN"], p["faN"] = a1, a2
    a1, a2 = rig.ik2(j["shoulderF"], left, rig.L["ua"], rig.L["fa"], (j["shoulderF"][0] - 8, j["shoulderF"][1] + 12))
    p["uaF"], p["faF"] = a1, a2
    return p


def _u(px, py):
    return ((px - FR.ox) / FR.s, (py - FR.oy) / FR.s)


# the hands rest on the shafts (as in the mark): arms almost straight
HAND_R = _u(784.0, 300.0)
HAND_L = _u(1024 - 784.0, 300.0)


def mark_pose(**kw):
    return _hands_to(_P(**kw), HAND_R, HAND_L)


def stand(**kw):
    return _P(**kw)


def capital_hand(v, side):
    """Hands of the mark: hanging, they go on in the forearm's line; as the arm rises to the
    capital the palm turns flat onto it, fingers up (as drawn in the mark)."""
    a = v["fa" + side]["abs"]
    k = min(1.0, max(0.0, (abs(math.sin(math.radians(a))) - 0.55) / 0.35))
    k = k * k * (3 - 2 * k)
    return k * rig.shortest(a, 0.0)


MARK_HANDS = {"N": ("open", capital_hand), "F": ("open", capital_hand)}


def _figure(b, poses, steps, start, pulses=(), front=True, springs=None, extra=None, contacts=(), hands=None):
    sc = motion.Script(poses, steps, start, contacts, springs or {"head": (3.0, 0.65)}, extra, front=front, loop=False)
    frames = sc.frames()
    titan.build_titan(b, frames, front, hands=MARK_HANDS if hands is None and front else hands)
    return sc


def _doc(b, sc, name, extra_markers=()):
    mk = [{"tm": int(round(t * motion.FPS)), "cm": m, "dr": 0} for m, t in sc.markers]
    mk += [{"tm": int(t), "cm": m, "dr": 0} for m, t in extra_markers]
    mk.append({"tm": b.n - 1, "cm": "poster", "dr": 0})
    return b.json(sorted(mk, key=lambda m: m["tm"]), {"g": "hot-lottie 2 (tools/lottie)", "a": "Agent 17", "k": "Health of a Titan", "d": name})


def _k(t, v, ease=(0.33, 0.0, 0.67, 1.0), last=False):
    k = {"t": t, "s": v if isinstance(v, list) else [v]}
    if not last:
        k["o"] = {"x": [ease[0]], "y": [ease[1]]}
        k["i"] = {"x": [ease[2]], "y": [ease[3]]}
    return k


def hero_seal(lib):
    """H1 «Печать» 2.75 s: the Titan straightens into the mark, the ring closes, light runs over
    the figure and the columns, one soft flash."""
    n = 165
    b = figure.Builder("hero-seal", n, [0, 30, 66, 96, n], FR, True)
    ring(b, "ring", trim_keys={"a": 1, "k": [_k(18, 0, (0.5, 0, 0.2, 1)), _k(96, 100, last=True)]}, width=5.0)
    columns(b, n, light_run=(70, 132))
    poses = {"rest": stand(uaN=176, faN=178, uaF=184, faF=182, head=4.0), "mark": mark_pose()}
    sc = _figure(b, poses, [("move", "mark", 1.1, "inout"), ("mark", "seal"), ("hold", 1.65)], "rest", pulses=[(82, 9, 9.0, 34)])
    flash(b, 96, peak=38, dur=40)
    return _doc(b, sc, "hero-seal")


def hero_level_up(lib):
    """H2 Новый уровень 3.5 s: hands rise to the capitals, the columns part a little, the ring
    takes the colour of the new level (ring_next, recoloured by the app)."""
    n = 210
    b = figure.Builder("hero-level-up", n, [0, 40, 90, 150, n], FR, True)
    ring(b, "ring", width=5.0, op_keys={"a": 1, "k": [_k(90, 100), _k(150, 25, last=True)]})
    ring(b, "ring_next", trim_keys={"a": 1, "k": [_k(90, 0, (0.45, 0, 0.25, 1)), _k(160, 100, last=True)]}, width=6.0)
    columns(b, n, spread=(40, 100, 16.0))
    up_r, up_l = _u(784.0 + 16, 240.0), _u(1024 - 784.0 - 16, 240.0)

    def extra(t, p, seg):
        # hands follow the parting columns
        f = t * motion.FPS
        k = min(max((f - 40) / 60.0, 0.0), 1.0)
        k = k * k * (3 - 2 * k)
        if seg[3] == "rise" or seg[2] == "rise":
            pass
        if f >= 40:
            r = (HAND_R[0] + (up_r[0] - HAND_R[0]) * k, HAND_R[1] + (up_r[1] - HAND_R[1]) * k)
            l_ = (HAND_L[0] + (up_l[0] - HAND_L[0]) * k, HAND_L[1] + (up_l[1] - HAND_L[1]) * k)
            _hands_to(p, r, l_)
    poses = {"mark": mark_pose(), "rise": mark_pose(head=-4.0, ry=48.3, breath=1.0)}
    sc = _figure(b, poses, [("hold", 40 / 60), ("move", "rise", 1.0, "inout"), ("mark", "level"), ("hold", 2.5 - 40 / 60 + 0.0)], "mark",
                 pulses=[(110, 10, 8.0, 30)], extra=extra)
    flash(b, 112, peak=26, dur=44)
    return _doc(b, sc, "hero-level-up")


def hero_record(lib):
    """H3 Личный рекорд 1.75 s, over the player: one impulse of light around the ring and a short
    swell of the ring — no figure (the player's Titan stays underneath), nothing over the numbers."""
    n = 105
    b = figure.Builder("hero-record", n, [0, n], FR, True)
    ring(b, "ring", width_keys={"a": 1, "k": [_k(0, 5.0), _k(24, 11.0), _k(70, 5.0, last=True)]},
         op_keys={"a": 1, "k": [_k(0, 0), _k(10, 100), _k(80, 100), _k(104, 0, last=True)]})
    # the impulse: a short bright arc running once around the ring
    el = {"ty": "el", "nm": "pulse_circle", "p": lt.static([round(RING_C[0], 1), round(RING_C[1], 1)]), "s": lt.static([RING_R * 2, RING_R * 2]), "d": 1}
    off = {"a": 1, "k": [_k(4, 0, (0.3, 0, 0.5, 1)), _k(84, 360, last=True)]}
    op = {"a": 1, "k": [_k(4, 0), _k(12, 100), _k(70, 100), _k(90, 0, last=True)]}
    b.add("ring_pulse", 4, lt.transform(o=op), [lt.group("ring", [el, lt.trim(0, 12, off), lt.stroke(WHITE, 12.0, "pulse_stroke")])])
    flash(b, 8, peak=22, dur=50, r0=200)
    b.layers.reverse()  # flash under the ring
    return _doc(b, _Fake(), "hero-record")


class _Fake:
    markers = [("impulse", 4 / 60)]


def hero_first_skill(lib):
    """H4 Первый навык 3 s: a muscle-up, close up (side view). Keys by hand: the pull (chest to the
    bar), the turn (the wrists roll over, the chest goes over the bar), the dip bottom over the bar,
    the press to support — then a pause on top with the light."""
    n = 180
    bar = (50.0, 40.0)
    legs = dict(thN=178.0, shN=182.0, ftN=160.0, thF=180.0, shF=184.0, ftF=162.0)
    P = lambda **kw: dict(dict(rx=50.0, ry=93.0, torso=0.0, head=0.0, uaN=0.0, faN=0.0, uaF=2.0, faF=2.0, spine=0.0, breath=0.0,
                               belly=0.0, armLenN=1.0, armLenF=1.0, nod=0.0, **legs), **kw)
    # driver arm angles only pick the elbow side for the IK (hands stay on the bar)
    poses = {
        "hang": P(rx=50.0, ry=93.0),
        "pull": P(rx=49.0, ry=68.0, torso=350.0, head=356.0, uaN=170.0, faN=20.0, uaF=170.0, faF=20.0, thN=160.0, shN=176.0, thF=162.0,
                  shF=178.0),
        "turn": P(rx=38.4, ry=59.0, torso=36.0, head=48.0, spine=0.12, uaN=280.0, faN=120.0, uaF=280.0, faF=120.0, thN=150.0, shN=168.0,
                  thF=152.0, shF=170.0),
        "_over": P(rx=44.0, ry=55.6, torso=20.0, head=26.0, uaN=230.0, faN=40.0, uaF=230.0, faF=40.0, thN=168.0, shN=176.0, thF=170.0,
                   shF=178.0),
        "top": P(rx=50.1, ry=40.0, torso=2.0, head=0.0, uaN=185.0, faN=180.0, uaF=185.0, faF=180.0, breath=1.0),
    }
    steps = [("hold", 0.3), ("move", "pull", 0.5, "in"), ("move", "turn", 0.25, "linear"), ("move", "_over", 0.25, "out"),
             ("move", "top", 0.45, "inout"), ("mark", "pause"), ("hold", 1.25)]
    contacts = [("armN", "hand", bar), ("armF", "hand", bar)]
    probe = motion.Script(poses, steps, "hang", contacts, {}, None, front=False, loop=False).frames()
    # close up: the frame holds the upper body and the bar; the legs may leave it while hanging
    pts = [(26, 38), (74, 38)]
    for p in probe[60:]:
        j = rig.joints(p, False)
        pts += [j[k] for k in ("head", "shoulder", "hip", "handN", "elbowN", "kneeN")]
    fr = figure.fit_frame(pts, smax=11.5, floor_max=4000, r=420)
    b = figure.Builder("hero-first-skill", n, [0, 18, 48, 63, 78, 105, n], fr, False)
    pr.bar(bar[1], 26, 74, grip_x=bar[0])(b, None, None)
    ring(b, "ring", width=5.0, op_keys={"a": 1, "k": [_k(100, 0), _k(120, 70, last=True)]})
    sc = _figure(b, poses, steps, "hang", front=False, contacts=contacts,
                 springs={"head": (3.0, 0.65), "shN": (2.4, 0.75), "shF": (2.4, 0.75)}, hands={"N": "fist", "F": "fist"})
    flash(b, 112, peak=24, dur=46)
    return _doc(b, sc, "hero-first-skill")


def hero_test_passed(lib):
    """H5 Тест Титана пройден 2.25 s: five marks of the scale light up in turn, the Titan nods
    and stands into the pose of the mark."""
    n = 135
    b = figure.Builder("hero-test-passed", n, [0, 60, 96, n], FR, True)
    ring(b, "ring", width=4.0, opacity=55)
    # five divisions on the lower arc of the ring (A40 §3: five steps of the test)
    for i in range(5):
        a = math.radians(90 + (i - 2) * 14)   # around the bottom
        a0, a1 = a - math.radians(5), a + math.radians(5)
        pts = [[RING_C[0] + RING_R * math.cos(x), RING_C[1] + RING_R * math.sin(x)] for x in (a1, (a0 + a1) / 2, a0)]
        path = {"c": False, "v": [[round(x, 1), round(y, 1)] for x, y in pts], "i": [[0, 0]] * 3, "o": [[0, 0]] * 3}
        f = 8 + i * 9
        op = {"a": 1, "k": [_k(0, 25), _k(f, 25), _k(f + 6, 100, (0.2, 0, 0.3, 1), last=True)]}
        wk = {"a": 1, "k": [_k(f, 6.0), _k(f + 6, 12.0), _k(f + 18, 9.0, last=True)]}
        b.add(f"scale_{i + 1}", 4, lt.transform(o=op), [lt.group("ring", [lt.shape_path(path), lt.stroke(figure.PROPS, wk, "tick_stroke")])])
    columns(b, n)
    # v2 drew this «nod» as a tilt of the head (head + nod); since Path v1 a front-view nod is a
    # pitch (figure.bone_values), so the tilt keeps its own channel
    poses = {"stand": stand(), "nod": stand(head=14.0), "mark": mark_pose()}
    sc = _figure(b, poses, [("hold", 0.75), ("move", "nod", 0.25, "inout"), ("move", "stand", 0.3, "inout"), ("move", "mark", 0.7, "inout"),
                            ("hold", 0.25)], "stand", pulses=[(100, 8, 7.0, 24)])
    return _doc(b, sc, "hero-test-passed")


HEROES = {
    "hero-seal": hero_seal,
    "hero-level-up": hero_level_up,
    "hero-record": hero_record,
    "hero-first-skill": hero_first_skill,
    "hero-test-passed": hero_test_passed,
}
