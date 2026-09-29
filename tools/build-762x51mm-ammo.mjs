// 7.62x51mm Visible Ammo V1: live round (M80-style FMJ ball) + spent casing as pure Mesh (lathe profiles), one shared
// 512 Base Color atlas with light LabPBR (_s / _n from the shared zone table in tools/ammo-pbr.mjs: flat normals, AO
// only). Also writes the ejected-casing low-poly FX asset (762x51mm_casing_fx, see the end of this file).
// Mesh build, UV packing, painting and file output live in tools/lathe-mesh-lib.mjs.
//   node tools/build-762x51mm-ammo.mjs            -> writes sources, atlas maps, geo and AFL mesh sidecars (V2)
//   node tools/build-762x51mm-ammo.mjs --check    -> verifies every output is up to date
// Asset contract (same as the 9 mm / .50 AE standard assets): axis +Y, case head on y = 0, centred on X/Z, one bone per
// model ('round', 'casing') with pivot [0,0,0]. Scale follows the BR51-01 model (1 Blockbench unit ~ 21.5 mm): the round is the size of
// BR51's former visible magazine rounds (d 0.551, l 3.32) so later dynamic ammo fits the BR51 magazine unchanged.
// Dimensions after C.I.P. / NATO 7.62x51 mm (308 Win case): rimless bottle neck, 20 deg shoulder, 147 gr FMJ ball with a
// tangent ogive; the boat tail sits inside the neck and is not modelled. No headstamp text, colour band or brand marks.
// Flat-shaded 20-segment lathe (no smoothed / fake-round normals in _n by request).
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {runLathe, sm, mix, sc, hash} from './lathe-mesh-lib.mjs';
import {ammoPbr, AMMO_PBR} from './ammo-pbr.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const assets = path.join(root, 'src/main/resources/assets/apocalypse_firstlight');
const OUT = {
  round: {src: path.join(root, 'src/main/blockbench/762x51mm_round_mesh.bbmodel'), geo: path.join(assets, 'geo/762x51mm_round.geo.json'), mesh: path.join(assets, 'meshes/762x51mm_round.aflmesh.json')},
  casing: {src: path.join(root, 'src/main/blockbench/762x51mm_casing_mesh.bbmodel'), geo: path.join(assets, 'geo/762x51mm_casing.geo.json'), mesh: path.join(assets, 'meshes/762x51mm_casing.aflmesh.json')},
  srcTexture: path.join(root, 'src/main/blockbench/textures/762x51mm_ammo_v1.png'), texture: path.join(assets, 'textures/item/762x51mm_ammo_v1.png'),
  srcSpec: path.join(root, 'src/main/blockbench/textures/762x51mm_ammo_v1_s.png'), spec: path.join(assets, 'textures/item/762x51mm_ammo_v1_s.png'),
  srcNormal: path.join(root, 'src/main/blockbench/textures/762x51mm_ammo_v1_n.png'), normal: path.join(assets, 'textures/item/762x51mm_ammo_v1_n.png'),
};

// ---------------- dimensions (mm -> Blockbench units) ----------------
const U = mm => mm / 21.5;
export const D = {
  rimR: U(6.005), baseR: U(5.98), shoulderR: U(5.765), neckR: U(4.36), crimpR: U(4.30), bulletR: U(3.91), grooveR: U(5.195),
  headChamR: U(5.70), headChamY: U(0.35), rimT: U(1.37), rimFaceY: U(1.80), grooveY1: U(2.25), bevelY: U(3.20),
  shoulderY: U(39.62), neckY: U(43.48), crimpY: U(50.55), caseL: U(51.18), bearingY: U(53.0), oal: U(71.12), tipR: U(0.10),
  primerR: U(2.665), primerFaceR: U(2.45), primerRecess: U(0.08), pocketR: U(2.72), pocketMouthR: U(2.84), pocketDepth: U(0.30),
  // fired casing: neck expanded to the chamber (no bullet), slightly rolled lip, brass wall ~0.4 mm, web at 6 mm
  firedNeckR: U(4.44), lipR: U(4.40), innerNeckR: U(4.00), innerBodyR: U(5.45), innerShoulderY: U(40.2), innerNeckY: U(43.7),
  webY: U(6.0), webR: U(5.2), flashR: U(0.9), casingTop: U(51.30),
};
const N = 20;   // segments around, same as the 9 mm / .50 AE sets

