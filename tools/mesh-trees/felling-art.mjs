// Mesh Trees P2.1 (docs/rendering/mesh_trees_p2_felling.md, U): the felling assets, formal versions. Imported by
// build-tree-textures.mjs (which writes and --checks them); deterministic, no randomness beyond fixed seeds.
//   end grain 32 x 32   the stump top and the falling log's cut: bark rim, inner bark, sapwood, heartwood rings, pith,
//                       radial drying checks; white oak: thin pale sapwood, fine rings, light ray flecks; white pine:
//                       wide creamy sapwood, wider rings, amber resin beads
//   cut wood 32 x 32    the notch faces: fresh wood, fibres running out from the apex, axe facets across
//   log end 32 x 32     P2.2 (V): the log block's ends, over the whole block face: the rounded-square outline of the log
//                       with its bark rim, rings following the outline near the bark and turning round toward the pith
//   items 16 x 16       branches, leaf material: vanilla-style pixel art with dark edges, lit from the upper left, the
//                       trees' own bark and wood palettes (P2.2: the logs are blocks, their 2D icons are gone)
//   LabPBR _n / _s      bark, end grain, cut wood: normals from a smoothed height (gentle, no POM: height alpha 255),
//                       dielectric wood (F0 10 / 255), rough; leaves keep the shader pack's own leaf handling

export const OAK = {
  barkLight: [0xa8, 0xa2, 0x97], bark1: [0x9d, 0x97, 0x8c], bark2: [0x93, 0x8d, 0x83], bark3: [0x8a, 0x85, 0x7b],
  crack: [0x63, 0x5e, 0x56], deep: [0x50, 0x4c, 0x45], rim: [0x6c, 0x67, 0x5e],
  inner: [0x7b, 0x61, 0x48], sap: [0xdc, 0xc8, 0x9e], sapLate: [0xcf, 0xb8, 0x89], heart: [0xbb, 0x93, 0x5f], late: [0x9c, 0x75, 0x46],
  ray: [0xd6, 0xbe, 0x90], pith: [0x5e, 0x44, 0x29], check: [0x6e, 0x52, 0x33],
  cut: [0xd8, 0xc1, 0x93], cutGrain: [0xc4, 0xa7, 0x77], cutLight: [0xe4, 0xd0, 0xa6], cutEdge: [0xb2, 0x93, 0x63],
  leaf: [0x5c, 0x8e, 0x35], leafLight: [0x80, 0xb0, 0x4a], leafHi: [0x9c, 0xc6, 0x5e], leafDark: [0x3e, 0x68, 0x24], vein: [0xa6, 0xc2, 0x6e],
  twig: [0x7c, 0x6e, 0x5e], twigLight: [0x9a, 0x8b, 0x77], twigDark: [0x55, 0x4a, 0x3f], bud: [0x8a, 0x5e, 0x3a],
};
export const PINE = {
  barkLight: [0x86, 0x78, 0x6c], bark1: [0x7e, 0x71, 0x66], bark2: [0x74, 0x67, 0x5d], bark3: [0x6a, 0x5e, 0x55],
  crack: [0x41, 0x39, 0x34], deep: [0x30, 0x2a, 0x27], rim: [0x45, 0x3c, 0x36], rust: [0x86, 0x5f, 0x4a],
  inner: [0x8c, 0x5c, 0x3c], sap: [0xf0, 0xe4, 0xc3], sapLate: [0xe2, 0xd2, 0xa8], heart: [0xdc, 0xbb, 0x89], late: [0xbf, 0x95, 0x60],
  resin: [0xc6, 0x84, 0x2c], resinHi: [0xe9, 0xb4, 0x52], pith: [0x7e, 0x5c, 0x3a], check: [0x8a, 0x6a, 0x45],
  cut: [0xee, 0xe1, 0xbb], cutGrain: [0xdc, 0xc8, 0x9c], cutLight: [0xf6, 0xec, 0xcd], cutEdge: [0xc9, 0xb1, 0x82],
  needle: [0x5e, 0x94, 0x72], needleLight: [0x80, 0xb4, 0x92], needleHi: [0x9e, 0xc9, 0xad], needleDark: [0x3f, 0x6b, 0x52],
  twig: [0x6a, 0x5c, 0x4e], twigLight: [0x86, 0x77, 0x66], twigDark: [0x48, 0x3e, 0x35], sheath: [0x9a, 0x7a, 0x55],
};

