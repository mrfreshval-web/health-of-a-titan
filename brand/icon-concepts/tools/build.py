"""Build every SVG of the three HoT mark concepts and the preview HTML pages.

    pip install shapely fonttools uharfbuzz
    HOT_FONT_DIR=/path/to/fonts python3 brand/icon-concepts/tools/build.py
    node brand/icon-concepts/tools/render.js      # PNG previews (Playwright)

Outputs go to brand/icon-concepts/<concept-id>/ ; preview HTML to tools/.render/.
"""
from __future__ import annotations

import json
import os

from concepts import CONCEPTS
from geom import CANVAS, safe_zone_report, to_d, transform
from wordmark import outline

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)  # brand/icon-concepts
RENDER = os.path.join(HERE, ".render")

# ---------------------------------------------------------------- palette
GOLD = "#C9A23A"  # hot_gold (android/app/src/main/res/values/colors.xml)
GOLD_HI = "#E2C26A"  # gradient top (app icon only)
GOLD_LO = "#A9852A"  # gradient bottom (app icon only)
GOLD_DEEP = "#8A6A1E"  # gold on light backgrounds (WCAG contrast 4.7:1 on #FAF7EF)
INK = "#16130B"  # hot_background
INK_HI = "#2A2414"  # background radial highlight
IVORY = "#EDE3C8"  # wordmark on dark
PAPER = "#FAF7EF"  # light background

# ---------------------------------------------------------------- type per concept
TYPE = {
    "c1-atlas": {
        "full": ("CormorantSC-Bold.ttf", "Health of a Titan", 0.05),
        "short": ("CormorantSC-Bold.ttf", "HoT", 0.04),
        "family": "Cormorant SC Bold (Christian Thalmann, SIL OFL 1.1, latin + cyrillic)",
    },
    "c2-portico": {
        "full": ("Manrope-SemiBold.ttf", "HEALTH OF A TITAN", 0.16),
        "short": ("Manrope-Bold.ttf", "HoT", 0.02),
        "family": "Manrope SemiBold / Bold (Mikhail Sharanda, SIL OFL 1.1, latin + cyrillic)",
    },
    "c3-monogram": {
        "full": ("TenorSans-Regular.ttf", "HEALTH OF A TITAN", 0.16),
        "short": ("TenorSans-Regular.ttf", "HoT", 0.06),
        "family": "Tenor Sans Regular (Denis Masharov, SIL OFL 1.1, latin + cyrillic)",
    },
}

ICON_SCALE = 0.9  # mark size inside the 108 dp canvas (1.0 = touches the 66 dp safe circle)
SVG_HEAD = '<svg xmlns="http://www.w3.org/2000/svg"'


def bbox(geom):
    return geom.bounds  # minx, miny, maxx, maxy


def svg(viewbox, body, w=None, h=None, title=None):
    size = f' width="{w}" height="{h}"' if w else ""
    t = f"<title>{title}</title>" if title else ""
    return f'{SVG_HEAD} viewBox="{viewbox}"{size}>{t}{body}</svg>\n'


def gold_grad(gid="g"):
    return (
        f'<linearGradient id="{gid}" x1="0" y1="30" x2="0" y2="78" gradientUnits="userSpaceOnUse">'
        f'<stop offset="0" stop-color="{GOLD_HI}"/><stop offset="1" stop-color="{GOLD_LO}"/></linearGradient>'
    )


def bg_layer(gid="bg"):
    return (
        f'<defs><radialGradient id="{gid}" cx="54" cy="40" r="62" gradientUnits="userSpaceOnUse">'
        f'<stop offset="0" stop-color="{INK_HI}"/><stop offset="1" stop-color="{INK}"/></radialGradient></defs>'
        f'<rect width="108" height="108" fill="url(#{gid})"/>'
    )


def fmt(v):
    return ("%.2f" % v).rstrip("0").rstrip(".")


