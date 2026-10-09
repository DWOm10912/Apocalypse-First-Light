// Underground Fuel Tank V1 (docs/models/underground_fuel_tank_v1.md): a modern double-walled FRP (fibreglass) tank buried
// under a fuel station forecourt, fixed size, 3 x 3 x 7 blocks: a ribbed cylinder 2.4 m across with dished heads; at the
// top centre a manway (an FRP collar under a bolted round steel lid) carries a square steel riser ending in the AFL fluid
// port (tools/afl-fluid-port.mjs) on the top face of the top centre cell, where a Fluid Pipe V2 comes down. One fuel per
// tank: the gasoline tank has red label plates and a red band on the collar, the diesel tank yellow ones.
// Pure Mesh + LabPBR atlas exported as Forge OBJ (static, chunk-baked, drawn by the port cell).
//   node tools/build-underground-fuel-tank-v1.mjs                -> writes source, OBJ / MTL / models, blockstates, atlas
//   node tools/build-underground-fuel-tank-v1.mjs --check        -> verifies every output is up to date
//   node tools/build-underground-fuel-tank-v1.mjs --preview DIR  -> writes only OBJ + maps into DIR (offline review)
// Frame (px): the port cell's centre is the origin (the tank runs along z, the canonical axis=z); the tank's axis lies at
// y = -20, the footprint is x -24..24, y -40..8, z -56..56. OBJ in block units: x = px / 16 + 0.5 etc. (the port cell's).
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {Part, extrude, add, sub, dot, cross, norm, newell, area2, unwrap, paint, png, zFightLevels} from './cube-slab-mesh-lib.mjs';
import {addFluidPort} from './afl-fluid-port.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function assert(c, m) { if (!c) throw new Error(m); }
const D2R = Math.PI / 180;

// ---------------- primitives ----------------
const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
const rect = (u0, v0, u1, v1) => [[u0, v0], [u1, v0], [u1, v1], [u0, v1]];
const shape = (L, holes = []) => ({outer: orient(L, true), holes: holes.map(h => orient(h, false))});
const zx = L => L.map(([x, z]) => [z, x]);
const planY = (part, L, y0, y1, c = 0) => extrude(part, 'y', shape(zx(L)), y0, y1, c);   // plan (x, z)
const hex = (cx, cy, r) => Array.from({length: 6}, (_, i) => { const a = (30 + 60 * i) * D2R; return [cx + r * Math.cos(a), cy + r * Math.sin(a)]; });
// a surface of revolution: profile [[s, r]] along the axis; axis 'z' about (cx, cy) = (x, y), axis 'y' about (x, z)
function lathe(part, axis, c, profile, seg, caps = [false, false], phase = 0.5) {
  const at = (s, r, i) => { const a = 2 * Math.PI * (i + phase) / seg, u = r * Math.cos(a), v = r * Math.sin(a);
    return axis === 'z' ? [c[0] + u, c[1] + v, s] : [c[0] + u, s, c[1] + v]; };
  const ring = ([s, r]) => Array.from({length: seg}, (_, i) => part.vtx(at(s, r, i)));
  const rings = profile.map(ring), out = (i, n) => { const a = 2 * Math.PI * (i + phase) / seg; return axis === 'z' ? [Math.cos(a) * n[0], Math.sin(a) * n[0], n[1]] : [Math.cos(a) * n[0], n[1], Math.sin(a) * n[0]]; };
  for (let k = 0; k + 1 < profile.length; k++) for (let i = 0; i < seg; i++) {
    const j = (i + 1) % seg, [s0, r0] = profile[k], [s1, r1] = profile[k + 1];
    const n = [s1 - s0, r0 - r1];   // (radial, axial) of the outward normal
    part.face([rings[k][i], rings[k][j], rings[k + 1][j], rings[k + 1][i]], out(i + 0.5, Math.hypot(...n) > 1e-9 ? n : [1, 0]));
  }
  [[0, -1], [profile.length - 1, 1]].forEach(([k, sgn], ci) => { if (!caps[ci]) return; const m = part.vtx(axis === 'z' ? [c[0], c[1], profile[k][0]] : [c[0], profile[k][0], c[1]]);
    for (let i = 0; i < seg; i++) part.face([m, rings[k][i], rings[k][(i + 1) % seg]], axis === 'z' ? [0, 0, sgn] : [0, sgn, 0], 'cap'); });
}
// a curved plate on the cylinder (axis z through (0, cy)): radii r0..r1, angles a0..a1 (degrees), z0..z1
function arcPlate(part, cy, r0, r1, a0, a1, z0, z1, n) {
  const P = (r, k, z) => { const a = (a0 + (a1 - a0) * k / n) * D2R; return part.vtx([r * Math.cos(a), cy + r * Math.sin(a), z]); };
  for (let k = 0; k < n; k++) {
    const m = (a0 + (a1 - a0) * (k + 0.5) / n) * D2R, o = [Math.cos(m), Math.sin(m), 0];
    part.face([P(r1, k, z0), P(r1, k + 1, z0), P(r1, k + 1, z1), P(r1, k, z1)], o);
    for (const [z, s] of [[z0, -1], [z1, 1]]) part.face([P(r0, k, z), P(r0, k + 1, z), P(r1, k + 1, z), P(r1, k, z)], [0, 0, s]);
  }
  for (const [k, a] of [[0, a0], [n, a1]]) { const t = [-Math.sin(a * D2R), Math.cos(a * D2R), 0].map(v => v * (k ? 1 : -1));
    part.face([P(r0, k, z0), P(r1, k, z0), P(r1, k, z1), P(r0, k, z1)], t); }
}

