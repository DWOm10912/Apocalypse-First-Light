// Fuel Island Kit V1: the pieces a fuel island is built from around Fuel Dispenser V1 (whose own 2 x 1 segment has the
// same section): a straight curb, a half-round end and a bollard (its own block; on a curb or an end it stands in the cell
// above and its model sinks 13 px onto the curb top, the 'bollard_on_curb' model). Concrete
// curb 3 px high with a steel angle on its top edges (the end's angle follows the half round); the bollard is a grey
// galvanised steel pipe with a domed cap, a bolted base flange and three reflective sleeves (yellow / black / yellow).
// Pure Mesh + LabPBR atlas exported as Forge OBJ block models (static, chunk-baked).
//   node tools/build-fuel-island-v1.mjs                -> writes source, OBJ / MTL / block + item models, blockstates, atlas
//   node tools/build-fuel-island-v1.mjs --check        -> verifies every output is up to date
//   node tools/build-fuel-island-v1.mjs --preview DIR  -> writes only OBJ + maps into DIR (offline review)
// Frame (px): one cell, x -8..8 along the island, z -8..8, y up; facing north the island runs east-west (x), as the
// dispenser's segment does (FuelDispenserBlock: the island along the facing's clockwise side). The end's joining side is
// -X, its half round toward +X. OBJ in block units: x = px / 16 + 0.5, y = px / 16, z = px / 16 + 0.5.
// Plain surfaces: no printed legends; the reflective bands are separate sleeves, not paint.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {Part, extrude, add, sub, mul, dot, cross, norm, newell, area2, unwrap, paint, png, zFightLevels} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function assert(c, m) { if (!c) throw new Error(m); }
const D2R = Math.PI / 180;

// ---------------- primitives ----------------
const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
const rect = (u0, v0, u1, v1) => [[u0, v0], [u1, v0], [u1, v1], [u0, v1]];
const shape = (L, holes = []) => ({outer: orient(L, true), holes: holes.map(h => orient(h, false))});
const zx = L => L.map(([x, z]) => [z, x]);
const planY = (part, L, y0, y1, c) => extrude(part, 'y', shape(zx(L)), y0, y1, c);
const arc = (cx, cz, r, a0, a1, n) => Array.from({length: n + 1}, (_, i) => { const a = (a0 + (a1 - a0) * i / n) * D2R; return [cx + r * Math.cos(a), cz + r * Math.sin(a)]; });
// a surface of revolution about a vertical axis: profile [[y, r]] bottom to top; caps where asked (bottom / top)
function lathe(part, cx, cz, profile, seg, caps = [true, true]) {
  const ring = ([y, r]) => Array.from({length: seg}, (_, i) => { const a = 2 * Math.PI * (i + 0.5) / seg; return part.vtx([cx + r * Math.cos(a), y, cz + r * Math.sin(a)]); });
  const rings = profile.map(ring);
  for (let k = 0; k + 1 < profile.length; k++) for (let i = 0; i < seg; i++) {
    const j = (i + 1) % seg, a = 2 * Math.PI * (i + 1) / seg, [y0, r0] = profile[k], [y1, r1] = profile[k + 1];
    const n = [Math.cos(a) * (y1 - y0), r0 - r1, Math.sin(a) * (y1 - y0)];
    part.face([rings[k][i], rings[k][j], rings[k + 1][j], rings[k + 1][i]], Math.hypot(...n) > 1e-9 ? n : [Math.cos(a), 0, Math.sin(a)]);
  }
  [[0, -1], [profile.length - 1, 1]].forEach(([k, s], c) => { if (!caps[c]) return; const m = part.vtx([cx, profile[k][0], cz]);
    for (let i = 0; i < seg; i++) part.face([m, rings[k][i], rings[k][(i + 1) % seg]], [0, s, 0], 'cap'); });
}
// sweeps a closed section (s outward from the path, y) along an open plan path (x, z), mitred, with flat end caps given
// as convex pieces of the section (index lists)
function sweep(part, pts, section, capPieces) {
  const n = pts.length, normals = pts.map((p, i) => {
    const a = pts[Math.max(0, i - 1)], b = pts[Math.min(n - 1, i + 1)], d = norm([b[0] - a[0], 0, b[1] - a[1]]);
    let o = [d[2], -d[0]];   // the right-hand normal of the path direction (x, z)
    if (i > 0 && i < n - 1) {
      const d0 = norm([pts[i][0] - pts[i - 1][0], 0, pts[i][1] - pts[i - 1][1]]), d1 = norm([pts[i + 1][0] - pts[i][0], 0, pts[i + 1][1] - pts[i][1]]);
      const n0 = [d0[2], -d0[0]], n1 = [d1[2], -d1[0]], m = norm([n0[0] + n1[0], 0, n0[1] + n1[1]]), k = 1 / Math.max(0.3, m[0] * n0[0] + m[2] * n0[1]);
      o = [m[0] * k, m[2] * k];
    }
    return o;
  });
  const rings = pts.map((p, i) => section.map(([s, y]) => part.vtx([p[0] + normals[i][0] * s, y, p[1] + normals[i][1] * s])));
  const c = section.reduce((a, q) => [a[0] + q[0] / section.length, a[1] + q[1] / section.length], [0, 0]);
  for (let i = 0; i + 1 < n; i++) for (let k = 0; k < section.length; k++) {
    const k2 = (k + 1) % section.length, mid = [(section[k][0] + section[k2][0]) / 2, (section[k][1] + section[k2][1]) / 2];
    const ns = [mid[0] - c[0], mid[1] - c[1]], o = normals[i], hint = [o[0] * ns[0], ns[1], o[1] * ns[0]];
    part.face([rings[i][k], rings[i][k2], rings[i + 1][k2], rings[i + 1][k]], hint);
  }
  for (const [i, j] of [[0, 1], [n - 1, n - 2]]) {
    const d = norm([pts[i][0] - pts[j][0], 0, pts[i][1] - pts[j][1]]);
    for (const piece of capPieces) part.face(piece.map(k => rings[i][k]), d, 'cap');
  }
}

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
export const MATS = {   // the curb's concrete and nosing match tools/build-fuel-dispenser-v1.mjs
  concrete: {c: p => tone([146, 144, 138], 1 + mott(p, 0.22, 0.08) + 0.04 * (vn(p[0] * 0.9, p[1] * 0.9, p[2] * 0.9) - 0.5)), hl: 5, sm: 40, se: 52, f0: 20},
  nosing:   {c: [126, 130, 132], hl: 8, sm: 112, se: 140, f0: 255},
  post:     {c: p => tone([118, 122, 126], 1 + mott(p, 0.5, 0.05)), hl: 9, sm: 110, se: 132, f0: 230},   // galvanised steel (LabPBR iron)
  yellow:   {c: [196, 160, 52], hl: 6, sm: 150, se: 160, f0: 24},    // reflective sheeting
  black:    {c: [30, 31, 33], hl: 3, sm: 140, se: 150, f0: 22},
  bolt:     {c: [96, 100, 104], hl: 6, sm: 120, se: 130, f0: 255},
};

