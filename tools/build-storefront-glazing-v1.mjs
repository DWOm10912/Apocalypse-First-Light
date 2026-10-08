// Storefront Glazing V1 (docs/models/storefront_glazing_v1.md): a North American centre-set aluminium storefront, one block
// of it per wall cell: a clear insulated glass lite in a black anodized frame (2" x 4 1/2" members, here 1 x 2 px), set
// 3 px back from the wall's outer face. The frame is assembled per block from pieces (blockstate multipart):
//   glass           the lite (translucent layer), always
//   frame_left      a 1 px vertical member at the left edge: the end jamb, or the shared mullion of the 1.5 m rhythm
//   frame_right     the end jamb at the right edge
//   mullion_center  the mid-cell mullion of the 1.5 m rhythm
//   rail_top        the head (nothing above) or a transom (toggled on)
//   sill            the bottom rail, the sill flashing over the masonry below and its drip
// Left / right are seen from outside. (Until 2026-10-07 it also recoloured the silver Commercial Glass Double Door texture
// into the black door's; the door V2 has its own generator, tools/build-commercial-glass-double-door-v2.mjs.)
// Pure Mesh + LabPBR atlas exported as Forge OBJ block models (static, chunk-baked, flat lit, blockstate y rotation).
//   node tools/build-storefront-glazing-v1.mjs                -> writes source, OBJ / MTL / block + item models, blockstate, atlas
//   node tools/build-storefront-glazing-v1.mjs --check        -> verifies every output is up to date
//   node tools/build-storefront-glazing-v1.mjs --preview DIR  -> writes only OBJ + maps into DIR (offline review)
// Frame (px): one cell, x, z -8..8, y up, the outside on -Z (FACING north); left = +X (the facing's clockwise side).
// OBJ in block units: x = px / 16 + 0.5, y = px / 16, z = px / 16 + 0.5.
// The frame and the glass continue into the next block: plain colours, no rims (hl 0), so the blocks join without seams.
// Members that cross (rails over jambs and mullions) are inset 0.02 px front and back so no faces are coplanar.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {Part, add, dot, norm, newell, unwrap, paint, png, readPng, zFightLevels} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function assert(c, m) { if (!c) throw new Error(m); }
const D2R = Math.PI / 180;

function quadBox(part, x0, z0, x1, z1, y0, y1, openX = false) {
  const at = k => [k & 1 ? x1 : x0, k & 4 ? y1 : y0, k & 2 ? z1 : z0];
  for (const [ks, n, tag] of [[[0, 1, 3, 2], [0, -1, 0], 'cap'], [[4, 5, 7, 6], [0, 1, 0], 'cap'], [[0, 1, 5, 4], [0, 0, -1], 'side'],
    [[2, 3, 7, 6], [0, 0, 1], 'side'], [[0, 2, 6, 4], [-1, 0, 0], 'side'], [[1, 3, 7, 5], [1, 0, 0], 'side']]) {
    if (openX && n[0]) continue;
    part.face(ks.map(k => part.vtx(at(k))), n, tag);
  }
}

export const MATS = {
  frame: {c: [36, 38, 41], hl: 0, sm: 76, se: 76, f0: 14},       // black anodized aluminium: satin (roughness ~0.5), oxide coat (not a bare-metal F0)
  // 2026-10-06 in game with shaders: sm 128 (roughness 0.25) / F0 26 mirrored the bright sky and ground, so the black frame read light grey
  glass: {c: [176, 204, 204], hl: 0, sm: 240, se: 240, f0: 10},   // clear low-E insulated glass, a faint green-blue; alpha GLASS_ALPHA
};
// > 26 survives the shader packs' translucent alpha test (the cooler's and the pipe's glass: 56); a storefront lite is clearer
const GLASS_ALPHA = 46;
// px. Glass line: the frame's depth band is 3..5 px behind the outer face (z -5..-3), the lite at its middle.
export const SF = {face: 1, frontZ: -5, backZ: -3, inset: 0.02, glass: [-4.15, -3.85], sill: {flash: 0.35, drip: [-8.25, -7.75], dripY: -0.45}};

