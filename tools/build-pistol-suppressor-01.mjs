// AFL universal 9 mm pistol suppressor (pistol_suppressor_01) V2: Pure Mesh lathe, one 512 atlas with Base Color and
// LabPBR _s / _n painted in the same pass (tools/lathe-mesh-lib.mjs builds, packs, paints and writes everything).
//   node tools/build-pistol-suppressor-01.mjs            -> writes source, atlas maps, geo and AFL mesh sidecar (V2)
//   node tools/build-pistol-suppressor-01.mjs --check    -> verifies every output is up to date
// Mount contract (gun-agnostic, asset scale 1.0): origin = centre of the rear mounting face, muzzle forward = -Z, bone
// pistol_suppressor_root, child bone muzzle_exit_anchor at the real front-face centre. A gun places its muzzle_slot anchor
// on the bore axis at the front end of its own (threaded) barrel; no gun-specific barrel or thread is baked in here.
// Units follow the P9-01 model (1 Blockbench unit ~ 18.8 mm). Cylindrical, segmented can: darker machined-steel booster
// / mount with 4 real ring grooves, recessed neck, matte black coated main tube with one shallow module line, screwed
// front cap with a rolled edge, dark rough bore. 20 segments around; the strip seam sits underneath.
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {runLathe, sm, mix, sc, hash} from './lathe-mesh-lib.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const assets = path.join(root, 'src/main/resources/assets/apocalypse_firstlight');
const src = path.join(root, 'src/main/blockbench');
const OUT = {
  suppressor: {src: path.join(src, 'pistol_suppressor_01.bbmodel'), geo: path.join(assets, 'geo/pistol_suppressor_01.geo.json'),
    mesh: path.join(assets, 'meshes/pistol_suppressor_01.aflmesh.json')},
  srcTexture: path.join(src, 'textures/pistol_suppressor_01.png'), texture: path.join(assets, 'textures/item/pistol_suppressor_01.png'),
  srcSpec: path.join(src, 'textures/pistol_suppressor_01_s.png'), spec: path.join(assets, 'textures/item/pistol_suppressor_01_s.png'),
  srcNormal: path.join(src, 'textures/pistol_suppressor_01_n.png'), normal: path.join(assets, 'textures/item/pistol_suppressor_01_n.png'),
};

// ---------------- dimensions (lathe space: r = radius, y = distance forward of the rear mounting face) ----------------
const R = {booster: 0.66, neck: 0.60, tube: 0.625, cap: 0.625, bore: 0.20, mount: 0.34};
const Y = {booster: 0.90, neck: 1.00, line: 5.20, seam: 7.50, front: 7.85};   // booster 0.9, neck 0.1, tube 6.5, cap 0.35
const N = 20;

// booster outer surface: rear chamfer, land, 4 rectangular ring grooves (w 0.055, depth 0.035, pitch 0.13), land, chamfer
const grooves = [];
for (let i = 0; i < 4; i++) {
  const a = 0.2275 + 0.13 * i;
  grooves.push([R.booster, a, 'land'], [R.booster - 0.035, a, 'grooveWall'], [R.booster - 0.035, a + 0.055, 'grooveFloor'], [R.booster, a + 0.055, 'grooveWall']);
}
const V = (y, zone, d = 0.012) => [[R.tube, y - d, 'tube'], [R.tube - d, y, zone]];   // shallow 45 deg V line: down-flank ends at y
const roll = 0.06;
const MODELS = {suppressor: {bone: 'pistol_suppressor_root', anchors: [{name: 'muzzle_exit_anchor', pivot: [0, 0, -Y.front]}],
  bounds: {width: 2, height: 1.5, offset: [0, 0.25, 0]}, parts: [
    {name: 'suppressor_booster', runs: [{region: 'booster', map: 'strip', pts: [
      [0, 0.30], [R.mount, 0.30, 'mountFloor'], [R.mount, 0, 'mountWall'],          // mount bore (internal thread), dark floor
      [0.62, 0, 'rearFace'], [R.booster, 0.04, 'rearEdge'],                          // machined mounting face, turned chamfer
      ...grooves, [R.booster, 0.855, 'land'], [R.neck, Y.booster, 'frontEdge'], [R.neck, Y.neck, 'neck']]}]},
    {name: 'suppressor_tube', runs: [{region: 'tube', map: 'strip', pts: [
      [R.neck, Y.neck], [R.tube, Y.neck + 0.025, 'tubeEdge'],
      ...V(Y.line, 'line'), [R.tube, Y.line + 0.012, 'line'],                         // module split line
      ...V(Y.seam, 'seam')]}]},                                                        // rear half of the cap seam
    {name: 'suppressor_front_cap', runs: [{region: 'cap', map: 'strip', pts: [
      [R.tube - 0.012, Y.seam], [R.cap, Y.seam + 0.012, 'seam'], [R.cap, Y.front - roll, 'cap'],
      [R.cap - roll + roll * Math.SQRT1_2, Y.front - roll + roll * Math.SQRT1_2, 'capRoll'], [R.cap - roll, Y.front, 'capRoll'],
      [R.bore + 0.05, Y.front, 'capFace'], [R.bore, Y.front - 0.05, 'boreMouth'],   // flat end face, countersunk exit
      [R.bore, Y.front - 0.50, 'bore'], [0, Y.front - 0.50, 'boreFloor']]}]},
  ]}};

