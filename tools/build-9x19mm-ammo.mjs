// 9x19mm Visible Ammo V1: live round + spent casing as pure Mesh (lathe profiles), one shared 512 Base Color atlas.
// Also writes the ejected-casing low-poly FX asset (9x19mm_casing_fx, see the end of this file).
// Mesh build, UV packing, painting and file output live in tools/lathe-mesh-lib.mjs.
//   node tools/build-9x19mm-ammo.mjs            -> writes sources, atlases, geo and AFL mesh sidecars
//   node tools/build-9x19mm-ammo.mjs --check    -> verifies every output is up to date
// Asset contract (same as the .50 AE standard assets): round axis +Y, case head on y = 0, centred on X/Z, one bone per
// model with pivot [0,0,0]. Scale follows the P9-01 model (1 Blockbench unit ~ 18.8 mm: case r 0.264 = P9 chamber r).
// Dimensions after C.I.P. 9x19 mm Parabellum (OAL 29.2 mm = typical factory FMJ, fits the P9 magazine); the FMJ round-nose ogive and the fired-case details are original.
// High-detail assets: no PBR, no headstamp text or brand marks.
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {runLathe, sm, mix, sc, hash} from './lathe-mesh-lib.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const assets = path.join(root, 'src/main/resources/assets/apocalypse_firstlight');
const OUT = {
  round: {src: path.join(root, 'src/main/blockbench/9x19mm_round_mesh.bbmodel'), geo: path.join(assets, 'geo/9x19mm_round.geo.json'), mesh: path.join(assets, 'meshes/9x19mm_round.aflmesh.json')},
  casing: {src: path.join(root, 'src/main/blockbench/9x19mm_casing_mesh.bbmodel'), geo: path.join(assets, 'geo/9x19mm_casing.geo.json'), mesh: path.join(assets, 'meshes/9x19mm_casing.aflmesh.json')},
  srcTexture: path.join(root, 'src/main/blockbench/textures/9x19mm_ammo_v1.png'),
  texture: path.join(assets, 'textures/item/9x19mm_ammo_v1.png'),
};

// ---------------- dimensions (mm -> Blockbench units) ----------------
const U = mm => mm / 18.8;
const D = {
  rimR: U(4.98), bodyR: U(4.965), mouthR: U(4.825), crimpR: U(4.765), bulletR: U(4.51), grooveR: U(4.0),
  rimT: U(1.27), grooveY0: U(1.42), grooveY1: U(2.03), bevelY: U(2.75), crimpY: U(18.35), caseL: U(19.15), oal: U(29.2),
  bearingY: U(20.2), headChamR: U(4.68), headChamY: U(0.30),
  primerR: U(2.2), primerFaceR: U(2.0), primerRecess: U(0.08), pocketR: U(2.32), pocketMouthR: U(2.42), pocketDepth: U(0.25),
};
const N = 20;   // segments around (smooth silhouette in close inspect; the .50 AE set uses the same count)

// FMJ round nose: blunt super-elliptic dome (exponent 1.9) from the bearing band to a rounded tip; rings get denser
// toward the tip where the curvature is highest.
function ogive() {
  const rb = D.bulletR, y0 = D.bearingY, L = D.oal - y0, n = 1.9, r = s => rb * Math.pow(1 - Math.pow(s, n), 1 / n);
  const pts = [0.22, 0.42, 0.6, 0.74, 0.85, 0.93, 0.975].map(s => [r(s), y0 + s * L, 'ogive']);
  return {pts: [...pts, [0, D.oal, 'tip']], y0, yCut: D.oal};
}
const OG = ogive();

