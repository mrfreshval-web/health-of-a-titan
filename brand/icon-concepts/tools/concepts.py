"""The three HoT mark concepts, drawn on the 108 dp adaptive-icon grid.

Each concept exposes mark(level) -> shapely geometry, where level is
  "full"  - symbol / launcher icon (all details),
  "mono"  - themed icon (Android 13+): same silhouette, hairline details removed,
  "small" - notification glyph (24 dp): simplified and heavier.
"""
from __future__ import annotations

from shapely.geometry import LineString

from geom import (
    circle,
    cubic,
    mirror_x,
    path_poly,
    poly,
    rect,
    ring,
    rrect,
    stroke,
    union,
)

C = 54.0  # vertical axis of symmetry


# ---------------------------------------------------------------- columns
def doric_column(cx, top, bottom, shaft_w, cap_w, flutes=0, flute_w=1.0, heavy=False):
    """Classical column: abacus, echinus, shaft (slight entasis), torus, plinth."""
    ab_h = 2.6 if not heavy else 3.2
    ech_h = 2.0 if not heavy else 2.2
    pl_h = 3.0 if not heavy else 3.4
    to_h = 2.0 if not heavy else 2.2
    hw_cap = cap_w / 2
    hw_top = shaft_w * 0.44  # entasis: shaft narrower at the top
    hw_bot = shaft_w * 0.5
    abacus = rect(cx - hw_cap, top, cx + hw_cap, top + ab_h)
    y_e = top + ab_h
    echinus = poly(
        [
            (cx - hw_cap + 0.9, y_e),
            (cx + hw_cap - 0.9, y_e),
            (cx + hw_top, y_e + ech_h),
            (cx - hw_top, y_e + ech_h),
        ]
    )
    y_s0 = y_e + ech_h
    y_s1 = bottom - pl_h - to_h
    shaft = poly([(cx - hw_top, y_s0), (cx + hw_top, y_s0), (cx + hw_bot, y_s1), (cx - hw_bot, y_s1)])
    torus = rrect(cx - hw_cap + 0.8, y_s1, cx + hw_cap - 0.8, y_s1 + to_h + 0.6, to_h / 2)
    plinth = rect(cx - hw_cap, bottom - pl_h, cx + hw_cap, bottom)
    col = union(abacus, echinus, shaft, torus, plinth)
    if flutes:
        cuts = []
        # flutes are evenly spaced; they stop short of capital and base
        for i in range(flutes):
            f = (i + 1) / (flutes + 1) - 0.5  # -0.5..0.5
            x_t = cx + f * 2 * hw_top * 0.92
            x_b = cx + f * 2 * hw_bot * 0.92
            cuts.append(
                poly(
                    [
                        (x_t - flute_w / 2, y_s0 + 1.6),
                        (x_t + flute_w / 2, y_s0 + 1.6),
                        (x_b + flute_w / 2, y_s1 - 1.4),
                        (x_b - flute_w / 2, y_s1 - 1.4),
                    ]
                )
            )
        col = col.difference(union(*cuts))
    return col


def pair(geom_left):
    return union(geom_left, mirror_x(geom_left, C))


