// Offline check of the selection outlines the cabin blocks get from their hit meshes (docs/rendering/mesh_hit_runtime_v1.md,
// "cabin pieces"; docs/models/cabin_exterior_v1.md "Outline and collision"): assembles typical looks from the cabin_mesh
// pieces exactly as meshhit/CabinMeshHits does (quads split in two, quarter turns (x, z) -> (1 - z, x)), finds the feature
// edges with MeshHitModel's rules (weld 1e-4, T-junctions split, fold > 35 degrees or one face, drop < 0.015), prints the
// counts and draws every look from two sides into an SVG (and a PNG through headless Chrome when --png is given).
//
//   node tools/cabin/audit-hit-outlines.mjs [filter] [--png=out.png] [--no-tfix]
import fs from 'node:fs';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const MESH = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/cabin_mesh');
const FOLD = Math.cos(35 * Math.PI / 180), SHORTEST = 0.015, WELD = 1e4, ON_EDGE = 1e-4;

const cache = new Map();
function piece(name) {
  if (!cache.has(name)) cache.set(name, JSON.parse(fs.readFileSync(path.join(MESH, name + '.json'), 'utf8')).quads);
  return cache.get(name);
}

/** Triangles (9 numbers each) of pieces [[name, turn], ...]. */
function assemble(list) {
  const tri = [];
  for (const [name, turn] of list) {
    for (const q of piece(name)) {
      const p = [0, 1, 2, 3].map(k => {
        let x = q[1 + k * 8], y = q[2 + k * 8], z = q[3 + k * 8];
        for (let t = 0; t < ((turn % 4) + 4) % 4; t++) [x, z] = [1 - z, x];
        return [x, y, z];
      });
      for (const [a, b, c] of [[p[0], p[1], p[2]], [p[0], p[2], p[3]]]) {
        const u = sub(b, a), w = sub(c, a), n = cross(u, w);
        if (dot(n, n) < 1e-14) continue;
        tri.push(...a, ...b, ...c);
      }
    }
  }
  return tri;
}
const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];

/** Feature edges as MeshHitModel#featureEdges (tfix: edges split where another corner lies on them). */
export function edges(tri, tfix = true) {
  const n = tri.length / 9, weld = new Map(), pts = [], corner = new Int32Array(n * 3);
  for (let i = 0; i < n * 3; i++) {
    const x = tri[i * 3], y = tri[i * 3 + 1], z = tri[i * 3 + 2];
    const key = Math.round(x * WELD) + ',' + Math.round(y * WELD) + ',' + Math.round(z * WELD);
    let at = weld.get(key);
    if (at === undefined) { weld.set(key, at = pts.length); pts.push([x, y, z]); }
    corner[i] = at;
  }
  const normal = [];
  for (let t = 0; t < n; t++) {
    const a = tri.slice(t * 9, t * 9 + 3), b = tri.slice(t * 9 + 3, t * 9 + 6), c = tri.slice(t * 9 + 6, t * 9 + 9);
    const nn = cross(sub(b, a), sub(c, a)), l = Math.hypot(...nn);
    normal.push(nn.map(v => v / l));
  }
  // corners in a coarse grid, for the points lying on an edge
  const G = 8, grid = new Map(), cell = p => p.map(v => Math.floor(v * G));
  pts.forEach((p, i) => { const k = cell(p).join(','); if (!grid.has(k)) grid.set(k, []); grid.get(k).push(i); });
  const faces = new Map();
  const add = (a, c, t) => { const key = Math.min(a, c) + ':' + Math.max(a, c); if (!faces.has(key)) faces.set(key, []); faces.get(key).push(t); };
  for (let t = 0; t < n; t++) for (let k = 0; k < 3; k++) {
    const a = corner[t * 3 + k], c = corner[t * 3 + (k + 1) % 3];
    if (a === c) continue;
    if (!tfix) { add(a, c, t); continue; }
    const A = pts[a], C = pts[c], d = sub(C, A), len2 = dot(d, d);
    const lo = cell(A.map((v, i) => Math.min(v, C[i]) - ON_EDGE)), hi = cell(A.map((v, i) => Math.max(v, C[i]) + ON_EDGE));
    const inner = [];
    for (let x = lo[0]; x <= hi[0]; x++) for (let y = lo[1]; y <= hi[1]; y++) for (let z = lo[2]; z <= hi[2]; z++) {
      for (const q of grid.get(x + ',' + y + ',' + z) || []) {
        if (q === a || q === c) continue;
        const w = sub(pts[q], A), s = dot(w, d) / len2;
        if (s <= 1e-6 || s >= 1 - 1e-6) continue;
        const off = sub(w, d.map(v => v * s));
        if (dot(off, off) < ON_EDGE * ON_EDGE) inner.push([s, q]);
      }
    }
    inner.sort((p, q) => p[0] - q[0]);
    let prev = a;
    for (const [, q] of inner) { add(prev, q, t); prev = q; }
    add(prev, c, t);
  }
  const out = [];
  let open = 0, folds = 0;
  for (const [key, by] of faces) {
    let keep = by.length !== 2;
    if (!keep) keep = Math.abs(dot(normal[by[0]], normal[by[1]])) < FOLD; else open++;
    if (!keep) continue;
    const [i, j] = key.split(':').map(Number), A = pts[i], C = pts[j];
    if (dot(sub(C, A), sub(C, A)) < SHORTEST * SHORTEST) continue;
    if (by.length === 2) folds++;
    out.push([A, C]);
  }
  return {lines: out, open, folds, triangles: n};
}