// Profiles: runs of [r, y, zone-of-the-segment-ending-here]; a run is either a lathe 'strip' (cylindrical unwrap) or a
// 'disc' (planar top-down projection). Traversal direction fixes the outward side: normal = (dy, -dr) in (r, y).
const pocketRun = region => ({region, map: 'strip', pts: [[0.100, D.pocketDepth], [D.pocketR, D.pocketDepth, 'pocket'], [D.pocketR, 0.004, 'pocket'], [D.pocketMouthR, 0, 'pocket']]});
const headRun = region => ({region, map: 'disc', pts: [[D.pocketMouthR, 0], [D.headChamR, 0, 'head']]});
const headSide = [[D.headChamR, 0], [D.rimR, D.headChamY, 'chamfer'], [D.rimR, D.rimT, 'rim'], [D.grooveR, D.grooveY0, 'rimtop'],
  [D.grooveR, D.grooveY1, 'groove'], [D.bodyR, D.bevelY, 'bevel']];
const primerRun = (region, fired) => ({region, map: 'disc', pts: [
  ...(fired ? [[0, 0.020], [0.022, 0.017, 'dimple'], [0.040, 0.009, 'dimple'], [0.052, D.primerRecess, 'dimple']] : [[0, D.primerRecess]]),
  [D.primerFaceR, D.primerRecess, 'primer'], [0.1150, 0.0060, 'primerEdge'], [D.primerR, 0.0095, 'primerEdge'], [D.primerR, D.pocketDepth, 'primerSide']]});

const MODELS = {
  round: {bone: 'round', parts: [
    {name: 'round_casing', runs: [pocketRun('r_pocket'), headRun('r_head'), {region: 'r_side', map: 'strip', pts: [...headSide,
      [D.mouthR, D.crimpY, 'body'], [D.crimpR, D.caseL, 'crimp'], [D.bulletR, D.caseL, 'mouth']]}]},
    {name: 'round_bullet', runs: [{region: 'bullet', map: 'strip', pts: [[D.bulletR, 0.95], [D.bulletR, D.caseL, 'seated'], [D.bulletR, D.bearingY, 'bearing'], ...OG.pts]}]},
    {name: 'round_primer', runs: [primerRun('r_head', false)]},
  ]},
  casing: {bone: 'casing', parts: [
    {name: 'casing_body', runs: [pocketRun('c_pocket'), headRun('c_head'), {region: 'c_side', map: 'strip', pts: [...headSide,
      // fired: body expanded to the chamber, mouth slightly flared, rolled lip, sooted interior down to the web
      [0.2649, 0.93, 'body'], [0.2653, 0.99, 'body'], [0.2642, 1.013, 'lip'], [0.2606, D.caseL, 'lip'], [0.2536, D.caseL, 'lipTop'],
      [0.2512, 1.012, 'lip'], [0.2506, 0.80, 'inner'], [0.2440, 0.30, 'innerDeep'], [0.2350, 0.28, 'innerDeep']]},
      {region: 'c_floor', map: 'disc', pts: [[0.2350, 0.28], [0.030, 0.28, 'floor'], [0.030, 0.265, 'flash'], [0, 0.265, 'flash']]}]},
    {name: 'casing_primer', runs: [primerRun('c_head', true)]},
  ]},
};

