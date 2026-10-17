// Route shields, cardinal plates and guide-sign mock-ups for the naming sheets (concept only, not game assets).
import { f, poly, sign, star } from './svg.mjs';

export const NAVY = '#1f3d7a', RED = '#b8282e', GOLD = '#e0b23a', GREEN = '#006b45', INK = '#111';

let clipN = 0;

/** Outline of a shield family, width w, height h, top-left at 0,0. */
function outline(family, w, h) {
  switch (family) {
    case 'A': // heater shield: flat top, straight sides, curved to a point
      return `M0,0 H${f(w)} V${f(0.42 * h)} C${f(w)},${f(0.78 * h)} ${f(0.62 * w)},${f(0.93 * h)} ${f(w / 2)},${f(h)} `
        + `C${f(0.38 * w)},${f(0.93 * h)} 0,${f(0.78 * h)} 0,${f(0.42 * h)} Z`;
    case 'B': // arched tablet
      return `M0,${f(h)} V${f(0.3 * h)} A${f(w / 2)},${f(0.3 * h)} 0 0 1 ${f(w)},${f(0.3 * h)} V${f(h)} Z`;
    default: // C: hexagon, pointed sides
      return `M${f(0.2 * w)},0 H${f(0.8 * w)} L${f(w)},${f(h / 2)} L${f(0.8 * w)},${f(h)} H${f(0.2 * w)} L0,${f(h / 2)} Z`;
  }
}

/** Shield proportions (width : height). */
export const ASPECT = { A: 1 / 1.08, B: 1 / 1.1, C: 1.18 };

/** One layer of the outline, scaled about the centre by k. */
function layer(family, w, h, k, fill, extra = '') {
  return `<path d="${outline(family, w, h)}" fill="${fill}" transform="translate(${f(w / 2)},${f(h / 2)}) scale(${k}) translate(${f(-w / 2)},${f(-h / 2)})" ${extra}/>`;
}

/**
 * A shield of height h with its top-centre at (cx, top). tier 'fx' = federal expressway, 'rt' = route.
 */
export function shield(family, tier, num, cx, top, h) {
  const w = h * ASPECT[family];
  const id = 'sc' + (++clipN);
  let s = `<g transform="translate(${f(cx - w / 2)},${f(top)})">`;
  if (tier === 'rt') {
    s += layer(family, w, h, 1, INK) + layer(family, w, h, 0.93, '#fff');
    const base = family === 'B' ? 0.8 : family === 'A' ? 0.66 : 0.68;
    s += sign(w / 2, base * h, num, `font-size="${f(0.5 * h)}" fill="${INK}" text-anchor="middle"`);
    return s + '</g>';
  }
  const k = 0.89;
  const clip = `<clipPath id="${id}">${layer(family, w, h, k, '#000')}</clipPath>`;
  if (family === 'A') {
    s += clip + layer(family, w, h, 1, INK) + layer(family, w, h, 0.955, '#fff') + layer(family, w, h, k, NAVY);
    s += `<rect x="0" y="0" width="${f(w)}" height="${f(0.3 * h)}" fill="${RED}" clip-path="url(#${id})"/>`;
    s += sign(w / 2, 0.245 * h, 'FEDERAL', `font-size="${f(0.135 * h)}" fill="#fff" text-anchor="middle" letter-spacing="${f(0.01 * h)}"`);
    s += sign(w / 2, 0.73 * h, num, `font-size="${f(0.46 * h)}" fill="#fff" text-anchor="middle"`);
  } else if (family === 'B') {
    s += clip + layer(family, w, h, 1, NAVY) + layer(family, w, h, 0.93, '#fff');
    s += `<rect x="0" y="0" width="${f(w)}" height="${f(0.4 * h)}" fill="${NAVY}" clip-path="url(#${id})"/>`;
    s += `<rect x="0" y="${f(0.4 * h)}" width="${f(w)}" height="${f(0.035 * h)}" fill="${RED}" clip-path="url(#${id})"/>`;
    for (const dx of [-0.24, 0, 0.24]) s += star(w / 2 + dx * w, (dx === 0 ? 0.2 : 0.26) * h, 0.075 * h, 'fill="#fff"');
    s += sign(w / 2, 0.86 * h, num, `font-size="${f(0.42 * h)}" fill="${INK}" text-anchor="middle"`);
  } else {
    s += clip + layer(family, w, h, 1, '#fff') + layer(family, w, h, 0.95, INK) + layer(family, w, h, 0.9, NAVY);
    s += `<rect x="0" y="0" width="${f(w)}" height="${f(0.3 * h)}" fill="${GOLD}" clip-path="url(#${id})"/>`;
    s += sign(w / 2, 0.235 * h, 'FEDERAL', `font-size="${f(0.15 * h)}" fill="${NAVY}" text-anchor="middle" letter-spacing="${f(0.01 * h)}"`);
    s += sign(w / 2, 0.8 * h, num, `font-size="${f(0.47 * h)}" fill="#fff" text-anchor="middle"`);
  }
  return s + '</g>';
}

