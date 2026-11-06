"""Split the R1 line mask into parts:
- capital (symmetrised 4-way), base (symmetrised 2-way) for the LEFT column
- figure (everything not explained by geometry: circle, pulse, column shafts, capitals, bases)
Writes masks at 4096 px (2x of the 2048 art board). Ink = black."""
from PIL import Image, ImageDraw, ImageFilter
import numpy as np, os, json
from scipy import ndimage as ndi
from skimage.measure import label, regionprops
os.chdir(os.path.dirname(os.path.abspath(__file__)))
S = 2
m = np.asarray(Image.open('mask.png')) < 128
H, W = m.shape
G = json.load(open('geom.json'))
AX = G['axis']        # global mirror axis (2048 units)
CX = G['colL']        # left column axis


def mirror(a, axis):
    """mirror float array about vertical line x=axis (2048 units)."""
    f = a[:, ::-1].astype(float)
    # flipped pixel i holds original pixel W-1-i, centre at (W-1)/2 in 4096 px
    shift = 2*axis*S - (W-1)
    return ndi.shift(f, (0, shift), order=1, mode='constant')


yy, xx = np.mgrid[0:H, 0:W]
_cx, _cy, _r, _ = G['circle']
_dist = np.hypot(xx/S - _cx, yy/S - _cy)
ring = np.abs(_dist - _r) < G['circleW']/2 + 3
fm = (m & ~ring).astype(float)
Rm = mirror(fm, AX)  # right column brought onto the left


fm_raw = m.astype(float)
Rm_raw = mirror(fm_raw, AX)


def sym_part(y0, y1, x0, x1, self_sym, raw=False):
    box = np.zeros_like(fm)
    box[y0*S:y1*S, x0*S:x1*S] = 1
    parts = [fm_raw*box, Rm_raw*box] if raw else [fm*box, Rm*box]
    if self_sym:
        parts += [mirror(p, CX)*box for p in parts]
    av = sum(parts)/len(parts)
    av = ndi.gaussian_filter(av, 1.2)
    return av > 0.5


cap = sym_part(*G['capBox'], False, raw=True)
base = sym_part(*G['baseBox'], False)
Image.fromarray(((~cap)*255).astype(np.uint8)).save('m_cap.png')
Image.fromarray(((~base)*255).astype(np.uint8)).save('m_base.png')

# ---- geometry raster to subtract (generous) ----
geo = Image.new('L', (W, H), 0)
d = ImageDraw.Draw(geo)
cx, cy, r, cw = G['circle']
rr = r*S
d.ellipse([cx*S-rr-cw*S, cy*S-rr-cw*S, cx*S+rr+cw*S, cy*S+rr+cw*S], outline=255, width=int(cw*2*S))
for seg in G['pulse']:
    pts = [(x*S, y*S) for x, y in seg['pts']]
    d.line(pts, fill=255, width=int((G['pulseW']+6)*S), joint='curve')
# columns: shafts as polygons covering whole shaft area between outer edges, + boxes
for side in (0, 1):
    def X(x):
        return x if side == 0 else 2*AX - x
    y0, y1 = G['shaftY']
    eT, eB = G['edge']
    poly = [(X(CX-eT-8), y0), (X(CX+eT+8), y0), (X(CX+eB+8), y1), (X(CX-eB-8), y1)]
    d.polygon([(x*S, y*S) for x, y in poly], fill=255)
    for key in ('capBox', 'baseBox'):
        y0b, y1b, x0b, x1b = G[key]
        xs = sorted([X(x0b), X(x1b)])
        d.rectangle([xs[0]*S, y0b*S, xs[1]*S, y1b*S], fill=255)
geo = np.asarray(geo) > 0
keep = np.zeros_like(m)
for (x0, y0, x1, y1) in G['handBoxes']:   # areas where figure ink overlaps column: keep everything
    keep[y0*S:y1*S, x0*S:x1*S] = True
fig0 = m & ~geo
near = ndi.binary_dilation(fig0, iterations=int(5*S))
fig = fig0 | (m & keep)
# hand boxes: the column's inner edge runs through them. Drop the edge-line pixels (the geometric shaft
# draws them) except on rows where the hand outline actually crosses the edge.
eimg = Image.new('L', (W, H), 0)
ed = ImageDraw.Draw(eimg)
y0s, y1s = G['shaftY']
def _e(y):
    return G['edge'][0] + (G['edge'][1]-G['edge'][0])*(y-y0s)/(y1s-y0s)
for (hx0, hy0, hx1, hy1) in G['handBoxes'][2:]:
    for sgn, axc in ((1, CX), (-1, 2*AX-CX)):
        xa, xb = axc + sgn*_e(hy0), axc + sgn*_e(hy1)
        if hx0 <= xa <= hx1:
            ed.line([(xa*S, hy0*S), (xb*S, hy1*S)], fill=255, width=int((G['edgeW']+5)*S))
strip = np.asarray(eimg) > 0
thumbs = np.zeros_like(m)
for (x0, y0, x1, y1) in G['handBoxes'][:2]:
    thumbs[y0*S:y1*S, x0*S:x1*S] = True
ys, xs = np.nonzero(strip & ~thumbs)
drop = np.zeros_like(m)
for y in np.unique(ys):
    xr = np.sort(xs[ys == y])
    for grp in np.split(xr, np.where(np.diff(xr) > 1)[0]+1):
        a, b = grp[0], grp[-1]
        if not (fig0[y, max(a-12, 0):a].any() and fig0[y, b+1:b+13].any()):
            drop[y, a:b+1] = True
fig &= ~drop
# remove specks left over
lab = label(fig)
out = np.zeros_like(fig)
for p in regionprops(lab):
    if p.area > 60:
        out[lab == p.label] = True
Image.fromarray(((~out)*255).astype(np.uint8)).save('m_fig.png')
Image.fromarray(((~geo)*255).astype(np.uint8)).save('m_geo.png')
print('ok', cap.sum(), base.sum(), out.sum())
