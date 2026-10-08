// Chest Freezer V2: two-cell commercial chest freezer with two sliding glass lids, as Pure Mesh + 512 LabPBR atlas,
// rendered by the AFL Animated Block Mesh Runtime (bones 'body', 'left_lid', 'right_lid', 'lights', 'lights_lit';
// channels 'left_open' / 'right_open') and in inventories by AflStaticMeshItemRenderer. Replaces the V1 GeckoLib model
// (afl_chest_freezer.bbmodel); the block's lid state machine, shapes and click test are V1's (ChestFreezerBlock), so the
// envelope, lid tracks and lid ranges below follow its VoxelShapes exactly.
//   node tools/build-chest-freezer-v2.mjs                 -> writes source, runtime geo / sidecar / profile / maps / item model
//   node tools/build-chest-freezer-v2.mjs --check         -> verifies every output is up to date
//   node tools/build-chest-freezer-v2.mjs --preview DIR   -> writes only geo / sidecar / maps into DIR (offline review)
// Frame (px): the beverage cooler's convention: x -8..24 across both cells, master = the left cell (x 8..24) as the
// viewer faces the front, y up, front toward -Z; moved by X -16 on output (master-centred).
// Look kept from V1: warm light-grey cabinet, beige top rim, white liner with wire dividers, two aluminium-framed glass
// lids, a control panel at the front's left end; toned down for shaders (no glaring white). New: a status display and a
// power LED (unlit set 'lights', lit set 'lights_lit': LabPBR emissive, neverRender in the geo), the standard AFL power
// port on the back of the master cell (tools/afl-power-port.mjs), and the stocked filler: anonymous frozen goods in the
// eight wire compartments, three arrangements each (bones goods_<compartment>_<variant>, neverRender; the block entity
// shows at most one per compartment, chosen from its position, while the freezer holds anything).
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {convert, serializeCompact} from './export-afl-mesh.mjs';
import {addIecInlet} from './afl-iec-inlet.mjs';
import {Part, extrude, area2, unwrap, paint, png, readPng, zFightLevels} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function assert(c, m) { if (!c) throw new Error(m); }
const PARTS = [], P = (name, bone, mat) => { const p = new Part(name, bone, mat); PARTS.push(p); return p; };
const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
const rect = (u0, v0, u1, v1) => [[u0, v0], [u1, v0], [u1, v1], [u0, v1]];
function slab(part, ax, a, b, c = 0) {
  const [ku, kv, ka] = ax === 'x' ? [2, 1, 0] : ax === 'y' ? [2, 0, 1] : [0, 1, 2];
  extrude(part, ax, {outer: orient(rect(a[ku], a[kv], b[ku], b[kv]), true), holes: []}, a[ka], b[ka], c);
}
// horizontal frame (extruded along y): outer / hole as [x0, z0, x1, z1]
function yFrame(part, outer, hole, y0, y1, c) {
  extrude(part, 'y', {outer: orient(rect(outer[1], outer[0], outer[3], outer[2]), true),
    holes: [orient(rect(hole[1], hole[0], hole[3], hole[2]), false)]}, y0, y1, c);
}

// ---------------- dimensions (source px) ----------------
export const SHIFT = -16;
export const RIM_TOP = 15.8, WALL_TOP = 13.65, LINER_FLOOR = 3.6;
export const LIDS = {   // ChestFreezerBlock.lids(): closed ranges, tracks (bottom of frame) and grip tops
  left: {x0: 7.9, x1: 22.25, z: 6.4, y0: 14.3, y1: 14.6, grip: 14.8},   // master half, lower track
  right: {x0: -6.25, x1: 8.1, z: 6.55, y0: 14.9, y1: 15.2, grip: 15.4}, // slave half, upper track
};
export const LID_TRAVEL = 14.15, LID_TICKS = 14;                          // ChestFreezerBlock.ANIMATION_TICKS
export const PANEL = {x0: 17.4, x1: 22.8, y0: 3.0, y1: 8.6, z: -7.7};     // control panel on the front, viewer's left end
export const DISPLAY = {x0: 18.0, x1: 21.2, y0: 6.9, y1: 8.1, z: -7.92};  // status display (text drawn by the renderer)
export const LED = {x0: 21.6, x1: 22.1, y0: 7.3, y1: 7.8};
// power inlet (source px): its centre on the back wall (z 7.7), low at the master's outer corner (ChestFreezerBlockEntity#cordGeometry: x - 16)
export const CORD = {x: 21.3, y: 2.2, z: 7.7};

