// T3: the lot and building size table the framework hands to the buildings (lot W along the street x D deep, the
// building body, storeys and height), each drawn to one scale; the block sizes and the storey rule.
import { sheet, text, lines, f } from './svg.mjs';
import { ZONES } from './layout.mjs';

// [name, lot W, lot D, body W, body D, body text, storeys / height, where, note, zone]
const GROUPS = [
  ['住宅', 'res', [
    ['小住宅', 24, 32, 14, 16, '14×16', '1–1.5 层 · 8', '住宅区 · 村镇城', ''],
    ['大住宅', 32, 40, 20, 22, '20×22', '2 层 · 10', '住宅区 · 镇城', ''],
    ['双拼住宅', 32, 32, 22, 16, '22×16', '2 层 · 10', '住宅区 · 镇城', ''],
    ['拖车房', 16, 28, 5, 20, '5×20', '1 层 · 4', '拖车营地 · 村镇', ''],
    ['公寓楼', 48, 48, 32, 18, '32×18', '3 层 · 13', '住宅区 · 镇城', ''],
    ['农庄', 64, 96, 16, 24, '屋 14×16 + 仓 16×24', '2 层 · 10', '镇外', ''],
  ]],
  ['市中心', 'down', [
    ['窄店面', 12, 32, 12, 22, '12×22', '2 层 · 9', '主街', '首层 5 格高'],
    ['宽店面', 24, 32, 24, 22, '24×22', '2 层 · 9', '主街', ''],
    ['街角楼（银行 / 旅馆）', 24, 32, 24, 28, '24×28', '3 层 · 13', '主街街角', ''],
    ['办公楼', 48, 48, 29, 23, '29×23 起', '4–17 层', '城市中心', '已有 office_midrise_01 等'],
  ]],
  ['公路商业', 'hwy', [
    ['加油站 + 便利店', 64, 64, 24, 16, '店 24×16 + 罩棚', '1 层 · 6', '立交口、主街两头', '已有 gas_station_01（A1）'],
    ['便利店', 40, 48, 24, 16, '24×16', '1 层 · 6', '主街、公路', ''],
    ['餐馆 / 快餐', 40, 48, 24, 20, '24×20', '1 层 · 6', '立交口', ''],
    ['汽车旅馆', 64, 48, 40, 12, 'L 形 40×12', '2 层 · 8', '立交口', ''],
    ['汽修店', 48, 56, 32, 24, '32×24', '1 层 · 8', '公路、工业', ''],
    ['超市', 80, 96, 48, 40, '48×40', '1 层 · 8', '镇城', ''],
    ['五金 / 农资店', 56, 64, 36, 28, '36×28', '1 层 · 8', '镇', ''],
  ]],
  ['公共', 'civic', [
    ['市政厅', 48, 48, 28, 20, '28×20', '2 层 · 10', '镇广场', ''],
    ['县法院', 64, 64, 30, 30, '30×30', '3 层 + 钟楼 · 20', 'Sutton 法院广场', ''],
    ['警察局', 48, 48, 28, 20, '28×20', '2 层 · 9', '镇城', ''],
    ['消防站', 48, 48, 28, 20, '28×20，3 个车库', '2 层 · 10', '镇城（村 32×32）', ''],
    ['邮局', 32, 40, 20, 16, '20×16', '1 层 · 6', '镇城', ''],
    ['教堂', 40, 48, 16, 28, '16×28 + 尖塔', '1 层 · 尖塔 20', '全部', ''],
    ['图书馆', 32, 40, 20, 18, '20×18', '1 层 · 7', '镇城', ''],
    ['诊所', 40, 48, 24, 20, '24×20', '1 层 · 6', '镇', ''],
    ['医院', 96, 96, 48, 40, '48×40', '4 层 · 17', '城市', ''],
    ['学校', 128, 160, 60, 40, '60×40 + 操场', '2 层 · 9', '镇城', ''],
  ]],
  ['工业', 'ind', [
    ['小仓库', 64, 64, 40, 32, '40×32', '1 层 · 10', '工业区', ''],
    ['工厂', 80, 96, 56, 48, '56×48', '1–2 层 · 14', '工业区（Kessler 多）', ''],
    ['粮仓合作社', 64, 64, 24, 16, '筒仓 4 个 Ø8 + 仓 24×16', '筒仓 24', '农镇', ''],
    ['锯木厂', 80, 96, 48, 24, '48×24 + 料场', '1 层 · 10', 'Birch Mills', ''],
    ['码头仓库', 48, 96, 36, 64, '36×64', '1 层 · 12', '港镇', ''],
    ['矿井', 96, 96, 32, 24, '井架 12×12 + 选矿房 32×24', '井架高 30', 'Cobalt', ''],
  ]],
];

const K = 0.42;   // px per m for the footprints