export function rngOf(seed) {
  let s = BigInt(seed) & 0xFFFFFFFFFFFFFFFFn;
  return () => {
    s = (s + 0x9E3779B97F4A7C15n) & 0xFFFFFFFFFFFFFFFFn;
    let z = s;
    z = ((z ^ (z >> 30n)) * 0xBF58476D1CE4E5B9n) & 0xFFFFFFFFFFFFFFFFn;
    z = ((z ^ (z >> 27n)) * 0x94D049BB133111EBn) & 0xFFFFFFFFFFFFFFFFn;
    z = z ^ (z >> 31n);
    return Number(z >> 11n) / 9007199254740992;
  };
}

export function canvas(W, H) { return {W, H, v: new Float32Array(W * H), a: new Uint8Array(W * H), rgb: new Uint8Array(W * H * 3)}; }
export function put(img, x, y, c) {
  if (x < 0 || y < 0 || x >= img.W || y >= img.H) return;
  img.rgb.set(c.map(v => Math.max(0, Math.min(255, Math.round(v)))), (y * img.W + x) * 3);
  img.a[y * img.W + x] = 1;
}
export function getc(img, x, y) { const i = (y * img.W + x) * 3; return [img.rgb[i], img.rgb[i + 1], img.rgb[i + 2]]; }
export const mul = (c, k) => c.map(v => v * k);
export const mix = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);
export const filled = (img, x, y) => x >= 0 && y >= 0 && x < img.W && y < img.H && img.a[y * img.W + x] === 1;

// ----------------------------------------------------------------------------------------- wood profiles

/**
 * A species' wood as the end grain, the log end and the cut show it: the stump cap's rim / inner bark / sapwood radii
 * (end grain, px of 32), the log end's rounded-square corner radius R (= LogShape.radius x 32) and its band widths
 * (rim, inner bark, sapwood, px), ring spacing and latewood share, drying checks (1 or 2), oak ray flecks, pine resin.
 * P3 adds the four new species (species-art.mjs); white oak's and white pine's values are P2.1's.
 */
export const OAK_WOOD = {R: 6, rimR: 14.0, innerR: 13.4, sapR: 11.9, rimW: 1.1, innerW: 0.6, sapW: 2.2, spacing: 2.15, lateShare: 0.4, checks: 1, rays: true, resin: false};
export const PINE_WOOD = {R: 10, rimR: 13.3, innerR: 12.7, sapR: 9.0, rimW: 1.7, innerW: 0.8, sapW: 4.2, spacing: 2.75, lateShare: 0.34, checks: 2, rays: false, resin: true};

// ------------------------------------------------------------------------------------------------- end grain

/**
 * 32 x 32, centred: the cap's UVs put the trunk's axis at the centre and its bark edge at radius 0.476 of the sprite
 * (15.2 px), so the bark rim fills the outermost 1.2-2 px and everything beyond (never seen) stays bark.
 */
