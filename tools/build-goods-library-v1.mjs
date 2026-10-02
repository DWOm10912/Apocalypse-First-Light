// Goods library V1: the anonymous retail products drawn in shelves, the beverage cooler and the vending machine
// (docs/gameplay/container_goods_v1.md), as one Pure Mesh + 512 LabPBR atlas, drawn per cell by those containers'
// renderers (client/goods/AflGoodsLibrary) instead of real items. Every product is modelled once, in a nominal cell
// 4 wide x 4.6 tall x 8 deep px (x across, y up from the deck, z depth, -z = the front, origin at the cell's bottom
// centre); a container scales it to its own cells. Each product has two bones: '<product>' (fixed colours: caps, lids,
// metal, white paper) and '<product>_tint' (a light neutral, coloured per cell by vertex colour). Low poly on purpose
// (renderers resubmit them every frame): 36..136 triangles a cell. No print or labels.
//   node tools/build-goods-library-v1.mjs                -> writes source, runtime geo / sidecar / maps
//   node tools/build-goods-library-v1.mjs --check        -> verifies every output is up to date
//   node tools/build-goods-library-v1.mjs --preview DIR  -> writes only geo / sidecar / maps into DIR (offline review)
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {convert, serializeCompact} from './export-afl-mesh.mjs';
import {Part, extrude, area2, unwrap, paint, png, zFightLevels} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function assert(c, m) { if (!c) throw new Error(m); }
const PARTS = [], P = (name, bone, mat) => { const p = new Part(name, bone, mat); PARTS.push(p); return p; };
const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
const ngon = (cu, cv, r, seg) => Array.from({length: seg}, (_, i) => { const a = Math.PI / seg + 2 * Math.PI * i / seg; return [cu + r * Math.cos(a), cv + r * Math.sin(a)]; });

// ---------------- primitives (no chamfers: products are small and redrawn every frame) ----------------
const box = (p, x0, y0, z0, x1, y1, z1, c = 0) => extrude(p, 'y', {outer: orient([[z0, x0], [z1, x0], [z1, x1], [z0, x1]], true), holes: []}, y0, y1, c);
const prism = (p, x, z, r, y0, y1, seg = 8) => extrude(p, 'y', {outer: orient(ngon(z, x, r, seg), true), holes: []}, y0, y1, 0);
// a flat polygon facing up (a can's top), one fan
function disc(p, x, z, r, y, seg = 8) {
  const c = p.vtx([x, y, z]), ring = ngon(z, x, r, seg).map(([zz, xx]) => p.vtx([xx, y, zz]));
  for (let i = 0; i < seg; i++) p.face([c, ring[i], ring[(i + 1) % seg]], [0, 1, 0], 'cap');
}
// gable top of a carton: a pentagon prism along x
const gable = (p, x0, x1, z0, z1, y0, rise) => extrude(p, 'x', {outer: orient([[z0, y0], [z1, y0], [z1, y0 + rise * 0.35], [(z0 + z1) / 2, y0 + rise], [z0, y0 + rise * 0.35]], true), holes: []}, x0, x1, 0);

export const CELL = {w: 4, h: 4.6, d: 8};
const PRODUCTS = [];
const product = (name, build) => { PRODUCTS.push(name); build(name, name + '_tint'); };

