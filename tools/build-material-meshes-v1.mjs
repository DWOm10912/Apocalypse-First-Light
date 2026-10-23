// AFL Material Meshes V1: eight Material System V1 items as Pure Mesh, drawn by AflStaticMeshItemRenderer in the world views
// (hand, ground, item frame); the inventory shows a 16x16 2D icon drawn from the same mesh in the same view, so the
// materials tab stays pixel art. One bone, one 256 LabPBR atlas (Base Color / _s / _n) per item.
//   node tools/build-material-meshes-v1.mjs            -> writes sources, atlases, geo, AFL mesh sidecars, icons and item models
//   node tools/build-material-meshes-v1.mjs --check    -> verifies every output is up to date
// Frame (px): mesh centred on X / Z, resting on y = 0; the item renderer's vertical offset (0.5 - height / 32) centres it.
// Items are authored at icon size (longest side 9-13 px), not real-world size, so they read like other held items.
// Materials follow the AFL rules: plain surfaces, no painted marks, no noise; metals are metal only where they really are
// bare metal, and stay low-smoothness so they do not glare under shaders.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {convert, serializeCompact} from './export-afl-mesh.mjs';
import {Part, extrude, area2, unwrap, paint, png, readPng, zFightLevels, add, sub, mul, dot, cross, norm, newell} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const BB = path.join(ROOT, 'src/main/blockbench'), ASSETS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const ATLAS = 256, ATLAS_DEFAULT = ATLAS, PAD = 2;
function assert(c, m) { if (!c) throw new Error(m); }
const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
const circle = (cu, cv, r, n, phase = Math.PI / n) => Array.from({length: n}, (_, i) => [cu + r * Math.cos(phase + 2 * Math.PI * i / n), cv + r * Math.sin(phase + 2 * Math.PI * i / n)]);
function roundRect(u0, v0, u1, v1, r, seg = 3) {
  const out = [];
  for (const [cu, cv, a0] of [[u1 - r, v0 + r, -90], [u1 - r, v1 - r, 0], [u0 + r, v1 - r, 90], [u0 + r, v0 + r, 180]])
    for (let i = 0; i <= seg; i++) { const a = (a0 + 90 * i / seg) * Math.PI / 180; out.push([cu + r * Math.cos(a), cv + r * Math.sin(a)]); }
  return out;
}
const rotY = deg => { const a = deg * Math.PI / 180, c = Math.cos(a), s = Math.sin(a); return p => [p[0] * c + p[2] * s, p[1], -p[0] * s + p[2] * c]; };
const rotX = deg => { const a = deg * Math.PI / 180, c = Math.cos(a), s = Math.sin(a); return p => [p[0], p[1] * c - p[2] * s, p[1] * s + p[2] * c]; };
const move = d => p => add(p, d);
const transform = (part, ...fns) => { part.v = part.v.map(p => fns.reduce((q, f) => f(q), p)); };

// ---------------------------------------------------------------- items

/** Steel billet: continuous-cast square billet, rounded corners, dark mill scale; torch-cut ends in bare steel. */
function steelBillet() {
  const bar = new Part('billet', 'steel_billet', 'scale');
  extrude(bar, 'x', {outer: orient(roundRect(-1.8, 0, 1.8, 3.6, 0.55), true), holes: []}, -6.5, 6.5, 0.2);
  for (const f of bar.f) if (f.tag === 'cap' || f.tag === 'bevel') f.mat = 'cut';
  return {PARTS: [bar], MATS: {
    scale: {c: [60, 63, 69], hl: 10, sm: 72, se: 100, f0: 30},        // iron-oxide mill scale: dielectric, matte
    cut:   {c: [146, 144, 140], hl: 10, sm: 128, se: 150, f0: 230},   // LabPBR iron
  }};
}

/** Lead brick: 1 : 2 : 4 interlocking shielding brick, male chevron at one end and the matching notch at the other. */
function leadBrick() {
  const brick = new Part('brick', 'lead_brick', 'lead');
  // plan outline in (z, x): notch end at +x, point at -x
  const plan = [[-3, -3], [-3, 6], [0, 3], [3, 6], [3, -3], [0, -6]];
  extrude(brick, 'y', {outer: orient(plan, true), holes: []}, 0, 3, 0.25);
  return {PARTS: [brick], MATS: {
    lead: {c: [112, 115, 121], hl: 6, sm: 58, se: 86, f0: 235},       // LabPBR lead, neutral grey (not the old purple)
  }};
}

/** Electrolytic nickel: one sheared cathode square, thick and flat. */
function electrolyticNickel() {
  const square = new Part('square', 'electrolytic_nickel', 'nickel');
  extrude(square, 'y', {outer: orient(roundRect(-3, -3, 3, 3, 0.3, 2), true), holes: []}, 0, 1.8, 0.2);
  return {PARTS: [square], MATS: {
    nickel: {c: [160, 161, 158], hl: 14, sm: 104, se: 132, f0: 255},  // nodular cathode: metal, matte
  }};
}

/** Cemented carbide blank: as-sintered round rod blank with two coolant holes, chamfered ends. */
function cementedCarbideBlank() {
  const rod = new Part('rod', 'cemented_carbide_blank', 'carbide');
  const holes = [-0.75, 0.75].map(u => orient(circle(u, 1.8, 0.3, 8), false));
  extrude(rod, 'x', {outer: orient(circle(0, 1.8, 1.8, 20), true), holes}, -6, 6, 0.25);
  return {PARTS: [rod], MATS: {
    carbide: {c: [104, 106, 112], hl: 10, sm: 84, se: 118, f0: 255},  // WC-Co: dark metal, as-sintered matte
  }};
}

