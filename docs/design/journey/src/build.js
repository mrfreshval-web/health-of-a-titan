// Builds the A42 «Путь Титана» mockups in the «Титаниум» style (A37) as HTML and renders them to PNG with Playwright.
// Usage (from repo root):
//   python3 docs/design/journey/src/getfonts.py   # once, downloads OFL fonts (Geist, Geist Mono)
//   node docs/design/journey/src/build.js [outDir]   # ONLY=j2-path,_journey for a subset
// art.js, titan.js, styles.css are copied from docs/design/concept-v3/src (branch claude/concept-v3) without changes.
const fs = require('fs');
const path = require('path');
const { ring, icon, picto } = require('./art');
const { DEFS, titanScene, titanZones } = require('./titan');

const SRC = __dirname;
const OUT = path.resolve(process.argv[2] || path.resolve(SRC, '..'));
const HTML = path.join(SRC, 'html');
fs.mkdirSync(HTML, { recursive: true });
fs.mkdirSync(OUT, { recursive: true });

const AQUA = '#7DE3D1', DAWN = '#FFC77A', IRIS = '#A8A3FF', SUN = '#FFB067';
const LIGHT = ['#FFFFFF', '#FFF1D6', '#FFD08A'];
const DIM = ['#B9BEC6', '#8D939C', '#6B7079'];
const IRIS_L = ['#FFFFFF', '#E4E1FF', '#B9B4FF'];

const page = (body, { theme = 'dark', extraCss = '', w = 412, h = 915, wrap = true } = {}) => `<!doctype html>
<html lang="ru"><head><meta charset="utf-8"><title>HoT · Путь Титана</title>
<link rel="stylesheet" href="../fonts/local.css"><link rel="stylesheet" href="../styles.css">
<style>html,body{width:${w}px;height:${h}px;overflow:hidden}${extraCss}</style></head>
<body>${DEFS}${wrap ? `<div class="screen ${theme}">${body}</div>` : body}</body></html>`;

const statusBar = (time, color = '') => `<div class="statusbar" ${color ? `style="color:${color}"` : ''}><span class="num">${time}</span><span class="icons">
  <svg width="16" height="12" viewBox="0 0 16 12" fill="currentColor"><path d="M15 1v10H1z"/></svg>
  ${icon('wifi', 17, 2)}
  <svg width="24" height="12" viewBox="0 0 24 12"><rect x=".6" y=".6" width="20.5" height="10.8" rx="3.4" fill="none" stroke="currentColor" stroke-width="1.2" opacity=".5"/><rect x="2.4" y="2.4" width="13.5" height="7.2" rx="1.8" fill="currentColor"/><rect x="22" y="4" width="1.6" height="4" rx=".8" fill="currentColor" opacity=".5"/></svg>
  </span></div>`;

// Navigation: «Я» becomes «Путь» (A42 §3.1, question 2 to the founder).
const nav = (active) => {
  const items = [['today', 'Сегодня'], ['mentor', 'Винс'], ['week', 'Неделя'], ['path', 'Путь']];
  return `<div class="nav glass">${items.map(([k, l]) => `<div class="it ${k === active ? 'on' : ''}">${k === 'path' ? picto('goal', 22, 1.5) : icon(k, 22, 1.6)}${k === active ? `<span>${l}</span>` : ''}</div>`).join('')}</div><div class="gesture"></div>`;
};
const progress = (n, of = 3) => `<div class="abs row" style="left:24px;right:24px;top:56px;gap:6px">${Array.from({ length: of }, (_, i) => `<div style="flex:1;height:3px;border-radius:2px;background:${i < n ? 'var(--fg)' : 'rgba(255,255,255,.14)'}"></div>`).join('')}</div>`;
const sceneSize = (r) => Math.round(r * 2048 / 940);

// Mentor avatars (A38 §8.3): geometry of the mark, no faces. size in px, col = line colour.
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
  } else {
    g = `<circle cx="28" cy="28" r="20" ${s} stroke-width="1.6"/><circle cx="28" cy="28" r="16.6" ${s} stroke-width=".8" opacity=".7"/>
      <path d="M24.5 21v19M31.5 21v19M22 40h12" ${s} stroke-width="1.6"/>
      <path d="M21 18.6c0-2 2.8-2 2.8 0M34.2 18.6c0-2-2.8-2-2.8 0M21.4 18.4h13.2" ${s} stroke-width="1.4"/>
      <path d="M28 23.6l-4 -2.4v4.8zM28 23.6l4 -2.4v4.8z" fill="${col}" stroke="${col}" stroke-width=".8" stroke-linejoin="round"/>`;
  }
  return `<svg width="${size}" height="${size}" viewBox="0 0 56 56" style="overflow:visible;filter:drop-shadow(0 0 6px ${col}55)">${g}</svg>`;
}

