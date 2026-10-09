// Normal-map relief audit (docs/rendering/shader_pbr_tuning_v1.md). Fine, coherent relief (lines or specks a few texels
// wide) on a flat tiling block texture reads as hard grain under light grazing the surface (Sundial at night), even when
// its mean tilt is only a degree or two (2026-10-09: the sidewalk broom lines and the concrete sand of Ground Materials V1).
//   node tools/audit-normal-relief.mjs [N]            -> ranks every block _n texture (<= 512 px: tiling block textures,
//                                                      not mesh atlases) by its fine relief: the RMS tilt of the normals
//                                                      minus their 7 x 7 box blur (detail under ~7 texels), beside the
//                                                      overall RMS tilt; prints the top N (default 40)
//   node tools/audit-normal-relief.mjs --stretch IN OUT  -> writes OUT, IN's normal x / y deviation x 12 around grey, to
//                                                      look at the pattern (judge maps by pattern and scale, not means)
// High numbers from narrow intended relief (mortar joints, tile grout, panel ribs, warning domes) are fine; the fault is a
// raised number spread evenly over a flat field. Field relief rule: features 5..16 texels wide, about 0.1..0.15 mm deep
// (FIELD in tools/build-ground-materials-v1.mjs).
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {readPng, png} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const BLOCKS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/textures/block');

function normals(file) {
  const r = readPng(fs.readFileSync(file)), n = r.w * r.h, nx = new Float32Array(n), ny = new Float32Array(n);
  for (let i = 0; i < n; i++) { nx[i] = (r.px[i * r.bpp] - 128) / 127; ny[i] = (r.px[i * r.bpp + 1] - 128) / 127; }
  return {w: r.w, h: r.h, nx, ny};
}
function audit(file) {
  const {w: W, h: H, nx, ny} = normals(file);
  const blur = a => { const o = new Float32Array(W * H); for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) { let s = 0; for (let dy = -3; dy <= 3; dy++) for (let dx = -3; dx <= 3; dx++) s += a[((y + dy + H) % H) * W + ((x + dx + W) % W)]; o[y * W + x] = s / 49; } return o; };
  const bx = blur(nx), by = blur(ny);
  let fine = 0, all = 0;
  for (let i = 0; i < W * H; i++) { const ex = nx[i] - bx[i], ey = ny[i] - by[i]; fine += ex * ex + ey * ey; all += nx[i] * nx[i] + ny[i] * ny[i]; }
  const deg = v => Math.atan(Math.sqrt(v / (W * H))) * 180 / Math.PI;
  return {size: W, all: deg(all), fine: deg(fine)};
}

const args = process.argv.slice(2);
if (args[0] === '--stretch') {
  const [, input, output] = args, {w, h, nx, ny} = normals(input), out = Buffer.alloc(w * h * 4);
  for (let i = 0; i < w * h; i++) {
    out[i * 4] = Math.max(0, Math.min(255, 128 + nx[i] * 127 * 12));
    out[i * 4 + 1] = Math.max(0, Math.min(255, 128 + ny[i] * 127 * 12));
    out[i * 4 + 2] = 128; out[i * 4 + 3] = 255;
  }
  fs.writeFileSync(output, png(out, w, h));
  console.log('wrote ' + output);
} else {
  const files = [];
  const walk = d => { for (const e of fs.readdirSync(d, {withFileTypes: true})) { const p = path.join(d, e.name); if (e.isDirectory()) walk(p); else if (e.name.endsWith('_n.png')) files.push(p); } };
  walk(BLOCKS);
  const rows = [];
  for (const f of files) {
    let a; try { a = audit(f); } catch { continue; }
    if (a.size <= 512) rows.push([path.relative(BLOCKS, f).split(path.sep).join('/'), a]);
  }
  rows.sort((p, q) => q[1].fine - p[1].fine);
  for (const [name, a] of rows.slice(0, Number(args[0] || 40)))
    console.log(name.padEnd(52), String(a.size).padStart(4), 'rms', a.all.toFixed(2) + '°', 'fine', a.fine.toFixed(2) + '°');
  console.log('scanned ' + rows.length);
}
