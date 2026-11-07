// Builds the training mockups in the «Титаниум» style (A37, D-031) for A40 §10.
// Usage (from repo root):
//   python3 docs/design/training/src/getfonts.py     # once: Geist + Geist Mono (OFL) into ./fonts
//   node docs/design/training/src/build.js [outDir]
// titan.js, art.js, styles.css, getfonts.py are copied from docs/design/concept-v3/src (branch claude/concept-v3);
// pose.js is new: the TitanPose skeleton rig (A40 §6.2).
const fs = require('fs');
const path = require('path');
const { ring, icon, picto } = require('./art');
const { DEFS, titanScene, titanZones } = require('./titan');
const { titanPose, POSES, P } = require('./pose');

const SRC = __dirname;
const OUT = path.resolve(process.argv[2] || path.resolve(SRC, '..'));
const HTML = path.join(SRC, 'html');
fs.mkdirSync(HTML, { recursive: true });
fs.mkdirSync(OUT, { recursive: true });

const AQUA = '#7DE3D1', DAWN = '#FFC77A', SUN = '#FFB067', ALERT = '#FF8A80';
const ICE = ['#FFFFFF', '#DFFBF6', '#9FEADC'];
const GOLD = ['#FFFFFF', '#FFF1D6', '#FFD08A'];
const FIRE = ['#FFF4E6', '#FFC98A', '#FF9A4D'];
const KNOCK = 'rgba(6,14,13,.96)';

const page = (body, { theme = 'dark', extraCss = '', w = 412, h = 915, wrap = true } = {}) => `<!doctype html>
<html lang="ru"><head><meta charset="utf-8"><title>HoT · Тренировки</title>
<link rel="stylesheet" href="../fonts/local.css"><link rel="stylesheet" href="../styles.css">
<style>html,body{width:${w}px;height:${h}px;overflow:hidden}${extraCss}</style></head>
<body>${DEFS}${wrap ? `<div class="screen ${theme}">${body}</div>` : body}</body></html>`;

const statusBar = (time) => `<div class="statusbar"><span class="num">${time}</span><span class="icons">
  <svg width="16" height="12" viewBox="0 0 16 12" fill="currentColor"><path d="M15 1v10H1z"/></svg>${icon('wifi', 17, 2)}
  <svg width="24" height="12" viewBox="0 0 24 12"><rect x=".6" y=".6" width="20.5" height="10.8" rx="3.4" fill="none" stroke="currentColor" stroke-width="1.2" opacity=".5"/><rect x="2.4" y="2.4" width="13.5" height="7.2" rx="1.8" fill="currentColor"/></svg></span></div>`;
const gesture = '<div class="gesture"></div>';
const aura = (col, y = 32, a = .18) => `<div class="abs" style="inset:0;background:radial-gradient(70% 38% at 50% ${y}%, ${col.replace('1)', `${a})`)}, transparent 72%)"></div>`;
const rgba = (hex, a) => { const n = parseInt(hex.slice(1), 16); return `rgba(${n >> 16},${(n >> 8) & 255},${n & 255},${a})`; };
const tok = (t, extra = '') => `<span class="tok" style="height:28px;padding:0 11px;font-size:11.5px;white-space:nowrap;flex:none;${extra}">${t}</span>`;
const sosBtn = (top) => `<div class="abs row" style="left:24px;top:${top}px;gap:8px;color:${ALERT}">
  <div class="center" style="width:36px;height:36px;border-radius:18px;box-shadow:inset 0 0 0 1px ${rgba(ALERT, .45)}">${picto('heart', 18, 1.6)}</div>
  <span class="l13" style="color:${ALERT}">Мне плохо</span></div>`;
const topBar = (left, right, center = '') => `<div class="abs row" style="left:16px;right:20px;top:50px;height:44px;justify-content:space-between;z-index:5">
  <div class="center" style="width:44px;height:44px;border-radius:22px">${left}</div>${center}<div class="row" style="gap:8px">${right}</div></div>`;

