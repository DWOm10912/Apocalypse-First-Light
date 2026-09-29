// HR55 V2 Pure Mesh (Phase 2): rebuilds the HR55 cube bullpup as AFL hard-surface Pure Mesh on the frozen Phase 1 Native
// Rig, with a 1024 atlas: Base Color + LabPBR _s / _n painted in one raster pass (same method and material language as
// the BR51-01 V2 builder, tools/build-br51-01-v2-mesh.mjs).
//   node tools/build-hr55-v2-mesh.mjs            -> writes source, runtime geo / sidecar / maps
//   node tools/build-hr55-v2-mesh.mjs --check    -> verifies every output is up to date
//   node tools/build-hr55-v2-mesh.mjs --debug <out.bbmodel>  -> writes the source to <out> only (previews)
// Reference: the Phase 1 source at git REF (cube geometry = 3D design reference; groups, pivots and animations = rig).
// Method: tools/cube-slab-mesh-lib.mjs slab / lathe per original geometry group, with the lib's opt-in rules:
//   SPLIT arrays : the author's single `group` holds the whole body, so side-profile regions give it five materials
//   SNAP         : outlines snap back to the cube coordinates (exact rail tops, magazine seat, anchors)
//   THIN         : the author's six zero-thickness backing planes (receiver window and groove, lower-handguard vents)
//                  become 0.03-thick plates grown inward
//   GROW         : two-pass build; slabs whose faces are coplanar, same-facing and overlapping (z-fighting) get priority
//                  levels, and each winner is inflated by level x STEP so its face is always in front
// Visible ammunition is not modelled: the top rounds are drawn by the generic dynamic-ammo renderer (12.7x55mm Pure Mesh
// round) on the empty bullet1 / bullet2 bones and, for the loaded new magazine, on reload_bullet1 / reload_bullet2
// (added here under reload_mag_standard, whose zero scale hides them with the magazine outside reloads).
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {convert, serializeCompact} from './export-afl-mesh.mjs';
import {M4, add, mul, Part, inRegion, collectGroups, createBuilder, unwrap, paint, zFightLevels} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export const REF = 'e184568';   // HR55 Native Rig V2 (Phase 1) commit
const gitShow = p => execFileSync('git', ['show', `${REF}:${p}`], {cwd: ROOT, encoding: 'utf8', maxBuffer: 1 << 28});
export const ref = JSON.parse(gitShow('src/main/blockbench/hr55.bbmodel'));
function assert(c, m) { if (!c) throw new Error(m); }

// ---------------- reference cubes (bind pose, model space) ----------------
// reload_mag_standard is a cube-for-cube copy of mag_standard (built as a mesh copy below)
export const groupInfo = collectGroups(ref, new Set(['reload_magazine']));

// ---------------- build rules ----------------
const MAT = {group: 'receiver', group2: 'handguard', group3: 'stock', sight: 'sight', octagon: 'barrel', octagon2: 'muzzle', octagon3: 'muzzle',
  octagon4: 'muzzle', bolt: 'handle', bone2: 'handle', mag_standard: 'magazine'};
const AXIS = {octagon4: 'z'};   // octagonal ported brake: front sections along the bore keep its facets and ports
const EPS = {group: 0.07, group2: 0.06, group3: 0.07, sight: 0.05, mag_standard: 0.07, bone2: 0.05};
const CHAMFER = {receiver: 0.04, lower: 0.04, handguard: 0.04, rail: 0.02, sight: 0.03, handle: 0.025, stock: 0.05, grip: 0.05, magazine: 0.035, muzzle: 0.03};
const R = (z0, z1, y0, y1) => [[z0, y0], [z1, y0], [z1, y1], [z0, y1]];   // side-profile (z, y) rectangle
// Regions (side profile z, y; first match wins). Receiver side panels end at y 5.8125: below it the pistol grip with the
// trigger guard (front of z 1.6) and the magazine-well / lower shell (behind); the upper handguard is the body ahead of the
// receiver chamfer break at z -6.25; the Picatinny rails ride on y 9.0 (receiver) and y 11.234 (carry handle) and under
// the lower handguard (y < 5.26).
export const SPLIT = {
  group: [
    {region: R(-13.8, 3.95, 9.0, 9.5), name: 'group_rail', mat: 'rail'},
    {region: R(-14.0, -6.25, 5.0, 9.0), name: 'group_handguard', mat: 'handguard'},
    {region: R(-4.7, 1.6, 0.3, 5.8125), name: 'group_grip', mat: 'grip'},
    {region: R(1.6, 14.3, 3.0, 5.8125), name: 'group_lower', mat: 'lower'},
  ],
  group2: [{region: R(-14.0, -3.4, 4.9, 5.26), name: 'group2_rail', mat: 'rail'}],
  sight: [{region: R(-4.85, 1.1, 11.2, 11.5), name: 'sight_rail', mat: 'rail'}],
};
export const STEP = 0.0025;   // GROW step (model units): ~1/400 texel at 16 px / unit, far above depth-buffer resolution

