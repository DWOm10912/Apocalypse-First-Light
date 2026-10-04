// Fluid Pipe V2 (docs/models/fluid_pipe_v2.md): the V1 idea kept (a square steel pipe whose sides are glass, so the fluid
// inside shows), remade as Pure Mesh: an 8 px square pipe of four dark grey steel corner rails with tempered glass panes
// between them; a steel band every third block of a straight run, a wall clamp instead where a solid face is beside it;
// fittings (bends, tees, crosses, a lone pipe) are a steel cube frame with glass in its closed faces and a bolted flange
// on each opening; a blind flange closes a line end; a bolted port flange meets a tank / machine port at the boundary.
// (2026-10-04: a round version, a glass tube with tie rods, was tried and reverted: the user kept this one.)
// The block model is assembled per block from pieces (client/FluidPipeBakedModel): steel in the solid layer, glass
// (`<piece>_glass`) in the translucent layer. Pieces are built once along +Z and turned to each direction (UVs follow).
// Pure Mesh + LabPBR atlas exported as Forge OBJ.
//   node tools/build-fluid-pipe-v2.mjs                -> writes source, OBJ / MTL / models, blockstate, atlas
//   node tools/build-fluid-pipe-v2.mjs --check        -> verifies every output is up to date
//   node tools/build-fluid-pipe-v2.mjs --preview DIR  -> writes only OBJ + maps into DIR (offline review)
// Frame (px): the cell centre is the origin, x, y, z -8..8; a canonical piece runs toward +Z (south). OBJ in block units:
// x = px / 16 + 0.5, y = px / 16 + 0.5, z = px / 16 + 0.5. Runs continue into the next block: their rails and glass are
// plain (no edge rims, colours constant along the run), so the blocks join without seams. No printed decoration.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {Part, AX, extrude, add, sub, dot, cross, norm, newell, area2, unwrap, paint, png, readPng, zFightLevels} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function assert(c, m) { if (!c) throw new Error(m); }
const D2R = Math.PI / 180;

