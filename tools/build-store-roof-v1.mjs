// Store roof V1 (docs/models/fuel_stop_a1_details_v1.md): the TPO roof block and the rooftop unit, for Fuel Stop A1. The user
// approved the concept on 2026-10-09 (the rooftop unit V1 is its look only; the roof's underside a white flat ceiling for now).
//   roof_tpo        a full block: the top a white TPO membrane (a soft mottle, no seams drawn), the underside a white flat
//                   ceiling (the dropped ceiling comes with the interior round), the sides the deck's edge; 256 px textures
//                   (240 texels a block) with LabPBR _s / _n, tileable.
//   rooftop_unit    a packaged rooftop unit, 2 x 1 x 2 cells (Pure Mesh, chunk-baked OBJ): a galvanized roof curb 350 mm, a
//                   1650 x 950 x 1050 mm cabinet in light grey with its base rail, the condenser section at the east end
//                   (coil grilles with louvres on three sides, the round fan guard on top), the economizer rain hood on the
//                   west end, the service panels' handles and the electrical disconnect on the front.
//   node tools/build-store-roof-v1.mjs                -> writes sources, textures, OBJ / MTL / block + item models, blockstates
//   node tools/build-store-roof-v1.mjs --check        -> verifies every output is up to date
//   node tools/build-store-roof-v1.mjs --preview DIR  -> writes only OBJ + maps into DIR
// Millimetres, origin at the master cell's bottom centre, x east, y up, z south; drawn facing north (front toward -z).
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {Part, extrude, unwrap, paint, png, readPng, zFightLevels, area2, add, dot, cross, norm, newell, sub} from './cube-slab-mesh-lib.mjs';
import {heldDisplay} from './item-held-display.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function assert(c, m) { if (!c) throw new Error(m); }
const PX = 0.016;

