// Offline checks for the Rifle Micro Red Dot V1 (rifle_red_dot_01) and every gun that accepts it.
//   node tools/verify-rifle-red-dot-01.mjs
// Asset (V2 sidecar, 512 maps, lens alone on the translucent layer, lens / aperture bones = the generator's LENS /
// APERTURE, lens rim inside the bore), collimated reticle data (round window), compatibility data (mount_interface
// rifle_optic_rail on the sight and on every accepting slot), and the rifle_optic_rail assembly of every accepting gun in
// bind pose: the slot origin sits on a rail top under the clamp, no gun geometry inside the optic above the rail, a clear
// sight line from the lens back toward the eye, ads_center = the mounted lens centre. Uses the shipped runtime files only.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';
import {AXIS, TUBE, LENS, APERTURE, FOOT} from './build-rifle-red-dot-01.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const A = path.join(root, 'src/main/resources/assets/apocalypse_firstlight'), D = path.join(root, 'src/main/resources/data/apocalypse_firstlight');
const read = p => JSON.parse(fs.readFileSync(p, 'utf8').replace(/^﻿/, ''));
const pngSize = p => { const b = fs.readFileSync(p); return [b.readUInt32BE(16), b.readUInt32BE(20)]; };
const ID = 'apocalypse_firstlight:rifle_red_dot_01', report = {};

// ---- asset ----
const mesh = read(path.join(A, 'meshes/rifle_red_dot_01.aflmesh.json')), geo = read(path.join(A, 'geo/rifle_red_dot_01.geo.json'))['minecraft:geometry'][0];
assert.equal(mesh.format_version, 2); assert.deepEqual(mesh.texture_size, [512, 512]);
for (const k of ['', '_s', '_n']) assert.deepEqual(pngSize(path.join(A, `textures/item/rifle_red_dot_01${k}.png`)), [512, 512], 'map ' + k);
let nan = 0, uvOut = 0;
for (const p of mesh.parts) for (const v of p.vertices) { if (v.some(x => !Number.isFinite(x))) nan++; if (v[3] < 0 || v[3] > 1 || v[4] < 0 || v[4] > 1) uvOut++; }
assert.equal(nan, 0, 'NaN'); assert.equal(uvOut, 0, 'UV range');
assert.deepEqual(mesh.parts.filter(p => p.render_layer === 'translucent').map(p => p.name), ['optic_lens'], 'only the lens is translucent');
const faces = mesh.parts.flatMap(p => p.faces), tri = faces.reduce((s, f) => s + f.length - 2, 0);
assert(tri <= 1400, 'over the soft budget: ' + tri);
report.mesh = {parts: mesh.parts.map(p => p.name + (p.render_layer ? '/' + p.render_layer : '')), triangleEquivalent: tri, quads: faces.filter(f => f.length === 4).length, triangles: faces.filter(f => f.length === 3).length};
const bone = n => geo.bones.find(b => b.name === n);
assert.equal(bone('optic_lens').parent, 'sight_root'); assert.equal(bone('lens_center').parent, 'optic_lens'); assert.equal(bone('lens_aperture').parent, 'lens_center');
const pv = n => { const b = bone(n); return [-b.pivot[0] || 0, b.pivot[1], b.pivot[2]]; };
assert.deepEqual(pv('lens_center'), LENS, 'lens_center'); assert(!bone('lens_center').rotation, 'lens_center must stay unrotated (normal = +Z)');
const half = [0, 1].map(i => Math.abs(pv('lens_aperture')[i] - LENS[i]));
assert(half.every(h => Math.abs(h - APERTURE) < 1e-9) && Math.abs(pv('lens_aperture')[2] - LENS[2]) < 1e-9, 'aperture half extents');
const lens = mesh.parts.find(p => p.name === 'optic_lens'), lensPivot = pv('optic_lens');
for (const v of lens.vertices) {
  const q = v.slice(0, 3).map((x, i) => x * 16 + lensPivot[i]);
  assert(Math.abs(q[2] - LENS[2]) < 1e-6, 'lens not planar at LENS z');
  assert(Math.hypot(q[0], q[1] - AXIS.y) <= TUBE.ri + 0.0051, 'lens rim outside the bore wall');
}
report.optic = {lensCenter: LENS, apertureHalfExtents: half, bore: TUBE.ri};

// ---- reticle + compatibility data ----
const optics = read(path.join(A, 'optics/rifle_red_dot_01.json')).collimated_reticle;
assert.equal(optics.aperture_shape, 'ellipse'); assert.equal(optics.lens_center_bone, 'lens_center'); assert.equal(optics.lens_aperture_bone, 'lens_aperture');
assert(fs.existsSync(path.join(A, 'textures/effects/collimated_reticle_dot.png')), 'reticle texture');
const data = read(path.join(D, 'native_attachments/rifle_red_dot_01.json'));
assert.equal(data.mount_interface, 'rifle_optic_rail');
const guns = Object.fromEntries(fs.readdirSync(path.join(D, 'native_guns')).map(f => [f.replace('.json', ''), read(path.join(D, 'native_guns', f))]));
const accepts = g => g.sight_slot?.accepts ?? [];
report.compatibility = Object.fromEntries(Object.entries(guns).map(([k, g]) => [k, accepts(g).includes(ID)]));
assert.equal(report.compatibility.br51_01, true); assert.equal(report.compatibility.hr55, true);
for (const [k, g] of Object.entries(guns)) if (accepts(g).includes(ID)) assert.equal(g.sight_slot.mount_interface, data.mount_interface, k + ' interface');

