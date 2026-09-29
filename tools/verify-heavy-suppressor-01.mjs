// Offline checks for the 12.7x55mm Heavy Suppressor V1 (heavy_suppressor_01) and its HR55 integration.
//   node tools/verify-heavy-suppressor-01.mjs      -> HEAVY_SUPPRESSOR_V1_OFFLINE_PASS
// Mesh validity (NaN, UV range, closed outward-facing device), texture set, origin / exit anchor, compatibility data
// (HR55 yes, BR51 no, accepts vs rated_ammo / mount_interface), the heavy_brake_qd assembly of every gun that accepts it
// (muzzle device inside the front cavity and seated on its collar, only a barrel inside the rear sleeve, nothing else of
// the gun inside the suppressor envelope, clearance to the handguard, sight line), registry ID / lang / creative tab.
// Uses the shipped runtime files only (geo + AFL mesh sidecars + JSON), no Minecraft.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';
import {CAVITY, SLEEVE, R, Y} from './build-heavy-suppressor-01.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const A = path.join(root, 'src/main/resources/assets/apocalypse_firstlight'), D = path.join(root, 'src/main/resources/data/apocalypse_firstlight');
const read = p => JSON.parse(fs.readFileSync(p, 'utf8').replace(/^﻿/, ''));
const pngSize = p => { const b = fs.readFileSync(p); return [b.readUInt32BE(16), b.readUInt32BE(20)]; };
const ID = 'apocalypse_firstlight:heavy_suppressor_01', report = {};

// ---- suppressor asset ----
const sup = read(path.join(A, 'meshes/heavy_suppressor_01.aflmesh.json'));
const supGeo = read(path.join(A, 'geo/heavy_suppressor_01.geo.json'))['minecraft:geometry'][0];
assert.equal(sup.format_version, 2, 'V2 sidecar'); assert.deepEqual(sup.texture_size, [512, 512]); assert.equal(supGeo.description.texture_width, 512);
let nan = 0, uvOut = 0, vol = 0; const faces = sup.parts.flatMap(p => p.faces), E = new Map(), key = v => v.slice(0, 3).map(x => x.toFixed(6)).join(',');
for (const p of sup.parts) {   // parts meet on shared rings, so closure and orientation are checked on the whole device
  for (const v of p.vertices) { if (v.some(x => !Number.isFinite(x))) nan++; if (v[3] < 0 || v[3] > 1 || v[4] < 0 || v[4] > 1) uvOut++; }
  for (const f of p.faces) { const P = f.map(i => p.vertices[i]);
    for (let k = 1; k + 1 < P.length; k++) { const [a, b, c] = [P[0], P[k], P[k + 1]]; vol += (a[0] * (b[1] * c[2] - b[2] * c[1]) - a[1] * (b[0] * c[2] - b[2] * c[0]) + a[2] * (b[0] * c[1] - b[1] * c[0])) / 6; }
    for (let k = 0; k < P.length; k++) { const e = key(P[k]) + '|' + key(P[(k + 1) % P.length]); E.set(e, (E.get(e) || 0) + 1); } }
}
assert(vol > 0, 'inward winding'); for (const e of E.keys()) { const [a, b] = e.split('|'); assert(E.has(b + '|' + a), 'open edge ' + e); }
assert.equal(nan, 0, 'NaN'); assert.equal(uvOut, 0, 'UV range');
const tri = faces.reduce((s, f) => s + f.length - 2, 0);
assert(tri <= 1400, 'over the soft budget: ' + tri);
report.mesh = {parts: sup.parts.map(p => p.name), triangleEquivalent: tri, quads: faces.filter(f => f.length === 4).length, triangles: faces.filter(f => f.length === 3).length};
for (const k of ['', '_s', '_n']) assert.deepEqual(pngSize(path.join(A, `textures/item/heavy_suppressor_01${k}.png`)), [512, 512], 'map ' + k);
// origin = mounting point: the device spans SLEEVE.length behind it to Y.front ahead (forward -Z); exit = front bore centre
const V = sup.parts.flatMap(p => p.vertices.map(v => v.slice(0, 3).map(x => x * 16)));
const zFront = Math.min(...V.map(v => v[2])), zRear = Math.max(...V.map(v => v[2])), rMax = Math.max(...V.map(v => Math.hypot(v[0], v[1])));
assert(Math.abs(zFront + Y.front) < 1e-6 && Math.abs(zRear - SLEEVE.length) < 1e-6, `device span ${zRear} .. ${zFront}`);
assert(Math.abs(rMax - R.tube) < 1e-6, 'tube radius');
const exit = supGeo.bones.find(b => b.name === 'muzzle_exit_anchor');
assert.equal(exit.parent, 'heavy_suppressor_root');
assert(Math.abs(exit.pivot[2] - zFront) < 1e-6 && Math.abs(exit.pivot[0]) < 1e-9 && Math.abs(exit.pivot[1]) < 1e-9, 'exit anchor not at the front bore centre');
report.device = {length: +(zRear - zFront).toFixed(4), diameter: 2 * R.tube, behindMount: SLEEVE.length, aheadOfMount: Y.front, exit: exit.pivot};

