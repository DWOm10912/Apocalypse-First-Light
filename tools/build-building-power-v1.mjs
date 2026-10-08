// Building Power V1 (docs/gameplay/building_power_v1_plan.md "V1 定案", docs/models/building_power_v1.md): the Distribution
// Panel (`distribution_panel`, 配电盘) and the Service Meter Box (`service_meter_box`, 电表箱) as Pure Mesh + 1024 LabPBR atlases,
// rendered by the AFL Animated Block Mesh Runtime and in inventories by AflStaticMeshItemRenderer.
// User 2026-10-07: concept B, a new North American panelboard (the old industrial_electrical_box stays a loot box): ANSI 61
// grey enclosure on the wall, a hinged door with a flush latch, behind it a dead-front with a 2-pole main breaker on top and
// two columns of six branch breakers, the circuit directory on the inside of the door. Power comes in through a conduit to
// a pull box on the cell's bottom face (the standard steel port, facing down) and leaves for cable devices through a
// second one on the top face (facing up). The meter box: a round meter in its socket, the main disconnect with the red
// handle on its right side (up = on), a generator inlet under the meter, a conduit to a pull box on the bottom face.
// Bones / channels:
//   panel: body; port_up (the top pull box, drawn only while a cable plugs in above); door ('open', -110 deg); main ('main_off'); b0..b11 ('b0_off'..'b11_off'): each handle swings away from
//          the middle when its breaker is off; b0..b5 the column on the viewer's left (+X), top to bottom, b6..b11 the right.
//   meter: body; handle ('off', the disconnect handle down).
//   node tools/build-building-power-v1.mjs                 -> writes sources, geo / sidecars / profiles / maps / models / blockstates
//   node tools/build-building-power-v1.mjs --check         -> verifies every output is up to date
//   node tools/build-building-power-v1.mjs --preview DIR   -> writes only geo / sidecars / maps into DIR
// Frame (px): block bottom centre at the origin, front toward -Z (NORTH at facing=north), the supporting wall at z = +8,
// +X = the viewer's left (as the electrical box). Both blocks hang on the wall in the cell in front of it.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {convert, serializeCompact} from './export-afl-mesh.mjs';
import {Part, AX, extrude, mul, area2, unwrap, paint, png, readPng, zFightLevels} from './cube-slab-mesh-lib.mjs';
import {addPowerPort, PORT} from './afl-power-port.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function assert(c, m) { if (!c) throw new Error(m); }
const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
const rect = (u0, v0, u1, v1) => [[u0, v0], [u1, v0], [u1, v1], [u0, v1]];
const r6 = v => +v.toFixed(6) || 0, r3 = v => +v.toFixed(3) || 0, r12 = v => +v.toFixed(12) || 0;

function slab(part, ax, a, b, c = 0) {
  const [ku, kv, ka] = ax === 'x' ? [2, 1, 0] : ax === 'y' ? [2, 0, 1] : [0, 1, 2];
  extrude(part, ax, {outer: orient(rect(a[ku], a[kv], b[ku], b[kv]), true), holes: []}, a[ka], b[ka], c);
}
function cyl(part, ax, cu, cv, r, a0, a1, seg) {
  const A = AX[ax], ang = i => Math.PI / seg + 2 * Math.PI * i / seg;
  const rg = a => Array.from({length: seg}, (_, i) => part.vtx(A.to3(cu + r * Math.cos(ang(i)), cv + r * Math.sin(ang(i)), a)));
  const r0 = rg(a0), r1 = rg(a1);
  for (let i = 0; i < seg; i++) { const j = (i + 1) % seg, m = (ang(i) + ang(j)) / 2;
    part.face([r0[i], r0[j], r1[j], r1[i]], A.to3(Math.cos(m), Math.sin(m), 0), 'side'); }
  for (const [ring_, a, s] of [[r0, a0, -1], [r1, a1, 1]]) { const c = part.vtx(A.to3(cu, cv, a));
    for (let i = 0; i < seg; i++) part.face([c, ring_[i], ring_[(i + 1) % seg]], mul(A.n, s), 'cap'); }
}
// a vertex transform applied to every vertex a builder adds after start() (the ports are built facing +Z, then turned)
function turned(parts, fn, build) { const n = parts.map(p => p.v.length); build(); parts.forEach((p, k) => { for (let i = n[k]; i < p.v.length; i++) p.v[i] = fn(p.v[i]); }); }
// the standard port facing down on the cell's bottom face / up on its top face (proper rotations: winding kept)
const portDown = q => [q[0], 8 - q[2], q[1]], portUp = q => [q[0], 8 + q[2], -q[1]];

