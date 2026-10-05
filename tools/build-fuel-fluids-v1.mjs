// Fuel fluids V1 textures (docs/gameplay/fuel_fluids_v1.md): gasoline and diesel, still (16 x 16) and flowing (32 x 32)
// animated sprites, 32 frames each, with LabPBR _s / _n. Pixel art like vanilla's liquids: a smooth tileable field
// (integer-frequency waves, periodic in time) quantised to a few tones; no per-pixel noise. The flowing sprites move one
// pixel down per frame (vanilla flow textures run along v). Gasoline: clear golden yellow with a faint oil sheen (a low
// tint toward lilac / cyan in the light tones); diesel: deeper amber, less transparent.
//   node tools/build-fuel-fluids-v1.mjs           -> writes textures/fluid/{gasoline,diesel}_{still,flow}{,_s,_n}.png + .mcmeta
//   node tools/build-fuel-fluids-v1.mjs --check   -> verifies every output is up to date
//   node tools/build-fuel-fluids-v1.mjs --preview DIR -> writes a contact sheet of the first frames into DIR
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {png} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const FRAMES = 32;
export const FUELS = {
  // tones from dark to light (r, g, b, a); sheen: tints mixed into the two lightest tones; s: LabPBR smoothness, F0
  gasoline: {tones: [[214, 176, 70, 138], [226, 190, 84, 142], [236, 202, 100, 146], [244, 214, 122, 150], [250, 228, 152, 156]],
    sheen: [[214, 198, 226], [198, 226, 220]], sheenMix: 0.14, frametime: 2, smooth: 226, f0: 6, ripple: 0.10},
  diesel:   {tones: [[146, 94, 24, 178], [164, 108, 30, 180], [180, 124, 38, 182], [196, 140, 50, 184], [210, 158, 66, 188]],
    sheen: [[204, 170, 92], [190, 168, 80]], sheenMix: 0.08, frametime: 3, smooth: 212, f0: 6, ripple: 0.07},
};
// waves: [kx, ky, kt, amplitude, phase]; integer kx, ky keep the tile seamless, integer kt keeps the loop seamless
const WAVES = [[1, 1, 1, 1.0, 0.3], [2, -1, -1, 0.7, 1.7], [-1, 2, 2, 0.55, 2.9], [3, 1, -1, 0.35, 4.1], [1, -3, 1, 0.3, 5.3]];
const SHEEN = [[1, 0, 1, 1.0, 0.8], [0, 1, -1, 0.8, 2.2]];
const field = (W, x, y, t, waves) => waves.reduce((s, [kx, ky, kt, a, p]) => s + a * Math.sin(2 * Math.PI * (kx * x / W + ky * y / W + kt * t / FRAMES) + p), 0);
const norm = waves => waves.reduce((s, w) => s + w[3], 0);

function sprite(fuel, W, flow) {
  const F = FUELS[fuel], H = W * FRAMES, n = norm(WAVES), base = Buffer.alloc(W * H * 4), spec = Buffer.alloc(W * H * 4), nrm = Buffer.alloc(W * H * 4);
  for (let f = 0; f < FRAMES; f++) for (let y = 0; y < W; y++) for (let x = 0; x < W; x++) {
    const yy = flow ? y - f * W / FRAMES : y;   // flowing: the pattern runs down one tile per loop
    const v = field(W, x, yy, flow ? 0 : f, WAVES) / n, level = Math.max(0, Math.min(4, Math.floor((v + 1) / 2 * 5)));
    let [r, g, b, a] = F.tones[level];
    if (level >= 3) { const s = field(W, x, yy, f, SHEEN) / norm(SHEEN), tint = F.sheen[s > 0 ? 0 : 1], m = F.sheenMix * Math.abs(s) * (level - 2) / 2;
      r = r + (tint[0] - r) * m; g = g + (tint[1] - g) * m; b = b + (tint[2] - b) * m; }
    const k = ((f * W + y) * W + x) * 4;
    base.set([Math.round(r), Math.round(g), Math.round(b), a], k);
    spec.set([F.smooth, F.f0, 0, 255], k);   // LabPBR: smoothness, F0 (a liquid's ~0.02), no porosity / SSS, no emission (255)
    // ripple normals from the field's gradient (wrapped central differences)
    const dx = (field(W, x + 1, yy, flow ? 0 : f, WAVES) - field(W, x - 1, yy, flow ? 0 : f, WAVES)) / (2 * n);
    const dy = (field(W, x, yy + 1, flow ? 0 : f, WAVES) - field(W, x, yy - 1, flow ? 0 : f, WAVES)) / (2 * n);
    const nx = -dx * F.ripple * W, ny = -dy * F.ripple * W, len = Math.hypot(nx, ny, 1);
    nrm.set([Math.round(128 + 127 * nx / len), Math.round(128 + 127 * ny / len), 255, 255], k);
  }
  return {W, H, base, spec, nrm, first: Buffer.from(base.subarray(0, W * W * 4))};
}

