// S2: the interstate corners at cities (joined and relaxed in the planner) and the shallow F-65 joins, on seed 10164's
// real plan around valley_town (the sharpest corner, 134 deg), with the seven corners of the six networks.
import fs from 'fs';
import { sheet, text, lines, line, polyline, f } from './svg.mjs';
import { Turtle, view, deg } from './geom.mjs';
import { joinCorner } from './relax.mjs';

const C = {
  turf: '#e2e7d6', road: '#4b5053', state: '#8a8f86', cut: '#b3b6ae', after: '#2f6f9f', ink: '#23282b', grey: '#6b7376', red: '#8e3a32',
  town: '#c0702c',
};
// extracted from Main's run cache (read only) by hand; see the file's "source"
const DATA = './data/seed10164_valley_town.json';

function map() {
  const j = JSON.parse(fs.readFileSync(new URL(DATA, import.meta.url), 'utf8'));
  const R = id => j.routes.find(r => r.id === id).control.map(p => [p[0], -p[1]]);   // world z south -> sheet north up
  const N = id => { const n = j.nodes.find(n => n.id === id); return [n.x, -n.z]; };
  const a = R('interstate_ew:county_seat-valley_town'), b = R('interstate_ew:valley_town-river_city');
  const n1 = R('interstate_ns:industrial_city-valley_town'), n2 = R('interstate_ns:valley_town-port_city');
  const hills = R('sh_hills:valley_town-hill_village');
  const town = N('valley_town'), j1 = N('interstate_ns:industrial_city-valley_town/join/end'), j2 = N('interstate_ns:valley_town-port_city/join/start');
  const c = joinCorner(a, b, town);
  const joined = c.joined, old = joined.slice(c.lo, c.hi + 1);
  // window round the corner and both joins
  const xs = [town[0], j1[0], j2[0]], ys = [town[1], j1[1], j2[1]];
  const x0 = Math.min(...xs) - 700, x1 = Math.max(...xs) + 700, y0 = Math.min(...ys) - 600, y1 = Math.max(...ys) + 600;
  const W = 1205, H = 712, s = Math.min(W / (x1 - x0), H / (y1 - y0));
  const V = view(60 + (W - (x1 - x0) * s) / 2 - x0 * s, 100 + (H - (y1 - y0) * s) / 2 + y1 * s, s);
  const inWin = p => p[0] > x0 - 600 && p[0] < x1 + 600 && p[1] > y0 - 600 && p[1] < y1 + 600;
  const roadLine = (p, colour, w, extra = '') => polyline(p.filter(inWin).map(V.P), `stroke="${colour}" stroke-width="${w}" stroke-linejoin="round" ${extra}`);
  let g = `<rect x="60" y="100" width="${W}" height="${H}" fill="${C.turf}"/>`;
  g += `<clipPath id="win"><rect x="60" y="100" width="${W}" height="${H}"/></clipPath><g clip-path="url(#win)">`;
  g += roadLine(hills, C.state, 3);
  g += roadLine(n1, C.road, 5) + roadLine(n2, C.road, 5);
  // F-40: unchanged outside the window, the old corner (the two legs into the town, trimmed apart there now), the new line
  g += roadLine(joined.slice(0, c.lo + 1), C.road, 6) + roadLine(joined.slice(c.hi), C.road, 6);
  g += roadLine(old, C.cut, 5, 'stroke-dasharray="7 5"');
  g += roadLine(c.win, C.after, 6, 'stroke-dasharray="12 6"');
  for (const p of [c.win[0], c.win[c.win.length - 1]]) g += `<circle cx="${f(V.X(p[0]))}" cy="${f(V.Y(p[1]))}" r="5" fill="${C.after}"/>`;
  let best = c.win[0];
  for (const p of c.win) if (Math.hypot(p[0] - town[0], p[1] - town[1]) < Math.hypot(best[0] - town[0], best[1] - town[1])) best = p;
  g += line(V.P(town), V.P(best), `stroke="${C.after}" stroke-width="1.4"`);
  g += text(V.X((town[0] + best[0]) / 2) + 10, V.Y((town[1] + best[1]) / 2) + 4, `${Math.round(c.closest / 10) * 10} m`, `font-size="14" font-weight="700" fill="${C.after}"`);
  // the town and the two system interchanges
  g += `<circle cx="${f(V.X(town[0]))}" cy="${f(V.Y(town[1]))}" r="9" fill="${C.town}"/>`;
  g += text(V.X(town[0]) - 14, V.Y(town[1]) + 6, 'valley_town（现状在这里折 134°）', `font-size="15" font-weight="700" fill="${C.town}" text-anchor="end" stroke="#e2e7d6" stroke-width="4" paint-order="stroke"`);
  for (const [p, label, dx, dy, anchor] of [[j1, '喇叭形 ①：北接点 82°', 0, 500 * V.s + 22, 'middle'],
    [j2, '喇叭形 ②：南接点 19° → 规划里改成 ≥ 60°', 0, -500 * V.s - 10, 'middle']]) {
    g += `<circle cx="${f(V.X(p[0]))}" cy="${f(V.Y(p[1]))}" r="${f(500 * V.s)}" fill="none" stroke="${C.red}" stroke-width="2" stroke-dasharray="4 4"/>`;
    g += text(V.X(p[0]) + dx, V.Y(p[1]) + dy, label, `font-size="14" font-weight="700" fill="${C.red}" text-anchor="${anchor}" stroke="#e2e7d6" stroke-width="4" paint-order="stroke"`);
  }
  g += '</g>';
  // route names on the last (or first) point of each route well inside the window, clear of the legend
  const inner = p => { const [X, Y] = V.P(p); return X > 80 && X < 60 + W - 20 && Y > 125 && Y < 100 + H - 20 && !(X < 700 && Y < 245); };
  const edge = (p, last) => { const q = p.filter(inner); return last ? q[q.length - 1] : q[0]; };
  const lab = (p, sx, sy, s2, anchor = 'start') => p ? text(V.X(p[0]) + sx, V.Y(p[1]) + sy, s2, `font-size="14" font-weight="700" fill="${C.ink}" text-anchor="${anchor}" stroke="#e2e7d6" stroke-width="4" paint-order="stroke"`) : '';
  g += lab(edge(a, false), 12, 18, 'F-40 ← county_seat');

  g += lab(edge(n1, false), 12, 4, 'F-65 ← industrial_city');
  g += lab(edge(n2, true), -40, -8, 'F-40 → river_city，F-65 → port_city', 'end');
  // legend, top left
  g += `<rect x="70" y="110" width="620" height="120" fill="${C.turf}" opacity="0.9"/>`;
  g += text(78, 130, `seed 10164… 规划网络，valley_town 一带（真实坐标，北在上），1 px ≈ ${f(1 / V.s)} m`, `font-size="13" fill="${C.grey}"`);
  g += line([78, 152], [118, 152], `stroke="${C.road}" stroke-width="6"`) + text(126, 157, '规划线（这次不动的部分）', `font-size="13" fill="${C.ink}"`);
  g += line([78, 174], [118, 174], `stroke="${C.cut}" stroke-width="5" stroke-dasharray="7 5"`) + text(126, 179, '现状：两段在城里折角，建网时各自截断，车开不过去', `font-size="13" fill="${C.ink}"`);
  g += line([78, 196], [118, 196], `stroke="${C.after}" stroke-width="6" stroke-dasharray="12 6"`) + text(126, 201, '改后：城前后各 2 km 接成一条弯（R ≥ 660 m，离线按规划器规则算出）', `font-size="13" fill="${C.ink}"`);
  g += line([78, 218], [118, 218], `stroke="${C.state}" stroke-width="3"`) + text(126, 223, '州道 sh_hills', `font-size="13" fill="${C.ink}"`);
  return { svg: g, closest: c.closest, shift: c.shift, minR: c.minR };
}

