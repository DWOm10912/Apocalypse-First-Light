// Bullet hole decals (client/BulletHoles, docs/native_guns/native_bullet_holes_v1.md). V1.1 (2026-10-05, HD + PBR; the
// first 32 px holes looked blurry close up, user): one block-atlas sprite textures/block/bullet_holes (384 x 384 = 3 x 3
// holes of 128 x 128; in the atlas it gets mipmaps, so a small far hole does not shimmer), drawn untinted over the hit face
// (0.16 / 0.18 block across). Rows: steel, stone (concrete, brick, earth and anything else), wood. Columns: three variants.
// Each hole is built as layers over a height field (1 = the surface, 0 = the bottom of the hole), 3 x 3 supersampled:
// - steel: a black hole; a lip of metal pushed in, its edge torn into petals by short radial cracks; a ring of bare
//   brushed steel where the paint flaked off (ragged edge, a few loose chips beyond it) with the paint's thin dark edge;
//   on some a faint ring of powder soot.
// - stone: a small dark hole in a bowl of freshly broken, lighter stone with grit; flakes round the rim; a few thin jagged
//   cracks; a pale dust halo.
// - wood: a dark hole in a pit of fresh, paler wood with grain; torn fibres sticking out along the grain (u); a darker bruise.
// LabPBR: _s R smoothness, G F0 (bare and torn steel 230 = iron; else dielectric), A 255 (no emission). _n DirectX
// (red right, green down), from the height field's slopes; B ambient occlusion (dark down the hole); A height (0 = 25 %
// deep). Transparent texels: flat, no occlusion, full height.
//   node tools/build-bullet-holes-v1.mjs [--check]
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {png} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const OUT = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/textures/block');
const N = 128, SS = 3, W = N * 3, H = N * 3, BUMP = 5.0;

function rng(seed) { let s = seed >>> 0; return () => { s = Math.imul(s ^ (s >>> 15), 2246822519) + 0x9e3779b9 >>> 0; s ^= s >>> 13; return (s >>> 0) / 4294967296; }; }
const smooth = (a, b, x) => { const t = Math.min(1, Math.max(0, (x - a) / (b - a))); return t * t * (3 - 2 * t); };
const mix = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);
// value noise on an integer lattice (smooth), any frequency
function noise2(seed) {
  const h = (i, j) => { let k = Math.imul(i, 374761393) ^ Math.imul(j, 668265263) ^ Math.imul(seed, 2147483647); k = Math.imul(k ^ (k >>> 13), 1274126177); return ((k ^ (k >>> 16)) >>> 0) / 4294967295; };
  return (x, y) => { const i = Math.floor(x), j = Math.floor(y), fx = smooth(0, 1, x - i), fy = smooth(0, 1, y - j);
    return (h(i, j) * (1 - fx) + h(i + 1, j) * fx) * (1 - fy) + (h(i, j + 1) * (1 - fx) + h(i + 1, j + 1) * fx) * fy; };
}
// a ragged radius round the hole: k lobes of random size, smoothly joined, -1..1
function ragged(seed, k) {
  const r = rng(seed), g = Array.from({length: k}, () => r() * 2 - 1);
  return a => { const u = (a / (2 * Math.PI) + 1) % 1 * k, i = Math.floor(u), f = smooth(0, 1, u - i); return g[i % k] * (1 - f) + g[(i + 1) % k] * f; };
}
// distance from p to the polyline pts
function lineDist(px, py, pts) {
  let best = 1e9;
  for (let i = 0; i + 1 < pts.length; i++) {
    const [ax, ay] = pts[i], [bx, by] = pts[i + 1], dx = bx - ax, dy = by - ay, t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)));
    best = Math.min(best, Math.hypot(px - ax - dx * t, py - ay - dy * t));
  }
  return best;
}
function jagged(r, x0, y0, angle, len, steps, wobble) {   // a crack: a polyline out at angle, wobbling
  const pts = [[x0, y0]];
  let a = angle, x = x0, y = y0;
  for (let i = 0; i < steps; i++) { a += (r() - 0.5) * wobble; x += Math.cos(a) * len / steps; y += Math.sin(a) * len / steps; pts.push([x, y]); }
  return pts;
}

