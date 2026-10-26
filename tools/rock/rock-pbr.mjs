// Rock PBR R1 (docs/worldgen/rock_pbr_v1.md): the LabPBR maps of the natural rock sprites, shared by the generators that
// make them (tools/boulders/build-boulders.mjs, build-rock-face.mjs, build-cut-textures.mjs, build-ore-textures.mjs).
//   relief(nrm, res, tilt)  -> the photo's relief made stronger: the normal map integrated to a height field (FFT, the
//                              tile wraps), scaled until 90% of its texels lean at most `tilt` degrees, differentiated
//                              again (central differences: the texel noise is softened, not amplified); and its ambient
//                              occlusion: how far a texel lies below its neighbourhood (crevices darker)
//   encodeN(nrm, ao, res)   -> the _n bytes: DirectX normal, B ambient occlusion, A 255
//   glints(s, n, res, ...)  -> mineral grains on a fresh break: crystal faces, each its own smoothness and tilt, written
//                              into the _s and _n bytes (feldspar, quartz and mica in granite, calcite spar ...)
//   tiltStats(nBytes, res)  -> the lean of an _n's texels (degrees, 50th and 90th percentile), for the reports
// A (height) stays 255 on all of them: the rock faces are drawn as a mesh of 1/8 m pieces (RockRelief, RockCarveMesh),
// each its own part of the sprite, and a shader's parallax walks within each piece's own rectangle: any depth there
// would show seams along every piece (rock_pbr_v1.md 3).

/** In-place radix-2 FFT of n complex values (re, im) at offset o with stride st; inverse when inv. */
function fft1(re, im, n, o, st, inv) {
  for (let i = 1, j = 0; i < n; i++) {
    let bit = n >> 1;
    for (; j & bit; bit >>= 1) j ^= bit;
    j ^= bit;
    if (i < j) {
      const a = o + i * st, b = o + j * st;
      [re[a], re[b]] = [re[b], re[a]];
      [im[a], im[b]] = [im[b], im[a]];
    }
  }
  for (let len = 2; len <= n; len <<= 1) {
    const ang = (inv ? 2 : -2) * Math.PI / len, wr = Math.cos(ang), wi = Math.sin(ang);
    for (let i = 0; i < n; i += len) {
      let cr = 1, ci = 0;
      for (let k = 0; k < len / 2; k++) {
        const a = o + (i + k) * st, b = o + (i + k + len / 2) * st;
        const xr = re[b] * cr - im[b] * ci, xi = re[b] * ci + im[b] * cr;
        re[b] = re[a] - xr; im[b] = im[a] - xi;
        re[a] += xr; im[a] += xi;
        [cr, ci] = [cr * wr - ci * wi, cr * wi + ci * wr];
      }
    }
  }
  if (inv) for (let k = 0; k < n; k++) { re[o + k * st] /= n; im[o + k * st] /= n; }
}

function fft2(re, im, res, inv) {
  for (let y = 0; y < res; y++) fft1(re, im, res, y * res, 1, inv);
  for (let x = 0; x < res; x++) fft1(re, im, res, x, res, inv);
}

const wrap = (v, n) => ((v % n) + n) % n;

/**
 * The height field (texel units, mean 0) whose central differences best match the normal map (OpenGL: x right, y up the
 * image), least squares over the wrapping tile: H = conj(D) . G / |D|^2 with D the central difference's i sin(k).
 */
export function heightOf(nrm, res) {
  const n = res * res, pr = new Float64Array(n), pi = new Float64Array(n), qr = new Float64Array(n), qi = new Float64Array(n);
  for (let i = 0; i < n; i++) {
    const nz = Math.max(nrm[i * 3 + 2], 0.2);
    pr[i] = -nrm[i * 3] / nz;      // dh / dx
    qr[i] = nrm[i * 3 + 1] / nz;   // dh / d(row): the normal's y points up the image, rows run down
  }
  fft2(pr, pi, res, false);
  fft2(qr, qi, res, false);
  const hr = new Float64Array(n), hi = new Float64Array(n);
  for (let v = 0; v < res; v++) for (let u = 0; u < res; u++) {
    const i = v * res + u, sx = Math.sin(2 * Math.PI * u / res), sy = Math.sin(2 * Math.PI * v / res), d = sx * sx + sy * sy;
    if (d < 1e-9) continue;
    // conj(i s) = -i s: (-i sx) P + (-i sy) Q
    hr[i] = (sx * pi[i] + sy * qi[i]) / d;
    hi[i] = -(sx * pr[i] + sy * qr[i]) / d;
  }
  fft2(hr, hi, res, true);
  return Float32Array.from(hr);
}

