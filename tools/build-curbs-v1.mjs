// Curbs V1 (docs/models/curbs_v1.md): a 15 x 15 cm cast concrete curb along the edge of a ground block (curb_sidewalk,
// curb_grass), from the plan the user approved on 2026-10-09: the walk stays level with the drive, only the curb stands
// 15 cm proud; the curb is part of the ground block (k -1) and rises into the cell above, which stays free for bollards,
// poles and markings. The block works out its shape from its neighbours (client/CurbModel, block/CurbGeometry); this file
// makes the pieces it assembles, each drawn for the curb on the NORTH edge of the cell (road to the north, x 0 = west):
//   strip_<profile>          the whole edge of a straight cell: full / lowered (2.5 cm) / transitions to lowered or to flush
//                            at the ccw (west) or cw (east) end
//   mid, square_ccw/_cw      a corner cell's edge: the middle and the end squares where the edge carries on
//   outer                    the rounded outer corner (north-east), radius = the curb width
//   post                     an inner corner: the curb square in the cell diagonal to the road (north-east)
//   cap_<ccw|cw>_<full|low>  an end face where the run stops
//   warning                  the detectable warning surface (yellow truncated domes, 0.6 m deep) behind a flush curb
// Every face sits 1 mm inside the cell (vanilla lights a face on the block boundary from the neighbour, here the opaque
// road block) and the top front arris has a 12 mm chamfer. Textures: 240 texels per metre, LabPBR (_s R smoothness, G F0,
// B porosity; _n DirectX normal, B AO, A height). Paint (red / yellow) is tint 1 on the curb material.
//   node tools/build-curbs-v1.mjs           -> writes textures, piece OBJ / MTL / JSON, block / item models, blockstates, Blockbench source
//   node tools/build-curbs-v1.mjs --check   -> verifies every output is up to date
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {png} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const ASSETS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const BB = path.join(ROOT, 'src/main/blockbench');
const NS = 'apocalypse_firstlight:', B = NS + 'block/';
const json = v => JSON.stringify(v, null, 2) + '\n';
const assert = (c, m) => { if (!c) throw new Error(m); };

// ---- dimensions (block units, 1 = 1 m; y 1 = the top of the ground block) ----
export const W = 0.15, H = 0.15, L = 0.025, E = 0.001, C = 0.012, WARN = 0.6, WT = 0.01;

// ---------------- textures ----------------
const N = 240, SS = 3, MM = 1000 / N;
const hash = (a, b, c) => { let h = Math.imul(a | 0, 374761393) ^ Math.imul(b | 0, 668265263) ^ Math.imul(c | 0, 2147483647); h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };
const smooth = t => t * t * (3 - 2 * t), clamp = (v, a, b) => Math.max(a, Math.min(b, v));
function vn(x, y, seed, cx, cy = cx) {   // periodic value noise, period N, -1..1
  const nx = N / cx, ny = N / cy, xi = Math.floor(x / cx), yi = Math.floor(y / cy);
  const h = (i, j) => hash(((i % nx) + nx) % nx, ((j % ny) + ny) % ny, seed);
  const tx = smooth(x / cx - xi), ty = smooth(y / cy - yi);
  const a = h(xi, yi) + (h(xi + 1, yi) - h(xi, yi)) * tx, b = h(xi, yi + 1) + (h(xi + 1, yi + 1) - h(xi, yi + 1)) * tx;
  return 2 * (a + (b - a) * ty) - 1;
}
const mul = (c, t) => c.map(v => v * t);
/** Cast, steel-trowelled curb concrete, a little lighter and smoother than the broom-finished walk. Tint 1 (CurbBlock.Paint)
 * multiplies it: bare 0xEAEAEA gives about [183,180,174], so the texture itself is lighter. */
