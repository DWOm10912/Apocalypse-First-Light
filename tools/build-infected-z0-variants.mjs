// Z0 r4 freshly turned variants, derived from the purchased zombie texture.
// User 2026-10-26: "你这些做的太呆了，我们现在的僵尸贴图就很不错，按照这种风格去做，这个是我买的贴图，别人做了好久".
// The procedural painter (build-infected-z0-skins.mjs) is dropped. Each variant here starts from
// assets/minecraft/textures/entity/zombie/zombie.png and only changes it in ways that keep its brushwork:
//   - clothes and hair are recoloured per pixel, keeping each pixel's offset from the region's mean, so folds, stains
//     and strands stay where the artist painted them; blood, torn-skin and dark fold marks are left alone;
//   - shorter hair moves the painted hair up the head and fills the uncovered skin from the texture's own forearm skin;
//   - long sleeves copy the shirt's own back panel onto the forearms;
//   - mirrored shirt panels move the blood streaks;
//   - faces get their own eyes, mouths and wounds, painted with the original's blood, torn-skin and skin colours and
//     the same per-pixel jitter (user, same day: "面部也可以换的，不是说一定要一模一样，风格基本上一样就可以了，你自由创作").
// The layout stays vanilla 64 doubled to 128, clothes on the base layer and hair on the hat layer, like the original,
// so every variant also works on the vanilla ZombieModel.
// Usage: node tools/build-infected-z0-variants.mjs  -> design/creatures/infected_z0/z0_s2.png ... z0_s7.png
import fs from 'node:fs';
import zlib from 'node:zlib';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const REPO = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SRC = path.join(REPO, 'src/main/resources/assets/minecraft/textures/entity/zombie/zombie.png');
const OUT = path.join(REPO, 'design/creatures/infected_z0');

// ---------------------------------------------------------------- PNG

function readPng(file) {
  const d = fs.readFileSync(file);
  let p = 8, w, h, ct, idat = [];
  while (p < d.length) {
    const len = d.readUInt32BE(p), type = d.toString('ascii', p + 4, p + 8), data = d.subarray(p + 8, p + 8 + len);
    if (type === 'IHDR') { w = data.readUInt32BE(0); h = data.readUInt32BE(4); ct = data[9]; if (data[8] !== 8) throw new Error('bit depth'); }
    if (type === 'IDAT') idat.push(data);
    p += 12 + len;
  }
  const bpp = ct === 6 ? 4 : ct === 2 ? 3 : ct === 4 ? 2 : 1;
  const raw = zlib.inflateSync(Buffer.concat(idat)), stride = w * bpp, out = Buffer.alloc(stride * h);
  for (let y = 0; y < h; y++) {
    const f = raw[y * (stride + 1)], src = raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1));
    for (let x = 0; x < stride; x++) {
      const a = x >= bpp ? out[y * stride + x - bpp] : 0, b = y ? out[(y - 1) * stride + x] : 0,
        c = x >= bpp && y ? out[(y - 1) * stride + x - bpp] : 0;
      let v = src[x];
      if (f === 1) v += a; else if (f === 2) v += b; else if (f === 3) v += (a + b) >> 1;
      else if (f === 4) { const pp = a + b - c, pa = Math.abs(pp - a), pb = Math.abs(pp - b), pc = Math.abs(pp - c); v += pa <= pb && pa <= pc ? a : pb <= pc ? b : c; }
      out[y * stride + x] = v & 255;
    }
  }
  const px = new Float64Array(w * h * 4);
  for (let i = 0; i < w * h; i++) {
    if (bpp === 4) for (let k = 0; k < 4; k++) px[i * 4 + k] = out[i * 4 + k];
    else if (bpp === 3) { for (let k = 0; k < 3; k++) px[i * 4 + k] = out[i * 3 + k]; px[i * 4 + 3] = 255; }
    else throw new Error('grey PNG');
  }
  return {w, h, px};
}

const crcT = Array.from({length: 256}, (_, n) => { let c = n; for (let j = 0; j < 8; j++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc = b => { let c = ~0; for (const x of b) c = crcT[(c ^ x) & 255] ^ (c >>> 8); return (~c) >>> 0; };
function writePng(file, w, h, px) {
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) for (let x = 0; x < w * 4; x++) raw[y * (w * 4 + 1) + 1 + x] = Math.max(0, Math.min(255, Math.round(px[y * w * 4 + x])));
  const ch = (t, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length); const td = Buffer.concat([Buffer.from(t), d]), c = Buffer.alloc(4); c.writeUInt32BE(crc(td)); return Buffer.concat([l, td, c]); };
  const ih = Buffer.alloc(13); ih.writeUInt32BE(w, 0); ih.writeUInt32BE(h, 4); ih[8] = 8; ih[9] = 6;
  fs.writeFileSync(file, Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), ch('IHDR', ih), ch('IDAT', zlib.deflateSync(raw)), ch('IEND', Buffer.alloc(0))]));
}

