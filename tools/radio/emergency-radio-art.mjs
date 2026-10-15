// Emergency Radio V1 (docs/models/emergency_radio_v1.md, 2026-10-14): the textures of the hand-crank emergency radio
// (tools/build-emergency-radio-v1.mjs), forest start home B M2 F4. The look follows the user's paid cube model and its
// concept sheet ("地堡收音机" BR-10, src/main/blockbench/emergency_radio_cube_v1.bbmodel): a grey-green painted steel case
// worn bare on its edges, a black slotted speaker plate, an FM / AM dial window with a red needle, black knurled knobs, a
// red power lamp, a steel strap handle and a chrome telescopic antenna.
// 240 texels = 1 m, LabPBR like cabin-art (renderX). AFL style: clean and quiet, the mesh carries the bevels, the paint is
// dielectric (F0 10), only the small bare-steel parts are metal; no painted decoration (no panel lettering). The dial is
// the one exception to the density: its own 448 x 128 sprite over the 16.8 x 4.8 cm window, because the scale's figures
// are what a radio's face is (functional print, not decoration). The lit dial and lamp are "_glow" textures: the cabin
// mesh model draws them at full light, the LabPBR emission (_s alpha) lights them under a shader pack.
import {png} from '../cube-slab-mesh-lib.mjs';
import {clamp, sstep, mul, mix, vn} from '../mesh-trees/hd-art.mjs';

/** The case: grey-green enamel on steel, satin, a faint large mottle and a few soft, slightly greyer worn patches. */
export function casePaint() {
  return (x, y) => {
    let c = mul([72, 77, 67], 1 + 0.035 * vn(x, y, 9000, 60) + 0.02 * vn(x, y, 9001, 24));
    c = mix(c, [86, 88, 82], 0.25 * sstep(0.45, 1, vn(x, y, 9002, 80, 40)));
    return {c, s: 104, f0: 10, p: 0, h: 0, ao: 1};
  };
}
/** The case's bevels: the paint worn through to steel (metal, a small part: catches the light the way worn edges do). */
export function caseEdge() {
  return (x, y) => ({c: mul([112, 113, 108], 1 + 0.04 * vn(x, y, 9010, 30)), s: 120, f0: 230, p: 0, h: 0, ao: 1});
}
/** Black satin steel: the speaker plate, the dial bezel, the switch plate, the handle and its feet, the crank hub. */
export function trim() {
  return (x, y) => ({c: mul([38, 39, 39], 1 + 0.04 * vn(x, y, 9020, 40)), s: 100, f0: 10, p: 0, h: 0, ao: 1});
}
/** The speaker cloth behind the slots and the slots' floors: near-black, matte. */
export function cloth() {
  return (x, y) => ({c: mul([24, 23, 22], 1 + 0.05 * vn(x, y, 9030, 12)), s: 14, f0: 10, p: 30, h: 0, ao: 1});
}
/** Black bakelite (the knobs, the slide switch, the crank knob): a little gloss. */
export function knob() {
  return (x, y) => ({c: mul([29, 28, 27], 1 + 0.03 * vn(x, y, 9040, 40)), s: 150, f0: 10, p: 0, h: 0, ao: 1});
}
/** The knobs' pointer inserts: cream. */
export function pointer() {
  return (x, y) => ({c: [218, 210, 188], s: 140, f0: 10, p: 0, h: 0, ao: 1});
}
/** Bare steel: screw heads, the label plate, the crank arm. */
export function steel() {
  return (x, y) => ({c: mul([128, 128, 124], 1 + 0.03 * vn(x, y, 9050, 30)), s: 130, f0: 230, p: 0, h: 0, ao: 1});
}
/** Chrome: the telescopic antenna and the lamp bezel. */
export function chrome() {
  return (x, y) => ({c: mul([178, 178, 174], 1 + 0.015 * vn(x, y, 9060, 40)), s: 215, f0: 230, p: 0, h: 0, ao: 1});
}
/** The power lamp's lens, dark (off) and lit (glow): red glass. */
export function lamp(lit) {
  return () => lit ? {c: [255, 74, 52], s: 230, f0: 10, p: 0, h: 0, ao: 1, e: 1} : {c: [88, 24, 20], s: 230, f0: 10, p: 0, h: 0, ao: 1};
}
/** The dial needle: red. */
export function needle() {
  return () => ({c: [192, 40, 30], s: 140, f0: 10, p: 0, h: 0, ao: 1});
}

// ------------------------------------------------------------------------------------------------- the dial

/** The dial sprite: 448 x 128 texels (both multiples of 16: an odd size would lower the block atlas' mip levels). */
export const DIAL_W = 448, DIAL_H = 128;
/**
 * FM 88..108 MHz from x 78 to 360 (linear); AM 530..1600 kHz over the same span, compressed toward the top like a
 * capacitor-tuned scale (piecewise between its labelled points, laid so no figure of either row sits under the needle).
 */
