// Tunnel V1 (docs/worldgen/federal_tunnels_v1.md): the textures of the drawn lining (TunnelMesh: wall panels, the dark
// arch, steel, the slot drain) and the equipment parts the client places along a tunnel (client/road/TunnelParts), as
// standalone Vextris meshes (assets/apocalypse_firstlight/meshes/road/tunnel/<name>.vxmesh.json) on one shared sheet
// (textures/block/road/tunnel/equipment). Every part is unlit: the world has no power (user, 2026-10-15), so luminaires,
// signals, lane signals and the message sign are dark lenses and dark LED faces, and every _s map keeps alpha 255
// (LabPBR: no emission).
// Part frame (TunnelMesh.Placement): x to the right seen from the front, y up, z out of the front (toward the traffic or
// out of the wall it is fixed to); 1 = 1 m. Roadside parts stand at the shoulder with the road toward -x.
// Textures: 240 texels per metre (the message sign's dot matrix 120), LabPBR (_s R smoothness, G F0, B porosity, A 255;
// _n DirectX normal, B AO, A height). Every size divides by 16.
//   node tools/build-tunnel-v1.mjs           -> writes textures, meshes, the Blockbench source
//   node tools/build-tunnel-v1.mjs --check   -> verifies every output is up to date
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {png} from './cube-slab-mesh-lib.mjs';
import {glyph as letterGlyph, WIDTH as LETTER_WIDTH} from './afl-sign-font.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const ASSETS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const BB = path.join(ROOT, 'src/main/blockbench');
const TEX = path.join(ASSETS, 'textures/block/road/tunnel');
const MESHES = path.join(ASSETS, 'meshes/road/tunnel');
const assert = (c, m) => { if (!c) throw new Error(m); };
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const mod = (a, n) => ((a % n) + n) % n;
const r6 = v => Math.round(v * 1e6) / 1e6 || 0;

// ================================================================ textures
const PX = 240, MM = 1000 / PX;
const hash = (a, b, c) => { let h = Math.imul(a | 0, 374761393) ^ Math.imul(b | 0, 668265263) ^ Math.imul(c | 0, 2147483647); h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };
const smooth = t => t * t * (3 - 2 * t);
/** Value noise -1..1 with cell size `cell` px, periodic over px / py texels when given (a tiling texture). */
function vn(x, y, seed, cell, px = 0, py = 0) {
  const nx = px ? Math.round(px / cell) : 0, ny = py ? Math.round(py / cell) : 0, xi = Math.floor(x / cell), yi = Math.floor(y / cell);
  const h = (i, j) => hash(nx ? mod(i, nx) : i, ny ? mod(j, ny) : j, seed);
  const tx = smooth(x / cell - xi), ty = smooth(y / cell - yi);
  const a = h(xi, yi) + (h(xi + 1, yi) - h(xi, yi)) * tx, b = h(xi, yi + 1) + (h(xi + 1, yi + 1) - h(xi, yi + 1)) * tx;
  return 2 * (a + (b - a) * ty) - 1;
}
const mul = (c, t) => c.map(v => v * t);
const mix = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);

/**
 * Raster buffers (colour, spec, AO, height, region id) of W x H texels; paint() fills a rect from a sample function
 * f(x, y) -> {c, s, f0, p, h, ao} (x, y local to the rect, supersampled ss x ss); finish() derives the normal map (heights
 * in mm, neighbours only within the same region; wrap: a tiling texture) and returns the three PNGs.
 */
class Raster {
  constructor(W, H) { this.W = W; this.H = H; this.col = Buffer.alloc(W * H * 4); this.spec = Buffer.alloc(W * H * 4); this.ao = new Float64Array(W * H).fill(1); this.h = new Float64Array(W * H); this.id = new Int32Array(W * H); this.next = 1; }
  paint(rx, ry, rw, rh, f, ss = 2) {
    const id = this.next++;
    for (let y = 0; y < rh; y++) for (let x = 0; x < rw; x++) {
      let c = [0, 0, 0], s = 0, f0 = 0, p = 0, h = 0, ao = 0;
      for (let sy = 0; sy < ss; sy++) for (let sx = 0; sx < ss; sx++) {
        const r = f(x + (sx + 0.5) / ss, y + (sy + 0.5) / ss);
        c = c.map((v, i) => v + r.c[i]); s += r.s; f0 += r.f0 ?? 14; p += r.p ?? 0; h += r.h ?? 0; ao += r.ao ?? 1;
      }
      const k = ss * ss, i = (ry + y) * this.W + rx + x, o = i * 4;
      c.forEach((v, j) => { this.col[o + j] = clamp(Math.round(v / k), 0, 255); }); this.col[o + 3] = 255;
      this.spec[o] = clamp(Math.round(s / k), 0, 255); this.spec[o + 1] = clamp(Math.round(f0 / k), 0, 229); this.spec[o + 2] = clamp(Math.round(p / k), 0, 64);
      this.spec[o + 3] = 255;   // no emission anywhere
      this.h[i] = h / k; this.ao[i] = ao / k; this.id[i] = id;
    }
  }
  finish(wrap = false) {
    const {W, H} = this, nrm = Buffer.alloc(W * H * 4);
    for (let i = 0; i < W * H; i++) if (!this.id[i]) { const o = i * 4; this.col.fill(128, o, o + 3); this.col[o + 3] = 255; this.spec[o] = 60; this.spec[o + 1] = 14; this.spec[o + 3] = 255; }
    const at = (x, y, ref) => {
      if (wrap) { x = mod(x, W); y = mod(y, H); } else if (x < 0 || y < 0 || x >= W || y >= H) return null;
      const i = y * W + x; return this.id[i] === ref ? this.h[i] : null;
    };
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const i = y * W + x, o = i * 4, ref = this.id[i], c = this.h[i];
      const l = at(x - 1, y, ref) ?? c, r = at(x + 1, y, ref) ?? c, u = at(x, y - 1, ref) ?? c, d = at(x, y + 1, ref) ?? c;
      let nx = -(r - l) / (2 * MM), ny = -(d - u) / (2 * MM); const len = Math.hypot(nx, ny, 1); nx /= len; ny /= len;
      nrm[o] = Math.round((nx * 0.5 + 0.5) * 255); nrm[o + 1] = Math.round((ny * 0.5 + 0.5) * 255);
      nrm[o + 2] = clamp(Math.round(255 * this.ao[i]), 0, 255); nrm[o + 3] = clamp(Math.round(255 * (1 + c / 250)), 0, 255);
    }
    return [png(this.col, W, H), png(this.spec, W, H), png(nrm, W, H)];
  }
}

