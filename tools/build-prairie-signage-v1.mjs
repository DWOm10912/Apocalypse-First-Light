// PRAIRIE signage V1 (docs/models/fuel_stop_a1_details_v1.md): the channel letters (A..Z) and the roadside price sign of the
// Federation's PRAIRIE fuel-and-market chain, for Fuel Stop A1. The user chose the brand on 2026-10-09 ("品牌用PRAIRIE"):
//   channel_letter  one aluminium channel letter per cell, 0.75 m cap height (tools/afl-sign-font.mjs, the AFL sign font):
//                   returns 120 mm deep in dark painted aluminium, a 7 mm red acrylic face 2.5 mm larger all round (the trim
//                   cap); the back 0.4 mm off the cell's back face (the wall). Off and lit faces.
//   price_sign      the roadside price sign, 4 x 3.8 m, both faces alike: a concrete footing pad over all four ground cells
//                   (it hides the feed cable's cell; the standard power port in the master cell's underside), a ground-face
//                   block base with a stone cap, a charcoal aluminium cabinet with the PRAIRIE light box and two price rows
//                   (GASOLINE, DIESEL: the two fuels A1 sells) of red seven-segment LED digits. Numbers only, no unit (user).
//                   Looks: the body; the light box off or lit; the digits shown only when powered (user: "价格数字默认不
//                   显示，但是如果有电了就固定显示一个").
// Pure Mesh + LabPBR atlases (240 texels a block), exported as Forge OBJ block models (chunk-baked); every part a closed,
// outward-wound surface (checked); item displays fitted (tools/item-held-display.mjs).
//   node tools/build-prairie-signage-v1.mjs                -> writes sources, OBJ / MTL / block + item models, blockstates, atlases
//   node tools/build-prairie-signage-v1.mjs --check        -> verifies every output is up to date
//   node tools/build-prairie-signage-v1.mjs --preview DIR  -> writes only OBJ + maps into DIR
// Millimetres, origin at the (master) cell's bottom centre, x east, y up, z south; drawn facing north (front toward -z).
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {Part, extrude, unwrap, paint, png, readPng, zFightLevels, area2, add, dot, cross, norm, newell, sub} from './cube-slab-mesh-lib.mjs';
import {heldDisplay} from './item-held-display.mjs';
import {LETTERS, WIDTH, CAP, outlines, textSdf} from './afl-sign-font.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function assert(c, m) { if (!c) throw new Error(m); }
const PX = 0.016;

