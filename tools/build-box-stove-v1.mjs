// Box Stove V1 (docs/models/box_stove_v1.md, 2026-10-13): the cast-iron box stove, the stove heat shield and the flagstone
// of the forest start home B ("溪谷老宅", docs/worldgen/forest_start_home_b_v1_plan.md, M2 F1). User 2026-10-13: stove A of
// the concepts (design/buildings/forest_start_home_v1/m2_sheets/m2_2_stove_A.png: the old long box stove, no oven), the heat
// shield as a block of its own, the hearth in flat stone slabs.
//   box_stove          body 70 x 32 x 36.5 cm on four splayed 15 cm legs (top plate at 56 cm), raised cast frames and
//                      corner beads, one cook lid, the loading door in the end (hinged on the wall side, it swings to the
//                      wall so the room sees the fire), a spin draft in it, a coil-spring handle, an ash apron under it;
//                      the flue collar at the cell's middle and a pipe stub with its damper up to the cell top, where the
//                      stove pipe (cabin) takes over. Firebrick lining, an ash bed and up to three splits inside.
//   stove_heat_shield  a galvanised sheet 10 cm out from the wall on ceramic spools (3 cm off the log faces), open at the
//                      bottom and the top so air rises behind it; joins the shields (and the stove / pipe it stands
//                      behind) beside, above and below it into one sheet: 16 pieces by the joined sides
//   flagstone          a full block: coursed random rectangular bluestone on top (a 3 x 3 macro texture), the fieldstone's
//                      rubble on its sides (MacroTileModel, its separate top group)
// Every piece is drawn facing north (the front at z 0, the wall at z 1); the stove's door end is x 0 (in the house, facing
// south, that is the east end toward the chairs).
// The loading door swings as an AFL Animated Block Mesh (box_stove_door, tools/furniture/furniture-mesh.mjs, 2026-10-15:
// the cabin pieces swapped it closed / open in one frame): only the door, drawn by the block entity renderer; the body,
// the splits and the shield sheet behind it stay cabin pieces drawn by the chunk. stove/door_closed is the item's door.
// Writes textures (block/stove/*.png + _s / _n), mesh pieces (cabin_mesh/stove/, cabin_mesh/shield/), the door's mesh
// (geo, sidecar, profile, atlas block/stove/box_stove_door, src/main/blockbench/stove/), block / item models,
// blockstates, loot tables. Tags, item mass, the temperature source and lang are edited by hand; --check audits them.
//   node tools/build-box-stove-v1.mjs            -> writes every output
//   node tools/build-box-stove-v1.mjs --check    -> verifies every output is up to date and the hand-edited data
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {renderX} from './cabin/cabin-art.mjs';
import {STOVE_TEXTURES, flagstoneTopTiles, FL_COLS, FL_ROWS} from './stove/box-stove-art.mjs';
import {Piece, solid, cylinder, serialise, newell, dot, sub, add, scl} from './cabin/cabin-geo.mjs';
import {furnitureMesh} from './furniture/furniture-mesh.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const ASSETS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const DATA = path.join(ROOT, 'src/main/resources/data');
const NS = 'apocalypse_firstlight:', TEX = NS + 'block/';
const json = v => JSON.stringify(v, null, 2) + '\n';

/** The blocks: id, item mass (kg; carry scale), carry tag, mining (AGENTS.md audit), names. */
export const BLOCKS = [
  {id: 'box_stove', mass: 60, carry: 'oversized', tier: 'iron', noise: 'metal', en: 'Cast Iron Box Stove', zh: '铸铁箱式炉'},
  {id: 'stove_heat_shield', mass: 3, carry: 'bulky', tier: 'stone', noise: 'metal', en: 'Stove Heat Shield', zh: '隔热钢板'},
  {id: 'flagstone', mass: 0.25, carry: null, tier: null, noise: 'stone', en: 'Flagstone', zh: '石板'},
];

