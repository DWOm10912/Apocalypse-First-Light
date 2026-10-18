// Terrain raster for the town sheets: a TownSurvey grid (data/<id>.json) drawn as a hillshaded, height-tinted PNG
// (data: URI), water blue; plus the grid's world-to-pixel helpers. No dependencies (zlib for the PNG).
import fs from 'fs';
import zlib from 'zlib';

export function loadGrid(id) {
  const d = JSON.parse(fs.readFileSync(new URL(`./data/${id}.json`, import.meta.url), 'utf8'));
  d.H = (i, j) => d.h[Math.max(0, Math.min(d.n - 1, j)) * d.n + Math.max(0, Math.min(d.n - 1, i))] / 10;
  d.W = (i, j) => d.w[Math.max(0, Math.min(d.n - 1, j)) * d.n + Math.max(0, Math.min(d.n - 1, i))];
  // world (x, z) to grid (fractional i, j) and the height there (bilinear)
  d.ij = (x, z) => [(x - (d.x - d.half)) / d.cell, (z - (d.z - d.half)) / d.cell];
  d.at = (x, z) => {
    const [fi, fj] = d.ij(x, z), i = Math.floor(fi), j = Math.floor(fj), u = fi - i, v = fj - j;
    return (d.H(i, j) * (1 - u) + d.H(i + 1, j) * u) * (1 - v) + (d.H(i, j + 1) * (1 - u) + d.H(i + 1, j + 1) * u) * v;
  };
  d.wet = (x, z) => { const [fi, fj] = d.ij(x, z); return d.W(Math.round(fi), Math.round(fj)); };
  return d;
}

const CRC = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c >>> 0;
  }
  return b => { let c = 0xffffffff; for (const x of b) c = t[(c ^ x) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
})();

function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(CRC(td));
  return Buffer.concat([len, td, crc]);
}

/** RGB pixels (Uint8Array w*h*3) to a PNG data URI. */
export function png(w, h, rgb) {
  const raw = Buffer.alloc((w * 3 + 1) * h);
  for (let y = 0; y < h; y++) {
    raw[y * (w * 3 + 1)] = 0;
    rgb.subarray(y * w * 3, (y + 1) * w * 3).forEach((v, k) => { raw[y * (w * 3 + 1) + 1 + k] = v; });
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 2;
  const buf = Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
  return 'data:image/png;base64,' + buf.toString('base64');
}

/**
 * The hillshade of world window [x0, x1] x [z0, z1] at px pixels per metre (north up: z0 at the top), muted for a
 * plan background; levelled(x, z) may return a finished height (a town terrace) or NaN for the natural ground.
 */
export function shade(d, x0, z0, x1, z1, px, levelled = () => NaN) {
  const w = Math.round((x1 - x0) * px), h = Math.round((z1 - z0) * px), rgb = new Uint8Array(w * h * 3);
  const lx = -0.6, lz = -0.6, ly = 0.55, ll = Math.hypot(lx, ly, lz);
  const hAt = (x, z) => { const l = levelled(x, z); return Number.isNaN(l) ? d.at(x, z) : l; };
  for (let py = 0; py < h; py++)
    for (let pxl = 0; pxl < w; pxl++) {
      const x = x0 + (pxl + 0.5) / px, z = z0 + (py + 0.5) / px, e = 4;
      const gx = (hAt(x + e, z) - hAt(x - e, z)) / (2 * e), gz = (hAt(x, z + e) - hAt(x, z - e)) / (2 * e);
      const nx = -gx, ny = 1, nz = -gz, nl = Math.hypot(nx, ny, nz);
      const lit = Math.max(0, (nx * lx + ny * ly + nz * lz) / (nl * ll));
      const k = (py * w + pxl) * 3, wet = d.wet(x, z);
      if (wet) { rgb[k] = 158; rgb[k + 1] = 194; rgb[k + 2] = 214; continue; }
      const hh = hAt(x, z), t = Math.max(0, Math.min(1, (hh - 90) / 130));
      // low green-grey to high warm grey, then the light
      const base = [222 - 18 * t, 228 - 26 * t, 208 - 14 * t], f = 0.72 + 0.34 * lit;
      rgb[k] = Math.min(255, base[0] * f); rgb[k + 1] = Math.min(255, base[1] * f); rgb[k + 2] = Math.min(255, base[2] * f);
    }
  return { w, h, uri: png(w, h, rgb) };
}

/** Contour polylines every step m over the window, as SVG path data in sheet coordinates (P maps world x, z). */
export function contours(d, x0, z0, x1, z1, step, cell, P) {
  const nx = Math.ceil((x1 - x0) / cell), nz = Math.ceil((z1 - z0) / cell);
  const v = (i, j) => d.at(x0 + i * cell, z0 + j * cell);
  let out = '';
  const lo = Math.floor(Math.min(...[0, nx].flatMap(i => [0, nz].map(j => v(i, j)))) / step) * step - 200;
  const levels = new Set();
  for (let j = 0; j <= nz; j++) for (let i = 0; i <= nx; i++) levels.add(Math.floor(v(i, j) / step) * step);
  for (const L0 of levels) {
    const L = L0 + step;
    if (L < lo) continue;
    for (let j = 0; j < nz; j++)
      for (let i = 0; i < nx; i++) {
        const c = [[i, j], [i + 1, j], [i + 1, j + 1], [i, j + 1]], hv = c.map(([a, b]) => v(a, b)), pts = [];
        for (let e = 0; e < 4; e++) {
          const a = hv[e], b = hv[(e + 1) % 4];
          if ((a < L) !== (b < L)) {
            const t = (L - a) / (b - a), p = c[e], q = c[(e + 1) % 4];
            pts.push(P(x0 + (p[0] + (q[0] - p[0]) * t) * cell, z0 + (p[1] + (q[1] - p[1]) * t) * cell));
          }
        }
        if (pts.length >= 2) out += `M${pts[0][0].toFixed(1)},${pts[0][1].toFixed(1)}L${pts[1][0].toFixed(1)},${pts[1][1].toFixed(1)}`;
        if (pts.length === 4) out += `M${pts[2][0].toFixed(1)},${pts[2][1].toFixed(1)}L${pts[3][0].toFixed(1)},${pts[3][1].toFixed(1)}`;
      }
  }
  return out;
}
