// AFL Material Meshes V1: six Material System V1 items as Pure Mesh, drawn by AflStaticMeshItemRenderer in the world views
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
import {Part, extrude, area2, unwrap, paint, png, zFightLevels, add, sub, mul, dot, cross, norm, newell} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const BB = path.join(ROOT, 'src/main/blockbench'), ASSETS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const ATLAS = 256, PAD = 2;
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
const R3 = ([ax, ay, az]) => {   // Minecraft ItemTransform rotation: rotationXYZ, applied to a vector as Rx * Ry * Rz
  const rx = rotX(ax), ry = rotY(ay), rz = deg => { const a = deg * Math.PI / 180, c = Math.cos(a), s = Math.sin(a); return p => [p[0] * c - p[1] * s, p[0] * s + p[1] * c, p[2]]; };
  const z = rz(az); return p => rx(ry(z(p)));
};

/**
 * 16x16 inventory icon drawn from the mesh in the inventory view (the item model's GUI perspective shows this sprite, the
 * mesh is only used in the world): flat tone per face (top brightest, screen-left side lighter than screen-right, like
 * vanilla block icons), each pixel takes the face that covers most of it (no anti-aliasing), a darker seam where one part
 * passes in front of another, then a vanilla rim (darker silhouette pixels at the bottom / right).
 * icon: {mat: {name: other material name or [r, g, b]}} (icon-only colours: pixel art wants more contrast between pieces
 * than the PBR base colours), coverage: fraction of a pixel a part must cover to be drawn (thin wires need less).
 */
function drawIcon(PARTS, MATS, view, centreY, {mat: iconMat = {}, coverage = 0.5} = {}) {
  // centreY: the mesh is drawn centred like the item renderer does (rests on y = 0, offset by half its height)
  const N = 16, SS = 6, R = R3(view.rotation), rot = v => R([v[0], v[1] - centreY, v[2]]), s = view.scale[0], tr = view.translation, tris = [];
  const colourOf = m => { const o = iconMat[m]; return Array.isArray(o) ? o : MATS[o ?? m].c; };
  for (const p of PARTS) for (const f of p.f) {
    const P = f.ids.map(i => { const q = rot(p.v[i]).map(v => v * s); return [8 + q[0] + tr[0], 8 - q[1] - tr[1], q[2]]; });
    const n3 = norm(newell(f.ids.map(i => rot(p.v[i]))));
    if (n3[2] <= 0) continue;                                            // faces turned away are always behind others
    const tone = 0.66 + 0.33 * Math.max(0, n3[1]) - 0.12 * n3[0], c = colourOf(f.mat).map(v => v * tone);
    for (let q = 1; q + 1 < P.length; q++) tris.push({P: [P[0], P[q], P[q + 1]], c, part: p});
  }
  const px = new Array(N * N).fill(null);
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const votes = new Map();
    for (let sy = 0; sy < SS; sy++) for (let sx = 0; sx < SS; sx++) {
      const X = x + (sx + 0.5) / SS, Y = y + (sy + 0.5) / SS; let best = null, bz = -Infinity;
      for (const tri of tris) {
        const [A, B, C] = tri.P, den = (B[1] - C[1]) * (A[0] - C[0]) + (C[0] - B[0]) * (A[1] - C[1]); if (Math.abs(den) < 1e-12) continue;
        const l1 = ((B[1] - C[1]) * (X - C[0]) + (C[0] - B[0]) * (Y - C[1])) / den, l2 = ((C[1] - A[1]) * (X - C[0]) + (A[0] - C[0]) * (Y - C[1])) / den, l3 = 1 - l1 - l2;
        if (l1 < 0 || l2 < 0 || l3 < 0) continue;
        const z = l1 * A[2] + l2 * B[2] + l3 * C[2]; if (z > bz) { bz = z; best = {tri, z}; }
      }
      if (best) { const v = votes.get(best.tri.c) || {n: 0, part: best.tri.part, z: 0}; v.n++; v.z += best.z; votes.set(best.tri.c, v); }
    }
    let win = null, total = 0; for (const [c, v] of votes) { total += v.n; if (!win || v.n > win.v.n) win = {c, v}; }
    if (win && total >= coverage * SS * SS) px[y * N + x] = {c: win.c, part: win.v.part, z: win.v.z / win.v.n};
  }
  const out = Buffer.alloc(N * N * 4), at = (x, y) => x < 0 || y < 0 || x >= N || y >= N ? null : px[y * N + x];
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const p = at(x, y); if (!p) continue;
    let c = p.c;
    if (!at(x + 1, y) || !at(x, y + 1)) c = c.map(v => v * 0.62); else if (!at(x - 1, y) || !at(x, y - 1)) c = c.map(v => v * 0.86);
    else if ([[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dy]) => { const q = at(x + dx, y + dy); return q.part !== p.part && q.z > p.z; })) c = c.map(v => v * 0.74);
    out.set([...c.map(v => Math.max(0, Math.min(255, Math.round(v)))), 255], (y * N + x) * 4);
  }
  return png(out, N, N);
}

// inventory icon view: vanilla block-item angle, no roll; an item may pick its own (icon.view), still without roll
const GUI = [30, 225, 0];