// ---------------- primitives (canonical axis z: u = x, v = y, a = z; AX.z) ----------------
const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
const rect = (u0, v0, u1, v1) => [[u0, v0], [u1, v0], [u1, v1], [u0, v1]];
const shape = (L, holes = []) => ({outer: orient(L, true), holes: holes.map(h => orient(h, false))});
const alongZ = (part, L, z0, z1, c = 0, holes = []) => extrude(part, 'z', shape(L, holes), z0, z1, c);
const sq = r => rect(-r, -r, r, r);
const hex = (cx, cy, r) => Array.from({length: 6}, (_, i) => { const a = (30 + 60 * i) * D2R; return [cx + r * Math.cos(a), cy + r * Math.sin(a)]; });
// a glass pane: only its two broad faces (normal along axis k). Its edges always meet a rail, a frame bar or the next
// block's pane, and seen through the glass they would draw a line at every joint
function pane(part, a, b, k) {
  const lo = a.map((v, i) => Math.min(v, b[i])), hi = a.map((v, i) => Math.max(v, b[i])), [i, j] = [0, 1, 2].filter(x => x !== k);
  for (const [side, n] of [[lo[k], -1], [hi[k], 1]]) {
    const at = (u, v) => { const q = [0, 0, 0]; q[k] = side; q[i] = u; q[j] = v; return part.vtx(q); }, nn = [0, 0, 0]; nn[k] = n;
    part.face([at(lo[i], lo[j]), at(hi[i], lo[j]), at(hi[i], hi[j]), at(lo[i], hi[j])], nn, 'side');
  }
}
function quadBox(part, a, b) {   // six rectangles, corners a / b (px)
  const lo = a.map((v, i) => Math.min(v, b[i])), hi = a.map((v, i) => Math.max(v, b[i])), at = k => [k & 1 ? hi[0] : lo[0], k & 4 ? hi[1] : lo[1], k & 2 ? hi[2] : lo[2]];
  for (const [ks, n, tag] of [[[0, 1, 3, 2], [0, -1, 0], 'cap'], [[4, 5, 7, 6], [0, 1, 0], 'cap'], [[0, 1, 5, 4], [0, 0, -1], 'side'],
    [[2, 3, 7, 6], [0, 0, 1], 'side'], [[0, 2, 6, 4], [-1, 0, 0], 'side'], [[1, 3, 7, 5], [1, 0, 0], 'side']]) part.face(ks.map(k => part.vtx(at(k))), n, tag);
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
  // continuous runs: plain, no rims (they join the next block's)
  rail:   {c: [46, 49, 54], hl: 0, sm: 108, se: 108, f0: 24},                                              // near-black painted steel corner rails (2026-10-04: [72, 76, 82] read dull grey without shaders)
  glass:  {c: [150, 186, 204], hl: 0, sm: 235, se: 235, f0: 10},                                           // the cooler's glass recipe (tools/build-beverage-cooler-v2.mjs), alpha GLASS_ALPHA
  // single-block parts (coated steel: F0 24-30, see docs/models/power_cable_v2.md "钢件 F0")
  frame:  {c: p => tone([44, 47, 52], 1 + mott(p, 0.35, 0.04)), hl: 8, sm: 112, se: 136, f0: 24},         // fitting frame (was [70, 74, 80])
  flange: {c: p => tone([62, 66, 72], 1 + mott(p, 0.35, 0.04)), hl: 9, sm: 116, se: 140, f0: 26},        // flanges, blind flanges, bands (was [90, 94, 100])
  clamp:  {c: p => tone([138, 141, 146], 1 + mott(p, 0.3, 0.03)), hl: 10, sm: 130, se: 150, f0: 30},      // galvanised wall clamp
  bolt:   {c: [124, 128, 133], hl: 7, sm: 124, se: 140, f0: 30},
};
// the cooler's: > 26 (0.1) survives the shader packs' translucent alpha test; 2026-10-04 the first pipe glass (a light [196, 214, 212]
// at 84) laid a grey veil over the fluid without shaders
const GLASS_ALPHA = 56;

// ---------------- dimensions (px) ----------------
export const PIPE = {h: 4.0, rail: 1.3, cham: 0.3, glass: [3.6, 3.85], fluid: 3.5};   // glass: the pane's inner / outer distance from the axis
export const FIT = {h: 4.6, bar: 1.2, glass: [4.2, 4.45]};
export const FLANGE = {r: 5.4, t: 0.8, hole: 4.0, bolt: 4.65, nut: 0.42, nutLen: 0.35};
export const BAND = {r: 4.6, half: 0.9};
export const PORT_AT = 7.2;   // a port flange from here to the boundary
export const END = {flange: [-1.2, -0.4], blind: [-2.0, -1.2]};

