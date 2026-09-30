// Silverwood 12 V3 first-person animations. Motion is authored as functions of time and baked at 60 Hz into linear keys
// (the Blackridge .50 method, tools/author-blackridge-50-animations.mjs). Imported by tools/build-silverwood-12-v3.mjs,
// which writes the clips into the Blockbench source and the runtime animation file together with the model.
// Handling language after the CoD Vanguard double barrel the user supplied, adapted to this hammerless boxlock: the
// extractor only lifts the shells, so the empty reload carries the gun left and tips it over (breech end down) to pour
// both spent shells out, then pushes two new shells in at once with the thumb; the tactical reload plucks the single spent shell (right barrel: it fires first) by
// hand and pushes one new shell in.
// Frame: Blockbench source (+X shooter's right, up +Y, muzzle -Z); bone rotation M = Rz * Ry * Rx (degrees); a bone's
// animated position is an offset in its parent's frame. Hand anchors: distal cap at the pivot, forearm along local -Y.
// The left hand lives under root, so while it holds the forend its track is solved from the barrels' world transform.

// ---------- math ----------
const Z = [0, 0, 0], D = Math.PI / 180, I3 = [[1, 0, 0], [0, 1, 0], [0, 0, 1]];
const add = (a, b) => a.map((v, i) => v + b[i]), sub = (a, b) => a.map((v, i) => v - b[i]), mul = (a, n) => a.map(v => v * n);
const lerp = (a, b, s) => a.map((v, i) => v + (b[i] - v) * s), norm = a => mul(a, 1 / Math.hypot(...a));
const dot = (a, b) => a.reduce((s, v, i) => s + v * b[i], 0), cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const clamp01 = u => Math.max(0, Math.min(1, u)), sum = (...v) => v.reduce((a, b) => add(a, b), Z);
function E([x, y, z]) {
  const [cx, sx, cy, sy, cz, sz] = [Math.cos(x * D), Math.sin(x * D), Math.cos(y * D), Math.sin(y * D), Math.cos(z * D), Math.sin(z * D)];
  return [[cy * cz, sx * sy * cz - cx * sz, cx * sy * cz + sx * sz], [cy * sz, sx * sy * sz + cx * cz, cx * sy * sz - sx * cz], [-sy, sx * cy, cx * cy]];
}
const mv = (m, v) => m.map(r => r[0] * v[0] + r[1] * v[1] + r[2] * v[2]);
const mm = (a, b) => a.map(r => [0, 1, 2].map(j => r[0] * b[0][j] + r[1] * b[1][j] + r[2] * b[2][j]));
const T3 = m => [0, 1, 2].map(i => [0, 1, 2].map(j => m[j][i]));
const euler = m => [Math.atan2(m[2][1], m[2][2]), Math.asin(Math.max(-1, Math.min(1, -m[2][0]))), Math.atan2(m[1][0], m[0][0])].map(v => v / D);
function quat(m) {
  const tr = m[0][0] + m[1][1] + m[2][2];
  if (tr > 0) { const S = Math.sqrt(tr + 1) * 2; return [(m[2][1] - m[1][2]) / S, (m[0][2] - m[2][0]) / S, (m[1][0] - m[0][1]) / S, S / 4]; }
  if (m[0][0] > m[1][1] && m[0][0] > m[2][2]) { const S = Math.sqrt(1 + m[0][0] - m[1][1] - m[2][2]) * 2; return [S / 4, (m[0][1] + m[1][0]) / S, (m[0][2] + m[2][0]) / S, (m[2][1] - m[1][2]) / S]; }
  if (m[1][1] > m[2][2]) { const S = Math.sqrt(1 + m[1][1] - m[0][0] - m[2][2]) * 2; return [(m[0][1] + m[1][0]) / S, S / 4, (m[1][2] + m[2][1]) / S, (m[0][2] - m[2][0]) / S]; }
  const S = Math.sqrt(1 + m[2][2] - m[0][0] - m[1][1]) * 2; return [(m[0][2] + m[2][0]) / S, (m[1][2] + m[2][1]) / S, S / 4, (m[1][0] - m[0][1]) / S];
}
const qmat = ([x, y, z, w]) => [[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)], [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
  [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]];