function build() {
  const PARTS = [], P = (name, bone, mat) => { const p = new Part(`${bone}_${name}`, bone, mat); PARTS.push(p); return p; };
  const F = SF, f0 = F.frontZ, b0 = F.backZ, i0 = f0 + F.inset, j0 = b0 - F.inset;
  {   // the lite: its two broad faces only (its edges always sit inside a frame member or meet the next block's lite)
    const g = P('lite', 'glass', 'glass'), [z0, z1] = F.glass;
    g.face([[-8, 0, z0], [8, 0, z0], [8, 16, z0], [-8, 16, z0]].map(q => g.vtx(q)), [0, 0, -1]);
    g.face([[-8, 0, z1], [8, 0, z1], [8, 16, z1], [-8, 16, z1]].map(q => g.vtx(q)), [0, 0, 1]);
  }
  quadBox(P('member', 'frame_left', 'frame'), 8 - F.face, f0, 8, b0, 0, 16);
  quadBox(P('member', 'frame_right', 'frame'), -8, f0, -8 + F.face, b0, 0, 16);
  quadBox(P('member', 'mullion_center', 'frame'), -F.face / 2, f0, F.face / 2, b0, 0, 16);
  // the rails run on into the next block's rail or end inside a jamb: no end faces at x = +-8, and their outer
  // faces sit 0.02 px inside the cell so they are not coplanar with the jambs' ends
  quadBox(P('member', 'rail_top', 'frame'), -8, i0, 8, j0, 16 - F.face, 16 - F.inset, true);
  quadBox(P('rail', 'sill', 'frame'), -8, i0, 8, j0, F.inset, F.face, true);
  quadBox(P('flashing', 'sill', 'frame'), -8, -8, 8, f0 - 0.01, 0, F.sill.flash);
  quadBox(P('drip', 'sill', 'frame'), -8, F.sill.drip[0], 8, F.sill.drip[1], F.sill.dripY, 0);
  return PARTS;
}

// ---------------- bake: UV, LabPBR maps ----------------
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
const BONES = ['glass', 'frame_left', 'frame_right', 'mullion_center', 'rail_top', 'sill'];
function bake() {
  const PARTS = build();
  const atlas = 1024, UV = unwrap(PARTS, {atlas, pad: 2, startS: 16, stepS: 0.25});
  for (const p of PARTS) assert(p.f.every(f => UV.faceUV.get(p)?.has(f)), `unmapped face in ${p.name}`);
  const painted = paint({PARTS, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas, pad: 2, MATS, ZONED: new Set(), groupInfo: new Map(),
    sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [36, 38, 41, 255], s: [76, 14, 0, 255], n: [128, 128, 255, 255]}});
  const maps = painted.PNG.map((buf, i) => {   // the glass's texels: translucent (base colour alpha; its model is in the translucent layer)
    if (i !== 0) return buf;
    const img = readPng(buf), px = Buffer.alloc(img.w * img.h * 4);
    for (let k = 0; k < img.w * img.h; k++) for (let c = 0; c < 4; c++) px[k * 4 + c] = img.bpp === 4 ? img.px[k * 4 + c] : (c < 3 ? img.px[k * 3 + c] : 255);
    for (const is of UV.islands.filter(is => is.part.mat === 'glass'))
      for (let y = is.py - 2; y < is.py + is.H + 2; y++) for (let x = is.px - 2; x < is.px + is.W + 2; x++) px[(y * img.w + x) * 4 + 3] = GLASS_ALPHA;
    return png(px, img.w, img.h);
  });
  // every frame piece can be drawn in one block together, so all of them are checked as one model
  const coplanar = zFightLevels(PARTS.filter(p => p.bone !== 'glass'), new Map()).unresolved;
  return {id: 'storefront_glazing', PARTS, atlas, UV, maps, coplanar};
}

