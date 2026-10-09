// Trash enclosure V1 (docs/models/fuel_stop_a1_details_v1.md): the CMU screen wall and the steel enclosure gate, for Fuel
// Stop A1. The user approved the concept on 2026-10-09 (wall 2 m with its cap; a 2 x 2 cell gate leaf, a pair per opening).
//   cmu_screen_wall  a 200 mm ground face CMU wall hugging one or more edges of its cell (states north / east / south /
//                    west), the top cell capped with the store's cast stone (state cap, 80 mm, 0.5 px proud each side; the
//                    wall below it 1.92 m so the whole wall is 2.0 m). Vanilla JSON element models on the Facade Masonry
//                    Base textures (240 texels a block, the same courses as the store's base), turned per edge; the
//                    north / south walls run the whole edge, the east / west ones stop short of them (no overlapping
//                    volume, no coplanar face at an outer corner); the caps likewise, with a corner piece on the
//                    north / south cap at an outer corner. The generator is the editable source (no Blockbench source).
//   enclosure_gate   a steel gate leaf over 2 x 2 cells (Pure Mesh + LabPBR, AFL Animated Block Mesh Runtime): its own
//                    hinge post (100 mm tube on a base plate, standing in front of the screen wall's end), two barrel
//                    hinges, the leaf (50 mm tube frame, 17 vertical slats, mid rail and back brace, drop rod and pull
//                    on the free stile), 1.82 m wide x 1.9 m high. Bones 'frame' (still) and 'leaf' (channel 'swing',
//                    90 degrees outward, following the block's OPEN); one rig per hinge side (right = the first column).
//   node tools/build-trash-enclosure-v1.mjs                -> writes models, blockstates, rigs, atlases, sources
//   node tools/build-trash-enclosure-v1.mjs --check        -> verifies every output is up to date
//   node tools/build-trash-enclosure-v1.mjs --preview DIR  -> writes only OBJ + maps into DIR
// Gate frame: millimetres, origin at the master cell's (c0r0) bottom centre, x east, y up, z south; drawn facing north
// (the front, toward which the leaf opens, at -z; the second column at +x). The leaf hangs at the back of its cells (the
// enclosure's edge, z 500 mm), so the screen walls run from the cells behind.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {Part, extrude, unwrap, paint, png, readPng, zFightLevels, area2, add, dot, cross, norm, newell, sub} from './cube-slab-mesh-lib.mjs';
import {convert, serializeCompact} from './export-afl-mesh.mjs';
import {heldDisplay} from './item-held-display.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function assert(c, m) { if (!c) throw new Error(m); }
const PX = 0.016, D2R = Math.PI / 180;
const r3 = v => +v.toFixed(3) || 0, r6 = v => +v.toFixed(6) || 0, r12 = v => +v.toFixed(12) || 0;
const NS = 'apocalypse_firstlight:block/';
const json = v => JSON.stringify(v, null, 2) + '\n';

