// T3: approach view of an R4 twin-bore portal.
import { g, XR, HW, HTOP, RING, WALL_LEN, wx, natural, wallTop, ground, opening, ringOuter, surface, arch } from './site.mjs';
import { camera, Scene, mix, scale } from './proj.mjs';
import { f, pts, text, sheet, bubble } from './svg.mjs';

const hash = (a, b) => { const s = Math.sin(a * 127.1 + b * 311.7) * 43758.5453; return s - Math.floor(s); };

function cellColor(p3, cut, x, z) {
  // light from the upper left / front
  const [a, b, c] = p3;
  const u = [b[0] - a[0], b[1] - a[1], b[2] - a[2]], v = [c[0] - a[0], c[1] - a[1], c[2] - a[2]];
  let n = [u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]];
  const len = Math.hypot(...n) || 1; n = n.map(q => q / len); if (n[1] < 0) n = n.map(q => -q);
  const L = [-0.35, 0.8, -0.48], ll = Math.hypot(...L);
  const k = 0.62 + 0.5 * Math.max(0, (n[0] * L[0] + n[1] * L[1] + n[2] * L[2]) / ll);
  const r = hash(Math.round(x), Math.round(z));
  const base = cut ? mix('#948f83', '#9b968a', r) : mix('#647a45', '#76894f', r);
  return scale(base, k);
}

