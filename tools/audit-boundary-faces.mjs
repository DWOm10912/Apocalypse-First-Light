// Boundary-face audit: model faces that can z-fight with a full neighbour block (docs/rendering/shader_pbr_tuning_v1.md
// sibling: docs/models/storefront_glazing_v1.md "2026-10-09 窗台滴水边闪"). A face lying exactly on a block-grid plane
// (x, y or z a whole number of blocks) whose back side is a cell this block does not own coincides with that neighbour's
// face pointing the same way when the neighbour is a full block: both draw, and they flicker. 2026-10-09: the storefront
// glazing's drip had its top at y = 0, the floor's top face, along the entry glass (user video).
//   node tools/audit-boundary-faces.mjs   -> every block model a blockstate uses (JSON elements and forge:obj), as turned by
//                                           the blockstate; one line per model and plane with the area at risk (px^2)
// Own cells: the cell the model is drawn in, plus for multi-cell blocks every cell its blockstate places a cell model
// in is not known here, so models reaching outside their cell are reported with "(reaches out)" for a manual look.
// Faces on a plane whose back is the model's own cell are fine (a neighbour's face there points the other way).
// "face" = the back is a cell sharing a face with the own cells (a full block there shows that face: flickers); "diag" =
// the back is an edge / corner neighbour: that neighbour's face on the plane is culled when the cell between is full too
// (e.g. a lip just past the wall face, buried in the floor), so it only flickers when that cell is open. Sorted by "face".
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const A = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const readJson = f => JSON.parse(fs.readFileSync(f, 'utf8'));
const pathOf = id => { const [ns, p] = id.includes(':') ? id.split(':') : ['minecraft', id]; return ns === 'apocalypse_firstlight' ? p : null; };
const EPS = 1e-4;
const DETAIL = process.argv.includes('--detail');

/** faces of a model: {P: [[x,y,z] px], n: [nx,ny,nz]} */
function faces(modelId, seen = new Set()) {
  const p = pathOf(modelId); if (!p || seen.has(p)) return null;
  seen.add(p);
  const file = path.join(A, 'models', p + '.json'); if (!fs.existsSync(file)) return null;
  const m = readJson(file), out = [];
  if (m.loader === 'forge:obj') {
    const op = pathOf(m.model); if (!op) return out;
    const text = fs.readFileSync(path.join(A, op), 'utf8'), v = [];
    for (const l of text.split('\n')) { const a = l.trim().split(/\s+/);
      if (a[0] === 'v') v.push(a.slice(1, 4).map(Number).map(q => q * 16));
      else if (a[0] === 'f') { const P = a.slice(1).map(s => v[Number(s.split('/')[0]) - 1]); out.push({P, n: newell(P)}); } }
    return out;
  }
  if (!m.elements && m.parent) return faces(m.parent, seen);
  for (const e of m.elements || []) {
    if (e.rotation && e.rotation.angle) continue;   // rotated elements are never on a grid plane in a useful way
    const [x0, y0, z0] = e.from, [x1, y1, z1] = e.to;
    const F = {north: [[[x0, y0, z0], [x1, y0, z0], [x1, y1, z0], [x0, y1, z0]], [0, 0, -1]], south: [[[x0, y0, z1], [x1, y0, z1], [x1, y1, z1], [x0, y1, z1]], [0, 0, 1]],
      west: [[[x0, y0, z0], [x0, y0, z1], [x0, y1, z1], [x0, y1, z0]], [-1, 0, 0]], east: [[[x1, y0, z0], [x1, y0, z1], [x1, y1, z1], [x1, y1, z0]], [1, 0, 0]],
      down: [[[x0, y0, z0], [x1, y0, z0], [x1, y0, z1], [x0, y0, z1]], [0, -1, 0]], up: [[[x0, y1, z0], [x1, y1, z0], [x1, y1, z1], [x0, y1, z1]], [0, 1, 0]]};
    for (const k of Object.keys(e.faces || {})) out.push({P: F[k][0], n: F[k][1]});
  }
  return out;
}
function newell(P) { let n = [0, 0, 0]; for (let i = 0; i < P.length; i++) { const a = P[i], b = P[(i + 1) % P.length]; n = [n[0] + (a[1] - b[1]) * (a[2] + b[2]), n[1] + (a[2] - b[2]) * (a[0] + b[0]), n[2] + (a[0] - b[0]) * (a[1] + b[1])]; } const l = Math.hypot(...n) || 1; return n.map(v => v / l); }
// own space in px: per axis the cells the model fills more than OWN_MIN px of (a multi-cell model owns the cells it spans;
// a lip a fraction of a px past the edge, like the glazing drip, does not make the neighbour its own)
const OWN_MIN = 1;
let own = [[0, 16], [0, 16], [0, 16]];
const inOwn = q => q.every((v, k) => v > own[k][0] - EPS && v < own[k][1] + EPS);
const outside = q => q.filter((v, k) => !(v > own[k][0] - EPS && v < own[k][1] + EPS)).length;
function ownOf(P) {
  return [0, 1, 2].map(k => { const lo = Math.min(...P.map(q => q[k])), hi = Math.max(...P.map(q => q[k]));
    let a = 0, b = 16; while (a - 16 + OWN_MIN < hi && lo < a - OWN_MIN) a -= 16; while (b + 16 - OWN_MIN > lo && hi > b + OWN_MIN) b += 16; return [a, b]; });
}
/** area (px^2) of a face lying on a grid plane whose back (the side opposite its normal) is not the own cell */
function risk(f) {
  const k = f.n.findIndex(v => Math.abs(Math.abs(v) - 1) < 1e-6); if (k < 0) return {face: 0, diag: 0};
  const c = f.P[0][k]; if (Math.abs(c / 16 - Math.round(c / 16)) > EPS / 16) return {face: 0, diag: 0};   // not on a grid plane
  if (!f.P.every(q => Math.abs(q[k] - c) < 1e-3)) return {face: 0, diag: 0};
  const [u, w] = [0, 1, 2].filter(i => i !== k);
  // sample the face on a fine grid; a sample is at risk when the point just behind it is outside the own cell
  const us = f.P.map(q => q[u]), ws = f.P.map(q => q[w]), u0 = Math.min(...us), u1 = Math.max(...us), w0 = Math.min(...ws), w1 = Math.max(...ws);
  const S = 24; let bad = 0, diag = 0, all = 0;
  const inside = (pu, pw) => { let c2 = false; const P = f.P; for (let i = 0, j = P.length - 1; i < P.length; j = i++) { const a = P[i], b = P[j]; if ((a[w] > pw) !== (b[w] > pw) && pu < (b[u] - a[u]) * (pw - a[w]) / (b[w] - a[w]) + a[u]) c2 = !c2; } return c2; };
  for (let i = 0; i < S; i++) for (let j = 0; j < S; j++) {
    const pu = u0 + (u1 - u0) * (i + 0.5) / S, pw = w0 + (w1 - w0) * (j + 0.5) / S;
    if (!inside(pu, pw)) continue;
    all++;
    const back = [0, 0, 0]; back[k] = c - f.n[k] * 0.01; back[u] = pu; back[w] = pw;
    const o = outside(back);
    if (o === 1) bad++; else if (o > 1) diag++;
  }
  const cellArea = (u1 - u0) * (w1 - w0) / (S * S);
  return all ? {face: cellArea * bad, diag: cellArea * diag} : {face: 0, diag: 0};
}
const rotY = (q, y) => { let [x, yy, z] = q; for (let i = 0; i < (((y || 0) / 90) % 4 + 4) % 4; i++) [x, z] = [16 - z, x]; return [x, yy, z]; };
const rotX = (q, x) => { let [a, y, z] = q; for (let i = 0; i < (((x || 0) / 90) % 4 + 4) % 4; i++) [y, z] = [16 - z, y]; return [a, y, z]; };

