// .50 AE Visible Ammo V2: jacketed soft point (JSP) live round + spent casing, pure Mesh, one shared 512 Base Color atlas.
// Also writes the ejected-casing low-poly FX asset (50_ae_casing_fx, see the end of this file).
//   node tools/build-50ae-ammo.mjs            -> writes sources, atlases, geo and AFL mesh sidecars
//   node tools/build-50ae-ammo.mjs --check    -> verifies every output is up to date
// Replaces the imported hollow-point asset so the calibre no longer reads like a large 9 mm FMJ from the side: a short
// gilding-metal jacket and a big exposed dark lead nose with a wide flat meplat. Output paths, bone names ('round',
// 'casing') and outer heights are unchanged (round 2.201056, casing 1.773824 units), so Blackridge's magazine round
// anchor, NativeGunFx, the item Display files and the Mesh item renderer's centre offsets (0.431217 / 0.444568 =
// 0.5 - height / 32) stay valid without any Java or data change.
// Scale and case dimensions follow the previous asset (1 unit ~ 18.58 mm; rebated rim, C.I.P. .50 Action Express).
// Light LabPBR (_s / _n) from the shared zone table in tools/ammo-pbr.mjs, same as the FX casing; no headstamp text or brand marks.
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {runLathe, sm, mix, sc, hash} from './lathe-mesh-lib.mjs';
import {ammoPbr, AMMO_PBR} from './ammo-pbr.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const assets = path.join(root, 'src/main/resources/assets/apocalypse_firstlight');
const OUT = {
  round: {src: path.join(root, 'src/main/blockbench/50ae_round.bbmodel'), geo: path.join(assets, 'geo/50_ae_round.geo.json'), mesh: path.join(assets, 'meshes/50_ae_round.aflmesh.json')},
  casing: {src: path.join(root, 'src/main/blockbench/50ae_casing.bbmodel'), geo: path.join(assets, 'geo/50_ae_casing.geo.json'), mesh: path.join(assets, 'meshes/50_ae_casing.aflmesh.json')},
  srcTexture: path.join(root, 'src/main/blockbench/textures/blackridge_50ae_ammo_v1.png'),
  texture: path.join(assets, 'textures/item/blackridge_50ae_ammo_v1.png'),
  srcSpec: path.join(root, 'src/main/blockbench/textures/blackridge_50ae_ammo_v1_s.png'), spec: path.join(assets, 'textures/item/blackridge_50ae_ammo_v1_s.png'),
  srcNormal: path.join(root, 'src/main/blockbench/textures/blackridge_50ae_ammo_v1_n.png'), normal: path.join(assets, 'textures/item/blackridge_50ae_ammo_v1_n.png'),
};

// ---------------- dimensions (Blockbench units, 1 unit ~ 18.58 mm) ----------------
const D = {
  rimR: 0.3488, bodyR: 0.3732, bodyEndR: 0.3650, crimpR: 0.3610, bulletR: 0.3418, grooveR: 0.3029,
  headChamR: 0.3245, headChamY: 0.0162, rimT: 0.0649, grooveY0: 0.0865, grooveY1: 0.1298, bevelY: 0.1785,
  bodyEndY: 1.715, caseL: 1.763, oal: 2.201056, casingTop: 1.773824, bearingY: 1.79,
  primerR: 0.1434, primerFaceR: 0.132, primerRecess: 0.0043, pocketR: 0.1497, pocketMouthR: 0.155, pocketDepth: 0.0135,
  jacketY: 2.03, leadY: 2.04, meplatR: 0.20,
};
const N = 20;   // segments around, same as the 9 mm set

// JSP nose: short jacket ogive, a small jacket-mouth bevel, then the exposed lead dome closing on a wide flat meplat.
const JACKET = [[0.3395, 1.85], [0.3320, 1.90], [0.3220, 1.95], [0.3110, 2.00], [0.3020, D.jacketY]].map(p => [...p, 'jacket']);
const LEAD = [[0.2890, D.leadY], [0.2780, 2.08], [0.2590, 2.12], [0.2360, 2.155], [0.2160, 2.183], [D.meplatR, D.oal]].map(p => [...p, 'lead']);

