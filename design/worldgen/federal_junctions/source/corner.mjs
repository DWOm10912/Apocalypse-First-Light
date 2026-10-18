// J2: town corners (C3.3b group 1): two state road ends at one town, today trimmed apart; the R 80 corner, the T-with-stub
// alternative, the profile re-grade, and the seed-4 cases.
import {f, pts, poly, line, text, sheet, dimH, leader, bubble} from './svg.mjs';
import {C, stopSign, wrap} from './tplan.mjs';

const EDGE = 6.05, RC = 80, TRIM = 81;
const add = (a, b, k = 1) => [a[0] + b[0] * k, a[1] + b[1] * k];
const unit = deg => [Math.cos(deg * Math.PI / 180), Math.sin(deg * Math.PI / 180)];

/** The R 80 arc between leg A (heading d1 into the node) and leg B (heading d2 out of it): points and the tangent length. */
function corner(N, d1, d2, r) {
  const cross = d1[0] * d2[1] - d1[1] * d2[0], sg = Math.sign(cross);
  const kink = Math.acos(Math.max(-1, Math.min(1, d1[0] * d2[0] + d1[1] * d2[1])));
  const T = r * Math.tan(kink / 2), TA = add(N, d1, -T), TB = add(N, d2, T);
  const n1 = sg > 0 ? [-d1[1], d1[0]] : [d1[1], -d1[0]], c = add(TA, n1, r);
  const a0 = Math.atan2(TA[1] - c[1], TA[0] - c[0]), arc = [];
  for (let i = 0; i <= 40; i++) { const a = a0 + sg * kink * i / 40; arc.push([c[0] + r * Math.cos(a), c[1] + r * Math.sin(a)]); }
  return {T, TA, TB, arc, kink: kink * 180 / Math.PI};
}

function road(ptsM, P, s, color = C.asphalt) {
  const q = ptsM.map(p => P(...p));
  return `<polyline points="${pts(q)}" fill="none" stroke="${C.formation}" stroke-width="${f(14 * s)}" stroke-linejoin="round" stroke-linecap="butt"/>` +
    `<polyline points="${pts(q)}" fill="none" stroke="${color}" stroke-width="${f(2 * EDGE * s)}" stroke-linejoin="round" stroke-linecap="butt"/>` +
    `<polyline points="${pts(q)}" fill="none" stroke="${C.yellow}" stroke-width="1.3" stroke-dasharray="5 9"/>`;
}

function panel(x0, y0, w, h, title) {
  return `<rect x="${x0}" y="${y0}" width="${w}" height="${h}" fill="#f4f6f1" stroke="#c9cec6"/>` +
    text(x0 + 16, y0 + 30, title, 'font-size="17" font-weight="700" fill="#23282b"');
}

/** pass_village: leg A arrives heading 20 deg below east, leg B leaves after a 99 deg kink. */
const D1 = unit(18), D2 = unit(18 + 99), L = 260;

function beforeAfter(x0, y0, w, h) {
  let b = panel(x0, y0, w, h, '城镇转角：两条州道在同一个镇相接，转角大于 45°（种子 4 pass_village，99°）');
  const s = 1.45, half = w / 2;
  for (const [k, after] of [[0, false], [1, true]]) {
    const N = [0, 0], P = (x, z) => [x0 + k * half + half * 0.62 + x * s, y0 + 250 + z * s];
    b += `<rect x="${x0 + k * half + 14}" y="${y0 + 50}" width="${half - 28}" height="${h - 64}" fill="${C.turf}"/>`;
    b += text(x0 + k * half + 28, y0 + 78, after ? '改后：R 80 转角，两段连成一条路' : '现在：两头各截掉约 80 m，镇上断开', `font-size="15.5" font-weight="700" fill="${after ? '#2b6a3c' : C.red}"`);
    const A0 = add(N, D1, -L), B1 = add(N, D2, L);
    if (!after) {
      b += road([A0, add(N, D1, -TRIM)], P, s) + road([add(N, D2, TRIM), B1], P, s);
      b += `<polyline points="${pts([P(...add(N, D1, -TRIM)), P(...N), P(...add(N, D2, TRIM))])}" fill="none" stroke="${C.red}" stroke-width="2" stroke-dasharray="8 6"/>`;
      b += leader(P(...add(N, D1, -TRIM)), add(P(...add(N, D1, -TRIM)), [-20, 70]), '截断（约 80 m）', 'end');
      b += leader(P(...add(N, D1, -40)), add(P(...add(N, D1, -40)), [40, -70]), '规划折线（不建）', 'start');
    } else {
      const k2 = corner(N, D1, D2, RC);
      b += road([A0, k2.TA, ...k2.arc, k2.TB, B1], P, s);
      b += `<polyline points="${pts(k2.arc.map(p => P(...p)))}" fill="none" stroke="#9fd3ae" stroke-width="2.4"/>`;
      b += leader(P(...k2.arc[20]), add(P(...k2.arc[20]), [-120, -40]), `R 80，切线长 ${Math.round(k2.T)} m`, 'end');
      b += leader(P(...k2.TA), add(P(...k2.TA), [-70, -70]), '起弯点', 'end');
      // curve warning sign with the 50 km/h plaque, 120 m before the corner on each leg
      for (const [p, d] of [[add(k2.TA, D1, -120), D1], [add(k2.TB, D2, 120), [-D2[0], -D2[1]]]]) {
        const side = [-d[1], d[0]], q = P(...add(p, side, 12));
        b += poly([[q[0], q[1] - 12], [q[0] + 12, q[1]], [q[0], q[1] + 12], [q[0] - 12, q[1]]], `fill="#f2c230" stroke="${C.ink}" stroke-width="1"`);
        b += `<rect x="${f(q[0] - 9)}" y="${f(q[1] + 14)}" width="18" height="11" fill="#f2c230" stroke="${C.ink}" stroke-width="0.8"/>` +
          text(q[0], q[1] + 23, '50', 'font-size="8.5" font-weight="700" text-anchor="middle" fill="#23282b"');
      }
      b += text(x0 + k * half + 28, y0 + h - 48, '弯前 120 m：转弯警告牌 + 50 km/h 建议车速牌', 'font-size="13.5" fill="#23282b"');
      b += text(x0 + k * half + 28, y0 + h - 26, '弯道超高 ≤ 6 %，跟州道一般弯道一样做', 'font-size="13.5" fill="#23282b"');
    }
    b += `<circle cx="${f(P(...N)[0])}" cy="${f(P(...N)[1])}" r="6" fill="none" stroke="${C.ink}" stroke-width="2"/>`;
    b += text(P(...N)[0] + 12, P(...N)[1] - 10, '镇中心（规划节点）', 'font-size="13" fill="#23282b"');
  }
  return b;
}

