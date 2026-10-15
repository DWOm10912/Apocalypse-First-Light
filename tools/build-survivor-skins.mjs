// Survivor roster skins (docs/gameplay/survivor_death_succession_v0.md 13): one fixed person per skin, built from parts
// (skin tone, face, hair, beard, shirt, belt, trousers, footwear) on the vanilla 64x64 player skin net. Each spec also
// names its person; survivor/SurvivorRoster.java must list the same id and name (every run checks it, exit 1 if not).
//
// A skin carries only the base clothing layer: one shirt, trousers and footwear. Outer garments (jackets, coats, vests,
// coveralls), headwear, eyewear, gloves and packs are left out on purpose: they are future modular equipment, drawn on
// top of the body (user decision 2026-10-14).
//
// Both layers are used: the base layer holds the complete figure (it must still read when a viewer hides the outer
// layers), the outer layer adds hair volume, shirt collars, cuffs and hems, trouser hems and boot collars for depth.
// Female survivors use the slim (3 px) arms, male survivors the classic (4 px) arms.
//
//   node tools/build-survivor-skins.mjs                       writes the PNGs
//   node tools/build-survivor-skins.mjs --check               compares only (exit 1 when an output differs)
//   node tools/build-survivor-skins.mjs --sheet <dir>         also renders two review sheets into <dir>
//                                                             (survivor_skins_female.png / _male.png): front,
//                                                             three-quarter and back views with the vanilla player
//                                                             model and entity light
//   node tools/build-survivor-skins.mjs --sheet <dir> --only f07,m14   one sheet of the listed skins only
//                                                             (survivor_skins_selection.png), for close review
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import {fileURLToPath} from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const OUT = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/textures/entity/survivor');
const SIZE = 64;

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

// ---------------------------------------------------------------- colour

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

// One material colour at a shading level: 0 = as given, negative = shadow (darker, a little more saturated, hue towards
// blue-violet), positive = light (lighter, a little less saturated, hue towards yellow). Even steps for dark and light
// materials alike, so black boots still show a fold and white shirts still show a shadow.
const toneCache = new Map();
function tone(hex, level) {
  const key = hex + '/' + level;
  if (toneCache.has(key)) return toneCache.get(key);
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
  const out = hslRgb([h, s, l]).map(Math.round);
  toneCache.set(key, out);
  return out;
}

// ---------------------------------------------------------------- the skin net

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

// Part-local position of a face texel: x from the character's right side (0) to the left side (w-1), y from the top,
// z from the back (0) to the front (d-1). "right" is the character's right (the viewer's left in a front view).
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

// ---------------------------------------------------------------- head

const EYE_WHITE = '#E8E3DC';
// Short cuts share one outline: crown, temples, sideburns, nape.
const SHORT_CUTS = new Set(['short', 'fade', 'receding', 'buzz', 'messy', 'sidepart', 'pixie']);

function shortHair(D, p, H) {
  const {face, x, y, z} = p, a = 7 - z, st = D.hairStyle;
  const fade = mix(D.skin, H, 0.5);
  if (face === 'top') {
    if (st === 'receding' && z === 7 && x >= 2 && x <= 5) return null;
    if (st === 'sidepart' && x === 2 && z >= 3) return [H, -2];
    return [H, x >= 2 && x <= 5 && z >= 2 && z <= 5 ? 1 : 0];
  }
  if (face === 'front') {
    switch (st) {
      case 'receding': return y === 0 && (x <= 1 || x >= 6) ? [H, 0] : null;
      case 'sidepart':
        if (y === 0) return [H, x === 2 ? -1 : x >= 3 ? 1 : 0];
        return y === 1 && (x <= 1 || x >= 6) ? [H, -1] : null;
      case 'messy':
        if (y === 0) return [H, 0];
        return y === 1 && (x <= 1 || x === 3 || x >= 6) ? [H, -1] : null;
      case 'pixie':   // swept fringe to the left, longer at the temples
        if (y === 0) return [H, 0];
        if (y === 1 && x >= 2) return [H, x === 2 ? -1 : 0];
        if (y === 2 && x >= 5) return [H, -1];
        return (x === 0 && y <= 3) || (x === 7 && y <= 4) ? [H, y >= 3 ? -1 : 0] : null;
      default:
        return y === 0 ? [H, 0] : y === 1 && (x <= 1 || x >= 6) ? [H, -1] : null;
    }
  }
  if (face === 'back') {
    if (st === 'fade') return y <= 3 ? [H, 0] : y <= 6 ? [fade, y === 6 ? -1 : 0] : null;
    return y <= 5 ? [H, 0] : y === 6 ? [H, -1] : null;
  }
  if (y <= 1) return [H, 0];
  if (st === 'fade') return a >= 3 && y <= 6 ? [fade, a === 3 && y >= 3 ? -1 : 0] : null;
  if (st === 'pixie' && a === 1 && y <= 3) return [H, -1];
  if (a === 2 && y <= 2) return [H, 0];
  if (a === 3 && y <= 4) return [H, -1];
  if (a === 4 && y <= 2) return [H, 0];
  if (a >= 5 && y <= 6) return [H, y === 6 ? -1 : 0];
  return null;
}

function hairBase(D, p) {
  const {face, x, y, z} = p, a = 7 - z, st = D.hairStyle;
  if (face === 'bottom' || st === 'bald') return null;
  if (SHORT_CUTS.has(st)) return shortHair(D, p, st === 'buzz' ? mix(D.skin, D.hair, 0.78) : D.hair);
  const H = D.hair;
  if (st === 'balding') {
    // a fringe over the ears and round the back of the head; bare crown and forehead
    if (face === 'back') return y >= 2 && y <= 5 ? [H, y === 5 ? -1 : 0] : null;
    if (face === 'left' || face === 'right') return a >= 4 && y >= 2 && y <= 5 ? [H, a === 4 || y === 5 ? -1 : 0] : null;
    return null;
  }
  if (face === 'top') return [H, x >= 2 && x <= 5 && z >= 2 && z <= 5 ? 1 : 0];
  const side = face === 'left' || face === 'right';
  switch (st) {
    case 'long': case 'locs': {
      const strand = v => (st === 'locs' ? (v % 2 ? -1 : 0) : v % 3 === 1 ? -1 : 0);
      if (face === 'front') {
        if (y === 0) return [H, 0];
        if (y === 1 && (x <= 2 || x >= 6)) return [H, 0];
        if (y === 2 && x >= 6) return [H, -1];
        if ((x === 0 || x === 7) && y <= 6) return [H, y >= 4 ? -1 : 0];
        return null;
      }
      if (side) return a === 0 && y === 7 ? null : [H, y === 7 ? -1 : y >= 3 ? strand(z) : 0];
      return [H, y === 7 ? -1 : y >= 2 ? strand(x) : 0];
    }
    case 'bob': {
      if (face === 'front') {
        if (y === 0) return [H, 0];
        if (y === 1 && (x <= 1 || x >= 4)) return [H, 0];
        if (y === 2 && x >= 6) return [H, -1];
        if ((x === 0 || x === 7) && y <= 5) return [H, y >= 4 ? -1 : 0];
        return null;
      }
      return y <= 6 ? [H, y === 6 ? -1 : 0] : null;
    }
    case 'ponytail': case 'bun': case 'braid': {
      if (face === 'front') return y === 0 ? [H, 0] : y === 1 && (x <= 1 || x >= 6) ? [H, -1] : null;
      if (face === 'back') {
        if (st !== 'bun' && y === 6 && (x === 3 || x === 4)) return [D.tie, 0];
        if (st !== 'bun' && y === 7 && (x === 3 || x === 4)) return [H, -1];
        return y <= 5 ? [H, 0] : y === 6 ? [H, -1] : null;
      }
      if (y <= 1) return [H, 0];
      if (a === 3 && y <= 3) return [H, -1];
      if (a === 4 && y <= 2) return [H, 0];
      if (a >= 5 && y <= 6) return [H, y === 6 ? -1 : 0];
      return null;
    }
    case 'curly': {
      if (face === 'front') return y <= 1 ? [H, y === 1 && x % 2 ? -1 : 0] : null;
      if (side) return y <= 3 || (a >= 3 && y <= 6) ? [H, 0] : null;
      return y <= 6 ? [H, 0] : null;
    }
  }
  return null;
}

