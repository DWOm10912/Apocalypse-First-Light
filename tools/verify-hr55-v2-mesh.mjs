// Offline checks for HR55 V2 Pure Mesh + PBR (Phase 2) and its dynamic 12.7x55mm top rounds.
//   node tools/verify-hr55-v2-mesh.mjs      -> HR55_V2_OFFLINE_PASS
// Uses the shipped runtime files, the generator (up to date, z-fighting cleared) and the Phase 1 reference at git REF:
// rig (bones unchanged but the moved muzzle_anchor, + the two reload round bones), animations (Phase 1 + the reload_empty right-hand grip blend),
// sidecar sanity and budget, reload copy, maps (1024, no warm pixels, LabPBR channels), silhouette against the Phase 1
// cubes (orthographic XOR), an independent coplanar-overlap audit of the shipped sidecar, the dynamic ammo placement
// against the Phase 1 cube rounds, and the reload_empty right-hand contacts (magazine floors, charging-handle knob).
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {collectGroups, zFightLevels, M4} from './cube-slab-mesh-lib.mjs';
import {REF, zFight, stats, RELOAD_EMPTY_RIGHT_HAND, MUZZLE_ANCHOR} from './build-hr55-v2-mesh.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const A = path.join(root, 'src/main/resources/assets/apocalypse_firstlight'), D = path.join(root, 'src/main/resources/data/apocalypse_firstlight');
const read = p => JSON.parse(fs.readFileSync(p, 'utf8').replace(/^﻿/, ''));
const git = p => execFileSync('git', ['show', `${REF}:${p}`], {cwd: root, encoding: 'utf8', maxBuffer: 1 << 28});
const report = {};

// ---- generator ----
execFileSync(process.execPath, [path.join(root, 'tools/build-hr55-v2-mesh.mjs'), '--check'], {cwd: root, stdio: 'pipe'});
assert.equal(zFight.passes.at(-1).pairs, 0, 'generator left z-fighting');
report.generator = {check: 'OK', zFightPasses: zFight.passes, maxGrow: zFight.maxGrow, texelsPerUnit: stats.texelsPerUnit, islands: stats.islands};

// ---- rig: Phase 1 bones unchanged, + reload_bullet1 / reload_bullet2; animations byte-identical ----
const geo = read(path.join(A, 'geo/hr55.geo.json'))['minecraft:geometry'][0], geo0 = JSON.parse(git('src/main/resources/assets/apocalypse_firstlight/geo/hr55.geo.json'))['minecraft:geometry'][0];
assert.equal(geo.description.texture_width, 1024); assert.equal(geo.description.texture_height, 1024);
assert(geo.bones.every(b => !b.cubes), 'runtime geo still has cubes');
const strip = b => JSON.stringify({name: b.name, parent: b.parent ?? null, pivot: b.pivot, rotation: b.rotation ?? null});
// muzzle_anchor moved to the heavy_brake_qd mounting point (2026-09-29); every other Phase 1 bone is unchanged
assert.deepEqual(geo.bones.find(b => b.name === 'muzzle_anchor').pivot, [-MUZZLE_ANCHOR[0] || 0, MUZZLE_ANCHOR[1], MUZZLE_ANCHOR[2]], 'muzzle_anchor');
const phase1Bones = geo0.bones.map(b => b.name === 'muzzle_anchor' ? {...b, pivot: [-MUZZLE_ANCHOR[0] || 0, MUZZLE_ANCHOR[1], MUZZLE_ANCHOR[2]]} : b);
assert.deepEqual(geo.bones.filter(b => !/^reload_bullet\d$/.test(b.name)).map(strip), phase1Bones.map(strip), 'Phase 1 bones changed');
for (const [n, like] of [['reload_bullet1', 'bullet1'], ['reload_bullet2', 'bullet2']]) {
  const b = geo.bones.find(q => q.name === n); assert(b, n); assert.equal(b.parent, 'reload_mag_standard'); assert.deepEqual(b.pivot, geo0.bones.find(q => q.name === like).pivot);
}
// animations: Phase 1 except reload_empty's right_hand_anchor position (the grip offset blends out while the right hand works)
const anims = read(path.join(A, 'animations/hr55.animation.json')), anims0 = JSON.parse(git('src/main/resources/assets/apocalypse_firstlight/animations/hr55.animation.json'));
{
  const a = structuredClone(anims), rh = a.animations.reload_empty.bones.right_hand_anchor;
  assert.deepEqual(rh.position, Object.fromEntries(RELOAD_EMPTY_RIGHT_HAND.map(([t, y]) => [t ? String(t) : '0.0', {vector: [0, y, 0]}])));
  rh.position = anims0.animations.reload_empty.bones.right_hand_anchor.position;
  assert.deepEqual(a, anims0, 'animations changed beyond reload_empty right_hand_anchor.position');
}
report.rig = {bones: geo.bones.length, added: ['reload_bullet1', 'reload_bullet2'], animations: 'Phase 1 + reload_empty right_hand_anchor position'};

