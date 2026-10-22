// AFL pick mattock (Rock R1a, docs/gameplay/rock_r1_stones_boulders_design.md 4): the Pure Mesh item by the Material Meshes
// bake (tools/build-material-meshes-v1.mjs: one bone, LabPBR atlas Base Color / _s / _n, the inventory icon rendered from
// the textured mesh).
//   node tools/build-pick-mattock-v1.mjs            -> writes source, atlas, geo, AFL mesh sidecar, icon, item model
//   node tools/build-pick-mattock-v1.mjs --check    -> verifies every output is up to date
// Built at real size, 1 px = 1/16 m (the player prop's scale 1): a 5 lb forged head 0.46 m from the pick's point to the
// mattock's edge, the edge 76 mm wide, on a 36 in (0.91 m) hickory handle that swells into the eye. Frame: the handle up +Y
// on x = 0, z = 0 (its knob at the bottom), the head across the top: the pick point to +Z, the mattock blade to -Z, both
// arms curving down a little towards the hands as a real head does; the blade's flat faces up and down, its edge along X.
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {Part, add, sub, mul, norm, cross} from './cube-slab-mesh-lib.mjs';
import {bake} from './build-material-meshes-v1.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const r4 = v => +v.toFixed(4) || 0;
const MM = 1 / 62.5;                                    // px per mm

// ---------------------------------------------------------------- dimensions (px)
const HANDLE = 910 * MM;                                // 36 in
const EYE_Y = HANDLE - 52 * MM;                         // the eye's middle on the handle: the head sits just under the swelled end
const PICK = 232 * MM, ADZE = 228 * MM;                 // eye centre to the point, to the edge (0.46 m over all)
const EYE_H = 92 * MM, EYE_W = 60 * MM, EYE_L = 66 * MM; // the eye boss: along the handle, across, along the head
const ARM_W = 34 * MM, ARM_H = 40 * MM;                 // both arms where they leave the boss (forged square, corners broken)
const EDGE_W = 76 * MM, EDGE_T = 2.4 * MM;              // the mattock's edge
const DROP_PICK = 30 * MM, DROP_ADZE = 26 * MM;         // how far each end curves down towards the hands

/** The head's centre line at z (+ pick, - mattock): y drops with the square of the run past the boss. */
function line(z) {
  const run = Math.max(0, Math.abs(z) - EYE_L / 2), len = (z >= 0 ? PICK : ADZE) - EYE_L / 2, drop = z >= 0 ? DROP_PICK : DROP_ADZE;
  return [0, EYE_Y - drop * Math.pow(run / len, 2), z];
}

/**
 * The head's section at z: [half width (x), half height (along the handle), chamfer]. The boss is tall; the pick narrows
 * to its point (square, the last 30 mm ground to a cone-like point); the mattock flattens and widens to its edge.
 */
function section(z) {
  const bossEnd = EYE_L / 2;
  if (Math.abs(z) <= bossEnd) {
    const u = Math.abs(z) / bossEnd, round = Math.sqrt(Math.max(0, 1 - Math.pow(u, 6)));
    return [ARM_W / 2 + (EYE_W / 2 - ARM_W / 2) * round, ARM_H / 2 + (EYE_H / 2 - ARM_H / 2) * round, 4 * MM];
  }
  if (z > 0) {
    const s = (z - bossEnd) / (PICK - bossEnd), taper = Math.pow(1 - s, 0.85);
    return [Math.max(0.02, ARM_W / 2 * taper), Math.max(0.02, ARM_H / 2 * taper), Math.max(0, 3 * MM * taper)];
  }
  const s = (-z - bossEnd) / (ADZE - bossEnd), flare = s < 0.25 ? 0 : Math.pow((s - 0.25) / 0.75, 1.4);
  return [ARM_W / 2 + (EDGE_W / 2 - ARM_W / 2) * flare, ARM_H / 2 + (EDGE_T / 2 - ARM_H / 2) * Math.pow(s, 0.8), (3 * MM) * (1 - s)];
}

/** An octagon (rectangle with broken corners) about a centre, in the plane of the head's side (x) and up (u). */
function ring(c, up, hw, hh, ch) {
  const x = [1, 0, 0], k = Math.min(Math.max(ch, 0.25 * Math.min(hw, hh)), hw * 0.45, hh * 0.45);   // never a zero-length corner
  const P = [[hw, hh - k], [hw - k, hh], [-hw + k, hh], [-hw, hh - k], [-hw, -hh + k], [-hw + k, -hh], [hw - k, -hh], [hw, -hh + k]];
  return P.map(([a, b]) => add(add(c, mul(x, a)), mul(up, b)));
}

