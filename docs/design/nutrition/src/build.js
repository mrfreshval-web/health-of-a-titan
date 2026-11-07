// Builds the HoT nutrition mockups (A41) in the «Титаниум» style (A37, D-031) and renders them to PNG with Playwright.
// Usage (from repo root):
//   python3 docs/design/nutrition/src/getfonts.py     # once, downloads OFL fonts (Geist, Geist Mono, Cormorant SC)
//   node docs/design/nutrition/src/build.js [outDir]  # default outDir: docs/design/nutrition
// Output: n1-levels, n2-profile, n3-day-plan, n4-meal-card, n5-plate-photo (412x915 @2x) and _nutrition.png (sheet).
// styles.css and art.js are copies of docs/design/concept-v3/src (branch claude/concept-v3); keep them in sync.
const fs = require('fs');
const path = require('path');
const { ring, icon, picto } = require('./art');

const SRC = __dirname;
const OUT = path.resolve(process.argv[2] || path.resolve(SRC, '..'));
const HTML = path.join(SRC, 'html');
fs.mkdirSync(HTML, { recursive: true });
fs.mkdirSync(OUT, { recursive: true });

const AQUA = '#7DE3D1', DAWN = '#FFC77A', IRIS = '#A8A3FF', ALERT = '#FF8A80';
// plate roles → colour (one meaning everywhere): vegetables aqua, protein dawn, starch iris, fat ivory
const ROLE = { veg: AQUA, protein: DAWN, starch: IRIS, fat: '#E9E4D8' };

const page = (body, { theme = 'dark', extraCss = '', w = 412, h = 915, wrap = true } = {}) => `<!doctype html>
<html lang="ru"><head><meta charset="utf-8"><title>HoT · Питание</title>
<link rel="stylesheet" href="../fonts/local.css"><link rel="stylesheet" href="../styles.css">
<style>html,body{width:${w}px;height:${h}px;overflow:hidden}${extraCss}</style></head>
<body>${wrap ? `<div class="screen ${theme}">${body}</div>` : body}</body></html>`;

const svg = (inner, size = 24, sw = 1.6, color = 'currentColor') =>
  `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="${color}" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round">${inner}</svg>`;

