// T2: inside an R4 bore, driver's view on the right lane.
import { R4, solve, surface, arch, archY } from './geom.mjs';
import { camera, Scene, mix, scale } from './proj.mjs';
import { f, pts, text, mono, sheet, bubble } from './svg.mjs';

const ZEND = 330;
const FOG = '#3d3c37';
const BASE = { road: '#3c3f42', barrier: '#cfcec6', walk: '#b3b2aa', panel: '#ece7d6', arch: '#6f7578' };

function slices() {
  const out = []; let z = 2.2;
  while (z < ZEND) { const dz = z < 30 ? 1.5 : z < 90 ? 3 : 6; out.push([z, Math.min(ZEND, z + dz)]); z += dz; }
  return out;
}
const fog = (c, z) => mix(c, FOG, 1 - Math.exp(-z / 260));

export function interiorSheet() {
  const g = solve(R4);
  const IW = 1400, IH = 860, OX = 28, OY = 28;
  const cam = camera({ pos: [7.825, 1.75, 0], yaw: -0.06, pitch: -0.035, fov: 62, w: IW, h: IH, ox: OX, oy: OY });
  const sc = new Scene(cam);
  const segs = surface(g);
  const S = slices();
  // shell
  for (const [z0, z1] of S) {
    const zm = (z0 + z1) / 2;
    for (const [x0, y0, x1, y1, m] of segs) {
      const ym = (y0 + y1) / 2;
      let k = 1;
      if (m === 'panel' || m === 'arch') k = 0.86 + 0.3 * Math.exp(-((ym - 5.5) ** 2) / 2.2);
      if (m === 'walk') k = 0.95;
      const col = fog(scale(BASE[m], k), zm);
      sc.add([[x0, y0, z0], [x1, y1, z0], [x1, y1, z1], [x0, y0, z1]], col);
    }
    // road
    const k = 0.92 + 0.12 * Math.sin(zm * 0.9) * 0.2;
    sc.add([[g.curbL + 0.25, 0, z0], [g.curbR - 0.25, 0, z0], [g.curbR - 0.25, 0, z1], [g.curbL + 0.25, 0, z1]], fog(scale(BASE.road, k), zm));
  }
  // lane markings: yellow left edge, white solid lane line (no lane change in the tunnel), white right edge
  const marks = [[g.laneL, '#d7ad2c'], [g.laneL + g.lanes[0], '#e9e9e4'], [g.laneR, '#e9e9e4']];
  for (const [z0, z1] of S) for (const [x, c] of marks)
    sc.add([[x - 0.075, 0.003, z0], [x + 0.075, 0.003, z0], [x + 0.075, 0.003, z1], [x - 0.075, 0.003, z1]], fog(c, (z0 + z1) / 2), { layer: 0.5 });
  // wall panel joints (left and right walls, panels 2.4 m long), and the horizontal joint
  for (let z = 12; z < 120; z += 2.4) {
    for (const side of [0, 1]) {
      const prof = segs.filter(s => s[4] === 'panel' && (side === 0 ? s[0] < g.xa : s[0] > g.xa));
      const p3 = [];
      for (const s of prof) { p3.push([s[0] + (side ? -0.01 : 0.01), s[1], z]); p3.push([s[2] + (side ? -0.01 : 0.01), s[3], z]); }
      sc.line(p3, fog('#c4bda8', z), 0.018, { layer: 0.5 });
    }
  }
  for (const side of [0, 1]) for (const yj of [2.65]) {
    const x = side ? g.W - 0.01 : 0.01;
    const zs = []; for (let z = 3; z < 200; z += 6) zs.push(z);
    for (let i = 0; i + 1 < zs.length; i++) sc.line([[x, yj, zs[i]], [x, yj, zs[i + 1]]], fog('#c4bda8', zs[i]), 0.018, { layer: 0.5 });
  }
  // handrail on the egress walk
  for (let z = 3; z < 200; z += 6) sc.line([[0.08, g.walkH + 1.0, z], [0.08, g.walkH + 1.0, z + 6]], fog('#8a8f91', z), 0.05, { layer: 0.5 });
  // luminaires: fixtures 1.4 m long every 4 m on both sides at about 5.75 m
  const a1 = arch(g, 0, 1);
  const at = (side, y) => { const half = a1.filter(p => side ? p[0] > g.xa : p[0] < g.xa); let b = half[0]; for (const p of half) if (Math.abs(p[1] - y) < Math.abs(b[1] - y)) b = p; return b; };
  const lights = [];
  for (const side of [0, 1]) {
    const p = at(side, 5.6), q = at(side, 5.95);
    for (let z = 11; z < ZEND; z += 4) {
      const n = side ? -0.04 : 0.04;
      sc.add([[p[0] + n, p[1], z], [q[0] + n, q[1], z], [q[0] + n, q[1], z + 1.4], [p[0] + n, p[1], z + 1.4]], mix('#fffbe8', '#d8d2b8', Math.min(0.6, z / 500)), { layer: 0.5 });
      if (z < 120) lights.push([(p[0] + q[0]) / 2, (p[1] + q[1]) / 2, z + 0.7]);
    }
  }
  // cross passage door on the egress wall, EXIT sign over it
  const door = (z) => {
    sc.add([[0.015, g.walkH, z - 1.1], [0.015, g.walkH, z + 1.1], [0.015, 3.1, z + 1.1], [0.015, 3.1, z - 1.1]], fog('#bdbcb4', z), { layer: 0.5, bias: -2 });
    sc.add([[0.02, g.walkH, z - 0.85], [0.02, g.walkH, z + 0.85], [0.02, 2.95, z + 0.85], [0.02, 2.95, z - 0.85]], fog('#2f7a4c', z), { layer: 0.5, bias: 3 });
    sc.line([[0.03, g.walkH, z], [0.03, 2.95, z]], fog('#1f4f33', z), 0.03, { layer: 0.5, bias: 4 });
    sc.add([[0.03, 3.25, z - 0.55], [0.03, 3.25, z + 0.55], [0.12, 3.6, z + 0.55], [0.12, 3.6, z - 0.55]], '#36d17a', { layer: 0.5, bias: 4 });
  };
  door(58); door(308);
  // emergency cabinets (SOS phone, extinguishers, hose valve) in shallow niches, every ~83 m, egress side
  const cabinet = (z) => {
    sc.add([[0.012, g.walkH + 0.1, z - 0.9], [0.012, g.walkH + 0.1, z + 0.9], [0.012, 2.55, z + 0.9], [0.012, 2.55, z - 0.9]], fog('#5a5c5b', z), { layer: 0.5, bias: -2 });
    sc.add([[0.02, g.walkH + 0.25, z - 0.75], [0.02, g.walkH + 0.25, z + 0.05], [0.02, 2.35, z + 0.05], [0.02, 2.35, z - 0.75]], fog('#f2f0ea', z), { layer: 0.5, bias: 3 });
    sc.add([[0.02, g.walkH + 0.25, z + 0.12], [0.02, g.walkH + 0.25, z + 0.75], [0.02, 2.35, z + 0.75], [0.02, 2.35, z + 0.12]], fog('#c0392b', z), { layer: 0.5, bias: 3 });
    sc.add([[0.03, 2.7, z - 0.3], [0.03, 2.7, z + 0.3], [0.03, 3.2, z + 0.3], [0.03, 3.2, z - 0.3]], fog('#2a62b8', z), { layer: 0.5, bias: 3 });
  };
  cabinet(24); cabinet(141.3); cabinet(224.6);
  // low-level egress distance signs every 25 m
  for (let z = 34; z < 300; z += 25) if (Math.abs(z - 58) > 3 && Math.abs(z - 24) > 3) sc.add([[0.02, 1.2, z], [0.02, 1.2, z + 0.7], [0.02, 1.48, z + 0.7], [0.02, 1.48, z]], '#3cc777', { layer: 0.5, bias: 3 });
  // CCTV on the right upper wall
  const cctv = (z) => { const p = at(1, 5.0); sc.add([[p[0] - 0.05, p[1], z], [p[0] - 0.55, p[1] - 0.1, z], [p[0] - 0.55, p[1] - 0.35, z + 0.5], [p[0] - 0.05, p[1] - 0.25, z + 0.5]], fog('#d9d9d4', z), { layer: 1 }); };
  cctv(26); cctv(151);
  // interior objects (always in front of the convex shell): jet fans, lane control signals
  const fan = (cx, z0, len) => {
    const top = archY(g, cx), cy = top - 0.95, r = 0.6, n = 18;
    for (const hx of [-0.3, 0.3]) for (const hz of [0.8, len - 0.8]) sc.add([[cx + hx - 0.04, top, z0 + hz], [cx + hx + 0.04, top, z0 + hz], [cx + hx + 0.04, cy, z0 + hz], [cx + hx - 0.04, cy, z0 + hz]], fog('#5d6366', z0), { layer: 1 });
    for (let i = 0; i < n; i++) {
      const a0 = i * 2 * Math.PI / n, a1 = (i + 1) * 2 * Math.PI / n, am = (a0 + a1) / 2;
      const k = 0.55 + 0.45 * Math.max(0, -Math.sin(am)) + 0.15 * Math.cos(am) * (cx < g.xa ? 1 : -1);
      const P = (a, z) => [cx + r * Math.cos(a), cy + r * Math.sin(a), z];
      sc.add([P(a0, z0), P(a1, z0), P(a1, z0 + len), P(a0, z0 + len)], fog(scale('#a3aaae', k), z0), { layer: 1 });
    }
    const cap = []; for (let i = 0; i < n; i++) cap.push([cx + r * Math.cos(i * 2 * Math.PI / n), cy + r * Math.sin(i * 2 * Math.PI / n), z0]);
    sc.add(cap, fog('#7f878b', z0), { layer: 1, bias: 0.3 });
    const hub = cap.map(p => [cx + (p[0] - cx) * 0.62, cy + (p[1] - cy) * 0.62, z0]);
    sc.add(hub, fog('#2b2f31', z0), { layer: 1, bias: 0.4 });
  };
  for (const z of [34, 184]) { fan(g.xa - 2.3, z, 5.2); fan(g.xa + 2.3, z, 5.2); }
  const lcs = (z) => {
    let x = g.laneL;
    for (const w of g.lanes) {
      const cx = x + w / 2; x += w;
      const top = archY(g, cx), b = g.clear + 0.25;
      sc.add([[cx - 0.03, top, z + 0.15], [cx + 0.03, top, z + 0.15], [cx + 0.03, b + 0.9, z + 0.15], [cx - 0.03, b + 0.9, z + 0.15]], fog('#55595b', z), { layer: 1 });
      sc.add([[cx - 0.45, b, z], [cx + 0.45, b, z], [cx + 0.45, b + 0.9, z], [cx - 0.45, b + 0.9, z]], fog('#1d1f20', z), { layer: 1 });
      sc.add([[cx - 0.45, b + 0.9, z], [cx + 0.45, b + 0.9, z], [cx + 0.45, b + 0.9, z + 0.3], [cx - 0.45, b + 0.9, z + 0.3]], fog('#3a3d3f', z), { layer: 1 });
      sc.line([[cx - 0.25, b + 0.62, z - 0.01], [cx, b + 0.25, z - 0.01], [cx + 0.25, b + 0.62, z - 0.01]], '#4fe07a', 0.07, { layer: 1, bias: 0.5 });
      sc.line([[cx, b + 0.75, z - 0.01], [cx, b + 0.3, z - 0.01]], '#4fe07a', 0.07, { layer: 1, bias: 0.5 });
    }
  };
  lcs(48); lcs(198);
  // exit portal far away
  const end = [[g.curbL + 0.25, 0, ZEND], ...segs.map(s => [s[2], s[3], ZEND])];
  const endP = end.map(p => cam.proj(p));
  let body = `<defs><clipPath id="img"><rect x="${OX}" y="${OY}" width="${IW}" height="${IH}"/></clipPath>
<filter id="glow" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="6"/></filter>
<radialGradient id="exitg"><stop offset="0" stop-color="#ffffff"/><stop offset="1" stop-color="#e9efe9"/></radialGradient></defs>`;
  body += `<g clip-path="url(#img)"><rect x="${OX}" y="${OY}" width="${IW}" height="${IH}" fill="#2b2a27"/>`;
  body += `<polygon points="${pts(endP)}" fill="url(#exitg)"/>`;
  body += `<polygon points="${pts(endP)}" fill="#fffef6" filter="url(#glow)" opacity="0.9"/>`;
  body += sc.render();
  // soft glow over the near fixtures
  for (const l of lights) { const p = cam.proj(l); if (p[2] > 1) body += `<circle cx="${f(p[0])}" cy="${f(p[1])}" r="${f(Math.min(40, 260 / p[2]))}" fill="#fff6d0" opacity="0.18" filter="url(#glow)"/>`; }
  body += `</g><rect x="${OX}" y="${OY}" width="${IW}" height="${IH}" fill="none" stroke="#23282b" stroke-width="1.5"/>`;
  // callouts
  const call = [
    [1, cam.proj([g.xa - 2.3, archY(g, g.xa - 2.3) - 0.95, 34])], [2, cam.proj([at(1, 5.75)[0], 5.75, 14])],
    [3, cam.proj([g.laneL + g.lanes[0] + g.lanes[1] / 2 + 0.45, g.clear + 0.7, 48])], [4, cam.proj([0, 2.2, 58])],
    [5, cam.proj([0, 1.6, 23.6])], [6, cam.proj([0, 1.35, 84.35])], [7, cam.proj([at(1, 5)[0] - 0.3, 4.75, 26])],
    [8, cam.proj([0.4, g.walkH, 20])], [9, cam.proj([0, 3.6, 18])], [10, cam.proj([g.laneL + g.lanes[0], 0, 24])],
    [11, cam.proj([g.xa, archY(g, g.xa), 30])],
  ];
  const shift = { 1: [-34, 30], 2: [24, 22], 3: [34, 20], 4: [30, -28], 5: [-30, -36], 6: [24, 22], 7: [-34, -10], 8: [36, 24], 9: [28, -18], 10: [-30, 20], 11: [0, -26] };
  for (const [n, p] of call) { const d = shift[n] || [0, 0]; body += `<line x1="${f(p[0])}" y1="${f(p[1])}" x2="${f(p[0] + d[0])}" y2="${f(p[1] + d[1])}" stroke="#fff" stroke-width="1.5"/>` + bubble(p[0] + d[0], p[1] + d[1], n); }
  const legend = [
    ['射流风机（成对）', ['直径 1.2 m、长 5.2 m，带消声段；', '风机段每 150 m 一组，长于 300 m 才有']],
    ['LED 隧道灯', ['两侧连续；入口段加密（见 T4），', '白天入口比洞内亮得多']],
    ['车道控制灯', ['每条车道一个：绿 ↓ 通行 / 红 ✕ 关闭；', '洞口和洞内每 250 m']],
    ['横通道防火门', ['通往另一孔的避难通道，每 250 m 一处；', '门上绿色 EXIT 灯箱']],
    ['紧急壁龛', ['SOS 电话 + 灭火器 + 消火栓阀门；', '约每 83 m，在人行道一侧']],
    ['低位疏散指示', ['离地低，烟里也看得见：', '“← EXIT 65 m”，每 25 m']],
    ['监控摄像机', ['对侧上墙，约每 125 m，全线覆盖']],
    ['疏散人行道', ['高 0.80、宽 1.20：F 型防撞墙顶面兼人行道，', '靠墙有扶手']],
    ['浅色墙面板', ['搪瓷钢板到 4.5 m 高，以上拱顶深灰；', '面板 2.4 m 一块']],
    ['车道线', ['洞内实线，不准变道；', '左边缘黄线，右边缘白线']],
    ['拱顶', ['三心圆马蹄形：起拱 3.00 m、拱顶 8.54 m']],
  ];
  const lx = OX + IW + 28;
  body += text(lx, 70, '洞内（州际 R4 单向孔，右车道驾驶员视角）', 'font-size="19" font-weight="700" fill="#23282b"');
  body += text(lx, 96, '视点高 1.75 m · 往前 330 m 是出口洞门', 'font-size="13.5" fill="#6b7376"');
  legend.forEach(([t, d], i) => {
    const y = 140 + i * 66;
    body += bubble(lx + 12, y, i + 1);
    body += text(lx + 34, y + 5, t, 'font-size="16" font-weight="700" fill="#23282b"');
    d.forEach((ln, j) => { body += text(lx + 34, y + 27 + j * 19, ln, 'font-size="13.5" fill="#4a5154"'); });
  });
  return sheet(2000, 940, '洞内透视：设备布置', 'T2', body);
}
