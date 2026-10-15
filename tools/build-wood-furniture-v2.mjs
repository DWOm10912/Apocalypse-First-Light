// Wood Furniture V2 (docs/models/wood_furniture_v2.md, 2026-10-15): the bedroom and storage furniture of the forest start
// home B ("溪谷老宅", M4 round 2 interior). User 2026-10-15: a Mesh wooden bed, a Mesh wardrobe, a nightstand and a tool
// rack; an old big steel chest freezer beside the commercial one; and an AFL storage chest instead of the vanilla chest
// (vanilla logic, our own ID). Same wood set as V1 (furniture/wood-furniture-art.mjs), soft goods and freezer insides in
// furniture/wood-furniture-v2-art.mjs.
//   wooden_bed            a single bed over two cells (vanilla bed logic): posts, a frame-and-panel head and foot board,
//                         side rails, a ticking mattress, a moss wool blanket with a turned-down sheet, a pillow
//   wooden_wardrobe       two cells high: plinth, a wide drawer, two frame-and-panel doors, a cornice; inside a hat
//                         shelf and a hanging rod (open: the doors swung out)
//   wooden_nightstand     52 cm wide, top at 66 cm: a drawer over an open shelf (open: the drawer pulled out); the
//                         emergency radio sits on it
//   wooden_tool_rack      on a wall: a peg board with three pairs of pegs (long tools hang head up) under a small shelf
//   wooden_storage_chest  two cells: a frame-and-panel blanket chest on bracket feet, waist moulding, an overhanging lid,
//                         an escutcheon and end handles; inside a till (open: the lid raised)
//   vintage_chest_freezer two cells: a rounded cream enamel cabinet, one hinged lid with a chrome latch handle, a pilot
//                         lamp and thermostat knob, a louvred kick plate; inside a frosted liner and a wire basket
// Every piece is drawn facing north (front at z 0, the back / wall at z 1); pieces over two cells are drawn whole and cut
// at the cell boundary (clipped), the second cell's moved back into 0..1. Writes textures, mesh pieces (cabin_mesh/
// furniture/), block / item models, blockstates, loot tables. Tags, item mass and lang are edited by hand; --check audits.
//   node tools/build-wood-furniture-v2.mjs            -> writes every output
//   node tools/build-wood-furniture-v2.mjs --check    -> verifies every output is up to date and the hand-edited data
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {renderX} from './cabin/cabin-art.mjs';
import {FURNITURE_V2_TEXTURES} from './furniture/wood-furniture-v2-art.mjs';
import {Piece, solid, cylinder, mirroredX, clipped, serialise, newell, dot, sub, cross, norm, add, scl} from './cabin/cabin-geo.mjs';
import {furnitureMesh} from './furniture/furniture-mesh.mjs';
import {bounds, state, profile} from './furniture/furniture-shapes.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const ASSETS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const DATA = path.join(ROOT, 'src/main/resources/data');
const NS = 'apocalypse_firstlight:', TEX = NS + 'block/furniture/';
const json = v => JSON.stringify(v, null, 2) + '\n';

/** The blocks: id, item mass (kg, furniture carry scale), carry tag, mining (axe: vanilla wood; the freezer as the commercial one), names. */
export const BLOCKS = [
  {id: 'wooden_bed', mass: 40, carry: 'oversized', tool: 'axe', en: 'Wooden Bed', zh: '木床'},
  {id: 'wooden_wardrobe', mass: 55, carry: 'oversized', tool: 'axe', en: 'Wooden Wardrobe', zh: '木衣柜'},
  {id: 'wooden_nightstand', mass: 9, carry: 'bulky', tool: 'axe', en: 'Wooden Nightstand', zh: '木床头柜'},
  {id: 'wooden_tool_rack', mass: 3, carry: 'bulky', tool: 'axe', en: 'Wooden Tool Rack', zh: '木质工具架'},
  {id: 'wooden_storage_chest', mass: 30, carry: 'oversized', tool: 'axe', en: 'Wooden Storage Chest', zh: '木储物箱'},
  {id: 'vintage_chest_freezer', mass: 75, carry: 'oversized', tool: 'pickaxe', tier: 'needs_iron_tool', en: 'Vintage Chest Freezer', zh: '老式冷柜'},
];

const TRIM = 'wood', TRIM_V = 'wood_v', TOP = 'wood_top', INSIDE = 'wood_inside', PANEL = 'wood_panel';
const IRON = 'iron', CAST = 'cast_iron', ENAMEL = 'enamel';
const BLANKET = 'blanket', LINEN = 'linen', TICKING = 'ticking', LINER = 'freezer_liner', RUBBER = 'rubber';
const CHROME = NS + 'block/radio/chrome', LAMP = NS + 'block/radio/lamp', LAMP_GLOW = NS + 'block/radio/lamp_glow';

// =============================================================================================== helpers

/** A polygon whose corners (and their normals) are reordered so its Newell normal points along `out`. */
function polyOut(pc, t, pts, out, opts = {}) {
  if (dot(newell(pts), out) < 0) {
    pts = [...pts].reverse();
    if (Array.isArray(opts.n) && Array.isArray(opts.n[0])) opts = {...opts, n: [...opts.n].reverse()};
  }
  return pc.poly(t, pts, opts);
}
/** A closed bar from a to b, w wide (across, perpendicular to `up`) and h high (along up's part across the bar). */
function bar(pc, t, a, b, up, w, h) {
  const d = norm(sub(b, a)), s = norm(cross(d, up)), u = cross(s, d);
  const C = (p, i, j) => add(add(p, scl(s, i * w / 2)), scl(u, j * h / 2));
  const A = [C(a, -1, -1), C(a, 1, -1), C(a, 1, 1), C(a, -1, 1)], B = [C(b, -1, -1), C(b, 1, -1), C(b, 1, 1), C(b, -1, 1)];
  polyOut(pc, t, A, scl(d, -1)); polyOut(pc, t, B, d);
  for (let i = 0; i < 4; i++) { const j = (i + 1) % 4, mid = sub(scl(add(A[i], A[j]), 0.5), a); polyOut(pc, t, [A[i], A[j], B[j], B[i]], mid); }
  return pc;
}
/** A copy moved by d. */
const moved = (src, d, name = src.name) => new Piece(name).merge(src, p => add(p, d));
/** A copy turned by ang (radians, right-handed) about the line through o along axis 'x' | 'y' | 'z'. */
function rotated(src, o, ax, ang, name = src.name) {
  const c = Math.cos(ang), s = Math.sin(ang);
  const R = ([x, y, z]) => ax === 'x' ? [x, y * c - z * s, y * s + z * c] : ax === 'y' ? [x * c + z * s, y, -x * s + z * c] : [x * c - y * s, x * s + y * c, z];
  return new Piece(name).merge(src, p => add(R(sub(p, o)), o), R);
}
/** A piece swung a quarter about the vertical line (hx, hz): (dx, dz) -> (dz, -dx), out toward -z (V1's counter doors). */
function swungOut(pc, hx, hz, name) {
  return new Piece(name).merge(pc, ([x, y, z]) => [hx + (z - hz), y, hz - (x - hx)], ([nx, ny, nz]) => [nz, ny, -nx]);
}
/** The part of a piece in the slab lo <= p[axis] <= hi, moved back by `shift` along that axis. */
function cut(src, axis, lo, hi, shift, name) {
  const a = [0, 0, 0], b = [0, 0, 0];
  a[axis] = -1; b[axis] = 1;
  const part = clipped(src, [[a, -lo], [b, hi]]);
  const d = [0, 0, 0]; d[axis] = -shift;
  return moved(part, d, name);
}

