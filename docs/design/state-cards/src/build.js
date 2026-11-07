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
    <span class="mono fg3">Вт · вечер · 3 тапа</span>
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
    ${picto('people', 20, 1.5)}<span class="fg2" style="font-size:14px">Люди сегодня</span><span class="mono fg3" style="margin-left:auto">3 / 3 · ротация</span>
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
  const sleepCells = [1, 2, 3, 4, 5].map((l) => cell(chargeTitan(104, l, { col: IRIS_L, glow: 'rgba(168,163,255,.6)' }), ['рвано', 'тонко', 'так себе', 'хорошо', 'глубоко'][l - 1], IRIS)).join('');
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
  ${rowBlock(214, 'Заряд · утро', 'свет наполняет Титана снизу вверх · питает Заряд и режим (SG-SELF)', charge, DAWN)}
  ${rowBlock(384, 'Погода внутри · утро', 'настроение как погода · шторм — не «плохо», а тяжёлая погода', weather, DAWN)}
  ${rowBlock(554, 'Как тело? · вечер', 'три Титана-ощущения (A39 §7.5) · «забито» открывает силуэт', bodyCells, IRIS)}
  <div class="abs" style="left:1040px;top:554px;width:500px">
    <div class="t21">Алкоголь · вечер</div><div class="fg3" style="font-size:14px;line-height:19px;margin-top:6px">один нейтральный бокал для всех ответов · «нет» первым · отключается</div>
    <div class="row" style="gap:12px;margin-top:22px">${alco}</div>
  </div>
  ${rowBlock(724, 'Как спалось? · утро', 'только если WHOOP не видел ночь · ночное ирисовое свечение', sleepCells, IRIS)}
  <div class="abs" style="left:72px;top:972px;width:290px"><div class="t21">Отклик · вечер</div><div class="fg3" style="font-size:14px;line-height:19px;margin-top:6px">одна строка в голосе характера · без оценок · об алкоголе ни слова</div></div>
  ${resp}
  <div class="abs row mono fg3" style="left:72px;bottom:28px;gap:26px">
    <span class="row" style="gap:6px">${icon('haptic', 13, 1.6)}тактильный отклик растёт с уровнем</span>
    <span>↑ смахни — пропустить · пропуск ≠ 0</span>
    <span>без стриков, баллов и «плохих» дней</span>
  </div>`;
  return { html: page(`<div class="screen dark" style="width:${W}px;height:${H}px">${body}</div>`, { w: W, h: H, wrap: false }), w: W, h: H };
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
    ['sc1-morning-charge', morningCharge], ['sc2-morning-weather', morningWeather], ['sc3-dawn-response', dawnResponse],
    ['sc4-evening-body', eveningDeck], ['sc5-body-zones', bodyZones], ['sc6-crisis-net', crisis], ['sc7-settings-ask', settings],
  ];
  for (const [n, fn] of jobs) if (!only || only.includes(n)) await shot(n, fn());
  if (!only || only.includes('_state-cards')) { const r = sheet(); await shot('_state-cards', r.html, r.w, r.h, 1.5); }
  await browser.close();
})();
