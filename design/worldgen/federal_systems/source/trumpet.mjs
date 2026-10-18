// S1: the F-65 / F-40 system interchange concept (trumpet), with the directional T for comparison and the twelve joins.
import { sheet, text, mono, lines, line, poly, polyline, f } from './svg.mjs';
import { Turtle, deg, view, gap, at, length } from './geom.mjs';

const C = {
  turf: '#e2e7d6', form: '#d8d0bf', asphalt: '#4b5053', lane: '#f4f4f0', deck: '#b8b4aa', deckEdge: '#6f6a60',
  main: '#2f6f9f', minor: '#c0702c', ink: '#23282b', grey: '#6b7376', red: '#8e3a32', water: '#a9c4d6',
};
const RW = 7.8;           // ramp paved width (RAMP section: one 4.2 m lane, 1.8 m shoulders)
const CW = [9.5, 22];     // R4 carriageway: |v| from the crown to the outer edge

// ---------------------------------------------------------------- the trumpet (world metres, F-40 along x, north +y)
function trumpet() {
  const r = {};
  // A: F-65 south -> F-40 west (right turn, direct): off the SB outer lane, R 314, merging on the WB edge
  r.A = new Turtle(-18, 340, deg(-90)).arc(314, deg(-90)).to(-472, 19).pts;
  // B: F-40 west -> F-65 north (right turn, direct): off the WB edge, R 314 into the NB outer lane
  r.B = new Turtle(472, 19, deg(180)).to(332, 26).arc(314, deg(-90)).pts;
  // loop: F-40 east -> F-65 north (the minor left turn): off the EB edge after the bridge, R 85 through 270 deg
  r.L = new Turtle(-70, -19, 0).shift(-7, 160).arc(85, deg(-270)).go(111 + 260).shift(-7.375, 80).pts;
  // semi-direct: F-65 south -> F-40 east (the main left turn): over the bridge, round the loop, merging after it
  const sd = new Turtle(-12.875, 340, deg(-90)).shift(7.375, 80).go(300).shift(-29.5, 70);
  const R1 = 125.5, phi = deg(50), R2 = 320;
  sd.arc(R1, deg(90) + phi);
  const L = (-26 - (sd.y + R2 * (1 - Math.cos(phi)))) / Math.sin(phi);
  r.S = sd.go(L).arc(R2, -phi).to(sd.x + 160, -19).pts;
  return r;
}

function road(V, pts, w, colour = C.asphalt, extra = '') {
  return polyline(pts.map(V.P), `stroke="${C.form}" stroke-width="${f((w + 4) * V.s)}" stroke-linejoin="round"`)
    + polyline(pts.map(V.P), `stroke="${colour}" stroke-width="${f(w * V.s)}" stroke-linejoin="round" ${extra}`);
}

/** Direction arrows along a ramp (every `every` m), coloured by its movement. */
function arrows(V, pts, colour, every = 120, first = 60) {
  let s = '';
  const len = length(pts);
  for (let d = first; d < len - 20; d += every) {
    const a = at(pts, d), x = V.X(a.x), y = V.Y(a.y), h = -a.h, k = 7;
    const tip = [x + Math.cos(h) * k, y + Math.sin(h) * k];
    const l = [x + Math.cos(h + 2.5) * k, y + Math.sin(h + 2.5) * k], rr = [x + Math.cos(h - 2.5) * k, y + Math.sin(h - 2.5) * k];
    s += poly([tip, l, rr], `fill="${colour}"`);
  }
  return polyline(pts.map(V.P), `stroke="${colour}" stroke-width="1.6" stroke-dasharray="6 5"`) + s;
}

function num(x, y, n, colour = C.red) {
  return `<circle cx="${f(x)}" cy="${f(y)}" r="11" fill="${colour}"/>` + text(x, y + 5, String(n), 'font-size="13" font-weight="700" fill="#fff" text-anchor="middle"');
}

function leader(x0, y0, x1, y1, s, anchor = 'start') {
  return line([x0, y0], [x1, y1], `stroke="${C.red}" stroke-width="1"`) + text(x1 + (anchor === 'end' ? -6 : 6), y1 + 5, s, `font-size="14" fill="${C.ink}" text-anchor="${anchor}"`);
}