// =============================== CMU screen wall ===============================
export const WALL = {t: 3.2, capH: 1.28, capO: 0.5};   // px: 200 mm thick, cap 80 mm high and 31 mm proud each side
const MASONRY = i => NS + 'facade_masonry/ground_face_block_' + i, CAP_TEX = NS + 'facade_masonry/cast_stone_cap';
/** A UV span inside 0..16: kept where it is, shifted in, or squeezed to 0..16 (the cap's stone is near plain). */
function span(a, b) {
  if (a >= 0 && b <= 16) return [r6(a), r6(b)];
  if (b - a <= 16) { const s = a < 0 ? -a : 16 - b; return [r6(a + s), r6(b + s)]; }
  return [0, 16];
}
/** A JSON element, UVs as vanilla's defaults for its place (the courses line up with full blocks), kept in 0..16. */
function element(from, to, texture, cull = {}) {
  const [x0, y0, z0] = from, [x1, y1, z1] = to, faces = {};
  const uv = (u, v) => { const [u0, u1] = span(...u), [v0, v1] = span(...v); return [u0, v0, u1, v1]; };
  const F = {
    north: uv([16 - x1, 16 - x0], [16 - y1, 16 - y0]), south: uv([x0, x1], [16 - y1, 16 - y0]),
    east: uv([16 - z1, 16 - z0], [16 - y1, 16 - y0]), west: uv([z0, z1], [16 - y1, 16 - y0]),
    up: uv([x0, x1], [z0, z1]), down: uv([x0, x1], [16 - z1, 16 - z0]),
  };
  for (const [k, u] of Object.entries(F)) faces[k] = {uv: u, texture: '#' + texture, ...(cull[k] ? {cullface: k} : {})};
  return {from: from.map(r6), to: to.map(r6), faces};
}
/** The wall along the north edge, x from lo to hi (an east / west wall turned onto the north edge, trimmed by a north / south one). */
function wallModel(variant, capped, trimLo, trimHi) {
  const {t, capH} = WALL, x0 = trimLo ? t : 0, x1 = trimHi ? 16 - t : 16, top = capped ? 16 - capH : 16;
  return {ambientocclusion: true, textures: {wall: MASONRY(variant), particle: MASONRY(variant)},
    elements: [element([x0, 0, 0], [x1, top, t], 'wall', {north: true, down: true, up: !capped, west: x0 === 0, east: x1 === 16})]};
}
function capModel(trimLo, trimHi) {
  const {t, capH, capO} = WALL, x0 = trimLo ? t + capO : 0, x1 = trimHi ? 16 - t - capO : 16;
  return {ambientocclusion: true, textures: {cap: CAP_TEX, particle: CAP_TEX}, elements: [element([x0, 16 - capH, -capO], [x1, 16, t + capO], 'cap')]};
}
const capCorner = hi => ({ambientocclusion: true, textures: {cap: CAP_TEX, particle: CAP_TEX},
  elements: [element([hi ? 16 : -WALL.capO, 16 - WALL.capH, -WALL.capO], [hi ? 16 + WALL.capO : 0, 16, WALL.t + WALL.capO], 'cap')]});
const TRIMS = {full: [false, false], lo: [true, false], hi: [false, true], both: [true, true]};
const trimName = (lo, hi) => lo && hi ? 'both' : lo ? 'lo' : hi ? 'hi' : 'full';
function wallBlockstate() {
  const parts = [], YROT = {north: 0, east: 90, south: 180, west: 270};
  const rot = (model, y) => ({model: 'apocalypse_firstlight:block/cmu_screen_wall/' + model, ...(y ? {y} : {})});
  // an edge's trims (its ends, in its own north-edge frame) from the walls it meets: east / west stop short of north / south
  const ends = {north: [[], []], south: [[], []], east: [['north'], ['south']], west: [['south'], ['north']]};
  for (const [edge, y] of Object.entries(YROT)) {
    const [loBy, hiBy] = ends[edge];
    for (const lo of loBy.length ? [false, true] : [false]) for (const hi of hiBy.length ? [false, true] : [false]) {
      const when = {[edge]: 'true', ...(loBy.length ? {[loBy[0]]: String(lo)} : {}), ...(hiBy.length ? {[hiBy[0]]: String(hi)} : {})};
      const trim = trimName(lo, hi);
      for (const capped of [false, true])
        parts.push({when: {...when, cap: String(capped)}, apply: [0, 1, 2].map(v => rot(`wall_${capped ? 'capped' : 'plain'}_${trim}_${v}`, y))});
      parts.push({when: {...when, cap: 'true'}, apply: rot('cap_' + trim, y)});
    }
  }
  // the north / south cap's corner pieces at an outer corner (lo = its west end facing north, its east end facing south)
  for (const [edge, y, loBy, hiBy] of [['north', 0, 'west', 'east'], ['south', 180, 'east', 'west']]) {
    parts.push({when: {[edge]: 'true', [loBy]: 'true', cap: 'true'}, apply: rot('cap_corner_lo', y)});
    parts.push({when: {[edge]: 'true', [hiBy]: 'true', cap: 'true'}, apply: rot('cap_corner_hi', y)});
  }
  return {multipart: parts};
}
// the item: a capped straight piece, centred in its cell
function wallItem() {
  const {t, capH, capO} = WALL, z0 = 8 - t / 2;
  return {parent: 'minecraft:block/block', textures: {wall: MASONRY(0), cap: CAP_TEX, particle: MASONRY(0)},
    elements: [element([0, 0, z0], [16, 16 - capH, z0 + t], 'wall'), element([0, 16 - capH, z0 - capO], [16, 16, z0 + t + capO], 'cap')]};
}

