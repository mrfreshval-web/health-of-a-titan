// Vince face concepts (agents 4 + 17). SVG in HTML -> PNG via Playwright @2x.
// Usage: node docs/design/vince-face/src/build.js [outDir]
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');
const { DEFS, titanScene } = require('../../concept-v3/src/titan');

const SRC = __dirname;
const OUT = path.resolve(process.argv[2] || '/mnt/project-files/brand/vince-face/');
const HTML = path.join(SRC, 'html');
fs.mkdirSync(HTML, { recursive: true });

const GOLD = '#C9A23A', IVORY = '#EDE5D3', INK = '#161A22', DGOLD = '#7A5A12';

// ---------- geometry (viewBox 0 0 100 100, ring r=40) ----------
const C = 50, R = 40;
const pol = (deg, r = R) => { const a = (deg - 90) * Math.PI / 180; return [C + r * Math.cos(a), C + r * Math.sin(a)]; };
const f = (n) => +n.toFixed(2);

const PULSE = {
  none: null,
  mark: 'M4 54 H34 L37 50 L40 58 L44 30 L48.5 70 L52 50 L55 54 H96',
  smile: 'M4 62 H34 C40 62 43 67 50 67 C56 67 58.5 63 61.5 62 L64.5 54.5 L67.5 66.5 L69.5 62 H96',
  sensei: 'M2 62 H45 L48 50 L51.5 66 L53.5 62 H98',
  bro: 'M-4 61 H33 C39 61 43 65.5 50 65.5 C56 65.5 59.5 62 62 59 L65 49 L68 65 L71 52 L74 63 L77 58.5 H106',
  calm: 'M4 62 H36 C42 62 44 65 50 65 C56 65 58 62 62 62 L64 57.5 L66.5 65 L68.3 62 H96',
  listen: 'M4 62 H36 L38 60.5 L40 63.5 L42 59 L44 65 L46 57.5 L48 66.5 L50 57 L52 66 L54 58.5 L56 64.5 L58 60 L60 63 L62 61.2 L63.5 62 H96',
  think: 'M4 62 H40 M60 62 H96',
  happy: 'M4 61 H31 C37 61 41 68.5 50 68.5 C58 68.5 61 61 64.5 61 L67 47 L70.5 69 L73 61 H96',
  gentle: 'M4 63.5 C24 63.5 32 65.5 50 65.5 C68 65.5 76 63.5 96 63.5',
  coach: 'M4 62 H29 L31.5 52 L34.5 67 L36.5 62 H45.5 L48 52 L51 67 L53 62 H62 L64.5 52 L67.5 67 L69.5 62 H96',
};
const BOW = 'M4 64 H41.5 M41.5 64 V60.2 L50 64 L58.5 67.8 V60.2 L50 64 L41.5 67.8 V64 M58.5 64 H96';

// eyes: return svg for both eyes
function eyes(type, col, o = {}) {
  const y = o.y ?? 45, dx = o.dx ?? 0, dy = o.dy ?? 0;
  const L = 40 + dx, Rx = 60 + dx, Y = y + dy;
  const both = (fn) => fn(L, -1) + fn(Rx, 1);
  switch (type) {
    case 'dot': return both((x) => `<circle cx="${x}" cy="${Y}" r="${o.r ?? 2.6}" fill="${col}" stroke="none"/>`);
    case 'half': return both((x) => `<path d="M${x - 3.8} ${Y + 0.6} Q${x} ${Y - 1.2} ${x + 3.8} ${Y + 0.6}" stroke-width="${o.sw * 0.75}"/><path d="M${x - 1.9} ${Y + 0.1} A1.9 1.9 0 0 0 ${x + 1.9} ${Y + 0.1} Z" fill="${col}" stroke="none"/>`);
    case 'lively': return both((x, s) => `<circle cx="${x}" cy="${Y}" r="2.8" fill="${col}" stroke="none"/><path d="M${x - 3.8} ${Y - 7 + (s > 0 ? -1.2 : 0.4)} L${x + 3.8} ${Y - 7.6 + (s > 0 ? 0.2 : 0)}" stroke-width="${o.sw * 0.8}"/>`);
    case 'bow': return both((x, s) => `<path d="M${x - 3.4} ${Y - 5.2} Q${x} ${Y - 7.4} ${x + 3.4} ${Y - 5.2}" stroke-width="${o.sw * 0.6}"/><circle cx="${x}" cy="${Y + 1}" r="2.3" fill="${col}" stroke="none"/>`);
    case 'closed': return both((x) => `<path d="M${x - 3.6} ${Y} Q${x} ${Y + 3} ${x + 3.6} ${Y}"/>`);
    case 'happy': return both((x) => `<path d="M${x - 3.6} ${Y + 1.4} Q${x} ${Y - 3.6} ${x + 3.6} ${Y + 1.4}"/>`);
    case 'narrow': return both((x, s) => `<g transform="rotate(${-s * 5} ${x} ${Y})"><path d="M${x - 3.9} ${Y - 0.3} H${x + 3.9}"/><path d="M${x - 2.4} ${Y - 0.3} A2.4 1.8 0 0 0 ${x + 2.4} ${Y - 0.3} Z" fill="${col}" stroke="none"/></g>`);
    case 'soft': return both((x) => `<path d="M${x - 3.6} ${Y + 0.4} Q${x} ${Y + 2.6} ${x + 3.6} ${Y + 0.4}"/><path d="M${x - 3.6} ${Y - 0.3} Q${x} ${Y - 0.9} ${x + 3.6} ${Y - 0.3}" opacity=".45"/>`);
    case 'brow': return both((x, s) => `<circle cx="${x}" cy="${Y + 4}" r="2.4" fill="${col}" stroke="none"/>`);
    default: return '';
  }
}