const pocketRun = region => ({region, map: 'strip', pts: [[0.125, D.pocketDepth], [D.pocketR, D.pocketDepth, 'pocket'], [D.pocketR, 0.005, 'pocket'], [D.pocketMouthR, 0, 'pocket']]});
const headRun = region => ({region, map: 'disc', pts: [[D.pocketMouthR, 0], [D.headChamR, 0, 'head']]});
const headSide = [[D.headChamR, 0], [D.rimR, D.headChamY, 'chamfer'], [D.rimR, D.rimT, 'rim'], [D.grooveR, D.grooveY0, 'rimtop'],
  [D.grooveR, D.grooveY1, 'groove'], [D.bodyR, D.bevelY, 'bevel']];
const primerRun = (region, fired) => ({region, map: 'disc', pts: [
  ...(fired ? [[0, 0.022], [0.028, 0.019, 'dimple'], [0.050, 0.010, 'dimple'], [0.064, D.primerRecess, 'dimple']] : [[0, D.primerRecess]]),
  [D.primerFaceR, D.primerRecess, 'primer'], [0.140, 0.0065, 'primerEdge'], [D.primerR, 0.010, 'primerEdge'], [D.primerR, D.pocketDepth, 'primerSide']]});

const MODELS = {
  round: {bone: 'round', parts: [
    {name: 'round_casing', runs: [pocketRun('r_pocket'), headRun('r_head'), {region: 'r_side', map: 'strip', pts: [...headSide,
      [D.bodyEndR, D.bodyEndY, 'body'], [D.crimpR, D.caseL, 'crimp'], [D.bulletR, D.caseL, 'mouth']]}]},
    {name: 'round_bullet', runs: [
      {region: 'bullet', map: 'strip', pts: [[D.bulletR, 1.68], [D.bulletR, D.caseL, 'seated'], [D.bulletR, D.bearingY, 'bearing'], ...JACKET,
        [0.2935, 2.036, 'jacketEdge'], ...LEAD]},
      {region: 'meplat', map: 'disc', pts: [[D.meplatR, D.oal], [0, D.oal, 'meplat']]}]},
    {name: 'round_primer', runs: [primerRun('r_head', false)]},
  ]},
  casing: {bone: 'casing', parts: [
    {name: 'casing_body', runs: [pocketRun('c_pocket'), headRun('c_head'), {region: 'c_side', map: 'strip', pts: [...headSide,
      // fired: body expanded to the chamber, mouth slightly flared, rolled lip, sooted interior down to the web
      [0.3745, 1.62, 'body'], [0.3750, 1.735, 'body'], [0.3738, 1.765, 'lip'], [0.3700, D.casingTop, 'lip'], [0.3600, D.casingTop, 'lipTop'],
      [0.3575, 1.765, 'lip'], [0.3570, 1.45, 'inner'], [0.3490, 0.42, 'innerDeep'], [0.3380, 0.40, 'innerDeep']]},
      {region: 'c_floor', map: 'disc', pts: [[0.3380, 0.40], [0.045, 0.40, 'floor'], [0.045, 0.385, 'flash'], [0, 0.385, 'flash']]}]},
    {name: 'casing_primer', runs: [primerRun('c_head', true)]},
  ]},
};

