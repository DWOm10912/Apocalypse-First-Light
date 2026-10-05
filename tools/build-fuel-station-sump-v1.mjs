// Fuel station sump set V1 (docs/models/fuel_station_sump_v1.md, 2026-10-05): what sits over an Underground Fuel Tank
// buried two blocks deep. The tank's top-centre port carries the submersible pump in its sump (the layer under the
// forecourt): a pump head on the port, its product outlet (an AFL fluid port) on the FACING side, its junction box and AFL
// power port on the other side, black HDPE sump walls round it. Over it, flush with the forecourt, the pump manhole
// cover (a round steel lid in a concrete-set frame, pried open with a crowbar). Over the tank's fill port (its end cell),
// a fluid pipe rises to the fill cover: a small lid over a spill bucket with the grade's colour on its rim and cap
// (gasoline red, diesel yellow), and an AFL fluid port on its bottom face for that pipe.
// Under the Fuel Dispenser, 2026-10-05: the dispenser sump (UDC), a black HDPE box two cells along the island and two deep
// (the forecourt layer and the pipe layer) with a steel flange the dispenser sits on: the gasoline line's AFL fluid port on
// the A end of its lower cells, the diesel line's on the B end, each in a frame of the grade's colour, and the AFL power
// port on the back of the lower A cell. It hands the fuel and the power up to the dispenser (FuelDispenserSumpBlock).
// Pure Mesh + one LabPBR atlas exported as Forge OBJ block models (static, chunk-baked).
//   node tools/build-fuel-station-sump-v1.mjs            -> writes sources, OBJ / MTL / block + item models, blockstates, atlas
//   node tools/build-fuel-station-sump-v1.mjs --check    -> verifies every output is up to date
//   node tools/build-fuel-station-sump-v1.mjs --preview DIR
// Frame (px): the cell's centre is the origin, front (FACING) = -Z, y up. OBJ in block units: px / 16 + 0.5.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {Part, extrude, add, sub, dot, cross, norm, newell, area2, unwrap, paint, png, zFightLevels} from './cube-slab-mesh-lib.mjs';
import {addFluidPort} from './afl-fluid-port.mjs';
import {addPowerPort, portHole} from './afl-power-port.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function assert(c, m) { if (!c) throw new Error(m); }
const D2R = Math.PI / 180;

