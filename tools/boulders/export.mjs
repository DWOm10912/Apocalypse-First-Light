// Rock R1a (docs/gameplay/rock_r1_stones_boulders_design.md 3.2, 11.2): the boulders as the game reads them, one gzip
// binary for both sides (assets/apocalypse_firstlight/boulders/granite_r1.bin, read by boulder/BoulderData).
//
// Every form is centred on its base (x, z of its bounds' middle at 0) and sunk by its bury share of the height, so form
// y = 0 is the ground; its origin sits at the bottom centre of a block (form cell (0, 0, 0) spans x, z -0.5..0.5, y 0..1).
// The stages of a form (budget order, boulder-shapes.mjs) are its nodes: a part, then each first-level piece and its
// second-level pieces. Per node:
//   - cells: every block cell its rock fills, with 4 x 4 columns each (the vertical span of the rock through the
//     column's centre, in 1/16 of the cell); the collision boxes, and where the rock needs a cell block;
//   - anchor: the cell of its root block, at y >= 0, never the anchor of a node that can exist beside it;
//   - heights: on the earthwork's vertex grid (0.25 m), the rock's top and bottom on each vertical (1/256 block), what an
//     edit of the ground there may not cut into;
//   - quads: its triangles split on the 2 m sprite tiles (the atlas does not wrap), each a degenerate quad drawn by the
//     nearest cell of its render set (its cells at y >= 0 and its anchor); box-projected on the triangle's dominant
//     axis, smooth normals across creases under 40 degrees; sprite 0 granite, 1 granite_lichen, 2 granite_fresh (a
//     fresh break, the cap of a cut).
import zlib from 'node:zlib';
import {V, hash, volume, centroid, bounds, strikes, budget} from './boulder-shapes.mjs';

export const SPRITES = ['granite', 'granite_lichen', 'granite_fresh'];
const CREASE = Math.cos(40 * Math.PI / 180), NONE = -32768;

/** The vertical span [lo, hi] of mesh m through (x, z), or null. */
function spanner(m) {
  const tris = m.T.map(([a, b, c]) => {
    const A = m.P[a], B = m.P[b], C = m.P[c];
    return {A, B, C, x0: Math.min(A[0], B[0], C[0]), x1: Math.max(A[0], B[0], C[0]), z0: Math.min(A[2], B[2], C[2]), z1: Math.max(A[2], B[2], C[2])};
  });
  return (x, z) => {
    let lo = Infinity, hi = -Infinity;
    for (const {A, B, C, x0, x1, z0, z1} of tris) {
      if (x < x0 || x > x1 || z < z0 || z > z1) continue;
      const d = (B[2] - C[2]) * (A[0] - C[0]) + (C[0] - B[0]) * (A[2] - C[2]);
      if (Math.abs(d) < 1e-12) continue;
      const l1 = ((B[2] - C[2]) * (x - C[0]) + (C[0] - B[0]) * (z - C[2])) / d;
      const l2 = ((C[2] - A[2]) * (x - C[0]) + (A[0] - C[0]) * (z - C[2])) / d, l3 = 1 - l1 - l2;
      if (l1 < -1e-9 || l2 < -1e-9 || l3 < -1e-9) continue;
      const y = l1 * A[1] + l2 * B[1] + l3 * C[1];
      lo = Math.min(lo, y);
      hi = Math.max(hi, y);
    }
    return lo < hi ? [lo, hi] : null;
  };
}

const key = (x, y, z) => x + ',' + y + ',' + z;

/** The cells of mesh m with their 4 x 4 column spans (cols: lo, hi per column, z-major, 1/16 of the cell). */
function cellsOf(m) {
  const span = spanner(m), b = bounds(m), out = new Map();
  for (let cx = Math.floor(b[0][0] + 0.5); cx <= Math.floor(b[0][1] + 0.5); cx++)
    for (let cz = Math.floor(b[2][0] + 0.5); cz <= Math.floor(b[2][1] + 0.5); cz++)
      for (let c = 0; c < 4; c++) for (let a = 0; a < 4; a++) {
        const s = span(cx - 0.5 + (a + 0.5) / 4, cz - 0.5 + (c + 0.5) / 4);
        if (!s) continue;
        for (let cy = Math.floor(s[0]); cy <= Math.floor(s[1] - 1e-9); cy++) {
          const lo = Math.max(0, Math.min(16, Math.round((s[0] - cy) * 16))), hi = Math.max(0, Math.min(16, Math.round((s[1] - cy) * 16)));
          if (hi <= lo) continue;
          const k = key(cx, cy, cz);
          if (!out.has(k)) out.set(k, {x: cx, y: cy, z: cz, cols: new Uint8Array(32)});
          const cols = out.get(k).cols, i = (c * 4 + a) * 2;
          cols[i] = lo;
          cols[i + 1] = hi;
        }
      }
  return out;
}