/** A rounded rectangle in x-z (corner radius r, seg steps a corner), counter-clockwise from above: [x, z, nx, nz] per point. */
function rrect(x0, z0, x1, z1, r, seg = 4) {
  r = Math.max(0.004, Math.min(r, (x1 - x0) / 2 - 1e-4, (z1 - z0) / 2 - 1e-4));
  const out = [];
  for (const [cx, cz, a0] of [[x1 - r, z1 - r, 0], [x0 + r, z1 - r, 90], [x0 + r, z0 + r, 180], [x1 - r, z0 + r, 270]])
    for (let i = 0; i <= seg; i++) { const a = (a0 + 90 * i / seg) * Math.PI / 180; out.push([cx + r * Math.cos(a), cz + r * Math.sin(a), Math.cos(a), Math.sin(a)]); }
  return out;
}
/**
 * A rounded slab: plan [x0, z0, x1, z1] with corner radius r, its side a stack of levels [y, inset, tex(, 'sharp')] (the
 * band from a level up takes its tex; a sharp level keeps each band's own normal there, else the two are averaged:
 * rounded), caps: bottom / top textures (null: open). inward: the surface of a well (normals in, the bottom cap faces up).
 */
function slab(pc, [x0, z0, x1, z1], r, levels, {bottom = null, top = null, seg = 4, inward = false} = {}) {
  const rings = levels.map(([, inset]) => rrect(x0 + inset, z0 + inset, x1 - inset, z1 - inset, r - inset, seg));
  const sg = inward ? -1 : 1;
  const band = [];
  for (let k = 0; k + 1 < levels.length; k++) {
    const dy = levels[k + 1][0] - levels[k][0], din = levels[k + 1][1] - levels[k][1], l = Math.hypot(dy, din) || 1;
    band.push([dy / l, din / l]);   // (outward, up) of the band's normal
  }
  const at = (k, own) => {   // the (outward, up) normal at level k for band `own`
    if (levels[k][3] === 'sharp') return band[own];
    const a = band[k - 1], b = band[k], v = a && b ? [a[0] + b[0], a[1] + b[1]] : a || b, l = Math.hypot(...v);
    return [v[0] / l, v[1] / l];
  };
  for (let k = 0; k + 1 < levels.length; k++) {
    const A = rings[k], B = rings[k + 1], ya = levels[k][0], yb = levels[k + 1][0], na = at(k, k), nb = at(k + 1, k), t = levels[k][2];
    for (let i = 0; i < A.length; i++) {
      const j = (i + 1) % A.length;
      const N = (p, n) => [sg * p[2] * n[0], sg * n[1], sg * p[3] * n[0]];
      const pts = [[A[i][0], ya, A[i][1]], [A[j][0], ya, A[j][1]], [B[j][0], yb, B[j][1]], [B[i][0], yb, B[i][1]]];
      const ns = [N(A[i], na), N(A[j], na), N(B[j], nb), N(B[i], nb)];
      if (Math.hypot(...sub(pts[0], pts[1])) < 1e-6 && Math.hypot(...sub(pts[2], pts[3])) < 1e-6) continue;
      polyOut(pc, t, pts, scl(add(add(ns[0], ns[1]), add(ns[2], ns[3])), 0.25), {n: ns});
    }
  }
  const cap = (k, t, up) => { if (t) polyOut(pc, t, rings[k].map(([x, z]) => [x, levels[k][0], z]), [0, up, 0]); };
  cap(0, bottom, inward ? 1 : -1);
  if (!inward) cap(levels.length - 1, top, 1);
  return pc;
}
/** The flat ring at height y between two contours of the same point count (rrect with the same seg), facing up / down. */
function ring(pc, t, outer, inner, y, up) {
  for (let i = 0; i < outer.length; i++) {
    const j = (i + 1) % outer.length;
    polyOut(pc, t, [[outer[i][0], y, outer[i][1]], [outer[j][0], y, outer[j][1]], [inner[j][0], y, inner[j][1]], [inner[i][0], y, inner[i][1]]], [0, up, 0]);
  }
}
/**
 * A sheet bent to a profile and run along z from z0 to z1: outer and inner are polylines in x-y (same count, inner the
 * sheet's other face); smooth across the profile, flat end caps, the two long edges closed.
 */
function sheet(pc, t, outer, inner, z0, z1) {
  const nrm = i => {   // the outer face's normal at i (x-y), pointing from inner to outer
    const a = outer[Math.max(0, i - 1)], b = outer[Math.min(outer.length - 1, i + 1)];
    let n = norm([b[1] - a[1], a[0] - b[0], 0]);
    if (dot(n, [outer[i][0] - inner[i][0], outer[i][1] - inner[i][1], 0]) < 0) n = scl(n, -1);
    return n;
  };
  const P = (p, z) => [p[0], p[1], z];
  for (let i = 0; i + 1 < outer.length; i++) {
    const na = nrm(i), nb = nrm(i + 1);
    polyOut(pc, t, [P(outer[i], z0), P(outer[i + 1], z0), P(outer[i + 1], z1), P(outer[i], z1)], scl(add(na, nb), 0.5), {n: [na, nb, nb, na]});
    polyOut(pc, t, [P(inner[i], z0), P(inner[i + 1], z0), P(inner[i + 1], z1), P(inner[i], z1)], scl(add(na, nb), -0.5), {n: [scl(na, -1), scl(nb, -1), scl(nb, -1), scl(na, -1)]});
    polyOut(pc, t, [P(outer[i], z0), P(outer[i + 1], z0), P(inner[i + 1], z0), P(inner[i], z0)], [0, 0, -1]);
    polyOut(pc, t, [P(outer[i], z1), P(outer[i + 1], z1), P(inner[i + 1], z1), P(inner[i], z1)], [0, 0, 1]);
  }
  for (const [i, k] of [[0, 1], [outer.length - 1, outer.length - 2]]) {
    const away = sub(P(outer[i], 0), P(outer[k], 0));
    polyOut(pc, t, [P(outer[i], z0), P(inner[i], z0), P(inner[i], z1), P(outer[i], z1)], away);
  }
  return pc;
}
/** An arc of points round (cx, cy), radius r, from angle a0 to a1 (degrees), steps segments. */
const arc = (cx, cy, r, a0, a1, steps) => Array.from({length: steps + 1}, (_, i) => { const a = (a0 + (a1 - a0) * i / steps) * Math.PI / 180; return [cx + r * Math.cos(a), cy + r * Math.sin(a)]; });

