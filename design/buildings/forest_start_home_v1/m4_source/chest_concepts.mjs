// Storage chest concepts (2026-10-15): the user found the V2 board chest (92 x 52 cm, 50 cm high; 192 cm when two join)
// "like a coffin and too small". Three real-world chest types drafted for a look in chest_concepts_preview.html, design
// use only (not game assets; the game chest is tools/build-wood-furniture-v2.mjs). Same frame as the game pieces: one
// cell x 0..1, front at low z (north), the furniture textures (block/furniture/*, walnut). Real sizes, from listings:
//   A  frame-and-panel blanket chest on bracket feet - blanket / hope chests run 109-124 x 46-53 cm, 51-56 cm high; a
//      North Carolina six-board chest of c. 1785 is 119 x 46 x 58 cm. Here 96 x 48 x 59 cm to stay inside one block.
//   B  two-drawer blanket chest ("mule chest") - a late-18th-century one is 127 x 58 x 74 cm; here 96 x 50 x 79 cm.
//   C  iron-bound flat-top trunk / footlocker - WWII-era footlockers are 81 x 42 x 33 cm, steamer trunks larger; here
//      90 x 52 x 58 cm, a deep lid, wood slats and iron corner caps.
// Run: node chest_concepts.mjs -> chest_concepts/<name>.json (the cabin_mesh format the preview reads).
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {Piece, solid, cylinder, serialise} from '../../../../tools/cabin/cabin-geo.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const TEX = 'apocalypse_firstlight:block/furniture/';
const TRIM = 'wood', TRIM_V = 'wood_v', TOP = 'wood_top', INSIDE = 'wood_inside', PANEL = 'wood_panel', IRON = 'iron', CAST = 'cast_iron';

/** Iron drop handles on both ends (plate, two loops, the bar hanging), y their top. */
function endHandles(pc, x0, x1, y, zc) {
  for (const [x, s] of [[x0, -1], [x1, 1]]) {
    const out = d => x + s * d, lo = (a, b) => [Math.min(out(a), out(b)), Math.max(out(a), out(b))];
    const [p0, p1] = lo(0, 0.004), [l0, l1] = lo(0.004, 0.016), [b0, b1] = lo(0.012, 0.024);
    solid(pc, [[IRON, [p0, y - 0.07, zc - 0.07], [p1, y, zc + 0.07]], [IRON, [l0, y - 0.03, zc - 0.055], [l1, y - 0.018, zc - 0.043]],
      [IRON, [l0, y - 0.03, zc + 0.043], [l1, y - 0.018, zc + 0.055]], [IRON, [b0, y - 0.065, zc - 0.055], [b1, y - 0.053, zc + 0.055]],
      [IRON, [b0, y - 0.053, zc - 0.055], [b1, y - 0.03, zc - 0.043]], [IRON, [b0, y - 0.053, zc + 0.043], [b1, y - 0.03, zc + 0.055]]]);
  }
}
/** A keyhole escutcheon on the front at (x, y). */
function escutcheon(pc, x, y, z0) {
  solid(pc, [[IRON, [x - 0.022, y - 0.03, z0 - 0.003], [x + 0.022, y + 0.03, z0]]]);
  solid(pc, [[CAST, [x - 0.004, y - 0.016, z0 - 0.0035], [x + 0.004, y + 0.006, z0 - 0.003]]]);
}
/** Bracket feet at the four corners, h high, each two boards a run long, stepped down at the inner end. */
function bracketFeet(x0, x1, z0, z1, h, run = 0.13) {
  const b = [], t = 0.024, o = 0.012;
  for (const [cx, sx] of [[x0, 1], [x1, -1]]) for (const [cz, sz] of [[z0, 1], [z1, -1]]) {
    const X = (a, c) => [Math.min(cx + sx * a, cx + sx * c), Math.max(cx + sx * a, cx + sx * c)];
    const Z = (a, c) => [Math.min(cz + sz * a, cz + sz * c), Math.max(cz + sz * a, cz + sz * c)];
    const [xa, xb] = X(-o, run), [xs, xt] = X(run, run + 0.05), [za, zb] = Z(-o, -o + t), [ta, tb] = X(-o, -o + t), [zc, zd] = Z(-o, run), [zs, zt] = Z(run, run + 0.05);
    b.push([TRIM, [xa, 0, za], [xb, h, zb]], [TRIM, [xs, h * 0.55, za], [xt, h, zb]], [TRIM, [ta, 0, zc], [tb, h, zd]], [TRIM, [ta, h * 0.55, zs], [tb, h, zt]]);
  }
  return b;
}
/** Hinges at the back: leaves on the back wall and lid, knuckles at y. */
function backHinges(pc, xs, y, z1, lidTop) {
  for (const hx of xs) {
    solid(pc, [[IRON, [hx - 0.02, y - 0.05, z1], [hx + 0.02, y, z1 + 0.004]], [IRON, [hx - 0.02, y, z1 + 0.004], [hx + 0.02, lidTop, z1 + 0.008]]]);
    cylinder(pc, IRON, IRON, 'x', [y, z1 + 0.008], hx - 0.02, hx + 0.02, 0.007, 8, 'ab');
  }
}