// ---------------------------------------------------------------- lining textures
/** Porcelain-enamel steel wall panel, 2.4 x 1.2 m (576 x 288), the joints on its edges (2 px each side). */
function panelTexture() {
  const W = 576, H = 288, R = new Raster(W, H), base = [196, 198, 192];
  R.paint(0, 0, W, H, (x, y) => {
    const e = Math.min(x, W - x, y, H - y), t = 1 + 0.018 * vn(x, y, 11, 48, W, H) + 0.008 * vn(x, y, 12, 16, W, H);
    // road film low on each panel and a faint wash down from its top joint
    const film = 0.04 * clamp(y / H - 0.55, 0, 1) + 0.015 * (0.5 + 0.5 * vn(x, y, 13, 96, W, H));
    if (e < 2) return {c: [62, 64, 64], s: 40, f0: 14, p: 10, h: -1.2, ao: 0.55};
    if (e < 3) return {c: mul(base, (1 - film) * 0.9), s: 150, f0: 14, h: -0.4, ao: 0.85};
    return {c: mul(base, t * (1 - film)), s: 175 + 10 * vn(x, y, 14, 48, W, H), f0: 14, p: 0, h: 0.05 * vn(x, y, 15, 16, W, H)};
  });
  return R.finish(true);
}
/** The arch above the panels: shotcrete under a dark matt coat, 2.4 x 2 m (576 x 480). */
function archTexture() {
  const W = 576, H = 480, R = new Raster(W, H), base = [54, 55, 56];
  R.paint(0, 0, W, H, (x, y) => {
    const t = 1 + 0.06 * vn(x, y, 21, 96, W, H) + 0.035 * vn(x, y, 22, 24, W, H) + 0.015 * vn(x, y, 23, 8, W, H);
    return {c: mul(base, t), s: 34 + 8 * vn(x, y, 24, 48, W, H), f0: 14, p: 30, h: 0.35 * vn(x, y, 25, 12, W, H) + 0.25 * vn(x, y, 26, 48, W, H)};
  });
  return R.finish(true);
}
/** Hot-dip galvanised steel (handrails, brackets): even, with a faint spangle, 1 m (240). */
function steelTexture() {
  const W = 240, R = new Raster(W, W), base = [150, 153, 154];
  R.paint(0, 0, W, W, (x, y) => {
    const t = 1 + 0.03 * vn(x, y, 31, 24, W, W) + 0.02 * vn(x, y, 32, 8, W, W);
    return {c: mul(base, t), s: 105 + 12 * vn(x, y, 33, 16, W, W), f0: 40, p: 0, h: 0.05 * vn(x, y, 34, 8, W, W)};
  });
  return R.finish(true);
}
/** The slot drain along a barrier's toe: 2.4 m along (576), 0.133 m across (32): steel lips either side of the slot. */
function drainTexture() {
  const W = 576, H = 32, R = new Raster(W, H), lip = [104, 106, 106];
  R.paint(0, 0, W, H, (x, y) => {
    const t = 1 + 0.05 * vn(x, y, 41, 24, W) + 0.03 * vn(x, y, 42, 6, W);
    if (y >= 12 && y < 20) return {c: [14, 14, 14], s: 20, f0: 14, h: -3, ao: 0.25};
    const edge = Math.min(Math.abs(y - 12), Math.abs(y - 20));
    return {c: mul(lip, t * (edge < 1.5 ? 0.85 : 1)), s: 70, f0: 30, p: 5, h: edge < 1.5 ? -0.5 : 0, ao: edge < 1.5 ? 0.7 : 1};
  });
  return R.finish(false);
}

