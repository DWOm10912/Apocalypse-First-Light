// Blackridge .50 V2 AFL Native Rig (docs/native_guns/blackridge_50_remake_v1.md 11): the Blender game low poly
// (design/items/blackridge_50/source/blender/game_export.py -> design/items/blackridge_50/source/blackridge_50_v2_game_lowpoly.json)
// in a new Desert-Eagle-correct rig, with the baked 2048 atlas (src/main/blockbench/textures/blackridge_50_v2_native.png,
// + _s / _n LabPBR from game_compose.py, not embedded).
//   node tools/build-blackridge-50-v2-native.mjs   -> writes src/main/blockbench/blackridge_50_v2_native.bbmodel
// Space: the P9 V3 rig's (one ruler for the pistols: 16.99015 mm per px, the same first-person display scale 0.53), with
// the grip's hand point on the P9 hand pivot HP, so the P9 pistol stance holds it the same way. mm() is game_export.py's
// mapping (concept mm: +y muzzle, +z up, +x right; Blockbench px: muzzle -Z). Animations: kept from an existing
// blackridge_50_v2_native.bbmodel (the animation author writes them).
// Moving parts (Desert Eagle): the slide (with the bolt, which turns about the bore to unlock, the slide-mounted safety and
// the rear sight), the hammer, the trigger, the slide stop and the magazine release on the left, the magazine (slides out
// along its rake) and its follower. The barrel is fixed to the frame: the front sight and the muzzle stay put.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {ARM_HALF, ARM_LEN, HP, LA} from './build-p9-01-v2-native.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export const lowPolyPath = path.join(root, 'design/items/blackridge_50/source/blackridge_50_v2_game_lowpoly.json');
export const texturePath = path.join(root, 'src/main/blockbench/textures/blackridge_50_v2_native.png');
export const specPath = path.join(root, 'src/main/blockbench/textures/blackridge_50_v2_native_s.png');
export const normalPath = path.join(root, 'src/main/blockbench/textures/blackridge_50_v2_native_n.png');
export const nativePath = path.join(root, 'src/main/blockbench/blackridge_50_v2_native.bbmodel');
export const TEX = 2048;

// ---------- rig facts (shared with the animation author) ----------
export const MM_PER_PX = 16.99015, HAND_Y = 68.0, HAND_Z = 78.0;
const r5 = v => +v.toFixed(5) || 0;
/** Concept mm (x right, y to the muzzle, z up) -> Blockbench px. */
export const mm = (x, y, z) => [r5(x / MM_PER_PX), r5(HP[1] + (z - HAND_Z) / MM_PER_PX), r5(HP[2] - (y - HAND_Y) / MM_PER_PX)];
export const BORE_Z = 134.0;
export const SLIDE_TRAVEL = 75 / MM_PER_PX;                    // the bolt face from the chamber to behind the top round's base, px
export const MAG_AXIS = (() => { const l = Math.hypot(0.179, 1); return [0, 1 / l, -0.179 / l]; })();   // up the magazine's rake, px
export const FOLLOWER_LOADED = MAG_AXIS.map(v => r5(-v * 9.5 / MM_PER_PX));   // follower pushed down under the top round
export const MP = mm(0, 80, 120);                             // magazine pivot: the top of the magazine
export const ROUND_ANCHOR = mm(0, 60.6, 118.5);               // the top round's base (single stack), lying along the bore
export const ROUND_TILT = -90;                                // a round's +y (base to tip) to the muzzle