// ---------------- shared: pull box + conduit to a cell face ----------------
// a 6 x 6 px pull box centred on the bottom (or top) face; its outer face carries the standard port; a 3/4" EMT conduit
// (r 0.32 px) from the device's bottom (or top) runs to the box's back at mid height.
const BOX_H = 1.4, COND_R = 0.32;
function pullBox(P, side, bone, from) {   // side 'down' | 'up'; from: [x, y, z] where the conduit leaves the device
  const down = side === 'down', y0 = down ? PORT.depth : 16 - PORT.depth - BOX_H, y1 = y0 + BOX_H, ym = (y0 + y1) / 2;
  slab(P(`pullbox_${side}`, bone, 'ansi'), 'z', [-3, y0, -3], [3, y1, 3], 0.08);
  const ports = {plate: P(`port_${side}_plate`, bone, 'port'), socket: P(`port_${side}_socket`, bone, 'socket'), pin: P(`port_${side}_pin`, bone, 'portdark')};
  turned([ports.plate, ports.socket, ports.pin], down ? portDown : portUp, () => addPowerPort(ports, 0, 0));
  const c = P(`conduit_${side}`, bone, 'emt'), [fx, fy, fz] = from;
  cyl(c, 'y', fz, fx, COND_R, Math.min(fy, ym), Math.max(fy, ym), 12);                  // vertical leg out of the device
  cyl(c, 'z', fx, ym, COND_R, 3 - 0.05, fz, 12);                                         // horizontal leg into the box back
  cyl(c, 'z', fx, ym, COND_R + 0.12, 3 - 0.05, 3.5, 12);                                 // connector at the box
  cyl(c, 'y', fz, fx, COND_R + 0.12, down ? fy - 0.5 : fy, down ? fy : fy + 0.5, 12);    // connector at the device
}

// ---------------- the distribution panel ----------------
export const PANEL = {x: 4.5, y: [2.4, 13.6], z: [5.6, 8.0], rim: 0.18, door: {z: [5.3, 5.6], hinge: [4.42, 0, 5.45], deg: -110},
  dead: 6.4, main: {x: 1.5, y: [11.2, 12.7]}, rows: 6, pitch: 1.18, rowTop: 10.55, col: [0.35, 3.3], handleDeg: 20};
