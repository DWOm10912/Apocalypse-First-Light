// T15 exterior (mesh pickup_t15): body shell, front clip, cab, doors, bed, tailgate, bumpers, lamps, glass.
// Structure per the reference drawings (design doc 2.4): fenders with plastic arch liners and a rolled top beside the
// hood; hood with a crowned top, two longitudinal ridges and an X-braced inner frame; cowl louver panel; curved windshield
// with rounded corners in a moulding; roof with drip rails; cab back panel with horizontal ribs and a rounded rear window
// in a rubber surround; doors with integral window sash, belt weatherstrips, a mirror on a triangular base and a flush
// pull handle; bed with a rolled top rail, stake pockets, rope-hook slots, wheel housings, liners, ribbed floor and front
// wall, rear corner posts carrying three-section tail lamps; tailgate with a centre handle, side cables, bottom hinges.
import {FX as FRX, FY, FZ, slab, rbox, abox, obox, loftSolid, loftRings, sweep, tube, lathe, roundPath, fillet, arc, ccw,
  thicken, rotAbout, splitAtY, triangulate, offsetLoop, circle, clamp, smoothstep, add, sub, mul, norm, cross, dot} from '../vehicle-mesh-kit.mjs';
import {HW, FX, RX, AY, AR, YB, BELT, ROOF, HF, FX0, CB, BED_F, BED_R, BED_TOP, BED_FL, SPLIT, WS0, WS1, DOOR_R, DOOR_F,
  ROOF_W, sideZ, hy, wsX, HOOD_X0, HOOD_Z, P, sym} from './common.mjs';

// the outside is two meshes (each inside the Vextris per-mesh limits): 'front' (front clip, hood, cowl) and 'ext' (the rest)
let MESH = 'ext';
const E = (bone, mat, tag) => P(MESH, bone, mat, tag);
/** A side's tag for the lamps the turn signals flash (sym: s = 1 right, -1 left). */
const side = s => s > 0 ? 'r' : 'l';
const zr = (s, a, b) => s > 0 ? [a, b] : [-b, -a];
/** A side panel (outline in x, y) between |z| zIn and zOut, the outer face rounded by rOut. */
function sidePanel(p, s, outline, zIn, zOut, rOut, rIn = 0.002, holes = []) {
  const [a0, a1] = zr(s, zIn, zOut);
  slab(p, FZ, outline, holes, a0, a1, s > 0 ? {r0: rIn, r1: rOut} : {r0: rOut, r1: rIn});
}
/** The same, split at SPLIT into paint2 (below) and paint (above). */
function twoTone(bone, s, outline, zIn, zOut, rOut, rIn = 0.002, holes = []) {
  const {lo, hi} = splitAtY(outline, SPLIT);
  const within = (h, L) => h.every(([x, y]) => x > Math.min(...L.map(q => q[0])) && x < Math.max(...L.map(q => q[0])) && y > Math.min(...L.map(q => q[1])) && y < Math.max(...L.map(q => q[1])));
  for (const L of lo) sidePanel(E(bone, 'paint2'), s, L, zIn, zOut, rOut, rIn, holes.filter(h => within(h, L)));
  for (const L of hi) sidePanel(E(bone, 'paint'), s, L, zIn, zOut, rOut, rIn, holes.filter(h => within(h, L)));
}
/** The fuel filler (left bed side, in front of the rear arch): the hole in the skin, the door over it (bone fuel_door, hinged at its front edge). */
const FILL = {x0: -0.695, x1: -0.55, y0: 0.87, y1: 1.01, cx: -0.6225, cy: 0.94};
const FILL_HOLE = fillet([[FILL.x0, FILL.y0], [FILL.x1, FILL.y0], [FILL.x1, FILL.y1], [FILL.x0, FILL.y1]], 0.01, 2);
/** Stations along x of a (z, y) section: rings in 3D, lofted as one closed solid. */
const xRings = stations => stations.map(({x, sec}) => sec.map(([z, y]) => [x, y, z]));
/** Arch points (x, y) over the wheel at cx from height yRear (rear foot) to yFront, n segments. */
function archPts(cx, r, yRear, yFront, n) {
  const a0 = Math.PI - Math.asin((yRear - AY) / r), a1 = Math.asin((yFront - AY) / r);
  return arc(cx, AY, r, a0, a1, n);
}
/** A lip around a wheel arch: rings of a small (radial, z) profile along the arch at the outer face. */
function archLip(p, s, cx, yRear, yFront, n, zOut, out = 0.006, depth = 0.034) {
  const a0 = Math.PI - Math.asin((yRear - AY) / AR), a1 = Math.asin((yFront - AY) / AR), rings = [];
  const prof = [[-0.003, out], [0.010, out], [0.014, out - 0.004], [0.014, -depth], [0.006, -depth], [-0.003, -0.004]];
  for (let i = 0; i <= n; i++) { const a = a0 + (a1 - a0) * i / n, c = Math.cos(a), sn = Math.sin(a);
    rings.push(prof.map(([dr, dz]) => [cx + (AR - dr) * c, AY + (AR - dr) * sn, s * (zOut + dz)])); }
  loftSolid(p, rings, {});
}
/** A plastic arch liner: a partial cylinder shell about the axle line (z from zIn to zOut). */
function archLiner(s, cx, yCut, zIn, zOut, r = AR - 0.006) {
  const a = Math.asin((yCut - AY) / r), [t0, t1] = zr(s, zIn, zOut);
  lathe(E('body', 'liner'), [cx, AY, 0], [0, 0, 1], [[t0, r], [t1, r], [t1, r + 0.006], [t0, r + 0.006]], 18, {a0: a, a1: Math.PI - a, e1: [1, 0, 0]});
}