const uuidOf = (ns, s) => { const h = createHash('sha256').update(`afl-storefront-glazing-v1:${ns}:${s}`).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r12 = v => +v.toFixed(12) || 0;
function sourceOf(b) {   // editable Free Model source (frame as the header, px): one group per model piece
  const uuid = s => uuidOf(b.id, s), name = b.id + '_v1', bones = [...new Set(b.PARTS.map(p => p.bone))];
  const elements = b.PARTS.map(p => {
    const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {}, uvs = b.UV.faceUV.get(p);
    p.v.forEach((q, i) => { vertices[key(i)] = q.map(r12); });
    p.f.forEach((f, fi) => { const uv = uvs.get(f); faces['f' + key(fi)] = {uv: Object.fromEntries(f.ids.map((id, j) => [key(id), uv[j].map(r12)])), vertices: f.ids.map(key), texture: 0}; });
    return {name: p.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: true, locked: false,
      render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: uuid('mesh:' + p.name)};
  });
  return {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name, model_identifier: '', visible_box: [1, 1, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: b.atlas, height: b.atlas},
    elements, groups: bones.map(bone => ({name: bone, uuid: uuid('group:' + bone), export: true, locked: false, scope: 0,
      selected: false, visibility: true, _static: {properties: {}, temp_data: {}}, origin: [0, 0, 0], rotation: [0, 0, 0],
      color: 0, children: [], reset: false, shade: true, mirror_uv: false, autouv: 0, isOpen: true, primary_selected: false})),
    outliner: bones.map(bone => ({uuid: uuid('group:' + bone), isOpen: true, children: elements.filter((e, i) => b.PARTS[i].bone === bone).map(e => e.uuid)})),
    textures: [{name: name + '.png', relative_path: `textures/${name}.png`, folder: '', namespace: '', id: '0', group: '', scope: 0,
      width: b.atlas, height: b.atlas, uv_width: b.atlas, uv_height: b.atlas, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
      file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
      frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture'),
      source: 'data:image/png;base64,' + b.maps[0].toString('base64')}],
    animations: []};
}

// ---------------- OBJ / MTL / models ----------------
const f6 = v => (+v.toFixed(6)).toString();
const toCell = q => [q[0] / 16 + 0.5, q[1] / 16, q[2] / 16 + 0.5];
function objOf(b, title, file, bones) {
  const out = [`# AFL ${title}, generated by tools/build-storefront-glazing-v1.mjs`, `mtllib ${file.split('/').pop()}.mtl`];
  let vBase = 1, tBase = 1, nBase = 1;
  for (const p of b.PARTS) {
    if (!bones.includes(p.bone)) continue;
    const V = p.v.map(toCell), F = p.f;
    out.push(`o ${p.name}`, `usemtl ${b.id}`);
    for (const q of V) out.push(`v ${f6(q[0])} ${f6(q[1])} ${f6(q[2])}`);
    const uvs = b.UV.faceUV.get(p), vt = [], vn = [], fl = [], nIndex = new Map();
    F.forEach(f => {
      // the glass is one flat colour: every corner samples its island's centre, so the mip levels never blend the frame's
      // texels into a lite's edge (seen at a grazing angle as a faint line at every block joint, offline review 2026-10-06)
      const raw = uvs.get(f), mid = raw.reduce((a, q) => [a[0] + q[0] / raw.length, a[1] + q[1] / raw.length], [0, 0]);
      const uv = p.mat === 'glass' ? raw.map(() => mid) : raw, n = norm(newell(f.ids.map(i => V[i]))), key = n.map(f6).join(' ');
      if (!nIndex.has(key)) { nIndex.set(key, nBase + vn.length); vn.push('vn ' + key); }
      fl.push('f ' + f.ids.map((id, j) => { vt.push(`vt ${f6(uv[j][0] / b.atlas)} ${f6(1 - uv[j][1] / b.atlas)}`); return `${vBase + id}/${tBase + vt.length - 1}/${nIndex.get(key)}`; }).join(' '));
    });
    out.push(...vt, ...vn, ...fl);
    vBase += p.v.length; tBase += vt.length; nBase += vn.length;
  }
  return out.join('\n') + '\n';
}
const mtlOf = (b, title) => `# AFL ${title}\nnewmtl ${b.id}\nKd 1 1 1\nmap_Kd apocalypse_firstlight:block/${b.id}\n`;
// flat lit: a facade is lit evenly by the sky, and flat lighting lets the blockstate turn one model to every facing
const objModel = (file, translucent) => ({loader: 'forge:obj', model: `apocalypse_firstlight:models/block/${file}.obj`, automatic_culling: false,
  flip_v: true, shade_quads: true, ambientocclusion: false, ...(translucent ? {render_type: 'minecraft:translucent'} : {}),
  textures: {particle: 'apocalypse_firstlight:block/storefront_glazing'}});
const r3 = v => +v.toFixed(3) || 0;
const S3 = v => [v, v, v];
function guiCentred(points, rotation, scale) {
  const [ax, ay] = rotation.map(v => v * D2R);
  const rot = q => { const x = q[0] * Math.cos(ay) + q[2] * Math.sin(ay), z = -q[0] * Math.sin(ay) + q[2] * Math.cos(ay); return [x, q[1] * Math.cos(ax) - z * Math.sin(ax)]; };
  const pts = points.map(q => rot([q[0] - 8, q[1] - 8, q[2] - 8]));
  const cx = (Math.max(...pts.map(q => q[0])) + Math.min(...pts.map(q => q[0]))) / 2, cy = (Math.max(...pts.map(q => q[1])) + Math.min(...pts.map(q => q[1]))) / 2;
  return {rotation, translation: [r3(-scale * cx), r3(-scale * cy), 0], scale: S3(scale)};
}
function display(b, bones) {
  const pts = b.PARTS.filter(p => bones.includes(p.bone)).flatMap(p => p.v.map(q => toCell(q).map(v => v * 16)));
  return {gui: guiCentred(pts, [30, 225, 0], 0.62), ground: {translation: [0, 2, 0], scale: S3(0.25)}, fixed: {rotation: [0, 180, 0], scale: S3(0.5)},
    thirdperson_righthand: {rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: S3(0.375)}, thirdperson_lefthand: {rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: S3(0.375)},
    firstperson_righthand: {rotation: [0, 45, 0], scale: S3(0.4)}, firstperson_lefthand: {rotation: [0, 225, 0], scale: S3(0.4)}};
}
const ROT = {north: 0, east: 90, south: 180, west: 270};
const ref = (file, y) => ({model: `apocalypse_firstlight:block/${file}`, ...(y % 360 ? {y: y % 360} : {})});

// ---------------- write ----------------
const B = bake();
const bbDir = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const json = v => JSON.stringify(v, null, 2) + '\n';
const outputs = [], objs = [];
outputs.push([path.join(bbDir, 'storefront_glazing_v1.bbmodel'), JSON.stringify(sourceOf(B))],
  ...['', '_s', '_n'].flatMap((k, i) => [[path.join(bbDir, `textures/storefront_glazing_v1${k}.png`), B.maps[i]], [path.join(assets, `textures/block/storefront_glazing${k}.png`), B.maps[i]]]));
const model = (file, title, bones, translucent = false) => {
  const obj = objOf(B, title, file, bones);
  outputs.push([path.join(assets, `models/block/${file}.obj`), obj], [path.join(assets, `models/block/${file}.mtl`), mtlOf(B, title)],
    [path.join(assets, `models/block/${file}.json`), json(objModel(file, translucent))]);
  objs.push([file, obj]);
};
const T = 'Storefront Glazing V1';
model('storefront_glazing/glass', T + ' glass lite', ['glass'], true);
model('storefront_glazing/frame_left', T + ' left jamb / shared mullion', ['frame_left']);
model('storefront_glazing/frame_right', T + ' right jamb', ['frame_right']);
model('storefront_glazing/mullion_center', T + ' mid-cell mullion', ['mullion_center']);
model('storefront_glazing/rail_top', T + ' head / transom', ['rail_top']);
model('storefront_glazing/sill', T + ' sill rail and flashing', ['sill']);
// the item: one lite with both jambs, head and sill, all in one translucent model (the frame's texels are opaque)
const ITEM_BONES = ['glass', 'frame_left', 'frame_right', 'rail_top', 'sill'];
model('storefront_glazing/item', T + ' (item)', ITEM_BONES, true);
outputs.push([path.join(assets, 'models/item/storefront_glazing.json'), json({parent: 'apocalypse_firstlight:block/storefront_glazing/item', gui_light: 'side', display: display(B, ITEM_BONES)})]);
{   // blockstate (StorefrontGlazingBlock: facing, left / right / up / down = connected to glazing of the same facing, mullion, transom)
  const parts = [];
  for (const [F, y] of Object.entries(ROT)) {
    parts.push({when: {facing: F}, apply: ref('storefront_glazing/glass', y)});
    parts.push({when: {OR: [{facing: F, left: 'false'}, {facing: F, mullion: 'edge'}]}, apply: ref('storefront_glazing/frame_left', y)});
    parts.push({when: {facing: F, right: 'false'}, apply: ref('storefront_glazing/frame_right', y)});
    parts.push({when: {facing: F, mullion: 'center'}, apply: ref('storefront_glazing/mullion_center', y)});
    parts.push({when: {OR: [{facing: F, up: 'false'}, {facing: F, transom: 'true'}]}, apply: ref('storefront_glazing/rail_top', y)});
    parts.push({when: {facing: F, down: 'false'}, apply: ref('storefront_glazing/sill', y)});
  }
  outputs.push([path.join(assets, 'blockstates/storefront_glazing.json'), json({multipart: parts})]);
}
const tris = bones => B.PARTS.filter(p => bones.includes(p.bone)).reduce((s, p) => s + p.f.reduce((t, f) => t + f.ids.length - 2, 0), 0);
export const stats = {triangles: Object.fromEntries(BONES.map(k => [k, tris([k])])), texelsPerPx: B.UV.S, islands: B.UV.islands.length, coplanar: B.coplanar.length};
const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  console.log(JSON.stringify(stats));
  if (B.coplanar.length) console.log('COPLANAR', JSON.stringify(B.coplanar.slice(0, 8)));
  const pi = process.argv.indexOf('--preview');
  if (pi > 0) {
    const dir = process.argv[pi + 1]; fs.mkdirSync(dir, {recursive: true});
    for (const [file, obj] of objs) fs.writeFileSync(path.join(dir, file.replace('/', '_') + '.obj'), obj);
    ['', '_s', '_n'].forEach((k, i) => fs.writeFileSync(path.join(dir, `${B.id}${k}.png`), B.maps[i]));
    console.log('preview written to ' + dir);
  } else if (process.argv.includes('--check')) {
    for (const [file, data] of outputs) { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; if (!cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data))) throw new Error('stale ' + path.relative(ROOT, file)); }
    console.log('CHECK OK');
  } else {
    for (const [file] of outputs) fs.mkdirSync(path.dirname(file), {recursive: true});
    for (const [file, data] of outputs) fs.writeFileSync(file, data);
    console.log('wrote ' + outputs.length + ' files');
  }
}
