// 128 px survivor skin samples (user 2026-10-26: "你先出5个128px的皮肤给我看看，可以有各式各样的人"). The vanilla 64x64
// player net doubled, so each model pixel is 2x2 texels. Only the person is painted: skin, face, hair, beard and
// underwear. Clothes are meant to be separate meshes on the old cube body, so none are painted here. Review samples
// only: nothing in this folder is read by the game.
//
//   node tools/survivor-skins-128.mjs     writes design/characters/survivor_skins_128/<id>.png and
//                                         survivor_skins_128_r1.png (front, three-quarter and back views on the
//                                         vanilla player boxes, plus two head close-ups per person)
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import {fileURLToPath} from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const OUT = path.join(ROOT, 'design/characters/survivor_skins_128');
const R = 2, SIZE = 64 * R;

// ---------------------------------------------------------------- PNG

const crcTable = Array.from({length: 256}, (_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});
const crc32 = buf => {
  let c = 0xFFFFFFFF;
  for (const b of buf) c = crcTable[(c ^ b) & 0xFF] ^ (c >>> 8);
  return (c ^ 0xFFFFFFFF) >>> 0;
};
function png(width, height, rgba) {
  const raw = Buffer.alloc((width * 4 + 1) * height);
  for (let y = 0; y < height; y++) {
    raw[y * (width * 4 + 1)] = 0;
    Buffer.from(rgba.buffer, rgba.byteOffset + y * width * 4, width * 4).copy(raw, y * (width * 4 + 1) + 1);
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8;
  ihdr[9] = 6;
  const chunk = (type, data) => {
    const len = Buffer.alloc(4);
    len.writeUInt32BE(data.length);
    const td = Buffer.concat([Buffer.from(type), data]), crc = Buffer.alloc(4);
    crc.writeUInt32BE(crc32(td));
    return Buffer.concat([len, td, crc]);
  };
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw, {level: 9})), chunk('IEND', Buffer.alloc(0))]);
}

// ---------------------------------------------------------------- colour (same shading model as build-survivor-skins)

const hexRgb = hex => [parseInt(hex.slice(1, 3), 16), parseInt(hex.slice(3, 5), 16), parseInt(hex.slice(5, 7), 16)];
const rgbHex = rgb => '#' + rgb.map(v => Math.max(0, Math.min(255, Math.round(v))).toString(16).padStart(2, '0')).join('');
const mix = (a, b, t) => {
  const A = hexRgb(a), B = hexRgb(b);
  return rgbHex(A.map((v, k) => v + (B[k] - v) * t));
};
function rgbHsl([r, g, b]) {
  r /= 255; g /= 255; b /= 255;
  const max = Math.max(r, g, b), min = Math.min(r, g, b), l = (max + min) / 2;
  if (max === min) return [0, 0, l];
  const d = max - min, s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
  let h = max === r ? (g - b) / d + (g < b ? 6 : 0) : max === g ? (b - r) / d + 2 : (r - g) / d + 4;
  return [h * 60, s, l];
}
function hslRgb([h, s, l]) {
  if (s === 0) return [l * 255, l * 255, l * 255];
  const q = l < 0.5 ? l * (1 + s) : l + s - l * s, p = 2 * l - q;
  const f = t => {
    t = (t + 1) % 1;
    if (t < 1 / 6) return p + (q - p) * 6 * t;
    if (t < 1 / 2) return q;
    if (t < 2 / 3) return p + (q - p) * (2 / 3 - t) * 6;
    return p;
  };
  return [f(h / 360 + 1 / 3) * 255, f(h / 360) * 255, f(h / 360 - 1 / 3) * 255];
}
const towards = (h, target, deg) => {
  const d = ((target - h + 540) % 360) - 180;
  return (h + Math.sign(d) * Math.min(Math.abs(d), deg) + 360) % 360;
};
function tone(hex, level) {
  let [h, s, l] = rgbHsl(hexRgb(hex));
  if (level < 0) {
    const k = -level;
    l = Math.max(0.02, l - 0.07 * k * (0.35 + l));
    s = Math.min(1, s + 0.03 * k);
    h = towards(h, 250, 5 * k);
  } else if (level > 0) {
    const k = level;
    l = Math.min(0.96, l + 0.06 * k * (0.55 + l));
    s = Math.max(0, s - 0.04 * k);
    h = towards(h, 55, 4 * k);
  }
  return hslRgb([h, s, l]).map(Math.round);
}

// Fixed per-texel noise, so a rerun writes the same files.
function hash(a, b, c) {
  let h = Math.imul(a | 0, 374761393) ^ Math.imul(b | 0, 668265263) ^ Math.imul((c | 0) + 7, 1013904223);
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  h ^= h >>> 16;
  return (h >>> 0) / 4294967296;
}

// ---------------------------------------------------------------- the skin net (model pixels; texels are R times)

