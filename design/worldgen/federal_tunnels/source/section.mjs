// T1: cross sections of the two tunnel types.
import { R4, R2, solve, arch, archY, surface, TOE, LINING, SHOTCRETE, PANEL_TOP } from './geom.mjs';
import { f, pts, poly, line, text, mono, sheet, dimH, dimV, leader } from './svg.mjs';

function drawSection(s, ox, oy, sc, opts) {
  const g = solve(s);
  const P = (x, y) => [ox + x * sc, oy - y * sc];
  const L = g.walkL, R = g.W - g.walkR, h = g.walkH;
  let o = '';
  const id = g.id.replace(/[^a-z0-9]/gi, '');
  o += `<defs><pattern id="rock${id}" width="9" height="9" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><rect width="9" height="9" fill="#e4dfd3"/><line x1="0" y1="0" x2="0" y2="9" stroke="#c9c1ae" stroke-width="1.2"/></pattern></defs>`;
  // ground mass around the bore
  const ex = SHOTCRETE + LINING;
  const rockPts = [];
  const big = arch(g, ex + 1.6, 6);
  rockPts.push(P(-ex - 1.6, -1.9), ...big.map(p => P(p[0], p[1])), P(g.W + ex + 1.6, -1.9));
  o += `<polygon points="${pts(rockPts)}" fill="url(#rock${id})" stroke="none"/>`;
  // excavation (shotcrete) and final lining
  const shell = (off, bottom) => [P(-off, bottom), P(-off, g.hc), ...arch(g, off).map(p => P(p[0], p[1])), P(g.W + off, bottom)];
  o += poly(shell(ex, -1.0), 'fill="#b8b4aa" stroke="#6f6a5f" stroke-width="1"');
  o += poly(shell(LINING, -0.95), 'fill="#cfcfca" stroke="#55595b" stroke-width="1.2"');
  // waterproofing membrane on the extrados of the final lining
  o += `<polyline points="${pts([P(-LINING, -0.6), P(-LINING, g.hc), ...arch(g, LINING).map(p => P(p[0], p[1])), P(g.W + LINING, -0.6)])}" fill="none" stroke="#2c6174" stroke-width="2" stroke-dasharray="7 4"/>`;
  // air
  const segs = surface(g);
  const air = [P(segs[0][0], segs[0][1]), ...segs.map(q => P(q[2], q[3]))];
  o += poly(air, 'fill="#ffffff" stroke="none"');
  // wall panels band and painted arch: thick strokes along the inner face
  for (const q of segs) {
    if (q[4] === 'panel') o += line(P(q[0], q[1]), P(q[2], q[3]), 'stroke="#e7dcc0" stroke-width="7"');
    if (q[4] === 'arch') o += line(P(q[0], q[1]), P(q[2], q[3]), 'stroke="#7f8589" stroke-width="5"');
  }
  o += `<polyline points="${pts([P(0, h), P(0, g.hc), ...arch(g).map(p => P(p[0], p[1])), P(g.W, h)])}" fill="none" stroke="#23282b" stroke-width="1.6"/>`;
  // walkways (raised safety walks on F-shape barriers), with utilities inside
  const walkPoly = left => left
    ? [P(0, h), P(L, h), P(L + 0.08, 0.25), P(L + TOE, 0), P(L + TOE, -0.35), P(0, -0.35)]
    : [P(g.W, h), P(R, h), P(R - 0.08, 0.25), P(R - TOE, 0), P(R - TOE, -0.35), P(g.W, -0.35)];
  o += poly(walkPoly(true), 'fill="#dcdcd6" stroke="#23282b" stroke-width="1.3"');
  o += poly(walkPoly(false), 'fill="#dcdcd6" stroke="#23282b" stroke-width="1.3"');
  // fire main in the egress walk, cable ducts in the other
  const fm = P(0.42, 0.25);
  o += `<circle cx="${f(fm[0])}" cy="${f(fm[1])}" r="${f(0.16 * sc)}" fill="#fff" stroke="#b03a2e" stroke-width="2.2"/>`;
  for (let i = 0; i < 3; i++) { const c = P(g.W - 0.62 + i * 0.0, 0.52 - i * 0.24); o += `<rect x="${f(c[0])}" y="${f(c[1])}" width="${f(0.36 * sc)}" height="${f(0.16 * sc)}" fill="#fff" stroke="#55595b" stroke-width="1"/>`; }
  // handrail on the egress side
  const hr0 = P(0.06, h + 1.07), hr1 = P(0.06, h);
  o += line(hr0, hr1, 'stroke="#23282b" stroke-width="2"') + line(P(0.0, h + 1.07), P(0.2, h + 1.07), 'stroke="#23282b" stroke-width="3"');
  // pavement and base
  o += poly([P(L + TOE, 0), P(R - TOE, 0), P(R - TOE, -0.12), P(L + TOE, -0.12)], 'fill="#3a3d40" stroke="none"');
  o += poly([P(L + TOE, -0.12), P(R - TOE, -0.12), P(R - TOE, -0.55), P(L + TOE, -0.55)], 'fill="#a59f92" stroke="none"');
  o += line(P(L + TOE, 0), P(R - TOE, 0), 'stroke="#23282b" stroke-width="1.4"');
  // slot drains at the barrier toes, groundwater drains at the footings
  for (const x of [L + TOE + 0.18, R - TOE - 0.18]) {
    const c = P(x, -0.3);
    o += `<circle cx="${f(c[0])}" cy="${f(c[1])}" r="${f(0.12 * sc)}" fill="#fff" stroke="#23282b" stroke-width="1.2"/>` + line(P(x, 0), P(x, -0.18), 'stroke="#23282b" stroke-width="2"');
  }
  for (const x of [-ex - 0.25, g.W + ex + 0.25]) {
    const c = P(x, -0.7);
    o += `<circle cx="${f(c[0])}" cy="${f(c[1])}" r="${f(0.13 * sc)}" fill="#fff" stroke="#2c6174" stroke-width="1.5" stroke-dasharray="2 2"/>`;
  }
  // lane markings
  let x = g.laneL;
  const marks = [x];
  for (const w of g.lanes) { x += w; marks.push(x); }
  marks.forEach((m, i) => { const a = P(m - 0.07, 0.02); o += `<rect x="${f(a[0])}" y="${f(a[1] - 3)}" width="${f(0.14 * sc)}" height="4" fill="${(opts.oneWay ? i === 0 : i === 1) ? '#e2b32b' : '#ffffff'}" stroke="#23282b" stroke-width="0.6"/>`; });
  // clearance envelopes
  const e0 = P(g.curbL, 0), e1 = P(g.curbR, g.clear);
  o += `<rect x="${f(e0[0])}" y="${f(e1[1])}" width="${f(e1[0] - e0[0])}" height="${f(e0[1] - e1[1])}" fill="none" stroke="#2e7448" stroke-width="1.6" stroke-dasharray="9 5"/>`;
  for (const [a, b] of [[0, L], [R, g.W]]) {
    const p0 = P(a, h), p1 = P(b, h + 2.2);
    o += `<rect x="${f(p0[0])}" y="${f(p1[1])}" width="${f(p1[0] - p0[0])}" height="${f(p0[1] - p1[1])}" fill="none" stroke="#2e7448" stroke-width="1.1" stroke-dasharray="4 4"/>`;
  }
  // jet fans (fan sections only)
  const fans = [];
  for (const sx of [-2.3, 2.3]) {
    const fx = g.xa + sx, top = archY(g, fx), cy = top - 0.35 - 0.6;
    const c = P(fx, cy);
    fans.push(c);
    o += line(P(fx - 0.25, top), P(fx - 0.25, cy + 0.4), 'stroke="#55595b" stroke-width="2"') + line(P(fx + 0.25, top), P(fx + 0.25, cy + 0.4), 'stroke="#55595b" stroke-width="2"');
    o += `<circle cx="${f(c[0])}" cy="${f(c[1])}" r="${f(0.6 * sc)}" fill="#8b9296" stroke="#23282b" stroke-width="1.4" stroke-dasharray="6 3"/>`;
    o += `<circle cx="${f(c[0])}" cy="${f(c[1])}" r="${f(0.38 * sc)}" fill="#5f666a" stroke="none"/>`;
  }
  // luminaires on both upper walls
  const lum = [];
  for (const side of [0, 1]) {
    const ys = 5.75;
    // walk the arch to find y = ys on this side
    const a = arch(g, 0, 1);
    const half = side === 0 ? a.filter(p => p[0] < g.xa) : a.filter(p => p[0] > g.xa);
    let best = half[0];
    for (const p of half) if (Math.abs(p[1] - ys) < Math.abs(best[1] - ys)) best = p;
    const c = P(best[0] + (side === 0 ? 0.12 : -0.12), best[1] - 0.12);
    lum.push(c);
    o += `<rect x="${f(c[0] - 0.22 * sc)}" y="${f(c[1] - 0.09 * sc)}" width="${f(0.44 * sc)}" height="${f(0.18 * sc)}" fill="#fff6c9" stroke="#23282b" stroke-width="1.2" transform="rotate(${side === 0 ? -48 : 48} ${f(c[0])} ${f(c[1])})"/>`;
  }
  // lane control signals above lane centres (signal sections only)
  const lcs = [];
  x = g.laneL;
  for (const w of g.lanes) {
    const cx = x + w / 2; x += w;
    const top = archY(g, cx), b = P(cx - 0.45, g.clear + 0.25 + 0.9);
    o += line(P(cx, top), P(cx, g.clear + 0.25 + 0.9), 'stroke="#55595b" stroke-width="1.4" stroke-dasharray="3 2"');
    o += `<rect x="${f(b[0])}" y="${f(b[1])}" width="${f(0.9 * sc)}" height="${f(0.9 * sc)}" fill="#26292b" stroke="#23282b" stroke-width="1" stroke-dasharray="4 2"/>`;
    const c = P(cx, g.clear + 0.25 + 0.45);
    o += `<path d="M ${f(c[0] - 9)} ${f(c[1] - 6)} L ${f(c[0])} ${f(c[1] + 8)} L ${f(c[0] + 9)} ${f(c[1] - 6)}" fill="none" stroke="#4fd06b" stroke-width="3"/>`;
    lcs.push(P(cx + 0.45, g.clear + 0.25 + 0.9));
  }
  // axis
  o += line(P(g.xa, -1.6), P(g.xa, g.crown + 1.3), 'stroke="#8e3a32" stroke-width="0.9" stroke-dasharray="14 4 3 4"');
  o += mono(P(g.xa, g.crown + 1.35)[0], P(g.xa, g.crown + 1.35)[1], '隧道轴线', 'font-size="11" fill="#8e3a32" text-anchor="middle"');

  // dimensions: widths
  const yd = P(0, -1.55)[1] + 20;
  let x0 = 0;
  const chain = [[g.walkL, '人行道'], [g.shL, '路肩'], ...g.lanes.map(w => [w, '车道']), [g.shR, '路肩'], [g.walkR, opts.walkRName]];
  for (const [w, name] of chain) {
    o += dimH(P(x0, 0)[0], P(x0 + w, 0)[0], yd, w.toFixed(2), P(0, -0.0)[1] + 4, 12.5);
    o += text((P(x0, 0)[0] + P(x0 + w, 0)[0]) / 2, yd + 16, name, 'font-size="11.5" fill="#6b7376" text-anchor="middle"');
    x0 += w;
  }
  o += dimH(P(0, 0)[0], P(g.W, 0)[0], yd + 44, `墙面到墙面 ${g.W.toFixed(2)}`, null, 13);
  o += dimH(P(g.curbL, 0)[0], P(g.curbR, 0)[0], yd + 70, `防撞墙之间 ${(g.curbR - g.curbL).toFixed(2)}`, null, 13);
  // heights
  if (!opts.vdims) return { o, g, P, fans, lum, lcs };
  const xr = P(g.W + ex + 1.9, 0)[0] + 18;
  o += dimV(xr, P(0, 0)[1], P(0, g.clear)[1], `净空 ${g.clear.toFixed(2)}`);
  o += dimV(xr + 34, P(0, 0)[1], P(0, g.crown)[1], `拱顶 ${g.crown.toFixed(2)}`);
  o += dimV(xr - 34, P(0, 0)[1], P(0, g.hc)[1], `起拱 ${g.hc.toFixed(2)}`, 11.5);
  for (const yy of [0, g.clear, g.crown, g.hc]) o += line([P(g.W, 0)[0] + 6, P(0, yy)[1]], [xr + 40, P(0, yy)[1]], 'stroke="#2c6174" stroke-width="0.6" stroke-dasharray="2 3"');
  return { o, g, P, fans, lum, lcs };
}

