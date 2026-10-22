// P9 trained-tier reload / inspect as player actions (third person; docs/dev/p9_reload_inspect_v1.md 3): writes
//   player_props/p9_01_mag.json, p9_01_mag_old.json   the magazines out of the gun (drawn by the gun's own mag_out /
//                                                      empty_old_mag bones: SkeletonGunStance, NativeAnimatedWeaponRenderer)
//   player_actions/gun/p9_01_reload_tactical.json, p9_01_reload_empty.json, p9_01_inspect.json, p9_01_inspect_empty.json,
//   p9_01_inspect_no_mag.json, p9_01_inspect_empty_no_mag.json (no magazine in the gun: no magazine check),
//   p9_01_reload_tactical_ads.json, p9_01_reload_empty_ads.json (reloads started while aiming: the gun stays near the eye
//   line in the view frame, dips and cants; the action is shorter than the clip and blends out into the aim)
// Same beats and lengths as the first-person clips (tools/author-p9-01-v3-native-animations.mjs), played from the clip's
// start at speed 1, so the gun's own clip (slide, the magazine in the gun) matches. The gun prop is keyed in the chest frame
// from the idle's chest ready; the fresh magazine comes out of a pouch at the left front of the belt into the left hand,
// rides into the magwell on the gun (prop:p9_01), and hides at the seat (the gun's own magazine shows from there); the old
// one slides out of the gun and falls to the ground in the root frame.
//   node tools/author-p9-01-reload-actions.mjs            write, then print the per-frame checks
//   node tools/author-p9-01-reload-actions.mjs --check    compare instead of writing (exit 1 on a difference)
import fs from 'node:fs';
import path from 'node:path';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
import {MP, MAG_TILT} from './build-p9-01-v2-native.mjs';
import {INSPECT_T, INSPECT_NO_MAG_T, tierTiming, SLIDE_LOCK, SLIDE_TOP_REAR, CROOKED, BUMP_DEPTH, OUT_DEPTH} from './author-p9-01-v3-native-animations.mjs';

const require = createRequire(import.meta.url);
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const core = require('./blockbench/afl_rig.js');
const {v3, q4, tf, rad, deg} = core;
const A = path.join(root, 'src/main/resources/assets/apocalypse_firstlight');
const read = p => JSON.parse(fs.readFileSync(p, 'utf8'));
const r3 = v => v.map(x => Math.round(x * 1000) / 1000 || 0);
const gunDef = read(path.join(A, 'player_props/p9_01.json'));
const S = gunDef.scale;

// ---------- the magazine props ----------
// gun model px; the magazine axis points out of the magwell (down and back), as the first-person authoring has it
const rotX = (p, d, o) => { const c = Math.cos(d * Math.PI / 180), s = Math.sin(d * Math.PI / 180), y = p[1] - o[1], z = p[2] - o[2]; return [p[0], o[1] + y * c - z * s, o[2] + y * s + z * c]; };
const AX = v3.norm(rotX([0, -1, 0], MAG_TILT, [0, 0, 0]));
const MAG_BASE = rotX([0, -0.99, 1.79], MAG_TILT, MP);
const MAG_CLEAR = 4.9, MAG_ENTRY = 4.2, MAG_PART = 0.9;
function magBounds(bone) {
    const mesh = read(path.join(A, 'meshes/p9_01_v3_native.aflmesh.json'));
    const geo = read(path.join(A, 'geo/p9_01_v3_native.geo.json'))['minecraft:geometry'][0].bones;
    const pivot = geo.find(b => b.name === bone).pivot.map(n => n / 16);
    const lo = [Infinity, Infinity, Infinity], hi = [-Infinity, -Infinity, -Infinity];
    for (const p of mesh.parts) if (p.bone === bone) for (const v of p.vertices) for (let i = 0; i < 3; i++) {
        lo[i] = Math.min(lo[i], v[i] + pivot[i]); hi[i] = Math.max(hi[i], v[i] + pivot[i]);
    }
    if (!(hi[0] > lo[0])) throw new Error('no mesh on ' + bone);
    return [r6(lo), r6(hi)];
}
const r6 = v => v.map(x => Math.round(x * 1e6) / 1e6 || 0);
const magProp = (bone, meshBone, what) => ({
    about: `The P9's ${what} as a player action's prop (docs/dev/p9_reload_inspect_v1.md 3): the gun's own ${bone} bone, drawn by the`
        + ' gun renderer where the action places this prop (gun_part); its frame is the gun\'s (the magazine seated when it sits on the gun'
        + ' prop unturned), same scale. Points: top (the magazine pivot), base (the base plate). Written by tools/author-p9-01-reload-actions.mjs.',
    items: [],
    gun_part: {gun: 'p9_01', bone},
    scale: S,
    bounds: magBounds(meshBone),
    points: {top: r6(MP.map(n => n / 16)), base: r6(MAG_BASE.map(n => n / 16))},
});
const MAG = magProp('mag_out', 'reload_magazine', 'fresh magazine'), OLD = magProp('empty_old_mag', 'empty_old_mag', 'old magazine');

