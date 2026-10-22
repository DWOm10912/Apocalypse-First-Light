// BR-51 V3 AFL Native export (docs/native_guns/br51_01_v3_game_model_v1.md): writes the V3 runtime resources only (the
// V2 br51_01 resources stay in the tree, unused).
//   node tools/export-br51-01-v3-native.mjs [--check]
// Outputs: geo/br51_01_v3_native.geo.json (bones only, texture 2048), meshes/br51_01_v3_native.aflmesh.json (Vextris
// format 3, crease_angle 35 / crease_weighting "area" as the bake's Weighted Normal), textures/item/br51_01_v3_native.png
// (Base Color from the bbmodel) + _s / _n (LabPBR from src/main/blockbench/textures/), textures/item/br51_01_v3_mags{,_s,_n}.png
// (the 30 / 50 round magazines' atlas, used by tools/build-br51-01-magazines.mjs), models/item/br51_01_v3_native_in_hand.json
// (the V2 display: same space, same hand scale). The clips are written by tools/author-br51-01-v3-native-animations.mjs.
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {convert, serializeCompact, meshCounts} from './export-afl-mesh.mjs';
import {nativePath, specPath, normalPath, TEX} from './build-br51-01-v3-native.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const assets = path.join(root, 'src/main/resources/assets/apocalypse_firstlight');
const bbTex = path.join(root, 'src/main/blockbench/textures');
const out = {
  geo: path.join(assets, 'geo/br51_01_v3_native.geo.json'),
  mesh: path.join(assets, 'meshes/br51_01_v3_native.aflmesh.json'),
  texture: path.join(assets, 'textures/item/br51_01_v3_native.png'),
  spec: path.join(assets, 'textures/item/br51_01_v3_native_s.png'),
  normal: path.join(assets, 'textures/item/br51_01_v3_native_n.png'),
  display: path.join(assets, 'models/item/br51_01_v3_native_in_hand.json'),
};
const copies = ['', '_s', '_n'].map(s => [path.join(bbTex, `br51_01_v3_mags${s}.png`), path.join(assets, `textures/item/br51_01_v3_mags${s}.png`)]);
const v2Display = path.join(assets, 'models/item/br51_01_in_hand.json');
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
const geo = {format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: 'geometry.br51_01_v3_native', texture_width: TEX, texture_height: TEX,
  visible_bounds_width: 16, visible_bounds_height: 10, visible_bounds_offset: [0, 4, 0]}, bones}]};

// ---- display: the V2 presentation (same space), particle from the V3 atlas
const display = JSON.parse(fs.readFileSync(v2Display, 'utf8'));
display.textures.particle = 'apocalypse_firstlight:item/br51_01_v3_native';

const geoText = JSON.stringify(geo, null, '\t') + '\n';
const flat = convert(source, geo, {}, nativePath, 2);
const model = {...flat, format_version: 3, crease_angle: CREASE_ANGLE, crease_weighting: 'area'};
const meshText = serializeCompact(model);
if (meshText.length > 4 * 1024 * 1024) throw new Error('sidecar exceeds runtime 4 MiB limit: ' + meshText.length);
if (JSON.stringify(JSON.parse(meshText)) !== JSON.stringify(model)) throw new Error('compact sidecar changed data');
const png = Buffer.from(source.textures[0].source.split(',')[1], 'base64');
const displayText = JSON.stringify(display, null, 2) + '\n';
const pairs = [[specPath, out.spec], [normalPath, out.normal], ...copies];
if (process.argv.includes('--check')) {
  for (const [f, t] of [[out.geo, geoText], [out.mesh, meshText], [out.display, displayText]]) if (fs.readFileSync(f, 'utf8') !== t) throw new Error('stale ' + f);
  if (!fs.readFileSync(out.texture).equals(png)) throw new Error('stale ' + out.texture);
  for (const [src, dst] of pairs) if (!fs.existsSync(dst) || !fs.readFileSync(dst).equals(fs.readFileSync(src))) throw new Error('stale ' + dst);
} else {
  fs.writeFileSync(out.geo, geoText); fs.writeFileSync(out.mesh, meshText); fs.writeFileSync(out.texture, png); fs.writeFileSync(out.display, displayText);
  for (const [src, dst] of pairs) fs.copyFileSync(src, dst);
}
console.log(JSON.stringify({bones: bones.length, parts: model.parts.length, format_version: model.format_version, ...meshCounts(model),
  sidecarChars: meshText.length, outputs: [...Object.values(out), ...copies.map(c => c[1])].map(p => path.relative(root, p))}, null, 1));
