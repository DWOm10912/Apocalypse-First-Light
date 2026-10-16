// T15 chassis (mesh pickup_t15_chassis, bone body; drawn near). Structure per the reference drawings (design doc 2.4: frame,
// front axle and leaf springs, rear axle housing, propeller shafts, exhaust, fuel tank, spare carrier groups): ladder frame
// with the kick-up over the rear axle and six crossmembers; solid front axle (driven, the carrier offset to the left) and
// banjo rear axle, both on five-leaf springs over the axle with hangers, shackles and U-bolts; four shock absorbers; steering
// box on the left rail, pitman arm, drag link and tie rod; gearbox and transfer case under the tunnel, front and rear
// propeller shafts with their joints; exhaust from the downpipe through the muffler, over the rear axle outside the right
// rail to the outlet behind the right rear wheel; fuel tank with straps and filler hose (left); spare carrier and winch
// cable; tow hooks, bumper brackets, body mounts. Concept frame (metres, x forward, y up, z right).
import {FX as FRX, FY, FZ, slab, rbox, abox, obox, sweep, tube, lathe, roundPath, fillet, circle, add, mul, sub, norm} from '../vehicle-mesh-kit.mjs';
import {FX, RX, R, SPARE, P, sym} from './common.mjs';

const U = mat => P('chassis', 'body', mat);
const RZ = 0.41;                                              // rail centre |z|
/** Rail centre height at x (front, under the cab, kick-up over the rear axle). */
const railPath = roundPath([[2.245, 0.50, 0], [1.12, 0.50, 0], [0.95, 0.455, 0], [-0.40, 0.455, 0], [-0.85, 0.625, 0], [-2.245, 0.625, 0]], 0.15, 4);
export const railY = x => { for (let i = 0; i + 1 < railPath.length; i++) { const a = railPath[i], b = railPath[i + 1]; if (x <= a[0] && x >= b[0]) return a[1] + (b[1] - a[1]) * (x - a[0]) / (b[0] - a[0]); } return 0.5; };
const AXLE_Y = R, FDIFF_Z = -0.27;

function frame() {
  // side rails: boxed section, rounded corners
  sym(s => sweep(U('under'), railPath.map(([x, y]) => [x, y, s * RZ]), fillet([[-0.06, -0.0275], [0.06, -0.0275], [0.06, 0.0275], [-0.06, 0.0275]], 0.008, 2), {up: [0, 1, 0]}));
  // crossmembers: front (radiator support), engine (under the shallow part of the pan), transmission, centre, kick-up, rear
  for (const [x, w, h] of [[2.20, 0.05, 0.10], [1.30, 0.07, 0.05], [0.62, 0.06, 0.05], [-0.40, 0.05, 0.08], [-0.88, 0.05, 0.09], [-2.225, 0.035, 0.09]]) {
    const y = railY(x) - (x === 1.30 ? 0.05 : x === 0.62 ? 0.035 : 0);
    rbox(U('under'), FRX, -(RZ - 0.02), RZ - 0.02, y - h / 2, y + h / 2, x - w / 2, x + w / 2, 0.006, {rc: 0.01});
  }
  // body mounts (rubber pucks on outriggers) under the cab and the bed
  sym(s => { for (const x of [0.85, -0.25, -1.0, -2.0]) { const y = railY(x) + 0.06;
    abox(U('under'), x - 0.035, x + 0.035, y - 0.03, y - 0.01, s * 0.44, s * 0.52, 0.004);
    lathe(U('rubber'), [x, y - 0.01, s * 0.49], [0, 1, 0], [[0, 0], [0, 0.026], [0.016, 0.026], [0.016, 0]], 10); } });
  // tow hooks and bumper brackets at the rail ends
  sym(s => {
    tube(U('steel'), roundPath([[2.20, 0.46, s * RZ], [2.27, 0.46, s * RZ], [2.29, 0.43, s * RZ], [2.27, 0.40, s * RZ], [2.22, 0.40, s * RZ]], 0.02, 3), 0.009, 8);
    abox(U('under'), 2.24, 2.335, 0.48, 0.53, s * RZ - 0.025, s * RZ + 0.025, 0.004);
    abox(U('under'), -2.345, -2.24, 0.56, 0.60, s * RZ - 0.025, s * RZ + 0.025, 0.004);
  });
}

