"""Key poses added or corrected by Agent 17 for the Lottie top-20 (draft for Agent 12's review).

    python3 -m hotlottie.keyposes      — writes the entries below into content/training/poses.yaml

New: ex-stretch-chest-doorway, ex-wrist-prep, ex-scap-pushup, ex-pullup, ex-cardio-machine.
Corrected: lying poses sat ~2 units under the floor (breath, hamstring); leg swings rocked the
torso by 12° (a listed mistake: «корпус раскачивается»), now 4°; quad stretch: the near hand
holds the near foot (IK), the far hand is on the wall.
Hands and feet are placed with IK so contacts match between key poses.
"""
from __future__ import annotations

from . import rig
from .build import load_poses
from .posetool import ground, solve_arm, solve_leg, write_poses


def P(**kw):
    base = dict(rx=50.0, ry=48.8, torso=0.0, head=0.0, uaN=178.0, faN=182.0, uaF=182.0, faF=178.0, thN=180.0, shN=180.0, ftN=90.0,
                 thF=183.0, shF=178.0, ftF=92.0, spine=0.0, breath=0.0, belly=0.0, armLenN=1.0, armLenF=1.0, nod=0.0)
    base.update(kw)
    return base


def chest_doorway():
    # v2: front view, so the forearm on the jamb reads (90/90: upper arm out to the side, forearm
    # up the jamb, palm on it). Stepping through is shown by the upper arm going back behind the
    # body (foreshortened in exercises.py: armLenN) while the hand stays on the jamb.
    s = P(rx=46.0, ry=44.8, uaN=90.0, faN=0.0, uaF=182.0, faF=178.0, thN=176.0, shN=180.0, ftN=135.0, thF=184.0, shF=180.0, ftF=225.0)
    st = dict(s, rx=47.4, head=356.0)
    return {"set": s, "stretch": st}


def child():
    """v2: hips back onto the heels, the back rounds over the thighs, forehead and palms to the mat."""
    knee = (40.0, 87.1)
    hip = (23.5, 79.4)
    import math
    th = math.degrees(math.atan2(knee[0] - hip[0], -(knee[1] - hip[1]))) % 360
    rest = P(rx=hip[0], ry=hip[1], torso=97.0, head=118.0, spine=0.42, thN=th, shN=268.0, ftN=232.0, thF=th + 1.5, shF=268.0, ftF=234.0)
    j = rig.joints(rest)
    for side, dx in (("N", 0.0), ("F", -0.6)):
        solve_arm(rest, side, (j["shoulder"][0] + 23.0 + dx, 90.6), (j["shoulder"][0] + 12.0, j["shoulder"][1] + 7.0))
    return rest


def swap_legs(p):
    """Near ↔ far leg (the stretched leg must be the near, bright one)."""
    q = dict(p)
    for a in ("th", "sh", "ft"):
        q[a + "N"], q[a + "F"] = p[a + "F"], p[a + "N"]
    return q


def wrist_prep(cc):
    n = dict(cc["neutral"])
    j = rig.joints(n)
    knee = j["kneeN"]
    hands = j["handN"], j["handF"]

    def at(thigh, torso, head):
        p = dict(n)
        p["thN"], p["thF"] = thigh, thigh + 2
        # hip on the circle around the knee
        p["rx"] = knee[0] - 21 * __import__("math").sin(__import__("math").radians(thigh))
        p["ry"] = knee[1] + 21 * __import__("math").cos(__import__("math").radians(thigh))
        p["torso"], p["head"] = torso, head
        for side, h in zip("NF", hands):
            jj = rig.joints(p)
            solve_arm(p, side, h, (jj["shoulderN"][0] - 5, jj["shoulderN"][1] + 13))
        return p
    center = at(180.0, 76.6, 96.0)
    forward = at(198.0, 80.0, 98.0)
    back = at(140.0, 70.0, 92.0)
    return {"center": center, "forward": forward, "back": back}


def scap_pushup(pu):
    top = dict(pu["top"])
    retract = dict(top, head=74.0)
    protract = dict(top, spine=0.16, head=80.0)
    return {"retract": retract, "protract": protract}