// ================================================================ front clip
function fenders() {
  sym(s => {
    const xr = DOOR_F + 0.006, xf = FX0 - 0.022, top = x => hy(x) - 0.002 - 0.03;
    const aRear = 0.53, aFront = 0.605;
    const outline = [[xr, 0.53], ...archPts(FX, AR, aRear, aFront, 22), [xf, 0.605], [xf, top(xf)], [xr, top(xr)]];
    twoTone('body', s, fillet(outline, [0.02, 0.01, ...new Array(21).fill(0), 0.01, 0.025, 0, 0], 3), HW - 0.012, HW, 0.006);
    // rolled top: side -> flange beside the hood -> down into the hood gap
    const sec = x => { const y0 = hy(x) - 0.002;
      return thicken(fillet([[HW, y0 - 0.03], [HW, y0], [HOOD_Z + 0.012, y0], [HOOD_Z + 0.004, y0 - 0.008], [HOOD_Z + 0.004, y0 - 0.045]], [0, 0.028, 0.006, 0.004, 0], 4, false), 0.011)
        .map(([z, y]) => [s * z, y]); };
    loftSolid(E('body', 'paint'), xRings([{x: xr, sec: sec(xr)}, {x: xf, sec: sec(xf)}]));
    archLip(E('body', 'paint'), s, FX, aRear, aFront, 22, HW);
    archLiner(s, FX, 0.52, 0.56, HW - 0.004);
    // inner apron (seen from the arch and with the hood open): a vertical wall over the arch
    const aw = [];
    for (let i = 0; i <= 12; i++) { const x = FX - AR + 0.02 + (2 * AR - 0.04) * i / 12; aw.push([x, AY + Math.sqrt(Math.max(0, (AR + 0.004) ** 2 - (x - FX) ** 2))]); }
    sidePanel(E('body', 'paint'), s, [[1.0, 0.55], ...aw, [2.2, 0.62], [2.2, hy(2.2) - 0.05], [1.0, hy(1.0) - 0.05]], 0.552, 0.562, 0.002);
    // side moulding (black strip on the split line) and front side marker
    const h2 = Math.sqrt(AR * AR - (SPLIT - AY) ** 2) + 0.012;
    for (const [a, b] of [[xr + 0.008, FX - h2], [FX + h2, xf - 0.004]]) {
      const [z0, z1] = zr(s, HW - 0.002, HW + 0.007);
      rbox(E('body', 'trim'), FZ, a, b, SPLIT - 0.016, SPLIT + 0.016, z0, z1, 0.003, {rc: 0.006});
    }
    const [mz0, mz1] = zr(s, HW - 0.002, HW + 0.005);
    rbox(E('body', 'lamp_park', side(s)), FZ, 2.13, 2.21, 0.835, 0.865, mz0, mz1, 0.002, {rc: 0.008});
  });
}

/** Front panel (between the fenders, behind lamps and grille), grille, lamps, bumper, valance. */
function frontEnd() {
  const B = 'body', yTop = hy(FX0) - 0.03;
  // header panel with the grille opening
  const panel = fillet([[-(HW - 0.004), 0.60], [HW - 0.004, 0.60], [HW - 0.004, yTop], [-(HW - 0.004), yTop]], [0.02, 0.02, 0.012, 0.012], 3);
  const gHole = fillet([[-0.455, 0.67], [0.455, 0.67], [0.455, 0.94], [-0.455, 0.94]], 0.012, 2);
  slab(E(B, 'paint'), FRX, panel, [gHole], FX0 - 0.024, FX0 - 0.004, {r0: 0.002, r1: 0.008});
  // grille: chrome surround, recessed black grid, three chrome bars, a plain centre plate
  const gOut = fillet([[-0.472, 0.655], [0.472, 0.655], [0.472, 0.955], [-0.472, 0.955]], 0.02, 3);
  slab(E(B, 'chrome'), FRX, gOut, [gHole], FX0 - 0.006, FX0 + 0.024, {r0: 0.002, r1: 0.006});
  slab(E(B, 'trim'), FRX, gHole.map(([z, y]) => [z * 1.002, y]), [], FX0 - 0.03, FX0 - 0.024, {r0: 0});   // dark backing frame edge
  for (let i = 1; i < 12; i++) { const z = -0.455 + 0.91 * i / 12; abox(E(B, 'trim'), FX0 - 0.02, FX0 + 0.008, 0.67, 0.94, z - 0.004, z + 0.004, 0.0015, {rc: 0}); }
  for (let j = 1; j < 6; j++) { const y = 0.67 + 0.27 * j / 6; abox(E(B, 'trim'), FX0 - 0.02, FX0 + 0.006, y - 0.003, y + 0.003, -0.455, 0.455, 0.0015, {rc: 0}); }
  for (const y of [0.733, 0.805, 0.877]) rbox(E(B, 'chrome'), FRX, -0.455, 0.455, y - 0.011, y + 0.011, FX0 + 0.004, FX0 + 0.02, 0.005, {rc: 0.008});
  rbox(E(B, 'chrome'), FRX, -0.085, 0.085, 0.785, 0.825, FX0 + 0.02, FX0 + 0.03, 0.004, {rc: 0.01});
  // four square headlamps: chrome housings, sealed-beam lenses; amber turn lamps under them
  sym(s => {
    const hz0 = 0.485, hz1 = 0.832, y0 = 0.725, y1 = 0.895;
    const H = fillet([[hz0, y0], [hz1, y0], [hz1, y1], [hz0, y1]].map(([z, y]) => [s * z, y]), 0.018, 3);
    const holes = [0.571, 0.746].map(zc => fillet([[zc - 0.072, 0.742], [zc + 0.072, 0.742], [zc + 0.072, 0.878], [zc - 0.072, 0.878]].map(([z, y]) => [s * z, y]), 0.012, 2));
    slab(E(B, 'chrome'), FRX, H, holes, FX0 - 0.006, FX0 + 0.026, {r0: 0.002, r1: 0.01});
    for (const zc of [0.571, 0.746]) {
      // lens: slightly domed (a flat plate with a rounded edge), set back in the bezel; outer: low + high beam, inner: high
      rbox(E(B, zc > 0.6 ? 'lamp_low' : 'lamp_high'), FRX, s * zc - 0.074, s * zc + 0.074, 0.74, 0.88, FX0 - 0.01, FX0 + 0.012, 0.008, {rc: 0.014});
    }
    const T = fillet([[hz0, 0.655], [hz1, 0.655], [hz1, 0.705], [hz0, 0.705]].map(([z, y]) => [s * z, y]), 0.01, 2);
    slab(E(B, 'chrome'), FRX, T, [], FX0 - 0.006, FX0 + 0.012, {r0: 0.002, r1: 0.004});
    rbox(E(B, 'lamp_park', side(s)), FRX, s > 0 ? hz0 + 0.008 : -hz1 + 0.008, s > 0 ? hz1 - 0.008 : -hz0 - 0.008, 0.662, 0.698, FX0 + 0.008, FX0 + 0.018, 0.003, {rc: 0.006});
  });
  // front bumper: chrome C-section swept along a plan line that wraps back at the corners
  const plan = roundPath([[2.20, 0.505, -0.885], [2.33, 0.505, -0.79], [2.37, 0.505, -0.60], [2.37, 0.505, 0.60], [2.33, 0.505, 0.79], [2.20, 0.505, 0.885]], 0.09, 5);
  // section (u up, v toward the rear): face with a slight forward bulge, top and bottom returns rolled
  const bs = fillet([[-0.095, 0.0], [-0.06, -0.012], [0.06, -0.012], [0.095, 0.0], [0.095, 0.075], [0.08, 0.075], [0.08, 0.012], [-0.08, 0.012], [-0.08, 0.075], [-0.095, 0.075]],
    [0.02, 0.03, 0.03, 0.02, 0.006, 0.004, 0.006, 0.006, 0.004, 0.006], 3);
  bumperSweep(E(B, 'chrome'), plan, bs);
  // black rubber strip on the face (6 mm proud of the chrome; its ends run into the wrap), the valance / air dam under it
  bumperSweep(E(B, 'rubber'), roundPath([[2.355, 0.505, -0.64], [2.388, 0.505, -0.5], [2.388, 0.505, 0.5], [2.355, 0.505, 0.64]], 0.06, 4),
    fillet([[-0.017, 0], [0.017, 0], [0.017, 0.012], [-0.017, 0.012]], 0.005, 2));
  const val = fillet([[-(HW - 0.03), 0.33], [HW - 0.03, 0.33], [HW - 0.02, 0.41], [-(HW - 0.02), 0.41]], [0.03, 0.03, 0.01, 0.01], 3);
  slab(E(B, 'trim'), FRX, val, [], 2.19, 2.27, {r0: 0.004, r1: 0.012});
  // licence plate bracket (black, centre of the bumper, 10 mm proud of the chrome face: flush, it z-fought it)
  rbox(E(B, 'trim'), FRX, -0.165, 0.165, 0.43, 0.58, 2.37, 2.392, 0.003, {rc: 0.01});
}
/** Sweeps a bumper section (u up, v rearward) along a plan path at constant height (the section's u axis stays +y). */
function bumperSweep(p, plan, sec) {
  const rings = plan.map((q, i) => {
    const a = plan[Math.max(0, i - 1)], b = plan[Math.min(plan.length - 1, i + 1)], t = norm(sub(b, a)), back = norm(cross([0, 1, 0], t));
    const rear = back[0] * q[0] > 0 ? mul(back, -1) : back;   // toward the body (front bumper: -x, rear bumper: +x)
    return sec.map(([u, v]) => add(q, add([0, u, 0], mul(rear, v))));
  });
  loftSolid(p, rings, {});
}