/** Five-leaf spring over an axle: main leaf with eyes at x0 (front hanger) and x1 (rear shackle), the axle seat at xc. */
function spring(z, x0, x1, xc, ySeat, eyeY) {
  const L = Math.max(xc - x1, x0 - xc), top = ySeat + 5 * 0.007;
  for (let k = 0; k < 5; k++) {
    const half = L * (1 - 0.17 * k), yk = top - 0.0035 - 0.007 * k, path = [];
    for (let i = 0; i <= 8; i++) { const t = -1 + 2 * i / 8, x = xc + t * half; path.push([x, yk + (eyeY - top) * t * t * (1 - 0.15 * k), z]); }
    sweep(U('under'), path, [[-0.0035, -0.03], [0.0035, -0.03], [0.0035, 0.03], [-0.0035, 0.03]], {up: [0, 1, 0]});
  }
  for (const xe of [xc - L, xc + L]) lathe(U('under'), [xe, eyeY + 0.016, z], [0, 0, 1], [[-0.032, 0.012], [0.032, 0.012], [0.032, 0.022], [-0.032, 0.022]], 12);
  // U-bolts over the axle seat and the clamp plate under it
  for (const dx of [-0.045, 0.045]) tube(U('steel'), roundPath([[xc + dx, ySeat - 0.075, z - 0.04], [xc + dx, top + 0.01, z - 0.04], [xc + dx, top + 0.03, z], [xc + dx, top + 0.01, z + 0.04], [xc + dx, ySeat - 0.075, z + 0.04]], 0.025, 3), 0.007, 6);
  abox(U('under'), xc - 0.07, xc + 0.07, AXLE_Y - 0.065, AXLE_Y - 0.05, z - 0.05, z + 0.05, 0.004);
  return {front: [xc + L, eyeY + 0.016], rear: [xc - L, eyeY + 0.016]};
}