const PARTS = slim => ({
  head: {base: [0, 0], over: [32, 0], w: 8, h: 8, d: 8},
  body: {base: [16, 16], over: [16, 32], w: 8, h: 12, d: 4},
  rarm: {base: [40, 16], over: [40, 32], w: slim ? 3 : 4, h: 12, d: 4, side: 'R'},
  larm: {base: [32, 48], over: [48, 48], w: slim ? 3 : 4, h: 12, d: 4, side: 'L'},
  rleg: {base: [0, 16], over: [0, 32], w: 4, h: 12, d: 4, side: 'R'},
  lleg: {base: [16, 48], over: [0, 48], w: 4, h: 12, d: 4, side: 'L'},
});
const FACES = ['top', 'bottom', 'right', 'front', 'left', 'back'];

function faceRect(face, u, v, w, h, d) {
  switch (face) {
    case 'top': return [u + d, v, w, d];
    case 'bottom': return [u + d + w, v, w, d];
    case 'right': return [u, v + d, d, h];
    case 'front': return [u + d, v + d, w, h];
    case 'left': return [u + d + w, v + d, d, h];
    default: return [u + d + w + d, v + d, w, h];   // back
  }
}

// Part-local texel position: x from the character's right side (0) to the left side, y from the top, z from the back
// (0) to the front.
function local(face, i, j, w, h, d) {
  switch (face) {
    case 'front': return [i, j, d - 1];
    case 'back': return [w - 1 - i, j, 0];
    case 'right': return [0, j, i];
    case 'left': return [w - 1, j, d - 1 - i];
    case 'top': return [i, 0, j];
    default: return [i, h - 1, d - 1 - j];   // bottom
  }
}

// ---------------------------------------------------------------- people

const SKIN = {
  s1: '#EDC6A6', s2: '#DDA987', s3: '#C4916A', s4: '#AA7654', s5: '#8A5A3B', s6: '#5F3B27',
  ea: '#E3B994', sa: '#B07C58', na: '#9C6A47', old: '#D3A386',
};

// Head texels are 16 per side. fringe(x): hair rows on the face; side(z): rows on the sides, z 0 at the back;
// back(x): rows on the back; fade: rows of clipper fade under side and back; over: rows kept on the hat layer.
const STYLES = {
  receding: {
    fringe: x => (x <= 2 || x >= 13 ? 2 : 0),
    side: z => (z <= 8 ? 6 : z <= 11 ? 10 : z <= 13 ? 4 : 2),
    back: () => 12, fade: 2,
    bald: (x, z) => (z >= 12 && x >= 3 && x <= 12) || (z >= 10 && x >= 5 && x <= 10),
    over: {front: 0, side: 3, back: 8},
  },
  natural: {
    fringe: x => 2 + (hash(x, 7, 1) < 0.45 ? 1 : 0),
    side: z => (z >= 13 ? 4 : 7),
    back: () => 12, fade: 0, curly: true,
    over: {front: 2, side: 6, back: 11, ragged: 0.5},
  },
  crop: {
    fringe: x => [3, 3, 4, 4, 5, 4, 4, 5, 5, 4, 4, 4, 3, 3, 2, 2][x],
    side: z => (z >= 13 ? 3 : 4),
    back: () => 8, fade: 3,
    over: {front: 1, side: 2, back: 4, ragged: 0.5},
  },
  ponytail: {
    fringe: x => (x <= 1 || x >= 14 ? 6 : x === 7 || x === 8 ? 1 : 2),
    side: z => (z <= 5 ? 10 : z <= 9 ? 6 : 8),
    back: () => 16, fade: 0, part: x => x === 7, tail: true,
    over: {front: 1, side: 4, back: 6},
  },
  sidePart: {
    fringe: x => (x === 4 ? 1 : 2),
    side: () => 5,
    back: () => 9, fade: 2, part: x => x === 4,
    over: {front: 2, side: 2, back: 5},
  },
};

const PEOPLE = {
  p1: {
    first: 'Dale', last: 'Whitaker', note: '58, long-haul driver', slim: false, skin: SKIN.old, torso: '#E6BEA2',
    hair: '#ABA79F', hairDark: '#5A554F', brow: '#7A746C', style: 'receding', iris: '#5C7486', lip: '#A0605A',
    beard: {type: 'stubble', color: '#8E8A84'}, age: 2,
    under: {type: 'boxer', color: '#8C8E92', band: '#3E4044'},
  },
  p2: {
    first: 'Renee', last: 'Okafor', note: '34, nurse', slim: true, skin: SKIN.s6, hair: '#16110E', brow: '#120D0B',
    style: 'natural', iris: '#2E1E14', lip: '#5A2E28', lipMix: 0.55, age: 0,
    under: {type: 'briefs', color: '#26262A', band: '#3A3A40'}, bra: '#26262A',
  },
  p3: {
    first: 'Kevin', last: 'Tran', note: '24, line cook', slim: false, skin: SKIN.ea, hair: '#16120F', brow: '#1E1915',
    style: 'crop', iris: '#2A1E16', lip: '#B26A5E', age: 0, fit: true,
    under: {type: 'boxer', color: '#2C3548', band: '#B4B8BE'},
  },
  p4: {
    first: 'Marisol', last: 'Vega', note: '43, school teacher', slim: true, skin: SKIN.s3, hair: '#2A1A12', brow: '#24170F',
    style: 'ponytail', tie: '#7A2E36', iris: '#5E5230', lip: '#9A4E48', lipMix: 0.5, age: 1,
    under: {type: 'briefs', color: '#5A3A52', band: '#4A2E44'}, bra: '#5A3A52',
  },
  p5: {
    first: 'Omar', last: 'Haddad', note: '36, electrician', slim: false, skin: SKIN.sa, hair: '#17120F', brow: '#16110E',
    style: 'sidePart', iris: '#3A2618', lip: '#8A5244', age: 0, fit: true,
    beard: {type: 'full', color: '#18130F'},
    under: {type: 'boxer', color: '#3A3B3E', band: '#1E1F22'},
  },
};