function icon(x, y, row, zone) {
  const [, W, D, bw, bd] = row, w = W * K, d = D * K;
  // the street below the lot; the body centred across, its front set back a third of the spare depth
  let s = `<rect x="${f(x)}" y="${f(y)}" width="${f(w)}" height="${f(d)}" fill="${ZONES[zone].fill}" stroke="#7b6e5c" stroke-width="0.8"/>`;
  const bx = x + (w - bw * K) / 2, by = y + d - (D - bd) / 3 * K - bd * K;
  s += `<rect x="${f(bx)}" y="${f(by)}" width="${f(bw * K)}" height="${f(bd * K)}" fill="#5f574d"/>`;
  s += `<line x1="${f(x - 2)}" y1="${f(y + d + 2)}" x2="${f(x + w + 2)}" y2="${f(y + d + 2)}" stroke="#4d5255" stroke-width="2"/>`;
  return s;
}

export function lotsSheet() {
  let b = '';
  const cols = [{ x: 40, groups: [0, 1, 2] }, { x: 920, groups: [3, 4] }];
  const C = [0, 78, 240, 310, 545, 665];   // icon, name, lot, body, storeys, where (relative)
  for (const col of cols) {
    let y = 106;
    const head = ['同比例', '类型', '地块 W×D', '建筑主体', '层 · 高（格）', '在哪'];
    b += `<rect x="${col.x - 10}" y="${y - 18}" width="850" height="28" fill="#e3e6df"/>`;
    head.forEach((h, i) => { b += text(col.x + C[i], y + 1, h, 'font-size="13.5" font-weight="700" fill="#23282b"'); });
    y += 22;
    for (const gi of col.groups) {
      const [gname, zone, rows] = GROUPS[gi];
      y += 8;
      b += `<rect x="${col.x - 10}" y="${y - 2}" width="12" height="16" fill="${ZONES[zone].fill}" stroke="#7b6e5c"/>` + text(col.x + 10, y + 12, gname, 'font-size="15" font-weight="700" fill="#23282b"');
      y += 22;
      for (const r of rows) {
        const h = Math.max(r[8] ? 46 : 30, r[2] * K + 10);
        b += `<line x1="${col.x - 10}" y1="${f(y - 4)}" x2="${col.x + 840}" y2="${f(y - 4)}" stroke="#d5d8d0"/>`;
        b += icon(col.x, y, r, zone);
        const ty = y + Math.min(h, 34) / 2 + 3;
        b += text(col.x + C[1], ty, r[0], 'font-size="13.5" font-weight="700" fill="#23282b"');
        b += text(col.x + C[2], ty, `${r[1]}×${r[2]}`, 'font-size="13.5" fill="#23282b"');
        b += text(col.x + C[3], ty, r[5], 'font-size="13" fill="#23282b"');
        b += text(col.x + C[4], ty, r[6], 'font-size="13" fill="#23282b"');
        b += text(col.x + C[5], ty, r[7], 'font-size="12.5" fill="#23282b"');
        if (r[8]) b += text(col.x + C[5], ty + 16, r[8], 'font-size="11.5" fill="#2f6f9f"');
        y += h;
      }
    }
    col.end = y;
  }
  // the rules under the right column
  const R = lines(920, cols[1].end + 30, [
    ['规则', 'font-size="16" font-weight="700" fill="#23282b"', 26],
    ['· 1 格 = 1 m；W 沿街，D 往里；图里地块下边那条线是临街一侧'],
    ['· 层高 4 格（店面首层 5 格）；高度到屋面，不含烟囱和招牌'],
    ['· 地块从街道红线外开始；主体到地块边的退界照'],
    ['  north_american_roads_and_lots_spec_v1.md 第 5 节'],
    ['· 宽度可以在同一街区里按比例拉伸 ±15 %，让一排正好排满'],
    ['· 框架只交地块（位置、朝向、档位、出入口），建筑以后按档位做'],
  ], 21, 'font-size="13.5" fill="#23282b"');
  b += R.svg;
  const B = lines(40, Math.max(cols[0].end + 30, 0), [
    ['街区', 'font-size="16" font-weight="700" fill="#23282b"', 26],
    ['· 住宅：两排背对背 32 + 后巷 6 + 32 = 70 深，长 128（横街间距 150）'],
    ['· 市中心：同样 70 深；临主街一排店面 12–24 宽，背后停车或小地块'],
    ['· 公路商业、工业：一排 64 深（加油站正好一块 64×64）'],
    ['· 学校、广场、医院：整块街区，或并两块（中间那条街不修）'],
  ], 21, 'font-size="13.5" fill="#23282b"');
  b += B.svg;
  return sheet(1800, Math.ceil(Math.max(1080, R.y + 80, B.y + 80)), '地块和建筑尺寸总表：框架按这些档位切地块，以后的建筑按档位做', 'T3', b,
    '在 2026-10-06 地块规范第 5 节上补齐城镇用的类型；同比例 0.42 px/m');
}
