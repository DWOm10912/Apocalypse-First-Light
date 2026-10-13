// Cabin Exterior V1 open-shell audit (docs/models/cabin_exterior_v1.md, M1.1 2026-10-12): every mesh piece is rendered
// alone from 42 directions with a small software z-buffer (1 cm pixels); a pixel whose nearest polygon faces away from
// the viewer is a hole (the renderer culls back faces, so the player looks through the part there). In game the user
// saw a window standing alone turn see-through from the side and from above (single-sided lining, casing and head).
// A piece meant to continue into a neighbour (log runs, roof cells, a door half) is open at that block face on purpose:
// the report lists where the holes are (the block faces they are seen through) so those can be told apart.
//   node tools/cabin/audit-open-shells.mjs [filter]   -> per piece: hole area (cm2) and the faces it is seen through
//   node tools/cabin/audit-open-shells.mjs a+b@1+c@0,0,1  -> one assembly: pieces a, b turned a quarter, c one block up (dx,dy,dz)
//   ... --pts=out,d                                    -> also where those holes are (5 cm groups, the back face hit)
import {pieces} from '../build-cabin-exterior-v1.mjs';
import {Piece, turned, newell, dot, cross, norm} from './cabin-geo.mjs';

const RES = 0.01, filter = process.argv[2] ?? '', PTS = (process.argv.find(a => a.startsWith('--pts=')) ?? '').slice(6).split(',').filter(Boolean);
const DIRS = [];
for (const x of [-1, 0, 1]) for (const y of [-1, 0, 1]) for (const z of [-1, 0, 1]) if (x || y || z) DIRS.push(norm([x, y, z]));
for (let i = 0; i < 16; i++) {   // a Fibonacci spread between them
  const y = 1 - (i + 0.5) / 8, r = Math.sqrt(Math.max(0, 1 - y * y)), a = i * 2.399963;
  DIRS.push(norm([Math.cos(a) * r, y, Math.sin(a) * r]));
}

/** Where a ray from the viewer (looking along d) enters the cell's cube [0, 1]^3 last before the hit: 'n'/'s'/... or 'out'. */
function seenThrough(p, d) {
  // walk back from the hit point toward the viewer; the first cube face crossed (leaving the cube) is the face it came in by
  let best = 'in', tb = Infinity;
  const F = [['w', 0, 0], ['e', 0, 1], ['d', 1, 0], ['u', 1, 1], ['n', 2, 0], ['s', 2, 1]];
  const inside = p.every(c => c > -1e-6 && c < 1 + 1e-6);
  if (!inside) return 'out';
  for (const [f, ax, v] of F) {
    const dv = -d[ax];
    if (Math.abs(dv) < 1e-9) continue;
    const t = (v - p[ax]) / dv;
    if (t <= 1e-9 || t >= tb) continue;
    const q = p.map((c, i) => c - d[i] * t);
    if (q.every((c, i) => i === ax || (c > -1e-6 && c < 1 + 1e-6))) { tb = t; best = f; }
  }
  return best;
}