const pv = b => [-b.pivot[0] || 0, b.pivot[1], b.pivot[2]];   // Blockbench convention: geo pivot [-x, y, z]
// ---- sidecar: parts, reload copy, NaN / UV, budget ----
const mesh = read(path.join(A, 'meshes/hr55.aflmesh.json'));
assert.equal(mesh.format_version, 2); assert.deepEqual(mesh.texture_size, [1024, 1024]);
const byBone = new Map(geo.bones.map(b => [b.name, b]));
let nan = 0, uvOut = 0;
for (const p of mesh.parts) { assert(byBone.has(p.bone), 'bone ' + p.bone); for (const v of p.vertices) { if (v.some(x => !Number.isFinite(x))) nan++; if (v[3] < 0 || v[3] > 1 || v[4] < 0 || v[4] > 1) uvOut++; } }
assert.equal(nan, 0); assert.equal(uvOut, 0);
assert(!mesh.parts.some(p => /^bullet/.test(p.bone)), 'visible ammunition must be dynamic, not mesh');
const mag = mesh.parts.find(p => p.name === 'mag_standard'), copy = mesh.parts.find(p => p.name === 'reload_mag_standard');
// the copy's bone pivot differs by 0.00005, so V2 may split a different near-planar quad: compare model-space corners + UVs
{
  const ms = (p, v) => { const q = pv(byBone.get(p.bone)); return [v[0] * 16 + q[0], v[1] * 16 + q[1], v[2] * 16 + q[2], v[3], v[4]]; };
  assert.equal(copy.vertices.length, mag.vertices.length);
  copy.vertices.forEach((v, i) => { const a = ms(copy, v), b = ms(mag, mag.vertices[i]); assert(a.every((x, k) => Math.abs(x - b[k]) < 1e-4), 'reload copy vertex ' + i); });
}
const tri = parts => parts.flatMap(p => p.faces).reduce((s, f) => s + f.length - 2, 0), idle = tri(mesh.parts.filter(p => !/^reload_/.test(p.bone)));
assert(idle <= 9000, 'idle over the soft budget: ' + idle); assert(tri(mesh.parts) <= 10000, 'sidecar over the hard budget');
report.mesh = {parts: mesh.parts.length, idleTriangleEquivalent: idle, allTriangleEquivalent: tri(mesh.parts)};

// ---- maps ----
function png(file) {
  const b = fs.readFileSync(file); let p = 8, w, h, id = [];
  while (p < b.length) { const l = b.readUInt32BE(p), t = b.toString('ascii', p + 4, p + 8), d = b.subarray(p + 8, p + 8 + l); if (t === 'IHDR') { w = d.readUInt32BE(0); h = d.readUInt32BE(4); assert.equal(d[9], 6); } if (t === 'IDAT') id.push(d); p += 12 + l; }
  const raw = zlib.inflateSync(Buffer.concat(id)), st = w * 4, px = Buffer.alloc(h * st);
  for (let y = 0; y < h; y++) { const f = raw[y * (st + 1)], r = raw.subarray(y * (st + 1) + 1, (y + 1) * (st + 1));
    for (let i = 0; i < st; i++) { const a = i >= 4 ? px[y * st + i - 4] : 0, bb = y ? px[(y - 1) * st + i] : 0, c = i >= 4 && y ? px[(y - 1) * st + i - 4] : 0; let v = r[i];
      if (f === 1) v += a; else if (f === 2) v += bb; else if (f === 3) v += (a + bb) >> 1; else if (f === 4) { const pp = a + bb - c, pa = Math.abs(pp - a), pb = Math.abs(pp - bb), pc = Math.abs(pp - c); v += pa <= pb && pa <= pc ? a : pb <= pc ? bb : c; }
      px[y * st + i] = v & 255; } }
  return {w, h, px};
}
const base = png(path.join(A, 'textures/item/hr55.png')), spec = png(path.join(A, 'textures/item/hr55_s.png')), nrm = png(path.join(A, 'textures/item/hr55_n.png'));
for (const m of [base, spec, nrm]) assert.deepEqual([m.w, m.h], [1024, 1024]);
let warm = 0; const f0 = {}; let flat = true;
for (let i = 0; i < 1024 * 1024; i++) {
  if (base.px[i * 4] - base.px[i * 4 + 2] > 8) warm++;
  f0[spec.px[i * 4 + 1]] = (f0[spec.px[i * 4 + 1]] || 0) + 1;
  if (nrm.px[i * 4] !== 128 || nrm.px[i * 4 + 1] !== 128) flat = false;
}
assert.equal(warm, 0, 'warm (yellow / gold) pixels in the base colour'); assert(flat, '_n must carry flat normals (AO in blue only)');
assert.deepEqual(Object.keys(f0).map(Number).sort((a, b) => a - b), [10, 24, 255], '_s F0 must be polymer 10 / coating 24 / metal 255');
for (const k of ['', '_s', '_n']) assert(fs.readFileSync(path.join(root, `src/main/blockbench/textures/hr55${k}.png`)).equals(fs.readFileSync(path.join(A, `textures/item/hr55${k}.png`))), 'source map copy ' + k);
report.maps = {size: 1024, warmPixels: warm, f0Share: Object.fromEntries(Object.entries(f0).map(([k, n]) => [k, +(100 * n / 1024 / 1024).toFixed(1)]))};