// Palms and soles are lighter than the rest of the skin, the more so the darker the skin.
const palmOf = S => mix(S.skin, '#D8A88E', Math.max(0, 0.75 - rgbHsl(hexRgb(S.skin))[2]) * 0.9);
const grain = (x, y, z, k) => (hash(x * 31 + z, y, k) - 0.5) * 0.3;

// ---------------------------------------------------------------- head

function hairColour(S, x, y, z) {
  return S.hairDark && hash(x * 5 + z, y >> 1, 5) < 0.35 ? S.hairDark : S.hair;
}

function hairShade(S, st, face, x, y, z, rows) {
  const col = face === 'right' || face === 'left' ? z : x, run = face === 'top' ? z : y;
  let lvl;
  if (st.curly) {   // tight coils: 2x2 clumps lit on one corner
    const n = hash(col >> 1, run >> 1, 11), lit = (col & 1) === 0 && (run & 1) === 0;
    lvl = (n - 0.5) * 0.9 + (lit ? 0.5 : -0.2);
  } else {          // strands along the fall of the hair
    lvl = (hash(col, run >> 2, 3) - 0.5) * 0.9;
  }
  lvl += face === 'top' ? 0.4 : -0.1;
  if (face !== 'top' && rows && y === rows - 1) lvl -= 0.3;
  if (face === 'top' && st.part && st.part(x) && z >= 8) lvl -= 1.0;
  return [hairColour(S, x, y, z), lvl];
}

function hairBase(S, face, x, y, z) {
  const st = STYLES[S.style];
  if (face === 'bottom') return null;
  if (face === 'top') return st.bald && st.bald(x, z) ? null : hairShade(S, st, face, x, y, z, 0);
  const rows = face === 'front' ? st.fringe(x) : face === 'back' ? st.back(x) : st.side(z);
  if (st.tail && face === 'back' && x >= 6 && x <= 9 && y >= 9) {
    if (y <= 10) return [S.tie, y === 9 ? 0.2 : -0.6];
    return [S.hair, x === 6 || x === 9 ? -0.8 : (hash(x, y >> 2, 9) - 0.5) * 0.8 + 0.2];
  }
  if (y < rows) return hairShade(S, st, face, x, y, z, rows);
  if (st.fade && face !== 'front' && y < rows + st.fade) {
    const t = 0.55 - 0.2 * (y - rows);
    return hash(x * 7 + z, y, 13) < 0.3 ? null : [mix(S.skin, S.hair, t), -0.2];
  }
  return null;
}

function hairOver(S, face, x, y, z) {
  const st = STYLES[S.style], o = st.over;
  if (face === 'bottom') return null;
  if (face === 'top') {
    if (st.bald && st.bald(x, z)) return null;
    const [c, l] = hairShade(S, st, face, x, y, z, 0);
    return [c, l + 0.2];
  }
  if (st.tail && face === 'back' && x >= 5 && x <= 10 && y >= 7 && y <= 12) {
    if (y >= 9 && y <= 10) return [S.tie, y === 9 ? 0.3 : -0.4];
    return [S.hair, (x === 5 || x === 10 ? -0.6 : 0.3) + (hash(x, y, 21) - 0.5) * 0.6];
  }
  const full = face === 'front' ? st.fringe(x) : face === 'back' ? st.back(x) : st.side(z);
  const rows = Math.min(full, face === 'front' ? o.front : face === 'back' ? o.back : o.side);
  if (y >= rows) return null;
  if (y === rows - 1 && hash(x * 3 + z, y, 17) < (o.ragged ?? 0.25)) return null;
  const [c, l] = hairShade(S, st, face, x, y, z, rows);
  return [c, l + 0.25];
}

