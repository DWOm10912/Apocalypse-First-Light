// T4: portal front elevation and plan (R4 twin bores), orthographic, same x scale.
import { g, XR, HW, HTOP, RING, WALL_LEN, CUT, wx, natural, wallTop, ground, opening, ringOuter } from './site.mjs';
import { f, pts, poly, line, text, mono, sheet, dimH, dimV, leader } from './svg.mjs';

export function elevPlanSheet() {
  const W = 2000, H = 1740, S = 9.2, SE = 14, CX = 1000;
  let b = '';
  // ---------- elevation ----------
  const EY = 640; // screen y of road level
  const E = (x, y) => [CX + x * SE, EY - y * SE];
  b += text(70, 70, '洞口立面（从引道看，进洞方向）', 'font-size="20" font-weight="700" fill="#23282b"');
  // natural silhouette behind (z = 40) and the cut crest
  const sil = [], crest = [];
  for (let x = -62; x <= 62; x += 2) sil.push(E(x, Math.min(36, natural(x, 40))));
  b += `<polygon points="${pts([E(-62, 0), ...sil, E(62, 0)])}" fill="#e6eadb" stroke="#9aa77f" stroke-width="1"/>`;
  for (let x = -62; x <= 62; x += 1) {
    // where the 2:1 cut meets the natural ground, scanning back from the face
    let y = null;
    for (let z = 0; z < 60; z += 0.25) { const [gy, cut] = ground(x, z); if (!cut) { y = gy; break; } }
    crest.push(E(x, y ?? natural(x, 60)));
  }
  b += `<polyline points="${pts(crest)}" fill="none" stroke="#6f6a5f" stroke-width="1.4" stroke-dasharray="6 3"/>`;
  // ground at the face plane: cut slopes beside the walls
  const gl = [];
  for (let x = -62; x <= -HW; x += 1) gl.push(E(x, ground(x, -0.01)[0]));
  b += `<polygon points="${pts([E(-62, -2), ...gl, E(-HW, -2)])}" fill="#d7d2c3" stroke="#6f6a5f" stroke-width="1.2"/>`;
  const gr = [];
  for (let x = HW; x <= 62; x += 1) gr.push(E(x, ground(x, -0.01)[0]));
  b += `<polygon points="${pts([E(HW, -2), ...gr, E(62, -2)])}" fill="#d7d2c3" stroke="#6f6a5f" stroke-width="1.2"/>`;
  // rock face above the headwall (the cut, seen face on)
  const face = [E(-HW, HTOP)];
  for (let x = -HW; x <= HW; x += 1) { let y = HTOP; for (let z = 0; z < 60; z += 0.25) { const [gy, cut] = ground(x, z); if (!cut) { y = gy; break; } } face.push(E(x, y)); }
  face.push(E(HW, HTOP));
  b += `<polygon points="${pts(face)}" fill="#cfcabd" stroke="#6f6a5f" stroke-width="1"/>`;
  for (let i = 0; i < 26; i++) { const x = -HW + 2 + i * 1.9, yt = HTOP + 0.6 + (i % 3) * 0.5; b += line(E(x, yt), E(x + 0.9, yt + 0.9), 'stroke="#a39e90" stroke-width="1"'); }
  // headwall with openings
  const hole = side => 'M ' + ringOuter(side).map(([x, y]) => E(x, y).map(f).join(' ')).join(' L ') + ' Z';
  b += `<path d="M ${pts([E(-HW, 0), E(HW, 0), E(HW, HTOP), E(-HW, HTOP)]).split(' ').join(' L ')} Z ${hole(1)} ${hole(-1)}" fill="#d9d6cd" fill-rule="evenodd" stroke="#23282b" stroke-width="1.8"/>`;
  for (let y = 1.2; y < HTOP; y += 1.2) b += `<line x1="${f(E(-HW, y)[0])}" y1="${f(E(0, y)[1])}" x2="${f(E(HW, y)[0])}" y2="${f(E(0, y)[1])}" stroke="#c4c0b5" stroke-width="0.7"/>`;
  for (const side of [1, -1]) {
    const op = 'M ' + opening(side).map(([x, y]) => E(x, y).map(f).join(' ')).join(' L ') + ' Z';
    b += `<path d="${hole(side)} ${op}" fill="#e8e5dd" fill-rule="evenodd" stroke="#23282b" stroke-width="1.4"/>`;
    b += `<path d="${op}" fill="#3b3d3c"/>`;
    // name plate + clearance plate
    const np = E(side * XR - 6, 11.5), cp = E(side * XR - 2.1, g.crown + RING + 0.7);
    b += `<rect x="${f(np[0])}" y="${f(np[1])}" width="${f(12 * SE)}" height="${f(1.1 * SE)}" fill="#3a3c3b"/>` + `<text x="${f(np[0] + 6 * SE)}" y="${f(np[1] + 0.8 * SE)}" font-family="Arial" font-weight="700" font-size="${f(0.7 * SE)}" fill="#e6e2d6" text-anchor="middle">CEDAR PASS TUNNEL</text>`;
    b += `<rect x="${f(cp[0])}" y="${f(cp[1])}" width="${f(4.2 * SE)}" height="${f(0.55 * SE)}" fill="#fff" stroke="#23282b" stroke-width="0.8"/>`;
    // lane control signals just inside, seen through the opening
    let x = g.laneL;
    for (const w of g.lanes) { const cx = wx(x + w / 2, side); x += w; const p = E(cx - 0.45, g.clear + 1.15); b += `<rect x="${f(p[0])}" y="${f(p[1])}" width="${f(0.9 * SE)}" height="${f(0.9 * SE)}" fill="#141515" stroke="#4fe07a" stroke-width="1"/>`; }
  }
  b += `<rect x="${f(E(-HW - 0.3, HTOP + 0.55)[0])}" y="${f(E(0, HTOP + 0.55)[1])}" width="${f((2 * HW + 0.6) * SE)}" height="${f(0.55 * SE)}" fill="#e3e0d8" stroke="#23282b" stroke-width="1.2"/>`;
  // road line
  b += line(E(-62, 0), E(62, 0), 'stroke="#23282b" stroke-width="1.6"');
  // axes and dims
  for (const s of [1, -1]) b += line(E(s * XR, -1.5), E(s * XR, HTOP + 3), 'stroke="#8e3a32" stroke-width="0.8" stroke-dasharray="12 4 3 4"');
  b += line(E(0, -1.5), E(0, HTOP + 3), 'stroke="#8e3a32" stroke-width="0.8" stroke-dasharray="12 4 3 4"');
  const yd = EY + 46;
  b += dimH(E(-XR, 0)[0], E(XR, 0)[0], yd, `两孔中心距 ${(2 * XR).toFixed(2)}（= R4 两幅车道中心距）`, EY + 4);
  b += dimH(E(-HW, 0)[0], E(HW, 0)[0], yd + 30, `端墙宽 ${(2 * HW).toFixed(1)}`, EY + 4);
  b += dimH(E(wx(0, 1), 0)[0], E(wx(g.W, 1), 0)[0], yd - 26, `洞宽 ${g.W.toFixed(2)}`, null, 12);
  b += dimH(E(-8.8, 0)[0], E(8.8, 0)[0], yd - 26, `两孔内墙之间 17.60`, null, 12);
  b += dimV(E(HW, 0)[0] + 40, EY, E(0, HTOP)[1], `端墙高 ${HTOP.toFixed(1)}`);
  b += dimV(E(-XR - g.W / 2 - 2.4, 0)[0], EY, E(0, g.crown)[1], `拱顶 ${g.crown.toFixed(2)}`, 12);
  // leaders
  b += leader(E(-HW + 2, 13.6), [300, 150], '洞顶仰坡：岩石 2:1', 'end');
  b += leader(E(-50, 34), [300, 112], '自然山坡（远处轮廓）', 'end');
  b += leader(E(-HW - 6, ground(-HW - 6, -0.01)[0]), [300, 300], '挡墙后岩石边坡 2:1', 'end');
  b += leader(E(-XR - 6.8, 7.2), [300, 380], '洞口环框 0.9（凸出 0.35）', 'end');
  b += leader(E(-HW + 1, 4), [300, 450], '端墙：现浇混凝土', 'end');
  b += leader(E(XR, 11.9), [1700, 150], '隧道名牌（示例名）');
  b += leader(E(XR + 1.9, g.crown + RING + 0.95), [1700, 200], '限高牌 CLEARANCE 5.1 m');
  b += leader(E(wx(g.laneL + g.lanes[0] + g.lanes[1] / 2, 1), g.clear + 1.6), [1700, 300], '洞内第一组车道控制灯');
  b += leader(E(HW - 1, HTOP + 0.8), [1700, 250], '压顶');
  b += leader(E(5, 30), [1700, 112], '仰坡顶线（虚线）');

  // ---------- plan ----------
  const PY = 1100; // screen y of z = 0
  const PL = (x, z) => [CX + x * S, PY - z * S];
  b += line([40, 742], [W - 40, 742], 'stroke="#c5cccd" stroke-width="1"');
  b += text(70, 776, '洞口平面（上方是山体，下方是引道）', 'font-size="20" font-weight="700" fill="#23282b"');
  // terrain shading: cut areas vs natural
  for (let z = -62; z < 32; z += 2) for (let x = -62; x < 62; x += 2) {
    if (z < 0 && Math.abs(x) < HW) continue;
    const [, cut] = ground(x + 1, z + 1);
    const p = PL(x, z + 2);
    b += `<rect x="${f(p[0])}" y="${f(p[1])}" width="${f(2 * S + 0.4)}" height="${f(2 * S + 0.4)}" fill="${cut ? '#dcd6c6' : '#e3ead6'}"/>`;
  }
  // crest line
  const cl = [];
  for (let x = -62; x <= 62; x += 1) { let zc = null; for (let z = -62; z < 40; z += 0.25) { const [, cut] = ground(x, z); if (cut) { zc = z; break; } } if (zc !== null) cl.push(PL(x, zc)); }
  b += `<polyline points="${pts(cl)}" fill="none" stroke="#6f6a5f" stroke-width="1.4" stroke-dasharray="6 3"/>`;
  // road, median, ditches
  for (const s of [1, -1]) {
    b += poly([PL(s * 9.5, -62), PL(s * 22, -62), PL(s * 22, 0), PL(s * 9.5, 0)], 'fill="#55585b"');
    b += poly([PL(s * 22, -62), PL(s * HW, -62), PL(s * HW, 0), PL(s * 22, 0)], 'fill="#b3afa4"');
    for (const [v, c, dash] of [[11, '#e2b32b', ''], [14.75, '#fff', ''], [18.5, '#fff', '']]) b += line(PL(s * v, -62), PL(s * v, 0), `stroke="${c}" stroke-width="2"${dash}`);
    // barrier lead-ins
    b += poly([PL(s * 10.25, 0), PL(s * 9.4, 0), PL(s * 9.4, -12), PL(s * 10.25, -16)], 'fill="#e9e7e0" stroke="#23282b" stroke-width="0.8"');
    b += poly([PL(s * 21.25, 0), PL(s * 22.1, 0), PL(s * 22.1, -12), PL(s * 21.25, -16)], 'fill="#e9e7e0" stroke="#23282b" stroke-width="0.8"');
    // retaining walls
    b += poly([PL(s * HW, 0), PL(s * (HW + 0.6), 0), PL(s * (HW + 0.6), -WALL_LEN), PL(s * HW, -WALL_LEN)], 'fill="#9b978d" stroke="#23282b" stroke-width="1"');
    // bores (underground, dashed), lining outside
    const x0 = wx(-0.75, s), x1 = wx(g.W + 0.75, s);
    b += `<rect x="${f(Math.min(PL(x0, 0)[0], PL(x1, 0)[0]))}" y="${f(PL(0, 32)[1])}" width="${f(Math.abs(x1 - x0) * S)}" height="${f(32 * S)}" fill="#f4f2ec" fill-opacity="0.75" stroke="#23282b" stroke-width="1.2" stroke-dasharray="8 4"/>`;
    for (const [v, c] of [[g.laneL, '#e2b32b'], [g.laneL + g.lanes[0], '#9a9a9a'], [g.laneR, '#9a9a9a']]) b += line(PL(wx(v, s), 0), PL(wx(v, s), 32), `stroke="${c}" stroke-width="1.2" stroke-dasharray="5 4"`);
    // direction arrows
    const ax = s * 14.75, az0 = s > 0 ? -50 : -30, az1 = s > 0 ? -30 : -50;
    b += `<line x1="${f(PL(ax + s * 1.9, az0)[0])}" y1="${f(PL(0, az0)[1])}" x2="${f(PL(ax + s * 1.9, az1)[0])}" y2="${f(PL(0, az1)[1])}" stroke="#fff" stroke-width="2.4" marker-end="url(#arr)"/>`;
  }
  b += `<defs><marker id="arr" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="#fff"/></marker></defs>`;
  b += poly([PL(-9.5, -62), PL(9.5, -62), PL(9.5, 0), PL(-9.5, 0)], 'fill="#a9bd8a"');
  b += line(PL(0, -62), PL(0, 0), 'stroke="#7f9566" stroke-width="3"');
  // headwall
  b += poly([PL(-HW - 0.3, 0), PL(HW + 0.3, 0), PL(HW + 0.3, 1.0), PL(-HW - 0.3, 1.0)], 'fill="#8d897f" stroke="#23282b" stroke-width="1.4"');
  // cut-and-cover note: the first few metres of the bore behind the headwall
  // equipment
  const dot = (x, z, c, r = 5) => `<circle cx="${f(PL(x, z)[0])}" cy="${f(PL(x, z)[1])}" r="${r}" fill="${c}" stroke="#23282b" stroke-width="1"/>`;
  b += line(PL(23.6, -40), PL(12.6, -40), 'stroke="#55595b" stroke-width="3"') + dot(23.6, -40, '#55595b') + dot(13.0, -40, '#3fe07a', 4) + dot(16.75, -40, '#3fe07a', 4);
  b += line(PL(23.2, -56), PL(9.8, -56), 'stroke="#c0392b" stroke-width="3" stroke-dasharray="6 4"') + dot(23.2, -56, '#f1efe8');
  b += line(PL(22.6, -47), PL(24.4, -47), 'stroke="#23282b" stroke-width="4"');
  // mirrored equipment for the outbound side is not needed (traffic leaves); the left carriageway has its own at the far portal
  // dims and labels
  b += dimV(PL(HW, 0)[0] + 60, PL(0, 0)[1], PL(0, -WALL_LEN)[1], `挡墙 ${WALL_LEN}`);
  b += leader(PL(23.4, -40), [1700, 1100], '交通信号（40 m）');
  b += leader(PL(18, -56), [1700, 1180], '关闭栏杆（55 m）');
  b += leader(PL(23.5, -47), [1700, 1140], '开灯提示牌（47 m）');
  b += leader(PL(HW + 0.3, -20), [1700, 1260], '引道挡墙');
  b += leader(PL(21.6, -8), [1700, 1300], '防撞墙引线 16 m');
  b += leader(PL(wx(g.xa, 1), 20), [1700, 900], '右幅孔（进洞），衬砌外轮廓');
  b += leader(PL(0, 0.5), [1700, 1040], '端墙（厚 1.0）');
  b += leader(PL(-60, 10), [300, 900], '自然山坡（绿）', 'end');
  b += leader(PL(-28, -12), [300, 980], '开挖坡面（灰）', 'end');
  b += leader(PL(-4, -40), [300, 1180], '中央分隔带 19 m', 'end');
  b += leader(PL(-wx(g.xa, 1), 20), [300, 820], '左幅孔（对向车出洞）', 'end');
  b += text(CX, PL(0, 31)[1] - 8, '↑ 第一条横通道在洞内 250 m 处；风机段从 150 m 起', 'font-size="13.5" fill="#23282b" text-anchor="middle"');
  b += text(CX, PL(0, -62)[1] + 22, '↓ 可变情报板门架在洞口前约 200 m（图外）', 'font-size="13.5" fill="#23282b" text-anchor="middle"');
  return sheet(W, H, '洞口立面和平面：州际双孔', 'T4', b);
}
