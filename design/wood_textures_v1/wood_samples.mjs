// Wood texture samples from CC0 photos (Poly Haven, ambientCG), two versions, contact sheets for the user.
// node wood_samples.mjs <srcDir> <outDir> <repoTexDir>
import fs from 'node:fs';
import path from 'node:path';
import {createRequire} from 'node:module';
const require = createRequire(import.meta.url);
const sharp = require('D:/Minecraft Modding/Apocalypse First Light/wiki/node_modules/sharp');

const [SRC, OUT, REPO] = process.argv.slice(2);
fs.mkdirSync(OUT, {recursive: true});

// ------------------------------------------------------------------ image helpers (float RGB 0..1, w*h*3)
async function load(file) {
  const {data, info} = await sharp(file).removeAlpha().raw().toBuffer({resolveWithObject: true});
  const f = new Float32Array(info.width * info.height * 3);
  for (let i = 0; i < f.length; i++) f[i] = data[i] / 255;
  return {w: info.width, h: info.height, d: f};
}
async function loadGrey(file) {
  const {data, info} = await sharp(file).greyscale().raw().toBuffer({resolveWithObject: true});
  const f = new Float32Array(info.width * info.height);
  for (let i = 0; i < f.length; i++) f[i] = data[i * info.channels] / 255;
  return {w: info.width, h: info.height, d: f};
}
const img = (w, h, c = 3) => ({w, h, c, d: new Float32Array(w * h * c)});
const clamp = (v, a, b) => v < a ? a : v > b ? b : v;
const sstep = (a, b, x) => { const t = clamp((x - a) / (b - a), 0, 1); return t * t * (3 - 2 * t); };
const lum = (r, g, b) => 0.2126 * r + 0.7152 * g + 0.0722 * b;

