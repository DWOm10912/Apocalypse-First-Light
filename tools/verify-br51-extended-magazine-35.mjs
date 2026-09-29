// Offline checks for the BR51-01 35-Round Extended Magazine V2 (br51_extended_magazine_35).
//   node tools/verify-br51-extended-magazine-35.mjs
// Asset (V2 sidecar, 512 maps, NaN / UV, parts, budget, silhouette = the V1 cube footprint), style (materials and texel
// density identical to the BR51 V2 standard magazine), mount / animation contract (replaced bones share one pivot, the
// dynamic top-round bones are not inside any replaced subtree), top compatibility with the BR51 V2 standard magazine
// (feed-lip height and upper envelope, top-round anchors inside the lips) and idle-pose clearance of the extra length
// against the rest of the gun. Uses shipped runtime files only (plus the two generators' exports), no Minecraft.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';
import * as MAG from './build-br51-extended-magazine-35-v2.mjs';
import * as BR51 from './build-br51-01-v2-mesh.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const A = path.join(root, 'src/main/resources/assets/apocalypse_firstlight'), D = path.join(root, 'src/main/resources/data/apocalypse_firstlight');
const read = p => JSON.parse(fs.readFileSync(p, 'utf8').replace(/^﻿/, ''));
const pngSize = p => { const b = fs.readFileSync(p); return [b.readUInt32BE(16), b.readUInt32BE(20)]; };
const report = {};

// ---- asset ----
const mesh = read(path.join(A, 'meshes/br51_extended_magazine_35.aflmesh.json')), geo = read(path.join(A, 'geo/br51_extended_magazine_35.geo.json'))['minecraft:geometry'][0];
assert.equal(mesh.format_version, 2); assert.deepEqual(mesh.texture_size, [512, 512]);
for (const k of ['', '_s', '_n']) assert.deepEqual(pngSize(path.join(A, `textures/item/br51_extended_magazine_35${k}.png`)), [512, 512], 'map ' + k);
assert.deepEqual(geo.bones.map(b => [b.name, b.pivot]), [['br51_extended_magazine_35_root', [0, 0, 0]]], 'single root bone at the mount pivot');
assert.deepEqual(mesh.parts.map(p => p.name), ['mag_body', 'mag_floorplate', 'mag_id_marks']);
let nan = 0, uvOut = 0;
for (const p of mesh.parts) for (const v of p.vertices) { if (v.some(x => !Number.isFinite(x))) nan++; if (v[3] < 0 || v[3] > 1 || v[4] < 0 || v[4] > 1) uvOut++; }
assert.equal(nan, 0, 'NaN'); assert.equal(uvOut, 0, 'UV range');
const faces = mesh.parts.flatMap(p => p.faces), tri = faces.reduce((s, f) => s + f.length - 2, 0);
assert(tri <= 600, 'over budget ' + tri);
const V = mesh.parts.flatMap(p => p.vertices.map(v => v.slice(0, 3).map(x => x * 16)));
const ext = (P, i) => [Math.min(...P.map(q => q[i])), Math.max(...P.map(q => q[i]))];
// silhouette inherited from the V1 cube footprint (chamfers only cut inward)
const cubeCorners = [...MAG.groupInfo.values()].flatMap(g => g.cubes.flatMap(c => c.corners));
for (let i = 0; i < 3; i++) { const m = ext(V, i), c = ext(cubeCorners, i); assert(m[0] >= c[0] - 0.03 && m[1] <= c[1] + 0.03, `bounds axis ${i}: mesh ${m} vs cubes ${c}`); }
report.asset = {triangleEquivalent: tri, quads: faces.filter(f => f.length === 4).length, triangles: faces.filter(f => f.length === 3).length,
  bounds: [0, 1, 2].map(i => ext(V, i).map(v => +v.toFixed(4))), texelsPerUnit: MAG.texelsPerUnit};

// ---- style: same materials and density as the BR51 V2 standard magazine ----
assert.deepEqual(MAG.MATS.magazine, BR51.MATS.magazine, 'magazine material differs from BR51 V2');
assert.deepEqual(MAG.MATS.floorplate, BR51.MATS.floorplate, 'floorplate material differs from BR51 V2');
assert.equal(MAG.texelsPerUnit, BR51.texelsPerUnit, 'texel density differs from BR51 V2');
assert.deepEqual(MAG.MATS.idmark.c, [124, 106, 74], 'identification colour (P9 extended magazine V2 muted tan)');

