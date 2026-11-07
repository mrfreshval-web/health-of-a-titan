// Builds the A44 «Карточки состояния» mockups in the «Титаниум» style (A37) as HTML and renders them to PNG with Playwright.
// Usage (from repo root):
//   python3 docs/design/state-cards/src/getfonts.py   # once, downloads OFL fonts (Geist, Geist Mono)
//   node docs/design/state-cards/src/build.js [outDir]   # ONLY=sc1-morning-charge,_state-cards for a subset
// art.js, titan.js, styles.css, getfonts.py are copied from docs/design/journey/src without changes.
const fs = require('fs');
const path = require('path');
const { icon, picto } = require('./art');
const { DEFS, titanScene, titanZones } = require('./titan');

const SRC = __dirname;
const OUT = path.resolve(process.argv[2] || path.resolve(SRC, '..'));
const HTML = path.join(SRC, 'html');
fs.mkdirSync(HTML, { recursive: true });
fs.mkdirSync(OUT, { recursive: true });

const AQUA = '#7DE3D1', DAWN = '#FFC77A', IRIS = '#A8A3FF', SUN = '#FFB067';
const ICE = ['#FFFFFF', '#DFFBF6', '#9FEADC'];
const IRIS_L = ['#FFFFFF', '#E4E1FF', '#B9B4FF'];
const STEEL = ['#E6EAF2', '#B4BCCB', '#8D97A8'];

const page = (body, { theme = 'dark', extraCss = '', w = 412, h = 915, wrap = true } = {}) => `<!doctype html>
<html lang="ru"><head><meta charset="utf-8"><title>HoT · Карточки состояния</title>
<link rel="stylesheet" href="../fonts/local.css"><link rel="stylesheet" href="../styles.css">
<style>html,body{width:${w}px;height:${h}px;overflow:hidden}
.card{position:absolute;border-radius:28px}
.opt{display:flex;flex-direction:column;align-items:center;gap:6px}
.lbl{font-family:var(--mono);font-size:10px;line-height:12px;letter-spacing:.08em;text-transform:uppercase;color:var(--fg3)}
.sel{border-radius:20px;background:rgba(255,255,255,.07);box-shadow:inset 0 0 0 1.5px var(--c),0 0 22px -6px var(--c)}
.tg{width:44px;height:26px;border-radius:13px;background:rgba(255,255,255,.12);position:relative;flex:none}
.tg::after{content:"";position:absolute;left:3px;top:3px;width:20px;height:20px;border-radius:10px;background:#80858D}
.tg.on{background:rgba(125,227,209,.35)}.tg.on::after{left:21px;background:#DFFBF6}
${extraCss}</style></head>
<body>${DEFS}${wrap ? `<div class="screen ${theme}">${body}</div>` : body}</body></html>`;

const statusBar = (time) => `<div class="statusbar"><span class="num">${time}</span><span class="icons">
  <svg width="16" height="12" viewBox="0 0 16 12" fill="currentColor"><path d="M15 1v10H1z"/></svg>
  ${icon('wifi', 17, 2)}
  <svg width="24" height="12" viewBox="0 0 24 12"><rect x=".6" y=".6" width="20.5" height="10.8" rx="3.4" fill="none" stroke="currentColor" stroke-width="1.2" opacity=".5"/><rect x="2.4" y="2.4" width="13.5" height="7.2" rx="1.8" fill="currentColor"/><rect x="22" y="4" width="1.6" height="4" rx=".8" fill="currentColor" opacity=".5"/></svg>
  </span></div>`;
const sceneSize = (r) => Math.round(r * 2048 / 940);
const dots = (n, of, col = 'var(--fg)') => `<div class="row" style="gap:5px">${Array.from({ length: of }, (_, i) => `<div style="width:${i === n - 1 ? 18 : 6}px;height:6px;border-radius:3px;background:${i < n ? col : 'rgba(255,255,255,.16)'}"></div>`).join('')}</div>`;

// Mentor avatars (A38 §8.3), copied from docs/design/journey/src/build.js; 'calm' = neutral avatar of the gentle mode (A38 §6).
function avatar(kind, size = 56, col = DAWN) {
  const s = `fill="none" stroke="${col}" stroke-linecap="round" stroke-linejoin="round"`;
  const column = (x, w = 7, top = 17, bot = 40) => `<path d="M${x - w / 2} ${top}v${bot - top}M${x + w / 2} ${top}v${bot - top}M${x - w / 2 - 2} ${top}h${w + 4}M${x - w / 2 - 2} ${bot}h${w + 4}" ${s} stroke-width="1.6"/>`;
  let g = '';
  if (kind === 'sensei') {
    g = `<path d="M44 14 A20 20 0 1 0 47.5 22" ${s} stroke-width="3.2"/><path d="M47.5 22 A20 20 0 0 1 48.6 27" ${s} stroke-width="1.4"/>
      ${column(28, 7, 16, 41)}<path d="M9 33h14l2-5 2 5h20" ${s} stroke-width="1.4" opacity=".9"/>`;
  } else if (kind === 'bro') {
    g = `<circle cx="28" cy="28" r="20" ${s} stroke-width="2"/>${column(20, 6, 17, 40)}${column(36, 6, 17, 40)}
      <path d="M2 31h12l2.5-9 3 14 2.5-5h6l2.5-9 3 14 2.5-5h17" ${s} stroke-width="1.6"/>`;
  } else if (kind === 'butler') {
    g = `<circle cx="28" cy="28" r="20" ${s} stroke-width="1.6"/><circle cx="28" cy="28" r="16.6" ${s} stroke-width=".8" opacity=".7"/>
      <path d="M24.5 21v19M31.5 21v19M22 40h12" ${s} stroke-width="1.6"/>
      <path d="M21 18.6c0-2 2.8-2 2.8 0M34.2 18.6c0-2-2.8-2-2.8 0M21.4 18.4h13.2" ${s} stroke-width="1.4"/>
      <path d="M28 23.6l-4 -2.4v4.8zM28 23.6l4 -2.4v4.8z" fill="${col}" stroke="${col}" stroke-width=".8" stroke-linejoin="round"/>`;
  } else {
    g = `<circle cx="28" cy="28" r="20" ${s} stroke-width="1.4"/>${column(28, 7, 18, 40)}`;
  }
  return `<svg width="${size}" height="${size}" viewBox="0 0 56 56" style="overflow:visible;${kind === 'calm' ? '' : `filter:drop-shadow(0 0 6px ${col}55)`}">${g}</svg>`;
}

// ---------------------------------------------------------------- illustrations: answers are states of the Titan
const CROP = { x: 400, y: 420, w: 1250, h: 1440 };
const cropStr = `${CROP.x} ${CROP.y} ${CROP.w} ${CROP.h}`;
const fit = (svg, size, w, h) => svg.replace(`width="${size}" height="${size}"`, `width="${w}" height="${h}"`);
const figW = (h) => Math.round(h * CROP.w / CROP.h);

// Zones of light on the Titan; unlike titan.js titanZones the glow sits outside the mask, so no box edges.
let zid = 0;
function zonesFig(size, zones, { base = 'rgba(255,255,255,.2)', knock = 'rgba(8,9,12,.9)', crop = cropStr } = {}) {
  const id = `zf${++zid}`;
  return `<svg width="${size}" height="${size}" viewBox="${crop}" style="overflow:visible">
    <defs><filter id="${id}b" x="-10%" y="-10%" width="120%" height="120%"><feGaussianBlur stdDeviation="30"/></filter>
    ${zones.map((z, i) => `<mask id="${id}m${i}" maskUnits="userSpaceOnUse" x="0" y="0" width="2048" height="2048"><g filter="url(#${id}b)">${(z.rects || [z]).map((q) => `<rect x="${q.x0}" y="${q.y0}" width="${q.x1 - q.x0}" height="${q.y1 - q.y0}" fill="#fff"/>`).join('')}</g></mask>`).join('')}</defs>
    <use href="#tt-sil" clip-path="url(#tt-nohands)" fill="${knock}"/>
    <use href="#hot-titan-art" fill="${base}"/>
    ${zones.map((z, i) => `<g style="filter:drop-shadow(0 0 3px ${z.glow}) drop-shadow(0 0 12px ${z.glow})"><g mask="url(#${id}m${i})" opacity="${z.lit}"><use href="#hot-titan-art" fill="${z.color}"/></g></g>`).join('')}
  </svg>`;
}
const SORE = (keys, lit = 1) => keys.map((k) => ({ rects: BZ[k].rects, lit, color: SUN, glow: 'rgba(255,176,103,.85)' }));
const LEVEL_TOP = [1900, 1600, 1250, 860, 640, 380];

// Заряд: the Titan fills with light from the feet up, 5 levels (A25: «сосуд, уровень»).
function chargeTitan(h, level, { col = null, base = 'rgba(255,255,255,.16)', glow } = {}) {
  const top = LEVEL_TOP[level];
  const zones = level ? [{ x0: 300, x1: 1750, y0: top, y1: 1900, lit: 1, glow }] : [];
  const svg = titanZones(h, zones, { crop: cropStr, base, lit: col || ['#FFFFFF', '#FFF1D6', '#FFD08A'] });
  return fit(svg, h, figW(h), h);
}
// A whole Titan in one light (weather, body feelings).
function lightTitan(h, light, glow, o = {}) {
  return fit(titanScene(h, { columns: false, light, glow, crop: cropStr, ...o }), h, figW(h), h);
}

