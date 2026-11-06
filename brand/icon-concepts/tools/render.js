// Render PNG previews of the HoT mark concepts with Playwright/Chromium.
//   node brand/icon-concepts/tools/render.js
// Needs tools/.render/meta.json (run build.py first).
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const HERE = __dirname;
const ROOT = path.dirname(HERE);
const RENDER = path.join(HERE, '.render');
const meta = JSON.parse(fs.readFileSync(path.join(RENDER, 'meta.json'), 'utf8'));
const read = (cid, f) => fs.readFileSync(path.join(ROOT, cid, f), 'utf8');

const NAMES = {
  'c1-atlas': ['C1 · Атлант', 'Фигура в драпировке держит колонны; пульс проходит сквозь хитон'],
  'c2-portico': ['C2 · Портик', 'Без фигуры: колонны + перекладина-кардиограмма'],
  'c3-monogram': ['C3 · Монограмма', 'Геометрический титан: колонны+руки = H, голова = o, руки+тело = T'],
};

// --- adaptive icon shown through a launcher mask (visible area = central 72 of 108 dp)
const SQUIRCLE = (() => {
  const pts = [];
  const n = 4.2;
  for (let i = 0; i < 96; i++) {
    const t = (i / 96) * 2 * Math.PI;
    const c = Math.cos(t), s = Math.sin(t);
    const x = Math.sign(c) * Math.pow(Math.abs(c), 2 / n);
    const y = Math.sign(s) * Math.pow(Math.abs(s), 2 / n);
    pts.push(`${(50 + 50 * x).toFixed(2)}% ${(50 + 50 * y).toFixed(2)}%`);
  }
  return `polygon(${pts.join(',')})`;
})();
const MASKS = {
  circle: 'circle(50% at 50% 50%)',
  squircle: SQUIRCLE,
  rounded: 'inset(0 round 22%)',
  teardrop: 'inset(0 round 50% 50% 50% 12%)',
};
function icon(m, size, mask = 'circle') {
  const svg = read(m.id, 'app-icon.svg').replace('viewBox="0 0 108 108" width="108" height="108"', `viewBox="18 18 72 72" width="${size}" height="${size}"`);
  return `<div class="ic" style="width:${size}px;height:${size}px;clip-path:${MASKS[mask]}">${svg}</div>`;
}
function themed(m, size, bg, fg) {
  return `<div class="ic" style="width:${size}px;height:${size}px;clip-path:${MASKS.circle};background:${bg}"><svg viewBox="18 18 72 72" width="${size}" height="${size}"><path fill="${fg}" fill-rule="evenodd" d="${m.d_mono}"/></svg></div>`;
}
function notif(m, size, color) {
  return `<svg viewBox="0 0 24 24" width="${size}" height="${size}"><path fill="${color}" fill-rule="evenodd" d="${m.d24}"/></svg>`;
}
function inline(cid, file, w) {
  return read(cid, file).replace(/<svg ([^>]*?)>/, (all, attrs) => `<svg ${attrs.replace(/ width="[^"]*" height="[^"]*"/, '')} style="width:${w}px;height:auto;display:block">`);
}