// ---------------- helpers (as tools/build-site-lighting-v1.mjs) ----------------
const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
function rrect(cu, cv, w, h, r, seg = 3) {
  const u0 = cu - w / 2, v0 = cv - h / 2, u1 = cu + w / 2, v1 = cv + h / 2, out = [];
  if (r <= 0) return [[u0, v0], [u1, v0], [u1, v1], [u0, v1]];
  for (const [ccu, ccv, a0] of [[u1 - r, v1 - r, 0], [u0 + r, v1 - r, 90], [u0 + r, v0 + r, 180], [u1 - r, v0 + r, 270]])
    for (let i = 0; i <= seg; i++) { const a = (a0 + 90 * i / seg) * Math.PI / 180; out.push([ccu + r * Math.cos(a), ccv + r * Math.sin(a)]); }
  return out;
}
const circle = (cu, cv, r, seg) => Array.from({length: seg}, (_, i) => { const a = Math.PI / seg + 2 * Math.PI * i / seg; return [cu + r * Math.cos(a), cv + r * Math.sin(a)]; });
const shapeOf = (outer, holes = []) => ({outer: orient(outer, true), holes: holes.map(h => orient(h, false))});
const planY = (part, outer, holes, y0, y1, c) => extrude(part, 'y', shapeOf(outer.map(([x, z]) => [z, x]), holes.map(h => h.map(([x, z]) => [z, x]))), y0, y1, c);
const alongZ = (part, outer, holes, z0, z1, c) => extrude(part, 'z', shapeOf(outer, holes), z0, z1, c);
/** A box from a to b (mm). */
const box = (part, a, b, c = 0) => planY(part, [[a[0], a[2]], [b[0], a[2]], [b[0], b[2]], [a[0], b[2]]], [], a[1], b[1], c);
function latheY(part, cx, cz, profile, seg, {capBottom = true, capTop = true, tag = 'side'} = {}) {
  const ang = i => Math.PI / seg + 2 * Math.PI * i / seg;
  const rings = profile.map(([r, y]) => r < 1e-6 ? null : Array.from({length: seg}, (_, i) => part.vtx([cx + r * Math.cos(ang(i)), y, cz + r * Math.sin(ang(i))])));
  for (let k = 0; k + 1 < profile.length; k++) {
    const [ra, ya] = profile[k], [rb, yb] = profile[k + 1], dr = rb - ra, dy = yb - ya;
    for (let i = 0; i < seg; i++) {
      const j = (i + 1) % seg, mm = ang(i) + Math.PI / seg, n = [dy * Math.cos(mm), -dr, dy * Math.sin(mm)];
      const t = Math.abs(dy) < 1e-6 ? 'cap' : Math.abs(dr) > 0.3 * Math.abs(dy) ? 'bevel' : tag;
      if (!rings[k]) part.face([part.vtx([cx, ya, cz]), rings[k + 1][j], rings[k + 1][i]], n, t);
      else if (!rings[k + 1]) part.face([rings[k][i], rings[k][j], part.vtx([cx, yb, cz])], n, t);
      else part.face([rings[k][i], rings[k][j], rings[k + 1][j], rings[k + 1][i]], n, t);
    }
  }
  for (const [k, s, on] of [[0, -1, capBottom], [profile.length - 1, 1, capTop]]) { if (!on || !rings[k]) continue; const c = part.vtx([cx, profile[k][1], cz]);
    for (let i = 0; i < seg; i++) part.face([c, rings[k][i], rings[k][(i + 1) % seg]], [0, s, 0], 'cap'); }
}
const MAT = (c, hl, sm, se, f0 = 20) => ({c, hl, sm, se, f0});
const hash3 = (a, b, c) => { let h = Math.imul(a | 0, 374761393) ^ Math.imul(b | 0, 668265263) ^ Math.imul(c | 0, 2147483647); h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };
const sm3 = t => t * t * (3 - 2 * t);
function vn3(x, y, z, seed) {
  const xi = Math.floor(x), yi = Math.floor(y), zi = Math.floor(z), tx = sm3(x - xi), ty = sm3(y - yi), tz = sm3(z - zi);
  const h = (i, j, k) => hash3(xi + i, yi + j + 977 * seed, zi + k), l = (a, b, t) => a + (b - a) * t;
  return l(l(l(h(0, 0, 0), h(1, 0, 0), tx), l(h(0, 1, 0), h(1, 1, 0), tx), ty), l(l(h(0, 0, 1), h(1, 0, 1), tx), l(h(0, 1, 1), h(1, 1, 1), tx), ty), tz) * 2 - 1;
}
/** A plain material with a soft low-frequency mottle only (no drawn lines). */
const mottled = (base, k = 0.03, seed = 1) => pos => { const [x, y, z] = pos.map(v => v / PX); return {c: base.map(v => v * (1 + k * vn3(x / 90, y / 90, z / 90, seed) + k * 0.4 * vn3(x / 25, y / 25, z / 25, seed + 7)))}; };
const mix = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);

// ---------------- channel letters ----------------
export const LETTER = {cap: CAP, base: 125, back: 499.6, returns: 120, face: 7, trim: 2.5};
export const LETTER_SETS = {am: LETTERS.slice(0, 13), nz: LETTERS.slice(13)};
const buildLetters = set => () => {
  const PARTS = [], P = (name, bone, mat) => { const p = new Part(name, bone, mat); PARTS.push(p); return p; };
  const L = LETTER, zFace = L.back - L.returns, zFront = zFace - L.face;
  for (const ch of LETTER_SETS[set]) {
    const body = outlines(ch), face = outlines(ch, 2, 0.6, L.trim), w = body.width;
    // the letter faces north: its viewer has east (+x) on the left, so the glyph's x runs toward -x (read left to right)
    const map = loop => loop.map(([x, y]) => [w / 2 - x, y + L.base]);
    // the returns run 1 mm into the face slab: no shared plane between the two solids
    for (const s of body.shapes) alongZ(P(`${ch}_body`, `${ch}_body`, 'returns'), map(s.outer), s.holes.map(map), zFace - 1, L.back, 0);
    for (const look of ['off', 'lit']) for (const s of face.shapes)
      alongZ(P(`${ch}_face_${look}`, `${ch}_${look}`, 'face_' + look), map(s.outer), s.holes.map(map), zFront, zFace, 1.2);
  }
  const MATS = {
    returns:  MAT(mottled([60, 62, 66], 0.02, 3), 8, 96, 118, 20),     // painted aluminium returns
    face_off: MAT(mottled([164, 38, 32], 0.015, 4), 4, 168, 182, 30),  // red acrylic, unlit
    face_lit: MAT([255, 98, 74], 2, 168, 182, 30),
  };
  return {PARTS, MATS, atlas: 2048, startS: 15};
};

// ---------------- price sign ----------------
// The four cells along x (east for facing north), the master is the second: the sign's centre is 500 mm east of the master's.
export const SIGN = {cx: 500, pad: 25, base: [1950, 300, 900], cap: [1990, 340, 980], cab: [1900, 200, 3800], hole: 1800,
  brand: [2700, 3700], rows: [[1960, 2600], [1160, 1800]], recess: 6};
