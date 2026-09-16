// Signal Tower V2 — cleaned tower-body source of truth.
// Local structure coordinates: X/Z 0..14, Y 0..63.
// Live edits deliberately protect the existing foundation, platforms and top equipment.

export const BASE_CENTER = [7, 7];
export const TOTAL_HEIGHT = 64;
export const BASE_WIDTH = 15;
export const MID_WIDTH = 11;
export const TOP_WIDTH = 5;
export const SECTION_HEIGHT = 16;
export const PLATFORM_LEVELS = [20, 36];
export const PLATFORM_RADIUS = [5, 4];
export const ANTENNA_LEVEL = 56;

export const PALETTE = Object.freeze({
  foundation: 'minecraft:stone_bricks',
  foundation_cracked: 'minecraft:cracked_stone_bricks',
  foundation_cap: 'minecraft:smooth_stone',
  foundation_concrete: 'minecraft:gray_concrete',
  main: 'minecraft:polished_andesite',
  accent: 'minecraft:weathered_copper',
  platform: 'minecraft:smooth_stone_slab[type=bottom]',
  railing: 'minecraft:iron_bars',
  ladder: 'minecraft:ladder[facing=south]',
  antenna: 'minecraft:light_gray_concrete',
  beacon: 'minecraft:red_concrete',
});

const sections = [
  { y0: 3, y1: 19, lo: 2, hi: 12, levels: [3, 7, 11, 15, 19] },
  { y0: 19, y1: 35, lo: 3, hi: 11, levels: [19, 23, 27, 31, 35] },
  { y0: 35, y1: 51, lo: 4, hi: 10, levels: [35, 39, 43, 47, 51] },
  { y0: 51, y1: 59, lo: 5, hi: 9, levels: [51, 55, 59] },
];

const ops = [];
const box = (min, max, block) => ops.push({ min, max, block });
const cell = (x, y, z, block) => box([x, y, z], [x, y, z], block);

function line(from, to, block) {
  const steps = Math.max(Math.abs(to[0] - from[0]), Math.abs(to[1] - from[1]), Math.abs(to[2] - from[2]));
  for (let i = 0; i <= steps; i++) {
    const t = steps === 0 ? 0 : i / steps;
    cell(Math.round(from[0] + (to[0] - from[0]) * t), Math.round(from[1] + (to[1] - from[1]) * t), Math.round(from[2] + (to[2] - from[2]) * t), block);
  }
}

function brace(lo, hi, a, b, side, axis, block) {
  const span = hi - lo;
  for (let i = 0; i <= span; i++) {
    const y = Math.round(a + (b - a) * i / span);
    const p = lo + i;
    if (axis === 'z') {
      cell(p, y, side, block);
      cell(hi - i, y, side, block);
    } else {
      cell(side, y, p, block);
      cell(side, y, hi - i, block);
    }
  }
}

function perimeter(level, lo, hi, block) {
  box([lo, level, lo], [hi, level, lo], block);
  box([lo, level, hi], [hi, level, hi], block);
  box([lo, level, lo + 1], [lo, level, hi - 1], block);
  box([hi, level, lo + 1], [hi, level, hi - 1], block);
}

function octagon(y, radius, block) {
  for (let z = 7 - radius; z <= 7 + radius; z++) {
    for (let x = 7 - radius; x <= 7 + radius; x++) {
      if (Math.abs(x - 7) + Math.abs(z - 7) <= radius * 2 - 1) cell(x, y, z, block);
    }
  }
}

// Protected context: foundation, platforms and top devices remain unchanged in-world.
for (const [x, z] of [[0, 0], [12, 0], [0, 12], [12, 12]]) {
  box([x, 0, z], [x + 2, 0, z + 2], PALETTE.foundation);
  box([x, 1, z], [x + 2, 1, z + 2], PALETTE.foundation_concrete);
  cell(x + 1, 2, z + 1, PALETTE.foundation_cap);
}
box([6, 0, 6], [8, 0, 8], PALETTE.foundation);
box([6, 1, 6], [8, 1, 8], PALETTE.foundation_cracked);