{
  // feet, base trim and the insulated shell (outer walls, bottom), white liner inside
  const feet = P('feet', 'body', 'rubber');
  for (const x of [-6.8, 22.8]) for (const z of [-6.4, 6.4]) slab(feet, 'y', [x - 0.8, 0, z - 0.8], [x + 0.8, 0.8, z + 0.8], 0);
  slab(P('base_trim', 'body', 'base'), 'y', [-7.8, 0.7, -7.5], [23.8, 1.3, 7.5], 0.1);
  const shell = P('cabinet', 'body', 'cabinet');
  slab(shell, 'z', [-8, 1.2, -7.7], [24, WALL_TOP, -6.3], 0.2);   // front wall
  extrude(shell, 'z', {outer: orient(rect(-8, 1.2, 24, WALL_TOP), true), holes: []}, 6.3, 7.7, 0.2);   // back wall
  slab(shell, 'x', [-8, 1.2, -6.3], [-6.3, WALL_TOP, 6.3], 0);    // side walls
  slab(shell, 'x', [22.3, 1.2, -6.3], [24, WALL_TOP, 6.3], 0);
  slab(shell, 'y', [-6.3, 1.2, -6.3], [22.3, 3.3, 6.3], 0);       // insulated bottom
  const liner = P('liner', 'body', 'liner');
  slab(liner, 'y', [-6.3, 3.3, -6.3], [22.3, LINER_FLOOR, 6.3], 0);
  slab(liner, 'z', [-6.3, LINER_FLOOR, -6.3], [22.3, WALL_TOP, -6.0], 0);
  slab(liner, 'z', [-6.3, LINER_FLOOR, 6.0], [22.3, WALL_TOP, 6.3], 0);
  slab(liner, 'x', [-6.3, LINER_FLOOR, -6.0], [-6.0, WALL_TOP, 6.0], 0);
  slab(liner, 'x', [22.0, LINER_FLOOR, -6.0], [22.3, WALL_TOP, 6.0], 0);
  // top rim: outer bumper ring to RIM_TOP, inner lip the lids slide on, raised rails of the upper track
  yFrame(P('rim', 'body', 'rim'), [-8, -8, 24, 8], [-6.6, -6.6, 22.6, 6.6], WALL_TOP, RIM_TOP, 0.25);
  const lip = P('rim_lip', 'body', 'rim');
  yFrame(lip, [-6.6, -6.6, 22.6, 6.6], [-6.0, -6.0, 22.0, 6.0], WALL_TOP, 14.25, 0);
  const rails = P('upper_rails', 'body', 'handle');
  for (const [z0, z1] of [[-6.6, -6.42], [6.42, 6.6]]) slab(rails, 'y', [-6.6, 14.25, z0], [22.6, LIDS.right.y0, z1], 0);
  // wire dividers: one lengthwise frame along z 0, three crosswise frames (rods 0.2 px)
  const wire = P('wire_dividers', 'body', 'wire'), R = 0.1, TOP = 10.8;
  const rodX = (x0, x1, y, z) => slab(wire, 'x', [x0, y - R, z - R], [x1, y + R, z + R], 0);
  const rodY = (x, y0, y1, z) => slab(wire, 'y', [x - R, y0, z - R], [x + R, y1, z + R], 0);
  const rodZ = (x, y, z0, z1) => slab(wire, 'z', [x - R, y - R, z0], [x + R, y + R, z1], 0);
  rodX(-5.95, 21.95, TOP, 0); rodX(-5.95, 21.95, LINER_FLOOR + 0.25, 0);
  for (const x of [-2.4, 1.2, 4.8, 11.2, 14.8, 18.4]) rodY(x, LINER_FLOOR + 0.35, TOP - R, 0);
  for (const x of [0.8, 8, 15.2]) {
    rodZ(x, TOP, -5.95, 5.95);
    for (const z of [-5.8, -2.9, 2.9, 5.8]) rodY(x, LINER_FLOOR, TOP - R, z);
    rodY(x, LINER_FLOOR, TOP - R, 0);
  }
  // control panel: dark plate, louvres, display window and LED (both in the light sets below)
  slab(P('control_panel', 'body', 'panel'), 'z', [PANEL.x0, PANEL.y0, PANEL.z - 0.15], [PANEL.x1, PANEL.y1, PANEL.z + 0.02], 0.05);
  const vent = P('panel_vents', 'body', 'vent');
  for (let i = 0; i < 5; i++) slab(vent, 'z', [18.0, 3.5 + i * 0.55, PANEL.z - 0.22], [22.2, 3.75 + i * 0.55, PANEL.z - 0.14], 0);
  // power inlet: IEC C14 (tools/afl-iec-inlet.mjs; Power Outlets V1, 2026-10-08), the detachable cord's C13 connector plugs in here
  addIecInlet({housing: P('inlet_housing', 'body', 'inlet'), floor: P('inlet_floor', 'body', 'socket'), pin: P('inlet_pins', 'body', 'inlet_pin')}, CORD.x, CORD.y, CORD.z);
}

