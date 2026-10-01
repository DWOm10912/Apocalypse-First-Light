#!/usr/bin/env node
// AFL Material System V1 textures (docs/gameplay/material_system_v1.md).
// 16x16 textures built from small geometric shapes, flat low-frequency shading (no noise, stains or text): the spodumene
// concentrate icon and the lead shielding bricks block. Steel billet, lead brick, electrolytic nickel, tungsten filament
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

/** Lead shielding bricks: staggered courses of chevron-jointed cast lead bricks, dry laid (thin dark joints). */
function leadShieldingBricks() {
  const icon = new Icon();
  const face = [104, 108, 116], lit = [120, 124, 132], low = [90, 94, 102], joint = [60, 63, 70];
  const tone = [0, 3, -3, 2, -2, 4, 1, -4];                          // per-brick, low frequency only
  for (let course = 0; course < 4; course++) {
    const y0 = course * 4, shift = course % 2 ? 4 : 0;
    for (let r = 0; r < 4; r++) for (let x = 0; x < N; x++) {
      const y = y0 + r;
      if (r === 3) { icon.set(x, y, joint); continue; }              // bed joint
      const lx = (x - shift + N) % N, chev = r === 1 ? 1 : 0;         // '>' shaped head joint
      const k = (lx - chev + N) % 8;
      if (k === 0) { icon.set(x, y, joint); continue; }
      const brick = (Math.floor(((lx - chev + N) % N) / 8) + course * 2) % tone.length;
      const base = r === 0 ? lit : r === 2 ? low : face;
      icon.set(x, y, base.map(v => clamp(v + tone[brick])));
    }
  }
  return icon;
}

// ---------------------------------------------------------------- output

const OUT = {
  'item/spodumene_concentrate.png': spodumeneConcentrate,
  'block/lead_shielding_bricks.png': leadShieldingBricks,
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