// ---------- placing ----------
const RX180 = [1, 0, 0, 0], RZ180 = [0, 0, 1, 0];
const local = (def, name) => core.propLocal(def, name);
/** The gun frame turned as a gun held in the model's axes would be: pitch + muzzle up, yaw + left, roll + top to the left. */
const turnOf = (pitch, yaw, roll) => q4.norm(q4.mul(q4.mul(RZ180, q4.mul(q4.mul(q4.y(rad(yaw)), q4.x(rad(pitch))), q4.z(rad(roll)))), RX180));
/** A key with the def's point at pos (frame px) turned so. */
const keyAt = (def, frame, point, pos, pitch, yaw, roll, extra = {}) =>
    ({frame, point, pos: r3(pos), rot: r3(q4.eulerXYZ(turnOf(pitch, yaw, roll)).map(deg)), ...extra});
/** The magazine on the gun, `d` gun px out along the magwell (0: seated). */
const inGun = (d, extra = {}) => ({frame: 'prop:p9_01', point: 'top', pos: r3(v3.add(local(MAG, 'top'), [AX[0] * d * S, -AX[1] * d * S, -AX[2] * d * S])),
    rot: [0, 0, 0], ...extra});
const CHEST_READY = {frame: 'chest', point: 'grip_r', pos: [-1.3, 6.6, -7.4], rot: [-156.887, 5.521, 177.648]};   // = gun/p9_01_idle
const gun = (grip, pitch, yaw, roll) => keyAt(gunDef, 'chest', 'grip_r', grip, pitch, yaw, roll);
const k = (at, key, blend, extra = {}) => ({at: +at.toFixed(4), ...key, blend: +blend.toFixed(4), ...extra});

// gun poses (chest frame, grip_r): the work space before the upper chest, eyes over it (trained: TC 3-23.35)
const UP = gun([-0.5, 1.6, -9.6], 30, 5, -28);     // rolled out (top right): the magwell to the support hand
const MEET = gun([0.3, 2.5, -9.1], 25, 8, -28);
const HOLD = gun([-0.3, 1.9, -9.4], 30, 10, -22);
const SHOW_L = gun([-1.2, 1.2, -10.0], 10, 50, 20);   // muzzle left: the left side to the eyes
const SHOW_R = roll => gun([-1.6, 1.8, -9.8], 5, -20, roll);   // muzzle right, rolled in: the ejection port up to the eyes
// the pouch: left front of the belt (pelvis frame), the magazine upright, bullets forward
const POUCH = keyAt(MAG, 'bone:pelvis', 'base', [5.2, 10.4, -4.6], 22, 0, 0);
const GROUND_Y = 23.6;   // root frame: just above the feet

// the old magazine falls from where it cleared the gun: free fall (16 px per metre), drawn as short legs
function fall(at, from, tumble) {
    const g = 9.81 * 16, keys = [];
    const ground = GROUND_Y - from.pos[1];
    const land = Math.sqrt(2 * Math.max(1, ground) / g);
    for (let i = 1; i <= 5; i++) {
        const t = land * i / 5, drop = 0.5 * g * t * t;
        keys.push(k(at + t - land / 5, {frame: 'root', point: 'top', pos: r3(v3.add(from.pos, [0, Math.min(drop, ground), -2.2 * t])),
            rot: r3(v3.add(from.rot, v3.mul(tumble, t)))}, land / 5));
    }
    const {frame, point, pos, rot} = keys.at(-1);
    keys.push(k(at + land + 0.02, {frame, point, pos, rot}, 0, {hidden: true}));
    return keys;
}

// the rest pose's placement of a key (for the fall's start): solve the action up to that moment
const rig = core.loadRig(read(path.join(A, 'rigs/afl_humanoid_v1.json')));
const poser = new core.Poser(rig, core.loadClips(rig, read(path.join(A, 'animations/player/locomotion.animation.json'))));
const defs = {p9_01: gunDef, p9_01_mag: MAG, p9_01_mag_old: OLD};
const asAction = json => core.actionFromJson(json, id => defs[id] || null, () => null);
function rootKeyOf(json, track, at) {
    const res = core.solveFrame(poser, {action: asAction(json), seconds: at, time: at});
    const placed = res.props[track], def = defs[json.props[track].id];
    const root = res.pose.world[rig.root];
    const inRoot = tf.mul(tf.inv(root), placed);
    return {pos: r3(tf.apply(inRoot, local(def, 'top'))), rot: r3(q4.eulerXYZ(inRoot.q).map(deg))};
}