const FM0 = 78, FM1 = 360, AM_AT = [[530, 78], [700, 140], [900, 192], [1200, 268], [1600, 360]];
export const fmX = f => FM0 + (FM1 - FM0) * (f - 88) / 20;
const AM = f => { let k = 0; while (k < AM_AT.length - 2 && f > AM_AT[k + 1][0]) k++; const [f0, x0] = AM_AT[k], [f1, x1] = AM_AT[k + 1]; return x0 + (x1 - x0) * (f - f0) / (f1 - f0); };
/** Where the needle stands (V1: no tuning yet; FM 98, between the figures as on the concept sheet): u across the window, 0 at the left. */
export const NEEDLE_U = fmX(98) / DIAL_W;

/** A stroke font (unit box, y down): polylines per glyph; digits 0.72 wide, letters as given. */
const ellipse = (cx, cy, rx, ry, n = 20) => Array.from({length: n + 1}, (_, i) => [cx + rx * Math.cos(i / n * Math.PI * 2), cy + ry * Math.sin(i / n * Math.PI * 2)]);
const SIX = [[0.85, 0.08], [0.6, 0], [0.35, 0.03], [0.12, 0.22], [0.03, 0.55], [0.05, 0.8], [0.25, 0.98], [0.55, 1], [0.85, 0.9], [0.97, 0.7],
  [0.88, 0.5], [0.6, 0.4], [0.3, 0.45], [0.05, 0.62]];
