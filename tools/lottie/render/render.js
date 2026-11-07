// Preview renderer: lottie-web (SVG) in headless Chromium via Playwright.
//   node render/render.js storyboard <out.png> <a.json> [b.json …]   — 6 frames per file, one row each
//   node render/render.js frames <dir> <a.json> [fps=30] [size=360] [mode] — PNG sequence for GIF/WebM
//   node render/render.js sheet <out.png> <cols> <a.json> …          — poster frames in a grid
// Mode recolours the light like the app does (dynamic properties): rovno | razgon | berezhno | light.
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const LOTTIE = fs.readFileSync(require.resolve('lottie-web/build/player/lottie_svg.min.js'), 'utf8');

const MODES = {
  rovno: { light: ['#FFFFFF', '#DFFBF6', '#9FEADC'], knock: '#060E0D', bg: '#08100F', glow: 'rgba(125,227,209,.16)', accent: '#7DE3D1' },
  razgon: { light: ['#FFFFFF', '#FFE7CF', '#FFB067'], knock: '#100B07', bg: '#0F0B08', glow: 'rgba(255,176,103,.16)', accent: '#FFB067' },
  berezhno: { light: ['#FFFFFF', '#E6E4FF', '#A8A3FF'], knock: '#0A0A12', bg: '#0A0A11', glow: 'rgba(168,163,255,.16)', accent: '#A8A3FF' },
  light: { light: ['#2B4A45', '#2F6B61', '#1F8A78'], knock: '#F4F1EA', bg: '#EFEBE3', glow: 'rgba(31,138,120,.10)', accent: '#1F8A78' },
};

function hexToRgb(h) {
  h = h.replace('#', '');
  return [0, 2, 4].map((i) => parseInt(h.slice(i, i + 2), 16) / 255);
}

// The same recolouring the app does with LottieProperty.GRADIENT_COLOR / COLOR by key path.
function recolor(doc, mode) {
  const m = MODES[mode] || MODES.rovno;
  const light = m.light.map(hexToRgb);
  const knock = hexToRgb(m.knock);
  const accent = hexToRgb(m.accent);
  const walk = (items, inGroup) => {
    for (const it of items || []) {
      const g = it.ty === 'gr' ? it.nm : inGroup;
      if (it.ty === 'gr') walk(it.it, g);
      if (it.ty === 'gs' && (g === 'titan_line' || g === 'ring_grad')) {
        const k = it.g.k.k; const n = it.g.p;
        for (let i = 0; i < n; i++) { const c = light[Math.min(i, light.length - 1)]; k[i * 4 + 1] = c[0]; k[i * 4 + 2] = c[1]; k[i * 4 + 3] = c[2]; }
      }
      if ((it.ty === 'fl' || it.ty === 'st') && it.c && it.c.a === 0) {
        if (g === 'titan_knockout' || g === 'columns_knockout') it.c.k = [...knock, 1];
        if (g === 'titan_inner') it.c.k = [...light[1], 1];
        if (g === 'props_line' || g === 'floor' || g === 'ring' || g === 'columns_line') it.c.k = [...(g === 'columns_line' ? light[1] : accent), 1];
      }
    }
  };
  for (const l of doc.layers) walk(l.shapes);
  return doc;
}

function page(size, bg, glow) {
  return `<!doctype html><html><body style="margin:0;background:${bg}">
  <div id="w" style="width:${size}px;height:${size}px;position:relative;background:radial-gradient(circle at 50% 50%, ${glow}, transparent 62%)">
  <div id="a" style="width:${size}px;height:${size}px"></div></div>
  <script>${LOTTIE}</script></body></html>`;
}

async function withAnim(browser, file, size, mode, fn) {
  const doc = recolor(JSON.parse(fs.readFileSync(file, 'utf8')), mode);
  const m = MODES[mode] || MODES.rovno;
  const p = await browser.newPage({ viewport: { width: size, height: size } });
  await p.setContent(page(size, m.bg, m.glow));
  await p.evaluate((d) => {
    window.anim = lottie.loadAnimation({ container: document.getElementById('a'), renderer: 'svg', loop: false, autoplay: false, animationData: d });
  }, doc);
  await fn(p, doc);
  await p.close();
}

async function frames(browser, dir, file, fps = 30, size = 360, mode = 'rovno') {
  fs.mkdirSync(dir, { recursive: true });
  await withAnim(browser, file, size, mode, async (p, doc) => {
    const n = doc.op; const step = doc.fr / fps;
    let i = 0;
    for (let f = 0; f < n - 1e-6; f += step) {
      await p.evaluate((fr) => window.anim.goToAndStop(fr, true), f);
      await p.screenshot({ path: path.join(dir, `f${String(i++).padStart(4, '0')}.png`) });
    }
  });
}

