// Builds the HoT concept v3 «Титаниум» mockups (A37 v0.2) as HTML and renders them to PNG with Playwright.
// Usage (from repo root):
//   python3 docs/design/concept-v3/src/getfonts.py   # once, downloads OFL fonts (Geist, Geist Mono, Cormorant SC)
//   node docs/design/concept-v3/src/build.js [outDir]
// Output: docs/design/concept-v3/s1…s9 (412x915 @2x), _icons.png, _sync-titan-storyboard.png, _concept.png
const fs = require('fs');
const path = require('path');
const { pulsePath, ring, icon, picto } = require('./art');
const { DEFS, titanScene, titanStencil, titanZones } = require('./titan');

const SRC = __dirname;
const OUT = path.resolve(process.argv[2] || path.resolve(SRC, '..'));
const HTML = path.join(SRC, 'html');
fs.mkdirSync(HTML, { recursive: true });
const MARK = path.resolve(SRC, '../../../../brand/mark-v2/master/hot-master.svg');
fs.copyFileSync(MARK, path.join(HTML, 'mark-full.svg'));

const AQUA = '#7DE3D1', DAWN = '#FFC77A', IRIS = '#A8A3FF', SUN = '#FFB067';
const LIGHT = ['#FFFFFF', '#FFF1D6', '#FFD08A'];
const ICE = ['#FFFFFF', '#DFFBF6', '#9FEADC'];

const page = (body, { theme = 'dark', extraCss = '', w = 412, h = 915, wrap = true } = {}) => `<!doctype html>
<html lang="ru"><head><meta charset="utf-8"><title>HoT · Титаниум</title>
<link rel="stylesheet" href="../fonts/local.css"><link rel="stylesheet" href="../styles.css">
<style>html,body{width:${w}px;height:${h}px;overflow:hidden}${extraCss}</style></head>
<body>${DEFS}${wrap ? `<div class="screen ${theme}">${body}</div>` : body}</body></html>`;

const statusBar = (time, color = '') => `<div class="statusbar" ${color ? `style="color:${color}"` : ''}><span class="num">${time}</span><span class="icons">
  <svg width="16" height="12" viewBox="0 0 16 12" fill="currentColor"><path d="M15 1v10H1z"/></svg>
  ${icon('wifi', 17, 2)}
  <svg width="24" height="12" viewBox="0 0 24 12"><rect x=".6" y=".6" width="20.5" height="10.8" rx="3.4" fill="none" stroke="currentColor" stroke-width="1.2" opacity=".5"/><rect x="2.4" y="2.4" width="13.5" height="7.2" rx="1.8" fill="currentColor"/><rect x="22" y="4" width="1.6" height="4" rx=".8" fill="currentColor" opacity=".5"/></svg>
  </span></div>`;

const nav = (active) => {
  const items = [['today', 'Сегодня'], ['mentor', 'Винс'], ['week', 'Неделя'], ['me', 'Я']];
  return `<div class="nav glass">${items.map(([k, l]) => `<div class="it ${k === active ? 'on' : ''}">${k === 'me' ? picto('body', 22, 1.5) : icon(k, 22, 1.6)}${k === active ? `<span>${l}</span>` : ''}</div>`).join('')}</div><div class="gesture"></div>`;
};

// Status capsule (D-029): strain vs mode ceiling · steps vs goal · time to lights-out. Icons carry the meaning.
const capsule = ({ light = false, v = ['8,4', '6,8k', '9:13'], p = [.7, .76, .62], glow = 0 } = {}) => `<div class="capsule glass">
    <div class="seg">${ring(22, 2.6, p[0], 'var(--mode)', { glow: light || glow !== 0 ? false : 'rgba(125,227,209,.45)' })}<span class="num">${v[0]}</span></div>
    <div class="seg">${ring(22, 2.6, p[1], 'var(--fg)')}<span class="num">${v[1]}</span></div>
    <div class="seg">${ring(22, 2.6, p[2], 'var(--berezhno)', { glow: glow === 2 ? 'rgba(168,163,255,.6)' : false })}<span class="num">${v[2]}</span></div>
  </div>`;
const brandDot = () => `<div class="center" style="width:32px;height:32px;border-radius:16px;box-shadow:inset 0 0 0 1px var(--hair2)">${picto('body', 20, 1.5)}</div>`;
const header = (date, opts = {}) => `
  <div class="abs row" style="left:20px;right:16px;top:50px;height:40px;justify-content:space-between;z-index:5">
    <div class="row" style="gap:10px">${brandDot()}<span class="mono fg2">${date}</span></div>
    ${capsule(opts)}
  </div>`;
const tile = (name, { size = 40, col = 'var(--fg)', bg = 'rgba(255,255,255,.05)', ring: r = 'var(--hair2)', ic = 22 } = {}) =>
  `<div class="center" style="flex:none;width:${size}px;height:${size}px;border-radius:${size / 2}px;background:${bg};box-shadow:inset 0 0 0 1px ${r};color:${col}">${picto(name, ic, 1.5)}</div>`;

// mark circle r=940 inside a 2048 box: px size of a scene whose circle has radius r
const sceneSize = (r) => Math.round(r * 2048 / 940);

// ------------------------------------------------------------------ Today (dark / light)
function today(theme) {
  const light = theme === 'light';
  const cx = 206, cy = 292, D = 296, R = D / 2;
  const S = sceneSize(R * .9);
  const aura = light
    ? `<div class="abs" style="inset:0;background:radial-gradient(64% 34% at 50% 31%, rgba(14,122,106,.10), transparent 70%), radial-gradient(120% 50% at 50% -10%, #FFFFFF, transparent 70%)"></div>`
    : `<div class="abs" style="inset:0;background:radial-gradient(60% 32% at 50% 31%, rgba(125,227,209,.17), transparent 72%), radial-gradient(120% 40% at 50% -8%, rgba(160,190,210,.08), transparent 70%)"></div>`;
  const th = (44 / 100) * 2 * Math.PI - Math.PI / 2;
  const px = cx + R * Math.cos(th), py = cy + R * Math.sin(th);
  const scene = titanScene(S, light
    ? { light: ['#0D0F12', '#20252B', '#3A3F46'], colLight: ['#8C939C', '#A7AEB6', '#B9BEC5'], knock: 'rgba(243,242,239,.96)', glow: false }
    : { light: ICE, colLight: ['#C9D2DB', '#8E98A4', '#5D6672'], knock: 'rgba(7,8,10,.94)', glow: 'rgba(125,227,209,.45)' });
  const nodes = [['07:00', 'body', 'done'], ['13:35', 'mind', 'now'], ['15:00', 'work', ''], ['18:00', 'body', ''], ['22:45', 'sleep', 'moon']];
  const nx = (i) => 44 + i * 81, ty = 760;
  const line = `
    <div class="abs" style="left:24px;right:24px;top:${ty}px;height:1px;background:var(--hair2)"></div>
    <div class="abs" style="left:24px;width:${nx(1) - 24}px;top:${ty}px;height:1px;background:linear-gradient(90deg,transparent,${light ? '#9A5B12' : DAWN})"></div>
    ${nodes.map(([t, p, s], i) => {
      const x = nx(i);
      const st = s === 'done' ? `background:var(--mode);color:${light ? '#fff' : '#06201B'};box-shadow:none`
        : s === 'now' ? `background:var(--dawn-fill);color:var(--on-dawn);box-shadow:0 0 0 5px ${light ? 'rgba(154,91,18,.12)' : 'rgba(255,199,122,.14)'},0 0 22px ${light ? 'transparent' : 'rgba(255,199,122,.7)'}`
        : s === 'moon' ? `background:var(--bg);color:var(--berezhno);box-shadow:inset 0 0 0 1px var(--hair2)`
        : `background:var(--bg);color:var(--fg2);box-shadow:inset 0 0 0 1px var(--hair2)`;
      return `<div class="abs col" style="left:${x - 40}px;width:80px;top:${ty - 46}px;align-items:center">
        <span class="mono ${s === 'now' ? '' : 'fg3'}" style="letter-spacing:.06em;${s === 'now' ? 'color:var(--dawn)' : ''}">${t}</span>
        <div class="center" style="margin-top:8px;width:${s === 'now' ? 40 : 36}px;height:${s === 'now' ? 40 : 36}px;border-radius:20px;${st}">${picto(p, s === 'now' ? 24 : 22, 1.5)}</div></div>`;
    }).join('')}`;
  const body = `
  ${aura}
  ${statusBar('13:32')}
  ${header('ВТ · 10 НОЯБРЯ', { light })}
  <div class="abs" style="left:${cx - R}px;top:${cy - R}px">${ring(D, 3, .44, 'var(--mode)', { glow: light ? false : 'rgba(125,227,209,.55)', grad: light ? ['#0E7A6A', '#2BA28F'] : ['#B9FFF2', '#5ED3BF'], dot: .5, dotCol: 'var(--fg2)', track: light ? 'rgba(13,15,18,.07)' : 'rgba(255,255,255,.07)' })}</div>
  <div class="abs" style="left:${cx - S / 2}px;top:${cy - S / 2}px">${scene}</div>
  <div class="abs center num" style="left:${px - 22}px;top:${py - 14}px;width:44px;height:28px;border-radius:14px;font-size:14px;font-weight:600;background:var(--mode);color:${light ? '#fff' : '#06201B'};${light ? '' : 'box-shadow:0 0 16px rgba(125,227,209,.7)'}">44</div>
  <div class="abs row" style="left:0;right:0;top:${cy + R + 22}px;justify-content:center;gap:12px;color:var(--mode)">${picto('rovno', 34, 1.4)}<span class="d34" style="font-size:40px;line-height:44px;font-weight:200;color:var(--fg)">Ровно</span></div>
  <div class="abs row" style="left:0;right:0;top:${cy + R + 80}px;justify-content:center;gap:8px">
    <span class="tok" style="height:30px;padding:0 12px;font-size:12px">${picto('sleep', 16, 1.6)} 5:50 ↓</span>
    <span class="tok" style="height:30px;padding:0 12px;font-size:12px">${picto('heart', 16, 1.6)} 52 =</span>
    <span class="tok" style="height:30px;padding:0 12px;font-size:12px">${picto('ceiling', 16, 1.6)} 12</span>
  </div>
  <div class="abs glass row" style="left:16px;right:16px;top:588px;height:88px;border-radius:28px;padding:0 12px 0 14px;justify-content:space-between">
    <div class="row" style="gap:14px">${tile('mind', { size: 56, ic: 28, col: 'var(--dawn)', ring: light ? 'rgba(154,91,18,.25)' : 'rgba(255,199,122,.3)', bg: light ? 'rgba(154,91,18,.06)' : 'rgba(255,199,122,.07)' })}
      <div class="col" style="gap:5px"><span class="mono" style="color:var(--dawn)">13:35–14:20</span><span class="t21">Слайд с цифрами</span></div></div>
    <div class="btn light-pill" style="width:60px;height:60px;border-radius:30px">${icon('play', 22, 1.8)}</div>
  </div>
  ${line}
  ${nav('today')}`;
  return page(body, { theme });
}

