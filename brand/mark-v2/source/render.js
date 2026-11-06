// usage: node render.js in.svg|in.html out.png width height [dpr] [bg]
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
(async () => {
  const [inp, out, w, h, dpr = '1', bg = 'transparent'] = process.argv.slice(2);
  const W = +w, H = +h;
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: W, height: H }, deviceScaleFactor: +dpr });
  if (inp.endsWith('.html')) {
    await page.goto('file://' + path.resolve(inp));
  } else {
    const svg = fs.readFileSync(inp, 'utf8');
    await page.setContent(`<html><body style="margin:0;background:${bg}">` +
      `<img src="data:image/svg+xml;base64,${Buffer.from(svg).toString('base64')}" style="display:block;width:${W}px;height:${H}px"></body></html>`);
  }
  await page.waitForLoadState('networkidle');
  await page.evaluate(() => document.fonts && document.fonts.ready);
  await page.waitForTimeout(300);
  await page.screenshot({ path: out, omitBackground: bg === 'transparent', fullPage: inp.endsWith('.html') });
  await browser.close();
})();