const dir = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/textures/fluid');
// The colour lives in the fluid's tint (FuelFluidType, AflFluids) and the textures hold colour / tint: the tint is the
// per-channel maximum of both sprites, the textures are divided by it, so texture x tint gives the colours above. Vanilla
// water works this way, and so Sundial Lite's "mod water detection" (MOD_WATER_DETECTION: an unlisted translucent block
// with a non-white vertex colour is water, coloured by that colour) can draw the fuels; it treats a white-tinted
// translucent as stained glass, and the fuels did not show in it (2026-10-04, user).
export const TINTS = {};
const outputs = [], sheet = [];
for (const fuel of Object.keys(FUELS)) {
  const sprites = [['still', 16], ['flow', 32]].map(([kind, W]) => [kind, sprite(fuel, W, kind === 'flow')]);
  const tint = [0, 1, 2].map(c => Math.max(...sprites.flatMap(([, sp]) => Array.from({length: sp.W * sp.H}, (_, i) => sp.base[i * 4 + c]))));
  TINTS[fuel] = tint;
  for (const [kind, sp] of sprites) {
    const name = fuel + '_' + kind, scaled = Buffer.from(sp.base);
    for (let i = 0; i < sp.W * sp.H; i++) for (let c = 0; c < 3; c++) scaled[i * 4 + c] = Math.min(255, Math.round(255 * sp.base[i * 4 + c] / tint[c]));
    [png(scaled, sp.W, sp.H), png(sp.spec, sp.W, sp.H), png(sp.nrm, sp.W, sp.H)].forEach((m, i) => outputs.push([path.join(dir, name + ['', '_s', '_n'][i] + '.png'), m]));
    outputs.push([path.join(dir, name + '.png.mcmeta'), JSON.stringify({animation: {frametime: FUELS[fuel].frametime, interpolate: false}}, null, 2) + '\n']);
    sheet.push(sp);
  }
}
const tintHex = fuel => '0xFF' + TINTS[fuel].map(v => v.toString(16).padStart(2, '0').toUpperCase()).join('');
// the liquid blocks (LiquidBlock: the fluid renderer draws them; the model only names the breaking particle)
const assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
for (const fuel of Object.keys(FUELS)) outputs.push(
  [path.join(assets, `blockstates/${fuel}.json`), JSON.stringify({variants: {'': {model: `apocalypse_firstlight:block/${fuel}`}}}, null, 2) + '\n'],
  [path.join(assets, `models/block/${fuel}.json`), JSON.stringify({textures: {particle: `apocalypse_firstlight:fluid/${fuel}_still`}}, null, 2) + '\n']);
const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  const pi = process.argv.indexOf('--preview');
  if (pi > 0) {   // first frames, scaled 8x, over a dark and a light ground
    const S = 8, cell = 32 * S + 16, W = cell * sheet.length, H = cell * 2, img = Buffer.alloc(W * H * 4);
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) img.set(y < cell ? [40, 42, 46, 255] : [200, 200, 196, 255], (y * W + x) * 4);
    sheet.forEach((s, i) => { for (const row of [0, 1]) for (let y = 0; y < s.W * S; y++) for (let x = 0; x < s.W * S; x++) {
      const k = ((Math.floor(y / S)) * s.W + Math.floor(x / S)) * 4, a = s.first[k + 3] / 255, o = ((row * cell + 8 + y) * W + i * cell + 8 + x) * 4;
      for (let c = 0; c < 3; c++) img[o + c] = Math.round(img[o + c] * (1 - a) + s.first[k + c] * a); } });
    fs.mkdirSync(process.argv[pi + 1], {recursive: true});
    fs.writeFileSync(path.join(process.argv[pi + 1], 'fuel_fluids.png'), png(img, W, H));
    console.log('preview written');
  } else if (process.argv.includes('--check')) {
    const fluids = fs.readFileSync(path.join(ROOT, 'src/main/java/com/antaurora/apofirstlight/registry/AflFluids.java'), 'utf8');
    for (const fuel of Object.keys(FUELS)) if (!fluids.includes(tintHex(fuel))) throw new Error('AflFluids lacks the ' + fuel + ' tint ' + tintHex(fuel));
    for (const [file, data] of outputs) { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; if (!cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data))) throw new Error('stale ' + path.relative(ROOT, file)); }
    console.log('CHECK OK');
  } else {
    for (const [file] of outputs) fs.mkdirSync(path.dirname(file), {recursive: true});
    for (const [file, data] of outputs) fs.writeFileSync(file, data);
    console.log('wrote ' + outputs.length + ' files; tints ' + Object.keys(FUELS).map(k => k + ' ' + tintHex(k)).join(', '));
  }
}
