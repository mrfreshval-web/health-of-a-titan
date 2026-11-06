// Builds the HoT key-screen mockups (A36) as HTML and renders them to PNG with Playwright.
// Usage (from repo root):
//   python3 docs/design/visual-language/src/getfonts.py   # once, downloads OFL fonts
//   node docs/design/visual-language/src/build.js
// Output: docs/design/visual-language/*.png and _screens.png
const fs = require('fs');
const path = require('path');
const { pulsePath, monogram, laurel, column, hMark, modeGlyph, icon } = require('./art');

const SRC = __dirname;
const OUT = path.resolve(SRC, '..');
const HTML = path.join(SRC, 'html');
fs.mkdirSync(HTML, { recursive: true });

const page = (body, { theme = 'dark', title = 'HoT', extraCss = '' } = {}) => `<!doctype html>
<html lang="ru"><head><meta charset="utf-8"><title>${title}</title>
<link rel="stylesheet" href="../fonts/local.css"><link rel="stylesheet" href="../styles.css">
<style>${extraCss}</style></head><body><div class="screen ${theme}">${body}</div></body></html>`;

const statusBar = (time, { color } = {}) => `<div class="statusbar" ${color ? `style="color:${color}"` : ''}><span class="num">${time}</span>
  <span class="icons">
   <svg width="17" height="12" viewBox="0 0 17 12" fill="currentColor"><rect x="0" y="8" width="3" height="4" rx=".8"/><rect x="4.6" y="5.5" width="3" height="6.5" rx=".8"/><rect x="9.2" y="3" width="3" height="9" rx=".8"/><rect x="13.8" y="0" width="3" height="12" rx=".8"/></svg>
   ${icon('wifi', 17, 2)}
   <svg width="26" height="13" viewBox="0 0 26 13"><rect x=".7" y=".7" width="22" height="11.6" rx="3.2" fill="none" stroke="currentColor" stroke-width="1.2" opacity=".6"/><rect x="2.6" y="2.6" width="15" height="7.8" rx="1.8" fill="currentColor"/><rect x="23.8" y="4.3" width="1.6" height="4.4" rx=".8" fill="currentColor" opacity=".6"/></svg>
  </span></div>`;

const navBar = (active) => {
  const items = [['today', 'Сегодня'], ['mentor', 'Наставник'], ['week', 'Неделя'], ['me', 'Я']];
  return `<div class="navbar">${items.map(([k, l]) => `<div class="item ${k === active ? 'on' : ''}"><div class="ind">${icon(k, 24, 1.5)}</div>${l}</div>`).join('')}</div><div class="gesture"></div>`;
};

const sosPill = () => `<div class="sos-pill" aria-label="SOS, срочная поддержка">${icon('sos', 20, 1.5)}SOS</div>`;

// ------------------------------------------------------------------ data
const TODAY_LEVELS = [-0.6, null, null, 0.2, null, 0.5, null, 0.4]; // Заряд…Весы (A25: 4 facets in MVP)
const WEEK_LEVELS = [0.3, null, null, 0.1, null, 0.8, null, 0.0];

// ------------------------------------------------------------------ a1 · notification + widget on home screen
function a1() {
  const widget = `
  <div class="abs" style="left:16px;right:16px;top:380px;height:200px;border-radius:28px;background:linear-gradient(160deg,#161C28,#0D1119);border:1px solid rgba(217,181,98,.22);overflow:hidden">
    <div class="abs" style="left:22px;top:20px">
      <div class="ov">Вторник · план дня</div>
      <div class="row" style="gap:8px;margin-top:6px;color:var(--rovno)">${modeGlyph('rovno', 26, 'var(--rovno)')}<span class="headline-l" style="font-size:40px;line-height:44px">Ровно</span></div>
      <div class="body-m muted" style="margin-top:2px">без интервалов</div><div class="body-m muted">Заряд <span class="num" style="color:var(--ivory);font-weight:600">44</span> · норма 50</div>
    </div>
    <div class="abs glow" style="right:20px;top:20px">${monogram(TODAY_LEVELS, 100, { id: 'w1' })}</div>
    <div class="abs row" style="left:14px;right:14px;bottom:14px;gap:8px">
      <div class="row" style="flex:1;height:48px;border-radius:24px;background:rgba(237,229,211,.06);padding:0 14px;gap:10px;font-size:14px">
        <span class="num gold" style="font-weight:700">15:00</span><span>Питч</span><span class="muted">·</span><span class="num gold" style="font-weight:700">18:00</span><span>Zone 2</span></div>
      <div class="iconbtn" style="background:rgba(237,229,211,.06);color:var(--ivory)">${icon('sos', 22)}</div>
    </div>
  </div>`;
  const appIcon = (inner, label, bg = '#1F2530') => `<div style="width:72px;display:flex;flex-direction:column;align-items:center;gap:6px;font-size:12px;color:#E8E4DA">
      <div style="width:58px;height:58px;border-radius:29px;background:${bg};display:flex;align-items:center;justify-content:center;color:#C9D1DC">${inner}</div>${label}</div>`;
  const body = `
  <div class="abs" style="inset:0;background:radial-gradient(90% 60% at 30% 20%,#1D2638 0%,#0E131C 55%,#07090D 100%)"></div>
  ${statusBar('6:52')}
  <div class="abs" style="left:26px;top:252px;color:#E8E4DA">
    <div class="num" style="font-size:64px;line-height:64px;font-weight:400;letter-spacing:-1px">6:52</div>
    <div style="font-size:16px;margin-top:8px;opacity:.85">Вт, 10 ноября · +4° · ясно</div>
  </div>
  ${widget}
  <div class="abs row" style="left:20px;right:20px;top:650px;justify-content:space-between">
    ${appIcon(icon('phone', 26), 'Телефон')}${appIcon(icon('chat', 26), 'Сообщения')}${appIcon(icon('camera', 26), 'Камера')}
    ${appIcon(hMark(46, '#D9B562'), 'HoT', 'radial-gradient(circle at 50% 40%,#1A2131,#0A0D14)')}
  </div>
  <div class="abs row" style="left:20px;right:20px;top:770px;height:56px;border-radius:28px;background:#1C222D;padding:0 18px;gap:12px;color:#A9B2BF;font-size:15px">${icon('search', 22)}Поиск</div>
  <!-- heads-up notification (system surface, brand only via icon + accent) -->
  <div class="abs" style="left:10px;right:10px;top:44px;border-radius:26px;background:#232933;box-shadow:0 18px 40px rgba(0,0,0,.55);padding:16px 18px 10px;color:#ECEAE4">
    <div class="row" style="gap:8px;font-size:12.5px;color:#B9C0CA">
      <div style="width:22px;height:22px;border-radius:11px;background:#0B0E15;display:flex;align-items:center;justify-content:center">${hMark(20, '#D9B562')}</div>
      <span style="color:#D9B562;font-weight:700">HoT</span><span>· сейчас</span><span style="margin-left:auto">${icon('chev', 16)}</span>
    </div>
    <div style="font-weight:700;font-size:16px;line-height:22px;margin-top:10px">Ровно · без интервалов</div>
    <div style="font-size:14.5px;line-height:21px;color:#C9CED6;margin-top:3px">Ночь 5 ч 50 мин, восстановление ниже нормы. Главное — питч в 15:00, вечером Zone 2.</div>
    <div class="row" style="gap:8px;margin-top:12px">
      <div class="row" style="height:40px;padding:0 16px;border-radius:20px;background:#343B47;font-weight:700;font-size:14px;color:#F0D79A">Колода · 1 мин</div>
      <div class="row" style="height:40px;padding:0 16px;border-radius:20px;background:#343B47;font-weight:700;font-size:14px;color:#F0D79A">Тренировка</div>
    </div>
  </div>
  <div class="gesture" style="background:#E8E4DA"></div>`;
  return page(body, { title: 'a1 Утренний пуш' });
}

