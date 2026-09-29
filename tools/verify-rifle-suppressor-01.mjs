// Offline checks for the 7.62x51mm Rifle Suppressor V1 (rifle_suppressor_01) and its BR51 integration.
//   node tools/verify-rifle-suppressor-01.mjs
// Mesh validity (NaN, UV range, closed outward-facing parts), texture set, exit anchor, compatibility data (BR51 yes,
// HR55 no, C.A.T. no slot, accepts vs rated_ammo / mount_interface), the rifle_fh_qd assembly of every gun that accepts
// it (muzzle device inside the QD cavity, seating shoulder, no other gun geometry inside the suppressor envelope, e.g.
// handguard, front sight, barrel), registry ID / lang, P9 suppressor unchanged. Uses the shipped runtime files only (geo + AFL mesh sidecars + JSON), no Minecraft.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {CAVITY, R, Y} from './build-rifle-suppressor-01.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const A = path.join(root, 'src/main/resources/assets/apocalypse_firstlight'), D = path.join(root, 'src/main/resources/data/apocalypse_firstlight');
const read = p => JSON.parse(fs.readFileSync(p, 'utf8').replace(/^﻿/, ''));
const pngSize = p => { const b = fs.readFileSync(p); return [b.readUInt32BE(16), b.readUInt32BE(20)]; };
const report = {};

// ---- suppressor asset ----
const sup = read(path.join(A, 'meshes/rifle_suppressor_01.aflmesh.json'));
const supGeo = read(path.join(A, 'geo/rifle_suppressor_01.geo.json'))['minecraft:geometry'][0];
assert.equal(sup.format_version, 2, 'V2 sidecar');
assert.deepEqual(sup.texture_size, [512, 512]);
assert.deepEqual(supGeo.description.texture_width, 512);
let nan = 0, uvOut = 0; const faces = sup.parts.flatMap(p => p.faces);
// parts meet on shared rings (mount | collar | tube | cap), so closure and orientation are checked on the whole device
let vol = 0; const E = new Map(), key = v => v.slice(0, 3).map(x => x.toFixed(6)).join(',');
for (const p of sup.parts) {
  for (const v of p.vertices) { if (v.some(x => !Number.isFinite(x))) nan++; if (v[3] < 0 || v[3] > 1 || v[4] < 0 || v[4] > 1) uvOut++; }
  for (const f of p.faces) { const P = f.map(i => p.vertices[i]);
    for (let k = 1; k + 1 < P.length; k++) { const [a, b, c] = [P[0], P[k], P[k + 1]]; vol += (a[0] * (b[1] * c[2] - b[2] * c[1]) - a[1] * (b[0] * c[2] - b[2] * c[0]) + a[2] * (b[0] * c[1] - b[1] * c[0])) / 6; }
    for (let k = 0; k < P.length; k++) { const e = key(P[k]) + '|' + key(P[(k + 1) % P.length]); E.set(e, (E.get(e) || 0) + 1); } }
}
assert(vol > 0, 'inward winding');
for (const e of E.keys()) { const [a, b] = e.split('|'); assert(E.has(b + '|' + a), 'open edge ' + e); }
assert.equal(nan, 0, 'NaN'); assert.equal(uvOut, 0, 'UV range');
const tri = faces.reduce((s, f) => s + f.length - 2, 0);
report.mesh = {parts: sup.parts.map(p => p.name), triangleEquivalent: tri, quads: faces.filter(f => f.length === 4).length,
  triangles: faces.filter(f => f.length === 3).length, vertexSubmissions: faces.length * 4};
assert(tri <= 1400, 'over the soft budget');
const maps = ['', '_s', '_n'].map(k => pngSize(path.join(A, `textures/item/rifle_suppressor_01${k}.png`)));
for (const m of maps) assert.deepEqual(m, [512, 512], 'texture size');
report.textures = maps.map(m => m.join('x'));
// exit anchor = front-face bore centre (model space: origin = rear mounting face, forward -Z)
const V = sup.parts.flatMap(p => p.vertices.map(v => v.slice(0, 3).map(x => x * 16)));
const zFront = Math.min(...V.map(v => v[2])), zRear = Math.max(...V.map(v => v[2]));
const exit = supGeo.bones.find(b => b.name === 'muzzle_exit_anchor');
assert.equal(exit.parent, 'rifle_suppressor_root');
assert(Math.abs(exit.pivot[2] - zFront) < 1e-6 && Math.abs(exit.pivot[0]) < 1e-9 && Math.abs(exit.pivot[1]) < 1e-9, 'exit anchor not at the front bore centre');
assert(Math.abs(zRear) < 1e-6, 'origin not on the rear mounting face');
report.exit = exit.pivot;

