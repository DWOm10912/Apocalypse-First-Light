// Town House A V1 (docs/worldgen/town_buildings_blocks_v1.md, 2026-10-21): the 240-texel LabPBR textures of the small
// town house's blocks (tools/build-town-house-v1.mjs). The same density and quiet as the forest home's set
// (tools/cabin/cabin-art.mjs): 240 texels = 1 m, every texture tiles both ways, colour steps small, relief only where the
// material has it (lap siding steps, brick joints, shingle courses, joists), grain and grit in the albedo only, nothing
// finer than 5 texels in the normal maps. Painted wood samples the photo pine (wood/white_pine_timber_side) faintly
// through the paint, like the cabin's stained siding.
import {renderX} from '../cabin/cabin-art.mjs';
import {texture, lum} from '../wood-photo/sample.mjs';
import {N, hash, clamp, sstep, mul, mix, vn, cells} from '../mesh-trees/hd-art.mjs';

const PINE = texture('wood/white_pine_timber_side');
/** The photo pine's relative brightness at (along the grain, across it), clamped (knots and grain darker). */
const grain = (along, across) => clamp(lum(PINE(across, along)) / PINE.mean, 0.55, 1.35);

// ------------------------------------------------------------------------------------------------- siding

/** The five lap siding paints (user to see the swatches once, blocks list section 9). */
export const LAP = {white: [228, 226, 216], cream: [226, 211, 170], sage: [146, 160, 130], blue_gray: [124, 140, 155], tan: [190, 166, 128]};
/** Lap siding courses per block (10 cm to the weather) and the texels of the shadow line under each butt. */
export const LAP_COURSES = 10;
const LAP_H = N / LAP_COURSES, LAP_STEP = 12, LAP_SOFT = 5;

/**
 * Painted lap siding: ten bevelled boards per block, the butt of each board standing LAP_STEP mm proud of the board
 * below (the parallax and normal show the steps; the step's edge 5 texels soft), its shadow line on the top of the board
 * below; the paint half-old: faint chalking in broad patches, now and then a short strip peeled at a butt (grey
 * weathered pine showing). Boards butt-jointed at random along each course (a thin joint, albedo only).
 */
export function lapSiding(name) {
  const paint = LAP[name], seed = 4000 + Object.keys(LAP).indexOf(name) * 37;
  const tones = Array.from({length: LAP_COURSES}, (_, k) => 0.97 + 0.06 * hash(k, 1, seed));
  const butts = Array.from({length: LAP_COURSES}, (_, k) => [N * hash(k, 2, seed), N * hash(k, 3, seed)]);
  const peel = Array.from({length: LAP_COURSES}, (_, k) => hash(k, 4, seed) < 0.35 ? [N * hash(k, 5, seed), 10 + 26 * hash(k, 6, seed)] : null);
  return (x, y) => {
    const k = Math.floor(y / LAP_H) % LAP_COURSES, t = (y - k * LAP_H) / LAP_H;   // t 0 at the top of a board, 1 at its butt
    // the bevel: thin under the board above, the butt LAP_STEP proud; the step to the next board eased over LAP_SOFT texels
    const ty = y - k * LAP_H, ease = sstep(LAP_H - LAP_SOFT, LAP_H, ty);
    const h = -LAP_STEP * (1 - t) * (1 - ease) - LAP_STEP * ease;
    let c = mul(paint, tones[k] * (1 + 0.1 * (grain(x + 67 * k, ty + 31 * k) - 1)) * (1 + 0.025 * vn(x, y, seed + 9, 60, 24)));
    c = mix(c, [214, 212, 204], 0.12 * sstep(0.2, 0.9, vn(x, y, seed + 13, 80, 48)));   // chalked
    let ao = 1;
    if (ty < 6) { const s = 1 - ty / 6; c = mul(c, 1 - 0.3 * s); ao = 1 - 0.35 * s; }   // the butt's shadow on this board's top
    if (ty > LAP_H - 1.2) c = mul(c, 0.9);   // the butt's lower arris
    for (const b of butts[k]) if (Math.abs(((x - b) % N + N) % N - 0) < 0.8 || Math.abs(((x - b) % N + N) % N - N) < 0.8) { c = mul(c, 0.72); ao = 0.8; }
    const p = peel[k];
    if (p && ty > LAP_H - 7 && ((x - p[0]) % N + N) % N < p[1] * (0.6 + 0.4 * sstep(-1, 1, vn(x, y, seed + 21, 8, 4)))) {
      const wood = mul([128, 122, 110], grain(x + 13 * k, ty));   // bare, weathered pine
      return {c: wood, s: 30, p: 40, h: h - 0.4, ao};
    }
    return {c, s: 95, p: 8, h, ao};
  };
}