// ------------------------------------------------------------------ Morning «Рассвет»: the sun rises as a ring around the Titan, haiku of the state
// Per mode: the Titan's light (saturation, glow), the sun's height and the sky change; the haiku names the state and turns the mood.
const DAWNS = {
  razgon: {
    word: 'Разгон', col: SUN, sunUp: 1.02, haiku: ['Выспался сполна.', 'Свет стоит за плечами.', 'Бери высоту.'],
    sky: ['#05060C', '#0D1430', '#2A2E5A', '#8A5A6A', '#F2A864', '#FFD08A'], light: ['#FFFFFF', '#FFE2B0', '#FFC070'], glow: 'rgba(255,184,100,.85)',
    ringCol: '#FFE6B8', aura: .7, cols: ['#FFF0D8', '#D9AE84', '#9A7356'], vals: [['sleep', '8:05', IRIS], ['charge', '78', AQUA], ['ceiling', '16', SUN]],
  },
  rovno: {
    word: 'Ровно', col: AQUA, sunUp: .70, haiku: ['Ночь чуть короче.', 'Тело дышит спокойно.', 'Ровный шаг — и в путь.'],
    sky: ['#04050A', '#070B18', '#121735', '#3A2D4A', '#9A5E52', '#F0A866'], light: ['#FFF6E6', '#FFD9A0', '#FFB870'], glow: 'rgba(255,190,110,.75)',
    ringCol: '#FFE2B0', aura: .55, cols: ['#FFE7C4', '#C9A07A', '#8A6A55'], vals: [['sleep', '7:12', IRIS], ['charge', '62', AQUA], ['ceiling', '12', SUN]],
  },
  berezhno: {
    word: 'Бережно', col: IRIS, sunUp: .38, haiku: ['Ночь вышла тонкой.', 'Тело просит тишины.', 'Бережность — сила.'],
    sky: ['#04040A', '#080A1A', '#151634', '#2A2547', '#5B4A72', '#A88AB0'], light: ['#F1EFFF', '#C9C4F2', '#9E97D8'], glow: 'rgba(168,163,255,.45)',
    ringCol: '#D8D4FF', aura: .28, cols: ['#D9D6F2', '#8F8AB0', '#5E5A78'], vals: [['sleep', '5:10', IRIS], ['charge', '31', AQUA], ['ceiling', '7', SUN]],
  },
};
function morning(mode = 'rovno') {
  const m = DAWNS[mode];
  const hz = 620, r = 150, S = sceneSize(r);
  const tcy = hz - Math.round(.70 * r);          // Titan stands on the horizon
  const scy = hz - Math.round(m.sunUp * r);       // the sun-ring rises by mode
  const stars = Array.from({ length: 28 }, (_, i) => {
    const x = (i * 157) % 412, y = 46 + ((i * 89) % 230), o = (.12 + ((i * 37) % 50) / 100) * (mode === 'razgon' ? .5 : 1) * (x > 60 && x < 352 && y > 140 ? .25 : 1);
    return `<div class="abs" style="left:${x}px;top:${y}px;width:${i % 5 ? 1.5 : 2}px;height:${i % 5 ? 1.5 : 2}px;border-radius:2px;background:#fff;opacity:${o}"></div>`;
  }).join('');
  const sk = m.sky, hp = hz / 9.15;
  const ringSvg = (w, dash = false) => `<svg width="${2 * r + 40}" height="${2 * r + 40}" viewBox="${-r - 20} ${-r - 20} ${2 * r + 40} ${2 * r + 40}" style="overflow:visible">
      <circle cx="0" cy="0" r="${r}" fill="none" stroke="${m.ringCol}" stroke-width="${w}" ${dash ? 'stroke-dasharray="3 7"' : ''} style="filter:drop-shadow(0 0 4px ${m.glow}) drop-shadow(0 0 16px ${m.glow})"/></svg>`;
  const scene = (o = {}) => titanScene(S, { light: m.light, colLight: m.cols, knock: 'rgba(14,10,16,.93)', glow: m.glow, ...o });
  const body = `
  <div class="abs" style="inset:0;background:linear-gradient(180deg,${sk[0]} 0%,${sk[1]} 22%,${sk[2]} 38%,${sk[3]} 50%,${sk[4]} ${hp - 3}%,${sk[5]} ${hp}%,#0B0A0C ${hp + .01}%,#08080A 100%)"></div>
  ${stars}
  <div class="abs" style="left:-80px;right:-80px;top:${scy - 290}px;height:580px;background:radial-gradient(50% 50% at 50% 50%, ${m.glow.replace(/[\d.]+\)$/, m.aura + ')')}, transparent 68%)"></div>
  <div class="abs" style="left:0;right:0;top:0;height:${hz}px;overflow:hidden">
    <div class="abs" style="left:${206 - r}px;top:${scy - r}px;width:${2 * r}px;height:${2 * r}px;border-radius:${r}px;background:radial-gradient(circle at 50% 75%,rgba(255,244,220,${.35 + m.aura * .6}) 0%,rgba(255,216,154,${.2 + m.aura * .5}) 32%,rgba(255,176,102,${m.aura * .4}) 62%,transparent 100%);${mode === 'berezhno' ? 'filter:hue-rotate(215deg) saturate(.6)' : ''}"></div>
    <div class="abs" style="left:${206 - r - 20}px;top:${scy - r - 20}px">${ringSvg(mode === 'razgon' ? 4 : 3, mode === 'berezhno')}</div>
    <div class="abs" style="left:${206 - S / 2}px;top:${tcy - S / 2}px">${scene()}</div>
  </div>
  <div class="abs" style="left:0;right:0;top:${hz}px;height:1px;background:linear-gradient(90deg,transparent,${m.ringCol},transparent)"></div>
  <div class="abs" style="left:${206 - S / 2}px;top:${hz}px;width:${S}px;height:110px;overflow:hidden;opacity:.2;filter:blur(2px);-webkit-mask:linear-gradient(180deg,#000,transparent);mask:linear-gradient(180deg,#000,transparent)">
    <div style="transform:scaleY(-1);transform-origin:50% 0;position:relative;top:0">
      <div style="position:absolute;left:0;top:${-(S / 2 - (hz - tcy))}px">${scene({ glow: false })}</div></div></div>
  ${statusBar('6:52', '#F3F1EC')}
  <div class="abs col" style="left:0;right:0;top:78px;align-items:center;color:#F3F1EC">
    <span class="mono" style="color:rgba(243,241,236,.55)">Среда · 11 ноября</span>
    <div class="row" style="margin-top:14px;gap:12px;align-items:center"><span style="color:${m.col}">${picto(mode, 36, 1.3)}</span><span class="d55" style="color:${m.col}">${m.word}</span></div>
    <div class="col" style="margin-top:22px;align-items:center;gap:4px">${m.haiku.map((l, i) => `<span style="font-size:22px;line-height:30px;font-weight:${i === 2 ? 400 : 300};letter-spacing:-.015em;color:${i === 2 ? '#FFFFFF' : 'rgba(243,241,236,.78)'}">${l}</span>`).join('')}</div>
  </div>
  <div class="abs row" style="left:24px;right:24px;top:${hz + 52}px;justify-content:space-between;color:#F3F1EC">
    ${m.vals.map(([p, v, c]) => `<div class="col" style="align-items:center;width:110px;gap:6px"><span style="color:${c}">${picto(p, 24, 1.5)}</span><span class="num" style="font-weight:200;font-size:38px;line-height:42px;letter-spacing:-.04em">${v}</span></div>`).join('<div style="width:1px;height:56px;background:rgba(255,255,255,.1)"></div>')}
  </div>
  <div class="abs" style="left:24px;right:24px;top:${hz + 172}px"><div class="btn light-pill" style="width:100%">Начать день ${icon('arrow', 18, 1.8)}</div></div>
  <div class="abs mono" style="left:0;right:0;top:${hz + 244}px;text-align:center;color:rgba(243,241,236,.42)">WHOOP · 06:41</div>
  <div class="gesture" style="background:#fff"></div>`;
  return page(body, { theme: 'dark' });
}