// ------------------------------------------------------------------ a2 · Сегодня (верх): состояние, «Сейчас», Карта дня
function todayTop(theme = 'dark') {
  const metric = (label, value, unit, cap, bar) => `<div style="flex:1">
      <div class="body-s muted">${label}</div>
      <div class="num" style="font-weight:600;font-size:24px;line-height:30px;margin-top:2px">${value}<span style="font-size:13px;font-weight:500;color:var(--ivory2);margin-left:3px">${unit}</span></div>
      ${bar ? `<div style="position:relative;height:10px;margin-top:4px"><div style="position:absolute;left:0;right:8px;top:4px;height:2px;border-radius:1px;background:var(--hair)"></div>
        <div style="position:absolute;left:${bar[0]}%;width:${bar[1]}%;top:2px;height:6px;border-radius:3px;background:var(--rule-soft)"></div>
        <div style="position:absolute;left:calc(${bar[2]}% - 3px);top:1px;width:8px;height:8px;border-radius:4px;background:var(--gold)"></div></div>` : ''}
      <div class="body-s muted" style="margin-top:2px">${cap}</div></div>`;
  const tile = (ic, label, val, sub) => `<div class="tile" style="padding:11px 10px 11px 12px">
      <div class="row" style="gap:6px;color:var(--gold)">${icon(ic, 17, 1.4)}<span class="ov" style="letter-spacing:.1em;font-size:10.5px">${label}</span></div>
      <div class="title-s" style="margin-top:7px;font-size:14px;white-space:nowrap">${val}</div><div class="body-s muted" style="margin-top:1px;font-size:12px;white-space:nowrap">${sub}</div></div>`;
  const body = `
  ${statusBar('13:32')}
  <div class="pad row between" style="margin-top:4px;align-items:flex-end">
    <div><div class="ov">Вторник · 10 ноября</div><div class="headline-l" style="margin-top:0">Сегодня</div></div>
    <div style="margin-bottom:4px">${sosPill()}</div>
  </div>
  <div class="card gild" style="margin:12px 16px 0;padding:16px 18px 2px;overflow:hidden">
    <div class="row" style="gap:10px;margin-top:6px;color:var(--rovno)">${modeGlyph('rovno', 30, 'var(--rovno)')}<span class="display-m" style="color:var(--rovno)">Ровно</span></div>
    <div class="body-m muted" style="margin-top:2px">режим дня · без интервалов</div>
    <div class="abs ${theme === 'dark' ? 'glow' : ''}" style="right:16px;top:10px">${monogram(TODAY_LEVELS, 112, { id: 'h' + theme })}</div>
    <div class="quote" style="margin-top:26px;font-size:19.5px;line-height:25px">Ночь короче обычной, но пульс покоя спокойный. Держим ровный темп — главное сегодня питч.</div>
    <div class="rule" style="margin:14px 0 10px"></div>
    <div class="row" style="gap:12px;align-items:flex-start">
      ${metric('Заряд', '44', '', 'норма 50', [36, 26, 41])}
      ${metric('Сон', '5:50', 'ч', 'обычно 7:10')}
      ${metric('Весы', '+0,4', '', 'в балансе')}
    </div>
    <div class="row between" style="margin-top:4px"><div class="btn text">Почему так ${icon('chev', 18)}</div><div class="btn text" style="color:var(--ivory2)">Не согласен ${icon('chev', 18)}</div></div>
  </div>
  <div class="card" style="margin:10px 16px 0;padding:12px 12px 12px 18px">
    <div class="row between"><div><div class="ov">Сейчас · 13:35–14:20</div>
      <div class="title-m" style="margin-top:3px;font-size:17px">Фокус: слайд с цифрами</div>
      <div class="body-s st-swap" style="margin-top:2px;display:flex;gap:4px;align-items:center">${icon('swap', 13, 1.8)}<span class="muted">изменено вместе с Винсом</span></div></div>
      <div class="btn primary" style="height:48px;padding:0 20px">Начать</div></div>
  </div>
  <div class="pad row between" style="margin-top:16px;align-items:baseline"><div class="headline-s">Карта дня</div><div class="body-s muted">потолки режима «Ровно»</div></div>
  <div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:8px;margin:8px 16px 0">
    ${tile('body', 'Тело', 'Zone 2 · 40′', 'без интервалов')}
    ${tile('work', 'Работа', 'Одна главная', 'питч в 15:00')}
    ${tile('moon', 'Сон', 'Отбой 22:45', 'на 30 мин раньше')}
    ${tile('plate', 'Еда', 'Тарелка дня', 'ужин до 19:45')}
    ${tile('cup', 'Кофе', 'До 13:45', 'бережём сон')}
    ${tile('breath', 'Голова', 'Выдох 3 мин', 'в 14:45')}
  </div>
  ${navBar('today')}`;
  return page(body, { theme, title: 'a2 Сегодня' });
}