// ---- compatibility data ----
const data = read(path.join(D, 'native_attachments/rifle_suppressor_01.json'));
assert.equal(data.noise_multiplier, 0.05); assert.equal(data.rated_ammo, 'apocalypse_firstlight:762x51mm_round'); assert.equal(data.mount_interface, 'rifle_fh_qd');
const guns = Object.fromEntries(fs.readdirSync(path.join(D, 'native_guns')).map(f => [f.replace('.json', ''), read(path.join(D, 'native_guns', f))]));
const ID = 'apocalypse_firstlight:rifle_suppressor_01';
const accepts = g => g.muzzle_slot?.accepts ?? [];
report.compatibility = Object.fromEntries(Object.entries(guns).map(([k, g]) => [k, accepts(g).includes(ID)]));
assert.equal(report.compatibility.br51_01, true, 'BR51 must accept');
assert.equal(report.compatibility.hr55, false, 'HR55 must not accept');
assert(!guns.cat.muzzle_slot, 'C.A.T. muzzle slot changed');
for (const [k, g] of Object.entries(guns)) if (accepts(g).includes(ID)) {   // same rule as NativeMuzzleMount.parse
  assert.equal(g.ammo, data.rated_ammo, k + ' calibre'); assert.equal(g.muzzle_slot.mount_interface, data.mount_interface, k + ' interface');
}

// ---- rifle_fh_qd assembly: every gun that accepts the suppressor, no gun-specific names ----
// Interface contract (rifle_fh_qd): the gun's muzzle_slot anchor sits on the bore axis at the mounting shoulder, forward
// = -Z; everything of the gun ahead of the shoulder and within the suppressor envelope must fit the QD cavity (radius
// CAVITY.r, depth CAVITY.depth), and the gun must present a shoulder wider than the cavity just behind the anchor.
// Bind pose with the geo's static bone rotations (Blockbench convention: geo [-rx, -ry, rz], pivot [-x, y, z]).
const DEG = Math.PI / 180;
const mul = (a, b) => { const r = new Array(16).fill(0); for (let i = 0; i < 4; i++) for (let j = 0; j < 4; j++) for (let k = 0; k < 4; k++) r[i * 4 + j] += a[i * 4 + k] * b[k * 4 + j]; return r; };
const T = v => [1, 0, 0, v[0], 0, 1, 0, v[1], 0, 0, 1, v[2], 0, 0, 0, 1];
const Rot = e => { const [x, y, z] = e.map(d => d * DEG), c = Math.cos, s = Math.sin;
  return mul([c(z), -s(z), 0, 0, s(z), c(z), 0, 0, 0, 0, 1, 0, 0, 0, 0, 1], mul([c(y), 0, s(y), 0, 0, 1, 0, 0, -s(y), 0, c(y), 0, 0, 0, 0, 1], [1, 0, 0, 0, 0, c(x), -s(x), 0, 0, s(x), c(x), 0, 0, 0, 0, 1])); };
