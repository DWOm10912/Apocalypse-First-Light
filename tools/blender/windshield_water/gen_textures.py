"""T15 windshield water textures (docs/vehicles/t15_windshield_rain_design.md): simulated, not painted.

windshield_beads.png: a 0.22 m square tile of the fine beads a windshield collects in the rain. Raindrop fragments land one
by one; one touching others merges with them (volumes add, at their balance point); grown past R_SLIDE it runs off (the
CPU drops take over from there) and leaves a gap. Each bead is a spherical cap (contact angle about 40 deg) on a slightly
irregular contact line (pinning; the more merges, the less round), a little fuller on its lower side (the glass leans
back about 54 deg).
  RGBA8, 1024 px (0.215 mm a texel), rows = up the glass (row 0 at s = 0), columns = across (u). Every channel is one
  the GPU's mipmaps average correctly (plain means), so a far, sub-pixel bead field still has the right wet share:
  B = the share of the texel under water; R, G = 0.5 + 0.5 x the surface normal's x (across, right) and y (up the
  glass) summed over that wet share (the shader divides by B); A = when the beads there show: once the shader's
  wetness (0..1) passes A (the time each bead took its final shape, over the run; texels between beads take the
  nearest bead's).
windshield_drops.png: an 8 x 4 sheet of 128 px cells, the shapes the CPU drops are drawn with (client/rain/WindshieldWater).
  rows 0-2: 24 resting drops (one to four merged blobs), fuller on the low side (-y); row 3: 8 running drops (a round
  head at +y, the way it runs, a tapering tail). The surface solves a Poisson equation on the shape (an even pressure, a
  little more toward the low or leading side) scaled to the contact angle. Cell coordinates -1..1 (x right, y up, row 0
  of a cell at y = -1). RGBA8: R, G = normal; B = thickness / DROP_HMAX (cell half widths); A = cover. Every shape is
  scaled so its area is a circle of radius R_EQ cell half widths, centred on its centroid.

Run with Blender's own Python (it has numpy):
  "C:/Program Files/Blender Foundation/Blender 5.2/5.2/python/bin/python.exe" tools/blender/windshield_water/gen_textures.py [work dir]
A work dir also gets the raw fields (.npy) for reference_render.py.
"""
import json
import math
import os
import struct
import sys
import time
import zlib

import numpy as np

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..'))
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'apocalypse_firstlight', 'textures', 'effect')
WORK = sys.argv[1] if len(sys.argv) > 1 else None

# ---- beads ----
L, N, SS = 0.22, 1024, 2            # tile (m), texels, supersampling for the raster
R_SLIDE = 0.0010                    # a bead grown past this runs off
COVER = 0.24                        # stop when the beads that show cover this much of the glass
R_VISIBLE = 0.00015                 # smaller beads (under a texel) are left out of the texture
THETA = math.radians(40)            # contact angle
# ---- drops ----
CELL_PX, COLS, ROWS, SSD = 128, 8, 4, 2
R_EQ = 0.5
DROP_HMAX = 0.25
DROP_THETA = math.radians(38)


def write_png(path, rgba):
    """RGBA8 PNG; each row takes the filter (None, Sub, Up, Average, Paeth) with the smallest sum of signed residues."""
    a = np.ascontiguousarray(rgba.astype(np.uint8))
    h, w, _ = a.shape
    x = a.reshape(h, w * 4).astype(np.int16)
    left = np.zeros_like(x)
    left[:, 4:] = x[:, :-4]
    up = np.zeros_like(x)
    up[1:] = x[:-1]
    ul = np.zeros_like(x)
    ul[1:, 4:] = x[:-1, :-4]
    p = left + up - ul
    pa, pb, pc = np.abs(p - left), np.abs(p - up), np.abs(p - ul)
    paeth = np.where((pa <= pb) & (pa <= pc), left, np.where(pb <= pc, up, ul))
    cands = [x, x - left, x - up, x - (left + up) // 2, x - paeth]
    res = np.stack([(c & 0xFF).astype(np.uint8) for c in cands])
    cost = np.stack([np.abs(r.astype(np.int8).astype(np.int16)).sum(axis=1) for r in res])
    best = cost.argmin(axis=0)
    rows = np.concatenate([best.astype(np.uint8)[:, None], res[best, np.arange(h)]], axis=1)

    def chunk(t, d):
        return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xFFFFFFFF)

    data = (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(rows.tobytes(), 9)) + chunk(b'IEND', b''))
    with open(path, 'wb') as f:
        f.write(data)
    return len(data)