export const PRICES = [['GASOLINE', '1.29', '9'], ['DIESEL', '1.38', '9']];
const PORT_MM = {half: 3.0 / PX, socket: 1.95 / PX, gap: 0.05 / PX, depth: 0.6 / PX, chamfer: 0.12 / PX, pinR: 0.45 / PX};
function bottomPort(P, bone) {   // as tools/build-site-lighting-v1.mjs: the standard AFL power port in the cell's underside
  const Q = PORT_MM, d = Q.depth;
  planY(P('port_plate', bone, 'portPlate'), rrect(0, 0, 2 * Q.half, 2 * Q.half, 0), [circle(0, 0, Q.socket, 16)], 0, d + 0.02 / PX, Q.chamfer);
  latheY(P('port_socket', bone, 'portSocket'), 0, 0, [[Q.socket - 0.05 / PX, d - 0.22 / PX], [Q.socket - 0.05 / PX, d + 0.02 / PX]], 16);
  latheY(P('port_pin', bone, 'portPin'), 0, 0, [[Q.pinR, d - 0.42 / PX], [Q.pinR, d - 0.22 / PX]], 8);
}
/** Seven-segment digit polygons (x, y mm) for ch in a w x h cell at (x0, y0), segment thickness t (lit segments only). */
const SEGS = {0: 'abcdef', 1: 'bc', 2: 'abged', 3: 'abgcd', 4: 'fgbc', 5: 'afgcd', 6: 'afgedc', 7: 'abc', 8: 'abcdefg', 9: 'abcdfg'};
function sevenSeg(ch, x0, y0, w, h, t) {
  const g = t * 0.14, hx = (xa, xb, y) => [[xa + g, y], [xa + t / 2 + g, y - t / 2], [xb - t / 2 - g, y - t / 2], [xb - g, y], [xb - t / 2 - g, y + t / 2], [xa + t / 2 + g, y + t / 2]];
  const vy = (x, ya, yb) => [[x, ya + g], [x + t / 2, ya + t / 2 + g], [x + t / 2, yb - t / 2 - g], [x, yb - g], [x - t / 2, yb - t / 2 - g], [x - t / 2, ya + t / 2 + g]];
  const l = x0 + t / 2, r = x0 + w - t / 2, b = y0 + t / 2, m = y0 + h / 2, top = y0 + h - t / 2;
  const S = {a: hx(l, r, top), g: hx(l, r, m), d: hx(l, r, b), f: vy(l, m, top), b: vy(r, m, top), e: vy(l, b, m), c: vy(r, b, m)};
  return [...SEGS[ch]].map(k => S[k]);
}
/** The lit LED segments of one price, right-aligned to xr (mm) in a row centred at yc; mirror: drawn for the back face. */
function pricePolys(price, tenth, xr, yc) {
  const H = 420, Wd = 230, t = 58, gap = 44, sH = 230, sW = 130, polys = [];
  let x = xr - sW;
  polys.push(...sevenSeg(tenth, x, yc + H / 2 - sH, sW, sH, t * 0.62));
  x -= gap;
  for (const ch of [...price].reverse()) {
    if (ch === '.') { x -= 70; polys.push([[x + 15, yc - H / 2], [x + 15 + 52, yc - H / 2], [x + 15 + 52, yc - H / 2 + 52], [x + 15, yc - H / 2 + 52]]); x -= gap * 0.4; continue; }
    x -= Wd; polys.push(...sevenSeg(ch, x, yc - H / 2, Wd, H, t)); x -= gap;
  }
  return polys;
}
/** Text coverage (0..1) of a word in the sign font, cap height `cap` mm, left x0 or centred on xc, baseline y0; aa mm. */
function textCover(word, cap, y0, {x0, xc}, aa = 4) {
  const t = textSdf(word), s = cap / CAP, left = xc !== undefined ? xc - t.width * s / 2 : x0;
  return (x, y) => { const d = t.sdf((x - left) / s, (y - y0) / s) * s; return Math.max(0, Math.min(1, 0.5 - d / aa)); };
}
const buildPriceSign = part => () => {
  const PARTS = [], P = (name, bone, mat) => { const p = new Part(name, bone, mat); if ((BUILD_OF[mat] || 'faces') === part) PARTS.push(p); return p; };
  const G = SIGN, cx = G.cx, hole = PORT_MM.half + PORT_MM.gap;
  // footing pad over the four ground cells, the port's opening through it; the port in the master cell's underside
  planY(P('pad', 'body', 'pad'), rrect(cx, 0, 3999.6, 999.6, 8), [rrect(0, 0, 2 * hole, 2 * hole, 0)], 0, G.pad, 5);
  bottomPort(P, 'body');
  // monument base (1 mm into the pad), stone cap (1 mm into the base), cabinet (1 mm into the cap)
  planY(P('base', 'body', 'base'), rrect(cx, 0, 2 * G.base[0], 2 * G.base[1], 12), [], G.pad - 1, G.base[2], 10);
  planY(P('cap', 'body', 'cap'), rrect(cx, 0, 2 * G.cap[0], 2 * G.cap[1], 14), [], G.base[2] - 1, G.cap[2], 8);
  // the cabinet: a frame with three openings through it (the light box and the two rows), faced on both sides by panels
  const hx0 = cx - G.hole, hx1 = cx + G.hole, rect = (x0, y0, x1, y1) => [[x0, y0], [x1, y0], [x1, y1], [x0, y1]];
  const holes = [rect(hx0, G.brand[0], hx1, G.brand[1]), ...G.rows.map(([y0, y1]) => rect(hx0, y0, hx1, y1))];
  alongZ(P('cabinet', 'body', 'frame'), rect(cx - G.cab[0], G.cap[2] - 1, cx + G.cab[0], G.cab[2]), holes, -G.cab[1], G.cab[1], 6);
  const zf = -G.cab[1] + G.recess, zb = G.cab[1] - G.recess;   // the panels' two faces, recessed into the openings
  // a panel fills its opening through the cabinet: its front and back faces are the sign's two faces
  const slab = (name, bone, mat, x0, y0, x1, y1) => alongZ(P(name, bone, mat), rect(x0 + 0.5, y0 + 0.5, x1 - 0.5, y1 - 0.5), [], zf, zb, 0);
  for (const look of ['off', 'lit']) slab('brand_' + look, 'brand_' + look, 'brand_' + look, hx0, G.brand[0], hx1, G.brand[1]);
  // each row one slab through the cabinet, both faces printed (the label at each face's own left)
  G.rows.forEach(([y0, y1], i) => slab(`row_${i}`, 'body', 'row_' + i, hx0, y0, hx1, y1));
  // the digits (shown when powered), at each face's own right: 3 mm proud of the row panel, 1 mm into it. Laid out in reading
  // x (+x to the reader's right); the front face's reader (north of the sign) has +x on the left, so the front set is mirrored
  G.rows.forEach(([y0, y1], i) => {
    const [, price, tenth] = PRICES[i], polys = pricePolys(price, tenth, hx1 - 90, (y0 + y1) / 2);
    polys.forEach((poly, k) => {
      alongZ(P(`digit_${i}_${k}_f`, 'digits', 'digit'), poly.map(([x, y]) => [2 * cx - x, y]), [], zf - 3, zf + 1, 0);
      alongZ(P(`digit_${i}_${k}_b`, 'digits', 'digit'), poly, [], zb - 1, zb + 3, 0);
    });
  });
  // printed faces in reading x: the front face (toward -z, read from the north) mirrored about the centre, the back as is
  const side = (pos, n) => { const x = pos[0] / PX, y = pos[1] / PX; return n[2] < -0.5 ? [2 * cx - x, y] : [x, y]; };
  const brandText = textCover('PRAIRIE', 430, (G.brand[0] + G.brand[1]) / 2 - 215, {xc: cx});
  const brand = (red, white) => (pos, n) => { const [x, y] = side(pos, n), k = brandText(x, y); return {c: mix(red, white, k)}; };
  const labels = PRICES.map(([name], i) => { const [y0, y1] = G.rows[i]; return textCover(name, 200, (y0 + y1) / 2 - 100, {x0: hx0 + 110}); });
  const row = i => (pos, n) => { const [x, y] = side(pos, n); return {c: mix([18, 19, 22], [236, 234, 228], labels[i](x, y))}; };
  const MATS = {
    pad:        MAT(mottled([164, 162, 156], 0.03, 1), 4, 52, 60),
    base:       MAT(mottled([182, 176, 164], 0.035, 2), 6, 60, 70),     // ground-face block, as the store's base course
    cap:        MAT(mottled([212, 206, 194], 0.02, 3), 6, 80, 92),
    frame:      MAT(mottled([60, 62, 66], 0.015, 4), 8, 110, 130, 20),  // charcoal powder-coated aluminium
    brand_off:  MAT(brand([150, 34, 30], [232, 228, 220]), 4, 150, 170, 25),
    brand_lit:  MAT(brand([255, 70, 52], [255, 250, 240]), 2, 150, 170, 25),
    row_0:      MAT(row(0), 3, 170, 186, 25),        // dark glass-fronted LED module, the grade printed white
    row_1:      MAT(row(1), 3, 170, 186, 25),
    digit:      MAT([255, 62, 38], 2, 160, 180, 25),
    portPlate:  MAT([150, 154, 160], 12, 150, 168, 255),
    portSocket: MAT([26, 27, 29], 2, 60, 70, 20),
    portPin:    MAT([96, 100, 104], 6, 130, 140, 255),
  };
  return {PARTS, MATS, atlas: 2048, startS: 15};
};
// the build (atlas) of each material: the footing and base, the cabinet frame, the rest (printed and lit faces). One 2048
// atlas for all the structure gave 184 texels a block; split, each part gets 240.
const BUILD_OF = {pad: 'base', base: 'base', cap: 'base', portPlate: 'base', portSocket: 'base', portPin: 'base', frame: 'cabinet'};

