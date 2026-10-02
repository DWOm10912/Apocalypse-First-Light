// Goods props: the anonymous everyday things a searchable container shows while it holds anything (first user: the
// industrial locker; docs/models/industrial_locker_v2.md "货物"). Built from boxes, cylinders and revolved profiles in a
// local frame (x across, y up from the base, z depth, -z toward the viewer), then turned about y and placed. No print or
// painted marks: parcel tape and reflective bands are raised strips. Sub-parts never share a plane facing the same way.
// Materials: PROP_MATS, merged into the generator's MATS (plain colours, nothing brighter than about 190).
import {extrude, area2, revolve} from './cube-slab-mesh-lib.mjs';

const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
const circle = (cu, cv, r, seg) => Array.from({length: seg}, (_, i) => { const a = Math.PI / seg + 2 * Math.PI * i / seg; return [cu + r * Math.cos(a), cv + r * Math.sin(a)]; });

// ---------------- local primitives ----------------
export const box = (p, x0, y0, z0, x1, y1, z1, c = 0) => extrude(p, 'y', {outer: orient([[z0, x0], [z1, x0], [z1, x1], [z0, x1]], true), holes: []}, y0, y1, c);
export const cylY = (p, x, z, r, y0, y1, seg = 12, c = 0) => extrude(p, 'y', {outer: orient(circle(z, x, r, seg), true), holes: []}, y0, y1, c);
export const cylZ = (p, x, y, r, z0, z1, seg = 12, c = 0) => extrude(p, 'z', {outer: orient(circle(x, y, r, seg), true), holes: []}, z0, z1, c);
// outline [[x, y], ...] in the front plane, extruded along z
export const slabZ = (p, outline, z0, z1, c = 0) => extrude(p, 'z', {outer: orient(outline, true), holes: []}, z0, z1, c);
// solid of revolution about the local y axis: profile [[h, r], ...] (any winding), h up from the base
export function revolveY(p, profile, seg = 12) {
  const start = p.v.length;
  revolve(p, 0, 0, orient(profile.map(([h, r]) => [h, r]), true), seg);
  for (let i = start; i < p.v.length; i++) { const [x, y, z] = p.v[i]; p.v[i] = [x, z, -y]; }   // z axis -> y (a rotation)
}
// open band (no caps) round the local y axis, e.g. a seam on a ball
export function bandY(p, r, y0, y1, seg = 12) {
  const a = i => Math.PI / seg + 2 * Math.PI * i / seg, lo = [], hi = [];
  for (let i = 0; i < seg; i++) { lo.push(p.vtx([r * Math.cos(a(i)), y0, r * Math.sin(a(i))])); hi.push(p.vtx([r * Math.cos(a(i)), y1, r * Math.sin(a(i))])); }
  for (let i = 0; i < seg; i++) { const j = (i + 1) % seg, m = (a(i) + a(j)) / 2; p.face([lo[i], lo[j], hi[j], hi[i]], [Math.cos(m), 0, Math.sin(m)], 'side'); }
}

// ---------------- frames ----------------
// at = {x, y, z, yaw}: local origin and turn (radians, about +y) in the generator's frame
export const frame = ({x = 0, y = 0, z = 0, yaw = 0}) => { const c = Math.cos(yaw), s = Math.sin(yaw);
  return q => [x + q[0] * c - q[2] * s, y + q[1], z + q[0] * s + q[2] * c]; };
export const then = (outer, inner) => q => outer(inner(q));