// ---------------------------------------------------------------- allergen pictograms (24 grid, monoline)
const EAR = 'M12 21V6.5M12 6.5c-1.6-1-1.6-3 0-4 1.6 1 1.6 3 0 4ZM12 10.5c-1.8-.2-3-1.6-3-3.3 1.8.1 3 1.5 3 3.3Zm0 0c1.8-.2 3-1.6 3-3.3-1.8.1-3 1.5-3 3.3ZM12 14.5c-1.8-.2-3-1.6-3-3.3 1.8.1 3 1.5 3 3.3Zm0 0c1.8-.2 3-1.6 3-3.3-1.8.1-3 1.5-3 3.3Z';
const ALLERGEN = {
  gluten: `<path d="${EAR}"/>`,
  wheat: `<path d="${EAR}" transform="translate(-3.5 0)"/><path d="M15.5 21V9.5M15.5 9.5c1.6-.9 3-.2 3.4 1.2M15.5 13.5c1.6-.9 3-.2 3.4 1.2" opacity=".8"/>`,
  crustacean: '<path d="M18.5 7.5c-3.5-3-9.5-2-11.5 2.5-1.6 3.7.5 7.8 4.5 8.5"/><path d="M11.5 18.5c-1.2-2.3-.2-4.6 2-5.4M8 13.5l-3 1.2M8.5 10.5 5.5 9M18.5 7.5 21 6M18.5 7.5l1 3"/><circle cx="16" cy="8.2" r=".8" fill="currentColor"/>',
  mollusc: '<path d="M4 15c0-5 3.6-9 8-9s8 4 8 9H4Z"/><path d="M12 6v9M8.2 7.4 10 15M15.8 7.4 14 15M5.4 10.4 7 15M18.6 10.4 17 15M9 15l-1 3h8l-1-3"/>',
  egg: '<path d="M12 3.5c3.4 0 6 5.2 6 9.3 0 3.6-2.7 6.2-6 6.2s-6-2.6-6-6.2c0-4.1 2.6-9.3 6-9.3Z"/>',
  fish: '<path d="M3 12c3-4.6 9.4-6 14-2l3.5-3v10L17 14c-4.6 4-11 2.6-14-2Z"/><circle cx="7.5" cy="11.2" r=".8" fill="currentColor"/>',
  peanut: '<path d="M12 3c2.6 0 4 2 3.6 4.2-.3 1.5-1.4 2.2-1.4 3.8s1.4 2.3 1.4 4.6C15.6 18.8 14 21 12 21s-3.6-2.2-3.6-5.4c0-2.3 1.4-3 1.4-4.6S8.7 8.7 8.4 7.2C8 5 9.4 3 12 3Z"/><path d="M10.8 6.4h.01M13.2 7.4h.01M11 15.6h.01M13 17.4h.01" stroke-width="2"/>',
  tree_nut: '<path d="M5.5 10c0-3 2.9-5 6.5-5s6.5 2 6.5 5H5.5Z"/><path d="M12 5V3M6.5 10c.5 6 3 10 5.5 11 2.5-1 5-5 5.5-11"/>',
  soy: '<path d="M5 17.5C5 11 10.5 5 18.5 5c.5 7.5-5 13-11 13.5H5v-1Z"/><circle cx="9.5" cy="14" r="1.6"/><circle cx="12.6" cy="11" r="1.6"/><circle cx="15.6" cy="8" r="1.6"/>',
  milk: '<path d="M9 3h6v3l2 3.2V21H7V9.2L9 6V3Z"/><path d="M7 12.5h10M9 6h6"/>',
  sesame: '<path d="M8 4.5c1.6 1.2 1.9 3.8 0 5.2-1.9-1.4-1.6-4 0-5.2ZM16 6.5c1.6 1.2 1.9 3.8 0 5.2-1.9-1.4-1.6-4 0-5.2ZM11 12.5c1.6 1.2 1.9 3.8 0 5.2-1.9-1.4-1.6-4 0-5.2Z"/>',
  celery: '<path d="M9 21 10.5 9M12.5 21V9M16 21l-1.5-12"/><path d="M10.5 9c-3-.4-4.6-2.6-4-5.2 2.4.2 3.9 2.2 4 5.2ZM12.5 9c-.6-2.6.2-4.8 2.4-6 1.2 2.3.4 4.8-2.4 6ZM14.5 9c1-2.2 3-3 5-2.4-.5 2.2-2.4 3-5 2.4Z"/>',
  mustard: '<path d="M9 8h6v2.5c1.7.7 2.5 2 2.5 4V21h-11v-6.5c0-2 .8-3.3 2.5-4V8Z"/><path d="M10.5 8V5.5L12 3l1.5 2.5V8M6.5 15h11"/>',
  sulphite: '<path d="M7.5 3h9c.2 4.5-1.3 8-4.5 8S7.3 7.5 7.5 3Z"/><path d="M12 11v8M8.5 21h7M7.7 6.5h8.6"/>',
  lupin: '<path d="M12 21V9"/><circle cx="12" cy="4.6" r="1.6"/><circle cx="9.6" cy="8" r="1.6"/><circle cx="14.4" cy="8" r="1.6"/><circle cx="9.6" cy="11.6" r="1.6"/><circle cx="14.4" cy="11.6" r="1.6"/><path d="M12 16.5c-2-1.8-4.6-1.6-6 0M12 16.5c2-1.8 4.6-1.6 6 0"/>',
  coconut: '<circle cx="12" cy="12" r="8"/><circle cx="9.8" cy="9.6" r="1" fill="currentColor"/><circle cx="14.2" cy="9.6" r="1" fill="currentColor"/><circle cx="12" cy="13" r="1" fill="currentColor"/>',
};
const allergen = (id, size = 24, sw = 1.5, col = 'currentColor') => svg(ALLERGEN[id], size, sw, col);

// ---------------------------------------------------------------- hand portions
const HAND = {
  palm: '<path d="M7 21v-8.5L5.5 8.5c-.4-1.2 1.2-1.8 1.8-.8L9 11V4.5c0-1.2 1.8-1.2 1.8 0V10V3.6c0-1.2 1.8-1.2 1.8 0V10V4.4c0-1.2 1.8-1.2 1.8 0V11V6.6c0-1.2 1.8-1.2 1.8 0v8.2c0 3.4-1.8 6.2-5 6.2H7"/>',
  cup: '<path d="M3.5 10.5c1 5.6 4.4 8.5 8.5 8.5s7.5-2.9 8.5-8.5"/><path d="M3.5 10.5c2.6 1.3 5.4 1.9 8.5 1.9s5.9-.6 8.5-1.9"/><circle cx="9" cy="9" r="1.1"/><circle cx="12" cy="8.2" r="1.1"/><circle cx="15" cy="9" r="1.1"/><circle cx="10.6" cy="6.6" r="1.1"/><circle cx="13.4" cy="6.6" r="1.1"/>',
  fist: '<path d="M6 9.5c0-1.4 1.1-2.5 2.5-2.5h7c1.4 0 2.5 1.1 2.5 2.5v6c0 3-2.4 5.5-5.5 5.5h-1C8.4 21 6 18.6 6 15.5v-6Z"/><path d="M9.5 7v3.5M12.5 7v3.5M15.5 7v3.5M6 12.5h4.5c1 0 1.5.8 1.5 1.6"/>',
  thumb: '<path d="M10 21V11.5l2.5-6c.5-1.3 2.5-1 2.4.5L14.5 10h4c1.1 0 1.9 1 1.6 2.1l-1.8 6.6c-.3 1.3-1.4 2.3-2.8 2.3H10Z"/><path d="M4.5 11.5H10V21H4.5z"/>',
};
const hand = (k, size = 22, col = 'currentColor') => svg(HAND[k], size, 1.4, col);