/**
 * Sweep a closed tube along a path (rings of sides + 1 vertices, the last one repeats the first so the UV seam is clean).
 * radius(i) per ring, mat(i) per segment i -> i + 1, capMat for both end caps. UV: one strip in the atlas band {x, y, w, h},
 * arc length along u, around the tube along v. Returns the vertex -> texel map.
 */
function sweepTube(part, path, {sides, radius, mat, capMat, band}) {
  const T = path.map((p, i) => norm(sub(path[Math.min(i + 1, path.length - 1)], path[Math.max(i - 1, 0)])));
  let n = cross(T[0], [0, 0, 1]); n = norm(Math.hypot(...n) < 1e-6 ? cross(T[0], [1, 0, 0]) : n);
  const rings = [], arc = [0];
  for (let i = 0; i < path.length; i++) {
    if (i) { n = norm(sub(n, mul(T[i], dot(n, T[i])))); arc.push(arc[i - 1] + Math.hypot(...sub(path[i], path[i - 1]))); }
    const b = cross(T[i], n), r = radius(i);
    rings.push(Array.from({length: sides + 1}, (_, j) => { const a = 2 * Math.PI * j / sides; return part.vtx(add(path[i], add(mul(n, r * Math.cos(a)), mul(b, r * Math.sin(a))))); }));
  }
  const tex = new Map(), total = arc[arc.length - 1];
  const U = s => band.x + 0.5 + (s / total) * (band.w - 1), V = j => band.y + 0.5 + (j / sides) * (band.h - 1);
  rings.forEach((ring, i) => ring.forEach((id, j) => tex.set(id, [U(arc[i]), V(j)])));
  for (let i = 0; i + 1 < path.length; i++) for (let j = 0; j < sides; j++) {
    const ids = [rings[i][j], rings[i][j + 1], rings[i + 1][j + 1], rings[i + 1][j]];
    const mid = mul(ids.reduce((s, id) => add(s, part.v[id]), [0, 0, 0]), 0.25), axis = mul(add(path[i], path[i + 1]), 0.5);
    // triangles, not quads: along a tight bend a ring-to-ring quad can twist enough to self-intersect
    for (const tri of [[0, 1, 2], [0, 2, 3]]) part.face(tri.map(k => ids[k]), sub(mid, axis), 'side', mat(i));
  }
  for (const [i, sgn] of [[0, -1], [path.length - 1, 1]]) {
    const c = part.vtx(path[i]); tex.set(c, [U(arc[i]), V(sides / 2)]);
    for (let j = 0; j < sides; j++) part.face([c, rings[i][j], rings[i][j + 1]], mul(T[i], sgn), 'cap', capMat);
  }
  return tex;
}
/** The whole tube part as one UV island (its strip), for paint(). */
const tubeIsland = (part, band, tex) => ({part, faces: part.f.map(f => ({f, n: norm(newell(f.ids.map(id => part.v[id])))})), px: band.x, py: band.y, W: band.w, H: band.h, tex});
const tubeFaceUV = (part, tex) => new Map(part.f.map(f => [f, f.ids.map(id => tex.get(id))]));

/**
 * Tungsten filament: a five-turn coil on two support leads, one continuous wire swept as a 6-sided tube. Thick enough
 * (0.84 px) to stay visible in the inventory. The tube gets its own UV: one strip along the wire (uniform material).
 */
function tungstenFilament() {
  const R = 1.35, yc = 5.8, x0 = -3.4, x1 = 3.4, turns = 5, perTurn = 12, lx = 4.2;
  const path = [[-lx, 0, 0], [-lx, 2.0, 0], [-lx, 4.0, 0]];
  const coil0 = path.length;
  for (let i = 0; i <= turns * perTurn; i++) {
    const t = Math.PI + 2 * Math.PI * i / perTurn, k = i / (turns * perTurn);
    path.push([x0 + (x1 - x0) * k, yc + R * Math.cos(t), R * Math.sin(t)]);
  }
  const coil1 = path.length - 1;
  path.push([lx, 4.0, 0], [lx, 2.0, 0], [lx, 0, 0]);
  const isLead = path.map((_, i) => i < coil0 || i > coil1);
  const wire = new Part('wire', 'tungsten_filament', 'tungsten'), band = {x: PAD, y: PAD, w: ATLAS - 2 * PAD, h: 14};
  const tex = sweepTube(wire, path, {sides: 6, radius: () => 0.42, mat: i => isLead[i] && isLead[i + 1] ? 'moly' : 'tungsten', capMat: 'moly', band});
  return {PARTS: [wire], MATS: {
    tungsten: {c: [178, 179, 184], hl: 6, sm: 150, se: 170, f0: 255},  // drawn tungsten wire
    moly:     {c: [150, 150, 147], hl: 6, sm: 128, se: 150, f0: 255},  // molybdenum support leads
  }, icon: {coverage: 0.3, view: [15, 180, 0]}, uv: {islands: [tubeIsland(wire, band, tex)], S: 8, uvOf: (is, id) => is.tex.get(id), faceUV: new Map([[wire, tubeFaceUV(wire, tex)]])}};
}

