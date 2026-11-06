"""Lockups: mark + outlined wordmark (Cormorant Garamond SemiBold / Cormorant SC SemiBold, OFL)."""
import os, re
HERE = os.path.dirname(os.path.abspath(__file__))
os.chdir(HERE)
import compose as C
from textpath import text_path, cap_height
O = os.path.join(HERE, 'out')
f2 = C.f2
WM_FONT, WM_W = 'CormorantGaramond[wght].ttf', 600
SC_FONT = 'CormorantSC-SemiBold.ttf'
MARK_VB = (54, 50, 1940, 1940)

THEMES = {
    'dark': dict(bg='#14171C', bg2='#14171C', text='url(#wmGold)', rule='#B48F4F',
                 grad=[(0, '#F6DA98'), (.5, '#D9AE68'), (1, '#EBC57F')], mark=dict()),
    'ivory': dict(bg='#F4EFE4', bg2='#F4EFE4', text='url(#wmGold)', rule='#9C7A3C',
                  grad=[(0, '#9A7330'), (.5, '#7D5A21'), (1, '#8E6A2B')], mark=dict(mono='url(#hotGoldDeep)', knock='#F4EFE4', bg=None)),
}


def mark_svg(theme, x, y, size, extra=4):
    t = THEMES[theme]
    kw = dict(viewbox=MARK_VB, size=(size, size), extra=extra, extra_fig=extra*0.8, pulse_extra=extra*0.6,
              circle_w=C.G['circleW']+extra)
    if theme == 'dark':
        kw.update(bg=None, knock=t['bg'])
    else:
        kw.update(t['mark'])
        kw.update(highlight=False, glow=False)
    s = C.build(**kw)
    s = s.replace('<svg ', f'<svg x="{f2(x)}" y="{f2(y)}" ', 1)
    s = re.sub(r'<title>.*?</title>', '', s)
    return s


def pulse_rule(x0, x1, y, color, w=2.0, blip=34):
    """hairline with a single ECG complex in the middle — the pulse of the master, abstracted."""
    cx = (x0+x1)/2
    k = blip/100
    pts = [(x0, y), (cx-60*k, y), (cx-48*k, y-22*k), (cx-28*k, y+30*k), (cx-8*k, y-100*k), (cx+12*k, y+70*k),
           (cx+32*k, y-34*k), (cx+44*k, y), (x1, y)]
    d = 'M' + ' L'.join(f'{f2(a)} {f2(b)}' for a, b in pts)
    return (f'<path d="{d}" fill="none" stroke="{color}" stroke-width="{w}" stroke-linejoin="round" stroke-linecap="round"/>')


def doc(w, h, theme, inner, title, bg=True):
    t = THEMES[theme]
    grad = ''.join(f'<stop offset="{o}" stop-color="{c}"/>' for o, c in t['grad'])
    back = ''
    if bg:
        back = (f'<radialGradient id="wmBg" cx="50%" cy="45%" r="75%"><stop offset="0" stop-color="{t["bg2"]}"/>'
                f'<stop offset="1" stop-color="{t["bg"]}"/></radialGradient>')
    s = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" width="{w}" height="{h}"><title>{title}</title>'
         f'<defs><linearGradient id="wmGold" x1="0" y1="0" x2="0" y2="1">{grad}</linearGradient>{back}</defs>')
    if bg:
        s += f'<rect width="{w}" height="{h}" fill="url(#wmBg)"/>'
    return s + inner + '</svg>'


def wordmark(x, y, size, theme, anchor='start', tracking=.2, text='HEALTH OF A TITAN'):
    d, wdt = text_path(text, WM_FONT, size, x, y, tracking=tracking, wght=WM_W, anchor=anchor)
    return f'<path d="{d}" fill="{THEMES[theme]["text"]}"/>', wdt


def build_all():
    out = {}
    for th in THEMES:
        col = THEMES[th]['rule']
        # 1. horizontal lockup
        W, H = 2600, 760
        m = 600
        mx, my = 70, 80
        size = 132
        ch = cap_height(WM_FONT, size, WM_W)
        tx = mx + m + 90
        base = H/2 + ch/2 - 18
        wm, wdt = wordmark(tx, base, size, th)
        rule = pulse_rule(tx, tx + wdt, base + 80, col, w=2.4, blip=40)
        W = int(tx + wdt + 90)
        inner = mark_svg(th, mx, my, m) + wm + rule
        out[f'hot-lockup-horizontal-{th}.svg'] = doc(W, H, th, inner, f'Health of a Titan — horizontal lockup ({th})')
        # 2. stacked lockup
        W, H = 1800, 1720
        m = 1180
        inner = mark_svg(th, (W-m)/2, 90, m)
        size = 118
        wm, wdt = wordmark(W/2, 90+m+190, size, th, anchor='middle', tracking=.22)
        inner += wm + pulse_rule(W/2 - wdt/2, W/2 + wdt/2, 90+m+300, col, w=2.4, blip=40)
        out[f'hot-lockup-stacked-{th}.svg'] = doc(W, H, th, inner, f'Health of a Titan — stacked lockup ({th})')
        # 3. short lockup HoT
        W, H = 1500, 760
        m = 600
        HS = 310
        tx = 70 + m + 90
        ch = cap_height(SC_FONT, HS)
        base = H/2 + ch/2 - 30
        d, wdt = text_path('HoT', SC_FONT, HS, tx, base, tracking=.12)
        W = int(tx + wdt + 110)
        inner = (mark_svg(th, 70, 80, m) + f'<path d="{d}" fill="{THEMES[th]["text"]}"/>' +
                 pulse_rule(tx, tx + wdt, base + 75, col, w=2.4, blip=40))
        out[f'hot-lockup-short-{th}.svg'] = doc(W, H, th, inner, f'HoT — short lockup ({th})')
        # 4. wordmark alone (transparent)
        size = 120
        wm, wdt = wordmark(10, 10 + cap_height(WM_FONT, size, WM_W), size, th)
        out[f'hot-wordmark-{th}.svg'] = doc(int(wdt + 20), int(cap_height(WM_FONT, size, WM_W) + 20), th, wm,
                                            f'Health of a Titan — wordmark ({th})', bg=False)
    for k, v in out.items():
        open(os.path.join(O, k), 'w').write(v)
    return out


if __name__ == '__main__':
    print(list(build_all()))
