// AFL sign font (2026-10-09, Fuel Stop A1 details: docs/models/fuel_stop_a1_details_v1.md): a bold geometric sans of
// rounded-rectangle bowls and straight strokes, drawn here (no commercial font outlines), for the PRAIRIE channel letters and
// any sign text. Each glyph is a signed distance field (mm, the glyph box x 0..width, y 0..CAP; negative inside) built from
// strokes, boxes and rounded-rectangle rings with min / max: strokes may overlap freely, the outline is one clean contour.
//   glyph(ch)          -> {width, sdf(x, y)}
//   outlines(ch, step, tol, level) -> {width, shapes: [{outer, holes}]}: the sdf's contour at `level` mm (0 = the glyph,
//                         > 0 grown), marching squares on a step mm grid, simplified, outer loops counter-clockwise, holes
//                         clockwise (x right, y up)
//   textSdf(text, opts)-> {width, sdf(x, y)}: a word set with the glyph advances (for painting a word into a texture)
export const CAP = 750, STROKE = 150;
const S = STROKE, H = CAP;

// ---- distance primitives (2D, mm) ----
const len = (x, y) => Math.hypot(x, y);
function sdBox(px, py, x0, y0, x1, y1) {
  const cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, hx = (x1 - x0) / 2, hy = (y1 - y0) / 2;
  const dx = Math.abs(px - cx) - hx, dy = Math.abs(py - cy) - hy;
  return len(Math.max(dx, 0), Math.max(dy, 0)) + Math.min(Math.max(dx, dy), 0);
}
function sdRoundBox(px, py, x0, y0, x1, y1, r) {
  const cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, hx = (x1 - x0) / 2 - r, hy = (y1 - y0) / 2 - r;
  const dx = Math.abs(px - cx) - hx, dy = Math.abs(py - cy) - hy;
  return len(Math.max(dx, 0), Math.max(dy, 0)) + Math.min(Math.max(dx, dy), 0) - r;
}
const box = (x0, y0, x1, y1) => (x, y) => sdBox(x, y, x0, y0, x1, y1);
/** A straight stroke of width w along a -> b, square ends at a and b. */
const seg = (ax, ay, bx, by, w = S) => {
  const dx = bx - ax, dy = by - ay, L = Math.hypot(dx, dy), ux = dx / L, uy = dy / L;
  return (x, y) => { const s = (x - ax) * ux + (y - ay) * uy - L / 2, t = -(x - ax) * uy + (y - ay) * ux; return sdBox(s, t, -L / 2, -w / 2, L / 2, w / 2); };
};
/** A rounded-rectangle ring: outer box x0..x1, y0..y1 with corner radius r, stroke S inward (inner radius r - S). */
const ring = (x0, y0, x1, y1, r) => (x, y) =>
  Math.max(sdRoundBox(x, y, x0, y0, x1, y1, r), -sdRoundBox(x, y, x0 + S, y0 + S, x1 - S, y1 - S, Math.max(r - S, 0)));
const U = (...fs) => (x, y) => Math.min(...fs.map(f => f(x, y)));
const minus = (a, ...bs) => (x, y) => Math.max(a(x, y), ...bs.map(b => -b(x, y)));
const and = (a, b) => (x, y) => Math.max(a(x, y), b(x, y));

