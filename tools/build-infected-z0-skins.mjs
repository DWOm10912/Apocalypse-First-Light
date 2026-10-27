// Z0 infected design samples (user 2026-10-26: "你直接设计3版完整的模型+贴图，先不做姿势，给我看 / 模型用main现在在弄的
// 最新旧版模型（改了髋部和肩部）"). 128 px skins on the vanilla 64x64 net doubled (2x2 texels per model pixel), the same
// net and shading model as Main's tools/survivor-skins-128.mjs. The base layer is the infected body (skin, wounds,
// lividity); the outer layer is torn clothing, so the holes in it show the wounds underneath. Design samples only:
// nothing here is read by the game.
//
//   node tools/build-infected-z0-skins.mjs     writes design/creatures/infected_z0/z0_a.png, z0_b.png, z0_c.png
//
// A  fresh   (hours to days): ashen grey-green skin, red-rimmed clouded eyes, slack mouth, bite on the neck
// B  weeks   : olive mottled skin, milky eyes, receded lips, forearm chunk torn out, trucker cap (brim is a mesh)
// C  months  : dried brown-grey skin, empty sockets, no lips or nose, ribs showing, slim arms, long patchy hair
//
// r2 (user 2026-10-26: "能不能就用现在的贴图版本，但是做6-7个变体，不同头发、衣服什么的 / 设定上就是刚感染的样子"): A is f1,
// and f2-f7 are more freshly turned people painted the same way, from a spec of skin tone, hair, outfit and bite
// (FRESH below). They write z0_f2.png ... z0_f7.png.
// r3 (user 2026-10-26: "这 7 个可以，接着给每个的脸加区别（眉形、皱纹、胡子等）"): per-face brows, eye and nose shape, lips,
// wrinkles, beards and a few accessories (glasses, earrings, run mascara), set in each variant's `face`.
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import {fileURLToPath} from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const OUT = path.join(ROOT, 'design/creatures/infected_z0');
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

// ---------------------------------------------------------------- colour (same shading model as survivor-skins-128)

const hexRgb = hex => [parseInt(hex.slice(1, 3), 16), parseInt(hex.slice(3, 5), 16), parseInt(hex.slice(5, 7), 16)];
const rgbHex = rgb => '#' + rgb.map(v => Math.max(0, Math.min(255, Math.round(v))).toString(16).padStart(2, '0')).join('');
const mix = (a, b, t) => {
  const A = hexRgb(a), B = hexRgb(b);
  return rgbHex(A.map((v, k) => v + (B[k] - v) * Math.max(0, Math.min(1, t))));
};
function rgbHsl([r, g, b]) {
  r /= 255; g /= 255; b /= 255;
  const max = Math.max(r, g, b), min = Math.min(r, g, b), l = (max + min) / 2;
  if (max === min) return [0, 0, l];
  const d = max - min, s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
  const h = max === r ? (g - b) / d + (g < b ? 6 : 0) : max === g ? (b - r) / d + 2 : (r - g) / d + 4;
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
  let h = (Math.imul(a | 0, 374761393) + Math.imul(b | 0, 668265263) + Math.imul((c | 0) + 7, 1013904223)) | 0;
  h = Math.imul(h ^ (h >>> 15), 0x85EBCA6B);
  h = Math.imul(h ^ (h >>> 13), 0xC2B2AE35);
  h ^= h >>> 16;
  return (h >>> 0) / 4294967296;
}
// Smooth value noise for mottling and stains, s texels per cell.
function vnoise(x, y, seed, s) {
  const fx = x / s, fy = y / s, ix = Math.floor(fx), iy = Math.floor(fy);
  const sm = t => t * t * (3 - 2 * t), tx = sm(fx - ix), ty = sm(fy - iy), h = (a, b) => hash(a, b, seed);
  const a = h(ix, iy) + (h(ix + 1, iy) - h(ix, iy)) * tx, b = h(ix, iy + 1) + (h(ix + 1, iy + 1) - h(ix, iy + 1)) * tx;
  return a + (b - a) * ty;
}
const grain = (x, y, z, k) => (hash(x * 31 + z, y, k) - 0.5) * 0.3;
// Normalised distance from an ellipse centre with a ragged edge: < 1 is inside.
const blobT = (x, y, cx, cy, rx, ry, seed, rough = 0.3) =>
  Math.hypot((x - cx) / rx, (y - cy) / ry) + (hash(x, y, seed) - 0.5) * rough;

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

// The two coordinates that run across a face, so marks can be placed per face: (x, y) on front and back, (z, y) on
// the sides, (x, z) on top and bottom.
const uv = (face, x, y, z) => face === 'right' || face === 'left' ? [z, y] : face === 'top' || face === 'bottom' ? [x, z] : [x, y];

// The same, moved apart per face so noise does not repeat on every side of a part.
const FACE_OFF = {front: 0, back: 37, right: 71, left: 113, top: 157, bottom: 199};
const nuv = (face, x, y, z) => { const [a, b] = uv(face, x, y, z); return [a + FACE_OFF[face], b]; };

const fabric = (colour, x, y, z, lvl) => [colour, lvl + (hash(x * 17 + z, y, 41) - 0.5) * 0.25];

// ---------------------------------------------------------------- shared infected body

// Marks are {part, face, cx, cy, rx, ry, ...} in the face coordinates from uv(); the first one that covers a texel wins.
function markAt(list, part, face, x, y, z) {
  if (!list) return null;
  const [a, b] = uv(face, x, y, z);
  for (const m of list) {
    if (m.part !== part || (m.face !== face && !(m.faces || []).includes(face))) continue;
    const t = blobT(a, b, m.cx, m.cy, m.rx, m.ry, m.seed || 3, m.rough ?? 0.3);
    if (t < (m.reach || 1)) return {m, t, a, b};
  }
  return null;
}

// Open wounds on the base layer.
function wound(V, hit, x, y, z) {
  const {m, t, a, b} = hit, W = V.wound;
  if (t >= 1 || m.kind === 'vein') {   // dark veins spreading from the wound, thinner further out
    const n = Math.abs(vnoise(a, b, m.seed + 11, 2.2) - 0.5);
    if (n < 0.07 * Math.min(1, (m.reach - t) / (m.reach - 1))) return [V.vein, -0.2 + grain(x, y, z, 8)];
    return null;
  }
  if (m.kind === 'chunk') {   // flesh torn out: muscle fibres in the middle, a dark crust round it
    if (t < 0.62) return [W.muscle, ((b & 1) ? -0.5 : 0.1) + (hash(a, b >> 1, 13) - 0.5) * 0.6];
    if (t < 0.85) return [W.crust, -0.3 + grain(x, y, z, 9)];
    return [mix(V.skin, W.crust, 0.5), -0.3];
  }
  if (m.kind === 'ribs') {   // skin gone between the ribs
    if (t < 0.8) return (b & 3) < 2 ? [W.bone, ((b & 3) === 0 ? 0.3 : -0.4) + grain(x, y, z, 10)] : [W.deep, -0.2];
    return [W.crust, -0.4];
  }
  // bite or gash: a deep middle, raw flesh, a bruised rim with tooth punctures
  if (t < 0.42) return [W.deep, -0.2];
  if (t < 0.72) return [W.raw, (hash(a, b, 14) - 0.5) * 0.7];
  if (m.kind === 'bite' && hash(a * 3, b, 15) < 0.3) return [W.deep, 0];
  return [W.rim, -0.2 + grain(x, y, z, 16)];
}

// Skin with mottling and lividity pooled low in the body (they stand upright).
function skinAt(V, part, face, x, y, z, lvl, low = 0) {
  const [a, b] = nuv(face, x, y, z);
  let c = V.skin;
  const n = vnoise(a + part.length * 7, b, V.seed + part.length, 4);
  if (n > V.mottle) c = mix(c, V.skinDark, (n - V.mottle) * 3);
  if (low > 0) c = mix(c, V.livid, low);
  return [c, lvl + grain(x, y, z, 2)];
}

function bloodAt(V, part, face, x, y, z) {
  const a = uv(face, x, y, z)[0];
  const d = (V.drips || []).find(p => p.part === part && p.face === face && a >= p.x && a < p.x + (p.w || 1)
    && y >= p.y0 && y <= p.y0 + p.len);
  if (!d) return null;
  const end = y === d.y0 + d.len;
  return [d.fresh ? V.blood : V.dried, end ? -0.6 : (hash(x, y, 17) - 0.5) * 0.4];
}

function hairBase(V, face, x, y, z) {
  const H = V.hair;
  if (!H) return null;
  let on = false;
  if (face === 'top') on = !(H.patchy && vnoise(x, z, 77, 3) < H.patchy) && !(H.bald && H.bald(x, z));
  else if (face === 'front') on = y < H.fringe(x);
  else if (face === 'right' || face === 'left') on = y < H.side(z);
  else if (face === 'back') on = y < H.back(x);
  if (!on) return face === 'top' ? [V.scalp || V.skin, 0.1 + grain(x, y, z, 18)] : null;
  const edge = face !== 'top' && (face === 'front' ? y === H.fringe(x) - 1 : face === 'back' ? y === H.back(x) - 1 : y === H.side(z) - 1);
  const streak = (hash(face === 'right' || face === 'left' ? z : x, y >> 2, 19) - 0.5) * 0.9;
  const colour = H.buzz ? mix(H.color, V.scalp || V.skin, H.buzz) : H.color;
  return [colour, (edge ? -0.5 : 0) + streak * (H.buzz ? 0.4 : 1) + (face === 'top' ? 0.3 : 0)];
}

