// Fuel Canopy Kit V1: the blocks a fuel station canopy is built from, one block thick, its ceiling 5 blocks above the
// forecourt (docs/models/fuel_canopy_kit_v1.md): a column (dark grey steel tube stacked one block a segment; the bottom
// segment has a concrete pedestal and the power port on its bottom face, set into a straight island curb when placed on
// one; the segment under the canopy has a head plate), a plain ceiling (one flat soffit, no seams), a ceiling light (a
// flush square LED luminaire, its lens unlit / lit) and a fascia (faded red, straight and outer corner). The canopy is
// wired inside: the column carries the power up, the ceiling blocks pass it on (FuelCanopyNetwork).
// Pure Mesh + LabPBR atlas exported as Forge OBJ block models (static, chunk-baked).
//   node tools/build-fuel-canopy-v1.mjs                -> writes source, OBJ / MTL / block + item models, blockstates, atlas
//   node tools/build-fuel-canopy-v1.mjs --check        -> verifies every output is up to date
//   node tools/build-fuel-canopy-v1.mjs --preview DIR  -> writes only OBJ + maps into DIR (offline review)
// Frame (px): one cell, x, z -8..8, y up; the fascia's face is -Z (facing north), the corner's second face -X (its
// counter-clockwise side); the island under an embedded column runs along x, as the curb's does. OBJ in block units:
// x = px / 16 + 0.5, y = px / 16, z = px / 16 + 0.5.
// Pieces that continue into the next block (soffit, roof, fascia, column shaft) are plain: no edge rims (hl 0, se = sm)
// and colours that do not change along the run, so the blocks join without seams. No printed decoration.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {Part, extrude, add, sub, mul, dot, cross, norm, newell, area2, unwrap, paint, png, readPng, zFightLevels} from './cube-slab-mesh-lib.mjs';
import {addPowerPort, portHole} from './afl-power-port.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function assert(c, m) { if (!c) throw new Error(m); }
const D2R = Math.PI / 180;