function axles() {
  sym(s => {
    // front: fixed hanger ahead, shackle behind; rear: the same
    const fz = s * RZ, f = spring(fz, FX + 0.52, FX - 0.52, FX, AXLE_Y + 0.045, railY(FX) - 0.07);
    abox(U('under'), f.front[0] - 0.025, f.front[0] + 0.025, f.front[1], railY(f.front[0]) - 0.05, fz - 0.035, fz + 0.035, 0.004);
    for (const dz of [-0.04, 0.04]) obox(U('under'), [f.rear[0] - 0.02, (f.rear[1] + railY(f.rear[0]) - 0.06) / 2, fz + dz], norm([-0.35, 1, 0]), [1, 0.35, 0], 0.05, 0.02, 0.004, 0.002);
    const r = spring(fz, RX + 0.62, RX - 0.62, RX, AXLE_Y + 0.045, railY(RX) - 0.10);
    abox(U('under'), r.front[0] - 0.025, r.front[0] + 0.025, r.front[1], railY(r.front[0]) - 0.05, fz - 0.035, fz + 0.035, 0.004);
    for (const dz of [-0.04, 0.04]) obox(U('under'), [r.rear[0] - 0.02, (r.rear[1] + railY(r.rear[0]) - 0.06) / 2, fz + dz], norm([-0.25, 1, 0]), [1, 0.25, 0], 0.06, 0.02, 0.004, 0.002);
    // shock absorbers: body, chromed rod, bushings
    for (const [ax, top] of [[[FX + 0.12, AXLE_Y + 0.02, s * 0.33], [FX + 0.20, railY(FX + 0.2) + 0.02, s * 0.36]], [[RX - 0.10, AXLE_Y + 0.02, s * 0.30], [RX - 0.22, railY(RX - 0.22), s * 0.36]]]) {
      const d = norm(sub(top, ax)), len = Math.hypot(...sub(top, ax));
      lathe(U('under'), ax, d, [[0, 0], [0, 0.026], [len * 0.62, 0.026], [len * 0.62, 0]], 12);
      lathe(U('chrome'), ax, d, [[len * 0.6, 0], [len * 0.6, 0.009], [len, 0.009], [len, 0]], 8);
      for (const q of [ax, top]) lathe(U('rubber'), q, [1, 0, 0], [[-0.02, 0], [-0.02, 0.018], [0.02, 0.018], [0.02, 0]], 10);
    }
  });
  // front axle: housing tubes, carrier offset to the left, closed knuckles, tie rod behind
  tube(U('under'), [[FX, AXLE_Y, -0.60], [FX, AXLE_Y, 0.60]], 0.042, 12);
  lathe(U('under'), [FX, AXLE_Y, FDIFF_Z], [-1, 0, 0], [[-0.09, 0], [-0.09, 0.08], [-0.05, 0.115], [0.04, 0.12], [0.09, 0.09], [0.16, 0.05], [0.20, 0.045], [0.20, 0]], 20);
  sym(s => {
    lathe(U('under'), [FX, AXLE_Y, s * 0.57], [0, 0, s], [[-0.04, 0], [-0.04, 0.05], [0, 0.075], [0.04, 0.05], [0.06, 0.04], [0.06, 0]], 16);
    obox(U('under'), [FX - 0.07, AXLE_Y + 0.02, s * 0.56], [1, 0, 0], [0, 1, 0], 0.07, 0.015, 0.012, 0.004);   // steering arm
  });
  tube(U('under'), [[FX - 0.13, AXLE_Y + 0.02, -0.55], [FX - 0.13, AXLE_Y + 0.02, 0.55]], 0.016, 8);
  // steering box on the left rail, pitman arm, drag link to the left knuckle
  lathe(U('under'), [FX + 0.36, railY(FX + 0.36), -(RZ + 0.08)], [0, 1, 0], [[-0.06, 0], [-0.06, 0.05], [0.06, 0.05], [0.06, 0]], 14);
  obox(U('under'), [FX + 0.36, railY(FX + 0.36) - 0.11, -(RZ + 0.08)], [0, 1, 0], [1, 0, 0], 0.07, 0.018, 0.012, 0.004);
  tube(U('under'), [[FX + 0.36, railY(FX + 0.36) - 0.17, -(RZ + 0.08)], [FX - 0.02, AXLE_Y + 0.03, -0.56]], 0.015, 8);
  tube(U('under'), roundPath([[FX + 0.36, railY(FX + 0.36) + 0.06, -(RZ + 0.08)], [1.70, 0.58, -0.47], [1.10, 0.70, -0.39], [0.98, 0.72, -0.37]], 0.08, 3), 0.012, 8);   // intermediate shaft under the battery tray to the column
  // rear axle: banjo housing, carrier, cover
  tube(U('under'), [[RX, AXLE_Y, -0.62], [RX, AXLE_Y, 0.62]], 0.042, 12);
  lathe(U('under'), [RX, AXLE_Y, 0], [-1, 0, 0], [[-0.14, 0], [-0.14, 0.09], [-0.09, 0.13], [0.02, 0.135], [0.08, 0.10], [0.17, 0.05], [0.22, 0.045], [0.22, 0]], 22);
}