// ---------------- textured base colours (low-frequency only) ----------------
const hash = (a, b, c) => { let h = Math.imul(a | 0, 374761393) ^ Math.imul(b | 0, 668265263) ^ Math.imul(c | 0, 2147483647); h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };
function vn(x, y, z) {
  const xi = Math.floor(x), yi = Math.floor(y), zi = Math.floor(z), s = t => t * t * (3 - 2 * t), l = (a, b, t) => a + (b - a) * t;
  const sx = s(x - xi), sy = s(y - yi), sz = s(z - zi), h = (a, b, c) => hash(a, b, c * 7919 + 101);
  return l(l(l(h(xi, yi, zi), h(xi + 1, yi, zi), sx), l(h(xi, yi + 1, zi), h(xi + 1, yi + 1, zi), sx), sy),
    l(l(h(xi, yi, zi + 1), h(xi + 1, yi, zi + 1), sx), l(h(xi, yi + 1, zi + 1), h(xi + 1, yi + 1, zi + 1), sx), sy), sz);
}
const fbm = (x, y, z) => 0.6 * vn(x, y, z) + 0.3 * vn(x * 2.03 + 7.1, y * 2.03 + 3.3, z * 2.03 + 1.7) + 0.1 * vn(x * 4.1 + 2.9, y * 4.1 + 8.1, z * 4.1 + 5.3);
const tone = (c, k) => c.map(v => v * k), mott = (p, s, k) => k * (fbm(p[0] * s, p[1] * s, p[2] * s) - 0.5);
export const MATS = {
  frp:      {c: p => tone([178, 150, 100], 1 + mott(p, 0.12, 0.06) + 0.025 * (vn(p[0] * 0.6, p[1] * 0.6, p[2] * 0.05) - 0.5)), hl: 6, sm: 116, se: 136, f0: 24},   // fibreglass resin skin, a faint winding grain along the axis
  rib:      {c: p => tone([166, 138, 90], 1 + mott(p, 0.15, 0.05)), hl: 7, sm: 112, se: 134, f0: 24},
  lid:      {c: p => tone([62, 66, 72], 1 + mott(p, 0.3, 0.04)), hl: 9, sm: 116, se: 140, f0: 26},     // coated steel (Fluid Pipe V2's flange steel)
  riser:    {c: p => tone([46, 49, 54], 1 + mott(p, 0.3, 0.04)), hl: 8, sm: 108, se: 132, f0: 24},     // Fluid Pipe V2's rail steel
  bolt:     {c: [124, 128, 133], hl: 7, sm: 124, se: 140, f0: 30},
  lug:      {c: p => tone([62, 66, 72], 1 + mott(p, 0.3, 0.04)), hl: 9, sm: 116, se: 140, f0: 26},
  portPlate:  {c: p => tone([62, 66, 72], 1 + mott(p, 0.3, 0.04)), hl: 10, sm: 120, se: 144, f0: 26}, // the AFL fluid port (pipe flange steel)
  portThroat: {c: [24, 25, 27], hl: 0, sm: 60, se: 60, f0: 20},
  portStud:   {c: [124, 128, 133], hl: 7, sm: 124, se: 140, f0: 30},
  gasoline: {c: [166, 46, 38], hl: 6, sm: 150, se: 160, f0: 24},    // enamelled label: gasoline red
  diesel:   {c: [204, 158, 34], hl: 6, sm: 150, se: 160, f0: 24},   // diesel yellow
};