def build_concept(cid, fn):
    out = os.path.join(ROOT, cid)
    os.makedirs(out, exist_ok=True)
    full, mono, small = fn("full"), fn("mono"), fn("small")
    d_full = to_d(full)
    # launcher layers: the mark at ICON_SCALE around the centre, so it breathes inside every mask
    icon = transform(full, ICON_SCALE, 0, 0, origin=(54, 54))
    mono_i = transform(mono, ICON_SCALE, 0, 0, origin=(54, 54))
    d_icon, d_mono = to_d(icon), to_d(mono_i)
    report = [safe_zone_report(icon, cid + " app icon"), safe_zone_report(mono_i, cid + " monochrome")]

    # (a) symbol: flat gold, tight viewBox with 4 % margin
    x0, y0, x1, y1 = bbox(full)
    m = 0.04 * max(x1 - x0, y1 - y0)
    vb = f"{fmt(x0 - m)} {fmt(y0 - m)} {fmt(x1 - x0 + 2 * m)} {fmt(y1 - y0 + 2 * m)}"
    files = {}
    files["symbol.svg"] = svg(vb, f'<path fill="{GOLD}" fill-rule="evenodd" d="{d_full}"/>', title=f"HoT symbol {cid}")
    files["symbol-ink.svg"] = svg(vb, f'<path fill="{INK}" fill-rule="evenodd" d="{d_full}"/>', title=f"HoT symbol {cid} (ink)")

    # (b) adaptive icon 108 x 108: layers + composite
    fg = f'<defs>{gold_grad("g")}</defs><path fill="url(#g)" fill-rule="evenodd" d="{d_icon}"/>'
    files["app-icon-foreground.svg"] = svg("0 0 108 108", fg, 108, 108, f"HoT adaptive foreground {cid}")
    files["app-icon-background.svg"] = svg("0 0 108 108", bg_layer(), 108, 108, "HoT adaptive background")
    files["app-icon.svg"] = svg(
        "0 0 108 108",
        bg_layer("bg").replace("</defs>", gold_grad("g") + "</defs>")
        + f'<path fill="url(#g)" fill-rule="evenodd" d="{d_icon}"/>',
        108,
        108,
        f"HoT app icon {cid} (108 dp canvas, safe zone r=33)",
    )

    # (c) monochrome: themed icon layer (108) and notification small icon (24)
    files["monochrome.svg"] = svg(
        "0 0 108 108", f'<path fill="#000" fill-rule="evenodd" d="{d_mono}"/>', 108, 108, f"HoT monochrome {cid}"
    )
    sx0, sy0, sx1, sy1 = bbox(small)
    live = 20.0  # 24 dp icon, 2 dp padding
    s = live / max(sx1 - sx0, sy1 - sy0)
    g24 = transform(small, s, 0, 0, origin=(0, 0))
    gx0, gy0, gx1, gy1 = bbox(g24)
    g24 = transform(g24, 1.0, 12 - (gx0 + gx1) / 2, 12 - (gy0 + gy1) / 2)
    d24 = to_d(g24, nd=3, simplify=0.004)
    files["notification-24.svg"] = svg(
        "0 0 24 24", f'<path fill="#fff" fill-rule="evenodd" d="{d24}"/>', 24, 24, f"HoT ic_stat_hot {cid}"
    )

    # (d) lockups: symbol + wordmark, dark and light
    t = TYPE[cid]
    mh = y1 - y0
    mw = x1 - x0
    for kind in ("full", "short"):
        font, text, tr = t[kind]
        cap_target = mh * (0.30 if kind == "full" else 0.56)
        # first pass to learn the cap height at size 100
        _, _, cap100 = outline(font, text, 100)
        size = 100 * cap_target / cap100
        gap = mw * (0.30 if kind == "full" else 0.26)
        tx = x1 + gap
        base = y0 + mh / 2 + cap_target / 2
        d_txt, adv, _ = outline(font, text, size, tx, base, tr)
        pad = mh * 0.18
        vbx, vby = x0 - pad, y0 - pad
        vbw, vbh = (tx + adv) - x0 + 2 * pad, mh + 2 * pad
        vbl = f"{fmt(vbx)} {fmt(vby)} {fmt(vbw)} {fmt(vbh)}"
        for theme, mark_c, text_c in (("dark", GOLD, IVORY), ("light", GOLD_DEEP, INK)):
            body = (
                f'<path fill="{mark_c}" fill-rule="evenodd" d="{d_full}"/>'
                f'<path fill="{text_c}" d="{d_txt}"/>'
            )
            name = f"lockup-{'horizontal' if kind == 'full' else 'hot'}-{theme}.svg"
            files[name] = svg(vbl, body, title=f"HoT lockup {cid}: {text} ({theme} backgrounds), type: {t['family']}")

    for name, content in files.items():
        with open(os.path.join(out, name), "w") as f:
            f.write(content)
    return {
        "id": cid,
        "files": sorted(files),
        "report": report,
        "d_full": d_full,
        "d_mono": d_mono,
        "d24": d24,
        "symbol_vb": vb,
        "family": t["family"],
    }


def main():
    os.makedirs(RENDER, exist_ok=True)
    meta = []
    for cid, fn in CONCEPTS.items():
        info = build_concept(cid, fn)
        for line in info["report"]:
            print(line)
        meta.append(info)
    with open(os.path.join(RENDER, "meta.json"), "w") as f:
        json.dump(meta, f)
    print("built:", ", ".join(m["id"] for m in meta))


if __name__ == "__main__":
    main()