// Goal ring: 12 week segments around a centre, milestones after weeks 4, 8, 12.
// weeks: array of 12 states: 'lit' | 'dim' (missed: contour) | 'now' | 'todo'
function goalRing(cx, cy, R, weeks, col, { w = 5, gap = 4.2, glow = true, milestones = [4, 8, 12], doneMs = 0 } = {}) {
  const seg = 30 - gap;
  const pt = (a, r = R) => [cx + r * Math.cos(a * Math.PI / 180), cy + r * Math.sin(a * Math.PI / 180)];
  const arc = (a0, a1, r = R) => { const [x0, y0] = pt(a0, r), [x1, y1] = pt(a1, r); return `M${x0.toFixed(2)} ${y0.toFixed(2)} A${r} ${r} 0 0 1 ${x1.toFixed(2)} ${y1.toFixed(2)}`; };
  let out = '';
  weeks.forEach((st, i) => {
    const a0 = -90 + i * 30 + gap / 2, a1 = a0 + seg;
    if (st === 'lit') out += `<path d="${arc(a0, a1)}" fill="none" stroke="${col}" stroke-width="${w}" stroke-linecap="round" ${glow ? `style="filter:drop-shadow(0 0 4px ${col}) drop-shadow(0 0 10px ${col}88)"` : ''}/>`;
    else if (st === 'dim') out += `<path d="${arc(a0, a1)}" fill="none" stroke="${col}" stroke-width="${w}" stroke-linecap="round" opacity=".28"/>`;
    else if (st === 'now') out += `<path d="${arc(a0, a1)}" fill="none" stroke="rgba(255,255,255,.10)" stroke-width="${w}" stroke-linecap="round"/><path d="${arc(a0, a0 + seg * .45)}" fill="none" stroke="${col}" stroke-width="${w}" stroke-linecap="round" style="filter:drop-shadow(0 0 4px ${col})"/><path d="${arc(a0 - 1, a1 + 1, R + 9)}" fill="none" stroke="${col}" stroke-width="1.2" stroke-dasharray="2 3" opacity=".8"/>`;
    else out += `<path d="${arc(a0, a1)}" fill="none" stroke="rgba(255,255,255,.09)" stroke-width="${w}" stroke-linecap="round"/>`;
  });
  milestones.forEach((m, k) => {
    const [x, y] = pt(-90 + m * 30);
    const done = k < doneMs;
    out += `<circle cx="${x}" cy="${y}" r="${done ? 4.5 : 4}" fill="${done ? col : 'var(--bg)'}" stroke="${col}" stroke-width="1.5" ${done ? `style="filter:drop-shadow(0 0 5px ${col})"` : ''}/>`;
  });
  return out;
}
const growthRings = (cx, cy, rings) => rings.map(([r, c, o = .5]) => `<circle cx="${cx}" cy="${cy}" r="${r}" fill="none" stroke="${c}" stroke-width="1.4" opacity="${o}" style="filter:drop-shadow(0 0 6px ${c})"/>`).join('');

// Titan body zones (A37 §8): legs = body, corpus = food, chest = people, arms = work, head = mind; halo = sleep
const Z = {
  mind: { x0: 860, x1: 1190, y0: 480, y1: 660 },
  work: { rects: [{ x0: 490, x1: 870, y0: 560, y1: 940 }, { x0: 1180, x1: 1560, y0: 560, y1: 940 }] },
  people: { x0: 870, x1: 1180, y0: 660, y1: 960 },
  food: { x0: 800, x1: 1260, y0: 960, y1: 1230 },
  body: { x0: 600, x1: 1460, y0: 1230, y1: 1820 },
};
const zonesOf = (lits) => Object.entries(lits).map(([k, v]) => ({ ...Z[k], lit: v }));
const columnsOnly = (S, op = .5) => `<svg width="${S}" height="${S}" viewBox="0 0 2048 2048" style="position:absolute;left:0;top:0;overflow:visible">
  <g clip-path="url(#tt-left)" opacity="${op}"><use href="#hot-columns-art" fill="#8D97A3" stroke="#8D97A3"/></g><g clip-path="url(#tt-right)" opacity="${op}"><use href="#hot-columns-art" fill="#8D97A3" stroke="#8D97A3"/></g></svg>`;

const SPHERES = [['body', 'Тело'], ['sleep', 'Сон'], ['work', 'Работа'], ['food', 'Еда'], ['people', 'Связи'], ['mind', 'Разум']];

// ------------------------------------------------------------------ j1-1 · Hello: the Titan comes, «Стань Титаном», six spheres
function hello() {
  const cx = 206, cy = 318, R = 128, S = sceneSize(R);
  const orbit = SPHERES.map(([p], i) => {
    const a = (-90 + i * 60) * Math.PI / 180, x = cx + (R + 44) * Math.cos(a), y = cy + (R + 44) * Math.sin(a);
    return `<div class="abs center" style="left:${x - 21}px;top:${y - 21}px;width:42px;height:42px;border-radius:21px;background:rgba(10,11,14,.9);box-shadow:inset 0 0 0 1px rgba(255,199,122,.28);color:${p === 'sleep' ? IRIS : 'var(--fg)'}">${picto(p, 22, 1.5)}</div>`;
  }).join('');
  const body = `
  <div class="abs" style="inset:0;background:radial-gradient(70% 36% at 50% 34%, rgba(255,190,110,.20), transparent 70%),linear-gradient(180deg,#05060A 0%,#060708 60%)"></div>
  <div class="abs" style="left:0;right:0;top:420px;height:200px;background:linear-gradient(180deg,transparent,rgba(160,170,190,.07) 40%,transparent);filter:blur(8px)"></div>
  ${statusBar('9:40')}
  ${progress(1)}
  <svg class="abs" style="left:0;top:0" width="412" height="915"><circle cx="${cx}" cy="${cy}" r="${R + 44}" fill="none" stroke="rgba(255,199,122,.16)" stroke-width="1" stroke-dasharray="2 5"/></svg>
  <div class="abs" style="left:${cx - S / 2}px;top:${cy - S / 2}px">${titanScene(S, { ring: .34, ringCol: DAWN, pulse: .5, pulseCol: DAWN, spread: .9, glow: 'rgba(255,199,122,.55)' })}</div>
  ${orbit}
  <div class="abs col" style="left:24px;right:24px;top:560px;align-items:center;text-align:center">
    <div class="d55">Стань Титаном</div>
    <div class="row fg2" style="gap:14px;margin-top:20px;align-items:center">${['body', 'sleep', 'work', 'food', 'people', 'mind'].map((p) => picto(p, 22, 1.4)).join('')}</div>
  </div>
  <div class="abs" style="left:24px;right:24px;top:776px"><div class="btn light-pill" style="width:100%">Начать ${icon('arrow', 18, 1.8)}</div></div>
  <div class="abs row mono fg3" style="left:0;right:0;top:850px;justify-content:center;gap:8px">${icon('moon', 13, 1.6)}<span>3 минуты · без анкет</span></div>
  <div class="gesture"></div>`;
  return page(body);
}

