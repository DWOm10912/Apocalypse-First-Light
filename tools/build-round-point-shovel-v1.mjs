// AFL round point shovel (Earthwork A2, docs/worldgen/earthwork_a2.md 4): the Pure Mesh item and the four heaps of earth
// its blade carries, by the Material Meshes bake (tools/build-material-meshes-v1.mjs: one bone, LabPBR atlas Base Color /
// _s / _n, the inventory icon rendered from the textured mesh).
//   node tools/build-round-point-shovel-v1.mjs            -> writes source, atlas, geo, AFL mesh sidecar, icon, item model
//   node tools/build-round-point-shovel-v1.mjs --check    -> verifies every output is up to date
// Built at real size, 1 px = 1/16 m (the player prop's scale 1): 1.5 m over all, the blade 0.34 m across and 0.42 m long
// (a large contractor's round point blade: user 2026-10-21, the No. 2 size read as a garden tool), its lift 25 degrees off
// the handle's line, a 40 mm ash handle. Frame: the handle up +Y on
// x = 0, z = SOCKET_Z; the blade's top edge centred on the origin, its tip down at -Y bent to +Z; the load face (the
// dished front) faces +Z and a little up. The handle ends in a D grip in the x-y plane (user 2026-10-22: it hangs from
// one peg of the wooden tool rack by it). The parts named load_* are the heaps (client/ShovelItemRenderer draws the one
// the load needs, tinted by its material); they are left out of the icon.
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {Part, add, sub, mul, dot, cross, norm} from './cube-slab-mesh-lib.mjs';
import {bake} from './build-material-meshes-v1.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const r4 = v => +v.toFixed(4) || 0;

// ---------------------------------------------------------------- dimensions (px, 1 px = 62.5 mm)
const LIFT = 25 * Math.PI / 180;
const D = [0, -Math.cos(LIFT), Math.sin(LIFT)], N = [0, Math.sin(LIFT), Math.cos(LIFT)], X = [1, 0, 0];   // down the blade, its load face, across
const BL = 6.72, BW = 5.44, TH = 0.1;                  // blade length (top edge to tip), width, steel (6.25 mm: drawn thicker than the 2 mm sheet)
const SOCKET_Z = -0.6, HANDLE_R = 0.32;                // the handle's line behind the blade's top edge; 40 mm handle
const TIP_Y = -BL * Math.cos(LIFT), TOP_Y = 24 + TIP_Y;     // 1.5 m from the tip to the handle's end
const S_ROWS = [0, 0.06, 0.14, 0.24, 0.34, 0.42, 0.5, 0.58, 0.66, 0.74, 0.81, 0.87, 0.92, 0.96, 1];
const T_COLS = [-1, -0.8, -0.6, -0.4, -0.2, 0, 0.2, 0.4, 0.6, 0.8, 1];
const STEP_FROM = 0.4;                                 // the turned steps: |t| >= this along the top edge

/** Half width at s (0 top edge .. 1 tip): straight sides, then the round point. */
function halfWidth(s) {
  if (s <= 0.42) return BW / 2 * (1 - 0.03 * s / 0.42);
  const u = (s - 0.42) / 0.58;
  return BW / 2 * 0.97 * Math.pow(Math.max(0, 1 - Math.pow(u, 1.8)), 0.62);
}
/** The dish (along N): the sides raised, the middle a little hollow along the blade. */
const dish = (t, s) => 0.42 * t * t * (1 - 0.5 * s) - 0.2 * Math.sin(Math.PI * s) * (1 - t * t);
const front = (t, s) => add(add(mul(D, s * BL), mul(X, t * halfWidth(s))), mul(N, dish(t, s)));
const back = (t, s) => sub(front(t, s), mul(N, TH));

// ---------------------------------------------------------------- parts