// ------------------------------------------------------------------ Workout confirmed by WHOOP («Печать»)
function workout() {
  const cy = 300, D = 268, R = D / 2, S = sceneSize(R * .9);
  const rays = `<div class="abs" style="left:${206 - 240}px;top:${cy - 240}px;width:480px;height:480px;border-radius:50%;
     background:repeating-conic-gradient(from 0deg, rgba(125,227,209,.11) 0deg 1deg, transparent 1deg 7.5deg);
     -webkit-mask:radial-gradient(circle, transparent 48%, #000 54%, transparent 72%);mask:radial-gradient(circle, transparent 48%, #000 54%, transparent 72%)"></div>`;
  const zones = [['Z1', 22], ['Z2', 59], ['Z3', 14], ['Z4', 5], ['Z5', 0]];
  const body = `
  <div class="abs" style="inset:0;background:radial-gradient(70% 40% at 50% 32%, rgba(125,227,209,.20), transparent 70%)"></div>
  ${statusBar('18:48')}
  <div class="abs row" style="left:16px;right:24px;top:50px;height:44px;justify-content:space-between">
    <div class="center" style="width:44px;height:44px;border-radius:22px">${icon('close', 22, 1.6)}</div>
    <div class="glass row" style="height:36px;border-radius:18px;padding:0 14px 0 6px;gap:8px">
      <div class="center" style="width:24px;height:24px;border-radius:12px;background:var(--mode);color:#06201B">${icon('check', 14, 2.6)}</div>
      <span class="mono">WHOOP</span></div>
    <span class="mono fg2">18:06–18:47</span>
  </div>
  ${rays}
  <div class="abs" style="left:${206 - R}px;top:${cy - R}px">${ring(D, 5, 1, 'var(--mode)', { glow: 'rgba(125,227,209,.6)', grad: ['#D6FFF7', '#4FC9B4'] })}</div>
  <div class="abs" style="left:${206 - S / 2}px;top:${cy - S / 2}px">${titanScene(S, { light: ICE, colLight: ['#DFFBF6', '#8FBDB5', '#5A7C78'], glow: 'rgba(125,227,209,.6)', knock: 'rgba(6,14,13,.93)' })}</div>
  <div class="abs col" style="left:0;right:0;top:${cy + R + 22}px;align-items:center">
    <div class="row" style="align-items:baseline;gap:6px"><span class="d89 num" style="font-size:72px;line-height:72px">41</span><span class="t21 fg2">мин</span></div>
    <div class="row mono fg2" style="gap:8px;margin-top:6px"><span>Zone 2</span><span class="fg3">·</span><span>цель 40</span></div>
  </div>
  <div class="abs row" style="left:24px;right:24px;top:578px;justify-content:space-between">
    ${[['heart', '127'], ['zones', '81 %'], ['ceiling', '8,4']].map(([p, v]) => `<div class="row" style="width:110px;justify-content:center;gap:8px"><span class="fg2">${picto(p, 20, 1.5)}</span><span class="t21 num" style="font-weight:300">${v}</span></div>`).join('')}
  </div>
  <div class="abs" style="left:24px;right:24px;top:630px">
    <div class="row" style="gap:3px;height:6px">${zones.filter(([, w]) => w > 0).map(([z, w]) => `<div style="flex:${w};height:6px;border-radius:3px;background:${z === 'Z2' ? 'var(--mode)' : z === 'Z1' ? 'rgba(125,227,209,.45)' : 'rgba(255,255,255,.18)'}"></div>`).join('')}</div>
    <div class="row mono fg3" style="justify-content:space-between;margin-top:10px;letter-spacing:.08em">${zones.map(([z]) => `<span style="${z === 'Z2' ? 'color:var(--fg)' : ''}">${z}</span>`).join('')}</div>
  </div>
  <div class="abs row" style="left:24px;right:24px;top:700px;justify-content:center;gap:12px">
    ${[['easy', 'Легко'], ['steady', 'Рабоче'], ['heavy', 'Тяжело']].map(([p, l], i) => `<div class="col" style="align-items:center;gap:8px;width:86px">
      <div class="center" style="width:60px;height:60px;border-radius:30px;${i === 1 ? 'background:rgba(125,227,209,.14);box-shadow:inset 0 0 0 1px rgba(125,227,209,.6);color:#fff' : 'box-shadow:inset 0 0 0 1px var(--hair2);color:var(--fg2)'}">${picto(p, 30, 1.4)}</div>
      <span class="l13 ${i === 1 ? '' : 'fg3'}" style="font-weight:400">${l}</span></div>`).join('')}
  </div>
  <div class="abs" style="left:24px;right:24px;top:826px"><div class="btn light-pill" style="width:100%">Готово</div></div>
  <div class="gesture"></div>`;
  return page(body, { theme: 'dark' });
}

// ------------------------------------------------------------------ Evening in three taps
function evening() {
  const seg = (opts, on) => `<div class="row" style="gap:6px;margin-top:12px">${opts.map((o, i) => `<div class="center l13" style="flex:1;height:44px;border-radius:22px;gap:6px;${i === on ? `background:rgba(168,163,255,.18);box-shadow:inset 0 0 0 1px rgba(168,163,255,.55);color:#fff` : 'box-shadow:inset 0 0 0 1px var(--hair2);color:var(--fg2)'}">${o}</div>`).join('')}</div>`;
  const plate = (k) => `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="8.5"/>${k === 1 ? '<path d="M12 3.5a8.5 8.5 0 0 1 0 17Z" fill="currentColor" stroke="none"/>' : k === 0 ? '<path d="M12 3.5v17M3.5 12H12M12 12l6 6" />' : '<circle cx="12" cy="12" r="3" fill="currentColor" stroke="none"/>'}</svg>`;
  const card = (top, p, title, inner, h, extra = '') => `<div class="abs glass" style="left:16px;right:16px;top:${top}px;height:${h}px;border-radius:26px;padding:14px 16px 0 14px;${extra}">
      <div class="row" style="gap:12px">${tile(p, { size: 40, ic: 24, col: '#E4E2FF', bg: 'rgba(168,163,255,.08)', ring: 'rgba(168,163,255,.25)' })}<span class="b16" style="font-weight:500">${title}</span></div>${inner}</div>`;
  const dayLine = `<svg class="abs" style="left:0;top:178px" width="412" height="70" viewBox="0 0 412 70">
     <defs><linearGradient id="dl" x1="0" x2="1"><stop offset="0" stop-color="#A8A3FF" stop-opacity="0"/><stop offset=".15" stop-color="#A8A3FF" stop-opacity=".5"/><stop offset=".85" stop-color="#C9C6FF" stop-opacity="1"/><stop offset="1" stop-color="#A8A3FF" stop-opacity="0"/></linearGradient></defs>
     <path d="M0 52 C40 52 50 44 70 40 S110 22 128 30 S150 50 170 46 S200 34 220 38 S262 12 282 18 S306 44 330 44 S380 50 412 50" fill="none" stroke="url(#dl)" stroke-width="1.6" style="filter:drop-shadow(0 0 4px rgba(168,163,255,.6))"/>
     ${[[70, 40], [170, 46], [282, 18], [372, 48]].map(([x, y]) => `<circle cx="${x}" cy="${y}" r="3.2" fill="#0B0A14" stroke="#C9C6FF" stroke-width="1.4"/>`).join('')}
   </svg>`;
  const body = `
  <div class="abs" style="inset:0;background:radial-gradient(90% 40% at 50% 0%, rgba(120,110,230,.28), transparent 70%), linear-gradient(180deg,#0A0912,#060608 60%)"></div>
  ${statusBar('21:32')}
  ${header('ВТ · ВЕЧЕР', { v: ['11,2', '9,4k', '1:13'], p: [.92, 1, .9], glow: 2 })}
  <div class="abs row" style="left:24px;top:110px;gap:14px;align-items:center"><span style="color:${IRIS}">${picto('berezhno', 40, 1.3)}</span><span class="d55" style="font-size:46px">Закрыть день</span></div>
  ${dayLine}
  <div class="abs row" style="left:24px;top:256px;gap:6px">${[1, 1, 0].map((f) => `<div style="width:22px;height:4px;border-radius:2px;background:${f ? '#C9C6FF' : 'rgba(255,255,255,.14)'}"></div>`).join('')}<span class="mono fg3" style="margin-left:8px">2 / 3</span></div>
  ${card(288, 'body', 'Тренировка', `<div class="abs row" style="right:16px;top:22px;gap:8px"><span class="l13 fg2">Zone 2 · 41′</span><div class="center" style="width:26px;height:26px;border-radius:13px;background:${AQUA};color:#06201B">${icon('check', 14, 2.6)}</div></div>`, 68)}
  ${card(370, 'food', 'Еда', seg([`${plate(0)} По тарелке`, `${plate(1)} Частично`, `${plate(2)} Просто`], 0), 124)}
  ${card(508, 'goal', 'Шаг к цели', seg([`${icon('check', 15, 2)} Сделал`, `${plate(1)} Частично`, `— Нет`], 0), 124)}
  ${card(646, 'seal', 'План помог?', seg([`${icon('check', 15, 2)} Да`, `Не очень`], -1), 124, 'box-shadow:var(--glass-edge),0 0 0 1px rgba(168,163,255,.35),0 0 40px -10px rgba(168,163,255,.45)')}
  <div class="abs row mono fg3" style="left:0;right:0;top:806px;justify-content:center;gap:8px">${picto('seal', 14, 1.6)}<span>последний тап закроет день</span></div>
  <div class="gesture"></div>`;
  return page(body, { theme: 'dark', extraCss: '.screen{--mode:#A8A3FF}' });
}

