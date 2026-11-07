// Builds the «Звук тренировки» mockups in the «Титаниум» style (A37, D-031) for A43.
// Usage (from repo root):
//   python3 docs/design/workout-sound/src/getfonts.py     # once: Geist + Geist Mono (OFL) into ./fonts
//   node docs/design/workout-sound/src/build.js [outDir]
// titan.js, art.js, pose.js, styles.css, getfonts.py are copied from docs/design/training/src (A40).
// Track and artist names on the mockups are invented; no third-party logos are drawn.
const fs = require('fs');
const path = require('path');
const { ring, icon, picto } = require('./art');
const { DEFS, titanScene } = require('./titan');
const { titanPose, POSES, P } = require('./pose');

const SRC = __dirname;
const OUT = path.resolve(process.argv[2] || path.resolve(SRC, '..'));
const HTML = path.join(SRC, 'html');
fs.mkdirSync(HTML, { recursive: true });
fs.mkdirSync(OUT, { recursive: true });

const AQUA = '#7DE3D1', DAWN = '#FFC77A', SUN = '#FFB067', ALERT = '#FF8A80', LILAC = '#A8A3FF';
const ICE = ['#FFFFFF', '#DFFBF6', '#9FEADC'];

const page = (body, { theme = 'dark', extraCss = '', w = 412, h = 915, wrap = true } = {}) => `<!doctype html>
<html lang="ru"><head><meta charset="utf-8"><title>HoT · Звук тренировки</title>
<link rel="stylesheet" href="../fonts/local.css"><link rel="stylesheet" href="../styles.css">
<style>html,body{width:${w}px;height:${h}px;overflow:hidden}${extraCss}</style></head>
<body>${DEFS}${wrap ? `<div class="screen ${theme}">${body}</div>` : body}</body></html>`;

const rgba = (hex, a) => { const n = parseInt(hex.slice(1), 16); return `rgba(${n >> 16},${(n >> 8) & 255},${n & 255},${a})`; };
const statusBar = (time, extra = '') => `<div class="statusbar"><span class="row" style="gap:10px"><span class="num">${time}</span>${extra}</span><span class="icons">
  <svg width="16" height="12" viewBox="0 0 16 12" fill="currentColor"><path d="M15 1v10H1z"/></svg>${icon('wifi', 17, 2)}
  <svg width="24" height="12" viewBox="0 0 24 12"><rect x=".6" y=".6" width="20.5" height="10.8" rx="3.4" fill="none" stroke="currentColor" stroke-width="1.2" opacity=".5"/><rect x="2.4" y="2.4" width="13.5" height="7.2" rx="1.8" fill="currentColor"/></svg></span></div>`;
const gesture = '<div class="gesture"></div>';
const aura = (col, y = 32, a = .18) => `<div class="abs" style="inset:0;background:radial-gradient(70% 38% at 50% ${y}%, ${rgba(col, a)}, transparent 72%)"></div>`;
const tok = (t, extra = '') => `<span class="tok" style="height:28px;padding:0 11px;font-size:11.5px;white-space:nowrap;flex:none;${extra}">${t}</span>`;
const topBar = (left, right, center = '') => `<div class="abs row" style="left:16px;right:20px;top:50px;height:44px;justify-content:space-between;z-index:5">
  <div class="center" style="width:44px;height:44px;border-radius:22px">${left}</div>${center}<div class="row" style="gap:8px">${right}</div></div>`;
const sosBtn = (top) => `<div class="abs row" style="left:24px;top:${top}px;gap:8px;color:${ALERT}">
  <div class="center" style="width:36px;height:36px;border-radius:18px;box-shadow:inset 0 0 0 1px ${rgba(ALERT, .45)}">${picto('heart', 18, 1.6)}</div>
  <span class="l13" style="color:${ALERT}">Мне плохо</span></div>`;

// local glyphs (same stroke language as art.js)
const G = {
  note: '<path d="M9 18V6l10-2v12"/><circle cx="6.5" cy="18" r="2.5"/><circle cx="16.5" cy="16" r="2.5"/>',
  phones: '<path d="M4 15v-3a8 8 0 0 1 16 0v3"/><rect x="3" y="14" width="4.5" height="6.5" rx="1.6"/><rect x="16.5" y="14" width="4.5" height="6.5" rx="1.6"/>',
  voice: '<path d="M4 10v4M8 7v10M12 4v16M16 7v10M20 10v4"/>',
  next: '<path d="M6 6l9 6-9 6V6Z"/><path d="M18 6v12"/>',
  pause: '<path d="M8 5.5v13M16 5.5v13"/>',
  link: '<path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1"/><path d="M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1"/>',
  mute: '<path d="M4 9.5h3.5L12 5.5v13l-4.5-4H4v-5Z"/><path d="m16 9.5 5 5M21 9.5l-5 5"/>',
  cont: '<path d="M4 12a8 8 0 1 0 2.4-5.7"/><path d="M4 4v4.5h4.5"/>',
  duck: '<path d="M4 9.5h3.5L12 5.5v13l-4.5-4H4v-5Z"/><path d="M16 10a3 3 0 0 1 0 4"/>',
  tracks: '<path d="M4 6h10M4 11h10M4 16h6"/><path d="M17 18V8l4-1"/><circle cx="15.5" cy="18" r="1.8"/>',
};
const gi = (n, s = 22, sw = 1.6) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round">${G[n]}</svg>`;

