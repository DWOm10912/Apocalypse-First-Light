// T15 cab interior (mesh pickup_t15_cab; bones body, door_l, door_r, steering). Structure per the reference drawings (design
// doc 2.4, Hilux LN106 instrument panel / seat / door trim groups): instrument panel with a padded top and a meter hood over
// the driver's cluster; round side vents, centre vents, defroster grilles; heater control panel above the radio, ashtray
// below; glovebox; tilt column with a two-piece square shroud, two stalks and the key cylinder; two-spoke wheel with a
// large horn pad; hanging clutch / brake / accelerator pedals; long bent floor shift lever and a transfer lever on the
// tunnel; T-handle parking brake under the panel; bench seat with pleated cloth inserts, vinyl ends, two headrest bumps,
// side shields, tracks and the front release bar; door trims with a moulded line, armrest pull, inner handle, window
// crank, lock knob; A-pillar garnishes, kick panels, back panel trim around the rear window, headliner with dome lamp,
// sun visors, rear-view mirror, assist grip, outboard belts and centre buckles; painted door jambs and hinges.
// Left-hand drive: the driver sits at -z. Concept frame (metres, x forward, y up, z right).
import {FX as FRX, FY, FZ, frame, slab, rbox, abox, obox, loftSolid, sweep, tube, lathe, roundPath, fillet, circle, cross, norm, add, mul, sub} from '../vehicle-mesh-kit.mjs';
import {HW, YB, BELT, ROOF, CB, WS1, DOOR_R, DOOR_F, ROOF_W, sideZ, wsX, PIVOTS, STEER_TILT, P, sym} from './common.mjs';

// two meshes on the cab atlas (per-mesh limits): 'cab' (panel, column, wheel, floor, trims, lining) and 'seat' (bench seat, belts)
let MESH = 'cab';
const C = (bone, mat) => P(MESH, bone, mat);
const B = 'body';
const zr = (s, a, b) => s > 0 ? [a, b] : [-b, -a];
const FLOOR = 0.535, DZ = 0.742;                     // floor pan top; dash half width (inboard of the door trims)
const D2R = Math.PI / 180;
/** Gauge dials (centre, radius, sweep start / end in degrees about the dial, measured from +z toward +y in the face plane):
 *  read by the dial shading for the tick marks. */
export const DIALS = [];