// ------------------------------------------------------------------ Mentor chat
function mentor() {
  const av = 48;
  const avatar = `<div style="position:relative;width:${av}px;height:${av}px;border-radius:${av / 2}px;overflow:hidden;background:radial-gradient(circle at 50% 30%,#1F242B,#0B0D10);box-shadow:inset 0 0 0 1px rgba(255,199,122,.35)">
     <div class="abs" style="left:-2px;top:2px">${titanScene(av + 4, { crop: '560 430 928 928', glow: 'rgba(255,199,122,.5)', light: LIGHT, colLight: ['#E9EEF3', '#9AA3AE', '#6B7480'] })}</div></div>`;
  const vince = (top, text, toks = '') => `<div class="abs" style="left:24px;right:56px;top:${top}px"><div class="b16" style="line-height:24px">${text}</div>${toks ? `<div class="row" style="gap:6px;margin-top:12px;flex-wrap:wrap">${toks}</div>` : ''}</div>`;
  const tok = (p, t, c) => `<span class="tok" style="height:28px;padding:0 10px;font-size:11.5px"><span style="color:${c}">${picto(p, 15, 1.6)}</span>${t}</span>`;
  const body = `
  <div class="abs" style="inset:0;background:radial-gradient(80% 30% at 50% -5%, rgba(255,199,122,.10), transparent 70%)"></div>
  ${statusBar('13:07')}
  <div class="abs row" style="left:20px;right:12px;top:52px;height:52px;justify-content:space-between">
    <div class="row" style="gap:12px"><div style="position:relative">${avatar}<div class="abs" style="right:0;bottom:0;width:12px;height:12px;border-radius:6px;background:var(--mode);box-shadow:0 0 0 2.5px var(--bg),0 0 8px rgba(125,227,209,.8)"></div></div>
      <div class="col"><span class="t21">Винс</span><span class="mono fg3" style="font-size:10px">ИИ · слушает</span></div></div>
    <div class="center fg2" style="width:48px;height:48px">${icon('dots', 22)}</div>
  </div>
  <div class="abs mono fg3" style="left:0;right:0;top:132px;text-align:center">Сегодня · 13:05</div>
  ${vince(168, 'Заряд ниже нормы, а в 15:00 питч. Давай без героизма: что в нём главное?', tok('sleep', '5:50', IRIS) + tok('charge', '44', AQUA) + tok('work', '15:00', DAWN))}
  <div class="abs glass b16" style="right:16px;top:300px;max-width:262px;border-radius:22px 22px 6px 22px;padding:14px 18px;line-height:23px">Цифры по юнит-экономике. Остальное — словами.</div>
  ${vince(406, 'Тогда так: до 14:20 — только слайд с цифрами. В 14:45 — три минуты выдоха.')}
  <div class="abs glass" style="left:16px;right:16px;top:484px;border-radius:24px;padding:6px">
    ${[['mind', '13:35–14:20', 'Фокус', 'Поставить'], ['sleep', '14:45', 'Выдох 3′', 'Напомнить']].map(([p, t, l, a], i) => `<div class="row" style="height:60px;padding:0 8px 0 8px;justify-content:space-between;${i ? 'border-top:1px solid var(--hair)' : ''}">
      <div class="row" style="gap:12px">${tile(i ? 'mind' : 'work', { size: 40, ic: 22, col: 'var(--dawn)' })}<div class="col" style="gap:2px"><span class="b15">${l}</span><span class="mono" style="color:var(--dawn);letter-spacing:.06em;font-size:10px">${t}</span></div></div>
      <div class="center l13" style="height:40px;padding:0 16px;border-radius:20px;${i ? 'box-shadow:inset 0 0 0 1px var(--hair2)' : 'background:var(--dawn-fill);color:var(--on-dawn)'}">${a}</div></div>`).join('')}
  </div>
  <div class="abs row mono fg3" style="left:24px;top:634px;gap:8px">${picto('mind', 15, 1.6)}<span>метки: напряжение · работа</span></div>
  <div class="abs row" style="left:16px;right:16px;top:740px;gap:10px">
    <div class="glass row" style="flex:1;height:56px;border-radius:28px;padding:0 22px"><span class="b16 fg3">Сказать Винсу…</span></div>
    <div class="btn light-pill" style="width:56px;height:56px">${icon('mic', 22, 1.8)}</div>
  </div>
  ${nav('mentor')}`;
  return page(body, { theme: 'dark' });
}

// ------------------------------------------------------------------ Connect WHOOP (onboarding 2/3)
function connect() {
  const R = 118, S = sceneSize(R), cy = 236;
  const src = (top, ic, name, toks, state) => `<div class="abs glass row" style="left:16px;right:16px;top:${top}px;height:80px;border-radius:26px;padding:0 16px 0 14px;justify-content:space-between">
    <div class="row" style="gap:14px"><div class="center" style="width:50px;height:50px;border-radius:25px;background:rgba(255,255,255,.05);box-shadow:inset 0 0 0 1px var(--hair2)">${icon(ic, 22, 1.5)}</div>
    <div class="col" style="gap:7px"><span class="t21" style="font-size:19px">${name}</span><div class="row fg2" style="gap:10px">${toks.map((p) => picto(p, 18, 1.5)).join('')}</div></div></div>${state}</div>`;
  const body = `
  <div class="abs" style="inset:0;background:radial-gradient(60% 30% at 50% 26%, rgba(255,190,110,.20), transparent 70%)"></div>
  ${statusBar('9:41')}
  <div class="abs row" style="left:24px;right:24px;top:56px;gap:6px">${[1, 1, 0].map((f) => `<div style="flex:1;height:3px;border-radius:2px;background:${f ? 'var(--fg)' : 'rgba(255,255,255,.14)'}"></div>`).join('')}</div>
  <div class="abs" style="left:${206 - S / 2}px;top:${cy - S / 2}px">${titanScene(S, { ring: .66, ringCol: DAWN, pulse: .66, pulseCol: DAWN, glow: 'rgba(255,199,122,.55)' })}</div>
  <div class="abs" style="left:${206 - S / 2}px;top:${cy - S / 2}px;opacity:.12">${titanScene(S, { ring: 1, ringCol: '#fff', titan: false, columns: false, glow: false, ringW: 4 })}</div>
  <div class="abs col" style="left:24px;right:24px;top:400px;align-items:center;text-align:center">
    <div class="d34" style="font-size:38px;line-height:42px;font-weight:200">Данные придут сами</div>
    <div class="row fg2" style="gap:18px;margin-top:18px">${['sleep', 'charge', 'body'].map((p) => picto(p, 26, 1.4)).join('<span class="fg3">·</span>')}</div>
  </div>
  ${src(530, 'band', 'WHOOP', ['sleep', 'charge', 'body'], `<div class="center" style="width:44px;height:44px;border-radius:22px;box-shadow:inset 0 0 0 1px rgba(255,199,122,.5);color:var(--dawn)">${icon('arrow', 18, 1.8)}</div>`)}
  ${src(624, 'watch', 'Pixel Watch', ['steps', 'heart'], `<div class="center" style="width:32px;height:32px;border-radius:16px;background:var(--mode);color:#06201B">${icon('check', 16, 2.6)}</div>`)}
  <div class="abs" style="left:24px;right:24px;top:776px"><div class="btn light-pill" style="width:100%">Подключить WHOOP</div></div>
  <div class="abs row mono fg3" style="left:0;right:0;top:850px;justify-content:center;gap:8px">${icon('lock', 13, 1.6)}<span>вход через WHOOP</span></div>
  <div class="gesture"></div>`;
  return page(body, { theme: 'dark' });
}