// ---------------- lids: aluminium frame, glass pane, grip at the meeting end ----------------
for (const side of ['left', 'right']) {
  const L = LIDS[side], bone = side + '_lid', inset = 0.6;
  yFrame(P(`${side}_lid_frame`, bone, 'lid_frame'), [L.x0, -L.z, L.x1, L.z], [L.x0 + inset, -L.z + inset, L.x1 - inset, L.z - inset], L.y0, L.y1, 0.06);
  const gy = (L.y0 + L.y1) / 2;
  slab(P(`${side}_lid_glass`, bone, 'glass'), 'y', [L.x0 + inset - 0.05, gy - 0.03, -L.z + inset - 0.05], [L.x1 - inset + 0.05, gy + 0.03, L.z - inset + 0.05], 0);
  const [hx0, hx1] = side === 'left' ? [L.x0 + 0.05, L.x0 + 0.65] : [L.x1 - 0.65, L.x1 - 0.05];
  slab(P(`${side}_lid_grip`, bone, 'handle'), 'y', [hx0, L.y1, -L.z + 0.9], [hx1, L.grip, L.z - 0.9], 0.04);
}

// ---------------- status lights: unlit set (bone 'lights') and lit set (bone 'lights_lit'), same geometry ----------------
function lights(bone, suffix) {
  slab(P('display' + suffix, bone, 'screen' + suffix), 'z', [DISPLAY.x0, DISPLAY.y0, DISPLAY.z], [DISPLAY.x1, DISPLAY.y1, PANEL.z - 0.14], 0);
  slab(P('led' + suffix, bone, 'led' + suffix), 'z', [LED.x0, LED.y0, DISPLAY.z - 0.04], [LED.x1, LED.y1, PANEL.z - 0.14], 0);
}
lights('lights', '');
lights('lights_lit', '_lit');

// ---------------- goods: the stocked filler (no labels or print; plain coloured cartons, soft bags, tubs) ----------------
// Compartments between the wire frames (crosswise at x 0.8 / 8 / 15.2, lengthwise at z 0), kept 0.3 px off the rods and
// the liner: compartment = column (x, from the slave end) * 2 + row (0 front, 1 back). Every arrangement is drawn from a
// fixed seed, so each compartment's three differ in size, angle and colour, and the output is reproducible.
export const GOODS = {floor: LINER_FLOOR + 0.02, cols: [[-5.7, 0.4], [1.2, 7.6], [8.4, 14.8], [15.6, 21.7]],
  rows: [[-5.7, -0.4], [0.4, 5.7]], variants: 3};
const CARTONS = ['carton_blue', 'carton_kraft', 'carton_white', 'carton_red'], BAGS = ['bag_frost', 'bag_blue', 'bag_green'],
  TUBS = ['tub_cream', 'tub_brown', 'tub_pink'], TUB_LIDS = ['tub_lid', 'lid_blue', 'lid_red'];
