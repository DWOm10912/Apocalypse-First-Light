// Cabin Exterior V1 (docs/models/cabin_exterior_v1.md): the small polygon kit of tools/build-cabin-exterior-v1.mjs.
// A piece is a list of convex polygons in block units (0..1 is the cell, geometry may reach about 0.3 outside it), each
// with a texture, per-vertex normals (smooth surfaces) and either explicit UVs (0..1 of its sprite) or a planar
// projection (1 block = 1 texture tile, or `scale` tiles a block). Projected polygons are split along whole-tile lines so every part's UVs lie
// inside one tile (an atlas sprite cannot repeat), then shifted into 0..1; the textures tile, so the splits are seamless.
// Pieces are written as JSON quads (CabinMeshModel reads them; it rotates, culls, lights and colours them in game).
// Polygons face outward counter-clockwise (vanilla's winding); triangles become quads with the last corner repeated.

export const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]], add = (a, b) => [a[0] + b[0], a[1] + b[1], a[2] + b[2]];
export const scl = (a, k) => [a[0] * k, a[1] * k, a[2] * k], dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
export const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
export const norm = a => { const l = Math.hypot(...a) || 1; return [a[0] / l, a[1] / l, a[2] / l]; };
export const lerp = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);
/** Newell normal of a polygon (unit; outward for counter-clockwise corners). */
export function newell(pts) {
  let n = [0, 0, 0];
  for (let i = 0; i < pts.length; i++) { const a = pts[i], b = pts[(i + 1) % pts.length]; n = add(n, [(a[1] - b[1]) * (a[2] + b[2]), (a[2] - b[2]) * (a[0] + b[0]), (a[0] - b[0]) * (a[1] + b[1])]); }
  return norm(n);
}
function area(pts) { let n = [0, 0, 0]; for (let i = 1; i + 1 < pts.length; i++) n = add(n, cross(sub(pts[i], pts[0]), sub(pts[i + 1], pts[0]))); return Math.hypot(...n) / 2; }

export class Piece {
  constructor(name) { this.name = name; this.polys = []; }
  /** A polygon: t texture name, v corners, n normals (one, per corner, or null: the face's), uv per corner or a projection. */
  poly(t, v, {n = null, uv = null, proj = null, off = [0, 0], swap = false, scale = 1} = {}) {
    if (v.length < 3) return this;
    const fn = newell(v);
    const ns = n == null ? v.map(() => fn) : typeof n[0] === 'number' ? v.map(() => norm(n)) : n.map(norm);
    this.polys.push({t, v, n: ns, uv, proj, off, swap, scale, fn});
    return this;
  }
  /** Every polygon of another piece (optionally transformed: f maps a point, fn a normal). */
  merge(o, f = p => p, fnrm = n => n) {
    for (const p of o.polys) this.polys.push({...p, v: p.v.map(f), n: p.n.map(fnrm), fn: fnrm(p.fn)});
    return this;
  }
}

/** A copy turned clockwise (seen from above) r quarter turns about the cell's vertical centre line. */
export function turned(piece, r, name = piece.name) {
  const out = new Piece(name);
  const f = p => { let [x, y, z] = p; for (let i = 0; i < r; i++) [x, z] = [1 - z, x]; return [x, y, z]; };
  const fn = n => { let [x, y, z] = n; for (let i = 0; i < r; i++) [x, z] = [-z, x]; return [x, y, z]; };
  out.merge(piece, f, fn);
  if (r % 2) for (const p of out.polys) if (p.proj === 'x' || p.proj === 'z') p.proj = p.proj === 'x' ? 'z' : 'x';
  return out;
}
/** A copy mirrored in x (x -> 1 - x), corners reversed to keep the winding. */
export function mirroredX(piece, name = piece.name) {
  const out = new Piece(name);
  for (const p of piece.polys) {
    const idx = p.v.map((_, i) => p.v.length - 1 - i);
    out.polys.push({...p, v: idx.map(i => [1 - p.v[i][0], p.v[i][1], p.v[i][2]]), n: idx.map(i => [-p.n[i][0], p.n[i][1], p.n[i][2]]),
      uv: p.uv ? idx.map(i => p.uv[i]) : null, fn: [-p.fn[0], p.fn[1], p.fn[2]]});
  }
  return out;
}