// ------------------------------------------------------------------ s8 · Camera: Titan as the stencil
function camera() {
  const crop = [470, 440, 1108, 1420]; // x y w h in mark space
  const W = 340, k = W / crop[2], H = crop[3] * k, top = 150, left = 206 - W / 2;
  const Y = (y) => top + (y - crop[1]) * k;
  const guide = (y, label, ok) => `<div class="abs row" style="left:20px;right:20px;top:${Y(y) - 0.5}px;height:1px;background:${ok ? 'linear-gradient(90deg,rgba(125,227,209,.0),rgba(125,227,209,.7) 20%,rgba(125,227,209,.7) 80%,rgba(125,227,209,0))' : 'rgba(255,255,255,.22)'}"></div>
     <div class="abs row mono" style="right:46px;top:${Y(y) - 20}px;gap:6px;font-size:9.5px;color:${ok ? AQUA : 'rgba(255,255,255,.6)'}">${ok ? icon('check', 11, 2.4) : ''}${label}</div>`;
  const ruler = Array.from({ length: 27 }, (_, i) => `<div class="abs" style="right:22px;top:${Y(501) + i * (Y(1802) - Y(501)) / 26}px;width:${i % 5 ? 6 : 12}px;height:1px;background:rgba(255,255,255,${i % 5 ? .25 : .5})"></div>`).join('');
  const person = `<div class="abs" style="left:${left + 3}px;top:${top + 6}px;filter:blur(6px);opacity:.8;transform:scale(.985)">
     <svg width="${W}" height="${H}" viewBox="${crop.join(' ')}" style="display:block"><use href="#tt-sil" fill="#3A3F48"/></svg></div>`;
  const body = `
  <div class="abs" style="inset:0;background:linear-gradient(180deg,#2B2A2E 0%,#232327 55%,#1A1A1D 70%,#141416 100%)"></div>
  <div class="abs" style="left:0;right:0;top:${Y(1802) + 4}px;height:1px;background:rgba(255,255,255,.06)"></div>
  <div class="abs" style="left:-40px;top:120px;width:200px;height:500px;background:radial-gradient(closest-side,rgba(255,230,200,.06),transparent)"></div>
  ${person}
  <div class="abs" style="inset:0;background:radial-gradient(70% 55% at 50% 48%, transparent 60%, rgba(0,0,0,.55))"></div>
  <div class="abs" style="left:${left}px;top:${top}px">${titanStencil(W, { crop: crop.join(' '), col: 'rgba(255,255,255,.92)', w: 6, dash: '20 14' })}</div>
  ${guide(740, 'плечи', true)}${guide(1150, 'талия', true)}${guide(1560, 'колени', false)}
  ${ruler}
  <div class="abs mono" style="right:16px;top:${Y(501) - 22}px;font-size:9.5px;color:rgba(255,255,255,.6)">рост</div>
  ${statusBar('10:14', '#F3F1EC')}
  <div class="abs row" style="left:16px;right:16px;top:50px;height:48px;justify-content:space-between;color:#F3F1EC">
    <div class="center" style="width:44px;height:44px;border-radius:22px;background:rgba(0,0,0,.35)">${icon('close', 20, 1.6)}</div>
    <div class="glass row" style="height:44px;border-radius:22px;padding:4px;gap:2px">${[['front', 1], ['side', 0], ['back', 0]].map(([p, on]) => `<div class="center" style="width:52px;height:36px;border-radius:18px;${on ? 'background:rgba(255,255,255,.16);color:#fff' : 'color:rgba(255,255,255,.55)'}">${picto(p, 22, 1.5)}</div>`).join('')}</div>
    <div class="center mono" style="width:44px;height:44px;border-radius:22px;background:rgba(0,0,0,.35);font-size:10px">1/3</div>
  </div>
  <div class="abs mono" style="left:0;right:0;top:${Y(1802) + 110}px;text-align:center;color:rgba(243,241,236,.6)">встань в контур</div>
  <div class="abs row" style="left:0;right:0;top:${Y(1802) + 40}px;justify-content:center;gap:10px">
    ${[['level', 1], ['distance', 1], ['lightq', 0]].map(([p, ok]) => `<div class="center" style="width:44px;height:44px;border-radius:22px;${ok ? `background:rgba(125,227,209,.16);box-shadow:inset 0 0 0 1px rgba(125,227,209,.6);color:${AQUA}` : 'background:rgba(0,0,0,.35);box-shadow:inset 0 0 0 1px rgba(255,255,255,.25);color:#F3F1EC'}">${picto(p, 22, 1.5)}</div>`).join('')}
  </div>
  <div class="abs" style="left:0;right:0;bottom:0;height:170px;background:linear-gradient(180deg,transparent,rgba(0,0,0,.75) 40%)"></div>
  <div class="abs row" style="left:40px;right:40px;bottom:46px;justify-content:space-between;align-items:center;color:#F3F1EC">
    <div style="width:48px;height:48px;border-radius:14px;overflow:hidden;box-shadow:inset 0 0 0 1px rgba(255,255,255,.3);background:#1d1f23" class="center">${picto('nodata', 26, 1.3, 'rgba(255,255,255,.5)')}</div>
    <div class="center" style="width:84px;height:84px;border-radius:42px;box-shadow:inset 0 0 0 3px #fff">
      <div class="center" style="width:68px;height:68px;border-radius:34px;background:#fff;color:#0D0F12;box-shadow:0 0 30px rgba(125,227,209,.5)">${picto('body', 34, 1.5)}</div></div>
    <div class="center" style="width:48px;height:48px;border-radius:24px;background:rgba(255,255,255,.1)">${picto('flip', 22, 1.6)}</div>
  </div>
  <div class="gesture" style="background:#fff"></div>`;
  return page(body, { theme: 'dark' });
}

