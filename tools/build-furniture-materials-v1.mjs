// AFL furniture materials V1 (docs/gameplay/furniture_recipes_v1.md, 2026-10-22): the first batch of the wooden furniture's
// materials as Pure Mesh items, by the Material Meshes bake (tools/build-material-meshes-v1.mjs: one bone, LabPBR atlas
// Base Color / _s / _n, a 64 px icon rendered from the model, the item model with the held displays).
//   <species>_board         a rip-sawn board of each AFL species, 120 x 20 x 2.5 cm, on the species' own timber photo
//   wood_finish_can         a quart can of walnut wood finish (1 L): tinplate, a plain paper label, wire bail
//   nail_box                a 1 lb carton of 6d common nails (180)
//   cabinet_hinge           a 3 inch butt hinge, opened flat, dark iron finish
//   iron_knob               a cast iron mushroom knob on its rosette, with the screw out the back
//   wooden_knob             a hand-carved wooden knob with a round tenon (made at the bench from a board)
//   keyhole_escutcheon      an oval iron keyhole plate with two nail holes
//   furniture_hardware_box  a taped corrugated carton of mixed cabinet hardware (opened for a random handful)
//   node tools/build-furniture-materials-v1.mjs [id ...]            -> writes source, atlas, geo, AFL mesh sidecar, icon, item model
//   node tools/build-furniture-materials-v1.mjs [id ...] --check    -> verifies every output is up to date
// Authored in cm at the felling axe's scale (1 px = 1.25 inch, like the hand saw and the book). Held displays follow
// docs/models/equipment_meshes_v1.md "手持适配规则": long and boxy things land their grip point on the claw hammer's
// (fitHeld); the small parts are held in the fist like the pebble, their lower side sunk into it and magnified a little
// so they show above the fingers (pinch). Plain materials, no printing (brands wait for the companies table).
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {Part, extrude, revolve, area2, png, readPng, add, sub, mul, cross, norm} from './cube-slab-mesh-lib.mjs';
import {bake} from './build-material-meshes-v1.mjs';
import {fitHeld, rotXYZ, meshCentre, vn3, fbm3} from './build-equipment-meshes-v1.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const WOOD = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/textures/block/wood');
const K = 0.8 / 2.54, cm = v => v * K;
const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
const circle = (cu, cv, r, n, phase = Math.PI / n) => Array.from({length: n}, (_, i) => [cu + r * Math.cos(phase + 2 * Math.PI * i / n), cv + r * Math.sin(phase + 2 * Math.PI * i / n)]);
function roundRect(u0, v0, u1, v1, r, seg = 3) {
  const out = [];
  for (const [cu, cv, a0] of [[u1 - r, v0 + r, -90], [u1 - r, v1 - r, 0], [u0 + r, v1 - r, 90], [u0 + r, v0 + r, 180]])
    for (let i = 0; i <= seg; i++) { const a = (a0 + 90 * i / seg) * Math.PI / 180; out.push([cu + r * Math.cos(a), cv + r * Math.sin(a)]); }
  return out;
}
/** revolve builds along +Z: turn the part so +Z becomes +Y (stands it up). */
const zToY = part => { part.v = part.v.map(([x, y, z]) => [x, z, -y]); };
const scaleCm = part => { part.v = part.v.map(q => q.map(cm)); };
const tone3 = (c, k) => c.map(v => v * k);
const r4 = v => +v.toFixed(4) || 0;

/** A round wire along a polyline (cm or px, as given), `sides` round, capped at both ends. */
function tube(part, pts, rad, sides = 6) {
  const rings = pts.map((p, i) => {
    const t = norm(sub(pts[Math.min(pts.length - 1, i + 1)], pts[Math.max(0, i - 1)]));
    const up = Math.abs(t[2]) < 0.9 ? [0, 0, 1] : [1, 0, 0], n1 = norm(cross(t, up)), n2 = cross(t, n1);
    return Array.from({length: sides}, (_, k) => { const a = 2 * Math.PI * k / sides; return part.vtx(add(p, add(mul(n1, rad * Math.cos(a)), mul(n2, rad * Math.sin(a))))); });
  });
  for (let i = 0; i + 1 < rings.length; i++) for (let k = 0; k < sides; k++) {
    const k1 = (k + 1) % sides, ids = [rings[i][k], rings[i][k1], rings[i + 1][k1], rings[i + 1][k]];
    part.face(ids, sub(mul(ids.map(j => part.v[j]).reduce(add, [0, 0, 0]), 1 / 4), mul(add(pts[i], pts[i + 1]), 0.5)));
  }
  part.face(rings[0].slice(), sub(pts[0], pts[1]), 'cap');
  part.face(rings[rings.length - 1].slice(), sub(pts[pts.length - 1], pts[pts.length - 2]), 'cap');
}

/**
 * Small parts held in the fist like the pebble (tools/boulders/build-field-stone.mjs held()): third person turned a little,
 * centred over the fist with its lower side sunk `depth` px into it (the wiring wrench's accepted grip is at y -0.9,
 * z 1.37 in the item frame); first person on the wrench's hand point, its lower side on it. Magnified (scale) so a 3 cm
 * part reads against the oversized hand. Returns the right-hand displays and, for the offline check, where the part's box
 * lands in each view (px).
 */
function pinch(PARTS, [tpTurn, tpScale, depth], [fpTurn, fpScale]) {
  const c = meshCentre(PARTS), all = PARTS.flatMap(p => p.v), display = {}, box = {};
  for (const [view, turn, scale, land] of [['thirdperson', tpTurn, tpScale, [0, -depth, 1.4]], ['firstperson', fpTurn, fpScale, [1.13, -0.46, 2.46]]]) {
    const P = all.map(q => rotXYZ(turn, sub(q, c)).map(v => v * scale));
    const lo = [0, 1, 2].map(k => Math.min(...P.map(q => q[k]))), hi = [0, 1, 2].map(k => Math.max(...P.map(q => q[k])));
    const t = [land[0] - (lo[0] + hi[0]) / 2, land[1] - lo[1], land[2] - (lo[2] + hi[2]) / 2].map(r4);
    display[view + '_righthand'] = {rotation: turn, translation: t, scale: [scale, scale, scale]};
    box[view] = {lo: lo.map((v, k) => r4(v + t[k])), hi: hi.map((v, k) => r4(v + t[k]))};
  }
  return {display, box};
}

