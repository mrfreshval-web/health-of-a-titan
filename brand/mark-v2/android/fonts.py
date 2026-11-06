import sys, os
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer
from fontTools import subset

src, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
UNI = (list(range(0x20, 0x7F)) + list(range(0xA0, 0x180)) + list(range(0x400, 0x460)) + [0x490, 0x491]
       + list(range(0x2000, 0x2070)) + list(range(0x2190, 0x2200))
       + [0x20AC, 0x20BD, 0x2116, 0x2122, 0x2212, 0x2248, 0x2260, 0x2264, 0x2265, 0x2022, 0x25CF, 0x2713])
JOBS = [
    ('cg.ttf', 500, 'cormorant_garamond_medium.ttf'),
    ('cg.ttf', 600, 'cormorant_garamond_semibold.ttf'),
    ('cgi.ttf', 500, 'cormorant_garamond_medium_italic.ttf'),
    ('mr.ttf', 300, 'manrope_light.ttf'),
    ('mr.ttf', 400, 'manrope_regular.ttf'),
    ('mr.ttf', 600, 'manrope_semibold.ttf'),
]
total = 0
for f, w, name in JOBS:
    vf = TTFont(os.path.join(src, f))
    st = instancer.instantiateVariableFont(vf, {'wght': w}, updateFontNames=True)
    opts = subset.Options()
    opts.layout_features = subset.Options().layout_features + ['tnum', 'lnum', 'pnum', 'case', 'frac']
    opts.name_IDs = ['*']
    opts.name_languages = ['*']
    opts.notdef_outline = True
    opts.hinting = False
    s = subset.Subsetter(opts)
    uni = UNI if f.startswith('mr') else [u for u in UNI if not (0x100 <= u < 0x180 or 0x2190 <= u < 0x2200)]
    s.populate(unicodes=uni)
    s.subset(st)
    p = os.path.join(out, name)
    st.save(p)
    feats = sorted({fr.FeatureTag for fr in st['GSUB'].table.FeatureList.FeatureRecord}) if 'GSUB' in st else []
    cmap = st.getBestCmap()
    total += os.path.getsize(p)
    print(name, os.path.getsize(p), 'tnum' in feats, 'Ж' in map(chr, cmap), 'weight', st['OS/2'].usWeightClass)
print('total', total)