const pt = (m, p) => [0, 1, 2].map(i => m[i * 4] * p[0] + m[i * 4 + 1] * p[1] + m[i * 4 + 2] * p[2] + m[i * 4 + 3]);
function bindPose(bones) {
  const by = new Map(bones.map(b => [b.name, b])), M = new Map();
  const get = n => { if (M.has(n)) return M.get(n); const b = by.get(n), pv = [-b.pivot[0] || 0, b.pivot[1], b.pivot[2]];
    const r = b.rotation ? [-b.rotation[0], -b.rotation[1], b.rotation[2]] : [0, 0, 0];
    const m = mul(b.parent ? get(b.parent) : T([0, 0, 0]), mul(T(pv), mul(Rot(r), T(pv.map(v => -v))))); M.set(n, m); return m; };
  return {matrix: get, pivot: n => { const b = by.get(n); return [-b.pivot[0] || 0, b.pivot[1], b.pivot[2]]; }};
}
const envelope = Math.max(R.collar, R.tube) + 0.02;
report.assembly = {};
for (const [gunId, g] of Object.entries(guns)) {
  if (!accepts(g).includes(ID)) continue;
  const meshFile = path.join(A, `meshes/${gunId}.aflmesh.json`);
  assert(fs.existsSync(meshFile), `${gunId}: rifle_fh_qd assembly check needs a Pure Mesh gun (no sidecar)`);
  const mesh = read(meshFile), bones = read(path.join(A, `geo/${gunId}.geo.json`))['minecraft:geometry'][0].bones, pose = bindPose(bones);
  const anchor = pt(pose.matrix(g.muzzle_slot.anchor), pose.pivot(g.muzzle_slot.anchor));
  // suppressor frame: y = distance forward of the seat, r = radius from the bore axis (anchor bone assumed axis-aligned)
  const local = v => ({y: anchor[2] - v[2], r: Math.hypot(v[0] - anchor[0], v[1] - anchor[1])});
  const collide = {}, inCavity = {}; let depth = 0, radius = 0, shoulder = 0;
  for (const p of mesh.parts) {
    if (/^(reload_|empty_old_)/.test(p.bone)) continue;   // reload / dropped magazine copies are hidden outside reloads
    const m = pose.matrix(p.bone), pv = pose.pivot(p.bone);
    for (const v of p.vertices) {
      const q = local(pt(m, [v[0] * 16 + pv[0], v[1] * 16 + pv[1], v[2] * 16 + pv[2]]));
      if (q.y <= 1e-4 && q.y > -0.8 && q.r < envelope) shoulder = Math.max(shoulder, q.r);
      if (q.y <= 1e-4 || q.y > Y.front + 0.02 || q.r >= envelope) continue;
      if (q.y < CAVITY.depth - 0.05 && q.r < CAVITY.r - 0.02) { inCavity[p.name] = (inCavity[p.name] || 0) + 1; depth = Math.max(depth, q.y); radius = Math.max(radius, q.r); }
      else collide[p.name] = (collide[p.name] || 0) + 1;
    }
  }
  assert.deepEqual(collide, {}, `${gunId}: geometry intersects the suppressor (${JSON.stringify(collide)})`);
  assert(shoulder > CAVITY.r, `${gunId}: no mounting shoulder wider than the cavity behind the anchor`);
  report.assembly[gunId] = {muzzleAnchor: anchor.map(v => +v.toFixed(5)), insideCavity: inCavity, deviceDepth: +depth.toFixed(4), deviceRadius: +radius.toFixed(4),
    shoulderRadius: +shoulder.toFixed(4), cavity: CAVITY, mountedExit: [anchor[0], anchor[1], +(anchor[2] - Y.front).toFixed(5)]};
}

// ---- registry / lang / P9 suppressor untouched ----
const items = fs.readFileSync(path.join(root, 'src/main/java/com/antaurora/apofirstlight/registry/AflItems.java'), 'utf8');
assert(/ITEMS\.register\("rifle_suppressor_01",\s*com\.antaurora\.apofirstlight\.weapon\.NativeSuppressorItem::new\)/.test(items), 'registry ID');
assert.equal(read(path.join(A, 'lang/zh_cn.json'))['item.apocalypse_firstlight.rifle_suppressor_01'], '7.62×51mm 步枪消音器');
assert.equal(read(path.join(A, 'lang/en_us.json'))['item.apocalypse_firstlight.rifle_suppressor_01'], '7.62×51mm Rifle Suppressor');
const changed = execFileSync('git', ['status', '--porcelain', '--', 'src/main/resources/assets/apocalypse_firstlight/geo/pistol_suppressor_01.geo.json',
  'src/main/resources/assets/apocalypse_firstlight/meshes/pistol_suppressor_01.aflmesh.json', 'src/main/resources/assets/apocalypse_firstlight/textures/item/pistol_suppressor_01.png',
  'src/main/blockbench/pistol_suppressor_01.bbmodel', 'src/main/resources/data/apocalypse_firstlight/native_guns/p9_01.json'], {cwd: root, encoding: 'utf8'}).trim();
assert.equal(changed, '', 'P9 suppressor files changed: ' + changed);
assert(!fs.existsSync(path.join(D, 'native_attachments/pistol_suppressor_01.json')), 'P9 suppressor keeps the code default');
console.log(JSON.stringify(report, null, 1));
console.log('RIFLE_SUPPRESSOR_V1_OFFLINE_PASS');