// ================================================================ instrument panel
function dash() {
  // the body is extruded across the cab; section (x, y), counter-clockwise. The cluster sits higher, in the meter hood,
  // where the driver sees it through the upper half of the wheel.
  const plain = fillet([[0.975, 0.78], [0.975, 1.04], [0.60, 1.015], [0.566, 0.995], [0.575, 0.95], [0.60, 0.80], [0.66, 0.78]], [0, 0, 0.01, 0.012, 0.02, 0.03, 0.01], 3);
  const zc0 = -0.62, zc1 = -0.14;
  slab(C(B, 'dash'), FZ, plain, [], -DZ, DZ, {r0: 0.012, r1: 0.012});
  // padded top: rolls over the rear edge, rises toward the glass
  const pad = fillet([[0.553, 0.993], [0.60, 1.006], [0.95, 1.045], [0.957, 1.062], [0.80, 1.077], [0.62, 1.064], [0.558, 1.036]], [0.008, 0, 0, 0.004, 0.02, 0.02, 0.016], 3);
  slab(C(B, 'pad'), FZ, pad, [], -DZ + 0.004, zc0 + 0.01, {r0: 0.014, r1: 0.004});
  slab(C(B, 'pad'), FZ, pad, [], zc1 - 0.01, DZ - 0.004, {r0: 0.004, r1: 0.014});
  // meter hood: a padded box on the panel with the cluster recess in its rear face (visor lip above), closed at the ends
  const hood = fillet([[0.60, 1.005], [0.80, 1.03], [0.82, 1.07], [0.74, 1.172], [0.565, 1.176], [0.528, 1.156], [0.536, 1.136], [0.62, 1.122], [0.628, 1.112],
    [0.628, 1.02], [0.60, 1.012]], [0, 0, 0.02, 0.03, 0.025, 0.012, 0.006, 0.006, 0.004, 0.004, 0], 3);
  slab(C(B, 'pad'), FZ, hood, [], zc0 - 0.006, zc1 + 0.006, {r0: 0.02, r1: 0.02});
  for (const [a, b] of [[zc0 - 0.006, zc0 + 0.008], [zc1 - 0.008, zc1 + 0.006]])
    slab(C(B, 'pad'), FZ, fillet([[0.536, 1.008], [0.628, 1.008], [0.628, 1.14], [0.536, 1.14]], [0.004, 0, 0, 0.01], 2), [], a, b, {r0: 0.003, r1: 0.003});
  // defroster grilles near the glass (dark slots along the pad slope), side demisters at the ends
  const slope = Math.atan2(1.062 - 1.077, 0.957 - 0.80), u = [Math.cos(slope), Math.sin(slope), 0], v = [0, 0, 1];
  for (const [z0, z1] of [[-0.56, -0.20], [-0.12, 0.12], [0.20, 0.56]]) {
    const c = [0.90, 1.0695, (z0 + z1) / 2];
    obox(C(B, 'trim'), c, u, v, 0.022, (z1 - z0) / 2, 0.002, 0.0015, {rc: 0.004});
    for (let z = z0 + 0.03; z < z1 - 0.02; z += 0.03) obox(C(B, 'dash'), add(c, [0, 0.0025, z - c[2]]), u, v, 0.018, 0.0035, 0.0012, 0);
  }
  // round side vents at the panel ends (bezel, dark throat, three horizontal vanes)
  sym(s => {
    const c = [0.573, 0.955, s * 0.665], ax = [-1, 0, 0];
    lathe(C(B, 'trim'), c, ax, [[-0.006, 0.040], [0.004, 0.040], [0.008, 0.036], [0.006, 0.030], [-0.006, 0.030]], 24);
    lathe(C(B, 'liner'), add(c, [0.02, 0, 0]), ax, [[-0.01, 0], [-0.01, 0.031], [0.02, 0.031], [0.02, 0]], 16);
    for (const dy of [-0.016, 0, 0.016]) abox(C(B, 'trim'), c[0] - 0.004, c[0] + 0.006, c[1] + dy - 0.002, c[1] + dy + 0.002, c[2] - 0.027 + 0 * s, c[2] + 0.027, 0.001);
  });
  // centre vents: two rectangles with vertical vanes
  for (const [z0, z1] of [[-0.15, -0.025], [0.025, 0.15]]) {
    rbox(C(B, 'trim'), FRX, z0, z1, 0.94, 0.988, 0.566, 0.578, 0.004, {rc: 0.008});
    rbox(C(B, 'liner'), FRX, z0 + 0.008, z1 - 0.008, 0.948, 0.98, 0.562, 0.566, 0, {rc: 0});
    for (let z = z0 + 0.02; z < z1 - 0.01; z += 0.018) abox(C(B, 'trim'), 0.562, 0.574, 0.95, 0.978, z - 0.0018, z + 0.0018, 0.0008);
  }
  // heater control panel: dark face, three slider slots (mode, temperature, fan) with knobs, recirculation lever
  rbox(C(B, 'trim'), FRX, -0.13, 0.13, 0.875, 0.932, 0.564, 0.578, 0.004, {rc: 0.008});
  [[-0.09, 0.3], [-0.03, 0.55], [0.03, 0.75]].forEach(([z, t], i) => {
    rbox(C(B, 'liner'), FRX, -0.115 + 0.08 * i, -0.055 + 0.08 * i, 0.898, 0.904, 0.561, 0.564, 0, {rc: 0});
    const kz = -0.115 + 0.08 * i + 0.06 * t;
    rbox(C(B, 'vinyl'), FRX, kz - 0.006, kz + 0.006, 0.892, 0.91, 0.552, 0.564, 0.002, {rc: 0.003});
  });
  for (let k = 0; k < 4; k++) rbox(C(B, 'dash'), FRX, 0.05 + k * 0.018, 0.064 + k * 0.018, 0.912, 0.924, 0.557, 0.564, 0.0015, {rc: 0.002});   // mode buttons
  // radio: face plate, cassette door, two knobs
  rbox(C(B, 'trim'), FRX, -0.092, 0.092, 0.826, 0.871, 0.562, 0.578, 0.003, {rc: 0.004});
  rbox(C(B, 'liner'), FRX, -0.05, 0.05, 0.84, 0.855, 0.559, 0.562, 0, {rc: 0});
  for (const z of [-0.072, 0.072]) lathe(C(B, 'vinyl'), [0.562, 0.8485, z], [-1, 0, 0], [[0, 0], [0, 0.009], [0.012, 0.0085], [0.014, 0.007], [0.014, 0]], 12);
  // ashtray drawer below
  rbox(C(B, 'dash'), FRX, -0.08, 0.08, 0.80, 0.82, 0.57, 0.585, 0.003, {rc: 0.004});
  rbox(C(B, 'trim'), FRX, -0.03, 0.03, 0.806, 0.814, 0.566, 0.57, 0.001, {rc: 0.003});
  // centre lower panel down to the tunnel
  slab(C(B, 'dash'), FRX, fillet([[-0.16, 0.66], [0.16, 0.66], [0.16, 0.80], [-0.16, 0.80]], 0.02, 2), [], 0.60, 0.70, {r0: 0.012, r1: 0});
  // glovebox door (passenger side) and its latch
  rbox(C(B, 'dash'), FRX, 0.20, 0.62, 0.835, 0.95, 0.572, 0.582, 0.005, {rc: 0.012});
  rbox(C(B, 'trim'), FRX, 0.38, 0.44, 0.925, 0.94, 0.567, 0.572, 0.002, {rc: 0.004});
  // driver's knee panel cover with the hood release, T-handle parking brake under the panel
  rbox(C(B, 'dash'), FRX, -0.62, -0.18, 0.80, 0.905, 0.585, 0.598, 0.005, {rc: 0.01});
  tube(C(B, 'steel'), [[0.62, 0.79, -0.13], [0.56, 0.79, -0.13]], 0.005, 8);
  obox(C(B, 'trim'), [0.548, 0.79, -0.13], [0, 0, 1], [0, 1, 0], 0.045, 0.011, 0.012, 0.004);
  obox(C(B, 'trim'), [0.70, 0.66, -0.715], [1, 0, 0], [0, 1, 0], 0.02, 0.012, 0.012, 0.003);   // hood release lever on the kick panel
}