// ---- glyphs: [width, sdf builder] ----
const vbar = (x, y0 = 0, y1 = H) => box(x, y0, x + S, y1);
/** A stroke from a through b and on past b by a stroke width (for vertices cut level afterwards). */
const vee = (ax, ay, bx, by, w = S) => { const dx = bx - ax, dy = by - ay, L = Math.hypot(dx, dy); return seg(ax, ay, bx + dx / L * w * 1.5, by + dy / L * w * 1.5, w); };
const G = {
  A: W => { const c = W / 2; return U(seg(S * 0.62, -80, c, H + 80), seg(W - S * 0.62, -80, c, H + 80), box(W * 0.22, H * 0.24, W * 0.78, H * 0.24 + S * 0.85)); },
  B: W => U(vbar(0), ring(0, H / 2 - S / 2, W - 30, H, 165), ring(0, 0, W, H / 2 + S / 2, 175)),
  C: W => minus(ring(0, 0, W, H, 245), box(W / 2, H * 0.34, W + 1, H * 0.66)),
  D: W => U(vbar(0), ring(0, 0, W, H, 240)),
  E: W => U(vbar(0), box(0, H - S, W, H), box(0, H / 2 - S / 2, W * 0.86, H / 2 + S / 2), box(0, 0, W, S)),
  F: W => U(vbar(0), box(0, H - S, W, H), box(0, H / 2 - S * 0.6, W * 0.86, H / 2 + S * 0.4)),
  G: W => U(minus(ring(0, 0, W, H, 245), box(W / 2, H * 0.46 + S / 2, W + 1, H * 0.72)), box(W * 0.52, H * 0.46 - S / 2, W, H * 0.46 + S / 2)),
  H: W => U(vbar(0), vbar(W - S), box(0, H / 2 - S / 2, W, H / 2 + S / 2)),
  I: () => vbar(0),
  J: W => U(box(W - S, H * 0.3, W, H), and(ring(0, 0, W, H * 0.72, 210), box(-1, -1, W + 1, H * 0.36))),
  K: W => U(vbar(0), seg(S * 0.75, H * 0.36, W - S * 0.32, H + 90), seg(W * 0.42, H * 0.56, W - S * 0.32, -90)),
  L: W => U(vbar(0), box(0, 0, W, S)),
  // M, W: where two diagonals meet, their square ends leave a notch; the overlap of the two strokes run on past the point
  // (a small diamond) fills it to the outer edges' meeting point, blunted by a level cut
  M: W => U(vbar(0), vbar(W - S), seg(S * 0.5, H + 30, W / 2, H * 0.3), seg(W - S * 0.5, H + 30, W / 2, H * 0.3),
            and(and(vee(S * 0.5, H + 30, W / 2, H * 0.3), vee(W - S * 0.5, H + 30, W / 2, H * 0.3)), box(0, H * 0.3 - S * 0.42, W, H))),
  N: W => U(vbar(0), vbar(W - S), seg(S * 0.5, H + 60, W - S * 0.5, -60)),
  O: W => ring(0, 0, W, H, 250),
  P: W => U(vbar(0), ring(0, H * 0.4, W, H, 180)),
  Q: W => U(ring(0, 0, W, H, 250), seg(W * 0.64, H * 0.2, W + 30, -60, S * 0.86)),
  R: W => U(vbar(0), ring(0, H * 0.42, W - 20, H, 175), seg(W * 0.44, H * 0.46, W - S * 0.34, -80)),
  // S: two C's sharing the middle stroke; the upper one keeps its lower-left corner, the lower one its upper-right corner
  // (each loses the other corner whole, so the middle stroke curves up at the left and down at the right without a step)
  S: W => U(minus(ring(0, H / 2 - S / 2, W, H, 170), box(W - 170, H / 2 - S / 2 - 1, W + 1, H * 0.8)),
            minus(ring(0, 0, W, H / 2 + S / 2, 170), box(-1, H * 0.2, 170, H / 2 + S / 2 + 1))),
  T: W => U(box(0, H - S, W, H), vbar(W / 2 - S / 2)),
  U: W => U(box(0, H * 0.35, S, H), box(W - S, H * 0.35, W, H), and(ring(0, 0, W, H * 0.8, 230), box(-1, -1, W + 1, H * 0.42))),
  V: W => U(seg(S * 0.5, H + 80, W / 2, -60), seg(W - S * 0.5, H + 80, W / 2, -60)),
  W: W => U(seg(S * 0.45, H + 60, W * 0.28, -60, S * 0.92), seg(W * 0.72, -60, W - S * 0.45, H + 60, S * 0.92),
            seg(W * 0.28, -60, W / 2, H * 0.78, S * 0.92), seg(W * 0.72, -60, W / 2, H * 0.78, S * 0.92),
            and(and(vee(W * 0.28, -60, W / 2, H * 0.78, S * 0.92), vee(W * 0.72, -60, W / 2, H * 0.78, S * 0.92)), box(0, 0, W, H * 0.78 + S * 0.38))),
  X: W => U(seg(S * 0.45, H + 70, W - S * 0.45, -70), seg(W - S * 0.45, H + 70, S * 0.45, -70)),
  Y: W => U(seg(S * 0.45, H + 70, W / 2, H * 0.42), seg(W - S * 0.45, H + 70, W / 2, H * 0.42), box(W / 2 - S / 2, 0, W / 2 + S / 2, H * 0.48)),
  // Z: the diagonal runs on into both bars and is cut level at their inner edges (its square ends poked out of the bars)
  Z: W => U(box(0, H - S, W, H), box(0, 0, W, S), and(seg(W - S * 0.42 + 60, H + 30, S * 0.42 - 60, -30, S * 1.06), box(0, S - 1, W, H - S + 1))),
};
// advance widths (mm): every glyph fits a 1 m channel-letter cell
export const WIDTH = {A: 640, B: 560, C: 600, D: 620, E: 500, F: 480, G: 620, H: 600, I: 150, J: 480, K: 580, L: 480, M: 760, N: 620,
  O: 640, P: 540, Q: 640, R: 580, S: 560, T: 600, U: 600, V: 640, W: 860, X: 620, Y: 640, Z: 560};