function notes(m) {
  const rows = [
    ['做法（推荐：在规划里放缓）', 'font-size="20" font-weight="700" fill="#23282b"', 36],
    ['① 现状：F-40 每段在两座城之间单独规划，在城里', ''], ['    的节点上折 86°–134°（6 个网络共 7 处），建网时', ''],
    ['    两段各自截断，车开不过去。≤ 45° 的折角建网时', ''], ['    已经合成一条（C3.3a）。', '', 30],
    ['② 改法（规划阶段 G1）：同一条州际相邻两段规划完，', ''], ['    在城里接成一条线，城前后各 2 km 内按规划器原有', ''],
    ['    的放缓规则拉顺到 R ≥ 660 m（州际最小半径 600 m', ''], ['    的 1.1 倍）；2 km 以外的线位不动。', '', 30],
    ['③ 然后在真实地形上重新设计这一段：纵断面、桥、', ''], ['    隧道、挖填，和别的路段一样走设计反馈。', '', 30],
    ['④ 离线按同一算法算了 6 个网络：7 处里 6 处放得开，线位', ''], ['    最多挪 120–690 m，州际从城旁 120–690 m 处经过', ''],
    [`    （这张图 ${Math.round(m.closest / 10) * 10} m）。城以后用出口接（城镇入口，不在第三组）。`, ''],
    ['    剩下 1 处（seed 4 valley_town）出城那段先往西再折回南，', ''], ['    拉顺要挪 1 km 以上：改成从城前约 1 km 处顺着进城方向', ''],
    ['    重新搜索出城那段，再拉顺。', '', 30],
    ['⑤ F-65 并上 F-40 的最后一段也在规划里改：交角', ''], ['    &lt; 60° 时最后约 1.5 km 改线，≥ 60° 进入，给喇叭形', ''],
    ['    留出正常形状（12 处里 6 处要改，这张图南接点 19°）。', '', 30],
    ['⑥ 为什么放在规划里：离线建网没有地形，只能"拉直线', ''], ['    + 硬挖填"；这处两头高差 56 m，附近有隧道、桥和高架，', ''],
    ['    只有规划器（有地形、会放桥和隧道）能做对。', '', 30],
    ['⑦ 代价：规划版本升级，新世界 F-40 在这几座城附近换位置；', ''], ['    州道接点、第二组菱形立交跟着新线重选；Main 的开局', ''],
    ['    老宅在 valley_town 附近找位置，可能跟着挪，合并前通知', ''], ['    Main。旧世界不变（版本锁）。', '', 30],
    ['⑧ 验证：用隔离客户端重新规划这 6 个种子（只规划，不', ''], ['    生成区块），再跑新世界探针。', ''],
  ];
  return lines(1300, 120, rows, 22, `font-size="15" fill="${C.ink}"`).svg;
}

