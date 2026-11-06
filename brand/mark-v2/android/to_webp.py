import os
import sys
from PIL import Image

src, res = sys.argv[1], sys.argv[2]
for d in ('mdpi', 'hdpi', 'xhdpi', 'xxhdpi', 'xxxhdpi'):
    out = os.path.join(res, f'mipmap-{d}')
    os.makedirs(out, exist_ok=True)
    p = os.path.join(out, 'ic_launcher_foreground.webp')
    Image.open(os.path.join(src, f'{d}.png')).convert('RGBA').save(p, 'WEBP', lossless=True, quality=100, method=6)
    print(p, os.path.getsize(p))