export const shieldWidth = (family, h) => h * ASPECT[family];

/** Cardinal plate above a reassurance shield: white, black border and letters, width w. */
export function cardinal(cx, top, w, word) {
  const h = w * 0.36;
  return `<rect x="${f(cx - w / 2)}" y="${f(top)}" width="${f(w)}" height="${f(h)}" rx="${f(h * 0.08)}" fill="#fff" stroke="${INK}" stroke-width="${f(h * 0.06)}"/>`
    + sign(cx, top + h * 0.74, word, `font-size="${f(h * 0.62)}" fill="${INK}" text-anchor="middle"`);
}

/** A green panel with a white inset border, sizes in metres, m px per metre. */
function panel(x, y, wm, hm, m) {
  return `<rect x="${f(x)}" y="${f(y)}" width="${f(wm * m)}" height="${f(hm * m)}" rx="${f(0.16 * m)}" fill="${GREEN}"/>`
    + `<rect x="${f(x + 0.07 * m)}" y="${f(y + 0.07 * m)}" width="${f((wm - 0.14) * m)}" height="${f((hm - 0.14) * m)}" rx="${f(0.11 * m)}" fill="none" stroke="#fff" stroke-width="${f(0.05 * m)}"/>`;
}

/** Arrow pointing up-right (entrance), length L px, at (x, y) = tail. */
function arrowUR(x, y, L, wpx) {
  const a = -Math.PI / 4, c = Math.cos(a), s = Math.sin(a), n = [-s, c];
  const head = L * 0.42, hw = wpx * 2.1;
  const tip = [x + c * L, y + s * L], neck = [x + c * (L - head), y + s * (L - head)];
  return poly([
    [x + n[0] * wpx / 2, y + n[1] * wpx / 2], [neck[0] + n[0] * wpx / 2, neck[1] + n[1] * wpx / 2],
    [neck[0] + n[0] * hw / 2, neck[1] + n[1] * hw / 2], tip,
    [neck[0] - n[0] * hw / 2, neck[1] - n[1] * hw / 2], [neck[0] - n[0] * wpx / 2, neck[1] - n[1] * wpx / 2],
    [x - n[0] * wpx / 2, y - n[1] * wpx / 2]], 'fill="#fff"');
}

/**
 * Advance exit guide sign (1 mile before the exit): exit tab, route shield + direction, destination, distance.
 * m px per metre; (x, y) top-left of the tab row.
 */
export function advanceSign(family, x, y, m, o) {
  const W = 4.9, H = 2.75, TW = 1.75, TH = 0.62;
  let s = panel(x + (W - TW) * m, y, TW, TH + 0.12, m);
  s += sign(x + (W - TW / 2) * m, y + 0.5 * m, `EXIT ${o.exit}`, `font-size="${f(0.4 * m)}" fill="#fff" text-anchor="middle"`);
  const py = y + TH * m;
  s += panel(x, py, W, H, m);
  const sh = 0.78, sw = shieldWidth(family, sh);
  const groupW = sw + 0.18 + 0.29 * o.dir.length;
  const gx = x + (W * m - groupW * m) / 2;
  s += shield(family, 'rt', o.route, gx + sw * m / 2, py + 0.28 * m, sh * m);
  s += sign(gx + (sw + 0.18) * m, py + 0.9 * m, o.dir, `font-size="${f(0.42 * m)}" fill="#fff"`);
  s += sign(x + W * m / 2, py + 1.72 * m, o.city, `font-size="${f(0.5 * m)}" fill="#fff" text-anchor="middle"`);
  s += sign(x + (W - 0.35) * m, py + 2.42 * m, o.dist, `font-size="${f(0.38 * m)}" fill="#fff" text-anchor="end"`);
  return s;
}

/** Entrance guide sign on the crossroad before an entrance ramp: expressway shield + direction, destination, arrow. */
export function entranceSign(family, x, y, m, o) {
  const W = 3.6, H = 2.6;
  let s = panel(x, y, W, H, m);
  const sh = 0.82, sw = shieldWidth(family, sh);
  const groupW = sw + 0.18 + 0.3 * o.dir.length;
  const gx = x + (W * m - groupW * m) / 2;
  s += shield(family, 'fx', o.fx, gx + sw * m / 2, y + 0.22 * m, sh * m);
  s += sign(gx + (sw + 0.18) * m, y + 0.86 * m, o.dir, `font-size="${f(0.44 * m)}" fill="#fff"`);
  s += sign(x + W * m / 2, y + 1.62 * m, o.city, `font-size="${f(0.46 * m)}" fill="#fff" text-anchor="middle"`);
  s += arrowUR(x + W * m / 2 - 0.3 * m, y + 2.42 * m, 0.72 * m, 0.13 * m);
  return s;
}