const POLE_R = [-0.27, 0.89, 0.37], POLE_L = [0.26, 0.95, 0.16], POLE_L_LOW = [0.6, 0.6, -0.3];
function base(id, about, length) {
    return {format_version: 1, about, layer: 'action', mask: 'upper', priority: 60, first_person: 'view_model',
        blend: {in: 0.12, out: 0.2}, length};
}

// ADS reload (docs/dev/p9_reload_inspect_v1.md 2.7): view frame (the eye, turned with the look), grip_r; eyes stay on the
// target over the sights while the gun dips a little and cants so the magwell meets the support hand under the line of sight
// two hands on in this frame reach at every look (-32..25 deg) only close and level: grip 4.4 px under the eye, 7 px ahead
const ADS_VIEW = [0, 4.4, -7.0];
const view = (grip, pitch, yaw, roll) => keyAt(gunDef, 'view', 'grip_r', grip, pitch, yaw, roll);
const AIMED = view(ADS_VIEW, 0, -3, 4);
const ADS_DIP = view(v3.add(ADS_VIEW, [0.1, 0.6, 0]), 8, 5, -18);
const ADS_MEET = view(v3.add(ADS_VIEW, [0, 1.0, 0.2]), 6, 6, -16);
const ADS_HOLD = view(v3.add(ADS_VIEW, [0, 0.6, 0]), 8, 4, -10);
const ADS_VIEW_PITCH = [-32, 25];   // the frame follows the look this far up / down (deg); further down the gun would reach the face

function reload(empty, ads = false) {
    const T = empty ? {release: 0.22, drop: 0.24, seat: 0.9, back: 1.06, backEnd: 1.5, end: 2.0} : {release: 0.22, drop: 0.24, seat: 0.86, back: 0.93, backEnd: 1.34, end: 1.7};
    if (ads) Object.assign(T, empty ? {back: 1.04, backEnd: 1.3, end: 1.3} : {back: 0.9, backEnd: 1.16, end: 1.16});   // = the first-person clip's return
    const id = (empty ? 'p9_01_reload_empty' : 'p9_01_reload_tactical') + (ads ? '_ads' : '');
    const json = base(id, `P9 ${empty ? 'empty' : 'tactical'} reload, trained tier (gun/${id}; docs/dev/p9_reload_inspect_v1.md 2-3): the gun comes up before`
        + ' the upper chest rolled so its magwell faces the support hand; the thumb drops the old magazine (it falls to the ground);'
        + ' the support hand takes a fresh one from the belt pouch at the left front, brings it up and seats it with the palm; '
        + (empty ? 'the thumb drops the slide off the slide stop (the gun\'s clip); ' : '') + 'back to the chest ready. Same beats and length as the'
        + ' first-person clip; the own first person shows the view model. Written by tools/author-p9-01-reload-actions.mjs.', T.end);
    if (ads) json.about = `P9 ${empty ? 'empty' : 'tactical'} reload while aiming (gun/${id}; docs/dev/p9_reload_inspect_v1.md 2.7, 3): eyes stay on the`
        + ' target; the gun (view frame) only dips under the line of sight and cants so the magwell meets the support hand, the magazine'
        + ' change happens by feel, then the gun comes straight back up. Same beats as the first-person clip; ends where that one is back on'
        + ' the sights (the clip runs on to its full length) and blends out into the aim. Written by tools/author-p9-01-reload-actions.mjs.';
    if (ads) json.view_pitch = ADS_VIEW_PITCH;
    json.props = [
        {id: 'p9_01', keys: ads
            ? [k(0, AIMED, 0), k(0, ADS_DIP, 0.2), k(0.3, ADS_MEET, T.seat - 0.32), k(T.seat, ADS_HOLD, 0.12), k(T.back, AIMED, T.backEnd - T.back)]
            : [k(0, CHEST_READY, 0), k(0, UP, 0.28), k(0.34, MEET, T.seat - 0.36), k(T.seat, HOLD, 0.12), k(T.back, CHEST_READY, T.backEnd - T.back)]},
        {id: 'p9_01_mag', keys: [k(0, POUCH, 0, {hidden: true}), k(0.28, POUCH, 0),
            k(0.3, keyAt(MAG, 'chest', 'base', [2.6, 8.0, -8.0], 26, 18, -40), 0.18),
            k(0.48, inGun(MAG_ENTRY), 0.14), k(0.62, inGun(MAG_PART), T.seat - 0.12 - 0.62), k(T.seat - 0.04, inGun(0), 0.04),
            k(T.seat, inGun(0), 0, {hidden: true})]},
        {id: 'p9_01_mag_old', keys: [k(0, inGun(0), 0, {hidden: true}), k(T.drop, inGun(0), 0), k(T.drop, inGun(MAG_CLEAR), 0.08)]},
    ];
    json.hands = {
        right: [{at: 0, target: 'prop:p9_01:grip_r', blend: 0, roll: 0, pole: POLE_R}],
        left: [{at: 0, target: 'prop:p9_01:grip_l', blend: 0, roll: 0, pole: POLE_L},
            {at: 0.02, target: 'prop:p9_01_mag:base', blend: 0.26, roll: 0, pole: POLE_L_LOW},
            {at: T.seat + 0.04, target: 'prop:p9_01:grip_l', blend: T.backEnd - 0.1 - T.seat, roll: 0, pole: POLE_L}],
    };
    json.events = [];
    const start = rootKeyOf(json, 2, T.drop + 0.08);
    json.props[2].keys.push(...fall(T.drop + 0.08, start, [-260, 40, 120]));
    if (ads) json.props[2].keys = json.props[2].keys.filter(key => key.at <= T.end);   // the action ends before it lands
    return [id, json];
}