/** Where a fitHeld grip puts two of the item's points in a view (px, item frame): the offline check of long items. */
function landed(PARTS, disp, view, pts) {
  const d = disp[view + '_righthand'], c = meshCentre(PARTS);
  return Object.fromEntries(Object.entries(pts).map(([k, q]) => [k, rotXYZ(d.rotation, sub(q, c)).map((v, i) => r4(v * d.scale[i] + d.translation[i]))]));
}

// ---------------------------------------------------------------- boards

/**
 * The six AFL species (docs/dev/wood_system_plan_v1.md). One log saws into 4 boards at the bench (user 2026-10-22); a board
 * is one long rip-sawn plank, not the planks block (木地板).
 */
export const SPECIES = ['white_oak', 'white_pine', 'red_oak', 'shagbark_hickory', 'red_maple', 'eastern_hemlock'];
export const BOARD = {L: 120, W: 20, T: 2.5, arris: 0.25, end: 0.1};
const TIMBER = 240;   // the timber photos' texels per metre (tools/build-wood-photo-v1.mjs), grain along v
const BOARD_ATLAS = 512;

/**
 * A rip-sawn board standing along +Y: the width on X, the thickness on Z (the broad faces toward +-Z), the long arrises
 * eased (the saw's burr knocked off), the ends square with a hair of chamfer. Box-projected onto the species' timber
 * photo at its own density (240 texels per metre): the broad faces and the edges on timber_side with the grain along the
 * board, the ends on timber_end; every face cut from a different place in the tile so the two faces are not twins.
 */
function board(species) {
  const B = species + '_board', L = cm(BOARD.L), W = cm(BOARD.W), T = cm(BOARD.T);
  const p = new Part('board', B, 'wood');
  extrude(p, 'y', {outer: orient(roundRect(-T / 2, -W / 2, T / 2, W / 2, cm(BOARD.arris), 1), true), holes: []}, 0, L, cm(BOARD.end));

  const S = TIMBER / 100 / K;   // texels per px
  const lo = [0, 1, 2].map(k => Math.min(...p.v.map(q => q[k]))), hi = [0, 1, 2].map(k => Math.max(...p.v.map(q => q[k])));
  const wT = Math.ceil(W * S), lT = Math.ceil(L * S), tT = Math.ceil(T * S);
  // regions: [x, y] in the atlas, the source tile and where in it the region is cut from
  const R = {front: [4, 4, 'side', 0, 0], back: [64, 4, 'side', 120, 70], right: [124, 4, 'side', 60, 150], left: [140, 4, 'side', 196, 30],
    top: [156, 4, 'end', 40, 60], bottom: [156, 24, 'end', 150, 130]};
  const faceUV = new Map([[p, new Map()]]);
  for (const f of p.f) {
    const P = f.ids.map(i => p.v[i]), n = cross(sub(P[1], P[0]), sub(P[2], P[0])), a = n.map(Math.abs), k = a.indexOf(Math.max(...a));
    const reg = k === 2 ? (n[2] > 0 ? 'front' : 'back') : k === 0 ? (n[0] > 0 ? 'right' : 'left') : (n[1] > 0 ? 'top' : 'bottom');
    const [rx, ry] = R[reg];
    faceUV.get(p).set(f, P.map(q => k === 2 ? [rx + (q[0] - lo[0]) * S, ry + (hi[1] - q[1]) * S]
      : k === 0 ? [rx + (q[2] - lo[2]) * S, ry + (hi[1] - q[1]) * S] : [rx + (q[0] - lo[0]) * S, ry + (q[2] - lo[2]) * S]));
  }
  const size = {front: [wT, lT], back: [wT, lT], right: [tT, lT], left: [tT, lT], top: [wT, tT], bottom: [wT, tT]};
  const textures = ['', '_s', '_n'].map(x => {
    const read = name => { const img = readPng(fs.readFileSync(path.join(WOOD, `${species}_timber_${name}${x}.png`)));
      if (img.w !== TIMBER || img.h !== TIMBER || img.bpp !== 4) throw new Error('timber photo format ' + species + name + x); return img; };
    const src = {side: read('side'), end: read('end')}, A = BOARD_ATLAS, out = Buffer.alloc(A * A * 4);
    const copy = (img, sx, sy, x0, y0, w, h) => {
      for (let y = y0; y < y0 + h; y++) for (let x = x0; x < x0 + w; x++) {
        const u = ((sx + x - x0) % TIMBER + TIMBER) % TIMBER, v = ((sy + y - y0) % TIMBER + TIMBER) % TIMBER, s = (v * TIMBER + u) * 4, d = (y * A + x) * 4;
        for (let ch = 0; ch < 4; ch++) out[d + ch] = img.px[s + ch];
      }
    };
    copy(src.side, 0, 0, 0, 0, A, A);   // the spare atlas: plain side grain (never sampled; keeps mips and _n sane)
    for (const [reg, [rx, ry, tile, sx, sy]] of Object.entries(R)) { const [w, h] = size[reg]; copy(src[tile], sx - 3, sy - 3, rx - 3, ry - 3, w + 6, h + 6); }
    return png(out, A, A);
  });
  const PARTS = [p];
  // Third person: carried at the side by its top edge, the board level along the walk (Ry -90 puts its +X edge up, Rz 15
  // takes back most of the arm's lift), the fist round the edge 2.5 cm in from it at the middle. First person: the same
  // grip a fifth of the way along, the board running forward and a little toward the screen's middle, its edge up.
  const tpGrip = [W / 2 - cm(2.5), L / 2, 0], fpGrip = [W / 2 - cm(3), L * 0.2, 0];
  const display = {
    ...fitHeld(PARTS, fpGrip, 'firstperson', [0, -80, 90], 0.6),
    ...fitHeld(PARTS, tpGrip, 'thirdperson', [0, -90, 15], 0.7),
    ground: {rotation: [90, 0, 0], translation: [0, 1, 0], scale: [0.4, 0.4, 0.4]},
    fixed: {rotation: [0, 0, -45], translation: [0, 0, 0], scale: [0.35, 0.35, 0.35]},
  };
  const ends = {near: [0, 0, 0], far: [0, L, 0], lowEdge: [-W / 2, L / 2, 0]};
  return {PARTS, MATS: {wood: {c: [150, 120, 90], hl: 0, sm: 60, se: 60, f0: 20}}, textures, atlas: BOARD_ATLAS,
    uv: {faceUV, S, islands: [], uvOf: null}, display,
    icon: {view: [20, 30, -45]}, check: {thirdperson: landed(PARTS, display, 'thirdperson', ends), firstperson: landed(PARTS, display, 'firstperson', ends)}};
}

// ---------------------------------------------------------------- the can, the nails, the box