function slerpM(A, B, s) {   // rotation blend through quaternions (Euler lerp takes the long way round)
  const a = quat(A); let b = quat(B), d = dot(a, b);
  if (d < 0) { b = mul(b, -1); d = -d; }
  let q; if (d > 0.9995) q = lerp(a, b, s);
  else { const th = Math.acos(d), sa = Math.sin((1 - s) * th) / Math.sin(th), sb = Math.sin(s * th) / Math.sin(th); q = a.map((v, i) => v * sa + b[i] * sb); }
  return qmat(norm(q));
}
// rigid transforms {R, t}: x -> R x + t
const X = (R = I3, t = Z) => ({R, t});
const cx = (a, b) => ({R: mm(a.R, b.R), t: add(mv(a.R, b.t), a.t)});
const ix = a => { const Rt = T3(a.R); return {R: Rt, t: mul(mv(Rt, a.t), -1)}; };
const mixX = (a, b, s) => ({R: slerpM(a.R, b.R, s), t: lerp(a.t, b.t, s)});
const bone = (P, pos = Z, rot = Z) => { const R = E(rot); return {R, t: add(pos, sub(P, mv(R, P)))}; };   // pivot P, offset pos, rotation
const unbone = (P, x) => ({pos: add(sub(x.t, P), mv(x.R, P)), rot: euler(x.R)});
const shift = v => X(I3, v);
// hand orientation from the forearm direction (hand -> elbow) and the palm normal: local -Y runs along the forearm
function arm(forearm, palm) {
  const ey = mul(norm(forearm), -1), ez = norm(sub(palm, mul(ey, dot(palm, ey)))), ex = cross(ey, ez);
  return [0, 1, 2].map(r => [ex[r], ey[r], ez[r]]);
}
// ---------- motion language (as Blackridge .50) ----------
// go(): anticipation dip -> accelerating travel -> hard arrival -> damped overshoot
function go(t, t0, t1, {antic = 0.04, over = 0.07, per = 0.17, tau = 0.07, pre = 0.08} = {}) {
  if (t < t0 - pre) return 0;
  if (t < t0) return -antic * Math.sin(Math.PI * (t - t0 + pre) / pre);
  if (t < t1) { const u = (t - t0) / (t1 - t0); return 1.6 * u * u - 0.6 * u * u * u; }
  const s = t - t1; return 1 + over * Math.sin(2 * Math.PI * s / per) * Math.exp(-s / tau);
}
const ease = (t, t0, t1) => { const u = clamp01((t - t0) / (t1 - t0)); return u * u * u * (10 + u * (-15 + 6 * u)); };
// jolt(): one- to two-frame mechanical hit, ringing out quickly
function jolt(t, te, amp, {rise = 0.017, per = 0.11, tau = 0.045} = {}) {
  const s = t - te; if (s <= 0) return Z;
  const k = s < rise ? s / rise : Math.exp(-(s - rise) / tau) * Math.cos(2 * Math.PI * (s - rise) / per);
  return mul(amp, k);
}
const pose = (p, w) => ({pos: mul(p.pos, w), rot: mul(p.rot, w)});
const plus = (...ps) => ({pos: sum(...ps.map(p => p.pos || Z)), rot: sum(...ps.map(p => p.rot || Z))});