function inspect(empty) {
    const T = INSPECT_T, id = empty ? 'p9_01_inspect_empty' : 'p9_01_inspect';
    const json = base(id, `P9 ${empty ? 'empty ' : ''}inspect, trained tier (gun/${id}; docs/dev/p9_reload_inspect_v1.md 2.4-2.5, 3): unhurried; the support`
        + ' hand lets go, the gun shows its left side, turns slowly to show its right side' + (empty ? ' (the open chamber)' : '') + ', then a magazine'
        + ' check: it drops into the support hand, which raises it to the left before the face and turns it (one side, the top with the round,'
        + ' the other side) while the gun rests low to the right; back into the magwell, seated with the palm; back to the chest ready.'
        + ' Same beats and length as the first-person clip (INSPECT_T). Written by tools/author-p9-01-reload-actions.mjs.', T.end);
    const CHECK = gun([-0.5, 1.6, -9.6], 30, 10, -22);
    const REST = gun([-1.6, 3.4, -8.8], 12, -6, -10);   // let down to the right while the magazine is looked at
    // the magazine before the face, out to the left (chest frame: +x left, -y up): one side, the top tipped to the eyes, the other side
    const look = (pitch, yaw, roll, d = [0, 0, 0]) => keyAt(MAG, 'chest', 'base', v3.add([5.4, 1.2, -9.4], d), pitch, yaw, roll);
    json.props = [
        {id: 'p9_01', keys: [k(0, CHEST_READY, 0), k(0.02, SHOW_L, T.up - 0.02), k(T.turn, SHOW_R(empty ? 40 : 28), T.turned - T.turn),
            k(T.check, CHECK, T.checked - T.check), k(T.out, REST, 0.75), k(T.lookEnd, CHECK, T.align - T.lookEnd),
            k(T.back, CHEST_READY, T.backEnd - T.back)]},
        {id: 'p9_01_mag', keys: [k(0, inGun(0), 0, {hidden: true}), k(T.drop, inGun(0), 0), k(T.drop, inGun(MAG_CLEAR), T.out - T.drop),
            k(T.out, look(-15, -65, -5), T.look - T.out),
            k(T.look + 0.35, look(-18, -75, -4, [0.1, -0.1, 0]), 0.4),
            k(T.look + 0.75, look(-55, -80, -2, [0.2, 0.1, 0.2]), 0.75),        // the top to the eyes: the lips and the round
            k(T.look + 1.8, look(-30, -15, -4), 0.6),
            k(T.look + 2.4, look(-18, 58, 4, [-0.2, 0, 0]), 0.6),               // the other side
            k(T.lookEnd, inGun(MAG_ENTRY), T.align - T.lookEnd), k(T.align, inGun(MAG_PART), T.seat - 0.05 - T.align),
            k(T.seat - 0.05, inGun(0), 0.05), k(T.seat, inGun(0), 0, {hidden: true})]},
    ];
    json.hands = {
        right: [{at: 0, target: 'prop:p9_01:grip_r', blend: 0, roll: 0, pole: POLE_R}],
        left: [{at: 0, target: 'prop:p9_01:grip_l', blend: 0, roll: 0, pole: POLE_L},
            {at: 0.02, target: 'pose', blend: 0.4, roll: 0},
            {at: T.release - 0.5, target: 'prop:p9_01_mag:base', blend: 0.45, roll: 0, pole: POLE_L_LOW},
            {at: T.seat + 0.06, target: 'prop:p9_01:grip_l', blend: T.regrip - T.seat - 0.06, roll: 0, pole: POLE_L}],
    };
    json.events = [];
    return [id, json];
}