// ---- rifle_optic_rail assembly (bind pose; Blockbench convention: geo pivot [-x, y, z], rotation [-rx, -ry, rz]) ----
const DEG = Math.PI / 180;
const mul = (a, b) => { const r = new Array(16).fill(0); for (let i = 0; i < 4; i++) for (let j = 0; j < 4; j++) for (let k = 0; k < 4; k++) r[i * 4 + j] += a[i * 4 + k] * b[k * 4 + j]; return r; };
const T = v => [1, 0, 0, v[0], 0, 1, 0, v[1], 0, 0, 1, v[2], 0, 0, 0, 1];
const Rot = e => { const [x, y, z] = e.map(d => d * DEG), c = Math.cos, s = Math.sin;
  return mul([c(z), -s(z), 0, 0, s(z), c(z), 0, 0, 0, 0, 1, 0, 0, 0, 0, 1], mul([c(y), 0, s(y), 0, 0, 1, 0, 0, -s(y), 0, c(y), 0, 0, 0, 0, 1], [1, 0, 0, 0, 0, c(x), -s(x), 0, 0, s(x), c(x), 0, 0, 0, 0, 1])); };
const pt = (m, p) => [0, 1, 2].map(i => m[i * 4] * p[0] + m[i * 4 + 1] * p[1] + m[i * 4 + 2] * p[2] + m[i * 4 + 3]);
const about = (pivot, rot) => mul(T(pivot), mul(Rot(rot), T(pivot.map(v => -v))));
function gunPoints(gunId) {
  const bones = read(path.join(A, `geo/${gunId}.geo.json`))['minecraft:geometry'][0].bones, by = new Map(bones.map(b => [b.name, b])), M = new Map();
  const pivot = b => [-b.pivot[0] || 0, b.pivot[1], b.pivot[2]], rot = r => r ? [-r[0], -r[1], r[2]] : [0, 0, 0];
  const matrix = n => { if (M.has(n)) return M.get(n); const b = by.get(n); const m = mul(b.parent ? matrix(b.parent) : T([0, 0, 0]), about(pivot(b), rot(b.rotation))); M.set(n, m); return m; };
  const hidden = n => { for (let b = by.get(n); b; b = by.get(b.parent)) if (/^(reload_|empty_old_|mag_out)/.test(b.name)) return true; return false; };
  const out = [], meshFile = path.join(A, `meshes/${gunId}.aflmesh.json`);
  if (fs.existsSync(meshFile)) for (const p of read(meshFile).parts) {
    if (hidden(p.bone)) continue; const m = matrix(p.bone), q = pivot(by.get(p.bone));
    for (const v of p.vertices) out.push(pt(m, [v[0] * 16 + q[0], v[1] * 16 + q[1], v[2] * 16 + q[2]]));
  }
  for (const b of bones) if (!hidden(b.name)) for (const c of b.cubes ?? []) {   // Cube guns: every cube corner (geo origin x is the mirrored max corner)
    const from = [-(c.origin[0] + c.size[0]), c.origin[1], c.origin[2]], to = [from[0] + c.size[0], from[1] + c.size[1], from[2] + c.size[2]];
    const m = mul(matrix(b.name), c.rotation ? about([-c.pivot[0] || 0, c.pivot[1], c.pivot[2]], rot(c.rotation)) : T([0, 0, 0]));
    for (const X of [0, 1]) for (const Y of [0, 1]) for (const Z of [0, 1]) out.push(pt(m, [X ? to[0] : from[0], Y ? to[1] : from[1], Z ? to[2] : from[2]]));
  }
  return {points: out, anchor: n => pt(matrix(n), pivot(by.get(n)))};
}
report.assembly = {};
for (const [gunId, g] of Object.entries(guns)) {
  if (!accepts(g).includes(ID)) continue;
  const s = g.sight_slot, {points, anchor} = gunPoints(gunId);
  const origin = anchor(s.anchor).map((v, i) => v + s.mount_offset[i]), local = p => p.map((v, i) => v - origin[i]);
  // rail top at the origin under the clamp: the highest gun point over the clamp's inner footprint is the origin plane
  let railTop = -Infinity, inside = 0, sightLine = 0;
  for (const q of points.map(local)) {
    const underClamp = Math.abs(q[0]) < 0.4 && Math.abs(q[2]) < FOOT.z - 0.05;
    if (underClamp && q[1] < 0.02 && q[1] > -0.8) railTop = Math.max(railTop, q[1]);
    if (Math.abs(q[0]) < FOOT.x + 0.3 && Math.abs(q[2]) < FOOT.z + 0.05 && q[1] > 0.02 && q[1] < AXIS.y + 1) inside++;
    // sight line: a cylinder of the window radius from the lens plane 6 units back toward the eye
    if (q[2] > LENS[2] && q[2] < LENS[2] + 6 && Math.hypot(q[0], q[1] - AXIS.y) < APERTURE + 0.05) sightLine++;
  }
  assert(Math.abs(railTop) < 0.02, `${gunId}: no rail top at the slot origin (highest point under the clamp ${railTop})`);
  assert.equal(inside, 0, `${gunId}: gun geometry inside the optic / riser envelope`);
  assert.equal(sightLine, 0, `${gunId}: gun geometry in the sight line`);
  const lensCentre = origin.map((v, i) => v + LENS[i]);
  assert(lensCentre.every((v, i) => Math.abs(v - s.ads_center[i]) < 1e-4), `${gunId}: ads_center ${s.ads_center} != mounted lens centre ${lensCentre}`);
  report.assembly[gunId] = {origin: origin.map(v => +v.toFixed(5)), railTopOffset: +railTop.toFixed(4), adsCenter: s.ads_center, mountedLens: lensCentre.map(v => +v.toFixed(5))};
}
console.log(JSON.stringify(report, null, 1));
console.log('RIFLE_RED_DOT_V1_OFFLINE_PASS');
