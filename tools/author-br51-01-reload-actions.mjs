// BR-51 reload / inspect as player actions (third person; docs/native_guns/br51_animation_redo_v1.md 5.6): writes
//   player_props/br51_01_mag.json, br51_01_mag_old.json   the magazines out of the gun (drawn by the gun's own mag_out /
//                                                          empty_old_mag bones: SkeletonGunStance, NativeAnimatedWeaponRenderer)
//   player_props/br51_01_rack.json                        where the firing hand takes the operating rod handle (no look)
//   player_actions/gun/br51_01_reload_tactical.json, br51_01_reload_empty.json, br51_01_reload_tactical_ads.json,
//   br51_01_reload_empty_ads.json (5.7: started while aiming, the rifle kept shouldered),
//   br51_01_reload_<tactical|empty>_<experienced|novice|novice_fumble|experienced_ads>.json (5.8: the proficiency tiers), br51_01_inspect.json,
//   br51_01_inspect_empty.json, br51_01_inspect_no_mag.json, br51_01_inspect_empty_no_mag.json
// The no-magazine reloads play the full reload's action from NO_MAG_CUT on (SkeletonGunStance#action). Same beats and lengths
// as the first-person clips (tools/author-br51-01-v3-native-animations.mjs RELOAD / INSPECT_T), played from the clip's start
// at speed 1, so the gun's own clip (the action, the magazine in the gun) matches. Until 2026-10-27 the BR-51 had no actions:
// the body reached for the first-person clip's anchors and the support hand fell short (GunStance#supportReach, kept as the
// fallback). The references (MW 2019 third-person EBR-14): the rifle comes down off the shoulder, the butt to the front of
// the right hip, the muzzle up and across to the left, rolled so the magwell is before the chest; the support hand works
// between the magwell and the left front of the belt. Keyed in the chest frame from the idle's low ready.
//   node tools/author-br51-01-reload-actions.mjs            write, then print the per-frame checks
//   node tools/author-br51-01-reload-actions.mjs --check    compare instead of writing (exit 1 on a difference)
import fs from 'node:fs';
import path from 'node:path';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
import {MP} from './build-br51-01-v3-native.mjs';
import {RELOAD, INSPECT_T, INSPECT_NO_MAG_T, INSPECT_LENGTH, MECH, TIER_T, FUMBLE} from './author-br51-01-v3-native-animations.mjs';

const require = createRequire(import.meta.url);
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const core = require('./blockbench/afl_rig.js');
const {v3, q4, tf, rad, deg} = core;
const A = path.join(root, 'src/main/resources/assets/apocalypse_firstlight');
const read = p => JSON.parse(fs.readFileSync(p, 'utf8'));
const r3 = v => v.map(x => Math.round(x * 1000) / 1000 || 0);
const r6 = v => v.map(x => Math.round(x * 1e6) / 1e6 || 0);
const gunDef = read(path.join(A, 'player_props/br51_01.json'));
const S = gunDef.scale;
const {MAG_ROCK, MAG_UNHOOK, OP_HELD, OP_FULL, OP_HANDLE} = MECH;
const ENV = process.env.BR51_TP ? JSON.parse(process.env.BR51_TP) : {};   // pose overrides while tuning

