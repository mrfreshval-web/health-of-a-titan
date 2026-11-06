#!/bin/bash
# usage: run.sh [parts] [trace]  — rebuild master and comparisons
set -e
W=/tmp/claude-0/-home-user-health-of-a-titan/ad7eab95-b6de-5a45-addc-3e2eb7b7055e/scratchpad/w
cd "$W"
for a in "$@"; do
  case $a in
    parts) python3 parts.py ;;
    trace) python3 trace.py ;;
  esac
done
python3 compose.py master.svg
node render.js master.svg master.png 2048 2048
python3 compare.py master.png R1-atlas-drapery-circle.jpg c
python3 compare.py master.png R1-atlas-drapery-circle.jpg z1 330 230 760 900
python3 compare.py master.png R1-atlas-drapery-circle.jpg z2 820 480 1240 1100
python3 compare.py master.png R1-atlas-drapery-circle.jpg z3 300 1050 1350 1990
python3 compare.py master.png R1-atlas-drapery-circle.jpg zh 460 580 720 860
python3 colcmp.py master.png