/**
 * Walnut wood finish, a quart (946 mL, sold as the 1 L the recipes count): a tinplate paint can 10.8 cm across and
 * 12.1 cm tall standing on +Y: the rolled bottom seam, the plain body, the double-seamed chime at the top round the
 * friction lid (its plug bead and the groove a screwdriver opens), the bottom recessed; a plain dark brown paper label
 * round the body (no printing); two ears near the top and the wire bail over the lid, upright.
 */
export const CAN = {R: 5.4, H: 12.1, ear: 10.3, bail: 0.13};
function woodFinishCan() {
  const B = 'wood_finish_can', {R, H} = CAN;
  const can = new Part('can', B, 'tin');
  revolve(can, 0, 0, [[H - 0.25, 0], [H - 0.25, R - 1.0], [H - 0.05, R - 0.85], [H - 0.05, R - 0.7], [H - 0.4, R - 0.55], [H - 0.4, R - 0.4],
    [H, R - 0.25], [H, R + 0.05], [H - 0.5, R + 0.1], [H - 0.6, R], [0.6, R], [0.5, R + 0.1], [0, R + 0.05], [0, R - 0.25], [0.25, R - 0.4], [0.25, 0]], 24);
  const label = new Part('label', B, 'label');
  revolve(label, 0, 0, [[H - 1.4, R + 0.04], [1.1, R + 0.04], [1.1, R - 0.05], [H - 1.4, R - 0.05]], 24);
  for (const q of [can, label]) zToY(q);
  // the ears: pressed tabs spot-welded to the body either side, each with the bail's end through it
  const ears = [1, -1].map((s, i) => {
    const e = new Part('ear_' + i, B, 'tin');
    extrude(e, 'x', {outer: orient(roundRect(-0.8, CAN.ear - 0.9, 0.8, CAN.ear + 0.9, 0.35, 2), true), holes: []}, s > 0 ? R - 0.1 : -R - 0.15, s > 0 ? R + 0.15 : -R + 0.1, 0);
    return e;
  });
  // the bail: in through the right ear, up over the lid in a tall arch, out through the left ear
  const bail = new Part('bail', B, 'wire'), Rb = R + 0.35, pts = [[R - 0.05, CAN.ear, 0], [Rb, CAN.ear, 0]];
  for (let i = 1; i < 12; i++) { const a = Math.PI * i / 12; pts.push([Rb * Math.cos(a), CAN.ear + 5.6 * Math.sin(a), 0]); }
  pts.push([-Rb, CAN.ear, 0], [-R + 0.05, CAN.ear, 0]);
  tube(bail, pts, CAN.bail, 6);
  const PARTS = [can, label, ...ears, bail];
  for (const q of PARTS) scaleCm(q);
  const top = [0, cm(CAN.ear + 5.6), 0];
  // Third person: carried by the bail, the can hanging under the fist (Rx 90 keeps +Y up, Ry 90 lays the bail along the
  // fist). First person: held round the body, the label toward the camera, the fist on its right side.
  const display = {
    ...fitHeld(PARTS, [cm(R * 0.8), cm(H * 0.45), 0], 'firstperson', [0, -30, 0], 1.2),
    ...fitHeld(PARTS, top, 'thirdperson', [90, 90, 0], 1.0),
  };
  return {PARTS, MATS: {
    tin: {c: [176, 178, 180], hl: 8, sm: 150, se: 166, f0: 230},       // tinplate, satin
    label: {c: [86, 54, 34], hl: 3, sm: 70, se: 78, f0: 20},           // the walnut-brown paper label, plain
    wire: {c: [150, 150, 152], hl: 6, sm: 130, se: 150, f0: 230},      // galvanised bail wire
  }, display, icon: {view: [25, 35, 0]},
  check: {thirdperson: landed(PARTS, display, 'thirdperson', {bailTop: top, bottom: [0, 0, 0]})}};
}

/**
 * A 1 lb box of 6d common nails: a kraft paperboard tuck-top carton 6.5 x 11 x 4.5 cm standing on +Y, its edges softened,
 * the top panel a hair larger than the body where it folds over, a plain off-white label on the front (no printing).
 */
export const NAILS = {W: 6.5, H: 11, D: 4.5};
function nailBox() {
  const B = 'nail_box', {W, H, D} = NAILS;
  const body = new Part('body', B, 'kraft');
  extrude(body, 'y', {outer: orient(roundRect(-D / 2, -W / 2, D / 2, W / 2, 0.15, 2), true), holes: []}, 0, H - 0.2, 0.08);
  const lid = new Part('lid', B, 'kraft');
  extrude(lid, 'y', {outer: orient(roundRect(-D / 2 - 0.04, -W / 2 - 0.04, D / 2 + 0.04, W / 2 + 0.04, 0.15, 2), true), holes: []}, H - 0.32, H, 0);
  // the tuck flap's edge showing down the front under the lid
  const flap = new Part('flap', B, 'kraft');
  extrude(flap, 'z', {outer: orient([[-W / 2 + 0.3, H - 0.32], [W / 2 - 0.3, H - 0.32], [W / 2 - 0.3, H - 1.3], [-W / 2 + 0.3, H - 1.3]], true), holes: []}, D / 2 - 0.02, D / 2 + 0.03, 0);
  const label = new Part('label', B, 'label');
  extrude(label, 'z', {outer: orient(roundRect(-W / 2 + 0.5, 2.2, W / 2 - 0.5, 7.2, 0.2, 2), true), holes: []}, D / 2 - 0.02, D / 2 + 0.04, 0);
  const PARTS = [body, lid, flap, label];
  for (const q of PARTS) scaleCm(q);
  const {display, box} = pinch(PARTS, [[20, 35, -10], 1.3, 1.0], [[-8, 30, 6], 1.2]);
  return {PARTS, MATS: {
    kraft: {c: [170, 128, 86], hl: 3, sm: 46, se: 52, f0: 20},          // kraft paperboard, matte
    label: {c: [226, 218, 196], hl: 2, sm: 52, se: 58, f0: 20},         // the plain paper label
  }, display, icon: {view: [20, 35, 0]}, check: box};
}

/**
 * Furniture hardware box (opened for a random handful, loot table gameplay/furniture_hardware_box): a corrugated carton
 * 20 x 8 x 14 cm lying flat, its two top flaps meeting down the middle and taped shut, the tape running down both ends,
 * a plain white label on the front (no printing).
 */
