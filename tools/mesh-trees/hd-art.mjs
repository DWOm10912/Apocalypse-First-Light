// Shared 240-texel texture helpers (AFL's man-made density, 2026-10-12): periodic value noise, periodic plate cells,
// smooth steps, and the supersampled rasteriser that writes the colour and its LabPBR _s / _n. Used by
// tools/build-wood-blocks-v1.mjs (Wood Building Blocks V1); tools/mesh-trees/bark-art.mjs borrows hash / smooth / clamp /
// mix for its 32 px bark (a 240 px bark drawn with these was rejected in game 2026-10-12, mesh_trees_p4_formal.md W).
import {png} from '../cube-slab-mesh-lib.mjs';

/** Texels per block (240 = 16 x 15: mip level 4 stays whole), supersampling per axis, millimetres per texel. */
export const N = 240, SS = 3, MM = 1000 / N;
export const hash = (a, b, c) => { let h = Math.imul(a | 0, 374761393) ^ Math.imul(b | 0, 668265263) ^ Math.imul(c | 0, 2147483647); h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };
export const smooth = t => t * t * (3 - 2 * t), clamp = (v, a, b) => Math.max(a, Math.min(b, v));
export const sstep = (a, b, v) => smooth(clamp((v - a) / (b - a), 0, 1));
export const mul = (c, t) => c.map(v => v * t), mix = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);
/** Periodic value noise (period N on both axes), cells cx x cy texels (both divide N), -1..1. */
export function vn(x, y, seed, cx, cy = cx) {
  const nx = N / cx, ny = N / cy, xi = Math.floor(x / cx), yi = Math.floor(y / cy);
  const h = (i, j) => hash(((i % nx) + nx) % nx, ((j % ny) + ny) % ny, seed);
  const tx = smooth(x / cx - xi), ty = smooth(y / cy - yi);
  const a = h(xi, yi) + (h(xi + 1, yi) - h(xi, yi)) * tx, b = h(xi, yi + 1) + (h(xi + 1, yi + 1) - h(xi, yi + 1)) * tx;
  return 2 * (a + (b - a) * ty) - 1;
}
export const wrapD = (a, n = N) => { const d = ((a % n) + n) % n; return Math.min(d, n - d); };

/**
 * Periodic plate cells (jittered centres on a cellsX x cellsY grid over the tile, wrapping both ways), x, y in texels:
 * the nearest cell (id, wrapped grid index i / j, centre in texels) and the distance in texels to its border, overall
 * (edge), to its upright borders (edgeV: the bisector's normal more across than along) and to its level ones (edgeH).
 * Each is a minimum of distances to straight bisectors, so it is continuous inside a cell and 0 on its border.
 */
export function cells(x, y, cellsX, cellsY, seed, jitter = 0.8) {
  const cw = N / cellsX, ch = N / cellsY, qx = x / cw, qy = y / ch, gx = Math.floor(qx), gy = Math.floor(qy);
  const px = [], py = [], ids = [];
  let best = 1e9, bi = 0;
  for (let j = -2; j <= 2; j++) for (let i = -2; i <= 2; i++) {
    const ci = gx + i, cj = gy + j, wi = ((ci % cellsX) + cellsX) % cellsX, wj = ((cj % cellsY) + cellsY) % cellsY;
    const cx = ci + 0.5 + jitter * (hash(wi, wj, seed) - 0.5), cy = cj + 0.5 + jitter * (hash(wi, wj, seed + 1) - 0.5);
    const d = (qx - cx) ** 2 + (qy - cy) ** 2;
    if (d < best) { best = d; bi = px.length; }
    px.push(cx); py.push(cy); ids.push([wi, wj]);
  }
  const ax = px[bi], ay = py[bi];
  let edge = 1e9, edgeV = 1e9, edgeH = 1e9;
  for (let k = 0; k < px.length; k++) {
    if (k === bi) continue;
    const dx = px[k] - ax, dy = py[k] - ay, len = Math.hypot(dx, dy);
    // grid-space distance to the bisector, then texels: the bisector's gradient in texel space
    const f = (((ax + px[k]) / 2 - qx) * dx + ((ay + py[k]) / 2 - qy) * dy) / len;
    const gxn = dx / len / cw, gyn = dy / len / ch, e = f / Math.hypot(gxn, gyn);
    if (e < edge) edge = e;
    if (Math.abs(gxn) >= Math.abs(gyn)) { if (e < edgeV) edgeV = e; } else if (e < edgeH) edgeH = e;
  }
  const [i, j] = ids[bi];
  return {id: j * cellsX + i, i, j, cx: ax * cw, cy: ay * ch, edge, edgeV, edgeH};
}

/**
 * Rasterises f(x, y) -> {c: [r, g, b], s: smoothness 0..255, p: porosity 0..64, h: height (mm, 0 = the face, below is
 * negative), ao: 0..1}, supersampled SS x SS, slopes wrapping (tiling textures; an end texture's border is never seen).
 * _s: R smoothness, G F0 10 (dielectric), B porosity. _n: DirectX normal from the height, B ambient occlusion, A height
 * (255 + h / 250 x 255) or, with height false, 255 everywhere (a curved mesh must not be displaced by parallax).
 * Returns {col: the RGBA buffer, maps: [colour, _s, _n] PNGs}.
 */
export function render(f, {height = true} = {}) {
  const col = Buffer.alloc(N * N * 4), spec = Buffer.alloc(N * N * 4), nrm = Buffer.alloc(N * N * 4), H = new Float64Array(N * N);
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    let c = [0, 0, 0], s = 0, p = 0, h = 0, ao = 0;
    for (let sy = 0; sy < SS; sy++) for (let sx = 0; sx < SS; sx++) {
      const r = f(x + (sx + 0.5) / SS, y + (sy + 0.5) / SS);
      c = c.map((v, i) => v + r.c[i]); s += r.s; p += r.p; h += r.h; ao += r.ao;
    }
    const k = SS * SS, o = (y * N + x) * 4;
    c.forEach((v, i) => { col[o + i] = clamp(Math.round(v / k), 0, 255); }); col[o + 3] = 255;
    spec[o] = clamp(Math.round(s / k), 0, 255); spec[o + 1] = 10; spec[o + 2] = clamp(Math.round(p / k), 0, 64); spec[o + 3] = 255;
    H[y * N + x] = h / k; nrm[o + 2] = clamp(Math.round(255 * ao / k), 0, 255);
  }
  const at = (i, j) => H[((j + N) % N) * N + ((i + N) % N)];
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const hx = (at(x + 1, y) - at(x - 1, y)) / (2 * MM), hy = (at(x, y + 1) - at(x, y - 1)) / (2 * MM);
    let nx = -hx, ny = -hy; const l = Math.hypot(nx, ny, 1); nx /= l; ny /= l;
    const o = (y * N + x) * 4;
    nrm[o] = Math.round((nx * 0.5 + 0.5) * 255); nrm[o + 1] = Math.round((ny * 0.5 + 0.5) * 255);
    nrm[o + 3] = height ? clamp(Math.round(255 * (1 + H[y * N + x] / 250)), 0, 255) : 255;
  }
  return {col, nrm, maps: [png(col, N, N), png(spec, N, N), png(nrm, N, N)]};
}