function ring(type, col, sw, uid) {
  if (type === 'closed') return `<circle cx="50" cy="50" r="${R}"/>`;
  if (type === 'double') return `<circle cx="50" cy="50" r="${R}"/><circle cx="50" cy="50" r="${R - 3.6}" stroke-width="${sw * 0.42}" opacity=".75"/>`;
  if (type === 'enso') {
    // brush ring: crescent between offset circles, gap at upper right, tapered ends
    const g0 = 28, g1 = 62; // gap in degrees clockwise from top
    const id = `en${uid}`;
    const [ax, ay] = pol(g1, 52), [bx, by] = pol(g0, 52);
    return `<defs><mask id="${id}" maskUnits="userSpaceOnUse" x="-10" y="-10" width="120" height="120">
        <rect x="-10" y="-10" width="120" height="120" fill="#fff"/>
        <path d="M50 50 L${f(bx)} ${f(by)} L${f(ax)} ${f(ay)} Z" fill="#000"/></mask></defs>
      <g mask="url(#${id})"><path fill-rule="evenodd" fill="${col}" stroke="none"
        d="M${50 - R - sw * 0.85} 50 a${R + sw * 0.85} ${R + sw * 0.85} 0 1 0 ${2 * (R + sw * 0.85)} 0 a${R + sw * 0.85} ${R + sw * 0.85} 0 1 0 ${-2 * (R + sw * 0.85)} 0 Z
           M${51.3 - R + sw * 0.35} 48.9 a${R - sw * 0.35} ${R - sw * 0.35} 0 1 0 ${2 * (R - sw * 0.35)} 0 a${R - sw * 0.35} ${R - sw * 0.35} 0 1 0 ${-2 * (R - sw * 0.35)} 0 Z"/></g>
`;
  }
  return '';
}

function tick(type, col, sw) {
  if (type === 'target') return `<path d="M50 4.5 V14.5"/>`;
  if (type === 'hit') return `<path d="M50 6.5 V14.5"/><circle cx="50" cy="4.2" r="${sw * 1.05}" fill="${col}" stroke="none"/>`;
  if (type === 'watch') return `<path d="M50 6 V14.5"/><path d="M46.6 4.6 H53.4"/><path d="${(() => { const [a, b] = pol(42, R + 1.5), [c, d] = pol(42, R + 6.5); return `M${f(a)} ${f(b)} L${f(c)} ${f(d)}`; })()}"/>`;
  if (type === 'orbit') {
    const [a, b] = pol(48, R - 4.5), [c, d] = pol(48, R + 5.5);
    const arcs = [10, 22, 34].map((deg, i) => { const [x, y] = pol(deg, R + 4); return `<circle cx="${f(x)}" cy="${f(y)}" r="${sw * 0.32}" fill="${col}" stroke="none" opacity="${0.25 + i * 0.2}"/>`; }).join('');
    return `${arcs}<path d="M${f(a)} ${f(b)} L${f(c)} ${f(d)}"/>`;
  }
  if (type === 'bow') return `<path d="M50 4.5 V14.5"/>`;
  return '';
}

