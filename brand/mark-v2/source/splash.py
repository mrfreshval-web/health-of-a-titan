"""Splash idea: the pulse draws across the medallion left->right and 'ignites' what it passes
(columns), the titan lights up when the pulse reaches him, then a warm halo breathes at a calm rhythm.
Writes static key frames and an animated SVG preview (SMIL)."""
import os, re
HERE = os.path.dirname(os.path.abspath(__file__))
os.chdir(HERE)
import compose as C
O = os.path.join(HERE, 'out')
VB = (54, 50, 1940, 1940)
BASE = C.build(viewbox=VB, size=(1024, 1024), extra=3, extra_fig=2.4, pulse_extra=1.8, circle_w=C.G['circleW']+3,
               title='HoT splash')
HALO = ('<radialGradient id="spHaloG" cx="50%" cy="50%" r="50%"><stop offset="0" stop-color="#E2A847" stop-opacity=".55"/>'
        '<stop offset=".55" stop-color="#E2A847" stop-opacity=".16"/><stop offset="1" stop-color="#E2A847" stop-opacity="0"/></radialGradient>')


def frame(sweep, circle_op, titan_op, halo_op, anim=False):
    s = BASE
    x0 = VB[0]
    rect_w = max(0.1, sweep - x0)
    anim_rect = anim_titan = anim_halo = anim_circle = ''
    if anim:
        anim_rect = ('<animate attributeName="width" values="0.1;0.1;1950" keyTimes="0;0.15;1" dur="1.1s" fill="freeze" '
                     'calcMode="spline" keySplines="0 0 1 1;0.45 0 0.25 1"/>')
        anim_titan = '<animate attributeName="opacity" values="0;0;1" keyTimes="0;0.45;1" dur="1.25s" fill="freeze"/>'
        anim_circle = '<animate attributeName="opacity" values="0.2;1" dur="0.9s" fill="freeze"/>'
        # after the 1 s reveal the halo breathes: 1.2 s per cycle (= 50 per minute, a calm resting rhythm)
        anim_halo = ('<animate attributeName="opacity" values=".25;.9;.25" keyTimes="0;.3;1" dur="1.2s" begin="1s" '
                     'repeatCount="indefinite" calcMode="spline" keySplines="0.3 0 0.2 1;0.4 0 0.4 1"/>')
    defs = (f'<clipPath id="spReveal"><rect x="{x0}" y="0" width="{C.f2(rect_w)}" height="2100">{anim_rect}</rect></clipPath>' + HALO)
    s = s.replace('<defs>', '<defs>' + defs, 1)
    halo = (f'<ellipse cx="1024" cy="1060" rx="560" ry="800" fill="url(#spHaloG)" opacity="{halo_op}">{anim_halo}</ellipse>')
    s = s.replace('<g id="hot-circle">', halo + f'<g id="hot-circle" opacity="{circle_op}">{anim_circle}', 1)
    s = s.replace('<g id="hot-pulse">', '<g id="hot-pulse" clip-path="url(#spReveal)">', 1)
    s = s.replace('<g id="hot-columns">', '<g id="hot-columns" clip-path="url(#spReveal)">', 1)
    s = s.replace('<g id="hot-titan">', f'<g id="hot-titan" opacity="{titan_op}">{anim_titan}', 1)
    return s


FRAMES = [
    ('0', 'splash-f1-000ms.svg', dict(sweep=54, circle_op=.22, titan_op=0, halo_op=0)),
    ('350', 'splash-f2-350ms.svg', dict(sweep=780, circle_op=.55, titan_op=0, halo_op=0)),
    ('700', 'splash-f3-700ms.svg', dict(sweep=1585, circle_op=.85, titan_op=.6, halo_op=0)),
    ('1000', 'splash-f4-1000ms.svg', dict(sweep=2000, circle_op=1, titan_op=1, halo_op=.0)),
    ('1150', 'splash-f5-breath.svg', dict(sweep=2000, circle_op=1, titan_op=1, halo_op=.9)),
]
for t, name, kw in FRAMES:
    open(os.path.join(O, name), 'w').write(frame(**kw))
open(os.path.join(O, 'hot-splash-animated.svg'), 'w').write(frame(54, .2, 0, 0, anim=True))
print('ok')