// 6 frames: the markers of key poses first, the rest evenly in between.
function pickFrames(doc) {
  const n = doc.op;
  const named = (doc.markers || []).filter((m) => !['poster', 'hold_loop_end', 'exit', 'enter', 'enter_end'].includes(m.cm)).map((m) => [m.tm, m.cm]);
  const out = new Map(named.slice(0, 6));
  const even = [0, 1, 2, 3, 4, 5].map((i) => Math.round((i * n) / 6));
  for (const e of even) {
    if (out.size >= 6) break;
    if (![...out.keys()].some((k) => Math.abs(k - e) < n / 14)) out.set(e, `${(e / doc.fr).toFixed(2)} s`);
  }
  for (let e = 0; out.size < 6; e += Math.round(n / 7)) out.set(e + 1, `${((e + 1) / doc.fr).toFixed(2)} s`);
  return [...out.entries()].sort((a, b) => a[0] - b[0]);
}

async function storyboard(browser, out, files, size = 300, mode = 'rovno') {
  const rows = [];
  for (const file of files) {
    const cells = [];
    await withAnim(browser, file, size, mode, async (p, doc) => {
      for (const [f, label] of pickFrames(doc)) {
        await p.evaluate((fr) => window.anim.goToAndStop(fr, true), f);
        const buf = await p.screenshot();
        cells.push({ img: buf.toString('base64'), label: `${label} · f${f}` });
      }
      rows.push({ name: path.basename(file, '.json'), cells, kb: (fs.statSync(file).size / 1024).toFixed(1), dur: (doc.op / doc.fr).toFixed(2) });
    });
  }
  await compose(browser, out, rows, size, mode);
}

async function compose(browser, out, rows, size, mode) {
  const m = MODES[mode] || MODES.rovno;
  const html = `<!doctype html><html><body style="margin:0;background:${m.bg};font:14px/1.3 ui-monospace,monospace;color:#cfe">
   ${rows.map((r) => `<div style="padding:10px 12px 2px">${r.name} · ${r.dur} s · ${r.kb} KB</div><div style="display:flex;gap:6px;padding:0 12px">
     ${r.cells.map((c) => `<div><img src="data:image/png;base64,${c.img}" width="${size}" height="${size}" style="display:block;border:1px solid #1d2b29"><div style="opacity:.6;font-size:12px">${c.label}</div></div>`).join('')}</div>`).join('')}
   </body></html>`;
  const w = 24 + rows.reduce((a, r) => Math.max(a, r.cells.length), 0) * (size + 8);
  const p = await browser.newPage({ viewport: { width: w, height: 400 } });
  await p.setContent(html);
  await p.screenshot({ path: out, fullPage: true });
  await p.close();
}

async function sheet(browser, out, cols, files, size = 240, mode = 'rovno') {
  const cells = [];
  for (const file of files) {
    await withAnim(browser, file, size, mode, async (p, doc) => {
      const poster = (doc.markers || []).find((m) => m.cm === 'poster');
      await p.evaluate((fr) => window.anim.goToAndStop(fr, true), poster ? poster.tm : Math.round(doc.op * 0.6));
      cells.push({ img: (await p.screenshot()).toString('base64'), name: path.basename(file, '.json'), kb: (fs.statSync(file).size / 1024).toFixed(0) });
    });
  }
  const m = MODES[mode] || MODES.rovno;
  const html = `<!doctype html><html><body style="margin:0;background:${m.bg};font:13px/1.3 ui-monospace,monospace;color:#cfe">
   <div style="display:grid;grid-template-columns:repeat(${cols},${size}px);gap:8px;padding:12px">
   ${cells.map((c) => `<div><img src="data:image/png;base64,${c.img}" width="${size}" height="${size}" style="display:block;border:1px solid #1d2b29"><div style="opacity:.7">${c.name} · ${c.kb} KB</div></div>`).join('')}
   </div></body></html>`;
  const p = await browser.newPage({ viewport: { width: cols * (size + 8) + 24, height: 400 } });
  await p.setContent(html);
  await p.screenshot({ path: out, fullPage: true });
  await p.close();
}

(async () => {
  const [cmd, out, ...rest] = process.argv.slice(2);
  const browser = await chromium.launch();
  try {
    if (cmd === 'storyboard') {
      const mode = process.env.MODE || 'rovno';
      await storyboard(browser, out, rest, Number(process.env.SIZE || 300), mode);
    } else if (cmd === 'frames') {
      const [file, fps, size, mode] = rest;
      await frames(browser, out, file, Number(fps || 30), Number(size || 360), mode || 'rovno');
    } else if (cmd === 'sheet') {
      const [cols, ...files] = rest;
      await sheet(browser, out, Number(cols), files, Number(process.env.SIZE || 240), process.env.MODE || 'rovno');
    } else {
      throw new Error('usage: storyboard|frames|sheet');
    }
  } finally {
    await browser.close();
  }
})();