let uid = 0;
// px: rendered size; sw: stroke in viewBox units
function face(px, o = {}) {
  const id = ++uid;
  const col = o.col || GOLD;
  const sw = o.sw ?? 2.1;
  const pulseD = o.pulseD ?? (o.pulse ? PULSE[o.pulse] : null);
  const fade = o.fade !== false;
  const grad = `<linearGradient id="pg${id}" gradientUnits="userSpaceOnUse" x1="-4" y1="0" x2="104" y2="0">
      <stop offset="0" stop-color="${col}" stop-opacity="0"/><stop offset=".14" stop-color="${col}" stop-opacity="1"/>
      <stop offset=".86" stop-color="${col}" stop-opacity="1"/><stop offset="1" stop-color="${col}" stop-opacity="0"/></linearGradient>`;
  const ft = o.featureT || '';
  return `<svg width="${px}" height="${px}" viewBox="${o.vb || '0 0 100 100'}" style="overflow:visible;display:block">
    <defs>${grad}</defs>
    <g fill="none" stroke="${col}" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round">
      ${ring(o.ring || 'closed', col, sw, id)}
      ${tick(o.tick || 'none', col, sw)}
      <g transform="${ft}">
        ${eyes(o.eyes || 'none', col, { ...(o.eyeOpt || {}), sw })}
        ${o.extra || ''}
        ${pulseD ? `<path d="${pulseD}" stroke="${fade ? `url(#pg${id})` : col}" stroke-width="${sw * (o.pulseW || 0.82)}"/>` : ''}
      </g>
      ${o.after || ''}
    </g></svg>`;
}

// ---------- the five variants ----------
const PROFILE = (() => {
  // circle = back of the head; the face is one pulse line: brow, nose = QRS spike, lips = two small beats
  const [fx, fy] = pol(32), [cx, cy] = pol(146);
  return `M${f(fx)} ${f(fy)} A${R} ${R} 0 1 0 ${f(cx)} ${f(cy)}`
    + ` C${f(cx + 3)} ${f(cy - 3)} 71 77 74.5 73.5 C76 72 76.6 70.5 75.6 68.6`
    + ` L77.4 65 L75 62.6 L77.8 59.6 L75.4 57`
    + ` L84.5 53 L74.6 41.5 L76.4 36.4 C76 30 ${f(fx + 2.5)} ${f(fy + 4)} ${f(fx)} ${f(fy)}`;
})();
const PROFILE_PULSE = 'M84.5 53 H88.5 L91 47.5 L93.6 57.5 L95.6 53 H106';

const portrait = (col) => {
  const half = `
    <path d="M50 81 C44.5 81 39.5 77.5 36.6 71.5 C34.6 67 33.4 61 33 54 C32.8 50.5 33 47.5 33.4 45"/>
    <path d="M33.4 45 C32 37 33.5 29.5 38.5 25.5 C42 22.6 46 21.6 50 21.6"/>
    <path d="M35 33 C37.5 28.5 41.5 26.6 45 27.2 C43 29 42.4 31.4 42.8 33.6" stroke-width="1.05"/>
    <path d="M33 49 C30.4 47.6 28.8 50 29.4 54 C29.9 57.2 31.4 59.6 33.6 60" stroke-width="1.15"/>
    <path d="M37 41.6 C39.6 39.6 43.6 39.4 46.6 41" stroke-width="1.5"/>
    <path d="M38 47 C39.8 45.3 43.4 45.1 45.6 46.8" stroke-width="1.1"/>
    <path d="M38.4 47.2 C40.2 48.6 43.4 48.6 45.4 47.1" stroke-width="0.8" opacity=".75"/>
    <circle cx="42" cy="47" r="1.15" fill="${col}" stroke="none"/>
    <path d="M47.6 45.5 C47.8 50 47.5 54.5 46.2 57.4 C45.7 58.6 46.6 59.6 48 59.6" stroke-width="1.05"/>
    <path d="M43.6 65.4 C46 64.4 48.4 64.4 50 65.2" stroke-width="1.05"/>
    <path d="M44.2 66.1 C46.6 66.7 48.6 66.8 50 66.6" stroke-width="1.25"/>
    <path d="M46.4 69.4 C48 70 49.2 70.1 50 70.1" stroke-width=".9" opacity=".8"/>
    <path d="M38.8 74.6 C39 79 38.8 83 38.2 86.5" stroke-width="1.15"/>`;
  return `<g fill="none" stroke="${col}" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round">
    ${half}<g transform="translate(100 0) scale(-1 1)">${half}</g>
    <path d="M50 21.6 C53 22.4 55.5 24.8 57 27.6" stroke-width="1" opacity=".7"/></g>`;
};