export const HWBOX = {L: 20, H: 8, D: 14, tape: 4.8};
function hardwareBox() {
  const B = 'furniture_hardware_box', {L, H, D} = HWBOX, t = HWBOX.tape / 2;
  const body = new Part('body', B, 'board');
  extrude(body, 'y', {outer: orient(roundRect(-D / 2, -L / 2, D / 2, L / 2, 0.25, 2), true), holes: []}, 0, H - 0.1, 0.1);
  // the two top flaps, a 1 mm gap where they meet
  const flaps = [1, -1].map((s, i) => {
    const f = new Part('flap_' + i, B, 'board');
    const z0 = s > 0 ? 0.05 : -D / 2 + 0.02, z1 = s > 0 ? D / 2 - 0.02 : -0.05;
    extrude(f, 'y', {outer: orient([[z0, -L / 2 + 0.02], [z1, -L / 2 + 0.02], [z1, L / 2 - 0.02], [z0, L / 2 - 0.02]], true), holes: []}, H - 0.3, H, 0);
    return f;
  });
  // the tape: across the top over the seam and down both ends
  const tape = new Part('tape', B, 'tape');
  extrude(tape, 'y', {outer: orient([[-t, -L / 2 - 0.04], [t, -L / 2 - 0.04], [t, L / 2 + 0.04], [-t, L / 2 + 0.04]], true), holes: []}, H - 0.01, H + 0.04, 0);
  for (const s of [1, -1]) extrude(tape, 'x', {outer: orient([[-t, H - 5], [t, H - 5], [t, H + 0.04], [-t, H + 0.04]], true), holes: []},
    s > 0 ? L / 2 - 0.01 : -L / 2 - 0.04, s > 0 ? L / 2 + 0.04 : -L / 2 + 0.01, 0);
  const label = new Part('label', B, 'label');
  extrude(label, 'z', {outer: orient(roundRect(-L / 2 + 2, 1.5, -L / 2 + 9, 5.5, 0.2, 2), true), holes: []}, D / 2 - 0.02, D / 2 + 0.04, 0);
  const PARTS = [body, ...flaps, tape, label];
  for (const q of PARTS) scaleCm(q);
  // Third person: carried flat-side down by the top edge nearest the body, the box hanging outward of the fist (Rx 90
  // keeps +Y up, Ry 90 lays the length along the walk). First person: held up on the right, its right end in the fist.
  const display = {
    ...fitHeld(PARTS, [cm(L / 2 - 3), cm(H * 0.5), 0], 'firstperson', [0, -20, 0], 0.9),
    ...fitHeld(PARTS, [0, cm(H - 1), cm(-D / 2 + 1)], 'thirdperson', [90, 90, 0], 0.8),
  };
  return {PARTS, MATS: {
    board: {c: [168, 126, 82], hl: 3, sm: 44, se: 50, f0: 20},          // corrugated board, matte
    tape: {c: [190, 152, 98], hl: 4, sm: 150, se: 160, f0: 20},         // tan packing tape, glossy
    label: {c: [232, 228, 218], hl: 2, sm: 56, se: 60, f0: 20},
  }, display, icon: {view: [25, 35, 0]},
  check: {thirdperson: landed(PARTS, display, 'thirdperson', {nearTop: [0, cm(H), cm(-D / 2)], farBottom: [0, 0, cm(D / 2)]})}};
}

// ---------------------------------------------------------------- the small parts

const IRON = {c: [66, 62, 58], hl: 6, sm: 112, se: 128, f0: 230};   // dark iron finish (oil-rubbed), satin

/**
 * Cabinet hinge: a 3 x 2 1/2 inch butt hinge opened flat (7.6 cm long, 6.35 cm across), 2 mm leaves with three
 * countersunk screw holes each, five knuckles on the pin along +Y, the pin's ball tips proud of the end knuckles.
 */
export const HINGE = {L: 7.6, W: 6.35, t: 0.2, knuckle: 0.32, gap: 0.04};
function cabinetHinge() {
  const B = 'cabinet_hinge', {L, W, t, knuckle: kr, gap} = HINGE;
  const leaves = [1, -1].map((s, i) => {
    const leaf = new Part('leaf_' + i, B, 'iron'), x0 = 0.15, x1 = W / 2;
    const outer = s > 0 ? roundRect(x0, 0, x1, L, 0.3, 2) : roundRect(-x1, 0, -x0, L, 0.3, 2);
    const holes = [1.3, L / 2, L - 1.3].map(y => orient(circle(s * 2.0, y, 0.24, 10), false));
    extrude(leaf, 'z', {outer: orient(outer, true), holes}, -t / 2, t / 2, 0);
    return leaf;
  });
  const knuckles = Array.from({length: 5}, (_, i) => {
    const k = new Part('knuckle_' + i, B, 'iron'), y0 = i * L / 5 + (i ? gap / 2 : 0), y1 = (i + 1) * L / 5 - (i < 4 ? gap / 2 : 0);
    revolve(k, 0, 0, [[y1, 0], [y1, kr - 0.04], [y1 - 0.04, kr], [y0 + 0.04, kr], [y0, kr - 0.04], [y0, 0]], 12);
    zToY(k);
    return k;
  });
  const pin = new Part('pin', B, 'iron');
  for (const [a, b] of [[L, L + 0.25], [-0.25, 0]]) {
    const top = a === L;
    revolve(pin, 0, 0, top ? [[b, 0], [b - 0.05, 0.14], [a + 0.08, 0.2], [a + 0.01, 0.17], [a + 0.01, 0]] : [[b - 0.01, 0], [b - 0.01, 0.17], [a + 0.17, 0.2], [a + 0.05, 0.14], [a, 0]], 10);
  }
  zToY(pin);
  const PARTS = [...leaves, ...knuckles, pin];
  for (const q of PARTS) scaleCm(q);
  const {display, box} = pinch(PARTS, [[20, 35, -10], 2.0, 0.3], [[-8, 30, 6], 1.8]);
  return {PARTS, MATS: {iron: IRON}, display, icon: {view: [35, 20, 0], coverage: 0.35}, check: box};
}