// ================================================================ gauge cluster
function cluster() {
  const x = 0.627;   // face plane in the meter hood recess (faces the driver, -x)
  const dial = (zc, yc, r, a0, a1, big) => {
    DIALS.push({c: [x - 0.003, yc, zc], r, a0, a1, big});
    lathe(C(B, 'dial'), [x - 0.003, yc, zc], [-1, 0, 0], [[0, 0], [0, r], [0.002, r], [0.002, 0]], big ? 32 : 20);
    lathe(C(B, 'trim'), [x - 0.003, yc, zc], [-1, 0, 0], [[0, r], [0.010, r + 0.001], [0.012, r + 0.005], [0, r + 0.006]], big ? 32 : 20);
    // needle at rest (static: the mesh has no gauge bones yet) and its hub
    const a = (a0 + (a1 - a0) * (big ? 0.0 : 0.5)) * D2R, len = r * 0.82, dir = [0, Math.sin(a), Math.cos(a)];
    obox(C(B, 'needle'), [x - 0.0065, yc + dir[1] * len * 0.45, zc + dir[2] * len * 0.45], [0, dir[1], dir[2]], [0, -dir[2], dir[1]], len * 0.55, 0.0016, 0.0008, 0);
    lathe(C(B, 'trim'), [x - 0.005, yc, zc], [-1, 0, 0], [[0, 0], [0, 0.007], [0.004, 0.006], [0.005, 0]], 10);
  };
  // cluster housing face and its frame
  rbox(C(B, 'dial'), FRX, -0.60, -0.16, 1.016, 1.116, x - 0.001, x + 0.003, 0, {rc: 0});
  dial(-0.445, 1.066, 0.041, 225, -45, true);   // tachometer
  dial(-0.335, 1.066, 0.041, 225, -45, true);   // speedometer
  dial(-0.54, 1.08, 0.022, 150, 30, false);     // fuel
  dial(-0.24, 1.08, 0.022, 150, 30, false);     // temperature
  // warning lamp strips under the small gauges
  for (const z0 of [-0.575, -0.275]) rbox(C(B, 'trim'), FRX, z0, z0 + 0.07, 1.03, 1.042, x - 0.004, x - 0.001, 0, {rc: 0.002});
  // lens over the whole cluster (translucent)
  rbox(C(B, 'glass'), FRX, -0.598, -0.162, 1.018, 1.114, x - 0.018, x - 0.016, 0, {rc: 0.008});
}