// ---------------------------------------------------------------- colour

function toHsl(r, g, b) {
  r /= 255; g /= 255; b /= 255;
  const M = Math.max(r, g, b), m = Math.min(r, g, b), l = (M + m) / 2, d = M - m;
  let h = 0, s = 0;
  if (d) { s = d / (1 - Math.abs(2 * l - 1)); h = M === r ? ((g - b) / d) % 6 : M === g ? (b - r) / d + 2 : (r - g) / d + 4; h *= 60; if (h < 0) h += 360; }
  return [h, s, l];
}
function fromHsl(h, s, l) {
  h = ((h % 360) + 360) % 360; s = clamp(s, 0, 1); l = clamp(l, 0, 1);
  const c = (1 - Math.abs(2 * l - 1)) * s, x = c * (1 - Math.abs((h / 60) % 2 - 1)), m = l - c / 2;
  const [r, g, b] = h < 60 ? [c, x, 0] : h < 120 ? [x, c, 0] : h < 180 ? [0, c, x] : h < 240 ? [0, x, c] : h < 300 ? [x, 0, c] : [c, 0, x];
  return [(r + m) * 255, (g + m) * 255, (b + m) * 255];
}
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const hex = s => [1, 3, 5].map(i => parseInt(s.slice(i, i + 2), 16));
const hslOf = s => toHsl(...hex(s));

function hash(x, y, s) {
  let h = Math.imul(x, 0x27d4eb2d) ^ Math.imul(y, 0x165667b1) ^ Math.imul(s, 0x9e3779b9);
  h ^= h >>> 15; h = Math.imul(h, 0x85ebca6b); h ^= h >>> 13; h = Math.imul(h, 0xc2b2ae35); h ^= h >>> 16;
  return (h >>> 0) / 4294967296;
}

// ---------------------------------------------------------------- layout (vanilla UV x 2)

const K = 2;
// Faces of a vanilla box at (u, v) with size w x h x d, in 128 px: {name: [x0, y0, x1, y1]}.
function box(u, v, w, h, d) {
  [u, v, w, h, d] = [u, v, w, h, d].map(n => n * K);
  return {
    top: [u + d, v, u + d + w, v + d], bottom: [u + d + w, v, u + d + 2 * w, v + d],
    right: [u, v + d, u + d, v + d + h], front: [u + d, v + d, u + d + w, v + d + h],
    left: [u + d + w, v + d, u + 2 * d + w, v + d + h], back: [u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h],
  };
}
const HEAD = box(0, 0, 8, 8, 8), HAT = box(32, 0, 8, 8, 8), BODY = box(16, 16, 8, 12, 4);
const RARM = box(40, 16, 4, 12, 4), LARM = box(32, 48, 4, 12, 4), RLEG = box(0, 16, 4, 12, 4), LLEG = box(16, 48, 4, 12, 4);
const SIDES = ['right', 'front', 'left', 'back'];
const ARM_SLEEVE = 11;    // the original's short sleeve covers the top 11 rows of each arm side (of 24)
const ARM_CUFF = 20;      // long sleeves end here; the last 4 rows are the hand
const LEG_SHOE = 21;      // the original's shoes start at row 21 of 24
// The eye whites on the head front (left eye, right eye), kept out of every recolour.
const EYES = [[19, 24, 22, 26], [26, 24, 29, 26]];

// ---------------------------------------------------------------- pixel classes of the original

const SRC_IMG = readPng(SRC);
const W = SRC_IMG.w;
if (W !== 128) throw new Error('expected a 128 px zombie texture');
const at = (x, y) => (y * W + x) * 4;
const inRect = (r, x, y) => x >= r[0] && x < r[2] && y >= r[1] && y < r[3];
const inEyes = (x, y) => EYES.some(e => inRect([HEAD.front[0] + e[0] - 16, HEAD.front[1] + e[1] - 16, HEAD.front[0] + e[2] - 16, HEAD.front[1] + e[3] - 16], x, y));

// Where the shirt and the trousers are painted: the whole torso (its bottom two rows are the trousers' waistband), the
// short sleeves and the legs above the shoes.
const sideRows = (part, r0, r1) => SIDES.map(s => [part[s][0], part[s][1] + r0, part[s][2], part[s][1] + r1]);
const SHIRT_AREA = [...Object.values(BODY).filter(r => r !== BODY.bottom).map(r => r[3] === BODY.front[3] ? [r[0], r[1], r[2], r[3] - 2] : r),
  ...sideRows(RARM, 0, 11), ...sideRows(LARM, 0, 11), RARM.top, LARM.top];
const PANTS_AREA = [...sideRows(BODY, 22, 24), ...sideRows(RLEG, 0, 21), ...sideRows(LLEG, 0, 21), RLEG.top, LLEG.top];

