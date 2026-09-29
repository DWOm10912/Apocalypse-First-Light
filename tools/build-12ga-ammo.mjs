// 12 Gauge Visible Ammo V1: the two hand-made 12 Gauge shotshell meshes become the formal round and casing items with a
// light LabPBR set, plus the ejected-casing low-poly FX asset.
//   node tools/build-12ga-ammo.mjs            -> writes the sidecars, the casing geo, the _s / _n maps and the FX asset
//   node tools/build-12ga-ammo.mjs --check    -> verifies every output is up to date
// Editable sources (hand-made, never rewritten here):
//   src/main/blockbench/12ga_hybrid_mesh_prototype.bbmodel   live shell (12_gauge_round): burgundy polymer hull, brass
//       head with rim and primer pocket, recessed steel primer, six-fold star crimp
//   src/main/blockbench/12ga_hybrid_mesh_spent.bbmodel       fired shell (12_gauge_casing): same hull and head, struck
//       primer, opened six-fold mouth with the inner case
// Both share one 256 atlas (textures/item/12_gauge_round_mesh.png, identical to the texture embedded in both sources);
// its Base Color is kept byte for byte. Outputs:
//   - V2 sidecars for both (the sources are mostly quads; V1 had split every face into triangles)
//   - geo/12_gauge_casing.geo.json (the round keeps its geo)
//   - 12_gauge_round_mesh_s / _n: LabPBR per part and face orientation, rasterised on the shared UVs (zones from the shared
//     ammo table tools/ammo-pbr.mjs: polymer hull / crimp / inside dielectric, brass head metal, steel primer metal); the
//     live shell is painted first, the spent shell only fills texels the live one does not use (its opened mouth)
//   - 12_gauge_casing_fx: the ejected-casing low-poly FX (Ejected Casing Low-Poly FX rules: 8 segments, 64 atlas, same
//     height and largest radius as the fired shell, bone 'casing', +Y, base on y = 0), palette sampled from the atlas.
//     Not wired to NativeGunFx: Silverwood 12 does not eject (break action, no extraction FX yet).
// Scale: the item shells are 18.42 / 18.945 units tall (the display transforms scale them); Silverwood's in-gun shells are
// copies of the same design at ~0.11x.
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import {fileURLToPath} from 'node:url';
import {convert, serializeCompact} from './export-afl-mesh.mjs';
import {png} from './cube-slab-mesh-lib.mjs';
import {runLathe, sm, mix, sc} from './lathe-mesh-lib.mjs';
import {ammoPbr, AMMO_PBR} from './ammo-pbr.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const assets = path.join(root, 'src/main/resources/assets/apocalypse_firstlight'), bb = path.join(root, 'src/main/blockbench');
const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url), CHECK = process.argv.includes('--check');
const readJson = p => JSON.parse(fs.readFileSync(p, 'utf8').replace(/^﻿/, ''));
function assert(c, m) { if (!c) throw new Error(m); }
function readPng(buf) {
  let p = 8, w, h, ct, id = [];
  while (p < buf.length) { const l = buf.readUInt32BE(p), t = buf.toString('ascii', p + 4, p + 8), d = buf.subarray(p + 8, p + 8 + l); if (t === 'IHDR') { w = d.readUInt32BE(0); h = d.readUInt32BE(4); ct = d[9]; } if (t === 'IDAT') id.push(d); p += 12 + l; }
  const bpp = ct === 6 ? 4 : 3, raw = zlib.inflateSync(Buffer.concat(id)), st = w * bpp, px = Buffer.alloc(h * st);
  for (let y = 0; y < h; y++) { const f = raw[y * (st + 1)], r = raw.subarray(y * (st + 1) + 1, (y + 1) * (st + 1));
    for (let i = 0; i < st; i++) { const a = i >= bpp ? px[y * st + i - bpp] : 0, b = y ? px[(y - 1) * st + i] : 0, c = i >= bpp && y ? px[(y - 1) * st + i - bpp] : 0; let v = r[i];
      if (f === 1) v += a; else if (f === 2) v += b; else if (f === 3) v += (a + b) >> 1; else if (f === 4) { const pp = a + b - c, pa = Math.abs(pp - a), pb = Math.abs(pp - b), pc = Math.abs(pp - c); v += pa <= pb && pa <= pc ? a : pb <= pc ? b : c; }
      px[y * st + i] = v & 255; } }
  return {w, h, at: (x, y) => [0, 1, 2].map(k => px[(y * w + x) * bpp + k])};
}