export const PROP_MATS = {
  kraft:          {c: [146, 114, 78], hl: 6, sm: 64, se: 78, f0: 20},
  tape:           {c: [186, 150, 50], hl: 8, sm: 130, se: 140, f0: 20},
  canvas_olive:   {c: [88, 92, 66], hl: 4, sm: 60, se: 70, f0: 20},
  canvas_navy:    {c: [52, 60, 80], hl: 4, sm: 60, se: 70, f0: 20},
  canvas_khaki:   {c: [128, 116, 88], hl: 4, sm: 60, se: 70, f0: 20},
  strap:          {c: [44, 44, 42], hl: 4, sm: 70, se: 80, f0: 20},
  toolbox_red:    {c: [148, 50, 42], hl: 10, sm: 120, se: 140, f0: 20},
  toolbox_lid:    {c: [128, 42, 36], hl: 10, sm: 120, se: 140, f0: 20},
  grip_black:     {c: [34, 34, 36], hl: 4, sm: 90, se: 100, f0: 20},
  leather_tan:    {c: [118, 86, 54], hl: 6, sm: 70, se: 84, f0: 20},
  leather_dark:   {c: [72, 54, 40], hl: 6, sm: 70, se: 84, f0: 20},
  rubber:         {c: [34, 34, 36], hl: 2, sm: 40, se: 48, f0: 20},
  glove_tan:      {c: [168, 140, 94], hl: 4, sm: 56, se: 64, f0: 20},
  glove_grey:     {c: [108, 110, 112], hl: 4, sm: 56, se: 64, f0: 20},
  helmet_yellow:  {c: [190, 158, 46], hl: 10, sm: 150, se: 160, f0: 20},
  helmet_white:   {c: [172, 174, 172], hl: 8, sm: 150, se: 160, f0: 20},
  helmet_orange:  {c: [186, 100, 44], hl: 10, sm: 150, se: 160, f0: 20},
  jacket_navy:    {c: [50, 58, 80], hl: 4, sm: 56, se: 66, f0: 20},
  jacket_grey:    {c: [72, 74, 78], hl: 4, sm: 56, se: 66, f0: 20},
  jacket_olive:   {c: [74, 80, 58], hl: 4, sm: 56, se: 66, f0: 20},
  hanger:         {c: [146, 148, 150], hl: 10, sm: 140, se: 150, f0: 20},
  vest_orange:    {c: [184, 100, 42], hl: 6, sm: 80, se: 90, f0: 20},
  reflective:     {c: [162, 166, 168], hl: 10, sm: 170, se: 180, f0: 20},
  pack_blue:      {c: [60, 84, 136], hl: 6, sm: 100, se: 112, f0: 20},
  pack_red:       {c: [142, 56, 52], hl: 6, sm: 100, se: 112, f0: 20},
  pack_green:     {c: [68, 98, 68], hl: 6, sm: 100, se: 112, f0: 20},
  pack_black:     {c: [40, 42, 46], hl: 6, sm: 100, se: 112, f0: 20},
  book_navy:      {c: [46, 58, 92], hl: 4, sm: 90, se: 100, f0: 20},
  book_maroon:    {c: [104, 40, 44], hl: 4, sm: 90, se: 100, f0: 20},
  book_olive:     {c: [84, 92, 56], hl: 4, sm: 90, se: 100, f0: 20},
  book_mustard:   {c: [160, 124, 48], hl: 4, sm: 90, se: 100, f0: 20},
  book_grey:      {c: [96, 98, 102], hl: 4, sm: 90, se: 100, f0: 20},
  pages:          {c: [172, 166, 148], hl: 2, sm: 50, se: 56, f0: 20},
  sole_white:     {c: [166, 166, 158], hl: 4, sm: 70, se: 80, f0: 20},
  sneaker_navy:   {c: [52, 62, 96], hl: 4, sm: 80, se: 90, f0: 20},
  sneaker_red:    {c: [142, 54, 50], hl: 4, sm: 80, se: 90, f0: 20},
  sneaker_grey:   {c: [110, 112, 116], hl: 4, sm: 80, se: 90, f0: 20},
  sports_blue:    {c: [58, 84, 130], hl: 6, sm: 110, se: 120, f0: 20},
  sports_black:   {c: [42, 44, 48], hl: 6, sm: 110, se: 120, f0: 20},
  sports_red:     {c: [140, 52, 50], hl: 6, sm: 110, se: 120, f0: 20},
  ball_orange:    {c: [172, 90, 44], hl: 4, sm: 60, se: 66, f0: 20},
  seam_dark:      {c: [36, 30, 26], hl: 0, sm: 50, se: 50, f0: 20},
  thermos_green:  {c: [66, 94, 76], hl: 8, sm: 130, se: 140, f0: 20},
  thermos_red:    {c: [140, 58, 52], hl: 8, sm: 130, se: 140, f0: 20},
  thermos_blue:   {c: [62, 88, 128], hl: 8, sm: 130, se: 140, f0: 20},
  cap_dark:       {c: [44, 46, 50], hl: 6, sm: 110, se: 120, f0: 20},
  lunch_blue:     {c: [70, 92, 130], hl: 6, sm: 120, se: 130, f0: 20},
  lunch_red:      {c: [138, 62, 56], hl: 6, sm: 120, se: 130, f0: 20},
};

