"""Compose HoT mark SVGs from traced parts (R1) + geometric primitives.

CLI: compose.py out.svg [--flat] [--nobg] [--mono COLOR]
Module: build(**opts) -> svg string (used by derive.py for icons / lockups)."""
import json, os, sys, re
HERE = os.path.dirname(os.path.abspath(__file__))
G = json.load(open(os.path.join(HERE, 'geom.json')))
T = json.load(open(os.path.join(HERE, 'traced.json')))
AX, CX = G['axis'], G['colL']
CCX, CCY, R, _ = G['circle']


def f2(v):
    return f'{v:.2f}'.rstrip('0').rstrip('.')


GOLD_RAMP = [(0, '#F4D48A'), (.22, '#EFCB80'), (.36, '#E4BB72'), (.46, '#D6A964'), (.56, '#D1A35F'),
             (.68, '#D6A862'), (.8, '#CF9F5A'), (.92, '#E9C078'), (1, '#EDC67E')]
GLOW = '#E2A847'
# the titan is lit brighter than the architecture in R1 (head/shoulders and feet catch the light)
GOLD_HI = [(0, '#FAE09C'), (.1, '#E9C379'), (.24, '#E0B66E'), (.42, '#ECC57F'), (.6, '#E4BA74'), (.8, '#E8BF78'), (1, '#F6D894')]
PULSE = '#BC9358'
SHADE = ('#7A4810', .46, .2)
BG = [(0, '#2A2D32'), (.18, '#272A2F'), (.45, '#212328'), (.73, '#1A1D22'), (.91, '#15181D'), (1, '#13161B')]


def bake(d, tx, ty):
    """apply translate(tx,ty) then scale(0.5) to vtracer absolute path data (M/L/C/Z only)."""
    toks = re.findall(r'[A-Za-z]|-?\d*\.?\d+(?:e-?\d+)?', d)
    res, coord = [], 0
    for t in toks:
        if t.isalpha():
            if t not in 'MLCZ':
                raise ValueError('unexpected command ' + t)
            res.append(t); coord = 0
        else:
            v = (float(t) + (tx if coord % 2 == 0 else ty)) * 0.5
            res.append(f2(round(v, 2)))
            coord += 1
    return re.sub(r' ?([MLCZ]) ?', r'\1', ' '.join(res))


def path_bbox_area(d):
    nums = [float(v) for v in re.findall(r'-?\d*\.?\d+', d)]
    xs, ys = nums[0::2], nums[1::2]
    return (max(xs)-min(xs))*(max(ys)-min(ys)), (min(xs), min(ys), max(xs), max(ys))


def traced(key, min_bbox=0, attrs=''):
    ps = []
    for d, tx, ty in T[key]:
        bd = bake(d, tx, ty)
        if min_bbox and path_bbox_area(bd)[0] < min_bbox:
            continue
        ps.append(f'<path d="{bd}"/>')
    return f'<g stroke="none"{attrs}>' + ''.join(ps) + '</g>'


def shaft(flutes=None, extra=0):
    y0, y1 = G['shaftY']
    fl = G['flutes'] if flutes is None else [G['flutes'][i] if isinstance(i, int) else i for i in flutes]
    s = []
    for sgn in (-1, 1):
        s.append(f'<line x1="{f2(CX+sgn*G["edge"][0])}" y1="{y0}" x2="{f2(CX+sgn*G["edge"][1])}" y2="{y1+2}" stroke-width="{f2(G["edgeW"]+extra)}"/>')
        for top, bot in fl:
            ft, fb = 512 + extra/2, 1652 - extra/2
            xt = CX+sgn*(top + (bot-top)*(ft-y0)/(y1-y0))
            xb = CX+sgn*(top + (bot-top)*(fb-y0)/(y1-y0))
            s.append(f'<line x1="{f2(xt)}" y1="{f2(ft)}" x2="{f2(xb)}" y2="{f2(fb)}" stroke-width="{f2(G["fluteW"]+extra)}"/>')
    return '<g fill="none" stroke-linecap="round">' + ''.join(s) + '</g>'