/** The plaster of an inside wall face, a ceiling's underside and the partitions: warm white, flat, matt. */
export function plaster() {
  const seed = 4300;
  return (x, y) => ({c: mul([222, 216, 204], 1 + 0.018 * vn(x, y, seed, 120) + 0.01 * vn(x, y, seed + 1, 30)), s: 40, p: 10, h: 0.6 * vn(x, y, seed + 2, 60), ao: 1});
}

// ------------------------------------------------------------------------------------------------- brick

/** Common red brick: fifteen courses a block (16 texels each, 2.4 of joint), five stretchers a course, a header course every fifth. */
const BR_COURSES = 15, BR_CH = N / BR_COURSES, BR_UNITS = 5, BR_UW = N / BR_UNITS, BR_J = 2.4;
export function redBrick() {
  const seed = 4400, shades = [[150, 64, 46], [138, 56, 42], [160, 74, 52]], mortar = [196, 190, 178];
  return (x, y) => {
    const r = Math.floor(y / BR_CH) % BR_COURSES, header = r % 5 === 0, uw = header ? BR_UW / 2 : BR_UW;
    const off = header ? (r % 10 === 0 ? 0 : uw / 2) : (r % 2 ? uw / 2 : 0);
    const xx = ((x + off) % N + N) % N, u = Math.floor(xx / uw), fx = xx - u * uw, fy = y - r * BR_CH;
    const units = Math.round(N / uw), wu = u % units;
    if (fx < BR_J || fy < BR_J) {
      const t = Math.min(fx < BR_J ? fx / BR_J : 1, fy < BR_J ? fy / BR_J : 1);
      return {c: mul(mortar, 1 + 0.04 * vn(x, y, seed + 3, 24)), s: 30, p: 50, h: -5 + 1.5 * t, ao: 0.82};
    }
    const sh = shades[Math.floor(hash(wu, r, seed) * 3) % 3], tone = 0.86 + 0.24 * hash(wu, r, seed + 1);
    let c = mul(sh, tone * (1 + 0.05 * vn(x, y, seed + 5, 12) + 0.04 * vn(x, y, seed + 6, 60)));
    if (header) c = mul(c, 0.94);   // the headers' ends a touch darker (harder burnt)
    const d = Math.min(fx - BR_J, uw - fx, fy - BR_J, BR_CH - fy), ar = sstep(0, 1.2, d);
    return {c, s: 55, p: 30, h: -0.8 * (1 - ar), ao: 1};
  };
}

// --------------------------------------------------------------------------------------- cement and stone

/** Parged foundation: grey cement over the block, broad trowel sweeps, a few hairline cracks (albedo only). */
export function parging() {
  const seed = 4500;
  return (x, y) => {
    let c = mul([146, 144, 138], 1 + 0.04 * vn(x, y, seed, 60, 30) + 0.03 * vn(x, y, seed + 1, 30, 15) + 0.02 * vn(x, y, seed + 2, 8));
    const cl = cells(x, y, 3, 2, seed + 7, 0.9);
    if (hash(cl.i, cl.j, seed + 8) < 0.4 && cl.edge < 0.6) c = mul(c, 0.8);
    return {c, s: 35, p: 45, h: 1.5 * vn(x, y, seed + 3, 40) + 0.8 * vn(x, y, seed + 4, 20), ao: 1};
  };
}

