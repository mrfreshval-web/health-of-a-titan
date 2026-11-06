// Gold hairline art generators for the HoT visual language (A36).
// All functions return SVG markup strings. Coordinates are CSS px (= dp).

const f = (n) => Math.round(n * 100) / 100;

/** ECG pulse line: flat baseline with `beats` heartbeats, signature motif of the brand. */
function pulsePath(x0, y, w, beats = 3, amp = 18, startFlat = 0.1) {
  const tpl = [[0, 0], [0.3, 0], [0.34, -0.12], [0.38, 0], [0.45, 0], [0.48, 0.16], [0.52, -1], [0.56, 0.45],
    [0.59, 0], [0.68, 0], [0.75, -0.22], [0.82, 0], [1, 0]];
  const flat = w * startFlat;
  const bw = (w - 2 * flat) / beats;
  let d = `M${f(x0)} ${f(y)} L${f(x0 + flat)} ${f(y)}`;
  for (let b = 0; b < beats; b++) {
    const bx = x0 + flat + b * bw;
    for (const [u, v] of tpl.slice(1)) d += ` L${f(bx + u * bw)} ${f(y + v * amp)}`;
  }
  d += ` L${f(x0 + w)} ${f(y)}`;
  return d;
}

/**
 * «Монограмма дня» as a classical rosette: 8 almond petals clockwise from 12 o'clock
 * (Заряд, Ритм, Ясность, Погода, Связи, Топливо, Рюкзак, Весы). level −2…+2, null = no data (dashed).
 */
function monogram(levels, size, opts = {}) {
  const { glow = false, ring = true, color = 'var(--gold)', id = 'm' + Math.random().toString(36).slice(2, 7), labels = false } = opts;
  const R = size / 2, c = R;
  const rIn = R * 0.13, rNorm = R * 0.56, rMax = R * 0.9, rMin = R * 0.3;
  const len = (l) => (l >= 0 ? rNorm + (l / 2) * (rMax - rNorm) : rNorm + (l / 2) * (rNorm - rMin));
  const pt = (r, deg) => { const a = (deg - 90) * Math.PI / 180; return [c + r * Math.cos(a), c + r * Math.sin(a)]; };
  let petals = '';
  levels.forEach((l, i) => {
    const ang = i * 45;
    const known = l !== null && l !== undefined;
    const r = known ? len(l) : rNorm;
    const petal = (rr, spread, k = 0.6) => {
      const [x0, y0] = pt(rIn, ang), [xt, yt] = pt(rr, ang);
      const [xl, yl] = pt(rr * k, ang - spread), [xr, yr] = pt(rr * k, ang + spread);
      return `M${f(x0)} ${f(y0)} Q${f(xl)} ${f(yl)} ${f(xt)} ${f(yt)} Q${f(xr)} ${f(yr)} ${f(x0)} ${f(y0)} Z`;
    };
    const [xt, yt] = pt(r, ang);
    if (known) {
      petals += `<path d="${petal(r, 19)}" fill="url(#${id}g)" stroke="${color}" stroke-width="1.2" stroke-linejoin="round"/>` +
        `<path d="${petal(r * 0.7, 8, 0.55)}" fill="none" stroke="${color}" stroke-width=".7" opacity=".7"/>` +
        `<circle cx="${f(xt)}" cy="${f(yt)}" r="${f(Math.max(1.3, R * 0.025))}" fill="${color}"/>`;
    } else {
      petals += `<path d="${petal(r, 16)}" fill="none" stroke="var(--ivory)" stroke-width=".9" stroke-dasharray="1.4 2.6" opacity=".5"/>` +
        `<circle cx="${f(xt)}" cy="${f(yt)}" r="${f(Math.max(1, R * 0.018))}" fill="none" stroke="var(--ivory)" stroke-width=".8" opacity=".5"/>`;
    }
  });
  const ringSvg = ring ? `<circle cx="${c}" cy="${c}" r="${f(R - 1)}" fill="none" stroke="${color}" stroke-width=".8" opacity=".45"/>` : '';
  const ticks = Array.from({ length: 8 }, (_, i) => {
    const [a1, b1] = pt(R - 1, i * 45 + 22.5), [a2, b2] = pt(R - 5, i * 45 + 22.5);
    return `<path d="M${f(a1)} ${f(b1)} L${f(a2)} ${f(b2)}" stroke="${color}" stroke-width=".7" opacity=".4"/>`;
  }).join('');
  return `<svg width="${size}" height="${size}" viewBox="0 0 ${size} ${size}" class="${glow ? 'glow' : ''}" role="img" aria-label="Монограмма дня">
  <defs><radialGradient id="${id}g" cx="${c}" cy="${c}" r="${R}" gradientUnits="userSpaceOnUse">
  <stop offset="0" stop-color="#F3E2B0" stop-opacity=".30"/><stop offset="1" stop-color="#D9B562" stop-opacity=".06"/></radialGradient></defs>
  ${ringSvg}${ticks}
  <circle cx="${c}" cy="${c}" r="${f(rNorm)}" fill="none" stroke="var(--ivory)" stroke-width=".7" stroke-dasharray="1 2.4" opacity=".35"/>
  ${petals}
  <circle cx="${c}" cy="${c}" r="${f(rIn * 0.75)}" fill="var(--bg)" stroke="${color}" stroke-width="1"/>
  <circle cx="${c}" cy="${c}" r="${f(rIn * 0.28)}" fill="${color}"/>
  </svg>`;
}

