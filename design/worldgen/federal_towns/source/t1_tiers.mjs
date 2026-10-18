// T1: the three town tiers side by side at one scale (schematic plans on flat ground, built by the same layout rules
// as the Linden sample), and the sixteen fixed towns by tier, role and terrain style.
import { sheet, text, lines, f } from './svg.mjs';
import { blocksOf, ZONES } from './layout.mjs';
import { along, across, quad } from './frame.mjs';
import { drawBlocks, drawStreets, TERRACE_PATTERN } from './draw.mjs';

// a straight town frame on flat ground: s east, v north; world z = -v
const FLAT = { W: (s, v) => [s, -v], at: s => ({ x: s, z: 0, tx: 1, tz: 0 }) };
const split = z => { if (!z) return null; if (z.startsWith('park:')) return { whole: 'park', label: z.slice(5) }; if (z.startsWith('whole:')) { const [, zone, label] = z.split(':'); return { whole: zone, label }; } const [lo, hi] = z.split('|'); return { lo: lo || null, hi: hi || null }; };

function plan(spec) {
  const F = FLAT, streets = [];
  for (const [v, s0, s1, cls] of spec.par) streets.push({ cls, pts: along(F, v, s0, s1) });
  for (const [s, v0, v1, cls] of spec.cross) streets.push({ cls, pts: across(F, s, v0, v1) });
  const blocks = blocksOf(F, spec.bands, spec.cuts, (b, i) => split(spec.zones[b]?.[i]), null);
  return { F, streets, blocks, hw: spec.hw || [], ramps: spec.ramps || [] };
}

// the village: a state route as Main St, three cross streets, a back street each side
const VILLAGE = plan({
  par: [[0, -330, 330, 'C14'], [94, -161, 161, 'R12'], [-94, -161, 161, 'R12']],
  cross: [[-150, -94, 94, 'R12'], [0, -94, 94, 'R12'], [150, -94, 94, 'R12']],
  cuts: [-300, -150, 0, 150, 300],
  bands: { N1: { v0: 13, v1: 83 }, S1: { v0: -83, v1: -13 }, N2: { v0: 105, v1: 137 }, S2: { v0: -137, v1: -105 } },
  zones: {
    N1: [null, 'down|res', 'civic|res', null], S1: [null, 'res|down', 'res|down', null],
    N2: [null, '|res', '|res', null].map(z => z && z.replace('|res', 'res|')), S2: [null, '|res', '|res', null],
  },
});
// the village's edge lots on Main St (a gas station east, houses west), drawn as extra blocks
for (const [s0, s1, v0, v1, z] of [[161, 225, 13, 77, 'hwy'], [-289, -161, 13, 45, 'res'], [-289, -161, -45, -13, 'res'], [161, 289, -45, -13, 'res']]) {
  const b = { s0, s1, v0, v1, poly: quad(FLAT, s0, s1, v0, v1, 8), zone: z, lo: v0 > 0 ? z : null, hi: v0 > 0 ? null : z };
  b.lots = (await import('./layout.mjs')).lots(FLAT, b);
  VILLAGE.blocks.push(b);
}

// the town: Linden's rules on flat ground, an interstate along the axis, a diamond at the centre street
const T_CUTS = [-750, -600, -450, -300, -150, 0, 150, 300, 450, 600, 750];
const TOWN = plan({
  par: [[80, -750, 750, 'R12'], [174, -800, 800, 'C14'], [268, -600, 600, 'R12'], [-80, -750, 750, 'R12'], [-172, -750, 750, 'R12']],
  cross: [...T_CUTS.map(s => [s, 80, s >= -600 && s <= 600 ? 268 : 174, s === 0 ? 'C14' : 'R12']), ...T_CUTS.map(s => [s, -172, -80, s >= 300 ? 'I12' : 'R12']), [0, -80, 80, 'C14']],
  cuts: T_CUTS,
  bands: { N1: { v0: 91, v1: 161 }, N2: { v0: 187, v1: 257 }, N3: { v0: 279, v1: 311 }, S1: { v0: -161, v1: -91 } },
  zones: {
    N1: ['res|res', 'res|res', 'park_lot|down', 'hwy|', 'hwy|', 'park_lot|down', 'res|res', 'res|res', 'res|res', 'res|res'],
    N2: ['res|res', 'res|res', 'down|res', 'park:Square', 'down|civic', 'down|res', 'res|res', 'res|res', 'res|res', 'res|res'],
    N3: [null, 'res|', 'res|', 'civic|', 'res|', 'res|', 'res|', 'res|', 'res|', null],
    S1: ['mobile|mobile', 'res|res', 'res|res', 'res|res', '|hwy', '|hwy', 'whole:school:School', 'res|res', '|ind', '|ind'],
  },
  hw: [[[-900, 0], [900, 0]]],
});
TOWN.diamonds = [[0, 0]];

