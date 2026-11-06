// SVG art for concept v3 «Ось»: the axis glyph (mark reduced to circle + two columns + pulse),
// progress rings, mode glyphs, pulse lines, icons. Pure functions returning SVG strings.

let uid = 0;
const nid = (p) => `${p}${++uid}`;

// Pulse polyline in a 0..100 x-space: flat → QRS outside the columns, small one inside, flat.
const PULSE = [[0, 0], [12, 0], [15, -3], [18, 5], [21, -17], [24, 7], [26, 0], [44, 0], [46.5, -3], [49, 4], [51.5, -10], [54, 3], [56, 0], [74, 0], [76, -3], [79, 5], [82, -17], [85, 7], [88, 0], [100, 0]];
function pulsePath(x0, y0, w, amp = 1, pts = PULSE) {
  return pts.map(([x, y], i) => `${i ? 'L' : 'M'}${(x0 + (x / 100) * w).toFixed(1)} ${(y0 + y * amp).toFixed(1)}`).join(' ');
}

// The axis glyph: the HoT mark reduced to geometry. Circle (measure), two columns (support), pulse (life).
// opts: col (stroke), sw (stroke width in viewBox 100), glow, pulse (bool), colsOnly, progress (0..1 circle), cols (0..1 growth), pulseT (0..1 drawn)
function axisGlyph(size, { col = 'currentColor', sw = 3, glow = false, pulse = true, progress = 1, cols = 1, pulseT = 1, circleOpacity = 1, colOpacity = 1, pulseCol, head = false } = {}) {
  const id = nid('ax');
  const C = 2 * Math.PI * 44;
  const colH = 56 * cols;
  const yb = 80; // column base
  const column = (x) => `
    <g opacity="${colOpacity}">
      <path d="M${x - 5} ${yb} H${x + 5}" />
      ${cols > 0 ? `<path d="M${x - 3} ${yb} V${yb - colH}" /><path d="M${x + 3} ${yb} V${yb - colH}" />` : ''}
      ${cols >= .98 ? `<path d="M${x - 6} ${yb - colH - 1} H${x + 6}" /><path d="M${x - 4.2} ${yb - colH - 4} H${x + 4.2}" />` : ''}
    </g>`;
  const pLen = 140;
  return `<svg width="${size}" height="${size}" viewBox="0 0 100 100" fill="none" stroke="${col}" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round" ${glow ? `style="filter:drop-shadow(0 0 ${size / 40}px ${glow}) drop-shadow(0 0 ${size / 14}px ${glow})"` : ''}>
    <circle cx="50" cy="50" r="44" opacity="${circleOpacity}" stroke-dasharray="${(C * progress).toFixed(1)} ${C.toFixed(1)}" transform="rotate(-90 50 50)"/>
    ${column(34)}${column(66)}
    ${pulse ? `<path d="${pulsePath(8, 56, 84, .9)}" stroke="${pulseCol || col}" stroke-width="${sw * .8}" pathLength="${pLen}" stroke-dasharray="${(pLen * pulseT).toFixed(1)} ${pLen}"/>` : ''}
    ${head ? `<circle cx="${head[0]}" cy="${head[1]}" r="${sw * 1.1}" fill="${pulseCol || col}" stroke="none" style="filter:drop-shadow(0 0 3px ${pulseCol || col}) drop-shadow(0 0 8px ${pulseCol || col})"/>` : ''}
  </svg>`;
}

