// BR-51 V3 magazine loading in third person (docs/native_guns/br51_01_v3_game_model_v1.md, magazine_system_v1.md 4):
// gun/br51_01_mag_load is the P9-01's gun/p9_01_mag_load (keyed in the AFL Rig plugin) with the BR-51's props:
//   player_props/br51_01_magazine.json  the held magazine (all three BR-51 magazines), at the gun's size on the body
//                                       (GunStance.Carried 0.307 x 1.25 = 0.38375), points from tools/build-br51-01-magazines.mjs
//   player_props/762x51mm_round.json    the round in the fingers, same size, pinch as the 9 mm one's (1.7 px behind the
//                                       base, 1.5 px off it on +z)
// The magazine keeps the P9's chest placement (by its grip point); the round's keys on the magazine move by the two lips'
// difference in the props' model frames (px), so it is pressed in at the BR-51's lips. The 30-round and the drum are held
// at the 20-round one's points: their item lift differs, so their lips sit 0.65 / 0.40 px (model) off the round's.
//   node tools/build-br51-01-mag-load.mjs [--check]
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const assets = path.join(root, 'src/main/resources/assets/apocalypse_firstlight');
const read = p => JSON.parse(fs.readFileSync(path.join(assets, p), 'utf8'));
const check = process.argv.includes('--check');
const r4 = n => +n.toFixed(4) || 0;

const SCALE = 0.38375;                                  // GunStance.Carried br51_01: 0.307 x 1.25
const K = 16 * SCALE;                                   // item units -> prop model px
// tools/build-br51-01-magazines.mjs, br51_01_magazine (item units)
const MAG = {bounds: [[0.45814, 0.27931, 0.49394], [0.54186, 0.72069, 0.74815]], top: [0.5, 0.72069, 0.62104], grip: [0.5, 0.5, 0.62104],
  base: [0.5, 0.27931, 0.62104]};
// client/AflStaticMeshItemClient: the 7.62 round stands on y 0.396628, 0.2067 tall, 0.035 across (meshes/762x51mm_round)
const ROUND = {base: 0.396628, height: 0.2067, radius: 0.0175};

const p9Mag = read('player_props/p9_01_magazine.json'), p9Round = read('player_props/9x19mm_round.json');
const local = (p, k) => [p[0] * k, -p[1] * k, -p[2] * k];
// hold_l: the P9's offset from its grip, in px, with the magazine's half width swapped
const p9K = 16 * p9Mag.scale, p9Half = (p9Mag.bounds[1][0] - p9Mag.bounds[0][0]) / 2 * p9K, half = (MAG.bounds[1][0] - MAG.bounds[0][0]) / 2 * K;
const sideGap = (p9Mag.points.grip[0] - p9Mag.points.hold_l[0]) * p9K - p9Half;
const lowFrac = (p9Mag.points.hold_l[1] - p9Mag.points.base[1]) / (p9Mag.points.top[1] - p9Mag.points.base[1]);
const backPx = (p9Mag.points.hold_l[2] - p9Mag.points.grip[2]) * p9K;
const holdL = [0.5 - (half + sideGap) / K, MAG.base[1] + lowFrac * (MAG.top[1] - MAG.base[1]), MAG.grip[2] + backPx / K].map(r4);

