// Offline checks for the BR51-01 50-Round Drum Magazine V1 (br51_drum_magazine_50) and its integration.
//   node tools/verify-br51-drum-magazine-50.mjs
// Asset (V2 sidecar, maps, NaN / UV, parts, budget, silhouette = the reference cube footprint), style (materials = BR51
// V2 magazine / stock), registration (item, capacity, replaced bones, creative tab, lang, item model, BR51 accepts), top
// compatibility (feed lips and top-round anchors = the standard magazine's), idle-pose interference with the gun, the
// empty-reload override (data, clip in the BR51 profile and animation file, sounds registered, the composed clip holds
// the bolt back, never shows a second drum, commits no earlier than its end) and the maintenance-bench rest lift. Uses
// shipped files and the generators' exports only, no Minecraft.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';
import * as DRUM from './build-br51-drum-magazine-50.mjs';
import * as BR51 from './build-br51-01-v2-mesh.mjs';
import * as RELOAD from './build-br51-drum-reload.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const A = path.join(root, 'src/main/resources/assets/apocalypse_firstlight'), D = path.join(root, 'src/main/resources/data/apocalypse_firstlight');
const J = path.join(root, 'src/main/java/com/antaurora/apofirstlight');
const read = p => JSON.parse(fs.readFileSync(p, 'utf8').replace(/^﻿/, ''));
const pngSize = p => { const b = fs.readFileSync(p); return [b.readUInt32BE(16), b.readUInt32BE(20)]; };
const ID = 'br51_drum_magazine_50', NS = 'apocalypse_firstlight:', report = {};
const ext = (P, i) => [Math.min(...P.map(q => q[i])), Math.max(...P.map(q => q[i]))];

// ---- asset + style ----
const mesh = read(path.join(A, `meshes/${ID}.aflmesh.json`)), geo = read(path.join(A, `geo/${ID}.geo.json`))['minecraft:geometry'][0];
assert.equal(mesh.format_version, 2); assert.deepEqual(mesh.texture_size, [512, 512]);
for (const k of ['', '_s', '_n']) assert.deepEqual(pngSize(path.join(A, `textures/item/${ID}${k}.png`)), [512, 512], 'map ' + k);
assert.deepEqual(geo.bones.map(b => [b.name, b.pivot]), [[ID + '_root', [0, 0, 0]]]);
assert.deepEqual(mesh.parts.map(p => p.name).sort(), ['drum_body', 'drum_id_marks', 'drum_tower']);
let bad = 0; for (const p of mesh.parts) for (const v of p.vertices) if (v.some(x => !Number.isFinite(x)) || v[3] < 0 || v[3] > 1 || v[4] < 0 || v[4] > 1) bad++;
assert.equal(bad, 0, 'NaN / UV range');
const faces = mesh.parts.flatMap(p => p.faces), tri = faces.reduce((s, f) => s + f.length - 2, 0);
assert(tri <= 1400, 'over budget ' + tri);
const V = mesh.parts.flatMap(p => p.vertices.map(v => v.slice(0, 3).map(x => x * 16)));
const cubes = DRUM.groupInfo.get('mag_extended_3').cubes.flatMap(c => c.corners);
for (let i = 0; i < 3; i++) { const m = ext(V, i), c = ext(cubes, i); assert(m[0] >= c[0] - 0.03 && m[1] <= c[1] + 0.03, `bounds axis ${i}: ${m} vs ${c}`); }
assert.deepEqual(DRUM.MATS.magazine, BR51.MATS.magazine); assert.deepEqual(DRUM.MATS.stock, BR51.MATS.stock);
assert(DRUM.texelsPerUnit >= 14, 'texel density ' + DRUM.texelsPerUnit);
report.asset = {triangleEquivalent: tri, bounds: [0, 1, 2].map(i => ext(V, i).map(v => +v.toFixed(3))), texelsPerUnit: DRUM.texelsPerUnit};