// generic launcher icons (deliberately brand-free shapes)
const GENERIC = {
  'Телефон': `<rect width="72" height="72" fill="#C9E7CF"/><path d="M27 22c2-1 4 0 5 2l3 6c1 2 0 3-1 4l-2 2c2 5 6 9 11 11l2-2c1-1 3-2 4-1l6 3c2 1 3 3 2 5l-2 4c-1 2-3 3-5 3-15-1-27-13-28-28 0-2 1-4 3-5z" fill="#1E6B35"/>`,
  'Сообщения': `<rect width="72" height="72" fill="#D3E3FD"/><path d="M20 22h32a4 4 0 0 1 4 4v18a4 4 0 0 1-4 4H32l-8 7v-7h-4a4 4 0 0 1-4-4V26a4 4 0 0 1 4-4z" fill="#0B57D0"/>`,
  'Камера': `<rect width="72" height="72" fill="#E8DEF8"/><rect x="17" y="25" width="38" height="27" rx="5" fill="#5B3F9E"/><rect x="29" y="20" width="14" height="7" rx="2" fill="#5B3F9E"/><circle cx="36" cy="38.5" r="8" fill="#E8DEF8"/><circle cx="36" cy="38.5" r="4.5" fill="#5B3F9E"/>`,
  'Часы': `<rect width="72" height="72" fill="#FFDBCB"/><circle cx="36" cy="36" r="17" fill="none" stroke="#8B3A0F" stroke-width="4"/><path d="M36 26v11l7 5" fill="none" stroke="#8B3A0F" stroke-width="4" stroke-linecap="round"/>`,
};
function themedGeneric(name) {
  return GENERIC[name]
    .replace(/<rect width="72" height="72" fill="[^"]+"\/>/, '<rect width="72" height="72" fill="#D6E3F7"/>')
    .replace(/(fill|stroke)="#(1E6B35|0B57D0|5B3F9E|8B3A0F)"/g, '$1="#1F3A5F"')
    .replace(/fill="#E8DEF8"/g, 'fill="#D6E3F7"');
}
function generic(name, size) {
  return `<div class="ic" style="width:${size}px;height:${size}px;clip-path:${MASKS.circle}"><svg viewBox="0 0 72 72" width="${size}" height="${size}">${GENERIC[name]}</svg></div>`;
}

function column(m) {
  const [title, sub] = NAMES[m.id];
  const sizes = [192, 108, 48, 24];
  const row = (sz, mask) => sz.map((s) => `<figure>${icon(m, s, mask)}<figcaption>${s}</figcaption></figure>`).join('');
  const names = Object.keys(GENERIC);
  const home = [names[0], names[1], 'HoT', names[2], names[3]]
    .map((n) => `<figure class="app">${n === 'HoT' ? icon(m, 56) : generic(n, 56)}<figcaption>${n === 'HoT' ? 'Titan' : n}</figcaption></figure>`)
    .join('');
  const homeThemed = [names[0], names[1], 'HoT', names[2], names[3]]
    .map((n) => `<figure class="app">${n === 'HoT' ? themed(m, 56, '#D6E3F7', '#1F3A5F') : `<div class="ic" style="width:56px;height:56px;clip-path:${MASKS.circle}"><svg viewBox="0 0 72 72" width="56" height="56">${themedGeneric(n)}</svg></div>`}<figcaption>${n === 'HoT' ? 'Titan' : n}</figcaption></figure>`)
    .join('');
  return `
  <section class="col">
    <h2>${title}</h2><p class="sub">${sub}</p>
    <div class="label">512 px · Google Play (полный квадрат, маску накладывает стор)</div>
    <div class="hero">${read(m.id, 'app-icon.svg').replace('viewBox="0 0 108 108" width="108" height="108"', 'viewBox="18 18 72 72" width="512" height="512"')}</div>
    <div class="label">192 · 108 · 48 · 24 px — тёмные обои (маска «круг», Pixel)</div>
    <div class="strip dark">${row(sizes, 'circle')}</div>
    <div class="label">Те же размеры — светлые обои (маска «сквиркл»)</div>
    <div class="strip light">${row(sizes, 'squircle')}</div>
    <div class="label">Маски адаптивной иконки, 108 px: круг · сквиркл · скруглённый квадрат · капля</div>
    <div class="strip mid">${['circle', 'squircle', 'rounded', 'teardrop'].map((k) => `<figure>${icon(m, 108, k)}<figcaption>${k}</figcaption></figure>`).join('')}</div>
    <div class="label">Тематическая иконка Android 13+ (монохромный слой) — светлая и тёмная тема: 108 · 48 · 24</div>
    <div class="strip pair">
      <div class="tl">${[108, 48, 24].map((s) => themed(m, s, '#D6E3F7', '#1F3A5F')).join('')}</div>
      <div class="td">${[108, 48, 24].map((s) => themed(m, s, '#2E3A4B', '#C9DCF5')).join('')}</div>
    </div>
    <div class="label">Значок уведомления ic_stat_hot (24 dp, белый силуэт): строка состояния и шторка</div>
    <div class="strip notif">
      <div class="statusbar"><span>9:41</span>${notif(m, 24, '#fff')}<span class="dot"></span><span class="dot"></span><span class="sb-r">▾ ▮ 87%</span></div>
      <div class="shade"><div class="nh">${notif(m, 20, '#C9A23A')}<span>Titan · сейчас</span></div><div class="nt">Карта дня готова</div><div class="nb">Восстановление 72 %. Один шаг на сегодня — внутри.</div></div>
      <div class="zoom">${notif(m, 96, '#fff')}<figcaption>24 dp ×4</figcaption></div>
    </div>
    <div class="label">Главный экран Pixel: ряд иконок 56 px (обычные и тематические)</div>
    <div class="homescreen">${home}</div>
    <div class="homescreen themedbg">${homeThemed}</div>
    <div class="label">Логотип (lockup) · шрифт: ${m.family}</div>
    <div class="lock dark">${inline(m.id, 'lockup-horizontal-dark.svg', 500)}</div>
    <div class="lock light">${inline(m.id, 'lockup-horizontal-light.svg', 500)}</div>
    <div class="lockrow"><div class="lock dark small">${inline(m.id, 'lockup-hot-dark.svg', 220)}</div><div class="lock light small">${inline(m.id, 'lockup-hot-light.svg', 220)}</div></div>
  </section>`;
}