// ================================================================ steering column, wheel, stalks (bone steering for the wheel)
function steering() {
  const c = PIVOTS.steering, t = STEER_TILT * D2R;
  const d = [Math.cos(t), -Math.sin(t), 0];      // column axis, into the panel
  const up = [Math.sin(t), Math.cos(t), 0];      // the wheel plane's up
  const z = [0, 0, 1], at = k => add(c, mul(d, k));
  // column shroud: upper and lower halves of a square cover, the column tube into the panel
  obox(C(B, 'dash'), add(at(0.17), mul(up, 0.022)), d, z, 0.085, 0.042, 0.024, 0.008, {rc: 0.014});
  obox(C(B, 'dash'), add(at(0.18), mul(up, -0.026)), d, z, 0.09, 0.044, 0.026, 0.008, {rc: 0.014});
  tube(C(B, 'under'), [at(0.27), at(0.36)], 0.022, 10);
  // key cylinder on the right of the shroud, stalks either side
  lathe(C(B, 'chrome'), add(at(0.13), [0, -0.01, 0.044]), [0, 0, 1], [[0, 0], [0, 0.012], [0.006, 0.012], [0.009, 0.009], [0.009, 0]], 12);
  sym(s => {
    const p0 = add(at(0.10), [0, 0, s * 0.04]);
    tube(C(B, 'dash'), [p0, add(p0, [-0.03, -0.012, s * 0.07]), add(p0, [-0.05, -0.024, s * 0.13])], 0.0065, 8);
    tube(C(B, 'trim'), [add(p0, [-0.05, -0.024, s * 0.13]), add(p0, [-0.055, -0.027, s * 0.15])], 0.008, 8);
  });
  // the wheel itself (bone steering): rim, two spokes dipping toward the hub, horn pad, hub boss
  const S = C('steering', 'vinyl'), Rr = 0.19, nd = mul(d, -1);
  const ring = []; for (let i = 0; i < 40; i++) { const a = i / 40 * Math.PI * 2; ring.push(add(c, add(mul(z, Rr * Math.cos(a)), mul(up, Rr * Math.sin(a))))); }
  sweep(S, ring, circle(1, 10).map(([u, v]) => [u * 0.016, v * 0.0125]), {closed: true, up: d});   // oval grip section
  sym(s => {
    const a = (-12) * D2R, rim = add(c, add(mul(z, s * (Rr - 0.012) * Math.cos(a)), mul(up, (Rr - 0.012) * Math.sin(a))));
    const hub = add(add(c, mul(d, 0.045)), add(mul(z, s * 0.085), mul(up, -0.03)));
    const mid = add(mul(add(rim, hub), 0.5), mul(d, 0.012));
    sweep(S, [hub, mid, rim], fillet([[-0.026, -0.006], [0.026, -0.006], [0.026, 0.006], [-0.026, 0.006]], 0.005, 2), {up: up});
  });
  const F = frame(add(c, mul(d, 0.05)), z, up, nd);
  slab(C('steering', 'vinyl'), F, fillet([[-0.105, -0.085], [0.105, -0.085], [0.095, 0.05], [-0.095, 0.05]], [0.03, 0.03, 0.035, 0.035], 4), [], 0, 0.034, {r0: 0, r1: 0.014});
  lathe(C('steering', 'trim'), add(c, mul(d, 0.05)), d, [[0, 0], [0, 0.045], [0.05, 0.035], [0.05, 0]], 16);
}