// ---------------- helpers (as tools/build-prairie-signage-v1.mjs) ----------------
const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
function rrect(cu, cv, w, h, r, seg = 3) {
  const u0 = cu - w / 2, v0 = cv - h / 2, u1 = cu + w / 2, v1 = cv + h / 2, out = [];
  if (r <= 0) return [[u0, v0], [u1, v0], [u1, v1], [u0, v1]];
  for (const [ccu, ccv, a0] of [[u1 - r, v1 - r, 0], [u0 + r, v1 - r, 90], [u0 + r, v0 + r, 180], [u1 - r, v0 + r, 270]])
    for (let i = 0; i <= seg; i++) { const a = (a0 + 90 * i / seg) * Math.PI / 180; out.push([ccu + r * Math.cos(a), ccv + r * Math.sin(a)]); }
  return out;
}
const shapeOf = (outer, holes = []) => ({outer: orient(outer, true), holes: holes.map(h => orient(h, false))});
const planY = (part, outer, holes, y0, y1, c) => extrude(part, 'y', shapeOf(outer.map(([x, z]) => [z, x]), holes.map(h => h.map(([x, z]) => [z, x]))), y0, y1, c);
const alongZ = (part, outline, z0, z1, c) => extrude(part, 'z', shapeOf(outline), z0, z1, c);   // a front outline (x, y) along z
/** A box from a to b (mm), chamfer c. */
const box = (part, a, b, c = 0) => planY(part, [[a[0], a[2]], [b[0], a[2]], [b[0], b[2]], [a[0], b[2]]], [], a[1], b[1], c);
function latheY(part, cx, cz, profile, seg, {capBottom = true, capTop = true, tag = 'side'} = {}) {
  const ang = i => Math.PI / seg + 2 * Math.PI * i / seg;
  const rings = profile.map(([r, y]) => r < 1e-6 ? null : Array.from({length: seg}, (_, i) => part.vtx([cx + r * Math.cos(ang(i)), y, cz + r * Math.sin(ang(i))])));
  for (let k = 0; k + 1 < profile.length; k++) {
    const [ra, ya] = profile[k], [rb, yb] = profile[k + 1], dr = rb - ra, dy = yb - ya;
    for (let i = 0; i < seg; i++) {
      const j = (i + 1) % seg, mm = ang(i) + Math.PI / seg, n = [dy * Math.cos(mm), -dr, dy * Math.sin(mm)];
      const t = Math.abs(dy) < 1e-6 ? 'cap' : Math.abs(dr) > 0.3 * Math.abs(dy) ? 'bevel' : tag;
      if (!rings[k]) part.face([part.vtx([cx, ya, cz]), rings[k + 1][j], rings[k + 1][i]], n, t);
      else if (!rings[k + 1]) part.face([rings[k][i], rings[k][j], part.vtx([cx, yb, cz])], n, t);
      else part.face([rings[k][i], rings[k][j], rings[k + 1][j], rings[k + 1][i]], n, t);
    }
  }
  for (const [k, s, on] of [[0, -1, capBottom], [profile.length - 1, 1, capTop]]) { if (!on || !rings[k]) continue; const c = part.vtx([cx, profile[k][1], cz]);
    for (let i = 0; i < seg; i++) part.face([c, rings[k][i], rings[k][(i + 1) % seg]], [0, s, 0], 'cap'); }
}
const MAT = (c, hl, sm, se, f0 = 20) => ({c, hl, sm, se, f0});
const hash3 = (a, b, c) => { let h = Math.imul(a | 0, 374761393) ^ Math.imul(b | 0, 668265263) ^ Math.imul(c | 0, 2147483647); h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };
const sm3 = t => t * t * (3 - 2 * t);
/** Value noise; with `per` the lattice wraps every `per` cells on x and y (tileable textures). */
function vn3(x, y, z, seed, per = 0) {
  const xi = Math.floor(x), yi = Math.floor(y), zi = Math.floor(z), tx = sm3(x - xi), ty = sm3(y - yi), tz = sm3(z - zi);
  const w = (v, p) => p ? ((v % p) + p) % p : v;
  const h = (i, j, k) => hash3(w(xi + i, per), w(yi + j, per) + 977 * seed, zi + k), l = (a, b, t) => a + (b - a) * t;
  return l(l(l(h(0, 0, 0), h(1, 0, 0), tx), l(h(0, 1, 0), h(1, 1, 0), tx), ty), l(l(h(0, 0, 1), h(1, 0, 1), tx), l(h(0, 1, 1), h(1, 1, 1), tx), ty), tz) * 2 - 1;
}
const mottled = (base, k = 0.03, seed = 1) => pos => { const [x, y, z] = pos.map(v => v / PX); return {c: base.map(v => v * (1 + k * vn3(x / 90, y / 90, z / 90, seed) + k * 0.4 * vn3(x / 25, y / 25, z / 25, seed + 7)))}; };

// ---------------- roof block textures ----------------
const TEX = 256;
/** A tileable 256 px face: colour from base * (1 + mottle), LabPBR _s (smoothness, F0 dielectric, no emission), flat _n. */
function face(base, {k = 0.025, sm = 100, seed = 1, dirt = 0} = {}) {
  const c = Buffer.alloc(TEX * TEX * 4), s = Buffer.alloc(TEX * TEX * 4), n = Buffer.alloc(TEX * TEX * 4);
  for (let y = 0; y < TEX; y++) for (let x = 0; x < TEX; x++) {
    const u = x / TEX * 4, v = y / TEX * 4, m = k * vn3(u, v, 0, seed, 4) + k * 0.45 * vn3(u * 4, v * 4, 0, seed + 3, 16);
    const d = dirt ? Math.max(0, vn3(u * 0.999 + 0.37, v + 0.19, 0, seed + 11, 4)) * dirt : 0;   // faint weathering
    const i = (y * TEX + x) * 4;
    for (let ch = 0; ch < 3; ch++) c[i + ch] = Math.max(0, Math.min(255, Math.round(base[ch] * (1 + m - d))));
    c[i + 3] = 255;
    s[i] = Math.round(sm * (1 - d * 1.5)); s[i + 1] = 20; s[i + 2] = 0; s[i + 3] = 255;
    n[i] = 128; n[i + 1] = 128; n[i + 2] = 255; n[i + 3] = 255;
  }
  return [png(c, TEX, TEX), png(s, TEX, TEX), png(n, TEX, TEX)];
}
const ROOF_FACES = {
  roof_tpo_top: face([226, 228, 224], {k: 0.022, sm: 118, seed: 2, dirt: 0.05}),    // white TPO membrane
  roof_tpo_bottom: face([236, 236, 232], {k: 0.012, sm: 60, seed: 5}),             // white flat ceiling
  roof_tpo_side: face([158, 156, 150], {k: 0.03, sm: 64, seed: 7}),               // the deck's edge (hidden in the walls)
};