const V = {
  a: (px, o = {}) => face(px, { ring: 'closed', tick: 'target', pulse: 'mark', ...o }),
  b: (px, o = {}) => face(px, { ring: 'closed', tick: 'target', eyes: 'dot', pulse: 'smile', ...o }),
  c: (px, o = {}) => face(px, {
    ring: 'closed', tick: 'none', eyes: 'brow', eyeOpt: { y: 44 }, ...o,
    pulseD: 'M2 44 H31 C35 44 36 40.2 40 40.2 C43 40.2 44 44 46 44 L48 37 L50.5 51 L52.2 44 C55 44 57 40.2 60 40.2 C64 40.2 65 44 69 44 H98',
    after: `<path d="M50 4.5 V13"/><path d="M50 87 V95.5"/><path d="M4.5 50 H13" opacity=".0"/><circle cx="50" cy="50" r="${R - 9}" stroke-width="${(o.sw ?? 2.1) * 0.35}" stroke-dasharray="0.1 4.6" opacity=".6"/>`,
  }),
  d: (px, o = {}) => face(px, {
    ring: 'none', ...o,
    after: `<path d="${PROFILE}"/><path d="${PROFILE_PULSE}" stroke-width="${(o.sw ?? 2.1) * 0.82}"/>
      <path d="M50 4.5 V14.5" transform="rotate(-24 50 50)"/>
      <path d="M66.8 41.4 Q69.6 40 72.4 41.2" stroke-width="${(o.sw ?? 2.1) * 0.7}"/><circle cx="70.2" cy="44.6" r="${(o.sw ?? 2.1) * 0.62}" fill="${o.col || GOLD}" stroke="none"/>`,
  }),
  e: (px, o = {}) => `<svg width="${px}" height="${px}" viewBox="0 0 100 100" style="overflow:visible;display:block">
      <defs><clipPath id="pc${++uid}"><circle cx="50" cy="50" r="${R - 1}"/></clipPath>
      <linearGradient id="pf${uid}" gradientUnits="userSpaceOnUse" x1="-4" y1="0" x2="104" y2="0"><stop offset="0" stop-color="${GOLD}" stop-opacity="0"/><stop offset=".14" stop-color="${GOLD}"/><stop offset=".86" stop-color="${GOLD}"/><stop offset="1" stop-color="${GOLD}" stop-opacity="0"/></linearGradient></defs>
      <circle cx="50" cy="50" r="${R}" fill="none" stroke="${GOLD}" stroke-width="1.6"/>
      <path d="M50 4.5 V14.5" stroke="${GOLD}" stroke-width="1.6" stroke-linecap="round"/>
      <g clip-path="url(#pc${uid})" transform="translate(50 50) scale(.92) translate(-50 -47)">${portrait(GOLD)}</g>
      <path d="M2 85.5 H34 L36.5 81 L39.5 89 L42 85.5 H58 L60.5 81 L63.5 89 L66 85.5 H98" fill="none" stroke="url(#pf${uid})" stroke-width="1.3" stroke-linejoin="round" stroke-linecap="round"/>
    </svg>`,
};

// ---------- page chrome ----------
const css = `
:root{--bg:#0B0E15;--card:#11151E;--line:rgba(237,229,211,.075);--fg:#EDE5D3;--fg2:#A9A292;--fg3:#938C7C;--gold:#D9B562}
*{box-sizing:border-box;margin:0;padding:0}
body{background:var(--bg);color:var(--fg);font-family:'Geist',system-ui,sans-serif;-webkit-font-smoothing:antialiased}
.sheet{padding:56px 64px 60px}
.eyebrow{font-family:'Geist Mono',monospace;font-size:12px;letter-spacing:.14em;text-transform:uppercase;color:var(--gold)}
h1{font-weight:300;font-size:34px;letter-spacing:-.01em;margin-top:10px}
.lead{color:var(--fg2);font-size:15px;line-height:1.5;margin-top:10px;max-width:980px}
.row{display:flex;gap:20px;margin-top:40px}
.card{flex:1;min-width:0;background:var(--card);border:1px solid var(--line);border-radius:24px;padding:28px 24px 26px;display:flex;flex-direction:column;align-items:center;position:relative}
.card.rec{border-color:rgba(217,181,98,.45)}
.badge{position:absolute;top:14px;right:16px;font-family:'Geist Mono';font-size:10.5px;letter-spacing:.12em;text-transform:uppercase;color:#1C1505;background:var(--gold);padding:4px 9px;border-radius:99px}
.art{height:220px;display:flex;align-items:center;justify-content:center}
.minis{display:flex;gap:14px;align-items:flex-end;margin-top:18px;padding-top:16px;border-top:1px solid var(--line);width:100%;justify-content:center}
.minis span{font-family:'Geist Mono';font-size:10px;color:var(--fg3);display:block;text-align:center;margin-top:6px}
.cap{width:100%;margin-top:18px}
.cap .k{font-family:'Geist Mono';font-size:12px;color:var(--gold);letter-spacing:.08em}
.cap .t{font-size:18px;font-weight:500;margin-top:4px}
.cap .d{font-size:13.5px;color:var(--fg2);line-height:1.5;margin-top:8px}
.cap .n{font-size:12.5px;color:var(--fg3);line-height:1.45;margin-top:10px;font-family:'Geist'}
.foot{margin-top:28px;color:var(--fg3);font-size:12.5px;font-family:'Geist Mono';letter-spacing:.02em}
.scale{display:flex;align-items:center;gap:8px;width:100%;margin-top:22px;font-family:'Geist Mono';font-size:10.5px;color:var(--fg3);letter-spacing:.1em;text-transform:uppercase}
.scale i{flex:1;height:1px;background:linear-gradient(90deg,rgba(217,181,98,.6),rgba(217,181,98,.08))}
`;
const page = (body, w, h, extra = '') => `<!doctype html><html lang="ru"><head><meta charset="utf-8"><title>Vince face</title>
<link rel="stylesheet" href="../fonts/local.css"><style>${css}html,body{width:${w}px;height:${h}px;overflow:hidden}${extra}</style></head>
<body>${DEFS}${body}</body></html>`;