// ------------------------------------------------------------------ t1 · level choice
function levels() {
  const L = [
    { id: 'Классика', col: AQUA, light: ICE, pose: POSES['ex-goblet-squat'].bottom, props: P.kettlebell(), vb: '6 8 88 88',
      line: 'функционалка · фитнес · гимнастика · Zone 2', state: 'open', note: 'RPE ≤ 8 · без отказа' },
    { id: 'Продвинутый', col: DAWN, light: GOLD, pose: POSES['ex-kb-swing'].float, props: P.kettlebell('aN', 0, 1), vb: '6 4 88 92',
      line: 'сила с периодизацией · кроссфит · ВИИТ · калистеника', state: 'current', note: 'ты здесь · 9-я неделя' },
    { id: 'Экстрим', col: SUN, light: FIRE, pose: POSES['ex-front-lever-tuck'].tuck, props: P.bar(10), vb: '4 0 92 70', floor: false,
      line: 'навыки калистеники · гимнастическая сила · спринты · чиппер', state: 'locked', note: 'тесты 3 / 5' },
  ];
  const card = (l, i) => {
    const top = 196 + i * 190, on = l.state === 'current';
    return `<div class="abs glass" style="left:16px;right:16px;top:${top}px;height:176px;border-radius:28px;${on ? `box-shadow:var(--glass-edge),0 0 0 1px ${rgba(l.col, .55)},0 0 44px -12px ${rgba(l.col, .6)}` : ''}">
      <div class="abs" style="left:10px;top:20px;width:140px;height:140px;border-radius:70px;background:radial-gradient(circle, ${rgba(l.col, on ? .16 : .08)}, transparent 70%)"></div>
      <div class="abs" style="left:14px;top:22px;opacity:${l.state === 'locked' ? .55 : 1}">${titanPose(132, l.pose, { light: l.light, glow: on ? rgba(l.col, .7) : (l.state === 'locked' ? false : rgba(l.col, .4)), props: l.props, vb: l.vb, floor: l.floor ?? 92 })}</div>
      <div class="abs col" style="left:164px;right:18px;top:22px;gap:8px">
        <div class="row" style="justify-content:space-between"><span class="t21" style="font-size:24px;line-height:28px;font-weight:300">${l.id}</span>
          ${l.state === 'locked' ? `<span class="fg2">${icon('lock', 20, 1.6)}</span>` : on ? `<span class="center" style="width:24px;height:24px;border-radius:12px;background:${l.col};color:#1A1206">${icon('check', 14, 2.6)}</span>` : ''}</div>
        <span class="b15 fg2" style="font-size:14px;line-height:19px">${l.line}</span>
        <div class="row" style="gap:6px;margin-top:4px">${l.state === 'locked'
          ? `<div class="row" style="gap:4px">${[1, 1, 1, 0, 0].map((d) => `<span style="width:16px;height:4px;border-radius:2px;background:${d ? l.col : 'rgba(255,255,255,.14)'}"></span>`).join('')}</div>`
          : `<span style="width:8px;height:8px;border-radius:4px;background:${l.col}"></span>`}
          <span class="mono" style="color:${l.state === 'locked' ? 'var(--fg2)' : l.col};letter-spacing:.08em">${l.note}</span></div>
      </div></div>`;
  };
  const body = `${aura('rgba(255,199,122,1)', 40, .10)}${statusBar('07:42')}
  ${topBar(icon('back', 22, 1.6), `<span class="mono fg2">ТРЕНИРОВКИ</span>`)}
  <div class="abs col" style="left:24px;right:24px;top:104px;gap:6px"><span class="d34">Твой уровень</span>
    <span class="b15 fg2" style="font-size:14px">Уровень — что умеешь. Режим дня — что можно сегодня.</span></div>
  ${L.map(card).join('')}
  <div class="abs glass row" style="left:16px;right:16px;top:774px;height:56px;border-radius:28px;padding:0 18px;gap:12px">
    <span style="color:${AQUA}">${picto('body', 24, 1.5)}</span>
    <span class="b15" style="font-size:14px">Мобильность и растяжка — в каждой тренировке</span></div>
  <div class="abs row" style="left:24px;right:24px;top:846px;justify-content:space-between">
    <span class="mono fg3">Тест Титана · через 12 дней</span><span class="mono" style="color:${SUN}">Пройти сейчас ›</span></div>
  ${gesture}`;
  return page(body);
}

