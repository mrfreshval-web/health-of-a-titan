import json, os, sys
os.chdir(os.path.dirname(os.path.abspath(__file__)))
import compose as C
for key in sys.argv[1:]:
    rows = []
    for d, tx, ty in C.T[key]:
        bd = C.bake(d, tx, ty)
        a, bb = C.path_bbox_area(bd)
        rows.append((a, [round(v) for v in bb], bd.count('M')))
    for r in sorted(rows):
        print(key, round(r[0]), r[1], 'subpaths', r[2])