const minis = (fn, o = {}) => `<div class="minis">${[[96, 2], [48, 2], [24, 1.5]].map(([s, dp]) => `<div>${fn(s, { sw: dp * 100 / s * (o.k || 1), ...(o.mini || {}) })}<span>${s}</span></div>`).join('')}</div>`;

// ---------- sheet 1: spectrum ----------
function sheetSpectrum() {
  const items = [
    ['а', 'Знак без лица', V.a, 'Круг, метка-цель, пульс насквозь. Чистая геометрия бренда.', 'Благородно, но это логотип, а не собеседник: говорить с ним трудно.'],
    ['б', 'Минимальное лицо', V.b, 'Две точки-глаза, кардиограмма работает как рот: улыбка и один пик.', 'Лицо читается за 0,2 с даже в 24 dp. Риск — смайлик; держим линию тонкой и асимметричной.'],
    ['в', 'Бровь и взгляд', V.c, 'Пульс — линия бровей и горизонт взгляда, пик — между бровей. Метки цели сверху и снизу.', 'Серьёзнее и «умнее» (б), но без рта беднее эмоции; в 24 dp брови сливаются.'],
    ['г', 'Профиль-маска', V.d, 'Круг — затылок, пульс одной линией рисует лоб, нос и губы и уходит вперёд.', 'Самый «авторский» и движущийся вперёд. В профиль не смотрит на человека; в 24 dp не читается.'],
    ['д', 'Портрет в линию', V.e, 'Иллюстрированное лицо в стиле Титана, золото на графите, пульс внизу.', 'Самый человечный — и второй Титан (аудит A58 №8). Есть «зловещая долина» и вопрос: чьё это лицо?'],
  ];
  const body = `<div class="sheet">
    <div class="eyebrow">Health of a Titan · Винс · концепт лица v0.1</div>
    <h1>Спектр: от знака к лицу</h1>
    <div class="lead">Один Винс в пяти степенях человечности. Язык один: круг с меткой-целью и кардиограмма, золото <span style="font-family:'Geist Mono'">#C9A23A</span> 2 dp на графите. Колонн нет — колонны принадлежат Титану. Под каждым вариантом — проверка размеров 96 / 48 / 24 dp.</div>
    <div class="scale">абстрактный<i style="background:linear-gradient(90deg,rgba(217,181,98,.7),rgba(217,181,98,.15))"></i>человечный</div>
    <div class="row" style="margin-top:18px">${items.map(([k, t, fn, d, n]) => `
      <div class="card ${k === 'б' ? 'rec' : ''}">${k === 'б' ? '<div class="badge">рекомендуем</div>' : ''}
        <div class="art">${fn(200)}</div>
        ${minis(fn)}
        <div class="cap"><div class="k">(${k})</div><div class="t">${t}</div><div class="d">${d}</div><div class="n">${n}</div></div>
      </div>`).join('')}</div>
    <div class="foot">Рекомендация: (б) как основа + метка-цель на кромке. (в) — запасной ход для «строгого» тона, (г) — для крупной иллюстрации и заставок, не для аватара.</div>
  </div>`;
  return page(body, 1600, 940);
}

// ---------- recommended (б) building blocks ----------
const VB = (px, o = {}) => face(px, { ring: 'closed', tick: 'target', eyes: 'dot', pulse: 'calm', ...o });
const CH = {
  sensei: (px, o = {}) => face(px, { ring: 'enso', tick: 'none', eyes: 'half', pulse: 'sensei', ...o }),
  bro: (px, o = {}) => face(px, { ring: 'closed', tick: 'target', eyes: 'lively', pulse: 'bro', ...o }),
  butler: (px, o = {}) => face(px, { ring: 'double', tick: 'target', eyes: 'bow', pulseD: BOW, featureT: 'translate(0 1.6)', ...o }),
  care: (px, o = {}) => face(px, { ring: 'closed', tick: 'none', eyes: 'closed', col: IVORY, ...o }),
};

