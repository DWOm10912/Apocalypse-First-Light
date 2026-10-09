// Pavement Markings V1 (docs/models/pavement_markings_v1.md): parking-lot and road paint at the Ground Materials density
// (user 2026-10-08: the concept approved, the accessibility symbol at one cell). Paint is a sheet in the cell above the
// ground, like the highway's road markings.
//   edge_lane_white / edge_lane_yellow / white_lane_divider (redo) and edge_lane_blue (new): a tileable paint field
//     (240 texels per block, LabPBR); their box models (and the highway ribbon parents) get world-scale UVs
//   pavement_hatch / pavement_crosshatch / pavement_bar / pavement_arrow / pavement_accessible_symbol: meshes (forge:obj).
//     The first version cut these shapes out of textures; their 45-degree and curved edges stepped every texel (user:
//     "锯齿感有点重"), so the shapes are now polygons, their edges geometry, textured with the paint field. The block
//     tints the hatch, cross-hatch and bar (white / yellow / blue).
//     Hatch stripes are centred on the cell diagonals (10 cm wide, 0.71 m apart), both directions alike; the small corner
//     triangles are the neighbouring cells' stripes passing the corner (user asked why the first version's two diagonals
//     differed: the stripe width sat on one side of the line).
//   pavement_arrow: straight 1 x 3 cells (parts 0 head .. 2 tail), turn 2 x 3 (part = row * 2 + col of the left turn; the
//     right turn is its mirror image); item icons
//   pavement_accessible_symbol: the International Symbol of Accessibility, 0.9 m blue square, white figure above it
//   node tools/build-pavement-markings-v1.mjs           -> writes textures, meshes, models, blockstates, item models
//   node tools/build-pavement-markings-v1.mjs --check   -> verifies every output is up to date
// Paint: white [236,235,230], yellow [228,180,60], blue [46,98,172]; whole, fresh paint (user 2026-10-08: no worn holes in
// V1; wear comes later with a world-wide wear / damage system; paint() keeps a worn option for that), no per-texel noise.
// _s: R smoothness 105, G F0 14, B porosity 12, A 255. _n flat (B 255, A 255).
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {png} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const ASSETS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const N = 240, SS = 3, MM = 1000 / N;
export const WHITE = [236, 235, 230], YELLOW = [228, 180, 60], BLUE = [46, 98, 172];
const hash = (a, b, c) => { let h = Math.imul(a | 0, 374761393) ^ Math.imul(b | 0, 668265263) ^ Math.imul(c | 0, 2147483647); h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };
const smooth = t => t * t * (3 - 2 * t), clamp = (v, a, b) => Math.max(a, Math.min(b, v));
/** Value noise 0..1, cells of `cell` texels; periodic over `period` texels when given (a tile), else free. */
function vn(x, y, seed, cell, period = 0) {
  const n = period ? period / cell : 0, xi = Math.floor(x / cell), yi = Math.floor(y / cell);
  const w = i => n ? ((i % n) + n) % n : i, h = (i, j) => hash(w(i), w(j), seed);
  const tx = smooth(x / cell - xi), ty = smooth(y / cell - yi);
  const a = h(xi, yi) + (h(xi + 1, yi) - h(xi, yi)) * tx, b = h(xi, yi + 1) + (h(xi + 1, yi + 1) - h(xi, yi + 1)) * tx;
  return a + (b - a) * ty;
}

/**
 * A tileable paint field (one cell, periodic): the texture of the line boxes and of every paint mesh. worn: the first
 * version's wear (holes and thin paint in large blotches), off since the user's 2026-10-08 call.
 */
function paintField(colour, seed, worn = false) {
  const a = Buffer.alloc(N * N * 4), s = Buffer.alloc(N * N * 4), n = Buffer.alloc(N * N * 4);
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const o = (y * N + x) * 4, wear = worn ? 0.7 * vn(x, y, seed, 16, N) + 0.3 * vn(x, y, seed + 1, 8, N) : 0;
    const t = (worn ? 0.97 : 0.988) + (worn ? 0.06 : 0.024) * vn(x, y, seed + 2, 30, N), thin = wear > 0.78;
    for (let j = 0; j < 3; j++) a[o + j] = clamp(Math.round((thin ? colour[j] * 0.68 + 120 * 0.32 : colour[j]) * t), 0, 255);
    a[o + 3] = wear > 0.86 ? 0 : 255;
    s[o] = thin ? 80 : 105; s[o + 1] = 14; s[o + 2] = 12; s[o + 3] = 255;
    n[o] = 128; n[o + 1] = 128; n[o + 2] = 255; n[o + 3] = 255;
  }
  return [png(a, N, N), png(s, N, N), png(n, N, N)];
}

