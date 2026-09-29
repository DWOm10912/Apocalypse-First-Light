// 12.7x55mm Visible Ammo V1: live round (ShAK-12 / ASh-12.7 light bullet) + spent casing as pure Mesh (lathe profiles),
// one shared 512 Base Color atlas with light LabPBR (_s / _n from the shared zone table in tools/ammo-pbr.mjs: flat
// normals, AO only). Also writes the ejected-casing low-poly FX asset (12_7x55mm_casing_fx, see the end of this file).
// Mesh build, UV packing, painting and file output live in tools/lathe-mesh-lib.mjs.
//   node tools/build-127x55mm-ammo.mjs            -> writes sources, atlas maps, geo and AFL mesh sidecars (V2)
//   node tools/build-127x55mm-ammo.mjs --check    -> verifies every output is up to date
// Asset contract (same as the 9 mm / .50 AE / 7.62 standard assets): axis +Y, case head on y = 0, centred on X/Z, one bone
// per model ('round', 'casing') with pivot [0,0,0]. Scale follows the HR55 model: the round is exactly as long as HR55's
// former visible magazine rounds (3.109 units for the 73 mm cartridge, 1 unit ~ 23.48 mm), so the later dynamic ammo
// fits the HR55 magazine.
// The cartridge: 12.7x55mm (STs-130 family, the ShAK-12 / ASh-12.7 short loading, OAL ~73 mm): rimless, nearly straight
// brass case derived from .338 Lapua Magnum (base 14.86, shoulder 13.76, case 54.91 mm), large rifle primer. The light
// bullet (LP) has an aluminium core exposed at the front, partially enclosed in a bimetal (copper-clad steel) jacket: the
// jacket bearing band and lower ogive end in a thin cut lip, the matte aluminium nose continues the ogive to a small flat
// meplat. No headstamp text, colour band or brand marks. Flat-shaded 20-segment lathe (as the other ammo sets).
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {runLathe, sm, mix, sc, hash} from './lathe-mesh-lib.mjs';
import {ammoPbr, AMMO_PBR} from './ammo-pbr.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const assets = path.join(root, 'src/main/resources/assets/apocalypse_firstlight');
const OUT = {
  round: {src: path.join(root, 'src/main/blockbench/12_7x55mm_round_mesh.bbmodel'), geo: path.join(assets, 'geo/12_7x55mm_round.geo.json'), mesh: path.join(assets, 'meshes/12_7x55mm_round.aflmesh.json')},
  casing: {src: path.join(root, 'src/main/blockbench/12_7x55mm_casing_mesh.bbmodel'), geo: path.join(assets, 'geo/12_7x55mm_casing.geo.json'), mesh: path.join(assets, 'meshes/12_7x55mm_casing.aflmesh.json')},
  srcTexture: path.join(root, 'src/main/blockbench/textures/12_7x55mm_ammo_v1.png'), texture: path.join(assets, 'textures/item/12_7x55mm_ammo_v1.png'),
  srcSpec: path.join(root, 'src/main/blockbench/textures/12_7x55mm_ammo_v1_s.png'), spec: path.join(assets, 'textures/item/12_7x55mm_ammo_v1_s.png'),
  srcNormal: path.join(root, 'src/main/blockbench/textures/12_7x55mm_ammo_v1_n.png'), normal: path.join(assets, 'textures/item/12_7x55mm_ammo_v1_n.png'),
};

// ---------------- dimensions (mm -> Blockbench units) ----------------
export const MM_PER_UNIT = 73 / 3.109;   // HR55's former visible round: 3.109 units long
const U = mm => mm / MM_PER_UNIT;
export const D = {
  rimR: U(7.465), baseR: U(7.43), bodyTopR: U(7.19), neckR: U(6.93), crimpR: U(6.87), bulletR: U(6.505), grooveR: U(6.35),
  headChamR: U(7.10), headChamY: U(0.40), rimT: U(1.52), rimFaceY: U(1.95), grooveY1: U(2.55), bevelY: U(3.65),
  bodyTopY: U(48.0), neckY: U(50.2), crimpY: U(54.3), caseL: U(54.91),
  bearingY: U(60.0), lipY: U(65.2), lipStep: U(0.26), oal: U(73.0), meplatR: U(1.05), noseTopY: U(72.75),
  primerR: U(2.665), primerFaceR: U(2.45), primerRecess: U(0.08), pocketR: U(2.72), pocketMouthR: U(2.84), pocketDepth: U(0.30),
  // fired casing: mouth expanded to the chamber (no bullet), slightly rolled lip, brass wall ~0.45 mm, web at 6.5 mm
  firedNeckR: U(7.00), lipR: U(6.96), innerNeckR: U(6.52), innerBodyR: U(6.85), innerTopY: U(50.4),
  webY: U(6.5), webR: U(6.4), flashR: U(0.9), casingTop: U(55.05),
};
const N = 20;   // segments around, same as the other ammo sets

