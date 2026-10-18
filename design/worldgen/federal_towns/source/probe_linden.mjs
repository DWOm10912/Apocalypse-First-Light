// Working probe: the ground's relief over Linden's candidate blocks (bands of 70 m between streets, cross streets
// every 150 m along F-40). node probe_linden.mjs
import { loadGrid } from './raster.mjs';
import { loadRoads, frame } from './frame.mjs';

const g = loadGrid('valley_town'), R = loadRoads();
const a = R.routes.find(r => r.id === 'interstate_ew:county_seat-valley_town').control;
const b = R.routes.find(r => r.id === 'interstate_ew:valley_town-river_city').control;
const F = frame(a.concat(b.slice(1)), R.centre);
const bands = { N3: [279, 349], N2: [187, 257], N1: [91, 161], S1: [-161, -91], S2: [-253, -183], S3: [-345, -275], N4: [371, 441] };
const S = [-900, -750, -600, -450, -300, -150, 0, 150, 300, 450, 600, 750, 900];
console.log('band  ' + S.slice(0, -1).map(s => String(s).padStart(9)).join(''));
for (const [k, [v0, v1]] of Object.entries(bands)) {
  let row = k.padEnd(6);
  for (let i = 0; i < S.length - 1; i++) {
    const r = (() => { let lo = Infinity, hi = -Infinity; for (let s = S[i] + 11; s <= S[i + 1] - 11; s += 8) for (let v = v0; v <= v1; v += 8) { const [x, z] = F.W(s, v), h = g.at(x, z); lo = Math.min(lo, h); hi = Math.max(hi, h); } return { lo, hi }; })();
    row += `${r.lo.toFixed(0)}+${(r.hi - r.lo).toFixed(0)}`.padStart(9);
  }
  console.log(row);
}
// F-40 itself: height and the frame's tangent direction at a few stations
for (const s of [-600, -300, 0, 300, 600]) { const q = F.at(s); console.log('s', s, 'dir', (Math.atan2(-q.tz, q.tx) * 180 / Math.PI).toFixed(0), 'deg (north of east)', 'ground', g.at(q.x, q.z).toFixed(1)); }
// how far the raw F-40 runs from the smoothed axis
let worst = 0;
for (const p of F.raw) { let best = Infinity; for (let s = -900; s <= 900; s += 5) { const [x, z] = F.W(s, 0); best = Math.min(best, Math.hypot(p[0] - x, p[1] - z)); } if (Math.hypot(p[0] - R.centre[0], p[1] - R.centre[1]) < 900) worst = Math.max(worst, best); }
console.log('F-40 off the axis within 900 m:', worst.toFixed(1), 'm');
