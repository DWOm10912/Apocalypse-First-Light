// Charging Station V1 (first Pure Mesh machine): a 2 x 1 x 1 floor-standing steel charging bench. Items to be charged lie
// on a recessed rubber tray (two places, or one long item across both); the front panel carries two charge bar windows
// with readout screens (lit by the block entity renderer), a rack of ten cell bays with orange status lamps (station has
// power), and a rotary knob. Style of the existing AFL machines: medium grey steel casing, dark charcoal frame and legs,
// recessed panels, orange accents. Plain surfaces, no labels.
//   node tools/build-charging-station-v1.mjs --preview DIR   -> writes geo / sidecar / maps into DIR (offline review)
// Frame (px): the beverage cooler's convention: x -8..24 across both cells (master = the left cell, x 8..24, as the viewer
// faces the front), y 0..16, front toward -Z (z -8..8).
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {convert, serializeCompact} from './export-afl-mesh.mjs';
import {Part, AX, extrude, mul, area2, unwrap, paint, png, zFightLevels} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function assert(c, m) { if (!c) throw new Error(m); }
const PARTS = [], P = (name, mat) => { const p = new Part(name, 'body', mat); PARTS.push(p); return p; };
const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
const rect = (u0, v0, u1, v1) => [[u0, v0], [u1, v0], [u1, v1], [u0, v1]];
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

// ---------------- dimensions ----------------
export const X0 = -8, X1 = 24, LEG = 1.6;                           // overall width, end frame thickness
export const DESK = {y0: 5.0, y1: 12.6, z0: -6.6, z1: 7.4};         // cabinet under the tray
export const TRAY = {x0: -5.4, x1: 21.4, z0: -5.2, z1: 5.3, y: 13.0}; // rubber charging bed (top surface)
export const PANEL = {y0: 10.4, y1: 12.2};                           // control strip on the cabinet front
export const RACK = {y0: 5.6, y1: 9.8};                              // cell bays

