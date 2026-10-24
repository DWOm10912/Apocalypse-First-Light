// AFL stone items (Rock R1a field stone, R1b pebble and flint; docs/gameplay/rock_r1_stones_boulders_design.md 2.1, 2.4,
// 11.2, 11.3): Pure Mesh items by the Material Meshes bake (tools/build-material-meshes-v1.mjs) on the boulders' own
// sprites: the rock made as a boulder is (boulder-shapes.mjs rock(): a noisy sphere, a few worn fracture planes, a settled
// side), small, and its photo sprite (textures/block/boulder/*.png, CC0 photo, 128 texels per metre) laid on it by box
// projection, so a stone in the hand has the grain of the rock it came from.
//   field_stone  the 17 cm granite cobble a broken boulder drops (R1a)
//   pebble       a 5 cm granite pebble (R1b)
//   flint        a flint nodule broken open: the white cortex (flint_cortex) and on its break the flint itself (flint)
//   node tools/boulders/build-field-stone.mjs            -> writes source, atlas, geo, AFL mesh sidecar, icon, item model of each
//   node tools/boulders/build-field-stone.mjs --check    -> verifies every output is up to date
// Built at real size, 1 px = 1/16 m. One shape each (the design's several shapes picked by the stack, and the two-handed
// carry of the field stone, are not built: 11.3).
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {rock, clip, V} from './boulder-shapes.mjs';
import {STONES} from './build-loose-stones.mjs';
import {Part, add, sub, mul, cross, readPng, png} from '../cube-slab-mesh-lib.mjs';
import {bake} from '../build-material-meshes-v1.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const SPRITES = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/textures/block/boulder');
const r4 = v => +v.toFixed(4) || 0;
const loose = id => STONES.find(s => s.id === id);

/**
 * The items. field_stone: about 17 x 11 x 16 cm (4 kg of granite, item_mass), rounder than any boulder form (pre and round
 * 2), three worn breaks. pebble and flint: the loose stones' own shapes (build-loose-stones.mjs) at a finer level; the flint
 * is the broken nodule, its break (cap) the flint sprite. Hands (held()): turn, scale and how deep the stone sinks into the
 * fist in third person; turn and scale in first person.
 */
const ITEMS = {
  field_stone: {spec: {L: 0.2, r: [1, 0.7, 0.8], planes: 3, pre: 2, round: 2, amp: 0.14, cut: [0.62, 0.86]}, seed: 4107, level: 3,
    body: 'granite', tp: [[20, 35, -10], 2.2, 1.0], fp: [[-8, 40, 6], 1.7]},
  pebble: {...loose('pebble_field_0'), level: 2, body: 'granite', tp: [[20, 35, -10], 2.4, 0.3], fp: [[-8, 40, 6], 2.0]},
  flint: {...loose('flint_1'), level: 3, body: 'flint_cortex', cap: 'flint', tp: [[20, 35, -10], 2.4, 0.4], fp: [[-8, 40, 6], 2.0]},
  // U3a-2 (docs/worldgen/underground_u3_rocks_ores_design.md 3.1): each rock's own stone, shaped as the rock breaks:
  // crystalline rock in angular blocks, the bedded rocks squarer along their beds, schist and shale in slabs and flakes
  // along their cleavage, marl and rock salt blunt. The bodies are the boulders' sprites; gneiss, schist, dolomite, marl and
  // rock salt borrow a neighbour's until their own photos (U3a-4); every one the sprite its block has (a fresh break).
  gneiss_stone: {spec: {L: 0.2, r: [1, 0.62, 0.78], planes: 5, pre: 1, round: 1, amp: 0.1, cut: [0.55, 0.8]}, seed: 4111, level: 3,
    body: 'granite_fresh', tp: [[20, 35, -10], 2.2, 1.0], fp: [[-8, 40, 6], 1.7]},
  schist_stone: {spec: {L: 0.2, r: [1, 0.4, 0.76], planes: 4, pre: 1, round: 1, amp: 0.08, top: true}, seed: 4113, level: 3,
    body: 'shale_worn', tp: [[20, 35, -10], 2.2, 0.8], fp: [[-8, 40, 6], 1.7]},
  sandstone_stone: {spec: {L: 0.2, r: [1, 0.6, 0.8], planes: 6, pre: 0, round: 1, amp: 0.06, cut: [0.5, 0.74]}, seed: 4117, level: 3,
    body: 'sandstone_fresh', tp: [[20, 35, -10], 2.2, 1.0], fp: [[-8, 40, 6], 1.7]},
  limestone_stone: {spec: {L: 0.2, r: [1, 0.62, 0.78], planes: 6, pre: 0, round: 1, amp: 0.07, cut: [0.52, 0.76]}, seed: 4119, level: 3,
    body: 'limestone_fresh', tp: [[20, 35, -10], 2.2, 1.0], fp: [[-8, 40, 6], 1.7]},
  dolomite_stone: {spec: {L: 0.19, r: [1, 0.68, 0.82], planes: 5, pre: 1, round: 1, amp: 0.1, cut: [0.58, 0.84]}, seed: 4123, level: 3,
    body: 'limestone', tp: [[20, 35, -10], 2.2, 1.0], fp: [[-8, 40, 6], 1.7]},
  shale_shard: {spec: {L: 0.16, r: [1, 0.2, 0.72], planes: 3, pre: 1, round: 0, amp: 0.06, top: true}, seed: 4127, level: 3,
    body: 'shale_fresh', tp: [[20, 35, -10], 2.4, 0.5], fp: [[-8, 40, 6], 1.9]},
  marl_lump: {spec: {L: 0.19, r: [1, 0.68, 0.85], planes: 2, pre: 3, round: 2, amp: 0.16}, seed: 4129, level: 3,
    body: 'limestone_worn', tp: [[20, 35, -10], 2.2, 1.0], fp: [[-8, 40, 6], 1.7]},
  rock_salt_lump: {spec: {L: 0.18, r: [1, 0.7, 0.85], planes: 4, pre: 2, round: 2, amp: 0.1}, seed: 4131, level: 3,
    body: 'limestone_fresh', tp: [[20, 35, -10], 2.2, 1.0], fp: [[-8, 40, 6], 1.7]},
};
const PX = 16;                      // px per metre
const ATLAS = 64, DENSITY = 128;    // the sprite's texels per metre (2 m over 256 texels)
const TPX = DENSITY / PX;           // texels per px
/**
 * Atlas regions (texels): the three projections, then the break's (a flint's cap; spare otherwise), and where in the 256
 * sprite each is cut from (different grain).
 */
