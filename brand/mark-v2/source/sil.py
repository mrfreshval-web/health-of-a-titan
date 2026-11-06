"""Solid silhouettes for the monochrome (themed) icon and the notification icon.
colSil: left column filled (capital + shaft + base, base clipped to the circle like the master)
figSolid: the titan filled, slightly emboldened."""
from PIL import Image, ImageDraw
import numpy as np, os, json
from scipy import ndimage as ndi
os.chdir(os.path.dirname(os.path.abspath(__file__)))
import trace as _t  # noqa: re-uses helpers (runs trace once; acceptable)
S = 2
G = json.load(open('geom.json'))
cap = _t.clean(_t.load('m_cap.png'), drop=lambda p: p.bbox[3] < 345*S)
base = _t.clean(_t.load('m_base.png'))
H, W = cap.shape
img = Image.new('L', (W, H), 0)
d = ImageDraw.Draw(img)
CX = G['colL']; y0, y1 = G['shaftY']; eT, eB = G['edge']; hw = G['edgeW']/2
d.polygon([((CX-eT-hw)*S, (y0-4)*S), ((CX+eT+hw)*S, (y0-4)*S), ((CX+eB+hw)*S, (y1+4)*S), ((CX-eB-hw)*S, (y1+4)*S)], fill=255)
col = cap | base | (np.asarray(img) > 0)
col = ndi.binary_closing(col, structure=np.ones((3, 3)), iterations=8)
col = ndi.binary_fill_holes(col)
# clip base by the circle as in the master
yy, xx = np.mgrid[0:H, 0:W]
cx, cy, r, _ = G['circle']
col &= ~((np.hypot(xx/S-cx, yy/S-cy) > r) & (yy > 1600*S))
col = ndi.binary_opening(col, iterations=2)
fig = _t.load('m_fig.png')
figS = _t.figSilCore | fig
figS = ndi.binary_opening(figS, iterations=3)
Image.fromarray(((~col)*255).astype(np.uint8)).resize((1024, 1024)).save('v_colsil.png')
res = json.load(open('traced.json'))
res['colSolid'] = _t.trace(col, 'colSolid', filter_speckle=40)
res['figSolid'] = _t.trace(figS, 'figSolid', filter_speckle=40)
json.dump(res, open('traced.json', 'w'))
print({k: len(v) for k, v in res.items()})