// =============================================================================================== bed
// Drawn whole from z -1 (the foot cell's front) to 1 (the head against the wall: the logs bulge ~7 cm into the cell, so
// the head posts stop at z 0.925); cut at z 0 into the head (0..1) and foot (-1..0, moved to 0..1) pieces.
// Posts 5.5 cm (head 1.0 m, foot 0.74 m, each with a cap), side rails 0.22..0.36, a frame-and-panel head board (rails, a
// V-groove panel recessed both sides) and foot board; mattress 0.32..0.54 between the posts (89 x 180 cm; its top near
// vanilla's 9/16, where a sleeper lies); the blanket drapes 14 cm down its sides from the foot to z 0.36, the sheet turned
// down over it to 0.44; the pillow on the sheet.
export const BED_TOP = 0.54;
function bedWhole() {
  const pc = new Piece('bed');
  const B = [];
  for (const [x0, x1] of [[0.02, 0.075], [0.925, 0.98]]) {
    for (const [z0, z1, h] of [[0.87, 0.925, 1.0], [-0.985, -0.93, 0.74]]) {
      B.push([TRIM_V, [x0, 0, z0], [x1, h, z1]]);
      B.push([{u: TOP, all: TRIM}, [x0 - 0.005, h, z0 - 0.005], [x1 + 0.005, h + 0.016, z1 + 0.005]]);
      B.push([{u: TOP, all: TRIM}, [x0 + 0.008, h + 0.016, z0 + 0.008], [x1 - 0.008, h + 0.028, z1 - 0.008]]);
    }
  }
  for (const [x0, x1] of [[0.025, 0.05], [0.95, 0.975]]) B.push([TRIM, [x0, 0.22, -0.93], [x1, 0.36, 0.87]]);
  // head board: rails and the recessed panel; foot board the same, lower
  B.push([TRIM, [0.075, 0.4, 0.885], [0.925, 0.48, 0.91]], [TRIM, [0.075, 0.86, 0.885], [0.925, 0.95, 0.91]]);
  B.push([{n: PANEL, s: PANEL, all: TRIM}, [0.075, 0.48, 0.891], [0.925, 0.86, 0.904]]);
  B.push([TRIM, [0.075, 0.34, -0.97], [0.925, 0.41, -0.945]], [TRIM, [0.075, 0.62, -0.97], [0.925, 0.7, -0.945]]);
  B.push([{n: PANEL, s: PANEL, all: TRIM}, [0.075, 0.41, -0.964], [0.925, 0.62, -0.951]]);
  solid(pc, B);
  // the mattress: ticking sides, the fitted sheet on top, edges rounded a little
  slab(pc, [0.055, -0.93, 0.945, 0.87], 0.035, [[0.32, 0.006, TICKING], [0.332, 0, TICKING], [0.528, 0, TICKING], [BED_TOP, 0.008]], {bottom: TICKING, top: LINEN});
  // the blanket over it (1.2 cm wool), then the sheet turned down over the blanket's head end (a little thicker)
  const drape = (r0, th) => {
    const o = [[0.04, 0.4], ...arc(0.04 + r0, 0.53, r0, 180, 90, 4), ...arc(0.96 - r0, 0.53, r0, 90, 0, 4), [0.96, 0.4]];
    const i = [[0.04 + th, 0.4], ...arc(0.04 + r0, 0.53, r0 - th, 180, 90, 4), ...arc(0.96 - r0, 0.53, r0 - th, 90, 0, 4), [0.96 - th, 0.4]];
    return [o, i];
  };
  const [bo, bi] = drape(0.025, 0.012);
  sheet(pc, BLANKET, bo, bi, -0.925, 0.36);
  const co = [[0.036, 0.395], ...arc(0.065, 0.53, 0.029, 180, 90, 4), ...arc(0.935, 0.53, 0.029, 90, 0, 4), [0.964, 0.395]];
  sheet(pc, LINEN, co, bi, 0.36, 0.44);
  // the pillow: a soft slab on the sheet
  slab(pc, [0.16, 0.56, 0.84, 0.855], 0.07, [[BED_TOP, 0.05, LINEN], [0.552, 0.014, LINEN], [0.575, 0, LINEN], [0.625, 0, LINEN], [0.645, 0.014, LINEN], [0.656, 0.05]], {bottom: LINEN, top: LINEN, seg: 5});
  return pc;
}
function bedPieces() {
  const w = bedWhole();
  return [cut(w, 2, 0, 1, 0, 'furniture/bed_head'), cut(w, 2, -1, 0, -1, 'furniture/bed_foot')];
}

// =============================================================================================== wardrobe
// Two cells high, its back on the wall (z 0.925), front at z 0.36 (57 cm deep, 96 wide): a 9 cm plinth set back, 2 cm sides,
// a full-width drawer 0.11..0.33, then two inset frame-and-panel doors 0.364..1.816 (a tall and a short panel each, iron
// knobs at the meeting stiles), a frieze rail and a stepped cornice to 1.96. Inside: a hat shelf at 1.62 and a rod at 1.55.
// Drawn whole (y 0..2) and cut at y 1 into the lower and upper pieces. Open: the doors swing out about their outer edges.
export const WARDROBE_FRONT = 0.36;
const WD = {y0: 0.364, y1: 1.816, mid0: 0.7, mid1: 0.76, f: 0.065, left: [0.044, 0.4985]};
function wardrobeBody() {
  const pc = new Piece('wardrobe');
  solid(pc, [
    [TRIM, [0.035, 0, 0.375], [0.965, 0.09, 0.92]],
    [{all: TRIM_V, e: INSIDE}, [0.02, 0.09, 0.36], [0.04, 1.86, 0.925]],
    [{all: TRIM_V, w: INSIDE}, [0.96, 0.09, 0.36], [0.98, 1.86, 0.925]],
    [INSIDE, [0.04, 0.09, 0.905], [0.96, 1.86, 0.925]],
    [TRIM, [0.044, 0.112, 0.36], [0.956, 0.328, 0.38]],
    [{all: INSIDE, n: TRIM}, [0.04, 0.09, 0.38], [0.96, 0.362, 0.905]],
    [{all: INSIDE, n: TRIM}, [0.04, 1.82, 0.36], [0.96, 1.86, 0.905]],
    [TRIM, [0.012, 1.86, 0.35], [0.988, 1.9, 0.925]],
    [{u: TOP, all: TRIM}, [0.0, 1.9, 0.335], [1.0, 1.96, 0.925]],
    [INSIDE, [0.04, 1.62, 0.4], [0.96, 1.64, 0.905]],
  ]);
  // the gap above the drawer front (0.328..0.36) shows the front rail; drawer knobs
  for (const x of [0.25, 0.75]) cylinder(pc, IRON, IRON, 'z', [x, 0.22], 0.338, 0.36, 0.011, 10, 'a');
  // the hanging rod and its two sockets
  cylinder(pc, IRON, IRON, 'x', [1.55, 0.64], 0.04, 0.96, 0.012, 10);
  solid(pc, [[INSIDE, [0.04, 1.525, 0.615], [0.055, 1.575, 0.665]], [INSIDE, [0.945, 1.525, 0.615], [0.96, 1.575, 0.665]]]);
  return pc;
}
/** The left door, closed (front at z 0.36, 2 cm): stiles, three rails, a tall and a short recessed panel, the knob. */
function wardrobeDoor() {
  const [x0, x1] = WD.left, {y0, y1, mid0, mid1, f} = WD, z0 = WARDROBE_FRONT, z1 = z0 + 0.02, pc = new Piece('d');
  solid(pc, [
    [TRIM_V, [x0, y0, z0], [x0 + f, y1, z1]], [TRIM_V, [x1 - f, y0, z0], [x1, y1, z1]],
    [TRIM, [x0 + f, y0, z0], [x1 - f, y0 + 0.08, z1]], [TRIM, [x0 + f, mid0, z0], [x1 - f, mid1, z1]], [TRIM, [x0 + f, y1 - 0.08, z0], [x1 - f, y1, z1]],
    [{n: PANEL, all: TRIM}, [x0 + f, y0 + 0.08, z0 + 0.007], [x1 - f, mid0, z1]],
    [{n: PANEL, all: TRIM}, [x0 + f, mid1, z0 + 0.007], [x1 - f, y1 - 0.08, z1]],
  ]);
  cylinder(pc, IRON, IRON, 'z', [x1 - 0.035, 1.06], z0 - 0.024, z0, 0.012, 10, 'a');
  return pc;
}
/**
 * The animated mesh (tools/furniture/furniture-mesh.mjs, 2026-10-15): the body, and each door its own bone hinged on its
 * outer stile at the front (x WD.left[0] and mirrored); 'open' swings both a quarter out, 10 ticks.
 */
function wardrobeMesh() {
  const d = wardrobeDoor();
  return {id: 'wooden_wardrobe', bones: [{name: 'body', pivot: [0.5, 0, 0.5], pieces: [wardrobeBody()]},
    {name: 'door_left', pivot: [WD.left[0], 0, WARDROBE_FRONT], pieces: [d]}, {name: 'door_right', pivot: [1 - WD.left[0], 0, WARDROBE_FRONT], pieces: [mirroredX(d)]}],
    open: {ticks: 10, transforms: {door_left: {rotation: [0, 90, 0]}, door_right: {rotation: [0, -90, 0]}}}};
}