function assemble(GROW) {
  const builder = createBuilder({MAT, AXIS, EPS, CHAMFER, SPLIT, defaultMat: 'receiver', latheMat: 'barrel', SNAP: true, GROW, THIN: 0.03});
  const report = {};
  for (const [name, info] of groupInfo) {
    if (info.hidden || !info.cubes.length) continue;
    if (['bullet1', 'bullet2', 'reload_mag_standard'].includes(name)) continue;   // dynamic ammo / magazine copy
    if (['octagon', 'octagon2', 'octagon3'].includes(name)) { report[name] = builder.latheGroup(name, info, name === 'octagon' ? 20 : 16); continue; }
    report[name] = builder.slabGroup(name, info);
  }
  // faces of the split groups built along other axes (front / top sections, boxes) follow the same regions by centroid
  for (const [name, list] of Object.entries(SPLIT)) {
    const part = builder.PARTS.find(p => p.name === name);
    for (const f of part.f) {
      const c = f.ids.reduce((a, i) => add(a, mul(part.v[i], 1 / f.ids.length)), [0, 0, 0]);
      const sp = list.find(s => inRegion([c[2], c[1]], s.region)); if (sp) f.mat = sp.mat;
    }
  }
  return {builder, report};
}
// Z-fighting: build, find the coplanar same-facing overlaps, rebuild with priority inflation; repeat until none are left
// (faces grown by the same step can newly overlap along a shared edge, and a grown face can land on a neighbour's plane:
// a winner still in conflict with a slab it already beats is lifted one more level)
const zSkip = () => false, zHistory = [], floor = new Map();
let GROW_MAP = null, pass, zf = null;
for (let it = 0; ; it++) {
  pass = assemble(GROW_MAP);
  const prior = zf?.beats, last = zf?.levels;
  if (prior) for (const p of zFightLevels(pass.builder.PARTS, pass.builder.SLABS, {skip: zSkip}).pairs)
    if (prior.get(p.win)?.has(p.lose)) floor.set(p.win, (last.get(p.win) || 0) + 1);
  zf = zFightLevels(pass.builder.PARTS, pass.builder.SLABS, {skip: zSkip, prior, floor});
  zHistory.push({pairs: zf.pairs.length, area: +zf.area.toFixed(4), unresolved: zf.unresolved.length});
  if (!zf.pairs.length && !zf.unresolved.length) break;
  if (process.env.HR55_ZF_DEBUG && it >= 3) break;
  assert(it < 8 && !zf.unresolved.length, 'z-fighting did not converge: ' + JSON.stringify(zHistory) + ' ' + JSON.stringify(zf.pairs.map(p => [p.win, p.lose, zf.levels.get(p.win), zf.levels.get(p.lose), pass.builder.SLABS.get(p.win), pass.builder.SLABS.get(p.lose), p.plane])));
  GROW_MAP = new Map([...zf.levels].map(([k, l]) => [k, l * STEP]));
}
const {builder, report: buildReport} = pass;
export const GROW = GROW_MAP || new Map(), PARTS = builder.PARTS, report = buildReport;
export const zFight = {passes: zHistory, maxLevel: Math.max(0, ...zf.levels.values()), grown: GROW.size, maxGrow: Math.max(0, ...GROW.values())};

