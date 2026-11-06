#!/bin/bash
set -e
W=/tmp/claude-0/-home-user-health-of-a-titan/ad7eab95-b6de-5a45-addc-3e2eb7b7055e/scratchpad/w
W3=/tmp/claude-0/-home-user-health-of-a-titan/ad7eab95-b6de-5a45-addc-3e2eb7b7055e/scratchpad/w3
mkdir -p "$W3"
cp "$W/mask.py" "$W/parts.py" "$W/trace.py" "$W/compose.py" "$W/geom.json" "$W3/"
cp "$W/R3-atlas-briefs-circle.jpg" "$W3/R1-atlas-drapery-circle.jpg"
python3 "$W3/mask.py" 0.5
python3 "$W3/parts.py"
python3 "$W3/trace.py"
