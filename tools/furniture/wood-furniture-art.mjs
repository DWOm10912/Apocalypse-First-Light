// Wood Furniture V1 (docs/models/wood_furniture_v1.md, 2026-10-13): the textures of the generic wood furniture
// (tools/build-wood-furniture-v1.mjs). User 2026-10-13: furniture is not made per species; one set in one finish that
// matches no species; user 2026-10-15: made later at the carpenter table from any AFL species wood + paint (no recipes yet). The building materials (logs, planks, timber,
// beams, stairs, slabs, firewood) stay per species (memory one-wood-per-species).
// The finish (user 2026-10-15, docs/models/wood_furniture_v2.md "家具木色"): a walnut oil finish, chocolate brown, face
// CIELAB L 37 / a 7 / b 15, against the six species' planks at 62..85 and the light log walls, so it reads as finished
// furniture beside any of them. Until 2026-10-15 a neutral mid-brown [142,112,82] / [112,86,62] (L 49, C 22.5) that looked muddy.
// 240 texels = 1 m, LabPBR like cabin-art (renderX).
import {N, hash, sstep, mul, mix, vn} from '../mesh-trees/hd-art.mjs';
import {grain, iron} from '../cabin/cabin-art.mjs';

/** The stained face and its latewood (sRGB). */
export let FACE = [106, 82, 63], LATE = [80, 57, 38];
/** Design studies only (design/.../m4_source/wood_finish_swatches.mjs): another finish for the textures made after it. */
export function setFinish(face, late) { FACE = face; LATE = late; }
const wood = (late, k = 0.45) => mix(FACE, LATE, late * k);

/** Planed stock (rails, aprons, seats, slats, drawer fronts, the brackets): grain along u (vertical: along v), satin. */
export function lumber(vertical) {
  const seed = vertical ? 6010 : 6000;
  return (X, Y) => {
    const [x, y] = vertical ? [Y, X] : [X, Y];
    const c = mul(wood(grain(y, x, 8, seed, x, y)), 1 + 0.02 * vn(x, y, seed + 3, 120, 30));
    return {c, s: 130, p: 16, h: 0, ao: 1};
  };
}

/**
 * Glued-up boards (table tops, the counters' worktops): six 16.7 cm boards across v, grain along u, each its own tone, a
 * hairline glue joint (colour only, no relief: a flat top under grazing light), a little glossier than the stock.
 */
export function top() {
  const seed = 6100, W = N / 6, tones = Array.from({length: 6}, (_, k) => 0.95 + 0.1 * hash(k, 1, seed));
  return (x, y) => {
    const k = Math.floor(y / W) % 6, ty = y - k * W, d = Math.min(ty, W - ty);
    let c = mul(wood(grain(ty + 23 * k, x, 8, seed + 5 + k, x, y)), tones[k] * (1 + 0.02 * vn(x, y, seed + 9, 120, 24)));
    if (d < 0.8) c = mul(c, 0.86);
    return {c, s: 145, p: 14, h: 0, ao: 1};
  };
}

/** The doors' recessed panels: eight 12.5 cm V-groove boards per block, grain along v, a 5-texel bevelled groove. */
export function panel() {
  const seed = 6200, W = N / 8, tones = Array.from({length: 8}, (_, k) => 0.95 + 0.1 * hash(k, 1, seed));
  return (x, y) => {
    const k = Math.floor(x / W) % 8, tx = x - k * W, d = Math.min(tx, W - tx);
    let c = mul(wood(grain(tx + 31 * k, y, 8, seed + 5 + k, y, x)), 0.97 * tones[k] * (1 + 0.02 * vn(x, y, seed + 9, 30, 120)));
    let h = 0, ao = 1;
    if (d < 2.5) { const t = 1 - d / 2.5; h = -1.4 * t; c = mul(c, 1 - 0.25 * t); ao = 1 - 0.25 * t; }
    return {c, s: 125, p: 18, h, ao};
  };
}

/** Inside a cabinet (sides, back, shelf, floor): the same wood unfinished and in shade, matte. */
export function inside() {
  const seed = 6300;
  return (x, y) => ({c: mul(wood(grain(y, x, 8, seed, x, y), 0.4), 0.75 * (1 + 0.02 * vn(x, y, seed + 3, 120, 30))), s: 60, p: 30, h: 0, ao: 1});
}

/** Old porcelain enamel (the drop-in sink): cream white, glossy, a faint mottle; dielectric (a glaze, not metal). */
export function enamel() {
  return (x, y) => ({c: mul([214, 208, 192], 1 + 0.012 * vn(x, y, 6400, 60) + 0.008 * vn(x, y, 6401, 20)), s: 205, f0: 10, p: 0, h: 0, ao: 1});
}

/** Black cast iron (the pitcher pump, the sink's drain): old stove-black paint, satin, a few faint rust-brown patches. */
export function castIron() {
  return (x, y) => {
    let c = mul([44, 43, 41], 1 + 0.06 * vn(x, y, 6500, 40) + 0.03 * vn(x, y, 6501, 12));
    c = mix(c, [86, 60, 44], 0.18 * sstep(0.55, 1, vn(x, y, 6502, 30)));
    return {c, s: 75, f0: 10, p: 0, h: 0, ao: 1};
  };
}

/** Every furniture texture: name -> [maker, height?]. Paths: textures/block/furniture/<name>.png (+ _s, _n). */
export const FURNITURE_TEXTURES = {
  wood: [() => lumber(false), false], wood_v: [() => lumber(true), false], wood_top: [top, false], wood_panel: [panel, true],
  wood_inside: [inside, false], iron: [iron, false], cast_iron: [castIron, false], enamel: [enamel, false],
};