// ---------------- magazine copy on the reload bone ----------------
{
  const o = PARTS.find(q => q.name === 'mag_standard'), c = new Part('reload_mag_standard', 'reload_mag_standard', o.mat);
  c.v = o.v.map(q => q.slice()); c.f = o.f.map(f => ({...f, ids: f.ids.slice()})); c.copyOf = o; PARTS.push(c);
}
for (const p of PARTS) assert(groupInfo.has(p.bone), 'bone ' + p.bone);

// ---------------- UV: planar islands (normal flood fill), shelf packing into 1024 ----------------
const ATLAS = 1024, PAD = 1;
const unwrapped = unwrap(PARTS, {atlas: ATLAS, pad: PAD, startS: 64, stepS: 0.5});
export const islands = unwrapped.islands, texelsPerUnit = unwrapped.S;
const S = unwrapped.S, faceUV = unwrapped.faceUV;

// ---------------- Base Color + LabPBR (same raster pass) ----------------
// Black-steel heavy bullpup in the BR51 V2 language: coated (phosphate / hard-anodised, F0 ~24) receiver, handguards,
// rails, sight and magazine; near-black barrel and ported brake; dark coated charging handle; black polymer grip,
// magazine-well shell and butt. Only the bevel strips are bare metal (worn edges). Values from the BR51 table.
export const MATS = {
  receiver:  {c: [40, 43, 46], hl: 22, sm: 86, se: 140, f0: 24, edgeF0: 255},
  handguard: {c: [43, 46, 49], hl: 22, sm: 96, se: 145, f0: 24, edgeF0: 255},
  rail:      {c: [33, 35, 38], hl: 18, sm: 90, se: 138, f0: 24, edgeF0: 255},
  sight:     {c: [36, 38, 41], hl: 18, sm: 80, se: 130, f0: 24, edgeF0: 255},
  barrel:    {c: [28, 29, 31], hl: 14, sm: 92, se: 140, f0: 24, edgeF0: 255},
  muzzle:    {c: [32, 33, 35], hl: 16, sm: 84, se: 132, f0: 24, edgeF0: 255},
  handle:    {c: [41, 43, 46], hl: 20, sm: 86, se: 136, f0: 24, edgeF0: 255},
  magazine:  {c: [39, 41, 43], hl: 20, sm: 82, se: 132, f0: 24, edgeF0: 255},
  lower:     {c: [31, 32, 34], hl: 7, sm: 58, se: 70, f0: 10},
  stock:     {c: [30, 31, 33], hl: 7, sm: 56, se: 68, f0: 10},
  grip:      {c: [28, 29, 30], hl: 6, sm: 48, se: 60, f0: 10},
};
const ZONED = new Set(['receiver', 'handguard', 'rail', 'sight', 'barrel', 'muzzle', 'handle', 'magazine']);   // polymer: one tone
const SOURCE_OF = {group_rail: 'group', group_handguard: 'group', group_grip: 'group', group_lower: 'group', group2_rail: 'group2', sight_rail: 'sight'};
const sourceGroups = p => { const b = p.copyOf || p, nm = SOURCE_OF[b.name] || b.name; return groupInfo.has(nm) && groupInfo.get(nm).cubes.length ? [nm] : []; };
const painted = paint({PARTS, islands, S, uvOf: unwrapped.uvOf, atlas: ATLAS, pad: PAD, MATS, ZONED, groupInfo, sourceGroups,
  refTexture: ref.textures[0], refUvWidth: ref.textures[0].uv_width || ref.resolution.width,
  background: {c: [40, 42, 44, 255], s: [86, 24, 0, 255], n: [128, 128, 255, 255]}});
export const MAT_REF = painted.MAT_REF, zoneStats = painted.zoneStats, PNG = painted.PNG;

// ---------------- dynamic ammo bones on the loaded (new) magazine ----------------
// Same pivots as bullet1 / bullet2; parented to reload_mag_standard so its zero scale hides them outside reloads.
export const RELOAD_ROUNDS = [['reload_bullet1', 'bullet1'], ['reload_bullet2', 'bullet2']];