// Weather glyphs (24 grid, monoline), «Погода» = mood (A25 EMB: шторм — не «плохо», а «тяжёлая погода»).
const CLOUD = 'M7.4 18.2h9.8a3.6 3.6 0 0 0 .5-7.15 5.1 5.1 0 0 0-9.85-1.2A4.2 4.2 0 0 0 7.4 18.2Z';
const WX = {
  storm: `<g transform="translate(0 -3)"><path d="${CLOUD}"/></g><path d="M12.6 15.6 10.6 19h2.8l-1.8 3.4"/>`,
  rain: `<g transform="translate(0 -3)"><path d="${CLOUD}"/></g><path d="M8.6 18.4l-.8 2.2M12.4 18.4l-.8 2.2M16.2 18.4l-.8 2.2"/>`,
  cloud: `<path d="${CLOUD}"/>`,
  partly: `<circle cx="15.6" cy="7.6" r="3.2"/><path d="M15.6 2.2v1.1M20.4 7.6h1.1M19.1 4.1l.8-.8M11 4.1l-.7-.7"/><path d="M5.4 19.4h8.4a3 3 0 0 0 .4-6 4.3 4.3 0 0 0-8.3-1A3.5 3.5 0 0 0 5.4 19.4Z"/>`,
  sun: `<circle cx="12" cy="12" r="4.2"/><path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6 7 7M17 17l1.4 1.4M5.6 18.4 7 17M17 7l1.4-1.4"/>`,
};
const wx = (k, size, col, sw = 1.6) => `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="${col}" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round" style="overflow:visible">${WX[k]}</svg>`;
const WEATHER = [
  ['storm', 'гроза', '#9AA3B8', STEEL, 'rgba(150,160,190,.35)'],
  ['rain', 'дождь', '#A9B6CC', STEEL, 'rgba(160,175,200,.4)'],
  ['cloud', 'облачно', '#C9CFDA', ['#FFFFFF', '#E9ECF2', '#C3C9D4'], 'rgba(200,210,225,.4)'],
  ['partly', 'просвет', '#FFD9A0', ['#FFFFFF', '#FFF1D6', '#FFD9A0'], 'rgba(255,210,150,.45)'],
  ['sun', 'ясно', DAWN, ['#FFFFFF', '#FFF1D6', '#FFD08A'], 'rgba(255,199,122,.6)'],
];
const CHARGE = ['на нуле', 'мало', 'средне', 'бодро', 'полный'];

// Evening «Как тело?» — three Titans-feelings (A39 §7.5) + «Простуда» pictogram.
const SICK = `<path d="M4 5v14.5M20 5v14.5M2.6 4.6h2.8M18.6 4.6h2.8M2.6 19.6h2.8M18.6 19.6h2.8"/><circle cx="14.4" cy="7" r="1.6"/><path d="M13.6 8.6 12 14.2M12 14.2l-1.6 5.3M12 14.2l1.4 5.3M12.9 10.6l4.8-1.6 1.8.8M12.6 10.8 9 12.6"/><path d="M7 4.2c.6.7.6 1.5 0 2.2s-.6 1.5 0 2.2" opacity=".8"/>`;
const sickPicto = (size, col, sw = 1.6) => `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="${col}" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round">${SICK}</svg>`;
// Body zones on the silhouette (front) — mark space; back zones are chips (the silhouette is a front view).
const BZ = {
  shoulders: { label: 'плечи', pts: [[870, 700], [1180, 700]], rects: [{ x0: 800, x1: 960, y0: 630, y1: 770 }, { x0: 1090, x1: 1250, y0: 630, y1: 770 }] },
  arms: { label: 'руки', pts: [[690, 735], [1360, 735]], rects: [{ x0: 520, x1: 830, y0: 640, y1: 830 }, { x0: 1220, x1: 1530, y0: 640, y1: 830 }] },
  chest: { label: 'грудь', pts: [[1024, 800]], rects: [{ x0: 900, x1: 1150, y0: 730, y1: 880 }] },
  core: { label: 'корпус', pts: [[1024, 1010]], rects: [{ x0: 900, x1: 1150, y0: 900, y1: 1130 }] },
  hips: { label: 'бёдра', pts: [[950, 1360], [1110, 1360]], rects: [{ x0: 860, x1: 1200, y0: 1230, y1: 1470 }] },
  knees: { label: 'колени', pts: [[945, 1540], [1085, 1540]], rects: [{ x0: 880, x1: 1160, y0: 1480, y1: 1600 }] },
  calves: { label: 'голени', pts: [[945, 1680], [1080, 1680]], rects: [{ x0: 860, x1: 1170, y0: 1600, y1: 1820 }] },
};

// Glass of the alcohol card: neutral, the same for every answer (no moral colour).
const glass = (size, col, n) => `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="${col}" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M6.5 4h11l-1.4 15.2a1.6 1.6 0 0 1-1.6 1.4H9.5a1.6 1.6 0 0 1-1.6-1.4Z"/>${n ? `<path d="M${7.6 + (4 - n) * .12} ${19.6 - n * 3.2}H${16.4 - (4 - n) * .12}L15.6 19.4H8.4Z" fill="${col}" fill-opacity=".3" stroke="none"/><path d="M${7.6 + (4 - n) * .12} ${19.6 - n * 3.2}H${16.4 - (4 - n) * .12}"/>` : '<path d="M9.5 13h5" opacity=".6"/>'}</svg>`;

// Background: dawn sky (morning) — same recipe as s1-morning-*.
const dawnSky = `<div class="abs" style="inset:0;background:linear-gradient(180deg,#05060B 0%,#0B1022 34%,#1A1E3A 50%,#3A2D3E 60%,#6E4A3E 68%,#060708 69%)"></div>
  <div class="abs" style="left:-40px;right:-40px;top:300px;height:200px;background:radial-gradient(50% 60% at 50% 100%,rgba(255,190,120,.45),transparent 70%)"></div>
  ${[[30, 70], [92, 120], [350, 66], [300, 140], [180, 96], [60, 210], [380, 230], [240, 52]].map(([x, y]) => `<div class="abs" style="left:${x}px;top:${y}px;width:2px;height:2px;border-radius:1px;background:#fff;opacity:.45"></div>`).join('')}`;

// The dawn scene with a Titan that holds the chosen charge level.
function dawnScene(cx, cy, R, level, { ring = .62 } = {}) {
  const S = sceneSize(R);
  const base = titanScene(S, { titan: false, ring, ringCol: DAWN, ringW: 10, spread: 1, colOpacity: .9, glow: 'rgba(255,199,122,.55)' });
  const fig = titanZones(S, [{ x0: 300, x1: 1750, y0: LEVEL_TOP[level], y1: 1900, lit: 1 }], { base: 'rgba(255,255,255,.22)' });
  return `<div class="abs" style="left:${cx - S / 2}px;top:${cy - S / 2}px;width:${S}px;height:${S}px">${base}<div class="abs" style="left:0;top:0">${fig}</div></div>`;
}

// ------------------------------------------------------------------ sc1 · morning, card 1/2 «Заряд» inside «Рассвет»
function morningCharge() {
  const lvl = 3;
  const opts = CHARGE.map((l, i) => `<div class="opt ${i + 1 === lvl ? 'sel' : ''}" style="--c:${DAWN};width:62px;padding:10px 0 8px">
      ${chargeTitan(74, i + 1, { base: 'rgba(255,255,255,.18)' })}<span class="lbl" style="${i + 1 === lvl ? 'color:var(--fg)' : ''}">${l}</span></div>`).join('');
  const body = `${dawnSky}
  ${statusBar('6:58')}
  <div class="abs col" style="left:0;right:0;top:54px;align-items:center;gap:12px">
    <span class="mono fg3">Среда · 11 ноября</span>
    <div class="row" style="gap:8px">
      <span class="tok" style="color:${IRIS}">${picto('sleep', 14, 1.6)}<span class="num">6:05 ↓</span></span>
      <span class="tok">${picto('heart', 14, 1.6)}<span class="num">52 =</span></span>
      <span class="tok">${picto('seal', 13, 1.8)}WHOOP 06:41</span>
    </div>
  </div>
  ${dawnScene(206, 340, 132, lvl, { ring: lvl / 5 })}
  <div class="abs" style="left:24px;right:24px;top:486px;height:1px;background:linear-gradient(90deg,transparent,rgba(255,214,160,.5),transparent)"></div>
  <div class="card" style="left:24px;right:24px;top:540px;height:226px;background:rgba(255,255,255,.035);transform:scale(.94);transform-origin:50% 100%;box-shadow:inset 0 0 0 1px var(--hair)"></div>
  <div class="card glass" style="left:16px;right:16px;top:520px;height:232px;padding:18px 14px 14px">
    <div class="row" style="justify-content:space-between;padding:0 6px">
      <div class="row" style="gap:10px;color:${DAWN}">${picto('charge', 26, 1.6)}<span class="t21" style="color:var(--fg)">Заряд</span></div>
      <div class="row" style="gap:12px">${dots(1, 2, DAWN)}<span class="fg3">${icon('dots', 18, 1.6)}</span></div>
    </div>
    <div class="row" style="justify-content:space-between;margin-top:22px">${opts}</div>
    <div class="row mono fg3" style="justify-content:center;gap:8px;margin-top:22px">${icon('haptic', 14, 1.6)}<span>тап — ответ · ↑ смахни — пропустить</span></div>
  </div>
  <div class="abs row" style="left:24px;right:24px;top:790px;justify-content:space-between">
    <span class="chip">${icon('bolt', 14, 1.8)} Быстро</span>
    <span class="row fg2" style="gap:6px;font-size:15px">Сразу к дню ${icon('arrow', 15, 1.8)}</span>
  </div>
  <div class="gesture"></div>`;
  return page(body);
}