// Progress ring. pct 0..1. opts: track, glow, cap (round), dot (norm marker pct)
function ring(size, sw, pct, color, { track = 'var(--track)', glow = false, grad = null, dot = null, dotCol = null, rot = -90 } = {}) {
  const r = (size - sw) / 2 - (glow ? 0 : 0);
  const c = size / 2;
  const C = 2 * Math.PI * r;
  const id = nid('rg');
  const stroke = grad ? `url(#${id})` : color;
  let dotSvg = '';
  if (dot != null) {
    const a = (rot + 360 * dot) * Math.PI / 180;
    dotSvg = `<circle cx="${(c + r * Math.cos(a)).toFixed(2)}" cy="${(c + r * Math.sin(a)).toFixed(2)}" r="${sw * .32}" fill="${dotCol || 'var(--fg)'}"/>`;
  }
  return `<svg width="${size}" height="${size}" viewBox="0 0 ${size} ${size}" style="overflow:visible">
    ${grad ? `<defs><linearGradient id="${id}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="${grad[0]}"/><stop offset="1" stop-color="${grad[1]}"/></linearGradient></defs>` : ''}
    <circle cx="${c}" cy="${c}" r="${r}" fill="none" stroke="${track}" stroke-width="${sw}"/>
    <circle cx="${c}" cy="${c}" r="${r}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="round"
      stroke-dasharray="${(C * pct).toFixed(2)} ${C.toFixed(2)}" transform="rotate(${rot} ${c} ${c})"
      ${glow ? `style="filter:drop-shadow(0 0 ${sw}px ${glow}) drop-shadow(0 0 ${sw * 3}px ${glow})"` : ''}/>
    ${dotSvg}
  </svg>`;
}

// Mode glyphs: the sun relative to the horizon (kept from A36, redrawn as monoline).
function modeGlyph(mode, size = 20, color = 'currentColor', sw = 1.6) {
  const s = `fill="none" stroke="${color}" stroke-width="${sw}" stroke-linecap="round"`;
  const g = {
    razgon: `<path d="M3 17h18" ${s}/><circle cx="12" cy="10.5" r="3.6" ${s}/><path d="M12 3.2v1.6M6.2 5.7l1.1 1.1M17.8 5.7l-1.1 1.1M3.8 11h1.6M18.6 11h1.6" ${s}/>`,
    rovno: `<path d="M3 15h18" ${s}/><path d="M7 15a5 5 0 0 1 10 0" ${s}/><path d="M7.5 19h9" ${s} opacity=".5"/>`,
    berezhno: `<path d="M3 11h18" ${s}/><path d="M7 11a5 5 0 0 0 10 0" ${s} stroke-dasharray="1.6 2.4"/><path d="M7.5 7h9" ${s} opacity=".5"/>`,
  }[mode];
  return `<svg width="${size}" height="${size}" viewBox="0 0 24 24">${g}</svg>`;
}

const ICONS = {
  today: '<path d="M3 16h18"/><path d="M7 16a5 5 0 0 1 10 0"/><path d="M12 5v2.2M5.6 8.1l1.4 1.4M18.4 8.1 17 9.5"/>',
  mentor: '<path d="M20 11.5c0 4.1-3.6 7.5-8 7.5-1.2 0-2.3-.2-3.3-.6L4 20l1.3-3.8A7.2 7.2 0 0 1 4 11.5C4 7.4 7.6 4 12 4s8 3.4 8 7.5Z"/>',
  week: '<path d="M5 19V9M9.7 19V6M14.3 19v-8M19 19V8"/>',
  me: '<circle cx="12" cy="8.5" r="3.6"/><path d="M5 20c.8-3.6 3.6-5.5 7-5.5s6.2 1.9 7 5.5"/>',
  bolt: '<path d="M13 3 5.5 13.2h5.6L10 21l7.5-10.2h-5.6L13 3Z"/>',
  steps: '<path d="M8.5 4.5c1.7 0 2.6 2 2.3 4.4-.2 1.6-1 2.6-2.4 2.6S6 10.5 6 8.9C6 6.5 6.9 4.5 8.5 4.5Z"/><path d="M6.6 14.2h3.8l-.4 2.6c-.2 1.3-1 2-1.9 2s-1.7-.8-1.6-2.1l.1-2.5Z"/><path d="M15.5 7.5c1.6 0 2.5 1.8 2.3 4-.2 1.5-1 2.5-2.3 2.5s-2.2-1-2.2-2.5c0-2.2.6-4 2.2-4Z"/><path d="M13.7 16.8h3.7l-.4 2.3c-.2 1.2-1 1.9-1.8 1.9s-1.6-.7-1.6-1.9l.1-2.3Z"/>',
  moon: '<path d="M19.5 14.6A7.6 7.6 0 0 1 9.4 4.5a7.6 7.6 0 1 0 10.1 10.1Z"/>',
  check: '<path d="m5 12.5 4.3 4.3L19 7.2"/>',
  dots: '<circle cx="12" cy="5.5" r="1.2" fill="currentColor"/><circle cx="12" cy="12" r="1.2" fill="currentColor"/><circle cx="12" cy="18.5" r="1.2" fill="currentColor"/>',
  mic: '<rect x="9" y="3.5" width="6" height="11" rx="3"/><path d="M5.5 11.5a6.5 6.5 0 0 0 13 0M12 18v2.5"/>',
  close: '<path d="M6 6l12 12M18 6 6 18"/>',
  arrow: '<path d="M5 12h14M13 6l6 6-6 6"/>',
  back: '<path d="M19 12H5M11 6l-6 6 6 6"/>',
  wifi: '<path d="M2.5 9a14 14 0 0 1 19 0M5.5 12.3a9.5 9.5 0 0 1 13 0M8.6 15.5a5 5 0 0 1 6.8 0"/><circle cx="12" cy="18.6" r="1" fill="currentColor"/>',
  watch: '<circle cx="12" cy="12" r="5.5"/><path d="M9 6.8 9.6 3h4.8l.6 3.8M9 17.2l.6 3.8h4.8l.6-3.8"/>',
  band: '<rect x="8" y="2.5" width="8" height="19" rx="4"/><path d="M8 9.5h8M8 14.5h8"/>',
  lock: '<rect x="5.5" y="10.5" width="13" height="9.5" rx="2.5"/><path d="M8.5 10.5V8a3.5 3.5 0 0 1 7 0v2.5"/>',
  sun: '<circle cx="12" cy="12" r="4"/><path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6 7 7M17 17l1.4 1.4M5.6 18.4 7 17M17 7l1.4-1.4"/>',
  spark: '<path d="M12 3.5 13.6 10.4 20.5 12 13.6 13.6 12 20.5 10.4 13.6 3.5 12 10.4 10.4 12 3.5Z"/>',
  play: '<path d="M8 5.5v13l10.5-6.5L8 5.5Z"/>',
  haptic: '<path d="M8 6v12M12 3.5v17M16 6v12M4 9.5v5M20 9.5v5"/>',
  layers: '<path d="m12 4 8.5 4.5L12 13 3.5 8.5 12 4Z"/><path d="m3.5 12.5 8.5 4.5 8.5-4.5"/>',
  wand: '<path d="m4 20 11-11M14 4v2.5M19.5 9.5H17M17.9 5.6l-1.6 1.6"/>',
  eye: '<path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12Z"/><circle cx="12" cy="12" r="2.8"/>',
  widget: '<rect x="3.5" y="3.5" width="7.5" height="7.5" rx="2"/><rect x="13" y="3.5" width="7.5" height="7.5" rx="3.75"/><rect x="3.5" y="13" width="17" height="7.5" rx="2"/>',
  dim: '<path d="M12 3a9 9 0 1 0 0 18V3Z" fill="currentColor" stroke="none" opacity=".35"/><circle cx="12" cy="12" r="9"/>',
  type: '<path d="M5 7V5h14v2M12 5v14M9 19h6"/>',
  ripple: '<circle cx="12" cy="12" r="2.5"/><circle cx="12" cy="12" r="6" opacity=".6"/><circle cx="12" cy="12" r="9.5" opacity=".3"/>',
  quote: '<path d="M4 7h7v6l-3 4M13 7h7v6l-3 4"/>',
  plate: '<circle cx="12" cy="12" r="8.5"/><circle cx="12" cy="12" r="4.5"/>',
  roll: '<path d="M7 4v16M17 4v16"/><path d="M4 9h6M14 15h6"/>',
};
function icon(name, size = 24, sw = 1.6, color = 'currentColor') {
  return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="${color}" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round">${ICONS[name]}</svg>`;
}

