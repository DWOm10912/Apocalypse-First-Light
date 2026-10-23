// Sling swivels (docs/native_guns/attachments/sling_physics_v1.md 3): the D-ring loops a two-point sling's snap hooks clip
// onto. A loop hangs in the gun's x-y plane (its bars run across the gun, along x, so a strap's width fits through it and
// the strap leaves along z), its top bar through the gun's lug / stud, its bottom bar the anchor the sling physics pins
// the hook to (gun JSON sling_slot.strap). Units: the gun's model pixels.
//   dRing(...)            the loop's centre line: a rounded rectangle, top bar centre first
//   sweepRing(part, ...)  a round wire swept along it (Part from cube-slab-mesh-lib)
//   remapRing(part, ...)  an existing ring part (BR-51's Blender loops lay in the y-z plane, across the strap's way)
//                         bent onto a D-ring, faces and UVs kept

const add = (a, b) => [a[0] + b[0], a[1] + b[1], a[2] + b[2]], mul = (a, s) => [a[0] * s, a[1] * s, a[2] * s];

/** Centre line of a D-ring in the x-y plane at z: width (x) and height (y) between bar centres, corner radius. */
export function dRing({x = 0, top, bottom, z, width, corner}) {
  const hw = width / 2, h = top - bottom, rc = Math.min(corner, hw, h / 2);
  const sx = hw - rc, sy = h / 2 - rc, cy = (top + bottom) / 2;
  // perimeter pieces from the top centre going +x (clockwise seen from +z): top half bar, corner, side, corner, bottom,
  // corner, side, corner, top half bar
  const arc = Math.PI / 2 * rc, L = [sx, arc, 2 * sy, arc, 2 * sx, arc, 2 * sy, arc, sx];
  const total = L.reduce((a, b) => a + b, 0);
  function at(s) {
    s = ((s % total) + total) % total;
    let k = 0;
    while (k < L.length - 1 && s > L[k]) { s -= L[k]; k++; }
    const corners = [[sx, sy, Math.PI / 2], [sx, -sy, 0], [-sx, -sy, -Math.PI / 2], [-sx, sy, Math.PI]];
    let p, n;
    switch (k) {
      case 0: p = [s, h / 2]; n = [0, 1]; break;
      case 2: p = [hw, sy - s]; n = [1, 0]; break;
      case 4: p = [sx - s, -h / 2]; n = [0, -1]; break;
      case 6: p = [-hw, -sy + s]; n = [-1, 0]; break;
      case 8: p = [-sx + s, h / 2]; n = [0, 1]; break;
      default: { const [ox, oy, a0] = corners[(k - 1) / 2], a = a0 - s / rc; n = [Math.cos(a), Math.sin(a)]; p = [ox + rc * n[0], oy + rc * n[1]]; }
    }
    return {p: [x + p[0], cy + p[1], z], n: [n[0], n[1], 0]};
  }
  // u in [0, 1) round the ring with the corners taking `cornerWeight` times their share, so a few segments still round them
  const W = L.map((l, i) => i % 2 ? l * 3 : l), wTotal = W.reduce((a, b) => a + b, 0);
  function atU(u) {
    let w = (((u % 1) + 1) % 1) * wTotal, s = 0, k = 0;
    while (k < W.length - 1 && w > W[k]) { w -= W[k]; s += L[k]; k++; }
    return at(s + w * L[k] / W[k]);
  }
  return {at, atU, total, anchor: [x, bottom, z]};
}

/** A round wire of radius r swept along the closed ring (segments along it, sides round it), outward faces. */
export function sweepRing(part, ring, r, {segments = 24, sides = 6} = {}) {
  const rings = [];
  for (let i = 0; i < segments; i++) {
    const {p, n} = ring.atU(i / segments);
    rings.push(Array.from({length: sides}, (_, j) => { const a = 2 * Math.PI * j / sides; return part.vtx(add(p, add(mul(n, r * Math.cos(a)), [0, 0, r * Math.sin(a)]))); }));
  }
  for (let i = 0; i < segments; i++) {
    const A = rings[i], B = rings[(i + 1) % segments], c = ring.atU((i + 0.5) / segments).p;
    for (let j = 0; j < sides; j++) {
      const ids = [A[j], A[(j + 1) % sides], B[(j + 1) % sides], B[j]];
      const mid = mul(ids.reduce((s, id) => add(s, part.v[id]), [0, 0, 0]), 1 / 4);
      part.face(ids, [mid[0] - c[0], mid[1] - c[1], mid[2] - c[2]]);
    }
  }
}