// Curls: a sparse, irregular darker texel instead of a lattice (a regular pattern reads as a grid on the model).
function curl(H, x, y, z) {
  const k = ((x * 37 + y * 61 + z * 23) ^ (x * y + z)) % 7;
  return [H, k === 0 ? -1 : 0];
}

function hairOver(D, p) {
  const {face, x, y, z} = p, a = 7 - z, H = D.hair;
  if (face === 'bottom') return null;
  const side = face === 'left' || face === 'right';
  switch (D.hairStyle) {
    case 'long': case 'locs': {
      const strand = v => (D.hairStyle === 'locs' ? (v % 2 ? -1 : 0) : v % 3 === 1 ? -1 : 0);
      if (face === 'top') return [H, 0];
      if (face === 'front') return y === 0 || ((x === 0 || x === 7) && y <= 6) ? [H, y >= 4 ? -1 : 0] : null;
      if (side) return y >= 1 && !(a === 0 && y >= 6) ? [H, y === 7 ? -1 : (y >= 3 ? strand(z) : 0)] : null;
      return [H, y === 7 ? -1 : (y >= 2 ? strand(x) : 0)];
    }
    case 'bob':
      if (face === 'top') return [H, 0];
      if (face === 'front') return y === 0 || ((x === 0 || x === 7) && y <= 5) ? [H, 0] : null;
      if (side) return y >= 2 && y <= 6 && a >= 1 ? [H, y === 6 ? -1 : 0] : null;
      return y >= 1 && y <= 6 ? [H, y === 6 ? -1 : 0] : null;
    case 'ponytail':
      return face === 'back' && y >= 6 && (x === 3 || x === 4) ? [H, y === 7 ? -1 : 0] : null;
    case 'braid':
      return face === 'back' && y >= 6 && (x === 3 || x === 4) ? [H, (x + y) % 2 ? 1 : -1] : null;
    case 'bun':
      if (face === 'back' && y >= 1 && y <= 3 && x >= 2 && x <= 5) return [H, x === 2 || x === 5 || y === 3 ? -1 : y === 1 ? 1 : 0];
      if (face === 'top' && z <= 1 && x >= 2 && x <= 5) return [H, 1];
      return null;
    case 'curly':
      if (face === 'top') return curl(H, x, 0, z);
      if (face === 'front') return y === 0 || (y === 1 && x % 2 === 0) ? curl(H, x, y, 7) : null;
      if (side) return y <= 4 || (y === 5 && z % 2 === 0) ? curl(H, a, y, z) : null;
      return y <= 6 || (y === 7 && x % 2 === 1) ? curl(H, x, y, 0) : null;
  }
  return null;
}

function beard(D, p) {
  const B = D.beard;
  if (!B) return null;
  const {face, x, y, z} = p, a = 7 - z;
  const mat = B === 'stubble' ? mix(D.skin, D.hair, 0.28) : D.beardColor ?? D.hair;
  const moustache = face === 'front' && y === 5 && x >= 2 && x <= 5 ? [mat, x === 2 || x === 5 ? -1 : 0] : null;
  switch (B) {
    case 'moustache':
      return moustache;
    case 'goatee':
      if (face === 'bottom') return x >= 2 && x <= 5 && z >= 5 ? [mat, -1] : null;
      if (face !== 'front') return null;
      if (y === 6 && (x === 2 || x === 5)) return [mat, -1];
      if (y === 7 && x >= 2 && x <= 5) return [mat, 0];
      return moustache;
    case 'stubble':
      if (face === 'bottom') return [mat, -1];
      if (face === 'front') {
        if (y === 7) return [mat, 0];
        if (y === 6 && x !== 3 && x !== 4) return [mat, 0];
        if (y === 5 && (x <= 1 || x >= 6)) return [mat, 0];
        return null;
      }
      return (face === 'left' || face === 'right') && a <= 3 && y >= 5 ? [mat, 0] : null;
    default:   // full
      if (face === 'bottom') return [mat, -1];
      if (face === 'front') {
        if (y === 7) return [mat, x === 0 || x === 7 ? -1 : 0];
        if (y === 6) return x === 3 || x === 4 ? null : [mat, 0];
        if (y === 5) return x === 3 || x === 4 ? null : [mat, x === 2 || x === 5 ? 1 : 0];
        return null;
      }
      return (face === 'left' || face === 'right') && a <= 3 && y >= 4 ? [mat, y === 7 ? -1 : 0] : null;
  }
}

function features(D, p) {
  if (p.face !== 'front') return null;
  const {x, y} = p, F = D.sex === 'female';
  const brow = D.brow ?? D.hair;
  const lash = mix(brow, '#1A1412', 0.6);
  if (y === 3) {
    if (F) return x === 1 || x === 6 ? [lash, 0] : x === 2 || x === 5 ? [mix(brow, D.skin, 0.3), 0] : null;
    return x === 1 || x === 2 || x === 5 || x === 6 ? [brow, -1] : null;
  }
  if (y === 4) {
    if (x === 1 || x === 6) return [EYE_WHITE, 0];
    if (x === 2 || x === 5) return [D.iris, 0];
    return null;
  }
  if (y === 5) {
    if (x === 4 || (!F && x === 3)) return [D.skin, -1];
    if (D.freckles && (x === 1 || x === 6)) return [mix(D.skin, '#8E4A2C', 0.28), 0];
    return null;
  }
  if (y === 6 && (x === 3 || x === 4)) return [D.lip ?? mix(D.skin, '#7A2E28', 0.35), x === 3 ? 0 : -1];
  return null;
}

function headSkin(D, p) {
  const {face, x, y, z} = p, a = 7 - z;
  if (face === 'top') return [D.skin, 1];
  if (face === 'bottom') return [D.skin, -2];
  if (face === 'front' && y > 0 && hairBase(D, {...p, y: y - 1})) return [D.skin, -1];   // under the fringe
  if (face === 'front') return [D.skin, y === 7 && (x === 0 || x === 7) ? -1 : 0];
  if (face === 'back') return [D.skin, y === 7 ? -1 : 0];
  if (a === 4 && (y === 4 || y === 5)) return [D.skin, -1];   // ear
  return [D.skin, y === 7 ? -1 : 0];
}

function head(D, p, over) {
  if (over) return hairOver(D, p);
  return hairBase(D, p) ?? beard(D, p) ?? features(D, p) ?? headSkin(D, p);
}

// ---------------------------------------------------------------- shirt

const SHORT_SLEEVES = new Set(['tee', 'ringer', 'polo']);
const COLLARED = new Set(['workshirt', 'flannel', 'polo']);
const BUTTONED = new Set(['workshirt', 'flannel']);

function plaid(S, p) {
  const {i, j} = p;
  if (S.check === 'buffalo') {
    const v = (i >> 1) % 2 === 0, h = (j >> 1) % 2 === 0;
    return v && h ? [S.stripe, 0] : v || h ? [S.color, -2] : [S.color, 0];
  }
  // check lines with a darker crossing; a light dot at every crossing read as a dotted grid
  const v = i % 4 === 1, h = j % 4 === 2;
  return v && h ? [S.stripe, -1] : v || h ? [S.stripe, 0] : [S.color, 0];
}

// The cloth at a texel, before folds: plaid, stripes or plain.
function cloth(S, p, sleeve) {
  if (S.type === 'flannel') return plaid(S, p);
  if (S.type === 'stripe' && p.face !== 'top' && p.face !== 'bottom' && p.j % 3 === 1) return [S.stripe, 0];
  if (S.type === 'raglan' && sleeve) return [S.sleeve, 0];
  return [S.color, 0];
}

