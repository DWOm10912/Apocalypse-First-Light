// A small software renderer for the boulder previews (tools/boulders/build-boulders.mjs --preview): perspective camera,
// z-buffer, sun with a shadow map, sky light, per-triangle box projection of a tiling sprite (the same mapping the game
// mesh will use, 128 texels per metre, nearest texels) and its normal map. Light is computed in linear space. It shows
// shape and material; it is not the game's renderer (no vanilla direction shading, no shader pack).
import {V} from './boulder-shapes.mjs';
const {add, sub, mul, dot, cross, norm} = V;

const lin = c => Math.pow(c, 2.2), srgb = c => Math.pow(Math.max(0, Math.min(1, c)), 1 / 2.2);

/**
 * A triangle soup ready to draw: per corner world position, world normal, object position (for the sprite projection);
 * per triangle the projection axis and the material ({sprite} or {ground: f(x, z) -> rgb}).
 */
export function soup() { return []; }

/** Adds mesh m (object space, metres) turned by yaw about y and moved to pos, with corner normals creased at 40 degrees. */
export function addMesh(S, m, {yaw = 0, pos = [0, 0, 0], sprite}) {
  const c = Math.cos(yaw), s = Math.sin(yaw), rot = ([x, y, z]) => [x * c + z * s, y, -x * s + z * c];
  const FN = m.T.map(([a, b, d]) => cross(sub(m.P[b], m.P[a]), sub(m.P[d], m.P[a])));
  const at = m.P.map(() => []);
  m.T.forEach((t, f) => t.forEach(i => at[i].push(f)));
  const crease = Math.cos(40 * Math.PI / 180);
  m.T.forEach((t, f) => {
    const fn = norm(FN[f]);
    if (!isFinite(fn[0]) || Math.hypot(...FN[f]) < 1e-12) return;
    const N = t.map(i => norm(rot(at[i].reduce((acc, g) => (dot(norm(FN[g]), fn) > crease ? add(acc, FN[g]) : acc), [0, 0, 0]))));
    const ax = Math.abs(fn[0]) > Math.abs(fn[1]) ? (Math.abs(fn[0]) > Math.abs(fn[2]) ? 0 : 2) : (Math.abs(fn[1]) > Math.abs(fn[2]) ? 1 : 2);
    S.push({W: t.map(i => add(rot(m.P[i]), pos)), N, O: t.map(i => m.P[i]), ax, sign: Math.sign(fn[ax]) || 1, yaw, sprite});
  });
}

/** Adds level ground (size x size m around centre, in 0.5 m squares so none crosses the camera) drawn by f(x, z) -> sRGB. */
export function addGround(S, centre, size, f) {
  const n = [0, 1, 0], step = 0.5, k = Math.round(size / step), x0 = centre[0] - size / 2, z0 = centre[2] - size / 2;
  for (let j = 0; j < k; j++) for (let i = 0; i < k; i++) {
    const x = x0 + i * step, z = z0 + j * step, p = [[x, 0, z], [x, 0, z + step], [x + step, 0, z + step], [x + step, 0, z]];
    for (const t of [[0, 1, 2], [0, 2, 3]]) S.push({W: t.map(q => p[q]), N: [n, n, n], O: t.map(q => p[q]), ground: f, noShadowCast: true});
  }
}

/** Sprite lookup: sRGB colour and tangent-space normal (OpenGL) at object position o for triangle t. */
function spriteAt(sp, t, o) {
  const [u, v] = t.ax === 0 ? [o[2] * -t.sign, o[1]] : t.ax === 1 ? [o[0], o[2] * -t.sign] : [o[0] * t.sign, o[1]];
  const R = sp.res, x = ((Math.floor(u / sp.tile * R) % R) + R) % R, y = ((Math.floor(-v / sp.tile * R) % R) + R) % R;
  const i = (y * R + x) * 3;
  return {c: [sp.col[i], sp.col[i + 1], sp.col[i + 2]], n: [sp.nrm[i], sp.nrm[i + 1], sp.nrm[i + 2]]};
}
/** Object-space directions of increasing u and v (v = up in the image, the OpenGL normal map's green) per projection. */
const frame = (ax, s) => (ax === 0 ? [[0, 0, -s], [0, 1, 0]] : ax === 1 ? [[1, 0, 0], [0, 0, -s]] : [[s, 0, 0], [0, 1, 0]]);

/**
 * Renders soup S to an sRGB 8-bit RGB buffer of w x h (after ss x ss supersampling), camera at eye looking at target.
 * light: {sun: unit vector towards the sun}.
 */