// hair  : near-black neutral strands on the head and hat
// shirt, pants : everything in the clothes areas that is not blood or skin, including the dark smudges and folds
// skin  : grey skin, also where it shows through tears
// keep  : blood, torn skin, shoes, eyes: never recoloured
const isBlood = (h, s) => (h < 30 || h > 330) && s > 0.2;
function clothOrNot(h, s, l, cloth) {
  if (isBlood(h, s)) return 'keep';
  if (s < 0.2 && l >= 0.2) return 'skin';
  return cloth;
}
function classify(px, x, y) {
  const i = at(x, y);
  if (px[i + 3] < 128) return 'none';
  if (inEyes(x, y)) return 'keep';
  const [h, s, l] = toHsl(px[i], px[i + 1], px[i + 2]);
  const frx = x - HEAD.front[0], fry = y - HEAD.front[1];
  const inFace = inRect(HEAD.front, x, y) && fry >= 6 && frx >= 3 && frx <= 12;
  if (y < 32 && l < 0.2 && s < 0.25 && !inFace) return 'hair';
  if (SHIRT_AREA.some(r => inRect(r, x, y))) return clothOrNot(h, s, l, 'shirt');
  if (PANTS_AREA.some(r => inRect(r, x, y))) return clothOrNot(h, s, l, 'pants');
  if (isBlood(h, s)) return 'keep';
  if (s < 0.2 && l >= 0.2) return 'skin';
  return 'keep';
}

// ---------------------------------------------------------------- the image being edited

class Skin {
  constructor() { this.px = Float64Array.from(SRC_IMG.px); this.cls = []; for (let y = 0; y < W; y++) for (let x = 0; x < W; x++) this.cls.push(classify(this.px, x, y)); }
  get(x, y) { const i = at(x, y); return [this.px[i], this.px[i + 1], this.px[i + 2], this.px[i + 3]]; }
  set(x, y, c, cls) { const i = at(x, y); for (let k = 0; k < c.length; k++) this.px[i + k] = c[k]; if (cls) this.cls[y * W + x] = cls; }
  kind(x, y) { return this.cls[y * W + x]; }
  each(rect, fn) { for (let y = rect[1]; y < rect[3]; y++) for (let x = rect[0]; x < rect[2]; x++) fn(x, y); }
}

// Mean HSL of one class over the whole texture (hue as a circular mean).
function meanOf(S, cls) {
  let n = 0, hx = 0, hy = 0, s = 0, l = 0;
  for (let y = 0; y < W; y++) for (let x = 0; x < W; x++) {
    if (S.kind(x, y) !== cls) continue;
    const [r, g, b] = S.get(x, y), c = toHsl(r, g, b);
    hx += Math.cos(c[0] * Math.PI / 180) * c[1]; hy += Math.sin(c[0] * Math.PI / 180) * c[1]; s += c[1]; l += c[2]; n++;
  }
  return [((Math.atan2(hy, hx) * 180 / Math.PI) + 360) % 360, s / n, l / n];
}

// Recolour every pixel of class `cls` from its mean `from` to the target colour. Lightness keeps the pixel's offset
// from the mean, scaled by `gain` (default: the lightness ratio, at least 1), so the painted folds and strands stay;
// hue shifts by a fraction of the pixel's own hue offset; saturation follows the target.
function recolour(S, cls, target, {gain, rects, satFollow = 0.5, maxGain = 1.5} = {}) {
  const from = meanOf(S, cls), [th, ts, tl] = typeof target === 'string' ? hslOf(target) : target;
  const g = gain ?? clamp(tl / from[2], 1, maxGain);
  for (let y = 0; y < W; y++) for (let x = 0; x < W; x++) {
    if (S.kind(x, y) !== cls || (rects && !rects.some(r => inRect(r, x, y)))) continue;
    const [r, gg, b, a] = S.get(x, y), [h, s, l] = toHsl(r, gg, b);
    let dh = h - from[0]; if (dh > 180) dh -= 360; if (dh < -180) dh += 360;
    const sr = from[1] > 0.08 ? clamp(s / from[1], 0.15, 1.6) : 1;
    S.set(x, y, [...fromHsl(th + dh * 0.3, ts * (1 - satFollow + satFollow * sr), clamp(tl + (l - from[2]) * g, 0.01, 0.92)), a]);
  }
}

// Multiply lightness of a rect's pixels (only classes in `only`), for shadows under a new hairline or on cuffs.
function shade(S, rect, k, only = ['skin', 'shirt', 'pants', 'hair']) {
  S.each(rect, (x, y) => {
    if (!only.includes(S.kind(x, y))) return;
    const [r, g, b, a] = S.get(x, y), [h, s, l] = toHsl(r, g, b);
    S.set(x, y, [...fromHsl(h, s, l * k), a]);
  });
}

// ---------------------------------------------------------------- source patches from the original