function shirtTorso(S, p) {
  const {face, x, y} = p;
  if (face === 'bottom') return [S.color, -1];
  if (face === 'top') {
    if (S.type === 'raglan') return [x <= 2 || x >= 5 ? S.sleeve : S.color, 1];
    return S.type === 'flannel' ? plaid(S, p) : [S.color, 1];
  }
  const neck = face === 'front' && y === 0 && (x === 3 || x === 4);
  const [c, l] = cloth(S, p, false);
  const hem = y === 11 ? -1 : 0;
  switch (S.type) {
    case 'tee': case 'longsleeve': case 'stripe':
      return neck ? null : [c, l + hem];
    case 'ringer':
      if (neck) return null;
      return y === 0 ? [S.trim, 0] : [c, l + hem];
    case 'raglan':
      if (neck) return null;
      if ((face === 'front' || face === 'back') && ((y === 0 && (x <= 2 || x >= 5)) || (y === 1 && (x <= 1 || x >= 6))
          || (y === 2 && (x === 0 || x === 7)))) return [S.sleeve, 0];
      if ((face === 'left' || face === 'right') && y <= 2) return [S.sleeve, 0];
      return [c, l + hem];
    case 'henley':
      if (neck) return null;
      if (face === 'front' && x === 4 && y >= 1 && y <= 3) return y === 3 ? [c, -1] : [S.button, 0];
      return [c, l + hem];
    case 'polo':
      if (neck) return [S.color, -2];
      if (face === 'front' && y <= 2 && (x === 3 || x === 4)) return x === 3 && y === 1 ? [S.button, 0] : [c, -1];
      return [c, l + hem];
    case 'workshirt': case 'flannel':
      if (neck) return null;
      if (face === 'front' && x === 4 && y >= 1) return [S.color, -1];   // placket
      if (face === 'front' && S.pockets && (x === 1 || x === 2 || x === 5 || x === 6) && y >= 2 && y <= 4) {
        return [c, y === 2 || y === 4 ? l - 1 : l];   // flap and pocket bottom
      }
      return [c, l + hem];
  }
  return [c, l];
}

function shirtSleeve(S, p) {
  const {y} = p;
  const short = SHORT_SLEEVES.has(S.type) || S.short;
  if (short) {
    if (y > 3) return null;
    if (S.type === 'ringer' && y === 3) return [S.trim, 0];
    const [c, l] = cloth(S, p, true);
    return [c, y === 3 ? l - 1 : l];
  }
  if (S.type === 'flannel' && S.rolled) {
    if (y <= 4) return plaid(S, p);
    if (y <= 6) return [S.color, y === 5 ? 1 : 0];
    return null;
  }
  if (y > 9) return null;
  const [c, l] = cloth(S, p, true);
  return y === 9 ? [S.type === 'raglan' ? S.sleeve : S.color, -1] : [c, l];
}

// Outer layer of the shirt: a raised collar on collared shirts, an untucked hem, sleeve hems and cuffs.
function shirtOverBody(D, p) {
  const S = D.shirt, {face, x, y} = p;
  if (face === 'top' || face === 'bottom') return null;
  if (COLLARED.has(S.type) && y === 0) {
    if (face === 'front') return (x >= 1 && x <= 2) || (x >= 5 && x <= 6) ? [S.collar ?? S.color, 1] : null;
    if (face === 'back') return x >= 1 && x <= 6 ? [S.collar ?? S.color, 0] : null;
    return null;
  }
  if (!D.belt && y === 11) return [S.type === 'raglan' ? S.color : S.color, -1];
  return null;
}

function shirtOverSleeve(S, p) {
  if (p.face === 'top' || p.face === 'bottom') return null;
  if (S.type === 'flannel' && S.rolled) return p.y === 5 ? [S.color, 1] : null;
  if (SHORT_SLEEVES.has(S.type) || S.short) return p.y === 3 ? [S.trim ?? S.color, -1] : null;
  if (BUTTONED.has(S.type)) return p.y === 9 ? [S.color, -1] : null;
  return null;
}

function belt(D, p) {
  if (!D.belt || p.y !== 11 || p.face === 'top' || p.face === 'bottom') return null;
  if (p.face === 'front' && (p.x === 3 || p.x === 4)) return [D.belt.buckle, p.x === 3 ? 1 : 0];
  return [D.belt.color, 0];
}

function hairOnBody(D, p) {
  if (p.face !== 'back') return null;
  const {x, y} = p, H = D.hair;
  switch (D.hairStyle) {
    case 'long': case 'locs': {
      const strand = D.hairStyle === 'locs' ? (x % 2 ? -1 : 0) : x % 3 === 1 ? -1 : 0;
      if (y <= 1) return [H, strand];
      if (y === 2 && x >= 1 && x <= 6) return [H, -1];
      if (y === 3 && x >= 2 && x <= 5) return [H, -2];
      return null;
    }
    case 'ponytail':
      return (x === 3 || x === 4) && y <= 3 ? [H, y === 3 ? -1 : 0] : null;
    case 'braid':
      if ((x === 3 || x === 4) && y <= 6) return [H, (x + y) % 2 ? 1 : -1];
      return (x === 3 || x === 4) && y === 7 ? [D.tie, 0] : null;
  }
  return null;
}

function torso(D, p, over) {
  if (over) return hairOnBody(D, p) ?? shirtOverBody(D, p);
  return belt(D, p) ?? shirtTorso(D.shirt, p) ?? [D.skin, 0];
}

function limbShade(c, p) {
  if (!c) return null;
  let [hex, lvl] = c;
  if (p.face === 'top') lvl += 1;
  else if (p.face === 'bottom') lvl -= 1;
  else if (p.inner) lvl -= 1;
  return [hex, lvl];
}

function arm(D, p, over) {
  if (over) return limbShade(shirtOverSleeve(D.shirt, p), p);
  return limbShade(shirtSleeve(D.shirt, p) ?? [D.skin, p.y === 11 ? -1 : 0], p);
}

// ---------------------------------------------------------------- legs

function bootTop(D) {
  return D.boots.type === 'combat' ? 8 : D.boots.type === 'sneaker' ? 10 : 9;
}

function boots(B, p, top) {
  const {face, x, y} = p;
  if (face === 'bottom') return [B.sole, -1];
  if (y < top) return null;
  if (y === 11) return [B.sole, 0];
  const lace = face === 'front' && (x === 1 || x === 2);
  switch (B.type) {
    case 'combat':
      if (lace && y <= 9) return [B.lace ?? B.color, (x + y) % 2 ? 1 : -1];
      return [B.color, y === top ? -1 : 0];
    case 'sneaker':
      return [B.color, face === 'front' && lace ? 1 : 0];
    default:   // work, hiker
      if (B.type === 'hiker' && y === top && !(face === 'front' && lace)) return [B.collar, 0];
      if (lace && y < 10) return [B.lace, 0];
      if (face === 'front' && y === 10) return [B.color, 1];
      return [B.color, 0];
  }
}

function pants(D, p, top) {
  const P = D.pants;
  const {face, x, y, z, side, w} = p;
  const outerFace = (side === 'R' && face === 'right') || (side === 'L' && face === 'left');
  const outerCol = side === 'R' ? 0 : w - 1;
  const crease = side === 'R' ? 1 : 2;
  if (face === 'top') return [P.color, 0];
  if (y === top - 1) return [P.color, -1];
  switch (P.type) {
    case 'jeans':
      if (face === 'front' && y === 0 && x === outerCol) return [P.color, -1];
      if (face === 'back' && y === 1 && (x === 1 || x === 2)) return [P.color, -1];
      if (outerFace && z === 2) return [P.color, -1];
      return [P.color, 0];
    case 'cargo':
      if (outerFace && (z === 1 || z === 2) && y >= 4 && y <= 7) return [P.color, y === 4 || y === 7 ? -1 : 0];
      if (face === 'front' && y === 0 && x === outerCol) return [P.color, -1];
      return [P.color, 0];
    case 'chinos':
      if (face === 'front' && x === crease && y >= 1) return [P.color, 1];
      if (face === 'front' && y === 0 && x === outerCol) return [P.color, -1];
      return [P.color, 0];
    case 'work':   // canvas work trousers: side seam and a utility loop on the right leg
      if (outerFace && z === 2) return [P.color, -1];
      if (side === 'R' && outerFace && z === 1 && (y === 1 || y === 2)) return [P.color, y === 1 ? 1 : -1];
      if (face === 'front' && y === 0 && x === outerCol) return [P.color, -1];
      return [P.color, 0];
  }
  return [P.color, 0];
}