// ---------- the props ----------
function magBounds(bone) {
    const mesh = read(path.join(A, 'meshes/br51_01_v3_native.aflmesh.json'));
    const geo = read(path.join(A, 'geo/br51_01_v3_native.geo.json'))['minecraft:geometry'][0].bones;
    const pivot = geo.find(b => b.name === bone).pivot.map(n => n / 16);
    const lo = [Infinity, Infinity, Infinity], hi = [-Infinity, -Infinity, -Infinity];
    for (const p of mesh.parts) if (p.bone === bone) for (const v of p.vertices) for (let i = 0; i < 3; i++) {
        lo[i] = Math.min(lo[i], v[i] + pivot[i]); hi[i] = Math.max(hi[i], v[i] + pivot[i]);
    }
    if (!(hi[0] > lo[0])) throw new Error('no mesh on ' + bone);
    return [r6(lo), r6(hi)];
}
// the 20-round magazine's base plate (gun px, seated): its body is x +-0.67, y 4.0-11.0, z 1.45-5.52
const MAG_BASE = [0, 4.0, 3.5];
const magProp = (bone, meshBone, what) => ({
    about: `The BR-51's ${what} as a player action's prop (docs/native_guns/br51_animation_redo_v1.md 5.6): the gun's own ${bone} bone,`
        + ' drawn by the gun renderer where the action places this prop (gun_part); its frame is the gun\'s (the magazine seated when it'
        + ' sits on the gun prop unturned), same scale. Points: top (the magazine pivot, its front lug in the receiver\'s front catch, which'
        + ' it rocks about), base (the base plate). Written by tools/author-br51-01-reload-actions.mjs.',
    items: [],
    gun_part: {gun: 'br51_01', bone},
    scale: S,
    bounds: magBounds(meshBone),
    points: {top: r6(MP.map(n => n / 16)), base: r6(MAG_BASE.map(n => n / 16))},
});
const MAG = magProp('mag_out', 'reload_magazine', 'fresh magazine'), OLD = magProp('empty_old_mag', 'empty_old_mag', 'old magazine');
const RACK = {
    about: 'Where the BR-51\'s firing hand takes the operating rod handle for the empty reload\'s rack (docs/native_guns/br51_animation_redo_v1.md'
        + ' 5.6): no look of its own; keyed on the gun prop at the handle, moved with the rod. Written by tools/author-br51-01-reload-actions.mjs.',
    items: [], scale: S, bounds: [[-0.01, -0.01, -0.01], [0.01, 0.01, 0.01]],
    points: {hand: r6(OP_HANDLE.map(n => n / 16))},
};

// ---------- placing ----------
const RX180 = [1, 0, 0, 0], RZ180 = [0, 0, 1, 0];
const local = (def, name) => core.propLocal(def, name);
/** The gun frame turned as a gun held in the model's axes would be: pitch + muzzle up, yaw + left, roll + top to the left. */
const turnOf = (pitch, yaw, roll) => q4.norm(q4.mul(q4.mul(RZ180, q4.mul(q4.mul(q4.y(rad(yaw)), q4.x(rad(pitch))), q4.z(rad(roll)))), RX180));
const keyAt = (def, frame, point, pos, pitch, yaw, roll, extra = {}) =>
    ({frame, point, pos: r3(pos), rot: r3(q4.eulerXYZ(turnOf(pitch, yaw, roll)).map(deg)), ...extra});
/** gun px (x right, y up, z back) to the gun prop's frame (px, its points' axes) */
const gunVec = v => [v[0] * S, -v[1] * S, -v[2] * S];
/** The magazine on the gun: rocked `a` degrees about its front lug (the rear down, the bottom forward), its pivot moved `o` gun px. */
const inGun = (a = 0, o = [0, 0, 0], extra = {}) => ({frame: 'prop:br51_01', point: 'top', pos: r3(v3.add(local(MAG, 'top'), gunVec(o))),
    rot: [r3([a])[0], 0, 0], ...extra});
const OUT = (far = 1) => inGun(MAG_ROCK, v3.mul(MAG_UNHOOK, far));   // rocked out and off the catch
// the fumble's first try (the first person's fumbleMag): pushed up nearly upright without its front lug in the catch, off to one
// side and rolled, stopped on the magwell edge
const CROOKED = () => { const key = inGun(MAG_ROCK * 0.3, v3.add(v3.mul(MAG_UNHOOK, 0.45), [0.7, 0, 0])); return {...key, rot: [key.rot[0], 0, 13]}; };
/** The rack point with the operating rod `op` gun px back. */
const onRod = op => ({frame: 'prop:br51_01', point: 'hand', pos: r3(v3.add(local(RACK, 'hand'), gunVec([0, 0, op]))), rot: [0, 0, 0]});
const idle = read(path.join(A, 'player_actions/gun/br51_01_idle.json')).prop.keys[0];
const READY = {frame: idle.frame, point: idle.point, pos: idle.pos, rot: idle.rot};   // = gun/br51_01_idle: the low ready
const gun = (grip, pitch, yaw, roll) => keyAt(gunDef, 'chest', 'grip_r', grip, pitch, yaw, roll);
const pose = (name, grip, pitch, yaw, roll) => { const o = ENV[name]; return o ? gun(o.slice(0, 3), o[3], o[4], o[5]) : gun(grip, pitch, yaw, roll); };
const k = (at, key, blend, extra = {}) => ({at: +at.toFixed(4), ...key, blend: +Math.max(0, blend).toFixed(4), ...extra});

