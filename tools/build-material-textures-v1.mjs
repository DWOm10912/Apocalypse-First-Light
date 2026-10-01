#!/usr/bin/env node
// AFL Material System V1 textures (docs/gameplay/material_system_v1.md).
// 16x16 textures built from small geometric shapes, flat low-frequency shading (no noise, stains or text): the spodumene
// concentrate icon, the lead shielding bricks block and the steel plate block (also used by its slab and stairs). Steel billet, lead brick, electrolytic nickel, tungsten filament
// and the cemented carbide blank are 3D meshes instead (tools/build-material-meshes-v1.mjs).
//   node tools/build-material-textures-v1.mjs           write the PNGs
//   node tools/build-material-textures-v1.mjs --check   fail when a PNG differs from the generator output
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {png} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const TEX = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/textures');
const N = 16, SS = 4;

// ---------------------------------------------------------------- canvas

class Icon {
  constructor() { this.px = new Array(N * N).fill(null); }
  get(x, y) { return x < 0 || y < 0 || x >= N || y >= N ? null : this.px[y * N + x]; }
  set(x, y, c) { if (x >= 0 && y >= 0 && x < N && y < N) this.px[y * N + x] = c; }
  /** Fill a screen polygon; a pixel is covered when at least half of its SSxSS samples fall inside. */
  poly(P, color) {
    const xs = P.map(p => p[0]), ys = P.map(p => p[1]);
    for (let y = Math.max(0, Math.floor(Math.min(...ys))); y < Math.min(N, Math.ceil(Math.max(...ys))); y++)
      for (let x = Math.max(0, Math.floor(Math.min(...xs))); x < Math.min(N, Math.ceil(Math.max(...xs))); x++) {
        let hit = 0;
        for (let sy = 0; sy < SS; sy++) for (let sx = 0; sx < SS; sx++)
          if (inside(P, x + (sx + 0.5) / SS, y + (sy + 0.5) / SS)) hit++;
        if (hit * 2 >= SS * SS) this.set(x, y, typeof color === 'function' ? color(x, y) : color);
      }
  }
  /** Vanilla-style rim: silhouette pixels facing down/right get dark, facing up/left slightly dark. */
  outline(dark = 0.58, light = 0.84) {
    const src = this.px.slice();
    for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
      const c = src[y * N + x];
      if (!c) continue;
      const open = (dx, dy) => { const nx = x + dx, ny = y + dy; return nx < 0 || ny < 0 || nx >= N || ny >= N || !src[ny * N + nx]; };
      if (open(1, 0) || open(0, 1)) this.px[y * N + x] = scale(c, dark);
      else if (open(-1, 0) || open(0, -1)) this.px[y * N + x] = scale(c, light);
    }
  }
  rgba() {
    const out = Buffer.alloc(N * N * 4);
    this.px.forEach((c, i) => { if (c) { out[i * 4] = c[0]; out[i * 4 + 1] = c[1]; out[i * 4 + 2] = c[2]; out[i * 4 + 3] = 255; } });
    return out;
  }
}

function inside(P, x, y) {
  let c = false;
  for (let i = 0, j = P.length - 1; i < P.length; j = i++)
    if ((P[i][1] > y) !== (P[j][1] > y) && x < (P[j][0] - P[i][0]) * (y - P[i][1]) / (P[j][1] - P[i][1]) + P[i][0]) c = !c;
  return c;
}
const clamp = v => Math.max(0, Math.min(255, Math.round(v)));
const scale = (c, k) => c.map(v => clamp(v * k));

// ---------------------------------------------------------------- axonometric prisms

/**
 * General axonometric view: screen = (ox + X ax[0] + Z az[0], oy + X ax[1] + Z az[1] - Y ay). The projection direction
 * is the cross product of the two screen rows, oriented so the top face (+Y) is visible.
 */
