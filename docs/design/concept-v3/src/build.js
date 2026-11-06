// Builds the HoT concept v3 «Ось» mockups (A37) as HTML and renders them to PNG with Playwright.
// Usage (from repo root):
//   python3 docs/design/concept-v3/src/getfonts.py   # once, downloads OFL fonts (Geist, Geist Mono, Cormorant SC)
//   node docs/design/concept-v3/src/build.js [outDir]
// Output: docs/design/concept-v3/*.png (screens 412x915 @2x), _concept.png, _sync-storyboard.png
const fs = require('fs');
const path = require('path');
const { pulsePath, axisGlyph, ring, modeGlyph, icon, columnSil } = require('./art');

const SRC = __dirname;
const OUT = path.resolve(process.argv[2] || path.resolve(SRC, '..'));
const HTML = path.join(SRC, 'html');
fs.mkdirSync(HTML, { recursive: true });
const MARK = path.resolve(SRC, '../../../../brand/mark-v2/master/hot-master-transparent.svg');
fs.copyFileSync(MARK, path.join(HTML, 'mark.svg'));
fs.copyFileSync(MARK.replace('-transparent', ''), path.join(HTML, 'mark-full.svg'));

const page = (body, { theme = 'dark', extraCss = '', w = 412, h = 915, wrap = true } = {}) => `<!doctype html>
<html lang="ru"><head><meta charset="utf-8"><title>HoT · Ось</title>
<link rel="stylesheet" href="../fonts/local.css"><link rel="stylesheet" href="../styles.css">
<style>html,body{width:${w}px;height:${h}px;overflow:hidden}${extraCss}</style></head>
<body>${wrap ? `<div class="screen ${theme}">${body}</div>` : body}</body></html>`;

const statusBar = (time) => `<div class="statusbar"><span class="num">${time}</span><span class="icons">
  <svg width="16" height="12" viewBox="0 0 16 12" fill="currentColor"><path d="M15 1v10H1z" opacity=".95"/></svg>
  ${icon('wifi', 17, 2)}
  <svg width="24" height="12" viewBox="0 0 24 12"><rect x=".6" y=".6" width="20.5" height="10.8" rx="3.4" fill="none" stroke="currentColor" stroke-width="1.2" opacity=".5"/><rect x="2.4" y="2.4" width="13.5" height="7.2" rx="1.8" fill="currentColor"/><rect x="22" y="4" width="1.6" height="4" rx=".8" fill="currentColor" opacity=".5"/></svg>
  </span></div>`;

const nav = (active, light = false) => {
  const items = [['today', 'Сегодня'], ['mentor', 'Винс'], ['week', 'Неделя'], ['me', 'Я']];
  return `<div class="nav glass">${items.map(([k, l]) => `<div class="it ${k === active ? 'on' : ''}">${icon(k, 22, 1.6)}${k === active ? `<span>${l}</span>` : ''}</div>`).join('')}</div><div class="gesture"></div>`;
};

// Status capsule (D-029): strain vs mode ceiling · steps vs goal · time to lights-out. No SOS in the header.
const capsule = ({ light = false, strain = .7, steps = .76, bed = .62 } = {}) => {
  const m = 'var(--mode)';
  return `<div class="capsule glass">
    <div class="seg">${ring(22, 2.6, strain, m, { glow: light ? false : 'rgba(125,227,209,.45)' })}<span class="num">8,4</span></div>
    <div class="seg">${ring(22, 2.6, steps, 'var(--fg)')}<span class="num">6,8k</span></div>
    <div class="seg">${ring(22, 2.6, bed, 'var(--berezhno)')}<span class="num">9:13</span></div>
  </div>`;
};

const header = (date, opts = {}) => `
  <div class="abs row" style="left:24px;right:16px;top:50px;height:40px;justify-content:space-between;z-index:5">
    <div class="row" style="gap:10px">${axisGlyph(26, { sw: 4.2, col: 'var(--fg)' })}<span class="mono fg2">${date}</span></div>
    ${capsule(opts)}
  </div>`;