/** True when point p lies inside the closed mesh of part (ray parity along a fixed skew direction). */
function insidePart(part, p) {
  const d = norm([0.31, 0.83, 0.47]); let hits = 0;
  for (const f of part.f) for (let q = 1; q + 1 < f.ids.length; q++) {
    const A = part.v[f.ids[0]], B = part.v[f.ids[q]], C = part.v[f.ids[q + 1]], e1 = sub(B, A), e2 = sub(C, A), h = cross(d, e2), det = dot(e1, h);
    if (Math.abs(det) < 1e-12) continue;
    const s = sub(p, A), u = dot(s, h) / det; if (u < 0 || u > 1) continue;
    const qv = cross(s, e1), v = dot(d, qv) / det; if (v < 0 || u + v > 1) continue;
    if (dot(e2, qv) / det > 1e-6) hits++;
  }
  return hits % 2 === 1;
}
/** No vertex or edge midpoint of one part may lie inside another part (the pile pieces only touch or float apart). */
function assertNoPenetration(parts, id) {
  for (const a of parts) for (const b of parts) if (a !== b) {
    const pts = [...a.v, ...a.f.flatMap(f => f.ids.map((v, k) => mul(add(a.v[v], a.v[f.ids[(k + 1) % f.ids.length]]), 0.5)))];
    const bad = pts.find(p => insidePart(b, p));
    if (bad) throw new Error(`${id}: ${a.name} penetrates ${b.name} at ${bad.map(v => v.toFixed(2))}`);
  }
}

/**
 * Steel scrap: a small pile of torn sheet fragments in one weathered-steel family (references: scrap yards / stamping
 * scrap): two flat fragments on the ground (the large one punched with two holes), one leaning on the large one's front
 * edge, a small shard lying on top, and a short bent rebar across the two flat ones.
 * PBR per piece, no rust patches: dull bare sheet steel (LabPBR iron, rough), one rust-filmed fragment and the rebar
 * (desaturated rust, rough dielectric), one mill-scale fragment; sheared and punched edges a little brighter.
 * All tones stay within a narrow grey range so the pile reads as one material.
 */
function steelScrap() {
  const TH = 0.45, zx = L => L.map(([x, z]) => [z, x]);
  // a flat fragment from a convex plan outline (x, z), optional punched holes, then placed by the given transforms
  const sheet = (name, mat, edgeMat, plan, holes, fns) => {
    const p = new Part(name, 'steel_scrap', mat);
    extrude(p, 'y', {outer: orient(zx(plan), true), holes: holes.map(([x, z]) => orient(circle(z, x, 0.55, 10), false))}, 0, TH, 0);
    for (const f of p.f) if (f.tag === 'side' || f.tag === 'wall') f.mat = edgeMat;
    transform(p, ...fns);
    return p;
  };
  const restOn = (p, y) => { const low = Math.min(...p.v.map(q => q[1])); transform(p, move([0, y - low, 0])); };

  const big = sheet('sheet_big', 'plate', 'edge', [[-3.0, -2.0], [0.2, -2.4], [2.9, -1.6], [3.1, 0.9], [2.2, 2.2], [-1.2, 2.4], [-3.2, 1.0]],
    [[-1.4, 0.4], [0.7, -0.2]], [rotY(8)]);
  const rusty = sheet('sheet_rusty', 'rustSheet', 'rustSheet', [[-1.8, -1.2], [1.6, -1.5], [2.0, 0.4], [0.6, 1.4], [-1.6, 1.1]], [],
    [rotY(-20), move([-5.4, 0, -0.4])]);
  // leaning fragment: tilted 34 degrees about its long axis, low edge on the ground in front, rising toward the big one
  const lean = sheet('sheet_lean', 'scale', 'edge', [[-2.2, -1.0], [1.8, -1.3], [2.3, 0.6], [0.4, 1.1], [-2.0, 0.8]], [],
    [rotX(-34), rotY(12), move([0.6, 0, -3.3])]);
  restOn(lean, 0);
  const shard = sheet('sheet_shard', 'plate', 'edge', [[-1.2, -0.8], [1.3, -0.6], [0.2, 1.0]], [], [rotY(30), move([1.6, 0, 0.3])]);
  restOn(shard, TH + 0.02);

  // short rebar lying across the two flat fragments (one bend, rounded by a quadratic fillet), low ribs every 0.9 px
  const RB = 0.45, RIB = 0.51, dense = [];
  {
    const y = TH + RIB + 0.01, P0 = [-6.3, y, 0.5], B = [-2.4, y, 1.0], P1 = [0.2, y, 2.0];
    const line = (a, b) => { const n = Math.ceil(Math.hypot(...sub(b, a)) / 0.02); return Array.from({length: n}, (_, i) => add(a, mul(sub(b, a), i / n))); };
    const pts = [...line(P0, B), ...line(B, P1), P1], F = 0.8;
    const ia = pts.findIndex(p => Math.hypot(...sub(p, B)) < F), ib = pts.length - 1 - [...pts].reverse().findIndex(p => Math.hypot(...sub(p, B)) < F);
    const A = pts[ia - 1], C = pts[ib + 1];
    dense.push(...pts.slice(0, ia));
    for (let i = 0; i <= 24; i++) { const s = i / 24; dense.push(add(add(mul(A, (1 - s) ** 2), mul(B, 2 * s * (1 - s))), mul(C, s * s))); }
    dense.push(...pts.slice(ib + 2));
  }
  const ds = [0]; for (let i = 1; i < dense.length; i++) ds.push(ds[i - 1] + Math.hypot(...sub(dense[i], dense[i - 1])));
  const at = s => { let i = ds.findIndex(v => v >= s); if (i <= 0) return dense[Math.max(i, 0)]; const k = (s - ds[i - 1]) / (ds[i] - ds[i - 1]); return add(dense[i - 1], mul(sub(dense[i], dense[i - 1]), k)); };
  const total = ds[ds.length - 1], S = [0], R = [RB];
  for (let s = 0.25; s + 0.6 < total - 0.15; s += 0.9) for (const [o, r] of [[0, RB], [0.1, RIB], [0.2, RB], [0.55, RB]]) { S.push(s + o); R.push(r); }
  S.push(total); R.push(RB);
  const rebar = new Part('rebar', 'steel_scrap', 'rust'), band = {x: PAD, y: 236, w: ATLAS - 2 * PAD, h: 14};
  const tex = sweepTube(rebar, S.map(at), {sides: 6, radius: i => R[i], mat: i => R[i] === RIB || R[i + 1] === RIB ? 'rib' : 'rust', capMat: 'rust', band});

  const PILE = [big, rusty, lean, shard, rebar];
  assertNoPenetration(PILE, 'steel_scrap');
  // the sheets unwrap into the top 232 x 232, the rebar strip takes the band below
  const sheets = [big, rusty, lean, shard], UA = unwrap(sheets, {atlas: 232, pad: PAD, startS: 40, stepS: 0.25});
  return {PARTS: PILE, MATS: {
    plate:     {c: [100, 98, 95], hl: 10, sm: 62, se: 88, f0: 230},   // dull bare sheet steel: LabPBR iron, rough
    scale:     {c: [84, 84, 85], hl: 10, sm: 60, se: 84, f0: 30},     // mill-scale fragment: dielectric, matte
    edge:      {c: [122, 119, 115], hl: 8, sm: 92, se: 108, f0: 230}, // sheared / punched edges of the bare pieces
    rustSheet: {c: [98, 90, 85], hl: 4, sm: 40, se: 50, f0: 20},      // rust-filmed fragment: rough dielectric
    rust:      {c: [96, 88, 82], hl: 4, sm: 40, se: 50, f0: 20},      // rebar: desaturated light rust
    rib:       {c: [104, 97, 91], hl: 4, sm: 50, se: 56, f0: 20},     // rib crests, rubbed slightly cleaner
  }, bg: 'plate', icon: {view: [45, 225, 0], mat: {plate: [120, 118, 114], scale: [74, 75, 78], rustSheet: [116, 94, 80], rust: [104, 84, 72], rib: [104, 84, 72], edge: [136, 133, 128]}}, uv: {islands: [...UA.islands, tubeIsland(rebar, band, tex)], S: UA.S,
    uvOf: (is, id) => is.tex ? is.tex.get(id) : UA.uvOf(is, id), faceUV: new Map([...UA.faceUV, [rebar, tubeFaceUV(rebar, tex)]])}};
}

