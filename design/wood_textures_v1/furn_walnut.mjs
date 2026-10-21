// furniture lacquer options: walnut veneers vs the v1 oak in walnut stain
process.env.WOOD_LIB = '1';
const L = await import('./wood_samples.mjs');
import path from 'node:path';
import {createRequire} from 'node:module';
const sharp = createRequire(import.meta.url)('D:/Minecraft Modding/Apocalypse First Light/wiki/node_modules/sharp');
const OUT = process.argv[3], WAL = process.argv[5];
const OPTS = [
  {src: 'white_oak_veneer', dir: process.argv[2], dim: 0.5, zh: 'A 白栎，胡桃色漆（第 1 版）', T: [96, 64, 44], sat: 0.9, con: 1.35},
  {src: 'walnut_veneer_02', dir: WAL, dim: 1, zh: 'B 胡桃木 walnut_veneer_02', T: [92, 60, 42], sat: 0.85, con: 1.35},
  {src: 'smoked_walnut_veneer', dir: WAL, dim: 1, zh: 'C 胡桃木 smoked_walnut', T: [90, 58, 40], sat: 0.85, con: 1.25},
  {src: 'natural_walnut_veneer', dir: WAL, dim: 1, zh: 'D 胡桃木 natural_walnut', T: [94, 62, 44], sat: 0.6, con: 1.15},
];
const CELL = 256, GAP = 10, LEFT = 330, TOP = 110;
const W = LEFT + 4 * (CELL + GAP) + GAP, H = TOP + OPTS.length * (CELL + GAP) + 20;
const cv = L.canvas(W, H), texts = [];
texts.push({x: 20, y: 46, s: 'AFL 家具统一漆面：胡桃木对比（CC0 实拍做底，240 px）', size: 30, bold: true});
['漆面 240', '2×2 铺开', '漆面反光示意', '柜体示意'].forEach((c, i) => texts.push({x: LEFT + GAP + i * (CELL + GAP), y: TOP - 12, s: c, size: 18, fill: '#d0d0d0'}));
for (const [j, F] of OPTS.entries()) {
  const t = await L.photoTile(F.src, F.dim, 1.0, 240, {delightS: 0.25});
  // photoTile reads from SRC: point it at the right folder by symlink-free copy of the call
  let fc = t.col; fc.c = 3;
  if (L.grainVertical(fc)) fc = L.rotate90(fc);
  L.grade(fc, {target: F.T, sat: F.sat, con: F.con});
  const sm = new Float32Array(240 * 240); let m = 0;
  for (let i = 0; i < 240 * 240; i++) m += L.lum(fc.d[i * 3], fc.d[i * 3 + 1], fc.d[i * 3 + 2]) / (240 * 240);
  const sb = Buffer.alloc(240 * 240 * 4);
  for (let i = 0; i < 240 * 240; i++) { const l = L.lum(fc.d[i * 3], fc.d[i * 3 + 1], fc.d[i * 3 + 2]) / m; sm[i] = L.clamp(196 + 18 * (l - 1), 160, 215) / 255; sb[i * 4] = Math.round(sm[i] * 255); sb[i * 4 + 1] = 10; sb[i * 4 + 3] = 255; }
  const name = 'furniture_wood_' + 'ABCD'[j];
  await L.savePng(path.join(OUT, name + '.png'), L.toRGBA8(fc), 240, 240);
  await L.savePng(path.join(OUT, name + '_s.png'), sb, 240, 240);
  const gloss = L.img(240, 240, 3);
  for (let y = 0; y < 240; y++) for (let x = 0; x < 240; x++) { const i = y * 240 + x, band = Math.exp(-((((x - y * 0.6) - 70) / 38) ** 2)) * 0.5 + 0.06;
    for (let k = 0; k < 3; k++) gloss.d[i * 3 + k] = L.clamp(fc.d[i * 3 + k] * 0.92 + band * sm[i] * sm[i] * 0.55, 0, 1); }
  const Y = TOP + j * (CELL + GAP), X = i => LEFT + GAP + i * (CELL + GAP);
  texts.push({x: 20, y: Y + CELL / 2, s: F.zh, size: 20, bold: true});
  L.blit(cv, fc, X(0) + 8, Y + 8, 240);
  L.blit(cv, fc, X(1), Y, CELL, 2);
  L.blit(cv, gloss, X(2) + 8, Y + 8, 240);
  const fv = L.rotate90(fc); fv.c = 3;
  L.logPrism(cv, fc, null, fv, null, 1 / 32, X(3), Y, CELL);
}
const file = path.join(OUT, 'furniture_walnut_options.png');
await sharp(cv.b, {raw: {width: W, height: H, channels: 4}}).composite([{input: L.svgText(texts, W, H), top: 0, left: 0}]).png().toFile(file);
console.log('wrote', file);
