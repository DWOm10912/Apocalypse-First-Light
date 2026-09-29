// BR51-01 drum empty reload (clip reload_empty_drum): composed from the two authored reloads, because the authored
// reload_empty strips the empty magazine by striking it with the new one and flings it away, which a drum cannot do.
//   node tools/build-br51-drum-reload.mjs          -> writes the clip into animations/br51_01.animation.json
//   node tools/build-br51-drum-reload.mjs --check  -> verifies the clip is up to date
// Timeline (seconds):
//   0 .. T_CUT           reload_tactical as authored: the hand pulls the empty drum down, swaps it off screen and seats
//                        the new drum (magazine click at 1.4) - except the bolt, which stays locked back (empty gun)
//   T_CUT .. +BLEND      linear blend of every channel from the tactical pose to reload_empty's pose at E_CUT
//   then                 reload_empty from E_CUT to its end (hand to the bolt catch, bolt release, settle), shifted
// Sounds: tactical_1 (0.3) and tactical_2 (1.4, drum seated) from the tactical part, reload_empty_4 (bolt release)
// from the empty tail. Keyframes of both parts are copied untouched (their lerp modes kept); the two seam keyframes are
// linear. The composed clip lives only in the runtime animation file (the editable Blockbench source keeps the two
// authored reloads); an authored reload_empty_drum can later replace it under the same name.
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const FILE = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/animations/br51_01.animation.json');
export const CLIP = 'reload_empty_drum', T_CUT = 1.6333, E_CUT = 1.5, BLEND = 0.2;
export const SHIFT = +(T_CUT + BLEND - E_CUT).toFixed(4);
const HOLD_FROM_EMPTY = new Set(['bolt']);   // channels that follow reload_empty's E_CUT pose during the tactical part
const DEFAULT = {position: [0, 0, 0], rotation: [0, 0, 0], scale: [1, 1, 1]};

const text = fs.readFileSync(FILE, 'utf8'), crlf = text.includes('\r\n');
const anim = JSON.parse(text), T = anim.animations.reload_tactical, E = anim.animations.reload_empty;
if (!T || !E) throw new Error('authored reloads missing');

// ---- keyframe access (GeckoLib / Bedrock runtime format) ----
const vec = k => Array.isArray(k) ? k : k.vector ?? k.post ?? k.pre;
const keyed = ch => ch && !Array.isArray(ch) && !ch.vector;
const keys = ch => Object.keys(ch).map(t => [+t, t]).sort((a, b) => a[0] - b[0]);
/** Linear sample (catmull-rom stretches are approximated by their chords; only used at the two seam times). */
function sample(ch, name, t) {
  if (!ch) return DEFAULT[name];
  if (!keyed(ch)) return vec(ch).map(Number);
  const K = keys(ch), post = k => vec(ch[k]).map(Number), pre = k => (ch[k].pre ?? vec(ch[k])).map(Number);
  const at = K.find(([k]) => Math.abs(k - t) < 1e-6);
  if (at) return post(at[1]);   // exactly on a key: the value the timeline continues from
  if (t <= K[0][0]) return pre(K[0][1]);
  for (let i = 0; i + 1 < K.length; i++) if (t <= K[i + 1][0]) {
    const a = post(K[i][1]), b = pre(K[i + 1][1]), u = (t - K[i][0]) / (K[i + 1][0] - K[i][0]);
    return a.map((v, j) => v + (b[j] - v) * u);
  }
  return post(K.at(-1)[1]);
}
const fmt = t => { const s = String(+t.toFixed(4)); return s.includes('.') ? s : s + '.0'; };
const round = v => v.map(x => +x.toFixed(5));

export const clip = {loop: false, animation_length: +(E.animation_length + SHIFT).toFixed(4), bones: {}, sound_effects: {}};
for (const bone of [...new Set([...Object.keys(T.bones), ...Object.keys(E.bones)])].sort()) {
  const out = {};
  for (const name of ['rotation', 'position', 'scale']) {
    const tc = T.bones[bone]?.[name], ec = E.bones[bone]?.[name];
    if (!tc && !ec) continue;
    const hold = HOLD_FROM_EMPTY.has(bone), head = hold ? null : tc;
    const k = {};
    // tactical part: authored keyframes up to the cut (or one held pose)
    if (keyed(head)) for (const [t, s] of keys(head)) { if (t < T_CUT - 1e-6) k[fmt(t)] = structuredClone(head[s]); }
    else k['0.0'] = {post: round(hold ? sample(ec, name, E_CUT) : sample(head, name, 0)), lerp_mode: 'linear'};
    // seam: tactical pose at the cut, then reload_empty's pose at E_CUT
    k[fmt(T_CUT)] = {post: round(hold ? sample(ec, name, E_CUT) : sample(head, name, T_CUT)), lerp_mode: 'linear'};
    k[fmt(T_CUT + BLEND)] = {post: round(sample(ec, name, E_CUT)), lerp_mode: 'linear'};
    // empty tail: authored keyframes after E_CUT, shifted
    if (keyed(ec)) for (const [t, s] of keys(ec)) { if (t > E_CUT + 1e-6) k[fmt(t + SHIFT)] = structuredClone(ec[s]); }
    // a channel that never changes stays a static vector
    const values = Object.values(k).map(v => JSON.stringify(vec(v).map(Number)));
    out[name] = values.every(v => v === values[0]) ? vec(Object.values(k)[0]).map(Number) : Object.fromEntries(Object.entries(k).sort((a, b) => +a[0] - +b[0]));
  }
  clip.bones[bone] = out;
}
for (const [t, s] of Object.entries(T.sound_effects ?? {})) if (+t < T_CUT) clip.sound_effects[fmt(+t)] = structuredClone(s);
for (const [t, s] of Object.entries(E.sound_effects ?? {})) if (+t > E_CUT) clip.sound_effects[fmt(+t + SHIFT)] = structuredClone(s);

const next = structuredClone(anim);
next.animations[CLIP] = clip;
let written = JSON.stringify(next, null, 2) + (text.endsWith('\n') || text.endsWith('\r\n') ? '\n' : '');
if (crlf) written = written.replace(/\n/g, '\r\n');
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  console.log(JSON.stringify({length: clip.animation_length, shift: SHIFT, bones: Object.keys(clip.bones).length, sounds: clip.sound_effects}));
  if (process.argv.includes('--check')) { if (written !== text) throw new Error('stale ' + path.relative(ROOT, FILE)); console.log('CHECK OK'); }
  else { fs.writeFileSync(FILE, written); console.log('wrote ' + path.relative(ROOT, FILE)); }
}