export function endGrain(p, w, seed) {
  const W = 32, r = rngOf(seed), img = canvas(W, W);
  const {rimR, innerR, sapR, spacing, lateShare} = w;
  const ph1 = r() * 6.283, ph2 = r() * 6.283;
  const checks = [r() * 6.283, ...(w.checks > 1 ? [r() * 6.283] : [])].map(a => ({a, len: 7 + r() * 3, bend: (r() - 0.5) * 0.08}));
  const rays = Array.from({length: 7}, (_, k) => ({a: (k + r() * 0.6) * 6.283 / 7, from: 3 + r() * 2, to: 6.5 + r() * 3}));
  const resin = w.resin ? Array.from({length: 4}, () => ({a: r() * 6.283, d: 8.2 + r() * 3.6})) : [];
  for (let y = 0; y < W; y++) for (let x = 0; x < W; x++) {
    const dx = x + 0.5 - 16, dy = y + 0.5 - 16, d = Math.hypot(dx, dy), a = Math.atan2(dy, dx);
    const wob = 0.42 * Math.sin(3 * a + ph1) + 0.22 * Math.sin(5 * a + ph2);
    const dw = d + wob;
    let c;
    if (d >= rimR) c = d >= rimR + 1 ? mul(p.rim, 0.9) : p.rim;                              // bark rim
    else if (d >= innerR) c = p.inner;
    else if (dw >= sapR) c = ((dw / spacing) % 1) < lateShare * 0.6 ? p.sapLate : p.sap;     // sapwood: faint rings
    else if (d < 1.1) c = p.pith;
    else c = ((dw / spacing) % 1) < lateShare ? p.late : p.heart;
    // a gentle light-to-dark across the cap (the cut is never quite flat)
    if (d < innerR) c = mul(c, 1.03 - 0.06 * ((x + y) / (2 * W)));
    // oaks: ray flecks out from the pith, broken
    if (w.rays) for (const ray of rays) {
      const off = Math.abs(Math.atan2(Math.sin(a - ray.a), Math.cos(a - ray.a))) * d;
      if (d > ray.from && d < ray.to && off < 0.5) c = mix(c, p.ray, 0.55);
    }
    // radial drying checks from the pith
    for (const ch of checks) {
      const aa = ch.a + ch.bend * d;
      const off = Math.abs(Math.atan2(Math.sin(a - aa), Math.cos(a - aa))) * d;
      if (d > 1.4 && d < ch.len && off < 0.55) c = p.check;
    }
    // white pine: resin beads in the sapwood
    for (const rs of resin) {
      const rx = 16 + Math.cos(rs.a) * rs.d, ry = 16 + Math.sin(rs.a) * rs.d;
      const q = Math.hypot(x + 0.5 - rx, y + 0.5 - ry);
      if (q < 1.15) c = q < 0.6 ? p.resinHi : p.resin;
    }
    put(img, x, y, c);
  }
  return img;
}

// ---------------------------------------------------------------------------------------------------- log end

/**
 * 32 x 32 over the whole block face (the cap's UVs are the block's own coordinates). The log's outline is the rounded
 * square of LogShape (corner radius 6 px white oak, 10 px white pine: radius x 32); outside it (never drawn) bark. From
 * the outline in: bark rim, inner bark, sapwood (bands following the outline), then heartwood rings whose shape turns
 * from the outline's near the sapwood to round at the pith (a squared trunk: vanilla's square log rings, softened).
 */
