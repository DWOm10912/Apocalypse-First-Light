// T2: Linden (valley_town) framework sample on r1's terrain: zoning, streets, lots, the F-40 diamond and underpasses.
import { sheet, text, lines, f, mono } from './svg.mjs';
import { shade, contours } from './raster.mjs';
import { lindenPlan } from './linden.mjs';
import { drawBlocks, drawStreets, nameStreets, TERRACE_PATTERN } from './draw.mjs';
import { ZONES, SECTIONS } from './layout.mjs';
import { across } from './frame.mjs';

export function lindenSheet() {
  const L = lindenPlan(), { F, g } = L;
  // window: the town's extent plus a margin, north up
  const pts = [];
  for (let s = -1250; s <= 650; s += 50) for (const v of [-330, 540]) pts.push(F.W(s, v));
  const xs = pts.map(p => p[0]), zs = pts.map(p => p[1]);
  const x0 = Math.min(...xs), x1 = Math.max(...xs), z0 = Math.min(...zs), z1 = Math.max(...zs);
  const MW = 1290, MH = 880, k = Math.min(MW / (x1 - x0), MH / (z1 - z0));
  const ox = 40 + (MW - (x1 - x0) * k) / 2, oy = 96 + (MH - (z1 - z0) * k) / 2;
  const P = (x, z) => [ox + (x - x0) * k, oy + (z - z0) * k];
  const r = shade(g, x0, z0, x1, z1, k);
  let m = `<defs>${TERRACE_PATTERN}<clipPath id="map"><rect x="40" y="96" width="${MW}" height="${MH}"/></clipPath></defs>`;
  m += `<g clip-path="url(#map)"><image href="${r.uri}" x="${f(ox)}" y="${f(oy)}" width="${r.w}" height="${r.h}"/>`;
  m += `<path d="${contours(g, x0, z0, x1, z1, 5, 12, P)}" stroke="#8a7656" stroke-width="0.5" fill="none" opacity="0.45"/>`;
  // town limit
  const lim = [];
  for (let s = -1180; s <= 540; s += 20) lim.push(F.W(s, 470));
  for (let s = 540; s >= -1180; s -= 20) lim.push(F.W(s, s > 120 ? -250 : -205));
  m += `<path d="M${lim.map(p => P(...p).map(f).join(',')).join('L')}Z" fill="none" stroke="#8e3a32" stroke-width="2" stroke-dasharray="10 5"/>`;
  m += drawBlocks(L.blocks, P, { labelSize: 12 });
  // F-40 under the town streets except 6th St's overpass
  const f40 = L.f40.filter(p => p[0] > x0 - 200 && p[0] < x1 + 200);
  const fp = 'M' + f40.map(p => P(...p).map(f).join(',')).join('L');
  m += drawStreets(L.streets.filter(s => s.kind !== 'over'), P, k);
  m += `<path d="${fp}" fill="none" stroke="#f4f2ec" stroke-width="${f(44 * k)}"/><path d="${fp}" fill="none" stroke="#3b4043" stroke-width="${f(32 * k)}"/><path d="${fp}" fill="none" stroke="#9aa59a" stroke-width="${f(4 * k)}"/>`;
  for (const rp of L.ramps) { const d = 'M' + rp.map(p => P(...p).map(f).join(',')).join('L'); m += `<path d="${d}" fill="none" stroke="#f4f2ec" stroke-width="${f(12 * k)}"/><path d="${d}" fill="none" stroke="#4d5255" stroke-width="${f(7 * k)}"/>`; }
  // underpasses: F-40 bridge decks over 3rd and 10th
  for (const st of L.streets.filter(s => s.kind === 'under')) {
    for (const v of [-24, 24]) { const a = P(...F.W(st.s - 16, v)), b = P(...F.W(st.s + 16, v)); m += `<line x1="${f(a[0])}" y1="${f(a[1])}" x2="${f(b[0])}" y2="${f(b[1])}" stroke="#23282b" stroke-width="2.2"/>`; }
  }
  m += drawStreets(L.streets.filter(s => s.kind === 'over'), P, k);
  for (const v of [-24, 24]) for (const ds of [-14, 14]) { const a = P(...F.W(L.sc + ds, v - 8)), b = P(...F.W(L.sc + ds, v + 8)); m += `<line x1="${f(a[0])}" y1="${f(a[1])}" x2="${f(b[0])}" y2="${f(b[1])}" stroke="#23282b" stroke-width="2"/>`; }
  m += nameStreets(L.streets, P, 10.5);
  // labels on the map
  const lab = (s, v, str, attrs = '') => {
    const q = P(...F.W(s, v)), fill = /fill="([^"]+)"/.exec(attrs)?.[1] || '#23282b', size = /font-size="([^"]+)"/.exec(attrs)?.[1] || '13';
    return text(q[0], q[1], str, `font-size="${size}" font-weight="700" fill="${fill}" text-anchor="middle" stroke="#fbfbf8" stroke-width="4" paint-order="stroke"`);
  };
  m += lab(-1080, -8, 'F-40 ← Sutton / Mesa Roja');
  m += lab(560, -38, 'F-40 → Bellhaven');
  m += lab(L.sc, -122, 'EXIT · 6th St / Downtown', 'fill="#8e3a32"');
  m += lab(-750, -112, '3rd St 下穿 F-40', 'font-size="11"');
  m += lab(300, 186, '10th St 下穿', 'font-size="11"');
  m += lab(-450, 500, '镇界（城界牌立在进镇的每条路上）', 'fill="#8e3a32" font-size="12"');
  m += lab(-60, -300, '南坡 17–19 m 坎：台地或留坡', 'font-size="11" fill="#6b5640"');
  m += lab(330, 430, '北山（不建）', 'font-size="12" fill="#6b5640"');
  // scale bar and north
  const sb = 200 * k;
  m += `</g><rect x="56" y="${96 + MH - 40}" width="${f(sb + 24)}" height="30" fill="#fbfbf8cc"/><line x1="66" y1="${96 + MH - 20}" x2="${f(66 + sb)}" y2="${96 + MH - 20}" stroke="#23282b" stroke-width="3"/>`;
  m += text(66 + sb / 2, 96 + MH - 26, '200 m', 'font-size="12" text-anchor="middle" fill="#23282b"');
  m += `<g transform="translate(${40 + MW - 34},${130})"><polygon points="0,-22 8,6 0,0 -8,6" fill="#23282b"/>${text(0, 22, 'N', 'font-size="13" font-weight="700" text-anchor="middle"')}</g>`;
  m += `<rect x="40" y="96" width="${MW}" height="${MH}" fill="none" stroke="#23282b" stroke-width="1.5"/>`;

  // right panel: legend, counts, sections
  const X = 1360;
  let p = text(X, 120, '图例', 'font-size="17" font-weight="700" fill="#23282b"');
  let y = 146;
  for (const [key, z] of Object.entries(ZONES)) {
    p += `<rect x="${X}" y="${y - 12}" width="22" height="15" fill="${z.fill}" stroke="#7b6e5c"/>` + text(X + 32, y, z.label, 'font-size="13" fill="#23282b"');
    y += 22;
  }
  p += `<rect x="${X}" y="${y - 12}" width="22" height="15" fill="url(#terrace)" stroke="#5b5146"/>` + text(X + 32, y, '街区起伏 > 6 m：做台地', 'font-size="13" fill="#23282b"');
  y += 22;
  p += `<line x1="${X}" y1="${y - 5}" x2="${X + 22}" y2="${y - 5}" stroke="#8e3a32" stroke-width="2" stroke-dasharray="6 3"/>` + text(X + 32, y, '镇界', 'font-size="13" fill="#23282b"');
  y += 34;
  // counts
  const cnt = {};
  let lotsAll = 0, terr = 0;
  for (const b of L.blocks) { if (b.relief > 6) terr++; for (const l of b.lots) { cnt[l.zone] = (cnt[l.zone] || 0) + 1; lotsAll++; } }
  const ext = 'Main St 西头到 11th St 约 1.6 km（沿 F-40），宽约 0.7 km';
  const rows = [
    ['Linden 样例的数字', 'font-size="17" font-weight="700" fill="#23282b"', 28],
    [`街区 ${L.blocks.length} 个（${terr} 个要做台地），地块 ${lotsAll} 块：`],
    [`  住宅 ${cnt.res || 0}、市中心店面 ${cnt.down || 0}、公路商业 ${cnt.hwy || 0}`],
    [`  拖车房 ${cnt.mobile || 0}、公共 ${cnt.civic || 0}、工业 ${cnt.ind || 0}、停车场 ${cnt.park_lot || 0}`],
    ['  学校 1、镇广场 1'],
    [ext],
    ['平行街：Front（两侧）、Main、Oak、Maple、Elm'],
    ['横街：1st–11th St'],
    ['镇中心：Main St × 6th St（Town Square 在 5th–6th）', '', 34],
    ['和 F-40 怎么接', 'font-size="17" font-weight="700" fill="#23282b"', 28],
    ['· 6th St 上跨 F-40，菱形立交（第二组那套）：EXIT 6th St'],
    ['  / Downtown；匝道口离 Front St 约 25 m'],
    ['· 3rd St、10th St 从 F-40 下面穿过（F-40 上两座小桥）'],
    ['· 别的横街到 Front St 就停，F-40 两边各一条 Front St'],
    ['· F-40 线位不动；镇子按它摆，主轴就是 F-40 的走向', '', 34],
    ['地形（r1 种子，规划高度）', 'font-size="17" font-weight="700" fill="#23282b"', 28],
    ['· 谷底在 F-40 北侧和西段，高 107–117'],
    ['· 南侧 7th–8th 一带有 17–19 m 的坎，东段北面是山'],
    ['· 起伏 ≤ 6 m 的街区整平；6–20 m 做台地；更陡不建'],
  ];
  const tx = lines(X, y, rows, 22, 'font-size="13.5" fill="#23282b"');
  p += tx.svg;
  y = tx.y + 14;
  // street sections
  p += text(X, y, '街道断面（米 = 格）', 'font-size="17" font-weight="700" fill="#23282b"');
  y += 14;
  for (const key of ['C14', 'R12', 'I12']) {
    const sct = SECTIONS[key], sk = 5.2, w = sct.row * sk, side = (sct.row - sct.asphalt) / 2;
    p += `<rect x="${X}" y="${y + 6}" width="${f(w)}" height="16" fill="#e7e4dc" stroke="#7b6e5c"/>`;
    p += `<rect x="${f(X + side * sk)}" y="${y + 6}" width="${f(sct.asphalt * sk)}" height="16" fill="#5a5f62"/>`;
    p += text(X + w + 12, y + 19, `${sct.label}：沥青 ${sct.asphalt}，红线 ${sct.row}`, 'font-size="13" fill="#23282b"');
    y += 26;
  }
  const body = m + p;
  return sheet(1800, 1060, 'Linden（林登，valley_town）框架样例：r1 种子的真实地形和 F-40 线位，不放建筑', 'T2',
    body, '离线：TownSurvey 地形 + SystemPlans 路网（c3_v2），2026-10-17');
}