/** Normals (OpenGL) of height field h times g, central differences on the wrapping tile. */
function normalsOf(h, res, g) {
  const out = new Float32Array(res * res * 3);
  for (let y = 0; y < res; y++) for (let x = 0; x < res; x++) {
    const dx = (h[y * res + wrap(x + 1, res)] - h[y * res + wrap(x - 1, res)]) / 2;
    const dr = (h[wrap(y + 1, res) * res + x] - h[wrap(y - 1, res) * res + x]) / 2;
    const nx = -g * dx, ny = g * dr, l = Math.hypot(nx, ny, 1), i = y * res + x;
    out[i * 3] = nx / l; out[i * 3 + 1] = ny / l; out[i * 3 + 2] = 1 / l;
  }
  return out;
}

const percentile = (a, p) => { const s = Float32Array.from(a).sort(); return s[Math.min(s.length - 1, Math.floor(s.length * p))]; };
const leanDeg = (nrm, n) => Float32Array.from({length: n}, (_, i) => Math.acos(Math.min(1, nrm[i * 3 + 2])) * 180 / Math.PI);

/** Wrapping box blur, radius r, two passes each way. */
function blur(a, res, r) {
  let src = a;
  for (let p = 0; p < 4; p++) {
    const dst = new Float32Array(src.length), hor = p % 2 === 0;
    for (let y = 0; y < res; y++) for (let x = 0; x < res; x++) {
      let s = 0;
      for (let d = -r; d <= r; d++) s += hor ? src[y * res + wrap(x + d, res)] : src[wrap(y + d, res) * res + x];
      dst[y * res + x] = s / (2 * r + 1);
    }
    src = dst;
  }
  return src;
}

/**
 * The relief of a rock normal map (OpenGL unit normals, res x res, wrapping) made stronger: 90% of the texels lean at most
 * `tilt` degrees. ao: crevice depth below the neighbourhood (a 2 and a 6 texel radius) in texels, times aoGain, darkens
 * to at most aoMin. gain: a fixed scale instead of the one `tilt` asks for (a lichen sprite takes its plain sprite's, so the
 * rock under the crust is as rough). Returns {nrm, ao, gain, height (texels, scaled)}.
 */
export function relief(nrm, res, tilt, {aoGain = 0.5, aoMin = 0.5, gain = null} = {}) {
  const h = heightOf(nrm, res), n = res * res;
  let lo = 0.1, hi = 20;
  if (gain != null) lo = gain;
  else for (let it = 0; it < 40; it++) {
    const g = Math.sqrt(lo * hi);
    if (percentile(leanDeg(normalsOf(h, res, g), n), 0.9) > tilt) hi = g; else lo = g;
  }
  const g = lo, out = normalsOf(h, res, g), H = h.map(v => v * g);
  const near = blur(H, res, 2), far = blur(H, res, 6), ao = new Float32Array(n);
  for (let i = 0; i < n; i++) {
    const cav = 0.6 * Math.max(0, near[i] - H[i]) + 0.4 * Math.max(0, far[i] - H[i]);
    ao[i] = Math.max(aoMin, 1 - aoGain * cav);
  }
  return {nrm: out, ao, gain: g, height: H};
}

/** The _n bytes (RGBA) of OpenGL normals and AO: DirectX green, B AO, A 255 (no parallax: see the header). */
export function encodeN(nrm, ao, res) {
  const b = Buffer.alloc(res * res * 4);
  for (let i = 0; i < res * res; i++) {
    b[i * 4] = Math.round((nrm[i * 3] * 0.5 + 0.5) * 255);
    b[i * 4 + 1] = Math.round((-nrm[i * 3 + 1] * 0.5 + 0.5) * 255);
    b[i * 4 + 2] = Math.round(Math.max(0, Math.min(1, ao ? ao[i] : 1)) * 255);
    b[i * 4 + 3] = 255;
  }
  return b;
}