/** bilinear sample with wrap */
function sampleWrap(im, x, y, out) {
  const {w, h, d} = im, c = im.c || 3;
  x = ((x % w) + w) % w; y = ((y % h) + h) % h;
  const x0 = Math.floor(x), y0 = Math.floor(y), fx = x - x0, fy = y - y0, x1 = (x0 + 1) % w, y1 = (y0 + 1) % h;
  for (let k = 0; k < c; k++) {
    const a = d[(y0 * w + x0) * c + k], b = d[(y0 * w + x1) * c + k], e = d[(y1 * w + x0) * c + k], f = d[(y1 * w + x1) * c + k];
    out[k] = (a * (1 - fx) + b * fx) * (1 - fy) + (e * (1 - fx) + f * fx) * fy;
  }
  return out;
}
/** resample region [ox,ox+C) of a periodic source into M x M */
function cropResample(src, ox, oy, C, M) {
  const c = src.c || 3, o = img(M, M, c), t = new Float32Array(c);
  for (let y = 0; y < M; y++) for (let x = 0; x < M; x++) {
    sampleWrap(src, ox + (x + 0.5) * C / M - 0.5, oy + (y + 0.5) * C / M - 0.5, t);
    for (let k = 0; k < c; k++) o.d[(y * M + x) * c + k] = t[k];
  }
  return o;
}
/** make periodic: blend with a half-rolled copy, x then y, variance preserving */
function makePeriodic(im) {
  const c = im.c || 3, {w, h} = im;
  const mean = new Float32Array(c);
  for (let i = 0; i < w * h; i++) for (let k = 0; k < c; k++) mean[k] += im.d[i * c + k] / (w * h);
  let cur = im;
  for (const axis of [0, 1]) {
    const o = img(w, h, c);
    for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
      const u = axis === 0 ? Math.abs((x + 0.5) / w - 0.5) * 2 : Math.abs((y + 0.5) / h - 0.5) * 2;
      const wt = sstep(0.45, 1.0, u);
      const xr = axis === 0 ? (x + (w >> 1)) % w : x, yr = axis === 1 ? (y + (h >> 1)) % h : y;
      const norm = Math.sqrt((1 - wt) ** 2 + wt ** 2);
      for (let k = 0; k < c; k++) {
        const a = cur.d[(y * w + x) * c + k] - mean[k], b = cur.d[(yr * w + xr) * c + k] - mean[k];
        o.d[(y * w + x) * c + k] = mean[k] + (a * (1 - wt) + b * wt) / norm;
      }
    }
    cur = o;
  }
  return cur;
}
/** box downsample by integer factor */
function down(im, f) {
  const c = im.c || 3, W = im.w / f, H = im.h / f, o = img(W, H, c);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) for (let k = 0; k < c; k++) {
    let s = 0;
    for (let j = 0; j < f; j++) for (let i = 0; i < f; i++) s += im.d[((y * f + j) * im.w + x * f + i) * c + k];
    o.d[(y * W + x) * c + k] = s / (f * f);
  }
  return o;
}
/** periodic box blur of one channel image (w*h), radius r, n passes */
function blurWrap(a, w, h, r, n = 3) {
  let cur = Float32Array.from(a), tmp = new Float32Array(a.length);
  for (let p = 0; p < n; p++) {
    for (let y = 0; y < h; y++) { let s = 0; for (let i = -r; i <= r; i++) s += cur[y * w + ((i + w) % w)];
      for (let x = 0; x < w; x++) { tmp[y * w + x] = s / (2 * r + 1); s += cur[y * w + (x + r + 1) % w] - cur[y * w + ((x - r) % w + w) % w]; } }
    for (let x = 0; x < w; x++) { let s = 0; for (let i = -r; i <= r; i++) s += tmp[((i + h) % h) * w + x];
      for (let y = 0; y < h; y++) { cur[y * w + x] = s / (2 * r + 1); s += tmp[((y + r + 1) % h) * w + x] - tmp[(((y - r) % h + h) % h) * w + x]; } }
  }
  return cur;
}
/** flatten the photo's large-scale light: divide luminance by its blur, strength s */
function delight(im, radius, s) {
  const {w, h, d} = im, L = new Float32Array(w * h);
  for (let i = 0; i < w * h; i++) L[i] = lum(d[i * 3], d[i * 3 + 1], d[i * 3 + 2]);
  const B = blurWrap(L, w, h, radius);
  let m = 0; for (let i = 0; i < w * h; i++) m += L[i] / (w * h);
  for (let i = 0; i < w * h; i++) { const f = Math.pow(m / Math.max(1e-3, B[i]), s); for (let k = 0; k < 3; k++) d[i * 3 + k] *= f; }
  return im;
}
/** grade: desaturate, contrast around mean, move mean to target (0..255), keep brown hues (no green) */
function grade(im, {target, sat = 1, con = 1, browns = true, moss = false, hi = 9}, mask) {
  const {w, h, d} = im, n = w * h, mean = [0, 0, 0]; let cnt = 0;
  for (let i = 0; i < n; i++) { if (mask && mask[i] < 0.5) continue; cnt++; for (let k = 0; k < 3; k++) mean[k] += d[i * 3 + k]; }
  for (let k = 0; k < 3; k++) mean[k] /= cnt;
  const ml = lum(...mean), tl = lum(...target.map(v => v / 255));
  for (let i = 0; i < n; i++) {
    if (mask && mask[i] <= 0) continue;
    let [r, g, b] = [d[i * 3], d[i * 3 + 1], d[i * 3 + 2]];
    if (moss) { const l0 = lum(r, g, b); g = Math.min(g, (r + b) / 2 + 0.02); const k0 = l0 / Math.max(1e-3, lum(r, g, b)); r *= k0; g *= k0; b *= k0; }
    if (browns) { g = Math.min(g, r * 1.0); b = Math.min(b, g); }
    const l = lum(r, g, b);
    let rel = (l / ml - 1) * con + 1;           // relative brightness
    if (rel > 1 + hi) rel = 1 + hi + (rel - 1 - hi) * 0.25;
    const chroma = [r - l, g - l, b - l];
    const out = target.map((t, k) => clamp((t / 255) * rel + chroma[k] * sat * Math.min(1, tl / ml), 0, 1));
    const a = mask ? clamp(mask[i], 0, 1) : 1;
    for (let k = 0; k < 3; k++) d[i * 3 + k] = d[i * 3 + k] * (1 - a) + out[k] * a;
  }
  return im;
}
function rotate90(im) { const c = im.c || 3, o = img(im.h, im.w, c); for (let y = 0; y < im.h; y++) for (let x = 0; x < im.w; x++) for (let k = 0; k < c; k++) o.d[(x * o.w + (im.h - 1 - y)) * c + k] = im.d[(y * im.w + x) * c + k]; return o; }
function flipX(im) { const c = im.c || 3, o = img(im.w, im.h, c); for (let y = 0; y < im.h; y++) for (let x = 0; x < im.w; x++) for (let k = 0; k < c; k++) o.d[(y * im.w + im.w - 1 - x) * c + k] = im.d[(y * im.w + x) * c + k]; return o; }
/** grain runs along y? (variation mostly across x) */
function grainVertical(im) {
  let gx = 0, gy = 0; const {w, h, d} = im;
  for (let y = 1; y < h; y++) for (let x = 1; x < w; x++) { const i = (y * w + x) * 3; gx += Math.abs(d[i] - d[i - 3]); gy += Math.abs(d[i] - d[i - w * 3]); }
  return gx > gy;
}
function toRGBA8(im) {
  const n = im.w * im.h, b = Buffer.alloc(n * 4), c = im.c || 3;
  for (let i = 0; i < n; i++) { for (let k = 0; k < 3; k++) b[i * 4 + k] = Math.round(clamp(im.d[i * c + Math.min(k, c - 1)], 0, 1) * 255); b[i * 4 + 3] = 255; }
  return b;
}
const savePng = (file, rgba, w, h) => sharp(rgba, {raw: {width: w, height: h, channels: 4}}).png().toFile(file);