// ------------------------------------------------------------------ s9 · Я: become the Titan in every sphere
const SPHERES = [
  ['mind', 'Разум', .8, { x0: 860, x1: 1190, y0: 480, y1: 660 }],
  ['work', 'Работа', .9, { rects: [{ x0: 490, x1: 870, y0: 560, y1: 940 }, { x0: 1180, x1: 1560, y0: 560, y1: 940 }] }],
  ['people', 'Связи', .45, { x0: 870, x1: 1180, y0: 660, y1: 960 }],
  ['food', 'Еда', .72, { x0: 800, x1: 1260, y0: 960, y1: 1230 }],
  ['body', 'Тело', .85, { x0: 600, x1: 1460, y0: 1230, y1: 1820 }],
  ['sleep', 'Сон', .55, null],
];
function me() {
  const cx = 206, cy = 318, R = 160, S = sceneSize(R * .86);
  const zones = SPHERES.filter((s) => s[3]).map(([, , v, r]) => ({ ...r, lit: v >= .7 ? 1 : .15 + v * .5 }));
  // six arcs = six spheres around the Titan
  const gap = 5, seg = 60 - gap;
  const arcs = SPHERES.map(([p, , v], i) => {
    const a0 = -90 + i * 60 + gap / 2, rr = R;
    const pt = (a) => [cx + rr * Math.cos(a * Math.PI / 180), cy + rr * Math.sin(a * Math.PI / 180)];
    const [x0, y0] = pt(a0), [x1, y1] = pt(a0 + seg), [xv, yv] = pt(a0 + seg * v);
    const am = (a0 + seg / 2) * Math.PI / 180, ix = cx + (R + 30) * Math.cos(am), iy = cy + (R + 30) * Math.sin(am);
    return { track: `M${x0} ${y0} A${rr} ${rr} 0 0 1 ${x1} ${y1}`, val: `M${x0} ${y0} A${rr} ${rr} 0 0 1 ${xv} ${yv}`, ix, iy, p, v };
  });
  const body = `
  <div class="abs" style="inset:0;background:radial-gradient(60% 34% at 50% 34%, rgba(255,199,122,.14), transparent 72%)"></div>
  ${statusBar('20:05')}
  <div class="abs row" style="left:24px;right:16px;top:50px;height:40px;justify-content:space-between">
    <div class="col"><span class="mono" style="color:var(--dawn)">Твой Титан · неделя 3</span></div>
    <div class="center fg2" style="width:44px;height:44px">${icon('dots', 22)}</div>
  </div>
  <svg class="abs" style="left:0;top:0" width="412" height="915">
    ${arcs.map((a) => `<path d="${a.track}" fill="none" stroke="rgba(255,255,255,.08)" stroke-width="4" stroke-linecap="round"/>
      <path d="${a.val}" fill="none" stroke="${a.p === 'sleep' ? IRIS : DAWN}" stroke-width="4" stroke-linecap="round" style="filter:drop-shadow(0 0 5px ${a.p === 'sleep' ? 'rgba(168,163,255,.8)' : 'rgba(255,199,122,.8)'})"/>`).join('')}
  </svg>
  ${arcs.map((a) => `<div class="abs center" style="left:${a.ix - 18}px;top:${a.iy - 18}px;width:36px;height:36px;border-radius:18px;background:var(--bg);box-shadow:inset 0 0 0 1px var(--hair2);color:${a.v > .7 ? 'var(--fg)' : 'var(--fg2)'}">${picto(a.p, 22, 1.5)}</div>`).join('')}
  <div class="abs" style="left:${cx - S / 2}px;top:${cy - S / 2}px">
    <svg width="${S}" height="${S}" viewBox="0 0 2048 2048" style="position:absolute;left:0;top:0;overflow:visible">
      <g clip-path="url(#tt-left)" opacity=".55"><use href="#hot-columns-art" fill="#8D97A3" stroke="#8D97A3"/></g><g clip-path="url(#tt-right)" opacity=".55"><use href="#hot-columns-art" fill="#8D97A3" stroke="#8D97A3"/></g>
      <circle cx="1024" cy="1023" r="980" fill="none" stroke="${IRIS}" stroke-width="10" opacity=".35" style="filter:drop-shadow(0 0 30px ${IRIS})"/></svg>
    <div style="position:absolute;left:0;top:0">${titanZones(S, zones, { base: 'rgba(255,255,255,.07)' })}</div>
  </div>
  <div class="abs col" style="left:0;right:0;top:${cy + R + 56}px;align-items:center">
    <div class="row" style="align-items:baseline;gap:8px"><span class="d55 num">4</span><span class="t21 fg2">из 6 в свете</span></div>
  </div>
  <div class="abs" style="left:16px;right:16px;top:640px;display:grid;grid-template-columns:repeat(3,1fr);gap:8px">
    ${SPHERES.map(([p, l, v]) => `<div class="glass col" style="height:72px;border-radius:20px;padding:12px 14px;justify-content:space-between">
      <div class="row" style="justify-content:space-between"><span style="color:${v > .7 ? 'var(--fg)' : 'var(--fg2)'}">${picto(p, 22, 1.5)}</span><span class="mono fg3" style="letter-spacing:.04em">${v >= .7 ? '↑' : v >= .5 ? '→' : '·'}</span></div>
      <div style="height:3px;border-radius:2px;background:rgba(255,255,255,.08)"><div style="width:${v * 100}%;height:3px;border-radius:2px;background:${p === 'sleep' ? IRIS : DAWN};box-shadow:0 0 8px ${p === 'sleep' ? IRIS : DAWN}"></div></div></div>`).join('')}
  </div>
  ${nav('me')}`;
  return page(body, { theme: 'dark' });
}

// ------------------------------------------------------------------ sync storyboard «Титан приходит» (wow 1)
const SYNC = [
  { t: 0, title: 'Туман', note: 'Две колонны сдвинуты к центру, кольцо едва видно. Тап или pull-to-refresh.', o: { spread: .5, titan: false, colOpacity: .35 }, fog: .9 },
  { t: 180, title: 'Силуэт вдали', note: 'Из глубины проступает Титан: масштаб 0,3, размытие, туман.', o: { spread: .5, tScale: .3, tY: -260, tBlur: 26, tOpacity: .35, colOpacity: .55 }, fog: .8 },
  { t: 420, title: 'Шаг вперёд', note: 'Подходит: масштаб 0,6, туман редеет. Пружина без перелёта.', o: { spread: .52, tScale: .6, tY: -110, tBlur: 10, tOpacity: .75, colOpacity: .75 }, fog: .5 },
  { t: 680, title: 'Ладони на колоннах', note: 'Масштаб 0,84, ладони ложатся на капители. Тактильный тик.', o: { spread: .69, tScale: .84, tY: -30, colOpacity: 1 }, fog: .2, tick: true },
  { t: 900, title: 'Раздвигает', note: 'Колонны уходят на место с перелётом 4 %; сквозь них вспыхивает пульс.', o: { spread: 1.04, pulse: 1, pulseCol: DAWN }, fog: 0, tick: true },
  { t: 1150, title: 'Кольцо замыкается', note: 'Круг дорисовывается по мере прихода данных — это и есть прогресс.', o: { spread: 1, ring: .72, ringCol: AQUA, pulse: 1, pulseCol: AQUA, pulseOpacity: .5, glow: 'rgba(125,227,209,.55)', light: ICE } },
  { t: 1500, title: 'Титан готов', note: 'Кольцо замкнуто, данные свежие. Сцена сжимается в кольцо Заряда на «Сегодня», шапка докатывает числа.', o: { spread: 1, ring: 1, ringCol: AQUA, glow: 'rgba(125,227,209,.6)', light: ICE }, final: true },
];
function storyboard() {
  const FW = 280, FH = 400, gapX = 22;
  const frame = (f, n) => {
    const S = 250;
    return `<div class="col" style="width:${FW}px;gap:14px">
    <div style="position:relative;width:${FW}px;height:${FH}px;border-radius:32px;overflow:hidden;background:radial-gradient(70% 50% at 50% 42%, rgba(125,227,209,${f.final ? .16 : .06}), transparent 70%),#07080A;box-shadow:inset 0 0 0 1px rgba(255,255,255,.07)">
      <div class="abs mono" style="left:18px;top:16px;color:rgba(243,241,236,.45)">${String(n + 1).padStart(2, '0')}</div>
      <div class="abs mono" style="right:18px;top:16px;color:${DAWN}">${f.t} мс</div>
      <div class="abs" style="left:${(FW - S) / 2}px;top:58px">${titanScene(S, { glow: 'rgba(255,199,122,.5)', ...f.o })}</div>
      ${f.o.ring === undefined ? `<div class="abs" style="left:${(FW - S) / 2}px;top:58px;opacity:.16">${titanScene(S, { ring: 1, ringCol: '#fff', titan: false, columns: false, glow: false, ringW: 5 })}</div>` : ''}
      ${f.fog ? `<div class="abs" style="inset:0;background:radial-gradient(60% 45% at 50% 45%, rgba(200,210,220,${.16 * f.fog}), transparent 70%);backdrop-filter:blur(${f.fog * 1.5}px)"></div>` : ''}
      ${f.tick ? `<div class="abs row mono" style="left:0;right:0;top:330px;justify-content:center;gap:6px;color:${DAWN}">${icon('haptic', 14, 1.6)} тик</div>` : ''}
      ${f.final ? `<div class="abs row" style="left:0;right:0;top:322px;justify-content:center"><div class="capsule glass" style="color:#F3F1EC;height:36px"><div class="seg">${ring(20, 2.4, .7, AQUA)}<span class="num">8,4</span></div><div class="seg">${ring(20, 2.4, .76, '#F3F1EC')}<span class="num">6,8k</span></div><div class="seg">${ring(20, 2.4, .62, IRIS)}<span class="num">9:13</span></div></div></div>` : ''}
    </div>
    <div class="col" style="gap:6px;padding:0 4px"><span style="font-size:17px;font-weight:500;color:#F3F1EC">${f.title}</span><span style="font-size:13px;line-height:18px;color:#A3A7AE">${f.note}</span></div></div>`;
  };
  const W = SYNC.length * FW + (SYNC.length - 1) * gapX + 2 * 56, H = 700;
  const body = `<div style="width:${W}px;height:${H}px;background:#030304;padding:48px 56px;font-family:var(--sans);color:#F3F1EC;position:relative">
    <div class="row" style="justify-content:space-between;align-items:flex-end;margin-bottom:30px">
      <div class="col" style="gap:10px"><span class="mono" style="color:${DAWN}">Вау-момент 1 · синхронизация</span><span style="font-size:44px;line-height:46px;font-weight:200;letter-spacing:-.04em">Титан приходит и раздвигает колонны</span></div>
      <span class="mono" style="color:rgba(243,241,236,.5);text-align:right;line-height:18px">1,5 с · пружины: подход 0.9/220 · толчок 0.55/420 · кольцо 1.0/300<br>тики: ладони, толчок · «удалить анимацию» → сразу кадр 07</span></div>
    <div class="row" style="gap:${gapX}px;align-items:flex-start">${SYNC.map(frame).join('')}</div></div>`;
  return { html: page(body, { wrap: false, w: W, h: H }), w: W, h: H };
}

