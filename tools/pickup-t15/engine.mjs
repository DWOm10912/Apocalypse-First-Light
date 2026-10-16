// T15 engine bay (mesh pickup_t15_engine, bone body; drawn while the hood is open). Design doc: front-mounted longitudinal
// 2.5 L inline-four turbo-diesel (structure per the reference 3L / 2L-T layout: injection pump and four steel lines on the
// left, exhaust manifold and turbo on the right, no intercooler). Block with ribbed skirt, oil pan with its rear sump, head,
// ribbed valve cover with the filler cap, timing cover, crank / water pump / alternator pulleys on one belt, fan in its
// shroud, radiator with tanks and hoses; intake manifold and runners, injection pump, lines and injectors, dipstick;
// exhaust manifold, turbo housings, downpipe; air box and hose; battery on its tray with the hold-down; coolant and washer
// reservoirs; brake booster and master cylinder (driver side, left), fuse box; wiring loom along the left apron; heater
// hoses into the firewall; skid plate. Concept frame (metres, x forward, y up, z right).
import {FX as FRX, FY, FZ, slab, rbox, abox, obox, sweep, tube, lathe, roundPath, fillet, circle, norm, add, sub, mul, cross} from '../vehicle-mesh-kit.mjs';
import {P, sym} from './common.mjs';

const E = mat => P('engine', 'body', mat);
const CRANK = [1.92, 0.58, 0], PUMP = [1.92, 0.73, 0], ALT = [1.90, 0.845, -0.215];

