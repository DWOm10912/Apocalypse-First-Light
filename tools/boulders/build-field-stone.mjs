// AFL field stone (Rock R1a, docs/gameplay/rock_r1_stones_boulders_design.md 2.1, 11.2): what a broken boulder piece drops,
// the Pure Mesh item by the Material Meshes bake (tools/build-material-meshes-v1.mjs) on the boulders' own granite: the
// rock made as a boulder is (boulder-shapes.mjs rock(): a noisy sphere, a few worn fracture planes, a settled side), small,
// and the plain granite sprite (textures/block/boulder/granite*.png, CC0 photo, 128 texels per metre) laid on it by
// box projection, so a stone in the hand has the grain of the rock it came from.
//   node tools/boulders/build-field-stone.mjs            -> writes source, atlas, geo, AFL mesh sidecar, icon, item model
//   node tools/boulders/build-field-stone.mjs --check    -> verifies every output is up to date
// Built at real size, 1 px = 1/16 m: a 17 cm glacial cobble, its edges worn round. R1a has one shape (the design's three,
// picked by the stack, and the two-handed carry come with R1b's items).
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {rock} from './boulder-shapes.mjs';
import {Part, add, sub, mul, norm, cross, dot, readPng, png} from '../cube-slab-mesh-lib.mjs';
import {bake} from '../build-material-meshes-v1.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const SPRITE = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/textures/block/boulder/granite');
const r4 = v => +v.toFixed(4) || 0;

/** The cobble: about 17 x 11 x 16 cm (4 kg of granite, item_mass), rounder than any boulder form (pre and round 2), three worn breaks. */
const STONE = {L: 0.2, r: [1, 0.7, 0.8], planes: 3, pre: 2, round: 2, amp: 0.14, cut: [0.62, 0.86]}, SEED = 4107;
const PX = 16;                      // px per metre
const ATLAS = 64, DENSITY = 128;    // the sprite's texels per metre (2 m over 256 texels)
const TPX = DENSITY / PX;           // texels per px
/** Atlas regions (texels) for the three projections, and where in the 256 sprite each is cut from (different grain). */
const REGION = [[1, 1], [33, 1], [1, 33]], CUT = [[20, 36], [140, 90], [72, 188]];

function stone() {
  const m = rock(STONE, SEED, 3);
  const p = new Part('stone', 'field_stone', 'granite');
  const c = mul(m.P.reduce((a, q) => add(a, q), [0, 0, 0]), 1 / m.P.length);
  m.P.forEach(q => p.vtx(mul(q, PX)));
  for (const t of m.T) {
    const P = t.map(i => m.P[i]), mid = mul(add(add(P[0], P[1]), P[2]), 1 / 3);
    p.face(t, sub(mid, c));
  }
  return p;
}

/**
 * Box projection: each face onto the plane across its dominant normal axis, at the sprite's density, into that axis's
 * region (the stone's extent there plus a texel's margin fits a 30-texel region).
 */
function project(p) {
  const lo = [0, 1, 2].map(k => Math.min(...p.v.map(q => q[k])));
  const faceUV = new Map([[p, new Map()]]);
  const AXES = [[2, 1], [0, 2], [0, 1]];   // x: (z, y); y: (x, z); z: (x, y)
  let span = 0;
  for (const f of p.f) {
    const P = f.ids.map(i => p.v[i]), n = cross(sub(P[1], P[0]), sub(P[2], P[0])).map(Math.abs);
    const k = n.indexOf(Math.max(...n)), [a, b] = AXES[k], [rx, ry] = REGION[k];
    faceUV.get(p).set(f, P.map(q => { const u = (q[a] - lo[a]) * TPX, v = (q[b] - lo[b]) * TPX; span = Math.max(span, u, v); return [rx + 1 + u, ry + 1 + v]; }));
  }
  if (span > 29) throw new Error('stone too big for its atlas regions: ' + span.toFixed(1) + ' texels');
  return {faceUV, S: TPX, islands: [], uvOf: null, span};
}