// ------------------------------------------------------------------ a3 · Сегодня (прокрутка): план по часам со статусами
function todayPlan() {
  const S = {
    whoop: (t) => `<span class="status st-whoop">${icon('check', 14, 2.2)}подтверждено WHOOP${t ? `<span class="muted" style="font-weight:500"> · ${t}</span>` : ''}</span>`,
    self: (t) => `<span class="status st-self">${icon('check', 14, 2.2)}отмечено тобой${t ? `<span style="font-weight:500"> · ${t}</span>` : ''}</span>`,
    swap: (t) => `<span class="status st-swap">${icon('swap', 14, 2)}изменено${t ? `<span class="muted" style="font-weight:500"> · ${t}</span>` : ''}</span>`,
  };
  const node = (k) => {
    if (k === 'whoop') return `<div style="width:22px;height:22px;border-radius:11px;background:var(--gold);color:var(--on-gold);display:flex;align-items:center;justify-content:center">${icon('check', 14, 2.4)}</div>`;
    if (k === 'self') return `<div style="width:22px;height:22px;border-radius:11px;border:1.4px solid var(--ivory2);color:var(--ivory);display:flex;align-items:center;justify-content:center;background:var(--bg)">${icon('check', 13, 2.2)}</div>`;
    if (k === 'swap') return `<div style="width:22px;height:22px;border-radius:11px;border:1.4px solid var(--rovno);color:var(--rovno);display:flex;align-items:center;justify-content:center;background:var(--bg)">${icon('swap', 13, 2)}</div>`;
    if (k === 'now') return `<div class="glow" style="width:22px;height:22px;border-radius:11px;border:1.5px solid var(--gold);display:flex;align-items:center;justify-content:center;background:var(--bg)"><div style="width:8px;height:8px;border-radius:4px;background:var(--gold)"></div></div>`;
    return `<div style="width:22px;height:22px;display:flex;align-items:center;justify-content:center"><div style="width:9px;height:9px;border-radius:5px;border:1.3px solid var(--ivory3);background:var(--bg)"></div></div>`;
  };
  const items = [
    ['06:45', 'Стакан воды', 'self', S.self()],
    ['07:00', 'Прогулка с собакой · утренний свет', 'whoop', S.whoop('22 мин')],
    ['07:30', 'Завтрак с белком', 'self', S.self()],
    ['08:00', 'Фокус: подготовка питча', 'swap', S.swap('сдвинул на 08:30')],
    ['10:00', 'Встречи · встать и пройтись 3 мин', 'self', S.self('2 перерыва')],
    ['12:40', 'Обед по тарелке', 'self', S.self('частично')],
    ['13:35', 'Фокус: слайд с цифрами · 45 мин', 'now', ''],
    ['14:45', 'Три минуты длинного выдоха', 'next', '<span class="body-s muted">перед питчем · Винс напомнит</span>'],
    ['15:00', 'Питч инвесторам', 'next', '<span class="body-s muted">календарь · ключевое событие дня</span>'],
    ['18:00', 'Zone 2 · 40 мин, улица', 'next', '<span class="body-s muted">сверю с WHOOP после тренировки</span>'],
    ['19:15', 'Ужин — закончить до 19:45', 'next', ''],
    ['22:45', 'Отбой', 'next', ''],
  ];
  const rows = items.map(([t, title, k, st]) => {
    const now = k === 'now';
    const past = ['self', 'whoop', 'swap'].includes(k);
    const content = now ? `<div class="card gild" style="padding:12px 14px;margin:-4px 0 4px">
          <div class="ov" style="font-size:10.5px">Сейчас</div>
          <div class="title-m" style="margin-top:2px">Фокус: слайд с цифрами</div>
          <div class="row between" style="margin-top:8px"><span class="body-s muted">45 мин · без почты</span><div class="btn primary" style="height:44px;padding:0 18px;font-size:14px">Начать</div></div></div>`
      : `<div style="padding-bottom:2px"><div class="title-s" style="font-weight:${past ? 500 : 600};color:${past ? 'var(--ivory2)' : 'var(--ivory)'};font-size:15px">${title}</div>${st ? `<div style="margin-top:2px">${st}</div>` : ''}</div>`;
    return `<div style="display:grid;grid-template-columns:50px 22px 1fr;column-gap:12px;align-items:start;min-height:${now ? 112 : st ? 54 : 40}px">
      <div class="num" style="font-weight:600;font-size:14px;line-height:22px;color:${now ? 'var(--gold)' : past ? 'var(--ivory3)' : 'var(--ivory)'}">${t}</div>
      <div style="display:flex;justify-content:center;position:relative;z-index:1">${node(k)}</div>${content}</div>`;
  }).join('');
  const body = `
  ${statusBar('13:32')}
  <div class="pad row between" style="height:60px;border-bottom:1px solid var(--rule-soft)">
    <div class="row" style="gap:10px"><span class="headline-s">Сегодня</span>
      <span class="chip" style="color:var(--rovno);border-color:transparent;background:var(--rovno-c)">${modeGlyph('rovno', 16, 'var(--rovno)')}Ровно</span></div>
    ${sosPill()}
  </div>
  <div class="pad" style="margin-top:14px"><div class="headline-s">План по часам</div><div class="body-s muted" style="margin-top:2px">сверяю с WHOOP и твоими отметками — без очков и серий</div></div>
  <div class="pad" style="margin-top:16px;position:relative">
    <div class="abs" style="left:94px;top:8px;bottom:0;width:1px;background:linear-gradient(180deg,var(--gold),var(--rule) 55%,var(--rule-soft))"></div>
    ${rows}
  </div>
  ${navBar('today')}`;
  return page(body, { title: 'a3 План по часам' });
}