// ---------------------------------------------------------------- sign glyphs
// Letters: tools/afl-sign-font.mjs (cap 750, stroke 150). Digits, the point, the metre's m and the arrows are drawn
// here in the same construction.
const CAP = 750, S = 150;
const len = (x, y) => Math.hypot(x, y);
function sdBox(px, py, x0, y0, x1, y1) { const cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, dx = Math.abs(px - cx) - (x1 - x0) / 2, dy = Math.abs(py - cy) - (y1 - y0) / 2; return len(Math.max(dx, 0), Math.max(dy, 0)) + Math.min(Math.max(dx, dy), 0); }
function sdRound(px, py, x0, y0, x1, y1, r) { const cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, dx = Math.abs(px - cx) - ((x1 - x0) / 2 - r), dy = Math.abs(py - cy) - ((y1 - y0) / 2 - r); return len(Math.max(dx, 0), Math.max(dy, 0)) + Math.min(Math.max(dx, dy), 0) - r; }
const box = (x0, y0, x1, y1) => (x, y) => sdBox(x, y, x0, y0, x1, y1);
const seg = (ax, ay, bx, by, w = S) => { const dx = bx - ax, dy = by - ay, L = Math.hypot(dx, dy), ux = dx / L, uy = dy / L; return (x, y) => sdBox((x - ax) * ux + (y - ay) * uy - L / 2, -(x - ax) * uy + (y - ay) * ux, -L / 2, -w / 2, L / 2, w / 2); };
const ring = (x0, y0, x1, y1, r) => (x, y) => Math.max(sdRound(x, y, x0, y0, x1, y1, r), -sdRound(x, y, x0 + S, y0 + S, x1 - S, y1 - S, Math.max(r - S, 0)));
const U = (...fs) => (x, y) => Math.min(...fs.map(f => f(x, y)));
const minus = (a, ...bs) => (x, y) => Math.max(a(x, y), ...bs.map(b => -b(x, y)));
const and = (a, b) => (x, y) => Math.max(a(x, y), b(x, y));
const H = CAP, XH = 540;
const EXTRA = {
  '0': [560, W => ring(0, 0, W, H, 250)],
  '1': [380, W => U(box(W - S - 40, 0, W - 40, H), seg(40, H - 230, W - S / 2 - 40, H, S))],
  '2': [540, W => U(and(ring(0, H * 0.4, W, H, 220), U(box(-1, H * 0.66, W + 1, H + 1), box(W / 2, H * 0.4, W + 1, H + 1))), seg(W - S * 0.55, H * 0.58, S * 0.35, S * 0.6), box(0, 0, W, S))],
  '3': [540, W => U(minus(ring(10, H / 2 - S / 2, W - 10, H, 170), box(-1, H / 2 - S / 2 - 1, W * 0.4, H * 0.78)), minus(ring(0, 0, W, H / 2 + S / 2, 180), box(-1, H * 0.22, W * 0.4, H / 2 + S / 2 + 1)))],
  '4': [580, W => U(box(W * 0.6, 0, W * 0.6 + S, H), box(0, H * 0.26, W, H * 0.26 + S), seg(S * 0.45, H * 0.26 + S / 2, W * 0.6 + S / 2, H + 60, S))],
  '5': [540, W => U(box(20, H - S, W, H), box(20, H * 0.5, 20 + S, H), minus(ring(0, 0, W, H * 0.62, 210), box(-1, H * 0.12, W * 0.4, H * 0.5)))],
  '6': [560, W => U(minus(ring(0, 0, W, H, 250), box(W / 2, H * 0.3, W + 1, H * 0.8)), ring(0, 0, W, H * 0.62, 220))],
  '7': [520, W => U(box(0, H - S, W, H), seg(W - S * 0.5, H - S / 2, W * 0.3, -80, S))],
  '8': [560, W => U(ring(25, H / 2 - S / 2, W - 25, H, 170), ring(0, 0, W, H / 2 + S / 2, 185))],
  '9': [560, W => U(minus(ring(0, 0, W, H, 250), box(-1, H * 0.2, W / 2, H * 0.7)), ring(0, H * 0.38, W, H, 220))],
  '.': [150, () => box(0, 0, S, S)],
  'm': [760, W => U(box(0, 0, S, XH), box(W / 2 - S / 2, 0, W / 2 + S / 2, XH * 0.7), box(W - S, 0, W, XH * 0.7),
    and(ring(0, 0, W / 2 + S / 2, XH, 165), box(-1, XH * 0.5, W, XH + 1)), and(ring(W / 2 - S / 2, 0, W, XH, 165), box(-1, XH * 0.5, W + 1, XH + 1)))],
  '>': [640, W => U(box(0, H / 2 - S / 2, W - 140, H / 2 + S / 2), seg(W - 60, H / 2 + 40, W - 330, H / 2 + 310), seg(W - 60, H / 2 - 40, W - 330, H / 2 - 310))],
  '<': [640, W => U(box(140, H / 2 - S / 2, W, H / 2 + S / 2), seg(60, H / 2 + 40, 330, H / 2 + 310), seg(60, H / 2 - 40, 330, H / 2 - 310))],
};
function glyphOf(ch) {
  if (EXTRA[ch]) { const [W, b] = EXTRA[ch], f = b(W); return {width: W, sdf: (x, y) => Math.max(f(x, y), sdBox(x, y, 0, 0, W, H))}; }
  assert(LETTER_WIDTH[ch] !== undefined, 'no glyph ' + ch);
  return letterGlyph(ch);
}
/** One line of text set with the glyph advances (font units; spaces `space`), its width and sdf. */
function line(text, {gap = 110, space = 320} = {}) {
  const parts = []; let x = 0;
  for (const ch of text) { if (ch === ' ') { x += space; continue; } const g = glyphOf(EXTRA[ch] ? ch : ch.toUpperCase()); parts.push([x, g]); x += g.width + gap; }
  const width = Math.max(0, x - gap);
  return {width, sdf: (px, py) => { let d = 1e3; for (const [ox, g] of parts) if (px > ox - 200 && px < ox + g.width + 200) d = Math.min(d, g.sdf(px - ox, py)); return d; }};
}
/**
 * A sign face sampler for a w x h px region: lines of text centred, each cap `cap` px high (shrunk to fit the width
 * with `margin` px either side), text colour on the background, an optional border `border` px in from the edge.
 */