# ===================================================== Concept 1: ATLAS
# Figurative: a draped titan presses on two columns. Arms + columns = "H".
# The pulse runs from column to column in gold and, where it meets the
# drapery, continues as a cut: the heartbeat passes through the titan.
def atlas(level="full"):
    small = level == "small"
    top, bottom = 31.0, 76.5
    shaft_w = 6.8 if not small else 8.8
    cap_w = 10.0 if not small else 12.0
    cx_l = 35.6 if not small else 35.0
    col = doric_column(cx_l, top, bottom, shaft_w, cap_w, flutes=2 if level == "full" else 0, flute_w=0.85, heavy=small)
    cols = pair(col)
    inner = cx_l + shaft_w * 0.44  # inner edge of the shaft where the hands press

    k = 1.0 if not small else 1.3  # limb thickness factor for the 24 dp glyph
    # heroic canon: head ~1/7.5 of the figure height
    head = path_poly(  # slightly oval head, chin narrower than the crown
        [
            (C, 33.6),
            ("c", (C + 2.0, 33.6), (C + 3.1, 35.0), (C + 3.0, 36.9)),
            ("c", (C + 2.9, 38.6), (C + 1.8, 40.1), (C, 40.2)),
            ("c", (C - 1.8, 40.1), (C - 2.9, 38.6), (C - 3.0, 36.9)),
            ("c", (C - 3.1, 35.0), (C - 2.0, 33.6), (C, 33.6)),
        ]
    ) if not small else circle(C, 36.6, 4.0)
    neck = rect(C - 1.5, 39.0, C + 1.5, 43.0)
    # left arm (mirrored): trapezius slope -> shoulder -> straight arm, hand flat on the shaft
    t = 1.2 * (k - 1)
    arm_l = path_poly(
        [
            (C - 1.4, 40.3),
            ("c", (C - 2.6, 41.0), (C - 4.4, 41.3), (C - 6.2, 41.3)),
            ("c", (C - 7.6, 41.2), (C - 9.0, 40.4), (C - 10.4, 39.9)),
            (inner - 0.5, 38.4 - t),
            (inner - 0.5, 41.9 + t),
            (C - 10.6, 43.4 + t),
            ("c", (C - 8.8, 43.9), (C - 7.6, 45.0), (C - 6.6, 46.8)),
            (C - 1.4, 46.8),
        ]
    )
    # torso: broad chest tapering to the waist, no anatomy drawn
    torso_l = path_poly(
        [
            (C, 40.4),
            (C - 1.4, 40.4),
            ("c", (C - 2.6, 41.1), (C - 4.4, 41.4), (C - 6.2, 41.5)),
            ("c", (C - 7.0, 43.4), (C - 6.6, 47.0), (C - 5.6, 49.4)),
            ("c", (C - 4.8, 51.2), (C - 4.2, 52.6), (C - 4.0, 54.6)),
            (C, 54.6),
        ]
    )
    upper = union(head, neck, arm_l, mirror_x(arm_l, C), torso_l, mirror_x(torso_l, C))
    # chiton: falls from the waist to mid-calf, the hem dipping to the left
    skirt = path_poly(
        [
            (C - 4.0, 54.0),
            (C + 4.0, 54.0),
            ("c", (C + 5.0, 58.0), (C + 5.7, 63.0), (C + 6.2, 68.0)),
            ("c", (C + 2.6, 68.8), (C - 2.4, 69.8), (C - 6.4, 71.2)),
            ("c", (C - 5.9, 65.0), (C - 4.8, 58.6), (C - 4.0, 54.0)),
        ]
    )
    # himation: thrown over the left shoulder, crossing the chest to the right hip,
    # its loose end hanging from the right hip
    sash = path_poly(
        [
            (C - 6.6, 41.6),
            (C - 3.4, 41.2),
            ("c", (C - 0.4, 45.6), (C + 2.8, 49.8), (C + 5.4, 53.4)),
            ("c", (C + 7.4, 56.6), (C + 8.0, 60.0), (C + 7.8, 63.8)),
            (C + 6.3, 68.1),
            ("c", (C + 6.0, 62.4), (C + 5.0, 58.4), (C + 2.0, 55.4)),
            ("c", (C - 1.6, 51.8), (C - 4.4, 47.8), (C - 6.6, 41.6)),
        ]
    )
    lw = 2.9 * k
    leg_l = poly([(C - 4.4, 70.0), (C - 4.4 + lw, 70.0), (C - 1.9, bottom - 1.0), (C - 1.9 - lw, bottom - 1.0)])
    leg_r = poly([(C + 1.2, 68.0), (C + 1.2 + lw, 68.0), (C + 3.5 + lw * 0.2, bottom - 1.0), (C + 3.5 - lw * 0.8, bottom - 1.0)])
    feet = union(
        rrect(C - 1.9 - lw - 0.9, bottom - 1.4, C - 1.7, bottom, 0.6),
        rrect(C + 3.5 - lw * 0.8 - 0.1, bottom - 1.4, C + 3.5 + lw * 0.2 + 1.0, bottom, 0.6),
    )
    figure = union(upper, skirt, sash, leg_l, leg_r, feet)
    if level != "small":
        # drapery line: the lower edge of the himation, a soft curve rather than a strap
        edge = LineString(cubic((C - 5.4, 45.2), (C - 3.4, 49.6), (C - 1.0, 52.6), (C + 2.2, 55.6), 16))
        figure = figure.difference(edge.buffer(0.36, cap_style="flat"))

    # pulse at hip height
    y = 60.0
    pw = 1.9 if not small else 2.8
    skirt_l = C - 5.0  # skirt edge at pulse height (approx.)
    pulse = union(
        stroke([(inner - 0.6, y), (skirt_l + 0.4, y)], pw),
        stroke([(2 * C - skirt_l - 0.6, y), (2 * C - inner + 0.6, y)], pw),
    )
    mark = union(cols, figure, pulse)
    if level != "small":
        beat = stroke(
            [(C - 9.0, y), (C - 2.6, y), (C - 1.2, y - 4.2), (C + 0.9, y + 4.4), (C + 2.3, y), (C + 9.0, y)],
            1.0,
            mitre_limit=4,
        )
        mark = mark.difference(beat.intersection(union(skirt, sash).buffer(0.2)))
    return mark


