// BR51-01 V2 Pure Mesh (Phase 2): rebuilds the approved BR51 cube rifle as AFL hard-surface Pure Mesh on the frozen
// Phase 1 Native Rig, with a 1024 atlas: Base Color + LabPBR _s / _n painted in one raster pass.
//   node tools/build-br51-01-v2-mesh.mjs            -> writes source, runtime geo / sidecar / maps
//   node tools/build-br51-01-v2-mesh.mjs --check    -> verifies every output is up to date
//   node tools/build-br51-01-v2-mesh.mjs --debug <out.bbmodel>  -> writes the source to <out> only (previews)
// Reference: the Phase 1 source at git REF (cube geometry = 3D design reference; groups, pivots and animations = rig).
// Method, per original geometry group:
//   slab  : the group's cubes are layered by their extent along the extrusion axis (x = side profile, y = top profile);
//           each layer's projected footprint is rasterised, traced, simplified (cube stair-steps become straight edges)
//           and extruded with a real chamfer. Layers fully enclosed by a wider layer are dropped (hidden geometry).
//           Cubes rotated about another axis (the cube modeller's 45 deg edge fillers) are dropped when a regular
//           layer already covers them, otherwise kept as oriented boxes.
//   lathe : octagon-built round parts (barrel, gas tube, collars, bolt face, sight drum) become true cylinders.
//   custom: flash hider (revolved tube + four prongs). No visible ammunition (see the ammo note below).
// Silhouette is inherited from the cube footprints by construction; see the verification printout.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {convert, serializeCompact} from './export-afl-mesh.mjs';
import {applySource as applyInspectRack} from './br51-inspect-rack.mjs';
import {M4, add, mul, Part, revolve, box, inRegion, collectGroups, createBuilder, unwrap, paint} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export const REF = '38b6c66';   // BR51 Native Rig V2 (Phase 1) commit
const gitShow = p => execFileSync('git', ['show', `${REF}:${p}`], {cwd: ROOT, encoding: 'utf8', maxBuffer: 1 << 28});
export const ref = JSON.parse(gitShow('src/main/blockbench/br51_01.bbmodel'));

// Toolkit (math, 2D polygons, footprint tracing, extrusion, slab / lathe builder, UV islands, painter, PNG):
// tools/cube-slab-mesh-lib.mjs (extracted unchanged from this builder, 2026-09-28; shared with the BR51 35-round magazine).

// ---------------- reference cubes (bind pose, model space) ----------------
const SOURCE_ONLY = new Set(['sight', 'grip_default', 'reload_magazine', 'empty_old_mag']);   // not rebuilt from (copies / source-only)
export const groupInfo = collectGroups(ref, SOURCE_ONLY);   // name -> {group, parent, static (bind matrix), cubes: []}
export {Part};

// ---------------- build rules ----------------
// material per original group (overridden per region below)
const MAT = {
  br51_01_default: 'receiver', bone5: 'receiver', octagon9: 'sight', bone6: 'lower', bone: 'rail', bone2: 'handguard', bone3: 'handguard',
  bone4: 'handguard', group49: 'rail', group2: 'rail', group3: 'rail', bone8: 'receiver', bone7: 'receiver', ar_stock_adapter_1: 'hinge',
  stock_default: 'stock', octagon: 'barrel', octagon8: 'barrel', octagon3: 'barrel', octagon5: 'barrel', octagon2: 'barrel', octagon6: 'barrel',
  bone13: 'rail', bone11: 'rail', bone12: 'rail', bone14: 'rail', bone20: 'rail', bone21: 'rail', afl_iron_risers: 'sight', muzzle_default: 'muzzle',
  bolt: 'bolt', octagon7: 'bolt', bone10: 'bolt', mag_standard: 'magazine', hu2: 'floorplate',
};
const AXIS = {};   // per-group override; otherwise the axis with the fewest non-conforming cubes (x preferred on ties)
// target bone for each group's mesh: statically rotated or unanimated helper groups attach to an identity-framed ancestor
const BONE = {group2: 'br51_01_default', group3: 'br51_01_default', '3': 'bullet', '7': 'bullet', bullet_in_barrel: 'bullet_in_barrel'};
const EPS = {bone6: 0.09, stock_default: 0.07, mag_standard: 0.07, hu2: 0.05, bone4: 0.07, bone5: 0.06, group49: 0.06, bone8: 0.06};
const CHAMFER = {receiver: 0.04, lower: 0.04, handguard: 0.04, rail: 0.02, sight: 0.025, bolt: 0.025, hinge: 0.03, stock: 0.04, magazine: 0.035, floorplate: 0.04, grip: 0.05};
// regions of the lower receiver (side profile z, y): pistol grip is polymer
const GRIP_REGION = [[8.0, 2.8], [15.6, 2.8], [15.6, 8.55], [8.0, 8.55]];   // whole grip incl. heel and trigger guard
const SPLIT = {bone6: {region: GRIP_REGION, name: 'bone6_grip', mat: 'grip'}};
const builder = createBuilder({MAT, AXIS, BONE, EPS, CHAMFER, SPLIT, defaultMat: 'receiver', latheMat: 'barrel'});
export const PARTS = builder.PARTS;
const {P, slabGroup, latheGroup} = builder;
const round = (v, k = 5) => +v.toFixed(k);