// ------------------------------------------------------------------ sc2 · morning, card 2/2 «Погода» (mood)
function morningWeather() {
  const pick = 3;
  const opts = WEATHER.map(([k, l, c, light, g], i) => `<div class="opt ${i === pick ? 'sel' : ''}" style="--c:${c};width:62px;padding:8px 0 8px">
      <div style="height:22px;margin-bottom:-2px">${wx(k, 24, c)}</div>
      ${lightTitan(58, light, i >= 3 ? g : false)}<span class="lbl" style="${i === pick ? 'color:var(--fg)' : ''}">${l}</span></div>`).join('');
  const body = `${dawnSky}
  ${statusBar('6:58')}
  <div class="abs col" style="left:0;right:0;top:54px;align-items:center;gap:12px">
    <span class="mono fg3">Среда · 11 ноября</span>
    <div class="row" style="gap:8px">
      <span class="tok" style="color:${IRIS}">${picto('sleep', 14, 1.6)}<span class="num">6:05 ↓</span></span>
      <span class="tok">${picto('heart', 14, 1.6)}<span class="num">52 =</span></span>
      <span class="tok" style="color:${DAWN}">${picto('charge', 14, 1.6)}<span class="num">▮▮▮▮▯</span></span>
    </div>
  </div>
  ${dawnScene(206, 340, 132, 4, { ring: .8 })}
  <div class="abs" style="left:250px;top:150px;opacity:.85;filter:drop-shadow(0 0 10px rgba(255,217,160,.5))">${wx('partly', 84, '#FFE2B6', 1.1)}</div>
  <div class="abs" style="left:24px;right:24px;top:486px;height:1px;background:linear-gradient(90deg,transparent,rgba(255,214,160,.5),transparent)"></div>
  <div class="card glass" style="left:16px;right:16px;top:520px;height:232px;padding:18px 14px 14px">
    <div class="row" style="justify-content:space-between;padding:0 6px">
      <div class="row" style="gap:10px">${wx('partly', 26, '#FFD9A0')}<span class="t21">Погода внутри</span></div>
      <div class="row" style="gap:12px">${dots(2, 2, DAWN)}<span class="fg3">${icon('dots', 18, 1.6)}</span></div>
    </div>
    <div class="row" style="justify-content:space-between;margin-top:18px">${opts}</div>
    <div class="row" style="justify-content:center;gap:8px;margin-top:16px">
      <span class="chip" style="height:28px;font-size:12px;color:var(--fg2)">+ словами</span>
      <span class="mono fg3" style="font-size:10px">по желанию · чипы, без текста</span>
    </div>
  </div>
  <div class="abs row" style="left:24px;right:24px;top:790px;justify-content:space-between">
    <span class="chip on">${icon('bolt', 14, 1.8)} Быстро</span>
    <span class="row fg2" style="gap:6px;font-size:15px">Сразу к дню ${icon('arrow', 15, 1.8)}</span>
  </div>
  <div class="gesture"></div>`;
  return page(body);
}

// ------------------------------------------------------------------ sc3 · after the deck: mode + haiku + one mentor line (response)
function dawnResponse() {
  const body = `${dawnSky}
  ${statusBar('6:59')}
  <div class="abs col" style="left:0;right:0;top:66px;align-items:center;text-align:center">
    <span class="mono fg3">Среда · 11 ноября</span>
    <div class="row" style="gap:14px;margin-top:14px;color:${AQUA}">${picto('rovno', 44, 1.4)}<span class="d55" style="color:${AQUA}">Ровно</span></div>
    <div class="t21 fg2" style="margin-top:18px;font-weight:300;line-height:30px">Ночь чуть короче.<br>Тело дышит спокойно.<br><span style="color:var(--fg);font-weight:400">Ровный шаг — и в путь.</span></div>
  </div>
  ${dawnScene(206, 470, 118, 4, { ring: .8 })}
  <div class="abs" style="left:0;right:0;top:600px;height:1px;background:linear-gradient(90deg,transparent,rgba(255,214,160,.55),transparent)"></div>
  <div class="abs glass row" style="left:16px;right:16px;top:626px;border-radius:24px;padding:14px 16px;gap:14px;align-items:center">
    <div class="center" style="width:48px;height:48px;border-radius:24px;background:rgba(0,0,0,.35);box-shadow:inset 0 0 0 1px ${SUN}44;flex:none">${avatar('sensei', 38, SUN)}</div>
    <div class="col" style="gap:4px">
      <span class="b16" style="font-weight:300">«Заряд выше, чем обычно по средам. Держи ровный шаг.»</span>
      <span class="mono" style="color:${SUN};font-size:10px">Сенсей Винс</span>
    </div>
  </div>
  <div class="abs row" style="left:24px;right:24px;top:744px;justify-content:center;gap:10px">
    <span class="tok" style="color:${IRIS}">${picto('sleep', 13, 1.6)}<span class="num">6:05</span></span>
    <span class="tok" style="color:${DAWN}">${picto('charge', 13, 1.6)}<span class="num">58</span></span>
    <span class="tok">${wx('partly', 13, '#FFD9A0')}</span>
    <span class="tok" style="color:${SUN}">${picto('ceiling', 13, 1.6)}<span class="num">12</span></span>
  </div>
  <div class="abs" style="left:24px;right:24px;top:790px"><div class="btn light-pill" style="width:100%">Начать день ${icon('arrow', 18, 1.8)}</div></div>
  <div class="gesture"></div>`;
  return page(body);
}

// ------------------------------------------------------------------ sc4 · evening «Закрыть день»: first «Как тело?»
const eveSky = `<div class="abs" style="inset:0;background:radial-gradient(90% 40% at 50% 0%,rgba(110,100,220,.22),transparent 70%),linear-gradient(180deg,#0B0A18 0%,#08080F 50%,#060708 100%)"></div>`;
function eveningDeck() {
  const opts = [
    ['Легко', lightTitan(118, ICE, 'rgba(125,227,209,.55)', { tY: -30 }), AQUA, true],
    ['Забито', '', SUN, false],
    ['Простуда', lightTitan(118, IRIS_L, false, { tOpacity: .75 }), IRIS, false],
  ];
  // «Забито» as amber spots on legs and shoulders
  opts[1][1] = fit(zonesFig(118, SORE(['hips', 'shoulders']), { base: 'rgba(255,255,255,.3)' }), 118, figW(118), 118);
  const opt = ([l, svg, c], i) => `<div class="opt ${i === 1 ? 'sel' : ''}" style="--c:${c};width:108px;padding:14px 0 12px;border-radius:22px;${i === 1 ? '' : 'box-shadow:inset 0 0 0 1px var(--hair)'}">
      <div style="position:relative">${svg}${i === 2 ? `<div class="abs" style="right:-6px;top:-10px;color:${IRIS}">${sickPicto(22, IRIS, 1.5)}</div>` : ''}</div>
      <span style="font-size:14px;font-weight:500;color:${i === 1 ? 'var(--fg)' : 'var(--fg2)'}">${l}</span></div>`;
  const body = `${eveSky}
  ${statusBar('21:32')}
  <div class="abs row" style="left:24px;right:24px;top:58px;justify-content:space-between">
    <span class="mono fg3">Вт · вечер · ≈ 20 с</span>
    <span class="row fg3" style="gap:6px">${icon('moon', 14, 1.6)}<span class="mono">отбой 22:40</span></span>
  </div>
  <div class="abs" style="left:24px;top:88px"><div class="d34">Закрыть день</div></div>
  <div class="abs col" style="left:24px;right:24px;top:150px;gap:10px">
    <span class="mono fg3">уже знаю — спрашивать не буду</span>
    <div class="row" style="gap:8px;flex-wrap:wrap">
      <span class="tok" style="color:${AQUA}">${picto('body', 13, 1.6)}Zone 2 · 41′ ${picto('seal', 12, 1.8)}</span>
      <span class="tok">${picto('steps', 13, 1.6)}<span class="num">9,4k</span></span>
      <span class="tok">${picto('goal', 13, 1.6)}шаг ${icon('check', 12, 2)}</span>
      <span class="tok">${picto('work', 13, 1.6)}<span class="num">5 встреч</span></span>
    </div>
  </div>
  <div class="card" style="left:28px;right:28px;top:280px;height:380px;background:rgba(255,255,255,.03);transform:scale(.93) translateY(30px);transform-origin:50% 100%;box-shadow:inset 0 0 0 1px var(--hair)"></div>
  <div class="card glass" style="left:16px;right:16px;top:248px;height:388px;padding:20px 14px 16px">
    <div class="row" style="justify-content:space-between;padding:0 6px">
      <div class="row" style="gap:10px;color:${IRIS}">${picto('body', 26, 1.6)}<span class="t21" style="color:var(--fg)">Как тело?</span></div>
      ${dots(1, 3, IRIS)}
    </div>
    <div class="row" style="justify-content:space-between;margin-top:30px">${opts.map(opt).join('')}</div>
    <div class="row" style="margin-top:22px;gap:10px;padding:0 6px;align-items:center">
      <span class="center" style="width:28px;height:28px;border-radius:14px;box-shadow:inset 0 0 0 1px ${SUN}88;color:${SUN}">${icon('arrow', 14, 1.8)}</span>
      <span class="fg2" style="font-size:14px;line-height:19px">«Забито» откроет силуэт: где — по желанию</span>
    </div>
    <div class="hr" style="margin:22px 6px 0"></div>
    <div class="row" style="justify-content:space-between;margin-top:14px;padding:0 6px">
      <span class="row mono fg3" style="gap:8px">${icon('haptic', 14, 1.6)}тап — и дальше</span>
      <span class="row fg2" style="gap:6px;font-size:14px">↑ Пропустить</span>
    </div>
  </div>
  <div class="abs glass row" style="left:34px;right:34px;top:660px;height:56px;border-radius:20px;padding:0 18px;gap:12px;opacity:.75">
    ${glass(22, 'var(--fg2)', 1)}<span class="b16 fg2">Алкоголь сегодня</span><span class="mono fg3" style="margin-left:auto">2 / 3</span>
  </div>
  <div class="abs row" style="left:34px;right:34px;top:728px;height:44px;border-radius:16px;padding:0 18px;gap:12px;opacity:.45;box-shadow:inset 0 0 0 1px var(--hair)">
    ${wx('cloud', 20, 'var(--fg2)')}<span class="fg2" style="font-size:14px">Погода вечера</span><span class="mono fg3" style="margin-left:auto">3 / 3</span>
  </div>
  <div class="abs row mono fg3" style="left:0;right:0;top:800px;justify-content:center;gap:8px">${picto('seal', 13, 1.8)}<span>последний тап закроет день</span></div>
  <div class="gesture"></div>`;
  return page(body);
}