// rails and glass of a run along z from z0 to z1
function run(P, z0, z1) {
  const H = PIPE.h, R = PIPE.rail, c = PIPE.cham, rails = P('rails', 'rail'), glass = P('panes', 'glass');
  for (const sx of [-1, 1]) for (const sy of [-1, 1]) {
    const i = H - R;   // a square bar in the corner, its outer edge chamfered
    alongZ(rails, [[sx * i, sy * i], [sx * H, sy * i], [sx * H, sy * (H - c)], [sx * (H - c), sy * H], [sx * i, sy * H]], z0, z1, 0);
  }
  const [g0, g1] = PIPE.glass, w = H - R;
  for (const s of [-1, 1]) { pane(glass, [s * g0, -w, z0], [s * g1, w, z1], 0); pane(glass, [-w, s * g0, z0], [w, s * g1, z1], 1); }
}
function nuts(part, z, dir) {   // four hex nuts on a flange face at z, toward dir (+1 / -1)
  const F = FLANGE;
  for (const sx of [-1, 1]) for (const sy of [-1, 1]) alongZ(part, hex(sx * F.bolt, sy * F.bolt, F.nut), dir > 0 ? z : z - F.nutLen, dir > 0 ? z + F.nutLen : z, 0);
}
function flange(P, z0, z1, name = 'flange', nutsAt = z1, nutDir = 1) {   // a square bolted ring round the pipe
  alongZ(P(name, 'flange'), sq(FLANGE.r), z0, z1, 0.1, [sq(FLANGE.hole)]);
  nuts(P(name + '_nuts', 'bolt'), nutsAt, nutDir);
}
function band(P) {
  alongZ(P('band', 'flange'), sq(BAND.r), -BAND.half, BAND.half, 0.1, [sq(PIPE.h)]);
  alongZ(P('band_bolt', 'bolt'), hex(0, BAND.r + 0.3, 0.38), -0.3, 0.3, 0);   // the band's clamping bolt on its top edge
  quadBox(P('band_lug', 'flange'), [-0.75, BAND.r, -0.45], [0.75, BAND.r + 0.3, 0.45]);
}
function clamp(P) {   // toward -Y (the floor): a strap ring, a leg down to the boundary, a base plate with two screws
  const r = BAND.r, t = 0.7;
  alongZ(P('strap', 'clamp'), sq(r), -t, t, 0.08, [sq(PIPE.h)]);
  quadBox(P('leg', 'clamp'), [-1.1, -7.5, -t], [1.1, -r, t]);
  alongZ(P('foot', 'clamp'), rect(-2.8, -8, 2.8, -7.5), -1.7, 1.7, 0);
  const screws = P('screws', 'bolt');
  for (const sx of [-1, 1]) extrude(screws, 'y', shape(hex(0, sx * 2.0, 0.42)), -7.5, -7.22, 0);   // AX.y: (u, v) = (z, x)
}
function fittingFrame(P) {   // the cube's twelve edges
  const F = FIT.h, b = FIT.bar, i = F - b, bars = P('frame', 'frame');
  for (const s1 of [-1, 1]) for (const s2 of [-1, 1]) {
    quadBox(bars, [-F, s1 * i, s2 * i], [F, s1 * F, s2 * F]);       // along x, full length
    quadBox(bars, [s1 * i, -i, s2 * i], [s1 * F, i, s2 * F]);        // along y, between
    quadBox(bars, [s1 * i, s2 * i, -i], [s1 * F, s2 * F, i]);        // along z, between
  }
}
function fittingGlass(P) {   // the pane in the +Z face
  const [g0, g1] = FIT.glass, w = FIT.h - FIT.bar;
  pane(P('pane', 'glass'), [-w, -w, g0], [w, w, g1], 2);
}
const P_END = PORT_AT;
export const PIECES = {
  half_pipe: P => run(P, 0, 8),
  half_port: P => { run(P, 0, P_END); flange(P, P_END, 8, 'port', P_END, -1); },
  arm_pipe: P => { flange(P, FIT.h, FIT.h + FLANGE.t, 'flange', FIT.h + FLANGE.t, 1); run(P, FIT.h + FLANGE.t, 8); },
  arm_port: P => { flange(P, FIT.h, FIT.h + FLANGE.t, 'flange', FIT.h + FLANGE.t, 1); run(P, FIT.h + FLANGE.t, P_END); flange(P, P_END, 8, 'port', P_END, -1); },
  end_pipe: P => { alongZ(P('blind', 'flange'), sq(FLANGE.r), ...END.blind, 0.1); flange(P, ...END.flange, 'flange', END.blind[0], -1); run(P, END.flange[1], 8); },
  end_port: P => { alongZ(P('blind', 'flange'), sq(FLANGE.r), ...END.blind, 0.1); flange(P, ...END.flange, 'flange', END.blind[0], -1); run(P, END.flange[1], P_END); flange(P, P_END, 8, 'port', P_END, -1); },
  band: P => band(P),
  clamp: P => clamp(P),
  box: P => fittingFrame(P),
  box_glass: P => fittingGlass(P),
  item: P => {   // one block of pipe, closed by blind flanges both ends, a band in the middle
    run(P, -6.4, 6.4); band(P);
    for (const s of [-1, 1]) { alongZ(P('blind' + s, 'flange'), sq(FLANGE.r), s > 0 ? 7.2 : -8, s > 0 ? 8 : -7.2, 0.1); flange(P, s > 0 ? 6.4 : -7.2, s > 0 ? 7.2 : -6.4, 'flange' + s, s > 0 ? 8 : -8, s); }
  },
};
// coplanar overlaps only matter where pieces share a block
const GROUPS = [['half_pipe', 'band'], ['half_pipe', 'clamp'], ['half_port'], ['box', 'box_glass', 'arm_pipe'], ['box', 'arm_port'], ['end_pipe'], ['end_port'], ['item']];