const CSS = `
*{box-sizing:border-box} body{margin:0;background:#E9E5DC;font-family:Inter,system-ui,sans-serif;color:#1d1a14}
header{padding:28px 36px 8px} h1{margin:0;font-size:30px;letter-spacing:-.01em} header p{margin:6px 0 0;color:#5a5446;font-size:15px}
main{display:grid;grid-template-columns:repeat(3,576px);gap:24px;padding:20px 36px 36px}
.col{background:#fff;border-radius:18px;padding:22px 24px 24px;box-shadow:0 1px 3px rgba(0,0,0,.08)}
h2{margin:0;font-size:24px} .sub{margin:4px 0 6px;color:#5a5446;font-size:14px;min-height:38px}
.label{font-size:12px;color:#6d6656;margin:16px 0 6px;text-transform:none}
.hero{width:528px;height:528px;display:flex;align-items:center;justify-content:center;background:#f3f0e8;border-radius:12px}
.hero svg{display:block}
.strip{display:flex;align-items:flex-end;gap:22px;padding:16px;border-radius:12px}
.strip.dark{background:linear-gradient(135deg,#1b2433,#3a2f48)} .strip.light{background:linear-gradient(135deg,#f6efe2,#dfe8f3)}
.strip.mid{background:#7f8a96;gap:24px}
.strip figure{margin:0;display:flex;flex-direction:column;align-items:center;gap:6px}
.strip figcaption{font-size:11px;color:rgba(255,255,255,.75)} .strip.light figcaption{color:#6d6656}
.ic{flex:none;overflow:hidden;display:flex;align-items:center;justify-content:center} .ic svg{display:block}
.pair{gap:12px;padding:0;background:none} .pair>div{display:flex;align-items:flex-end;gap:14px;padding:16px;border-radius:12px;flex:1}
.tl{background:#F4F7FC} .td{background:#141A22}
.notif{background:#10141b;flex-wrap:nowrap;gap:14px;align-items:center}
.statusbar{display:flex;align-items:center;gap:8px;color:#fff;font-size:15px;background:#000;padding:8px 12px;border-radius:10px;width:150px}
.statusbar .dot{width:12px;height:12px;border-radius:50%;background:#bbb;display:inline-block} .sb-r{display:none}
.shade{background:#2a2f38;border-radius:16px;padding:10px 12px;color:#e8eaed;width:236px}
.nh{display:flex;align-items:center;gap:6px;font-size:11px;color:#b9bec7} .nt{font-size:14px;font-weight:600;margin-top:4px} .nb{font-size:12px;color:#c7cbd2;margin-top:2px}
.zoom{display:flex;flex-direction:column;align-items:center;gap:2px;background:#000;border-radius:10px;padding:6px} .zoom figcaption{font-size:10px;color:#9aa0a6}
.homescreen{display:flex;justify-content:space-between;padding:18px 22px 14px;border-radius:14px;background:radial-gradient(120% 140% at 20% 0%,#4b5d7a,#1d2433 60%,#151a24)}
.homescreen.themedbg{margin-top:8px;background:radial-gradient(120% 140% at 20% 0%,#e6edf7,#c5d3e6)}
.app{margin:0;display:flex;flex-direction:column;align-items:center;gap:6px;width:72px}
.app figcaption{font-size:12px;color:#fff;text-shadow:0 1px 2px rgba(0,0,0,.5)} .themedbg .app figcaption{color:#1f2a3a;text-shadow:none}
.lock{border-radius:12px;padding:18px;display:flex;justify-content:center;margin-top:8px} .lock.dark{background:#16130B} .lock.light{background:#FAF7EF;border:1px solid #e6e0d0}
.lockrow{display:flex;gap:8px} .lockrow .lock{flex:1}
`;