/**
 * Rings rolling a thickened section (3D points, the outer line first and the inner line second, as thicken returns it) over
 * an edge along x: each point turns about a centre below itself, rad for the outer points and rad - t for the inner ones,
 * so outer and inner stay concentric and a section that drops below the roll centre (a rolled side) cannot fold.
 * angles in radians; sg +1 rolls toward -x (a rear edge), -1 toward +x (a front edge).
 */
function rollRings(sec3, rad, t, angles, sg) {
  const n = sec3.length / 2;
  return angles.map(a => sec3.map((q, i) => { const r = i < n ? rad : Math.max(0.002, rad - t); return [q[0] - sg * r * Math.sin(a), q[1] - r + r * Math.cos(a), q[2]]; }));
}

function hood() {
  const H = 'hood', zs = [];
  // section across: side flange and roll, crowned top with two soft longitudinal ridges
  const topY = z => 0.014 * (1 - (z / HOOD_Z) ** 2) + 0.0055 * Math.exp(-(((Math.abs(z) - 0.335) / 0.028) ** 2));
  for (const z of [0, 0.10, 0.20, 0.27, 0.30, 0.32, 0.335, 0.35, 0.37, 0.40, 0.48, 0.56, 0.63, 0.68, 0.705]) zs.push(z);
  const half = zs.map(z => [z, topY(z)]);
  const rollR = 0.022, zE = HOOD_Z;
  const right = [...half, ...arc(zE - rollR, topY(zE) - rollR, rollR, Math.PI / 2 - 0.25, 0, 4).slice(1), [zE, topY(zE) - 0.045]];
  const line = [...right.slice(1).reverse().map(([z, y]) => [-z, y]), ...right];
  const sec = thicken(line, -0.009);   // the skin; the inner frame is separate
  const at = (x, dy = 0) => sec.map(([z, y]) => [x, hy(x) + y + dy, z]);
  const rings = [], xr = HOOD_X0 + 0.014, xf = FX0 - 0.032, rr = 0.014, rf = 0.034;
  // rear edge rolls down a little (rotate about z), front edge rolls over toward the grille
  const D = Math.PI / 180;
  rings.push(...rollRings(at(xr), rr, 0.009, [90, 60, 30].map(a => a * D), 1));
  rings.push(at(xr), at((xr + xf) / 2), at(xf));
  rings.push(...rollRings(at(xf), rf, 0.009, [22.5, 45, 67.5, 90].map(a => a * D), -1));
  rings.push(rollRings(at(xf), rf, 0.009, [90 * D], -1)[0].map(q => [q[0], q[1] - 0.018, q[2]]));
  loftSolid(E(H, 'paint'), rings, {});
  // inner frame: perimeter channel and an X brace under the skin
  const fy = x => hy(x) - 0.02, fp = (x, z) => [x, fy(x) + 0.012 * (1 - (z / HOOD_Z) ** 2) - 0.004, z];
  const rect = fillet([[-0.017, -0.009], [0.017, -0.009], [0.017, 0.009], [-0.017, 0.009]], 0.004, 1);
  const zi = HOOD_Z - 0.045, x0 = xr + 0.04, x1 = xf - 0.03;
  sweep(E(H, 'paint'), roundPath([fp(x0, -zi), fp(x1, -zi), fp(x1, zi), fp(x0, zi), fp(x0, -zi)], 0.08, 4).slice(0, -1), rect, {closed: true, up: [0, 1, 0]});
  for (const sz of [1, -1]) sweep(E(H, 'paint'), [fp(x0 + 0.03, -sz * (zi - 0.03)), fp((x0 + x1) / 2, 0), fp(x1 - 0.03, sz * (zi - 0.03))], rect, {up: [0, 1, 0]});
  // insulation pad between the frame and the skin (felt), ends ahead of the radiator
  const pad = (x, d) => [[-0.60, 0], [0.60, 0], [0.60, d], [-0.60, d]].map(([z, y]) => [x, fy(x) + 0.006 + y, z]);
  loftSolid(E(H, 'felt'), [pad(x0 + 0.08, 0.008), pad(2.05, 0.008)], {});
  // latch striker (bare steel loop) at the front centre
  tube(E(H, 'steel'), [[xf - 0.02, fy(xf) - 0.01, -0.025], [xf - 0.01, fy(xf) - 0.05, -0.02], [xf - 0.01, fy(xf) - 0.05, 0.02], [xf - 0.02, fy(xf) - 0.01, 0.025]], 0.004, 6);
}