function sheetCharacters() {
  const items = [
    ['Сенсей', CH.sensei, 'Разомкнутый круг-энсо с утолщением мазка. Прикрытые глаза, плоский пульс с одним пиком.', 'тишина · дисциплина · «скажу одно»', 'Жест: пик «дописывается» одним движением.'],
    ['Винс · Бро', CH.bro, 'Замкнутый круг, живые глаза с бровями вразнобой. Пульс-ухмылка с двумя пиками выходит за край.', 'энергия · напор · «вдвоём»', 'Жест: пульс «подмигивает» двойным ударом.'],
    ['Винсент · Дворецкий', CH.butler, 'Двойное кольцо, как фарфоровое блюдо. Взгляд опущен, брови приподняты — лёгкий поклон. Ровный пульс завязан бабочкой.', 'порядок · сервис · достоинство', 'Жест: черты опускаются на 2 dp и возвращаются.'],
    ['Бережный режим', CH.care, 'Один для всех. Круг цвета слоновой кости, мягко закрытые глаза. Без пульса, без метки, без жестов.', 'тут нет характера, тут забота', 'Без анимации (A36 §SOS). Голос — текущего характера, медленнее.'],
  ];
  const body = `<div class="sheet">
    <div class="eyebrow">Health of a Titan · Винс · вариант (б) в трёх характерах</div>
    <h1>Один наставник, три характера и бережный режим</h1>
    <div class="lead">Основа одна: круг, две точки, кардиограмма-рот. Характер меняют три детали — кромка круга, веки и рисунок пульса. Узнаваемость Винса сохраняется при любой смене характера.</div>
    <div class="row">${items.map(([t, fn, d, k, n], i) => `
      <div class="card" ${i === 3 ? 'style="background:#0E1219"' : ''}>
        <div class="art" style="height:260px">${fn(230)}</div>
        ${minis(fn)}
        <div class="cap"><div class="k" ${i === 3 ? 'style="color:#EDE5D3"' : ''}>${k}</div><div class="t">${t}</div><div class="d">${d}</div><div class="n">${n}</div></div>
      </div>`).join('')}</div>
    <div class="foot">Метки момента (тренер, летописец, проводник, секундант) живут на кромке круга поверх любого характера — см. лист эмоций. Свечения у Винса нет: свет режима — только у Титана (D-048).</div>
  </div>`;
  return page(body, 1600, 960);
}

// ---------- sheet 3: emotions ----------
function sheetEmotions() {
  const items = [
    ['Спокоен', (px, o) => VB(px, { ...o }), 'Точки-глаза, лёгкая улыбка, один невысокий пик.', 'по умолчанию'],
    ['Слушает', (px, o) => VB(px, { eyes: 'dot', eyeOpt: { r: 2.9, y: 44 }, pulse: 'listen', ...o }), 'Глаза чуть раскрыты, пульс превращается в волну голоса человека.', 'пока говорит человек'],
    ['Думает', (px, o) => VB(px, { tick: 'orbit', eyeOpt: { dx: 1.8, dy: -2 }, pulse: 'think', extra: [44, 50, 56].map((x, i) => `<circle cx="${x}" cy="62" r="1.25" fill="${GOLD}" stroke="none" opacity="${0.4 + i * 0.3}"/>`).join(''), ...o }), 'Взгляд вверх-вправо, пульс прерван тремя точками, метка идёт по кругу.', 'ждём ответ модели'],
    ['Рад · рубеж взят', (px, o) => VB(px, { tick: 'hit', eyes: 'happy', pulse: 'happy', ...o }), 'Глаза-дуги улыбаются, улыбка шире, пик уверенный, в метку «попали».', 'цель, рекорд, возвращение'],
    ['Бережно · плохая ночь', (px, o) => VB(px, { tick: 'none', eyes: 'soft', pulse: 'gentle', pulseW: 0.7, ...o }), 'Веки опущены, пульс — тихая пологая волна без пиков. Не путать с SOS.', 'утро после плохого сна'],
    ['Тренер · концентрация', (px, o) => VB(px, { tick: 'watch', eyes: 'narrow', pulse: 'coach', ...o }), 'Прищур, взгляд в одну точку, ровные сильные удары, метка-секундомер.', 'тренировка, таймер'],
  ];
  const body = `<div class="sheet">
    <div class="eyebrow">Health of a Titan · Винс · вариант (б) · состояния</div>
    <h1>Шесть состояний — только чертами</h1>
    <div class="lead">Эмоцию несут глаза, рисунок пульса и метка на кромке. Круг не деформируется, нет щёк, рук и мультяшных преувеличений. Переход между состояниями — перерисовка линии за 300–400 мс.</div>
    <div class="row" style="gap:16px">${items.map(([t, fn, d, k]) => `
      <div class="card" style="padding:26px 18px 22px">
        <div class="art" style="height:190px">${fn(170, {})}</div>
        ${minis(fn)}
        <div class="cap"><div class="k">${k}</div><div class="t" style="font-size:16.5px">${t}</div><div class="d" style="font-size:13px">${d}</div></div>
      </div>`).join('')}</div>
    <div class="foot">«Бережно» здесь — тон дня (режим Бережно), Винс остаётся золотым. Бережный режим SOS — отдельный аватар цвета слоновой кости без характера.</div>
  </div>`;
  return page(body, 1600, 820);
}