// ---- geometry in cell units (x east, z south; symbols drawn pointing north, -z) ----
const cross2 = (a, b, c) => (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
const area2 = P => { let s = 0; for (let i = 0; i < P.length; i++) { const a = P[i], b = P[(i + 1) % P.length]; s += a[0] * b[1] - b[0] * a[1]; } return s; };
/** Ear clipping of a simple polygon -> triangles. */
function triangulate(poly) {
  const P = area2(poly) > 0 ? poly.slice() : poly.slice().reverse(), V = P.map((_, i) => i), tris = [];
  // a vertex inside the ear or on its edges blocks it (collinear runs, like an arrow head's base, otherwise cut outside)
  const inside = (p, a, b, c) => cross2(a, b, p) >= -1e-12 && cross2(b, c, p) >= -1e-12 && cross2(c, a, p) >= -1e-12;
  while (V.length > 3) {
    let cut = false;
    for (let i = 0; i < V.length && !cut; i++) {
      const a = P[V[(i + V.length - 1) % V.length]], b = P[V[i]], c = P[V[(i + 1) % V.length]];
      if (Math.abs(cross2(a, b, c)) <= 1e-12) { V.splice(i, 1); cut = true; continue; }   // collinear: drop the vertex
      if (cross2(a, b, c) < 0) continue;
      if (V.some(j => P[j] !== a && P[j] !== b && P[j] !== c && inside(P[j], a, b, c))) continue;
      tris.push([a, b, c]); V.splice(i, 1); cut = true;
    }
    if (!cut) throw Error('triangulate: no ear');
  }
  if (V.length === 3 && Math.abs(cross2(...V.map(i => P[i]))) > 1e-12) tris.push(V.map(i => P[i]));
  return tris;
}
const lerpAt = (a, b, k, v) => { const t = (v - a[k]) / (b[k] - a[k]); return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t]; };
/** A convex polygon clipped to the rectangle [x0, x1] x [z0, z1] (Sutherland-Hodgman). */
function clip(poly, x0, z0, x1, z1) {
  let out = poly;
  for (const [k, v, keep] of [[0, x0, 1], [0, x1, -1], [1, z0, 1], [1, z1, -1]]) {
    const inp = out; out = [];
    for (let i = 0; i < inp.length; i++) {
      const a = inp[i], b = inp[(i + 1) % inp.length], ia = (a[k] - v) * keep >= 0, ib = (b[k] - v) * keep >= 0;
      if (ia) out.push(a);
      if (ia !== ib) out.push(lerpAt(a, b, k, v));
    }
    if (!out.length) break;
  }
  return out;
}
const rect = (x0, z0, x1, z1) => [[x0, z0], [x1, z0], [x1, z1], [x0, z1]];
const circle = (cx, cz, r, n = 24) => Array.from({length: n}, (_, i) => [cx + r * Math.cos(2 * Math.PI * i / n), cz + r * Math.sin(2 * Math.PI * i / n)]);
/** A stroke from a to b, width w (a rectangle; joints and ends are rounded with circles by the caller). */
const segment = (a, b, w) => { const dx = b[0] - a[0], dz = b[1] - a[1], l = Math.hypot(dx, dz), nx = -dz / l * w / 2, nz = dx / l * w / 2;
  return [[a[0] + nx, a[1] + nz], [b[0] + nx, b[1] + nz], [b[0] - nx, b[1] - nz], [a[0] - nx, a[1] - nz]]; };
/** A band of half width hw along the line p0 + t * d, long enough to cross any cell of a piece. */
const band = (p0, d, hw) => { const n = [-d[1] * hw, d[0] * hw], A = [p0[0] - 4 * d[0], p0[1] - 4 * d[1]], B = [p0[0] + 4 * d[0], p0[1] + 4 * d[1]];
  return [[A[0] + n[0], A[1] + n[1]], [B[0] + n[0], B[1] + n[1]], [B[0] - n[0], B[1] - n[1]], [A[0] - n[0], A[1] - n[1]]]; };
