// BR51-01 35-Round Extended Magazine V2 (item br51_extended_magazine_35): rebuilds the V1 cube magazine (the BR51 artist's
// mag_extended_2: 20 body cubes + 4 floorplate cubes) as AFL hard-surface Pure Mesh with exactly the method, rules,
// materials and texel density of the BR51 V2 standard magazine (tools/build-br51-01-v2-mesh.mjs via the shared
// tools/cube-slab-mesh-lib.mjs), so the two magazines read as one family on the gun. One 512 atlas: Base Color + LabPBR
// _s / _n in one raster pass. Adds the AFL extended-magazine identification: a muted tan strip on each side of the
// floorplate (same colour language as the P9 extended magazine V2).
//   node tools/build-br51-extended-magazine-35-v2.mjs          -> writes the editable source, source maps and runtime files
//   node tools/build-br51-extended-magazine-35-v2.mjs --check  -> verifies every output is up to date
// Mount contract (unchanged from V1): model origin = the shared pivot of the BR51 magazine bones mag_standard /
// reload_mag_standard / empty_old_mag_standard ([0, 9.41883, 4.52607] in the gun); NativeMagazineRendering draws this
// model at the replaced bone's pivot and skips that bone's own subtree. The dynamic top-round bones (7 / 3,
// reload_bullet_7 / reload_bullet_3) are siblings of the magazine bones, so they stay live with this magazine fitted.
// Reference: the V1 cube source at git REF (geometry = design reference, its atlas = tone zoning reference). Silhouette,
// 5 deg body rake, side ribs, feed lips and floorplate loop are inherited from the cube footprints by construction.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {convert, serializeCompact} from './export-afl-mesh.mjs';
import {M4, add, sub, mul, norm, Part, collectGroups, createBuilder, unwrap, paint} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export const REF = '099137f';   // V1 cube magazine (+br51 extend mag)
export const ref = JSON.parse(execFileSync('git', ['show', `${REF}:src/main/blockbench/br51_extended_magazine_35.bbmodel`], {cwd: ROOT, encoding: 'utf8', maxBuffer: 1 << 26}));
const BONE_ROOT = 'br51_extended_magazine_35_root';
export const groupInfo = collectGroups(ref);

// ---------------- build rules: the BR51 V2 standard magazine's (mag_standard / hu2) ----------------
const MAT = {[BONE_ROOT]: 'magazine', baseplate: 'floorplate'};
const EPS = {[BONE_ROOT]: 0.07, baseplate: 0.05};
const CHAMFER = {magazine: 0.035, floorplate: 0.04};
const builder = createBuilder({MAT, EPS, CHAMFER, BONE: {baseplate: BONE_ROOT}, defaultMat: 'magazine'});
export const PARTS = builder.PARTS;
export const report = {body: builder.slabGroup(BONE_ROOT, groupInfo.get(BONE_ROOT)), floorplate: builder.slabGroup('baseplate', groupInfo.get('baseplate'))};
const NAMES = {[BONE_ROOT]: 'mag_body', baseplate: 'mag_floorplate'}, SOURCE_GROUP = {mag_body: BONE_ROOT, mag_floorplate: 'baseplate'};
for (const p of PARTS) p.name = NAMES[p.name];

// ---------------- identification strips (muted tan) ----------------
// On each side face of the floorplate block, rear section (behind the pull loop), authored in that cube's own unrotated
// frame and carried through its bind matrix (the 5 deg rake). A low frustum: base sunk 0.01 into the plate, face 0.02
// proud, 0.012 chamfer all round (bevel tone), mid-height of the plate.
export const ID_STRIP = {z0: 0.17, z1: 1.02, yc: -7.04383, h: 0.16, proud: 0.02, sink: 0.01, chamfer: 0.012};
const ids = new Part('mag_id_marks', BONE_ROOT, 'idmark');
PARTS.push(ids);
{
  const plate = groupInfo.get('baseplate').cubes.find(c => c.from[0] === -1 && c.to[0] === 1 && c.to[1] - c.from[1] > 1.5);
  if (!plate) throw new Error('floorplate block not found');
  const {z0, z1, yc, h, proud, sink, chamfer: c} = ID_STRIP;
  for (const sx of [1, -1]) {
    const xs = plate.to[0] * sx, P = (x, y, z) => ids.vtx(M4.pt(plate.M, [x, y, z]));
    const base = [[z0, yc - h / 2], [z1, yc - h / 2], [z1, yc + h / 2], [z0, yc + h / 2]].map(([z, y]) => P(xs - sx * sink, y, z));
    const top = [[z0 + c, yc - h / 2 + c], [z1 - c, yc - h / 2 + c], [z1 - c, yc + h / 2 - c], [z0 + c, yc + h / 2 - c]].map(([z, y]) => P(xs + sx * proud, y, z));
    const out = M4.dir(plate.M, [sx, 0, 0]);
    ids.face(top, out, 'side');
    for (let i = 0; i < 4; i++) {
      const j = (i + 1) % 4, q = [base[i], base[j], top[j], top[i]], mid = mul(q.reduce((a, k) => add(a, ids.v[k]), [0, 0, 0]), 1 / 4);
      const ctr = mul(top.reduce((a, k) => add(a, ids.v[k]), [0, 0, 0]), 1 / 4);
      ids.face(q, add(norm(sub(mid, ctr)), mul(out, 0.3)), 'bevel');
    }
  }
}