/** Clip a polygon record to the half-space a.p <= d (positions, normals and explicit UVs interpolated). */
function clipHalf(rec, a, d) {
  const {v, n, uv} = rec, ov = [], on = [], ouv = uv ? [] : null, k = v.length;
  for (let i = 0; i < k; i++) {
    const j = (i + 1) % k, si = dot(a, v[i]) - d, sj = dot(a, v[j]) - d;
    if (si <= 1e-9) { ov.push(v[i]); on.push(n[i]); if (uv) ouv.push(uv[i]); }
    if ((si < -1e-9 && sj > 1e-9) || (si > 1e-9 && sj < -1e-9)) {
      const t = si / (si - sj);
      ov.push(lerp(v[i], v[j], t)); on.push(norm(lerp(n[i], n[j], t))); if (uv) ouv.push(lerp(uv[i], uv[j], t));
    }
  }
  return {...rec, v: ov, n: on, uv: ouv};
}
/** A copy clipped to every half-space [a, d] (a.p <= d); polygons cut away vanish. */
export function clipped(piece, planes, name = piece.name) {
  const out = new Piece(name);
  for (const p of piece.polys) {
    let r = p;
    for (const [a, d] of planes) { r = clipHalf(r, a, d); if (r.v.length < 3) break; }
    if (r.v.length >= 3 && area(r.v) > 1e-7) out.polys.push(r);
  }
  return out;
}

// ------------------------------------------------------------------------------------------------- shapes

/** Box faces ('n' -z, 's' +z, 'w' -x, 'e' +x, 'd' -y, 'u' +y), each a projected quad; tex: a name or {face: name}. */
export function box(pc, tex, [x0, y0, z0], [x1, y1, z1], faces = 'nsewud', opts = {}) {
  const T = f => typeof tex === 'string' ? tex : tex[f] ?? tex.all;
  const F = {
    n: [[x1, y0, z0], [x0, y0, z0], [x0, y1, z0], [x1, y1, z0]], s: [[x0, y0, z1], [x1, y0, z1], [x1, y1, z1], [x0, y1, z1]],
    w: [[x0, y0, z0], [x0, y0, z1], [x0, y1, z1], [x0, y1, z0]], e: [[x1, y0, z1], [x1, y0, z0], [x1, y1, z0], [x1, y1, z1]],
    d: [[x0, y0, z0], [x1, y0, z0], [x1, y0, z1], [x0, y0, z1]], u: [[x0, y1, z1], [x1, y1, z1], [x1, y1, z0], [x0, y1, z0]],
  };
  for (const f of faces) if (F[f]) pc.poly(T(f), F[f], opts[f] || opts.all || {});
  return pc;
}

/**
 * M1.1 (2026-10-12): the closed outer surface of a union of boxes. Boxes are [tex, min, max, opts] as box() takes them;
 * a face is drawn only where the union meets air, so every part is closed from every side (the user saw a single-sided
 * window lining turn see-through from the side) with no inner faces and no coplanar overlaps (z-fighting). A face cell
 * takes the texture of the first box that holds the cell; neighbouring cells with the same texture merge into rectangles.
 */