// ------------------------------------------------------------------ 01/02 · Today (dark / light)
function today(theme) {
  const light = theme === 'light';
  const cx = 206, cy = 300, D = 272, R = D / 2, colX = Math.round(R * 0.618);
  const aura = light
    ? `<div class="abs" style="inset:0;background:radial-gradient(70% 38% at 50% 31%, rgba(14,122,106,.10), transparent 70%), radial-gradient(120% 50% at 50% -10%, #FFFFFF, transparent 70%)"></div>`
    : `<div class="abs" style="inset:0;background:radial-gradient(64% 34% at 50% 32%, rgba(125,227,209,.16), transparent 72%), radial-gradient(120% 40% at 50% -8%, rgba(160,190,210,.10), transparent 70%)"></div>`;
  const colLine = (x) => `<div class="abs vline" style="left:${x}px;top:${cy - R - 26}px;height:${D + 52}px"></div>
     <div class="abs" style="left:${x - 6}px;top:${cy - R - 27}px;width:13px;height:1px;background:var(--hair2)"></div>
     <div class="abs" style="left:${x - 6}px;top:${cy + R + 26}px;width:13px;height:1px;background:var(--hair2)"></div>`;
  const pulseY = cy + 84;
  const nodes = [
    ['07:00', 'Прогулка', 'done'], ['13:35', 'Фокус', 'now'], ['15:00', 'Питч', ''], ['18:00', 'Zone 2', ''], ['22:45', 'Отбой', 'moon'],
  ];
  const nx = (i) => 44 + i * 81;
  const ty = 742;
  const line = `
    <div class="abs" style="left:24px;right:24px;top:${ty}px;height:1px;background:var(--hair2)"></div>
    <div class="abs" style="left:24px;width:${nx(1) - 24}px;top:${ty}px;height:1px;background:linear-gradient(90deg,transparent,${light ? '#9A5B12' : 'var(--dawn)'})"></div>
    ${nodes.map(([t, l, s], i) => {
      const x = nx(i);
      const dot = s === 'done' ? `<div class="center" style="width:18px;height:18px;border-radius:9px;background:var(--mode);color:${light ? '#fff' : '#06201B'}">${icon('check', 12, 2.6)}</div>`
        : s === 'now' ? `<div style="width:14px;height:14px;border-radius:7px;background:var(--dawn);box-shadow:0 0 0 5px ${light ? 'rgba(154,91,18,.14)' : 'rgba(255,199,122,.16)'},0 0 18px ${light ? 'transparent' : 'rgba(255,199,122,.8)'}"></div>`
        : s === 'moon' ? `<div class="center" style="width:18px;height:18px;border-radius:9px;background:var(--bg);box-shadow:inset 0 0 0 1px var(--hair2);color:var(--berezhno)">${icon('moon', 11, 2)}</div>`
        : `<div style="width:9px;height:9px;border-radius:5px;background:var(--bg);box-shadow:inset 0 0 0 1.5px var(--fg3)"></div>`;
      return `<div class="abs col" style="left:${x - 40}px;width:80px;top:${ty - 34}px;align-items:center">
        <span class="mono ${s === 'now' ? '' : 'fg3'}" style="letter-spacing:.06em;${s === 'now' ? 'color:var(--dawn)' : ''}">${t}</span>
        <div class="center" style="height:24px;margin-top:9px">${dot}</div>
        <span class="l13 ${s === 'now' || s === 'done' ? '' : 'fg2'}" style="margin-top:9px">${l}</span></div>`;
    }).join('')}`;
  const body = `
  ${aura}
  ${statusBar('13:32')}
  ${header('ВТ · 10 НОЯБРЯ', { light })}
  ${colLine(cx - colX)}${colLine(cx + colX)}
  <div class="abs" style="left:${cx - R}px;top:${cy - R}px">${ring(D, 3, .44, 'var(--mode)', { glow: light ? false : 'rgba(125,227,209,.55)', grad: light ? ['#0E7A6A', '#2BA28F'] : ['#B9FFF2', '#5ED3BF'], dot: .5, dotCol: 'var(--fg2)', track: light ? 'rgba(13,15,18,.07)' : 'rgba(255,255,255,.07)' })}</div>
  <div class="abs" style="left:${cx - R + 14}px;top:${cy - R + 14}px">${ring(D - 28, 1, 1, 'transparent', { track: light ? 'rgba(13,15,18,.05)' : 'rgba(255,255,255,.04)' })}</div>
  <svg class="abs" style="left:0;top:${pulseY - 20}px" width="412" height="40" viewBox="0 ${pulseY - 20} 412 40">
    <defs><linearGradient id="pl" x1="0" x2="1"><stop offset="0" stop-color="var(--fg)" stop-opacity="0"/><stop offset=".25" stop-color="${light ? '#0D0F12' : '#fff'}" stop-opacity=".18"/><stop offset=".72" stop-color="${light ? '#0E7A6A' : '#7DE3D1'}" stop-opacity=".9"/><stop offset=".78" stop-color="${light ? '#0D0F12' : '#fff'}" stop-opacity=".12"/><stop offset="1" stop-opacity="0" stop-color="#fff"/></linearGradient></defs>
    <path d="${pulsePath(0, pulseY, 412, .9)}" fill="none" stroke="url(#pl)" stroke-width="1.3" stroke-linejoin="round"/>
  </svg>
  <div class="abs col" style="left:0;right:0;top:${cy - 78}px;align-items:center">
    <div style="color:var(--mode)">${modeGlyph('rovno', 30, 'currentColor', 1.5)}</div>
    <div class="d55" style="margin-top:6px">Ровно</div>
    <div class="row mono fg2" style="margin-top:12px;gap:10px"><span>Заряд <b class="num" style="color:var(--fg);font-weight:500">44</b></span><span class="fg3">/</span><span>норма 50</span></div>
  </div>
  <div class="abs b15 fg2" style="left:0;right:0;top:${cy + R + 40}px;text-align:center">Ночь короче обычной. Держим ровный темп.</div>
  <div class="abs glass row" style="left:16px;right:16px;top:566px;height:96px;border-radius:28px;padding:0 12px 0 22px;justify-content:space-between">
    <div class="col" style="gap:6px"><span class="mono" style="color:var(--dawn)">Сейчас · 13:35–14:20</span><span class="t21">Фокус: слайд с цифрами</span></div>
    <div class="btn light-pill" style="width:64px;height:64px;border-radius:32px">${icon('play', 22, 1.8)}</div>
  </div>
  ${line}
  ${nav('today', light)}`;
  return page(body, { theme });
}