function auditPiece(pc) {
  const polys = pc.polys.map(p => ({v: p.v, fn: newell(p.v), t: p.t}));
  let holes = 0;
  const via = {}, tex = {}, where = {};
  for (const d of DIRS) {
    const up = Math.abs(d[1]) < 0.9 ? [0, 1, 0] : [1, 0, 0], U = norm(cross(up, d)), V = cross(d, U);
    const proj = polys.map(p => p.v.map(q => [dot(q, U), dot(q, V), dot(q, d)]));
    let u0 = Infinity, u1 = -Infinity, v0 = Infinity, v1 = -Infinity;
    for (const pp of proj) for (const [u, v] of pp) { u0 = Math.min(u0, u); u1 = Math.max(u1, u); v0 = Math.min(v0, v); v1 = Math.max(v1, v); }
    const W = Math.ceil((u1 - u0) / RES) + 1, H = Math.ceil((v1 - v0) / RES) + 1;
    const depth = new Float64Array(W * H).fill(Infinity), who = new Int32Array(W * H).fill(-1);
    proj.forEach((pp, pi) => {
      const front = dot(polys[pi].fn, d) < 0;
      for (let k = 1; k + 1 < pp.length; k++) {
        const a = pp[0], b = pp[k], c = pp[k + 1];
        const den = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1]);
        if (Math.abs(den) < 1e-12) continue;
        const iu0 = Math.max(0, Math.floor((Math.min(a[0], b[0], c[0]) - u0) / RES)), iu1 = Math.min(W - 1, Math.ceil((Math.max(a[0], b[0], c[0]) - u0) / RES));
        const iv0 = Math.max(0, Math.floor((Math.min(a[1], b[1], c[1]) - v0) / RES)), iv1 = Math.min(H - 1, Math.ceil((Math.max(a[1], b[1], c[1]) - v0) / RES));
        for (let iv = iv0; iv <= iv1; iv++) for (let iu = iu0; iu <= iu1; iu++) {
          const x = u0 + (iu + 0.5) * RES, y = v0 + (iv + 0.5) * RES;
          const l1 = ((b[1] - c[1]) * (x - c[0]) + (c[0] - b[0]) * (y - c[1])) / den, l2 = ((c[1] - a[1]) * (x - c[0]) + (a[0] - c[0]) * (y - c[1])) / den, l3 = 1 - l1 - l2;
          if (l1 < -1e-9 || l2 < -1e-9 || l3 < -1e-9) continue;
          const z = l1 * a[2] + l2 * b[2] + l3 * c[2], i = iv * W + iu, cur = depth[i];
          // nearer wins; a tie goes to a polygon that faces the viewer (double-sided glass, coincident faces)
          if (z < cur - 1e-5 || (Math.abs(z - cur) <= 1e-5 && front && !(who[i] >= 0 && dot(polys[who[i]].fn, d) < 0))) { depth[i] = z; who[i] = pi; }
        }
      }
    });
    for (let i = 0; i < W * H; i++) {
      const pi = who[i];
      if (pi < 0 || dot(polys[pi].fn, d) < 0) continue;
      holes++;
      const iu = i % W, iv = (i - iu) / W, x = u0 + (iu + 0.5) * RES, y = v0 + (iv + 0.5) * RES, z = depth[i];
      const p = [0, 1, 2].map(k => U[k] * x + V[k] * y + d[k] * z);
      const f = seenThrough(p, d);
      via[f] = (via[f] || 0) + 1;
      tex[polys[pi].t] = (tex[polys[pi].t] || 0) + 1;
      if (PTS.includes(f)) { const g = f + ' ' + p.map(c => (Math.round(c * 20) / 20).toFixed(2)).join(',') + ' ' + polys[pi].t + ' n ' + polys[pi].fn.map(c => c.toFixed(1)).join(','); where[g] = (where[g] || 0) + 1; }
    }
  }
  return {holes, via, tex, where};
}

const cm2 = n => Math.round(n * RES * RES * 1e4 / DIRS.length);   // mean over the directions
const rows = [], all = pieces();
const list = filter.includes('+') ? [filter.split('+').reduce((asm, spec) => {
  const [nm, at = '0'] = spec.split('@'), parts = at.split(',').map(Number), r = parts.length === 1 ? parts[0] : 0, off = parts.length === 3 ? parts : [0, 0, 0];
  const pc = all.find(q => q.name === nm);
  if (!pc) throw new Error('no piece ' + nm);
  return asm.merge(turned(pc, r), q => [q[0] + off[0], q[1] + off[1], q[2] + off[2]]);
}, new Piece(filter))] : all;
for (const pc of list) {
  if (filter && !filter.includes('+') && !pc.name.includes(filter)) continue;
  const r = auditPiece(pc);
  if (!r.holes) continue;
  const via = Object.entries(r.via).sort((a, b) => b[1] - a[1]).map(([f, n]) => `${f} ${cm2(n)}`).join(', ');
  const tex = Object.entries(r.tex).sort((a, b) => b[1] - a[1]).slice(0, 3).map(([t, n]) => `${t} ${cm2(n)}`).join(', ');
  const pts = Object.entries(r.where).sort((a, b) => b[1] - a[1]).slice(0, 30).map(([g, n]) => `
    ${g}  (${n} px)`).join('');
  rows.push([cm2(r.holes), `${pc.name.padEnd(42)} ${String(cm2(r.holes)).padStart(5)} cm2   via: ${via}   back faces: ${tex}${pts}`]);
}
rows.sort((a, b) => b[0] - a[0]);
for (const [, line] of rows) console.log(line);
console.log(`${rows.length} pieces with holes (mean hole area over ${DIRS.length} directions; 'out' = seen outside the cell's cube, 'in' = no cube face crossed)`);