// gun poses (chest frame: +x left, +y down, -z ahead; grip_r): the butt to the front of the right hip, the magwell before
// the chest (tactical), steeper for the empty reload; rolled back for the rack; the inspect's sides at chest height. Searched
// (2026-10-27) near the drawn poses for the hands: the support hand reaching the magazine (off <= 0.4 px), the magazines and
// the rifle clear of the torso, the arms off the chest. The firing hand is locked to the grip, so with the rifle tipped up its
// wrist sits at the rig's limit (60 degrees), as does the support hand under the handguard at the idle's low ready: both are
// the rifle grips' own turn on this body, kept out of the check (below).
const UP_T = pose('UP_T', [-1.77, 5.29, -7.01], 31.25, 38.88, -31.88);
const MEET_T = pose('MEET_T', [-1.35, 6.98, -6.4], 40.5, 46.5, -35);
const UP_E = pose('UP_E', [-2.05, 5.61, -6.8], 40, 38.5, -40.5);
const MEET_E = pose('MEET_E', [-1.7, 6.0, -6.4], 26, 37.5, -38.38);
const RACK_E = pose('RACK_E', [-2.9, 6.25, -6.0], 39.25, 31.5, 4.88);
const SHOW_L = pose('SHOW_L', [-1.2, 4.71, -7.4], 14, 50, 18);
const SHOW_R = pose('SHOW_R', [-1.18, 4.1, -6.09], 17.75, 40, 40);   // rolled top to the left: the right side (the op rod) up
const CHECK = pose('CHECK', [-1.8, 5.43, -7.0], 30, 41, -30);
const REST = pose('REST', [-5.0, 7.0, -6.6], 18, 20, -12);   // let down to the right while the magazine is looked at
const WELL = pose('WELL', [-1.46, 5.9, -7.2], 40, 35.5, -34);
// the pouch: left front of the belt (pelvis frame), the magazine upright, the rounds forward
const POUCH = keyAt(MAG, 'bone:pelvis', 'base', [5.0, 10.6, -4.8], 18, 0, 0);
const POUCH_OLD = keyAt(OLD, 'bone:pelvis', 'base', [5.0, 10.6, -4.8], 18, 0, 0);
const GROUND_Y = 23.6;   // root frame: just above the feet

// the old magazine falls from where it left the catch: free fall (16 px per metre), drawn as short legs
function fall(at, from, tumble) {
    const g = 9.81 * 16, keys = [];
    const ground = GROUND_Y - from.pos[1];
    const land = Math.sqrt(2 * Math.max(1, ground) / g);
    for (let i = 1; i <= 5; i++) {
        const t = land * i / 5, drop = 0.5 * g * t * t;
        keys.push(k(at + t - land / 5, {frame: 'root', point: 'top', pos: r3(v3.add(from.pos, [0, Math.min(drop, ground), -1.6 * t])),
            rot: r3(v3.add(from.rot, v3.mul(tumble, t)))}, land / 5));
    }
    const {frame, point, pos, rot} = keys.at(-1);
    keys.push(k(at + land + 0.02, {frame, point, pos, rot}, 0, {hidden: true}));
    return keys;
}
const rig = core.loadRig(read(path.join(A, 'rigs/afl_humanoid_v1.json')));
const poser = new core.Poser(rig, core.loadClips(rig, read(path.join(A, 'animations/player/locomotion.animation.json'))));
const defs = {br51_01: gunDef, br51_01_mag: MAG, br51_01_mag_old: OLD, br51_01_rack: RACK};
const asAction = json => core.actionFromJson(json, id => defs[id] || null, () => null);
function rootKeyOf(json, track, at, body = {}) {
    const res = core.solveFrame(poser, {action: asAction(json), seconds: at, time: at, ...body});
    const placed = res.props[track], def = defs[json.props[track].id];
    const inRoot = tf.mul(tf.inv(res.pose.world[rig.root]), placed);
    return {pos: r3(tf.apply(inRoot, local(def, 'top'))), rot: r3(q4.eulerXYZ(inRoot.q).map(deg))};
}

const POLE_R = [-0.35, 1, 0.45], POLE_L = [1, 0.5, 0], POLE_L_LOW = [0.6, 0.6, -0.3], POLE_R_RACK = [-0.6, 0.8, 0.2];