// ---------------- primitives ----------------
const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
const rect = (u0, v0, u1, v1) => [[u0, v0], [u1, v0], [u1, v1], [u0, v1]];
const shape = (L, holes = []) => ({outer: orient(L, true), holes: holes.map(h => orient(h, false))});
const zx = L => L.map(([x, z]) => [z, x]);
const planY = (part, L, y0, y1, c = 0, holes = []) => extrude(part, 'y', shape(zx(L), holes.map(zx)), y0, y1, c);
// a plain box is six rectangles (extrude triangulates its caps; smooth-lit models need rectangles, see FACE_INFO)
const box = (part, x0, z0, x1, z1, y0, y1, c = 0, holes = []) => c || holes.length ? planY(part, rect(x0, z0, x1, z1), y0, y1, c, holes) : quadBox(part, x0, z0, x1, z1, y0, y1);
function quadBox(part, x0, z0, x1, z1, y0, y1) {
  const at = k => [k & 1 ? x1 : x0, k & 4 ? y1 : y0, k & 2 ? z1 : z0];
  for (const [ks, n, tag] of [[[0, 1, 3, 2], [0, -1, 0], 'cap'], [[4, 5, 7, 6], [0, 1, 0], 'cap'], [[0, 1, 5, 4], [0, 0, -1], 'side'],
    [[2, 3, 7, 6], [0, 0, 1], 'side'], [[0, 2, 6, 4], [-1, 0, 0], 'side'], [[1, 3, 7, 5], [1, 0, 0], 'side']]) part.face(ks.map(k => part.vtx(at(k))), n, tag);
}
// a square with chamfered corners (the column's tube, its plates and pedestal), plan (x, z)
const cham = (r, c) => [[-r + c, -r], [r - c, -r], [r, -r + c], [r, r - c], [r - c, r], [-r + c, r], [-r, r - c], [-r, -r + c]];
function lathe(part, cx, cz, profile, seg, caps = [true, true]) {   // profile [[y, r]] bottom to top
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
  // continuous runs: a colour that depends only on the height (fascia) or on the plan position (column), no rims
  red:      {c: p => tone([142, 58, 50], 1 + 0.05 * (p[1] - 8) / 8), hl: 0, sm: 96, se: 96, f0: 22},          // faded red fascia panel (the dispenser header's red), lighter toward the sunlit top
  trimRun:  {c: [118, 121, 123], hl: 0, sm: 112, se: 112, f0: 24},                                          // painted aluminium drip edge and coping
  pan:      {c: [140, 142, 140], hl: 0, sm: 100, se: 100, f0: 24},                                          // painted metal soffit
  roof:     {c: [92, 94, 95], hl: 0, sm: 50, se: 50, f0: 20},                                               // roof membrane and framing
  column:   {c: p => tone([118, 122, 126], 1 + mott([p[0], 0, p[2]], 0.25, 0.05)), hl: 0, sm: 104, se: 104, f0: 24},   // dark grey painted steel tube
  // single-block parts
  plate:    {c: p => tone([104, 108, 112], 1 + mott(p, 0.3, 0.04)), hl: 9, sm: 112, se: 136, f0: 24},       // painted steel base / head plates
  bolt:     {c: [92, 96, 100], hl: 6, sm: 120, se: 132, f0: 255},
  concrete: {c: p => tone([146, 144, 138], 1 + mott(p, 0.22, 0.08) + 0.04 * (vn(p[0] * 0.9, p[1] * 0.9, p[2] * 0.9) - 0.5)), hl: 5, sm: 40, se: 52, f0: 20},
  nosing:   {c: [126, 130, 132], hl: 8, sm: 112, se: 140, f0: 255},
  trim:     {c: [120, 123, 126], hl: 6, sm: 110, se: 130, f0: 24},                                          // the luminaire's frame
  lens:     {c: [168, 170, 166], hl: 0, sm: 150, se: 150, f0: 16},                                          // opal diffuser, unlit
  // the lens while powered: warm white; its _s alpha marks it LabPBR emissive and its MTL's Ka makes Forge bake it full bright
  lensLit:  {c: [255, 246, 228], hl: 0, sm: 160, se: 160, f0: 16},
  // the standard power port (tools/afl-power-port.mjs)
  portPlate:  {c: [150, 154, 160], hl: 12, sm: 150, se: 168, f0: 255},
  portSocket: {c: [26, 27, 29], hl: 2, sm: 60, se: 70, f0: 20},
  portPin:    {c: [96, 100, 104], hl: 6, sm: 130, se: 140, f0: 255},
};

// ---------------- the pieces ----------------
export const COLUMN = {footing: 1.0, r: 4.5, ch: 0.9, ped: 6.5, pedCh: 1.2, pedH: 5.0, cap: [6.0, 1.0, 0.6], plate: [5.4, 1.0, 0.4], bolt: 4.45,
  head: {plate: [5.6, 1.0], collar: [4.9, 0.9], y: [14.6, 15.4, 15.96], bolt: 4.75}};