// grocery
product('boxes', (fixed, tint) => {   // cereal-style boxes, three deep
  const t = P('boxes_box', tint, 'tint_paper');
  [[-3.95, 4.2, 0.05], [-2.6, 4.0, -0.08], [-1.25, 4.3, 0.06]].forEach(([z, h, dx]) => box(t, -1.7 + dx, 0, z, 1.7 + dx, h, z + 1.25));
});
product('cans', (fixed, tint) => {    // 2 x 2 cans with metal tops
  const t = P('cans_body', tint, 'tint_metal'), m = P('cans_top', fixed, 'metal_top');
  for (const x of [-0.95, 0.95]) for (const z of [-3.0, -1.1]) { prism(t, x, z, 0.88, 0, 2.4); disc(m, x, z, 0.78, 2.42); }
});
product('bottles', (fixed, tint) => { // two plastic bottles: body, neck, cap
  const t = P('bottles_body', tint, 'tint_plastic'), c = P('bottles_cap', fixed, 'cap_white');
  for (const x of [-0.95, 0.95]) { prism(t, x, -2.9, 0.82, 0, 2.6); prism(t, x, -2.9, 0.42, 2.6, 3.4, 6); prism(c, x, -2.9, 0.46, 3.42, 3.8, 6); }
});
product('jars', (fixed, tint) => {    // two jars with metal lids
  const t = P('jars_body', tint, 'tint_plastic'), m = P('jars_lid', fixed, 'metal_top');
  for (const x of [-0.98, 0.98]) { prism(t, x, -2.9, 0.92, 0, 1.9); prism(m, x, -2.9, 0.97, 1.92, 2.28); }
});
product('bags', (fixed, tint) => {    // two snack bags standing, pillowed
  const t = P('bags_bag', tint, 'tint_film');
  box(t, -1.7, 0, -3.95, 1.7, 3.7, -3.0, 0.3);
  box(t, -1.6, 0, -2.85, 1.6, 3.4, -1.95, 0.3);
});
// pharmacy
product('pill_boxes', (fixed, tint) => {   // small boxes two wide, three high, coloured and white
  const t = P('pill_boxes_box', tint, 'tint_paper'), w = P('pill_boxes_white', fixed, 'white_paper');
  for (const [i, y] of [0, 1.24, 2.48].entries()) for (const x of [-0.98, 0.98]) box(i === 1 ? w : t, x - 0.9, y, -3.9, x + 0.9, y + 1.2, -1.5);
});
product('med_bottles', (fixed, tint) => {  // three small white bottles with coloured caps
  const w = P('med_bottles_body', fixed, 'bottle_white'), t = P('med_bottles_cap', tint, 'tint_plastic');
  for (const x of [-1.27, 0, 1.27]) { prism(w, x, -3.1, 0.56, 0, 1.6, 6); prism(t, x, -3.1, 0.6, 1.62, 2.07, 6); }
});
product('tubes', (fixed, tint) => {   // toothpaste-style boxes lying, three high, two deep
  const t = P('tubes_box', tint, 'tint_paper');
  for (const z of [-3.95, -2.7]) for (const y of [0, 0.92, 1.84]) box(t, -1.8, y, z, 1.8, y + 0.9, z + 1.15);
});
// hardware
product('paint_cans', (fixed, tint) => {   // two paint cans: metal, a coloured label band, lid
  const m = P('paint_cans_can', fixed, 'metal_can'), t = P('paint_cans_label', tint, 'tint_paper'), l = P('paint_cans_lid', fixed, 'metal_top');
  for (const x of [-0.97, 0.97]) { prism(m, x, -2.9, 0.95, 0, 2.0); prism(t, x, -2.9, 0.98, 0.35, 1.65); disc(l, x, -2.9, 0.86, 2.02); }
});
product('spray_cans', (fixed, tint) => {   // three spray cans with white caps
  const t = P('spray_cans_body', tint, 'tint_metal'), c = P('spray_cans_cap', fixed, 'cap_white');
  for (const x of [-1.25, 0, 1.25]) { prism(t, x, -3.1, 0.56, 0, 2.6, 6); prism(c, x, -3.1, 0.5, 2.62, 3.2, 6); }
});
product('part_boxes', (fixed) => {    // plain kraft parts boxes, two wide, two high
  const k = P('part_boxes_box', fixed, 'kraft');
  for (const y of [0, 1.42]) for (const x of [-0.98, 0.98]) box(k, x - 0.9, y, -3.95, x + 0.9, y + 1.4, -1.35);
});
// drinks
product('cartons', (fixed, tint) => { // gable-top cartons, two wide, two deep, white tops
  const t = P('cartons_body', tint, 'tint_paper'), w = P('cartons_top', fixed, 'white_paper');
  for (const x of [-0.97, 0.97]) for (const z of [-3.9, -1.95]) { box(t, x - 0.86, 0, z, x + 0.86, 2.7, z + 1.72); gable(w, x - 0.86, x + 0.86, z, z + 1.72, 2.7, 0.7); }
});