// =============================== enclosure gate ===============================
const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
const shapeOf = (outer, holes = []) => ({outer: orient(outer, true), holes: holes.map(h => orient(h, false))});
const planY = (part, outer, y0, y1, c) => extrude(part, 'y', shapeOf(outer.map(([x, z]) => [z, x])), y0, y1, c);
const alongZ = (part, outline, z0, z1, c) => extrude(part, 'z', shapeOf(outline), z0, z1, c);
const box = (part, a, b, c = 0) => planY(part, [[a[0], a[2]], [b[0], a[2]], [b[0], b[2]], [a[0], b[2]]], a[1], b[1], c);
function latheY(part, cx, cz, profile, seg) {
  const ang = i => Math.PI / seg + 2 * Math.PI * i / seg;
  const rings = profile.map(([r, y]) => Array.from({length: seg}, (_, i) => part.vtx([cx + r * Math.cos(ang(i)), y, cz + r * Math.sin(ang(i))])));
  for (let k = 0; k + 1 < profile.length; k++) {
    const [ra, ya] = profile[k], [rb, yb] = profile[k + 1], dr = rb - ra, dy = yb - ya;
    for (let i = 0; i < seg; i++) {
      const j = (i + 1) % seg, mm = ang(i) + Math.PI / seg;
      part.face([rings[k][i], rings[k][j], rings[k + 1][j], rings[k + 1][i]], [dy * Math.cos(mm), -dr, dy * Math.sin(mm)], Math.abs(dr) > 0.3 * Math.abs(dy) ? 'bevel' : 'side');
    }
  }
  for (const [k, s] of [[0, -1], [profile.length - 1, 1]]) { const c = part.vtx([cx, profile[k][1], cz]);
    for (let i = 0; i < seg; i++) part.face(s < 0 ? [c, rings[k][i], rings[k][(i + 1) % seg]] : [c, rings[k][(i + 1) % seg], rings[k][i]], [0, s, 0], 'cap'); }
}
const rod = (part, cx, cz, r, y0, y1, seg = 12) => latheY(part, cx, cz, [[r, y0], [r, y1]], seg);
const MAT = (c, hl, sm, se, f0 = 20) => ({c, hl, sm, se, f0});
const hash3 = (a, b, c) => { let h = Math.imul(a | 0, 374761393) ^ Math.imul(b | 0, 668265263) ^ Math.imul(c | 0, 2147483647); h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };
const sm3 = t => t * t * (3 - 2 * t);
function vn3(x, y, z, seed) {
  const xi = Math.floor(x), yi = Math.floor(y), zi = Math.floor(z), tx = sm3(x - xi), ty = sm3(y - yi), tz = sm3(z - zi);
  const h = (i, j, k) => hash3(xi + i, yi + j + 977 * seed, zi + k), l = (a, b, t) => a + (b - a) * t;
  return l(l(l(h(0, 0, 0), h(1, 0, 0), tx), l(h(0, 1, 0), h(1, 1, 0), tx), ty), l(l(h(0, 0, 1), h(1, 0, 1), tx), l(h(0, 1, 1), h(1, 1, 1), tx), ty), tz) * 2 - 1;
}
const mottled = (base, k = 0.03, seed = 1) => pos => { const [x, y, z] = pos.map(v => v / PX); return {c: base.map(v => v * (1 + k * vn3(x / 90, y / 90, z / 90, seed) + k * 0.4 * vn3(x / 25, y / 25, z / 25, seed + 7)))}; };