// ------------------------------------------------------------------ sc5 · «Где забито?» zones on the silhouette
function bodyZones() {
  const H = 440, W = figW(H), left = (412 - W) / 2, top = 178;
  const sel = ['hips', 'shoulders'];
  const fig = fit(zonesFig(H, SORE(sel), { base: 'rgba(255,255,255,.26)' }), H, W, H);
  const k = H / CROP.h;
  const pt = ([x, y]) => [left + (x - CROP.x) * k, top + (y - CROP.y) * k];
  const spots = Object.entries(BZ).flatMap(([key, z]) => z.pts.map((p) => {
    const [x, y] = pt(p); const on = sel.includes(key);
    return `<div class="abs" style="left:${x - 13}px;top:${y - 13}px;width:26px;height:26px;border-radius:13px;${on ? `background:${SUN};box-shadow:0 0 0 5px rgba(255,176,103,.22),0 0 18px ${SUN}` : 'background:rgba(10,11,14,.7);box-shadow:inset 0 0 0 1.5px rgba(255,255,255,.45)'}">${on ? `<div class="center" style="width:26px;height:26px;color:#1A1206">${icon('check', 14, 2.4)}</div>` : ''}</div>`;
  })).join('');
  const body = `${eveSky}
  ${statusBar('21:33')}
  <div class="abs row" style="left:24px;right:24px;top:58px;justify-content:space-between;align-items:center">
    <span class="row" style="gap:8px;color:var(--fg2)">${icon('back', 20, 1.6)}<span class="mono fg3">Как тело? · Забито</span></span>
    ${dots(1, 3, IRIS)}
  </div>
  <div class="abs row" style="left:24px;top:94px;gap:12px;color:${SUN}">${picto('heavy', 30, 1.5)}<span class="d34" style="color:var(--fg)">Где?</span></div>
  <div class="abs mono fg3" style="left:24px;top:146px">тап по точке · можно не отмечать</div>
  <div class="abs" style="left:${left}px;top:${top}px">${fig}</div>
  ${spots}
  <div class="abs row" style="left:24px;top:628px;gap:8px;align-items:center">
    <span class="lbl">сзади</span>
    <span class="chip on" style="color:${SUN};box-shadow:inset 0 0 0 1px ${SUN}88">${icon('check', 13, 2.2)} спина</span>
    <span class="chip">поясница</span>
  </div>
  <div class="abs glass row" style="left:16px;right:16px;top:676px;border-radius:20px;padding:12px 14px;gap:12px;align-items:center">
    <span class="center" style="width:34px;height:34px;border-radius:17px;box-shadow:inset 0 0 0 1px var(--hair2);color:var(--fg2);flex:none">${picto('heart', 18, 1.6)}</span>
    <span class="fg2" style="font-size:13.5px;line-height:18px">Не усталость, а острая боль в суставе или давит в груди?</span>
    <span class="row" style="gap:4px;margin-left:auto;font-size:14px;color:var(--fg);white-space:nowrap">Это боль ${icon('arrow', 14, 1.8)}</span>
  </div>
  <div class="abs" style="left:24px;right:24px;top:766px"><div class="btn ghost" style="width:100%;box-shadow:inset 0 0 0 1px ${SUN}66">Дальше · 3 зоны ${icon('arrow', 18, 1.8)}</div></div>
  <div class="abs row mono fg3" style="left:0;right:0;top:842px;justify-content:center;gap:8px">${icon('lock', 13, 1.6)}<span>зоны видит только тренировка завтра</span></div>
  <div class="gesture"></div>`;
  return page(body);
}

// ------------------------------------------------------------------ sc6 · crisis net (A25 §2.3 / A07): sober, no glow, neutral avatar
function crisis() {
  const body = `<div class="abs" style="inset:0;background:linear-gradient(180deg,#0B0C10 0%,#08090C 100%)"></div>
  ${statusBar('7:02')}
  <div class="abs col" style="left:24px;right:24px;top:96px;align-items:center;text-align:center;gap:20px">
    <div class="center" style="width:76px;height:76px;border-radius:38px;box-shadow:inset 0 0 0 1px var(--hair2)">${avatar('calm', 56, '#C9CDD4')}</div>
    <div class="d34">Как ты на самом деле?</div>
    <div class="b16 fg2" style="max-width:320px">Второе утро подряд — тяжёлая погода. Я рядом и никуда не тороплю.</div>
    <div class="row" style="gap:10px;color:#9AA3B8;opacity:.9">${wx('rain', 26, '#9AA3B8')}${wx('rain', 26, '#9AA3B8')}</div>
  </div>
  <div class="abs col" style="left:16px;right:16px;top:420px;gap:12px">
    <div class="btn" style="background:#ECE8DF;color:#14161A;font-weight:600;height:60px;border-radius:30px">${icon('mentor', 20, 1.8)} Нужна помощь сейчас</div>
    <div class="btn ghost" style="height:60px;border-radius:30px">${icon('mentor', 20, 1.6)} Поговорить</div>
    <div class="btn" style="height:52px;color:var(--fg2)">Всё ок, просто день</div>
  </div>
  <div class="abs glass col" style="left:16px;right:16px;top:654px;border-radius:22px;padding:16px 18px;gap:8px">
    <span class="row" style="gap:10px;font-size:15px"><span class="num" style="font-size:19px;font-weight:500">988</span><span class="fg2">звонок · SMS · чат — бесплатно, круглосуточно</span></span>
    <span class="row" style="gap:10px;font-size:15px"><span class="num" style="font-size:19px;font-weight:500">911</span><span class="fg2">если опасность прямо сейчас</span></span>
  </div>
  <div class="abs row mono fg3" style="left:0;right:0;top:842px;justify-content:center;gap:8px">${icon('lock', 13, 1.6)}<span>без хокку и без режима · только на телефоне</span></div>
  <div class="gesture"></div>`;
  return page(body);
}

// ------------------------------------------------------------------ sc7 · settings «Что спрашивать»
function settings() {
  const known = [['sleep', 'сон', 'WHOOP'], ['charge', 'восстановление', 'WHOOP'], ['body', 'тренировки', 'WHOOP'], ['steps', 'шаги', 'телефон'], ['work', 'встречи', 'календарь'], ['goal', 'план ↔ факт', 'HoT']];
  const row = (glyph, title, sub, on, extra = '') => `<div class="row" style="height:58px;gap:14px;border-top:1px solid var(--hair)">
    <span class="center" style="width:34px;color:var(--fg2)">${glyph}</span>
    <div class="col" style="flex:1;gap:2px"><span class="b16">${title}</span><span class="fg3" style="font-size:12.5px;line-height:16px">${sub}</span></div>${extra}<div class="tg ${on ? 'on' : ''}"></div></div>`;
  const body = `${statusBar('20:10')}
  <div class="abs row" style="left:24px;right:24px;top:54px;gap:12px;align-items:center">${icon('back', 22, 1.6)}<span class="t21">Что спрашивать</span></div>
  <div class="abs col" style="left:16px;right:16px;top:100px;gap:10px">
    <span class="mono fg3" style="padding:0 8px">знаю без вопросов</span>
    <div class="glass" style="border-radius:22px;padding:12px 12px;display:grid;grid-template-columns:repeat(3,1fr);gap:10px 6px">
      ${known.map(([p, l, s]) => `<div class="col" style="align-items:center;gap:3px;text-align:center"><span style="color:${AQUA}">${picto(p, 22, 1.5)}</span><span style="font-size:13px">${l}</span><span class="lbl" style="font-size:9px">${s}</span></div>`).join('')}
    </div>
  </div>
  <div class="abs col" style="left:16px;right:16px;top:300px">
    <span class="mono" style="padding:0 8px 8px;color:${DAWN}">утро · до 20 секунд</span>
    <div class="glass" style="border-radius:22px;padding:0 14px">
      ${row(`<span style="color:${DAWN}">${picto('charge', 22, 1.5)}</span>`, 'Заряд', 'питает Заряд и режим дня', true).replace('border-top:1px solid var(--hair)', '')}
      ${row(wx('partly', 22, '#FFD9A0'), 'Погода внутри', 'питает Погоду', true)}
      ${row(`<span>${icon('layers', 22, 1.5)}</span>`, 'Третья карточка', 'только если данных не хватило', true)}
    </div>
    <span class="mono" style="padding:16px 8px 8px;color:${IRIS}">вечер · до 3 тапов</span>
    <div class="glass" style="border-radius:22px;padding:0 14px">
      ${row(`<span style="color:${IRIS}">${picto('body', 22, 1.5)}</span>`, 'Как тело?', 'настраивает тренировку завтра', true).replace('border-top:1px solid var(--hair)', '')}
      ${row(glass(22, 'currentColor', 1), 'Алкоголь', 'для сна и Топлива · без оценок', true)}
      ${row(picto('people', 22, 1.5), 'Ротация и вопрос недели', 'люди, «план помог?» · 1 карточка', true)}
      ${row(icon('lock', 22, 1.5), 'Близость', 'только ты · по биометрии', false)}
    </div>
  </div>
  <div class="abs row" style="left:24px;right:24px;top:836px;justify-content:space-between">
    <span class="row fg2" style="gap:8px;font-size:14px">${icon('haptic', 16, 1.6)}Отклик вибрацией</span><div class="tg on"></div>
  </div>
  <div class="gesture"></div>`;
  return page(body);
}

