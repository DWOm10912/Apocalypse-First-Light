// BR-51 HUD weapon-list silhouette (textures/gui/gun/br51_01_hud.png, client/inventory/WeaponSilhouettes): the V3 game
// low poly in its factory state (the standard 20-round magazine, no attachments) seen orthographically from its left
// side, muzzle to the left, in the grey of the other gun HUD silhouettes: flat grey lit from the top left, a darker rim
// on the outline, 2 x 2 supersampled (as tools/build-equipment-meshes-v1.mjs drawHud). Replaces the V1 picture
// (user 2026-10-22; drawn offline because the Blockbench link was down, the same model and the same view).
//   node tools/render-br51-01-hud.mjs [--check]
// Prints the picture's aspect: native_guns/br51_01.json presentation.hud keeps width 60 and takes height = 60 / aspect.
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {png, norm, newell, dot} from './cube-slab-mesh-lib.mjs';
import {lowParts} from './build-br51-01-v3-native.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const OUT = path.join(root, 'src/main/resources/assets/apocalypse_firstlight/textures/gui/gun/br51_01_hud.png');
const LONG = 1184, PAD = 2;
const check = process.argv.includes('--check');

// model px -> view: seen from -X (the left side), x right = +Z (muzzle -Z to the left), y up, z toward the eye = -X
const view = v => [v[2], v[1], -v[0]];
// light from the top left, toward the viewer
const LIGHT = norm([-0.55 / Math.SQRT2, 0.55 / Math.SQRT2, 1]);

const tris = [];
for (const p of lowParts().parts) for (const f of p.faces) {
  const P = f.v.map(i => view(p.vertices[i]));
  let n = norm(newell(P));
  if (n[2] < 0) n = n.map(x => -x);
  const lum = 150 + 52 * Math.max(0, dot(n, LIGHT));
  for (let k = 1; k + 1 < P.length; k++) tris.push({P: [P[0], P[k], P[k + 1]], lum});
}
const lo = [Infinity, Infinity], hi = [-Infinity, -Infinity];
for (const t of tris) for (const q of t.P) for (let k = 0; k < 2; k++) { lo[k] = Math.min(lo[k], q[k]); hi[k] = Math.max(hi[k], q[k]); }
const scale = (LONG - 2 * PAD) / (hi[0] - lo[0]);
const W = LONG, H = Math.ceil((hi[1] - lo[1]) * scale) + 2 * PAD, S = scale * 2, W2 = W * 2, H2 = H * 2;
const depth = new Float64Array(W2 * H2).fill(-Infinity), shade = new Float64Array(W2 * H2);
for (const {P, lum} of tris) {
  const Q = P.map(q => [(q[0] - lo[0]) * S + PAD * 2, (hi[1] - q[1]) * S + PAD * 2, q[2]]);
  const area = (Q[1][0] - Q[0][0]) * (Q[2][1] - Q[0][1]) - (Q[2][0] - Q[0][0]) * (Q[1][1] - Q[0][1]);
  if (Math.abs(area) < 1e-9) continue;
  const x0 = Math.max(0, Math.floor(Math.min(...Q.map(q => q[0])))), x1 = Math.min(W2 - 1, Math.ceil(Math.max(...Q.map(q => q[0]))));
  const y0 = Math.max(0, Math.floor(Math.min(...Q.map(q => q[1])))), y1 = Math.min(H2 - 1, Math.ceil(Math.max(...Q.map(q => q[1]))));
  for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
    const px = x + 0.5, py = y + 0.5;
    const w0 = ((Q[1][0] - px) * (Q[2][1] - py) - (Q[2][0] - px) * (Q[1][1] - py)) / area;
    const w1 = ((Q[2][0] - px) * (Q[0][1] - py) - (Q[0][0] - px) * (Q[2][1] - py)) / area, w2 = 1 - w0 - w1;
    if (w0 < -1e-6 || w1 < -1e-6 || w2 < -1e-6) continue;
    const z = w0 * Q[0][2] + w1 * Q[1][2] + w2 * Q[2][2], i = y * W2 + x;
    if (z > depth[i]) { depth[i] = z; shade[i] = lum; }
  }
}
const filled = i => depth[i] > -Infinity, rgba = Buffer.alloc(W * H * 4);
for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
  let a = 0, g = 0;
  for (const [dx, dy] of [[0, 0], [1, 0], [0, 1], [1, 1]]) {
    const X = x * 2 + dx, Y = y * 2 + dy, i = Y * W2 + X;
    if (!filled(i)) continue;
    // outline: a sample within 2 supersamples of empty space reads darker
    let rim = false;
    for (let oy = -2; oy <= 2 && !rim; oy++) for (let ox = -2; ox <= 2; ox++) {
      const XX = X + ox, YY = Y + oy;
      if (XX < 0 || YY < 0 || XX >= W2 || YY >= H2 || !filled(YY * W2 + XX)) { rim = true; break; }
    }
    a++; g += rim ? shade[i] * 0.72 : shade[i];
  }
  if (!a) continue;
  const v = Math.round(g / a), o = (y * W + x) * 4;
  rgba[o] = rgba[o + 1] = rgba[o + 2] = v; rgba[o + 3] = Math.round(255 * a / 4);
}
const buf = png(rgba, W, H);
console.log(`br51_01_hud ${W} x ${H}, aspect ${(W / H).toFixed(3)} -> hud height ${(60 * H / W).toFixed(2)} at width 60`);
if (check) {
  const same = fs.existsSync(OUT) && fs.readFileSync(OUT).equals(buf);
  console.log(same ? 'up to date' : 'stale: ' + path.relative(root, OUT));
  process.exit(same ? 0 : 1);
}
fs.writeFileSync(OUT, buf);
console.log('wrote', path.relative(root, OUT));
