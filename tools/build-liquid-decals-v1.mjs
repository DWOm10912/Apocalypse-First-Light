// Liquid stain textures (client/LiquidDecals, 2026-10-05; first user: the fuel nozzle's jet): pale greyscale stains with
// soft transparent edges, tinted with the liquid's colour at runtime, so one set serves every liquid.
// - liquid_splat_0..2: a floor / ceiling stain, a metaball blob (a main pool and a few lobes) with a few loose drops; a
//   slightly darker rim (a dried edge) and a faint low-frequency variation inside, nothing at pixel scale.
// - liquid_sheet_0..1 (32 x 64): a wall run, a sheet of four or five uneven streaks of different widths and lengths over a
//   faint film, fading in at the top (under the blob at the hit) and thinning out downward, each streak ending in a small
//   bead; drawn stretched over the run's current length (client/LiquidDecals). 2026-10-05: replaced liquid_run_* (one
//   long thin thread, "ugly" to the user), which had replaced liquid_streak_* (a fixed blob-and-run).
// - liquid_film (32 x 32, tiles every 2 blocks, opaque): the surface of the floor pools (client/FuelPuddleMesher), a pale
//   grey with a faint tileable low-frequency variation; the pool's shape and alpha come from its mesh.
// LabPBR _s: wet and smooth (smoothness 215, F0 12), never emissive (alpha 255). No _n (flat).
//   node tools/build-liquid-decals-v1.mjs [--check]
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {png} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const OUT = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/textures/block');
const N = 32;   // splats are N x N; runs W x H
const W = 32, H = 64;

function rng(seed) { let s = seed >>> 0; return () => { s = Math.imul(s ^ (s >>> 15), 2246822519) + 0x9e3779b9 >>> 0; s ^= s >>> 13; return (s >>> 0) / 4294967296; }; }
const smooth = (a, b, x) => { const t = Math.min(1, Math.max(0, (x - a) / (b - a))); return t * t * (3 - 2 * t); };
function noise(seed) {   // low-frequency value noise over the 32 px tile (a 4 x 4 lattice)
  const r = rng(seed), g = Array.from({length: 25}, r);
  return (x, y) => { const u = x / N * 4, v = y / N * 4, i = Math.min(3, Math.floor(u)), j = Math.min(3, Math.floor(v)), fu = smooth(0, 1, u - i), fv = smooth(0, 1, v - j);
    const a = g[j * 5 + i], b = g[j * 5 + i + 1], c = g[(j + 1) * 5 + i], d = g[(j + 1) * 5 + i + 1];
    return (a + (b - a) * fu) * (1 - fv) + (c + (d - c) * fu) * fv; };
}
// metaball field of circles [cx, cy, r]: inside where > 1, sharp falloff so lobes and drops stay apart
const field = (balls, x, y) => balls.reduce((f, [cx, cy, r]) => f + (r * r / ((x - cx) ** 2 + (y - cy) ** 2 + 0.25)) ** 2, 0);   // (r/d)^4: tight joins

function paint(balls, seed, w = N, h = N, fadeTop = 0, film = null) {
  const base = Buffer.alloc(w * h * 4), spec = Buffer.alloc(w * h * 4), n = noise(seed);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    let f = 0;   // 3 x 3 supersampling for a clean edge
    for (let sy = 0; sy < 3; sy++) for (let sx = 0; sx < 3; sx++) f += field(balls, x + (sx + 0.5) / 3, y + (sy + 0.5) / 3) / 9;
    const cover = Math.max(smooth(0.8, 1.2, f), film ? film(x, y) : 0), rim = smooth(1.0, 1.25, f) * (1 - smooth(1.4, 2.2, f));
    const k = (y * w + x) * 4, grey = Math.round(232 - 26 * rim + 14 * (n(x % N, y % N) - 0.5));
    base[k] = base[k + 1] = base[k + 2] = grey;
    base[k + 3] = Math.round(255 * cover * (0.78 + 0.14 * rim) * (fadeTop ? smooth(0, fadeTop, y) : 1));
    spec[k] = 215; spec[k + 1] = 12; spec[k + 2] = 0; spec[k + 3] = 255;
  }
  return [png(base, w, h), png(spec, w, h)];
}