def mirror(svg):
    return f'<g transform="translate({f2(2*AX)} 0) scale(-1 1)">{svg}</g>'


def build(out=None, glow=True, bg='gradient', mono=None, extra=0, extra_fig=None, layers=('circle', 'pulse', 'columns', 'titan'),
          viewbox=(0, 0, 2048, 2048), size=None, flutes=None, fig_min=0, cap_min=0, pulse_segs=(0, 1, 2, 3), pulse_extra=None,
          pulse_color=None, circle_w=None, highlight=True, title='Health of a Titan', occlude=True, pulse_full=False,
          circle_r=None, knock=None, bg_center=(1024, 1000), bg_r=1110, glow_scale=1.0):
    """extra: added stroke thickness (art units) for the optical-size versions."""
    ef = extra if extra_fig is None else extra_fig
    pe = extra if pulse_extra is None else pulse_extra
    gold = mono or 'url(#hotGold)'
    pcol = mono or pulse_color or PULSE
    FADE = pcol if not pcol.startswith('url') else '#8F692A'
    occl_paint = None
    if occlude and (not mono or knock):
        occl_paint = knock or ('url(#hotBg)' if bg == 'gradient' else (bg if bg and bg.startswith('#') else None))
    defs = ['<defs>']
    # deep gold for light (ivory) grounds
    defs.append('<linearGradient id="hotGoldDeep" gradientUnits="userSpaceOnUse" x1="0" y1="60" x2="0" y2="1990">'
                '<stop offset="0" stop-color="#A27A33"/><stop offset=".45" stop-color="#8A6426"/><stop offset="1" stop-color="#9A732F"/></linearGradient>')
    defs.append(f'<radialGradient id="hotBg" gradientUnits="userSpaceOnUse" cx="{bg_center[0]}" cy="{bg_center[1]}" r="{bg_r}">' +
                ''.join(f'<stop offset="{o}" stop-color="{c}"/>' for o, c in BG) + '</radialGradient>')
    defs.append('<linearGradient id="hotGold" gradientUnits="userSpaceOnUse" x1="0" y1="60" x2="0" y2="1990">' +
                ''.join(f'<stop offset="{o}" stop-color="{c}"/>' for o, c in GOLD_RAMP) + '</linearGradient>')
    defs.append('<linearGradient id="hotGoldHi" gradientUnits="userSpaceOnUse" x1="0" y1="480" x2="0" y2="1880">' +
                ''.join(f'<stop offset="{o}" stop-color="{c}"/>' for o, c in GOLD_HI) + '</linearGradient>')
    sc, s0, s1 = SHADE
    defs.append('<linearGradient id="hotShade" gradientUnits="userSpaceOnUse" x1="60" y1="0" x2="1990" y2="0">' +
                f'<stop offset="0" stop-color="{sc}" stop-opacity="{s0}"/><stop offset=".22" stop-color="{sc}" stop-opacity="{s1}"/>'
                f'<stop offset=".4" stop-color="{sc}" stop-opacity="0"/><stop offset=".6" stop-color="{sc}" stop-opacity="0"/>'
                f'<stop offset=".78" stop-color="{sc}" stop-opacity="{s1}"/><stop offset="1" stop-color="{sc}" stop-opacity="{s0}"/></linearGradient>')
    rr = circle_r or R
    defs.append(f'<clipPath id="hotInCircle"><circle cx="{f2(CCX)}" cy="{f2(CCY)}" r="{rr}"/></clipPath>')
    k = max(1.0, (viewbox[2]) / 2048 * 1.0)  # glow radii scale with view
    gs = (1 + extra/6) * glow_scale
    def glowf(fid, s1, s2, a1, a2):
        return (f'<filter id="{fid}" x="-20%" y="-20%" width="140%" height="140%" color-interpolation-filters="sRGB">'
                f'<feFlood flood-color="{GLOW}" result="c"/>'
                f'<feGaussianBlur in="SourceAlpha" stdDeviation="{f2(s1*gs)}" result="a"/>'
                f'<feGaussianBlur in="SourceAlpha" stdDeviation="{f2(s2*gs)}" result="b"/>'
                f'<feComponentTransfer in="a" result="a2"><feFuncA type="linear" slope="{a1}"/></feComponentTransfer>'
                f'<feComponentTransfer in="b" result="b2"><feFuncA type="linear" slope="{a2}"/></feComponentTransfer>'
                '<feMerge result="m"><feMergeNode in="b2"/><feMergeNode in="a2"/></feMerge>'
                '<feComposite in="c" in2="m" operator="in"/></filter>')
    defs.append(glowf('hotGlowSoft', 4, 18, .22, .12))
    defs.append(glowf('hotGlowStrong', 5, 22, .42, .32))
    # bright core along every line (polished gold) and a hairline dark rim (R1's embossed edge)
    defs.append('<filter id="hotCore" x="-5%" y="-5%" width="110%" height="110%" color-interpolation-filters="sRGB">'
                f'<feMorphology in="SourceAlpha" operator="erode" radius="{f2(1.8+extra/5)}" result="e"/>'
                f'<feGaussianBlur in="e" stdDeviation="{f2(1.1+extra/8)}" result="eb"/>'
                '<feFlood flood-color="#FFF1BE" flood-opacity=".3"/><feComposite in2="eb" operator="in"/></filter>')
    defs.append('<filter id="hotRim" x="-5%" y="-5%" width="110%" height="110%" color-interpolation-filters="sRGB">'
                f'<feMorphology in="SourceAlpha" operator="dilate" radius="{f2(1.6+extra/6)}" result="d"/>'
                '<feGaussianBlur in="d" stdDeviation="1" result="db"/>'
                '<feFlood flood-color="#0B0D10" flood-opacity=".55"/><feComposite in2="db" operator="in"/></filter>')

    # ---- symbols (geometry only, paint applied on <use>) ----
    sym = []
    if 'circle' in layers:
        cw = circle_w or (G['circleW'] + extra)
        sym.append(f'<g id="hot-circle-art"><circle cx="{f2(CCX)}" cy="{f2(CCY)}" r="{rr}" fill="none" stroke-width="{f2(cw)}"/></g>')
    if 'columns' in layers:
        colL = (shaft(flutes, extra) +
                f'<g stroke-width="{f2(extra)}" stroke-linejoin="round">{traced("cap", cap_min)}</g>'.replace('stroke="none"', '' if extra else 'stroke="none"') +
                f'<g clip-path="url(#hotInCircle)" stroke-width="{f2(extra)}" stroke-linejoin="round">{traced("base")}</g>'.replace('stroke="none"', '' if extra else 'stroke="none"'))
        sym.append(f'<g id="hot-columns-art">{colL}{mirror(colL)}</g>')
        if occl_paint:
            cs = traced('capSil')
            sym.append(f'<g id="hot-columns-knockout">{cs}{mirror(cs)}</g>')
    if 'titan' in layers:
        fl = traced('fig', fig_min)
        if ef:
            fl = fl.replace('stroke="none"', f'stroke-width="{f2(ef)}" stroke-linejoin="round"')
        sym.append(f'<g id="hot-titan-art">{fl}</g>')
        if occl_paint:
            sym.append(f'<g id="hot-titan-knockout">{traced("figSil")}</g>')
    if 'pulse' in layers:
        ps = []
        for i, seg in enumerate(G['pulse']):
            if i not in pulse_segs:
                continue
            pts = [p[:] for p in seg['pts']]
            stroke = pcol
            fade = None if pulse_full else (seg.get('fadeIn') or seg.get('fadeOut'))
            if fade:
                a, b = fade
                o0, o1 = (0, 1) if seg.get('fadeIn') else (1, 0)
                defs.append(f'<linearGradient id="hotPulseFade{i}" gradientUnits="userSpaceOnUse" x1="{a}" y1="0" x2="{b}" y2="0">'
                            f'<stop offset="0" stop-color="{FADE}" stop-opacity="{o0}"/><stop offset="1" stop-color="{FADE}" stop-opacity="{o1}"/></linearGradient>')
                stroke = f'url(#hotPulseFade{i})'
            d = 'M' + ' L'.join(f'{f2(x)} {f2(y)}' for x, y in pts)
            ps.append(f'<path d="{d}" stroke="{stroke}"/>')
        sym.append(f'<g id="hot-pulse-art" fill="none" stroke-width="{f2(G["pulseW"]+pe)}" stroke-linejoin="round" stroke-linecap="round">{"".join(ps)}</g>')
    defs += sym
    defs.append('</defs>')

    def use(i, paint, extra_attr=''):
        return f'<use href="#{i}" fill="{paint}" stroke="{paint}"{extra_attr}/>'

    def lines(i, glowf, paint=None):
        p = mono or paint or gold
        s = ''
        if glow and not mono and glowf:
            s += f'<g filter="url(#{glowf})">{use(i, p)}</g>'
        if highlight and not mono:
            s += f'<g filter="url(#hotRim)">{use(i, p)}</g>'
        s += use(i, p)
        if not mono:
            s += use(i, 'url(#hotShade)')
            if highlight:
                s += f'<g filter="url(#hotCore)">{use(i, p)}</g>'
        return s

    body = []
    vb = viewbox
    if bg == 'gradient' and not mono:
        body.append(f'<rect x="{vb[0]}" y="{vb[1]}" width="{vb[2]}" height="{vb[3]}" fill="url(#hotBg)"/>')
    elif bg and bg.startswith('#'):
        body.append(f'<rect x="{vb[0]}" y="{vb[1]}" width="{vb[2]}" height="{vb[3]}" fill="{bg}"/>')
    if 'circle' in layers:
        body.append(f'<g id="hot-circle">{lines("hot-circle-art", "hotGlowSoft")}</g>')
    if 'pulse' in layers:
        body.append(f'<g id="hot-pulse"><use href="#hot-pulse-art"/></g>')
    if 'columns' in layers:
        k = use('hot-columns-knockout', occl_paint) if occl_paint else ''
        body.append(f'<g id="hot-columns">{k}{lines("hot-columns-art", "hotGlowSoft")}</g>')
    if 'titan' in layers:
        k = use('hot-titan-knockout', occl_paint) if occl_paint else ''
        body.append(f'<g id="hot-titan">{k}{lines("hot-titan-art", "hotGlowStrong", "url(#hotGoldHi)")}</g>')
        if 'columns' in layers and occl_paint:
            # hand zones use R1's own ink; where its straight column lines are cut at the zone border,
            # the geometric column is laid back on top in a thin band so the seam disappears
            rects = ''
            for (x0, y0, x1, y1) in G['handBoxes']:
                for yy in (y0, y1):
                    rects += f'<rect x="{x0-2}" y="{yy-9}" width="{x1-x0+4}" height="18"/>'
            defs.insert(len(defs)-1, f'<clipPath id="hotSeams">{rects}</clipPath>')
            body.append(f'<g id="hot-seams" clip-path="url(#hotSeams)">{lines("hot-columns-art", None)}</g>')
    w, h = size or (vb[2], vb[3])
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" viewBox="{" ".join(f2(v) for v in vb)}" width="{f2(w)}" height="{f2(h)}">'
           f'<title>{title}</title>' + ''.join(defs) + ''.join(body) + '</svg>')
    if out:
        open(out, 'w').write(svg)
    return svg


if __name__ == '__main__':
    out = sys.argv[1]
    flat = '--flat' in sys.argv
    mono = sys.argv[sys.argv.index('--mono')+1] if '--mono' in sys.argv else None
    s = build(out, glow=not flat, highlight=not flat, bg=None if '--nobg' in sys.argv else 'gradient', mono=mono)
    print(out, len(s))