// inspect with no magazine in the gun (user 2026-10-20): the same left and right side, then the gun tips up to show the empty
// magwell and goes home; the support hand stays off it and comes back to the grip on the way. Same beats and length as the
// first-person clip (tools/author-p9-01-v3-native-animations.mjs INSPECT_NO_MAG_T).
function inspectNoMag(empty) {
    const T = INSPECT_NO_MAG_T, id = empty ? 'p9_01_inspect_empty_no_mag' : 'p9_01_inspect_no_mag';
    const json = base(id, `P9 ${empty ? 'empty ' : ''}inspect with no magazine in the gun (gun/${id}; docs/dev/p9_reload_inspect_v1.md 2.4-2.5, 3):`
        + ' unhurried; the support hand lets go, the gun shows its left side, turns slowly to show its right side' + (empty ? ' (the open chamber)' : '')
        + ', then tips up to show the empty magwell; back to the chest ready, the support hand regrips on the way. Same beats and length'
        + ' as the first-person clip (INSPECT_NO_MAG_T). Written by tools/author-p9-01-reload-actions.mjs.', T.end);
    const CHECK = gun([-0.5, 1.6, -9.6], 30, 10, -22);
    json.props = [
        {id: 'p9_01', keys: [k(0, CHEST_READY, 0), k(0.02, SHOW_L, T.up - 0.02), k(T.turn, SHOW_R(empty ? 40 : 28), T.turned - T.turn),
            k(T.check, CHECK, T.checked - T.check), k(T.back, CHEST_READY, T.backEnd - T.back)]},
    ];
    json.hands = {
        right: [{at: 0, target: 'prop:p9_01:grip_r', blend: 0, roll: 0, pole: POLE_R}],
        left: [{at: 0, target: 'prop:p9_01:grip_l', blend: 0, roll: 0, pole: POLE_L},
            {at: 0.02, target: 'pose', blend: 0.4, roll: 0},
            {at: T.back, target: 'prop:p9_01:grip_l', blend: T.regrip - T.back, roll: 0, pole: POLE_L}],
    };
    json.events = [];
    return [id, json];
}

// unload (docs/native_guns/magazine_system_v1.md 3.1): no fuller spare, the R key takes the fitted magazine out into the hand. Same
// beats as the first-person clip (tools/author-p9-01-v3-native-animations.mjs UNLOAD): the gun comes up as for the magazine check,
// the thumb releases the magazine into the support hand, which keeps it and goes low; the gun goes back to the chest ready. The
// action ends at the swap (1.05 s: the magazine becomes the held item); the gun's put-away (gun/p9_01_holster) follows.
const UNLOAD = {release: 0.36, drop: 0.38, out: 0.52, carried: 0.85, swap: 1.05};
function unload(empty) {
    const id = empty ? 'p9_01_unload_empty' : 'p9_01_unload';
    const json = base(id, `P9 ${empty ? 'empty ' : ''}unload (gun/${id}; docs/native_guns/magazine_system_v1.md 3.1): no fuller spare magazine,`
        + ' so the reload key takes the fitted one out: the gun comes up as for the magazine check, the thumb releases the magazine into'
        + ' the support hand, which keeps it and brings it down to the front of the belt; the gun goes back to the chest ready. Ends at'
        + ' the swap (the magazine becomes the held item, the gun is put away). Same beats as the first-person clip. Written by'
        + ' tools/author-p9-01-reload-actions.mjs.', UNLOAD.swap);
    const CHECK = gun([-0.5, 1.6, -9.6], 30, 10, -22);
    const LOW = keyAt(MAG, 'bone:pelvis', 'base', [3.4, 7.6, -6.2], 30, 10, -10);
    json.props = [
        {id: 'p9_01', keys: [k(0, CHEST_READY, 0), k(0.02, CHECK, 0.28), k(0.6, CHEST_READY, 0.4)]},
        {id: 'p9_01_mag', keys: [k(0, inGun(0), 0, {hidden: true}), k(UNLOAD.drop, inGun(0), 0), k(UNLOAD.drop, inGun(MAG_CLEAR), UNLOAD.out - UNLOAD.drop),
            k(UNLOAD.out + 0.03, LOW, UNLOAD.carried - UNLOAD.out - 0.03)]},
    ];
    json.hands = {
        right: [{at: 0, target: 'prop:p9_01:grip_r', blend: 0, roll: 0, pole: POLE_R}],
        left: [{at: 0, target: 'prop:p9_01:grip_l', blend: 0, roll: 0, pole: POLE_L},
            {at: 0.04, target: 'prop:p9_01_mag:base', blend: 0.26, roll: 0, pole: POLE_L_LOW}],
    };
    json.events = [];
    return [id, json];
}