const GOODS_BONES = [];
{
  let seed = 0x5eed1234;
  const rnd = () => (seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0) / 4294967296;
  const range = (a, b) => a + (b - a) * rnd(), pick = list => list[Math.floor(rnd() * list.length)];
  const ext = (w, d, yaw) => [Math.abs(w / 2 * Math.cos(yaw)) + Math.abs(d / 2 * Math.sin(yaw)), Math.abs(w / 2 * Math.sin(yaw)) + Math.abs(d / 2 * Math.cos(yaw))];
  const fit = (lo, hi, half, want) => Math.min(hi - half, Math.max(lo + half, want));   // a centre whose extent stays in [lo, hi]
  // box w (x) by d (z), turned by yaw about y, centred at (cx, cz), from y0 up h; outline points are [z, x] for 'y'
  const box = (part, cx, cz, w, d, yaw, y0, h, c) => {
    const ca = Math.cos(yaw), sa = Math.sin(yaw);
    const pts = [[-w / 2, -d / 2], [w / 2, -d / 2], [w / 2, d / 2], [-w / 2, d / 2]].map(([x, z]) => [cz + x * sa + z * ca, cx + x * ca - z * sa]);
    extrude(part, 'y', {outer: orient(pts, true), holes: []}, y0, y0 + h, c);
  };
  const disc = (part, cx, cz, r, y0, y1) => extrude(part, 'y', {outer: orient(Array.from({length: 10}, (_, i) => {
    const a = Math.PI / 10 + 2 * Math.PI * i / 10; return [cz + r * Math.sin(a), cx + r * Math.cos(a)]; }), true), holes: []}, y0, y1, 0);
  for (let col = 0; col < 4; col++) for (let row = 0; row < 2; row++) {
    const k = col * 2 + row, [x0, x1] = GOODS.cols[col], [z0, z1] = GOODS.rows[row], mx = (x0 + x1) / 2, mz = (z0 + z1) / 2, y = GOODS.floor;
    // a pile of up to three yawed boxes on one footprint, each smaller than the one below; sizes [w0, w1, d0, d1, h0, h1]
    const pile = (bone, mats, layers, sizes, turn, c) => {
      let y0 = y, cx = mx + range(-0.7, 0.7), cz = mz + range(-0.4, 0.4), yaw = range(-turn, turn);
      layers.forEach((shrink, i) => {
        const [w0, w1, d0, d1, h0, h1] = sizes, w = range(w0, w1) * shrink, d = range(d0, d1) * shrink, h = range(h0, h1) * (1 - i * 0.1);
        if (i) { yaw += range(-turn, turn) * 1.5; cx += range(-0.5, 0.5); cz += range(-0.35, 0.35); }
        const [ex, ez] = ext(w, d, yaw); cx = fit(x0, x1, ex, cx); cz = fit(z0, z1, ez, cz);
        box(P(`${bone}_${i}`, bone, pick(mats)), cx, cz, w, d, yaw, y0, h, Math.min(c, h / 3));
        y0 += h + 0.02;
      });
    };
    // 0: cartons, two or three high
    { const bone = `goods_${k}_0`; GOODS_BONES.push(bone);
      pile(bone, CARTONS, rnd() < 0.6 ? [1, 0.86, 0.72] : [1, 0.84], [4.4, 5.2, 3.3, 3.9, 1.6, 2.0], 0.2, 0.12); }
    // 1: soft bags (large chamfer), two or three, each turned across the one below
    { const bone = `goods_${k}_1`; GOODS_BONES.push(bone);
      pile(bone, BAGS, rnd() < 0.6 ? [1, 0.88, 0.76] : [1, 0.86], [4.2, 5.0, 3.0, 3.5, 1.2, 1.5], 0.55, 0.4); }
    // 2: two tubs side by side with lids, often a third stacked on the first
    {
      const bone = `goods_${k}_2`; GOODS_BONES.push(bone);
      const r = range(1.15, 1.3), h = range(1.9, 2.3), lid = 0.18, half = r + 0.08, along = r + range(0.2, 0.3) + 0.08;
      const shift = Math.min(x1 - half - (mx + along), Math.max(x0 + half - (mx - along), range(-0.4, 0.4)));
      const cz = fit(z0, z1, half, mz + range(-0.5, 0.5)), cxs = [mx - along + shift, mx + along + shift];
      cxs.forEach((cx, i) => {
        disc(P(`${bone}_tub${i}`, bone, pick(TUBS)), cx, cz, r, y, y + h);
        disc(P(`${bone}_lid${i}`, bone, pick(TUB_LIDS)), cx, cz, half, y + h + 0.02, y + h + 0.02 + lid);
      });
      if (rnd() < 0.6) {
        const y2 = y + h + 0.04 + lid, top = cxs[rnd() < 0.5 ? 0 : 1];
        disc(P(`${bone}_tub2`, bone, pick(TUBS)), top, cz, r, y2, y2 + h);
        disc(P(`${bone}_lid2`, bone, pick(TUB_LIDS)), top, cz, half, y2 + h + 0.02, y2 + h + 0.02 + lid);
      }
    }
  }
}