def pullup():
    bar = (50.0, 8.0)
    legs = dict(thN=172.0, shN=262.0, ftN=250.0, thF=175.0, shF=264.0, ftF=252.0)
    hang = P(rx=50.0, ry=61.0, torso=0.0, head=0.0, **legs)
    mid = P(rx=47.2, ry=48.0, torso=352.0, head=356.0, **legs)
    # v2: the top is a full rep — chin over the bar, elbows down beside the ribs
    top = P(rx=45.6, ry=35.6, torso=352.0, head=4.0, **legs)
    for p, hint in ((hang, (50, 20)), (mid, (54, 30)), (top, (52, 20))):
        j = rig.joints(p)
        for side in "NF":
            solve_arm(p, side, bar, (hint[0], hint[1]) if p is hang else (j["shoulder" + side][0] + 6, j["shoulder" + side][1] + 8))
    return {"hang": hang, "mid": mid, "top": top}


def cardio_machine():
    top = P(rx=44.0, ry=54.0, torso=20.0, head=30.0, uaN=120.0, faN=105.0, uaF=122.0, faF=107.0,
            thN=75.0, shN=170.0, ftN=100.0, thF=135.0, shF=165.0, ftF=110.0)
    bottom = dict(top, thN=135.0, shN=165.0, ftN=110.0, thF=75.0, shF=170.0, ftF=100.0)
    return {"top": top, "bottom": bottom}


def _upper_intersection(c1, c2, r=21.0):
    import math
    dx, dy = c2[0] - c1[0], c2[1] - c1[1]
    d = math.hypot(dx, dy)
    a = d / 2
    h = math.sqrt(max(r * r - a * a, 0.0))
    mx, my = c1[0] + dx / 2, c1[1] + dy / 2
    ux, uy = dx / d, dy / d
    cands = [(mx + uy * h, my - ux * h), (mx - uy * h, my + ux * h)]
    return min(cands, key=lambda p: p[1])


def ankle_knee_wall():
    """Half-kneeling at the wall: front heel on the floor, rear knee on the mat, hands on the wall.
    The draft had the front foot 3 units above the floor and the knee short of the wall."""
    wall = 79.5
    ankle_n = (69.0, 90.8)
    knee_f = (48.0, 89.2)
    hands = (wall - 1.4, 34.0)
    start = P(rx=48.0, ry=knee_f[1] - 21.0, torso=4.0, head=4.0, thF=180.0, shF=270.0, ftF=235.0)
    knee_n_touch = (wall - 2.9, 0.0)
    knee_n_touch = (knee_n_touch[0], ankle_n[1] - (21 ** 2 - (knee_n_touch[0] - ankle_n[0]) ** 2) ** 0.5)
    hip = _upper_intersection(knee_f, knee_n_touch)
    touch = P(rx=hip[0], ry=hip[1], torso=8.0, head=8.0, shF=270.0, ftF=235.0)
    import math
    touch["thF"] = math.degrees(math.atan2(knee_f[0] - hip[0], -(knee_f[1] - hip[1]))) % 360
    for p in (start, touch):
        j = rig.joints(p)
        solve_leg(p, "N", ankle_n, (j["hip"][0] + 14, j["hip"][1] + 4))
        p["ftN"] = 90.0
        for side in "NF":
            jj = rig.joints(p)
            solve_arm(p, side, hands, (jj["shoulder" + side][0] + 8, jj["shoulder" + side][1] + 10))
    return {"start": start, "touch": touch}


def calf_wall():
    """Hands on the wall, rear heel stays on the floor (the draft lifted it: a listed mistake)."""
    hands = (78.0, 20.0)
    ankle_n, ankle_f = (34.0, 90.8), (53.5, 90.8)  # near = the rear, stretched leg (bright)
    st = P(rx=46.5, ry=50.5, torso=14.0, head=14.0)
    sr = P(rx=51.0, ry=52.5, torso=22.0, head=22.0)
    for p in (st, sr):
        j = rig.joints(p)
        solve_leg(p, "N", ankle_n, (j["hip"][0] - 4, j["hip"][1] + 21))
        solve_leg(p, "F", ankle_f, (j["hip"][0] + 8, j["hip"][1] + 18))
        p["ftN"], p["ftF"] = 92.0, 90.0
        for side in "NF":
            jj = rig.joints(p)
            solve_arm(p, side, hands, (jj["shoulder" + side][0] + 8, jj["shoulder" + side][1] + 10))
    return {"set": st, "stretch": sr}


