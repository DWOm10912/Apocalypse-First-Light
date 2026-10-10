"""Top-down render of a saved world window (2026-10-10): ground top shaded by height, water tinted, plan cell grid and
water-class changes marked, plus a list of straight steps on plan-cell (= chunk) borders.

  python save_render.py SAVE_DIR CACHE.bin SEED X Z HALF OUT.png
"""
import os
import struct
import sys
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mca  # noqa: E402
import save_audit as sa  # noqa: E402


def write_png(path, w, h, rows):
    raw = b''.join(b'\x00' + bytes(r) for r in rows)

    def chunk(t, d):
        c = struct.pack('>I', len(d)) + t + d
        return c + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0))
    png += chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
    open(path, 'wb').write(png)


def main():
    save, cache, seed, cx, cz, half, out = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4]), int(sys.argv[5]), int(sys.argv[6]), sys.argv[7]
    want = set()
    for rx in range((cx - half) >> 9, ((cx + half) >> 9) + 1):
        for rz in range((cz - half) >> 9, ((cz + half) >> 9) + 1):
            want.add('r.%d.%d.mca' % (rx, rz))
    chunks = mca.load_world(os.path.join(save, 'region'), want)
    sel = {k: v for k, v in chunks.items() if cx - half - 16 <= k[0] * 16 <= cx + half and cz - half - 16 <= k[1] * 16 <= cz + half}
    plan = sa.plan_columns(sel, cache, seed)
    W = 2 * half
    top = {}
    for (kx, kz), ch in sel.items():
        hm = sa.heightmap(ch, 'WORLD_SURFACE')
        if hm is None: continue
        for lz in range(16):
            for lx in range(16):
                x, z = kx * 16 + lx, kz * 16 + lz
                g = sa.ground_top(ch, lx, lz, hm)
                wt, _, fname = sa.column_fluid(ch, lx, lz, hm)
                top[(x, z)] = (g, wt, plan[(kx, kz)][lz * 16 + lx])
    rows = []
    steps = {}
    for pz in range(W):
        row = []
        for px in range(W):
            x, z = cx - half + px, cz - half + pz
            v = top.get((x, z))
            if v is None:
                row += [0, 0, 0]; continue
            g, wt, (h, base, stable, wc, shore) = v
            t = max(0.0, min(1.0, (g - 55) / 20.0))
            r, gg, b = int(90 + 120 * t), int(80 + 110 * t), int(60 + 60 * t)
            # shading by the step to the west / north neighbour
            for d in ((-1, 0), (0, -1)):
                n = top.get((x + d[0], z + d[1]))
                if n is not None and n[0] != g:
                    f = 1.25 if n[0] < g else 0.7
                    r, gg, b = min(255, int(r * f)), min(255, int(gg * f)), min(255, int(b * f))
                    if (x % 16 == 0 and d[0]) or (z % 16 == 0 and d[1]):
                        key = 'class change' if n[2][3] != wc else 'same class'
                        steps[key] = steps.get(key, 0) + 1
                    else:
                        steps['inside cell'] = steps.get('inside cell', 0) + 1
            if wt is not None:
                depth = wt - g
                r, gg, b = int(30 + 20 / (1 + depth)), int(90 + 40 / (1 + depth)), int(170 + 40 / (1 + depth))
            # a step exactly on a plan-cell (= chunk) border: red
            for d in ((-1, 0), (0, -1)):
                n = top.get((x + d[0], z + d[1]))
                if n is not None and n[0] != g and ((x % 16 == 0 and d[0]) or (z % 16 == 0 and d[1])):
                    r, gg, b = 230, 40, 40
            row += [r, gg, b] * 3
        for _ in range(3): rows.append(row)
    write_png(out, W * 3, W * 3, rows)
    print('steps:', steps)


if __name__ == '__main__':
    main()