export const GATE = {
  post: [-450, -350, 400, 498], postTop: 2001, plate: [-470, -330, 380, 498, 10],
  pivot: [-334, 388], knuckleR: 16,   // the knuckles clear the post's corner by 4 mm
  hinges: [[280, 360, 362, 442], [1640, 1720, 1722, 1802]],   // the post's knuckle y0..y1, the leaf's knuckle y0..y1
  leaf: {x0: -314, x1: 1492, z0: 392, z1: 442, y0: 75, y1: 1975, tube: 50},
  slats: {n: 17, w: 80, z0: 398, z1: 418}, midRail: [975, 1025],
  swing: {ticks: 18, easing: 'ease_in_out', degrees: 90},
};
function buildGate() {
  const PARTS = [], P = (name, bone, mat) => { const p = new Part(name, bone, mat); PARTS.push(p); return p; };
  const G = GATE, [px0, px1, pz0, pz1] = G.post, [bx0, bx1, bz0, bz1, bh] = G.plate, [kx, kz] = G.pivot, L = G.leaf, T = L.tube;
  // ---- frame: the post on its base plate, the cap, the post's hinge knuckles with their plates
  box(P('base_plate', 'frame', 'galv'), [bx0, 0, bz0], [bx1, bh, bz1], 2);
  box(P('post', 'frame', 'paint'), [px0, bh - 1, pz0], [px1, G.postTop, pz1], 3);
  box(P('post_cap', 'frame', 'paint'), [px0 - 6, G.postTop - 1, pz0 - 6], [px1 + 6, G.postTop + 7, pz1 + 2], 2);
  G.hinges.forEach(([a0, a1, b0, b1], i) => {
    rod(P(`post_knuckle_${i}`, 'frame', 'hardware'), kx, kz, G.knuckleR, a0, a1, 16);
    box(P(`post_strap_${i}`, 'frame', 'hardware'), [px0 + 70, a0 + 4, kz - 9], [kx + 2, a1 - 4, pz0 + 1], 1);
    // ---- the leaf's knuckle and its strap onto the hinge stile
    rod(P(`leaf_knuckle_${i}`, 'leaf', 'hardware'), kx, kz, G.knuckleR, b0, b1, 16);
    box(P(`leaf_strap_${i}`, 'leaf', 'hardware'), [kx - 2, b0 + 4, L.z0 - 8], [L.x0 + 30, b1 - 4, L.z0 + 1], 1);
  });
  // ---- the leaf: stiles full height, rails between them 3 mm shallower, slats in front of the mid rail, brace behind
  box(P('stile_hinge', 'leaf', 'paint'), [L.x0, L.y0, L.z0], [L.x0 + T, L.y1, L.z1], 3);
  box(P('stile_free', 'leaf', 'paint'), [L.x1 - T, L.y0, L.z0], [L.x1, L.y1, L.z1], 3);
  const ix0 = L.x0 + T, ix1 = L.x1 - T;
  box(P('rail_bottom', 'leaf', 'paint'), [ix0, L.y0, L.z0 + 3], [ix1, L.y0 + T, L.z1 - 3], 2);
  box(P('rail_top', 'leaf', 'paint'), [ix0, L.y1 - T, L.z0 + 3], [ix1, L.y1, L.z1 - 3], 2);
  box(P('rail_mid', 'leaf', 'paint'), [ix0, G.midRail[0], G.slats.z1 - 1], [ix1, G.midRail[1], L.z1 - 3], 2);
  const S = G.slats, gap = (ix1 - ix0 - S.n * S.w) / (S.n + 1);
  for (let i = 0; i < S.n; i++) { const x = ix0 + gap + i * (S.w + gap);
    box(P(`slat_${i}`, 'leaf', 'slat'), [x, L.y0 + T - 2, S.z0], [x + S.w, L.y1 - T + 2, S.z1], 2); }
  // the brace: hinge side low to free side high, on the back, ends 20 mm into the stiles
  const [ya, yb, hb] = [L.y0 + T + 25, L.y1 - T - 25, 34];
  alongZ(P('brace', 'leaf', 'paint'), [[L.x0 + 30, ya - hb], [L.x1 - 30, yb - hb], [L.x1 - 30, yb + hb], [L.x0 + 30, ya + hb]], L.z1 - 4, L.z1 + 4, 2);
  // the free stile's front: the drop rod in two guides with its handle, and the pull
  const rx = L.x1 - T / 2, rz = L.z0 - 20;
  rod(P('drop_rod', 'leaf', 'hardware'), rx, rz, 8, 45, 760);
  for (const y of [300, 640]) box(P(`rod_guide_${y}`, 'leaf', 'hardware'), [rx - 15, y, rz - 10], [rx + 15, y + 30, L.z0 + 1], 1);
  box(P('rod_handle', 'leaf', 'hardware'), [rx - 45, 742, rz - 6], [rx + 2, 756, rz + 6], 3);
  for (const y of [950, 1090]) box(P(`pull_post_${y}`, 'leaf', 'hardware'), [rx - 7, y, L.z0 - 40], [rx + 7, y + 20, L.z0 + 1], 2);
  box(P('pull_grip', 'leaf', 'hardware'), [rx - 10, 930, L.z0 - 52], [rx + 10, 1130, L.z0 - 38], 4);
  const MATS = {
    paint:    MAT(mottled([54, 56, 58], 0.025, 2), 8, 112, 130),
    slat:     MAT(mottled([60, 62, 64], 0.02, 3), 6, 108, 126),
    hardware: MAT(mottled([44, 45, 47], 0.02, 4), 6, 118, 136, 230),
    galv:     MAT(mottled([150, 154, 152], 0.035, 5), 8, 88, 104, 230),
  };
  return {PARTS, MATS, atlas: 2048, startS: 15};
}

