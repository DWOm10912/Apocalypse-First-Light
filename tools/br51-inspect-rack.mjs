// BR51-01 inspect rack, re-timed to the empty reload's charging-handle sound (2026-09-29).
// The author's inspect pulled the bolt back slowly (3.458 -> 3.75 s), held it open until 4.208 s and let it go
// (-> 4.292 s), later voiced with two dedicated sounds (inspect_slide_back / inspect_slide_release). The user asked for the
// empty reload's sound instead, so the rack is re-timed to reload_empty_4 rather than the sound to the motion:
//   - the right hand still takes the charging handle at 3.375 s and the gun still turns and settles as authored (root,
//     camera and left hand are untouched); the bolt stays forward meanwhile;
//   - at the end of that settle the hand racks it in one quick motion synced to the file: small clicks 35-85 ms in = the
//     handle drawn to the rear stop, main impact 100-160 ms in = the bolt slamming home;
//   - the authored recoil of the gun (root 4.2917 s) and camera kick (4.25 -> 4.2917 s) now follow the slam.
// The sound key sits just below 4.15 s: the server cue tick is ceil(t * 20), and 4.15 * 20 is 83.000000000000014.
//   node tools/br51-inspect-rack.mjs           -> rewrites the inspect clip in animations/br51_01.animation.json
//   node tools/br51-inspect-rack.mjs --check   -> verifies it
// tools/build-br51-01-v2-mesh.mjs applies the same keys to the Blockbench source (applySource).
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const FILE = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/animations/br51_01.animation.json');
export const SOUND = 'apocalypse_firstlight:br51_01_reload_empty_4';
export const RACK = {sound: 4.149, pull0: 4.10, pullMid: 4.19, pull1: 4.225, rel0: 4.235, home: 4.26, off: 4.36};
const REPLACED = ['apocalypse_firstlight:br51_01_inspect_slide_back', 'apocalypse_firstlight:br51_01_inspect_slide_release', SOUND];   // SOUND: idempotent re-runs
// window of the authored rack: keys of bolt / righthand strictly inside it are replaced
const WIN = [3.375, 4.4583];
const T = RACK, HAND = [-10, -8, -0.25], TRAVEL = 4, MID = 2.1475;   // authored hand-on-handle pose; bolt travel; ease-in point
// runtime (GeckoLib) values, keyed by time
export const KEYS = {
  bolt: {
    position: [[T.pull0, [0, 0, 0]], [T.pullMid, [0, 0, MID]], [T.pull1, [0, 0, TRAVEL]], [T.rel0, [0, 0, TRAVEL]], [T.home, [0, 0, 0]]],
    rotation: [[T.pull0, [0, 0, 0]], [T.home, [0, 0, 0]]],
  },
  righthand: {   // rides the handle (it lives under handling, the bolt under gun_body: same frame, travel along +Z)
    position: [[T.pull0, HAND], [T.pullMid, [HAND[0], HAND[1], HAND[2] + MID]], [T.pull1, [HAND[0], HAND[1], HAND[2] + TRAVEL]],
      [T.rel0, [HAND[0], HAND[1], HAND[2] + TRAVEL]], [T.home, HAND], [T.off, [-9.37, -10.5, 5.25]]],
    rotation: [[T.pull0, [90, -360, -207.5]], [4.17, [90, -360, -200]], [T.pull1, [90, -360, -207.5]], [T.home, [90, -360, -207.5]]],
  },
};
const key = t => { const s = String(+t.toFixed(4)); return s.includes('.') ? s : s + '.0'; };
const inWin = t => t > WIN[0] + 1e-6 && t < WIN[1] - 1e-6;

/** Runtime clip -> re-timed rack (bolt channels replaced, righthand keys inside the window replaced, sounds swapped). */
export function applyRuntime(clip) {
  for (const [bone, chs] of Object.entries(KEYS)) for (const [ch, list] of Object.entries(chs)) {
    const old = clip.bones[bone][ch], keep = bone === 'bolt' ? [] : Object.entries(old).filter(([t]) => !inWin(+t));
    const merged = [...keep, ...list.map(([t, v]) => [key(t), v])].sort((a, b) => +a[0] - +b[0]);
    clip.bones[bone][ch] = Object.fromEntries(merged.map(([t, v]) => [t, Array.isArray(v) ? v.slice() : v]));
  }
  const fx = Object.entries(clip.sound_effects).filter(([, s]) => !REPLACED.includes(s.effect));
  fx.push([key(T.sound), {effect: SOUND}]);
  clip.sound_effects = Object.fromEntries(fx.sort((a, b) => +a[0] - +b[0]));
  return clip;
}
/** Blockbench source animation (Blockbench sign convention: position x and rotation x / y flip). */
export function applySource(anim, groupId, uuid) {
  const bb = (ch, v) => ch === 'position' ? [-v[0] || 0, v[1], v[2]] : ch === 'rotation' ? [-v[0] || 0, -v[1] || 0, v[2]] : v;
  for (const [bone, chs] of Object.entries(KEYS)) {
    const animator = anim.animators[groupId(bone)];
    for (const [ch, list] of Object.entries(chs)) {
      animator.keyframes = animator.keyframes.filter(k => k.channel !== ch || (bone !== 'bolt' && !inWin(k.time)));
      for (const [t, v] of list) animator.keyframes.push({channel: ch, data_points: [{x: String(bb(ch, v)[0]), y: String(bb(ch, v)[1]), z: String(bb(ch, v)[2])}],
        uuid: uuid(`inspect-rack:${bone}:${ch}:${t}`), time: t, color: -1, interpolation: 'linear'});
    }
    animator.keyframes.sort((a, b) => a.time - b.time || (a.channel < b.channel ? -1 : 1));
  }
  const fx = Object.values(anim.animators).find(a => a.type === 'effect');
  fx.keyframes = fx.keyframes.filter(k => !(k.channel === 'sound' && REPLACED.includes(k.data_points[0].effect)));
  fx.keyframes.push({channel: 'sound', data_points: [{effect: SOUND, locator: '', file: ''}], uuid: uuid('inspect-rack:sound'), time: T.sound, color: -1, interpolation: 'linear'});
  fx.keyframes.sort((a, b) => a.time - b.time);
  return anim;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const text = fs.readFileSync(FILE, 'utf8'), crlf = text.includes('\r\n'), anim = JSON.parse(text);
  applyRuntime(anim.animations.inspect);
  let written = JSON.stringify(anim, null, 2) + (text.endsWith('\n') ? '\n' : '');
  if (crlf) written = written.replace(/\n/g, '\r\n');
  if (process.argv.includes('--check')) { if (written !== text) throw new Error('stale ' + path.relative(ROOT, FILE)); console.log('CHECK OK'); }
  else { fs.writeFileSync(FILE, written); console.log('wrote ' + path.relative(ROOT, FILE), JSON.stringify(anim.animations.inspect.sound_effects)); }
}