/** Indiana limestone (the chimney's coping): buff grey, honed, faint beds. */
export function limestone() {
  const seed = 4600;
  return (x, y) => ({c: mul([196, 190, 176], 1 + 0.03 * vn(x, y, seed, 60) + 0.02 * vn(x, y, seed + 1, 240, 20) + 0.015 * vn(x, y, seed + 2, 6)), s: 60, p: 30, h: 0, ao: 1});
}

/** Fired clay (the flue liner), and the flue's top: the clay rim round a sooty bore (the middle of the texture). */
export function terracotta() {
  const seed = 4700;
  return (x, y) => ({c: mul([170, 96, 64], 1 + 0.04 * vn(x, y, seed, 30) + 0.02 * vn(x, y, seed + 1, 6)), s: 50, p: 30, h: 0, ao: 1});
}
export function flueTop() {
  const clay = terracotta();
  return (x, y) => {
    const d = Math.max(Math.abs(x - 120), Math.abs(y - 120));
    if (d < 30) return {c: mul([24, 22, 20], 1 + 0.1 * vn(x, y, 4710, 12)), s: 20, p: 50, h: 0, ao: 0.4};
    return {...clay(x, y), ao: d < 33 ? 0.7 : 1};
  };
}

// --------------------------------------------------------------------------------------------- floor

/** Marbleised linoleum (a 1940s kitchen): cream ground, soft grey-brown veins, waxed. Sheet goods: no seams. */
export function linoleum() {
  const seed = 4800;
  return (x, y) => {
    const a = 4 * x / N + 2 * y / N + 0.9 * vn(x, y, seed, 60) + 0.45 * vn(x, y, seed + 1, 30) + 0.2 * vn(x, y, seed + 2, 12);
    const m = Math.abs(Math.sin(Math.PI * a)), vein = 1 - sstep(0.04, 0.2, m);
    let c = mul([214, 204, 180], 1 + 0.03 * vn(x, y, seed + 3, 40));
    c = mix(c, [158, 148, 130], 0.6 * vein);
    c = mix(c, [190, 178, 154], 0.25 * sstep(0.3, 0.9, vn(x, y, seed + 4, 48)));
    return {c, s: 150, p: 0, h: 0, ao: 1};
  };
}

/** The attic side of a plaster ceiling: two joists a block along u (38 mm wide), mineral wool batts between. */
export function ceilingTop() {
  const seed = 4900, J = 9;
  return (x, y) => {
    const fx = ((x % 120) + 120) % 120;
    if (fx < J) {
      const c = mul([168, 140, 104], grain(y, fx + 40 * Math.floor(x / 120)) * (1 + 0.02 * vn(x, y, seed, 24)));
      return {c, s: 40, p: 30, h: 0, ao: 1};
    }
    const edge = Math.min(fx - J, 120 - fx), ao = 0.75 + 0.25 * sstep(0, 10, edge);
    const c = mul([156, 142, 118], (1 + 0.08 * vn(x, y, seed + 1, 12) + 0.05 * vn(x, y, seed + 2, 6)) * ao);
    return {c, s: 10, p: 60, h: -25 + 6 * sstep(0, 12, edge) + 3 * vn(x, y, seed + 3, 20), ao};
  };
}

// --------------------------------------------------------------------------------------------- trim and paint