/** The atlas: the three regions (and the spare quadrant) cut from the granite sprite, its _s and _n alike. */
function atlas() {
  return ['', '_s', '_n'].map(x => {
    const src = readPng(fs.readFileSync(SPRITE + x + '.png'));
    if (src.w !== 256 || src.bpp !== 4) throw new Error('sprite format ' + x);
    const out = Buffer.alloc(ATLAS * ATLAS * 4);
    const cuts = [...REGION.map((r, i) => [r, CUT[i]]), [[33, 33], [180, 160]]];
    for (const [[rx, ry], [sx, sy]] of cuts)
      for (let y = 0; y < 31; y++) for (let x = 0; x < 31; x++) {
        const s = ((sy + y) % 256 * 256 + (sx + x) % 256) * 4, d = ((ry - 1 + y) * ATLAS + rx - 1 + x) * 4;
        for (let c = 0; c < 4; c++) out[d + c] = src.px[s + c];
      }
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
 * GRIP_DEPTH px into the fist (item frame: the fist spans x -2..2, y -4..0, z 0..3; the wrench's accepted grip is at
 * y -0.9, z 1.37) and centred over it, scale so its 17 cm read as a real stone against the oversized hand (about 1.6
 * fists long); first person the same stone where the wrench's hand is ([1.13, -0.46, 2.46]), its lower side on it.
 * translation = the landing point - (scale x turn x the stone's own point, centred as drawn).
 */
const GRIP_DEPTH = 1.0;
function held(verts, size) {
  const c = verts.map(q => [q[0], q[1] - size[1] / 2, q[2]]), out = {};
  for (const [view, turn, scale, land] of [['thirdperson_righthand', [20, 35, -10], 2.2, [0, -GRIP_DEPTH, 1.4]], ['firstperson_righthand', [-8, 40, 6], 1.7, [1.13, -0.46, 2.46]]]) {
    const M = rot3(turn), P = c.map(q => apply(M, q).map(v => v * scale));
    const lo = [0, 1, 2].map(k => Math.min(...P.map(q => q[k]))), hi = [0, 1, 2].map(k => Math.max(...P.map(q => q[k])));
    const t = [land[0] - (lo[0] + hi[0]) / 2, land[1] - lo[1], land[2] - (lo[2] + hi[2]) / 2].map(r4);
    out[view] = {rotation: turn, translation: t, scale: [scale, scale, scale]};
    out['_' + view] = {lo: lo.map((v, k) => r4(v + t[k])), hi: hi.map((v, k) => r4(v + t[k]))};
  }
  return out;
}

export function fieldStone() {
  const part = stone(), uv = project(part);
  return {PARTS: [part], MATS: {granite: {c: [135, 133, 128], hl: 0, sm: 60, se: 60, f0: 10}}, uv, textures: atlas(), atlas: ATLAS};
}

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  const check = process.argv.includes('--check'), id = 'field_stone';
  const spec = fieldStone();
  const all = spec.PARTS.flatMap(p => p.v), lo = [0, 1, 2].map(k => Math.min(...all.map(q => q[k]))), hi = [0, 1, 2].map(k => Math.max(...all.map(q => q[k])));
  const shift = [-(lo[0] + hi[0]) / 2, -lo[1], -(lo[2] + hi[2]) / 2], size = sub(hi, lo);
  const hand = held(all.map(q => add(q, shift)), size);
  spec.display = {thirdperson_righthand: hand.thirdperson_righthand, firstperson_righthand: hand.firstperson_righthand};
  const {outputs, stats} = bake(id, spec);
  // offline hand check: the held stone's box in the item frame (px), against the fist (x -2..2, y -4..0, z 0..3)
  stats.hands = {thirdperson: hand._thirdperson_righthand, firstperson: hand._firstperson_righthand};
  stats.sizeCm = size.map(v => r4(v / PX * 100));
  stats.atlasSpan = r4(spec.uv.span);
  console.log(JSON.stringify(stats, null, 1));
  let stale = 0;
  for (const [file, data] of outputs) {
    const buf = Buffer.isBuffer(data) ? data : Buffer.from(data);
    if (check) { if (!fs.existsSync(file) || !fs.readFileSync(file).equals(buf)) { console.log('stale ' + path.relative(ROOT, file)); stale++; } }
    else { fs.mkdirSync(path.dirname(file), {recursive: true}); fs.writeFileSync(file, buf); }
  }
  if (check) { if (stale) process.exit(1); console.log('CHECK OK'); } else console.log('written');
}