// ---- mount / animation contract ----
const items = fs.readFileSync(path.join(root, 'src/main/java/com/antaurora/apofirstlight/registry/AflItems.java'), 'utf8');
const reg = items.match(/"br51_extended_magazine_35",\s*\(\)\s*->\s*new com\.antaurora\.apofirstlight\.weapon\.NativeMagazineItem\("br51_01",(\d+),\s*java\.util\.Set\.of\(([^)]*)\),(true|false),([\d.]+)f\)\)/);
assert(reg, 'registration');
const replaced = reg[2].split(',').map(s => s.trim().replace(/"/g, ''));
assert.equal(+reg[1], 35); assert.equal(reg[3], 'true', 'replaces whole subtrees');
const gunGeo = read(path.join(A, 'geo/br51_01.geo.json'))['minecraft:geometry'][0], by = new Map(gunGeo.bones.map(b => [b.name, b]));
const pivots = replaced.map(n => { assert(by.has(n), 'missing bone ' + n); return by.get(n).pivot; });
for (const p of pivots) assert.deepEqual(p, pivots[0], 'replaced bones must share one pivot');
const under = (n, r) => { for (let b = by.get(n); b; b = by.get(b.parent)) if (b.name === r) return true; return false; };
const visual = read(path.join(D, 'native_guns/br51_01.json')).presentation.magazine_round_visual, arr = v => v == null ? [] : Array.isArray(v) ? v : [v];
const roundBones = [...arr(visual.anchor), ...arr(visual.loaded_auxiliary_anchor), ...arr(visual.old_magazine_anchor)];
for (const b of roundBones) for (const r of replaced) assert(!under(b, r), `round bone ${b} is inside replaced subtree ${r}: it would vanish with this magazine`);
report.mount = {replaced, pivot: pivots[0], roundBones};

// ---- top compatibility with the BR51 V2 standard magazine (magazine-local coordinates) ----
const DEG = Math.PI / 180, mul4 = (a, b) => { const r = new Array(16).fill(0); for (let i = 0; i < 4; i++) for (let j = 0; j < 4; j++) for (let k = 0; k < 4; k++) r[i * 4 + j] += a[i * 4 + k] * b[k * 4 + j]; return r; };
const T = v => [1, 0, 0, v[0], 0, 1, 0, v[1], 0, 0, 1, v[2], 0, 0, 0, 1];
const Rot = e => { const [x, y, z] = e.map(d => d * DEG), c = Math.cos, s = Math.sin; return mul4([c(z), -s(z), 0, 0, s(z), c(z), 0, 0, 0, 0, 1, 0, 0, 0, 0, 1], mul4([c(y), 0, s(y), 0, 0, 1, 0, 0, -s(y), 0, c(y), 0, 0, 0, 0, 1], [1, 0, 0, 0, 0, c(x), -s(x), 0, 0, s(x), c(x), 0, 0, 0, 0, 1])); };
const pt = (m, p) => [0, 1, 2].map(i => m[i * 4] * p[0] + m[i * 4 + 1] * p[1] + m[i * 4 + 2] * p[2] + m[i * 4 + 3]);
const piv = n => { const b = by.get(n); return [-b.pivot[0] || 0, b.pivot[1], b.pivot[2]]; };
const M = new Map(), matrix = n => { if (M.has(n)) return M.get(n); const b = by.get(n), p = piv(n), r = b.rotation ? [-b.rotation[0], -b.rotation[1], b.rotation[2]] : [0, 0, 0];
  const m = mul4(b.parent ? matrix(b.parent) : T([0, 0, 0]), mul4(T(p), mul4(Rot(r), T(p.map(v => -v))))); M.set(n, m); return m; };
const magOrigin = pt(matrix(replaced[0]), piv(replaced[0]));
const gunMesh = read(path.join(A, 'meshes/br51_01.aflmesh.json'));
const partPoints = p => { const m = matrix(p.bone), q = piv(p.bone); return p.vertices.map(v => pt(m, [v[0] * 16 + q[0], v[1] * 16 + q[1], v[2] * 16 + q[2]]).map((x, i) => x - magOrigin[i])); };
const std = gunMesh.parts.filter(p => under(p.bone, 'mag_standard')).flatMap(partPoints);
const top = P => P.filter(q => q[1] > 0);   // above the magazine pivot: feed lips and the magwell section
const [sTop, mTop] = [top(std), top(V)];
const topY = [ext(std, 1)[1], ext(V, 1)[1]];
assert(Math.abs(topY[0] - topY[1]) < 0.01, `feed-lip height ${topY}`);
for (const i of [0, 2]) { const s = ext(sTop, i), m = ext(mTop, i); assert(Math.abs(s[0] - m[0]) < 0.03 && Math.abs(s[1] - m[1]) < 0.03, `upper envelope axis ${i}: standard ${s} vs 35 ${m}`); }
const rounds = arr(visual.anchor).map(n => { const q = pt(matrix(n), piv(n)).map((x, i) => x - magOrigin[i]); return {bone: n, local: q.map(v => +v.toFixed(4))}; });
const lipX = ext(mTop, 0), lipZ = ext(mTop, 2);
for (const r of rounds) assert(r.local[0] > lipX[0] && r.local[0] < lipX[1] && r.local[2] > lipZ[0] && r.local[2] < lipZ[1], `round anchor ${r.bone} outside the feed lips`);
report.top = {feedLipY: topY.map(v => +v.toFixed(4)), standardUpperX: ext(sTop, 0).map(v => +v.toFixed(3)), m35UpperX: lipX.map(v => +v.toFixed(3)), roundAnchors: rounds};

// ---- idle-pose clearance: the extra length (below the standard magazine) against the rest of the gun ----
const stdBottom = ext(std, 1)[0], magBones = new Set(['bullet', 'bullet_in_barrel']);
const rest = gunMesh.parts.filter(p => !replaced.some(r => under(p.bone, r)) && !/^(reload_|empty_old_|mag_out)/.test(p.bone) && !magBones.has(p.bone)
  && !['mag_out', 'empty_old_mag', 'reload_magazine'].some(r => under(p.bone, r)));
const extra = V.filter(q => q[1] < stdBottom), bx = ext(extra, 0), bz = ext(extra, 2), by0 = ext(extra, 1);
let hits = 0;
for (const p of rest) for (const q of partPoints(p)) if (q[0] > bx[0] - 0.05 && q[0] < bx[1] + 0.05 && q[2] > bz[0] - 0.05 && q[2] < bz[1] + 0.05 && q[1] > by0[0] - 0.05 && q[1] < stdBottom) hits++;
assert.equal(hits, 0, 'gun geometry inside the extra length');
report.clearance = {standardBottom: +stdBottom.toFixed(4), m35Bottom: +ext(V, 1)[0].toFixed(4), extraLength: +(stdBottom - ext(V, 1)[0]).toFixed(4), gunPointsInside: hits};
console.log(JSON.stringify(report, null, 1));
console.log('BR51_MAG35_V2_OFFLINE_PASS');