// ---------------- rooftop unit ----------------
// two cells along x (east for facing north), the master the first: the cabinet runs from x -300 to 1350
export const RTU = {curb: [-260, 1310, -420, 420, 350], body: [-300, 1350, -475, 475, 350, 1400], rail: 80, cond: 850, hood: 190};
function buildRtu() {
  const PARTS = [], P = (name, bone, mat) => { const p = new Part(name, bone, mat); PARTS.push(p); return p; };
  const [cx0, cx1, cz0, cz1, ch] = RTU.curb, [bx0, bx1, bz0, bz1, by0, by1] = RTU.body;
  box(P('curb', 'body', 'galv'), [cx0, 0, cz0], [cx1, ch + 1, cz1], 4);                                   // roof curb (1 mm into the rail)
  box(P('rail', 'body', 'rail'), [bx0, by0, bz0], [bx1, by0 + RTU.rail, bz1], 3);                        // base rail
  // one cabinet, 4 mm inside the rail all round (no face shared with it); the condenser end is its louvred east part
  const [kx0, kx1, kz0, kz1] = [bx0 + 4, bx1 - 4, bz0 + 4, bz1 - 4];
  box(P('cabinet', 'body', 'casing'), [kx0, by0 + RTU.rail - 1, kz0], [kx1, by1, kz1], 6);
  // coil grilles: louvre bars on the condenser's front, back and east end (proud of the casing by 6 mm, 2 mm into it)
  const gy0 = by0 + RTU.rail + 60, gy1 = by1 - 70;
  for (let y = gy0; y + 18 <= gy1; y += 34) {
    box(P(`louvre_f_${y}`, 'body', 'grille'), [RTU.cond + 50, y, kz0 - 6], [kx1 - 50, y + 18, kz0 + 2], 1);
    box(P(`louvre_b_${y}`, 'body', 'grille'), [RTU.cond + 50, y, kz1 - 2], [kx1 - 50, y + 18, kz1 + 6], 1);
    box(P(`louvre_e_${y}`, 'body', 'grille'), [kx1 - 2, y, kz0 + 60], [kx1 + 6, y + 18, kz1 - 60], 1);
  }
  // condenser fan: the round guard on top, its rim and hub
  const fx = (RTU.cond + bx1) / 2;
  latheY(P('fan_ring', 'body', 'guard'), fx, 0, [[220, by1 - 2], [235, by1 - 2], [235, by1 + 40], [220, by1 + 40], [220, by1 - 2]], 32, {capBottom: false, capTop: false});
  latheY(P('fan_grill', 'body', 'guard_dark'), fx, 0, [[0, by1 + 24], [222, by1 + 24], [222, by1 + 30], [0, by1 + 30]], 32);
  latheY(P('fan_hub', 'body', 'guard'), fx, 0, [[55, by1 + 29], [55, by1 + 46], [30, by1 + 52], [0, by1 + 53]], 16);
  // economizer rain hood on the west end: its top slopes down away from the unit (side profile x, y), 2 mm into the casing
  alongZ(P('hood', 'body', 'casing'), [[kx0 + 2, by0 + 900], [kx0 - RTU.hood, by0 + 720], [kx0 - RTU.hood, by0 + 380], [kx0 + 2, by0 + 380]], kz0 + 120, kz1 - 120, 4);
  // the hood's open underside: a dark louvre plate just under it (1 mm into the hood)
  box(P('hood_louvre', 'body', 'grille'), [kx0 - RTU.hood + 12, by0 + 380 - 12, kz0 + 132], [kx0 - 12, by0 + 380 + 1, kz1 - 132], 1);
  // service panel handles and the electrical disconnect on the front (-z), each 2 mm into the casing
  for (const x of [kx0 + 260, kx0 + 640]) box(P(`handle_${x}`, 'body', 'handle'), [x - 45, by0 + 520, kz0 - 18], [x + 45, by0 + 548, kz0 + 2], 3);
  box(P('disconnect', 'body', 'disconnect'), [kx0 + 380, by0 + 160, kz0 - 114], [kx0 + 560, by0 + 420, kz0 + 2], 8);
  box(P('disconnect_handle', 'body', 'handle'), [kx0 + 440, by0 + 250, kz0 - 132], [kx0 + 500, by0 + 340, kz0 - 112], 3);
  const MATS = {
    casing:     MAT(mottled([210, 208, 200], 0.02, 2), 8, 104, 124),
    galv:       MAT(mottled([168, 172, 170], 0.035, 3), 8, 92, 110, 230),
    rail:       MAT(mottled([92, 94, 96], 0.02, 4), 6, 90, 110),
    grille:     MAT([70, 72, 74], 4, 120, 136, 230),
    guard:      MAT([44, 45, 47], 6, 96, 112, 230),
    guard_dark: MAT([28, 29, 31], 2, 70, 84, 230),
    handle:     MAT([60, 62, 64], 4, 120, 136),
    disconnect: MAT(mottled([170, 172, 168], 0.02, 5), 6, 100, 120),
  };
  return {PARTS, MATS, atlas: 2048, startS: 15};
}

