// Crosshair V1 (docs/ui/crosshair_v1.md): offline mock of client/AflCrosshair + NativeDynamicCrosshair (1080 lines: unit 1,
// GUI scale 4), each pixel's coverage of the smooth shapes as AflCrosshair#capsule fades it (solid to half a pixel inside the
// edge, nothing half a pixel outside), upscaled x4 to show the pixels. Usage: node tools/ui-crosshair-mock-v1.mjs <out.png>
import fs from 'node:fs';
import {png} from './cube-slab-mesh-lib.mjs';
const CELL = 120, Z = 4, COLS = 6, W = CELL * COLS, H = CELL * 2;
const img = Buffer.alloc(W * H * 4);
const bgs = [[110, 92, 70], [205, 215, 225]];   // wood interior, bright sky
for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) { const b = bgs[Math.floor(y / CELL)]; img.set([...b, 255], (y * W + x) * 4); }
let ox = 0, oy = 0;
const clamp = (v, a, b) => Math.min(b, Math.max(a, v));
function capsule(ax, ay, bx, by, half, a, rgb) {
  half = Math.max(0.5, half);
  const dx = bx - ax, dy = by - ay, ll = dx * dx + dy * dy, reach = Math.ceil(Math.max(Math.abs(ax), Math.abs(bx), Math.abs(ay), Math.abs(by)) + half + 2);
  for (let py = -reach; py < reach; py++) for (let px = -reach; px < reach; px++) {
    // the pixel's centre; the screen centre sits between pixels (AflCrosshair#begin: width / 2)
    const x = px + 0.5, y = py + 0.5, t = ll ? clamp(((x - ax) * dx + (y - ay) * dy) / ll, 0, 1) : 0;
    const d = Math.hypot(x - ax - t * dx, y - ay - t * dy) - half, cov = clamp(0.5 - d, 0, 1) * a;
    if (cov <= 0) continue;
    const X = ox + px, Y = oy + py; if (X < 0 || Y < 0 || X >= W || Y >= H) continue;
    const i = (Y * W + X) * 4; for (let c = 0; c < 3; c++) img[i + c] = Math.round(img[i + c] * (1 - cov) + rgb[c] * cov);
  }
}
const COLOR = [0xE8, 0xE8, 0xE2], SHADE = [16, 16, 16], u = 1, DOT_RADIUS = 1.75, RING = 1;
const dot = (a) => { capsule(0, 0, 0, 0, DOT_RADIUS + RING, a * 0.45, SHADE); capsule(0, 0, 0, 0, DOT_RADIUS, a, COLOR); };
function arms(from, len, half, a, rgb) {
  const d = Math.SQRT1_2, s = from + half, e = Math.max(s, from + len - half);
  for (const pass of [0, 1]) for (const sx of [-1, 1]) for (const sy of [-1, 1])
    if (pass === 0) capsule(sx * d * s, sy * d * s, sx * d * e, sy * d * e, half + RING, a * 0.35, SHADE);
    else capsule(sx * d * s, sy * d * s, sx * d * e, sy * d * e, half, a, rgb);
}
const gs = 4, thickness = 0.8, lineLength = 4;
const gun = (gap, a, flash = 0, head = false) => {
  const rgb = flash > 0 ? COLOR.map((c, i) => Math.round(c * (1 - flash) + (head ? [255, 59, 48] : [255, 255, 255])[i] * flash)) : COLOR;
  const half = clamp(thickness * gs * 0.3, 0.75 * u, 2.5 * u);
  dot(a); arms(Math.max(gap * gs, (DOT_RADIUS + 3) * u), Math.max((lineLength + 2 * flash) * gs, 2 * half + 2 * u), half, Math.max(a, flash), rgb);
};
const cells = [() => dot(0.3), () => dot(0.9), () => gun(3, 0.9), () => gun(9, 0.9), () => gun(3, 0.9, 0.8, false), () => gun(3, 0.9, 0.8, true)];
for (let r = 0; r < 2; r++) cells.forEach((f, i) => { ox = i * CELL + CELL / 2; oy = r * CELL + CELL / 2; f(); });
const big = Buffer.alloc(W * Z * H * Z * 4);
for (let y = 0; y < H * Z; y++) for (let x = 0; x < W * Z; x++) img.copy(big, (y * W * Z + x) * 4, (Math.floor(y / Z) * W + Math.floor(x / Z)) * 4, (Math.floor(y / Z) * W + Math.floor(x / Z)) * 4 + 4);
fs.writeFileSync(process.argv[2], png(big, W * Z, H * Z));
// the same at 1:1, as the screen shows it
fs.writeFileSync(process.argv[2].replace(/\.png$/, '_1x.png'), png(img, W, H));