// HR arc around the Titan (D-034): a partial ring from 7 to 5 o'clock, coloured by zone
function hrArc(D, pct, col, sw = 5) {
  const r = D / 2 - sw, c = D / 2, a0 = 140, span = 260;
  const pt = (a) => [c + r * Math.cos((a * Math.PI) / 180), c + r * Math.sin((a * Math.PI) / 180)];
  const arc = (from, to) => { const [x0, y0] = pt(from), [x1, y1] = pt(to); return `M${x0.toFixed(1)} ${y0.toFixed(1)} A${r} ${r} 0 ${to - from > 180 ? 1 : 0} 1 ${x1.toFixed(1)} ${y1.toFixed(1)}`; };
  const end = a0 + span * pct, [dx, dy] = pt(end);
  const ticks = [0.55, 0.7, 0.85].map((z) => { const a = a0 + span * z, [x, y] = pt(a); const [x2, y2] = [c + (r + 9) * Math.cos(a * Math.PI / 180), c + (r + 9) * Math.sin(a * Math.PI / 180)]; return `<line x1="${x}" y1="${y}" x2="${x2}" y2="${y2}" stroke="rgba(255,255,255,.28)" stroke-width="1.2"/>`; }).join('');
  return `<svg width="${D}" height="${D}" viewBox="0 0 ${D} ${D}" style="overflow:visible">
    <path d="${arc(a0, a0 + span)}" fill="none" stroke="rgba(255,255,255,.08)" stroke-width="${sw}" stroke-linecap="round"/>
    <path d="${arc(a0, end)}" fill="none" stroke="${col}" stroke-width="${sw}" stroke-linecap="round" style="filter:drop-shadow(0 0 6px ${col})"/>
    ${ticks}<circle cx="${dx}" cy="${dy}" r="${sw * .7}" fill="#fff"/></svg>`;
}
// sound wave bars (voice speaking)
const wave = (n, col, h = 26, seed = 3) => `<div class="row" style="gap:3px;height:${h}px">${Array.from({ length: n }, (_, i) => {
  const v = .25 + .75 * Math.abs(Math.sin(i * 1.7 + seed) * Math.cos(i * .6));
  return `<span style="width:3px;height:${Math.round(h * v)}px;border-radius:2px;background:${col};opacity:${.5 + v / 2}"></span>`;
}).join('')}</div>`;

