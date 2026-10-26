// River / lake V1 design sheet: plan view, cross-sections, long profile, lake silhouettes -> PNG.
const path = require('path');
const fs = require('fs');
const root = process.argv[2], out = process.argv[3];
const sharp = require(path.join(root, 'wiki', 'node_modules', 'sharp'));
const W = 1800, H = 1580;
const F = `font-family="Microsoft YaHei, SimHei, sans-serif"`;
const C = {bg: '#f4f1ea', ink: '#2b2b2b', mute: '#77736a', water: '#5b8fb0', waterD: '#2f6488', soil: '#8a6a48', turf: '#7d9455',
  gravel: '#a39a8c', sand: '#d9c48f', mud: '#6b5a48', rock: '#8c8c88', bar: '#cdb98a'};
let s = `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}"><rect width="${W}" height="${H}" fill="${C.bg}"/>`;
const t = (x, y, str, size = 20, fill = C.ink, anchor = 'start', weight = 'normal') =>
  s += `<text x="${x}" y="${y}" ${F} font-size="${size}" fill="${fill}" text-anchor="${anchor}" font-weight="${weight}">${str.replace(/&/g, '&amp;').replace(/</g, '&lt;')}</text>`;
const panel = (x, y, w, h, title) => { s += `<rect x="${x}" y="${y}" width="${w}" height="${h}" fill="#fbfaf6" stroke="#cfc9bb"/>`; t(x + 16, y + 32, title, 24, C.ink, 'start', 'bold'); };

t(40, 52, '河流与湖泊 V1 设计示意（AFL，按美国实测比例，1 格 = 1 m）', 32, C.ink, 'start', 'bold');
t(40, 84, 'W = 平滩宽，D = 平滩平均深；数字见 docs/worldgen/river_lake_v1_design.md', 18, C.mute);

// ---- A. plan view
const ax = 40, ay = 110, aw = 1720, ah = 430;
panel(ax, ay, aw, ah, 'A  平面：曲流波长约 11 W，弯道半径 2–3 W，浅滩间距 5–7 W');
const Wpx = 26, lam = 11 * Wpx * 1.0, amp = 2.2 * Wpx, cy = ay + 250, x0 = ax + 60, x1 = ax + aw - 60;
const cl = [];
for (let x = x0; x <= x1; x += 2) { const p = (x - x0) / lam * 2 * Math.PI; cl.push([x, cy + amp * Math.sin(p) + 0.18 * amp * Math.sin(2 * p + 0.6)]); }
const off = (k) => cl.map(([x, y], i) => { const [xa, ya] = cl[Math.max(0, i - 1)], [xb, yb] = cl[Math.min(cl.length - 1, i + 1)];
  const dx = xb - xa, dy = yb - ya, l = Math.hypot(dx, dy); return [x - dy / l * k, y + dx / l * k]; });