def encode(nx, ny, thick, alpha):
    out = np.empty(nx.shape + (4,), np.float64)
    out[..., 0] = 0.5 + 0.5 * nx
    out[..., 1] = 0.5 + 0.5 * ny
    out[..., 2] = thick
    out[..., 3] = alpha
    return np.clip(np.rint(out * 255), 0, 255)


def normals(h, px):
    """Normals of a height field (periodic edges; the drops' cells have empty borders anyway)."""
    hx = (np.roll(h, -1, 1) - np.roll(h, 1, 1)) / (2 * px)
    hy = (np.roll(h, -1, 0) - np.roll(h, 1, 0)) / (2 * px)
    n = np.stack([-hx, -hy, np.ones_like(h)], -1)
    return n / np.linalg.norm(n, axis=-1, keepdims=True)


def down(a, k):
    """k x k block mean."""
    s = a.shape
    return a.reshape(s[0] // k, k, s[1] // k, k, *s[2:]).mean(axis=(1, 3))


def fill_nearest(a, empty=1.0):
    """Texels equal to empty take the value of the nearest one that is not (grown out a step at a time; periodic)."""
    a = a.copy()
    hole = a >= empty
    while hole.any():
        for sh, ax in ((1, 0), (-1, 0), (1, 1), (-1, 1)):
            src, srch = np.roll(a, sh, ax), np.roll(hole, sh, ax)
            take = hole & ~srch
            a[take] = src[take]
            hole &= ~take
    return a


# ================================================================ beads
def simulate_beads(rng):
    cell = L / 128
    g = 128
    X, Y, R, T, M, alive = [], [], [], [], [], []
    grid = {}
    area = 0.0
    t = 0

    def key(x, y):
        return int(x / cell) % g, int(y / cell) % g

    def offset(x0, y0, x1, y1):
        return (x1 - x0 + L / 2) % L - L / 2, (y1 - y0 + L / 2) % L - L / 2

    def touching(x, y, r):
        cx, cy = key(x, y)
        hits = []
        for i in (cx - 1, cx, cx + 1):
            for j in (cy - 1, cy, cy + 1):
                for k in grid.get((i % g, j % g), ()):
                    dx, dy = offset(x, y, X[k], Y[k])
                    if dx * dx + dy * dy < (r + R[k]) ** 2:
                        hits.append(k)
        return hits

    while area < COVER * L * L:
        t += 1
        x, y = rng.random() * L, rng.random() * L
        r = float(np.clip(rng.lognormal(math.log(0.00020), 0.55), 0.00008, 0.0007))
        m = 0
        while True:
            hits = touching(x, y, r)
            if not hits:
                break
            v, sx, sy = r ** 3, 0.0, 0.0
            for k in hits:
                dx, dy = offset(x, y, X[k], Y[k])
                vk = R[k] ** 3
                sx += dx * vk
                sy += dy * vk
                v += vk
                m += M[k] + 1
                grid[key(X[k], Y[k])].discard(k)
                alive[k] = False
                area -= math.pi * R[k] ** 2 if R[k] >= R_VISIBLE else 0
            x, y, r = (x + sx / v) % L, (y + sy / v) % L, v ** (1 / 3)
        if r > R_SLIDE:
            continue
        k = len(X)
        X.append(x); Y.append(y); R.append(r); T.append(t); M.append(m); alive.append(True)
        grid.setdefault(key(x, y), set()).add(k)
        area += math.pi * r * r if r >= R_VISIBLE else 0
    keep = [k for k in range(len(X)) if alive[k]]
    return (np.array([X[k] for k in keep]), np.array([Y[k] for k in keep]), np.array([R[k] for k in keep]),
            np.array([T[k] for k in keep]) / t, np.array([M[k] for k in keep]), t)


def raster_beads(rng, X, Y, R, A, M):
    m = N * SS
    px = L / m
    H = np.zeros((m, m), np.float64)
    AV = np.ones((m, m), np.float64)
    for i in range(len(X)):
        r, mk = R[i], min(1.0, M[i] / 4)
        th = THETA + rng.uniform(-1, 1) * math.radians(6)
        amp = [rng.uniform(0, 0.035 + 0.045 * mk), rng.uniform(0, 0.025 + 0.035 * mk), rng.uniform(0, 0.015 + 0.02 * mk)]
        ph = rng.uniform(0, 2 * math.pi, 3)
        grav = 0.12 * min(1.0, r / 0.001)
        ext = r * (1 + sum(amp)) + 2 * px
        cx, cy = X[i] / px, Y[i] / px
        ix = np.arange(int(math.floor(cx - ext / px)), int(math.ceil(cx + ext / px)) + 1)
        iy = np.arange(int(math.floor(cy - ext / px)), int(math.ceil(cy + ext / px)) + 1)
        dx = ((ix + 0.5) * px - X[i])[None, :]
        dy = ((iy + 0.5) * px - Y[i])[:, None]
        phi = np.arctan2(dy, dx)
        rr = r * (1 + amp[0] * np.cos(2 * phi + ph[0]) + amp[1] * np.cos(3 * phi + ph[1]) + amp[2] * np.cos(4 * phi + ph[2]))
        d = np.sqrt(dx * dx + dy * dy) / rr
        s = math.sin(th)
        h = r * (np.sqrt(np.maximum(0, 1 / (s * s) - d * d)) - 1 / math.tan(th)) * (1 - grav * dy / r)
        h = np.where(d < 1, np.maximum(h, 0), 0)
        sel = np.ix_(iy % m, ix % m)
        H[sel] = np.maximum(H[sel], h)
        AV[sel] = np.where(h > 0, A[i], AV[sel])
    return H, AV, px


def beads():
    rng = np.random.default_rng(20261017)
    t0 = time.time()
    X, Y, R, A, M, falls = simulate_beads(rng)
    t1 = time.time()
    # A: kept off the very ends (0 would show on a dry glass, 1 never), in 64 steps (it compresses better)
    A = np.round((0.02 + 0.96 * A) * 63) / 63
    seen = R >= R_VISIBLE
    X, Y, R, A, M = X[seen], Y[seen], R[seen], A[seen], M[seen]
    H, AV, px = raster_beads(rng, X, Y, R, A, M)
    t2 = time.time()
    n = normals(H, px)
    wet = (H > 0).astype(np.float64)
    # everything averages the way the GPU's mipmaps do (plain means): RG the normal summed over the wet part, B the wet
    # share; RG / B (the shader) is then the beads' mean normal at any level
    nd = down(n[..., :2] * wet[..., None], SS)
    cd = down(wet, SS)
    ad = fill_nearest(AV.reshape(N, SS, N, SS).min(axis=(1, 3)))
    rgba = encode(nd[..., 0], nd[..., 1], cd, ad)
    size = write_png(os.path.join(OUT, 'windshield_beads.png'), rgba)
    if WORK:
        np.save(os.path.join(WORK, 'beads_h.npy'), H.astype(np.float32))
        np.save(os.path.join(WORK, 'beads_a.npy'), AV.astype(np.float32))
    hist = np.histogram(R * 1000, bins=[0, 0.15, 0.25, 0.4, 0.6, 0.8, 1.01])[0]
    print(f'beads: {falls} falls, {len(X)} beads, cover {np.pi * (R ** 2).sum() / L / L:.3f}, r mm hist {hist.tolist()} '
          f'(<0.15 <0.25 <0.4 <0.6 <0.8 <=1.0), max h {H.max() * 1000:.3f} mm, png {size / 1e6:.2f} MB, '
          f'sim {t1 - t0:.1f} s raster {t2 - t1:.1f} s')
    return {'tile_m': L, 'texels': N, 'cover': COVER, 'beads': int(len(X))}


# ================================================================ drops
def field(blobs, x, y):
    f = np.zeros_like(x)
    for bx, by, br in blobs:
        q = 1 - ((x - bx) ** 2 + (y - by) ** 2) / (br * br)
        f += np.where(q > 0, q * q, 0)
    return f


def sor(mask, src, dx, iters):
    """Red-black SOR for lap(h) = -src inside mask, h = 0 outside."""
    h = np.zeros_like(src)
    ii, jj = np.indices(mask.shape)
    red, black = ((ii + jj) % 2 == 0) & mask, ((ii + jj) % 2 == 1) & mask
    w = 1.94
    for _ in range(iters):
        for sel in (red, black):
            nb = np.roll(h, 1, 0) + np.roll(h, -1, 0) + np.roll(h, 1, 1) + np.roll(h, -1, 1)
            new = (nb + src * dx * dx) / 4
            h = np.where(sel, h + w * (new - h), h)
    return np.where(mask, h, 0)


def make_drop(rng, running):
    for _ in range(200):
        if running:
            blobs = [(0.0, 0.0, 1.0)]
            n = int(rng.integers(3, 6))
            x, y, r = 0.0, 0.0, 1.0
            for k in range(n):
                r2 = r * rng.uniform(0.62, 0.78)
                y -= 0.55 * (r + r2) * rng.uniform(0.8, 1.0)
                x += rng.uniform(-0.09, 0.09)
                blobs.append((x, y, r2))
                r = r2
        else:
            blobs = [(0.0, 0.0, 1.0)]
            for k in range(int(rng.integers(0, 4))):
                a = rng.uniform(0, 2 * math.pi)
                d = rng.uniform(0.35, 0.75)
                blobs.append((d * math.cos(a), d * math.sin(a), rng.uniform(0.35, 0.7)))
        thr = 0.18
        # the shape in its own units: area and centroid
        g = np.linspace(-4, 4, 801)
        gx, gy = np.meshgrid(g, g)
        m = field(blobs, gx, gy) > thr
        cell = (g[1] - g[0]) ** 2
        area = m.sum() * cell
        cx, cy = (gx * m).sum() * cell / area, (gy * m).sum() * cell / area
        s = R_EQ / math.sqrt(area / math.pi)   # own units -> cell half widths
        # the cell grid (pixel centres), back into own units
        k = CELL_PX * SSD
        c = (np.arange(k) + 0.5) / k * 2 - 1
        x, y = np.meshgrid(c, c)
        mask = field(blobs, x / s + cx, y / s + cy) > thr
        edge = mask[0].any() or mask[-1].any() or mask[:, 0].any() or mask[:, -1].any()
        ys, xs = np.nonzero(mask)
        if edge or abs(c[xs]).max() > 0.94 or abs(c[ys]).max() > 0.94:
            continue
        dx = 2 / k
        # pressure: even, a little more on the low side (resting) or the leading head (running)
        src = (1 + 0.35 * y) if running else (1 - 0.25 * y)
        h = sor(mask, src, dx, 700)
        # scale to the contact angle: the slope on the ring one pixel in from the edge (both neighbours inside)
        def erode(a):
            return a & np.roll(a, 1, 0) & np.roll(a, -1, 0) & np.roll(a, 1, 1) & np.roll(a, -1, 1)
        m1 = erode(mask)
        ring = m1 & ~erode(m1)
        hx = (np.roll(h, -1, 1) - np.roll(h, 1, 1)) / (2 * dx)
        hy = (np.roll(h, -1, 0) - np.roll(h, 1, 0)) / (2 * dx)
        slope = np.sqrt(hx * hx + hy * hy)[ring].mean()
        h *= math.tan(DROP_THETA) / slope
        return h, mask, dx
    raise RuntimeError('no shape fits the cell')


def drops():
    rng = np.random.default_rng(57)
    k = CELL_PX * SSD
    sheet = np.zeros((ROWS * CELL_PX, COLS * CELL_PX, 4))
    raw = np.zeros((ROWS, COLS, k, k), np.float32)
    hmax = 0
    for row in range(ROWS):
        for col in range(COLS):
            h, mask, dx = make_drop(rng, running=row == ROWS - 1)
            n = normals(h, dx)
            nd = down(n, SSD)
            nd /= np.linalg.norm(nd, axis=-1, keepdims=True)
            hd, cover = down(h, SSD), down(mask.astype(np.float64), SSD)
            hmax = max(hmax, h.max())
            sheet[row * CELL_PX:(row + 1) * CELL_PX, col * CELL_PX:(col + 1) * CELL_PX] = encode(nd[..., 0], nd[..., 1], hd / DROP_HMAX, cover)
            raw[row, col] = h
    size = write_png(os.path.join(OUT, 'windshield_drops.png'), sheet)
    if WORK:
        np.save(os.path.join(WORK, 'drops_h.npy'), raw)
    print(f'drops: {ROWS * COLS} shapes, max thickness {hmax:.3f} cell half widths, png {size / 1e3:.0f} kB')
    return {'cell_px': CELL_PX, 'cols': COLS, 'rows': ROWS, 'r_eq': R_EQ, 'hmax': DROP_HMAX, 'running_row': ROWS - 1}


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    meta = {'beads': beads(), 'drops': drops()}
    if WORK:
        with open(os.path.join(WORK, 'meta.json'), 'w') as f:
            json.dump(meta, f, indent=1)