const IRON = 'stove/cast_iron', EDGE = 'stove/cast_iron_edge', SOOT = 'stove/soot', NICKEL = 'stove/nickel', BRICK = 'stove/firebrick';
const ASH = 'stove/ash', BARK = 'stove/firewood_bark', END = 'stove/firewood_end', SHEET = 'stove/heat_shield', CERAMIC = 'stove/ceramic';
const PIPE = 'cabin/stove_pipe';

/** A polygon whose corners are reordered so its Newell normal points along `out`. */
function polyOut(pc, t, pts, out, opts = {}) {
  if (dot(newell(pts), out) < 0) pts = [...pts].reverse();
  return pc.poly(t, pts, opts);
}
/**
 * A box with its edges chamfered r (flat 45-degree strips, flat corner triangles): the bevels catch the light the way
 * the cast edges do (memory gun-material-tone: dark iron reads by its edge highlights). Closed, outward.
 */
function cbox(pc, t, lo, hi, r) {
  const c = lo.map((v, i) => (v + hi[i]) / 2), e = lo.map((v, i) => (hi[i] - v) / 2);
  r = Math.min(r, ...e.map(v => v * 0.45));
  const P = (s, m) => [0, 1, 2].map(a => c[a] + s[a] * (m.includes(a) ? e[a] : e[a] - r));
  const sg = [-1, 1];
  for (let a = 0; a < 3; a++) for (const sg1 of sg) {   // the six faces
    const [b, d] = [0, 1, 2].filter(k => k !== a), pts = [];
    for (const [i, j] of [[-1, -1], [1, -1], [1, 1], [-1, 1]]) { const s = [0, 0, 0]; s[a] = sg1; s[b] = i; s[d] = j; pts.push(P(s, [a])); }
    const out = [0, 0, 0]; out[a] = sg1;
    polyOut(pc, t, pts, out);
  }
  if (r <= 0) return pc;
  for (let a = 0; a < 3; a++) for (let b = a + 1; b < 3; b++) for (const sa of sg) for (const sb of sg) {   // twelve edge strips
    const k = 3 - a - b, s = (q) => { const v = [0, 0, 0]; v[a] = sa; v[b] = sb; v[k] = q; return v; };
    const out = [0, 0, 0]; out[a] = sa; out[b] = sb;
    polyOut(pc, t, [P(s(-1), [a]), P(s(1), [a]), P(s(1), [b]), P(s(-1), [b])], out);
  }
  for (const sx of sg) for (const sy of sg) for (const sz of sg) {   // eight corners
    const s = [sx, sy, sz];
    polyOut(pc, t, [P(s, [0]), P(s, [1]), P(s, [2])], s);
  }
  return pc;
}
/** A flat disc of radius r round c facing `out` (an axis 'x' | 'y' | 'z' and its sign), UVs on the sprite's middle disc. */
function disc(pc, t, axis, sign, c, r, seg) {
  const pts = [], uv = [];
  for (let i = 0; i < seg; i++) {
    const a = i / seg * Math.PI * 2, u = Math.cos(a) * r, w = Math.sin(a) * r;
    pts.push(axis === 'x' ? [c[0], c[1] + u, c[2] + w] : axis === 'y' ? [c[0] + w, c[1], c[2] + u] : [c[0] + u, c[1] + w, c[2]]);
    uv.push([0.5 + 0.47 * Math.cos(a), 0.5 - 0.47 * Math.sin(a)]);
  }
  const out = axis === 'x' ? [sign, 0, 0] : axis === 'y' ? [0, sign, 0] : [0, 0, sign];
  if (dot(newell(pts), out) < 0) { pts.reverse(); uv.reverse(); }
  return pc.poly(t, pts, {uv});
}
/** A coil-spring handle (stays cool to the touch): nickel turns, each a short closed ring, over a core; along axis from a to b. */
function coil(pc, axis, c, a, b) {
  cylinder(pc, NICKEL, NICKEL, axis, c, a, b, 0.0068, 8, 'ab');
  const n = Math.round((b - a) / 0.006);
  for (let i = 0; i < n; i++) { const t0 = a + (b - a) * (i + 0.15) / n, t1 = a + (b - a) * (i + 0.85) / n; cylinder(pc, NICKEL, NICKEL, axis, c, t0, t1, 0.0092, 8, 'ab'); }
  return pc;
}
/** A raised cast frame (4 strips) on a face: plane 'z' (x, y rectangle) or 'x' (z, y), out = the face's position and sign. */
function castFrame(pc, plane, at, sign, u0, u1, v0, v1, w = 0.016, t = 0.005) {
  const f0 = sign < 0 ? at - t : at, f1 = sign < 0 ? at : at + t;
  const strip = (a0, a1, b0, b1) => plane === 'z' ? [EDGE, [a0, b0, f0], [a1, b1, f1]] : [EDGE, [f0, b0, a0], [f1, b1, a1]];
  return solid(pc, [strip(u0, u1, v0, v0 + w), strip(u0, u1, v1 - w, v1), strip(u0, u0 + w, v0 + w, v1 - w), strip(u1 - w, u1, v0 + w, v1 - w)]);
}
/** A cast leg h high under (x, z): tapered, splayed out toward (sx, sz) along a slight curve, on a pad foot. */
function leg(pc, x, z, h, sx, sz) {
  const ring = (y, s, k) => [[-1, -1], [1, -1], [1, 1], [-1, 1]].map(([i, j]) => [x + i * 0.022 * s + sx * k, y, z + j * 0.022 * s + sz * k]);
  const R = [ring(h, 1, 0), ring(h / 2, 0.85, 0.004), ring(0.012, 0.72, 0.0145)];
  for (let r = 0; r < 2; r++) for (let i = 0; i < 4; i++) {
    const j = (i + 1) % 4, q = [R[r][i], R[r][j], R[r + 1][j], R[r + 1][i]], mid = scl(add(add(q[0], q[1]), add(q[2], q[3])), 0.25);
    polyOut(pc, IRON, q, sub(mid, [x + sx * 0.006 * r, mid[1], z + sz * 0.006 * r]));
  }
  const fx = x + sx * 0.016, fz = z + sz * 0.016;
  return cbox(pc, IRON, [fx - 0.024, 0, fz - 0.024], [fx + 0.024, 0.012, fz + 0.024], 0.003);
}