// ---------------- rotations: canonical +Z to every direction ----------------
const DIRS = {down: [0, -1, 0], up: [0, 1, 0], north: [0, 0, -1], south: [0, 0, 1], west: [-1, 0, 0], east: [1, 0, 0]};
const AXES = {x: [1, 0, 0], y: [0, 1, 0], z: [0, 0, 1]};
const ROTS = (() => {   // the 24 proper rotations as 3x3 integer matrices (rows)
  const out = [], perms = [[0, 1, 2], [0, 2, 1], [1, 0, 2], [1, 2, 0], [2, 0, 1], [2, 1, 0]];
  for (const p of perms) for (const s of [[1, 1, 1], [1, 1, -1], [1, -1, 1], [1, -1, -1], [-1, 1, 1], [-1, 1, -1], [-1, -1, 1], [-1, -1, -1]]) {
    const m = [0, 1, 2].map(r => [0, 1, 2].map(c => c === p[r] ? s[r] : 0));
    const det = m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1]) - m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0]) + m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0]);
    if (det === 1) out.push(m);
  }
  return out;
})();
const apply = (m, q) => m.map(r => r[0] * q[0] + r[1] * q[1] + r[2] * q[2]);
const same = (a, b) => a.every((v, i) => v === b[i]);
// a rotation taking +Z to z (and -Y to down when given); else one that keeps +Y up where it can, so a band's bolt is on top
const rotTo = (z, down = null) => down ? ROTS.find(m => same(apply(m, [0, 0, 1]), z) && same(apply(m, [0, -1, 0]), down))
  : ROTS.find(m => same(apply(m, [0, 0, 1]), z) && same(apply(m, [0, 1, 0]), [0, 1, 0])) ?? ROTS.find(m => same(apply(m, [0, 0, 1]), z) && same(apply(m, [0, 1, 0]), [0, 0, -1]));
// the models: name -> [piece, rotation]
const VARIANTS = [];
for (const [d, v] of Object.entries(DIRS)) for (const k of ['half_pipe', 'half_port', 'arm_pipe', 'arm_port', 'end_pipe', 'end_port', 'box_glass'])
  VARIANTS.push([k === 'box_glass' ? `box_glass_${d}` : `${k.replace('_pipe', '').replace('_port', '')}_${k.endsWith('port') ? 'port' : 'pipe'}_${d}`, k, rotTo(v)]);
for (const [a, v] of Object.entries(AXES)) {
  VARIANTS.push([`band_${a}`, 'band', rotTo(v)]);
  for (const [d, s] of Object.entries(DIRS)) if (s[{x: 0, y: 1, z: 2}[a]] === 0) VARIANTS.push([`clamp_${a}_${d}`, 'clamp', rotTo(v, s)]);
}
VARIANTS.push(['box', 'box', ROTS.find(m => same(m[0], [1, 0, 0]) && same(m[1], [0, 1, 0]))], ['item', 'item', ROTS.find(m => same(m[0], [1, 0, 0]) && same(m[1], [0, 1, 0]))]);
for (const [name, , m] of VARIANTS) assert(m, 'no rotation for ' + name);