// ---------------- primitives ----------------
const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
const rect = (u0, v0, u1, v1) => [[u0, v0], [u1, v0], [u1, v1], [u0, v1]];
const shape = (L, holes = []) => ({outer: orient(L, true), holes: holes.map(h => orient(h, false))});
const circle = (cu, cv, r, n, ph = 0.5) => Array.from({length: n}, (_, i) => { const a = 2 * Math.PI * (i + ph) / n; return [cu + r * Math.cos(a), cv + r * Math.sin(a)]; });
const hex = (cu, cv, r) => Array.from({length: 6}, (_, i) => { const a = (30 + 60 * i) * D2R; return [cu + r * Math.cos(a), cv + r * Math.sin(a)]; });
const zx = L => L.map(([x, z]) => [z, x]);
const planY = (part, L, y0, y1, c = 0, holes = []) => extrude(part, 'y', shape(zx(L), holes.map(zx)), y0, y1, c);   // plan (x, z)
function slab(part, ax, a, b, c = 0) {
  const [ku, kv, ka] = ax === 'x' ? [2, 1, 0] : ax === 'y' ? [2, 0, 1] : [0, 1, 2];
  extrude(part, ax, shape(rect(Math.min(a[ku], b[ku]), Math.min(a[kv], b[kv]), Math.max(a[ku], b[ku]), Math.max(a[kv], b[kv]))), Math.min(a[ka], b[ka]), Math.max(a[ka], b[ka]), c);
}
const boltY = (part, x, z, r, y0, y1) => extrude(part, 'y', shape(hex(z, x, r)), y0, y1, 0);
function lathe(part, axis, c, profile, seg, caps = [true, true], phase = 0.5) {
  const at = (s, r, i) => { const a = 2 * Math.PI * (i + phase) / seg, u = r * Math.cos(a), v = r * Math.sin(a);
    return axis === 'z' ? [c[0] + u, c[1] + v, s] : axis === 'y' ? [c[0] + u, s, c[1] + v] : [s, c[0] + u, c[1] + v]; };
  const dir = (i, nr, na) => { const a = 2 * Math.PI * (i + phase) / seg, u = Math.cos(a) * nr, v = Math.sin(a) * nr;
    return axis === 'z' ? [u, v, na] : axis === 'y' ? [u, na, v] : [na, u, v]; };
  const rings = profile.map(([s, r]) => Array.from({length: seg}, (_, i) => part.vtx(at(s, r, i))));
  for (let k = 0; k + 1 < profile.length; k++) for (let i = 0; i < seg; i++) {
    const j = (i + 1) % seg, [s0, r0] = profile[k], [s1, r1] = profile[k + 1], nr = s1 - s0, na = r0 - r1;
    part.face([rings[k][i], rings[k][j], rings[k + 1][j], rings[k + 1][i]], dir(i + 0.5, Math.hypot(nr, na) > 1e-9 ? nr : 1, Math.hypot(nr, na) > 1e-9 ? na : 0));
  }
  [[0, -1], [profile.length - 1, 1]].forEach(([k, sgn], ci) => { if (!caps[ci]) return;
    const s = profile[k][0], m = part.vtx(axis === 'z' ? [c[0], c[1], s] : axis === 'y' ? [c[0], s, c[1]] : [s, c[0], c[1]]);
    for (let i = 0; i < seg; i++) part.face([m, rings[k][i], rings[k][(i + 1) % seg]], dir(0, 0, sgn), 'cap'); });
}
const cyl = (part, axis, c, s0, s1, r, seg, caps = [true, true]) => lathe(part, axis, c, [[s0, r], [s1, r]], seg, caps);
// rotate a part's vertices about the X axis through (y0, z0) by deg (the opened lids)
const hingeX = (part, y0, z0, deg) => { const a = deg * D2R, c = Math.cos(a), s = Math.sin(a);
  part.v = part.v.map(([x, y, z]) => { const dy = y - y0, dz = z - z0; return [x, y0 + dy * c + dz * s, z0 + dz * c - dy * s]; }); };

// ---------------- materials ----------------
const hash = (a, b, c) => { let h = Math.imul(a | 0, 374761393) ^ Math.imul(b | 0, 668265263) ^ Math.imul(c | 0, 2147483647); h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };
function vn(x, y, z) {
  const xi = Math.floor(x), yi = Math.floor(y), zi = Math.floor(z), s = t => t * t * (3 - 2 * t), l = (a, b, t) => a + (b - a) * t;
  const sx = s(x - xi), sy = s(y - yi), sz = s(z - zi), h = (a, b, c) => hash(a, b, c * 7919 + 101);
  return l(l(l(h(xi, yi, zi), h(xi + 1, yi, zi), sx), l(h(xi, yi + 1, zi), h(xi + 1, yi + 1, zi), sx), sy),
    l(l(h(xi, yi, zi + 1), h(xi + 1, yi, zi + 1), sx), l(h(xi, yi + 1, zi + 1), h(xi + 1, yi + 1, zi + 1), sx), sy), sz);
}
const tone = (c, k) => c.map(v => v * k), mott = (p, s, k) => k * (vn(p[0] * s, p[1] * s, p[2] * s) - 0.5);
export const MATS = {
  head:     {c: p => tone([70, 74, 80], 1 + mott(p, 0.3, 0.04)), hl: 8, sm: 112, se: 136, f0: 26},     // cast pump head, coated
  cap:      {c: p => tone([56, 59, 64], 1 + mott(p, 0.3, 0.04)), hl: 8, sm: 108, se: 132, f0: 26},
  jbox:     {c: p => tone([40, 42, 46], 1 + mott(p, 0.3, 0.04)), hl: 6, sm: 96, se: 118, f0: 22},      // junction box, conduit
  hdpe:     {c: p => tone([31, 32, 34], 1 + mott(p, 0.2, 0.04)), hl: 3, sm: 70, se: 84, f0: 18},       // black HDPE sump / spill bucket
  steel:    {c: p => tone([86, 90, 96], 1 + mott(p, 0.3, 0.04)), hl: 10, sm: 104, se: 132, f0: 28},    // manhole frame and lid (coated cast steel)
  concrete: {c: p => tone([146, 144, 138], 1 + mott(p, 0.22, 0.08)), hl: 5, sm: 40, se: 52, f0: 20},   // the forecourt's concrete (fuel island curb)
  pry:      {c: [20, 21, 23], hl: 0, sm: 60, se: 60, f0: 20},                                          // the lid's pry slots
  bolt:     {c: [124, 128, 133], hl: 7, sm: 124, se: 140, f0: 30},
  gasoline: {c: [166, 46, 38], hl: 6, sm: 150, se: 160, f0: 24},                                     // grade colours, as the tank labels
  diesel:   {c: [204, 158, 34], hl: 6, sm: 150, se: 160, f0: 24},
  fportPlate:  {c: p => tone([62, 66, 72], 1 + mott(p, 0.3, 0.04)), hl: 10, sm: 120, se: 144, f0: 26},
  fportThroat: {c: [24, 25, 27], hl: 0, sm: 60, se: 60, f0: 20},
  fportStud:   {c: [124, 128, 133], hl: 7, sm: 124, se: 140, f0: 30},
  pportPlate:  {c: [150, 154, 160], hl: 12, sm: 150, se: 168, f0: 255},
  pportSocket: {c: [26, 27, 29], hl: 2, sm: 60, se: 70, f0: 20},
  pportPin:    {c: [96, 100, 104], hl: 6, sm: 130, se: 140, f0: 255},
};

