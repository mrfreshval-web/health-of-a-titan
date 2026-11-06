from PIL import Image, ImageFilter
import numpy as np, sys, os
os.chdir(os.path.dirname(os.path.abspath(__file__)))
src = sys.argv[2] if len(sys.argv) > 2 else 'R1-atlas-drapery-circle.jpg'
out = sys.argv[3] if len(sys.argv) > 3 else 'mask.png'
im = Image.open(src).convert('RGB')
a = np.asarray(im).astype(float)
L = 0.45*a[..., 0] + 0.45*a[..., 1] + 0.1*a[..., 2]
Li = Image.fromarray(np.clip(L, 0, 255).astype(np.uint8))
S = 2
N = im.size[0]
Lb = Li.resize((N*S, N*S), Image.BICUBIC).filter(ImageFilter.GaussianBlur(1.2))
b = np.asarray(Lb).astype(float)
bg = np.asarray(Li.filter(ImageFilter.MinFilter(9)).filter(ImageFilter.GaussianBlur(20)).resize((N*S, N*S), Image.BILINEAR)).astype(float)
thr = float(sys.argv[1]) if len(sys.argv) > 1 else 0.5
m = (b > bg + thr*np.clip(150-bg, 40, None))
Image.fromarray(((~m)*255).astype(np.uint8)).save(out)
print(m.mean())