// ------------------------------------------------------------------ 03 · Morning «Рассвет»
function morning() {
  const hz = 548;
  const stars = Array.from({ length: 26 }, (_, i) => {
    const x = (i * 157) % 412, y = 46 + ((i * 89) % 260), o = .15 + ((i * 37) % 50) / 100;
    return `<div class="abs" style="left:${x}px;top:${y}px;width:${i % 5 ? 1.5 : 2}px;height:${i % 5 ? 1.5 : 2}px;border-radius:2px;background:#fff;opacity:${o}"></div>`;
  }).join('');
  const body = `
  <div class="abs" style="inset:0;background:linear-gradient(180deg,#04050A 0%,#070B18 26%,#121735 44%,#3A2D4A 56%,#9A5E52 ${(hz - 30) / 9.15}%,#F0A866 ${hz / 9.15}%,#0B0A0C ${hz / 9.15 + .01}%,#08080A 100%)"></div>
  ${stars}
  <div class="abs" style="left:-60px;right:-60px;top:${hz - 260}px;height:520px;background:radial-gradient(50% 50% at 50% 50%, rgba(255,190,120,.55), rgba(255,150,90,.12) 45%, transparent 70%);"></div>
  <div class="abs" style="left:106px;top:${hz - 100}px;width:200px;height:100px;overflow:hidden">
    <div style="width:200px;height:200px;border-radius:100px;background:radial-gradient(circle at 50% 70%,#FFF4DC 0%,#FFD89A 40%,#FFB066 75%,#F08A4E 100%)"></div>
  </div>
  <div class="abs" style="left:62px;top:${hz - 214}px">${columnSil(214, 28, { rim: 'rgba(255,214,160,.75)' })}</div>
  <div class="abs" style="left:322px;top:${hz - 214}px;transform:scaleX(-1)">${columnSil(214, 28, { rim: 'rgba(255,214,160,.75)' })}</div>
  <div class="abs" style="left:0;right:0;top:${hz}px;height:1px;background:linear-gradient(90deg,transparent,rgba(255,226,180,.9),transparent)"></div>
  <div class="abs" style="left:156px;top:${hz + 1}px;width:100px;height:150px;background:linear-gradient(180deg,rgba(255,200,130,.28),transparent);filter:blur(10px)"></div>
  ${[[6,70,.5],[13,96,.32],[22,54,.26],[33,120,.16],[46,74,.12],[62,140,.07]].map(([y,w,o]) => `<div class="abs" style="left:${206 - w / 2}px;top:${hz + y}px;width:${w}px;height:1px;border-radius:1px;background:linear-gradient(90deg,transparent,rgba(255,220,170,${o}),transparent);filter:blur(.4px)"></div>`).join('')}
  <div style="color:#F3F1EC">${statusBar('6:52')}</div>
  <div class="abs col" style="left:0;right:0;top:92px;align-items:center;color:#F3F1EC">
    <span class="mono" style="color:rgba(243,241,236,.6)">Среда · 11 ноября</span>
    <div class="d89" style="margin-top:22px;font-size:96px;line-height:92px">Ровно</div>
    <div class="t21" style="margin-top:14px;color:rgba(243,241,236,.72)">Ночь в твоей норме.</div>
  </div>
  <div class="abs row" style="left:24px;right:24px;top:${hz + 70}px;justify-content:space-between;color:#F3F1EC">
    ${[['Сон', '7:12'], ['Заряд', '62'], ['Потолок', '12']].map(([l, v]) => `<div class="col" style="align-items:center;width:110px"><span class="d34 num" style="font-weight:200;font-size:40px;line-height:44px">${v}</span><span class="mono" style="margin-top:6px;color:rgba(243,241,236,.5)">${l}</span></div>`).join('<div style="width:1px;height:44px;background:rgba(255,255,255,.1)"></div>')}
  </div>
  <div class="abs" style="left:24px;right:24px;top:${hz + 196}px"><div class="btn light-pill" style="width:100%">Начать день ${icon('arrow', 18, 1.8)}</div></div>
  <div class="abs mono" style="left:0;right:0;top:${hz + 268}px;text-align:center;color:rgba(243,241,236,.42)">WHOOP · 06:41</div>
  <div class="gesture" style="background:#fff"></div>`;
  return page(body, { theme: 'dark' });
}

// ------------------------------------------------------------------ 04 · Workout confirmed by WHOOP («Печать»)
function workout() {
  const cy = 318, D = 252, R = D / 2;
  const rays = `<div class="abs" style="left:${206 - 230}px;top:${cy - 230}px;width:460px;height:460px;border-radius:50%;
     background:repeating-conic-gradient(from 0deg, rgba(125,227,209,.10) 0deg 1deg, transparent 1deg 7.5deg);
     -webkit-mask:radial-gradient(circle, transparent 46%, #000 52%, transparent 72%);mask:radial-gradient(circle, transparent 46%, #000 52%, transparent 72%)"></div>`;
  const zones = [['Z1', 22], ['Z2', 59], ['Z3', 14], ['Z4', 5], ['Z5', 0]];
  const body = `
  <div class="abs" style="inset:0;background:radial-gradient(70% 40% at 50% 34%, rgba(125,227,209,.20), transparent 70%)"></div>
  ${statusBar('18:48')}
  <div class="abs row" style="left:16px;right:24px;top:50px;height:44px;justify-content:space-between">
    <div class="center" style="width:44px;height:44px;border-radius:22px">${icon('close', 22, 1.6)}</div>
    <span class="mono fg2">18:06 — 18:47</span>
  </div>
  <div class="abs row" style="left:0;right:0;top:112px;justify-content:center">
    <div class="glass row" style="height:36px;border-radius:18px;padding:0 14px 0 6px;gap:8px">
      <div class="center" style="width:24px;height:24px;border-radius:12px;background:var(--mode);color:#06201B">${icon('check', 14, 2.6)}</div>
      <span class="mono" style="color:var(--fg)">WHOOP · подтверждено</span></div>
  </div>
  ${rays}
  <div class="abs" style="left:${206 - R}px;top:${cy - R}px">${ring(D, 5, 1, 'var(--mode)', { glow: 'rgba(125,227,209,.6)', grad: ['#D6FFF7', '#4FC9B4'] })}</div>
  <div class="abs" style="left:${206 - R + 18}px;top:${cy - R + 18}px">${ring(D - 36, 1, 1, 'transparent', { track: 'rgba(255,255,255,.06)' })}</div>
  <div class="abs col" style="left:0;right:0;top:${cy - 74}px;align-items:center">
    <span class="mono fg2">Zone 2</span>
    <div class="row" style="align-items:baseline;margin-top:8px"><span class="d89 num" style="font-size:104px;line-height:100px">41</span><span class="t21 fg2" style="margin-left:4px">мин</span></div>
    <span class="mono fg3" style="margin-top:8px">цель 40</span>
  </div>
  <div class="abs row" style="left:24px;right:24px;top:500px;justify-content:space-between">
    ${[['127', 'уд/мин'], ['81 %', 'в Z1–Z2'], ['8,4', 'нагрузка']].map(([v, l]) => `<div class="col" style="width:108px;align-items:center"><span class="d34 num" style="font-weight:200">${v}</span><span class="mono fg3" style="margin-top:6px">${l}</span></div>`).join('')}
  </div>
  <div class="abs" style="left:24px;right:24px;top:594px">
    <div class="row" style="gap:3px;height:6px">${zones.filter(([, w]) => w > 0).map(([z, w]) => `<div style="flex:${w};height:6px;border-radius:3px;background:${z === 'Z1' || z === 'Z2' ? (z === 'Z2' ? 'var(--mode)' : 'rgba(125,227,209,.45)') : 'rgba(255,255,255,.18)'}"></div>`).join('')}</div>
    <div class="row mono fg3" style="justify-content:space-between;margin-top:10px;letter-spacing:.08em">${zones.map(([z, w]) => `<span>${z} <span class="${z === 'Z2' ? '' : ''}" style="color:${z === 'Z2' ? 'var(--fg)' : ''}">${w}%</span></span>`).join('')}</div>
  </div>
  <div class="abs col" style="left:24px;right:24px;top:676px;align-items:center;gap:14px">
    <span class="mono fg3">Как ощущалось · по желанию</span>
    <div class="row" style="gap:8px"><span class="chip">Легко</span><span class="chip on">Рабоче</span><span class="chip">Тяжело</span></div>
  </div>
  <div class="abs" style="left:24px;right:24px;top:806px"><div class="btn light-pill" style="width:100%">Готово</div></div>
  <div class="gesture"></div>`;
  return page(body, { theme: 'dark' });
}