const magProp = {
  about: 'The BR-51 magazine in the hand while it is loaded (gun/br51_01_mag_load; docs/native_guns/magazine_system_v1.md 4, docs/native_guns/br51_01_v3_game_model_v1.md): the held magazine item as NativeMagazineRendering draws it (item block units: the magazine pivot, the front lug, at (0.5, 0.5 + lift / 16, 0.5), the lips up, the front at -z). Scale as the BR-51 on the body (GunStance.Carried 0.307 x 1.25), so it is the gun\'s own magazine size. Points from tools/build-br51-01-magazines.mjs (the 20-round one): top (the feed lips), grip (the middle of the body), base (the floorplate), hold_l (the left palm on the magazine\'s left side, low, as the P9-01\'s). The 30-round and the drum are held at the same points. Symmetry x. Written by tools/build-br51-01-mag-load.mjs.',
  items: ['br51_01_magazine', 'br51_01_extended_magazine', 'br51_drum_magazine_50'],
  scale: SCALE,
  symmetry: 'x',
  bounds: MAG.bounds,
  points: {top: MAG.top, grip: MAG.grip, base: MAG.base, hold_l: holdL},
};
const pinch = [0.5, r4(ROUND.base - 1.7 / K), r4(0.5 + 1.5 / K)];
const roundProp = {
  about: 'A loose 7.62x51 mm round in the fingers while a BR-51 magazine is loaded (gun/br51_01_mag_load): the round item as client/AflStaticMeshItemClient draws it (upright, base at y 0.396628, centred on x / z), at the BR-51 magazine prop\'s scale so it matches the magazine. Points: base, mid, tip, and pinch (1.7 px behind the base along the round, 1.5 px off it on +z, as the 9x19 mm round\'s). Symmetry x. Written by tools/build-br51-01-mag-load.mjs.',
  items: ['762x51mm_round'],
  scale: SCALE,
  symmetry: 'x',
  bounds: [[0.5 - ROUND.radius, ROUND.base, 0.5 - ROUND.radius], [0.5 + ROUND.radius, r4(ROUND.base + ROUND.height), 0.5 + ROUND.radius]].map(v => v.map(r4)),
  points: {base: [0.5, r4(ROUND.base), 0.5], mid: [0.5, r4(ROUND.base + ROUND.height / 2), 0.5], tip: [0.5, r4(ROUND.base + ROUND.height), 0.5], pinch},
};

const p9 = read('player_actions/gun/p9_01_mag_load.json');
const a = local(p9Mag.points.top, p9K), b = local(MAG.top, K), delta = b.map((v, i) => v - a[i]);
const action = structuredClone(p9);
action.about = 'Loading a BR-51 magazine by hand (weapon/NativeMagazineLoading; client/skeleton/SkeletonActions plays it from the loading tag on the held magazine): gun/p9_01_mag_load (its timing, hands and chest placement) with the BR-51 props, the round\'s keys on the magazine moved to the BR-51\'s lips. Written by tools/build-br51-01-mag-load.mjs; the source animation is src/main/blockbench/player_actions/p9_01_mag_load.bbmodel.';
action.afl_rig = {...p9.afl_rig, animation: 'gun.br51_01_mag_load', derived_from: 'gun.p9_01_mag_load'};
for (const track of action.props) {
  if (track.id === 'p9_01_magazine') track.id = 'br51_01_magazine';
  else if (track.id === '9x19mm_round') {
    track.id = '762x51mm_round';
    for (const key of track.keys) if (key.frame === 'prop:p9_01_magazine') { key.frame = 'prop:br51_01_magazine'; key.pos = key.pos.map((v, i) => r4(v + delta[i])); }
  } else throw new Error('unexpected prop ' + track.id);
}
for (const side of Object.values(action.hands)) for (const h of side) h.target = h.target.replace('9x19mm_round', '762x51mm_round');

const out = [['player_props/br51_01_magazine.json', magProp], ['player_props/762x51mm_round.json', roundProp], ['player_actions/gun/br51_01_mag_load.json', action]];
let stale = 0;
for (const [rel, data] of out) {
  const file = path.join(assets, rel), text = JSON.stringify(data, null, 2) + '\n';
  if (check) { if (!fs.existsSync(file) || fs.readFileSync(file, 'utf8').replace(/\r\n/g, '\n') !== text) { console.log('STALE ' + rel); stale++; } }
  else fs.writeFileSync(file, text);
}
if (stale) process.exit(1);
console.log(JSON.stringify({holdL, pinch, lipsDeltaPx: delta.map(r4), wrote: !check}));