// =============================================================================================== nightstand
// 52 x 40 cm, its back 1 cm off the wall's logs (z 0.92); 3.5 cm legs, side and back panels from 0.12, a bottom shelf at
// 0.10..0.12, a drawer (front 0.49..0.61 between rails) and the top 0.635..0.66 with a 1 cm overhang. The cubby under the
// drawer is open. Open: the drawer out 24 cm, its box (sides, back, floor) and the empty runner inside shown.
export const NIGHTSTAND_TOP = 0.66;
const NS_DRAWER_OUT = 0.24;
function nightstandBody() {
  const pc = new Piece('furniture/nightstand_body');
  const B = [];
  for (const x of [0.24, 0.725]) for (const z of [0.52, 0.885]) B.push([TRIM_V, [x, 0, z], [x + 0.035, 0.635, z + 0.035]]);
  B.push([{all: TRIM_V, e: INSIDE}, [0.245, 0.12, 0.555], [0.268, 0.635, 0.885]], [{all: TRIM_V, w: INSIDE}, [0.732, 0.12, 0.555], [0.755, 0.635, 0.885]]);
  B.push([{all: TRIM, n: INSIDE}, [0.275, 0.12, 0.895], [0.725, 0.635, 0.915]]);
  B.push([{all: TRIM, u: INSIDE}, [0.268, 0.1, 0.555], [0.732, 0.12, 0.895]], [TRIM, [0.275, 0.06, 0.525], [0.725, 0.12, 0.545]]);
  B.push([{all: TRIM, u: INSIDE}, [0.275, 0.465, 0.52], [0.725, 0.487, 0.895]]);   // the rail under the drawer and its runner board
  B.push([{all: TRIM, d: INSIDE}, [0.275, 0.613, 0.52], [0.725, 0.635, 0.895]]);    // the top rail and the dust board
  B.push([{u: TOP, all: TRIM}, [0.23, 0.635, 0.51], [0.77, NIGHTSTAND_TOP, 0.925]]);
  return solid(pc, B);
}
/** The drawer, closed: a front flush with the rails, a knob, the box behind it (12 mm sides, a back, a floor). */
function nightstandDrawer(dz, name) {
  const pc = new Piece(name), z = v => v - dz;
  solid(pc, [
    [TRIM, [0.277, 0.489, z(0.52)], [0.723, 0.611, z(0.54)]],
    [{all: TRIM_V, e: INSIDE}, [0.283, 0.495, z(0.54)], [0.295, 0.59, z(0.875)]], [{all: TRIM_V, w: INSIDE}, [0.705, 0.495, z(0.54)], [0.717, 0.59, z(0.875)]],
    [{all: TRIM, n: INSIDE}, [0.295, 0.495, z(0.863)], [0.705, 0.59, z(0.875)]],
    [{all: TRIM, u: INSIDE}, [0.295, 0.495, z(0.54)], [0.705, 0.503, z(0.863)]],
  ]);
  cylinder(pc, IRON, IRON, 'z', [0.5, 0.55], z(0.497), z(0.52), 0.012, 10, 'a');
  return pc;
}

// =============================================================================================== tool rack
// On a wall (the wall at z 1): a peg board 0.68..0.86 the cell wide, three pairs of 2.2 cm pegs at 0.745 (long tools hang
// on them head up, in the plane z TOOL_Z), a 2.2 cm shelf at 0.86 out to z 0.80 with a front lip, on two braced brackets.
// On a round log wall the rack stands LOG_DEPTH out on the pad the wall draws (block/LogMount kind 12): tool_rack_log.
export const LOG_DEPTH = 0.08, TOOL_X = [0.2, 0.5, 0.8], PEG_Y = 0.745, TOOL_Z = 0.925, RACK_SHELF_Y = 0.882, RACK_SHELF_Z = 0.895, RACK_SHELF_X = [0.7, 0.3];
function toolRack() {
  const pc = new Piece('furniture/tool_rack');
  solid(pc, [
    [TRIM, [0.03, 0.68, 0.975], [0.97, 0.86, 1]],
    [{u: TOP, all: TRIM}, [0.03, 0.86, 0.8], [0.97, RACK_SHELF_Y, 1]],
    [TRIM, [0.03, RACK_SHELF_Y, 0.8], [0.97, 0.896, 0.815]],
    [TRIM_V, [0.05, 0.8, 0.84], [0.07, 0.86, 0.975]], [TRIM_V, [0.93, 0.8, 0.84], [0.95, 0.86, 0.975]],
  ]);
  for (const x of [0.06, 0.94]) bar(pc, TRIM_V, [x, 0.705, 0.968], [x, 0.852, 0.845], [1, 0, 0], 0.018, 0.022);
  for (const x of TOOL_X) for (const dx of [-0.05, 0.05]) cylinder(pc, TRIM_V, TRIM, 'z', [x + dx, PEG_Y], 0.86, 0.975, 0.011, 10, 'a');
  return pc;
}

// =============================================================================================== storage chest
// A frame-and-panel blanket chest over two cells across (left = the master, x 0..1; right 1..2; user 2026-10-15: concept A
// of design/buildings/forest_start_home_v1/m4_sheets/chest_concepts.png, one piece over two cells, no joining of two), 124
// x 50 cm, 60 cm high to the lid's top (blanket chests run 109-124 cm wide, 46-53 deep, 51-58 high). Bracket feet (8 cm)
// under a base moulding; the case of 2 cm walls, framed outside (stiles, rails, muntins 1 cm proud) round three recessed
// front panels and one panel each end; a waist moulding round front and ends; a 3.3 cm lid overhanging 2.5 cm, two
// battens under it, on two butt hinges at the back (axis y 0.565, z 0.758). Inside: the floor at 0.135, a till (an open
// tray) across the left end. An iron escutcheon on the top rail, drop handles on the ends' top rails. Open: the lid raised
// 80 degrees, its front edge up to 1.10 (over the cells: AflOverhangPickBlock).
export const CHEST = {x0: 0.38, x1: 1.62, z0: 0.25, z1: 0.75, feet: 0.08, base: 0.115, waist: 0.54, rim: 0.565, lid: 0.598, open: 80,
  frame: 0.01, wall: 0.03, rail: [0.17, 0.5], endRail: [0.17, 0.42], hinges: [0.66, 1.34]};