// ADS reload (round 2; docs/native_guns/br51_animation_redo_v1.md 5.7): view frame (the eye, turned with the look, held within
// ADS_VIEW_PITCH), grip_r. The third-person aim is the shouldered rifle (GunStance#onShoulder: the butt in the chest pocket, the
// bore along the view); offline it is the idle's low ready turned back up 30 degrees about the butt (the idle is that shouldered
// rifle turned 30 degrees muzzle down about it), at a level look, in the view frame. The rifle stays shouldered: it dips a
// little and cants so the magwell meets the support hand under the line of sight, as the first-person clip.
const BUTT = [0, 0.5, 1.67];   // the butt plate's middle (gun blocks)
// in game a rifle in the hands stands bladed: the chest 30 degrees right of the view (GunStance.Kind.RIFLE chestTwist,
// SkeletonGunStance#chestTwist), and the chest follows the look's pitch at the rifle's share (HumanoidPoser#chestPitch)
const BODY = {rifle: true, chestYaw: 30};
function shouldered() {
    const res = core.solveFrame(poser, {action: asAction(read(path.join(A, 'player_actions/gun/br51_01_idle.json'))), seconds: 0.5, time: 0.5, ...BODY});
    const P = res.prop ?? res.props[0], butt = tf.apply(P, gunVec(BUTT.map(n => n * 16)));
    const axis = v3.norm(q4.apply(P.q, [1, 0, 0]));
    let best = null;
    for (const sign of [1, -1]) {
        const r = q4.norm([...v3.mul(axis, Math.sin(rad(15 * sign))), Math.cos(rad(15 * sign))]);
        const q = q4.norm(q4.mul(r, P.q)), t = v3.add(butt, q4.apply(r, v3.sub(P.t, butt)));
        const muzzle = tf.apply({q, t}, local(gunDef, 'muzzle'));
        if (!best || muzzle[1] < best.muzzle[1]) best = {q, t, muzzle};   // posed y is down: the higher muzzle
    }
    const V = {q: q4.y(0), t: [0, core.eyeModelY(false), 0]};
    return {pos: tf.apply(tf.inv(V), tf.apply(best, local(gunDef, 'grip_r'))), q: q4.norm(q4.mul(q4.conj(V.q), best.q))};
}
const SHOULDERED = shouldered();
/** The shouldered aim turned (deg: + muzzle up, + muzzle left, + top to the left, about the grip) and moved (view px: +x right, +y down). */
const aimed = (pitch = 0, yaw = 0, roll = 0, d = [0, 0, 0]) => {
    const turn = q4.mul(q4.mul(q4.y(rad(-yaw)), q4.x(rad(-pitch))), q4.z(rad(roll)));
    return {frame: 'view', point: 'grip_r', pos: r3(v3.add(v3.add(SHOULDERED.pos, ADS_GIVE), d)), rot: r3(q4.eulerXYZ(q4.norm(q4.mul(turn, SHOULDERED.q))).map(deg))};
};
const ADS_VIEW_PITCH = ENV.VIEW_PITCH ?? [-30, 25];
// the whole aim nearer the body (view px: 0.7 down, 2 back), as GunStance#inReach brings a shouldered rifle back where the
// hands fall short (up to POCKET_GIVE 3 px, the butt into the chest): at the shouldered aim itself the support hand is
// short of the handguard and the rack hand of the handle
const ADS_GIVE = ENV.GIVE ?? [0, 0.7, 2.0];
const AIMED = aimed();
// dipped and canted top to the right (the magwell toward the support hand); for the rack canted the other way (the rod handle up)
const DIP_A = aimed(-3, 2, -10, [0.2, 0.9, 0.2]), MEET_A = aimed(-4, 3, -13, [0.1, 1.1, 0.2]), RACK_A = aimed(-2, -2, 8, [-0.3, 0.7, 0.2]);
function base(about, length) {
    return {format_version: 1, about, layer: 'action', mask: 'upper', priority: 60, first_person: 'view_model', blend: {in: 0.12, out: 0.2}, length};
}