// ------------------------------------------------------------------ j1-2 · Mentor: three characters (A38 §9), preview line 7.1
function mentorPick() {
  const cards = [
    ['sensei', 'Сенсей Винс', 'Держит рамку', '«Короткая ночь — не поражение, а условие задачи. Начни с воды.»', SUN],
    ['bro', 'Винс', 'Свой парень', '«4:40 — это не сон, это перекур. Интервалы в топку, идём гулять.»', DAWN],
    ['butler', 'Винсент', 'Всё уже подано', '«Ночь выдалась лаконичной. Я взял смелость заменить интервалы прогулкой.»', AQUA],
  ];
  const card = ([k, name, gist, line, c], i) => `<div class="abs glass" style="left:16px;right:16px;top:${206 + i * 182}px;height:168px;border-radius:28px;padding:18px 18px 16px">
    <div class="row" style="gap:14px;align-items:center">
      <div class="center" style="width:64px;height:64px;border-radius:32px;background:rgba(0,0,0,.35);box-shadow:inset 0 0 0 1px ${c}44">${avatar(k, 50, c)}</div>
      <div class="col" style="gap:5px;flex:1"><span class="t21">${name}</span><span class="mono" style="color:${c}">${gist}</span></div>
      <div class="center" style="width:44px;height:44px;border-radius:22px;box-shadow:inset 0 0 0 1px var(--hair2)">${icon('play', 18, 1.8)}</div>
    </div>
    <div class="b15" style="margin-top:16px;color:rgba(243,241,236,.86);font-weight:300">${line}</div>
  </div>`;
  const body = `
  <div class="abs" style="inset:0;background:radial-gradient(60% 24% at 50% 10%, rgba(255,190,110,.12), transparent 70%)"></div>
  ${statusBar('9:41')}
  ${progress(2)}
  <div class="abs col" style="left:24px;right:24px;top:92px;gap:10px">
    <span class="mono" style="color:var(--dawn)">Наставник · утро после короткой ночи</span>
    <div class="d34">Кто поведёт тебя?</div>
  </div>
  ${cards.map(card).join('')}
  <div class="abs row" style="left:24px;right:24px;top:768px;justify-content:space-between;align-items:center">
    <span class="row mono fg3" style="gap:8px">${picto('changed', 13, 1.6)}сменить можно в любой момент</span>
    <span class="row" style="gap:6px;color:var(--fg2);font-size:15px">Пропустить ${icon('arrow', 15, 1.8)}</span>
  </div>
  <div class="abs row mono fg3" style="left:0;right:0;top:838px;justify-content:center;gap:8px">${picto('self', 14, 1.6)}<span>один тап по карточке</span></div>
  <div class="gesture"></div>`;
  return page(body);
}

// ------------------------------------------------------------------ j1-3 · Profile derived from data (D-027): sources, feature chips, start sphere
function profile() {
  const cx = 206, cy = 168, R = 74, S = sceneSize(R);
  const rows = [
    ['sun', 'Подъём', '6:40', 'будильник'],
    ['moon', 'Отбой', '23:10', 'WHOOP · 28 ночей'],
    ['bolt', 'Тренировки', '18–20', 'WHOOP · 90 дней'],
    ['ceiling', 'Уровень', 'Классика', 'WHOOP · нагрузка'],
  ];
  const row = ([ic, l, v, src], i) => `<div class="row" style="height:52px;justify-content:space-between;${i ? 'border-top:1px solid var(--hair)' : ''}">
    <div class="row" style="gap:12px"><span class="fg2">${ic === 'ceiling' ? picto('ceiling', 20, 1.5) : icon(ic, 20, 1.6)}</span><span class="b16 fg2">${l}</span></div>
    <div class="row" style="gap:10px"><span class="tok">${src}</span><span class="num" style="font-size:19px;font-weight:400;min-width:${ic === 'ceiling' ? 0 : 52}px;text-align:right">${v}</span></div></div>`;
  const chips = [['дети', 0], ['собака', 1], ['смены', 0], ['травма', 0], ['ем вне дома', 1]];
  const body = `
  <div class="abs" style="inset:0;background:radial-gradient(60% 22% at 50% 18%, rgba(125,227,209,.14), transparent 70%)"></div>
  ${statusBar('9:42')}
  ${progress(3)}
  <div class="abs" style="left:${cx - S / 2}px;top:${cy - S / 2}px">${titanScene(S, { ring: 1, ringCol: AQUA, light: ['#FFFFFF', '#DFFBF6', '#9FEADC'], glow: 'rgba(125,227,209,.55)', ringW: 14 })}</div>
  <div class="abs col" style="left:24px;right:24px;top:262px;align-items:center;gap:8px">
    <div class="d34">Я уже знаю тебя</div>
    <span class="row mono" style="gap:8px;color:${AQUA}">${picto('seal', 14, 1.8)}WHOOP · 90 дней · календарь</span>
  </div>
  <div class="abs glass" style="left:16px;right:16px;top:340px;border-radius:26px;padding:4px 18px">${rows.map(row).join('')}</div>
  <div class="abs" style="left:20px;right:20px;top:572px;display:flex;flex-wrap:wrap;gap:8px">${chips.map(([c, on]) => `<span class="chip ${on ? 'on' : ''}">${on ? icon('check', 13, 2.2) : '+'} ${c}</span>`).join('')}</div>
  <div class="abs col" style="left:24px;right:24px;top:660px;gap:12px">
    <span class="mono fg2">С чего начнём?</span>
    <div class="row" style="justify-content:space-between">${SPHERES.map(([p, l]) => `<div class="col" style="align-items:center;gap:6px"><div class="center" style="width:48px;height:48px;border-radius:24px;${p === 'sleep' ? `background:rgba(168,163,255,.16);box-shadow:inset 0 0 0 1.5px ${IRIS},0 0 18px rgba(168,163,255,.35);color:${IRIS}` : 'background:rgba(255,255,255,.04);box-shadow:inset 0 0 0 1px var(--hair2);color:var(--fg2)'}">${picto(p, 24, 1.5)}</div>${p === 'sleep' ? `<span class="mono" style="font-size:9.5px;color:${IRIS};letter-spacing:.08em">${l}</span>` : '<span style="height:14px"></span>'}</div>`).join('')}</div>
  </div>
  <div class="abs" style="left:24px;right:24px;top:788px"><div class="btn light-pill" style="width:100%">Всё верно ${icon('arrow', 18, 1.8)}</div></div>
  <div class="gesture"></div>`;
  return page(body);
}