/** Lathe along +Y about (0, z0): rings [y, r] (r 0: a point on the axis), seg sides. */
function latheY(part, z0, rings, seg, {phase = Math.PI / seg, mat = part.mat, capBottom = true} = {}) {
  const ids = rings.map(([y, r]) => r < 1e-6 ? [part.vtx([0, y, z0])] : Array.from({length: seg}, (_, i) => {
    const a = phase + 2 * Math.PI * i / seg; return part.vtx([r * Math.cos(a), y, z0 + r * Math.sin(a)]);
  }));
  for (let k = 0; k + 1 < rings.length; k++) {
    const A = ids[k], B = ids[k + 1];
    for (let i = 0; i < seg; i++) {
      const j = (i + 1) % seg, a = phase + 2 * Math.PI * (i + 0.5) / seg, out = [Math.cos(a), 0, Math.sin(a)];
      const dr = rings[k + 1][1] - rings[k][1], dy = rings[k + 1][0] - rings[k][0], hint = norm(add(mul(out, dy), [0, -dr, 0]));
      if (A.length === 1) part.face([A[0], B[j], B[i]], hint, 'side', mat);
      else if (B.length === 1) part.face([A[i], A[j], B[0]], hint, 'side', mat);
      else part.face([A[i], A[j], B[j], B[i]], hint, 'side', mat);
    }
  }
  if (capBottom && ids[0].length > 1) { const c = part.vtx([0, rings[0][0], z0]); for (let i = 0; i < seg; i++) part.face([c, ids[0][(i + 1) % seg], ids[0][i]], [0, -1, 0], 'cap', mat); }
  return ids;
}

/** The blade: the dished front, the back, the edge band all round (no top edge under the steps), worn bright at the point. */
function blade() {
  const p = new Part('blade', 'round_point_shovel', 'coat');
  const nr = S_ROWS.length, nc = T_COLS.length, F = [], B = [];
  for (let i = 0; i < nr; i++) {
    if (i === nr - 1) { F.push(Array(nc).fill(p.vtx(front(0, 1)))); B.push(Array(nc).fill(p.vtx(back(0, 1)))); continue; }
    F.push(T_COLS.map(t => p.vtx(front(t, S_ROWS[i]))));
    B.push(T_COLS.map(t => p.vtx(back(t, S_ROWS[i]))));
  }
  const worn = (i, j) => (S_ROWS[i] + S_ROWS[i + 1]) / 2 > 0.45 && (j === 0 || j === nc - 2 || i >= nr - 3);
  for (let i = 0; i + 1 < nr; i++) for (let j = 0; j + 1 < nc; j++) {
    const m = worn(i, j) ? 'steel' : 'coat';
    const f = i + 1 === nr - 1 ? [F[i][j], F[i][j + 1], F[i + 1][0]] : [F[i][j], F[i][j + 1], F[i + 1][j + 1], F[i + 1][j]];
    const b = i + 1 === nr - 1 ? [B[i][j], B[i][j + 1], B[i + 1][0]] : [B[i][j], B[i][j + 1], B[i + 1][j + 1], B[i + 1][j]];
    p.face(f, N, 'side', m);
    p.face(b, mul(N, -1), 'side', m);
  }
  // the edge band down both sides and round the point
  for (const j of [0, nc - 1]) for (let i = 0; i + 1 < nr; i++) {
    const s = (S_ROWS[i] + S_ROWS[i + 1]) / 2, out = sub(front(T_COLS[j], s), mul(D, s * BL));
    const ids = i + 1 === nr - 1 ? [F[i][j], F[i + 1][0], B[i + 1][0], B[i][j]] : [F[i][j], F[i + 1][j], B[i + 1][j], B[i][j]];
    p.face(ids, add(out, mul(D, s > 0.42 ? 0.6 : 0)), 'side', s > 0.45 ? 'steel' : 'coat');
  }
  // the top edge between the steps
  for (let j = 0; j + 1 < nc; j++) {
    if (Math.abs(T_COLS[j]) >= STEP_FROM - 1e-9 && Math.abs(T_COLS[j + 1]) >= STEP_FROM - 1e-9) continue;
    p.face([F[0][j], F[0][j + 1], B[0][j + 1], B[0][j]], mul(D, -1), 'side', 'coat');
  }
  return p;
}

