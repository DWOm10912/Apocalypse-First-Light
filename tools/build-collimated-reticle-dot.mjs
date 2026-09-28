// Collimated reticle dot texture for NativeCollimatedReticleRendering (first-person red dot sights).
//   node tools/build-collimated-reticle-dot.mjs          -> writes textures/effects/collimated_reticle_dot{,_s}.png
//   node tools/build-collimated-reticle-dot.mjs --check  -> verifies both files are up to date
// One small soft-edged disc. RGB is white everywhere (also under alpha 0, so linear filtering never darkens the rim);
// the optic data (assets/<ns>/optics/<item>.json) tints it through the vertex colour and sets its angular size.
// Alpha: 1 inside CORE, smoothstep falloff to 0 at EDGE, transparent margin to the quad border (half alpha at 0.68 of
// the quad radius). _s (LabPBR): R smoothness 0, G 10 (dielectric), B 0, A emission = 254 x alpha, so shader packs
// that read LabPBR emission light it as an emitter; no _n (flat default).
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import {fileURLToPath} from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const DIR = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/textures/effects');
const SIZE = 32, CORE = 0.42, EDGE = 0.94;

const smoothstep = (a, b, x) => { const t = Math.min(1, Math.max(0, (x - a) / (b - a))); return t * t * (3 - 2 * t); };
const albedo = Buffer.alloc(SIZE * SIZE * 4), specular = Buffer.alloc(SIZE * SIZE * 4);
for (let y = 0; y < SIZE; y++) for (let x = 0; x < SIZE; x++) {
  const r = Math.hypot(x + 0.5 - SIZE / 2, y + 0.5 - SIZE / 2) / (SIZE / 2);
  const a = 1 - smoothstep(CORE, EDGE, r), k = (y * SIZE + x) * 4;
  albedo.set([255, 255, 255, Math.round(255 * a)], k);
  specular.set([0, 10, 0, Math.round(254 * a)], k);
}

const crcTable = Array.from({length: 256}, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc32 = buf => { let c = 0xFFFFFFFF; for (const b of buf) c = crcTable[(c ^ b) & 0xFF] ^ (c >>> 8); return (c ^ 0xFFFFFFFF) >>> 0; };
const encodePng = rgba => {
  const chunk = (type, data) => { const len = Buffer.alloc(4); len.writeUInt32BE(data.length); const td = Buffer.concat([Buffer.from(type), data]), crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td)); return Buffer.concat([len, td, crc]); };
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(SIZE, 0); ihdr.writeUInt32BE(SIZE, 4); ihdr[8] = 8; ihdr[9] = 6;
  const raw = Buffer.alloc(SIZE * (SIZE * 4 + 1)); for (let y = 0; y < SIZE; y++) rgba.copy(raw, y * (SIZE * 4 + 1) + 1, y * SIZE * 4, (y + 1) * SIZE * 4);
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, {level: 9})), chunk('IEND', Buffer.alloc(0))]);
};

const outputs = [['collimated_reticle_dot.png', encodePng(albedo)], ['collimated_reticle_dot_s.png', encodePng(specular)]];
if (process.argv.includes('--check')) {
  const stale = outputs.filter(([name, data]) => { const file = path.join(DIR, name); return !fs.existsSync(file) || !fs.readFileSync(file).equals(data); });
  for (const [name] of stale) console.error('STALE ' + name);
  console.log(stale.length ? 'FAIL' : 'PASS: ' + outputs.map(([name]) => name).join(', '));
  process.exit(stale.length ? 1 : 0);
}
fs.mkdirSync(DIR, {recursive: true});
for (const [name, data] of outputs) fs.writeFileSync(path.join(DIR, name), data);
console.log(`wrote ${outputs.length} textures (${SIZE}x${SIZE}) to ${path.relative(ROOT, DIR)}`);
