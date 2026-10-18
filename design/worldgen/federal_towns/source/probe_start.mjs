// How the forest-home start region (FederalTemplate START 300,-400 r 2000) meets Linden's town limit and F-40 on r1.
import { lindenPlan } from './linden.mjs';

const L = lindenPlan(), { F } = L;
const SX = 300, SZ = -400, SR = 2000;
const d = (p) => Math.hypot(p[0] - SX, p[1] - SZ);
// town limit (same polygon as T2)
const lim = [];
for (let s = -1180; s <= 540; s += 20) lim.push(F.W(s, 470));
for (let s = 540; s >= -1180; s -= 20) lim.push(F.W(s, s > 120 ? -250 : -205));
let best = Infinity, bp = null, inside = 0;
for (const p of lim) { const q = d(p); if (q < best) { best = q; bp = p; } if (q < SR) inside++; }
console.log('town limit nearest to start centre', best.toFixed(0), 'm at', bp.map(Math.round), 'limit points inside the start circle', inside, '/', lim.length);
// share of the start circle (8 m grid) inside the town limit, approx via town coords
let tot = 0, inTown = 0, nearF40 = 0;
const inv = (x, z) => { let bs = 0, bv = 0, bd = Infinity; for (let s = -1400; s <= 800; s += 10) { const [ax, az] = F.W(s, 0), [ux, uz] = F.W(s, 1); const nx = ux - ax, nz = uz - az; const v = (x - ax) * nx + (z - az) * nz; const [px, pz] = F.W(s, v); const dd = Math.hypot(px - x, pz - z); if (dd < bd) { bd = dd; bs = s; bv = v; } } return [bs, bv, bd]; };
for (let z = SZ - SR; z <= SZ + SR; z += 50) for (let x = SX - SR; x <= SX + SR; x += 50) {
  if (Math.hypot(x - SX, z - SZ) > SR) continue;
  tot++;
  const [s, v, dd] = inv(x, z);
  if (dd < 30 && s > -1180 && s < 540 && v < 470 && v > (s > 120 ? -250 : -205)) inTown++;
}
// F-40 distance to start centre
let fb = Infinity; for (const p of L.f40) fb = Math.min(fb, d(p));
console.log('start circle cells', tot, 'inside Linden limit', inTown, `(${(100 * inTown / tot).toFixed(1)}%)`);
console.log('F-40 nearest to start centre', fb.toFixed(0), 'm');
