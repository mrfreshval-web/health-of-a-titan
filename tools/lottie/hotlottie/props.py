"""Props (brief: same line, 40 % thinner, 60 % opacity, no glow): floor, wall, mat, bar, kettlebell,
band, strap, door frame. World props live in canvas px; held props are parented to the hand."""
from __future__ import annotations

import math

from . import lottie as lt
from . import rig
from .figure import KNOCKOUT, PROPS, FLOOR_C, PROP_PX, CANVAS, CIRCLE_R


def _line_group(paths, width_px, opacity=60, closed_fill=False):
    items = [lt.shape_path(p, f"p{i}") for i, p in enumerate(paths)]
    items.append(lt.stroke(PROPS, width_px, "props_stroke", opacity=opacity))
    return lt.group("props_line", items)


def _poly(pts, closed=False):
    return {"c": closed, "v": [list(p) for p in pts], "i": [[0, 0] for _ in pts], "o": [[0, 0] for _ in pts]}


def floor(b, half=None):
    y = b.fr.px(0, rig.FLOOR)[1]
    dy = y - CANVAS / 2
    chord = math.sqrt(max(CIRCLE_R ** 2 - dy ** 2, 0)) if abs(dy) < CIRCLE_R else 0
    hw = half if half is not None else max(chord * 0.92, 120)
    path = _poly([(CANVAS / 2 - hw, y), (CANVAS / 2 + hw, y)])
    b.add("floor", 4, lt.transform(), [lt.group("floor", [lt.shape_path(path), lt.stroke(FLOOR_C, 3.0, "floor_stroke", opacity=30)])])


def wall(x, top=14.0, bottom=rig.FLOOR):
    def add(b, vals, frames):
        p0, p1 = b.fr.px(x, top), b.fr.px(x, bottom)
        b.add("props_wall", 4, lt.transform(), [_line_group([_poly([p0, p1])], PROP_PX)])
    return add


def door(x, top=6.0, depth=3.0):
    """Door frame seen from the side: the jamb as two lines and the lintel corner."""
    def add(b, vals, frames):
        a = b.fr.px(x, top)
        c = b.fr.px(x, rig.FLOOR)
        a2 = b.fr.px(x + depth, top)
        c2 = b.fr.px(x + depth, rig.FLOOR)
        b.add("props_door", 4, lt.transform(), [_line_group([_poly([a, c]), _poly([a2, c2]), _poly([b.fr.px(x - 9, top), a2])], PROP_PX)])
    return add


def mat(x0, x1):
    def add(b, vals, frames):
        y0, y1 = rig.FLOOR - 0.9, rig.FLOOR
        pts = [b.fr.px(x0 + 0.6, y0), b.fr.px(x1 - 0.6, y0), b.fr.px(x1, y1), b.fr.px(x0, y1)]
        b.add("props_mat", 4, lt.transform(), [_line_group([_poly(pts, True)], PROP_PX * 0.8)])
    return add


def bar(y, x0=20.0, x1=80.0, grip_x=None):
    def add(b, vals, frames):
        paths = [_poly([b.fr.px(x0, y), b.fr.px(x1, y)])]
        if grip_x is not None:
            c = b.fr.px(grip_x, y)
            r = 1.3 * b.fr.s
            k = 0.5523 * r
            paths.append({"c": True, "v": [[c[0], c[1] - r], [c[0] + r, c[1]], [c[0], c[1] + r], [c[0] - r, c[1]]],
                          "i": [[-k, 0], [0, -k], [k, 0], [0, k]], "o": [[k, 0], [0, k], [-k, 0], [0, -k]]})
        b.add("props_bar", 4, lt.transform(), [_line_group(paths, PROP_PX)])
    return add


def kettlebell(side="N", at=11.8, dx=2.6, dy=0.6):
    """Goblet hold: the bell hangs upright under the hands, in front of the chest."""
    def add(b, vals, frames):
        rot = b.scalar([-v["fa" + side]["abs"] for v in vals], 0.1)
        r = 3.3
        cx, cy = dx, dy + 3.4
        k = 0.5523 * r
        bell = {"c": True, "v": [[cx, cy - r], [cx + r, cy], [cx, cy + r], [cx - r, cy]], "i": [[-k, 0], [0, -k], [k, 0], [0, k]],
                "o": [[k, 0], [0, k], [-k, 0], [0, -k]]}
        handle = {"c": False, "v": [[cx - 2.0, cy - 2.4], [cx, dy - 1.0], [cx + 2.0, cy - 2.4]], "i": [[0, 0], [-1.4, 0], [0, 0]], "o": [[0, 0], [1.4, 0], [0, 0]]}
        w = PROP_PX / b.fr.s
        # a holder layer at the hand, counter-rotated so the bell stays upright
        ox, oy = getattr(b, "anchor", {}).get("fa" + side, (0.0, 0.0))
        holder = b.add("props_kettlebell", 4, lt.transform(p=lt.static([ox, oy - at, 0]), r=rot), [
            lt.group("props_line", [lt.shape_path(handle, "handle"), lt.shape_path(bell, "bell"), lt.stroke(PROPS, w * 1.25, "props_stroke", opacity=75)]),
            lt.group("titan_knockout", [lt.shape_path(bell, "bell_fill"), lt.fill(KNOCKOUT)]),
        ], parent=b.inds["fa" + side])
        return holder
    return add


