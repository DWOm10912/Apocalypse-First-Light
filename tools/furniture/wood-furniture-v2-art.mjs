// Wood Furniture V2 (docs/models/wood_furniture_v2.md, 2026-10-15): the new textures of the bedroom and storage furniture
// (tools/build-wood-furniture-v2.mjs). The wood is V1's (furniture/wood-furniture-art.mjs: one neutral stained set); these
// are the soft goods of the bed and the vintage chest freezer's insides. 240 texels = 1 m, LabPBR like cabin-art (renderX).
// Plain materials, no printed pattern but the mattress ticking's woven stripe (the cloth itself, mostly under the blanket).
// Flat normal maps (h 0): fine coherent relief in a flat tiling map reads harsh under grazing light (memory ground relief).
import {sstep, mul, mix, vn} from '../mesh-trees/hd-art.mjs';

/** A wool blanket: muted moss green, heathered (two fibre tones), a soft nap; matte. */
export function blanket() {
  return (x, y) => {
    const heather = sstep(0.2, 0.9, vn(x, y, 7000, 4, 3)) * 0.5 + sstep(0.3, 1, vn(x, y, 7001, 2)) * 0.3;
    let c = mix([92, 100, 80], [118, 124, 102], heather * 0.35);
    c = mul(c, 1 + 0.035 * vn(x, y, 7002, 24) + 0.025 * vn(x, y, 7003, 6));
    return {c, s: 26, p: 0, h: 0, ao: 1};
  };
}

/** Cotton sheets and the pillow case: off-white, a faint weave tone, a little sheen. */
export function linen() {
  return (x, y) => ({c: mul([220, 216, 204], 1 + 0.018 * vn(x, y, 7100, 30) + 0.012 * vn(x, y, 7101, 3)), s: 48, p: 0, h: 0, ao: 1});
}

/** Mattress ticking: off-white twill with a woven blue-grey stripe along the mattress (v = height on its sides). */
export function ticking() {
  return (x, y) => {
    const t = ((y % 24) + 24) % 24, stripe = t < 5 ? 1 : t > 12 && t < 14 ? 0.6 : 0;
    const c = mix([212, 208, 194], [96, 110, 128], stripe * 0.85);
    return {c: mul(c, 1 + 0.02 * vn(x, y, 7200, 20) + 0.015 * vn(x, y, 7201, 3)), s: 42, p: 0, h: 0, ao: 1};
  };
}

/** The freezer's liner: white enamelled steel inside, satin, under a patchy hoar frost (whiter, matte, faint blue). */
export function freezerLiner() {
  return (x, y) => {
    const frost = sstep(0.0, 0.8, vn(x, y, 7300, 40) * 0.7 + vn(x, y, 7301, 8) * 0.3);
    let c = mul([226, 229, 228], 1 + 0.012 * vn(x, y, 7302, 30));
    c = mix(c, [240, 245, 248], frost * 0.8);
    c = mul(c, 1 + 0.03 * frost * vn(x, y, 7303, 2));
    return {c, s: Math.round(160 - 110 * frost), f0: 10, p: 0, h: 0, ao: 1};
  };
}

/** Black rubber (the lid gasket, the cord inlet): satin, near black. */
export function rubber() {
  return (x, y) => ({c: mul([38, 38, 37], 1 + 0.04 * vn(x, y, 7400, 20)), s: 60, f0: 10, p: 0, h: 0, ao: 1});
}

/** Every new texture: name -> [maker, height?]. Paths: textures/block/furniture/<name>.png (+ _s, _n). */
export const FURNITURE_V2_TEXTURES = {
  blanket: [blanket, false], linen: [linen, false], ticking: [ticking, false], freezer_liner: [freezerLiner, false], rubber: [rubber, false],
};

