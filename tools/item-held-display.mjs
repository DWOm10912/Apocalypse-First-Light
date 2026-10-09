// Item display contexts centred on the model (2026-10-09). The generators used to give every held / dropped / framed
// context a fixed translation, which only suits a model that fills its cell like a block; a model off the cell centre (a
// downlight under the ceiling, a wall fitting on one side, a mouse on the floor) then floated above the hand or sank
// below it (user: "手持物品都飞上天了"). Here each context keeps its rotation, gets a scale that makes the model's largest
// on-screen extent `size` times a vanilla block's in that context, and a translation that puts the model's bounds centre
// where a vanilla block's centre is. Scales stop at 4: vanilla clamps a display scale to -4..4 (ItemTransform), and a
// translation computed for a larger scale would put the model off centre.
//   points: model vertices in block pixels (the cell is 0..16 on every axis), as the item model draws them.
// Left-hand contexts: vanilla applies them mirrored (x translation and the y / z rotations negated), so they are fitted
// with the rotation actually applied.
const D2R = Math.PI / 180, r3 = v => +v.toFixed(3) || 0, S3 = v => [v, v, v];

/** Vanilla 1.20.1 block/block.json: where and how big a placed block's item is drawn. */
export const VANILLA = {
  thirdperson_righthand: {rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: 0.375},
  firstperson_righthand: {rotation: [0, 45, 0], translation: [0, 0, 0], scale: 0.4},
  firstperson_lefthand: {rotation: [0, 225, 0], translation: [0, 0, 0], scale: 0.4},
  ground: {rotation: [0, 0, 0], translation: [0, 3, 0], scale: 0.25},
  fixed: {rotation: [0, 0, 0], translation: [0, 0, 0], scale: 0.5},
  gui: {rotation: [30, 225, 0], translation: [0, 0, 0], scale: 0.625},
};
export const MAX_SCALE = 4;

/** As Quaternionf.rotationXYZ: x, then y, then z, applied to the vector in the order z, y, x. */
function rotate(p, [rx, ry, rz]) {
  const [ax, ay, az] = [rx, ry, rz].map(a => a * D2R);
  let [x, y, z] = p;
  [x, y] = [x * Math.cos(az) - y * Math.sin(az), x * Math.sin(az) + y * Math.cos(az)];
  [x, z] = [x * Math.cos(ay) + z * Math.sin(ay), -x * Math.sin(ay) + z * Math.cos(ay)];
  [y, z] = [y * Math.cos(ax) - z * Math.sin(ax), y * Math.sin(ax) + z * Math.cos(ax)];
  return [x, y, z];
}
function bounds(points, rotation) {
  const lo = [Infinity, Infinity, Infinity], hi = [-Infinity, -Infinity, -Infinity];
  for (const q of points) { const p = rotate([q[0] - 8, q[1] - 8, q[2] - 8], rotation); for (let i = 0; i < 3; i++) { lo[i] = Math.min(lo[i], p[i]); hi[i] = Math.max(hi[i], p[i]); } }
  return {c: lo.map((v, i) => (v + hi[i]) / 2), e: Math.max(hi[0] - lo[0], hi[1] - lo[1])};
}
const CUBE = []; for (const x of [0, 16]) for (const y of [0, 16]) for (const z of [0, 16]) CUBE.push([x, y, z]);

/** One context, fitted (see the file header). `left`: a left-hand context, applied mirrored. */
export function fit(points, context, size, rotation = (VANILLA[context] || VANILLA[context.replace('lefthand', 'righthand')]).rotation, left = false) {
  const V = VANILLA[context] || VANILLA[context.replace('lefthand', 'righthand')];
  const applied = r => left ? [r[0], -r[1], -r[2]] : r;
  const ref = bounds(CUBE, applied(V.rotation)), b = bounds(points, applied(rotation));
  const s = r3(Math.min(MAX_SCALE, size * V.scale * ref.e / b.e));
  // the model's centre lands on the block's: (applied translation) + s * c = (applied vanilla translation)
  const t = V.translation.map((v, i) => r3(left && i === 0 ? v + s * b.c[0] : v - s * b.c[i]));
  return {rotation, translation: t, scale: S3(s)};
}

/**
 * Third person, first person (both hands), ground and item frame, fitted. size: the model's largest extent in units of a
 * vanilla block's in the same context (1 = as big as a held block). rotations: per-context rotation overrides
 * (default vanilla's; item frames usually [0, 180, 0] so the front faces out).
 */
export function heldDisplay(points, {size = 1, rotations = {}} = {}) {
  const rot = c => rotations[c] ?? (VANILLA[c] || VANILLA[c.replace('lefthand', 'righthand')]).rotation;
  return {
    thirdperson_righthand: fit(points, 'thirdperson_righthand', size, rot('thirdperson_righthand')),
    thirdperson_lefthand: fit(points, 'thirdperson_righthand', size, rot('thirdperson_lefthand'), true),
    firstperson_righthand: fit(points, 'firstperson_righthand', size, rot('firstperson_righthand')),
    firstperson_lefthand: fit(points, 'firstperson_lefthand', size, rot('firstperson_lefthand'), true),
    ground: fit(points, 'ground', size, rot('ground')),
    fixed: fit(points, 'fixed', size, rot('fixed')),
  };
}
