"""side-by-side, 50% overlay and diff of rendered master vs R1. usage: compare.py render.png ref.jpg prefix [crop x0 y0 x1 y1]"""
from PIL import Image, ImageChops
import sys, os
os.chdir(os.path.dirname(os.path.abspath(__file__)))
a = Image.open(sys.argv[1]).convert('RGB').resize((2048, 2048), Image.LANCZOS)
b = Image.open(sys.argv[2]).convert('RGB')
pre = sys.argv[3]
box = tuple(map(int, sys.argv[4:8])) if len(sys.argv) > 4 else (0, 0, 2048, 2048)
a, b = a.crop(box), b.crop(box)
w, h = a.size
k = 1000/max(w, h) if max(w, h) < 400 else min(1.0, 1000/max(w, h))
A = a.resize((int(w*k), int(h*k)), Image.LANCZOS)
B = b.resize((int(w*k), int(h*k)), Image.LANCZOS)
sbs = Image.new('RGB', (A.size[0]*2+10, A.size[1]), (0, 0, 0))
sbs.paste(B, (0, 0)); sbs.paste(A, (A.size[0]+10, 0))
sbs.save(pre + '_sbs.png')
Image.blend(A, B, 0.5).save(pre + '_ov.png')
# diff: R1 lines red, ours cyan
import numpy as np
la = np.asarray(A.convert('L')).astype(float); lb = np.asarray(B.convert('L')).astype(float)
d = np.zeros(A.size[::-1] + (3,), np.uint8)
d[..., 0] = np.clip(lb*1.2, 0, 255); d[..., 1] = np.clip(la*1.2, 0, 255); d[..., 2] = np.clip(la*1.2, 0, 255)
Image.fromarray(d).save(pre + '_diff.png')
