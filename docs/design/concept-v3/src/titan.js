// The Titan from mark v2, re-lit for concept v3 «Титаниум».
// Parts are taken verbatim from brand/mark-v2/master/hot-master-transparent.svg (traced from R1) —
// the figure is the same Titan, only the material changes: line of light, glass knockout, glow.
const fs = require('fs');
const path = require('path');

const MARK = path.resolve(__dirname, '../../../../brand/mark-v2/master/hot-master-transparent.svg');
const GEOM = require(path.resolve(__dirname, '../../../../brand/mark-v2/source/geom.json'));
const SRC = fs.readFileSync(MARK, 'utf8');

function group(id) {
  const start = SRC.indexOf(`<g id="${id}"`);
  if (start < 0) throw new Error(`no group ${id}`);
  const re = /<g\b|<\/g>/g;
  re.lastIndex = start;
  let depth = 0; let m;
  while ((m = re.exec(SRC))) {
    depth += m[0] === '</g>' ? -1 : 1;
    if (depth === 0) return SRC.slice(start, m.index + 4);
  }
  throw new Error(`unclosed ${id}`);
}
const strip = (g) => g.replace(/ stroke="none"/g, '');

// One hidden <svg> per page with the shared parts; every scene <use>s them.
const DEFS = `<svg width="0" height="0" style="position:absolute" aria-hidden="true"><defs>
  ${group('hot-titan-art')}
  ${strip(group('hot-titan-knockout')).replace('id="hot-titan-knockout"', 'id="tt-sil"')}
  ${group('hot-columns-art')}
  ${group('hot-columns-knockout')}
  <clipPath id="tt-left"><rect x="0" y="0" width="1024" height="2048"/></clipPath>
  <clipPath id="tt-right"><rect x="1024" y="0" width="1024" height="2048"/></clipPath>
  <clipPath id="tt-nohands"><path clip-rule="evenodd" d="M0 0H2048V2048H0Z M495 617H648V823H495Z M1400 617H1553V823H1400Z"/></clipPath>
  <clipPath id="tt-hands"><path d="M495 617H648V823H495Z M1400 617H1553V823H1400Z"/></clipPath>
</defs></svg>`;

const PULSE_D = GEOM.pulse.map((p) => p.pts.map(([x, y], i) => `${i ? 'L' : 'M'}${x} ${y}`).join(' '));

let n = 0;
// Scene in the mark's 2048 space. All options are optional.
//  light: [top, mid, bottom] colours of the line of light for the Titan; colLight for the columns
//  knock: fill of the glass knockout behind lines; spread: 0..1.04 columns position (1 = mark)
//  tScale, tY, tBlur, tOpacity: Titan approach; ring: 0..1 progress of the circle; ringCol; pulse: 0..1
//  glow: css colour of the glow or false; crop: viewBox override
function titanScene(size, o = {}) {
  const id = `ts${++n}`;
  const {
    light = ['#FFFFFF', '#FFF1D6', '#FFD08A'], colLight = ['#E9EEF3', '#B9C2CC', '#8D97A3'], colOpacity = 1,
    knock = 'rgba(8,9,12,.92)', spread = 1, tScale = 1, tY = 0, tBlur = 0, tOpacity = 1, titan = true, columns = true,
    ring = 0, ringCol = '#FFC77A', ringW = 11, ringOpacity = 1, pulse = 0, pulseCol = '#FFC77A', pulseOpacity = .9,
    glow = 'rgba(255,199,122,.55)', colGlow = false, crop = '0 0 2048 2048', titanFill = null, style = '',
  } = o;
  const d = (1 - spread) * (1024 - GEOM.colL);
  const C = 2 * Math.PI * 940;
  const grad = (gid, c) => `<linearGradient id="${gid}" gradientUnits="userSpaceOnUse" x1="0" y1="450" x2="0" y2="1820"><stop offset="0" stop-color="${c[0]}"/><stop offset=".5" stop-color="${c[1]}"/><stop offset="1" stop-color="${c[2]}"/></linearGradient>`;
  const glowCss = (c, k = 1) => c ? `filter:drop-shadow(0 0 ${2.5 * k}px ${c}) drop-shadow(0 0 ${9 * k}px ${c})` : '';
  const col = (side, dx) => `<g clip-path="url(#tt-${side})" transform="translate(${dx} 0)">
      <use href="#hot-columns-knockout" fill="${knock}" stroke="${knock}"/>
      <use href="#hot-columns-art" fill="url(#${id}c)" stroke="url(#${id}c)"/></g>`;
  return `<svg width="${size}" height="${size}" viewBox="${crop}" style="overflow:visible;${style}">
    <defs>${grad(`${id}t`, light)}${grad(`${id}c`, colLight)}
      ${tBlur ? `<filter id="${id}f" x="-20%" y="-20%" width="140%" height="140%"><feGaussianBlur stdDeviation="${tBlur}"/></filter>` : ''}</defs>
    ${ring ? `<circle cx="1024.2" cy="1023" r="940" fill="none" stroke="${ringCol}" stroke-width="${ringW}" stroke-linecap="round" opacity="${ringOpacity}"
        stroke-dasharray="${(C * ring).toFixed(1)} ${C.toFixed(1)}" transform="rotate(-90 1024.2 1023)" style="${glowCss(glow ? ringCol : false, 1.2)}"/>` : ''}
    ${pulse ? `<g fill="none" stroke="${pulseCol}" stroke-width="7" stroke-linejoin="round" stroke-linecap="round" opacity="${pulseOpacity}" style="${glowCss(glow ? pulseCol : false, .8)}">
        ${PULSE_D.map((p) => `<path d="${p}" pathLength="100" stroke-dasharray="${100 * pulse} 100"/>`).join('')}</g>` : ''}
    ${columns ? `<g opacity="${colOpacity}" style="${glowCss(colGlow)}">${col('left', d)}${col('right', -d)}</g>` : ''}
    ${titan ? `<g transform="translate(1024 1250) scale(${tScale}) translate(-1024 ${-1250 + tY})" opacity="${tOpacity}" ${tBlur ? `filter="url(#${id}f)"` : ''}>
        <use href="#tt-sil" clip-path="url(#tt-nohands)" fill="${titanFill || knock}" stroke="none"/>
        <g style="${glowCss(glow)}"><use href="#hot-titan-art" fill="url(#${id}t)"/></g></g>` : ''}
  </svg>`;
}