function buildPanel() {
  const PARTS = [], P = (name, bone, mat) => { const p = new Part(name, bone, mat); PARTS.push(p); return p; };
  const {x: X, y: [y0, y1], z: [z0, z1], rim} = PANEL;
  const shell = P('enclosure', 'body', 'ansi');
  extrude(shell, 'z', {outer: orient(rect(-X, y0, X, y1), true), holes: [orient(rect(-X + rim, y0 + rim, X - rim, y1 - rim), false)]}, z0, z1 - 0.35, 0.08);
  slab(shell, 'z', [-X, y0, z1 - 0.4], [X, y1, z1], 0.08);
  // dead-front (inner cover) with the breaker openings, and the breakers behind it
  const dz = PANEL.dead;
  slab(P('dead_front', 'body', 'dead'), 'z', [-X + rim, y0 + rim, dz], [X - rim, y1 - rim, dz + 0.2], 0);
  const brk = P('breaker_bodies', 'body', 'breaker');
  slab(brk, 'z', [-PANEL.main.x - 0.35, PANEL.main.y[0] - 0.25, dz - 0.25], [PANEL.main.x + 0.35, PANEL.main.y[1] + 0.25, dz], 0.05);
  const slots = [];
  for (const side of [1, -1]) for (let i = 0; i < PANEL.rows; i++) {
    const yc = PANEL.rowTop - i * PANEL.pitch, xa = side * PANEL.col[0], xb = side * PANEL.col[1];
    slab(brk, 'z', [Math.min(xa, xb), yc - 0.48, dz - 0.18], [Math.max(xa, xb), yc + 0.48, dz], 0.04);
    slots.push({side, yc, bone: 'b' + (side > 0 ? i : 6 + i)});
  }
  // handles: the main (2-pole tie bar) pivots about X at its body centre; each branch handle about Y at its inner end
  const hMain = P('handle_main', 'main', 'handle');
  slab(hMain, 'z', [-PANEL.main.x + 0.2, (PANEL.main.y[0] + PANEL.main.y[1]) / 2 - 0.2, dz - 0.65], [PANEL.main.x - 0.2, (PANEL.main.y[0] + PANEL.main.y[1]) / 2 + 0.2, dz - 0.25], 0.05);
  const pivots = {main: [0, (PANEL.main.y[0] + PANEL.main.y[1]) / 2, dz - 0.25]};
  for (const s of slots) {
    const h = P('handle_' + s.bone, s.bone, 'handle'), xi = s.side * (PANEL.col[0] + 0.85);
    slab(h, 'z', [xi - 0.26, s.yc - 0.22, dz - 0.66], [xi + 0.26, s.yc + 0.22, dz - 0.18], 0.04);
    pivots[s.bone] = [xi, s.yc, dz - 0.18];
  }
  // door: steel cover, flush slot latch on the viewer's right, two hinges on the left; the directory card inside
  const {z: [dz0, dz1], hinge} = PANEL.door;
  slab(P('door_cover', 'door', 'ansi'), 'z', [-X + 0.06, y0 + 0.06, dz0], [X - 0.06, y1 - 0.06, dz1 - 0.02], 0.06);
  slab(P('door_latch', 'door', 'chrome'), 'z', [-X + 0.55, 7.2, dz0 - 0.12], [-X + 1.05, 8.8, dz0], 0.04);
  slab(P('door_card', 'door', 'card'), 'z', [-X + 1.2, y0 + 1.2, dz1], [X - 1.2, y1 - 1.6, dz1 + 0.04], 0);
  const hg = P('hinges', 'body', 'chrome');
  for (const [a, b] of [[3.4, 4.6], [11.4, 12.6]]) cyl(hg, 'y', hinge[2], hinge[0], 0.2, a, b, 10);
  // conduits to the two pull boxes
  pullBox(P, 'down', 'body', [0, y0, 6.8]);
  pullBox(P, 'up', 'port_up', [0, y1, 6.8]);
  // rest = on: the main tie bar up 30 deg, each branch toggle 20 deg toward the middle; off swings them through the straight
  // position to the same angle the other way
  const D = PANEL.handleDeg;
  const RIG = [['body', [0, 0, 0]], ['port_up', [0, 0, 0]], ['door', hinge], ['main', pivots.main, [30, 0, 0]], ...slots.map(s => [s.bone, pivots[s.bone], [0, s.side * D, 0]])];
  const ANIM = {open: {ticks: 10, transforms: {door: {rotation: [0, PANEL.door.deg, 0]}}},
    main_off: {ticks: 3, transforms: {main: {rotation: [-60, 0, 0]}}},
    ...Object.fromEntries(slots.map(s => [s.bone + '_off', {ticks: 2, transforms: {[s.bone]: {rotation: [0, -s.side * 2 * D, 0]}}}]))};
  return {PARTS, RIG, ANIM};
}

// ---------------- the service meter box ----------------
export const METER = {socket: [1.2, 5.6, 4.0, 10.8], glass: {c: [3.4, 7.4], r: 1.85, z: [4.3, 6.4]}, disc: [-5.8, -0.4, 3.0, 11.4],
  handle: {pivot: [-5.95, 8.4, 6.6], deg: -120}, inlet: [1.6, 5.2, 1.4, 3.2]};