// ---- model-space geometry (bind pose) ----
const hidden = n => { for (let b = byBone.get(n); b; b = byBone.get(b.parent)) if (/^reload_/.test(b.name)) return true; return false; };
const meshTris = [];   // [[p0, p1, p2]] of the visible (idle) mesh, model space
const faceList = [];
for (const p of mesh.parts) {
  if (hidden(p.bone)) continue; const q = pv(byBone.get(p.bone)), V = p.vertices.map(v => [v[0] * 16 + q[0], v[1] * 16 + q[1], v[2] * 16 + q[2]]);
  for (const f of p.faces) { faceList.push(f.map(i => V[i])); for (let k = 1; k + 1 < f.length; k++) meshTris.push([V[f[0]], V[f[k]], V[f[k + 1]]]); }
}
const ref = JSON.parse(git('src/main/blockbench/hr55.bbmodel')), gi = collectGroups(ref, new Set(['reload_magazine']));
const cubeTris = [];
for (const [name, info] of gi) {
  if (info.hidden || /^(bullet|reload_)/.test(name)) continue;
  for (const c of info.cubes) { const C = c.corners; for (const q of [[0, 2, 3, 1], [4, 5, 7, 6], [0, 1, 5, 4], [2, 6, 7, 3], [0, 4, 6, 2], [1, 3, 7, 5]]) cubeTris.push([C[q[0]], C[q[1]], C[q[2]]], [C[q[0]], C[q[2]], C[q[3]]]); }
}

// ---- silhouette: orthographic coverage masks, 40 px / unit ----
function mask(tris, ax) {
  const [u, v] = {x: [2, 1], y: [2, 0], z: [0, 1]}[ax], PX = 40, U0 = -24, V0 = -4, W = 48 * PX, H = 28 * PX, m = new Uint8Array(W * H);
  for (const t of tris) {
    const P = t.map(p => [(p[u] - U0) * PX, (p[v] - V0) * PX]), den = (P[1][1] - P[2][1]) * (P[0][0] - P[2][0]) + (P[2][0] - P[1][0]) * (P[0][1] - P[2][1]);
    if (Math.abs(den) < 1e-12) continue;
    const x0 = Math.max(0, Math.floor(Math.min(...P.map(p => p[0])))), x1 = Math.min(W - 1, Math.ceil(Math.max(...P.map(p => p[0]))));
    const y0 = Math.max(0, Math.floor(Math.min(...P.map(p => p[1])))), y1 = Math.min(H - 1, Math.ceil(Math.max(...P.map(p => p[1]))));
    for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
      const X = x + 0.5, Y = y + 0.5, l1 = ((P[1][1] - P[2][1]) * (X - P[2][0]) + (P[2][0] - P[1][0]) * (Y - P[2][1])) / den, l2 = ((P[2][1] - P[0][1]) * (X - P[2][0]) + (P[0][0] - P[2][0]) * (Y - P[2][1])) / den;
      if (l1 >= 0 && l2 >= 0 && l1 + l2 <= 1) m[y * W + x] = 1;
    }
  }
  return {m, W, H};
}
const erode = ({m, W, H}, r) => { const o = new Uint8Array(m.length); for (let y = r; y < H - r; y++) for (let x = r; x < W - r; x++) { let ok = 1; for (let dy = -r; dy <= r && ok; dy++) for (let dx = -r; dx <= r; dx++) if (!m[(y + dy) * W + x + dx]) { ok = 0; break; } o[y * W + x] = ok; } return o; };
report.silhouette = {};
for (const ax of ['x', 'y', 'z']) {
  const a = mask(cubeTris, ax), b = mask(meshTris, ax);
  let xor = 0, area = 0; for (let i = 0; i < a.m.length; i++) { area += a.m[i]; xor += a.m[i] !== b.m[i]; }
  // structural difference: pixels that differ even after a 2 px erosion of both masks (bevels and GROW live in the rim)
  const ea = erode(a, 2), eb = erode(b, 2); let core = 0; for (let i = 0; i < ea.length; i++) if (ea[i] !== eb[i] && (ea[i] ? !b.m[i] : !a.m[i])) core++;
  report.silhouette[ax] = {xorPercent: +(100 * xor / area).toFixed(2), structuralPx: core};
  assert(xor / area < 0.03, `silhouette ${ax} differs by ${(100 * xor / area).toFixed(2)}%`);
  assert(core < 60, `silhouette ${ax}: ${core} structural pixels`);
}