// ------------------------------------------------------------------ t2 · exercise card
function exercise() {
  const ps = POSES['ex-pullup'];
  const frames = [['hang', 'ВИС'], ['mid', '90°'], ['top', 'ВЕРХ']];
  const body = `${aura('rgba(255,199,122,1)', 22, .14)}${statusBar('18:20')}
  ${topBar(icon('back', 22, 1.6), `<span class="center" style="width:40px;height:40px;border-radius:20px;box-shadow:inset 0 0 0 1px var(--hair2)">${icon('play', 18, 1.8)}</span>`)}
  <div class="abs col" style="left:24px;right:24px;top:100px;gap:8px"><span class="d34">Подтягивания</span>
    <div class="row" style="gap:6px">${tok('ПРОДВИНУТЫЙ', `color:${DAWN}`)}${tok('ТЯГА ВЕРТ.')}${tok('ПЕРЕКЛАДИНА')}</div></div>
  <div class="abs glass" style="left:16px;right:16px;top:194px;height:262px;border-radius:28px;overflow:hidden">
    <div class="abs row" style="left:6px;right:6px;top:10px;justify-content:space-between">
      ${frames.map(([k, l], i) => `<div class="col" style="align-items:center;width:124px">
        ${titanPose(124, ps[k], { light: GOLD, glow: i === 2 ? 'rgba(255,199,122,.75)' : 'rgba(255,199,122,.35)', props: P.bar(9), vb: '4 2 92 106', floor: false, h: 142 })}
        <span class="mono" style="margin-top:4px;color:${i === 2 ? DAWN : 'var(--fg2)'}">${l}</span></div>`).join('')}
    </div>
    <div class="abs row" style="left:22px;right:22px;top:212px;gap:4px;height:6px">
      ${[[1, 'вверх 1 с'], [0, ''], [1, 'вниз 1 с'], [2, 'пауза 2 с']].map(([f], i) => `<div style="flex:${[1, .25, 1, 2][i]};height:4px;border-radius:2px;background:${i === 0 ? DAWN : i === 1 ? 'rgba(255,255,255,.1)' : 'rgba(255,255,255,.22)'}"></div>`).join('')}</div>
    <div class="abs row mono fg3" style="left:22px;right:22px;top:228px;justify-content:space-between;letter-spacing:.06em"><span style="color:${DAWN}">ТЕМП 1-0-1-2</span><span>4 × 4–8 · ОТДЫХ 2:00 · RPE 7–8</span></div>
  </div>
  <div class="abs col" style="left:24px;right:24px;top:478px;gap:12px">
    <span class="mono fg2">КАК ДЕЛАТЬ</span>
    ${['Вис хватом чуть шире плеч, лопатки собраны.', 'Тяни локти к рёбрам, пока подбородок не пройдёт перекладину.', 'Опускайся 2–3 с до прямых рук, без раскачки.'].map((t, i) => `<div class="row" style="gap:12px;align-items:flex-start">
      <span class="center num" style="flex:none;width:24px;height:24px;border-radius:12px;box-shadow:inset 0 0 0 1px var(--hair2);font-size:12px">${i + 1}</span><span class="b15">${t}</span></div>`).join('')}
  </div>
  <div class="abs row" style="left:24px;right:24px;top:652px;gap:8px">
    ${['× Раскачка ногами', '× Неполная амплитуда'].map((t) => `<span class="chip" style="white-space:nowrap;color:${ALERT};box-shadow:inset 0 0 0 1px ${rgba(ALERT, .35)}">${t}</span>`).join('')}
  </div>
  <div class="abs row mono" style="left:24px;right:24px;top:700px;gap:8px;letter-spacing:.04em;color:${DAWN};font-size:10.5px">ГОЛОС: «ГРУДЬ К ПЕРЕКЛАДИНЕ» · «ЛОКТИ ВНИЗ»</div>
  <div class="abs glass row" style="left:16px;right:16px;top:732px;height:76px;border-radius:24px;padding:0 6px">
    ${[['↓ проще', 'Австралийские'], ['⇄ иначе', 'Верхний блок'], ['↑ сложнее', 'Выход силой']].map(([s, t], i) => `<div class="col" style="flex:1;align-items:center;gap:4px;${i ? 'border-left:1px solid var(--hair)' : ''}"><span class="mono fg3">${s}</span><span class="b15" style="font-size:14px">${t}</span></div>`).join('')}
  </div>
  <div class="abs row mono fg3" style="left:24px;right:24px;top:832px;gap:8px;letter-spacing:.06em">${icon('lock', 14, 1.6)}<span>ПРОПУСКАЕМ ПРИ БОЛИ В ПЛЕЧЕ ИЛИ ЛОКТЕ</span></div>
  ${gesture}`;
  return page(body);
}