const R = (name, t) => [name, t];
export const LOOKS = {
  'roof green lower, eave + gable': [R('roof/green_lower', 0), R('roof/under_lower', 0), R('roof/green_eave_lower', 0), R('roof/green_rake_neg_lower', 0), R('roof/green_eave_corner_neg_lower', 0)],
  'roof green upper, middle': [R('roof/green_upper', 0), R('roof/under_upper', 0)],
  'roof galvanized lower, eave': [R('roof/galvanized_lower', 0), R('roof/under_lower', 0), R('roof/galvanized_eave_lower', 0)],
  'roof rust upper over log wall': [R('roof/rust_upper', 0), R('roof/under_upper', 0), R('roof/infill_log_along_upper', 0)],
  'roof green ridge': [R('roof/green_ridge', 0), R('roof/under_ridge', 0)],
  'log wall run x': [R('log_wall/run_x', 0)],
  'log wall corner': [R('log_wall/core_x', 0), R('log_wall/core_z', 0), R('log_wall/stubx_neg', 0), R('log_wall/stubz_neg', 0)],
  'cabin door lower closed': [R('door/cabin_frame_lower', 0), R('door/cabin_panel_lower_right_closed', 0)],
  'cabin door upper open': [R('door/cabin_frame_upper', 0), R('door/cabin_panel_upper_right_open', 0)],
  'window single': [R('window/single', 0)],
  'porch deck corner': [R('porch/deck_x', 0), R('porch/deck_rim_x_n', 0), R('porch/deck_rim_x_e', 0), R('porch/deck_corner_x_ne', 0), R('porch/pier_corner', 0)],
  'porch post + rail': [R('porch/post', 0), R('porch/post_rail', 1)],
  'porch railing': [R('porch/railing', 0)],
  'stove pipe cap': [R('pipe/stove_pipe_cap', 0)],
  'board and batten': [R('siding/brown_core', 0), R('siding/brown_battens', 0)],
  'table single': [R('furniture/table_x_0', 0)],
  'chair': [R('furniture/chair', 0)],
  'box stove closed': [R('stove/body', 0), R('stove/door_closed', 0)],
};

/** Orthographic view from yaw / pitch (degrees): screen x, y and depth. */
function view(yaw, pitch) {
  const cy = Math.cos(yaw * Math.PI / 180), sy = Math.sin(yaw * Math.PI / 180), cp = Math.cos(pitch * Math.PI / 180), sp = Math.sin(pitch * Math.PI / 180);
  return ([x, y, z]) => {
    x -= 0.5; y -= 0.5; z -= 0.5;
    const rx = x * cy - z * sy, rz = x * sy + z * cy;
    return [rx, -(y * cp - rz * sp)];
  };
}