// ================================================================ floor, tunnel, pedals, levers
function floor() {
  // transmission tunnel and toe boards under the mat
  slab(C(B, 'mat'), FRX, fillet([[-0.175, FLOOR], [0.175, FLOOR], [0.13, 0.665], [-0.13, 0.665]], [0, 0, 0.05, 0.05], 3), [], 0.05, 0.97, {r0: 0.05, r1: 0});
  sym(s => {
    const [a, b] = zr(s, 0.165, DZ);
    abox(C(B, 'mat'), -0.27, 0.80, FLOOR, FLOOR + 0.008, a, b, 0.003);
    // toe board rising to the firewall
    const tb = frame([0.79, FLOOR + 0.004, 0], [Math.cos(38 * D2R), Math.sin(38 * D2R), 0], [0, 0, 1]);
    rbox(C(B, 'mat'), tb, 0, 0.23, a, b, -0.008, 0, 0.002, {rc: 0});
    // scuff plate on the sill
    const [c0, c1] = zr(s, DZ, 0.80);
    abox(C(B, 'steel'), -0.17, 0.93, FLOOR - 0.002, FLOOR + 0.012, c0, c1, 0.003);
    // kick panel inside the hinge pillar
    const [k0, k1] = zr(s, DZ - 0.012, DZ + 0.004);
    slab(C(B, 'panel'), FZ, fillet([[0.76, FLOOR + 0.01], [0.975, FLOOR + 0.01], [0.975, 0.84], [0.86, 0.84], [0.76, 0.66]], 0.02, 2), [], k0, k1, {r0: 0.004, r1: 0.004});
  });
  // pedals: arms from a bracket under the panel, pads facing the driver (clutch, brake, accelerator)
  for (const [zp, w, h, y0, acc] of [[-0.53, 0.032, 0.05, 0.665, false], [-0.41, 0.034, 0.05, 0.665, false], [-0.25, 0.022, 0.085, 0.62, true]]) {
    const pad = [0.735, y0, zp], top = [0.86, 0.93, zp];
    tube(C(B, 'under'), [top, [0.80, 0.82, zp], add(pad, [0.012, 0.02, 0])], 0.007, 6);
    const n = norm([-0.75, 0.66, 0]), u = norm(cross([0, 0, 1], n));
    obox(C(B, 'rubber'), pad, u, [0, 0, 1], h / 2, w, 0.008, 0.004);
  }
  abox(C(B, 'under'), 0.84, 0.90, 0.90, 0.96, -0.58, -0.20, 0.004);   // pedal bracket
  // floor shift lever: boot, long bent rod leaning back to the driver, oval knob; transfer lever beside it
  const lever = (base, path, knobR, bootR) => {
    lathe(C(B, 'rubber'), base, [0, 1, 0], [[0, 0], [0, bootR], [0.02, bootR * 0.8], [0.05, bootR * 0.35], [0.06, 0.012], [0.06, 0]], 14);
    tube(C(B, 'trim'), path, 0.0075, 8);
    const tip = path[path.length - 1], dir = norm(sub(tip, path[path.length - 2]));
    lathe(C(B, 'trim'), tip, dir, [[-0.012, 0], [-0.012, 0.012], [0.0, knobR], [0.018, knobR * 0.9], [0.03, knobR * 0.5], [0.034, 0]], 14);
  };
  lever([0.43, 0.665, -0.02], roundPath([[0.43, 0.70, -0.02], [0.43, 0.82, -0.03], [0.35, 0.97, -0.05], [0.33, 1.0, -0.055]], 0.06, 4), 0.021, 0.05);
  lever([0.38, 0.665, 0.07], roundPath([[0.38, 0.70, 0.07], [0.38, 0.80, 0.07], [0.33, 0.88, 0.07]], 0.05, 3), 0.017, 0.035);
}