export const FASCIA = {face: -8, panel: -7.0, lip: -8.35, drip: [-0.4, 1.1], dripIn: -7.0, red: [1.1, 14.9], cope: [14.9, 16.15], copeIn: -5.9};   // pieces abut, none overlap
export const LIGHT = {hole: 5.7, lens: 5.07, trimY: -0.2, lensY: 0.3, pan: 0.6};
const CURB_L = [[0.13, 1.6], [-0.04, 1.6], [-0.04, 2.94], [-0.9, 2.94], [-0.9, 3.07], [0.13, 3.07]];   // tools/build-fuel-island-v1.mjs L_SECTION
const nut = (part, x, z, y, dir) => lathe(part, x, z, dir > 0 ? [[y, 0.34], [y + 0.32, 0.34], [y + 0.4, 0.2], [y + 0.52, 0.16]] : [[y - 0.52, 0.16], [y - 0.4, 0.2], [y - 0.32, 0.34], [y, 0.34]], 6, dir > 0 ? [false, true] : [true, false]);
function port(P, bone) {   // built facing +Z with the mating face at z 0, then turned to face down at y 0 (x, z centred)
  const pt = {plate: P('port_plate', bone, 'portPlate'), socket: P('port_socket', bone, 'portSocket'), pin: P('port_pin', bone, 'portPin')};
  addPowerPort(pt, 0, 0, -0.6, 0);
  for (const p of Object.values(pt)) p.v = p.v.map(([x, y, z]) => [x, -z, y]);
}
// the bottom segment: pedestal, base plate with four anchor nuts on the diagonals, the tube; y0 = the pedestal's foot
function columnBase(P, bone, y0) {
  const C = COLUMN, [hx0, hz0, hx1, hz1] = portHole(0, 0);
  if (y0 === 0) {   // on the ground: a full-cell footing pad (its bottom covers the cell below, where the cable comes up), the port set into it
    box(P('footing', bone, 'concrete'), -8, -8, 8, 8, 0, C.footing, 0, [rect(hx0, hz0, hx1, hz1)]);
    planY(P('ped', bone, 'concrete'), cham(C.ped, C.pedCh), C.footing, C.pedH, 0.15);
    port(P, bone);
  } else planY(P('ped', bone, 'concrete'), cham(C.ped, C.pedCh), y0, y0 + C.pedH, 0.15);
  const yc = y0 + C.pedH, yp = yc + C.cap[2], yt = yp + C.plate[2];
  planY(P('ped_cap', bone, 'concrete'), cham(C.cap[0], C.cap[1]), yc, yp, 0.12);
  planY(P('base_plate', bone, 'plate'), cham(C.plate[0], C.plate[1]), yp, yt, 0.08);
  const nuts = P('anchor_nuts', bone, 'bolt');
  for (const [sx, sz] of [[1, 1], [-1, 1], [-1, -1], [1, -1]]) nut(nuts, sx * C.bolt, sz * C.bolt, yt, 1);
  planY(P('tube', bone, 'column'), cham(C.r, C.ch), yt, 16, 0);
}
function curb(P, bone) {   // a straight island curb, the port set into its bottom layer under the cell centre
  const [hx0, hz0, hx1, hz1] = portHole(0, 0);
  box(P('curb_foot', bone, 'concrete'), -8, -8, 8, 8, 0, 0.6, 0, [rect(hx0, hz0, hx1, hz1)]);
  box(P('curb', bone, 'concrete'), -8, -8, 8, 8, 0.6, 3.0);
  const n = P('nosing', bone, 'nosing');
  for (const s of [-1, 1]) extrude(n, 'x', shape(CURB_L.map(([o, y]) => [s * (8 + o), y])), -7.99, 7.99, 0);
  port(P, bone);
}
function columnHead(P, bone) {   // under the canopy: a collar band and a head plate bolted to the ceiling structure
  const H = COLUMN.head;
  planY(P('head_collar', bone, 'column'), cham(...H.collar), H.y[0], H.y[1], 0.06);
  planY(P('head_plate', bone, 'plate'), cham(...H.plate), H.y[1], H.y[2], 0.08);
  const nuts = P('head_nuts', bone, 'bolt');
  for (const [sx, sz] of [[1, 1], [-1, 1], [-1, -1], [1, -1]]) nut(nuts, sx * H.bolt, sz * H.bolt, H.y[1], -1);
}
function ceiling(P, bone) {
  box(P('soffit', bone, 'pan'), -8, -8, 8, 8, 0, LIGHT.pan);
  box(P('roof', bone, 'roof'), -8, -8, 8, 8, LIGHT.pan, 16);
}
function light(P) {
  const L = LIGHT, h = L.hole, t = L.lens;
  // smooth-lit model: only axis-aligned rectangles (OBJ_AO), so the soffit and the frame are four strips round the hole
  const ring = (part, o, i, y0, y1) => { box(part, -o, -o, o, -i, y0, y1); box(part, -o, i, o, o, y0, y1); box(part, -o, -i, -i, i, y0, y1); box(part, i, -i, o, i, y0, y1); };
  ring(P('soffit', 'light', 'pan'), 8, h, 0, L.pan);
  ring(P('frame', 'light', 'trim'), h, t - 0.02, L.trimY, L.pan);
  box(P('roof', 'light', 'roof'), -8, -8, 8, 8, L.pan, 16);
  box(P('lens', 'light_lens', 'lens'), -t, -t, t, t, L.lensY, L.pan);
  box(P('lens_lit', 'light_lens_lit', 'lensLit'), -t, -t, t, t, L.lensY, L.pan);
}
function fascia(P, bone, corner) {   // the face on -Z (and on -X for the corner); drip edge below, coping above, soffit behind
  // smooth-lit model: only axis-aligned rectangles (OBJ_AO); the corner's L pieces are two boxes each
  const F = FASCIA, run = (part, o, i, y0, y1) => { box(part, corner ? o : -8, o, 8, i, y0, y1); if (corner) box(part, o, i, i, 8, y0, y1); };
  run(P('panel', bone, 'red'), F.face, F.panel, F.red[0], F.red[1]);
  run(P('drip', bone, 'trimRun'), F.lip, F.dripIn, F.drip[0], F.drip[1]);
  run(P('coping', bone, 'trimRun'), F.lip, F.copeIn, F.cope[0], F.cope[1]);
  const x0 = v => corner ? v : -8;
  box(P('soffit', bone, 'pan'), x0(F.dripIn), F.dripIn, 8, 8, 0, LIGHT.pan);
  box(P('roof', bone, 'roof'), x0(F.panel), F.panel, 8, 8, LIGHT.pan, F.red[1]);
  box(P('roof_top', bone, 'roof'), x0(F.copeIn), F.copeIn, 8, 8, F.red[1], 16);
}
function build() {
  const PARTS = [], P = (name, bone, mat) => { const p = new Part(`${bone}_${name}`, bone, mat); PARTS.push(p); return p; };
  columnBase(P, 'column_base', 0);
  curb(P, 'column_base_curb'); columnBase(P, 'column_base_curb', 3.0);
  planY(P('tube', 'column_shaft', 'column'), cham(COLUMN.r, COLUMN.ch), 0, 16, 0);
  columnHead(P, 'column_head');
  ceiling(P, 'ceiling');
  light(P);
  fascia(P, 'fascia', false); fascia(P, 'fascia_corner', true);
  return PARTS;
}

