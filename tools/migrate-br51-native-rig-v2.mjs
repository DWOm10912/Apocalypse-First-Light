// BR51-01 Native Rig V2 (Phase 1): TaCZ-era rig -> AFL Native rig semantics, Cube / GeckoLib geometry unchanged.
//   node tools/migrate-br51-native-rig-v2.mjs   -> migrates the TaCZ-era source + runtime files and writes them;
//                                                 on already migrated files it re-derives from git HEAD~ originals
//                                                 (BR51_RIG_BASE, default HEAD) and only verifies the outputs
// Exact by construction: every group involved has no static rotation, so channels move between bones with the same
// pivot at the same chain position, and identity helper levels are dropped. Verified per clip every 1/60 s: the world
// boxes of all visible cubes and the hand / attachment anchors are identical before and after.
// Rig:  root > handling (was gun_and_righthand) > gun_body (was br51_01) > magazine (in-gun; mag_standard + bullet),
//       empty_old_mag (was additional_magazine) > empty_old_mag_standard, bolt, anchors ...; handling > righthand ...
//       root > mag_out (copy of mag_and_lefthand's channels) > reload_magazine (was magazine_bullet) > reload_mag_standard, reload_bullet
//       root > lefthand (mag_and_lefthand's channels, its pivot) > lefthand_pos (old lefthand channels + pivot) > left_hand_anchor
// Magazines: mag_extended_2 / mag_extended_3 are extracted first to br51_extended_magazine.bbmodel / br51_drum_magazine.bbmodel
// (unchanged cubes / UVs / texture reference, origin = the gun's mag_standard seat like br51_extended_magazine_35), then
// mag_extended_1..3 leave the main source. Visibility: magazine is hidden in reload_empty / reload_tactical / inspect,
// reload_magazine is shown only there (explicit constant scale keys in all eight clips, like empty_old_mag_standard).
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const P = {
  src: 'src/main/blockbench/br51_01.bbmodel',
  geo: 'src/main/resources/assets/apocalypse_firstlight/geo/br51_01.geo.json',
  anim: 'src/main/resources/assets/apocalypse_firstlight/animations/br51_01.animation.json',
  ext: 'src/main/blockbench/br51_extended_magazine.bbmodel',
  drum: 'src/main/blockbench/br51_drum_magazine.bbmodel',
};
const read = p => fs.readFileSync(path.join(ROOT, p), 'utf8');
const RENAME = {gun_and_righthand: 'handling', br51_01: 'gun_body', additional_magazine: 'empty_old_mag', magazine_bullet: 'reload_magazine', shell: 'ejection_anchor'};
const DELETE = ['constraint', 'positioning2', 'positioning', 'view'];   // subtrees; none contains a cube (asserted)
const ANCHORS = ['sight_anchor', 'muzzle_anchor', 'muzzle_pos', 'ejection_anchor'];
const EXTENDED = ['mag_extended_1', 'mag_extended_2', 'mag_extended_3'];
const CARRIED = ['reload_empty', 'reload_tactical', 'inspect'];   // clips where the left hand carries the magazine
const COPY = {mag_standard: 'reload_mag_standard', hu2: 'reload_hu2', bullet: 'reload_bullet', 2: 'reload_bullet_2', 3: 'reload_bullet_3', 7: 'reload_bullet_7'};
const SEAT = [0, 9.41883, 4.52607];   // mag_standard origin: seat of every BR51 magazine attachment (source coordinates)
const uuid = key => { const h = createHash('sha256').update('afl-br51-native-rig-v2:' + key).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const clone = o => structuredClone(o);

// ---------------- source (.bbmodel) ----------------
function migrateSource(s) {
  const groups = new Map(s.groups.map(g => [g.uuid, g])), elements = new Map(s.elements.map(e => [e.uuid, e]));
  assert(s.groups.every(g => !g.children || !g.children.length), 'groups carry no hierarchy (outliner does)');
  const nodes = new Map(), parentOf = new Map();
  (function index(list, parent) { for (const n of list) if (typeof n !== 'string') { nodes.set(n.uuid, n); parentOf.set(n.uuid, parent); index(n.children, n); } })(s.outliner, null);
  const byName = name => { const hits = s.groups.filter(g => g.name === name); assert.equal(hits.length, 1, 'unique group ' + name); return hits[0]; };
  const node = name => nodes.get(byName(name).uuid);
  const detach = n => { const p = parentOf.get(n.uuid), list = p ? p.children : s.outliner; list.splice(list.indexOf(n), 1); };
  const attach = (n, parent, index = parent.children.length) => { parent.children.splice(index, 0, n); parentOf.set(n.uuid, parent); };
  const subtree = n => { const g = [n.uuid], e = []; for (const c of n.children) if (typeof c === 'string') e.push(c); else { const s2 = subtree(c); g.push(...s2.g); e.push(...s2.e); } return {g, e}; };
  const drop = n => { const {g, e} = subtree(n); detach(n); s.groups = s.groups.filter(x => !g.includes(x.uuid)); s.elements = s.elements.filter(x => !e.includes(x.uuid)); return {g, e}; };
  for (const g of s.groups) assert(!g.rotation || g.rotation.every(v => !v) || !['root', 'gun_and_righthand', 'br51_01', 'mag_and_lefthand', 'magazine_bullet', 'magazine', 'lefthand', 'lefthand_pos', 'righthand', 'righthand_pos', 'positioning2', ...ANCHORS.map(a => a === 'ejection_anchor' ? 'shell' : a)].includes(g.name), 'static rotation on rig group ' + g.name);

  // 1) magazine references out of the main source, unchanged cubes, seat-relative
  const extract = (name, file, label) => {
    const n = clone(node(name)), {g, e} = subtree(n);
    const shift = v => v.map((x, i) => +(x - SEAT[i]).toFixed(8));
    const gs = g.map(id => { const x = clone(groups.get(id)); x.origin = shift(x.origin); x.export = x.visibility = true; return x; });
    const es = e.map(id => { const x = clone(elements.get(id)); x.from = shift(x.from); x.to = shift(x.to); x.origin = shift(x.origin || [0, 0, 0]); x.export = x.visibility = true; return x; });
    for (const x of es) for (const f of Object.values(x.faces)) assert(f.texture === 0 || f.texture === null, 'magazine faces use the BR51 texture');
    return {file, label, cubes: es.length, data: {meta: s.meta, name: label, model_identifier: label, visible_box: s.visible_box, resolution: s.resolution,
      elements: es, groups: gs, outliner: [n], textures: [clone(s.textures[0])], animations: []}};
  };
  const refs = [extract('mag_extended_2', P.ext, 'br51_extended_magazine'), extract('mag_extended_3', P.drum, 'br51_drum_magazine')];
  const removedExtended = EXTENDED.map(name => drop(node(name)));

  // 2) renames (groups keep their UUIDs, so the source animators follow them)
  for (const [from, to] of Object.entries(RENAME)) byName(from).name = to;
  // 3) anchors straight under gun_body; TaCZ helpers out
  const gunBody = node('gun_body');
  for (const a of ANCHORS) { const n = node(a); detach(n); attach(n, gunBody); }
  const removedHelpers = DELETE.map(name => { const r = drop(node(name)); assert.equal(r.e.length, 0, name + ' holds no cube'); return r; });
  // 4) in-gun magazine under gun_body, carrying the top rounds
  const magazine = node('magazine'), bullet = node('bullet'), reload = node('reload_magazine');
  detach(magazine); attach(magazine, gunBody, gunBody.children.indexOf(node('empty_old_mag')));
  detach(bullet); attach(bullet, magazine);
  // 5) reload copies of the standard magazine and its rounds under reload_magazine
  const copyTree = n => {
    const g = clone(groups.get(n.uuid)); g.name = COPY[g.name]; assert(g.name, 'copy name'); g.uuid = uuid('group:' + g.name); s.groups.push(g);
    return {uuid: g.uuid, isOpen: n.isOpen, children: n.children.map(c => {
      if (typeof c !== 'string') return copyTree(c);
      const e = clone(elements.get(c)); e.uuid = uuid(`element:${g.name}:${c}`); s.elements.push(e); return e.uuid;
    })};
  };
  assert.equal(reload.children.length, 0);
  for (const n of [node('mag_standard'), bullet]) { const c = copyTree(n); nodes.set(c.uuid, c); attach(c, reload); }
  // 6) mag_and_lefthand split: mag_out carries reload_magazine, lefthand takes the carrier's pivot, lefthand_pos the hand's
  const M = byName('mag_and_lefthand'), L = byName('lefthand'), LP = byName('lefthand_pos'), mNode = node('mag_and_lefthand'), root = node('root');
  const magOut = clone(M); magOut.name = 'mag_out'; magOut.uuid = uuid('group:mag_out'); s.groups.push(magOut);
  const magOutNode = {uuid: magOut.uuid, isOpen: true, children: []}; nodes.set(magOut.uuid, magOutNode);
  attach(magOutNode, root, root.children.indexOf(mNode)); detach(reload); attach(reload, magOutNode);
  const lNode = node('lefthand'); detach(lNode); attach(lNode, root, root.children.indexOf(mNode) + 1);
  const handPivot = L.origin; L.origin = [...M.origin]; LP.origin = [...handPivot];
  assert.equal(mNode.children.length, 0); drop(mNode);

  // 7) animations: channels follow the rig
  const deleted = new Set([...removedExtended, ...removedHelpers].flatMap(r => r.g).concat(M.uuid));
  const keyframe = (template, anim, bone, value) => ({...clone(template), uuid: uuid(`kf:${anim}:${bone}:scale`), channel: 'scale', time: 0,
    interpolation: 'linear', data_points: [{x: String(value), y: String(value), z: String(value)}]});
  let template = null;
  for (const a of s.animations) for (const an of Object.values(a.animators || {})) for (const k of an.keyframes || []) if (!template && k.channel === 'scale' && an.name === 'empty_old_mag_standard') template = k;
  assert(template, 'constant scale keyframe template');
  const ids = new Set(s.groups.map(g => g.uuid));
  for (const a of s.animations) {
    const A = a.animators || (a.animators = {}), old = {M: A[M.uuid], L: A[L.uuid]};
    for (const [id, an] of Object.entries(A)) {
      if (an.type && an.type !== 'bone') continue;
      if (deleted.has(id) || !ids.has(id)) { assert(!(an.keyframes || []).length || ['constraint', 'bolt2', 'charger', 'mag_and_lefthand'].includes(an.name), `drop animated ${a.name}/${an.name}`); delete A[id]; continue; }
      an.name = groups.get(id)?.name ?? s.groups.find(g => g.uuid === id).name;
    }
    const withKeys = an => an && (an.keyframes || []).length;
    if (withKeys(old.M)) { A[L.uuid] = {...clone(old.M), name: 'lefthand', keyframes: old.M.keyframes.map(k => ({...clone(k), uuid: uuid(`kf-lefthand:${k.uuid}`)}))};
      A[magOut.uuid] = {...clone(old.M), name: 'mag_out', keyframes: old.M.keyframes.map(k => ({...clone(k), uuid: uuid(`kf-mag_out:${k.uuid}`)}))}; }
    else delete A[L.uuid];
    if (withKeys(old.L)) A[LP.uuid] = {...old.L, name: 'lefthand_pos'}; else delete A[LP.uuid];
    const show = (group, value) => {
      const an = A[group.uuid] || (A[group.uuid] = {name: group.name, type: 'bone', keyframes: []});
      assert(!(an.keyframes || []).some(k => k.channel === 'scale'), `existing scale keys ${a.name}/${group.name}`);
      (an.keyframes || (an.keyframes = [])).push(keyframe(template, a.name, group.name, value));
    };
    const carried = CARRIED.includes(a.name), magGroup = byName('magazine'), reloadGroup = byName('reload_magazine');
    show(magGroup, carried ? 0 : 1);
    if (!(a.name === 'reload_empty')) show(reloadGroup, carried ? 1 : 0);
    else assert((A[reloadGroup.uuid].keyframes || []).some(k => k.channel === 'scale'), 'reload_empty keeps its own reload_magazine scale keys');
  }
  return {s, refs, removed: {extended: removedExtended, helpers: removedHelpers}};
}

// ---------------- runtime geo / animation (same operations) ----------------
function migrateGeo(geo, src) {
  const G = geo['minecraft:geometry'][0], old = new Map(G.bones.map(b => [b.name, b]));
  const inv = Object.fromEntries(Object.entries(RENAME).map(([a, b]) => [b, a]));
  const flip = v => [-v[0] || 0, v[1], v[2]];
  // bones in the migrated source's outliner order, exported groups only
  const groups = new Map(src.groups.map(g => [g.uuid, g])), bones = [];
  (function walk(list, parent, hidden) {
    for (const n of list) {
      if (typeof n === 'string') continue;
      const g = groups.get(n.uuid), omit = hidden || g.export === false;
      if (!omit) {
        const oldName = inv[g.name] ?? g.name, copyOf = Object.entries(COPY).find(([, v]) => v === g.name)?.[0];
        const base = old.get(g.name) ?? old.get(oldName) ?? (copyOf ? old.get(copyOf) : null);
        const b = base ? clone(base) : {name: g.name};
        b.name = g.name; if (parent) b.parent = parent; else delete b.parent;
        if (!base || ['lefthand', 'lefthand_pos', 'mag_out'].includes(g.name)) b.pivot = flip(g.origin);   // others keep the exported numbers
        if (g.name === 'mag_out' || g.name === 'lefthand') delete b.cubes;
        if (!base) assert.equal(g.name, 'mag_out');
        bones.push(b);
      }
      walk(n.children, omit ? parent : g.name, omit);
    }
  })(src.outliner, null, false);
  // lefthand / lefthand_pos cube ownership stays with the original bones (both exported cube-less in V1)
  G.bones = bones;
  return geo;
}
function migrateAnimation(json) {
  for (const [clip, a] of Object.entries(json.animations)) {
    const B = a.bones, out = {};
    for (const [name, ch] of Object.entries(B)) {
      if (['constraint', 'bolt2', 'charger'].includes(name)) continue;
      if (name === 'mag_and_lefthand') { out.mag_out = clone(ch); out.lefthand = clone(ch); continue; }
      if (name === 'lefthand') { out.lefthand_pos = ch; continue; }
      out[RENAME[name] ?? name] = ch;
    }
    if (!B.mag_and_lefthand) { if (B.lefthand) { delete out.lefthand; out.lefthand_pos = B.lefthand; } }
    const carried = CARRIED.includes(clip), v = x => [x, x, x];
    (out.magazine ||= {}).scale = v(carried ? 0 : 1);
    if (clip !== 'reload_empty') (out.reload_magazine ||= {}).scale = v(carried ? 1 : 0);
    a.bones = out;
  }
  return json;
}

// ---------------- evaluation (GeckoLib-like; both rigs share it, so equivalence is convention independent) ----------------
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
const vec = v => Array.isArray(v) ? v.map(Number) : typeof v === 'number' ? [v, v, v] : null;
function sample(ch, t, rest) {
  if (ch === undefined) return rest;
  const c = vec(ch); if (c) return c;
  const keys = Object.entries(ch).map(([k, v]) => [+k, v]).sort((a, b) => a[0] - b[0]);
  const pre = v => vec(v) ?? vec(v.pre) ?? vec(v.vector) ?? vec(v.post), post = v => vec(v) ?? vec(v.post) ?? vec(v.vector) ?? vec(v.pre);
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
const migrated = !disk.groups.some(g => g.name === 'gun_and_righthand');
const base = process.env.BR51_RIG_BASE || 'HEAD';
const original = p => migrated ? execFileSync('git', ['show', `${base}:${p}`], {cwd: ROOT, encoding: 'utf8', maxBuffer: 1 << 28}) : read(p);
const src0 = JSON.parse(original(P.src)), geo0 = JSON.parse(original(P.geo)), anim0 = JSON.parse(original(P.anim));
const {s: src1, refs, removed} = migrateSource(clone(src0));
const geo1 = migrateGeo(clone(geo0), src1), anim1 = migrateAnimation(clone(anim0));

// V1: source animations == runtime animations (values, times, channels; x / y rotation and x position flip on export)
{
  const conv = (ch, p) => { const v = ['x', 'y', 'z'].map(k => +p[k]); return ch === 'rotation' ? [-v[0], -v[1], v[2]] : ch === 'position' ? [-v[0], v[1], v[2]] : v; };
  let checked = 0;
  for (const a of src1.animations) {
    const r = anim1.animations[a.name], seen = new Set(); assert(r, 'runtime clip ' + a.name);
    for (const an of Object.values(a.animators || {})) {
      if (an.type && an.type !== 'bone' || !(an.keyframes || []).length) continue; seen.add(an.name);
      for (const ch of ['rotation', 'position', 'scale']) {
        const ks = an.keyframes.filter(k => k.channel === ch).sort((x, y) => x.time - y.time), rv = r.bones[an.name]?.[ch];
        if (!ks.length) { assert.equal(rv, undefined, `${a.name}/${an.name}/${ch} runtime only`); continue; }
        const entries = vec(rv) ? [[0, rv]] : Object.entries(rv).map(([t, v]) => [+t, v]);
        assert.equal(entries.length, ks.length, `${a.name}/${an.name}/${ch} key count`);
        ks.forEach((k, i) => { const [t, v] = entries[i], got = vec(v) ?? vec(v.vector) ?? vec(v.post) ?? vec(v.pre), want = conv(ch, k.data_points[0]); checked++;
          assert(Math.abs(t - k.time) < 1e-4 && got.every((x, j) => Math.abs(x - want[j]) < 1e-4), `${a.name}/${an.name}/${ch}@${k.time}`); });
      }
    }
    for (const b of Object.keys(r.bones)) assert(seen.has(b), `${a.name}: runtime-only bone ${b}`);
  }
  console.log('V1 source <-> runtime animation keys:', checked);
}
// V2: runtime geo == exported groups of the source (names, parents, pivots, rotations, cube counts)
{
  const groups = new Map(src1.groups.map(g => [g.uuid, g])), exp = new Map();
  (function walk(list, parent, hidden) { for (const n of list) { if (typeof n === 'string') continue; const g = groups.get(n.uuid), omit = hidden || g.export === false;
    if (!omit) exp.set(g.name, {parent, origin: g.origin, rotation: g.rotation, cubes: n.children.filter(c => typeof c === 'string').filter(c => src1.elements.find(e => e.uuid === c).export !== false).length});
    walk(n.children, omit ? parent : g.name, omit); } })(src1.outliner, null, false);
  const bones = geo1['minecraft:geometry'][0].bones;
  assert.deepEqual(bones.map(b => b.name).sort(), [...exp.keys()].sort(), 'geo bones == exported groups');
  for (const b of bones) { const e = exp.get(b.name);
    assert.equal(b.parent ?? null, e.parent, 'parent ' + b.name);
    assert.deepEqual(b.pivot.map(v => +v.toFixed(5)), [-e.origin[0] || 0, e.origin[1], e.origin[2]].map(v => +v.toFixed(5)), 'pivot ' + b.name);
    assert.deepEqual((b.rotation || [0, 0, 0]).map(v => v || 0), (e.rotation || [0, 0, 0]).map((v, i) => (i < 2 ? -v : v) || 0), 'rotation ' + b.name);
    assert.equal((b.cubes || []).length, e.cubes, 'cubes ' + b.name); }
  console.log('V2 geo bones:', bones.length, 'cubes:', bones.reduce((n, b) => n + (b.cubes || []).length, 0));
}
// V3: every animated bone exists; clip set, lengths, loop modes and existing key times unchanged
{
  const names = new Set(geo1['minecraft:geometry'][0].bones.map(b => b.name));
  assert.deepEqual(Object.keys(anim1.animations), Object.keys(anim0.animations));
  for (const [clip, a] of Object.entries(anim1.animations)) {
    const o = anim0.animations[clip]; assert.equal(a.animation_length, o.animation_length); assert.equal(a.loop, o.loop);
    assert.deepEqual(a.sound_effects, o.sound_effects);
    for (const b of Object.keys(a.bones)) assert(names.has(b), `${clip} animates missing bone ${b}`);
  }
  for (const n of ['magazine', 'reload_magazine', 'empty_old_mag', 'mag_standard', 'reload_mag_standard', 'empty_old_mag_standard', 'handling', 'gun_body', 'mag_out', 'lefthand', 'lefthand_pos', 'righthand',
    'right_hand_anchor', 'left_hand_anchor', 'sight_anchor', 'muzzle_anchor', 'muzzle_pos', 'ejection_anchor', 'camera']) assert(names.has(n), 'bone ' + n);
  for (const n of [...EXTENDED, 'gun_and_righthand', 'mag_and_lefthand', 'magazine_bullet', 'additional_magazine', 'constraint', 'positioning2', 'positioning', 'view', 'shell'])
    assert(!names.has(n) && !src1.groups.some(g => g.name === n), 'removed ' + n);
  console.log('V3 clips:', Object.keys(anim1.animations).length);
}
// V4: identical visible geometry and anchors, every clip, every 1/60 s
{
  let samples = 0, visible = 0;
  const anchors = [['right_hand_anchor'], ['left_hand_anchor'], ['sight_anchor'], ['muzzle_anchor'], ['muzzle_pos'], ['shell', 'ejection_anchor'], ['camera']];
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
// V5: geometry / UV untouched: new cubes = old cubes + reload copies; references hold the original magazine cubes
{
  const key = e => JSON.stringify([e.from, e.to, e.origin, e.rotation, e.inflate, e.faces]);
  const before = src0.elements.map(key), after = src1.elements.map(key);
  const removedIds = new Set(removed.extended.flatMap(r => r.e)), copies = src1.elements.filter(e => !src0.elements.some(o => o.uuid === e.uuid));
  const expect = src0.elements.filter(e => !removedIds.has(e.uuid)).map(key).concat(copies.map(key)).sort();
  assert.deepEqual(after.slice().sort(), expect);
  const copySources = ['mag_standard', 'hu2', '3', '7'].flatMap(n => { const g = src0.groups.find(x => x.name === n), node = (function f(l) { for (const x of l) { if (typeof x === 'string') continue; if (x.uuid === g.uuid) return x; const r = f(x.children); if (r) return r; } })(src0.outliner);
    return node.children.filter(c => typeof c === 'string'); }).map(id => key(src0.elements.find(e => e.uuid === id))).sort();
  assert.deepEqual(copies.map(key).sort(), copySources, 'reload copies are exact copies');
  for (const r of refs) {
    const unshift = v => v.map((x, i) => +(x + SEAT[i]).toFixed(8));
    const near = (a, b) => a.every((x, i) => Math.abs(x - b[i]) < 1e-6);
    for (const e of r.data.elements) { const o = src0.elements.find(x => x.uuid === e.uuid); assert(near(unshift(e.from), o.from) && near(unshift(e.to), o.to), 'reference cube ' + e.name); assert.deepEqual(e.faces, o.faces); }
  }
  assert.deepEqual(src1.textures, src0.textures);
  console.log('V5 cubes:', src0.elements.length, '->', src1.elements.length, `(-${removedIds.size} extended, +${copies.length} reload copies); refs`, refs.map(r => `${r.label}:${r.cubes}`).join(' '));
}

const outputs = [[P.src, JSON.stringify(src1)], [P.geo, JSON.stringify(geo1, null, 2)], [P.anim, JSON.stringify(anim1, null, 2)],
  ...refs.map(r => [r.file, JSON.stringify(r.data)])];
if (migrated) {
  for (const [file, data] of outputs) assert.equal(read(file), data, 'stale ' + file);
  console.log(`CHECK OK (re-derived from ${base})`);
} else {
  for (const [file, data] of outputs) fs.writeFileSync(path.join(ROOT, file), data);
  console.log('wrote ' + outputs.map(([f]) => f).join(', '));
}