// Slender column silhouette for the dawn horizon (the single antique accent), drawn as a monoline with rim light.
function columnSil(h, w, { rim = 'rgba(255,214,160,.55)' } = {}) {
  const id = nid('cs');
  const cw = w * .56; const x0 = (w - cw) / 2;
  const f = `url(#${id})`;
  return `<svg width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">
    <defs><linearGradient id="${id}" x1="0" x2="1"><stop offset="0" stop-color="#05060A"/><stop offset=".7" stop-color="#0D0E14"/><stop offset="1" stop-color="#1A1517"/></linearGradient>
    <linearGradient id="${id}r" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${rim}" stop-opacity=".15"/><stop offset=".75" stop-color="${rim}"/><stop offset="1" stop-color="${rim}" stop-opacity=".9"/></linearGradient></defs>
    <rect x="0" y="0" width="${w}" height="3" rx="1" fill="${f}"/><path d="M${w * .1} 3 H${w * .9} L${w * .8} 8 H${w * .2} Z" fill="${f}"/>
    <path d="M${x0} 8 H${x0 + cw} L${x0 + cw - 1.2} ${h - 9} H${x0 + 1.2} Z" fill="${f}"/>
    <rect x="${w * .12}" y="${h - 9}" width="${w * .76}" height="4" fill="${f}"/><rect x="0" y="${h - 5}" width="${w}" height="5" rx="1" fill="${f}"/>
    <path d="M${x0 + cw - .7} 9 L${x0 + cw - 1.8} ${h - 9}" stroke="url(#${id}r)" stroke-width="1.1"/>
    <path d="M${w * .9 - .5} 3.5 L${w * .8} 8" stroke="${rim}" stroke-width=".8" opacity=".7"/>
    <path d="M${w - .5} ${h - 5} V${h}" stroke="${rim}" stroke-width="1"/>
    ${[.3, .55, .8].map((k) => `<path d="M${x0 + cw * k} 10 V${h - 11}" stroke="rgba(255,220,180,${k > .7 ? .10 : .04})" stroke-width=".8"/>`).join('')}
  </svg>`;
}


