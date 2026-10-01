// Retail Shelf V3: the V2 white plastic supermarket shelf rebuilt as a bevelled mesh with a 512 LabPBR atlas, exported as
// a Forge OBJ block model (static prop: chunk-baked, no per-frame cost; the shelf's BlockEntity renderer only draws the
// displayed items).
//   node tools/build-retail-shelf-v3.mjs            -> writes the mesh source, OBJ / MTL / block model and the atlas maps
//   node tools/build-retail-shelf-v3.mjs --check    -> verifies every output is up to date
// Input: the V2 cube source src/main/blockbench/afl_supermarket_shelf_v2_review.bbmodel (171 cubes, 128 px swatch atlas).
// Every cube takes the material of the swatch its faces use (the V2 atlas is eight flat tone stripes), the cubes of one
// group and swatch are merged into bevelled slabs (tools/cube-slab-mesh-lib.mjs, as for the native guns), coplanar
// same-facing overlaps are resolved by priority inflation. Frame: the V2 source units (px, X / Z centred, Y 0..32,
// front toward -Z); the OBJ is in block units of the lower half (x = px / 16 + 0.5, y = px / 16, z = px / 16 + 0.5).
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {collectGroups, createBuilder, unwrap, paint, png, zFightLevels, norm, newell, cross, dot, sub} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SOURCE = path.join(ROOT, 'src/main/blockbench/afl_supermarket_shelf_v2_review.bbmodel');
function assert(c, m) { if (!c) throw new Error(m); }
const ref = JSON.parse(fs.readFileSync(SOURCE, 'utf8'));
const groupInfo = collectGroups(ref);

// ---------------- swatch -> material ----------------
// the V2 atlas: eight 16 px stripes; a cube's swatch = the stripe most of its textured faces use
const SWATCH = ['plastic', 'trim', 'mid', 'deck', 'label', 'bluegrey', 'dark', 'plinth'];
const uvWidth = ref.textures[0].uv_width || ref.resolution.width;
function swatchOf(e) {
  const votes = new Map();
  for (const f of Object.values(e.faces || {})) {
    if (f.texture === null || f.texture === undefined) continue;
    const s = Math.min(7, Math.floor(((f.uv[0] + f.uv[2]) / 2) * 128 / uvWidth / 16));
    votes.set(s, (votes.get(s) || 0) + 1);
  }
  return [...votes].sort((a, b) => b[1] - a[1])[0][0];
}
// virtual groups: one per (source group, swatch)
const VIRTUAL = [];
for (const [name, info] of groupInfo) {
  if (info.hidden || !info.cubes.length) continue;
  const bySwatch = new Map();
  for (const c of info.cubes) {
    if (/^price_rail_glint_\d+$/.test(c.e.name)) continue;   // 0.07 px fake highlight lines: the bevelled rail edge catches the light now
    const s = swatchOf(c.e); (bySwatch.get(s) || bySwatch.set(s, []).get(s)).push(c);
  }
  for (const [s, cubes] of [...bySwatch].sort((a, b) => a[0] - b[0])) VIRTUAL.push({name: `${name}__${SWATCH[s]}`, mat: SWATCH[s], info: {...info, cubes}});
}
export const MATS = {   // all moulded / painted plastic: dielectric (LabPBR F0 20), satin; Base Color = the V2 swatch tone
  plastic:  {c: [210, 208, 199], hl: 8, sm: 118, se: 132, f0: 20},
  trim:     {c: [224, 222, 213], hl: 8, sm: 122, se: 136, f0: 20},
  mid:      {c: [188, 189, 182], hl: 8, sm: 112, se: 126, f0: 20},
  deck:     {c: [165, 170, 167], hl: 8, sm: 108, se: 124, f0: 20},
  label:    {c: [199, 202, 197], hl: 6, sm: 104, se: 116, f0: 20},
  bluegrey: {c: [150, 159, 159], hl: 8, sm: 104, se: 118, f0: 20},
  dark:     {c: [98, 108, 108], hl: 8, sm: 100, se: 116, f0: 20},
  plinth:   {c: [65, 72, 71], hl: 6, sm: 92, se: 104, f0: 20},
};
const CHAMFER = Object.fromEntries(Object.keys(MATS).map(m => [m, 0.12]));   // soft moulded edges at block scale

