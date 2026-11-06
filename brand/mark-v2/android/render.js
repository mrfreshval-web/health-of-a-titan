// usage: node render.js <out.png> <size> <svgfile> [bg] ; or node render.js --sheet <out.png> <cell> <files...>
const { chromium } = require('playwright');
const fs = require('fs');
(async () => {
  const args = process.argv.slice(2);
  const browser = await chromium.launch();
  const page = await browser.newPage({ deviceScaleFactor: 1 });
  if (args[0] === '--sheet') {
    const [, out, cell, ...files] = args;
    const imgs = files.map(f => `<div style="display:inline-block;margin:6px;text-align:center;color:#aaa;font:11px sans-serif"><img src="data:image/svg+xml;base64,${fs.readFileSync(f).toString('base64')}" width="${cell}" height="${cell}"><br>${f.split('/').pop()}</div>`).join('');
    await page.setContent(`<body style="margin:0;background:#333">${imgs}</body>`);
    await page.waitForTimeout(300);
    await page.screenshot({ path: out, fullPage: true });
  } else {
    const [out, size, file, bg] = args;
    const s = parseInt(size, 10);
    await page.setViewportSize({ width: s, height: s });
    const data = fs.readFileSync(file).toString('base64');
    await page.setContent(`<html><body style="margin:0;background:${bg || 'transparent'}"><img src="data:image/svg+xml;base64,${data}" width="${s}" height="${s}" style="display:block"></body></html>`);
    await page.waitForTimeout(200);
    await page.screenshot({ path: out, omitBackground: !bg, clip: { x: 0, y: 0, width: s, height: s } });
  }
  await browser.close();
})();