export function logEnd(p, w, seed) {
  const W = 32, R = w.R, h = 16 - R, r = rngOf(seed), img = canvas(W, W);
  const {rimW, innerW, sapW, spacing, lateShare} = w, heartE = rimW + innerW + sapW;
  const ph1 = r() * 6.283, ph2 = r() * 6.283;
  const depth = (dx, dy) => {
    const qx = Math.abs(dx) - h, qy = Math.abs(dy) - h;
    return -(Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - R);
  };
  const checks = [r() * 6.283, ...(w.checks > 1 ? [r() * 6.283] : [])].map(a => ({a, len: 8 + r() * 3, bend: (r() - 0.5) * 0.08}));
  const rays = Array.from({length: 8}, (_, k) => ({a: (k + r() * 0.6) * 6.283 / 8, from: 3 + r() * 2, to: 7.5 + r() * 3}));
  const resin = [];
  while (w.resin && resin.length < 4) {
    const x = 2 + r() * 28, y = 2 + r() * 28, e = depth(x - 16, y - 16);
    if (e > rimW + innerW + 0.9 && e < heartE - 0.9) resin.push([x, y]);
  }
  for (let y = 0; y < W; y++) for (let x = 0; x < W; x++) {
    const dx = x + 0.5 - 16, dy = y + 0.5 - 16, d = Math.hypot(dx, dy), a = Math.atan2(dy, dx), e = depth(dx, dy);
    // ring radius: round near the pith, the outline's rounded square toward the bark
    const sq = 16 - e, t = Math.max(0, Math.min(1, (sq - 3) / 10)), s = t * t * (3 - 2 * t);
    const rho = d + (sq - d) * s;
    const rw = rho + 0.42 * Math.sin(3 * a + ph1) + 0.22 * Math.sin(5 * a + ph2);
    let c;
    if (e < rimW) c = e < 0 ? mul(p.rim, 0.9) : p.rim;                                         // bark rim
    else if (e < rimW + innerW) c = p.inner;
    else if (e < heartE) c = ((e / spacing) % 1) < lateShare * 0.6 ? p.sapLate : p.sap;       // sapwood: faint bands
    else if (d < 1.1) c = p.pith;
    else c = ((rw / spacing) % 1) < lateShare ? p.late : p.heart;
    // a gentle light-to-dark across the end (the saw cut is never quite flat)
    if (e >= rimW + innerW) c = mul(c, 1.03 - 0.06 * ((x + y) / (2 * W)));
    // oaks: ray flecks out from the pith, broken
    if (w.rays) for (const ray of rays) {
      const off = Math.abs(Math.atan2(Math.sin(a - ray.a), Math.cos(a - ray.a))) * d;
      if (d > ray.from && d < ray.to && off < 0.5) c = mix(c, p.ray, 0.55);
    }
    // radial drying checks from the pith
    for (const ch of checks) {
      const aa = ch.a + ch.bend * d;
      const off = Math.abs(Math.atan2(Math.sin(a - aa), Math.cos(a - aa))) * d;
      if (d > 1.4 && d < ch.len && off < 0.55) c = p.check;
    }
    // white pine: resin beads in the sapwood
    for (const [rx, ry] of resin) {
      const q = Math.hypot(x + 0.5 - rx, y + 0.5 - ry);
      if (q < 1.15) c = q < 0.6 ? p.resinHi : p.resin;
    }
    put(img, x, y, c);
  }
  return img;
}

// --------------------------------------------------------------------------------------------------- cut wood

/**
 * 32 x 32: u across the notch, v from the bark (0 / 1) to the apex (0.5). Axe facets are bands across (each stroke's
 * edge leaves a ridge parallel to the apex), the wood's fibres run along v (the grain crosses the slanted face).
 */
export function cutWood(p, w, seed) {
  const W = 32, r = rngOf(seed), img = canvas(W, W);
  const bands = [];
  for (let y = 0; y < W;) { const h = 4 + Math.floor(r() * 4); bands.push([y, Math.min(W, y + h), r()]); y += h; }
  const fibres = Array.from({length: 6}, () => ({x: r() * W, wob: r() * 6.283, gap: r() * 9}));
  const resin = w.resin ? [[r() * W, r() * W], [r() * W, r() * W]] : [];
  for (const [y0, y1, k] of bands) for (let y = y0; y < y1; y++) for (let x = 0; x < W; x++) {
    let c = mix(p.cut, p.cutLight, 0.1 + 0.55 * k);
    if (y === y0 && y0 > 0) c = p.cutEdge;                                        // the ridge left by a stroke
    else if (y === y0 + 1 && y0 > 0) c = mix(c, p.cutLight, 0.45);
    for (const f of fibres) if (Math.abs(x + 0.5 - f.x - 0.25 * Math.sin(y * 0.3 + f.wob)) < 0.5 && (y + f.gap) % 11 < 7) c = mix(c, p.cutGrain, 0.5);
    // toward the apex (v 0.5) a little darker, as it is shaded inside the notch
    c = mul(c, 1 - 0.1 * (1 - Math.abs(y + 0.5 - 16) / 16));
    for (const [rx, ry] of resin) if (Math.abs(x + 0.5 - rx) < 0.5 && Math.abs(y + 0.5 - ry) < 1.6) c = p.resin;
    put(img, x, y, c);
  }
  return img;
}