// ---------------------------------------------------------------- shared chrome
const statusBar = (time, color = '') => `<div class="statusbar" ${color ? `style="color:${color}"` : ''}><span class="num">${time}</span><span class="icons">
  <svg width="16" height="12" viewBox="0 0 16 12" fill="currentColor"><path d="M15 1v10H1z"/></svg>
  ${icon('wifi', 17, 2)}
  <svg width="24" height="12" viewBox="0 0 24 12"><rect x=".6" y=".6" width="20.5" height="10.8" rx="3.4" fill="none" stroke="currentColor" stroke-width="1.2" opacity=".5"/><rect x="2.4" y="2.4" width="13.5" height="7.2" rx="1.8" fill="currentColor"/><rect x="22" y="4" width="1.6" height="4" rx=".8" fill="currentColor" opacity=".5"/></svg>
  </span></div>`;
const nav = (active) => {
  const items = [['today', 'Сегодня'], ['mentor', 'Винс'], ['week', 'Неделя'], ['me', 'Я']];
  return `<div class="nav glass">${items.map(([k, l]) => `<div class="it ${k === active ? 'on' : ''}">${k === 'me' ? picto('body', 22, 1.5) : icon(k, 22, 1.6)}${k === active ? `<span>${l}</span>` : ''}</div>`).join('')}</div><div class="gesture"></div>`;
};
const topBar = (title, right = '') => `
  <div class="abs row" style="left:16px;right:16px;top:50px;height:44px;justify-content:space-between;z-index:5">
    <div class="center" style="width:44px;height:44px;border-radius:22px;box-shadow:inset 0 0 0 1px var(--hair2)">${icon('back', 20, 1.6)}</div>
    <span class="mono fg2">${title}</span>
    <div style="width:44px;height:44px" class="center">${right}</div>
  </div>`;
const tok = (t, col = '') => `<span class="tok" ${col ? `style="color:${col}"` : ''}>${t}</span>`;

// Plate as a pie: shares by role, drawn with conic-gradient, a knock-out centre and hairline dividers.
function plate(size, share, { inner = .5, glow = true, labels = false } = {}) {
  const order = ['veg', 'protein', 'starch', 'fat'];
  let acc = 0;
  const stops = [];
  const marks = [];
  for (const k of order) {
    const v = share[k] || 0;
    if (!v) continue;
    stops.push(`${ROLE[k]} ${acc * 360}deg ${(acc + v) * 360 - 1.2}deg`, `transparent ${(acc + v) * 360 - 1.2}deg ${(acc + v) * 360}deg`);
    if (labels) {
      const mid = (acc + v / 2) * 2 * Math.PI - Math.PI / 2;
      marks.push({ k, x: Math.cos(mid), y: Math.sin(mid), v });
    }
    acc += v;
  }
  const r = size / 2;
  const lab = { veg: 'овощи', protein: 'белок', starch: 'крупа', fat: 'жир' };
  return `<div class="abs" style="position:relative;width:${size}px;height:${size}px">
    <div class="abs" style="inset:0;border-radius:50%;background:conic-gradient(${stops.join(',')});opacity:.92;${glow ? 'filter:drop-shadow(0 0 18px rgba(125,227,209,.25))' : ''}"></div>
    <div class="abs" style="inset:${size * inner / 2}px;border-radius:50%;background:var(--bg);box-shadow:inset 0 0 0 1px var(--hair2)"></div>
    <div class="abs" style="inset:-6px;border-radius:50%;box-shadow:inset 0 0 0 1px var(--hair)"></div>
    ${marks.filter((m) => m.v >= .2).map((m) => `<div class="abs mono" style="left:${r + m.x * r * .76 - 30}px;top:${r + m.y * r * .76 - 7}px;width:60px;text-align:center;font-size:9.5px;color:#0B0C0F;letter-spacing:.08em">${lab[m.k]}</div>`).join('')}
  </div>`;
}