function axo(ox, oy, ax, az, ay) {
  const P = ([x, y, z]) => [ox + x * ax[0] + z * az[0], oy + x * ax[1] + z * az[1] - y * ay];
  const r1 = [ax[0], 0, az[0]], r2 = [ax[1], -ay, az[1]];
  let d = [r1[1] * r2[2] - r1[2] * r2[1], r1[2] * r2[0] - r1[0] * r2[2], r1[0] * r2[1] - r1[1] * r2[0]];
  if (d[1] > 0) d = d.map(v => -v);
  return {P, d};
}
const rotXZ = (pts, deg, cx = 0, cz = 0) => { const a = deg * Math.PI / 180, c = Math.cos(a), s = Math.sin(a); return pts.map(([x, z]) => [cx + x * c - z * s, cz + x * s + z * c]); };
const LIGHT = (() => { const l = [-0.35, 1, -0.55], n = Math.hypot(...l); return l.map(v => v / n); })();

/** Collect the visible faces of a prism as {poly, depth, normal}; the caller paints them far to near. */
function prismFaces(v, outline, y0, y1, tag) {
  const faces = [];
  for (let i = 0; i < outline.length; i++) {
    const [ax, az] = outline[i], [bx, bz] = outline[(i + 1) % outline.length];
    const len = Math.hypot(bx - ax, bz - az), n = [(bz - az) / len, 0, -(bx - ax) / len];   // outward for CCW
    if (n[0] * v.d[0] + n[2] * v.d[2] >= -1e-6 * Math.hypot(...v.d)) continue;
    const q = [[ax, y0, az], [bx, y0, bz], [bx, y1, bz], [ax, y1, az]];
    faces.push({poly: q.map(v.P), depth: depthOf(v, q), normal: n, tag});
  }
  const top = outline.map(([x, z]) => [x, y1, z]);
  faces.push({poly: top.map(v.P), depth: depthOf(v, top) - 0.01, normal: [0, 1, 0], tag: tag + ':top'});
  return faces;
}
const depthOf = (v, Q) => Q.reduce((s, p) => s + p[0] * v.d[0] + p[1] * v.d[1] + p[2] * v.d[2], 0) / Q.length;
const shade = n => 0.52 + 0.62 * Math.max(0, n[0] * LIGHT[0] + n[1] * LIGHT[1] + n[2] * LIGHT[2]);

function paintFaces(icon, faces, colorOf) {
  faces.sort((a, b) => b.depth - a.depth).forEach(f => icon.poly(f.poly, colorOf(f)));
}

// ---------------------------------------------------------------- items

/** Spodumene concentrate: small mound of coarse, blocky cleavage chips (dense-media concentrate), not a powder cone. */
function spodumeneConcentrate() {
  const icon = new Icon(), v = axo(8, 10, [0.9, 0.45], [-0.9, 0.45], 1);
  const spod = [182, 200, 184];
  const chip = (w, d, h, deg, cx, cz, y0, tag) =>
    prismFaces(v, rotXZ([[-w / 2, -d / 2], [w / 2, -d / 2], [w / 2, d / 2], [-w / 2, d / 2]], deg, cx, cz), y0, y0 + h, tag);
  const faces = [
    ...chip(3.6, 2.6, 2.4, 25, -3.2, 1.0, 0, 'a'),
    ...chip(3.2, 2.8, 2.2, -20, 1.4, 3.4, 0, 'b'),
    ...chip(4.0, 3.0, 2.6, 10, 2.6, -2.2, 0, 'c'),
    ...chip(3.0, 2.4, 2.0, 40, -1.6, -3.6, 0, 'd'),
    ...chip(3.4, 2.8, 2.4, -15, -0.2, -0.2, 1.8, 'e'),
  ];
  paintFaces(icon, faces, f => scale(spod, shade(f.normal)));
  icon.outline(0.6, 0.88);
  return icon;
}

// ---------------------------------------------------------------- block

// Block textures follow the steel block (modern vanilla style): few large shapes, a 1 px bevel (light top / left, dark
// bottom / right), gentle value ramps and contrast, no 1 px repeating detail.