export const MATS = {   // tint_*: light neutral, coloured by the renderer; the rest fixed. All dielectric (F0 20).
  tint_paper:   {c: [214, 214, 210], hl: 4, sm: 72, se: 84, f0: 20},
  tint_plastic: {c: [214, 214, 210], hl: 6, sm: 130, se: 140, f0: 20},
  tint_film:    {c: [214, 214, 210], hl: 6, sm: 150, se: 156, f0: 20},
  tint_metal:   {c: [214, 214, 210], hl: 8, sm: 150, se: 160, f0: 20},
  metal_top:    {c: [168, 170, 172], hl: 10, sm: 160, se: 170, f0: 20},
  metal_can:    {c: [150, 152, 156], hl: 10, sm: 150, se: 160, f0: 20},
  cap_white:    {c: [186, 186, 182], hl: 6, sm: 120, se: 130, f0: 20},
  bottle_white: {c: [184, 184, 180], hl: 6, sm: 110, se: 120, f0: 20},
  white_paper:  {c: [184, 184, 178], hl: 4, sm: 70, se: 80, f0: 20},
  kraft:        {c: [146, 114, 78], hl: 6, sm: 64, se: 78, f0: 20},
};

const ATLAS = 512, PAD = 2;
const UV = unwrap(PARTS, {atlas: ATLAS, pad: PAD, startS: 16, stepS: 0.25});
for (const p of PARTS) assert(p.f.every(f => UV.faceUV.get(p)?.has(f)), 'unmapped face in ' + p.name);
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
const painted = paint({PARTS, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas: ATLAS, pad: PAD, MATS, ZONED: new Set(), groupInfo: new Map(),
  sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [214, 214, 210, 255], s: [72, 20, 0, 255], n: [128, 128, 255, 255]}});
const PNGS = painted.PNG;

// ---------------- source (Free Model) ----------------
const ID = 'goods_library';
const uuid = s => { const h = createHash('sha256').update('afl-goods-library-v1:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r12 = v => +v.toFixed(12) || 0;
const RIG = [...new Set(PARTS.map(p => p.bone))];
const source = (() => {
  const groups = RIG.map(name => ({name, uuid: uuid('group:' + name), export: true, locked: false, scope: 0, selected: false, visibility: true,
    _static: {properties: {}, temp_data: {}}, origin: [0, 0, 0], rotation: [0, 0, 0], color: 0, children: [], reset: false, shade: true,
    mirror_uv: false, autouv: 0, isOpen: true, primary_selected: false}));
  const nodes = new Map(RIG.map(name => [name, {uuid: uuid('group:' + name), isOpen: true, children: []}]));
  const elements = [];
  for (const p of PARTS) {
    const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {}, uvs = UV.faceUV.get(p);
    p.v.forEach((q, i) => { vertices[key(i)] = q.map(r12); });
    p.f.forEach((f, fi) => { const uv = uvs.get(f); faces['f' + key(fi)] = {uv: Object.fromEntries(f.ids.map((id, j) => [key(id), uv[j].map(r12)])), vertices: f.ids.map(key), texture: 0}; });
    const id = uuid('mesh:' + p.name);
    elements.push({name: p.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: true, locked: false,
      render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: id});
    nodes.get(p.bone).children.push(id);
  }
  return {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: ID, model_identifier: '', visible_box: [1, 1, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: ATLAS, height: ATLAS},
    elements, groups, outliner: RIG.map(name => nodes.get(name)),
    textures: [{name: ID + '.png', relative_path: `textures/${ID}.png`, folder: '', namespace: '', id: '0', group: '', scope: 0,
      width: ATLAS, height: ATLAS, uv_width: ATLAS, uv_height: ATLAS, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
      file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
      frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture'),
      source: 'data:image/png;base64,' + PNGS[0].toString('base64')}],
    animations: []};
})();

// ---------------- runtime: geo (bones only) + sidecar; no profile, the containers' renderers draw bones directly ----------------
const geo = {format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: 'geometry.' + ID, texture_width: ATLAS, texture_height: ATLAS,
  visible_bounds_width: 1, visible_bounds_height: 1, visible_bounds_offset: [0, 0.5, 0]}, bones: RIG.map(name => ({name, pivot: [0, 0, 0]}))}]};