function buildMeter() {
  const PARTS = [], P = (name, bone, mat) => { const p = new Part(name, bone, mat); PARTS.push(p); return p; };
  const [sx0, sx1, sy0, sy1] = METER.socket, [dx0, dx1, dy0, dy1] = METER.disc, [ix0, ix1, iy0, iy1] = METER.inlet;
  slab(P('meter_socket', 'body', 'ansi'), 'z', [sx0, sy0, 6.4], [sx1, sy1, 8], 0.1);
  const g = METER.glass, ring = P('meter_ring', 'body', 'chrome');
  cyl(ring, 'z', g.c[0], g.c[1], g.r + 0.25, g.z[1] - 0.35, g.z[1], 24);
  const base = P('meter_base', 'body', 'meterbase');
  cyl(base, 'z', g.c[0], g.c[1], g.r - 0.05, g.z[1] - 0.6, g.z[1] - 0.35, 24);
  const face = P('meter_face', 'body', 'meterface');
  cyl(face, 'z', g.c[0], g.c[1], g.r - 0.25, g.z[1] - 0.75, g.z[1] - 0.6, 24);
  cyl(P('meter_glass', 'body', 'glass'), 'z', g.c[0], g.c[1], g.r, g.z[0], g.z[1] - 0.36, 24);
  // the disconnect (fused safety switch) and its operating handle on the viewer's right side (-X)
  slab(P('disconnect', 'body', 'ansi'), 'z', [dx0, dy0, 5.4], [dx1, dy1, 8], 0.1);
  slab(P('disconnect_cover_seam', 'body', 'ansidk'), 'z', [dx0 + 0.3, dy0 + 0.3, 5.3], [dx1 - 0.3, dy1 - 0.3, 5.4], 0);
  const [hx, hy, hz] = METER.handle.pivot;
  cyl(P('handle_hub', 'body', 'ansidk'), 'x', hz, hy, 0.55, dx0 - 0.3, dx0, 12);
  const h = P('handle_lever', 'handle', 'red');
  slab(h, 'x', [dx0 - 0.62, hy - 0.3, hz - 0.35], [dx0 - 0.3, hy + 2.6, hz + 0.35], 0.06);
  // generator inlet under the meter: a small box with a hinged weather cover
  slab(P('inlet_box', 'body', 'ansi'), 'z', [ix0, iy0, 6.5], [ix1, iy1, 8], 0.08);
  slab(P('inlet_cover', 'body', 'ansidk'), 'z', [ix0 + 0.3, iy0 + 0.25, 6.3], [ix1 - 0.3, iy1 - 0.25, 6.5], 0.04);
  // nipple meter -> disconnect, conduit disconnect -> the bottom pull box
  cyl(P('nipple', 'body', 'emt'), 'x', 7.2, 7.4, 0.36, dx1 - 0.05, sx0 + 0.05, 12);
  pullBox(P, 'down', 'body', [-2.4, dy0, 6.7]);
  const RIG = [['body', [0, 0, 0]], ['handle', METER.handle.pivot]];
  const ANIM = {off: {ticks: 4, transforms: {handle: {rotation: [METER.handle.deg, 0, 0]}}}};   // lever from up (on) toward down (off)
  return {PARTS, RIG, ANIM};
}