export function bake(id, {PARTS, MATS, uv, bg, icon: iconOpts = {}}) {
  const gui = iconOpts.view ?? GUI;
  // centre on X / Z, rest on y = 0
  const all = PARTS.flatMap(p => p.v), lo = [0, 1, 2].map(k => Math.min(...all.map(q => q[k]))), hi = [0, 1, 2].map(k => Math.max(...all.map(q => q[k])));
  const shift = [-(lo[0] + hi[0]) / 2, -lo[1], -(lo[2] + hi[2]) / 2];
  for (const p of PARTS) transform(p, move(shift));
  const size = sub(hi, lo);

  const UV = uv ?? unwrap(PARTS, {atlas: ATLAS, pad: PAD, startS: 40, stepS: 0.25});
  for (const p of PARTS) assert(p.f.every(f => UV.faceUV.get(p)?.has(f)), 'unmapped face in ' + id + '/' + p.name);
  const main = MATS[bg ?? PARTS[0].f[0].mat];
  const painted = paint({PARTS, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas: ATLAS, pad: PAD, MATS, ZONED: new Set(), groupInfo: new Map(),
    sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [...main.c, 255], s: [main.sm, main.f0, 0, 255], n: [128, 128, 255, 255]}});

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
      source: 'data:image/png;base64,' + painted.PNG[0].toString('base64')}],
    animations: []};
  const geo = {format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: 'geometry.' + id, texture_width: ATLAS, texture_height: ATLAS,
    visible_bounds_width: 2, visible_bounds_height: 2, visible_bounds_offset: [0, 0.5, 0]}, bones: [{name: id, pivot: [0, 0, 0]}]}]};
  const sidecar = convert(source, geo, {}, id + '.bbmodel', 2);

  // inventory view fitted to 14 of 16 slot pixels from the projected mesh (used to draw the 2D icon); the world views share
  // one size rule (longest side normalised to 12 px) so every material is held at the same apparent size
  const centred = PARTS.flatMap(p => p.v).map(q => [q[0], q[1] - size[1] / 2, q[2]]), rot = R3(gui);
  const pr = centred.map(rot), px = pr.map(q => q[0]), py = pr.map(q => q[1]);
  const w = Math.max(...px) - Math.min(...px), h = Math.max(...py) - Math.min(...py), s = r3(14 / Math.max(w, h));
  const cx = (Math.max(...px) + Math.min(...px)) / 2, cy = (Math.max(...py) + Math.min(...py)) / 2, k = 12 / Math.max(...size);
  const S3 = v => [r3(v), r3(v), r3(v)];
  const display = {
    thirdperson_righthand: {rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: S3(0.42 * k)},
    firstperson_righthand: {rotation: [0, 45, 0], translation: [0, 1, 0], scale: S3(0.5 * k)},
    ground: {rotation: [0, 0, 0], translation: [0, 3, 0], scale: S3(0.4 * k)},
    fixed: {rotation: [0, 0, 0], translation: [0, 0, 0], scale: S3(0.7 * k)},
  };
  const iconView = {rotation: gui, translation: [r3(-s * cx), r3(-s * cy), 0], scale: S3(s)};
  // inventory: the flat 2D icon; hand, ground and item frame: the mesh (forge:separate_transforms, base = builtin/entity)
  const icon = `apocalypse_firstlight:item/${id}`;
  const itemModel = {loader: 'forge:separate_transforms', gui_light: 'front', textures: {particle: icon},
    base: {parent: 'builtin/entity', textures: {particle: icon}, display},
    perspectives: {gui: {parent: 'minecraft:item/generated', textures: {layer0: icon}}}};
  const offset = r6(0.5 - size[1] / 32);

  const outputs = [
    [path.join(BB, id + '.bbmodel'), JSON.stringify(source)],
    [path.join(ASSETS, `geo/${id}.geo.json`), JSON.stringify(geo, null, 2) + '\n'],
    [path.join(ASSETS, `meshes/${id}.aflmesh.json`), serializeCompact(sidecar)],
    [path.join(ASSETS, `models/item/${id}.json`), JSON.stringify(itemModel, null, 2) + '\n'],
    [path.join(ASSETS, `textures/item/${id}.png`), drawIcon(PARTS, MATS, iconView, size[1] / 2, iconOpts)],
    ...['', '_s', '_n'].flatMap((x, i) => [[path.join(BB, `textures/${atlasName}${x}.png`), painted.PNG[i]], [path.join(ASSETS, `textures/item/${atlasName}${x}.png`), painted.PNG[i]]]),
  ];
  const zf = zFightLevels(PARTS, new Map());
  const stats = {id, triangles: sidecar.parts.flatMap(p => p.faces).reduce((t, q) => t + q.length - 2, 0), size: size.map(r3), texelsPerPx: r3(UV.S),
    verticalOffset: offset, iconView, coplanar: zf.unresolved.length};
  return {outputs, stats};
}

export const ITEMS = {
  steel_billet: steelBillet,
  lead_brick: leadBrick,
  electrolytic_nickel: electrolyticNickel,
  tungsten_filament: tungstenFilament,
  cemented_carbide_blank: cementedCarbideBlank,
  steel_scrap: steelScrap,
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