function leg(D, p, over) {
  const top = bootTop(D);
  if (over) {
    if (p.face === 'top' || p.face === 'bottom') return null;
    if (D.boots.type !== 'combat' && p.y === top - 1) return limbShade(pants(D, p, top), p);
    if (D.boots.type === 'combat' && p.y === top) return limbShade([D.boots.color, 0], p);
    if (D.boots.type === 'hiker' && p.y === top && !(p.face === 'front' && (p.x === 1 || p.x === 2))) {
      return limbShade([D.boots.collar, 0], p);
    }
    return null;
  }
  return limbShade(boots(D.boots, p, top) ?? pants(D, p, top), p);
}

// ---------------------------------------------------------------- painting

function paintSkin(D) {
  const px = new Uint8Array(SIZE * SIZE * 4);
  for (const [name, part] of Object.entries(PARTS(D.slim))) {
    for (const layer of ['base', 'over']) {
      const [u, v] = part[layer], over = layer === 'over';
      for (const face of FACES) {
        const [x0, y0, fw, fh] = faceRect(face, u, v, part.w, part.h, part.d);
        for (let j = 0; j < fh; j++) {
          for (let i = 0; i < fw; i++) {
            const [x, y, z] = local(face, i, j, part.w, part.h, part.d);
            const inner = part.side === 'R' ? face === 'left' : part.side === 'L' ? face === 'right' : false;
            const p = {face, i, j, x, y, z, w: part.w, h: part.h, d: part.d, side: part.side, inner};
            const c = name === 'head' ? head(D, p, over) : name === 'body' ? torso(D, p, over)
                : name.endsWith('arm') ? arm(D, p, over) : leg(D, p, over);
            if (!c) continue;
            const [r, g, b] = tone(c[0], c[1]);
            const o = ((y0 + j) * SIZE + x0 + i) * 4;
            px[o] = r; px[o + 1] = g; px[o + 2] = b; px[o + 3] = 255;
          }
        }
      }
    }
  }
  return px;
}

// ---------------------------------------------------------------- the library

const SKIN = {
  s1: '#EDC6A6', s2: '#DDA987', s3: '#C4916A', s4: '#AA7654', s5: '#8A5A3B', s6: '#5F3B27',
  ea: '#E3B994', sa: '#B07C58', na: '#9C6A47', old: '#D3A386',
};
const BELT = {color: '#3A2A20', buckle: '#9A958A'};
const BELT_DARK = {color: '#262220', buckle: '#8E8A80'};

const BOOTS = {
  brown: {type: 'work', color: '#5C3F2A', lace: '#3A2A1C', sole: '#221A14'},
  tan: {type: 'work', color: '#9A7148', lace: '#5C4430', sole: '#2E241C'},
  dark: {type: 'work', color: '#3E3028', lace: '#2A201A', sole: '#1C1612'},
  black: {type: 'work', color: '#272727', lace: '#1A1A1A', sole: '#141414'},
  hikerBrown: {type: 'hiker', color: '#6A4A31', collar: '#2E2622', lace: '#B89A6A', sole: '#2A221C'},
  hikerGrey: {type: 'hiker', color: '#57524B', collar: '#2E2A26', lace: '#8C6E4A', sole: '#211E1A'},
  combat: {type: 'combat', color: '#1F1F21', lace: '#2E2E30', sole: '#141414'},
  combatBrown: {type: 'combat', color: '#3A2E26', lace: '#4A3C30', sole: '#1A1512'},
  sneakerBlack: {type: 'sneaker', color: '#2B2B2E', sole: '#DCDAD4'},
  sneakerGrey: {type: 'sneaker', color: '#5E6064', sole: '#D8D6D0'},
  sneakerWhite: {type: 'sneaker', color: '#D2D0CA', sole: '#8A6A48'},
};