// ---------------- reload_empty: the right hand's grip offset follows the hand (2026-09-29) ----------------
// Every HR55 clip carries a constant right_hand_anchor position [0, -7, 0] that composes the idle right hand. In
// reload_empty the right hand leaves the grip: it pulls the old magazine, seats the new one and slaps the charging handle
// forward, and there the author's contacts only line up without that offset (hand to magazine floor 1.2-2.5 / 1.4-4,
// hand to charging-handle knob 1.5-3.6 units; with it 8-10). The offset now blends out while the hand swings from the
// grip to the old magazine (0.083 -> 0.25 s) and back in while it returns from the charging handle to the grip
// (3.217 -> 3.467 s), so the clip still starts and ends on the idle pose. Nothing else in the animation changes.
export const RELOAD_EMPTY_RIGHT_HAND = [[0, -7], [0.0833, -7], [0.25, 0], [3.2167, 0], [3.4667, -7]];
const ANIM_PATH = 'src/main/resources/assets/apocalypse_firstlight/animations/hr55.animation.json';
const animText0 = gitShow(ANIM_PATH).replace(/\r\n/g, '\n'), anim = JSON.parse(animText0);
assert(JSON.stringify(anim, null, 2) === animText0, 'Phase 1 animation file is not in canonical two-space JSON');
anim.animations.reload_empty.bones.right_hand_anchor.position = Object.fromEntries(RELOAD_EMPTY_RIGHT_HAND.map(([t, y]) => [t ? String(t) : '0.0', {vector: [0, y, 0]}]));

