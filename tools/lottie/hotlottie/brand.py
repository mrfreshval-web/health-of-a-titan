"""The brand Titan (brand/mark-v2 master, traced figure R1) as geometry for the cut-out rig."""
import os
import sys

import pathops

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..'))
MARK = os.path.join(ROOT, 'brand', 'mark-v2')
sys.path.insert(0, os.path.join(MARK, 'android'))
import svgflat as F  # noqa: E402

WHITE = {'fill': '#fff', 'stroke': '#fff'}
_doc = None


def _master():
    global _doc
    if _doc is None:
        _doc = F.Doc(os.path.join(MARK, 'master', 'hot-master-transparent.svg'))
    return _doc


def group(gid):
    m = _master()
    return F.union([g for g, _ in m.collect(m.ids[gid], dict(WHITE))])


def titan():
    """(line art, knockout) of the Titan in the master's 2048 space."""
    return group('hot-titan-art'), group('hot-titan-knockout')


if __name__ == '__main__':
    line, knock = titan()
    print('line', [round(v) for v in line.bounds], len(F.to_d(line, 1)), 'contours', len(list(line.contours)))
    print('knock', [round(v) for v in knock.bounds], len(F.to_d(knock, 1)), 'contours', len(list(knock.contours)))
    out = sys.argv[1] if len(sys.argv) > 1 else '.'
    with open(os.path.join(out, 'titan.svg'), 'w') as f:
        f.write('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 2048 2048" width="2048" height="2048">'
                '<rect width="2048" height="2048" fill="#111"/>'
                f'<path d="{F.to_d(knock, 1)}" fill="#234"/><path d="{F.to_d(line, 1)}" fill="#fc6"/>')
        for x in range(0, 2048, 50):
            f.write(f'<line x1="{x}" y1="0" x2="{x}" y2="2048" stroke="{"#f44" if x % 200 == 0 else "#333"}" stroke-width="1"/>')
        for y in range(0, 2048, 50):
            f.write(f'<line x1="0" y1="{y}" x2="2048" y2="{y}" stroke="{"#f44" if y % 200 == 0 else "#333"}" stroke-width="1"/>')
        for v in range(0, 2048, 200):
            f.write(f'<text x="{v + 3}" y="20" fill="#f66" font-size="20">{v}</text><text x="3" y="{v + 20}" fill="#f66" font-size="20">{v}</text>')
        f.write('</svg>')
