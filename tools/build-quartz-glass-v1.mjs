// Quartz Glass V1 (docs/gameplay/material_system_v1.md): a full glass block of fused quartz (2026-10-05). Very clear, its
// 1 px edge a little frosted (a ground edge), no streaks or marks; translucent, LabPBR smooth dielectric (as the AFL glass:
// smoothness 235-240, F0 10). Writes the 16x16 texture set, the blockstate, the block model (cube_all, translucent) and
// the item model.
//   node tools/build-quartz-glass-v1.mjs            -> writes the outputs
//   node tools/build-quartz-glass-v1.mjs --check    -> verifies they are up to date
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {png} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const ASSETS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const N = 16;
export const GLASS = {clear: {c: [204, 224, 230], a: 34, sm: 240}, edge: {c: [222, 236, 240], a: 120, sm: 200}, f0: 10};

const base = Buffer.alloc(N * N * 4), spec = Buffer.alloc(N * N * 4), normal = Buffer.alloc(N * N * 4);
for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
  const m = x === 0 || y === 0 || x === N - 1 || y === N - 1 ? GLASS.edge : GLASS.clear, k = (y * N + x) * 4;
  base.set([...m.c, m.a], k);
  spec.set([m.sm, GLASS.f0, 0, 255], k);
  normal.set([128, 128, 255, 255], k);
}
const json = v => JSON.stringify(v, null, 2) + '\n';
const outputs = [
  [path.join(ASSETS, 'textures/block/quartz_glass.png'), png(base, N, N)],
  [path.join(ASSETS, 'textures/block/quartz_glass_s.png'), png(spec, N, N)],
  [path.join(ASSETS, 'textures/block/quartz_glass_n.png'), png(normal, N, N)],
  [path.join(ASSETS, 'blockstates/quartz_glass.json'), json({variants: {'': {model: 'apocalypse_firstlight:block/quartz_glass'}}})],
  [path.join(ASSETS, 'models/block/quartz_glass.json'), json({parent: 'minecraft:block/cube_all', render_type: 'minecraft:translucent',
    textures: {all: 'apocalypse_firstlight:block/quartz_glass'}})],
  [path.join(ASSETS, 'models/item/quartz_glass.json'), json({parent: 'apocalypse_firstlight:block/quartz_glass'})],
];
if (process.argv.includes('--check')) {
  for (const [file, data] of outputs) { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; if (!cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data))) throw new Error('stale ' + path.relative(ROOT, file)); }
  console.log('CHECK OK');
} else {
  for (const [file, data] of outputs) { fs.mkdirSync(path.dirname(file), {recursive: true}); fs.writeFileSync(file, data); }
  console.log('wrote ' + outputs.length + ' files');
}
