// CurseForge / Modrinth description cards for Apocalypse: First Light, in the style of the title art
// (E:/Download/AFL/afl_title.png) and badge (afl_badge.png): pixel art on a unit grid, rusty steel frames with a peach top
// edge, dusk sky bands, block letters with a dark extrusion and a black outline, in "steel" (APOCALYPSE) or "sun"
// (FIRST LIGHT) fills. Colours are sampled from those two images. Each card is drawn at 1 px per unit, then scaled up
// UNIT times with nearest neighbour, so edges stay crisp. See docs/项目内容/04 - CurseForge 等 mod 发布页.md.
//
//   node tools/build-curseforge-cards.mjs [outDir]     (default E:/Download/AFL_CurseForge_Cards)
import fs from 'fs';
import path from 'path';
import {png, readPng} from './cube-slab-mesh-lib.mjs';

const OUT = process.argv[2] || 'E:/Download/AFL_CurseForge_Cards';
const TITLE = 'E:/Download/AFL/afl_title.png';
const UNIT = 4;

const hex = h => [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16)];
const P = {
  black: hex('000000'), ink: hex('121214'), night: hex('1a181e'), skyline: hex('221f27'),
  sky: ['2a2238', '3c2a40', '5a3445', '6b4349', '874a4c', 'cb7049', 'e19557'].map(hex),
  steelLight: hex('7a7e84'), steel: hex('5c6066'), steelDark: hex('3a3c40'),
  face: hex('52565b'), faceDark: hex('45484d'), faceLight: hex('5f636a'), depth: hex('2c2a28'), depthDark: hex('1c1d1f'),
  peach: hex('f2c07e'), peachLight: hex('f4c37e'),
  rust: ['7a4229', 'a07355', '7a3212', 'b65c1f'].map(hex),
  sun: ['fff4cc', 'ffe08a', 'ffd877', 'ffc55a', 'ffb94a', 'ffa640', 'f7952e', 'e2722a'].map(hex),
  sunTop: hex('fff8e2'), cream: hex('fff0be'), orange: hex('ffa640'), blurple: hex('5865f2'), white: hex('ffffff'),
};