// Beard on the base layer; full beards also get a little volume on the hat layer.
function beardAt(S, face, x, y, z, over) {
  const b = S.beard;
  if (!b) return null;
  const mx = Math.min(x, 15 - x);
  let on = false;
  if (b.type === 'stubble') {
    if (over) return null;
    if (face === 'front') on = (y >= 10 && mx <= 2) || (y >= 11 && !(y >= 12 && y <= 13 && mx >= 6)) || y >= 14;
    else if (face === 'right' || face === 'left') on = z >= 10 && y >= 9;
    else if (face === 'bottom') on = z >= 8;
    if (!on) return null;
    const n = hash(x * 11 + z, y, 19);
    return n < 0.12 ? null : [mix(S.skin, b.color, 0.3 + n * 0.12), -0.15];
  }
  if (over) {
    if (face === 'front') on = y >= 13 && !(y === 13 && mx >= 6);
    else if (face === 'right' || face === 'left') on = z >= 10 && y >= 12;
    else if (face === 'bottom') on = z >= 8;
    if (!on || hash(x * 13 + z, y, 23) < 0.25) return null;
    return [b.color, 0.2 + (hash(x, y, 27) - 0.5) * 0.8];
  }
  let edge = false;
  if (face === 'front') {
    const lips = (y === 12 && mx >= 6) || (y === 13 && mx >= 7);
    on = !lips && ((y >= 9 && mx <= 1) || (y >= 10 && mx <= 3) || y >= 11);
    edge = (y === 9 && mx <= 1) || (y === 10 && mx >= 2 && mx <= 3) || (y === 11 && mx >= 4 && mx <= 5);
  } else if (face === 'right' || face === 'left') {
    on = (z >= 9 && y >= 5) || (z >= 6 && y >= 12);
    edge = (z === 9 && y < 12) || (y === 12 && z < 9);
  } else if (face === 'bottom') on = z >= 5;
  if (!on) return null;
  if (edge) return hash(x, y * 3 + z, 29) < 0.4 ? null : [mix(S.skin, b.color, 0.6), -0.2];
  return [b.color, (hash(x * 3 + z, y >> 1, 31) - 0.5) * 0.9 + (face === 'bottom' ? -0.6 : 0)];
}

function faceFront(S, x, y) {
  const mx = Math.min(x, 15 - x), sk = S.skin, g = grain(x, y, 15, 1);
  const lip = mix(sk, S.lip, S.lipMix ?? 0.4);
  // brows
  if (y === 7 && mx >= 2 && mx <= 5) return [S.brow, mx === 2 ? 0.3 : -0.2];
  if (y === 6 && mx >= 3 && mx <= 5 && S.slim === false && S.age < 2) return [mix(sk, S.brow, 0.5), 0];
  // eyes: white on the outer side, iris on the inner side, pupil low on the iris
  if (y === 8 || y === 9) {
    if (mx === 2) return y === 8 ? [sk, -1.0] : ['#E6E0D8', -0.6];
    if (mx === 3) return ['#E6E0D8', y === 8 ? -0.9 : 0];
    if (mx === 4) return [S.iris, y === 8 ? -0.9 : -1.8];
    if (mx === 5) return [S.iris, y === 8 ? -0.6 : 0.4];
  }
  if (y === 10 && mx >= 2 && mx <= 5) return [sk, -0.25 - 0.3 * S.age + g];   // under-eye
  if (S.age >= 2) {
    if ((y === 3 || y === 5) && mx >= 4) return [sk, -0.45 + g];             // forehead lines
    if (mx === 1 && (y === 8 || y === 10)) return [sk, -0.5];                 // crow's feet
    if (y === 12 && mx === 5) return [sk, -0.6];                              // smile lines
  }
  // nose
  if (mx === 7 && (y === 8 || y === 9)) return [sk, 0.15 + g];
  if (y === 10 && mx >= 6) return [sk, mx === 7 ? 0.35 : -0.1];
  if (y === 11 && mx >= 6) return [sk, mx === 7 ? -1.1 : -0.5];
  // mouth
  if (y === 12 && mx >= 6) return [lip, -1.2];
  if (y === 13 && mx >= 6) return [lip, mx === 6 ? -0.4 : 0.1];
  if (y === 14 && mx === 7) return [sk, -0.35];
  if (y === 15) return [sk, -0.6 + g];
  // cheeks and forehead
  return [sk, (mx === 0 ? -0.45 : mx === 1 ? -0.15 : 0) + (y <= 1 ? 0.15 : 0) + g];
}

function headBase(S, face, x, y, z) {
  const st = STYLES[S.style];
  const beard = beardAt(S, face, x, y, z, false);
  if (beard) return beard;
  const hair = hairBase(S, face, x, y, z);
  if (hair) return hair;
  const sk = S.skin, g = grain(x, y, z, 2);
  if (face === 'front') {
    // skin just under the hair line sits in its shadow
    if (y === st.fringe(x) && y > 0) return [sk, -0.45 + g];
    return faceFront(S, x, y);
  }
  if (face === 'top') return [sk, 0.35 + g];   // bald crown
  if (face === 'bottom') return [sk, -1.5];
  if (face === 'right' || face === 'left') {
    if (z >= 6 && z <= 8 && y >= 7 && y <= 11) {   // ear
      const inner = z >= 7 && y >= 8 && y <= 10;
      return [sk, inner ? (z === 7 ? -1.3 : -0.7) : z === 6 ? -0.2 : -0.5];
    }
    return [sk, (y >= 14 ? -0.8 : -0.25) + g];
  }
  return [sk, (y >= 14 ? -0.9 : -0.3) + g];
}

// ---------------------------------------------------------------- body, arms, legs

function fabric(colour, x, y, z, lvl) {
  return [colour, lvl + (hash(x * 17 + z, y, 41) - 0.5) * 0.25];
}

