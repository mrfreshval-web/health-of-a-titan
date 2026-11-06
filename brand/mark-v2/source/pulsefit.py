"""Refine pulse vertices so the polyline runs along the centre of R1's ink (max of inner EDT)."""
from PIL import Image
import numpy as np, json, os
from scipy import ndimage as ndi
os.chdir(os.path.dirname(os.path.abspath(__file__)))
S = 2
m = np.asarray(Image.open('mask.png')) < 128
edt = ndi.distance_transform_edt(m)
edt = ndi.gaussian_filter(edt, 1.0)
G = json.load(open('geom.json'))


def score(pts):
    s = 0.0
    for (x0, y0), (x1, y1) in zip(pts[:-1], pts[1:]):
        n = int(max(abs(x1-x0), abs(y1-y0))*S)+1
        t = np.linspace(0.05, 0.95, n)
        xs = ((x0+(x1-x0)*t)*S).astype(int)
        ys = ((y0+(y1-y0)*t)*S).astype(int)
        s += edt[ys, xs].mean() * np.hypot(x1-x0, y1-y0)
    return s / sum(np.hypot(b[0]-a[0], b[1]-a[1]) for a, b in zip(pts[:-1], pts[1:]))


for seg in G['pulse']:
    P = [list(map(float, p)) for p in seg['pts']]
    for it in range(6):
        for i in range(1, len(P)-1):
            best = (score(P), P[i][0], P[i][1])
            for dx in np.arange(-12, 12.5, 1.0 if it < 2 else 0.5):
                for dy in np.arange(-10, 10.5, 1.0 if it < 2 else 0.5):
                    Q = [p[:] for p in P]
                    Q[i] = [P[i][0]+dx, P[i][1]+dy]
                    sc = score(Q)
                    if sc > best[0]:
                        best = (sc, Q[i][0], Q[i][1])
            P[i] = [best[1], best[2]]
    print(round(score(P), 2), [[round(x, 1), round(y, 1)] for x, y in P])
    seg['pts'] = [[round(x, 1), round(y, 1)] for x, y in P]
json.dump(G, open('geom_fit.json', 'w'), indent=1)