const SKINS = {
  // auburn ponytail, freckles; cream henley; dark jeans, brown hikers
  f01: {
    first: 'Megan', last: 'Kelly',
    sex: 'female', slim: true, skin: SKIN.s1, hair: '#7E3A1F', hairStyle: 'ponytail', tie: '#2E2A26', iris: '#4F6B3A',
    freckles: true,
    shirt: {type: 'henley', color: '#D4C8AE', button: '#A89A80'},
    belt: BELT, pants: {type: 'jeans', color: '#2E3D5A'}, boots: BOOTS.hikerBrown,
  },
  // short curly hair; ochre long-sleeve; grey cargo trousers, black trainers
  f02: {
    first: 'Denise', last: 'Carter',
    sex: 'female', slim: true, skin: SKIN.s6, hair: '#1B1512', hairStyle: 'curly', iris: '#3A2416', lip: '#5A2E28',
    shirt: {type: 'longsleeve', color: '#B0812F'},
    pants: {type: 'cargo', color: '#585B5E'}, boots: BOOTS.sneakerBlack,
  },
  // long black hair; slate henley; olive cargo trousers, tan boots
  f03: {
    first: 'Elena', last: 'Castillo',
    sex: 'female', slim: true, skin: SKIN.s3, hair: '#1F1915', hairStyle: 'long', iris: '#4A3020',
    shirt: {type: 'henley', color: '#5E666E', button: '#3E444A'},
    belt: BELT_DARK, pants: {type: 'cargo', color: '#565C3C'}, boots: BOOTS.tan,
  },
  // blonde bob; white tee; black jeans, brown boots
  f04: {
    first: 'Holly', last: 'Jensen',
    sex: 'female', slim: true, skin: SKIN.s2, hair: '#C6A260', hairStyle: 'bob', iris: '#47698A',
    shirt: {type: 'tee', color: '#E4E2DC'},
    belt: {color: '#4A3426', buckle: '#B0A890'}, pants: {type: 'jeans', color: '#2C2C31'}, boots: BOOTS.brown,
  },
  // straight black hair to the shoulders; maroon long-sleeve; khaki trousers, dark boots
  f05: {
    first: 'Grace', last: 'Chen',
    sex: 'female', slim: true, skin: SKIN.ea, hair: '#16120F', hairStyle: 'long', iris: '#2E2018',
    shirt: {type: 'longsleeve', color: '#6E3036'},
    belt: BELT_DARK, pants: {type: 'chinos', color: '#A08F6C'}, boots: BOOTS.dark,
  },
  // dark brown braid; red-and-black buffalo-check flannel, sleeves rolled; mid-blue jeans, brown boots
  f06: {
    first: 'Rosa', last: 'Mendez',
    sex: 'female', slim: true, skin: SKIN.s4, hair: '#2E1F17', hairStyle: 'braid', tie: '#8C2E2A', iris: '#5E5030',
    shirt: {type: 'flannel', check: 'buffalo', color: '#8E2E2C', stripe: '#211C1C', rolled: true},
    belt: BELT, pants: {type: 'jeans', color: '#3F5878'}, boots: {type: 'work', color: '#6C4A30', lace: '#4A3222', sole: '#261D16'},
  },
  // older; grey hair in a bun; chambray work shirt; tan trousers, brown boots
  f07: {
    first: 'Janet', last: 'Sullivan',
    sex: 'female', slim: true, skin: SKIN.s1, hair: '#B8B4AE', hairStyle: 'bun', iris: '#5A6A78',
    shirt: {type: 'workshirt', color: '#7F9AB5', pockets: true},
    belt: {color: '#4A3426', buckle: '#A39E92'}, pants: {type: 'chinos', color: '#9C8A66'}, boots: BOOTS.brown,
  },
  // shoulder-length locs; olive tee; black jeans, grey trainers
  f08: {
    first: 'Tara', last: 'Brooks',
    sex: 'female', slim: true, skin: SKIN.s5, hair: '#1E1712', hairStyle: 'locs', iris: '#2E1E14', lip: '#5E3428',
    shirt: {type: 'tee', color: '#5F6640'},
    belt: BELT_DARK, pants: {type: 'jeans', color: '#26272B'}, boots: BOOTS.sneakerGrey,
  },
  // black pixie cut; navy polo; khaki trousers, white trainers
  f09: {
    first: 'Kim', last: 'Nguyen',
    sex: 'female', slim: true, skin: SKIN.ea, hair: '#15110F', hairStyle: 'pixie', iris: '#2A1E16',
    shirt: {type: 'polo', color: '#2C3A5A', button: '#D8D6D0'},
    belt: BELT, pants: {type: 'chinos', color: '#A89874'}, boots: BOOTS.sneakerWhite,
  },
  // long dark brown hair; green tartan flannel; mid-blue jeans, brown hikers
  f10: {
    first: 'Claire', last: 'Morgan',
    sex: 'female', slim: true, skin: SKIN.s2, hair: '#4A2E1E', hairStyle: 'long', iris: '#5A4630',
    shirt: {type: 'flannel', color: '#3C5A40', stripe: '#26392A'},
    pants: {type: 'jeans', color: '#3F5878'}, boots: BOOTS.hikerBrown,
  },
  // long black braid; teal tee; dark jeans, white trainers
  f11: {
    first: 'Priya', last: 'Patel',
    sex: 'female', slim: true, skin: SKIN.sa, hair: '#1A1412', hairStyle: 'braid', tie: '#6A2E3A', iris: '#3A2618',
    shirt: {type: 'tee', color: '#2F6B6A'},
    belt: BELT_DARK, pants: {type: 'jeans', color: '#2E3D5A'}, boots: BOOTS.sneakerWhite,
  },
  // strawberry-blonde ponytail, freckles; cream ringer tee with brick-red trim; light jeans, black trainers
  f12: {
    first: 'Ivy', last: 'Riley',
    sex: 'female', slim: true, skin: SKIN.s1, hair: '#C08A5A', hairStyle: 'ponytail', tie: '#3A3A3E', iris: '#47698A',
    freckles: true,
    shirt: {type: 'ringer', color: '#E2DED4', trim: '#8E2E2C'},
    pants: {type: 'jeans', color: '#6680A0'}, boots: BOOTS.sneakerBlack,
  },
  // close-cropped hair; burgundy henley; khaki cargo trousers, brown combat boots
  f13: {
    first: 'Dana', last: 'Hayes',
    sex: 'female', slim: true, skin: SKIN.s6, hair: '#141110', hairStyle: 'buzz', iris: '#2E1E14', lip: '#5A2E28',
    shirt: {type: 'henley', color: '#6A2F35', button: '#3E2226'},
    belt: BELT_DARK, pants: {type: 'cargo', color: '#8A7A58'}, boots: BOOTS.combatBrown,
  },
  // dark hair in a ponytail; black long-sleeve; olive work trousers, brown boots
  f14: {
    first: 'Gloria', last: 'Ortiz',
    sex: 'female', slim: true, skin: SKIN.s4, hair: '#22170F', hairStyle: 'ponytail', tie: '#1A1A1A', iris: '#3A2618',
    shirt: {type: 'longsleeve', color: '#2A2B2F'},
    belt: BELT, pants: {type: 'work', color: '#5A5C3E'}, boots: BOOTS.brown,
  },
  // brown bob; cream long-sleeve with navy stripes; grey trousers, dark brown boots
  f15: {
    first: 'Lena', last: 'Fischer',
    sex: 'female', slim: true, skin: SKIN.s2, hair: '#6A4A30', hairStyle: 'bob', iris: '#566C7C',
    shirt: {type: 'stripe', color: '#E4E0D6', stripe: '#2C3A5A'},
    belt: {color: '#4A3426', buckle: '#B0A890'}, pants: {type: 'chinos', color: '#6E6E6A'}, boots: BOOTS.dark,
  },
  // long dark hair; sand work shirt; dark jeans, brown boots
  f16: {
    first: 'Leila', last: 'Nasser',
    sex: 'female', slim: true, skin: SKIN.s3, hair: '#1F1610', hairStyle: 'long', iris: '#3A2618',
    shirt: {type: 'workshirt', color: '#B8A07A', pockets: true},
    belt: BELT, pants: {type: 'jeans', color: '#2C3A55'}, boots: BOOTS.brown,
  },
  // older; short white pixie; forest-green polo; tan trousers, white trainers
  f17: {
    first: 'Wendy', last: 'Baker',
    sex: 'female', slim: true, skin: SKIN.s1, hair: '#D8D4CE', brow: '#A8A29A', hairStyle: 'pixie', iris: '#5A6A78',
    shirt: {type: 'polo', color: '#3E5A40', button: '#D8D6D0'},
    belt: BELT, pants: {type: 'chinos', color: '#9C8A66'}, boots: BOOTS.sneakerWhite,
  },
  // black hair in a bun; mustard tee; grey cargo trousers, combat boots
  f18: {
    first: 'Angela', last: 'Ward',
    sex: 'female', slim: true, skin: SKIN.s6, hair: '#16110E', hairStyle: 'bun', iris: '#2E1E14', lip: '#5A2E28',
    shirt: {type: 'tee', color: '#C49A3A'},
    belt: BELT_DARK, pants: {type: 'cargo', color: '#4A4D50'}, boots: BOOTS.combat,
  },
  // black ponytail; grey raglan with maroon sleeves; dark jeans, black trainers
  f19: {
    first: 'Amy', last: 'Lin',
    sex: 'female', slim: true, skin: SKIN.ea, hair: '#15110F', hairStyle: 'ponytail', tie: '#3A3A3E', iris: '#2A1E16',
    shirt: {type: 'raglan', color: '#9A9C9E', sleeve: '#6E3036'},
    pants: {type: 'jeans', color: '#2E3D5A'}, boots: BOOTS.sneakerBlack,
  },
  // long blonde hair; heather-grey tee; light jeans, brown hikers
  f20: {
    first: 'Sarah', last: 'Collins',
    sex: 'female', slim: true, skin: SKIN.s2, hair: '#C9A86A', hairStyle: 'long', iris: '#4E6A80',
    shirt: {type: 'tee', color: '#8A8C8F'},
    belt: {color: '#4A3426', buckle: '#B0A890'}, pants: {type: 'jeans', color: '#6680A0'}, boots: BOOTS.hikerBrown,
  },
  // dark curly hair; red henley; black jeans, white trainers
  f21: {
    first: 'Carmen', last: 'Flores',
    sex: 'female', slim: true, skin: SKIN.s4, hair: '#2A1C14', hairStyle: 'curly', iris: '#3A2618',
    shirt: {type: 'henley', color: '#8E2E2C', button: '#5A1E1E'},
    belt: BELT_DARK, pants: {type: 'jeans', color: '#26272B'}, boots: BOOTS.sneakerWhite,
  },
  // short curly hair; denim work shirt; khaki cargo trousers, brown boots
  f22: {
    first: 'Monique', last: 'Harris',
    sex: 'female', slim: true, skin: SKIN.s5, hair: '#1A1410', hairStyle: 'curly', iris: '#2E1E14', lip: '#5E3428',
    shirt: {type: 'workshirt', color: '#3E5A80', pockets: true},
    belt: BELT, pants: {type: 'cargo', color: '#9A8A64'}, boots: BOOTS.brown,
  },
  // copper-red bob, freckles; olive long-sleeve; brown work trousers, tan boots
  f23: {
    first: 'Fiona', last: 'Walsh',
    sex: 'female', slim: true, skin: SKIN.s1, hair: '#A04A24', hairStyle: 'bob', iris: '#4F6B3A', freckles: true,
    shirt: {type: 'longsleeve', color: '#5F6640'},
    belt: BELT, pants: {type: 'work', color: '#5A4A38'}, boots: BOOTS.tan,
  },
  // long black hair; cream long-sleeve; mid-blue jeans, white trainers
  f24: {
    first: 'Anita', last: 'Shah',
    sex: 'female', slim: true, skin: SKIN.sa, hair: '#16110E', hairStyle: 'long', iris: '#2E1E14',
    shirt: {type: 'longsleeve', color: '#D8D0BE'},
    belt: BELT_DARK, pants: {type: 'jeans', color: '#3A4C6A'}, boots: BOOTS.sneakerWhite,
  },
  // brown ponytail; green-and-black buffalo-check flannel, sleeves rolled; jeans, grey hikers
  f25: {
    first: 'Jenna', last: 'Turner',
    sex: 'female', slim: true, skin: SKIN.s2, hair: '#5A3E28', hairStyle: 'ponytail', tie: '#2E2A26', iris: '#566C7C',
    shirt: {type: 'flannel', check: 'buffalo', color: '#3E5A3A', stripe: '#1E221C', rolled: true},
    pants: {type: 'jeans', color: '#3F5878'}, boots: BOOTS.hikerGrey,
  },
  // older; grey curls; plum long-sleeve; grey trousers, black boots
  f26: {
    first: 'Beverly', last: 'Coleman',
    sex: 'female', slim: true, skin: SKIN.s6, hair: '#8E8A86', brow: '#6A6662', hairStyle: 'curly', iris: '#2E1E14',
    lip: '#5A2E28',
    shirt: {type: 'longsleeve', color: '#5E4660'},
    belt: BELT, pants: {type: 'chinos', color: '#6E6E6A'}, boots: BOOTS.black,
  },
  // black bob; light ringer tee with navy trim; black jeans, black trainers
  f27: {
    first: 'Hana', last: 'Mori',
    sex: 'female', slim: true, skin: SKIN.ea, hair: '#15110F', hairStyle: 'bob', iris: '#2A1E16',
    shirt: {type: 'ringer', color: '#D8D6D0', trim: '#2C3A5A'},
    pants: {type: 'jeans', color: '#26272B'}, boots: BOOTS.sneakerBlack,
  },
  // brown braid; rust tee; olive cargo trousers, tan boots
  f28: {
    first: 'Maria', last: 'Gonzalez',
    sex: 'female', slim: true, skin: SKIN.s3, hair: '#3A2A1E', hairStyle: 'braid', tie: '#2E2A26', iris: '#3A2618',
    shirt: {type: 'tee', color: '#A85A32'},
    belt: BELT, pants: {type: 'cargo', color: '#565C3C'}, boots: BOOTS.tan,
  },
  // long dark brown hair; navy tartan flannel; dark jeans, brown hikers
  f29: {
    first: 'Rachel', last: 'Cooper',
    sex: 'female', slim: true, skin: SKIN.s1, hair: '#3A281C', hairStyle: 'long', iris: '#5A4630',
    shirt: {type: 'flannel', color: '#2E3E5A', stripe: '#1C2638'},
    pants: {type: 'jeans', color: '#2C3A55'}, boots: BOOTS.hikerBrown,
  },
  // black hair in a bun; maroon polo; khaki trousers, grey trainers
  f30: {
    first: 'Keisha', last: 'Robinson',
    sex: 'female', slim: true, skin: SKIN.s5, hair: '#1A1410', hairStyle: 'bun', iris: '#2E1E14', lip: '#5E3428',
    shirt: {type: 'polo', color: '#6E3036', button: '#D8D6D0'},
    belt: BELT, pants: {type: 'chinos', color: '#A89874'}, boots: BOOTS.sneakerGrey,
  },
  // short brown hair, stubble; heather-grey henley; jeans, work boots
  m01: {
    first: 'Jake', last: 'Miller',
    sex: 'male', slim: false, skin: SKIN.s2, hair: '#4A3423', hairStyle: 'short', beard: 'stubble', iris: '#566C7C',
    shirt: {type: 'henley', color: '#8A8C8F', button: '#5E6064'},
    belt: BELT, pants: {type: 'jeans', color: '#3A4C6A'}, boots: BOOTS.brown,
  },
  // short fade and a full beard; black tee; black cargo trousers, combat boots
  m02: {
    first: 'Marcus', last: 'Reed',
    sex: 'male', slim: false, skin: SKIN.s6, hair: '#141110', hairStyle: 'fade', beard: 'full', beardColor: '#2C2420',
    iris: '#2E1E14', lip: '#4E2A24',
    shirt: {type: 'tee', color: '#46484C'},
    belt: BELT_DARK, pants: {type: 'cargo', color: '#2E2F31'}, boots: BOOTS.combat,
  },
  // short black hair; blue-grey flannel shirt; tan work trousers, brown boots
  m03: {
    first: 'Miguel', last: 'Torres',
    sex: 'male', slim: false, skin: SKIN.s4, hair: '#191411', hairStyle: 'short', iris: '#3A2618',
    shirt: {type: 'flannel', color: '#4F6273', stripe: '#33414E'},
    belt: {color: '#3A2A20', buckle: '#A39E92'}, pants: {type: 'chinos', color: '#8E7A55'},
    boots: {type: 'work', color: '#6A4A2E', lace: '#45301E', sole: '#241B14'},
  },
  // ginger hair and beard; navy long-sleeve; charcoal cargo trousers, work boots
  m04: {
    first: 'Sean', last: "O'Brien",
    sex: 'male', slim: false, skin: SKIN.s1, hair: '#93461F', hairStyle: 'short', beard: 'full', iris: '#4E6A80',
    shirt: {type: 'longsleeve', color: '#2B3346'},
    belt: BELT_DARK, pants: {type: 'cargo', color: '#3F4245'},
    boots: {type: 'work', color: '#4E3A28', lace: '#2E2218', sole: '#1E1712'},
  },
  // short black hair, stubble; khaki work shirt; dark jeans, grey hikers
  m05: {
    first: 'Andre', last: 'Walker',
    sex: 'male', slim: false, skin: SKIN.s5, hair: '#15110E', hairStyle: 'short', beard: 'stubble', iris: '#2A1C12',
    shirt: {type: 'workshirt', color: '#A8915F', pockets: true},
    belt: BELT, pants: {type: 'jeans', color: '#2C3A55'}, boots: BOOTS.hikerGrey,
  },
  // older: receding grey hair and grey beard; navy work shirt and work trousers; black boots
  m06: {
    first: 'Frank', last: 'Kowalski',
    sex: 'male', slim: false, skin: SKIN.old, hair: '#9A9894', hairStyle: 'receding', beard: 'full', iris: '#4E5A66',
    shirt: {type: 'workshirt', color: '#3E5068', pockets: true},
    belt: BELT_DARK, pants: {type: 'work', color: '#3A4458'}, boots: BOOTS.black,
  },
  // buzz cut, moustache; coyote-brown tee; grey cargo trousers, tan boots
  m07: {
    first: 'Victor', last: 'Alvarez',
    sex: 'male', slim: false, skin: SKIN.s3, hair: '#1A1410', hairStyle: 'buzz', beard: 'moustache', beardColor: '#4A3626', iris: '#3A2618',
    shirt: {type: 'tee', color: '#6B5A3A'},
    belt: BELT_DARK, pants: {type: 'cargo', color: '#4A4D50'}, boots: BOOTS.tan,
  },
  // black hair with a side part; light chambray work shirt; charcoal trousers, white trainers
  m08: {
    first: 'Daniel', last: 'Tanaka',
    sex: 'male', slim: false, skin: SKIN.ea, hair: '#16120F', hairStyle: 'sidepart', iris: '#2A1E16',
    shirt: {type: 'workshirt', color: '#8FA6BF'},
    belt: {color: '#2A221E', buckle: '#8E8A80'}, pants: {type: 'chinos', color: '#3A3C40'}, boots: BOOTS.sneakerWhite,
  },
  // untidy blond hair, stubble; red tartan flannel; mid-blue jeans, brown hikers
  m09: {
    first: 'Ryan', last: 'Anderson',
    sex: 'male', slim: false, skin: SKIN.s1, hair: '#B8914E', hairStyle: 'messy', beard: 'stubble', iris: '#4E6A80',
    shirt: {type: 'flannel', color: '#8E2E2C', stripe: '#4A1E1E'},
    pants: {type: 'jeans', color: '#3F5878'}, boots: BOOTS.hikerBrown,
  },
  // shaved head, goatee; maroon tee; grey jeans, black trainers
  m10: {
    first: 'Isaac', last: 'Price',
    sex: 'male', slim: false, skin: SKIN.s6, hair: '#141110', hairStyle: 'bald', beard: 'goatee', beardColor: '#2C2420',
    iris: '#2E1E14', lip: '#4E2A24',
    shirt: {type: 'tee', color: '#6E3036'},
    belt: BELT_DARK, pants: {type: 'jeans', color: '#55585C'}, boots: BOOTS.sneakerBlack,
  },
  // brown hair tied back in a bun, full beard; green henley; olive cargo trousers, brown boots
  m11: {
    first: 'Eli', last: 'Novak',
    sex: 'male', slim: false, skin: SKIN.s2, hair: '#5A3E28', hairStyle: 'bun', beard: 'full', iris: '#5A6A50',
    shirt: {type: 'henley', color: '#4E5E46', button: '#2E3828'},
    belt: BELT, pants: {type: 'cargo', color: '#565C3C'}, boots: BOOTS.brown,
  },
  // short curly hair, stubble; rust long-sleeve; black jeans, brown boots
  m12: {
    first: 'Lucas', last: 'Silva',
    sex: 'male', slim: false, skin: SKIN.s4, hair: '#191411', hairStyle: 'curly', beard: 'stubble', iris: '#3A2618',
    shirt: {type: 'longsleeve', color: '#8A4A2E'},
    belt: BELT_DARK, pants: {type: 'jeans', color: '#26272B'}, boots: BOOTS.brown,
  },
  // long straight black hair; grey check flannel; dark jeans, brown boots
  m13: {
    first: 'Owen', last: 'Begay',
    sex: 'male', slim: false, skin: SKIN.na, hair: '#121010', hairStyle: 'long', iris: '#2A1C12',
    shirt: {type: 'flannel', color: '#4A4D52', stripe: '#2E3034'},
    belt: BELT, pants: {type: 'jeans', color: '#2E3D5A'}, boots: {type: 'work', color: '#6C4A30', lace: '#4A3222', sole: '#261D16'},
  },
  // older, bald crown with a white fringe, white moustache; brown work shirt; tan work trousers, dark brown boots
  m14: {
    first: 'Hank', last: 'Schmidt',
    sex: 'male', slim: false, skin: SKIN.s1, hair: '#C9C6C0', hairStyle: 'balding', beard: 'moustache', iris: '#4E6A80',
    shirt: {type: 'workshirt', color: '#6A5440', pockets: true},
    belt: BELT, pants: {type: 'work', color: '#8E7A55'}, boots: BOOTS.dark,
  },
  // short dark hair, stubble; grey raglan with navy sleeves; olive cargo trousers, grey trainers
  m15: {
    first: 'Leon', last: 'Russo',
    sex: 'male', slim: false, skin: SKIN.s3, hair: '#2A1E16', hairStyle: 'short', beard: 'stubble', iris: '#3A2618',
    shirt: {type: 'raglan', color: '#9A9C9E', sleeve: '#2C3A5A'},
    pants: {type: 'cargo', color: '#4F5236'}, boots: BOOTS.sneakerGrey,
  },
  // dark brown hair and full beard; forest-green long-sleeve; jeans, brown boots
  m16: {
    first: 'Tom', last: 'Bennett',
    sex: 'male', slim: false, skin: SKIN.s1, hair: '#3A281C', hairStyle: 'short', beard: 'full', iris: '#566C7C',
    shirt: {type: 'longsleeve', color: '#3E5240'},
    belt: BELT, pants: {type: 'jeans', color: '#3A4C6A'}, boots: BOOTS.brown,
  },
  // short curly hair, stubble; grey work shirt; black jeans, dark boots
  m17: {
    first: 'Darnell', last: 'Jackson',
    sex: 'male', slim: false, skin: SKIN.s6, hair: '#141110', hairStyle: 'curly', beard: 'stubble', iris: '#2E1E14',
    lip: '#4E2A24',
    shirt: {type: 'workshirt', color: '#6E7276', pockets: true},
    belt: BELT_DARK, pants: {type: 'jeans', color: '#26272B'}, boots: BOOTS.dark,
  },
  // buzz cut; navy tee; olive cargo trousers, combat boots
  m18: {
    first: 'Kevin', last: 'Park',
    sex: 'male', slim: false, skin: SKIN.ea, hair: '#15110F', hairStyle: 'buzz', iris: '#2A1E16',
    shirt: {type: 'tee', color: '#2C3A5A'},
    belt: BELT_DARK, pants: {type: 'cargo', color: '#565C3C'}, boots: BOOTS.combat,
  },
  // black hair with a side part, moustache; off-white polo; tan work trousers, brown boots
  m19: {
    first: 'Hector', last: 'Morales',
    sex: 'male', slim: false, skin: SKIN.s4, hair: '#191411', hairStyle: 'sidepart', beard: 'moustache', iris: '#3A2618',
    shirt: {type: 'polo', color: '#D8D6D0', button: '#9A9C9E'},
    belt: BELT, pants: {type: 'work', color: '#8E7A55'}, boots: BOOTS.brown,
  },
  // long dark-blond hair, stubble; white raglan with red sleeves; light jeans, black trainers
  m20: {
    first: 'Cody', last: 'Larsen',
    sex: 'male', slim: false, skin: SKIN.s2, hair: '#8A6A40', hairStyle: 'long', beard: 'stubble', iris: '#4E6A80',
    shirt: {type: 'raglan', color: '#D8D6D0', sleeve: '#8E2E2C'},
    pants: {type: 'jeans', color: '#6680A0'}, boots: BOOTS.sneakerBlack,
  },
  // older: short grey hair and grey beard; brown check flannel; grey trousers, black boots
  m21: {
    first: 'Walter', last: 'Banks',
    sex: 'male', slim: false, skin: SKIN.s6, hair: '#8A8682', hairStyle: 'short', beard: 'full', beardColor: '#9A9894',
    brow: '#7A7672', iris: '#2E1E14',
    shirt: {type: 'flannel', color: '#6A4A30', stripe: '#3E2A1C'},
    belt: BELT, pants: {type: 'chinos', color: '#6E6E6A'}, boots: BOOTS.black,
  },
  // short black hair, full beard; sand tee; grey cargo trousers, tan boots
  m22: {
    first: 'Omar', last: 'Haddad',
    sex: 'male', slim: false, skin: SKIN.s3, hair: '#15110E', hairStyle: 'short', beard: 'full', iris: '#2A1C12',
    shirt: {type: 'tee', color: '#B8A07A'},
    belt: BELT_DARK, pants: {type: 'cargo', color: '#4A4D50'}, boots: BOOTS.tan,
  },
  // untidy brown hair, stubble; blue-and-black buffalo-check flannel; olive work trousers, brown hikers
  m23: {
    first: 'Kyle', last: 'Dawson',
    sex: 'male', slim: false, skin: SKIN.s1, hair: '#5A3E28', hairStyle: 'messy', beard: 'stubble', iris: '#566C7C',
    shirt: {type: 'flannel', check: 'buffalo', color: '#2E4A6E', stripe: '#1A1E26'},
    belt: BELT, pants: {type: 'work', color: '#5A5C3E'}, boots: BOOTS.hikerBrown,
  },
  // short fade, stubble; teal long-sleeve; jeans, brown boots
  m24: {
    first: 'Jesse', last: 'Harjo',
    sex: 'male', slim: false, skin: SKIN.na, hair: '#121010', hairStyle: 'fade', beard: 'stubble', iris: '#2A1C12',
    shirt: {type: 'longsleeve', color: '#2F5A5A'},
    belt: BELT, pants: {type: 'jeans', color: '#3A4C6A'}, boots: BOOTS.brown,
  },
  // shaved head, full beard; slate-blue henley; black cargo trousers, combat boots
  m25: {
    first: 'Calvin', last: 'Grant',
    sex: 'male', slim: false, skin: SKIN.s5, hair: '#1A1410', hairStyle: 'bald', beard: 'full', iris: '#2A1C12',
    lip: '#4E2A24',
    shirt: {type: 'henley', color: '#3E4A5A', button: '#26303C'},
    belt: BELT_DARK, pants: {type: 'cargo', color: '#2E2F31'}, boots: BOOTS.combat,
  },
  // older: balding with a brown-grey fringe, stubble; burgundy polo; khaki trousers, white trainers
  m26: {
    first: 'Gary', last: 'Mitchell',
    sex: 'male', slim: false, skin: SKIN.s2, hair: '#7A6A5A', hairStyle: 'balding', beard: 'stubble', iris: '#566C7C',
    shirt: {type: 'polo', color: '#6A2F35', button: '#D8D6D0'},
    belt: BELT, pants: {type: 'chinos', color: '#A89874'}, boots: BOOTS.sneakerWhite,
  },
  // black hair tied in a bun, stubble; navy long-sleeve with grey stripes; charcoal trousers, grey trainers
  m27: {
    first: 'Jun', last: 'Sato',
    sex: 'male', slim: false, skin: SKIN.ea, hair: '#15110F', hairStyle: 'bun', beard: 'stubble', iris: '#2A1E16',
    shirt: {type: 'stripe', color: '#2C3A5A', stripe: '#9A9C9E'},
    belt: BELT_DARK, pants: {type: 'chinos', color: '#3A3C40'}, boots: BOOTS.sneakerGrey,
  },
  // untidy black hair, stubble; mustard henley; black jeans, brown boots
  m28: {
    first: 'Diego', last: 'Vargas',
    sex: 'male', slim: false, skin: SKIN.s4, hair: '#191411', hairStyle: 'messy', beard: 'stubble', iris: '#3A2618',
    shirt: {type: 'henley', color: '#B0812F', button: '#6A4A1E'},
    belt: BELT_DARK, pants: {type: 'jeans', color: '#26272B'}, boots: BOOTS.brown,
  },
  // blond buzz cut; olive-drab tee; coyote cargo trousers, brown combat boots
  m29: {
    first: 'Tyler', last: 'Grady',
    sex: 'male', slim: false, skin: SKIN.s1, hair: '#C8A266', hairStyle: 'buzz', brow: '#A88A56', iris: '#4E6A80',
    shirt: {type: 'tee', color: '#5A5E3A'},
    belt: BELT_DARK, pants: {type: 'cargo', color: '#7A6A4A'}, boots: BOOTS.combatBrown,
  },
  // shoulder-length locs, stubble; grey-green work shirt; black jeans, grey trainers
  m30: {
    first: 'Malik', last: 'Thompson',
    sex: 'male', slim: false, skin: SKIN.s6, hair: '#16110E', hairStyle: 'locs', beard: 'stubble', iris: '#2E1E14',
    lip: '#4E2A24',
    shirt: {type: 'workshirt', color: '#5E6A5A', pockets: true},
    belt: BELT, pants: {type: 'jeans', color: '#26272B'}, boots: BOOTS.sneakerGrey,
  },
};

