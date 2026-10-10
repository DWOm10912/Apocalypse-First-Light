"""Shaded-relief panels for the Terrain V2 comparison, one renderer for real and AFL terrain (no PIL: own PNG writer).
Hillshade: Horn gradient, sun azimuth 315 deg, altitude 45 deg, no vertical exaggeration, true cell size.
Colours: a fixed hypsometric ramp over height ABOVE THE PANEL'S OWN 1st PERCENTILE (relative relief, never absolute
elevation), or fixed slope classes; open water flat blue. Identical rules for every panel, so panels are comparable.
  python -I render.py NPZ OUT.png [tint|slope] [array=z] [x0 y0 size (cells)] [channels]"""
import os
import struct
import sys
import zlib

import numpy as np

RAMP = [(0, (96, 132, 92)), (5, (120, 152, 98)), (15, (156, 176, 108)), (35, (200, 196, 128)), (70, (204, 172, 116)),
        (130, (176, 138, 102)), (200, (150, 124, 108)), (300, (196, 190, 186))]
SLOPE = [(1 / 32, (186, 214, 166)), (1 / 16, (226, 226, 150)), (1 / 8, (240, 194, 122)), (1 / 4, (222, 134, 92)),
         (1 / 2, (170, 82, 82)), (99, (104, 62, 92))]
WATER = (88, 128, 168)


def png(rgb, path):
    h, w, _ = rgb.shape
    raw = b''.join(b'\0' + rgb[r].tobytes() for r in range(h))
    def chunk(t, d):
        return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    data = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0)) + \
        chunk(b'IDAT', zlib.compress(raw, 6)) + chunk(b'IEND', b'')
    open(path, 'wb').write(data)


def shade(z, cell):
    p = np.pad(z.astype(np.float64), 1, mode='edge')
    a, b, c = p[:-2, :-2], p[:-2, 1:-1], p[:-2, 2:]
    d, f = p[1:-1, :-2], p[1:-1, 2:]
    g, h, i = p[2:, :-2], p[2:, 1:-1], p[2:, 2:]
    gx = ((c + 2 * f + i) - (a + 2 * d + g)) / (8 * cell)          # dz / d east
    gn = -((g + 2 * h + i) - (a + 2 * b + c)) / (8 * cell)         # dz / d north (rows run south)
    alt, az = np.radians(45), np.radians(315)
    L = np.array([np.cos(alt) * np.sin(az), np.cos(alt) * np.cos(az), np.sin(alt)])
    n = np.stack([-gx, -gn, np.ones_like(gx)])
    n /= np.linalg.norm(n, axis=0)
    hs = np.clip(L[0] * n[0] + L[1] * n[1] + L[2] * n[2], 0, 1)
    return hs, np.hypot(gx, gn)


def ramp(v, stops):
    xs = np.array([s[0] for s in stops], dtype=np.float64)
    cs = np.array([s[1] for s in stops], dtype=np.float64)
    out = np.empty(v.shape + (3,))
    for k in range(3):
        out[..., k] = np.interp(v, xs, cs[:, k])
    return out


def panel(z, cell, water=None, mode='tint', channels=None, ref=None):
    hs, s = shade(z, cell)
    land = ~water if water is not None else np.ones(z.shape, bool)
    base = np.percentile(z[land], 1) if ref is None else ref
    if mode == 'slope':
        col = np.empty(z.shape + (3,))
        lo = 0
        for lim, c in SLOPE:
            sel = (s >= lo) & (s < lim)
            col[sel] = c
            lo = lim
    else:
        col = ramp(z - base, RAMP)
    k = (0.30 + 0.70 * hs)[..., None] / (0.30 + 0.70 * 0.7071)
    rgb = np.clip(col * k, 0, 255)
    if water is not None:
        rgb[water] = WATER
    if channels is not None:
        rgb[channels & land] = (60, 100, 170)
    return rgb.astype(np.uint8)


def upscale(rgb, k):
    return np.repeat(np.repeat(rgb, k, 0), k, 1) if k > 1 else rgb


def main():
    a = np.load(sys.argv[1])
    out = sys.argv[2]
    mode = sys.argv[3] if len(sys.argv) > 3 else 'tint'
    arr = sys.argv[4] if len(sys.argv) > 4 else 'z'
    z = a[arr].astype(np.float64)
    cell = float(a['cell']) if 'cell' in a and arr == 'z' else {'z': 10.0, 'fine1': 1.0, 'fine2': 2.0}[arr]
    water = a['water'] if arr == 'z' else None
    if len(sys.argv) > 7:
        x0, y0, n = map(int, sys.argv[5:8])
        z = z[y0:y0 + n, x0:x0 + n]
        water = water[y0:y0 + n, x0:x0 + n] if water is not None else None
    ch = a['ch05'] if (arr == 'z' and 'channels' in sys.argv) else None
    png(panel(z, cell, water, mode, ch), out)


if __name__ == '__main__':
    main()