// ---------- sheet 4: with Titan + header sizes ----------
function titanFrame(theme) {
  const dark = theme === 'dark';
  const line = dark ? GOLD : DGOLD;
  const bg = dark ? 'radial-gradient(circle at 50% 42%,#1B2232,#0B0E15 70%)' : 'radial-gradient(circle at 50% 42%,#FFFFFF,#F5F0E6 70%)';
  const S = 640;
  // big ring = Vince embracing; face medallion is the target mark at 12 o'clock
  const ringR = 292, cx = S / 2, cy = S / 2 + 6;
  const med = 66; // medallion size
  const gap = 9; // deg gap each side of medallion
  const arc = (a0, a1) => { const p = (a) => { const r = (a - 90) * Math.PI / 180; return [cx + ringR * Math.cos(r), cy + ringR * Math.sin(r)]; }; const [x0, y0] = p(a0), [x1, y1] = p(a1); return `M${f(x0)} ${f(y0)} A${ringR} ${ringR} 0 1 1 ${f(x1)} ${f(y1)}`; };
  const pulseY = cy + 8;
  const pulse = `M${cx - ringR - 40} ${pulseY} H${cx - 190} L${cx - 180} ${pulseY - 22} L${cx - 166} ${pulseY + 30} L${cx - 156} ${pulseY} H${cx + 150} L${cx + 162} ${pulseY - 22} L${cx + 176} ${pulseY + 30} L${cx + 186} ${pulseY} H${cx + ringR + 40}`;
  const titan = titanScene(560, {
    light: dark ? ['#F4D48A', '#E4BB72', '#CF9F5A'] : ['#5E4510', '#7A5A12', '#5E4510'],
    colLight: dark ? ['#8D8676', '#6E685B', '#57524A'] : ['#A9A292', '#938C7C', '#857C6B'],
    knock: dark ? 'rgba(11,14,21,.94)' : 'rgba(245,240,230,.96)', glow: false, colOpacity: dark ? .75 : .85,
    spread: 0.86, ring: 0, pulse: 0, crop: '0 0 2048 2048',
  });
  const fg = dark ? '#EDE5D3' : '#161A22', fg2 = dark ? '#A9A292' : '#575144';
  return `<div class="frame" style="background:${bg};color:${fg}">
    <div class="fr-h"><span class="eyebrow" style="color:${dark ? '#D9B562' : DGOLD}">${dark ? 'тёмная тема · «Ночь»' : 'светлая тема · «Мрамор»'}</span></div>
    <div style="position:relative;width:${S}px;height:${S}px;margin:0 auto">
      <svg width="${S}" height="${S}" style="position:absolute;inset:0;overflow:visible">
        <defs><linearGradient id="tp${theme}" gradientUnits="userSpaceOnUse" x1="${cx - ringR - 40}" x2="${cx + ringR + 40}" y1="0" y2="0">
          <stop offset="0" stop-color="${line}" stop-opacity="0"/><stop offset=".12" stop-color="${line}"/><stop offset=".88" stop-color="${line}"/><stop offset="1" stop-color="${line}" stop-opacity="0"/></linearGradient></defs>
        <path d="${arc(gap, 360 - gap)}" fill="none" stroke="${line}" stroke-width="2.4" stroke-linecap="round"/>
        <path d="M${cx} ${cy + ringR - 9} V${cy + ringR + 9}" stroke="${line}" stroke-width="2.4" stroke-linecap="round" opacity=".55"/>
      </svg>
      <div style="position:absolute;left:${(S - 560) / 2}px;top:${(S - 560) / 2 + 26}px">${titan}</div>
      <svg width="${S}" height="${S}" style="position:absolute;inset:0;overflow:visible">
        <path d="${pulse}" fill="none" stroke="url(#tp${theme})" stroke-width="2.2" stroke-linejoin="round" stroke-linecap="round"/>
      </svg>
      <div style="position:absolute;left:${cx - med / 2}px;top:${cy - ringR - med / 2}px">${VB(med, { col: line, tick: 'none', sw: 2.4 * 100 / med })}</div>
    </div>
    <div class="fr-cap" style="color:${fg2}"><b style="color:${fg};font-weight:500">Винс — кольцо, Титан — фигура.</b> Кольцо охватывает Титана, лицо Винса стоит на кромке там, где у цели метка — над головой, как направление. Кардиограмма выходит из кольца и проходит сквозь Титана: Винс слышит его пульс. Колонны остаются Титану.</div>
  </div>`;
}