// ------------------------------------------------------------------ sheet · the «alphabet» of answers + responses by character
function sheet() {
  const W = 1600, H = 1180;
  const cell = (svg, l, c = 'var(--fg2)') => `<div class="col" style="align-items:center;gap:10px;width:112px"><div style="height:126px;display:flex;align-items:flex-end">${svg}</div><span class="lbl" style="color:${c}">${l}</span></div>`;
  const rowBlock = (y, title, sub, cells, col) => `<div class="abs" style="left:72px;top:${y}px;width:300px"><div class="row" style="gap:10px;color:${col}"><span class="t21" style="color:var(--fg)">${title}</span></div><div class="fg3" style="font-size:14px;line-height:19px;margin-top:6px">${sub}</div></div>
    <div class="abs row" style="left:380px;top:${y - 12}px;gap:22px">${cells}</div>`;
  const charge = CHARGE.map((l, i) => cell(chargeTitan(118, i + 1), l)).join('');
  const weather = WEATHER.map(([k, l, c, light, g], i) => cell(`<div class="col" style="align-items:center;gap:2px">${wx(k, 30, c)}${lightTitan(92, light, i >= 3 ? g : false)}</div>`, l)).join('');
  const bodyCells = [
    cell(lightTitan(118, ICE, 'rgba(125,227,209,.55)', { tY: -30 }), 'легко', AQUA),
    cell(fit(zonesFig(118, SORE(['hips', 'shoulders']), { base: 'rgba(255,255,255,.3)' }), 118, figW(118), 118), 'забито → где', SUN),
    cell(`<div style="position:relative">${lightTitan(118, IRIS_L, false, { tOpacity: .75 })}<div class="abs" style="right:-8px;top:-8px">${sickPicto(26, IRIS, 1.5)}</div></div>`, 'простуда', IRIS),
  ].join('');
  const alco = ['нет', '1', '2', '3', '4+'].map((l, i) => `<div class="col" style="align-items:center;gap:8px;width:70px"><div class="center" style="width:64px;height:64px;border-radius:32px;box-shadow:inset 0 0 0 1px var(--hair2)">${glass(28, '#C9CDD4', i)}</div><span class="lbl">${l}</span></div>`).join('');
  const head = [['туман', 16, .55], ['средне', 5, .8], ['ясно', 0, 1]].map(([l, b, o], i) => cell(`<div style="filter:blur(${b / 6}px);opacity:${o}">${lightTitan(104, i === 2 ? ['#FFFFFF', '#F4F1EA', '#E0DCD3'] : ['#E9ECF2', '#C9CFDA', '#A9B1BE'], i === 2 ? 'rgba(255,255,255,.45)' : false)}</div>`, l)).join('');
  const drive = [['нет сил', 60, ['#C9CFDA', '#A9B1BE', '#8D97A8'], false], ['норм', 0, ICE, false], ['рвусь', -70, ICE, 'rgba(125,227,209,.6)']].map(([l, ty, li, g]) => cell(lightTitan(104, li, g, { tY: ty }), l, AQUA)).join('');
  const sleepCells = head + '<div style="width:40px"></div>' + drive;
  const lines = [
    ['sensei', 'Сенсей Винс', SUN, '«День закрыт. Ноги забиты — утром дам им лёгкую работу. Отпусти день.»'],
    ['bro', 'Винс', DAWN, '«Принято. Ноги гудят — завтра без геройства, лёгкая тренировка. Телефон — на зарядку.»'],
    ['butler', 'Винсент', AQUA, '«Записал. Завтра я подготовлю щадящий вариант для ног. Доброй ночи.»'],
  ];
  const resp = lines.map(([k, n, c, t], i) => `<div class="abs glass row" style="left:${380 + i * 392}px;top:972px;width:372px;border-radius:22px;padding:14px 16px;gap:12px;align-items:flex-start">
    <div class="center" style="width:46px;height:46px;border-radius:23px;background:rgba(0,0,0,.35);box-shadow:inset 0 0 0 1px ${c}44;flex:none">${avatar(k, 36, c)}</div>
    <div class="col" style="gap:6px"><span class="mono" style="color:${c};font-size:10px">${n}</span><span style="font-size:14.5px;line-height:20px;font-weight:300">${t}</span></div></div>`).join('');
  const body = `<div class="abs" style="inset:0;background:radial-gradient(40% 50% at 92% 10%, rgba(255,199,122,.12), transparent 70%),radial-gradient(40% 50% at 8% 90%, rgba(168,163,255,.10), transparent 70%),#060708"></div>
  <div class="abs col" style="left:72px;top:52px;gap:10px;width:1400px">
    <span class="mono" style="color:var(--dawn)">HoT · A44 · карточки состояния</span>
    <div class="d55">Ответы — это состояния Титана</div>
    <div class="t21 fg2" style="font-size:18px">Один тап по картинке вместо слова. Подписи видны первые 3 раза и в TalkBack. Цвет никогда не единственный носитель смысла; красного нет.</div>
  </div>
  ${rowBlock(214, 'Заряд · пробуждение и после ритуала', 'свет наполняет Титана снизу вверх · дельта = что помогает', charge, DAWN)}
  ${rowBlock(384, 'Погода внутри · утро и вечер', 'настроение как погода · шторм — не «плохо», а тяжёлая погода', weather, DAWN)}
  ${rowBlock(554, 'Как тело? · утро и вечер', 'три Титана-ощущения (A39 §7.5) · «забито» открывает силуэт', bodyCells, IRIS)}
  <div class="abs" style="left:1040px;top:554px;width:500px">
    <div class="t21">Алкоголь · вечер или «вчера»</div><div class="fg3" style="font-size:14px;line-height:19px;margin-top:6px">один нейтральный бокал для всех ответов · «нет» первым · отключается</div>
    <div class="row" style="gap:12px;margin-top:22px">${alco}</div>
  </div>
  ${rowBlock(724, 'Голова · Тяга к делу', 'ясность — резкость Титана · мотивация — Титан поднимается', sleepCells, AQUA)}
  <div class="abs" style="left:72px;top:972px;width:290px"><div class="t21">Отклик · вечер</div><div class="fg3" style="font-size:14px;line-height:19px;margin-top:6px">одна строка в голосе характера · без оценок · об алкоголе ни слова</div></div>
  ${resp}
  <div class="abs row mono fg3" style="left:72px;bottom:28px;gap:26px">
    <span class="row" style="gap:6px">${icon('haptic', 13, 1.6)}тактильный отклик растёт с уровнем</span>
    <span>↑ смахни — пропустить · пропуск ≠ 0</span>
    <span>без стриков, баллов и «плохих» дней</span>
  </div>`;
  return { html: page(`<div class="screen dark" style="width:${W}px;height:${H}px">${body}</div>`, { w: W, h: H, wrap: false }), w: W, h: H };
}

// ================================================================== v0.2 · «то, что математика не видит» (A44 v0.2)
// Chips: behaviour and ritual icons (24 grid, monoline 1.6) — habits as icons, remembered from yesterday.
const CH = {
  screens: '<rect x="7" y="2.5" width="10" height="19" rx="2.5"/><path d="M11 18.5h2"/><path d="M20.6 5.2a2.6 2.6 0 0 1-3.2 3.2 2.6 2.6 0 1 0 3.2-3.2Z"/>',
  latefood: '<circle cx="11" cy="13" r="7"/><circle cx="11" cy="13" r="3.4"/><path d="M21 3.6a2.6 2.6 0 0 1-3.2 3.2 2.6 2.6 0 1 0 3.2-3.2Z"/>',
  sweets: '<circle cx="12" cy="12" r="4"/><path d="M8.6 10.2 4 7.5v9l4.6-2.7M15.4 10.2 20 7.5v9l-4.6-2.7"/>',
  latecoffee: '<path d="M4 9h11v4.5a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5Z"/><path d="M15 10.5h1.2a2.4 2.4 0 0 1 0 4.8H15"/><circle cx="18.5" cy="5.5" r="3"/><path d="M18.5 4.2v1.5l1 .7"/>',
  none: '<circle cx="12" cy="12" r="8.4"/><path d="M6.2 17.8 17.8 6.2"/>',
  coffee: '<path d="M4 9h11v4.5a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5Z"/><path d="M15 10.5h1.2a2.4 2.4 0 0 1 0 4.8H15"/><path d="M7.5 3.5c0 1 1 1 1 2s-1 1-1 2M11 3.5c0 1 1 1 1 2s-1 1-1 2"/>',
  capsule: '<g transform="rotate(-38 12 12)"><rect x="3.5" y="8.6" width="17" height="6.8" rx="3.4"/><path d="M12 8.6v6.8"/></g>',
  capsule2: '<g transform="rotate(-38 12 12)"><rect x="3.5" y="8.6" width="17" height="6.8" rx="3.4"/><path d="M12 8.6v6.8"/></g><circle cx="18.6" cy="18.6" r="2.4"/>',
  shower: '<path d="M5 21V8.5a4.5 4.5 0 0 1 9 0"/><path d="M11 8.5h6"/><path d="M12.5 12.5v1M15.5 12.5v1M18.5 12.5v1M14 16v1M17 16v1M12.5 19.5v1M15.5 19.5v1M18.5 19.5v1"/>',
  breath: '<path d="M3 9h10a3 3 0 1 0-3-3M3 13h15a3 3 0 1 1-3 3M3 17h6"/>',
  water: '<path d="M6.5 4h11l-1.4 15.2a1.6 1.6 0 0 1-1.6 1.4H9.5a1.6 1.6 0 0 1-1.6-1.4Z"/><path d="M7.4 10c1.5-1 3-1 4.6 0s3.1 1 4.6 0"/>',
  feed: '<rect x="6" y="2.5" width="12" height="19" rx="2.5"/><path d="M9 7h6M9 10.5h6M9 14h4"/>',
  video: '<rect x="3" y="5.5" width="18" height="13" rx="3"/><path d="M10.5 9.5v5l4-2.5Z"/>',
  games: '<path d="M7 8h10a4 4 0 0 1 3.9 4.9l-.9 3.7a2 2 0 0 1-3.4.9L14 15h-4l-2.6 2.5a2 2 0 0 1-3.4-.9l-.9-3.7A4 4 0 0 1 7 8Z"/><path d="M8 11v3M6.5 12.5h3"/><circle cx="16" cy="12" r=".7" fill="currentColor"/>',
  shop: '<path d="M5 8h14l-1 12H6Z"/><path d="M9 8V6.5a3 3 0 0 1 6 0V8"/>',
  adult: '<circle cx="12" cy="12" r="9.4"/><text x="12" y="14.6" font-size="7.6" font-weight="600" text-anchor="middle" fill="currentColor" stroke="none" font-family="Geist, sans-serif">18+</text>',
  nothing: '<circle cx="12" cy="12" r="8.6" stroke-dasharray="2 2.4"/>',
  partner: '<circle cx="9.2" cy="12" r="5.6"/><circle cx="14.8" cy="12" r="5.6"/>',
  solo: '<circle cx="12" cy="12" r="5.6"/>',
  wantless: '<path d="M4 12h16"/><path d="M8 8l-4 4 4 4"/>',
};
const ch = (k, size = 24, col = 'currentColor', sw = 1.6) => `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="${col}" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round" style="overflow:visible">${CH[k]}</svg>`;
const tile = (k, label, on, col = DAWN, w = 108, extra = '') => `<div class="col" style="width:${w}px;height:92px;border-radius:20px;align-items:center;justify-content:center;gap:8px;position:relative;${on ? `background:rgba(255,255,255,.08);box-shadow:inset 0 0 0 1.5px ${col},0 0 18px -6px ${col};color:${col}` : 'box-shadow:inset 0 0 0 1px var(--hair2);color:var(--fg2)'}">
  ${k === 'alcohol' ? glass(26, 'currentColor', on ? 2 : 1) : k === 'sun' ? icon('sun', 26, 1.6) : ch(k, 26)}<span style="font-size:12.5px;line-height:15px;text-align:center;color:${on ? 'var(--fg)' : 'var(--fg2)'}">${label}</span>
  ${on ? `<div class="abs center" style="right:8px;top:8px;width:18px;height:18px;border-radius:9px;background:${col};color:#1A1206">${icon('check', 11, 2.6)}</div>` : ''}${extra}</div>`;

