// Blackridge .50 V2 AFL Native export (docs/native_guns/blackridge_50_remake_v1.md 11): writes the V2 runtime resources
// only. The item still points at the old blackridge_50 resources until the new animations are in (the old clips are keyed
// to the old TACZ-derived rig); they stay in the tree, as the P9 and BR-51 kept theirs.
//   node tools/export-blackridge-50-v2-native.mjs [--check]
// Outputs: geo/blackridge_50_v2_native.geo.json (bones only, texture 2048), meshes/blackridge_50_v2_native.aflmesh.json
// (Vextris format 3, crease_angle 35 / crease_weighting "area" as the bake's Weighted Normal),
// textures/item/blackridge_50_v2_native.png (Base Color from the bbmodel) + _s / _n (LabPBR from src/main/blockbench/textures/),
// models/item/blackridge_50_v2_native_in_hand.json (the P9 V3 display: same ruler, the grip on the same hand point).
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {convert, serializeCompact, meshCounts} from './export-afl-mesh.mjs';
import {nativePath, specPath, normalPath, TEX} from './build-blackridge-50-v2-native.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const assets = path.join(root, 'src/main/resources/assets/apocalypse_firstlight');
const out = {
  geo: path.join(assets, 'geo/blackridge_50_v2_native.geo.json'),
  mesh: path.join(assets, 'meshes/blackridge_50_v2_native.aflmesh.json'),
  texture: path.join(assets, 'textures/item/blackridge_50_v2_native.png'),
  spec: path.join(assets, 'textures/item/blackridge_50_v2_native_s.png'),
  normal: path.join(assets, 'textures/item/blackridge_50_v2_native_n.png'),
  display: path.join(assets, 'models/item/blackridge_50_v2_native_in_hand.json'),
};
const p9Display = path.join(assets, 'models/item/p9_01_v3_native_in_hand.json');
const CREASE_ANGLE = 35;   // = the bake's CREASE: Blender marked edges at >= 35 degrees sharp before baking

const source = JSON.parse(fs.readFileSync(nativePath, 'utf8'));
const byUuid = new Map(source.groups.map(g => [g.uuid, g]));
const r = n => +n.toFixed(5) || 0;

// ---- geo: exportable bones in outliner order; native exporter sign convention pivot [-x,y,z], rotation [-rx,-ry,rz]
const bones = [];
(function walk(nodes, parent) {
  for (const n of nodes) {
    if (typeof n === 'string') continue;
    const g = byUuid.get(n.uuid); if (g.export === false) continue;
    const b = {name: g.name};
    if (parent) b.parent = parent;
    b.pivot = [r(-g.origin[0]), r(g.origin[1]), r(g.origin[2])];
    if (g.rotation.some(v => v)) b.rotation = [r(-g.rotation[0]), r(-g.rotation[1]), r(g.rotation[2])];
    bones.push(b); walk(n.children || [], g.name);
  }
})(source.outliner, null);
const geo = {format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: 'geometry.blackridge_50_v2_native', texture_width: TEX, texture_height: TEX,
  visible_bounds_width: 20, visible_bounds_height: 12, visible_bounds_offset: [0, 4, 0]}, bones}]};

// ---- display: the P9 V3 presentation (same ruler and hand point), particle from the Blackridge atlas
const display = JSON.parse(fs.readFileSync(p9Display, 'utf8'));
display.textures.particle = 'apocalypse_firstlight:item/blackridge_50_v2_native';

const geoText = JSON.stringify(geo, null, '\t') + '\n';
const flat = convert(source, geo, {}, nativePath, 2);
const model = {...flat, format_version: 3, crease_angle: CREASE_ANGLE, crease_weighting: 'area'};
const meshText = serializeCompact(model);
if (meshText.length > 4 * 1024 * 1024) throw new Error('sidecar exceeds runtime 4 MiB limit: ' + meshText.length);
if (JSON.stringify(JSON.parse(meshText)) !== JSON.stringify(model)) throw new Error('compact sidecar changed data');
const png = Buffer.from(source.textures[0].source.split(',')[1], 'base64');
const displayText = JSON.stringify(display, null, 2) + '\n';
const pairs = [[specPath, out.spec], [normalPath, out.normal]];
if (process.argv.includes('--check')) {
  for (const [f, t] of [[out.geo, geoText], [out.mesh, meshText], [out.display, displayText]]) if (fs.readFileSync(f, 'utf8') !== t) throw new Error('stale ' + f);
  if (!fs.readFileSync(out.texture).equals(png)) throw new Error('stale ' + out.texture);
  for (const [src, dst] of pairs) if (!fs.existsSync(dst) || !fs.readFileSync(dst).equals(fs.readFileSync(src))) throw new Error('stale ' + dst);
} else {
  fs.writeFileSync(out.geo, geoText); fs.writeFileSync(out.mesh, meshText); fs.writeFileSync(out.texture, png); fs.writeFileSync(out.display, displayText);
  for (const [src, dst] of pairs) fs.copyFileSync(src, dst);
}
console.log(JSON.stringify({bones: bones.length, parts: model.parts.length, format_version: model.format_version, ...meshCounts(model),
  sidecarChars: meshText.length, outputs: Object.values(out).map(p => path.relative(root, p))}, null, 1));