// Brows sit on rows 5-6, mx 1-5 (mx 0 is the face edge, 7 the middle).
function browAt(F, x, y, mx, L, sk) {
  if (!F.brow) return null;
  if (F.browScar && L && mx === 3 && y >= 5 && y <= 7) return [sk, 0.6];   // an old scar through the left brow
  const c = mix(sk, F.brow, F.browShape === 'thin' ? 0.5 : 0.8);
  switch (F.browShape || 'straight') {
    case 'arched':
      if ((y === 5 && mx >= 3 && mx <= 4) || (y === 6 && (mx === 2 || mx === 5))) return [c, y === 5 ? 0 : -0.2];
      break;
    case 'thick':
      if ((y === 5 || y === 6) && mx >= 2 && mx <= 5) return [c, y === 5 ? 0.1 : -0.3];
      break;
    case 'bushy':
      if ((y === 5 || y === 6) && mx >= 1 && mx <= 5 && !(y === 5 && mx === 1)) return [c, (hash(x, y, 55) - 0.5) * 0.9];
      break;
    default:   // straight and thin
      if (y === 6 && mx >= 2 && mx <= 5) return [c, mx === 2 ? 0.2 : -0.2];
  }
  return null;
}

function faceFront(V, x, y) {
  const F = V.face, mx = Math.min(x, 15 - x), L = x >= 8, sk = V.skin, g = grain(x, y, 15, 1);
  // eyes: rows 8-9, white outer (mx 2-3), iris inner (mx 4-5), in dark sockets
  if (y === 8 || y === 9) {
    if (mx >= 2 && mx <= 5) {
      if (F.eyes === 'hollow') {
        const glint = !L && y === 9 && mx === 4;
        return glint ? [F.glint, 0] : [F.socket, mx === 2 || y === 8 ? -0.6 : 0];
      }
      if (F.eyes === 'milky') {
        if (L && y === 8) return [F.lid, -0.4];   // the left lid hangs half shut
        if (mx === 2) return [F.socket, y === 8 ? -0.3 : 0];
        if (mx === 4 && y === 9) return [F.pupil, 0];
        return [F.white, y === 8 ? -0.5 : 0.1];
      }
      // fresh: bloodshot white, clouded iris, no clear pupil
      if (F.narrow && y === 8 && mx >= 3) return [mix(sk, F.socket, 0.45), -0.3];
      if (mx === 2) return y === 8 ? [F.socket, -0.2] : [F.redWhite, -0.3];
      if (mx === 3) return [y === 9 ? F.white : F.redWhite, y === 8 ? -0.6 : 0];
      return [F.iris, (y === 8 ? -0.7 : 0.2) + (mx === 4 && y === 9 ? -0.5 : 0)];
    }
  }
  const brow = browAt(F, x, y, mx, L, sk);
  if (brow) return brow;
  // run mascara: dark streaks down from the eyes
  if (F.mascara && mx === 3 && y >= 10 && y <= (L ? 12 : 11)) return [mix(sk, F.mascara, y === 10 ? 0.8 : 0.55), -0.2];
  if (F.mustache && y === 11 && mx >= (F.mustacheFrom || 4)) return [F.mustache, (hash(x, y, 49) - 0.5) * 0.6 + (mx === 4 ? -0.3 : 0)];
  // sockets: brow shadow above, sagging dark below
  if (y === 7 && mx >= 1 && mx <= 6) return [mix(sk, F.socket, mx >= 2 && mx <= 5 ? 0.55 : 0.3), -0.3];
  if (y === 10 && mx >= 2 && mx <= 5) return F.eyes === 'fresh' ? [F.lidRed, -0.1] : [mix(sk, F.socket, 0.6), -0.2];
  if (y === 11 && mx >= 2 && mx <= 4) return [mix(sk, F.socket, 0.3), -0.2 + g];
  // nose
  if (F.nose === 'cavity') {
    if ((y === 10 || y === 11) && mx >= 6) return [F.socket, y === 10 ? -0.3 : 0];
    if (y === 9 && mx >= 6) return [V.wound.bone, -0.4];
  } else {
    const nw = F.noseWidth || 6, top = F.noseTop || 8;   // nostrils from mx nw (5 wide, 7 small); bridge from row top
    if (mx === 7 && y >= top && y <= 9) return [sk, 0.15 + g];
    if (y === 10 && mx >= nw) return [F.nose === 'dark' ? mix(sk, V.livid, 0.5) : sk, mx === 7 ? 0.3 : -0.1];
    if (y === 11 && mx >= nw) return [sk, mx === 7 ? -1.2 : mx === nw && nw < 6 ? -0.4 : -0.6];
  }
  // cheek hole: molars through the right cheek
  if (F.cheekHole && !L && mx >= 1 && mx <= 3 && y >= 11 && y <= 13) {
    return y === 12 && (mx & 1) ? [V.wound.bone, 0] : [V.wound.deep, mx === 1 ? -0.4 : 0];
  }
  // mouth
  if (F.mouth === 'slack') {
    const lip = F.lipColor ? mix(F.lip, F.lipColor, 0.55) : F.lip;
    if (y === 11 && mx >= 5) return F.lips === 'thin' ? [mix(sk, lip, 0.4), -0.3] : [lip, -0.3];
    if (y === 12 && mx >= 5) return [F.mouthDark, mx === 5 ? 0.2 : 0];
    if (y === 12 && mx === 4) return [lip, -0.5];
    if (y === 13 && mx >= (F.lips === 'full' ? 4 : 5)) return [lip, 0.1];
    if (F.lips === 'full' && y === 14 && mx >= 6) return [lip, -0.25];
  } else if (F.mouth === 'teeth' || F.mouth === 'bare') {
    const wide = F.mouth === 'bare' ? 3 : 4;
    if (y === 11 && mx >= wide + 1) return [F.gum, -0.3];
    if ((y === 12 || y === 13) && mx >= wide) {
      if (y === 13 && mx === wide) return [F.mouthDark, 0];
      const gap = (x + (y === 13 ? 1 : 0)) % 3 === 0;
      return gap ? [F.mouthDark, 0] : [V.wound.teeth, y === 12 ? 0.2 : -0.4];
    }
    if (y === 14 && mx >= wide + 1) return [F.gum, -0.5];
  }
  if (F.goatee && ((y === 15 && mx >= 5) || (y === 14 && mx >= 6))) return [F.goatee, (hash(x, y, 56) - 0.5) * 0.6 + (y === 15 ? -0.3 : 0)];
  // age: crow's feet and smile lines from 1, forehead lines and jowls from 2
  if (F.age >= 1 && mx === 1 && (y === 8 || y === 10)) return [sk, -0.5 + g];
  if (F.age >= 1 && mx === 3 && (y === 12 || y === 13)) return [sk, -0.45 + g];
  if (F.age >= 2 && (y === 3 || y === 5) && mx >= 3) return [sk, -0.4 + g];
  if (F.age >= 2 && y === 14 && mx >= 2 && mx <= 3) return [sk, -0.5 + g];
  // sunken cheeks under the cheekbones
  if (F.sunken && mx >= 1 && mx <= 3 && y >= 10 && y <= 13) return [mix(sk, V.skinDark, 0.5), (y === 10 ? 0.2 : -0.5) + g];
  if (F.stubble && y >= 12 && hash(x, y, 21) < (F.stubbleDensity || 0.28)) return [mix(sk, F.stubble, 0.45), -0.15];
  if (y === 15) return [sk, -0.6 + g];
  return [sk, (mx === 0 ? -0.45 : mx === 1 ? -0.15 : 0) + (y <= 1 ? 0.15 : 0) + g];
}

function headBase(V, face, x, y, z) {
  const blood = bloodAt(V, 'head', face, x, y, z);
  if (blood) return blood;
  const w = markAt(V.wounds, 'head', face, x, y, z);
  if (w) { const c = wound(V, w, x, y, z); if (c) return c; }
  const hair = hairBase(V, face, x, y, z);
  if (hair) return hair;
  const sk = V.skin, g = grain(x, y, z, 2);
  if (V.cracks && face !== 'bottom' && Math.abs(vnoise(nuv(face, x, y, z)[0], y, 31, 2.5) - 0.5) < 0.035) return [V.skinDark, -0.7];
  if (face === 'front') return faceFront(V, x, y);
  if (face === 'top') return [sk, 0.35 + g];
  if (face === 'bottom') return [sk, -1.5];
  if (face === 'right' || face === 'left') {
    if (V.face.earring && z === 7 && y === 12) return [V.face.earring, 0.4];
    if (z >= 6 && z <= 8 && y >= 7 && y <= 11) {   // ear; B's left one is torn
      if (V.tornEar && face === 'left' && y <= 8) return [V.wound.crust, -0.3];
      const inner = z >= 7 && y >= 8 && y <= 10;
      return [sk, inner ? (z === 7 ? -1.3 : -0.7) : z === 6 ? -0.2 : -0.5];
    }
    if (V.face.stubble && z >= 9 && y >= 11 && hash(z, y, 22) < (V.face.stubbleDensity || 0.28)) return [mix(sk, V.face.stubble, 0.45), -0.2];
    return [sk, (y >= 14 ? -0.8 : -0.25) + g];
  }
  return [sk, (y >= 14 ? -0.9 : -0.3) + g];
}

