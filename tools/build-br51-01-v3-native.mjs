// BR-51 V3 AFL Native Rig (docs/native_guns/br51_01_v3_game_model_v1.md): the Blender game low poly
// (design/items/br51_m14/source/blender/game_export.py -> design/items/br51_m14/source/br51_01_v3_game_lowpoly_gun.json)
// in a new M14-correct rig, with the baked 2048 atlas (src/main/blockbench/textures/br51_01_v3_native.png, + _s / _n
// LabPBR from game_compose.py, not embedded).
//   node tools/build-br51-01-v3-native.mjs   -> writes src/main/blockbench/br51_01_v3_native.bbmodel
// Space: the old BR-51 rig's (Blockbench px; bore axis y 11.4375, muzzle crown z -28, muzzle -Z, +X the right side), so
// the in-hand display and the third-person body scale stay as they were. Animations: kept from an existing
// br51_01_v3_native.bbmodel (tools/author-br51-01-v3-native-animations.mjs --write-source writes them).
// Moving parts (M14 mechanism): the operating rod (oprod, its handle on the right) and the bolt (bolt: back along the bore
// and turned about it while it unlocks), the bolt lock on the left (bolt_lock), the trigger, the safety in the front of the
// trigger guard, the magazine latch behind the magazine (mag_latch), the selector on the right rear of the receiver and the
// magazine, which rocks in and out about its front catch (the magazine pivot MP is the front lug).
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export const lowPolyPath = path.join(root, 'design/items/br51_m14/source/br51_01_v3_game_lowpoly_gun.json');
export const texturePath = path.join(root, 'src/main/blockbench/textures/br51_01_v3_native.png');
export const specPath = path.join(root, 'src/main/blockbench/textures/br51_01_v3_native_s.png');
export const normalPath = path.join(root, 'src/main/blockbench/textures/br51_01_v3_native_n.png');
export const nativePath = path.join(root, 'src/main/blockbench/br51_01_v3_native.bbmodel');
export const TEX = 2048;

// ---------- rig facts (shared with the animation author and the magazine builder) ----------
export const FP_SCALE = 0.45;                                  // first-person Display scale (models/item/br51_01_v3_native_in_hand.json)
export const ARM_HALF = 4 * 0.62 / FP_SCALE / 2;               // Vanilla arm half width after presentation scale, model units
export const ARM_LEN = 12 * 0.78 / FP_SCALE;                   // forearm length, model units
export const BORE_Y = 11.4375, MUZZLE_Z = -28;
export const MP = [0, 10.637, 1.549];                          // magazine pivot: the front lug in the receiver's front catch
export const HP = [0, 8.3, 12.8];                              // handling pivot = right grip (the stock's wrist)
export const RP = [0, 0, 0], LP = [0, 0, 0];                   // hand chain pivots
export const LA = [-1.0, 9.0, -6.7];                           // left_hand_anchor rest origin (any rigid point; solved per frame)
export const BOLT_P = [0, BORE_Y, 3.645];                      // bolt and operating rod pivot (on the bore, the bolt's middle)
export const ROUND_ANCHORS = [[-0.2233, 10.656, 5.223], [0.2233, 10.365, 5.223]];   // top two rounds' bases (double stack)
export const ROUND_TILT = -90;                                 // a round's +y (base to tip) to the muzzle

export const uuid = s => { const h = createHash('sha256').update('br51-01-v3-native:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };

// name, origin, rotation, children, {export:false} ; order = outliner order
const G = (name, origin, children = [], opt = {}) => ({name, origin, rotation: opt.rotation || [0, 0, 0], children, export: opt.export !== false});
const rounds = prefix => ROUND_ANCHORS.map((p, i) => G(`${prefix}_round_${i + 1}`, p, [], {rotation: [ROUND_TILT, 0, 0]}));
export const RIG = G('root', [0, 7, 10], [
  G('handling', HP, [
    G('gun_body', [0, 0, 0], [
      G('muzzle_anchor', [0, BORE_Y, MUZZLE_Z]),
      G('sight_anchor', [0, 12.748, 9.325]),
      G('rear_sight', [0, 12.748, 9.31]),
      G('ejection_anchor', [0.388, 11.729, 3.84]),
      G('trigger', [0, 9.108, 8.305]),
      G('safety', [0, 8.574, 6.762]),
      G('mag_latch', [0, 8.9136, 5.733]),
      G('selector', [0.743, 11.389, 9.907]),
      G('bolt_lock', [-0.67, BORE_Y, 5.004]),
    ]),
    G('oprod', BOLT_P),
    G('bolt', BOLT_P),
    G('magazine', MP, rounds('magazine')),
    G('righthand', RP, [
      G('right_hand_anchor', HP, [G('right_arm_reference', HP, [], {export: false})]),
    ]),
  ]),
  G('lefthand', LP, [
    G('lefthand_pos', LP, [
      G('left_hand_anchor', LA, [G('left_arm_reference', LA, [], {export: false})]),
    ]),
  ]),
  G('mag_out', MP, [G('reload_magazine', MP, rounds('mag_out'))]),
  G('empty_old_mag', MP, rounds('empty_old_mag')),
  G('positioning', [0, 0, 0]),
  G('maintenance_anchor', [0, 10, 0]),
]);
export const CAMERA = G('camera', [3.2125, 14.3, 14.90625]);

// The magazine is also drawn by the reload helpers (mag_out/reload_magazine, empty_old_mag).
const MAG_COPIES = {magazine: true};

export function meshElement(name, part) {
  const used = [...new Set(part.faces.flatMap(f => f.v))].sort((a, b) => a - b);
  const key = i => 'v' + i.toString(36);
  const vertices = Object.fromEntries(used.map(i => [key(i), part.vertices[i]]));
  const faces = Object.fromEntries(part.faces.map((f, n) => [
    'f' + n.toString(36), {uv: Object.fromEntries(f.v.map((i, k) => [key(i), f.uv[k]])), vertices: f.v.map(key), texture: 0}]));
  return {name, color: 0, shading: 'flat', locked: false, render_order: 'default', scope: 0, allow_mirror_modeling: true,
    origin: [0, 0, 0], rotation: [0, 0, 0], vertices, faces, type: 'mesh', uuid: uuid('mesh:' + name), export: true, visibility: true};
}

// Twice a face's area in px^2 (|sum of edge cross products|); below this a face is a sliver nobody sees (Blender's
// export threshold is in mm^2, 424x smaller in px^2) and the mesh exporter rejects it as zero-area.
const SLIVER = 1e-6;
const doubleArea = pts => {
  let n = [0, 0, 0];
  for (let k = 0; k < pts.length; k++) { const a = pts[k], b = pts[(k + 1) % pts.length]; n = [n[0] + a[1] * b[2] - a[2] * b[1], n[1] + a[2] * b[0] - a[0] * b[2], n[2] + a[0] * b[1] - a[1] * b[0]]; }
  return Math.hypot(...n);
};

/** The low poly parts with unique names (Blender splits some into several objects of one base name: _2, _3 ...), slivers dropped. */
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
  const texture = {name: 'br51_01_v3_native.png', relative_path: 'textures/br51_01_v3_native.png', folder: '', namespace: '', id: '0', group: '', scope: 0,
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
    meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: 'br51_01_v3_native', model_identifier: '', visible_box: [1, 1, 0],
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
