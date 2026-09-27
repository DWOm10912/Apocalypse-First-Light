// P9-01 V2 AFL Native Rig: runs the Pure Mesh generator (tools/build-p9-01-v2-mesh.mjs: geometry, UV atlas and V3 Base
// Color), re-parents its meshes into the AFL native gun hierarchy used by Blackridge .50, and writes the Free Model
// source plus its texture (src/main/blockbench/textures/p9_01_v2_native.png). The legacy p9_01.bbmodel is not read.
// Animations are added afterwards by tools/author-p9-01-v2-native-animations.mjs.
//   node tools/build-p9-01-v2-native.mjs            -> writes src/main/blockbench/p9_01_v2_native.bbmodel (no animations)
// Coordinates: Blockbench source units, muzzle -Z, +X = ejection side, same bind space as the legacy P9 meshes.
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import zlib from 'node:zlib';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const generatorPath = path.join(root, 'tools/build-p9-01-v2-mesh.mjs');
export const texturePath = path.join(root, 'src/main/blockbench/textures/p9_01_v2_native.png');
export const nativePath = path.join(root, 'src/main/blockbench/p9_01_v2_native.bbmodel');

// ---------- rig facts (shared with the animation author) ----------
export const FP_SCALE = 0.53;                                  // first-person Display scale of the native P9
export const ARM_HALF = 4 * 0.62 / FP_SCALE / 2;               // Vanilla arm half width after presentation scale, model units
export const ARM_LEN = 12 * 0.78 / FP_SCALE;                   // forearm length, model units
export const MP = [0, 3.5992, 1.647];                          // magazine pivot (top of the magazine, on the magazine axis)
export const MAG_TILT = -22;
export const HP = [0, 3.0, 2.06];                              // handling pivot = right grip (upper grip, on the grip axis)
export const RP = [0, 0, 0], LP = [0, 0, 0];                   // hand chain pivots
export const LA = [-1.5, 4.0, 0.5];                            // left_hand_anchor rest origin (any rigid point; solved per frame)
const AXIS_Y = 5.3752, MUZZLE_Z = -7.233;