function curbConcrete() {
  const base = [200, 197, 190];
  const pits = []; for (let i = 0; i < 9; i++) pits.push([hash(i, 1, 91) * N, hash(i, 2, 91) * N, 0.6 + 0.6 * hash(i, 3, 91)]);
  return (x, y) => {
    const t = 1 + 0.022 * vn(x, y, 71, 48) + 0.009 * vn(x, y, 72, 16) + 0.004 * vn(x, y, 73, 6);
    let h = 0.25 * vn(x, y, 74, 4) + 0.15 * vn(x, y, 75, 2), c = mul(base, t), s = 78 + 10 * vn(x, y, 76, 30), ao = 1;
    for (const [px, py, r] of pits) { for (const dx of [-N, 0, N]) for (const dy of [-N, 0, N]) { const d = Math.hypot(x - px - dx, y - py - dy); if (d < r) { h = -1.2 * (1 - d / r); c = mul(c, 0.93); ao = 0.85; s = 55; } } }
    return {c, s, p: 40, h, ao};
  };
}
/** Detectable warning surface: federal yellow polymer panel, truncated domes on a 58.8 mm grid (17 per metre), base 23 mm,
 * top 12 mm, 5 mm high. The panel piece maps z W..W+0.6 to the texture rows 36..180. */
function warningDomes() {
  const field = [234, 186, 48], P = N / 17, rb = 11.5 / MM, rt = 6 / MM;
  return (x, y) => {
    const cx = (Math.floor(x / P) + 0.5) * P, cy = (Math.floor(y / P) + 0.5) * P, d = Math.hypot(x - cx, y - cy);
    const t = 1 + 0.012 * vn(x, y, 81, 40);
    if (d <= rt) return {c: mul(field, t * 1.04), s: 130, p: 0, h: 5, ao: 1};
    if (d <= rb) { const k = (d - rt) / (rb - rt); return {c: mul(field, t * (1.04 - 0.06 * k)), s: 125, p: 0, h: 5 * (1 - k), ao: 1 - 0.12 * k}; }
    return {c: mul(field, t * 0.97), s: 118, p: 0, h: 0, ao: 0.9 + 0.1 * clamp((d - rb) / 3, 0, 1)};
  };
}
function render(f) {
  const col = Buffer.alloc(N * N * 4), spec = Buffer.alloc(N * N * 4), nrm = Buffer.alloc(N * N * 4), Hh = new Float64Array(N * N);
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    let c = [0, 0, 0], s = 0, p = 0, h = 0, ao = 0;
    for (let sy = 0; sy < SS; sy++) for (let sx = 0; sx < SS; sx++) { const r = f(x + (sx + 0.5) / SS, y + (sy + 0.5) / SS); c = c.map((v, i) => v + r.c[i]); s += r.s; p += r.p; h += r.h; ao += r.ao; }
    const k = SS * SS, o = (y * N + x) * 4;
    c.forEach((v, i) => { col[o + i] = clamp(Math.round(v / k), 0, 255); }); col[o + 3] = 255;
    spec[o] = clamp(Math.round(s / k), 0, 255); spec[o + 1] = 14; spec[o + 2] = clamp(Math.round(p / k), 0, 64); spec[o + 3] = 255;
    Hh[y * N + x] = h / k; nrm[o + 2] = clamp(Math.round(255 * ao / k), 0, 255);
  }
  const at = (i, j) => Hh[(((j % N) + N) % N) * N + (((i % N) + N) % N)];
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const hx = (at(x + 1, y) - at(x - 1, y)) / (2 * MM), hy = (at(x, y + 1) - at(x, y - 1)) / (2 * MM);
    let nx = -hx, ny = -hy; const l = Math.hypot(nx, ny, 1); nx /= l; ny /= l;
    const o = (y * N + x) * 4;
    nrm[o] = Math.round((nx * 0.5 + 0.5) * 255); nrm[o + 1] = Math.round((ny * 0.5 + 0.5) * 255); nrm[o + 3] = clamp(Math.round(255 * (1 + Hh[y * N + x] / 250)), 0, 255);
  }
  return [png(col, N, N), png(spec, N, N), png(nrm, N, N)];
}