// ---------------- UV + Base Color / LabPBR ----------------
// Texel density = the BR51 V2 atlas (16 texels per unit), so this magazine and the gun's own magazine share rim widths
// and panel scale (a 256 atlas would force 10).
const ATLAS = 512, PAD = 1;
const unwrapped = unwrap(PARTS, {atlas: ATLAS, pad: PAD, startS: 16, stepS: 0.5});
export const texelsPerUnit = unwrapped.S;
// BR51 V2 MATS.magazine / MATS.floorplate verbatim (tools/verify-br51-extended-magazine-35.mjs asserts it), plus the
// P9 extended magazine V2 identification colour (muted tan 124,106,74, bevel 132,114,80, smoothness 62, dielectric).
export const MATS = {
  magazine:   {c: [39, 41, 43], hl: 20, sm: 82,  se: 132, f0: 24, edgeF0: 255},
  floorplate: {c: [25, 26, 27], hl: 5, sm: 62, se: 72, f0: 10},
  idmark:     {c: [124, 106, 74], hl: 8, sm: 62, se: 62, f0: 10},
};
const painted = paint({PARTS, islands: unwrapped.islands, S: unwrapped.S, uvOf: unwrapped.uvOf, atlas: ATLAS, pad: PAD, MATS, ZONED: new Set(['magazine']),
  groupInfo, sourceGroups: p => SOURCE_GROUP[p.name] ? [SOURCE_GROUP[p.name]] : [],
  refTexture: ref.textures[0], refUvWidth: ref.textures[0].uv_width || ref.resolution.width,
  background: {c: [40, 42, 44, 255], s: [100, 255, 0, 255], n: [128, 128, 255, 255]}});
export const PNG = painted.PNG, MAT_REF = painted.MAT_REF, zoneStats = painted.zoneStats;

// ---------------- source, geo, sidecar ----------------
const uuid = s => { const h = createHash('sha256').update('afl-br51-extended-magazine-35-v2:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r12 = v => +v.toFixed(12) || 0;   // full bake precision: V2 keeps a planar quad only when the source proves it
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
export const source = {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: 'br51_extended_magazine_35', model_identifier: '', visible_box: [1, 1, 0],
  variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: ATLAS, height: ATLAS},
  elements, groups: [root], outliner: [{uuid: root.uuid, isOpen: true, children: elements.map(e => e.uuid)}],
  textures: [{name: 'br51_extended_magazine_35.png', relative_path: 'textures/br51_extended_magazine_35.png', folder: '', namespace: '', id: '0', group: '', scope: 0,
    width: ATLAS, height: ATLAS, uv_width: ATLAS, uv_height: ATLAS, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
    file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
    frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture'),
    source: 'data:image/png;base64,' + PNG[0].toString('base64')}],
  animations: []};
export const geo = {format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: 'geometry.br51_extended_magazine_35', texture_width: ATLAS, texture_height: ATLAS,
  visible_bounds_width: 2, visible_bounds_height: 2, visible_bounds_offset: [0, -0.25, 0]}, bones: [{name: BONE_ROOT, pivot: [0, 0, 0]}]}]};
export const sidecar = convert(source, geo, {}, 'br51_extended_magazine_35_mesh.bbmodel', 2);

const bb = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const OUT = {src: path.join(bb, 'br51_extended_magazine_35_mesh.bbmodel'), geo: path.join(assets, 'geo/br51_extended_magazine_35.geo.json'),
  mesh: path.join(assets, 'meshes/br51_extended_magazine_35.aflmesh.json'),
  maps: ['', '_s', '_n'].map(k => [path.join(bb, `textures/br51_extended_magazine_35${k}.png`), path.join(assets, `textures/item/br51_extended_magazine_35${k}.png`)])};
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