export const LETTERS = Object.keys(WIDTH);

export function glyph(ch) {
  const W = WIDTH[ch], f = G[ch](W);
  if (W === undefined) throw new Error('no glyph ' + ch);
  // flat tops, bottoms and sides: everything clipped to the glyph box
  return {width: W, sdf: (x, y) => Math.max(f(x, y), sdBox(x, y, 0, 0, W, H))};
}

/** A word laid out with the glyph advances and a gap between letters (mm); spaces advance `space`. */
export function textSdf(text, {gap = 110, space = 320} = {}) {
  const parts = []; let x = 0;
  for (const ch of text.toUpperCase()) {
    if (ch === ' ') { x += space; continue; }
    const g = glyph(ch); parts.push([x, g]); x += g.width + gap;
  }
  const width = Math.max(0, x - gap);
  return {width, sdf: (px, py) => { let d = Infinity; for (const [ox, g] of parts) if (px > ox - 200 && px < ox + g.width + 200) d = Math.min(d, g.sdf(px - ox, py)); return d === Infinity ? 1e3 : d; }};
}

// ---- contours ----
/** Zero contour of the glyph sdf: marching squares on a `step` mm grid, joined into loops, simplified (tolerance mm). */
export function outlines(ch, step = 2, tol = 0.6, level = 0) {
  const {width, sdf: base} = glyph(ch), sdf = (x, y) => base(x, y) - level;   // level > 0: the outline grown by that much
  // the grid is offset by an odd fraction of a step so no sample falls exactly on a glyph edge (a zero sample makes several
  // segments meet at one grid point, and the loops could not be joined)
  const x0 = -2 * step + 0.3183 * step, y0 = -2 * step + 0.2718 * step, nx = Math.ceil((width + 4 * step) / step) + 1, ny = Math.ceil((H + 4 * step) / step) + 1;
  const v = new Float64Array(nx * ny);
  for (let j = 0; j < ny; j++) for (let i = 0; i < nx; i++) { const d = sdf(x0 + i * step, y0 + j * step); v[j * nx + i] = d === 0 ? 1e-7 : d; }
  const segs = [];
  // a grid edge's crossing, always interpolated from its lower grid index (both cells sharing the edge get the same point)
  const cross = (ia, ja, ib, jb) => {
    if (ja * nx + ia > jb * nx + ib) [ia, ja, ib, jb] = [ib, jb, ia, ja];
    const va = v[ja * nx + ia], vb = v[jb * nx + ib], t = va / (va - vb);
    return [x0 + (ia + (ib - ia) * t) * step, y0 + (ja + (jb - ja) * t) * step];
  };
  for (let j = 0; j + 1 < ny; j++) for (let i = 0; i + 1 < nx; i++) {
    const c = [v[j * nx + i], v[j * nx + i + 1], v[(j + 1) * nx + i + 1], v[(j + 1) * nx + i]];   // bl, br, tr, tl
    const g = [[i, j], [i + 1, j], [i + 1, j + 1], [i, j + 1]];
    const k = (c[0] < 0 ? 1 : 0) | (c[1] < 0 ? 2 : 0) | (c[2] < 0 ? 4 : 0) | (c[3] < 0 ? 8 : 0);
    if (k === 0 || k === 15) continue;
    const e = n => cross(...g[n], ...g[(n + 1) % 4]);   // edge n: bottom 0, right 1, top 2, left 3
    // segments run with the inside on their right (clockwise round the solid); loops are turned as needed below
    const T = {1: [[3, 0]], 2: [[0, 1]], 3: [[3, 1]], 4: [[1, 2]], 6: [[0, 2]], 7: [[3, 2]], 8: [[2, 3]], 9: [[2, 0]], 11: [[2, 1]],
      12: [[1, 3]], 13: [[1, 0]], 14: [[0, 3]]};
    let list = T[k];
    if (k === 5 || k === 10) {   // saddles: the centre decides
      const mid = (c[0] + c[1] + c[2] + c[3]) / 4 < 0;
      list = k === 5 ? (mid ? [[3, 2], [1, 0]] : [[3, 0], [1, 2]]) : (mid ? [[0, 3], [2, 1]] : [[0, 1], [2, 3]]);
    }
    for (const [a, b] of list) segs.push([e(a), e(b)]);
  }
  // join: each segment's end is the next one's start
  const key = q => Math.round(q[0] * 1e4) + ',' + Math.round(q[1] * 1e4), byStart = new Map();
  segs.forEach((s, n) => byStart.set(key(s[0]), n));
  const used = new Uint8Array(segs.length), loops = [];
  for (let n = 0; n < segs.length; n++) {
    if (used[n]) continue;
    const loop = []; let m = n;
    while (m !== undefined && !used[m]) { used[m] = 1; loop.push(segs[m][0]); m = byStart.get(key(segs[m][1])); }
    if (loop.length > 2) loops.push(loop);
  }
  const simple = loops.map(l => snapCorners(simplifyClosed(l, tol))).filter(l => l.length >= 3);
  // nesting: a loop inside an odd number of others is a hole of the smallest outer one round it
  const area = l => l.reduce((s, p, i) => { const q = l[(i + 1) % l.length]; return s + p[0] * q[1] - q[0] * p[1]; }, 0) / 2;
  const inside = (pt, l) => { let c = false; for (let i = 0, j = l.length - 1; i < l.length; j = i++) { const [xi, yi] = l[i], [xj, yj] = l[j];
    if ((yi > pt[1]) !== (yj > pt[1]) && pt[0] < (xj - xi) * (pt[1] - yi) / (yj - yi) + xi) c = !c; } return c; };
  const depth = simple.map((l, a) => simple.reduce((d, o, b) => d + (a !== b && inside(l[0], o) ? 1 : 0), 0));
  const ccw = (l, want) => (area(l) > 0) === want ? l : l.slice().reverse();
  const shapes = [];
  simple.forEach((l, a) => { if (depth[a] % 2 === 0) shapes.push({outer: ccw(l, true), holes: [], area: Math.abs(area(l)), src: l}); });
  simple.forEach((l, a) => {
    if (depth[a] % 2 === 0) return;
    const owners = shapes.filter(s => inside(l[0], s.src)).sort((p, q) => p.area - q.area);
    if (owners.length) owners[0].holes.push(ccw(l, false));
  });
  return {width, shapes: shapes.map(({outer, holes}) => ({outer, holes}))};
}