function bodyBase(V, face, x, y, z) {
  const blood = bloodAt(V, 'body', face, x, y, z);
  if (blood) return blood;
  const w = markAt(V.wounds, 'body', face, x, y, z);
  if (w) { const c = wound(V, w, x, y, z); if (c) return c; }
  const mx = Math.min(x, 15 - x);
  if (face === 'bottom' || (face !== 'top' && y >= 20)) return fabric(V.under, x, y, z, y === 20 ? 0.2 : face === 'bottom' ? -0.9 : 0);
  if (face === 'top') return skinAt(V, 'body', face, x, y, z, 0.3);
  if (face === 'front') {
    if (y === 0 && mx >= 5) return skinAt(V, 'body', face, x, y, z, -1.0);
    if (y === 2 && mx >= 2 && mx <= 5) return skinAt(V, 'body', face, x, y, z, V.thin ? 0.4 : -0.4);   // collarbones
    if (V.thin && y >= 6 && y <= 13 && (y & 1) === 0 && mx >= 2 && mx <= 6) return skinAt(V, 'body', face, x, y, z, -0.6);
    if (y === 16 && mx === 7) return skinAt(V, 'body', face, x, y, z, -1.2);
    return skinAt(V, 'body', face, x, y, z, mx === 0 ? -0.45 : 0, y >= 14 ? 0.15 : 0);
  }
  if (face === 'back') {
    if (mx === 7 && y >= 3 && y <= 18) return skinAt(V, 'body', face, x, y, z, V.thin ? 0.3 : -0.35);   // spine
    return skinAt(V, 'body', face, x, y, z, mx === 0 ? -0.5 : -0.1, y >= 12 ? 0.3 : 0.1);
  }
  return skinAt(V, 'body', face, x, y, z, y <= 3 ? -0.8 : -0.3, 0.1);
}

function armBase(V, name, part, face, x, y, z, W) {
  const blood = bloodAt(V, name, face, x, y, z);
  if (blood) return blood;
  const w = markAt(V.wounds, name, face, x, y, z);
  if (w) { const c = wound(V, w, x, y, z); if (c) return c; }
  const sc = (V.scratches || []).find(s => s.part === name && s.face === face && y >= s.y0 && y <= s.y1
    && Math.abs((face === 'front' || face === 'back' ? x : z) - (s.x0 + (y - s.y0) * s.slope)) < 0.6);
  if (sc) return [V.wound.raw, -0.3];
  const inner = part.side === 'R' ? 'left' : 'right', outer = part.side === 'R' ? 'right' : 'left';
  if (face === 'top') return skinAt(V, name, face, x, y, z, 0.3);
  if (face === 'bottom') return [mix(V.skin, V.livid, 0.6), -0.5];
  if (y >= 18) {   // hands: fingertips darken, nails dirty
    const tip = y >= 22 ? 0.75 : y >= 20 ? 0.4 : 0.2;
    if (face === inner) return skinAt(V, name, face, x, y, z, (y >= 20 && (z & 1) ? -0.4 : 0), tip);
    if (face === outer) {
      if (y === 19 && V.thin) return [V.wound.bone, -0.2];   // knuckles through the skin
      if (y >= 20 && (z === 2 || z === 4 || z === 6)) return skinAt(V, name, face, x, y, z, -0.6, tip);
      if (y === 23) return [V.nails, 0];
      return skinAt(V, name, face, x, y, z, 0, tip);
    }
    if (face === 'front' && y >= 20 && y <= 22 && x === Math.floor(W / 2)) return skinAt(V, name, face, x, y, z, -0.6, tip);
    return skinAt(V, name, face, x, y, z, -0.2, tip);
  }
  if (y === 17) return skinAt(V, name, face, x, y, z, -0.3, 0.1);
  if (face === inner) return skinAt(V, name, face, x, y, z, y <= 2 ? -1.1 : -0.55);
  if (face === 'back' && y >= 11 && y <= 12) return skinAt(V, name, face, x, y, z, y === 11 ? 0.25 : -0.5);
  if (V.thin && face === outer && y >= 12 && y <= 16 && (z === 3 || z === 5)) return skinAt(V, name, face, x, y, z, -0.7);
  return skinAt(V, name, face, x, y, z, face === 'back' ? -0.2 : 0, y >= 12 ? 0.1 : 0);
}

function legBase(V, name, part, face, x, y, z) {
  const blood = bloodAt(V, name, face, x, y, z);
  if (blood) return blood;
  const w = markAt(V.wounds, name, face, x, y, z);
  if (w) { const c = wound(V, w, x, y, z); if (c) return c; }
  const inner = part.side === 'R' ? 'left' : 'right', low = y >= 14 ? 0.25 + (y - 14) * 0.07 : 0;
  if (face === 'top' || y <= 1) return fabric(V.under, x, y, z, 0);
  if (face === 'bottom') return [mix(V.skin, V.livid, 0.8), -0.8];
  if (y >= 21 && face === 'front') return skinAt(V, name, face, x, y, z, y === 21 ? -0.3 : (x & 1) ? -0.7 : 0, low);
  if (V.thin && face === 'front' && y >= 13 && y <= 19 && x === 4) return [mix(V.skin, V.wound.bone, 0.45), 0.2];   // shin
  if (face === inner) return skinAt(V, name, face, x, y, z, y <= 4 ? -0.8 : -0.5, low);
  if (face === 'front' && y === 10) return skinAt(V, name, face, x, y, z, 0.25, low);
  if (face === 'back' && y === 11) return skinAt(V, name, face, x, y, z, -0.6, low);
  return skinAt(V, name, face, x, y, z, face === 'back' ? -0.2 : 0, low);
}

// ---------------------------------------------------------------- clothes (outer layer)

// Holes torn in the clothes: transparent inside, a dark frayed edge, blood soaked round the wounded ones.
function torn(V, part, face, x, y, z, cloth) {
  const hit = markAt(V.holes, part, face, x, y, z);
  if (hit) {
    if (hit.t < 0.8) return 'hole';
    if (hit.t < 1) return [mix(cloth, '#1A1410', 0.45), -0.3];
    if (hit.m.soak) return [mix(cloth, V.dried, 0.75 * (hit.m.reach - hit.t) / (hit.m.reach - 1) + 0.15), -0.2];
  }
  if (V.moth && vnoise(...nuv(face, x, y, z), V.seed + part.length * 5, 2.5) > V.moth) return 'hole';
  const a = uv(face, x, y, z)[0];
  const stain = (V.stains || []).find(s => s.part === part && s.face === face && a >= s.x && a < s.x + s.w
    && y >= s.y0 && y <= s.y0 + s.len + (hash(a, 0, 23) < 0.5 ? 1 : 0));
  if (stain) return [mix(cloth, stain.fresh ? V.blood : V.dried, 0.8), (hash(a, y, 24) - 0.5) * 0.4];
  return null;
}

function dirty(V, cloth, part, face, x, y, z) {
  const [a, b] = nuv(face, x, y, z), n = vnoise(a, b, V.seed + 50 + part.length, 3);
  return n > 0.66 ? mix(cloth, V.dirt, (n - 0.66) * 2.2) : cloth;
}

function plaid(x, y) {
  const bx = (x + 1) % 6 < 2, by = (y + 2) % 6 < 2;
  if (bx && by) return ['#1E1716', 0];
  if (bx || by) return ['#47201C', 0];
  return ['#8C2C26', x % 6 === 4 || y % 6 === 4 ? 0.35 : 0];
}

