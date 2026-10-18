// Rain V1 splash sheet (docs/rendering/rain_v1.md, 2026-10-17): textures/effect/rain_splash.png, 256 x 64, four 64 x 64
// cells, white with straight alpha (tinted by the light when drawn, client/rain/RainSplashes):
// - cell 0, 1: the crown of a drop hitting a hard surface, seen from the side (the base at the bottom middle): a low
//   flat mist and a fan of thin sheets thrown up and out, a bead at each tip; two seeds.
// - cell 2: a ripple ring on water seen from above (an outer ring and a fainter inner one).
// - cell 3: a soft round droplet (the beads thrown off a splash).
//   node tools/build-rain-fx-v1.mjs [--check]
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {png} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const A = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const C = 64, W = C * 4, H = C;

function rng(seed) { let s = seed >>> 0; return () => { s = Math.imul(s ^ (s >>> 15), 2246822519) + 0x9e3779b9 >>> 0; s ^= s >>> 13; return (s >>> 0) / 4294967296; }; }
const alpha = new Float32Array(W * H);
// coverage add (screen blend of alphas) at a cell pixel, 4 x 4 supersampled by the shapes below
function put(cell, x, y, a) {
  if (x < 0 || y < 0 || x >= C || y >= C || a <= 0) return;
  const i = y * W + cell * C + x;
  alpha[i] = 1 - (1 - alpha[i]) * (1 - Math.min(1, a));
}
// a field over a cell: f(px, py) -> alpha, averaged over 4 x 4 samples per pixel
function field(cell, f) {
  for (let y = 0; y < C; y++) for (let x = 0; x < C; x++) {
    let s = 0;
    for (let j = 0; j < 4; j++) for (let i = 0; i < 4; i++) s += f(x + (i + 0.5) / 4, y + (j + 0.5) / 4);
    put(cell, x, y, s / 16);
  }
}
function segDist(px, py, ax, ay, bx, by) {
  const dx = bx - ax, dy = by - ay, l2 = dx * dx + dy * dy;
  const t = l2 > 0 ? Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / l2)) : 0;
  return [Math.hypot(px - ax - dx * t, py - ay - dy * t), t];
}

// ---- crowns ----
for (let cell = 0; cell < 2; cell++) {
  const r = rng(0x5eed + cell * 977), base = 59, cx = 32;
  // the flat mist where the drop broke up
  field(cell, (x, y) => {
    const u = (x - cx) / 22, v = (y - base) / 3.8;
    const d = u * u + v * v;
    return y > base + 2 ? 0 : 0.34 * Math.exp(-d * 2.0);
  });
  // the fan: thin short sheets thrown out, a small bead at some tips, a few loose droplets above
  const n = 11 + Math.floor(r() * 4);
  for (let k = 0; k < n; k++) {
    const side = k / (n - 1) * 2 - 1;
    const ang = (side * 66 + (r() - 0.5) * 16) * Math.PI / 180;
    const len = 7 + r() * 13 * (1 - 0.4 * Math.abs(side));
    const ax = cx + side * 8, ay = base - 1;
    const mx = ax + Math.sin(ang) * len * 0.6, my = ay - Math.cos(ang) * len * 0.6;
    const bend = ang * 1.2, bx = mx + Math.sin(bend) * len * 0.4, by = my - Math.cos(bend) * len * 0.4;
    const a0 = 0.32 + r() * 0.22, bead = r() < 0.6 ? 0.55 + r() * 0.5 : 0;
    field(cell, (x, y) => {
      const [d1, t1] = segDist(x, y, ax, ay, mx, my), [d2, t2] = segDist(x, y, mx, my, bx, by);
      const d = Math.min(d1, d2), t = d1 < d2 ? t1 * 0.6 : 0.6 + t2 * 0.4;
      const half = 0.8 * (1 - t) + 0.25;
      const sheet = d < half + 0.5 ? a0 * (1 - t * 0.7) * Math.min(1, (half + 0.5 - d) / 0.8) : 0;
      const bd = Math.hypot(x - bx, y - by);
      const tip = bead > 0 && bd < bead + 0.6 ? 0.7 * Math.min(1, (bead + 0.6 - bd) / 0.8) : 0;
      return Math.max(sheet, tip);
    });
  }
  for (let k = 0; k < 9; k++) {
    const ang = (r() - 0.5) * 2.4, dist = 12 + r() * 16, px = cx + Math.sin(ang) * dist * 1.1, py = base - 2 - Math.cos(ang) * dist * 0.9;
    const rad = 0.5 + r() * 0.5, a0 = 0.45 + r() * 0.3;
    field(cell, (x, y) => { const d = Math.hypot(x - px, y - py); return d < rad + 0.6 ? a0 * Math.min(1, (rad + 0.6 - d) / 0.8) : 0; });
  }
}

// ---- ripple ----
field(2, (x, y) => {
  const d = Math.hypot(x - 32, y - 32);
  return 0.9 * Math.exp(-((d - 26) ** 2) / (2 * 1.6 * 1.6)) + 0.4 * Math.exp(-((d - 17) ** 2) / (2 * 1.2 * 1.2));
});

// ---- droplet ----
field(3, (x, y) => {
  const d = Math.hypot(x - 32, y - 32);
  return d > 15 ? 0 : Math.exp(-(d * d) / (2 * 5.5 * 5.5));
});

const rgba = Buffer.alloc(W * H * 4);
for (let i = 0; i < W * H; i++) {
  rgba[i * 4] = rgba[i * 4 + 1] = rgba[i * 4 + 2] = 255;
  rgba[i * 4 + 3] = Math.round(Math.min(1, alpha[i]) * 255);
}
const out = {'textures/effect/rain_splash.png': png(rgba, W, H)};

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