export const EMISSION = {face_lit: 245, brand_lit: 235, digit: 250};
export const GLOW = new Set(['face_lit', 'brand_lit', 'digit']);

// ---------------- bake ----------------
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
function bake(key, id, source, build) {
  const {PARTS: all, MATS, atlas, startS} = build(), PARTS = all.filter(p => p.f.length);
  for (const p of PARTS) p.v = p.v.map(q => q.map(v => v * PX));
  for (const p of PARTS) {
    const out = [];
    for (const f of p.f) {
      const P3 = f.ids.map(i => p.v[i]), n = newell(P3);
      const convexQuad = f.ids.length === 4 && [0, 1, 2, 3].every(k => dot(cross(sub(P3[(k + 1) % 4], P3[k]), sub(P3[(k + 2) % 4], P3[(k + 1) % 4])), n) > 1e-14);
      if (f.ids.length === 3 || convexQuad) { out.push(f); continue; }
      assert(f.ids.length === 4, `${key}: ${f.ids.length}-gon in ${p.name}`);
      const k = [0, 1, 2, 3].find(k => dot(cross(sub(P3[k], P3[(k + 3) % 4]), sub(P3[(k + 1) % 4], P3[k])), n) <= 1e-14) ?? 0;
      out.push({...f, ids: [f.ids[k], f.ids[(k + 1) % 4], f.ids[(k + 2) % 4]]}, {...f, ids: [f.ids[(k + 2) % 4], f.ids[(k + 3) % 4], f.ids[k]]});
    }
    p.f = out;
  }
  const UV = unwrap(PARTS, {atlas, pad: 2, startS, stepS: 0.5});
  for (const p of PARTS) assert(p.f.every(f => UV.faceUV.get(p)?.has(f)), `${key}: unmapped face in ${p.name}`);
  const first = Object.values(MATS)[0], bg = typeof first.c === 'function' ? [160, 160, 160] : first.c;
  const painted = paint({PARTS, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas, pad: 2, MATS, ZONED: new Set(), groupInfo: new Map(),
    sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [...bg, 255], s: [first.sm, first.f0, 0, 255], n: [128, 128, 255, 255]}});
  const maps = painted.PNG.map(b => { const r = readPng(b); assert(r.bpp === 4 && r.w === atlas, 'atlas format'); return r.px; });
  for (const is of UV.islands) {
    const e = EMISSION[is.part.mat]; if (e === undefined) continue;
    for (let y = is.py - 2; y < is.py + is.H + 2; y++) for (let x = is.px - 2; x < is.px + is.W + 2; x++) maps[1][(y * atlas + x) * 4 + 3] = e;
  }
  return {key, id, source, PARTS, MATS, atlas, UV, maps: maps.map(px => png(px, atlas, atlas))};
}
const B = {
  letters_am: bake('letters_am', 'channel_letter_am', 'channel_letters_am_v1', buildLetters('am')),
  letters_nz: bake('letters_nz', 'channel_letter_nz', 'channel_letters_nz_v1', buildLetters('nz')),
  sign: bake('sign', 'price_sign', 'price_sign_v1', buildPriceSign('base')),
  sign_cabinet: bake('sign_cabinet', 'price_sign_cabinet', 'price_sign_cabinet_v1', buildPriceSign('cabinet')),
  sign_face: bake('sign_face', 'price_sign_face', 'price_sign_face_v1', buildPriceSign('faces')),
};
const letterBake = ch => LETTER_SETS.am.includes(ch) ? B.letters_am : B.letters_nz;
/** A look: the parts of some bones, possibly from more than one build (the price sign's body: structure + static panels). */
const LOOKS = {
  ...Object.fromEntries(LETTERS.flatMap(ch => ['off', 'lit'].map(look => [`letter_${ch}_${look}`, [[letterBake(ch), new Set([`${ch}_body`, `${ch}_${look}`])]]]))),
  sign_body: [[B.sign, new Set(['body'])], [B.sign_cabinet, new Set(['body'])], [B.sign_face, new Set(['body'])]], sign_brand_off: [[B.sign_face, new Set(['brand_off'])]],
  sign_brand_lit: [[B.sign_face, new Set(['brand_lit'])]], sign_digits: [[B.sign_face, new Set(['digits'])]],
};
const partsOf = look => look.flatMap(([b, bones]) => b.PARTS.filter(p => bones.has(p.bone)));
const coplanar = {};
for (const [k, look] of Object.entries(LOOKS)) { const zf = zFightLevels(partsOf(look), new Map()); if (zf.unresolved.length) coplanar[k] = zf.unresolved.slice(0, 6); }
{ const zf = zFightLevels(partsOf([...LOOKS.sign_body, ...LOOKS.sign_brand_lit, ...LOOKS.sign_digits]), new Map()); if (zf.unresolved.length) coplanar.sign_all = zf.unresolved.slice(0, 6); }
// closed surfaces: every part closed, wound one way, facing out (held, dropped and framed items are seen from every side)
function solidity(p) {
  const key = q => q.map(v => Math.round(v * 1e5)).join(','), dir = new Map(); let vol = 0;
  for (const f of p.f) {
    const P3 = f.ids.map(i => p.v[i]);
    for (let j = 1; j + 1 < P3.length; j++) vol += dot(P3[0], cross(P3[j], P3[j + 1])) / 6;
    for (let j = 0; j < f.ids.length; j++) { const a = key(P3[j]), b = key(P3[(j + 1) % P3.length]); if (a !== b) dir.set(a + '>' + b, (dir.get(a + '>' + b) || 0) + 1); }
  }
  let open = 0, flipped = 0;
  for (const [e, n] of dir) { const [a, b] = e.split('>'); if (!dir.get(b + '>' + a)) open++; if (n > 1) flipped++; }
  return {open, flipped, vol};
}
export const solids = {};
for (const b of Object.values(B)) for (const p of b.PARTS) { const s = solidity(p); if (s.open || s.flipped || s.vol <= 0) solids[b.key + '/' + p.name] = s; }
assert(!Object.keys(solids).length, 'not closed / wound out: ' + JSON.stringify(solids).slice(0, 2000));