const CLOTHES = {
  // A: light blue short-sleeve work shirt tucked into khakis, belt, black shoes
  a: {
    body(V, face, x, y, z) {
      const mx = Math.min(x, 15 - x), S = '#8EA8C0', P = '#9C8B69';
      if (face === 'bottom' || (face !== 'top' && y >= 21)) return fabric(dirty(V, P, 'body', face, x, y, z), x, y, z, face === 'bottom' ? -0.9 : 0);
      if (face !== 'top' && y >= 19) return face === 'front' && mx === 7 ? ['#A08C58', y === 19 ? 0.4 : -0.2] : fabric('#3A2C21', x, y, z, y === 19 ? 0.2 : -0.2);
      const cloth = dirty(V, S, 'body', face, x, y, z), t = torn(V, 'body', face, x, y, z, cloth);
      if (t === 'hole') return null;
      if (t) return t;
      if (face === 'top') return fabric(cloth, x, y, z, z >= 3 && z <= 4 && mx >= 5 ? -0.3 : 0.2);
      if (face === 'front') {
        if (y <= 2 && mx === 7 || (y === 0 && mx === 6)) return null;   // open collar
        if (y <= 1 && mx >= 4) return fabric(mix(cloth, '#5E7890', 0.5), x, y, z, y === 0 ? 0.3 : -0.2);
        if (x === 8 && y >= 3) return (y & 3) === 0 ? ['#E2E0D6', 0] : fabric(cloth, x, y, z, 0.25);
        if (x === 7 && y >= 3) return fabric(cloth, x, y, z, -0.35);
        if (x >= 10 && x <= 13 && y >= 4 && y <= 8) return fabric(cloth, x, y, z, y === 4 ? -0.6 : x === 10 || x === 13 || y === 8 ? -0.35 : 0);
        if (y >= 17) return fabric(cloth, x, y, z, -0.25 - (hash(x, 0, 25) < 0.4 ? 0.3 : 0));
      }
      return fabric(cloth, x, y, z, mx === 0 ? -0.35 : face === 'back' ? -0.1 : face === 'front' ? 0 : -0.25);
    },
    arm(V, name, part, face, x, y, z) {
      if (y > 7 && face !== 'top') return null;
      const cloth = dirty(V, '#8EA8C0', name, face, x, y, z), t = torn(V, name, face, x, y, z, cloth);
      if (t === 'hole') return null;
      if (t) return t;
      const inner = part.side === 'R' ? 'left' : 'right';
      if (face === 'bottom') return null;
      return fabric(cloth, x, y, z, y === 7 ? -0.5 : face === inner ? -0.5 : 0);
    },
    head(V, face, x, y, z) {   // messy tufts over the short hair
      const H = V.hair;
      if (face === 'bottom') return null;
      if (face === 'top') return hash(x, z, 42) < 0.22 ? [H.color, 0.2 + (hash(x, z, 43) - 0.5) * 0.8] : null;
      const rows = face === 'front' ? H.fringe(x) : face === 'back' ? H.back(x) : H.side(z);
      if (y >= rows - 1 || hash(x * 3 + z, y, 44) < 0.5) return null;
      return [H.color, -0.2 + (hash(x + z, y, 45) - 0.5) * 0.8];
    },
    leg(V, name, part, face, x, y, z) {
      const inner = part.side === 'R' ? 'left' : 'right';
      if (face === 'bottom') return ['#2E2622', -0.6];
      if (y >= 20) {
        if (y === 23) return ['#4A4038', -0.2];
        return ['#2A2826', y === 20 ? 0.3 : face === 'front' && y === 21 && x >= 3 && x <= 4 ? 0.5 : -0.1 + grain(x, y, z, 26)];
      }
      const cloth = dirty(V, '#9C8B69', name, face, x, y, z), t = torn(V, name, face, x, y, z, cloth);
      if (t === 'hole') return null;
      if (t) return t;
      if (face === 'front' && x >= 3 && x <= 4) return fabric(cloth, x, y, z, x === 3 ? 0.25 : -0.15);   // crease
      if (face === 'front' && name === 'rleg' && y >= 9 && y <= 12 && vnoise(x, y, 61, 2) > 0.45) return fabric(mix(cloth, V.dirt, 0.6), x, y, z, 0);
      return fabric(cloth, x, y, z, y === 19 ? -0.5 : face === inner ? -0.45 : y >= 10 && y <= 11 && hash(x, y, 27) < 0.3 ? -0.3 : 0);
    },
  },
  // B: open red flannel over a grey tee, sleeves rolled to the elbow, faded jeans with a torn knee, work boots
  b: {
    body(V, face, x, y, z) {
      const mx = Math.min(x, 15 - x), J = '#40546C';
      if (face === 'bottom' || (face !== 'top' && y >= 21)) return fabric(J, x, y, z, face === 'bottom' ? -0.9 : 0);
      const tee = face === 'front' && mx >= 6, base = tee ? '#7C7E79' : null;
      const pl = plaid(face === 'right' || face === 'left' ? z : x, y), cloth = dirty(V, base || pl[0], 'body', face, x, y, z);
      const t = torn(V, 'body', face, x, y, z, cloth);
      if (t === 'hole') return null;
      if (t) return t;
      if (face === 'top') return fabric(cloth, x, y, z, 0.2 + pl[1]);
      if (tee) {
        if (y === 0 && mx === 7) return null;
        if (y === 0) return fabric(cloth, x, y, z, 0.3);
        return fabric(cloth, x, y, z, (mx === 6 ? -0.4 : 0));
      }
      if (face === 'front' && mx === 5) return fabric(cloth, x, y, z, -0.5 + pl[1]);                       // flannel edge
      if (face === 'front' && y <= 2 && mx >= 3) return fabric(cloth, x, y, z, 0.3 + pl[1]);              // collar
      if (y >= 19) return hash(x, z, 28) < 0.3 && y === 20 ? null : fabric(cloth, x, y, z, -0.3 + pl[1]); // untucked hem
      return fabric(cloth, x, y, z, pl[1] + (mx === 0 ? -0.35 : face === 'left' || face === 'right' ? -0.2 : 0));
    },
    arm(V, name, part, face, x, y, z) {
      if (face === 'bottom' || (y > 9 && face !== 'top')) return null;
      const pl = plaid(face === 'right' || face === 'left' ? z : x, y), cloth = dirty(V, pl[0], name, face, x, y, z);
      const t = torn(V, name, face, x, y, z, cloth);
      if (t === 'hole') return null;
      if (t) return t;
      const inner = part.side === 'R' ? 'left' : 'right';
      if (y >= 8) return fabric(mix(cloth, '#C9A090', 0.25), x, y, z, y === 9 ? -0.3 : 0.3);   // the rolled cuff
      return fabric(cloth, x, y, z, pl[1] + (face === inner ? -0.5 : 0));
    },
    leg(V, name, part, face, x, y, z) {
      const inner = part.side === 'R' ? 'left' : 'right';
      if (face === 'bottom') return ['#2A2420', -0.4];
      if (y >= 17) {
        const mud = vnoise(x + z, y, 71, 2) > 0.55 && y >= 19;
        if (y === 23) return ['#2A2420', 0];
        if (face === 'front' && y <= 20 && (x === 3 || x === 4)) return (y & 1) ? ['#C9B98E', 0] : ['#3A2A1C', 0];   // laces
        return [mud ? '#5B4A36' : '#5E4128', y === 17 ? 0.3 : grain(x, y, z, 29) + (face === inner ? -0.3 : 0)];
      }
      let cloth = '#40546C';
      const [a, b] = nuv(face, x, y, z), fade = vnoise(a + name.length * 9, b, 81, 4);
      if (face === 'front' && (fade > 0.55 || (y >= 9 && y <= 12))) cloth = mix(cloth, '#7E92A8', 0.45);
      cloth = dirty(V, cloth, name, face, x, y, z);
      // torn right knee: white threads across a hole
      if (name === 'rleg' && face === 'front' && y >= 9 && y <= 12 && x >= 2 && x <= 6) {
        if (y === 10 || y === 12) return hash(x, y, 30) < 0.25 ? null : ['#D4CFC0', 0];
        return null;
      }
      const t = torn(V, name, face, x, y, z, cloth);
      if (t === 'hole') return null;
      if (t) return t;
      if (face === (part.side === 'R' ? 'right' : 'left') && z === 4) return [mix(cloth, '#B49A5E', 0.35), -0.2];   // outseam
      return fabric(cloth, x, y, z, face === inner ? -0.45 : y === 16 ? -0.4 : 0);
    },
    // trucker cap: tan foam front panel, navy mesh round the sides, snapback gap at the back; the brim is a mesh
    head(V, face, x, y, z) {
      const N = '#2D3848';
      if (face === 'bottom') return null;
      if (face === 'top') return fabric(N, x, y, z, 0.2 + (Math.abs(x - 7.5) < 1 ? -0.3 : 0));
      if (y > 4) return null;
      if (face === 'front') return y === 0 ? fabric(N, x, y, z, 0) : fabric(dirty(V, '#C3B48E', 'head', face, x, y, z), x, y, z, y === 4 ? -0.5 : 0);
      if (face === 'back' && y >= 3 && Math.min(x, 15 - x) >= 6) return null;
      return [N, ((x + y + z) & 1) ? -0.6 : 0.1];
    },
  },
  // C: torn green cardigan over a filthy tank top, dark jeans worn off at the shins, barefoot
  c: {
    body(V, face, x, y, z) {
      const mx = Math.min(x, 15 - x);
      if (face === 'back' && y <= 10 && x >= 2 && x <= 13 && y <= 4 + Math.floor(hash(x, 0, 32) * 7)) {   // hair down the back
        return [V.hair.color, (hash(x, y >> 2, 33) - 0.5) * 0.9 + (x & 1 ? -0.3 : 0)];
      }
      const J = '#3C3A3D';
      if (face === 'bottom' || (face !== 'top' && y >= 20)) return fabric(dirty(V, J, 'body', face, x, y, z), x, y, z, face === 'bottom' ? -0.9 : 0);
      const tank = face === 'front' && mx >= 3, base = tank ? '#A39C86' : '#4A5644';
      const hem = tank ? 19 : 16 + Math.floor(hash(x, z, 34) * 4);
      if (face !== 'top' && y > hem) return null;
      if (tank && y <= 2 && mx >= 4) return null;   // scoop neck
      const cloth = dirty(V, base, 'body', face, x, y, z), t = torn(V, 'body', face, x, y, z, cloth);
      if (t === 'hole') return null;
      if (t) return t;
      if (face === 'front' && mx === 2) return fabric(cloth, x, y, z, -0.5);
      return fabric(cloth, x, y, z, (y === hem ? -0.5 : 0) + (mx === 0 ? -0.35 : 0) + ((x * 3 + y) % 4 === 0 && !tank ? -0.2 : 0));
    },
    arm(V, name, part, face, x, y, z) {
      const end = name === 'rarm' ? 9 + Math.floor(hash(x, z, 35) * 3) : 16 + Math.floor(hash(x, z, 36) * 2);
      if (face === 'bottom' || (y > end && face !== 'top')) return null;
      const cloth = dirty(V, '#4A5644', name, face, x, y, z), t = torn(V, name, face, x, y, z, cloth);
      if (t === 'hole') return null;
      if (t) return t;
      const inner = part.side === 'R' ? 'left' : 'right';
      return fabric(cloth, x, y, z, (y === end ? -0.5 : 0) + (face === inner ? -0.5 : 0) + ((y + x) % 4 === 0 ? -0.2 : 0));
    },
    leg(V, name, part, face, x, y, z) {
      const end = 12 + Math.floor(hash(x * 3 + z, name.length, 37) * 4);
      if (face === 'bottom' || (y > end && face !== 'top')) return null;
      const cloth = dirty(V, '#3C3A3D', name, face, x, y, z), t = torn(V, name, face, x, y, z, cloth);
      if (t === 'hole') return null;
      if (t) return t;
      const inner = part.side === 'R' ? 'left' : 'right';
      return fabric(cloth, x, y, z, (y === end ? -0.6 : 0) + (face === inner ? -0.45 : 0));
    },
    head(V, face, x, y, z) {   // loose strands over the long hair
      const H = V.hair;
      if (face === 'top' || face === 'bottom') return null;
      const col = face === 'right' || face === 'left' ? z : x;
      const reach = face === 'front' ? (Math.min(x, 15 - x) <= 1 ? 15 : 2) : 6 + Math.floor(hash(col, face.length, 38) * 10);
      if (y > reach || (y > 2 && hash(col, y >> 1, 39) < 0.35)) return null;
      return [H.color, -0.3 + (hash(col, y >> 2, 40) - 0.5) * 0.8];
    },
  },
};