export function render(S, {w, h, ss = 3, eye, target, fov = 30, sun = norm([-0.55, 0.78, -0.3]), sky = [0.62, 0.7, 0.82]}) {
  const W = w * ss, H = h * ss;
  const f = norm(sub(target, eye)), r = norm(cross(f, [0, 1, 0])), u = cross(r, f), fp = (H / 2) / Math.tan(fov * Math.PI / 360);
  const proj = p => { const q = sub(p, eye), z = dot(q, f); return [W / 2 + dot(q, r) / z * fp, H / 2 - dot(q, u) / z * fp, z]; };

  // shadow map: orthographic along the sun over the casters' bounds
  const su = norm(cross(sun, Math.abs(sun[1]) > 0.9 ? [1, 0, 0] : [0, 1, 0])), sv = cross(sun, su), SM = 1536;
  let lo = [1e9, 1e9], hi = [-1e9, -1e9];
  for (const t of S) if (!t.noShadowCast) for (const p of t.W) { const a = dot(p, su), b = dot(p, sv); lo = [Math.min(lo[0], a), Math.min(lo[1], b)]; hi = [Math.max(hi[0], a), Math.max(hi[1], b)]; }
  const ext = Math.max(hi[0] - lo[0], hi[1] - lo[1]) * 1.1 + 0.5, sc = SM / ext, o0 = [(lo[0] + hi[0]) / 2 - ext / 2, (lo[1] + hi[1]) / 2 - ext / 2];
  const sDepth = new Float32Array(SM * SM).fill(-1e9);
  const sProj = p => [(dot(p, su) - o0[0]) * sc, (dot(p, sv) - o0[1]) * sc, dot(p, sun)];
  for (const t of S) {
    if (t.noShadowCast) continue;
    const q = t.W.map(sProj);
    raster(q, SM, SM, (x, y, b) => { const d = b[0] * q[0][2] + b[1] * q[1][2] + b[2] * q[2][2], k = y * SM + x; if (d > sDepth[k]) sDepth[k] = d; }, false);
  }
  const lit = p => {
    const [x, y, d] = sProj(p);
    let s = 0;
    for (let j = -1; j <= 1; j++) for (let i = -1; i <= 1; i++) {
      const xi = Math.floor(x) + i, yi = Math.floor(y) + j;
      if (xi < 0 || yi < 0 || xi >= SM || yi >= SM) { s++; continue; }
      s += d + 0.02 >= sDepth[yi * SM + xi] ? 1 : 0;
    }
    return s / 9;
  };

  const zb = new Float32Array(W * H).fill(1e9), out = new Float32Array(W * H * 3);
  for (let y = 0; y < H; y++) {
    const k = 1 - y / H, c = [0.56 + 0.2 * k, 0.66 + 0.16 * k, 0.8 + 0.1 * k];
    for (let x = 0; x < W; x++) out.set(c, (y * W + x) * 3);
  }
  for (const t of S) {
    const q = t.W.map(proj);
    if (q.some(p => p[2] < 0.05)) continue;
    raster(q, W, H, (x, y, b) => {
      // perspective-correct barycentrics
      const iz = b.map((v, i) => v / q[i][2]), s = iz[0] + iz[1] + iz[2], z = 1 / s, k = y * W + x;
      if (z >= zb[k]) return;
      zb[k] = z;
      const B = iz.map(v => v / s), mix3 = A => add(add(mul(A[0], B[0]), mul(A[1], B[1])), mul(A[2], B[2]));
      const p = mix3(t.W), o = mix3(t.O);
      let n = norm(mix3(t.N)), col;
      if (t.ground) col = t.ground(p[0], p[2]);
      else {
        const sp = spriteAt(t.sprite, t, o);
        col = sp.c;
        // tangent frame from the projection axes, turned like the mesh, made orthogonal to the normal
        const [ta, ba] = frame(t.ax, t.sign), c = Math.cos(t.yaw), sn = Math.sin(t.yaw), rot = ([x, y, z]) => [x * c + z * sn, y, -x * sn + z * c];
        let T = rot(ta), Bt = rot(ba);
        T = norm(sub(T, mul(n, dot(T, n)))); Bt = norm(sub(Bt, mul(n, dot(Bt, n))));
        n = norm(add(add(mul(T, sp.n[0]), mul(Bt, sp.n[1])), mul(n, sp.n[2])));
      }
      const ndl = Math.max(0, dot(n, sun)), sh = ndl > 0 ? lit(p) : 0, amb = 0.42 * (0.55 + 0.45 * n[1]) + 0.06;
      const L = [0, 1, 2].map(i => 1.25 * ndl * sh * [1, 0.96, 0.9][i] + amb * sky[i] / 0.7);
      out.set([0, 1, 2].map(i => srgb(lin(col[i]) * L[i])), k * 3);
    }, true);
  }
  // box filter down to w x h
  const res = Buffer.alloc(w * h * 3);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) for (let c = 0; c < 3; c++) {
    let s = 0;
    for (let j = 0; j < ss; j++) for (let i = 0; i < ss; i++) s += lin(out[((y * ss + j) * W + x * ss + i) * 3 + c]);
    res[(y * w + x) * 3 + c] = Math.round(255 * srgb(s / (ss * ss)));
  }
  return res;
}

/** Scan-converts screen triangle q ([x, y, z] x 3) into pixel centres, calling px(x, y, barycentrics); cull: back faces. */
function raster(q, W, H, px, cull) {
  const [a, b, c] = q, area = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
  if (Math.abs(area) < 1e-9 || (cull && area > 0)) return;
  const x0 = Math.max(0, Math.floor(Math.min(a[0], b[0], c[0]))), x1 = Math.min(W - 1, Math.ceil(Math.max(a[0], b[0], c[0])));
  const y0 = Math.max(0, Math.floor(Math.min(a[1], b[1], c[1]))), y1 = Math.min(H - 1, Math.ceil(Math.max(a[1], b[1], c[1])));
  for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
    const px_ = x + 0.5, py = y + 0.5;
    const w0 = ((b[0] - px_) * (c[1] - py) - (b[1] - py) * (c[0] - px_)) / area;
    const w1 = ((c[0] - px_) * (a[1] - py) - (c[1] - py) * (a[0] - px_)) / area;
    const w2 = 1 - w0 - w1;
    if (w0 < 0 || w1 < 0 || w2 < 0) continue;
    px(x, y, [w0, w1, w2]);
  }
}