// ================================================================ bench seat
function seat() {
  const zs = [];
  for (let i = 0; i <= 8; i++) zs.push(-0.55 + 1.10 * i / 8);   // eight pleats, about 14 cm each
  // cushion: pleated cloth insert (separate pleats leave the seams as grooves), vinyl ends
  const cushO = [[-0.25, 0.68], [0.27, 0.68], [0.305, 0.80], [0.285, 0.868], [0.20, 0.878], [-0.21, 0.842], [-0.25, 0.80]], cushR = [0.02, 0.03, 0.04, 0.035, 0.03, 0.03, 0.02];
  const cush = fillet(cushO, cushR, 3);
  const backO = [[-0.215, 0.80], [-0.10, 0.83], [-0.105, 0.98], [-0.155, 1.39], [-0.20, 1.43], [-0.272, 1.415], [-0.262, 1.10]], backR = [0.02, 0.03, 0.04, 0.04, 0.035, 0.03, 0.02];
  const back = fillet(backO, backR, 3);
  const cushP = fillet(cushO, cushR, 2), backP = fillet(backO, backR, 2);
  for (let i = 0; i < 8; i++) {
    slab(C(B, 'cloth'), FZ, cushP, [], zs[i] + 0.002, zs[i + 1] - 0.002, {r0: 0.014, r1: 0.014});
    slab(C(B, 'cloth'), FZ, backP, [], zs[i] + 0.002, zs[i + 1] - 0.002, {r0: 0.014, r1: 0.014});
  }
  sym(s => {
    const [a, b] = zr(s, 0.55, 0.70);
    slab(C(B, 'vinyl'), FZ, cush, [], a, b, s > 0 ? {r0: 0.006, r1: 0.04} : {r0: 0.04, r1: 0.006});
    slab(C(B, 'vinyl'), FZ, back, [], a, b, s > 0 ? {r0: 0.006, r1: 0.04} : {r0: 0.04, r1: 0.006});
    // headrest bump over each outboard place
    const hr = fillet([[-0.255, 1.40], [-0.16, 1.40], [-0.17, 1.52], [-0.20, 1.56], [-0.26, 1.55], [-0.268, 1.46]], [0.01, 0.01, 0.03, 0.03, 0.025, 0.01], 3);
    const [h0, h1] = zr(s, 0.28, 0.54);
    slab(C(B, 'cloth'), FZ, hr, [], h0, h1, {r0: 0.035, r1: 0.035});
    // side shield over the recliner / hinge, tracks, belts
    const [w0, w1] = zr(s, 0.70, 0.716);
    slab(C(B, 'dash'), FZ, fillet([[-0.25, 0.62], [0.12, 0.62], [0.10, 0.80], [-0.24, 0.84]], 0.025, 2), [], w0, w1, s > 0 ? {r0: 0, r1: 0.005} : {r0: 0.005, r1: 0});
    abox(C(B, 'steel'), -0.20, 0.30, FLOOR, FLOOR + 0.04, s * 0.40 - 0.018, s * 0.40 + 0.018, 0.003);
    abox(C(B, 'under'), -0.22, 0.25, FLOOR + 0.04, 0.685, s * 0.40 - 0.03, s * 0.40 + 0.03, 0.006);
    // outboard three-point belt: webbing hanging down the B pillar from the D-ring, retractor cover low
    const zb = s * (DZ - 0.006), xb = -0.235;
    lathe(C(B, 'steel'), [xb, 1.48, zb], [0, 0, s], [[0, 0.004], [0, 0.03], [0.006, 0.03], [0.006, 0.004]], 12);
    abox(C(B, 'belt'), xb - 0.024, xb + 0.024, 0.66, 1.475, zb - 0.0015 - s * 0.004, zb + 0.0015 - s * 0.004, 0);
    abox(C(B, 'steel'), xb - 0.02, xb + 0.02, 0.82, 0.88, zb - 0.002 - s * 0.007, zb + 0.002 - s * 0.007, 0.002);   // latch plate
    // centre buckle on a short stalk between the places
    const zc = s * 0.06;
    abox(C(B, 'trim'), -0.21, -0.17, 0.80, 0.88, zc - 0.012, zc + 0.012, 0.004);
  });
  // release bar under the cushion front
  tube(C(B, 'steel'), roundPath([[0.24, 0.64, -0.44], [0.31, 0.64, -0.40], [0.31, 0.64, -0.18], [0.24, 0.64, -0.14]], 0.03, 3), 0.006, 8);
  abox(C(B, 'under'), -0.24, 0.26, 0.60, 0.685, -0.68, 0.68, 0.006);   // seat frame
}