// ---------------- outputs ----------------
const uuid = s => { const h = createHash('sha256').update('afl-hr55-v2-native:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r12 = v => +v.toFixed(12) || 0;   // full bake precision: V2 keeps a planar quad only when the source proves it
export function buildSource() {
  const src = structuredClone(ref), byName = new Map(src.groups.map(g => [g.name, g]));
  const find = (ns, id) => { for (const n of ns) { if (typeof n === 'string') continue; if (n.uuid === id) return n; const r = find(n.children, id); if (r) return r; } };
  const keep = new Set(src.elements.filter(e => e.export === false).map(e => e.uuid));   // source-only reference arms
  (function strip(ns) { for (const n of ns) if (typeof n !== 'string') { n.children = n.children.filter(c => typeof c !== 'string' || keep.has(c)); strip(n.children); } })(src.outliner);
  src.elements = src.elements.filter(e => keep.has(e.uuid));
  for (const p of PARTS) {
    const g = byName.get(p.bone), inv = M4.inv(groupInfo.get(p.bone).static), node = find(src.outliner, g.uuid), uvs = faceUV.get(p.copyOf || p);
    const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {};
    p.v.forEach((q, i) => { vertices[key(i)] = M4.pt(inv, q).map(r12); });
    (p.copyOf || p).f.forEach((f, fi) => { const uv = uvs.get(f); faces['f' + key(fi)] = {uv: Object.fromEntries(f.ids.map((id, j) => [key(id), uv[j].map(r12)])), vertices: f.ids.map(key), texture: 0}; });
    const id = uuid('mesh:' + p.name);
    src.elements.push({name: p.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: true, locked: false,
      render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: id});
    node.children.push(id);
  }
  const parent = find(src.outliner, byName.get('reload_mag_standard').uuid);
  for (const [name, like] of RELOAD_ROUNDS) {
    const g0 = byName.get(like), id = uuid('group:' + name);
    src.groups.push({...structuredClone(g0), name, uuid: id, origin: g0.origin.slice(), rotation: (g0.rotation || [0, 0, 0]).slice()});
    parent.children.push({uuid: id, isOpen: false, children: []});
  }
  for (const g of src.groups) delete g.bedrock_binding;   // Bedrock-only field; the Free Model source has no binding
  // reload_empty right-hand contact (same keys as the runtime clip below; Blockbench position = runtime (-x, y, z))
  const anchor = src.animations.find(a => a.name === 'reload_empty').animators[byName.get('right_hand_anchor').uuid];
  anchor.keyframes = [...anchor.keyframes.filter(k => k.channel !== 'position'), ...RELOAD_EMPTY_RIGHT_HAND.map(([t, y]) => ({channel: 'position',
    data_points: [{x: '0', y: String(y), z: '0'}], uuid: uuid('reload_empty:right_hand_anchor:position:' + t), time: t, color: -1, interpolation: 'linear'}))];
  src.meta = {...src.meta, model_format: 'free'};
  src.resolution = {width: ATLAS, height: ATLAS};
  const t0 = src.textures[0];
  src.textures = [{...t0, name: 'hr55.png', width: ATLAS, height: ATLAS, uv_width: ATLAS, uv_height: ATLAS, relative_path: 'textures/hr55.png', folder: '', namespace: '',
    source: 'data:image/png;base64,' + PNG[0].toString('base64')}, ...src.textures.slice(1)];
  return src;
}
const source = buildSource();
// runtime geo: the Phase 1 bones unchanged (names, parents, pivots, rotations), cubes removed, 1024 atlas, + reload rounds
const geo = JSON.parse(gitShow('src/main/resources/assets/apocalypse_firstlight/geo/hr55.geo.json'));
{
  const G = geo['minecraft:geometry'][0];
  Object.assign(G.description, {texture_width: ATLAS, texture_height: ATLAS});
  for (const b of G.bones) delete b.cubes;
  const at = G.bones.findIndex(b => b.name === 'reload_mag_standard');
  G.bones.splice(at + 1, 0, ...RELOAD_ROUNDS.map(([name, like]) => ({name, parent: 'reload_mag_standard', pivot: G.bones.find(b => b.name === like).pivot.slice()})));
}
const sidecar = convert(source, geo, {}, 'hr55.bbmodel', 2);
const meshText = serializeCompact(sidecar);
assert(meshText.length < 4 * 1024 * 1024, 'sidecar exceeds runtime 4 MiB limit');
assert(JSON.stringify(JSON.parse(meshText)) === JSON.stringify(sidecar), 'compact sidecar changed data');

const bb = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const OUT = {src: path.join(bb, 'hr55.bbmodel'), geo: path.join(assets, 'geo/hr55.geo.json'), mesh: path.join(assets, 'meshes/hr55.aflmesh.json'), anim: path.join(ROOT, ANIM_PATH),
  maps: ['', '_s', '_n'].map(k => [path.join(bb, `textures/hr55${k}.png`), path.join(assets, `textures/item/hr55${k}.png`)])};
const outputs = [[OUT.src, JSON.stringify(source)], [OUT.geo, JSON.stringify(geo, null, 2)], [OUT.mesh, meshText], [OUT.anim, JSON.stringify(anim, null, 2)],
  ...OUT.maps.flatMap(([s, r], k) => [[s, PNG[k]], [r, PNG[k]]])];

// counts: whole sidecar, and the idle state (one magazine: the reload copy is zero-scaled outside reloads)
const COPIES = new Set(PARTS.filter(p => p.copyOf).map(p => p.name));
const count = parts => { const f = parts.flatMap(p => p.faces); return {triangleEquivalent: f.reduce((s, q) => s + q.length - 2, 0), faces: f.length,
  quadFaces: f.filter(q => q.length === 4).length, triangleFaces: f.filter(q => q.length === 3).length, vertexSubmissions: f.length * 4}; };
export const stats = {all: count(sidecar.parts), idle: count(sidecar.parts.filter(p => !COPIES.has(p.name))), parts: sidecar.parts.length,
  texelsPerUnit: S, islands: islands.length, zFight,
  byPart: Object.fromEntries(sidecar.parts.filter(p => !COPIES.has(p.name)).map(p => [p.name, p.faces.reduce((s, q) => s + q.length - 2, 0)]))};
const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  console.log(JSON.stringify(stats));
  if (process.argv.includes('--debug')) fs.writeFileSync(process.argv[process.argv.indexOf('--debug') + 1], JSON.stringify(source));
  else if (process.argv.includes('--check')) {
    for (const [file, data] of outputs) { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; if (!cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data))) throw new Error('stale ' + path.relative(ROOT, file)); }
    console.log('CHECK OK');
  } else {
    for (const [file, data] of outputs) fs.writeFileSync(file, data);
    console.log('wrote ' + outputs.map(([f]) => path.relative(ROOT, f)).join(', '));
  }
}