// ------------------------------------------------------------------------------------------------ item icons

/** Edge pixels darker (more on the lower right), the vanilla item outline. */
export function outline(img) {
  const out = [];
  for (let y = 0; y < img.H; y++) for (let x = 0; x < img.W; x++) {
    if (!filled(img, x, y)) continue;
    const lowerRight = !filled(img, x + 1, y) || !filled(img, x, y + 1);
    const upperLeft = !filled(img, x - 1, y) || !filled(img, x, y - 1);
    if (lowerRight) out.push([x, y, 0.58]); else if (upperLeft) out.push([x, y, 0.78]);
  }
  for (const [x, y, k] of out) put(img, x, y, mul(getc(img, x, y), k));
}

/** Fills discs along a polyline (thickness tapering from r0 to r1 pixels radius). */
export function limb(img, pts, r0, r1, colour) {
  let total = 0;
  for (let i = 1; i < pts.length; i++) total += Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]);
  let run = 0;
  for (let i = 1; i < pts.length; i++) {
    const [x0, y0] = pts[i - 1], [x1, y1] = pts[i], len = Math.hypot(x1 - x0, y1 - y0);
    for (let k = 0; k <= len * 8; k++) {
      const u = k / (len * 8), cx = x0 + (x1 - x0) * u, cy = y0 + (y1 - y0) * u, rad = r0 + (r1 - r0) * (run + len * u) / total;
      for (let y = Math.floor(cy - rad - 1); y <= cy + rad + 1; y++) for (let x = Math.floor(cx - rad - 1); x <= cx + rad + 1; x++)
        if (Math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= rad) put(img, x, y, typeof colour === 'function' ? colour(x, y, cx, cy) : colour);
    }
    run += len;
  }
}

/** A small broadleaf blob (white oak, lobed edge) centred at (cx, cy), pointing along angle a. */
function smallLeaf(img, p, cx, cy, a, len) {
  const ca = Math.cos(a), sa = Math.sin(a);
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const dx = x + 0.5 - cx, dy = y + 0.5 - cy, t = dx * ca + dy * sa, s = -dx * sa + dy * ca;
    const u = t / len;
    if (u < -0.5 || u > 0.5) continue;
    const half = len * 0.36 * Math.sqrt(Math.max(0, 1 - (2 * u) ** 2)) * (0.75 + 0.25 * Math.abs(Math.cos(u * 9)));
    if (Math.abs(s) > half + 0.15) continue;
    put(img, x, y, Math.abs(s) < 0.45 && u > -0.4 ? p.vein : s < 0 ? p.leafLight : p.leaf);
  }
}

export function branchIcon(p, pine) {
  const img = canvas(16, 16);
  const shade = (x, y, cx, cy) => (x + 0.5 - cx) + (y + 0.5 - cy) < -0.3 ? p.twigLight : (x + 0.5 - cx) + (y + 0.5 - cy) > 0.5 ? p.twigDark : p.twig;
  if (pine) {
    // a slender limb with soft needle tufts at its tips and along it
    const tufts = [[13.0, 2.9, -0.8], [10.4, 8.0, -0.15], [6.6, 6.2, -1.95]];
    for (const [tx, ty, dir] of tufts) for (let n = 0; n < 5; n++) {
      const ang = dir + (n - 2) * 0.42, len = 3.6 + (n % 2) * 0.8;
      seg(img, tx, ty, tx + Math.cos(ang) * len, ty + Math.sin(ang) * len, n % 2 ? p.needle : p.needleLight, p.needleHi);
    }
    limb(img, [[1.5, 14.5], [5.5, 10.8], [9.0, 7.3], [13.2, 2.8]], 1.0, 0.45, shade);
    limb(img, [[7.0, 9.0], [10.4, 8.2]], 0.55, 0.4, shade);
    limb(img, [[5.0, 11.4], [6.6, 6.2]], 0.55, 0.4, shade);
    for (const [x, y] of [[13, 2], [10, 8], [6, 6]]) put(img, x, y, p.sheath);
  } else {
    // a stout zigzag twig, clustered end buds, two lobed leaves
    smallLeaf(img, p, 12.4, 9.6, 0.35, 5.6);
    smallLeaf(img, p, 3.8, 5.0, -2.2, 5.2);
    limb(img, [[1.5, 14.5], [5.2, 11.4], [7.4, 9.9], [9.4, 6.6], [12.6, 3.6], [13.6, 1.8]], 1.05, 0.5, shade);
    limb(img, [[7.4, 9.9], [10.2, 9.6]], 0.6, 0.45, shade);
    limb(img, [[5.2, 11.4], [4.6, 7.6]], 0.6, 0.45, shade);
    for (const [x, y] of [[13, 1], [14, 2], [13, 2]]) put(img, x, y, p.bud);
  }
  outline(img);
  return img;
}