function cowl() {
  // black louver panel between the hood's rear edge and the windshield, a row of slots
  // (built as a frame plus the bars between the slots: one cap with thirty holes does not triangulate cleanly)
  const x0 = WS0 + 0.012, x1 = HOOD_X0 - 0.004, zH = HW - 0.10, y = hy(x1) - 0.006, T = E('body', 'trim');
  const s0 = x0 + 0.04, s1 = x1 - 0.03;
  rbox(T, FY, x0, s0, -zH, zH, y - 0.008, y, 0.003, {rc: 0.004});
  rbox(T, FY, s1, x1, -zH, zH, y - 0.008, y, 0.003, {rc: 0.004});
  const zs = []; for (let z = -zH + 0.06; z <= zH - 0.06 + 1e-9; z += 0.046) zs.push(z);
  const edges = [-zH, ...zs.flatMap(z => [z - 0.008, z + 0.008]), zH];
  for (let i = 0; i < edges.length; i += 2) rbox(T, FY, s0, s1, edges[i], edges[i + 1], y - 0.008, y, 0.002, {rc: 0});
  // dark plenum floor under the slots
  abox(E('body', 'liner'), x0 + 0.02, x1 - 0.01, y - 0.06, y - 0.05, -zH + 0.03, zH - 0.03);
  // wipers parked along the glass base (arms from their pivots, blades)
  for (const zp of [-0.47, 0.03]) {
    const pv = [x0 + 0.03, y + 0.004, zp], end = [wsX(BELT + 0.03) + 0.012, BELT + 0.035, zp + 0.42];
    lathe(E('body', 'trim'), pv, [0, 1, 0], [[0, 0], [0, 0.016], [0.014, 0.016], [0.02, 0]], 10);
    tube(E('body', 'trim'), [add(pv, [0, 0.02, 0]), add(pv, [-0.02, 0.03, 0.06]), end], 0.0055, 5, {up: [0, 1, 0]});
    const g = norm(sub([wsX(BELT + 0.1), BELT + 0.1, 0], [wsX(BELT), BELT, 0]));   // up the glass
    obox(E('body', 'trim'), add(end, mul(g, 0.004)), [0, 0, 1], g, 0.215, 0.006, 0.009, 0.002);
  }
}

// ================================================================ cab
function cabShell() {
  const B = 'body';
  // windshield: rounded rectangle on the glass plane, the side edges curving back
  const yg0 = BELT + 0.025, yg1 = ROOF - 0.08, gz = y => sideZ(y) - 0.072;
  const bend = z => 0.022 * smoothstep(0.42, 0.74, Math.abs(z)) ** 1.6;
  const at = (z, y, off = 0) => [wsX(y) - bend(z) - off, y, z];
  const outline2 = [];
  const corners = fillet([[-gz(yg0), yg0], [gz(yg0), yg0], [gz(yg1), yg1], [-gz(yg1), yg1]], 0.07, 6);
  // densify the long edges so the bend shows
  for (let i = 0; i < corners.length; i++) { const a = corners[i], b = corners[(i + 1) % corners.length]; outline2.push(a);
    const n = Math.floor(Math.hypot(b[0] - a[0], b[1] - a[1]) / 0.09); for (let k = 1; k < n; k++) outline2.push([a[0] + (b[0] - a[0]) * k / n, a[1] + (b[1] - a[1]) * k / n]); }
  plate3(E(B, 'glass'), outline2, (z, y) => at(z, y));
  // its moulding: black rubber lip around the glass
  moulding(E(B, 'trim'), outline2, (z, y) => at(z, y), 0.016, 0.009);
  // A pillars: from the cowl to the roof header along the glass edges
  sym(s => {
    const p0 = [wsX(BELT - 0.03) + 0.012, BELT - 0.03, s * (sideZ(BELT) - 0.034)], p1 = [wsX(ROOF - 0.05) - 0.01, ROOF - 0.05, s * (sideZ(ROOF - 0.05) - 0.036)];
    const prof = fillet([[-0.034, -0.045], [0.026, -0.045], [0.026, 0.008], [-0.034, 0.008]], [0.006, 0.016, 0.016, 0.006], 3);
    sweep(E(B, 'paint'), [p0, p1], prof.map(([u, v]) => [u, v * s]), {up: [0, 0, s]});
  });
  // roof: crowned, side rolls with drip rails, rolled down into the back panel and the windshield header
  {
    const half = [0, 0.15, 0.30, 0.45, 0.58, 0.68].map(z => [z, 0.011 * (1 - (z / ROOF_W) ** 2)]), rr = 0.03;
    const right = [...half, ...arc(ROOF_W - rr, 0.011 * (1 - ((ROOF_W - rr) / ROOF_W) ** 2) - rr + 0.0, rr, Math.PI / 2 - 0.15, 0, 4), [ROOF_W, -0.06]];
    const line = [...right.slice(1).reverse().map(([z, y]) => [-z, y]), ...right], sec = thicken(line, -0.01);
    const at2 = x => sec.map(([z, y]) => [x, ROOF - 0.016 + y, z]);
    const xb = CB + 0.05, xf = WS1 - 0.03, rb = 0.05, rf = 0.055, phi = Math.atan2(ROOF - 0.045 - BELT, WS0 - WS1);
    const rings = [];
    const D = Math.PI / 180;
    rings.push(rollRings(at2(xb), rb, 0.01, [90 * D], 1)[0].map(q => [q[0], q[1] - 0.03, q[2]]));
    rings.push(...rollRings(at2(xb), rb, 0.01, [90, 60, 30].map(a => a * D), 1));
    rings.push(at2(xb), at2((xb + xf) / 2), at2(xf));
    rings.push(...rollRings(at2(xf), rf, 0.01, [1, 2, 3].map(k => phi * k / 3), -1));
    const d = [Math.cos(phi) * 0.05, -Math.sin(phi) * 0.05];
    rings.push(rollRings(at2(xf), rf, 0.01, [phi], -1)[0].map(q => [q[0] + d[0], q[1] + d[1], q[2]]));
    loftSolid(E(B, 'paint'), rings, {});
    // drip rails along the roof edges
    sym(s => sweep(E(B, 'paint'), [[xb - 0.01, ROOF - 0.064, s * (ROOF_W + 0.004)], [xf + 0.01, ROOF - 0.064, s * (ROOF_W + 0.004)]],
      fillet([[-0.006, -0.004], [0.006, -0.004], [0.006, 0.012], [-0.006, 0.012]], 0.003, 1), {up: [0, 1, 0]}));
  }
  // back panel with the rear window opening and two horizontal ribs; rounded rear corners; B pillar strips
  const rc = 0.06, top = ROOF - 0.075, zc = y => sideZ(y) - rc;
  const back = [[-zc(YB + 0.01), YB + 0.01], [zc(YB + 0.01), YB + 0.01], [zc(BELT), BELT], [zc(top), top], [-zc(top), top], [-zc(BELT), BELT]];
  const rw = fillet([[-0.585, BELT + 0.075], [0.585, BELT + 0.075], [0.56, top - 0.075], [-0.56, top - 0.075]], 0.07, 6);
  const {lo, hi} = splitAtY(back, SPLIT);
  for (const L of lo) slab(E(B, 'paint2'), FRX, L, [], CB, CB + 0.012, {r0: 0.003, r1: 0});
  for (const L of hi) slab(E(B, 'paint'), FRX, L, L.some(q => q[1] > BELT + 0.1) ? [rw] : [], CB, CB + 0.012, {r0: 0.003, r1: 0});
  for (const y of [0.70, 0.92]) rbox(E(B, y < SPLIT ? 'paint2' : 'paint'), FRX, -0.62, 0.62, y - 0.016, y + 0.016, CB - 0.006, CB + 0.004, 0.005, {rc: 0.016});
  plate3(E(B, 'glass'), rw, (z, y) => [CB + 0.004, y, z]);
  moulding(E(B, 'trim'), rw, (z, y) => [CB + 0.004, y, z], 0.014, 0.008, -1);
  sym(s => {
    const corner = (y, mat) => { const z0 = zc(y), out = [], inn = [];
      for (let i = 0; i <= 6; i++) { const a = Math.PI / 2 * i / 6; out.push([CB + rc - rc * Math.cos(a), y, s * (z0 + rc * Math.sin(a))]); inn.push([CB + rc - (rc - 0.012) * Math.cos(a), y, s * (z0 + (rc - 0.012) * Math.sin(a))]); }
      return [...out, ...inn.reverse()]; };
    loftSolid(E(B, 'paint2'), [corner(YB + 0.01), corner(SPLIT)], {});
    loftSolid(E(B, 'paint'), [corner(SPLIT), corner(BELT), corner(top)], {});
    // B pillar strip: from the rounded corner to the door's rear edge
    const strip = y => { const z = sideZ(y); return [[CB + rc, y, s * (z - 0.012)], [DOOR_R - 0.004, y, s * (z - 0.012)], [DOOR_R - 0.004, y, s * z], [CB + rc, y, s * z]]; };
    loftSolid(E(B, 'paint2'), [strip(YB + 0.01), strip(SPLIT)], {});
    loftSolid(E(B, 'paint'), [strip(SPLIT), strip(BELT), strip(top)], {});
    // rocker panel under the door
    const [z0, z1] = zr(s, HW - 0.045, HW - 0.016);
    rbox(E(B, 'paint2'), FZ, CB + 0.02, DOOR_F, YB - 0.012, YB + 0.04, z0, z1, 0.008, {rc: 0.012});
    // cab-to-bed and cab-to-fender seams are open gaps; the jambs behind are in the cab mesh
  });
  // floor pan and firewall (the shell's underside and front wall)
  abox(E(B, 'under'), CB + 0.012, 0.99, 0.52, 0.535, -(HW - 0.045), HW - 0.045);
  abox(E(B, 'paint'), 0.975, 0.99, 0.535, hy(0.99) - 0.05, -0.552, 0.552);
}