function signFace(w, h, lines, {cap, margin, fg, bg, border = 0, bw = 0, gapLines = 0.7}) {
  const L = lines.map(t => line(t));
  const scales = L.map(l => Math.min(cap / CAP, (w - 2 * margin) / l.width));
  const heights = scales.map(s => s * CAP), total = heights.reduce((a, b) => a + b, 0) + gapLines * cap * (L.length - 1);
  const base = []; let y = (h - total) / 2;
  for (let i = 0; i < L.length; i++) { y += heights[i]; base.push(y); y += gapLines * cap; }
  const t = (x, yy) => 0.012 * vn(x, yy, 51, 24) + 0.006 * vn(x, yy, 52, 6);
  return (x, yy) => {
    const n = 1 + t(x, yy);
    if (border && bw) { const e = Math.min(x, w - x, yy, h - yy); if (e >= border && e < border + bw) return {c: mul(fg, n), s: 120, f0: 14, h: 0.3}; }
    for (let i = 0; i < L.length; i++) {
      const s = scales[i], x0 = (w - L[i].width * s) / 2;
      if (yy < base[i] - heights[i] - 4 || yy > base[i] + 4) continue;
      if (L[i].sdf((x - x0) / s, (base[i] - yy) / s) < 0) return {c: mul(fg, n), s: 120, f0: 14, h: 0.3};
    }
    return {c: mul(bg, n), s: 110, f0: 14, h: 0};
  };
}

// ---------------------------------------------------------------- equipment sheet
const SW = 2048, SH = 2048;
/** Uniform materials, 64 x 64 tiles along the top row; faces map to the middle 32 x 32 (no bleeding under mipmaps). */
const MATS = {
  galv: {c: [150, 153, 154], s: 105, f0: 40},
  stainless: {c: [168, 171, 172], s: 150, f0: 50},
  black: {c: [30, 31, 32], s: 90, f0: 14},
  red: {c: [150, 30, 26], s: 110, f0: 14},
  green: {c: [20, 98, 66], s: 110, f0: 14},
  white: {c: [222, 222, 216], s: 115, f0: 14},
  yellow: {c: [222, 178, 34], s: 110, f0: 14},
  lens_red: {c: [86, 12, 10], s: 225, f0: 10},
  lens_amber: {c: [104, 58, 8], s: 225, f0: 10},
  lens_green: {c: [12, 66, 50], s: 225, f0: 10},
  diffuser: {c: [150, 150, 144], s: 190, f0: 10},
  glass: {c: [18, 20, 22], s: 235, f0: 10},
  concrete: {c: [150, 148, 142], s: 55, f0: 14, p: 40},
};
const NAMES = ['cedar_pass', 'hollow_ridge', 'iron_gap', 'pine_summit', 'black_canyon', 'eagle_crest', 'stone_notch', 'red_bluff'];
const EGRESS = []; for (const side of ['l', 'r']) for (const d of [25, 50, 75, 100, 125, 150]) EGRESS.push(side + d);
const SIGN = {green: [20, 98, 66], white: [226, 226, 220], black: [24, 24, 24], yellow: [226, 182, 30], red: [160, 30, 26]};
/** Region rects (x, y, w, h px) on the sheet. */
const R = {};
Object.keys(MATS).forEach((k, i) => { R['mat:' + k] = [i * 64, 0, 64, 64]; });
NAMES.forEach((n, i) => { R['name:' + n] = [0, 64 + 192 * i, 1536, 192]; });              // 6.4 x 0.8 m
EGRESS.forEach((n, i) => { R['egress:' + n] = [1536, 64 + 72 * i, 288, 72]; });          // 1.2 x 0.3 m
R.lane = [1536, 928, 144, 144];                                                           // 0.6 x 0.6 m
R.grille = [1536, 1072, 240, 240];                                                        // 1 m disc
R.emergency = [1776, 1072, 216, 48];                                                      // 0.9 x 0.2 m
R.exit = [1776, 1136, 144, 60];                                                           // 0.6 x 0.25 m
R.clearance = [0, 1600, 576, 128];                                                        // 2.4 x 0.533 m
R.headlight = [576, 1600, 384, 288];                                                      // 1.6 x 1.2 m
R.cms = [960, 1600, 720, 216];                                                            // 6 x 1.8 m at 120 / m

function sheet() {
  const Rs = new Raster(SW, SH);
  for (const [k, m] of Object.entries(MATS)) {
    const [x0, y0, w, h] = R['mat:' + k];
    Rs.paint(x0, y0, w, h, (x, y) => ({c: mul(m.c, 1 + 0.02 * vn(x + x0, y, 61, 16)), s: m.s, f0: m.f0, p: m.p ?? 0}));
  }
  const paint = (key, f) => { const [x0, y0, w, h] = R[key]; Rs.paint(x0, y0, w, h, f); };
  for (const n of NAMES)
    paint('name:' + n, signFace(1536, 192, [n.replace('_', ' ').toUpperCase() + ' TUNNEL'], {cap: 86, margin: 70, fg: SIGN.white, bg: SIGN.green, border: 8, bw: 6}));
  for (const n of EGRESS) {
    const right = n[0] === 'r', d = n.slice(1), text = right ? `EXIT ${d} m >` : `< EXIT ${d} m`;
    paint('egress:' + n, signFace(288, 72, [text], {cap: 30, margin: 14, fg: SIGN.white, bg: SIGN.green, border: 3, bw: 2}));
  }
  paint('clearance', signFace(576, 128, ['CLEARANCE 5.1 m'], {cap: 62, margin: 30, fg: SIGN.black, bg: SIGN.yellow, border: 7, bw: 5}));
  paint('headlight', signFace(384, 288, ['TUNNEL', 'TURN ON', 'HEADLIGHTS'], {cap: 46, margin: 26, fg: SIGN.black, bg: SIGN.white, border: 9, bw: 6, gapLines: 0.55}));
  paint('emergency', signFace(216, 48, ['EMERGENCY'], {cap: 26, margin: 14, fg: SIGN.white, bg: SIGN.red}));
  paint('exit', signFace(144, 60, ['EXIT'], {cap: 32, margin: 16, fg: SIGN.white, bg: SIGN.green, border: 3, bw: 2}));
  // dark LED faces: black boards with unlit LED dots (lane signal 12 x 12 at 5 cm, message sign 120 x 36 at 5 cm)
  const dots = (pitch, r) => (x, y) => {
    const cx = (Math.floor(x / pitch) + 0.5) * pitch, cy = (Math.floor(y / pitch) + 0.5) * pitch, d = Math.hypot(x - cx, y - cy);
    return d < r ? {c: [44, 44, 42], s: 200, f0: 10, h: 0.4} : {c: [14, 14, 15], s: 60, f0: 14, h: 0};
  };
  paint('lane', dots(12, 3.6));
  paint('cms', dots(6, 2.0));
  // jet fan end: a guard of rings and spokes over the dark duct
  paint('grille', (x, y) => {
    const dx = x - 120, dy = y - 120, r = Math.hypot(dx, dy) / 120, a = Math.atan2(dy, dx);
    const ringWire = Math.abs(mod(r * 12, 1) - 0.5) > 0.42, spoke = Math.abs(Math.sin(a * 4)) * r * 120 < 2.2;
    if (r > 0.97) return {c: [130, 133, 134], s: 105, f0: 40, h: 0.6};
    if (ringWire || spoke) return {c: [140, 143, 144], s: 105, f0: 40, h: 0.8};
    return {c: [12, 12, 12], s: 20, f0: 14, h: -2, ao: 0.35};
  });
  return Rs.finish(false);
}