/** White painted trim (fascia, casings, corner boards, sashes, skirting): the pine's grain faintly under the paint. */
export function trimWhite(vertical) {
  const seed = vertical ? 5010 : 5000;
  return (X, Y) => {
    const [x, y] = vertical ? [Y, X] : [X, Y];
    return {c: mul([230, 228, 220], (1 + 0.08 * (grain(x + (vertical ? 131 : 0), y + 41) - 1)) * (1 + 0.015 * vn(x, y, seed, 120, 30))), s: 120, p: 6, h: 0, ao: 1};
  };
}
/** White painted aluminium (drip edges, rake flashing). */
export function dripWhite() { return (x, y) => ({c: mul([236, 236, 232], 1 + 0.01 * vn(x, y, 5100, 60)), s: 150, f0: 10, p: 0, h: 0, ao: 1}); }
/** A door's paint (front doors: green, wine, black; the white one is trimWhite): semi-gloss, faint brush marks along v. */
export const PAINT = {green: [46, 74, 58], wine: [98, 36, 40], black: [32, 32, 34]};
export function paint(name) {
  const seed = 5200 + Object.keys(PAINT).indexOf(name) * 17;
  return (x, y) => ({c: mul(PAINT[name], (1 + 0.02 * vn(x, y, seed, 120, 6)) * (1 + 0.06 * (grain(y, x) - 1))), s: 160, p: 4, h: 0, ao: 1});
}
/** Black painted wrought iron: semi-gloss enamel, a faint orange peel, no wear (the user 2026-10-21: no rust spots). */
export function ironBlack() {
  const seed = 5300;
  return (x, y) => ({c: mul([34, 34, 35], 1 + 0.04 * vn(x, y, seed, 30) + 0.02 * vn(x, y, seed + 1, 8)), s: 120, f0: 10, p: 0, h: 0, ao: 1});
}
/**
 * Smooth concrete (the stoop's landing and steps, any smooth residential concrete; 2026-10-21 in place of the blowholed
 * stoop concrete, whose ~200 pits a block read as black dots under the AFL shaders): light warm grey, a steel-trowelled
 * or smooth-formed face, broad soft mottling of the cement paste and a faint fine grit in the albedo only; no blowholes,
 * no broom lines (they would tile), the normal map all but flat.
 */
export function smoothConcrete() {
  const seed = 5350;
  return (x, y) => {
    const c = mul([168, 166, 160], 1 + 0.03 * vn(x, y, seed, 80, 40) + 0.018 * vn(x, y, seed + 1, 24) + 0.012 * vn(x, y, seed + 2, 4));
    return {c, s: 55, p: 30, h: 0.25 * vn(x, y, seed + 3, 60), ao: 1};
  };
}
/** Brass (front door knob and plate): the albedo as F0 (LabPBR 255). */
export function brass() { return (x, y) => ({c: mul([182, 146, 78], 1 + 0.04 * vn(x, y, 5400, 30)), s: 190, f0: 255, p: 0, h: 0, ao: 1}); }
/** Pressed glass knob (interior doors). */
export function knobGlass() { return (x, y) => ({c: mul([222, 228, 226], 1 + 0.02 * vn(x, y, 5500, 30)), s: 250, f0: 10, p: 0, h: 0, ao: 1, a: 170}); }
/** Insect screen (the back door's screen door): dark wire mesh, the weave a faint grid (albedo and alpha only). */
export function screenGlass() {
  return (x, y) => {
    const gx = ((x % 3) + 3) % 3, gy = ((y % 3) + 3) % 3, wire = gx < 1 || gy < 1;
    return {c: mul([44, 46, 48], 1 + 0.05 * vn(x, y, 5600, 40)), s: 60, f0: 10, p: 0, h: 0, ao: 1, a: wire ? 140 : 100};
  };
}

// ------------------------------------------------------------------------------------------------- shingles

/** The three asphalt shingle colours (3-tab, a blend of granules). */
export const SHINGLE = {charcoal: [62, 62, 64], weathered_brown: [92, 76, 62], slate: [84, 90, 98]};
/** Courses per block (12.5 cm to the weather; the blocks list said 13), tabs per course (33 cm), course step (mm). */
export const SH_COURSES = 8, SH_TABS = 3, SH_STEP = 4;
const SH_H = N / SH_COURSES, SH_W = N / SH_TABS;
/** Town House B's 1:1 roof (2026-10-21): its cell is projected top-down too, a tile 1.41 m down the slope: twelve courses (11.8 cm; even, for the half-tab stagger). */
export const SH_STEEP_COURSES = 12;
/** Granule grit and per-tab tone (shared by the field and the ridge cap). */
function granules(base, x, y, seed, tab) {
  const blend = hash(tab[0], tab[1], seed);
  let c = mul(base, (0.9 + 0.2 * blend) * (1 + 0.05 * vn(x, y, seed + 3, 3) + 0.03 * vn(x, y, seed + 4, 6) + 0.03 * vn(x, y, seed + 5, 60)));
  if (hash(tab[0], tab[1], seed + 6) < 0.15) c = mix(c, mul(base, 1.25), 0.4);   // a lighter tab from another bundle
  return c;
}
/**
 * The roof field: v down the slope (v 0 at the eave, as the roof pieces project), each course's butt at its v-low edge
 * standing SH_STEP mm proud of the course below, its shadow on the course below; the tabs' cutouts (2 texels, the
 * course underneath showing dark) staggered half a tab from course to course. courses: per tile (the steep roof's 12).
 */