// ---------------- dimensions (px) ----------------
export const SUMP = {wall: 7.4};                                 // HDPE sump walls from here to the boundary
export const MANHOLE = {hole: 6.0, rim: 6.6, lid: 5.9, open: 76, hinge: -6.0};   // the round opening, the steel rim, the lid; opened about the -Z edge
export const FILL = {hole: 4.6, rim: 5.2, lid: 4.5, bucket: [4.4, -1.6], riser: 1.5, cap: 2.0, open: 76, hinge: -4.6};

// the submersible pump in its sump: outlet toward -Z (FACING), power toward +Z
function pump(P) {
  const walls = P('sump_walls', 'hdpe'), w = SUMP.wall;
  // four walls, open top and bottom; the outlet and power port go through the -Z / +Z walls
  extrude(walls, 'z', shape(rect(-8, -8, 8, 8), [rect(-5.6, -5.6, 5.6, 5.6)]), -8, -w, 0);
  extrude(walls, 'z', shape(rect(-8, -8, 8, 8), [rect(...portHole(0, 0))]), w, 8, 0);
  slab(walls, 'x', [-8, -8, -w], [-w, 8, w], 0);
  slab(walls, 'x', [w, -8, -w], [8, 8, w], 0);
  // the head: base flange over the tank's port (its studs inside), column, casting, cap with bolts
  planY(P('base_flange', 'cap'), rect(-5.4, -5.4, 5.4, 5.4), -8, -7.2, 0.1);
  cyl(P('column', 'head'), 'y', [0, 0], -7.2, -4.2, 3.0, 20, [false, false]);
  lathe(P('casting', 'head'), 'y', [0, 0], [[-4.2, 3.2], [-3.6, 4.6], [1.0, 4.6], [1.6, 4.2]], 24, [false, false]);
  lathe(P('cap', 'cap'), 'y', [0, 0], [[1.6, 4.8], [1.9, 5.0], [2.6, 5.0], [2.9, 4.7]], 24, [false, true]);
  const bolts = P('cap_bolts', 'bolt');
  for (let k = 0; k < 6; k++) { const a = (30 + 60 * k) * D2R; boltY(bolts, 4.4 * Math.cos(a), 4.4 * Math.sin(a), 0.36, 2.9, 3.2); }
  // product outlet: a round nozzle toward -Z, a square neck, the AFL fluid port on the -Z face centre (set in the wall)
  cyl(P('outlet', 'head'), 'z', [0, 0], -6.2, -4.2, 2.0, 20, [false, false]);
  slab(P('outlet_neck', 'head'), 'z', [-5.6, -5.6, -7.35], [5.6, 5.6, -6.2], 0.08);   // behind the port plate and its throat
  const fport = {plate: P('fport_plate', 'fportPlate'), throat: P('fport_throat', 'fportThroat'), studs: P('fport_studs', 'fportStud')};
  addFluidPort(fport, 0, 0, 7.4, 8);
  for (const p of Object.values(fport)) p.v = p.v.map(([x, y, z]) => [-x, y, -z]);   // turned to face -Z
  // junction box toward +Z with its conduit to the head, the AFL power port on the +Z face centre
  slab(P('junction_box', 'jbox'), 'z', [-2.6, -2.8, 4.2], [2.6, 2.8, 6.8], 0.15);
  cyl(P('conduit', 'jbox'), 'z', [0, 2.0], 3.8, 4.4, 0.7, 10, [false, false]);
  const pport = {plate: P('pport_plate', 'pportPlate'), socket: P('pport_socket', 'pportSocket'), pin: P('pport_pin', 'pportPin')};
  addPowerPort(pport, 0, 0, 6.8, 8);
}
// the manhole cover cell: concrete top with the round opening, the steel rim set in it, the shaft down to the sump
function manholeFrame(P) {
  const M = MANHOLE;
  planY(P('top', 'concrete'), rect(-8, -8, 8, 8), 6.8, 8, 0, [circle(0, 0, M.rim, 32)]);
  lathe(P('rim', 'steel'), 'y', [0, 0], [[8, M.rim], [8, M.hole], [7.4, M.hole]], 32, [false, false]);
  lathe(P('shaft', 'concrete'), 'y', [0, 0], [[7.4, M.hole], [-8, M.hole], [-8, M.rim + 0.8], [6.8, M.rim + 0.8]], 32, [false, false]);
}
function manholeLid(P, open) {
  const M = MANHOLE, lid = P('lid', 'steel');
  cyl(lid, 'y', [0, 0], 7.4, 7.98, M.lid, 32);
  const slots = P('pry_slots', 'pry');
  for (const x of [-3.2, 3.2]) slab(slots, 'y', [x - 0.9, 7.98, 3.6], [x + 0.9, 8.02, 4.4], 0);
  if (open) for (const p of [lid, slots]) hingeX(p, 7.7, M.hinge, M.open);
}
// the fill cover cell: concrete top, the grade-coloured rim, the spill bucket, the fill riser stub and its cap, the AFL
// fluid port on the bottom face (the pipe from the tank's fill port)
function fillFrame(P, grade) {
  const F = FILL;
  planY(P('top', 'concrete'), rect(-8, -8, 8, 8), 6.8, 8, 0, [circle(0, 0, F.rim, 24)]);
  lathe(P('rim', grade), 'y', [0, 0], [[8, F.rim], [8, F.hole], [7.4, F.hole]], 24, [false, false]);
  const b = F.bucket[1];
  lathe(P('bucket', 'hdpe'), 'y', [0, 0], [[7.4, F.hole], [b, F.hole], [b, F.riser + 0.3], [b - 0.4, F.riser + 0.3], [b - 0.4, F.rim + 0.6], [6.8, F.rim + 0.6]], 24, [false, false]);
  cyl(P('riser', 'steel'), 'y', [0, 0], -6.2, b + 1.4, F.riser, 16, [false, false]);
  planY(P('riser_base', 'steel'), rect(-4.2, -4.2, 4.2, 4.2), -7.35, -6.2, 0.08);   // over the port plate and its throat
  lathe(P('cap', grade), 'y', [0, 0], [[F.bucket[1] + 1.4, F.cap], [F.bucket[1] + 2.4, F.cap], [F.bucket[1] + 2.6, F.cap - 0.3]], 16, [false, true]);
  const fport = {plate: P('fport_plate', 'fportPlate'), throat: P('fport_throat', 'fportThroat'), studs: P('fport_studs', 'fportStud')};
  addFluidPort(fport, 0, 0, 7.4, 8);
  for (const p of Object.values(fport)) p.v = p.v.map(([x, y, z]) => [x, -z, y]);   // turned to face -Y
}
function fillLid(P, open) {
  const F = FILL, lid = P('lid', 'steel');
  cyl(lid, 'y', [0, 0], 7.4, 7.98, F.lid, 24);
  if (open) hingeX(lid, 7.7, F.hinge, F.open);
}