// ------------------------------------------------------------------ species
const SPECIES = [
  {id: 'white_oak', logR: 3 / 16, zh: '白栎', bark: 'bark_willow_02', barkT: [152, 146, 136], barkSat: 0.55, barkCon: 1.0,
    end: 'TreeEnd002', endRot: 0, endT: [186, 154, 112], endCon: 1.0, rim: [118, 108, 96],
    timber: 'white_oak_veneer', timberT: [196, 170, 128]},
  {id: 'red_oak', logR: 3.5 / 16, zh: '红栎', bark: 'bark_willow', barkT: [100, 94, 88], barkSat: 0.45, barkCon: 1.1,
    end: 'TreeEnd002', endRot: 2, endFlip: true, endT: [188, 142, 110], endCon: 1.0, rim: [78, 72, 66],
    timber: 'red_oak_veneer', timberT: [198, 152, 118]},
  {id: 'shagbark_hickory', logR: 2.5 / 16, zh: '粗皮山核桃', bark: 'tree_bark_03', barkT: [120, 112, 102], barkSat: 0.25, barkCon: 1.15, moss: true,
    end: 'TreeEnd002', endRot: 1, endT: [194, 164, 124], endCon: 0.95, rim: [92, 86, 78],
    timber: 'ash_veneer', timberT: [206, 178, 138]},
  {id: 'red_maple', logR: 4.5 / 16, zh: '红枫', bark: 'chinese_hackberry_bark', barkT: [140, 138, 132], barkSat: 0.25, barkCon: 0.9, moss: true, hi: 0.12,
    end: 'TreeEnd003', endRot: 0, endT: [214, 194, 160], endCon: 1.0, rim: [104, 100, 94],
    timber: 'white_maple_veneer', timberT: [222, 204, 172]},
  {id: 'eastern_hemlock', logR: 6 / 16, zh: '东部铁杉', bark: 'pine_bark', barkT: [96, 76, 64], barkSat: 0.45, barkCon: 1.0,
    end: 'TreeEnd003', endRot: 1, endFlip: true, endT: [198, 162, 126], endCon: 1.25, rim: [92, 62, 48],
    timber: 'coated_pine', timberOx: 400, timberT: [208, 174, 142], timberSat: 0.45, timberCon: 0.8},
  {id: 'white_pine', logR: 5 / 16, zh: '北美白松', bark: 'knotted_pine_bark', barkT: [86, 82, 78], barkSat: 0.45, barkCon: 1.05, barkOx: 300,
    end: 'TreeEnd003', endRot: 3, endT: [216, 190, 142], endCon: 1.3, rim: [70, 66, 62],
    timber: 'coated_pine', timberT: [224, 202, 160], timberSat: 0.35, timberCon: 0.8},
];
const DIM = {bark_willow_02: 2.15, bark_willow: 1, chinese_hackberry_bark: 1.8, tree_bark_03: 1, pine_bark: 2, knotted_pine_bark: 1.7,
  white_oak_veneer: 0.5, red_oak_veneer: 1, white_maple_veneer: 1, ash_veneer: 1, coated_pine: 0.74, hinoki_planks: 1.89,
  black_walnut_veneer_01: 1, lacquered_cherry_wood: 1};

const VERSIONS = [
  {v: 1, N: 128, tileM: 1.0, nStrength: 0.35, delight: 0.6, endN: 128,
    furn: {src: 'white_oak_veneer', zh: '白栎，胡桃色漆', T: [96, 64, 44], sat: 0.9, con: 1.35}},
  {v: 2, N: 64, tileM: 0.7, nStrength: 0.2, delight: 0.85, endN: 64,
    furn: {src: 'red_oak_veneer', zh: '红栎，琥珀色清漆', T: [150, 100, 66], sat: 0.7, con: 1.6}},
];

/** a periodic tile of `tileM` metres from a source of `dim` metres, at N px; returns {col, nrm?} */
async function photoTile(name, dim, tileM, N, {withNormal = false, delightS = 0.6, ox = 0, oy = 0} = {}) {
  const alt = process.env.WOOD_ALT && fs.existsSync(path.join(process.env.WOOD_ALT, name + '_diff_1k.jpg')) ? process.env.WOOD_ALT : SRC;
  const col = await load(path.join(alt, name + '_diff_1k.jpg'));
  col.c = 3;
  delight(col, 64, delightS);
  const C = col.w * tileM / dim;                     // source px per tile
  const periodic = Math.abs(C / col.w - Math.round(C / col.w)) < 1e-3 && Math.round(C / col.w) >= 1;
  const SSF = 6, M = N * SSF;
  let a = cropResample(col, ox, oy, C, M);
  if (!periodic) a = makePeriodic(a);
  const res = {col: down(a, SSF), periodic};
  if (withNormal) {
    const nm = await load(path.join(SRC, name + '_nor_gl_1k.jpg'));
    nm.c = 3;
    for (let i = 0; i < nm.d.length; i++) nm.d[i] = nm.d[i] * 2 - 1;
    let b = cropResample(nm, ox, oy, C, M);
    if (!periodic) b = makePeriodic(b);
    res.nrm = down(b, SSF);
  }
  return res;
}

