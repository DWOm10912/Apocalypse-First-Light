// Wood finish study (2026-10-15; docs/models/wood_furniture_v2.md "家具木色"): the user found the furniture's neutral
// finished wood ugly and asked what colour real wooden furniture is. This writes, for the current finish and for real
// finishes (target CIELAB of the face, D65), the sRGB face / latewood colours and a 240 x 240 planed-stock tile drawn the
// way tools/furniture/wood-furniture-art.mjs draws `wood` (same grain), into ../m4_sheets/finish_<key>.png, and prints
// the table; and the five wood textures per finish into finish_tex/<key>/. Design use only; the game textures are not touched.
//   node design/buildings/forest_start_home_v1/m4_source/wood_finish_swatches.mjs
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {png} from '../../../../tools/cube-slab-mesh-lib.mjs';
import {mul, mix, vn} from '../../../../tools/mesh-trees/hd-art.mjs';
import {grain, renderX} from '../../../../tools/cabin/cabin-art.mjs';
import {FURNITURE_TEXTURES, setFinish} from '../../../../tools/furniture/wood-furniture-art.mjs';

const OUT = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'm4_sheets');

const lin = c => { c /= 255; return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4; };
const gam = c => 255 * (c <= 0.0031308 ? 12.92 * c : 1.055 * c ** (1 / 2.4) - 0.055);
const W = [0.95047, 1, 1.08883];
function lab([r, g, b]) {
  const [R, G, B] = [lin(r), lin(g), lin(b)];
  const X = 0.4124 * R + 0.3576 * G + 0.1805 * B, Y = 0.2126 * R + 0.7152 * G + 0.0722 * B, Z = 0.0193 * R + 0.1192 * G + 0.9505 * B;
  const f = t => t > 216 / 24389 ? Math.cbrt(t) : (24389 / 27 * t + 16) / 116;
  const [fx, fy, fz] = [f(X / W[0]), f(Y / W[1]), f(Z / W[2])];
  return [116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)];
}
function rgb([L, a, b]) {
  const fy = (L + 16) / 116, fx = fy + a / 500, fz = fy - b / 200;
  const inv = t => t ** 3 > 216 / 24389 ? t ** 3 : (116 * t - 16) / (24389 / 27);
  const [X, Y, Z] = [inv(fx) * W[0], inv(fy) * W[1], inv(fz) * W[2]];
  const R = 3.2406 * X - 1.5372 * Y - 0.4986 * Z, G = -0.9689 * X + 1.8758 * Y + 0.0415 * Z, B = 0.0557 * X - 0.204 * Y + 1.057 * Z;
  return [R, G, B].map(v => Math.round(Math.max(0, Math.min(255, gam(Math.max(0, v))))));
}
const fmt = v => v.map(x => x.toFixed(1)).join(', ');

// the face (earlywood-dominated average of the surface) and the latewood: darker, a little more saturated
const late = ([L, a, b], dL, k) => [L - dL, a * k, b * k];
const FINISHES = [
  {key: 'current', name: '现在（中性中棕，V1 起）', face: lab([142, 112, 82])},
  {key: 'oak_clear', name: '白橡清漆（原色，略泛黄）', face: [63, 8, 25]},
  {key: 'honey', name: '蜂蜜色 / Golden Oak（橡木上琥珀色）', face: [57, 12, 33]},
  {key: 'pine_amber', name: '松木 + 琥珀虫胶（50 年代木屋）', face: [62, 14, 38]},
  {key: 'cherry', name: '老樱桃木（红棕，晒深）', face: [43, 18, 24]},
  {key: 'walnut', name: '黑胡桃油饰面（巧克力棕）', face: [37, 7, 15]},
];

fs.mkdirSync(OUT, {recursive: true});
console.log('key | face L a b | face sRGB | late sRGB | chroma | hue');
for (const f of FINISHES) {
  const FACE = f.key === 'current' ? [142, 112, 82] : rgb(f.face);
  const LATE = f.key === 'current' ? [112, 86, 62] : rgb(late(f.face, 11, 1.05));
  const L0 = lab(FACE), C = Math.hypot(L0[1], L0[2]), h = Math.atan2(L0[2], L0[1]) * 180 / Math.PI;
  console.log(`${f.key} | ${fmt(L0)} | ${FACE} | ${LATE} | ${C.toFixed(1)} | ${h.toFixed(1)}`);
  // the planed stock, as wood-furniture-art lumber(false)
  const N = 240, img = Buffer.alloc(N * N * 4);
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const c = mul(mix(FACE, LATE, grain(y, x, 8, 6000, x, y) * 0.45), 1 + 0.02 * vn(x, y, 6003, 120, 30));
    const o = (y * N + x) * 4;
    img[o] = Math.max(0, Math.min(255, Math.round(c[0]))); img[o + 1] = Math.max(0, Math.min(255, Math.round(c[1])));
    img[o + 2] = Math.max(0, Math.min(255, Math.round(c[2]))); img[o + 3] = 255;
  }
  fs.writeFileSync(path.join(OUT, `finish_${f.key}.png`), png(img, N, N));
  f.FACE = FACE; f.LATE = LATE;
  // the wood textures of this finish for the preview (furniture_v2_preview.html window.finishSheet): finish_tex/<key>/<name>.png
  setFinish(FACE, LATE);
  const dir = path.join(path.dirname(fileURLToPath(import.meta.url)), 'finish_tex', f.key);
  fs.mkdirSync(dir, {recursive: true});
  for (const name of ['wood', 'wood_v', 'wood_top', 'wood_panel', 'wood_inside']) fs.writeFileSync(path.join(dir, name + '.png'), renderX(FURNITURE_TEXTURES[name][0](), {height: FURNITURE_TEXTURES[name][1]})[0]);
}
fs.writeFileSync(path.join(OUT, 'finish_table.json'), JSON.stringify(FINISHES.map(({key, name, FACE, LATE}) => ({key, name, FACE, LATE})), null, 1));