// ------------------------------------------------------------------ j2 · «Путь»: Titan + 12-week goal ring + growth rings + ladder of stages
function pathTab() {
  const cx = 206, cy = 290, R = 146, S = sceneSize(R * .82);
  const weeks = ['lit', 'lit', 'dim', 'lit', 'lit', 'now', 'todo', 'todo', 'todo', 'todo', 'todo', 'todo'];
  const zones = zonesOf({ body: 1, food: .5, mind: .4, work: .6, people: .35 });
  const stages = [['3 мин', 1], ['день', 1], ['неделя', 1], ['30 дн', 1], ['90 дн', 1], ['год', 0], ['дальше', 0]];
  const lx0 = 36, lx1 = 376, ly = 616;
  const sx = (i) => lx0 + i * (lx1 - lx0) / 6;
  const curX = sx(4) + (sx(5) - sx(4)) * .17;
  const body = `
  <div class="abs" style="inset:0;background:radial-gradient(60% 30% at 50% 31%, rgba(255,199,122,.14), transparent 72%)"></div>
  ${statusBar('20:05')}
  <div class="abs row" style="left:24px;right:16px;top:50px;height:44px;justify-content:space-between">
    <span class="mono" style="color:var(--dawn)">Путь · день 131 · цикл 2</span>
    <div class="row" style="gap:6px"><div class="center" style="width:40px;height:40px;border-radius:20px;box-shadow:inset 0 0 0 1px var(--hair2)">${avatar('sensei', 28, SUN)}</div><div class="center fg2" style="width:36px;height:40px">${icon('dots', 22)}</div></div>
  </div>
  <svg class="abs" style="left:0;top:0;overflow:visible" width="412" height="915">
    ${growthRings(cx, cy, [[R + 22, IRIS, .55]])}
    ${goalRing(cx, cy, R, weeks, DAWN, { doneMs: 1 })}
  </svg>
  <div class="abs center" style="left:${cx + (R + 22) * Math.cos(-.62) - 12}px;top:${cy + (R + 22) * Math.sin(-.62) - 12}px;width:24px;height:24px;border-radius:12px;background:var(--bg);color:${IRIS}">${picto('sleep', 16, 1.6)}</div>
  <div class="abs" style="left:${cx - S / 2}px;top:${cy - S / 2}px">${columnsOnly(S, .45)}<div style="position:absolute;left:0;top:0">${titanZones(S, zones, { base: 'rgba(255,255,255,.22)' })}</div></div>
  <div class="abs col" style="left:24px;right:24px;top:${cy + R + 28}px;align-items:center;gap:8px">
    <div class="row" style="gap:10px;align-items:center"><span style="color:var(--dawn)">${picto('body', 26, 1.5)}</span><span class="t21">Силовая 2× · Zone 2 150 мин</span></div>
    <span class="mono fg2">этап 2 из 3 · неделя 6 из 12</span>
  </div>
  <svg class="abs" style="left:0;top:0;overflow:visible" width="412" height="915">
    <path d="M${lx0} ${ly}H${curX}" stroke="${DAWN}" stroke-width="2" style="filter:drop-shadow(0 0 4px ${DAWN})"/>
    <path d="M${curX} ${ly}H${lx1}" stroke="rgba(255,255,255,.16)" stroke-width="2" stroke-dasharray="3 5"/>
    ${stages.map(([, done], i) => `<circle cx="${sx(i)}" cy="${ly}" r="${done ? 4.5 : 4}" fill="${done ? DAWN : 'var(--bg)'}" stroke="${done ? DAWN : 'rgba(255,255,255,.3)'}" stroke-width="1.5"/>`).join('')}
  </svg>
  <div class="abs center" style="left:${curX - 15}px;top:${ly - 36}px;width:30px;height:30px;color:#fff;filter:drop-shadow(0 0 6px ${DAWN})">${picto('body', 26, 1.6)}</div>
  ${stages.map(([l, done], i) => `<div class="abs mono" style="left:${sx(i) - 30}px;width:60px;top:${ly + 14}px;text-align:center;font-size:9.5px;letter-spacing:.06em;color:${done ? 'var(--fg2)' : 'var(--fg3)'}">${l}</div>`).join('')}
  <div class="abs glass row" style="left:16px;right:16px;top:672px;height:88px;border-radius:26px;padding:0 16px;justify-content:space-between">
    <div class="row" style="gap:14px">
      <div class="center" style="width:48px;height:48px;border-radius:24px;background:rgba(255,199,122,.10);box-shadow:inset 0 0 0 1px rgba(255,199,122,.4);color:var(--dawn)">${picto('body', 24, 1.5)}</div>
      <div class="col" style="gap:5px"><span class="mono fg3">сегодня к цели</span><span class="t21" style="font-size:19px">Zone 2 · 40 мин</span></div>
    </div>
    <div class="row" style="gap:10px"><span class="mono" style="color:var(--dawn)">18:00</span>${ring(30, 3, .45, DAWN)}</div>
  </div>
  ${nav('path')}`;
  return page(body);
}

