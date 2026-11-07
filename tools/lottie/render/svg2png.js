// node render/svg2png.js <in.svg> <out.png> [width=1024] — rasterises an SVG in headless Chromium.
const fs = require('fs');
const { chromium } = require('playwright');

(async () => {
  const [inp, out, w = '1024'] = process.argv.slice(2);
  const svg = fs.readFileSync(inp, 'utf8');
  const browser = await chromium.launch();
  const p = await browser.newPage({ viewport: { width: Number(w), height: Number(w) } });
  await p.setContent(`<html><body style="margin:0;background:#000">${svg.replace('<svg ', `<svg style="width:${w}px;height:auto;display:block" `)}</body></html>`);
  const el = await p.$('svg');
  await el.screenshot({ path: out });
  await browser.close();
})();