// ---------------------------------------------------------------- the three versions

const WOUND = {deep: '#2A0C0A', raw: '#8A2018', rim: '#5E3440', crust: '#3A1A16', muscle: '#6A2A22', bone: '#CFC5A6', teeth: '#BFB38E'};

const VERSIONS = {
  a: {
    name: 'A 刚转化', slim: false, seed: 101,
    skin: '#A7AE9F', skinDark: '#8E9590', livid: '#7A6D84', mottle: 0.62, vein: '#4F5A6A', scalp: '#A7AE9F',
    blood: '#741812', dried: '#4E1A14', dirt: '#6E6A58', nails: '#4A3C44', under: '#3A3F48',
    wound: WOUND,
    face: {eyes: 'fresh', socket: '#3E3846', white: '#D2CCBE', redWhite: '#B9A49C', iris: '#9FAAB0', lidRed: '#5E4450',
      lip: '#5E5060', mouthDark: '#2A1513', nose: 'normal', mouth: 'slack', brow: '#3F3226', browShape: 'straight',
      stubble: '#4A4438'},
    hair: {color: '#3F3226', fringe: x => 2 + Math.floor(hash(x >> 1, 0, 5) * 3), side: z => (z <= 9 ? 6 : z <= 11 ? 9 : 2) - (hash(z, 1, 5) < 0.3 ? 1 : 0), back: x => 7 + (hash(x, 2, 5) < 0.4 ? 1 : 0)},
    wounds: [
      // the bite that turned him: left side of the neck into the shoulder, veins spreading from it
      {part: 'body', face: 'front', cx: 13, cy: 1.5, rx: 2.6, ry: 2.2, kind: 'bite', seed: 41, reach: 2.6},
      {part: 'body', face: 'top', cx: 13, cy: 4.5, rx: 2.4, ry: 2.6, kind: 'bite', seed: 42, reach: 2.4},
      {part: 'head', face: 'left', cx: 9, cy: 16, rx: 3, ry: 3, kind: 'vein', seed: 43, reach: 2},
    ],
    holes: [
      {part: 'body', face: 'front', cx: 13, cy: 1.5, rx: 3, ry: 2.6, seed: 45, soak: true, reach: 2},
      {part: 'body', face: 'top', cx: 13, cy: 4.5, rx: 2.6, ry: 3, seed: 46, soak: true, reach: 2},
      {part: 'larm', face: 'top', cx: 1, cy: 4, rx: 1.5, ry: 2, seed: 47, soak: true, reach: 2.5},
      {part: 'larm', face: 'front', cx: 1, cy: 0, rx: 1.5, ry: 2, seed: 48, soak: true, reach: 3},
    ],
    drips: [
      {part: 'head', face: 'front', x: 10, y0: 13, len: 2, fresh: true},
      {part: 'head', face: 'front', x: 6, y0: 14, len: 1},
      {part: 'rarm', face: 'right', x: 3, y0: 18, len: 3, w: 1},
    ],
    stains: [
      {part: 'body', face: 'front', x: 5, w: 6, y0: 0, len: 1, fresh: true},
      {part: 'body', face: 'front', x: 5, w: 2, y0: 2, len: 3},
      {part: 'body', face: 'front', x: 9, w: 2, y0: 2, len: 2, fresh: true},
      {part: 'body', face: 'front', x: 10, w: 1, y0: 4, len: 7},
      {part: 'body', face: 'front', x: 12, w: 2, y0: 4, len: 8},
    ],
    scratches: [
      {part: 'rarm', face: 'right', y0: 11, y1: 15, x0: 2, slope: 0.6},
      {part: 'rarm', face: 'right', y0: 12, y1: 16, x0: 5, slope: 0.4},
      {part: 'larm', face: 'front', y0: 12, y1: 14, x0: 1, slope: 0.7},
    ],
  },
  b: {
    name: 'B 数周', slim: false, seed: 202, tornEar: true,
    skin: '#8E9677', skinDark: '#6A7058', livid: '#5E4F5E', mottle: 0.5, vein: '#3E4652', scalp: '#8E9677',
    blood: '#7A1A14', dried: '#4A2018', dirt: '#5B4C38', nails: '#2E2622', under: '#34383C',
    wound: WOUND,
    face: {eyes: 'milky', socket: '#3E3A36', white: '#CBC9B4', pupil: '#8A8878', lid: '#6A7058', gum: '#4A2A26',
      mouthDark: '#1E100E', nose: 'dark', mouth: 'teeth', sunken: true, stubble: '#2E2A24'},
    hair: {color: '#2F2A24', fringe: () => 2, side: z => (z <= 9 ? 7 : z <= 11 ? 9 : 3), back: () => 9},
    wounds: [
      // a chunk torn out of the right forearm, and a gash on the left flank under the shirt
      {part: 'rarm', face: 'right', cx: 4, cy: 14, rx: 2.8, ry: 3, kind: 'chunk', seed: 51, reach: 2.2},
      {part: 'rarm', face: 'front', cx: 1, cy: 14, rx: 1.8, ry: 2.5, kind: 'chunk', seed: 52, reach: 2.2},
      {part: 'body', face: 'left', cx: 4, cy: 11, rx: 1.6, ry: 3.5, kind: 'gash', seed: 53, reach: 1.8},
    ],
    holes: [
      {part: 'body', face: 'left', cx: 4, cy: 11, rx: 2.2, ry: 4, seed: 54, soak: true, reach: 1.6},
      {part: 'body', face: 'back', cx: 4, cy: 14, rx: 1.6, ry: 1.2, seed: 55},
    ],
    drips: [
      {part: 'head', face: 'front', x: 5, y0: 14, len: 1},
      {part: 'head', face: 'front', x: 10, y0: 14, len: 1},
      {part: 'rarm', face: 'right', x: 3, y0: 18, len: 4, w: 2},
      {part: 'rarm', face: 'front', x: 1, y0: 18, len: 3, w: 1},
    ],
    stains: [
      {part: 'body', face: 'front', x: 7, w: 2, y0: 1, len: 9},
      {part: 'body', face: 'front', x: 9, w: 1, y0: 1, len: 5},
      {part: 'rleg', face: 'front', x: 5, w: 2, y0: 2, len: 5},
    ],
  },
  c: {
    name: 'C 数月', slim: true, seed: 303, thin: true, cracks: true, moth: 0.74,
    skin: '#7A6E58', skinDark: '#544A3A', livid: '#3E3530', mottle: 0.45, vein: '#3A3028', scalp: '#6E6250',
    blood: '#4A1A14', dried: '#3A1A14', dirt: '#4E4434', nails: '#2A2420', under: '#2E2C2E',
    wound: {...WOUND, bone: '#ADA387', deep: '#1E1210', raw: '#5E2A22', rim: '#3E2A24', muscle: '#5A2620', crust: '#2E1C18'},
    face: {eyes: 'hollow', socket: '#16110F', glint: '#7F7A6A', gum: '#4D2620', mouthDark: '#16100E', nose: 'cavity',
      mouth: 'bare', sunken: true, cheekHole: true},
    hair: {color: '#2A231E', patchy: 0.36,
      fringe: x => (Math.min(x, 15 - x) <= 1 ? 15 : Math.min(x, 15 - x) <= 2 ? 6 : 3), side: () => 16, back: () => 16},
    wounds: [
      // ribs through the dried skin on the left of the chest, the right forearm worn to the tendons
      {part: 'body', face: 'front', cx: 11, cy: 10.5, rx: 3.8, ry: 7, kind: 'ribs', seed: 61, rough: 0.2},
      {part: 'body', face: 'left', cx: 3.5, cy: 10.5, rx: 3, ry: 6.5, kind: 'ribs', seed: 62, rough: 0.2},
      {part: 'rarm', face: 'right', cx: 4, cy: 14, rx: 2.2, ry: 3.2, kind: 'chunk', seed: 63, reach: 1},
      {part: 'lleg', face: 'front', cx: 3, cy: 17, rx: 2, ry: 2, kind: 'chunk', seed: 64, reach: 1},
    ],
    holes: [
      {part: 'body', face: 'front', cx: 11, cy: 10.5, rx: 4.6, ry: 8, seed: 65, rough: 0.4},
      {part: 'body', face: 'left', cx: 3.5, cy: 10.5, rx: 3.6, ry: 7.5, seed: 66, rough: 0.4},
      {part: 'larm', face: 'back', cx: 3, cy: 8, rx: 1.8, ry: 2.5, seed: 67},
    ],
    drips: [],
    stains: [
      {part: 'body', face: 'front', x: 5, w: 6, y0: 3, len: 5},
      {part: 'body', face: 'front', x: 6, w: 2, y0: 8, len: 8},
    ],
  },
};