(async () => {
  const html = `<!doctype html><html lang="ru"><head><meta charset="utf-8"><style>${CSS}</style></head><body>
  <header><h1>Health of a Titan — три концепции знака (фаза 1)</h1>
  <p>Векторные SVG, собранные вручную из геометрии; адаптивная иконка 108 dp, ключевое содержимое в безопасной зоне 66 dp. Золото #C9A23A на #16130B.</p></header>
  <main>${meta.map(column).join('')}</main></body></html>`;
  fs.writeFileSync(path.join(RENDER, 'comparison.html'), html);

  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1848, height: 1000 }, deviceScaleFactor: 2 });
  await page.goto('file://' + path.join(RENDER, 'comparison.html'));
  await page.waitForTimeout(300);
  await page.screenshot({ path: path.join(ROOT, '_comparison.png'), fullPage: true });

  // per-concept PNGs at exact pixel sizes
  const one = await browser.newPage({ viewport: { width: 512, height: 512 }, deviceScaleFactor: 1 });
  for (const m of meta) {
    const svg = read(m.id, 'app-icon.svg').replace('viewBox="0 0 108 108" width="108" height="108"', 'viewBox="18 18 72 72" width="512" height="512"');
    await one.setContent(`<html><body style="margin:0">${svg}</body></html>`);
    await one.screenshot({ path: path.join(ROOT, m.id, 'app-icon-512.png'), clip: { x: 0, y: 0, width: 512, height: 512 } });
    for (const theme of ['dark', 'light']) {
      const bg = theme === 'dark' ? '#16130B' : '#FAF7EF';
      await one.setViewportSize({ width: 1200, height: 400 });
      await one.setContent(`<html><body style="margin:0;background:${bg}">${inline(m.id, `lockup-horizontal-${theme}.svg`, 1200)}</body></html>`);
      const h = await one.evaluate(() => document.querySelector('svg').getBoundingClientRect().height);
      await one.screenshot({ path: path.join(ROOT, m.id, `lockup-horizontal-${theme}.png`), clip: { x: 0, y: 0, width: 1200, height: Math.ceil(h) } });
      await one.setViewportSize({ width: 512, height: 512 });
    }
  }
  await browser.close();
  console.log('rendered _comparison.png and per-concept PNGs');
})();