// ================================================================ meshes
const sub = (a, b) => a.map((v, i) => v - b[i]), dot = (a, b) => a.reduce((s, v, i) => s + v * b[i], 0);
const newell = P => { let n = [0, 0, 0]; for (let i = 0; i < P.length; i++) { const a = P[i], b = P[(i + 1) % P.length]; n = [n[0] + (a[1] - b[1]) * (a[2] + b[2]), n[1] + (a[2] - b[2]) * (a[0] + b[0]), n[2] + (a[0] - b[0]) * (a[1] + b[1])]; } return n; };

class Mesh {
  constructor(name) { this.name = name; this.parts = []; }
  part(name, bone = 'root') { const p = {name, bone, vertices: [], faces: []}; this.parts.push(p); return p; }
  tris() { return this.parts.reduce((s, p) => s + p.faces.reduce((t, f) => t + f.length - 2, 0), 0); }
}
/** A planar face (corners and their sheet texel UVs), wound counter-clockwise seen from `out`. */
function face(p, pts, uvs, out) {
  let n = newell(pts);
  if (Math.hypot(...n) < 1e-12) return;
  if (dot(n, out) < 0) { pts = pts.slice().reverse(); uvs = uvs.slice().reverse(); }
  const b = p.vertices.length;
  pts.forEach((q, i) => p.vertices.push([r6(q[0]), r6(q[1]), r6(q[2]), r6(uvs[i][0] / SW), r6(uvs[i][1] / SH)]));
  p.faces.push(pts.map((_, i) => b + i));
}
/** A uniform material's UV square for n corners (its tile's middle). */
const matUV = (mat, n) => { const [x, y] = R['mat:' + mat]; const q = [[x + 16, y + 16], [x + 48, y + 16], [x + 48, y + 48], [x + 16, y + 48]]; return n === 4 ? q : [q[0], q[1], q[2]]; };
/** A rect region mapped onto a face: the corners top-left, top-right, bottom-right, bottom-left (sub: a fraction rect). */
const regionUV = (key, sub = [0, 0, 1, 1]) => { const [x, y, w, h] = R[key]; const u0 = x + w * sub[0], u1 = x + w * sub[2], v0 = y + h * sub[1], v1 = y + h * sub[3]; return [[u0, v0], [u1, v0], [u1, v1], [u0, v1]]; };

/** An axis-aligned box; `mat` on every face, `front` a region (or [region, sub]) on its +z face read from the front. */
function cuboid(p, x0, y0, z0, x1, y1, z1, mat, front = null) {
  const F = (pts, out, uv) => face(p, pts, uv ?? matUV(mat, 4), out);
  const fr = front ? (Array.isArray(front) ? regionUV(front[0], front[1]) : regionUV(front)) : null;
  F([[x0, y1, z1], [x1, y1, z1], [x1, y0, z1], [x0, y0, z1]], [0, 0, 1], fr);   // front: tl, tr, br, bl
  F([[x1, y1, z0], [x0, y1, z0], [x0, y0, z0], [x1, y0, z0]], [0, 0, -1]);
  F([[x0, y1, z0], [x1, y1, z0], [x1, y1, z1], [x0, y1, z1]], [0, 1, 0]);
  F([[x0, y0, z1], [x1, y0, z1], [x1, y0, z0], [x0, y0, z0]], [0, -1, 0]);
  F([[x1, y1, z1], [x1, y1, z0], [x1, y0, z0], [x1, y0, z1]], [1, 0, 0]);
  F([[x0, y1, z0], [x0, y1, z1], [x0, y0, z1], [x0, y0, z0]], [-1, 0, 0]);
}
/**
 * A cylinder along z (centre cx, cy, radius r, z0 .. z1, n sides); ends capped with `cap` (a material or the grille
 * region, mapped as a disc) unless null.
 */