// ---------------------------------------------------------------- bake

const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
const r12 = v => +v.toFixed(12) || 0, r3 = v => +v.toFixed(3) || 0, r6 = v => +v.toFixed(6) || 0;
export const R3 = ([ax, ay, az]) => {   // Minecraft ItemTransform rotation: rotationXYZ, applied to a vector as Rx * Ry * Rz
  const rx = rotX(ax), ry = rotY(ay), rz = deg => { const a = deg * Math.PI / 180, c = Math.cos(a), s = Math.sin(a); return p => [p[0] * c - p[1] * s, p[0] * s + p[1] * c, p[2]]; };
  const z = rz(az); return p => rx(ry(z(p)));
};

/**
 * Inventory icon: the textured mesh rendered flat in the inventory view (user 2026-10-21: "以后游戏内的所有物品都用其3D
 * 模型的平面图"; the item model's GUI perspective shows this picture, the mesh itself is drawn in the world). ICON_SIZE
 * px square, the same framing as the 16 px view (14 of 16 slot pixels), every face sampled from the bake's own Base
 * Color atlas, lit from the top front left (both sides of a face, so open straps and sheets still show), ICON_SS x
 * ICON_SS supersampled and box-filtered down. Glass (atlas alpha below 255) is blended over what is behind it.
 * icon.hide: part names left out of the picture and its fit (straps of worn gear that wrap a body the icon does not show).
 * The old pixel-art options (mat, coverage, xray, seamless, depthSeam) no longer change the picture.
 */
