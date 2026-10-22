// BR-51 V3 wiki pictures (docs/native_guns/br51_01_v3_game_model_v1.md, "物品栏图标"), until Blockbench renders them:
// the game low poly and the three magazine meshes drawn offline with their own Base Color atlases, lit and filtered as
// tools/render-br51-01-inventory-icon.mjs.
//   node tools/render-br51-01-wiki.mjs
//   design/wiki/gun_renders/raw/br51_01.png      the gun, 3/4 from the right front above (as the other gun headers);
//                                                wiki/scripts/frame-renders.mjs frames it into design/wiki/gun_renders/
//   design/wiki/icons/raw/<magazine id>.png       512 px, 3/4 from the right front; wiki/scripts/icons.mjs builds the webp
// The gun leaves out the reload's spare magazines (bones reload_magazine, empty_old_mag), which sit inside the fitted one.
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {png, readPng, norm, newell, dot} from './cube-slab-mesh-lib.mjs';
import {lowParts, texturePath} from './build-br51-01-v3-native.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const assets = path.join(root, 'src/main/resources/assets/apocalypse_firstlight');
const LIGHT = norm([-0.35, 0.85, 0.4]);
const rad = d => d * Math.PI / 180;

// model (x right side, y up, muzzle / front -Z) -> view (x right, y up, z toward the eye): turned YAW about y so the
// front comes toward the eye, tipped PITCH so the top shows, seen from +X
const viewer = (yaw, pitch) => {
  const ca = Math.cos(rad(yaw)), sa = Math.sin(rad(yaw)), cp = Math.cos(rad(pitch)), sp = Math.sin(rad(pitch));
  return v => {
    const x = v[0] * ca - v[2] * sa, z = v[0] * sa + v[2] * ca;
    return [-z, v[1] * cp - x * sp, v[1] * sp + x * cp];
  };
};