// ---------------- OBJ / MTL / models ----------------
const D2R = Math.PI / 180, f6 = v => (+v.toFixed(6)).toString(), r3 = v => +v.toFixed(3) || 0;
const SMOOTH = Math.cos(36 * D2R);
function cornerNormals(p) {
  const fn = p.f.map(f => newell(f.ids.map(i => p.v[i]))), byV = new Map();
  p.f.forEach((f, k) => f.ids.forEach(i => (byV.get(i) || byV.set(i, []).get(i)).push(k)));
  return p.f.map((f, k) => { const n0 = norm(fn[k]); return f.ids.map(i => norm(byV.get(i).reduce((a, j) => dot(norm(fn[j]), n0) >= SMOOTH ? add(a, fn[j]) : a, [0, 0, 0]))); });
}
function objOf(title, file, look) {
  const out = [`# AFL ${title}, generated by tools/build-prairie-signage-v1.mjs`, `mtllib ${file.split('/').pop()}.mtl`];
  let vBase = 1, tBase = 1, nBase = 1;
  for (const [b, bones] of look) for (const p of b.PARTS) {
    if (!bones.has(p.bone)) continue;
    out.push(`o ${p.name}`, `usemtl ${b.key}${GLOW.has(p.mat) ? '_glow' : ''}`);
    for (const q of p.v) out.push(`v ${f6(q[0] / 16 + 0.5)} ${f6(q[1] / 16)} ${f6(q[2] / 16 + 0.5)}`);
    const uvs = b.UV.faceUV.get(p), vt = [], vn = [], fl = [], cn = cornerNormals(p), nIndex = new Map();
    p.f.forEach((f, k) => {
      const uv = uvs.get(f);
      fl.push('f ' + f.ids.map((id, j) => {
        const key = cn[k][j].map(f6).join(' ');
        if (!nIndex.has(key)) { nIndex.set(key, nBase + vn.length); vn.push('vn ' + key); }
        vt.push(`vt ${f6(uv[j][0] / b.atlas)} ${f6(1 - uv[j][1] / b.atlas)}`); return `${vBase + id}/${tBase + vt.length - 1}/${nIndex.get(key)}`;
      }).join(' '));
    });
    out.push(...vt, ...vn, ...fl);
    vBase += p.v.length; tBase += vt.length; nBase += vn.length;
  }
  return out.join('\n') + '\n';
}
const mtlOf = (title, look) => `# AFL ${title}\n` + look.map(([b, bones]) => `newmtl ${b.key}\nKd 1 1 1\nmap_Kd apocalypse_firstlight:block/${b.id}\n` +
  (b.PARTS.some(p => bones.has(p.bone) && GLOW.has(p.mat)) ? `newmtl ${b.key}_glow\nKa 1 1 1\nKd 1 1 1\nmap_Kd apocalypse_firstlight:block/${b.id}\n` : '')).join('');