// Clean four-leg frame with 4-block horizontal rhythm and sparse 8-block X bracing.
for (const section of sections) {
  for (const [x, z] of [[section.lo, section.lo], [section.hi, section.lo], [section.lo, section.hi], [section.hi, section.hi]]) {
    box([x, section.y0, z], [x, section.y1, z], PALETTE.main);
  }
  for (const level of section.levels) perimeter(level, section.lo, section.hi, PALETTE.main);
  for (let i = 0; i + 2 < section.levels.length; i += 2) {
    const a = section.levels[i];
    const b = section.levels[i + 2];
    for (const side of [section.lo, section.hi]) brace(section.lo, section.hi, a, b, side, 'z', PALETTE.main);
    for (const side of [section.lo, section.hi]) brace(section.lo, section.hi, a, b, side, 'x', PALETTE.main);
  }
}

for (const y of [3, 19, 35, 51, 59]) {
  const inset = y < 19 ? 2 : y < 35 ? 3 : y < 51 ? 4 : 5;
  for (const [x, z] of [[inset, inset], [14 - inset, inset], [inset, 14 - inset], [14 - inset, 14 - inset]]) cell(x, y, z, PALETTE.accent);
}

// Context copies: exact existing platform and antenna layouts for preview/source parity.
for (let i = 0; i < PLATFORM_LEVELS.length; i++) {
  const y = PLATFORM_LEVELS[i];
  const radius = PLATFORM_RADIUS[i];
  octagon(y, radius, PALETTE.platform);
  for (let z = 7 - radius; z <= 7 + radius; z++) {
    for (let x = 7 - radius; x <= 7 + radius; x++) {
      if (Math.abs(x - 7) + Math.abs(z - 7) <= radius * 2 - 1 && (x !== 7 || (z !== 6 && z !== 7))) {
        if (Math.abs(x - 7) === radius || Math.abs(z - 7) === radius || Math.abs(x - 7) + Math.abs(z - 7) === radius * 2 - 1) cell(x, y + 1, z, PALETTE.railing);
      }
    }
  }
}
box([7, 2, 6], [7, 59, 6], PALETTE.main);
for (let y = 2; y <= 58; y++) cell(7, y, 7, PALETTE.ladder);
box([7, 59, 7], [7, 62, 7], PALETTE.main);
cell(7, 63, 7, PALETTE.beacon);
box([6, 55, 1], [8, 59, 1], PALETTE.antenna);
box([6, 55, 13], [8, 59, 13], PALETTE.antenna);
box([1, 55, 6], [1, 59, 8], PALETTE.antenna);
box([13, 55, 6], [13, 59, 8], PALETTE.antenna);
line([7, ANTENNA_LEVEL, 6], [7, ANTENNA_LEVEL, 1], PALETTE.railing);
line([7, ANTENNA_LEVEL, 8], [7, ANTENNA_LEVEL, 13], PALETTE.railing);
line([6, ANTENNA_LEVEL, 7], [1, ANTENNA_LEVEL, 7], PALETTE.railing);
line([8, ANTENNA_LEVEL, 7], [13, ANTENNA_LEVEL, 7], PALETTE.railing);

export const operations = ops;
export default function build(api) {
  const structure = api.createStructure([BASE_WIDTH, TOTAL_HEIGHT, BASE_WIDTH]);
  for (const operation of operations) api.fill(structure, operation.min, operation.max, operation.block);
  return {
    structure,
    metadata: {
      name: 'signal_tower_body_rebuild_v1',
      parameters: { BASE_CENTER, TOTAL_HEIGHT, BASE_WIDTH, MID_WIDTH, TOP_WIDTH, SECTION_HEIGHT, PLATFORM_LEVELS, PLATFORM_RADIUS, ANTENNA_LEVEL },
      palette: PALETTE,
    },
  };
}