const GLYPHS = {
  0: {w: 0.72, s: [ellipse(0.5, 0.5, 0.48, 0.5)]},
  1: {w: 0.72, s: [[[0.28, 0.2], [0.58, 0], [0.58, 1]]]},
  2: {w: 0.72, s: [[[0.05, 0.25], [0.15, 0.08], [0.35, 0], [0.65, 0], [0.85, 0.08], [0.95, 0.25], [0.9, 0.42], [0.05, 1], [0.97, 1]]]},
  3: {w: 0.72, s: [[[0.05, 0.1], [0.3, 0], [0.7, 0], [0.92, 0.12], [0.95, 0.28], [0.8, 0.43], [0.45, 0.48], [0.8, 0.53], [0.97, 0.7], [0.95, 0.86],
    [0.75, 0.98], [0.3, 1], [0.05, 0.9]]]},
  4: {w: 0.72, s: [[[0.72, 1], [0.72, 0], [0.02, 0.7], [1, 0.7]]]},
  5: {w: 0.72, s: [[[0.9, 0], [0.15, 0], [0.08, 0.45], [0.45, 0.38], [0.8, 0.45], [0.97, 0.65], [0.92, 0.88], [0.65, 1], [0.3, 1], [0.05, 0.9]]]},
  6: {w: 0.72, s: [SIX]},
  7: {w: 0.72, s: [[[0.03, 0], [0.97, 0], [0.4, 1]]]},
  8: {w: 0.72, s: [ellipse(0.5, 0.25, 0.38, 0.25), ellipse(0.5, 0.73, 0.46, 0.27)]},
  9: {w: 0.72, s: [SIX.map(([x, y]) => [1 - x, 1 - y])]},
  F: {w: 0.62, s: [[[0.92, 0], [0.1, 0], [0.1, 1]], [[0.1, 0.48], [0.75, 0.48]]]},
  M: {w: 0.82, s: [[[0.05, 1], [0.05, 0], [0.5, 0.62], [0.95, 0], [0.95, 1]]]},
  A: {w: 0.76, s: [[[0.02, 1], [0.5, 0], [0.98, 1]], [[0.2, 0.64], [0.8, 0.64]]]},
  H: {w: 0.74, s: [[[0.08, 0], [0.08, 1]], [[0.92, 0], [0.92, 1]], [[0.08, 0.5], [0.92, 0.5]]]},
  k: {w: 0.6, s: [[[0.1, 0], [0.1, 1]], [[0.88, 0.4], [0.1, 0.76]], [[0.4, 0.62], [0.92, 1]]]},
  z: {w: 0.58, s: [[[0.08, 0.42], [0.92, 0.42], [0.08, 1], [0.92, 1]]]},
};
/** Segments of a text set centred at cx (or from x0 when align is 'left' / to it when 'right'), top y, glyph height h. */
function text(str, at, y, h, align = 'centre') {
  const gap = 0.2, ws = [...str].map(ch => GLYPHS[ch].w), total = (ws.reduce((a, b) => a + b, 0) + gap * (ws.length - 1)) * h;
  let x = align === 'left' ? at : align === 'right' ? at - total : at - total / 2;
  const segs = [];
  [...str].forEach((ch, i) => {
    const g = GLYPHS[ch], w = g.w * h;
    for (const line of g.s) for (let k = 0; k + 1 < line.length; k++)
      segs.push([x + line[k][0] * w, y + line[k][1] * h, x + line[k + 1][0] * w, y + line[k + 1][1] * h]);
    x += w + gap * h;
  });
  return segs;
}
function segDist(px, py, [ax, ay, bx, by]) {
  const dx = bx - ax, dy = by - ay, t = clamp(((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy || 1), 0, 1);
  return Math.hypot(px - ax - t * dx, py - ay - t * dy);
}
/** The dial's ink: the figures and labels (strokes, half width 1.25 texels) and the scale (rectangles). */
function dialInk() {
  const LINE = 64, segs = [], rects = [];
  const GH = 16, GH_SMALL = 12;
  for (let f = 88; f <= 108; f++) {
    const major = f % 4 === 0, x = fmX(f);
    rects.push([x - 1.1, LINE - (major ? 13 : 7), x + 1.1, LINE]);
    if (major) segs.push(...text(String(f), x, 26, GH));
  }
  for (const [f, label] of [[530, true], [600, false], [700, true], [800, false], [900, true], [1000, false], [1200, true], [1400, false], [1600, true]]) {
    const x = AM(f);
    rects.push([x - 1.1, LINE, x + 1.1, LINE + (label ? 13 : 7)]);
    if (label) segs.push(...text(String(f), x, 86, GH));
  }
  rects.push([FM0 - 8, LINE - 1.2, FM1 + 8, LINE + 1.2]);
  segs.push(...text('FM', 12, 26, GH, 'left'), ...text('AM', 12, 86, GH, 'left'));
  segs.push(...text('MHz', DIAL_W - 10, 30, GH_SMALL, 'right'), ...text('kHz', DIAL_W - 10, 90, GH_SMALL, 'right'));
  return {segs, rects};
}
/** Coverage of the ink at a point (0..1, the strokes' soft edge over one texel). */
function inkAt(ink, x, y) {
  let d = Infinity;
  for (const s of ink.segs) { if (Math.min(s[0], s[2]) - 3 > x || Math.max(s[0], s[2]) + 3 < x || Math.min(s[1], s[3]) - 3 > y || Math.max(s[1], s[3]) + 3 < y) continue; d = Math.min(d, segDist(x, y, s)); }
  const stroke = 1 - sstep(1.0, 1.6, d);
  let rect = 0;
  for (const [x0, y0, x1, y1] of ink.rects) if (x >= x0 && x <= x1 && y >= y0 && y <= y1) rect = 1;
  return Math.max(stroke, rect);
}
/**
 * The dial face: aged cream printed in near-black (off), the same lit warm amber from behind (lit: emission on the face,
 * none on the ink); the bezel's shadow darkens the last 10 texels round the edge. Glossy: the face sits under glass.
 * Returns [colour, _s, _n] PNGs of DIAL_W x DIAL_H.
 */
export function renderDial(lit) {
  const ink = dialInk(), W = DIAL_W, H = DIAL_H, SS = 3;
  const col = Buffer.alloc(W * H * 4), spec = Buffer.alloc(W * H * 4), nrm = Buffer.alloc(W * H * 4);
  const face = lit ? [255, 200, 118] : [206, 196, 168], INK = [38, 32, 28];
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    let cov = 0;
    for (let sy = 0; sy < SS; sy++) for (let sx = 0; sx < SS; sx++) cov += inkAt(ink, x + (sx + 0.5) / SS, y + (sy + 0.5) / SS);
    cov /= SS * SS;
    const edge = Math.min(x + 0.5, W - x - 0.5, y + 0.5, H - y - 0.5), shade = 0.8 + 0.2 * sstep(0, 10, edge);
    const c = mix(mul(face, (lit ? 0.9 + 0.1 * shade : shade) * (1 + 0.012 * vn(x % 240, y, 9070, 60))), INK, cov), o = (y * W + x) * 4;
    for (let i = 0; i < 3; i++) col[o + i] = clamp(Math.round(c[i]), 0, 255);
    col[o + 3] = 255;
    spec[o] = 205; spec[o + 1] = 10; spec[o + 2] = 0;
    spec[o + 3] = lit ? clamp(Math.round(254 * 0.85 * (1 - cov) * (0.75 + 0.25 * shade)), 0, 254) : 255;
    nrm[o] = 128; nrm[o + 1] = 128; nrm[o + 2] = 255; nrm[o + 3] = 255;
  }
  return [png(col, W, H), png(spec, W, H), png(nrm, W, H)];
}

/** Every 240-texel radio texture: name -> maker. Paths: textures/block/radio/<name>.png (+ _s, _n); no height (small parts). */
export const RADIO_TEXTURES = {
  case_paint: casePaint, case_edge: caseEdge, trim, cloth, knob, pointer, steel, chrome,
  lamp: () => lamp(false), lamp_glow: () => lamp(true), needle,
};
