// HR55 Native Rig V2 (Phase 1): source <-> runtime sync, then the TaCZ-era rig -> AFL Native rig semantics. Cube / GeckoLib
// geometry, UVs, texture, animations and feel unchanged (same method as tools/migrate-br51-native-rig-v2.mjs).
//   node tools/migrate-hr55-native-rig-v2.mjs   -> on the TaCZ-era files: syncs, migrates and writes them;
//                                                 on already migrated files: re-derives from git (HR55_RIG_BASE, default
//                                                 97e9270, the pre-migration commit) and only verifies the outputs
// 1) Sync (the runtime is the tested truth; the editable source had drifted):
//    - sight_anchor pivot: runtime (0, 11.18035, -2.10661) (rifle red dot calibration), source still (0, 16.28, -23.1)
//    - sound markers: the runtime uses registered events (apocalypse_firstlight:hr55_*), shoot has none (NativeGunActions
//      plays hr55_fire), inspect's marker sits at 0.0417 s; the source had raw names, a local file path and 0.025 s
//    - inspect / inspect_empty: the runtime keys were re-timed to the 1/24 s grid and thinned (e.g. 158 keys instead of
//      189, 9 left_hand_anchor scale keys instead of 514); every source channel that differs is rebuilt from the runtime
// 2) Rig (exact by construction: dropped levels have no rotation, no animation and no own cubes; renamed bones keep their
//    pivot and channels):
//      afl_equip_motion > root > handling (was root_ash12_1: the whole held weapon incl. both hands; per-clip constant offset)
//        handling > righthand > righthand_pos > right_hand_anchor
//        handling > gun_body (was root_ash12: the gun, animated against the hands in reloads / inspect)
//          gun_body > magazine (in-gun magazine; in reloads it is the magazine taken out / flung away) > mag_standard, bullet
//          gun_body > reload_magazine (was additional_magazine: the new magazine, shown in reloads only) > reload_mag_standard
//          gun_body > muzzle_default, t, bolt, sight (geometry, unchanged), sight_anchor, muzzle_anchor, muzzle_pos, ejection_anchor
//        handling > lefthand > lefthand_pos > left_hand_anchor
//    Dropped: mag_1, ash12, mag_and_lefthand (identity containers), positioning2 (anchor container), gun_and_righthand,
//    constraint (empty; its channels go), scope_pos, muzzle_flash, shell, laser_pos, grip_pos, positioning (+ thirdperson_hand,
//    ground, fixed) - none holds a cube. Unlike BR51 the left hand stays under handling: the author's constant handling
//    offset (2 deg roll) would otherwise have to be baked into its keys, which is not exact between keys.
// 3) mag_extended_1 (72 cubes, export=false) leaves the main source for src/main/blockbench/hr55_extended_magazine.bbmodel
//    (unchanged cubes / UVs / texture; origin = mag_standard's origin (0, 0, 0), the future HR55 magazine seat).
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const P = {
  src: 'src/main/blockbench/hr55.bbmodel',
  geo: 'src/main/resources/assets/apocalypse_firstlight/geo/hr55.geo.json',
  anim: 'src/main/resources/assets/apocalypse_firstlight/animations/hr55.animation.json',
  ext: 'src/main/blockbench/hr55_extended_magazine.bbmodel',
};
const read = p => fs.readFileSync(path.join(ROOT, p), 'utf8');
const RENAME = {root_ash12_1: 'handling', root_ash12: 'gun_body', additional_magazine: 'reload_magazine'};
const DISSOLVE = ['mag_1', 'ash12', 'mag_and_lefthand', 'positioning2'];     // identity containers: children move up one level
const DELETE = ['gun_and_righthand', 'constraint', 'positioning', 'scope_pos', 'muzzle_flash', 'shell', 'laser_pos', 'grip_pos'];   // cube-less helpers (anchors under them are rescued first)
const ANCHORS = ['sight_anchor', 'muzzle_anchor', 'muzzle_pos', 'ejection_anchor'];
const ANIMATED_HELPERS = ['constraint'];
const uuid = key => { const h = createHash('sha256').update('afl-hr55-native-rig-v2:' + key).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const clone = o => structuredClone(o);
const vec = v => { if (v == null) return null; if (Array.isArray(v)) return v.map(Number); if (typeof v === 'number') return [v, v, v]; if (typeof v === 'object') return vec(v.vector ?? v.post ?? v.pre); return null; };
const near = (a, b, e = 1e-4) => a.length === b.length && a.every((x, i) => Math.abs(x - b[i]) < e);
// Blockbench keyframe <-> runtime value: rotation x / y and position x flip on export
const toRuntime = (ch, p) => { const v = ['x', 'y', 'z'].map(k => +p[k]); return ch === 'rotation' ? [-v[0] || 0, -v[1] || 0, v[2]] : ch === 'position' ? [-v[0] || 0, v[1], v[2]] : v; };
const toSource = (ch, v) => toRuntime(ch, {x: v[0], y: v[1], z: v[2]});   // the flip is its own inverse
const runtimeEntries = rv => vec(rv) && (Array.isArray(rv) || rv.vector) ? [[0, rv]] : Object.entries(rv).map(([t, v]) => [+t, v]).sort((a, b) => a[0] - b[0]);

// ---------------- 1) sync the source to the runtime ----------------
function syncSource(s, geo, anim) {
  const groups = new Map(s.groups.map(g => [g.uuid, g])), byName = n => { const h = s.groups.filter(g => g.name === n); assert.equal(h.length, 1, 'unique group ' + n); return h[0]; };
  const report = {pivots: [], sounds: [], channels: []};
  // bone pivots (exported groups only; the audit found only sight_anchor)
  for (const b of geo['minecraft:geometry'][0].bones) {
    const g = byName(b.name), want = [-b.pivot[0] || 0, b.pivot[1], b.pivot[2]];
    if (!near(g.origin, want, 1e-5)) { report.pivots.push(`${b.name}: ${JSON.stringify(g.origin)} -> ${JSON.stringify(want)}`); g.origin = want; }
  }
  for (const a of s.animations) {
    const r = anim.animations[a.name]; assert(r, 'runtime clip ' + a.name);
    const A = a.animators || (a.animators = {});
    // sound markers
    const fxId = Object.keys(A).find(id => A[id].type === 'effect');
    const want = Object.entries(r.sound_effects || {}).map(([t, x]) => [+t, x.effect]).sort((x, y) => x[0] - y[0]);
    const have = fxId ? (A[fxId].keyframes || []).filter(k => k.channel === 'sound').map(k => [+k.time, k.data_points[0].effect]).sort((x, y) => x[0] - y[0]) : [];
    if (JSON.stringify(have) !== JSON.stringify(want)) {
      report.sounds.push(`${a.name}: ${JSON.stringify(have)} -> ${JSON.stringify(want)}`);
      const fx = fxId ? A[fxId] : (A.effects = {name: 'Effects', type: 'effect', keyframes: []});
      fx.keyframes = want.map(([t, e]) => ({channel: 'sound', data_points: [{effect: e, locator: '', file: ''}], uuid: uuid(`sound:${a.name}:${t}`), time: t, color: -1, interpolation: 'linear'}));
    }
    // bone channels
    for (const [bone, chs] of Object.entries(r.bones || {})) {
      const g = byName(bone), an = A[g.uuid] || (A[g.uuid] = {name: bone, type: 'bone', keyframes: []});
      for (const ch of ['rotation', 'position', 'scale']) {
        const rv = chs[ch], ks = (an.keyframes || []).filter(k => k.channel === ch).sort((x, y) => x.time - y.time);
        const entries = rv === undefined ? [] : runtimeEntries(rv);
        const same = entries.length === ks.length && ks.every((k, i) => Math.abs(entries[i][0] - k.time) < 1e-3 && near(vec(entries[i][1]), toRuntime(ch, k.data_points[0]), 1e-3));
        if (same) continue;
        report.channels.push(`${a.name}/${bone}/${ch}: ${ks.length} -> ${entries.length} keys`);
        an.keyframes = (an.keyframes || []).filter(k => k.channel !== ch).concat(entries.map(([t, v]) => {
          const [x, y, z] = toSource(ch, vec(v));
          return {channel: ch, data_points: [{x: String(x), y: String(y), z: String(z)}], uuid: uuid(`kf:${a.name}:${bone}:${ch}:${t}`), time: t, color: -1,
            interpolation: v && typeof v === 'object' && !Array.isArray(v) && v.lerp_mode === 'catmullrom' ? 'catmullrom' : 'linear'};
        }));
      }
    }
    // source-only animated channels (none expected once the runtime is complete)
    for (const an of Object.values(A)) if (an.type !== 'effect' && (an.keyframes || []).length && !(r.bones || {})[an.name]) { report.channels.push(`${a.name}/${an.name}: source-only channels dropped`); an.keyframes = []; }
  }
  return report;
}

// ---------------- 2) migrate the source ----------------
function migrateSource(s) {
  const groups = new Map(s.groups.map(g => [g.uuid, g])), elements = new Map(s.elements.map(e => [e.uuid, e]));
  const nodes = new Map(), parentOf = new Map();
  (function index(list, parent) { for (const n of list) if (typeof n !== 'string') { nodes.set(n.uuid, n); parentOf.set(n.uuid, parent); index(n.children, n); } })(s.outliner, null);
  const byName = name => { const hits = s.groups.filter(g => g.name === name); assert.equal(hits.length, 1, 'unique group ' + name); return hits[0]; };
  const node = name => nodes.get(byName(name).uuid);
  const detach = n => { const p = parentOf.get(n.uuid), list = p ? p.children : s.outliner; list.splice(list.indexOf(n), 1); };
  const attach = (n, parent, index = parent.children.length) => { parent.children.splice(index, 0, n); parentOf.set(n.uuid, parent); };
  const subtree = n => { const g = [n.uuid], e = []; for (const c of n.children) if (typeof c === 'string') e.push(c); else { const s2 = subtree(c); g.push(...s2.g); e.push(...s2.e); } return {g, e}; };
  const drop = n => { const {g, e} = subtree(n); detach(n); s.groups = s.groups.filter(x => !g.includes(x.uuid)); s.elements = s.elements.filter(x => !e.includes(x.uuid)); return {g, e}; };
  const animated = new Set(s.animations.flatMap(a => Object.values(a.animators || {}).filter(an => an.type !== 'effect' && (an.keyframes || []).length).map(an => an.name)));
  for (const n of [...DISSOLVE, ...DELETE]) {
    const g = byName(n); assert(!g.rotation || g.rotation.every(v => !v) || DELETE.includes(n), n + ' has a static rotation');
    assert(!animated.has(n) || ANIMATED_HELPERS.includes(n), n + ' is animated');
  }
  for (const n of DISSOLVE) assert(!node(n).children.some(c => typeof c === 'string'), n + ' owns cubes');

  // 3) the extended magazine reference out of the main source
  const ext = clone(node('mag_extended_1')), extTree = subtree(ext);
  const refGroups = extTree.g.map(id => { const x = clone(groups.get(id)); x.export = x.visibility = true; return x; });
  const refElements = extTree.e.map(id => { const x = clone(elements.get(id)); x.export = x.visibility = true; return x; });
  for (const x of refElements) for (const f of Object.values(x.faces)) assert(f.texture === 0 || f.texture === null, 'magazine faces use the HR55 texture');
  const ref = {meta: s.meta, name: 'hr55_extended_magazine', model_identifier: 'hr55_extended_magazine', visible_box: s.visible_box, resolution: s.resolution,
    elements: refElements, groups: refGroups, outliner: [ext], textures: [clone(s.textures[0])], animations: []};
  const removedExtended = drop(node('mag_extended_1'));

  // renames (groups keep their UUIDs, so the source animators follow them)
  for (const [from, to] of Object.entries(RENAME)) byName(from).name = to;
  // anchors straight under gun_body (world positions unchanged: every level in between is an identity)
  const gunBody = node('gun_body');
  for (const a of ANCHORS) { const n = node(a); detach(n); attach(n, gunBody); }
  const removedHelpers = DELETE.map(name => { const r = drop(node(name)); assert.equal(r.e.length, 0, name + ' holds no cube'); return r; });
  // identity containers dissolve: their children take their place
  for (const name of DISSOLVE) {
    const n = node(name), p = parentOf.get(n.uuid), list = p ? p.children : s.outliner, at = list.indexOf(n);
    const kids = n.children.slice(); detach(n);
    kids.forEach((k, i) => { list.splice(at + i, 0, k); if (typeof k !== 'string') parentOf.set(k.uuid, p); });
    s.groups = s.groups.filter(g => g.uuid !== n.uuid);
    removedHelpers.push({g: [n.uuid], e: []});
  }
  // animators of removed groups go (only constraint carried keys)
  const gone = new Set([...removedHelpers, removedExtended].flatMap(r => r.g)), ids = new Set(s.groups.map(g => g.uuid));
  for (const a of s.animations) for (const [id, an] of Object.entries(a.animators || {})) {
    if (an.type === 'effect') continue;
    if (gone.has(id) || !ids.has(id)) { assert(!(an.keyframes || []).length || ANIMATED_HELPERS.includes(an.name), `drop animated ${a.name}/${an.name}`); delete a.animators[id]; continue; }
    an.name = s.groups.find(g => g.uuid === id).name;
  }
  return {s, ref, removed: {extended: removedExtended, helpers: removedHelpers}};
}

// ---------------- runtime geo / animation (same operations) ----------------
function migrateGeo(geo, src) {
  const G = geo['minecraft:geometry'][0], old = new Map(G.bones.map(b => [b.name, b]));
  const inv = Object.fromEntries(Object.entries(RENAME).map(([a, b]) => [b, a]));
  const groups = new Map(src.groups.map(g => [g.uuid, g])), bones = [];
  (function walk(list, parent, hidden) {
    for (const n of list) {
      if (typeof n === 'string') continue;
      const g = groups.get(n.uuid), omit = hidden || g.export === false;
      if (!omit) { const base = old.get(g.name) ?? old.get(inv[g.name]); assert(base, 'runtime bone for ' + g.name);
        const b = clone(base); b.name = g.name; if (parent) b.parent = parent; else delete b.parent; bones.push(b); }
      walk(n.children, omit ? parent : g.name, omit);
    }
  })(src.outliner, null, false);
  G.bones = bones;
  return geo;
}
function migrateAnimation(json) {
  for (const a of Object.values(json.animations)) {
    const out = {};
    for (const [name, ch] of Object.entries(a.bones || {})) { if (ANIMATED_HELPERS.includes(name)) continue; out[RENAME[name] ?? name] = ch; }
    a.bones = out;
  }
  return json;
}

// ---------------- evaluation (GeckoLib-like; both rigs share it, so the equivalence is convention independent) ----------------
const mat = {
  I: () => [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1],
  mul: (a, b) => { const r = new Array(16).fill(0); for (let i = 0; i < 4; i++) for (let j = 0; j < 4; j++) for (let k = 0; k < 4; k++) r[i * 4 + j] += a[i * 4 + k] * b[k * 4 + j]; return r; },
  T: v => [1, 0, 0, v[0], 0, 1, 0, v[1], 0, 0, 1, v[2], 0, 0, 0, 1],
  S: v => [v[0], 0, 0, 0, 0, v[1], 0, 0, 0, 0, v[2], 0, 0, 0, 0, 1],
  R: e => { const [x, y, z] = e.map(d => d * Math.PI / 180), c = Math.cos, s = Math.sin;
    const rx = [1, 0, 0, 0, 0, c(x), -s(x), 0, 0, s(x), c(x), 0, 0, 0, 0, 1], ry = [c(y), 0, s(y), 0, 0, 1, 0, 0, -s(y), 0, c(y), 0, 0, 0, 0, 1], rz = [c(z), -s(z), 0, 0, s(z), c(z), 0, 0, 0, 0, 1, 0, 0, 0, 0, 1];
    return mat.mul(rz, mat.mul(ry, rx)); },
  pt: (m, p) => [0, 1, 2].map(i => m[i * 4] * p[0] + m[i * 4 + 1] * p[1] + m[i * 4 + 2] * p[2] + m[i * 4 + 3]),
};
function sample(ch, t, rest) {
  if (ch === undefined) return rest;
  if (Array.isArray(ch) || ch.vector) return vec(ch);
  const keys = runtimeEntries(ch);
  const pre = v => vec(v?.pre ?? v), post = v => vec(v?.post ?? v);
  if (t <= keys[0][0]) return pre(keys[0][1]);
  const last = keys[keys.length - 1]; if (t >= last[0]) return post(last[1]);
  const i = keys.findIndex((k, j) => j + 1 < keys.length && t >= k[0] && t < keys[j + 1][0]);
  const [t0, a] = keys[i], [t1, b] = keys[i + 1], A = post(a), B = pre(b), u = (t - t0) / (t1 - t0);
  return A.map((x, k) => x + (B[k] - x) * u);
}
function evaluate(geo, clip, t) {
  const bones = new Map(geo['minecraft:geometry'][0].bones.map(b => [b.name, b])), W = new Map();
  const world = name => { if (!name) return mat.I(); if (W.has(name)) return W.get(name); const b = bones.get(name), ch = clip?.bones?.[name] || {};
    const p = b.pivot, rot = sample(ch.rotation, t, [0, 0, 0]), pos = sample(ch.position, t, [0, 0, 0]), sc = sample(ch.scale, t, [1, 1, 1]), r0 = b.rotation || [0, 0, 0];
    const m = mat.mul(world(b.parent), [mat.T(pos), mat.T(p), mat.R(r0.map((v, i) => v + rot[i])), mat.S(sc), mat.T(p.map(v => -v))].reduce(mat.mul));
    W.set(name, m); return m; };
  const boxes = [];
  for (const b of bones.values()) for (const c of b.cubes || []) {
    let m = world(b.name);
    if (c.rotation) m = mat.mul(m, [mat.T(c.pivot), mat.R(c.rotation), mat.T(c.pivot.map(v => -v))].reduce(mat.mul));
    const det = m[0] * (m[5] * m[10] - m[6] * m[9]) - m[1] * (m[4] * m[10] - m[6] * m[8]) + m[2] * (m[4] * m[9] - m[5] * m[8]);
    if (Math.abs(det) < 1e-9) continue;
    const n = c.inflate || 0, lo = c.origin.map(v => v - n), hi = c.origin.map((v, i) => v + c.size[i] + n);
    const corners = []; for (let k = 0; k < 8; k++) corners.push(mat.pt(m, [k & 1 ? hi[0] : lo[0], k & 2 ? hi[1] : lo[1], k & 4 ? hi[2] : lo[2]]).map(v => (Math.round(v * 1000) / 1000 || 0).toFixed(3)).join(','));
    boxes.push(corners.sort().join('|') + '#' + JSON.stringify(c.uv));
  }
  return {boxes: boxes.sort(), world};
}

// ---------------- run ----------------
const disk = JSON.parse(read(P.src));
const migrated = !disk.groups.some(g => g.name === 'root_ash12_1');
const base = process.env.HR55_RIG_BASE || '97e9270';   // last commit with the TaCZ-era HR55 files (the migration landed in e184568)
const original = p => migrated ? execFileSync('git', ['show', `${base}:${p}`], {cwd: ROOT, encoding: 'utf8', maxBuffer: 1 << 28}) : read(p);
const src0 = JSON.parse(original(P.src)), geo0 = JSON.parse(original(P.geo)), anim0 = JSON.parse(original(P.anim));
const synced = clone(src0), sync = syncSource(synced, geo0, anim0);
const {s: src1, ref, removed} = migrateSource(clone(synced));
const geo1 = migrateGeo(clone(geo0), src1), anim1 = migrateAnimation(clone(anim0));
console.log('sync:', JSON.stringify({pivots: sync.pivots, sounds: sync.sounds.length, channels: sync.channels.length}));

// V1: source animations == runtime animations (every key: time, value, interpolation-independent) and sound markers
{
  let checked = 0;
  for (const a of src1.animations) {
    const r = anim1.animations[a.name], seen = new Set(); assert(r, 'runtime clip ' + a.name);
    assert(Math.abs((r.animation_length ?? 0) - (a.length ?? 0)) < 1e-4, 'length ' + a.name);
    for (const an of Object.values(a.animators || {})) {
      if (an.type === 'effect') { const fx = (an.keyframes || []).map(k => [+k.time, k.data_points[0].effect]).sort((x, y) => x[0] - y[0]);
        assert.deepEqual(fx, Object.entries(r.sound_effects || {}).map(([t, x]) => [+t, x.effect]).sort((x, y) => x[0] - y[0]), 'sounds ' + a.name); continue; }
      if (!(an.keyframes || []).length) continue; seen.add(an.name);
      for (const ch of ['rotation', 'position', 'scale']) {
        const ks = an.keyframes.filter(k => k.channel === ch).sort((x, y) => x.time - y.time), rv = r.bones[an.name]?.[ch];
        if (!ks.length) { assert.equal(rv, undefined, `${a.name}/${an.name}/${ch} runtime only`); continue; }
        const entries = runtimeEntries(rv);
        assert.equal(entries.length, ks.length, `${a.name}/${an.name}/${ch} key count`);
        ks.forEach((k, i) => { checked++; assert(Math.abs(entries[i][0] - k.time) < 1e-3 && near(vec(entries[i][1]), toRuntime(ch, k.data_points[0]), 1e-3), `${a.name}/${an.name}/${ch}@${k.time}`); });
      }
    }
    for (const b of Object.keys(r.bones)) assert(seen.has(b), `${a.name}: runtime-only bone ${b}`);
  }
  console.log('V1 source <-> runtime animation keys:', checked);
}
// V2: runtime geo == exported groups of the source (names, parents, pivots, rotations, cube counts and cubes)
{
  const groups = new Map(src1.groups.map(g => [g.uuid, g])), els = new Map(src1.elements.map(e => [e.uuid, e])), exp = new Map();
  (function walk(list, parent, hidden) { for (const n of list) { if (typeof n === 'string') continue; const g = groups.get(n.uuid), omit = hidden || g.export === false;
    if (!omit) exp.set(g.name, {parent, origin: g.origin, rotation: g.rotation, cubes: n.children.filter(c => typeof c === 'string').map(c => els.get(c)).filter(e => e.export !== false)});
    walk(n.children, omit ? parent : g.name, omit); } })(src1.outliner, null, false);
  const bones = geo1['minecraft:geometry'][0].bones;
  assert.deepEqual(bones.map(b => b.name).sort(), [...exp.keys()].sort(), 'geo bones == exported groups');
  const flat = c => [c.origin, c.size, c.pivot || [0, 0, 0], c.rotation || [0, 0, 0]].flat().map(Number);
  const sameCubes = (A, B) => { if (A.length !== B.length) return false; const used = new Set();   // tolerance pairing (export rounding)
    return A.every(c => { const x = flat(c), i = B.findIndex((d, j) => !used.has(j) && flat(d).every((v, k) => Math.abs(v - x[k]) < 2e-3)); if (i < 0) return false; used.add(i); return true; }); };
  const conv = e => ({origin: [-e.to[0], e.from[1], e.from[2]], size: e.to.map((v, i) => v - e.from[i]),
    ...(e.rotation && e.rotation.some(v => v) ? {pivot: [-e.origin[0] || 0, e.origin[1], e.origin[2]], rotation: [-e.rotation[0] || 0, -e.rotation[1] || 0, e.rotation[2]]} : {})});
  for (const b of bones) { const e = exp.get(b.name);
    assert.equal(b.parent ?? null, e.parent, 'parent ' + b.name);
    assert(near(b.pivot, [-e.origin[0] || 0, e.origin[1], e.origin[2]], 1e-5), 'pivot ' + b.name);
    assert(near((b.rotation || [0, 0, 0]).map(v => v || 0), (e.rotation || [0, 0, 0]).map((v, i) => (i < 2 ? -v : v) || 0)), 'rotation ' + b.name);
    assert(sameCubes(b.cubes || [], e.cubes.map(conv)), 'cubes ' + b.name); }
  console.log('V2 geo bones:', bones.length, 'cubes:', bones.reduce((n, b) => n + (b.cubes || []).length, 0));
}
// V3: clip set, lengths, loop modes and sound markers unchanged; required bones present; removed ones gone
{
  const names = new Set(geo1['minecraft:geometry'][0].bones.map(b => b.name));
  assert.deepEqual(Object.keys(anim1.animations), Object.keys(anim0.animations));
  for (const [clip, a] of Object.entries(anim1.animations)) {
    const o = anim0.animations[clip]; assert.equal(a.animation_length, o.animation_length); assert.equal(a.loop, o.loop); assert.deepEqual(a.sound_effects, o.sound_effects);
    for (const b of Object.keys(a.bones)) assert(names.has(b), `${clip} animates missing bone ${b}`);
  }
  for (const n of ['afl_equip_motion', 'root', 'handling', 'gun_body', 'magazine', 'mag_standard', 'bullet', 'reload_magazine', 'reload_mag_standard', 'bolt', 'righthand', 'righthand_pos',
    'right_hand_anchor', 'lefthand', 'lefthand_pos', 'left_hand_anchor', 'sight_anchor', 'muzzle_anchor', 'muzzle_pos', 'ejection_anchor', 'camera']) assert(names.has(n), 'bone ' + n);
  for (const n of [...Object.keys(RENAME), ...DISSOLVE, ...DELETE, 'mag_extended_1', 'thirdperson_hand', 'ground', 'fixed'])
    assert(!names.has(n) && !src1.groups.some(g => g.name === n), 'removed ' + n);
  for (const n of ANCHORS) assert.equal(geo1['minecraft:geometry'][0].bones.find(b => b.name === n).parent, 'gun_body', n + ' under gun_body');
  console.log('V3 clips:', Object.keys(anim1.animations).length);
}
// V4: identical visible geometry and anchors, every clip, every 1/60 s
{
  let samples = 0, visible = 0;
  const anchors = [['right_hand_anchor'], ['left_hand_anchor'], ['sight_anchor'], ['muzzle_anchor'], ['muzzle_pos'], ['ejection_anchor'], ['camera']];
  for (const clip of Object.keys(anim0.animations)) {
    const len = anim0.animations[clip].animation_length || 0;
    for (let t = 0; t <= len + 1e-9; t += 1 / 60) {
      const a = evaluate(geo0, anim0.animations[clip], t), b = evaluate(geo1, anim1.animations[clip], t);
      assert.deepEqual(b.boxes, a.boxes, `${clip}@${t.toFixed(3)} visible geometry`);
      for (const [o, n = o] of anchors) { const p = mat.pt(a.world(o), [0, 0, 0]), q = mat.pt(b.world(n), [0, 0, 0]);
        assert(p.every((v, i) => Math.abs(v - q[i]) < 1e-6), `${clip}@${t.toFixed(3)} anchor ${n}`); }
      samples++; visible = Math.max(visible, a.boxes.length);
    }
  }
  console.log('V4 samples:', samples, 'max visible cubes:', visible);
}
// V5: geometry / UV / texture untouched; the reference holds the original extended magazine cubes
{
  const key = e => JSON.stringify([e.from, e.to, e.origin, e.rotation, e.inflate, e.faces, e.export !== false]);
  const removedIds = new Set(removed.extended.e);
  assert.deepEqual(src1.elements.map(key).sort(), src0.elements.filter(e => !removedIds.has(e.uuid)).map(key).sort(), 'main source cubes');
  assert.equal(ref.elements.length, 72, 'extended magazine cubes');
  for (const e of ref.elements) { const o = src0.elements.find(x => x.uuid === e.uuid); assert.deepEqual([e.from, e.to, e.origin, e.rotation, e.faces], [o.from, o.to, o.origin, o.rotation, o.faces]); }
  assert.deepEqual(src1.textures, src0.textures);
  console.log('V5 cubes:', src0.elements.length, '->', src1.elements.length, `(-${removedIds.size} extended magazine reference)`);
}

const outputs = [[P.src, JSON.stringify(src1)], [P.geo, JSON.stringify(geo1, null, 2)], [P.anim, JSON.stringify(anim1, null, 2)], [P.ext, JSON.stringify(ref)]];
if (migrated) {
  // git autocrlf may check these out with CRLF: compare the content, not the line endings
  for (const [file, data] of outputs) assert.equal(read(file).replace(/\r\n/g, '\n'), data, 'stale ' + file);
  console.log(`CHECK OK (re-derived from ${base})`);
} else {
  for (const [file, data] of outputs) fs.writeFileSync(path.join(ROOT, file), data);
  console.log('wrote ' + outputs.map(([f]) => f).join(', '));
}