/** disc photo -> square end texture, wood graded, rim graded to the species bark, outside filled with bark */
async function endDisc(sp, N) {
  const dir = path.join(SRC, sp.end);
  let col = await load(path.join(dir, sp.end + '_1K-JPG_Color.jpg')); col.c = 3;
  let op = await loadGrey(path.join(dir, sp.end + '_1K-JPG_Opacity.jpg')); op.c = 1;
  for (let r = 0; r < sp.endRot; r++) { col = rotate90(col); op = rotate90(op); }
  if (sp.endFlip) { col = flipX(col); op = flipX(op); }
  const {w, h} = col;
  // centre and outer radius per angle
  let sx = 0, sy = 0, sn = 0;
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) if (op.d[y * w + x] > 0.5) { sx += x; sy += y; sn++; }
  const cx = sx / sn, cy = sy / sn, A = 360, rOut = new Float32Array(A);
  for (let a = 0; a < A; a++) {
    const th = a / A * 2 * Math.PI; let r = 0;
    for (let t = 0; t < w; t += 0.5) { const x = Math.round(cx + Math.cos(th) * t), y = Math.round(cy + Math.sin(th) * t); if (x < 0 || y < 0 || x >= w || y >= h || op.d[y * w + x] < 0.5) break; r = t; }
    rOut[a] = r;
  }
  // smooth radius profile
  const rs = new Float32Array(A); for (let a = 0; a < A; a++) { let s = 0; for (let k = -4; k <= 4; k++) s += rOut[(a + k + A) % A]; rs[a] = s / 9; }
  const R = rs.reduce((m, v) => Math.max(m, v), 0) * 0.98;     // square covers the disc (corners are bark fill)
  // wood mask (inside rim) and rim mask
  const rimW = R * 0.07, woodMask = new Float32Array(w * h), rimMask = new Float32Array(w * h);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const dx = x - cx, dy = y - cy, rr = Math.hypot(dx, dy), a = ((Math.round(Math.atan2(dy, dx) / (2 * Math.PI) * A) % A) + A) % A;
    const edge = rs[a];
    const inner = 1 - sstep(edge - rimW * 1.4, edge - rimW * 0.6, rr);
    woodMask[y * w + x] = inner;
    rimMask[y * w + x] = op.d[y * w + x] > 0.5 ? (1 - inner) : 0;
  }
  { const L = new Float32Array(w * h), Wm = new Float32Array(w * h);
    for (let i = 0; i < w * h; i++) { const m = woodMask[i] > 0.9 ? 1 : 0; Wm[i] = m; L[i] = m * lum(col.d[i * 3], col.d[i * 3 + 1], col.d[i * 3 + 2]); }
    const BL = blurWrap(L, w, h, 60), BW = blurWrap(Wm, w, h, 60);
    let mL = 0, n = 0; for (let i = 0; i < w * h; i++) if (Wm[i]) { mL += L[i]; n++; } mL /= n;
    for (let i = 0; i < w * h; i++) { if (BW[i] < 0.05) continue; const f = Math.pow(mL / Math.max(1e-3, BL[i] / BW[i]), 0.8 * woodMask[i]); for (let k = 0; k < 3; k++) col.d[i * 3 + k] *= f; } }
  grade(col, {target: sp.endT, sat: 0.5, con: sp.endCon, browns: true, hi: 0.25}, woodMask);
  grade(col, {target: sp.rim, sat: 0.4, con: 1.0, browns: true}, rimMask);
  // fill outside the disc with dark bark colour
  for (let i = 0; i < w * h; i++) { const o = op.d[i]; for (let k = 0; k < 3; k++) col.d[i * 3 + k] = col.d[i * 3 + k] * o + (sp.rim[k] / 255) * 0.75 * (1 - o); }
  // stump disc: square around the centre (TreeCut maps the ring radius to 1/2.1 of the texture)
  const SSF = 6, M = N * SSF, o = img(M, M, 3), t = new Float32Array(3);
  const Rs = rs.reduce((m, v) => m + v, 0) / A * 1.05;
  for (let y = 0; y < M; y++) for (let x = 0; x < M; x++) {
    const px = cx - Rs + (x + 0.5) / M * 2 * Rs, py = cy - Rs + (y + 0.5) / M * 2 * Rs;
    sampleWrap(col, clamp(px, 0, w - 1.001), clamp(py, 0, h - 1.001), t);
    for (let k = 0; k < 3; k++) o.d[(y * M + x) * 3 + k] = t[k];
  }
  // log block end: the disc warped into the species' rounded square (LogShape radius), bark rim along the outline
  const r = sp.logR, ob = img(M, M, 3);
  const sdRR = (px, py) => { const qx = Math.abs(px) - (0.5 - r), qy = Math.abs(py) - (0.5 - r);
    return Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - r; };
  for (let y = 0; y < M; y++) for (let x = 0; x < M; x++) {
    const px = (x + 0.5) / M - 0.5, py = (y + 0.5) / M - 0.5, th = Math.atan2(py, px), rr = Math.hypot(px, py);
    // distance to the outline along th (bisection on the SDF)
    let lo = 0, hi = 0.75; for (let i = 0; i < 24; i++) { const m = (lo + hi) / 2; if (sdRR(Math.cos(th) * m, Math.sin(th) * m) < 0) lo = m; else hi = m; }
    const rho = Math.min(1.08, rr / lo);
    const a = ((Math.round(th / (2 * Math.PI) * A) % A) + A) % A, edge = rs[a];
    sampleWrap(col, clamp(cx + Math.cos(th) * rho * edge * 0.99, 0, w - 1.001), clamp(cy + Math.sin(th) * rho * edge * 0.99, 0, h - 1.001), t);
    for (let k = 0; k < 3; k++) ob.d[(y * M + x) * 3 + k] = t[k];
  }
  return {disc: down(o, SSF), block: down(ob, SSF)};
}

/**
 * Orthographic 3/4 view of a rounded-square prism (the log block: LogShape cross-section, radius r, one block long),
 * ray-marched: bark round the sides laid out like LogShape (each side plus its corners' halves is one tile across, seams
 * on the corners' middles, v down the block), the end texture on top, lambert light with the textures' weak normals.
 */