// ================================================================ door trims (door bones), pillars, roof lining
function doorTrims() {
  sym(s => {
    const D = s > 0 ? 'door_r' : 'door_l', xr = DOOR_R + 0.012, xf = DOOR_F - 0.012, zi = HW - 0.075;
    const at = (a, b) => zr(s, zi - a, zi - b);
    // lower board up to the moulded line, upper board (thinner) to the belt garnish
    const [l0, l1] = at(0.022, 0);
    slab(C(D, 'panel'), FZ, fillet([[xr, YB + 0.06], [xf, YB + 0.06], [xf, 0.83], [xr, 0.83]], [0.02, 0.02, 0, 0], 2), [], l0, l1, s > 0 ? {r0: 0.008, r1: 0} : {r0: 0, r1: 0.008});
    const [u0, u1] = at(0.012, 0);
    slab(C(D, 'panel'), FZ, fillet([[xr, 0.83], [xf, 0.83], [xf - 0.03, BELT - 0.03], [xr, BELT - 0.03]], [0, 0, 0.02, 0.01], 2), [], u0, u1, s > 0 ? {r0: 0.004, r1: 0} : {r0: 0, r1: 0.004});
    // the moulded line: a rounded lip where the boards meet
    const [m0, m1] = at(0.027, 0.006);
    rbox(C(D, 'panel'), FZ, xr + 0.01, xf - 0.01, 0.822, 0.838, m0, m1, 0.006, {rc: 0.008});
    // belt garnish along the top
    const [g0, g1] = at(0.03, -0.002);
    rbox(C(D, 'dash'), FZ, xr, xf - 0.04, BELT - 0.032, BELT - 0.002, g0, g1, 0.006, {rc: 0.012});
    // armrest pull, inner handle in its bezel, window crank, lock knob
    const [a0, a1] = at(0.06, 0.012);
    rbox(C(D, 'panel'), FZ, 0.18, 0.56, 0.80, 0.845, a0, a1, 0.012, {rc: 0.02});
    const [b0, b1] = at(0.016, 0.008);
    rbox(C(D, 'trim'), FZ, 0.66, 0.80, 0.90, 0.95, b0, b1, 0.004, {rc: 0.012});
    const [h0, h1] = at(0.026, 0.012);
    rbox(C(D, 'chrome'), FZ, 0.68, 0.78, 0.917, 0.933, h0, h1, 0.003, {rc: 0.006});
    const cc = [0.32, 0.74, s * (zi - 0.012)];
    lathe(C(D, 'trim'), cc, [0, 0, -s], [[0, 0], [0, 0.026], [0.008, 0.022], [0.012, 0.008], [0.012, 0]], 14);
    const ce = [0.32 + 0.075 * Math.cos(-0.5), 0.74 + 0.075 * Math.sin(-0.5), s * (zi - 0.026)];
    sweep(C(D, 'chrome'), [add(cc, [0, 0, -s * 0.012]), ce], fillet([[-0.007, -0.0025], [0.007, -0.0025], [0.007, 0.0025], [-0.007, 0.0025]], 0.002, 1), {up: [0, 0, s]});
    lathe(C(D, 'trim'), ce, [0, 0, -s], [[0, 0], [0, 0.011], [0.03, 0.01], [0.034, 0]], 10);
    lathe(C(D, 'trim'), [xr + 0.08, BELT - 0.002, s * (zi - 0.012)], [0, 1, 0], [[0, 0], [0, 0.0045], [0.024, 0.0045], [0.03, 0.0035], [0.032, 0]], 8);
  });
}
function pillars() {
  sym(s => {
    // painted door jambs: the B pillar face behind the door's rear edge, the hinge pillar ahead of its front edge, hinges
    const [j0, j1] = zr(s, HW - 0.13, HW - 0.012);
    abox(C(B, 'paint'), CB + 0.03, DOOR_R - 0.006, YB + 0.02, BELT + 0.02, j0, j1, 0.004);
    abox(C(B, 'paint'), DOOR_F + 0.006, 0.99, YB + 0.02, BELT - 0.01, j0, j1, 0.004);
    for (const y of [0.62, 0.93]) {
      const [h0, h1] = zr(s, HW - 0.07, HW - 0.035);
      abox(C(B, 'steel'), DOOR_F - 0.02, DOOR_F + 0.02, y - 0.03, y + 0.03, h0, h1, 0.004);
    }
    // B pillar trim above the belt, A pillar garnish along the glass edge, assist grip on the passenger side
    const zb = y => s * (sideZ(y) - 0.04);
    sweep(C(B, 'panel'), [[CB + 0.09, BELT, zb(BELT)], [CB + 0.09, ROOF - 0.07, zb(ROOF - 0.07)]], fillet([[-0.05, -0.008], [0.05, -0.008], [0.05, 0.008], [-0.05, 0.008]], 0.006, 2), {up: [0, 0, s]});
    const ax = y => wsX(y) - 0.06;
    sweep(C(B, 'panel'), [[ax(BELT + 0.02), BELT + 0.02, s * (sideZ(BELT) - 0.07)], [ax(ROOF - 0.08), ROOF - 0.08, s * (sideZ(ROOF - 0.08) - 0.07)]],
      fillet([[-0.03, -0.012], [0.03, -0.012], [0.03, 0.01], [-0.03, 0.01]], [0.008, 0.008, 0.012, 0.012], 2), {up: [0, 0, s]});
    if (s > 0) tube(C(B, 'dash'), roundPath([[0.0, 1.66, ROOF_W - 0.06], [0.03, 1.60, ROOF_W - 0.07], [0.27, 1.60, ROOF_W - 0.07], [0.30, 1.66, ROOF_W - 0.06]], 0.03, 3), 0.011, 8);
  });
  // back panel trim around the rear window
  const top = ROOF - 0.10;
  const rw = fillet([[-0.585, BELT + 0.075], [0.585, BELT + 0.075], [0.56, ROOF - 0.15], [-0.56, ROOF - 0.15]], 0.07, 6);
  slab(C(B, 'panel'), FRX, fillet([[-(HW - 0.09), BELT - 0.04], [HW - 0.09, BELT - 0.04], [HW - 0.09, top], [-(HW - 0.09), top]], 0.04, 3), [rw.map(([z, y]) => [z * 1.012, y])], CB + 0.012, CB + 0.03, {r0: 0, r1: 0.008});
}
function roofLining() {
  // headliner: a moulded board under the roof with rounded edges, dome lamp, sun visors, rear-view mirror
  const y = ROOF - 0.042;
  slab(C(B, 'headliner'), FY, fillet([[CB + 0.03, -(ROOF_W - 0.02)], [WS1 + 0.03, -(ROOF_W - 0.02)], [WS1 + 0.03, ROOF_W - 0.02], [CB + 0.03, ROOF_W - 0.02]], 0.05, 3), [], y - 0.012, y, {r0: 0.01, r1: 0});
  rbox(C(B, 'lens'), FY, -0.10, 0.02, -0.06, 0.06, y - 0.022, y - 0.012, 0.005, {rc: 0.015});
  sym(s => {
    const [z0, z1] = zr(s, 0.12, 0.70);
    rbox(C(B, 'headliner'), FY, WS1 + 0.04, WS1 + 0.20, z0, z1, y - 0.04, y - 0.022, 0.008, {rc: 0.03});
    tube(C(B, 'steel'), [[WS1 + 0.18, y - 0.031, s * 0.70], [WS1 + 0.18, y - 0.031, s * 0.74]], 0.004, 6);
  });
  tube(C(B, 'dash'), [[WS1 + 0.06, y - 0.012, 0], [WS1 + 0.05, y - 0.06, 0]], 0.007, 8);
  const mc = [WS1 + 0.045, y - 0.085, 0];
  obox(C(B, 'dash'), mc, [0, 0, 1], [0, 1, 0], 0.11, 0.032, 0.014, 0.008);
  rbox(C(B, 'mirror'), FRX, -0.10, 0.10, mc[1] - 0.025, mc[1] + 0.025, mc[0] - 0.0155, mc[0] - 0.0145, 0, {rc: 0.01});
}

export function buildCab() {
  dash(); cluster(); steering(); floor(); doorTrims(); pillars(); roofLining();
  MESH = 'seat'; seat(); MESH = 'cab';
}