const CHEST_HINGE = [0, CHEST.rim, CHEST.z1 + 0.008];
/** Bracket feet at the four corners, h high, each two boards a run long, stepped down at the inner end. */
function bracketFeet(x0, x1, z0, z1, h, run = 0.13) {
  const b = [], t = 0.024, o = 0.012;
  for (const [cx, sx] of [[x0, 1], [x1, -1]]) for (const [cz, sz] of [[z0, 1], [z1, -1]]) {
    const X = (a, c) => [Math.min(cx + sx * a, cx + sx * c), Math.max(cx + sx * a, cx + sx * c)];
    const Z = (a, c) => [Math.min(cz + sz * a, cz + sz * c), Math.max(cz + sz * a, cz + sz * c)];
    const [xa, xb] = X(-o, run), [xs, xt] = X(run, run + 0.05), [za, zb] = Z(-o, -o + t), [ta, tb] = X(-o, -o + t), [zc, zd] = Z(-o, run), [zs, zt] = Z(run, run + 0.05);
    b.push([TRIM, [xa, 0, za], [xb, h, zb]], [TRIM, [xs, h * 0.55, za], [xt, h, zb]], [TRIM_V, [ta, 0, zc], [tb, h, zd]], [TRIM_V, [ta, h * 0.55, zs], [tb, h, zt]]);
  }
  return b;
}
function chestBody() {
  const {x0, x1, z0, z1, feet, base, waist, rim, frame: f, wall: w, rail, endRail} = CHEST, pc = new Piece('cb');
  const fx = [x0 + 0.075, x1 - 0.075], muntin = 0.04, pw = (fx[1] - fx[0] - 2 * muntin) / 3;
  const ends = [[x0, x0 + f], [x1 - f, x1]].flatMap(([a, b]) => [
    [TRIM_V, [a, base, z0], [b, waist, z0 + 0.065]], [TRIM_V, [a, base, z1 - 0.055], [b, waist, z1]],
    [TRIM, [a, base, z0], [b, endRail[0], z1]], [TRIM, [a, endRail[1], z0], [b, waist, z1]]]);
  solid(pc, [
    // the walls (the panels outside, the inside face in), the floor, the till across the left end
    [{all: TRIM, n: PANEL, s: INSIDE, u: TOP}, [x0 + f, base, z0 + f], [x1 - f, rim, z0 + w]],
    [{all: TRIM, n: INSIDE, u: TOP}, [x0 + f, base, z1 - 0.02], [x1 - f, rim, z1]],
    [{all: TRIM, w: PANEL, e: INSIDE, u: TOP}, [x0 + f, base, z0 + w], [x0 + w, rim, z1 - 0.02]],
    [{all: TRIM, e: PANEL, w: INSIDE, u: TOP}, [x1 - w, base, z0 + w], [x1 - f, rim, z1 - 0.02]],
    [INSIDE, [x0 + w, base, z0 + w], [x1 - w, base + 0.02, z1 - 0.02]],
    [INSIDE, [x0 + w, 0.4, z0 + w], [x0 + 0.25, 0.41, z1 - 0.02]], [INSIDE, [x0 + 0.24, 0.41, z0 + w], [x0 + 0.25, 0.5, z1 - 0.02]],
    // the front frame: stiles, rails, two muntins; the ends' frames
    [TRIM_V, [x0, base, z0], [fx[0], waist, z0 + f]], [TRIM_V, [fx[1], base, z0], [x1, waist, z0 + f]],
    [TRIM, [x0, base, z0], [x1, rail[0], z0 + f]], [TRIM, [x0, rail[1], z0], [x1, waist, z0 + f]],
    [TRIM_V, [fx[0] + pw, rail[0], z0], [fx[0] + pw + muntin, rail[1], z0 + f]], [TRIM_V, [fx[1] - pw - muntin, rail[0], z0], [fx[1] - pw, rail[1], z0 + f]],
    ...ends,
    // the waist moulding round the front and ends, the base moulding, the feet
    [{all: TRIM, u: TOP}, [x0 - 0.012, waist, z0 - 0.012], [x1 + 0.012, rim, z0 + w]],
    [{all: TRIM, u: TOP}, [x0 - 0.012, waist, z0 + w], [x0 + w, rim, z1]], [{all: TRIM, u: TOP}, [x1 - w, waist, z0 + w], [x1 + 0.012, rim, z1]],
    [{all: TRIM, u: TOP}, [x0 - 0.014, feet, z0 - 0.014], [x1 + 0.014, base, z1 + 0.006]],
    ...bracketFeet(x0, x1, z0, z1, feet),
  ]);
  // the escutcheon on the top rail, the keyhole in it
  const mx = (x0 + x1) / 2;
  solid(pc, [[IRON, [mx - 0.015, 0.503, z0 - 0.003], [mx + 0.015, 0.537, z0]]]);
  solid(pc, [[CAST, [mx - 0.0035, 0.508, z0 - 0.0035], [mx + 0.0035, 0.526, z0 - 0.003]]]);
  // drop handles on the ends' top rails: the plate, two loops, the bar hanging
  const zc = (z0 + z1) / 2, y = 0.525;
  for (const [x, s] of [[x0, -1], [x1, 1]]) {
    const out = d => x + s * d, lo = (a, b) => [Math.min(out(a), out(b)), Math.max(out(a), out(b))];
    const [p0, p1] = lo(0, 0.004), [l0, l1] = lo(0.004, 0.016), [b0, b1] = lo(0.012, 0.024);
    solid(pc, [[IRON, [p0, y - 0.07, zc - 0.07], [p1, y, zc + 0.07]], [IRON, [l0, y - 0.03, zc - 0.055], [l1, y - 0.018, zc - 0.043]],
      [IRON, [l0, y - 0.03, zc + 0.043], [l1, y - 0.018, zc + 0.055]], [IRON, [b0, y - 0.065, zc - 0.055], [b1, y - 0.053, zc + 0.055]],
      [IRON, [b0, y - 0.053, zc - 0.055], [b1, y - 0.03, zc - 0.043]], [IRON, [b0, y - 0.053, zc + 0.043], [b1, y - 0.03, zc + 0.055]]]);
  }
  // the hinges' lower leaves on the back (the knuckle and upper leaf go with the lid)
  for (const hx of CHEST.hinges) solid(pc, [[IRON, [hx - 0.025, rim - 0.045, z1], [hx + 0.025, rim, z1 + 0.004]]]);
  return pc;
}
/** The lid, closed: the board (overhanging front and ends), two battens under its ends, the hinges' upper leaves and knuckles. */
function chestLid() {
  const {x0, x1, z0, z1, rim, lid, wall: w} = CHEST, pc = new Piece('cl');
  solid(pc, [
    [{u: TOP, d: INSIDE, all: TRIM}, [x0 - 0.025, rim, z0 - 0.025], [x1 + 0.025, lid, z1 + 0.004]],
    [INSIDE, [x0 + w + 0.005, rim - 0.018, z0 + w + 0.005], [x0 + w + 0.045, rim, z1 - 0.025]],
    [INSIDE, [x1 - w - 0.045, rim - 0.018, z0 + w + 0.005], [x1 - w - 0.005, rim, z1 - 0.025]],
  ]);
  for (const hx of CHEST.hinges) {
    solid(pc, [[IRON, [hx - 0.025, rim, z1 + 0.004], [hx + 0.025, lid - 0.004, z1 + 0.008]]]);
    cylinder(pc, IRON, IRON, 'x', [rim, z1 + 0.008], hx - 0.025, hx + 0.025, 0.006, 8, 'ab');
  }
  return pc;
}
const chestLidOpen = l => rotated(l, CHEST_HINGE, 'x', CHEST.open * Math.PI / 180);
/** The animated mesh (2026-10-15): the body over both cells, the lid a bone on the hinges' axis; 'open' raises it CHEST.open degrees, 12 ticks. */
function chestMesh() {
  return {id: 'wooden_storage_chest', bones: [{name: 'body', pivot: [0.5, 0, 0.5], pieces: [chestBody()]},
    {name: 'lid', pivot: [1, CHEST_HINGE[1], CHEST_HINGE[2]], pieces: [chestLid()]}],
    open: {ticks: 12, transforms: {lid: {rotation: [CHEST.open, 0, 0]}}}};
}

// =============================================================================================== vintage chest freezer
// Two cells along x (left = the master, x 0..1; right 1..2): a 1960s chest deep freezer, 160 x 74 cm, 90 cm high. The
// cabinet a rounded box (7 cm corners) of cream enamel on a dark louvred kick plate (set back 3.5 cm, 0..0.085), its top a
// breaker ring at 0.83 round the well (x 0.30..1.70, z 0.27..0.83, floor 0.13), the liner frosted. The lid 0.835..0.90,
// its top edge rounded, a rubber gasket under its rim and a liner plug that drops into the well; hinged at the back
// (axis y 0.835, z 0.925), open 80 degrees. A chrome latch handle with a lock at the middle front, a pilot lamp and a
// thermostat knob at the front right (as seen from the front: low x), a C14 inlet low on the back at that end. A wire
// basket hangs in the other end of the well.
export const FREEZER = {x0: 0.2, x1: 1.8, z0: 0.18, z1: 0.92, top: 0.83, lid0: 0.835, lid1: 0.9, open: 80,
  well: [0.3, 0.27, 1.7, 0.83], floor: 0.13, lamp: [0.36, 0.77], knob: [0.46, 0.77], inlet: [0.38, 0.16]};