// the dispenser sump: lower A cell centre at the origin, B at +X (FACING.getClockWise() of a north-facing sump), 2 deep
function dispenserSump(P) {
  slab(P('udc_box', 'hdpe'), 'y', [-7.4, -8, -7.4], [23.4, 23, 7.4], 0.15);   // its top inside the flange
  slab(P('udc_flange', 'steel'), 'y', [-7.9, 22.8, -7.9], [23.9, 24, 7.9], 0.1);
  const entry = (name, grade, turn) => {   // built facing +Z, turned onto an end face
    const port = {plate: P(name + '_plate', 'fportPlate'), throat: P(name + '_throat', 'fportThroat'), studs: P(name + '_studs', 'fportStud')};
    addFluidPort(port, 0, 0, 7.4, 8);
    const frame = P(name + '_frame', grade);
    extrude(frame, 'z', shape(rect(-6.3, -6.3, 6.3, 6.3), [rect(-5.6, -5.6, 5.6, 5.6)]), 7.3, 7.6, 0);
    for (const p of [...Object.values(port), frame]) p.v = p.v.map(turn);
  };
  entry('gasoline', 'gasoline', ([x, y, z]) => [-z, y, x]);         // the A end (-X)
  entry('diesel', 'diesel', ([x, y, z]) => [z + 16, y, -x]);        // the B end (+X of the B cell)
  addPowerPort({plate: P('pport_plate', 'pportPlate'), socket: P('pport_socket', 'pportSocket'), pin: P('pport_pin', 'pportPin')}, 0, 0, 7.4, 8);
}