// ---------------- bake: UV, LabPBR maps ----------------
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
function bake() {
  const PARTS = [];
  for (const [piece, build] of Object.entries(PIECES)) build((name, mat) => { const p = new Part(`${piece}_${name}`, piece, mat); PARTS.push(p); return p; });
  const live = PARTS.filter(p => p.f.length);
  for (const p of live) {   // Forge draws a quad as 0-1-2 / 2-3-0: split anything that is not a triangle or a strictly convex quad
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
  const atlas = 2048, UV = unwrap(live, {atlas, pad: 2, startS: 32, stepS: 0.25});
  for (const p of live) assert(p.f.every(f => UV.faceUV.get(p)?.has(f)), `unmapped face in ${p.name}`);
  const painted = paint({PARTS: live, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas, pad: 2, MATS, ZONED: new Set(), groupInfo: new Map(),
    sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [72, 76, 82, 255], s: [108, 24, 0, 255], n: [128, 128, 255, 255]}});
  const maps = painted.PNG.map((buf, i) => {   // the glass's texels: translucent (base colour alpha; the pieces' glass is drawn in the translucent layer)
    if (i !== 0) return buf;
    const img = readPng(buf), px = Buffer.alloc(img.w * img.h * 4);
    for (let k = 0; k < img.w * img.h; k++) for (let c = 0; c < 4; c++) px[k * 4 + c] = img.bpp === 4 ? img.px[k * 4 + c] : (c < 3 ? img.px[k * 3 + c] : 255);
    for (const is of UV.islands.filter(is => is.part.mat === 'glass'))
      for (let y = is.py - 2; y < is.py + is.H + 2; y++) for (let x = is.px - 2; x < is.px + is.W + 2; x++) px[(y * img.w + x) * 4 + 3] = GLASS_ALPHA;
    return png(px, img.w, img.h);
  });
  const coplanar = GROUPS.flatMap(g => zFightLevels(live.filter(p => g.includes(p.bone)), new Map()).unresolved);
  return {id: 'fluid_pipe', PARTS: live, atlas, UV, maps, coplanar};
}