// ------------------------------------------------------------------ w1 · start: one tap starts training + music + voice
function start() {
  const cx = 206, cy = 300, S = 280;
  const body = `${aura(AQUA, 30, .18)}${statusBar('18:38')}
  ${topBar(icon('back', 22, 1.6), `<span class="mono fg2">СИЛА · 45 МИН</span>`)}
  <div class="abs col" style="left:0;right:0;top:100px;align-items:center;gap:2px">
    <span class="mono" style="color:${AQUA}">РОВНО · ЗАРЯД 62</span>
    <span class="t21" style="font-size:26px;line-height:32px;font-weight:300">Сила с гантелями</span></div>
  <div class="abs" style="left:${cx - S / 2}px;top:${cy - S / 2 + 20}px">${hrArc(S, .32, AQUA)}</div>
  <div class="abs" style="left:${cx - 118}px;top:${cy - 98}px">${titanScene(236, { light: ICE, glow: 'rgba(125,227,209,.55)', colLight: ['#DDE6EA', '#9FB0B8', '#6C7A82'], knock: 'rgba(6,14,13,.95)' })}</div>
  <div class="abs row" style="left:0;right:0;top:${cy + 136}px;justify-content:center;gap:8px;align-items:baseline">
    <span style="color:${ALERT}">${picto('heart', 16, 1.6)}</span><span class="t21 num" style="font-weight:300">74</span><span class="mono fg3">WHOOP · ПОДКЛЮЧЁН</span></div>
  <div class="abs glass" style="left:16px;right:16px;top:492px;height:150px;border-radius:28px;padding:16px 18px">
    <div class="row" style="justify-content:space-between"><span class="mono fg2">ЗВУК</span><span class="mono fg3">${gi('phones', 14)} НАУШНИКИ</span></div>
    <div class="row" style="gap:12px;margin-top:14px">
      <div class="center" style="width:48px;height:48px;border-radius:14px;background:linear-gradient(135deg, ${rgba(SUN, .35)}, ${rgba(AQUA, .25)});color:#fff">${gi('note', 24)}</div>
      <div class="col" style="flex:1;gap:2px"><span class="b15">Мой «Железо»</span><span class="mono fg2" style="letter-spacing:.06em">YOUTUBE MUSIC · ФОНОМ</span></div>
      <span class="chip" style="height:30px">Сменить</span></div>
    <div class="row" style="gap:12px;margin-top:14px">
      <div class="center" style="width:48px;height:48px;border-radius:24px;box-shadow:inset 0 0 0 1px ${rgba(AQUA, .5)};color:${AQUA}">${gi('voice', 22)}</div>
      <div class="col" style="flex:1;gap:2px"><span class="b15">Только важное</span><span class="mono fg2" style="letter-spacing:.06em">БЕЗ ПРЕРЫВАНИЙ · ВИБРО + ТОН</span></div>
      <span class="chip" style="height:30px">${icon('dots', 16, 1.6)}</span></div>
  </div>
  <div class="abs row mono fg3" style="left:24px;right:24px;top:664px;justify-content:center;gap:8px;letter-spacing:.06em">${icon('haptic', 14, 1.6)}<span>ТЕЛЕФОН — В КАРМАН · МУЗЫКУ НЕ ТРОНУ</span></div>
  <div class="abs" style="left:24px;right:24px;top:700px"><div class="btn light-pill" style="width:100%;height:64px;border-radius:32px;font-size:18px">${icon('play', 20, 2)} Старт</div></div>
  <div class="abs col" style="left:24px;right:24px;top:786px;gap:6px;align-items:center">
    <span class="mono fg3" style="letter-spacing:.06em">ОДИН ТАП: ТАЙМЕРЫ + МУЗЫКА + СИГНАЛЫ</span></div>
  ${gesture}`;
  return page(body);
}

// ------------------------------------------------------------------ w2 · music picker, one tap
function picker() {
  const opts = [
    { g: 'cont', t: 'Как играет', s: 'продолжаю то, что уже играет', col: '#F3F1EC' },
    { g: 'note', t: 'Мой «Железо»', s: 'твой плейлист по ссылке', col: SUN, on: true, grad: [SUN, AQUA] },
    { g: 'tracks', t: 'Воркаут · Сила', s: 'подборка HoT под тип тренировки', col: DAWN, grad: [DAWN, '#FF9A4D'] },
    { g: 'mute', t: 'Тишина', s: 'только голос и тики', col: '#A3A7AE' },
  ];
  const row = (o, i) => `<div class="abs row ${o.on ? 'glass' : ''}" style="left:16px;right:16px;top:${300 + i * 82}px;height:72px;border-radius:24px;padding:0 16px;gap:14px;${o.on ? `box-shadow:var(--glass-edge),0 0 0 1px ${rgba(SUN, .55)},0 0 40px -14px ${rgba(SUN, .7)}` : 'box-shadow:inset 0 0 0 1px var(--hair)'}">
    <div class="center" style="width:44px;height:44px;border-radius:14px;color:${o.grad ? '#fff' : o.col};${o.grad ? `background:linear-gradient(135deg, ${rgba(o.grad[0], .45)}, ${rgba(o.grad[1], .3)})` : 'box-shadow:inset 0 0 0 1px var(--hair2)'}">${gi(o.g, 22)}</div>
    <div class="col" style="flex:1;gap:2px"><span class="b16">${o.t}</span><span class="b15 fg2" style="font-size:13px;line-height:17px">${o.s}</span></div>
    ${o.on ? `<span class="center" style="width:26px;height:26px;border-radius:13px;background:${SUN};color:#1A1206">${icon('check', 15, 2.6)}</span>` : ''}</div>`;
  const phases = [['РАЗМИНКА', 'Мой «Железо»', AQUA, 1], ['РАБОТА', 'Мой «Железо»', SUN, 1], ['ЗАМИНКА', 'Воркаут · Растяжка', LILAC, 0]];
  const body = `<div class="abs" style="inset:0;background:rgba(0,0,0,.55)"></div>
  <div class="abs" style="left:0;right:0;top:0;height:200px;opacity:.35;filter:blur(2px)">${aura(AQUA, 60, .25)}</div>
  ${statusBar('18:38')}
  <div class="abs" style="left:0;right:0;top:176px;bottom:0;border-radius:36px 36px 0 0;background:#0E1013;box-shadow:inset 0 1px 0 rgba(255,255,255,.1)">
    <div class="abs" style="left:182px;top:10px;width:48px;height:4px;border-radius:2px;background:rgba(255,255,255,.2)"></div></div>
  <div class="abs col" style="left:24px;right:24px;top:206px;gap:4px">
    <span class="d34" style="font-size:30px">Музыка</span>
    <span class="b15 fg2" style="font-size:14px">Один тап. Играет фоном в YouTube Music.</span></div>
  ${opts.map(row).join('')}
  <div class="abs col" style="left:24px;right:24px;top:640px;gap:10px">
    <div class="row" style="justify-content:space-between"><span class="mono fg2">ПО ФАЗАМ</span><span class="mono fg3">АВТО</span></div>
    <div class="row" style="gap:6px">${phases.map(([p, n, c, same]) => `<div class="col" style="flex:1;gap:6px"><span style="height:4px;border-radius:2px;background:${c};box-shadow:0 0 10px ${rgba(c, .6)}"></span>
      <span class="mono" style="color:${c};font-size:10px">${p}</span><span class="l13 ${same ? 'fg2' : ''}" style="font-weight:400;font-size:12px;line-height:15px">${n}</span></div>`).join('')}</div></div>
  <div class="abs row" style="left:16px;right:16px;top:752px;height:56px;border-radius:20px;padding:0 16px;gap:12px;box-shadow:inset 0 0 0 1px var(--hair);border-style:dashed">
    <span class="fg2">${gi('link', 20)}</span><span class="b15 fg2" style="font-size:13.5px;flex:1">Добавить: «Поделиться» → HoT в YouTube Music</span></div>
  <div class="abs row mono fg3" style="left:24px;right:24px;top:832px;justify-content:space-between;letter-spacing:.06em"><span>SPOTIFY · ЭТАП 2</span><span>ЗАПОМНЮ ВЫБОР</span></div>
  ${gesture}`;
  return page(body);
}

