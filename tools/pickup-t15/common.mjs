// Halstead Tamarack T15 pickup: shared dimensions, materials and the mesh / part registry (tools/build-pickup-t15.mjs).
// Concept frame while building: metres, x forward, y up, z to the right, the driver at -z (left-hand drive). The builder
// turns it into the runtime frame at the end (front toward -Z, right toward +X: runtime px = (z, y, -x) x 16).
// Structure follows the real reference (docs/vehicles/first_pickup_v0_design.md section 2.4: Toyota Hilux LN106 1992
// regular cab, the factory parts catalogue drawings); the face keeps the user's direction A (four square lamps, chrome
// grille and bumpers). Nothing here copies a trademark or a badge.
import {Part} from '../vehicle-mesh-kit.mjs';

// ---------------------------------------------------------------- dimensions (metres)
// reference: wheelbase 2850, width 1690, height 1755, single-cab length ~4730, track 1420 / 1400, tyres 225/75 R15
export const W = 1.69, HW = W / 2;
export const FX = 1.60, RX = -1.25;               // front / rear axle (wheelbase 2.85)
export const R = 0.36, TW = 0.225, TR = 0.712;     // tyre radius (225/75 R15: 0.718 m), width, wheel centre |z|
export const AY = 0.40, AR = 0.455;                // wheel arch centre height and radius
export const YB = 0.46;                            // body bottom (sills, panel lower edges)
export const BELT = 1.07, ROOF = 1.845;            // window sill line, roof top (raised 10 cm 2026-10-16: room for a larger seated rider)
export const HF = 1.005;                           // hood top at its front edge
export const FX0 = 2.275;                          // front face of the body (lamps, grille)
export const CB = -0.30;                           // outer face of the cab back panel
export const BED_F = -0.36, BED_R = -2.255, BED_TOP = 1.11, BED_FL = 0.76;
export const SPLIT = 0.78;                         // two-tone split, hidden under the side moulding
export const WS0 = 0.955;                          // windshield base (x at the belt height)
export const WS1 = 0.42;                           // windshield top (x at the roof header)
export const DOOR_R = -0.19, DOOR_F = 0.946;       // door rear / front edges (x)
export const LEAN = 0.055;                         // tumblehome: the cab side leans in this much from the belt to the roof
export const ROOF_W = HW - LEAN - 0.012;           // half width of the roof panel
/** Cab side plane above the belt (|z| of the outer side at height y). */
export const sideZ = y => HW - LEAN * Math.max(0, Math.min(1, (y - BELT) / (ROOF - 0.04 - BELT)));
/** Hood / fender top line (y at x). */
export const hy = x => 1.077 + (x - 1.08) * (HF - 1.077) / (2.25 - 1.08);
/** Windshield line (x at height y, on the glass plane). */
export const wsX = y => WS0 + (y - BELT) * (WS1 - WS0) / (ROOF - 0.045 - BELT);
export const HOOD_X0 = 1.095;                      // hood rear edge
export const HOOD_Z = HW - 0.112;                  // hood half width