export function silverwoodClips(F) {
  // ---------- rig facts ----------
  const G = new Map(F.rig.map(([name, parent, origin, rot]) => [name, {parent, origin, rot: rot || Z}]));
  const piv = n => G.get(n).origin;
  const GRIP = piv('handling'), PIN = piv('barrels'), EP = piv('extractor'), RP = piv('righthand'), LP = piv('lefthand');
  const R0R = E(G.get('right_hand_anchor').rot), R0L = E(G.get('left_hand_anchor').rot);
  const OUT = F.OUT;                                   // chamber axis toward the breech (barrels frame, unit)
  const OPEN = -36, LEVER = 30, EXTRACT = 0.32, S_OUT = 3.45, GRAV = [0, -330, 0];
  const CH = {right: piv('chamber_right'), left: piv('chamber_left')};

  // ---------- gun ----------
  // state: h = handling {pos, rot}, open = barrel angle (deg, <= 0), ext = extractor travel, lever = top lever (deg)
  const Wh = s => bone(GRIP, s.h.pos, s.h.rot);
  const Wb = s => cx(Wh(s), bone(PIN, Z, [s.open, 0, 0]));
  const We = s => cx(Wb(s), shift(mul(OUT, s.ext)));
  const extOf = open => EXTRACT * clamp01(open / OPEN) ** 2;   // the extractor cam lifts with the last part of the opening
  const leftOnForend = s => cx(Wb(s), X(R0L, LP));
  const rightOnGrip = s => cx(Wh(s), X(R0R, RP));
  const inChamber = (s, out = 0) => cx(We(s), shift(mul(OUT, out)));   // shell / pair frame: chamber, pulled out by `out`
  // wrist: the hand stays on its grip point but its forearm keeps (share k of) its idle direction in the root frame, so a
  // gun turning in the hands does not swing the rigid arm box across the view
  const wrist = (A, R0, k) => ({R: slerpM(A.R, R0, k), t: A.t});

  // ---------- design poses (handling about the grip) ----------
  const IDLE = {pos: Z, rot: Z};
  const LOAD = {pos: [-1.6, 1.4, 8.0], rot: [14, -16, 26]};        // breech raised toward the screen centre, chambers to the eye
  // empty reload dump: the gun is carried left, turned so the barrels lie across the view toward the left (yaw 45), then
  // tipped over about the view axis so the breech end drops (roll -38) and the spent shells slide out and fall
  const DUMP_SHIFT = [-3.6, -1.2, 0.4], DUMP_TIP = [10, 45, -38];   // root-frame translation / root-frame rotation (deg)
  const LOW = {pos: [3.0, -9.5, 3], rot: [-40, 12, 30]};            // just below the frame, right (draw / put away)
  // inspect: the camera sits above, behind and left of the gun, so the sides are shown by rolling the gun about its own
  // length (right side rolled up to the eye, then the left side), small enough that both arms stay in the frame
  const INS_A = {pos: [-1.2, 2.2, -1.0], rot: [6, 8, 45]};          // right side rolled up toward the eye
  const INS_B = {pos: [-0.6, 1.6, -1.2], rot: [4, 14, -30]};         // rolled the other way: left side and top rib
  const OFF_R = [9, -15, 9], OFF_L = [-8, -12, 9];                  // off-screen hand positions (root frame)
  const LOW_LEFT = X(mm(E([-35, 10, -15]), R0L), OFF_L);
  // right hand relative to the shell pair frame (both shells) / to one shell: cap just behind the base(s), forearm back,
  // right and down, palm toward the shells
  // right hand carrying shells: the fist sits just below the shells' base(s), its forearm fixed in the root (screen) frame
  // running down-right and a little toward the camera, so the arm is seen from the side and never covers the breech
  // The firing (right) hand never leaves the wrist; the support (left) hand does the loading, its forearm fixed in the root
  // frame running down and LEFT, so the arm comes in from the lower left, away from the breech
  // The fist sits below-left of the shells' base(s) so they stick out of its upper-right edge and the breech stays visible.
  const CARRY_L = arm([-0.45, -0.86, 0.22], [0.2, 0.6, -0.8]), CARRY_AT = [-1.7, -1.1, 0.25];
  const carry = (D, base) => X(CARRY_L, add(add(D.t, mv(D.R, base)), CARRY_AT));
  const BASE_PAIR = [0, CH.right[1], F.BZ + 0.1], BASE_ONE = [CH.right[0], CH.right[1], F.BZ + 0.1];

  const clips = [];
  const clip = (name, length, loop, build) => clips.push({name, length, loop, build});

  clip('static_idle', 0.25, 'hold', () => ({tracks: {}}));

  // ---------- shoot: secondary kick on top of the Native recoil ----------
  clip('shoot', 0.5, 'once', () => {
    const S = t => ({h: plus({rot: jolt(t, 0, [2.4, 0.4, 1.3], {rise: 0.02, per: 0.26, tau: 0.07})}, {pos: jolt(t, 0, [0, 0.12, 0.55], {rise: 0.02, per: 0.26, tau: 0.07})}), open: 0, ext: 0, lever: 0});
    return {state: S, left: t => leftOnForend(S(t)), sounds: [[0, 'fire']]};   // fire: resource consistency only, the server plays the shot
  });

  // ---------- draw: comes up from low right slightly open and snaps shut; support hand arrives on the forend ----------
  clip('draw', 1.0, 'once', () => {
    const SNAP = 0.34;
    const S = t => { const w = go(t, 0.02, 0.32, {antic: 0, over: 0.08, per: 0.2, tau: 0.08}), open = t < SNAP ? -14 * (1 - ease(t, SNAP - 0.06, SNAP)) : 0;
      return {h: plus(pose(LOW, 1 - w), {rot: jolt(t, SNAP, [3.0, 0.3, -1.2])}, {pos: jolt(t, SNAP, [0, 0.1, 0.25])}), open, ext: extOf(open), lever: t < SNAP ? 20 : 20 * (1 - clamp01((t - SNAP) / 0.03))}; };
    return {state: S, left: t => mixX(LOW_LEFT, leftOnForend(S(t)), go(t, 0.3, 0.56, {antic: 0, over: 0.04})),
      camera: t => sum(mul([-1.6, 0.6, 1.2], 1 - go(t, 0.02, 0.32, {antic: 0, over: 0.1})), jolt(t, SNAP, [0.9, -0.2, -0.4])), sounds: [[SNAP - 0.02, 'close']]};
  });

  // ---------- put away: support hand leaves first, the gun drops out of view ----------
  clip('put_away', 0.5, 'once', () => {
    const S = t => ({h: pose(LOW, go(t, 0.1, 0.46, {antic: 0.05, over: 0})), open: 0, ext: 0, lever: 0});
    return {state: S, left: t => mixX(leftOnForend(S(t)), LOW_LEFT, go(t, 0.0, 0.22, {antic: 0, over: 0})),
      camera: t => mul([-1.2, 0.4, 1], go(t, 0.1, 0.46, {antic: 0, over: 0}))};
  });

  // ---------- reloads ----------
  // Opening and closing are shared: thumb on the top lever, the gun rolls in to the loading pose while the barrels drop
  // (left hand pulls the forend down), the extractor lifts the shells; closing snaps the barrels up, the lever springs back.
  function breakOpen(T) {
    const wLoad = t => go(t, T.in0, T.in1, {antic: 0.04, over: 0.06, per: 0.24, tau: 0.09}) - go(t, T.out0, T.out1, {antic: 0.03, over: 0.08, per: 0.26, tau: 0.1});
    const open = t => OPEN * (ease(t, T.open0, T.open1) - ease(t, T.close0, T.close1));
    const lever = t => t < T.close1 ? LEVER * ease(t, 0, T.open0) : LEVER * (1 - clamp01((t - T.close1) / 0.03));
    const hits = t => sum(jolt(t, T.open1, [-2.6, 0.3, 1.2], {per: 0.13, tau: 0.05}), jolt(t, T.close1, [4.2, 0.6, -1.8], {per: 0.12, tau: 0.05}),
      T.seat ? jolt(t, T.seat, [1.4, 0, -0.5]) : Z);
    return {wLoad, open, lever, hits};
  }
  // spent shell flight: slides out of the chamber (by `slide`), leaves at tr with its true world velocity plus a kick, falls, tumbles
  function flight(path, tr, kick, spin, carryOver = 0.3) {   // carryOver: share of the gun's own velocity the shell keeps
    const at = path(tr), v = add(mul(sub(at.t, path(tr - 0.004).t), carryOver / 0.004), kick);
    return t => { if (t <= tr) return path(t); const d = t - tr; return {R: mm(E(mul(spin, d)), at.R), t: add(add(at.t, mul(v, d)), mul(GRAV, 0.5 * d * d))}; };
  }

  function reload(empty) {
    const T = empty
      ? {in0: 0.04, in1: 0.40, open0: 0.10, open1: 0.30, left0: 0.44, left1: 0.60, tip0: 0.56, tip1: 0.74, back0: 0.82, back1: 1.12, slide0: 0.62, release: 0.72, hide: 1.25,
        leave0: 0.80, leave1: 1.10, come0: 1.24, come1: 1.62, push0: 1.66, seat: 1.84, ret0: 1.90, ret1: 2.22, close0: 2.30, close1: 2.40, out0: 2.34, out1: 2.90, end: 3.2}
      : {in0: 0.04, in1: 0.40, open0: 0.10, open1: 0.30, leave0: 0.40, grab: 0.66, pull0: 0.70, pull1: 0.86, toss0: 0.86, release: 0.96, hide: 1.30,
        off: 1.16, come0: 1.30, come1: 1.62, push0: 1.66, seat: 1.82, ret0: 1.88, ret1: 2.18, close0: 2.24, close1: 2.34, out0: 2.28, out1: 2.80, end: 2.8};
    const B = breakOpen(T);
    // dump weights: carried left, tipped over, both brought back together
    const dLeft = t => !empty ? 0 : go(t, T.left0, T.left1, {antic: 0.04, over: 0.04}) - go(t, T.back0, T.back1, {antic: 0, over: 0.05});
    const dTip = t => !empty ? 0 : go(t, T.tip0, T.tip1, {antic: 0.06, over: 0.1, per: 0.26, tau: 0.08}) - go(t, T.back0, T.back1, {antic: 0, over: 0.05});
    const S = t => { const open = B.open(t);
      const base = plus(pose(LOAD, B.wLoad(t)), {rot: B.hits(t)}, {pos: mul([0, 0.1, 0.2], B.hits(t)[0])});
      // the tip is a true root-frame rotation composed on top of the pose (about the breech, so the gun tips over its front)
      const Q = E(mul(DUMP_TIP, dTip(t))), about = add(GRIP, [0, 1.3, -7.4]);
      const Hb = bone(GRIP, base.pos, base.rot), tipped = cx(bone(about, Z, euler(Q)), Hb), h = unbone(GRIP, cx(shift(mul(DUMP_SHIFT, dLeft(t))), tipped));
      return {h, open, ext: extOf(open), lever: B.lever(t)}; };
    // new shell(s): off-screen -> lined up behind the chamber(s) -> pushed home by the thumb (hard stop)
    const aligned = t => inChamber(S(t), S_OUT);
    const offPair = X(mm(E([28, 18, -36]), aligned(T.come1).R)), OFF_T = add(aligned(T.come1).t, [-9, -14, 7]);   // off-screen low left
    const newShells = t => {
      if (t < T.come1) return mixX({R: offPair.R, t: OFF_T}, aligned(t), go(t, T.come0, T.come1, {antic: 0, over: 0.04, per: 0.12, tau: 0.04}));
      return inChamber(S(t), S_OUT * (1 - go(t, T.push0, T.seat, {antic: 0.02, over: 0, pre: 0.03})));
    };
    const base = empty ? BASE_PAIR : BASE_ONE, offHand = carry({R: offPair.R, t: OFF_T}, base);
    const tracks = {}, shells = {};
    let left;
    if (empty) {
      // dump: as the breech end drops, both spent shells slide out under gravity and fall away with part of the gun's velocity
      const slide = t => t < T.slide0 ? 0 : 2.6 * ((t - T.slide0) / (T.release - T.slide0)) ** 2;   // gravity pulls them out as the breech turns down
      shells.spent_shell_right = flight(t => inChamber(S(t), slide(t)), T.release, [-4, -14, 6], [-260, 60, 180], 0.6);
      shells.spent_shell_left = flight(t => inChamber(S(t), slide(t) * 0.94), T.release + 0.02, [-8, -12, 4], [-220, -50, -160], 0.6);
      shells.live_shell_right = shells.live_shell_left = newShells;
      // left hand holds the forend through the opening and the dump, fetches both shells, pushes them in, returns to the
      // forend in time to lift the barrels shut
      left = t => {
        if (t < T.leave0) return leftOnForend(S(t));
        if (t < T.come0) return mixX(leftOnForend(S(t)), offHand, go(t, T.leave0, T.leave1, {antic: 0.03, over: 0}));
        if (t < T.ret0) return carry(newShells(t), base);
        return mixX(carry(newShells(T.ret0), base), leftOnForend(S(t)), go(t, T.ret0, T.ret1, {antic: 0, over: 0.04}));
      };
      tracks.spent_shell_right = tracks.spent_shell_left = {scale: t => t < T.hide ? [1, 1, 1] : [0, 0, 0], step: true};
      tracks.live_shell_right = tracks.live_shell_left = {scale: t => t >= T.come0 ? [1, 1, 1] : [0, 0, 0], step: true};
    } else {
      // pluck: the left hand pinches the spent right shell, draws it out along the chamber and tosses it away to the left
      const pulled = t => inChamber(S(t), S_OUT * go(t, T.pull0, T.pull1, {antic: 0, over: 0}));
      const grip0 = t => carry(pulled(t), BASE_ONE);   // the fist under the rim, pinching it
      const tossTo = X(mm(E([-10, 20, 40]), CARRY_L), add(grip0(T.pull1).t, [-10, -7, 6]));
      const handToss = t => mixX(grip0(T.toss0), tossTo, go(t, T.toss0, T.release + 0.12, {antic: 0.02, over: 0}));
      const inHand = cx(ix(grip0(T.toss0)), pulled(T.toss0));   // shell relative to the hand once drawn out
      const held = t => t < T.toss0 ? pulled(t) : cx(handToss(t), inHand);
      shells.spent_shell_right = flight(held, T.release, [-16, 4, 10], [300, 120, -380], 0.8);
      shells.live_shell_right = newShells;
      left = t => {
        if (t < T.grab) return mixX(leftOnForend(S(t)), grip0(t), go(t, T.leave0, T.grab, {antic: 0.03, over: 0}));
        if (t < T.toss0) return grip0(t);
        if (t < T.off) return mixX(handToss(t), offHand, ease(t, T.release, T.off));
        if (t < T.ret0) return t < T.come0 ? offHand : carry(newShells(t), base);
        return mixX(carry(newShells(T.ret0), base), leftOnForend(S(t)), go(t, T.ret0, T.ret1, {antic: 0, over: 0.04}));
      };
      tracks.spent_shell_left = {scale: () => [0, 0, 0], step: true};
      tracks.spent_shell_right = {scale: t => t < T.hide ? [1, 1, 1] : [0, 0, 0], step: true};
      tracks.live_shell_right = {scale: t => t >= T.come0 ? [1, 1, 1] : [0, 0, 0], step: true};
    }
    const camera = t => sum(mul([-1.0, 1.2, 1.6], B.wLoad(t)), mul([-0.8, 0.6, 1.2], dLeft(t)), mul([-1.2, 0, 0.6], dTip(t)), mul(B.hits(t), 0.35), jolt(t, T.close1, [1.2, -0.2, -0.5]));
    const sounds = [[0.08, 'open'], [empty ? T.release - 0.03 : T.pull0 + 0.02, 'eject'], [T.seat - 0.03, 'shell_insert'], [T.close1 - 0.02, 'close']];
    // No chamber_eject_fx cue: in the loading pose the chamber mouths face the eye (9-35 deg off the view axis, 0.3-0.7
    // blocks away), so the Chamber Gas FX jet would blow straight into the camera. It returns with a softer, rising variant.
    // the firing hand stays on the wrist, turning a little at the wrist as the gun rolls into the loading pose
    return {state: S, left, right: t => wrist(rightOnGrip(S(t)), R0R, 0.5), shells, tracks, camera, sounds};
  }
  clip('reload_empty', 3.2, 'once', () => reload(true));
  clip('reload_tactical', 2.8, 'once', () => reload(false));

  // ---------- inspect: right side, roll over to the top and left side, break it open and look into the chambers ----------
  clip('inspect', 5.0, 'once', () => {
    const T = {a0: 0.05, a1: 0.6, b0: 1.7, b1: 2.3, c0: 2.35, c1: 2.75, open0: 2.62, open1: 2.80, close0: 3.98, close1: 4.10, d0: 4.05, d1: 4.75};
    const wA = t => go(t, T.a0, T.a1, {antic: 0.04, over: 0.06}) - go(t, T.b0, T.b1, {antic: 0.02, over: 0});
    const wB = t => go(t, T.b0, T.b1, {antic: 0.02, over: 0.05}) - go(t, T.c0, T.c1, {antic: 0.02, over: 0});
    const wC = t => go(t, T.c0, T.c1, {antic: 0.02, over: 0.05}) - go(t, T.d0, T.d1, {antic: 0.03, over: 0.08, per: 0.26, tau: 0.1});
    const drift = t => ({rot: [2 * Math.sin(2.1 * t), 3 * Math.sin(1.3 * t + 1), 2 * Math.sin(1.7 * t + 2)].map(v => v * clamp01((t - T.a1) / 0.3) * (1 - clamp01((t - T.d0) / 0.3)))});
    const S = t => { const open = OPEN * (ease(t, T.open0, T.open1) - ease(t, T.close0, T.close1));
      const hits = sum(jolt(t, T.open1, [-2.2, 0.2, 1.0], {per: 0.13, tau: 0.05}), jolt(t, T.close1, [3.6, 0.5, -1.5], {per: 0.12, tau: 0.05}));
      return {h: plus(pose(INS_A, wA(t)), pose(INS_B, wB(t)), pose(LOAD, wC(t)), drift(t), {rot: hits}), open, ext: extOf(open),
        lever: t < T.close1 ? LEVER * ease(t, T.open0 - 0.1, T.open0) : LEVER * (1 - clamp01((t - T.close1) / 0.03))}; };
    return {state: S, left: t => wrist(leftOnForend(S(t)), R0L, 0.6), right: t => wrist(rightOnGrip(S(t)), R0R, 0.8),
      camera: t => sum(mul([-1.0, 1.4, 1.6], wA(t)), mul([-1.2, -1.2, -1.4], wB(t)), mul([-1.0, 1.2, 1.6], wC(t)), jolt(t, T.close1, [1.1, -0.2, -0.4])),
      sounds: [[T.open0 - 0.02, 'open'], [T.close1 - 0.02, 'close']]};
  });

  // ---------- solve every clip into bone tracks ----------
  const HAND_CANDIDATES = [[0, 0, 0], [0, 90, 0], [0, -90, 0], [90, 0, 0], [-90, 0, 0], [0, 0, 90], [0, 0, -90]];
  return clips.map(c => {
    const r = c.build(), tracks = {...(r.tracks || {})};
    if (r.state) {
      tracks.handling = {position: t => r.state(t).h.pos, rotation: t => r.state(t).h.rot};
      tracks.barrels = {rotation: t => [r.state(t).open, 0, 0]};
      tracks.extractor = {position: t => mul(OUT, r.state(t).ext)};
      tracks.top_lever = {rotation: t => [0, r.state(t).lever, 0]};
    }
    // hands: anchor world pose -> hand bone (pivot = anchor point) with a constant pre-rotation on *_pos chosen per clip to
    // keep the Euler track far from gimbal lock
    const hand = (side, A) => {
      const P = side === 'right' ? RP : LP, R0 = side === 'right' ? R0R : R0L;
      const local = side === 'right' ? t => cx(ix(Wh(r.state(t))), A(t)) : A;
      return HAND_CANDIDATES.map(cand => ({cand, pos: t => sub(local(t).t, P), rot: t => euler(mm(mm(local(t).R, T3(R0)), T3(E(cand))))}));
    };
    const hands = {};
    if (r.left) hands.lefthand = hand('left', r.left);
    if (r.right) hands.righthand = hand('right', r.right);
    for (const [name, A] of Object.entries(r.shells || {})) {
      const C = piv(name);
      const f = t => unbone(C, cx(ix(We(r.state(t))), A(t)));
      tracks[name] = {...(tracks[name] || {}), position: t => f(t).pos, rotation: t => f(t).rot};
    }
    if (r.camera) tracks.camera = {rotation: r.camera};
    return {name: c.name, length: c.length, loop: c.loop, tracks, hands, sounds: r.sounds || [], timeline: r.timeline || []};
  });
}

