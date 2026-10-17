// J1: the state-state T junction (C3.3b group 1): plan, the skew realignment, the minor road's landing profile.
import {f, pts, poly, line, text, sheet, dimH, dimV, leader, bubble} from './svg.mjs';

export const C = {
  turf: '#e3e9d7', formation: '#d8d2c3', asphalt: '#4e5356', asphaltNew: '#5d6467', white: '#f4f4f0', yellow: '#e2b33c',
  ink: '#23282b', red: '#b8342b', dim: '#2c6174', faint: '#8a9296',
};
// R2: lane 3.65, shoulder 2.4 -> edge 6.05; formation 7.0; fillet (curb return) 15; landing 15
const LANE = 3.65, EDGE = 6.05, FORM = 7.0, R = 15, STOP_BACK = 1.2;

/** Plan projection: metres (x east, z south) to px. */
export function proj(ox, oy, s) { return (x, z) => [ox + x * s, oy + z * s]; }

/** Wraps Chinese/mixed text to about n CJK-wide columns: never splits an ASCII word, never starts a line with punctuation. */
export function wrap(s, n) {
  const toks = s.match(/[A-Za-z0-9.%×≈≤≥°#_\-/]+|./gu);
  const w = t => [...t].reduce((a, c) => a + (c.charCodeAt(0) < 0x2000 ? 0.55 : 1), 0);
  const out = [];
  let cur = '', cw = 0;
  for (const t of toks) {
    const tw = w(t);
    if (cw + tw > n && cur && !/^[，。、）；：”」%]$/.test(t)) {
      let carry = '';
      if (/[（“「]$/.test(cur)) { carry = cur.slice(-1); cur = cur.slice(0, -1); }
      out.push(cur); cur = carry; cw = w(carry);
      if (t === ' ') continue;
    }
    cur += t; cw += tw;
  }
  if (cur) out.push(cur);
  return out;
}

/** Pavement (or formation) polygon: major band, minor band down to zEnd, two fillets of radius r around the curb-return centres. */
function outline(P, e, r, x0, x1, zTop, zEnd) {
  const cx = EDGE + R, cz = EDGE + R, a = [P(x0, zTop), P(x1, zTop), P(x1, e)];
  for (let i = 0; i <= 24; i++) { const t = -Math.PI / 2 - (i / 24) * (Math.PI / 2); a.push(P(cx + r * Math.cos(t), cz + r * Math.sin(t))); }
  a.push(P(e, zEnd), P(-e, zEnd));
  for (let i = 0; i <= 24; i++) { const t = -(i / 24) * (Math.PI / 2); a.push(P(-cx + r * Math.cos(t), cz + r * Math.sin(t))); }
  a.push(P(x0, e));
  return a;
}

function dashes(P, x0, x1, z, dash, gap, w, color, s) {
  let out = '';
  for (let x = x0; x < x1; x += dash + gap) out += line(P(x, z), P(Math.min(x + dash, x1), z), `stroke="${color}" stroke-width="${w * s}"`);
  return out;
}
function dashesV(P, z0, z1, x, dash, gap, w, color, s) {
  let out = '';
  for (let z = z0; z < z1; z += dash + gap) out += line(P(x, z), P(x, Math.min(z + dash, z1)), `stroke="${color}" stroke-width="${w * s}"`);
  return out;
}

export function stopSign(x, y, r) {
  const o = [];
  for (let i = 0; i < 8; i++) { const t = Math.PI / 8 + i * Math.PI / 4; o.push([x + r * Math.cos(t), y + r * Math.sin(t)]); }
  return poly(o, `fill="${C.red}" stroke="#fff" stroke-width="${f(r * 0.12)}"`) +
    text(x, y + r * 0.22, 'STOP', `font-size="${f(r * 0.6)}" font-weight="700" fill="#fff" text-anchor="middle"`);
}
export function warnSign(x, y, r) {
  return poly([[x, y - r], [x + r, y], [x, y + r], [x - r, y]], `fill="#f2c230" stroke="${C.ink}" stroke-width="1.2"`) +
    line([x, y + r * 0.6], [x, y - r * 0.6], `stroke="${C.ink}" stroke-width="${f(r * 0.16)}"`) +
    line([x, y + r * 0.05], [x + r * 0.45, y + r * 0.05], `stroke="${C.ink}" stroke-width="${f(r * 0.16)}"`);
}

function planView() {
  const s = 9.2, P = proj(700, 330, s);
  const X0 = -68, X1 = 68, ZT = -22, ZE = 54, cx = EDGE + R, cz = EDGE + R;
  let b = text(70, 70, '平面：州道 T 字路口（主路直行，支路在主路中段接入，停车让行）', 'font-size="22" font-weight="700" fill="#23282b"');
  b += `<rect x="${f(P(X0, ZT)[0])}" y="${f(P(X0, ZT)[1])}" width="${f((X1 - X0) * s)}" height="${f((ZE - ZT) * s)}" fill="${C.turf}"/>`;
  b += poly(outline(P, FORM, R - (FORM - EDGE), X0, X1, -FORM, ZE), `fill="${C.formation}"`);
  b += poly(outline(P, EDGE, R, X0, X1, -EDGE, ZE), `fill="${C.asphalt}"`);
  // the junction pad: from the major's edge, both fillets and the minor's first 15 m (the landing), one height function
  const pad = [P(-cx, EDGE), P(cx, EDGE)];
  for (let i = 0; i <= 24; i++) { const t = -Math.PI / 2 - (i / 24) * (Math.PI / 2); pad.push(P(cx + R * Math.cos(t), cz + R * Math.sin(t))); }
  for (let i = 0; i <= 24; i++) { const t = -(i / 24) * (Math.PI / 2); pad.push(P(-cx + R * Math.cos(t), cz + R * Math.sin(t))); }
  b += poly(pad, `fill="${C.asphaltNew}" stroke="#a9bcc3" stroke-width="1.4" stroke-dasharray="7 5"`);
  // major markings: yellow broken centre line, white edge lines, the near one broken across the mouth
  b += dashes(P, X0, X1, 0, 3, 9, 0.12, C.yellow, s);
  b += line(P(X0, -LANE), P(X1, -LANE), `stroke="${C.white}" stroke-width="${0.15 * s}"`);
  b += line(P(X0, LANE), P(-cx, LANE), `stroke="${C.white}" stroke-width="${0.15 * s}"`);
  b += line(P(cx, LANE), P(X1, LANE), `stroke="${C.white}" stroke-width="${0.15 * s}"`);
  // minor markings: edge lines from the fillet ends, double yellow for the last 30 m, broken beyond
  b += line(P(-LANE, cz), P(-LANE, ZE), `stroke="${C.white}" stroke-width="${0.15 * s}"`);
  b += line(P(LANE, cz), P(LANE, ZE), `stroke="${C.white}" stroke-width="${0.15 * s}"`);
  const zStop = EDGE + STOP_BACK;
  b += line(P(-0.12, zStop + 0.6), P(-0.12, zStop + 30), `stroke="${C.yellow}" stroke-width="${0.12 * s}"`);
  b += line(P(0.12, zStop + 0.6), P(0.12, zStop + 30), `stroke="${C.yellow}" stroke-width="${0.12 * s}"`);
  b += dashesV(P, zStop + 33, ZE, 0, 3, 9, 0.12, C.yellow, s);
  // stop bar (0.6 m) across the approach lane (right-hand traffic: the northbound lane is the east half)
  b += poly([P(0.3, zStop), P(LANE, zStop), P(LANE, zStop + 0.6), P(0.3, zStop + 0.6)], `fill="${C.white}"`);
  b += `<g stroke="#cfd5d8" stroke-width="2" fill="#cfd5d8">` +
    line(P(-40, 1.8), P(-26, 1.8), '') + poly([P(-26, 1.1), P(-24, 1.8), P(-26, 2.5)], '') +
    line(P(40, -1.8), P(26, -1.8), '') + poly([P(26, -1.1), P(24, -1.8), P(26, -2.5)], '') +
    line(P(1.8, 46), P(1.8, 34), '') + poly([P(1.1, 34), P(1.8, 32), P(2.5, 34)], '') + `</g>`;
  // sight triangle: the driver 4.4 m behind the major's edge sees 190 m each way
  const eye = P(1.8, EDGE + 4.4);
  b += line(eye, P(X0, -1.8), `stroke="${C.red}" stroke-width="1.3" stroke-dasharray="8 5"`);
  b += line(eye, P(X1, 1.8), `stroke="${C.red}" stroke-width="1.3" stroke-dasharray="8 5"`);
  b += `<circle cx="${f(eye[0])}" cy="${f(eye[1])}" r="4" fill="${C.red}"/>`;
  // STOP sign on the right, 2 m off the edge, level with the stop bar
  const sp = P(EDGE + 2.6, zStop + 0.5);
  b += line(sp, [sp[0] + 70, sp[1] + 40], `stroke="${C.ink}" stroke-width="0.8"`);
  b += `<circle cx="${f(sp[0])}" cy="${f(sp[1])}" r="3.2" fill="${C.ink}"/>` + stopSign(sp[0] + 92, sp[1] + 50, 22);
  // labels
  b += text(P(X0, 0)[0] + 10, P(0, -16)[1], '主路：线形、纵断面、横坡、标线都不变，只在口子处断开边线', 'font-size="15" fill="#23282b"');
  b += text(P(X0, 0)[0] + 10, P(0, -10.5)[1], '红虚线：视距三角形。停车线后 4.4 m 的驾驶员看两边各 190 m（左转出口 7.5 s × 90 km/h），三角形内不长树、灌木和高草', `font-size="13.5" fill="${C.red}"`);
  b += leader(P(-30, LANE), P(-40, 15), '主路边线在口子处断开', 'end');
  b += leader(P(-cx + R * Math.cos(-Math.PI / 4), cz + R * Math.sin(-Math.PI / 4)), P(-40, 31), '转角半径 R 15（大货车右转不出铺装）', 'end');
  b += leader(P(-0.12, zStop + 24), P(-40, 45), '支路最后 30 m：双黄实线（不许超车）', 'end');
  b += leader(P(2.2, zStop + 0.3), P(30, 33), '停车线 0.6 宽，离主路铺装边 1.2', 'start');
  b += leader(P(9, EDGE + 1.5), P(30, 20), '路口面（虚线框）：主路铺装边高程 + 落坡', 'start');
  b += text(sp[0] + 122, sp[1] + 56, '右侧 STOP 牌，离路边 2 m', 'font-size="13.5" fill="#23282b"');
  // dimensions
  b += dimV(P(X1, 0)[0] + 26, P(X1, -EDGE)[1], P(X1, EDGE)[1], '12.1');
  b += dimV(P(X1, 0)[0] + 58, P(X1, -LANE)[1], P(X1, 0)[1], '3.65');
  b += dimH(P(-EDGE, 0)[0], P(EDGE, 0)[0], P(0, ZE)[1] + 22, '12.1', P(0, ZE)[1]);
  b += dimV(P(-31, 0)[0], P(0, EDGE)[1], P(0, cz)[1], '落坡 15');
  return b;
}

/** Inset A: a skewed meeting realigned to come in square. */
function skewInset(x0, y0, w, h) {
  let b = `<rect x="${x0}" y="${y0}" width="${w}" height="${h}" fill="#f4f6f1" stroke="#c9cec6"/>`;
  b += text(x0 + 16, y0 + 30, '斜交改直：交角小于 70° 时，支路最后约 120 m 改线，垂直进入', 'font-size="17" font-weight="700" fill="#23282b"');
  const s = 2.9, P = proj(x0 + w * 0.7, y0 + 100, s);
  b += `<rect x="${x0 + 10}" y="${f(P(0, -EDGE)[1])}" width="${w - 20}" height="${f(2 * EDGE * s)}" fill="${C.asphalt}"/>`;
  b += line([x0 + 10, P(0, 0)[1]], [x0 + w - 10, P(0, 0)[1]], `stroke="${C.yellow}" stroke-width="1" stroke-dasharray="6 10"`);
  // the planner's 35 deg approach and the realigned one
  const a = 35 * Math.PI / 180, dir = [-Math.cos(a), Math.sin(a)], at = t => [dir[0] * t, dir[1] * t];
  const p0 = at(140), xm = -52, q = [xm, 30];
  const c1 = [p0[0] - dir[0] * 55, p0[1] - dir[1] * 55], c2 = [q[0], q[1] + 45];
  const curve = [];
  for (let i = 0; i <= 40; i++) {
    const t = i / 40, u = 1 - t;
    curve.push(P(u * u * u * p0[0] + 3 * u * u * t * c1[0] + 3 * u * t * t * c2[0] + t * t * t * q[0],
      u * u * u * p0[1] + 3 * u * u * t * c1[1] + 3 * u * t * t * c2[1] + t * t * t * q[1]));
  }
  const path = [P(...at(185)), ...curve, P(xm, EDGE)];
  b += `<polyline points="${pts(path)}" fill="none" stroke="${C.asphalt}" stroke-width="${f(2 * EDGE * s)}" stroke-linejoin="round"/>`;
  b += `<polyline points="${pts([P(0, EDGE), P(...at(140))])}" fill="none" stroke="${C.red}" stroke-width="2" stroke-dasharray="8 6"/>`;
  b += leader(P(...at(60)), [P(...at(60))[0] + 130, P(...at(60))[1] + 40], '规划线 35°（不建）', 'start');
  b += leader(P(xm, 18), [P(xm, 18)[0] - 120, P(xm, 18)[1] + 10], '最后 25 m 直线，垂直进入', 'end');
  b += leader(curve[22], [curve[22][0] + 150, curve[22][1] + 20], '改线 R ≥ 60 m', 'start');
  b += leader(P(...p0), [P(...p0)[0] + 60, P(...p0)[1] + 50], '从这里起改（离路口约 120 m）', 'start');
  b += text(x0 + 16, y0 + h - 40, '交角 70°–90°：不改线，只做转角和落坡。', 'font-size="14" fill="#4a5256"');
  b += text(x0 + 16, y0 + h - 18, '改线放不下时（地形、桥、隧道），交点沿主路挪动；主路不动。', 'font-size="14" fill="#4a5256"');
  return b;
}

/** Inset B: the minor road's profile into the junction. */
function profileInset(x0, y0, w, h) {
  let b = `<rect x="${x0}" y="${y0}" width="${w}" height="${h}" fill="#f4f6f1" stroke="#c9cec6"/>`;
  b += text(x0 + 16, y0 + 30, '支路纵断面（竖向放大 8 倍）', 'font-size="17" font-weight="700" fill="#23282b"');
  const sx = (w - 110) / 130, sy = 8 * sx, X = d => x0 + 60 + (d + 10) * sx, Y = hgt => y0 + h - 150 - hgt * sy;
  const gL = 0.015, g = 0.045, L = 15, vc = 30;
  const prof = d => {
    if (d <= L) return d * gL;
    if (d <= L + vc) { const t = d - L; return L * gL + t * gL + (g - gL) * t * t / (2 * vc); }
    return L * gL + vc * gL + (g - gL) * vc / 2 + (d - L - vc) * g;
  };
  const pp = [];
  for (let d = 0; d <= 120; d += 1) pp.push([X(d), Y(prof(d))]);
  // the major's half section (crown 2 % above its edge) to the left of the mouth
  b += line([X(-10), Y(6.05 * 0.02)], [X(0), Y(0)], `stroke="${C.faint}" stroke-width="5"`);
  b += `<polyline points="${pts(pp)}" fill="none" stroke="${C.asphalt}" stroke-width="5"/>`;
  for (const d of [0, L, L + vc]) b += line([X(d), Y(0) + 30], [X(d), Y(prof(d) + 0.9)], `stroke="${C.dim}" stroke-width="1" stroke-dasharray="4 4"`);
  b += dimH(X(0), X(L), Y(0) + 40, '15', null);
  b += dimH(X(L), X(L + vc), Y(0) + 40, '30', null);
  b += text(X(-5), Y(0) + 72, '主路', `font-size="13.5" fill="${C.dim}" text-anchor="middle"`);
  b += text(X(L / 2), Y(0) + 72, '落坡 ≤ 2 %', `font-size="13.5" fill="${C.dim}" text-anchor="middle"`);
  b += text(X(L + vc / 2), Y(0) + 72, '竖曲线', `font-size="13.5" fill="${C.dim}" text-anchor="middle"`);
  b += leader([X(90), Y(prof(90))], [X(70), Y(prof(90)) - 70], '支路原纵坡（州道 ≤ 5 %，山区 ≤ 7 %）', 'end');
  b += leader([X(0), Y(0)], [X(4), Y(0) - 110], '起点高程 = 主路铺装边在这一点的高程', 'start');
  b += text(x0 + 16, y0 + h - 40, '主路不为支路改高程；支路高差大时，落坡前的一段按州道纵坡重新拉坡。', 'font-size="14" fill="#4a5256"');
  b += text(x0 + 16, y0 + h - 18, '落坡 ≤ 2 %：停车起步不溜车，大货车停在停车线时车尾不扫地。', 'font-size="14" fill="#4a5256"');
  return b;
}

function notes(x0, y0) {
  const items = [
    '主路（直行）照旧：线形、纵断面、横坡、标线都按原设计，只在口子处断开边线。',
    '支路接到主路铺装边：最后 15 m 是落坡段（≤ 2 %），高程从主路铺装边起算，再用竖曲线接回原纵坡。',
    '转角半径 15 m：大货车右转不出铺装。两个转角和落坡段合成一块“路口面”，用一个高度函数。',
    '支路：停车线（0.6 m，离主路铺装边 1.2 m）、最后 30 m 双黄实线、右侧 STOP 牌（离路边 2 m）。',
    '主路两个方向，路口前 150 m 各一块“侧向路口”黄色警告牌。',
    '视距三角形（左转出口 7.5 s × 90 km/h ≈ 190 m）内不长树、灌木和高草。',
    '交角小于 70°：支路最后约 120 m 改线，垂直进入（R ≥ 60 m，停车前已减速）。',
    '路口面的碰撞方块、路面网格和接地查询（RoadSurfaceQuery）用同一个高度函数，车开过不跳。',
    '路牌和路口标线跟隧道部件一样可拆；版本锁升 surface15 / road_works#7（旧联邦世界被锁，要换新世界）。',
  ];
  let b = text(x0, y0, '做法', 'font-size="20" font-weight="700" fill="#23282b"');
  let y = y0 + 36;
  items.forEach((s, i) => {
    b += bubble(x0 + 11, y - 5, i + 1);
    const lines = wrap(s, 23);
    lines.forEach((l, j) => { b += text(x0 + 32, y + j * 21, l, 'font-size="14.5" fill="#23282b"'); });
    y += lines.length * 21 + 14;
  });
  b += stopSign(x0 + 40, y + 30, 26) + text(x0 + 78, y + 36, 'STOP（R1-1 式样，0.75 m）', 'font-size="14" fill="#23282b"');
  b += warnSign(x0 + 40, y + 98, 28) + text(x0 + 78, y + 104, '侧向路口警告（W2-2 式样，0.9 m）', 'font-size="14" fill="#23282b"');
  return b;
}

export function tSheet() {
  const W = 2000, H = 1560;
  let body = planView();
  body += notes(1460, 100);
  body += skewInset(40, 880, 1000, 590);
  body += profileInset(1060, 880, 900, 590);
  return sheet(W, H, '州道 T 字路口：平面、斜交改直、支路纵断面', 'J1', body);
}