// ------------------------------------------------------------------ 05 · Evening in three taps
function evening() {
  const seg = (opts, on) => `<div class="row" style="gap:6px;margin-top:14px">${opts.map((o, i) => `<div class="center l13" style="flex:1;height:44px;border-radius:22px;${i === on ? 'background:rgba(168,163,255,.18);box-shadow:inset 0 0 0 1px rgba(168,163,255,.55);color:#fff' : 'box-shadow:inset 0 0 0 1px var(--hair2);color:var(--fg2)'}">${i === on ? icon('check', 14, 2.4) + '&nbsp;' : ''}${o}</div>`).join('')}</div>`;
  const card = (top, ic, title, inner, h, extra = '') => `<div class="abs glass" style="left:16px;right:16px;top:${top}px;height:${h}px;border-radius:26px;padding:18px 18px 0 20px;${extra}">
      <div class="row" style="gap:10px"><span class="fg2">${icon(ic, 20, 1.6)}</span><span class="b16" style="font-weight:500">${title}</span></div>${inner}</div>`;
  const dayLine = `<svg class="abs" style="left:0;top:178px" width="412" height="70" viewBox="0 0 412 70">
     <defs><linearGradient id="dl" x1="0" x2="1"><stop offset="0" stop-color="#A8A3FF" stop-opacity="0"/><stop offset=".15" stop-color="#A8A3FF" stop-opacity=".5"/><stop offset=".85" stop-color="#C9C6FF" stop-opacity="1"/><stop offset="1" stop-color="#A8A3FF" stop-opacity="0"/></linearGradient></defs>
     <path d="M0 52 C40 52 50 44 70 40 S110 22 128 30 S150 50 170 46 S200 34 220 38 S262 12 282 18 S306 44 330 44 S380 50 412 50" fill="none" stroke="url(#dl)" stroke-width="1.6" style="filter:drop-shadow(0 0 4px rgba(168,163,255,.6))"/>
     ${[[70, 40], [170, 46], [282, 18], [372, 48]].map(([x, y]) => `<circle cx="${x}" cy="${y}" r="3.2" fill="#0B0A14" stroke="#C9C6FF" stroke-width="1.4"/>`).join('')}
   </svg>`;
  const body = `
  <div class="abs" style="inset:0;background:radial-gradient(90% 40% at 50% 0%, rgba(120,110,230,.28), transparent 70%), linear-gradient(180deg,#0A0912,#060608 60%)"></div>
  ${statusBar('21:32')}
  <div class="abs row" style="left:24px;right:16px;top:50px;height:40px;justify-content:space-between">
    <span class="mono fg2">Вторник · вечер</span>
    <div class="capsule glass"><div class="seg">${ring(22, 2.6, .92, '#7DE3D1')}<span class="num">11,2</span></div><div class="seg">${ring(22, 2.6, 1, 'var(--fg)')}<span class="num">9,4k</span></div><div class="seg">${ring(22, 2.6, .9, 'var(--berezhno)', { glow: 'rgba(168,163,255,.6)' })}<span class="num">1:13</span></div></div>
  </div>
  <div class="abs" style="left:24px;top:112px"><div class="d55">Закрыть день</div></div>
  ${dayLine}
  <div class="abs row" style="left:24px;top:256px;gap:6px">${[1, 1, 0].map((f) => `<div style="width:${f ? 22 : 22}px;height:4px;border-radius:2px;background:${f ? '#C9C6FF' : 'rgba(255,255,255,.14)'}"></div>`).join('')}<span class="mono fg3" style="margin-left:8px">2 из 3</span></div>
  ${card(288, 'bolt', 'Тренировка', `<div class="abs row" style="right:18px;top:15px;gap:8px"><span class="l13 fg2">Zone 2 · 41′</span><div class="center" style="width:24px;height:24px;border-radius:12px;background:#7DE3D1;color:#06201B">${icon('check', 14, 2.6)}</div></div><div class="mono fg3" style="margin-top:8px;margin-left:30px">отметил WHOOP</div>`, 84)}
  ${card(386, 'plate', 'Еда', seg(['По тарелке', 'Частично', 'Просто поел'], 0), 120)}
  ${card(520, 'spark', 'Шаг к цели', seg(['Сделал', 'Частично', 'Нет'], 0), 120)}
  ${card(654, 'quote', 'План помог сегодня?', seg(['Да', 'Не очень'], -1), 120, 'box-shadow:var(--glass-edge),0 0 0 1px rgba(168,163,255,.35),0 0 40px -10px rgba(168,163,255,.45)')}
  <div class="abs mono fg3" style="left:0;right:0;top:806px;text-align:center">последний тап закроет день</div>
  <div class="gesture"></div>`;
  return page(body, { theme: 'dark', extraCss: '.screen{--mode:#A8A3FF}' });
}