// ---------------------------------------------------------------- n1 · level choice
function levels() {
  const L = [
    { id: 'base', name: 'Основа', line: 'Тарелка и привычки. Ничего не считаем.', see: ['тарелка', 'порции ладонью'], train: 'Классика', cols: 1 },
    { id: 'structured', name: 'Опора', line: 'Тарелка меняется под нагрузку дня. Еда до и после тренировки.', see: ['тарелка по нагрузке', 'ужин под завтра'], train: 'Продвинутый', cols: 2, on: true },
    { id: 'precise', name: 'Точность', line: 'Углеводы под нагрузку, топливо на длинную сессию.', see: ['граммы диапазоном — по желанию'], train: 'Экстрим', cols: 3 },
  ];
  const glyph = (n, on) => `<svg width="44" height="44" viewBox="0 0 44 44" fill="none" stroke="${on ? AQUA : 'var(--fg2)'}" stroke-width="1.6" stroke-linecap="round">
      <circle cx="22" cy="22" r="20" opacity=".35"/>
      ${n >= 1 ? '<path d="M8 30h28"/>' : ''}${n >= 2 ? '<path d="M14 30V16M30 30V16M12 15.5h4M28 15.5h4"/>' : ''}${n >= 3 ? '<path d="M14 11h16" /><circle cx="22" cy="20" r="2.4"/><path d="M22 22.5V30"/>' : ''}
    </svg>`;
  const card = (l, i) => `
    <div class="glass" style="position:relative;border-radius:28px;padding:18px 18px 16px;${l.on ? `box-shadow:var(--glass-edge),inset 0 0 0 1.5px ${AQUA},0 0 40px -10px rgba(125,227,209,.45)` : ''}">
      <div class="row" style="gap:14px;align-items:flex-start">
        ${glyph(l.cols, l.on)}
        <div class="col" style="gap:6px;flex:1">
          <div class="row" style="justify-content:space-between"><span class="t21" style="font-size:23px;font-weight:400">${l.name}</span>
            <div class="row" style="gap:8px"><span class="mono" style="font-size:9.5px;color:${l.on ? AQUA : 'var(--fg3)'}">${l.train}</span>${l.on ? `<div class="center" style="width:24px;height:24px;border-radius:12px;background:${AQUA};color:#06201B">${icon('check', 15, 2.4)}</div>` : ''}</div></div>
          <span class="b15 fg2">${l.line}</span>
          <div class="row" style="gap:6px;flex-wrap:wrap;margin-top:4px">${l.see.map((s) => tok(s)).join('')}</div>
        </div>
      </div>
    </div>`;
  const body = `
  <div class="abs" style="inset:0;background:radial-gradient(70% 30% at 50% 0%, rgba(125,227,209,.10), transparent 70%)"></div>
  ${statusBar('08:12')}
  ${topBar('питание · уровень')}
  <div class="abs col" style="left:24px;right:24px;top:112px;gap:10px">
    <span class="d34">Как ты хочешь есть</span>
    <span class="b15 fg2">Уровень можно сменить в любой момент. Профиль и самочувствие могут только упростить план — никогда не усложнить.</span>
  </div>
  <div class="abs col" style="left:16px;right:16px;top:236px;gap:12px">${L.map(card).join('')}</div>
  <div class="abs row" style="left:24px;right:24px;top:742px;gap:10px;color:var(--fg3)">
    ${picto('seal', 18, 1.5)}<span class="l13" style="color:var(--fg2);font-weight:400">Калорий нет ни на одном уровне. Цифры появляются только там, где ты сам их включишь.</span>
  </div>
  <div class="abs" style="left:24px;right:24px;bottom:58px"><div class="btn light-pill">Выбрать «Опору»</div></div>
  <div class="gesture"></div>`;
  return page(body);
}

// ---------------------------------------------------------------- n2 · profile of specifics
function profile() {
  const A = [['gluten', 'Глютен'], ['wheat', 'Пшеница'], ['milk', 'Молоко'], ['egg', 'Яйца'], ['peanut', 'Арахис'], ['tree_nut', 'Орехи'], ['soy', 'Соя'], ['sesame', 'Кунжут'],
    ['fish', 'Рыба'], ['crustacean', 'Ракообр.'], ['mollusc', 'Моллюски'], ['celery', 'Сельдерей'], ['mustard', 'Горчица'], ['sulphite', 'Сульфиты'], ['lupin', 'Люпин'], ['coconut', 'Кокос']];
  const sel = { peanut: ['тяжёлая', ALERT], tree_nut: ['аллергия', DAWN] };
  const cell = ([id, ru]) => {
    const s = sel[id];
    return `<div class="col center" style="gap:5px;height:76px;border-radius:20px;position:relative;${s ? `background:${s[1] === ALERT ? 'rgba(255,138,128,.10)' : 'rgba(255,199,122,.10)'};box-shadow:inset 0 0 0 1.4px ${s[1]}` : 'box-shadow:inset 0 0 0 1px var(--hair2)'};color:${s ? s[1] : 'var(--fg2)'}">
      ${allergen(id, 26, 1.5)}<span style="font-size:11.5px;line-height:13px;font-weight:500;color:${s ? 'var(--fg)' : 'var(--fg2)'}">${ru}</span>
      ${s ? `<span class="mono" style="position:absolute;top:-8px;right:6px;font-size:8.5px;letter-spacing:.08em;padding:2px 6px;border-radius:8px;background:${s[1]};color:#14100A">${s[0]}</span>` : ''}
    </div>`;
  };
  const chips = (list) => list.map(([t, on]) => `<span class="chip ${on ? 'on' : ''}" style="${on ? `box-shadow:inset 0 0 0 1.4px ${AQUA};color:var(--fg)` : 'color:var(--fg2)'}">${on ? icon('check', 13, 2.2, AQUA) : ''}${t}</span>`).join('');
  const body = `
  ${statusBar('08:14')}
  ${topBar('я · еда и тело', `<div style="color:var(--fg2)">${icon('lock', 20, 1.6)}</div>`)}
  <div class="abs col" style="left:24px;right:24px;top:110px;gap:6px">
    <span class="d34">Особенности</span>
    <div class="row" style="gap:6px">${tok('только на телефоне', AQUA)}${tok('согласие 07.11')}${tok('удалить')}</div>
  </div>
  <div class="abs col" style="left:16px;right:16px;top:196px;gap:10px">
    <div class="row" style="justify-content:space-between;padding:0 8px"><span class="mono fg2">аллергены · ЕС 14 + США 9</span><span class="mono fg3">2</span></div>
    <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:8px">${A.map(cell).join('')}</div>
  </div>
  <div class="abs col" style="left:24px;right:24px;top:556px;gap:10px">
    <span class="mono fg2">тип питания</span>
    <div class="row" style="gap:6px;flex-wrap:wrap">${chips([['Ем всё', 1], ['Вегетарианство'], ['Веганство'], ['Халяль'], ['Кошер'], ['Без глютена'], ['Без лактозы']])}</div>
  </div>
  <div class="abs col" style="left:16px;right:16px;top:676px;gap:8px">
    <div class="glass row" style="border-radius:22px;padding:12px 16px;gap:12px">
      <div class="center" style="flex:none;width:36px;height:36px;border-radius:18px;box-shadow:inset 0 0 0 1px var(--hair2);color:${IRIS}">${picto('heart', 20, 1.5)}</div>
      <div class="col" style="flex:1"><span class="b15">Состояния по словам врача</span><span class="l13 fg3" style="font-weight:400">беременность · диабет · почки · РПП · ЖКТ</span></div>
      <span class="mono fg2">нет</span>
    </div>
    <div class="glass row" style="border-radius:22px;padding:12px 16px;gap:12px">
      <div class="center" style="flex:none;width:36px;height:36px;border-radius:18px;box-shadow:inset 0 0 0 1px var(--hair2);color:${DAWN}">${picto('body', 20, 1.5)}</div>
      <div class="col" style="flex:1"><span class="b15">Травмы и ограничения</span><span class="l13 fg3" style="font-weight:400">для тренировок · knee_injury</span></div>
      <span class="mono" style="color:${DAWN}">колено</span>
    </div>
  </div>
  <div class="abs l13 fg3" style="left:24px;right:24px;top:812px;font-weight:400;line-height:17px">Подбираем блюда без отмеченного по составу рецепта. Этикетку и кухню кафе проверяй всегда.</div>
  <div class="gesture"></div>`;
  return page(body);
}