function driveline() {
  // gearbox and transfer case (aluminium cases) under the tunnel, the gear lever tower
  slab(U('alu'), FRX, fillet([[-0.12, 0.42], [0.12, 0.42], [0.13, 0.55], [0.09, 0.62], [-0.09, 0.62], [-0.13, 0.55]], [0.03, 0.03, 0.03, 0.03, 0.03, 0.03], 2), [], 0.66, 1.09, {r0: 0.02, r1: 0.01});
  slab(U('alu'), FRX, fillet([[-0.22, 0.40], [0.14, 0.40], [0.14, 0.58], [-0.22, 0.58]], [0.04, 0.03, 0.03, 0.04], 3), [], 0.40, 0.66, {r0: 0.02, r1: 0.01});
  lathe(U('alu'), [0.43, 0.62, -0.02], [0, 1, 0], [[0, 0], [0, 0.04], [0.04, 0.03], [0.05, 0]], 12);
  // propeller shafts with joint yokes: rear from the transfer case to the rear carrier, front to the offset front carrier
  const shaft = (a, b, r) => { tube(U('under'), [a, b], r, 10); for (const q of [a, b]) obox(U('steel'), q, norm(sub(b, a)), [0, 1, 0], 0.03, 0.03, 0.03, 0.006); };
  shaft([0.38, 0.47, 0.02], [RX + 0.25, AXLE_Y, 0], 0.032);
  shaft([0.62, 0.45, -0.17], [FX - 0.22, AXLE_Y + 0.01, FDIFF_Z], 0.026);
}

function exhaust() {
  const pipe = [[1.30, 0.44, 0.30], [0.40, 0.42, 0.29], [0.38, 0.42, 0.28]];
  tube(U('iron'), roundPath(pipe, 0.1, 3), 0.025, 10);
  // muffler: an oval can between the gearbox and the right rail
  const mf = circle(1, 18).map(([u, v]) => [u * 0.075, v * 0.06]);
  sweep(U('iron'), [[0.38, 0.42, 0.28], [-0.12, 0.42, 0.28]], mf, {up: [0, 1, 0]});
  tube(U('iron'), roundPath([[-0.12, 0.42, 0.28], [-0.45, 0.42, 0.30], [-0.70, 0.43, 0.52], [-1.00, 0.47, 0.52], [-1.50, 0.47, 0.52], [-1.68, 0.43, 0.52], [-1.80, 0.42, 0.62], [-1.86, 0.42, 0.70]], 0.08, 4), 0.025, 10);
  for (const x of [0.10, -1.10, -1.70]) abox(U('rubber'), x - 0.015, x + 0.015, x > 0 ? 0.48 : 0.50, x > 0 ? 0.52 : 0.58, x > 0 ? 0.27 : 0.50, x > 0 ? 0.29 : 0.54, 0.004);   // hangers
}

function tankAndSpare() {
  // fuel tank (left, under the bed front), two straps, filler hose up inside the bed side to the filler door
  slab(U('under'), FZ, fillet([[-0.78, 0.43], [-0.40, 0.43], [-0.40, 0.68], [-0.78, 0.68]], 0.04, 3), [], -0.80, -0.46, {r0: 0.03, r1: 0.03});
  for (const x of [-0.50, -0.68]) sweep(U('steel'), [[x, 0.70, -0.45], [x, 0.425, -0.45], [x, 0.425, -0.81], [x, 0.70, -0.81]], [[-0.002, -0.02], [0.002, -0.02], [0.002, 0.02], [-0.002, 0.02]], {up: [1, 0, 0]});
  tube(U('rubber'), roundPath([[-0.62, 0.66, -0.74], [-0.62, 0.78, -0.80], [-0.62, 0.92, -0.835]], 0.06, 3), 0.019, 10);
  // spare carrier: crossmember bracket, winch, cable down to the hanger plate under the spare
  const sx = SPARE.x, sy = SPARE.y;
  abox(U('under'), sx - 0.05, sx + 0.05, railY(sx) - 0.05, railY(sx) + 0.03, -(RZ - 0.03), RZ - 0.03, 0.006);
  lathe(U('under'), [sx, railY(sx) - 0.06, 0], [0, 1, 0], [[-0.04, 0], [-0.04, 0.05], [0.02, 0.05], [0.02, 0]], 14);
  tube(U('steel'), [[sx, railY(sx) - 0.10, 0], [sx, sy - 0.12, 0]], 0.004, 6);
  obox(U('steel'), [sx, sy - 0.125, 0], [1, 0, 0], [0, 0, 1], 0.11, 0.02, 0.006, 0.003);
}

export function buildChassis() {
  frame(); axles(); driveline(); exhaust(); tankAndSpare();
}