// ---------------- materials ----------------
const hash = (a, b, c) => { let h = Math.imul(a | 0, 374761393) ^ Math.imul(b | 0, 668265263) ^ Math.imul(c | 0, 2147483647); h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };
function vn(x, y, z) {
  const xi = Math.floor(x), yi = Math.floor(y), zi = Math.floor(z), s = t => t * t * (3 - 2 * t), l = (a, b, t) => a + (b - a) * t;
  const sx = s(x - xi), sy = s(y - yi), sz = s(z - zi), h = (a, b, c) => hash(a, b, c * 7919 + 337);
  return l(l(l(h(xi, yi, zi), h(xi + 1, yi, zi), sx), l(h(xi, yi + 1, zi), h(xi + 1, yi + 1, zi), sx), sy),
    l(l(h(xi, yi, zi + 1), h(xi + 1, yi, zi + 1), sx), l(h(xi, yi + 1, zi + 1), h(xi + 1, yi + 1, zi + 1), sx), sy), sz);
}
const tone = (c, k) => c.map(v => v * k), mix = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t), sm = (a, b, x) => { const t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); };
// ANSI 61 grey powder coat with a faint orange-peel clouding (+-2 %), low frequency only
const ANSI = [148, 153, 156];
const ansiC = p => tone(ANSI, 1 + 0.04 * (vn(p[0] * 0.35, p[1] * 0.35, p[2] * 0.35) - 0.5));
// the directory card: off-white board with faint ruled lines (two columns of circuit lines, no writing)
const cardC = (p, n) => { if (n[2] < 0.5) return [214, 210, 198]; const v = ((p[1] - 2) / 0.55) % 1, line = sm(0.04, 0.0, Math.abs(v - 0.5) - 0.06);
  return mix([228, 224, 212], [150, 152, 158], 0.55 * line * (Math.abs(p[0]) > 0.15 ? 1 : 0)); };
// the meter face: white dial plate, five small register dials and a black rotating disc slot (printed, anti-aliased)
const faceC = (p, n) => { if (n[2] > -0.5) return [228, 228, 222]; const g = METER.glass, u = -(p[0] - g.c[0]), v = p[1] - g.c[1];
  let k = 0; for (let i = 0; i < 5; i++) { const d = Math.hypot(u - (-1.0 + i * 0.5), v - 0.55) - 0.2; k = Math.max(k, sm(0.04, -0.04, d)); }
  const slot = Math.max(Math.abs(u) - 0.9, Math.abs(v + 0.6) - 0.09); k = Math.max(k, 0.85 * sm(0.04, -0.04, slot));
  return mix([232, 231, 225], [36, 38, 41], k); };
export const MATS = {
  ansi:     {c: ansiC, hl: 10, sm: 98, se: 118, f0: 20},
  ansidk:   {c: [118, 123, 127], hl: 8, sm: 92, se: 110, f0: 20},
  dead:     {c: [176, 179, 180], hl: 8, sm: 96, se: 112, f0: 20},      // the dead-front, a lighter grey
  breaker:  {c: [34, 35, 38], hl: 6, sm: 116, se: 130, f0: 20},        // black moulded breakers
  handle:   {c: [70, 72, 76], hl: 10, sm: 120, se: 136, f0: 20},     // lighter than the faces: on / off reads at a glance
  chrome:   {c: [160, 163, 167], hl: 14, sm: 140, se: 160, f0: 255},   // latch, hinges, meter ring
  card:     {c: cardC, hl: 0, sm: 70, se: 70, f0: 10},
  box:      {c: [126, 131, 135], hl: 8, sm: 96, se: 112, f0: 20},      // the pull boxes, a darker grey than the panel
  emt:      {c: [150, 154, 158], hl: 10, sm: 124, se: 146, f0: 30},    // galvanised EMT, coated (as the cable's steel, F0 30)
  port:     {c: [150, 153, 158], hl: 12, sm: 120, se: 140, f0: 30},    // the standard port plate (as the cable plug's steel)
  socket:   {c: [26, 27, 29], hl: 0, sm: 60, se: 60, f0: 20},
  portdark: {c: [96, 98, 102], hl: 6, sm: 110, se: 120, f0: 30},
  meterbase:{c: [44, 46, 50], hl: 6, sm: 100, se: 110, f0: 20},
  meterface:{c: faceC, hl: 0, sm: 90, se: 90, f0: 14},
  glass:    {c: [200, 214, 216], hl: 0, sm: 240, se: 240, f0: 10},
  red:      {c: [176, 46, 38], hl: 8, sm: 104, se: 120, f0: 20},
};
export const GLASS_ALPHA = 40;