// a sample: colour (0..255), alpha, height, metal, smoothness, ao; layers composite over (straight alpha)
const EMPTY = () => ({c: [0, 0, 0], a: 0, h: 1, m: 0, s: 0.1, ao: 1});
function over(dst, c, a, extra = {}) {
  const oa = a + dst.a * (1 - a);
  if (oa > 0) dst.c = dst.c.map((v, i) => (c[i] * a + v * dst.a * (1 - a)) / oa);
  dst.a = oa;
  if (extra.h !== undefined) dst.h = dst.h + (extra.h - dst.h) * a;
  if (extra.m !== undefined) dst.m = dst.m + (extra.m - dst.m) * a;
  if (extra.s !== undefined) dst.s = dst.s + (extra.s - dst.s) * a;
  if (extra.ao !== undefined) dst.ao = dst.ao + (extra.ao - dst.ao) * a;
}

function steel(seed, soot) {
  const r = rng(seed), lip = ragged(seed + 1, 9), bare = ragged(seed + 2, 13), fine = noise2(seed + 3), brush = noise2(seed + 4), chipN = noise2(seed + 5);
  const R0 = 7 + r() * 1.2, R1 = R0 + 4.5 + r() * 1.5, R2 = 19 + r() * 4, petals = Array.from({length: 5 + Math.floor(r() * 3)}, () => r() * Math.PI * 2);
  const brushAngle = r() * Math.PI;
  return (x, y) => {
    const d = Math.hypot(x, y), a = Math.atan2(y, x), out = EMPTY();
    const r2 = R2 + 5 * bare(a) + 2.2 * (fine(x * 0.35, y * 0.35) - 0.5);
    if (soot) over(out, [32, 30, 28], 0.28 * (1 - smooth(r2, r2 + 16, d)) * (0.7 + 0.3 * fine(x * 0.2 + 9, y * 0.2)));
    // loose paint chips beyond the ring
    if (d > r2 && d < r2 + 10 && chipN(x * 0.45, y * 0.45) > 0.8) {
      const v = 150 + 40 * brush(x * 0.1, y * 2);
      over(out, [v, v + 2, v + 6], 0.92, {m: 1, s: 0.6, h: 0.97});
    }
    // the paint's thin dark edge, the bare ring
    over(out, [44, 44, 46], 0.5 * (1 - smooth(0.4, 1.4, Math.abs(d - r2))), {h: 0.96});
    const ring = 1 - smooth(r2 - 0.6, r2 + 0.6, d);
    if (ring > 0) {
      const along = x * Math.cos(brushAngle) + y * Math.sin(brushAngle), across = -x * Math.sin(brushAngle) + y * Math.cos(brushAngle);
      const v = 148 + 48 * brush(along * 0.08, across * 1.6) + 18 * (fine(x * 0.6, y * 0.6) - 0.5);
      over(out, [v, v + 2, v + 6], 0.97 * ring, {m: 1, s: 0.62, h: 0.86 + 0.14 * smooth(R1, r2, d), ao: 0.85 + 0.15 * smooth(R1, r2, d)});
    }
    // the lip, pushed in and torn into petals
    const rh = R0 + 1.3 * lip(a), r1 = R1 + 1.0 * lip(a + 1.7);
    const lipT = 1 - smooth(r1 - 0.6, r1 + 0.6, d);
    if (lipT > 0) {
      const t = Math.min(1, Math.max(0, (d - rh) / (r1 - rh)));
      let crack = 0;
      for (const p of petals) { let da = Math.abs(((a - p) % (2 * Math.PI) + 3 * Math.PI) % (2 * Math.PI) - Math.PI) * d; crack = Math.max(crack, (1 - smooth(0.3, 1.0, da)) * (1 - smooth(r1, r1 + 3, d))); }
      const v = 58 + 22 * t + 12 * (fine(x * 0.8, y * 0.8) - 0.5);
      over(out, mix([v, v + 1, v + 4], [20, 20, 22], crack), lipT, {m: 1, s: 0.45, h: 0.3 + 0.55 * t - 0.2 * crack, ao: 0.35 + 0.5 * t});
    }
    // the hole
    const hole = 1 - smooth(rh - 0.6, rh + 0.6, d);
    if (hole > 0) over(out, [9, 9, 10], hole, {h: 0, m: 0.2, s: 0.05, ao: 0.08});
    return out;
  };
}