{
  // end frames (legs + side cheeks), dark: full height at the back, sloping down toward the front above the tray
  const frame = P('end_frames', 'frame');
  for (const [a, b] of [[X0, X0 + LEG], [X1 - LEG, X1]])
    extrude(frame, 'x', {outer: orient([[-8, 0.3], [-5.6, 0.3], [-5.6, 4.2], [-8, 4.6], [-8, 13.6], [-6.4, 14.4], [6.6, 16], [8, 16], [8, 0.3], [6.4, 0.3], [6.4, 4.2], [-3.6, 4.2], [-3.6, 0.3]].map(([z, y]) => [z, y]), true), holes: []}, a, b, 0.2);
  // cabinet: grey steel box between the frames; back panel and a low stretcher at the back between the legs
  const casing = P('casing', 'casing');
  slab(casing, 'z', [X0 + LEG - 0.05, DESK.y0, DESK.z0], [X1 - LEG + 0.05, DESK.y1, DESK.z1], 0.25);
  slab(casing, 'z', [X0 + LEG - 0.05, DESK.y1 - 0.1, 5.6], [X1 - LEG + 0.05, 15.4, 7.6], 0.25);      // raised back wall
  slab(P('stretcher', 'frame'), 'z', [X0 + LEG - 0.1, 1.2, 5.2], [X1 - LEG + 0.1, 2.4, 6.4], 0.15);
  // tray: rubber bed recessed into a steel rim, front bumper rail (dark)
  // steel rim around the bed (its top 0.45 above the rubber), the bed sunk into it
  extrude(P('tray_rim', 'casing'), 'y', {outer: orient(rect(DESK.z0 - 0.2, X0 + LEG, 5.7, X1 - LEG), true), holes: [orient(rect(TRAY.z0, TRAY.x0, TRAY.z1, TRAY.x1), false)]},
    DESK.y1 - 0.05, TRAY.y + 0.45, 0.12);
  slab(P('tray_pad', 'pad'), 'y', [TRAY.x0 + 0.02, DESK.y1 - 0.02, TRAY.z0 + 0.02], [TRAY.x1 - 0.02, TRAY.y, TRAY.z1 - 0.02], 0.08);
  cyl(P('bumper', 'frame'), 'x', DESK.z0 - 0.3, DESK.y1 + 0.25, 0.75, X0 + LEG + 0.05, X1 - LEG - 0.05, 10);
  // control strip: recessed dark panel with two charge bar windows + readout screens, knob, small lamps
  slab(P('panel_recess', 'inset'), 'z', [X0 + LEG + 0.6, PANEL.y0, DESK.z0 - 0.06], [X1 - LEG - 0.6, PANEL.y1, DESK.z0 + 0.2], 0);
  const glass = P('readouts', 'screen');
  for (const cx of [0, 16]) {
    slab(glass, 'z', [cx - 4.6, PANEL.y0 + 0.45, DESK.z0 - 0.12], [cx + 2.2, PANEL.y1 - 0.45, DESK.z0 - 0.04], 0);     // charge bar window
    slab(glass, 'z', [cx + 2.8, PANEL.y0 + 0.3, DESK.z0 - 0.12], [cx + 5.4, PANEL.y1 - 0.3, DESK.z0 - 0.04], 0);      // % readout
  }
  const knob = P('knob', 'knob');
  cyl(knob, 'z', -5.6 + 0.2, 11.3, 0.62, DESK.z0 - 0.7, DESK.z0 - 0.04, 12);
  // cell rack: ten bays in a dark recess, each with a lamp lens
  slab(P('rack_recess', 'rack'), 'z', [X0 + LEG + 0.6, RACK.y0, DESK.z0 - 0.06], [X1 - LEG - 0.6, RACK.y1, DESK.z0 + 0.4], 0);
  const cells = P('rack_cells', 'cell'), lamps = P('rack_lamps', 'lamp');
  for (let i = 0; i < 10; i++) {
    const x0 = X0 + LEG + 1.0 + i * 2.66;
    slab(cells, 'z', [x0, RACK.y0 + 0.35, DESK.z0 - 0.12], [x0 + 2.36, RACK.y1 - 0.35, DESK.z0 - 0.02], 0);
    cyl(lamps, 'z', x0 + 1.18, RACK.y0 + 1.15, 0.36, DESK.z0 - 0.3, DESK.z0 - 0.1, 8);
  }
  // feet pads under the legs
  const feet = P('feet', 'rubber');
  for (const x of [X0 + LEG / 2, X1 - LEG / 2]) for (const [z0, z1] of [[-8, -5.6], [6.4, 8]]) slab(feet, 'y', [x - 0.9, 0, z0 + 0.1], [x + 0.9, 0.36, z1 - 0.1], 0);
}

export const MATS = {   // Base Color, bevel highlight, smoothness open / edge, F0
  casing: {c: [118, 122, 128], hl: 10, sm: 96, se: 118, f0: 20},     // painted steel, AFL machine grey
  frame:  {c: [52, 55, 60], hl: 10, sm: 92, se: 116, f0: 20},        // charcoal frames, legs, bumper
  inset:  {c: [86, 90, 96], hl: 6, sm: 90, se: 104, f0: 20},
  rack:   {c: [34, 36, 40], hl: 4, sm: 84, se: 96, f0: 20},
  cell:   {c: [70, 74, 80], hl: 8, sm: 96, se: 112, f0: 20},
  lamp:   {c: [210, 122, 44], hl: 6, sm: 170, se: 170, f0: 20},     // orange lamp lenses (unlit here)
  screen: {c: [16, 20, 22], hl: 2, sm: 200, se: 200, f0: 20},
  knob:   {c: [40, 42, 46], hl: 10, sm: 110, se: 130, f0: 20},
  pad:    {c: [30, 31, 34], hl: 2, sm: 48, se: 56, f0: 20},         // rubber charging bed
  rubber: {c: [26, 26, 28], hl: 2, sm: 40, se: 48, f0: 20},
};