// ---------------- assets ----------------
const ATLAS = 1024, PAD = 2;
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
const ASSETS = {panel: {id: 'distribution_panel', build: buildPanel}, meter: {id: 'service_meter_box', build: buildMeter}};
function asset(kind) {
  const {id, build} = ASSETS[kind], {PARTS, RIG, ANIM} = build();
  const UV = unwrap(PARTS, {atlas: ATLAS, pad: PAD, startS: 20, stepS: 0.5});
  for (const p of PARTS) assert(p.f.every(f => UV.faceUV.get(p)?.has(f)), 'unmapped face in ' + p.name);
  const painted = paint({PARTS, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas: ATLAS, pad: PAD, MATS, ZONED: new Set(), groupInfo: new Map(),
    sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [...ANSI, 255], s: [98, 20, 0, 255], n: [128, 128, 255, 255]}});
  const M = painted.PNG.map(b => readPng(b).px);
  for (const is of UV.islands) if (is.part.mat === 'glass')
    for (let y = is.py - PAD; y < is.py + is.H + PAD; y++) for (let x = is.px - PAD; x < is.px + is.W + PAD; x++) M[0][(y * ATLAS + x) * 4 + 3] = GLASS_ALPHA;
  const PNGS = M.map(px => png(px, ATLAS, ATLAS));
  const uuid = k => { const h = createHash('sha256').update('afl-building-power-v1:' + id + ':' + k).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
  const nodes = new Map(RIG.map(([b]) => [b, {uuid: uuid('group:' + b), isOpen: true, children: []}])), elements = [];
  for (const p of PARTS) {
    const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {}, uvs = UV.faceUV.get(p);
    p.v.forEach((q, i) => { vertices[key(i)] = q.map(r12); });
    p.f.forEach((f, fi) => { const uv = uvs.get(f); faces['f' + key(fi)] = {uv: Object.fromEntries(f.ids.map((vid, j) => [key(vid), uv[j].map(r12)])), vertices: f.ids.map(key), texture: 0}; });
    const eid = uuid('mesh:' + p.name);
    elements.push({name: p.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: true, locked: false,
      render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: eid});
    nodes.get(p.bone).children.push(eid);
  }
  const source = {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: id, model_identifier: '', visible_box: [1, 1, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: ATLAS, height: ATLAS},
    elements, groups: RIG.map(([b, origin]) => ({name: b, uuid: uuid('group:' + b), export: true, locked: false, scope: 0, selected: false, visibility: true,
      _static: {properties: {}, temp_data: {}}, origin: origin.slice(), rotation: [0, 0, 0], color: 0, children: [], reset: false, shade: true,
      mirror_uv: false, autouv: 0, isOpen: true, primary_selected: false})),
    outliner: RIG.map(([b]) => nodes.get(b)),
    textures: [{name: id + '.png', relative_path: `textures/${id}.png`, folder: '', namespace: '', id: '0', group: '', scope: 0,
      width: ATLAS, height: ATLAS, uv_width: ATLAS, uv_height: ATLAS, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
      file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
      frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture'),
      source: 'data:image/png;base64,' + PNGS[0].toString('base64')}],
    animations: []};
  const geo = {format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: 'geometry.' + id, texture_width: ATLAS, texture_height: ATLAS,
    visible_bounds_width: 2, visible_bounds_height: 2, visible_bounds_offset: [0, 0.5, 0]},
    bones: RIG.map(([b, origin]) => ({name: b, pivot: [-origin[0] || 0, origin[1], origin[2]], ...(b === 'port_up' ? {neverRender: true} : {})}))}]};
  const LAYERS = Object.fromEntries(PARTS.filter(p => p.mat === 'glass').map(p => [p.name, 'translucent']));
  const sidecar = convert(source, geo, {}, id + '.bbmodel', 2, null, LAYERS);
  // bounds: every vertex through every channel's motion (Euler XYZ as the runtime: Rx, then Ry, then Rz about the pivot)
  const pivotOf = Object.fromEntries(RIG), rot = (q, pv, [ax, ay, az], t) => {
    let x = q[0] - pv[0], y = q[1] - pv[1], z = q[2] - pv[2]; const a = ax * t * Math.PI / 180, b = ay * t * Math.PI / 180, c = az * t * Math.PI / 180;
    [y, z] = [y * Math.cos(a) - z * Math.sin(a), y * Math.sin(a) + z * Math.cos(a)]; [x, z] = [x * Math.cos(b) + z * Math.sin(b), -x * Math.sin(b) + z * Math.cos(b)];
    [x, y] = [x * Math.cos(c) - y * Math.sin(c), x * Math.sin(c) + y * Math.cos(c)]; return [pv[0] + x, pv[1] + y, pv[2] + z]; };
  const motion = b => Object.values(ANIM).find(a => a.transforms[b])?.transforms[b].rotation, restOf = Object.fromEntries(RIG.map(([b, , r]) => [b, r]));
  const pts = PARTS.flatMap(p => { const m = motion(p.bone) || [0, 0, 0], r = restOf[p.bone] || [0, 0, 0];
    return Array.from({length: 11}, (_, k) => p.v.map(q => rot(q, pivotOf[p.bone], r.map((v, i) => v + m[i] * k / 10), 1))).flat(); });
  const lo = [0, 1, 2].map(k => Math.min(...pts.map(q => q[k]))), hi = [0, 1, 2].map(k => Math.max(...pts.map(q => q[k]))), mg = 0.25;
  const bounds = [(lo[0] - mg + 8) / 16, (lo[1] - mg) / 16, (lo[2] - mg + 8) / 16, (hi[0] + mg + 8) / 16, (hi[1] + mg) / 16, (hi[2] + mg + 8) / 16].map(r6);
  const profile = {format_version: 1, geometry: `apocalypse_firstlight:geo/${id}.geo.json`, texture: `apocalypse_firstlight:textures/block/${id}.png`,
    origin: [0, 0, 0], scale: [1, 1, 1], facing: 'horizontal', bounds,
    parts: Object.fromEntries(RIG.map(([b, origin, rest]) => [b, {pivot: origin.map(v => r6(v / 16)), ...(rest ? {rest: {rotation: rest}} : {})}])),
    animations: Object.fromEntries(Object.entries(ANIM).map(([ch, a]) => [ch, {duration_ticks: a.ticks, easing: 'ease_in_out', transforms: a.transforms}]))};
  const zf = zFightLevels(PARTS, new Map());
  const aabb = ps => [0, 1, 2].map(k => Math.min(...ps.map(q => q[k]))).concat([0, 1, 2].map(k => Math.max(...ps.map(q => q[k])))).map(r3);
  return {kind, id, PARTS, UV, PNGS, source, geo, sidecar, meshText: serializeCompact(sidecar), profile, bounds, zf, closed: aabb(PARTS.flatMap(p => p.v)), channels: Object.keys(ANIM)};
}
function itemDisplay(PARTS) {
  const rot = ([ax, ay]) => q => { const a = ay * Math.PI / 180, b = ax * Math.PI / 180, x = q[0] * Math.cos(a) + q[2] * Math.sin(a), z = -q[0] * Math.sin(a) + q[2] * Math.cos(a);
    return [x, q[1] * Math.cos(b) - z * Math.sin(b), q[1] * Math.sin(b) + z * Math.cos(b)]; };
  const pts = PARTS.flatMap(p => p.v).map(q => [q[0], q[1] - 8, q[2]]), S3 = v => [v, v, v];
  const view = (r, fit, extra = [0, 0, 0]) => { const R = pts.map(rot(r)), xs = R.map(q => q[0]), ys = R.map(q => q[1]), zs = R.map(q => q[2]);
    const s = typeof fit === 'number' && fit > 1 ? r3(fit / Math.max(Math.max(...xs) - Math.min(...xs), Math.max(...ys) - Math.min(...ys))) : fit;
    const c = [(Math.max(...xs) + Math.min(...xs)) / 2, (Math.max(...ys) + Math.min(...ys)) / 2, (Math.max(...zs) + Math.min(...zs)) / 2];
    return {rotation: [r[0], r[1], 0], translation: [r3(-s * c[0] + extra[0]), r3(-s * c[1] + extra[1]), r3(-s * c[2] + extra[2])], scale: S3(s)}; };
  return {gui: view([20, 205], 14), ground: view([0, 0], 0.3, [0, 3, 0]), fixed: view([0, 180], 0.6),
    thirdperson_righthand: view([75, 205], 0.4, [0, 2.5, 0]), firstperson_righthand: view([0, 205], 0.45, [0, 1, 0])};
}

