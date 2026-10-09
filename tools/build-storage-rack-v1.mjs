// Storage Rack V1: grey boltless steel stock shelving for back rooms (reference: a light-duty five-level rack, 2.0 m tall,
// 0.6 m deep, user 2026-10-04). One cell wide, two tall, against a wall (front -Z): equal-leg angle uprights with plastic
// feet, five levels of front / back Z-beams with short side beams carrying a painted steel deck; goods (the shared goods
// library) stand on the lower four decks, the top deck stays bare. Racks side by side share their uprights.
// Pure Mesh + LabPBR atlas exported as Forge OBJ block models (static, chunk-baked), one model set per half:
//   core_<half>          the decks and the front / back beams between x -6 and 6;
//   end_<side>_<half>    an unshared end: the rack's own uprights at the cell's edge and the beams / deck out to them;
//   joint_<side>_<half>  a shared end: the beams / deck out to the shared upright, which the joint on the right (+X) side
//                        draws centred on the boundary (the neighbour's joint_left draws none).
// StorageRackBlock's LEFT / RIGHT (a rack with the same facing on that side) pick end or joint per half.
//   node tools/build-storage-rack-v1.mjs                -> writes source, OBJ / MTL / block + item models, blockstate, atlas
//   node tools/build-storage-rack-v1.mjs --check        -> verifies every output is up to date
//   node tools/build-storage-rack-v1.mjs --preview DIR  -> writes only OBJ + maps into DIR (offline review)
// Frame (px): the lower cell, x -8..8 (left = -X = the facing's counter-clockwise side), z -8..8, y 0..32 over both halves.
// OBJ in block units of each half's own cell: x = px / 16 + 0.5, y = px / 16 (upper: minus 1), z = px / 16 + 0.5.
// Plain surfaces: no slots, labels or decals.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {Part, extrude, add, sub, dot, cross, norm, newell, area2, unwrap, paint, png, zFightLevels} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function assert(c, m) { if (!c) throw new Error(m); }
const D2R = Math.PI / 180;

// ---------------- primitives ----------------
const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
const rect = (u0, v0, u1, v1) => [[u0, v0], [u1, v0], [u1, v1], [u0, v1]];
const shape = L => ({outer: orient(L, true), holes: []});
const zx = L => L.map(([x, z]) => [z, x]);
const planY = (part, L, y0, y1, c = 0) => extrude(part, 'y', shape(zx(L)), y0, y1, c);
const sideX = (part, L, x0, x1, c = 0) => extrude(part, 'x', shape(L), x0, x1, c);   // section (z, y)
const box = (part, a, b, c = 0) => planY(part, rect(a[0], a[2], b[0], b[2]), a[1], b[1], c);

// ---------------- textured base colours (low-frequency only) ----------------
const hash = (a, b, c) => { let h = Math.imul(a | 0, 374761393) ^ Math.imul(b | 0, 668265263) ^ Math.imul(c | 0, 2147483647); h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };
function vn(x, y, z) {
  const xi = Math.floor(x), yi = Math.floor(y), zi = Math.floor(z), s = t => t * t * (3 - 2 * t), l = (a, b, t) => a + (b - a) * t;
  const sx = s(x - xi), sy = s(y - yi), sz = s(z - zi), h = (a, b, c) => hash(a, b, c * 7919 + 101);
  return l(l(l(h(xi, yi, zi), h(xi + 1, yi, zi), sx), l(h(xi, yi + 1, zi), h(xi + 1, yi + 1, zi), sx), sy),
    l(l(h(xi, yi, zi + 1), h(xi + 1, yi, zi + 1), sx), l(h(xi, yi + 1, zi + 1), h(xi + 1, yi + 1, zi + 1), sx), sy), sz);
}
const fbm = (x, y, z) => 0.6 * vn(x, y, z) + 0.3 * vn(x * 2.03 + 7.1, y * 2.03 + 3.3, z * 2.03 + 1.7) + 0.1 * vn(x * 4.1 + 2.9, y * 4.1 + 8.1, z * 4.1 + 5.3);
const tone = (c, k) => c.map(v => v * k), mott = (p, s, k) => k * (fbm(p[0] * s, p[1] * s, p[2] * s) - 0.5);
export const MATS = {
  frame: {c: p => tone([72, 76, 81], 1 + mott(p, 0.3, 0.05)), hl: 9, sm: 104, se: 128, f0: 24},    // dark grey powder-coated steel
  deck:  {c: p => tone([98, 102, 107], 1 + mott(p, 0.25, 0.05)), hl: 7, sm: 96, se: 118, f0: 24},  // painted steel deck, a shade lighter
  foot:  {c: [40, 41, 44], hl: 3, sm: 70, se: 80, f0: 20},                                         // black plastic feet
};