const models = new Map();   // model id -> set of "x,y" rotations used
for (const f of fs.readdirSync(path.join(A, 'blockstates'))) {
  const bs = readJson(path.join(A, 'blockstates', f));
  const applies = bs.variants ? Object.values(bs.variants) : (bs.multipart || []).map(p => p.apply);
  for (const ap of applies) for (const a of [].concat(ap)) {
    if (!a || !a.model) continue;
    const key = (a.x || 0) + ',' + (a.y || 0);
    (models.get(a.model) || models.set(a.model, new Set()).get(a.model)).add(key);
  }
}
const rows = [];
for (const [id, rots] of models) {
  const F = faces(id); if (!F || !F.length) continue;
  const reaches = F.some(f => f.P.some(q => q.some(v => v < -0.5 || v > 16.5)));
  for (const r of rots) {
    const [rx, ry] = r.split(',').map(Number);
    let total = 0, totalDiag = 0;
    const RP = F.map(f => f.P.map(q => rotY(rotX(q, rx), ry)));
    own = ownOf(RP.flat());
    const hits = [];
    for (const f of F) {
      const P = f.P.map(q => rotY(rotX(q, rx), ry)), c = rotY(rotX([8 + f.n[0], 8 + f.n[1], 8 + f.n[2]], rx), ry);
      const a = risk({P, n: [c[0] - 8, c[1] - 8, c[2] - 8]});
      if (a.face + a.diag > 0.01) hits.push(`n[${[c[0] - 8, c[1] - 8, c[2] - 8].map(Math.round)}] @${P[0].map(v => +v.toFixed(2))} face ${a.face.toFixed(2)} diag ${a.diag.toFixed(2)}`);
      total += a.face; totalDiag += a.diag;
    }
    if (DETAIL && total + totalDiag > 0.01) console.log(id.replace("apocalypse_firstlight:block/", ""), r, JSON.stringify(own), JSON.stringify(hits.slice(0, 6)));
    if (total + totalDiag > 0.01) { rows.push([id.replace('apocalypse_firstlight:block/', ''), r, total, reaches, totalDiag]); break; }
  }
}
rows.sort((a, b) => b[2] - a[2] || b[4] - a[4]);
for (const [id, r, t, reach, d] of rows) console.log(id.padEnd(58), 'rot', r.padEnd(7), 'face', t.toFixed(2).padStart(8), 'diag', d.toFixed(2).padStart(7), reach ? '(reaches out)' : '');
console.log('models', models.size, 'flagged', rows.length, 'with face risk', rows.filter(r => r[2] > 0.01).length);