// tier: a proficiency tier's reload (5.8; the first-person TIER_T beats, the same poses): experienced, novice, fumble (the
// novice's, the magazine crooked against the magwell edge on the first try) or experiencedAds
const TIER_SUFFIX = {experienced: '_experienced', novice: '_novice', fumble: '_novice_fumble', experiencedAds: '_experienced'};
function reload(empty, ads = false, tier = null) {
    const T = tier ? TIER_T[tier][empty ? 'empty' : 'tactical'] : RELOAD[(empty ? 'empty' : 'tactical') + (ads ? 'Ads' : '')];
    const id = (empty ? 'br51_01_reload_empty' : 'br51_01_reload_tactical') + (tier ? TIER_SUFFIX[tier] : '') + (ads ? '_ads' : '');
    const json = base(`BR-51 ${empty ? 'empty' : 'tactical'} reload (gun/${id}; docs/native_guns/br51_animation_redo_v1.md 5.1-5.2, 5.6): the rifle comes`
        + ' down off the shoulder, the butt to the front of the right hip, the muzzle up and across to the left, rolled so the magwell is'
        + ' before the chest; the support hand presses the latch, rocks the magazine forward off its catch and '
        + (empty ? 'lets it fall' : 'takes it down to the belt pouch at the left front') + '; a fresh one comes up from the pouch, its front lug'
        + ' is hooked into the catch and it is rocked back home; ' + (empty ? 'the support hand holds the handguard while the firing hand racks'
        + ' the operating rod off the bolt lock; ' : '') + 'back to the low ready. Same beats and length as the first-person clip; the own'
        + ' first person shows the view model. Written by tools/author-br51-01-reload-actions.mjs.', ads ? T.backEnd : T.end);
    if (ads) {
        json.about = `BR-51 ${empty ? 'empty' : 'tactical'} reload while aiming (gun/${id}; docs/native_guns/br51_animation_redo_v1.md 5.7): eyes stay on`
            + ' the target; the rifle stays shouldered (view frame) and only dips and cants so the magwell meets the support hand, which works'
            + ' between the magwell and the belt pouch by feel' + (empty ? '; canted back for the firing hand to rack the operating rod' : '')
            + '; then straight back onto the sights. Same beats as the first-person clip; ends where that one is back on the sights (the clip'
            + ' runs on to its full length) and blends out into the aim. Written by tools/author-br51-01-reload-actions.mjs.';
        json.view_pitch = ADS_VIEW_PITCH;
    }
    if (tier) json.about += ` Proficiency tier ${tier} (docs/native_guns/br51_animation_redo_v1.md 5.8): the first-person tier clip's beats`
        + (tier === 'fumble' ? '; the fresh magazine goes up crooked without its front lug in the catch, stops on the magwell edge, is taken'
            + ' back down and lined up, and goes in on the second try' : '') + '.';
    const [REST0, UP, MEET, RACK] = ads ? [AIMED, DIP_A, MEET_A, RACK_A] : empty ? [READY, UP_E, MEET_E, RACK_E] : [READY, UP_T, MEET_T];
    const gunKeys = [k(0, REST0, 0), k(0, UP, ads ? 0.25 : 0.32), k(T.unhook, MEET, T.entry - T.unhook)];
    if (empty) gunKeys.push(k(T.seat + 0.04, RACK, T.rhOn - T.seat - 0.04));
    gunKeys.push(k(T.back, REST0, T.backEnd - T.back));
    json.props = [
        {id: 'br51_01', keys: gunKeys},
        {id: 'br51_01_mag', keys: [k(0, POUCH, 0, {hidden: true}), k(T.gone, POUCH, 0),
            k(T.fetch, keyAt(MAG, 'chest', 'base', [1.6, 8.6, -6.8], 30, 20, -34), ((T.miss ?? T.entry) - T.fetch) * 0.45),
            k(T.fetch + ((T.miss ?? T.entry) - T.fetch) * 0.45, OUT(), ((T.miss ?? T.entry) - T.fetch) * 0.55),
            ...(T.miss != null ? [k(T.miss, CROOKED(), 0.28 * FUMBLE), k(T.miss + 0.6 * FUMBLE, OUT(1.5), 0.15 * FUMBLE),
                k(T.miss + 0.79 * FUMBLE, OUT(), T.entry - T.miss - 0.79 * FUMBLE)] : []),
            k(T.entry, inGun(MAG_ROCK), T.hook - T.entry), k(T.hook, inGun(0), T.seat - T.hook), k(T.seat, inGun(0), 0, {hidden: true})]},
        {id: 'br51_01_mag_old', keys: [k(0, inGun(0), 0, {hidden: true}), k(T.out0, inGun(0), 0), k(T.out0, inGun(MAG_ROCK), T.out1 - T.out0),
            k(T.out1, OUT(), T.unhook - T.out1),
            ...(empty ? [] : [k(T.unhook, POUCH_OLD, T.gone - T.unhook), k(T.gone, POUCH_OLD, 0, {hidden: true})])]},
    ];
    const left = [{at: 0, target: 'prop:br51_01:grip_l', blend: 0, roll: 0, pole: POLE_L},
        {at: 0.04, target: 'prop:br51_01_mag_old:base', blend: T.grab - 0.04, roll: 0, pole: POLE_L_LOW}];
    // tactical: the old magazine in the hand down to the pouch, the fresh one taken there; empty: let go, down to the pouch
    // (aimed: quicker to the pouch, as the falling magazine it lets go of is further out and the hand would fall short; a slower
    // tier gets there as fast and waits: the hand would lose the falling magazine)
    if (empty) left.push({at: T.unhook + 0.02, target: 'prop:br51_01_mag:base', blend: (ads ? 0.6 : 1) * Math.min(T.gone - T.unhook,
        RELOAD.empty.gone - RELOAD.empty.unhook), roll: 0, pole: POLE_L_LOW});
    else left.push({at: T.gone, target: 'prop:br51_01_mag:base', blend: 0.06, roll: 0, pole: POLE_L_LOW});
    left.push({at: T.seat + 0.04, target: 'prop:br51_01:grip_l', blend: T.regrip - T.seat - 0.04, roll: 0, pole: POLE_L});
    const right = [{at: 0, target: 'prop:br51_01:grip_r', blend: 0, roll: 0, pole: POLE_R}];
    if (empty) {
        right.push({at: T.rhOff, target: 'prop:br51_01_rack:hand', blend: T.rhOn - T.rhOff, roll: 0, pole: POLE_R_RACK},
            {at: T.rhBack, target: 'prop:br51_01:grip_r', blend: T.rhHome - T.rhBack, roll: 0, pole: POLE_R});
        // let go, the hand stays where the handle left it (aimed, the slammed-home handle is out of reach; the hip reloads, as
        // passed 2026-10-27, follow it home)
        json.props.push({id: 'br51_01_rack', keys: [k(0, onRod(OP_HELD), 0), k(T.pull, onRod(OP_FULL), T.pulled - T.pull),
            k(T.let, onRod(OP_FULL), 0), ...(ads ? [] : [k(T.let, onRod(0), 0.035)])]});
    }
    json.hands = {right, left};
    json.events = [];
    if (empty) {
        const start = rootKeyOf(json, 2, T.unhook, ads ? BODY : {});   // (the hip reloads' from the square chest, as passed 2026-10-27)
        json.props[2].keys.push(...fall(T.unhook, start, [-200, 30, 90]));
    }
    if (ads) for (const p of json.props) p.keys = p.keys.filter(key => key.at <= json.length);   // the action ends before these
    return [id, json];
}