/** Laurel branch along an arc (degrees, 0 = up, clockwise). Used as a frame, never as a reward. */
function laurel(cx, cy, r, a0, a1, n, leaf = 9, color = 'var(--gold)', mirror = false) {
  const pt = (rr, deg) => { const a = (deg - 90) * Math.PI / 180; return [cx + rr * Math.cos(a), cy + rr * Math.sin(a)]; };
  const steps = 40;
  let stem = '';
  for (let i = 0; i <= steps; i++) {
    const [x, y] = pt(r, a0 + (a1 - a0) * i / steps);
    stem += (i ? ' L' : 'M') + f(x) + ' ' + f(y);
  }
  let leaves = '';
  for (let i = 0; i < n; i++) {
    const t = (i + 0.5) / n;
    const deg = a0 + (a1 - a0) * t;
    const [x, y] = pt(r, deg);
    const dir = a1 > a0 ? 1 : -1;
    const tangent = deg + 90 * dir; // direction of travel
    for (const side of [-1, 1]) {
      const rot = tangent - 90 + side * 38 * dir;
      const L = leaf * (0.75 + 0.25 * Math.sin(t * Math.PI));
      leaves += `<path transform="translate(${f(x)} ${f(y)}) rotate(${f(rot)})" d="M0 0 Q${f(L * 0.32)} ${f(-L * 0.5)} 0 ${f(-L)} Q${f(-L * 0.32)} ${f(-L * 0.5)} 0 0Z" fill="none" stroke="${color}" stroke-width=".9"/>`;
    }
  }
  return `<path d="${stem}" fill="none" stroke="${color}" stroke-width=".9"/>${leaves}`;
}