// ---------------- bake (as tools/build-prairie-signage-v1.mjs) ----------------
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
function bake(key, id, source, build) {
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
  const first = Object.values(MATS)[0], bg = typeof first.c === 'function' ? [160, 160, 160] : first.c;
  const painted = paint({PARTS, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas, pad: 2, MATS, ZONED: new Set(), groupInfo: new Map(),
    sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [...bg, 255], s: [first.sm, first.f0, 0, 255], n: [128, 128, 255, 255]}});
  const maps = painted.PNG.map(b => { const r = readPng(b); assert(r.bpp === 4 && r.w === atlas, 'atlas format'); return r.px; });
  return {key, id, source, PARTS, MATS, atlas, UV, maps: maps.map(px => png(px, atlas, atlas))};
}
const B = {rtu: bake('rtu', 'rooftop_unit', 'rooftop_unit_v1', buildRtu)};
const LOOK = [[B.rtu, new Set(['body'])]];
const partsOf = look => look.flatMap(([b, bones]) => b.PARTS.filter(p => bones.has(p.bone)));
const coplanar = zFightLevels(partsOf(LOOK), new Map()).unresolved.slice(0, 6);
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
for (const p of B.rtu.PARTS) { const s = solidity(p); if (s.open || s.flipped || s.vol <= 0) solids[p.name] = s; }
assert(!Object.keys(solids).length, 'not closed / wound out: ' + JSON.stringify(solids));