// ---------------------------------------------------------------- Titan pictograms (24 grid, monoline 1.6)
// Base grammar: two columns (support) + the Titan (head, arms reaching the capitals, drape-free body).
const COLS = 'M4 5v14.5M20 5v14.5M2.6 4.6h2.8M18.6 4.6h2.8M2.6 19.6h2.8M18.6 19.6h2.8';
const T_HEAD = (cx = 12, cy = 6.4, r = 1.7) => `<circle cx="${cx}" cy="${cy}" r="${r}"/>`;
const T_BODY = 'M5.6 6.4C8.2 8.7 9.7 9.5 12 9.5s3.8-.8 6.4-3.1M12 9.5v4.9M12 14.4l-2.1 5.1M12 14.4l2.1 5.1';
const PICTO = {
  // spheres
  body: `<path d="${COLS}"/>${T_HEAD()}<path d="${T_BODY}"/>`,
  sleep: `<path d="${COLS}"/><circle cx="7.6" cy="16.2" r="1.4"/><path d="M9.4 16.6h8.6M9.4 18.4h8.6"/><path d="M13.2 6.6a3.2 3.2 0 1 0 3.6 3.9 2.6 2.6 0 0 1-3.6-3.9Z"/>`,
  work: `<path d="M2.6 4.6h18.8M4 5v14.5M20 5v14.5M2.6 19.6h2.8M18.6 19.6h2.8"/><path d="M9.6 4.9 10.4 9.6M14.4 4.9 13.6 9.6"/><circle cx="12" cy="7.6" r="1.5"/><path d="M10.4 9.6h3.2M12 9.6v4.8M12 14.4l-2.1 5.1M12 14.4l2.1 5.1"/>`,
  food: `<path d="${COLS}"/>${T_HEAD(12, 6.6, 1.6)}<path d="M12 8.4v2.4M7.4 11.6h9.2a4.6 4.6 0 0 1-9.2 0Z"/><path d="M10 19.4h4"/>`,
  people: `<path d="${COLS}"/><circle cx="9" cy="7.4" r="1.4"/><circle cx="15" cy="7.4" r="1.4"/><path d="M5.6 7.2 9 10.4l3 1 3-1 3.4-3.2M9 9.4v5M15 9.4v5M9 14.4l-1.3 5.1M9 14.4l1.3 5.1M15 14.4l-1.3 5.1M15 14.4l1.3 5.1"/>`,
  mind: `<path d="${COLS}"/><circle cx="12" cy="10.4" r="2.4"/><circle cx="12" cy="10.4" r="5.2" stroke-dasharray="1.4 2"/><path d="M7.6 19.5c.6-2.6 2.2-3.8 4.4-3.8s3.8 1.2 4.4 3.8"/>`,
  goal: `<path d="${COLS}"/>${T_HEAD(11, 6.6, 1.6)}<path d="M11 8.4l1 5M12 13.4l-2.6 2.4-.4 3.7M12 13.4l3 2.2.8 3.8M11.2 9.6 7.6 11M11.4 9.4l3.4-2.6 2.4.2"/>`,
  // modes: the sun is a ring around the Titan
  razgon: `<path d="M2 19.6h20"/><circle cx="12" cy="11.2" r="6.6"/><path d="M12 1.6v1.4M4.2 4.4l1 1M19.8 4.4l-1 1"/>${T_HEAD(12, 9.6, 1.3)}<path d="M8.2 9.8 12 12l3.8-2.2M12 12v3.6M12 15.6l-1.4 4M12 15.6l1.4 4"/>`,
  rovno: `<path d="M2 19.6h20"/><path d="M4.6 19.6a7.4 7.4 0 0 1 14.8 0"/>${T_HEAD(12, 10.6, 1.3)}<path d="M8.6 11.4 12 13.2l3.4-1.8M12 13.2v3M12 16.2l-1.3 3.4M12 16.2l1.3 3.4"/>`,
  berezhno: `<path d="M2 14.6h20"/><path d="M4.6 14.6a7.4 7.4 0 0 0 14.8 0" stroke-dasharray="1.6 2.2"/>${T_HEAD(12, 6.4, 1.3)}<path d="M12 8v3.6M10 9.6 12 9l2 .6M12 11.6l-2.4 3M12 11.6l2.4 3"/>`,
  // statuses
  seal: `<circle cx="12" cy="12" r="8.6"/><path d="m8.2 12.2 2.6 2.6 5-5.2"/>`,
  self: `<circle cx="12" cy="12" r="8.6" stroke-dasharray="2 2.2"/><path d="m8.2 12.2 2.6 2.6 5-5.2"/>`,
  changed: `<path d="M5 9h13l-3-3M19 15H6l3 3"/>`,
  now: `<circle cx="12" cy="12" r="8.6"/><circle cx="12" cy="12" r="3" fill="currentColor"/>`,
  nodata: `<path d="${COLS}" opacity=".5"/><g stroke-dasharray="1.2 2">${T_HEAD()}<path d="${T_BODY}"/></g>`,
  // feelings after training
  easy: `<path d="${COLS}" opacity=".55"/>${T_HEAD(12, 7.4, 1.5)}<path d="M8.6 2.8 10.4 9.4h3.2l1.8-6.6M12 9.4v5M12 14.4l-2.4 5.1M12 14.4l2.4 5.1"/>`,
  steady: `<path d="${COLS}"/>${T_HEAD()}<path d="${T_BODY}"/>`,
  heavy: `<path d="M2.6 8.4h18.8M4 8.8v10.7M20 8.8v10.7M2.6 19.6h2.8M18.6 19.6h2.8"/><circle cx="12" cy="11.2" r="1.5"/><path d="M9.4 8.7 10.6 12.4M14.6 8.7 13.4 12.4M10.6 12.6h2.8M12 12.6v3.2M12 15.8l-2.6 1.4.6 2.4M12 15.8l2.6 1.4-.6 2.4"/>`,
  // camera poses
  front: `${T_HEAD(12, 4.6, 1.7)}<path d="M12 6.4v7.6M7 9.2 12 8l5 1.2M7 9.2l-1.4 4.6M17 9.2l1.4 4.6M12 14l-2 6M12 14l2 6"/>`,
  side: `${T_HEAD(12, 4.6, 1.7)}<path d="M13.6 4.6h.8M12 6.4v7.6M12 8.6l1.2 5M12 14l-.6 6M12 14l1.4 6"/>`,
  back: `${T_HEAD(12, 4.6, 1.7)}<path d="M12 6.4v7.6M7 9.2 12 8l5 1.2M7 9.2l-1.4 4.6M17 9.2l1.4 4.6M12 14l-2 6M12 14l2 6"/><path d="M12 7.6v6" stroke-dasharray="1 1.4" stroke-width="2.6" opacity=".45"/>`,
  // data
  heart: `<path d="M12 19.2S4 14.6 4 9.2A4 4 0 0 1 12 7.6a4 4 0 0 1 8 1.6c0 5.4-8 10-8 10Z"/><path d="M7.2 12h2.6l1.2-2 2 4 1.2-2h2.6"/>`,
  charge: `<circle cx="12" cy="12" r="8.6" stroke-dasharray="40 100" transform="rotate(-90 12 12)"/><circle cx="12" cy="12" r="8.6" opacity=".3"/><path d="M12.8 7.6 9.8 12.4h2.8l-.8 4 3-4.8H12l.8-4Z"/>`,
  ceiling: `<path d="M3 6.4h18" stroke-dasharray="2 2"/><path d="M4 19.6c3-1.2 4.4-6 6.4-6s2.6 3 4.2 3 2.6-4.4 5.4-6.6"/>`,
  steps: `<path d="M8.5 4.5c1.7 0 2.6 2 2.3 4.4-.2 1.6-1 2.6-2.4 2.6S6 10.5 6 8.9C6 6.5 6.9 4.5 8.5 4.5Z"/><path d="M15.5 7.5c1.6 0 2.5 1.8 2.3 4-.2 1.5-1 2.5-2.3 2.5s-2.2-1-2.2-2.5c0-2.2.6-4 2.2-4Z"/><path d="M7 14.4h3.4M13.6 17h3.4"/>`,
  zones: `<path d="M4 18V14M9 18V9M14 18V11M19 18v-3"/>`,
  // camera checks
  level: `<path d="M3 12h18"/><circle cx="12" cy="12" r="3"/><path d="M6 8v8M18 8v8"/>`,
  distance: `<path d="M4 12h16M7 9l-3 3 3 3M17 9l3 3-3 3"/>`,
  lightq: `<circle cx="12" cy="12" r="3.6"/><path d="M12 3.5v2M12 18.5v2M3.5 12h2M18.5 12h2M6 6l1.4 1.4M16.6 16.6 18 18M6 18l1.4-1.4M16.6 7.4 18 6"/>`,
  flip: `<path d="M4 8.5h11.5a4 4 0 0 1 0 8H9"/><path d="m7 5.5-3 3 3 3"/>`,
};
function picto(name, size = 24, sw = 1.6, color = 'currentColor') {
  return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="${color}" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round">${PICTO[name]}</svg>`;
}

module.exports = { pulsePath, axisGlyph, ring, modeGlyph, icon, columnSil, PULSE, picto, PICTO };
