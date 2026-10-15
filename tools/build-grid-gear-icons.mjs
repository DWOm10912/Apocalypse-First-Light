// Grid Inventory V0 slot icons (docs/ui/afl_inventory_grid_v0_plan.md 6.3, 10): the empty rig / holster slots' 16 px
// outlines (same look as empty_slot_back / _ears). The gear's own icons come from their meshes since 2026-10-14
// (tools/build-equipment-meshes-v1.mjs). Pixel maps below; one character per pixel, '.' transparent. Run: node tools/build-grid-gear-icons.mjs
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {png} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const OUT = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/textures/item');

const ICONS = {
  empty_slot_rig: {
    palette: {o: [139, 139, 139, 255]},
    rows: [
      '................',
      '................',
      '....oo....oo....',
      '...o..o..o..o...',
      '..o....oo....o..',
      '..o..........o..',
      '..o.oo.oo.oo.o..',
      '..o.oo.oo.oo.o..',
      '..o.oo.oo.oo.o..',
      '..o..........o..',
      '..o..oooooo..o..',
      '..o..o....o..o..',
      '..o..oooooo..o..',
      '..oooooooooooo..',
      '................',
      '................',
    ],
  },
  // weapon positions (plan 10): the empty holster slot's outline, a rolled sling with its buckle, a leather holster
  empty_slot_holster: {
    palette: {o: [139, 139, 139, 255]},
    rows: [
      '................',
      '................',
      '.....oooooo.....',
      '.....o....o.....',
      '.....o....ooo...',
      '.....o......o...',
      '.....o....ooo...',
      '......o...o.....',
      '......o...o.....',
      '......o...o.....',
      '......o...o.....',
      '......o...o.....',
      '.......o..o.....',
      '.......oooo.....',
      '................',
      '................',
    ],
  },
};

for (const [name, icon] of Object.entries(ICONS)) {
  if (icon.rows.length !== 16) throw new Error(`${name}: ${icon.rows.length} rows`);
  const rgba = Buffer.alloc(16 * 16 * 4);
  icon.rows.forEach((row, y) => {
    if (row.length !== 16) throw new Error(`${name}: row ${y} is ${row.length} px`);
    [...row].forEach((ch, x) => {
      if (ch === '.') return;
      const c = icon.palette[ch];
      if (!c) throw new Error(`${name}: unknown pixel '${ch}' at ${x},${y}`);
      rgba.set(c, (y * 16 + x) * 4);
    });
  });
  fs.writeFileSync(path.join(OUT, `${name}.png`), png(rgba, 16, 16));
  console.log('wrote', `${name}.png`);
}