const R2 = Math.SQRT1_2, HW = 0.05;   // stripe half width 5 cm
const DIAG = k => band([k, 0], [R2, R2], HW), ANTI = k => band([k, 0], [R2, -R2], HW);   // x - z = k, x + z = k
function quadCurve(out, [x0, y0], [cx, cy], [x1, y1]) { for (let i = 1; i <= 10; i++) { const t = i / 10, u = 1 - t; out.push([u * u * x0 + 2 * u * t * cx + t * t * x1, u * u * y0 + 2 * u * t * cy + t * t * y1]); } }
const ARROW_STRAIGHT = [[0.5, 0.1], [0.95, 1.05], [0.62, 1.05], [0.62, 2.9], [0.38, 2.9], [0.38, 1.05], [0.05, 1.05]];
const ARROW_LEFT = (() => {   // 2 x 3: the shaft rises in the right column and turns left, the head in the left column
  const p = [[1.62, 2.9], [1.62, 1.35]]; quadCurve(p, [1.62, 1.35], [1.62, 0.92], [1.2, 0.92]);
  p.push([0.95, 0.92], [0.95, 0.5], [0.08, 1.04], [0.95, 1.58], [0.95, 1.16], [1.2, 1.16]); quadCurve(p, [1.2, 1.16], [1.38, 1.16], [1.38, 1.35]);
  p.push([1.38, 2.9]); return p;
})();
/** The International Symbol of Accessibility: [blue polygons, white polygons]. */
function isaShapes() {
  const white = [circle(0.47, 0.2, 0.065)], w = 0.075, body = [[0.45, 0.32], [0.43, 0.56], [0.64, 0.56], [0.72, 0.78], [0.8, 0.75]];
  for (let i = 1; i < body.length; i++) white.push(segment(body[i - 1], body[i], w));
  for (const p of body) white.push(circle(p[0], p[1], w / 2, 12));
  white.push(segment([0.45, 0.42], [0.6, 0.42], w), circle(0.45, 0.42, w / 2, 12), circle(0.6, 0.42, w / 2, 12));
  const a0 = -0.35 * Math.PI, a1 = 0.95 * Math.PI, steps = 28, r0 = 0.17, r1 = 0.23;
  for (let i = 0; i < steps; i++) {   // the wheel: an arc band in quads, rounded ends
    const u = a0 + (a1 - a0) * i / steps, v = a0 + (a1 - a0) * (i + 1) / steps, P = (a, r) => [0.42 + r * Math.cos(a), 0.62 + r * Math.sin(a)];
    white.push([P(u, r0), P(u, r1), P(v, r1), P(v, r0)]);
  }
  for (const a of [a0, a1]) white.push(circle(0.42 + 0.2 * Math.cos(a), 0.62 + 0.2 * Math.sin(a), 0.03, 12));
  return [[rect(0.05, 0.05, 0.95, 0.95)], white];
}
/** Every piece: name -> {w, h (cells), layers: [{mat, lift (px above the ground), polys}]} in piece space. */
export function pieces() {
  const [blue, white] = isaShapes();
  return {
    // the main stripes; the corner triangles (a neighbour's stripe passing a corner) are their own meshes, drawn only where
    // a neighbouring hatch continues the stripe (PavementHatchBlock)
    pavement_hatch: {w: 1, h: 1, layers: [{mat: 'tinted', lift: 0.1, polys: [DIAG(0)]}]},
    pavement_crosshatch: {w: 1, h: 1, layers: [{mat: 'tinted', lift: 0.1, polys: [DIAG(0), ANTI(1)]}]},
    pavement_hatch_corner_ne: {w: 1, h: 1, layers: [{mat: 'tinted', lift: 0.1, polys: [DIAG(1)]}]},
    pavement_hatch_corner_sw: {w: 1, h: 1, layers: [{mat: 'tinted', lift: 0.1, polys: [DIAG(-1)]}]},
    pavement_hatch_corner_nw: {w: 1, h: 1, layers: [{mat: 'tinted', lift: 0.1, polys: [ANTI(0)]}]},
    pavement_hatch_corner_se: {w: 1, h: 1, layers: [{mat: 'tinted', lift: 0.1, polys: [ANTI(2)]}]},
    pavement_bar: {w: 1, h: 1, layers: [{mat: 'tinted', lift: 0.1, polys: [rect(0, 0.25, 1, 0.75)]}]},
    pavement_arrow_straight: {w: 1, h: 3, layers: [{mat: 'white', lift: 0.1, polys: [ARROW_STRAIGHT]}]},
    pavement_arrow_turn: {w: 2, h: 3, layers: [{mat: 'white', lift: 0.1, polys: [ARROW_LEFT]}]},
    pavement_accessible_symbol: {w: 1, h: 1, layers: [{mat: 'blue', lift: 0.1, polys: blue}, {mat: 'white', lift: 0.12, polys: white}]},
  };
}
/** The triangles of a piece's cell (col, row) in cell space; mirror flips the piece left-right. */
function cellTriangles(piece, col, row, mirror = false) {
  return piece.layers.map(layer => {
    const tris = [];
    for (let poly of layer.polys) {
      if (mirror) poly = poly.map(([x, z]) => [piece.w - x, z]);
      for (const t of triangulate(poly)) {
        const c = clip(t, col, row, col + 1, row + 1);
        if (c.length < 3 || Math.abs(area2(c)) < 1e-9) continue;
        for (let i = 1; i < c.length - 1; i++) tris.push([c[0], c[i], c[i + 1]].map(([x, z]) => [x - col, z - row]));
      }
    }
    return {...layer, tris};
  });
}
const MATS = {
  tinted: {texture: 'edge_lane_white', tint: true}, white: {texture: 'edge_lane_white', tint: false}, blue: {texture: 'edge_lane_blue', tint: false},
};
const r6 = v => Math.round(v * 1e6) / 1e6;
/** OBJ + MTL text for one cell: up-facing triangles (counter-clockwise seen from above), UV = the cell position. */
function objFiles(name, layers) {
  const obj = [`# AFL Pavement Markings V1 ${name}, generated by tools/build-pavement-markings-v1.mjs`, `mtllib ${name}.mtl`, `o ${name}`, 'vn 0 1 0'];
  const used = new Set();
  let v = 1;
  for (const layer of layers) {
    if (!layer.tris.length) continue;
    used.add(layer.mat); obj.push(`usemtl ${layer.mat}`);
    const y = r6(layer.lift / 16);
    for (let t of layer.tris) {
      if (area2(t) > 0) t = [t[0], t[2], t[1]];   // (x, z) clockwise = +y normal with x east, z south
      for (const [x, z] of t) obj.push(`v ${r6(x)} ${y} ${r6(z)}`, `vt ${r6(x)} ${r6(1 - z)}`);
      obj.push(`f ${v}/${v}/1 ${v + 1}/${v + 1}/1 ${v + 2}/${v + 2}/1`); v += 3;
    }
  }
  const mtl = [`# AFL Pavement Markings V1 ${name}`];
  for (const m of used) { const d = MATS[m]; mtl.push(`newmtl ${m}`, 'Kd 1 1 1', `map_Kd apocalypse_firstlight:block/${d.texture}`, ...(d.tint ? ['forge_TintIndex 0'] : [])); }
  return [obj.join('\n') + '\n', mtl.join('\n') + '\n'];
}
/** An item icon: the piece's triangles rasterised into one square (white or the layer colour). */
function icon(piece, mirror = false) {
  const span = Math.max(piece.w, piece.h), buf = Buffer.alloc(N * N * 4), layers = [];
  for (const layer of piece.layers) { const tris = []; for (let poly of layer.polys) { if (mirror) poly = poly.map(([x, z]) => [piece.w - x, z]); tris.push(...triangulate(poly)); }
    layers.push({colour: layer.mat === 'blue' ? BLUE : WHITE, tris}); }
  const inTri = (p, [a, b, c]) => { const s1 = cross2(a, b, p), s2 = cross2(b, c, p), s3 = cross2(c, a, p); return (s1 >= 0 && s2 >= 0 && s3 >= 0) || (s1 <= 0 && s2 <= 0 && s3 <= 0); };
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const acc = [0, 0, 0]; let hits = 0;
    for (let sy = 0; sy < SS; sy++) for (let sx = 0; sx < SS; sx++) {
      const p = [(x + (sx + 0.5) / SS) / N * span - (span - piece.w) / 2, (y + (sy + 0.5) / SS) / N * span - (span - piece.h) / 2];
      if (p[0] < 0 || p[0] > piece.w || p[1] < 0 || p[1] > piece.h) continue;
      let c = null; for (const l of layers) if (l.tris.some(t => inTri(p, t))) c = l.colour;
      if (c) { hits++; for (let j = 0; j < 3; j++) acc[j] += c[j]; }
    }
    const o = (y * N + x) * 4;
    if (hits * 2 > SS * SS) { for (let j = 0; j < 3; j++) buf[o + j] = Math.round(acc[j] / hits); buf[o + 3] = 255; }
  }
  return png(buf, N, N);
}