// ---------------- build with z-fighting resolution ----------------
export const STEP = 0.03;   // px: about 1/500 block, far above depth-buffer resolution at shop distances
function assemble(GROW) {
  const MAT = Object.fromEntries(VIRTUAL.map(v => [v.name, v.mat])), BONE = Object.fromEntries(VIRTUAL.map(v => [v.name, 'shelf']));
  const builder = createBuilder({MAT, BONE, CHAMFER, SNAP: true, GROW, defaultMat: 'plastic'});
  for (const v of VIRTUAL) builder.slabGroup(v.name, v.info);
  return builder;
}
const zHistory = [], floor = new Map();
let GROW_MAP = null, builder, zf = null;
for (let it = 0; ; it++) {
  builder = assemble(GROW_MAP);
  const prior = zf?.beats, last = zf?.levels;
  if (prior) for (const p of zFightLevels(builder.PARTS, builder.SLABS).pairs)
    if (prior.get(p.win)?.has(p.lose)) floor.set(p.win, (last.get(p.win) || 0) + 1);
  zf = zFightLevels(builder.PARTS, builder.SLABS, {prior, floor});
  zHistory.push({pairs: zf.pairs.length, unresolved: zf.unresolved.length});
  if (!zf.pairs.length && !zf.unresolved.length) break;
  assert(it < 8 && !zf.unresolved.length, 'z-fighting did not converge: ' + JSON.stringify(zHistory));
  GROW_MAP = new Map([...zf.levels].map(([k, l]) => [k, l * STEP]));
}
const PARTS = builder.PARTS.filter(p => p.f.length);
// the OBJ keeps faces as they are (Forge draws a quad as 0-1-2 / 2-3-0): only triangles and strictly convex quads
for (const p of PARTS) for (const f of p.f) {
  assert(f.ids.length === 3 || f.ids.length === 4, 'n-gon in ' + p.name);
  if (f.ids.length === 4) { const P = f.ids.map(i => p.v[i]), n = newell(P);
    for (let k = 0; k < 4; k++) assert(dot(cross(sub(P[(k + 1) % 4], P[k]), sub(P[(k + 2) % 4], P[(k + 1) % 4])), n) > 0, 'non-convex quad in ' + p.name); }
}

// ---------------- UV + paint ----------------
const ATLAS = 512, PAD = 2;
const UV = unwrap(PARTS, {atlas: ATLAS, pad: PAD, startS: 24, stepS: 0.25});
for (const p of PARTS) assert(p.f.every(f => UV.faceUV.get(p)?.has(f)), 'unmapped face in ' + p.name);
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
const painted = paint({PARTS, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas: ATLAS, pad: PAD, MATS, ZONED: new Set(), groupInfo: new Map(),
  sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [...MATS.plastic.c, 255], s: [118, 20, 0, 255], n: [128, 128, 255, 255]}});

// ---------------- OBJ / MTL / block model ----------------
const ID = 'retail_shelf_single';
const f6 = v => (+v.toFixed(6)).toString();
const obj = (() => {
  const out = [`# AFL Retail Shelf V3, generated by tools/build-retail-shelf-v3.mjs`, `mtllib ${ID}.mtl`];
  let vBase = 1, tBase = 1, nBase = 1;
  for (const p of PARTS) {
    out.push(`o ${p.name}`, `usemtl shelf`);
    for (const q of p.v) out.push(`v ${f6(q[0] / 16 + 0.5)} ${f6(q[1] / 16)} ${f6(q[2] / 16 + 0.5)}`);
    const uvs = UV.faceUV.get(p), vt = [], vn = [], fl = [];
    for (const f of p.f) {
      const n = norm(newell(f.ids.map(i => p.v[i]))), uv = uvs.get(f), ni = nBase + vn.length;
      vn.push(`vn ${f6(n[0])} ${f6(n[1])} ${f6(n[2])}`);
      const corner = f.ids.map((id, j) => { vt.push(`vt ${f6(uv[j][0] / ATLAS)} ${f6(1 - uv[j][1] / ATLAS)}`); return `${vBase + id}/${tBase + vt.length - 1}/${ni}`; });
      fl.push('f ' + corner.join(' '));
    }
    out.push(...vt, ...vn, ...fl);
    vBase += p.v.length; tBase += vt.length; nBase += vn.length;
  }
  return out.join('\n') + '\n';
})();
const mtl = `# AFL Retail Shelf V3\nnewmtl shelf\nKd 1 1 1\nmap_Kd apocalypse_firstlight:block/${ID}\n`;
// display: the V2 cube model's transforms, unchanged (the item model overrides most of them; on_shelf is a custom context)
const S25 = [0.25, 0.25, 0.25], S50 = [0.5, 0.5, 0.5];
const blockModel = {loader: 'forge:obj', model: `apocalypse_firstlight:models/block/${ID}.obj`, automatic_culling: false, flip_v: true,
  shade_quads: true, ambientocclusion: false, textures: {particle: `apocalypse_firstlight:block/${ID}`},
  display: {thirdperson_righthand: {translation: [0, 2, 0], scale: S25}, thirdperson_lefthand: {translation: [0, 2, 0], scale: S25},
    firstperson_righthand: {scale: S25}, firstperson_lefthand: {scale: S25}, ground: {translation: [0, 2.75, 0], scale: S50},
    gui: {rotation: [30, 128, 0], translation: [0, -2, 0], scale: [0.35, 0.35, 0.35]}, head: {scale: S25},
    fixed: {translation: [0, -3, -3.25], scale: S50}, on_shelf: {rotation: [0, -180, 0], scale: S50}}};