function cylinderZ(p, cx, cy, r, z0, z1, n, mat, cap0 = mat, cap1 = mat) {
  const pt = (k, z) => { const a = 2 * Math.PI * k / n; return [cx + r * Math.cos(a), cy + r * Math.sin(a), z]; };
  for (let k = 0; k < n; k++) { const m = [Math.cos(2 * Math.PI * (k + 0.5) / n), Math.sin(2 * Math.PI * (k + 0.5) / n), 0]; face(p, [pt(k, z0), pt(k + 1, z0), pt(k + 1, z1), pt(k, z1)], matUV(mat, 4), m); }
  for (const [z, cap, s] of [[z0, cap0, -1], [z1, cap1, 1]]) {
    if (!cap) continue;
    for (let k = 0; k < n; k++) {
      const a = pt(k, z), b = pt(k + 1, z), c = [cx, cy, z];
      let uv;
      if (cap === 'grille') { const [gx, gy, gw] = R.grille, m = q => [gx + gw / 2 + (q[0] - cx) / r * (gw / 2 - 1), gy + gw / 2 - (q[1] - cy) / r * (gw / 2 - 1)]; uv = [m(a), m(b), m(c)]; }
      else uv = matUV(cap, 3);
      face(p, [a, b, c], uv, [0, 0, s]);
    }
  }
}
/** A cylinder along y (a pole): centre cx, cz, radius r, y0 .. y1, n sides, capped. */
function cylinderY(p, cx, cz, r, y0, y1, n, mat) {
  const pt = (k, y) => { const a = 2 * Math.PI * k / n; return [cx + r * Math.cos(a), y, cz + r * Math.sin(a)]; };
  for (let k = 0; k < n; k++) { const a = 2 * Math.PI * (k + 0.5) / n; face(p, [pt(k, y0), pt(k + 1, y0), pt(k + 1, y1), pt(k, y1)], matUV(mat, 4), [Math.cos(a), 0, Math.sin(a)]); }
  for (const [y, s] of [[y0, -1], [y1, 1]]) for (let k = 0; k < n; k++) face(p, [pt(k, y), pt(k + 1, y), [cx, y, cz]], matUV(mat, 3), [0, s, 0]);
}
/** An annulus at z between radii r0 < r1 (the step between two cylinders), facing s. */
function annulusZ(p, cx, cy, r0, r1, z, n, mat, s) {
  for (let k = 0; k < n; k++) {
    const a = 2 * Math.PI * k / n, b = 2 * Math.PI * (k + 1) / n;
    face(p, [[cx + r0 * Math.cos(a), cy + r0 * Math.sin(a), z], [cx + r1 * Math.cos(a), cy + r1 * Math.sin(a), z], [cx + r1 * Math.cos(b), cy + r1 * Math.sin(b), z], [cx + r0 * Math.cos(b), cy + r0 * Math.sin(b), z]], matUV(mat, 4), [0, 0, s]);
  }
}

/** Lateral offsets of the signal heads / sign over the lanes (road toward -x from the shoulder part). */
const ROADSIDE = {r4: {arm: 11.0, heads: [-5.8, -9.5], cms: [-10.2, -4.2]}, r2: {arm: 6.0, heads: [-3.9], cms: [-6.0, -2.4]}};