// ---- registration ----
const items = fs.readFileSync(path.join(J, 'registry/AflItems.java'), 'utf8');
const reg = items.match(/"br51_drum_magazine_50",\s*\(\)\s*->\s*new com\.antaurora\.apofirstlight\.weapon\.NativeMagazineItem\("br51_01",(\d+),\s*java\.util\.Set\.of\(([^)]*)\),(true|false),([\d.]+)f\)\)/);
assert(reg, 'registration'); assert.equal(+reg[1], 50); assert.equal(reg[3], 'true');
const replaced = reg[2].split(',').map(s => s.trim().replace(/"/g, ''));
assert.deepEqual(replaced, ['mag_standard', 'reload_mag_standard', 'empty_old_mag_standard']);
const itemLift = +reg[4], centreY = (ext(V, 1)[0] + ext(V, 1)[1]) / 2;
assert(Math.abs(itemLift + centreY) < 0.25, `item lift ${itemLift} should centre the drum (bounds centre y ${centreY})`);
assert(/AflItems\.BR51_EXTENDED_MAGAZINE_35,\s*AflItems\.BR51_DRUM_MAGAZINE_50\);/.test(fs.readFileSync(path.join(J, 'registry/AflCreativeTabs.java'), 'utf8')), 'attachments tab');
assert.equal(read(path.join(A, 'lang/zh_cn.json'))[`item.${NS.replace(':', '.')}${ID}`], 'BR-51 弹鼓');
assert.equal(read(path.join(A, 'lang/en_us.json'))[`item.${NS.replace(':', '.')}${ID}`], 'BR-51 Drum Magazine');
for (const l of ['zh_cn', 'en_us']) assert(read(path.join(A, `lang/${l}.json`))[`tooltip.${NS.replace(':', '.')}${ID}.description`], 'tooltip ' + l);
assert.equal(read(path.join(A, `models/item/${ID}.json`)).parent, 'builtin/entity');
const gun = read(path.join(D, 'native_guns/br51_01.json'));
assert(gun.magazine_slot.accepts.includes(NS + ID) && gun.magazine_slot.accepts.includes(NS + 'br51_extended_magazine_35'), 'BR51 accepts');

// ---- gun bind pose (magazine-local coordinates) ----
const gunGeo = read(path.join(A, 'geo/br51_01.geo.json'))['minecraft:geometry'][0], by = new Map(gunGeo.bones.map(b => [b.name, b]));
const DEG = Math.PI / 180, mul4 = (a, b) => { const r = new Array(16).fill(0); for (let i = 0; i < 4; i++) for (let j = 0; j < 4; j++) for (let k = 0; k < 4; k++) r[i * 4 + j] += a[i * 4 + k] * b[k * 4 + j]; return r; };
const T4 = v => [1, 0, 0, v[0], 0, 1, 0, v[1], 0, 0, 1, v[2], 0, 0, 0, 1];
const Rot = e => { const [x, y, z] = e.map(d => d * DEG), c = Math.cos, s = Math.sin; return mul4([c(z), -s(z), 0, 0, s(z), c(z), 0, 0, 0, 0, 1, 0, 0, 0, 0, 1], mul4([c(y), 0, s(y), 0, 0, 1, 0, 0, -s(y), 0, c(y), 0, 0, 0, 0, 1], [1, 0, 0, 0, 0, c(x), -s(x), 0, 0, s(x), c(x), 0, 0, 0, 0, 1])); };
const pt = (m, p) => [0, 1, 2].map(i => m[i * 4] * p[0] + m[i * 4 + 1] * p[1] + m[i * 4 + 2] * p[2] + m[i * 4 + 3]);
const piv = n => { const b = by.get(n); return [-b.pivot[0] || 0, b.pivot[1], b.pivot[2]]; };
const M = new Map(), matrix = n => { if (M.has(n)) return M.get(n); const b = by.get(n), p = piv(n), r = b.rotation ? [-b.rotation[0], -b.rotation[1], b.rotation[2]] : [0, 0, 0];
  const m = mul4(b.parent ? matrix(b.parent) : T4([0, 0, 0]), mul4(T4(p), mul4(Rot(r), T4(p.map(v => -v))))); M.set(n, m); return m; };
const under = (n, r) => { for (let b = by.get(n); b; b = by.get(b.parent)) if (b.name === r) return true; return false; };
const magOrigin = pt(matrix('mag_standard'), piv('mag_standard'));
const gunMesh = read(path.join(A, 'meshes/br51_01.aflmesh.json'));
const partPoints = p => { const m = matrix(p.bone), q = piv(p.bone); return p.vertices.map(v => pt(m, [v[0] * 16 + q[0], v[1] * 16 + q[1], v[2] * 16 + q[2]]).map((x, i) => x - magOrigin[i])); };
const std = gunMesh.parts.filter(p => under(p.bone, 'mag_standard')).flatMap(partPoints);

// ---- top compatibility + top rounds ----
assert(Math.abs(ext(std, 1)[1] - ext(V, 1)[1]) < 0.01, 'feed-lip height');
const up = P => P.filter(q => q[1] > 0);
for (const i of [0, 2]) { const s = ext(up(std), i), m = ext(up(V), i); assert(Math.abs(s[0] - m[0]) < 0.03 && Math.abs(s[1] - m[1]) < 0.03, `upper envelope axis ${i}`); }
const visual = gun.presentation.magazine_round_visual, arr = v => v == null ? [] : Array.isArray(v) ? v : [v];
for (const b of [...arr(visual.anchor), ...arr(visual.loaded_auxiliary_anchor)]) for (const r of replaced) assert(!under(b, r), `round bone ${b} inside ${r}`);
const lipX = ext(up(V), 0), lipZ = ext(up(V), 2);
for (const n of arr(visual.anchor)) { const q = pt(matrix(n), piv(n)).map((x, i) => x - magOrigin[i]); assert(q[0] > lipX[0] && q[0] < lipX[1] && q[2] > lipZ[0] && q[2] < lipZ[1], 'round anchor ' + n); }

// ---- idle-pose interference: the gun (magazine bones excluded) against the drum below the magwell ----
// the feed tower sits in the magwell by design; everything from the collar down (y < -1.8) must be clear of the gun
const rest = gunMesh.parts.filter(p => !replaced.some(r => under(p.bone, r)) && !['mag_out', 'empty_old_mag', 'reload_magazine', 'bullet', 'bullet_in_barrel'].some(r => p.bone === r || under(p.bone, r)));
const drumLow = V.filter(q => q[1] < -1.8), bx = ext(drumLow, 0), by0 = ext(drumLow, 1), bz = ext(drumLow, 2);
const inside = []; for (const p of rest) for (const q of partPoints(p)) if (q[0] > bx[0] && q[0] < bx[1] && q[1] > by0[0] && q[1] < -1.8 && q[2] > bz[0] && q[2] < bz[1]) inside.push(p.name);
report.interference = {drumBelowCollar: {x: bx.map(v => +v.toFixed(2)), y: by0.map(v => +v.toFixed(2)), z: bz.map(v => +v.toFixed(2))}, gunPointsInBox: inside.length, parts: [...new Set(inside)]};
assert.equal(inside.length, 0, 'gun geometry inside the drum housing box: ' + [...new Set(inside)]);

// ---- empty-reload override ----
const data = read(path.join(D, `native_attachments/${ID}.json`)).reload_overrides;
assert.deepEqual(Object.keys(data), ['empty'], 'only the empty reload is overridden (tactical reuses the authored clip)');
assert.equal(data.empty.clip, RELOAD.CLIP);
const ticks = Math.ceil(data.empty.seconds * 20), magIn = data.empty.mag_in_tick ?? ticks;
const anims = read(path.join(A, 'animations/br51_01.animation.json')).animations, clip = anims[RELOAD.CLIP];
assert(clip, 'clip in animation file');
assert(ticks >= Math.ceil(clip.animation_length * 20 - 1e-6), 'override shorter than the clip');
assert.equal(magIn, ticks, 'rounds committed at the end (the BR51 rifle default)');
assert(/"reload_empty_drum"/.test(items.slice(items.indexOf('"br51_01"'), items.indexOf('HR55'))), 'clip missing from the BR51 profile clip list');
const sounds = read(path.join(A, 'sounds.json'));
for (const s of Object.values(clip.sound_effects)) assert(sounds[s.effect.replace(NS, '')], 'unregistered sound ' + s.effect);
const vec = k => Array.isArray(k) ? k : k.vector ?? k.post ?? k.pre;
const chan = (b, c) => clip.bones[b]?.[c];
const valuesOf = ch => Array.isArray(ch) ? [[0, ch]] : Object.entries(ch).map(([t, k]) => [+t, vec(k)]);
// bolt locked back until the bolt-release sound, forward at the end
const release = +Object.entries(clip.sound_effects).find(([, s]) => s.effect.endsWith('reload_empty_4'))[0];
for (const [t, v] of valuesOf(chan('bolt', 'position'))) if (t <= release) assert.equal(v[2], 3, `bolt must stay back until ${release}s (t ${t})`);
assert.deepEqual(valuesOf(chan('bolt', 'position')).at(-1)[1], [0, 0, 0], 'bolt home at the end');
// never a second drum: the dropped-magazine copy stays zero-scaled, the in-gun copy hidden (the reload copy is the drum)
for (const [, v] of valuesOf(chan('empty_old_mag_standard', 'scale'))) assert.deepEqual(v.map(Number), [0, 0, 0], 'old-magazine copy visible');
for (const [, v] of valuesOf(chan('magazine', 'scale'))) assert.deepEqual(v.map(Number), [0, 0, 0], 'in-gun magazine visible during reload');
report.reload = {clip: RELOAD.CLIP, seconds: data.empty.seconds, ticks, magInTick: magIn, length: clip.animation_length, bones: Object.keys(clip.bones).length,
  sounds: Object.fromEntries(Object.entries(clip.sound_effects).map(([t, s]) => [t, s.effect.replace(NS + 'br51_01_', '')])), boltRelease: release,
  standardEmptySeconds: gun.reload.empty_seconds};

// ---- maintenance bench rest lift (MaintenanceGunRendering.restLift, DEFAULT profile rotation Y -90, X 0, Z -90, scale .29) ----
// bench vertical = (Ry Rx Rz v).y: Rz(-90) maps model +x to bench -y, so the gun rests on its +x side
const down = v => -v[0];
const allGun = gunMesh.parts.filter(p => !['mag_out', 'empty_old_mag', 'reload_magazine'].some(r => p.bone === r || under(p.bone, r)) && !/^(reload_|empty_old_)/.test(p.bone))
  .flatMap(partPoints).map(q => q.map((x, i) => x + magOrigin[i]));
const drumGun = V.map(q => q.map((x, i) => x + magOrigin[i]));
const baseLow = Math.min(...allGun.map(down)), drumLowest = Math.min(...drumGun.map(down));
report.bench = {baseLowest: +baseLow.toFixed(3), drumLowest: +drumLowest.toFixed(3), liftModelUnits: +Math.max(0, baseLow - drumLowest).toFixed(3),
  liftBlocks: +(Math.max(0, baseLow - drumLowest) / 16 * 0.29).toFixed(4), note: 'runtime computes the same from bind-pose bounds; the drum is symmetric in x, so either resting side gives this lift'};
assert(baseLow - drumLowest > 0, 'the drum should overhang the gun sideways (the reason for the lift)');
console.log(JSON.stringify(report, null, 1));
console.log('BR51_DRUM50_V1_OFFLINE_PASS');