// ------------------------------------------------------------------ t3 · player during a set
function playerSet() {
  const cx = 206, cy = 352, D = 300, R = D / 2;
  const phases = [['ВНИЗ', 3], ['', 0], ['ВВЕРХ', 1], ['', 0]];
  const body = `${aura('rgba(125,227,209,1)', 38, .2)}${statusBar('18:42')}
  ${topBar(icon('close', 22, 1.6), `<span class="glass row" style="height:34px;border-radius:17px;padding:0 12px 0 8px;gap:6px"><span style="color:${ALERT}">${picto('heart', 18, 1.6)}</span><span class="num l13" style="font-size:15px">132</span><span class="mono fg2" style="letter-spacing:.06em">Z3</span></span>`,
    `<div class="row" style="gap:4px">${[['done', 46], ['on', 120], ['', 40]].map(([s, w]) => `<span style="width:${w}px;height:4px;border-radius:2px;background:${s === 'done' ? 'rgba(125,227,209,.55)' : s === 'on' ? 'linear-gradient(90deg,#7DE3D1 55%,rgba(255,255,255,.14) 55%)' : 'rgba(255,255,255,.14)'}"></span>`).join('')}</div>`)}
  <div class="abs col" style="left:0;right:0;top:108px;align-items:center;gap:4px">
    <span class="mono" style="color:${AQUA}">СУПЕРСЕТ A · ПОДХОД 2 / 3</span>
    <span class="t21" style="font-size:26px;line-height:32px;font-weight:300">Гоблет-присед</span></div>
  <div class="abs" style="left:${cx - R}px;top:${cy - R}px">${ring(D, 4, .6, AQUA, { glow: 'rgba(125,227,209,.55)', grad: ['#D6FFF7', '#4FC9B4'], dot: .6, dotCol: '#fff' })}</div>
  <div class="abs" style="left:${cx - 120}px;top:${cy - 128}px">${titanPose(240, POSES['ex-goblet-squat'].bottom, { light: ICE, ghost: POSES['ex-goblet-squat'].stand, props: P.kettlebell(), floor: 92 })}</div>
  <div class="abs row" style="left:${cx - 110}px;top:${cy + R + 14}px;width:220px;gap:6px">
    ${phases.map(([l, s], i) => `<div class="col" style="flex:${s || .25};gap:6px;align-items:center"><span style="width:100%;height:4px;border-radius:2px;background:${i === 0 ? AQUA : 'rgba(255,255,255,.2)'}"></span>${l ? `<span class="mono" style="font-size:9.5px;white-space:nowrap;${i === 0 ? `color:${AQUA}` : 'color:var(--fg3)'}">${l} ${s} С</span>` : ''}</div>`).join('')}</div>
  <div class="abs row" style="left:24px;right:24px;top:556px;justify-content:space-between;align-items:center">
    <div class="col" style="align-items:center;width:170px">
      <span class="fg3" style="height:16px">${icon('back', 16, 1.6).replace('<svg', '<svg style="transform:rotate(90deg)"')}</span>
      <span class="d89 num" style="font-size:96px;line-height:92px">10</span>
      <span class="fg3" style="height:16px">${icon('back', 16, 1.6).replace('<svg', '<svg style="transform:rotate(-90deg)"')}</span>
      <span class="mono fg2" style="margin-top:6px">ПОВТОРОВ · ПЛАН 8–12</span></div>
    <div class="vline" style="height:120px"></div>
    <div class="col" style="align-items:center;width:170px">
      <div class="row" style="gap:10px;align-items:center"><span class="fg3">${icon('back', 16, 1.6)}</span><span class="d55 num">16</span><span class="fg3">${icon('arrow', 16, 1.6)}</span></div>
      <span class="mono fg2" style="margin-top:8px">КГ · ШАГ 2</span></div>
  </div>
  <div class="abs row mono fg3" style="left:0;right:0;top:730px;justify-content:center;gap:8px;letter-spacing:.06em">${icon('haptic', 14, 1.6)}<span>СВАЙП ПО ЧИСЛУ ±1 · ТАП «ГОТОВО» — КАК В ПЛАНЕ</span></div>
  <div class="abs row" style="left:24px;right:24px;top:764px;gap:12px">
    <div class="btn ghost" style="width:64px">⇄</div>
    <div class="btn light-pill" style="flex:1">${icon('check', 20, 2.2)} Готово</div></div>
  ${sosBtn(840)}
  <div class="abs mono fg3" style="right:24px;top:850px">ЗАРЯД 62 · РОВНО</div>
  ${gesture}`;
  return page(body);
}