// ------------------------------------------------------------------ 06 · Mentor chat
function mentor() {
  const avatar = `<div class="center" style="width:44px;height:44px;border-radius:22px;background:radial-gradient(circle at 50% 35%,#1E2328,#0E1013);box-shadow:inset 0 0 0 1px var(--hair2);position:relative">
     ${axisGlyph(30, { sw: 4, col: '#F3F1EC', pulseCol: '#FFC77A' })}
     <div class="abs" style="right:1px;bottom:1px;width:11px;height:11px;border-radius:6px;background:var(--mode);box-shadow:0 0 0 2.5px var(--bg),0 0 8px rgba(125,227,209,.8)"></div></div>`;
  const vince = (top, text, toks = '') => `<div class="abs" style="left:24px;right:56px;top:${top}px"><div class="b16" style="line-height:24px">${text}</div>${toks ? `<div class="row" style="gap:6px;margin-top:12px;flex-wrap:wrap">${toks}</div>` : ''}</div>`;
  const tok = (t, c = '') => `<span class="tok">${c ? `<span style="width:6px;height:6px;border-radius:3px;background:${c}"></span>` : ''}${t}</span>`;
  const body = `
  <div class="abs" style="inset:0;background:radial-gradient(80% 30% at 50% -5%, rgba(255,199,122,.10), transparent 70%)"></div>
  ${statusBar('13:07')}
  <div class="abs row" style="left:20px;right:12px;top:52px;height:52px;justify-content:space-between">
    <div class="row" style="gap:12px">${avatar}<div class="col"><span class="t21">Винс</span><span class="mono fg3" style="font-size:10px">ИИ · слушает</span></div></div>
    <div class="center fg2" style="width:48px;height:48px">${icon('dots', 22)}</div>
  </div>
  <div class="abs mono fg3" style="left:0;right:0;top:132px;text-align:center">Сегодня · 13:05</div>
  ${vince(170, 'Заряд ниже нормы, а в 15:00 питч. Давай без героизма: что в нём главное?', tok('сон 5:50', '#A8A3FF') + tok('заряд 44', '#7DE3D1') + tok('питч 15:00', '#FFC77A'))}
  <div class="abs glass b16" style="right:16px;top:300px;max-width:262px;border-radius:22px 22px 6px 22px;padding:14px 18px;line-height:23px">Цифры по юнит-экономике. Остальное — словами.</div>
  ${vince(406, 'Тогда так: до 14:20 — только слайд с цифрами, без почты. В 14:45 — три минуты выдоха.')}
  <div class="abs glass" style="left:16px;right:16px;top:504px;border-radius:24px;padding:6px">
    ${[['13:35', 'Фокус до 14:20', 'Поставить'], ['14:45', 'Выдох · 3 мин', 'Напомнить']].map(([t, l, a], i) => `<div class="row" style="height:56px;padding:0 8px 0 14px;justify-content:space-between;${i ? 'border-top:1px solid var(--hair)' : ''}">
      <div class="row" style="gap:14px"><span class="mono" style="color:var(--dawn);letter-spacing:.06em">${t}</span><span class="b15">${l}</span></div>
      <div class="center l13" style="height:40px;padding:0 16px;border-radius:20px;${i ? 'box-shadow:inset 0 0 0 1px var(--hair2)' : 'background:var(--dawn-fill);color:var(--on-dawn)'}">${a}</div></div>`).join('')}
  </div>
  <div class="abs row mono fg3" style="left:24px;top:648px;gap:8px">${icon('spark', 14, 1.6)}<span>записал: напряжение · работа</span></div>
  <div class="abs row" style="left:16px;right:16px;top:740px;gap:10px">
    <div class="glass row" style="flex:1;height:56px;border-radius:28px;padding:0 22px"><span class="b16 fg3">Сказать Винсу…</span></div>
    <div class="btn light-pill" style="width:56px;height:56px">${icon('mic', 22, 1.8)}</div>
  </div>
  ${nav('mentor')}`;
  return page(body, { theme: 'dark' });
}

