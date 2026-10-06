// Fire effect textures (docs/gameplay/fuel_fire_v1.md "火焰效果 V2", 2026-10-05).
// - textures/effect/fire_smoke (512 x 256: 4 x 2 puffs of 128 x 128, client/FireFx): soft billowing smoke puffs rendered in
//   Blender (tools/blender/fuel_smoke_v1.blend: a noise-warped sphere of Principled Volume, lit by a sun from above and a grey
//   sky, Cycles, orthographic, transparent film; puff_0..7 = noise W 3.7 i), box-filtered 256 -> 128 with alpha-weighted
//   colour, kept grey (tinted when drawn: oil smoke near black, wood smoke grey). V2.3: one sheet drawn by FireFx itself
//   instead of particle sprites (Sundial showed no translucent particles).
// - textures/effect/fire_glow (64 x 64, FuelFlames#glow, FireFx sparks): a soft round glow, white (tinted fire orange), laid
//   on the floor under flames, so a fire seen from high above (where its upright planes thin out) still lights the ground.
// - textures/block/scorch_char (32 x 32, tiles every 2 blocks, 16 px a block like the world's own textures): burnt ground
//   in pixel style, charcoal blacks with a few lighter ash pixels; drawn world aligned under burnt-out fuel (client/Scorches,
//   the shape from the burnt stains' blobs, a soft edge). _s rough, dielectric.
// - textures/block/scorch_embers_0..2 (32 x 32): single glowing pixels on transparent, about 24 each at different places,
//   drawn additively over the char while it cools (layer k fades out first as the heat drops: fewer embers, dimmer).
//   node tools/build-fire-fx-v1.mjs [puffDir] [--check]      (puffDir: tools/blender/fuel_smoke_v1_puffs)
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {png, readPng} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const A = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const args = process.argv.slice(2).filter(a => !a.startsWith('--'));
const PUFFS = args[0] || path.join(ROOT, 'tools/blender/fuel_smoke_v1_puffs');
const SMOKES = 8, S = 128;

function rng(seed) { let s = seed >>> 0; return () => { s = Math.imul(s ^ (s >>> 15), 2246822519) + 0x9e3779b9 >>> 0; s ^= s >>> 13; return (s >>> 0) / 4294967296; }; }
const out = {};

// ---- smoke puffs: one sheet ----
{
  const sheet = Buffer.alloc(S * 4 * S * 2 * 4);
  for (let i = 0; i < SMOKES; i++) {
    const src = readPng(fs.readFileSync(path.join(PUFFS, `puff_${i}.png`)));
    if (src.bpp !== 4) throw new Error("puff_" + i + " needs alpha");
    const k = src.w / S, ox = (i % 4) * S, oy = Math.floor(i / 4) * S;
    for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
      let a = 0, g = 0;
      for (let sy = 0; sy < k; sy++) for (let sx = 0; sx < k; sx++) {
        const p = ((y * k + sy) * src.w + x * k + sx) * 4, pa = src.px[p + 3] / 255;
        a += pa;
        g += pa * (0.2126 * src.px[p] + 0.7152 * src.px[p + 1] + 0.0722 * src.px[p + 2]);
      }
      const o = ((oy + y) * S * 4 + ox + x) * 4, grey = a > 0 ? Math.round(g / a) : 128;
      sheet[o] = sheet[o + 1] = sheet[o + 2] = grey;
      sheet[o + 3] = Math.round(a / (k * k) * 255);
    }
  }
  out["textures/effect/fire_smoke.png"] = png(sheet, S * 4, S * 2);
}

// ---- fire glow on the floor ----
{
  const N = 64, buf = Buffer.alloc(N * N * 4);
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const d = Math.hypot(x + 0.5 - N / 2, y + 0.5 - N / 2) / (N / 2), o = (y * N + x) * 4;
    buf[o] = buf[o + 1] = buf[o + 2] = 255;
    buf[o + 3] = Math.round(255 * Math.exp(-6 * d * d) * Math.sqrt(Math.max(0, 1 - d)));   // bright under the flame, no visible rim
  }
  out["textures/effect/fire_glow.png"] = png(buf, N, N);
}

// ---- scorch: charcoal tile (pixel style) ----
{
  const N = 32, r = rng(7001), base = Buffer.alloc(N * N * 4), spec = Buffer.alloc(N * N * 4);
  // a little low-frequency mottling (4 x 4 lattice, tileable) under per-pixel grain
  const L = 4, g = Array.from({length: L * L}, r), sm = t => t * t * (3 - 2 * t);
  const mot = (x, y) => { const u = x / N * L, v = y / N * L, i = Math.floor(u), j = Math.floor(v), fu = sm(u - i), fv = sm(v - j);
    const at = (a, b) => g[((b % L) + L) % L * L + ((a % L) + L) % L];
    return (at(i, j) * (1 - fu) + at(i + 1, j) * fu) * (1 - fv) + (at(i, j + 1) * (1 - fu) + at(i + 1, j + 1) * fu) * fv; };
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const o = (y * N + x) * 4, n = r();
    let v = 26 + 20 * mot(x, y) + 14 * (n - 0.5);
    if (n > 0.93) v = 70 + 40 * r();          // ash
    else if (n < 0.08) v = 12;                 // deep char
    const warm = n > 0.93 ? 0 : 3;             // char is faintly warm, ash neutral
    base[o] = Math.round(v + warm); base[o + 1] = Math.round(v); base[o + 2] = Math.round(v - warm); base[o + 3] = 255;
    spec[o] = 30; spec[o + 1] = 10; spec[o + 2] = 0; spec[o + 3] = 255;
  }
  out['textures/block/scorch_char.png'] = png(base, N, N);
  out['textures/block/scorch_char_s.png'] = png(spec, N, N);
}

// ---- scorch: ember layers ----
for (let k = 0; k < 3; k++) {
  const N = 32, r = rng(8101 + k * 37), buf = Buffer.alloc(N * N * 4);
  for (let e = 0; e < 24; e++) {
    const x = Math.floor(r() * N), y = Math.floor(r() * N), t = r();
    const put = (px, py, f) => { const o = (((py + N) % N) * N + (px + N) % N) * 4;
      buf[o] = 255; buf[o + 1] = Math.round(110 + 90 * t * f); buf[o + 2] = Math.round(30 + 50 * t * f); buf[o + 3] = Math.round(255 * f); };
    put(x, y, 1);
    if (r() < 0.25) put(x + (r() < 0.5 ? 1 : 0), y + (r() < 0.5 ? 0 : 1), 0.55);   // now and then a pair
  }
  out[`textures/block/scorch_embers_${k}.png`] = png(buf, N, N);
}

if (process.argv.includes('--check')) {
  let stale = 0;
  for (const [rel, data] of Object.entries(out)) {
    const p = path.join(A, rel);
    if (!fs.existsSync(p) || !fs.readFileSync(p).equals(data)) { console.error('STALE ' + rel); stale++; }
  }
  if (stale) process.exit(1);
  console.log('CHECK OK');
} else {
  for (const [rel, data] of Object.entries(out)) {
    const p = path.join(A, rel);
    fs.mkdirSync(path.dirname(p), {recursive: true});
    fs.writeFileSync(p, data);
  }
  console.log('wrote ' + Object.keys(out).length + ' files');
}