export const PIECES = {
  'submersible_fuel_pump/body': P => pump(P),
  'pump_manhole_cover/frame': P => manholeFrame(P),
  'pump_manhole_cover/lid_closed': P => manholeLid(P, false),
  'pump_manhole_cover/lid_open': P => manholeLid(P, true),
  'fuel_fill_cover/frame_gasoline': P => fillFrame(P, 'gasoline'),
  'fuel_fill_cover/frame_diesel': P => fillFrame(P, 'diesel'),
  'fuel_fill_cover/lid_closed': P => fillLid(P, false),
  'fuel_fill_cover/lid_open': P => fillLid(P, true),
  'fuel_dispenser_sump/body': P => dispenserSump(P),
};

// ---------------- bake ----------------
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
function bake() {
  const PARTS = [];
  for (const [piece, build] of Object.entries(PIECES)) build((name, mat) => { const p = new Part(`${piece.replace('/', '__')}__${name}`, piece, mat); PARTS.push(p); return p; });
  const live = PARTS.filter(p => p.f.length);
  for (const p of live) {
    const out = [];
    for (const f of p.f) {
      const P3 = f.ids.map(i => p.v[i]), n = newell(P3);
      const convexQuad = f.ids.length === 4 && [0, 1, 2, 3].every(k => dot(cross(sub(P3[(k + 1) % 4], P3[k]), sub(P3[(k + 2) % 4], P3[(k + 1) % 4])), n) > 1e-12);
      if (f.ids.length === 3 || convexQuad) { out.push(f); continue; }
      assert(f.ids.length === 4, `${f.ids.length}-gon in ${p.name}`);
      const k = [0, 1, 2, 3].find(k => dot(cross(sub(P3[k], P3[(k + 3) % 4]), sub(P3[(k + 1) % 4], P3[k])), n) <= 1e-12) ?? 0;
      out.push({...f, ids: [f.ids[k], f.ids[(k + 1) % 4], f.ids[(k + 2) % 4]]}, {...f, ids: [f.ids[(k + 2) % 4], f.ids[(k + 3) % 4], f.ids[k]]});
    }
    p.f = out;
  }
  const atlas = 1024, UV = unwrap(live, {atlas, pad: 2, startS: 16, stepS: 0.25});
  for (const p of live) assert(p.f.every(f => UV.faceUV.get(p)?.has(f)), `unmapped face in ${p.name}`);
  const painted = paint({PARTS: live, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas, pad: 2, MATS, ZONED: new Set(), groupInfo: new Map(),
    sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [70, 74, 80, 255], s: [112, 26, 0, 255], n: [128, 128, 255, 255]}});
  const groups = [['submersible_fuel_pump/body'], ['pump_manhole_cover/frame', 'pump_manhole_cover/lid_closed'], ['pump_manhole_cover/frame', 'pump_manhole_cover/lid_open'],
    ['fuel_fill_cover/frame_gasoline', 'fuel_fill_cover/lid_closed'], ['fuel_fill_cover/frame_gasoline', 'fuel_fill_cover/lid_open'], ['fuel_dispenser_sump/body']];
  const coplanar = groups.flatMap(g => zFightLevels(live.filter(p => g.includes(p.bone)), new Map()).unresolved);
  return {id: 'fuel_station_sump', PARTS: live, atlas, UV, maps: painted.PNG, coplanar};
}