/** A flat-ish plate: a 2D outline (u, v) triangulated and mapped by map(u, v) -> 3D (single surface, both sides drawn). */
function plate3(p, outline, map) {
  const L = ccw(outline), {pts, tris} = triangulate(L, []), ids = pts.map(([u, v]) => p.vtx(map(u, v)));
  for (const t of tris) { const q = t.map(i => ids[i]), A = p.v[q[0]], Bq = p.v[q[1]], C = p.v[q[2]];
    p.face(q, cross(sub(Bq, A), sub(C, A)), 'cap'); }
}
/** A rubber or chrome surround along a closed outline on a mapped plate: width w outward, depth d (toward -normal). */
function moulding(p, outline, map, w, d, side = 1) {
  const L = ccw(outline), n = L.length, rings = [];
  for (let i = 0; i < n; i++) {
    const a = L[(i + n - 1) % n], b = L[i], c = L[(i + 1) % n];
    const e = norm([c[0] - a[0], c[1] - a[1], 0]), o2 = [e[1], -e[0]];   // outward in the outline plane
    const P0 = map(b[0], b[1]), Pu = map(b[0] + o2[0] * 0.01, b[1] + o2[1] * 0.01), Pt = map(c[0], c[1]);
    const out = norm(sub(Pu, P0)), tng = norm(sub(Pt, P0)), nrm = mul(norm(cross(tng, out)), 1);
    const prof = [[-0.006, 0.003], [w * 0.5, 0.004], [w, -0.001], [w, -d], [-0.006, -d * 0.6]];
    rings.push(prof.map(([o, h]) => add(P0, add(mul(out, o), mul(nrm, h * side)))));
  }
  loftSolid(p, rings, {wrap: true});
}