// ------------------------------------------------------------------ icons sheet
const ICONSET = [
  ['Сферы жизни', [['body', 'Тело'], ['sleep', 'Сон'], ['work', 'Работа'], ['food', 'Еда'], ['people', 'Связи'], ['mind', 'Разум'], ['goal', 'Шаг к цели']]],
  ['Режимы · солнце — кольцо вокруг Титана', [['razgon', 'Разгон', SUN], ['rovno', 'Ровно', AQUA], ['berezhno', 'Бережно', IRIS]]],
  ['Статусы сверки (A35)', [['seal', 'WHOOP', AQUA], ['self', 'Отмечено тобой'], ['changed', 'Изменено', AQUA], ['now', 'Сейчас', DAWN], ['nodata', 'Нет данных']]],
  ['Как ощущалось', [['easy', 'Легко'], ['steady', 'Рабоче'], ['heavy', 'Тяжело']]],
  ['Камера', [['front', 'Анфас'], ['side', 'Профиль'], ['back', 'Спина'], ['level', 'Ровно держишь'], ['distance', 'Дистанция'], ['lightq', 'Свет'], ['flip', 'Сменить камеру']]],
  ['Данные', [['charge', 'Заряд', AQUA], ['heart', 'Пульс покоя'], ['ceiling', 'Потолок нагрузки', SUN], ['steps', 'Шаги'], ['zones', 'Зоны пульса']]],
];
function icons() {
  const W = 2000, H = 1720;
  const cell = ([p, l, c]) => `<div class="col" style="align-items:center;gap:14px;width:150px">
    <div class="center" style="width:104px;height:104px;border-radius:32px;background:linear-gradient(180deg,rgba(255,255,255,.07),rgba(255,255,255,.02));box-shadow:inset 0 1px 0 rgba(255,255,255,.1),inset 0 0 0 1px rgba(255,255,255,.07);color:${c || '#F3F1EC'}">${picto(p, 56, 1.3)}</div>
    <div class="row" style="gap:10px;align-items:center;color:${c || '#F3F1EC'}">${picto(p, 24, 1.6)}<span style="font-size:14px;color:#A3A7AE">${l}</span></div></div>`;
  const empty = [
    ['Набираю данные', titanStencil(220, { col: 'rgba(255,255,255,.7)', dash: '10 18', w: 6, inner: .08, glow: 'rgba(255,255,255,.2)' }), 'пунктирный Титан, кольцо не замкнуто'],
    ['Первый день', `<div style="position:relative;width:220px;height:220px">${titanScene(220, { spread: .55, tScale: .4, tY: -200, tBlur: 14, tOpacity: .5, colOpacity: .6 })}<div class="abs" style="inset:0;background:radial-gradient(55% 45% at 50% 45%, rgba(200,210,220,.14), transparent 70%)"></div></div>`, 'Титан вдали, в тумане — он придёт'],
    ['Возвращение', titanScene(220, { ring: .35, ringCol: DAWN, glow: 'rgba(255,199,122,.5)', titanFill: 'rgba(8,9,12,.92)', light: ['#FFE2B0', '#C9A07A', '#6B5A4E'] }), 'Титан снова в свете, кольцо начинается заново'],
  ];
  const body = `<div style="width:${W}px;height:${H}px;background:radial-gradient(50% 30% at 10% 0%, rgba(255,199,122,.08), transparent 60%),#040405;color:#F3F1EC;font-family:var(--sans);padding:80px 96px">
    <span class="mono" style="color:${DAWN};font-size:13px">Титаниум · пиктограммы вместо слов</span>
    <div style="font-size:72px;line-height:76px;font-weight:200;letter-spacing:-.05em;margin-top:14px">Колонны и Титан — грамматика всех знаков</div>
    <div style="font-size:19px;color:#A3A7AE;margin-top:14px;max-width:1300px">Сетка 24 dp, линия 1,6 dp, скругления. Две колонны — опора, фигура между ними — человек в своей сфере. Режимы — солнце-кольцо вокруг Титана. Подпись видна только при первом показе и в TalkBack.</div>
    <div style="display:grid;grid-template-columns:1fr 1fr;gap:48px 80px;margin-top:56px">
      ${ICONSET.map(([t, items]) => `<div class="col" style="gap:22px"><span class="mono" style="color:#80858D">${t}</span><div class="row" style="flex-wrap:wrap;gap:22px 8px">${items.map(cell).join('')}</div></div>`).join('')}
    </div>
    <div class="col" style="gap:22px;margin-top:56px"><span class="mono" style="color:#80858D">Пустые состояния — сам Титан</span>
      <div class="row" style="gap:40px">${empty.map(([t, art, d]) => `<div class="glass row" style="border-radius:32px;padding:18px 28px 18px 18px;gap:20px;width:580px">${art}<div class="col" style="gap:8px"><span style="font-size:24px;font-weight:300">${t}</span><span style="font-size:14px;color:#A3A7AE;line-height:20px">${d}</span></div></div>`).join('')}</div></div>
  </div>`;
  return { html: page(body, { wrap: false, w: W, h: H }), w: W, h: H };
}