// ------------------------------------------------------------------ t4 · rest
function rest() {
  const cx = 206, cy = 330, D = 296, R = D / 2;
  const hr = [141, 139, 134, 128, 123, 118, 114, 110, 107, 104];
  const sx = (i) => 30 + i * 30, sy = (v) => 600 - (v - 100) * 1.6;
  const spark = hr.map((v, i) => `${i ? 'L' : 'M'}${sx(i)} ${sy(v)}`).join(' ');
  const breath = [0, 1, 2].map((k) => `<div class="abs" style="left:${cx - 112 + k * 14}px;top:${cy - 112 + k * 14}px;width:${224 - k * 28}px;height:${224 - k * 28}px;border-radius:50%;box-shadow:inset 0 0 0 1px rgba(125,227,209,${.10 + k * .08}), 0 0 ${30 - k * 8}px rgba(125,227,209,${.10 + k * .05})"></div>`).join('');
  const body = `<div class="abs" style="inset:0;background:radial-gradient(60% 34% at 50% 36%, rgba(125,227,209,.16), transparent 72%), linear-gradient(180deg, transparent 60%, rgba(168,163,255,.06))"></div>${statusBar('18:44')}
  ${topBar(icon('close', 22, 1.6), `<span class="mono fg2">ОТДЫХ</span>`)}
  <div class="abs" style="left:${cx - R}px;top:${cy - R}px">${ring(D, 4, .62, AQUA, { glow: 'rgba(125,227,209,.5)', grad: ['#D6FFF7', '#4FC9B4'], rot: -90 })}</div>
  ${breath}
  <div class="abs col" style="left:0;right:0;top:${cy - 64}px;align-items:center;gap:6px">
    <span class="d89 num">0:54</span>
    <span class="mono" style="color:${AQUA}">ВДОХ 4 · ВЫДОХ 6</span></div>
  <div class="abs col" style="left:0;right:0;top:${cy + R + 18}px;align-items:center;gap:2px">
    <div class="row" style="gap:8px;align-items:baseline"><span style="color:${ALERT}">${picto('heart', 18, 1.6)}</span><span class="t21 num" style="font-weight:300">141 → <b style="font-weight:500">104</b></span></div>
    <span class="mono fg3">WHOOP · HR BROADCAST</span></div>
  <div class="abs" style="left:24px;right:24px;top:540px">
    <svg width="364" height="80" viewBox="0 540 364 80" style="overflow:visible">
      <line x1="0" y1="${sy(105)}" x2="364" y2="${sy(105)}" stroke="rgba(125,227,209,.45)" stroke-dasharray="3 4"/>
      <path d="${spark}" fill="none" stroke="${AQUA}" stroke-width="2" stroke-linecap="round" style="filter:drop-shadow(0 0 4px rgba(125,227,209,.6))"/>
      <circle cx="${sx(9)}" cy="${sy(104)}" r="4" fill="#fff"/>
      <text x="364" y="${sy(105) - 8}" fill="#A3A7AE" font-family="Geist Mono" font-size="10" text-anchor="end" letter-spacing="1">ГОТОВ ПРИ 105</text></svg></div>
  <div class="abs row" style="left:24px;right:24px;top:640px;gap:12px">
    <div class="btn ghost" style="width:110px">+15 с</div>
    <div class="btn light-pill" style="flex:1">Готов</div></div>
  <div class="abs glass row" style="left:16px;right:16px;top:722px;height:108px;border-radius:28px;padding:0 16px 0 6px;gap:6px">
    <div style="flex:none">${titanPose(96, POSES['ex-db-row'].bottom, { light: ICE, glow: 'rgba(125,227,209,.4)', props: P.all(P.bench(10, 75, 62, 6), P.dumbbell('aN')), vb: '6 10 86 86', floor: false })}</div>
    <div class="col" style="gap:4px"><span class="mono fg2">ДАЛЬШЕ · ПАРА A</span><span class="t21" style="font-size:19px">Тяга гантели в наклоне</span>
      <span class="b15 fg2" style="font-size:13px">10 на руку · 12 кг · «локоть в задний карман»</span></div></div>
  ${sosBtn(842)}
  ${gesture}`;
  return page(body);
}

