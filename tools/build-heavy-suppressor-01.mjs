// AFL 12.7x55mm Heavy Suppressor V1 (heavy_suppressor_01): Pure Mesh lathe, one 512 atlas with Base Color and LabPBR
// _s / _n painted in the same pass (tools/lathe-mesh-lib.mjs builds, packs, paints and writes everything, V2 sidecar).
//   node tools/build-heavy-suppressor-01.mjs            -> writes source, atlas maps, geo and AFL mesh sidecar (V2)
//   node tools/build-heavy-suppressor-01.mjs --check    -> verifies every output is up to date
// Generic heavy-rifle suppressor (mount_interface heavy_brake_qd, rated 12.7x55mm), overbore ("reflex") layout like the
// big integral cans of the 12.7x55mm family: the front cavity slides over the gun's muzzle brake and locks on the brake
// collar, and a rear sleeve runs back over the bare barrel almost to the handguard, so the gun reads as handguard + one
// long thick tube (length / diameter ~7.6). Mount contract (gun-agnostic, asset scale 1.0): origin = the mounting point on
// the bore axis at the rear face of the brake collar, muzzle forward = -Z, bone heavy_suppressor_root, child bone
// muzzle_exit_anchor at the real front-face bore centre. Ahead of the origin the gun's muzzle device must fit the front
// cavity (CAVITY); behind it, for SLEEVE.length, only a barrel thinner than the sleeve bore (SLEEVE.r); checked for every
// accepting gun by tools/verify-heavy-suppressor-01.mjs.
// Units follow the HR55 model (1 unit ~ 23.5 mm): tube diameter 2.9 (~68 mm), overall 22 (~520 mm).
// Structure: machined rear end cap with a knurled locking ring (knurl in _n only) and a recessed neck, plain matte black
// coated main tube, coated front cap behind a shallow seam with a large rounded front edge, flat end face, countersunk
// exit, dark rough bores. No markings, no internal baffles. 24 segments around; the strip seam sits underneath.
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {runLathe, sm, mix, sc, hash} from './lathe-mesh-lib.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const assets = path.join(root, 'src/main/resources/assets/apocalypse_firstlight');
const src = path.join(root, 'src/main/blockbench');
const OUT = {
  suppressor: {src: path.join(src, 'heavy_suppressor_01.bbmodel'), geo: path.join(assets, 'geo/heavy_suppressor_01.geo.json'),
    mesh: path.join(assets, 'meshes/heavy_suppressor_01.aflmesh.json')},
  srcTexture: path.join(src, 'textures/heavy_suppressor_01.png'), texture: path.join(assets, 'textures/item/heavy_suppressor_01.png'),
  srcSpec: path.join(src, 'textures/heavy_suppressor_01_s.png'), spec: path.join(assets, 'textures/item/heavy_suppressor_01_s.png'),
  srcNormal: path.join(src, 'textures/heavy_suppressor_01_n.png'), normal: path.join(assets, 'textures/item/heavy_suppressor_01_n.png'),
};

// ---------------- dimensions (lathe space: r = radius, y = distance forward of the mounting point) ----------------
// HR55 reference: barrel r 0.536, brake collar r 0.637 starting at the mount, octagonal brake r 0.93 ending 3.55 ahead,
// handguard front face 4.62 behind the mount.
export const CAVITY = {r: 1.0, depth: 3.7};          // front cavity over the brake
export const SLEEVE = {r: 0.6, length: 4.25};        // rear sleeve over the barrel (rear face 0.37 short of the HR55 handguard)
export const R = {rear: 1.34, ring: 1.40, neck: 1.33, tube: 1.45, roll: 0.28, bore: 0.40};
export const Y = {rear: -SLEEVE.length, ring0: -3.95, ring1: -3.25, neck: -3.10, tube: -2.98, seam: 16.55, front: 17.75};
const N = 24;
const SEAM = {d: 0.02, w: 0.05};
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
// large rounded front edge: quarter circle of radius R.roll in 4 steps (from the tube side to the end face)
const ROLL = [1, 2, 3, 4].map(k => { const a = Math.PI / 2 * k / 4; return [R.tube - R.roll + R.roll * Math.cos(a), Y.front - R.roll + R.roll * Math.sin(a), 'capRoll']; });
const MODELS = {suppressor: {bone: 'heavy_suppressor_root', anchors: [{name: 'muzzle_exit_anchor', pivot: [0, 0, -Y.front]}],
  bounds: {width: 3, height: 1.5, offset: [0, 0.25, 0]}, parts: [
    {name: 'suppressor_rear_mount', runs: runs('mount', [
      [0, CAVITY.depth], [CAVITY.r, CAVITY.depth, 'cavityFloor'], [CAVITY.r, 0, 'cavityWall'],        // front cavity over the brake
      [SLEEVE.r, 0, 'cavityStep'], [SLEEVE.r, Y.rear + 0.06, 'sleeveBore'],                            // rear sleeve over the barrel
      [SLEEVE.r + 0.06, Y.rear, 'boreChamfer'], [R.rear - 0.08, Y.rear, 'rearFace'],                   // machined rear face
      [R.rear, Y.rear + 0.08, 'rearEdge'], [R.rear, Y.ring0, 'rearCap']])},
    {name: 'suppressor_locking_ring', runs: runs('ring', [
      [R.rear, Y.ring0], [R.ring, Y.ring0 + 0.06, 'ringEdge'], [R.ring, Y.ring1 - 0.06, 'knurl'],
      [R.rear, Y.ring1, 'ringEdge'], [R.neck, Y.ring1, 'ringStep'], [R.neck, Y.neck, 'neck']])},
    {name: 'suppressor_main_tube', runs: runs('tube', [
      [R.neck, Y.neck], [R.tube, Y.tube, 'tubeEdge'],
      [R.tube, Y.seam - SEAM.w, 'tube'], [R.tube - SEAM.d, Y.seam, 'seam']])},                       // rear half of the cap seam
    {name: 'suppressor_front_cap', runs: runs('cap', [
      [R.tube - SEAM.d, Y.seam], [R.tube, Y.seam + SEAM.w, 'seam'], [R.tube, Y.front - R.roll, 'cap'], ...ROLL,
      [R.bore + 0.07, Y.front, 'capFace'], [R.bore, Y.front - 0.07, 'boreMouth'],                     // flat end face, countersunk exit
      [R.bore, Y.front - 1.3, 'bore'], [0, Y.front - 1.3, 'boreFloor']])},
  ]}};