// bone pivots (concept frame); runtime turns: hood +X 58 deg, tailgate +X 90 deg, doors about Y
export const PIVOTS = {
  body: [0, 0, 0],
  hood: [HOOD_X0 + 0.01, hy(HOOD_X0) - 0.035, 0],
  door_l: [DOOR_F - 0.03, 0.80, -(HW - 0.02)], door_r: [DOOR_F - 0.03, 0.80, HW - 0.02],
  tailgate: [BED_R + 0.01, 0.615, 0],
  fuel_door: [-0.545, 0.94, -HW],
  steering: [0.40, 1.125, -0.37],
  // the gear lever's rod and knob tilt about the top of its rubber boot (2026-10-16: the H gate, vehicle/ShiftLever)
  shift: [0.43, 0.725, -0.02],
  // the transfer case lever beside it: rod and knob tilt fore / aft about its boot's top (2026-10-17: 2H 4H N 4L, vehicle/TransferLever)
  transfer: [0.38, 0.725, 0.07],
  // gauge needles: the dial centres in the cluster face (cab.mjs cluster())
  tach: [0.6205, 1.066, -0.445], speedo: [0.6205, 1.066, -0.335], fuel_gauge: [0.6205, 1.08, -0.54], temp_gauge: [0.6205, 1.08, -0.24],
  wheel: [0, 0, 0],
};
export const STEER_TILT = 27;
// hinges: axis (concept frame, through the bone pivot) and the open angle in degrees (the renderer's turn; sign as drawn)
export const HINGES = {
  hood: {axis: [0, 0, 1], open: 58, note: 'rear edge hinges; front lifts'},
  door_l: {axis: [0, 1, 0], open: 68, note: 'front hinge pillar'},
  door_r: {axis: [0, 1, 0], open: 68, note: 'front hinge pillar'},
  tailgate: {axis: [0, 0, 1], open: 90, note: 'bottom hinges; drops flat'},
  fuel_door: {axis: [0, 1, 0], open: 80, note: 'front edge; swings out like the doors'},
  steering: {axis: [Math.cos(27 * Math.PI / 180), -Math.sin(27 * Math.PI / 180), 0], open: 540, note: 'column axis; +-1.5 turns lock to lock'},
  shift: {axis: [0, 0, 1], open: 11, note: 'gear lever: fore / aft 11 deg about the across axis, across 9 deg about the fore axis (H gate: 1 3 5 forward, 2 4 R back)'},
  transfer: {axis: [0, 0, 1], open: 14, note: 'transfer lever: fore / aft about the across axis; 2H +8, 4H -1, N -7, 4L -14 deg (knob forward +)'},
  tach: {axis: [1, 0, 0], open: 270, note: 'needle about the dial axis; 0-5000 rpm over 270 deg clockwise'},
  speedo: {axis: [1, 0, 0], open: 270, note: 'needle about the dial axis; 0-180 km/h over 270 deg clockwise'},
  fuel_gauge: {axis: [1, 0, 0], open: 120, note: 'needle about the dial axis; empty - full over 120 deg clockwise'},
  temp_gauge: {axis: [1, 0, 0], open: 120, note: 'needle about the dial axis; 40 - 120 C over 120 deg clockwise'},
};
// interaction / attachment anchors (concept frame, on a bone): seats and eye points for the player skeleton, hands on the
// wheel, pedals and levers for IK, handles for interaction, lamps for light sources, filler and exhaust for effects
export const ANCHORS = {
  // the cushion's top under the hips (lowered 3 cm 2026-10-16) and the seated eye (SeatPose: cushion + EYE_OVER_SEAT, EYE_AHEAD ahead)
  seat_driver_hip: ['body', [-0.06, 0.815, -0.37]], seat_passenger_hip: ['body', [-0.06, 0.815, 0.37]],
  eye_driver: ['body', [0.04, 1.487, -0.37]], eye_passenger: ['body', [0.04, 1.487, 0.37]],
  steering_centre: ['steering', [0.40, 1.125, -0.37]], steering_grip_l: ['steering', [0.40, 1.125, -0.56]], steering_grip_r: ['steering', [0.40, 1.125, -0.18]],
  pedal_clutch: ['body', [0.735, 0.665, -0.53]], pedal_brake: ['body', [0.735, 0.665, -0.41]], pedal_accelerator: ['body', [0.735, 0.62, -0.25]],
  shift_knob: ['shift', [0.33, 1.02, -0.055]], transfer_knob: ['transfer', [0.33, 0.895, 0.07]], parking_brake: ['body', [0.548, 0.79, -0.13]],
  door_l_outer_handle: ['door_l', [-0.057, 0.98, -0.85]], door_r_outer_handle: ['door_r', [-0.057, 0.98, 0.85]],
  door_l_inner_handle: ['door_l', [0.73, 0.925, -0.755]], door_r_inner_handle: ['door_r', [0.73, 0.925, 0.755]],
  door_l_entry: ['body', [0.30, 0.0, -1.45]], door_r_entry: ['body', [0.30, 0.0, 1.45]],
  hood_latch: ['hood', [2.24, 1.0, 0]], tailgate_handle: ['tailgate', [-2.262, 1.05, 0]],
  fuel_filler: ['body', [-0.6225, 0.94, -(HW - 0.015)]],
  headlamp_l: ['body', [2.282, 0.81, -0.66]], headlamp_r: ['body', [2.282, 0.81, 0.66]],
  tail_lamp_l: ['body', [-2.285, 0.83, -0.77]], tail_lamp_r: ['body', [-2.285, 0.83, 0.77]],
};
// soft / hard edges for the per-corner normals (Vextris format 3 crease_angle, written into every sidecar): within one part,
// faces sharing a corner position are smoothed together when their dihedral angle is under the crease angle (rounded edges are built from steps of at most
// 45 deg, so they come out smooth; 90 deg panel edges, folds and flanges stay hard; separate primitives never share
// vertices, so seams between them stay hard). Per-material overrides where a surface must stay faceted or fully soft.
export const SMOOTHING = {crease_deg: 50, materials: {glass: 0, mirror: 0, dial: 0, lens: 30, tire: 60,
  lamp_low: 30, lamp_high: 30, lamp_reverse: 30, lamp_plate: 30, lamp_dome: 30}};