// ---------------- bake (as tools/build-store-roof-v1.mjs) ----------------
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
function bake(key, id, build) {
  const {PARTS: all, MATS, atlas, startS} = build(), PARTS = all.filter(p => p.f.length);
  for (const p of PARTS) p.v = p.v.map(q => q.map(v => v * PX));
  for (const p of PARTS) {
    const out = [];
    for (const f of p.f) {
      const P3 = f.ids.map(i => p.v[i]), n = newell(P3);
      const convexQuad = f.ids.length === 4 && [0, 1, 2, 3].every(k => dot(cross(sub(P3[(k + 1) % 4], P3[k]), sub(P3[(k + 2) % 4], P3[(k + 1) % 4])), n) > 1e-14);
      if (f.ids.length === 3 || convexQuad) { out.push(f); continue; }
      assert(f.ids.length === 4, `${key}: ${f.ids.length}-gon in ${p.name}`);
      const k = [0, 1, 2, 3].find(k => dot(cross(sub(P3[k], P3[(k + 3) % 4]), sub(P3[(k + 1) % 4], P3[k])), n) <= 1e-14) ?? 0;
      out.push({...f, ids: [f.ids[k], f.ids[(k + 1) % 4], f.ids[(k + 2) % 4]]}, {...f, ids: [f.ids[(k + 2) % 4], f.ids[(k + 3) % 4], f.ids[k]]});
    }
    p.f = out;
  }
  const UV = unwrap(PARTS, {atlas, pad: 2, startS, stepS: 0.5});
  for (const p of PARTS) assert(p.f.every(f => UV.faceUV.get(p)?.has(f)), `${key}: unmapped face in ${p.name}`);
  const first = Object.values(MATS)[0], bg = [60, 62, 64];
  const painted = paint({PARTS, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas, pad: 2, MATS, ZONED: new Set(), groupInfo: new Map(),
    sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [...bg, 255], s: [first.sm, first.f0, 0, 255], n: [128, 128, 255, 255]}});
  const maps = painted.PNG.map(b => { const r = readPng(b); assert(r.bpp === 4 && r.w === atlas, 'atlas format'); return r.px; });
  return {key, id, PARTS, MATS, atlas, UV, maps: maps.map(px => png(px, atlas, atlas))};
}
const GB = bake('gate', 'enclosure_gate', buildGate);
function solidity(p) {
  const key = q => q.map(v => Math.round(v * 1e5)).join(','), dir = new Map(); let vol = 0;
  for (const f of p.f) {
    const P3 = f.ids.map(i => p.v[i]);
    for (let j = 1; j + 1 < P3.length; j++) vol += dot(P3[0], cross(P3[j], P3[j + 1])) / 6;
    for (let j = 0; j < f.ids.length; j++) { const a = key(P3[j]), b = key(P3[(j + 1) % P3.length]); if (a !== b) dir.set(a + '>' + b, (dir.get(a + '>' + b) || 0) + 1); }
  }
  let open = 0, flipped = 0;
  for (const [e, n] of dir) { const [a, b] = e.split('>'); if (!dir.get(b + '>' + a)) open++; if (n > 1) flipped++; }
  return {open, flipped, vol};
}
export const solids = {};
for (const p of GB.PARTS) { const s = solidity(p); if (s.open || s.flipped || s.vol <= 0) solids[p.name] = s; }
assert(!Object.keys(solids).length, 'not closed / wound out: ' + JSON.stringify(solids));
const coplanar = zFightLevels(GB.PARTS, new Map()).unresolved.slice(0, 6);