// ---------------- sources, atlas ----------------
const LIVE = readJson(path.join(bb, '12ga_hybrid_mesh_prototype.bbmodel')), SPENT = readJson(path.join(bb, '12ga_hybrid_mesh_spent.bbmodel'));
const ATLAS_PATH = path.join(assets, 'textures/item/12_gauge_round_mesh.png'), baseBytes = fs.readFileSync(ATLAS_PATH);
for (const s of [LIVE, SPENT]) assert(Buffer.from(s.textures[0].source.split(',')[1], 'base64').equals(baseBytes), 'embedded texture differs from 12_gauge_round_mesh.png');
const BASE = readPng(baseBytes), N = BASE.w;
assert(N === 256 && BASE.h === 256 && LIVE.resolution.width === 256, 'atlas size');
export const HEIGHT = {round: 18.42, casing: 18.945};

// ---------------- LabPBR on the shared UVs ----------------
const sub = (a, b) => a.map((v, i) => v - b[i]), cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const norm = a => { const l = Math.hypot(...a) || 1; return a.map(v => v / l); };
const kind = name => name.startsWith('01') ? 'hull' : name.startsWith('02') ? 'brass' : name.startsWith('03') ? 'primer' : name.includes('crimp') ? 'crimp' : 'mouth';
// zone (+ AO override) of one face, or of one texel of it (p = its 3D position): orientation decides pockets and insides
function zoneOf(k, n, c, p) {
  const r = Math.hypot(c[0], c[2]), radial = r > 1e-6 ? (n[0] * c[0] + n[2] * c[2]) / r : 0;
  switch (k) {
    case 'hull': return radial < -0.5 ? ['shellInner'] : n[1] > 0.7 && c[1] > 17 ? ['shellHullTop'] : ['shellHull'];
    case 'brass': return r < 1.15 || radial < -0.5 ? ['pocket'] : n[1] < -0.7 ? ['head'] : r >= 3.34 ? ['rim'] : n[1] > 0.7 ? ['rimtop'] : ['body'];
    case 'primer': return n[1] < -0.7 ? ['shellPrimer'] : ['primerSide'];
    case 'crimp': return ['shellCrimp', Math.round(190 + 65 * sm(0, 2.62, Math.hypot(p[0], p[2])))];   // folds darken toward the centre
    case 'mouth': return radial < -0.3 || r < 2.75 ? ['shellInner', Math.round(150 + 60 * sm(17.6, 18.9, p[1]))] : ['shellHull'];
  }
}
const spec = Buffer.alloc(N * N * 4), nrm = Buffer.alloc(N * N * 4), owner = new Int8Array(N * N).fill(-1), rankAt = new Int8Array(N * N).fill(-1);
// a few brass faces of the hand-made UVs share texels (outer body, rim top step and primer pocket): the visible outer
// surface wins, recesses and insides never overwrite it
const RANK = {pocket: 0, shellInner: 0, rimtop: 1, primerSide: 1};
for (let i = 0; i < N * N; i++) { spec.set(ammoPbr('shellHull').s, i * 4); nrm.set(ammoPbr('shellHull').n, i * 4); }
const sample = {hull: [0, 0, 0, 0], brass: [0, 0, 0, 0], primer: [0, 0, 0, 0]};   // atlas palette for the FX (texel averages)
export const zoneTexels = {};
[LIVE, SPENT].forEach((src, si) => {
  for (const e of src.elements.filter(q => q.type === 'mesh')) {
    const k = kind(e.name), V = e.vertices;
    for (const f of Object.values(e.faces)) {
      const P = f.vertices.map(v => V[v]), U = f.vertices.map(v => f.uv[v]);
      let n = [0, 0, 0]; for (let q = 1; q + 1 < P.length; q++) n = n.map((x, i) => x + cross(sub(P[q], P[0]), sub(P[q + 1], P[0]))[i]); n = norm(n);
      const c = P.reduce((a, q) => a.map((x, i) => x + q[i] / P.length), [0, 0, 0]);
      for (let q = 1; q + 1 < P.length; q++) {
        const T = [U[0], U[q], U[q + 1]], Q = [P[0], P[q], P[q + 1]], den = (T[1][1] - T[2][1]) * (T[0][0] - T[2][0]) + (T[2][0] - T[1][0]) * (T[0][1] - T[2][1]);
        if (Math.abs(den) < 1e-9) continue;
        const x0 = Math.floor(Math.min(...T.map(t => t[0]))), x1 = Math.ceil(Math.max(...T.map(t => t[0]))), y0 = Math.floor(Math.min(...T.map(t => t[1]))), y1 = Math.ceil(Math.max(...T.map(t => t[1])));
        for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) {
          const X = x + 0.5, Y = y + 0.5, l1 = ((T[1][1] - T[2][1]) * (X - T[2][0]) + (T[2][0] - T[1][0]) * (Y - T[2][1])) / den, l2 = ((T[2][1] - T[0][1]) * (X - T[2][0]) + (T[0][0] - T[2][0]) * (Y - T[2][1])) / den, l3 = 1 - l1 - l2;
          if (l1 < 0 || l2 < 0 || l3 < 0 || x < 0 || y < 0 || x >= N || y >= N) continue;
          const i = y * N + x; if (si === 1 && owner[i] === 0) continue;   // the spent shell only fills texels the live one leaves
          const p = [0, 1, 2].map(j => Q[0][j] * l1 + Q[1][j] * l2 + Q[2][j] * l3), [zone, ao] = zoneOf(k, n, c, p), rank = RANK[zone] ?? 2;
          if (owner[i] === si && rankAt[i] > rank) continue;
          const t = ammoPbr(zone, ao ?? null);
          spec.set(t.s, i * 4); nrm.set(t.n, i * 4); owner[i] = si; rankAt[i] = rank; zoneTexels[zone] = (zoneTexels[zone] || 0) + 1;
          const key = zone === 'shellHull' ? 'hull' : zone === 'body' ? 'brass' : zone === 'shellPrimer' ? 'primer' : null;
          if (key && si === 0) { const col = BASE.at(x, y); for (let j = 0; j < 3; j++) sample[key][j] += col[j]; sample[key][3]++; }
        }
      }
    }
  }
});
export const PALETTE = Object.fromEntries(Object.entries(sample).map(([k, v]) => [k, v.slice(0, 3).map(x => Math.round(x / v[3]))]));