const objModel = (file, particle) => ({loader: 'forge:obj', model: `apocalypse_firstlight:models/block/${file}.obj`, automatic_culling: false,
  flip_v: true, shade_quads: true, emissive_ambient: true, ambientocclusion: false, textures: {particle: `apocalypse_firstlight:block/${particle}`}});
/** GUI framing: the projected bounds scaled to `size` px (at most 4: vanilla clamps display scales) and centred. */
function guiFit(look, rotation, size = 15.2) {
  const [ax, ay] = rotation.map(v => v * D2R);
  const rot = q => { const x = q[0] * Math.cos(ay) + q[2] * Math.sin(ay), z = -q[0] * Math.sin(ay) + q[2] * Math.cos(ay); return [x, q[1] * Math.cos(ax) - z * Math.sin(ax)]; };
  const pts = partsOf(look).flatMap(p => p.v.map(q => rot([q[0], q[1] - 8, q[2]])));
  const xs = pts.map(q => q[0]), ys = pts.map(q => q[1]), w = Math.max(...xs) - Math.min(...xs), h = Math.max(...ys) - Math.min(...ys);
  const scale = r3(Math.min(4, size / Math.max(w, h))), cx = (Math.max(...xs) + Math.min(...xs)) / 2, cy = (Math.max(...ys) + Math.min(...ys)) / 2;
  return {rotation, translation: [r3(-scale * cx), r3(-scale * cy), 0], scale: [scale, scale, scale]};
}
const cellPoints = look => partsOf(look).flatMap(p => p.v.map(([x, y, z]) => [x + 8, y, z + 8]));
const display = (gui, look, size) => ({...heldDisplay(cellPoints(look), {size, rotations: {fixed: [0, 180, 0]}}), gui});