/** The rock's top and bottom over the earthwork vertices around it (form vertex i: x = (i - 2) / 4). */
function heightsOf(m) {
  const span = spanner(m), b = bounds(m);
  const i0 = Math.floor(4 * b[0][0] + 2) - 1, i1 = Math.ceil(4 * b[0][1] + 2) + 1, j0 = Math.floor(4 * b[2][0] + 2) - 1, j1 = Math.ceil(4 * b[2][1] + 2) + 1;
  const w = i1 - i0 + 1, d = j1 - j0 + 1, h = new Int16Array(w * d * 2);
  for (let j = 0; j < d; j++) for (let i = 0; i < w; i++) {
    const s = span((i0 + i - 2) / 4, (j0 + j - 2) / 4);
    h[(j * w + i) * 2] = s ? Math.round(s[1] * 256) : NONE;
    h[(j * w + i) * 2 + 1] = s ? Math.round(s[0] * 256) : NONE;
  }
  return {i0, j0, w, d, h};
}

/** Smooth corner normals: each corner the area-weighted mean of the faces at its vertex within 40 degrees of its face. */
function cornerNormals(m) {
  const fn = m.T.map(([a, b, c]) => V.cross(V.sub(m.P[b], m.P[a]), V.sub(m.P[c], m.P[a])));
  const unit = fn.map(V.norm), at = m.P.map(() => []);
  m.T.forEach((t, ti) => t.forEach(v => at[v].push(ti)));
  return m.T.map((t, ti) => t.map(v => {
    let s = [0, 0, 0];
    for (const f of at[v]) if (V.dot(unit[f], unit[ti]) >= CREASE) s = V.add(s, fn[f]);
    return V.norm(s);
  }));
}

/** A convex polygon of {p, n, uv} cut at uv[k] = line: [below, above]. */
function cut(poly, k, line) {
  const below = [], above = [];
  const lerp = (a, b, t) => ({p: a.p.map((v, i) => v + (b.p[i] - v) * t), n: V.norm(a.n.map((v, i) => v + (b.n[i] - v) * t)), uv: a.uv.map((v, i) => v + (b.uv[i] - v) * t)});
  for (let i = 0; i < poly.length; i++) {
    const a = poly[i], b = poly[(i + 1) % poly.length], da = a.uv[k] - line, db = b.uv[k] - line;
    if (da <= 0) below.push(a);
    if (da >= 0) above.push(a);
    if ((da < 0 && db > 0) || (da > 0 && db < 0)) { const m = lerp(a, b, da / (da - db)); below.push(m); above.push(m); }
  }
  return [below, above];
}

/** A polygon split on every whole uv line it crosses, both directions. */
function tiles(poly) {
  let polys = [poly];
  for (const k of [0, 1]) {
    const out = [];
    for (let rest of polys) {
      const lo = Math.min(...rest.map(q => q.uv[k])), hi = Math.max(...rest.map(q => q.uv[k]));
      for (let line = Math.floor(lo) + 1; line < hi; line++) {
        const [below, above] = cut(rest, k, line);
        if (below.length >= 3) out.push(below);
        rest = above;
      }
      if (rest.length >= 3) out.push(rest);
    }
    polys = out;
  }
  return polys;
}