// ================================================================ doors (bones door_l / door_r)
function doors() {
  sym(s => {
    const D = s > 0 ? 'door_r' : 'door_l', xr = DOOR_R, xf = DOOR_F, yb = YB + 0.04, yt = BELT - 0.028;
    // shell: a thick panel (outer skin to the inner panel edge), outer edges rounded
    const outline = fillet([[xr, yb], [xf, yb], [xf, yt], [xr, yt]], [0.03, 0.03, 0, 0], 3);
    const {lo, hi} = splitAtY(outline, SPLIT);
    for (const L of lo) sidePanel(E(D, 'paint2'), s, L, HW - 0.075, HW, 0.006, 0.01);
    for (const L of hi) sidePanel(E(D, 'paint'), s, L, HW - 0.075, HW, 0.006, 0.01);
    // top roll into the window slot
    const sec = thicken(fillet([[HW, yt - 0.004], [HW, BELT], [HW - 0.022, BELT], [HW - 0.022, BELT - 0.03]], [0, 0.02, 0.004, 0], 4, false), 0.01).map(([z, y]) => [s * z, y]);
    loftSolid(E(D, 'paint'), xRings([{x: xr + 0.002, sec}, {x: xf - 0.002, sec}]), {});
    // belt weatherstrips (outer rubber on the slot, inner rubber on the trim side)
    for (const zz of [HW - 0.024, HW - 0.068]) sweep(E(D, 'rubber'), [[xr + 0.03, BELT + 0.004, s * zz], [aX(BELT) - 0.07, BELT + 0.004, s * zz]],
      fillet([[-0.006, -0.005], [0.006, -0.005], [0.006, 0.006], [-0.006, 0.006]], 0.003, 1), {up: [0, 1, 0]});
    // window sash: painted channel around the glass, leaning with the cab side, following the A pillar at the front
    const yS = ROOF - 0.072, sz = y => s * (sideZ(y) - 0.016);
    const path = roundPath([[xr + 0.022, BELT, sz(BELT)], [xr + 0.022, yS, sz(yS)], [aX(yS) - 0.035, yS, sz(yS)], [aX(BELT) - 0.035, BELT, sz(BELT)]], 0.045, 5);
    sweep(E(D, 'paint'), path, fillet([[-0.013, -0.016], [0.013, -0.016], [0.013, 0.016], [-0.013, 0.016]], 0.004, 1).map(([u, v]) => [u, v]), {up: [0, 0, s]});
    // glass inside the sash (single surface on the leaning side plane)
    const g = roundPath([[xr + 0.04, BELT + 0.01], [xr + 0.04, yS - 0.02], [aX(yS - 0.02) - 0.05, yS - 0.02], [aX(BELT + 0.01) - 0.05, BELT + 0.01]].map(([x, y]) => [x, y, 0]), 0.03, 4).map(([x, y]) => [x, y]);
    plate3(E(D, 'glass'), g.map(([x, y]) => [x, y]), (x, y) => [x, y, s * (sideZ(y) - 0.026)]);
    // mirror: triangular base at the sash's front corner, arm, housing, glass
    const mb = [aX(BELT) - 0.06, BELT + 0.03];
    const tri = [[mb[0] - 0.09, BELT + 0.004], [mb[0] + 0.02, BELT + 0.004], [mb[0] + 0.02, BELT + 0.10]];
    const [t0, t1] = zr(s, HW - 0.022, HW - 0.004);
    slab(E(D, 'trim'), FZ, tri, [], t0, t1, s > 0 ? {r0: 0, r1: 0.004} : {r0: 0.004, r1: 0});
    const hc = [mb[0] - 0.03, BELT + 0.135, s * (HW + 0.13)];
    tube(E(D, 'trim'), [[mb[0] - 0.02, BELT + 0.05, s * (HW - 0.01)], [mb[0] - 0.03, BELT + 0.085, s * (HW + 0.05)], [hc[0], hc[1] - 0.02, s * (HW + 0.08)]], 0.011, 6);
    obox(E(D, 'trim'), hc, [0, 0, 1], [0, 1, 0], 0.085, 0.07, 0.03, 0.012);
    const mg = fillet([[-0.075, -0.06], [0.075, -0.06], [0.075, 0.06], [-0.075, 0.06]], 0.012, 2);
    plate3(E(D, 'mirror'), mg, (u, v) => [hc[0] - 0.033, hc[1] + v, hc[2] + u]);   // 3 mm off the housing face (0.5 mm z-fought)
    // flush pull handle in a recess near the rear, key cylinder beside it
    const hx0 = xr + 0.06, hx1 = xr + 0.205, hy0 = BELT - 0.11, hy1 = BELT - 0.07;
    const [r0, r1] = zr(s, HW - 0.003, HW + 0.002);
    rbox(E(D, 'trim'), FZ, hx0, hx1, hy0, hy1, r0, r1, 0.002, {rc: 0.012});
    const [q0, q1] = zr(s, HW, HW + 0.009);
    rbox(E(D, 'trim'), FZ, hx0 + 0.012, hx1 - 0.012, hy0 + 0.012, hy1 - 0.006, q0, q1, 0.003, {rc: 0.006});
    lathe(E(D, 'chrome'), [hx1 + 0.03, (hy0 + hy1) / 2, s * HW], [0, 0, s], [[0, 0], [0, 0.011], [0.004, 0.011], [0.006, 0.008], [0.006, 0]], 12);
    // side moulding on the split line
    const [m0, m1] = zr(s, HW - 0.002, HW + 0.007);
    rbox(E(D, 'trim'), FZ, xr + 0.01, xf - 0.01, SPLIT - 0.016, SPLIT + 0.016, m0, m1, 0.003, {rc: 0.006});
  });
}
/** The A pillar's centre line x at height y (the windshield edge). */
export const aX = y => wsX(y) - 0.02;

