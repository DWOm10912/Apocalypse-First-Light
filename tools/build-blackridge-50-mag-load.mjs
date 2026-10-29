// Blackridge .50 V2 magazine loading in third person (docs/native_guns/blackridge_50_remake_v1.md 12, magazine_system_v1.md 4):
// gun/blackridge_50_mag_load is the P9-01's gun/p9_01_mag_load (keyed in the AFL Rig plugin) with the Blackridge's props,
// as tools/build-br51-01-mag-load.mjs does for the BR-51:
//   player_props/blackridge_50_magazine.json  the held magazine, at the pistols' size on the body (GunStance.Carried, the
//                                             P9's 0.49467), points from the straightened item mesh
//                                             (tools/build-blackridge-50-magazine.mjs: tilt 10.15 deg, lift 3.55866 px)
//   player_props/50_ae_round.json             the round in the fingers, same size, pinch as the 9 mm one's (1.7 px behind
//                                             the base, 1.5 px off it on +z)
// The magazine keeps the P9's chest placement (by its grip point); the round's keys on the magazine move by the two lips'
// difference in the props' model frames (px), so it is pressed in at the Blackridge's lips.
//   node tools/build-blackridge-50-mag-load.mjs [--check]
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {MP, ROUND_ANCHOR, mm as gunMm} from './build-blackridge-50-v2-native.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const assets = path.join(root, 'src/main/resources/assets/apocalypse_firstlight');
const read = p => JSON.parse(fs.readFileSync(path.join(assets, p), 'utf8'));
const check = process.argv.includes('--check');
const r4 = n => +n.toFixed(4) || 0;

const SCALE = 0.49467;                                  // GunStance.Carried blackridge_50 = the P9's
const K = 16 * SCALE;                                   // item units -> prop model px
const TILT = 10.15, LIFT = 3.55866;                     // AflItems BLACKRIDGE_50_MAGAZINE (tools/build-blackridge-50-magazine.mjs)
// a point of the magazine (px from MP, as built) in item units: straightened +TILT about x, the pivot at (0.5, 0.5 + lift/16, 0.5)
const c = Math.cos(TILT * Math.PI / 180), s = Math.sin(TILT * Math.PI / 180);
const item = p => [0.5 + p[0] / 16, 0.5 + (LIFT + p[1] * c - p[2] * s) / 16, 0.5 + (p[1] * s + p[2] * c) / 16];
const lo = [Infinity, Infinity, Infinity], hi = [-Infinity, -Infinity, -Infinity];
for (const part of read('meshes/blackridge_50_magazine.aflmesh.json').parts) for (const v of part.vertices) {
  const q = item(v.map(n => n * 16));
  for (let i = 0; i < 3; i++) { lo[i] = Math.min(lo[i], q[i]); hi[i] = Math.max(hi[i], q[i]); }
}
const rel = p => p.map((n, i) => n - MP[i]);
// the top round's middle (its base 1.14 px behind MP, 2.2 px long toward the muzzle), the base pad's middle
const lips = item(rel(ROUND_ANCHOR.map((n, i) => i === 2 ? n - 0.1376 * 8 : n))), basePad = item(rel(gunMm(0, 43.5, 0)));
const MAG = {bounds: [lo.map(r4), hi.map(r4)], top: [0.5, r4(hi[1]), r4(lips[2])], grip: [0.5, r4((lo[1] + hi[1]) / 2), r4((lo[2] + hi[2]) / 2)],
  base: [0.5, r4(lo[1]), r4(basePad[2])]};
// client/AflStaticMeshItemClient: the .50 AE round stands on y 0.431217, 0.1376 tall, 0.0466 across (meshes/50_ae_round)
const ROUND = {base: 0.431217, height: 0.1376, radius: 0.0233};

const p9Mag = read('player_props/p9_01_magazine.json');
const local = (p, k) => [p[0] * k, -p[1] * k, -p[2] * k];
// hold_l: the P9's offset from its grip, in px, with the magazine's half width swapped
const p9K = 16 * p9Mag.scale, p9Half = (p9Mag.bounds[1][0] - p9Mag.bounds[0][0]) / 2 * p9K, half = (MAG.bounds[1][0] - MAG.bounds[0][0]) / 2 * K;
const sideGap = (p9Mag.points.grip[0] - p9Mag.points.hold_l[0]) * p9K - p9Half;
const lowFrac = (p9Mag.points.hold_l[1] - p9Mag.points.base[1]) / (p9Mag.points.top[1] - p9Mag.points.base[1]);
const backPx = (p9Mag.points.hold_l[2] - p9Mag.points.grip[2]) * p9K;
const holdL = [0.5 - (half + sideGap) / K, MAG.base[1] + lowFrac * (MAG.top[1] - MAG.base[1]), MAG.grip[2] + backPx / K].map(r4);