// The live Titan of the wake deck: columns + ring, charge light, sore zones, weather over the head.
function liveTitan(cx, cy, R, { charge = 0, sore = [], weather = null, blur = 0, ring = 0 } = {}) {
  const S = sceneSize(R);
  const base = titanScene(S, { titan: false, ring, ringCol: DAWN, ringW: 10, colOpacity: .75, glow: 'rgba(255,199,122,.5)' });
  const zones = [];
  if (charge) zones.push({ rects: [{ x0: 300, x1: 1750, y0: LEVEL_TOP[charge], y1: 1900 }], lit: 1, color: '#FFE1AE', glow: 'rgba(255,199,122,.6)' });
  zones.push(...SORE(sore));
  const fig = zonesFig(S, zones, { crop: '0 0 2048 2048', base: 'rgba(255,255,255,.24)' });
  const w = weather ? `<div class="abs" style="left:${S / 2 - R * .275}px;top:${S * .245 - R * .62}px;opacity:.9;filter:drop-shadow(0 0 8px rgba(200,210,225,.35))">${wx(weather, R * .55, '#D5DAE3', 1.2)}</div>` : '';
  return `<div class="abs" style="left:${cx - S / 2}px;top:${cy - S / 2}px;width:${S}px;height:${S}px">${base}<div class="abs" style="left:0;top:0;${blur ? `filter:blur(${blur}px)` : ''}">${fig}</div>${w}</div>`;
}
const answerTrail = (items) => `<div class="row" style="gap:6px;justify-content:center">${items.map(([g, t, c]) => `<span class="tok" style="color:${c || 'var(--fg2)'}">${g}${t ? `<span class="num">${t}</span>` : ''}</span>`).join('')}</div>`;
const nightSky = `<div class="abs" style="inset:0;background:radial-gradient(70% 34% at 50% 26%,rgba(160,150,255,.10),transparent 70%),linear-gradient(180deg,#07070D 0%,#0A0B14 45%,#060708 100%)"></div>`;
const fan = (top, h) => `<div class="card" style="left:30px;right:30px;top:${top + 18}px;height:${h}px;background:rgba(255,255,255,.025);box-shadow:inset 0 0 0 1px var(--hair);transform:rotate(2.2deg)"></div>
  <div class="card" style="left:24px;right:24px;top:${top + 9}px;height:${h}px;background:rgba(255,255,255,.035);box-shadow:inset 0 0 0 1px var(--hair);transform:rotate(-1.4deg)"></div>`;

// ------------------------------------------------------------------ w1 · wake deck as a game: card 4/7 «Голова», the Titan reacts live
function wakeDeck() {
  const opts = [['туман', 16, .55], ['средне', 5, .8], ['ясно', 0, 1]].map(([l, b, o], i) => `<div class="opt ${i === 0 ? 'sel' : ''}" style="--c:#C9CFDA;width:104px;padding:12px 0 10px;border-radius:22px;${i ? 'box-shadow:inset 0 0 0 1px var(--hair)' : ''}">
      <div style="filter:blur(${b / 6}px);opacity:${o}">${lightTitan(104, i === 2 ? ['#FFFFFF', '#F4F1EA', '#E0DCD3'] : ['#E9ECF2', '#C9CFDA', '#A9B1BE'], i === 2 ? 'rgba(255,255,255,.45)' : false)}</div>
      <span style="font-size:14px;font-weight:500;color:${i === 0 ? 'var(--fg)' : 'var(--fg2)'}">${l}</span></div>`).join('');
  const body = `${nightSky}
  ${statusBar('6:41')}
  <div class="abs row" style="left:24px;right:24px;top:52px;justify-content:space-between;align-items:center">
    <span class="mono fg3">пробуждение · 4 / 7</span>
    <span class="row mono fg3" style="gap:6px">${icon('moon', 13, 1.6)}≈ 35 с осталось</span>
  </div>
  ${liveTitan(206, 228, 108, { charge: 2, sore: ['hips'], weather: 'cloud', blur: 1.2, ring: .4 })}
  <div class="abs" style="left:0;right:0;top:370px">${answerTrail([[picto('charge', 13, 1.6), '▮▮▯▯▯', DAWN], [wx('cloud', 13, '#C9CFDA'), '', ''], [picto('heavy', 13, 1.6), 'бёдра', SUN]])}</div>
  ${fan(430, 330)}
  <div class="card glass" style="left:16px;right:16px;top:430px;height:330px;padding:22px 14px 16px">
    <div class="row" style="justify-content:space-between;padding:0 6px">
      <div class="row" style="gap:10px;color:#C9CFDA">${picto('mind', 26, 1.6)}<span class="t21" style="color:var(--fg)">Голова</span></div>
      ${dots(4, 7, '#C9CFDA')}
    </div>
    <div class="row" style="justify-content:space-between;margin-top:26px">${opts}</div>
    <div class="row mono fg3" style="justify-content:center;gap:8px;margin-top:26px">${icon('haptic', 14, 1.6)}<span>тап — и Титан меняется сразу</span></div>
    <div class="row" style="justify-content:space-between;margin-top:20px;padding:0 6px">
      <span class="row fg3" style="gap:6px;font-size:13.5px">${icon('back', 14, 1.6)} назад</span>
      <span class="row fg2" style="gap:6px;font-size:13.5px">↑ пропустить</span>
    </div>
  </div>
  <div class="abs row" style="left:24px;right:24px;top:822px;justify-content:space-between;align-items:center">
    <span class="chip">${icon('bolt', 14, 1.8)} Быстро</span>
    <span class="mono fg3">дальше: тяга · вчера · только ты</span>
  </div>
  <div class="gesture"></div>`;
  return page(body);
}

// ------------------------------------------------------------------ w2 · «Вчера вечером»: behaviour as icon chips (6/7)
function yesterday() {
  const tiles = [['screens', 'экраны после 23', true, IRIS], ['latefood', 'поздняя еда', false], ['alcohol', 'алкоголь', true, '#C9CDD4'], ['sweets', 'сладкое', false], ['latecoffee', 'кофе после 14', false], ['none', 'ничего из этого', false]];
  const body = `${nightSky}
  ${statusBar('6:42')}
  <div class="abs row" style="left:24px;right:24px;top:52px;justify-content:space-between;align-items:center">
    <span class="mono fg3">пробуждение · 6 / 7</span>
    <span class="row mono fg3" style="gap:6px">${icon('moon', 13, 1.6)}≈ 15 с</span>
  </div>
  ${liveTitan(206, 196, 86, { charge: 2, sore: ['hips'], weather: 'cloud', ring: .4 })}
  <div class="abs" style="left:0;right:0;top:312px">${answerTrail([[picto('charge', 13, 1.6), '▮▮▯▯▯', DAWN], [wx('cloud', 13, '#C9CFDA'), '', ''], [picto('heavy', 13, 1.6), '', SUN], [picto('mind', 13, 1.6), 'туман', '#C9CFDA'], [picto('goal', 13, 1.6), 'норм', AQUA]])}</div>
  ${fan(352, 400)}
  <div class="card glass" style="left:16px;right:16px;top:352px;height:400px;padding:20px 16px 16px">
    <div class="row" style="justify-content:space-between;padding:0 4px">
      <div class="row" style="gap:10px;color:${IRIS}">${icon('moon', 24, 1.6)}<span class="t21" style="color:var(--fg)">Вчера вечером</span></div>
      ${dots(6, 7, IRIS)}
    </div>
    <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin-top:18px;justify-items:center">${tiles.map(([k, l, on, c]) => tile(k, l, on, c || DAWN)).join('')}</div>
    <div class="row" style="gap:8px;margin-top:14px;padding:0 4px;align-items:center">
      <span class="mono fg3" style="margin-right:4px">сколько</span>
      ${['1', '2', '3', '4+'].map((n) => `<span class="center" style="width:44px;height:34px;border-radius:17px;font-size:14px;${n === '2' ? 'background:rgba(255,255,255,.12);box-shadow:inset 0 0 0 1.5px #C9CDD4' : 'box-shadow:inset 0 0 0 1px var(--hair2);color:var(--fg2)'}">${n}</span>`).join('')}
    </div>
    <div class="row" style="justify-content:space-between;margin-top:16px;padding:0 4px">
      <span class="row mono fg3" style="gap:6px">${icon('check', 13, 2)}вечером отмечено</span>
      <span class="row" style="gap:6px;font-size:14px">дальше ${icon('arrow', 14, 1.8)}</span>
    </div>
  </div>
  <div class="abs row mono fg3" style="left:0;right:0;top:790px;justify-content:center;gap:8px">${icon('lock', 13, 1.6)}<span>7 / 7 — «только ты», если включено</span></div>
  <div class="gesture"></div>`;
  return page(body);
}