function stone(seed) {
  const r = rng(seed), edge = ragged(seed + 1, 10), holeE = ragged(seed + 2, 7), grit = noise2(seed + 3), flakeN = noise2(seed + 4), haloN = noise2(seed + 5);
  const Rh = 5 + r() * 1.2, Rc = 13 + r() * 3;
  const cracks = Array.from({length: 2 + Math.floor(r() * 3)}, () => {
    const a = r() * Math.PI * 2, rc = Rc + 3 * edge(a) - 1;
    return jagged(r, Math.cos(a) * rc, Math.sin(a) * rc, a, 8 + r() * 16, 6, 0.9);
  });
  return (x, y) => {
    const d = Math.hypot(x, y), a = Math.atan2(y, x), out = EMPTY();
    const rc = Rc + 3.5 * edge(a) + 1.5 * (grit(x * 0.3, y * 0.3) - 0.5);
    over(out, [205, 200, 192], 0.32 * (1 - smooth(rc, rc + 26, d)) * (0.6 + 0.4 * haloN(x * 0.15, y * 0.15)));
    let crack = 0;
    for (const c of cracks) crack = Math.max(crack, 1 - smooth(0.35, 1.05, lineDist(x, y, c)));
    if (crack > 0) over(out, [42, 40, 38], 0.85 * crack, {h: 0.82});
    if (d > rc - 1 && d < rc + 6 && flakeN(x * 0.55, y * 0.55) > 0.66) {   // flakes thrown round the rim
      const v = 140 + 25 * grit(x * 1.3, y * 1.3);
      over(out, [v, v - 2, v - 5], 0.9, {h: 0.97, s: 0.15});
    }
    const crater = 1 - smooth(rc - 0.6, rc + 0.6, d);
    if (crater > 0) {
      const t = Math.min(1, d / rc), g = grit(x * 1.4, y * 1.4), speck = grit(x * 3.1 + 40, y * 3.1) > 0.78 ? -45 : 0;
      const v = 118 + 30 * t + 22 * (g - 0.5) + speck;
      over(out, [v, v - 2, v - 5], 0.96 * crater, {h: 0.25 + 0.75 * Math.pow(t, 1.5) + 0.05 * (g - 0.5), s: 0.12, ao: 0.4 + 0.6 * t});
    }
    const rh = Rh + 1.0 * holeE(a), hole = 1 - smooth(rh - 0.6, rh + 0.6, d);
    if (hole > 0) over(out, [17, 16, 15], hole, {h: 0, s: 0.05, ao: 0.08});
    return out;
  };
}

function wood(seed) {
  const r = rng(seed), grainN = noise2(seed + 1), torn = ragged(seed + 2, 8), fineN = noise2(seed + 3);
  const Rh = 5.5 + r() * 1.2;
  const fibres = Array.from({length: 10 + Math.floor(r() * 5)}, () => {
    const side = r() < 0.5 ? -1 : 1, y0 = (r() - 0.5) * Rh * 1.7, len = 5 + r() * 13;
    return {side, len, x0: side * Rh * 0.9, w: 0.45 + r() * 0.8, pts: jagged(r, side * Rh * 0.9, y0, side < 0 ? Math.PI : 0, len, 5, 0.5)};
  });
  return (x, y) => {
    const e = Math.hypot(x / 1.7, y), a = Math.atan2(y, x), out = EMPTY();
    over(out, [78, 56, 36], 0.45 * (1 - smooth(Rh * 2.2, Rh * 3.4 + 2 * torn(a), e)), {h: 0.93});
    const pitR = Rh * 1.9 + 1.5 * torn(a), pit = 1 - smooth(pitR - 0.6, pitR + 0.6, e);
    if (pit > 0) {
      const g = 0.5 + 0.5 * Math.sin(y * 1.25 + 3 * grainN(x * 0.05, y * 0.4));
      const v = [150 + 20 * g, 116 + 16 * g, 78 + 10 * g];
      over(out, v, 0.95 * pit, {h: 0.5 + 0.4 * Math.min(1, e / pitR), s: 0.15, ao: 0.45 + 0.5 * Math.min(1, e / pitR)});
    }
    let fibre = 0;
    for (const f of fibres) {   // tapering towards the torn end
      const reach = Math.min(1, Math.abs(x - f.x0) / f.len), w = f.w * (1 - 0.6 * reach);
      fibre = Math.max(fibre, (1 - smooth(w * 0.5, w * 0.5 + 0.8, lineDist(x, y, f.pts))) * (1 - 0.5 * reach));
    }
    if (fibre > 0) {
      const v = 200 + 25 * (fineN(x * 0.7, y * 0.7) - 0.5);
      over(out, [v, v * 0.86, v * 0.62], 0.95 * fibre, {h: 1.0, s: 0.2, ao: 1});
    }
    const holeD = Math.hypot(x / 1.25, y), rh = Rh + 0.8 * torn(a + 2), hole = 1 - smooth(rh - 0.6, rh + 0.6, holeD);
    if (hole > 0) over(out, [22, 15, 10], hole, {h: 0, s: 0.05, ao: 0.08});
    return out;
  };
}