const REGION = [[1, 1], [33, 1], [1, 33], [33, 33]], CUT = [[20, 36], [140, 90], [72, 188], [180, 160]];

/** The stone as a part, and the faces on its fresh break (a flint's cap). */
function stone(it) {
  let m = rock(it.spec, it.seed, it.level);
  m.F = m.T.map(() => 0);
  if (it.cut) {
    const n = V.norm(it.cut.n), ext = Math.max(...m.P.map(p => V.dot(p, n)));
    m = clip(m, n, ext * it.cut.f);
  }
  const p = new Part('stone', 'stone', 'granite'), cap = new Set();
  const c = mul(m.P.reduce((a, q) => add(a, q), [0, 0, 0]), 1 / m.P.length);
  m.P.forEach(q => p.vtx(mul(q, PX)));
  m.T.forEach((t, ti) => {
    const P = t.map(i => m.P[i]), mid = mul(add(add(P[0], P[1]), P[2]), 1 / 3), before = p.f.length;
    p.face(t, sub(mid, c));
    if (it.cap && m.F[ti] === 1 && p.f.length > before) cap.add(p.f[p.f.length - 1]);
  });
  return {p, cap};
}

/**
 * Box projection: each face onto the plane across its dominant normal axis, at the sprite's density, into that axis's
 * region (a break's faces into the fourth); the stone's extent there plus a texel's margin fits a 30-texel region.
 */
function project(p, cap) {
  const lo = [0, 1, 2].map(k => Math.min(...p.v.map(q => q[k])));
  const faceUV = new Map([[p, new Map()]]);
  const AXES = [[2, 1], [0, 2], [0, 1]];   // x: (z, y); y: (x, z); z: (x, y)
  let span = 0;
  for (const f of p.f) {
    const P = f.ids.map(i => p.v[i]), n = cross(sub(P[1], P[0]), sub(P[2], P[0])).map(Math.abs);
    const k = n.indexOf(Math.max(...n)), [a, b] = AXES[k], [rx, ry] = REGION[cap.has(f) ? 3 : k];
    faceUV.get(p).set(f, P.map(q => { const u = (q[a] - lo[a]) * TPX, v = (q[b] - lo[b]) * TPX; span = Math.max(span, u, v); return [rx + 1 + u, ry + 1 + v]; }));
  }
  if (span > 29) throw new Error('stone too big for its atlas regions: ' + span.toFixed(1) + ' texels');
  return {faceUV, S: TPX, islands: [], uvOf: null, span};
}

/** The atlas: the four regions cut from the body's sprite (the fourth from the cap's, if any), its _s and _n alike. */
function atlas(it) {
  return ['', '_s', '_n'].map(x => {
    const read = name => {
      const src = readPng(fs.readFileSync(path.join(SPRITES, name + x + '.png')));
      if (src.w !== 256 || src.bpp !== 4) throw new Error('sprite format ' + name + x);
      return src;
    };
    const body = read(it.body), cap = it.cap ? read(it.cap) : body;
    const out = Buffer.alloc(ATLAS * ATLAS * 4);
    REGION.forEach(([rx, ry], i) => {
      const src = i === 3 ? cap : body, [sx, sy] = CUT[i];
      for (let y = 0; y < 31; y++) for (let x = 0; x < 31; x++) {
        const s = ((sy + y) % 256 * 256 + (sx + x) % 256) * 4, d = ((ry - 1 + y) * ATLAS + rx - 1 + x) * 4;
        for (let c = 0; c < 4; c++) out[d + c] = src.px[s + c];
      }
    });
    return png(out, ATLAS, ATLAS);
  });
}