/** Rings along the head from the mattock's edge to the pick's point, joined; ground bright where it cuts. */
function head() {
  const p = new Part('head', 'pick_mattock', 'scale');
  const zs = [];
  for (let i = 0; i <= 14; i++) zs.push(-ADZE + (ADZE - EYE_L / 2) * i / 14);
  for (let i = 1; i < 10; i++) zs.push(-EYE_L / 2 + EYE_L * i / 10);
  for (let i = 0; i <= 18; i++) zs.push(EYE_L / 2 + (PICK - EYE_L / 2) * (1 - Math.pow(1 - i / 18, 1.3)));
  const R = zs.map(z => {
    const c = line(z), t = norm(sub(line(z + 0.05), line(z - 0.05))), up = norm(cross(t, [1, 0, 0]));
    const [hw, hh, ch] = section(z);
    return {z, c, t, ids: z >= PICK - 1e-9 ? null : ring(c, up[1] < 0 ? mul(up, -1) : up, hw, hh, ch).map(q => p.vtx(q))};
  });
  const tip = p.vtx(line(PICK));
  const bright = z => z > PICK - 70 * MM || z < -ADZE + 36 * MM;
  for (let k = 0; k + 1 < R.length; k++) {
    const A = R[k].ids, B = R[k + 1].ids, mid = (R[k].z + R[k + 1].z) / 2, m = bright(mid) ? 'ground' : 'scale';
    for (let i = 0; i < 8; i++) {
      const j = (i + 1) % 8, out = norm(sub(add(p.v[A[i]], p.v[A[j]]), mul(R[k].c, 2)));
      if (!B) p.face([A[i], A[j], tip], out, 'side', m);
      else p.face([A[i], A[j], B[j], B[i]], out, 'side', m);
    }
  }
  // the mattock's edge: the end ring closed with a ground strip
  const E = R[0].ids;
  p.face([E[0], E[1], E[2], E[3]], [0, 0, -1], 'cap', 'ground');
  p.face([E[3], E[4], E[5], E[6]], [0, 0, -1], 'cap', 'ground');
  p.face([E[6], E[7], E[0], E[3]], [0, 0, -1], 'cap', 'ground');
  return p;
}

/** Lathe-like loft along +Y of ellipses [y, rx, rz] about the handle's axis (seg sides). */
function loftY(part, rings, seg, mat, {capTop = true, capBottom = true} = {}) {
  const ids = rings.map(([y, rx, rz]) => Array.from({length: seg}, (_, i) => {
    const a = Math.PI / seg + 2 * Math.PI * i / seg; return part.vtx([rx * Math.cos(a), y, rz * Math.sin(a)]);
  }));
  for (let k = 0; k + 1 < rings.length; k++) {
    const m = typeof mat === 'function' ? mat((rings[k][0] + rings[k + 1][0]) / 2) : mat;
    for (let i = 0; i < seg; i++) {
      const j = (i + 1) % seg, a = Math.PI / seg + 2 * Math.PI * (i + 0.5) / seg;
      const dr = (rings[k + 1][1] - rings[k][1]), dy = rings[k + 1][0] - rings[k][0];
      part.face([ids[k][i], ids[k][j], ids[k + 1][j], ids[k + 1][i]], norm([Math.cos(a) * dy, -dr, Math.sin(a) * dy]), 'side', m);
    }
  }
  const cap = (row, y, dir) => { const c = part.vtx([0, y, 0]); for (let i = 0; i < seg; i++) part.face([c, row[(i + 1) % seg], row[i]], [0, dir, 0], 'cap', typeof mat === 'function' ? mat(y) : mat); };
  if (capBottom) cap(ids[0], rings[0][0], -1);
  if (capTop) cap(ids[ids.length - 1], rings[rings.length - 1][0], 1);
  return ids;
}

/** The hickory handle: an oval grip with a knob at the end, swelling into the eye; worn where the hands slide. */
function handle() {
  const p = new Part('handle', 'pick_mattock', 'hickory');
  const rings = [[0, 15.5 * MM, 12 * MM], [6 * MM, 18.5 * MM, 14.5 * MM], [22 * MM, 19.5 * MM, 15 * MM], [40 * MM, 17.5 * MM, 13.5 * MM]];
  for (let y = 120; y < 700; y += 80) rings.push([y * MM, 17.5 * MM + 1.5 * MM * (y - 120) / 580, 13.5 * MM + 1.2 * MM * (y - 120) / 580]);
  rings.push([760 * MM, 20.5 * MM, 15.5 * MM], [EYE_Y - EYE_H / 2 - 4 * MM, 23 * MM, 17 * MM], [HANDLE - 6 * MM, 25 * MM, 18.5 * MM], [HANDLE, 24 * MM, 18 * MM]);
  const worn = y => (y < 220 * MM || (y > 330 * MM && y < 520 * MM)) ? 'grip' : 'hickory';
  loftY(p, rings, 14, worn);
  return p;
}