// ================================================================ bed and tailgate
function bed() {
  const B = 'body', xr = BED_R, xf = BED_F, top = BED_TOP - 0.03, post = 0.10;   // rear corner post depth (x)
  sym(s => {
    const aRear = YB + 0.03, aFront = YB + 0.03;
    const ap = archPts(RX, AR, aRear, aFront, 22);
    const out2 = [[xr + post, YB + 0.03], ...ap, [xf, YB + 0.03], [xf, top], [xr + post, top]];
    twoTone(B, s, fillet(out2, [0.0, ...new Array(23).fill(0), 0.025, 0.01, 0], 3).filter(Boolean), HW - 0.012, HW, 0.006, 0.002, s < 0 ? [FILL_HOLE] : []);
    archLip(E(B, 'paint'), s, RX, aRear, aFront, 22, HW);
    archLiner(s, RX, 0.49, 0.565, HW - 0.004);
    // rear corner post: a quarter round from the side to the rear face, the rear face carrying the lamps
    const rc = 0.05, corner = (y) => { const out = [], inn = [];
      for (let i = 0; i <= 5; i++) { const a = Math.PI / 2 * i / 5; out.push([xr + rc - rc * Math.cos(a), y, s * (HW - rc + rc * Math.sin(a))]); inn.push([xr + rc - (rc - 0.012) * Math.cos(a), y, s * (HW - rc + (rc - 0.012) * Math.sin(a))]); }
      return [...out, ...inn.reverse()]; };
    loftSolid(E(B, 'paint2'), [corner(YB + 0.03), corner(SPLIT)], {});
    loftSolid(E(B, 'paint'), [corner(SPLIT), corner(top)], {});
    // post side between the corner and the side panel
    const ps = y => [[xr + rc, y, s * (HW - 0.012)], [xr + post, y, s * (HW - 0.012)], [xr + post, y, s * HW], [xr + rc, y, s * HW]];
    loftSolid(E(B, 'paint2'), [ps(YB + 0.03), ps(SPLIT)], {}); loftSolid(E(B, 'paint'), [ps(SPLIT), ps(top)], {});
    // post rear face (x = xr) from the corner inward to the tailgate gap
    const pz0 = 0.672, pz1 = HW - rc;
    const face = (y) => [[xr, y, s * pz0], [xr, y, s * pz1], [xr + 0.012, y, s * pz1], [xr + 0.012, y, s * pz0]];
    loftSolid(E(B, 'paint2'), [face(YB + 0.03), face(SPLIT)], {}); loftSolid(E(B, 'paint'), [face(SPLIT), face(top)], {});
    // post inner wall (the tailgate gap side)
    const inner = y => [[xr, y, s * (pz0 - 0.012)], [xr, y, s * pz0], [xr + 0.09, y, s * pz0], [xr + 0.09, y, s * (pz0 - 0.012)]];
    loftSolid(E(B, 'paint'), [inner(0.60), inner(top)], {});
    // top rail: rolled outer edge, flat top, down the inner side
    const rail = thicken(fillet([[HW, top - 0.004], [HW, BED_TOP], [HW - 0.088, BED_TOP], [HW - 0.088, BED_TOP - 0.05]], [0, 0.02, 0.012, 0], 4, false), 0.01).map(([z, y]) => [s * z, y]);
    loftSolid(E(B, 'paint'), xRings([{x: xr + 0.002, sec: rail}, {x: xf - 0.002, sec: rail}]), {});
    // stake pockets on the rail (dark rectangular openings)
    for (const x of [-0.52, -1.32, -2.08]) { const [a, b] = zr(s, HW - 0.072, HW - 0.022); rbox(E(B, 'liner'), FY, x - 0.035, x + 0.035, a, b, BED_TOP - 0.002, BED_TOP + 0.004, 0, {rc: 0.006}); }   // 4 mm over the rail top (1.2 mm z-fought)
    // inner wall with a row of rope-hook slots near the top
    const iz = HW - 0.088, [w0, w1] = zr(s, iz - 0.008, iz);
    const wall = [[xr + 0.09, BED_FL - 0.005], [xf - 0.04, BED_FL - 0.005], [xf - 0.04, BED_TOP - 0.05], [xr + 0.09, BED_TOP - 0.05]];
    slab(E(B, 'paint'), FZ, wall, [], w0, w1, {r0: 0, r1: 0});
    for (let x = xr + 0.20; x < xf - 0.12; x += 0.215) { const [a, b] = zr(s, iz - 0.012, iz - 0.0075); rbox(E(B, 'liner'), FZ, x - 0.026, x + 0.026, BED_TOP - 0.10, BED_TOP - 0.085, a, b, 0, {rc: 0.006}); }   // 4 mm proud of the wall (1.5 mm z-fought)
    // wheel housing inside the bed
    const hr = AR + 0.035, ha = Math.asin((BED_FL - 0.01 - AY) / hr), [h0, h1] = zr(s, 0.56, iz - 0.008);
    lathe(E(B, 'paint'), [RX, AY, 0], [0, 0, 1], [[h0, 0], [h1, 0], [h1, hr], [h0, hr]], 16, {a0: ha, a1: Math.PI - ha, e1: [1, 0, 0]});
    // side moulding, rear side marker, fuel filler door (left)
    const h2 = Math.sqrt(AR * AR - (SPLIT - AY) ** 2) + 0.012, [m0, m1] = zr(s, HW - 0.002, HW + 0.007);
    for (const [a, b] of [[xr + post + 0.004, RX - h2], [RX + h2, xf - 0.008]]) rbox(E(B, 'trim'), FZ, a, b, SPLIT - 0.016, SPLIT + 0.016, m0, m1, 0.003, {rc: 0.006});
    const [k0, k1] = zr(s, HW - 0.002, HW + 0.005);
    rbox(E(B, 'red'), FZ, xr + post + 0.03, xr + post + 0.11, 0.935, 0.965, k0, k1, 0.002, {rc: 0.008});
    if (s < 0) {
      // the door (its own bone, swings out 80 deg about its front edge), a black pocket 12 mm behind the skin, the steel neck ring and a ribbed black cap
      const [f0, f1] = zr(s, HW - 0.002, HW + 0.002);
      rbox(E('fuel_door', 'paint'), FZ, FILL.x0 - 0.005, FILL.x1 + 0.005, FILL.y0 - 0.005, FILL.y1 + 0.005, f0, f1, 0.0015, {rc: 0.012});
      const [k0, k1] = zr(s, HW - 0.040, HW - 0.012);
      rbox(E(B, 'trim'), FZ, FILL.x0 - 0.006, FILL.x1 + 0.006, FILL.y0 - 0.006, FILL.y1 + 0.006, k0, k1, 0, {rc: 0.012});
      const back = -(HW - 0.012);
      // lathe profiles are [along the axis, radius]; the cap tops out 3.5 mm under the skin (the shut door's back is 2 mm under it)
      lathe(E(B, 'steel'), [FILL.cx, FILL.cy, back], [0, 0, -1], [[0, 0.026], [0.003, 0.026], [0.003, 0.036], [0, 0.036]], 16);
      lathe(E(B, 'rubber'), [FILL.cx, FILL.cy, back], [0, 0, -1], [[0.003, 0], [0.003, 0.030], [0.0065, 0.030], [0.008, 0.026], [0.008, 0]], 16);
      for (let k = 0; k < 6; k++) { const a = k * Math.PI / 3, c = Math.cos(a), sn = Math.sin(a);   // the cap's grip ribs
        obox(E(B, 'rubber'), [FILL.cx + 0.017 * c, FILL.cy + 0.017 * sn, back - 0.0085], [c, sn, 0], [-sn, c, 0], 0.009, 0.0025, 0.001, 0.0005); }   // 8..26 mm out: they do not meet in the middle
    }
    // tail lamp: three sections (turn / stop-tail / reverse) on the post's rear face
    const lz0 = 0.69, lz1 = HW - 0.012;
    const lamp = (y0, y1, mat, d, tag) => rbox(E(B, mat, tag), FRX, s > 0 ? lz0 : -lz1, s > 0 ? lz1 : -lz0, y0, y1, xr - d, xr + 0.004, 0.004, {rc: 0.008});
    rbox(E(B, 'trim'), FRX, s > 0 ? lz0 - 0.008 : -lz1 - 0.004, s > 0 ? lz1 + 0.004 : -lz0 + 0.008, 0.645, 1.045, xr - 0.016, xr + 0.002, 0.004, {rc: 0.012});
    lamp(0.92, 1.035, 'amber', 0.026, side(s)); lamp(0.745, 0.912, 'lamp_tail', 0.026); lamp(0.655, 0.737, 'lamp_reverse', 0.026);
  });
  // floor with longitudinal ribs, front wall with two horizontal ribs
  const fz = HW - 0.096;
  abox(E(B, 'paint'), BED_R + 0.02, BED_F - 0.04, BED_FL - 0.04, BED_FL, -fz, fz);
  for (let i = -3; i <= 3; i++) { const z = i * 0.19; rbox(E(B, 'paint'), FY, BED_R + 0.06, BED_F - 0.08, z - 0.022, z + 0.022, BED_FL - 0.002, BED_FL + 0.011, 0.004, {rc: 0.008}); }
  slab(E(B, 'paint'), FRX, [[-fz, BED_FL - 0.04], [fz, BED_FL - 0.04], [fz, BED_TOP - 0.004], [-fz, BED_TOP - 0.004]], [], BED_F - 0.04, BED_F - 0.028, {r0: 0.003, r1: 0});
  rbox(E(B, 'paint'), FRX, -(HW - 0.004), HW - 0.004, BED_TOP - 0.032, BED_TOP, BED_F - 0.05, BED_F, 0.008, {rc: 0.012});   // front rail cap
  sym(s => { const [a, b] = zr(s, fz - 0.002, HW - 0.004); abox(E(B, 'paint'), BED_F - 0.04, BED_F, YB + 0.04, BED_TOP - 0.03, a, b, 0.004); });
  for (const y of [0.88, 1.0]) rbox(E(B, 'paint'), FRX, -(fz - 0.04), fz - 0.04, y - 0.018, y + 0.018, BED_F - 0.054, BED_F - 0.038, 0.006, {rc: 0.018});
  abox(E(B, 'paint'), BED_R + 0.002, BED_R + 0.04, 0.584, 0.615, -0.672, 0.672, 0.006);   // rear sill under the tailgate (underside clear of the rear crossmember's)
  // rear bumper: chrome step bumper with a black step pad and the plate recess
  const plan = roundPath([[-2.17, 0.47, -0.88], [-2.33, 0.47, -0.80], [-2.355, 0.47, -0.60], [-2.355, 0.47, 0.60], [-2.33, 0.47, 0.80], [-2.17, 0.47, 0.88]], 0.13, 5);
  const bs = fillet([[-0.09, 0.0], [0.09, 0.0], [0.09, 0.11], [0.075, 0.11], [0.075, 0.012], [-0.075, 0.012], [-0.075, 0.09], [-0.09, 0.09]], [0.02, 0.02, 0.006, 0.004, 0.004, 0.004, 0.004, 0.006], 3);
  bumperSweep(E(B, 'chrome'), plan.map(q => q), bs.map(([u, v]) => [u, v]));
  rbox(E(B, 'rubber'), FY, -2.345, -2.25, -0.30, 0.30, 0.558, 0.566, 0.003, {rc: 0.01});
  rbox(E(B, 'trim'), FRX, -0.17, 0.17, 0.40, 0.54, -2.37, -2.356, 0.003, {rc: 0.01});   // plate holder (no plate)
  lathe(E(B, 'lamp_plate'), [-2.352, 0.552, 0], [0, -1, 0], [[0, 0], [0, 0.018], [0.008, 0.014], [0.01, 0]], 10);   // plate lamp
}

