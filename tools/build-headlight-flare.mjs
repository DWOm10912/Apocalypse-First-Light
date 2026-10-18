// The T15's lamp glow texture (docs/vehicles/first_pickup_v0_design.md 8.10 / 8.13, client/vehicle/HeadlightFx):
// textures/effect/headlight_flare (256 x 256, drawn with linear filtering: LiquidRenderTypes.HEADLIGHT_GLOW): a soft
// round pool in the colour, (1 - d^2)^2 of the distance from the middle, exactly 0 (black) at the edge (no rim); alpha
// full everywhere, since Oculus's program for that render type cuts fragments under alpha 0.1 (Aureveil A 2026-10-17:
// a falloff in the alpha came out as a hard edge). The beam cones read its middle; the tail lamps' red pools on the
// ground are drawn with the whole of it (2026-10-17: their shape comes from this texture, not from vertex values). The
// flare sprites it was made for (a hot core over a halo, 2026-10-16) were removed 2026-10-17. _s: emissive (LabPBR
// alpha 254) where it shows.
//   node tools/build-headlight-flare.mjs [--check]
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {png} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const A = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const check = process.argv.includes('--check');
const N = 256;

const buf = Buffer.alloc(N * N * 4), s = Buffer.alloc(N * N * 4);
for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
  const d = Math.hypot(x + 0.5 - N / 2, y + 0.5 - N / 2) / (N / 2), o = (y * N + x) * 4;
  const a = d >= 1 ? 0 : (1 - d * d) ** 2;   // smooth to 0 at the rim
  buf[o] = buf[o + 1] = buf[o + 2] = Math.round(255 * a);
  buf[o + 3] = 255;
  s[o + 3] = buf[o] > 4 ? 254 : 255;
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
const mid = buf[((N / 2) * N + N / 2) * 4], q = buf[((N / 2) * N + N / 2 + N / 4) * 4], rim = buf[((N / 2) * N + N - 1) * 4];
console.log(`${check ? 'checked' : 'wrote'} ${Object.keys(out).length} files; value centre ${mid}, half radius ${q}, rim ${rim}`);
if (stale) process.exit(1);