function inspect(empty) {
    const T = INSPECT_T, id = empty ? 'br51_01_inspect_empty' : 'br51_01_inspect';
    const json = base(`BR-51 ${empty ? 'empty ' : ''}inspect (gun/${id}; docs/native_guns/br51_animation_redo_v1.md 5.3, 5.6): unhurried, both hands on`
        + ' the rifle: brought up before the chest to show its left side, turned to show its right side (the operating rod'
        + (empty ? ', the action held open' : '') + '), then the magazine check: rocked out and brought up before the face at the left, held'
        + ' by its base plate: one side, the top turned to the eyes (the round on the follower), the other side, each held, while the rifle'
        + ' rests low to the right in the firing hand; hooked and rocked back home; back to the low ready. Same beats and length as the'
        + ' first-person clip (INSPECT_T). Written by tools/author-br51-01-reload-actions.mjs.', INSPECT_LENGTH.full);
    const look = (pitch, yaw, roll, d = [0, 0, 0]) => keyAt(MAG, 'chest', 'base', v3.add([4.6, 3.0, -9.4], d), pitch, yaw, roll);
    json.props = [
        {id: 'br51_01', keys: [k(0, READY, 0), k(0.02, SHOW_L, T.up - 0.02), k(T.turn, SHOW_R, T.turned - T.turn),
            k(T.check, CHECK, T.checked - T.check), k(T.unhook, REST, T.show - T.unhook), k(T.showEnd, CHECK, T.entry - T.showEnd),
            k(T.back, READY, T.backEnd - T.back)]},
        {id: 'br51_01_mag', keys: [k(0, inGun(0), 0, {hidden: true}), k(T.out0, inGun(0), 0), k(T.out0, inGun(MAG_ROCK), T.out1 - T.out0),
            k(T.out1, OUT(), T.unhook - T.out1), k(T.unhook, look(-10, -65, -5), T.show - T.unhook),
            k(T.sideEnd, look(-50, -35, -2, [0.2, 0.2, 0.2]), T.top - T.sideEnd),       // the top to the eyes: the round on the follower
            k(T.topEnd, look(-12, 60, 4, [-0.2, 0, 0]), T.other - T.topEnd),            // the other side
            k(T.showEnd, OUT(), T.entry - T.showEnd), k(T.entry, inGun(MAG_ROCK), T.hook - T.entry), k(T.hook, inGun(0), T.seat - T.hook),
            k(T.seat, inGun(0), 0, {hidden: true})]},
    ];
    json.hands = {
        right: [{at: 0, target: 'prop:br51_01:grip_r', blend: 0, roll: 0, pole: POLE_R}],
        left: [{at: 0, target: 'prop:br51_01:grip_l', blend: 0, roll: 0, pole: POLE_L},
            {at: T.check - 0.15, target: 'prop:br51_01_mag:base', blend: T.grab - T.check + 0.15, roll: 0, pole: POLE_L_LOW},
            {at: T.seat + 0.04, target: 'prop:br51_01:grip_l', blend: T.regrip - T.seat - 0.04, roll: 0, pole: POLE_L}],
    };
    json.events = [];
    return [id, json];
}