// ---------------- OBJ / MTL / models ----------------
const D2R = Math.PI / 180, f6 = v => (+v.toFixed(6)).toString(), r3 = v => +v.toFixed(3) || 0;
const SMOOTH = Math.cos(36 * D2R);
function cornerNormals(p) {
  const fn = p.f.map(f => newell(f.ids.map(i => p.v[i]))), byV = new Map();
  p.f.forEach((f, k) => f.ids.forEach(i => (byV.get(i) || byV.set(i, []).get(i)).push(k)));
  return p.f.map((f, k) => { const n0 = norm(fn[k]); return f.ids.map(i => norm(byV.get(i).reduce((a, j) => dot(norm(fn[j]), n0) >= SMOOTH ? add(a, fn[j]) : a, [0, 0, 0]))); });
}
function objOf(title, file, look) {
  const out = [`# AFL ${title}, generated by tools/build-store-roof-v1.mjs`, `mtllib ${file.split('/').pop()}.mtl`];
  let vBase = 1, tBase = 1, nBase = 1;
  for (const [b, bones] of look) for (const p of b.PARTS) {
    if (!bones.has(p.bone)) continue;
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
const mtlOf = (title, look) => `# AFL ${title}\n` + look.map(([b]) => `newmtl ${b.key}\nKd 1 1 1\nmap_Kd apocalypse_firstlight:block/${b.id}\n`).join('');
const objModel = (file, particle) => ({loader: 'forge:obj', model: `apocalypse_firstlight:models/block/${file}.obj`, automatic_culling: false,
  flip_v: true, shade_quads: true, emissive_ambient: true, ambientocclusion: false, textures: {particle: `apocalypse_firstlight:block/${particle}`}});
function guiFit(look, rotation, size = 15.2) {
  const [ax, ay] = rotation.map(v => v * D2R);
  const rot = q => { const x = q[0] * Math.cos(ay) + q[2] * Math.sin(ay), z = -q[0] * Math.sin(ay) + q[2] * Math.cos(ay); return [x, q[1] * Math.cos(ax) - z * Math.sin(ax)]; };
  const pts = partsOf(look).flatMap(p => p.v.map(q => rot([q[0], q[1] - 8, q[2]])));
  const xs = pts.map(q => q[0]), ys = pts.map(q => q[1]), w = Math.max(...xs) - Math.min(...xs), h = Math.max(...ys) - Math.min(...ys);
  const scale = r3(Math.min(4, size / Math.max(w, h))), cx = (Math.max(...xs) + Math.min(...xs)) / 2, cy = (Math.max(...ys) + Math.min(...ys)) / 2;
  return {rotation, translation: [r3(-scale * cx), r3(-scale * cy), 0], scale: [scale, scale, scale]};
}
const cellPoints = look => partsOf(look).flatMap(p => p.v.map(([x, y, z]) => [x + 8, y, z + 8]));
const display = (gui, look, size) => ({...heldDisplay(cellPoints(look), {size, rotations: {fixed: [0, 180, 0]}}), gui});
const uuidOf = (ns, s) => { const h = createHash('sha256').update(`afl-store-roof-v1:${ns}:${s}`).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r12 = v => +v.toFixed(12) || 0;
function sourceOf(b) {
  const uuid = s => uuidOf(b.id, s), name = b.source;
  const bones = [...new Set(b.PARTS.map(p => p.bone))], gid = bone => uuid('group:' + bone);
  const elements = b.PARTS.map((p, n) => {
    const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {}, uvs = b.UV.faceUV.get(p);
    p.v.forEach((q, i) => { vertices[key(i)] = q.map(r12); });
    p.f.forEach((f, fi) => { const uv = uvs.get(f); faces['f' + key(fi)] = {uv: Object.fromEntries(f.ids.map((id, j) => [key(id), uv[j].map(r12)])), vertices: f.ids.map(key), texture: 0}; });
    return {name: p.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: true, locked: false,
      render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: uuid('mesh:' + p.name + ':' + n)};
  });
  return {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name, model_identifier: '', visible_box: [1, 1, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: b.atlas, height: b.atlas},
    elements, groups: bones.map(bone => ({name: bone, uuid: gid(bone), export: true, locked: false, scope: 0,
      selected: false, visibility: true, _static: {properties: {}, temp_data: {}}, origin: [0, 0, 0], rotation: [0, 0, 0],
      color: 0, children: [], reset: false, shade: true, mirror_uv: false, autouv: 0, isOpen: true, primary_selected: false})),
    outliner: bones.map(bone => ({uuid: gid(bone), isOpen: true, children: elements.filter((e, i) => b.PARTS[i].bone === bone).map(e => e.uuid)})),
    textures: [{name: name + '.png', relative_path: `textures/${name}.png`, folder: '', namespace: '', id: '0', group: '', scope: 0,
      width: b.atlas, height: b.atlas, uv_width: b.atlas, uv_height: b.atlas, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
      file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
      frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture'),
      source: 'data:image/png;base64,' + b.maps[0].toString('base64')}],
    animations: []};
}

const bb = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const json = v => JSON.stringify(v, null, 2) + '\n';
const outputs = [], objs = [];
const NS = 'apocalypse_firstlight:block/';
// roof block: textures (+ the editable copies under src/main/blockbench/textures), cube model, blockstate, item
for (const [id, maps] of Object.entries(ROOF_FACES)) ['', '_s', '_n'].forEach((k, i) => {
  outputs.push([path.join(assets, `textures/block/${id}${k}.png`), maps[i]], [path.join(bb, `textures/${id}_v1${k}.png`), maps[i]]);
});
outputs.push([path.join(assets, 'models/block/roof_tpo.json'), json({parent: 'minecraft:block/cube', textures: {particle: NS + 'roof_tpo_top',
  up: NS + 'roof_tpo_top', down: NS + 'roof_tpo_bottom', north: NS + 'roof_tpo_side', south: NS + 'roof_tpo_side', east: NS + 'roof_tpo_side', west: NS + 'roof_tpo_side'}})]);
outputs.push([path.join(assets, 'blockstates/roof_tpo.json'), json({variants: {'': {model: NS + 'roof_tpo'}}})]);
outputs.push([path.join(assets, 'models/item/roof_tpo.json'), json({parent: NS + 'roof_tpo'})]);
// rooftop unit: the master (c0r0) draws it, the other cells nothing
{
  const b = B.rtu, file = 'rooftop_unit/unit', title = 'Store roof V1 rooftop unit';
  const obj = objOf(title, file, LOOK);
  outputs.push([path.join(assets, `models/block/${file}.obj`), obj], [path.join(assets, `models/block/${file}.mtl`), mtlOf(title, LOOK)],
    [path.join(assets, `models/block/${file}.json`), json(objModel(file, b.id))]);
  objs.push([file, obj]);
  outputs.push([path.join(bb, `${b.source}.bbmodel`), JSON.stringify(sourceOf(b))],
    ...['', '_s', '_n'].flatMap((k, i) => [[path.join(bb, `textures/${b.source}${k}.png`), b.maps[i]], [path.join(assets, `textures/block/${b.id}${k}.png`), b.maps[i]]]));
  outputs.push([path.join(assets, 'models/block/rooftop_unit/cell.json'), json({textures: {particle: NS + 'rooftop_unit'}, elements: []})]);
  const YROT = {north: 0, east: 90, south: 180, west: 270}, yr = y => (y % 360 ? {y: y % 360} : {});
  const variants = {};
  for (const [f, y] of Object.entries(YROT)) for (const cell of ['c0r0', 'c1r0', 'c0r1', 'c1r1'])
    variants[`cell=${cell},facing=${f}`] = cell === 'c0r0' ? {model: NS + file, ...yr(y)} : {model: NS + 'rooftop_unit/cell'};
  outputs.push([path.join(assets, 'blockstates/rooftop_unit.json'), json({variants})]);
  outputs.push([path.join(assets, 'models/item/rooftop_unit.json'), json({parent: NS + file, gui_light: 'side', display: display(guiFit(LOOK, [25, 200, 0]), LOOK, 1)})]);
}
const tris = look => partsOf(look).reduce((s, p) => s + p.f.reduce((t, f) => t + f.ids.length - 2, 0), 0);
const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  console.log(JSON.stringify({triangles: tris(LOOK), texelsPerPx: B.rtu.UV.S, coplanar}));
  const pi = process.argv.indexOf('--preview');
  if (pi > 0) {
    const dir = process.argv[pi + 1]; fs.mkdirSync(dir, {recursive: true});
    for (const [file, obj] of objs) fs.writeFileSync(path.join(dir, file.replace('/', '_') + '.obj'), obj);
    ['', '_s', '_n'].forEach((k, i) => fs.writeFileSync(path.join(dir, `${B.rtu.id}${k}.png`), B.rtu.maps[i]));
    for (const [id, maps] of Object.entries(ROOF_FACES)) fs.writeFileSync(path.join(dir, `${id}.png`), maps[0]);
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