// ---------------------------------------------------------------- fresh variants (r2)

// A living skin tone turned ashen: towards its own grey with a slight green cast, so dark skin stays dark.
function ashen(base, k = 0.78) {
  const [r, g, b] = hexRgb(base), l = 0.3 * r + 0.59 * g + 0.11 * b;
  return mix(base, rgbHex([l * 0.9, l * 1.04, l * 0.92]), k);
}

// Head texels are 16 per side. fringe(x): rows on the face; side(z): rows on the sides, z 0 at the back; back(x): rows
// on the back. tufts / curls / volume say how the hat layer is used; tail is the ponytail band colour.
const HAIR = {
  short: {fringe: x => 2 + Math.floor(hash(x >> 1, 0, 5) * 3), side: z => (z <= 9 ? 6 : z <= 11 ? 9 : 2) - (hash(z, 1, 5) < 0.3 ? 1 : 0),
    back: x => 7 + (hash(x, 2, 5) < 0.4 ? 1 : 0), tufts: 0.22},
  ponytail: {fringe: x => { const mx = Math.min(x, 15 - x); return mx <= 1 ? 7 : mx <= 3 ? 4 : mx === 7 ? 1 : 2; },
    side: z => (z <= 10 ? 8 : z <= 12 ? 5 : 3), back: () => 10, tail: '#7A2A30'},
  bob: {fringe: x => (Math.min(x, 15 - x) <= 1 ? 14 : x <= 8 ? 5 : 3), side: z => (z >= 13 ? 12 : 14), back: () => 15, volume: true},
  shortbob: {fringe: x => (Math.min(x, 15 - x) <= 1 ? 9 : x <= 8 ? 4 : 3), side: z => (z >= 13 ? 8 : 10), back: () => 11, volume: true},
  curly: {fringe: x => 3 + (x & 1), side: z => (z <= 11 ? 7 : 4), back: () => 10, curls: 0.6},
  balding: {fringe: () => 0, side: z => (z <= 11 ? 7 : z <= 12 ? 5 : 0), back: () => 9, bald: (x, z) => z >= 5 && x >= 3 && x <= 12},
};

function hairOver(V, face, x, y, z) {
  const H = V.hair;
  if (face === 'bottom') return null;
  if (face === 'top') {
    if (H.bald && H.bald(x, z)) return null;
    const p = H.curls || H.tufts || (H.volume ? 1 : 0);
    return p && hash(x, z, 42) < p ? [H.color, 0.2 + (hash(x, z, 43) - 0.5) * 0.8] : null;
  }
  const col = face === 'right' || face === 'left' ? z : x;
  const rows = face === 'front' ? H.fringe(x) : face === 'back' ? H.back(x) : H.side(z);
  if (y >= rows) return null;
  if (H.volume) return y >= rows - 1 && hash(col, y, 46) < 0.5 ? null : [H.color, -0.3 + (hash(col, y >> 2, 47) - 0.5) * 0.9];
  if (H.curls) return hash(x * 3 + z, y, 48) < H.curls ? [H.color, (x + y + z) & 1 ? -0.5 : 0.3] : null;
  if (H.tufts) return y >= rows - 1 || hash(x * 3 + z, y, 44) < 0.5 ? null : [H.color, -0.2 + (hash(x + z, y, 45) - 0.5) * 0.8];
  return null;
}

function freshVersion(s) {
  const skin = ashen(s.skin);
  return {
    name: s.name, slim: s.slim, seed: s.seed, skin, skinDark: rgbHex(tone(skin, -0.9)), livid: mix(skin, '#5E4A70', 0.45),
    mottle: 0.62, vein: mix(skin, '#2E3A4E', 0.6), scalp: skin, blood: '#741812', dried: '#4E1A14', dirt: s.dirt || '#6E6A58',
    nails: mix(skin, '#3A2C38', 0.6), under: '#3A3F48', wound: WOUND,
    face: {eyes: 'fresh', socket: mix(skin, '#1E1A26', 0.62), white: '#D2CCBE', redWhite: '#B9A49C', iris: s.iris || '#9FAAB0',
      lidRed: mix(skin, '#5E2E3A', 0.55), lip: mix(skin, '#4E3E58', 0.55), mouthDark: '#2A1513', nose: 'normal', mouth: 'slack',
      brow: s.brow, stubble: s.stubble, mustache: s.mustache, ...(s.face || {})},
    hair: {...HAIR[s.hair.style], color: s.hair.color},
    wounds: s.wounds, holes: s.holes || [], drips: s.drips || [], stains: s.stains || [], scratches: s.scratches || [],
    outfit: s.outfit,
  };
}

// Neck openings and other places the top leaves bare, before tears and stains are looked at.
function opening(T, face, x, y) {
  if (face !== 'front') return false;
  const mx = Math.min(x, 15 - x);
  switch (T.type) {
    case 'scrubs': return y <= 3 && mx >= 4 + y;
    case 'tee': case 'hivis': return (y === 0 && mx >= 5) || (y === 1 && mx >= 6);
    case 'hoodie': return y === 0 && mx >= 5;
    case 'blouse': return y <= 2 && mx === 7;
    case 'coverall': return y === 0 && mx === 7;
    case 'cardigan': return (y === 0 && mx >= 5) || (y === 1 && mx >= 6);
    default: return false;
  }
}

function topBody(V, T, face, x, y, z) {
  const mx = Math.min(x, 15 - x);
  if (opening(T, face, x, y)) return null;
  const col = T.type === 'cardigan' && face === 'front' && mx >= 5 ? T.inner : T.color;
  const cloth = dirty(V, col, 'body', face, x, y, z), t = torn(V, 'body', face, x, y, z, cloth);
  if (t === 'hole') return null;
  if (t) return t;
  const L = lvl => fabric(cloth, x, y, z, lvl);
  if (face === 'top') return L(0.2 + (T.type === 'hoodie' && z <= 3 ? -0.4 : 0));
  if (face === 'front') {
    switch (T.type) {
      case 'scrubs':
        if (y <= 4 && mx === Math.min(7, 3 + y)) return L(-0.5);
        if (x >= 10 && x <= 12 && y >= 5 && y <= 8) return L(y === 5 ? -0.6 : x === 10 || x === 12 || y === 8 ? -0.3 : 0);
        break;
      case 'tee':
        if ((y === 1 && mx === 5) || (y === 2 && mx >= 6) || (y === 0 && mx === 4)) return L(-0.45);
        break;
      case 'hoodie':
        if (y <= 1 && mx >= 3 && mx <= 4) return L(-0.5);
        if ((x === 6 || x === 9) && y >= 1 && y <= 6) return ['#D6D4CC', y === 6 ? -0.4 : 0];
        if (y >= 12 && y <= 17 && mx >= 2) return L(y === 12 || mx === 2 ? -0.5 : -0.15);
        if (y >= 19) return L(-0.35);
        break;
      case 'blouse':
        if (y <= 1 && mx >= 4) return L(y === 0 ? 0.4 : -0.3);
        if (x === 8 && y >= 3) return (y & 3) === 1 ? ['#B8B4AA', 0] : L(-0.15);
        if (x === 7 && y >= 3) return L(-0.25);
        break;
      case 'coverall':
        if (y <= 1 && mx >= 4) return L(-0.45);
        if (x === 8 && y >= 1) return ['#8E9296', 0.1];   // zip
        if (x >= 10 && x <= 13 && y >= 4 && y <= 6) return x === 10 || x === 13 || y === 4 || y === 6 ? ['#8E9AA6', 0] : ['#D8D4C8', 0];   // blank name patch
        if (y >= 19) return L(-0.3);
        break;
      case 'cardigan':
        if (mx >= 5) return L(mx === 5 ? -0.4 : y === 1 || (y === 2 && mx >= 6) ? -0.45 : 0);
        if (mx === 4) return y % 4 === 2 ? ['#D8D0C0', 0] : L(-0.3);
        if (y >= 18) return L(-0.35 + (x & 1 ? -0.15 : 0));
        break;
    }
    if (y >= 17 && T.tucked) return L(-0.25 - (hash(x, 0, 25) < 0.4 ? 0.3 : 0));
    return L(mx === 0 ? -0.35 : 0);
  }
  if (T.type === 'hoodie' && face === 'back' && y <= 6 && mx >= 1) return L(y === 6 || mx === 1 ? -0.6 : -0.15);   // hood
  if (face === 'back' && (T.type === 'coverall' || T.type === 'hoodie') && y >= 19) return L(-0.3);
  return L(face === 'back' ? (mx === 0 ? -0.35 : -0.1) : -0.25);
}