export const BUILT = Object.fromEntries(Object.keys(ASSETS).map(k => [k, asset(k)]));
const bb = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const outputs = [];
for (const d of Object.values(BUILT)) {
  const {id} = d;
  outputs.push([path.join(bb, `${id}_v1.bbmodel`), JSON.stringify(d.source)], [path.join(assets, `geo/${id}.geo.json`), JSON.stringify(d.geo, null, 2) + '\n'],
    [path.join(assets, `meshes/${id}.aflmesh.json`), d.meshText], [path.join(assets, `block_mesh_profiles/${id}.json`), JSON.stringify(d.profile, null, 2) + '\n'],
    [path.join(assets, `models/item/${id}.json`), JSON.stringify({parent: 'builtin/entity', gui_light: 'side', textures: {particle: `apocalypse_firstlight:block/${id}`}, display: itemDisplay(d.PARTS)}, null, 2) + '\n'],
    [path.join(assets, `models/block/${id}.json`), JSON.stringify({textures: {particle: `apocalypse_firstlight:block/${id}`}}, null, 2) + '\n'],
    [path.join(assets, `blockstates/${id}.json`), JSON.stringify({variants: {'': {model: `apocalypse_firstlight:block/${id}`}}}, null, 2) + '\n'],
    ...['', '_s', '_n'].flatMap((k, i) => [[path.join(bb, `textures/${id}${k}.png`), d.PNGS[i]], [path.join(assets, `textures/block/${id}${k}.png`), d.PNGS[i]]]));
}
export const stats = Object.fromEntries(Object.values(BUILT).map(d => [d.id, {triangles: d.sidecar.parts.flatMap(p => p.faces).reduce((s, q) => s + q.length - 2, 0),
  parts: d.sidecar.parts.length, texelsPerPx: d.UV.S, islands: d.UV.islands.length, coplanarOverlaps: d.zf.unresolved.length, closed: d.closed, bounds: d.bounds, channels: d.channels.length}]));