function bodyBase(S, face, x, y, z) {
  const sk = S.torso || S.skin, g = grain(x, y, z, 3), mx = Math.min(x, 15 - x), u = S.under;
  const female = !!S.bra;
  // underwear
  const waist = u.type === 'boxer' ? 19 : 20;
  if (face === 'bottom') return fabric(u.color, x, y, z, -0.9);
  if (face !== 'top' && y >= waist) {
    if (y <= waist + (u.type === 'boxer' ? 1 : 0)) return fabric(u.band, x, y, z, y === waist ? 0.2 : -0.2);
    if (face === 'front' && u.type === 'boxer' && x === 7 && y >= 21) return fabric(u.color, x, y, z, -0.6);
    return fabric(u.color, x, y, z, (mx === 0 ? -0.4 : 0) + (face === 'back' ? -0.1 : 0));
  }
  // sports bra
  if (female) {
    const strap = mx >= 3 && mx <= 4;
    if (face === 'top' && strap) return fabric(S.bra, x, y, z, 0.3);
    if (face === 'front') {
      if (y >= 10 && y <= 11) return fabric(S.bra, x, y, z, y === 11 ? -0.5 : -0.2);
      if (y >= 5 && y <= 9 && (mx <= 6 || y >= 8)) return fabric(S.bra, x, y, z, y === 5 ? 0.25 : y === 9 && mx >= 3 ? -0.5 : 0);
      if (y < 5 && strap) return fabric(S.bra, x, y, z, 0);
      if (y >= 6 && y <= 7 && mx === 7) return [sk, -0.8];
    } else if (face === 'back') {
      if (y >= 9 && y <= 11) return fabric(S.bra, x, y, z, y === 11 ? -0.5 : 0);
      if (y < 9 && strap) return fabric(S.bra, x, y, z, -0.1);
    } else if (face !== 'top' && y >= 6 && y <= 11) return fabric(S.bra, x, y, z, -0.3);
  }
  if (S.style === 'ponytail' && face === 'back' && y <= 7 && (x >= 7 && x <= 8 || (y <= 3 && x >= 6 && x <= 9))) {
    return [S.hair, x === 6 || x === 9 ? -0.7 : (hash(x, y >> 2, 9) - 0.5) * 0.8];
  }
  if (face === 'top') return [sk, 0.3 + g];
  if (face === 'front') {
    if (y === 0 && mx >= 5) return [sk, -1.0];                  // under the chin
    if (y === 1 && mx >= 6) return [sk, -0.5];
    if (y === 2 && mx >= 2 && mx <= 5) return [sk, -0.4 + g];   // collarbones
    if (!female && y === 7 && mx >= 1 && mx <= 6) return [sk, -0.55 + g];
    if (y === 16 && mx === 7) return [sk, x === 7 ? -1.2 : -0.8];   // navel
    if (S.fit && mx === 7 && y >= 9 && y <= 17) return [sk, -0.3 + g];
    if (S.fit && (y === 11 || y === 14) && mx >= 4 && mx <= 6) return [sk, -0.2 + g];
    if (female && mx === 0 && y >= 12) return [sk, -0.6 + g];
    return [sk, (mx === 0 ? -0.45 : 0) + g];
  }
  if (face === 'back') {
    if (y === 0 && mx >= 5) return [sk, -0.6];
    if (mx === 7 && y >= 3 && y <= 18) return [sk, -0.35 + g];   // spine
    if (y === 9 && mx >= 3 && mx <= 5) return [sk, -0.45 + g];   // shoulder blades
    return [sk, (mx === 0 ? -0.5 : -0.1) + g];
  }
  return [sk, (y <= 3 ? -0.8 : -0.3) + g];   // flanks, darker under the arms
}

function armBase(S, part, face, x, y, z, W) {
  const sk = S.skin, g = grain(x, y, z, 4);
  const inner = part.side === 'R' ? 'left' : 'right', outer = part.side === 'R' ? 'right' : 'left';
  if (face === 'top') return [S.torso || sk, 0.3 + g];
  if (face === 'bottom') return [palmOf(S), -0.5];
  // farmer's tan: the upper arm stays as pale as the torso
  const skin = S.torso && y <= 5 ? (y === 5 ? mix(S.torso, sk, 0.5) : S.torso) : sk;
  if (y >= 18) {   // hand: back of the hand outwards, palm towards the body, thumb forwards
    if (face === inner) return [palmOf(S), (y >= 20 && (z & 1) ? -0.4 : 0) + g];
    if (face === outer) {
      if (y === 19) return [sk, 0.3];
      if (y >= 20 && (z === 2 || z === 4 || z === 6)) return [sk, -0.5];
      return [sk, y === 23 ? -0.4 : g];
    }
    if (face === 'front') {
      if (y >= 20 && y <= 22 && x === Math.floor(W / 2)) return [sk, -0.6];
      return [sk, -0.1 + g];
    }
    return [sk, -0.3 + g];
  }
  if (y === 17) return [sk, -0.3 + g];   // wrist
  if (face === inner) return [skin, (y <= 2 ? -1.1 : -0.55) + g];
  if (face === 'back' && y >= 11 && y <= 12) return [skin, y === 11 ? 0.25 : -0.5];   // elbow
  if (face === 'front' && y === 11) return [skin, -0.45];
  if (face === outer && y === 6 && S.fit) return [skin, -0.3];
  return [skin, (face === 'back' ? -0.2 : 0) + g];
}