// ------------------------------------------------------------------ w3 · set, headphones mode: music untouched, signals instead of voice
function set() {
  const cx = 206, cy = 318, D = 286, R = D / 2;
  const body = `${aura(AQUA, 36, .2)}${statusBar('18:52')}
  ${topBar(icon('close', 22, 1.6), `<span class="glass row" style="height:34px;border-radius:17px;padding:0 12px 0 8px;gap:6px"><span style="color:${SUN}">${picto('heart', 18, 1.6)}</span><span class="num l13" style="font-size:15px">151</span><span class="mono" style="letter-spacing:.06em;color:${SUN}">Z4</span></span>`,
    `<div class="row" style="gap:4px">${[['done', 46], ['on', 120], ['', 40]].map(([s, w]) => `<span style="width:${w}px;height:4px;border-radius:2px;background:${s === 'done' ? 'rgba(125,227,209,.55)' : s === 'on' ? 'linear-gradient(90deg,#7DE3D1 40%,rgba(255,255,255,.14) 40%)' : 'rgba(255,255,255,.14)'}"></span>`).join('')}</div>`)}
  <div class="abs col" style="left:0;right:0;top:104px;align-items:center;gap:2px">
    <span class="mono" style="color:${AQUA}">ПОДХОД 3 / 4 · 12 КГ</span>
    <span class="t21" style="font-size:26px;line-height:32px;font-weight:300">Тяга гантели</span></div>
  <div class="abs" style="left:${cx - R}px;top:${cy - R}px">${hrArc(D, .72, SUN, 5)}</div>
  <div class="abs" style="left:${cx - R + 18}px;top:${cy - R + 18}px">${ring(D - 36, 3, .55, AQUA, { glow: 'rgba(125,227,209,.5)', grad: ['#D6FFF7', '#4FC9B4'] })}</div>
  <div class="abs" style="left:${cx - 110}px;top:${cy - 112}px">${titanPose(220, POSES['ex-db-row'].bottom, { light: ICE, glow: 'rgba(125,227,209,.5)', props: P.all(P.bench(10, 75, 62, 6), P.dumbbell('aN')), vb: '6 10 86 86', floor: false })}</div>
  <div class="abs col" style="left:0;right:0;top:${cy + R - 8}px;align-items:center"><span class="d89 num" style="font-size:72px;line-height:70px">7</span><span class="mono fg2">ИЗ 10 · ТЕМП — ТИКАМИ</span></div>
  <div class="abs glass col" style="left:16px;right:16px;top:552px;border-radius:28px;padding:12px 18px;gap:8px;box-shadow:var(--glass-edge),0 0 0 1px ${rgba(AQUA, .35)}">
    <div class="row" style="justify-content:space-between"><span class="mono" style="color:${AQUA}">ТОЛЬКО ВАЖНОЕ</span><span class="mono fg3">ГОЛОС МОЛЧИТ</span></div>
    <div class="row" style="gap:10px">
      ${[['haptic', '▮ ▮ ▮', 'конец · 3 тика'], ['ripple', '◜◝', 'отдых · тон'], ['heart', '▮▮▮▮', 'выше плана']].map(([g, pat, l], i) => `<div class="col" style="flex:1;gap:6px;align-items:center;padding:8px 0;border-radius:16px;${i === 0 ? `background:${rgba(AQUA, .1)}` : ''}">
        <span style="color:${i === 2 ? SUN : AQUA}">${g === 'heart' ? picto('heart', 18, 1.6) : icon(g, 18, 1.6)}</span>
        <span class="mono" style="font-size:9.5px;letter-spacing:.04em;color:${i === 0 ? '#fff' : 'var(--fg2)'}">${l}</span></div>`).join('')}</div>
    <div class="row" style="gap:8px"><span class="fg3">${gi('voice', 14)}</span><span class="b15 fg2" style="font-size:12.5px">Пульс скажу в паузе между треками · через 0:48</span></div></div>
  <div class="abs glass row" style="left:16px;right:16px;top:716px;height:62px;border-radius:22px;padding:0 10px 0 10px;gap:12px">
    <div class="center" style="width:44px;height:44px;border-radius:12px;background:linear-gradient(135deg, ${rgba(SUN, .35)}, ${rgba(AQUA, .25)});color:#fff">${gi('note', 20)}</div>
    <div class="col" style="flex:1;gap:1px;min-width:0"><span class="b15" style="font-size:14px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">Iron Tide — Northbound</span>
      <span class="mono" style="letter-spacing:.06em;color:${AQUA}">${gi('note', 12)} ИГРАЕТ КАК ЕСТЬ · 100 %</span></div>
    <span class="fg2">${gi('pause', 22, 2)}</span><span class="fg2" style="margin-right:6px">${gi('next', 22, 1.6)}</span></div>
  <div class="abs row" style="left:24px;right:24px;top:792px;gap:12px">
    <div class="btn ghost" style="width:64px">⇄</div>
    <div class="btn light-pill" style="flex:1">${icon('check', 20, 2.2)} Готово</div></div>
  ${sosBtn(860)}
  <div class="abs mono fg3" style="right:24px;top:870px">${gi('phones', 12)} PIXEL BUDS</div>
  ${gesture}`;
  return page(body);
}