const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  console.log(JSON.stringify(stats, null, 1));
  for (const d of Object.values(BUILT)) if (d.zf.unresolved.length) console.log('COPLANAR ' + d.id, JSON.stringify(d.zf.unresolved.slice(0, 12)));
  const pi = process.argv.indexOf('--preview');
  if (pi > 0) {
    const dir = process.argv[pi + 1]; fs.mkdirSync(dir, {recursive: true});
    for (const d of Object.values(BUILT)) { fs.writeFileSync(path.join(dir, `${d.id}.aflmesh.json`), d.meshText); fs.writeFileSync(path.join(dir, `${d.id}.profile.json`), JSON.stringify(d.profile));
      ['', '_s', '_n'].forEach((k, i) => fs.writeFileSync(path.join(dir, `${d.id}${k}.png`), d.PNGS[i])); }
    console.log('preview written to ' + dir);
  } else if (process.argv.includes('--check')) {
    for (const [file, data] of outputs) { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; if (!cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data))) throw new Error('stale ' + path.relative(ROOT, file)); }
    console.log('CHECK OK');
  } else {
    for (const [file] of outputs) fs.mkdirSync(path.dirname(file), {recursive: true});
    for (const [file, data] of outputs) fs.writeFileSync(file, data);
    console.log('wrote ' + outputs.length + ' files');
  }
}