/**
 * Bends a ring part of a low-poly export (vertices [[x, y, z]], faces {v, uv}) lying in the y-z plane onto the D-ring
 * (x-y plane): each vertex keeps its angle round the old centre (mapped onto the D-ring's length), its radial offset from
 * the old centre line (onto the D-ring's in-plane normal) and its x (onto z), scaled to the new wire radius. Faces, UVs
 * and topology stay, so the baked texture of the old loop keeps covering it. Returns the new part.
 */
export function remapRing(part, ring, r) {
  const used = [...new Set(part.faces.flatMap(f => f.v))], V = used.map(i => part.vertices[i]);
  const lo = [0, 1, 2].map(k => Math.min(...V.map(v => v[k]))), hi = [0, 1, 2].map(k => Math.max(...V.map(v => v[k])));
  const c = [(lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, (lo[2] + hi[2]) / 2], wire = (hi[0] - lo[0]) / 2;
  const R0 = V.reduce((s, v) => s + Math.hypot(v[1] - c[1], v[2] - c[2]), 0) / V.length, k = r / wire;
  const vertices = part.vertices.map((v, i) => {
    if (!used.includes(i)) return v;
    const dy = v[1] - c[1], dz = v[2] - c[2], t = Math.atan2(dz, dy);   // 0 at the top, growing toward +z
    const a = (Math.hypot(dy, dz) - R0) * k, b = (v[0] - c[0]) * k;
    const {p, n} = ring.atU(t / (2 * Math.PI));
    return add(p, add(mul(n, a), [0, 0, b]));
  });
  // the bend may mirror the old winding: faces must face away from the centre line (most faces decide)
  const outward = f => {
    const P = f.v.map(i => vertices[i]), m = mul(P.reduce((s, q) => add(s, q), [0, 0, 0]), 1 / P.length);
    let nx = 0, ny = 0, nz = 0;
    for (let q = 0; q < P.length; q++) { const a = P[q], b = P[(q + 1) % P.length]; nx += a[1] * b[2] - a[2] * b[1]; ny += a[2] * b[0] - a[0] * b[2]; nz += a[0] * b[1] - a[1] * b[0]; }
    // nearest centre line point
    let best = null, d = Infinity;
    for (let i = 0; i < 256; i++) { const q = ring.atU(i / 256).p, e = Math.hypot(m[0] - q[0], m[1] - q[1], m[2] - q[2]); if (e < d) { d = e; best = q; } }
    return nx * (m[0] - best[0]) + ny * (m[1] - best[1]) + nz * (m[2] - best[2]) > 0;
  };
  const flip = part.faces.filter(f => !outward({v: f.v})).length > part.faces.length / 2;
  return {...part, vertices, faces: part.faces.map(f => flip ? {...f, v: f.v.slice().reverse(), uv: f.uv.map(u => u.slice()).reverse()} : {...f, v: f.v.slice(), uv: f.uv.map(u => u.slice())})};
}

/** A box between lo and hi with every edge chamfered by c (a 26-face rounded block; c 0 = plain box). */
export function box(part, lo, hi, c = 0) {
  const ctr = mul(add(lo, hi), 0.5), h = [0, 1, 2].map(k => (hi[k] - lo[k]) / 2);
  c = Math.min(c, ...h.map(v => v * 0.45));
  if (c <= 0) {
    const P = (sx, sy, sz) => part.vtx([ctr[0] + sx * h[0], ctr[1] + sy * h[1], ctr[2] + sz * h[2]]);
    const v = {}; for (const sx of [-1, 1]) for (const sy of [-1, 1]) for (const sz of [-1, 1]) v[`${sx}${sy}${sz}`] = P(sx, sy, sz);
    const q = (a, b, c2, d, n) => part.face([v[a], v[b], v[c2], v[d]], n);
    q('-1-1-1', '1-1-1', '1-11', '-1-11', [0, -1, 0]); q('-11-1', '-111', '111', '11-1', [0, 1, 0]);
    q('-1-1-1', '-11-1', '11-1', '1-1-1', [0, 0, -1]); q('-1-11', '1-11', '111', '-111', [0, 0, 1]);
    q('-1-1-1', '-1-11', '-111', '-11-1', [-1, 0, 0]); q('1-1-1', '11-1', '111', '1-11', [1, 0, 0]);
    return;
  }
  // chamfered: each corner of the box splits into three points (one per face it touches), faces = 6 sides, 12 edge
  // strips, 8 corner triangles
  const id = {};
  const pt = (sx, sy, sz, axis) => {
    const key = `${sx}${sy}${sz}${axis}`;
    if (id[key] === undefined) {
      const p = [sx * h[0], sy * h[1], sz * h[2]];
      for (let k = 0; k < 3; k++) if (k !== axis) p[k] -= [sx, sy, sz][k] * c;
      id[key] = part.vtx(add(ctr, p));
    }
    return id[key];
  };
  const S = [-1, 1];
  for (let axis = 0; axis < 3; axis++) for (const s of S) {
    const [a, b] = [0, 1, 2].filter(k => k !== axis), n = [0, 0, 0]; n[axis] = s;
    const corner = (ua, ub) => { const sg = [0, 0, 0]; sg[axis] = s; sg[a] = ua; sg[b] = ub; return pt(sg[0], sg[1], sg[2], axis); };
    part.face([corner(-1, -1), corner(1, -1), corner(1, 1), corner(-1, 1)], n);
  }
  for (let axis = 0; axis < 3; axis++) {   // edges along `axis`: between the faces of the other two axes
    const [a, b] = [0, 1, 2].filter(k => k !== axis);
    for (const sa of S) for (const sb of S) {
      const P = (sx, axisFace) => { const sg = [0, 0, 0]; sg[axis] = sx; sg[a] = sa; sg[b] = sb; return pt(sg[0], sg[1], sg[2], axisFace); };
      const n = [0, 0, 0]; n[a] = sa; n[b] = sb;
      part.face([P(-1, a), P(1, a), P(1, b), P(-1, b)], n);
    }
  }
  for (const sx of S) for (const sy of S) for (const sz of S) part.face([pt(sx, sy, sz, 0), pt(sx, sy, sz, 1), pt(sx, sy, sz, 2)], [sx, sy, sz]);
}

/**
 * A round solid along an axis ('x' | 'y' | 'z') through centre c: profile [[along, radius], ...] from one end to the
 * other (radius 0 closes an end on the axis; otherwise the ends are capped flat).
 */
export function lathe(part, axis, c, profile, seg = 12) {
  const k = {x: 0, y: 1, z: 2}[axis], [a, b] = [0, 1, 2].filter(q => q !== k);
  const P = (t, r, ang) => { const p = c.slice(); p[k] += t; p[a] += r * Math.cos(ang); p[b] += r * Math.sin(ang); return p; };
  const rings = profile.map(([t, r]) => r < 1e-6 ? [part.vtx(P(t, 0, 0))] : Array.from({length: seg}, (_, i) => part.vtx(P(t, r, 2 * Math.PI * (i + 0.5) / seg))));
  for (let q = 0; q + 1 < profile.length; q++) {
    const A = rings[q], B = rings[q + 1], [t0, r0] = profile[q], [t1, r1] = profile[q + 1];
    for (let i = 0; i < seg; i++) {
      const ang = 2 * Math.PI * (i + 1) / seg;
      const out = [0, 0, 0]; out[a] = Math.cos(ang); out[b] = Math.sin(ang); out[k] = (r0 - r1) * Math.sign(t1 - t0);
      const j = (i + 1) % seg;
      if (A.length === 1) part.face([A[0], B[i], B[j]], out);
      else if (B.length === 1) part.face([A[i], A[j], B[0]], out);
      else part.face([A[i], A[j], B[j], B[i]], out);
    }
  }
  const cap = (R, sgn) => { if (R.length > 1) { const n = [0, 0, 0]; n[k] = sgn; part.face(R.slice(), n); } };
  cap(rings[0], -Math.sign(profile.at(-1)[0] - profile[0][0])); cap(rings.at(-1), Math.sign(profile.at(-1)[0] - profile[0][0]));
}