function pants(V, O, part, face, x, y, z, lvl = 0) {
  const B = O.bottom;
  let c = B.color;
  if (B.type === 'jeans' && face === 'front') {
    const [a, b] = nuv(face, x, y, z);
    if (vnoise(a + part.length * 9, b, 81, 4) > 0.55) c = mix(c, '#7E92A8', 0.4);
  }
  c = dirty(V, c, part, face, x, y, z);
  const t = torn(V, part, face, x, y, z, c);
  if (t === 'hole') return null;
  return t || fabric(c, x, y, z, lvl);
}

function shoe(S, face, x, y, z, outer, top) {
  if (y === 23) return [S.sole, -0.1];
  if (S.type === 'sneakers') {
    if (face === outer && y === 21 && z >= 2 && z <= 5) return [S.accent, 0];
    if (face === 'front' && y === 20 && (x === 3 || x === 4)) return [S.accent, -0.3];
    return [S.color, y === top ? 0.2 : grain(x, y, z, 53) - (face === 'back' ? 0.2 : 0)];
  }
  if (S.type === 'boots' && face === 'front' && y <= 20 && (x === 3 || x === 4)) return y & 1 ? ['#C9B98E', 0] : ['#2A2018', 0];
  return [S.color, y === top ? 0.3 : face === 'front' && y === top + 1 && x >= 3 && x <= 4 ? 0.5 : -0.1 + grain(x, y, z, 26)];
}

const FRESH_CLOTHES = {
  head(V, face, x, y, z) {
    const H = V.hair, G = V.face.glasses;
    if (G && face === 'front') {
      const mx = Math.min(x, 15 - x);
      if (((y === 7 || y === 10) && mx >= 1 && mx <= 6) || (y >= 7 && y <= 10 && (mx === 1 || mx === 6))) return [G, y === 7 ? 0.3 : -0.2];
      if (y === 8 && mx === 7) return [G, 0];   // bridge
      if (y >= 8 && y <= 9 && mx >= 2 && mx <= 5) return null;   // clear lenses
    }
    if (G && (face === 'left' || face === 'right') && y === 8 && z >= 9) return [G, -0.2];   // temples
    if (H.tail && face === 'back') {
      if (y >= 6 && y <= 7 && x >= 7 && x <= 8) return [H.tail, y === 6 ? 0.2 : -0.3];
      if (y >= 8 && x >= 6 && x <= 9) return [H.color, (x === 6 || x === 9 ? -0.6 : 0) + (hash(x, y >> 1, 50) - 0.5) * 0.6];
    }
    return hairOver(V, face, x, y, z);
  },
  body(V, face, x, y, z) {
    const O = V.outfit;
    if (V.hair.tail && face === 'back' && y <= 8 && x >= 7 && x <= 8) {   // the ponytail down the back
      return [V.hair.color, (hash(x, y >> 1, 51) - 0.5) * 0.6 + (y === 8 ? -0.5 : 0)];
    }
    if (face === 'bottom' || (face !== 'top' && y >= 21)) return pants(V, O, 'body', face, x, y, z, face === 'bottom' ? -0.9 : 0);
    if (O.top.tucked && face !== 'top' && y >= 19) {
      const mx = Math.min(x, 15 - x);
      if (!O.belt) return pants(V, O, 'body', face, x, y, z, y === 19 ? 0.3 : -0.2);
      return face === 'front' && mx === 7 ? ['#A08C58', y === 19 ? 0.4 : -0.2] : fabric(O.belt, x, y, z, y === 19 ? 0.2 : -0.2);
    }
    return topBody(V, O.top, face, x, y, z);
  },
  arm(V, name, part, face, x, y, z) {
    const T = V.outfit.top, end = {short: 7, rolled: 9, long: 17}[T.sleeve];
    if (face === 'bottom' || (y > end && face !== 'top')) return null;
    const inner = part.side === 'R' ? 'left' : 'right';
    const cloth = dirty(V, T.color, name, face, x, y, z), t = torn(V, name, face, x, y, z, cloth);
    if (t === 'hole') return null;
    if (t) return t;
    const cuff = T.sleeve === 'long' && y >= 16 && (T.type === 'hoodie' || T.type === 'cardigan');
    return fabric(cloth, x, y, z, (y === end ? -0.5 : 0) + (cuff ? -0.3 : 0) + (face === inner ? -0.5 : 0)
      + (T.sleeve === 'rolled' && y >= 8 ? 0.3 : 0));
  },
  leg(V, name, part, face, x, y, z) {
    const O = V.outfit, B = O.bottom, S = O.shoes;
    const inner = part.side === 'R' ? 'left' : 'right', outer = part.side === 'R' ? 'right' : 'left';
    if (face === 'bottom') return [S.sole, -0.4];
    const top = S.type === 'boots' ? 17 : S.type === 'flats' ? 21 : 20;
    if (y >= top) return shoe(S, face, x, y, z, outer, top);
    if (S.socks && y >= top - 2) return [S.socks, y === top - 2 ? 0.2 : -0.1 + grain(x, y, z, 52)];
    const end = B.type === 'shorts' ? 8 : 19;
    if (y > end && face !== 'top') return null;
    const p = pants(V, O, name, face, x, y, z);
    if (!p) return null;
    let lvl = p[1] + (face === inner ? -0.45 : 0) + (y === end ? -0.5 : 0);
    if (B.type === 'shorts' && face === outer && y >= 3 && y <= 7 && z >= 2 && z <= 5) lvl += y === 3 ? -0.5 : z === 2 || z === 5 ? -0.3 : 0.1;
    if ((B.type === 'slacks' || B.type === 'scrubs') && face === 'front' && x >= 3 && x <= 4) lvl += x === 3 ? 0.25 : -0.15;
    if (B.type === 'jeans' && face === outer && z === 4) return [mix(p[0], '#B49A5E', 0.35), -0.2];
    if (B.type === 'coverall' && face === 'front' && y >= 9 && y <= 12) lvl -= 0.25;
    return [p[0], lvl];
  },
};