// The forearm skin of both arms, one strip 64 wide x 13 tall, used to fill skin where hair or sleeves are removed.
const SKIN_SRC = [];
for (let y = 0; y < 13; y++) {
  const row = [];
  for (const [arm, side] of [[RARM, 'right'], [RARM, 'front'], [RARM, 'left'], [RARM, 'back'], [LARM, 'right'], [LARM, 'front'], [LARM, 'left'], [LARM, 'back']]) {
    const f = arm[side];
    for (let x = f[0]; x < f[2]; x++) {
      const yy = f[1] + ARM_SLEEVE + y;
      const c = SRC_IMG.px.slice(at(x, yy), at(x, yy) + 4);
      // skip torn spots and blood: take the pixel above instead
      const [h, s] = toHsl(c[0], c[1], c[2]);
      row.push((h < 30 || h > 330) && s > 0.2 ? SRC_IMG.px.slice(at(x, yy - 1), at(x, yy - 1) + 4) : c);
    }
  }
  SKIN_SRC.push(row);
}
const skinAt = (x, y, ox, oy) => SKIN_SRC[((y + oy) % 13 + 13) % 13][((x + ox) % 64 + 64) % 64];

// The shirt's back panel above its darker hem, 16 wide x 19 tall, used to paint long sleeves.
const SHIRT_SRC = [];
for (let y = BODY.back[1]; y < BODY.back[3] - 5; y++) {
  const row = [];
  for (let x = BODY.back[0]; x < BODY.back[2]; x++) row.push(SRC_IMG.px.slice(at(x, y), at(x, y) + 4));
  SHIRT_SRC.push(row);
}

// ---------------------------------------------------------------- edits

// Shorter hair: on every head side the painted hair moves up `lift` rows (its ragged lower edge comes with it) and the
// uncovered pixels become skin. The hat layer keeps `hatRows` rows of strands below the top (0 = none).
function cutHair(S, lift, hatRows, seed) {
  const orig = new Skin();
  orig.px = Float64Array.from(S.px); orig.cls = S.cls.slice();
  for (const side of SIDES) {
    const f = HEAD[side], ox = Math.floor(hash(seed, side.length, 3) * 64), oy = Math.floor(hash(seed, side.length, 4) * 13);
    for (let y = f[1]; y < f[3]; y++) for (let x = f[0]; x < f[2]; x++) {
      if (orig.kind(x, y) !== 'hair') continue;
      // the lower edge gets a little per-column jitter so a straight cut does not show on the back
      const sy = y + lift + (lift && hash(x, seed, 7) < 0.3 ? 1 : 0);
      if (sy < f[3] && orig.kind(x, sy) === 'hair') S.set(x, y, orig.get(x, sy), 'hair');
      else {
        const c = skinAt(x - f[0], y - f[1], ox, oy);
        S.set(x, y, [c[0], c[1], c[2], 255], 'skin');
      }
    }
    // shadow on the skin just under the new hairline
    for (let x = f[0]; x < f[2]; x++) for (let y = f[1] + 1; y < f[3]; y++)
      if (S.kind(x, y) === 'skin' && S.kind(x, y - 1) === 'hair' && orig.kind(x, y) === 'hair') { shade(S, [x, y, x + 1, y + 1], 0.8, ['skin']); break; }
  }
  // hat: strands below the top are cut to hatRows, and their lower ends lift with the base hair; with none left the
  // top goes too, or it floats over the cut
  if (!hatRows) S.each(HAT.top, (x, y) => { if (S.kind(x, y) !== 'none') S.set(x, y, [0, 0, 0, 0], 'none'); });
  for (const side of SIDES) {
    const f = HAT[side];
    for (let y = f[1]; y < f[3]; y++) for (let x = f[0]; x < f[2]; x++) {
      if (S.kind(x, y) === 'none') continue;
      const ry = y - f[1];
      if (ry >= hatRows || hash(x, y, seed) < ry / Math.max(1, hatRows) * 0.5) S.set(x, y, [0, 0, 0, 0], 'none');
    }
  }
}

// Bald crown: the head top becomes skin except a ring `ring` px wide on the back and sides (balding).
function baldTop(S, ring, seed) {
  const f = HEAD.top, h = HAT.top;
  for (let y = f[1]; y < f[3]; y++) for (let x = f[0]; x < f[2]; x++) {
    const ry = y - f[1], rx = x - f[0];   // ry 0 = back edge, 15 = front edge
    const edge = Math.min(rx, 15 - rx, ry);
    if (edge < ring && !(ry > 11)) continue;
    const c = skinAt(rx, ry, 23 + seed, 2);
    S.set(x, y, [c[0], c[1], c[2], 255], 'skin');
  }
  // a little shine on the crown
  shade(S, [f[0] + 5, f[1] + 6, f[0] + 11, f[1] + 11], 1.12, ['skin']);
  S.each(h, (x, y) => { if (S.kind(x, y) !== 'none') S.set(x, y, [0, 0, 0, 0], 'none'); });
  // the sides keep hair only around the ears and the back; the front hairline is gone
  for (const side of SIDES) {
    const s = HEAD[side], bare = side === 'front' ? 7 : 3;
    for (let y = s[1]; y < s[1] + bare; y++) for (let x = s[0]; x < s[2]; x++) {
      if (S.kind(x, y) !== 'hair') continue;
      const c = skinAt(x - s[0], y - s[1], 41 + seed, 5);
      S.set(x, y, [c[0], c[1], c[2], 255], 'skin');
    }
  }
}

