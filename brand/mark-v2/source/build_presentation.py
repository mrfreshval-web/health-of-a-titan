"""Build brand/mark-v2/_presentation.html from source/presentation.template.html:
monochrome glyphs are inlined (CSS masks with local SVG files are not reliable), then render with
  node source/render.js _presentation.html _presentation.png 1600 1000 2"""
import os, re
HERE = os.path.dirname(os.path.abspath(__file__))
M = os.path.dirname(HERE)
tpl = open(os.path.join(HERE, 'presentation.template.html'), encoding='utf-8').read()
mono = open(os.path.join(M, 'icon/ic_launcher_monochrome.svg'), encoding='utf-8').read()
stat = open(os.path.join(M, 'icon/ic_stat_hot.svg'), encoding='utf-8').read()
n = [0]


def inline(svg, color, size, src):
    n[0] += 1
    s = re.sub(r'<title>.*?</title>', '', svg)
    s = s.replace(f'fill="{src}"', f'fill="{color}"').replace(f'stroke="{src}"', f'stroke="{color}"')
    s = s.replace('id="m"', f'id="m{n[0]}"').replace('url(#m)', f'url(#m{n[0]})')
    s = re.sub(r'width="[\d.]+" height="[\d.]+"', f'width="{size}" height="{size}"', s, count=1)
    return s


def tint(m):
    cls, bg, fg = m.group(1), m.group(2), m.group(3)
    size = 72 if 'tsm' in cls else 180
    return f'<div class="{cls}" style="background:{bg}">' + inline(mono, fg, size, '#000000') + '</div>'


html = re.sub(r'<div class="(tint[^"]*)" style="background:(#\w+)"><i style="background:(#\w+)"></i></div>', tint, tpl)


def glyph(m):
    w = m.group(1)
    rest = m.group(2) or ''
    col = re.search(r'background:(#\w+)', rest)
    op = re.search(r'opacity:([\d.]+)', rest)
    style = f' style="opacity:{op.group(1)}"' if op else ''
    return f'<span class="glyph"{style}>' + inline(stat, col.group(1) if col else '#FFFFFF', w, '#FFFFFF') + '</span>'


html = re.sub(r'<span class="glyph" style="width:(\d+)px;height:\d+px([^"]*)"></span>', glyph, html)
open(os.path.join(M, '_presentation.html'), 'w', encoding='utf-8').write(html)
print('built', n[0], 'inline glyphs')