// f1 is version A above.
const FRESH = {
  f2: {name: '2 护士', slim: true, seed: 402, skin: '#7A4E35', iris: '#A9A69C', brow: '#1E1A18',
    face: {browShape: 'arched', noseWidth: 5, lips: 'full', earring: '#C8A848', lipColor: '#8A6A74'},
    hair: {style: 'ponytail', color: '#1E1A18'},
    outfit: {top: {type: 'scrubs', color: '#3F8A8C', sleeve: 'short'}, bottom: {type: 'scrubs', color: '#3F8A8C'},
      shoes: {type: 'sneakers', color: '#DAD7D0', accent: '#8C9AA8', sole: '#B8B4AC'}},
    wounds: [
      {part: 'rarm', face: 'right', cx: 4, cy: 13.5, rx: 2.4, ry: 2, kind: 'bite', seed: 71, reach: 2.6},
      {part: 'rarm', face: 'front', cx: 1, cy: 13.5, rx: 1.4, ry: 1.8, kind: 'bite', seed: 72, reach: 2.2},
    ],
    drips: [
      {part: 'head', face: 'front', x: 5, y0: 13, len: 2, fresh: true},
      {part: 'rarm', face: 'right', x: 4, y0: 15, len: 6, fresh: true},
    ],
    stains: [
      {part: 'body', face: 'front', x: 4, w: 2, y0: 4, len: 6},
      {part: 'body', face: 'front', x: 9, w: 1, y0: 5, len: 4},
    ]},
  f3: {name: '3 卫衣', slim: false, seed: 403, skin: '#E3B994', brow: '#16130F',
    face: {browShape: 'thin', narrow: true, noseWidth: 7, lips: 'thin'},
    hair: {style: 'short', color: '#16130F'},
    outfit: {top: {type: 'hoodie', color: '#7D8288', sleeve: 'long'}, bottom: {type: 'jeans', color: '#3A4C66'},
      shoes: {type: 'sneakers', color: '#D8D6CE', accent: '#2A2C30', sole: '#E2E0D8'}},
    wounds: [{part: 'larm', face: 'left', cx: 4, cy: 20, rx: 2.4, ry: 1.7, kind: 'bite', seed: 73, reach: 2.4}],
    drips: [
      {part: 'head', face: 'front', x: 10, y0: 13, len: 2, fresh: true},
      {part: 'larm', face: 'left', x: 4, y0: 21, len: 2, fresh: true},
      {part: 'larm', face: 'front', x: 5, y0: 19, len: 4, fresh: true},
    ],
    stains: [
      {part: 'body', face: 'front', x: 9, w: 2, y0: 1, len: 4, fresh: true},
      {part: 'larm', face: 'left', x: 3, w: 3, y0: 15, len: 2},
    ]},
  f4: {name: '4 上班族', slim: true, seed: 404, skin: '#B07C58', brow: '#2E2018',
    face: {browShape: 'arched', mascara: '#2A2228', lipColor: '#8E3A44', age: 1},
    hair: {style: 'bob', color: '#2E2018'},
    outfit: {top: {type: 'blouse', color: '#E2DDD0', sleeve: 'long', tucked: true}, belt: '#1E1A18',
      bottom: {type: 'slacks', color: '#3A3C42'}, shoes: {type: 'flats', color: '#1E1C1C', sole: '#2A2626'}},
    wounds: [
      {part: 'rarm', face: 'top', cx: 3, cy: 4, rx: 2.4, ry: 2.8, kind: 'bite', seed: 74, reach: 2},
      {part: 'rarm', face: 'right', cx: 4, cy: 1, rx: 2.4, ry: 2, kind: 'bite', seed: 75, reach: 2.6},
      {part: 'body', face: 'top', cx: 1.5, cy: 4, rx: 2, ry: 2.4, kind: 'bite', seed: 76, reach: 2},
    ],
    holes: [
      {part: 'rarm', face: 'top', cx: 3, cy: 4, rx: 2.8, ry: 3.2, seed: 77, soak: true, reach: 2},
      {part: 'rarm', face: 'right', cx: 4, cy: 1, rx: 2.8, ry: 2.4, seed: 78, soak: true, reach: 2.4},
      {part: 'body', face: 'top', cx: 1.5, cy: 4, rx: 2.4, ry: 2.8, seed: 79, soak: true, reach: 2},
    ],
    drips: [{part: 'head', face: 'front', x: 6, y0: 13, len: 1}],
    stains: [
      {part: 'body', face: 'front', x: 0, w: 2, y0: 0, len: 9},
      {part: 'rarm', face: 'front', x: 0, w: 2, y0: 2, len: 8},
      {part: 'rarm', face: 'right', x: 3, w: 3, y0: 4, len: 6, fresh: true},
    ]},
  f5: {name: '5 修车工', slim: false, seed: 405, skin: '#D3A386', dirt: '#3E3A34', iris: '#A4AEB4',
    hair: {style: 'balding', color: '#8E8A84'}, brow: '#8A8680', mustache: '#5E5A56', stubble: '#6A6660',
    face: {browShape: 'bushy', age: 2, noseTop: 7, noseWidth: 5},
    outfit: {top: {type: 'coverall', color: '#4E6274', sleeve: 'long', tucked: true}, bottom: {type: 'coverall', color: '#4E6274'},
      shoes: {type: 'boots', color: '#2A2622', sole: '#1A1816'}},
    wounds: [
      {part: 'larm', face: 'front', cx: 3.5, cy: 13, rx: 2.2, ry: 2, kind: 'bite', seed: 81, reach: 2.4},
      {part: 'larm', face: 'left', cx: 4, cy: 13, rx: 2, ry: 2, kind: 'bite', seed: 82, reach: 2.4},
    ],
    holes: [
      {part: 'larm', face: 'front', cx: 3.5, cy: 13, rx: 2.8, ry: 2.6, seed: 83, soak: true, reach: 1.8},
      {part: 'larm', face: 'left', cx: 4, cy: 13, rx: 2.6, ry: 2.6, seed: 84, soak: true, reach: 1.8},
    ],
    drips: [
      {part: 'head', face: 'front', x: 10, y0: 13, len: 2, fresh: true},
      {part: 'larm', face: 'front', x: 3, y0: 18, len: 4, fresh: true},
    ],
    stains: [{part: 'body', face: 'front', x: 9, w: 2, y0: 1, len: 6}]},
  f6: {name: '6 T 恤短裤', slim: false, seed: 406, skin: '#8A5A3B', iris: '#A9A69C', brow: '#14110F',
    face: {browShape: 'thick', browScar: true, goatee: '#14110F', noseWidth: 5, lipColor: '#8A6A74'},
    hair: {style: 'curly', color: '#14110F'},
    outfit: {top: {type: 'tee', color: '#A23A32', sleeve: 'short'}, bottom: {type: 'shorts', color: '#8C7F5E'},
      shoes: {type: 'sneakers', color: '#2E3036', accent: '#D8D6CE', sole: '#D8D6CE', socks: '#D6D3CA'}},
    wounds: [
      {part: 'rleg', face: 'back', cx: 3.5, cy: 14, rx: 2.6, ry: 2.2, kind: 'bite', seed: 85, reach: 2.6},
      {part: 'rleg', face: 'right', cx: 1.5, cy: 14, rx: 1.4, ry: 2, kind: 'bite', seed: 86, reach: 2},
    ],
    drips: [
      {part: 'head', face: 'front', x: 6, y0: 13, len: 2, fresh: true},
      {part: 'rleg', face: 'back', x: 3, y0: 16, len: 2, w: 2, fresh: true},
    ],
    stains: [{part: 'body', face: 'front', x: 6, w: 4, y0: 0, len: 3, fresh: true}]},
  f7: {name: '7 开衫', slim: true, seed: 407, skin: '#EDC6A6', brow: '#8E8A84',
    face: {browShape: 'thin', age: 2, glasses: '#5A5048', lipColor: '#9A5A66', lips: 'thin', earring: '#D8D4C8'},
    hair: {style: 'shortbob', color: '#A8A49C'},
    outfit: {top: {type: 'cardigan', color: '#8A7A9E', inner: '#D8D4CA', sleeve: 'long'}, bottom: {type: 'slacks', color: '#2E3548'},
      shoes: {type: 'shoes', color: '#4A3426', sole: '#2A1E16'}},
    wounds: [
      {part: 'head', face: 'left', cx: 12, cy: 12, rx: 2.2, ry: 2, kind: 'bite', seed: 87, reach: 2.4},
      {part: 'head', face: 'front', cx: 15.3, cy: 12.5, rx: 1, ry: 1.6, kind: 'bite', seed: 88, reach: 1.6},
    ],
    drips: [
      {part: 'head', face: 'front', x: 6, y0: 13, len: 1},
      {part: 'head', face: 'left', x: 12, y0: 14, len: 1, fresh: true},
    ],
    stains: [
      {part: 'body', face: 'front', x: 12, w: 3, y0: 0, len: 6},
      {part: 'body', face: 'left', x: 9, w: 3, y0: 0, len: 4},
    ]},
};

// ---------------------------------------------------------------- painting

function paintSkin(V, C) {
  const px = new Uint8Array(SIZE * SIZE * 4), parts = PARTS(V.slim);
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
              if (name === 'head') c = C.head ? C.head(V, face, x, y, z) : null;
              else if (name === 'body') c = C.body(V, face, x, y, z);
              else if (name.endsWith('arm')) c = C.arm(V, name, p, face, x, y, z);
              else c = C.leg(V, name, p, face, x, y, z);
            } else if (name === 'head') c = headBase(V, face, x, y, z);
            else if (name === 'body') c = bodyBase(V, face, x, y, z);
            else if (name.endsWith('arm')) c = armBase(V, name, p, face, x, y, z, W);
            else c = legBase(V, name, p, face, x, y, z);
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

fs.mkdirSync(OUT, {recursive: true});
for (const id of Object.keys(VERSIONS)) {
  fs.writeFileSync(path.join(OUT, `z0_${id}.png`), png(SIZE, SIZE, paintSkin(VERSIONS[id], CLOTHES[id])));
  console.log(`z0_${id}.png  ${VERSIONS[id].name}${VERSIONS[id].slim ? '  (slim arms)' : ''}`);
}
for (const [id, spec] of Object.entries(FRESH)) {
  const V = freshVersion(spec);
  fs.writeFileSync(path.join(OUT, `z0_${id}.png`), png(SIZE, SIZE, paintSkin(V, FRESH_CLOTHES)));
  console.log(`z0_${id}.png  ${V.name}${V.slim ? '  (slim arms)' : ''}`);
}
