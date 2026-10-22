// BR-51 V3 magazines as items (docs/native_guns/br51_01_v3_game_model_v1.md, docs/native_guns/magazine_system_v1.md):
//   br51_01_magazine           20 rounds: the gun's own magazine (bone magazine of the gun low poly), drawn with the gun's
//                              texture (NativeMagazineItem textureAsset br51_01_v3_native), so the item, the magazine in the
//                              gun and the reload's magazines are one shape
//   br51_01_extended_magazine  30 rounds, curved (mags low poly, bone mag30), texture br51_01_v3_mags
//   br51_drum_magazine_50      50-round drum (mags low poly, bone mag50), texture br51_01_v3_mags
//   node tools/build-br51-01-magazines.mjs [--check]
// Each: geo/<id>.geo.json (one bone magazine_root at the origin) and meshes/<id>.aflmesh.json (Vextris format 3, crease 35
// "area" as the gun), vertices in blocks from the gun's magazine pivot MP (the front lug), as built in the gun: all three
// stand where the 20-round one does, their feed lips at the same place, so the gun's own top rounds (magazine_round_1/2)
// show on any of them. No tilt to take out (the magazine rocks in to vertical). Prints each item lift (px: the item
// renderer raises the pivot so the magazine's middle sits at the block's middle) and the prop points.
// Also writes src/main/blockbench/br51_01_v3_mags.bbmodel: the editable Blockbench source of the 30-round magazine and the
// drum (groups mag30 / mag50 around MP, the br51_01_v3_mags atlas embedded), which the wiki icons render (wiki/icons.json);
// the 20-round one's source is the gun's own br51_01_v3_native.bbmodel (group magazine).
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {convert, serializeCompact, meshCounts} from './export-afl-mesh.mjs';
import {lowParts, meshElement, uuid, MP, TEX} from './build-br51-01-v3-native.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const assets = path.join(root, 'src/main/resources/assets/apocalypse_firstlight');
const MAGS_LOW = path.join(root, 'design/items/br51_m14/source/br51_01_v3_game_lowpoly_mags.json');
const ITEMS = [
  {id: 'br51_01_magazine', file: null, bone: 'magazine'},
  {id: 'br51_01_extended_magazine', file: MAGS_LOW, bone: 'mag30'},
  {id: 'br51_drum_magazine_50', file: MAGS_LOW, bone: 'mag50'},
];
const CREASE_ANGLE = 35;
const check = process.argv.includes('--check');
const r5 = n => +n.toFixed(5) || 0;

