"""The brand Titan as a cut-out rig («марионетка», v2): pieces of the mark on the bones of TitanPose.

Pieces (cutout.py, drawn.py) are filled line art — a heavy outer line and tapered inner lines, the
way the mark is drawn — over a glass knockout. Each piece is one shape layer parented like a bone.

Joints: of two pieces meeting at a joint the child lies under its parent; both end in a disc round
the joint, so when the joint bends the parent's disc covers the child's cut, and a ring of the
light line under the child shows only where the disc is the silhouette (the outer side of a bend).

Draw order (bottom → top), side view: floor · props behind · far arm · far leg (veiled) · chest ·
pelvis · head · near leg · skirt · belt · flap · props held · near arm · props in front.

Names are the contract with the app: titan_line (gradient, the light), titan_knockout (glass,
also the veil over far limbs), props_line, floor.
"""
from __future__ import annotations

import math

from . import cutout, drawn
from . import lottie as lt
from . import rig
from .figure import GRAD_BOTTOM, GRAD_TOP, KNOCKOUT, NEUTRAL_STOPS, bone_values, to_local

FAR_OPACITY = 45         # far limbs (side view), as in v1
SHARED = ("ua", "fa", "hd", "th", "sh", "ft")
# rig y of each piece's joint in a standing figure: the light of the shared pieces is fixed to it
STAND_Y = {"ua": 22.0, "fa": 36.0, "ha": 47.0, "th": 50.0, "sh": 71.0, "ft": 90.0}
LINE_W = 1.0             # rig units: the outer line of the drawn pieces (≈ the mark's line)

PARENT = {"pelvis": "root", "chest": "pelvis", "head": "chest", "uaN": "chest", "uaF": "chest", "faN": "uaN", "faF": "uaF",
          "hdN": "faN", "hdF": "faF", "thN": "root", "thF": "root", "shN": "thN", "shF": "thF", "ftN": "shN", "ftF": "shF",
          "skirt": "pelvis", "belt": "pelvis", "flap": "pelvis"}


def gradient_fill(stops, start, end, nm="light"):
    k = []
    for off, c in stops:
        k += [off] + list(c)
    return {"ty": "gf", "nm": nm, "o": lt.static(100), "r": 1, "g": {"p": len(stops), "k": lt.static(k)}, "s": start, "e": end, "t": 1}


def _paths(items, nm):
    return [lt.shape_path(p, f"{nm}{i}") for i, p in enumerate(items)]


def hand_spec(spec, side):
    """('open'|'fist', rel_deg | ('world', deg)) — the default hand hangs on in the forearm's line."""
    h = (spec or {}).get(side, ("open", 0.0))
    return h if isinstance(h, tuple) else (h, 0.0)