function interstate(V, x0, x1) {
  let s = '';
  for (const sg of [1, -1]) {
    const yc = sg * (CW[0] + CW[1]) / 2, w = CW[1] - CW[0];
    s += road(V, [[x0, yc], [x1, yc]], w);
    for (const v of [11, 22 - 3.5]) s += line(V.P([x0, sg * v]), V.P([x1, sg * v]), `stroke="${C.lane}" stroke-width="1"`);
    s += line(V.P([x0, sg * 14.75]), V.P([x1, sg * 14.75]), `stroke="${C.lane}" stroke-width="0.8" stroke-dasharray="3 9"`);
  }
  return s;
}

function stem(V, y0, y1) {
  let s = '';
  for (const sg of [1, -1]) {
    const xc = sg * (CW[0] + CW[1]) / 2;
    s += road(V, [[xc, y0], [xc, y1]], CW[1] - CW[0]);
    for (const v of [11, 22 - 3.5]) s += line(V.P([sg * v, y0]), V.P([sg * v, y1]), `stroke="${C.lane}" stroke-width="1"`);
  }
  return s;
}

// ---------------------------------------------------------------- the main plan
function mainPlan() {
  const V = view(612, 548, 0.95), r = trumpet();
  let s = `<rect x="60" y="100" width="1205" height="712" fill="${C.turf}"/>`;
  s += text(60, 70, '平面：F-65 从北面接 F-40 的喇叭形系统互通（并线方向朝东：F-65 沿 F-40 走向 valley_town）', `font-size="22" font-weight="700" fill="${C.ink}"`);
  s += text(78, 128, '按实际尺寸画（R 314 / 125 / 85 m），交角取 90°，斜交时匝道跟着斜', `font-size="13" fill="${C.grey}"`);
  s += interstate(V, -560, 700) + stem(V, 340, 470);
  for (const k of ['A', 'B', 'L', 'S']) s += road(V, r[k], RW);
  // the bridge: twin decks over F-40, a pier row in the median
  for (const x of [-5.5, 5.5]) s += `<rect x="${f(V.X(x - RW / 2 - 0.8))}" y="${f(V.Y(44))}" width="${f((RW + 1.6) * V.s)}" height="${f(88 * V.s)}" fill="${C.deck}" stroke="${C.deckEdge}" stroke-width="1"/>`;
  for (const x of [-7, -2.5, 2.5, 7]) s += `<circle cx="${f(V.X(x))}" cy="${f(V.Y(0))}" r="1.6" fill="${C.deckEdge}"/>`;
  s += arrows(V, r.S, C.main) + arrows(V, r.B, C.main, 140, 40) + arrows(V, r.A, C.minor, 140, 40) + arrows(V, r.L, C.minor, 110, 40);
  // labels
  s += text(V.X(640), V.Y(-46), 'F-40 东行 → valley_town', `font-size="14" fill="${C.ink}" text-anchor="end"`);
  s += text(V.X(-540), V.Y(36), '← F-40 西行 → county_seat', `font-size="14" fill="${C.ink}"`);
  s += text(V.X(30), V.Y(458), 'F-65（来自 industrial_city）', `font-size="14" fill="${C.ink}"`);
  s += num(V.X(-200), V.Y(-12), 1) + num(V.X(42), V.Y(380), 2);
  s += num(V.X(-210), V.Y(255), 3) + num(V.X(210), V.Y(255), 3);
  s += num(V.X(330), V.Y(-170), 4) + num(V.X(90), V.Y(-111), 5) + num(V.X(-52), V.Y(64), 6);
  s += text(V.X(-470), V.Y(-170), '西南角空着：以后放路边设施或排水', `font-size="13" fill="${C.grey}"`);
  // north arrow and scale bar
  s += poly([[1225, 140], [1218, 162], [1232, 162]], `fill="${C.ink}"`) + text(1225, 180, 'N', `font-size="13" fill="${C.ink}" text-anchor="middle"`);
  s += line([90, 790], [90 + 200 * V.s, 790], `stroke="${C.ink}" stroke-width="2"`) + mono(90, 782, '0', 'font-size="11"') + mono(90 + 200 * V.s - 18, 782, '200 m', 'font-size="11"');
  // legend
  s += line([78, 152], [118, 152], `stroke="${C.main}" stroke-width="2" stroke-dasharray="6 5"`) + text(126, 157, 'F-65 直行车流（往并线方向 / 从并线方向来）', `font-size="13" fill="${C.ink}"`);
  s += line([78, 176], [118, 176], `stroke="${C.minor}" stroke-width="2" stroke-dasharray="6 5"`) + text(126, 181, '次要转向（F-65 ↔ 另一头的 F-40）', `font-size="13" fill="${C.ink}"`);
  // measured clearances for the note
  return { svg: s, loopGap: Math.min(...r.S.filter(p => p[1] < -115).map(p => Math.hypot(p[0] - 90.5, p[1] + 111))) - 85, lenS: length(r.S), lenL: length(r.L) };
}

