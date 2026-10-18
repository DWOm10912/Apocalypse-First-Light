// T5: equipment along a 1 km R4 bore, rules by length, the new mesh list and phases.
import { f, line, text, mono, sheet } from './svg.mjs';

export function layoutSheet() {
  const W = 2000, H = 1420;
  let b = '';
  const L = 1000, X0 = 260, SX = 1.30, from = -220;
  const X = s => X0 + (s - from) * SX;
  b += text(60, 70, '纵向布置：一条 1 km 州际隧道（单孔，进洞方向从左到右）', 'font-size="20" font-weight="700" fill="#23282b"');
  // axis
  const ay = 470;
  b += line([X(from), ay], [X(L + 60), ay], 'stroke="#23282b" stroke-width="1.4"');
  for (let s = -200; s <= L; s += 100) { b += line([X(s), ay - 5], [X(s), ay + 5], 'stroke="#23282b" stroke-width="1.2"'); b += mono(X(s), ay + 22, (s >= 0 ? '' : '−') + Math.abs(s) + ' m', 'font-size="12" fill="#23282b" text-anchor="middle"'); }
  // tunnel body
  b += `<rect x="${f(X(0))}" y="${ay - 330}" width="${f(L * SX)}" height="330" fill="#f1efe9" stroke="#23282b" stroke-width="1.6"/>`;
  b += text(X(0) + 6, ay - 312, '隧道内', 'font-size="12.5" fill="#6b7376"');
  b += text(X(from) + 6, ay - 312, '引道', 'font-size="12.5" fill="#6b7376"');
  // rows
  const rows = [
    ['照明分段', 405], ['灯具', 370], ['射流风机（成对）', 330], ['横通道', 290], ['紧急壁龛', 255], ['车道控制灯', 220], ['监控', 185], ['低位疏散指示', 155], ['洞口交通设施', 110],
  ];
  for (const [name, y] of rows) { b += text(X0 - 14, ay - (ay - y) + 4, name, 'font-size="13.5" fill="#23282b" text-anchor="end"'); b += line([X(from), y], [X(L + 60), y], 'stroke="#d8dcdc" stroke-width="0.8"'); }
  // lighting zones as a step chart of relative daytime level
  const zones = [[-160, 0, '接近段', 0], [0, 80, '入口段', 1.0], [80, 230, '过渡段', 0.45], [230, 940, '基本段', 0.12], [940, 1000, '出口段', 0.3]];
  for (const [a, c, n, lv] of zones) {
    const h = 26 * lv;
    if (lv > 0) b += `<rect x="${f(X(a))}" y="${f(420 - h)}" width="${f((c - a) * SX)}" height="${f(h)}" fill="#f2d67a" stroke="#b8932e" stroke-width="0.8"/>`;
    b += line([X(a), 380], [X(a), 425], 'stroke="#b8932e" stroke-width="1" stroke-dasharray="3 2"');
    b += text((X(a) + X(c)) / 2, 438, n, 'font-size="12" fill="#6b5a1e" text-anchor="middle"');
  }
  // luminaires: dense in the entrance zone
  for (let s = 2; s < L; s += s < 80 ? 2.2 : s < 230 ? 4 : 8) b += line([X(s), 362], [X(s), 378], 'stroke="#c99a1e" stroke-width="0.9"');
  // fans every 150 m
  for (let s = 150; s < L - 60; s += 150) b += `<g><circle cx="${f(X(s) - 5)}" cy="330" r="6" fill="#8b9296" stroke="#23282b"/><circle cx="${f(X(s) + 9)}" cy="330" r="6" fill="#8b9296" stroke="#23282b"/></g>`;
  // cross passages every 250 m
  for (let s = 250; s < L; s += 250) b += `<rect x="${f(X(s) - 7)}" y="280" width="14" height="20" fill="#2f7a4c" stroke="#23282b"/>` + mono(X(s), 274, s + ' m', 'font-size="11" fill="#2f7a4c" text-anchor="middle"');
  // cabinets every ~83 m
  for (let i = 1; i * 83.33 < L - 5; i++) b += `<rect x="${f(X(i * 83.33) - 5)}" y="247" width="10" height="16" fill="#c0392b" stroke="#23282b"/>`;
  // lane control signals at the portal and every 250 m
  for (let s = 0; s < L; s += 250) b += `<rect x="${f(X(s + 2) - 7)}" y="212" width="14" height="14" fill="#1d1f20"/><path d="M ${f(X(s + 2) - 4)} 216 L ${f(X(s + 2))} 222 L ${f(X(s + 2) + 4)} 216" stroke="#4fe07a" stroke-width="2" fill="none"/>`;
  // cctv every 125 m
  for (let s = 60; s < L; s += 125) b += `<path d="M ${f(X(s) - 6)} 180 L ${f(X(s) + 6)} 180 L ${f(X(s) + 6)} 190 L ${f(X(s) - 6)} 190 Z" fill="#d9d9d4" stroke="#23282b"/>`;
  // egress signs every 25 m
  for (let s = 25; s < L; s += 25) b += line([X(s), 149], [X(s), 161], 'stroke="#3cc777" stroke-width="2"');
  // approach equipment
  const ap = [[-200, '情报板门架'], [-55, '关闭栏杆'], [-47, '开灯牌'], [-40, '信号']];
  ap.forEach(([s]) => { b += `<circle cx="${f(X(s))}" cy="110" r="5" fill="#8e3a32"/>`; });
  b += text(X(-200) + 9, 100, '情报板门架 200 m', 'font-size="12" fill="#23282b"');
  b += text(X(-36) + 2, 100, '关闭栏杆 55 m · 开灯牌 47 m · 信号 40 m', 'font-size="12" fill="#23282b"');
  b += text(X(L) + 6, 100, '出口洞门 →', 'font-size="12" fill="#23282b"');
  b += text(X(from), 530, '另一孔（对向）布置相同、方向相反；横通道两孔共用。照明分段的黄色高度是白天相对亮度（入口段最高，夜间各段一样）。', 'font-size="13" fill="#4a5154"');

  // ---- rules by length ----
  let y = 600;
  b += line([40, 570], [W - 40, 570], 'stroke="#c5cccd" stroke-width="1"');
  b += text(60, y, '按长度配设备（依据 NFPA 502 / FHWA 隧道手册的门槛）', 'font-size="18" font-weight="700" fill="#23282b"');
  const rules = [
    ['短于 90 m', '照明（基本 + 入口加强）、限高牌、名牌；不设风机'],
    ['90 – 240 m', '加洞口交通信号、关闭栏杆（洞外能拦住车）'],
    ['240 – 300 m', '加洞内车道控制灯、监控、紧急壁龛、低位疏散指示'],
    ['300 m 以上', '加横通道（双孔，每 250 m）和射流风机（每 150 m 一组）；'],
    ['', '州道单孔：疏散出口方式待定（平行通道或通往地面）'],
  ];
  rules.forEach(([a, c], i) => { const yy = y + 36 + i * 28; b += mono(70, yy, a, 'font-size="14" font-weight="700" fill="#8e3a32"'); b += text(200, yy, c, 'font-size="14" fill="#23282b"'); });
  y += 36 + rules.length * 28 + 30;
  b += text(60, y, '分期（每期一次实机验收）', 'font-size="18" font-weight="700" fill="#23282b"');
  const phases = [
    ['T-A', '能走的隧道', ['规划不再整段丢掉有隧道的路；挖洞、隐形碰撞、衬砌、人行道、', '洞门、挡墙、洞顶仰坡；基本照明（洞内有亮度）；R4 双孔 + R2 单孔']],
    ['T-B', '洞内设备', ['灯具模型、射流风机、紧急壁龛、横通道和防火门、', '疏散指示、车道控制灯、监控']],
    ['T-C', '洞口交通设施', ['交通信号、关闭栏杆、名牌和限高牌、开灯牌、情报板门架', '（可以和以后的路边标志一起做）']],
  ];
  phases.forEach(([c, t, d], i) => {
    const yy = y + 38 + i * 70;
    b += mono(70, yy, c, 'font-size="15" font-weight="700" fill="#2c6174"') + text(130, yy, t, 'font-size="15" font-weight="700" fill="#23282b"');
    d.forEach((ln, j) => { b += text(130, yy + 22 + j * 19, ln, 'font-size="13.5" fill="#4a5154"'); });
  });
  y += 38 + phases.length * 70 + 10;
  b += text(60, y, '不需要新 Mesh 的部分：洞顶仰坡和岩石边坡走地形平滑 + 岩石材质；路面沿用现有沥青网格。', 'font-size="13.5" fill="#4a5154"');

  // ---- mesh list ----
  const mx = 960; let my = 600;
  b += line([930, 590], [930, H - 80], 'stroke="#c5cccd" stroke-width="1"');
  b += text(mx, my, '需要的新 Mesh 模型', 'font-size="18" font-weight="700" fill="#23282b"');
  const groups = [
    ['连续结构：道路自己的分块网格（同桥的 BridgeMesh 做法，新加 TunnelMesh）', '#2c6174', [
      ['衬砌', '侧墙 + 三心圆拱顶 + 浅色面板带，跟着路线的弯和纵坡走', 'T-A'],
      ['人行道和防撞墙', 'F 型防撞墙、人行道顶面、扶手、缝隙式排水沟', 'T-A'],
      ['洞门', '端墙 + 洞口环框 + 压顶；宽高按两孔位置和地形算', 'T-A'],
      ['引道挡墙', '挡墙 + 防撞墙引线（斜坡收口）', 'T-A'],
      ['横通道', '连两孔的短通道（宽 4.2、高 4.7）和门洞', 'T-B'],
    ]],
    ['独立部件：Vextris 模型，按间距放进分块网格', '#8e3a32', [
      ['射流风机', '成对，带消声段和吊架', 'T-B'],
      ['隧道灯具', '一个模型，基本段稀、入口段密两排', 'T-B'],
      ['紧急壁龛柜', 'SOS 电话、灭火器、消火栓阀门', 'T-B'],
      ['防火门 + EXIT 灯箱', '横通道门，绿色，双向开', 'T-B'],
      ['低位疏散指示牌', '“← EXIT 65 m”，数字按位置生成', 'T-B'],
      ['车道控制灯', '绿 ↓ / 红 ✕ 两种状态', 'T-B'],
      ['监控摄像机', '上墙支架', 'T-B'],
      ['洞口交通信号', '悬臂杆 + 三色灯', 'T-C'],
      ['关闭栏杆', '立柱 + 红白横杆，竖起 / 放下', 'T-C'],
      ['标志牌', '名牌、限高牌、开灯牌、情报板门架', 'T-C'],
    ]],
  ];
  let n = 0; my += 34;
  for (const [title, col, items] of groups) {
    b += text(mx, my, title, `font-size="14.5" font-weight="700" fill="${col}"`);
    my += 12;
    for (const [name, desc, ph] of items) {
      n++; my += 30;
      b += mono(mx + 8, my, String(n).padStart(2, '0'), `font-size="13" fill="${col}" font-weight="700"`);
      b += text(mx + 40, my, name, 'font-size="14.5" font-weight="700" fill="#23282b"');
      b += text(mx + 210, my, desc, 'font-size="13.5" fill="#4a5154"');
      b += mono(W - 70, my, ph, 'font-size="13" fill="#2c6174" text-anchor="end"');
    }
    my += 34;
  }
  b += text(mx, my, '共 15 种：5 种连续结构 + 10 种独立部件。碰撞仍是看不见的方块，网格只管外观。', 'font-size="13.5" fill="#23282b"');
  return sheet(W, H, '纵向布置、按长度配设备、新 Mesh 清单和分期', 'T5', b);
}