/** Iron knob: a cast iron mushroom knob 3.2 cm across on a round rosette, its machine screw out the back (+Z is the face). */
function ironKnob() {
  const B = 'iron_knob';
  const knob = new Part('knob', B, 'iron');
  revolve(knob, 0, 0, [[2.8, 0], [2.78, 0.6], [2.7, 1.1], [2.5, 1.5], [2.2, 1.6], [1.9, 1.55], [1.6, 1.2], [1.2, 0.65], [0.7, 0.6],
    [0.45, 0.85], [0.3, 1.25], [0.12, 1.3], [0, 1.25], [0, 0]], 16);
  const screw = new Part('screw', B, 'zinc');
  revolve(screw, 0, 0, [[0.05, 0], [0.05, 0.24], [-2.4, 0.24], [-2.5, 0.14], [-2.5, 0]], 8);
  const PARTS = [knob, screw];
  for (const q of PARTS) { zToY(q); scaleCm(q); }
  const {display, box} = pinch(PARTS, [[20, 35, -10], 2.2, 0.3], [[-8, 30, 6], 2.0]);
  return {PARTS, MATS: {iron: IRON, zinc: {c: [158, 160, 158], hl: 6, sm: 140, se: 150, f0: 230}}, display, icon: {view: [20, 35, 0], coverage: 0.35}, check: box};
}

/**
 * Wooden knob: carved by hand from a board offcut at the bench, so its round is not turned true (eight flat facets, each
 * a little off) and it keeps a round tenon at the back to glue into the drawer front. Bare wood, no finish; the grain runs
 * across the knob (the board's length).
 */
function woodenKnob() {
  const B = 'wooden_knob';
  const knob = new Part('knob', B, 'wood');
  revolve(knob, 0, 0, [[2.6, 0], [2.55, 0.7], [2.35, 1.25], [2.0, 1.5], [1.6, 1.4], [1.2, 0.9], [0.8, 0.75], [0.4, 0.9], [0, 0.95],
    [0, 0.5], [-1.5, 0.5], [-1.5, 0]], 8);
  // the carver's hand: each facet's ring a little off round (fixed, seeded by position)
  knob.v = knob.v.map(([x, y, z]) => { const r = Math.hypot(x, y); if (r < 1e-6) return [x, y, z];
    const k = 1 + 0.06 * (vn3(x * 3.1 + 1.7, y * 3.1 + 4.2, z * 0.7 + 2.9) - 0.5); return [x * k, y * k, z]; });
  zToY(knob); scaleCm(knob);
  const PARTS = [knob];
  const wood = pos => tone3([196, 156, 108], 1 + 0.16 * (fbm3(pos[0] * 0.6 + 2.1, pos[1] * 5.5, pos[2] * 5.5) - 0.5) + 0.05 * (vn3(pos[0] * 2 + 5.3, pos[1] * 14, pos[2] * 14) - 0.5));
  const {display, box} = pinch(PARTS, [[20, 35, -10], 2.2, 0.3], [[-8, 30, 6], 2.0]);
  return {PARTS, bg: 'plate', MATS: {plate: {c: [196, 156, 108], hl: 4, sm: 56, se: 62, f0: 20}, wood: {c: wood, hl: 4, sm: 56, se: 62, f0: 20}}, display, icon: {view: [20, 35, 0], coverage: 0.35}, check: box};
}

/**
 * The wooden knob at the bench (docs/gameplay/furniture_recipes_v1.md 4.1, tools/author-wooden-knob-actions.mjs): a mesh
 * drawn by code only, no item. The strip on the bench hook (`stick`, lying along +X) with its end that is sawn off
 * (`blank_end`, x 2.8..7 cm), and the blank in the hand at each stage of the whittling, standing along +Y from the tenon's
 * end: `blank` (sawn square 3.2 cm), `octagon` (the corners off), `rough` (rounded, no neck), `necked` (the head and the
 * neck cut, the foot still thick), `finished` (the knob itself: woodenKnob's profile and carver's hand). The parts share
 * one frame: the stick is symmetric about x = 0 and every part's foot is at y = -1.5 cm, so the bake's shift is
 * (0, 1.5 cm, ~0).
 */
export const KNOB_WORK = {half: 1.6, foot: -1.5, top: 2.7, stick: 7, cut: 2.8};
function woodenKnobWork() {
  const B = 'wooden_knob_work', {half: H, foot: F, top: T, stick: S, cut: C} = KNOB_WORK;
  const carver = (part, amount) => { part.v = part.v.map(([x, y, z]) => { const r = Math.hypot(x, y); if (r < 1e-6) return [x, y, z];
    const k = 1 + amount * (vn3(x * 3.1 + 1.7, y * 3.1 + 4.2, z * 0.7 + 2.9) - 0.5); return [x * k, y * k, z]; }); };
  const stage = (name, profile, seg, amount) => {
    const p = new Part(name, B, 'wood');
    revolve(p, 0, 0, profile, seg, Math.PI / seg);
    if (amount) carver(p, amount);
    zToY(p);
    return p;
  };
  const sq = H * Math.SQRT2, oct = H / Math.cos(Math.PI / 8);
  const blank = stage('blank', [[T, 0], [T, sq], [F, sq], [F, 0]], 4, 0);
  const octagon = stage('octagon', [[T, 0], [T, oct * 0.9], [T - 0.1, oct], [F, oct], [F, 0]], 8, 0.03);
  const rough = stage('rough', [[T - 0.05, 0], [T - 0.1, 0.8], [2.4, 1.35], [2.0, 1.6], [0.6, 1.62], [F, 1.62], [F, 0]], 8, 0.06);
  const necked = stage('necked', [[2.6, 0], [2.55, 0.7], [2.35, 1.25], [2.0, 1.5], [1.6, 1.4], [1.2, 0.9], [0.8, 0.75], [0.4, 0.9], [0.15, 1.3],
    [F, 1.3], [F, 0]], 8, 0.06);
  const finished = stage('finished', [[2.6, 0], [2.55, 0.7], [2.35, 1.25], [2.0, 1.5], [1.6, 1.4], [1.2, 0.9], [0.8, 0.75], [0.4, 0.9], [0, 0.95],
    [0, 0.5], [F, 0.5], [F, 0]], 8, 0.06);
  // the strip: a square bar along +X (built along +Z, then turned), its foot on y = F like the stages
  const bar = (name, z0, z1) => {
    const p = new Part(name, B, 'wood');
    revolve(p, 0, 0, [[z1, 0], [z1, sq], [z0, sq], [z0, 0]], 4, Math.PI / 4);
    p.v = p.v.map(([x, y, z]) => [z, y + H + F, -x]);
    return p;
  };
  const PARTS = [blank, octagon, rough, necked, finished, bar('stick', -S, C), bar('blank_end', C, S)];
  for (const q of PARTS) scaleCm(q);
  const wood = pos => tone3([196, 156, 108], 1 + 0.16 * (fbm3(pos[0] * 0.6 + 2.1, pos[1] * 5.5, pos[2] * 5.5) - 0.5) + 0.05 * (vn3(pos[0] * 2 + 5.3, pos[1] * 14, pos[2] * 14) - 0.5));
  return {PARTS, bg: 'plate', MATS: {plate: {c: [196, 156, 108], hl: 4, sm: 56, se: 62, f0: 20}, wood: {c: wood, hl: 4, sm: 56, se: 62, f0: 20}}, meshOnly: true};
}

