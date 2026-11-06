#!/bin/bash
# Assemble brand/mark-v2 in the repo from the scratch pipeline and render all PNGs.
set -e
W=/tmp/claude-0/-home-user-health-of-a-titan/ad7eab95-b6de-5a45-addc-3e2eb7b7055e/scratchpad/w
W3=/tmp/claude-0/-home-user-health-of-a-titan/ad7eab95-b6de-5a45-addc-3e2eb7b7055e/scratchpad/w3
REPO=/home/user/health-of-a-titan/.claude/worktrees/agent-abe55e570a24ab9db
M=$REPO/brand/mark-v2
R="node $W/render.js"
cd "$W"
python3 derive.py
python3 mono.py
python3 lockups.py
python3 splash.py
mkdir -p "$M"/master "$M"/icon "$M"/lockup "$M"/splash "$M"/compare "$M"/source "$REPO"/brand/references
cp /mnt/project-files/brand/references/R1-atlas-drapery-circle.jpg /mnt/project-files/brand/references/R2-vitruvian-line-circle.jpg /mnt/project-files/brand/references/R3-atlas-briefs-circle.jpg "$REPO"/brand/references/
O=$W/out
# master
cp "$O"/hot-master.svg "$O"/hot-master-flat.svg "$O"/hot-master-transparent.svg "$O"/hot-master-ink-on-white.svg "$M"/master/
cp "$W3"/master-r3.svg "$M"/master/hot-master-r3-briefs.svg
$R "$M"/master/hot-master.svg "$M"/master/hot-master-2000.png 2000 2000 1
$R "$M"/master/hot-master-flat.svg "$M"/master/hot-master-flat-2000.png 2000 2000 1
$R "$M"/master/hot-master-r3-briefs.svg "$M"/master/hot-master-r3-briefs-2000.png 2000 2000 1
# icon
cp "$O"/ic_launcher_foreground.svg "$O"/ic_launcher_background.svg "$O"/ic_launcher_full.svg "$O"/ic_launcher_monochrome.svg "$O"/ic_stat_hot.svg "$O"/hot-playstore-512.svg "$M"/icon/
$R "$M"/icon/hot-playstore-512.svg "$M"/icon/hot-playstore-512.png 512 512 1
for s in 432 192 108 48; do $R "$M"/icon/ic_launcher_full.svg "$M"/icon/ic_launcher_full-$s.png $s $s 1; done
$R "$M"/icon/ic_stat_hot.svg "$M"/icon/ic_stat_hot-96.png 96 96 1
# lockups
cp "$O"/hot-lockup-*.svg "$O"/hot-wordmark-*.svg "$M"/lockup/
# splash
cp "$O"/splash-f*.svg "$O"/hot-splash-animated.svg "$M"/splash/
for f in "$M"/splash/splash-f*.svg; do $R "$f" "${f%.svg}.png" 1024 1024 1; done
# comparisons
cp "$W"/c_sbs.png "$M"/compare/r1-vs-master-side-by-side.png
cp "$W"/c_ov.png "$M"/compare/r1-vs-master-overlay-50.png
cp "$W"/c_diff.png "$M"/compare/r1-vs-master-diff.png
for z in z1 z2 z3 zh; do cp "$W"/${z}_sbs.png "$M"/compare/detail-$z.png; done
cp "$W"/zh2_sbs.png "$M"/compare/detail-hand-left-x7.png
cp "$W"/zh3_sbs.png "$M"/compare/detail-hand-right-x7.png
cp "$W"/r3_sbs.png "$M"/compare/r3-vs-briefs-variant-side-by-side.png
# source pipeline
cp "$W"/mask.py "$W"/parts.py "$W"/trace.py "$W"/sil.py "$W"/pulsefit.py "$W"/compose.py "$W"/derive.py "$W"/mono.py "$W"/lockups.py "$W"/textpath.py "$W"/splash.py "$W"/compare.py "$W"/colcmp.py "$W"/render.js "$W"/geom.json "$W"/traced.json "$M"/source/
cp "$W3"/traced_r3.json "$M"/source/traced_r3.json
cp /tmp/claude-0/-home-user-health-of-a-titan/ad7eab95-b6de-5a45-addc-3e2eb7b7055e/scratchpad/fonts/cormorantgaramond-OFL.txt "$M"/lockup/OFL-Cormorant.txt
cp "$W"/merge_r3.py "$W"/run_r3.sh "$W"/run.sh "$W"/assemble.sh "$W"/extra_pngs.py "$W"/pathinfo.py "$M"/source/
python3 "$W"/extra_pngs.py "$M"
python3 "$M"/source/build_presentation.py
cd "$M"
node "$W"/render.js _presentation.html _presentation.png 1600 1000 2
echo assembled