// ---- independent coplanar-overlap audit of the shipped sidecar (every face its own key) ----
{
  const parts = [{name: 'hr55', f: faceList.map((P, i) => ({ids: P.map((_, k) => i * 8 + k), slab: 'f' + i})), v: []}];
  faceList.forEach((P, i) => P.forEach((q, k) => { parts[0].v[i * 8 + k] = q; }));
  const z = zFightLevels(parts, new Map());
  report.zFight = {overlappingPairs: z.unresolved.length, area: +z.area.toFixed(4)};
  assert.equal(z.unresolved.length, 0, 'coplanar overlapping faces in the shipped sidecar');
}

// ---- dynamic ammo: config, placement against the Phase 1 cube rounds, fit ----
const gun = read(path.join(D, 'native_guns/hr55.json')), vis = gun.presentation.magazine_round_visual;
assert.deepEqual(vis.anchor, ['bullet1', 'bullet2']); assert.deepEqual(vis.loaded_auxiliary_anchor, ['reload_bullet1', 'reload_bullet2']);
assert.equal(vis.geometry, 'apocalypse_firstlight:geo/12_7x55mm_round.geo.json'); assert.equal(vis.texture, 'apocalypse_firstlight:textures/item/12_7x55mm_ammo_v1.png');
const roundGeo = read(path.join(A, 'geo/12_7x55mm_round.geo.json')), roundMesh = read(path.join(A, 'meshes/12_7x55mm_round.aflmesh.json'));
const rv = roundMesh.parts.flatMap(p => p.vertices.map(v => v.slice(0, 3).map(x => x * 16))), rh = Math.max(...rv.map(v => v[1])), rr = Math.max(...rv.map(v => Math.hypot(v[0], v[2])));
assert(vis.local_rotation[0] === -90 && !vis.local_rotation[1] && !vis.local_rotation[2], 'rounds point along -Z (muzzle) from the unrotated anchors');
report.ammo = {roundLength: +rh.toFixed(4), roundRadius: +rr.toFixed(4), anchors: {}};
const rounds = [];
for (const n of ['bullet1', 'bullet2']) {
  const b = byBone.get(n); assert(!b.rotation, n + ' must stay unrotated');
  const p = pv(b), baseZ = p[2] + vis.local_offset[2], tipZ = baseZ - rh;
  const cubes = gi.get(n).cubes.flatMap(c => c.corners), cz = [Math.min(...cubes.map(q => q[2])), Math.max(...cubes.map(q => q[2]))];
  const cx = (Math.min(...cubes.map(q => q[0])) + Math.max(...cubes.map(q => q[0]))) / 2, cy = (Math.min(...cubes.map(q => q[1])) + Math.max(...cubes.map(q => q[1]))) / 2;
  assert(Math.abs(baseZ - cz[1]) < 0.005 && Math.abs(tipZ - cz[0]) < 0.005, `${n}: round ${tipZ}..${baseZ} vs Phase 1 cube round ${cz}`);
  assert(Math.abs(p[0] + vis.local_offset[0] - cx) < 0.005 && Math.abs(p[1] + vis.local_offset[1] - cy) < 0.005, `${n}: round axis off the Phase 1 round axis`);
  rounds.push([p[0], p[1]]); report.ammo.anchors[n] = {axis: [p[0], p[1]], base: +baseZ.toFixed(5), tip: +tipZ.toFixed(5)};
}
const gap = Math.hypot(rounds[0][0] - rounds[1][0], rounds[0][1] - rounds[1][1]);
assert(gap > 2 * rr, 'the two top rounds intersect'); report.ammo.centreDistance = +gap.toFixed(4);
// inside the magazine's outer envelope (x, top) — the rounds sit in the feed lips
const magV = mag.vertices.map(v => { const q = pv(byBone.get(mag.bone)); return [v[0] * 16 + q[0], v[1] * 16 + q[1], v[2] * 16 + q[2]]; });
const mx = Math.max(...magV.map(v => Math.abs(v[0]))), mtop = Math.max(...magV.map(v => v[1]));
for (const [x, y] of rounds) { assert(Math.abs(x) + rr < mx, 'round outside the magazine width'); assert(y + rr < mtop, 'round above the magazine top'); }
report.ammo.magazineEnvelope = {halfWidth: +mx.toFixed(4), top: +mtop.toFixed(4)};