// ---------------- dimensions (px) ----------------
export const TANK = {cy: -20, r: 19, straight: 47, end: 55.9, seg: 32, ribs: [11, 22, 33, 44], rib: [1.5, 1.3]};   // rib: half width, height
// the fill port (2026-10-05): a second AFL port on the top face of the end cell (along 6, z +48 px), where the fill riser
// comes up; a fluid pipe takes it up to the fill cover at the forecourt (tools/build-fuel-station-sump-v1.mjs)
export const FILL = {z: 48, collar: [3.4, -2.6, 0.6], riser: [2.6, 0.6, 7.4], band: [3.6, 5.2]};
export const MANWAY = {collar: [8, -2.5, 2.0], lid: [9.6, 2.0, 3.2], bolts: [8.8, 12], flange: [5.8, 3.2, 3.9], riser: [4.2, 3.9, 7.4], band: [0.2, 1.2]};
export const LABEL = {z: [24.2, 30.8], half: 14};   // between the ribs at 22 and 33, +-14 degrees round the side
export const CAPACITY_MB = 30000;   // UndergroundFuelTankBlockEntity: about the tank's volume (2.4 m x ~6.4 m)

function tank(P) {
  const T = TANK, shell = P('shell', 'frp');
  lathe(shell, 'z', [0, T.cy], [[-T.straight, T.r], [T.straight, T.r]], T.seg);
  // dished (elliptical) heads, 8.9 px deep, in ten rings
  const D = T.end - T.straight, head = Array.from({length: 11}, (_, i) => { const t = i / 10 * Math.PI / 2; return [D * Math.sin(t), i === 10 ? 0 : T.r * Math.cos(t)]; });
  lathe(shell, 'z', [0, T.cy], head.map(([s, r]) => [T.straight + s, r]), T.seg);
  lathe(shell, 'z', [0, T.cy], head.map(([s, r]) => [-T.straight - s, r]).reverse(), T.seg);
  const ribs = P('ribs', 'rib'), [w, h] = T.rib;
  for (const z0 of T.ribs) for (const z of [z0, -z0])
    lathe(ribs, 'z', [0, T.cy], [[z - w, T.r], [z - w, T.r + h - 0.4], [z - w + 0.4, T.r + h], [z + w - 0.4, T.r + h], [z + w, T.r + h - 0.4], [z + w, T.r]], T.seg);
  // lifting lugs on the crown near the ends
  for (const z of [-38.5, 38.5]) planY(P('lug' + (z > 0 ? 'S' : 'N'), 'lug'), rect(-0.6, z - 2.2, 0.6, z + 2.2), T.cy + T.r - 0.6, T.cy + T.r + 2.6, 0.1);
  // manway: FRP collar, bolted round steel lid, square steel riser on a bolted base flange, the AFL fluid port on top
  const M = MANWAY;
  lathe(P('collar', 'frp'), 'y', [0, 0], [[M.collar[1], M.collar[0]], [M.collar[2], M.collar[0]]], 24, [false, false]);
  lathe(P('lid', 'lid'), 'y', [0, 0], [[M.lid[1], 0], [M.lid[1], M.lid[0]], [M.lid[2] - 0.35, M.lid[0]], [M.lid[2], M.lid[0] - 0.35], [M.lid[2], 0]], 24);
  const nuts = P('lid_bolts', 'bolt');
  for (let k = 0; k < M.bolts[1]; k++) { const a = (15 + 360 * k / M.bolts[1]) * D2R;
    planY(nuts, hex(M.bolts[0] * Math.cos(a), M.bolts[0] * Math.sin(a), 0.42), M.lid[2], M.lid[2] + 0.34, 0); }
  planY(P('base_flange', 'lid'), rect(-M.flange[0], -M.flange[0], M.flange[0], M.flange[0]), M.flange[1], M.flange[2], 0.08);
  const fnuts = P('flange_bolts', 'bolt');
  for (const sx of [-1, 1]) for (const sz of [-1, 1]) planY(fnuts, hex(sx * 5.0, sz * 5.0, 0.4), M.flange[2], M.flange[2] + 0.32, 0);
  planY(P('riser', 'riser'), rect(-M.riser[0], -M.riser[0], M.riser[0], M.riser[0]), M.riser[1], M.riser[2], 0.06);
  const port = {plate: P('port_plate', 'portPlate'), throat: P('port_throat', 'portThroat'), studs: P('port_studs', 'portStud')};
  addFluidPort(port, 0, 0, M.riser[2] - 8, 0);   // built facing +Z with the mating face at z 0, turned to face up at y 8
  for (const p of Object.values(port)) p.v = p.v.map(([x, y, z]) => [x, z + 8, -y]);
  // the fill riser and its port on the end cell's top face
  const F = FILL;
  lathe(P('fill_collar', 'frp'), 'y', [0, F.z], [[F.collar[1], F.collar[0]], [F.collar[2], F.collar[0]]], 20, [false, true]);
  lathe(P('fill_riser', 'lid'), 'y', [0, F.z], [[F.riser[1], F.riser[0]], [F.riser[2], F.riser[0]]], 16, [false, false]);
  planY(P('fill_neck', 'riser'), rect(-4.2, F.z - 4.2, 4.2, F.z + 4.2), M.riser[2] - 0.6, M.riser[2], 0.06);
  const fill = {plate: P('fill_plate', 'portPlate'), throat: P('fill_throat', 'portThroat'), studs: P('fill_studs', 'portStud')};
  addFluidPort(fill, 0, -F.z, M.riser[2] - 8, 0);
  for (const p of Object.values(fill)) p.v = p.v.map(([x, y, z]) => [x, z + 8, -y]);
}
function labels(P, fuel) {   // colour label plates on both sides between two ribs, a colour band round the collar
  const T = TANK, L = LABEL, part = P('label', fuel);
  for (const side of [0, 180]) for (const s of [-1, 1]) arcPlate(part, T.cy, T.r - 0.05, T.r + 0.3, side - L.half, side + L.half, s > 0 ? L.z[0] : -L.z[1], s > 0 ? L.z[1] : -L.z[0], 4);
  lathe(P('fill_band', fuel), 'y', [0, FILL.z], [[FILL.band[0], FILL.riser[0]], [FILL.band[0], FILL.riser[0] + 0.25], [FILL.band[1], FILL.riser[0] + 0.25], [FILL.band[1], FILL.riser[0]]], 16);
  lathe(P('band', fuel), 'y', [0, 0], [[MANWAY.band[0], MANWAY.collar[0]], [MANWAY.band[0], MANWAY.collar[0] + 0.25], [MANWAY.band[1], MANWAY.collar[0] + 0.25], [MANWAY.band[1], MANWAY.collar[0]]], 24);
}
export const FUELS = ['gasoline', 'diesel'];
export const PIECES = {tank: P => tank(P), label_gasoline: P => labels(P, 'gasoline'), label_diesel: P => labels(P, 'diesel')};
const GROUPS = [['tank', 'label_gasoline'], ['tank', 'label_diesel']];