// ------------------------------------------------------------------ b1 · тренировка идёт
function workout() {
  const R = 124, C = 2 * Math.PI * R, prog = 24.27 / 40;
  const body = `
  ${statusBar('18:24')}
  <div class="row between" style="padding:4px 12px 0 8px">
    <div class="iconbtn">${icon('back', 24)}</div>
    <div class="btn outline" style="height:44px;font-size:14px;padding:0 16px;border-color:rgba(237,229,211,.28)">Мне плохо</div>
  </div>
  <div class="pad" style="margin-top:6px">
    <div class="ov">Тренировка дня · Ровно</div>
    <div class="headline-l" style="margin-top:4px">Zone 2 · 40 минут</div>
    <div class="body-m muted" style="margin-top:2px">улица · разговорный темп</div>
  </div>
  <div class="pad" style="display:flex;gap:6px;margin-top:18px;align-items:flex-start">
    ${[['Разминка', '5′', 1.25, 1], ['Основная часть', '30′', 3, 0.81], ['Заминка', '5′', 1.25, 0]].map(([l, m, w, p]) => `<div style="flex:${w}">
      <div style="height:4px;border-radius:2px;background:var(--hair);overflow:hidden"><div style="width:${p * 100}%;height:100%;background:var(--gold)"></div></div>
      <div class="body-s" style="margin-top:6px;white-space:nowrap;color:${p > 0 && p < 1 ? 'var(--ivory)' : 'var(--ivory2)'}">${l} ${m}</div></div>`).join('')}
  </div>
  <div style="position:relative;width:300px;height:300px;margin:34px auto 0">
    <svg class="abs" style="left:-56px;top:128px;opacity:.28" width="412" height="44"><path d="${pulsePath(0, 22, 412, 3, 20, 0.04)}" fill="none" stroke="var(--gold)" stroke-width="1"/></svg>
    <svg class="abs" width="300" height="300" viewBox="0 0 300 300">
      <circle cx="150" cy="150" r="${R + 14}" fill="none" stroke="var(--gold)" stroke-width=".7" opacity=".35"/>
      <circle cx="150" cy="150" r="${R}" fill="#0B0E15" stroke="var(--hair)" stroke-width="3"/>
      <g class="glow"><circle cx="150" cy="150" r="${R}" fill="none" stroke="var(--gold)" stroke-width="3" stroke-linecap="round"
        stroke-dasharray="${C * prog} ${C}" transform="rotate(-90 150 150)"/></g>
      ${Array.from({ length: 40 }, (_, i) => { const a = (i / 40) * 2 * Math.PI - Math.PI / 2; const r1 = R + 14, r2 = R + (i % 5 === 0 ? 20 : 17);
        return `<path d="M${150 + r1 * Math.cos(a)} ${150 + r1 * Math.sin(a)} L${150 + r2 * Math.cos(a)} ${150 + r2 * Math.sin(a)}" stroke="var(--gold)" stroke-width=".8" opacity="${i % 5 === 0 ? .6 : .3}"/>`; }).join('')}
    </svg>
    <div class="abs" style="inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center">
      <div class="ov muted">Основная часть</div>
      <div class="num" style="font-size:72px;line-height:80px;font-weight:300;letter-spacing:-1.5px">24:16</div>
      <div class="body-m muted">из 40:00 · ещё <span class="num" style="color:var(--ivory)">15:44</span></div>
    </div>
  </div>
  <div class="card" style="margin:34px 16px 0;padding:14px 16px">
    <div class="row" style="gap:10px"><span class="gold">${icon('breath', 22, 1.4)}</span><div>
      <div class="title-s">Можешь говорить фразами — темп верный</div>
      <div class="body-s muted" style="margin-top:2px">Не хватает воздуха на фразу — сбавь шаг. Пульс WHOOP пришлёт после тренировки, сверю сам.</div></div></div>
  </div>
  <div class="pad row" style="margin-top:18px;gap:10px;color:var(--ivory2)"><span class="ov muted">Дальше</span><div class="rule solid" style="flex:1"></div><span class="body-m">заминка 5′ шагом</span></div>
  <div class="row" style="position:absolute;left:0;right:0;bottom:44px;justify-content:space-between;padding:0 28px">
    <div class="btn tonal" style="width:120px;height:56px">Закончить</div>
    <div style="width:80px;height:80px;border-radius:40px;background:linear-gradient(180deg,#E6C77E,#CFA845);color:var(--on-gold);display:flex;align-items:center;justify-content:center;box-shadow:0 0 0 6px rgba(217,181,98,.10)" aria-label="Пауза">${icon('pause', 30, 2.4)}</div>
    <div class="btn tonal" style="width:120px;height:56px;gap:6px">${icon('sound', 20)}Звук</div>
  </div>
  <div class="gesture"></div>`;
  return page(body, { title: 'b1 Тренировка' });
}

// ------------------------------------------------------------------ b2 · сверка с WHOOP
function verify() {
  const zones = [['Z1', 22, 9], ['Z2', 59, 24], ['Z3', 14, 6], ['Z4', 5, 2], ['Z5', 0, 0]];
  const zc = ['#CBBF9F', '#D9B562', '#E6A867', '#C98A5E', '#8E6A55'];
  const bg = todayTop('dark').match(/<div class="screen dark">([\s\S]*)<\/div><\/body>/)[1].replace(/13:32/g, '19:02');
  const body = `
  <div class="abs" style="inset:0;filter:saturate(.8)">${bg}</div>
  <div class="abs" style="inset:0;background:rgba(4,6,10,.72)"></div>
  <div class="abs" style="left:0;right:0;bottom:0;height:700px;border-radius:28px 28px 0 0;background:linear-gradient(180deg,#1A202B,#141923 40%);border-top:1px solid var(--rule);padding:12px 20px 0">
    <div style="width:36px;height:4px;border-radius:2px;background:var(--ivory3);margin:0 auto"></div>
    <div class="ov" style="margin-top:16px">Сверка с WHOOP · тренировка дня</div>
    <div class="headline-m" style="margin-top:6px">Zone 2 засчитана</div>
    <div class="row" style="margin-top:8px;gap:8px"><span class="chip" style="background:var(--gold);color:var(--on-gold);border:none">${icon('check', 15, 2.4)}подтверждено WHOOP</span><span class="body-s muted">план 40 мин · 18:06–18:47</span></div>
    <div class="row" style="margin-top:18px;gap:0">
      ${[['41', 'мин', 'длительность'], ['127', 'уд/мин', 'пульс средний'], ['81', '%', 'в зонах 1–2']].map(([v, u, l], i) => `<div style="flex:1;${i ? 'border-left:1px solid var(--rule-soft);padding-left:16px' : ''}">
        <div class="num" style="font-size:30px;line-height:36px;font-weight:600">${v}<span style="font-size:13px;color:var(--ivory2);font-weight:500;margin-left:3px">${u}</span></div><div class="body-s muted">${l}</div></div>`).join('')}
    </div>
    <div class="rule" style="margin:18px 0 14px"></div>
    <div class="row between"><div class="title-s">Время в пульсовых зонах</div><div class="body-s muted">цель: ≥ 60 % в Z1–Z2</div></div>
    <div style="margin-top:10px;position:relative">
      <div class="abs" style="left:28px;right:-8px;top:-3px;height:58px;border:1px dashed var(--rule);border-radius:10px"></div>
      ${zones.map(([z, p, m], i) => `<div class="row" style="height:26px;gap:10px">
        <div class="num body-s" style="width:24px;color:var(--ivory2);font-weight:700">${z}</div>
        <div style="flex:1;height:10px;border-radius:5px;background:var(--hair);overflow:hidden"><div style="width:${p}%;height:100%;border-radius:5px;background:${zc[i]}"></div></div>
        <div class="num body-s" style="width:96px;text-align:right;white-space:nowrap"><b>${p} %</b> <span class="muted">· ${m} мин</span></div></div>`).join('')}
    </div>
    <div class="body-m muted" style="margin-top:12px">Засчитал тренировку дня. Нагрузку беру из WHOOP — вечером учту её в Весах.</div>
    <div class="title-s" style="margin-top:14px">Как ощущалось? <span class="muted" style="font-weight:500">по желанию</span></div>
    <div class="row" style="gap:8px;margin-top:8px">${['Легко', 'Рабоче', 'Тяжело', 'Предел'].map((l, i) => `<div class="chip" style="height:48px;flex:1;justify-content:center;border-radius:24px;font-size:14px;${i === 1 ? 'border-color:var(--gold);color:var(--gold);background:rgba(217,181,98,.08)' : ''}">${l}</div>`).join('')}</div>
    <div class="btn primary" style="width:100%;margin-top:16px">Верно</div>
    <div class="row" style="justify-content:center;margin-top:2px"><div class="btn text" style="color:var(--ivory2)">Это была другая тренировка</div></div>
  </div>
  <div class="gesture"></div>`;
  return page(body, { title: 'b2 Сверка' });
}