// ------------------------------------------------------------------ 07 · Connect WHOOP (onboarding 2/3)
function connect() {
  const src = (top, ic, name, toks, state) => `<div class="abs glass row" style="left:16px;right:16px;top:${top}px;height:84px;border-radius:26px;padding:0 16px 0 18px;justify-content:space-between">
    <div class="row" style="gap:14px"><div class="center" style="width:48px;height:48px;border-radius:24px;background:rgba(255,255,255,.05);box-shadow:inset 0 0 0 1px var(--hair2)">${icon(ic, 22, 1.5)}</div>
    <div class="col" style="gap:5px"><span class="t21" style="font-size:19px">${name}</span><span class="mono fg3" style="font-size:10px">${toks}</span></div></div>${state}</div>`;
  const body = `
  <div class="abs" style="inset:0;background:radial-gradient(60% 30% at 50% 24%, rgba(255,190,110,.20), transparent 70%)"></div>
  ${statusBar('9:41')}
  <div class="abs row" style="left:24px;right:24px;top:56px;gap:6px">${[1, 1, 0].map((f) => `<div style="flex:1;height:3px;border-radius:2px;background:${f ? 'var(--fg)' : 'rgba(255,255,255,.14)'}"></div>`).join('')}</div>
  <div class="abs" style="left:91px;top:104px;width:230px;height:230px;border-radius:50%;overflow:hidden;box-shadow:0 0 0 1px rgba(255,255,255,.05),0 0 80px -10px rgba(226,168,71,.35)"><img src="mark-full.svg" style="width:250px;height:250px;margin:-10px"></div>
  
  <div class="abs col" style="left:24px;right:24px;top:400px;align-items:center;text-align:center">
    <div class="d34" style="font-size:38px;line-height:42px;font-weight:200">Данные придут сами</div>
    <div class="b15 fg2" style="margin-top:12px;max-width:300px">Без анкет. Сон, восстановление и тренировки — из WHOOP.</div>
  </div>
  ${src(544, 'band', 'WHOOP', 'сон · восстановление', `<div class="center" style="width:44px;height:44px;border-radius:22px;box-shadow:inset 0 0 0 1px rgba(255,199,122,.5);color:var(--dawn)">${icon('arrow', 18, 1.8)}</div>`)}
  ${src(640, 'watch', 'Pixel Watch', 'шаги · пульс', `<div class="center" style="width:32px;height:32px;border-radius:16px;background:var(--mode);color:#06201B">${icon('check', 16, 2.6)}</div>`)}
  <div class="abs" style="left:24px;right:24px;top:776px"><div class="btn light-pill" style="width:100%">Подключить WHOOP</div></div>
  <div class="abs row mono fg3" style="left:0;right:0;top:850px;justify-content:center;gap:8px">${icon('lock', 13, 1.6)}<span>вход через WHOOP · пароль не видим</span></div>
  <div class="gesture"></div>`;
  return page(body, { theme: 'dark' });
}

// ------------------------------------------------------------------ sync storyboard (wow 1)
function storyboard() {
  const FW = 300, FH = 420;
  const frame = (n, t, title, inner, note) => `<div class="col" style="width:${FW}px;gap:16px">
    <div style="position:relative;width:${FW}px;height:${FH}px;border-radius:34px;overflow:hidden;background:radial-gradient(70% 50% at 50% 42%, rgba(125,227,209,${n === 4 ? .16 : .07}), transparent 70%),#07080A;box-shadow:inset 0 0 0 1px rgba(255,255,255,.07)">
      <div class="abs mono" style="left:20px;top:18px;color:rgba(243,241,236,.45)">${String(n).padStart(2, '0')}</div>
      <div class="abs mono" style="right:20px;top:18px;color:#FFC77A">${t}</div>
      ${inner}
    </div>
    <div class="col" style="gap:6px;padding:0 6px"><span style="font-size:17px;font-weight:500;color:#F3F1EC">${title}</span><span style="font-size:13.5px;line-height:19px;color:#A3A7AE">${note}</span></div></div>`;
  const G = 190, gx = (FW - G) / 2, gy = 100;
  const glyph = (o) => `<div class="abs" style="left:${gx}px;top:${gy}px">${axisGlyph(G, { sw: 1.6, col: '#F3F1EC', ...o })}</div>`;
  const hdr = `<div class="abs row" style="left:0;right:0;top:${gy + 236}px;justify-content:center"><div class="capsule glass" style="color:#F3F1EC">
     <div class="seg">${ring(22, 2.6, .7, '#7DE3D1', { glow: 'rgba(125,227,209,.5)' })}<span class="num">8,4</span></div>
     <div class="seg">${ring(22, 2.6, .76, '#F3F1EC')}<span class="num">6,8k</span></div>
     <div class="seg">${ring(22, 2.6, .62, '#A8A3FF')}<span class="num">9:13</span></div></div></div>`;
  const frames = [
    frame(1, '0 мс', 'Тишина', glyph({ progress: 1, circleOpacity: .18, cols: 0, colOpacity: .25, pulse: false }), 'Потянул вниз или открыл утром — на месте шапки проступает круг знака, 18 %.'),
    frame(2, '250 мс', 'Колонны встают', glyph({ progress: 1, circleOpacity: .22, cols: .7, colOpacity: 1, pulse: false }), 'Две колонны вырастают от базы — пружина, без перелёта. Это «запрос ушёл».'),
    frame(3, '600 мс', 'Пульс проходит', glyph({ progress: 1, circleOpacity: .22, cols: 1, pulseT: .62, pulseCol: '#FFC77A', head: [60.5, 57], glow: false }) + `<div class="abs mono" style="left:0;right:0;top:${gy + 236}px;text-align:center;color:#FFC77A">• тик</div>`, 'Данные приходят — свет бежит по пульсу; на каждом пике лёгкий тактильный тик.'),
    frame(4, '900 мс', 'Круг замыкается', glyph({ progress: .82, cols: 1, pulseT: 1, pulseCol: '#7DE3D1', col: '#F3F1EC', glow: 'rgba(125,227,209,.55)' }), 'Круг дорисовывается по мере прихода данных — это и есть прогресс.'),
    frame(5, '1300 мс', 'Три кольца', `<div class="abs" style="left:${gx}px;top:${gy}px;opacity:.16">${axisGlyph(G, { sw: 1.4, col: '#F3F1EC' })}</div>` + hdr + `<div class="abs mono" style="left:0;right:0;top:${gy + 300}px;text-align:center;color:rgba(243,241,236,.45)">WHOOP · 06:41</div>`, 'Круг делится на три кольца шапки, числа докатываются. Знак гаснет до следующего раза.'),
  ];
  const W = 5 * FW + 4 * 28 + 2 * 64;
  const body = `<div style="width:${W}px;height:760px;background:#030304;padding:56px 64px;font-family:var(--sans);color:#F3F1EC;position:relative">
    <div class="row" style="justify-content:space-between;align-items:flex-end;margin-bottom:36px">
      <div class="col" style="gap:10px"><span class="mono" style="color:#FFC77A">Вау-момент 1 · синхронизация</span><span style="font-size:44px;line-height:46px;font-weight:200;letter-spacing:-.04em">Знак оживает, когда приходят данные</span></div>
      <span class="mono" style="color:rgba(243,241,236,.45);text-align:right;line-height:18px">1,3 с · пружины · тактильный тик на пиках<br>«удалить анимацию» → сразу кадр 05</span></div>
    <div class="row" style="gap:28px;align-items:flex-start">${frames.join('')}</div></div>`;
  return { html: page(body, { wrap: false, w: W, h: 760 }), w: W, h: 760 };
}