function stubOption(x0, y0, w, h) {
  let b = panel(x0, y0, w, h, '备选：镇上做 T 字路口，直行方向留一段 60 m 短路');
  const s = 1.45, P = (x, z) => [x0 + w * 0.55 + x * s, y0 + 250 + z * s], N = [0, 0];
  b += `<rect x="${x0 + 14}" y="${y0 + 50}" width="${w - 28}" height="${h - 64}" fill="${C.turf}"/>`;
  // leg A continues 60 m past the node as a stub; leg B comes in square to it (re-aligned like J1)
  const A0 = add(N, D1, -L), stub = add(N, D1, 60), side = [-D1[1], D1[0]];
  b += road([A0, stub], P, s);
  const Bm = add(N, side, EDGE + 25), B1 = add(N, D2, L);
  b += road([add(N, side, EDGE), Bm, add(Bm, D2, 40), B1], P, s);
  b += poly([P(...add(stub, side, -EDGE)), P(...add(stub, side, EDGE)), P(...add(add(stub, side, EDGE), D1, 1.2)), P(...add(add(stub, side, -EDGE), D1, 1.2))], `fill="#c9533f"`);
  const sp = P(...add(add(N, side, EDGE + 14), D1, EDGE + 9));
  b += stopSign(sp[0], sp[1], 9);
  b += leader(P(...add(stub, D1, -10)), add(P(...add(stub, D1, -10)), [40, -60]), '短路尽头（以后接镇里的街道）', 'start');
  b += leader(P(...Bm), add(P(...Bm), [-90, 70]), '支路垂直接入，停车让行（J1 做法）', 'end');
  b += text(x0 + 28, y0 + h - 70, '好处：以后建城镇（C3.4）时，这里直接就是镇上的路口。', 'font-size="13.5" fill="#23282b"');
  b += text(x0 + 28, y0 + h - 48, '坏处：现在镇还不存在，60 m 短路是条断头路；州道在 STOP 牌处转弯。', 'font-size="13.5" fill="#23282b"');
  b += text(x0 + 28, y0 + h - 26, '转角超过 120°（掉头弯）时只能用这个做法。', 'font-size="13.5" fill="#23282b"');
  return b;
}

