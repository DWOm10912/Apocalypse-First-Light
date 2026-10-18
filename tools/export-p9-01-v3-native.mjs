// P9-01 V3 AFL Native export: writes the V3 runtime resources only (the V2 resources stay untouched).
//   node tools/export-p9-01-v3-native.mjs [--check]
// Outputs: geo/p9_01_v3_native.geo.json (bones only, texture 2048), meshes/p9_01_v3_native.aflmesh.json (Vextris
// format 3: the V2 converter output plus crease_angle 35 / crease_weighting "area", the loader smooths the curved parts
// the way Blender's Weighted Normal did for the bake), textures/item/p9_01_v3_native.png (Base Color from the bbmodel)
// + _s / _n (LabPBR from src/main/blockbench/textures/), models/item/p9_01_v3_native_in_hand.json (V2 display),
// The clips (animations/p9_01_v3_native.animation.json) are written by tools/author-p9-01-v3-native-animations.mjs
// --write-runtime since 2026-10-17 (trained-tier reload / inspect, docs/dev/p9_reload_inspect_v1.md); before that they were
// a copy of the V2 runtime clips (same rig, same bone names).
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {convert, serializeCompact, meshCounts} from './export-afl-mesh.mjs';
import {nativePath, specPath, normalPath, TEX} from './build-p9-01-v3-native.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const assets = path.join(root, 'src/main/resources/assets/apocalypse_firstlight');
const out = {
  geo: path.join(assets, 'geo/p9_01_v3_native.geo.json'),
  mesh: path.join(assets, 'meshes/p9_01_v3_native.aflmesh.json'),
  texture: path.join(assets, 'textures/item/p9_01_v3_native.png'),
  spec: path.join(assets, 'textures/item/p9_01_v3_native_s.png'),
  normal: path.join(assets, 'textures/item/p9_01_v3_native_n.png'),
  display: path.join(assets, 'models/item/p9_01_v3_native_in_hand.json'),
};
const v2 = {
  display: path.join(assets, 'models/item/p9_01_v2_native_in_hand.json'),
  animation: path.join(assets, 'animations/p9_01_v2_native.animation.json'),
};
const CREASE_ANGLE = 35;   // = game_build.py CREASE: Blender marked edges at >= 35 degrees sharp before baking

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
const geo = {format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: 'geometry.p9_01_v3_native', texture_width: TEX, texture_height: TEX,
  visible_bounds_width: 16, visible_bounds_height: 10, visible_bounds_offset: [0, 4, 0]}, bones}]};

// ---- display: the V2 presentation (same rig, same hand anchors), particle from the V3 atlas
const display = JSON.parse(fs.readFileSync(v2.display, 'utf8'));
display.textures.particle = 'apocalypse_firstlight:item/p9_01_v3_native';

const geoText = JSON.stringify(geo, null, '\t') + '\n';
const flat = convert(source, geo, {}, nativePath, 2);
const model = {...flat, format_version: 3, crease_angle: CREASE_ANGLE, crease_weighting: 'area'};
const meshText = serializeCompact(model);
if (meshText.length > 4 * 1024 * 1024) throw new Error('sidecar exceeds runtime 4 MiB limit: ' + meshText.length);
if (JSON.stringify(JSON.parse(meshText)) !== JSON.stringify(model)) throw new Error('compact sidecar changed data');
const png = Buffer.from(source.textures[0].source.split(',')[1], 'base64');
const displayText = JSON.stringify(display, null, 2) + '\n';
const v2Before = Object.values(v2).map(p => fs.readFileSync(p));
if (process.argv.includes('--check')) {
  for (const [f, t] of [[out.geo, geoText], [out.mesh, meshText], [out.display, displayText]]) if (fs.readFileSync(f, 'utf8') !== t) throw new Error('stale ' + f);
  if (!fs.readFileSync(out.texture).equals(png)) throw new Error('stale ' + out.texture);
  for (const [src, dst] of [[specPath, out.spec], [normalPath, out.normal]]) if (!fs.existsSync(dst) || !fs.readFileSync(dst).equals(fs.readFileSync(src))) throw new Error('stale ' + dst);
} else {
  fs.writeFileSync(out.geo, geoText); fs.writeFileSync(out.mesh, meshText); fs.writeFileSync(out.texture, png); fs.writeFileSync(out.display, displayText);
  fs.copyFileSync(specPath, out.spec); fs.copyFileSync(normalPath, out.normal);
}
Object.values(v2).forEach((p, i) => { if (!fs.readFileSync(p).equals(v2Before[i])) throw new Error('V2 resource changed: ' + p); });
console.log(JSON.stringify({bones: bones.length, parts: model.parts.length, format_version: model.format_version, ...meshCounts(model),
  sidecarChars: meshText.length, outputs: Object.values(out).map(p => path.relative(root, p))}, null, 1));