// ---- assemble ----
export const report = {};
for (const [name, info] of groupInfo) {
  if (info.hidden || !info.cubes.length) continue;
  if (['3', '7', 'bullet_in_barrel', 'muzzle_default'].includes(name)) continue;   // custom below
  if (/^octagon\d*$/.test(name)) {
    const pts = info.cubes.flatMap(c => c.corners), dx = Math.max(...pts.map(p => p[0])) - Math.min(...pts.map(p => p[0])), dy = Math.max(...pts.map(p => p[1])) - Math.min(...pts.map(p => p[1]));
    if (Math.abs(dx - dy) < 0.06) { report[name] = latheGroup(name, info, name === 'octagon' ? 20 : name === 'octagon9' || name === 'octagon7' ? 12 : 16); continue; }
  }
  report[name] = slabGroup(name, info);
}
// flash hider: revolved bored body + four prongs (reference cube radii)
{
  const part = P('muzzle_default', 'muzzle_default', 'muzzle'), cy = 11.4375;
  revolve(part, 0, cy, [[-23.219, 0.30], [-23.219, 0.5625], [-23.984, 0.5625], [-23.984, 0.469], [-25.75, 0.469], [-25.75, 0.438], [-25.813, 0.438],
    [-25.813, 0.469], [-26.375, 0.469], [-26.375, 0.281], [-23.219 - 0.02, 0.281]], 16);
  for (const c of groupInfo.get('muzzle_default').cubes.filter(c => Math.min(...c.corners.map(p => p[2])) < -27.9)) box(part, c, 'side');
}
// faces of the split groups built along other axes (front / top sections, boxes) follow the same region by centroid
for (const [name, sp] of Object.entries(SPLIT)) { const part = PARTS.find(p => p.name === name);
  for (const f of part.f) { const c = f.ids.reduce((a, i) => add(a, mul(part.v[i], 1 / f.ids.length)), [0, 0, 0]); if (inRegion([c[2], c[1]], sp.region)) f.mat = sp.mat; } }

// No visible ammunition (2026-09-28): the 7.62 round rework comes first, then dynamic ammo display. The ammo bones
// (bullet -> 2 -> 3 / 7, bullet_in_barrel, reload_bullet*) stay in the rig with no geometry, so the Phase 1 animations
// keep every channel and the later dynamic-ammo logic has its mount points.

// ---------------- magazine states: identical copies on the reload / dropped-magazine bones ----------------
// (copy bones share the originals' bind pose; only one state is visible at a time, the others are zero-scaled)
function assert(c, m) { if (!c) throw new Error(m); }
for (const [src, dst] of [['mag_standard', 'reload_mag_standard'], ['hu2', 'reload_hu2'], ['mag_standard', 'empty_old_mag_standard'], ['hu2', 'empty_old_hu2'],
  ]) {
  const o = PARTS.find(q => q.name === src), c = new Part(dst, dst, o.mat);
  c.v = o.v.map(q => q.slice()); c.f = o.f.map(f => ({...f, ids: f.ids.slice()})); c.copyOf = o; PARTS.push(c);
}
for (const p of PARTS) assert(groupInfo.has(p.bone), 'bone ' + p.bone);

