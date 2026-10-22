// BR-51 V3 inventory icon (docs/native_guns/br51_01_v3_game_model_v1.md): the game low poly in its rest pose, drawn
// with its own Base Color atlas (src/main/blockbench/textures/br51_01_v3_native.png), so the inventory shows the gun the
// hands hold (user 2026-10-21: every item's inventory icon is a picture of its 3D model).
//   node tools/render-br51-01-inventory-icon.mjs [--check]
// View: the right side (the operating rod handle), muzzle to the lower right, a little of the top showing, as the other
// native gun icons. Framing as tools/normalize-native-gun-inventory-icons.py (LONG_GUN): the longest projected side fills
// 0.88 of a 256 px transparent square, centred. Lighting and supersampling as tools/build-material-meshes-v1.mjs drawIcon
// (light from the top front left, both sides of a face lit, 4 x 4 box filter). Writes the framed picture to both the
// runtime texture and the inventory_icons source the normalizer reads (it frames it again to the same result).
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {png, readPng, norm, newell, dot} from './cube-slab-mesh-lib.mjs';
import {lowParts, texturePath} from './build-br51-01-v3-native.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const OUT = [path.join(root, 'src/main/resources/assets/apocalypse_firstlight/textures/item/br51_01_inventory.png'),
  path.join(root, 'src/main/blockbench/inventory_icons/br51_01_inventory.png')];
const CANVAS = 256, SS = 4, FILL = 0.88, PITCH = 14, ROLL = -32;
const LIGHT = norm([-0.35, 0.85, 0.4]);
const check = process.argv.includes('--check');

// model px -> view (x right, y up, z toward the eye): the right side seen from +X (muzzle -Z to the right), tipped PITCH
// degrees so the top shows, then turned ROLL degrees in the picture (muzzle down)
const rad = d => d * Math.PI / 180, cp = Math.cos(rad(PITCH)), sp = Math.sin(rad(PITCH)), cr = Math.cos(rad(ROLL)), sr = Math.sin(rad(ROLL));
const view = v => {
  const x = -v[2], y = v[1] * cp - v[0] * sp, z = v[1] * sp + v[0] * cp;
  return [x * cr - y * sr, x * sr + y * cr, z];
};

const low = lowParts();
const atlas = readPng(fs.readFileSync(texturePath));
const tris = [];
for (const p of low.parts) for (const f of p.faces) {
  const P = f.v.map(i => view(p.vertices[i]));
  let n = norm(newell(P));
  if (n[2] < 0) n = n.map(v => -v);
  const shade = 0.58 + 0.5 * Math.max(0, dot(n, LIGHT));
  for (let q = 1; q + 1 < P.length; q++) tris.push({P: [P[0], P[q], P[q + 1]], T: [f.uv[0], f.uv[q], f.uv[q + 1]], shade});
}
const lo = [Infinity, Infinity], hi = [-Infinity, -Infinity];
for (const t of tris) for (const p of t.P) for (let k = 0; k < 2; k++) { lo[k] = Math.min(lo[k], p[k]); hi[k] = Math.max(hi[k], p[k]); }
const N = CANVAS * SS, scale = FILL * N / Math.max(hi[0] - lo[0], hi[1] - lo[1]);
const cx = (lo[0] + hi[0]) / 2, cy = (lo[1] + hi[1]) / 2;
for (const t of tris) t.P = t.P.map(p => [N / 2 + (p[0] - cx) * scale, N / 2 - (p[1] - cy) * scale, p[2]]);

const col = new Float32Array(N * N * 3), cover = new Uint8Array(N * N), depth = new Float32Array(N * N).fill(-Infinity);
const texel = (u, v) => {
  const x = Math.min(atlas.w - 1, Math.max(0, Math.floor(u))), y = Math.min(atlas.h - 1, Math.max(0, Math.floor(v))), o = (y * atlas.w + x) * atlas.bpp;
  return [atlas.px[o] / 255, atlas.px[o + 1] / 255, atlas.px[o + 2] / 255];
};
for (const {P: [A, B, C], T, shade} of tris) {
  const den = (B[1] - C[1]) * (A[0] - C[0]) + (C[0] - B[0]) * (A[1] - C[1]); if (Math.abs(den) < 1e-12) continue;
  const x0 = Math.max(0, Math.floor(Math.min(A[0], B[0], C[0]))), x1 = Math.min(N - 1, Math.ceil(Math.max(A[0], B[0], C[0])));
  const y0 = Math.max(0, Math.floor(Math.min(A[1], B[1], C[1]))), y1 = Math.min(N - 1, Math.ceil(Math.max(A[1], B[1], C[1])));
  for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
    const X = x + 0.5, Y = y + 0.5;
    const l1 = ((B[1] - C[1]) * (X - C[0]) + (C[0] - B[0]) * (Y - C[1])) / den, l2 = ((C[1] - A[1]) * (X - C[0]) + (A[0] - C[0]) * (Y - C[1])) / den, l3 = 1 - l1 - l2;
    if (l1 < 0 || l2 < 0 || l3 < 0) continue;
    const z = l1 * A[2] + l2 * B[2] + l3 * C[2], i = y * N + x;
    if (z <= depth[i]) continue;
    const t = texel(l1 * T[0][0] + l2 * T[1][0] + l3 * T[2][0], l1 * T[0][1] + l2 * T[1][1] + l3 * T[2][1]);
    depth[i] = z; cover[i] = 1;
    col.set([t[0] * shade, t[1] * shade, t[2] * shade], i * 3);
  }
}
const out = Buffer.alloc(CANVAS * CANVAS * 4);
for (let y = 0; y < CANVAS; y++) for (let x = 0; x < CANVAS; x++) {
  let r = 0, g = 0, b = 0, n = 0;
  for (let j = 0; j < SS; j++) for (let i = 0; i < SS; i++) {
    const q = (y * SS + j) * N + x * SS + i; if (!cover[q]) continue;
    r += col[q * 3]; g += col[q * 3 + 1]; b += col[q * 3 + 2]; n++;
  }
  if (n) out.set([r / n, g / n, b / n].map(v => Math.max(0, Math.min(255, Math.round(v * 255)))).concat(Math.round(255 * n / (SS * SS))), (y * CANVAS + x) * 4);
}
const image = png(out, CANVAS, CANVAS);
let stale = 0;
for (const file of OUT) {
  if (check) { if (!fs.existsSync(file) || !fs.readFileSync(file).equals(image)) { console.log('STALE ' + path.relative(root, file)); stale++; } }
  else fs.writeFileSync(file, image);
}
if (stale) process.exit(1);
console.log(JSON.stringify({triangles: tris.length, canvas: CANVAS, fill: FILL, view: {pitch: PITCH, roll: ROLL}, wrote: !check}));
