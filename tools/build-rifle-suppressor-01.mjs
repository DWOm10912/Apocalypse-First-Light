// AFL 7.62x51mm Rifle Suppressor V1 (rifle_suppressor_01): Pure Mesh lathe, one 512 atlas with Base Color and LabPBR
// _s / _n painted in the same pass (tools/lathe-mesh-lib.mjs builds, packs, paints and writes everything, V2 sidecar).
//   node tools/build-rifle-suppressor-01.mjs            -> writes source, atlas maps, geo and AFL mesh sidecar (V2)
//   node tools/build-rifle-suppressor-01.mjs --check    -> verifies every output is up to date
// Generic QD muzzle-device suppressor (mount_interface rifle_fh_qd, rated 7.62x51mm): it slides over the gun's flash hider
// and seats on the hider's rear collar. Mount contract (gun-agnostic, asset scale 1.0): origin = centre of the rear
// mounting face, muzzle forward = -Z, bone rifle_suppressor_root, child bone muzzle_exit_anchor at the real front-face
// bore centre. A gun puts its muzzle_slot anchor on the bore axis at its mounting shoulder; its flash hider must fit the
// rear cavity (radius CAVITY.r, depth CAVITY.depth; checked by tools/verify-rifle-suppressor-01.mjs).
// Units follow the BR51-01 model. Structure: machined rear QD mount with the flash-hider cavity, locking collar with two
// knurled bands (knurl in _n only) around a groove, recessed neck, matte black high-temperature-coated main tube with two
// very shallow segment lines, separate treated-steel front cap with a rolled edge, dark rough bore. No markings, no
// internal baffles. 20 segments around; the strip seam sits underneath.
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {runLathe, sm, mix, sc, hash} from './lathe-mesh-lib.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const assets = path.join(root, 'src/main/resources/assets/apocalypse_firstlight');
const src = path.join(root, 'src/main/blockbench');
const OUT = {
  suppressor: {src: path.join(src, 'rifle_suppressor_01.bbmodel'), geo: path.join(assets, 'geo/rifle_suppressor_01.geo.json'),
    mesh: path.join(assets, 'meshes/rifle_suppressor_01.aflmesh.json')},
  srcTexture: path.join(src, 'textures/rifle_suppressor_01.png'), texture: path.join(assets, 'textures/item/rifle_suppressor_01.png'),
  srcSpec: path.join(src, 'textures/rifle_suppressor_01_s.png'), spec: path.join(assets, 'textures/item/rifle_suppressor_01_s.png'),
  srcNormal: path.join(src, 'textures/rifle_suppressor_01_n.png'), normal: path.join(assets, 'textures/item/rifle_suppressor_01_n.png'),
};

// ---------------- dimensions (lathe space: r = radius, y = distance forward of the rear mounting face) ----------------
// BR51 reference: barrel r 0.328, flash hider collar r 0.5625 (the seat), hider body r 0.469, prong tips r 0.507 and
// 4.016 ahead of the seat. Tube r 0.92: 2.8x the barrel, 1.6x the hider collar (a 7.62 can, not a .50 one).
export const CAVITY = {r: 0.545, depth: 4.12};
export const R = {mount: 0.84, collar: 0.97, groove: 0.94, step: 0.93, neck: 0.88, tube: 0.92, bore: 0.26};
export const Y = {mount: 0.62, groove0: 1.10, groove1: 1.16, collar: 1.50, neck: 1.62, line1: 5.40, line2: 9.20, seam: 12.35, front: 13.20};
const N = 20;
// broad, very shallow V line (depth d over half-width w): readable ring, no sliver faces; down-flank ends at y
const LINE = {d: 0.015, w: 0.06}, SEAM = {d: 0.02, w: 0.04};
const V = (y, zone, {d, w} = LINE) => [[R.tube, y - w, 'tube'], [R.tube - d, y, zone]];
const Vup = (y, zone, {d, w} = LINE) => [R.tube, y + w, zone];
const roll = 0.07;
// A profile becomes several runs: cylindrical stretches unwrap as 'strip' (rectangles -> affine UV), tapered or annular
// stretches as 'facets' (each facet flat and congruent in its own column -> affine UV), so AFL Mesh V2 keeps them as quads.
const runs = (base, pts) => {
  const out = []; let cur = null, i = 0;
  for (let k = 1; k < pts.length; k++) {
    const map = Math.abs(pts[k][0] - pts[k - 1][0]) < 1e-9 ? 'strip' : 'facets';
    if (!cur || cur.map !== map) { cur = {region: `${base}_${i++}`, map, pts: [[pts[k - 1][0], pts[k - 1][1]]]}; out.push(cur); }
    cur.pts.push(pts[k]);
  }
  return out;
};
const MODELS = {suppressor: {bone: 'rifle_suppressor_root', anchors: [{name: 'muzzle_exit_anchor', pivot: [0, 0, -Y.front]}],
  bounds: {width: 2, height: 1.5, offset: [0, 0.25, 0]}, parts: [
    {name: 'suppressor_qd_mount', runs: runs('mount', [
      [0, CAVITY.depth], [CAVITY.r, CAVITY.depth, 'cavityFloor'], [CAVITY.r, 0, 'cavityWall'],   // flash-hider cavity
      [0.80, 0, 'rearFace'], [R.mount, 0.04, 'mountEdge'],                                         // machined mounting face
      [R.mount, Y.mount, 'mount']])},
    {name: 'suppressor_locking_collar', runs: runs('collar', [
      [R.mount, Y.mount], [R.step, Y.mount, 'collarStep'], [R.collar, Y.mount + 0.04, 'collarEdge'],
      [R.collar, Y.groove0, 'knurl'], [R.groove, Y.groove0, 'grooveWall'], [R.groove, Y.groove1, 'grooveFloor'],
      [R.collar, Y.groove1, 'grooveWall'], [R.collar, Y.collar - 0.04, 'knurl'], [R.step, Y.collar, 'collarEdge'],
      [R.neck, Y.collar, 'collarStep'], [R.neck, Y.neck, 'neck']])},
    {name: 'suppressor_main_tube', runs: runs('tube', [
      [R.neck, Y.neck], [R.tube, Y.neck + 0.04, 'tubeEdge'],
      ...V(Y.line1, 'line'), Vup(Y.line1, 'line'),                                                // segment line 1
      ...V(Y.line2, 'line'), Vup(Y.line2, 'line'),                                                // segment line 2
      ...V(Y.seam, 'seam', SEAM)])},                                                                   // rear half of the cap seam
    {name: 'suppressor_front_cap', runs: runs('cap', [
      [R.tube - SEAM.d, Y.seam], Vup(Y.seam, 'seam', SEAM), [R.tube, Y.front - roll, 'cap'],
      [R.tube - roll + roll * Math.SQRT1_2, Y.front - roll + roll * Math.SQRT1_2, 'capRoll'], [R.tube - roll, Y.front, 'capRoll'],
      [R.bore + 0.06, Y.front, 'capFace'], [R.bore, Y.front - 0.06, 'boreMouth'],               // flat end face, countersunk exit
      [R.bore, Y.front - 0.90, 'bore'], [0, Y.front - 0.90, 'boreFloor']])},
  ]}};