/** Keyhole escutcheon: an oval iron plate 2.8 x 4.5 cm, 1.5 mm thick, the keyhole (a round head and a tapered slot) through it, a nail hole above and below. */
function keyholeEscutcheon() {
  const B = 'keyhole_escutcheon', rk = 0.42, cy = 0.5, w0 = 0.16, w1 = 0.2, yb = -0.9;
  const plate = new Part('plate', B, 'iron');
  const oval = Array.from({length: 28}, (_, i) => { const a = 2 * Math.PI * i / 28; return [1.4 * Math.cos(a), 2.25 * Math.sin(a)]; });
  const ys = cy - Math.sqrt(rk * rk - w0 * w0), a0 = Math.atan2(ys - cy, w0), a1 = Math.PI - a0, key = [];
  for (let i = 0; i <= 14; i++) { const a = a0 + (a1 - a0) * i / 14; key.push([rk * Math.cos(a), cy + rk * Math.sin(a)]); }
  key.push([-w1, yb], [w1, yb]);
  const nails = [1.85, -1.85].map(y => orient(circle(0, y, 0.1, 8), false));
  extrude(plate, 'z', {outer: orient(oval, true), holes: [orient(key, false), ...nails]}, 0, 0.15, 0);
  scaleCm(plate);
  const PARTS = [plate];
  const {display, box} = pinch(PARTS, [[20, 35, -10], 2.4, 0.3], [[-8, 30, 6], 2.0]);
  return {PARTS, MATS: {iron: IRON}, display, icon: {view: [15, 20, 0], coverage: 0.35}, check: box};
}

/**
 * A box of 100 #8 x 1 1/4 inch flat-head wood screws (docs/gameplay/furniture_bench_work_v1.md 3.5): a small kraft
 * tuck-top carton 5.5 x 8 x 3.5 cm standing on +Y, the lid a hair larger where it folds over, a plain off-white label
 * (no printing). Parts as the nail box (body, lid, flap, label): the bench draws it open without its lid.
 */
export const SCREWS = {W: 5.5, H: 8, D: 3.5};
function screwBox() {
  const B = 'screw_box', {W, H, D} = SCREWS;
  const body = new Part('body', B, 'kraft');
  extrude(body, 'y', {outer: orient(roundRect(-D / 2, -W / 2, D / 2, W / 2, 0.12, 2), true), holes: []}, 0, H - 0.2, 0.06);
  const lid = new Part('lid', B, 'kraft');
  extrude(lid, 'y', {outer: orient(roundRect(-D / 2 - 0.04, -W / 2 - 0.04, D / 2 + 0.04, W / 2 + 0.04, 0.12, 2), true), holes: []}, H - 0.3, H, 0);
  const flap = new Part('flap', B, 'kraft');
  extrude(flap, 'z', {outer: orient([[-W / 2 + 0.3, H - 0.3], [W / 2 - 0.3, H - 0.3], [W / 2 - 0.3, H - 1.1], [-W / 2 + 0.3, H - 1.1]], true), holes: []}, D / 2 - 0.02, D / 2 + 0.03, 0);
  const label = new Part('label', B, 'label');
  extrude(label, 'z', {outer: orient(roundRect(-W / 2 + 0.45, 1.6, W / 2 - 0.45, 5.4, 0.2, 2), true), holes: []}, D / 2 - 0.02, D / 2 + 0.04, 0);
  const PARTS = [body, lid, flap, label];
  for (const q of PARTS) scaleCm(q);
  const {display, box} = pinch(PARTS, [[20, 35, -10], 1.4, 1.0], [[-8, 30, 6], 1.3]);
  return {PARTS, MATS: {
    kraft: {c: [150, 118, 84], hl: 3, sm: 46, se: 52, f0: 20},          // a darker kraft than the nails' box
    label: {c: [222, 216, 200], hl: 2, sm: 52, se: 58, f0: 20},
  }, display, icon: {view: [20, 35, 0]}, check: box};
}

/**
 * The furniture work at the bench (docs/gameplay/furniture_bench_work_v1.md 3, tools/author-furniture-actions.mjs,
 * client/WorkbenchRenderer): a mesh drawn by code only, no item. Every part centred on x = z = 0 with its foot on y = 0
 * (the offcut's 28 x 20 cm bounds the whole mesh symmetrically, so the bake does not move them), cm:
 *   nail        a 6d common nail standing on its point, 5.1 cm, the head 0.67 cm across
 *   screw       a #8 x 1 1/4 inch flat-head wood screw standing on its point, screw_slot across its head along x
 *   brush_*     a 50 mm flat stain brush standing on its bristles (doc 12.2), 20 cm: brush_wet the lower 3.5 cm of the
 *               bristles dark with stain, brush_bristle the dry rest, brush_ferrule the tinplate band, brush_handle beech
 *   stick       the stir stick, a strip of board 26 x 2.4 x 1.2 cm standing up, stick_wet its lower 9 cm
 *   offcut      a board crosscut to 28 cm (20 x 2.5 cm in section), lying flat, the length along x
 *   lid         the quart can's lid, the plug ring down, the stain wet inside the plug
 *   can_open    the wood finish can without its lid; can_label, can_stain (the stain 2.3 cm under the rim),
 *               can_bail (folded down round the -z side), can_ear_0/1
 *   nail_heap   nails lying in the opened nail box (inside its 6.5 x 4.5 cm), screw_heap in the screw box (5.5 x 3.5)
 */
export const WORK = {nail: 5.08, nailHead: 0.335, nailShank: 0.144, screw: 3.18, screwHead: 0.42, stick: [2.4, 26, 1.2],
  stickWet: 9, offcut: [28, 2.5, 20], lidR: 5.15, stainDrop: 2.3,
  brush: {width: 5.0, wet: 3.5, bristle: 5.5, ferrule: 8.4, ferruleW: 5.4, ferruleT: 1.9, length: 20, handleW: 3.0, handleT: 1.5}};
