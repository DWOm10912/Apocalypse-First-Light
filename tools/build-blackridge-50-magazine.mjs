// Blackridge .50 V2 7-round magazine as an item (blackridge_50_magazine; docs/native_guns/magazine_system_v1.md 11,
// docs/native_guns/blackridge_50_remake_v1.md 12): the gun's own magazine bone cut from the V2 game low poly, so the item,
// the magazine in the gun and the reload's magazines are one shape; drawn with the gun's texture (NativeMagazineItem
// textureAsset blackridge_50_v2_native), replaces no bone. As tools/build-br51-01-magazines.mjs (no follower: the item's
// top round, magazine_round_visual.item_rounds, sits where a loaded magazine's follower is pushed down from).
//   node tools/build-blackridge-50-magazine.mjs [--check]
// Writes geo/blackridge_50_magazine.geo.json (one bone magazine_root at the origin) and meshes/blackridge_50_magazine.aflmesh.json
// (Vextris format 3, crease 35 "area" as the gun), vertices in blocks from the gun's magazine pivot MP, the 10.1 deg rake
// baked in; the item renderer turns it upright (NativeMagazineItem itemTilt = TILT). Prints the item lift (px: the
// straightened magazine's middle on the block's middle).
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {convert, serializeCompact, meshCounts} from './export-afl-mesh.mjs';
import {lowParts, meshElement, uuid, MP, MAG_AXIS, TEX} from './build-blackridge-50-v2-native.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const assets = path.join(root, 'src/main/resources/assets/apocalypse_firstlight');
const ID = 'blackridge_50_magazine';
export const TILT = +(Math.atan2(-MAG_AXIS[2], MAG_AXIS[1]) * 180 / Math.PI).toFixed(2);   // the rake, taken out about +x
const CREASE_ANGLE = 35;
const check = process.argv.includes('--check');
const r5 = n => +n.toFixed(5) || 0;

const parts = lowParts().parts.filter(p => p.bone === 'magazine');
if (!parts.length) throw new Error('no parts on bone magazine');
const group = {name: 'magazine_root', uuid: uuid(ID + ':group'), export: true, origin: MP, rotation: [0, 0, 0]};
const elements = parts.map(p => ({...meshElement(ID + ':' + p.name, p), name: p.name, uuid: uuid(ID + ':' + p.name)}));
const source = {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, resolution: {width: TEX, height: TEX},
  textures: [{uuid: uuid(ID + ':texture'), width: TEX, height: TEX, uv_width: TEX, uv_height: TEX}],
  elements, groups: [group], outliner: [{uuid: group.uuid, children: elements.map(e => e.uuid)}], animations: []};
const target = {format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: `geometry.${ID}`, texture_width: TEX, texture_height: TEX,
  visible_bounds_width: 2, visible_bounds_height: 2, visible_bounds_offset: [0, -0.25, 0]}, bones: [{name: 'magazine_root', pivot: [-MP[0], MP[1], MP[2]]}]}]};
const flat = convert(source, target, {}, ID, 2);
const model = {...flat, format_version: 3, crease_angle: CREASE_ANGLE, crease_weighting: 'area'};
const meshText = serializeCompact(model);
if (JSON.stringify(JSON.parse(meshText)) !== JSON.stringify(model)) throw new Error('compact sidecar changed data');
target['minecraft:geometry'][0].bones = [{name: 'magazine_root', pivot: [0, 0, 0]}];   // the vertices are already relative to MP
const geoText = JSON.stringify(target, null, 2) + '\n';
let stale = 0;
for (const [file, text] of [[path.join(assets, `geo/${ID}.geo.json`), geoText], [path.join(assets, `meshes/${ID}.aflmesh.json`), meshText]]) {
  if (check) { if (!fs.existsSync(file) || fs.readFileSync(file, 'utf8').replace(/\r\n/g, '\n') !== text) { console.log('STALE ' + path.relative(root, file)); stale++; } }
  else fs.writeFileSync(file, text);
}
if (stale) process.exit(1);
// bounds in px from MP, straightened (+TILT about x, as the item renderer's Axis.XP), and the item lift
const c = Math.cos(TILT * Math.PI / 180), s = Math.sin(TILT * Math.PI / 180);
const min = [Infinity, Infinity, Infinity], max = [-Infinity, -Infinity, -Infinity];
for (const p of model.parts) for (const v of p.vertices) {
  const st = [v[0] * 16, (v[1] * c - v[2] * s) * 16, (v[1] * s + v[2] * c) * 16];
  for (let i = 0; i < 3; i++) { min[i] = Math.min(min[i], st[i]); max[i] = Math.max(max[i], st[i]); }
}
console.log(JSON.stringify({...meshCounts(model), sidecarChars: meshText.length, tilt: TILT, straightenedPx: [min.map(r5), max.map(r5)],
  itemLift: r5(-(min[1] + max[1]) / 2), zCentrePx: r5((min[2] + max[2]) / 2)}, null, 1));