const ICON_SIZE = 64, ICON_SS = 4, ICON_LIGHT = norm([-0.35, 0.85, 0.4]);
export function drawIcon(PARTS, UV, atlasPng, view, centreY) {
  const N = ICON_SIZE * ICON_SS, k = N / 16, R = R3(view.rotation), rot = v => R([v[0], v[1] - centreY, v[2]]), s = view.scale[0], tr = view.translation;
  const atlas = readPng(atlasPng);
  const opaque = [], glass = [];
  for (const p of PARTS) {
    const uvs = UV.faceUV.get(p);
    for (const f of p.f) {
      const P = f.ids.map(i => { const q = rot(p.v[i]).map(v => v * s); return [(8 + q[0] + tr[0]) * k, (8 - q[1] - tr[1]) * k, q[2]]; });
      let n3 = norm(newell(f.ids.map(i => rot(p.v[i]))));
      if (n3[2] < 0) n3 = n3.map(v => -v);                              // both sides: the side the viewer sees
      const shade = 0.58 + 0.5 * Math.max(0, dot(n3, ICON_LIGHT)), u = uvs.get(f);
      for (let q = 1; q + 1 < P.length; q++) {
        const tri = {P: [P[0], P[q], P[q + 1]], T: [u[0], u[q], u[q + 1]], shade};
        (MATS_ALPHA_OF(atlas, tri) < 1 ? glass : opaque).push(tri);
      }
    }
  }
  const col = new Float32Array(N * N * 3), cover = new Uint8Array(N * N), depth = new Float32Array(N * N).fill(-Infinity);
  const texel = (tu, tv) => { const x = Math.min(atlas.w - 1, Math.max(0, Math.floor(tu))), y = Math.min(atlas.w - 1, Math.max(0, Math.floor(tv))), o = (y * atlas.w + x) * atlas.bpp;
    return [atlas.px[o] / 255, atlas.px[o + 1] / 255, atlas.px[o + 2] / 255, atlas.bpp === 4 ? atlas.px[o + 3] / 255 : 1]; };
  const raster = (tri, blend) => {
    const [A, B, C] = tri.P, den = (B[1] - C[1]) * (A[0] - C[0]) + (C[0] - B[0]) * (A[1] - C[1]); if (Math.abs(den) < 1e-12) return;
    const x0 = Math.max(0, Math.floor(Math.min(A[0], B[0], C[0]))), x1 = Math.min(N - 1, Math.ceil(Math.max(A[0], B[0], C[0])));
    const y0 = Math.max(0, Math.floor(Math.min(A[1], B[1], C[1]))), y1 = Math.min(N - 1, Math.ceil(Math.max(A[1], B[1], C[1])));
    for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
      const X = x + 0.5, Y = y + 0.5;
      const l1 = ((B[1] - C[1]) * (X - C[0]) + (C[0] - B[0]) * (Y - C[1])) / den, l2 = ((C[1] - A[1]) * (X - C[0]) + (A[0] - C[0]) * (Y - C[1])) / den, l3 = 1 - l1 - l2;
      if (l1 < 0 || l2 < 0 || l3 < 0) continue;
      const z = l1 * A[2] + l2 * B[2] + l3 * C[2], i = y * N + x;
      if (z <= depth[i]) continue;
      const t = texel(l1 * tri.T[0][0] + l2 * tri.T[1][0] + l3 * tri.T[2][0], l1 * tri.T[0][1] + l2 * tri.T[1][1] + l3 * tri.T[2][1]);
      const c = [t[0] * tri.shade, t[1] * tri.shade, t[2] * tri.shade];
      if (blend) {                                                       // glass over what is behind it (or over nothing)
        const al = cover[i] ? t[3] : 1;
        for (let j = 0; j < 3; j++) col[i * 3 + j] = col[i * 3 + j] * (1 - al) + c[j] * al;
      } else { depth[i] = z; col.set(c, i * 3); }
      cover[i] = 1;
    }
  };
  for (const tri of opaque) raster(tri, false);
  glass.sort((p, q) => (p.P[0][2] + p.P[1][2] + p.P[2][2]) - (q.P[0][2] + q.P[1][2] + q.P[2][2]));   // back to front
  for (const tri of glass) raster(tri, true);
  const out = Buffer.alloc(ICON_SIZE * ICON_SIZE * 4);
  for (let y = 0; y < ICON_SIZE; y++) for (let x = 0; x < ICON_SIZE; x++) {
    let r = 0, g = 0, b = 0, n = 0;
    for (let j = 0; j < ICON_SS; j++) for (let i = 0; i < ICON_SS; i++) {
      const q = (y * ICON_SS + j) * N + x * ICON_SS + i; if (!cover[q]) continue;
      r += col[q * 3]; g += col[q * 3 + 1]; b += col[q * 3 + 2]; n++;
    }
    if (n) out.set([r / n, g / n, b / n].map(v => Math.max(0, Math.min(255, Math.round(v * 255)))).concat(Math.round(255 * n / (ICON_SS * ICON_SS))), (y * ICON_SIZE + x) * 4);
  }
  return png(out, ICON_SIZE, ICON_SIZE);
}
/** A triangle's glass alpha: the atlas alpha at its centroid (glass islands carry their material's alpha, the rest 255). */
function MATS_ALPHA_OF(atlas, tri) {
  const tu = (tri.T[0][0] + tri.T[1][0] + tri.T[2][0]) / 3, tv = (tri.T[0][1] + tri.T[1][1] + tri.T[2][1]) / 3;
  const x = Math.min(atlas.w - 1, Math.max(0, Math.floor(tu))), y = Math.min(atlas.w - 1, Math.max(0, Math.floor(tv)));
  return atlas.bpp === 4 ? atlas.px[(y * atlas.w + x) * 4 + 3] / 255 : 1;
}

// inventory icon view: vanilla block-item angle, no roll; an item may pick its own (icon.view), still without roll
const GUI = [30, 225, 0];