// ---------------- pieces ----------------
const sub = (a, b) => a.map((v, i) => v - b[i]), dot = (a, b) => a.reduce((s, v, i) => s + v * b[i], 0);
const newell = P => { let n = [0, 0, 0]; for (let i = 0; i < P.length; i++) { const a = P[i], b = P[(i + 1) % P.length]; n = [n[0] + (a[1] - b[1]) * (a[2] + b[2]), n[1] + (a[2] - b[2]) * (a[0] + b[0]), n[2] + (a[0] - b[0]) * (a[1] + b[1])]; } return n; };
const unit = v => { const l = Math.hypot(...v) || 1; return v.map(x => x / l); };
class Piece {
  constructor(name) { this.name = name; this.faces = []; }
  /** A planar polygon facing `out` (reversed when its winding disagrees); degenerate ones are dropped. UV by facing. */
  face(pts, out, mat = 'curb') {
    const q = []; for (const p of pts) if (!q.length || Math.hypot(...sub(p, q[q.length - 1])) > 1e-7) q.push(p);
    while (q.length > 2 && Math.hypot(...sub(q[0], q[q.length - 1])) < 1e-7) q.pop();
    if (q.length < 3) return;
    let n = newell(q); if (Math.hypot(...n) < 1e-10) return;
    if (dot(n, out) < 0) { q.reverse(); n = n.map(v => -v); }
    this.faces.push({pts: q, n: unit(n), mat});
  }
}
const uvOf = (p, n, mat) => {
  if (mat === 'warning') return [p[0], (p[2] - W) / WARN * 0.6 + 0.15];   // rows 36..180 of the dome texture
  if (Math.abs(n[1]) > 0.6) return [p[0], p[2]];                         // tops and chamfers: plan position
  return Math.abs(n[0]) > Math.abs(n[2]) ? [p[2], 0.5 + 1 + H - p[1]] : [p[0], 0.5 + 1 + H - p[1]];
};
/** The strip profile (z, y) at a height h, front (z E) to back (z W): front foot, front top, chamfer top, back top, back foot. */
function profile(h) { const c = Math.min(C, h); return [[E, 1], [E, 1 + h - c], [E + c, 1 + h], [W, 1 + h], [W, 1]]; }
/** Extrude the profile along x from x0 to x1, the height h(x) sampled at n segments; front (-z), chamfer, top, back (+z). */
function strip(pc, x0, x1, h, n = 1) {
  const OUT = [[0, 0, -1], [0, 1, -1], [0, 1, 0], [0, 0, 1]];
  for (let i = 0; i < n; i++) {
    const a = x0 + (x1 - x0) * i / n, b = x0 + (x1 - x0) * (i + 1) / n, pa = profile(h(a)), pb = profile(h(b));
    for (let k = 0; k < 4; k++) pc.face([[a, pa[k][1], pa[k][0]], [b, pb[k][1], pb[k][0]], [b, pb[k + 1][1], pb[k + 1][0]], [a, pa[k + 1][1], pa[k + 1][0]]], OUT[k]);
  }
}
export function pieces() {
  const P = [], piece = name => { const p = new Piece(name); P.push(p); return p; };
  const lerp = (h0, h1) => x => h0 + (h1 - h0) * x;
  strip(piece('strip_full'), 0, 1, () => H);
  strip(piece('strip_lowered'), 0, 1, () => L);
  strip(piece('strip_low_ccw'), 0, 1, lerp(L, H), 8);    // the low end at x 0 (west, counter-clockwise from north)
  strip(piece('strip_low_cw'), 0, 1, lerp(H, L), 8);
  strip(piece('strip_zero_ccw'), 0, 1, lerp(0, H), 12);
  strip(piece('strip_zero_cw'), 0, 1, lerp(H, 0), 12);
  strip(piece('mid'), W, 1 - W, () => H);
  strip(piece('square_ccw'), 0, W, () => H);
  strip(piece('square_cw'), 1 - W, 1, () => H);
  // outer corner (north-east): the profile turned round the axis x 1 - W, z W, from facing north to facing east
  { const pc = piece('outer'), cx = 1 - W, cz = W, ro = W - E, S = 8, ring = (r, y, t) => [cx + r * Math.cos(t), y, cz + r * Math.sin(t)];
    const rad = [[ro, 1], [ro, 1 + H - C], [ro - C, 1 + H]];
    for (let i = 0; i < S; i++) {
      const t0 = -Math.PI / 2 + Math.PI / 2 * i / S, t1 = -Math.PI / 2 + Math.PI / 2 * (i + 1) / S, tm = (t0 + t1) / 2, o = [Math.cos(tm), 0, Math.sin(tm)];
      pc.face([ring(rad[0][0], rad[0][1], t0), ring(rad[0][0], rad[0][1], t1), ring(rad[1][0], rad[1][1], t1), ring(rad[1][0], rad[1][1], t0)], o);
      pc.face([ring(rad[1][0], rad[1][1], t0), ring(rad[1][0], rad[1][1], t1), ring(rad[2][0], rad[2][1], t1), ring(rad[2][0], rad[2][1], t0)], [o[0], 1, o[2]]);
      pc.face([[cx, 1 + H, cz], ring(rad[2][0], 1 + H, t0), ring(rad[2][0], 1 + H, t1)], [0, 1, 0]);
    } }
  // inner corner post (north-east square): top and the two sides toward the cell
  { const pc = piece('post'), x0 = 1 - W, z1 = W, y1 = 1 + H;
    pc.face([[x0, y1, 0], [1, y1, 0], [1, y1, z1], [x0, y1, z1]], [0, 1, 0]);
    pc.face([[x0, 1, 0], [x0, 1, z1], [x0, y1, z1], [x0, y1, 0]], [-1, 0, 0]);
    pc.face([[x0, 1, z1], [1, 1, z1], [1, y1, z1], [x0, y1, z1]], [0, 0, 1]); }
  // end caps: the profile at x E (facing west) or 1 - E (facing east)
  for (const [end, x, out] of [['ccw', E, [-1, 0, 0]], ['cw', 1 - E, [1, 0, 0]]]) for (const [k, h] of [['full', H], ['low', L]])
    piece(`cap_${end}_${k}`).face(profile(h).map(([z, y]) => [x, y, z]), out);
  // the ground cube for the hit mesh only (CurbBlock#meshHitPieces: crosshair, outline, bullets); never drawn, the ground
  // block's own model draws it
  { const pc = piece('hit_base'), c = (x, y, z) => [x, y, z];
    pc.face([c(0, 1, 0), c(1, 1, 0), c(1, 1, 1), c(0, 1, 1)], [0, 1, 0]); pc.face([c(0, 0, 0), c(1, 0, 0), c(1, 0, 1), c(0, 0, 1)], [0, -1, 0]);
    pc.face([c(0, 0, 0), c(1, 0, 0), c(1, 1, 0), c(0, 1, 0)], [0, 0, -1]); pc.face([c(0, 0, 1), c(1, 0, 1), c(1, 1, 1), c(0, 1, 1)], [0, 0, 1]);
    pc.face([c(0, 0, 0), c(0, 0, 1), c(0, 1, 1), c(0, 1, 0)], [-1, 0, 0]); pc.face([c(1, 0, 0), c(1, 0, 1), c(1, 1, 1), c(1, 1, 0)], [1, 0, 0]); }
  // detectable warning panel behind a flush curb: 1 cm proud, its front and back edges
  { const pc = piece('warning'), z0 = W, z1 = W + WARN, y1 = 1 + WT;
    pc.face([[0, y1, z0], [1, y1, z0], [1, y1, z1], [0, y1, z1]], [0, 1, 0], 'warning');
    pc.face([[0, 1, z0], [1, 1, z0], [1, y1, z0], [0, y1, z0]], [0, 0, -1], 'warning');
    pc.face([[0, 1, z1], [1, 1, z1], [1, y1, z1], [0, y1, z1]], [0, 0, 1], 'warning'); }
  return P;
}