// ---------- lower tiers (docs/dev/gun_proficiency_v1_plan.md 4; 2026-10-18) ----------
// Same beats as the first-person tier clips (tierTiming): the gun is worked lower and closer, the head over it (the head
// follows the look, so it is the gun that comes into the eyes' way): experienced at the lower chest, novice tucked in close
// and tipped up to look into the magwell, a second look after the seat; the empty reload racks the slide overhand (the
// left hand on a helper point riding the slide). Fumble: the magazine goes in crooked, stops, comes out, goes in again.
const RACK = {
    about: 'Where the support hand takes the P9\'s slide for an overhand rack (docs/dev/gun_proficiency_v1_plan.md 4): no look of its own;'
        + ' keyed on the gun prop at the slide\'s rear top, moved with the slide. Written by tools/author-p9-01-reload-actions.mjs.',
    items: [], scale: S, bounds: [[-0.01, -0.01, -0.01], [0.01, 0.01, 0.01]],
    points: {hand: r6(SLIDE_TOP_REAR.map(n => n / 16))},
};
defs.p9_01_rack = RACK;
/** The rack point with the slide `d` gun px back from battery. */
const onSlide = d => ({frame: 'prop:p9_01', point: 'hand', pos: r3(v3.add(local(RACK, 'hand'), [0, 0, -d * S])), rot: [0, 0, 0]});
const TIER_POSES = {
    experienced: {work: gun([-0.6, 3.4, -8.0], 30, 10, -24), meet: gun([0.0, 3.9, -7.8], 26, 12, -26), hold: gun([-0.4, 3.6, -8.0], 30, 12, -20),
        rack: gun([-0.6, 3.6, -9.4], 14, 24, 30), mid: [2.6, 8.4, -7.4]},
    novice: {work: gun([-0.8, 3.0, -7.6], 38, 12, -20), meet: gun([-0.2, 3.4, -7.4], 34, 14, -24), hold: gun([-0.6, 3.2, -7.6], 38, 16, -16),
        look: gun([-0.8, 2.8, -7.6], 40, 20, -12), rack: gun([-0.4, 3.2, -9.4], 16, 24, 30), mid: [2.4, 8.6, -6.8]},
};
function tierReload(empty, tierName, fumble = false) {
    const T = tierTiming(empty, tierName, fumble), P = TIER_POSES[tierName];
    const id = `p9_01_reload_${empty ? 'empty' : 'tactical'}_${tierName}${fumble ? '_fumble' : ''}`;
    const json = base(id, `P9 ${empty ? 'empty' : 'tactical'} reload, ${tierName} tier${fumble ? ', fumbled' : ''} (gun/${id};`
        + ' docs/dev/gun_proficiency_v1_plan.md 4): the gun is worked ' + (tierName === 'novice' ? 'close to the body, tipped up to look into the'
        + ' magwell' : 'at the lower chest') + ', the old magazine drops, a fresh one comes from the belt pouch and is guided in'
        + (fumble ? ' crooked, stops on the edge, comes out and goes in on the second try' : '') + (tierName === 'novice' ? ', then a second look' : '')
        + (empty ? '; the support hand racks the slide overhand' : '') + '; back to the chest ready. Same beats and length as the first-person'
        + ' clip. Written by tools/author-p9-01-reload-actions.mjs.', T.end);
    const gunKeys = [k(0, CHEST_READY, 0), k(0, P.work, T.up), k(T.up + 0.06, P.meet, (fumble ? T.bump : T.seat) - 0.02 - T.up - 0.06)];
    if (fumble) gunKeys.push(k(T.out, P.work, T.again - T.out), k(T.again + 0.04, P.meet, T.seat - 0.06 - T.again));
    gunKeys.push(k(T.seat, P.hold, 0.14));
    if (T.lookAt != null) gunKeys.push(k(T.lookAt, P.look, 0.18));
    if (empty) { const r0 = T.lookEnd != null ? T.lookEnd + 0.02 : T.seat + 0.16; gunKeys.push(k(r0, P.rack, T.grab - r0)); }
    gunKeys.push(k(T.back, CHEST_READY, T.backEnd - T.back));
    // the fresh magazine: from the pouch when the hand gets there, up past the chest, into the magwell
    const atPouch = Math.max(0.28, T.drop - 0.04);
    const crooked = {rot: CROOKED};
    const magKeys = [k(0, POUCH, 0, {hidden: true}), k(atPouch, POUCH, 0),
        k(atPouch + 0.02, keyAt(MAG, 'chest', 'base', P.mid, 26, 18, -40), T.enter - atPouch - 0.02),
        k(T.enter + 0.02, inGun(MAG_ENTRY, fumble ? crooked : {}), T.align - T.enter - 0.02)];
    // as the first-person fumblePath: crooked in to the edge, it will not go, out, straightened, in again
    if (fumble) magKeys.push(k(T.align, inGun(BUMP_DEPTH, crooked), T.bump - T.align), k(T.out, inGun(OUT_DEPTH, crooked), 0.16),
        k(T.out + 0.16, inGun(MAG_ENTRY), T.again - T.out - 0.16));
    const pushFrom = fumble ? T.again : T.align;
    magKeys.push(k(pushFrom, inGun(MAG_PART), T.seat - 0.12 - pushFrom), k(T.seat - 0.04, inGun(0), 0.04), k(T.seat, inGun(0), 0, {hidden: true}));
    json.props = [
        {id: 'p9_01', keys: gunKeys},
        {id: 'p9_01_mag', keys: magKeys},
        {id: 'p9_01_mag_old', keys: [k(0, inGun(0), 0, {hidden: true}), k(T.drop, inGun(0), 0), k(T.drop, inGun(MAG_CLEAR), 0.08)]},
    ];
    if (empty) json.props.push({id: 'p9_01_rack', keys: [k(0, onSlide(SLIDE_LOCK), 0), k(T.pull, onSlide(SLIDE_LOCK + 0.08), T.pulled - T.pull),
        k(T.let, onSlide(SLIDE_LOCK + 0.08), 0), k(T.let, onSlide(0), 0.03)]});
    // the support hand lets go as the gun turns (as the trained reload), meets the magazine at the pouch, and takes the grip
    // again while the gun comes back to the chest ready
    const left = [{at: 0, target: 'prop:p9_01:grip_l', blend: 0, roll: 0, pole: POLE_L},
        {at: 0.02, target: 'prop:p9_01_mag:base', blend: atPouch - 0.02, roll: 0, pole: POLE_L_LOW}];
    if (empty) left.push({at: T.seat + 0.04, target: 'prop:p9_01_rack:hand', blend: T.grab - T.seat - 0.04, roll: 0, pole: POLE_L},
        {at: T.back, target: 'prop:p9_01:grip_l', blend: T.backEnd - 0.1 - T.back, roll: 0, pole: POLE_L});
    else left.push({at: Math.max(T.seat + 0.04, T.back - 0.06), target: 'prop:p9_01:grip_l', blend: T.backEnd - 0.1 - Math.max(T.seat + 0.04, T.back - 0.06), roll: 0, pole: POLE_L});
    json.hands = {right: [{at: 0, target: 'prop:p9_01:grip_r', blend: 0, roll: 0, pole: POLE_R}], left};
    json.events = [];
    const start = rootKeyOf(json, 2, T.drop + 0.08);
    json.props[2].keys.push(...fall(T.drop + 0.08, start, [-260, 40, 120]));
    return [id, json];
}

