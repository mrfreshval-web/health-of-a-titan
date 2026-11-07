// TitanPose — prototype of the exercise animation rig (A40 §6.2) for the mockups.
// A side-view skeleton of 12 joints, driven by absolute segment angles (degrees, clockwise from "up").
// Drawn in the «line of light» material of the Titan (A37 §3): every segment is a capsule; all light
// strokes are laid first, then the dark glass knockout on top, so the silhouette reads as one outline.
// In the app the same data drives TitanPoseView in Compose; keyframes live in content/training/poses/<id>.json.

// Segment lengths in a 100-unit box, after the proportions of the Titan in mark v2.
const L = { torso: 26, neck: 3.6, head: 5.4, ua: 14, fa: 13, th: 21, sh: 21, ft: 6 };
const W = { torso: 9.5, ua: 5.2, fa: 4.4, th: 6.6, sh: 5.2, ft: 3.4 };

const rad = (a) => (a * Math.PI) / 180;
const step = (o, a, l) => [o[0] + Math.sin(rad(a)) * l, o[1] - Math.cos(rad(a)) * l];

// pose: {root:[x,y] (hip), torso, head?, armN:[ua,fa], armF:[ua,fa], legN:[th,sh,ft], legF:[th,sh,ft]}
function joints(p) {
  const hip = p.root;
  const sh = step(hip, p.torso, L.torso);
  const neck = step(sh, p.head ?? p.torso, L.neck);
  const head = step(neck, p.head ?? p.torso, L.head);
  const arm = ([u, f]) => { const e = step(sh, u, L.ua); return { e, h: step(e, f, L.fa) }; };
  const leg = ([t, s, f]) => { const k = step(hip, t, L.th); const a = step(k, s, L.sh); return { k, a, t: step(a, f, L.ft) }; };
  return { hip, sh, neck, head, aN: arm(p.armN), aF: arm(p.armF || p.armN), lN: leg(p.legN), lF: leg(p.legF || p.legN) };
}

const seg = (a, b, w, stroke, extra = '') =>
  `<line x1="${a[0].toFixed(2)}" y1="${a[1].toFixed(2)}" x2="${b[0].toFixed(2)}" y2="${b[1].toFixed(2)}" stroke="${stroke}" stroke-width="${w}" stroke-linecap="round" ${extra}/>`;

function limbs(j, side, stroke, inset) {
  const a = side === 'N' ? j.aN : j.aF, l = side === 'N' ? j.lN : j.lF;
  return seg(j.hip, l.k, W.th - inset, stroke) + seg(l.k, l.a, W.sh - inset, stroke) + seg(l.a, l.t, W.ft - inset, stroke)
    + seg(j.sh, a.e, W.ua - inset, stroke) + seg(a.e, a.h, W.fa - inset, stroke);
}
function core(j, stroke, inset) {
  return seg(j.hip, j.sh, W.torso - inset, stroke) + seg(j.sh, j.neck, 3.6 - inset, stroke)
    + `<circle cx="${j.head[0].toFixed(2)}" cy="${j.head[1].toFixed(2)}" r="${(5.6 - inset / 2).toFixed(2)}" fill="${stroke}"/>`;
}