function freezerBody() {
  const {x0, x1, z0, z1, top, well, floor} = FREEZER, pc = new Piece('fb');
  slab(pc, [x0, z0, x1, z1], 0.07, [
    [0, 0.035, CAST], [0.085, 0.035, ENAMEL, 'sharp'], [0.085, 0, ENAMEL, 'sharp'], [0.8, 0, ENAMEL], [0.82, 0.006, ENAMEL], [top, 0.02],
  ], {bottom: CAST, seg: 5});
  // the breaker ring between the cabinet's top contour and the well; the well's liner, its floor facing up
  const outer = rrect(x0 + 0.02, z0 + 0.02, x1 - 0.02, z1 - 0.02, 0.05, 5), inner = rrect(...well, 0.03, 5);
  ring(pc, ENAMEL, outer, inner, top, 1);
  slab(pc, well, 0.03, [[floor, 0, LINER], [top, 0, LINER]], {bottom: LINER, seg: 5, inward: true});
  // louvres on the kick plate's front, x 0.5..1.5
  for (let k = 0; k < 4; k++) { const y = 0.018 + k * 0.017; bar(pc, CAST, [0.5, y, z0 + 0.03], [1.5, y, z0 + 0.03], [0, 1, 0], 0.012, 0.004); }
  // thermostat knob (chrome, round the z axis) and the lamp's bezel; the cord's C14 inlet on the back
  cylinder(pc, CHROME, CHROME, 'z', FREEZER.knob, z0 - 0.022, z0, 0.018, 14, 'a');
  cylinder(pc, CHROME, CHROME, 'z', FREEZER.lamp, z0 - 0.006, z0, 0.016, 14, 'a');
  solid(pc, [[RUBBER, [FREEZER.inlet[0] - 0.025, FREEZER.inlet[1] - 0.018, z1], [FREEZER.inlet[0] + 0.025, FREEZER.inlet[1] + 0.018, z1 + 0.012]]]);
  // the hinge brackets on the back
  for (const hx of [0.45, 1.55]) solid(pc, [[CHROME, [hx - 0.04, 0.76, z1], [hx + 0.04, top, z1 + 0.012]]]);
  // the wire basket in the left of the well: top and bottom frames, uprights, a bottom grid, two hooks over the rim
  const W = 0.004, bx0 = 1.24, bx1 = 1.67, bz0 = 0.3, bz1 = 0.8, by0 = 0.53, by1 = 0.77;
  const wire = (a, b) => bar(pc, CHROME, a, b, Math.abs(a[1] - b[1]) > 1e-6 ? [1, 0, 0] : [0, 1, 0], W, W);
  for (const y of [by0, by1]) { wire([bx0, y, bz0], [bx1, y, bz0]); wire([bx0, y, bz1], [bx1, y, bz1]); wire([bx0, y, bz0], [bx0, y, bz1]); wire([bx1, y, bz0], [bx1, y, bz1]); }
  for (let x = bx0; x <= bx1 + 1e-6; x += (bx1 - bx0) / 6) { wire([x, by0, bz0], [x, by1, bz0]); wire([x, by0, bz1], [x, by1, bz1]); wire([x, by0, bz0], [x, by0, bz1]); }
  for (let z = bz0 + 0.1; z < bz1 - 1e-6; z += 0.1) { wire([bx0, by0, z], [bx0, by1, z]); wire([bx1, by0, z], [bx1, by1, z]); }
  for (const x of [bx0 + 0.04, bx1 - 0.04]) for (const z of [bz0, bz1]) {
    const s = z < 0.5 ? -1 : 1, rimZ = s < 0 ? well[1] : well[3];
    wire([x, by1, z], [x, top + 0.003, z]); wire([x, top + 0.003, z], [x, top + 0.003, rimZ + s * 0.012]);
  }
  return pc;
}
/** The lid, closed: rounded slab, liner underneath, gasket and plug, the chrome latch handle and lock. */
function freezerLid() {
  const {x0, x1, z0, z1, top, lid0, lid1, well} = FREEZER, pc = new Piece('fl');
  slab(pc, [x0 - 0.005, z0 - 0.005, x1 + 0.005, z1 + 0.005], 0.072, [[lid0, 0, ENAMEL], [0.88, 0, ENAMEL], [0.893, 0.008, ENAMEL], [lid1, 0.026]], {bottom: LINER, top: ENAMEL, seg: 5});
  slab(pc, [well[0] - 0.03, well[1] - 0.03, well[2] + 0.03, well[3] + 0.03], 0.05, [[top, 0, RUBBER], [lid0, 0]], {bottom: RUBBER, seg: 5});
  slab(pc, [well[0] + 0.005, well[1] + 0.005, well[2] - 0.005, well[3] - 0.005], 0.028, [[0.79, 0, LINER], [top, 0]], {bottom: LINER, seg: 5});
  // the latch handle: a bar on two posts off the lid's front, the lock barrel between them
  const zf = z0 - 0.005;
  solid(pc, [[CHROME, [0.9, 0.852, zf - 0.03], [1.1, 0.872, zf - 0.016]], [CHROME, [0.9, 0.852, zf - 0.016], [0.918, 0.872, zf]], [CHROME, [1.082, 0.852, zf - 0.016], [1.1, 0.872, zf]],
    [CHROME, [0.94, 0.84, zf - 0.006], [1.06, 0.884, zf]]]);
  cylinder(pc, CHROME, CHROME, 'z', [1.0, 0.846], zf - 0.014, zf - 0.006, 0.006, 12, 'a');
  return pc;
}
/** The pilot lamp (left cell), unlit or lit (the glow texture: full bright, LabPBR emission). */
function freezerLamp(lit) {
  const pc = new Piece('lamp'), [lx, ly] = FREEZER.lamp;
  const c = [lx, ly, FREEZER.z0 - 0.006], r = 0.011, h = 0.008, seg = 12, rings = 3;
  for (let k = 0; k < rings; k++) for (let i = 0; i < seg; i++) {
    const at = (kk, ii) => { const phi = kk / rings * Math.PI / 2, a = ii / seg * Math.PI * 2, rr = r * Math.cos(phi);
      return {p: [c[0] + rr * Math.cos(a), c[1] + rr * Math.sin(a), c[2] - h * Math.sin(phi)], n: norm([Math.cos(phi) * Math.cos(a), Math.cos(phi) * Math.sin(a), -Math.sin(phi) * r / h])}; };
    if (k === rings - 1) continue;
    const q = [at(k, i), at(k, i + 1), at(k + 1, i + 1), at(k + 1, i)];
    polyOut(pc, lit ? LAMP_GLOW : LAMP, q.map(v => v.p), scl(add(q[0].n, q[2].n), 0.5), {n: q.map(v => v.n)});
  }
  const capPts = Array.from({length: seg}, (_, i) => { const phi = (rings - 1) / rings * Math.PI / 2, a = i / seg * Math.PI * 2, rr = r * Math.cos(phi); return [c[0] + rr * Math.cos(a), c[1] + rr * Math.sin(a), c[2] - h * Math.sin(phi)]; });
  polyOut(pc, lit ? LAMP_GLOW : LAMP, capPts, [0, 0, -1]);
  return pc;
}
/**
 * The animated mesh (2026-10-15): the cabinet over both cells, the lid a bone on its hinge axis ('open': FREEZER.open
 * degrees up, 14 ticks), the lamp as 'lights' (unlit) and 'lights_lit' (lit, emissive, not in the item icon), as the
 * commercial chest freezer's.
 */