// Titan as a stencil: dashed outline of the silhouette (+ faint inner lines).
function titanStencil(size, { col = 'rgba(255,255,255,.85)', dash = '22 16', w = 7, inner = .16, crop = '0 0 2048 2048', glow = 'rgba(125,227,209,.6)' } = {}) {
  const [, , cw, ch] = crop.split(' ').map(Number);
  return `<svg width="${size}" height="${size * ch / cw}" viewBox="${crop}" style="overflow:visible;display:block">
    <use href="#hot-titan-art" fill="${col}" opacity="${inner}"/>
    <g style="filter:drop-shadow(0 0 4px ${glow})">
      <use href="#tt-sil" clip-path="url(#tt-nohands)" fill="none" stroke="${col}" stroke-width="${w}" stroke-dasharray="${dash}" stroke-linecap="round" stroke-linejoin="round"/>
      <use href="#hot-titan-art" clip-path="url(#tt-hands)" fill="${col}"/></g>
  </svg>`;
}

// Titan lit by spheres: body regions clipped by bands, each band's light = progress of its sphere.
// zones: [{y0,y1,x0,x1,lit}] in mark space.
function titanZones(size, zones, { base = 'rgba(255,255,255,.14)', knock = 'rgba(8,9,12,.9)', lit = ['#FFFFFF', '#FFF1D6', '#FFD08A'], crop = '0 0 2048 2048' } = {}) {
  const id = `tz${++n}`;
  return `<svg width="${size}" height="${size}" viewBox="${crop}" style="overflow:visible">
    <defs><linearGradient id="${id}g" gradientUnits="userSpaceOnUse" x1="0" y1="450" x2="0" y2="1820"><stop offset="0" stop-color="${lit[0]}"/><stop offset=".5" stop-color="${lit[1]}"/><stop offset="1" stop-color="${lit[2]}"/></linearGradient>
    <filter id="${id}fb" x="-10%" y="-10%" width="120%" height="120%"><feGaussianBlur stdDeviation="28"/></filter>
    ${zones.map((z, i) => `<mask id="${id}z${i}" maskUnits="userSpaceOnUse" x="0" y="0" width="2048" height="2048"><g filter="url(#${id}fb)">${(z.rects || [z]).map((r) => `<rect x="${r.x0}" y="${r.y0}" width="${r.x1 - r.x0}" height="${r.y1 - r.y0}" fill="#fff"/>`).join('')}</g></mask>`).join('')}</defs>
    <use href="#tt-sil" clip-path="url(#tt-nohands)" fill="${knock}"/>
    <use href="#hot-titan-art" fill="${base}"/>
    ${zones.map((z, i) => `<g mask="url(#${id}z${i})" opacity="${z.lit}" style="filter:drop-shadow(0 0 3px ${z.glow || 'rgba(255,199,122,.7)'}) drop-shadow(0 0 10px ${z.glow || 'rgba(255,199,122,.5)'})"><use href="#hot-titan-art" fill="${z.color || `url(#${id}g)`}"/></g>`).join('')}
  </svg>`;
}

module.exports = { DEFS, titanScene, titanStencil, titanZones, GEOM };