// ------------------------------------------------------------------ t5 · summary with the Seal
function summary() {
  const cy = 262, D = 250, R = D / 2, S = Math.round(R * .92 * 2048 / 940);
  const rays = `<div class="abs" style="left:${206 - 230}px;top:${cy - 230}px;width:460px;height:460px;border-radius:50%;
     background:repeating-conic-gradient(from 0deg, rgba(125,227,209,.11) 0deg 1deg, transparent 1deg 7.5deg);
     -webkit-mask:radial-gradient(circle, transparent 48%, #000 54%, transparent 72%);mask:radial-gradient(circle, transparent 48%, #000 54%, transparent 72%)"></div>`;
  const zones = [
    { rects: [{ x0: 700, y0: 1230, x1: 1350, y1: 1840 }], lit: 1, glow: 'rgba(125,227,209,.8)', color: '#BFFBF0' },
    { rects: [{ x0: 760, y0: 760, x1: 1290, y1: 1140 }], lit: .85, glow: 'rgba(125,227,209,.7)', color: '#BFFBF0' },
    { rects: [{ x0: 840, y0: 1080, x1: 1210, y1: 1260 }], lit: .6, glow: 'rgba(125,227,209,.6)', color: '#BFFBF0' },
  ];
  const body = `<div class="abs" style="inset:0;background:radial-gradient(70% 36% at 50% 28%, rgba(125,227,209,.20), transparent 70%)"></div>${statusBar('19:31')}
  ${topBar(icon('close', 22, 1.6), `<span class="mono fg2">18:42–19:29</span>`, `<div class="glass row" style="height:36px;border-radius:18px;padding:0 14px 0 6px;gap:8px">
      <div class="center" style="width:24px;height:24px;border-radius:12px;background:${AQUA};color:#06201B">${icon('check', 14, 2.6)}</div><span class="mono">WHOOP</span></div>`)}
  ${rays}
  <div class="abs" style="left:${206 - R}px;top:${cy - R}px">${ring(D, 5, 1, AQUA, { glow: 'rgba(125,227,209,.6)', grad: ['#D6FFF7', '#4FC9B4'] })}</div>
  <div class="abs" style="left:${206 - S / 2}px;top:${cy - S / 2}px">${titanZones(S, zones, { base: 'rgba(255,255,255,.16)', knock: 'rgba(6,14,13,.93)' })}</div>
  <div class="abs col" style="left:0;right:0;top:${cy + R + 16}px;align-items:center">
    <div class="row" style="align-items:baseline;gap:6px"><span class="d89 num" style="font-size:72px;line-height:72px">47</span><span class="t21 fg2">мин</span></div>
    <span class="mono fg2" style="margin-top:4px">СИЛА С ГАНТЕЛЯМИ · ОБЛЕГЧЁННАЯ</span></div>
  <div class="abs row" style="left:24px;right:24px;top:510px;justify-content:space-between">
    ${[['heart', '128'], ['ceiling', '11,2'], ['zones', '14 подх.']].map(([p, v]) => `<div class="row" style="width:112px;justify-content:center;gap:8px"><span class="fg2">${picto(p, 20, 1.5)}</span><span class="t21 num" style="font-weight:300">${v}</span></div>`).join('')}</div>
  <div class="abs row" style="left:24px;right:24px;top:560px;justify-content:center;gap:8px">
    ${tok(`<span style="color:${AQUA}">●</span> НОГИ`)}${tok(`<span style="color:${AQUA};opacity:.8">●</span> СПИНА`)}${tok(`<span style="color:${AQUA};opacity:.6">●</span> КОР`)}${tok(`${icon('check', 12, 2.4)} РАСТЯЖКА`, `color:${AQUA}`)}</div>
  <div class="abs row" style="left:24px;right:24px;top:614px;justify-content:center;gap:12px">
    ${[['easy', 'Легко'], ['steady', 'Рабоче'], ['heavy', 'Тяжело']].map(([p, l], i) => `<div class="col" style="align-items:center;gap:8px;width:86px">
      <div class="center" style="width:60px;height:60px;border-radius:30px;${i === 1 ? 'background:rgba(125,227,209,.14);box-shadow:inset 0 0 0 1px rgba(125,227,209,.6);color:#fff' : 'box-shadow:inset 0 0 0 1px var(--hair2);color:var(--fg2)'}">${picto(p, 30, 1.4)}</div>
      <span class="l13 ${i === 1 ? '' : 'fg3'}" style="font-weight:400">${l}${i === 1 ? ' · 7' : ''}</span></div>`).join('')}</div>
  <div class="abs glass row" style="left:16px;right:16px;top:728px;height:64px;border-radius:24px;padding:0 16px;gap:12px">
    <span style="color:${DAWN}">${icon('spark', 20, 1.6)}</span>
    <div class="col"><span class="b15" style="font-size:14px">Гоблет-присед: все подходы по 12</span><span class="mono fg2" style="letter-spacing:.06em">В СЛЕДУЮЩИЙ РАЗ — 18 КГ</span></div></div>
  <div class="abs" style="left:24px;right:24px;top:818px"><div class="btn light-pill" style="width:100%">Готово</div></div>
  ${gesture}`;
  return page(body);
}

