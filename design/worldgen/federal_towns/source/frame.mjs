// A town's street frame along an interstate: the interstate's centreline (world x, z) smoothed into an axis, and the
// map from town coordinates (s along the axis from the town node, east positive; v to the axis's left, i.e. north of an
// eastbound axis) to the world. Streets parallel to the axis are offsets of it; cross streets are its normals.
import fs from 'fs';

export function loadRoads() {
  return JSON.parse(fs.readFileSync(new URL('./data/r1_roads.json', import.meta.url), 'utf8'));
}

/** Polyline resampled every step m. */
export function resample(p, step) {
  const out = [p[0]];
  let carry = 0;
  for (let i = 1; i < p.length; i++) {
    const [ax, az] = p[i - 1], [bx, bz] = p[i], l = Math.hypot(bx - ax, bz - az);
    let t = step - carry;
    while (t <= l) { out.push([ax + (bx - ax) * t / l, az + (bz - az) * t / l]); t += step; }
    carry = l - (t - step);
  }
  return out;
}

/**
 * The frame round a town node on an axis polyline (world points, running east): smoothed over +-smooth m; s = 0 at
 * the axis point nearest the node.
 */
export function frame(axis, node, smooth = 150) {
  const STEP = 5, p = resample(axis, STEP), k = Math.round(smooth / STEP);
  const a = p.map((_, i) => {
    let x = 0, z = 0, n = 0;
    for (let j = Math.max(0, i - k); j <= Math.min(p.length - 1, i + k); j++) { x += p[j][0]; z += p[j][1]; n++; }
    return [x / n, z / n];
  });
  let i0 = 0;
  for (let i = 0; i < a.length; i++) if (Math.hypot(a[i][0] - node[0], a[i][1] - node[1]) < Math.hypot(a[i0][0] - node[0], a[i0][1] - node[1])) i0 = i;
  const at = s => {
    const f = i0 + s / STEP, i = Math.max(0, Math.min(a.length - 2, Math.floor(f))), t = f - i;
    const x = a[i][0] + (a[i + 1][0] - a[i][0]) * t, z = a[i][1] + (a[i + 1][1] - a[i][1]) * t;
    const j0 = Math.max(0, i - 2), j1 = Math.min(a.length - 1, i + 3);
    const tx = a[j1][0] - a[j0][0], tz = a[j1][1] - a[j0][1], l = Math.hypot(tx, tz);
    return { x, z, tx: tx / l, tz: tz / l };
  };
  // v > 0: left of travel along increasing s (north of an eastbound axis): normal (tz, -tx)
  const W = (s, v) => { const q = at(s); return [q.x + q.tz * v, q.z - q.tx * v]; };
  return { at, W, raw: p };
}

/** The frame's point list for a street parallel to the axis at v from s0 to s1 (or a cross street at s from v0 to v1). */
export const along = (F, v, s0, s1, step = 5) => { const o = []; for (let s = s0; s <= s1 + 1e-9; s += step) o.push(F.W(Math.min(s, s1), v)); return o; };
export const across = (F, s, v0, v1, step = 5) => { const o = []; for (let v = v0; v <= v1 + 1e-9; v += step) o.push(F.W(s, Math.min(v, v1))); return o; };

/** A town-coordinate rectangle [s0, s1] x [v0, v1] as a world polygon (its long sides follow the axis). */
export function quad(F, s0, s1, v0, v1, step = 10) {
  const o = [];
  for (let s = s0; s < s1; s += step) o.push(F.W(s, v0));
  o.push(F.W(s1, v0));
  for (let s = s1; s > s0; s -= step) o.push(F.W(s, v1));
  o.push(F.W(s0, v1));
  return o;
}

/** Height range (max - min) and mean of the ground over a town-coordinate rectangle, sampled every 8 m. */
export function relief(F, g, s0, s1, v0, v1) {
  let lo = Infinity, hi = -Infinity, sum = 0, n = 0;
  for (let s = s0; s <= s1; s += 8)
    for (let v = v0; v <= v1; v += 8) {
      const [x, z] = F.W(s, v), h = g.at(x, z);
      lo = Math.min(lo, h); hi = Math.max(hi, h); sum += h; n++;
    }
  return { range: hi - lo, mean: sum / n, lo, hi };
}