// ---------------- rigs: right (hinge on the first column, -x) and left (its mirror across the structure's middle) ----------------
const uuidOf = (ns, s) => { const h = createHash('sha256').update(`afl-trash-enclosure-v1:${ns}:${s}`).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const BONES = ['frame', 'leaf'], MID = 8;   // px: the structure's middle line (the two columns span x -8..24)
function rig(b, hinge) {
  const s = hinge === 'right' ? 1 : -1, id = `${b.id}_${hinge}`, uuid = k => uuidOf(id, k);
  const mir = q => [s > 0 ? q[0] : 2 * MID - q[0], q[1], q[2]];
  const pivot = mir([GATE.pivot[0] * PX, 0, GATE.pivot[1] * PX]), origin = bone => bone === 'frame' ? [0, 0, 0] : pivot;
  const elements = [], nodes = new Map(BONES.map(bn => [bn, {uuid: uuid('group:' + bn), isOpen: true, children: []}]));
  for (const p of b.PARTS) {
    const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {}, uvs = b.UV.faceUV.get(p);
    p.v.forEach((q, i) => { vertices[key(i)] = mir(q).map(r12); });
    // a mirror turns the winding inside out: reverse each face's vertex order (UVs stay keyed by vertex)
    p.f.forEach((f, fi) => { const uv = uvs.get(f), ids = s > 0 ? f.ids : f.ids.slice().reverse();
      faces['f' + key(fi)] = {uv: Object.fromEntries(f.ids.map((vid, j) => [key(vid), uv[j].map(r12)])), vertices: ids.map(key), texture: 0}; });
    const eid = uuid('mesh:' + p.name);
    elements.push({name: p.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: true, locked: false,
      render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: eid});
    nodes.get(p.bone).children.push(eid);
  }
  const source = {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: id, model_identifier: '', visible_box: [2, 2, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: b.atlas, height: b.atlas},
    elements, groups: BONES.map(bn => ({name: bn, uuid: uuid('group:' + bn), export: true, locked: false, scope: 0, selected: false, visibility: true,
      _static: {properties: {}, temp_data: {}}, origin: origin(bn).slice(), rotation: [0, 0, 0], color: 0, children: [], reset: false, shade: true,
      mirror_uv: false, autouv: 0, isOpen: true, primary_selected: false})),
    outliner: BONES.map(bn => nodes.get(bn)),
    textures: [{name: b.id + '.png', relative_path: `textures/${b.id}_v1.png`, folder: '', namespace: '', id: '0', group: '', scope: 0,
      width: b.atlas, height: b.atlas, uv_width: b.atlas, uv_height: b.atlas, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
      file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
      frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture'),
      source: 'data:image/png;base64,' + b.maps[0].toString('base64')}],
    animations: []};
  const geo = {format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: 'geometry.' + id, texture_width: b.atlas,
    texture_height: b.atlas, visible_bounds_width: 4, visible_bounds_height: 3, visible_bounds_offset: [0, 1, 0]},
    bones: BONES.map(bn => { const o = origin(bn); return {name: bn, pivot: [-o[0] || 0, o[1], o[2]]}; })}]};
  const sidecar = convert(source, geo, {}, id + '.bbmodel', 2);
  // the swing as the runtime applies it (right-handed Ry about the pivot): +90 for the right rig opens toward -z
  const deg = GATE.swing.degrees * s;
  const aroundY = (q, t) => { const a = deg * t * D2R, x = q[0] - pivot[0], z = q[2] - pivot[2];
    return [pivot[0] + x * Math.cos(a) + z * Math.sin(a), q[1], pivot[2] - x * Math.sin(a) + z * Math.cos(a)]; };
  const posed = (p, t) => p.bone === 'frame' ? p.v.map(mir) : p.v.map(q => aroundY(mir(q), t));
  const aabbOf = pts => [0, 1, 2].map(k => Math.min(...pts.map(q => q[k]))).concat([0, 1, 2].map(k => Math.max(...pts.map(q => q[k]))));
  const sweep = aabbOf(b.PARTS.flatMap(p => Array.from({length: 13}, (_, k) => posed(p, k / 12)).flat())), m = 0.25;
  const bounds = [(sweep[0] - m + 8) / 16, (sweep[1] - m) / 16, (sweep[2] - m + 8) / 16, (sweep[3] + m + 8) / 16, (sweep[4] + m) / 16, (sweep[5] + m + 8) / 16].map(r6);
  // open, the leaf clears the post (the frame) everywhere: no posed leaf vertex inside the post's box
  const post = aabbOf(b.PARTS.filter(p => p.name === 'post').flatMap(p => p.v.map(mir)));
  for (const p of b.PARTS.filter(p => p.bone === 'leaf')) for (const t of [0.25, 0.5, 0.75, 1]) for (const q of posed(p, t))
    assert(!(q[0] > post[0] && q[0] < post[3] && q[2] > post[2] && q[2] < post[5] && q[1] > post[1] && q[1] < post[4]), `${id}: ${p.name} swings into the post`);
  const leafBox = t => aabbOf(b.PARTS.filter(p => p.name.startsWith('stile') || p.name.startsWith('rail_')).flatMap(p => posed(p, t)));
  const profile = {format_version: 1, geometry: `apocalypse_firstlight:geo/${id}.geo.json`, texture: `apocalypse_firstlight:textures/block/${b.id}.png`,
    origin: [0, 0, 0], scale: [1, 1, 1], facing: 'horizontal', bounds,
    parts: Object.fromEntries(BONES.map(bn => [bn, {pivot: origin(bn).map(v => r6(v / 16))}])),
    animations: {swing: {duration_ticks: GATE.swing.ticks, easing: GATE.swing.easing, transforms: {leaf: {rotation: [0, deg, 0]}}}}};
  return {hinge, id, source, geo, sidecar, profile, bounds, closed: leafBox(0).map(r3), open: leafBox(1).map(r3)};
}
const RIGS = ['right', 'left'].map(h => rig(GB, h));

