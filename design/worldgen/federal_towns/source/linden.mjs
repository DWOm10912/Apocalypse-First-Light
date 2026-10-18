// Linden (valley_town) framework sample on r1's real terrain and F-40 line: streets, blocks, zoning, lots, the F-40
// diamond at 6th St, the two underpasses. Town coordinates (frame.mjs): s along F-40 eastward from the town node,
// v north of it. Values chosen from probe_linden.mjs (the valley floor is north of F-40 and on the west).
import { loadGrid } from './raster.mjs';
import { loadRoads, frame, along, across, quad } from './frame.mjs';
import { blocksOf } from './layout.mjs';

export function lindenPlan() {
  const g = loadGrid('valley_town'), R = loadRoads();
  const a = R.routes.find(r => r.id === 'interstate_ew:county_seat-valley_town').control;
  const b = R.routes.find(r => r.id === 'interstate_ew:valley_town-river_city').control;
  const f40 = a.concat(b.slice(1));
  const F = frame(f40, R.centre);

  // parallel streets (v) and cross streets (s), named
  const V = { front: 80, main: 174, oak: 268, maple: 360, frontS: -80, elm: -172 };
  const CROSS = [-1050, -900, -750, -600, -450, -300, -150, 0, 150, 300, 450];
  const ORD = ['1st', '2nd', '3rd', '4th', '5th', '6th', '7th', '8th', '9th', '10th', '11th'];
  const streets = [
    { name: 'Front St', cls: 'R12', pts: along(F, V.front, -1050, 450), labelAt: 0.12 },
    { name: 'Main St', cls: 'C14', pts: along(F, V.main, -1160, 520), labelAt: 0.3 },
    { name: 'Oak St', cls: 'R12', pts: along(F, V.oak, -1050, 0), labelAt: 0.6 },
    { name: 'Maple St', cls: 'R12', pts: along(F, V.maple, -750, -300), labelAt: 0.5 },
    { name: 'Front St', cls: 'R12', pts: along(F, V.frontS, -1050, 150), labelAt: 0.15 },
    { name: 'Front St', cls: 'I12', pts: along(F, V.frontS, 150, 450), labelAt: 0.5 },
    { name: 'Elm St', cls: 'R12', pts: along(F, V.elm, -1050, 450), labelAt: 0.2 },
  ];
  // cross streets: [index, v0, v1, class, crosses F-40?]
  const XS = [[0, -172, 441], [1, -172, 268], [2, -172, 441, 'R12', 'under'], [3, 80, 360], [3, -172, -80], [4, 80, 360], [4, -172, -80],
    [5, -172, 360, 'C14', 'over'], [6, 80, 268], [6, -172, -80], [7, 80, 268], [7, -172, -80], [8, 80, 174], [8, -215, -80],
    [9, -215, 174, 'R12', 'under'], [10, 80, 174], [10, -215, -80]];
  for (const [i, v0, v1, cls, kind] of XS)
    streets.push({ name: ORD[i] + ' St', cls: cls || (CROSS[i] >= 150 && v1 <= -80 ? 'I12' : 'R12'), pts: across(F, CROSS[i], v0, v1), kind, s: CROSS[i],
      labelAt: v1 > 300 ? 0.72 : v0 < -100 && v1 < 0 ? 0.5 : 0.55 });

  // bands between streets (block edges = street centre +- half the right of way)
  const bands = {
    N1: { v0: V.front + 11, v1: V.main - 13 }, N2: { v0: V.main + 13, v1: V.oak - 11 }, N3: { v0: V.oak + 11, v1: V.maple - 11 },
    N4: { v0: V.maple + 11, v1: V.maple + 11 + 32 }, S1: { v0: V.elm + 11, v1: V.frontS - 11 }, S2: { v0: V.elm - 11 - 32, v1: V.elm - 11 },
  };
  const Z = {
    N1: ['res|res', 'res|res', 'park_lot|down', 'park_lot|down', 'hwy|', 'hwy|', 'res|res', 'res|res', 'res|res', 'res|res'],
    N2: ['res|res', 'res|res', 'down|res', 'park:Town Square', 'down|civic', 'down|res', 'res|res', 'res|res', 'res|res', 'res|res'],
    N3: [null, null, 'civic|res', 'civic|civic', 'res|res', 'res|res', 'res|res', null, null, null],
    N4: [null, null, '|res', '|res', '|res', null, null, null, null, null],
    S1: ['res|res', 'res|res', 'res|res', 'res|res', '|hwy', '|hwy', 'mobile|mobile', 'res|res', '|ind', '|ind'],
    S2: [null, null, null, null, null, null, null, null, '|ind', '|ind'],
  };
  // S2's single row faces Elm on its high side
  const zoning = (band, i) => {
    const z = Z[band][i];
    if (!z) return null;
    if (z.startsWith('park:')) return { whole: 'park', label: z.slice(5) };
    const [lo, hi] = z.split('|');
    return { lo: lo || null, hi: hi || null };
  };
  const blocks = blocksOf(F, bands, CROSS, zoning, g);
  // the school: two intervals of Oak-to-north merged (no Maple there), with its ball field
  const school = { band: 'N3+N4', s0: CROSS[0] + 11, s1: CROSS[2] - 11, v0: V.oak + 11, v1: V.maple + 11 + 70, zone: 'school', whole: true, label: 'Linden School 学校' };
  school.poly = quad(F, school.s0, school.s1, school.v0, school.v1, 8);
  school.lots = [{ poly: school.poly, zone: 'school', w: school.s1 - school.s0, d: school.v1 - school.v0 }];
  school.relief = (() => { let lo = Infinity, hi = -Infinity; for (let s = school.s0; s <= school.s1; s += 8) for (let v = school.v0; v <= school.v1; v += 8) { const [x, z] = F.W(s, v), h = g.at(x, z); lo = Math.min(lo, h); hi = Math.max(hi, h); } return hi - lo; })();
  blocks.push(school);

  // the F-40 diamond at 6th St (s -300): ramps as smooth curves in (s, v)
  const curve = (p0, p1, p2, p3, n = 40) => { const o = []; for (let k = 0; k <= n; k++) { const t = k / n, u = 1 - t; const s = u * u * u * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t * t * t * p3[0]; const v = u * u * u * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t * t * t * p3[1]; o.push(F.W(s, v)); } return o; };
  const sc = -300;
  const ramps = [
    curve([sc - 330, -19], [sc - 200, -22], [sc - 90, -52], [sc - 8, -56]),
    curve([sc + 8, -56], [sc + 90, -52], [sc + 200, -22], [sc + 330, -19]),
    curve([sc + 330, 19], [sc + 200, 22], [sc + 90, 52], [sc + 8, 56]),
    curve([sc - 8, 56], [sc - 90, 52], [sc - 200, 22], [sc - 330, 19]),
  ];
  return { F, g, f40, streets, blocks, ramps, CROSS, ORD, V, centre: R.centre, sc };
}