// ---- models ----
const json = v => JSON.stringify(v, null, 2) + '\n';
const NS = 'apocalypse_firstlight:', B = NS + 'block/';
/** World-scale UVs from an element's geometry (v folded into 0..16). */
function worldUv(face, from, to) {
  let u0, v0, u1, v1;
  if (face === 'up' || face === 'down') [u0, v0, u1, v1] = [from[0], from[2], to[0], to[2]];
  else if (face === 'north' || face === 'south') [u0, v0, u1, v1] = [from[0], 16 - to[1], to[0], 16 - from[1]];
  else [u0, v0, u1, v1] = [from[2], 16 - to[1], to[2], 16 - from[1]];
  const shift = v1 > 16 ? -16 * Math.ceil((v1 - 16) / 16) : v0 < 0 ? 16 * Math.ceil(-v0 / 16) : 0;
  const r = v => Math.round(v * 1000) / 1000;
  return [r(u0), r(clamp(v0 + shift, 0, 16)), r(u1), r(clamp(v1 + shift, 0, 16))];
}
function rescaled(model) {
  const j = structuredClone(model);
  for (const e of j.elements || []) for (const [face, f] of Object.entries(e.faces)) f.uv = worldUv(face, e.from, e.to);
  return j;
}
const objModel = name => ({loader: 'forge:obj', model: `${NS}models/block/marking/${name}.obj`, automatic_culling: false, flip_v: true,
  shade_quads: true, ambientocclusion: false, render_type: 'minecraft:solid', textures: {particle: B + 'edge_lane_white'}});