// ---------------- 5 x 7 font ----------------
const G = {
  A: ['.###.', '#...#', '#...#', '#####', '#...#', '#...#', '#...#'], B: ['####.', '#...#', '#...#', '####.', '#...#', '#...#', '####.'],
  C: ['.####', '#....', '#....', '#....', '#....', '#....', '.####'], D: ['####.', '#...#', '#...#', '#...#', '#...#', '#...#', '####.'],
  E: ['#####', '#....', '#....', '####.', '#....', '#....', '#####'], F: ['#####', '#....', '#....', '####.', '#....', '#....', '#....'],
  G: ['.####', '#....', '#....', '#.###', '#...#', '#...#', '.####'], H: ['#...#', '#...#', '#...#', '#####', '#...#', '#...#', '#...#'],
  I: ['###', '.#.', '.#.', '.#.', '.#.', '.#.', '###'], J: ['..###', '...#.', '...#.', '...#.', '...#.', '#..#.', '.##..'],
  K: ['#...#', '#..#.', '#.#..', '##...', '#.#..', '#..#.', '#...#'], L: ['#....', '#....', '#....', '#....', '#....', '#....', '#####'],
  M: ['#...#', '##.##', '#.#.#', '#.#.#', '#...#', '#...#', '#...#'], N: ['#...#', '##..#', '#.#.#', '#..##', '#...#', '#...#', '#...#'],
  O: ['.###.', '#...#', '#...#', '#...#', '#...#', '#...#', '.###.'], P: ['####.', '#...#', '#...#', '####.', '#....', '#....', '#....'],
  Q: ['.###.', '#...#', '#...#', '#...#', '#.#.#', '#..#.', '.##.#'], R: ['####.', '#...#', '#...#', '####.', '#.#..', '#..#.', '#...#'],
  S: ['.####', '#....', '#....', '.###.', '....#', '....#', '####.'], T: ['#####', '..#..', '..#..', '..#..', '..#..', '..#..', '..#..'],
  U: ['#...#', '#...#', '#...#', '#...#', '#...#', '#...#', '.###.'], V: ['#...#', '#...#', '#...#', '#...#', '#...#', '.#.#.', '..#..'],
  W: ['#...#', '#...#', '#...#', '#.#.#', '#.#.#', '##.##', '#...#'], X: ['#...#', '#...#', '.#.#.', '..#..', '.#.#.', '#...#', '#...#'],
  Y: ['#...#', '#...#', '.#.#.', '..#..', '..#..', '..#..', '..#..'], Z: ['#####', '....#', '...#.', '..#..', '.#...', '#....', '#####'],
  0: ['.###.', '#...#', '#..##', '#.#.#', '##..#', '#...#', '.###.'], 1: ['.#.', '##.', '.#.', '.#.', '.#.', '.#.', '###'],
  2: ['.###.', '#...#', '....#', '...#.', '..#..', '.#...', '#####'], 3: ['####.', '....#', '....#', '.###.', '....#', '....#', '####.'],
  4: ['...#.', '..##.', '.#.#.', '#..#.', '#####', '...#.', '...#.'], 5: ['#####', '#....', '####.', '....#', '....#', '#...#', '.###.'],
  6: ['.###.', '#....', '#....', '####.', '#...#', '#...#', '.###.'], 7: ['#####', '....#', '...#.', '..#..', '.#...', '.#...', '.#...'],
  8: ['.###.', '#...#', '#...#', '.###.', '#...#', '#...#', '.###.'], 9: ['.###.', '#...#', '#...#', '.####', '....#', '....#', '.###.'],
  '.': ['.', '.', '.', '.', '.', '.', '#'], ',': ['..', '..', '..', '..', '..', '.#', '#.'], '!': ['#', '#', '#', '#', '#', '.', '#'],
  '?': ['.###.', '#...#', '....#', '...#.', '..#..', '.....', '..#..'], ':': ['.', '#', '.', '.', '.', '#', '.'],
  '-': ['...', '...', '...', '###', '...', '...', '...'], '/': ['....#', '...#.', '...#.', '..#..', '.#...', '.#...', '#....'],
  '&': ['.##..', '#..#.', '#.#..', '.#...', '#.#.#', '#..#.', '.##.#'], '+': ['.....', '..#..', '..#..', '#####', '..#..', '..#..', '.....'],
  "'": ['#', '#', '.', '.', '.', '.', '.'], '(': ['.#', '#.', '#.', '#.', '#.', '#.', '.#'], ')': ['#.', '.#', '.#', '.#', '.#', '.#', '#.'],
  '<': ['...#', '..#.', '.#..', '#...', '.#..', '..#.', '...#'], '>': ['#...', '.#..', '..#.', '...#', '..#.', '.#..', '#...'],
  '·': ['.', '.', '.', '#', '.', '.', '.'], ' ': ['...', '...', '...', '...', '...', '...', '...'],
};
const glyph = ch => G[ch] || G[ch.toUpperCase()] || G['?'];
const textWidth = (text, s = 1) => [...text].reduce((w, ch, i) => w + glyph(ch)[0].length * s + (i ? s : 0), 0);
/** Width of bold block letters: glyph pixels s apart, each (s+1) wide, so a glyph is one unit wider. */
const boldWidth = (text, s) => [...text].reduce((w, ch, i) => w + glyph(ch)[0].length * s + 1 + (i ? s : 0), 0);