def lat_wall():
    """Hands stay on the support; hips go back until back and arms are one line (90° at the hips)."""
    import math
    hands = (80.0, 42.0)
    ankles = (38.0, 90.8)
    th = math.degrees(math.acos((52.0 - hands[1]) / 53.0))
    sr = P(rx=hands[0] - 53.0 * math.sin(math.radians(th)), ry=52.0, torso=th, head=th + 6, uaN=th, faN=th, uaF=th + 1, faF=th + 1)
    st = P(rx=40.0, ry=50.0, torso=40.0, head=44.0)
    for p in (st, sr):
        j = rig.joints(p)
        for side, dx in (("N", 0.0), ("F", -0.4)):
            solve_leg(p, side, (ankles[0] + dx, ankles[1]), (j["hip"][0] + 6, j["hip"][1] + 21))
            p["ft" + side] = 90.0
        if p is st:
            for side in "NF":
                jj = rig.joints(p)
                solve_arm(p, side, hands, (jj["shoulder" + side][0] + 12, jj["shoulder" + side][1] + 10))
    return {"start": st, "stretch": sr}


def pigeon():
    """The draft floated the pelvis 20 units over the mat. Now: the rear leg lies on the mat, the
    front shin is folded under (foreshortened in the side view), arms straight → forearms down."""
    legs = dict(thN=100.0, shN=262.0, ftN=250.0, thF=268.0, shF=270.0, ftF=265.0)
    st = P(rx=44.0, ry=86.0, torso=15.0, head=12.0, uaN=168.0, faN=172.0, uaF=170.0, faF=174.0, **legs)
    fo = P(rx=44.0, ry=86.0, torso=62.0, head=78.0, spine=0.1, uaN=176.0, faN=92.0, uaF=178.0, faF=94.0, **legs)
    return {"set": ground(st), "fold": ground(fo)}


def main():
    lib = load_poses()
    up = {}
    up["ex-stretch-pigeon"] = pigeon()
    ch = lib["ex-stretch-child"]
    up["ex-stretch-child"] = {"kneel": ch["kneel"], "rest": child()}
    hf = lib["ex-stretch-hip-flexor"]
    if hf["start"]["thN"] < 135.0:          # the near leg is still the front one: swap once
        up["ex-stretch-hip-flexor"] = {k: swap_legs(v) for k, v in hf.items()}
    bd = lib["ex-band-dislocate"]
    up["ex-band-dislocate"] = {"front": dict(bd["front"], uaN=165.0, faN=165.0, uaF=167.0, faF=167.0), "overhead": bd["overhead"], "behind": bd["behind"]}
    up["ex-ankle-knee-wall"] = ankle_knee_wall()
    up["ex-stretch-calf"] = calf_wall()
    up["ex-stretch-lat-wall"] = lat_wall()
    up["ex-stretch-chest-doorway"] = chest_doorway()
    up["ex-wrist-prep"] = wrist_prep(lib["ex-cat-cow"])
    up["ex-scap-pushup"] = scap_pushup(lib["ex-pushup"])
    up["ex-pullup"] = pullup()
    up["ex-cardio-machine"] = cardio_machine()
    up["ex-breath-long-exhale"] = {k: ground(v) for k, v in lib["ex-breath-long-exhale"].items()}
    up["ex-stretch-hamstring"] = {k: ground(v) for k, v in lib["ex-stretch-hamstring"].items()}
    ls = lib["ex-leg-swings"]
    up["ex-leg-swings"] = {"front": dict(ls["front"], torso=358.0, head=0.0), "back": dict(ls["back"], torso=2.0, head=2.0)}
    q = lib["ex-stretch-quad"]
    start = dict(q["start"], uaN=182.0, faN=178.0, uaF=92.0, faF=92.0)
    stretch = dict(q["stretch"], uaF=92.0, faF=92.0)
    j = rig.joints(stretch)
    solve_arm(stretch, "N", (j["ankleN"][0] - 0.5, j["ankleN"][1] + 0.5), (j["shoulderN"][0] - 2, j["shoulderN"][1] + 14))
    up["ex-stretch-quad"] = {"start": start, "stretch": stretch}
    for ex, frames in up.items():
        for k, p in frames.items():
            pr = problems(p)
            if pr:
                print("PROBLEM", ex, k, pr)
    write_poses(up)