// ---------- bake (as Blackridge .50): 60 Hz linear keys, Euler unwrapped, gimbal-refined, redundant keys dropped ----------
export const FPS = 60;
const round = v => +v.toFixed(4);
function unwrapE(v, ref) {   // the Euler triple equivalent to v closest to ref (no +-360 or gimbal-mirror jumps)
  let best = null, bd = Infinity;
  for (const base of [v, [v[0] + 180, 180 - v[1], v[2] + 180]])
    for (const k0 of [-2, -1, 0, 1, 2]) for (const k1 of [-2, -1, 0, 1, 2]) for (const k2 of [-2, -1, 0, 1, 2]) {
      const c = [base[0] + 360 * k0, base[1] + 360 * k1, base[2] + 360 * k2], d = c.reduce((m, x, i) => m + (x - ref[i]) ** 2, 0);
      if (d < bd) { bd = d; best = c; }
    }
  return best;
}
export function bake(fn, length, {step = false, rotRef = null} = {}) {
  const n = Math.round(length * FPS), keys = [];
  let prev = rotRef;
  for (let i = 0; i <= n; i++) {
    const t = round(Math.min(length, i / FPS)); let v = fn(t);
    if (prev) { v = unwrapE(v, prev); prev = v; }
    if (prev && keys.length) {
      const refine = (ta, va, tb, vb, depth) => {
        if (Math.max(...va.map((x, j) => Math.abs(x - vb[j]))) <= 6 || depth > 6) return [];
        const tm = (ta + tb) / 2, vm = unwrapE(fn(tm), va);
        return [...refine(ta, va, tm, vm, depth + 1), [tm, vm], ...refine(tm, vm, tb, vb, depth + 1)];
      };
      const [tp, vp] = keys.at(-1);
      for (const [tm, vm] of refine(tp, vp, t, v, 0)) keys.push([+tm.toFixed(5), vm.map(round)]);
    }
    keys.push([t, v.map(round)]);
  }
  const out = [keys[0]];
  for (let i = 1; i < keys.length - 1; i++) {
    const [ta, a] = out.at(-1), [t, v] = keys[i], [tb, b] = keys[i + 1];
    const same = step ? v.every((x, k) => x === a[k]) : v.every((x, k) => Math.abs(a[k] + (b[k] - a[k]) * (t - ta) / (tb - ta) - x) < 2e-4);
    if (!same) out.push(keys[i]);
  }
  if (keys.length > 1) out.push(keys.at(-1));
  return step ? out.filter((k, i) => i === 0 || i === out.length - 1 || !k[1].every((x, j) => x === out[i - 1][1][j])) : out;
}
// pick the hand pre-rotation whose baked Euler track has the smallest largest step
export function bakeHand(cands, length) {
  let best = null;
  for (const c of cands) {
    const keys = bake(c.rot, length, {rotRef: c.rot(0)});
    let worst = 0; for (let i = 1; i < keys.length; i++) worst = Math.max(worst, ...keys[i][1].map((v, j) => Math.abs(v - keys[i - 1][1][j])));
    if (!best || worst < best.worst - 1e-6) best = {c, keys, worst};
  }
  return {pre: best.c.cand, rotation: best.keys, position: bake(best.c.pos, length), worst: best.worst};
}