// ---------------- OBJ / models ----------------
const r6 = v => Math.round(v * 1e6) / 1e6 || 0;
const MATS = {curb: {texture: 'curb/curb_concrete', tint: 1}, warning: {texture: 'curb/curb_warning', tint: -1}};
function objOf(pc) {
  const lines = [`# AFL Curbs V1 ${pc.name}, generated by tools/build-curbs-v1.mjs`, `mtllib ${pc.name}.mtl`, `o ${pc.name}`];
  let v = 1, vn = 1; const used = [];
  for (const mat of Object.keys(MATS)) {
    const fs_ = pc.faces.filter(f => f.mat === mat); if (!fs_.length) continue; used.push(mat); lines.push(`usemtl ${mat}`);
    for (const f of fs_) {
      lines.push(`vn ${r6(f.n[0])} ${r6(f.n[1])} ${r6(f.n[2])}`);
      // forge:obj takes triangles and quads; larger polygons become a fan
      const tri = f.pts.length <= 4 ? [f.pts] : f.pts.slice(1, -1).map((p, i) => [f.pts[0], p, f.pts[i + 2]]);
      for (const poly of tri) {
        const ids = [];
        for (const p of poly) { const [u, w] = uvOf(p, f.n, mat); lines.push(`v ${r6(p[0])} ${r6(p[1])} ${r6(p[2])}`, `vt ${r6(u)} ${r6(1 - w)}`); ids.push(v++); }
        lines.push('f ' + ids.map(i => `${i}/${i}/${vn}`).join(' '));
      }
      vn++;
    }
  }
  const mtl = [`# AFL Curbs V1 ${pc.name}`];
  for (const m of used) mtl.push(`newmtl ${m}`, 'Kd 1 1 1', `map_Kd ${B}${MATS[m].texture}`, ...(MATS[m].tint >= 0 ? [`forge_TintIndex ${MATS[m].tint}`] : []));
  return [lines.join('\n') + '\n', mtl.join('\n') + '\n'];
}
const pieceModel = name => ({loader: 'forge:obj', model: `${NS}models/block/curb/${name}.obj`, automatic_culling: false, flip_v: true,
  shade_quads: true, ambientocclusion: false, textures: {particle: B + 'curb/curb_concrete'}});