export function meshes() {
  const M = [], mesh = n => { const m = new Mesh(n); M.push(m); return m; };
  // luminaire on the upper wall: a stainless housing, the unlit diffuser, two brackets; along x (the tunnel)
  { const m = mesh('luminaire');
    cuboid(m.part('housing'), -0.6, -0.15, 0.03, 0.6, 0.15, 0.17, 'stainless');
    cuboid(m.part('diffuser'), -0.55, -0.12, 0.17, 0.55, 0.12, 0.18, 'diffuser');
    cuboid(m.part('bracket_a'), -0.49, -0.1, 0, -0.41, 0.1, 0.03, 'galv');
    cuboid(m.part('bracket_b'), 0.41, -0.1, 0, 0.49, 0.1, 0.03, 'galv'); }
  // emergency cabinet on the egress wall (extinguishers, phone): body, red door, label, handle
  { const m = mesh('emergency_cabinet');
    cuboid(m.part('body'), -0.45, 0.9, 0, 0.45, 2.0, 0.22, 'stainless');
    cuboid(m.part('door'), -0.42, 0.93, 0.22, 0.42, 1.97, 0.235, 'red');
    cuboid(m.part('label'), -0.36, 1.78, 0.235, 0.36, 1.94, 0.24, 'galv', 'emergency');
    cuboid(m.part('handle'), 0.3, 1.3, 0.235, 0.34, 1.5, 0.27, 'black'); }
  // egress signs low on the egress wall (photoluminescent, unlit): one bone per variant, arrow toward the nearest exit
  { const m = mesh('egress_sign');
    for (const n of EGRESS) cuboid(m.part('sign_' + n, n), -0.6, 0, 0, 0.6, 0.3, 0.03, 'galv', 'egress:' + n); }
  // CCTV camera on a wall arm, looking along +x (the tunnel)
  { const m = mesh('cctv');
    cuboid(m.part('plate'), -0.08, -0.08, 0, 0.08, 0.08, 0.02, 'galv');
    cuboid(m.part('arm'), -0.03, -0.03, 0.02, 0.03, 0.03, 0.37, 'galv');
    cuboid(m.part('stem'), -0.02, -0.06, 0.33, 0.02, -0.03, 0.37, 'galv');
    cuboid(m.part('shield'), -0.14, -0.07, 0.26, 0.26, -0.06, 0.44, 'white');
    cuboid(m.part('housing'), -0.12, -0.19, 0.28, 0.22, -0.07, 0.42, 'white');
    cuboid(m.part('lens'), 0.22, -0.16, 0.31, 0.23, -0.1, 0.39, 'glass'); }
  // lane control signal over a lane (dark LED face toward the traffic), hung on two rods into the arch
  { const m = mesh('lane_signal');
    cuboid(m.part('box'), -0.3, 0, -0.12, 0.3, 0.6, 0.12, 'black', 'lane');
    cuboid(m.part('rod_a'), -0.215, 0.6, -0.015, -0.185, 3.8, 0.015, 'galv');
    cuboid(m.part('rod_b'), 0.185, 0.6, -0.015, 0.215, 3.8, 0.015, 'galv'); }
  // jet fan hung under the arch, its axis along z (the tunnel): silencers, motor casing, guards, hangers
  { const m = mesh('jet_fan'), y = -0.92, n = 16;   // 1.2 m across, 5.2 m long with its silencers (concept T2)
    cylinderZ(m.part('silencer_a'), 0, y, 0.6, -2.6, -0.8, n, 'galv', 'grille', null);
    cylinderZ(m.part('casing'), 0, y, 0.64, -0.8, 0.8, n, 'galv', null, null);
    cylinderZ(m.part('silencer_b'), 0, y, 0.6, 0.8, 2.6, n, 'galv', null, 'grille');
    annulusZ(m.part('step_a'), 0, y, 0.6, 0.64, -0.8, n, 'galv', -1);
    annulusZ(m.part('step_b'), 0, y, 0.6, 0.64, 0.8, n, 'galv', 1);
    for (const [k, z] of [['a', -1.5], ['b', 1.5]]) {
      cuboid(m.part('beam_' + k), -0.45, -0.32, z - 0.05, 0.45, -0.24, z + 0.05, 'galv');
      cuboid(m.part('rod_' + k + '1'), -0.41, -0.24, z - 0.02, -0.37, 0.35, z + 0.02, 'galv');
      cuboid(m.part('rod_' + k + '2'), 0.37, -0.24, z - 0.02, 0.41, 0.35, z + 0.02, 'galv');
    } }
  // fire door of a cross passage: frame in the wall opening (2.4 x 2.2 m), both leaves standing open against the
  // passage walls (z < 0: into the passage), the EXIT sign over it
  { const m = mesh('fire_door');
    cuboid(m.part('jamb_l'), -1.2, 0, -0.12, -1.12, 2.2, 0.02, 'green');
    cuboid(m.part('jamb_r'), 1.12, 0, -0.12, 1.2, 2.2, 0.02, 'green');
    cuboid(m.part('head'), -1.12, 2.12, -0.12, 1.12, 2.2, 0.02, 'green');
    cuboid(m.part('leaf_l'), -1.115, 0.01, -1.24, -1.07, 2.1, -0.13, 'green');
    cuboid(m.part('leaf_r'), 1.07, 0.01, -1.24, 1.115, 2.1, -0.13, 'green');
    cuboid(m.part('sign'), -0.3, 2.3, 0, 0.3, 2.55, 0.03, 'galv', 'exit'); }
  // tunnel name plate on each portal (guide-sign green), one bone per name
  { const m = mesh('name_plate');
    for (const n of NAMES) cuboid(m.part('plate_' + n, n), -3.2, -0.4, 0, 3.2, 0.4, 0.06, 'galv', 'name:' + n); }
  // vertical clearance plate over each ring
  { const m = mesh('clearance_plate');
    cuboid(m.part('plate'), -1.2, 0, 0, 1.2, 0.5333, 0.04, 'galv', 'clearance'); }
  // portal traffic signal: pole, mast arm over the lanes, a head over each lane and one on the pole (all lenses dark)
  { const m = mesh('portal_signal');
    for (const [cls, g] of Object.entries(ROADSIDE)) {
      const P = n => m.part(cls + '_' + n, cls);
      cylinderY(P('pole'), 0, 0, 0.12, 0, 6.6, 8, 'galv');
      cuboid(P('base'), -0.3, 0, -0.3, 0.3, 0.05, 0.3, 'galv');
      cuboid(P('arm'), -g.arm, 6.3, -0.08, -0.12, 6.45, 0.08, 'galv');
      const heads = g.heads.map(x => [x, 5.3]).concat([[-0.5, 3.0]]);
      heads.forEach(([x, y0], i) => {
        cuboid(P('back' + i), x - 0.33, y0 - 0.05, -0.02, x + 0.33, y0 + 0.95, 0, 'black');
        cuboid(P('head' + i), x - 0.17, y0, 0, x + 0.17, y0 + 0.9, 0.25, 'black');
        [['lens_red', 0.75], ['lens_amber', 0.45], ['lens_green', 0.15]].forEach(([lens, dy], j) =>
          cuboid(P('lens' + i + j), x - 0.11, y0 + dy - 0.11, 0.25, x + 0.11, y0 + dy + 0.11, 0.26, lens));
        if (y0 > 4) cuboid(P('hanger' + i), x - 0.03, y0 + 0.9, -0.03, x + 0.03, 6.3, 0.03, 'galv');
        else cuboid(P('bracket' + i), x + 0.17, y0 + 0.4, -0.03, -0.1, y0 + 0.5, 0.03, 'galv');
      });
    } }
  // headlight sign on two posts
  { const m = mesh('headlight_sign');
    for (const [k, x] of [['a', -0.55], ['b', 0.55]]) cuboid(m.part('post_' + k), x - 0.04, 0, -0.04, x + 0.04, 3.3, 0.04, 'galv');
    cuboid(m.part('sign'), -0.8, 2.1, 0.04, 0.8, 3.3, 0.07, 'galv', 'headlight'); }
  // closure gate: the drive cabinet and its boom standing raised (red and white, 0.5 m bands)
  { const m = mesh('closure_gate');
    cuboid(m.part('cabinet'), -0.25, 0, -0.25, 0.25, 1.0, 0.25, 'yellow');
    cuboid(m.part('hub'), -0.36, 0.8, -0.1, -0.25, 1.0, 0.1, 'galv');
    for (let i = 0; i < 16; i++) cuboid(m.part('boom' + i), -0.36, 1.0 + 0.5 * i, -0.055, -0.26, 1.5 + 0.5 * i, 0.055, i % 2 ? 'white' : 'red'); }
  // changeable message sign on a cantilever over the lanes (dark matrix)
  { const m = mesh('cms_gantry');
    for (const [cls, g] of Object.entries(ROADSIDE)) {
      const P = n => m.part(cls + '_' + n, cls), [x0, x1] = g.cms, w = x1 - x0;
      cuboid(P('pole'), -0.18, 0, -0.18, 0.18, 7.7, 0.18, 'galv');
      cuboid(P('base'), -0.4, 0, -0.4, 0.4, 0.06, 0.4, 'galv');
      cuboid(P('chord_top'), x0 - 0.2, 7.45, -0.1, -0.18, 7.6, 0.1, 'galv');
      cuboid(P('chord_low'), x1 - 0.1, 6.6, -0.08, -0.18, 6.72, 0.08, 'galv');
      cuboid(P('sign'), x0, 5.5, -0.35, x1, 7.3, 0.35, 'black', ['cms', [0, 0, w / 6, 1]]);
      for (const [k, x] of [['a', x0 + 0.4], ['b', x1 - 0.4]]) cuboid(P('hanger_' + k), x - 0.04, 7.3, -0.04, x + 0.04, 7.45, 0.04, 'galv');
    } }
  return M;
}

