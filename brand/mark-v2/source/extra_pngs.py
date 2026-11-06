"""Render lockup PNGs at native size and the 'visible 48 px' launcher icon."""
import os, re, subprocess, sys
from PIL import Image
M = sys.argv[1]
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'render.js')
for f in sorted(os.listdir(os.path.join(M, 'lockup'))):
    if not f.endswith('.svg'):
        continue
    p = os.path.join(M, 'lockup', f)
    s = open(p).read()
    w, h = re.search(r'width="([\d.]+)" height="([\d.]+)"', s).groups()
    subprocess.run(['node', R, p, p[:-4] + '.png', str(int(float(w))), str(int(float(h))), '1'], check=True)
# 48 px as seen in a launcher: the 108 dp canvas rendered at 72 px, centre 48 px kept, circle mask
full = os.path.join(M, 'icon', 'ic_launcher_full.svg')
tmp = os.path.join(M, 'icon', '_tmp72.png')
subprocess.run(['node', R, full, tmp, '72', '72', '1'], check=True)
im = Image.open(tmp).convert('RGBA').crop((12, 12, 60, 60))
big = Image.new('L', (480, 480), 0)
from PIL import ImageDraw
ImageDraw.Draw(big).ellipse((0, 0, 479, 479), fill=255)
im.putalpha(big.resize((48, 48), Image.LANCZOS))
im.save(os.path.join(M, 'icon', 'ic_launcher-48-visible.png'))
os.remove(tmp)
print('ok')