// ---------------- Base Color painter ----------------
// Clean modern factory ammo: smooth value gradients per zone, no speckle noise, no baked specular. Brass / copper /
// nickel primer values sit next to the .50 AE atlas so both calibres read as one family. Zone changes run along
// texel rows in the strips, so every band edge is axis aligned (no stair-stepping).
const BRASS = [182, 144, 78], COPPER = [180, 102, 66], NICKEL = [168, 164, 154];
const rowGrain = y => 1 + 0.006 * (hash(Math.round(y * 400), 17) - 0.5);   // faint drawn-brass lines along the case
function paint(model, zone, p) {
  const r = Math.hypot(p[0], p[2]), y = p[1], fired = model === 'casing';
  switch (zone) {
    case 'pocket': return [58, 46, 30];
    case 'head': return sc(BRASS, (0.84 + 0.06 * sm(0.13, 0.245, r)) * (1 + 0.008 * Math.sin(r * 520)));   // faint turning rings
    case 'chamfer': return sc(BRASS, 1.06);
    case 'rim': return sc(BRASS, 0.97 * rowGrain(y));
    case 'rimtop': return sc(BRASS, 0.78);
    case 'groove': return sc(BRASS, 0.64);
    case 'bevel': return sc(BRASS, 0.80 + 0.12 * sm(D.grooveY1, D.bevelY, y));
    case 'body': {
      let c = sc(BRASS, (0.93 + 0.09 * sm(D.bevelY, 0.45, y) - 0.03 * sm(0.75, D.crimpY, y)) * rowGrain(y));
      if (fired) c = mix(sc(c, 1 - 0.10 * sm(0.80, 1.0, y)), [118, 106, 90], 0.14 * sm(0.80, 1.0, y));   // light powder soot at the mouth
      return c;
    }
    case 'crimp': return sc(BRASS, 0.94 * rowGrain(y));
    case 'mouth': return sc(BRASS, 1.08);
    case 'lip': return sc(BRASS, 1.02);
    case 'lipTop': return sc(BRASS, 1.10);
    case 'inner': return mix(sc(BRASS, 0.52), [70, 56, 40], 0.35 * sm(1.0, 0.80, y));
    case 'innerDeep': return mix(sc(BRASS, 0.40), [48, 39, 28], 0.4 + 0.5 * sm(0.80, 0.30, y));
    case 'floor': return [46, 38, 28];
    case 'flash': return [16, 13, 10];
    case 'seated': return sc(COPPER, 0.75);
    case 'bearing': return sc(COPPER, 0.78 + 0.22 * sm(D.caseL, D.caseL + 0.014, y));   // shadow line right above the case mouth
    case 'ogive': { const t = (y - OG.y0) / (OG.yCut - OG.y0); return sc(COPPER, 1 + 0.07 * Math.sin(Math.PI * Math.min(1, t)) - 0.03 * sm(0.8, 1, t)); }
    case 'tip': return sc(COPPER, 0.94);
    case 'primer': return sc(NICKEL, 1.04 - 0.08 * (r / D.primerFaceR));
    case 'primerEdge': return sc(NICKEL, 0.84);
    case 'primerSide': return sc(NICKEL, 0.70);
    case 'dimple': return sc(NICKEL, 0.55 + 0.30 * sm(0.0, 0.052, r));   // firing-pin strike
  }
  throw new Error('unpainted zone ' + zone);
}
runLathe({root, N, atlas: 512, background: [118, 92, 52], uuidSeed: 'afl-9x19mm-ammo', models: MODELS, paint,
  sourceName: model => `9x19mm_${model}_mesh`, geoId: model => `geometry.9x19mm_${model}`,
  texture: {name: '9x19mm_ammo_v1.png', relativePath: 'textures/9x19mm_ammo_v1.png'}, out: OUT});