function furnitureWork() {
  const B = 'furniture_work', {R, H} = CAN;
  const nail = new Part('nail', B, 'nail');
  revolve(nail, 0, 0, [[WORK.nail, 0], [WORK.nail, WORK.nailHead], [WORK.nail - 0.08, WORK.nailHead], [WORK.nail - 0.14, 0.15], [0.55, WORK.nailShank], [0, 0]], 8);
  zToY(nail);
  // the screw: a countersunk head over a smooth shank, the thread as alternating rings down to the point
  const S = WORK.screw, screw = new Part('screw', B, 'zinc'), profile = [[S, 0], [S, WORK.screwHead], [S - 0.05, WORK.screwHead], [S - 0.42, 0.21], [S - 1.0, 0.21]];
  for (let y = S - 1.1, k = 0; y > 0.5; y -= 0.16, k++) profile.push([y, k % 2 ? 0.2 : 0.15]);
  profile.push([0.4, 0.14], [0, 0]);
  revolve(screw, 0, 0, profile, 10);
  zToY(screw);
  const slot = new Part('screw_slot', B, 'slot');
  extrude(slot, 'y', {outer: orient([[-0.05, -WORK.screwHead + 0.03], [0.05, -WORK.screwHead + 0.03], [0.05, WORK.screwHead - 0.03], [-0.05, WORK.screwHead - 0.03]], true), holes: []}, S - 0.002, S + 0.012, 0);
  // the brush: the bristles a flat block thinning to the tip, the lower part wet; the ferrule a rounded band over their root;
  // the handle a flat paddle, a little fuller in its middle, its end rounded off
  const BR = WORK.brush, slab = (name, mat, w, t, y0, y1, r, ch) => {
    const p = new Part(name, B, mat);
    extrude(p, 'y', {outer: orient(roundRect(-t / 2, -w / 2, t / 2, w / 2, r, 2), true), holes: []}, y0, y1, ch);
    return p;
  };
  const brushWet = slab('brush_wet', 'stain', BR.width - 0.1, 1.15, 0, BR.wet + 0.02, 0.2, 0.05);
  brushWet.v = brushWet.v.map(([x, y, z]) => [x, y, z * (0.5 + 0.5 * Math.min(1, y / BR.wet))]);
  const brushBristle = slab('brush_bristle', 'bristle', BR.width, 1.25, BR.wet, BR.bristle + 0.2, 0.2, 0);
  const brushFerrule = slab('brush_ferrule', 'tin', BR.ferruleW, BR.ferruleT, BR.bristle, BR.ferrule, 0.6, 0.15);
  const brushHandle = slab('brush_handle', 'beech', BR.handleW, BR.handleT, BR.ferrule, BR.length, 0.5, 0.4);
  brushHandle.v = brushHandle.v.map(([x, y, z]) => { const s = (y - BR.ferrule) / (BR.length - BR.ferrule);
    return [x * (1 + 0.12 * Math.sin(Math.PI * Math.min(1, s * 1.3)) - 0.15 * Math.max(0, s - 0.85) / 0.15), y, z]; });
  const [sw, sh, sd] = WORK.stick;
  const stick = new Part('stick', B, 'pine');
  extrude(stick, 'y', {outer: orient(roundRect(-sd / 2, -sw / 2, sd / 2, sw / 2, 0.1, 1), true), holes: []}, WORK.stickWet, sh, 0.05);
  const stickWet = new Part('stick_wet', B, 'stain');
  extrude(stickWet, 'y', {outer: orient(roundRect(-sd / 2 - 0.02, -sw / 2 - 0.02, sd / 2 + 0.02, sw / 2 + 0.02, 0.1, 1), true), holes: []}, 0, WORK.stickWet + 0.02, 0.05);
  const [ol, ot, ow] = WORK.offcut;
  const offcut = new Part('offcut', B, 'pine');
  extrude(offcut, 'y', {outer: orient(roundRect(-ow / 2, -ol / 2, ow / 2, ol / 2, 0.15, 1), true), holes: []}, 0, ot, 0.08);
  for (const f of offcut.f) if (Math.abs(newellOf(offcut, f)[0]) > 0.9) f.mat = 'endgrain';
  // the lid: tin, the plug ring down (y 0..0.45), the flat top over it; its underside inside the plug wet with stain
  const lid = new Part('lid', B, 'tin'), Lr = WORK.lidR;
  revolve(lid, 0, 0, [[0.72, 0], [0.72, Lr], [0.6, Lr + 0.05], [0.45, Lr], [0.05, Lr - 0.25], [0, Lr - 0.45], [0.45, Lr - 0.6], [0.45, 0]], 24);
  zToY(lid);
  for (const f of lid.f) if (f.ids.every(i => lid.v[i][1] < 0.5 && Math.hypot(lid.v[i][0], lid.v[i][2]) < Lr - 0.55)) f.mat = 'stain';
  // the open can: woodFinishCan's body with its inside wall down to the bottom instead of the lid
  const canOpen = new Part('can_open', B, 'tin'), Ri = R - 0.55;
  revolve(canOpen, 0, 0, [[0.35, 0], [0.35, Ri], [H - 0.4, Ri], [H - 0.4, R - 0.4], [H, R - 0.25], [H, R + 0.05], [H - 0.5, R + 0.1], [H - 0.6, R],
    [0.6, R], [0.5, R + 0.1], [0, R + 0.05], [0, R - 0.25], [0.25, R - 0.4], [0.25, 0]], 24);
  zToY(canOpen);
  const canLabel = new Part('can_label', B, 'label');
  revolve(canLabel, 0, 0, [[H - 1.4, R + 0.04], [1.1, R + 0.04], [1.1, R - 0.05], [H - 1.4, R - 0.05]], 24);
  zToY(canLabel);
  const canStain = new Part('can_stain', B, 'stain'), sy = H - WORK.stainDrop;
  revolve(canStain, 0, 0, [[sy, 0], [sy, Ri - 0.01], [sy - 0.1, Ri - 0.01], [sy - 0.1, 0]], 24);
  zToY(canStain);
  // the bail folded down: out of the right ear, down round the -z side of the body and up into the left ear
  const bail = new Part('can_bail', B, 'wire'), Rb = R + 0.35, pts = [[R - 0.05, CAN.ear, 0], [Rb, CAN.ear, 0]];
  for (let i = 1; i < 12; i++) { const a = Math.PI * i / 12; pts.push([Rb * Math.cos(a), CAN.ear - 3.5 * Math.sin(a), -Rb * Math.sin(a)]); }
  pts.push([-Rb, CAN.ear, 0], [-R + 0.05, CAN.ear, 0]);
  tube(bail, pts, CAN.bail, 6);
  const ears = [1, -1].map((s, i) => {
    const e = new Part('can_ear_' + i, B, 'tin');
    extrude(e, 'x', {outer: orient(roundRect(-0.8, CAN.ear - 0.9, 0.8, CAN.ear + 0.9, 0.35, 2), true), holes: []}, s > 0 ? R - 0.1 : -R - 0.15, s > 0 ? R + 0.15 : -R + 0.1, 0);
    return e;
  });
  // the heaps: nails or screws lying mostly along x in layers in the opened box (w along x, d along z), fixed scatter
  const heap = (name, w, d, n, len, shank, head, mat) => {
    const p = new Part(name, B, mat);
    for (let k = 0; k < n; k++) {
      const a = (vn3(k * 3.1, 0.7, 9.1) - 0.5) * 0.7 + (k % 2) * Math.PI, c = Math.cos(a) * len / 2, s = Math.sin(a) * len / 2;
      const x = (vn3(k * 1.7 + 0.3, 2.1, 5.5) - 0.5) * Math.max(0, w - 2 * Math.abs(c) - 0.3), z = (vn3(k * 2.3 + 4.1, 7.7, 1.3) - 0.5) * Math.max(0, d - 2 * Math.abs(s) - 0.3);
      const y = head + 0.3 * (k % 3), tip = [x - c, y, z - s], top = [x + c, y, z + s], dir = norm(sub(top, tip));
      tube(p, [tip, sub(top, mul(dir, 0.02))], shank, 5);
      tube(p, [top, add(top, mul(dir, 0.08))], head, 8);
    }
    return p;
  };
  const nailHeap = heap('nail_heap', NAILS.W, NAILS.D, 16, WORK.nail, WORK.nailShank, WORK.nailHead, 'nail');
  const screwHeap = heap('screw_heap', SCREWS.W, SCREWS.D, 14, WORK.screw, 0.2, WORK.screwHead, 'zinc');
  const PARTS = [nail, screw, slot, brushWet, brushBristle, brushFerrule, brushHandle, stick, stickWet, offcut, lid, canOpen, canLabel, canStain, bail, ...ears, nailHeap, screwHeap];
  for (const q of PARTS) scaleCm(q);
  const pine = pos => tone3([206, 172, 124], 1 + 0.14 * (fbm3(pos[0] * 0.5 + 2.1, pos[1] * 6, pos[2] * 6) - 0.5) + 0.05 * (vn3(pos[0] * 1.6 + 5.3, pos[1] * 16, pos[2] * 16) - 0.5));
  const endgrain = pos => tone3([182, 146, 100], 1 + 0.06 * Math.sin(Math.hypot(pos[1] + 3, pos[2] + 9) * 40) + 0.04 * (vn3(pos[1] * 9, pos[2] * 9, 3) - 0.5));
  // the bristles' dry part: pale natural bristle, streaked along their length
  const bristle = pos => tone3([196, 178, 142], 1 + 0.12 * (vn3(pos[0] * 14, pos[1] * 0.8, pos[2] * 14) - 0.5));
  const beech = pos => tone3([178, 124, 78], 1 + 0.08 * (fbm3(pos[0] * 3, pos[1] * 0.4, pos[2] * 3) - 0.5));
  return {PARTS, bg: 'plate', MATS: {
    plate: {c: [206, 172, 124], hl: 4, sm: 56, se: 62, f0: 20},
    pine: {c: pine, hl: 4, sm: 56, se: 62, f0: 20},                     // bare softwood (the bench draws the species' own boards)
    endgrain: {c: endgrain, hl: 3, sm: 50, se: 56, f0: 20},
    nail: {c: [118, 120, 124], hl: 8, sm: 120, se: 134, f0: 230},       // bright common nails, a little dull
    zinc: {c: [168, 170, 166], hl: 8, sm: 146, se: 156, f0: 230},       // zinc-plated screws
    slot: {c: [40, 40, 42], hl: 2, sm: 60, se: 70, f0: 230},            // the slot's shadow
    bristle: {c: bristle, hl: 2, sm: 60, se: 70, f0: 20},
    beech: {c: beech, hl: 4, sm: 120, se: 140, f0: 20},                 // a varnished beech handle
    stain: {c: [70, 42, 24], hl: 3, sm: 196, se: 210, f0: 20},          // wet walnut stain, glossy
    tin: {c: [176, 178, 180], hl: 8, sm: 150, se: 166, f0: 230},
    label: {c: [86, 54, 34], hl: 3, sm: 70, se: 78, f0: 20},
    wire: {c: [150, 150, 152], hl: 6, sm: 130, se: 150, f0: 230},
  }, atlas: 1024, meshOnly: true};
}
/** A face's unit normal (Newell), for picking the offcut's end grain. */
const newellOf = (part, f) => { const P = f.ids.map(i => part.v[i]); let n = [0, 0, 0];
  for (let i = 0; i < P.length; i++) { const a = P[i], b = P[(i + 1) % P.length]; n = add(n, [(a[1] - b[1]) * (a[2] + b[2]), (a[2] - b[2]) * (a[0] + b[0]), (a[0] - b[0]) * (a[1] + b[1])]); }
  return norm(n); };

