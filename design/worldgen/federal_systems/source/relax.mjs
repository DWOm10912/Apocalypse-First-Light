// The planner's curvature relaxation (RoutePolyline.relax, C3.1) in JS: every vertex whose local radius is under
// rmin moves half way toward its neighbours' midpoint until none is (end points fixed). Used to sketch the joined
// interstate corner exactly as the planner's smoothing would round it (before the terrain design feedback).
export function radius(p, i) {
  const ax = p[i][0] - p[i - 1][0], az = p[i][1] - p[i - 1][1], bx = p[i + 1][0] - p[i][0], bz = p[i + 1][1] - p[i][1];
  const turn = Math.abs(Math.atan2(ax * bz - az * bx, ax * bx + az * bz));
  return turn < 1e-9 ? Infinity : 0.5 * (Math.hypot(ax, az) + Math.hypot(bx, bz)) / turn;
}

export function relax(src, rmin) {
  const p = src.map(q => [q[0], q[1]]);
  for (let it = 0; it < 6000; it++) {
    let moved = false;
    for (let i = 1; i < p.length - 1; i++) {
      if (radius(p, i) >= rmin) continue;
      p[i][0] += 0.5 * (0.5 * (p[i - 1][0] + p[i + 1][0]) - p[i][0]);
      p[i][1] += 0.5 * (0.5 * (p[i - 1][1] + p[i + 1][1]) - p[i][1]);
      moved = true;
    }
    if (!moved) break;
  }
  return p;
}

/** Polyline resampled every step m (ends kept; the last piece may be shorter). */
export function resample(p, step) {
  const out = [p[0]];
  let need = step;
  for (let i = 1; i < p.length; i++) {
    let [x, y] = p[i - 1];
    let l = Math.hypot(p[i][0] - x, p[i][1] - y);
    while (l >= need) {
      const t = need / l;
      x += (p[i][0] - x) * t; y += (p[i][1] - y) * t;
      out.push([x, y]);
      l -= need; need = step;
    }
    need -= l;
  }
  const e = p[p.length - 1], q = out[out.length - 1];
  if (Math.hypot(e[0] - q[0], e[1] - q[1]) > step * 0.25) out.push(e); else out[out.length - 1] = e;
  return out;
}

/**
 * Route a into the town and route b out of it (control polylines, 40 m apart, both ending on the town) as one
 * polyline, relaxed to rmin within half m either side of the town only (the window's ends fixed, so the rest of both
 * legs stays as planned); with how far it moved and how close it now passes the town.
 */
export function joinCorner(a, b, town, rmin = 660, half = 2000) {
  // the planned control points bunch up near a town (each leg was relaxed with its town end fixed): resample first
  const ra = resample(a, 40), rb = resample(b, 40);
  const joined = ra.concat(rb.slice(1)), c = ra.length - 1, h = Math.round(half / 40);
  const lo = Math.max(0, c - h), hi = Math.min(joined.length - 1, c + h);
  const win = relax(joined.slice(lo, hi + 1), rmin), pts = joined.slice(0, lo).concat(win, joined.slice(hi + 1));
  let closest = Infinity, minR = Infinity, shift = 0;
  for (let i = 0; i < win.length; i++) {
    shift = Math.max(shift, Math.hypot(win[i][0] - joined[lo + i][0], win[i][1] - joined[lo + i][1]));
    closest = Math.min(closest, Math.hypot(win[i][0] - town[0], win[i][1] - town[1]));
    if (i > 0 && i < win.length - 1) minR = Math.min(minR, radius(win, i));
  }
  return { pts, joined, win, lo, hi, closest, minR, shift };
}