// The experienced ADS reload (docs/dev/p9_reload_inspect_v1.md 5.1): the trained ADS action on the experienced ADS beats; every key
// time goes through the same warp as the first-person keys (trained beat -> experienced beat), blends by their end times.
const ADS_WARP = {
    tactical: [[0, 0], [0.22, 0.3], [0.24, 0.33], [0.42, 0.62], [0.62, 0.92], [0.86, 1.2], [1.28, 1.66], [1.7, 2.3]],
    empty: [[0, 0], [0.22, 0.3], [0.24, 0.32], [0.46, 0.66], [0.66, 0.96], [0.9, 1.24], [1.0, 1.36], [1.44, 1.84], [2.0, 2.7]],
};
function warpAt(pairs, t) {
    for (let i = 1; i < pairs.length; i++) if (t <= pairs[i][0] + 1e-9) {
        const [a0, b0] = pairs[i - 1], [a1, b1] = pairs[i];
        return b0 + (b1 - b0) * (t - a0) / (a1 - a0);
    }
    return pairs.at(-1)[1];
}
function tierAdsReload(empty, tierName) {
    const W = ADS_WARP[empty ? 'empty' : 'tactical'];
    const [trainedId, trained] = reload(empty, true);
    const id = `p9_01_reload_${empty ? 'empty' : 'tactical'}_${tierName}_ads`;
    const json = JSON.parse(JSON.stringify(trained));
    const warp = key => { const a = warpAt(W, key.at), b = warpAt(W, key.at + key.blend); key.at = +a.toFixed(4); key.blend = +(b - a).toFixed(4); };
    for (const prop of json.props) prop.keys.forEach(warp);
    json.hands.left.forEach(warp);
    json.about = json.about.replace(`reload while aiming (gun/${trainedId}`, `reload while aiming, ${tierName} tier (gun/${id}`)
        .replace('2.7, 3)', '2.7, 5.1)').replace('Same beats as the first-person clip', 'The trained ADS action on the slower beats of the first-person clip');
    json.length = +warpAt(W, trained.length).toFixed(3);   // back on the sights, as the trained action
    return [id, json];
}

// ---------- write / check ----------
const text = o => JSON.stringify(o, null, 2) + '\n';
const outputs = [
    ['player_props/p9_01_mag.json', MAG], ['player_props/p9_01_mag_old.json', OLD], ['player_props/p9_01_rack.json', RACK],
    ...[reload(false), reload(true), inspect(false), inspect(true), inspectNoMag(false), inspectNoMag(true), reload(false, true), reload(true, true), unload(false), unload(true),
        ...['experienced', 'novice'].flatMap(tier => [tierReload(false, tier), tierReload(true, tier)]),
        tierReload(false, 'novice', true), tierReload(true, 'novice', true), tierAdsReload(false, 'experienced'), tierAdsReload(true, 'experienced')].map(([id, json]) => ['player_actions/gun/' + id + '.json', json]),
];
const check = process.argv.includes('--check');
let differ = 0;
for (const [rel, obj] of outputs) {
    const file = path.join(A, rel), t = text(obj);
    if (check) { const same = fs.existsSync(file) && fs.readFileSync(file, 'utf8').replace(/\r\n/g, '\n') === t; if (!same) { differ++; console.log('DIFFERS ' + rel); } }
    else fs.writeFileSync(file, t);
}
if (check) { console.log(differ ? differ + ' differ' : 'up to date'); process.exit(differ ? 1 : 0); }

