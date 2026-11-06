"""Trace part masks with vtracer (binary, spline) into path data in 2048-unit coordinates.
Also builds occluder silhouettes (capital, figure) and filters stray specks."""
from PIL import Image
import numpy as np, os, re, json, vtracer
from scipy import ndimage as ndi
from skimage.measure import label, regionprops
os.chdir(os.path.dirname(os.path.abspath(__file__)))
S = 2


def load(p):
    return np.asarray(Image.open(p)) < 128


def clean(mask, min_area=300, drop=None):
    lab = label(mask)
    out = np.zeros_like(mask)
    for p in regionprops(lab):
        if p.area < min_area:
            continue
        if drop and drop(p):
            continue
        out[lab == p.label] = True
    return out


def trace(mask, name, **kw):
    ys, xs = np.nonzero(mask)
    pad = 8
    y0, y1 = max(ys.min()-pad, 0), ys.max()+pad
    x0, x1 = max(xs.min()-pad, 0), xs.max()+pad
    sub = mask[y0:y1, x0:x1]
    tmp = f'_t_{name}.png'
    out = f'_t_{name}.svg'
    Image.fromarray(((~sub)*255).astype(np.uint8)).convert('RGB').save(tmp)
    args = dict(colormode='binary', mode='spline', filter_speckle=3, corner_threshold=70,
                length_threshold=4.0, splice_threshold=45, path_precision=2, max_iterations=10)
    args.update(kw)
    vtracer.convert_image_to_svg_py(tmp, out, **args)
    svg = open(out).read()
    paths = []
    for m in re.finditer(r'<path d="([^"]+)"[^>]*?transform="translate\(([-\d.]+),([-\d.]+)\)"', svg):
        d, tx, ty = m.group(1), float(m.group(2)), float(m.group(3))
        paths.append((d, tx + x0, ty + y0))
    if not paths:
        for m in re.finditer(r'<path d="([^"]+)"', svg):
            paths.append((m.group(1), x0, y0))
    return paths


def fill_sil(mask, close_r):
    st = np.ones((3, 3), bool)
    c = ndi.binary_closing(mask, structure=st, iterations=close_r)
    f = ndi.binary_fill_holes(c)
    return ndi.binary_erosion(f, iterations=2)


res = {}
cap = clean(load('m_cap.png'), drop=lambda p: p.bbox[3] < 345*S)
base = clean(load('m_base.png'))
fig = clean(load("m_fig.png"), min_area=30)
res['cap'] = trace(cap, 'cap')
res['base'] = trace(base, 'base')
res['fig'] = trace(fig, 'fig')
capSil = fill_sil(cap, 6)
# R1 leaves a dark gap (~15 px) between the circle and the capital: knock out a dilated silhouette
capSil = ndi.distance_transform_edt(~capSil) < 15*S
G = json.load(open('geom.json'))
from PIL import ImageDraw
edgeimg = Image.new('L', (fig.shape[1], fig.shape[0]), 0)
dr = ImageDraw.Draw(edgeimg)
def e(y):
    return G['edge'][0] + (G['edge'][1]-G['edge'][0])*(y-G['shaftY'][0])/(G['shaftY'][1]-G['shaftY'][0])
for (hx0, hy0, hx1, hy1) in G['handBoxes']:
    for sgn, ax in ((1, G['colL']), (-1, 2*G['axis']-G['colL'])):
        x0e, x1e = ax + sgn*e(hy0), ax + sgn*e(hy1)
        if hx0 <= x0e <= hx1:
            dr.line([(x0e*S, hy0*S), (x1e*S, hy1*S)], fill=255, width=int(G['edgeW']*S))
figE = fig | (np.asarray(edgeimg) > 0)
figSil = fill_sil(figE, 4)
strip = ndi.binary_dilation(np.asarray(edgeimg) > 0, iterations=3)
sil2 = figSil & ~strip
# re-fill the strip only on rows where the hand covers both sides of the column edge
ys, xs = np.nonzero(strip)
for y in np.unique(ys):
    xr = xs[ys == y]
    for grp in np.split(np.sort(xr), np.where(np.diff(np.sort(xr)) > 1)[0]+1):
        a, b = grp[0], grp[-1]
        if sil2[y, max(a-10, 0):a].any() and sil2[y, b+1:b+11].any():
            sil2[y, a:b+1] = True
figSil = ndi.binary_opening(sil2, iterations=2)
figSilCore = figSil.copy()   # pure titan silhouette (used for the solid monochrome glyphs)
# hand zones: R1's own ink is used there as drawn (the hand lies over flute and edge), so the
# geometric column lines underneath are knocked out across the whole zone
for (hx0, hy0, hx1, hy1) in G['handBoxes']:
    figSil[hy0*S:hy1*S, hx0*S:hx1*S] = True
Image.fromarray(((~figSil)*255).astype(np.uint8)).resize((1024, 1024)).save('v_figsil.png')
res['capSil'] = trace(capSil, 'capSil', filter_speckle=40)
res['figSil'] = trace(figSil, 'figSil', filter_speckle=40)
json.dump(res, open('traced.json', 'w'))
for k, v in res.items():
    print(k, len(v), sum(len(d) for d, _, _ in v))
