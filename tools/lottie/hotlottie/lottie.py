"""Small Lottie (Bodymovin 5.x) writer: layers, shapes and keyframes fitted to sampled curves.

Scalar channels are fitted with cubic Hermite segments: a Lottie keyframe with time handles at
1/3 and 2/3 makes the value a cubic Bézier in time, so one key carries slope (ease, overshoot,
arcs) without dense keys. Vectors (gradient points, paths) are fitted with linear segments.
Only features lottie-android 6.x supports: shape layers, null parents, groups, paths, fill,
stroke, gradient stroke, trim paths, transforms. No expressions, effects, masks, mattes, text.
"""
from __future__ import annotations

R = lambda v, n=2: round(float(v), n) if abs(v) > 10 ** -n / 2 else 0


def static(v):
    return {"a": 0, "k": v}


def fit_scalar(samples, breaks, tol=0.05, max_handle=12.0):
    """samples: values for frames 0…N (inclusive). breaks: frames that must be keys.
    Returns Lottie keyframes (list) or a static value."""
    n = len(samples) - 1
    lo, hi = min(samples), max(samples)
    if hi - lo < tol * 0.5:
        return static(R(samples[0], 3))
    # slopes per frame (central differences; ends one-sided, the cycle is closed by the caller)
    m = [0.0] * (n + 1)
    for k in range(n + 1):
        a = samples[max(k - 1, 0)]
        b = samples[min(k + 1, n)]
        m[k] = (b - a) / (min(k + 1, n) - max(k - 1, 0))
    if samples[0] == samples[n] and n > 2:
        m[0] = m[n] = (samples[1] - samples[n - 1]) / 2

    def herm(a, b, f):
        dt = b - a
        u = (f - a) / dt
        h00 = 2 * u ** 3 - 3 * u ** 2 + 1
        h10 = u ** 3 - 2 * u ** 2 + u
        h01 = -2 * u ** 3 + 3 * u ** 2
        h11 = u ** 3 - u ** 2
        return h00 * samples[a] + h10 * dt * m[a] + h01 * samples[b] + h11 * dt * m[b]

    def handles(a, b):
        d = samples[b] - samples[a]
        dt = b - a
        if abs(d) < 1e-6:
            if (abs(m[a]) + abs(m[b])) * dt / 4 < tol:
                return 0.0, 1.0
            return None
        oy = m[a] * dt / (3 * d)
        iy = 1 - m[b] * dt / (3 * d)
        if abs(oy) > max_handle or abs(iy) > max_handle:
            return None
        return oy, iy

    keys = sorted(set([0, n] + [b for b in breaks if 0 < b < n]))
    out = []
    stack = list(zip(keys[:-1], keys[1:]))[::-1]
    segs = []
    while stack:
        a, b = stack.pop()
        hd = handles(a, b)
        worst, wf = 0.0, None
        if hd is not None:
            for f in range(a + 1, b):
                e = abs(herm(a, b, f) - samples[f])
                if e > worst:
                    worst, wf = e, f
        if (hd is None or worst > tol) and b - a > 1:
            mid = wf if (wf is not None and hd is not None) else (a + b) // 2
            mid = min(max(mid, a + 1), b - 1)
            stack.append((mid, b))
            stack.append((a, mid))
            continue
        segs.append((a, b, hd if hd is not None else (0.0, 1.0)))
    segs.sort()
    for a, b, (oy, iy) in segs:
        out.append({"t": a, "s": [R(samples[a], 3)], "o": {"x": [0.333], "y": [R(oy, 4)]}, "i": {"x": [0.667], "y": [R(iy, 4)]}})
    out.append({"t": n, "s": [R(samples[n], 3)]})
    return {"a": 1, "k": out}


def _dist(a, b):
    return max(abs(x - y) for x, y in zip(a, b))


