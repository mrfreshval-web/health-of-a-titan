"""The Titan as Lottie layers: a null rig with parented bones, light line + glass knockout.

Draw order (bottom → top), so the outline reads as one figure (A37 §3):
  floor · props behind · far limbs (dim) · body (pelvis, chest, head) · near leg · wrap ·
  props held · near arm · props in front.
Inside each group every light line is laid first and every knockout after it, so lines between
overlapping parts of one group disappear and only the outer contour stays.

Layer and group names are the contract with the app (brief, «Слои и имена»):
  titan_line (Gradient Stroke) · titan_knockout (Fill) · titan_inner (Stroke) · props_line · floor.
"""
from __future__ import annotations

import math

from . import lottie as lt
from . import rig, shapes

NEUTRAL_STOPS = [(0.0, lt.hex_rgb("#FFFFFF")), (0.55, lt.hex_rgb("#DFFBF6")), (1.0, lt.hex_rgb("#9FEADC"))]
KNOCKOUT = lt.hex_rgb("#060E0D")
INNER = lt.hex_rgb("#DFFBF6")
PROPS = lt.hex_rgb("#9FEADC")
FLOOR_C = lt.hex_rgb("#9FEADC")

CANVAS = 1024
CIRCLE_R = 410
LINE_PX = 13.0          # visible outline (brief: 14 px at 1024; the stroke is centred on the edge)
INNER_PX = 5.0
PROP_PX = LINE_PX * 0.6
FAR_OPACITY = 45
GRAD_TOP, GRAD_BOTTOM = 8.0, 92.0


class Bone:
    def __init__(self, name, parent, world):
        self.name, self.parent, self.world = name, parent, world


def _deg(x):
    return math.degrees(x)


def bone_values(p, front):
    """Per-frame layer values: rotation relative to the parent, position in the parent, scale y,
    plus the world origin/angle of every bone (rig units) for gradient points and props."""
    d = _deg(math.atan(p.get("spine", 0.0)))
    h = 13.0 / math.cos(math.radians(d))
    torso = p["torso"]
    head = p["head"] + p.get("nod", 0.0)
    chest_abs = torso + d
    sw = rig.FRONT_SHOULDER if front else 0.0
    hw = rig.FRONT_HIP if front else 0.0
    v = {}
    hip = (p["rx"], p["ry"])
    v["pelvis"] = dict(r=torso - d, p=(0.0, 0.0), abs=torso - d, o=hip)
    mid = rig.step(hip, torso - d, h)
    v["chest"] = dict(r=2 * d, p=(0.0, -h), abs=chest_abs, o=mid)
    sh = rig.step(mid, chest_abs, h)
    v["head"] = dict(r=head - chest_abs, p=(0.0, -h), abs=head, o=sh)
    for side, sgn in (("N", 1), ("F", -1)):
        ln = p.get("armLen" + side, 1.0)
        # the shoulder offset of the front view is along the chest's local x
        so = (sh[0] + sgn * sw * math.cos(math.radians(chest_abs)), sh[1] + sgn * sw * math.sin(math.radians(chest_abs)))
        v["ua" + side] = dict(r=p["ua" + side] - chest_abs, p=(sgn * sw, -h), abs=p["ua" + side], o=so, sy=ln)
        el = rig.step(so, p["ua" + side], rig.L["ua"] * ln)
        v["fa" + side] = dict(r=p["fa" + side] - p["ua" + side], p=(0.0, -rig.L["ua"] * ln), abs=p["fa" + side], o=el, sy=ln)
        ho = (hip[0] + sgn * hw, hip[1])
        v["th" + side] = dict(r=p["th" + side], p=(sgn * hw, 0.0), abs=p["th" + side], o=ho)
        kn = rig.step(ho, p["th" + side], rig.L["th"])
        v["sh" + side] = dict(r=p["sh" + side] - p["th" + side], p=(0.0, -rig.L["th"]), abs=p["sh" + side], o=kn)
        an = rig.step(kn, p["sh" + side], rig.L["sh"])
        v["ft" + side] = dict(r=p["ft" + side] - p["sh" + side], p=(0.0, -rig.L["sh"]), abs=p["ft" + side], o=an)
    v["hand"] = {s: rig.step(v["fa" + s]["o"], p["fa" + s], rig.L["fa"] * p.get("armLen" + s, 1.0)) for s in "NF"}
    return v


PARENT = {"pelvis": "root", "chest": "pelvis", "head": "chest", "uaN": "chest", "uaF": "chest", "faN": "uaN", "faF": "uaF",
          "thN": "root", "thF": "root", "shN": "thN", "shF": "thF", "ftN": "shN", "ftF": "shF", "wrap": "pelvis"}


def to_local(world_pt, origin, angle):
    dx, dy = world_pt[0] - origin[0], world_pt[1] - origin[1]
    c, s = math.cos(math.radians(angle)), math.sin(math.radians(angle))
    # world = o + R(angle)·local, R = [[c, -s], [s, c]]  →  local = Rᵀ·(world − o)
    return (c * dx + s * dy, -s * dx + c * dy)