/** GUI view: the ground cube with a full curb on its north edge, fitted to about 15.5 px of the 16 px slot. */
function guiDisplay() {
  const rot = [30, 225, 0], [ax, ay] = rot.map(v => v * Math.PI / 180);
  const pts = [];
  for (const x of [0, 1]) for (const y of [0, 1]) for (const z of [0, 1]) pts.push([x, y, z]);
  for (const f of pieces().find(p => p.name === 'strip_full').faces) pts.push(...f.pts);
  const pr = pts.map(([x, y, z]) => { const X = x - 0.5, Y = y - 0.5, Z = z - 0.5, x1 = X * Math.cos(ay) + Z * Math.sin(ay), z1 = -X * Math.sin(ay) + Z * Math.cos(ay); return [x1, Y * Math.cos(ax) - z1 * Math.sin(ax)]; });
  const xs = pr.map(p => p[0]), ys = pr.map(p => p[1]), w = Math.max(...xs) - Math.min(...xs), h = Math.max(...ys) - Math.min(...ys);
  const scale = Math.round(15.5 / 16 / Math.max(w, h) * 1000) / 1000, cx = (Math.max(...xs) + Math.min(...xs)) / 2, cy = (Math.max(...ys) + Math.min(...ys)) / 2;
  const r3 = v => Math.round(v * 1000) / 1000 || 0;
  return {gui: {rotation: rot, translation: [r3(-scale * cx * 16), r3(-scale * cy * 16), 0], scale: [scale, scale, scale]},
    ground: {rotation: [0, 0, 0], translation: [0, 3, 0], scale: [0.25, 0.25, 0.25]}, fixed: {rotation: [0, 0, 0], translation: [0, 0, 0], scale: [0.5, 0.5, 0.5]},
    thirdperson_righthand: {rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [0.375, 0.375, 0.375]},
    firstperson_righthand: {rotation: [0, 45, 0], translation: [0, 0, 0], scale: [0.4, 0.4, 0.4]}, firstperson_lefthand: {rotation: [0, 225, 0], translation: [0, 0, 0], scale: [0.4, 0.4, 0.4]}};
}