export const MATS = {   // Base Color, bevel highlight, smoothness open / edge, F0 (all coated / dielectric except glass F0 10)
  cabinet:    {c: [168, 166, 158], hl: 8, sm: 112, se: 130, f0: 20},     // warm light-grey powder coat (toned down from V1)
  rim:        {c: [142, 136, 124], hl: 10, sm: 104, se: 124, f0: 20},    // beige bumper rim
  base:       {c: [70, 70, 68], hl: 6, sm: 90, se: 104, f0: 20},
  liner:      {c: [184, 190, 194], hl: 6, sm: 122, se: 134, f0: 20},     // white enamel liner (the beverage cooler's)
  lid_frame:  {c: [118, 122, 126], hl: 10, sm: 140, se: 160, f0: 20},    // coated aluminium extrusions
  glass:      {c: [150, 186, 204], hl: 0, sm: 235, se: 235, f0: 10},     // the cooler's glass recipe, alpha set below
  handle:     {c: [150, 154, 158], hl: 12, sm: 150, se: 166, f0: 20},
  wire:       {c: [196, 198, 200], hl: 0, sm: 140, se: 140, f0: 20},
  panel:      {c: [52, 54, 58], hl: 6, sm: 100, se: 118, f0: 20},
  vent:       {c: [36, 38, 42], hl: 4, sm: 90, se: 100, f0: 20},
  screen:     {c: [16, 22, 24], hl: 0, sm: 200, se: 200, f0: 20},       // status display, off
  screen_lit: {c: [24, 70, 66], hl: 0, sm: 200, se: 200, f0: 20},       // backlit (emissive)
  led:        {c: [34, 56, 38], hl: 0, sm: 170, se: 170, f0: 20},       // power LED, off
  led_lit:    {c: [120, 255, 140], hl: 0, sm: 190, se: 190, f0: 20},    // on (emissive)
  rubber:     {c: [26, 26, 28], hl: 2, sm: 40, se: 48, f0: 20},
  port:       {c: [134, 138, 144], hl: 12, sm: 112, se: 130, f0: 20},   // power port plate (as the charging station's)
  socket:     {c: [24, 25, 28], hl: 0, sm: 60, se: 60, f0: 20},
  port_pin:   {c: [40, 42, 46], hl: 10, sm: 110, se: 130, f0: 20},
  inlet:        {c: [22, 23, 25], hl: 8, sm: 112, se: 132, f0: 20},
  inlet_pin:    {c: [176, 172, 160], hl: 10, sm: 172, se: 188, f0: 255},
  // goods: muted packaging (no print), rough paper, glossier film and plastic; nothing brighter than the cabinet
  carton_blue:  {c: [84, 108, 132], hl: 6, sm: 70, se: 84, f0: 20},
  carton_kraft: {c: [140, 112, 80], hl: 6, sm: 64, se: 78, f0: 20},
  carton_white: {c: [156, 158, 154], hl: 4, sm: 70, se: 84, f0: 20},
  carton_red:   {c: [136, 66, 60], hl: 6, sm: 70, se: 84, f0: 20},
  bag_frost:    {c: [164, 170, 174], hl: 4, sm: 110, se: 120, f0: 20},
  bag_blue:     {c: [90, 124, 156], hl: 6, sm: 120, se: 132, f0: 20},
  bag_green:    {c: [80, 110, 78], hl: 6, sm: 120, se: 132, f0: 20},
  tub_cream:    {c: [172, 160, 132], hl: 6, sm: 120, se: 136, f0: 20},
  tub_brown:    {c: [98, 70, 56], hl: 6, sm: 120, se: 136, f0: 20},
  tub_pink:     {c: [160, 114, 118], hl: 6, sm: 120, se: 136, f0: 20},
  tub_lid:      {c: [148, 154, 160], hl: 6, sm: 130, se: 140, f0: 20},
  lid_blue:     {c: [92, 122, 152], hl: 6, sm: 130, se: 140, f0: 20},
  lid_red:      {c: [146, 72, 66], hl: 6, sm: 130, se: 140, f0: 20},
};
export const GLASS_ALPHA = 56;                                        // the cooler's: survives the packs' alpha test
export const EMISSION = {screen_lit: 120, led_lit: 230};             // LabPBR _s alpha (0..254)