function svg(results) {
  const W = 260, H = 260, cols = 4, views = [[35, 28], [215, 28]];
  const rows = Math.ceil(results.length / cols * 2);
  const body = [];
  results.forEach((r, idx) => {
    views.forEach((v, vi) => {
      const slot = idx * 2 + vi, cx = (slot % cols) * W, cy = Math.floor(slot / cols) * H, pr = view(...v);
      body.push(`<rect x="${cx}" y="${cy}" width="${W}" height="${H}" fill="#f4f1ea" stroke="#ccc"/>`);
      // the cell, light
      const box = [[0, 0, 0], [1, 0, 0], [1, 0, 1], [0, 0, 1], [0, 1, 0], [1, 1, 0], [1, 1, 1], [0, 1, 1]], E = [[0, 1], [1, 2], [2, 3], [3, 0], [4, 5], [5, 6], [6, 7], [7, 4], [0, 4], [1, 5], [2, 6], [3, 7]];
      const S = 130, ox = cx + W / 2, oy = cy + H / 2 + 10, P = p => { const q = pr(p); return [ox + q[0] * S, oy + q[1] * S]; };
      for (const [a, b] of E) { const p = P(box[a]), q = P(box[b]); body.push(`<line x1="${p[0].toFixed(1)}" y1="${p[1].toFixed(1)}" x2="${q[0].toFixed(1)}" y2="${q[1].toFixed(1)}" stroke="#d8cfc0" stroke-width="1" stroke-dasharray="3 3"/>`); }
      for (const [a, b] of r.lines) { const p = P(a), q = P(b); body.push(`<line x1="${p[0].toFixed(1)}" y1="${p[1].toFixed(1)}" x2="${q[0].toFixed(1)}" y2="${q[1].toFixed(1)}" stroke="#111" stroke-width="1"/>`); }
      if (vi === 0) body.push(`<text x="${cx + 6}" y="${cy + 16}" font-family="sans-serif" font-size="12">${r.name}</text><text x="${cx + 6}" y="${cy + 32}" font-family="sans-serif" font-size="11" fill="#555">${r.lines.length} lines, ${r.triangles} tris</text>`);
    });
  });
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${cols * W}" height="${rows * H}" viewBox="0 0 ${cols * W} ${rows * H}">${body.join('')}</svg>`;
}

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  const args = process.argv.slice(2), filter = args.find(a => !a.startsWith('--')) || '';
  const png = (args.find(a => a.startsWith('--png=')) || '').slice(6), tfix = !args.includes('--no-tfix');
  const results = [];
  for (const [name, list] of Object.entries(LOOKS)) {
    if (!name.includes(filter)) continue;
    const r = edges(assemble(list), tfix);
    results.push({name, ...r});
    console.log(`${name.padEnd(34)} ${String(r.triangles).padStart(5)} tris  ${String(r.lines.length).padStart(4)} lines (${r.folds} folds, ${r.open} open or shared by 3+)`);
  }
  if (png) {
    const file = path.resolve(png.replace(/\.png$/, '') + '.svg');
    fs.writeFileSync(file, svg(results));
    const html = file.replace(/\.svg$/, '.html');
    fs.writeFileSync(html, `<!doctype html><meta charset="utf-8"><style>body{margin:0;background:#fff}</style><img src="${path.basename(file)}">`);
    const size = fs.readFileSync(file, 'utf8').match(/width="(\d+)" height="(\d+)"/);
    execFileSync('C:/Program Files/Google/Chrome/Application/chrome.exe', ['--headless=new', '--disable-gpu', `--screenshot=${path.resolve(png)}`,
      `--window-size=${size[1]},${size[2]}`, 'file:///' + html.replace(/\\/g, '/')], {stdio: 'ignore'});
    console.log('wrote ' + png);
  }
}