/** Simplified Ionic column (hairline). x,y = top-left of capital, w = capital width, h = total height. */
function column(x, y, w, h, color = 'var(--gold)', sw = 1, detail = true) {
  const shaftW = w * 0.62, sx = x + (w - shaftW) / 2;
  const capH = w * 0.32, baseH = w * 0.22;
  const shaftTop = y + capH, shaftBot = y + h - baseH;
  const vr = w * 0.11;
  let flutes = '';
  const nf = detail ? 5 : 1;
  for (let i = 1; i < nf; i++) {
    const fx = sx + shaftW * i / nf;
    flutes += `<path d="M${f(fx)} ${f(shaftTop + 3)} L${f(fx)} ${f(shaftBot - 3)}"/>`;
  }
  return `<g fill="none" stroke="${color}" stroke-width="${sw}" stroke-linecap="round">
    <path d="M${f(x)} ${f(y)} H${f(x + w)}"/>
    <path d="M${f(x + vr)} ${f(y + capH * 0.42)} H${f(x + w - vr)}"/>
    ${detail ? `<circle cx="${f(x + vr * 1.1)}" cy="${f(y + capH * 0.62)}" r="${f(vr)}"/><circle cx="${f(x + vr * 1.1)}" cy="${f(y + capH * 0.62)}" r="${f(vr * 0.35)}"/>
    <circle cx="${f(x + w - vr * 1.1)}" cy="${f(y + capH * 0.62)}" r="${f(vr)}"/><circle cx="${f(x + w - vr * 1.1)}" cy="${f(y + capH * 0.62)}" r="${f(vr * 0.35)}"/>` : ''}
    <path d="M${f(sx - 2)} ${f(shaftTop)} H${f(sx + shaftW + 2)}"/>
    <path d="M${f(sx)} ${f(shaftTop)} V${f(shaftBot)} M${f(sx + shaftW)} ${f(shaftTop)} V${f(shaftBot)}"/>
    ${flutes}
    <path d="M${f(sx - 3)} ${f(shaftBot)} H${f(sx + shaftW + 3)}"/>
    <path d="M${f(x + w * 0.04)} ${f(y + h - baseH * 0.45)} H${f(x + w * 0.96)}"/>
    <path d="M${f(x - 1)} ${f(y + h)} H${f(x + w + 1)}"/>
  </g>`;
}

/** App mark used in notifications and the launcher: two columns + pulse crossbar = «H». */
function hMark(size, color = 'var(--gold)', bg = null) {
  const s = size;
  const detail = s >= 64;
  const colW = s * (detail ? 0.2 : 0.16), colH = s * 0.54, y = s * 0.23;
  const sw = detail ? Math.max(0.8, s / 50) : Math.max(1, s / 30);
  return `<svg width="${s}" height="${s}" viewBox="0 0 ${s} ${s}">
    ${bg ? `<circle cx="${s / 2}" cy="${s / 2}" r="${s / 2}" fill="${bg}"/>` : ''}
    <circle cx="${s / 2}" cy="${s / 2}" r="${f(s * 0.44)}" fill="none" stroke="${color}" stroke-width="${f(sw * 0.8)}" opacity=".8"/>
    ${column(s * (detail ? 0.24 : 0.27), y, colW, colH, color, sw, detail)}
    ${column(s * (detail ? 0.56 : 0.57), y, colW, colH, color, sw, detail)}
    <path d="${pulsePath(s * 0.1, s * 0.52, s * 0.8, 1, s * 0.12, 0.28)}" fill="none" stroke="${color}" stroke-width="${f(sw * 1.1)}" stroke-linejoin="round"/>
  </svg>`;
}

/** Mode glyphs: Разгон = восход, Ровно = горизонт, Бережно = сумерки (never colour-only). */
function modeGlyph(mode, size = 20, color = 'currentColor') {
  const s = size;
  const common = `fill="none" stroke="${color}" stroke-width="1.5" stroke-linecap="round"`;
  // Разгон: солнце над горизонтом с лучами; Ровно: солнце наполовину над горизонтом; Бережно: солнце за горизонтом
  if (mode === 'razgon') return `<svg width="${s}" height="${s}" viewBox="0 0 24 24" ${common}><path d="M2.5 19h19"/><circle cx="12" cy="12.5" r="3.6"/><path d="M12 4.2v2M5.6 6.6l1.4 1.4M18.4 6.6 17 8M3.6 12.5h2M18.4 12.5h2"/></svg>`;
  if (mode === 'rovno') return `<svg width="${s}" height="${s}" viewBox="0 0 24 24" ${common}><path d="M2.5 15.5h19"/><path d="M6.5 15.5a5.5 5.5 0 0 1 11 0"/><path d="M7 19h10" opacity=".55"/></svg>`;
  return `<svg width="${s}" height="${s}" viewBox="0 0 24 24" ${common}><path d="M2.5 11h19"/><path d="M6.5 11a5.5 5.5 0 0 0 11 0" stroke-dasharray="2 2.2"/><path d="M12 4.5v2.5" opacity=".55"/></svg>`;
}