// ------------------------------------------------------------------ w4 · lock screen: Live Update + music controls
function lock() {
  const body = `<div class="abs" style="inset:0;background:radial-gradient(80% 50% at 50% 20%, rgba(125,227,209,.10), transparent 70%), #030405"></div>
  ${statusBar('18:55', `<span style="color:${AQUA}" class="row">${gi('voice', 14)}</span>`)}
  <div class="abs col" style="left:0;right:0;top:92px;align-items:center">
    <span class="d89 num" style="font-size:112px;line-height:110px;font-weight:200;color:#E8F7F3">18:55</span>
    <span class="b15 fg2">Пт, 7 ноября</span></div>
  <div class="abs glass" style="left:12px;right:12px;top:300px;border-radius:30px;padding:16px 16px 14px;box-shadow:var(--glass-edge),0 0 0 1px ${rgba(AQUA, .3)}">
    <div class="row" style="gap:12px">
      <div style="flex:none;width:56px;height:56px;position:relative">
        <div class="abs" style="left:0;top:0">${ring(56, 3, .62, AQUA, { glow: 'rgba(125,227,209,.6)', rot: -90 })}</div>
        <div class="abs" style="left:4px;top:4px">${titanScene(48, { light: ICE, glow: 'rgba(125,227,209,.6)', columns: false, knock: 'rgba(6,14,13,.95)' })}</div></div>
      <div class="col" style="flex:1;gap:2px"><div class="row" style="justify-content:space-between"><span class="mono fg2">HOT · ОТДЫХ</span><span class="row" style="gap:6px;color:${AQUA}">${picto('heart', 14, 1.6)}<span class="num l13">108</span></span></div>
        <span class="t21" style="font-size:24px;line-height:28px;font-weight:300"><span class="num">0:41</span> · готов при 105</span>
        <span class="b15 fg2" style="font-size:13px">Дальше: жим гантелей лёжа · 4 × 10</span></div></div>
    <div class="row" style="gap:8px;margin-top:14px">
      <div class="center l13" style="flex:1;height:44px;border-radius:22px;background:rgba(255,255,255,.9);color:#0D0F12">${icon('check', 16, 2.4)}&nbsp;Готов</div>
      <div class="center l13" style="width:84px;height:44px;border-radius:22px;box-shadow:inset 0 0 0 1px var(--hair2)">+15 с</div>
      <div class="center l13" style="width:120px;height:44px;border-radius:22px;color:${ALERT};box-shadow:inset 0 0 0 1px ${rgba(ALERT, .5)}">Мне плохо</div></div></div>
  <div class="abs glass" style="left:12px;right:12px;top:520px;border-radius:30px;padding:16px">
    <div class="row" style="gap:12px">
      <div class="center" style="width:56px;height:56px;border-radius:14px;background:linear-gradient(135deg, ${rgba(SUN, .4)}, ${rgba('#6A4BFF', .35)});color:#fff">${gi('note', 26)}</div>
      <div class="col" style="flex:1;gap:2px"><span class="mono fg3">YOUTUBE MUSIC</span><span class="b16">Iron Tide</span><span class="b15 fg2" style="font-size:13px">Northbound</span></div></div>
    <div class="row" style="margin-top:12px;gap:8px;align-items:center"><span class="mono fg3 num">1:12</span><div style="flex:1;height:4px;border-radius:2px;background:linear-gradient(90deg,#F3F1EC 34%,rgba(255,255,255,.15) 34%)"></div><span class="mono fg3 num">3:31</span></div>
    <div class="row" style="justify-content:center;gap:44px;margin-top:10px;color:#F3F1EC">${icon('back', 22, 1.8)}<span class="center" style="width:52px;height:52px;border-radius:26px;background:rgba(255,255,255,.12)">${gi('pause', 22, 2.2)}</span>${gi('next', 24, 1.8)}</div></div>
  <div class="abs col" style="left:24px;right:24px;top:752px;gap:10px">
    ${[[gi('phones', 16), 'Наушники: пауза, следующий трек — как обычно'], [gi('voice', 16), 'Музыку не прерываю: время и пульс — здесь, без разблокировки']].map(([g, t]) => `<div class="row fg2" style="gap:10px"><span>${g}</span><span class="b15" style="font-size:13.5px">${t}</span></div>`).join('')}</div>
  <div class="abs row" style="left:0;right:0;top:846px;justify-content:space-between;padding:0 40px;color:rgba(255,255,255,.6)">${icon('lock', 20, 1.6)}${icon('dots', 20, 1.6)}</div>
  ${gesture}`;
  return page(body);
}

