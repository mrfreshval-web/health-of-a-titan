// Storyboard of HotSyncMark (android/core/ui/.../HotSyncMark.kt) rendered in Chromium with a
// canvas port of the same drawing code and the same generated geometry (HotMarkGeometry.kt).
// usage: node sync_storyboard.js <HotMarkGeometry.kt> <out.png>
// It is a preview for design review, not a screenshot of the app: the Kotlin code is the source.
const { chromium } = require('playwright');
const fs = require('fs');

const [, , geomFile, out] = process.argv;
const kt = fs.readFileSync(geomFile, 'utf8');
const str = (name) => {
  const m = kt.match(new RegExp(`const val ${name} =\\n([\\s\\S]*?)\\n(\\n|})`));
  return m[1].split('\n').map((l) => l.trim().replace(/^"/, '').replace(/" \+?$/, '').replace(/"$/, '')).join('');
};
const num = (name) => parseFloat(kt.match(new RegExp(`const val ${name} = ([0-9.]+)f`))[1]);
const pulse = [...kt.matchAll(/floatArrayOf\(([^)]*)\)/g)].map((m) => m[1].split(',').map((v) => parseFloat(v)));
const G = {
  cx: num('RING_CX'), cy: num('RING_CY'), r: num('RING_R'),
  lx: num('LEFT_COLUMN_X'), tx: num('TITAN_X'), rx: num('RIGHT_COLUMN_X'),
  colY: num('COLUMNS_MID_Y'), titY: num('TITAN_MID_Y'),
  left: str('LEFT_COLUMN'), right: str('RIGHT_COLUMN'), titan: str('TITAN'), pulse,
};

const frames = [
  ['Ожидание', { phase: 'IDLE' }],
  ['Синхр. · дни', { phase: 'RUNNING', t: 0.12, ring: 0.12 }],
  ['· восстановление', { phase: 'RUNNING', t: 0.5, ring: 0.32 }],
  ['· сон', { phase: 'RUNNING', t: 0.78, ring: 0.52 }],
  ['· тренировки', { phase: 'RUNNING', t: 0.3, ring: 0.72 }],
  ['Успех · кольцо', { phase: 'SUCCESS', f: 0.3 }],
  ['Успех · волна', { phase: 'SUCCESS', f: 0.6 }],
  ['Готово', { phase: 'SUCCESS', f: 1 }],
  ['Ошибка', { phase: 'ERROR' }],
];

const html = `<html><body style="margin:0;background:#0B0E15;font:13px Manrope,sans-serif;color:#A9A292">
<div id="grid" style="display:flex;flex-wrap:wrap;width:${3 * 260}px"></div>
<script>
const G = ${JSON.stringify(G)}; const frames = ${JSON.stringify(frames)};
const GOLD='#D9B562', HI='#F3E2B0', MUTED='#938C7C', TRACK='rgba(217,181,98,0.14)', ON='#1C1505', BADGE='#222937', ONB='#A9A292';
const parts=[[new Path2D(G.left),G.lx,G.colY,420],[new Path2D(G.titan),G.tx,G.titY,520],[new Path2D(G.right),G.rx,G.colY,420]];
const bump=(h,x)=>Math.exp(-(((h-x)/150)**2));
const ease=t=>1-(1-t)**3;
function rgba(hex,a){const n=parseInt(hex.slice(1),16);return 'rgba('+(n>>16)+','+((n>>8)&255)+','+(n&255)+','+a+')'}
function glow(c,col,x,y,r,a){if(a<=0.004)return;const g=c.createRadialGradient(x,y,0,x,y,r);g.addColorStop(0,rgba(col,a));g.addColorStop(.45,rgba(col,a*.35));g.addColorStop(1,rgba(col,0));c.fillStyle=g;c.beginPath();c.arc(x,y,r,0,7);c.fill()}
function poly(c,p){c.beginPath();c.moveTo(p[0],p[1]);for(let i=2;i<p.length;i+=2)c.lineTo(p[i],p[i+1])}
function yAt(x){for(const p of G.pulse){if(x<p[0]||x>p[p.length-2])continue;for(let i=0;i+3<p.length;i+=2){if(x>=p[i]&&x<=p[i+2]){const k=(x-p[i])/Math.max(.001,p[i+2]-p[i]);return p[i+1]+(p[i+3]-p[i+1])*k}}}return null}
function draw(c,S,st){
  const r=G.r, view=2*r*1.075, s=S/view, ringW=(1.5*2+S*.006)/s, pulseW=(1.1*2+S*.006)/s;
  c.save();c.translate(S/2-G.cx*s,S/2-G.cy*s);c.scale(s,s);
  const err=st.phase==='ERROR'?1:0, f=st.phase==='SUCCESS'?st.f:1, run=st.phase==='RUNNING';
  const head=run?2050*st.t:NaN, base=err?MUTED:GOLD, dim=(run||err)?.5:(st.phase==='SUCCESS'?Math.min(1,.5+f*1.2):1);
  if(run) parts.forEach(p=>glow(c,GOLD,p[1],p[2],p[3],.3*bump(head,p[1])));
  if(f<1){const e=Math.max(0,Math.min(1,(f-.22)/.78));if(e>0){c.strokeStyle=rgba(GOLD,.6*(1-e)**2);c.lineWidth=ringW*(2.6-1.6*e);c.beginPath();c.arc(G.cx,G.cy,r*(1+.2*ease(e)),0,7);c.stroke()}
    glow(c,GOLD,G.cx,G.cy+r*.12,r*.87,.34*Math.sin(Math.min(f,.7)/.7*Math.PI))}
  const arc=run?st.ring:(st.phase==='SUCCESS'?Math.min(1,.72+f*3):1);
  if(err){c.setLineDash([ringW*2.2,ringW*2.6]);c.strokeStyle=rgba(MUTED,.7);c.lineWidth=ringW;c.beginPath();c.arc(G.cx,G.cy,r,0,7);c.stroke();c.setLineDash([])}
  else{ if(arc<1){c.strokeStyle=TRACK;c.lineWidth=ringW;c.beginPath();c.arc(G.cx,G.cy,r,0,7);c.stroke()}
    c.strokeStyle=GOLD;c.lineWidth=ringW;c.lineCap=arc<1?'round':'butt';c.beginPath();c.arc(G.cx,G.cy,r,-Math.PI/2,-Math.PI/2+2*Math.PI*arc);c.stroke();
    if(run){const a=-Math.PI/2+2*Math.PI*arc,x=G.cx+r*Math.cos(a),y=G.cy+r*Math.sin(a);glow(c,HI,x,y,ringW*7,.55);c.fillStyle=HI;c.beginPath();c.arc(x,y,ringW*.9,0,7);c.fill()}}
  c.lineCap='round';c.lineJoin='round';c.lineWidth=pulseW;c.strokeStyle=rgba(base,run?.32:.85-.45*err);G.pulse.forEach(p=>{poly(c,p);c.stroke()});
  if(run){const tail=head-430;const g=c.createLinearGradient(tail,0,head,0);g.addColorStop(0,rgba(HI,0));g.addColorStop(.7,rgba(HI,.55));g.addColorStop(1,HI);
    G.pulse.forEach(p=>{const L=Math.max(p[0]-pulseW,tail),R=Math.min(p[p.length-2]+pulseW,head);if(R>L){c.save();c.beginPath();c.rect(L,-1e5,R-L,2e5);c.clip();poly(c,p);c.strokeStyle=g;c.lineWidth=pulseW*1.7;c.stroke();c.restore()}});
    const y=yAt(head);if(y!==null){glow(c,HI,head,y,pulseW*9,.6);c.fillStyle=HI;c.beginPath();c.arc(head,y,pulseW*1.5,0,7);c.fill()}}
  parts.forEach(p=>{const a=run?Math.min(1,dim+(1-dim)*bump(head,p[1])):dim;c.fillStyle=rgba(base,a);c.fill(p[0])});
  const br=r*.2,bx=G.cx+r*Math.SQRT1_2,by=G.cy+r*Math.SQRT1_2;
  if(st.phase==='SUCCESS'){const k=Math.max(0,Math.min(1,(f-.4)/.25)),ck=Math.max(0,Math.min(1,(f-.52)/.38));
    if(k>0){c.fillStyle=GOLD;c.beginPath();c.arc(bx,by,br*ease(k),0,7);c.fill();
      if(ck>0){const pts=[[-.42,.02],[-.12,.32],[.44,-.28]];const l1=Math.hypot(.3,.3),l2=Math.hypot(.56,.6),L=(l1+l2)*ease(ck);c.strokeStyle=ON;c.lineWidth=br*.17;c.beginPath();c.moveTo(bx+pts[0][0]*br,by+pts[0][1]*br);
        if(L<=l1){c.lineTo(bx+(pts[0][0]+.3*L/l1)*br,by+(pts[0][1]+.3*L/l1)*br)}else{c.lineTo(bx+pts[1][0]*br,by+pts[1][1]*br);const q=(L-l1)/l2;c.lineTo(bx+(pts[1][0]+.56*q)*br,by+(pts[1][1]-.6*q)*br)}c.stroke()}}}
  if(err){c.fillStyle=BADGE;c.beginPath();c.arc(bx,by,br,0,7);c.fill();c.strokeStyle=MUTED;c.lineWidth=ringW;c.stroke();c.strokeStyle=ONB;c.lineWidth=br*.14;c.beginPath();c.moveTo(bx,by-br*.45);c.lineTo(bx,by+br*.12);c.stroke();c.fillStyle=ONB;c.beginPath();c.arc(bx,by+br*.42,br*.087,0,7);c.fill()}
  c.restore();
}
const grid=document.getElementById('grid');
frames.forEach(([label,st])=>{const d=document.createElement('div');d.style.cssText='width:260px;height:250px;text-align:center';
  const cv=document.createElement('canvas');cv.width=cv.height=200*2;cv.style.cssText='width:200px;height:200px;margin:14px 30px 4px;background:#151A24;border-radius:18px';
  d.appendChild(cv);const t=document.createElement('div');t.textContent=label;d.appendChild(t);grid.appendChild(d);draw(cv.getContext('2d'),400,st)});
</script></body></html>`;

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ deviceScaleFactor: 1 });
  await page.setViewportSize({ width: 780, height: 760 });
  page.on('pageerror', (e) => console.error('pageerror', e.message));
  await page.setContent(html);
  await page.waitForTimeout(300);
  await page.screenshot({ path: out, fullPage: true });
  await browser.close();
})();