const I = (d, s = 24, sw = 1.5) => `<svg class="ic" width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round">${d}</svg>`;
const icons = {
  today: '<path d="M3 18h18"/><path d="M7 18a5 5 0 0 1 10 0"/><path d="M12 7v2.5M6.3 9.8l1.7 1.7M17.7 9.8 16 11.5"/>',
  mentor: '<path d="M5 5.5h14a1.5 1.5 0 0 1 1.5 1.5v8a1.5 1.5 0 0 1-1.5 1.5h-8L6.5 20v-3.5H5A1.5 1.5 0 0 1 3.5 15V7A1.5 1.5 0 0 1 5 5.5z"/><path d="M8 10.5h8M8 13h5"/>',
  week: '<path d="M3 20.5h18M4 8h16M12 3.5 4 8M12 3.5 20 8"/><path d="M6.5 10.5v7.5M10 10.5v7.5M14 10.5v7.5M17.5 10.5v7.5"/>',
  me: '<circle cx="12" cy="8.5" r="3.6"/><path d="M5 20c.9-3.8 3.7-5.8 7-5.8s6.1 2 7 5.8"/>',
  sos: '<circle cx="12" cy="12" r="8.5"/><circle cx="12" cy="12" r="3.6"/><path d="m6 6 3.4 3.4M18 6l-3.4 3.4M6 18l3.4-3.4M18 18l-3.4-3.4"/>',
  chev: '<path d="m9.5 6 6 6-6 6"/>', back: '<path d="M19 12H5M11 6l-6 6 6 6"/>',
  close: '<path d="M6 6l12 12M18 6 6 18"/>',
  check: '<path d="m5 12.5 4.2 4.2L19 7"/>',
  swap: '<path d="M4 9h14l-3.5-3.5M20 15H6l3.5 3.5"/>',
  body: '<circle cx="12" cy="4.8" r="1.9"/><path d="M12 7.5v6.5M12 9.5l-4.5 2.5M12 9.5l4.5 2.5M12 14l-3 6.5M12 14l3 6.5"/>',
  work: '<rect x="3.5" y="7.5" width="17" height="12" rx="1.5"/><path d="M9 7.5V5.5h6v2M3.5 12.5h17"/>',
  moon: '<path d="M19.5 14.5A8 8 0 0 1 9.5 4.5a8 8 0 1 0 10 10z"/>',
  plate: '<circle cx="12" cy="12" r="8.5"/><circle cx="12" cy="12" r="5"/><path d="M12 7v10M12 12h5"/>',
  cup: '<path d="M5 9.5h11v5a5 5 0 0 1-5 5h-1a5 5 0 0 1-5-5z"/><path d="M16 11h1.5a2.5 2.5 0 0 1 0 5H16"/><path d="M9 3.5c-.8 1 .8 2 0 3M12.5 3.5c-.8 1 .8 2 0 3"/>',
  breath: '<path d="M3 9h11a3 3 0 1 0-3-3"/><path d="M3 13h15a3 3 0 1 1-3 3"/><path d="M3 17h7"/>',
  clock: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>',
  pin: '<path d="M12 21s-6.5-6-6.5-11a6.5 6.5 0 0 1 13 0c0 5-6.5 11-6.5 11z"/><circle cx="12" cy="10" r="2.3"/>',
  mic: '<rect x="9" y="3.5" width="6" height="11" rx="3"/><path d="M5.5 11.5a6.5 6.5 0 0 0 13 0M12 18v2.5"/>',
  clip: '<path d="M20 11.5 12 19.5a5 5 0 0 1-7-7l8-8a3.3 3.3 0 0 1 4.7 4.7l-8 8a1.7 1.7 0 0 1-2.4-2.4l7.3-7.3"/>',
  eyeoff: '<path d="M3 3l18 18"/><path d="M10.6 5.6A9.8 9.8 0 0 1 12 5.5c5 0 8.5 4.5 9.5 6.5a14 14 0 0 1-3 3.9M6.6 6.6A14 14 0 0 0 2.5 12c1 2 4.5 6.5 9.5 6.5a9.6 9.6 0 0 0 4.4-1"/><path d="M9.9 9.9a3 3 0 0 0 4.2 4.2"/>',
  pause: '<path d="M9 6v12M15 6v12"/>', stop: '<rect x="6.5" y="6.5" width="11" height="11" rx="1.5"/>',
  sound: '<path d="M4 9.5h3.5L12 5.5v13l-4.5-4H4z"/><path d="M15.5 9a4 4 0 0 1 0 6M18 6.5a7.5 7.5 0 0 1 0 11"/>',
  phone: '<path d="M6.5 3.5h3l1.5 4.5-2 1.3a11 11 0 0 0 5.7 5.7l1.3-2 4.5 1.5v3a2 2 0 0 1-2 2A16.5 16.5 0 0 1 4.5 5.5a2 2 0 0 1 2-2z"/>',
  sms: '<path d="M4 5.5h16v10.5H9.5L5 20v-4H4z"/>',
  band: '<rect x="7" y="7" width="10" height="10" rx="3"/><path d="M9 7V3.5h6V7M9 17v3.5h6V17"/>',
  calendar: '<rect x="3.5" y="5" width="17" height="15.5" rx="1.5"/><path d="M3.5 9.5h17M8 3v4M16 3v4"/>',
  alarm: '<circle cx="12" cy="13" r="7"/><path d="M12 9.5V13l2.5 1.5M4 5.5 6.5 3M20 5.5 17.5 3"/>',
  steps: '<path d="M8.5 3.5c1.6 0 2.5 1.6 2.5 4s-1 4-2.5 4S6 9.9 6 7.5s.9-4 2.5-4zM7 13.5h3l-.3 3a1.5 1.5 0 0 1-3 0zM15.5 7.5c1.6 0 2.5 1.6 2.5 4s-1 4-2.5 4-2.5-1.6-2.5-4 .9-4 2.5-4zM14 17.5h3l-.3 2a1.5 1.5 0 0 1-3 0z"/>',
  pencil: '<path d="M4 20h4L19 9l-4-4L4 16z"/><path d="m13.5 6.5 4 4"/>',
  globe: '<circle cx="12" cy="12" r="8.5"/><path d="M3.5 12h17M12 3.5c2.5 2.6 3.5 5.4 3.5 8.5s-1 5.9-3.5 8.5c-2.5-2.6-3.5-5.4-3.5-8.5s1-5.9 3.5-8.5z"/>',
  tag: '<path d="M3.5 12.5V4.5h8l9 9-8 8z"/><circle cx="8" cy="9" r="1.3"/>',
  star: '<path d="m12 4 2.4 5 5.4.7-4 3.8 1 5.4L12 16.3 7.2 18.9l1-5.4-4-3.8 5.4-.7z"/>',
  info: '<circle cx="12" cy="12" r="8.5"/><path d="M12 11v5.5M12 7.8v.2"/>',
  wifi: '<path d="M2.5 9a14 14 0 0 1 19 0M5.5 12.3a9.5 9.5 0 0 1 13 0M8.6 15.5a5 5 0 0 1 6.8 0"/><circle cx="12" cy="18.6" r=".8" fill="currentColor"/>',
  bt: '<path d="m7 7.5 10 9-5 4.5V3l5 4.5-10 9"/>',
  dnd: '<circle cx="12" cy="12" r="8.5"/><path d="M8 12h8"/>',
  flash: '<path d="M9 3.5h6l-1.5 6H17l-7 11 1.5-8H8z"/>',
  search: '<circle cx="11" cy="11" r="6"/><path d="m20 20-4.5-4.5"/>',
  camera: '<path d="M4 8h3l1.5-2.5h7L17 8h3v11H4z"/><circle cx="12" cy="13.3" r="3.3"/>',
  chat: '<path d="M4 5.5h16v11H9l-5 4z"/>',
  grid: '<path d="M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z"/>',
  feather: '<path d="M20 4C10 4 5 10 5 19"/><path d="M5 19c6 0 11-3 13-9M9 13h6"/>',
  shield: '<path d="M12 3.5 19 6v5.5c0 4.5-3 7.7-7 9-4-1.3-7-4.5-7-9V6z"/>',
};
const icon = (name, s = 24, sw = 1.5) => I(icons[name], s, sw);

module.exports = { pulsePath, monogram, laurel, column, hMark, modeGlyph, icon, icons };