function legBase(S, part, face, x, y, z, W) {
  const sk = S.skin, g = grain(x, y, z, 5), u = S.under;
  const inner = part.side === 'R' ? 'left' : 'right';
  if (face === 'top') return fabric(u.color, x, y, z, 0);
  if (face === 'bottom') return [palmOf(S), -0.6];
  if (u.type === 'boxer' && y <= 5) return fabric(u.color, x, y, z, y === 5 ? -0.7 : face === inner ? -0.5 : 0);
  if (u.type === 'briefs') {
    const innerHalf = face === inner || (face === 'front' || face === 'back') && (part.side === 'R' ? x >= 4 : x <= 3);
    if (y === 0 || (y === 1 && innerHalf)) return fabric(u.color, x, y, z, y === 1 ? -0.5 : -0.2);
  }
  if (y >= 21) {   // bare feet
    if (face === 'front') return [sk, y === 21 ? -0.3 : (y >= 22 && (x === 2 || x === 4 || x === 6) ? -0.6 : 0.1)];
    return [sk, (face === inner ? -0.5 : -0.2) + g];
  }
  if (y === 20 && (face === inner || face === (part.side === 'R' ? 'right' : 'left')) && z >= 3 && z <= 4) return [sk, 0.35];
  if (face === inner) return [sk, (y <= 4 ? -0.8 : -0.5) + g];
  if (face === 'front' && y === 10) return [sk, 0.25 + g];   // knee
  if (face === 'front' && y === 12) return [sk, -0.35 + g];
  if (face === 'back' && y === 11) return [sk, -0.6];
  if (face === 'back' && y >= 13 && y <= 16) return [sk, 0.1 + g];
  return [sk, (face === 'back' ? -0.2 : 0) + g];
}

// ---------------------------------------------------------------- painting

function paintSkin(S) {
  const px = new Uint8Array(SIZE * SIZE * 4), parts = PARTS(S.slim);
  for (const [name, p] of Object.entries(parts)) {
    for (const layer of ['base', 'over']) {
      const W = p.w * R, H = p.h * R, D = p.d * R;
      for (const face of FACES) {
        const [fu, fv, fw, fh] = faceRect(face, p[layer][0] * R, p[layer][1] * R, W, H, D);
        for (let j = 0; j < fh; j++) {
          for (let i = 0; i < fw; i++) {
            const [x, y, z] = local(face, i, j, W, H, D);
            let c = null;
            if (layer === 'over') {
              if (name === 'head') c = beardAt(S, face, x, y, z, true) || hairOver(S, face, x, y, z);
            } else if (name === 'head') c = headBase(S, face, x, y, z);
            else if (name === 'body') c = bodyBase(S, face, x, y, z);
            else if (name.endsWith('arm')) c = armBase(S, p, face, x, y, z, W);
            else c = legBase(S, p, face, x, y, z, W);
            if (!c) continue;
            const rgb = tone(c[0], c[1]), o = ((fv + j) * SIZE + fu + i) * 4;
            px[o] = rgb[0]; px[o + 1] = rgb[1]; px[o + 2] = rgb[2]; px[o + 3] = 255;
          }
        }
      }
    }
  }
  return px;
}

// ---------------------------------------------------------------- review sheet (raycast on the vanilla player boxes)

const L0 = (() => { const v = [0.2, 1, -0.7], n = Math.hypot(...v); return v.map(c => c / n); })();
const L1 = (() => { const v = [-0.2, 1, 0.7], n = Math.hypot(...v); return v.map(c => c / n); })();
const light = n => {
  const d0 = Math.max(0, n[0] * L0[0] + n[1] * L0[1] + n[2] * L0[2]);
  const d1 = Math.max(0, n[0] * L1[0] + n[1] * L1[1] + n[2] * L1[2]);
  return Math.min(1, (0.4 + 0.6 * (d0 + d1)) / 0.74);
};

function modelBoxes(slim) {
  const aw = slim ? 3 : 4, boxes = [];
  const add = (u, v, w, h, d, min, inf) => boxes.push({u: u * R, v: v * R, w: w * R, h: h * R, d: d * R,
    min: [min[0] - inf, min[1] - inf, min[2] - inf], max: [min[0] + w + inf, min[1] + h + inf, min[2] + d + inf]});
  add(0, 0, 8, 8, 8, [-4, 24, -4], 0);
  add(32, 0, 8, 8, 8, [-4, 24, -4], 0.5);
  add(16, 16, 8, 12, 4, [-4, 12, -2], 0);
  add(16, 32, 8, 12, 4, [-4, 12, -2], 0.25);
  add(40, 16, aw, 12, 4, [-4 - aw, 12, -2], 0);
  add(40, 32, aw, 12, 4, [-4 - aw, 12, -2], 0.25);
  add(32, 48, aw, 12, 4, [4, 12, -2], 0);
  add(48, 48, aw, 12, 4, [4, 12, -2], 0.25);
  add(0, 16, 4, 12, 4, [-4, 0, -2], 0);
  add(0, 32, 4, 12, 4, [-4, 0, -2], 0.25);
  add(16, 48, 4, 12, 4, [0, 0, -2], 0);
  add(0, 48, 4, 12, 4, [0, 0, -2], 0.25);
  return boxes;
}