def problems(p):
    out = []
    for side in "NF":
        k = rig.shortest(p["th" + side], p["sh" + side])
        if k < -10 or k > 165:
            out.append(f"knee{side} {k:.0f}")
        e = -rig.shortest(p["ua" + side], p["fa" + side])
        if e < -10 or e > 160:
            out.append(f"elbow{side} {e:.0f}")
    j = rig.joints(p)
    if max(q[1] for q in j.values()) > rig.FLOOR + 2.5:
        out.append("below floor")
    return out


# --- v4: Agent 12's review of v3 (R-2026-11-07) -------------------------------------------------------

FRONT_LEGS = dict(thN=176.0, shN=180.0, ftN=135.0, thF=184.0, shF=180.0, ftF=225.0)
# pull-up grip, front view: hands ±13 from the midline — 1.3× the rendered deltoid width (±10.3),
# the width Agent 12 measures in the render; elbows end up under the hands at mid (forearms vertical)
PULLUP_GRIP = 13.0
PULLUP_BAR = 8.0
# band dislocate, front view: the band holds the hands ±16 from the midline (≈ 2 shoulder widths)
BAND_GRIP = 16.0


def _hip_from_ankle(ankle, shin, thigh):
    import math
    knee = (ankle[0] - 21.0 * math.sin(math.radians(shin)), ankle[1] + 21.0 * math.cos(math.radians(shin)))
    return (knee[0] - 21.0 * math.sin(math.radians(thigh)), knee[1] + 21.0 * math.cos(math.radians(thigh))), knee


def _chest_point(p, fwd, down):
    """A point on the chest: `fwd` in front of the spine line, `down` below the shoulder joint."""
    import math
    j = rig.joints(p)
    a = math.radians(p["torso"])
    sh = j["shoulder"]
    return (sh[0] + fwd * math.cos(a) - down * math.sin(a), sh[1] + fwd * math.sin(a) + down * math.cos(a))


def goblet_v4():
    """Strict profile. Heels down, feet flat in every frame; the bell centred on the sternum, elbows
    down; at the bottom the hips are level with the knees, the shins lean ~32°, the torso 34°, and
    the elbows come to the inside of the knees."""
    stand = P(rx=50.0, ry=48.8, torso=0.0, head=0.0, thN=180.0, shN=180.0, ftN=90.0, thF=182.0, shF=178.0, ftF=90.0)
    j = rig.joints(stand)
    ank_n, ank_f = j["ankleN"], j["ankleF"]
    hip, knee = _hip_from_ankle(ank_n, 212.0, 88.0)
    bottom = P(rx=hip[0], ry=hip[1], torso=34.0, head=22.0, thN=88.0, shN=212.0, ftN=90.0, ftF=90.0)
    jb = rig.joints(bottom)
    solve_leg(bottom, "F", ank_f, (jb["kneeN"][0] - 0.5, jb["kneeN"][1] - 0.5))
    for p, hint in ((stand, None), (bottom, knee)):
        for side, dx in (("N", 0.0), ("F", 0.4)):
            h = _chest_point(p, 7.2 + dx, 5.0)
            jj = rig.joints(p)
            eh = (hint[0] - 1.2, hint[1] - 2.0) if hint else (jj["shoulder"][0] + 2.0, jj["shoulder"][1] + 13.0)
            solve_arm(p, side, h, eh)
    return {"stand": stand, "bottom": bottom}


def pullup_v4():
    """Front view (Agent 12: the grip must read; arms never cross the face). Active hang: arms in a
    V, the head clear between them; the elbows go down and out; top: chin over the bar."""
    out = {}
    for name, ys, hint in (("hang", 34.0, (66.0, 22.0)), ("mid", 24.0, (64.0, 21.0)), ("top", 9.5, (68.0, 22.0))):
        p = P(rx=50.0, ry=ys + 26.0, torso=0.0, head=0.0, **FRONT_LEGS)
        for side, sgn in (("N", 1), ("F", -1)):
            solve_arm(p, side, (50.0 + sgn * PULLUP_GRIP, PULLUP_BAR), (50.0 + sgn * (hint[0] - 50.0), hint[1]), front=True)
        out[name] = p
    return out