# ===================================================== Concept 2: PORTICO
# Figure-less: two fluted columns; the H crossbar is one heartbeat whose
# R-spike rises between the columns. Neutral, enterprise-ready.
def portico(level="full"):
    small = level == "small"
    top, bottom = 31.0, 77.0
    shaft_w = 9.0 if not small else 10.6
    cap_w = 12.6 if not small else 13.8
    cx_l = 37.0 if not small else 36.4
    col = doric_column(cx_l, top, bottom, shaft_w, cap_w, flutes=2 if level != "small" else 0, flute_w=1.3, heavy=True)
    cols = pair(col)
    inner_b = cx_l + shaft_w * 0.5
    x0, x1 = inner_b - 1.0, 2 * C - inner_b + 1.0
    y = 56.5
    pw = 3.6 if not small else 5.0
    amp_r, amp_s = (16.5, 7.4) if not small else (15.0, 7.0)
    pts = [
        (x0, y),
        (C - 5.0, y),
        (C - 3.5, y + 3.2),  # Q
        (C - 0.5, y - amp_r),  # R
        (C + 2.7, y + amp_s),  # S
        (C + 4.4, y),
        (x1, y),
    ]
    beat = stroke(pts, pw, join="mitre", mitre_limit=5)
    return union(cols, beat)


# ===================================================== Concept 3: MONOGRAM
# A geometric titan that spells the name: pillars + arms = H, head = o,
# arms + body = T  ->  H-o-T. No drawn pulse: the heartbeat lives in motion
# (splash / loading: a beat runs along the arms), the mark stays pure.
def monogram(level="full"):
    small = level == "small"
    top, bottom = 31.4, 76.6
    pw_ = 8.2 if not small else 9.2  # pillar width
    cap_ext = 1.4 if not small else 1.3
    x_l0 = 31.6 if not small else 31.0
    x_l1 = x_l0 + pw_
    cap_h = 3.0 if not small else 3.4
    pillar_l = union(
        rect(x_l0, top + cap_h, x_l1, bottom - cap_h),
        rect(x_l0 - cap_ext, top, x_l1 + cap_ext, top + cap_h),
        rect(x_l0 - cap_ext, bottom - cap_h, x_l1 + cap_ext, bottom),
    )
    pillars = pair(pillar_l)
    bar_t, bar_b = (42.8, 48.4) if not small else (42.4, 49.0)
    bar = rect(x_l1 - 0.5, bar_t, 2 * C - x_l1 + 0.5, bar_b)
    sw = 4.5 if not small else 5.0  # stem half-width at the shoulders
    bw = 3.3 if not small else 4.0  # stem half-width at the base: the figure stands, drapery falls
    body = poly([(C - sw, bar_b - 0.5), (C + sw, bar_b - 0.5), (C + bw, bottom), (C - bw, bottom)])
    r_o = 5.5 if not small else 6.0
    head = ring(C, 34.9, r_o, 2.8 if not small else 2.4)
    return union(pillars, bar, body, head)


CONCEPTS = {
    "c1-atlas": atlas,
    "c2-portico": portico,
    "c3-monogram": monogram,
}