// ------------------------------------------------------------------ c · наставник Винс
function mentor() {
  const vince = (html, time) => `<div style="display:grid;grid-template-columns:3px 1fr;column-gap:14px;margin-top:16px;padding-right:28px">
     <div style="background:linear-gradient(180deg,var(--gold),var(--rule-soft));border-radius:2px"></div>
     <div><div class="body-l">${html}</div>${time ? `<div class="body-s" style="color:var(--ivory3);margin-top:4px">Винс · ${time}</div>` : ''}</div></div>`;
  const me = (text, time) => `<div style="display:flex;flex-direction:column;align-items:flex-end;margin-top:16px;padding-left:64px">
     <div class="body-l" style="background:var(--s3);border:1px solid var(--hair);border-radius:20px 20px 6px 20px;padding:10px 14px">${text}</div>
     <div class="body-s" style="color:var(--ivory3);margin-top:4px">${time}</div></div>`;
  const avatar = `<svg width="44" height="44" viewBox="0 0 44 44">
     <circle cx="22" cy="22" r="21" fill="#10141D" stroke="var(--gold)" stroke-width=".8" opacity="1"/>
     ${laurel(22, 22, 16.5, 200, 330, 5, 5.5)}${laurel(22, 22, 16.5, 160, 30, 5, 5.5)}
     <text x="22" y="28.5" text-anchor="middle" font-family="Cormorant Garamond" font-weight="600" font-size="19" fill="var(--gold)">В</text></svg>`;
  const body = `
  ${statusBar('13:07')}
  <div class="row between" style="padding:6px 8px 0 20px">
    <div class="row" style="gap:12px">${avatar}<div><div class="headline-s" style="line-height:26px">Винс</div>
      <div class="row" style="gap:6px"><span class="body-s muted">наставник</span><span class="chip" style="height:20px;padding:0 7px;font-size:10.5px;letter-spacing:.1em;border-color:var(--rule);color:var(--gold);background:transparent">ИИ</span></div></div></div>
    <div class="row" style="gap:2px"><div class="iconbtn" style="color:var(--ivory2)" aria-label="Не записывай">${icon('eyeoff', 22)}</div>${sosPill()}</div>
  </div>
  <div class="pad" style="margin-top:14px"><div class="row" style="height:40px;border:1px solid var(--rule-soft);border-radius:20px;padding:3px">
    <div class="row" style="flex:1;height:100%;justify-content:center;border-radius:17px;background:var(--rule-soft);color:var(--gold);font-weight:600;font-size:14px;gap:6px">${icon('check', 16, 2)}Разговор</div>
    <div class="row" style="flex:1;height:100%;justify-content:center;color:var(--ivory2);font-weight:600;font-size:14px">Работа</div></div></div>
  <div class="pad" style="margin-top:6px">
    <div class="row" style="gap:12px;margin-top:12px"><div class="rule" style="flex:1"></div><span class="ov muted" style="font-size:10px">Сегодня · 13:05</span><div class="rule" style="flex:1"></div></div>
    ${vince('Заряд чуть ниже нормы, а в 15:00 питч. Давай без героизма: что в нём точно главное?', '13:05')}
    ${me('Цифры по юнит-экономике. Остальное расскажу своими словами.', '13:06')}
    ${vince('Тогда так: с 13:35 до 14:20 — только слайд с цифрами, без почты. В 14:45 — три минуты длинного выдоха, я напомню. Zone 2 вечером оставляю: после питча она поможет уснуть вовремя.', '13:06')}
    <div class="row" style="gap:8px;margin:12px 0 0 17px;flex-wrap:wrap">
      <span class="chip gold" style="height:40px;border-radius:20px">${icon('check', 15, 2)}Поставить фокус 13:35</span>
      <span class="chip" style="height:40px;border-radius:20px">Напомни в 14:45</span>
    </div>
    <div class="row" style="gap:8px;margin:16px 0 0 17px;color:var(--ivory3)" >${icon('tag', 15, 1.5)}<span class="body-s">Записал: напряжение · работа. Проверишь вечером.</span></div>
    ${me('Давай. И про кофе напомни.', '13:07')}
  </div>
  <div class="abs row" style="left:12px;right:12px;bottom:104px;gap:8px">
    <div class="row" style="flex:1;height:52px;border-radius:26px;background:var(--s2);border:1px solid var(--hair);padding:0 6px 0 18px;color:var(--ivory3);font-size:15px">Написать Винсу…<span style="margin-left:auto" class="iconbtn">${icon('clip', 22)}</span></div>
    <div style="width:52px;height:52px;border-radius:26px;background:linear-gradient(180deg,#E6C77E,#CFA845);color:var(--on-gold);display:flex;align-items:center;justify-content:center" aria-label="Говорить голосом">${icon('mic', 24, 1.8)}</div>
  </div>
  ${navBar('mentor')}`;
  return page(body, { title: 'c Наставник' });
}