// ---------------- UV: planar islands (normal flood fill), shelf packing into 1024 ----------------
const ATLAS = 1024, PAD = 1;   // nearest-filtered entity texture (no mipmaps): 1 px gutter suffices
const unwrapped = unwrap(PARTS, {atlas: ATLAS, pad: PAD, startS: 64, stepS: 0.5});
export const islands = unwrapped.islands, texelsPerUnit = unwrapped.S;
const S = unwrapped.S, faceUV = unwrapped.faceUV;

// ---------------- Base Color + LabPBR (same raster pass) ----------------
// modern dark battle rifle: graphite coated receiver, cooler anodised handguard, darker rails, near-black nitrided barrel,
// brighter machined bolt, dark treated sights, stamped-steel magazine, black polymer stock / grip / floorplate.
// Separation through value + smoothness + metal; bevel highlights, hole-wall occlusion and the original artist's
// panel zoning (below) keep the surfaces from reading as an untextured clay model.
export const MATS = {   // base colour, bevel highlight (added), smoothness open / edge, F0 open (255 metal, else linear F0), F0 of bevels
  // Texture pass (2026-09-28): black-steel rifle one step darker; every large surface is a COATING (phosphate /
  // ceramic-coated steel, hard-anodised aluminium: F0 ~0.09, matte) so shader packs no longer mirror the sky off the
  // side panels; only the machined bolt group is bare metal, and the bevel strips read as worn bare edges (metal F0).
  receiver:   {c: [40, 43, 46], hl: 22, sm: 86,  se: 140, f0: 24, edgeF0: 255},
  lower:      {c: [38, 40, 43], hl: 20, sm: 80,  se: 132, f0: 24, edgeF0: 255},
  handguard:  {c: [43, 46, 49], hl: 22, sm: 96,  se: 145, f0: 24, edgeF0: 255},
  rail:       {c: [33, 35, 38], hl: 18, sm: 90,  se: 138, f0: 24, edgeF0: 255},
  barrel:     {c: [28, 29, 31], hl: 14, sm: 92,  se: 140, f0: 24, edgeF0: 255},
  muzzle:     {c: [32, 33, 35], hl: 16, sm: 84,  se: 132, f0: 24, edgeF0: 255},
  bolt:       {c: [76, 78, 81], hl: 24, sm: 165, se: 190, f0: 255},
  hinge:      {c: [41, 43, 46], hl: 20, sm: 86,  se: 136, f0: 24, edgeF0: 255},
  sight:      {c: [35, 37, 40], hl: 18, sm: 76,  se: 128, f0: 24, edgeF0: 255},
  magazine:   {c: [39, 41, 43], hl: 20, sm: 82,  se: 132, f0: 24, edgeF0: 255},
  floorplate: {c: [25, 26, 27], hl: 5, sm: 62, se: 72, f0: 10},
  stock:      {c: [31, 32, 34], hl: 7, sm: 58, se: 70, f0: 10},
  grip:       {c: [28, 29, 30], hl: 6, sm: 48, se: 60, f0: 10},
};
// ---- tone zoning from the original artist's texture (cube-slab-mesh-lib paint) ----
// metals only: molded polymer (stock, grip, floorplate) is one uniform tone
const ZONED = new Set(['receiver', 'lower', 'handguard', 'rail', 'barrel', 'muzzle', 'hinge', 'sight', 'magazine', 'bolt']);
const sourceGroups = p => { const b = p.copyOf || p, nm = b.name === 'bone6_grip' ? 'bone6' : b.name; return groupInfo.has(nm) && groupInfo.get(nm).cubes.length ? [nm] : []; };
const painted = paint({PARTS, islands, S, uvOf: unwrapped.uvOf, atlas: ATLAS, pad: PAD, MATS, ZONED, groupInfo, sourceGroups,
  refTexture: ref.textures[0], refUvWidth: ref.textures[0].uv_width || ref.resolution.width,
  background: {c: [40, 42, 44, 255], s: [100, 255, 0, 255], n: [128, 128, 255, 255]}});
export const MAT_REF = painted.MAT_REF, zoneStats = painted.zoneStats;