// V-neck: the round neck opening of the original is narrowed to a V, the rest filled with the shirt's own back panel.
function vNeck(S) {
  const f = BODY.front;
  for (let ry = 0; ry < 8; ry++) for (let rx = 0; rx < 16; rx++) {
    const x = f[0] + rx, y = f[1] + ry;
    if (S.kind(x, y) !== 'skin' || Math.abs(rx - 7.5) <= 4 - ry * 0.55) continue;
    const c = SHIRT_SRC[ry + 2][rx];
    S.set(x, y, [c[0], c[1], c[2], 255], clothOrNot(...toHsl(c[0], c[1], c[2]), 'shirt'));
  }
}

// Hoodie: a bunched hood on the upper back and around the neck, drawstrings and a front pocket, all as light and shade
// on the shirt's own pixels (run after the recolour).
function hoodie(S, string) {
  const b = BODY.back, f = BODY.front, tp = BODY.top;
  for (let ry = 0; ry < 9; ry++) for (let rx = 0; rx < 16; rx++) {
    const x = b[0] + rx, y = b[1] + ry, d = Math.abs(rx - 7.5);
    if (S.kind(x, y) !== 'shirt' || d > 7.5 - ry * 0.3) continue;
    const edge = ry === 8 - Math.floor(d / 3) || d > 6.6 - ry * 0.3;
    shade(S, [x, y, x + 1, y + 1], edge ? 0.62 : ry < 2 ? 1.3 : 1.12, ['shirt']);
  }
  // hood rim round the neck on the top face
  S.each(tp, (x, y) => {
    const rx = x - tp[0], ry = y - tp[1];
    if (rx < 3 || rx > 12 || ry > 5) return;
    if (S.kind(x, y) === 'skin' && (rx < 5 || rx > 10 || ry < 2)) {
      const c = SHIRT_SRC[ry + 4][rx];
      S.set(x, y, [c[0], c[1], c[2], 255], 'shirt');
    }
  });
  const [sh, ss, sl] = hslOf(string);
  for (const rx of [5, 10]) for (let ry = 1; ry < 9; ry++) {
    const x = f[0] + rx, y = f[1] + ry;
    if (S.kind(x, y) === 'keep') continue;
    S.set(x, y, [...fromHsl(sh, ss, sl * (0.85 + 0.3 * hash(x, y, 31)) * (ry === 8 ? 0.7 : 1)), 255], 'keep');
  }
  // pocket: a slightly lighter patch with darker openings and a dark seam
  for (let ry = 14; ry < 21; ry++) for (let rx = 2; rx < 14; rx++) {
    const x = f[0] + rx, y = f[1] + ry, open = (rx === 2 || rx === 13) || (ry === 14 && (rx < 4 || rx > 11));
    shade(S, [x, y, x + 1, y + 1], ry === 14 ? 0.7 : open ? 0.75 : 1.1, ['shirt']);
  }
}

// ---------------------------------------------------------------- faces

// Colours sampled from the original: its fresh blood, dried blood, torn skin and the dark inside of the mouth.
const BLOOD = [0x4b, 0x30, 0x2b], RED = [0x5e, 0x1e, 0x1a], TORN = [0x6e, 0x4c, 0x43], DARK = [0x12, 0x0b, 0x0b];
// Blend a pixel toward a colour with strength k, the colour jittered per pixel like the original's brushwork.
function dab(S, x, y, colour, k, seed = 9) {
  const [r, g, b, a] = S.get(x, y), j = 0.85 + 0.3 * hash(x, y, seed);
  S.set(x, y, [r + (colour[0] * j - r) * k, g + (colour[1] * j - g) * k, b + (colour[2] * j - b) * k, a], 'keep');
}
const FX = rx => HEAD.front[0] + rx, FY = ry => HEAD.front[1] + ry;
const EYE_X = [[3, 4, 5], [10, 11, 12]];   // eye whites, rows 8 and 9