// Bullet nose: one tangent ogive from the bearing band toward a virtual point 1 mm beyond the case length + 73 mm; the
// bimetal jacket follows it to the cut lip, the aluminium core continues it (inset by the lip step) to a small meplat.
const NOSE = (() => { const rb = D.bulletR, y0 = D.bearingY, L = U(73.9) - y0, R = (rb * rb + L * L) / (2 * rb); return y => Math.sqrt(R * R - (y - y0) ** 2) - (R - rb); })();
function jacketNose() {
  const ys = [0.20, 0.42, 0.64, 0.84, 1.0].map(t => D.bearingY + t * (D.lipY - D.bearingY));
  return ys.map(y => [NOSE(y), y, 'ogive']);
}
function aluminiumNose() {
  const y0 = D.lipY + U(0.10), r0 = NOSE(D.lipY) - D.lipStep;
  // the core's side follows the ogive inset by the lip step; rings denser toward the meplat edge
  const ys = [0.16, 0.34, 0.52, 0.68, 0.82, 0.92, 1.0].map(t => y0 + t * (D.noseTopY - y0));
  return [[NOSE(D.lipY) - D.lipStep, D.lipY, 'jacketLip'], [r0, y0, 'alu'], ...ys.map(y => [Math.max(D.meplatR + U(0.25), NOSE(y) - D.lipStep), y, 'alu']),
    [D.meplatR, D.oal - U(0.06), 'aluTip'], [D.meplatR - U(0.12), D.oal, 'aluTip'], [0, D.oal, 'aluTip']];
}
// A profile becomes several runs: cylindrical stretches unwrap as 'strip', tapered / annular ones as 'facets' (each facet
// flat and congruent in its own column), so AFL Mesh V2 keeps every band as a quad (same scheme as the 7.62 set).
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
      [D.grooveR, D.grooveY1, 'groove'], [D.baseR, D.bevelY, 'bevel'], [D.bodyTopR, D.bodyTopY, 'body'], [D.neckR, D.neckY, 'shoulder'],
      [D.neckR, D.crimpY, 'neck'], [D.crimpR, D.caseL, 'crimp'], [D.bulletR, D.caseL, 'mouth']])]},
  {name: 'round_bullet', runs: runs('bullet', [[D.bulletR, D.crimpY], [D.bulletR, D.caseL, 'seated'], [D.bulletR, D.bearingY, 'bearing'],
    ...jacketNose(), ...aluminiumNose()])},
  {name: 'round_primer', runs: runs('primer', [[0, D.primerRecess], [D.primerFaceR, D.primerRecess, 'primer'], [D.primerR, U(0.14), 'primerEdge'], [D.primerR, D.pocketDepth, 'primerSide']])},
]},
casing: {bone: 'casing', parts: [
  {name: 'casing_body', runs: [
    ...runs('c_pocket', [[0.10, D.pocketDepth], [D.pocketR, D.pocketDepth, 'pocket'], [D.pocketR, U(0.05), 'pocket'], [D.pocketMouthR, 0, 'pocket']]),
    ...runs('c_head', [[D.pocketMouthR, 0], [D.headChamR, 0, 'head']]),
    // outside as the live round up to the body top, then the fired (expanded) neck, the rolled lip and the sooted interior
    ...runs('c_side', [[D.headChamR, 0], [D.rimR, D.headChamY, 'chamfer'], [D.rimR, D.rimT, 'rim'], [D.grooveR, D.rimFaceY, 'rimtop'],
      [D.grooveR, D.grooveY1, 'groove'], [D.baseR, D.bevelY, 'bevel'], [D.bodyTopR, D.bodyTopY, 'body'], [D.firedNeckR, D.neckY, 'shoulder'],
      [D.firedNeckR, D.caseL - U(0.35), 'neck'], [D.lipR, D.casingTop, 'lip'], [D.innerNeckR, D.casingTop, 'lipTop'],
      [D.innerNeckR, D.innerTopY, 'inner'], [D.innerBodyR, D.innerTopY - U(2.0), 'inner'], [D.innerBodyR, D.webY + U(1.6), 'innerDeep'], [D.webR, D.webY, 'innerDeep']]),
    ...runs('c_floor', [[D.webR, D.webY], [D.flashR, D.webY, 'floor'], [D.flashR, D.webY - U(1.2), 'flash'], [0, D.webY - U(1.2), 'flash']])]},
  {name: 'casing_primer', runs: runs('c_primer', [[0, U(0.45)], [U(0.45), U(0.35), 'dimple'], [U(0.75), U(0.18), 'dimple'], [U(0.95), D.primerRecess, 'dimple'],
    [D.primerFaceR, D.primerRecess, 'primer'], [D.primerR, U(0.14), 'primerEdge'], [D.primerR, D.pocketDepth, 'primerSide']])},
]}};