// ---------------- materials ----------------
// Base Color sRGB, LabPBR smoothness (perceptual 0-255), f0: 'metal' (F0 = Base Color) or a linear dielectric F0 (0-229),
// ao: material occlusion. Mount / collar / cap: dark machined or treated steel (metal, value steps + smoothness). Tube:
// high-temperature ceramic-type coating over steel -> matte, low but not plastic-flat specular (F0 ~0.09). Bore: dark, rough.
const Z = {
  cavityFloor: {c: [12, 12, 13], sm: 45,  f0: 'metal', ao: 100},
  cavityWall:  {c: [24, 24, 26], sm: 80,  f0: 'metal', ao: 150},
  rearFace:    {c: [50, 51, 54], sm: 145, f0: 'metal'},
  mountEdge:   {c: [64, 65, 68], sm: 165, f0: 'metal'},
  mount:       {c: [46, 47, 50], sm: 138, f0: 'metal'},
  collarStep:  {c: [48, 49, 52], sm: 140, f0: 'metal', ao: 225},
  collarEdge:  {c: [62, 63, 66], sm: 160, f0: 'metal'},
  knurl:       {c: [44, 45, 48], sm: 118, f0: 'metal'},
  grooveWall:  {c: [34, 35, 37], sm: 110, f0: 'metal', ao: 210},
  grooveFloor: {c: [30, 31, 33], sm: 100, f0: 'metal', ao: 195},
  neck:        {c: [28, 29, 31], sm: 95,  f0: 'metal', ao: 180},
  tubeEdge:    {c: [42, 43, 45], sm: 84,  f0: 24, ao: 225},
  tube:        {c: [35, 36, 38], sm: 72,  f0: 24},
  line:        {c: [27, 28, 30], sm: 64,  f0: 24, ao: 215},
  seam:        {c: [27, 28, 30], sm: 64,  f0: 24, ao: 215},
  cap:         {c: [40, 41, 44], sm: 108, f0: 'metal'},
  capRoll:     {c: [54, 55, 58], sm: 132, f0: 'metal'},
  capFace:     {c: [38, 39, 42], sm: 100, f0: 'metal'},
  boreMouth:   {c: [24, 23, 22], sm: 45,  f0: 10, ao: 200},
  bore:        {c: [13, 13, 14], sm: 35,  f0: 10, ao: 130},
  boreFloor:   {c: [7, 7, 8],    sm: 28,  f0: 10, ao: 90},
};
const STEEL = new Set(['rearFace', 'mountEdge', 'mount', 'collarStep', 'collarEdge', 'knurl', 'grooveWall', 'grooveFloor', 'cap', 'capRoll', 'capFace']);
const COATING = new Set(['tube', 'tubeEdge']);
// smooth 3D value noise in [0, 1] (low frequency only: never per-pixel speckle)
const vn3 = (x, y, z) => {
  const xi = Math.floor(x), yi = Math.floor(y), zi = Math.floor(z), s = t => t * t * (3 - 2 * t), l = (a, b, t) => a + (b - a) * t;
  const sx = s(x - xi), sy = s(y - yi), sz = s(z - zi), h = (a, b, c) => hash(a * 7919 + c * 104729, b + 31 * c);
  return l(l(l(h(xi, yi, zi), h(xi + 1, yi, zi), sx), l(h(xi, yi + 1, zi), h(xi + 1, yi + 1, zi), sx), sy),
    l(l(h(xi, yi, zi + 1), h(xi + 1, yi, zi + 1), sx), l(h(xi, yi + 1, zi + 1), h(xi + 1, yi + 1, zi + 1), sx), sy), sz);
};
const zoneOf = zone => { const z = Z[zone]; if (!z) throw new Error('unpainted zone ' + zone); return z; };
const soot = (zone, p) => zone === 'capFace' ? sm(0.52, 0.30, Math.hypot(p[0], p[2])) : 0;   // faint carbon ring at the exit
// straight diamond knurl on the collar bands: height field in (arc length around, axial), pitch KP, smooth (no hard texel edges)
const KP = 0.16;
const knurlH = (s, y) => { const a = Math.sin(Math.PI * (s + y) / KP), b = Math.sin(Math.PI * (s - y) / KP); return Math.abs(a) * Math.abs(b); };

