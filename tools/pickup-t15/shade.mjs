// T15 material detail painted by the baker (tools/build-pickup-t15.mjs -> vehicle-mesh-kit bake): per material a shade(ctx)
// returning {c, sm, f0, h, ao}; ctx.p and ctx.n are in the concept frame (metres, x forward, y up, z right), h is in runtime
// units (1/16 block) and feeds the _n normal map. Only plain material structure: no painted decoration or line art.
// A texel is about 5 mm on the car (13 texels per runtime pixel): patterns stay below ~90 cycles per metre, finer noise
// only aliases into moire under nearest filtering.
import {MATS, FX, RX, R, TR} from './common.mjs';
import {DIALS} from './cab.mjs';

const fract = v => v - Math.floor(v), h3 = (a, b, c) => fract(Math.sin(a * 127.1 + b * 311.7 + c * 74.7) * 43758.5453);
function vnoise(x, y, z) {
  const xi = Math.floor(x), yi = Math.floor(y), zi = Math.floor(z), s = t => t * t * (3 - 2 * t), l = (a, b, t) => a + (b - a) * t;
  const fx = s(x - xi), fy = s(y - yi), fz = s(z - zi), v = (i, j, k) => h3(xi + i, yi + j, zi + k);
  return l(l(l(v(0, 0, 0), v(1, 0, 0), fx), l(v(0, 1, 0), v(1, 1, 0), fx), fy), l(l(v(0, 0, 1), v(1, 0, 1), fx), l(v(0, 1, 1), v(1, 1, 1), fx), fy), fz);
}
const clamp01 = v => Math.max(0, Math.min(1, v)), smooth = (a, b, v) => { const t = clamp01((v - a) / (b - a)); return t * t * (3 - 2 * t); };
const scale = (c, k) => c.map(v => Math.max(0, Math.min(255, v * k)));
const mix = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);
/** Fine grain (moulded plastic, vinyl): a little height and brightness noise at the given frequency (per metre). */
const grain = (base, f, hk, ck) => ({p}) => { f = Math.min(f, 90); const g = vnoise(p[0] * f, p[1] * f, p[2] * f); return {c: scale(base.c, 1 + ck * (g - 0.5)), h: hk * g}; };

