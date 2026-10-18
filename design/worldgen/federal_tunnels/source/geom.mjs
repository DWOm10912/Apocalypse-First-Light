// Tunnel section geometry shared by the section sheets and the perspectives. Units: metres; x across the bore from the
// inner (left) wall face, y up from the road surface at the barrier toe.

export const R4 = {
  id: 'T-R4', name: '州际 R4 单向孔（每幅一孔，双孔）',
  walkL: 1.20, walkR: 0.75, shL: 1.00, shR: 3.00, lanes: [3.75, 3.75],
  walkH: 0.80, clear: 5.10, rs: 5.0, phi: 35, fans: true,
};
export const R2 = {
  id: 'T-R2', name: '州道 R2 双向孔（单孔）',
  walkL: 0.90, walkR: 0.90, shL: 1.20, shR: 1.20, lanes: [3.65, 3.65],
  walkH: 0.80, clear: 5.10, rs: 4.5, phi: 35, fans: true,
};

export const TOE = 0.25;      // F-shape barrier: toe sits this far out from the walkway edge
export const LINING = 0.45;   // cast-in-place final lining
export const SHOTCRETE = 0.30; // initial support
export const PANEL_TOP = 4.5; // light wall panels up to here

const rad = d => d * Math.PI / 180;

export function solve(s) {
  const W = s.walkL + s.shL + s.lanes.reduce((a, b) => a + b, 0) + s.shR + s.walkR;
  const xa = W / 2, d = W / 2 - s.rs, phi = rad(s.phi);
  const k = d / Math.tan(phi), rc = s.rs + Math.hypot(d, k);
  const curbL = s.walkL, curbR = W - s.walkR;
  // springline: envelope top corners stay 0.2 m inside the side arcs; walkway headroom 2.2 m
  let hc = 0;
  for (const x of [curbL, curbR]) {
    const dx = Math.abs(x - xa) - d, r = s.rs - 0.2;
    hc = Math.max(hc, s.clear - Math.sqrt(Math.max(0, r * r - dx * dx)));
  }
  hc = Math.max(hc, s.walkH + 2.2);
  const crown = hc - k + rc;
  const laneL = curbL + s.shL, laneR = curbR - s.shR;
  return { ...s, W, xa, d, k, rc, hc, crown, curbL, curbR, laneL, laneR };
}

/** The inner arch from the left wall at the springline over the crown to the right wall, as points; step in degrees. */
export function arch(g, off = 0, step = 3) {
  const pts = [], rs = g.rs + off, rc = g.rc + off;
  const cl = [g.xa - g.d, g.hc], cr = [g.xa + g.d, g.hc], c0 = [g.xa, g.hc - g.k];
  const a1 = 90 + g.phi, a2 = 90 - g.phi;
  for (let a = 180; a > a1; a -= step) pts.push([cl[0] + rs * Math.cos(rad(a)), cl[1] + rs * Math.sin(rad(a))]);
  for (let a = a1; a > a2; a -= step) pts.push([c0[0] + rc * Math.cos(rad(a)), c0[1] + rc * Math.sin(rad(a))]);
  for (let a = a2; a >= -0.001; a -= step) pts.push([cr[0] + rs * Math.cos(rad(a)), cr[1] + rs * Math.sin(rad(a))]);
  pts.push([cr[0] + rs, cr[1]]);
  return pts;
}

/** Height of the inner arch surface at x (for hanging fans, lights). */
export function archY(g, x) {
  const dx = x - g.xa;
  if (Math.abs(dx) <= g.rc * Math.sin(rad(g.phi))) return g.hc - g.k + Math.sqrt(g.rc * g.rc - dx * dx);
  const cx = dx < 0 ? g.xa - g.d : g.xa + g.d, ex = x - cx;
  return g.hc + Math.sqrt(Math.max(0, g.rs * g.rs - ex * ex));
}

/**
 * The inner surface as material segments from the left barrier toe round to the right toe (the road closes it).
 * Each entry: [x0, y0, x1, y1, material].
 */
export function surface(g) {
  const segs = [], push = (p, q, m) => segs.push([p[0], p[1], q[0], q[1], m]);
  const L = g.walkL, R = g.W - g.walkR, h = g.walkH;
  push([L + TOE, 0], [L + 0.08, 0.25], 'barrier');
  push([L + 0.08, 0.25], [L, h], 'barrier');
  push([L, h], [0, h], 'walk');
  // wall + arch, split at the panel top
  const poly = [[0, h], [0, g.hc], ...arch(g).slice(1), [g.W, h]];
  for (let i = 0; i + 1 < poly.length; i++) {
    const a = poly[i], b = poly[i + 1];
    const ym = (a[1] + b[1]) / 2;
    if ((a[1] - PANEL_TOP) * (b[1] - PANEL_TOP) < 0) {
      const t = (PANEL_TOP - a[1]) / (b[1] - a[1]), m = [a[0] + t * (b[0] - a[0]), PANEL_TOP];
      push(a, m, a[1] < PANEL_TOP ? 'panel' : 'arch');
      push(m, b, b[1] < PANEL_TOP ? 'panel' : 'arch');
    } else push(a, b, ym < PANEL_TOP ? 'panel' : 'arch');
  }
  push([g.W, h], [R, h], 'walk');
  push([R, h], [R - 0.08, 0.25], 'barrier');
  push([R - 0.08, 0.25], [R - TOE, 0], 'barrier');
  return segs;
}