def fit_linear(samples, breaks, tol=0.15, wrap=None, rnd=2):
    """samples: list of flat float lists, frames 0…N. Linear keys within tol (max abs).
    wrap(values) → Lottie value (for paths)."""
    n = len(samples) - 1
    if all(_dist(s, samples[0]) < tol * 0.5 for s in samples):
        v = samples[0]
        return static(wrap(v) if wrap else [R(x, rnd) for x in v])
    keys = sorted(set([0, n] + [b for b in breaks if 0 < b < n]))
    stack = list(zip(keys[:-1], keys[1:]))[::-1]
    kept = set(keys)
    while stack:
        a, b = stack.pop()
        worst, wf = 0.0, None
        for f in range(a + 1, b):
            u = (f - a) / (b - a)
            e = max(abs(samples[a][i] + (samples[b][i] - samples[a][i]) * u - samples[f][i]) for i in range(len(samples[f])))
            if e > worst:
                worst, wf = e, f
        if worst > tol and wf is not None:
            kept.add(wf)
            stack.append((wf, b))
            stack.append((a, wf))
    ks = sorted(kept)
    out = []
    for idx, f in enumerate(ks):
        v = wrap(samples[f]) if wrap else [R(x, rnd) for x in samples[f]]
        k = {"t": f, "s": v if wrap is None else [v]}
        if idx < len(ks) - 1:
            k["o"] = {"x": 0, "y": 0} if wrap is None else {"x": 0, "y": 0}
            k["i"] = {"x": 1, "y": 1}
        out.append(k)
    return {"a": 1, "k": out}


def path_value(v, i, o, closed, n=2):
    return {"c": closed, "v": [[R(a, n), R(b, n)] for a, b in v], "i": [[R(a, n), R(b, n)] for a, b in i], "o": [[R(a, n), R(b, n)] for a, b in o]}


def transform(p=None, r=None, s=None, o=None, a=None):
    return {
        "o": o if o is not None else static(100),
        "r": r if r is not None else static(0),
        "p": p if p is not None else static([0, 0, 0]),
        "a": a if a is not None else static([0, 0, 0]),
        "s": s if s is not None else static([100, 100, 100]),
    }


def group_tr(p=(0, 0), a=(0, 0), s=None, r=None, o=None):
    return {"ty": "tr", "p": static(list(p)), "a": static(list(a)), "s": s if s is not None else static([100, 100]),
            "r": r if r is not None else static(0), "o": o if o is not None else static(100),
            "sk": static(0), "sa": static(0), "nm": "tr"}


def shape_path(path, nm="path"):
    if isinstance(path, dict) and "a" in path:
        ks = path
    else:
        ks = static(path_value(path["v"], path["i"], path["o"], path["c"], 1))
    return {"ty": "sh", "nm": nm, "ks": ks}


def fill(color, nm="fill", opacity=100):
    return {"ty": "fl", "nm": nm, "c": static(list(color) + [1]), "o": static(opacity), "r": 1}


def stroke(color, width, nm="stroke", opacity=100, cap=2, join=2):
    return {"ty": "st", "nm": nm, "c": static(list(color) + [1]), "o": static(opacity), "w": width if isinstance(width, dict) else static(width),
            "lc": cap, "lj": join, "ml": 4}


def gradient_stroke(stops, width, start, end, nm="gradient", cap=2, join=2):
    """stops: [(offset, (r,g,b))]; start/end: static or animated point properties."""
    k = []
    for off, c in stops:
        k += [off] + list(c)
    return {"ty": "gs", "nm": nm, "o": static(100), "w": width if isinstance(width, dict) else static(width),
            "g": {"p": len(stops), "k": static(k)}, "s": start, "e": end, "t": 1, "lc": cap, "lj": join, "ml": 4}


def trim(start, end, offset=None, nm="trim"):
    return {"ty": "tm", "nm": nm, "s": start if isinstance(start, dict) else static(start), "e": end if isinstance(end, dict) else static(end),
            "o": offset if offset is not None else static(0), "m": 1}


def group(nm, items, tr=None):
    return {"ty": "gr", "nm": nm, "it": items + [tr or group_tr()]}


def layer(ind, nm, ty, n_frames, ks, shapes=None, parent=None, opacity=None):
    lay = {"ddd": 0, "ind": ind, "ty": ty, "nm": nm, "sr": 1, "ks": ks, "ao": 0, "ip": 0, "op": n_frames, "st": 0, "bm": 0}
    if shapes is not None:
        lay["shapes"] = shapes
    if parent is not None:
        lay["parent"] = parent
    return lay


def hex_rgb(h):
    h = h.lstrip("#")
    return tuple(round(int(h[i:i + 2], 16) / 255, 4) for i in (0, 2, 4))