function header(theme, size) {
  const dark = theme === 'dark';
  const col = dark ? GOLD : DGOLD, fg = dark ? '#EDE5D3' : '#161A22', fg2 = dark ? '#A9A292' : '#575144';
  const bg = dark ? '#0B0E15' : '#F5F0E6', cont = dark ? '#151A24' : '#FAF6EE';
  const dp = size === 24 ? 1.5 : 2;
  const av = VB(size, { col, sw: dp * 100 / size });
  const label = size === 24 ? 'шапка экрана, рядом с датой' : size === 48 ? 'чат: шапка диалога' : 'знакомство, карточка характера';
  let inner;
  if (size === 24) inner = `<div style="display:flex;align-items:center;gap:10px">${av}<span style="font-family:'Geist Mono';font-size:13px;color:${fg2}">сб, 7 ноября</span></div><span style="font-size:13px;color:${fg2}">Сегодня</span>`;
  else if (size === 48) inner = `<div style="display:flex;align-items:center;gap:14px">${av}<div><div style="font-size:17px;font-weight:500;color:${fg}">Винс</div><div style="font-size:12.5px;color:${fg2};margin-top:2px">ИИ-наставник · слушает</div></div></div><span style="font-size:20px;color:${fg2}">⋯</span>`;
  else inner = `<div style="display:flex;align-items:center;gap:20px">${av}<div><div style="font-size:21px;font-weight:400;color:${fg}">Винс</div><div style="font-size:13px;color:${fg2};margin-top:4px;max-width:180px;line-height:1.4">«Будем делать — вместе и без соплей»</div></div></div>`;
  return `<div style="background:${bg};padding:10px;border-radius:20px">
    <div style="font-family:'Geist Mono';font-size:10.5px;letter-spacing:.1em;text-transform:uppercase;color:${fg2};margin:2px 4px 8px">${size} dp · ${label}</div>
    <div style="background:${cont};border-radius:14px;padding:${size === 96 ? 18 : 12}px 16px;display:flex;justify-content:space-between;align-items:center;width:352px">${inner}</div></div>`;
}

function sheetWithTitan() {
  const body = `<div class="sheet">
    <div class="eyebrow">Health of a Titan · Винс и Титан</div>
    <h1>Винс рядом с Титаном</h1>
    <div class="lead">Круг с меткой-целью охватывает и направляет фигуру. Метка цели — это и есть лицо Винса. Пульс проходит сквозь Титана: наставник содержит, пронизывает и ведёт, но не заслоняет.</div>
    <div style="display:flex;gap:24px;margin-top:36px">${titanFrame('dark')}${titanFrame('light')}</div>
    <div class="eyebrow" style="margin-top:40px">Аватар в интерфейсе · 24 / 48 / 96 dp · линия 1,5 / 2 / 2 dp</div>
    <div style="display:grid;grid-template-columns:repeat(3,auto);gap:16px;margin-top:16px;justify-content:start;align-items:start">
      ${[24, 48, 96].map((s) => header('dark', s)).join('')}
      ${[24, 48, 96].map((s) => header('light', s)).join('')}
    </div>
  </div>`;
  const extra = `.frame{flex:1;border-radius:28px;padding:28px 28px 30px;border:1px solid var(--line)}
    .fr-cap{font-size:14px;line-height:1.55;margin-top:14px;max-width:620px}`;
  return page(body, 1600, 1500, extra);
}

(async () => {
  const sheets = [
    ['_faces-spectrum', sheetSpectrum()],
    ['_three-characters', sheetCharacters()],
    ['_emotions', sheetEmotions()],
    ['_with-titan', sheetWithTitan()],
  ];
  const only = process.env.ONLY;
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ deviceScaleFactor: 2, viewport: { width: 1600, height: 900 } });
  for (const [name, html] of sheets) {
    if (only && !name.includes(only)) continue;
    const file = path.join(HTML, `${name}.html`);
    fs.writeFileSync(file, html);
    const p = await ctx.newPage();
    const [, w, h] = html.match(/html,body\{width:(\d+)px;height:(\d+)px/);
    await p.setViewportSize({ width: +w, height: +h });
    await p.goto('file://' + file);
    await p.evaluate(() => document.fonts.ready);
    await p.screenshot({ path: path.join(OUT, `${name}.png`) });
    await p.close();
    console.log('ok', name);
  }
  await browser.close();
})();