// ---------------------------------------------------------------- n3 · day plan on «Сегодня»
function dayPlan() {
  const rows = [
    { t: '07:30', k: 'Завтрак', name: 'Творог, яблоко, тыквенные семечки', hands: [['palm', '1'], ['cup', '1']], st: 'done', swap: 'грецкие орехи → семечки' },
    { t: '13:00', k: 'Обед', name: 'Гречка с курицей и овощами', hands: [['palm', '1'], ['cup', '1'], ['fist', '1½']], st: 'now' },
    { t: '17:15', k: 'До тренировки', name: 'Банан и рисовые хлебцы', hands: [['cup', '1']], st: '' },
    { t: '18:00', k: 'Силовая · 60 мин', name: 'из плана тренировок', st: 'train' },
    { t: '19:30', k: 'После', name: 'Лосось, бурый рис, брокколи', hands: [['palm', '1'], ['cup', '1½'], ['fist', '1']], st: '' },
  ];
  const node = (st) => st === 'done' ? `background:${AQUA};color:#06201B`
    : st === 'now' ? 'background:var(--dawn-fill);color:var(--on-dawn);box-shadow:0 0 0 5px rgba(255,199,122,.14),0 0 22px rgba(255,199,122,.6)'
    : st === 'train' ? `background:var(--bg);color:${DAWN};box-shadow:inset 0 0 0 1px rgba(255,199,122,.5)`
    : 'background:var(--bg);color:var(--fg2);box-shadow:inset 0 0 0 1px var(--hair2)';
  const row = (r, i) => `
    <div class="row" style="position:relative;gap:14px;align-items:flex-start;height:${r.st === 'train' ? 52 : 74}px">
      <span class="mono num" style="width:40px;padding-top:12px;color:${r.st === 'now' ? DAWN : 'var(--fg3)'};letter-spacing:.06em">${r.t}</span>
      <div class="center" style="flex:none;width:38px;height:38px;border-radius:19px;${node(r.st)};z-index:2">${r.st === 'train' ? picto('heavy', 20, 1.5) : r.st === 'done' ? icon('check', 18, 2.2) : icon('plate', 18, 1.6)}</div>
      <div class="col" style="gap:3px;flex:1;padding-top:1px">
        <span class="mono" style="font-size:9.5px;color:${r.st === 'train' ? DAWN : 'var(--fg3)'}">${r.k}</span>
        <span class="b15" style="${r.st === 'train' ? 'color:var(--fg2)' : ''}">${r.name}</span>
        ${r.hands ? `<div class="row" style="gap:10px;color:var(--fg2)">${r.hands.map(([h, n]) => `<span class="row num" style="gap:3px;font-size:12px">${hand(h, 16)}${n}</span>`).join('')}
          ${r.swap ? `<span class="row" style="gap:4px;font-size:11.5px;color:${AQUA}">${picto('changed', 14, 1.6)}${r.swap}</span>` : ''}</div>` : ''}
      </div>
    </div>`;
  const body = `
  <div class="abs" style="inset:0;background:radial-gradient(60% 26% at 30% 16%, rgba(125,227,209,.12), transparent 72%)"></div>
  ${statusBar('12:41')}
  <div class="abs row" style="left:20px;right:16px;top:50px;height:40px;justify-content:space-between">
    <div class="row" style="gap:10px"><div class="center" style="width:32px;height:32px;border-radius:16px;box-shadow:inset 0 0 0 1px var(--hair2)">${picto('food', 20, 1.5)}</div><span class="mono fg2">сегодня · питание</span></div>
    <div class="capsule glass"><div class="seg" style="color:${DAWN}">${icon('sun', 18, 1.6)}<span class="num">кофе до 14:15</span></div></div>
  </div>
  <div class="abs row" style="left:24px;right:20px;top:112px;gap:20px;align-items:center">
    ${plate(132, { veg: .4, protein: .25, starch: .3, fat: .05 })}
    <div class="col" style="gap:6px;flex:1">
      <span class="mono" style="color:${AQUA}">Опора · ровно</span>
      <span class="t21" style="font-size:24px;line-height:28px">Рабочий день</span>
      <span class="l13 fg2" style="font-weight:400;line-height:18px">Вечером силовая — к обеду горсть крупы, после — белок и рис.</span>
    </div>
  </div>
  <div class="abs row" style="left:24px;top:262px;gap:14px;color:var(--fg2)">
    ${[['veg', 'овощи'], ['protein', 'белок'], ['starch', 'крупа']].map(([k, l]) => `<span class="row" style="gap:6px;font-size:12px"><span style="width:8px;height:8px;border-radius:4px;background:${ROLE[k]}"></span>${l}</span>`).join('')}
    <span class="row" style="gap:5px;font-size:12px;color:${AQUA}">${picto('seal', 14, 1.6)}без арахиса и орехов</span>
  </div>
  <div class="abs" style="left:82px;top:322px;width:1px;height:372px;background:linear-gradient(180deg,${AQUA} 0,${DAWN} 26%,var(--hair2) 34%,var(--hair2) 100%)"></div>
  <div class="abs col" style="left:24px;right:20px;top:304px">${rows.map(row).join('')}</div>
  <div class="abs row" style="left:24px;right:16px;top:676px;gap:8px">
    ${['Нет времени', 'Ем вне дома', 'В дороге'].map((c) => `<span class="chip" style="color:var(--fg2)">${c}</span>`).join('')}
  </div>
  <div class="abs row" style="left:24px;right:24px;top:726px;gap:10px;color:var(--fg3)">${icon('moon', 16, 1.6, IRIS)}<span class="l13" style="font-weight:400">Ужин в 20:30 · отбой 23:15</span></div>
  ${nav('today')}`;
  return page(body);
}