// no magazine in the gun: the same sides, then tipped up to look into the empty magwell; both hands stay on
function inspectNoMag(empty) {
    const T = INSPECT_NO_MAG_T, id = empty ? 'br51_01_inspect_empty_no_mag' : 'br51_01_inspect_no_mag';
    const json = base(`BR-51 ${empty ? 'empty ' : ''}inspect with no magazine in the gun (gun/${id}; docs/native_guns/br51_animation_redo_v1.md 5.4,`
        + ' 5.6): unhurried, both hands on the rifle: its left side, its right side' + (empty ? ' (the action held open)' : '') + ', then tipped up'
        + ' to look into the empty magwell; back to the low ready. Same beats and length as the first-person clip (INSPECT_NO_MAG_T).'
        + ' Written by tools/author-br51-01-reload-actions.mjs.', INSPECT_LENGTH.noMag);
    json.props = [
        {id: 'br51_01', keys: [k(0, READY, 0), k(0.02, SHOW_L, T.up - 0.02), k(T.turn, SHOW_R, T.turned - T.turn),
            k(T.check, WELL, T.checked - T.check), k(T.back, READY, T.backEnd - T.back)]},
    ];
    json.hands = {
        right: [{at: 0, target: 'prop:br51_01:grip_r', blend: 0, roll: 0, pole: POLE_R}],
        left: [{at: 0, target: 'prop:br51_01:grip_l', blend: 0, roll: 0, pole: POLE_L}],
    };
    json.events = [];
    return [id, json];
}