// ---------------- bake: UV, LabPBR maps ----------------
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
function bake() {
  const PARTS = [];
  for (const [piece, build] of Object.entries(PIECES)) build((name, mat) => { const p = new Part(`${piece}_${name}`, piece, mat); PARTS.push(p); return p; });
  const live = PARTS.filter(p => p.f.length);
  for (const p of live) {   // Forge draws a quad as 0-1-2 / 2-3-0: split anything that is not a triangle or a strictly convex quad
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
  const atlas = 2048, UV = unwrap(live, {atlas, pad: 2, startS: 12, stepS: 0.25});
  for (const p of live) assert(p.f.every(f => UV.faceUV.get(p)?.has(f)), `unmapped face in ${p.name}`);
  const painted = paint({PARTS: live, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas, pad: 2, MATS, ZONED: new Set(), groupInfo: new Map(),
    sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [178, 150, 100, 255], s: [116, 24, 0, 255], n: [128, 128, 255, 255]}});
  const coplanar = GROUPS.flatMap(g => zFightLevels(live.filter(p => g.includes(p.bone)), new Map()).unresolved);
  return {id: 'underground_fuel_tank', PARTS: live, atlas, UV, maps: painted.PNG, coplanar};
}

const uuidOf = (ns, s) => { const h = createHash('sha256').update(`afl-underground-fuel-tank-v1:${ns}:${s}`).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r12 = v => +v.toFixed(12) || 0;
function sourceOf(b) {   // editable Free Model source (frame as the header, px): one group per piece
  const uuid = s => uuidOf(b.id, s), name = b.id + '_v1', bones = [...new Set(b.PARTS.map(p => p.bone))];
  const elements = b.PARTS.map(p => {
    const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {}, uvs = b.UV.faceUV.get(p);
    p.v.forEach((q, i) => { vertices[key(i)] = q.map(r12); });
    p.f.forEach((f, fi) => { const uv = uvs.get(f); faces['f' + key(fi)] = {uv: Object.fromEntries(f.ids.map((id, j) => [key(id), uv[j].map(r12)])), vertices: f.ids.map(key), texture: 0}; });
    return {name: p.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: p.bone !== 'label_diesel', locked: false,
      render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: uuid('mesh:' + p.name)};
  });
  return {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name, model_identifier: '', visible_box: [1, 1, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: b.atlas, height: b.atlas},
    elements, groups: bones.map(bone => ({name: bone, uuid: uuid('group:' + bone), export: true, locked: false, scope: 0,
      selected: false, visibility: bone !== 'label_diesel', _static: {properties: {}, temp_data: {}}, origin: [0, 0, 0], rotation: [0, 0, 0],
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
const SMOOTH = Math.cos(30 * D2R);   // the 32-sided shell, collar and lid shade round
function cornerNormals(V, F) {
  const fn = F.map(f => newell(f.ids.map(i => V[i]))), byV = new Map();
  F.forEach((f, k) => f.ids.forEach(i => (byV.get(i) || byV.set(i, []).get(i)).push(k)));
  return F.map((f, k) => { const n0 = norm(fn[k]); return f.ids.map(i => norm(byV.get(i).reduce((a, j) => dot(norm(fn[j]), n0) >= SMOOTH ? add(a, fn[j]) : a, [0, 0, 0]))); });
}
const toCell = q => q.map(v => v / 16 + 0.5);
function objOf(b, title, file, bones) {
  const out = [`# AFL ${title}, generated by tools/build-underground-fuel-tank-v1.mjs`, 'mtllib underground_fuel_tank.mtl'];
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
const objModel = file => ({loader: 'forge:obj', model: `apocalypse_firstlight:models/block/${file}.obj`, automatic_culling: false,
  flip_v: true, shade_quads: true, ambientocclusion: false, textures: {particle: 'apocalypse_firstlight:block/underground_fuel_tank'}});
const r3 = v => +v.toFixed(3) || 0;
const S3 = v => [v, v, v];
function guiCentred(points, rotation, scale) {
  const [ax, ay] = rotation.map(v => v * D2R);
  const rot = q => { const x = q[0] * Math.cos(ay) + q[2] * Math.sin(ay), z = -q[0] * Math.sin(ay) + q[2] * Math.cos(ay); return [x, q[1] * Math.cos(ax) - z * Math.sin(ax)]; };
  const pts = points.map(q => rot([q[0] - 8, q[1] - 8, q[2] - 8]));
  const cx = (Math.max(...pts.map(q => q[0])) + Math.min(...pts.map(q => q[0]))) / 2, cy = (Math.max(...pts.map(q => q[1])) + Math.min(...pts.map(q => q[1]))) / 2;
  return {rotation, translation: [r3(-scale * cx), r3(-scale * cy), 0], scale: S3(scale)};
}
function display(b, bones) {
  const pts = b.PARTS.filter(p => bones.includes(p.bone)).flatMap(p => p.v.map(q => toCell(q).map(v => v * 16)));
  return {gui: guiCentred(pts, [30, 225, 0], 0.13), ground: {translation: [0, 2, 0], scale: S3(0.06)}, fixed: {rotation: [0, 90, 0], scale: S3(0.12)},
    thirdperson_righthand: {rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: S3(0.08)}, thirdperson_lefthand: {rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: S3(0.08)},
    firstperson_righthand: {rotation: [0, 45, 0], scale: S3(0.1)}, firstperson_lefthand: {rotation: [0, 225, 0], scale: S3(0.1)}};
}

// ---------------- write ----------------
const B = bake();
const bbDir = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const json = v => JSON.stringify(v, null, 2) + '\n';
const outputs = [], objs = [];
outputs.push([path.join(bbDir, 'underground_fuel_tank_v1.bbmodel'), JSON.stringify(sourceOf(B))],
  ...['', '_s', '_n'].flatMap((k, i) => [[path.join(bbDir, `textures/underground_fuel_tank_v1${k}.png`), B.maps[i]], [path.join(assets, `textures/block/underground_fuel_tank${k}.png`), B.maps[i]]]),
  [path.join(assets, 'models/block/underground_fuel_tank/underground_fuel_tank.mtl'), `# AFL Underground Fuel Tank V1\nnewmtl ${B.id}\nKd 1 1 1\nmap_Kd apocalypse_firstlight:block/underground_fuel_tank\n`],
  [path.join(assets, 'models/block/underground_fuel_tank/cell.json'), json({textures: {particle: 'apocalypse_firstlight:block/underground_fuel_tank'}})]);
for (const fuel of FUELS) {
  const file = `underground_fuel_tank/${fuel}`, bones = ['tank', 'label_' + fuel], obj = objOf(B, `Underground Fuel Tank V1 (${fuel})`, file, bones);
  outputs.push([path.join(assets, `models/block/${file}.obj`), obj], [path.join(assets, `models/block/${file}.json`), json(objModel(file))],
    [path.join(assets, `models/item/underground_fuel_tank_${fuel}.json`), json({parent: `apocalypse_firstlight:block/${file}`, gui_light: 'side', display: display(B, bones)})]);
  objs.push([file, obj]);
  // UndergroundFuelTankBlock: axis, flipped, along 0..6, across 0..2, level 0..2; the port cell (3, 1, 2) draws the tank.
  // The model's fill end is +z: +along SOUTH (z) y 0, EAST (x) y 270, NORTH (z flipped) y 180, WEST (x flipped) y 90.
  const turns = [['z', 'false', 0], ['x', 'false', 270], ['z', 'true', 180], ['x', 'true', 90]];
  outputs.push([path.join(assets, `blockstates/underground_fuel_tank_${fuel}.json`), json({multipart: [
    {apply: {model: 'apocalypse_firstlight:block/underground_fuel_tank/cell'}},
    ...turns.map(([axis, flipped, y]) => ({when: {axis, flipped, along: '3', across: '1', level: '2'},
      apply: {model: `apocalypse_firstlight:block/${file}`, ...(y ? {y} : {})}}))]})]);
}
const tris = bone => B.PARTS.filter(p => p.bone === bone).reduce((s, p) => s + p.f.reduce((t, f) => t + f.ids.length - 2, 0), 0);
export const stats = {triangles: Object.fromEntries(Object.keys(PIECES).map(k => [k, tris(k)])), texelsPerPx: B.UV.S, islands: B.UV.islands.length,
  coplanar: B.coplanar.length, gui: display(B, ['tank']).gui.translation};
const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  console.log(JSON.stringify(stats));
  if (B.coplanar.length) console.log('COPLANAR', JSON.stringify([...new Set(B.coplanar.map(c => c.a + ' | ' + c.b))]));
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