export function shingle(name, courses = SH_COURSES) {
  const base = SHINGLE[name], seed = 5700 + Object.keys(SHINGLE).indexOf(name) * 29, SH_H = N / courses;
  return (x, y) => {
    const k = Math.floor(y / SH_H) % courses, ty = y - k * SH_H, t = ty / SH_H;   // ty 0 at this course's butt
    const off = (k % 2) * SH_W / 2, xx = ((x + off) % N + N) % N, tab = Math.floor(xx / SH_W), fx = xx - tab * SH_W;
    // the course falls from its butt (0) toward the next course's butt (-SH_STEP), eased into the step over 5 texels
    let h = -SH_STEP * t;
    if (ty > SH_H - 5) h = -SH_STEP * t * (1 - sstep(SH_H - 5, SH_H, ty));
    let c = granules(base, x, y, seed, [tab, k]), ao = 1;
    if (ty > SH_H - 5) { const s = sstep(SH_H - 5, SH_H, ty); c = mul(c, 1 - 0.35 * s); ao = 1 - 0.35 * s; }   // under the next butt
    if (ty < 1.2) c = mul(c, 1.08);   // the butt's lit arris
    if (fx < 2 || fx > SH_W - 0.4) { c = mul(base, 0.45); ao = 0.6; }   // a cutout: the course below in shadow
    return {c, s: 35, p: 20, h, ao};
  };
}
/** The ridge cap: cap shingles lapping along u (one every 12.5 cm), the butts at each piece's u-low edge. */
export function shingleRidge(name) {
  const base = SHINGLE[name], seed = 5800 + Object.keys(SHINGLE).indexOf(name) * 29;
  return (x, y) => {
    const k = Math.floor(x / SH_H) % SH_COURSES, tx = x - k * SH_H, t = tx / SH_H;
    let h = -SH_STEP * t;
    if (tx > SH_H - 5) h = -SH_STEP * t * (1 - sstep(SH_H - 5, SH_H, tx));
    let c = mul(granules(base, x, y, seed, [k, 0]), 0.96), ao = 1;
    if (tx > SH_H - 5) { const s = sstep(SH_H - 5, SH_H, tx); c = mul(c, 1 - 0.3 * s); ao = 1 - 0.3 * s; }
    return {c, s: 35, p: 20, h, ao};
  };
}

/** name -> [factory, parallax height]: the town house textures under textures/block/town/. */
export const TEXTURES = {
  ...Object.fromEntries(Object.keys(LAP).map(c => ['lap_' + c, [() => lapSiding(c), true]])),
  plaster: [plaster, false], red_brick_common: [redBrick, true], parged_foundation: [parging, true],
  limestone: [limestone, false], terracotta: [terracotta, false], flue_top: [flueTop, false],
  linoleum: [linoleum, false], ceiling_top: [ceilingTop, true],
  trim_white: [() => trimWhite(false), false], trim_white_v: [() => trimWhite(true), false], drip_white: [dripWhite, false],
  ...Object.fromEntries(Object.keys(PAINT).map(c => ['paint_' + c, [() => paint(c), false]])),
  iron_black: [ironBlack, false], smooth_concrete: [smoothConcrete, true], brass: [brass, false], knob_glass: [knobGlass, false], screen_glass: [screenGlass, false],
  ...Object.fromEntries(Object.keys(SHINGLE).flatMap(c => [['shingle_' + c, [() => shingle(c), true]], ['shingle_' + c + '_steep', [() => shingle(c, SH_STEEP_COURSES), true]], ['shingle_' + c + '_ridge', [() => shingleRidge(c), true]]])),
};
export {renderX};