// ------------------------------------------------------------------ j3 · Goal setting for a sphere: now → goal, 3 stages, one confirm
function goalSet() {
  const cx = 206, cy = 254, R = 104, S = sceneSize(R * .9);
  const body = `
  <div class="abs" style="inset:0;background:radial-gradient(60% 26% at 50% 26%, rgba(168,163,255,.18), transparent 72%)"></div>
  ${statusBar('20:12')}
  <div class="abs row" style="left:16px;right:16px;top:50px;height:44px;justify-content:space-between;align-items:center">
    <div class="center" style="width:44px;height:44px">${icon('back', 22, 1.6)}</div>
    <span class="mono" style="color:${IRIS}">Новая цель · 12 недель</span>
    <div style="width:44px"></div>
  </div>
  <div class="abs row" style="left:24px;right:24px;top:104px;justify-content:space-between">${SPHERES.map(([p]) => `<div class="center" style="width:44px;height:44px;border-radius:22px;${p === 'sleep' ? `background:rgba(168,163,255,.16);box-shadow:inset 0 0 0 1.5px ${IRIS};color:${IRIS}` : 'box-shadow:inset 0 0 0 1px var(--hair);color:var(--fg3)'}">${picto(p, 22, 1.5)}</div>`).join('')}</div>
  <svg class="abs" style="left:0;top:0;overflow:visible" width="412" height="915">
    <circle cx="${cx}" cy="${cy}" r="${R}" fill="none" stroke="rgba(168,163,255,.14)" stroke-width="5"/>
    <circle cx="${cx}" cy="${cy}" r="${R}" fill="none" stroke="${IRIS}" stroke-width="5" stroke-linecap="round" stroke-dasharray="${(2 * Math.PI * R * 3 / 7).toFixed(1)} 999" transform="rotate(-90 ${cx} ${cy})" style="filter:drop-shadow(0 0 5px ${IRIS})"/>
    <circle cx="${cx}" cy="${cy}" r="${R}" fill="none" stroke="${IRIS}" stroke-width="1.4" stroke-dasharray="2 4" opacity=".7" transform="rotate(${-90 + 360 * 3 / 7} ${cx} ${cy})" pathLength="700" style="stroke-dasharray:2 4"/>
  </svg>
  <div class="abs" style="left:${cx - S / 2}px;top:${cy - S / 2}px">${titanScene(S, { light: IRIS_L, colLight: ['#D9D6F2', '#8F8AB0', '#5E5A78'], glow: 'rgba(168,163,255,.45)', colOpacity: .7 })}</div>
  <div class="abs row" style="left:0;right:0;top:394px;justify-content:center;align-items:center;gap:18px">
    <div class="col" style="align-items:center;gap:2px"><span class="mono fg3">сейчас</span><span class="d55 num" style="color:var(--fg2)">3<span style="font-size:28px">/7</span></span></div>
    <span style="color:${IRIS};margin-top:16px">${icon('arrow', 28, 1.4)}</span>
    <div class="col" style="align-items:center;gap:2px"><span class="mono" style="color:${IRIS}">цель</span><span class="d55 num" style="color:#fff;text-shadow:0 0 18px rgba(168,163,255,.6)">5<span style="font-size:28px">/7</span></span></div>
  </div>
  <div class="abs row" style="left:0;right:0;top:488px;justify-content:center;gap:10px;align-items:center"><span class="b16 fg2">ночей от 7 часов</span><span class="tok">WHOOP · 28 ночей</span></div>
  <div class="abs row" style="left:20px;right:20px;top:540px;gap:18px">${[['4 нед', '4/7', 1], ['4 нед', '5/7', .6], ['4 нед', 'держать', .35]].map(([w, t, o], i) => `<div class="col" style="flex:1;gap:8px"><div style="height:6px;border-radius:3px;background:${IRIS};opacity:${o};${i === 0 ? `box-shadow:0 0 10px ${IRIS}` : ''}"></div><div class="row" style="justify-content:space-between"><span class="mono fg3">${w}</span><span class="l13">${t}</span></div></div>`).join('')}</div>
  <div class="abs glass row" style="left:16px;right:16px;top:612px;border-radius:24px;padding:14px 16px;gap:12px;align-items:flex-start">
    <div class="center" style="flex:none;width:40px;height:40px;border-radius:20px;background:rgba(0,0,0,.3)">${avatar('sensei', 30, SUN)}</div>
    <div class="col" style="gap:6px"><span class="b15" style="font-weight:300;color:rgba(243,241,236,.9)">«После ночей от 7 часов твой Заряд выше на 14. Начнём с четырёх таких ночей.»</span>
    <div class="row" style="gap:6px"><span class="tok">${picto('charge', 12, 1.6)} +14</span><span class="tok">${picto('sleep', 12, 1.6)} 6:58 медиана</span></div></div>
  </div>
  <div class="abs row" style="left:24px;right:24px;top:772px;gap:10px">
    <div class="btn ghost" style="width:120px">Другая</div>
    <div class="btn light-pill" style="flex:1">Беру ${icon('check', 18, 2)}</div>
  </div>
  <div class="abs mono fg3" style="left:0;right:0;top:846px;text-align:center">зачем · больше сил днём</div>
  <div class="gesture"></div>`;
  return page(body);
}

// ------------------------------------------------------------------ j4 · Goal achieved: ritual, growth ring, was → now, next choice
function achieved() {
  const cx = 206, cy = 300, R = 132, S = sceneSize(R * .9);
  const rays = Array.from({ length: 36 }, (_, i) => {
    const a = i * 10 * Math.PI / 180, r0 = R + 30, r1 = R + 30 + (i % 3 ? 16 : 30);
    return `<path d="M${cx + r0 * Math.cos(a)} ${cy + r0 * Math.sin(a)}L${cx + r1 * Math.cos(a)} ${cy + r1 * Math.sin(a)}" stroke="${DAWN}" stroke-width="${i % 3 ? 1 : 1.6}" opacity="${i % 3 ? .35 : .7}" stroke-linecap="round"/>`;
  }).join('');
  const zones = zonesOf({ body: 1, food: .4, mind: .35, work: .5, people: .25 });
  const body = `
  <div class="abs" style="inset:0;background:radial-gradient(70% 38% at 50% 33%, rgba(255,199,122,.30), transparent 72%)"></div>
  ${statusBar('19:48')}
  <div class="abs col" style="left:0;right:0;top:62px;align-items:center"><span class="mono" style="color:var(--dawn)">Цель взята · 12 недель · день 175</span></div>
  <svg class="abs" style="left:0;top:0;overflow:visible" width="412" height="915">
    ${rays}
    ${growthRings(cx, cy, [[R + 14, IRIS, .5], [R + 22, DAWN, .8]])}
    <circle cx="${cx}" cy="${cy}" r="${R}" fill="none" stroke="${DAWN}" stroke-width="6" style="filter:drop-shadow(0 0 6px ${DAWN}) drop-shadow(0 0 20px ${DAWN})"/>
  </svg>
  <div class="abs" style="left:${cx - S / 2}px;top:${cy - S / 2}px">${columnsOnly(S, .6)}<div style="position:absolute;left:0;top:0">${titanZones(S, zones, { base: 'rgba(255,255,255,.26)' })}</div></div>
  <div class="abs col" style="left:24px;right:24px;top:500px;align-items:center;gap:6px">
    <div class="row" style="gap:12px;align-items:center"><span style="color:var(--dawn)">${picto('body', 34, 1.4)}</span><span class="d55">Тело в свете</span></div>
  </div>
  <div class="abs row" style="left:24px;right:24px;top:578px;justify-content:center;gap:12px">
    ${[['силовая / нед', '1', '2'], ['Zone 2, мин', '70', '150']].map(([l, a, b]) => `<div class="glass col" style="flex:1;border-radius:20px;padding:12px 14px;gap:4px"><span class="mono fg3" style="font-size:10px">${l}</span><div class="row num" style="gap:8px;align-items:baseline"><span style="font-size:24px;font-weight:200;color:var(--fg3)">${a}</span><span class="fg3">→</span><span style="font-size:30px;font-weight:300">${b}</span></div></div>`).join('')}
  </div>
  <div class="abs row" style="left:24px;right:24px;top:676px;gap:12px;align-items:center">
    <div class="center" style="flex:none;width:36px;height:36px;border-radius:18px;background:rgba(0,0,0,.3)">${avatar('sensei', 26, SUN)}</div>
    <span class="b15" style="font-weight:300;color:rgba(243,241,236,.9)">«Ты держал форму двенадцать недель. Так строится Титан.»</span>
  </div>
  <div class="abs row" style="left:24px;right:24px;top:752px;gap:10px">
    <div class="btn ghost" style="flex:1;font-size:15px">${picto('ceiling', 18, 1.6)} Продвинутый</div>
    <div class="btn light-pill" style="flex:1.15">Новая цель ${icon('arrow', 18, 1.8)}</div>
  </div>
  <div class="abs row mono fg3" style="left:0;right:0;top:826px;justify-content:center;gap:18px"><span>допуск: 12 нед · WHOOP</span><span>·</span><span>неделя отдыха</span></div>
  <div class="gesture"></div>`;
  return page(body);
}