// ---------------- bake: UV, LabPBR maps ----------------
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
// coplanar overlaps only matter inside one model
const MODEL_GROUPS = [['column_base'], ['column_base_curb'], ['column_shaft', 'column_head'], ['ceiling'], ['light', 'light_lens'], ['light', 'light_lens_lit'], ['fascia'], ['fascia_corner']];
function bake() {
  const PARTS = build();
  // canopy pieces: faces inside the solid at the soffit's back (y = LIGHT.pan), the tops there and the roofs' backs (only
  // these: 2026-10-04 the first version dropped every top at that height, the column's pedestal foot among them, and left a
  // see-through gap under the pedestal's bevel)
  const CANOPY = new Set(['ceiling', 'light', 'light_lens', 'light_lens_lit', 'fascia', 'fascia_corner']);
  for (const p of PARTS) p.f = p.f.filter(f => {
    const P3 = f.ids.map(i => p.v[i]); if (!P3.every(q => Math.abs(q[1] - LIGHT.pan) < 1e-9)) return true;
    if (!CANOPY.has(p.bone)) return true;
    const ny = newell(P3)[1]; return !(ny > 0 || (ny < 0 && p.name.endsWith('_roof')));
  });
  for (let i = PARTS.length - 1; i >= 0; i--) if (!PARTS[i].f.length) PARTS.splice(i, 1);
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
  const atlas = 2048, UV = unwrap(PARTS, {atlas, pad: 2, startS: 24, stepS: 0.25});
  for (const p of PARTS) assert(p.f.every(f => UV.faceUV.get(p)?.has(f)), `unmapped face in ${p.name}`);
  const painted = paint({PARTS, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas, pad: 2, MATS, ZONED: new Set(), groupInfo: new Map(),
    sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [100, 102, 104, 255], s: [60, 20, 0, 255], n: [128, 128, 255, 255]}});
  const maps = painted.PNG.map((buf, i) => {
    if (i !== 1) return buf;
    const img = readPng(buf), px = Buffer.alloc(img.w * img.h * 4);
    for (let k = 0; k < img.w * img.h; k++) for (let c = 0; c < 4; c++) px[k * 4 + c] = img.bpp === 4 ? img.px[k * 4 + c] : (c < 3 ? img.px[k * 3 + c] : 255);
    for (const is of UV.islands.filter(is => is.part.mat === 'lensLit'))   // LabPBR emission: _s alpha 254 = full
      for (let y = is.py - 2; y < is.py + is.H + 2; y++) for (let x = is.px - 2; x < is.px + is.W + 2; x++) px[(y * img.w + x) * 4 + 3] = 254;
    return png(px, img.w, img.h);
  });
  const coplanar = MODEL_GROUPS.flatMap(g => zFightLevels(PARTS.filter(p => g.includes(p.bone)), new Map()).unresolved);
  return {id: 'fuel_canopy', PARTS, atlas, UV, maps, coplanar};
}