// spare wheel: under the bed behind the rear axle, lying flat with its outer face down (hung from the carrier winch)
export const SPARE = {x: -1.86, y: 0.50};   // degrees the steering wheel plane leans back from vertical

// ---------------------------------------------------------------- materials
// c: sRGB albedo; sm: LabPBR smoothness; f0: LabPBR F0 (10 = 0.04 dielectric; 230 iron, 232 aluminium, 233 chrome);
// alpha: translucent (glass); aoK: how much baked occlusion it takes (0 = none); crease overrides are
// in SMOOTHING above. Painted materials are a light neutral grey the renderer multiplies by the paint.
export const MATS = {
  paint: {c: [226, 226, 223], sm: 168, f0: 12},
  paint2: {c: [226, 226, 223], sm: 168, f0: 12},
  chrome: {c: [196, 200, 204], sm: 236, f0: 233, env: 1},
  trim: {c: [33, 34, 35], sm: 78, f0: 10},           // black textured plastic
  rubber: {c: [24, 24, 25], sm: 46, f0: 8},
  tire: {c: [27, 27, 28], sm: 38, f0: 8},
  glass: {c: [128, 146, 150], sm: 248, f0: 10, alpha: 92, aoK: 0},
  lens: {c: [214, 218, 220], sm: 232, f0: 10},
  amber: {c: [214, 128, 32], sm: 226, f0: 10},
  red: {c: [158, 22, 22], sm: 226, f0: 10},
  // lamps that light (2026-10-16, docs/vehicles/first_pickup_v0_design.md 8.10): their own parts, so the renderer can light
  // each group; they look like the lens / red / amber above
  lamp_low: {c: [214, 218, 220], sm: 232, f0: 10},     // outer headlamps: low and high beam
  lamp_high: {c: [214, 218, 220], sm: 232, f0: 10},    // inner headlamps: high beam only
  lamp_park: {c: [214, 128, 32], sm: 226, f0: 10},     // front amber parking lamps and side markers (with the lights)
  lamp_tail: {c: [158, 22, 22], sm: 226, f0: 10},      // stop / tail section (dim with the lights, bright braking)
  lamp_reverse: {c: [214, 218, 220], sm: 232, f0: 10}, // reverse section
  lamp_plate: {c: [214, 218, 220], sm: 232, f0: 10},   // licence plate lamp (with the lights)
  lamp_dome: {c: [214, 218, 220], sm: 232, f0: 10},    // cab dome lamp (a door open)
  mirror: {c: [150, 158, 162], sm: 252, f0: 237, env: 1},
  liner: {c: [24, 24, 24], sm: 34, f0: 8},
  under: {c: [30, 30, 31], sm: 70, f0: 10},          // black underbody paint (frame, axles, springs)
  steel: {c: [118, 120, 122], sm: 120, f0: 230},     // bare / zinc-plated steel (bolts, hinges, cables)
  wheel: {c: [172, 174, 176], sm: 150, f0: 12},      // silver painted steel wheel
  // cab interior (1990s grey trim)
  dash: {c: [60, 61, 63], sm: 62, f0: 10},           // moulded panel plastic, grained
  pad: {c: [50, 51, 53], sm: 84, f0: 10},            // padded vinyl dash top
  panel: {c: [104, 104, 102], sm: 58, f0: 10},       // door trim, kick panels, garnishes (lighter grey)
  cloth: {c: [92, 92, 94], sm: 30, f0: 8},           // seat cloth
  vinyl: {c: [66, 66, 68], sm: 92, f0: 10},          // seat sides, steering wheel, knobs
  headliner: {c: [172, 168, 160], sm: 36, f0: 8},
  mat: {c: [32, 32, 33], sm: 48, f0: 8},             // rubber floor mat
  belt: {c: [48, 49, 52], sm: 40, f0: 8},            // seat belt webbing
  dial: {c: [16, 16, 18], sm: 120, f0: 10},          // gauge faces (white marks painted by shade)
  needle: {c: [222, 96, 36], sm: 140, f0: 10},
  // engine bay
  engine: {c: [60, 68, 76], sm: 90, f0: 12},         // painted cast iron
  cover: {c: [34, 34, 35], sm: 70, f0: 12},          // crinkle-black valve cover
  alu: {c: [140, 140, 136], sm: 120, f0: 232},       // cast aluminium
  iron: {c: [82, 66, 56], sm: 56, f0: 24},           // exhaust iron, heat-browned
  radiator: {c: [38, 38, 38], sm: 70, f0: 20},
  cream: {c: [196, 190, 170], sm: 118, f0: 10},      // translucent-white reservoir plastic
  washer: {c: [112, 150, 178], sm: 118, f0: 10},
  redcap: {c: [150, 32, 28], sm: 120, f0: 10},
  yellow: {c: [198, 160, 40], sm: 120, f0: 10},
  lead: {c: [104, 106, 108], sm: 90, f0: 235},
  felt: {c: [36, 35, 33], sm: 28, f0: 8},            // hood insulation
  belting: {c: [28, 28, 28], sm: 40, f0: 8},         // drive belts
};
export const PAINTED = new Set(['paint', 'paint2']);