export function solid(pc, boxes) {
  const k = v => Math.round(v * 1e6) / 1e6;
  const axes = [0, 1, 2].map(a => [...new Set(boxes.flatMap(b => [k(b[1][a]), k(b[2][a])]))].sort((p, q) => p - q));
  const [X, Y, Z] = axes, nx = X.length - 1, ny = Y.length - 1, nz = Z.length - 1;
  const own = new Int32Array(Math.max(0, nx * ny * nz)).fill(-1), at = (i, j, l) => (i * ny + j) * nz + l;
  boxes.forEach(([, a, b], bi) => {
    const r = ax => [axes[ax].indexOf(k(a[ax])), axes[ax].indexOf(k(b[ax]))];
    const [[i0, i1], [j0, j1], [l0, l1]] = [r(0), r(1), r(2)];
    for (let i = i0; i < i1; i++) for (let j = j0; j < j1; j++) for (let l = l0; l < l1; l++) if (own[at(i, j, l)] < 0) own[at(i, j, l)] = bi;
  });
  const cell = (c) => c[0] < 0 || c[1] < 0 || c[2] < 0 || c[0] >= nx || c[1] >= ny || c[2] >= nz ? -1 : own[at(c[0], c[1], c[2])];
  const texOf = (bi, f) => { const t = boxes[bi][0], o = boxes[bi][3] || {}; return [typeof t === 'string' ? t : t[f] ?? t.all, o[f] || o.all || {}]; };
  // per face: its axis, the side (-1 / +1) and the two in-plane axes
  for (const [f, ax, sg] of [['n', 2, -1], ['s', 2, 1], ['w', 0, -1], ['e', 0, 1], ['d', 1, -1], ['u', 1, 1]]) {
    const [pa, pb] = [0, 1, 2].filter(a => a !== ax), na = axes[pa].length - 1, nb = axes[pb].length - 1;
    for (let p = 0; p < axes[ax].length - 1; p++) {
      const keys = new Array(na * nb).fill(null), tex = [];
      for (let a = 0; a < na; a++) for (let b = 0; b < nb; b++) {
        const c = [0, 0, 0]; c[ax] = p; c[pa] = a; c[pb] = b;
        const bi = cell(c);
        if (bi < 0) continue;
        const o = [...c]; o[ax] += sg;
        if (cell(o) >= 0) continue;
        const [t, opt] = texOf(bi, f), key = t + '|' + JSON.stringify(opt);
        keys[a * nb + b] = key; tex[a * nb + b] = [t, opt];
      }
      // greedy rectangles over the plane's cells
      for (let a = 0; a < na; a++) for (let b = 0; b < nb; b++) {
        const key = keys[a * nb + b];
        if (key == null) continue;
        let b1 = b + 1;
        while (b1 < nb && keys[a * nb + b1] === key) b1++;
        let a1 = a + 1;
        while (a1 < na) { let ok = true; for (let q = b; q < b1; q++) if (keys[a1 * nb + q] !== key) { ok = false; break; } if (!ok) break; a1++; }
        for (let r = a; r < a1; r++) for (let q = b; q < b1; q++) keys[r * nb + q] = null;
        const lo = [0, 0, 0], hi = [0, 0, 0], plane = sg < 0 ? axes[ax][p] : axes[ax][p + 1];
        lo[ax] = hi[ax] = plane; lo[pa] = axes[pa][a]; hi[pa] = axes[pa][a1]; lo[pb] = axes[pb][b]; hi[pb] = axes[pb][b1];
        const [t, opt] = tex[a * nb + b];
        box(pc, t, lo, hi, f, {[f]: opt});
      }
    }
  }
  return pc;
}

/**
 * A cylinder along `axis` ('x' | 'y' | 'z') through centre c (the other two coordinates), from a to b, radius r, seg
 * sides, smooth. Skin UVs explicit: u along the axis (metres from a, so a..b must stay within one block), v round it.
 * caps: which ends get a disc ('a', 'b'), mapped to the end texture's disc of radius 0.47 round its middle.
 */
export function cylinder(pc, skin, end, axis, c, a, b, r, seg, caps = '') {
  const P = (t, ang, rr = r) => {
    const u = c[0] + Math.cos(ang) * rr, w = c[1] + Math.sin(ang) * rr;
    return axis === 'x' ? [t, u, w] : axis === 'y' ? [w, t, u] : [u, w, t];
  };
  const N = ang => { const u = Math.cos(ang), w = Math.sin(ang); return axis === 'x' ? [0, u, w] : axis === 'y' ? [w, 0, u] : [u, w, 0]; };
  for (let i = 0; i < seg; i++) {
    const a0 = i / seg * Math.PI * 2, a1 = (i + 1) / seg * Math.PI * 2;
    const v0 = i / seg, v1 = (i + 1) / seg;
    // corners counter-clockwise from outside: (a, a0), (a, a1), (b, a1), (b, a0) for the x/y/z right-handed frames
    const quad = [P(a, a0), P(b, a0), P(b, a1), P(a, a1)], nq = [N(a0), N(a0), N(a1), N(a1)];
    const uvq = [[0, v0], [b - a, v0], [b - a, v1], [0, v1]];
    const fnTest = newell(quad), mid = N((a0 + a1) / 2);
    if (dot(fnTest, mid) < 0) { quad.reverse(); nq.reverse(); uvq.reverse(); }
    pc.poly(skin, quad, {n: nq, uv: uvq});
  }
  const disc = (t, outward) => {
    const pts = [], uvs = [];
    for (let i = 0; i < seg; i++) { const ang = i / seg * Math.PI * 2; pts.push(P(t, ang)); uvs.push([0.5 + 0.47 * Math.cos(ang), 0.5 - 0.47 * Math.sin(ang)]); }
    const nrm = axis === 'x' ? [outward, 0, 0] : axis === 'y' ? [0, outward, 0] : [0, 0, outward];
    if (dot(newell(pts), nrm) < 0) { pts.reverse(); uvs.reverse(); }
    pc.poly(end, pts, {uv: uvs});
  };
  if (caps.includes('a')) disc(a, -1);
  if (caps.includes('b')) disc(b, 1);
  return pc;
}

// ------------------------------------------------------------------------------------------------- output