/**
 * display: per-context ItemTransforms replacing the size rule below (a long thin tool is held like a vanilla handheld).
 * A material with alpha (0..255) is glass: its islands get that Base Color alpha and every part made only of such
 * materials goes to the mesh's translucent layer (AflHybridMeshRendering); the icon draws it through iconOpts.xray.
 * atlas: atlas size in texels (default 256; big worn gear such as backpacks takes 512 to keep the texel density).
 * meshOnly: no item model and no icon (a mesh drawn only by code, e.g. a strap worn on the body).
 * textures: [Base Color, _s, _n] PNG buffers of the atlas, ready made (a photo sprite, Rock R1a's field stone); nothing is
 * painted, so uv (faceUV per part and face, S texels per px) must map the faces onto them.
 * texelsPerPx: the auto unwrap's starting density (default 40; a printed surface such as a book cover asks for more).
 */
export function bake(id, {PARTS, MATS, uv, bg, icon: iconOpts = {}, display: displayOverrides = {}, atlas: ATLAS = ATLAS_DEFAULT, meshOnly = false, textures = null, texelsPerPx = 40}) {
  const gui = iconOpts.view ?? GUI;
  // centre on X / Z, rest on y = 0
  const all = PARTS.flatMap(p => p.v), lo = [0, 1, 2].map(k => Math.min(...all.map(q => q[k]))), hi = [0, 1, 2].map(k => Math.max(...all.map(q => q[k])));
  const shift = [-(lo[0] + hi[0]) / 2, -lo[1], -(lo[2] + hi[2]) / 2];
  for (const p of PARTS) transform(p, move(shift));
  const size = sub(hi, lo);

  const UV = uv ?? unwrap(PARTS, {atlas: ATLAS, pad: PAD, startS: texelsPerPx, stepS: 0.25});
  for (const p of PARTS) assert(p.f.every(f => UV.faceUV.get(p)?.has(f)), 'unmapped face in ' + id + '/' + p.name);
  const main = MATS[bg ?? PARTS[0].f[0].mat];
  const painted = textures ? {PNG: textures} : paint({PARTS, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas: ATLAS, pad: PAD, MATS, ZONED: new Set(), groupInfo: new Map(),
    sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [...main.c, 255], s: [main.sm, main.f0, 0, 255], n: [128, 128, 255, 255]}});

  const glassMats = new Set(Object.keys(MATS).filter(m => MATS[m].alpha !== undefined));
  let PNGS = painted.PNG;
  if (glassMats.size) {
    const base = readPng(painted.PNG[0]);
    assert(base.bpp === 4 && base.w === ATLAS, 'atlas format');
    for (const is of UV.islands) { const m = is.faces[0].f.mat; if (!glassMats.has(m)) continue;
      for (let y = is.py - PAD; y < is.py + is.H + PAD; y++) for (let x = is.px - PAD; x < is.px + is.W + PAD; x++) base.px[(y * ATLAS + x) * 4 + 3] = MATS[m].alpha; }
    PNGS = [png(base.px, ATLAS, ATLAS), painted.PNG[1], painted.PNG[2]];
  }
  const LAYERS = Object.fromEntries(PARTS.filter(p => p.f.length && p.f.every(f => glassMats.has(f.mat))).map(p => [p.name, 'translucent']));
  const uuid = s => { const h = createHash('sha256').update(`afl-material-mesh-${id}:` + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
  const elements = PARTS.map(p => {
    const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {}, uvs = UV.faceUV.get(p);
    p.v.forEach((q, i) => { vertices[key(i)] = q.map(r12); });
    p.f.forEach((f, fi) => { const u = uvs.get(f); faces['f' + key(fi)] = {uv: Object.fromEntries(f.ids.map((vid, j) => [key(vid), u[j].map(r12)])), vertices: f.ids.map(key), texture: 0}; });
    return {name: p.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: true, locked: false,
      render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: uuid('mesh:' + p.name)};
  });
  const atlasName = id + '_mesh';
  const source = {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: id, model_identifier: '', visible_box: [1, 1, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: ATLAS, height: ATLAS},
    elements, groups: [{name: id, uuid: uuid('group'), export: true, locked: false, scope: 0, selected: false, visibility: true,
      _static: {properties: {}, temp_data: {}}, origin: [0, 0, 0], rotation: [0, 0, 0], color: 0, children: [], reset: false, shade: true,
      mirror_uv: false, autouv: 0, isOpen: true, primary_selected: false}],
    outliner: [{uuid: uuid('group'), isOpen: true, children: elements.map(e => e.uuid)}],
    textures: [{name: atlasName + '.png', relative_path: `textures/${atlasName}.png`, folder: '', namespace: '', id: '0', group: '', scope: 0,
      width: ATLAS, height: ATLAS, uv_width: ATLAS, uv_height: ATLAS, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
      file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
      frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture'),
      source: 'data:image/png;base64,' + PNGS[0].toString('base64')}],
    animations: []};
  const geo = {format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: 'geometry.' + id, texture_width: ATLAS, texture_height: ATLAS,
    visible_bounds_width: 2, visible_bounds_height: 2, visible_bounds_offset: [0, 0.5, 0]}, bones: [{name: id, pivot: [0, 0, 0]}]}]};
  const sidecar = Object.keys(LAYERS).length ? convert(source, geo, {}, id + '.bbmodel', 2, null, LAYERS) : convert(source, geo, {}, id + '.bbmodel', 2);

  // inventory view fitted to 14 of 16 slot pixels from the projected mesh (used to draw the icon render); the world views share
  // one size rule (longest side normalised to 12 px) so every material is held at the same apparent size
  const iconParts = PARTS.filter(p => !(iconOpts.hide ?? []).includes(p.name));
  const centred = iconParts.flatMap(p => p.v).map(q => [q[0], q[1] - size[1] / 2, q[2]]), rot = R3(gui);
  const pr = centred.map(rot), px = pr.map(q => q[0]), py = pr.map(q => q[1]);
  const w = Math.max(...px) - Math.min(...px), h = Math.max(...py) - Math.min(...py), s = r3(14 / Math.max(w, h));
  const cx = (Math.max(...px) + Math.min(...px)) / 2, cy = (Math.max(...py) + Math.min(...py)) / 2, k = 12 / Math.max(...size);
  const S3 = v => [r3(v), r3(v), r3(v)];
  // third person: a vanilla block's turn, moved so the mesh's point nearest the hand lands where the hand closes on the
  // wiring wrench's grip (item frame [0, -0.9, 1.37] px, accepted in game; docs/models/equipment_meshes_v1.md
  // "手持适配规则"): a vanilla block's fixed [0, 2.5, 0] left flat and small meshes 0.2-0.6 px in front of the fist
  // (user 2026-10-21, with the body's hand taking the item looking down)
  const tpS = 0.42 * k, tpR = R3([75, 45, 0]);
  const near = PARTS.flatMap(p => p.v).map(q => tpR([q[0] * tpS, (q[1] - size[1] / 2) * tpS, q[2] * tpS])).reduce((a, b) => b[1] < a[1] ? b : a);
  const display = {
    thirdperson_righthand: {rotation: [75, 45, 0], translation: [0, r3(-0.9 - near[1]), r3(1.37 - near[2])], scale: S3(tpS)},
    firstperson_righthand: {rotation: [0, 45, 0], translation: [0, 1, 0], scale: S3(0.5 * k)},
    ground: {rotation: [0, 0, 0], translation: [0, 3, 0], scale: S3(0.4 * k)},
    fixed: {rotation: [0, 0, 0], translation: [0, 0, 0], scale: S3(0.7 * k)},
    ...displayOverrides,
  };
  const iconView = {rotation: gui, translation: [r3(-s * cx), r3(-s * cy), 0], scale: S3(s)};
  // inventory: the flat 2D icon; hand, ground and item frame: the mesh (forge:separate_transforms, base = builtin/entity)
  const icon = `apocalypse_firstlight:item/${id}`;
  // icon.live: the mesh in the inventory too, in the icon's view (its renderer draws moving parts there, like a vanilla
  // compass); the 2D icon is still written (particles, wiki)
  const itemModel = iconOpts.live
    ? {parent: 'builtin/entity', gui_light: 'front', textures: {particle: icon}, display: {...display, gui: iconView}}
    : {loader: 'forge:separate_transforms', gui_light: 'front', textures: {particle: icon},
      base: {parent: 'builtin/entity', textures: {particle: icon}, display},
      perspectives: {gui: {parent: 'minecraft:item/generated', textures: {layer0: icon}}}};
  const offset = r6(0.5 - size[1] / 32);

  const outputs = [
    [path.join(BB, id + '.bbmodel'), JSON.stringify(source)],
    [path.join(ASSETS, `geo/${id}.geo.json`), JSON.stringify(geo, null, 2) + '\n'],
    [path.join(ASSETS, `meshes/${id}.aflmesh.json`), serializeCompact(sidecar)],
    // meshOnly: a mesh drawn by code only (no item: no item model, no icon)
    ...(meshOnly ? [] : [[path.join(ASSETS, `models/item/${id}.json`), JSON.stringify(itemModel, null, 2) + '\n'],
      [path.join(ASSETS, `textures/item/${id}.png`), drawIcon(iconParts, UV, PNGS[0], iconView, size[1] / 2)]]),
    ...['', '_s', '_n'].flatMap((x, i) => [[path.join(BB, `textures/${atlasName}${x}.png`), PNGS[i]], [path.join(ASSETS, `textures/item/${atlasName}${x}.png`), PNGS[i]]]),
  ];
  const zf = zFightLevels(PARTS, new Map());
  const stats = {id, triangles: sidecar.parts.flatMap(p => p.faces).reduce((t, q) => t + q.length - 2, 0), size: size.map(r3), texelsPerPx: r3(UV.S),
    verticalOffset: offset, iconView, coplanar: zf.unresolved.length};
  return {outputs, stats};
}