// FMJ nose: tangent ogive from the bearing band to a small rounded tip (rings denser toward the tip)
function ogive() {
  const rb = D.bulletR, y0 = D.bearingY, L = D.oal - y0, R = (rb * rb + L * L) / (2 * rb), r = s => Math.sqrt(R * R - s * s) - (R - rb);
  return [0.18, 0.36, 0.52, 0.65, 0.76, 0.85, 0.915, 0.955, 0.985].map(t => [r(t * L), y0 + t * L, 'ogive'])
    .concat([[D.tipR, D.oal, 'tip']]);   // small flat meplat (the tangent ogive is 0.12 mm wide at t = 0.985)
}
// A profile becomes several runs: cylindrical stretches unwrap as 'strip', tapered / annular ones as 'facets' (each facet
// flat and congruent in its own column), so AFL Mesh V2 keeps every band as a quad (same scheme as the rifle suppressor).
const runs = (base, pts) => {
  const out = []; let cur = null, i = 0;
  for (let k = 1; k < pts.length; k++) {
    const map = Math.abs(pts[k][0] - pts[k - 1][0]) < 1e-9 ? 'strip' : 'facets';
    if (!cur || cur.map !== map) { cur = {region: `${base}_${i++}`, map, pts: [[pts[k - 1][0], pts[k - 1][1]]]}; out.push(cur); }
    cur.pts.push(pts[k]);
  }
  return out;
};
const MODELS = {round: {bone: 'round', parts: [
  {name: 'round_casing', runs: [
    ...runs('pocket', [[0.10, D.pocketDepth], [D.pocketR, D.pocketDepth, 'pocket'], [D.pocketR, U(0.05), 'pocket'], [D.pocketMouthR, 0, 'pocket']]),
    ...runs('head', [[D.pocketMouthR, 0], [D.headChamR, 0, 'head']]),
    ...runs('case', [[D.headChamR, 0], [D.rimR, D.headChamY, 'chamfer'], [D.rimR, D.rimT, 'rim'], [D.grooveR, D.rimFaceY, 'rimtop'],
      [D.grooveR, D.grooveY1, 'groove'], [D.baseR, D.bevelY, 'bevel'], [D.shoulderR, D.shoulderY, 'body'], [D.neckR, D.neckY, 'shoulder'],
      [D.neckR, D.crimpY, 'neck'], [D.crimpR, D.caseL, 'crimp'], [D.bulletR, D.caseL, 'mouth']])]},
  {name: 'round_bullet', runs: runs('bullet', [[D.bulletR, D.crimpY], [D.bulletR, D.caseL, 'seated'], [D.bulletR, D.bearingY, 'bearing'], ...ogive(), [0, D.oal, 'tip']])},
  {name: 'round_primer', runs: runs('primer', [[0, D.primerRecess], [D.primerFaceR, D.primerRecess, 'primer'], [D.primerR, U(0.14), 'primerEdge'], [D.primerR, D.pocketDepth, 'primerSide']])},
]},
casing: {bone: 'casing', parts: [
  {name: 'casing_body', runs: [
    ...runs('c_pocket', [[0.10, D.pocketDepth], [D.pocketR, D.pocketDepth, 'pocket'], [D.pocketR, U(0.05), 'pocket'], [D.pocketMouthR, 0, 'pocket']]),
    ...runs('c_head', [[D.pocketMouthR, 0], [D.headChamR, 0, 'head']]),
    // outside to the shoulder as the live round, then the fired neck, the rolled lip and the sooted interior down to the web
    ...runs('c_side', [[D.headChamR, 0], [D.rimR, D.headChamY, 'chamfer'], [D.rimR, D.rimT, 'rim'], [D.grooveR, D.rimFaceY, 'rimtop'],
      [D.grooveR, D.grooveY1, 'groove'], [D.baseR, D.bevelY, 'bevel'], [D.shoulderR, D.shoulderY, 'body'], [D.firedNeckR, D.neckY, 'shoulder'],
      [D.firedNeckR, D.caseL - U(0.35), 'neck'], [D.lipR, D.casingTop, 'lip'], [D.innerNeckR, D.casingTop, 'lipTop'],
      [D.innerNeckR, D.innerNeckY, 'inner'], [D.innerBodyR, D.innerShoulderY, 'inner'], [D.innerBodyR, D.webY + U(1.5), 'innerDeep'], [D.webR, D.webY, 'innerDeep']]),
    ...runs('c_floor', [[D.webR, D.webY], [D.flashR, D.webY, 'floor'], [D.flashR, D.webY - U(1.2), 'flash'], [0, D.webY - U(1.2), 'flash']])]},
  {name: 'casing_primer', runs: runs('c_primer', [[0, U(0.45)], [U(0.45), U(0.35), 'dimple'], [U(0.75), U(0.18), 'dimple'], [U(0.95), D.primerRecess, 'dimple'],
    [D.primerFaceR, D.primerRecess, 'primer'], [D.primerR, U(0.14), 'primerEdge'], [D.primerR, D.pocketDepth, 'primerSide']])},
]}};