/** Editable source (AGENTS.md): every piece as a Free Model mesh, laid out on a grid two blocks apart, both textures. */
function bbmodel(P, maps) {
  const uuid = s => { const h = createHash('sha256').update('afl-curbs-v1:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
  const key = i => i.toString(36).padStart(4, '0'), TEX = {curb: 0, warning: 1};
  const elements = P.map((pc, i) => {
    const ox = (i % 6) * 32, oz = Math.floor(i / 6) * 32, vertices = {}, faces = {}; let n = 0;
    pc.faces.forEach((f, fi) => { const ids = f.pts.map(p => { const k = key(n++); vertices[k] = [p[0] * 16 + ox - 8, p[1] * 16 - 16, p[2] * 16 + oz - 8].map(r6); return k; });
      faces['f' + key(fi)] = {uv: Object.fromEntries(ids.map((k, j) => { const [u, w] = uvOf(f.pts[j], f.n, f.mat); return [k, [r6(u * N), r6(w * N)]]; })), vertices: ids, texture: TEX[f.mat]}; });
    return {name: pc.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: true, locked: false, render_order: 'default',
      scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: uuid('mesh:' + pc.name)};
  });
  const texture = (name, id, data) => ({name: name + '.png', relative_path: `textures/${name}.png`, folder: '', namespace: '', id: String(id), group: '', scope: 0, width: N, height: N,
    uv_width: N, uv_height: N, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '', file_format: 'png', render_mode: 'default', render_sides: 'auto',
    wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1, frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true,
    uuid: uuid('texture:' + name), source: 'data:image/png;base64,' + data.toString('base64')});
  return {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: 'curbs_v1', model_identifier: '', visible_box: [1, 1, 0], variable_placeholders: '',
    variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: N, height: N}, elements,
    outliner: elements.map(e => e.uuid), textures: [texture('curbs_v1_concrete', 0, maps.curb[0]), texture('curbs_v1_warning', 1, maps.warning[0])], animations: []};
}

export function outputs() {
  const out = [], P = pieces();
  const maps = {curb: render(curbConcrete()), warning: render(warningDomes())};
  for (const [name, m] of [['curb_concrete', maps.curb], ['curb_warning', maps.warning]])
    ['', '_s', '_n'].forEach((k, i) => out.push([path.join(ASSETS, 'textures/block/curb', name + k + '.png'), m[i]]));
  for (const pc of P) {
    const [o, m] = objOf(pc), dir = path.join(ASSETS, 'models/block/curb');
    out.push([path.join(dir, pc.name + '.obj'), o], [path.join(dir, pc.name + '.mtl'), m], [path.join(dir, pc.name + '.json'), json(pieceModel(pc.name))]);
  }
  const display = guiDisplay();
  for (const [id, base, grass] of [['curb_sidewalk', B + 'ground/concrete_sidewalk', false], ['curb_grass', 'minecraft:block/grass_block', true]]) {
    out.push([path.join(ASSETS, 'models/block/curb', id + '.json'), json({loader: NS + 'curb', base, grass, pieces: B + 'curb/', gui_light: 'side',
      textures: {particle: grass ? 'minecraft:block/dirt' : B + 'ground/concrete_sidewalk_plain'}})]);
    out.push([path.join(ASSETS, 'blockstates', id + '.json'), json({variants: {'': {model: B + 'curb/' + id}}})]);
    out.push([path.join(ASSETS, 'models/item', id + '.json'), json({parent: B + 'curb/' + id, display})]);
  }
  out.push([path.join(BB, 'curbs_v1.bbmodel'), JSON.stringify(bbmodel(P, maps))]);
  return out;
}
/** The piece names CurbModel loads (kept in step with client/CurbModel.PIECES). */
export const PIECE_NAMES = () => pieces().map(p => p.name);

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  const list = outputs();
  // every piece closed where it must be: report faces per piece, and no piece is empty
  for (const pc of pieces()) assert(pc.faces.length, 'empty piece ' + pc.name);
  if (process.argv.includes('--check')) {
    for (const [file, data] of list) { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; if (!cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data))) throw new Error('stale ' + path.relative(ROOT, file)); }
    console.log('CHECK OK');
  } else {
    for (const [file] of list) fs.mkdirSync(path.dirname(file), {recursive: true});
    for (const [file, data] of list) fs.writeFileSync(file, data);
    console.log(JSON.stringify({pieces: pieces().map(p => `${p.name}:${p.faces.length}`), gui: guiDisplay().gui}));
    console.log('wrote ' + list.length + ' files');
  }
}