def build_titan(b, frames, front, props_behind=(), props_held=(), props_front=(), breath_scale=0.035, belly_scale=0.07,
                stops=NEUTRAL_STOPS, hands=None, cloth=True, pulses=(), fold=()):
    """Adds the rig and the cut-out figure to the builder; returns the per-frame bone values."""
    s = b.fr.s
    vals = [bone_values(p, front) for p in frames]
    n = b.n
    P = drawn.pieces(front)

    rx = [b.fr.px(p["rx"], p["ry"])[0] for p in frames]
    ry = [b.fr.px(p["rx"], p["ry"])[1] for p in frames]
    root = b.add("rig", 3, lt.transform(p=b.split_pos(rx, ry, tol=0.35), s=lt.static([s * 100, s * 100, 100])), key="root")

    # --- hands: a bone on the forearm's end (wrist), rotation relative to the forearm
    for side in "NF":
        kind, ang = hand_spec(hands, side)
        rel = []
        for v in vals:
            if callable(ang):
                rel.append(ang(v, side))
            elif isinstance(ang, tuple):
                rel.append(ang[1] - v["fa" + side]["abs"])
            else:
                rel.append(ang)
        for k in range(1, len(rel)):
            rel[k] = rel[k - 1] + rig.shortest(rel[k - 1], rel[k])
        for v, r in zip(vals, rel):
            fa = v["fa" + side]
            v["hd" + side] = dict(r=r, p=(0.0, -cutout.FOREARM), abs=fa["abs"] + r, o=rig.step(fa["o"], fa["abs"], cutout.FOREARM * fa.get("sy", 1.0)),
                                  kind=kind)

    # --- cloth: the skirt follows the thighs halfway, the flap hangs and swings (secondary motion)
    if cloth and not front:
        _cloth(vals, frames, b)

    def rot(name, tol=0.3):
        return b.scalar([v[name]["r"] for v in vals], tol=tol)

    def pos(name):
        return b.split_pos([v[name]["p"][0] for v in vals], [v[name]["p"][1] for v in vals], tol=0.01)

    def grad_pts(name):
        st, en = [], []
        for v in vals:
            o, a = v[name]["o"], v[name]["abs"]
            st.append(to_local((o[0], GRAD_TOP), o, a))
            en.append(to_local((o[0], GRAD_BOTTOM), o, a))
        return b.point(st, tol=2.5), b.point(en, tol=2.5)

    def group_scale(name):
        if name == "chest":
            vs, first = [100 * (1 + breath_scale * f.get("breath", 0.0)) for f in frames], True
        elif name == "pelvis":
            vs, first = [100 * (1 + belly_scale * f.get("belly", 0.0)) for f in frames], True
        elif name[:2] in ("ua", "fa"):
            vs, first = [100 * v[name]["sy"] for v in vals], False
        else:
            return None
        fx = b.scalar(vs, 0.1)
        if fx["a"] == 0:
            return None
        from .figure import _pair_keys
        return _pair_keys(fx, 100, first)

    anchor_for = {"chest": (0.0, -6.5), "pelvis": (0.0, -6.0)}
    b.assets = getattr(b, "assets", [])
    b.anchor = getattr(b, "anchor", {})
    made_assets = {}

    def ppos(name):
        """Position in the parent: a precomp parent's space is shifted by its anchor."""
        ox, oy = b.anchor.get(PARENT[name], (0.0, 0.0))
        return b.split_pos([v[name]["p"][0] + ox for v in vals], [v[name]["p"][1] + oy for v in vals], tol=0.01)

    def shared(key, pc):
        """A limb piece used by both sides: one precomp asset, gradient fixed in the piece (the light
        of a standing figure), so near and far share every path."""
        if key in made_assets:
            return made_assets[key]
        xs = [x for p in pc.line + pc.knock for x, _ in p["v"]]
        ys = [y for p in pc.line + pc.knock for _, y in p["v"]]
        half = math.ceil(max(max(map(abs, xs)), max(map(abs, ys))) + 2)
        size = 2 * half
        y0 = STAND_Y.get(key[:2], 50.0)
        gs, ge = lt.static([0, y0 - GRAD_TOP]), lt.static([0, y0 - GRAD_BOTTOM])
        shapes_ = [lt.group("titan_line", _paths(pc.line, "line") + [gradient_fill(stops, gs, ge)]),
                   lt.group("titan_knockout", _paths(pc.knock, "glass") + [lt.fill(KNOCKOUT, "glass")])]
        if pc.ring:
            r = pc.ring
            shapes_.append(lt.group("titan_line", [{"ty": "el", "nm": "joint", "p": lt.static([0, 0]), "s": lt.static([2 * r, 2 * r]), "d": 1},
                                                   lt.gradient_stroke(stops, LINE_W, gs, ge, nm="joint_light")]))
        inner = lt.layer(1, key, 4, n, lt.transform(p=lt.static([half, half, 0])), shapes_)
        aid = "piece_" + key
        b.assets.append({"id": aid, "nm": key, "fr": 60, "layers": [inner]})
        made_assets[key] = (aid, half, size)
        return made_assets[key]

    def piece_layer(name, piece_key=None, far=False):
        key = piece_key or name
        pc = P.get(key)
        parent = b.inds.get(PARENT[name], root)
        if name[:2] in SHARED:
            akey = key if key.startswith("hand") else name[:2]
            if front:
                key = key + name[2] if key.startswith("hand") else name
                pc = P[key]
                akey = key
            if name == "shN" and "N" in fold:
                akey, pc = "sh_fold", cutout.Piece(pc.line, pc.knock, None)
            aid, half, size = shared(akey, pc)
            sc = None
            if name[:2] == "ua":
                # a foreshortened arm (front view circles): the forearm and the hand inherit it
                fy = b.scalar([100 * v[name]["sy"] for v in vals], 0.1)
                if fy["a"]:
                    from .figure import _pair_keys
                    sc = _pair_keys(fy, 100, False)
                    for k in sc["k"]:
                        k["s"] = k["s"] + [100]
            ks = lt.transform(p=ppos(name), r=rot(name), a=lt.static([half, half, 0]), o=lt.static(FAR_OPACITY if far else 100), s=sc)
            ind = b.add(name, 0, ks, None, parent=parent, key=name)
            lay = b.layers[-1]
            lay.update({"refId": aid, "w": size, "h": size})
            b.anchor[name] = (half, half)
            return ind
        sc = group_scale(name)
        an = anchor_for.get(name, (0, 0))
        tr = lambda: lt.group_tr(p=an, a=an, s=sc) if sc else lt.group_tr()
        gs, ge = grad_pts(name)
        shapes_ = [lt.group("titan_line", _paths(pc.line, "line") + [gradient_fill(stops, gs, ge)], tr()),
                   lt.group("titan_knockout", _paths(pc.knock, "glass") + [lt.fill(KNOCKOUT, "glass")], tr())]
        if pc.ring:
            r = pc.ring
            shapes_.append(lt.group("titan_line", [{"ty": "el", "nm": "joint", "p": lt.static([0, 0]), "s": lt.static([2 * r, 2 * r]), "d": 1},
                                                   lt.gradient_stroke(stops, LINE_W, gs, ge, nm="joint_light")], tr()))
        ks = lt.transform(p=ppos(name), r=rot(name))
        return b.add(name, 4, ks, shapes_, parent=parent, key=name)

    def chain(names, far=False, keys=None):
        """Create in hierarchy order, then lay children under their parents."""
        mark = len(b.layers)
        for nm in names:
            k = keys.get(nm) if keys else None
            piece_layer(nm, k, far)
        made = b.layers[mark:]
        del b.layers[mark:]
        b.layers += list(reversed(made))

    hand_key = lambda side: {"hd" + side: "hand_" + vals[0]["hd" + side]["kind"]}

    for pr in props_behind:
        pr(b, vals, frames)
    if front:
        mark = len(b.layers)
        piece_layer("pelvis")
        piece_layer("chest")
        piece_layer("head")
        body = b.layers[mark:]
        del b.layers[mark:]
        chain(["thF", "shF", "ftF"])
        chain(["thN", "shN", "ftN"])
        b.layers += [body[1], body[0], body[2]]      # chest under pelvis (the wrap lies over the waist)
        mark = len(b.layers)
        chain(["uaF", "faF", "hdF"], keys=hand_key("F"))
        chain(["uaN", "faN", "hdN"], keys=hand_key("N"))
        arms = b.layers[mark:]
        del b.layers[mark:]
        for pr in props_held:
            pr(b, vals, frames)
        b.layers += arms
    else:
        # body first (parents of the arms), moved above the far limbs afterwards
        mark = len(b.layers)
        piece_layer("pelvis")
        piece_layer("chest")
        piece_layer("head")
        body = b.layers[mark:]
        del b.layers[mark:]
        far = len(b.layers)
        chain(["uaF", "faF", "hdF"], far=True, keys=hand_key("F"))
        chain(["thF", "shF", "ftF"], far=True)
        b.layers += [body[1], body[0], body[2]]
        if "N" in fold:
            # a folded leg (heel to the buttock): the shin lies over the thigh, like the drawing would show it
            mark = len(b.layers)
            chain(["thN", "shN", "ftN"])
            made = b.layers[mark:]
            del b.layers[mark:]
            # the knee's ring, faded in as the knee bends (straight, it would ring the knee)
            flex = [abs(rig.shortest(v["thN"]["abs"], v["shN"]["abs"])) for v in vals]
            op = b.scalar([max(0.0, min(1.0, (f - 25.0) / 35.0)) * 100 for f in flex], 2.0)
            gs, ge = grad_pts("shN")
            r = cutout.R_KNEE
            ax, ay = b.anchor["shN"]
            b.add("knee_ring", 4, lt.transform(p=lt.static([ax, ay, 0]), o=op),
                  [lt.group("titan_line", [{"ty": "el", "nm": "joint", "p": lt.static([0, 0]), "s": lt.static([2 * r, 2 * r]), "d": 1},
                                           lt.gradient_stroke(stops, LINE_W, gs, ge, nm="joint_light")])], parent=b.inds["shN"])
            ring = b.layers.pop()
            made = list(reversed(made))          # thigh, shin, foot (bottom → top)
            b.layers += [made[0], ring] + made[1:]
        else:
            chain(["thN", "shN", "ftN"])
        if cloth:
            for nm in ("skirt", "belt", "flap"):
                piece_layer(nm)
        mark = len(b.layers)
        chain(["uaN", "faN", "hdN"], keys=hand_key("N"))
        arms = b.layers[mark:]
        del b.layers[mark:]
        for pr in props_held:
            pr(b, vals, frames)
        b.layers += arms
    for pr in props_front:
        pr(b, vals, frames)
    return vals