function logPrism(cv, end, endN, side, sideN, r, X, Y, s, {light = [0.45, 0.8, 0.55], tall = 1} = {}) {
  const ll = Math.hypot(...light), L = light.map(v => v / ll);
  const d = [-1, -0.9, -1], dl = Math.hypot(...d); d.forEach((v, i) => d[i] = v / dl);
  const right = [1 / Math.SQRT2, 0, -1 / Math.SQRT2];
  const up = [d[1] * right[2] - d[2] * right[1], d[2] * right[0] - d[0] * right[2], d[0] * right[1] - d[1] * right[0]].map(v => -v);
  const sd = (p) => { const qx = Math.abs(p[0]) - (0.5 - r), qz = Math.abs(p[2]) - (0.5 - r);
    const d2 = Math.hypot(Math.max(qx, 0), Math.max(qz, 0)) + Math.min(Math.max(qx, qz), 0) - r;
    const dy = Math.abs(p[1]) - tall / 2; return Math.min(Math.max(d2, dy), 0) + Math.hypot(Math.max(d2, 0), Math.max(dy, 0)); };
  const scale = 1.75 + (tall - 1) * 0.9, SSA = 3;
  const tex = (im, u, v) => { const c = im.c || 3, x = Math.min(im.w - 1, Math.max(0, Math.floor(u * im.w))), y = Math.min(im.h - 1, Math.max(0, Math.floor(v * im.h))); return (y * im.w + x); };
  const flat = 0.5 - r, tile = 2 * (flat + r * Math.PI / 4);
  for (let py = 0; py < s; py++) for (let px = 0; px < s; px++) {
    const acc = [0, 0, 0]; let hits = 0;
    for (let j = 0; j < SSA; j++) for (let i = 0; i < SSA; i++) {
      const sx = ((px + (i + 0.5) / SSA) / s - 0.5) * scale, sy = -((py + (j + 0.5) / SSA) / s - 0.5) * scale;
      const o = [0, 1, 2].map(k => right[k] * sx + up[k] * sy - d[k] * 3);
      let t = 0, hit = false, p;
      for (let it = 0; it < 96; it++) { p = [0, 1, 2].map(k => o[k] + d[k] * t); const dist = sd(p); if (dist < 1e-4) { hit = true; break; } t += dist; if (t > 8) break; }
      if (!hit) continue;
      hits++;
      let col, n;
      if (p[1] > tall / 2 - 2e-3) {                               // end cap
        const u = p[0] + 0.5, v = p[2] + 0.5, ix = tex(end, u, v);
        col = [end.d[ix * 3], end.d[ix * 3 + 1], end.d[ix * 3 + 2]];
        const nx = endN ? endN[ix * 3] : 0, ny = endN ? endN[ix * 3 + 1] : 0, nz = endN ? endN[ix * 3 + 2] : 1;
        n = [nx, nz, -ny];
      } else {                                                     // bark round the sides
        const ax = Math.abs(p[0]) > Math.abs(p[2]) ? 0 : 2, sg = Math.sign(p[ax]) || 1;
        // tangent coordinate along the side, seen from outside (counter-clockwise from above)
        const tc = ax === 0 ? -sg * p[2] : sg * p[0];
        let sArc;
        if (Math.abs(tc) <= flat) sArc = tc;
        else { const cxp = (ax === 0 ? [sg * flat, Math.sign(p[2]) * flat] : [Math.sign(p[0]) * flat, sg * flat]);
          const phi = Math.atan2(Math.abs(ax === 0 ? p[2] - cxp[1] : p[0] - cxp[0]), Math.abs(ax === 0 ? p[0] - cxp[0] : p[2] - cxp[1]));
          sArc = Math.sign(tc) * (flat + r * Math.min(phi, Math.PI / 4)); }
        const u = sArc / tile + 0.5, v = (tall / 2 - p[1]) / tall * tall % 1;
        const ix = tex(side, u, v);
        col = [side.d[ix * 3], side.d[ix * 3 + 1], side.d[ix * 3 + 2]];
        // geometric normal (rounded corner) and its horizontal tangent
        const e = 1e-3, g = [0, 1, 2].map(k => { const a = p.slice(), b = p.slice(); a[k] += e; b[k] -= e; return sd(a) - sd(b); });
        g[1] = 0; const gl = Math.hypot(...g), N0 = g.map(v => v / gl);
        const T = [-N0[2], 0, N0[0]].map(v => -v);
        const nx = sideN ? sideN[ix * 3] : 0, ny = sideN ? sideN[ix * 3 + 1] : 0, nz = sideN ? sideN[ix * 3 + 2] : 1;
        n = [0, 1, 2].map(k => T[k] * nx + (k === 1 ? ny : 0) + N0[k] * nz);
      }
      const nl = Math.hypot(...n), lam = Math.max(0, (n[0] * L[0] + n[1] * L[1] + n[2] * L[2]) / nl);
      const f = 0.38 + 0.7 * lam;
      for (let k = 0; k < 3; k++) acc[k] += clamp(col[k] * f, 0, 1);
    }
    if (!hits) continue;
    const o = ((Y + py) * cv.W + X + px) * 4, a = hits / (SSA * SSA);
    for (let k = 0; k < 3; k++) cv.b[o + k] = Math.round(cv.b[o + k] * (1 - a) + acc[k] / hits * 255 * a);
  }
}