// ---------------- canvas ----------------
class Canvas {
  constructor(w, h) { this.w = w; this.h = h; this.px = new Float64Array(w * h * 4); }
  set(x, y, c, a = 1) {
    x = Math.round(x); y = Math.round(y);
    if (x < 0 || y < 0 || x >= this.w || y >= this.h || a <= 0) return;
    const i = (y * this.w + x) * 4, p = this.px;
    p[i] = p[i] * (1 - a) + c[0] * a; p[i + 1] = p[i + 1] * (1 - a) + c[1] * a; p[i + 2] = p[i + 2] * (1 - a) + c[2] * a;
    p[i + 3] = p[i + 3] + (1 - p[i + 3]) * a;
  }
  rect(x, y, w, h, c, a = 1) { for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) this.set(x + i, y + j, c, a); }
  save(name) {
    const W = this.w * UNIT, H = this.h * UNIT, out = Buffer.alloc(W * H * 4);
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const i = ((y / UNIT | 0) * this.w + (x / UNIT | 0)) * 4, o = (y * W + x) * 4, a = this.px[i + 3];
      out[o] = Math.round(this.px[i]); out[o + 1] = Math.round(this.px[i + 1]); out[o + 2] = Math.round(this.px[i + 2]); out[o + 3] = Math.round(a * 255);
    }
    fs.writeFileSync(path.join(OUT, name), png(out, W, H));
    return name + ` ${W}x${H}`;
  }
}

let seed = 1;
const rand = () => (seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff;

/** The badge's frame: black outline, a 3-unit steel border (peach top edge), rust specks low down, a filled inside. */
function frame(c, x, y, w, h, fill = P.night) {
  c.rect(x, y, w, h, P.black);
  c.rect(x + 1, y + 1, w - 2, h - 2, P.steelLight);
  c.rect(x + 2, y + 2, w - 4, h - 4, P.steel);
  c.rect(x + 3, y + 3, w - 6, h - 6, P.steelDark);
  c.rect(x + 1, y + 1, w - 2, 1, P.peach);
  for (let j = 1; j < h - 1; j++) for (let i = 1; i < w - 1; i++) {
    const border = i < 4 || j < 4 || i >= w - 4 || j >= h - 4;
    if (!border || j === 1) continue;
    const low = j / h;
    if (rand() < 0.04 + 0.22 * low * low) c.set(x + i, y + j, P.rust[rand() * 4 | 0]);
  }
  if (Array.isArray(fill)) c.rect(x + 4, y + 4, w - 8, h - 8, fill);
  else fill(x + 4, y + 4, w - 8, h - 8);
}

/** Dusk sky bands (the badge's sky), top to bottom, as a fill function. */
const dusk = c => (x, y, w, h) => {
  const bands = [P.night, ...P.sky.slice(0, 4)];
  for (let j = 0; j < h; j++) c.rect(x, y + j, w, 1, bands[Math.min(bands.length - 1, Math.floor(j / h * bands.length))]);
};

/** Small text: 5 x 7, 1 unit per glyph pixel, a black drop shadow. {o:...} spans are orange. */
function small(c, text, x, y, colour = P.cream) {
  let at = x, col = colour;
  for (const part of text.split(/(\{o:[^}]*\})/)) {
    const orange = part.startsWith('{o:');
    const t = orange ? part.slice(3, -1) : part;
    for (const ch of t) {
      const g = glyph(ch);
      for (let j = 0; j < 7; j++) for (let i = 0; i < g[j].length; i++) if (g[j][i] === '#') {
        c.set(at + i + 1, y + j + 1, P.black);
      }
      for (let j = 0; j < 7; j++) for (let i = 0; i < g[j].length; i++) if (g[j][i] === '#') c.set(at + i, y + j, orange ? P.orange : col);
      at += g[0].length + 1;
    }
  }
}
const smallWidth = text => textWidth(text.replace(/\{o:([^}]*)\}/g, '$1')) + 0;

/**
 * Block letters like the title: glyph pixels s x s units, a dark extrusion `depth` units down, a black outline, and a
 * "steel" face (grey, peach top edge, rust drips) or a "sun" face (the sunrise gradient top to bottom).
 */