/** The steel wedge driven across the handle's end above the eye. */
function wedge() {
  const p = new Part('wedge', 'pick_mattock', 'ground');
  const y0 = HANDLE - 0.4 * MM, y1 = HANDLE + 1.6 * MM, w = 21 * MM, t = 2.2 * MM;
  const c = [[-w, -t], [w, -t], [w, t], [-w, t]].map(([z, x]) => [x, z]);
  const lo = c.map(([x, z]) => p.vtx([x, y0, z])), hi = c.map(([x, z]) => p.vtx([x * 0.8, y1, z * 0.96]));
  for (let i = 0; i < 4; i++) { const j = (i + 1) % 4; p.face([lo[i], lo[j], hi[j], hi[i]], norm([c[i][0] + c[j][0], 0, c[i][1] + c[j][1]])); }
  p.face(hi, [0, 1, 0], 'cap');
  return p;
}

// ---------------------------------------------------------------- held transforms (as tools/build-round-point-shovel-v1.mjs)

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
 * docs/models/equipment_meshes_v1.md "手持适配规则": the wiring wrench's turn (its mesh along +X, the tip out) with the pick
 * first turned so its knob (-Y) is that tip (a turn of 90 degrees about Z, as the shovel's blade) and the head stands up
 * past the fist as a held pick does, the hand on the handle's end grip;
 * translation = the wrench grip's place in the hand - scale x turn x the pick's own grip (px, centred as drawn).
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

export function pickMattock() {
  const PARTS = [head(), handle(), wedge()];
  const points = {
    grip: [0, 110 * MM, 0], grip_mid: [0, 430 * MM, 0], grip_high: [0, 650 * MM, 0],
    tip: line(PICK), edge: line(-ADZE), eye: line(0),
  };
  return {PARTS, MATS: {
    scale: {c: [52, 50, 48], hl: 14, sm: 70, se: 90, f0: 230},       // forged steel under its black scale, dull
    ground: {c: [150, 150, 148], hl: 18, sm: 150, se: 170, f0: 240},  // the point and the edge ground bright
    hickory: {c: [184, 146, 98], hl: 7, sm: 90, se: 110, f0: 20},     // oiled hickory
    grip: {c: [150, 112, 72], hl: 6, sm: 130, se: 140, f0: 22},       // the same wood darkened and polished by hands
  }, bg: 'hickory', atlas: 512, points,
  // the icon sees the head from the side (Y turned 90 degrees: the point to the right), the handle diagonal, the head top right
  icon: {view: [90, 45, -78]}};
}

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  const check = process.argv.includes('--check'), id = 'pick_mattock';
  const spec = pickMattock();
  const all = spec.PARTS.flatMap(p => p.v), lo = [0, 1, 2].map(k => Math.min(...all.map(q => q[k]))), hi = [0, 1, 2].map(k => Math.max(...all.map(q => q[k])));
  const shift = [-(lo[0] + hi[0]) / 2, -lo[1], -(lo[2] + hi[2]) / 2], size = sub(hi, lo);
  const baked = Object.fromEntries(Object.entries(spec.points).map(([k, q]) => [k, add(q, shift)]));
  const hand = held(baked.grip, size);
  spec.display = {thirdperson_righthand: hand.thirdperson_righthand, firstperson_righthand: hand.firstperson_righthand};
  const {outputs, stats} = bake(id, spec);
  // the prop's points: item block units as the renderer draws the mesh (px / 16 + (0.5, verticalOffset, 0.5))
  stats.points = Object.fromEntries(Object.entries(baked).map(([k, q]) => [k, [r4(q[0] / 16 + 0.5), r4(q[1] / 16 + stats.verticalOffset), r4(q[2] / 16 + 0.5)]]));
  stats.bounds = [lo.map((v, k) => r4((v + shift[k]) / 16 + (k === 1 ? stats.verticalOffset : 0.5))), hi.map((v, k) => r4((v + shift[k]) / 16 + (k === 1 ? stats.verticalOffset : 0.5)))];
  // offline hand check: where the point, the edge and the knob land, px from the fist's grip point
  const c = q => [q[0], q[1] - size[1] / 2, q[2]];
  stats.hands = Object.fromEntries(['thirdperson_righthand', 'firstperson_righthand'].map(v => {
    const M = hand['_' + v], s = hand[v].scale[0], at = q => apply(M, c(q)).map((x, i) => r4(s * x + hand[v].translation[i]));
    return [v, {grip: at(baked.grip), tip: at(baked.tip), edge: at(baked.edge), knob: at(add([0, 0, 0], shift))}];
  }));
  stats.head = r4((PICK + ADZE) / 16);
  console.log(JSON.stringify(stats, null, 1));
  let stale = 0;
  for (const [file, data] of outputs) {
    const buf = Buffer.isBuffer(data) ? data : Buffer.from(data);
    if (check) { if (!fs.existsSync(file) || !fs.readFileSync(file).equals(buf)) { console.log('stale ' + path.relative(ROOT, file)); stale++; } }
    else { fs.mkdirSync(path.dirname(file), {recursive: true}); fs.writeFileSync(file, buf); }
  }
  if (check) { if (stale) process.exit(1); console.log('CHECK OK'); } else console.log('written');
}