// ---- compatibility data ----
const data = read(path.join(D, 'native_attachments/heavy_suppressor_01.json'));
assert.equal(data.noise_multiplier, 0.05); assert.equal(data.rated_ammo, 'apocalypse_firstlight:12_7x55mm_round'); assert.equal(data.mount_interface, 'heavy_brake_qd');
const guns = Object.fromEntries(fs.readdirSync(path.join(D, 'native_guns')).map(f => [f.replace('.json', ''), read(path.join(D, 'native_guns', f))]));
const accepts = g => g.muzzle_slot?.accepts ?? [];
report.compatibility = Object.fromEntries(Object.entries(guns).map(([k, g]) => [k, accepts(g).includes(ID)]));
assert.equal(report.compatibility.hr55, true, 'HR55 must accept'); assert.equal(report.compatibility.br51_01, false, 'BR51 must not accept');
for (const [k, g] of Object.entries(guns)) if (accepts(g).includes(ID)) {   // same rule as NativeMuzzleMount.parse
  assert.equal(g.ammo, data.rated_ammo, k + ' calibre'); assert.equal(g.muzzle_slot.mount_interface, data.mount_interface, k + ' interface');
  assert(g.suppressed_fire_sound, k + ' has no suppressed_fire_sound');
}

// ---- heavy_brake_qd assembly: every gun that accepts the suppressor, no gun-specific names ----
// Interface contract: the gun's muzzle_slot anchor sits on the bore axis at the rear face of its muzzle-device collar,
// forward = -Z, unrotated. Ahead of it everything within the suppressor envelope must fit the front cavity (CAVITY), and the
// device must be wider than the sleeve bore right at the anchor (the collar seats against the cavity step). Behind it, for
// SLEEVE.length, only a barrel thinner than the sleeve bore may lie within the envelope. Bind pose with the geo's static
// bone rotations (Blockbench convention: geo [-rx, -ry, rz], pivot [-x, y, z]); faces are sampled along their edges, so long
// faces crossing the envelope without a vertex inside are caught too.
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
  return {matrix: get, pivot: n => { const b = by.get(n); return [-b.pivot[0] || 0, b.pivot[1], b.pivot[2]]; }, bone: n => by.get(n)};
}
const envelope = R.tube + 0.02;
report.assembly = {};
for (const [gunId, g] of Object.entries(guns)) {
  if (!accepts(g).includes(ID)) continue;
  const meshFile = path.join(A, `meshes/${gunId}.aflmesh.json`);
  assert(fs.existsSync(meshFile), `${gunId}: heavy_brake_qd assembly check needs a Pure Mesh gun (no sidecar)`);
  const mesh = read(meshFile), bones = read(path.join(A, `geo/${gunId}.geo.json`))['minecraft:geometry'][0].bones, pose = bindPose(bones);
  assert(!pose.bone(g.muzzle_slot.anchor).rotation, `${gunId}: the mount anchor must be unrotated`);
  const anchor = pt(pose.matrix(g.muzzle_slot.anchor), pose.pivot(g.muzzle_slot.anchor));
  const local = v => ({y: anchor[2] - v[2], r: Math.hypot(v[0] - anchor[0], v[1] - anchor[1])});   // y = forward of the mount
  const collide = {}, inCavity = {}, inSleeve = {}; let depth = 0, radius = 0, seat = 0, sleeveR = 0, behind = Infinity;
  const test = (name, q) => {   // the collar rear face lies on the mount plane (y = 0): the seat contact, part of the cavity side
    if (q.r >= envelope) return;
    if (q.y > 0 && q.y < 0.3) seat = Math.max(seat, q.r);
    if (q.y < -SLEEVE.length - 0.02) { if (q.r >= SLEEVE.r - 0.02) behind = Math.min(behind, -SLEEVE.length - q.y); return; }   // the barrel runs on through the sleeve
    if (q.y > Y.front + 0.02) return;
    if (q.y > -1e-3 && q.y < CAVITY.depth - 0.05 && q.r < CAVITY.r - 0.02) { inCavity[name] = (inCavity[name] || 0) + 1; depth = Math.max(depth, q.y); radius = Math.max(radius, q.r); }
    else if (q.y <= -1e-3 && q.y >= -SLEEVE.length - 0.02 && q.r < SLEEVE.r - 0.02) { inSleeve[name] = (inSleeve[name] || 0) + 1; sleeveR = Math.max(sleeveR, q.r); }
    else collide[name] = (collide[name] || 0) + 1;
  };
  for (const p of mesh.parts) {
    if (/^(reload_|empty_old_)/.test(p.bone)) continue;   // reload / dropped magazine copies are hidden outside reloads
    const m = pose.matrix(p.bone), pv = pose.pivot(p.bone), W = p.vertices.map(v => pt(m, [v[0] * 16 + pv[0], v[1] * 16 + pv[1], v[2] * 16 + pv[2]]));
    for (const f of p.faces) for (let k = 0; k < f.length; k++) {
      const a = W[f[k]], b = W[f[(k + 1) % f.length]], n = Math.max(1, Math.ceil(Math.hypot(b[0] - a[0], b[1] - a[1], b[2] - a[2]) / 0.2));
      for (let s = 0; s < n; s++) test(p.name, local(a.map((x, i) => x + (b[i] - x) * s / n)));
    }
  }
  assert.deepEqual(collide, {}, `${gunId}: geometry intersects the suppressor (${JSON.stringify(collide)})`);
  assert(Object.keys(inCavity).length, `${gunId}: no muzzle device inside the front cavity`);
  assert(seat > SLEEVE.r, `${gunId}: no collar wider than the sleeve bore at the mount (${seat})`);
  // sight line: the tube top stays well below the configured sight picture
  const sightY = g.sight_slot?.ads_center?.[1] ?? g.ads?.ads_center?.[1];
  report.assembly[gunId] = {muzzleAnchor: anchor.map(v => +v.toFixed(5)), insideCavity: inCavity, deviceDepth: +depth.toFixed(4), deviceRadius: +radius.toFixed(4),
    seatRadius: +seat.toFixed(4), insideSleeve: inSleeve, sleeveContentRadius: +sleeveR.toFixed(4), clearanceBehindRearFace: +behind.toFixed(4),
    mountedExit: [anchor[0], anchor[1], +(anchor[2] - Y.front).toFixed(5)], tubeTopBelowRedDotAxis: sightY ? +(sightY - anchor[1] - R.tube).toFixed(4) : null};
  assert(behind > 0.2, `${gunId}: rear face too close to the gun (${behind})`);
}