// A - frame-and-panel blanket chest on bracket feet
function conceptA() {
  const pc = new Piece('a'), x0 = 0.02, x1 = 0.98, z0 = 0.26, z1 = 0.74, feet = 0.075, base = 0.11, waist = 0.53, rim = 0.555, lid = 0.588;
  solid(pc, [
    ...bracketFeet(x0, x1, z0, z1, feet),
    [{all: TRIM, u: TOP}, [x0 - 0.014, feet, z0 - 0.014], [x1 + 0.014, base, z1 + 0.006]],
    [{all: TRIM, u: TOP}, [x0 - 0.008, waist, z0 - 0.008], [x1 + 0.008, rim, z1 + 0.002]],
    [TRIM, [x0, base, z0], [x1, waist, z1]],
    [PANEL, [x0 + 0.075, 0.165, z0 - 0.008], [0.485, 0.475, z0]], [PANEL, [0.515, 0.165, z0 - 0.008], [x1 - 0.075, 0.475, z0]],
    [PANEL, [x0 - 0.008, 0.165, z0 + 0.07], [x0, 0.475, z1 - 0.07]], [PANEL, [x1, 0.165, z0 + 0.07], [x1 + 0.008, 0.475, z1 - 0.07]],
    [{u: TOP, all: TRIM}, [x0 - 0.025, rim, z0 - 0.025], [x1 + 0.025, lid, z1 + 0.006]],
  ]);
  escutcheon(pc, 0.5, 0.505, z0 - 0.008);
  endHandles(pc, x0 - 0.008, x1 + 0.008, 0.45, 0.5);
  backHinges(pc, [0.22, 0.78], rim, z1 + 0.002, lid);
  return pc;
}

// B - two-drawer blanket chest
function conceptB() {
  const pc = new Piece('b'), x0 = 0.02, x1 = 0.98, z0 = 0.25, z1 = 0.75, feet = 0.08, base = 0.115, mid = 0.335, waist = 0.73, rim = 0.755, lid = 0.788;
  solid(pc, [
    ...bracketFeet(x0, x1, z0, z1, feet),
    [{all: TRIM, u: TOP}, [x0 - 0.014, feet, z0 - 0.014], [x1 + 0.014, base, z1 + 0.006]],
    [{all: TRIM, u: TOP}, [x0 - 0.008, mid - 0.016, z0 - 0.008], [x1 + 0.008, mid, z1 + 0.002]],
    [{all: TRIM, u: TOP}, [x0 - 0.008, waist, z0 - 0.008], [x1 + 0.008, rim, z1 + 0.002]],
    [TRIM, [x0, base, z0], [x1, waist, z1]],
    [TRIM_V, [x0 + 0.035, 0.14, z0 - 0.007], [0.49, 0.295, z0]], [TRIM_V, [0.51, 0.14, z0 - 0.007], [x1 - 0.035, 0.295, z0]],
    [PANEL, [x0 + 0.075, 0.385, z0 - 0.008], [x1 - 0.075, 0.665, z0]],
    [{u: TOP, all: TRIM}, [x0 - 0.025, rim, z0 - 0.025], [x1 + 0.025, lid, z1 + 0.006]],
  ]);
  for (const cx of [0.255, 0.745]) cylinder(pc, TRIM_V, TRIM, 'z', [cx, 0.2175], z0 - 0.035, z0 - 0.007, 0.016, 10, 'a');
  escutcheon(pc, 0.5, 0.7, z0 - 0.008);
  endHandles(pc, x0, x1, 0.64, 0.5);
  backHinges(pc, [0.22, 0.78], rim, z1 + 0.002, lid);
  return pc;
}

// C - iron-bound flat-top trunk: a deep lid, wood slats round body and lid, iron corner caps, two latches and a lock
function conceptC() {
  const pc = new Piece('c'), x0 = 0.05, x1 = 0.95, z0 = 0.24, z1 = 0.76, y0 = 0.02, seam = 0.42, top = 0.58, s = 0.012;
  const band = (ya, yb) => [TRIM, [x0 - s, ya, z0 - s], [x1 + s, yb, z1 + s]];
  solid(pc, [
    [TRIM, [x0 + 0.04, 0, z0 + 0.03], [x1 - 0.04, y0, z0 + 0.08]], [TRIM, [x0 + 0.04, 0, z1 - 0.08], [x1 - 0.04, y0, z1 - 0.03]],
    band(0.06, 0.105), band(0.32, 0.365), band(seam - 0.035, seam), band(seam + 0.004, seam + 0.04), band(0.51, 0.55),
    [{all: PANEL, u: TOP}, [x0, y0, z0], [x1, seam, z1]],
    [{all: PANEL, u: TOP}, [x0, seam + 0.004, z0], [x1, top, z1]],
    [TRIM, [0.26, top, z0 - s], [0.34, top + s, z1 + s]], [TRIM, [0.66, top, z0 - s], [0.74, top + s, z1 + s]],
  ]);
  const caps = [];
  const c = 0.065, e = 0.016;
  for (const [ya, yb] of [[y0 - 0.004, y0 + c], [seam - c, seam], [seam + 0.004, seam + 0.004 + c], [top - c + e, top + e]])
    for (const [xa, xb] of [[x0 - e, x0 + c], [x1 - c, x1 + e]]) for (const [za, zb] of [[z0 - e, z0 + c], [z1 - c, z1 + e]])
      caps.push([IRON, [xa, ya, za], [xb, yb, zb]]);
  for (const cp of caps) solid(pc, [cp]);
  for (const lx of [0.24, 0.76]) solid(pc, [[IRON, [lx - 0.025, seam - 0.06, z0 - s - 0.005], [lx + 0.025, seam + 0.05, z0 - s]]]);
  escutcheon(pc, 0.5, seam - 0.005, z0 - s);
  endHandles(pc, x0 - s, x1 + s, 0.3, 0.5);
  return pc;
}

const out = path.join(HERE, 'chest_concepts');
fs.mkdirSync(out, {recursive: true});
for (const pc of [conceptA(), conceptB(), conceptC()]) fs.writeFileSync(path.join(out, pc.name + '.json'), JSON.stringify(serialise(pc, TEX)) + '\n');
console.log('wrote', fs.readdirSync(out).join(' '));