// ---------------- editable mesh source (Free Model) ----------------
const uuid = s => { const h = createHash('sha256').update('afl-retail-shelf-v3:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r12 = v => +v.toFixed(12) || 0;
const source = (() => {
  const elements = PARTS.map(p => {
    const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {}, uvs = UV.faceUV.get(p);
    p.v.forEach((q, i) => { vertices[key(i)] = q.map(r12); });
    p.f.forEach((f, fi) => { const uv = uvs.get(f); faces['f' + key(fi)] = {uv: Object.fromEntries(f.ids.map((id, j) => [key(id), uv[j].map(r12)])), vertices: f.ids.map(key), texture: 0}; });
    return {name: p.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: true, locked: false,
      render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: uuid('mesh:' + p.name)};
  });
  return {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: ID, model_identifier: '', visible_box: [1, 2, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: ATLAS, height: ATLAS},
    elements, groups: [{name: 'shelf', uuid: uuid('group'), export: true, locked: false, scope: 0, selected: false, visibility: true,
      _static: {properties: {}, temp_data: {}}, origin: [0, 0, 0], rotation: [0, 0, 0], color: 0, children: [], reset: false, shade: true,
      mirror_uv: false, autouv: 0, isOpen: true, primary_selected: false}],
    outliner: [{uuid: uuid('group'), isOpen: true, children: elements.map(e => e.uuid)}],
    textures: [{name: ID + '.png', relative_path: `textures/${ID}.png`, folder: '', namespace: '', id: '0', group: '', scope: 0,
      width: ATLAS, height: ATLAS, uv_width: ATLAS, uv_height: ATLAS, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
      file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
      frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture'),
      source: 'data:image/png;base64,' + painted.PNG[0].toString('base64')}],
    animations: []};
})();

const bb = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const outputs = [[path.join(bb, ID + '.bbmodel'), JSON.stringify(source)],
  [path.join(assets, `models/block/${ID}.obj`), obj], [path.join(assets, `models/block/${ID}.mtl`), mtl],
  [path.join(assets, `models/block/${ID}.json`), JSON.stringify(blockModel, null, 2) + '\n'],
  ...['', '_s', '_n'].flatMap((k, i) => [[path.join(bb, `textures/${ID}${k}.png`), painted.PNG[i]], [path.join(assets, `textures/block/${ID}${k}.png`), painted.PNG[i]]])];

const all = PARTS.flatMap(p => p.v);
export const stats = {triangles: PARTS.reduce((s, p) => s + p.f.reduce((t, f) => t + f.ids.length - 2, 0), 0), parts: PARTS.length, virtualGroups: VIRTUAL.length,
  texelsPerPx: UV.S, islands: UV.islands.length, zFight: zHistory, grown: GROW_MAP ? GROW_MAP.size : 0,
  bounds: [0, 1, 2].map(k => [+Math.min(...all.map(q => q[k])).toFixed(3), +Math.max(...all.map(q => q[k])).toFixed(3)])};
const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  console.log(JSON.stringify(stats));
  if (process.argv.includes('--check')) {
    for (const [file, data] of outputs) { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; if (!cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data))) throw new Error('stale ' + path.relative(ROOT, file)); }
    console.log('CHECK OK');
  } else if (process.argv.includes('--preview')) {   // --preview DIR: OBJ + maps only, for offline renders
    const dir = process.argv[process.argv.indexOf('--preview') + 1]; fs.mkdirSync(dir, {recursive: true});
    fs.writeFileSync(path.join(dir, ID + '.obj'), obj); ['', '_s', '_n'].forEach((k, i) => fs.writeFileSync(path.join(dir, ID + k + '.png'), painted.PNG[i]));
    console.log('preview written to ' + dir);
  } else if (process.argv.includes('--dry')) {
    console.log('dry run, nothing written');
  } else {
    for (const [file] of outputs) fs.mkdirSync(path.dirname(file), {recursive: true});
    for (const [file, data] of outputs) fs.writeFileSync(file, data);
    console.log('wrote ' + outputs.map(([f]) => path.relative(ROOT, f)).join(', '));
  }
}