// ------------------------------------------------------------------ concept sheet
function concept() {
  const W = 2400, H = 2400;
  const sw = (c, n, hex, dark = false) => `<div class="col" style="gap:10px;width:132px"><div style="height:96px;border-radius:24px;background:${c};box-shadow:inset 0 0 0 1px rgba(255,255,255,.08)"></div><span style="font-size:15px;font-weight:500">${n}</span><span class="mono" style="color:#80858D;letter-spacing:.06em">${hex}</span></div>`;
  const shot = (f, label, w = 380) => `<div class="col" style="gap:14px;align-items:center"><img src="file://${OUT}/${f}" style="width:${w}px;border-radius:44px;box-shadow:0 0 0 1px rgba(255,255,255,.10),0 40px 80px -30px rgba(0,0,0,.9)"><span class="mono" style="color:#80858D">${label}</span></div>`;
  const feat = (ic, t, d) => `<div class="row" style="gap:16px;align-items:flex-start"><div class="center" style="flex:none;width:44px;height:44px;border-radius:22px;background:rgba(255,255,255,.05);box-shadow:inset 0 0 0 1px rgba(255,255,255,.1);color:#FFC77A">${icon(ic, 20, 1.6)}</div><div class="col" style="gap:4px"><span style="font-size:17px;font-weight:500">${t}</span><span style="font-size:14px;line-height:19px;color:#A3A7AE">${d}</span></div></div>`;
  const feats = [
    ['ripple', 'Синхронизация знаком', 'колонны, пульс, круг → три кольца'],
    ['eye', 'Шапка из трёх колец', 'нагрузка, шаги, до отбоя — без SOS'],
    ['sun', 'Рассвет', 'утро открывается солнцем между колонн'],
    ['check', 'Печать WHOOP', 'кольцо замыкается, двойной тик'],
    ['quote', 'Вечер в 3 тапа', 'последний тап сам закрывает день'],
    ['roll', 'Цифры докатываются', 'табличные цифры, без прыжков'],
    ['layers', 'Аура режима', 'свет экрана окрашен режимом дня'],
    ['spark', 'Данные-цитаты', 'Винс ссылается на сон и заряд'],
    ['dim', 'Сумерки к отбою', 'после 21:00 интерфейс теплеет и тускнеет'],
    ['haptic', 'Тактильный пульс', 'выдох ведёт вибрация, можно не смотреть'],
    ['widget', 'Живой виджет', 'кольца и следующий шаг на главном'],
    ['wand', 'Норма — коридор света', 'твоя полоса нормы, не чужие проценты'],
  ];
  const body = `<div style="width:${W}px;height:${H}px;background:radial-gradient(60% 30% at 20% 0%, rgba(255,199,122,.10), transparent 60%),radial-gradient(50% 30% at 90% 30%, rgba(125,227,209,.07), transparent 60%),#040405;color:#F3F1EC;font-family:var(--sans);position:relative;padding:96px 110px">
    <div class="row" style="justify-content:space-between;align-items:flex-start">
      <div class="col" style="gap:22px">
        <span class="mono" style="color:#FFC77A;font-size:14px">Health of a Titan · концепция v3 · A37</span>
        <div style="font-size:180px;line-height:160px;font-weight:200;letter-spacing:-.06em">Ось</div>
        <div style="font-size:34px;line-height:44px;font-weight:300;letter-spacing:-.02em;color:#A3A7AE;max-width:1000px">Титан, свет и воздух. Античность — в пропорциях и одном жесте: две колонны держат твой день, пульс проходит сквозь.</div>
      </div>
      <div class="col" style="gap:20px;align-items:flex-end;margin-top:20px">
        <div class="row" style="gap:48px;align-items:center">
          <div class="col" style="gap:12px;align-items:center"><div style="width:240px;height:240px;border-radius:50%;overflow:hidden;box-shadow:0 0 70px -10px rgba(226,168,71,.35)"><img src="mark-full.svg" style="width:262px;height:262px;margin:-11px"></div><span class="mono" style="color:#80858D">знак v2 · без изменений</span></div>
          <div class="col" style="gap:12px;align-items:center"><div class="center" style="width:240px;height:240px">${axisGlyph(200, { sw: 1.8, col: '#F3F1EC', pulseCol: '#FFC77A' })}</div><span class="mono" style="color:#80858D">глиф «Ось» · для интерфейса</span></div>
        </div>
        <div class="row" style="gap:40px;margin-top:10px;align-items:baseline">
          <span style="font-family:'Cormorant SC';font-weight:600;font-size:34px;letter-spacing:.2em">HEALTH OF A TITAN</span>
          <span class="mono" style="font-size:16px;letter-spacing:.42em;color:#F3F1EC">HEALTH OF A TITAN</span>
        </div>
        <span class="mono" style="color:#80858D">вордмарк: антиква — стор и сплэш · моно-разрядка — в приложении</span>
      </div>
    </div>

    <div class="row" style="margin-top:80px;gap:0;align-items:center">
      ${[['06:52', 'Утро'], ['13:32', 'Режим и план'], ['18:48', 'Тренировка'], ['21:32', 'Вечер · 3 тапа'], ['день', 'Светлая тема']].map(([t, l], i) => `<div class="row" style="width:${i < 4 ? 424 : 380}px;gap:12px"><span class="mono" style="color:#FFC77A">${t}</span><span style="font-size:16px;font-weight:500">${l}</span>${i < 3 ? '<div style="flex:1;height:1px;margin:0 22px 0 6px;background:linear-gradient(90deg,rgba(255,199,122,.5),rgba(255,255,255,.08))"></div>' : ''}</div>`).join('')}
    </div>
    <div class="row" style="gap:44px;margin-top:26px;justify-content:space-between;align-items:flex-end">
      ${shot('s1-morning.png', '01 · Рассвет')}
      ${shot('s2-today-dark.png', '02 · Сегодня')}
      ${shot('s4-workout-whoop.png', '04 · Печать WHOOP')}
      ${shot('s5-evening.png', '05 · Вечер')}
      ${shot('s3-today-light.png', '03 · Сегодня · день')}
    </div>

    <div class="row" style="gap:90px;margin-top:96px;align-items:flex-start">
      <div class="col" style="gap:26px;width:820px">
        <span class="mono" style="color:#FFC77A">Палитра · титан и свет</span>
        <div class="row" style="gap:20px">${sw('#060708', 'Глубина', '#060708')}${sw('#15181C', 'Титан', '#15181C')}${sw('linear-gradient(180deg,rgba(255,255,255,.10),rgba(255,255,255,.03)),#0E1013', 'Стекло', 'white 7 %')}${sw('#F3F1EC', 'Кость', '#F3F1EC')}${sw('linear-gradient(180deg,#FFE1AE,#FFC77A 50%,#FFB05E)', 'Рассвет', '#FFC77A')}</div>
        <div class="row" style="gap:20px">${sw('#FFB067', 'Разгон · солнце', '#FFB067')}${sw('#7DE3D1', 'Ровно · лёд', '#7DE3D1')}${sw('#A8A3FF', 'Бережно · ирис', '#A8A3FF')}${sw('#F3F2EF', 'Фарфор', '#F3F2EF')}${sw('#D6A964', 'Золото знака', '#D6A964')}</div>
        <span class="mono" style="color:#FFC77A;margin-top:30px">Шрифт · Geist + Geist Mono (OFL)</span>
        <div class="row" style="gap:40px;align-items:flex-end">
          <span style="font-size:144px;line-height:130px;font-weight:200;letter-spacing:-.06em">44</span>
          <div class="col" style="gap:10px"><span style="font-size:55px;line-height:56px;font-weight:200;letter-spacing:-.045em">Ровно</span><span style="font-size:21px;font-weight:400">Фокус: слайд с цифрами</span><span class="mono" style="color:#A3A7AE">Заряд 44 / норма 50 · WHOOP 06:41</span></div>
        </div>
        <span class="mono" style="color:#80858D;line-height:20px">шкала Фибоначчи: 89 · 55 · 34 · 21 · 16 · 13 · 11 &nbsp;·&nbsp; поля 24 · сетка 8 &nbsp;·&nbsp; герой = 61,8 % первого экрана</span>
      </div>
      <div class="col" style="gap:26px;flex:1">
        <span class="mono" style="color:#FFC77A">12 фишек</span>
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:28px 40px">${feats.map((f) => feat(...f)).join('')}</div>
        <span class="mono" style="color:#FFC77A;margin-top:24px">3 вау-момента</span>
        <div class="row" style="gap:20px">${[['Знак оживает', 'синхронизация: колонны, пульс, круг делится на три кольца'], ['Рассвет', 'первое открытие дня: солнце встаёт между колонн, появляется режим'], ['Печать', 'WHOOP подтвердил — кольцо замыкается, свет расходится лучами']].map(([t, d]) => `<div class="glass col" style="flex:1;border-radius:28px;padding:24px;gap:8px"><span style="font-size:24px;font-weight:300;letter-spacing:-.02em">${t}</span><span style="font-size:14px;line-height:20px;color:#A3A7AE">${d}</span></div>`).join('')}</div>
      </div>
    </div>
  </div>`;
  return { html: page(body, { wrap: false, w: W, h: H }), w: W, h: H };
}