// ------------------------------------------------------------------ w5 · sound settings: levels and channels («музыка священна»)
function settings() {
  // event × channel matrix for the default level «Только важное»
  const CH = { v: ['haptic', 'вибро'], t: ['ripple', 'тон'], s: ['voice', 'голос'] };
  const rows = [
    ['Старт подхода', ['v'], 0], ['Последние 3 с', ['v', 't'], 0], ['Отдых кончился · готов', ['v', 't'], 0],
    ['Пульс и зона', ['s'], 1, 'в паузе трека'], ['Пульс выше плана', ['v', 's'], 2, 'всегда'],
  ];
  const chip = (k, on, crit) => `<span class="row" style="gap:4px;height:24px;padding:0 8px;border-radius:12px;font-size:11px;font-weight:500;${on ? `background:${rgba(crit ? SUN : AQUA, .14)};color:${crit ? SUN : AQUA}` : 'color:var(--fg3);box-shadow:inset 0 0 0 1px var(--hair)'}">${k === 's' ? gi('voice', 12) : icon(CH[k][0], 12, 1.8)}${CH[k][1]}</span>`;
  const body = `${aura(AQUA, 12, .12)}${statusBar('09:12')}
  ${topBar(icon('back', 22, 1.6), `<span class="mono fg2">ЗВУК ТРЕНИРОВКИ</span>`)}
  <div class="abs col" style="left:24px;right:24px;top:100px;gap:4px"><span class="d34" style="font-size:30px">Музыка священна</span>
    <span class="b15 fg2" style="font-size:14px">Не прерываю и не приглушаю. Говорю, только когда важно.</span></div>
  <div class="abs col" style="left:16px;right:16px;top:186px;gap:8px">
    ${[['mute', 'Тишина', 'только вибро и экран блокировки', 0], ['haptic', 'Только важное', 'вибро + тихие тоны поверх музыки; голос — в паузах и при риске', 1], ['voice', 'Тренер', 'голос на каждый подход поверх музыки; приглушать — по желанию', 0]]
      .map(([g, t, s, on]) => `<div class="row ${on ? 'glass' : ''}" style="height:${on ? 76 : 60}px;border-radius:22px;padding:0 16px;gap:14px;${on ? `box-shadow:var(--glass-edge),0 0 0 1px ${rgba(AQUA, .55)},0 0 36px -14px ${rgba(AQUA, .7)}` : 'box-shadow:inset 0 0 0 1px var(--hair)'}">
        <span style="color:${on ? AQUA : 'var(--fg2)'}">${g === 'haptic' ? icon('haptic', 22, 1.6) : gi(g, 22)}</span>
        <div class="col" style="flex:1;gap:2px"><span class="b16" style="${on ? '' : 'color:var(--fg2)'}">${t}${on ? `<span class="mono" style="margin-left:8px;color:${AQUA}">ПО УМОЛЧАНИЮ</span>` : ''}</span><span class="b15 fg3" style="font-size:12.5px;line-height:16px">${s}</span></div>
        ${on ? `<span class="center" style="width:24px;height:24px;border-radius:12px;background:${AQUA};color:#06201B">${icon('check', 14, 2.6)}</span>` : ''}</div>`).join('')}</div>
  <span class="abs mono fg2" style="left:24px;top:422px">КАК СООБЩАЮ</span>
  <div class="abs col" style="left:16px;right:16px;top:446px;gap:6px">
    ${rows.map(([t, ch, kind, note]) => `<div class="row" style="height:44px;padding:0 8px;gap:8px;border-bottom:1px solid var(--hair)">
      <span class="b15" style="flex:1;font-size:14px;${kind === 2 ? `color:${SUN}` : ''}">${t}${note ? `<span class="mono fg3" style="margin-left:6px;font-size:9.5px;letter-spacing:.04em">${note.toUpperCase()}</span>` : ''}</span>
      ${['v', 't', 's'].map((k) => chip(k, ch.includes(k), kind === 2)).join('')}</div>`).join('')}</div>
  <div class="abs col" style="left:24px;right:24px;top:716px;gap:12px">
    <div class="row" style="gap:12px"><span class="fg2">${gi('voice', 18)}</span><span class="b15" style="flex:1;font-size:14px">Голос</span><span class="chip on" style="height:30px;box-shadow:inset 0 0 0 1px ${rgba(AQUA, .5)}">Сенсей</span></div>
    <div class="row" style="gap:12px"><span class="fg2">${icon('dots', 18, 1.6)}</span><span class="b15" style="flex:1;font-size:14px">«Скажи пульс» — двойной стук по Pixel</span><span class="mono" style="color:${AQUA}">ВКЛ</span></div></div>
  <div class="abs glass row" style="left:16px;right:16px;top:810px;height:56px;border-radius:22px;padding:0 16px;gap:12px;box-shadow:var(--glass-edge),0 0 0 1px ${rgba(ALERT, .3)}">
    <span style="color:${ALERT}">${picto('heart', 18, 1.6)}</span><span class="b15" style="flex:1;font-size:13.5px">«Мне плохо» — музыка на паузу, всегда</span><span class="fg3">${icon('lock', 16, 1.6)}</span></div>
  ${gesture}`;
  return page(body);
}