// ---------- write / check ----------
const text = o => JSON.stringify(o, null, 2) + '\n';
const outputs = [
    ['player_props/br51_01_mag.json', MAG], ['player_props/br51_01_mag_old.json', OLD], ['player_props/br51_01_rack.json', RACK],
    ...[reload(false), reload(true), reload(false, true), reload(true, true),
        ...['experienced', 'novice', 'fumble'].flatMap(tier => [reload(false, false, tier), reload(true, false, tier)]),
        reload(false, true, 'experiencedAds'), reload(true, true, 'experiencedAds'), inspect(false), inspect(true), inspectNoMag(false), inspectNoMag(true)]
        .map(([id, json]) => ['player_actions/gun/' + id + '.json', json]),
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
// a hand on one of the rifle's own grips is locked to it (the prop's grips), so its wrist and twist are the grip's turn on this
// body: the idle's low ready already has the firing hand at 29 / 48 degrees and the support hand under the handguard at its
// limits, 2.5 px short (GunStance.Support places it in game). Those (and the hand turning onto or off a grip) are not counted; the
// palm off its grip is, past the idle's.
const GRIPS = new Set(['prop:br51_01:grip_r', 'prop:br51_01:grip_l']);
function onGrip(keys, t) {
    let i = 0;
    for (let j = 0; j < keys.length; j++) if (keys[j].at <= t + 1e-9) i = j;
    const cur = keys[i], prev = i ? keys[i - 1] : null, blending = prev && t < cur.at + cur.blend;
    return GRIPS.has(cur.target) || (blending && GRIPS.has(prev.target));   // on it, or turning off it
}
const idleAction = asAction(read(path.join(A, 'player_actions/gun/br51_01_idle.json')));
const idleOff = {}, idleClear = {};
for (const pitch of [0, -30, 30, -45, 45]) {
    const res = core.solveFrame(poser, {action: idleAction, seconds: 0.5, time: 0.5, pitch, ...BODY}), ro = core.readouts(res);
    idleOff[pitch] = {right: ro.right.off, left: ro.left.off};
    const inv = tf.inv(res.pose.world[rig.chest]), head = tf.inv(res.pose.world[rig.head]);
    idleClear[pitch] = Math.min(...corners(gunDef).map(c => { const w = tf.apply(res.prop ?? res.props[0], c); return Math.min(outside(tf.apply(inv, w), TORSO), outside(tf.apply(head, w), HEAD)); }));
}
const worst = {};
for (const [rel, json] of outputs.filter(([r]) => r.startsWith('player_actions'))) {
    const action = asAction(json), name = path.basename(rel, '.json'), bad = [];
    let wristMax = 0, twistMax = 0, offMax = 0, clearMin = Infinity, clearAt = '', armMin = Infinity, armAt = '', gripWristMax = 0;
    for (const pitch of [0, -30, 30, -45, 45]) for (let t = 0; t <= json.length + 1e-6; t += 0.02) {
        const res = core.solveFrame(poser, {action, seconds: t, time: t, pitch, ...BODY});
        const ro = core.readouts(res);
        for (const side of ['right', 'left']) {
            const r = ro[side];
            if (r.mode !== 'ik' || r.w < 0.5) continue;
            const grip = onGrip(json.hands[side], t), offLimit = grip ? Math.max(0.6, idleOff[pitch][side] + 0.3) : 0.6;
            if (grip) gripWristMax = Math.max(gripWristMax, r.wrist);
            else { wristMax = Math.max(wristMax, r.wrist); twistMax = Math.max(twistMax, r.twist); }
            if (r.w > 0.99 && !grip) offMax = Math.max(offMax, r.off);
            if ((r.w > 0.99 && r.off > offLimit) || (!grip && (r.wrist > 50 || r.twist > 30))) bad.push(`${t.toFixed(2)}/${pitch} ${side} off ${r.off.toFixed(2)} wrist ${r.wrist.toFixed(0)} twist ${r.twist.toFixed(0)}`);
        }
        const inv = tf.inv(res.pose.world[rig.chest]), head = tf.inv(res.pose.world[rig.head]);
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
            if (!res.props[i] || res.hidden[i] || p.id === 'br51_01_rack') return;
            for (const c of corners(defs[p.id])) {
                const w = tf.apply(res.props[i], c), o = Math.min(outside(tf.apply(inv, w), TORSO), outside(tf.apply(head, w), HEAD));
                const below = o - Math.min(0, idleClear[pitch]);   // the gun no deeper than at the idle's low ready
                if (below < clearMin) { clearMin = below; clearAt = `${p.id} ${t.toFixed(2)}/${pitch}`; }
            }
        });
    }
    worst[name] = {offMax: +offMax.toFixed(2), wristMax: +wristMax.toFixed(0), twistMax: +twistMax.toFixed(0), gripWristMax: +gripWristMax.toFixed(0), clearMin: +clearMin.toFixed(2), clearAt, armMin: +armMin.toFixed(2), armAt,
        bad: bad.length, firstBad: process.env.ALL ? bad : bad.slice(0, 8)};
}
console.log(JSON.stringify(worst, null, 1));