const ATLAS = 512, PAD = 2;
const UV = unwrap(PARTS, {atlas: ATLAS, pad: PAD, startS: 24, stepS: 0.25});
for (const p of PARTS) assert(p.f.every(f => UV.faceUV.get(p)?.has(f)), 'unmapped face in ' + p.name);
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
const painted = paint({PARTS, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas: ATLAS, pad: PAD, MATS, ZONED: new Set(), groupInfo: new Map(),
  sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [...MATS.casing.c, 255], s: [96, 20, 0, 255], n: [128, 128, 255, 255]}});

const ID = 'charging_station';
const uuid = s => { const h = createHash('sha256').update('afl-charging-station-v1:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r12 = v => +v.toFixed(12) || 0;
const SHIFT = -16;   // master-centred output, like the cooler
for (const p of PARTS) p.v = p.v.map(q => [q[0] + SHIFT, q[1], q[2]]);
const source = (() => {
  const elements = PARTS.map(p => {
    const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {}, uvs = UV.faceUV.get(p);
    p.v.forEach((q, i) => { vertices[key(i)] = q.map(r12); });
    p.f.forEach((f, fi) => { const uv = uvs.get(f); faces['f' + key(fi)] = {uv: Object.fromEntries(f.ids.map((id, j) => [key(id), uv[j].map(r12)])), vertices: f.ids.map(key), texture: 0}; });
    return {name: p.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: true, locked: false,
      render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: uuid('mesh:' + p.name)};
  });
  return {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: ID, model_identifier: '', visible_box: [2, 1, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: ATLAS, height: ATLAS},
    elements, groups: [{name: 'body', uuid: uuid('group'), export: true, locked: false, scope: 0, selected: false, visibility: true,
      _static: {properties: {}, temp_data: {}}, origin: [0, 0, 0], rotation: [0, 0, 0], color: 0, children: [], reset: false, shade: true,
      mirror_uv: false, autouv: 0, isOpen: true, primary_selected: false}],
    outliner: [{uuid: uuid('group'), isOpen: true, children: elements.map(e => e.uuid)}],
    textures: [{name: ID + '.png', relative_path: `textures/${ID}.png`, folder: '', namespace: '', id: '0', group: '', scope: 0,
      width: ATLAS, height: ATLAS, uv_width: ATLAS, uv_height: ATLAS, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
      file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
      frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture'),
      source: 'data:image/png;base64,' + painted.PNG[0].toString('base64')}],
    animations: []};
})();
const geo = {format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: 'geometry.' + ID, texture_width: ATLAS, texture_height: ATLAS,
  visible_bounds_width: 3, visible_bounds_height: 2, visible_bounds_offset: [0, 0.5, 0]}, bones: [{name: 'body', pivot: [0, 0, 0]}]}]};
const sidecar = convert(source, geo, {}, ID + '.bbmodel', 2);
const zf = zFightLevels(PARTS, new Map());
const all = PARTS.flatMap(p => p.v);
export const stats = {triangles: sidecar.parts.flatMap(p => p.faces).reduce((s, q) => s + q.length - 2, 0), parts: sidecar.parts.length, texelsPerPx: UV.S,
  islands: UV.islands.length, coplanarOverlaps: zf.unresolved.length, bounds: [0, 1, 2].map(k => [+Math.min(...all.map(q => q[k])).toFixed(2), +Math.max(...all.map(q => q[k])).toFixed(2)])};

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  console.log(JSON.stringify(stats));
  if (zf.unresolved.length) console.log('COPLANAR', JSON.stringify(zf.unresolved.slice(0, 12)));
  const pi = process.argv.indexOf('--preview');
  assert(pi > 0, 'V1 is preview-only so far: pass --preview DIR');
  const dir = process.argv[pi + 1]; fs.mkdirSync(dir, {recursive: true});
  fs.writeFileSync(path.join(dir, `${ID}.geo.json`), JSON.stringify(geo, null, 2) + '\n');
  fs.writeFileSync(path.join(dir, `${ID}.aflmesh.json`), serializeCompact(sidecar));
  fs.writeFileSync(path.join(dir, `${ID}.bbmodel`), JSON.stringify(source));
  ['', '_s', '_n'].forEach((k, i) => fs.writeFileSync(path.join(dir, `${ID}${k}.png`), painted.PNG[i]));
  console.log('preview written to ' + dir);
}