// ---------------- materials ----------------
// Base Color sRGB, LabPBR smoothness (perceptual, 0-255), metal (F0 = Base Color) or dielectric (F0 0.04), AO.
// Booster: darker machined steel (black nitride, turned). Tube and cap: matte black ceramic coating. Bore: dark, rough.
const Z = {
  mountFloor:  {c: [14, 14, 15], sm: 50,  metal: true,  ao: 110},
  mountWall:   {c: [30, 30, 32], sm: 90,  metal: true,  ao: 170},
  rearFace:    {c: [60, 61, 64], sm: 150, metal: true},
  rearEdge:    {c: [74, 75, 78], sm: 165, metal: true},
  land:        {c: [56, 57, 60], sm: 140, metal: true},
  grooveWall:  {c: [44, 45, 47], sm: 120, metal: true,  ao: 215},
  grooveFloor: {c: [36, 37, 39], sm: 100, metal: true,  ao: 195},
  frontEdge:   {c: [68, 69, 72], sm: 155, metal: true},
  neck:        {c: [28, 29, 31], sm: 95,  metal: true,  ao: 175},
  tubeEdge:    {c: [42, 43, 45], sm: 78,  metal: false, ao: 225},
  tube:        {c: [36, 37, 39], sm: 70,  metal: false},
  line:        {c: [27, 28, 30], sm: 62,  metal: false, ao: 215},
  seam:        {c: [27, 28, 30], sm: 62,  metal: false, ao: 215},
  cap:         {c: [37, 38, 40], sm: 72,  metal: false},
  capRoll:     {c: [46, 47, 50], sm: 84,  metal: false},
  capFace:     {c: [36, 37, 39], sm: 68,  metal: false},
  boreMouth:   {c: [24, 23, 22], sm: 45,  metal: false, ao: 200},
  bore:        {c: [14, 14, 15], sm: 40,  metal: false, ao: 140},
  boreFloor:   {c: [8, 8, 9],    sm: 30,  metal: false, ao: 100},
};
const STEEL = new Set(['rearFace', 'rearEdge', 'land', 'grooveWall', 'grooveFloor', 'frontEdge']);
// smooth 3D value noise in [0, 1] (low frequency only: never per-pixel speckle)
const vn3 = (x, y, z) => {
  const xi = Math.floor(x), yi = Math.floor(y), zi = Math.floor(z), s = t => t * t * (3 - 2 * t), l = (a, b, t) => a + (b - a) * t;
  const sx = s(x - xi), sy = s(y - yi), sz = s(z - zi), h = (a, b, c) => hash(a * 7919 + c * 104729, b + 31 * c);
  return l(l(l(h(xi, yi, zi), h(xi + 1, yi, zi), sx), l(h(xi, yi + 1, zi), h(xi + 1, yi + 1, zi), sx), sy),
    l(l(h(xi, yi, zi + 1), h(xi + 1, yi, zi + 1), sx), l(h(xi, yi + 1, zi + 1), h(xi + 1, yi + 1, zi + 1), sx), sy), sz);
};
const zoneOf = zone => { const z = Z[zone]; if (!z) throw new Error('unpainted zone ' + zone); return z; };
const soot = (zone, p) => zone === 'capFace' ? sm(0.42, 0.25, Math.hypot(p[0], p[2])) : 0;   // carbon ring around the exit

function paint(model, zone, p, px) {
  const z = zoneOf(zone), up = px.nr * Math.sin(px.theta);          // model +Y component of the surface normal
  let k = 1 + 0.03 * up - 0.02 * Math.max(0, -up);                    // faint top light, same term as the P9 painter
  if (STEEL.has(zone)) k *= 1 + 0.012 * (hash(Math.round(p[1] * 400), 29) - 0.5);          // turning rings (texel rows)
  else if (!z.ao) k *= 1 + 0.02 * (vn3(p[0] * 2.2, p[1] * 0.9, p[2] * 2.2) - 0.5);         // coating: very soft mottling
  let c = sc(z.c, k);
  if (zone === 'capFace') c = mix(c, [22, 21, 20], 0.6 * soot(zone, p));
  if (zone === 'bore') c = sc(c, 1 - 0.35 * sm(Y.front - 0.05, Y.front - 0.45, p[1]));    // darker deeper in
  return c;
}
function pbr(model, zone, p, px) {
  const z = zoneOf(zone);
  let s = z.sm + (z.metal ? 5 : 3) * 2 * (vn3(p[0] * 3.2 + 7, p[1] * 1.3, p[2] * 3.2) - 0.5);
  if (zone === 'capFace') s += (40 - s) * soot(zone, p);
  // _n: 20-sided lathe facets shaded as the true cylinder under shader packs. Tangent-space x = +u = increasing lathe
  // angle; the ramp is continuous across every facet edge (edges lie on texel boundaries, alignFacets). Flat rings stay 128.
  let d = px.theta - px.am; d -= 2 * Math.PI * Math.round(d / (2 * Math.PI));
  const nx = 128 + 127 * px.nr * Math.sin(d);
  return {s: [s, z.metal ? 255 : 10, 0, 255], n: [nx, 128, z.ao || 255, 255]};
}

runLathe({root, N, atlas: 512, background: [30, 31, 33], uuidSeed: 'afl-pistol-suppressor-01-v2', models: MODELS, paint, pbr,
  phase: -Math.PI / 2, transform: p => [p[0], p[2], -p[1]], alignFacets: true, noOverdraw: true, meshFormat: 2, compact: true,
  sourceName: () => 'pistol_suppressor_01', geoId: () => 'geometry.pistol_suppressor_01',
  texture: {name: 'pistol_suppressor_01.png', relativePath: 'textures/pistol_suppressor_01.png'}, out: OUT});