function splat(seed) {
  const r = rng(seed), balls = [[16 + (r() - 0.5) * 2, 16 + (r() - 0.5) * 2, 6.2 + r() * 1.2]];
  const lobes = 3 + Math.floor(r() * 3);
  for (let i = 0; i < lobes; i++) { const a = r() * 2 * Math.PI, d = 6 + r() * 3.5; balls.push([16 + Math.cos(a) * d, 16 + Math.sin(a) * d, 2.2 + r() * 1.6]); }
  for (let i = 0; i < 4; i++) { const a = r() * 2 * Math.PI, d = 11 + r() * 3; balls.push([16 + Math.cos(a) * d, 16 + Math.sin(a) * d, 0.9 + r() * 0.6]); }
  return paint(balls, seed + 7);
}

// streaks by distance to their wavering centre lines (not metaballs: overlapping balls along a line thicken it)
function sheet(seed) {
  const r = rng(seed), n = noise(seed + 11), streaks = [];
  const count = 4 + Math.floor(r() * 2);
  for (let i = 0; i < count; i++) {
    const pts = [], end = 28 + r() * 32, width = 0.9 + r() * 0.9;
    let x = 5 + (i + 0.5) * 22 / count + (r() - 0.5) * 2.5;
    for (let y = 0; y <= end; y += 0.5) { x += (r() - 0.5) * 0.25; pts.push([x, y]); }
    streaks.push({pts, end, width, bead: [x, end + 0.6, width * 1.6]});
  }
  const base = Buffer.alloc(W * H * 4), spec = Buffer.alloc(W * H * 4);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const px = x + 0.5, py = y + 0.5;
    let cover = 0.24 * (1 - smooth(6, 13, Math.abs(px - 16))) * (1 - smooth(8, 36, py));   // a faint film, thinning out downward
    for (const st of streaks) {
      let d = Infinity;
      for (const [cx, cy] of st.pts) d = Math.min(d, Math.hypot(px - cx, py - cy));
      const w = st.width * (1 - 0.3 * py / H);
      cover = Math.max(cover, 0.85 * (1 - smooth(w - 0.5, w + 0.6, d)));
      const [bx, by, br] = st.bead;
      cover = Math.max(cover, 0.85 * (1 - smooth(br - 0.5, br + 0.6, Math.hypot(px - bx, py - by))));
    }
    cover *= smooth(0, 6, py);   // fades in under the blob at the hit
    const k = (y * W + x) * 4, grey = Math.round(226 + 14 * (n(x % N, y % N) - 0.5));
    base[k] = base[k + 1] = base[k + 2] = grey;
    base[k + 3] = Math.round(255 * cover);
    spec[k] = 215; spec[k + 1] = 12; spec[k + 2] = 0; spec[k + 3] = 255;
  }
  return [png(base, W, H), png(spec, W, H)];
}

function film(seed) {   // tileable: the lattice wraps
  const r = rng(seed), g = Array.from({length: 16}, r), at = (i, j) => g[(j & 3) * 4 + (i & 3)];
  const base = Buffer.alloc(N * N * 4), spec = Buffer.alloc(N * N * 4);
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const u = x / N * 4, v = y / N * 4, i = Math.floor(u), j = Math.floor(v), fu = smooth(0, 1, u - i), fv = smooth(0, 1, v - j);
    const val = (at(i, j) + (at(i + 1, j) - at(i, j)) * fu) * (1 - fv) + (at(i, j + 1) + (at(i + 1, j + 1) - at(i, j + 1)) * fu) * fv;
    const k = (y * N + x) * 4, grey = Math.round(228 + 16 * (val - 0.5));
    base[k] = base[k + 1] = base[k + 2] = grey; base[k + 3] = 255;
    spec[k] = 215; spec[k + 1] = 12; spec[k + 2] = 0; spec[k + 3] = 255;
  }
  return [png(base, N, N), png(spec, N, N)];
}

const outputs = [];
{ const [b, s] = film(3000); outputs.push(['liquid_film.png', b], ['liquid_film_s.png', s]); }
for (let i = 0; i < 3; i++) { const [b, s] = splat(1000 + i * 37); outputs.push([`liquid_splat_${i}.png`, b], [`liquid_splat_${i}_s.png`, s]); }
for (let i = 0; i < 2; i++) { const [b, s] = sheet(2000 + i * 53); outputs.push([`liquid_sheet_${i}.png`, b], [`liquid_sheet_${i}_s.png`, s]); }
if (process.argv.includes('--check')) {
  for (const [f, d] of outputs) { const p = path.join(OUT, f); if (!fs.existsSync(p) || !fs.readFileSync(p).equals(d)) throw new Error('stale ' + f); }
  console.log('CHECK OK');
} else {
  for (const [f, d] of outputs) fs.writeFileSync(path.join(OUT, f), d);
  console.log('wrote ' + outputs.length + ' files');
}