const uuid = s => { const h = createHash('sha256').update('p9-01-v2-native:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };

// name, origin, rotation, children, {export:false} ; order = outliner order
const G = (name, origin, children = [], opt = {}) => ({name, origin, rotation: opt.rotation || [0, 0, 0], children, export: opt.export !== false});
export const RIG = G('root', [0, 0, 0], [
  G('handling', HP, [
    G('gun_body', [0, 0, 0], [
      G('barrel', [0, AXIS_Y, -1.6], [
        G('muzzle_anchor', [0, AXIS_Y, MUZZLE_Z]),
        G('chamber_round_anchor', [0, AXIS_Y, -0.44]),
      ]),
      G('trigger', [0, 4.04318, -0.60261]),
    ]),
    G('slide', [0, AXIS_Y, 0], [
      G('front_sight', [0, 5.96718, -6.22661]),
      G('rear_sight', [0, 5.96718, 2.94939]),
      G('ejection_anchor', [0.8399, 5.64158, -0.66181]),
      G('sight_anchor', [0, 6.29278, -6.22661]),
    ]),
    G('magazine', MP, [
      G('follower', MP),
      G('magazine_round_anchor', [0, 3.39381, 1.85725], [], {rotation: [MAG_TILT, 0, 0]}),
    ]),
    G('righthand', RP, [
      G('right_hand_anchor', HP, [G('right_arm_reference', HP, [], {export: false})]),
    ]),
  ]),
  G('lefthand', LP, [
    G('lefthand_pos', LP, [
      G('left_hand_anchor', LA, [G('left_arm_reference', LA, [], {export: false})]),
    ]),
  ]),
  G('mag_out', MP, [
    G('reload_magazine', MP, [G('mag_out_round_anchor', [0, 3.39381, 1.85725], [], {rotation: [MAG_TILT, 0, 0]})]),
  ]),
  G('empty_old_mag', MP, [G('empty_old_mag_round_anchor', [0, 3.39381, 1.85725], [], {rotation: [MAG_TILT, 0, 0]})]),
  G('positioning', [0, 0, 0]),
  G('maintenance_anchor', [0, 4.6, -1.6]),
]);
export const CAMERA = G('camera', [0, 12, 18]);

// generator mesh name -> native bone (magazine meshes are additionally copied into mag_out/reload_magazine and empty_old_mag)
function boneFor(name) {
  if (name === 'front_sight_post') return 'front_sight';
  if (name === 'rear_sight_body') return 'rear_sight';
  if (name.startsWith('slide_')) return 'slide';
  if (name.startsWith('barrel_')) return 'barrel';
  if (name === 'trigger_blade') return 'trigger';
  if (name.startsWith('frame_')) return 'gun_body';
  if (name === 'magazine_body' || name === 'magazine_baseplate') return 'magazine';
  if (name === 'magazine_follower') return 'follower';
  return null;   // generator *_reload duplicates (legacy rig) are replaced by the helper copies below
}

// The AFL mesh converter accepts planar polygons and quads twisted by <=10% of their extent. A few ramp quads at the
// serration ends twist more; split exactly those along a diagonal (same vertices, same UVs, same rendered surface).
const sub3 = (a, b) => a.map((v, i) => v - b[i]), cross3 = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
function splitWarped(el) {
  const faces = {};
  for (const [key, f] of Object.entries(el.faces)) {
    const P = f.vertices.map(k => el.vertices[k]);
    let n = [0, 0, 0]; for (let i = 0; i < P.length; i++) n = n.map((v, j) => v + cross3(P[i], P[(i + 1) % P.length])[j]);
    const L = Math.hypot(...n) || 1, u = n.map(v => v / L);
    const extent = Math.max(...[0, 1, 2].map(k => Math.max(...P.map(p => p[k])) - Math.min(...P.map(p => p[k]))));
    const dev = Math.max(...P.map(p => Math.abs(sub3(p, P[0]).reduce((s, v, i) => s + v * u[i], 0))));
    const ok = dev <= Math.max(1e-5, extent * 1e-5) || (P.length === 4 && dev <= extent * 0.1);
    if (ok || P.length < 4) { faces[key] = f; continue; }
    for (let i = 1; i + 1 < f.vertices.length; i++) {
      const vs = [f.vertices[0], f.vertices[i], f.vertices[i + 1]];
      faces[key + 't' + i] = {...f, vertices: vs, uv: Object.fromEntries(vs.map(k => [k, f.uv[k]]))};
    }
  }
  return {...el, faces};
}

// Minimal PNG encoder (RGBA8, filter 0) so the texture needs no external tool.
const crcTable = Array.from({length: 256}, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc32 = buf => { let c = 0xFFFFFFFF; for (const b of buf) c = crcTable[(c ^ b) & 0xFF] ^ (c >>> 8); return (c ^ 0xFFFFFFFF) >>> 0; };
function encodePng(rgba, w, h) {
  const chunk = (type, data) => { const len = Buffer.alloc(4); len.writeUInt32BE(data.length); const td = Buffer.concat([Buffer.from(type), data]), crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td)); return Buffer.concat([len, td, crc]); };
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  const raw = Buffer.alloc(h * (w * 4 + 1)); for (let y = 0; y < h; y++) rgba.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4);
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, {level: 9})), chunk('IEND', Buffer.alloc(0))]);
}