const magProp = {
  about: 'The Blackridge .50 magazine in the hand while it is loaded (gun/blackridge_50_mag_load; docs/native_guns/magazine_system_v1.md 4, docs/native_guns/blackridge_50_remake_v1.md 12): the held magazine item as NativeMagazineRendering draws it, straightened (item block units: the magazine pivot at (0.5, 0.5 + lift / 16, 0.5), the lips up, the front at -z). Scale as the pistols on the body (GunStance.Carried, the P9-01\'s), so it is the gun\'s own magazine size. Points from the straightened item mesh: top (the feed lips, where a round is pressed in), grip (the middle of the body), base (the base pad), hold_l (the left palm on the magazine\'s left side, low, as the P9-01\'s). Symmetry x. Written by tools/build-blackridge-50-mag-load.mjs.',
  items: ['blackridge_50_magazine'],
  scale: SCALE,
  symmetry: 'x',
  bounds: MAG.bounds,
  points: {top: MAG.top, grip: MAG.grip, base: MAG.base, hold_l: holdL},
};
const pinch = [0.5, r4(ROUND.base - 1.7 / K), r4(0.5 + 1.5 / K)];
const roundProp = {
  about: 'A loose .50 AE round in the fingers while a Blackridge .50 magazine is loaded (gun/blackridge_50_mag_load): the round item as client/AflStaticMeshItemClient draws it (upright, base at y 0.431217, centred on x / z), at the magazine prop\'s scale so it matches the magazine. Points: base, mid, tip, and pinch (1.7 px behind the base along the round, 1.5 px off it on +z, as the 9x19 mm round\'s). Symmetry x. Written by tools/build-blackridge-50-mag-load.mjs.',
  items: ['50_ae_round'],
  scale: SCALE,
  symmetry: 'x',
  bounds: [[0.5 - ROUND.radius, ROUND.base, 0.5 - ROUND.radius], [0.5 + ROUND.radius, r4(ROUND.base + ROUND.height), 0.5 + ROUND.radius]].map(v => v.map(r4)),
  points: {base: [0.5, r4(ROUND.base), 0.5], mid: [0.5, r4(ROUND.base + ROUND.height / 2), 0.5], tip: [0.5, r4(ROUND.base + ROUND.height), 0.5], pinch},
};

const p9 = read('player_actions/gun/p9_01_mag_load.json');
const a = local(p9Mag.points.top, p9K), b = local(MAG.top, K), delta = b.map((v, i) => v - a[i]);
const action = structuredClone(p9);
action.about = 'Loading a Blackridge .50 magazine by hand (weapon/NativeMagazineLoading; client/skeleton/SkeletonActions plays it from the loading tag on the held magazine): gun/p9_01_mag_load (its timing, hands and chest placement) with the Blackridge props, the round\'s keys on the magazine moved to the Blackridge\'s lips. Written by tools/build-blackridge-50-mag-load.mjs; the source animation is src/main/blockbench/player_actions/p9_01_mag_load.bbmodel.';
action.afl_rig = {...p9.afl_rig, animation: 'gun.blackridge_50_mag_load', derived_from: 'gun.p9_01_mag_load'};
for (const track of action.props) {
  if (track.id === 'p9_01_magazine') track.id = 'blackridge_50_magazine';
  else if (track.id === '9x19mm_round') {
    track.id = '50_ae_round';
    for (const key of track.keys) if (key.frame === 'prop:p9_01_magazine') { key.frame = 'prop:blackridge_50_magazine'; key.pos = key.pos.map((v, i) => r4(v + delta[i])); }
  } else throw new Error('unexpected prop ' + track.id);
}
for (const side of Object.values(action.hands)) for (const h of side) h.target = h.target.replace('9x19mm_round', '50_ae_round');

const out = [['player_props/blackridge_50_magazine.json', magProp], ['player_props/50_ae_round.json', roundProp], ['player_actions/gun/blackridge_50_mag_load.json', action]];
let stale = 0;
for (const [relPath, data] of out) {
  const file = path.join(assets, relPath), text = JSON.stringify(data, null, 2) + '\n';
  if (check) { if (!fs.existsSync(file) || fs.readFileSync(file, 'utf8').replace(/\r\n/g, '\n') !== text) { console.log('STALE ' + relPath); stale++; } }
  else fs.writeFileSync(file, text);
}
if (stale) process.exit(1);
console.log(JSON.stringify({mag: MAG, holdL, pinch, lipsDeltaPx: delta.map(r4), wrote: !check}));
