// The T15's Creative item icon (VehicleSpawnerItem, 2026-10-21): a small side view of the truck, drawn offline from the
// Blockbench sources (outside, front, wheels, and the chassis, engine and cab behind the wheel arches and gaps;
// src/main/blockbench/vehicles) with the runtime atlases, painted deep
// red as the spawner's default. Orthographic from the driver's side (-X), front to the right; flat-lit by the face
// normal, the glass over a dark cab; rendered 16x larger and box-filtered down. Needs ffmpeg (PNG in and out).
//
//   node tools/pickup-t15/render-icon.mjs            -> src/main/resources/assets/apocalypse_firstlight/textures/item/pickup_t15.png
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const BB = path.join(ROOT, 'src/main/blockbench/vehicles');
const ATLASES = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/textures/entity/vehicle');
const OUT = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/textures/item/pickup_t15.png');
const SIZE = 64, SS = 16, MARGIN = 1;
const PAINT = 0x7B1E1A, PAINT2 = 0x7B1E1A;   // deep red (VehicleSpawnerItem.PAINTS)
const CAB_DARK = [22, 25, 28];
// wheel hubs (pickup_t15.rig.json, blocks); left wheels are the mesh turned 180 deg about Y
const HUBS = [[-0.712, 0.36, -1.6, true], [0.712, 0.36, -1.6, false], [-0.712, 0.36, 1.25, true], [0.712, 0.36, 1.25, false]];

function readPng(file) {
  const dims = execFileSync('ffprobe', ['-v', 'error', '-select_streams', 'v:0', '-show_entries', 'stream=width,height', '-of', 'csv=p=0', file]).toString().trim().split(',').map(Number);
  const raw = execFileSync('ffmpeg', ['-v', 'error', '-i', file, '-f', 'rawvideo', '-pix_fmt', 'rgba', '-'], { maxBuffer: 1 << 28 });
  return { w: dims[0], h: dims[1], data: raw };
}

// triangles: {p: [[x,y,z] px], uv: [[u,v] texels], kind}
const tris = [];
function kindOf(name) {
  if (name.endsWith('_paint2')) return 'paint2';
  if (name.endsWith('_paint')) return 'paint';
  if (name.endsWith('_glass')) return 'glass';
  return 'plain';
}
function area(pts) {
  let nx = 0, ny = 0, nz = 0;
  for (let i = 0; i < pts.length; i++) {
    const a = pts[i], b = pts[(i + 1) % pts.length];
    nx += (a[1] - b[1]) * (a[2] + b[2]); ny += (a[2] - b[2]) * (a[0] + b[0]); nz += (a[0] - b[0]) * (a[1] + b[1]);
  }
  return Math.hypot(nx, ny, nz);
}
const atlases = {};
function addModel(file, transform) {
  const m = JSON.parse(fs.readFileSync(path.join(BB, file), 'utf8'));
  const name = m.textures[0].name;
  const atlas = atlases[name] ??= readPng(path.join(ATLASES, name));
  for (const e of m.elements) {
    if (e.type !== 'mesh') continue;
    const kind = kindOf(e.name);
    for (const f of Object.values(e.faces)) {
      let ids = f.vertices;
      if (ids.length < 3) continue;
      const pos = id => transform(e.vertices[id].map((v, a) => v + e.origin[a]));
      if (ids.length === 4) {
        // Blockbench keeps a quad's corners unordered: the cyclic order is the one with the largest area
        const orders = [[0, 1, 2, 3], [0, 2, 1, 3], [0, 1, 3, 2]];
        let best = orders[0], bestArea = -1;
        for (const o of orders) { const a = area(o.map(i => pos(ids[i]))); if (a > bestArea) { bestArea = a; best = o; } }
        ids = best.map(i => ids[i]);
      }
      for (let i = 1; i + 1 < ids.length; i++) {
        const t = [ids[0], ids[i], ids[i + 1]];
        tris.push({ p: t.map(pos), uv: t.map(id => f.uv[id]), kind, atlas });
      }
    }
  }
}
addModel('pickup_t15.bbmodel', v => v);
addModel('pickup_t15_front.bbmodel', v => v);
for (const f of ['pickup_t15_chassis.bbmodel', 'pickup_t15_engine.bbmodel', 'pickup_t15_cab.bbmodel', 'pickup_t15_seat.bbmodel']) addModel(f, v => v);
for (const [hx, hy, hz, turned] of HUBS)
  addModel('pickup_t15_wheel.bbmodel', v => turned ? [-v[0] + hx * 16, v[1] + hy * 16, -v[2] + hz * 16] : [v[0] + hx * 16, v[1] + hy * 16, v[2] + hz * 16]);

// frame: screen x = -z (front right), screen y = -y, depth = x (smaller nearer: seen from -X)
let minZ = 1e9, maxZ = -1e9, minY = 1e9, maxY = -1e9;
for (const t of tris) for (const p of t.p) { minZ = Math.min(minZ, p[2]); maxZ = Math.max(maxZ, p[2]); minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]); }
const W = SIZE * SS, scale = (SIZE - 2 * MARGIN) * SS / (maxZ - minZ);
const cx = W / 2 + (maxZ + minZ) / 2 * scale, cy = W / 2 + (maxY + minY) / 2 * scale;
const sx = p => cx - p[2] * scale, sy = p => cy - p[1] * scale;