// ---------------------------------------------------------------- held transforms (docs/models/equipment_meshes_v1.md "手持适配规则")

const rot3 = ([ax, ay, az]) => {
  const [a, b, c] = [ax, ay, az].map(v => v * Math.PI / 180);
  const Rx = [[1, 0, 0], [0, Math.cos(a), -Math.sin(a)], [0, Math.sin(a), Math.cos(a)]];
  const Ry = [[Math.cos(b), 0, Math.sin(b)], [0, 1, 0], [-Math.sin(b), 0, Math.cos(b)]];
  const Rz = [[Math.cos(c), -Math.sin(c), 0], [Math.sin(c), Math.cos(c), 0], [0, 0, 1]];
  const mm = (P, Q) => P.map(r => [0, 1, 2].map(j => r[0] * Q[0][j] + r[1] * Q[1][j] + r[2] * Q[2][j]));
  return mm(mm(Rx, Ry), Rz);
};
const apply = (M, v) => M.map(r => r[0] * v[0] + r[1] * v[1] + r[2] * v[2]);

/**
 * A stone held in the fist, not on it: third person the stone turned a little (rotation), its side nearest the palm sunk
 * depth px into the fist (item frame: the fist spans x -2..2, y -4..0, z 0..3; the wrench's accepted grip is at y -0.9,
 * z 1.37) and centred over it, scale so it reads as a real stone against the oversized hand (the field stone about 1.6
 * fists long; the small stones a little magnified so they show above the fingers); first person the same stone where the
 * wrench's hand is ([1.13, -0.46, 2.46]), its lower side on it.
 * translation = the landing point - (scale x turn x the stone's own point, centred as drawn).
 */
function held(verts, size, it) {
  const c = verts.map(q => [q[0], q[1] - size[1] / 2, q[2]]), out = {};
  const [tpTurn, tpScale, depth] = it.tp, [fpTurn, fpScale] = it.fp;
  for (const [view, turn, scale, land] of [['thirdperson_righthand', tpTurn, tpScale, [0, -depth, 1.4]], ['firstperson_righthand', fpTurn, fpScale, [1.13, -0.46, 2.46]]]) {
    const M = rot3(turn), P = c.map(q => apply(M, q).map(v => v * scale));
    const lo = [0, 1, 2].map(k => Math.min(...P.map(q => q[k]))), hi = [0, 1, 2].map(k => Math.max(...P.map(q => q[k])));
    const t = [land[0] - (lo[0] + hi[0]) / 2, land[1] - lo[1], land[2] - (lo[2] + hi[2]) / 2].map(r4);
    out[view] = {rotation: turn, translation: t, scale: [scale, scale, scale]};
    out['_' + view] = {lo: lo.map((v, k) => r4(v + t[k])), hi: hi.map((v, k) => r4(v + t[k]))};
  }
  return out;
}

export function stoneItem(id) {
  const it = ITEMS[id], {p, cap} = stone(it), uv = project(p, cap);
  return {PARTS: [p], MATS: {granite: {c: [135, 133, 128], hl: 0, sm: 60, se: 60, f0: 10}}, uv, textures: atlas(it), atlas: ATLAS, it};
}

/** R1a's name for the field stone's spec. */
export const fieldStone = () => stoneItem('field_stone');

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  const check = process.argv.includes('--check');
  let stale = 0;
  for (const id of Object.keys(ITEMS)) {
    const spec = stoneItem(id), it = spec.it;
    delete spec.it;
    const all = spec.PARTS.flatMap(p => p.v), lo = [0, 1, 2].map(k => Math.min(...all.map(q => q[k]))), hi = [0, 1, 2].map(k => Math.max(...all.map(q => q[k])));
    const shift = [-(lo[0] + hi[0]) / 2, -lo[1], -(lo[2] + hi[2]) / 2], size = sub(hi, lo);
    const hand = held(all.map(q => add(q, shift)), size, it);
    spec.display = {thirdperson_righthand: hand.thirdperson_righthand, firstperson_righthand: hand.firstperson_righthand};
    const {outputs, stats} = bake(id, spec);
    // offline hand check: the held stone's box in the item frame (px), against the fist (x -2..2, y -4..0, z 0..3)
    stats.hands = {thirdperson: hand._thirdperson_righthand, firstperson: hand._firstperson_righthand};
    stats.sizeCm = size.map(v => r4(v / PX * 100));
    stats.itemOffset = r4(0.5 - size[1] / 32);   // AflStaticMeshItemClient: 0.5 - height / 32
    stats.atlasSpan = r4(spec.uv.span);
    console.log(id, JSON.stringify(stats));
    for (const [file, data] of outputs) {
      const buf = Buffer.isBuffer(data) ? data : Buffer.from(data);
      if (check) { if (!fs.existsSync(file) || !fs.readFileSync(file).equals(buf)) { console.log('stale ' + path.relative(ROOT, file)); stale++; } }
      else { fs.mkdirSync(path.dirname(file), {recursive: true}); fs.writeFileSync(file, buf); }
    }
  }
  if (check) { if (stale) process.exit(1); console.log('CHECK OK'); } else console.log('written');
}