// the city: a downtown grid on A20 avenues, neighbourhoods round it, industry by the interstate, two diamonds
const C_CUTS = [-910, -780, -650, -520, -390, -260, -130, 0, 130, 260, 390, 520, 650, 780, 910];
const cityBands = {}, cityZones = {};
const VS = [-282, -188, -94, 0, 94, 188, 282, 376, 470, 564];   // street lines; blocks between consecutive lines
for (let k = 0; k < VS.length - 1; k++) {
  const name = 'B' + k;
  cityBands[name] = { v0: VS[k] + 11, v1: VS[k + 1] - 11 };
  cityZones[name] = C_CUTS.slice(0, -1).map((s, i) => {
    const sc = (s + C_CUTS[i + 1]) / 2, vc = (VS[k] + VS[k + 1]) / 2;
    if (vc < -150 && sc > 200) return '|ind';
    if (vc < -150 && sc < -500) return 'mobile|res';
    if (vc < -150 && Math.abs(sc + 390) < 140) return '|hwy';
    if (vc < -150 && Math.abs(sc - 130) < 70) return '|hwy';
    if (Math.abs(sc) < 200 && vc > 0 && vc < 200) return sc < 0 && sc > -130 && vc < 100 ? 'park:City Hall' : 'down|down';
    if (Math.abs(sc) < 330 && vc > -150 && vc < 330) return 'down|down';
    if (Math.abs(sc - 455) < 70 && Math.abs(vc - 141) < 50) return 'whole:civic:Hospital';
    if (Math.abs(sc + 585) < 70 && Math.abs(vc - 329) < 50) return 'whole:school:School';
    if (Math.abs(sc - 715) < 70 && Math.abs(vc - 423) < 50) return 'whole:school:School';
    if (Math.abs(sc + 715) < 70 && Math.abs(vc - 47) < 50) return 'park:Park';
    if (Math.abs(sc - 455) < 70 && Math.abs(vc - 329) < 50) return 'civic|civic';
    return 'res|res';
  });
}
const CITY = plan({
  par: VS.map(v => [v, -910, 910, v === 0 || v === 282 ? 'A20' : v < -150 ? 'I12' : 'C14'].map((x, i) => i === 3 && Math.abs(v) > 300 && v > 0 ? 'R12' : x)),
  cross: C_CUTS.map(s => [s, -282, 564, Math.abs(s) === 390 || s === 0 ? 'A20' : Math.abs(s) < 330 ? 'C14' : 'R12']),
  cuts: C_CUTS,
  bands: cityBands,
  zones: cityZones,
  hw: [[[-1050, -380], [1050, -380]]],
});
// the city's two A20 avenues go on down to the interstate's diamonds
for (const s0 of [-390, 390]) CITY.streets.push({ cls: 'A20', pts: across(FLAT, s0, -440, -282) });
CITY.diamonds = [[-390, -380], [390, -380]];