/** A white oak leaf: rounded lobes with deep sinuses, the tip up, the stalk down, midrib and side veins. */
export function oakLeafIcon(p) {
  const img = canvas(16, 16);
  const lobes = [[7.5, 2.6, 2.0], [4.4, 5.0, 1.75], [10.6, 5.0, 1.75], [3.6, 9.1, 1.9], [11.4, 9.1, 1.9], [5.0, 12.7, 1.35], [10.0, 12.7, 1.35]];
  const inLeaf = (x, y) => {
    const px = x + 0.5, py = y + 0.5;
    if (((px - 7.5) / 2.1) ** 2 + ((py - 8.3) / 6.4) ** 2 <= 1) return true;
    return lobes.some(([cx, cy, r]) => Math.hypot(px - cx, py - cy) <= r);
  };
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) if (inLeaf(x, y)) {
    const px = x + 0.5;
    let c = px < 7.5 ? p.leafLight : p.leaf;
    if (px < 5.2 && y < 9) c = p.leafHi;
    put(img, x, y, c);
  }
  // veins: midrib, and one to each lobe
  for (let y = 2; y <= 14; y++) if (filled(img, 7, y)) put(img, 7, y, p.vein);
  for (const [cx, cy] of [[4.4, 5.0], [10.6, 5.0], [3.6, 9.1], [11.4, 9.1], [5.0, 12.7], [10.0, 12.7]]) {
    const y0 = cy + 1.6, n = 6;
    for (let k = 1; k < n; k++) {
      const x = Math.floor(7.5 + (cx - 7.5) * k / n), y = Math.floor(y0 + (cy - y0) * k / n);
      if (filled(img, x, y) && x !== 7) put(img, x, y, mix(getc(img, x, y), p.vein, 0.6));
    }
  }
  outline(img);
  put(img, 7, 15, p.twig); put(img, 6, 15, p.twigDark);
  return img;
}

/** White pine needles: two fans of long, straight, soft needles from short sheathed bases, tips lighter. */
export function needleIcon(p) {
  const img = canvas(16, 16);
  const fans = [[2.6, 13.4, -1.05, 5], [4.4, 14.2, -0.5, 4]];
  let n = 0;
  for (const [bx, by, dir, count] of fans) for (let k = 0; k < count; k++, n++) {
    const ang = dir + (k - (count - 1) / 2) * 0.21, len = 9.6 + ((n * 5) % 4) * 0.6;
    seg(img, bx, by, bx + Math.cos(ang) * len, by + Math.sin(ang) * len, k % 3 === 0 ? p.needleDark : k % 3 === 1 ? p.needle : p.needleLight, p.needleHi);
  }
  limb(img, [[1.0, 15.4], [2.6, 13.6]], 0.6, 0.5, p.twig);
  limb(img, [[3.2, 15.4], [4.4, 14.2]], 0.55, 0.5, p.twig);
  put(img, 2, 13, p.sheath); put(img, 4, 14, p.sheath);
  return img;
}