// ---------------------------------------------------------------- notes
function notes(m) {
  const rows = [
    ['做法（推荐：喇叭形）', 'font-size="20" font-weight="700" fill="#23282b"', 36],
    ['① F-40 主线不动：线形、纵断面、标线都照规划，', ''], ['    只在匝道口断开边线（同第二组）。', '', 30],
    ['② F-65 在 F-40 前约 340 m 结束，两幅车行道各分成', ''], ['    两条单车道匝道（规划线最后一段截掉）。', '', 30],
    ['③ 两个右转用直接式匝道，R 314 m（设计 70 km/h）：', ''], ['    F-65 南行 → F-40 西行；F-40 西行 → F-65 北行。', '', 30],
    ['④ 半定向匝道：F-65 南行 → F-40 东行（往并线方向，', ''], [`    主要车流）。过桥后绕到环形匝道外侧，R ≥ 125 m，`, ''],
    [`    离环形匝道 ${Math.round(m.loopGap - RW)} m，在环形匝道下游汇入；全长约 ${Math.round(m.lenS / 10) * 10} m。`, '', 30],
    ['⑤ 环形匝道：F-40 东行 → F-65 北行（次要的回头方向），', ''], ['    R 85 m 转 270°，设计 50 km/h，牌上 RAMP 30 MPH。', '', 30],
    ['⑥ 只有一座桥：两条匝道并排跨 F-40（两幅桥面），', ''], ['    梁底净空 ≥ 5.3 m，中央带一排墩（第二组跨线桥构件）。', '', 30],
    ['⑦ 朝向按车流定：哪一头是并线方向（F-65 沿 F-40', ''], ['    走的那段），半定向匝道就朝哪头；南边那处镜像。', '', 30],
    ['⑧ 匝道断面用第二组的单车道匝道，带渐变段、鼻端、', ''], ['    纵坡 ≤ 6 %；桥上匝道纵坡 ≤ 5 %。', '', 30],
    ['⑨ 选址：先建系统互通，第二组的菱形立交再避开它', ''], ['    （两座立交之间 ≥ 1.6 km，不够就挪或不建）。', '', 30],
    ['⑩ 指路标志接着用 B 包那套：F-40 上 1 MILE、', ''], ['    1/2 MILE 预告、出口牌、鼻端牌（出口号按 F-40', ''],
    ['    里程桩）；F-65 尽头是分流牌 F-40 WEST / F-40 EAST', ''], ['    （EAST 那块同时挂 F-65 SOUTH），带车道箭头。', '', 30],
  ];
  return lines(1300, 120, rows, 22, `font-size="15" fill="${C.ink}"`).svg;
}

// ---------------------------------------------------------------- plan B: the directional T (schematic)
function planB() {
  const V = view(318, 1100, 0.31);
  let s = `<rect x="40" y="860" width="960" height="480" fill="#f4f5f0" stroke="#c9ccc4"/>`;
  s += text(58, 890, '方案 B：定向 T（三层，没有环形匝道）', `font-size="17" font-weight="700" fill="${C.ink}"`);
  s += `<rect x="58" y="905" width="560" height="415" fill="${C.turf}"/>`;
  s += interstate(V, -760, 760) + stem(V, 300, 560);
  const A = new Turtle(-18, 340, deg(-90)).arc(314, deg(-90)).to(-472, 19).pts;
  const B = new Turtle(472, 19, deg(180)).to(332, 26).arc(314, deg(-90)).pts;
  // F-65 south -> F-40 east: flyover over F-40, a wide left sweep, merging on the EB edge
  const sb = new Turtle(-12.875, 340, deg(-90)).shift(7.375, 80).go(320), phi = deg(35);
  sb.arc(230, deg(90) + phi);
  const L = (-26 - (sb.y + 380 * (1 - Math.cos(phi)))) / Math.sin(phi);
  const S = sb.go(L).arc(380, -phi).to(sb.x + 140, -19).pts;
  // F-40 east -> F-65 north: off the EB edge before the stem, a left sweep over F-40 and the other flyover (third level)
  const E = new Turtle(-344.5, -19, 0).shift(-9, 120).arc(230, deg(90)).go(80).shift(-7.375, 60).pts;
  // the third level: where the two flyovers cross (x -5.5 on the southbound one)
  const third = E.reduce((b, p) => Math.abs(p[0] + 5.5) < Math.abs(b[0] + 5.5) ? p : b);
  for (const p of [A, B, S, E]) s += road(V, p, RW);
  s += arrows(V, S, C.main, 160, 80) + arrows(V, B, C.main, 160, 60) + arrows(V, A, C.minor, 160, 60) + arrows(V, E, C.minor, 160, 80);
  // where the two flyovers cross: the third level
  s += `<circle cx="${f(V.X(third[0]))}" cy="${f(V.Y(third[1]))}" r="14" fill="none" stroke="${C.red}" stroke-width="2"/>`;
  s += text(V.X(third[0]) + 20, V.Y(third[1]) - 14, '第三层', `font-size="13" fill="${C.red}"`);
  s += lines(640, 935, [
    ['· 两个左转都走定向匝道，车速高（70–80 km/h），', ''], ['  没有回头环。', '', 30],
    ['· 要两座长弯桥，两条匝道互相再跨一次（三层），', ''], ['  桥的长度约是喇叭形的 4–5 倍，占地也更大。', '', 30],
    ['· 现实里多见于城市、流量大的州际交叉；乡村', ''], ['  州际相接一般是喇叭形。', '', 30],
    ['· 两种都只做 F-65 接 F-40 这一类 T 形；四向的', ''], ['  苜蓿叶 / 四层立交在这张网里用不上', ''], ['  （两条州际不交叉成十字）。', '', 30],
  ], 22, `font-size="14" fill="${C.ink}"`).svg;
  return s;
}