// ---------------- Ejected Casing Low-Poly FX (NativeGunFx ejection only) ----------------
// The flying casing is small, fast and short-lived: 8 segments, no pocket, chamfer, inner wall or dimple geometry.
// Same outer dimensions, bone ('casing'), axis (+Y, case head on y = 0) and brass / nickel palette as the high-detail
// fired casing above, so the ejection size and tumbling centre are unchanged. Every band is laid out as flat facets
// ('facets' map), so AFL Mesh V2 keeps all side, rim and flat bands as quads. Inventory, world item, dynamic magazine
// round and static display keep the high-detail assets. Light LabPBR: brass metal at medium smoothness, flat normals,
// AO only in the extractor groove and the shallow dark mouth.
const FX_OUT = {
  casing: {src: path.join(root, 'src/main/blockbench/9x19mm_casing_fx.bbmodel'), geo: path.join(assets, 'geo/9x19mm_casing_fx.geo.json'), mesh: path.join(assets, 'meshes/9x19mm_casing_fx.aflmesh.json')},
  srcTexture: path.join(root, 'src/main/blockbench/textures/9x19mm_casing_fx.png'), texture: path.join(assets, 'textures/item/9x19mm_casing_fx.png'),
  srcSpec: path.join(root, 'src/main/blockbench/textures/9x19mm_casing_fx_s.png'), spec: path.join(assets, 'textures/item/9x19mm_casing_fx_s.png'),
  srcNormal: path.join(root, 'src/main/blockbench/textures/9x19mm_casing_fx_n.png'), normal: path.join(assets, 'textures/item/9x19mm_casing_fx_n.png'),
};
const FX = {mouthR: 0.2606, lipR: 0.2446, dish: 0.10, gy: (D.grooveY0 + D.grooveY1) / 2, dimpleR: 0.052};
const FX_MODELS = {casing: {bone: 'casing', parts: [{name: 'casing_fx', runs: [
  {region: 'fx_primer', map: 'facets', pts: [[0, 0], [D.primerR, 0, 'primer']]},
  {region: 'fx_case', map: 'facets', pts: [[D.primerR, 0], [D.rimR, 0, 'head'], [D.rimR, D.rimT, 'rim'], [D.grooveR, FX.gy, 'groove'],
    [D.bodyR, D.bevelY, 'bevel'], [FX.mouthR, D.caseL, 'body'], [FX.lipR, D.caseL, 'lipTop'], [0, D.caseL - FX.dish, 'mouth']]}]}]}};
// zone -> [smoothness, F0 (255 metal / 10 dielectric), AO]
const FX_PBR = {primer: [140, 255, 255], head: [110, 255, 255], rim: [125, 255, 255], groove: [100, 255, 215], bevel: [110, 255, 255],
  body: [115, 255, 255], lipTop: [135, 255, 255], mouth: [50, 10, 150]};
function paintFx(model, zone, p) {
  const r = Math.hypot(p[0], p[2]), y = p[1];
  switch (zone) {
    case 'primer': return sc(NICKEL, r < FX.dimpleR ? 0.55 + 0.45 * sm(0, FX.dimpleR, r) : 1.04 - 0.08 * (r / D.primerFaceR));   // strike, no geometry
    case 'head': return sc(BRASS, 0.84 + 0.06 * sm(0.13, 0.245, r));
    case 'rim': return sc(BRASS, 0.97);
    case 'groove': return sc(BRASS, 0.70);
    case 'bevel': return sc(BRASS, 0.80 + 0.12 * sm(FX.gy, D.bevelY, y));
    case 'body': {
      const c = sc(BRASS, 0.93 + 0.09 * sm(D.bevelY, 0.45, y) - 0.03 * sm(0.75, D.crimpY, y));
      return mix(sc(c, 1 - 0.10 * sm(0.80, 1.0, y)), [118, 106, 90], 0.14 * sm(0.80, 1.0, y));   // light powder soot at the mouth
    }
    case 'lipTop': return sc(BRASS, 1.10);
    case 'mouth': return mix(sc(BRASS, 0.45), [16, 13, 10], sm(FX.lipR, 0.06, r));   // shallow sooted opening, darker to the centre
  }
  throw new Error('unpainted FX zone ' + zone);
}
function pbrFx(model, zone, p) {
  const [s, f0, ao] = FX_PBR[zone], r = Math.hypot(p[0], p[2]);
  return {s: [s, f0, 0, 255], n: [128, 128, zone === 'mouth' ? ao - 40 * sm(FX.lipR, 0.06, r) : ao, 255]};
}
runLathe({root, N: 8, atlas: 64, background: [118, 92, 52], uuidSeed: 'afl-9x19mm-casing-fx', models: FX_MODELS, paint: paintFx, pbr: pbrFx,
  alignFacets: true, noOverdraw: true, meshFormat: 2, compact: true, uvDecimals: 8, posDecimals: 8,
  sourceName: () => '9x19mm_casing_fx', geoId: () => 'geometry.9x19mm_casing_fx',
  texture: {name: '9x19mm_casing_fx.png', relativePath: 'textures/9x19mm_casing_fx.png'}, out: FX_OUT});