export function portalSheet() {
  const IW = 1360, IH = 860, OX = 28, OY = 28;
  const cam = camera({ pos: [14.2, 2.3, -86], yaw: -0.118, pitch: 0.035, fov: 56, w: IW, h: IH, ox: OX, oy: OY });
  const sc = new Scene(cam);
  const haze = (c, d) => mix(c, '#c9d3d8', Math.min(0.55, Math.max(0, (d - 90) / 420)));
  const depth = p => cam.toCam(p)[2];

  // terrain behind the portal (layer 1) and beside the approach (layer 3)
  const cellsBehind = [], dx = 5;
  for (let z = 0; z < 175; z += z < 30 ? 2.5 : 5) {
    const dz = z < 30 ? 2.5 : 5;
    for (let x = -160; x < 160; x += dx) {
      const q = [[x, z], [x + dx, z], [x + dx, z + dz], [x, z + dz]].map(([a, b]) => [a, ground(a, b)[0], b]);
      const cut = ground(x + dx / 2, z + dz / 2)[1];
      const d = depth(q[0]);
      sc.add(q, haze(cellColor(q, cut, x, z), d), { layer: 1 });
      if (!cut && z > 10 && hash(x, z) > 0.8) cellsBehind.push([x + dx * hash(z, x), z + dz * hash(x + 1, z), 1]);
    }
  }
  for (let z = -84; z < 0; z += 3) {
    for (let x = HW; x < 160; x += x < 60 ? 2.5 : 5) {
      const ddx = x < 60 ? 2.5 : 5;
      for (const s of [1, -1]) {
        const xs = [x, x + ddx].map(v => v * s);
        const q = [[xs[0], z], [xs[1], z], [xs[1], z + 3], [xs[0], z + 3]].map(([a, b]) => [a, ground(a, b)[0], b]);
        const cut = ground((xs[0] + xs[1]) / 2, z + 1.5)[1];
        sc.add(q, haze(cellColor(q, cut, x * s, z), depth(q[0])), { layer: 3 });
        if (!cut && x > 40 && hash(x * s, z) > 0.84) cellsBehind.push([(x + ddx * hash(z, x)) * s, z + 3 * hash(x, z + 1), 3]);
      }
    }
  }
  // trees (conifers) on natural ground
  for (const [x, z, layer] of cellsBehind) {
    const y = ground(x, z)[0], h = 7 + 5 * hash(z, x), w = h * 0.22;
    const c = haze(mix('#2f4a2c', '#3e5a35', hash(x, z)), depth([x, y, z]));
    sc.add([[x - w, y + h * 0.18, z], [x + w, y + h * 0.18, z], [x, y + h, z]], c, { layer, bias: 0.5 });
    sc.add([[x - 0.25, y, z], [x + 0.25, y, z], [x + 0.25, y + h * 0.2, z], [x - 0.25, y + h * 0.2, z]], haze('#4a3a2c', depth([x, y, z])), { layer, bias: 0.4 });
  }

  // tube interiors seen through the openings (layer 2, drawn before the headwall face)
  const segs = surface(g);
  const mat = { road: '#3c3f42', barrier: '#cfcec6', walk: '#b3b2aa', panel: '#ece7d6', arch: '#6f7578' };
  for (const side of [1, -1]) {
    for (let z = 0; z < 90; z += z < 24 ? 2 : 6) {
      const z1 = z + (z < 24 ? 2 : 6), dark = 1 - Math.exp(-z / 30);
      for (const [x0, y0, x1, y1, m] of segs) {
        const col = mix(mat[m], '#191917', dark * 0.85);
        sc.add([[wx(x0, side), y0, z], [wx(x1, side), y1, z], [wx(x1, side), y1, z1], [wx(x0, side), y0, z1]], col, { layer: 2, bias: -1000 });
      }
      sc.add([[wx(g.curbL + 0.25, side), 0, z], [wx(g.curbR - 0.25, side), 0, z], [wx(g.curbR - 0.25, side), 0, z1], [wx(g.curbL + 0.25, side), 0, z1]], mix('#3c3f42', '#151514', dark), { layer: 2, bias: -1000 });
    }
    // threshold lighting: two dense rows of fixtures each side for the first 80 m
    const a1 = arch(g, 0, 1);
    const at = (s2, y) => { const half = a1.filter(p => s2 ? p[0] > g.xa : p[0] < g.xa); let b = half[0]; for (const p of half) if (Math.abs(p[1] - y) < Math.abs(b[1] - y)) b = p; return b; };
    for (const s2 of [0, 1]) for (const yy of [5.5, 6.3]) {
      const p = at(s2, yy);
      for (let z = 1.5; z < 86; z += 2.2) sc.add([[wx(p[0], side), p[1] - 0.12, z], [wx(p[0], side), p[1] + 0.12, z], [wx(p[0], side), p[1] + 0.12, z + 1.2], [wx(p[0], side), p[1] - 0.12, z + 1.2]], '#fffbe3', { layer: 2, bias: -999 });
    }
    sc.add(opening(side).map(([x, y]) => [x, y, 90]), '#121211', { layer: 2, bias: -998 + 0 });
  }
  let body = '';
  // sky
  body += `<defs><linearGradient id="sky" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#8fb3cf"/><stop offset="1" stop-color="#dbe6ea"/></linearGradient>
<clipPath id="img2"><rect x="${OX}" y="${OY}" width="${IW}" height="${IH}"/></clipPath></defs>`;
  body += `<g clip-path="url(#img2)"><rect x="${OX}" y="${OY}" width="${IW}" height="${IH}" fill="url(#sky)"/>`;
  // distant ridge
  const ridge = [];
  for (let x = -900; x <= 900; x += 30) ridge.push(cam.proj([x, 150 + 40 * Math.sin(x / 140) + 25 * Math.sin(x / 47), 700]));
  ridge.push(cam.proj([900, -50, 700]), cam.proj([-900, -50, 700]));
  body += `<polygon points="${pts(ridge)}" fill="#a9b9b8"/>`;
  // headwall, rings, coping and plates as raw svg in layer 2 after the interiors
  const P = (x, y, z = 0) => cam.proj([x, y, z]);
  const holePath = side => 'M ' + ringOuter(side).map(([x, y]) => { const p = P(x, y); return f(p[0]) + ' ' + f(p[1]); }).join(' L ') + ' Z';
  const openPath = side => 'M ' + opening(side).map(([x, y]) => { const p = P(x, y, -0.35); return f(p[0]) + ' ' + f(p[1]); }).join(' L ') + ' Z';
  const ringPath = side => 'M ' + ringOuter(side).map(([x, y]) => { const p = P(x, y, -0.35); return f(p[0]) + ' ' + f(p[1]); }).join(' L ') + ' Z';
  const rect = (x0, y0, x1, y1, z = 0) => [P(x0, y0, z), P(x1, y0, z), P(x1, y1, z), P(x0, y1, z)];
  const face = rect(-HW, 0, HW, HTOP);
  let portal = `<path d="M ${pts(face).split(' ').join(' L ')} Z ${holePath(1)} ${holePath(-1)}" fill="#c9c6bd" fill-rule="evenodd"/>`;
  // formwork lines
  portal += `<clipPath id="hwc"><path d="M ${pts(face).split(' ').join(' L ')} Z ${holePath(1)} ${holePath(-1)}" clip-rule="evenodd"/></clipPath><g clip-path="url(#hwc)">`;
  for (let y = 1.2; y < HTOP; y += 1.2) { const a = P(-HW, y), b = P(HW, y); portal += `<line x1="${f(a[0])}" y1="${f(a[1])}" x2="${f(b[0])}" y2="${f(b[1])}" stroke="#b5b2a8" stroke-width="0.8"/>`; }
  for (let x = -HW + 3; x < HW; x += 6) { const a = P(x, 0), b = P(x, HTOP); portal += `<line x1="${f(a[0])}" y1="${f(a[1])}" x2="${f(b[0])}" y2="${f(b[1])}" stroke="#bdbab0" stroke-width="0.6"/>`; }
  portal += `</g>`;
  for (const side of [1, -1]) portal += `<path d="${ringPath(side)} ${openPath(side)}" fill="#d9d6ce" fill-rule="evenodd" stroke="#a9a69c" stroke-width="0.8"/>`;
  // coping
  portal += `<polygon points="${pts([P(-HW - 0.3, HTOP, -0.4), P(HW + 0.3, HTOP, -0.4), P(HW + 0.3, HTOP + 0.55, -0.4), P(-HW - 0.3, HTOP + 0.55, -0.4)])}" fill="#d6d3ca" stroke="#a9a69c" stroke-width="0.8"/>`;
  portal += `<polygon points="${pts([P(-HW - 0.3, HTOP + 0.55, -0.4), P(HW + 0.3, HTOP + 0.55, -0.4), P(HW + 0.3, HTOP + 0.55, 0.6), P(-HW - 0.3, HTOP + 0.55, 0.6)])}" fill="#e3e0d8"/>`;
  // name plates and clearance plates (affine-mapped text)
  const plate = (cx, y, w, h, label, bg, fg, z = -0.45) => {
    const p00 = P(cx - w / 2, y, z), p10 = P(cx + w / 2, y, z), p01 = P(cx - w / 2, y + h, z), p11 = P(cx + w / 2, y + h, z);
    const W = 100, H = 100 * h / w;
    const m = [(p11[0] - p01[0]) / W, (p11[1] - p01[1]) / W, (p00[0] - p01[0]) / H, (p00[1] - p01[1]) / H, p01[0], p01[1]];
    return `<g transform="matrix(${m.map(f).join(' ')})"><rect width="${W}" height="${f(H)}" fill="${bg}"/>` +
      `<text x="${W / 2}" y="${f(H * 0.7)}" font-family="Arial Narrow, Arial, sans-serif" font-weight="700" font-size="${f(H * 0.62)}" fill="${fg}" text-anchor="middle" textLength="${W * 0.9}" lengthAdjust="spacingAndGlyphs">${label}</text></g>`;
  };
  for (const side of [1, -1]) {
    portal += plate(side * XR, 10.4, 12, 1.1, 'CEDAR PASS TUNNEL', '#3a3c3b', '#e6e2d6');
    portal += plate(side * XR, g.crown + RING + 0.15, 4.2, 0.55, 'CLEARANCE 5.1 m', '#f2f0e8', '#1e1f1f');
  }
  sc.raw(portal, -500, 2);

  // approach: median, road, ditches, retaining walls, barrier lead-ins (layer 3)
  for (let z = -84; z < 0; z += 3) {
    const z1 = z + 3, d = depth([0, 0, z]);
    for (const s of [1, -1]) {
      const X = v => v * s;
      sc.add([[X(9.5), 0, z], [X(22), 0, z], [X(22), 0, z1], [X(9.5), 0, z1]], haze('#3e4144', d), { layer: 3 });
      sc.add([[X(22), 0, z], [X(HW), 0, z], [X(HW), 0, z1], [X(22), 0, z1]], haze('#8c897f', d), { layer: 3 });
      for (const [v, c, wdt] of [[11, '#d8ae2e', 0.15], [14.75, '#ecece6', 0.15], [18.5, '#ecece6', 0.15]])
        sc.add([[X(v - wdt / 2), 0.004, z], [X(v + wdt / 2), 0.004, z], [X(v + wdt / 2), 0.004, z1], [X(v - wdt / 2), 0.004, z1]], haze(c, d), { layer: 3, bias: 0.3 });
    }
    sc.add([[-9.5, -0.05, z], [9.5, -0.05, z], [9.5, -0.05, z1], [-9.5, -0.05, z1]], haze(mix('#6f8550', '#5f7646', hash(z, 3)), d), { layer: 3 });
    sc.add([[-1.2, -0.04, z], [1.2, -0.04, z], [1.2, -0.04, z1], [-1.2, -0.04, z1]], haze('#566b3f', d), { layer: 3, bias: 0.2 });
  }
  for (let z = -WALL_LEN; z < 0; z += 2) {
    const z1 = z + 2;
    for (const s of [1, -1]) {
      const x = s * HW;
      sc.add([[x, 0, z], [x, wallTop(z), z], [x, wallTop(z1), z1], [x, 0, z1]], scale('#bcb9b0', s > 0 ? 0.86 : 1.02), { layer: 3, bias: -0.5 });
      sc.add([[x, wallTop(z), z], [x + s * 0.6, wallTop(z), z], [x + s * 0.6, wallTop(z1), z1], [x, wallTop(z1), z1]], '#d8d5cc', { layer: 3, bias: -0.4 });
    }
  }
  // barrier lead-ins out of the portal (tunnel safety walks end in sloped concrete barriers)
  for (const s of [1, -1]) for (const [toe, top, back] of [[10.25, 10.0, 9.4], [21.25, 21.5, 22.1]]) {
    for (let z = -16; z < 0; z += 2) {
      const h0 = z < -12 ? 0.8 * (z + 16) / 4 : 0.8, h1 = z + 2 < -12 ? 0.8 * (z + 18) / 4 : 0.8;
      sc.add([[s * toe, 0, z], [s * top, h0, z], [s * top, h1, z + 2], [s * toe, 0, z + 2]], '#d3d1c9', { layer: 3, bias: -0.6 });
      sc.add([[s * top, h0, z], [s * back, h0, z], [s * back, h1, z + 2], [s * top, h1, z + 2]], '#e2e0d8', { layer: 3, bias: -0.6 });
    }
  }
  // foreground: traffic signal on a mast arm, closure gate, headlight sign (layer 4)
  const pole = (x, z, h, w, col) => sc.add([[x - w, 0, z], [x + w, 0, z], [x + w, h, z], [x - w, h, z]], col, { layer: 4 });
  pole(23.6, -40, 7.0, 0.14, '#6c7275');
  sc.line([[23.6, 6.7, -40], [12.6, 6.7, -40]], '#6c7275', 0.18, { layer: 4 });
  for (const x of [13.0, 16.75]) {
    sc.add([[x - 0.2, 6.6, -40.1], [x + 0.2, 6.6, -40.1], [x + 0.2, 5.5, -40.1], [x - 0.2, 5.5, -40.1]], '#1f2224', { layer: 4, bias: 0.1 });
    [['#4a1a16', 6.42], ['#4a3a14', 6.05], ['#3fe07a', 5.68]].forEach(([c, y]) => sc.add([[x - 0.13, y - 0.13, -40.2], [x + 0.13, y - 0.13, -40.2], [x + 0.13, y + 0.13, -40.2], [x - 0.13, y + 0.13, -40.2]], c, { layer: 4, bias: 0.2 }));
  }
  pole(23.2, -56, 1.3, 0.25, '#d9d6cd');
  for (let i = 0; i < 6; i++) sc.add([[23.12, 1.3 + i * 0.75, -56.05], [23.28, 1.3 + i * 0.75, -56.05], [23.28, 2.05 + i * 0.75, -56.05], [23.12, 2.05 + i * 0.75, -56.05]], i % 2 ? '#f1efe8' : '#c0392b', { layer: 4, bias: 0.1 });
  pole(22.9, -47, 2.2, 0.06, '#6c7275'); pole(24.1, -47, 2.2, 0.06, '#6c7275');
  const sp00 = cam.proj([22.5, 2.2, -47]), sp10 = cam.proj([24.5, 2.2, -47]), sp01 = cam.proj([22.5, 3.6, -47]), sp11 = cam.proj([24.5, 3.6, -47]);
  const signSvg = `<g transform="matrix(${[(sp11[0] - sp01[0]) / 100, (sp11[1] - sp01[1]) / 100, (sp00[0] - sp01[0]) / 70, (sp00[1] - sp01[1]) / 70, sp01[0], sp01[1]].map(f).join(' ')})"><rect width="100" height="70" fill="#f4f3ee" stroke="#1d1e1e" stroke-width="3"/><text x="50" y="30" font-family="Arial Narrow, Arial" font-weight="700" font-size="21" text-anchor="middle" fill="#1d1e1e" textLength="62" lengthAdjust="spacingAndGlyphs">TURN ON</text><text x="50" y="56" font-family="Arial Narrow, Arial" font-weight="700" font-size="21" text-anchor="middle" fill="#1d1e1e" textLength="86" lengthAdjust="spacingAndGlyphs">HEADLIGHTS</text></g>`;
  sc.raw(signSvg, -100, 4.5);

  body += sc.render();
  body += `</g><rect x="${OX}" y="${OY}" width="${IW}" height="${IH}" fill="none" stroke="#23282b" stroke-width="1.5"/>`;
  // callouts
  const C = (n, p, d) => `<line x1="${f(p[0])}" y1="${f(p[1])}" x2="${f(p[0] + d[0])}" y2="${f(p[1] + d[1])}" stroke="#fff" stroke-width="1.5"/>` + bubble(p[0] + d[0], p[1] + d[1], n);
  body += C(1, P(-HW + 4, 5), [-10, -40]);
  body += C(2, P(wx(g.W + RING / 2, 1), 4.5, -0.35), [34, -16]);
  body += C(3, P(XR + 4.5, 10.9, -0.45), [30, -34]);
  body += C(4, P(4, HTOP + 4, 2.2), [0, -40]);
  body += C(5, P(HW, 3, -20), [-36, 44]);
  body += C(6, P(21.5, 0.8, -10), [-20, 36]);
  body += C(7, P(16.75, 6.05, -40), [44, 6]);
  body += C(8, P(23.2, 4.0, -56), [36, 0]);
  body += C(9, cam.proj([23.5, 3.6, -47]), [-30, -30]);
  body += C(10, P(wx(g.xa, 1), 4.5, 20), [-6, -44]);
  body += C(11, P(-2, 0, -40), [-30, 30]);
  const legend = [
    ['洞门端墙', ['现浇混凝土，带模板缝；两孔共用一道，', '宽 51 m、高 12.6 m，顶上压顶']],
    ['洞口环框', ['凸出端墙 0.35 m、宽 0.9 m 的环，', '沿拱和侧墙一圈']],
    ['隧道名牌 / 限高牌', ['“CEDAR PASS TUNNEL”（示例名）；', '洞口顶上 “CLEARANCE 5.1 m”']],
    ['洞顶仰坡', ['岩石按 2:1 开挖，喷混凝土或裸岩；', '上面接自然山坡和树']],
    ['引道挡墙', ['两侧各一道，从端墙高度往外降低，', '长约 40 m，墙后和更远处是 2:1 岩石边坡']],
    ['防撞墙引线', ['洞内人行道的防撞墙伸出洞口 16 m，', '末端斜坡收口']],
    ['洞口交通信号', ['悬臂杆，每条车道一个；平时绿灯，', '事故时红灯封洞（NFPA 502）']],
    ['关闭栏杆', ['洞口前约 55 m，平时竖起；', '封洞时放下拦住车道']],
    ['开灯提示牌', ['“TURN ON HEADLIGHTS”']],
    ['入口段加强照明', ['洞口里面前约 80 m 灯具加密两排，', '白天减轻“黑洞效应”']],
    ['中央分隔带', ['草皮 + 排水浅沟，一直到两孔之间', '的端墙']],
  ];
  const lx = OX + IW + 28;
  body += text(lx, 70, '洞口（州际 R4 双孔，进洞方向）', 'font-size="19" font-weight="700" fill="#23282b"');
  body += text(lx, 96, '视点在右幅车道，离洞口 86 m，高 2.3 m', 'font-size="13.5" fill="#6b7376"');
  legend.forEach(([t, d], i) => {
    const y = 140 + i * 66;
    body += bubble(lx + 12, y, i + 1);
    body += text(lx + 34, y + 5, t, 'font-size="16" font-weight="700" fill="#23282b"');
    d.forEach((ln, j) => { body += text(lx + 34, y + 27 + j * 19, ln, 'font-size="13.5" fill="#4a5154"'); });
  });
  return sheet(2000, 940, '洞口透视：州际双孔洞门', 'T3', body);
}