def stick(side="N", at=12.2, length=6.0):
    """A band or stick held in both hands, seen almost end-on in the side view."""
    def add(b, vals, frames):
        w = PROP_PX / b.fr.s
        path = _poly([(-length / 2, -at - 0.6), (length / 2, -at + 0.6)])
        ox, oy = getattr(b, "anchor", {}).get("fa" + side, (0.0, 0.0))
        b.add("props_band", 4, lt.transform(p=lt.static([ox, oy, 0])), [_line_group([path], w)], parent=b.inds["fa" + side])
    return add


def rail(y, x0, x1, base=None):
    """A wall seen face-on behind the figure (v4, leg swings side-on to the wall): a hand rail on
    its brackets and the skirting line; no edge the swinging leg could cross."""
    def add(b, vals, frames):
        paths = [_poly([b.fr.px(x0, y), b.fr.px(x1, y)])]
        for x in (x0 + 4.0, x1 - 4.0):
            paths.append(_poly([b.fr.px(x, y), b.fr.px(x, y + 2.2)]))
        if base is not None:
            paths.append(_poly([b.fr.px(x0 - 6, base), b.fr.px(x1 + 6, base)]))
        b.add("props_rail", 4, lt.transform(), [_line_group(paths, PROP_PX, opacity=45)])
    return add


def band(layer):
    """The band between the hands (v4, band dislocates seen from the front), drawn twice: in front of
    the body while the hands are in front (frame key "_band_front" = 1) and behind it afterwards."""
    def add(b, vals, frames):
        pts = []
        for v in vals:
            n, f = b.fr.px(*v["hand"]["N"]), b.fr.px(*v["hand"]["F"])
            pts.append([n[0], n[1], f[0], f[1]])

        def wrap(vv):
            return lt.path_value([(vv[0], vv[1]), (vv[2], vv[3])], [(0, 0), (0, 0)], [(0, 0), (0, 0)], False)
        ks = lt.fit_linear(pts, b.breaks, 1.0, wrap=wrap)
        vis = [p.get("_band_front", 1.0) for p in frames]
        if layer == "behind":
            vis = [1.0 - x for x in vis]
        op = b.scalar([100 * x for x in vis], 1.0)
        b.add("props_band_" + layer, 4, lt.transform(o=op), [lt.group("props_line", [{"ty": "sh", "nm": "band", "ks": ks},
                                                                                    lt.stroke(PROPS, PROP_PX * 1.3, "props_stroke", opacity=80)])])
    return add


def strap(side="N"):
    """Strap from the hand to the sole of the foot (hamstring stretch)."""
    def add(b, vals, frames):
        pts = []
        for v, p in zip(vals, frames):
            hand = v["hand"][side]
            ft = v["ft" + side]
            a = math.radians(ft["abs"])
            # sole point: 3 units along the foot, 1.5 units to the sole side (local +x)
            o = ft["o"]
            sx = o[0] + math.sin(a) * 3.2 + math.cos(a) * 1.6
            sy = o[1] - math.cos(a) * 3.2 + math.sin(a) * 1.6
            h = b.fr.px(*hand)
            s_ = b.fr.px(sx, sy)
            pts.append([h[0], h[1], s_[0], s_[1]])

        def wrap(vv):
            return lt.path_value([(vv[0], vv[1]), (vv[2], vv[3])], [(0, 0), (0, 0)], [(0, 0), (0, 0)], False)
        ks = lt.fit_linear(pts, b.breaks, 1.0, wrap=wrap)
        b.add("props_strap", 4, lt.transform(), [lt.group("props_line", [{"ty": "sh", "nm": "strap", "ks": ks}, lt.stroke(PROPS, PROP_PX, "props_stroke", opacity=60)])])
    return add