/** encode a weak LabPBR normal (repo convention: R = x, G = -dh/dy i.e. green flipped from GL, B = AO, A = 255) */
function encodeNormal(nrm, k, aoFrom) {
  const n = nrm.w * nrm.h, b = Buffer.alloc(n * 4), lit = new Float32Array(n * 3);
  for (let i = 0; i < n; i++) {
    let x = nrm.d[i * 3] * k, y = nrm.d[i * 3 + 1] * k, z = Math.max(0.2, nrm.d[i * 3 + 2]);
    const l = Math.hypot(x, y, z); x /= l; y /= l; z /= l;
    b[i * 4] = Math.round((x * 0.5 + 0.5) * 255); b[i * 4 + 1] = Math.round((-y * 0.5 + 0.5) * 255);
    b[i * 4 + 2] = aoFrom ? Math.round(255 * aoFrom[i]) : 255; b[i * 4 + 3] = 255;
    lit[i * 3] = x; lit[i * 3 + 1] = y; lit[i * 3 + 2] = z;
  }
  return {buf: b, n: lit};
}
/** preview: albedo lit by a low side light from upper left using the (GL) normals */
function litPreview(col, n) {
  const L = [-0.62, 0.55, 0.56], ll = Math.hypot(...L); L.forEach((v, i) => L[i] = v / ll);
  const o = img(col.w, col.h, 3);
  for (let i = 0; i < col.w * col.h; i++) {
    const d = Math.max(0, n[i * 3] * L[0] + n[i * 3 + 1] * L[1] + n[i * 3 + 2] * L[2]);
    const f = 0.3 + 0.85 * d;
    for (let k = 0; k < 3; k++) o.d[i * 3 + k] = clamp(col.d[i * 3 + k] * f, 0, 1);
  }
  return o;
}
function flatN(w, h) { const n = new Float32Array(w * h * 3); for (let i = 0; i < w * h; i++) n[i * 3 + 2] = 1; return n; }

// ------------------------------------------------------------------ sheet drawing (RGBA8 canvas)
function canvas(W, H, bg = [34, 34, 36]) { const b = Buffer.alloc(W * H * 4); for (let i = 0; i < W * H; i++) { b[i * 4] = bg[0]; b[i * 4 + 1] = bg[1]; b[i * 4 + 2] = bg[2]; b[i * 4 + 3] = 255; } return {W, H, b}; }
/** blit image `im` (float) scaled nearest to size s x s, tiled t x t, at (X,Y) */
function blit(cv, im, X, Y, s, t = 1) {
  const c = im.c || 3;
  for (let y = 0; y < s; y++) for (let x = 0; x < s; x++) {
    const u = Math.floor((x * t / s) % 1 * im.w + 1e-6) % im.w, v = Math.floor((y * t / s) % 1 * im.h + 1e-6) % im.h;
    const sx = Math.min(im.w - 1, Math.floor(((x * t) % s) / s * im.w)), sy = Math.min(im.h - 1, Math.floor(((y * t) % s) / s * im.h));
    const o = ((Y + y) * cv.W + X + x) * 4, i = (sy * im.w + sx) * c;
    for (let k = 0; k < 3; k++) cv.b[o + k] = Math.round(clamp(im.d[i + Math.min(k, c - 1)], 0, 1) * 255);
  }
}
/** isometric block: top texture `top`, side textures `side` (v along y), nearest, size s */
function isoCube(cv, top, side, X, Y, s, sideRight = side) {
  const k = s / 2.0, cx = X + s / 2, cy = Y + s * 0.25;
  const ex = [Math.cos(Math.PI / 6) * k, Math.sin(Math.PI / 6) * k], ez = [-Math.cos(Math.PI / 6) * k, Math.sin(Math.PI / 6) * k], ey = [0, -k];
  // screen = C + x*ex + z*ez + (y-1)*ey with C the top-back corner projected; top face y=1
  const C = [cx, cy - 0.0];
  const faces = [
    {o: C, a: ex, b: ez, tex: top, shade: 1.0, map: (p, q) => [p, q]},                                         // top: x,z
    {o: [C[0] + ez[0], C[1] + ez[1]], a: ex, b: [-ey[0], -ey[1]], tex: side, shade: 0.8, map: (p, q) => [p, q]},     // front-left: z=1, x,y
    {o: [C[0] + ex[0], C[1] + ex[1]], a: ez, b: [-ey[0], -ey[1]], tex: sideRight, shade: 0.62, map: (p, q) => [p, q]}, // front-right: x=1, z,y
  ];
  for (const f of faces) {
    const det = f.a[0] * f.b[1] - f.a[1] * f.b[0];
    const x0 = Math.floor(Math.min(f.o[0], f.o[0] + f.a[0], f.o[0] + f.b[0], f.o[0] + f.a[0] + f.b[0])), x1 = Math.ceil(Math.max(f.o[0], f.o[0] + f.a[0], f.o[0] + f.b[0], f.o[0] + f.a[0] + f.b[0]));
    const y0 = Math.floor(Math.min(f.o[1], f.o[1] + f.a[1], f.o[1] + f.b[1], f.o[1] + f.a[1] + f.b[1])), y1 = Math.ceil(Math.max(f.o[1], f.o[1] + f.a[1], f.o[1] + f.b[1], f.o[1] + f.a[1] + f.b[1]));
    const c = f.tex.c || 3;
    for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) {
      const dx = x + 0.5 - f.o[0], dy = y + 0.5 - f.o[1];
      const p = (dx * f.b[1] - dy * f.b[0]) / det, q = (f.a[0] * dy - f.a[1] * dx) / det;
      if (p < 0 || p >= 1 || q < 0 || q >= 1) continue;
      const u = Math.min(f.tex.w - 1, Math.floor(p * f.tex.w)), v = Math.min(f.tex.h - 1, Math.floor(q * f.tex.h));
      const o = (y * cv.W + x) * 4, i = (v * f.tex.w + u) * c;
      for (let k2 = 0; k2 < 3; k2++) cv.b[o + k2] = Math.round(clamp(f.tex.d[i + Math.min(k2, c - 1)] * f.shade, 0, 1) * 255);
    }
  }
}
function svgText(items, W, H) {
  const esc = s => s.replace(/&/g, '&amp;').replace(/</g, '&lt;');
  return Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}">` + items.map(t =>
    `<text x="${t.x}" y="${t.y}" font-family="Microsoft YaHei, SimHei, sans-serif" font-size="${t.size || 18}" fill="${t.fill || '#e8e8e8'}" font-weight="${t.bold ? 700 : 400}">${esc(t.s)}</text>`).join('') + '</svg>');
}
async function readRepo(rel) {
  const f = path.join(REPO, rel); if (!fs.existsSync(f)) return null;
  const im = await load(f); im.c = 3; return im;
}