// ------------------------------------------------------------------ t6 · fitness test
function test() {
  const cx = 206, cy = 330, D = 270, R = D / 2;
  const marks = [[0, 'Классика'], [15, 'Продвинутый'], [30, 'Экстрим']];
  const X = (v) => 24 + (v / 36) * 364;
  const body = `${aura('rgba(255,176,103,1)', 34, .16)}${statusBar('10:06')}
  ${topBar(icon('close', 22, 1.6), `<span class="mono" style="color:${SUN}">ТЕСТ ТИТАНА · 2 / 5</span>`)}
  <div class="abs col" style="left:0;right:0;top:104px;align-items:center;gap:4px">
    <span class="t21" style="font-size:26px;line-height:32px;font-weight:300">Отжимания подряд</span>
    <span class="b15 fg2" style="font-size:14px">Стоп, когда тело перестаёт быть прямой линией</span></div>
  <div class="abs" style="left:${cx - R}px;top:${cy - R + 20}px">${ring(D, 3, 17 / 30, SUN, { glow: 'rgba(255,176,103,.5)', grad: ['#FFE6B8', '#FF9A4D'], dot: .5, dotCol: '#fff' })}</div>
  <div class="abs" style="left:${cx - 120}px;top:${cy - 96}px">${titanPose(240, POSES['ex-pushup'].bottom, { light: FIRE, glow: 'rgba(255,176,103,.6)', ghost: POSES['ex-pushup'].top, floor: 92 })}</div>
  <div class="abs col" style="left:0;right:0;top:${cy - 104}px;align-items:center"><span class="d89 num" style="font-size:96px;line-height:96px">17</span></div>
  <div class="abs row mono fg3" style="left:0;right:0;top:${cy + R + 34}px;justify-content:center;gap:8px;letter-spacing:.06em">${icon('haptic', 14, 1.6)}<span>ТАП ПО ЭКРАНУ — ПОВТОР · ГОЛОС СЧИТАЕТ</span></div>
  <div class="abs" style="left:0;right:0;top:560px">
    <div class="abs" style="left:24px;right:24px;top:30px;height:4px;border-radius:2px;background:rgba(255,255,255,.1)"></div>
    <div class="abs" style="left:24px;width:${X(17) - 24}px;top:30px;height:4px;border-radius:2px;background:linear-gradient(90deg,#FFE6B8,#FF9A4D);box-shadow:0 0 12px rgba(255,176,103,.6)"></div>
    ${marks.map(([v, l]) => `<div class="abs" style="left:${X(v) - 1}px;top:22px;width:2px;height:20px;background:rgba(255,255,255,.35)"></div>
      <div class="abs mono fg3" style="left:${X(v) - 20}px;width:40px;top:0;text-align:center">${v}</div>
      <div class="abs l13 ${v === 15 ? '' : 'fg2'}" style="left:${v === 0 ? 24 : X(v) - 60}px;width:120px;top:52px;text-align:${v === 0 ? 'left' : 'center'};font-weight:400">${l}</div>`).join('')}
    <div class="abs center" style="left:${X(17) - 9}px;top:23px;width:18px;height:18px;border-radius:9px;background:#fff;box-shadow:0 0 14px rgba(255,176,103,.9)"></div>
  </div>
  <div class="abs glass col" style="left:16px;right:16px;top:660px;height:92px;border-radius:24px;padding:16px 18px;gap:8px">
    <div class="row" style="justify-content:space-between"><span class="mono fg2">ПУТЬ К ЭКСТРИМУ</span><span class="mono" style="color:${SUN}">3 / 5</span></div>
    <div class="row" style="gap:6px">${[['Отжим.', 1], ['Присед', 1], ['Планка', 1], ['Подтяг.', 0], ['Наклон', 0]].map(([l, d]) => `<span class="tok" style="flex:1;justify-content:center;white-space:nowrap;padding:0 4px;font-size:10px;${d ? `color:${SUN};box-shadow:inset 0 0 0 1px rgba(255,176,103,.4)` : ''}">${l}</span>`).join('')}</div></div>
  <div class="abs row" style="left:24px;right:24px;top:772px"><div class="btn light-pill" style="width:100%">Закончить подход</div></div>
  ${sosBtn(846)}
  ${gesture}`;
  return page(body);
}