// ---------------- Base Color ----------------
// Same clean language as the 9 mm / .50 AE atlases (smooth zone gradients, no speckle, no baked specular). Military
// brass case with a faint darker annealing band on neck and shoulder, warm copper (gilding metal) jacket reading
// pinker than the brass, nickel-free brass primer; no tip colour (plain ball).
const BRASS = [186, 150, 82], COPPER = [182, 112, 72], PRIMER = [170, 138, 80];
const rowGrain = y => 1 + 0.006 * (hash(Math.round(y * 300), 29) - 0.5);   // faint drawn-brass lines along the case
function paint(model, zone, p) {
  const r = Math.hypot(p[0], p[2]), y = p[1], f = y / D.caseL, fired = model === 'casing';
  const soot = c => fired ? mix(sc(c, 1 - 0.10 * sm(0.86, 1.0, f)), [118, 106, 90], 0.14 * sm(0.86, 1.0, f)) : c;   // light powder soot at the mouth
  const anneal = c => mix(c, [150, 112, 70], 0.22 * sm(D.shoulderY - U(4), D.shoulderY + U(1), y));   // neck / shoulder anneal tint
  switch (zone) {
    case 'pocket': return [58, 46, 30];
    case 'head': return sc(BRASS, (0.84 + 0.06 * sm(U(3.2), U(5.5), r)) * (1 + 0.008 * Math.sin(r * 400)));   // faint turning rings
    case 'chamfer': return sc(BRASS, 1.06);
    case 'rim': return sc(BRASS, 0.97 * rowGrain(y));
    case 'rimtop': return sc(BRASS, 0.78);
    case 'groove': return sc(BRASS, 0.64);
    case 'bevel': return sc(BRASS, 0.80 + 0.12 * sm(D.grooveY1, D.bevelY, y));
    case 'body': return anneal(sc(BRASS, (0.93 + 0.08 * sm(0.06, 0.2, f) - 0.03 * sm(0.62, 0.77, f)) * rowGrain(y)));
    case 'shoulder': return anneal(sc(BRASS, 1.04));
    case 'neck': return soot(anneal(sc(BRASS, 0.95 * rowGrain(y))));
    case 'lip': return soot(anneal(sc(BRASS, 1.0)));
    case 'lipTop': return sc(BRASS, 1.08);
    case 'inner': return mix(sc(BRASS, 0.50), [70, 56, 40], 0.4);
    case 'innerDeep': return mix(sc(BRASS, 0.38), [44, 36, 26], 0.5 + 0.4 * sm(D.innerShoulderY, D.webY, y));
    case 'floor': return [44, 36, 26];
    case 'flash': return [16, 13, 10];
    case 'dimple': return sc(PRIMER, 0.55 + 0.30 * sm(0, U(0.95), r));   // firing-pin strike
    case 'crimp': return anneal(sc(BRASS, 0.92));
    case 'mouth': return sc(BRASS, 1.06);
    case 'seated': return sc(COPPER, 0.72);
    case 'bearing': return sc(COPPER, 0.78 + 0.22 * sm(D.caseL, D.caseL + U(0.5), y));   // shadow line right above the case mouth
    case 'ogive': return sc(COPPER, 1 + 0.05 * sm(D.bearingY, D.bearingY + U(8), y) - 0.05 * sm(D.oal - U(5), D.oal, y));
    case 'tip': return sc(COPPER, 0.92);
    case 'primer': return sc(PRIMER, 1.02 - 0.08 * (r / D.primerFaceR));
    case 'primerEdge': return sc(PRIMER, 0.84);
    case 'primerSide': return sc(PRIMER, 0.70);
  }
  throw new Error('unpainted zone ' + zone);
}
// zones without an entry of their own reuse the closest material in the shared table
const PBR_ZONE = {shoulder: 'body', neck: 'body', primer: 'head', primerEdge: 'rimtop', primerSide: 'groove'};
const pbr = (model, zone) => ammoPbr(PBR_ZONE[zone] || zone);

const isMain = path.resolve(process.argv[1] || '') === fileURLToPath(import.meta.url);
if (isMain)
  runLathe({root, N, atlas: 512, background: [112, 88, 52], uuidSeed: 'afl-762x51mm-ammo-v1', models: MODELS, paint, pbr,
    phase: -Math.PI / 2, alignFacets: true, noOverdraw: true, meshFormat: 2, compact: true, posDecimals: 12, uvDecimals: 9,
    sourceName: model => `762x51mm_${model}_mesh`, geoId: model => `geometry.762x51mm_${model}`,
    texture: {name: '762x51mm_ammo_v1.png', relativePath: 'textures/762x51mm_ammo_v1.png'}, out: OUT});