// ------------------------------------------------------------------ w3 · reveal: you vs WHOOP («ножницы»), then mode and mentor
function reveal() {
  const fig = (h, lvl, glow) => chargeTitan(h, lvl, { base: 'rgba(255,255,255,.2)', glow });
  const body = `${dawnSky}
  ${statusBar('6:43')}
  <div class="abs col" style="left:0;right:0;top:58px;align-items:center;gap:8px">
    <span class="mono fg3">среда · 11 ноября · раскрытие</span>
    <div class="t21" style="text-align:center">Ты и WHOOP разошлись</div>
  </div>
  <div class="abs row" style="left:28px;right:28px;top:128px;justify-content:space-between;align-items:flex-end">
    <div class="col" style="align-items:center;gap:10px">
      <div style="position:relative">${fig(172, 2)}<div class="abs" style="right:-6px;top:-10px">${wx('cloud', 30, '#D5DAE3', 1.4)}</div></div>
      <span class="mono" style="color:var(--fg)">ты</span><span class="tok">${picto('charge', 12, 1.6)}мало · облачно</span>
    </div>
    <div class="col" style="align-items:center;gap:8px;padding-bottom:62px">
      <svg width="54" height="120" viewBox="0 0 54 120"><path d="M27 4v112" stroke="rgba(255,255,255,.18)" stroke-dasharray="3 5"/><path d="M8 44 46 76M46 44 8 76" stroke="${DAWN}" stroke-width="1.6" stroke-linecap="round" opacity=".85"/></svg>
      <span class="mono" style="color:${DAWN}">ножницы</span>
    </div>
    <div class="col" style="align-items:center;gap:10px">
      ${fig(172, 4, 'rgba(255,199,122,.6)')}
      <span class="mono" style="color:var(--fg)">WHOOP</span><span class="tok">${picto('sleep', 12, 1.6)}7:48 ${picto('heart', 12, 1.6)}−3</span>
    </div>
  </div>
  <div class="abs col" style="left:24px;right:24px;top:476px;align-items:center;gap:12px;text-align:center">
    <div class="row" style="gap:12px;color:${AQUA}">${picto('rovno', 34, 1.4)}<span class="d34" style="color:${AQUA}">Ровно</span></div>
    <span class="fg2" style="font-size:14px;line-height:19px;max-width:330px">тело восстановилось, ощущение — нет. Это тоже данные: режим ниже, чем по одному WHOOP</span>
  </div>
  <div class="abs glass row" style="left:16px;right:16px;top:604px;border-radius:24px;padding:14px 16px;gap:14px;align-items:center">
    <div class="center" style="width:48px;height:48px;border-radius:24px;background:rgba(0,0,0,.35);box-shadow:inset 0 0 0 1px ${DAWN}44;flex:none">${avatar('bro', 38, DAWN)}</div>
    <div class="col" style="gap:4px">
      <span class="b16" style="font-weight:300">«Выспался, а ощущение так себе. Бывает. Делай свой утренний ритуал — потом сверим Заряд.»</span>
      <span class="mono" style="color:${DAWN};font-size:10px">Винс</span>
    </div>
  </div>
  <div class="abs row mono fg3" style="left:0;right:0;top:732px;justify-content:center;gap:8px;font-size:10px"><span style="color:${AQUA}">утра с тобой 23 из 30</span><span>·</span><span>совпали с WHOOP 16 из 23</span></div>
  <div class="abs" style="left:24px;right:24px;top:770px"><div class="btn light-pill" style="width:100%">Начать день ${icon('arrow', 18, 1.8)}</div></div>
  <div class="abs row mono fg3" style="left:0;right:0;top:842px;justify-content:center;gap:8px">${icon('spark', 13, 1.6)}<span>«после ритуала» спрошу при следующем открытии</span></div>
  <div class="gesture"></div>`;
  return page(body);
}

// ------------------------------------------------------------------ w4 · after the ritual: stack «как вчера» + charge now + delta
function ritual() {
  const stack = [['coffee', 'кофе', true], ['capsule', 'Alpha-GPC', true], ['capsule2', 'L-тирозин', true], ['shower', 'холодный душ', true], ['sun', 'свет', false], ['breath', 'дыхание', false]];
  const lvl = 4;
  const opts = CHARGE.map((l, i) => `<div class="opt ${i + 1 === lvl ? 'sel' : ''}" style="--c:${DAWN};width:62px;padding:8px 0 6px">
      ${chargeTitan(64, i + 1, { base: 'rgba(255,255,255,.18)' })}<span class="lbl" style="${i + 1 === lvl ? 'color:var(--fg)' : ''}">${l}</span></div>`).join('');
  const body = `<div class="abs" style="inset:0;background:radial-gradient(80% 30% at 50% 0%,rgba(255,199,122,.14),transparent 70%),#060708"></div>
  ${statusBar('7:41')}
  <div class="abs row" style="left:24px;right:24px;top:52px;justify-content:space-between;align-items:center">
    <span class="mono" style="color:${DAWN}">после ритуала · 15 секунд</span>
    <span class="fg3">${icon('close', 18, 1.6)}</span>
  </div>
  <div class="abs" style="left:24px;top:82px"><div class="d34">Утро как вчера?</div></div>
  <div class="card glass" style="left:16px;right:16px;top:142px;height:262px;padding:16px">
    <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:10px;justify-items:center">${stack.map(([k, l, on]) => tile(k, l, on, DAWN, 108)).join('')}</div>
    <div class="row" style="justify-content:space-between;margin-top:12px;padding:0 4px">
      <span class="mono fg3">из твоего стека · без доз</span>
      <span class="chip on" style="color:${DAWN};box-shadow:inset 0 0 0 1px ${DAWN}88">${icon('check', 13, 2.2)} как вчера</span>
    </div>
  </div>
  <div class="card glass" style="left:16px;right:16px;top:420px;height:184px;padding:16px 14px">
    <div class="row" style="gap:10px;padding:0 6px;color:${DAWN}">${picto('charge', 24, 1.6)}<span class="t21" style="color:var(--fg)">Заряд сейчас</span></div>
    <div class="row" style="justify-content:space-between;margin-top:12px">${opts}</div>
  </div>
  <div class="abs glass" style="left:16px;right:16px;top:636px;border-radius:24px;padding:16px 18px">
    <div class="row" style="align-items:center;justify-content:space-between">
      <div class="col" style="align-items:center;gap:4px">${chargeTitan(74, 2, { base: 'rgba(255,255,255,.18)' })}<span class="mono fg3">6:41</span></div>
      <div class="col" style="align-items:center;gap:2px;flex:1">
        <span class="d55 num" style="color:${DAWN};font-size:46px;line-height:48px">+2</span>
        <svg width="150" height="12" viewBox="0 0 150 12"><path d="M4 6h138M136 1l6 5-6 5" fill="none" stroke="${DAWN}" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>
        <span class="mono fg3" style="font-size:10px">уровня за час</span>
      </div>
      <div class="col" style="align-items:center;gap:4px">${chargeTitan(74, 4, { base: 'rgba(255,255,255,.18)', glow: 'rgba(255,199,122,.6)' })}<span class="mono fg3">7:41</span></div>
    </div>
    <div class="fg3" style="font-size:12.5px;line-height:17px;margin-top:10px">Бодрость после сна растёт и сама. Открытие сравнит утра с ритуалом и без него — не раньше 8 и 8.</div>
  </div>
  <div class="abs row mono fg3" style="left:0;right:0;top:848px;justify-content:center;gap:8px">${icon('widget', 13, 1.6)}<span>без пуша: карточка на «Сегодня» до 12:00</span></div>
  <div class="gesture"></div>`;
  return page(body);
}

// ------------------------------------------------------------------ w5 · «только ты» (S5, opt-in): intimacy + quick rewards
function privateLayer() {
  const intim = [['nothing', 'не было'], ['partner', 'с партнёром'], ['solo', 'сам с собой'], ['none', 'не отмечать']];
  const pulls = [['feed', 'лента', true], ['video', 'видео', true], ['games', 'игры', false], ['sweets', 'сладкое', false], ['shop', 'покупки', false], ['adult', '18+', true]];
  const body = `<div class="abs" style="inset:0;background:linear-gradient(180deg,#0A0A0E 0%,#07070A 100%)"></div>
  ${statusBar('6:43')}
  <div class="abs row" style="left:24px;right:24px;top:52px;justify-content:space-between;align-items:center">
    <span class="row mono fg3" style="gap:8px">${icon('lock', 13, 1.6)}только ты · 7 / 7</span>
    <span class="tok">${icon('eye', 12, 1.6)}по отпечатку</span>
  </div>
  <div class="abs" style="left:24px;top:84px"><div class="d34">Вчера</div></div>
  <div class="card" style="left:16px;right:16px;top:142px;padding:16px;background:rgba(255,255,255,.04);box-shadow:inset 0 0 0 1px var(--hair2)">
    <div class="row" style="justify-content:space-between"><span class="t21" style="font-size:18px">Близость</span><span class="mono fg3">1 тап</span></div>
    <div class="row" style="justify-content:space-between;margin-top:14px">${intim.map(([k, l], i) => `<div class="col" style="width:80px;height:84px;border-radius:18px;align-items:center;justify-content:center;gap:8px;${i === 2 ? 'background:rgba(255,255,255,.09);box-shadow:inset 0 0 0 1.5px #D9D6CF' : 'box-shadow:inset 0 0 0 1px var(--hair2)'};color:${i === 2 ? 'var(--fg)' : 'var(--fg2)'}">${ch(k, 26)}<span style="font-size:12px;line-height:14px;text-align:center">${l}</span></div>`).join('')}</div>
  </div>
  <div class="card" style="left:16px;right:16px;top:300px;padding:16px;background:rgba(255,255,255,.04);box-shadow:inset 0 0 0 1px var(--hair2)">
    <div class="row" style="justify-content:space-between"><span class="t21" style="font-size:18px">Что тянуло?</span><span class="mono fg3">по иконкам</span></div>
    <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin-top:14px;justify-items:center">${pulls.map(([k, l, on]) => tile(k, l, on, '#D9D6CF', 104)).join('')}</div>
    <div class="row" style="gap:10px;margin-top:14px;align-items:center">
      <span class="chip" style="height:30px;font-size:12.5px">${ch('wantless', 14)} хотелось меньше</span>
      <span class="fg3" style="font-size:12px;line-height:16px">по желанию · не оценка</span>
    </div>
  </div>
  <div class="abs glass col" style="left:16px;right:16px;top:666px;border-radius:20px;padding:14px 16px;gap:6px">
    <span class="row" style="gap:8px;font-size:14px">${icon('spark', 14, 1.6)}<span>Раз в неделю: «Хватает ли тебе близости?» 1–5</span></span>
    <span class="fg3" style="font-size:12.5px;line-height:17px">Частоту не оцениваем и норм нет. Связи покажу, только если сам включишь «замечать связи».</span>
  </div>
  <div class="abs col mono fg3" style="left:24px;right:24px;top:800px;align-items:center;gap:6px;text-align:center">
    <span class="row" style="gap:8px">${icon('lock', 13, 1.6)}S5 · зашифровано · только на телефоне</span>
    <span>никогда в пушах, виджетах, отклике и у наставника</span>
  </div>
  <div class="gesture"></div>`;
  return page(body);
}