/** Tileable value noise on a cells x cells lattice over the 16 px tile, smoothstep interpolated, in [0, 1]. */
function lowFreq(seed, cells = 4) {
  const h = i => { let x = Math.imul(i + 1, 0x9E3779B1) ^ Math.imul(seed + 7, 0x85EBCA77); x ^= x >>> 15; x = Math.imul(x, 0xC2B2AE3D); x ^= x >>> 13; return (x >>> 0) / 4294967295; };
  const g = (i, j) => h(((j + cells) % cells) * cells + ((i + cells) % cells)), s = v => v * v * (3 - 2 * v), step = N / cells;
  return (x, y) => {
    const u = (x + 0.5) / step - 0.5, v = (y + 0.5) / step - 0.5, i = Math.floor(u), j = Math.floor(v), fu = s(u - i), fv = s(v - j);
    const top = g(i, j) + (g(i + 1, j) - g(i, j)) * fu, bot = g(i, j + 1) + (g(i + 1, j + 1) - g(i, j + 1)) * fu;
    return top + (bot - top) * fv;
  };
}

/** Lead shielding bricks: two courses of large cast lead bricks per block, half-bond, dry laid with thin joints. */
function leadShieldingBricks() {
  const icon = new Icon(), face = [94, 98, 107], joint = [70, 73, 81], n = lowFreq(7);
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const course = y >> 3, r = y & 7, lx = (x - (course ? 8 : 0) + N) % N;   // r 7 = bed joint, lx 0 = head joint
    if (r === 7 || lx === 0) { icon.set(x, y, joint); continue; }
    let k = 1 + 0.03 * (3 - r) / 3 + (course ? -0.015 : 0.015) + 0.03 * (n(x, y) - 0.5);
    if (r === 0) k += 0.10; else if (r === 6) k -= 0.07;                     // top / lower arris
    if (lx === 1) k += 0.05; else if (lx === 15) k -= 0.05;                  // left / right arris
    icon.set(x, y, face.map(v => clamp(v * k)));
  }
  return icon;
}

/** Steel plate: one bolted plate per block, steel-block bevel, cooler and darker than the steel block, 2 x 2 corner bolts. */
function steelPlate() {
  const icon = new Icon(), base = [70, 74, 82], n = lowFreq(11);
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    let k = 1 + 0.04 * (7.5 - y) / 7.5 + 0.03 * (n(x, y) - 0.5);
    if (x === 15 || y === 15) k -= 0.30; else if (x === 0 || y === 0) k += 0.22;
    icon.set(x, y, base.map(v => clamp(v * k)));
  }
  const at = (x, y, dk) => icon.set(x, y, icon.get(x, y).map(v => clamp(v * (1 + dk))));
  for (const [bx, by] of [[2, 2], [12, 2], [2, 12], [12, 12]]) {
    at(bx, by, 0.30); at(bx + 1, by, 0.12); at(bx, by + 1, 0.12); at(bx + 1, by + 1, -0.08);   // domed head, lit from top left
    at(bx + 2, by + 1, -0.10); at(bx + 1, by + 2, -0.10); at(bx + 2, by + 2, -0.12);          // soft shadow
  }
  return icon;
}

// ---------------------------------------------------------------- output

const OUT = {
  'item/spodumene_concentrate.png': spodumeneConcentrate,
  'block/lead_shielding_bricks.png': leadShieldingBricks,
  'block/steel_plate.png': steelPlate,
};

const check = process.argv.includes('--check');
let stale = 0;
for (const [rel, build] of Object.entries(OUT)) {
  const file = path.join(TEX, rel), data = png(build().rgba(), N, N);
  if (check) {
    if (!fs.existsSync(file) || !fs.readFileSync(file).equals(data)) { console.log('stale: ' + rel); stale++; }
  } else {
    fs.writeFileSync(file, data);
    console.log('wrote ' + rel);
  }
}
if (check && stale) process.exit(1);