const sidecar = convert(source, geo, {}, ID + '.bbmodel', 2);
const meshText = serializeCompact(sidecar);

const bb = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const outputs = [[path.join(bb, ID + '.bbmodel'), JSON.stringify(source)], [path.join(assets, `geo/${ID}.geo.json`), JSON.stringify(geo, null, 2) + '\n'],
  [path.join(assets, `meshes/${ID}.aflmesh.json`), meshText],
  ...['', '_s', '_n'].flatMap((k, i) => [[path.join(bb, `textures/${ID}${k}.png`), PNGS[i]], [path.join(assets, `textures/block/${ID}${k}.png`), PNGS[i]]])];

// every product alone (a cell holds one): coplanar check per product, extent check against the nominal cell
const trisOf = p => p.f.reduce((t, f) => t + f.ids.length - 2, 0);
const unresolved = PRODUCTS.flatMap(n => zFightLevels(PARTS, new Map(), {skip: p => p.bone !== n && p.bone !== n + '_tint'}).unresolved);
const extents = Object.fromEntries(PRODUCTS.map(n => { const v = PARTS.filter(p => p.bone === n || p.bone === n + '_tint').flatMap(p => p.v);
  return [n, [0, 1, 2].map(k => [Math.min(...v.map(q => q[k])), Math.max(...v.map(q => q[k]))].map(x => +x.toFixed(2)))]; }));
for (const [n, [[x0, x1], [y0, y1], [z0, z1]]] of Object.entries(extents))
  assert(x0 >= -CELL.w / 2 - 1e-6 && x1 <= CELL.w / 2 + 1e-6 && y0 >= -1e-6 && y1 <= CELL.h + 1e-6 && z0 >= -CELL.d / 2 - 1e-6 && z1 <= CELL.d / 2 + 1e-6, `${n} leaves the cell: ${JSON.stringify(extents[n])}`);
export const stats = {triangles: sidecar.parts.flatMap(p => p.faces).reduce((s, q) => s + q.length - 2, 0), parts: sidecar.parts.length, texelsPerPx: UV.S,
  products: Object.fromEntries(PRODUCTS.map(n => [n, PARTS.filter(p => p.bone === n || p.bone === n + '_tint').reduce((s, p) => s + trisOf(p), 0)])),
  bones: RIG.length, coplanarOverlaps: unresolved.length};
const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  console.log(JSON.stringify(stats));
  if (unresolved.length) console.log('COPLANAR', JSON.stringify(unresolved.slice(0, 12)));
  const pi = process.argv.indexOf('--preview');
  if (pi > 0) {
    const dir = process.argv[pi + 1]; fs.mkdirSync(dir, {recursive: true});
    fs.writeFileSync(path.join(dir, `${ID}.geo.json`), JSON.stringify(geo, null, 2) + '\n');
    fs.writeFileSync(path.join(dir, `${ID}.aflmesh.json`), meshText);
    ['', '_s', '_n'].forEach((k, i) => fs.writeFileSync(path.join(dir, `${ID}${k}.png`), PNGS[i]));
    console.log('preview written to ' + dir);
  } else if (process.argv.includes('--check')) {
    for (const [file, data] of outputs) { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; if (!cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data))) throw new Error('stale ' + path.relative(ROOT, file)); }
    console.log('CHECK OK');
  } else {
    for (const [file] of outputs) fs.mkdirSync(path.dirname(file), {recursive: true});
    for (const [file, data] of outputs) fs.writeFileSync(file, data);
    console.log('wrote ' + outputs.map(([f]) => path.relative(ROOT, f)).join(', '));
  }
}