const ATLAS = 512, PAD = 2;
const UV = unwrap(PARTS, {atlas: ATLAS, pad: PAD, startS: 24, stepS: 0.25});
for (const p of PARTS) assert(p.f.every(f => UV.faceUV.get(p)?.has(f)), 'unmapped face in ' + p.name);
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
const painted = paint({PARTS, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas: ATLAS, pad: PAD, MATS, ZONED: new Set(), groupInfo: new Map(),
  sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [...MATS.cabinet.c, 255], s: [112, 20, 0, 255], n: [128, 128, 255, 255]}});
const MAPS = painted.PNG.map(b => { const r = readPng(b); assert(r.bpp === 4 && r.w === ATLAS, 'atlas format'); return r.px; });
for (const is of UV.islands) {
  const glass = is.part.mat === 'glass', emission = EMISSION[is.part.mat];
  if (!glass && emission === undefined) continue;
  for (let y = is.py - PAD; y < is.py + is.H + PAD; y++) for (let x = is.px - PAD; x < is.px + is.W + PAD; x++) {
    if (glass) MAPS[0][(y * ATLAS + x) * 4 + 3] = GLASS_ALPHA;
    else MAPS[1][(y * ATLAS + x) * 4 + 3] = emission;
  }
}
const PNGS = MAPS.map(px => png(px, ATLAS, ATLAS));

// ---------------- source (Free Model) ----------------
const ID = 'chest_freezer';
const uuid = s => { const h = createHash('sha256').update('afl-chest-freezer-v2:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r12 = v => +v.toFixed(12) || 0;
for (const p of PARTS) p.v = p.v.map(q => [q[0] + SHIFT, q[1], q[2]]);
const RIG = ['body', 'left_lid', 'right_lid', 'lights', 'lights_lit', ...GOODS_BONES];
const NEVER_RENDER = new Set(['lights_lit', ...GOODS_BONES]);   // the item icon: unlit lights, no goods
const source = (() => {
  const groups = RIG.map(name => ({name, uuid: uuid('group:' + name), export: true, locked: false, scope: 0, selected: false, visibility: true,
    _static: {properties: {}, temp_data: {}}, origin: [0, 0, 0], rotation: [0, 0, 0], color: 0, children: [], reset: false, shade: true,
    mirror_uv: false, autouv: 0, isOpen: true, primary_selected: false}));
  const nodes = new Map(RIG.map(name => [name, {uuid: uuid('group:' + name), isOpen: true, children: []}]));
  const elements = [];
  for (const p of PARTS) {
    const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {}, uvs = UV.faceUV.get(p);
    p.v.forEach((q, i) => { vertices[key(i)] = q.map(r12); });
    p.f.forEach((f, fi) => { const uv = uvs.get(f); faces['f' + key(fi)] = {uv: Object.fromEntries(f.ids.map((id, j) => [key(id), uv[j].map(r12)])), vertices: f.ids.map(key), texture: 0}; });
    const id = uuid('mesh:' + p.name);
    elements.push({name: p.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: true, locked: false,
      render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: id});
    nodes.get(p.bone).children.push(id);
  }
  return {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: ID, model_identifier: '', visible_box: [2, 1, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: ATLAS, height: ATLAS},
    elements, groups, outliner: RIG.map(name => nodes.get(name)),
    textures: [{name: ID + '.png', relative_path: `textures/${ID}.png`, folder: '', namespace: '', id: '0', group: '', scope: 0,
      width: ATLAS, height: ATLAS, uv_width: ATLAS, uv_height: ATLAS, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
      file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
      frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture'),
      source: 'data:image/png;base64,' + PNGS[0].toString('base64')}],
    animations: []};
})();

// ---------------- runtime ----------------
const geo = {format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: 'geometry.' + ID, texture_width: ATLAS, texture_height: ATLAS,
  visible_bounds_width: 3, visible_bounds_height: 2, visible_bounds_offset: [0, 0.5, 0]},
  bones: RIG.map(name => NEVER_RENDER.has(name) ? {name, pivot: [0, 0, 0], neverRender: true} : {name, pivot: [0, 0, 0]})}]};