// ---------------- materials ----------------
// Base Color sRGB, LabPBR smoothness (perceptual 0-255), f0: 'metal' (F0 = Base Color) or a linear dielectric F0 (0-229),
// ao: material occlusion. Rear cap / ring: dark machined or treated steel (metal). Tube and front cap: the same matte
// high-temperature coating as the 7.62 can (F0 ~0.09): large coated surfaces must not mirror the sky under shaders.
const Z = {
  cavityFloor: {c: [12, 12, 13], sm: 45,  f0: 'metal', ao: 100},
  cavityWall:  {c: [24, 24, 26], sm: 80,  f0: 'metal', ao: 150},
  cavityStep:  {c: [18, 18, 20], sm: 70,  f0: 'metal', ao: 130},
  sleeveBore:  {c: [22, 22, 24], sm: 72,  f0: 'metal', ao: 150},
  boreChamfer: {c: [40, 41, 44], sm: 130, f0: 'metal', ao: 220},
  rearFace:    {c: [46, 47, 50], sm: 140, f0: 'metal'},
  rearEdge:    {c: [60, 61, 64], sm: 160, f0: 'metal'},
  rearCap:     {c: [44, 45, 48], sm: 132, f0: 'metal'},
  ringEdge:    {c: [58, 59, 62], sm: 155, f0: 'metal'},
  knurl:       {c: [42, 43, 46], sm: 116, f0: 'metal'},
  ringStep:    {c: [40, 41, 44], sm: 128, f0: 'metal', ao: 215},
  neck:        {c: [28, 29, 31], sm: 95,  f0: 'metal', ao: 185},
  tubeEdge:    {c: [42, 43, 45], sm: 84,  f0: 24, ao: 225},
  tube:        {c: [35, 36, 38], sm: 72,  f0: 24},
  seam:        {c: [27, 28, 30], sm: 64,  f0: 24, ao: 215},
  cap:         {c: [36, 37, 39], sm: 76,  f0: 24},
  capRoll:     {c: [44, 45, 47], sm: 92,  f0: 24},
  capFace:     {c: [34, 35, 37], sm: 74,  f0: 24},
  boreMouth:   {c: [24, 23, 22], sm: 45,  f0: 10, ao: 200},
  bore:        {c: [13, 13, 14], sm: 35,  f0: 10, ao: 130},
  boreFloor:   {c: [7, 7, 8],    sm: 28,  f0: 10, ao: 90},
};
const STEEL = new Set(['boreChamfer', 'rearFace', 'rearEdge', 'rearCap', 'ringEdge', 'knurl', 'ringStep']);
const COATING = new Set(['tube', 'tubeEdge', 'cap', 'capRoll', 'capFace']);
// smooth 3D value noise in [0, 1] (low frequency only: never per-pixel speckle)
const vn3 = (x, y, z) => {
  const xi = Math.floor(x), yi = Math.floor(y), zi = Math.floor(z), s = t => t * t * (3 - 2 * t), l = (a, b, t) => a + (b - a) * t;
  const sx = s(x - xi), sy = s(y - yi), sz = s(z - zi), h = (a, b, c) => hash(a * 7919 + c * 104729, b + 31 * c);
  return l(l(l(h(xi, yi, zi), h(xi + 1, yi, zi), sx), l(h(xi, yi + 1, zi), h(xi + 1, yi + 1, zi), sx), sy),
    l(l(h(xi, yi, zi + 1), h(xi + 1, yi, zi + 1), sx), l(h(xi, yi + 1, zi + 1), h(xi + 1, yi + 1, zi + 1), sx), sy), sz);
};
const zoneOf = zone => { const z = Z[zone]; if (!z) throw new Error('unpainted zone ' + zone); return z; };
const soot = (zone, p) => zone === 'capFace' ? sm(0.78, 0.44, Math.hypot(p[0], p[2])) : 0;   // faint carbon ring at the exit
// straight diamond knurl on the locking ring: height field in (arc length around, axial), pitch KP, smooth (no hard texel edges)
const KP = 0.18;
const knurlH = (s, y) => { const a = Math.sin(Math.PI * (s + y) / KP), b = Math.sin(Math.PI * (s - y) / KP); return Math.abs(a) * Math.abs(b); };

