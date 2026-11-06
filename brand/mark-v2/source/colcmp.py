"""Compare line colour and glow of a render against R1 on the same pixels."""
from PIL import Image
import numpy as np, os, sys
from scipy import ndimage as ndi
os.chdir(os.path.dirname(os.path.abspath(__file__)))
r1 = np.asarray(Image.open('R1-atlas-drapery-circle.jpg').convert('RGB')).astype(float)
me = np.asarray(Image.open(sys.argv[1]).convert('RGB').resize((2048, 2048))).astype(float)
m = np.asarray(Image.open('mask.png').resize((2048, 2048))) < 128
core = ndi.binary_erosion(m, iterations=2)
d = ndi.distance_transform_edt(~m)
B = 512
for by in range(0, 2048, B):
    row = []
    for bx in range(0, 2048, B):
        sel = core[by:by+B, bx:bx+B]
        if sel.sum() > 200:
            a = np.median(r1[by:by+B, bx:bx+B][sel], axis=0).astype(int)
            b = np.median(me[by:by+B, bx:bx+B][sel], axis=0).astype(int)
            row.append('%02x%02x%02x/%02x%02x%02x' % (*a, *b))
        else:
            row.append('-------------')
    print(by, ' '.join(row))
for name, (x0, y0, x1, y1) in {'fig': (820, 500, 1230, 1950), 'col': (250, 520, 380, 1650)}.items():
    zone = np.zeros_like(m); zone[y0:y1, x0:x1] = True
    out = []
    for dd in (1, 3, 6, 10, 15, 22, 30, 45, 60):
        sel = zone & (d >= dd) & (d < dd+2)
        out.append('%d:%d/%d' % (dd, np.median(r1[sel].mean(1)), np.median(me[sel].mean(1))))
    print(name, ' '.join(out))
yy, xx = np.mgrid[0:2048, 0:2048]
r = np.hypot(xx-1024, yy-1024)
print('bg', ' '.join('%d:%d/%d' % (lo, np.median(r1[(r >= lo) & (r < lo+100) & (d > 70)].mean(1)), np.median(me[(r >= lo) & (r < lo+100) & (d > 70)].mean(1))) for lo in range(200, 1400, 200)))