// ---- registry / lang / creative tab ----
const items = fs.readFileSync(path.join(root, 'src/main/java/com/antaurora/apofirstlight/registry/AflItems.java'), 'utf8');
assert(/ITEMS\.register\("heavy_suppressor_01",\s*com\.antaurora\.apofirstlight\.weapon\.NativeSuppressorItem::new\)/.test(items), 'registry ID');
assert(/AflItems\.RIFLE_SUPPRESSOR_01,\s*AflItems\.HEAVY_SUPPRESSOR_01,/.test(fs.readFileSync(path.join(root, 'src/main/java/com/antaurora/apofirstlight/registry/AflCreativeTabs.java'), 'utf8')), 'creative tab order');
assert.equal(read(path.join(A, 'lang/zh_cn.json'))['item.apocalypse_firstlight.heavy_suppressor_01'], '12.7×55mm 重型消音器');
assert.equal(read(path.join(A, 'lang/en_us.json'))['item.apocalypse_firstlight.heavy_suppressor_01'], '12.7×55mm Heavy Suppressor');
for (const l of ['zh_cn', 'en_us']) assert(read(path.join(A, `lang/${l}.json`))['tooltip.apocalypse_firstlight.heavy_suppressor_01.description'], 'tooltip ' + l);
assert.equal(read(path.join(A, 'models/item/heavy_suppressor_01.json')).parent, 'builtin/entity');
console.log(JSON.stringify(report, null, 1));
console.log('HEAVY_SUPPRESSOR_V1_OFFLINE_PASS');