// ------------------------------------------------------------------ d · неделя
function week() {
  const days = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс'];
  const modes = ['rovno', 'razgon', 'razgon', 'rovno', 'berezhno', 'rovno', 'rovno'];
  const W = 156, H = 70;
  const chart = (title, unit, vals, band, kind, fmt, note) => {
    const lo = Math.min(...vals, band[0]) * 0.9, hi = Math.max(...vals, band[1]) * 1.06;
    const x = (i) => 6 + i * ((W - 12) / 6), y = (v) => H - 4 - ((v - lo) / (hi - lo)) * (H - 10);
    let g = '';
    if (kind === 'bars') {
      g += vals.map((v, i) => `<rect x="${x(i) - 5}" y="${y(v)}" width="10" height="${H - y(v)}" rx="1.5" fill="var(--gold)" opacity="${i === 6 ? .95 : .6}"/>
        <path d="M${x(i) - 7} ${y(v)} H${x(i) + 7}" stroke="var(--gold-hi)" stroke-width="1" opacity=".7"/>`).join('');
      g += `<path d="M0 ${y(band[0])} H${W}" stroke="var(--ivory)" stroke-width="1" stroke-dasharray="3 3" opacity=".55"/>`;
    } else {
      g += `<rect x="0" y="${y(band[1])}" width="${W}" height="${y(band[0]) - y(band[1])}" fill="var(--ivory)" opacity=".07"/>`;
      if (kind === 'line') {
        const d = vals.map((v, i) => `${i ? 'L' : 'M'}${x(i)} ${y(v)}`).join(' ');
        g += `<path d="${d}" fill="none" stroke="var(--gold)" stroke-width="1.6" stroke-linejoin="round" class="glow"/>`;
      }
      g += vals.map((v, i) => `<circle cx="${x(i)}" cy="${y(v)}" r="${i === 6 ? 3.6 : 2.6}" fill="${kind === 'line' ? 'var(--bg)' : 'var(--gold)'}" stroke="var(--gold)" stroke-width="1.4"/>`).join('');
    }
    return `<div class="tile" style="padding:12px 12px 10px">
      <div class="row between"><span class="title-s" style="font-size:13.5px">${title}</span><span class="body-s muted">${unit}</span></div>
      <div class="num" style="font-size:22px;line-height:28px;font-weight:600;margin-top:2px">${fmt}</div>
      <svg width="${W}" height="${H}" style="display:block;margin-top:4px;overflow:visible">${g}</svg>
      <div class="row between body-s" style="color:var(--ivory3);font-size:10.5px;margin-top:3px">${days.map((d) => `<span style="width:22px;text-align:center">${d}</span>`).join('')}</div>
      <div class="body-s muted" style="margin-top:4px">${note}</div></div>`;
  };
  const body = `
  ${statusBar('20:10')}
  <div class="pad row between" style="margin-top:6px;align-items:flex-end">
    <div><div class="ov">Итоги недели</div><div class="headline-l" style="margin-top:2px">2–8 ноября</div></div>
    <div class="btn text" style="color:var(--ivory2);font-size:14px">Скрыть цифры</div>
  </div>
  <div class="row" style="margin:6px 16px 0;gap:14px;align-items:center">
    <div style="position:relative;width:150px;height:150px;flex:none">
      <svg class="abs" width="150" height="150">${laurel(75, 75, 70, 205, 335, 7, 9)}${laurel(75, 75, 70, 155, 25, 7, 9)}</svg>
      <div class="abs glow" style="left:13px;top:13px">${monogram(WEEK_LEVELS, 124, { id: 'wk', ring: false })}</div>
    </div>
    <div><div class="quote" style="font-size:21px;line-height:27px">Неделя ровная: Весы в балансе, Топливо выше твоей нормы.</div>
      <div class="body-s muted" style="margin-top:6px">4 грани из 8 · остальные набирают данные</div></div>
  </div>
  <div class="row" style="margin:10px 16px 0;padding:10px 6px;border-top:1px solid var(--rule-soft);border-bottom:1px solid var(--rule-soft)">
    ${days.map((d, i) => `<div style="flex:1;display:flex;flex-direction:column;align-items:center;gap:2px;color:var(--${modes[i]})">${modeGlyph(modes[i], 22, `var(--${modes[i]})`)}<span class="body-s" style="color:var(--ivory2);font-weight:600">${d}</span></div>`).join('')}
  </div>
  <div class="pad row body-s muted" style="gap:14px;margin-top:6px;justify-content:center">
    <span><b style="color:var(--razgon)">Разгон</b> 2</span><span><b style="color:var(--rovno)">Ровно</b> 4</span><span><b style="color:var(--berezhno)">Бережно</b> 1</span></div>
  <div style="display:grid;grid-template-columns:1fr 1fr;gap:8px;margin:12px 16px 0">
    ${chart('Заряд', 'норма 50', [52, 61, 64, 55, 38, 47, 53], [44, 56], 'line', '53', '5 из 7 дней в норме')}
    ${chart('Ночная HRV', 'мс', [54, 62, 66, 57, 41, 49, 55], [48, 60], 'dots', '55', 'пятница ниже полосы')}
    ${chart('Шаги', 'тыс. в день', [8.4, 11.2, 9.8, 7.1, 4.9, 12.6, 10.3], [8.9, 8.9], 'bars', '9,2', 'пунктир — медиана 8,9')}
    ${chart('Нагрузка', 'Весы', [0.2, 0.6, 0.9, 0.5, -0.4, 0.1, 0.0], [-0.5, 0.5], 'line', '0,0', 'в балансе')}
  </div>
  ${navBar('week')}`;
  return page(body, { title: 'd Неделя' });
}

// ------------------------------------------------------------------ e · онбординг
function onboarding() {
  const body = `
  <div class="abs" style="left:-64px;top:-18px;width:540px;height:540px;background:url('../assets/placeholder-R1-atlas.jpg') center/cover;-webkit-mask-image:linear-gradient(180deg,#000 62%,transparent 96%),radial-gradient(closest-side,#000 80%,transparent);-webkit-mask-composite:source-in"></div>
  ${statusBar('9:41')}
  <div class="abs" style="left:50%;transform:translateX(-50%);top:46px;font-size:10px;line-height:14px;letter-spacing:.08em;color:rgba(237,229,211,.75);background:rgba(11,14,21,.75);text-align:center;white-space:nowrap">
    <div class="dashed-ph" style="padding:4px 8px">PLACEHOLDER · растр R1<br>заменить вектором знака</div></div>
  <div class="abs" style="left:0;right:0;top:500px;text-align:center">
    <div style="font-family:var(--serif);font-weight:600;font-size:14px;letter-spacing:.42em;color:var(--gold);padding-left:.42em">HEALTH OF A TITAN</div>
    <div class="row" style="justify-content:center;gap:10px;margin-top:10px"><div style="width:44px;height:1px;background:var(--rule)"></div><div style="width:5px;height:5px;transform:rotate(45deg);border:1px solid var(--gold)"></div><div style="width:44px;height:1px;background:var(--rule)"></div></div>
  </div>
  <div class="pad abs" style="left:0;right:0;top:556px;text-align:center">
    <div class="display-m" style="font-size:40px;line-height:44px">Держи свой день,<br>как титан держит небо</div>
    <div class="body-l muted" style="margin:14px auto 0;max-width:340px">Каждое утро HoT читает твой сон и нагрузку и собирает план, который по силам сегодня. По твоей норме — не по чужой.</div>
  </div>
  <div class="abs pad" style="left:0;right:0;bottom:40px">
    <div class="row" style="justify-content:center;gap:8px;margin-bottom:20px"><div style="width:22px;height:4px;border-radius:2px;background:var(--gold)"></div><div style="width:6px;height:4px;border-radius:2px;background:var(--ivory3)"></div><div style="width:6px;height:4px;border-radius:2px;background:var(--ivory3)"></div></div>
    <div class="btn primary" style="width:100%;height:56px;border-radius:28px;font-size:16px">Начать · 3 минуты</div>
    <div class="row" style="justify-content:center;margin-top:4px"><div class="btn text" style="color:var(--ivory2);font-size:14px">${icon('shield', 18)}Как HoT бережёт данные</div></div>
  </div>
  <div class="gesture"></div>`;
  return page(body, { title: 'e Онбординг' });
}