/** Projected UVs of a polygon: by its projection axis (or its normal's main axis), 1 block = 1 tile (x scale), offset off (blocks). */
function project(rec) {
  const fn = rec.fn, ax = rec.proj ?? (Math.abs(fn[1]) >= Math.abs(fn[0]) && Math.abs(fn[1]) >= Math.abs(fn[2]) ? 'y' : Math.abs(fn[0]) >= Math.abs(fn[2]) ? 'x' : 'z');
  const s = ax === 'x' ? Math.sign(fn[0]) || 1 : ax === 'y' ? Math.sign(fn[1]) || 1 : Math.sign(fn[2]) || 1;
  return rec.v.map(([x, y, z]) => {
    let u, v;
    if (ax === 'y') { u = x; v = s > 0 ? z : -z; }
    else if (ax === 'z') { u = s > 0 ? x : -x; v = -y; }
    else { u = s > 0 ? -z : z; v = -y; }
    if (rec.swap) [u, v] = [v, -u];
    const k = rec.scale ?? 1;
    return [(u + rec.off[0]) * k, (v + rec.off[1]) * k];
  });
}
const snap = v => Math.abs(v - Math.round(v)) < 1e-6 ? Math.round(v) : v;
/** Split a projected polygon into its parts inside whole tiles, UVs shifted into 0..1. */
function tiles(rec) {
  const uv = project(rec).map(([u, v]) => [snap(u), snap(v)]);
  const us = uv.map(p => p[0]), vs = uv.map(p => p[1]);
  const out = [];
  for (let iu = Math.floor(Math.min(...us)); iu < Math.max(...us); iu++) for (let iv = Math.floor(Math.min(...vs)); iv < Math.max(...vs); iv++) {
    // clip in UV space (an extra 2-D "position" carried as the uv), positions and normals interpolated alongside
    let r = {v: rec.v, n: rec.n, uv};
    const cut = (sel, lim, sgn) => {   // keep sgn * (uv[sel] - lim) <= 0
      const ov = [], on = [], ouv = [], k = r.v.length;
      for (let i = 0; i < k; i++) {
        const j = (i + 1) % k, si = sgn * (r.uv[i][sel] - lim), sj = sgn * (r.uv[j][sel] - lim);
        if (si <= 1e-9) { ov.push(r.v[i]); on.push(r.n[i]); ouv.push(r.uv[i]); }
        if ((si < -1e-9 && sj > 1e-9) || (si > 1e-9 && sj < -1e-9)) {
          const t = si / (si - sj);
          ov.push(lerp(r.v[i], r.v[j], t)); on.push(norm(lerp(r.n[i], r.n[j], t))); ouv.push(lerp(r.uv[i], r.uv[j], t));
        }
      }
      r = {v: ov, n: on, uv: ouv};
    };
    cut(0, iu + 1, 1); if (r.v.length >= 3) cut(0, iu, -1); if (r.v.length >= 3) cut(1, iv + 1, 1); if (r.v.length >= 3) cut(1, iv, -1);
    if (r.v.length < 3 || area(r.v) < 1e-7) continue;
    out.push({...rec, v: r.v, n: r.n, uv: r.uv.map(([u, v]) => [Math.min(1, Math.max(0, u - iu)), Math.min(1, Math.max(0, v - iv))])});
  }
  return out;
}

const R5 = v => Math.round(v * 1e5) / 1e5 || 0;
/** A piece as {textures: [...], quads: [[tex, x, y, z, u, v, nx, ny, nz] x 4, ...]} (convex polygons fanned into quads). */
export function serialise(piece, texPrefix) {
  const texList = [], texIdx = new Map(), quads = [];
  const T = t => { if (!texIdx.has(t)) { texIdx.set(t, texList.length); texList.push(texPrefix + t); } return texIdx.get(t); };
  for (const rec of piece.polys) {
    const parts = rec.uv ? [rec] : tiles(rec);
    for (const p of parts) {
      for (const u of p.uv) if (u[0] < -1e-6 || u[0] > 1 + 1e-6 || u[1] < -1e-6 || u[1] > 1 + 1e-6) throw new Error(`${piece.name}: UV outside its sprite ${u}`);
      const k = p.v.length, ti = T(p.t);
      const corner = i => [...p.v[i].map(R5), R5(p.uv[i][0]), R5(p.uv[i][1]), ...p.n[i].map(R5)];
      for (let i = 1; i + 1 < k; i += 2) {
        const ids = i + 2 < k ? [0, i, i + 1, i + 2] : [0, i, i + 1, i + 1];
        quads.push([ti, ...ids.flatMap(corner)]);
      }
    }
  }
  return {textures: texList, quads};
}