function table() {
  let s = `<rect x="1030" y="860" width="930" height="480" fill="#f4f5f0" stroke="#c9ccc4"/>`;
  s += text(1048, 890, '7 处州际城镇折角（离线普查 + 按规划器放缓规则算）', `font-size="17" font-weight="700" fill="${C.ink}"`);
  const rows = [
    ['种子', '城', '折角', '两段高差', '线位最多挪', '离城中心'],
    ['4', 'county_seat', '116°', '0', '686 m', '685 m'],
    ['4', 'valley_town', '96°', '10.4 m', '出城段折回', '→ 重新搜索'],
    ['10132…', 'valley_town', '86°', '0', '120 m', '120 m'],
    ['10164…', 'county_seat', '91°', '0.1 m', '235 m', '235 m'],
    ['10164…', 'valley_town', '134°', '56.2 m', '284 m', '267 m'],
    ['11823…', 'county_seat', '92°', '0.1 m', '267 m', '267 m'],
    ['16060…', 'county_seat', '111°', '0.1 m', '311 m', '311 m'],
  ];
  const xs = [1050, 1190, 1380, 1480, 1620, 1780];
  rows.forEach((r, i) => {
    const y = 930 + i * 32;
    if (i === 0) s += `<rect x="1046" y="${y - 22}" width="890" height="30" fill="#e6e8e1"/>`;
    r.forEach((c, k) => s += text(xs[k], y, c, `font-size="15" fill="${i === 5 ? C.red : C.ink}" ${i === 0 ? 'font-weight="700"' : ''}`));
  });
  s += lines(1050, 1200, [
    ['· 只算了平面（城前后各 2 km，R ≥ 660 m，窗口两头不动），没算地形；规划器按地形', ''],
    ['  设计以后线位还会再变。红色一行就是左上的图。', '', 28],
    ['· ≤ 45° 的另外 5 处（含 r1 默认种子的两座城）同样改走这条规则，只挪 0–108 m。', ''],
  ], 22, `font-size="14" fill="${C.ink}"`).svg;
  return s;
}