/** A turned step on one side of the top edge: a flat lip folded back from the blade for the boot, 0.75 px deep. */
function step(side) {
  const p = new Part('step_' + (side < 0 ? 'r' : 'l'), 'round_point_shovel', 'coat');
  const cols = T_COLS.filter(t => t * side >= STEP_FROM - 1e-9).sort((a, b) => a - b);
  const DEPTH = TH + 0.66, RISE = 0.1, R = [];
  for (const t of cols) {
    const a = front(t, 0), c = sub(a, mul(N, DEPTH));
    R.push([a, sub(a, mul(D, RISE)), sub(c, mul(D, RISE)), c].map(q => p.vtx(q)));   // front low, front high, back high, back low
  }
  for (let k = 0; k + 1 < R.length; k++) {
    const A = R[k], Bq = R[k + 1];
    p.face([A[1], Bq[1], Bq[2], A[2]], mul(D, -1));      // top
    p.face([A[0], Bq[0], Bq[3], A[3]], D);               // under
    p.face([A[2], Bq[2], Bq[3], A[3]], mul(N, -1));      // back lip
    p.face([A[0], Bq[0], Bq[1], A[1]], N);               // front lip
  }
  for (const [Q, dir] of [[R[0], -1], [R[R.length - 1], 1]]) p.face([Q[0], Q[1], Q[2], Q[3]], [dir, 0, 0]);
  return p;
}

/** The frog: the ridge of the closed back running down from the socket, tapering out at 0.45 of the blade. */
function frog() {
  const p = new Part('frog', 'round_point_shovel', 'coat'), END = 0.45, rows = [0.015, 0.08, 0.16, 0.25, 0.34, END], R = [];
  for (const s of rows) {
    const f = 1 - s / END, w = 0.06 + 0.5 * Math.pow(f, 0.7), h = 0.78 * f, c = add(back(0, s), mul(N, 0.03));
    if (f <= 1e-6) { R.push([p.vtx(c)]); continue; }
    R.push([sub(c, mul(X, w)), sub(sub(c, mul(X, w * 0.45)), mul(N, h)), sub(add(c, mul(X, w * 0.45)), mul(N, h)), add(c, mul(X, w))].map(q => p.vtx(q)));
  }
  for (let k = 0; k + 1 < R.length; k++) for (let e = 0; e < 3; e++) {
    const A = R[k], Bq = R[k + 1], hint = e === 0 ? sub(mul(X, -1), N) : e === 1 ? mul(N, -1) : sub(X, N);
    if (Bq.length === 1) p.face([A[e], A[e + 1], Bq[0]], hint);
    else p.face([A[e], A[e + 1], Bq[e + 1], Bq[e]], hint);
  }
  p.face([R[0][0], R[0][1], R[0][2], R[0][3]], mul(D, -1), 'cap');
  return p;
}

/** The socket round the handle's foot, a rivet through it, and the ash handle with its rounded end. */
function socket() {
  const p = new Part('socket', 'round_point_shovel', 'coat');
  const top = latheY(p, SOCKET_Z, [[-0.6, 0.52], [0, 0.52], [1.2, 0.48], [2.6, 0.44], [3.6, 0.41]], 12)[4];
  const inner = latheY(new Part('x', 'x', 'coat'), SOCKET_Z, [[3.6, HANDLE_R + 0.01]], 12, {capBottom: false});   // ids only in the scratch part
  const lip = Array.from({length: 12}, (_, i) => { const a = Math.PI / 12 + 2 * Math.PI * i / 12; return p.vtx([(HANDLE_R + 0.01) * Math.cos(a), 3.6, SOCKET_Z + (HANDLE_R + 0.01) * Math.sin(a)]); });
  void inner;
  for (let i = 0; i < 12; i++) { const j = (i + 1) % 12; p.face([top[i], top[j], lip[j], lip[i]], [0, 1, 0], 'cap'); }
  return p;
}
function rivet() {
  const p = new Part('rivet', 'round_point_shovel', 'steel'), y = 1.9, r = 0.46, z0 = SOCKET_Z + r - 0.03, seg = 8;
  const ring = (dz, rr) => Array.from({length: seg}, (_, i) => { const a = 2 * Math.PI * i / seg; return p.vtx([rr * Math.cos(a), y + rr * Math.sin(a), z0 + dz]); });
  const a = ring(0, 0.13), b = ring(0.07, 0.11), apex = p.vtx([0, y, z0 + 0.11]);
  for (let i = 0; i < seg; i++) { const j = (i + 1) % seg; p.face([a[i], a[j], b[j], b[i]], [Math.cos(2 * Math.PI * (i + 0.5) / seg), Math.sin(2 * Math.PI * (i + 0.5) / seg), 0.4]); p.face([b[i], b[j], apex], [0, 0, 1]); }
  return p;
}
function handle() {
  const p = new Part('handle', 'round_point_shovel', 'ash');
  const rings = [[3.4, HANDLE_R]];
  for (let y = 6; y < D_SPLIT - 0.5; y += 4) rings.push([y, HANDLE_R]);
  rings.push([D_SPLIT + 0.1, HANDLE_R]);
  latheY(p, SOCKET_Z, rings, 12);
  return p;
}