/** 2D convex hull (monotone chain). */
function hull(pts) {
  const Q = pts.slice().sort((a, b) => a[0] - b[0] || a[1] - b[1]), cr = (o, a, b) => (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
  const lo = [], hi = [];
  for (const p of Q) { while (lo.length >= 2 && cr(lo[lo.length - 2], lo[lo.length - 1], p) <= 0) lo.pop(); lo.push(p); }
  for (const p of Q.slice().reverse()) { while (hi.length >= 2 && cr(hi[hi.length - 2], hi[hi.length - 1], p) <= 0) hi.pop(); hi.push(p); }
  return lo.slice(0, -1).concat(hi.slice(0, -1));
}
/** A drive belt around pulleys [[z, y, r]] in the plane x: the hull of the pulley rims, swept as a flat band. */
function belt(x, pulleys) {
  const pts = []; for (const [z, y, r] of pulleys) for (let i = 0; i < 48; i++) { const a = i / 48 * Math.PI * 2; pts.push([z + (r + 0.003) * Math.cos(a), y + (r + 0.003) * Math.sin(a)]); }
  const path = hull(pts).map(([z, y]) => [x, y, z]);
  sweep(E('belting'), path, [[-0.009, -0.003], [0.009, -0.003], [0.009, 0.003], [-0.009, 0.003]], {closed: true, up: [1, 0, 0]});
}
/** A pulley on the x axis: hub, grooved rim. */
function pulley(c, r, w, mat = 'alu') {
  lathe(E(mat), c, [1, 0, 0], [[-w / 2, 0], [-w / 2, r * 0.45], [-w / 2 + 0.004, r * 0.5], [-w / 2 + 0.004, r], [-0.002, r], [-0.002, r - 0.006], [0.002, r - 0.006], [0.002, r],
    [w / 2, r], [w / 2, r * 0.3], [w / 2 + 0.008, r * 0.25], [w / 2 + 0.008, 0]], 24);
}

function block() {
  // block: the main casting, a ribbed skirt flaring to the pan rail, bell housing behind
  abox(E('engine'), 1.22, 1.86, 0.53, 0.80, -0.145, 0.145, 0.014);
  sym(s => {
    for (let x = 1.30; x < 1.82; x += 0.12) abox(E('engine'), x - 0.012, x + 0.012, 0.56, 0.78, ...(s > 0 ? [0.14, 0.16] : [-0.16, -0.14]), 0.004);   // main bearing ribs
  });
  slab(E('engine'), FRX, fillet([[-0.165, 0.515], [0.165, 0.515], [0.155, 0.55], [-0.155, 0.55]], 0.01, 2), [], 1.23, 1.85, {r0: 0.006, r1: 0.006});   // pan rail
  // oil pan: shallow front, deep sump at the rear (over the front axle it stays shallow)
  slab(E('engine'), FZ, fillet([[1.27, 0.48], [1.50, 0.48], [1.56, 0.43], [1.80, 0.43], [1.82, 0.515], [1.27, 0.515]], [0.02, 0.04, 0.02, 0.02, 0.01, 0.01], 3), [], -0.13, 0.13, {r0: 0.012, r1: 0.012});
  lathe(E('steel'), [1.70, 0.43, 0.04], [0, -1, 0], [[0, 0], [0, 0.012], [0.008, 0.012], [0.012, 0.008], [0.012, 0]], 6, {phase: 0});   // drain plug
  // bell housing (aluminium) toward the gearbox
  lathe(E('alu'), [1.22, 0.58, 0], [-1, 0, 0], [[0, 0.2], [0.04, 0.2], [0.10, 0.17], [0.13, 0.14], [0.13, 0.11], [0.0, 0.11]], 28);
  // cylinder head and ribbed valve cover, oil filler cap, breather hose
  abox(E('engine'), 1.24, 1.84, 0.80, 0.875, -0.14, 0.14, 0.012);
  slab(E('cover'), FRX, fillet([[-0.125, 0.875], [0.125, 0.875], [0.118, 0.912], [0.09, 0.935], [-0.09, 0.935], [-0.118, 0.912]], [0.004, 0.004, 0.02, 0.02, 0.02, 0.02], 3), [], 1.26, 1.82, {r0: 0.02, r1: 0.02});
  for (let x = 1.34; x < 1.80; x += 0.075) rbox(E('cover'), FRX, -0.10, 0.10, 0.93, 0.94, x - 0.008, x + 0.008, 0.003, {rc: 0.004});
  lathe(E('trim'), [1.70, 0.935, 0.05], [0, 1, 0], [[0, 0], [0, 0.03], [0.012, 0.03], [0.02, 0.026], [0.024, 0.016], [0.024, 0]], 14);
  tube(E('rubber'), roundPath([[1.32, 0.935, 0.07], [1.30, 0.97, 0.07], [1.25, 0.90, 0.20]], 0.03, 3), 0.009, 8);
  // timing cover (front face), front oil seal
  slab(E('engine'), FRX, fillet([[-0.13, 0.52], [0.13, 0.52], [0.13, 0.74], [0.08, 0.86], [-0.10, 0.86], [-0.13, 0.80]], [0.04, 0.04, 0.03, 0.04, 0.04, 0.03], 3), [], 1.86, 1.895, {r0: 0, r1: 0.012});
  // engine mounts to the crossmember
  sym(s => abox(E('rubber'), 1.26, 1.34, 0.475, 0.53, s * 0.17 - 0.03, s * 0.17 + 0.03, 0.008));
}

function frontDrive() {
  // pulleys and the belt; alternator on its bracket; fan on the pump; shroud; radiator, tanks, cap, hoses
  pulley(CRANK, 0.075, 0.03); pulley(PUMP, 0.06, 0.026); pulley([ALT[0] + 0.02, ALT[1], ALT[2]], 0.035, 0.026);
  belt(1.92, [[CRANK[2], CRANK[1], 0.075], [PUMP[2], PUMP[1], 0.06], [ALT[2], ALT[1], 0.035]]);
  lathe(E('alu'), ALT, [1, 0, 0], [[-0.07, 0], [-0.07, 0.055], [-0.06, 0.065], [0.05, 0.065], [0.06, 0.055], [0.06, 0]], 18);
  for (let k = 0; k < 8; k++) { const a = k / 8 * Math.PI * 2; abox(E('alu'), ALT[0] + 0.058, ALT[0] + 0.064, ALT[1] + 0.045 * Math.sin(a) - 0.004, ALT[1] + 0.045 * Math.sin(a) + 0.004, ALT[2] + 0.045 * Math.cos(a) - 0.004, ALT[2] + 0.045 * Math.cos(a) + 0.004, 0); }
  abox(E('engine'), 1.80, 1.86, 0.74, 0.80, -0.20, -0.12, 0.006);   // alternator bracket
  tube(E('alu'), [[1.92, 0.73, 0], [1.99, 0.73, 0]], 0.03, 12);   // pump shaft / fan clutch
  lathe(E('trim'), [1.995, 0.73, 0], [1, 0, 0], [[-0.02, 0], [-0.02, 0.07], [0.02, 0.07], [0.02, 0]], 18);
  for (let k = 0; k < 7; k++) {   // seven pitched blades
    const a = k / 7 * Math.PI * 2, rad = [0, Math.sin(a), Math.cos(a)], tan = [0, Math.cos(a), -Math.sin(a)];
    obox(E('trim'), add([2.0, 0.73, 0], mul(rad, 0.14)), rad, norm(add(tan, [0.6, 0, 0])), 0.075, 0.034, 0.002, 0);
  }
  slab(E('trim'), FRX, fillet([[-0.35, 0.495], [0.35, 0.495], [0.35, 0.965], [-0.35, 0.965]], 0.03, 2), [circle(0.215, 28).map(([z, y]) => [z, y + 0.73])], 2.05, 2.062, {r0: 0, r1: 0});
  lathe(E('trim'), [2.05, 0.73, 0], [-1, 0, 0], [[0, 0.215], [0.05, 0.215], [0.05, 0.225], [0, 0.225]], 28);   // shroud ring
  // radiator: core, tanks, side brackets, cap; hoses
  abox(E('radiator'), 2.10, 2.15, 0.575, 0.905, -0.34, 0.34, 0);
  abox(E('trim'), 2.095, 2.155, 0.905, 0.95, -0.35, 0.35, 0.01);
  abox(E('trim'), 2.095, 2.155, 0.54, 0.575, -0.35, 0.35, 0.01);
  sym(s => abox(E('under'), 2.09, 2.16, 0.54, 0.95, s * 0.355 - 0.012, s * 0.355 + 0.012, 0.004));
  lathe(E('steel'), [2.125, 0.95, 0.25], [0, 1, 0], [[0, 0], [0, 0.016], [0.012, 0.016], [0.014, 0.026], [0.02, 0.026], [0.022, 0]], 14);
  tube(E('rubber'), roundPath([[2.09, 0.93, -0.22], [2.00, 0.93, -0.18], [1.90, 0.87, -0.08], [1.86, 0.86, -0.06]], 0.05, 4), 0.019, 10);
  tube(E('rubber'), roundPath([[2.09, 0.56, 0.22], [2.02, 0.56, 0.20], [1.95, 0.60, 0.10], [1.88, 0.62, 0.06]], 0.05, 4), 0.019, 10);
}

function intakeSide() {
  // intake manifold (left): plenum along the head, four short runners
  slab(E('alu'), FZ, fillet([[1.30, 0.80], [1.78, 0.80], [1.78, 0.86], [1.30, 0.86]], 0.02, 2), [], -0.24, -0.19, {r0: 0.012, r1: 0.006});
  for (const x of [1.36, 1.49, 1.62, 1.75]) tube(E('alu'), [[x, 0.83, -0.19], [x, 0.83, -0.14]], 0.022, 10);
  // injection pump on the block's left front, four steel lines rising to the injectors on the head
  lathe(E('alu'), [1.73, 0.64, -0.24], [1, 0, 0], [[-0.10, 0], [-0.10, 0.05], [0.02, 0.055], [0.06, 0.045], [0.07, 0.03], [0.07, 0]], 16);
  abox(E('alu'), 1.64, 1.78, 0.68, 0.73, -0.27, -0.21, 0.008);
  [1.36, 1.49, 1.62, 1.75].forEach((xi, i) => {
    const xp = 1.66 + 0.03 * i;
    tube(E('steel'), roundPath([[xp, 0.72, -0.25], [xp, 0.80, -0.27], [xp - 0.02, 0.89, -0.25], [xi + 0.03, 0.93, -0.16], [xi, 0.935, -0.13]], 0.03, 3), 0.0035, 5);
    lathe(E('steel'), [xi, 0.90, -0.13], [0, 1, 0], [[0, 0], [0, 0.011], [0.035, 0.011], [0.04, 0.007], [0.045, 0]], 8);
  });
  // dipstick tube and yellow handle; boost pipe from the turbo over the back of the head into the plenum
  tube(E('steel'), [[1.42, 0.60, -0.16], [1.43, 0.80, -0.20], [1.44, 0.95, -0.22]], 0.004, 5);
  lathe(E('yellow'), [1.44, 0.955, -0.22], [0, 1, 0], [[0, 0], [0, 0.016], [0.02, 0.018], [0.03, 0.01], [0.03, 0]], 8);
  tube(E('alu'), roundPath([[1.52, 0.86, 0.30], [1.22, 0.88, 0.30], [1.20, 0.88, -0.22], [1.30, 0.84, -0.22]], 0.06, 4), 0.026, 10);
}

function exhaustSide() {
  // exhaust manifold (right): four runners into a collector, heat shield over it
  for (const x of [1.36, 1.49, 1.62, 1.75]) tube(E('iron'), roundPath([[x, 0.70, 0.14], [x, 0.70, 0.18], [(x + 1.55) / 2, 0.70, 0.22]], 0.03, 3), 0.02, 8);
  slab(E('iron'), FZ, fillet([[1.32, 0.67], [1.78, 0.67], [1.78, 0.735], [1.32, 0.735]], 0.02, 2), [], 0.20, 0.235, {r0: 0.006, r1: 0.012});
  slab(E('steel'), FZ, fillet([[1.30, 0.66], [1.80, 0.66], [1.80, 0.76], [1.30, 0.76]], 0.02, 2), [], 0.245, 0.25, {r0: 0, r1: 0});
  // turbo: turbine housing (iron) on the collector, centre section, compressor housing (aluminium); downpipe
  lathe(E('iron'), [1.50, 0.80, 0.30], [1, 0, 0], [[-0.045, 0], [-0.045, 0.06], [-0.025, 0.075], [0.01, 0.075], [0.02, 0.05], [0.02, 0]], 18);
  tube(E('iron'), [[1.50, 0.735, 0.24], [1.50, 0.785, 0.29]], 0.03, 10);
  lathe(E('steel'), [1.53, 0.80, 0.30], [1, 0, 0], [[-0.01, 0], [-0.01, 0.035], [0.02, 0.035], [0.02, 0]], 12);
  lathe(E('alu'), [1.57, 0.80, 0.30], [1, 0, 0], [[-0.02, 0], [-0.02, 0.068], [0.015, 0.07], [0.03, 0.05], [0.05, 0.04], [0.05, 0]], 18);
  tube(E('iron'), roundPath([[1.46, 0.79, 0.30], [1.40, 0.72, 0.31], [1.36, 0.52, 0.30], [1.30, 0.44, 0.30]], 0.06, 4), 0.03, 10);
  // air box at the right front, hose to the compressor inlet
  slab(E('trim'), FY, fillet([[1.84, 0.36], [2.03, 0.36], [2.03, 0.54], [1.84, 0.54]], 0.025, 3), [], 0.70, 0.90, {r0: 0.01, r1: 0.02});
  abox(E('trim'), 1.835, 2.035, 0.835, 0.845, 0.355, 0.545, 0.004);   // lid seam
  sym(s => s > 0 && [1.88, 1.99].forEach(x => abox(E('steel'), x - 0.012, x + 0.012, 0.825, 0.86, 0.538, 0.548, 0.002)));   // clips
  tube(E('rubber'), roundPath([[1.84, 0.80, 0.42], [1.72, 0.80, 0.40], [1.64, 0.80, 0.32], [1.62, 0.80, 0.30]], 0.05, 4), 0.035, 12);
}

function firewallSide() {
  // battery (left front): tray, case, hold-down frame, terminals with caps, cables
  abox(E('under'), 1.82, 2.05, 0.59, 0.61, -0.55, -0.28, 0.004);
  abox(E('trim'), 1.835, 2.035, 0.61, 0.81, -0.535, -0.295, 0.008);
  abox(E('trim'), 1.84, 2.03, 0.81, 0.82, -0.53, -0.30, 0.004);
  abox(E('steel'), 1.93, 1.95, 0.82, 0.83, -0.545, -0.285, 0.003);
  for (const [z, m] of [[-0.35, 'lead'], [-0.48, 'redcap']]) lathe(E(m), [1.88, 0.82, z], [0, 1, 0], [[0, 0], [0, 0.014], [0.02, 0.013], [0.024, 0]], 10);
  tube(E('trim'), roundPath([[1.88, 0.845, -0.48], [1.88, 0.88, -0.48], [1.80, 0.88, -0.40], [1.75, 0.70, -0.25]], 0.04, 3), 0.008, 8);
  tube(E('trim'), roundPath([[1.88, 0.845, -0.35], [1.88, 0.87, -0.35], [1.94, 0.87, -0.30], [1.96, 0.62, -0.20]], 0.04, 3), 0.008, 8);
  // coolant reservoir (right, near the firewall), fuse box, washer bottle (left)
  slab(E('cream'), FY, fillet([[1.00, 0.38], [1.14, 0.38], [1.14, 0.52], [1.00, 0.52]], 0.03, 3), [], 0.72, 0.88, {r0: 0.01, r1: 0.02});
  lathe(E('trim'), [1.07, 0.88, 0.45], [0, 1, 0], [[0, 0], [0, 0.026], [0.016, 0.024], [0.02, 0]], 12);
  tube(E('rubber'), roundPath([[1.07, 0.75, 0.38], [1.20, 0.75, 0.30], [2.08, 0.95, 0.26]], 0.1, 4), 0.005, 6);
  abox(E('trim'), 1.00, 1.12, 0.76, 0.86, 0.16, 0.32, 0.01);
  abox(E('trim'), 1.01, 1.11, 0.86, 0.872, 0.17, 0.31, 0.004);
  slab(E('washer'), FY, fillet([[1.40, -0.55], [1.56, -0.55], [1.56, -0.42], [1.40, -0.42]], 0.025, 3), [], 0.68, 0.86, {r0: 0.008, r1: 0.016});
  lathe(E('trim'), [1.48, 0.86, -0.48], [0, 1, 0], [[0, 0], [0, 0.02], [0.012, 0.02], [0.014, 0]], 10);
  // brake booster (driver side, left) on the firewall, master cylinder and its fluid reservoir
  lathe(E('under'), [0.99, 0.82, -0.36], [1, 0, 0], [[0, 0], [0, 0.10], [0.02, 0.115], [0.07, 0.115], [0.09, 0.10], [0.095, 0]], 28);
  lathe(E('alu'), [1.085, 0.82, -0.36], [1, 0, 0], [[0, 0], [0, 0.03], [0.12, 0.026], [0.13, 0.02], [0.13, 0]], 14);
  slab(E('cream'), FY, fillet([[1.11, -0.39], [1.19, -0.39], [1.19, -0.33], [1.11, -0.33]], 0.012, 2), [], 0.85, 0.90, {r0: 0, r1: 0.008});
  lathe(E('trim'), [1.15, 0.90, -0.36], [0, 1, 0], [[0, 0], [0, 0.018], [0.012, 0.016], [0.014, 0]], 10);
  for (const dz of [-0.01, 0.01]) tube(E('steel'), roundPath([[1.17, 0.81, -0.36 + dz], [1.20, 0.76, -0.38 + dz], [1.20, 0.70, -0.50 + dz], [1.15, 0.64, -0.54 + dz]], 0.03, 3), 0.003, 5);
  // wiring loom along the left apron, heater hoses through the firewall
  tube(E('trim'), roundPath([[1.0, 0.92, -0.50], [1.40, 0.92, -0.53], [1.80, 0.88, -0.53], [1.86, 0.84, -0.50]], 0.08, 4), 0.008, 6);
  for (const [z, y] of [[0.10, 0.84], [0.16, 0.80]]) tube(E('rubber'), roundPath([[1.0, y, z], [1.10, y, z], [1.25, 0.74, z - 0.01], [1.35, 0.72, 0.14]], 0.06, 3), 0.009, 8);
  // skid plate under the front
  slab(E('under'), FY, fillet([[1.72, -0.36], [2.20, -0.36], [2.20, 0.36], [1.72, 0.36]], 0.04, 2), [], 0.425, 0.43, {r0: 0, r1: 0});   // ahead of the front axle
}

export function buildEngine() {
  block(); frontDrive(); intakeSide(); exhaustSide(); firewallSide();
}