function tailgate() {
  const T = 'tailgate', z1 = 0.664, x0 = BED_R, y0 = 0.62, y1 = BED_TOP - 0.004;
  // outer skin with a horizontal crease near the top; inner panel with stamped ribs
  const out = fillet([[-z1, y0], [z1, y0], [z1, y1], [-z1, y1]], [0.012, 0.012, 0.006, 0.006], 2);
  const {lo, hi} = splitAtY(out, SPLIT);
  for (const L of lo) slab(E(T, 'paint2'), FRX, L, [], x0, x0 + 0.05, {r0: 0.006, r1: 0.004});
  for (const L of hi) slab(E(T, 'paint'), FRX, L, [], x0, x0 + 0.05, {r0: 0.006, r1: 0.004});
  rbox(E(T, 'paint'), FRX, -(z1 - 0.03), z1 - 0.03, 1.002, 1.014, x0 - 0.004, x0 + 0.003, 0.003, {rc: 0.006});   // crease
  for (const y of [0.72, 0.85, 0.98]) rbox(E(T, 'paint'), FRX, -(z1 - 0.06), z1 - 0.06, y - 0.03, y + 0.03, x0 + 0.045, x0 + 0.058, 0.006, {rc: 0.02});
  // centre handle in a recess at the top
  rbox(E(T, 'trim'), FRX, -0.10, 0.10, y1 - 0.075, y1 - 0.03, x0 - 0.003, x0 + 0.004, 0.002, {rc: 0.012});
  rbox(E(T, 'trim'), FRX, -0.085, 0.085, y1 - 0.065, y1 - 0.045, x0 - 0.008, x0, 0.003, {rc: 0.008});
  // bottom hinges and the two support cables to the posts (bed side)
  sym(s => {
    abox(E(T, 'steel'), x0 + 0.01, x0 + 0.055, y0 - 0.02, y0 + 0.03, s * (z1 - 0.07) - 0.025, s * (z1 - 0.07) + 0.025, 0.004);   // 5 mm past the skin's inner face
    tube(E(T, 'steel'), [[x0 + 0.04, y1 - 0.05, s * (z1 - 0.012)], [x0 + 0.20, y1 - 0.22, s * (z1 + 0.004)], [x0 + 0.075, y1 - 0.06, s * (z1 + 0.012)]].map((q, i) => q), 0.003, 5);
  });
}

export function buildExterior() {
  MESH = 'front';
  fenders(); frontEnd(); hood(); cowl();
  // antenna on the right fender ahead of the A pillar
  tube(E('body', 'chrome'), [[WS0 + 0.06, hy(WS0 + 0.06) - 0.004, HW - 0.065], [WS0 + 0.06, hy(WS0 + 0.06) + 0.76, HW - 0.065]], 0.0028, 5);
  lathe(E('body', 'trim'), [WS0 + 0.06, hy(WS0 + 0.06) - 0.004, HW - 0.065], [0, 1, 0], [[0, 0], [0, 0.012], [0.012, 0.007], [0.02, 0]], 10);
  MESH = 'ext';
  cabShell(); doors(); bed(); tailgate();
}