// ---------------- Base Color ----------------
// Same clean language as the 9 mm atlas (smooth zone gradients, no speckle, no baked specular). The case brass is one
// step brighter and yellower than the 9 mm; the calibre reads through the warm gilding-metal jacket band and the large
// dark lead nose.
const BRASS = [188, 152, 84], GILDING = [190, 128, 78], LEADC = [78, 80, 86], NICKEL = [168, 164, 154];
const rowGrain = y => 1 + 0.006 * (hash(Math.round(y * 300), 29) - 0.5);   // faint drawn-brass lines along the case
function paint(model, zone, p) {
  const r = Math.hypot(p[0], p[2]), y = p[1], a = Math.atan2(p[2], p[0]), fired = model === 'casing', f = y / D.caseL;
  switch (zone) {
    case 'pocket': return [58, 46, 30];
    case 'head': return sc(BRASS, (0.84 + 0.06 * sm(0.16, 0.32, r)) * (1 + 0.008 * Math.sin(r * 400)));   // faint turning rings
    case 'chamfer': return sc(BRASS, 1.06);
    case 'rim': return sc(BRASS, 0.97 * rowGrain(y));
    case 'rimtop': return sc(BRASS, 0.78);
    case 'groove': return sc(BRASS, 0.64);
    case 'bevel': return sc(BRASS, 0.80 + 0.12 * sm(D.grooveY1, D.bevelY, y));
    case 'body': {
      let c = sc(BRASS, (0.93 + 0.09 * sm(0.1, 0.26, f) - 0.03 * sm(0.74, 0.97, f)) * rowGrain(y));
      if (fired) c = mix(sc(c, 1 - 0.10 * sm(0.80, 1.0, f)), [118, 106, 90], 0.14 * sm(0.80, 1.0, f));   // light powder soot at the mouth
      return c;
    }
    case 'crimp': return sc(BRASS, 0.94 * rowGrain(y));
    case 'mouth': return sc(BRASS, 1.08);
    case 'lip': return sc(BRASS, 1.02);
    case 'lipTop': return sc(BRASS, 1.10);
    case 'inner': return mix(sc(BRASS, 0.52), [70, 56, 40], 0.35 * sm(1.0, 0.82, f));
    case 'innerDeep': return mix(sc(BRASS, 0.40), [48, 39, 28], 0.4 + 0.5 * sm(0.82, 0.24, f));
    case 'floor': return [46, 38, 28];
    case 'flash': return [16, 13, 10];
    case 'seated': return sc(GILDING, 0.75);
    case 'bearing': return sc(GILDING, 0.76 + 0.24 * sm(D.caseL, D.caseL + 0.018, y));   // shadow line right above the case mouth
    case 'jacket': return sc(GILDING, 1 + 0.06 * sm(D.bearingY, 1.93, y) - 0.04 * sm(1.98, D.jacketY, y));
    case 'jacketEdge': return sc(GILDING, 1.12);                                             // bright rolled jacket mouth
    case 'lead': {   // soft matte lead: darker where it leaves the jacket, faint low-frequency mottling
      const t = (y - D.leadY) / (D.oal - D.leadY);
      return sc(LEADC, (0.82 + 0.18 * sm(0, 0.35, t)) * (1 + 0.03 * Math.sin(a * 3 + y * 24)));
    }
    case 'meplat': return sc(LEADC, (1.14 - 0.12 * sm(0.10, D.meplatR, r)) * (1 + 0.025 * Math.sin(p[0] * 30 + p[2] * 22)));   // flat lead face
    case 'primer': return sc(NICKEL, 1.04 - 0.08 * (r / D.primerFaceR));
    case 'primerEdge': return sc(NICKEL, 0.84);
    case 'primerSide': return sc(NICKEL, 0.70);
    case 'dimple': return sc(NICKEL, 0.55 + 0.30 * sm(0.0, 0.064, r));   // firing-pin strike
  }
  throw new Error('unpainted zone ' + zone);
}

runLathe({root, N, atlas: 512, background: [112, 88, 52], uuidSeed: 'afl-50ae-ammo-v2', models: MODELS, paint, pbr: (model, zone) => ammoPbr(zone),
  sourceName: model => `50ae_${model}`, geoId: model => `geometry.50_ae_${model}`,
  texture: {name: 'blackridge_50ae_ammo_v1.png', relativePath: 'textures/blackridge_50ae_ammo_v1.png'}, out: OUT});

