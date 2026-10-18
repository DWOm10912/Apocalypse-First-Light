// Town framework layout (concept): streets, blocks and lots in a town frame (frame.mjs: s along the axis, v to its
// left). A plan is plain data the sheets draw: streets {pts, cls, name}, blocks {poly, zone, s0, s1, v0, v1, relief},
// lots {poly, zone, w, d}. Street classes (north_american_roads_and_lots_spec_v1.md 2, + the city arterial):
// asphalt / right of way, m.
import { quad } from './frame.mjs';

export const SECTIONS = {
  R12: { asphalt: 12, row: 22, label: 'R12 住宅街' },
  C14: { asphalt: 14, row: 26, label: 'C14 商业街 / 主街' },
  I12: { asphalt: 12, row: 24, label: 'I12 工业街' },
  A20: { asphalt: 20, row: 32, label: 'A20 城市主干道' },
};

export const ZONES = {
  down: { fill: '#c98a5e', label: '市中心（主街店面）' },
  hwy: { fill: '#e0b04f', label: '公路商业（加油站、汽车旅馆、餐馆）' },
  res: { fill: '#efe3c4', label: '住宅' },
  mobile: { fill: '#e6cfae', label: '拖车房营地' },
  civic: { fill: '#86a6c8', label: '公共（市政厅、警察、消防、教堂、邮局）' },
  school: { fill: '#a7bfd9', label: '学校（含操场）' },
  park: { fill: '#a9cf92', label: '公园 / 广场' },
  ind: { fill: '#aca3b8', label: '工业 / 仓储' },
  park_lot: { fill: '#d8d6cf', label: '停车场' },
};

/** Lot rows: widths along the frontage (repeated to fill), depth. */
const ROWS = {
  res: { widths: [24], depth: 32 },
  down: { widths: [12, 12, 16, 12, 24, 12, 16], depth: 32 },
  hwy: { widths: [64, 48, 40], depth: 64 },
  ind: { widths: [80, 64], depth: 64 },
  mobile: { widths: [16], depth: 28 },
  civic: { widths: [48, 40], depth: 48 },
  park_lot: { widths: [999], depth: 32 },
};

/**
 * Lots of one row along a block side: frontage from s0 to s1 at the street edge vEdge, depth d inward (dir +1: toward
 * +v), widths fitted so the row ends flush (each width stretched by the same factor).
 */
function row(F, zone, s0, s1, vEdge, dir, spec) {
  const len = s1 - s0, base = [];
  let sum = 0;
  for (let i = 0; sum + spec.widths[i % spec.widths.length] <= len + 1e-6 || base.length === 0; i++) {
    const w = Math.min(spec.widths[i % spec.widths.length], len);
    base.push(w); sum += w;
    if (sum >= len) break;
  }
  const k = len / sum, out = [];
  let s = s0;
  for (const w of base) {
    const v0 = vEdge, v1 = vEdge + dir * spec.depth;
    out.push({ poly: quad(F, s, s + w * k, Math.min(v0, v1), Math.max(v0, v1), 4), zone, w: w * k, d: spec.depth });
    s += w * k;
  }
  return out;
}

/**
 * The lots of a block: rows facing its two long sides (each side's zone: front for the low-v side, back for the
 * high-v side; a 'whole' zone fills the block as one lot).
 */
export function lots(F, b) {
  if (b.whole) return [{ poly: b.poly, zone: b.zone, w: b.s1 - b.s0, d: b.v1 - b.v0 }];
  const out = [], depth = b.v1 - b.v0;
  // a block with a street on one long side only: one row facing it
  if (!b.hi) return row(F, b.lo, b.s0, b.s1, b.v0, 1, { widths: ROWS[b.lo].widths, depth: Math.min(ROWS[b.lo].depth, depth) });
  if (!b.lo) return row(F, b.hi, b.s0, b.s1, b.v1, -1, { widths: ROWS[b.hi].widths, depth: Math.min(ROWS[b.hi].depth, depth) });
  const lo = ROWS[b.lo], hi = ROWS[b.hi];
  if (lo.depth + hi.depth <= depth + 0.5) {
    out.push(...row(F, b.lo, b.s0, b.s1, b.v0, 1, lo));
    out.push(...row(F, b.hi, b.s0, b.s1, b.v1, -1, hi));
  } else {
    // one deep row (the deeper of the two zones faces its street)
    const z = lo.depth >= hi.depth ? b.lo : b.hi, spec = ROWS[z];
    out.push(...row(F, z, b.s0, b.s1, z === b.lo ? b.v0 : b.v1, z === b.lo ? 1 : -1, { widths: spec.widths, depth: Math.min(spec.depth, depth) }));
  }
  return out;
}

/**
 * A plan from bands (named v gaps between two street lines, {v0, v1}) and cross streets (s list) and a zoning
 * table: zoning(band, i) -> null (no block) or {lo, hi} zones of the block's two long sides, or {whole: zone}; merges:
 * [{band, i0, i1, ...zoning}] blocks spanning several intervals (their cross streets stop at the block).
 */
export function blocksOf(F, bands, cross, zoning, ground) {
  const out = [];
  for (const [name, band] of Object.entries(bands))
    for (let i = 0; i < cross.length - 1; i++) {
      const z = zoning(name, i);
      if (!z || z.skip) continue;
      const span = z.span || 1;
      const s0 = cross[i] + 11, s1 = cross[i + span] - 11, v0 = band.v0, v1 = band.v1;
      const b = { band: name, i, s0, s1, v0, v1, poly: quad(F, s0, s1, v0, v1, 8), zone: z.whole || z.lo || z.hi, lo: z.lo, hi: z.hi, whole: !!z.whole, label: z.label };
      if (ground) {
        let lo = Infinity, hi = -Infinity;
        for (let s = s0; s <= s1; s += 8) for (let v = v0; v <= v1; v += 8) { const [x, zz] = F.W(s, v), h = ground.at(x, zz); lo = Math.min(lo, h); hi = Math.max(hi, h); }
        b.relief = hi - lo;
      }
      b.lots = lots(F, b);
      out.push(b);
      i += span - 1;
    }
  return out;
}
