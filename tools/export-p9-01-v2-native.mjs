// P9-01 V2 AFL Native export: writes NEW runtime resources only (never touches the legacy p9_01 resources).
//   node tools/export-p9-01-v2-native.mjs [--check]
// Outputs: geo/p9_01_v2_native.geo.json (bones only), meshes/p9_01_v2_native.aflmesh.json (Pure Mesh sidecar via
// tools/export-afl-mesh.mjs), textures/item/p9_01_v2_native.png (Base Color) + _s / _n (LabPBR, copied from the generator
// output in src/main/blockbench/textures/), models/item/p9_01_v2_native_in_hand.json.
// The animation JSON is written by tools/author-p9-01-v2-native-animations.mjs --write-runtime.
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {convert, serializeCompact, meshCounts} from './export-afl-mesh.mjs';
import {nativePath, specPath, normalPath, FP_SCALE} from './build-p9-01-v2-native.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const assets = path.join(root, 'src/main/resources/assets/apocalypse_firstlight');
const out = {
  geo: path.join(assets, 'geo/p9_01_v2_native.geo.json'),
  mesh: path.join(assets, 'meshes/p9_01_v2_native.aflmesh.json'),
  texture: path.join(assets, 'textures/item/p9_01_v2_native.png'),
  spec: path.join(assets, 'textures/item/p9_01_v2_native_s.png'),
  normal: path.join(assets, 'textures/item/p9_01_v2_native_n.png'),
  display: path.join(assets, 'models/item/p9_01_v2_native_in_hand.json'),
};
// Legacy P9 runtime files must stay untouched while they exist (the P9 runtime migration retired and removed them).
const LEGACY = ['geo/p9_01.geo.json', 'animations/p9_01.animation.json', 'textures/item/p9_01.png', 'models/item/p9_01_in_hand.json']
  .filter(p => fs.existsSync(path.join(assets, p)));

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
const geo = {format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: 'geometry.p9_01_v2_native', texture_width: 1024, texture_height: 1024,
  visible_bounds_width: 16, visible_bounds_height: 10, visible_bounds_offset: [0, 4, 0]}, bones}]};

// ---- first-person Display: the right hand anchor lands on exactly the same view point as Blackridge's, so both
// pistols share one arm pose; P9 scale 0.53 keeps the hand/grip proportion of a 9 mm duty pistol.
// Hand anchor rest points are the authored RH_POINTs (Blackridge [0,3.70,1.7] @0.45, P9 [0,2.11,0.95] @0.53).
const BR = {tr: [3.25, -7, -12.25], s: 0.45, rh: [0, 7.05 - 4 * 0.62 / 0.37 / 2, 1.7]};
const ARM_HALF = 4 * 0.62 / FP_SCALE / 2, P9_RH = [0, 4.45 - ARM_HALF, 0.95];
const view = (tr, s, p) => [0, 1, 2].map(i => tr[i] / 16 + s * ((i === 1 ? 0.01 : 0) + p[i] / 16));
const target = view(BR.tr, BR.s, BR.rh);
const fpTr = [0, 1, 2].map(i => r((target[i] - FP_SCALE * ((i === 1 ? 0.01 : 0) + P9_RH[i] / 16)) * 16));
const k = FP_SCALE / BR.s, sc = s => [r(s * k), r(s * k), r(s * k)];
const display = {
  parent: 'builtin/entity',
  textures: {particle: 'apocalypse_firstlight:item/p9_01_v2_native'},
  display: {
    firstperson_righthand: {translation: fpTr, rotation: [0, 0, 0], scale: [FP_SCALE, FP_SCALE, FP_SCALE]},
    firstperson_lefthand: {translation: fpTr, rotation: [0, 0, 0], scale: [FP_SCALE, FP_SCALE, FP_SCALE]},
    thirdperson_righthand: {rotation: [0, 0, 0], translation: [0, -1.7, -4.5], scale: sc(0.42)},
    thirdperson_lefthand: {rotation: [0, 0, 0], translation: [0, -1.7, -4.5], scale: sc(0.42)},
    gui: {rotation: [20, 135, 0], translation: [0, -3, 0], scale: sc(0.24)},
    ground: {rotation: [0, 0, 90], translation: [0, 1, 0], scale: sc(0.3)},
    fixed: {rotation: [0, 90, 0], scale: sc(0.25)},
  },
};

const geoText = JSON.stringify(geo, null, '\t') + '\n';
// Keep numeric vertex/face rows compact and retain the runtime 4 MiB limit.
// Only this migrated asset opts into V2; the shared converter's default remains V1.
const model = convert(source, geo, {}, nativePath, 2);
const meshText = serializeCompact(model);
if (meshText.length > 4 * 1024 * 1024) throw new Error('sidecar exceeds runtime 4 MiB limit');
if (JSON.stringify(JSON.parse(meshText)) !== JSON.stringify(model)) throw new Error('compact sidecar changed data');
const png = Buffer.from(source.textures[0].source.split(',')[1], 'base64');
const displayText = JSON.stringify(display, null, 2) + '\n';
const legacyBefore = LEGACY.map(p => fs.readFileSync(path.join(assets, p)));
if (process.argv.includes('--check')) {
  for (const [f, t] of [[out.geo, geoText], [out.mesh, meshText], [out.display, displayText]]) if (fs.readFileSync(f, 'utf8') !== t) throw new Error('stale ' + f);
  if (!fs.readFileSync(out.texture).equals(png)) throw new Error('stale ' + out.texture);
  for (const [src, dst] of [[specPath, out.spec], [normalPath, out.normal]]) if (!fs.existsSync(dst) || !fs.readFileSync(dst).equals(fs.readFileSync(src))) throw new Error('stale ' + dst);
} else {
  fs.writeFileSync(out.geo, geoText); fs.writeFileSync(out.mesh, meshText); fs.writeFileSync(out.texture, png); fs.writeFileSync(out.display, displayText);
  fs.copyFileSync(specPath, out.spec); fs.copyFileSync(normalPath, out.normal);
}
LEGACY.forEach((p, i) => { if (!fs.readFileSync(path.join(assets, p)).equals(legacyBefore[i])) throw new Error('legacy resource changed: ' + p); });
console.log(JSON.stringify({bones: bones.length, parts: model.parts.length, format_version: model.format_version, ...meshCounts(model),
  fpTranslation: fpTr, fpScale: FP_SCALE, outputs: Object.values(out).map(p => path.relative(root, p))}, null, 1));