// ---------------- outputs ----------------
export const PNG = painted.PNG;
const uuid = s => { const h = createHash('sha256').update('afl-br51-01-v2-native:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r6 = v => +v.toFixed(12) || 0;   // full bake precision: V2 keeps a planar quad only when the source proves it
// source: the Phase 1 rig (groups, pivots, uuids, animations) with the reference cubes replaced by the new meshes;
// the source-only reference arms (export false) stay for authoring
export function buildSource() {
  const src = structuredClone(ref), byName = new Map(src.groups.map(g => [g.name, g]));
  const find = (ns, id) => { for (const n of ns) { if (typeof n === 'string') continue; if (n.uuid === id) return n; const r = find(n.children, id); if (r) return r; } };
  const keep = new Set(src.elements.filter(e => e.export === false).map(e => e.uuid));
  (function strip(ns) { for (const n of ns) if (typeof n !== 'string') { n.children = n.children.filter(c => typeof c !== 'string' || keep.has(c)); strip(n.children); } })(src.outliner);
  src.elements = src.elements.filter(e => keep.has(e.uuid));
  for (const p of PARTS) {
    const g = byName.get(p.bone), inv = M4.inv(groupInfo.get(p.bone).static), node = find(src.outliner, g.uuid), uvs = faceUV.get(p.copyOf || p);
    const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {};
    p.v.forEach((q, i) => { vertices[key(i)] = M4.pt(inv, q).map(r6); });
    (p.copyOf || p).f.forEach((f, fi) => { const uv = uvs.get(f); faces['f' + key(fi)] = {uv: Object.fromEntries(f.ids.map((id, j) => [key(id), uv[j].map(r6)])), vertices: f.ids.map(key), texture: 0}; });
    const id = uuid('mesh:' + p.name);
    src.elements.push({name: p.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: true, locked: false,
      render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: id});
    node.children.push(id);
  }
  src.meta = {...src.meta, model_format: 'free'};
  src.resolution = {width: ATLAS, height: ATLAS};
  const t0 = src.textures[0];
  src.textures = [{...t0, name: 'br51_01.png', width: ATLAS, height: ATLAS, uv_width: ATLAS, uv_height: ATLAS, source: 'data:image/png;base64,' + PNG[0].toString('base64')}, ...src.textures.slice(1)];
  return src;
}

const source = buildSource();
// Phase 2 retires the source-only TaCZ leftovers: the old cube red dot / laser (`sight`) and the cube foregrip
// (`grip_default`). Neither is exported; their animators carry no keyframes. Both stay recoverable from git REF.
{
  const drop = new Set(), dropEl = new Set(), G = new Map(source.groups.map(g => [g.uuid, g]));
  (function walk(ns, gone) { for (const n of ns) { if (typeof n === 'string') { if (gone) dropEl.add(n); continue; }
    const g = gone || ['sight', 'grip_default'].includes(G.get(n.uuid).name); if (g) drop.add(n.uuid); walk(n.children, g); } })(source.outliner, false);
  (function prune(ns) { for (const n of ns) if (typeof n !== 'string') { n.children = n.children.filter(c => typeof c === 'string' ? !dropEl.has(c) : !drop.has(c.uuid)); prune(n.children); } })(source.outliner);
  source.groups = source.groups.filter(g => !drop.has(g.uuid)); source.elements = source.elements.filter(e => !dropEl.has(e.uuid));
  for (const a of source.animations) for (const id of Object.keys(a.animators || {})) if (drop.has(id)) { assert(!a.animators[id].keyframes?.length, 'keyed ' + id); delete a.animators[id]; }
  console.error(`retired ${drop.size} groups / ${dropEl.size} cubes`);
}
for (const g of source.groups) delete g.bedrock_binding;   // Bedrock-only field; the Free Model source has no binding
Object.assign(source.textures[0], {relative_path: 'textures/br51_01.png', folder: '', namespace: ''});
// Inspect rack (2026-09-28, re-timed 2026-09-29): the author's inspect pulled the bolt back slowly (3.458 -> 3.75 s) with
// 'draw' standing in for the pull and let it forward (4.208 -> 4.292 s) silently. It is now one quick rack at the end of
// the gun's settle, synced to the empty reload's charging-handle sound reload_empty_4 (tools/br51-inspect-rack.mjs, which
// also rewrites the runtime clip); the two dedicated inspect_slide_* sounds of 2026-09-28 are no longer used.
{
  const inspect = source.animations.find(a => a.name === 'inspect');
  const fx = Object.values(inspect.animators).find(a => a.type === 'effect');
  fx.keyframes = fx.keyframes.filter(k => !(k.time === 3.5 && k.data_points[0].effect === 'apocalypse_firstlight:br51_01_draw'));
  const byName = new Map(source.groups.map(g => [g.name, g.uuid]));
  applyInspectRack(inspect, name => byName.get(name), uuid);
}
// Rifle Suppressor V1 (2026-09-28): muzzle_anchor = the muzzle-device mounting shoulder (front face of the flash hider's rear
// collar, where a QD suppressor seats), no longer mid-hider at z -26.2. Empty locator; nothing else in the rig moves.
export const MUZZLE_ANCHOR = [0, 11.4375, -23.98437];
source.groups.find(g => g.name === 'muzzle_anchor').origin = MUZZLE_ANCHOR.slice();
// runtime geo: the Phase 1 bones unchanged (names, parents, pivots, rotations), cubes removed, 1024 atlas
const geo = JSON.parse(gitShow(`src/main/resources/assets/apocalypse_firstlight/geo/br51_01.geo.json`));
Object.assign(geo['minecraft:geometry'][0].description, {texture_width: ATLAS, texture_height: ATLAS});
for (const b of geo['minecraft:geometry'][0].bones) delete b.cubes;
geo['minecraft:geometry'][0].bones.find(b => b.name === 'muzzle_anchor').pivot = [-MUZZLE_ANCHOR[0] || 0, MUZZLE_ANCHOR[1], MUZZLE_ANCHOR[2]];
const sidecar = convert(source, geo, {}, 'br51_01.bbmodel', 2);
const meshText = serializeCompact(sidecar);
assert(meshText.length < 4 * 1024 * 1024, 'sidecar exceeds runtime 4 MiB limit');
assert(JSON.stringify(JSON.parse(meshText)) === JSON.stringify(sidecar), 'compact sidecar changed data');

const root = ROOT, bb = path.join(root, 'src/main/blockbench'), assets = path.join(root, 'src/main/resources/assets/apocalypse_firstlight');
const OUT = {src: path.join(bb, 'br51_01.bbmodel'), geo: path.join(assets, 'geo/br51_01.geo.json'), mesh: path.join(assets, 'meshes/br51_01.aflmesh.json'),
  maps: ['', '_s', '_n'].map(k => [path.join(bb, `textures/br51_01${k}.png`), path.join(assets, `textures/item/br51_01${k}.png`)])};
const outputs = [[OUT.src, JSON.stringify(source)], [OUT.geo, JSON.stringify(geo, null, 2)], [OUT.mesh, meshText],
  ...OUT.maps.flatMap(([s, r], k) => [[s, PNG[k]], [r, PNG[k]]])];

// counts: whole sidecar, and the idle state (one magazine: reload / dropped copies are zero-scaled outside reloads)
const COPIES = new Set(PARTS.filter(p => p.copyOf).map(p => p.name));
const count = parts => { const f = parts.flatMap(p => p.faces); return {triangleEquivalent: f.reduce((s, q) => s + q.length - 2, 0), faces: f.length,
  quadFaces: f.filter(q => q.length === 4).length, triangleFaces: f.filter(q => q.length === 3).length, vertexSubmissions: f.length * 4}; };
export const stats = {all: count(sidecar.parts), idle: count(sidecar.parts.filter(p => !COPIES.has(p.name))), parts: sidecar.parts.length,
  texelsPerUnit: S, islands: islands.length,
  byPart: Object.fromEntries(sidecar.parts.filter(p => !COPIES.has(p.name)).map(p => [p.name, p.faces.reduce((s, q) => s + q.length - 2, 0)]))};
const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  console.log(JSON.stringify(stats));
  if (process.argv.includes('--debug')) fs.writeFileSync(process.argv[process.argv.indexOf('--debug') + 1], JSON.stringify(source));
  else if (process.argv.includes('--check')) {
    for (const [file, data] of outputs) { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; if (!cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data))) throw new Error('stale ' + path.relative(root, file)); }
    console.log('CHECK OK');
  } else {
    for (const [file, data] of outputs) fs.writeFileSync(file, data);
    console.log('wrote ' + outputs.map(([f]) => path.relative(root, f)).join(', '));
  }
}