class Frame:
    """Placement of the 100-unit rig box on the 1024 canvas."""

    def __init__(self, s, ox, oy):
        self.s, self.ox, self.oy = s, ox, oy

    def px(self, x, y):
        return (self.ox + x * self.s, self.oy + y * self.s)


def fit_frame(points, smax=9.2, floor_max=900.0, r=CIRCLE_R - 10):
    """Largest scale ≤ smax that keeps every point in the circle, centred on the motion's box."""
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
    far = max(math.hypot(x - cx, y - cy) for x, y in points)
    s = min(smax, r / far)
    ox = CANVAS / 2 - cx * s
    oy = CANVAS / 2 - cy * s
    if oy + rig.FLOOR * s > floor_max:
        oy = floor_max - rig.FLOOR * s
    return Frame(s, ox, oy)


def motion_points(frames, front, extra_pts=()):
    pts = list(extra_pts)
    for p in frames[:: max(1, len(frames) // 90)]:
        j = rig.joints(p, front)
        for k, q in j.items():
            pts.append(q)
        hd = j["head"]
        pts += [(hd[0] + 6, hd[1]), (hd[0] - 6, hd[1]), (hd[0], hd[1] - 6.5), (hd[0], hd[1] + 6)]
        for k in ("knee", "ankle", "toe", "hand", "elbow"):
            for s in "NF":
                q = j[k + s]
                pts += [(q[0] + 3, q[1] + 3), (q[0] - 3, q[1] - 3)]
    return pts


class Builder:
    def __init__(self, name, n_frames, breaks, frame: Frame, front=False):
        self.name = name
        self.n = n_frames
        self.breaks = breaks
        self.fr = frame
        self.front = front
        self.layers = []  # bottom → top
        self.ind = 0
        self.inds = {}

    def add(self, nm, ty, ks, shapes_=None, parent=None, key=None):
        self.ind += 1
        lay = lt.layer(self.ind, nm, ty, self.n, ks, shapes_, parent)
        self.layers.append(lay)
        if key:
            self.inds[key] = self.ind
        return self.ind

    def scalar(self, values, tol=0.04):
        return lt.fit_scalar(values, self.breaks, tol)

    def split_pos(self, xs, ys, tol=0.02):
        fx, fy = self.scalar(xs, tol), self.scalar(ys, tol)
        if fx["a"] == 0 and fy["a"] == 0:
            return lt.static([fx["k"], fy["k"], 0])
        return {"s": True, "x": fx, "y": fy}

    def point(self, pts, tol=0.6):
        return lt.fit_linear([list(p) for p in pts], self.breaks, tol, rnd=1)

    def json(self, markers, meta):
        layers = list(reversed(self.layers))
        return {"v": "5.12.2", "fr": 60, "ip": 0, "op": self.n, "w": CANVAS, "h": CANVAS, "nm": self.name, "ddd": 0,
                "assets": list(getattr(self, "assets", [])), "layers": layers, "markers": markers, "meta": meta}


def build_figure(b: Builder, frames, front, props_behind=(), props_held=(), props_front=(), breath_scale=0.035, belly_scale=0.07,
                 line_px=LINE_PX, extra_layers=None, stops=NEUTRAL_STOPS, pulses=()):
    """Adds the rig and the figure layers to the builder. Returns the per-frame bone values.
    pulses: [(frame, spread_frames, extra_px, sweep_frames)] — a swell of the light line that runs
    over the figure from the head down (heroic moments: «линия света пробегает по фигуре»)."""
    s = b.fr.s
    vals = [bone_values(p, front) for p in frames]
    n = b.n
    rim = line_px / s            # visible outline, rig units
    w_line = 2 * rim
    w_inner = INNER_PX / s

    # --- root null: hip position on the canvas, scale = rig units → px
    rx = [b.fr.px(p["rx"], p["ry"])[0] for p in frames]
    ry = [b.fr.px(p["rx"], p["ry"])[1] for p in frames]
    root = b.add("rig", 3, lt.transform(p=b.split_pos(rx, ry, tol=0.35), s=lt.static([s * 100, s * 100, 100])), key="root")

    shp = shapes.bone_shapes(front)
    inner = shapes.inner_lines(front)

    def rot(name):
        return b.scalar([v[name]["r"] for v in vals], tol=0.3)

    def pos(name):
        xs = [v[name]["p"][0] for v in vals]
        ys = [v[name]["p"][1] for v in vals]
        return b.split_pos(xs, ys, tol=0.01)

    def grad_pts(name):
        st, en = [], []
        for v in vals:
            o, a = v[name]["o"], v[name]["abs"]
            st.append(to_local((o[0], GRAD_TOP), o, a))
            en.append(to_local((o[0], GRAD_BOTTOM), o, a))
        return b.point(st, tol=2.5), b.point(en, tol=2.5)

    def group_scale(name):
        """Breathing widens the chest (and the belly for breath work) along the local x;
        foreshortened arms (front view circles) shorten along the local y."""
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
        return _pair_keys(fx, 100, first)

    anchor_for = {"chest": (-3.6, -6.5), "pelvis": (-3.6, -5.0)}

    def bone_layers(name, opacity=100, line_only_path=None):
        fill_path, line_path = shp[name]
        sc = group_scale(name)
        an = anchor_for.get(name, (0, 0))
        tr = lambda: lt.group_tr(p=an, a=an, s=sc) if sc else lt.group_tr()
        gs, ge = grad_pts(name)
        width = w_line
        if pulses:
            import math as _m
            oy = vals[0][name]["o"][1]
            ws = []
            for f in range(n + 1):
                w = w_line
                for fc, spread, extra, sweep in pulses:
                    d = fc + sweep * min(max((oy - 10.0) / 80.0, 0.0), 1.0)
                    w += extra / s * _m.exp(-((f - d) / spread) ** 2)
                ws.append(w)
            width = b.scalar(ws, 0.01)
        line_shapes = [lt.group("titan_line", [lt.shape_path(line_path), lt.gradient_stroke(stops, width, gs, ge)], tr())]
        parent = b.inds[PARENT[name]] if PARENT[name] in b.inds else root
        ks = lt.transform(p=pos(name), r=rot(name), o=lt.static(opacity))
        line_ind = b.add(f"{name}_line", 4, ks, line_shapes, parent=parent, key=name)
        ko_shapes = []
        if name in inner:
            ko_shapes.append(lt.group("titan_inner", [lt.shape_path(pth, f"inner{i}") for i, pth in enumerate(inner[name])] +
                                      [lt.stroke(INNER, w_inner, "inner_stroke", opacity=70, cap=2)], tr()))
        ko_shapes.append(lt.group("titan_knockout", [lt.shape_path(fill_path), lt.fill(KNOCKOUT)], tr()))
        return line_ind, ko_shapes

    def group_pass(names, opacity=100):
        kos = []
        for nm in names:
            li, ko = bone_layers(nm, opacity)
            kos.append((nm, li, ko))
        for nm, li, ko in kos:
            b.add(f"{nm}_knockout", 4, lt.transform(), ko, parent=li)

    for pr in props_behind:
        pr(b, vals, frames)
    # parents must exist before children: create in hierarchy order within passes
    if front:
        group_pass(["thF", "shF", "ftF", "thN", "shN", "ftN", "pelvis", "chest", "head"])
        wrap_pass(b, shp, stops, w_line, vals, frames, grad_pts, inner.get("wrap"), w_inner)
        mark = len(b.layers)
        group_pass(["uaF", "faF"])
        group_pass(["uaN", "faN"])
        arms = b.layers[mark:]
        del b.layers[mark:]
        for pr in props_held:
            pr(b, vals, frames)
        b.layers += arms
    else:
        # far limbs need their parents (chest) first: create the body chain as invisible-order
        # parents by building the body before, then moving far layers below it.
        body_start = len(b.layers)
        group_pass(["pelvis", "chest", "head"])
        body = b.layers[body_start:]
        del b.layers[body_start:]
        far_start = len(b.layers)
        group_pass(["uaF", "faF", "thF", "shF", "ftF"], opacity=FAR_OPACITY)
        far = b.layers[far_start:]
        del b.layers[far_start:]
        b.layers += far + body
        group_pass(["thN", "shN", "ftN"])
        wrap_pass(b, shp, stops, w_line, vals, frames, grad_pts, inner.get("wrap"), w_inner)
        mark = len(b.layers)
        group_pass(["uaN", "faN"])
        arms = b.layers[mark:]
        del b.layers[mark:]
        for pr in props_held:
            pr(b, vals, frames)
        b.layers += arms
    for pr in props_front:
        pr(b, vals, frames)
    return vals


def wrap_pass(b, shp, stops, w_line, vals, frames, grad_pts, inner=None, w_inner=0.5):
    fill_path, line_path = shp["wrap"]
    ko = [lt.group("titan_inner", [lt.shape_path(line_path, "edge"), lt.stroke(INNER, w_inner * 1.5, "wrap_edge", opacity=85)])]
    if inner:
        ko.append(lt.group("titan_inner", [lt.shape_path(p, f"fold{i}") for i, p in enumerate(inner)] +
                           [lt.stroke(INNER, w_inner, "inner_stroke", opacity=55)]))
    ko.append(lt.group("titan_knockout", [lt.shape_path(fill_path), lt.fill(KNOCKOUT)]))
    # the wrap lies inside the silhouette: its edge is a fine inner line, the body keeps the outline
    b.add("wrap", 4, lt.transform(), ko, parent=b.inds["pelvis"], key="wrap")


def _pair_keys(fx, other, first=True):
    """Scalar keyframes → 2D scale keyframes; the other dimension is constant, so one easing fits both."""
    out = []
    for k in fx["k"]:
        v = k["s"][0]
        k2 = {"t": k["t"], "s": [v, other] if first else [other, v]}
        if "o" in k:
            k2["o"] = {"x": k["o"]["x"][0], "y": k["o"]["y"][0]}
            k2["i"] = {"x": k["i"]["x"][0], "y": k["i"]["y"][0]}
        out.append(k2)
    return {"a": 1, "k": out}
