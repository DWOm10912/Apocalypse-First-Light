// Shared portal-site geometry for the exterior perspective, elevation and plan (R4 twin bores).
// World: x across the road (right carriageway +), y up, z along the road (portal face at z = 0, tunnel at z > 0).
import { R4, solve, surface, arch } from './geom.mjs';

export const g = solve(R4);
export const XR = 8.8 + g.W / 2;          // bore axis: inner wall at v = 8.8 (lanes stay at 11 .. 18.5)
export const HW = 25.5;                   // headwall half width
export const HTOP = 12.6;                 // headwall top
export const RING = 0.9;                  // portal ring width
export const WALL_LEN = 40;               // approach retaining walls
export const CUT = 2.0;                   // rock cut slope, vertical per horizontal

/** Bore-local x (0 at the inner wall) to world x; side +1 right bore, -1 left bore. */
export const wx = (lx, side) => side * (XR - g.W / 2 + lx);

/** Natural mountain surface. */
export function natural(x, z) {
  const y = 18.2 + 0.40 * z + 0.0025 * x * x + 2.2 * Math.sin(x / 17 + z / 23) * Math.min(1, Math.max(0, (z + 40) / 40));
  return Math.min(y, 54 + 6 * Math.sin(x / 41));
}
/** Retaining wall top at z (z <= 0). */
export function wallTop(z) { return Math.max(0.5, Math.min(HTOP, natural(HW, z) - 2.2)); }
/** Finished ground: natural, or the rock cut where it is lower. Returns [y, isCut]. */
export function ground(x, z) {
  const n = natural(x, z);
  let c = Infinity;
  if (z >= 0 && Math.abs(x) <= HW + 1) c = HTOP + CUT * z;              // face above the portal
  else if (z >= 0) c = HTOP + CUT * Math.max(z, Math.abs(x) - HW);
  else if (Math.abs(x) >= HW) c = (z > -WALL_LEN ? wallTop(z) : 0.3) + CUT * (Math.abs(x) - HW);
  const y = Math.min(n, c);
  return [Math.max(0.3, y), c < n];
}

/** Opening outline of a bore in world coords at the portal face (road edge to road edge). */
export function opening(side) {
  const segs = surface(g);
  const pts = [[wx(segs[0][0], side), segs[0][1]], ...segs.map(s => [wx(s[2], side), s[3]])];
  return side > 0 ? pts : pts.reverse();
}
/** Portal ring outer outline (offset arch + walls to the walkway level). */
export function ringOuter(side) {
  const a = arch(g, RING, 3).map(p => [wx(p[0], side), p[1]]);
  const lo = [wx(-RING, side), 0], hi = [wx(g.W + RING, side), 0];
  const out = [lo, [lo[0], g.hc], ...a, [hi[0], g.hc], hi];
  return side > 0 ? out : out.reverse();
}
export { solve, surface, arch, R4 };