function freezerMesh() {
  return {id: 'vintage_chest_freezer', bones: [{name: 'body', pivot: [0.5, 0, 0.5], pieces: [freezerBody()]},
    {name: 'lid', pivot: [1, FREEZER.lid0, FREEZER.z1 + 0.005], pieces: [freezerLid()]},
    {name: 'lights', pivot: [0.5, 0, 0.5], pieces: [freezerLamp(false)]}, {name: 'lights_lit', pivot: [0.5, 0, 0.5], pieces: [freezerLamp(true)], hidden: true}],
    open: {ticks: 14, transforms: {lid: {rotation: [FREEZER.open, 0, 0]}}}};
}

// =============================================================================================== shapes
// The Mesh Shape profiles of the storage (cabin/FurnitureOpening, docs/rendering/mesh_shape_runtime_v1.md), from the pieces
// drawn: closed, the doors / drawer / lid (open them); open, those swung out (close them) and the open interior (search /
// view). Regions are grown 1 cm so their edges are easy to hit.
const grow = ([a, b], e = 0.01) => [a.map(v => v - e), b.map(v => v + e)];
function wardrobeShapes() {
  const d = wardrobeDoor(), sw = swungOut(d, WD.left[0], WARDROBE_FRONT, 's');
  const body = [[0, 0, 0.335], [1, 1.96, 0.925]], [lo, hi] = bounds(d), left = bounds(sw), right = bounds(mirroredX(sw));
  const knob = [WD.left[1] - 0.035, 1.06, WARDROBE_FRONT - 0.012], swung = ([x, y, z]) => [WD.left[0] + (z - WARDROBE_FRONT), y, WARDROBE_FRONT - (x - WD.left[0])];
  return profile({
    closed: state({physical: [body], regions: {door: [[grow([lo, [1 - lo[0], hi[1], hi[2]]])], [0.5, knob[1], knob[2]]]}}),
    open: state({physical: [body], selection: [body, left, right], regions: {
      door: [[grow(left), grow(right)], swung(knob)],
      interior: [[[[left[1][0], lo[1], 0.325], [right[0][0], hi[1], WARDROBE_FRONT + 0.04]]], [0.5, 1.1, WARDROBE_FRONT]]}}),
  }, 2);
}
function nightstandShapes() {
  const body = [[0.23, 0, 0.51], [0.77, NIGHTSTAND_TOP, 0.925]];
  const [lo, hi] = bounds(nightstandDrawer(0, 'c')), [olo, ohi] = bounds(nightstandDrawer(NS_DRAWER_OUT, 'o'));
  const front = 0.52 - NS_DRAWER_OUT, knob = [0.5, 0.55, 0.508];
  return profile({
    closed: state({physical: [body], regions: {drawer: [[grow([[lo[0], lo[1], lo[2]], [hi[0], hi[1], 0.545]])], knob]}}),
    open: state({physical: [body], selection: [body, [olo, [ohi[0], ohi[1], 0.52]]], regions: {
      drawer: [[grow([olo, [ohi[0], ohi[1], front + 0.02]])], [0.5, 0.55, knob[2] - NS_DRAWER_OUT]],
      interior: [[[[olo[0] + 0.006, 0.495, front + 0.02], [ohi[0] - 0.006, 0.612, 0.52]]], [0.5, 0.6, (front + 0.52) / 2]]}}),
  });
}
/** Two cells across: each side's state in its own frame (the right one 1 block over), the regions over the whole freezer. */
function freezerShapes() {
  const {x0, x1, z0, z1, top, well} = FREEZER, lid = freezerLid();
  const open = rotated(lid, [0, FREEZER.lid0, FREEZER.z1 + 0.005], 'x', FREEZER.open * Math.PI / 180);
  const [lo, hi] = bounds(lid), [olo, ohi] = bounds(open);
  const cut = ([a, b], c0, c1) => [[Math.max(a[0], c0), a[1], a[2]], [Math.min(b[0], c1), b[1], b[2]]];
  const states = {};
  for (const [side, c0, dx] of [['left', 0, 0], ['right', 1, -1]]) {
    const body = cut([[x0, 0, z0], [x1, top, z1]], c0, c0 + 1);
    states[side + '_closed'] = state({physical: [body], selection: [body, cut([lo, hi], c0, c0 + 1)], regions: {lid: [[grow([lo, hi])], [1.0, 0.862, lo[2]]]}}, dx);
    states[side + '_open'] = state({physical: [body], selection: [body, cut([olo, ohi], c0, c0 + 1)], regions: {
      lid: [[grow([olo, ohi])], [1.0, ohi[1] - 0.04, olo[2]]],
      interior: [[[[well[0], top - 0.08, well[1]], [well[2], top + 0.008, well[3]]]], [1.0, top, (well[1] + well[3]) / 2]]}}, dx);
  }
  return profile(states);
}
/** The chest, as the freezer: each side's state in its own frame, the lid and interior regions over the whole chest. */
function chestShapes() {
  const {x0, x1, z0, z1, rim, lid: top, wall: w} = CHEST, lid = chestLid(), open = chestLidOpen(lid);
  const [lo, hi] = bounds(lid), [olo, ohi] = bounds(open);
  const cutX = ([a, b], c0, c1) => [[Math.max(a[0], c0), a[1], a[2]], [Math.min(b[0], c1), b[1], b[2]]];
  const states = {};
  for (const [side, c0, dx] of [['left', 0, 0], ['right', 1, -1]]) {
    const body = cutX([[x0 - 0.014, 0, z0 - 0.014], [x1 + 0.014, rim, z1 + 0.006]], c0, c0 + 1);
    states[side + '_closed'] = state({physical: [body], selection: [body, cutX([lo, hi], c0, c0 + 1)],
      regions: {lid: [[grow([lo, hi])], [1.0, (rim + top) / 2, lo[2]]]}}, dx);
    states[side + '_open'] = state({physical: [body], selection: [body, cutX([olo, ohi], c0, c0 + 1)], regions: {
      lid: [[grow([olo, ohi])], [1.0, ohi[1] - 0.04, olo[2]]],
      interior: [[[[x0 + w, rim - 0.08, z0 + w], [x1 - w, rim + 0.008, z1 - 0.02]]], [1.0, rim, (z0 + z1) / 2]]}}, dx);
  }
  return profile(states);
}

/** The animated mesh (2026-10-15): the body, the drawer a bone; 'open' pulls it NS_DRAWER_OUT out, 7 ticks. */
function nightstandMesh() {
  return {id: 'wooden_nightstand', bones: [{name: 'body', pivot: [0.5, 0, 0.5], pieces: [nightstandBody()]},
    {name: 'drawer', pivot: [0.5, 0.55, 0.52], pieces: [nightstandDrawer(0, 'drawer')]}],
    open: {ticks: 7, transforms: {drawer: {translation: [0, 0, -NS_DRAWER_OUT]}}}};
}

/** The pieces still drawn by the cabin kit (no moving parts): the bed, the tool rack. */
export function pieces() {
  return [...bedPieces(), toolRack(), moved(toolRack(), [0, 0, -LOG_DEPTH], 'furniture/tool_rack_log')];
}

/**
 * The furniture with moving parts, as AFL Animated Block Meshes (tools/furniture/furniture-mesh.mjs, 2026-10-15: the
 * cabin pieces swapped between closed and open in one frame; user: "之前的那些模型，打开都有流畅的动画").
 */
