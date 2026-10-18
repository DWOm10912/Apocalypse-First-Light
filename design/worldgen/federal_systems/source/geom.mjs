// Plan geometry for the system interchange sheets: a turtle over the world plane (metres, y north, heading in radians
// from east toward north), sampled every STEP m, and the world-to-sheet transform.
export const STEP = 2;
export const deg = a => a * Math.PI / 180;

export class Turtle {
  constructor(x, y, h) { this.x = x; this.y = y; this.h = h; this.pts = [[x, y]]; }
  /** Straight run of length l. */
  go(l) {
    const n = Math.max(1, Math.ceil(l / STEP));
    for (let i = 1; i <= n; i++) this.pts.push([this.x + Math.cos(this.h) * l * i / n, this.y + Math.sin(this.h) * l * i / n]);
    this.x += Math.cos(this.h) * l; this.y += Math.sin(this.h) * l;
    return this;
  }
  /** Arc of radius r turning by a (positive: left). */
  arc(r, a) {
    const s = Math.sign(a), cx = this.x - s * r * Math.sin(this.h), cy = this.y + s * r * Math.cos(this.h);
    const p0 = Math.atan2(this.y - cy, this.x - cx), n = Math.max(2, Math.ceil(Math.abs(a) * r / STEP));
    for (let i = 1; i <= n; i++) {
      const p = p0 + a * i / n;
      this.pts.push([cx + r * Math.cos(p), cy + r * Math.sin(p)]);
    }
    this.x = cx + r * Math.cos(p0 + a); this.y = cy + r * Math.sin(p0 + a); this.h += a;
    return this;
  }
  /** Straight run to a point (heading set toward it). */
  to(x, y) { this.h = Math.atan2(y - this.y, x - this.x); return this.go(Math.hypot(x - this.x, y - this.y)); }
  /** Smooth lateral shift by d (positive: to the left) over length l (two opposite arcs). */
  shift(d, l) {
    // two arcs of equal radius r and angle a: 2 r (1 - cos a) = |d|, 2 r sin a = l
    const a = 2 * Math.atan2(Math.abs(d), l), r = l / (2 * Math.sin(a));
    return this.arc(r, Math.sign(d) * a).arc(r, -Math.sign(d) * a);
  }
}

/** Polyline length. */
export function length(p) {
  let l = 0;
  for (let i = 1; i < p.length; i++) l += Math.hypot(p[i][0] - p[i - 1][0], p[i][1] - p[i - 1][1]);
  return l;
}

/** Point and heading at arc length s along p. */
export function at(p, s) {
  let run = 0;
  for (let i = 1; i < p.length; i++) {
    const l = Math.hypot(p[i][0] - p[i - 1][0], p[i][1] - p[i - 1][1]);
    if (run + l >= s || i === p.length - 1) {
      const t = l < 1e-9 ? 0 : Math.max(0, Math.min(1, (s - run) / l));
      return { x: p[i - 1][0] + (p[i][0] - p[i - 1][0]) * t, y: p[i - 1][1] + (p[i][1] - p[i - 1][1]) * t,
        h: Math.atan2(p[i][1] - p[i - 1][1], p[i][0] - p[i - 1][0]) };
    }
    run += l;
  }
  return { x: p[0][0], y: p[0][1], h: 0 };
}

/** Least distance between two polylines (sampled points). */
export function gap(a, b) {
  let d = Infinity;
  for (const p of a) for (const q of b) d = Math.min(d, Math.hypot(p[0] - q[0], p[1] - q[1]));
  return d;
}

/** World-to-sheet transform: origin (ox, oy) on the sheet, s px per m, north up. */
export const view = (ox, oy, s) => ({ s, X: x => ox + x * s, Y: y => oy - y * s, P: p => [ox + p[0] * s, oy - p[1] * s] });
