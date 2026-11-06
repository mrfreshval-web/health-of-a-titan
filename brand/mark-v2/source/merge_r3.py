import json, shutil, os
W = '/tmp/claude-0/-home-user-health-of-a-titan/ad7eab95-b6de-5a45-addc-3e2eb7b7055e/scratchpad/w'
W3 = W + '3'
if not os.path.exists(W3 + '/traced_r3.json'):
    shutil.copy(W3 + '/traced.json', W3 + '/traced_r3.json')
a = json.load(open(W + '/traced.json'))
b = json.load(open(W3 + '/traced_r3.json'))
import sys
sys.path.insert(0, W)
import compose as C


def keep(p):
    area, (x0, y0, x1, y1) = C.path_bbox_area(C.bake(*p))
    if area < 10:
        return False
    if y0 > 850 and y1 < 1160 and (x1 < 850 or x0 > 1200):   # pulse remnants
        return False
    return True


a['fig'] = [p for p in b['fig'] if keep(p)]
a['figSil'] = b['figSil']
json.dump(a, open(W3 + '/traced.json', 'w'))
shutil.copy(W + '/compose.py', W3 + '/compose.py')
print('merged')