// ---------------------------------------------------------------- the D grip (user 2026-10-22: a ring grip, so the shovel
// hangs from one peg of the tool rack by it). The shaft ends in a steel ferrule; two split ash arms run up out of it into
// a crossbar the hand closes round, the D in the blade's plane (x, y) on the handle's line. The crossbar's middle is where
// the straight handle's top hand was (TOP_Y - 0.9: grip_end unchanged, so the A2 strokes' two hands keep their spacing),
// so the shovel ends 4 cm short of TOP_Y, 1.48 m over all; hang is the crossbar's underside (the tool rack's peg).
const D_BAR_R = 0.26, D_ARM_R = 0.2, D_INNER = 0.92, D_CORNER = 0.35;   // 32 mm bar, 25 mm arms, 115 mm across inside
const D_BAR_Y = TOP_Y - 0.9, D_SPLIT = D_BAR_Y - 2.75, D_SIDE = D_INNER + D_ARM_R;

/** A round tube swept along a path in the plane z = z0 (points [x, y, r]), its ends capped. */
function sweepXY(part, z0, pts, seg) {
  const rings = pts.map((q, i) => {
    const a = pts[Math.max(0, i - 1)], b = pts[Math.min(pts.length - 1, i + 1)], t = norm([b[0] - a[0], b[1] - a[1], 0]), n = [-t[1], t[0], 0];
    return Array.from({length: seg}, (_, k) => { const w = 2 * Math.PI * k / seg; return part.vtx(add([q[0], q[1], z0], add(mul(n, q[2] * Math.cos(w)), [0, 0, q[2] * Math.sin(w)]))); });
  });
  for (let i = 0; i + 1 < rings.length; i++) for (let k = 0; k < seg; k++) {
    const j = (k + 1) % seg, A = part.v[rings[i][k]], B = part.v[rings[i + 1][j]], m = [(pts[i][0] + pts[i + 1][0]) / 2, (pts[i][1] + pts[i + 1][1]) / 2, z0];
    part.face([rings[i][k], rings[i][j], rings[i + 1][j], rings[i + 1][k]], sub(mul(add(A, B), 0.5), m));
  }
  for (const [ring, q, i2] of [[rings[0], pts[0], 1], [rings[rings.length - 1], pts[pts.length - 1], pts.length - 2]])
    part.face(ring, [q[0] - pts[i2][0], q[1] - pts[i2][1], 0], 'cap');
}
function dGrip() {
  const p = new Part('d_grip', 'round_point_shovel', 'ash'), left = [];
  const bez = (P0, P1, P2, P3, t) => [0, 1].map(k => (1 - t) ** 3 * P0[k] + 3 * (1 - t) ** 2 * t * P1[k] + 3 * (1 - t) * t * t * P2[k] + t ** 3 * P3[k]);
  // one arm (+x) up out of the ferrule, round its corner into the bar's end; the other is its mirror
  for (let i = 0; i <= 8; i++) left.push([...bez([0.13, D_SPLIT], [0.13, D_SPLIT + 0.9], [D_SIDE, D_BAR_Y - D_CORNER - 1.0], [D_SIDE, D_BAR_Y - D_CORNER], i / 8), D_ARM_R]);
  for (let i = 1; i <= 4; i++) {
    const a = Math.PI / 2 * i / 4;
    left.push([D_SIDE - D_CORNER + D_CORNER * Math.cos(a), D_BAR_Y - D_CORNER + D_CORNER * Math.sin(a), D_ARM_R + (D_BAR_R - D_ARM_R) * i / 4]);
  }
  const pts = [...left.map(q => [-q[0], q[1], q[2]]), ...left.slice().reverse()];
  sweepXY(p, SOCKET_Z, pts, 10);
  return p;
}
/** The steel ferrule clamping the split. */
function ferrule() {
  const p = new Part('ferrule', 'round_point_shovel', 'coat');
  latheY(p, SOCKET_Z, [[D_SPLIT - 0.8, HANDLE_R + 0.03], [D_SPLIT - 0.75, HANDLE_R + 0.06], [D_SPLIT + 0.2, HANDLE_R + 0.06], [D_SPLIT + 0.3, HANDLE_R + 0.01]], 12);
  return p;
}