// ------------------------------------------------------------------ f · Распорядок (D-027)
function routine() {
  const src = (ic, name, note) => `<span class="row" style="gap:5px;font-size:12.5px;line-height:16px;color:var(--ivory2)"><span class="row" style="gap:4px;height:22px;padding:0 8px 0 6px;border-radius:11px;border:1px solid var(--rule-soft);color:var(--gold);font-weight:700;font-size:11.5px">${icon(ic, 13, 1.7)}${name}</span>${note}</span>`;
  const row = (label, value, source, extra = '') => `<div class="row between" style="min-height:72px;padding:10px 0;border-bottom:1px solid var(--hair);gap:12px">
     <div style="min-width:0"><div class="title-s" style="font-size:15px;font-weight:500">${label}</div><div style="margin-top:6px">${source}</div>${extra}</div>
     <div class="row" style="gap:2px;flex:none"><span class="num" style="font-weight:600;font-size:17px">${value}</span><span style="color:var(--ivory3)">${icon('chev', 18)}</span></div></div>`;
  const body = `
  ${statusBar('21:30')}
  <div class="row" style="padding:4px 8px 0 4px"><div class="iconbtn">${icon('back', 24)}</div><span class="body-m muted">Я · Настройки</span></div>
  <div class="pad" style="margin-top:2px"><div class="headline-l">Распорядок</div>
    <div class="body-m muted" style="margin-top:6px">Собрал сам — из WHOOP, телефона и календаря. Поправь, если что-то не так: твоя правка главнее.</div></div>
  <div class="card gild" style="margin:16px 16px 0;padding:14px 16px 10px">
    <div class="row" style="gap:10px;align-items:flex-start"><span class="gold" style="margin-top:2px">${icon('steps', 20, 1.4)}</span>
      <div class="body-m" style="font-size:15px;line-height:21px">Похоже, по будням ты гуляешь с собакой около <b class="num">07:00</b> — так и держим?</div></div>
    <div class="row" style="gap:8px;margin:10px 0 0 30px"><div class="btn primary" style="height:44px;padding:0 22px;font-size:14px">Да</div><div class="btn text" style="font-size:14px;height:44px">Поправить</div></div>
  </div>
  <div class="pad" style="margin-top:18px">
    <div class="ov">Сон</div>
    ${row('Подъём в будни', '06:45', src('band', 'WHOOP', 'медиана 28 ночей'))}
    ${row('Подъём завтра', '06:30', src('alarm', 'Будильник', 'на телефоне'))}
    ${row('Обычный отбой', '23:15', src('band', 'WHOOP', 'медиана начала сна'))}
    ${row('Целевой отбой', '23:00', src('pencil', 'Ты', 'вручную'), `<div class="body-s" style="color:var(--ivory3);margin-top:4px">по данным было бы 22:50 · правка главнее</div>`)}
    <div class="ov" style="margin-top:18px">День</div>
    ${row('Окно тренировки', '18:00–20:00', src('band', 'WHOOP', 'Пн, Ср, Пт'))}
    ${row('Встречи сегодня', '4 · пик 15:00', src('calendar', 'Календарь', 'только занятость'))}
    ${row('Часовой пояс', 'UTC−5', src('globe', 'Телефон', 'Нью-Йорк'))}
  </div>
  <div class="gesture"></div>`;
  return page(body, { title: 'f Распорядок' });
}

// ------------------------------------------------------------------ g · SOS (sober: no gold, no glow, no serif)
function sos() {
  const chip = (t) => `<div style="height:60px;border-radius:16px;background:#1A1F28;border:1px solid rgba(237,229,211,.10);display:flex;align-items:center;padding:0 16px;font-size:16px;font-weight:600;color:#EDE5D3">${t}</div>`;
  const body = `
  ${statusBar('23:48', { color: '#EDE5D3' })}
  <div class="row between" style="padding:4px 16px 0 8px"><div class="iconbtn" aria-label="Закрыть">${icon('close', 24)}</div><span style="font-weight:700;letter-spacing:.12em;font-size:14px;color:#B8C0CC">SOS</span></div>
  <div style="margin:14px 16px 0;padding:18px 18px 18px;border-radius:20px;background:var(--err-c);color:var(--on-err-c)">
    <div style="font-weight:700;font-size:17px;line-height:24px">Если опасность прямо сейчас</div>
    <div style="font-size:14px;line-height:20px;opacity:.92;margin-top:2px">Бесплатно и круглосуточно. Откроется набор номера.</div>
    <div class="row" style="gap:10px;margin-top:14px">
      <div class="row" style="flex:1.6;height:56px;border-radius:28px;background:#F9DEDC;color:#410E0B;justify-content:center;gap:8px;font-weight:700;font-size:16px">${icon('phone', 20, 1.8)}988 · поговорить</div>
      <div class="row" style="flex:1;height:56px;border-radius:28px;border:1.5px solid #F9DEDC;color:#F9DEDC;justify-content:center;gap:8px;font-weight:700;font-size:16px">${icon('phone', 20, 1.8)}911</div>
    </div>
    <div class="row" style="gap:6px;margin-top:10px;font-size:13.5px;opacity:.9">${icon('sms', 16, 1.6)}SMS на 988 или чат 988lifeline.org</div>
  </div>
  <div class="pad" style="margin-top:26px"><div style="font-weight:600;font-size:26px;line-height:32px;color:#EDE5D3">Я рядом. Что сейчас?</div>
    <div style="font-size:15px;color:#B8C0CC;margin-top:4px">Выбери, что ближе. Можно не объяснять.</div></div>
  <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px;margin:16px 16px 0">
    ${['Злость', 'Тревога или паника', 'Перегруз', 'Не могу уснуть', 'Грусть, пустота', 'Другое'].map(chip).join('')}
  </div>
  <div style="margin:14px 16px 0;height:60px;border-radius:30px;border:1.5px solid rgba(237,229,211,.55);display:flex;align-items:center;justify-content:center;gap:10px;font-weight:700;font-size:16px;color:#EDE5D3">${icon('breath', 22, 1.6)}Просто подышать · 1 мин</div>
  <div class="pad" style="position:absolute;left:0;right:0;bottom:64px;font-size:14px;line-height:20px;color:#B8C0CC;text-align:center">Я ИИ, не человек и не врач.<br>Живые люди, которые этому обучены, — 988.</div>
  <div class="gesture"></div>`;
  return page(body, { title: 'g SOS', extraCss: '.screen{background:#0E1117 !important}.screen::before{display:none}' });
}

// ------------------------------------------------------------------ render
const SCREENS = [
  ['a1-morning-push', 'Утренний пуш и виджет', '06:52 · пуш по готовности WHOOP', a1],
  ['a2-today', 'Сегодня · Карта дня', 'режим, Заряд, «Сейчас», 6 сфер', () => todayTop('dark')],
  ['a3-today-plan', 'План по часам', 'статусы сверки с фактом (A35)', todayPlan],
  ['b1-workout', 'Тренировка идёт', 'Zone 2, таймер, «Мне плохо»', workout],
  ['b2-workout-verified', 'Сверка с WHOOP', '«✓ подтверждено WHOOP»', verify],
  ['c-mentor', 'Наставник Винс', 'разговор, метки, голос', mentor],
  ['d-week', 'Итоги недели', 'Монограмма, режимы, графики', week],
  ['e-onboarding', 'Онбординг', 'первый экран, плейсхолдер знака', onboarding],
  ['f-routine', 'Распорядок', 'выведенные значения и источники (D-027)', routine],
  ['g-sos', 'SOS', 'трезвый экран без декора', sos],
  ['a2-today-light', 'Сегодня · светлая тема', 'дневная тема «Мрамор»', () => todayTop('light')],
];