/** The standalone Vextris mesh file (format 2, blocks, UV 0..1 top left, counter-clockwise). */
function vxmesh(m) {
  return JSON.stringify({format_version: 2, coordinate_space: 'bone_pivot_local_blocks', uv_origin: 'top_left', winding: 'ccw', texture_size: [SW, SH],
    parts: m.parts.map(p => ({name: p.name, bone: p.bone, vertices: p.vertices, faces: p.faces}))}) + '\n';
}

/** Editable source (AGENTS.md): every part as a Free Model mesh, the meshes side by side, with the sheet. */
function bbmodel(M, sheetPng) {
  const uuid = s => { const h = createHash('sha256').update('afl-tunnel-v1:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
  const key = i => i.toString(36).padStart(4, '0'), elements = [], groups = [];
  M.forEach((m, mi) => {
    const ox = (mi % 5) * 240, oz = Math.floor(mi / 5) * 240, children = [];
    for (const p of m.parts) {
      const vertices = {}, faces = {};
      p.vertices.forEach((v, i) => { vertices[key(i)] = [r6(v[0] * 16 + ox), r6(v[1] * 16), r6(v[2] * 16 + oz)]; });
      p.faces.forEach((f, fi) => { faces['f' + key(fi)] = {uv: Object.fromEntries(f.map(i => [key(i), [r6(p.vertices[i][3] * SW), r6(p.vertices[i][4] * SH)]])), vertices: f.map(key), texture: 0}; });
      const e = {name: m.name + '/' + p.bone + '/' + p.name, color: 0, origin: [ox, 0, oz], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: true, locked: false,
        render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: uuid('mesh:' + m.name + ':' + p.name)};
      elements.push(e); children.push(e.uuid);
    }
    groups.push({name: m.name, origin: [ox, 0, oz], color: 0, uuid: uuid('group:' + m.name), export: true, isOpen: false, locked: false, visibility: true, autouv: 0, children});
  });
  const texture = {name: 'equipment.png', relative_path: '../resources/assets/apocalypse_firstlight/textures/block/road/tunnel/equipment.png', folder: '', namespace: '', id: '0', group: '',
    scope: 0, width: SW, height: SH, uv_width: SW, uv_height: SH, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '', file_format: 'png',
    render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1, frame_order_type: 'loop', frame_order: '',
    frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture:equipment'), source: 'data:image/png;base64,' + sheetPng.toString('base64')};
  return JSON.stringify({meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: 'tunnel_equipment_v1', model_identifier: '', visible_box: [1, 1, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: SW, height: SH}, elements,
    outliner: groups, textures: [texture], animations: []});
}

export function outputs() {
  const out = [];
  const tex = {panel: panelTexture(), arch: archTexture(), steel: steelTexture(), drain: drainTexture(), equipment: sheet()};
  for (const [name, maps] of Object.entries(tex)) ['', '_s', '_n'].forEach((k, i) => out.push([path.join(TEX, name + k + '.png'), maps[i]]));
  const M = meshes();
  for (const m of M) out.push([path.join(MESHES, m.name + '.vxmesh.json'), vxmesh(m)]);
  out.push([path.join(BB, 'tunnel_equipment_v1.bbmodel'), bbmodel(M, tex.equipment[0])]);
  return out;
}

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  const M = meshes();
  for (const m of M) {
    assert(m.parts.length <= 128, m.name + ' has more than 128 parts');
    assert(m.tris() <= 16384, m.name + ' over 16384 triangles');
    for (const p of m.parts) for (const v of p.vertices) assert(v.slice(0, 3).every(c => Math.abs(c) <= 256) && v[3] >= 0 && v[3] <= 1 && v[4] >= 0 && v[4] <= 1, m.name + ' vertex out of range');
  }
  const list = outputs();
  if (process.argv.includes('--check')) {
    for (const [file, data] of list) { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; if (!cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data))) throw new Error('stale ' + path.relative(ROOT, file)); }
    console.log('CHECK OK');
  } else {
    for (const [file] of list) fs.mkdirSync(path.dirname(file), {recursive: true});
    for (const [file, data] of list) fs.writeFileSync(file, data);
    console.log(JSON.stringify(Object.fromEntries(M.map(m => [m.name, `${m.parts.length} parts, ${m.tris()} tris`]))));
    console.log('wrote ' + list.length + ' files');
  }
}