/** Refractory ceramic (2026-10-05): one straight high-alumina firebrick, warm off-white, matte, chamfered edges. */
function refractoryCeramic() {
  const brick = new Part('brick', 'refractory_ceramic', 'ceramic');
  extrude(brick, 'y', {outer: orient([[-2.7, -5.5], [2.7, -5.5], [2.7, 5.5], [-2.7, 5.5]], true), holes: []}, 0, 3.2, 0.3);
  return {PARTS: [brick], MATS: {
    ceramic: {c: [194, 183, 160], hl: 6, sm: 46, se: 70, f0: 20},     // fired high-alumina refractory: dielectric, matte
  }};
}

/** High-purity quartz sand (2026-10-05): a small, slightly uneven heap of washed white sand. */
function highPurityQuartzSand() {
  const pile = new Part('pile', 'high_purity_quartz_sand', 'sand');
  const A = 24, R = 5.4, H = 3.0, wob = a => 1 + 0.07 * Math.sin(3 * a + 0.6) + 0.04 * Math.sin(5 * a + 2.1);
  const rings = [0, 0.2, 0.4, 0.6, 0.8].map(t => Array.from({length: A}, (_, i) => { const a = 2 * Math.PI * i / A, r = R * (1 - t) * wob(a);
    return pile.vtx([r * Math.cos(a), H * (1 - Math.pow(1 - t, 1.6)), r * Math.sin(a)]); }));
  const out = id => { const q = pile.v[id]; return [q[0], 0.6, q[2]]; };
  for (let k = 0; k + 1 < rings.length; k++) for (let i = 0; i < A; i++) { const j = (i + 1) % A;
    pile.face([rings[k][i], rings[k][j], rings[k + 1][j], rings[k + 1][i]], out(rings[k][i])); }
  const apex = pile.vtx([0, H, 0]), last = rings[rings.length - 1], base = pile.vtx([0, 0, 0]);
  for (let i = 0; i < A; i++) { pile.face([last[i], last[(i + 1) % A], apex], [0, 1, 0]); pile.face([rings[0][i], rings[0][(i + 1) % A], base], [0, -1, 0], 'cap'); }
  return {PARTS: [pile], MATS: {
    sand: {c: [208, 204, 192], hl: 4, sm: 40, se: 60, f0: 20},        // washed quartz sand: dielectric, matte
  }};
}

