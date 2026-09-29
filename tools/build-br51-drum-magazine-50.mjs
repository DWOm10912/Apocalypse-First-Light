// BR51-01 50-Round Drum Magazine V1 (item br51_drum_magazine_50): the BR51 artist's reference drum (mag_extended_3,
// 51 cubes, src/main/blockbench/br51_drum_magazine.bbmodel at git REF) rebuilt as AFL hard-surface Pure Mesh with the
// BR51 V2 method, rules and texel density (tools/cube-slab-mesh-lib.mjs), one 512 atlas: Base Color + LabPBR _s / _n.
//   node tools/build-br51-drum-magazine-50.mjs          -> writes the editable source, source maps and runtime files
//   node tools/build-br51-drum-magazine-50.mjs --check  -> verifies every output is up to date
// Design (the artist's, kept): a box-section twin drum, not a round drum: the standard feed tower and lips (identical to
// the 20 / 35 round magazines), a collar, a 5-wide / 4-tall drum housing raked 10 deg with 45 deg corner fillers, a centre
// band on each side, top / bottom plates and a front cover. Materials: the feed tower is the magazine's coated steel,
// the collar, housing and cover are the BR51 stock polymer; plus the AFL extended-magazine identification (muted tan
// strip on each side, as the 35-round magazine and the P9 extended magazine).
// Mount contract (as the 35-round magazine): model origin = the shared pivot of mag_standard / reload_mag_standard /
// empty_old_mag_standard; the top-round bones stay live (siblings of the replaced bones).
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {convert, serializeCompact} from './export-afl-mesh.mjs';
import {M4, add, sub, mul, norm, Part, collectGroups, createBuilder, unwrap, paint} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export const REF = '38b6c66';   // BR-51 Native Rig Migration: drum reference extracted, origin = mag_standard pivot
export const ref = JSON.parse(execFileSync('git', ['show', `${REF}:src/main/blockbench/br51_drum_magazine.bbmodel`], {cwd: ROOT, encoding: 'utf8', maxBuffer: 1 << 26}));
export const ID = 'br51_drum_magazine_50', BONE_ROOT = ID + '_root';
export const groupInfo = collectGroups(ref);
{
  // split the single source group: unraked cubes = feed tower and lips (steel), raked cubes = collar / drum / cover (polymer)
  const src = groupInfo.get('mag_extended_3');
  if (!src || src.cubes.length !== 51) throw new Error('unexpected drum reference');
  groupInfo.set('drum_tower', {...src, cubes: src.cubes.filter(c => c.rot[0] === 0)});
  groupInfo.set('drum_body', {...src, cubes: src.cubes.filter(c => c.rot[0] !== 0)});
  if (groupInfo.get('drum_tower').cubes.length !== 14) throw new Error('feed tower split');
}

// ---------------- build rules: BR51 V2 magazine (tower) and stock (drum) ----------------
const MAT = {drum_tower: 'magazine', drum_body: 'stock'};
const EPS = {drum_tower: 0.07, drum_body: 0.07};
const CHAMFER = {magazine: 0.035, stock: 0.04};
const builder = createBuilder({MAT, EPS, CHAMFER, BONE: {drum_tower: BONE_ROOT, drum_body: BONE_ROOT}, defaultMat: 'stock'});
export const PARTS = builder.PARTS;
export const report = {tower: builder.slabGroup('drum_tower', groupInfo.get('drum_tower')), body: builder.slabGroup('drum_body', groupInfo.get('drum_body'))};

// ---------------- identification strips (muted tan), same size and build as the 35-round magazine ----------------
// On each side face of the drum housing, rear section (behind the centre band), mid-height, in the housing cube's own
// unrotated frame carried through its bind matrix (the 10 deg rake).
export const ID_STRIP = {z0: 0.65, z1: 1.50, yc: -5.41883, h: 0.16, proud: 0.02, sink: 0.01, chamfer: 0.012};
const ids = new Part('drum_id_marks', BONE_ROOT, 'idmark');
PARTS.push(ids);
{
  const housing = groupInfo.get('drum_body').cubes.find(c => c.from[0] === -2.5 && c.to[0] === 2.5);
  if (!housing) throw new Error('drum housing not found');
  const {z0, z1, yc, h, proud, sink, chamfer: c} = ID_STRIP;
  for (const sx of [1, -1]) {
    const xs = housing.to[0] * sx, P = (x, y, z) => ids.vtx(M4.pt(housing.M, [x, y, z]));
    const base = [[z0, yc - h / 2], [z1, yc - h / 2], [z1, yc + h / 2], [z0, yc + h / 2]].map(([z, y]) => P(xs - sx * sink, y, z));
    const top = [[z0 + c, yc - h / 2 + c], [z1 - c, yc - h / 2 + c], [z1 - c, yc + h / 2 - c], [z0 + c, yc + h / 2 - c]].map(([z, y]) => P(xs + sx * proud, y, z));
    const out = M4.dir(housing.M, [sx, 0, 0]);
    ids.face(top, out, 'side');
    for (let i = 0; i < 4; i++) {
      const j = (i + 1) % 4, q = [base[i], base[j], top[j], top[i]], mid = mul(q.reduce((a, k) => add(a, ids.v[k]), [0, 0, 0]), 1 / 4);
      const ctr = mul(top.reduce((a, k) => add(a, ids.v[k]), [0, 0, 0]), 1 / 4);
      ids.face(q, add(norm(sub(mid, ctr)), mul(out, 0.3)), 'bevel');
    }
  }
}