// ---------------- the pieces ----------------
export const CURB = {h: 3.0, leg: [8.13, 7.96, 1.6], flange: [7.1, 2.94, 3.07]};   // the steel angle: leg outer / inner, its bottom; flange inner, bottom, top
// the angle's section (s = distance outward from the edge line at r 8 / z +-8, y): one L, two convex cap pieces
const L_SECTION = [[0.13, 1.6], [-0.04, 1.6], [-0.04, 2.94], [-0.9, 2.94], [-0.9, 3.07], [0.13, 3.07]];
const L_CAPS = [[0, 1, 2, 5], [2, 3, 4, 5]];
export const BOLLARD = {r: 1.45, height: 16.5, flange: 2.6, bolts: 2.0, bands: [['yellow', 11.0, 12.2], ['black', 12.2, 13.0], ['yellow', 13.0, 14.2]]};
function straight(P) {
  slab(P('curb', 'concrete'), [-8, 0, -8], [8, CURB.h, 8]);
  const n = P('nosing', 'nosing');
  for (const s of [-1, 1]) extrude(n, 'x', shape(L_SECTION.map(([o, y]) => [s * (8 + o), y])), -7.99, 7.99, 0);
}
function slab(part, a, b) { extrude(part, 'y', shape(zx(rect(a[0], a[2], b[0], b[2]))), a[1], b[1], 0); }
function end(P) {
  planY(P('curb', 'concrete'), [[-8, -8], ...arc(0, 0, 8, -90, 90, 24), [-8, 8]], 0, CURB.h, 0);
  // the angle runs from the joining side along -Z, round the half round, back along +Z: in this direction the path's
  // right-hand side is outward, where the section's s points (2026-10-04: the first version ran the other way, the angle
  // came out inside out and stepped against the straight curb's at the joint)
  sweep(P('nosing', 'nosing'), [[-7.99, -8], ...arc(0, 0, 8, -90, 90, 24), [-7.99, 8]], L_SECTION, L_CAPS);
}
function bollard(P, cx, base) {
  const B = BOLLARD, R = B.r, H = B.height;
  lathe(P('post', 'post'), cx, 0, [[base + 0.5, R], [base + H - 1.0, R], [base + H - 0.4, R * 0.78], [base + H, R * 0.35], [base + H + 0.08, 0.05]], 16, [false, true]);
  lathe(P('flange', 'post'), cx, 0, [[base, B.flange], [base + 0.35, B.flange], [base + 0.5, B.flange - 0.3], [base + 0.5, R - 0.01]], 16, [true, true]);
  const bolts = P('bolts', 'bolt');
  for (let k = 0; k < 4; k++) { const a = (45 + 90 * k) * D2R; lathe(bolts, cx + B.bolts * Math.cos(a), B.bolts * Math.sin(a), [[base + 0.45, 0.32], [base + 0.75, 0.32], [base + 0.82, 0.18]], 6, [false, true]); }
  // the reflective sleeves: one continuous skin over the pipe, its bands each their own material, capped only at its ends
  B.bands.forEach(([mat, y0, y1], i) => lathe(P('band_' + i, mat), cx, 0, [[base + y0, R + 0.05], [base + y1, R + 0.05]], 16, [i === 0, i === B.bands.length - 1]));
}
export const PIECES = {
  straight: P => straight(P),
  end: P => end(P),
  bollard: P => bollard(P, 0, 0),
};