// ---------------- geo + V2 sidecars ----------------
const roundGeo = readJson(path.join(assets, 'geo/12_gauge_round.geo.json'));
const casingGeo = {format_version: '1.12.0', 'minecraft:geometry': [{
  description: {identifier: 'geometry.12_gauge_casing', texture_width: 256, texture_height: 256, visible_bounds_width: 2, visible_bounds_height: 2, visible_bounds_offset: [0, 0.5, 0]},
  bones: [{name: SPENT.groups[0].name, pivot: [0, 0, 0], rotation: [0, 0, 0]}]}]};
assert(roundGeo['minecraft:geometry'][0].bones[0].name === LIVE.groups[0].name, 'round geo bone');
const roundMesh = convert(LIVE, roundGeo, {}, '12ga_hybrid_mesh_prototype.bbmodel', 2), casingMesh = convert(SPENT, casingGeo, {}, '12ga_hybrid_mesh_spent.bbmodel', 2);
const count = m => { const f = m.parts.flatMap(p => p.faces); return {triangleEquivalent: f.reduce((s, q) => s + q.length - 2, 0), quads: f.filter(q => q.length === 4).length, triangles: f.filter(q => q.length === 3).length}; };
const height = m => { const ys = m.parts.flatMap(p => p.vertices.map(v => v[1] * 16)); return +(Math.max(...ys) - Math.min(...ys)).toFixed(4); };
assert(height(roundMesh) === HEIGHT.round && height(casingMesh) === HEIGHT.casing, 'shell heights');
export const stats = {round: count(roundMesh), casing: count(casingMesh), palette: PALETTE, zoneTexels};

const maps = [png(spec, N, N), png(nrm, N, N)];
const outputs = [
  [path.join(assets, 'meshes/12_gauge_round.aflmesh.json'), serializeCompact(roundMesh)],
  [path.join(assets, 'geo/12_gauge_casing.geo.json'), JSON.stringify(casingGeo, null, 2)],
  [path.join(assets, 'meshes/12_gauge_casing.aflmesh.json'), serializeCompact(casingMesh)],
  [path.join(bb, 'textures/12_gauge_round_mesh.png'), baseBytes],
  ...['_s', '_n'].flatMap((k, i) => [[path.join(assets, `textures/item/12_gauge_round_mesh${k}.png`), maps[i]], [path.join(bb, `textures/12_gauge_round_mesh${k}.png`), maps[i]]]),
];
if (isMain) {
  console.log(JSON.stringify(stats));
  if (CHECK) { for (const [f, d] of outputs) { const cur = fs.existsSync(f) ? fs.readFileSync(f) : null; if (!cur || !cur.equals(Buffer.isBuffer(d) ? d : Buffer.from(d))) throw new Error('stale ' + path.relative(root, f)); } console.log('CHECK OK'); }
  else { for (const [f, d] of outputs) fs.writeFileSync(f, d); console.log('wrote ' + outputs.map(([f]) => path.relative(root, f)).join(', ')); }
}