// =============================================================================================== the stove
// Body x 0..0.70, z 0.30..0.62 (the back 31 cm from the logs, which bulge to z 0.93), y 0.165..0.53 between a bottom
// plate (0.14) and a top plate (to 0.56, 1.2 cm over); 1.2 cm walls. The door end (x 0) has the loading opening
// y 0.255..0.435 x z 0.355..0.565; the door 21 x 24 cm hangs on the wall side (hinge line x 0, z 0.58).
export const BODY = {x0: 0, x1: 0.70, z0: 0.30, z1: 0.62, y0: 0.165, y1: 0.53, top: 0.56};
const T = 0.012, OY0 = 0.255, OY1 = 0.435, OZ0 = 0.355, OZ1 = 0.565, HZ = 0.58;
export const LID = [0.20, 0.46], FLUE = [0.5, 0.5], PIPE_R = 0.0755;
function body() {
  const {x0: X0, x1: X1, z0: Z0, z1: Z1, y0: Y0, y1: Y1} = BODY, pc = new Piece('stove/body');
  solid(pc, [
    [BRICK, [0.02, Y0, Z0 + T], [0.68, 0.40, Z0 + T + 0.022]], [BRICK, [0.02, Y0, Z1 - T - 0.022], [0.68, 0.40, Z1 - T]],
    [BRICK, [0.666, Y0, Z0 + T], [X1 - T, 0.40, Z1 - T]],
    [ASH, [X0 + T, Y0, Z0 + T + 0.022], [0.666, 0.19, Z1 - T - 0.022]],
    [IRON, [X0, Y0, Z0], [X1, Y1, Z0 + T]], [IRON, [X0, Y0, Z1 - T], [X1, Y1, Z1]], [IRON, [X1 - T, Y0, Z0 + T], [X1, Y1, Z1 - T]],
    [IRON, [X0, Y0, Z0 + T], [X0 + T, OY0, Z1 - T]], [IRON, [X0, OY1, Z0 + T], [X0 + T, Y1, Z1 - T]],
    [IRON, [X0, OY0, Z0 + T], [X0 + T, OY1, OZ0]], [IRON, [X0, OY0, OZ1], [X0 + T, OY1, Z1 - T]],
  ]);
  cbox(pc, IRON, [X0 - 0.012, Y1, Z0 - 0.012], [X1 + 0.012, BODY.top, Z1 + 0.012], 0.006);   // top plate
  cbox(pc, IRON, [X0 - 0.006, 0.14, Z0 - 0.006], [X1 + 0.006, Y0, Z1 + 0.006], 0.004);      // bottom plate
  castFrame(pc, 'z', Z0, -1, X0 + 0.025, X1 - 0.025, Y0 + 0.025, Y1 - 0.025);                 // the room side
  castFrame(pc, 'x', X1, 1, Z0 + 0.025, Z1 - 0.025, Y0 + 0.025, Y1 - 0.025);                  // the far end
  for (const [x, z] of [[X0, Z0], [X1, Z0], [X0, Z1], [X1, Z1]]) cylinder(pc, EDGE, EDGE, 'y', [z, x], Y0, Y1, 0.009, 8);   // corner beads
  for (const x of [0.016, 0.684]) for (const y of [0.18, 0.515]) cylinder(pc, EDGE, EDGE, 'z', [x, y], Z0 - 0.0035, Z0, 0.0055, 8, 'a');   // rivets
  for (const [x, z, sx, sz] of [[0.65, 0.335, 1, -1], [0.05, 0.335, -1, -1], [0.65, 0.585, 1, 1], [0.05, 0.585, -1, 1]]) leg(pc, x, z, 0.15, sx, sz);
  cbox(pc, IRON, [-0.08, 0.142, 0.33], [X0, 0.16, 0.59], 0.003);      // ash apron under the door
  cbox(pc, EDGE, [-0.082, 0.16, 0.33], [-0.07, 0.172, 0.59], 0.003);  // and its lip
  // the cook lid (its gap a dark ring, the lifter's notch), the flue collar on its flange, the pipe stub and its damper
  const [lx, lz] = LID, [fx, fz] = FLUE;
  cylinder(pc, SOOT, SOOT, 'y', [lz, lx], BODY.top, BODY.top + 0.0015, 0.0905, 24, 'b');
  cylinder(pc, EDGE, EDGE, 'y', [lz, lx], BODY.top, BODY.top + 0.007, 0.087, 24, 'b');
  cbox(pc, SOOT, [lx - 0.088, BODY.top + 0.007, lz - 0.005], [lx - 0.075, BODY.top + 0.0078, lz + 0.005], 0);
  cylinder(pc, EDGE, EDGE, 'y', [fz, fx], BODY.top, BODY.top + 0.008, 0.10, 24, 'b');
  cylinder(pc, IRON, IRON, 'y', [fz, fx], BODY.top + 0.008, 0.62, 0.08, 24, 'b');
  cylinder(pc, PIPE, SOOT, 'y', [fz, fx], 0.62, 1, PIPE_R, 16, 'b');
  cylinder(pc, IRON, IRON, 'x', [0.86, fz], fx - 0.1, fx + 0.1, 0.004, 6, 'b');
  coil(pc, 'x', [0.86, fz], fx - 0.1225, fx - 0.0775);
  return pc;
}
/** The loading door, closed: the plate, its raised frame, the spin draft (six holes, a knob), the latch and its coil handle, two hinge knuckles. */
export const DRAFT = {x: -0.0235, y: 0.345, z: 0.46, ring: 0.026, hole: 0.0065};
function door() {
  const pc = new Piece('stove/door_closed'), y0 = 0.24, y1 = 0.45, z0 = 0.34, z1 = HZ;
  cbox(pc, IRON, [-0.015, y0, z0], [0, y1, z1], 0.003);
  solid(pc, [[EDGE, [-0.019, y0, z0], [-0.015, y1, z0 + 0.014]], [EDGE, [-0.019, y0, z1 - 0.014], [-0.015, y1, z1]],
    [EDGE, [-0.019, y0, z0 + 0.014], [-0.015, y0 + 0.014, z1 - 0.014]], [EDGE, [-0.019, y1 - 0.014, z0 + 0.014], [-0.015, y1, z1 - 0.014]]]);
  const {y: dy, z: dz} = DRAFT;
  cylinder(pc, EDGE, EDGE, 'x', [dy, dz], -0.023, -0.015, 0.042, 24, 'a');
  for (let i = 0; i < 6; i++) { const a = i / 6 * Math.PI * 2; disc(pc, SOOT, 'x', -1, [DRAFT.x, dy + Math.sin(a) * DRAFT.ring, dz + Math.cos(a) * DRAFT.ring], DRAFT.hole, 10); }
  cylinder(pc, EDGE, EDGE, 'x', [dy, dz], -0.035, -0.023, 0.011, 12, 'a');
  cbox(pc, IRON, [-0.042, dy - 0.012, 0.354], [-0.015, dy + 0.012, 0.366], 0.002);   // latch arm at the free edge
  coil(pc, 'y', [0.36, -0.046], dy - 0.03, dy + 0.03);
  for (const [a, b] of [[0.26, 0.29], [0.40, 0.43]]) cylinder(pc, EDGE, EDGE, 'y', [HZ + 0.002, -0.008], a, b, 0.008, 10, 'ab');
  return pc;
}
/** The splits on the ash bed: one, two or three (the load: the fuel left in logs' worth). */
const SPLITS = [[0.045, 0.10, 0.54, 0.237, 0.43], [0.038, 0.17, 0.55, 0.229, 0.52], [0.035, 0.15, 0.51, 0.300, 0.474]];
export const FLAMES = [[0.30, 0.22, 0.47, 0.24, 0.17], [0.42, 0.21, 0.45, 0.22, 0.15], [0.20, 0.21, 0.47, 0.18, 0.13]];   // x, base y, z, width, height
function loads() {
  return [1, 2, 3].map(n => {
    const pc = new Piece('stove/load_' + n);
    for (const [r, a, b, y, z] of SPLITS.slice(0, n)) cylinder(pc, BARK, END, 'x', [y, z], a, b, r, 10, 'ab');
    return pc;
  });
}
function stovePieces() {
  return [body(), door(), ...loads()];
}
/**
 * The loading door as an animated mesh: one bone hinged on the vertical line x 0, z HZ (the wall side); 'open' swings it
 * a quarter out of the door end toward the wall, (dx, dz) -> (dz, -dx), where the old open piece stood. 10 ticks.
 */