// ---------------- Base Color ----------------
// Same clean language as the 9 mm / .50 AE / 7.62 atlases (smooth zone gradients, no speckle, no baked specular).
// Brass case (same brass as 7.62) with a faint darker anneal band near the mouth, brass primer; bullet: darker copper-steel
// bimetal jacket (reads browner and duller than the 7.62's gilding metal), a bright cut lip, then the matte aluminium
// core: cool light grey with a faint low-frequency sheen gradient toward the meplat (never white).
const BRASS = [186, 150, 82], BIMETAL = [146, 105, 84], ALU = [150, 153, 157], PRIMER = [170, 138, 80];
const rowGrain = y => 1 + 0.006 * (hash(Math.round(y * 300), 29) - 0.5);   // faint drawn-brass lines along the case
function paint(model, zone, p) {
  const r = Math.hypot(p[0], p[2]), y = p[1], f = y / D.caseL, fired = model === 'casing';
  const soot = c => fired ? mix(sc(c, 1 - 0.10 * sm(0.86, 1.0, f)), [118, 106, 90], 0.14 * sm(0.86, 1.0, f)) : c;   // light powder soot at the mouth
  const anneal = c => mix(c, [150, 112, 70], 0.20 * sm(D.bodyTopY - U(6), D.neckY, y));   // mouth anneal tint
  switch (zone) {
    case 'pocket': return [58, 46, 30];
    case 'head': return sc(BRASS, (0.84 + 0.06 * sm(U(3.2), U(6.8), r)) * (1 + 0.008 * Math.sin(r * 400)));   // faint turning rings
    case 'chamfer': return sc(BRASS, 1.06);
    case 'rim': return sc(BRASS, 0.97 * rowGrain(y));
    case 'rimtop': return sc(BRASS, 0.78);
    case 'groove': return sc(BRASS, 0.64);
    case 'bevel': return sc(BRASS, 0.80 + 0.12 * sm(D.grooveY1, D.bevelY, y));
    case 'body': return anneal(sc(BRASS, (0.93 + 0.08 * sm(0.06, 0.2, f) - 0.03 * sm(0.72, 0.86, f)) * rowGrain(y)));
    case 'shoulder': return anneal(sc(BRASS, 1.03));
    case 'neck': return soot(anneal(sc(BRASS, 0.95 * rowGrain(y))));
    case 'lip': return soot(anneal(sc(BRASS, 1.0)));
    case 'lipTop': return sc(BRASS, 1.08);
    case 'inner': return mix(sc(BRASS, 0.50), [70, 56, 40], 0.4);
    case 'innerDeep': return mix(sc(BRASS, 0.38), [44, 36, 26], 0.5 + 0.4 * sm(D.innerTopY, D.webY, y));
    case 'floor': return [44, 36, 26];
    case 'flash': return [16, 13, 10];
    case 'dimple': return sc(PRIMER, 0.55 + 0.30 * sm(0, U(0.95), r));   // firing-pin strike
    case 'crimp': return anneal(sc(BRASS, 0.92));
    case 'mouth': return sc(BRASS, 1.06);
    case 'seated': return sc(BIMETAL, 0.72);
    case 'bearing': return sc(BIMETAL, 0.80 + 0.20 * sm(D.caseL, D.caseL + U(0.6), y));   // shadow line right above the case mouth
    case 'ogive': return sc(BIMETAL, 1 + 0.04 * sm(D.bearingY, D.lipY, y));
    case 'jacketLip': return sc(BIMETAL, 1.16);   // the jacket's cut edge catches the light
    case 'alu': return sc(ALU, 0.94 + 0.08 * sm(D.lipY, D.noseTopY, y));
    case 'aluTip': return sc(ALU, 1.02 - 0.06 * (r / D.meplatR));
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
  runLathe({root, N, atlas: 512, background: [112, 88, 52], uuidSeed: 'afl-127x55mm-ammo-v1', models: MODELS, paint, pbr,
    phase: -Math.PI / 2, alignFacets: true, noOverdraw: true, meshFormat: 2, compact: true, posDecimals: 12, uvDecimals: 9,
    sourceName: model => `12_7x55mm_${model}_mesh`, geoId: model => `geometry.12_7x55mm_${model}`,
    texture: {name: '12_7x55mm_ammo_v1.png', relativePath: 'textures/12_7x55mm_ammo_v1.png'}, out: OUT});

// ---------------- Ejected Casing Low-Poly FX (NativeGunFx ejection only) ----------------
// Same rules as the 9 mm / .50 AE / 7.62 FX casings: 8 segments, no pocket, chamfer, inner wall or dimple geometry; same
// outer dimensions, bone ('casing'), axis and palette as the high-detail fired casing, so the ejection size and tumbling
// centre match it. The short mouth shoulder stays in the silhouette. 'facets' layout keeps every band a V2 quad.
const FX_OUT = {
  casing: {src: path.join(root, 'src/main/blockbench/12_7x55mm_casing_fx.bbmodel'), geo: path.join(assets, 'geo/12_7x55mm_casing_fx.geo.json'), mesh: path.join(assets, 'meshes/12_7x55mm_casing_fx.aflmesh.json')},
  srcTexture: path.join(root, 'src/main/blockbench/textures/12_7x55mm_casing_fx.png'), texture: path.join(assets, 'textures/item/12_7x55mm_casing_fx.png'),
  srcSpec: path.join(root, 'src/main/blockbench/textures/12_7x55mm_casing_fx_s.png'), spec: path.join(assets, 'textures/item/12_7x55mm_casing_fx_s.png'),
  srcNormal: path.join(root, 'src/main/blockbench/textures/12_7x55mm_casing_fx_n.png'), normal: path.join(assets, 'textures/item/12_7x55mm_casing_fx_n.png'),
};
const FX = {gy: (D.rimFaceY + D.grooveY1) / 2, dish: U(3.0), dimpleR: U(0.95)};
const FX_MODELS = {casing: {bone: 'casing', parts: [{name: 'casing_fx', runs: [
  {region: 'fx_primer', map: 'facets', pts: [[0, 0], [D.primerR, 0, 'primer']]},
  {region: 'fx_case', map: 'facets', pts: [[D.primerR, 0], [D.rimR, 0, 'head'], [D.rimR, D.rimT, 'rim'], [D.grooveR, FX.gy, 'groove'],
    [D.baseR, D.bevelY, 'bevel'], [D.bodyTopR, D.bodyTopY, 'body'], [D.firedNeckR, D.neckY, 'shoulder'], [D.firedNeckR, D.casingTop, 'neck'],
    [D.innerNeckR, D.casingTop, 'lipTop'], [0, D.casingTop - FX.dish, 'dish']]}]}]}};
function paintFx(model, zone, p) {
  const r = Math.hypot(p[0], p[2]), y = p[1], f = y / D.caseL;
  switch (zone) {
    case 'primer': return sc(PRIMER, r < FX.dimpleR ? 0.55 + 0.45 * sm(0, FX.dimpleR, r) : 1.02 - 0.08 * (r / D.primerFaceR));   // strike, no geometry
    case 'head': return sc(BRASS, 0.84 + 0.06 * sm(U(3.2), U(6.8), r));
    case 'rim': return sc(BRASS, 0.97);
    case 'groove': return sc(BRASS, 0.70);
    case 'bevel': return sc(BRASS, 0.80 + 0.12 * sm(FX.gy, D.bevelY, y));
    case 'body': return sc(BRASS, 0.93 + 0.08 * sm(0.06, 0.2, f) - 0.03 * sm(0.72, 0.86, f));
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
  runLathe({root, N: 8, atlas: 64, background: [112, 88, 52], uuidSeed: 'afl-127x55mm-casing-fx', models: FX_MODELS, paint: paintFx, pbr: pbrFx,
    alignFacets: true, noOverdraw: true, meshFormat: 2, compact: true, uvDecimals: 8, posDecimals: 8,
    sourceName: () => '12_7x55mm_casing_fx', geoId: () => 'geometry.12_7x55mm_casing_fx',
    texture: {name: '12_7x55mm_casing_fx.png', relativePath: 'textures/12_7x55mm_casing_fx.png'}, out: FX_OUT});