export function sectionSheet() {
  const W = 2000, H = 1000;
  let b = '';
  // R4
  const R4X = 440, R4S = 42;
  const a = drawSection(R4, R4X, 560, R4S, { oneWay: true, walkRName: '检修道', vdims: true });
  b += a.o;
  const { P, g } = a;
  b += text(R4X + g.W * R4S / 2, 64, 'T-R4　州际单向孔（双孔，每幅一孔）', 'font-size="22" font-weight="700" fill="#23282b" text-anchor="middle"');
  b += text(R4X + g.W * R4S / 2, 88, '两车道 3.75 + 内侧路肩 1.00 / 外侧 3.00（洞外 1.50 / 3.50，进洞缩窄）· 车道线保持洞外位置', 'font-size="13.5" fill="#6b7376" text-anchor="middle"');
  const lx = 380;
  b += leader(P(-0.6, 6.4), [lx - 60, 150], '初期支护：喷混凝土 + 锚杆 0.30', 'end');
  b += leader(P(-0.3, 4.2), [lx - 60, 185], '二次衬砌：现浇混凝土 0.45', 'end');
  b += leader(P(-0.45, 2.0), [lx - 60, 220], '防水层（排水型 PVC 膜）', 'end');
  b += leader(P(0.02, 3.6), [lx - 60, 300], '浅色墙面板（搪瓷钢板，到 4.5 m）', 'end');
  b += leader(P(0.6, 1.3), [lx - 60, 340], '疏散人行道 1.20 + 扶手（横通道一侧）', 'end');
  b += leader(P(0.42, 0.25), [lx - 60, 380], '消防水管（人行道内）', 'end');
  b += leader(P(1.1, 0.4), [lx - 60, 420], 'F 型防撞墙，顶面 0.80', 'end');
  b += leader(P(g.curbL + TOE + 0.18, -0.3), [lx - 60, 520], '缝隙式排水沟（两侧）', 'end');
  b += leader(P(-0.98, -0.7), [lx - 60, 560], '侧墙排水管（地下水）', 'end');
  const rx = R4X + g.W * R4S + 230;
  b += leader(a.fans[1], [rx, 150], '射流风机 ∅1.2 成对（只在风机段）');
  b += leader(a.lum[1], [rx, 195], 'LED 条灯，两侧连续');
  b += leader(a.lcs[1], [rx, 240], '车道控制灯 ↓ / ✕（只在信号断面）');
  b += leader(P(g.xa + 2.5, 7.85), [rx, 112], '拱顶深灰涂装');
  b += leader(P(g.W - 0.35, 0.55), [rx, 470], '检修道 0.75 + 电缆槽');
  b += leader(P(g.xa + 1.2, -0.05), [rx, 540], '沥青路面（和洞外相同），横坡 2%');
  b += leader(P(g.curbR - 0.4, 4.6), [rx, 300], '建筑限界 5.10（绿色虚线）');
  // R2
  const R2X = 1590, R2S = 27;
  const c = drawSection(R2, R2X, 560, R2S, { oneWay: false, walkRName: '人行道' });
  b += c.o;
  b += text(R2X + c.g.W * R2S / 2, 64, 'T-R2　州道双向孔（单孔）', 'font-size="22" font-weight="700" fill="#23282b" text-anchor="middle"');
  b += text(R2X + c.g.W * R2S / 2, 88, '两车道 3.65 + 路肩 1.20 ×2（洞外 2.40，进洞缩窄）· 中线双黄线', 'font-size="13.5" fill="#6b7376" text-anchor="middle"');
  b += line([1500, 115], [1500, 900], 'stroke="#c5cccd" stroke-width="1"');
  // notes under R2
  const notes = [
    '部件和 T-R4 相同；两侧人行道都是 0.90。',
    '净空 5.10，起拱 3.00，拱顶 7.89，墙面到墙面 11.50。',
    '单孔没有横通道：长于 300 m 的州道隧道',
    '　加平行疏散通道或通往地面的出口（待定）。',
    '双孔中心距 31.05 m（R4 两幅中心线），',
    '　岩柱约 16 m，大于一个洞径。',
  ];
  notes.forEach((s, i) => { b += text(1530, 735 + i * 22, s, 'font-size="13.5" fill="#23282b"'); });
  return sheet(W, H, '横断面：州际 R4 单向孔 / 州道 R2 双向孔', 'T1', b);
}