/** The corner geometry and the shallow-join realignment, as small diagrams. */
function principle() {
  let s = `<rect x="40" y="860" width="960" height="480" fill="#f4f5f0" stroke="#c9ccc4"/>`;
  s += text(58, 890, '放缓的几何 / F-65 浅角并线的改线', `font-size="17" font-weight="700" fill="${C.ink}"`);
  // the corner: incoming from the west, leaving with deflection D (to the left), the arc of R tangent to both
  const D = deg(100), R = 140, T = R * Math.tan(D / 2), P = [330, 1236], ub = [Math.cos(D), -Math.sin(D)];
  const A = [P[0] - 280, P[1]], B = [P[0] + ub[0] * 260, P[1] + ub[1] * 260];
  const ta = [P[0] - T, P[1]], tb = [P[0] + ub[0] * T, P[1] + ub[1] * T], O = [ta[0], ta[1] - R];
  s += line(A, P, `stroke="${C.cut}" stroke-width="5" stroke-dasharray="7 5"`) + line(P, B, `stroke="${C.cut}" stroke-width="5" stroke-dasharray="7 5"`);
  s += line(A, ta, `stroke="${C.road}" stroke-width="5"`) + line(tb, B, `stroke="${C.road}" stroke-width="5"`);
  s += `<path d="M ${f(ta[0])} ${f(ta[1])} A ${R} ${R} 0 0 0 ${f(tb[0])} ${f(tb[1])}" fill="none" stroke="${C.after}" stroke-width="5" stroke-dasharray="12 6"/>`;
  s += `<circle cx="${P[0]}" cy="${P[1]}" r="8" fill="${C.town}"/>` + text(P[0] + 14, P[1] + 22, '城', `font-size="14" fill="${C.town}"`);
  s += line(O, ta, `stroke="${C.grey}" stroke-width="1" stroke-dasharray="3 3"`) + text(O[0] - 20, (O[1] + ta[1]) / 2 + 5, 'R', `font-size="14" fill="${C.grey}"`);
  s += line([ta[0], ta[1] + 16], [P[0], P[1] + 16], `stroke="${C.grey}" stroke-width="1"`) + text((ta[0] + P[0]) / 2 - 4, P[1] + 34, 'T', `font-size="14" fill="${C.grey}"`);
  const dOP = Math.hypot(P[0] - O[0], P[1] - O[1]), mid = [O[0] + (P[0] - O[0]) / dOP * R, O[1] + (P[1] - O[1]) / dOP * R];
  s += line(mid, P, `stroke="${C.red}" stroke-width="2"`) + text((mid[0] + P[0]) / 2 + 10, (mid[1] + P[1]) / 2 + 4, 'E', `font-size="15" font-weight="700" fill="${C.red}"`);
  s += text(P[0] + 20, P[1] - 30, '折角 Δ', `font-size="14" fill="${C.ink}"`);
  s += lines(60, 1300, [['两段都是直线时：T = R·tan(Δ/2)，E = R·(1/cos(Δ/2) − 1)', ''], ['实际两段本身也在弯，所以按规划器的放缓规则算（右表）', '']], 20, `font-size="13" fill="${C.ink}"`).svg;
  // the shallow join: F-65 coming in at 20 deg, realigned over its last ~1.5 km to come in at 70 deg
  const V = view(574, 1236, 0.22), J = [1500, 0], h0 = deg(-20);
  s += polyline([[-100, 0], [1800, 0]].map(V.P), `stroke="${C.road}" stroke-width="6"`) + text(V.X(1800), V.Y(0) + 22, 'F-40', `font-size="14" fill="${C.ink}" text-anchor="end"`);
  const O0 = [J[0] - 1700 * Math.cos(h0), J[1] - 1700 * Math.sin(h0)];
  s += polyline([O0, J].map(V.P), `stroke="${C.cut}" stroke-width="5" stroke-dasharray="7 5"`);
  const K = [J[0] - 1450 * Math.cos(h0), J[1] - 1450 * Math.sin(h0)];
  const t = new Turtle(K[0], K[1], h0).arc(600, deg(-50));
  t.go(t.y / Math.sin(deg(70)));
  s += polyline([O0, K].map(V.P), `stroke="${C.road}" stroke-width="5"`);
  s += polyline(t.pts.map(V.P), `stroke="${C.after}" stroke-width="5" stroke-dasharray="12 6"`);
  s += text(V.X(J[0]) + 4, V.Y(0) - 12, '19°–46°', `font-size="13" fill="${C.grey}"`);
  s += text(V.X(t.x) - 10, V.Y(0) - 12, '≥ 60°', `font-size="13" font-weight="700" fill="${C.after}" text-anchor="end"`);
  s += text(V.X(O0[0]), V.Y(O0[1]) - 14, 'F-65 规划线：斜着贴上 F-40（12 处里 6 处 &lt; 60°）', `font-size="13" fill="${C.grey}"`);
  s += text(V.X(-100), V.Y(0) + 44, '改后：最后约 1.5 km 改线，≥ 60° 进入（喇叭形的柄）', `font-size="13" fill="${C.after}"`);
  return s;
}

export function cornerSheet() {
  const m = map();
  const head = text(60, 70, '州际在城里的折角：在规划里接成一条弯（F-40 从城旁经过），F-65 浅角并线改成正常进入', `font-size="22" font-weight="700" fill="${C.ink}"`);
  console.log(`  seed 10164 valley_town: passes ${m.closest.toFixed(0)} m from the town, moved up to ${m.shift.toFixed(0)} m, least radius ${m.minR.toFixed(0)} m`);
  return sheet(2000, 1420, '州际城镇折角和 F-65 进入角：规划层改法', 'S2', head + m.svg + notes(m) + principle() + table(), 'AFL C3.3b 第三组 · 州际—州际概念 · 2026-10-17 · 尺寸单位 m');
}