// ---------------- OBJ (the item: the right rig, closed) ----------------
const f6 = v => (+v.toFixed(6)).toString(), SMOOTH = Math.cos(36 * D2R);
function cornerNormals(p) {
  const fn = p.f.map(f => newell(f.ids.map(i => p.v[i]))), byV = new Map();
  p.f.forEach((f, k) => f.ids.forEach(i => (byV.get(i) || byV.set(i, []).get(i)).push(k)));
  return p.f.map((f, k) => { const n0 = norm(fn[k]); return f.ids.map(i => norm(byV.get(i).reduce((a, j) => dot(norm(fn[j]), n0) >= SMOOTH ? add(a, fn[j]) : a, [0, 0, 0]))); });
}
function objOf(title, file, b) {
  const out = [`# AFL ${title}, generated by tools/build-trash-enclosure-v1.mjs`, `mtllib ${file.split('/').pop()}.mtl`];
  let vBase = 1, tBase = 1, nBase = 1;
  for (const p of b.PARTS) {
    out.push(`o ${p.name}`, `usemtl ${b.key}`);
    for (const q of p.v) out.push(`v ${f6(q[0] / 16 + 0.5)} ${f6(q[1] / 16)} ${f6(q[2] / 16 + 0.5)}`);
    const uvs = b.UV.faceUV.get(p), vt = [], vn = [], fl = [], cn = cornerNormals(p), nIndex = new Map();
    p.f.forEach((f, k) => {
      const uv = uvs.get(f);
      fl.push('f ' + f.ids.map((id, j) => {
        const key = cn[k][j].map(f6).join(' ');
        if (!nIndex.has(key)) { nIndex.set(key, nBase + vn.length); vn.push('vn ' + key); }
        vt.push(`vt ${f6(uv[j][0] / b.atlas)} ${f6(1 - uv[j][1] / b.atlas)}`); return `${vBase + id}/${tBase + vt.length - 1}/${nIndex.get(key)}`;
      }).join(' '));
    });
    out.push(...vt, ...vn, ...fl);
    vBase += p.v.length; tBase += vt.length; nBase += vn.length;
  }
  return out.join('\n') + '\n';
}
const mtlOf = (title, b) => `# AFL ${title}\nnewmtl ${b.key}\nKd 1 1 1\nmap_Kd apocalypse_firstlight:block/${b.id}\n`;
const objModel = (file, particle) => ({loader: 'forge:obj', model: `apocalypse_firstlight:models/block/${file}.obj`, automatic_culling: false,
  flip_v: true, shade_quads: true, emissive_ambient: true, ambientocclusion: false, textures: {particle: `apocalypse_firstlight:block/${particle}`}});
function guiFit(parts, rotation, size = 15.2) {
  const [ax, ay] = rotation.map(v => v * D2R);
  const rot = q => { const x = q[0] * Math.cos(ay) + q[2] * Math.sin(ay), z = -q[0] * Math.sin(ay) + q[2] * Math.cos(ay); return [x, q[1] * Math.cos(ax) - z * Math.sin(ax)]; };
  const pts = parts.flatMap(p => p.v.map(q => rot([q[0], q[1] - 8, q[2]])));
  const xs = pts.map(q => q[0]), ys = pts.map(q => q[1]), w = Math.max(...xs) - Math.min(...xs), h = Math.max(...ys) - Math.min(...ys);
  const scale = r3(Math.min(4, size / Math.max(w, h))), cx = (Math.max(...xs) + Math.min(...xs)) / 2, cy = (Math.max(...ys) + Math.min(...ys)) / 2;
  return {rotation, translation: [r3(-scale * cx), r3(-scale * cy), 0], scale: [scale, scale, scale]};
}
const cellPoints = parts => parts.flatMap(p => p.v.map(([x, y, z]) => [x + 8, y, z + 8]));