/** Heap k (1..4) of loose earth on the blade's front: a lumpy mound, its rim sunk into the steel. */
const HEAPS = [null, {h: 1.0, w: 0.62, s: [0.18, 0.78]}, {h: 1.9, w: 0.74, s: [0.12, 0.84]}, {h: 2.8, w: 0.84, s: [0.08, 0.88]}, {h: 3.7, w: 0.9, s: [0.05, 0.9]}];
function heap(k) {
  const {h, w, s: [s0, s1]} = HEAPS[k], p = new Part('load_' + k, 'round_point_shovel', 'soil'), n = 10, G = [];
  const bell = x => Math.pow(Math.max(0, 1 - x * x), 1.3);
  for (let i = 0; i <= n; i++) {
    const row = [], sn = -1 + 2 * i / n, s = s0 + (s1 - s0) * i / n;
    for (let j = 0; j <= n; j++) {
      const tn = -1 + 2 * j / n, b = bell(tn) * bell(sn);
      const lump = 1 + 0.13 * Math.sin(5.1 * tn + 7.3 * sn + k) * Math.sin(4.3 * sn - 2.1 * tn + 1.1 + k);
      row.push(p.vtx(add(front(tn * w, s), mul(N, h * b * lump - 0.025))));
    }
    G.push(row);
  }
  for (let i = 0; i < n; i++) for (let j = 0; j < n; j++) p.face([G[i][j], G[i][j + 1], G[i + 1][j + 1], G[i + 1][j]], N);
  return p;
}

// ---------------------------------------------------------------- held transforms

/** M = Rx Ry Rz (Minecraft ItemTransform rotation, the bake's R3) as rows, and back to degrees. */
const rot3 = ([ax, ay, az]) => {
  const [a, b, c] = [ax, ay, az].map(v => v * Math.PI / 180);
  const Rx = [[1, 0, 0], [0, Math.cos(a), -Math.sin(a)], [0, Math.sin(a), Math.cos(a)]];
  const Ry = [[Math.cos(b), 0, Math.sin(b)], [0, 1, 0], [-Math.sin(b), 0, Math.cos(b)]];
  const Rz = [[Math.cos(c), -Math.sin(c), 0], [Math.sin(c), Math.cos(c), 0], [0, 0, 1]];
  const mm = (P, Q) => P.map(r => [0, 1, 2].map(j => r[0] * Q[0][j] + r[1] * Q[1][j] + r[2] * Q[2][j]));
  return mm(mm(Rx, Ry), Rz);
};
const mmul = (P, Q) => P.map(r => [0, 1, 2].map(j => r[0] * Q[0][j] + r[1] * Q[1][j] + r[2] * Q[2][j]));
const apply = (M, v) => M.map(r => r[0] * v[0] + r[1] * v[1] + r[2] * v[2]);
const euler = M => [Math.atan2(-M[1][2], M[2][2]), Math.asin(Math.max(-1, Math.min(1, M[0][2]))), Math.atan2(-M[0][1], M[0][0])].map(v => r4(v * 180 / Math.PI));
/**
 * docs/models/equipment_meshes_v1.md "手持适配规则": the wiring wrench's turn (its mesh along +X, the tip out) with the
 * shovel first turned so its blade (-Y) is that tip (+X: a turn of 90 degrees about Z), the hand on the handle's middle;
 * translation = the wrench grip's place in the hand - scale x turn x the shovel's own grip (px, centred as drawn).
 */
function held(grip, size) {
  const toX = rot3([0, 0, 90]), c = [grip[0], grip[1] - size[1] / 2, grip[2]], out = {};
  for (const [view, wrench, at, scale] of [['thirdperson_righthand', [0, -90, -80], [0, -0.9, 1.37], 1.0], ['firstperson_righthand', [0, -90, -110], [1.13, -0.46, 2.46], 0.85]]) {
    const M = mmul(rot3(wrench), toX), q = apply(M, c);
    out[view] = {rotation: euler(M), translation: at.map((v, i) => r4(v - scale * q[i])), scale: [scale, scale, scale]};
    out['_' + view] = M;
  }
  return out;
}