const uuidOf = (ns, s) => { const h = createHash('sha256').update(`afl-fuel-station-sump-v1:${ns}:${s}`).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r12 = v => +v.toFixed(12) || 0;
const SHOWN = new Set(['submersible_fuel_pump/body', 'pump_manhole_cover/frame', 'pump_manhole_cover/lid_closed']);
function sourceOf(b) {
  const uuid = s => uuidOf(b.id, s), name = b.id + '_v1', bones = [...new Set(b.PARTS.map(p => p.bone))];
  const elements = b.PARTS.map(p => {
    const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {}, uvs = b.UV.faceUV.get(p);
    p.v.forEach((q, i) => { vertices[key(i)] = q.map(r12); });
    p.f.forEach((f, fi) => { const uv = uvs.get(f); faces['f' + key(fi)] = {uv: Object.fromEntries(f.ids.map((id, j) => [key(id), uv[j].map(r12)])), vertices: f.ids.map(key), texture: 0}; });
    return {name: p.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: SHOWN.has(p.bone), locked: false,
      render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: uuid('mesh:' + p.name)};
  });
  return {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name, model_identifier: '', visible_box: [1, 1, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: b.atlas, height: b.atlas},
    elements, groups: bones.map(bone => ({name: bone, uuid: uuid('group:' + bone), export: true, locked: false, scope: 0,
      selected: false, visibility: SHOWN.has(bone), _static: {properties: {}, temp_data: {}}, origin: [0, 0, 0], rotation: [0, 0, 0],
      color: 0, children: [], reset: false, shade: true, mirror_uv: false, autouv: 0, isOpen: true, primary_selected: false})),
    outliner: bones.map(bone => ({uuid: uuid('group:' + bone), isOpen: true, children: elements.filter((e, i) => b.PARTS[i].bone === bone).map(e => e.uuid)})),
    textures: [{name: name + '.png', relative_path: `textures/${name}.png`, folder: '', namespace: '', id: '0', group: '', scope: 0,
      width: b.atlas, height: b.atlas, uv_width: b.atlas, uv_height: b.atlas, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
      file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
      frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture'),
      source: 'data:image/png;base64,' + b.maps[0].toString('base64')}],
    animations: []};
}

// ---------------- OBJ / MTL / models ----------------
const f6 = v => (+v.toFixed(6)).toString();
const SMOOTH = Math.cos(36 * D2R);
function cornerNormals(V, F) {
  const fn = F.map(f => newell(f.ids.map(i => V[i]))), byV = new Map();
  F.forEach((f, k) => f.ids.forEach(i => (byV.get(i) || byV.set(i, []).get(i)).push(k)));
  return F.map((f, k) => { const n0 = norm(fn[k]); return f.ids.map(i => norm(byV.get(i).reduce((a, j) => dot(norm(fn[j]), n0) >= SMOOTH ? add(a, fn[j]) : a, [0, 0, 0]))); });
}
const toCell = q => q.map(v => v / 16 + 0.5);
function objOf(b, title, file, bones) {
  const out = [`# AFL ${title}, generated by tools/build-fuel-station-sump-v1.mjs`, `mtllib ${file.split('/').pop()}.mtl`];
  let vBase = 1, tBase = 1, nBase = 1;
  for (const p of b.PARTS) {
    if (!bones.includes(p.bone)) continue;
    const V = p.v.map(toCell), F = p.f;
    out.push(`o ${p.name}`, `usemtl ${b.id}`);
    for (const q of V) out.push(`v ${f6(q[0])} ${f6(q[1])} ${f6(q[2])}`);
    const uvs = b.UV.faceUV.get(p), vt = [], vn = [], fl = [], cn = cornerNormals(V, F), nIndex = new Map();
    F.forEach((f, k) => {
      const uv = uvs.get(f);
      fl.push('f ' + f.ids.map((id, j) => {
        const n = cn[k][j], key = n.map(f6).join(' ');
        if (!nIndex.has(key)) { nIndex.set(key, nBase + vn.length); vn.push('vn ' + key); }
        vt.push(`vt ${f6(uv[j][0] / b.atlas)} ${f6(1 - uv[j][1] / b.atlas)}`); return `${vBase + id}/${tBase + vt.length - 1}/${nIndex.get(key)}`;
      }).join(' '));
    });
    out.push(...vt, ...vn, ...fl);
    vBase += p.v.length; tBase += vt.length; nBase += vn.length;
  }
  return out.join('\n') + '\n';
}
const mtlOf = (b, title) => `# AFL ${title}\nnewmtl ${b.id}\nKd 1 1 1\nmap_Kd apocalypse_firstlight:block/${b.id}\n`;
const objModel = file => ({loader: 'forge:obj', model: `apocalypse_firstlight:models/block/${file}.obj`, automatic_culling: false,
  flip_v: true, shade_quads: true, ambientocclusion: false, textures: {particle: 'apocalypse_firstlight:block/fuel_station_sump'}});
const DIRS = ['north', 'east', 'south', 'west'], ROT = {north: 0, east: 90, south: 180, west: 270};
const ref = (model, facing) => ({model: `apocalypse_firstlight:block/${model}`, ...(ROT[facing] ? {y: ROT[facing]} : {})});
const block = {gui: {rotation: [30, 225, 0], scale: [0.625, 0.625, 0.625]}, ground: {translation: [0, 3, 0], scale: [0.25, 0.25, 0.25]},
  fixed: {scale: [0.5, 0.5, 0.5]}, thirdperson_righthand: {rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [0.375, 0.375, 0.375]},
  firstperson_righthand: {rotation: [0, 45, 0], scale: [0.4, 0.4, 0.4]}, firstperson_lefthand: {rotation: [0, 225, 0], scale: [0.4, 0.4, 0.4]}};

// ---------------- write ----------------
const B = bake();
const bbDir = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const json = v => JSON.stringify(v, null, 2) + '\n';
const outputs = [], objs = [];
const model = (file, bones) => { const obj = objOf(B, file, file, bones);
  outputs.push([path.join(assets, `models/block/${file}.obj`), obj], [path.join(assets, `models/block/${file}.mtl`), mtlOf(B, file)], [path.join(assets, `models/block/${file}.json`), json(objModel(file))]);
  objs.push([file, obj]); };
outputs.push([path.join(bbDir, 'fuel_station_sump_v1.bbmodel'), JSON.stringify(sourceOf(B))],
  ...['', '_s', '_n'].flatMap((k, i) => [[path.join(bbDir, `textures/fuel_station_sump_v1${k}.png`), B.maps[i]], [path.join(assets, `textures/block/fuel_station_sump${k}.png`), B.maps[i]]]));
for (const piece of Object.keys(PIECES)) model(piece, [piece]);
model('pump_manhole_cover/item', ['pump_manhole_cover/frame', 'pump_manhole_cover/lid_closed']);
for (const g of ['gasoline', 'diesel']) model(`fuel_fill_cover/item_${g}`, [`fuel_fill_cover/frame_${g}`, 'fuel_fill_cover/lid_closed']);
// blockstates: SubmersibleFuelPumpBlock (facing = the outlet side), PumpManholeCoverBlock / FuelFillCoverBlock (facing = the
// hinge side toward the player who placed it, open)
outputs.push([path.join(assets, 'blockstates/submersible_fuel_pump.json'), json({variants: Object.fromEntries(DIRS.map(F => [`facing=${F}`, ref('submersible_fuel_pump/body', F)]))})]);
const covered = (frame, lid) => ({multipart: DIRS.flatMap(F => [
  {when: {facing: F}, apply: ref(frame, F)},
  {when: {facing: F, open: 'false'}, apply: ref(`${lid}/lid_closed`, F)},
  {when: {facing: F, open: 'true'}, apply: ref(`${lid}/lid_open`, F)}])});
outputs.push([path.join(assets, 'blockstates/pump_manhole_cover.json'), json(covered('pump_manhole_cover/frame', 'pump_manhole_cover'))]);
for (const g of ['gasoline', 'diesel']) outputs.push([path.join(assets, `blockstates/fuel_fill_cover_${g}.json`), json(covered(`fuel_fill_cover/frame_${g}`, 'fuel_fill_cover'))]);
outputs.push([path.join(assets, 'models/item/submersible_fuel_pump.json'), json({parent: 'apocalypse_firstlight:block/submersible_fuel_pump/body', gui_light: 'side', display: block})],
  [path.join(assets, 'models/item/pump_manhole_cover.json'), json({parent: 'apocalypse_firstlight:block/pump_manhole_cover/item', gui_light: 'side', display: block})],
  ...['gasoline', 'diesel'].map(g => [path.join(assets, `models/item/fuel_fill_cover_${g}.json`), json({parent: `apocalypse_firstlight:block/fuel_fill_cover/item_${g}`, gui_light: 'side', display: block})]));

// the dispenser sump: the master (lower A) draws the whole box; the other cells only carry the particle texture
outputs.push([path.join(assets, 'models/block/fuel_dispenser_sump/cell.json'), json({textures: {particle: 'apocalypse_firstlight:block/fuel_station_sump'}})],
  [path.join(assets, 'blockstates/fuel_dispenser_sump.json'), json({variants: Object.fromEntries(DIRS.flatMap(F => ['a0', 'b0', 'a1', 'b1'].map(c =>
    [`cell=${c},facing=${F}`, c === 'a0' ? ref('fuel_dispenser_sump/body', F) : {model: 'apocalypse_firstlight:block/fuel_dispenser_sump/cell'}])))})],
  // 2 x 2 x 1 cells: GUI translation = -R(30, 225) * scale * (0.5, 0.5, 0) blocks, in px (the box's centre on the slot's)
  [path.join(assets, 'models/item/fuel_dispenser_sump.json'), json({parent: 'apocalypse_firstlight:block/fuel_dispenser_sump/body', gui_light: 'side', display: {
    gui: {rotation: [30, 225, 0], translation: [1.7, -1.23, -2.67], scale: [0.3, 0.3, 0.3]},
    ground: {translation: [-1, 1, 0], scale: [0.15, 0.15, 0.15]},
    fixed: {translation: [-2.4, -2.4, 0], scale: [0.3, 0.3, 0.3]},
    thirdperson_righthand: {rotation: [75, 45, 0], translation: [0, 0.5, 0], scale: [0.2, 0.2, 0.2]},
    firstperson_righthand: {rotation: [0, 45, 0], translation: [0, -1, 0], scale: [0.2, 0.2, 0.2]},
    firstperson_lefthand: {rotation: [0, 225, 0], translation: [0, -1, 0], scale: [0.2, 0.2, 0.2]}}})]);

const tris = bone => B.PARTS.filter(p => p.bone === bone).reduce((s, p) => s + p.f.reduce((t, f) => t + f.ids.length - 2, 0), 0);
export const stats = {triangles: Object.fromEntries(Object.keys(PIECES).map(k => [k, tris(k)])), atlas: {texelsPerPx: B.UV.S, islands: B.UV.islands.length, coplanar: B.coplanar.length}};
const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  console.log(JSON.stringify(stats));
  if (B.coplanar.length) console.log('COPLANAR', JSON.stringify([...new Set(B.coplanar.map(c => c.a + ' | ' + c.b))].slice(0, 12)));
  const pi = process.argv.indexOf('--preview');
  if (pi > 0) {
    const dir = process.argv[pi + 1]; fs.mkdirSync(dir, {recursive: true});
    for (const [file, obj] of objs) fs.writeFileSync(path.join(dir, file.replace('/', '_') + '.obj'), obj);
    ['', '_s', '_n'].forEach((k, i) => fs.writeFileSync(path.join(dir, `${B.id}${k}.png`), B.maps[i]));
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