/**
 * Sharp corners back: marching squares cut a square corner by up to a grid step, leaving a short edge between two long ones
 * (a 1..2 mm sliver face once extruded). Such a short edge, where the long neighbours turn by more than `minTurn` degrees,
 * is replaced by the point where the neighbours' lines meet. Arcs (many medium edges, small turns) are left as they are.
 */
function snapCorners(loop, snap = 4, minTurn = 25) {
  let pts = loop.slice(), changed = true;
  const L = (a, b) => Math.hypot(b[0] - a[0], b[1] - a[1]);
  while (changed && pts.length > 3) {
    changed = false;
    const n = pts.length;
    for (let i = 0; i < n && !changed; i++) {
      // a long edge a -> b, then k >= 1 short edges b -> ... -> c, then a long edge c -> d
      const a = pts[(i - 1 + n) % n], b = pts[i];
      if (L(a, b) < 2 * snap) continue;
      let k = 0;
      while (k < 4 && L(pts[(i + k) % n], pts[(i + k + 1) % n]) < snap) k++;
      if (k === 0) continue;
      const c = pts[(i + k) % n], d = pts[(i + k + 1) % n];
      if (L(c, d) < 2 * snap) continue;
      const u = [b[0] - a[0], b[1] - a[1]], w = [d[0] - c[0], d[1] - c[1]], den = u[0] * w[1] - u[1] * w[0];
      const turn = Math.acos(Math.max(-1, Math.min(1, (u[0] * w[0] + u[1] * w[1]) / (Math.hypot(...u) * Math.hypot(...w))))) * 180 / Math.PI;
      if (turn < minTurn || Math.abs(den) < 1e-9) continue;
      const t = ((c[0] - a[0]) * w[1] - (c[1] - a[1]) * w[0]) / den, p = [a[0] + u[0] * t, a[1] + u[1] * t];
      if (L(p, b) > 3 * snap || L(p, c) > 3 * snap) continue;   // the lines meet far away: not a cut corner
      // replace b .. c (k + 1 points, possibly wrapping past the end) by p
      const drop = new Set(Array.from({length: k + 1}, (_, m) => (i + m) % n));
      const next = [];
      for (let m = 0; m < n; m++) { if (m === i) next.push(p); else if (!drop.has(m)) next.push(pts[m]); }
      pts = next; changed = true;
    }
  }
  return pts;
}

