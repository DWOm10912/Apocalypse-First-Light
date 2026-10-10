"""Surface census of a saved world window (2026-10-10, ecology V1 acceptance): per biome the top-block mix, and the
connected patches of bare sand tops (size, biome, distance to the sea from the plan).

  python save_surface.py SAVE_DIR CACHE.bin SEED X Z HALF
"""
import collections
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mca  # noqa: E402
import save_audit as sa  # noqa: E402


def main():
    sys.stdout.reconfigure(encoding='utf-8')
    save, cache, seed, cx, cz, half = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4]), int(sys.argv[5]), int(sys.argv[6])
    want = set()
    for rx in range((cx - half) >> 9, ((cx + half) >> 9) + 1):
        for rz in range((cz - half) >> 9, ((cz + half) >> 9) + 1):
            want.add('r.%d.%d.mca' % (rx, rz))
    chunks = mca.load_world(os.path.join(save, 'region'), want)
    sel = {k: v for k, v in chunks.items() if abs(k[0] * 16 + 8 - cx) <= half and abs(k[1] * 16 + 8 - cz) <= half}
    plan = sa.plan_columns(sel, cache, seed)
    tops = collections.defaultdict(collections.Counter)
    sand = {}
    for (kx, kz), ch in sel.items():
        hm = sa.heightmap(ch, 'WORLD_SURFACE')
        if hm is None: continue
        for lz in range(16):
            for lx in range(16):
                y = sa.ground_top(ch, lx, lz, hm)
                wt, _, _ = sa.column_fluid(ch, lx, lz, hm)
                b = ch.biome(lx, y, lz).replace('apocalypse_firstlight:', 'afl:').replace('minecraft:', 'mc:')
                n = ch.name(lx, y, lz).replace('minecraft:', '')
                if wt is not None: n = 'under water: ' + n
                tops[b][n] += 1
                if n == 'sand':
                    shore = plan[(kx, kz)][lz * 16 + lx][4]
                    sand[(kx * 16 + lx, kz * 16 + lz)] = (b, shore)
    print('chunks %d' % len(sel))
    for b, c in sorted(tops.items(), key=lambda t: -sum(t[1].values())):
        tot = sum(c.values())
        print('%-36s %6d cols | %s' % (b, tot, ', '.join('%s %.0f%%' % (k, 100.0 * v / tot) for k, v in c.most_common(6))))
    # connected sand patches
    seen, patches = set(), []
    for p in sand:
        if p in seen: continue
        stack, comp = [p], []
        seen.add(p)
        while stack:
            q = stack.pop()
            comp.append(q)
            for d in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                r = (q[0] + d[0], q[1] + d[1])
                if r in sand and r not in seen:
                    seen.add(r); stack.append(r)
        bc = collections.Counter(sand[q][0] for q in comp)
        shore = min(sand[q][1] for q in comp)
        xs = [q[0] for q in comp]; zs = [q[1] for q in comp]
        patches.append((len(comp), bc.most_common(1)[0][0], shore, (min(xs) + max(xs)) // 2, (min(zs) + max(zs)) // 2,
                        max(xs) - min(xs) + 1, max(zs) - min(zs) + 1))
    patches.sort(reverse=True)
    print('\nsand patches: %d, columns %d; largest:' % (len(patches), len(sand)))
    for pt in patches[:15]:
        print('  %5d cols  %-32s nearest shore %4d m  centre %d %d  extent %dx%d' % pt)


if __name__ == '__main__':
    main()