// ---------------- bake: UV, LabPBR maps ----------------
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
function bake() {
  const PARTS = [];
  for (const [piece, build] of Object.entries(PIECES)) build((name, mat) => { const p = new Part(`${piece}_${name}`, piece, mat); PARTS.push(p); return p; });
  for (const p of PARTS) {   // Forge draws a quad as 0-1-2 / 2-3-0: split anything that is not a triangle or a strictly convex quad
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
    sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [120, 120, 120, 255], s: [60, 20, 0, 255], n: [128, 128, 255, 255]}});
  const coplanar = Object.keys(PIECES).flatMap(k => zFightLevels(live.filter(p => p.bone === k), new Map()).unresolved);
  return {id: 'fuel_island', PARTS: live, atlas, UV, maps: painted.PNG, coplanar};
}

const uuidOf = (ns, s) => { const h = createHash('sha256').update(`afl-fuel-island-v1:${ns}:${s}`).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
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
const SMOOTH = Math.cos(36 * D2R), SMOOTH_ROUND = Math.cos(50 * D2R);   // the 16-sided pipe and sleeves shade round
function cornerNormals(V, F, smooth) {
  const fn = F.map(f => newell(f.ids.map(i => V[i]))), byV = new Map();
  F.forEach((f, k) => f.ids.forEach(i => (byV.get(i) || byV.set(i, []).get(i)).push(k)));
  return F.map((f, k) => { const n0 = norm(fn[k]); return f.ids.map(i => norm(byV.get(i).reduce((a, j) => dot(norm(fn[j]), n0) >= smooth ? add(a, fn[j]) : a, [0, 0, 0]))); });
}
const toCell = q => [q[0] / 16 + 0.5, q[1] / 16, q[2] / 16 + 0.5];
function objOf(b, title, file, piece, dy = 0) {
  const out = [`# AFL ${title}, generated by tools/build-fuel-island-v1.mjs`, `mtllib ${file.split('/').pop()}.mtl`];
  let vBase = 1, tBase = 1, nBase = 1;
  for (const p of b.PARTS) {
    if (p.bone !== piece) continue;
    const V = p.v.map(q => toCell([q[0], q[1] + dy, q[2]])), F = p.f, round = /post|band|flange/.test(p.name);
    out.push(`o ${p.name}`, `usemtl ${b.id}`);
    for (const q of V) out.push(`v ${f6(q[0])} ${f6(q[1])} ${f6(q[2])}`);
    const uvs = b.UV.faceUV.get(p), vt = [], vn = [], fl = [], cn = cornerNormals(V, F, round ? SMOOTH_ROUND : SMOOTH), nIndex = new Map();
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
  flip_v: true, shade_quads: true, ambientocclusion: false, textures: {particle: 'apocalypse_firstlight:block/fuel_island'}});
const r3 = v => +v.toFixed(3) || 0;
const S3 = v => [v, v, v];
function guiCentred(points, rotation, scale) {
  const [ax, ay] = rotation.map(v => v * D2R);
  const rot = q => { const x = q[0] * Math.cos(ay) + q[2] * Math.sin(ay), z = -q[0] * Math.sin(ay) + q[2] * Math.cos(ay); return [x, q[1] * Math.cos(ax) - z * Math.sin(ax)]; };
  const pts = points.map(q => rot([q[0] - 8, q[1] - 8, q[2] - 8]));
  const cx = (Math.max(...pts.map(q => q[0])) + Math.min(...pts.map(q => q[0]))) / 2, cy = (Math.max(...pts.map(q => q[1])) + Math.min(...pts.map(q => q[1]))) / 2;
  return {rotation, translation: [r3(-scale * cx), r3(-scale * cy), 0], scale: S3(scale)};
}
function display(b, piece) {
  const pts = b.PARTS.filter(p => p.bone === piece).flatMap(p => p.v.map(q => toCell(q).map(v => v * 16)));
  const tall = piece === 'bollard', s = tall ? 0.5 : 0.62;
  return {gui: guiCentred(pts, [30, 225, 0], tall ? 0.55 : 0.62), ground: {translation: [0, 2, 0], scale: S3(s * 0.5)}, fixed: {rotation: [0, 180, 0], scale: S3(s)},
    thirdperson_righthand: {rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: S3(s * 0.6)}, thirdperson_lefthand: {rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: S3(s * 0.6)},
    firstperson_righthand: {rotation: [0, 45, 0], scale: S3(s * 0.7)}, firstperson_lefthand: {rotation: [0, 225, 0], scale: S3(s * 0.7)}};
}
const ROT = {north: 0, east: 90, south: 180, west: 270};
const facingBlockstate = model => ({variants: Object.fromEntries(Object.entries(ROT).map(([f, y]) => [`facing=${f}`, {model: `apocalypse_firstlight:block/${model}`, ...(y ? {y} : {})}]))});

// ---------------- write ----------------
const B = bake();
const bbDir = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const json = v => JSON.stringify(v, null, 2) + '\n';
const BLOCKS = {straight: 'fuel_island_curb', end: 'fuel_island_end', bollard: 'fuel_island_bollard'};
const outputs = [], objs = [];
outputs.push([path.join(bbDir, 'fuel_island_v1.bbmodel'), JSON.stringify(sourceOf(B))],
  ...['', '_s', '_n'].flatMap((k, i) => [[path.join(bbDir, `textures/fuel_island_v1${k}.png`), B.maps[i]], [path.join(assets, `textures/block/fuel_island${k}.png`), B.maps[i]]]));
for (const [piece, id] of Object.entries(BLOCKS)) {
  const file = 'fuel_island/' + piece, obj = objOf(B, `Fuel Island V1 ${piece}`, file, piece);
  outputs.push([path.join(assets, `models/block/${file}.obj`), obj], [path.join(assets, `models/block/${file}.mtl`), mtlOf(B, `Fuel Island V1 ${piece}`)],
    [path.join(assets, `models/block/${file}.json`), json(objModel(file))],
    [path.join(assets, `models/item/${id}.json`), json({parent: `apocalypse_firstlight:block/${file}`, gui_light: 'side', display: display(B, piece)})],
    [path.join(assets, `blockstates/${id}.json`), json(piece === 'bollard'
      ? {variants: {'on_curb=false': {model: `apocalypse_firstlight:block/${file}`}, 'on_curb=true': {model: `apocalypse_firstlight:block/${file}_on_curb`}}}
      : facingBlockstate(file))]);
  objs.push([file, obj]);
}
{ // the bollard on a curb or an end: its block is the cell above, the model sinks onto the curb top (y 3 of the cell below)
  const file = 'fuel_island/bollard_on_curb', obj = objOf(B, 'Fuel Island V1 bollard on a curb', file, 'bollard', CURB.h - 16);
  outputs.push([path.join(assets, `models/block/${file}.obj`), obj], [path.join(assets, `models/block/${file}.mtl`), mtlOf(B, 'Fuel Island V1 bollard on a curb')],
    [path.join(assets, `models/block/${file}.json`), json(objModel(file))]);
  objs.push([file, obj]);
}
const tris = piece => B.PARTS.filter(p => p.bone === piece).reduce((s, p) => s + p.f.reduce((t, f) => t + f.ids.length - 2, 0), 0);
export const stats = {triangles: Object.fromEntries(Object.keys(PIECES).map(k => [k, tris(k)])), texelsPerPx: B.UV.S, islands: B.UV.islands.length, coplanar: B.coplanar.length,
  gui: Object.fromEntries(Object.keys(PIECES).map(k => [k, display(B, k).gui.translation]))};
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