// ---------------------------------------------------------------- meshes and parts
// ext + front: the outside (always drawn; two meshes for the per-mesh limits: cab, doors, bed, tailgate / front clip and
// hood); wheel: one wheel (drawn per corner and as the spare); cab + seat: the interior (drawn near); engine: the engine
// bay (drawn while the hood is open); chassis: frame, axles, running gear, radiator (drawn near).
export const MESHES = {
  ext: {id: 'pickup_t15', bones: ['body', 'door_l', 'door_r', 'tailgate', 'fuel_door'], atlas: 'ext'},
  front: {id: 'pickup_t15_front', bones: ['body', 'hood'], atlas: 'ext'},
  wheel: {id: 'pickup_t15_wheel', bones: ['wheel'], atlas: 'ext'},
  cab: {id: 'pickup_t15_cab', bones: ['body', 'door_l', 'door_r', 'steering', 'shift', 'transfer', 'tach', 'speedo', 'fuel_gauge', 'temp_gauge'], atlas: 'cab'},
  seat: {id: 'pickup_t15_seat', bones: ['body'], atlas: 'cab'},
  engine: {id: 'pickup_t15_engine', bones: ['body'], atlas: 'under'},
  chassis: {id: 'pickup_t15_chassis', bones: ['body'], atlas: 'under'},
};
export const ATLASES = {ext: {size: 2048, file: 'pickup_t15'}, cab: {size: 2048, file: 'pickup_t15_cab'}, under: {size: 1024, file: 'pickup_t15_under'}};
const REG = new Map();
/** The part of a mesh for a bone and material (created on first use). */
// tag: an extra part name suffix (the same material), so the game can switch one side's lamps on their own
// (bone_mat_tag, e.g. body_lamp_park_l: the turn signals, docs/vehicles/first_pickup_v0_design.md 8.12)
export function P(mesh, bone, mat, tag) {
  if (!MESHES[mesh]) throw new Error('unknown mesh ' + mesh);
  if (!MESHES[mesh].bones.includes(bone)) throw new Error(`bone ${bone} not in mesh ${mesh}`);
  if (!MATS[mat]) throw new Error('unknown material ' + mat);
  const k = mesh + '|' + bone + '|' + mat + (tag ? '|' + tag : '');
  if (!REG.has(k)) { const p = new Part(bone + '_' + mat + (tag ? '_' + tag : ''), bone, mat); p.mesh = mesh; REG.set(k, p); }
  return REG.get(k);
}
export const allParts = () => [...REG.values()].filter(p => p.f.length);
/** Calls f(1) and f(-1): right then left side; z values are multiplied by s. */
export const sym = f => { f(1); f(-1); };