const FACINGS = {north: 0, east: 90, south: 180, west: 270};

export function outputs() {
  const out = [], tex = (name, maps) => ['', '_s', '_n'].forEach((k, i) => out.push([path.join(ASSETS, 'textures/block', name + k + '.png'), maps[i]]));
  // paint fields: the line boxes sample them anywhere; the meshes use white and blue
  const fields = {edge_lane_white: [WHITE, 1101], white_lane_divider: [WHITE, 1201], edge_lane_yellow: [YELLOW, 1301], edge_lane_blue: [BLUE, 1401]};
  for (const [name, [c, seed]] of Object.entries(fields)) tex(name, paintField(c, seed));
  // the line models: world-scale UVs; the blue line copies the white one
  const MODELS = path.join(ASSETS, 'models/block');
  const lineModel = /^(highway_ribbon_(arm|center|riser)|edge_lane_(white|yellow)(_boundary_corner|_step_connector_(left|right))?|white_lane_divider(_step_connector_(left|right))?)\.json$/;
  for (const f of fs.readdirSync(MODELS).filter(f => lineModel.test(f))) {
    const j = JSON.parse(fs.readFileSync(path.join(MODELS, f), 'utf8'));
    if (j.elements) out.push([path.join(MODELS, f), json(rescaled(j))]);
  }
  for (const part of ['', '_boundary_corner', '_ribbon_arm', '_ribbon_center', '_ribbon_riser']) {
    const j = JSON.parse(fs.readFileSync(path.join(MODELS, 'edge_lane_white' + part + '.json'), 'utf8').split('edge_lane_white').join('edge_lane_blue'));
    out.push([path.join(MODELS, 'edge_lane_blue' + part + '.json'), json(j.elements ? rescaled(j) : j)]);
  }
  out.push([path.join(ASSETS, 'blockstates/edge_lane_blue.json'), fs.readFileSync(path.join(ASSETS, 'blockstates/edge_lane_white.json'), 'utf8').split('edge_lane_white').join('edge_lane_blue')]);
  out.push([path.join(ASSETS, 'models/item/edge_lane_blue.json'), json({parent: B + 'edge_lane_blue'})]);
  // meshes, one per cell
  const P = pieces(), mesh = (name, layers) => { const [o, m] = objFiles(name, layers);
    out.push([path.join(MODELS, 'marking', name + '.obj'), o], [path.join(MODELS, 'marking', name + '.mtl'), m], [path.join(MODELS, 'marking', name + '.json'), json(objModel(name))]); };
  for (const id of ['pavement_hatch', 'pavement_crosshatch', 'pavement_bar', 'pavement_accessible_symbol', ...['ne', 'sw', 'nw', 'se'].map(c => 'pavement_hatch_corner_' + c)])
    mesh(id, cellTriangles(P[id], 0, 0));
  for (let r = 0; r < 3; r++) mesh(`pavement_arrow_straight_${r}`, cellTriangles(P.pavement_arrow_straight, 0, r));
  for (let r = 0; r < 3; r++) for (let c = 0; c < 2; c++) {
    mesh(`pavement_arrow_turn_${r * 2 + c}`, cellTriangles(P.pavement_arrow_turn, c, r));
    mesh(`pavement_arrow_turn_${r * 2 + c}_mirrored`, cellTriangles(P.pavement_arrow_turn, 1 - c, r, true));   // the right turn's part sits in column 1 - c
  }
  // blockstates: facing turns a piece (y), colour is a tint
  const COLOURS = ['white', 'yellow', 'blue'];
  {
    const variants = {};
    for (const [f, y] of Object.entries(FACINGS)) for (const c of COLOURS) variants[`color=${c},facing=${f}`] = {model: B + 'marking/pavement_bar', ...(y ? {y} : {})};
    out.push([path.join(ASSETS, 'blockstates/pavement_bar.json'), json({variants})]);
  }
  for (const [id, corners] of [['pavement_hatch', ['ne', 'sw']], ['pavement_crosshatch', ['ne', 'sw', 'nw', 'se']]]) {
    const multipart = [];
    for (const [f, y] of Object.entries(FACINGS)) {
      multipart.push({when: {facing: f}, apply: {model: B + 'marking/' + id, ...(y ? {y} : {})}});
      for (const c of corners) multipart.push({when: {facing: f, [c]: 'true'}, apply: {model: B + 'marking/pavement_hatch_corner_' + c, ...(y ? {y} : {})}});
    }
    out.push([path.join(ASSETS, 'blockstates', id + '.json'), json({multipart})]);
  }
  const arrowVariants = {};
  for (const kind of ['straight', 'left', 'right']) for (let p = 0; p < 6; p++) for (const [f, y] of Object.entries(FACINGS)) {
    const model = kind === 'straight' ? `marking/pavement_arrow_straight_${Math.min(p, 2)}` : `marking/pavement_arrow_turn_${p}${kind === 'right' ? '_mirrored' : ''}`;
    arrowVariants[`facing=${f},kind=${kind},part=${p}`] = {model: B + model, ...(y ? {y} : {})};
  }
  out.push([path.join(ASSETS, 'blockstates/pavement_arrow.json'), json({variants: arrowVariants})]);
  out.push([path.join(ASSETS, 'blockstates/pavement_accessible_symbol.json'), json({variants: Object.fromEntries(Object.entries(FACINGS).map(([f, y]) => [`facing=${f}`, {model: B + 'marking/pavement_accessible_symbol', ...(y ? {y} : {})}]))})]);
  // item icons (flat, the item tints the paint tiles by colour)
  const icons = {pavement_hatch: [P.pavement_hatch], pavement_crosshatch: [P.pavement_crosshatch], pavement_bar: [P.pavement_bar], pavement_accessible_symbol: [P.pavement_accessible_symbol],
    pavement_arrow_straight: [P.pavement_arrow_straight], pavement_arrow_left: [P.pavement_arrow_turn], pavement_arrow_right: [P.pavement_arrow_turn, true]};
  for (const [id, [piece, mirror]] of Object.entries(icons)) {
    out.push([path.join(ASSETS, `textures/item/${id}.png`), icon(piece, mirror)]);
    out.push([path.join(ASSETS, `models/item/${id}.json`), json({parent: 'minecraft:item/generated', textures: {layer0: NS + `item/${id}`}})]);
  }
  return out;
}

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  const list = outputs();
  if (process.argv.includes('--check')) {
    for (const [file, data] of list) { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; if (!cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data))) throw new Error('stale ' + path.relative(ROOT, file)); }
    console.log('CHECK OK');
  } else {
    for (const [file] of list) fs.mkdirSync(path.dirname(file), {recursive: true});
    for (const [file, data] of list) fs.writeFileSync(file, data);
    console.log('wrote ' + list.length + ' files');
  }
}