// ---- reload_empty right-hand contacts (Blockbench convention: pivots / positions (-x, y, z), rotations (-rx, -ry, rz)) ----
{
  const clip = anims.animations.reload_empty, vec = v => Array.isArray(v) ? v.map(Number) : typeof v === 'number' ? [v, v, v] : vec(v.vector ?? v.post ?? v.pre);
  const sample = (ch, t, rest) => { if (!ch) return rest; if (Array.isArray(ch) || ch.vector !== undefined) return vec(ch);
    const k = Object.entries(ch).map(([x, v]) => [+x, vec(v)]).sort((a, b) => a[0] - b[0]); if (t <= k[0][0]) return k[0][1]; if (t >= k.at(-1)[0]) return k.at(-1)[1];
    const i = k.findIndex((q, j) => t >= q[0] && t < k[j + 1][0]), u = (t - k[i][0]) / (k[i + 1][0] - k[i][0]); return k[i][1].map((x, j) => x + (k[i + 1][1][j] - x) * u); };
  const P = v => [-v[0] || 0, v[1], v[2]], Rt = v => [-v[0] || 0, -v[1] || 0, v[2]], S = v => [v[0], 0, 0, 0, 0, v[1], 0, 0, 0, 0, v[2], 0, 0, 0, 0, 1];
  const world = (t, n) => { if (!n) return M4.I(); const b = byBone.get(n), ch = clip.bones[n] || {}, p = P(b.pivot), rot = sample(ch.rotation, t, [0, 0, 0]);
    return M4.mul(world(t, b.parent), [M4.T(P(sample(ch.position, t, [0, 0, 0]))), M4.T(p), M4.R(Rt((b.rotation || [0, 0, 0]).map((v, i) => v + rot[i]))),
      S(sample(ch.scale, t, [1, 1, 1])), M4.T(p.map(v => -v))].reduce(M4.mul)); };
  const hand = t => M4.pt(world(t, 'right_hand_anchor'), P(byBone.get('right_hand_anchor').pivot));
  const dist = (a, b) => Math.hypot(a[0] - b[0], a[1] - b[1], a[2] - b[2]);
  const worst = (t0, t1, bone, point) => { let m = 0; for (let t = t0; t <= t1 + 1e-9; t += 1 / 48) m = Math.max(m, dist(hand(t), M4.pt(world(t, bone), point))); return +m.toFixed(2); };
  const c = {oldMagazineFloor: worst(0.33, 0.75, 'mag_standard', [0, 0.4, 5.2]), newMagazineFloor: worst(1.5, 2.33, 'reload_mag_standard', [0, 0.4, 5.2]),
    chargingHandleKnob: worst(2.8, 3.13, 'bone2', [1.18, 9.8, -6.7])};
  assert(c.oldMagazineFloor < 3 && c.newMagazineFloor < 4.5 && c.chargingHandleKnob < 4, 'reload_empty right hand off its contacts: ' + JSON.stringify(c));
  const inGun = t => M4.pt(M4.inv(world(t, 'gun_body')), hand(t));   // gun_body carries no scale: the rigid inverse is exact
  assert(dist(inGun(0), inGun(clip.animation_length)) < 0.01, 'reload_empty must start and end on the idle right hand');
  report.reloadEmptyRightHand = {maxContactDistance: c, blend: RELOAD_EMPTY_RIGHT_HAND};
}

console.log(JSON.stringify(report, null, 1));
console.log('HR55_V2_OFFLINE_PASS');