// ------------------------------------------------------------------ sheet
const SHOTS = [
  ['t1-levels', '01 · Уровень', levels], ['t2-exercise', '02 · Упражнение', exercise], ['t3-player-set', '03 · Подход', playerSet],
  ['t4-rest', '04 · Отдых', rest], ['t5-summary-seal', '05 · Итог и «Печать»', summary], ['t6-test', '06 · Тест Титана', test],
];
function sheet() {
  const w = 6 * 412 + 7 * 48, h = 1420;
  const body = `<div style="width:${w}px;height:${h}px;background:#060708;color:#F3F1EC;font-family:Geist,sans-serif;position:relative;overflow:hidden">
    <div style="position:absolute;inset:0;background:radial-gradient(40% 30% at 50% 0%, rgba(255,199,122,.10), transparent 70%)"></div>
    <div style="position:absolute;left:48px;top:48px" class="col">
      <span class="mono" style="color:#A3A7AE;font-size:13px">HEALTH OF A TITAN · A40 · ТРЕНИРОВКИ</span>
      <span style="font-size:64px;line-height:70px;font-weight:200;letter-spacing:-.04em;margin-top:10px">Три уровня. Мобильность везде. Титан показывает.</span></div>
    <div style="position:absolute;left:48px;right:48px;top:200px;display:flex;gap:48px">
      ${SHOTS.map(([f, l]) => `<div class="col" style="gap:14px"><span class="mono" style="color:#A3A7AE;font-size:12px">${l}</span>
        <img src="${path.join(OUT, f + '.png')}" style="width:412px;height:915px;border-radius:36px;box-shadow:0 0 0 1px rgba(255,255,255,.08),0 30px 80px -30px rgba(0,0,0,.8)"></div>`).join('')}
    </div>
    <div style="position:absolute;left:48px;right:48px;top:1196px;display:flex;gap:24px">
      ${[['Классика', AQUA, 'функционалка · фитнес · гимнастика · Zone 2'], ['Продвинутый', DAWN, 'периодизация · кроссфит · ВИИТ · калистеника'], ['Экстрим', SUN, 'навыки · гимнастическая сила · спринты · чиппер'],
        ['M-ALL', AQUA, 'разминка с мобильностью и заминка с растяжкой — в каждой тренировке'], ['Конфигуратор', '#F3F1EC', 'уровень → цель → методика → шаблон → блоки → упражнения → параметры']]
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
  if (!only || only.includes('_training')) { const s = sheet(); await shot('_training', s.html, s.w, s.h, 1); }
  await browser.close();
})();
