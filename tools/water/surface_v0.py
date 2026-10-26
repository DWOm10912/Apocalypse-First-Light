"""Physical water V1 package 2 (docs/gameplay/water_v1_design.md 16.6): the simplest surface sprite.

A 32 x 32 tileable texture of faint ripples, near white so the vertex colour (ClientLiquid) gives the water its tint.
Procedural, AFL's own (no Mojang water texture, visual design 9). Package 6 replaces it with the full material set.

    python -I tools/water/surface_v0.py
"""
import math
import os
import struct
import zlib

SIZE = 32
OUT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "apocalypse_firstlight",
                   "textures", "block", "liquid", "water_surface.png")

# whole-number frequencies tile; a few crossing ripple trains of different lengths
WAVES = [(1, 2, 0.0, 0.30), (3, -1, 1.7, 0.22), (-2, 3, 4.1, 0.18), (4, 1, 2.6, 0.12), (1, -5, 5.3, 0.10)]


def value(x, y):
    v = 0.0
    for fx, fy, phase, amp in WAVES:
        v += amp * math.sin(2 * math.pi * (fx * x + fy * y) / SIZE + phase)
    return v


def main():
    rows = []
    lo, hi = min(value(x, y) for x in range(SIZE) for y in range(SIZE)), max(value(x, y) for x in range(SIZE) for y in range(SIZE))
    for y in range(SIZE):
        row = bytearray([0])
        for x in range(SIZE):
            t = (value(x, y) - lo) / (hi - lo)
            g = int(round(214 + 41 * t))   # 214..255: faint
            row += bytes([g, g, min(255, g + 2), 255])
        rows.append(bytes(row))
    raw = b"".join(rows)

    def chunk(kind, data):
        c = struct.pack(">I", len(data)) + kind + data
        return c + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "wb") as f:
        f.write(png)
    print("wrote", os.path.normpath(OUT), len(png), "bytes")


if __name__ == "__main__":
    main()