// ---------- per-frame checks (ActionCheck's rules: hand off its target, wrist, forearm twist; gun and magazines vs the torso) ----------
function outside(p, b) {
    const dx = Math.max(b[0] - p[0], p[0] - b[1]), dy = Math.max(b[2] - p[1], p[1] - b[3]), dz = Math.max(b[4] - p[2], p[2] - b[5]);
    const o = Math.hypot(Math.max(dx, 0), Math.max(dy, 0), Math.max(dz, 0));
    return o > 0 ? o : Math.max(dx, dy, dz);
}
const corners = def => { const [lo, hi] = def.bounds; return Array.from({length: 8}, (_, i) => [i & 1 ? hi[0] : lo[0], i & 2 ? hi[1] : lo[1], i & 4 ? hi[2] : lo[2]])
    .map(p => { const kk = 16 * def.scale; return [p[0] * kk, -p[1] * kk, -p[2] * kk]; }); };
const TORSO = [-4, 4, 0, 12, -2, 2], HEAD = [-4, 4, -8, 0, -4, 4];
const worst = {};
for (const [rel, json] of outputs.filter(([r]) => r.startsWith('player_actions'))) {
    const action = asAction(json), name = path.basename(rel, '.json'), bad = [];
    let wristMax = 0, twistMax = 0, offMax = 0, clearMin = Infinity, clearAt = '';
    let armMin = Infinity, armAt = '';
    for (const pitch of [0, -30, 30, -45, 45]) for (let t = 0; t <= json.length + 1e-6; t += 0.02) {
        const res = core.solveFrame(poser, {action, seconds: t, time: t, pitch});
        const ro = core.readouts(res);
        for (const side of ['right', 'left']) {
            const r = ro[side];
            if (r.mode !== 'ik' || r.w < 0.5) continue;
            wristMax = Math.max(wristMax, r.wrist); twistMax = Math.max(twistMax, r.twist); if (r.w > 0.99) offMax = Math.max(offMax, r.off);
            if ((r.w > 0.99 && r.off > 0.6) || r.wrist > 50 || r.twist > 30) bad.push(`${t.toFixed(2)}/${pitch} ${side} off ${r.off.toFixed(2)} wrist ${r.wrist.toFixed(0)} twist ${r.twist.toFixed(0)}`);
        }
        const inv = tf.inv(res.pose.world[rig.chest]), head = tf.inv(res.pose.world[rig.head]);
        // ActionCheck's arm rule: forearm and hand centre lines >= 2 px outside the chest box (the skin around them); as its
        // scenes, level look and away from the chest ready at both ends (the idle's own arms sit closer)
        if (pitch === 0 && t > 0.3 && t < json.length - 0.6) for (const right of [true, false]) {
            const upper = right ? rig.upperArmR : rig.upperArmL, fore = right ? rig.forearmR : rig.forearmL, hand = right ? rig.handR : rig.handL;
            for (let y = 6; y <= core.DISTAL_Y; y += 0.5) {
                const p = tf.apply(inv, res.pose.point(y < 8 ? fore : hand, v3.add(rig.pivot[upper], [core.centreX(right, false), y, 0])));
                const o = outside(p, TORSO);
                if (o < armMin) { armMin = o; armAt = `${right ? 'R' : 'L'} ${t.toFixed(2)}/${pitch}`; }
                if (o < 2) bad.push(`${t.toFixed(2)}/${pitch} ${right ? 'R' : 'L'} arm ${o.toFixed(2)} px from the chest box`);
            }
        }
        json.props.forEach((p, i) => {
            if (!res.props[i] || res.hidden[i]) return;
            for (const c of corners(defs[p.id])) {
                const w = tf.apply(res.props[i], c), o = Math.min(outside(tf.apply(inv, w), TORSO), outside(tf.apply(head, w), HEAD));
                if (o < clearMin) { clearMin = o; clearAt = `${p.id} ${t.toFixed(2)}/${pitch}`; }
            }
        });
    }
    worst[name] = {offMax: +offMax.toFixed(2), wristMax: +wristMax.toFixed(0), twistMax: +twistMax.toFixed(0), clearMin: +clearMin.toFixed(2), clearAt, armMin: +armMin.toFixed(2), armAt,
        bad: bad.length, firstBad: process.env.ALL ? bad : bad.slice(0, 12)};
}
console.log(JSON.stringify(worst, null, 1));