function profile(x0, y0, w, h) {
  let b = panel(x0, y0, w, h, '纵断面重新拉坡（pass_village 两头高差 11.9 m；示意，竖向放大 15 倍）');
  const sx = (w - 120) / 620, sy = 15 * sx, X = d => x0 + 60 + (d + 310) * sx, Y = hh => y0 + h - 200 - hh * sy;
  // before: leg A ends 11.9 m above leg B's start, falling 2 % towards the town; leg B rises 1.5 % away from it
  const hA = d => 11.9 - 0.02 * (d + 81), hB = d => 0.015 * (d - 81), legA = [], legB = [];
  for (let d = -310; d <= -81; d += 2) legA.push([X(d), Y(hA(d))]);
  for (let d = 81; d <= 310; d += 2) legB.push([X(d), Y(hB(d))]);
  b += `<polyline points="${pts(legA)}" fill="none" stroke="${C.faint}" stroke-width="4"/>`;
  b += `<polyline points="${pts(legB)}" fill="none" stroke="${C.faint}" stroke-width="4"/>`;
  // after: the legs kept beyond 230 m, a Hermite blend between (grade -2 % in, +1.5 % out), max about -4.3 %
  const a = -231, z = 231, ya = hA(a), yz = hB(z), L = z - a, after = [];
  for (let d = -310; d <= 310; d += 2) {
    let hh;
    if (d <= a) hh = hA(d); else if (d >= z) hh = hB(d);
    else {
      const t = (d - a) / L, t2 = t * t, t3 = t2 * t;
      hh = (2 * t3 - 3 * t2 + 1) * ya + (t3 - 2 * t2 + t) * L * -0.02 + (-2 * t3 + 3 * t2) * yz + (t3 - t2) * L * 0.015;
    }
    after.push([X(d), Y(hh)]);
  }
  b += `<polyline points="${pts(after)}" fill="none" stroke="${C.asphalt}" stroke-width="5"/>`;
  for (const d of [-81, 81]) b += line([X(d), Y(0) + 40], [X(d), Y(16)], `stroke="${C.dim}" stroke-width="1" stroke-dasharray="4 4"`);
  b += dimH(X(-81), X(81), Y(0) + 52, '转角 + 两头截断段', null);
  b += leader([X(150), Y(hB(150))], [X(190), Y(hB(150)) + 50], '灰：现在两头的纵断面（中间断开）', 'start');
  b += leader(after[155], add(after[155], [60, -90]), '黑：重新拉坡，州道 ≤ 5 %，山区 ≤ 7 %', 'start');
  b += text(x0 + 16, y0 + h - 70, '高差摊到转角两边各约 300 m 内；摊不下（超过纵坡上限）时不连，退回现在的截断，并在报告里列出来。', 'font-size="14" fill="#4a5256"');
  b += text(x0 + 16, y0 + h - 46, '桥、隧道、立交范围内不改纵断面：拉坡区遇到它们就缩短，缩短后摊不下同样退回截断。', 'font-size="14" fill="#4a5256"');
  b += text(x0 + 16, y0 + h - 22, '路基（填、挖）沿用现在的道路做法；转角那段的地形照常整平。', 'font-size="14" fill="#4a5256"');
  return b;
}

const CASES = [
  ['1', 'pass_village', 'sh_mountain 两段', '城镇转角', '99°', '11.9 m', 'R 80 转角 + 拉坡'],
  ['2', 'shore_town', 'sh_gulf 两段', '城镇转角', '90°', '0.0', 'R 80 转角'],
  ['3', 'hill_village', 'sh_hills 两段', '城镇转角', '107°', '0.1', 'R 80 转角'],
  ['4', 'bayou_village', 'sh_gulf 两段', '城镇转角', '56°', '0.1', 'R 80 转角'],
  ['5', 'mill_town', 'sh_farm_north → sh_lakes', '两条路在镇上首尾相接', '47°', '0.4', 'R 80 转角'],
  ['6', 'farm_town_south', 'sh_farm_belt → sh_gulf', 'T 离对方路尾只有 87 m', '80°', '0.1', '按城镇转角'],
  ['7', 'farm_town_north', 'sh_mountain → sh_farm_north', '首尾相接，几乎直行', '9°', '0.1', '直接连通'],
  ['8', '(sh_mountain s 2500)', 'sh_farm_belt 起点', 'T 接在中段', '66°', '0.2', 'T 字 + 斜交改直'],
  ['9', '(sh_lakes s 4683)', 'sh_hills 终点', 'T 接在中段', '84°', '0.1', 'T 字'],
];

function table(x0, y0, w, h) {
  let b = panel(x0, y0, w, h, '种子 4 的州道—州道相接（离线普查，共 9 处）');
  const cols = [34, 150, 200, 190, 50, 64, 160], heads = ['#', '地点', '路', '情况', '角度', '高差', '做法'];
  let y = y0 + 66;
  const row = (cells, bold) => {
    let x = x0 + 16, out = '';
    cells.forEach((c, i) => {
      out += text(x, y, c, `font-size="13.5" ${bold ? 'font-weight="700"' : ''} fill="#23282b"`);
      x += cols[i];
    });
    return out;
  };
  b += row(heads, true);
  b += line([x0 + 12, y + 8], [x0 + w - 12, y + 8], 'stroke="#9aa3a6" stroke-width="1"');
  y += 32;
  for (const c of CASES) { b += row(c, false); y += 30; }
  y += 6;
  const foot = [
    '其他相接不在第一组：州道接州际 8 处（第二组，菱形立交）；州际在 county_seat、valley_town 两处折角（第三组）；sh_range_front 有一段贴着州际走（第二组）。',
    '另外 4 个种子也跑了普查，情况同类：州道之间从不交叉成十字，只有 T 和城镇转角。十字路口等县道和城镇时再做。',
  ];
  for (const s of foot) for (const l of wrap(s, 50)) { b += text(x0 + 16, y, l, 'font-size="13" fill="#4a5256"'); y += 20; }
  return b;
}

export function cornerSheet() {
  const W = 2000, H = 1560;
  let body = beforeAfter(40, 60, 1180, 700);
  body += stubOption(1240, 60, 720, 700);
  body += profile(40, 780, 1000, 690);
  body += table(1060, 780, 900, 690);
  return sheet(W, H, '城镇转角：R 80 连通、T 字备选、纵断面拉坡、种子 4 普查', 'J2', body);
}