function drawPlan(p, ox, oy, k, title, sub) {
  const P = (x, z) => [ox + x * k, oy + z * k];
  let s = drawBlocks(p.blocks, P, { lotStroke: 0.3, blockStroke: 0.6, labelSize: 9 });
  s += drawStreets(p.streets, P, k);
  for (const h of p.hw) {
    const a = P(...FLAT.W(...h[0])), b = P(...FLAT.W(...h[1]));
    s += `<line x1="${f(a[0])}" y1="${f(a[1])}" x2="${f(b[0])}" y2="${f(b[1])}" stroke="#3b4043" stroke-width="${f(32 * k)}"/>`;
  }
  s += text(ox, oy - 0, '', '');
  return s;
}

export function tiersSheet() {
  const k = 0.36;
  let b = `<defs>${TERRACE_PATTERN}</defs>`;
  // three plans in a row, centred in their boxes
  const boxes = [
    { p: VILLAGE, x: 40, w: 290, name: '村 VILLAGE', sub: '约 0.6 × 0.3 km' },
    { p: TOWN, x: 345, w: 610, name: '镇 TOWN', sub: '约 1.6 × 0.6 km' },
    { p: CITY, x: 970, w: 790, name: '城市 CITY', sub: '约 1.8 × 1.0 km 的网格' },
  ];
  const top = 96, H = 440;
  for (const bx of boxes) {
    b += `<rect x="${bx.x}" y="${top}" width="${bx.w}" height="${H}" fill="#e6eadc" stroke="#23282b" stroke-width="1.2"/>`;
    // centre the plan's extent (blocks, streets, interstate) in the box below its title, clipped to the box
    let lo = [Infinity, Infinity], hi = [-Infinity, -Infinity];
    const grow = q => { lo = [Math.min(lo[0], q[0]), Math.min(lo[1], q[1])]; hi = [Math.max(hi[0], q[0]), Math.max(hi[1], q[1])]; };
    for (const bl of bx.p.blocks) bl.poly.forEach(grow);
    for (const st of bx.p.streets) st.pts.forEach(grow);
    const cx = bx.x + bx.w / 2 - (lo[0] + hi[0]) / 2 * k, cy = top + 60 + (H - 60) / 2 - (lo[1] + hi[1]) / 2 * k;
    b += `<clipPath id="c${bx.x}"><rect x="${bx.x}" y="${top}" width="${bx.w}" height="${H}"/></clipPath><g clip-path="url(#c${bx.x})">`;
    b += drawPlan(bx.p, cx, cy, k);
    // diamonds where a street meets the interstate (schematic)
    for (const d of bx.p.diamonds || []) {
      const P = (x, z) => [cx + x * k, cy + z * k];
      for (const [sx, sv] of [[-1, 1], [1, 1], [-1, -1], [1, -1]]) {
        const a = P(d[0] + sx * 150, -(d[1] + sv * 14)), c = P(d[0] + sx * 12, -(d[1] + sv * 46));
        b += `<path d="M${f(a[0])},${f(a[1])}Q${f(P(d[0] + sx * 60, -(d[1] + sv * 40))[0])},${f(P(d[0] + sx * 60, -(d[1] + sv * 40))[1])} ${f(c[0])},${f(c[1])}" fill="none" stroke="#4d5255" stroke-width="${f(7 * k + 0.8)}"/>`;
      }
    }
    b += '</g>';
    let n = 0;
    for (const bl of bx.p.blocks) n += bl.lots.length;
    b += text(bx.x + 12, top + 26, bx.name, 'font-size="18" font-weight="700" fill="#23282b"');
    b += text(bx.x + 12, top + 48, bx.sub + `，这张图 ${n} 块地`, 'font-size="13" fill="#4a5054"');
  }
  // scale bar
  b += `<line x1="60" y1="${top + H - 22}" x2="${60 + 500 * k}" y2="${top + H - 22}" stroke="#23282b" stroke-width="3"/>` + text(60 + 250 * k, top + H - 30, '500 m（三张同一比例）', 'font-size="12" text-anchor="middle"');
  // the tier table
  const T = top + H + 40;
  const col = [40, 170, 650, 950, 1200, 1450];
  const head = ['档', '城镇（名字固定，每个世界都一样）', '框架', '街道', '地块（估）', '和高速'];
  b += `<rect x="30" y="${T - 22}" width="1740" height="30" fill="#e3e6df"/>`;
  head.forEach((h, i) => { b += text(col[i], T - 2, h, 'font-size="14" font-weight="700" fill="#23282b"'); });
  const rows = [
    ['城市 CITY', ['Bellhaven 贝尔黑文（河城，S1）', 'Kessler 凯斯勒（工业城，S4）'], ['市中心网格 + 住宅区', '+ 工业区 / 货场；约 2 × 1–1.5 km'], ['A20 主干道（4 车道）', 'C14 市中心街、R12、I12'], ['900–1200', '市中心店面 / 办公楼 300+'], ['州际 2–3 处菱形立交', '主干道接立交']],
    ['镇 TOWN', ['Linden 林登（S1）、Sutton 萨顿（县城，S2）', 'Mesa Roja 梅萨罗哈（S6）、Port Marais 马雷港（港，S5）'], ['主街 + 两侧平行街 2–4 条', '+ 横街 8–12 条；1.2–1.6 × 0.5–0.8 km'], ['Main St 用 C14', '其余 R12，工业 I12'], ['250–300', '主街店面 30–40'], ['州际经过：1 处菱形立交', '+ 1–2 处下穿；州道就是主街']],
    ['村 VILLAGE', ['Laurel Ridge、Elmwood、Redfield、Cobalt、Tamarack、Gull Harbor、', 'Birch Mills、Bayou Noir、Pelican Point、Fort Ocotillo'], ['州道穿村当主街', '+ 2–4 条短街；0.3–0.6 km'], ['Main St 用 C14', '其余 R12'], ['40–90', '主街店面 6–12'], ['只接州道', '（离州际近的从州道去最近的立交）']],
  ];
  let y = T + 30;
  for (const r of rows) {
    b += `<line x1="30" y1="${y - 22}" x2="1770" y2="${y - 22}" stroke="#c9ccc4"/>`;
    b += text(col[0], y, r[0], 'font-size="15" font-weight="700" fill="#23282b"');
    for (let i = 1; i < r.length; i++) r[i].forEach((t, j) => { b += text(col[i], y + j * 20, t, 'font-size="13.5" fill="#23282b"'); });
    y += 56;
  }
  // terrain style and role variants
  const L1 = lines(40, y + 20, [
    ['按地形摆（r1 种子量的；换种子时按当地地形重新判断）', 'font-size="16" font-weight="700" fill="#23282b"', 26],
    ['· 平地网格（起伏小）：Sutton、Elmwood、Redfield、Birch Mills、Gull Harbor、Port Marais、Bayou Noir、Mesa Roja'],
    ['· 谷地带状（沿主路拉长，两边上坡做台地）：Linden、Bellhaven、Laurel Ridge、Fort Ocotillo'],
    ['· 山地线形（沿路一两排，台地多）：Cobalt、Tamarack'],
    ['· 水边（沿岸摆，留码头 / 堤）：Pelican Point、Kessler 的河湖一侧'],
  ], 22, 'font-size="13.5" fill="#23282b"');
  b += L1.svg;
  const L2 = lines(940, y + 20, [
    ['按角色变体（模板里的 CityRole）', 'font-size="16" font-weight="700" fill="#23282b"', 26],
    ['· INDUSTRIAL（Kessler）：工业区占三分之一，厂房、仓库、货场'],
    ['· PORT（Port Marais、Gull Harbor）：码头区（码头仓库、渔港、油库）'],
    ['· 县城（Sutton）：主街中间是法院广场（Courthouse Square）'],
    ['· MINING（Cobalt）：矿区（井架、选矿厂、矿工住宅）'],
    ['· OUTPOST（Fort Ocotillo）：公路服务点：加油站、汽车旅馆、警察分驻所'],
  ], 22, 'font-size="13.5" fill="#23282b"');
  b += L2.svg;
  return sheet(1800, 1060, '三档城镇：同一比例的示意框架（平地），和 16 个城镇的分档', 'T1', b, '颜色同 T2 图例；示意图里的数字只是这张图的，实际按地形');
}