export const uuid = s => { const h = createHash('sha256').update('blackridge-50-v2-native:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };

// name, origin, children, {rotation, export:false}; order = outliner order
const G = (name, origin, children = [], opt = {}) => ({name, origin, rotation: opt.rotation || [0, 0, 0], children, export: opt.export !== false});
const round = name => G(name, ROUND_ANCHOR, [], {rotation: [ROUND_TILT, 0, 0]});
export const RIG = G('root', [0, 0, 0], [
  G('handling', HP, [
    G('gun_body', [0, 0, 0], [
      G('barrel', mm(0, 190, BORE_Z), [
        G('muzzle_anchor', mm(0, 273, BORE_Z)),
        G('chamber_round_anchor', mm(0, 133, BORE_Z), [], {rotation: [ROUND_TILT, 0, 0]}),
        G('front_sight', mm(0, 262, 155.3)),
        G('sight_anchor', mm(0, 262, 160.8)),
      ]),
      G('trigger', mm(0, 109.5, 104)),
      G('hammer', mm(0, 40.5, 113)),
      G('slide_stop', mm(-14, 75, 103.5)),
      G('magazine_release', mm(-14, 99, 75.5)),
    ]),
    G('slide', mm(0, 75, BORE_Z), [
      G('bolt', mm(0, 121, BORE_Z + 0.7)),
      G('safety', mm(0, 52.5, 133.7)),
      G('rear_sight', mm(0, 56, 155.3)),
      G('ejection_anchor', mm(9, 126, 140)),
    ]),
    G('magazine', MP, [G('follower', MP), round('magazine_round_anchor')]),
    G('righthand', [0, 0, 0], [
      G('right_hand_anchor', HP, [G('right_arm_reference', HP, [], {export: false})]),
    ]),
  ]),
  G('lefthand', [0, 0, 0], [
    G('lefthand_pos', [0, 0, 0], [
      G('left_hand_anchor', LA, [G('left_arm_reference', LA, [], {export: false})]),
    ]),
  ]),
  G('mag_out', MP, [G('reload_magazine', MP, [round('mag_out_round_anchor')])]),
  G('empty_old_mag', MP, [round('empty_old_mag_round_anchor')]),
  G('positioning', [0, 0, 0]),
  G('maintenance_anchor', mm(0, 140, 95)),
]);
export const CAMERA = G('camera', [0, 12, 18]);   // the P9's

// The magazine parts are also drawn by the reload helpers (mag_out/reload_magazine, empty_old_mag).
const MAG_COPIES = {magazine: true, follower: true};

export function meshElement(name, part) {
  const used = [...new Set(part.faces.flatMap(f => f.v))].sort((a, b) => a - b);
  const key = i => 'v' + i.toString(36);
  const vertices = Object.fromEntries(used.map(i => [key(i), part.vertices[i]]));
  const faces = Object.fromEntries(part.faces.map((f, n) => [
    'f' + n.toString(36), {uv: Object.fromEntries(f.v.map((i, k) => [key(i), f.uv[k]])), vertices: f.v.map(key), texture: 0}]));
  return {name, color: 0, shading: 'flat', locked: false, render_order: 'default', scope: 0, allow_mirror_modeling: true,
    origin: [0, 0, 0], rotation: [0, 0, 0], vertices, faces, type: 'mesh', uuid: uuid('mesh:' + name), export: true, visibility: true};
}

// Twice a face's area in px^2; below this a face is a sliver nobody sees and the mesh exporter rejects it as zero-area.
const SLIVER = 1e-6;
const doubleArea = pts => {
  let n = [0, 0, 0];
  for (let k = 0; k < pts.length; k++) { const a = pts[k], b = pts[(k + 1) % pts.length]; n = [n[0] + a[1] * b[2] - a[2] * b[1], n[1] + a[2] * b[0] - a[0] * b[2], n[2] + a[0] * b[1] - a[1] * b[0]]; }
  return Math.hypot(...n);
};

/** The low poly parts (unique names), slivers dropped. */
export function lowParts(file = lowPolyPath) {
  const low = JSON.parse(fs.readFileSync(file, 'utf8'));
  if (low.texture_size !== TEX) throw new Error('low poly UVs are not in ' + TEX + ' px');
  const seen = new Map();
  low.slivers = 0;
  low.parts = low.parts.map(p => {
    const n = (seen.get(p.name) || 0) + 1; seen.set(p.name, n);
    const faces = p.faces.filter(f => doubleArea(f.v.map(i => p.vertices[i])) >= SLIVER);
    low.slivers += p.faces.length - faces.length;
    return {...p, name: n > 1 ? `${p.name}_${n}` : p.name, faces};
  });
  return low;
}

export function build() {
  const low = lowParts();
  const png = fs.readFileSync(texturePath);
  const texture = {name: 'blackridge_50_v2_native.png', relative_path: 'textures/blackridge_50_v2_native.png', folder: '', namespace: '', id: '0', group: '', scope: 0,
    width: TEX, height: TEX, uv_width: TEX, uv_height: TEX, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
    file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
    frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture'),
    source: 'data:image/png;base64,' + png.toString('base64')};
  const elements = [], owner = new Map();
  const put = (bone, el) => { elements.push(el); (owner.get(bone) || owner.set(bone, []).get(bone)).push(el.uuid); };
  for (const part of low.parts) {
    put(part.bone, meshElement(part.name, part));
    if (MAG_COPIES[part.bone]) {
      put('reload_magazine', meshElement('mag_out_' + part.name, part));
      put('empty_old_mag', meshElement('empty_old_mag_' + part.name, part));
    }
  }
  // Editor-only Classic arm references (export:false): the box NativePlayerArmRenderer draws at each anchor.
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
    meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: 'blackridge_50_v2_native', model_identifier: '', visible_box: [1, 1, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {},
    resolution: {width: TEX, height: TEX}, elements, groups, outliner, textures: [texture], animations: [],
  };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const model = build();
  const prior = fs.existsSync(nativePath) ? JSON.parse(fs.readFileSync(nativePath, 'utf8')) : null;
  model.animations = prior?.animations || [];
  fs.writeFileSync(nativePath, JSON.stringify(model));
  const mesh = model.elements.filter(e => e.type === 'mesh');
  console.log(JSON.stringify({wrote: path.relative(root, nativePath), groups: model.groups.length, meshes: mesh.length, animations: model.animations.length,
    triangles: mesh.reduce((s, e) => s + Object.values(e.faces).reduce((a, f) => a + f.vertices.length - 2, 0), 0)}));
}