// parts: [{vertices: [[x, y, z]], faces: [{v: [i...], uv: [[u, v] in atlas px]}]}]
function render(parts, atlas, view, W, H, SS, fill) {
  const tris = [];
  for (const p of parts) for (const f of p.faces) {
    const P = f.v.map(i => view(p.vertices[i]));
    let n = norm(newell(P));
    if (n[2] < 0) n = n.map(v => -v);
    const shade = 0.58 + 0.5 * Math.max(0, dot(n, LIGHT));
    for (let q = 1; q + 1 < P.length; q++) tris.push({P: [P[0], P[q], P[q + 1]], T: [f.uv[0], f.uv[q], f.uv[q + 1]], shade});
  }
  const lo = [Infinity, Infinity], hi = [-Infinity, -Infinity];
  for (const t of tris) for (const p of t.P) for (let k = 0; k < 2; k++) { lo[k] = Math.min(lo[k], p[k]); hi[k] = Math.max(hi[k], p[k]); }
  const NW = W * SS, NH = H * SS, scale = fill * Math.min(NW / (hi[0] - lo[0]), NH / (hi[1] - lo[1]));
  const cx = (lo[0] + hi[0]) / 2, cy = (lo[1] + hi[1]) / 2;
  for (const t of tris) t.P = t.P.map(p => [NW / 2 + (p[0] - cx) * scale, NH / 2 - (p[1] - cy) * scale, p[2]]);
  const col = new Float32Array(NW * NH * 3), cover = new Uint8Array(NW * NH), depth = new Float32Array(NW * NH).fill(-Infinity);
  const texel = (u, v) => {
    const x = Math.min(atlas.w - 1, Math.max(0, Math.floor(u))), y = Math.min(atlas.h - 1, Math.max(0, Math.floor(v))), o = (y * atlas.w + x) * atlas.bpp;
    return [atlas.px[o] / 255, atlas.px[o + 1] / 255, atlas.px[o + 2] / 255];
  };
  for (const {P: [A, B, C], T, shade} of tris) {
    const den = (B[1] - C[1]) * (A[0] - C[0]) + (C[0] - B[0]) * (A[1] - C[1]); if (Math.abs(den) < 1e-12) continue;
    const x0 = Math.max(0, Math.floor(Math.min(A[0], B[0], C[0]))), x1 = Math.min(NW - 1, Math.ceil(Math.max(A[0], B[0], C[0])));
    const y0 = Math.max(0, Math.floor(Math.min(A[1], B[1], C[1]))), y1 = Math.min(NH - 1, Math.ceil(Math.max(A[1], B[1], C[1])));
    for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
      const X = x + 0.5, Y = y + 0.5;
      const l1 = ((B[1] - C[1]) * (X - C[0]) + (C[0] - B[0]) * (Y - C[1])) / den, l2 = ((C[1] - A[1]) * (X - C[0]) + (A[0] - C[0]) * (Y - C[1])) / den, l3 = 1 - l1 - l2;
      if (l1 < 0 || l2 < 0 || l3 < 0) continue;
      const z = l1 * A[2] + l2 * B[2] + l3 * C[2], i = y * NW + x;
      if (z <= depth[i]) continue;
      const t = texel(l1 * T[0][0] + l2 * T[1][0] + l3 * T[2][0], l1 * T[0][1] + l2 * T[1][1] + l3 * T[2][1]);
      depth[i] = z; cover[i] = 1;
      col.set([t[0] * shade, t[1] * shade, t[2] * shade], i * 3);
    }
  }
  const out = Buffer.alloc(W * H * 4);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    let r = 0, g = 0, b = 0, n = 0;
    for (let j = 0; j < SS; j++) for (let i = 0; i < SS; i++) {
      const q = (y * SS + j) * NW + x * SS + i; if (!cover[q]) continue;
      r += col[q * 3]; g += col[q * 3 + 1]; b += col[q * 3 + 2]; n++;
    }
    if (n) out.set([r / n, g / n, b / n].map(v => Math.max(0, Math.min(255, Math.round(v * 255)))).concat(Math.round(255 * n / (SS * SS))), (y * W + x) * 4);
  }
  return {image: png(out, W, H), triangles: tris.length};
}

const write = (rel, image) => { const file = path.join(root, rel); fs.mkdirSync(path.dirname(file), {recursive: true}); fs.writeFileSync(file, image); return rel; };
const report = {};

const gun = lowParts().parts.filter(p => p.bone !== 'reload_magazine' && p.bone !== 'empty_old_mag');
const gunAtlas = readPng(fs.readFileSync(texturePath));
const g = render(gun, gunAtlas, viewer(28, 24), 1600, 1000, 3, 0.9);
report.gun = {file: write('design/wiki/gun_renders/raw/br51_01.png', g.image), triangles: g.triangles};

const magsAtlas = readPng(fs.readFileSync(path.join(root, 'src/main/blockbench/textures/br51_01_v3_mags.png')));
for (const [id, atlas] of [['br51_01_magazine', gunAtlas], ['br51_01_extended_magazine', magsAtlas], ['br51_drum_magazine_50', magsAtlas]]) {
  const mesh = JSON.parse(fs.readFileSync(path.join(assets, 'meshes', id + '.aflmesh.json'), 'utf8'));
  const [tw, th] = mesh.texture_size;
  // magazine_root sits at the origin, so the bone-local vertices are the model's (blocks -> px)
  const parts = mesh.parts.map(p => ({vertices: p.vertices.map(v => [v[0] * 16, v[1] * 16, v[2] * 16]),
    faces: p.faces.map(f => ({v: f, uv: f.map(i => [p.vertices[i][3] * tw, p.vertices[i][4] * th])}))}));
  const r = render(parts, atlas, viewer(-35, 18), 512, 512, 4, 0.94);
  report[id] = {file: write('design/wiki/icons/raw/' + id + '.png', r.image), triangles: r.triangles};
}
console.log(JSON.stringify(report));