function big(c, text, x, y, s, style, depth = Math.max(1, s - 1)) {
  const w = boldWidth(text, s) + 2, h = 7 * s + 1 + depth + 2;
  const face = new Uint8Array(w * h);
  let at = 1;
  for (const ch of text) {
    const g = glyph(ch);
    for (let j = 0; j < 7; j++) for (let i = 0; i < g[j].length; i++) if (g[j][i] === '#')
      for (let a = 0; a <= s; a++) for (let b = 0; b <= s; b++) face[(1 + j * s + b) * w + at + i * s + a] = 1;
    at += g[0].length * s + 1 + s;
  }
  const isFace = (i, j) => i >= 0 && j >= 0 && i < w && j < h && face[j * w + i] === 1;
  const solid = new Uint8Array(w * h);
  for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) {
    if (isFace(i, j)) solid[j * w + i] = 2;
    else for (let d = 1; d <= depth; d++) if (isFace(i, j - d)) { solid[j * w + i] = 1; break; }
  }
  const isSolid = (i, j) => i >= 0 && j >= 0 && i < w && j < h && solid[j * w + i] > 0;
  for (let j = -1; j <= h; j++) for (let i = -1; i <= w; i++) {
    if (isSolid(i, j)) continue;
    let near = false;
    for (let dj = -1; dj <= 1 && !near; dj++) for (let di = -1; di <= 1; di++) if (isSolid(i + di, j + dj)) { near = true; break; }
    if (near) c.set(x + i, y + j, P.black);
  }
  const top = 1, bottom = 2 + 7 * s;
  for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) {
    const v = solid[j * w + i];
    if (!v) continue;
    if (v === 1) { c.set(x + i, y + j, isFace(i, j - 1) ? P.depth : P.depthDark); continue; }
    let col;
    if (style === 'sun') {
      col = P.sun[Math.min(P.sun.length - 1, Math.floor((j - top) / (bottom - top) * P.sun.length))];
      if (!isFace(i, j - 1)) col = P.sunTop;
    } else {
      col = !isFace(i, j - 1) ? P.peachLight : !isFace(i, j + 1) ? P.faceDark : !isFace(i - 1, j) ? P.faceLight : P.face;
    }
    c.set(x + i, y + j, col);
  }
  if (style !== 'sun') for (let i = 0; i < w; i++) for (let j = 0; j < h; j++) {
    if (!isFace(i, j) || isFace(i, j - 1) || rand() > 0.14) continue;
    const len = 1 + (rand() * 3 | 0), col = P.rust[2 + (rand() * 2 | 0)];
    for (let d = 1; d <= len && isFace(i, j + d) && isFace(i, j + d + 1); d++) c.set(x + i, y + j + d, col);
  }
  return {w, h};
}
const bigSize = (text, s, depth = Math.max(1, s - 1)) => ({w: boldWidth(text, s) + 2, h: 7 * s + 1 + depth + 2});