const uuidOf = (ns, s) => { const h = createHash('sha256').update(`afl-fuel-canopy-v1:${ns}:${s}`).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
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
const SMOOTH = Math.cos(36 * D2R);
function cornerNormals(V, F) {
  const fn = F.map(f => newell(f.ids.map(i => V[i]))), byV = new Map();
  F.forEach((f, k) => f.ids.forEach(i => (byV.get(i) || byV.set(i, []).get(i)).push(k)));
  return F.map((f, k) => { const n0 = norm(fn[k]); return f.ids.map(i => norm(byV.get(i).reduce((a, j) => dot(norm(fn[j]), n0) >= SMOOTH ? add(a, fn[j]) : a, [0, 0, 0]))); });
}
const toCell = q => [q[0] / 16 + 0.5, q[1] / 16, q[2] / 16 + 0.5];
// a blockstate y rotation, baked (clockwise seen from above: north -> east), px about the cell centre
const rotY = (q, deg) => { let [x, y, z] = q; for (let k = 0; k < deg / 90; k++) [x, z] = [-z, x]; return [x, y, z]; };
// Smooth lighting (vanilla ModelBlockRenderer.AmbientOcclusionFace) gives the four corner lights to a quad's vertices by
// index, in FaceInfo's order, and FaceBakery bakes every vanilla quad in that order; Forge's OBJ loader keeps the file's
// order and a blockstate rotation does not reorder. So a smooth-lit OBJ model must be axis-aligned rectangles written in
// FaceInfo order, its rotations baked into separate models (2026-10-04: in the file's own order every soffit's light ran
// the wrong way and the canopy showed a checkerboard). Per direction: the in-plane axes and, per vertex, min (0) / max (1).
const FACE_INFO = {'-y': [[0, 2], [[0, 1], [0, 0], [1, 0], [1, 1]]], '+y': [[0, 2], [[0, 0], [0, 1], [1, 1], [1, 0]]],
  '-z': [[0, 1], [[1, 1], [1, 0], [0, 0], [0, 1]]], '+z': [[0, 1], [[0, 1], [0, 0], [1, 0], [1, 1]]],
  '-x': [[1, 2], [[1, 0], [0, 0], [0, 1], [1, 1]]], '+x': [[1, 2], [[1, 1], [0, 1], [0, 0], [1, 0]]]};
function faceInfoOrder(V, ids, where) {
  const n = norm(newell(ids.map(i => V[i]))), ax = [0, 1, 2].find(k => Math.abs(n[k]) > 0.999);
  assert(ids.length === 4 && ax !== undefined, `smooth-lit model needs axis-aligned rectangles: ${where}`);
  const [axes, corners] = FACE_INFO[(n[ax] > 0 ? '+' : '-') + 'xyz'[ax]];
  const lo = axes.map(a => Math.min(...ids.map(i => V[i][a]))), hi = axes.map(a => Math.max(...ids.map(i => V[i][a])));
  const order = corners.map(c => ids.findIndex(i => axes.every((a, k) => Math.abs(V[i][a] - (c[k] ? hi[k] : lo[k])) < 1e-6)));
  assert(order.every(j => j >= 0) && new Set(order).size === 4, `not a rectangle: ${where}`);
  assert(order.every((j, k) => order[(k + 1) % 4] === (j + 1) % 4), `winding differs from FaceInfo: ${where}`);
  return order;
}
function objOf(b, title, file, bones, {rot = 0, ao = false} = {}) {
  const out = [`# AFL ${title}, generated by tools/build-fuel-canopy-v1.mjs`, `mtllib ${file.split('/').pop()}.mtl`];
  let vBase = 1, tBase = 1, nBase = 1;
  for (const p of b.PARTS) {
    if (!bones.includes(p.bone)) continue;
    const V = p.v.map(q => toCell(rotY(q, rot))), F = p.f;
    out.push(`o ${p.name}`, `usemtl ${b.id}`);
    for (const q of V) out.push(`v ${f6(q[0])} ${f6(q[1])} ${f6(q[2])}`);
    const uvs = b.UV.faceUV.get(p), vt = [], vn = [], fl = [], cn = cornerNormals(V, F), nIndex = new Map();
    F.forEach((f, k) => {
      const uv = uvs.get(f), order = ao ? faceInfoOrder(V, f.ids, p.name) : f.ids.map((id, j) => j);
      fl.push('f ' + order.map(j => [f.ids[j], j]).map(([id, j]) => {
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
const mtlOf = (b, title, glow) => `# AFL ${title}\nnewmtl ${b.id}\nKd 1 1 1\n${glow ? 'Ka 1 1 1\n' : ''}map_Kd apocalypse_firstlight:block/${b.id}\n`;
// smooth lighting (ambient occlusion) on the canopy's flat runs: with flat lighting every block's soffit took one light
// value and the canopy showed a checkerboard of steps (2026-10-04, in game); the column and the lenses stay flat
const objModel = (file, ao = false) => ({loader: 'forge:obj', model: `apocalypse_firstlight:models/block/${file}.obj`, automatic_culling: false,
  flip_v: true, shade_quads: true, ambientocclusion: ao, textures: {particle: 'apocalypse_firstlight:block/fuel_canopy'}});
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
outputs.push([path.join(bbDir, 'fuel_canopy_v1.bbmodel'), JSON.stringify(sourceOf(B))],
  ...['', '_s', '_n'].flatMap((k, i) => [[path.join(bbDir, `textures/fuel_canopy_v1${k}.png`), B.maps[i]], [path.join(assets, `textures/block/fuel_canopy${k}.png`), B.maps[i]]]));
const model = (file, title, bones, glow = false, ao = false, rot = 0) => {
  const obj = objOf(B, title, file, bones, {rot, ao});
  outputs.push([path.join(assets, `models/block/${file}.obj`), obj], [path.join(assets, `models/block/${file}.mtl`), mtlOf(B, title, glow)],
    [path.join(assets, `models/block/${file}.json`), json(objModel(file, ao))]);
  objs.push([file, obj]);
};
const T = 'Fuel Canopy V1';
model('fuel_canopy/column_base', T + ' column, bottom segment', ['column_base']);
model('fuel_canopy/column_base_curb', T + ' column, bottom segment set into an island curb', ['column_base_curb']);
model('fuel_canopy/column_shaft', T + ' column segment', ['column_shaft']);
model('fuel_canopy/column_head', T + ' column head (under the canopy)', ['column_head']);
model('fuel_canopy/ceiling', T + ' ceiling', ['ceiling'], false, true);
model('fuel_canopy/light', T + ' ceiling light', ['light'], false, true);
model('fuel_canopy/light_lens', T + ' ceiling light lens (unlit)', ['light_lens']);
model('fuel_canopy/light_lens_lit', T + ' ceiling light lens (lit)', ['light_lens_lit'], true);
model('fuel_canopy/light_item', T + ' ceiling light (item)', ['light', 'light_lens']);
for (const [F, y] of Object.entries(ROT)) {   // the fascia's rotations baked (see FACE_INFO); the corner's by its own model
  model(`fuel_canopy/fascia_${F}`, `${T} fascia, facing ${F}`, ['fascia'], false, true, y);
  model(`fuel_canopy/fascia_corner_${F}`, `${T} fascia, outer corner turned ${y} degrees`, ['fascia_corner'], false, true, y);
}
const ITEMS = {fuel_canopy_column: ['fuel_canopy/column_base', ['column_base']], fuel_canopy_ceiling: ['fuel_canopy/ceiling', ['ceiling']],
  fuel_canopy_light: ['fuel_canopy/light_item', ['light', 'light_lens']], fuel_canopy_fascia: ['fuel_canopy/fascia_north', ['fascia']]};
for (const [id, [file, bones]] of Object.entries(ITEMS))
  outputs.push([path.join(assets, `models/item/${id}.json`), json({parent: `apocalypse_firstlight:block/${file}`, gui_light: 'side', display: display(B, bones)})]);
// blockstates (FuelCanopyColumnBlock: segment, island, top, facing; FuelCanopyLightBlock: lit; FuelCanopyFasciaBlock: facing, shape)
{
  const parts = [];
  for (const [F, y] of Object.entries(ROT)) {
    parts.push({when: {facing: F, segment: 'base', island: 'false'}, apply: ref('fuel_canopy/column_base', y)});
    parts.push({when: {facing: F, segment: 'base', island: 'true'}, apply: ref('fuel_canopy/column_base_curb', y)});
  }
  parts.push({when: {segment: 'shaft'}, apply: ref('fuel_canopy/column_shaft', 0)}, {when: {top: 'true'}, apply: ref('fuel_canopy/column_head', 0)});
  outputs.push([path.join(assets, 'blockstates/fuel_canopy_column.json'), json({multipart: parts})]);
  outputs.push([path.join(assets, 'blockstates/fuel_canopy_ceiling.json'), json({variants: {'': ref('fuel_canopy/ceiling', 0)}})]);
  outputs.push([path.join(assets, 'blockstates/fuel_canopy_light.json'), json({multipart: [{apply: ref('fuel_canopy/light', 0)},
    {when: {lit: 'false'}, apply: ref('fuel_canopy/light_lens', 0)}, {when: {lit: 'true'}, apply: ref('fuel_canopy/light_lens_lit', 0)}]})]);
  const variants = {};
  for (const [F, y] of Object.entries(ROT)) {
    const turned = deg => Object.keys(ROT).find(k => ROT[k] === deg % 360);
    variants[`facing=${F},shape=straight`] = ref(`fuel_canopy/fascia_${F}`, 0);
    variants[`facing=${F},shape=outer_left`] = ref(`fuel_canopy/fascia_corner_${turned(y)}`, 0);
    variants[`facing=${F},shape=outer_right`] = ref(`fuel_canopy/fascia_corner_${turned(y + 90)}`, 0);
  }
  outputs.push([path.join(assets, 'blockstates/fuel_canopy_fascia.json'), json({variants})]);
}
const tris = bones => B.PARTS.filter(p => bones.includes(p.bone)).reduce((s, p) => s + p.f.reduce((t, f) => t + f.ids.length - 2, 0), 0);
export const stats = {triangles: Object.fromEntries([...new Set(B.PARTS.map(p => p.bone))].map(k => [k, tris([k])])), texelsPerPx: B.UV.S,
  islands: B.UV.islands.length, coplanar: B.coplanar.length, gui: Object.fromEntries(Object.entries(ITEMS).map(([id, [, bones]]) => [id, display(B, bones).gui.translation]))};
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