// ---------------------------------------------------------------- review sheet

const L0 = (() => { const v = [0.2, 1, -0.7], n = Math.hypot(...v); return v.map(c => c / n); })();
const L1 = (() => { const v = [-0.2, 1, 0.7], n = Math.hypot(...v); return v.map(c => c / n); })();
const light = n => {
  const d0 = Math.max(0, n[0] * L0[0] + n[1] * L0[1] + n[2] * L0[2]);
  const d1 = Math.max(0, n[0] * L1[0] + n[1] * L1[1] + n[2] * L1[2]);
  return Math.min(1, (0.4 + 0.6 * (d0 + d1)) / 0.74);
};

function modelBoxes(slim) {
  const aw = slim ? 3 : 4, boxes = [];
  const add = (u, v, w, h, d, min, inf) => boxes.push({u, v, w, h, d,
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

function renderView(px, slim, yawDeg, pitchDeg, scale, W, H, out, ox, oy, outW) {
  const t = yawDeg * Math.PI / 180, f = pitchDeg * Math.PI / 180;
  const c = [Math.sin(t) * Math.cos(f), Math.sin(f), Math.cos(t) * Math.cos(f)];
  const rl = Math.hypot(c[2], c[0]), r = [c[2] / rl, 0, -c[0] / rl];
  const up = [c[1] * r[2] - c[2] * r[1], c[2] * r[0] - c[0] * r[2], c[0] * r[1] - c[1] * r[0]];
  const dir = c.map(v => -v), boxes = modelBoxes(slim), target = [0, 16.5, 0];
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
  "'": ['00100', '00100', '01000', '00000', '00000', '00000', '00000'],
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
  const scale = 10, VW = 230, VH = 370, LABEL = 48, PAD = 20, COLS = 5;
  const cellW = VW * 3 + PAD, cellH = VH + LABEL + PAD;
  const rows = Math.ceil(entries.length / COLS), outW = cellW * COLS + PAD, outH = cellH * rows + PAD;
  const out = new Uint8Array(outW * outH * 4);
  for (let i = 0; i < outW * outH; i++) {
    out[i * 4] = 0x9A; out[i * 4 + 1] = 0xA4; out[i * 4 + 2] = 0xAB; out[i * 4 + 3] = 255;
  }
  entries.forEach(([id, spec, px], n) => {
    const cx = PAD + (n % COLS) * cellW, cy = PAD + Math.floor(n / COLS) * cellH;
    for (let yy = cy + LABEL - 6; yy < cy + LABEL + VH; yy++) {
      for (let xx = cx; xx < cx + VW * 3; xx++) {
        const oi = (yy * outW + xx) * 4;
        out[oi] = 0xB4; out[oi + 1] = 0xBC; out[oi + 2] = 0xC2;
      }
    }
    label(out, outW, (id.replace(/^([fm])0/, '$1') + ' ' + spec.first + ' ' + spec.last).toUpperCase(), cx + 6, cy + 6, 4);
    [[0, 0], [35, 8], [180, 0]].forEach(([yaw, pitch], k) =>
      renderView(px, spec.slim, yaw, pitch, scale, VW, VH, out, cx + k * VW, cy + LABEL, outW));
  });
  fs.mkdirSync(path.dirname(file), {recursive: true});
  fs.writeFileSync(file, png(outW, outH, out));
  console.log('sheet ' + path.relative(ROOT, file));
}

// ---------------------------------------------------------------- main

const check = process.argv.includes('--check');
const sheetAt = process.argv.indexOf('--sheet');
const sheetDir = sheetAt >= 0 ? path.resolve(process.argv[sheetAt + 1]) : null;
let differs = 0;
const rendered = [];
if (!check) fs.mkdirSync(OUT, {recursive: true});
for (const [id, spec] of Object.entries(SKINS)) {
  const px = paintSkin(spec);
  rendered.push([id, spec, px]);
  const bytes = png(SIZE, SIZE, px);
  const file = path.join(OUT, id + '.png');
  if (check) {
    const same = fs.existsSync(file) && fs.readFileSync(file).equals(bytes);
    if (!same) differs++;
    console.log((same ? 'same    ' : 'DIFFERS ') + path.relative(ROOT, file));
  } else {
    fs.writeFileSync(file, bytes);
    console.log('wrote ' + path.relative(ROOT, file));
  }
}
// The roster in Java (survivor/SurvivorRoster) must name the same people as the specs here.
const ROSTER = path.join(ROOT, 'src/main/java/com/antaurora/apofirstlight/survivor/SurvivorRoster.java');
const javaPeople = new Map();
if (fs.existsSync(ROSTER)) {
  for (const m of fs.readFileSync(ROSTER, 'utf8').matchAll(/person\("([fm]\d\d)", "([^"]+)", "([^"]+)"\)/g)) {
    javaPeople.set(m[1], m[2] + ' ' + m[3]);
  }
}
let rosterErrors = 0;
for (const [id, spec] of Object.entries(SKINS)) {
  const name = spec.first + ' ' + spec.last;
  if (javaPeople.get(id) !== name) {
    rosterErrors++;
    console.log('ROSTER  ' + id + ': spec "' + name + '", SurvivorRoster "' + (javaPeople.get(id) ?? '-') + '"');
  }
}
for (const id of javaPeople.keys()) {
  if (!SKINS[id]) {
    rosterErrors++;
    console.log('ROSTER  ' + id + ' is in SurvivorRoster but has no skin spec');
  }
}
if (rosterErrors) process.exitCode = 1;

const onlyAt = process.argv.indexOf('--only');
const only = onlyAt >= 0 ? new Set(process.argv[onlyAt + 1].split(',')) : null;
if (sheetDir && only) {
  sheet(rendered.filter(([id]) => only.has(id)), path.join(sheetDir, 'survivor_skins_selection.png'));
} else if (sheetDir) {
  sheet(rendered.filter(([, spec]) => spec.sex === 'female'), path.join(sheetDir, 'survivor_skins_female.png'));
  sheet(rendered.filter(([, spec]) => spec.sex === 'male'), path.join(sheetDir, 'survivor_skins_male.png'));
}
if (check && differs) process.exit(1);