// editable Free Model sources (AGENTS.md): one group per bone
const uuidOf = (ns, s) => { const h = createHash('sha256').update(`afl-prairie-signage-v1:${ns}:${s}`).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r12 = v => +v.toFixed(12) || 0;
function sourceOf(b) {
  const uuid = s => uuidOf(b.id, s), name = b.source;
  const bones = [...new Set(b.PARTS.map(p => p.bone))], gid = bone => uuid('group:' + bone);
  const elements = b.PARTS.map((p, n) => {
    const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {}, uvs = b.UV.faceUV.get(p);
    p.v.forEach((q, i) => { vertices[key(i)] = q.map(r12); });
    p.f.forEach((f, fi) => { const uv = uvs.get(f); faces['f' + key(fi)] = {uv: Object.fromEntries(f.ids.map((id, j) => [key(id), uv[j].map(r12)])), vertices: f.ids.map(key), texture: 0}; });
    return {name: p.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: true, locked: false,
      render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: uuid('mesh:' + p.name + ':' + n)};
  });
  return {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name, model_identifier: '', visible_box: [1, 1, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: b.atlas, height: b.atlas},
    elements, groups: bones.map(bone => ({name: bone, uuid: gid(bone), export: true, locked: false, scope: 0,
      selected: false, visibility: true, _static: {properties: {}, temp_data: {}}, origin: [0, 0, 0], rotation: [0, 0, 0],
      color: 0, children: [], reset: false, shade: true, mirror_uv: false, autouv: 0, isOpen: true, primary_selected: false})),
    outliner: bones.map(bone => ({uuid: gid(bone), isOpen: true, children: elements.filter((e, i) => b.PARTS[i].bone === bone).map(e => e.uuid)})),
    textures: [{name: name + '.png', relative_path: `textures/${name}.png`, folder: '', namespace: '', id: '0', group: '', scope: 0,
      width: b.atlas, height: b.atlas, uv_width: b.atlas, uv_height: b.atlas, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
      file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
      frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture'),
      source: 'data:image/png;base64,' + b.maps[0].toString('base64')}],
    animations: []};
}

const bb = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const json = v => JSON.stringify(v, null, 2) + '\n';
const outputs = [], objs = [];
const NS = 'apocalypse_firstlight:block/';
const model = (file, title, look) => {
  const obj = objOf(title, file, look);
  outputs.push([path.join(assets, `models/block/${file}.obj`), obj], [path.join(assets, `models/block/${file}.mtl`), mtlOf(title, look)],
    [path.join(assets, `models/block/${file}.json`), json(objModel(file, look[0][0].id))]);
  objs.push([file, obj]);
};
for (const b of Object.values(B)) outputs.push([path.join(bb, `${b.source}.bbmodel`), JSON.stringify(sourceOf(b))],
  ...['', '_s', '_n'].flatMap((k, i) => [[path.join(bb, `textures/${b.source}${k}.png`), b.maps[i]], [path.join(assets, `textures/block/${b.id}${k}.png`), b.maps[i]]]));
const YROT = {north: 0, east: 90, south: 180, west: 270};
const yr = y => (y % 360 ? {y: y % 360} : {});

// channel letters: <letter>_off / _lit per letter; facing = away from the wall
for (const ch of LETTERS) for (const look of ['off', 'lit']) model(`channel_letter/${ch.toLowerCase()}_${look}`, `PRAIRIE signage V1 channel letter ${ch} (${look})`, LOOKS[`letter_${ch}_${look}`]);
outputs.push([path.join(assets, 'blockstates/channel_letter.json'), json({variants: Object.fromEntries(Object.entries(YROT).flatMap(([f, y]) => LETTERS.flatMap(ch => [false, true].map(lit =>
  [`facing=${f},letter=${ch.toLowerCase()},lit=${lit}`, {model: NS + `channel_letter/${ch.toLowerCase()}_${lit ? 'lit' : 'off'}`, ...yr(y)}]))))})]);
// the item: one item, the letter in its BlockStateTag; the icon follows apocalypse_firstlight:letter (index / 25)
for (const ch of LETTERS) {
  const look = LOOKS[`letter_${ch}_off`];
  outputs.push([path.join(assets, `models/item/channel_letter_${ch.toLowerCase()}.json`), json({parent: NS + `channel_letter/${ch.toLowerCase()}_off`, gui_light: 'side',
    display: display(guiFit(look, [10, 200, 0]), look, 0.8)})]);
}
outputs.push([path.join(assets, 'models/item/channel_letter.json'), json({parent: 'apocalypse_firstlight:item/channel_letter_a',
  overrides: LETTERS.slice(1).map((ch, i) => ({predicate: {'apocalypse_firstlight:letter': r3((i + 0.5) / 25)}, model: `apocalypse_firstlight:item/channel_letter_${ch.toLowerCase()}`}))})]);

// price sign: the master cell (c1r0) draws the body, the light box (off / lit) and, when powered, the digits; other cells nothing
model('price_sign/body', 'PRAIRIE signage V1 price sign', LOOKS.sign_body);
model('price_sign/brand_off', 'PRAIRIE signage V1 price sign light box (off)', LOOKS.sign_brand_off);
model('price_sign/brand_lit', 'PRAIRIE signage V1 price sign light box (lit)', LOOKS.sign_brand_lit);
model('price_sign/digits', 'PRAIRIE signage V1 price sign digits', LOOKS.sign_digits);
outputs.push([path.join(assets, 'models/block/price_sign/cell.json'), json({textures: {particle: NS + 'price_sign'}, elements: []})]);
export const SIGN_CELLS = [0, 1, 2, 3].flatMap(r => [0, 1, 2, 3].map(c => `c${c}r${r}`));
{
  const others = SIGN_CELLS.filter(c => c !== 'c1r0').join('|'), multipart = [{when: {cell: others}, apply: {model: NS + 'price_sign/cell'}}];
  for (const [f, y] of Object.entries(YROT)) {
    multipart.push({when: {cell: 'c1r0', facing: f}, apply: {model: NS + 'price_sign/body', ...yr(y)}});
    for (const lit of [false, true]) multipart.push({when: {cell: 'c1r0', facing: f, lit: String(lit)}, apply: {model: NS + `price_sign/brand_${lit ? 'lit' : 'off'}`, ...yr(y)}});
    multipart.push({when: {cell: 'c1r0', facing: f, digits: 'true'}, apply: {model: NS + 'price_sign/digits', ...yr(y)}});
  }
  outputs.push([path.join(assets, 'blockstates/price_sign.json'), json({multipart})]);
}
outputs.push([path.join(assets, 'models/block/price_sign/item.json'), json({loader: 'forge:composite', textures: {particle: NS + 'price_sign'},
  children: {body: {parent: NS + 'price_sign/body'}, brand: {parent: NS + 'price_sign/brand_off'}}})]);
outputs.push([path.join(assets, 'models/item/price_sign.json'), json({parent: NS + 'price_sign/item', gui_light: 'side',
  display: display(guiFit([...LOOKS.sign_body, ...LOOKS.sign_brand_off], [20, 200, 0]), [...LOOKS.sign_body, ...LOOKS.sign_brand_off], 1)})]);

const tris = look => partsOf(look).reduce((s, p) => s + p.f.reduce((t, f) => t + f.ids.length - 2, 0), 0);
export const stats = Object.fromEntries(Object.entries(LOOKS).filter(([k]) => !k.startsWith('letter_') || k.endsWith('_off')).map(([k, look]) => [k, tris(look)]));
const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  console.log(JSON.stringify({triangles: stats, texelsPerPx: Object.fromEntries(Object.entries(B).map(([k, b]) => [k, b.UV.S])), coplanar}));
  const pi = process.argv.indexOf('--preview');
  if (pi > 0) {
    const dir = process.argv[pi + 1]; fs.mkdirSync(dir, {recursive: true});
    for (const [file, obj] of objs) fs.writeFileSync(path.join(dir, file.replace('/', '_') + '.obj'), obj);
    for (const b of Object.values(B)) ['', '_s', '_n'].forEach((k, i) => fs.writeFileSync(path.join(dir, `${b.id}${k}.png`), b.maps[i]));
    console.log('preview written to ' + dir);
  } else if (process.argv.includes('--check')) {
    for (const [file, data] of outputs) { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; if (!cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data))) throw new Error('stale ' + path.relative(ROOT, file)); }
    console.log('CHECK OK');
  } else {
    for (const [file] of outputs) fs.mkdirSync(path.dirname(file), {recursive: true});
    for (const [file, data] of outputs) fs.writeFileSync(file, data);
    console.log('wrote ' + outputs.length + ' files');
  }
}