// ---------------- outputs ----------------
const bb = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const outputs = [], objs = [];
// wall
const WM = path.join(assets, 'models/block/cmu_screen_wall');
for (const [trim, [lo, hi]] of Object.entries(TRIMS)) {
  for (const v of [0, 1, 2]) for (const capped of [false, true])
    outputs.push([path.join(WM, `wall_${capped ? 'capped' : 'plain'}_${trim}_${v}.json`), json(wallModel(v, capped, lo, hi))]);
  outputs.push([path.join(WM, `cap_${trim}.json`), json(capModel(lo, hi))]);
}
outputs.push([path.join(WM, 'cap_corner_lo.json'), json(capCorner(false))], [path.join(WM, 'cap_corner_hi.json'), json(capCorner(true))],
  [path.join(WM, 'item.json'), json(wallItem())]);
outputs.push([path.join(assets, 'blockstates/cmu_screen_wall.json'), json(wallBlockstate())]);
outputs.push([path.join(assets, 'models/item/cmu_screen_wall.json'), json({parent: NS + 'cmu_screen_wall/item'})]);
// gate
{
  const b = GB, file = 'enclosure_gate/item', title = 'Trash enclosure V1 gate (closed, item)', obj = objOf(title, file, b);
  outputs.push([path.join(assets, `models/block/${file}.obj`), obj], [path.join(assets, `models/block/${file}.mtl`), mtlOf(title, b)],
    [path.join(assets, `models/block/${file}.json`), json(objModel(file, b.id))]);
  objs.push([file, obj]);
  for (const r of RIGS) outputs.push([path.join(assets, `geo/${r.id}.geo.json`), json(r.geo)], [path.join(assets, `meshes/${r.id}.aflmesh.json`), serializeCompact(r.sidecar)],
    [path.join(assets, `block_mesh_profiles/${r.id}.json`), json(r.profile)]);
  outputs.push([path.join(bb, 'enclosure_gate_v1.bbmodel'), JSON.stringify(RIGS[0].source)]);   // the right rig (the left one is its mirror)
  ['', '_s', '_n'].forEach((k, i) => outputs.push([path.join(bb, `textures/${b.id}_v1${k}.png`), b.maps[i]], [path.join(assets, `textures/block/${b.id}${k}.png`), b.maps[i]]));
  outputs.push([path.join(assets, 'models/block/enclosure_gate/gate.json'), json({loader: 'apocalypse_firstlight:static_mesh', textures: {particle: NS + b.id}})],
    [path.join(assets, 'models/block/enclosure_gate/cell.json'), json({textures: {particle: NS + b.id}, elements: []})]);
  const variants = {};
  for (const cell of ['c0r0', 'c1r0', 'c0r1', 'c1r1']) variants['cell=' + cell] = {model: NS + (cell === 'c0r0' ? 'enclosure_gate/gate' : 'enclosure_gate/cell')};
  outputs.push([path.join(assets, 'blockstates/enclosure_gate.json'), json({variants})]);
  outputs.push([path.join(assets, 'models/item/enclosure_gate.json'), json({parent: NS + file, gui_light: 'side',
    display: {...heldDisplay(cellPoints(b.PARTS), {size: 1, rotations: {fixed: [0, 180, 0]}}), gui: guiFit(b.PARTS, [20, 200, 0])}})]);
}
const tris = parts => parts.reduce((s, p) => s + p.f.reduce((t, f) => t + f.ids.length - 2, 0), 0);
export const stats = {gate: {triangles: tris(GB.PARTS), texelsPerPx: GB.UV.S, coplanar, rigs: Object.fromEntries(RIGS.map(r => [r.id, {bounds: r.bounds, closed: r.closed, open: r.open}]))},
  wall: {models: outputs.filter(([f]) => f.includes('cmu_screen_wall')).length}};
const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  console.log(JSON.stringify(stats));
  const pi = process.argv.indexOf('--preview');
  if (pi > 0) {
    const dir = process.argv[pi + 1]; fs.mkdirSync(dir, {recursive: true});
    for (const [file, obj] of objs) fs.writeFileSync(path.join(dir, file.replace('/', '_') + '.obj'), obj);
    ['', '_s', '_n'].forEach((k, i) => fs.writeFileSync(path.join(dir, `${GB.id}${k}.png`), GB.maps[i]));
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