// ------------------------------------------------------------------ w6 · «Открытие»: a personal link as a reward + collection
function discovery() {
  const coll = [
    ['shower', 'душ → Заряд', 'open', DAWN], ['screens', 'экраны → сон', 'open', IRIS], ['alcohol', 'алкоголь → пульс', 'open', '#C9CDD4'],
    ['capsule', 'стек → Голова', 3, AQUA], ['sweets', 'сладкое → Погода', 5, AQUA], ['lock', 'только ты', 'lock', '#80858D'],
  ];
  const cell = ([k, l, st, c]) => `<div class="col" style="width:110px;height:104px;border-radius:20px;align-items:center;justify-content:center;gap:8px;position:relative;${st === 'open' ? `background:rgba(255,255,255,.06);box-shadow:inset 0 0 0 1px ${c}66;color:${c}` : 'box-shadow:inset 0 0 0 1px var(--hair);color:var(--fg3)'}">
    ${typeof st === 'number' ? `<svg class="abs" style="left:31px;top:14px" width="48" height="48" viewBox="0 0 48 48"><circle cx="24" cy="24" r="21" fill="none" stroke="rgba(255,255,255,.1)" stroke-width="2"/><circle cx="24" cy="24" r="21" fill="none" stroke="${c}" stroke-width="2" stroke-linecap="round" stroke-dasharray="${132 * (1 - st / 10)} 132" transform="rotate(-90 24 24)"/></svg>` : ''}
    <div style="height:48px;display:flex;align-items:center">${k === 'alcohol' ? glass(26, 'currentColor', 1) : k === 'lock' ? icon('lock', 24, 1.6) : ch(k, 26)}</div>
    <span style="font-size:11.5px;line-height:14px;color:${st === 'open' ? 'var(--fg)' : 'var(--fg3)'}">${l}</span>
    ${typeof st === 'number' ? `<span class="lbl" style="font-size:9px;margin-top:-4px">ещё ${st} утр</span>` : ''}</div>`;
  const body = `<div class="abs" style="inset:0;background:radial-gradient(70% 34% at 50% 22%,rgba(255,199,122,.20),transparent 70%),#060708"></div>
  ${statusBar('9:12')}
  <div class="abs row" style="left:24px;right:24px;top:52px;justify-content:space-between;align-items:center">
    <span class="mono" style="color:${DAWN}">открытие №1 · 21 день</span><span class="fg3">${icon('close', 18, 1.6)}</span>
  </div>
  <div class="abs" style="left:116px;top:84px">${chargeTitan(150, 5, { base: 'rgba(255,255,255,.2)', glow: 'rgba(255,199,122,.7)' })}</div>
  <div class="abs center" style="left:262px;top:96px;width:54px;height:54px;border-radius:27px;background:rgba(10,11,14,.85);box-shadow:inset 0 0 0 1px ${DAWN}88;color:${DAWN}">${ch('shower', 28)}</div>
  <div class="abs col" style="left:24px;right:24px;top:252px;align-items:center;text-align:center;gap:6px">
    <div class="t21">После холодного душа Заряд растёт сильнее</div>
    <div class="row" style="gap:10px;align-items:baseline"><span class="d55 num" style="color:${DAWN}">+1,4</span><span class="fg2">уровня к 9:00</span></div>
    <span class="tok">11 утр с душем · 9 без · связь, не причина</span>
  </div>
  <div class="abs glass row" style="left:16px;right:16px;top:420px;border-radius:22px;padding:12px 14px;gap:12px;align-items:center">
    <div class="center" style="width:42px;height:42px;border-radius:21px;background:rgba(0,0,0,.35);box-shadow:inset 0 0 0 1px ${DAWN}44;flex:none">${avatar('bro', 32, DAWN)}</div>
    <span style="font-size:14.5px;line-height:20px;font-weight:300">«Твой душ работает на тебя, а не на статистику. Держим.»</span>
  </div>
  <div class="abs row" style="left:24px;right:24px;top:510px;justify-content:space-between;align-items:center">
    <span class="mono fg2">открытия</span><span class="mono fg3">3 из 12 возможных</span>
  </div>
  <div class="abs" style="left:16px;right:16px;top:540px;display:grid;grid-template-columns:repeat(3,1fr);gap:10px;justify-items:center">${coll.map(cell).join('')}</div>
  <div class="abs row" style="left:24px;right:24px;top:790px;gap:10px">
    <div class="btn ghost" style="flex:1;height:50px">Как считал</div>
    <div class="btn light-pill" style="flex:1;height:50px">Принято</div>
  </div>
  <div class="abs row mono fg3" style="left:0;right:0;top:858px;justify-content:center;gap:8px"><span>прогресс открытий не сгорает и не наказывает</span></div>
  <div class="gesture"></div>`;
  return page(body);
}

// ------------------------------------------------------------------ sc7 v0.2 · settings «Что спрашивать»
function settings2() {
  const row = (g, t, s, on, first) => `<div class="row" style="height:46px;gap:12px;${first ? '' : 'border-top:1px solid var(--hair)'}">
    <span class="center" style="width:28px;color:var(--fg2)">${g}</span><div class="col" style="flex:1"><span style="font-size:15px">${t}</span>${s ? `<span class="fg3" style="font-size:11.5px;line-height:14px">${s}</span>` : ''}</div><div class="tg ${on ? 'on' : ''}"></div></div>`;
  const grp = (title, col, rows, top) => `<div class="abs col" style="left:16px;right:16px;top:${top}px"><span class="mono" style="padding:0 8px 6px;color:${col}">${title}</span>
    <div class="glass" style="border-radius:20px;padding:0 14px">${rows.map((r, i) => row(...r, i === 0)).join('')}</div></div>`;
  const body = `${statusBar('20:10')}
  <div class="abs row" style="left:24px;right:24px;top:52px;gap:12px;align-items:center">${icon('back', 22, 1.6)}<span class="t21">Что спрашивать</span></div>
  ${grp('пробуждение · ≈ 60 с · колода', DAWN, [
    [picto('charge', 20, 1.5), 'Заряд · Погода · Тело', 'ядро, «Быстро» оставляет Заряд и Погоду', true],
    [picto('mind', 20, 1.5), 'Голова · Тяга к делу', 'Ясность и мотивация', true],
    [icon('moon', 20, 1.5), 'Вчера вечером', 'экраны, еда, алкоголь, сладкое, кофе', true]], 92)}
  ${grp('после ритуала · ≈ 15 с · без пуша', DAWN, [
    [ch('coffee', 20), 'Стек утра + Заряд сейчас', 'как вчера — 1 тап', true],
    [icon('widget', 20, 1.5), 'Напомнить пушем', 'по умолчанию — карточка на «Сегодня»', false]], 284)}
  ${grp('вечер · ≈ 20 с', IRIS, [
    [picto('body', 20, 1.5), 'Тело · Алкоголь · Погода вечера', '', true]], 418)}
  ${grp('только ты · S5 · по отпечатку', '#C9CDD4', [
    [ch('partner', 20), 'Близость', 'один раз предложу на 7-й день', false],
    [ch('feed', 20), 'Что тянуло', 'лента, видео, игры, сладкое, покупки, 18+', false],
    [icon('spark', 20, 1.5), 'Замечать связи', 'открытия по этим данным', false]], 504)}
  ${grp('игра', AQUA, [
    [icon('spark', 20, 1.5), 'Открытия и коллекция', 'после 8 дней «с» и 8 «без»', true],
    [icon('haptic', 20, 1.5), 'Отклик вибрацией', '', true]], 704)}
  <div class="gesture"></div>`;
  return page(body);
}

// ------------------------------------------------------------------ render
(async () => {
  const { chromium } = require('playwright');
  const browser = await chromium.launch();
  const shot = async (name, html, w = 412, h = 915, scale = 2) => {
    const f = path.join(HTML, `${name}.html`);
    fs.writeFileSync(f, html);
    const ctx = await browser.newContext({ viewport: { width: w, height: h }, deviceScaleFactor: scale });
    const p = await ctx.newPage();
    await p.goto('file://' + f);
    await p.evaluate(() => document.fonts.ready);
    await p.waitForTimeout(400);
    await p.screenshot({ path: path.join(OUT, `${name}.png`) });
    await ctx.close();
    console.log('✓', name);
  };
  const only = process.env.ONLY ? process.env.ONLY.split(',') : null;
  const jobs = [
    ['w1-wake-deck', wakeDeck], ['w2-yesterday', yesterday], ['w3-reveal-scissors', reveal], ['w4-ritual-delta', ritual],
    ['w5-private-layer', privateLayer], ['w6-discovery', discovery],
    ['e1-evening-20s', eveningDeck], ['sc5-body-zones', bodyZones], ['sc6-crisis-net', crisis], ['sc7-settings-ask', settings2],
  ];
  for (const [n, fn] of jobs) if (!only || only.includes(n)) await shot(n, fn());
  if (!only || only.includes('_state-cards')) { const r = sheet(); await shot('_state-cards', r.html, r.w, r.h, 1.5); }
  await browser.close();
})();
