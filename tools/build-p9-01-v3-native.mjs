// P9-01 V3 AFL Native Rig: the Blender game low poly (design/items/p9_01_v3/source/blender/game_export.py ->
// design/items/p9_01_v3/source/p9_01_v3_game_lowpoly.json) in the unchanged V2 native rig, with the baked 2048 atlas
// (src/main/blockbench/textures/p9_01_v3_native.png, + _s / _n LabPBR from game_compose.py, not embedded).
//   node tools/build-p9-01-v3-native.mjs   -> writes src/main/blockbench/p9_01_v3_native.bbmodel
// Animations: kept from an existing p9_01_v3_native.bbmodel; on the first build copied from p9_01_v2_native.bbmodel
// (read only) with each bone animator re-keyed to the V3 group of the same name. The V2 source is never written.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {RIG, CAMERA, HP, LA, ARM_HALF, ARM_LEN, nativePath as v2Path} from './build-p9-01-v2-native.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export const lowPolyPath = path.join(root, 'design/items/p9_01_v3/source/p9_01_v3_game_lowpoly.json');
export const texturePath = path.join(root, 'src/main/blockbench/textures/p9_01_v3_native.png');
export const specPath = path.join(root, 'src/main/blockbench/textures/p9_01_v3_native_s.png');
export const normalPath = path.join(root, 'src/main/blockbench/textures/p9_01_v3_native_n.png');
export const nativePath = path.join(root, 'src/main/blockbench/p9_01_v3_native.bbmodel');
export const TEX = 2048;

const uuid = s => { const h = createHash('sha256').update('p9-01-v3-native:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
// The magazine parts are also drawn by the reload helpers (mag_out/reload_magazine, empty_old_mag).
const MAG_COPIES = {magazine: true, follower: true};

function meshElement(name, part) {
  const used = [...new Set(part.faces.flatMap(f => f.v))].sort((a, b) => a - b);
  const key = i => 'v' + i.toString(36);
  const vertices = Object.fromEntries(used.map(i => [key(i), part.vertices[i]]));
  const faces = Object.fromEntries(part.faces.map((f, n) => [
    'f' + n.toString(36), {uv: Object.fromEntries(f.v.map((i, k) => [key(i), f.uv[k]])), vertices: f.v.map(key), texture: 0}]));
  return {name, color: 0, shading: 'flat', locked: false, render_order: 'default', scope: 0, allow_mirror_modeling: true,
    origin: [0, 0, 0], rotation: [0, 0, 0], vertices, faces, type: 'mesh', uuid: uuid('mesh:' + name), export: true, visibility: true};
}

export function build() {
  const low = JSON.parse(fs.readFileSync(lowPolyPath, 'utf8'));
  if (low.texture_size !== TEX) throw new Error('low poly UVs are not in ' + TEX + ' px');
  const png = fs.readFileSync(texturePath);
  const texture = {name: 'p9_01_v3_native.png', relative_path: 'textures/p9_01_v3_native.png', folder: '', namespace: '', id: '0', group: '', scope: 0,
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
  // Editor-only Classic arm references (export:false), as in V2.
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
    meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: 'p9_01_v3_native', model_identifier: '', visible_box: [1, 1, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {},
    resolution: {width: TEX, height: TEX}, elements, groups, outliner, textures: [texture], animations: [],
  };
}

// V2 clips re-keyed to the V3 groups (same bone names, same rig).
function animationsFromV2(model) {
  const v2 = JSON.parse(fs.readFileSync(v2Path, 'utf8'));
  const byName = new Map(model.groups.map(g => [g.name, g.uuid]));
  return (v2.animations || []).map(a => {
    const copy = JSON.parse(JSON.stringify(a));
    copy.uuid = uuid('animation:' + a.name);
    copy.selected = false;
    copy.animators = Object.fromEntries(Object.entries(a.animators || {}).map(([id, an]) => {
      if (an.type === 'effect' || !an.name) return [id, an];
      const to = byName.get(an.name);
      if (!to) throw new Error('V2 animator without a V3 group: ' + an.name);
      return [to, an];
    }));
    return copy;
  });
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const model = build();
  const prior = fs.existsSync(nativePath) ? JSON.parse(fs.readFileSync(nativePath, 'utf8')) : null;
  model.animations = prior?.animations?.length ? prior.animations : animationsFromV2(model);
  fs.writeFileSync(nativePath, JSON.stringify(model));
  const mesh = model.elements.filter(e => e.type === 'mesh');
  console.log(JSON.stringify({wrote: path.relative(root, nativePath), groups: model.groups.length, meshes: mesh.length, animations: model.animations.length,
    triangles: mesh.reduce((s, e) => s + Object.values(e.faces).reduce((a, f) => a + f.vertices.length - 2, 0), 0)}));
}