export function doorMesh() {
  return furnitureMesh(ROOT, {id: 'box_stove_door', dir: 'stove', texPrefix: TEX, particle: TEX + IRON, part: true,
    bones: [{name: 'door', pivot: [0, 0, HZ], pieces: [door()]}],
    animations: {open: {duration_ticks: 10, easing: 'ease_in_out', transforms: {door: {rotation: [0, 90, 0]}}}}});
}

// =============================================================================================== the heat shield
// The sheet z 0.896..0.900 (10 cm out from the cell's wall face, 3 cm off the log faces at 0.93), ceramic spools behind
// it to z 1 at x 0.25 / 0.75, y 0.5, a screw head on each. Joined sides (mask: 1 x-, 2 x+, 4 up, 8 down) run to the
// cell edge; open ones stop 3 cm short with a rolled hem (open at the bottom and top: air in under it, out over it).
export const SHEET_Z = [0.896, 0.9];
function shield(mask) {
  const pc = new Piece('shield/panel_' + mask), j = b => (mask & b) !== 0, [z0, z1] = SHEET_Z, zm = (z0 + z1) / 2, hem = 0.0042;
  const x0 = j(1) ? 0 : 0.03, x1 = j(2) ? 1 : 0.97, y0 = j(8) ? 0 : 0.03, y1 = j(4) ? 1 : 0.97;
  solid(pc, [[SHEET, [x0, y0, z0], [x1, y1, z1]]]);
  if (!j(1)) cylinder(pc, SHEET, SHEET, 'y', [zm, x0], y0, y1, hem, 8, 'ab');
  if (!j(2)) cylinder(pc, SHEET, SHEET, 'y', [zm, x1], y0, y1, hem, 8, 'ab');
  if (!j(8)) cylinder(pc, SHEET, SHEET, 'x', [y0, zm], x0, x1, hem, 8, 'ab');
  if (!j(4)) cylinder(pc, SHEET, SHEET, 'x', [y1, zm], x0, x1, hem, 8, 'ab');
  for (const x of [0.25, 0.75]) {
    cylinder(pc, CERAMIC, CERAMIC, 'z', [x, 0.5], z1, 1, 0.014, 12);
    cylinder(pc, EDGE, EDGE, 'z', [x, 0.5], z0 - 0.003, z0, 0.0065, 10, 'a');
  }
  return pc;
}