// ---------------------------------------------------------------- the twelve joins
function table() {
  let s = `<rect x="1030" y="860" width="930" height="480" fill="#f4f5f0" stroke="#c9ccc4"/>`;
  s += text(1048, 890, '6 个网络里的 12 处 F-65 接 F-40（离线普查，SystemSurvey）', `font-size="17" font-weight="700" fill="${C.ink}"`);
  const rows = [
    ['种子', '北接点交角', '南接点交角', '附近的菱形立交'],
    ['4', '87°', '42°', '无'],
    ['r1（-295…513）', '83°', '61°', '北接点 1.6 km'],
    ['10132…', '77°', '30°', '无'],
    ['10164…', '82°', '19°', '无'],
    ['11823…', '67°', '46°', '北接点 1.3 km'],
    ['16060…', '43°', '24°', '无'],
  ];
  const xs = [1050, 1260, 1430, 1600];
  rows.forEach((r, i) => {
    const y = 930 + i * 32;
    if (i === 0) s += `<rect x="1046" y="${y - 22}" width="890" height="30" fill="#e6e8e1"/>`;
    r.forEach((c, j) => {
      const shallow = i > 0 && j > 0 && j < 3 && parseInt(c) < 60;
      s += text(xs[j], y, c, `font-size="15" fill="${shallow ? C.red : C.ink}" ${i === 0 ? 'font-weight="700"' : ''}`);
    });
  });
  s += lines(1050, 1170, [
    ['· 北半段 F-65 从 industrial_city 来，在 valley_town 西边约 2.4 km 接上 F-40；', ''],
    ['  南半段在 valley_town 东边约 2.5 km 离开 F-40 去 port_city。中间两条州际共线', ''],
    ['  （出口号沿 F-40 编，B 包已经这样做）。', '', 30],
    ['· 红色 6 处交角 &lt; 60°：F-65 斜着贴上 F-40（最浅的 19°–30° 要贴着走 1–1.4 km）。', ''],
    ['  喇叭形斜得太厉害，所以在规划里把 F-65 最后一段改成 ≥ 60° 进入（S2 第 ⑤ 条）。', '', 30],
    ['· 接点附近 F-40 半径都 ≥ 640 m、纵坡 ≤ 3 %；1.5 km 内多数没有桥，有桥的', ''],
    ['  离接点 140 m 以上，选址时沿 F-40 挪位置避开（同第二组）。', ''],
  ], 22, `font-size="14" fill="${C.ink}"`).svg;
  return s;
}

export function trumpetSheet() {
  const m = mainPlan();
  const body = m.svg + notes(m) + planB() + table();
  console.log(`  loop to semi-direct centreline gap ${m.loopGap.toFixed(1)} m, semi-direct ${m.lenS.toFixed(0)} m, loop ${m.lenL.toFixed(0)} m`);
  return sheet(2000, 1420, '系统互通：F-65 接 F-40 的喇叭形（推荐）和定向 T', 'S1', body, 'AFL C3.3b 第三组 · 州际—州际概念 · 2026-10-17 · 尺寸单位 m');
}