const cells = [];
for (let v = 0; v < 3; v++) cells.push([0, v, steel(101 + v * 17, v !== 1)]);
for (let v = 0; v < 3; v++) cells.push([1, v, stone(301 + v * 17)]);
for (let v = 0; v < 3; v++) cells.push([2, v, wood(501 + v * 17)]);

const base = Buffer.alloc(W * H * 4), spec = Buffer.alloc(W * H * 4), nrm = Buffer.alloc(W * H * 4);
for (const [row, col, fn] of cells) {
  const height = new Float64Array(N * N);
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    let cr = 0, cg = 0, cb = 0, a = 0, h = 0, m = 0, s = 0, ao = 0;
    for (let sy = 0; sy < SS; sy++) for (let sx = 0; sx < SS; sx++) {
      const p = fn(x + (sx + 0.5) / SS - N / 2, y + (sy + 0.5) / SS - N / 2);
      cr += p.c[0] * p.a; cg += p.c[1] * p.a; cb += p.c[2] * p.a; a += p.a; h += p.h; m += p.m * p.a; s += p.s * p.a; ao += p.ao;
    }
    const k = SS * SS, o = ((row * N + y) * W + col * N + x) * 4;
    height[y * N + x] = h / k;
    base[o] = a > 0 ? Math.round(cr / a) : 0; base[o + 1] = a > 0 ? Math.round(cg / a) : 0; base[o + 2] = a > 0 ? Math.round(cb / a) : 0;
    base[o + 3] = Math.round(a / k * 255);
    const metal = a > 0 ? m / a : 0, sm = a > 0 ? s / a : 0.1;
    spec[o] = Math.round(sm * 255); spec[o + 1] = metal > 0.5 ? 230 : metal > 0 ? 40 : 18; spec[o + 2] = 0; spec[o + 3] = 255;
    nrm[o + 2] = Math.round(Math.min(1, ao / k) * 255);
  }
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {   // slopes of the height field (DirectX: green points down)
    const hx = (height[y * N + Math.min(N - 1, x + 1)] - height[y * N + Math.max(0, x - 1)]) / 2;
    const hy = (height[Math.min(N - 1, y + 1) * N + x] - height[Math.max(0, y - 1) * N + x]) / 2;
    let nx = -hx * BUMP, ny = -hy * BUMP, nz = 1;
    const l = Math.hypot(nx, ny, nz);
    nx /= l; ny /= l;
    const o = ((row * N + y) * W + col * N + x) * 4;
    nrm[o] = Math.round((nx * 0.5 + 0.5) * 255); nrm[o + 1] = Math.round((ny * 0.5 + 0.5) * 255);
    nrm[o + 3] = Math.round(height[y * N + x] * 255);
  }
}
const files = {'bullet_holes.png': png(base, W, H), 'bullet_holes_s.png': png(spec, W, H), 'bullet_holes_n.png': png(nrm, W, H)};
if (process.argv.includes('--check')) {
  for (const [name, data] of Object.entries(files)) {
    const p = path.join(OUT, name);
    if (!fs.existsSync(p) || !fs.readFileSync(p).equals(data)) { console.error('STALE ' + name); process.exit(1); }
  }
  console.log('CHECK OK');
} else {
  for (const [name, data] of Object.entries(files)) fs.writeFileSync(path.join(OUT, name), data);
  console.log('wrote ' + Object.keys(files).join(', '));
}