export function pieces() {
  return [...stovePieces(), ...Array.from({length: 16}, (_, m) => shield(m))];
}

// =============================================================================================== models, states, loot
const ITEMS = {box_stove: [['stove/body', 0], ['stove/door_closed', 0]], stove_heat_shield: [['shield/panel_0', 0]]};
const PARTICLE = {box_stove: IRON, stove_heat_shield: SHEET};
const loot = id => json({type: 'minecraft:block', pools: [{rolls: 1, bonus_rolls: 0, entries: [{type: 'minecraft:item', name: NS + id}],
  conditions: [{condition: 'minecraft:survives_explosion'}]}], random_sequence: `${NS}blocks/${id}`});

export function outputs() {
  const out = [];
  const texOut = (name, maps) => maps.forEach((m, i) => out.push([path.join(ASSETS, 'textures/block/stove', name + ['', '_s', '_n'][i] + '.png'), m]));
  for (const [name, [make, height]] of Object.entries(STOVE_TEXTURES)) texOut(name, renderX(make(), {height}));
  flagstoneTopTiles().forEach((maps, t) => texOut('flagstone_top_' + t, maps));
  for (const pc of pieces()) out.push([path.join(ASSETS, 'cabin_mesh', pc.name + '.json'), JSON.stringify(serialise(pc, TEX)) + '\n']);
  out.push(...doorMesh().outputs);
  const bm = id => `${NS}block/stove/${id}`;
  for (const {id} of BLOCKS) {
    if (id === 'flagstone') {
      const side = Object.fromEntries(Array.from({length: 6}, (_, t) => ['tile' + t, TEX + 'cabin/fieldstone_' + t]));
      const top = Object.fromEntries(Array.from({length: FL_COLS * FL_ROWS}, (_, t) => ['top' + t, TEX + 'stove/flagstone_top_' + t]));
      out.push([path.join(ASSETS, 'models/block/stove', id + '.json'), json({parent: 'minecraft:block/block', loader: NS + 'macro_tile', columns: 3, rows: 2,
        top_columns: FL_COLS, top_rows: FL_ROWS, textures: {particle: TEX + 'stove/flagstone_top_0', ...side, ...top}})]);
    } else out.push([path.join(ASSETS, 'models/block/stove', id + '.json'), json({parent: 'minecraft:block/block', loader: NS + 'cabin', textures: {particle: TEX + PARTICLE[id]}, item: ITEMS[id]})]);
    out.push([path.join(ASSETS, 'blockstates', id + '.json'), json({variants: {'': {model: bm(id)}}})]);
    out.push([path.join(ASSETS, 'models/item', id + '.json'), json({parent: bm(id)})]);
    out.push([path.join(DATA, 'apocalypse_firstlight/loot_tables/blocks', id + '.json'), loot(id)]);
  }
  return out;
}