let n = 0;
// opts: light [top, mid, bottom], knock, glow, line (outline width), vb, floor (y or false), props (fn(j) → svg), ghost (pose drawn faint behind)
function titanPose(size, pose, o = {}) {
  const id = `tp${++n}`;
  const {
    light = ['#FFFFFF', '#DFFBF6', '#9FEADC'], knock = 'rgba(6,14,13,.96)', glow = 'rgba(125,227,209,.55)', line = 1.15,
    vb = '0 0 100 100', floor = 92, props = null, ghost = null, ghostOpacity = .22, far = .5, w = size, h = size,
  } = o;
  const j = joints(pose);
  const grad = `<linearGradient id="${id}g" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="0" y2="100"><stop offset="0" stop-color="${light[0]}"/><stop offset=".55" stop-color="${light[1]}"/><stop offset="1" stop-color="${light[2]}"/></linearGradient>`;
  const L2 = line * 2;
  const figure = (jj, op = 1, glowOn = true) => `
    <g opacity="${op * far}">${limbs(jj, 'F', `url(#${id}g)`, 0)}${limbs(jj, 'F', knock, L2)}</g>
    <g opacity="${op}" ${glowOn && glow ? `style="filter:drop-shadow(0 0 .8px ${glow}) drop-shadow(0 0 3px ${glow})"` : ''}>
      ${core(jj, `url(#${id}g)`, 0)}${limbs(jj, 'N', `url(#${id}g)`, 0)}
    </g>
    <g opacity="${op}">${core(jj, knock, L2)}${limbs(jj, 'N', knock, L2)}
      ${seg(jj.hip, jj.sh, .5, `url(#${id}g)`, 'opacity=".35"')}</g>`;
  return `<svg width="${w}" height="${h}" viewBox="${vb}" style="overflow:visible;display:block">
    <defs>${grad}</defs>
    ${floor !== false ? `<line x1="-4" y1="${floor}" x2="104" y2="${floor}" stroke="rgba(255,255,255,.14)" stroke-width=".5"/>` : ''}
    ${ghost ? figure(joints(ghost), ghostOpacity, false) : ''}
    ${props ? props(j, `url(#${id}g)`) : ''}
    ${figure(j)}
  </svg>`;
}

// Props drawn in the same light, thinner. Each returns svg for given joints.
const P = {
  kettlebell: (at = 'aN', dx = 1.5, dy = 2.5) => (j, g) => {
    const h = j[at].h; const c = [h[0] + dx, h[1] + dy];
    return `<g fill="none" stroke="${g}" stroke-width=".9" opacity=".95"><circle cx="${c[0]}" cy="${c[1] + 2.6}" r="3.6" fill="rgba(6,14,13,.9)"/><path d="M${c[0] - 2} ${c[1]} q2 -3.2 4 0"/></g>`;
  },
  dumbbell: (at = 'aN') => (j, g) => {
    const h = j[at].h;
    return `<g stroke="${g}" stroke-linecap="round"><line x1="${h[0] - 3.4}" y1="${h[1]}" x2="${h[0] + 3.4}" y2="${h[1]}" stroke-width="1"/><line x1="${h[0] - 3.6}" y1="${h[1] - 2.4}" x2="${h[0] - 3.6}" y2="${h[1] + 2.4}" stroke-width="2.2"/><line x1="${h[0] + 3.6}" y1="${h[1] - 2.4}" x2="${h[0] + 3.6}" y2="${h[1] + 2.4}" stroke-width="2.2"/></g>`;
  },
  bar: (y = 9) => (j, g) => `<g stroke="${g}" stroke-linecap="round"><line x1="8" y1="${y}" x2="92" y2="${y}" stroke-width="1.3"/><line x1="10" y1="${y}" x2="10" y2="${y + 80}" stroke-width=".6" opacity=".35"/><line x1="90" y1="${y}" x2="90" y2="${y + 80}" stroke-width=".6" opacity=".35"/></g>`,
  bench: (x, y, w, h) => (j, g) => `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="1.2" fill="rgba(255,255,255,.04)" stroke="${g}" stroke-width=".6" opacity=".7"/>`,
  all: (...fs) => (j, g) => fs.map((f) => f(j, g)).join(''),
};

// Key poses used in the mockups (keys match content/training/exercises.yaml animation.poses).
const POSES = {
  'ex-goblet-squat': {
    stand: { root: [50, 49], torso: 0, head: 0, armN: [170, 15], armF: [172, 18], legN: [180, 180, 90], legF: [183, 178, 92] },
    bottom: { root: [41.3, 70.2], torso: 40, head: 28, armN: [165, 10], armF: [168, 14], legN: [100, 215, 90], legF: [104, 212, 92] },
  },
  'ex-pullup': {
    hang: { root: [50, 62], torso: 0, head: 0, armN: [0, 0], armF: [2, 2], legN: [178, 176, 160], legF: [180, 178, 162] },
    mid: { root: [51.5, 49.6], torso: 350, head: 355, armN: [67.4, 318], armF: [70, 320], legN: [172, 192, 160], legF: [175, 195, 162] },
    top: { root: [48.6, 40.7], torso: 352, head: 0, armN: [118.6, 333.4], armF: [121, 335], legN: [174, 190, 160], legF: [177, 193, 162] },
  },
  'ex-pushup': {
    top: { root: [47.6, 72.4], torso: 66.6, head: 72, armN: [180, 180], armF: [182, 181], legN: [246.6, 246.6, 160], legF: [247.5, 247, 162] },
    bottom: { root: [50.9, 85.9], torso: 85.8, head: 90, armN: [295, 150], armF: [297, 152], legN: [265.8, 265.8, 170], legF: [266.5, 266, 172] },
  },
  'ex-kb-swing': {
    hike: { root: [40, 52], torso: 50, head: 60, armN: [190, 200], armF: [192, 202], legN: [168, 190, 90], legF: [171, 188, 92] },
    float: { root: [50, 49], torso: 0, head: 0, armN: [90, 90], armF: [92, 92], legN: [180, 180, 90], legF: [183, 178, 92] },
  },
  'ex-front-lever-tuck': {
    tuck: { root: [16, 36], torso: 90, head: 95, armN: [21.8, 21.8], armF: [23, 23], legN: [60, 250, 230], legF: [62, 252, 232] },
  },
  'ex-db-row': {
    bottom: { root: [38, 52], torso: 80, head: 86, armN: [182, 180], armF: [180, 180], legN: [182, 180, 90], legF: [180, 270, 270] },
  },
  'ex-stretch-pigeon': {
    fold: { root: [44, 78], torso: 70, head: 80, armN: [160, 95], armF: [162, 97], legN: [100, 220, 240], legF: [262, 268, 265] },
  },
};

module.exports = { titanPose, joints, POSES, P, L };