// chrome and mirror glass: no real reflections in vanilla, so a soft sky / horizon / ground gradient by the normal
function env(m) {
  return ({n}) => {
    const y = n[1], sky = [214, 222, 232], hor = [96, 100, 106], gnd = [58, 54, 50];
    const c = y > 0 ? mix(hor, sky, smooth(0.0, 0.75, y)) : mix(hor, gnd, smooth(0.0, 0.5, -y));
    return {c: mix(c, m.c, 0.35)};
  };
}
// lamp lenses: a moulded pillow-optic grid on the lens face (the reflector pattern of a sealed-beam / composite lamp)
function optics(m, cell, depth) {
  return ({p, n}) => {
    const ax = Math.abs(n[0]) > 0.6 ? [p[2], p[1]] : Math.abs(n[2]) > 0.6 ? [p[0], p[1]] : [p[0], p[2]];
    const u = fract(ax[0] / cell) - 0.5, v = fract(ax[1] / cell) - 0.5, d = Math.hypot(u, v);
    return {h: depth * (1 - smooth(0.15, 0.5, d)), c: scale(m.c, 0.92 + 0.12 * (1 - smooth(0.1, 0.5, d)))};
  };
}
// tyre: circumferential grooves and lateral blocks across the tread, a little sidewall texture
function tire(m) {
  return ({p, n}) => {
    const hubX = Math.abs(p[0] - FX) < Math.abs(p[0] - RX) ? FX : RX, dx = p[0] - hubX, dy = p[1] - R, rad = Math.hypot(dx, dy);
    const w = Math.abs(p[2]) - TR;   // across the tread (sign-free: both sides alike)
    const radial = Math.abs(n[0] * dx / (rad || 1) + n[1] * dy / (rad || 1));
    if (rad > R - 0.012 && radial > 0.7) {
      const ang = Math.atan2(dy, dx), block = fract(ang * 52 / (2 * Math.PI) + (Math.abs(w) > 0.035 ? 0.5 : 0));
      const groove = [0, 0.035, 0.07].some(g => Math.abs(Math.abs(w) - g) < 0.006) || (Math.abs(w) > 0.012 && block < 0.12);
      return groove ? {h: -0.08, c: scale(m.c, 0.62), ao: 0.75} : {h: 0, c: scale(m.c, 1.04 + 0.05 * (vnoise(p[0] * 300, p[1] * 300, p[2] * 300) - 0.5))};
    }
    const g = vnoise(p[0] * 80, p[1] * 80, p[2] * 80);
    return {c: scale(m.c, 0.98 + 0.06 * (g - 0.5)), h: 0.01 * g};
  };
}
// woven seat cloth: the weave itself is below a texel, so only its soft brightness drift
function cloth(m) {
  return ({p, n}) => {
    const ax = Math.abs(n[1]) > 0.6 ? [p[0], p[2]] : [p[1], Math.abs(n[0]) > Math.abs(n[2]) ? p[2] : p[0]];
    const g = vnoise(p[0] * 40, p[1] * 40, p[2] * 40), g2 = vnoise(ax[0] * 85 + 3, ax[1] * 85, 0);
    return {h: 0.01 * g2, c: scale(m.c, 0.95 + 0.06 * (g2 - 0.5) + 0.06 * (g - 0.5))};
  };
}
// textured black plastic, underbody paint, liner: mottled, not flat
const FMAX = 90;
function mottle(m, f, ck, smk) {
  const f1 = Math.min(f, FMAX / 3), f2 = Math.min(f * 3, FMAX);
  return ({p}) => { const g = vnoise(p[0] * f1, p[1] * f1, p[2] * f1), g2 = vnoise(p[0] * f2 + 7, p[1] * f2, p[2] * f2);
    return {c: scale(m.c, 1 + ck * (g - 0.5) + 0.5 * ck * (g2 - 0.5)), sm: m.sm * (1 + smk * (g - 0.5)), h: 0.006 * g2}; };
}
// gauge faces: plain white tick marks around each dial's sweep (no numerals or symbols); DIALS is filled by buildCab
function dial(m) {
  const white = [214, 214, 208];
  return ({p}) => {
    for (const d of DIALS) {
      const dz = p[2] - d.c[2], dy = p[1] - d.c[1], rr = Math.hypot(dz, dy) / d.r;
      if (rr > 1) continue;
      const a = Math.atan2(dy, dz) * 180 / Math.PI, span = d.a0 - d.a1, t = (((d.a0 - a) % 360) + 360) % 360 / span;
      if (t > 1.0001) return {};
      const major = d.major, minor = d.minor, arc = span * Math.PI / 180 * d.r * rr;   // metres along the sweep
      const off = N => Math.abs(t * N - Math.round(t * N)) / N * arc;
      if (rr > 0.72 && rr < 0.94 && off(major) < 0.0013) return {c: white, sm: 120};
      if (rr > 0.84 && rr < 0.94 && off(minor) < 0.0006) return {c: white, sm: 120};
      return {};
    }
    return {};
  };
}
// drive belts: ribbed (poly-V) along the length
const ribbed = m => ({p, n}) => { const t = fract((Math.abs(n[2]) > 0.6 ? p[1] : p[2]) * 80); return {h: t < 0.5 ? 0.01 : 0, c: scale(m.c, t < 0.5 ? 1.05 : 0.9)}; };

export const SHADE = {
  chrome: env(MATS.chrome),
  mirror: env(MATS.mirror),
  lens: optics(MATS.lens, 0.014, 0.05),
  amber: optics(MATS.amber, 0.010, 0.04),
  red: optics(MATS.red, 0.010, 0.04),
  tire: tire(MATS.tire),
  cloth: cloth(MATS.cloth),
  trim: mottle(MATS.trim, 180, 0.10, 0.2),
  under: mottle(MATS.under, 30, 0.16, 0.3),
  liner: mottle(MATS.liner, 120, 0.14, 0.2),
  rubber: mottle(MATS.rubber, 90, 0.08, 0.2),
  mat: mottle(MATS.mat, 90, 0.10, 0.2),
  felt: mottle(MATS.felt, 160, 0.16, 0.2),
  iron: mottle(MATS.iron, 24, 0.30, 0.4),
  engine: mottle(MATS.engine, 26, 0.12, 0.3),
  cover: mottle(MATS.cover, 220, 0.12, 0.3),
  alu: mottle(MATS.alu, 60, 0.10, 0.3),
  dash: grain(MATS.dash, 420, 0.008, 0.06),
  pad: grain(MATS.pad, 360, 0.010, 0.06),
  panel: grain(MATS.panel, 420, 0.008, 0.05),
  vinyl: grain(MATS.vinyl, 360, 0.010, 0.06),
  belting: ribbed(MATS.belting),
  dial: dial(MATS.dial),
};