const LAYERS = Object.fromEntries(PARTS.filter(p => p.mat === 'glass').map(p => [p.name, 'translucent']));
const sidecar = convert(source, geo, {}, ID + '.bbmodel', 2, null, LAYERS);
const meshText = serializeCompact(sidecar);

const r3 = v => +v.toFixed(3) || 0, r6 = v => +v.toFixed(6) || 0;
const aabbOf = pts => [0, 1, 2].map(k => Math.min(...pts.map(q => q[k]))).concat([0, 1, 2].map(k => Math.max(...pts.map(q => q[k]))));
const bounds = (() => {   // closed and both open poses (the lids slide inside the footprint)
  const shifted = p => p.bone === 'left_lid' ? p.v.map(q => [q[0] - LID_TRAVEL, q[1], q[2]]) : p.bone === 'right_lid' ? p.v.map(q => [q[0] + LID_TRAVEL, q[1], q[2]]) : [];
  const b = aabbOf(PARTS.flatMap(p => [...p.v, ...shifted(p)])), m = 0.25;
  return [(b[0] - m + 8) / 16, (b[1] - m) / 16, (b[2] - m + 8) / 16, (b[3] + m + 8) / 16, (b[4] + m) / 16, (b[5] + m + 8) / 16].map(r6);
})();
const profile = {format_version: 1, geometry: `apocalypse_firstlight:geo/${ID}.geo.json`,
  texture: `apocalypse_firstlight:textures/block/${ID}.png`, origin: [0, 0, 0], scale: [1, 1, 1], facing: 'horizontal', bounds,
  parts: Object.fromEntries(RIG.map(name => [name, {pivot: [0, 0, 0]}])),
  // V1's 0.70 s slides (ChestFreezerBlock commits the state after ANIMATION_TICKS); left opens toward the viewer's right
  animations: {
    left_open: {duration_ticks: LID_TICKS, easing: 'ease_in_out', transforms: {left_lid: {translation: [r6(-LID_TRAVEL / 16), 0, 0]}}},
    right_open: {duration_ticks: LID_TICKS, easing: 'ease_in_out', transforms: {right_lid: {translation: [r6(LID_TRAVEL / 16), 0, 0]}}}}};

// item model: builtin/entity; every view centred on the closed mesh (its middle sits 8 px toward -X from the master cell)
const itemDisplay = (() => {
  const rot = ([ax, ay]) => q => { const a = ay * Math.PI / 180, b = ax * Math.PI / 180, x = q[0] * Math.cos(a) + q[2] * Math.sin(a), z = -q[0] * Math.sin(a) + q[2] * Math.cos(a);
    return [x, q[1] * Math.cos(b) - z * Math.sin(b), q[1] * Math.sin(b) + z * Math.cos(b)]; };
  const pts = PARTS.filter(p => !NEVER_RENDER.has(p.bone)).flatMap(p => p.v).map(q => [q[0], q[1] - 8, q[2]]), S3 = v => [v, v, v];
  const view = (r, fit, extra = [0, 0, 0]) => { const R = pts.map(rot(r)), xs = R.map(q => q[0]), ys = R.map(q => q[1]), zs = R.map(q => q[2]);
    const s = typeof fit === 'number' && fit > 1 ? r3(fit / Math.max(Math.max(...xs) - Math.min(...xs), Math.max(...ys) - Math.min(...ys))) : fit;
    const c = [(Math.max(...xs) + Math.min(...xs)) / 2, (Math.max(...ys) + Math.min(...ys)) / 2, (Math.max(...zs) + Math.min(...zs)) / 2];
    return {rotation: [r[0], r[1], 0], translation: [r3(-s * c[0] + extra[0]), r3(-s * c[1] + extra[1]), r3(-s * c[2] + extra[2])], scale: S3(s)}; };
  // the front (-Z) toward the viewer like a vanilla block item: gui / hands at 225 deg, item frame at 0
  return {gui: view([25, 225], 15), ground: view([0, 0], 0.25, [0, 3, 0]), fixed: view([0, 0], 0.45),
    thirdperson_righthand: view([75, 225], 0.28, [0, 2.5, 0]), firstperson_righthand: view([0, 225], 0.32, [0, 1, 0])};
})();
const itemModel = {parent: 'builtin/entity', gui_light: 'side', textures: {particle: `apocalypse_firstlight:block/${ID}`}, display: itemDisplay};
const blockModel = {loader: 'apocalypse_firstlight:static_mesh', textures: {particle: `apocalypse_firstlight:block/${ID}`}};