// ---------------------------------------------------------------- n4 · meal card
function mealCard() {
  const portions = [['palm', 'ладонь', 'курица', ROLE.protein], ['cup', 'горсть', 'гречка', ROLE.starch], ['fist', '1½ кулака', 'овощи', ROLE.veg], ['thumb', 'палец', 'масло', ROLE.fat]];
  const body = `
  <div class="abs" style="inset:0;background:radial-gradient(70% 34% at 50% 18%, rgba(255,199,122,.10), transparent 70%)"></div>
  ${statusBar('12:58')}
  ${topBar('обед · 13:00', `<div style="color:var(--fg2)">${icon('dots', 20, 1.6)}</div>`)}
  <div class="abs center" style="left:0;right:0;top:110px">${plate(196, { veg: .4, protein: .25, starch: .3, fat: .05 }, { labels: true })}</div>
  <div class="abs col" style="left:24px;right:24px;top:326px;gap:8px">
    <span class="d34" style="font-size:30px;line-height:34px">Гречка с курицей и овощами</span>
    <div class="row" style="gap:6px">${tok('15 мин')}${tok('дом · офис')}${tok('рабочая тарелка', AQUA)}</div>
  </div>
  <div class="abs row" style="left:16px;right:16px;top:444px;gap:8px">
    ${portions.map(([h, n, w, c]) => `<div class="glass col center" style="flex:1;height:86px;border-radius:20px;gap:4px">
      <div style="color:${c}">${hand(h, 26)}</div><span style="font-size:13px;font-weight:500">${n}</span><span class="mono fg3" style="font-size:9px">${w}</span></div>`).join('')}
  </div>
  <div class="abs glass" style="left:16px;right:16px;top:544px;border-radius:22px;padding:14px 16px">
    <div class="row" style="gap:10px">
      <div class="center" style="flex:none;width:34px;height:34px;border-radius:17px;background:rgba(125,227,209,.14);box-shadow:inset 0 0 0 1px rgba(125,227,209,.5);color:${AQUA}">${picto('seal', 20, 1.6)}</div>
      <div class="col" style="flex:1"><span class="b15">Проверено по твоему профилю</span><span class="l13 fg3" style="font-weight:400">по составу: нет арахиса и орехов</span></div>
      <div class="row" style="gap:6px;color:var(--fg2)">${['peanut', 'tree_nut'].map((a) => `<div style="position:relative">${allergen(a, 22, 1.4)}<div class="abs" style="left:-2px;right:-2px;top:10px;height:1.6px;background:${AQUA};transform:rotate(-40deg)"></div></div>`).join('')}</div>
    </div>
  </div>
  <div class="abs col" style="left:24px;right:24px;top:636px;gap:8px">
    <span class="mono fg2">почему</span>
    <span class="b15" style="color:var(--fg)">Вечером силовая — горсть крупы сейчас даст топливо. Белок — ладонь, как в каждом приёме.</span>
    <div class="row" style="gap:6px">${tok('A · Thomas 2016')}${tok('замена: курица → тофу')}</div>
  </div>
  <div class="abs row" style="left:16px;right:16px;bottom:52px;gap:10px">
    <div class="btn ghost" style="flex:1">${picto('changed', 20, 1.6)}Заменить</div>
    <div class="btn light-pill" style="flex:1.3">${icon('check', 18, 2)}Ем это</div>
  </div>
  <div class="gesture"></div>`;
  return page(body);
}