// ------------------------------------------------------------------ j5 · Return after a break: Titan in the light again, stage kept, start small
function comeback() {
  const cx = 206, cy = 300, R = 132, S = sceneSize(R * .82);
  const weeks = ['lit', 'lit', 'dim', 'lit', 'lit', 'now', 'todo', 'todo', 'todo', 'todo', 'todo', 'todo'];
  const zones = zonesOf({ body: .55, food: .3, mind: .3, work: .35, people: .2 });
  const body = `
  <div class="abs" style="inset:0;background:radial-gradient(70% 34% at 50% 32%, rgba(255,214,160,.16), transparent 72%),linear-gradient(180deg,#07080C 0%,#060708 70%)"></div>
  ${statusBar('8:15')}
  <div class="abs col" style="left:0;right:0;top:62px;align-items:center"><span class="mono" style="color:var(--dawn)">Путь · цикл 2 · этап 2</span></div>
  <svg class="abs" style="left:0;top:0;overflow:visible" width="412" height="915">
    ${growthRings(cx, cy, [[R + 22, IRIS, .45]])}
    ${goalRing(cx, cy, R, weeks, DAWN, { doneMs: 1 })}
  </svg>
  <div class="abs" style="left:${cx - S / 2}px;top:${cy - S / 2}px">${columnsOnly(S, .5)}<div style="position:absolute;left:0;top:0">${titanScene(S, { columns: false, light: LIGHT, glow: 'rgba(255,214,160,.65)' })}</div></div>
  <div class="abs col" style="left:24px;right:24px;top:494px;align-items:center;text-align:center;gap:10px">
    <div class="d55" style="font-size:46px;line-height:50px;white-space:nowrap">С возвращением</div>
    <span class="row mono fg2" style="gap:8px">${picto('body', 14, 1.6)}цель ждёт · этап 2 сохранён</span>
  </div>
  <div class="abs glass row" style="left:16px;right:16px;top:612px;height:96px;border-radius:26px;padding:0 16px;gap:14px">
    <div class="center" style="flex:none;width:52px;height:52px;border-radius:26px;background:rgba(125,227,209,.10);box-shadow:inset 0 0 0 1px rgba(125,227,209,.45);color:${AQUA}">${picto('easy', 26, 1.5)}</div>
    <div class="col" style="gap:6px;flex:1"><span class="t21" style="font-size:19px">10 минут и один шаг</span><span class="row" style="gap:6px"><span class="tok">${picto('rovno', 12, 1.6)} 7 дней мягко</span><span class="tok">${picto('steps', 12, 1.6)} прогулка</span></span></div>
  </div>
  <div class="abs row" style="left:24px;right:24px;top:722px;gap:12px;align-items:center">
    <div class="center" style="flex:none;width:34px;height:34px;border-radius:17px;background:rgba(0,0,0,.3)">${avatar('butler', 24, AQUA)}</div>
    <span class="b15" style="font-weight:300;color:rgba(243,241,236,.86)">«Рад вас видеть. Всё на своих местах.»</span>
  </div>
  <div class="abs" style="left:24px;right:24px;top:784px"><div class="btn light-pill" style="width:100%">Начать с малого ${icon('arrow', 18, 1.8)}</div></div>
  <div class="abs mono fg3" style="left:0;right:0;top:856px;text-align:center">новый старт с понедельника</div>
  <div class="gesture"></div>`;
  return page(body);
}