// ------------------------------------------------------------------ main
if (!process.env.WOOD_LIB) for (const V of VERSIONS) {
  const vdir = path.join(OUT, 'v' + V.v); fs.mkdirSync(vdir, {recursive: true});
  const rows = [];
  for (const sp of SPECIES) {
    const bark = await photoTile(sp.bark, DIM[sp.bark], V.tileM, V.N, {withNormal: true, delightS: V.delight, ox: sp.barkOx || 0, oy: sp.barkOy || 0});
    grade(bark.col, {target: sp.barkT, sat: sp.barkSat, con: sp.barkCon, moss: !!sp.moss, hi: sp.hi ?? 9});
    // AO from the albedo cavities, mild
    const ao = new Float32Array(V.N * V.N); { let m = 0; for (let i = 0; i < V.N * V.N; i++) m += lum(bark.col.d[i * 3], bark.col.d[i * 3 + 1], bark.col.d[i * 3 + 2]) / (V.N * V.N);
      for (let i = 0; i < V.N * V.N; i++) ao[i] = clamp(0.85 + 0.15 * lum(bark.col.d[i * 3], bark.col.d[i * 3 + 1], bark.col.d[i * 3 + 2]) / m, 0.75, 1); }
    const nn = encodeNormal(bark.nrm, V.nStrength, ao);
    const barkS = Buffer.alloc(V.N * V.N * 4);
    for (let i = 0; i < V.N * V.N; i++) { barkS[i * 4] = Math.round(18 + 22 * ao[i]); barkS[i * 4 + 1] = 10; barkS[i * 4 + 2] = 48; barkS[i * 4 + 3] = 255; }
    await savePng(path.join(vdir, sp.id + '_bark.png'), toRGBA8(bark.col), V.N, V.N);
    await savePng(path.join(vdir, sp.id + '_bark_n.png'), nn.buf, V.N, V.N);
    await savePng(path.join(vdir, sp.id + '_bark_s.png'), barkS, V.N, V.N);

    const {disc, block: end} = await endDisc(sp, V.endN);
    await savePng(path.join(vdir, sp.id + '_log_end.png'), toRGBA8(end), V.endN, V.endN);
    await savePng(path.join(vdir, sp.id + '_end_grain.png'), toRGBA8(disc), V.endN, V.endN);

    const tim = await photoTile(sp.timber, DIM[sp.timber], 1.0, 240, {delightS: V.delight * 0.5, ox: sp.timberOx || 0});
    let tc = tim.col; tc.c = 3;
    if (!grainVertical(tc)) { tc = rotate90(tc); }
    grade(tc, {target: sp.timberT, sat: sp.timberSat ?? 0.6, con: (V.v === 1 ? 1.5 : 1.25) * (sp.timberCon ?? 1)});
    await savePng(path.join(vdir, sp.id + '_timber_side.png'), toRGBA8(tc), 240, 240);

    const oldBark = await readRepo('tree/' + sp.id + '_bark.png');
    const oldEnd = await readRepo('tree/' + sp.id + '_log_end.png');
    const oldTimber = await readRepo('wood/' + sp.id + '_timber_side.png');
    rows.push({sp, bark: bark.col, barkN: nn.n, lit: litPreview(bark.col, nn.n), end, disc, timber: tc, oldBark, oldEnd, oldTimber});
  }
  // furniture
  const F = V.furn;
  const fur = await photoTile(F.src, DIM[F.src], 1.0, 240, {delightS: 0.25});
  let fc = fur.col; fc.c = 3;
  if (grainVertical(fc)) fc = rotate90(fc);   // furniture.wood runs horizontally like the current one
  grade(fc, {target: F.T, sat: F.sat, con: F.con});
  const fs_ = Buffer.alloc(240 * 240 * 4); { let m = 0; for (let i = 0; i < 240 * 240; i++) m += lum(fc.d[i * 3], fc.d[i * 3 + 1], fc.d[i * 3 + 2]) / (240 * 240);
    for (let i = 0; i < 240 * 240; i++) { const l = lum(fc.d[i * 3], fc.d[i * 3 + 1], fc.d[i * 3 + 2]) / m; fs_[i * 4] = Math.round(clamp(196 + 18 * (l - 1), 160, 215)); fs_[i * 4 + 1] = 10; fs_[i * 4 + 2] = 0; fs_[i * 4 + 3] = 255; } }
  await savePng(path.join(vdir, 'furniture_wood.png'), toRGBA8(fc), 240, 240);
  await savePng(path.join(vdir, 'furniture_wood_s.png'), fs_, 240, 240);
  const oldFur = await readRepo('furniture/wood.png');
  // gloss preview: soft window reflection band
  const gloss = img(240, 240, 3);
  for (let y = 0; y < 240; y++) for (let x = 0; x < 240; x++) {
    const i = y * 240 + x, sm = fs_[i * 4] / 255;
    const band = Math.exp(-((((x - y * 0.6) - 70) / 38) ** 2)) * 0.5 + 0.06;
    for (let k = 0; k < 3; k++) gloss.d[i * 3 + k] = clamp(fc.d[i * 3 + k] * 0.92 + band * sm * sm * 0.55, 0, 1);
  }

  // ---- draw sheet
  const CELL = 256, GAP = 10, LEFT = 150, TOP = 120, HEAD = 34;
  const cols = ['现在：树皮 32', `新：树皮 ${V.N}`, '新：树皮 2×2 铺开', '新：树皮 侧光（弱法线）', '现在：原木端面 32', `新：原木端面 ${V.endN}`, `新：树桩年轮 ${V.endN}`, '新：刨光木料 240', '新：原木（圆角方柱）'];
  const W = LEFT + cols.length * (CELL + GAP) + GAP, H = TOP + HEAD + (rows.length + 1) * (CELL + GAP) + HEAD + 40;
  const cv = canvas(W, H);
  const texts = [];
  texts.push({x: 20, y: 46, s: `AFL 木纹样板 第 ${V.v} 版（CC0 实拍做底）`, size: 32, bold: true});
  texts.push({x: 20, y: 88, s: `树皮 ${V.N} px，一张约 ${V.tileM} m，法线强度 ${V.nStrength}；年轮 ${V.endN} px；木料 240 px；家具统一漆面：${F.zh}。游戏里的像素放大（不插值）`, size: 20, fill: '#bdbdbd'});
  cols.forEach((c, i) => texts.push({x: LEFT + GAP + i * (CELL + GAP), y: TOP + 22, s: c, size: 18, fill: '#d0d0d0'}));
  rows.forEach((r, j) => {
    const Y = TOP + HEAD + j * (CELL + GAP), X = i => LEFT + GAP + i * (CELL + GAP);
    texts.push({x: 20, y: Y + CELL / 2, s: r.sp.zh, size: 24, bold: true});
    texts.push({x: 20, y: Y + CELL / 2 + 28, s: r.sp.id, size: 13, fill: '#9a9a9a'});
    if (r.oldBark) blit(cv, r.oldBark, X(0), Y, CELL);
    blit(cv, r.bark, X(1), Y, CELL);
    blit(cv, r.bark, X(2), Y, CELL, 2);
    blit(cv, r.lit, X(3), Y, CELL);
    if (r.oldEnd) blit(cv, r.oldEnd, X(4), Y, CELL);
    blit(cv, r.end, X(5), Y, CELL);
    blit(cv, r.disc, X(6), Y, CELL);
    blit(cv, r.timber, X(7) + 8, Y + 8, 240);
    logPrism(cv, r.end, null, r.bark, r.barkN, r.sp.logR, X(8), Y, CELL);
    texts.push({x: X(8) + 6, y: Y + CELL - 6, s: '圆角 ' + Math.round(r.sp.logR * 32) / 2 + '/16', size: 13, fill: '#9a9a9a'});
  });
  { // furniture row
    const j = rows.length, Y = TOP + HEAD + j * (CELL + GAP) + HEAD, X = i => LEFT + GAP + i * (CELL + GAP);
    texts.push({x: LEFT + GAP, y: Y - 10, s: '家具统一漆面', size: 20, fill: '#d0d0d0'});
    const fl = ['现在：furniture/wood', `新：${F.zh}漆面 240`, '新：2×2 铺开', '新：漆面反光示意', '新：柜体示意'];
    fl.forEach((c, i) => texts.push({x: X(i), y: Y + CELL + 24, s: c, size: 16, fill: '#bdbdbd'}));
    texts.push({x: 20, y: Y + CELL / 2, s: '家具', size: 24, bold: true});
    if (oldFur) blit(cv, oldFur, X(0) + 8, Y + 8, 240);
    blit(cv, fc, X(1) + 8, Y + 8, 240);
    blit(cv, fc, X(2), Y, CELL, 2);
    blit(cv, gloss, X(3) + 8, Y + 8, 240);
    const fv = rotate90(fc); fv.c = 3;
    logPrism(cv, fc, null, fv, null, 1 / 32, X(4), Y, CELL);
  }
  const base = sharp(cv.b, {raw: {width: W, height: H, channels: 4}});
  const file = path.join(OUT, `wood_samples_v${V.v}.png`);
  await base.composite([{input: svgText(texts, W, H), top: 0, left: 0}]).png().toFile(file);
  console.log('wrote', file, W + 'x' + H);
}

export {load, img, clamp, lum, grade, rotate90, grainVertical, toRGBA8, savePng, photoTile, logPrism, blit, canvas, svgText, readRepo};