/** A one-pixel line (Bresenham) from (x0, y0) to (x1, y1); its last two pixels in the tip colour. */
export function seg(img, x0, y0, x1, y1, colour, tip) {
  let x = Math.floor(x0), y = Math.floor(y0);
  const xe = Math.floor(x1), ye = Math.floor(y1), dx = Math.abs(xe - x), dy = -Math.abs(ye - y), sx = x < xe ? 1 : -1, sy = y < ye ? 1 : -1;
  const pts = [];
  let err = dx + dy;
  for (;;) {
    pts.push([x, y]);
    if (x === xe && y === ye) break;
    const e2 = 2 * err;
    if (e2 >= dy) { err += dy; x += sx; }
    if (e2 <= dx) { err += dx; y += sy; }
  }
  pts.forEach(([px, py], i) => put(img, px, py, i >= pts.length - 2 ? tip : colour));
}

// ---------------------------------------------------------------------------------------------------- LabPBR

/** Luminance 0..1 of an RGB image (the base texture). */
function lum(img) {
  const h = new Float32Array(img.W * img.H);
  for (let i = 0; i < h.length; i++) h[i] = (0.3 * img.rgb[i * 3] + 0.59 * img.rgb[i * 3 + 1] + 0.11 * img.rgb[i * 3 + 2]) / 255;
  return h;
}

function blur(h, W, H, wrap) {
  const o = new Float32Array(h.length);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    let s = 0, n = 0;
    for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) {
      let xx = x + dx, yy = y + dy;
      if (wrap) { xx = (xx + W) % W; yy = (yy + H) % H; } else if (xx < 0 || yy < 0 || xx >= W || yy >= H) continue;
      const w = dx === 0 && dy === 0 ? 2 : 1;
      s += h[yy * W + xx] * w; n += w;
    }
    o[y * W + x] = s / n;
  }
  return o;
}

/**
 * LabPBR normal map: R, G the normal's x, y (128 flat), B ambient occlusion (255 none), A height (255: flat, so a
 * shader pack's parallax does not displace the curved mesh). Height from the base texture's luminance (dark fissures,
 * rings and checks low), smoothed once so no single texel stands out, at a gentle strength.
 */
export function normalMap(base, strength, wrap, aoDepth) {
  const W = base.W, H = base.H, h = blur(lum(base), W, H, wrap), img = canvas(W, H);
  let lo = 1, hi = 0;
  for (const v of h) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
  const at = (x, y) => {
    if (wrap) { x = (x + W) % W; y = (y + H) % H; } else { x = Math.max(0, Math.min(W - 1, x)); y = Math.max(0, Math.min(H - 1, y)); }
    return (h[y * W + x] - lo) / Math.max(1e-4, hi - lo);
  };
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const dx = (at(x + 1, y) - at(x - 1, y)) * strength, dy = (at(x, y + 1) - at(x, y - 1)) * strength;
    const l = Math.hypot(dx, dy, 1), nx = -dx / l, ny = -dy / l;
    const ao = 255 - aoDepth * (1 - at(x, y));
    put(img, x, y, [128 + nx * 127, 128 + ny * 127, ao]);
  }
  return img;
}

/** LabPBR specular: R perceptual smoothness, G F0 (10: about 4 %, dielectric), B porosity (0..64), A emission 255 (none). */
export function specularMap(base, smoothLo, smoothHi, porosity, wrap) {
  const W = base.W, H = base.H, h = blur(lum(base), W, H, wrap), img = canvas(W, H);
  let lo = 1, hi = 0;
  for (const v of h) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
  for (let i = 0; i < h.length; i++) {
    const t = (h[i] - lo) / Math.max(1e-4, hi - lo);
    put(img, i % W, Math.floor(i / W), [smoothLo + (smoothHi - smoothLo) * t, 10, porosity]);
  }
  return img;
}
