"""Geometry helpers for the HoT mark concepts.

Every mark is built from primitives (rects, circles, cubic curves, polylines)
and merged with exact boolean operations, so each artwork exports as ONE clean
compound path (fill-rule evenodd). That is what Android VectorDrawable,
themed (monochrome) icons and a trademark filing all need.
"""
from __future__ import annotations

import math

from shapely.geometry import LineString, Point, Polygon, box
from shapely.geometry.polygon import orient
from shapely.ops import unary_union

# 108 x 108 dp adaptive-icon canvas; keep key content inside the 66 dp circle.
CANVAS = 108.0
CENTER = 54.0
SAFE_R = 33.0


def rect(x0, y0, x1, y1):
    return box(min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1))


def rrect(x0, y0, x1, y1, r):
    """Rounded rectangle."""
    if r <= 0:
        return rect(x0, y0, x1, y1)
    return rect(x0 + r, y0 + r, x1 - r, y1 - r).buffer(r, quad_segs=12, join_style="round")


def circle(cx, cy, r):
    return Point(cx, cy).buffer(r, quad_segs=24)


def ring(cx, cy, r_out, r_in):
    return circle(cx, cy, r_out).difference(circle(cx, cy, r_in))


def poly(points):
    return Polygon(points).buffer(0)


def cubic(p0, p1, p2, p3, n=14):
    """Sample a cubic Bezier (endpoints included)."""
    pts = []
    for i in range(n + 1):
        t = i / n
        mt = 1 - t
        x = mt**3 * p0[0] + 3 * mt**2 * t * p1[0] + 3 * mt * t**2 * p2[0] + t**3 * p3[0]
        y = mt**3 * p0[1] + 3 * mt**2 * t * p1[1] + 3 * mt * t**2 * p2[1] + t**3 * p3[1]
        pts.append((x, y))
    return pts


def path_poly(segments):
    """Closed outline from a list of segments.

    A segment is either a point (x, y) or a cubic ('c', p1, p2, p3) continuing
    from the previous point.
    """
    pts = []
    for seg in segments:
        if seg[0] == "c":
            p0 = pts[-1]
            pts.extend(cubic(p0, seg[1], seg[2], seg[3])[1:])
        else:
            pts.append(seg)
    return Polygon(pts).buffer(0)


def stroke(points, width, join="mitre", cap="flat", mitre_limit=6.0):
    """Polyline outlined to a polygon (sharp ECG peaks with mitre joins)."""
    return LineString(points).buffer(
        width / 2, cap_style=cap, join_style=join, mitre_limit=mitre_limit, quad_segs=10
    )


def union(*geoms):
    return unary_union([g for g in geoms if g is not None and not g.is_empty])


def mirror_x(geom, cx=CENTER):
    from shapely.affinity import scale

    return scale(geom, xfact=-1, yfact=1, origin=(cx, 0))


def transform(geom, s=1.0, dx=0.0, dy=0.0, origin=(0, 0)):
    from shapely.affinity import scale, translate

    return translate(scale(geom, s, s, origin=origin), dx, dy)


def _ring_d(coords, nd):
    fmt = "{:.%df}" % nd
    pts = list(coords)
    if pts[0] == pts[-1]:
        pts = pts[:-1]
    out = []
    prev = None
    for i, (x, y) in enumerate(pts):
        sx, sy = fmt.format(x).rstrip("0").rstrip("."), fmt.format(y).rstrip("0").rstrip(".")
        if (sx, sy) == prev:
            continue
        out.append(("M" if i == 0 else "L") + sx + "," + sy)
        prev = (sx, sy)
    return "".join(out) + "Z"


def to_d(geom, nd=2, simplify=0.012):
    """Shapely geometry -> SVG path data (all rings, use fill-rule evenodd)."""
    if simplify:
        geom = geom.simplify(simplify, preserve_topology=True)
    polys = [geom] if geom.geom_type == "Polygon" else list(geom.geoms)
    parts = []
    for p in polys:
        p = orient(p, sign=1.0)
        parts.append(_ring_d(p.exterior.coords, nd))
        for hole in p.interiors:
            parts.append(_ring_d(hole.coords, nd))
    return "".join(parts)


def safe_zone_report(geom, name):
    """Max distance of the artwork from the canvas centre vs. the 33 dp safe radius."""
    polys = [geom] if geom.geom_type == "Polygon" else list(geom.geoms)
    far = max(math.hypot(x - CENTER, y - CENTER) for p in polys for x, y in p.exterior.coords)
    status = "OK" if far <= SAFE_R + 1e-6 else "OUTSIDE"
    return f"{name}: farthest point {far:.2f} dp from centre (safe zone {SAFE_R}) -> {status}"