// ---------------- UV + Base Color / LabPBR (BR51 V2 density: 16 texels per unit) ----------------
const ATLAS = 512, PAD = 1;
const unwrapped = unwrap(PARTS, {atlas: ATLAS, pad: PAD, startS: 16, stepS: 0.5});
export const texelsPerUnit = unwrapped.S;
// BR51 V2 MATS.magazine / MATS.stock verbatim (verified), plus the AFL identification colour.
export const MATS = {
  magazine: {c: [39, 41, 43], hl: 20, sm: 82,  se: 132, f0: 24, edgeF0: 255},
  stock:    {c: [31, 32, 34], hl: 7, sm: 58, se: 70, f0: 10},
  idmark:   {c: [124, 106, 74], hl: 8, sm: 62, se: 62, f0: 10},
};
const SOURCE_GROUP = {drum_tower: 'drum_tower', drum_body: 'drum_body'};
const painted = paint({PARTS, islands: unwrapped.islands, S: unwrapped.S, uvOf: unwrapped.uvOf, atlas: ATLAS, pad: PAD, MATS, ZONED: new Set(['magazine']),
  groupInfo, sourceGroups: p => SOURCE_GROUP[p.name] ? [SOURCE_GROUP[p.name]] : [],
  refTexture: ref.textures[0], refUvWidth: ref.textures[0].uv_width || ref.resolution.width,
  background: {c: [40, 42, 44, 255], s: [100, 255, 0, 255], n: [128, 128, 255, 255]}});
export const PNG = painted.PNG, MAT_REF = painted.MAT_REF, zoneStats = painted.zoneStats;

// ---------------- source, geo, sidecar ----------------
const uuid = s => { const h = createHash('sha256').update('afl-br51-drum-magazine-50-v1:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r12 = v => +v.toFixed(12) || 0;
const elements = PARTS.map(p => {
  const uvs = unwrapped.faceUV.get(p), key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {};
  p.v.forEach((q, i) => { vertices[key(i)] = q.map(r12); });
  p.f.forEach((f, fi) => { const uv = uvs.get(f); faces['f' + key(fi)] = {uv: Object.fromEntries(f.ids.map((id, j) => [key(id), uv[j].map(r12)])), vertices: f.ids.map(key), texture: 0}; });
  return {name: p.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: true, locked: false,
    render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: uuid('mesh:' + p.name)};
});
const root = {name: BONE_ROOT, uuid: uuid('group:' + BONE_ROOT), export: true, locked: false, scope: 0, selected: false, visibility: true,
  _static: {properties: {}, temp_data: {}}, origin: [0, 0, 0], rotation: [0, 0, 0], color: 0, children: [], reset: false, shade: true, mirror_uv: false,
  autouv: 0, isOpen: true, primary_selected: false};
export const source = {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: ID, model_identifier: '', visible_box: [1, 1, 0],
  variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: ATLAS, height: ATLAS},
  elements, groups: [root], outliner: [{uuid: root.uuid, isOpen: true, children: elements.map(e => e.uuid)}],
  textures: [{name: ID + '.png', relative_path: `textures/${ID}.png`, folder: '', namespace: '', id: '0', group: '', scope: 0,
    width: ATLAS, height: ATLAS, uv_width: ATLAS, uv_height: ATLAS, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
    file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
    frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture'),
    source: 'data:image/png;base64,' + PNG[0].toString('base64')}],
  animations: []};
export const geo = {format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: 'geometry.' + ID, texture_width: ATLAS, texture_height: ATLAS,
  visible_bounds_width: 3, visible_bounds_height: 2, visible_bounds_offset: [0, -0.25, 0]}, bones: [{name: BONE_ROOT, pivot: [0, 0, 0]}]}]};
export const sidecar = convert(source, geo, {}, ID + '_mesh.bbmodel', 2);

const bb = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const OUT = {src: path.join(bb, ID + '_mesh.bbmodel'), geo: path.join(assets, `geo/${ID}.geo.json`), mesh: path.join(assets, `meshes/${ID}.aflmesh.json`),
  maps: ['', '_s', '_n'].map(k => [path.join(bb, `textures/${ID}${k}.png`), path.join(assets, `textures/item/${ID}${k}.png`)])};
const outputs = [[OUT.src, JSON.stringify(source)], [OUT.geo, JSON.stringify(geo, null, 2) + '\n'], [OUT.mesh, serializeCompact(sidecar)],
  ...OUT.maps.flatMap(([s, r], k) => [[s, PNG[k]], [r, PNG[k]]])];
const faces = sidecar.parts.flatMap(p => p.faces), all = PARTS.flatMap(p => p.v), ext = i => [Math.min(...all.map(q => q[i])), Math.max(...all.map(q => q[i]))].map(v => +v.toFixed(4));
export const stats = {parts: sidecar.parts.map(p => `${p.name}:${p.faces.reduce((s, f) => s + f.length - 2, 0)}`), triangleEquivalent: faces.reduce((s, f) => s + f.length - 2, 0),
  quads: faces.filter(f => f.length === 4).length, triangles: faces.filter(f => f.length === 3).length, texelsPerUnit: unwrapped.S, islands: unwrapped.islands.length,
  bounds: {x: ext(0), y: ext(1), z: ext(2)}, zoneStats, slab: report};
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  console.log(JSON.stringify(stats));
  if (process.argv.includes('--check')) {
    for (const [file, data] of outputs) { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; if (!cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data))) throw new Error('stale ' + path.relative(ROOT, file)); }
    console.log('CHECK OK');
  } else {
    for (const [file, data] of outputs) fs.writeFileSync(file, data);
    console.log('wrote ' + outputs.map(([f]) => path.relative(ROOT, f)).join(', '));
  }
}