async function main() {
  const only = process.argv[2];
  for (const [name, , , fn] of SCREENS) fs.writeFileSync(path.join(HTML, name + '.html'), fn());
  const { chromium } = require('playwright');
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 412, height: 915 }, deviceScaleFactor: 2 });
  const pg = await ctx.newPage();
  for (const [name] of SCREENS) {
    if (only && only !== name && only !== 'board') continue;
    await pg.goto('file://' + path.join(HTML, name + '.html'));
    await pg.evaluate(() => document.fonts.ready);
    await pg.waitForTimeout(150);
    await pg.locator('.screen').screenshot({ path: path.join(OUT, name + '.png') });
    console.log('rendered', name);
  }
  if (!only || only === 'board') {
    // contact board
    const cols = 4;
    const cards = SCREENS.map(([name, t, sub], i) => `<figure><img src="../../${name}.png"><figcaption><b>${String.fromCharCode(97 + 0)}${''}</b><span class="n">${String(i + 1).padStart(2, '0')}</span> ${t}<em>${sub}</em></figcaption></figure>`).join('');
    const sw = (hex, name, sub) => `<div style="display:flex;align-items:center;gap:12px;height:44px"><div style="width:40px;height:40px;border-radius:20px;background:${hex};box-shadow:inset 0 0 0 1px rgba(237,229,211,.14)"></div><div><div style="font-size:14px;font-weight:600">${name}</div><div style="font-size:12px;color:#A9A292">${hex} · ${sub}</div></div></div>`;
    const tokens = `<figure><div style="width:412px;height:915px;border-radius:34px;padding:34px 30px;background:radial-gradient(120% 50% at 50% -10%,#1B2232,#0B0E15 60%);box-shadow:0 0 0 1px rgba(217,181,98,.28),0 0 0 9px #11151C,0 0 0 10px rgba(237,229,211,.08)">
      <div class="ov" style="font-size:11px;margin-bottom:8px">Токены</div>
      <div style="font-family:'Cormorant Garamond';font-weight:500;font-size:44px;line-height:46px">Аа Бб 44</div>
      <div style="font-size:13px;color:#A9A292;margin:6px 0 18px">Cormorant Garamond — заголовки · Manrope — текст и цифры</div>
      ${sw('#0B0E15', 'Ночь', 'фон')}${sw('#151A24', 'Базальт', 'карточка')}${sw('#EDE5D3', 'Слоновая кость', 'текст 13,9:1')}${sw('#A9A292', 'Травертин', 'вторичный 6,9:1')}
      ${sw('#D9B562', 'Золото', 'primary 8,9:1')}${sw('#C9A23A', 'Золото бренда', 'seed, знак')}
      <div style="height:1px;background:rgba(217,181,98,.25);margin:14px 0"></div>
      <div style="display:flex;justify-content:space-between">${[['razgon', '#E6A867', 'Разгон'], ['rovno', '#8FC2B2', 'Ровно'], ['berezhno', '#ACA9DE', 'Бережно']].map(([m, c, l]) => `<div style="display:flex;flex-direction:column;align-items:center;gap:6px;color:${c}">${modeGlyph(m, 34, c)}<span style="font-weight:600;font-size:14px">${l}</span><span style="font-size:11px;color:#A9A292">${c}</span></div>`).join('')}</div>
      <div style="height:1px;background:rgba(217,181,98,.25);margin:14px 0"></div>
      <div style="display:flex;align-items:center;gap:18px"><div style="filter:drop-shadow(0 0 6px rgba(217,181,98,.5));--gold:#D9B562;--ivory:#EDE5D3;--bg:#0B0E15">${monogram([0.4, null, null, 0.2, null, 1, null, -0.8], 110, { id: 'tk' })}</div>
        <div style="font-size:12.5px;line-height:18px;color:#A9A292">Монограмма дня: 8 лепестков, длина — уровень от твоей нормы (пунктирный круг). Пунктир лепестка — данных пока нет.</div></div>
      ${sw('#8C1D18', 'Тревога', 'только SOS и красные флаги')}
    </div><figcaption><span class="n">12</span> Палитра, шрифты, режимы<em>контраст — WCAG AA на фоне карточки</em></figcaption></figure>`;
    const board = `<!doctype html><html lang="ru"><head><meta charset="utf-8"><link rel="stylesheet" href="../fonts/local.css"><style>
      *{margin:0;padding:0;box-sizing:border-box} body{background:#07090E;color:#EDE5D3;font-family:Manrope;width:${cols * 412 + (cols - 1) * 56 + 160}px;padding:72px 80px 80px;
      background:radial-gradient(70% 40% at 50% 0%,#182032 0%,#07090E 70%)}
      header{display:flex;align-items:flex-end;justify-content:space-between;border-bottom:1px solid rgba(217,181,98,.3);padding-bottom:28px;margin-bottom:56px}
      h1{font-family:'Cormorant Garamond';font-weight:500;font-size:64px;line-height:64px} .ov{font-weight:600;font-size:14px;letter-spacing:.24em;color:#D9B562;text-transform:uppercase;margin-bottom:14px}
      header p{font-size:16px;color:#A9A292;max-width:620px;text-align:right;line-height:24px}
      .grid{display:grid;grid-template-columns:repeat(${cols},412px);gap:64px 56px}
      figure img{width:412px;height:915px;display:block;border-radius:34px;box-shadow:0 0 0 1px rgba(217,181,98,.28),0 0 0 9px #11151C,0 0 0 10px rgba(237,229,211,.08),0 30px 60px rgba(0,0,0,.6)}
      figcaption{margin-top:26px;font-size:17px;font-weight:600} figcaption .n{color:#D9B562;font-weight:700;margin-right:6px;letter-spacing:.06em} figcaption b{display:none}
      figcaption em{display:block;font-style:normal;font-weight:500;font-size:14px;color:#A9A292;margin-top:4px}
      .mark{display:flex;align-items:center;gap:18px}
    </style></head><body><header><div class="mark">${hMark(84, '#D9B562')}<div><div class="ov">Health of a Titan · MATUTA · A36</div><h1>Колоннада — визуальный язык</h1></div></div>
      <p>Золотая линия на ночном мраморе. Тёмная тема — основная, светлая «Мрамор» — дневная. SOS — без декора. Знак на онбординге — растровый плейсхолдер до вектора.</p></header>
      <div class="grid">${cards}${tokens}</div></body></html>`;
    fs.writeFileSync(path.join(HTML, '_board.html'), board);
    const bctx = await browser.newContext({ viewport: { width: 1000, height: 800 }, deviceScaleFactor: 1.5 });
    const bp = await bctx.newPage();
    await bp.goto('file://' + path.join(HTML, '_board.html'));
    await bp.evaluate(() => document.fonts.ready);
    await bp.waitForTimeout(300);
    await bp.screenshot({ path: path.join(OUT, '_screens.png'), fullPage: true });
    console.log('rendered _screens');
  }
  await browser.close();
}
main().catch((e) => { console.error(e); process.exit(1); });