const colour = new Float32Array(W * W * 4), depth = new Float32Array(W * W).fill(Infinity);
const light = (() => { const l = [-1, 0.7, -0.35], n = Math.hypot(...l); return l.map(x => x / n); })();
const rgb = c => [(c >> 16 & 255) / 255, (c >> 8 & 255) / 255, (c & 255) / 255];
const tints = { paint: rgb(PAINT), paint2: rgb(PAINT2) };

for (const t of tris) {
  const [a, b, c] = t.p;
  const u = [b[0] - a[0], b[1] - a[1], b[2] - a[2]], v = [c[0] - a[0], c[1] - a[1], c[2] - a[2]];
  let n = [u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]];
  const nl = Math.hypot(...n); if (nl < 1e-9) continue;
  n = n.map(x => x / nl);
  if (n[0] > 0) n = n.map(x => -x);   // two-sided: face the viewer
  const shade = 0.5 + 0.5 * Math.max(0, n[0] * light[0] + n[1] * light[1] + n[2] * light[2]);
  const atlas = t.atlas;
  const X = t.p.map(sx), Y = t.p.map(sy);
  const den = (Y[1] - Y[2]) * (X[0] - X[2]) + (X[2] - X[1]) * (Y[0] - Y[2]);
  if (Math.abs(den) < 1e-9) continue;
  const x0 = Math.max(0, Math.floor(Math.min(...X))), x1 = Math.min(W - 1, Math.ceil(Math.max(...X)));
  const y0 = Math.max(0, Math.floor(Math.min(...Y))), y1 = Math.min(W - 1, Math.ceil(Math.max(...Y)));
  for (let py = y0; py <= y1; py++) for (let px = x0; px <= x1; px++) {
    const qx = px + 0.5, qy = py + 0.5;
    const w0 = ((Y[1] - Y[2]) * (qx - X[2]) + (X[2] - X[1]) * (qy - Y[2])) / den;
    const w1 = ((Y[2] - Y[0]) * (qx - X[2]) + (X[0] - X[2]) * (qy - Y[2])) / den;
    const w2 = 1 - w0 - w1;
    if (w0 < 0 || w1 < 0 || w2 < 0) continue;
    const d = w0 * a[0] + w1 * b[0] + w2 * c[0];
    const k = py * W + px;
    if (d >= depth[k]) continue;
    const tu = w0 * t.uv[0][0] + w1 * t.uv[1][0] + w2 * t.uv[2][0], tv = w0 * t.uv[0][1] + w1 * t.uv[1][1] + w2 * t.uv[2][1];
    const ix = Math.min(atlas.w - 1, Math.max(0, Math.floor(tu))), iy = Math.min(atlas.h - 1, Math.max(0, Math.floor(tv)));
    const o = (iy * atlas.w + ix) * 4;
    const alpha = atlas.data[o + 3] / 255;
    if (t.kind !== 'glass' && alpha < 0.5) continue;   // cutout
    let col = [atlas.data[o] / 255, atlas.data[o + 1] / 255, atlas.data[o + 2] / 255];
    if (tints[t.kind]) col = col.map((x, i) => x * tints[t.kind][i]);
    // the glass over a dark cab (the interior is not drawn)
    if (t.kind === 'glass') col = col.map((x, i) => x * alpha + CAB_DARK[i] / 255 * (1 - alpha));
    depth[k] = d;
    colour.set([col[0] * shade, col[1] * shade, col[2] * shade, 1], k * 4);
  }
}

// box filter SSxSS (premultiplied alpha)
const out = Buffer.alloc(SIZE * SIZE * 4);
for (let y = 0; y < SIZE; y++) for (let x = 0; x < SIZE; x++) {
  let r = 0, g = 0, b = 0, al = 0;
  for (let j = 0; j < SS; j++) for (let i = 0; i < SS; i++) {
    const k = ((y * SS + j) * W + x * SS + i) * 4;
    r += colour[k] * colour[k + 3]; g += colour[k + 1] * colour[k + 3]; b += colour[k + 2] * colour[k + 3]; al += colour[k + 3];
  }
  const o = (y * SIZE + x) * 4;
  if (al > 0) { out[o] = Math.round(Math.min(1, Math.pow(r / al, 1)) * 255); out[o + 1] = Math.round(Math.min(1, g / al) * 255); out[o + 2] = Math.round(Math.min(1, b / al) * 255); }
  out[o + 3] = Math.round(al / (SS * SS) * 255);
}
const raw = path.join(os.tmpdir(), 'afl_t15_icon.rgba');
fs.writeFileSync(raw, out);
fs.mkdirSync(path.dirname(OUT), { recursive: true });
execFileSync('ffmpeg', ['-v', 'error', '-y', '-f', 'rawvideo', '-pix_fmt', 'rgba', '-s', SIZE + 'x' + SIZE, '-i', raw, '-pix_fmt', 'rgba', OUT]);
fs.unlinkSync(raw);
console.log('icon', OUT, tris.length + ' triangles', 'truck ' + ((maxZ - minZ) / 16).toFixed(2) + ' x ' + ((maxY - minY) / 16).toFixed(2) + ' m');