function texel(b, axis, sign, hit) {
  const f = k => (hit[k] - b.min[k]) / (b.max[k] - b.min[k]);
  const cl = (v, n) => Math.max(0, Math.min(n - 1, Math.floor(v * n)));
  const ix = cl(f(0), b.w), iy = cl(1 - f(1), b.h), iz = cl(f(2), b.d);
  const {u, v, w, h, d} = b;
  if (axis === 2) return sign > 0 ? [u + d + ix, v + d + iy] : [u + 2 * d + w + (w - 1 - ix), v + d + iy];
  if (axis === 0) return sign < 0 ? [u + iz, v + d + iy] : [u + d + w + (d - 1 - iz), v + d + iy];
  return sign > 0 ? [u + d + ix, v + iz] : [u + d + w + ix, v + (d - 1 - iz)];
}

function renderView(px, slim, yawDeg, pitchDeg, scale, target, W, H, out, ox, oy, outW) {
  const t = yawDeg * Math.PI / 180, f = pitchDeg * Math.PI / 180;
  const c = [Math.sin(t) * Math.cos(f), Math.sin(f), Math.cos(t) * Math.cos(f)];
  const rl = Math.hypot(c[2], c[0]), r = [c[2] / rl, 0, -c[0] / rl];
  const up = [c[1] * r[2] - c[2] * r[1], c[2] * r[0] - c[0] * r[2], c[0] * r[1] - c[1] * r[0]];
  const dir = c.map(v => -v), boxes = modelBoxes(slim);
  for (let py = 0; py < H; py++) {
    for (let qx = 0; qx < W; qx++) {
      const s = (qx - W / 2 + 0.5) / scale, q = (H / 2 - py - 0.5) / scale;
      const o = [0, 1, 2].map(k => target[k] + r[k] * s + up[k] * q + c[k] * 100);
      const hits = [];
      for (const b of boxes) {
        let tn = -Infinity, tf = Infinity, axis = -1, sign = 0, miss = false;
        for (let k = 0; k < 3; k++) {
          if (Math.abs(dir[k]) < 1e-9) {
            if (o[k] < b.min[k] || o[k] > b.max[k]) { miss = true; break; }
            continue;
          }
          let t1 = (b.min[k] - o[k]) / dir[k], t2 = (b.max[k] - o[k]) / dir[k];
          const sg = t1 < t2 ? -1 : 1;
          if (t1 > t2) [t1, t2] = [t2, t1];
          if (t1 > tn) { tn = t1; axis = k; sign = sg; }
          tf = Math.min(tf, t2);
        }
        if (!miss && tn <= tf) hits.push({tn, b, axis, sign});
      }
      hits.sort((a, b2) => a.tn - b2.tn);
      for (const hit of hits) {
        const pt = o.map((v, k) => v + dir[k] * hit.tn);
        const [tx, ty] = texel(hit.b, hit.axis, hit.sign, pt);
        const si = (ty * SIZE + tx) * 4;
        if (!px[si + 3]) continue;
        const n = [0, 0, 0];
        n[hit.axis] = hit.sign;
        const k = light(n), oi = ((oy + py) * outW + ox + qx) * 4;
        out[oi] = px[si] * k; out[oi + 1] = px[si + 1] * k; out[oi + 2] = px[si + 2] * k; out[oi + 3] = 255;
        break;
      }
    }
  }
}