def band_angles(theta, side):
    """Front-view arm angle and projected length for a straight arm at `theta` (degrees, 0 = down in
    front of the thighs, 180 = overhead, 340 = down behind) holding the band BAND_GRIP wide."""
    import math
    reach = rig.L["ua"] + rig.L["fa"]
    a = (BAND_GRIP - rig.FRONT_SHOULDER) / reach
    s_ = math.sqrt(1 - a * a)
    dx = a * (1 if side == "N" else -1)
    dy = s_ * math.cos(math.radians(theta))            # + = down
    ang = math.degrees(math.atan2(dx, -dy)) % 360
    return ang, math.hypot(dx, dy)


def band_v4():
    out = {}
    for name, th in (("front", 20.0), ("overhead", 180.0), ("behind", 340.0)):
        p = P(rx=50.0, ry=48.8, **FRONT_LEGS)
        for side in "NF":
            ang, _ = band_angles(th, side)
            p["ua" + side] = p["fa" + side] = ang
        out[name] = p
    return out


def leg_swings_v4(ls):
    """Side-on to the wall (Agent 12: «встань боком к стене»): the wall is behind the figure, the far
    hand holds a rail on it, the near hand is on the hip; the leg swings parallel to the wall."""
    out = {}
    for k in ("front", "back"):
        p = dict(ls[k], torso=0.0, head=0.0, uaF=160.0, faF=140.0)
        j = rig.joints(p)
        solve_arm(p, "N", (j["hip"][0] + 2.6, j["hip"][1] - 7.5), (j["hip"][0] - 7.0, j["hip"][1] - 15.0))
        out[k] = p
    return out


def hip_flexor_v4(hf):
    """Hands on the hips (Agent 12: arms overhead invite an arched lower back)."""
    out = {}
    for k, p in hf.items():
        q = dict(p)
        j = rig.joints(q)
        for side, dx in (("N", 0.0), ("F", -0.5)):
            solve_arm(q, side, (j["hip"][0] + 2.6 + dx, j["hip"][1] - 7.5), (j["hip"][0] - 7.0, j["hip"][1] - 15.0))
        out[k] = q
    return out


def cat_cow_v4(cc):
    """Cow: the arch runs through the whole spine (pelvis tips forward, the chest goes forward and
    up), the head continues the line of the chest — no longer a neck-only movement."""
    import math
    cow = dict(cc["cow"], spine=-0.38)
    chest = cow["torso"] + math.degrees(math.atan(cow["spine"]))
    cow["head"] = chest - 4.0
    return dict(cc, cow=cow)


def breath_v4(br):
    """Inhale: the palms ride the belly up ~2.6 units (the belly rises in titan.py), the chest stays."""
    ex = dict(br["exhale"])
    inh = dict(br["inhale"], uaN=ex["uaN"], faN=ex["faN"], uaF=ex["uaF"], faF=ex["faF"])
    j = rig.joints(ex)
    for side in "NF":
        h = j["hand" + side]
        e = j["elbow" + side]
        solve_arm(inh, side, (h[0], h[1] - 2.6), e)
    return {"inhale": inh, "exhale": ex}


def hamstring_v4(hs):
    """The working leg at ~75° to the floor («до первого ощущения»), not 90°."""
    st = dict(hs["stretch"], thN=15.0, shN=15.0, ftN=103.0)
    return dict(hs, stretch=st)


def main_v4():
    lib = load_poses()
    up = {
        "ex-goblet-squat": goblet_v4(),
        "ex-leg-swings": leg_swings_v4(lib["ex-leg-swings"]),
        "ex-stretch-hip-flexor": hip_flexor_v4(lib["ex-stretch-hip-flexor"]),
        "ex-cat-cow": cat_cow_v4(lib["ex-cat-cow"]),
        "ex-breath-long-exhale": breath_v4(lib["ex-breath-long-exhale"]),
        "ex-stretch-hamstring": hamstring_v4(lib["ex-stretch-hamstring"]),
    }
    for ex, frames in up.items():
        for k, p in frames.items():
            pr = problems(p)
            if pr:
                print("PROBLEM", ex, k, pr)
    write_poses(up)


if __name__ == "__main__":
    import sys
    main_v4() if sys.argv[1:] == ["v4"] else main()