// ---------------------------------------------------------------- n5 · plate photo with feedback
function platePhoto() {
  const cx = 206, cy = 330, R = 150;
  // a photographed plate, painted with soft blobs (buckwheat, chicken, cucumber/tomato)
  const food = `
    <div class="abs" style="left:${cx - R}px;top:${cy - R}px;width:${2 * R}px;height:${2 * R}px;border-radius:50%;background:radial-gradient(circle at 45% 40%,#F4F1EA,#D9D4C9 62%,#B9B3A7 70%,#8F8A80 71%,#CFC9BD 73%,#A9A398 100%);box-shadow:0 30px 60px -20px rgba(0,0,0,.8)"></div>
    <div class="abs" style="left:${cx - 92}px;top:${cy - 6}px;width:120px;height:92px;border-radius:50% 46% 52% 48%;background:radial-gradient(circle at 40% 40%,#9A6B3F,#6E4626 70%);filter:blur(1.5px)"></div>
    <div class="abs" style="left:${cx - 96}px;top:${cy - 6}px;width:120px;height:92px;border-radius:50%;background:repeating-radial-gradient(circle at 30% 30%,rgba(255,220,170,.25) 0 1px,transparent 1px 4px);opacity:.6"></div>
    <div class="abs" style="left:${cx - 6}px;top:${cy - 92}px;width:96px;height:74px;border-radius:44% 56% 50% 50%;background:linear-gradient(160deg,#F1D9B0,#D7AE73 60%,#B98A50);transform:rotate(-14deg);filter:blur(.8px)"></div>
    <div class="abs" style="left:${cx + 8}px;top:${cy - 62}px;width:70px;height:6px;border-radius:3px;background:rgba(140,90,40,.45);transform:rotate(-14deg)"></div>
    ${[[-70, -60, '#5E9C4E'], [-50, -82, '#79B062'], [-88, -36, '#5E9C4E'], [36, 20, '#D9503C'], [58, 34, '#C8432F'], [30, 46, '#79B062']].map(([x, y, c]) => `<div class="abs" style="left:${cx + x}px;top:${cy + y}px;width:34px;height:30px;border-radius:50%;background:radial-gradient(circle at 40% 35%,${c},${c}AA);box-shadow:inset 0 0 0 3px rgba(255,255,255,.18);filter:blur(.6px)"></div>`).join('')}`;
  const arc = (a0, a1, col, label, ok, lx, ly) => {
    const p = (a) => [cx + (R + 16) * Math.cos(a), cy + (R + 16) * Math.sin(a)];
    const [x0, y0] = p(a0), [x1, y1] = p(a1);
    return `<path d="M${x0} ${y0} A${R + 16} ${R + 16} 0 ${a1 - a0 > Math.PI ? 1 : 0} 1 ${x1} ${y1}" stroke="${col}" stroke-width="5" stroke-linecap="round" fill="none" style="filter:drop-shadow(0 0 8px ${col})"/>
      <foreignObject x="${lx}" y="${ly}" width="120" height="24"><div xmlns="http://www.w3.org/1999/xhtml" class="mono" style="display:flex;gap:5px;align-items:center;color:${col};font-size:10px">${ok ? icon('check', 12, 2.4) : '·'}${label}</div></foreignObject>`;
  };
  const d = Math.PI / 180;
  const body = `
  <div class="abs" style="inset:0;background:radial-gradient(80% 60% at 50% 36%,#2A2724,#151416 70%,#0B0B0C)"></div>
  ${food}
  <div class="abs" style="left:${cx - R - 2}px;top:${cy - R - 2}px;width:${2 * R + 4}px;height:${2 * R + 4}px;border-radius:50%;border:2px dashed rgba(255,255,255,.75)"></div>
  <svg class="abs" style="left:0;top:0" width="412" height="915" viewBox="0 0 412 915">
    ${arc(-150 * d, -40 * d, DAWN, 'белок', true, 250, 150)}
    ${arc(110 * d, 215 * d, IRIS, 'крупа', true, 12, 452)}
    ${arc(-20 * d, 95 * d, AQUA, 'овощи · мало', false, 300, 470)}
  </svg>
  ${statusBar('13:12', '#F3F1EC')}
  <div class="abs row" style="left:16px;right:16px;top:50px;height:48px;justify-content:space-between;color:#F3F1EC">
    <div class="center" style="width:44px;height:44px;border-radius:22px;background:rgba(0,0,0,.35)">${icon('close', 20, 1.6)}</div>
    <span class="mono" style="color:rgba(243,241,236,.7)">тарелка · обед</span>
    <div class="center" style="width:44px;height:44px;border-radius:22px;background:rgba(0,0,0,.35)">${picto('lightq', 20, 1.5)}</div>
  </div>
  <div class="abs glass" style="left:12px;right:12px;bottom:28px;border-radius:30px;padding:18px 18px 16px;background:linear-gradient(180deg,rgba(30,32,36,.88),rgba(18,19,22,.94))">
    <span class="mono fg3">похоже на</span>
    <div class="row" style="justify-content:space-between;margin-top:6px;gap:10px">
      <span class="t21">Гречка с курицей и огурцами</span>
      <div class="row" style="gap:6px"><span class="chip on" style="box-shadow:inset 0 0 0 1.4px ${AQUA}">${icon('check', 13, 2.2, AQUA)}да</span><span class="chip" style="color:var(--fg2)">другое</span></div>
    </div>
    <div class="hr" style="margin:14px 0"></div>
    <div class="row" style="gap:12px;align-items:flex-start">
      <div style="flex:none">${plate(64, { veg: .2, protein: .3, starch: .5 }, { glow: false, inner: .42 })}</div>
      <div class="col" style="gap:6px">
        <span class="b16" style="font-weight:500">По тарелке — белок и крупа на месте</span>
        <span class="b15 fg2">Овощей можно больше: добавь кулак овощей к ужину.</span>
      </div>
    </div>
    <div class="row" style="justify-content:space-between;margin-top:14px">
      <span class="mono fg3" style="font-size:9.5px">отклик по форме тарелки · без калорий</span>
      <span class="mono" style="font-size:9.5px;color:${AQUA}">в «Топливо» ✓</span>
    </div>
  </div>
  <div class="gesture" style="background:#fff"></div>`;
  return page(body);
}