// ------------------------------------------------------------------ render
(async () => {
  const { chromium } = require('playwright');
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 412, height: 915 }, deviceScaleFactor: 2 });
  const shot = async (name, html, w = 412, h = 915, scale = 2) => {
    const f = path.join(HTML, `${name}.html`);
    fs.writeFileSync(f, html);
    const p = await (scale === 2 && w === 412 ? ctx : await browser.newContext({ viewport: { width: w, height: h }, deviceScaleFactor: scale })).newPage();
    await p.setViewportSize({ width: w, height: h });
    await p.goto('file://' + f);
    await p.evaluate(() => document.fonts.ready);
    await p.waitForTimeout(300);
    await p.screenshot({ path: path.join(OUT, `${name}.png`) });
    await p.close();
    console.log('✓', name);
  };
  await shot('s1-morning', morning());
  await shot('s2-today-dark', today('dark'));
  await shot('s3-today-light', today('light'));
  await shot('s4-workout-whoop', workout());
  await shot('s5-evening', evening());
  await shot('s6-mentor', mentor());
  await shot('s7-connect-whoop', connect());
  const sb = storyboard();
  await shot('_sync-storyboard', sb.html, sb.w, sb.h, 1.5);
  // concept sheet references the PNGs rendered above; copy them next to the html dir
  const c = concept();
  await shot('_concept', c.html, c.w, c.h, 1);
  await browser.close();
})();