/**
 * Prop builders bound to the generator's part factory P(name, bone, mat) and a seeded rnd(). Each builder draws into new
 * parts named after the bone, maps them through tf (local -> generator frame, see frame / then) and returns the height
 * it reaches (local y), for stacking.
 */
export function goodsProps(P, rnd) {
  const range = (a, b) => a + (b - a) * rnd(), pick = list => list[Math.floor(rnd() * list.length)];
  const counters = new Map();
  const part = (bone, mat, tf) => {   // a new part whose vertices are mapped through tf once drawn (see done)
    const n = (counters.get(bone) || 0) + 1; counters.set(bone, n);
    const p = P(`${bone}_${n}`, bone, mat); p.tf = tf; return p;
  };
  const done = (...parts) => { for (const p of parts) { p.v = p.v.map(p.tf); delete p.tf; } };

  // parcel carton with yellow tape along the top seam and over both ends
  function carton(bone, tf, w, d, h) {
    const b = part(bone, 'kraft', tf), t = part(bone, 'tape', tf);
    box(b, -w / 2, 0, -d / 2, w / 2, h, d / 2, 0.12);
    box(t, -w / 2 - 0.04, h - 0.02, -0.32, w / 2 + 0.04, h + 0.05, 0.32);
    for (const s of [-1, 1]) box(t, s > 0 ? w / 2 - 0.02 : -w / 2 - 0.06, h * 0.68, -0.3, s > 0 ? w / 2 + 0.06 : -w / 2 + 0.02, h + 0.03, 0.3);
    done(b, t);
    return h + 0.05;
  }
  // one to `layers` cartons, each smaller and a little turned on the one below
  function cartons(bone, tf, [w0, w1, d0, d1, h0, h1], layers) {
    let y = 0, yaw = 0, ox = 0, oz = 0;
    for (let i = 0; i < layers; i++) {
      const k = 1 - i * 0.16, w = range(w0, w1) * k, d = range(d0, d1) * k, h = range(h0, h1) * (1 - i * 0.12);
      if (i) { yaw += range(-0.35, 0.35); ox += range(-0.3, 0.3); oz += range(-0.3, 0.3); }
      y += carton(bone, then(tf, frame({x: ox, y, z: oz, yaw})), w, d, h) + 0.02;
    }
    return y;
  }
  // soft canvas holdall: rounded body, a strap round it near each end
  function holdall(bone, tf, w, d, h) {
    const b = part(bone, pick(['canvas_olive', 'canvas_navy', 'canvas_khaki']), tf), s = part(bone, 'strap', tf);
    box(b, -w / 2, 0, -d / 2, w / 2, h, d / 2, Math.min(0.9, h / 3));
    for (const zc of [-d / 4, d / 4]) box(s, -w / 2 - 0.06, 0.03, zc - 0.3, w / 2 + 0.06, h + 0.06, zc + 0.3);
    done(b, s);
    return h + 0.06;
  }
  // tote hanging from a coat hook: body against the wall (z up to wallZ), two straps over the hook
  function tote(bone, tf, hookY, wallZ) {
    const b = part(bone, pick(['canvas_olive', 'canvas_navy', 'canvas_khaki']), tf), s = part(bone, 'strap', tf);
    const top = hookY - 1.6, w = range(3.2, 3.6), h = range(4.2, 4.8), d = range(1.4, 1.7);
    box(b, -w / 2, top - h, wallZ - 0.1 - d, w / 2, top, wallZ - 0.1, 0.45);
    for (const sx of [-0.7, 0.4]) box(s, sx, top - 0.3, wallZ - 1.1, sx + 0.3, hookY + 0.25, wallZ - 0.9);
    box(s, -0.75, hookY + 0.05, wallZ - 1.15, 0.75, hookY + 0.3, wallZ - 0.35);
    done(b, s);
  }
  // steel toolbox: body, lid band, carry handle on two posts (long side along z)
  function toolbox(bone, tf) {
    const b = part(bone, 'toolbox_red', tf), l = part(bone, 'toolbox_lid', tf), g = part(bone, 'grip_black', tf);
    const w = range(3.0, 3.4), d = range(6.0, 6.6), h = range(1.9, 2.2);
    box(b, -w / 2, 0, -d / 2, w / 2, h, d / 2, 0.1);
    box(l, -w / 2 - 0.08, h, -d / 2 - 0.08, w / 2 + 0.08, h + 0.75, d / 2 + 0.08, 0.1);
    for (const s of [-1, 1]) box(g, -0.15, h + 0.7, s > 0 ? 1.15 : -1.4, 0.15, h + 1.5, s > 0 ? 1.4 : -1.15);
    cylZ(g, 0, h + 1.55, 0.2, -1.3, 1.3, 8);
    done(b, l, g);
    return h + 1.75;
  }
  // a boot: sole, rounded foot, shaft at the heel (toe toward -z)
  function boot(bone, tf, mat) {
    const s = part(bone, 'rubber', tf), u = part(bone, mat, tf);
    box(s, -1.0, 0, -2.4, 1.0, 0.45, 2.4, 0.12);
    box(u, -0.9, 0.45, -2.25, 0.9, 2.1, 2.05, 0.55);
    box(u, -0.9, 2.0, 0.35, 0.9, 5.4, 2.3, 0.3);
    done(s, u);
  }
  function boots(bone, tf) {
    const mat = pick(['leather_tan', 'leather_dark']);
    for (const s of [-1, 1]) boot(bone, then(tf, frame({x: s * 1.1, z: range(-0.3, 0.3), yaw: range(-0.12, 0.12)})), mat);
    return 5.4;
  }
  // a sneaker: sole, rounded upper, heel collar
  function sneaker(bone, tf, mat) {
    const s = part(bone, 'sole_white', tf), u = part(bone, mat, tf);
    box(s, -0.9, 0, -2.2, 0.9, 0.55, 2.2, 0.15);
    box(u, -0.8, 0.55, -1.95, 0.8, 1.95, 1.75, 0.55);
    box(u, -0.8, 1.9, 0.55, 0.8, 2.7, 1.9, 0.3);
    done(s, u);
  }
  function sneakers(bone, tf) {
    const mat = pick(['sneaker_navy', 'sneaker_red', 'sneaker_grey']);
    for (const s of [-1, 1]) sneaker(bone, then(tf, frame({x: s * 1.0, z: range(-0.4, 0.4), yaw: range(-0.2, 0.2)})), mat);
    return 2.7;
  }
  // sports bag: a drum lying along z with darker ends and a carry strap over the top
  function sportsBag(bone, tf) {
    const mat = pick(['sports_blue', 'sports_black', 'sports_red']), r = range(1.7, 1.9), len = range(5.6, 6.2);
    const b = part(bone, mat, tf), e = part(bone, 'strap', tf);
    cylZ(b, 0, r, r, -len / 2, len / 2, 12, 0.3);
    for (const s of [-1, 1]) cylZ(e, 0, r, r + 0.05, s > 0 ? len / 2 - 0.5 : -len / 2 - 0.05, s > 0 ? len / 2 + 0.05 : -len / 2 + 0.5, 12);
    box(e, -0.25, 2 * r + 0.55, -1.4, 0.25, 2 * r + 0.75, 1.4);
    for (const s of [-1, 1]) box(e, -0.2, 2 * r - 0.2, s > 0 ? 1.0 : -1.35, 0.2, 2 * r + 0.7, s > 0 ? 1.35 : -1.0);
    done(b, e);
    return 2 * r + 0.75;
  }
  // basketball with two seams (raised bands)
  function basketball(bone, tf) {
    const R = 2.35, b = part(bone, 'ball_orange', tf), s = part(bone, 'seam_dark', tf);
    const prof = [[0, 0]];
    for (let i = 1; i < 8; i++) { const t = -Math.PI / 2 + Math.PI * i / 8; prof.push([R + R * Math.sin(t), R * Math.cos(t)]); }
    prof.push([2 * R, 0]);
    revolveY(b, prof, 12);
    bandY(s, R + 0.03, R - 0.07, R + 0.07, 16);
    const start = s.v.length;
    bandY(s, R + 0.03, -0.07, 0.07, 16);
    for (let i = start; i < s.v.length; i++) { const [x, y, z] = s.v[i]; s.v[i] = [x, z + R, -y]; }   // the second seam stands upright
    done(b, s);
    return 2 * R;
  }
  // backpack, its back on the frame's z: rounded body, front pocket (-z), top grab loop. Hanging (hookTop given; frame at y 0): the
  // bag hangs so that its loop, near its back, goes up over the hook to hookTop + 0.25
  function backpack(bone, tf, hookTop) {
    const body = pick(['pack_blue', 'pack_red', 'pack_green', 'pack_black']);
    const w = range(3.6, 4.0), h = range(4.8, 5.3), d = range(2.0, 2.3);
    // the back (local z = +d / 2) on the frame's z: the caller puts the frame at the wall
    const at = then(tf, frame({y: hookTop === undefined ? 0 : hookTop + 0.25 - h - 1.2, z: -d / 2}));
    const b = part(bone, body, at), k = part(bone, pick(['pack_black', 'pack_blue', 'pack_red'].filter(m => m !== body)), at), s = part(bone, 'strap', at);
    box(b, -w / 2, 0, -d / 2, w / 2, h, d / 2, 0.6);
    box(k, -w / 2 + 0.45, 0.4, -d / 2 - 0.8, w / 2 - 0.45, h * 0.48, -d / 2 + 0.3, 0.3);
    if (hookTop !== undefined) box(s, -0.35, h - 0.3, d / 2 - 0.85, 0.35, h + 1.2, d / 2 - 0.55);
    else box(s, -0.6, h - 0.2, -0.15, 0.6, h + 0.45, 0.15);
    done(b, k, s);
    return h;
  }
  // a stack of books lying flat: covers and spine in the cover colour, the page block a little inset
  function books(bone, tf) {
    const n = 3 + Math.floor(rnd() * 2);
    const mats = ['book_navy', 'book_maroon', 'book_olive', 'book_mustard', 'book_grey'];
    let y = 0;
    for (let i = 0; i < n; i++) {
      const w = range(3.4, 4.0), d = range(2.6, 3.1), t = range(0.5, 0.75), yaw = range(-0.25, 0.25), m = mats.splice(Math.floor(rnd() * mats.length), 1)[0];
      const c = part(bone, m, then(tf, frame({x: range(-0.3, 0.3), y, z: range(-0.3, 0.3), yaw})));
      const pgs = part(bone, 'pages', c.tf);
      box(c, -w / 2, 0, -d / 2, w / 2, 0.12, d / 2);                       // back cover
      box(c, -w / 2, t - 0.12, -d / 2, w / 2, t, d / 2);                   // front cover
      box(c, -w / 2 - 0.01, 0.1, -d / 2 + 0.01, -w / 2 + 0.12, t - 0.1, d / 2 - 0.01);   // spine
      box(pgs, -w / 2 + 0.1, 0.11, -d / 2 + 0.12, w / 2 - 0.12, t - 0.11, d / 2 - 0.12);
      done(c, pgs);
      y += t + 0.01;
    }
    return y;
  }
  // hard hat: revolved shell with brim; hanging: the crown points out of the wall (local -z)
  const HAT = [[0, 0], [0, 2.55], [0.22, 2.55], [0.3, 2.05], [1.1, 2.0], [1.9, 1.72], [2.5, 1.12], [2.75, 0.5], [2.8, 0]];
  function hardHat(bone, tf, hanging) {
    const p = part(bone, pick(['helmet_yellow', 'helmet_white', 'helmet_orange']), tf), k = hanging ? 0.95 : 1;
    revolveY(p, HAT.map(([h, r]) => [h * k, r * k]), 12);
    if (hanging) p.v = p.v.map(([x, y, z]) => [x, z, -y]);   // crown (+y) out of the wall (-z)
    done(p);
    return 2.8 * k;
  }
  // thermos and lunch box side by side
  function thermosLunch(bone, tf) {
    const t = part(bone, pick(['thermos_green', 'thermos_red', 'thermos_blue']), tf), c = part(bone, 'cap_dark', tf);
    const l = part(bone, pick(['lunch_blue', 'lunch_red']), tf), g = part(bone, 'grip_black', tf);
    const r = range(0.72, 0.8), th = range(3.4, 3.9), tx = -1.55;
    cylY(t, tx, 0, r, 0, th, 12, 0.15);
    cylY(c, tx, 0, r + 0.06, th - 0.1, th + 0.65, 12, 0.1);
    const lw = range(2.8, 3.1), ld = range(1.9, 2.2), lh = range(1.9, 2.2), lx = -0.6;
    box(l, lx, 0, -ld / 2, lx + lw, lh, ld / 2, 0.25);
    box(g, lx + lw / 2 - 0.8, lh - 0.05, -0.15, lx + lw / 2 + 0.8, lh + 0.35, 0.15);
    done(t, c, l, g);
    return th + 0.65;
  }
  // garments on a wire hanger hooked over a rod along x at (rodY, rodZ): the hook, then the garment below it
  function hangerHook(bone, tf, rodY, rodZ) {
    const h = part(bone, 'hanger', tf);
    box(h, -0.1, rodY - 1.05, rodZ - 0.42, 0.1, rodY + 0.38, rodZ - 0.26);
    box(h, -0.1, rodY + 0.25, rodZ - 0.42, 0.1, rodY + 0.42, rodZ + 0.32);
    box(h, -3.2, rodY - 1.25, rodZ - 0.12, 3.2, rodY - 1.05, rodZ + 0.12);
    done(h);
  }
  function jacket(bone, tf, rodY, rodZ) {
    hangerHook(bone, tf, rodY, rodZ);
    const p = part(bone, pick(['jacket_navy', 'jacket_grey', 'jacket_olive']), tf), T = rodY - 1.0;
    const sw = range(4.2, 4.5), bw = range(3.1, 3.3), hem = T - range(10.0, 10.8), cuff = T - range(8.2, 8.8);
    slabZ(p, [[0, T - 0.9], [0.9, T], [3.9, T - 0.9], [sw, cuff], [bw, cuff], [bw, hem], [-bw, hem], [-bw, cuff], [-sw, cuff], [-3.9, T - 0.9], [-0.9, T]],
      rodZ - 1.2, rodZ + 1.2, 0.4);
    done(p);
  }
  function vest(bone, tf, rodY, rodZ) {
    hangerHook(bone, tf, rodY, rodZ);
    const p = part(bone, 'vest_orange', tf), r = part(bone, 'reflective', tf), T = rodY - 1.0, hem = T - range(9.4, 9.8);
    slabZ(p, [[0, T - 2.8], [0.9, T], [2.6, T - 0.5], [2.8, T - 2.0], [3.6, T - 3.6], [3.6, hem], [-3.6, hem], [-3.6, T - 3.6], [-2.8, T - 2.0], [-2.6, T - 0.5], [-0.9, T]],
      rodZ - 0.5, rodZ + 0.5, 0.2);
    for (const y of [T - 6.2, T - 8.4]) box(r, -3.62, y, rodZ - 0.56, 3.62, y + 0.6, rodZ - 0.44);
    done(p, r);
  }
  // pair of work gloves hanging by the cuffs from a hook (palms against the wall)
  function gloves(bone, tf, hookY, wallZ) {
    const mat = pick(['glove_tan', 'glove_grey']);
    for (let i = 0; i < 2; i++) {
      const p = part(bone, mat, then(tf, frame({x: i ? 0.55 : -0.2, y: i ? -0.6 : 0})));
      const z1 = wallZ - 0.1 - i * 0.62, z0 = z1 - 0.5, top = hookY + 0.35;
      box(p, -0.95, top - 1.2, z0 - 0.04, 0.95, top, z1 + 0.04);                 // cuff
      box(p, -0.85, top - 3.8, z0, 0.85, top - 1.15, z1);                         // palm
      box(p, -0.8, top - 5.6, z0 + 0.04, 0.8, top - 3.75, z1 - 0.04);             // fingers
      box(p, 0.85, top - 3.5, z0 + 0.06, 1.3, top - 2.2, z1 - 0.06);              // thumb
      done(p);
    }
  }
  return {range, pick, carton, cartons, holdall, tote, toolbox, boots, sneakers, sportsBag, basketball, backpack, books, hardHat, thermosLunch, jacket, vest, gloves};
}