// ---------------- icons ----------------
const IK = {k: P.black, d: P.skyline, s: P.steel, l: P.steelLight, p: P.peach, o: P.sun[6], y: P.sun[2], w: P.sun[0], r: P.rust[3], m: hex('e2722a'), b: P.blurple, W: P.white, c: hex('b0521d')};
/** The radiation trefoil on a yellow disc: three black 60-degree blades (down, up-left, up-right) and a centre dot. */
function trefoil(n) {
  const rows = [], c = (n - 1) / 2;
  for (let j = 0; j < n; j++) {
    let row = '';
    for (let i = 0; i < n; i++) {
      const x = i - c, y = c - j, r = Math.hypot(x, y), deg = (Math.atan2(y, x) * 180 / Math.PI + 360) % 360;
      const blade = [270, 30, 150].some(m => Math.min(Math.abs(deg - m), 360 - Math.abs(deg - m)) <= 30);
      row += r > c + 0.5 ? '.' : r > c - 0.5 ? 'k' : r < 1.6 || r > 2.6 && r < c - 1 && blade ? 'k' : 'y';
    }
    rows.push(row);
  }
  return rows;
}
const ICONS = {
  discord: ['...kkk....kkk...', '..kbbbkkkkbbbk..', '.kbbbbbbbbbbbbk.', '.kbbbbbbbbbbbbk.', 'kbbbbbbbbbbbbbbk', 'kbbbWWbbbbWWbbbk',
    'kbbbWWbbbbWWbbbk', 'kbbbbbbbbbbbbbbk', 'kbbbbbbbbbbbbbbk', '.kbbbkkkkkkbbbk.', '..kkk......kkk..'],
  wiki: ['.kkkkkk..kkkkkk.', 'kwwwwwwkkwwwwwwk', 'kwppppwkkwppppwk', 'kwwwwwwkkwwwwwwk', 'kwppppwkkwpppwwk', 'kwwwwwwkkwwwwwwk',
    'kwpppwwkkwppppwk', 'kwwwwwwkkwwwwwwk', 'krrrrrrkkrrrrrrk', '.kkkkkkkkkkkkkk.'],
  source: ['....kk....kk....', '...kyk....kyk...', '..kyk..kk..kyk..', '.kyk..kyk...kyk.', 'kyk...kyk....kyk', '.kyk.kyk....kyk.',
    '..kyk.kyk..kyk..', '...kyk.k..kyk...', '....kk....kk....'],
  warning: ['.......k.......', '......kyk......', '.....kyyyk.....', '.....kykyk.....', '....kyykyyk....', '....kyykyyk....',
    '...kyyykyyyk...', '...kyyyyyyyk...', '..kyyyykyyyyk..', '..kyyyyyyyyyk..', '.kyyyyyyyyyyyk.', 'kkkkkkkkkkkkkkk'],
  gun: ['kkkkkkkkkkkkk.', 'kllllllllllllk', 'kssssssssssssk', 'kkkkkkssskkkkk', '.....ksssk....', '....ksssk.....',
    '....kssk......', '...ksssk......', '...kkkkk......'],
  survival: ['...kkk...', '..kwwwk..', '..kwmwk..', '..kwmwk..', '..kwmwk..', '..kwmwk..', '.kwmmmwk.', 'kwmmmmmwk', 'kwmmmmmwk', '.kwmmmwk.', '..kkkkk..'],
  loot: ['..kkkk......', '.kwwwwk.....', 'kwyywwwk....', 'kwywwwwk....', 'kwwwwwwk....', '.kwwwwk.....', '..kkkkkk....',
    '......kssk..', '.......kssk.', '........kssk', '.........kk.'],
  fuel: ['...kkkk.....', '..kk..kk....', '.kkkkkkkkkk.', '.kcccccccck.', '.kcckcckcck.', '.kccckkccck.', '.kcckcckcck.',
    '.kcccccccck.', '.kcccccccck.', '.kkkkkkkkkk.'],
  power: ['......kkkk', '.....kyyk.', '....kyyk..', '...kyyk...', '..kyyyykk.', '.kkkkyyk..', '....kyk...', '...kyk....', '..kyk.....', '..kk......'],
  radiation: trefoil(15),
};
function icon(c, name, x, y, s = 1) {
  const rows = ICONS[name];
  for (let j = 0; j < rows.length; j++) for (let i = 0; i < rows[j].length; i++) {
    const col = IK[rows[j][i]];
    if (col) c.rect(x + i * s, y + j * s, s, s, col);
  }
  return {w: rows[0].length * s, h: rows.length * s};
}
const iconSize = (name, s = 1) => ({w: ICONS[name][0].length * s, h: ICONS[name].length * s});

// ---------------- cards ----------------
fs.mkdirSync(OUT, {recursive: true});
const made = [];

// link buttons: icon + steel word
for (const [name, word] of [['discord', 'DISCORD'], ['wiki', 'WIKI'], ['source', 'SOURCE']]) {
  seed = word.length * 97;
  const is = iconSize(name), t = bigSize(word, 2), W = 132, H = 30;
  const total = is.w + 6 + t.w, x0 = Math.round((W - total) / 2);
  if (total > W - 14) throw new Error(word + ' does not fit its button');
  const c = new Canvas(W, H);
  frame(c, 0, 0, W, H);
  icon(c, name, x0, Math.round((H - is.h) / 2));
  big(c, word, x0 + is.w + 6, Math.round((H - t.h) / 2) + 1, 2, 'steel');
  made.push(c.save(`link_${name}.png`));
}

// section header
{
  seed = 7;
  const c = new Canvas(300, 36);
  frame(c, 0, 0, 300, 36, dusk(c));
  const t = bigSize('FEATURES', 3);
  big(c, 'FEATURES', Math.round((300 - t.w) / 2), Math.round((36 - t.h) / 2) + 1, 3, 'steel');
  made.push(c.save('header_features.png'));
}