export function shovel() {
  const PARTS = [blade(), step(-1), step(1), frog(), socket(), rivet(), handle(), ferrule(), dGrip(), heap(1), heap(2), heap(3), heap(4)];
  const points = {
    grip_end: [0, D_BAR_Y, SOCKET_Z], hang: [0, D_BAR_Y - D_BAR_R, SOCKET_Z], grip: [0, 10.6, SOCKET_Z], grip_low: [0, 5.6, SOCKET_Z],
    heel: front(0, 0), blade: front(0, 0.5), tip: front(0, 1), step_r: sub(sub(front(-0.75, 0), mul(N, 0.4)), mul(D, 0.1)),
  };
  return {PARTS, MATS: {
    coat: {c: [44, 46, 48], hl: 12, sm: 120, se: 150, f0: 22},     // black powder coat on the pressed steel
    steel: {c: [118, 120, 122], hl: 14, sm: 110, se: 130, f0: 230},  // the cutting edge, worn bright
    ash: {c: [196, 166, 122], hl: 6, sm: 96, se: 110, f0: 20},       // clear-lacquered ash
    soil: {c: [188, 182, 172], hl: 3, sm: 26, se: 30, f0: 20},       // the heap: neutral, tinted by its material in the renderer
  }, bg: 'coat', atlas: 512, points,
  icon: {view: [0, 0, -45], hide: ['load_1', 'load_2', 'load_3', 'load_4']}};
}

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  const check = process.argv.includes('--check'), id = 'round_point_shovel';
  const spec = shovel();
  const all = spec.PARTS.flatMap(p => p.v), lo = [0, 1, 2].map(k => Math.min(...all.map(q => q[k]))), hi = [0, 1, 2].map(k => Math.max(...all.map(q => q[k])));
  const shift = [-(lo[0] + hi[0]) / 2, -lo[1], -(lo[2] + hi[2]) / 2], size = sub(hi, lo);
  const baked = Object.fromEntries(Object.entries(spec.points).map(([k, q]) => [k, add(q, shift)]));
  const hand = held(baked.grip, size);
  spec.display = {thirdperson_righthand: hand.thirdperson_righthand, firstperson_righthand: hand.firstperson_righthand};
  const {outputs, stats} = bake(id, spec);
  // the prop's points: item block units as the renderer draws the mesh (px / 16 + (0.5, verticalOffset, 0.5))
  stats.points = Object.fromEntries(Object.entries(baked).map(([k, q]) => [k, [r4(q[0] / 16 + 0.5), r4(q[1] / 16 + stats.verticalOffset), r4(q[2] / 16 + 0.5)]]));
  stats.bounds = [lo.map((v, k) => r4((v + shift[k]) / 16 + (k === 1 ? stats.verticalOffset : 0.5))), hi.map((v, k) => r4((v + shift[k]) / 16 + (k === 1 ? stats.verticalOffset : 0.5)))];
  // offline hand check: where the tip and the handle's end land, px from the fist's grip point
  const c = q => [q[0], q[1] - size[1] / 2, q[2]];
  stats.hands = Object.fromEntries(['thirdperson_righthand', 'firstperson_righthand'].map(v => {
    const M = hand['_' + v], s = hand[v].scale[0], at = q => apply(M, c(q)).map((x, i) => r4(s * x + hand[v].translation[i]));
    return [v, {grip: at(baked.grip), tip: at(baked.tip), end: at(add(spec.points.grip_end, shift))}];
  }));
  stats.length = r4(Math.hypot(...sub(spec.points.tip, [0, D_BAR_Y + D_BAR_R, SOCKET_Z])) / 16);   // tip to the top of the D
  console.log(JSON.stringify(stats, null, 1));
  let stale = 0;
  for (const [file, data] of outputs) {
    const buf = Buffer.isBuffer(data) ? data : Buffer.from(data);
    if (check) { if (!fs.existsSync(file) || !fs.readFileSync(file).equals(buf)) { console.log('stale ' + path.relative(ROOT, file)); stale++; } }
    else { fs.mkdirSync(path.dirname(file), {recursive: true}); fs.writeFileSync(file, buf); }
  }
  if (check) { if (stale) process.exit(1); console.log('CHECK OK'); } else console.log('written');
}