function paint(model, zone, p, px) {
  const z = zoneOf(zone), up = px.nr * Math.sin(px.theta);          // model +Y component of the surface normal
  let k = 1 + 0.03 * up - 0.02 * Math.max(0, -up);                    // faint top light, same term as the other painters
  if (STEEL.has(zone)) k *= 1 + 0.012 * (hash(Math.round(p[1] * 300), 29) - 0.5);          // turning rings (texel rows)
  else if (COATING.has(zone)) k *= 1 + 0.02 * (vn3(p[0] * 1.2, p[1] * 0.4, p[2] * 1.2) - 0.5);   // coating: very soft mottling
  let c = sc(z.c, k);
  if (zone === 'capFace') c = mix(c, [22, 21, 20], 0.45 * soot(zone, p));
  if (zone === 'bore') c = sc(c, 1 - 0.35 * sm(Y.front - 0.07, Y.front - 1.2, p[1]));      // darker deeper in
  if (zone === 'cavityWall') c = sc(c, 1 - 0.4 * sm(0.3, 2.8, p[1]));                        // brake cavity fades into shadow
  if (zone === 'sleeveBore') c = sc(c, 1 - 0.35 * sm(Y.rear + 0.3, Y.rear + 2.5, p[1]));    // barrel sleeve fades inward
  return c;
}
function pbr(model, zone, p, px) {
  const z = zoneOf(zone), metal = z.f0 === 'metal';
  let s = z.sm + (metal ? 5 : 3) * 2 * (vn3(p[0] * 2.4 + 7, p[1] * 0.8, p[2] * 2.4) - 0.5);
  if (zone === 'capFace') s += (40 - s) * soot(zone, p);
  // _n: 24-sided lathe facets shaded as the true cylinder under shader packs (tangent x = +u = increasing lathe angle; the
  // ramp is continuous across facet edges, which lie on texel boundaries). Knurl adds a shallow slope field on the ring.
  let d = px.theta - px.am; d -= 2 * Math.PI * Math.round(d / (2 * Math.PI));
  let nx = px.nr * Math.sin(d), ny = 0;
  if (zone === 'knurl') {
    const r = Math.hypot(p[0], p[2]), sArc = px.theta * r, e = 0.01, A = 0.022;
    const gx = (knurlH(sArc + e, p[1]) - knurlH(sArc - e, p[1])) / (2 * e), gy = (knurlH(sArc, p[1] + e) - knurlH(sArc, p[1] - e)) / (2 * e);
    nx -= A * gx; ny -= A * gy; s -= 10 * knurlH(sArc, p[1]);
  }
  const l = Math.hypot(nx, ny, 1);
  return {s: [s, metal ? 255 : z.f0, 0, 255], n: [128 + 127 * nx / l, 128 + 127 * ny / l, z.ao || 255, 255]};
}

// importing this module (tools/verify-heavy-suppressor-01.mjs reads CAVITY / SLEEVE / R / Y) must not rebuild anything
if (path.resolve(process.argv[1] || '') === fileURLToPath(import.meta.url)) runLathe({root, N, atlas: 512, background: [30, 31, 33], uuidSeed: 'afl-heavy-suppressor-01-v1', models: MODELS, paint, pbr,
  phase: -Math.PI / 2, transform: p => [p[0], p[2], -p[1]], alignFacets: true, noOverdraw: true, meshFormat: 2, compact: true,
  posDecimals: 12, uvDecimals: 9,   // full bake precision: V2 keeps a small planar quad only when the source proves it
  sourceName: () => 'heavy_suppressor_01', geoId: () => 'geometry.heavy_suppressor_01',
  texture: {name: 'heavy_suppressor_01.png', relativePath: 'textures/heavy_suppressor_01.png'}, out: OUT});