// ---------------------------------------------------------------- sheet
function sheet(files) {
  const W = 2380, H = 1240;
  const names = [['n1-levels', 'Уровень', 'Три уровня в паре с тренировками; калорий нет'], ['n2-profile', 'Особенности', 'Аллергены пиктограммами, тип питания, безопасный режим'],
    ['n3-day-plan', 'План дня', 'Тарелка под нагрузку, приёмы вокруг тренировки'], ['n4-meal-card', 'Блюдо', 'Порции ладонью, щит аллергенов, почему'], ['n5-plate-photo', 'Фото тарелки', 'Отклик по форме тарелки в 1 тап']];
  const body = `<div style="width:${W}px;height:${H}px;background:#060708;color:#F3F1EC;font-family:var(--sans);position:relative;overflow:hidden">
    <div class="abs" style="inset:0;background:radial-gradient(50% 40% at 50% 0%,rgba(125,227,209,.10),transparent 70%)"></div>
    <div class="abs col" style="left:64px;top:52px;gap:8px">
      <span class="mono" style="color:#A3A7AE">HoT · A41 · Титаниум</span>
      <span style="font-size:48px;line-height:52px;font-weight:200;letter-spacing:-.04em">Питание: три уровня, профиль особенностей, план дня</span>
      <span style="font-size:17px;color:#A3A7AE">Основа · Опора · Точность — без калорий по умолчанию. Сначала жёсткие исключения профиля, потом тарелка под нагрузку WHOOP.</span>
    </div>
    <div class="abs row" style="left:64px;top:220px;gap:40px">
      ${names.map(([f, t, s]) => `<div class="col" style="gap:14px;width:412px">
        <img src="${files[f]}" style="width:412px;height:915px;border-radius:36px;box-shadow:0 0 0 1px rgba(255,255,255,.10),0 30px 80px -30px rgba(0,0,0,.9)">
        <div class="col" style="gap:2px"><span style="font-size:20px;font-weight:500">${t}</span><span style="font-size:14px;color:#80858D">${s}</span></div></div>`).join('')}
    </div></div>`;
  return { html: page(body, { wrap: false, w: W, h: H }), w: W, h: H };
}

// ---------------------------------------------------------------- render
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
    const out = path.join(OUT, `${name}.png`);
    await p.screenshot({ path: out });
    await ctx.close();
    console.log('✓', out);
    return out;
  };
  const jobs = [['n1-levels', levels], ['n2-profile', profile], ['n3-day-plan', dayPlan], ['n4-meal-card', mealCard], ['n5-plate-photo', platePhoto]];
  const files = {};
  for (const [n, fn] of jobs) files[n] = await shot(n, fn());
  const r = sheet(Object.fromEntries(Object.entries(files).map(([k, v]) => [k, 'file://' + v])));
  await shot('_nutrition', r.html, r.w, r.h, 1);
  await browser.close();
})();