// =============================================================================================== audit
const readJson = p => JSON.parse(fs.readFileSync(path.join(ROOT, p), 'utf8'));
function audit() {
  const errs = [], tags = 'src/main/resources/data/minecraft/tags/blocks/', afl = 'src/main/resources/data/apocalypse_firstlight/';
  const tagHas = (file, id) => { try { return readJson(file).values.some(v => (typeof v === 'string' ? v : v.id) === (id.includes(':') ? id : NS + id)); } catch { return false; } };
  for (const b of BLOCKS) {
    if (!tagHas(tags + 'mineable/pickaxe.json', b.id)) errs.push(`${b.id}: missing from minecraft:mineable/pickaxe`);
    if (tagHas(tags + 'mineable/axe.json', b.id)) errs.push(`${b.id}: in minecraft:mineable/axe`);
    for (const t of ['stone', 'iron', 'diamond']) if (tagHas(tags + `needs_${t}_tool.json`, b.id) !== (b.tier === t)) errs.push(`${b.id}: needs_${t}_tool ${b.tier === t ? 'missing' : 'unexpected'}`);
    if (!tagHas(`${afl}tags/blocks/noise_${b.noise}_blocks.json`, b.id)) errs.push(`${b.id}: missing from noise_${b.noise}_blocks`);
  }
  const mass = readJson(afl + 'item_mass/afl_content_v1.json').items ?? {};
  for (const b of BLOCKS) if (mass[NS + b.id]?.unit_mass_kg !== b.mass) errs.push(`${b.id}: item mass ${mass[NS + b.id]?.unit_mass_kg} (want ${b.mass})`);
  for (const b of BLOCKS) for (const c of ['bulky', 'oversized'])
    if (tagHas(`${afl}tags/items/carry/${c}.json`, b.id) !== (b.carry === c)) errs.push(`${b.id}: carry/${c} ${b.carry === c ? 'missing' : 'unexpected'}`);
  for (const lang of ['en_us', 'zh_cn']) {
    const l = readJson(`src/main/resources/assets/apocalypse_firstlight/lang/${lang}.json`);
    for (const b of BLOCKS) { const v = l['block.apocalypse_firstlight.' + b.id]; if (v !== (lang === 'en_us' ? b.en : b.zh)) errs.push(`${b.id}: lang ${lang} = ${v}`); }
  }
  for (const f of ['logs', 'wood', 'kindling']) if (!fs.existsSync(path.join(ROOT, `${afl}tags/items/stove_fuel/${f}.json`))) errs.push(`stove_fuel/${f} tag missing`);
  const src = readJson(afl + 'temperature/temperature_v1.json').sources.find(s => s.block === NS + 'box_stove');
  if (!src || src.lit !== true) errs.push('box_stove: no lit heat source in temperature_v1.json');
  return errs;
}

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  const out = outputs();
  if (process.argv.includes('--check')) {
    const stale = out.filter(([file, data]) => { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; return !cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data)); });
    for (const [file] of stale) console.log('stale ' + path.relative(ROOT, file));
    const errs = audit();
    for (const e of errs) console.log('audit: ' + e);
    if (stale.length || errs.length) process.exit(1);
    console.log(`CHECK OK (${out.length} outputs)`);
  } else {
    for (const [file] of out) fs.mkdirSync(path.dirname(file), {recursive: true});
    for (const [file, data] of out) fs.writeFileSync(file, data);
    const pcs = pieces();
    const quads = pcs.reduce((s, p) => s + serialise(p, TEX).quads.length, 0);
    console.log(`wrote ${out.length} files (${pcs.length} mesh pieces, ${quads} quads)`);
    console.log('  mesh ' + JSON.stringify(doorMesh().stats));
    for (const p of pcs.filter(p => !p.name.startsWith('shield/') || p.name === 'shield/panel_0' || p.name === 'shield/panel_15')) console.log('  ' + p.name, serialise(p, TEX).quads.length);
    const errs = audit();
    for (const e of errs) console.log('audit: ' + e);
  }
}