// ---------------- Ejected Casing Low-Poly FX (not wired: Silverwood 12 does not eject yet) ----------------
// Fired-shell silhouette in 8 segments: primer disc, brass head face, rim (chamfered), step to the brass body, brass body,
// step to the hull, hull, open mouth lip and a shallow dark dish (the mouth is not modelled open inside).
const FX_OUT = {
  casing: {src: path.join(bb, '12_gauge_casing_fx.bbmodel'), geo: path.join(assets, 'geo/12_gauge_casing_fx.geo.json'), mesh: path.join(assets, 'meshes/12_gauge_casing_fx.aflmesh.json')},
  srcTexture: path.join(bb, 'textures/12_gauge_casing_fx.png'), texture: path.join(assets, 'textures/item/12_gauge_casing_fx.png'),
  srcSpec: path.join(bb, 'textures/12_gauge_casing_fx_s.png'), spec: path.join(assets, 'textures/item/12_gauge_casing_fx_s.png'),
  srcNormal: path.join(bb, 'textures/12_gauge_casing_fx_n.png'), normal: path.join(assets, 'textures/item/12_gauge_casing_fx_n.png'),
};
export const FX = {primerR: 0.84, rimR: 3.45, rimT: 0.5, brassR: 3.28, brassTop: 4.2, hullR: 3.10, innerR: 2.73, top: HEIGHT.casing, dish: 1.35};
const FX_MODELS = {casing: {bone: 'casing', parts: [{name: 'casing_fx', runs: [
  {region: 'fx_primer', map: 'facets', pts: [[0, 0], [FX.primerR, 0, 'primer']]},
  {region: 'fx_case', map: 'facets', pts: [[FX.primerR, 0], [FX.rimR - 0.06, 0, 'head'], [FX.rimR, 0.06, 'rim'], [FX.rimR, FX.rimT, 'rim'],
    [FX.brassR, FX.rimT + 0.13, 'rimStep'], [FX.brassR, FX.brassTop - 0.2, 'brass'], [FX.hullR, FX.brassTop, 'brassStep'], [FX.hullR, FX.top, 'hull'],
    [FX.innerR, FX.top, 'lip'], [0, FX.top - FX.dish, 'dish']]}]}]}};
const FX_PBR = {primer: 'shellPrimer', head: 'head', rim: 'rim', rimStep: 'groove', brass: 'body', brassStep: 'bevel', hull: 'shellHull', lip: 'shellHullTop', dish: 'shellInner'};
function paintFx(model, zone, p) {
  const r = Math.hypot(p[0], p[2]), y = p[1], B = PALETTE.brass, H = PALETTE.hull;
  switch (zone) {
    case 'primer': return sc(PALETTE.primer, r < 0.38 ? 0.6 + 0.4 * sm(0, 0.38, r) : 1);   // struck centre, no geometry
    case 'head': return sc(B, 0.86);
    case 'rim': return sc(B, 1.0);
    case 'rimStep': return sc(B, 0.72);
    case 'brass': return sc(B, 0.96);
    case 'brassStep': return sc(B, 0.88);
    case 'hull': return sc(H, 1 - 0.06 * sm(17.4, FX.top, y));
    case 'lip': return sc(H, 0.9);
    case 'dish': return mix(sc(H, 0.45), [14, 10, 10], sm(FX.innerR, 0.8, r));   // shallow dark opening, darker to the centre
  }
  throw new Error('unpainted FX zone ' + zone);
}
const pbrFx = (model, zone, p) => { const r = Math.hypot(p[0], p[2]);
  return ammoPbr(FX_PBR[zone], zone === 'dish' ? AMMO_PBR.shellInner[2] - 40 * sm(FX.innerR, 0.8, r) : null); };
if (isMain)
  runLathe({root, N: 8, atlas: 64, background: sc(PALETTE.hull, 0.9).map(Math.round), uuidSeed: 'afl-12ga-casing-fx', models: FX_MODELS, paint: paintFx, pbr: pbrFx,
    alignFacets: true, noOverdraw: true, meshFormat: 2, compact: true, uvDecimals: 8, posDecimals: 8,
    sourceName: () => '12_gauge_casing_fx', geoId: () => 'geometry.12_gauge_casing_fx',
    texture: {name: '12_gauge_casing_fx.png', relativePath: 'textures/12_gauge_casing_fx.png'}, out: FX_OUT});