// feature strips: icon (x2) + sun title
for (const [name, title] of [['gun', 'GUNS & ATTACHMENTS'], ['survival', 'SURVIVAL'], ['loot', 'SCAVENGING'],
                             ['fuel', 'FUEL & FIRE'], ['power', 'POWER'], ['radiation', 'RADIATION & INDUSTRY']]) {
  seed = title.length * 31 + 5;
  const H = 40, c = new Canvas(300, H);
  frame(c, 0, 0, 300, H);
  const is = iconSize(name, 2);
  icon(c, name, 10 + Math.round((30 - is.w) / 2), Math.round((H - is.h) / 2), 2);
  const t = bigSize(title, 2);
  big(c, title, 10 + 30 + 8, Math.round((H - t.h) / 2) + 1, 2, 'sun');
  made.push(c.save(`feature_${name}.png`));
}

// notices
function notice(file, iconName, title, lines) {
  seed = title.length * 13 + lines.length;
  const h = 9 + 19 + lines.length * 10 + 8;
  const c = new Canvas(300, h);
  frame(c, 0, 0, 300, h);
  let x = 12;
  if (iconName) { const is = iconSize(iconName, 2); icon(c, iconName, 12, Math.round((h - is.h) / 2), 2); x = 12 + is.w + 10; }
  big(c, title, x, 9, 2, 'sun');
  lines.forEach((line, i) => {
    if (x + 1 + smallWidth(line) > 300 - 10) throw new Error('line too long: ' + line);
    small(c, line, x + 1, 9 + 19 + i * 10);
  });
  made.push(c.save(file));
}
notice('notice_alpha.png', 'warning', 'EARLY ALPHA', [
  'EXPECT BUGS AND MISSING CONTENT.',
  'NUMBERS AND BALANCE {o:WILL CHANGE}.',
  'UPDATES MAY BREAK WORLDS - {o:BACK THEM UP}.',
]);
notice('notice_requirements.png', null, 'REQUIREMENTS', [
  'MINECRAFT {o:1.20.1} · FORGE',
  'REQUIRED: {o:GECKOLIB} · {o:CURIOS} · {o:TERRABLENDER}',
  'OPTIONAL: JEI · JADE',
]);

// hero: the title art on a dusk sky (the title's own unit is about 12 px, so it is composited at full size)
if (fs.existsSync(TITLE)) {
  const t = readPng(fs.readFileSync(TITLE));
  const W = t.w + 156, H = t.h + 60, u = 12, out = Buffer.alloc(W * H * 4);
  const bands = [P.night, ...P.sky];
  for (let y = 0; y < H; y++) {
    const band = bands[Math.min(bands.length - 1, Math.floor(Math.floor(y / u) * u / H * bands.length))];
    for (let x = 0; x < W; x++) { const o = (y * W + x) * 4; out[o] = band[0]; out[o + 1] = band[1]; out[o + 2] = band[2]; out[o + 3] = 255; }
  }
  const ground = H - 5 * u;
  for (let y = ground; y < H; y++) for (let x = 0; x < W; x++) { const o = (y * W + x) * 4; out[o] = P.skyline[0]; out[o + 1] = P.skyline[1]; out[o + 2] = P.skyline[2]; }
  const ox = (W - t.w) >> 1, oy = 8;
  for (let y = 0; y < t.h; y++) for (let x = 0; x < t.w; x++) {
    const i = (y * t.w + x) * 4, a = t.px[i + 3] / 255;
    if (a <= 0) continue;
    const o = ((y + oy) * W + x + ox) * 4;
    for (let k = 0; k < 3; k++) out[o + k] = Math.round(out[o + k] * (1 - a) + t.px[i + k] * a);
  }
  fs.writeFileSync(path.join(OUT, 'hero.png'), png(out, W, H));
  made.push(`hero.png ${W}x${H}`);
}
console.log('wrote to ' + OUT + ':\n  ' + made.join('\n  '));