/**
 * Insulated copper wire (Building Wiring V1, 2026-10-13): a hank of house wire lying flat, two and a half turns of an
 * off-white PVC-jacketed cable (references: NM-B house cable coils), both ends leaving the coil tangentially with the copper
 * bared for 0.6 px. One item is 1 m of any circuit's wire (the circuit colours live in the wiring view), so the jacket
 * stays neutral. One continuous 12-sided tube with its own UV strip, as the tungsten filament. The icon looks from 50
 * degrees with seams where a turn passes in front of another: thinner wire with more turns smeared into a mottled ring
 * at 16 px (variants compared 2026-10-13).
 */
function insulatedCopperWire({Rc = 4.0, r = 0.75, core = 0.4, pitch = 1.62, turns = 2.5, sides = 12} = {}) {
  const per = 20, lead = 2.4, bare = 0.6, step = 0.06, y0 = r;
  const helix = Array.from({length: turns * per + 1}, (_, i) => { const a = 2 * Math.PI * i / per; return [Rc * Math.cos(a), y0 + pitch * i / per, Rc * Math.sin(a)]; });
  // a lead runs on along the tangent at an end of the helix: d = the outward tangent, k from the coil to the bared tip
  const leadOut = (from, d) => [lead - bare - step, lead - bare, lead].map(k => add(from, mul(d, k)));
  const first = helix[0], last = helix[helix.length - 1];
  const head = leadOut(first, [0, 0, -1]).reverse(), tail = leadOut(last, [0, 0, -1]);   // both tangents point to -z there
  const path = [...head, ...helix, ...tail], n = path.length;
  // rings: tip (core), jacket end (core), jacket (r) ... jacket (r), jacket end (core), tip (core)
  const radius = i => i <= 1 || i >= n - 2 ? core : r;
  const mat = i => i === 0 || i === n - 2 ? 'copper' : 'jacket';
  const wire = new Part('wire', 'insulated_copper_wire', 'jacket'), band = {x: PAD, y: PAD, w: ATLAS - 2 * PAD, h: 14};
  const tex = sweepTube(wire, path, {sides, radius, mat, capMat: 'copper', band});
  return {PARTS: [wire], MATS: {
    jacket: {c: [200, 194, 180], hl: 4, sm: 92, se: 104, f0: 20},     // off-white PVC jacket: dielectric, satin
    copper: {c: [182, 112, 74], hl: 8, sm: 140, se: 160, f0: 234},    // bare copper conductor (LabPBR copper)
  }, icon: {coverage: 0.35, view: [50, 225, 0], depthSeam: 0.8, mat: {jacket: [226, 220, 204], copper: [214, 128, 76]}},
  uv: {islands: [tubeIsland(wire, band, tex)], S: 8, uvOf: (is, id) => is.tex.get(id), faceUV: new Map([[wire, tubeFaceUV(wire, tex)]])}};
}

export const ITEMS = {
  steel_billet: steelBillet,
  lead_brick: leadBrick,
  electrolytic_nickel: electrolyticNickel,
  tungsten_filament: tungstenFilament,
  cemented_carbide_blank: cementedCarbideBlank,
  steel_scrap: steelScrap,
  refractory_ceramic: refractoryCeramic,
  high_purity_quartz_sand: highPurityQuartzSand,
  insulated_copper_wire: insulatedCopperWire,
};

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  const check = process.argv.includes('--check');
  let stale = 0;
  for (const [id, build] of Object.entries(ITEMS)) {
    const {outputs, stats} = bake(id, build());
    console.log(JSON.stringify(stats));
    for (const [file, data] of outputs) {
      const buf = Buffer.isBuffer(data) ? data : Buffer.from(data);
      if (check) { if (!fs.existsSync(file) || !fs.readFileSync(file).equals(buf)) { console.log('stale ' + path.relative(ROOT, file)); stale++; } }
      else { fs.mkdirSync(path.dirname(file), {recursive: true}); fs.writeFileSync(file, buf); }
    }
  }
  if (check) { if (stale) process.exit(1); console.log('CHECK OK'); }
}