const bb = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const outputs = [[path.join(bb, ID + '.bbmodel'), JSON.stringify(source)], [path.join(assets, `geo/${ID}.geo.json`), JSON.stringify(geo, null, 2) + '\n'],
  [path.join(assets, `meshes/${ID}.aflmesh.json`), meshText], [path.join(assets, `block_mesh_profiles/${ID}.json`), JSON.stringify(profile, null, 2) + '\n'],
  [path.join(assets, `models/item/${ID}.json`), JSON.stringify(itemModel, null, 2) + '\n'], [path.join(assets, `models/block/${ID}.json`), JSON.stringify(blockModel, null, 2) + '\n'],
  ...['', '_s', '_n'].flatMap((k, i) => [[path.join(bb, `textures/${ID}${k}.png`), PNGS[i]], [path.join(assets, `textures/block/${ID}${k}.png`), PNGS[i]]])];

// coplanar check per goods variant: a compartment never shows two arrangements, which share its floor
const goodsVariant = p => p.bone.startsWith('goods_') ? +p.bone.split('_')[2] : -1;
const zfs = Array.from({length: GOODS.variants}, (_, v) => zFightLevels(PARTS, new Map(),
  {skip: p => p.bone === 'lights_lit' || (goodsVariant(p) >= 0 && goodsVariant(p) !== v)}));
const faces = sidecar.parts.flatMap(p => p.faces), zf = {unresolved: zfs.flatMap(z => z.unresolved)};
const trisOf = p => p.f.reduce((t, f) => t + f.ids.length - 2, 0);
export const stats = {triangles: faces.reduce((s, q) => s + q.length - 2, 0), parts: sidecar.parts.length, texelsPerPx: UV.S, islands: UV.islands.length,
  translucent: Object.keys(LAYERS), coplanarOverlaps: zf.unresolved.length, bounds, closed: aabbOf(PARTS.flatMap(p => p.v)).map(r3), gui: itemDisplay.gui,
  byBone: Object.fromEntries(RIG.filter(b => !b.startsWith('goods_')).map(b => [b, PARTS.filter(p => p.bone === b).reduce((s, p) => s + trisOf(p), 0)])),
  goods: (() => { const per = GOODS_BONES.map(b => PARTS.filter(p => p.bone === b).reduce((s, p) => s + trisOf(p), 0));
    return {bones: GOODS_BONES.length, total: per.reduce((a, b) => a + b, 0), maxPerArrangement: Math.max(...per), minPerArrangement: Math.min(...per)}; })()};
const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  console.log(JSON.stringify(stats));
  if (zf.unresolved.length) console.log('COPLANAR', JSON.stringify(zf.unresolved.slice(0, 12)));
  const pi = process.argv.indexOf('--preview');
  if (pi > 0) {
    const dir = process.argv[pi + 1]; fs.mkdirSync(dir, {recursive: true});
    fs.writeFileSync(path.join(dir, `${ID}.geo.json`), JSON.stringify(geo, null, 2) + '\n');
    fs.writeFileSync(path.join(dir, `${ID}.aflmesh.json`), meshText);
    ['', '_s', '_n'].forEach((k, i) => fs.writeFileSync(path.join(dir, `${ID}${k}.png`), PNGS[i]));
    console.log('preview written to ' + dir);
  } else if (process.argv.includes('--check')) {
    for (const [file, data] of outputs) { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; if (!cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data))) throw new Error('stale ' + path.relative(ROOT, file)); }
    console.log('CHECK OK');
  } else {
    for (const [file] of outputs) fs.mkdirSync(path.dirname(file), {recursive: true});
    for (const [file, data] of outputs) fs.writeFileSync(file, data);
    console.log('wrote ' + outputs.map(([f]) => path.relative(ROOT, f)).join(', '));
  }
}