/** OpenGL unit normals decoded from _n bytes (RGBA, DirectX green). */
export function decodeN(bytes, res, bpp = 4) {
  const out = new Float32Array(res * res * 3);
  for (let i = 0; i < res * res; i++) {
    const x = bytes[i * bpp] / 127.5 - 1, y = -(bytes[i * bpp + 1] / 127.5 - 1), l = Math.hypot(x, y), z = Math.sqrt(Math.max(0, 1 - Math.min(1, l * l)));
    out[i * 3] = x; out[i * 3 + 1] = y; out[i * 3 + 2] = z;
    const m = Math.hypot(x, y, z) || 1;
    for (let k = 0; k < 3; k++) out[i * 3 + k] /= m;
  }
  return out;
}

/** 50th and 90th percentile lean (degrees) of an _n's texels. */
export function tiltStats(bytes, res, bpp = 4) {
  const lean = leanDeg(decodeN(bytes, res, bpp), res * res);
  return [percentile(lean, 0.5), percentile(lean, 0.9)];
}

function hash(i, j, s) {
  let h = (Math.imul(i, 374761393) + Math.imul(j, 668265263) + Math.imul(s, 1442695041)) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  h ^= h >>> 16;
  return (h >>> 0) / 4294967296;
}

/**
 * Mineral grains of a fresh break, written into _s and _n (RGBA bytes, res x res, both wrapping): each kind
 * {share: of the texels, smooth: [lo, hi] bytes, f0: byte, tilt: degrees (the cleavage face's lean, random direction;
 * with `along` the lean keeps to one direction, a foliation's mica), big: share of 2 x 2 grains (else 1 texel)}.
 * A grain is a crystal face: one smoothness and one normal over its texels, porosity 0. Returns the share written.
 */
export function glints(s, n, res, kinds, seed, along = null) {
  const taken = new Uint8Array(res * res);
  let written = 0;
  kinds.forEach((k, ki) => {
    // grains on cells of 2 x 2: a cell starts one with probability share / (texels per grain)
    const cells = res / 2, per = 1 + 3 * k.big;
    for (let cy = 0; cy < cells; cy++) for (let cx = 0; cx < cells; cx++) {
      if (hash(cx, cy, seed + 31 * ki) >= k.share * 4 / per) continue;
      const big = hash(cx, cy, seed + 31 * ki + 1) < k.big;
      const ox = cx * 2 + (hash(cx, cy, seed + 31 * ki + 2) < 0.5 ? 0 : 1), oy = cy * 2 + (hash(cx, cy, seed + 31 * ki + 3) < 0.5 ? 0 : 1);
      const texels = big ? [[0, 0], [1, 0], [0, 1], [1, 1]] : [[0, 0]];
      const at = texels.map(([dx, dy]) => wrap(oy + dy, res) * res + wrap(ox + dx, res));
      if (at.some(i => taken[i])) continue;
      const sm = Math.round(k.smooth[0] + (k.smooth[1] - k.smooth[0]) * hash(cx, cy, seed + 31 * ki + 4));
      const az = along ?? hash(cx, cy, seed + 31 * ki + 5) * 2 * Math.PI;
      const lean = k.tilt * Math.PI / 180 * Math.sqrt(hash(cx, cy, seed + 31 * ki + 6));
      for (const i of at) {
        taken[i] = 1;
        s[i * 4] = Math.max(s[i * 4], sm);
        s[i * 4 + 1] = k.f0;
        s[i * 4 + 2] = 0;
        // the face's normal: the rock's own, leaned by the cleavage
        const [x, y, z] = decodeN(n.subarray(i * 4, i * 4 + 4), 1);
        const tx = Math.sin(lean) * Math.cos(az), ty = Math.sin(lean) * Math.sin(az), l = Math.hypot(x + tx, y + ty, z);
        n[i * 4] = Math.round(((x + tx) / l * 0.5 + 0.5) * 255);
        n[i * 4 + 1] = Math.round((-(y + ty) / l * 0.5 + 0.5) * 255);
        written++;
      }
    }
  });
  return written / (res * res);
}