// ---------------- Ejected Casing Low-Poly FX (NativeGunFx ejection only) ----------------
// Same rules as the 9 mm / .50 AE FX casings: 8 segments, no pocket, chamfer, inner wall or dimple geometry; same outer
// dimensions, bone ('casing'), axis and palette as the high-detail fired casing, so the ejection size and tumbling centre
// match it. The bottle-neck shoulder stays in the silhouette (it is what reads as a rifle case in flight). 'facets'
// layout keeps every band a V2 quad.
const FX_OUT = {
  casing: {src: path.join(root, 'src/main/blockbench/762x51mm_casing_fx.bbmodel'), geo: path.join(assets, 'geo/762x51mm_casing_fx.geo.json'), mesh: path.join(assets, 'meshes/762x51mm_casing_fx.aflmesh.json')},
  srcTexture: path.join(root, 'src/main/blockbench/textures/762x51mm_casing_fx.png'), texture: path.join(assets, 'textures/item/762x51mm_casing_fx.png'),
  srcSpec: path.join(root, 'src/main/blockbench/textures/762x51mm_casing_fx_s.png'), spec: path.join(assets, 'textures/item/762x51mm_casing_fx_s.png'),
  srcNormal: path.join(root, 'src/main/blockbench/textures/762x51mm_casing_fx_n.png'), normal: path.join(assets, 'textures/item/762x51mm_casing_fx_n.png'),
};
const FX = {gy: (D.rimFaceY + D.grooveY1) / 2, dish: U(3.0), dimpleR: U(0.95)};
const FX_MODELS = {casing: {bone: 'casing', parts: [{name: 'casing_fx', runs: [
  {region: 'fx_primer', map: 'facets', pts: [[0, 0], [D.primerR, 0, 'primer']]},
  {region: 'fx_case', map: 'facets', pts: [[D.primerR, 0], [D.rimR, 0, 'head'], [D.rimR, D.rimT, 'rim'], [D.grooveR, FX.gy, 'groove'],
    [D.baseR, D.bevelY, 'bevel'], [D.shoulderR, D.shoulderY, 'body'], [D.firedNeckR, D.neckY, 'shoulder'], [D.firedNeckR, D.casingTop, 'neck'],
    [D.innerNeckR, D.casingTop, 'lipTop'], [0, D.casingTop - FX.dish, 'dish']]}]}]}};
function paintFx(model, zone, p) {
  const r = Math.hypot(p[0], p[2]), y = p[1], f = y / D.caseL;
  switch (zone) {
    case 'primer': return sc(PRIMER, r < FX.dimpleR ? 0.55 + 0.45 * sm(0, FX.dimpleR, r) : 1.02 - 0.08 * (r / D.primerFaceR));   // strike, no geometry
    case 'head': return sc(BRASS, 0.84 + 0.06 * sm(U(3.2), U(5.5), r));
    case 'rim': return sc(BRASS, 0.97);
    case 'groove': return sc(BRASS, 0.70);
    case 'bevel': return sc(BRASS, 0.80 + 0.12 * sm(FX.gy, D.bevelY, y));
    case 'body': return sc(BRASS, 0.93 + 0.08 * sm(0.06, 0.2, f) - 0.03 * sm(0.62, 0.77, f));
    case 'shoulder': return sc(BRASS, 1.0);
    case 'neck': return mix(sc(BRASS, 0.92 - 0.08 * sm(0.86, 1.0, f)), [118, 106, 90], 0.14 * sm(0.86, 1.0, f));   // light powder soot at the mouth
    case 'lipTop': return sc(BRASS, 1.08);
    case 'dish': return mix(sc(BRASS, 0.45), [16, 13, 10], sm(D.innerNeckR, U(0.8), r));   // shallow sooted opening, darker to the centre
  }
  throw new Error('unpainted FX zone ' + zone);
}
const pbrFx = (model, zone, p) => { const r = Math.hypot(p[0], p[2]);
  return ammoPbr(PBR_ZONE[zone] || zone, zone === 'dish' ? AMMO_PBR.dish[2] - 40 * sm(D.innerNeckR, U(0.8), r) : null); };
if (isMain)
  runLathe({root, N: 8, atlas: 64, background: [112, 88, 52], uuidSeed: 'afl-762x51mm-casing-fx', models: FX_MODELS, paint: paintFx, pbr: pbrFx,
    alignFacets: true, noOverdraw: true, meshFormat: 2, compact: true, uvDecimals: 8, posDecimals: 8,
    sourceName: () => '762x51mm_casing_fx', geoId: () => 'geometry.762x51mm_casing_fx',
    texture: {name: '762x51mm_casing_fx.png', relativePath: 'textures/762x51mm_casing_fx.png'}, out: FX_OUT});