function paint(model, zone, p, px) {
  const z = zoneOf(zone), up = px.nr * Math.sin(px.theta);          // model +Y component of the surface normal
  let k = 1 + 0.03 * up - 0.02 * Math.max(0, -up);                    // faint top light, same term as the P9 / BR51 painters
  if (STEEL.has(zone)) k *= 1 + 0.012 * (hash(Math.round(p[1] * 300), 29) - 0.5);          // turning rings (texel rows)
  else if (COATING.has(zone)) k *= 1 + 0.02 * (vn3(p[0] * 1.8, p[1] * 0.6, p[2] * 1.8) - 0.5);   // coating: very soft mottling
  let c = sc(z.c, k);
  if (zone === 'capFace') c = mix(c, [22, 21, 20], 0.45 * soot(zone, p));
  if (zone === 'bore') c = sc(c, 1 - 0.35 * sm(Y.front - 0.06, Y.front - 0.8, p[1]));      // darker deeper in
  if (zone === 'cavityWall') c = sc(c, 1 - 0.4 * sm(0.3, 2.5, p[1]));                        // hider cavity fades into shadow
  return c;
}
function pbr(model, zone, p, px) {
  const z = zoneOf(zone), metal = z.f0 === 'metal';
  let s = z.sm + (metal ? 5 : 3) * 2 * (vn3(p[0] * 3.2 + 7, p[1] * 1.1, p[2] * 3.2) - 0.5);
  if (zone === 'capFace') s += (40 - s) * soot(zone, p);
  // _n: 20-sided lathe facets shaded as the true cylinder under shader packs (tangent x = +u = increasing lathe angle; the
  // ramp is continuous across facet edges, which lie on texel boundaries). Knurl adds a shallow slope field on the collar.
  let d = px.theta - px.am; d -= 2 * Math.PI * Math.round(d / (2 * Math.PI));
  let nx = px.nr * Math.sin(d), ny = 0;
  if (zone === 'knurl') {
    const r = Math.hypot(p[0], p[2]), sArc = px.theta * r, e = 0.01, A = 0.022;
    const gx = (knurlH(sArc + e, p[1]) - knurlH(sArc - e, p[1])) / (2 * e), gy = (knurlH(sArc, p[1] + e) - knurlH(sArc, p[1] - e)) / (2 * e);
    nx -= A * gx; ny -= A * gy; s -= 10 * knurlH(sArc, p[1]);           // valleys hold a little more dirt-free roughness
  }
  const l = Math.hypot(nx, ny, 1);
  return {s: [s, metal ? 255 : z.f0, 0, 255], n: [128 + 127 * nx / l, 128 + 127 * ny / l, z.ao || 255, 255]};
}

// importing this module (tools/verify-rifle-suppressor-01.mjs reads CAVITY / R / Y) must not rebuild anything
if (path.resolve(process.argv[1] || '') === fileURLToPath(import.meta.url)) runLathe({root, N, atlas: 512, background: [30, 31, 33], uuidSeed: 'afl-rifle-suppressor-01-v1', models: MODELS, paint, pbr,
  phase: -Math.PI / 2, transform: p => [p[0], p[2], -p[1]], alignFacets: true, noOverdraw: true, meshFormat: 2, compact: true,
  posDecimals: 12, uvDecimals: 9,   // full bake precision: V2 keeps a small planar quad only when the source proves it
  sourceName: () => 'rifle_suppressor_01', geoId: () => 'geometry.rifle_suppressor_01',
  texture: {name: 'rifle_suppressor_01.png', relativePath: 'textures/rifle_suppressor_01.png'}, out: OUT});