export function build() {
  // Generator output: mesh elements (vertices relative to element origin, UVs in 1024 texture pixels) + RGBA atlas.
  const tmpJson = path.join(os.tmpdir(), 'p9_01_v2_native_gen.json'), tmpRgba = path.join(os.tmpdir(), 'p9_01_v2_native_gen.rgba');
  execFileSync(process.execPath, [generatorPath, tmpJson, tmpRgba], {stdio: ['ignore', 'ignore', 'inherit']});
  const gen = JSON.parse(fs.readFileSync(tmpJson, 'utf8')), png = encodePng(fs.readFileSync(tmpRgba), 1024, 1024);
  fs.writeFileSync(texturePath, png);
  // Free Model: uv size = texture pixel size (the mesh UVs are in 1024 px), as in Blackridge.
  const texture = {name: 'p9_01_v2_native.png', relative_path: 'textures/p9_01_v2_native.png', folder: '', namespace: '', id: '0', group: '', scope: 0,
    width: 1024, height: 1024, uv_width: 1024, uv_height: 1024, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
    file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
    frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture'),
    source: 'data:image/png;base64,' + png.toString('base64')};
  const meshes = gen.elements.map(e => ({color: 0, shading: 'flat', locked: false, render_order: 'default', scope: 0, allow_mirror_modeling: true, ...e.element}));
  const elements = [], owner = new Map();   // bone -> [element uuid]
  const put = (bone, el) => { elements.push(el); (owner.get(bone) || owner.set(bone, []).get(bone)).push(el.uuid); };
  for (const m of meshes) {
    const bone = boneFor(m.name); if (!bone) continue;
    const fresh = el => splitWarped({...el, faces: Object.fromEntries(Object.entries(el.faces).map(([k, f]) => [k, {...f, texture: 0}]))});
    put(bone, {...fresh(m), uuid: uuid('mesh:' + m.name), export: true, visibility: true});
    if (bone === 'magazine' || bone === 'follower') {
      put('reload_magazine', {...fresh(m), name: m.name.replace('magazine_', 'mag_out_'), uuid: uuid('mesh:mag_out:' + m.name), export: true, visibility: true});
      put('empty_old_mag', {...fresh(m), name: m.name.replace('magazine_', 'empty_old_mag_'), uuid: uuid('mesh:empty_old:' + m.name), export: true, visibility: true});
    }
  }
  // Editor-only Classic arm references (export:false): the exact box NativePlayerArmRenderer draws at each anchor.
  const armCube = (name, a, right) => ({name, box_uv: false, render_order: 'default', locked: false, export: false, scope: 0, allow_mirror_modeling: true,
    from: [a[0] - ARM_HALF, a[1] - ARM_LEN, a[2] - ARM_HALF], to: [a[0] + ARM_HALF, a[1], a[2] + ARM_HALF], autouv: 0, color: right ? 5 : 3, visibility: true, origin: a,
    faces: Object.fromEntries(['north', 'east', 'south', 'west', 'up', 'down'].map(f => [f, {uv: [0, 0, 16, 16], texture: false}])), type: 'cube', uuid: uuid('arm:' + name)});
  put('right_arm_reference', armCube('right_arm_reference_cube', HP, true));
  put('left_arm_reference', armCube('left_arm_reference_cube', LA, false));

  const groups = [];
  const outline = g => {
    const gid = uuid('group:' + g.name);
    groups.push({name: g.name, uuid: gid, export: g.export, locked: false, scope: 0, selected: false, visibility: true, _static: {properties: {}, temp_data: {}},
      origin: g.origin, rotation: g.rotation, color: 0, children: [], reset: false, shade: true, mirror_uv: false, autouv: 0, isOpen: true, primary_selected: false});
    return {uuid: gid, isOpen: true, children: [...(owner.get(g.name) || []), ...g.children.map(outline)]};
  };
  const outliner = [outline(RIG), outline(CAMERA)];
  for (const [bone] of owner) if (!groups.some(g => g.name === bone)) throw new Error('mesh bone missing from rig: ' + bone);
  return {
    meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: 'p9_01_v2_native', model_identifier: '', visible_box: [1, 1, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {},
    resolution: {width: 1024, height: 1024}, elements, groups, outliner, textures: [texture], animations: [],
  };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const model = build();
  const prior = fs.existsSync(nativePath) ? JSON.parse(fs.readFileSync(nativePath, 'utf8')) : null;
  if (prior?.animations?.length) model.animations = prior.animations;   // keep authored clips when re-running the rig build
  fs.writeFileSync(nativePath, JSON.stringify(model));
  const mesh = model.elements.filter(e => e.type === 'mesh');
  console.log(JSON.stringify({wrote: path.relative(root, nativePath), groups: model.groups.length, meshes: mesh.length,
    triangles: mesh.reduce((s, e) => s + Object.values(e.faces).reduce((a, f) => a + f.vertices.length - 2, 0), 0), ARM_HALF: +ARM_HALF.toFixed(3), ARM_LEN: +ARM_LEN.toFixed(3)}));
}