const report = {};
let stale = 0;
for (const item of ITEMS) {
  const low = item.file ? lowParts(item.file) : lowParts();
  const parts = low.parts.filter(p => p.bone === item.bone);
  if (!parts.length) throw new Error(`${item.id}: no parts on bone ${item.bone}`);
  // a one-group Free model around MP, converted as the gun is (vertices relative to the bone pivot, in blocks)
  const group = {name: 'magazine_root', uuid: uuid(item.id + ':group'), export: true, origin: MP, rotation: [0, 0, 0]};
  const elements = parts.map(p => ({...meshElement(item.id + ':' + p.name, p), name: p.name}));
  elements.forEach(e => { e.uuid = uuid(item.id + ':' + e.name); });
  const source = {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, resolution: {width: TEX, height: TEX},
    textures: [{uuid: uuid(item.id + ':texture'), width: TEX, height: TEX, uv_width: TEX, uv_height: TEX}],
    elements, groups: [group], outliner: [{uuid: group.uuid, children: elements.map(e => e.uuid)}], animations: []};
  const target = {format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: `geometry.${item.id}`, texture_width: TEX, texture_height: TEX,
    visible_bounds_width: 2, visible_bounds_height: 2, visible_bounds_offset: [0, -0.25, 0]}, bones: [{name: 'magazine_root', pivot: [-MP[0], MP[1], MP[2]]}]}]};
  const flat = convert(source, target, {}, item.id, 2);
  const model = {...flat, format_version: 3, crease_angle: CREASE_ANGLE, crease_weighting: 'area'};
  const meshText = serializeCompact(model);
  if (JSON.stringify(JSON.parse(meshText)) !== JSON.stringify(model)) throw new Error('compact sidecar changed data');
  // the runtime geo: the bone at the origin (the vertices are already relative to MP), as p9_01_magazine
  target['minecraft:geometry'][0].bones = [{name: 'magazine_root', pivot: [0, 0, 0]}];
  const geoText = JSON.stringify(target, null, 2) + '\n';
  const out = [[path.join(assets, `geo/${item.id}.geo.json`), geoText], [path.join(assets, `meshes/${item.id}.aflmesh.json`), meshText]];
  for (const [file, text] of out) {
    if (check) { if (!fs.existsSync(file) || fs.readFileSync(file, 'utf8').replace(/\r\n/g, '\n') !== text) { console.log('STALE ' + path.relative(root, file)); stale++; } }
    else fs.writeFileSync(file, text);
  }
  // bounds in px from MP, the item lift, and the prop points in item block units (the pivot at (0.5, 0.5 + lift/16, 0.5))
  const min = [Infinity, Infinity, Infinity], max = [-Infinity, -Infinity, -Infinity];
  for (const p of model.parts) for (const v of p.vertices) for (let i = 0; i < 3; i++) { min[i] = Math.min(min[i], v[i] * 16); max[i] = Math.max(max[i], v[i] * 16); }
  const lift = -(min[1] + max[1]) / 2, blk = (x, y, z) => [r5(0.5 + x / 16), r5(0.5 + (lift + y) / 16), r5(0.5 + z / 16)];
  report[item.id] = {...meshCounts(model), sidecarChars: meshText.length, boundsPx: [min.map(r5), max.map(r5)], itemLift: r5(lift),
    points: {top: blk(0, max[1], (min[2] + max[2]) / 2), grip: blk(0, (min[1] + max[1]) / 2, (min[2] + max[2]) / 2), base: blk(0, min[1], (min[2] + max[2]) / 2)},
    boundsBlocks: [blk(min[0], min[1], min[2]), blk(max[0], max[1], max[2])]};
}
// the Blockbench source of the 30-round magazine and the drum (one group per magazine, its atlas embedded)
{
  const low = lowParts(MAGS_LOW), magsTex = path.join(root, 'src/main/blockbench/textures/br51_01_v3_mags.png');
  const texture = {name: 'br51_01_v3_mags.png', relative_path: 'textures/br51_01_v3_mags.png', folder: '', namespace: '', id: '0', group: '', scope: 0,
    width: TEX, height: TEX, uv_width: TEX, uv_height: TEX, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
    file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
    frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('mags:texture'),
    source: 'data:image/png;base64,' + fs.readFileSync(magsTex).toString('base64')};
  const elements = [], groups = [], outliner = [];
  for (const bone of ['mag30', 'mag50']) {
    const els = low.parts.filter(p => p.bone === bone).map(p => ({...meshElement('mags:' + p.name, p), name: p.name, uuid: uuid('mags:' + p.name)}));
    const gid = uuid('mags:group:' + bone);
    elements.push(...els);
    groups.push({name: bone, uuid: gid, export: true, locked: false, scope: 0, selected: false, visibility: true, _static: {properties: {}, temp_data: {}},
      origin: MP, rotation: [0, 0, 0], color: 0, children: [], reset: false, shade: true, mirror_uv: false, autouv: 0, isOpen: true, primary_selected: false});
    outliner.push({uuid: gid, isOpen: true, children: els.map(e => e.uuid)});
  }
  const model = {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: 'br51_01_v3_mags', model_identifier: '', visible_box: [1, 1, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {},
    resolution: {width: TEX, height: TEX}, elements, groups, outliner, textures: [texture], animations: []};
  const file = path.join(root, 'src/main/blockbench/br51_01_v3_mags.bbmodel'), text = JSON.stringify(model);
  if (check) { if (!fs.existsSync(file) || fs.readFileSync(file, 'utf8') !== text) { console.log('STALE ' + path.relative(root, file)); stale++; } }
  else fs.writeFileSync(file, text);
}
if (stale) process.exit(1);
console.log(JSON.stringify(report, null, 1));