/** A node's quads: [{cell: [x, y, z], sprite, v: [{p, n, uv}] x 4}], drawn by the nearest cell of its render set. */
function quadsOf(m, render, lichen, off, tile) {
  const normals = cornerNormals(m), R = [...render.values()], out = [];
  const owner = c => {
    const k = key(Math.floor(c[0] + 0.5), Math.floor(c[1]), Math.floor(c[2] + 0.5));
    if (render.has(k)) return render.get(k);
    let best = null, bd = Infinity;
    for (const r of R) { const d = (r.x - c[0]) ** 2 + (r.y + 0.5 - c[1]) ** 2 + (r.z - c[2]) ** 2; if (d < bd) { bd = d; best = r; } }
    return best;
  };
  m.T.forEach((t, ti) => {
    const P = t.map(i => m.P[i]), f = V.cross(V.sub(P[1], P[0]), V.sub(P[2], P[0])), ax = f.map(Math.abs);
    const axis = ax[0] >= ax[1] && ax[0] >= ax[2] ? 0 : ax[1] >= ax[2] ? 1 : 2;
    const proj = p => (axis === 0 ? [p[2], -p[1]] : axis === 1 ? [p[0], p[2]] : [p[0], -p[1]]).map((v, i) => (v + off[i]) / tile);
    const sprite = m.F && m.F[ti] === 1 ? 2 : lichen ? 1 : 0;
    for (const poly of tiles(P.map((p, i) => ({p, n: normals[ti][i], uv: proj(p)})))) {
      for (let i = 1; i + 1 < poly.length; i++) {
        const tri = [poly[0], poly[i], poly[i + 1]];
        const c = V.mul(tri.reduce((s, q) => V.add(s, q.p), [0, 0, 0]), 1 / 3);
        const ku = Math.floor(tri.reduce((s, q) => s + q.uv[0], 0) / 3), kv = Math.floor(tri.reduce((s, q) => s + q.uv[1], 0) / 3);
        const v = tri.map(q => ({p: q.p, n: q.n, uv: [Math.max(0, Math.min(1, q.uv[0] - ku)), Math.max(0, Math.min(1, q.uv[1] - kv))]}));
        const o = owner(c);
        out.push({cell: [o.x, o.y, o.z], sprite, v: [v[0], v[1], v[2], v[2]]});
      }
    }
  });
  return out;
}

/** Every form's nodes, ready to write: {forms: [{id, lichen, top}], nodes: [...]}. all: build() of build-boulders.mjs. */
export function exportForms(all, tile) {
  const forms = [], nodes = [];
  all.forEach(({f, parts, l1}, fi) => {
    // centred on the base, sunk by its bury share of the height
    const pts = parts.flatMap(p => p.P), b = bounds({P: pts}), sink = (f.bury || 0.25) * b[1][1];
    const shift = m => ({P: m.P.map(p => [p[0] - (b[0][0] + b[0][1]) / 2, p[1] - sink, p[2] - (b[2][0] + b[2][1]) / 2]), T: m.T, F: m.F});
    const first = nodes.length, top = [];
    const push = (m, parent) => {
      const n = {form: fi, parent, children: [], m: shift(m), vol: volume(m)};
      nodes.push(n);
      if (parent >= 0) nodes[parent].children.push(nodes.length - 1);
      return nodes.length - 1;
    };
    parts.forEach((p, j) => {
      const id = push(p, -1);
      top.push(id);
      if (volume(p) <= 0.25) return;
      for (const [q, l2] of l1[j]) {
        const qi = push(q, id);
        if (l2) for (const r of l2) push(r, qi);
      }
    });
    const mine = nodes.slice(first), plan = budget(mine.map(n => strikes(n.vol)));
    mine.forEach((n, k) => {
      n.strikes = plan[k];
      n.stones = n.children.length ? 0 : Math.max(1, Math.min(6, Math.round(n.vol * 20)));
    });
    // anchors: parents first; never one a node beside it (no ancestor, no descendant) already has
    const ancestor = (a, d) => { for (let x = nodes[d].parent; x >= 0; x = nodes[x].parent) if (x === a) return true; return false; };
    const off = [hash(fi, 1, 41) * tile, hash(fi, 2, 41) * tile];
    for (let id = first; id < nodes.length; id++) {
      const n = nodes[id];
      n.cells = cellsOf(n.m);
      n.heights = heightsOf(n.m);
      const c = centroid(n.m), taken = new Set();
      for (let o = first; o < id; o++) if (!ancestor(o, id)) taken.add(key(...nodes[o].anchor));
      const cand = [...n.cells.values()].filter(q => q.y >= 0)
        .sort((p, q) => Math.hypot(p.x - c[0], p.z - c[2]) - Math.hypot(q.x - c[0], q.z - c[2]) || p.y - q.y);
      let a = cand.find(q => !taken.has(key(q.x, q.y, q.z)));
      for (let r = 0; !a && r < 4; r++)   // a piece wholly underground: a free cell at ground level over it
        for (let dz = -r; dz <= r && !a; dz++) for (let dx = -r; dx <= r && !a; dx++) {
          const x = Math.round(c[0]) + dx, z = Math.round(c[2]) + dz;
          if (!taken.has(key(x, 0, z))) a = {x, y: 0, z};
        }
      n.anchor = [a.x, a.y, a.z];
      const render = new Map([...n.cells].filter(([, q]) => q.y >= 0));
      if (!render.has(key(a.x, a.y, a.z))) render.set(key(a.x, a.y, a.z), {x: a.x, y: a.y, z: a.z});
      n.quads = quadsOf(n.m, render, !!f.lichen, off, tile);
    }
    forms.push({id: f.id, lichen: !!f.lichen, top});
  });
  return {forms, nodes};
}