// ------------------------------------------------------------------ concept sheet
function concept() {
  const W = 2400, H = 2660;
  const sw = (c, n, hex) => `<div class="col" style="gap:10px;width:132px"><div style="height:90px;border-radius:24px;background:${c};box-shadow:inset 0 0 0 1px rgba(255,255,255,.08)"></div><span style="font-size:15px;font-weight:500">${n}</span><span class="mono" style="color:#80858D;letter-spacing:.06em">${hex}</span></div>`;
  const shot = (f, label, w = 384) => `<div class="col" style="gap:14px;align-items:center"><img src="file://${OUT}/${f}" style="width:${w}px;border-radius:44px;box-shadow:0 0 0 1px rgba(255,255,255,.10),0 40px 80px -30px rgba(0,0,0,.9)"><span class="mono" style="color:#80858D">${label}</span></div>`;
  const feat = (p, t, d) => `<div class="row" style="gap:16px;align-items:flex-start"><div class="center" style="flex:none;width:48px;height:48px;border-radius:24px;background:rgba(255,255,255,.05);box-shadow:inset 0 0 0 1px rgba(255,255,255,.1);color:${DAWN}">${p}</div><div class="col" style="gap:4px"><span style="font-size:17px;font-weight:500">${t}</span><span style="font-size:14px;line-height:19px;color:#A3A7AE">${d}</span></div></div>`;
  const feats = [
    [picto('body', 24, 1.5), 'Титан приходит', 'синхронизация: выходит из тумана, раздвигает колонны'],
    [icon('eye', 20, 1.6), 'Шапка из трёх колец', 'нагрузка, шаги, до отбоя — без SOS'],
    [picto('rovno', 24, 1.5), 'Рассвет', 'солнце встаёт кольцом вокруг Титана; свет — по восстановлению'],
    [picto('seal', 24, 1.5), 'Печать WHOOP', 'кольцо замыкается вокруг Титана, двойной тик'],
    [picto('front', 24, 1.5), 'Камера-трафарет', 'встаёшь в контур Титана: плечи, талия, рост'],
    [picto('mind', 24, 1.5), 'Титан по сферам', 'шесть сфер наполняют фигуру светом'],
    [picto('food', 24, 1.5), 'Пиктограммы вместо слов', 'сферы, режимы, статусы — одна грамматика'],
    [picto('berezhno', 24, 1.5), 'Вечер в 3 тапа', 'последний тап сам закрывает день'],
    [picto('rovno', 24, 1.5), 'Хокку утра', 'три строки: состояние честно, настрой на день'],
    [icon('roll', 20, 1.6), 'Цифры докатываются', 'табличные цифры, без прыжков'],
    [icon('layers', 20, 1.6), 'Аура режима', 'свет вокруг Титана окрашен режимом'],
    [picto('sleep', 24, 1.5), 'Сумерки к отбою', 'после 21:00 интерфейс теплеет и тускнеет'],
  ];
  const hero = titanScene(560, { ring: 1, ringCol: DAWN, pulse: 1, pulseCol: DAWN, glow: 'rgba(255,199,122,.55)' });
  const body = `<div style="width:${W}px;height:${H}px;background:radial-gradient(40% 25% at 78% 8%, rgba(255,199,122,.16), transparent 60%),radial-gradient(50% 30% at 10% 40%, rgba(125,227,209,.06), transparent 60%),#040405;color:#F3F1EC;font-family:var(--sans);position:relative;padding:90px 110px">
    <div class="row" style="justify-content:space-between;align-items:center">
      <div class="col" style="gap:22px;max-width:1150px">
        <span class="mono" style="color:${DAWN};font-size:14px">Health of a Titan · концепция v3 · A37 v0.2</span>
        <div style="font-size:176px;line-height:160px;font-weight:200;letter-spacing:-.06em">Титаниум</div>
        <div style="font-size:34px;line-height:44px;font-weight:300;letter-spacing:-.02em;color:#A3A7AE">Титан — герой каждого экрана. Титан — материал: свет, стекло, точность. Он приходит, раздвигает колонны и держит твой день; ты хочешь стать им во всех сферах.</div>
        <div class="row" style="gap:40px;margin-top:20px;align-items:center">
          <div style="width:150px;height:150px;border-radius:50%;overflow:hidden;box-shadow:0 0 50px -10px rgba(226,168,71,.4)"><img src="mark-full.svg" style="width:164px;height:164px;margin:-7px"></div>
          <div class="col" style="gap:10px"><span style="font-family:'Cormorant SC';font-weight:600;font-size:32px;letter-spacing:.2em">HEALTH OF A TITAN</span><span class="mono" style="font-size:15px;letter-spacing:.42em">HEALTH OF A TITAN</span><span class="mono" style="color:#80858D">знак v2 без изменений · вордмарк: антиква — стор, моно — приложение</span></div>
        </div>
      </div>
      <div style="position:relative;width:640px;height:640px" class="center"><div class="abs" style="inset:0;background:radial-gradient(closest-side, rgba(255,199,122,.16), transparent)"></div>${hero}</div>
    </div>

    <div class="row" style="margin-top:70px;align-items:center">
      ${[['06:52', 'Утро'], ['13:32', 'Режим и план'], ['18:48', 'Тренировка'], ['10:14', 'Фото тела'], ['20:05', 'Твой Титан']].map(([t, l], i) => `<div class="row" style="width:${i < 4 ? 432 : 380}px;gap:12px"><span class="mono" style="color:${DAWN}">${t}</span><span style="font-size:16px;font-weight:500">${l}</span>${i < 4 ? '<div style="flex:1;height:1px;margin:0 26px 0 6px;background:linear-gradient(90deg,rgba(255,199,122,.5),rgba(255,255,255,.08))"></div>' : ''}</div>`).join('')}
    </div>
    <div class="row" style="gap:44px;margin-top:24px;justify-content:space-between;align-items:flex-end">
      ${shot('s1-morning-rovno.png', '01 · Рассвет · хокку')}
      ${shot('s2-today-dark.png', '02 · Сегодня')}
      ${shot('s4-workout-whoop.png', '04 · Печать WHOOP')}
      ${shot('s8-camera.png', '08 · Камера-трафарет')}
      ${shot('s9-me-titan.png', '09 · Титан по сферам')}
    </div>

    <div class="row" style="gap:90px;margin-top:90px;align-items:flex-start">
      <div class="col" style="gap:24px;width:820px">
        <span class="mono" style="color:${DAWN}">Палитра · титан и свет</span>
        <div class="row" style="gap:20px">${sw('#060708', 'Глубина', '#060708')}${sw('#15181C', 'Титан', '#15181C')}${sw('linear-gradient(180deg,rgba(255,255,255,.10),rgba(255,255,255,.03)),#0E1013', 'Стекло', 'white 7 %')}${sw('#F3F1EC', 'Кость', '#F3F1EC')}${sw('linear-gradient(180deg,#FFE1AE,#FFC77A 50%,#FFB05E)', 'Рассвет', '#FFC77A')}</div>
        <div class="row" style="gap:20px">${sw(SUN, 'Разгон · солнце', SUN)}${sw(AQUA, 'Ровно · лёд', AQUA)}${sw(IRIS, 'Бережно · ирис', IRIS)}${sw('#F3F2EF', 'Фарфор', '#F3F2EF')}${sw('#D6A964', 'Золото знака', '#D6A964')}</div>
        <span class="mono" style="color:${DAWN};margin-top:20px">Шрифт · Geist + Geist Mono (OFL)</span>
        <div class="row" style="gap:40px;align-items:flex-end">
          <span style="font-size:140px;line-height:126px;font-weight:200;letter-spacing:-.06em">44</span>
          <div class="col" style="gap:10px"><span style="font-size:55px;line-height:56px;font-weight:200;letter-spacing:-.045em">Ровно</span><span style="font-size:21px">Слайд с цифрами</span><span class="mono" style="color:#A3A7AE">13:35–14:20 · WHOOP 06:41</span></div>
        </div>
        <span class="mono" style="color:${DAWN};margin-top:20px">Пиктограммы · колонны и Титан</span>
        <div class="row" style="gap:14px">${['body', 'sleep', 'work', 'food', 'people', 'mind', 'razgon', 'rovno', 'berezhno', 'seal'].map((p) => `<div class="center" style="width:66px;height:66px;border-radius:22px;background:rgba(255,255,255,.05);box-shadow:inset 0 0 0 1px rgba(255,255,255,.08);color:${p === 'rovno' || p === 'seal' ? AQUA : p === 'razgon' ? SUN : p === 'berezhno' ? IRIS : '#F3F1EC'}">${picto(p, 34, 1.4)}</div>`).join('')}</div>
      </div>
      <div class="col" style="gap:24px;flex:1">
        <span class="mono" style="color:${DAWN}">12 фишек</span>
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:26px 40px">${feats.map((f) => feat(...f)).join('')}</div>
        <span class="mono" style="color:${DAWN};margin-top:20px">3 вау-момента</span>
        <div class="row" style="gap:20px">${[['Титан приходит', 'выходит из тумана, раздвигает колонны, кольцо замыкается'], ['Рассвет', 'солнце встаёт кольцом вокруг Титана между колонн'], ['Печать', 'WHOOP подтвердил — кольцо вокруг Титана замыкается лучами']].map(([t, d]) => `<div class="glass col" style="flex:1;border-radius:28px;padding:24px;gap:8px"><span style="font-size:24px;font-weight:300;letter-spacing:-.02em">${t}</span><span style="font-size:14px;line-height:20px;color:#A3A7AE">${d}</span></div>`).join('')}</div>
      </div>
    </div>
  </div>`;
  return { html: page(body, { wrap: false, w: W, h: H }), w: W, h: H };
}

// ------------------------------------------------------------------ dawn × 3 modes with haiku
function dawnSheet() {
  const W = 1560, H = 1330;
  const body = `<div style="width:${W}px;height:${H}px;background:#040405;color:#F3F1EC;font-family:var(--sans);padding:64px 72px">
    <span class="mono" style="color:${DAWN}">Рассвет · хокку состояния и настроя</span>
    <div style="font-size:52px;line-height:56px;font-weight:200;letter-spacing:-.045em;margin-top:12px">Солнце встаёт кольцом вокруг Титана — так высоко, как ты восстановился</div>
    <div class="row" style="gap:48px;margin-top:44px;align-items:flex-start">
      ${['razgon', 'rovno', 'berezhno'].map((m) => `<div class="col" style="gap:18px;width:440px"><img src="file://${OUT}/s1-morning-${m}.png" style="width:440px;border-radius:48px;box-shadow:0 0 0 1px rgba(255,255,255,.1)">
        <div class="row" style="gap:10px;color:${DAWNS[m].col}">${picto(m, 22, 1.5)}<span style="font-size:18px;font-weight:500">${DAWNS[m].word}</span><span class="mono" style="color:#80858D;margin-left:auto">ревью 10 · 3</span></div></div>`).join('')}
    </div></div>`;
  return { html: page(body, { wrap: false, w: W, h: H }), w: W, h: H };
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
    ['s1-morning-rovno', () => morning('rovno')], ['s1-morning-razgon', () => morning('razgon')], ['s1-morning-berezhno', () => morning('berezhno')], ['s2-today-dark', () => today('dark')], ['s3-today-light', () => today('light')],
    ['s4-workout-whoop', workout], ['s5-evening', evening], ['s6-mentor', mentor], ['s7-connect-whoop', connect],
    ['s8-camera', camera], ['s9-me-titan', me],
  ];
  for (const [n, fn] of jobs) if (!only || only.includes(n)) await shot(n, fn());
  for (const [n, fn, sc] of [['_dawn-haiku', dawnSheet, 1.25], ['_sync-titan-storyboard', storyboard, 1.5], ['_icons', icons, 1], ['_concept', concept, 1]]) {
    if (only && !only.includes(n)) continue;
    const r = fn();
    await shot(n, r.html, r.w, r.h, sc);
  }
  await browser.close();
})();