// ---------------- the rack ----------------
export const RACK = {
  front: -2.2, back: 7.8, top: 31.2, levels: [1.4, 8.6, 15.8, 23.0, 30.2],   // deck tops; goods on the lower four
  post: 1.3, t: 0.22, beam: 1.1, deck: 0.2, core: 6.0, foot: 0.35,
  get endInner() { return 8 - this.post; }, get jointInner() { return 8 - this.post / 2; },
};
const R = RACK;
// the part of [y0, y1] in a half (lower 0..16, upper 16..32)
const clip = (half, y0, y1) => { const a = half === 'lower' ? 0 : 16, b = a + 16; return [Math.max(a, y0), Math.min(b, y1)]; };
function beamsAndDecks(P, half, x0, x1, name, sideBeamsAt = null) {
  const beams = P(name + '_beams', 'frame'), deck = P(name + '_deck', 'deck');
  for (const y of R.levels) {
    if (y - R.beam < (half === 'lower' ? 0 : 16) || y > (half === 'lower' ? 16 : 32)) continue;
    for (const z of [R.front, R.back]) {   // Z-beam (section z, y): the web and the step under the deck
      const s = z === R.front ? 1 : -1;
      sideX(beams, [[z, y - R.beam], [z + s * 0.2, y - R.beam], [z + s * 0.2, y - 0.25], [z + s * 0.75, y - 0.25], [z + s * 0.75, y], [z, y]], x0, x1);
    }
    // the deck ends inside a side beam (its end face would lie on the beam's outer face)
    const d0 = sideBeamsAt !== null && sideBeamsAt < 0 ? sideBeamsAt + R.t / 2 : x0, d1 = sideBeamsAt !== null && sideBeamsAt > 0 ? sideBeamsAt - R.t / 2 : x1;
    box(deck, [d0, y - 0.25 - R.deck, R.front + 0.75], [d1, y - 0.25, R.back - 0.75]);
    if (sideBeamsAt !== null) {   // the short side beam along the end (between the front and back uprights)
      const xs = sideBeamsAt < 0 ? [sideBeamsAt, sideBeamsAt + R.t] : [sideBeamsAt - R.t, sideBeamsAt];
      box(beams, [xs[0], y - 0.8, R.front + R.post], [xs[1], y, R.back - R.post]);
    }
  }
}
function angle(P, part, x, z, sx, sz, y0, y1) {   // an equal-leg angle whose corner is at (x, z), legs toward -sx / -sz
  const L = R.post, T = R.t;
  const pts = [[x, z], [x - sx * L, z], [x - sx * L, z - sz * T], [x - sx * T, z - sz * T], [x - sx * T, z - sz * L], [x, z - sz * L]];
  planY(part, pts, y0, y1);
}
function end(P, half, side) {   // the rack's own uprights at x = side * 8, beams and deck from the core out to them
  const n = `end_${side < 0 ? 'left' : 'right'}_${half}`, inner = side * R.endInner;
  beamsAndDecks(P, half, Math.min(side * R.core, inner), Math.max(side * R.core, inner), n, inner);
  const posts = P(n + '_posts', 'frame'), [y0, y1] = clip(half, R.foot, R.top);
  for (const [z, sz] of [[R.front, -1], [R.back, 1]]) {
    angle(P, posts, side * 8, z, side, sz, y0, y1);
    if (half === 'lower') { const foot = P(n + '_foot' + sz, 'foot'), xa = side * 8, xb = side * (8 - R.post), za = z, zb = z - sz * R.post;
      box(foot, [Math.min(xa, xb), 0, Math.min(za, zb)], [Math.max(xa, xb), R.foot, Math.max(za, zb)]); }
  }
}
function joint(P, half, side) {   // beams and deck out to the shared upright; the right joint draws the upright itself
  const n = `joint_${side < 0 ? 'left' : 'right'}_${half}`, inner = side * R.jointInner;
  beamsAndDecks(P, half, Math.min(side * R.core, inner), Math.max(side * R.core, inner), n, side > 0 ? inner : null);
  if (side < 0) return;
  const posts = P(n + '_posts', 'frame'), [y0, y1] = clip(half, R.foot, R.top);
  // the shared upright: a T of two angles back to back, centred on the boundary
  for (const [z, sz] of [[R.front, -1], [R.back, 1]]) {
    angle(P, posts, 8 + R.post / 2, z, 1, sz, y0, y1);
    box(posts, [8 - R.post / 2, y0, sz < 0 ? z : z - R.t], [8 + R.post / 2 - R.t, y1, sz < 0 ? z + R.t : z]);
    if (half === 'lower') box(P(n + '_foot' + sz, 'foot'), [8 - R.post / 2, 0, Math.min(z, z - sz * R.post)], [8 + R.post / 2, R.foot, Math.max(z, z - sz * R.post)]);
  }
}
export const PIECES = {};
for (const half of ['lower', 'upper']) {
  PIECES['core_' + half] = P => beamsAndDecks(P, half, -R.core, R.core, 'core_' + half);
  for (const side of [-1, 1]) {
    const s = side < 0 ? 'left' : 'right';
    PIECES[`end_${s}_${half}`] = P => end(P, half, side);
    PIECES[`joint_${s}_${half}`] = P => joint(P, half, side);
  }
}