const uuidOf = (ns, s) => { const h = createHash('sha256').update(`afl-fluid-pipe-v2:${ns}:${s}`).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r12 = v => +v.toFixed(12) || 0;
function sourceOf(b) {   // editable Free Model source (canonical pieces, px, frame as the header): one group per piece
  const uuid = s => uuidOf(b.id, s), name = b.id + '_v2', bones = [...new Set(b.PARTS.map(p => p.bone))];
  const elements = b.PARTS.map(p => {
    const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {}, uvs = b.UV.faceUV.get(p);
    p.v.forEach((q, i) => { vertices[key(i)] = [q[0] + 8, q[1] + 8, q[2] + 8].map(r12); });
    p.f.forEach((f, fi) => { const uv = uvs.get(f); faces['f' + key(fi)] = {uv: Object.fromEntries(f.ids.map((id, j) => [key(id), uv[j].map(r12)])), vertices: f.ids.map(key), texture: 0}; });
    return {name: p.name, color: 0, origin: [8, 8, 8], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: p.bone === 'item', locked: false,
      render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: uuid('mesh:' + p.name)};
  });
  return {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name, model_identifier: '', visible_box: [1, 1, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: b.atlas, height: b.atlas},
    elements, groups: bones.map(bone => ({name: bone, uuid: uuid('group:' + bone), export: true, locked: false, scope: 0,
      selected: false, visibility: bone === 'item', _static: {properties: {}, temp_data: {}}, origin: [8, 8, 8], rotation: [0, 0, 0],
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
const toCell = q => q.map(v => v / 16 + 0.5);
function objOf(b, title, file, piece, m, glass) {   // glass: true / false / null (both)
  const out = [`# AFL ${title}, generated by tools/build-fluid-pipe-v2.mjs`, 'mtllib fluid_pipe.mtl'];
  let vBase = 1, tBase = 1, nBase = 1, faces = 0;
  for (const p of b.PARTS) {
    if (p.bone !== piece || (glass !== null && (p.mat === 'glass') !== glass)) continue;
    const V = p.v.map(q => toCell(apply(m, q))), F = p.f;
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
    vBase += p.v.length; tBase += vt.length; nBase += vn.length; faces += F.length;
  }
  return faces ? out.join('\n') + '\n' : null;
}
const objModel = (file, translucent) => ({loader: 'forge:obj', model: `apocalypse_firstlight:models/block/${file}.obj`, automatic_culling: false,
  flip_v: true, shade_quads: true, ambientocclusion: false, ...(translucent ? {render_type: 'minecraft:translucent'} : {}),
  textures: {particle: 'apocalypse_firstlight:block/fluid_pipe'}});
const r3 = v => +v.toFixed(3) || 0;
const S3 = v => [v, v, v];

// ---------------- write ----------------
const B = bake();
const bbDir = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const json = v => JSON.stringify(v, null, 2) + '\n';
const outputs = [], objs = [];
outputs.push([path.join(bbDir, 'fluid_pipe_v2.bbmodel'), JSON.stringify(sourceOf(B))],
  ...['', '_s', '_n'].flatMap((k, i) => [[path.join(bbDir, `textures/fluid_pipe_v2${k}.png`), B.maps[i]], [path.join(assets, `textures/block/fluid_pipe${k}.png`), B.maps[i]]]),
  [path.join(assets, 'models/block/fluid_pipe/fluid_pipe.mtl'), `# AFL Fluid Pipe V2\nnewmtl ${B.id}\nKd 1 1 1\nmap_Kd apocalypse_firstlight:block/fluid_pipe\n`]);
export const MODELS = [];
for (const [name, piece, m] of VARIANTS) for (const glass of [false, true]) {
  if (piece === 'item' && glass) continue;
  const file = `fluid_pipe/${name}${glass ? '_glass' : ''}`;
  const obj = objOf(B, `Fluid Pipe V2 ${name}${glass ? ' (glass)' : ''}`, file, piece, m, piece === 'item' ? null : glass);
  if (!obj) continue;
  MODELS.push(file.slice('fluid_pipe/'.length));
  outputs.push([path.join(assets, `models/block/${file}.obj`), obj], [path.join(assets, `models/block/${file}.json`), json(objModel(file, glass || piece === 'item'))]);
  objs.push([file, obj]);
}
outputs.push([path.join(assets, 'models/item/fluid_pipe.json'), json({parent: 'apocalypse_firstlight:block/fluid_pipe/item', gui_light: 'side', display: {
  gui: {rotation: [30, 225, 0], translation: [0, 0, 0], scale: S3(0.625)}, ground: {translation: [0, 3, 0], scale: S3(0.25)}, fixed: {rotation: [0, 90, 0], scale: S3(0.5)},
  thirdperson_righthand: {rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: S3(0.375)}, thirdperson_lefthand: {rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: S3(0.375)},
  firstperson_righthand: {rotation: [0, 45, 0], scale: S3(0.4)}, firstperson_lefthand: {rotation: [0, 225, 0], scale: S3(0.4)}}})],
  // every state is replaced by the assembled model (client/FluidPipeModels); the variant only names a model
  [path.join(assets, 'blockstates/fluid_pipe.json'), json({variants: {'': {model: 'apocalypse_firstlight:block/fluid_pipe/box'}}})]);
const tris = piece => B.PARTS.filter(p => p.bone === piece).reduce((s, p) => s + p.f.reduce((t, f) => t + f.ids.length - 2, 0), 0);
export const stats = {triangles: Object.fromEntries(Object.keys(PIECES).map(k => [k, tris(k)])), models: MODELS.length, texelsPerPx: B.UV.S,
  islands: B.UV.islands.length, coplanar: B.coplanar.length};
const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  console.log(JSON.stringify(stats));
  if (B.coplanar.length) console.log("COPLANAR", JSON.stringify([...new Set(B.coplanar.map(c => c.a + " | " + c.b))]));
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