export const ITEMS = {
  ...Object.fromEntries(SPECIES.map(s => [s + '_board', () => board(s)])),
  wood_finish_can: woodFinishCan,
  nail_box: nailBox,
  furniture_hardware_box: hardwareBox,
  cabinet_hinge: cabinetHinge,
  iron_knob: ironKnob,
  wooden_knob: woodenKnob,
  wooden_knob_work: woodenKnobWork,
  keyhole_escutcheon: keyholeEscutcheon,
  screw_box: screwBox,
  furniture_work: furnitureWork,
};

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  const args = process.argv.slice(2), check = args.includes('--check'), ids = args.filter(a => !a.startsWith('--'));
  for (const id of ids) if (!ITEMS[id]) throw new Error('unknown furniture material ' + id);
  let stale = 0;
  for (const id of ids.length ? ids : Object.keys(ITEMS)) {
    const spec = ITEMS[id](), held = spec.check;
    delete spec.check;
    const {outputs, stats} = bake(id, spec);
    stats.sizeCm = stats.size.map(v => r4(v / K));
    if (held) stats.held = held;
    console.log(JSON.stringify(stats));
    for (const [file, data] of outputs) {
      const buf = Buffer.isBuffer(data) ? data : Buffer.from(data);
      if (check) { if (!fs.existsSync(file) || !fs.readFileSync(file).equals(buf)) { console.log('stale ' + path.relative(ROOT, file)); stale++; } }
      else { fs.mkdirSync(path.dirname(file), {recursive: true}); fs.writeFileSync(file, buf); }
    }
  }
  if (check) { if (stale) process.exit(1); console.log('CHECK OK'); } else console.log('written');
}