// ------------------------------------------------------------------ w6 · «Твои треки силы»: local music × results
function tracks() {
  const T = [
    ['Iron Tide', 'Northbound', 14, '+1,4', AQUA, .92], ['Low Orbit', 'Kairo Lane', 11, '+0,9', AQUA, .7],
    ['Glass Hammer', 'Vessel 9', 9, '+0,6', AQUA, .52], ['Slow Coast', 'Ferns', 12, '−0,8', LILAC, .22],
  ];
  const body = `${aura(SUN, 24, .14)}${statusBar('20:04')}
  ${topBar(icon('back', 22, 1.6), `<span class="mono fg2">30 ДНЕЙ · 11 СИЛОВЫХ</span>`)}
  <div class="abs col" style="left:24px;right:24px;top:104px;gap:6px"><span class="d34">Твои треки силы</span>
    <span class="b15 fg2" style="font-size:14px">С ними ты делаешь больше повторов при том же RPE</span></div>
  <div class="abs" style="left:${206 - 100}px;top:196px">${titanScene(200, { light: ['#FFFFFF', '#FFF1D6', '#FFD08A'], glow: 'rgba(255,199,122,.55)', ring: .72, ringCol: DAWN, knock: 'rgba(14,10,6,.95)' })}</div>
  ${T.map(([t, a, n, d, c, w], i) => `<div class="abs row" style="left:16px;right:16px;top:${420 + i * 72}px;height:62px;border-radius:20px;padding:0 14px;gap:12px;${i === 0 ? `background:var(--glass);box-shadow:var(--glass-edge),0 0 0 1px ${rgba(SUN, .4)}` : 'box-shadow:inset 0 0 0 1px var(--hair)'}">
    <span class="mono fg3" style="width:14px">${i + 1}</span>
    <div class="col" style="flex:1;gap:1px"><span class="b15">${t}</span><span class="mono fg3" style="letter-spacing:.06em">${a.toUpperCase()} · ${n} ПОДХ.</span></div>
    <div class="col" style="align-items:flex-end;gap:4px;width:96px"><span class="t21 num" style="font-weight:300;color:${c}">${d}</span>
      <span style="width:${Math.round(80 * w)}px;height:3px;border-radius:2px;background:${c};box-shadow:0 0 8px ${rgba(c, .6)}"></span></div></div>`).join('')}
  <div class="abs row mono fg3" style="left:24px;right:24px;top:712px;justify-content:space-between;letter-spacing:.06em"><span>ПОВТОРОВ К ПЛАНУ · RPE ТОТ ЖЕ</span><span>≥ 8 ПОДХОДОВ</span></div>
  <div class="abs glass row" style="left:16px;right:16px;top:744px;height:60px;border-radius:22px;padding:0 16px;gap:12px">
    <span style="color:${DAWN}">${icon('spark', 20, 1.6)}</span><span class="b15" style="flex:1;font-size:14px">Собрать «Мой Титан · Сила» из топа</span><span class="fg2">${icon('arrow', 18, 1.6)}</span></div>
  <div class="abs row mono fg3" style="left:24px;right:24px;top:826px;gap:8px;letter-spacing:.06em">${icon('lock', 14, 1.6)}<span>ТОЛЬКО НА ТЕЛЕФОНЕ · СВЯЗЬ, НЕ ПРИЧИНА</span></div>
  ${gesture}`;
  return page(body);
}