export function meshes() {
  return [wardrobeMesh(), nightstandMesh(), chestMesh(), freezerMesh()].map(m => furnitureMesh(ROOT, {...m, dir: 'furniture', texPrefix: TEX, particle: PARTICLE[m.id]}));
}
export const MESH_IDS = ['wooden_wardrobe', 'wooden_nightstand', 'wooden_storage_chest', 'vintage_chest_freezer'];

// =============================================================================================== models, states, loot
// "item": [piece, turn, dx, dy, dz]; two-cell pieces get a display that fits them (the cabin door's for the wardrobe)
const ITEMS = {
  wooden_bed: [['furniture/bed_head', 0, 0, 0, 0.5], ['furniture/bed_foot', 0, 0, 0, -0.5]],
  wooden_tool_rack: [['furniture/tool_rack', 0]],
};
const T = (r, t, s) => ({rotation: r, translation: t, scale: [s, s, s]});
const DISPLAY_LONG = {gui: T([30, 225, 0], [0, 0, 0], 0.36), ground: T([0, 0, 0], [0, 3, 0], 0.18), fixed: T([0, 0, 0], [0, 0, 0], 0.4),
  thirdperson_righthand: T([75, 45, 0], [0, 2, 0], 0.22), firstperson_righthand: T([0, 45, 0], [0, 0, 0], 0.26), firstperson_lefthand: T([0, 225, 0], [0, 0, 0], 0.26)};
const DISPLAY = {wooden_bed: DISPLAY_LONG};
const PARTICLE = {wooden_bed: TEX + TRIM, wooden_wardrobe: TEX + TRIM_V, wooden_nightstand: TEX + TRIM_V, wooden_tool_rack: TEX + TRIM_V,
  wooden_storage_chest: TEX + TRIM, vintage_chest_freezer: TEX + ENAMEL};
/** Loot: the block itself (the bed only from its head, as vanilla; the wardrobe from its lower half, the chest and freezer from their master half). */
const loot = id => {
  const cond = [{condition: 'minecraft:survives_explosion'}];
  if (id === 'wooden_bed') cond.push({condition: 'minecraft:block_state_property', block: NS + id, properties: {part: 'head'}});
  if (id === 'vintage_chest_freezer' || id === 'wooden_storage_chest') cond.push({condition: 'minecraft:block_state_property', block: NS + id, properties: {part: 'left'}});
  if (id === 'wooden_wardrobe') cond.push({condition: 'minecraft:block_state_property', block: NS + id, properties: {half: 'lower'}});
  return json({type: 'minecraft:block', pools: [{rolls: 1, bonus_rolls: 0, entries: [{type: 'minecraft:item', name: NS + id}], conditions: cond}], random_sequence: `${NS}blocks/${id}`});
};

export function outputs() {
  const out = [];
  const texOut = (name, maps) => maps.forEach((m, i) => out.push([path.join(ASSETS, 'textures/block/furniture', name + ['', '_s', '_n'][i] + '.png'), m]));
  for (const [name, [make, height]] of Object.entries(FURNITURE_V2_TEXTURES)) texOut(name, renderX(make(), {height}));
  for (const pc of pieces()) out.push([path.join(ASSETS, 'cabin_mesh', pc.name + '.json'), JSON.stringify(serialise(pc, TEX)) + '\n']);
  for (const [id, make] of [['wooden_wardrobe', wardrobeShapes], ['wooden_nightstand', nightstandShapes], ['vintage_chest_freezer', freezerShapes], ['wooden_storage_chest', chestShapes]])
    out.push([path.join(DATA, 'apocalypse_firstlight/mesh_shapes', id + '.json'), make()]);
  const bm = id => `${NS}block/furniture/${id}`;
  for (const {id} of BLOCKS) {
    if (!MESH_IDS.includes(id)) {
      const model = {parent: 'minecraft:block/block', loader: NS + 'cabin', textures: {particle: PARTICLE[id]}, item: ITEMS[id]};
      if (DISPLAY[id]) model.display = DISPLAY[id];
      out.push([path.join(ASSETS, 'models/block/furniture', id + '.json'), json(model)]);
      out.push([path.join(ASSETS, 'models/item', id + '.json'), json({parent: bm(id)})]);
    }
    out.push([path.join(ASSETS, 'blockstates', id + '.json'), json({variants: {'': {model: bm(id)}}})]);
    out.push([path.join(DATA, 'apocalypse_firstlight/loot_tables/blocks', id + '.json'), loot(id)]);
  }
  for (const m of meshes()) out.push(...m.outputs);
  return out;
}

// =============================================================================================== audit
const readJson = p => JSON.parse(fs.readFileSync(path.join(ROOT, p), 'utf8'));
function audit() {
  const errs = [], tags = 'src/main/resources/data/minecraft/tags/blocks/';
  const tagHas = (file, id) => { try { return readJson(file).values.some(v => (typeof v === 'string' ? v : v.id) === NS + id); } catch { return false; } };
  for (const {id, tool, tier} of BLOCKS) {
    for (const t of ['axe', 'pickaxe']) if (tagHas(tags + `mineable/${t}.json`, id) !== (tool === t)) errs.push(`${id}: minecraft:mineable/${t} ${tool === t ? 'missing' : 'unexpected'}`);
    for (const t of ['needs_stone_tool', 'needs_iron_tool', 'needs_diamond_tool']) if (tagHas(tags + t + '.json', id) !== (tier === t)) errs.push(`${id}: minecraft:${t} ${tier === t ? 'missing' : 'unexpected'}`);
    if (tool === 'axe' && !tagHas('src/main/resources/data/apocalypse_firstlight/tags/blocks/noise_wood_blocks.json', id)) errs.push(`${id}: missing from noise_wood_blocks`);
  }
  const mass = readJson('src/main/resources/data/apocalypse_firstlight/item_mass/afl_content_v1.json').items ?? {};
  for (const b of BLOCKS) if (mass[NS + b.id]?.unit_mass_kg !== b.mass) errs.push(`${b.id}: item mass ${mass[NS + b.id]?.unit_mass_kg} (want ${b.mass})`);
  for (const b of BLOCKS) for (const c of ['bulky', 'oversized'])
    if (tagHas(`src/main/resources/data/apocalypse_firstlight/tags/items/carry/${c}.json`, b.id) !== (b.carry === c)) errs.push(`${b.id}: carry/${c} ${b.carry === c ? 'missing' : 'unexpected'}`);
  for (const lang of ['en_us', 'zh_cn']) {
    const l = readJson(`src/main/resources/assets/apocalypse_firstlight/lang/${lang}.json`);
    for (const b of BLOCKS) { const v = l['block.apocalypse_firstlight.' + b.id]; if (v !== (lang === 'en_us' ? b.en : b.zh)) errs.push(`${b.id}: lang ${lang} = ${v}`); }
  }
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
  } else if (process.argv.includes('--pieces')) {
    // pieces only (the preview): no textures, models or data
    for (const pc of pieces()) { const f = path.join(ASSETS, 'cabin_mesh', pc.name + '.json'); fs.mkdirSync(path.dirname(f), {recursive: true}); fs.writeFileSync(f, JSON.stringify(serialise(pc, TEX)) + '\n'); }
    console.log('wrote pieces');
  } else {
    for (const [file] of out) fs.mkdirSync(path.dirname(file), {recursive: true});
    for (const [file, data] of out) fs.writeFileSync(file, data);
    const pcs = pieces();
    console.log(`wrote ${out.length} files (${pcs.length} mesh pieces)`);
    for (const p of pcs) console.log('  ' + p.name, serialise(p, TEX).quads.length);
    for (const m of meshes()) console.log('  mesh ' + JSON.stringify(m.stats));
  }
}