function face(S, F, seed) {
  if (F.mirror) mirror(S, HEAD.front);
  const lidColour = x => { const [r, g, b] = S.get(x, FY(11)); return [r * 0.72, g * 0.72, b * 0.72]; };
  F.eyes?.forEach((kind, e) => {
    const xs = EYE_X[e];
    if (kind === 'iris') {   // clouded grey iris, a darker centre
      dab(S, FX(xs[1]), FY(8), [0x86, 0x8a, 0x82], 0.75, seed);
      dab(S, FX(xs[1]), FY(9), [0x5c, 0x60, 0x5a], 0.7, seed);
      dab(S, FX(xs[e ? 2 : 0]), FY(8), [0x9a, 0x9c, 0x94], 0.4, seed);
    } else if (kind === 'half') {   // upper lid down over the top row
      for (const rx of xs) dab(S, FX(rx), FY(8), lidColour(FX(rx)), 0.9, seed);
      dab(S, FX(xs[1]), FY(9), [0x86, 0x8a, 0x82], 0.5, seed);
    } else if (kind === 'narrow') {   // a slit: lid over the top row, lower lid creeping in
      for (const rx of xs) dab(S, FX(rx), FY(8), lidColour(FX(rx)), 0.95, seed);
      dab(S, FX(xs[e ? 2 : 0]), FY(9), lidColour(FX(xs[0])), 0.6, seed);
    } else if (kind === 'red') {   // bloodshot: red rims and a pink cast on the white
      for (const rx of [xs[0] - 1, ...xs, xs[2] + 1]) { dab(S, FX(rx), FY(7), RED, 0.35, seed); dab(S, FX(rx), FY(10), RED, 0.5, seed); }
      for (const rx of xs) for (const ry of [8, 9]) dab(S, FX(rx), FY(ry), [0xb0, 0x86, 0x80], 0.35, seed);
    }
  });
  if (F.mouth === 'open') {
    // A hanging jaw, not a grin (user: "这些嘴看起来这么像在笑"): a narrow slit under the upper lip that opens wider
    // further down, the corners pulled down, lopsided toward `side`, and one stub of tooth instead of a row.
    const sd = F.mouthSide || 1, m = rx => sd > 0 ? rx : 15 - rx;
    for (const [rx, ry, k] of [[7, 12, 0.55], [8, 12, 0.7],
      [6, 13, 0.7], [7, 13, 0.92], [8, 13, 0.92], [9, 13, 0.8],
      [6, 14, 0.85], [7, 14, 0.95], [8, 14, 0.95], [9, 14, 0.9], [10, 14, 0.5],
      [7, 15, 0.7], [8, 15, 0.8], [9, 15, 0.55]]) dab(S, FX(m(rx)), FY(ry), DARK, k, seed);
    dab(S, FX(m(9)), FY(13), [0x7e, 0x78, 0x66], 0.55, seed);   // a stub of lower tooth on one side
    for (const [rx, ry, k] of [[6, 12, 0.45], [9, 12, 0.5], [5, 13, 0.4], [10, 13, 0.6], [5, 14, 0.55], [11, 15, 0.5], [10, 15, 0.65], [6, 15, 0.5]])
      dab(S, FX(m(rx)), FY(ry), BLOOD, k, seed);
  }
  if (F.drool) {   // blood run from the mouth over the chin and under it
    for (const [rx, ry0] of F.drool) {
      for (let ry = ry0; ry < 16; ry++) dab(S, FX(rx), FY(ry), ry === ry0 ? RED : BLOOD, 0.75 - (ry - ry0) * 0.04, seed);
      const bt = HEAD.bottom;   // the underside of the head
      for (let ry = 0; ry < 4; ry++) dab(S, bt[0] + rx, bt[1] + ry, BLOOD, 0.6 - ry * 0.12, seed);
    }
  }
  for (const [x0, y0, x1, y1] of F.gash || []) {   // a cut: dark core, torn edges
    const n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
    for (let i = 0; i <= n; i++) {
      const x = Math.round(x0 + (x1 - x0) * i / n), y = Math.round(y0 + (y1 - y0) * i / n);
      dab(S, FX(x), FY(y), RED, 0.85, seed);
      if (y + 1 < 16) dab(S, FX(x), FY(y + 1), TORN, 0.55, seed);
      if (y > 0 && i % 2) dab(S, FX(x), FY(y - 1), TORN, 0.35, seed);
    }
  }
  if (F.bite) {   // a bite: a ragged ring of torn skin round a dark centre, with a run below
    const [side, cx, cy] = F.bite, f = HEAD[side];
    for (let dy = -2; dy <= 2; dy++) for (let dx = -2; dx <= 2; dx++) {
      const d = Math.hypot(dx, dy), x = f[0] + cx + dx, y = f[1] + cy + dy;
      if (d > 2.3 || S.kind(x, y) === 'none') continue;
      dab(S, x, y, d < 1 ? DARK : d < 1.8 ? RED : TORN, d < 1 ? 0.8 : 0.65, seed);
    }
    for (let y = f[1] + cy + 3; y < f[3]; y++) dab(S, f[0] + cx, y, BLOOD, 0.6, seed);
  }
}