// ---------------- bake: UV, LabPBR maps ----------------
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
function bake() {
  const PARTS = [];
  for (const [piece, build] of Object.entries(PIECES)) build((name, mat) => { const p = new Part(`${piece}__${name}`, piece, mat); PARTS.push(p); return p; });
  for (const p of PARTS) {
    const out = [];
    for (const f of p.f) {
      const P3 = f.ids.map(i => p.v[i]), n = newell(P3);
      const convexQuad = f.ids.length === 4 && [0, 1, 2, 3].every(k => dot(cross(sub(P3[(k + 1) % 4], P3[k]), sub(P3[(k + 2) % 4], P3[(k + 1) % 4])), n) > 1e-12);
      if (f.ids.length === 3 || convexQuad) { out.push(f); continue; }
      assert(f.ids.length === 4, `${f.ids.length}-gon in ${p.name}`);
      const k = [0, 1, 2, 3].find(k => dot(cross(sub(P3[k], P3[(k + 3) % 4]), sub(P3[(k + 1) % 4], P3[k])), n) <= 1e-12) ?? 0;
      out.push({...f, ids: [f.ids[k], f.ids[(k + 1) % 4], f.ids[(k + 2) % 4]]}, {...f, ids: [f.ids[(k + 2) % 4], f.ids[(k + 3) % 4], f.ids[k]]});
    }
    p.f = out;
  }
  const live = PARTS.filter(p => p.f.length), atlas = 1024;
  const UV = unwrap(live, {atlas, pad: 2, startS: 30, stepS: 0.25});
  for (const p of live) assert(p.f.every(f => UV.faceUV.get(p)?.has(f)), `unmapped face in ${p.name}`);
  const painted = paint({PARTS: live, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas, pad: 2, MATS, ZONED: new Set(), groupInfo: new Map(),
    sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [72, 76, 81, 255], s: [104, 24, 0, 255], n: [128, 128, 255, 255]}});
  // coplanar overlaps inside each drawn combination of pieces (one half: the core, one side each)
  const combos = [];
  for (const half of ['lower', 'upper']) for (const l of ['end', 'joint']) for (const r of ['end', 'joint'])
    combos.push([`core_${half}`, `${l}_left_${half}`, `${r}_right_${half}`]);
  const coplanar = combos.flatMap(c => zFightLevels(live.filter(p => c.includes(p.bone)), new Map()).unresolved);
  return {id: 'storage_rack', PARTS: live, atlas, UV, maps: painted.PNG, coplanar};
}