/** The binary (big-endian, Java DataInputStream order), gzipped. */
export function encode({forms, nodes}, tile) {
  const parts = [];
  const u8 = v => { const b = Buffer.alloc(1); b.writeInt8(v); parts.push(b); };
  const i16 = v => { const b = Buffer.alloc(2); b.writeInt16BE(v); parts.push(b); };
  const u16 = v => { const b = Buffer.alloc(2); b.writeUInt16BE(v); parts.push(b); };
  const i32 = v => { const b = Buffer.alloc(4); b.writeInt32BE(v); parts.push(b); };
  const f32 = v => { const b = Buffer.alloc(4); b.writeFloatBE(v); parts.push(b); };
  const utf = s => { const b = Buffer.from(s, 'utf8'); u16(b.length); parts.push(b); };
  const q = v => Math.max(-32767, Math.min(32767, Math.round(v * 2048)));
  i32(0x41464C42); i16(1); f32(tile);
  u8(SPRITES.length); SPRITES.forEach(utf);
  i16(forms.length);
  for (const f of forms) { utf(f.id); u8(f.lichen ? 1 : 0); u8(f.top.length); f.top.forEach(i16); }
  i16(nodes.length);
  for (const n of nodes) {
    u8(n.form); i16(n.parent); u8(n.children.length); n.children.forEach(i16);
    u8(n.strikes); u8(n.stones); f32(n.vol);
    n.anchor.forEach(u8);
    i16(n.cells.size);
    for (const c of n.cells.values()) { u8(c.x); u8(c.y); u8(c.z); parts.push(Buffer.from(c.cols)); }
    const h = n.heights;
    i16(h.i0); i16(h.j0); i16(h.w); i16(h.d);
    for (const v of h.h) i16(v);
    i32(n.quads.length);
    for (const qd of n.quads) {
      qd.cell.forEach(u8); u8(qd.sprite);
      for (const v of qd.v) {
        v.p.forEach(c => i16(q(c)));
        v.n.forEach(c => u8(Math.max(-127, Math.min(127, Math.round(c * 127)))));
        v.uv.forEach(c => u16(Math.round(c * 65535)));
      }
    }
  }
  return zlib.gzipSync(Buffer.concat(parts), {level: 9});
}

/** Offline checks of the export: anchors at y >= 0 and distinct among nodes that coexist, every node has cells or an anchor. */
export function checkExport({forms, nodes}) {
  let bad = 0;
  forms.forEach((f, fi) => {
    const ids = nodes.map((n, i) => i).filter(i => nodes[i].form === fi);
    const ancestor = (a, d) => { for (let x = nodes[d].parent; x >= 0; x = nodes[x].parent) if (x === a) return true; return false; };
    for (const a of ids) {
      if (nodes[a].anchor[1] < 0) { bad++; console.log(`${f.id} node ${a}: anchor below ground`); }
      for (const b of ids) if (a < b && !ancestor(a, b) && nodes[a].anchor.join() === nodes[b].anchor.join()) { bad++; console.log(`${f.id} nodes ${a} ${b}: same anchor`); }
    }
  });
  const quads = nodes.reduce((s, n) => s + n.quads.length, 0);
  const reach = Math.max(...nodes.map(n => Math.max(...[...n.cells.values()].map(c => Math.max(Math.abs(c.x - n.anchor[0]), Math.abs(c.z - n.anchor[2]))), 0)));
  console.log(`export: ${forms.length} forms, ${nodes.length} nodes, ${quads} quads, cells reach ${reach} blocks from their anchor`);
  return bad === 0;
}
