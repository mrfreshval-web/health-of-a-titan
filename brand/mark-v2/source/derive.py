"""Derive the HoT mark system from the master (compose.build)."""
import os, json, sys
HERE = os.path.dirname(os.path.abspath(__file__))
os.chdir(HERE)
import compose as C
O = os.path.join(HERE, 'out')
os.makedirs(O, exist_ok=True)
G = C.G
f2 = C.f2

# ---------- master ----------
C.build(f'{O}/hot-master.svg', title='Health of a Titan — master (glow)')
C.build(f'{O}/hot-master-flat.svg', glow=False, highlight=False, title='Health of a Titan — master (flat)')
C.build(f'{O}/hot-master-transparent.svg', bg=None, knock='#171A1F', title='Health of a Titan — master on transparent (knockouts #171A1F)')
C.build(f'{O}/hot-master-ink-on-white.svg', mono='#2B2116', knock='#FFFFFF', bg='#FFFFFF', title='Health of a Titan — single colour on white')

# ---------- app icon (optical size) ----------
# Android adaptive icon: 108 dp canvas, 66 dp safe zone. The ring sits at r = 31.2 dp so that even the
# capital corners (which break the ring, as in R1) stay inside the 66 dp safe circle.
R_DP = 31.2
S = R_DP / G['circle'][2]           # dp per art unit
U = 108 / S                          # art units across the canvas
VB = (G['circle'][0] - U/2, G['circle'][1] - U/2, U, U)
ICON = dict(viewbox=VB, size=(108, 108), extra=13, extra_fig=10, pulse_extra=9, circle_w=G['circleW']+15,
            flutes=[(21, 26)], fig_min=1500, bg_center=(G['circle'][0], G['circle'][1]), bg_r=1180, glow_scale=0.9)
json.dump({'R_DP': R_DP, 'VB': VB}, open(f'{O}/icon-geom.json', 'w'))
C.build(f'{O}/ic_launcher_full.svg', title='HoT app icon — full (preview)', **ICON)
C.build(f'{O}/ic_launcher_foreground.svg', bg=None, knock='url(#hotBg)', title='HoT adaptive icon — foreground', **ICON)
open(f'{O}/ic_launcher_background.svg', 'w').write(
    f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{" ".join(f2(v) for v in VB)}" width="108" height="108"><title>HoT adaptive icon — background</title>'
    f'<defs><radialGradient id="hotBg" gradientUnits="userSpaceOnUse" cx="{f2(G["circle"][0])}" cy="{f2(G["circle"][1])}" r="1180">'
    + ''.join(f'<stop offset="{o}" stop-color="{c}"/>' for o, c in C.BG) +
    f'</radialGradient></defs><rect x="{f2(VB[0])}" y="{f2(VB[1])}" width="{f2(VB[2])}" height="{f2(VB[3])}" fill="url(#hotBg)"/></svg>')

# Play Store 512: same drawing, a little larger in frame (Play applies its own rounded mask)
R_PLAY = 0.405 * 512
SP = R_PLAY / G['circle'][2]
UP = 512 / SP
VBP = (G['circle'][0] - UP/2, G['circle'][1] - UP/2 + 6, UP, UP)
C.build(f'{O}/hot-playstore-512.svg', viewbox=VBP, size=(512, 512), extra=7, extra_fig=5, pulse_extra=4,
        circle_w=G['circleW']+7, flutes=[1, 2], fig_min=300, bg_center=(G['circle'][0], G['circle'][1]), bg_r=1250,
        title='HoT — Google Play icon 512')
print('ok')