const uuidOf = (ns, s) => { const h = createHash('sha256').update(`afl-storage-rack-v1:${ns}:${s}`).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r12 = v => +v.toFixed(12) || 0;
function sourceOf(b) {   // editable Free Model source (frame as the header, px): one group per piece
  const uuid = s => uuidOf(b.id, s), name = b.id + '_v1', bones = [...new Set(b.PARTS.map(p => p.bone))];
  const elements = b.PARTS.map(p => {
    const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {}, uvs = b.UV.faceUV.get(p);
    p.v.forEach((q, i) => { vertices[key(i)] = q.map(r12); });
    p.f.forEach((f, fi) => { const uv = uvs.get(f); faces['f' + key(fi)] = {uv: Object.fromEntries(f.ids.map((id, j) => [key(id), uv[j].map(r12)])), vertices: f.ids.map(key), texture: 0}; });
    return {name: p.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: true, locked: false,
      render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: uuid('mesh:' + p.name)};
  });
  return {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name, model_identifier: '', visible_box: [1, 2, 0],
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
const SMOOTH = Math.cos(36 * D2R);
function cornerNormals(V, F) {
  const fn = F.map(f => newell(f.ids.map(i => V[i]))), byV = new Map();
  F.forEach((f, k) => f.ids.forEach(i => (byV.get(i) || byV.set(i, []).get(i)).push(k)));
  return F.map((f, k) => { const n0 = norm(fn[k]); return f.ids.map(i => norm(byV.get(i).reduce((a, j) => dot(norm(fn[j]), n0) >= SMOOTH ? add(a, fn[j]) : a, [0, 0, 0]))); });
}
function objOf(b, title, file, bones, dy) {
  const out = [`# AFL ${title}, generated by tools/build-storage-rack-v1.mjs`, `mtllib ${file.split('/').pop()}.mtl`];
  let vBase = 1, tBase = 1, nBase = 1;
  for (const p of b.PARTS) {
    if (!bones.includes(p.bone)) continue;
    const V = p.v.map(q => [q[0] / 16 + 0.5, (q[1] - dy) / 16, q[2] / 16 + 0.5]), F = p.f;
    out.push(`o ${p.name}`, `usemtl ${b.id}`);
    for (const q of V) out.push(`v ${f6(q[0])} ${f6(q[1])} ${f6(q[2])}`);
    const uvs = b.UV.faceUV.get(p), vt = [], vn = [], fl = [], cn = cornerNormals(V, F), nIndex = new Map();
    F.forEach((f, k) => {
      const uv = uvs.get(f);
      fl.push('f ' + f.ids.map((id, j) => {
        const n = cn[k][j], key = n.map(f6).join(' ');
        if (!nIndex.has(key)) { nIndex.set(key, nBase + vn.length); vn.push('vn ' + key); }
        vt.push(`vt ${f6(uv[j][0] / b.atlas)} ${f6(1 - uv[j][1] / b.atlas)}`); return `${vBase + id}/${tBase + vt.length - 1}/${nIndex.get(key)}`;
      }).join(' '));
    });
    out.push(...vt, ...vn, ...fl);
    vBase += p.v.length; tBase += vt.length; nBase += vn.length;
  }
  return out.join('\n') + '\n';
}
const mtlOf = (b, title) => `# AFL ${title}\nnewmtl ${b.id}\nKd 1 1 1\nmap_Kd apocalypse_firstlight:block/${b.id}\n`;
const objModel = file => ({loader: 'forge:obj', model: `apocalypse_firstlight:models/block/${file}.obj`, automatic_culling: false,
  flip_v: true, shade_quads: true, ambientocclusion: false, textures: {particle: 'apocalypse_firstlight:block/storage_rack'}});
const r3 = v => +v.toFixed(3) || 0;
const S3 = v => [v, v, v];
function guiCentred(points, rotation, scale) {
  const [ax, ay] = rotation.map(v => v * D2R);
  const rot = q => { const x = q[0] * Math.cos(ay) + q[2] * Math.sin(ay), z = -q[0] * Math.sin(ay) + q[2] * Math.cos(ay); return [x, q[1] * Math.cos(ax) - z * Math.sin(ax)]; };
  const pts = points.map(q => rot([q[0] - 8, q[1] - 8, q[2] - 8]));
  const cx = (Math.max(...pts.map(q => q[0])) + Math.min(...pts.map(q => q[0]))) / 2, cy = (Math.max(...pts.map(q => q[1])) + Math.min(...pts.map(q => q[1]))) / 2;
  return {rotation, translation: [r3(-scale * cx), r3(-scale * cy), 0], scale: S3(scale)};
}
const DIRS = ['north', 'east', 'south', 'west'], ROT = {north: 0, east: 90, south: 180, west: 270};
const ref = (model, facing) => ({model: `apocalypse_firstlight:block/${model}`, ...(ROT[facing] ? {y: ROT[facing]} : {})});
function blockstate() {
  const parts = [];
  for (const F of DIRS) for (const half of ['lower', 'upper']) {
    parts.push({when: {facing: F, half}, apply: ref(`storage_rack/core_${half}`, F)});
    for (const side of ['left', 'right']) for (const [joined, kind] of [['false', 'end'], ['true', 'joint']])
      parts.push({when: {facing: F, half, [side]: joined}, apply: ref(`storage_rack/${kind}_${side}_${half}`, F)});
  }
  return {multipart: parts};
}

// ---------------- write ----------------
const B = bake();
const bbDir = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const json = v => JSON.stringify(v, null, 2) + '\n';
const outputs = [], objs = [];
outputs.push([path.join(bbDir, 'storage_rack_v1.bbmodel'), JSON.stringify(sourceOf(B))],
  ...['', '_s', '_n'].flatMap((k, i) => [[path.join(bbDir, `textures/storage_rack_v1${k}.png`), B.maps[i]], [path.join(assets, `textures/block/storage_rack${k}.png`), B.maps[i]]]));
const model = (name, bones, dy) => {
  const file = 'storage_rack/' + name, obj = objOf(B, `Storage Rack V1 ${name}`, file, bones, dy);
  outputs.push([path.join(assets, `models/block/${file}.obj`), obj], [path.join(assets, `models/block/${file}.mtl`), mtlOf(B, `Storage Rack V1 ${name}`)],
    [path.join(assets, `models/block/${file}.json`), json(objModel(file))]);
  objs.push([file, obj]);
};
for (const piece of Object.keys(PIECES)) model(piece, [piece], piece.endsWith('upper') ? 16 : 0);
const ITEM = ['core_lower', 'core_upper', 'end_left_lower', 'end_right_lower', 'end_left_upper', 'end_right_upper'];
model('item', ITEM, 0);
const itemPts = B.PARTS.filter(p => ITEM.includes(p.bone)).flatMap(p => p.v.map(q => [q[0] + 8, q[1], q[2] + 8]));
// gui scale 0.41: at 0.5 the two-block rack was 18.1 px tall and stuck out of the 16 px slot (user 2026-10-08); now 14.9 px
const GUI_SCALE = 0.41;
outputs.push([path.join(assets, 'models/item/storage_rack.json'), json({parent: 'apocalypse_firstlight:block/storage_rack/item', gui_light: 'side', display: {
  gui: guiCentred(itemPts, [30, 225, 0], GUI_SCALE), ground: {translation: [0, 2, 0], scale: S3(0.25)}, fixed: {rotation: [0, 180, 0], translation: [0, -4, 0], scale: S3(0.5)},
  thirdperson_righthand: {rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: S3(0.25)}, thirdperson_lefthand: {rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: S3(0.25)},
  firstperson_righthand: {rotation: [0, 45, 0], translation: [0, 1.5, 0], scale: S3(0.29)}, firstperson_lefthand: {rotation: [0, 225, 0], translation: [0, 1.5, 0], scale: S3(0.29)}}})]);
outputs.push([path.join(assets, 'blockstates/storage_rack.json'), json(blockstate())]);

const tris = bones => B.PARTS.filter(p => bones.includes(p.bone)).reduce((s, p) => s + p.f.reduce((t, f) => t + f.ids.length - 2, 0), 0);
export const stats = {triangles: Object.fromEntries(Object.keys(PIECES).map(k => [k, tris([k])])), lone: tris(ITEM), texelsPerPx: B.UV.S, islands: B.UV.islands.length,
  coplanar: B.coplanar.length, gui: guiCentred(itemPts, [30, 225, 0], GUI_SCALE).translation};
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