// ---------------- Ejected Casing Low-Poly FX (NativeGunFx ejection only) ----------------
// Same rules as the 9 mm FX casing (tools/build-9x19mm-ammo.mjs): 8 segments, same outer dimensions, bone ('casing'),
// axis and palette as the high-detail fired casing; the rebated rim and the extractor groove stay in the silhouette,
// so the calibre still reads heavier than the 9 mm without more faces. 'facets' layout keeps every band a V2 quad.
const FX_OUT = {
  casing: {src: path.join(root, 'src/main/blockbench/50ae_casing_fx.bbmodel'), geo: path.join(assets, 'geo/50_ae_casing_fx.geo.json'), mesh: path.join(assets, 'meshes/50_ae_casing_fx.aflmesh.json')},
  srcTexture: path.join(root, 'src/main/blockbench/textures/50_ae_casing_fx.png'), texture: path.join(assets, 'textures/item/50_ae_casing_fx.png'),
  srcSpec: path.join(root, 'src/main/blockbench/textures/50_ae_casing_fx_s.png'), spec: path.join(assets, 'textures/item/50_ae_casing_fx_s.png'),
  srcNormal: path.join(root, 'src/main/blockbench/textures/50_ae_casing_fx_n.png'), normal: path.join(assets, 'textures/item/50_ae_casing_fx_n.png'),
};
const FX = {mouthR: 0.3700, lipR: 0.3500, dish: 0.14, gy: (D.grooveY0 + D.grooveY1) / 2, dimpleR: 0.064};
const FX_MODELS = {casing: {bone: 'casing', parts: [{name: 'casing_fx', runs: [
  {region: 'fx_primer', map: 'facets', pts: [[0, 0], [D.primerR, 0, 'primer']]},
  {region: 'fx_case', map: 'facets', pts: [[D.primerR, 0], [D.rimR, 0, 'head'], [D.rimR, D.rimT, 'rim'], [D.grooveR, FX.gy, 'groove'],
    [D.bodyR, D.bevelY, 'bevel'], [FX.mouthR, D.casingTop, 'body'], [FX.lipR, D.casingTop, 'lipTop'], [0, D.casingTop - FX.dish, 'dish']]}]}]}};
function paintFx(model, zone, p) {
  const r = Math.hypot(p[0], p[2]), y = p[1], f = y / D.caseL;
  switch (zone) {
    case 'primer': return sc(NICKEL, r < FX.dimpleR ? 0.55 + 0.45 * sm(0, FX.dimpleR, r) : 1.04 - 0.08 * (r / D.primerFaceR));   // strike, no geometry
    case 'head': return sc(BRASS, 0.84 + 0.06 * sm(0.16, 0.32, r));
    case 'rim': return sc(BRASS, 0.97);
    case 'groove': return sc(BRASS, 0.70);
    case 'bevel': return sc(BRASS, 0.80 + 0.12 * sm(FX.gy, D.bevelY, y));
    case 'body': {
      const c = sc(BRASS, 0.93 + 0.09 * sm(0.1, 0.26, f) - 0.03 * sm(0.74, 0.97, f));
      return mix(sc(c, 1 - 0.10 * sm(0.80, 1.0, f)), [118, 106, 90], 0.14 * sm(0.80, 1.0, f));   // light powder soot at the mouth
    }
    case 'lipTop': return sc(BRASS, 1.10);
    case 'dish': return mix(sc(BRASS, 0.45), [16, 13, 10], sm(FX.lipR, 0.08, r));   // shallow sooted opening, darker to the centre
  }
  throw new Error('unpainted FX zone ' + zone);
}
function pbrFx(model, zone, p) {
  const r = Math.hypot(p[0], p[2]);
  return ammoPbr(zone, zone === 'dish' ? AMMO_PBR.dish[2] - 40 * sm(FX.lipR, 0.08, r) : null);   // dish AO darkens to the centre
}
runLathe({root, N: 8, atlas: 64, background: [112, 88, 52], uuidSeed: 'afl-50ae-casing-fx', models: FX_MODELS, paint: paintFx, pbr: pbrFx,
  alignFacets: true, noOverdraw: true, meshFormat: 2, compact: true, uvDecimals: 8, posDecimals: 8,
  sourceName: () => '50ae_casing_fx', geoId: () => 'geometry.50_ae_casing_fx',
  texture: {name: '50_ae_casing_fx.png', relativePath: 'textures/50_ae_casing_fx.png'}, out: FX_OUT});
