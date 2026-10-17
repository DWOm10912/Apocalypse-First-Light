// The T15's lamp flare (docs/vehicles/first_pickup_v0_design.md 8.10, client/vehicle/HeadlightFx, 2026-10-16):
// textures/effect/headlight_flare (256 x 256, drawn with linear filtering: LiquidRenderTypes.HEADLIGHT_GLOW): white, the
// alpha a small hot core over a soft halo, reaching exactly 0 at the edge (no rim). The fire glow it replaced was 64 px
// drawn nearest-filtered, a ring of big pixels at headlamp size. _s: emissive (LabPBR alpha 254) where it shows.
//   node tools/build-headlight-flare.mjs [--check]
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {png} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const A = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const check = process.argv.includes('--check');
const N = 256;
/** Core and halo widths (share of the radius) and the halo's strength next to the core's 1. */
const CORE = 0.10, HALO = 0.42, HALO_K = 0.32;

const buf = Buffer.alloc(N * N * 4), s = Buffer.alloc(N * N * 4);
for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
  const d = Math.hypot(x + 0.5 - N / 2, y + 0.5 - N / 2) / (N / 2), o = (y * N + x) * 4;
  const edge = d >= 1 ? 0 : (1 - d * d) ** 2;   // smooth to 0 at the rim
  const a = Math.min(1, Math.exp(-((d / CORE) ** 2)) + HALO_K * Math.exp(-((d / HALO) ** 2))) * edge;
  buf[o] = buf[o + 1] = buf[o + 2] = 255;
  buf[o + 3] = Math.round(255 * a);
  s[o + 3] = buf[o + 3] > 4 ? 254 : 255;
}
const out = {
  'textures/effect/headlight_flare.png': png(buf, N, N),
  'textures/effect/headlight_flare_s.png': png(s, N, N),
};
let stale = 0;
for (const [rel, data] of Object.entries(out)) {
  const file = path.join(A, rel);
  if (check) {
    if (!fs.existsSync(file) || !fs.readFileSync(file).equals(data)) { console.log('stale: ' + rel); stale++; }
  } else fs.writeFileSync(file, data);
}
const mid = buf[((N / 2) * N + N / 2) * 4 + 3], q = buf[((N / 2) * N + N / 2 + N / 4) * 4 + 3], rim = buf[((N / 2) * N + N - 1) * 4 + 3];
console.log(`${check ? 'checked' : 'wrote'} ${Object.keys(out).length} files; alpha centre ${mid}, half radius ${q}, rim ${rim}`);
if (stale) process.exit(1);