const poly = (pts) => pts.map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`).join(' ');
// floodplain + banks
s += `<polygon points="${poly(off(-Wpx * 1.9))} ${poly(off(Wpx * 1.9).reverse())}" fill="#dfe6cf"/>`;
// point bars on inner bends
for (let k = 0; k < 8; k++) {
  const xb = x0 + lam * (k / 2 + 0.25); if (xb > x1 - 20) break;
  const i = Math.round((xb - x0) / 2), [px, py] = cl[i], up = Math.sin((xb - x0) / lam * 2 * Math.PI) > 0;
  s += `<ellipse cx="${px}" cy="${py + (up ? -Wpx * 0.55 : Wpx * 0.55)}" rx="${Wpx * 2.4}" ry="${Wpx * 0.75}" fill="${C.bar}"/>`;
}
s += `<polygon points="${poly(off(-Wpx / 2))} ${poly(off(Wpx / 2).reverse())}" fill="${C.water}"/>`;
// pools at bend apex, riffles at crossings
for (let k = 0; k < 8; k++) {
  const xa = x0 + lam * (k / 2 + 0.25), xr = x0 + lam * (k / 2 + 0.5);
  if (xa < x1 - 20) { const [px, py] = cl[Math.round((xa - x0) / 2)], up = Math.sin((xa - x0) / lam * 2 * Math.PI) > 0;
    s += `<ellipse cx="${px}" cy="${py + (up ? Wpx * 0.22 : -Wpx * 0.22)}" rx="${Wpx * 1.3}" ry="${Wpx * 0.3}" fill="${C.waterD}"/>`;
    s += `<path d="M ${px - Wpx * 1.6} ${py + (up ? Wpx * 0.62 : -Wpx * 0.62)} q ${Wpx * 1.6} ${up ? Wpx * 0.35 : -Wpx * 0.35} ${Wpx * 3.2} 0" stroke="${C.soil}" stroke-width="5" fill="none"/>`; }
  if (xr < x1 - 10) { const [px, py] = cl[Math.round((xr - x0) / 2)];
    for (let j = -2; j <= 2; j++) s += `<line x1="${px - 8 + j * 6}" y1="${py - Wpx * 0.4}" x2="${px + 8 + j * 6}" y2="${py + Wpx * 0.4}" stroke="#e8f1f6" stroke-width="2"/>`; }
}
// labels
t(x0 + lam * 0.25 - 10, ay + 92, '深潭（弯顶外侧）', 18); s += `<line x1="${x0 + lam * 0.25}" y1="${ay + 100}" x2="${x0 + lam * 0.25}" y2="${cy + amp - 4}" stroke="${C.mute}" stroke-dasharray="4 4"/>`;
t(x0 + lam * 0.5 - 40, ay + 410, '浅滩（两弯之间）', 18); s += `<line x1="${x0 + lam * 0.5}" y1="${ay + 392}" x2="${x0 + lam * 0.5}" y2="${cy + 14}" stroke="${C.mute}" stroke-dasharray="4 4"/>`;
t(x0 + lam * 0.75 + 70, ay + 380, '边滩（凸岸沙砾，露出水面）', 18);
t(x0 + lam * 0.75 + 6, ay + 92, '冲刷岸（凹岸土坎，棕线）', 18); s += `<line x1="${x0 + lam * 0.75}" y1="${ay + 80}" x2="${x0 + lam * 0.75}" y2="${cy - amp - 16}" stroke="${C.mute}" stroke-dasharray="4 4"/>`;
// wavelength bar
const by = ay + 70; s += `<line x1="${x0 + 2 * lam}" y1="${by}" x2="${x0 + 3 * lam}" y2="${by}" stroke="${C.ink}" stroke-width="2"/>`;
s += `<line x1="${x0 + 2 * lam}" y1="${by - 8}" x2="${x0 + 2 * lam}" y2="${by + 8}" stroke="${C.ink}" stroke-width="2"/><line x1="${x0 + 3 * lam}" y1="${by - 8}" x2="${x0 + 3 * lam}" y2="${by + 8}" stroke="${C.ink}" stroke-width="2"/>`;
t(x0 + 2.5 * lam, by - 12, '波长 ≈ 11 W', 18, C.ink, 'middle');
s += `<rect x="${x1 - 210}" y="${ay + 392}" width="${Wpx}" height="10" fill="${C.ink}"/>`; t(x1 - 176, ay + 402, '= W', 16);

// ---- B. cross-sections
const bx = 40, byy = 560, bw = 1100, bh = 500;
panel(bx, byy, bw, bh, 'B  横断面（竖向放大 3 倍）：又宽又浅，宽深比 8–16');
const sect = (ox, oy, title, prof, mats) => {
  t(ox, oy - 130, title, 20, C.ink, 'start', 'bold');
  const sx = 34, sz = 3 * 34;   // px per m horizontal, vertical
  // ground polygon
  const pts = prof.map(([x, z]) => [ox + x * sx, oy - z * sz]);
  s += `<polygon points="${poly(pts)} ${ox + prof.at(-1)[0] * sx},${oy + 190} ${ox},${oy + 190}" fill="${C.soil}"/>`;
  // water at 0
  const wet = []; for (let i = 0; i < prof.length - 1; i++) { const [xa, za] = prof[i], [xb, zb] = prof[i + 1];
    if ((za > 0) !== (zb > 0)) wet.push(xa + (0 - za) / (zb - za) * (xb - xa)); }
  if (wet.length >= 2) { const inside = prof.filter(([x, z]) => z < 0 && x > wet[0] && x < wet[1]).map(([x, z]) => [ox + x * sx, oy - z * sz]);
    s += `<polygon points="${ox + wet[0] * sx},${oy} ${poly(inside)} ${ox + wet[1] * sx},${oy}" fill="${C.water}" opacity="0.9"/>`; }
  // bed material strip
  for (const [xa, xb, col] of mats) { const seg = prof.filter(([x]) => x >= xa && x <= xb).map(([x, z]) => [ox + x * sx, oy - z * sz]);
    if (seg.length > 1) s += `<polyline points="${poly(seg)}" stroke="${col}" stroke-width="9" fill="none" stroke-linejoin="round"/>`; }
  // turf on top
  const top = prof.filter(([, z]) => z > 0.35).map(([x, z]) => [ox + x * sx, oy - z * sz]);
  // levels
  s += `<line x1="${ox - 10}" y1="${oy}" x2="${ox + prof.at(-1)[0] * sx + 10}" y2="${oy}" stroke="${C.waterD}" stroke-dasharray="6 4"/>`;
  t(ox + prof.at(-1)[0] * sx + 14, oy + 6, '平常水位', 16, C.waterD);
};
// pool section (bend): outer cut bank on left, point bar on right; z in m relative to normal water
sect(bx + 60, byy + 210, '深潭（弯道）  W = 8 m，最深 1.6 m', [[0, 0.7], [1.2, 0.7], [1.25, -0.2], [1.6, -1.5], [2.6, -1.6], [4.0, -1.0], [6.0, -0.35], [7.6, 0.1], [8.6, 0.35], [10.4, 0.55], [12, 0.6]],
  [[1.25, 4.2, C.mud], [4.0, 8.7, C.gravel], [7.4, 8.8, C.bar]]);
t(bx + 60, byy + 428, '冲刷岸：0.5–1.5 m 垂直土坎', 16, C.soil);
t(bx + 300, byy + 428, '边滩：8–15% 缓坡，露出 0.2–0.6 m', 16, C.mute);
sect(bx + 600, byy + 210, '浅滩（过渡段）  水深 0.15–0.4 m', [[0, 0.65], [1.6, 0.6], [2.8, 0.15], [3.6, -0.25], [6.4, -0.3], [7.4, 0.1], [8.6, 0.55], [12, 0.62]],
  [[2.6, 7.6, C.gravel]]);
t(bx + 600, byy + 428, '岸坡伸进水里，没有一格台阶；圆砾底', 16, C.mute);
// legend
let lx = bx + 60, ly = byy + 460;
for (const [n, c] of [['泥 / 细砾', C.mud], ['圆砾', C.gravel], ['沙砾边滩', C.bar], ['表土', C.soil], ['水', C.water]]) {
  s += `<rect x="${lx}" y="${ly - 14}" width="22" height="16" fill="${c}"/>`; t(lx + 30, ly, n, 16); lx += 150; }
t(bx + 60, byy + 488, '对比：AFL 现在 10 km² 中心 3 格深，水边 1 格深，岸是 1 格台阶', 16, '#a0453a');

// ---- C. long profile
const cx = 1160, cyy = 560, cw = 600, ch = 500;
panel(cx, cyy, cw, ch, 'C  纵剖面：落差集中在浅滩');
const g0 = cyy + 150, gx = cx + 30, step = 70;
let bed = [], wl = [];
for (let i = 0; i <= 8; i++) {
  const x = gx + i * 66, lvl = Math.floor(i / 3);
  wl.push([x, g0 + 40 + lvl * step]);
}
// water surface steps: flat runs, steps over riffle ramps
let d = `M ${gx} ${g0 + 40}`;
for (let k = 0; k < 3; k++) { const xs = gx + k * 180 + 130; d += ` L ${xs} ${g0 + 40 + k * step} L ${xs + 40} ${g0 + 40 + (k + 1) * step}`; }
d += ` L ${gx + 560} ${g0 + 40 + 3 * step}`;
// bed: pools between, riffle humps at ramps
let bd = `M ${gx} ${g0 + 90}`;
for (let k = 0; k < 3; k++) { const xs = gx + k * 180; bd += ` Q ${xs + 60} ${g0 + 115 + k * step} ${xs + 125} ${g0 + 52 + k * step} L ${xs + 175} ${g0 + 52 + (k + 1) * step}`; }
bd += ` L ${gx + 560} ${g0 + 80 + 3 * step}`;
s += `<path d="${bd} L ${gx + 560} ${cyy + ch - 20} L ${gx} ${cyy + ch - 20} Z" fill="${C.soil}"/>`;
s += `<path d="${d}" stroke="${C.waterD}" stroke-width="4" fill="none"/>`;
for (let k = 0; k < 3; k++) { const xs = gx + k * 180; s += `<line x1="${xs + 125}" y1="${g0 + 52 + k * step}" x2="${xs + 175}" y2="${g0 + 52 + (k + 1) * step}" stroke="${C.gravel}" stroke-width="9"/>`; }
t(gx, cyy + 70, 'B 的水：连续水位，深潭里近乎平，浅滩上集中下降', 16, C.mute);
t(gx, cyy + 94, '原版水（过渡期、湖口）：取整格，浅滩下游端 2–6 m 砾石坡落一格', 16, C.mute);
t(gx, cyy + 118, '新世界的河最终只有 AFL 的水（用户 10-25 定）', 16, '#a0453a');

// ---- D. lake silhouettes
const dx = 40, dy = 1080, dw = 1720, dh = 480;
panel(dx, dy, dw, dh, 'D  湖泊形状（各自比例尺不同）');
const blob = (cx0, cy0, rx, ry, n, rough, seed, rot = 0) => { const p = []; for (let i = 0; i < 96; i++) { const a = i / 96 * 2 * Math.PI;
  let r = 1; for (let k = 2; k <= n; k++) r += rough / k * Math.sin(k * a + seed * k * 1.7);
  const x = Math.cos(a) * rx * r, y = Math.sin(a) * ry * r, c = Math.cos(rot), sn = Math.sin(rot);
  p.push([cx0 + x * c - y * sn, cy0 + x * sn + y * c]); } return p; };
const lake = (pts, fill = C.water) => s += `<polygon points="${poly(pts)}" fill="${fill}"/>`;
const cap = (x, y, a, b) => { t(x, y, a, 18, C.ink, 'middle', 'bold'); t(x, y + 24, b, 15, C.mute, 'middle'); };
const row1 = dy + 140, row2 = dy + 345;
lake(blob(150, row1, 70, 62, 5, 0.12, 1)); cap(150, row1 + 95, '锅穴湖', '圆，50 m–1 km，深 < 10 m');
lake(blob(390, row1, 150, 20, 4, 0.06, 2, -0.12)); cap(390, row1 + 95, '槽谷 / 指形湖', '长宽比 8–27，顺冰流');
{ const p = blob(660, row1, 95, 58, 14, 0.3, 3); lake(p); for (const [ix, iy, r] of [[640, row1 - 10, 12], [690, row1 + 20, 8], [610, row1 + 25, 6]]) lake(blob(ix, iy, r, r * 0.7, 4, 0.2, ix), '#7d8a6e'); }
cap(660, row1 + 95, '基岩冰蚀湖', '多湾多岛，顺断层');
lake(blob(920, row1, 70, 58, 3, 0.1, 4)); s += `<path d="M 870 ${row1 - 70} L 970 ${row1 - 70}" stroke="${C.rock}" stroke-width="10"/>`; cap(920, row1 + 95, '冰斗湖', '三面陡壁，林线以上');
{ let d2 = ''; for (let i = 0; i <= 60; i++) { const a = Math.PI * (0.1 + 0.8 * i / 60); d2 += `${1180 + Math.cos(a) * 110},${row1 + 40 - Math.sin(a) * 90} `; }
  s += `<polyline points="${d2}" stroke="${C.water}" stroke-width="22" fill="none" stroke-linecap="round"/>`;
  s += `<path d="M 1050 ${row1 + 60} C 1120 ${row1 + 30}, 1240 ${row1 + 30}, 1310 ${row1 + 60}" stroke="${C.waterD}" stroke-width="16" fill="none"/>`; }
cap(1180, row1 + 95, '牛轭湖', '新月形，宽 ≈ 原河宽');
{ lake(blob(1460, row1, 120, 50, 9, 0.35, 6)); s += `<line x1="1555" y1="${row1 - 45}" x2="1575" y2="${row1 + 45}" stroke="${C.soil}" stroke-width="9"/>`;
  for (const [x, y] of [[1420, row1 - 10], [1450, row1 + 15], [1490, row1 - 20], [1510, row1 + 10]]) s += `<line x1="${x}" y1="${y - 14}" x2="${x}" y2="${y + 6}" stroke="#5c4a36" stroke-width="3"/>`; }
cap(1470, row1 + 95, '河狸塘', '坝后浅塘，枯立木，0.1–2 ha');
{ const p = []; for (let i = 0; i < 96; i++) { const a = i / 96 * 2 * Math.PI; const r = 1 + 0.1 * Math.sin(3 * a);
    p.push([200 + Math.cos(a) * 75 * r * (Math.cos(a) > 0.55 ? 0.82 : 1), row2 + Math.sin(a) * 52 * r]); } lake(p);
  s += `<line x1="262" y1="${row2 - 48}" x2="262" y2="${row2 + 48}" stroke="${C.soil}" stroke-width="10"/>`; }
cap(200, row2 + 80, '农场池塘', '一面直土坝，< 0.6 ha');
lake(blob(470, row2, 85, 55, 6, 0.08, 7), '#c9b48a'); lake(blob(470, row2 + 6, 42, 22, 5, 0.25, 8), '#8fb0c2'); cap(470, row2 + 80, '干盐湖 playa', '平底浅盘，雨后薄水');
{ // dendritic reservoir
  const arms = [[0, 0, 220, 0, 26], [60, 0, 30, -70, 14], [110, 0, 150, 60, 14], [170, 0, 200, -60, 12], [90, 0, 70, 50, 10], [200, 0, 240, 40, 9]];
  for (const [ax0, ay0, ax1, ay1, w] of arms) s += `<line x1="${760 + ax0}" y1="${row2 + ay0}" x2="${760 + ax1}" y2="${row2 + ay1}" stroke="${C.water}" stroke-width="${w}" stroke-linecap="round"/>`;
  s += `<line x1="752" y1="${row2 - 26}" x2="752" y2="${row2 + 26}" stroke="#9a9a96" stroke-width="10"/>`; }
cap(870, row2 + 80, '水库', '淹没的河谷，一头是坝');
lake(blob(1150, row2, 60, 60, 3, 0.04, 9)); lake(blob(1260, row2 + 10, 30, 28, 3, 0.05, 10)); cap(1200, row2 + 80, '落水洞湖', '圆，佛罗里达中部');
{ lake(blob(1490, row2, 140, 55, 12, 0.3, 11)); for (let i = 0; i < 14; i++) s += `<circle cx="${1380 + i * 16}" cy="${row2 + Math.sin(i) * 30}" r="5" fill="#5f7a52"/>`; }
cap(1490, row2 + 80, '沼泽湖 / 堵塞湖', '浅，柏树或浮毯边');

s += `</svg>`;
sharp(Buffer.from(s)).png().toFile(out).then(() => console.log('ok', out));