/** Ramer-Douglas-Peucker on a closed loop (split at its two farthest-apart points). */
function simplifyClosed(loop, tol) {
  let a = 0, b = 0, best = -1;
  for (let i = 0; i < loop.length; i++) { const d = (loop[i][0] - loop[0][0]) ** 2 + (loop[i][1] - loop[0][1]) ** 2; if (d > best) { best = d; b = i; } }
  const half1 = loop.slice(a, b + 1), half2 = loop.slice(b).concat([loop[0]]);
  const r1 = rdp(half1, tol), r2 = rdp(half2, tol);
  return r1.slice(0, -1).concat(r2.slice(0, -1));
}
function rdp(pts, tol) {
  if (pts.length < 3) return pts;
  const [ax, ay] = pts[0], [bx, by] = pts[pts.length - 1], dx = bx - ax, dy = by - ay, L = Math.hypot(dx, dy) || 1e-9;
  let far = 0, idx = 0;
  for (let i = 1; i < pts.length - 1; i++) { const d = Math.abs((pts[i][0] - ax) * dy - (pts[i][1] - ay) * dx) / L; if (d > far) { far = d; idx = i; } }
  if (far <= tol) return [pts[0], pts[pts.length - 1]];
  return rdp(pts.slice(0, idx + 1), tol).slice(0, -1).concat(rdp(pts.slice(idx), tol));
}
