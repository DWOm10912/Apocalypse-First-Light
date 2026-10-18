// Drawing a town plan (layout.mjs) on a sheet: blocks and lots by zone, streets by class (right of way and asphalt),
// street names along the streets, terrace and steep marks.
import { ZONES, SECTIONS } from './layout.mjs';
import { f, text } from './svg.mjs';

const pathOf = (pts, P) => 'M' + pts.map(p => { const q = P(p[0], p[1]); return f(q[0]) + ',' + f(q[1]); }).join('L');

export function drawBlocks(blocks, P, opts = {}) {
  let s = '';
  for (const b of blocks) {
    const steep = b.relief > (opts.terrace ?? 6);
    for (const l of b.lots) s += `<path d="${pathOf(l.poly, P)}Z" fill="${ZONES[l.zone].fill}" stroke="#7b6e5c" stroke-width="${opts.lotStroke ?? 0.5}"/>`;
    s += `<path d="${pathOf(b.poly, P)}Z" fill="${steep ? 'url(#terrace)' : 'none'}" stroke="#5b5146" stroke-width="${opts.blockStroke ?? 1.1}"/>`;
    if (b.label) {
      const c = b.poly.reduce((a, p) => [a[0] + p[0] / b.poly.length, a[1] + p[1] / b.poly.length], [0, 0]), q = P(c[0], c[1]);
      s += text(q[0], q[1] + 4, b.label, `font-size="${opts.labelSize ?? 11}" font-weight="700" fill="#2b2f33" text-anchor="middle" stroke="#ffffffcc" stroke-width="3" paint-order="stroke"`);
    }
  }
  return s;
}

/** Streets: the right of way light, the asphalt dark (widths in metres times the sheet scale k px/m). */
export function drawStreets(streets, P, k) {
  let s = '';
  for (const st of streets) s += `<path d="${pathOf(st.pts, P)}" fill="none" stroke="#f4f2ec" stroke-width="${f(SECTIONS[st.cls].row * k)}" stroke-linecap="butt" stroke-linejoin="round"/>`;
  for (const st of streets) s += `<path d="${pathOf(st.pts, P)}" fill="none" stroke="${st.cls === 'C14' || st.cls === 'A20' ? '#4d5255' : '#6c7174'}" stroke-width="${f(SECTIONS[st.cls].asphalt * k)}" stroke-linecap="butt" stroke-linejoin="round"/>`;
  return s;
}

/** Street names: centred on the street's middle stretch, turned along it (kept upright). */
export function nameStreets(streets, P, size = 11) {
  let s = '';
  for (const st of streets) {
    if (!st.name) continue;
    const m = st.labelAt ?? 0.5, i = Math.max(1, Math.min(st.pts.length - 1, Math.round((st.pts.length - 1) * m)));
    const a = P(...st.pts[i - 1]), b = P(...st.pts[i]);
    let ang = Math.atan2(b[1] - a[1], b[0] - a[0]) * 180 / Math.PI;
    if (ang > 90) ang -= 180;
    if (ang < -90) ang += 180;
    s += `<text transform="translate(${f(b[0])},${f(b[1])}) rotate(${f(ang)})" y="${f(size * 0.36)}" font-family="Bahnschrift, Arial Narrow, sans-serif" font-size="${size}" font-weight="600" fill="#ffffff" text-anchor="middle">${st.name}</text>`;
  }
  return s;
}

export const TERRACE_PATTERN = `<pattern id="terrace" width="7" height="7" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><line x1="0" y1="0" x2="0" y2="7" stroke="#6b5640" stroke-width="1.1" opacity="0.55"/></pattern>`;