const GLYPHS = {
  A: ['01110', '10001', '10001', '11111', '10001', '10001', '10001'],
  B: ['11110', '10001', '10001', '11110', '10001', '10001', '11110'],
  C: ['01110', '10001', '10000', '10000', '10000', '10001', '01110'],
  D: ['11110', '10001', '10001', '10001', '10001', '10001', '11110'],
  E: ['11111', '10000', '10000', '11110', '10000', '10000', '11111'],
  F: ['11111', '10000', '10000', '11110', '10000', '10000', '10000'],
  G: ['01110', '10001', '10000', '10111', '10001', '10001', '01111'],
  H: ['10001', '10001', '10001', '11111', '10001', '10001', '10001'],
  I: ['01110', '00100', '00100', '00100', '00100', '00100', '01110'],
  J: ['00111', '00010', '00010', '00010', '00010', '10010', '01100'],
  K: ['10001', '10010', '10100', '11000', '10100', '10010', '10001'],
  L: ['10000', '10000', '10000', '10000', '10000', '10000', '11111'],
  M: ['10001', '11011', '10101', '10101', '10001', '10001', '10001'],
  N: ['10001', '11001', '10101', '10011', '10001', '10001', '10001'],
  O: ['01110', '10001', '10001', '10001', '10001', '10001', '01110'],
  P: ['11110', '10001', '10001', '11110', '10000', '10000', '10000'],
  Q: ['01110', '10001', '10001', '10001', '10101', '10010', '01101'],
  R: ['11110', '10001', '10001', '11110', '10100', '10010', '10001'],
  S: ['01111', '10000', '10000', '01110', '00001', '00001', '11110'],
  T: ['11111', '00100', '00100', '00100', '00100', '00100', '00100'],
  U: ['10001', '10001', '10001', '10001', '10001', '10001', '01110'],
  V: ['10001', '10001', '10001', '10001', '10001', '01010', '00100'],
  W: ['10001', '10001', '10001', '10101', '10101', '10101', '01010'],
  X: ['10001', '10001', '01010', '00100', '01010', '10001', '10001'],
  Y: ['10001', '10001', '01010', '00100', '00100', '00100', '00100'],
  Z: ['11111', '00001', '00010', '00100', '01000', '10000', '11111'],
  0: ['01110', '10001', '10011', '10101', '11001', '10001', '01110'],
  1: ['00100', '01100', '00100', '00100', '00100', '00100', '01110'],
  2: ['01110', '10001', '00001', '00110', '01000', '10000', '11111'],
  3: ['11110', '00001', '00001', '01110', '00001', '00001', '11110'],
  4: ['00010', '00110', '01010', '10010', '11111', '00010', '00010'],
  5: ['11111', '10000', '11110', '00001', '00001', '10001', '01110'],
  6: ['00110', '01000', '10000', '11110', '10001', '10001', '01110'],
  7: ['11111', '00001', '00010', '00100', '01000', '01000', '01000'],
  8: ['01110', '10001', '10001', '01110', '10001', '10001', '01110'],
  9: ['01110', '10001', '10001', '01111', '00001', '00010', '01100'],
  ',': ['00000', '00000', '00000', '00000', '00110', '00100', '01000'],
};
function label(out, outW, text, x, y, k) {
  for (const [n, ch] of [...text].entries()) {
    const g = GLYPHS[ch];
    if (!g) continue;
    for (let gy = 0; gy < 7; gy++) {
      for (let gx = 0; gx < 5; gx++) {
        if (g[gy][gx] !== '1') continue;
        for (let yy = 0; yy < k; yy++) {
          for (let xx = 0; xx < k; xx++) {
            const oi = ((y + gy * k + yy) * outW + x + n * 6 * k + gx * k + xx) * 4;
            out[oi] = 32; out[oi + 1] = 36; out[oi + 2] = 40; out[oi + 3] = 255;
          }
        }
      }
    }
  }
}

function sheet(entries, file) {
  const VW = 230, VH = 370, HW = 345, HH = 270, LABEL = 70, PAD = 20, COLS = entries.length;
  const cellW = VW * 3 + PAD, cellH = LABEL + VH + HH + PAD;
  const outW = cellW * COLS + PAD, outH = cellH + PAD;
  const out = new Uint8Array(outW * outH * 4);
  for (let i = 0; i < outW * outH; i++) {
    out[i * 4] = 0x9A; out[i * 4 + 1] = 0xA4; out[i * 4 + 2] = 0xAB; out[i * 4 + 3] = 255;
  }
  entries.forEach(([id, S, px], n) => {
    const cx = PAD + n * cellW, cy = PAD;
    for (let yy = cy + LABEL - 6; yy < cy + LABEL + VH + HH; yy++) {
      for (let xx = cx; xx < cx + VW * 3; xx++) {
        const oi = (yy * outW + xx) * 4;
        out[oi] = 0xB4; out[oi + 1] = 0xBC; out[oi + 2] = 0xC2;
      }
    }
    label(out, outW, (id.slice(1) + ' ' + S.first + ' ' + S.last).toUpperCase(), cx + 6, cy + 4, 4);
    label(out, outW, S.note.toUpperCase(), cx + 6, cy + 40, 3);
    [[0, 0], [35, 8], [180, 0]].forEach(([yaw, pitch], k) =>
      renderView(px, S.slim, yaw, pitch, 10, [0, 16.5, 0], VW, VH, out, cx + k * VW, cy + LABEL, outW));
    [[0, 0], [40, 6]].forEach(([yaw, pitch], k) =>
      renderView(px, S.slim, yaw, pitch, 22, [0, 28, 0], HW, HH, out, cx + k * HW, cy + LABEL + VH, outW));
  });
  fs.writeFileSync(file, png(outW, outH, out));
  console.log('sheet ' + path.relative(ROOT, file));
}

// ---------------------------------------------------------------- main

fs.mkdirSync(OUT, {recursive: true});
const rendered = [];
for (const [id, S] of Object.entries(PEOPLE)) {
  const px = paintSkin(S);
  rendered.push([id, S, px]);
  fs.writeFileSync(path.join(OUT, id + '.png'), png(SIZE, SIZE, px));
  console.log('skin ' + id + ' ' + S.first + ' ' + S.last);
}
sheet(rendered, path.join(OUT, 'survivor_skins_128_r1.png'));