// ------------------------------------------------------------------ sheet
const SHOTS = [
  ['w1-start', '01 · Старт в один тап', start], ['w2-music-picker', '02 · Музыка: один тап', picker], ['w3-set-signals', '03 · Подход: музыка не тронута', set],
  ['w4-lockscreen', '04 · Экран блокировки', lock], ['w5-sound-settings', '05 · Тишина · Важное · Тренер', settings], ['w6-power-tracks', '06 · Твои треки силы', tracks],
];
function sheet() {
  const w = 6 * 412 + 7 * 48, h = 1420;
  const body = `<div style="width:${w}px;height:${h}px;background:#060708;color:#F3F1EC;font-family:Geist,sans-serif;position:relative;overflow:hidden">
    <div style="position:absolute;inset:0;background:radial-gradient(40% 30% at 50% 0%, rgba(125,227,209,.10), transparent 70%)"></div>
    <div style="position:absolute;left:48px;top:48px" class="col">
      <span class="mono" style="color:#A3A7AE;font-size:13px">HEALTH OF A TITAN · A43 · ЗВУК ТРЕНИРОВКИ</span>
      <span style="font-size:64px;line-height:70px;font-weight:200;letter-spacing:-.04em;margin-top:10px">Музыка священна. Титан говорит, только когда важно.</span></div>
    <div style="position:absolute;left:48px;right:48px;top:200px;display:flex;gap:48px">
      ${SHOTS.map(([f, l]) => `<div class="col" style="gap:14px"><span class="mono" style="color:#A3A7AE;font-size:12px">${l}</span>
        <img src="${path.join(OUT, f + '.png')}" style="width:412px;height:915px;border-radius:36px;box-shadow:0 0 0 1px rgba(255,255,255,.08),0 30px 80px -30px rgba(0,0,0,.8)"></div>`).join('')}
    </div>
    <div style="position:absolute;left:48px;right:48px;top:1196px;display:flex;gap:24px">
      ${[['Музыка', SUN, 'YouTube Music фоном: твой плейлист по ссылке, подборка HoT или «как играет»'], ['Сигналы', AQUA, 'по умолчанию вибро и тихие тоны поверх музыки; без паузы и без приглушения'],
        ['Голос', ALERT, 'в паузе между треками, по двойному стуку или при риске: «пульс выше плана»'], ['Без рук', '#F3F1EC', 'Live Update на экране блокировки, кнопки наушников — музыке'],
        ['Треки силы', DAWN, 'по желанию: что играло × повторы и RPE, только на телефоне']]
        .map(([t, c, d]) => `<div class="glass" style="flex:1;border-radius:28px;padding:22px 24px"><div style="font-size:24px;font-weight:300;color:${c}">${t}</div><div style="font-size:15px;line-height:21px;color:#A3A7AE;margin-top:6px">${d}</div></div>`).join('')}
    </div></div>`;
  return { html: page(body, { w, h, wrap: false }), w, h };
}

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
  for (const [n, , fn] of SHOTS) if (!only || only.includes(n)) await shot(n, fn());
  if (!only || only.includes('_workout-sound')) { const s = sheet(); await shot('_workout-sound', s.html, s.w, s.h, 1); }
  await browser.close();
})();