// ------------------------------------------------------------------ _journey · the whole life path in one illustration
function journey() {
  const W = 1800, H = 1120;
  const ST = [
    { t: '3 минуты', n: 'Знакомство', see: 'Титан приходит, наставник, WHOOP', self: 'выводит распорядок из 90 дней', wow: '«Я уже знаю тебя»', next: 'сфера выбрана', o: { spread: .55, tScale: .55, tY: -60, tBlur: 14, tOpacity: .45, colOpacity: .35, ring: .08, glow: false, light: DIM, colLight: DIM }, fog: .9, size: 150, rings: [] },
    { t: 'День 1', n: 'Первый план', see: '«Сегодня» и шаг к сфере', self: 'план по правилам без вопросов', wow: 'день закрыт в 3 тапа', next: 'первый закрытый день', o: { spread: .85, tScale: .8, tOpacity: .75, colOpacity: .6, ring: .18, glow: 'rgba(255,199,122,.25)', light: ['#E9E4DA', '#C9BBA4', '#A08D73'] }, fog: .5, size: 168, rings: [] },
    { t: 'Неделя 1', n: 'Первый свет', see: 'Рассвет, хокку, Печать, Неделя', self: 'ищет самый короткий рычаг', wow: 'Титан светится по сферам', next: 'цель на 12 недель взята', o: { spread: 1, ring: .33, glow: 'rgba(255,199,122,.4)', light: ['#FFF6E6', '#FFD9A0', '#D9A870'] }, fog: .2, size: 186, rings: [] },
    { t: '30 дней', n: 'Норма и веха', see: 'треть кольца цели в свете', self: 'калибрует норму, оценка', wow: 'первая победа числом', next: 'веха 1', o: { spread: 1, ring: .5, glow: 'rgba(255,199,122,.5)', light: LIGHT }, size: 200, rings: [] },
    { t: '90 дней', n: 'Цель взята', see: 'ритуал, паспорт квартала', self: 'сверяет, предлагает уровень', wow: 'первое кольцо роста', next: 'новая цель', o: { spread: 1, ring: 1, glow: 'rgba(255,199,122,.65)', light: LIGHT }, size: 214, rings: [[1, IRIS]] },
    { t: 'Год', n: 'Четыре кольца', see: 'год назад — сейчас', self: 'поддержание взятых сфер', wow: 'почти весь Титан в свете', next: 'доступ к Экстриму', o: { spread: 1, ring: 1, glow: 'rgba(255,199,122,.8)', light: ['#FFFFFF', '#FFE9C4', '#FFC27A'] }, size: 228, rings: [[1, IRIS], [2, DAWN], [3, AQUA], [4, DAWN]] },
    { t: 'Дальше', n: 'Вся жизнь', see: 'новые цели и уровни', self: 'ротация сфер, возврат света', wow: 'шесть сфер в свете', next: '∞', o: { spread: 1, ring: 1, ringCol: '#FFE6B8', glow: 'rgba(255,214,150,.9)', light: ['#FFFFFF', '#FFF4DE', '#FFD08A'] }, size: 236, rings: [[1, IRIS], [2, DAWN], [3, AQUA], [4, DAWN], [5, IRIS], [6, DAWN], [7, AQUA]] },
  ];
  const x0 = 150, x1 = 1650;
  const X = (i) => x0 + i * (x1 - x0) / 6;
  const ground = (i) => 760 - i * 46 + (i === 4 ? 0 : 0);
  // the road: rises through the stages, with a dip between 30 and 90 days (a bad week) and a return
  const pts = ST.map((_, i) => [X(i), ground(i)]);
  const dipX = (X(3) + X(4)) / 2, dipY = ground(3) + 70;
  let road = `M40 ${ground(0) + 30} C ${X(0) - 40} ${ground(0)}, ${X(0) - 20} ${ground(0)}, ${X(0)} ${ground(0)}`;
  for (let i = 1; i < pts.length; i++) {
    const [px, py] = pts[i - 1], [qx, qy] = pts[i];
    if (i === 4) road += ` C ${px + 60} ${py}, ${dipX - 80} ${dipY}, ${dipX} ${dipY} C ${dipX + 80} ${dipY}, ${qx - 60} ${qy}, ${qx} ${qy}`;
    else road += ` C ${px + 90} ${py}, ${qx - 90} ${qy}, ${qx} ${qy}`;
  }
  road += ` C ${X(6) + 60} ${ground(6)}, ${W - 60} ${ground(6) - 30}, ${W} ${ground(6) - 40}`;
  const station = (s, i) => {
    const S = s.size, cx = X(i), gy = ground(i), cy = gy - S * .42;
    const ringR = S * 940 / 2048;
    return `
    ${s.fog ? `<div class="abs" style="left:${cx - S * .7}px;top:${cy - S * .5}px;width:${S * 1.4}px;height:${S}px;background:radial-gradient(closest-side,rgba(170,180,200,${.18 * s.fog}),transparent);filter:blur(6px)"></div>` : ''}
    <svg class="abs" style="left:0;top:0;overflow:visible" width="${W}" height="${H}">${s.rings.map(([k, c]) => `<circle cx="${cx}" cy="${cy}" r="${ringR + 6 + k * 6}" fill="none" stroke="${c}" stroke-width="1.3" opacity=".55" style="filter:drop-shadow(0 0 4px ${c})"/>`).join('')}</svg>
    <div class="abs" style="left:${cx - S / 2}px;top:${cy - S / 2}px">${titanScene(S, { ringCol: DAWN, ...s.o })}</div>
    <div class="abs" style="left:${cx - 5}px;top:${gy - 5}px;width:10px;height:10px;border-radius:5px;background:${i < 5 ? DAWN : '#FFE6B8'};box-shadow:0 0 12px ${DAWN}"></div>`;
  };
  const cardTop = 838;
  const card = (s, i) => `<div class="abs col" style="left:${X(i) - 104}px;width:208px;top:${cardTop}px;gap:6px">
    <span class="mono" style="color:var(--dawn)">${s.t}</span>
    <span class="t21" style="font-size:20px">${s.n}</span>
    <div class="col" style="gap:5px;margin-top:6px;font-size:13px;line-height:17px">
      <div class="row" style="gap:7px;align-items:flex-start"><span class="fg3" style="flex:none;margin-top:1px">${icon('eye', 14, 1.6)}</span><span class="fg2">${s.see}</span></div>
      <div class="row" style="gap:7px;align-items:flex-start"><span class="fg3" style="flex:none;margin-top:1px">${icon('wand', 14, 1.6)}</span><span class="fg2">${s.self}</span></div>
      <div class="row" style="gap:7px;align-items:flex-start"><span style="flex:none;margin-top:1px;color:var(--dawn)">${icon('spark', 14, 1.6)}</span><span>${s.wow}</span></div>
      <div class="row" style="gap:7px;align-items:flex-start"><span class="fg3" style="flex:none;margin-top:1px">${icon('arrow', 14, 1.6)}</span><span class="fg3">${s.next}</span></div>
    </div></div>`;
  // cycle of becoming: six nodes on a ring
  const CY = [['goal', 'цель'], ['layers', 'план'], ['steps', 'шаги'], ['check', 'сверка'], ['seal', 'итог'], ['spark', 'новая цель']];
  const ccx = 1190, ccy = 178, cr = 92;
  const cyc = CY.map(([ic, l], i) => {
    const a = (-90 + i * 60) * Math.PI / 180, x = ccx + cr * Math.cos(a), y = ccy + cr * Math.sin(a);
    const isP = ['goal', 'steps', 'seal'].includes(ic);
    const tx = x + (Math.cos(a) > .3 ? 30 : Math.cos(a) < -.3 ? -30 : 0), ty = y + (Math.sin(a) < -.5 ? -30 : Math.sin(a) > .5 ? 34 : 0);
    const al = Math.cos(a) > .3 ? 'left' : Math.cos(a) < -.3 ? 'right' : 'center';
    return `<div class="abs center" style="left:${x - 20}px;top:${y - 20}px;width:40px;height:40px;border-radius:20px;background:#0B0C0F;box-shadow:inset 0 0 0 1px rgba(255,199,122,.45);color:var(--dawn)">${isP && ic !== 'steps' ? (ic === 'seal' ? picto('seal', 20, 1.6) : picto('goal', 22, 1.5)) : (ic === 'steps' ? picto('steps', 20, 1.6) : icon(ic, 18, 1.7))}</div>
      <div class="abs mono" style="${al === 'left' ? `left:${tx - 4}px` : al === 'right' ? `right:${W - tx - 4}px` : `left:${tx - 60}px;width:120px;text-align:center`};top:${ty - 7}px;color:var(--fg2)">${l}</div>`;
  }).join('');
  const body = `
  <div class="abs" style="inset:0;background:radial-gradient(40% 50% at 92% 58%, rgba(255,199,122,.16), transparent 70%),radial-gradient(30% 40% at 6% 70%, rgba(150,160,190,.10), transparent 70%),linear-gradient(180deg,#05060A 0%,#060708 100%)"></div>
  <div class="abs col" style="left:72px;top:64px;gap:14px;width:900px">
    <span class="mono" style="color:var(--dawn)">HoT · A42 · путь на всю жизнь</span>
    <div class="d89" style="font-size:96px;line-height:92px">Путь Титана</div>
    <div class="t21 fg2" style="max-width:760px">Один и тот же Титан от первой минуты до года и дальше. Свет на нём — твой путь, кольца за спиной — взятые цели.</div>
  </div>
  <svg class="abs" style="left:0;top:0;overflow:visible" width="${W}" height="${H}">
    <circle cx="${ccx}" cy="${ccy}" r="${cr}" fill="none" stroke="rgba(255,199,122,.35)" stroke-width="1.5" stroke-dasharray="3 5"/>
    <path d="M${ccx + cr * Math.cos(-1.2)} ${ccy + cr * Math.sin(-1.2)} A${cr} ${cr} 0 0 1 ${ccx + cr * Math.cos(-.95)} ${ccy + cr * Math.sin(-.95)}" fill="none" stroke="${DAWN}" stroke-width="2" marker-end="url(#ar)"/>
    <defs><marker id="ar" viewBox="0 0 10 10" refX="6" refY="5" markerWidth="6" markerHeight="6" orient="auto"><path d="M0 0L10 5L0 10z" fill="${DAWN}"/></marker></defs>
  </svg>
  <div class="abs col" style="left:${ccx - 70}px;top:${ccy - 26}px;width:140px;align-items:center;text-align:center"><span class="mono" style="color:var(--dawn)">цикл</span><span class="t21" style="font-size:19px">12 недель</span></div>
  ${cyc}
  <svg class="abs" style="left:0;top:0;overflow:visible" width="${W}" height="${H}">
    <defs><linearGradient id="rg" x1="0" x2="1"><stop offset="0" stop-color="#5A6070" stop-opacity=".25"/><stop offset=".35" stop-color="${DAWN}" stop-opacity=".7"/><stop offset="1" stop-color="#FFE6B8"/></linearGradient></defs>
    <path d="${road}" fill="none" stroke="url(#rg)" stroke-width="3" style="filter:drop-shadow(0 0 6px rgba(255,199,122,.5))"/>
    <path d="${road}" fill="none" stroke="rgba(255,255,255,.06)" stroke-width="26" stroke-linecap="round"/>
  </svg>
  <div class="abs col" style="left:${dipX - 150}px;top:${dipY + 16}px;width:300px;align-items:center;text-align:center;gap:4px">
    <span class="row mono" style="gap:6px;color:${AQUA}">${picto('easy', 14, 1.6)}плохая неделя</span>
    <span style="font-size:13px;line-height:17px" class="fg2">пауза, а не обнуление · возврат в 1 тап</span>
  </div>
  ${ST.map(station).join('')}
  <div class="abs" style="left:40px;right:40px;top:${cardTop - 22}px;height:1px;background:linear-gradient(90deg,transparent,rgba(255,255,255,.10) 10%,rgba(255,255,255,.10) 90%,transparent)"></div>
  ${ST.map(card).join('')}
  <div class="abs row mono fg3" style="left:72px;bottom:28px;gap:26px">
    <span class="row" style="gap:6px">${icon('eye', 13, 1.6)}видит человек</span>
    <span class="row" style="gap:6px">${icon('wand', 13, 1.6)}делает само</span>
    <span class="row" style="gap:6px;color:var(--dawn)">${icon('spark', 13, 1.6)}вау</span>
    <span class="row" style="gap:6px">${icon('arrow', 13, 1.6)}дальше, когда</span>
    <span>· без стриков, баллов и бейджей · одно кольцо роста = одна взятая цель</span>
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
    ['j1-onboarding-1-hello', hello], ['j1-onboarding-2-mentor', mentorPick], ['j1-onboarding-3-profile', profile],
    ['j2-path', pathTab], ['j3-goal-set', goalSet], ['j4-goal-achieved', achieved], ['j5-return', comeback],
  ];
  for (const [n, fn] of jobs) if (!only || only.includes(n)) await shot(n, fn());
  if (!only || only.includes('_journey')) { const r = journey(); await shot('_journey', r.html, r.w, r.h, 1.5); }
  await browser.close();
})();