def _cloth(vals, frames, b):
    """Skirt: turns round the hip by half the mean thigh flexion (it drapes over the thighs).
    Flap: a pendulum hanging from the knot — it lags behind the hips and settles (follow-through)."""
    from .motion import FPS
    n = len(vals)
    loop = abs(vals[0]["pelvis"]["abs"] - vals[-1]["pelvis"]["abs"]) < 1e-6 and vals[0]["pelvis"]["o"] == vals[-1]["pelvis"]["o"]
    sk = []
    for v in vals:
        pel = v["pelvis"]["abs"]
        flex = []
        for side in "NF":
            rel = rig.shortest(pel + 180.0, v["th" + side]["abs"])    # 0 = hanging, − = forward
            flex.append(rel)
        f = sum(flex) / 2
        # forward flexion swings the skirt forward (the front thigh lifts it); backward much less
        r = 0.55 * f if f < 0 else 0.2 * f
        sk.append(max(-55.0, min(25.0, r)))
    # the knot of the flap on the pelvis (local) → world, its acceleration drives the pendulum
    knot_local = drawn.FLAP_KNOT
    pts = []
    for v in vals:
        o, a = v["pelvis"]["o"], v["pelvis"]["abs"]
        c, s_ = math.cos(math.radians(a)), math.sin(math.radians(a))
        pts.append((o[0] + c * knot_local[0] - s_ * knot_local[1], o[1] + s_ * knot_local[0] + c * knot_local[1]))
    dt = 1.0 / FPS
    w = 2 * math.pi * 1.1
    zeta = 0.32
    theta, om = 180.0, 0.0
    res = [0.0] * n
    cycles = 4 if loop else 1
    for cyc in range(cycles):
        for f in range(n):
            fp, fn = pts[max(f - 1, 0)], pts[min(f + 1, n - 1)]
            f0 = pts[f]
            ax = (fn[0] - 2 * f0[0] + fp[0]) / (dt * dt)
            pel = vals[f]["pelvis"]["abs"]
            # hangs down, a little held by the knot (30 % follows the hips), pushed back by acceleration
            target = 180.0 + 0.3 * rig.shortest(0.0, pel) + max(-40.0, min(40.0, -ax * 0.9)) + 0.5 * sk[f]
            for _ in range(4):
                h = dt / 4
                acc = w * w * rig.shortest(theta, target) - 2 * zeta * w * om
                om += acc * h
                theta += om * h
            if cyc == cycles - 1:
                res[f] = theta
    if loop:
        res[-1] = res[0]
    for v, skr, th in zip(vals, sk, res):
        pel = v["pelvis"]
        v["skirt"] = dict(r=skr, p=drawn.SKIRT_PIVOT, abs=pel["abs"] + skr, o=pel["o"])
        v["belt"] = dict(r=0.0, p=(0.0, 0.0), abs=pel["abs"], o=pel["o"])
        rel = rig.shortest(0.0, th - (pel["abs"] + 180.0))
        v["flap"] = dict(r=rel, p=knot_local, abs=pel["abs"] + rel, o=pel["o"])