// Long sleeves: the forearm skin of each arm side down to ARM_CUFF is painted with the shirt's own back panel, then
// recoloured with the rest of the shirt. The cuff row is darker.
function longSleeves(S, seed) {
  for (const arm of [RARM, LARM]) SIDES.forEach((side, si) => {
    const f = arm[side], ox = Math.floor(hash(seed, si, arm === RARM ? 1 : 2) * 9);
    for (let y = f[1] + ARM_SLEEVE; y < f[1] + ARM_CUFF; y++) for (let x = f[0]; x < f[2]; x++) {
      const c = SHIRT_SRC[(y - f[1] - ARM_SLEEVE + 3 + si * 4) % SHIRT_SRC.length][(x - f[0] + ox) % 16];
      S.set(x, y, [c[0], c[1], c[2], 255], clothOrNot(...toHsl(c[0], c[1], c[2]), 'shirt'));
    }
    shade(S, [f[0], f[1] + ARM_CUFF - 1, f[2], f[1] + ARM_CUFF], 0.85, ['shirt']);
    shade(S, [f[0], f[1] + ARM_CUFF, f[2], f[1] + ARM_CUFF + 1], 0.9, ['skin']);
  });
}

// Mirror a face left to right (moves the blood streaks without repainting them).
function mirror(S, rect) {
  const copy = [];
  S.each(rect, (x, y) => copy.push([S.get(x, y), S.kind(x, y)]));
  let i = 0;
  const w = rect[2] - rect[0];
  S.each(rect, (x, y) => { const j = Math.floor(i / w) * w + (w - 1 - (i % w)); S.set(x, y, copy[j][0], copy[j][1]); i++; });
}

// Swap two faces of the same size.
function swap(S, a, b) {
  const ca = [], cb = [];
  S.each(a, (x, y) => ca.push([S.get(x, y), S.kind(x, y)]));
  S.each(b, (x, y) => cb.push([S.get(x, y), S.kind(x, y)]));
  let i = 0; S.each(a, (x, y) => { S.set(x, y, cb[i][0], cb[i][1]); i++; });
  i = 0; S.each(b, (x, y) => { S.set(x, y, ca[i][0], ca[i][1]); i++; });
}

// Flannel: darker bands every 8 px across the recoloured shirt, the original's own light and dark showing through.
function plaid(S, dark, seed) {
  const [dh, ds] = hslOf(dark);
  for (let y = 32; y < W; y++) for (let x = 0; x < W; x++) {
    if (S.kind(x, y) !== 'shirt') continue;
    const bx = ((x + seed) % 8 + 8) % 8, by = ((y + seed * 3) % 8 + 8) % 8;
    const v = (bx < 2 ? 1 : 0) + (by < 2 ? 1 : 0), thin = bx === 5 || by === 5;
    if (!v && !thin) continue;
    const [r, g, b, a] = S.get(x, y), [h, s, l] = toHsl(r, g, b);
    if (v) S.set(x, y, [...fromHsl(dh, ds, l * (v === 2 ? 0.45 : 0.62)), a]);
    else S.set(x, y, [...fromHsl(h, s * 0.8, Math.min(0.9, l * 1.18)), a]);
  }
}

// Stubble or a short beard: the hair colour laid over the jaw and chin at partial strength, pixel by pixel.
function beard(S, colour, strength, seed) {
  const f = HEAD.front, [bh, bs, bl] = hslOf(colour);
  for (let y = f[1] + 11; y < f[3]; y++) for (let x = f[0]; x < f[2]; x++) {
    const rx = x - f[0], ry = y - f[1];
    if (S.kind(x, y) !== 'skin') continue;
    if (ry < 13 && rx > 3 && rx < 12) continue;             // cheeks above the jaw stay bare
    if (ry === 14 && rx >= 6 && rx <= 9) continue;         // the mouth
    const k = strength * (0.6 + 0.8 * hash(x, y, seed));
    const [r, g, b, a] = S.get(x, y), [h, s, l] = toHsl(r, g, b);
    S.set(x, y, [...fromHsl(bh, bs * k + s * (1 - k), l * (1 - k) + (bl + (l - 0.4) * 0.5) * k), a]);
  }
  for (const side of ['right', 'left']) {
    const s = HEAD[side];
    for (let y = s[1] + 12; y < s[3]; y++) for (let x = s[0]; x < s[2]; x++) {
      const toward = side === 'right' ? x - s[0] : s[2] - 1 - x;   // 15 = next to the face
      if (toward < 10 || S.kind(x, y) !== 'skin') continue;
      const k = strength * 0.8 * (0.6 + 0.8 * hash(x, y, seed + 1));
      const [r, g, b, a] = S.get(x, y), [h, sat, l] = toHsl(r, g, b);
      S.set(x, y, [...fromHsl(bh, bs * k + sat * (1 - k), l * (1 - k) + (bl + (l - 0.4) * 0.5) * k), a]);
    }
  }
}

// ---------------------------------------------------------------- variants

