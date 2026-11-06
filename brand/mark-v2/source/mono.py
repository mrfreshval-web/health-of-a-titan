"""Monochrome glyphs: themed icon (Android 13+, 108 dp layer) and notification small icon (24 dp).
Solid silhouettes cut from the master: titan silhouette traced from R1, columns drawn geometrically
on R1's measurements (abacus, volutes, bell, astragal, entasis shaft, base)."""
import os, json
HERE = os.path.dirname(os.path.abspath(__file__))
os.chdir(HERE)
import compose as C
G = C.G
f2 = C.f2
O = os.path.join(HERE, 'out')
CX = G['colL']
eT, eB = G['edge']; hw = G['edgeW']/2
y0, y1 = G['shaftY']


def column_solid(flute_cut=0.0):
    p = []
    p.append('M338 313H598V324L576 343H360L338 324Z')
    p.append('M346 381a40 40 0 1 0 80 0a40 40 0 1 0 -80 0Z')
    p.append('M510 381a40 40 0 1 0 80 0a40 40 0 1 0 -80 0Z')
    p.append('M398 343H538L526 488H410Z')
    p.append('M409 484H527a12 12 0 0 1 0 24H409a12 12 0 0 1 0 -24Z')
    p.append(f'M{f2(CX-eT-hw)} 500H{f2(CX+eT+hw)}L{f2(CX+eB+hw)} 1680H{f2(CX-eB-hw)}Z')
    p.append('M395 1676H541a13 13 0 0 1 0 26H395a13 13 0 0 1 0 -26Z')
    p.append('M392 1700H544V1716H392Z')
    p.append('M391 1714H545a13 13 0 0 1 0 26H391a13 13 0 0 1 0 -26Z')
    p.append('M366 1738H570V1786H366Z')
    s = ''.join(f'<path d="{q}"/>' for q in p)
    cuts = ''
    if flute_cut:
        # two flutes engraved as negative lines (cut with a mask)
        for sgn in (-1, 1):
            xt, xb = CX + sgn*21, CX + sgn*26
            cuts += f'<line x1="{f2(xt)}" y1="540" x2="{f2(xb)}" y2="1640" stroke="#000" stroke-width="{flute_cut}" stroke-linecap="round"/>'
    return s, cuts


def glyph(viewbox, size, color, flute_cut=0, pulse=False, pulse_w=0, ring=None, title='', fig_key='figSolid'):
    col, cuts = column_solid(flute_cut)
    colR = C.mirror(col)
    cutsR = C.mirror(cuts) if cuts else ''
    fig = ''.join(f'<path d="{C.bake(d, tx, ty)}"/>' for d, tx, ty in C.T[fig_key])
    mask = ''
    body_cols = f'<g>{col}{colR}</g>'
    if cuts:
        vb = viewbox
        mask = (f'<mask id="m" maskUnits="userSpaceOnUse" x="{f2(vb[0])}" y="{f2(vb[1])}" width="{f2(vb[2])}" height="{f2(vb[3])}">'
                f'<rect x="{f2(vb[0])}" y="{f2(vb[1])}" width="{f2(vb[2])}" height="{f2(vb[3])}" fill="#fff"/>{cuts}{cutsR}'
                f'<g fill="#000">{fig}</g></mask>')
        body_cols = f'<g mask="url(#m)">{col}{colR}</g>'
    extra = ''
    if pulse:
        segs = []
        for seg in G['pulse']:
            segs.append('M' + ' L'.join(f'{f2(x)} {f2(y)}' for x, y in seg['pts']))
        # pulse only outside the columns (between them the titan's arms are the crossbar of the H)
        extra += (f'<path d="{segs[0]} {segs[3]}" fill="none" stroke="{color}" stroke-width="{pulse_w}" '
                  'stroke-linejoin="round" stroke-linecap="round"/>')
    if ring:
        rw, rr = ring
        extra += f'<circle cx="{f2(G["circle"][0])}" cy="{f2(G["circle"][1])}" r="{rr}" fill="none" stroke="{color}" stroke-width="{rw}"/>'
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{" ".join(f2(v) for v in viewbox)}" width="{size}" height="{size}">'
            f'<title>{title}</title><defs>{mask}</defs><g fill="{color}">{body_cols}<g>{fig}</g></g>{extra}</svg>')


ig = json.load(open(f'{O}/icon-geom.json'))
VB = ig['VB']
# themed icon: same frame as the launcher icon
open(f'{O}/ic_launcher_monochrome.svg', 'w').write(glyph(VB, 108, '#000000', flute_cut=16, pulse=True, pulse_w=30,
                                                       ring=(24, G['circle'][2]), title='HoT themed icon — monochrome layer'))
# notification: 24 dp, art fills 22 dp height, white on transparent
bx0, by0, bx1, by1 = 330, 300, 1718, 1790
h = by1 - by0
U = h * 24 / 22
cxm = (bx0 + bx1)/2
VBN = (cxm - U/2, by0 - U*1/24, U, U)
open(f'{O}/ic_stat_hot.svg', 'w').write(glyph(VBN, 24, '#FFFFFF', title='HoT notification icon (24 dp)'))
print('ok', VBN)