// Lightness targets are on the dark side, like the original: its shirt sits at 0.18 and its trousers at 0.10.
// Skin targets are ashen, already grey with a little green, like the original's grey-white skin.
const VARIANTS = {
  s2: {   // short dark-brown hair, dark red T-shirt, jeans
    hair: {cut: [4, 2], colour: [24, 0.28, 0.13], gain: 1.4},
    shirt: [358, 0.42, 0.2], pants: [214, 0.34, 0.2], skin: [70, 0.07, 0.4],
    blood: [['mirror', BODY.front]], stubble: ['#2A2018', 0.35],
    face: {eyes: ['iris', 'iris'], mouth: 'open'},
  },
  s3: {   // long dirty-blonde hair, heather grey T-shirt, black trousers
    hair: {colour: [40, 0.3, 0.36], gain: 2.2},
    shirt: [200, 0.06, 0.3], pants: [230, 0.06, 0.07], skin: [60, 0.06, 0.45],
    blood: [['swap', BODY.front, BODY.back]],
    face: {mirror: true, eyes: ['white', 'half'], drool: [[7, 13], [9, 14]]},
  },
  s4: {   // buzz cut, red flannel with long sleeves, brown work trousers, darker skin
    hair: {cut: [6, 0], colour: [20, 0.15, 0.09], gain: 1.2},
    shirt: [2, 0.48, 0.24], plaid: ['#1A1414', 3], sleeves: true, pants: [30, 0.32, 0.16], skin: [32, 0.12, 0.27],
    stubble: ['#141210', 0.45],
    face: {eyes: ['red', 'red'], gash: [[9, 10, 13, 12]]},
  },
  s5: {   // balding grey hair, olive sweater with long sleeves, khaki trousers
    hair: {cut: [5, 0], bald: 4, colour: [40, 0.04, 0.42], gain: 2},
    shirt: [78, 0.22, 0.2], sleeves: true, pants: [42, 0.22, 0.28], skin: [55, 0.07, 0.42],
    blood: [['mirror', BODY.back], ['mirror', BODY.front]], stubble: ['#8A8880', 0.3],
    face: {eyes: ['narrow', 'narrow'], mouth: 'open', mouthSide: -1, gash: [[2, 2, 7, 4]]},
  },
  s6: {   // scrubs: top and trousers in one light teal-green, dark brown hair to the shoulders, brown skin
    hair: {cut: [2, 3], colour: [20, 0.25, 0.1], gain: 1.3},
    shirt: [172, 0.3, 0.32], pants: [172, 0.3, 0.27], skin: [28, 0.14, 0.33], vNeck: true,
    blood: [['mirror', BODY.front]],
    face: {mirror: true, eyes: ['iris', 'white'], drool: [[8, 13]], bite: ['left', 6, 12]},
  },
  s7: {   // auburn hair, charcoal hoodie with long sleeves, dark jeans
    hair: {cut: [3, 2], colour: [14, 0.42, 0.17], gain: 1.6},
    shirt: [215, 0.08, 0.14], sleeves: true, pants: [218, 0.36, 0.14], skin: [65, 0.06, 0.43],
    blood: [['swap', BODY.front, BODY.back], ['mirror', BODY.front]], hood: '#8E8C86',
    face: {eyes: ['half', 'half'], mouth: 'open', bite: ['right', 9, 11]},
  },
  c3: {   // r6 concept base for the heavy road-crew infected: bald, stubble, grey T-shirt, jeans (vest is added in Blender)
    hair: {cut: [5, 0], bald: 5, colour: [30, 0.05, 0.2], gain: 1.6},
    shirt: [210, 0.04, 0.34], pants: [214, 0.3, 0.21], skin: [52, 0.08, 0.4],
    blood: [['mirror', BODY.front]], stubble: ['#22201C', 0.4],
    face: {eyes: ['narrow', 'half'], gash: [[2, 2, 7, 4]]},
  },
};

function build(key, V) {
  const S = new Skin();
  for (const [op, a, b] of V.blood || []) op === 'mirror' ? mirror(S, a) : swap(S, a, b);
  if (V.sleeves) longSleeves(S, key.charCodeAt(1));
  if (V.vNeck) vNeck(S);
  if (V.hair.cut) cutHair(S, V.hair.cut[0], V.hair.cut[1], key.charCodeAt(1));
  if (V.hair.bald) baldTop(S, V.hair.bald, key.charCodeAt(1));
  recolour(S, 'hair', V.hair.colour, {gain: V.hair.gain, satFollow: 0});
  recolour(S, 'shirt', V.shirt);
  if (V.plaid) plaid(S, V.plaid[0], V.plaid[1]);
  recolour(S, 'pants', V.pants);
  recolour(S, 'skin', V.skin, {satFollow: 0});
  if (V.stubble) beard(S, V.stubble[0], V.stubble[1], key.charCodeAt(1));
  if (V.hood) hoodie(S, V.hood);
  if (V.face) face(S, V.face, key.charCodeAt(1));
  writePng(path.join(OUT, `z0_${key}.png`), W, W, S.px);
  return key;
}

const only = process.argv[2];
console.log(Object.entries(VARIANTS).filter(([k]) => !only || k === only).map(([k, v]) => build(k, v)).join(' '));
