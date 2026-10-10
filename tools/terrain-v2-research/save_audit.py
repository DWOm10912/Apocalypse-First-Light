"""Terrain V2 save audit (2026-10-10): reads a world's region files offline and measures what the in-game
/afl dev terrain_v2 check reports, column by column, against the cached plan surface.

  python save_audit.py names SAVE_DIR                        block names at the top of the ground (to tune GROUND)
  python save_audit.py stable SAVE_DIR CACHE.bin SEED OUT.md  stable-layer openings by depth, cause, province
  python save_audit.py water SAVE_DIR CACHE.bin SEED OUT.md   estuary / marsh / river water as generated

The plan values per column come from java PlanColumnDump (compiled from src/main/java + tools/.../java).
"""
import collections
import math
import os
import struct
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mca  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
OUT_DIR = os.path.join(ROOT, 'build', 'terrain-v2-research', 'save_audit')
MIN_Y = -64

AIR = {'minecraft:air', 'minecraft:cave_air', 'minecraft:void_air'}
FLUID = {'minecraft:water', 'minecraft:lava'}
NOT_GROUND_PARTS = ('log', 'leaves', '_wood', 'sapling', 'flower', 'poppy', 'dandelion', 'tulip', 'orchid', 'allium',
                    'bluet', 'daisy', 'cornflower', 'lily', 'rose_bush', 'peony', 'lilac', 'sunflower', 'dead_bush',
                    'fern', 'vine', 'seagrass', 'kelp', 'sugar_cane', 'mushroom', 'berry_bush', 'snow', 'moss_carpet',
                    'glow_lichen', 'pitcher', 'torchflower', 'cocoa', 'bee_nest', 'pumpkin', 'melon')


def is_ground(name):
    if name in AIR or name in FLUID: return False
    if name in ('minecraft:grass', 'minecraft:tall_grass', 'minecraft:short_grass'): return False
    if name.endswith('mushroom_block'): return True
    return not any(p in name for p in NOT_GROUND_PARTS)


def heightmap(chunk, key):
    hm = chunk.nbt.get('Heightmaps', {}).get(key)
    if hm is None: return None
    out = []
    for v in hm:
        v &= 0xFFFFFFFFFFFFFFFF
        for k in range(7):
            out.append((v >> (9 * k)) & 511)
            if len(out) == 256: return out
    return out


def ground_top(chunk, lx, lz, hm):
    """The ground's top block: below plants, trees, snow and fluid (from WORLD_SURFACE)."""
    y = hm[lz * 16 + lx] + MIN_Y - 1
    while y > MIN_Y and not is_ground(chunk.name(lx, y, lz)):
        y -= 1
    return y


def load(save):
    return mca.load_world(os.path.join(save, 'region'))


def plan_columns(chunks, cache, seed):
    """{(cx, cz): [(h, base, stable, water, shore)] * 256} from PlanColumnDump."""
    os.makedirs(OUT_DIR, exist_ok=True)
    keys = sorted(chunks)
    lst = os.path.join(OUT_DIR, 'chunks.txt')
    with open(lst, 'w') as f:
        for cx, cz in keys: f.write('%d %d\n' % (cx, cz))
    classes = os.path.join(ROOT, 'build', 'terrain-v2-research', 'classes')
    out = os.path.join(OUT_DIR, 'columns.bin')
    subprocess.run(['java', '-cp', classes, 'com.antaurora.apofirstlight.worldgen.terrain.v2.PlanColumnDump',
                    cache, str(seed), lst, out], check=True)
    data = open(out, 'rb').read()
    rec = struct.Struct('>ffbbb')
    res, p = {}, 0
    for k in keys:
        cols = []
        for _ in range(256):
            cols.append(rec.unpack_from(data, p)); p += rec.size
        res[k] = cols
    return res


def cmd_names(save):
    chunks = load(save)
    c = collections.Counter()
    for ch in chunks.values():
        hm = heightmap(ch, 'WORLD_SURFACE')
        if hm is None: continue
        for lz in range(16):
            for lx in range(16):
                y = hm[lz * 16 + lx] + MIN_Y - 1
                for k in range(4):
                    c[ch.name(lx, y - k, lz)] += 1
    for n, v in c.most_common(80): print(v, n)


def flood(chunks, start, limit=20000):
    """Air component from start (x, y, z) within saved full chunks: (size, min y, max y, positions or None)."""
    seen = {start}
    todo = [start]
    while todo and len(seen) < limit:
        x, y, z = todo.pop()
        for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            q = (x + dx, y + dy, z + dz)
            if q in seen: continue
            ch = chunks.get((q[0] >> 4, q[2] >> 4))
            if ch is None or q[1] < MIN_Y or q[1] > 319: continue
            if ch.name(q[0] & 15, q[1], q[2] & 15) in AIR:
                seen.add(q); todo.append(q)
    ys = [p[1] for p in seen]
    return len(seen), min(ys), max(ys), seen


PROVINCE = {12: 'plain/coastal', 6: 'foothill/water floor', 4: 'fold belt'}
FEATURE_HINTS = {
    'lake': ('minecraft:lava', 'minecraft:obsidian', 'minecraft:magma_block'),
    'dungeon': ('minecraft:mossy_cobblestone', 'minecraft:spawner'),
    'mineshaft': ('minecraft:rail', 'minecraft:cobweb', 'minecraft:oak_planks', 'minecraft:oak_fence', 'minecraft:dark_oak_planks'),
    'geode': ('minecraft:amethyst_block', 'minecraft:calcite', 'minecraft:smooth_basalt'),
}


def cmd_stable(save, cache, seed, out_md):
    chunks = load(save)
    plan = plan_columns(chunks, cache, seed)
    # per land column: true top T, plan h, the check's own window (it started one block low, see below)
    land = 0
    old_window = 0            # what the in-game check counted: air in [T-2, T-1-stable] (heightmap read one too low)
    by_k = collections.Counter()           # shallowest air: blocks below the true top (k = T - y)
    by_plan_depth = collections.Counter()  # shallowest air: plan depth h - y, floored
    in_layer = []             # air with plan depth < stable (the design promise)
    cases = collections.Counter()
    prov = collections.Counter()
    prov_open = collections.Counter()
    comp_cache = {}
    for (cx, cz), ch in chunks.items():
        hm = heightmap(ch, 'WORLD_SURFACE')
        if hm is None: continue
        cols = plan[(cx, cz)]
        for lz in range(16):
            for lx in range(16):
                h, base, stable, water, shore = cols[lz * 16 + lx]
                if water != 0 or stable <= 0: continue
                land += 1
                prov[stable] += 1
                T = ground_top(ch, lx, lz, hm)
                x, z = cx * 16 + lx, cz * 16 + lz
                # the in-game check's window
                if any(ch.name(lx, y, lz) in AIR for y in range(T - 2, T - 2 - stable, -1)):
                    old_window += 1
                first = None
                for k in range(1, 24):
                    y = T - k
                    if ch.name(lx, y, lz) in AIR:
                        first = y; break
                if first is None: continue
                k = T - first
                by_k[k] += 1
                d = h - first
                by_plan_depth[int(d)] += 1
                if d < stable:
                    prov_open[stable] += 1
                    in_layer.append((x, first, z, h, base, stable, shore, T))
    # classify the openings inside the designed layer
    # one row per air component (a hole), not per column
    rows = []
    owner = {}
    comps = []
    for x, y, z, h, base, stable, shore, T in in_layer:
        if (x, y, z) in owner:
            comps[owner[(x, y, z)]]['cols'].append((x, y, z, h, stable, T)); continue
        size, ymin, ymax, cells = flood(chunks, (x, y, z))
        for c in cells: owner[c] = len(comps)
        comps.append({'size': size, 'ymin': ymin, 'ymax': ymax, 'cols': [(x, y, z, h, stable, T)]})
    for cp in comps:
        x, y, z, h, stable, T = min(cp['cols'], key=lambda c: c[3] - c[1])
        near = collections.Counter()
        for px in range(x - 4, x + 5):
            for pz in range(z - 4, z + 5):
                ch = chunks.get((px >> 4, pz >> 4))
                if ch is None: continue
                for py in range(y - 4, y + 5): near[ch.name(px & 15, py, pz & 15)] += 1
        hint = None
        for name, blocks in FEATURE_HINTS.items():
            if any(near[b] for b in blocks): hint = name
        reach = h - y
        if cp['ymax'] >= min(c[5] for c in cp['cols']): cause = 'open to the surface'
        elif hint: cause = 'feature near: ' + hint
        elif h - cp['ymin'] >= 12.5: cause = 'cave from below, pushes up through the soft ramp'
        else: cause = 'isolated pocket inside the layer'
        band = '< 4' if reach < 4 else '4..8' if reach < 8 else '8..stable'
        cases[(cause, band)] += 1
        rows.append((x, y, z, reach, stable, len(cp['cols']), cp['size'], cp['ymin'], cp['ymax'], cause,
                     [n.replace('minecraft:', '') for n, _ in near.most_common(5)]))
    rows.sort(key=lambda r: r[3])
    L = []
    L.append('# Terrain V2 save audit: stable layer\n')
    L.append('Save `%s`, seed %s, %d full chunks, %d land columns (plan water class 0).\n' % (save, seed, len(chunks), land))
    L.append('- in-game check window (air in T-2 .. T-1-stable, T = true top): %d columns (%.2f%%)' % (old_window, 100.0 * old_window / max(1, land)))
    L.append('- air inside the designed layer (plan depth h - y < stable): %d columns (%.3f%%)\n' % (len(in_layer), 100.0 * len(in_layer) / max(1, land)))
    L.append('| province (stable) | land columns | in-layer openings |\n|---|---|---|')
    for s in sorted(prov):
        L.append('| %s (%d) | %d | %d (%.3f%%) |' % (PROVINCE.get(s, '?'), s, prov[s], prov_open[s], 100.0 * prov_open[s] / prov[s]))
    L.append('\nShallowest air below the true top (k = T - y), all columns:\n')
    L.append('| k | columns |\n|---|---|')
    for k in sorted(by_k): L.append('| %d | %d |' % (k, by_k[k]))
    L.append('\nShallowest air by plan depth floor(h - y):\n')
    L.append('| plan depth | columns |\n|---|---|')
    for k in sorted(by_plan_depth): L.append('| %d | %d |' % (k, by_plan_depth[k]))
    L.append('\nIn-layer holes (air components) by cause and the shallowest plan depth they reach:\n')
    L.append('| cause | reach < 4 | 4..8 | 8..stable |\n|---|---|---|---|')
    for c in sorted({k[0] for k in cases}):
        L.append('| %s | %d | %d | %d |' % (c, cases[(c, '< 4')], cases[(c, '4..8')], cases[(c, '8..stable')]))
    L.append('\nHoles, shallowest first: x y z | plan depth | stable | columns | component size, y range | cause | blocks round it\n')
    for r in rows[:80]: L.append('- %d %d %d | %.2f | %d | %d | %d, %d..%d | %s | %s' % r)
    open(out_md, 'w', encoding='utf-8').write('\n'.join(L) + '\n')
    print('\n'.join(L[:40]))


def column_fluid(chunk, lx, lz, hm):
    """(top of the water column, or None; lowest fluid y; whether it is a source at the top; fluid name)."""
    y = hm[lz * 16 + lx] + MIN_Y - 1
    top = None
    name = None
    while y > MIN_Y:
        n = chunk.name(lx, y, lz)
        if n in FLUID or n in ('minecraft:seagrass', 'minecraft:tall_seagrass', 'minecraft:kelp', 'minecraft:kelp_plant'):
            if top is None: top, name = y, n
        elif is_ground(n):
            break
        y -= 1
    return top, y, name


def cmd_water(save, cache, seed, out_md):
    """Generated water against the unified mask: per class, water surface, floor vs plan, leaks onto land."""
    chunks = load(save)
    plan = plan_columns(chunks, cache, seed)
    names = {0: 'land', 1: 'sea', 2: 'estuary', 3: 'marsh'}
    n = collections.Counter()
    wet = collections.Counter()
    surf = collections.defaultdict(collections.Counter)
    floor_d = collections.defaultdict(list)
    exact = collections.Counter()
    land_wet_hi = 0
    examples = collections.defaultdict(list)
    for (cx, cz), ch in chunks.items():
        hm = heightmap(ch, 'WORLD_SURFACE')
        if hm is None: continue
        cols = plan[(cx, cz)]
        for lz in range(16):
            for lx in range(16):
                h, base, stable, water, shore = cols[lz * 16 + lx]
                n[water] += 1
                top, floor, fname = column_fluid(ch, lx, lz, hm)
                T = ground_top(ch, lx, lz, hm)
                floor_d[water].append(T + 1 - h)
                if T == math.ceil(h) - 1: exact[water] += 1
                if top is not None:
                    wet[water] += 1
                    surf[water][top] += 1
                    if water == 0 and top >= 62:
                        land_wet_hi += 1
                        if len(examples['land']) < 12: examples['land'].append((cx * 16 + lx, top, cz * 16 + lz, round(h, 2), fname))
                elif water in (1, 2) and len(examples['dry']) < 12:
                    examples['dry'].append((cx * 16 + lx, T, cz * 16 + lz, round(h, 2)))
    L = ['# Terrain V2 save audit: water\n', 'Save `%s`, seed %s, %d full chunks.\n' % (save, seed, len(chunks))]
    L.append('| plan class | columns | with fluid | fluid top Y (columns) | top block = ceil(h) - 1 | mean (T + 1 - h) |')
    L.append('|---|---|---|---|---|---|')
    for w in sorted(n):
        tops = ', '.join('%d: %d' % (y, c) for y, c in sorted(surf[w].items(), key=lambda t: -t[1])[:5])
        fd = floor_d[w]
        L.append('| %s | %d | %d | %s | %.1f%% | %+.2f |' % (names[w], n[w], wet[w], tops, 100.0 * exact[w] / n[w], sum(fd) / len(fd)))
    L.append('\nLand columns (plan class 0) with fluid at Y >= 62: %d' % land_wet_hi)
    for k, v in examples.items():
        L.append('\n%s examples: %s' % (k, v))
    open(out_md, 'w', encoding='utf-8').write('\n'.join(L) + '\n')
    print('\n'.join(L))


def main():
    sys.stdout.reconfigure(encoding='utf-8')
    cmd = sys.argv[1]
    if cmd == 'names': cmd_names(sys.argv[2])
    elif cmd == 'stable': cmd_stable(sys.argv[2], sys.argv[3], int(sys.argv[4]), sys.argv[5])
    elif cmd == 'water': cmd_water(sys.argv[2], sys.argv[3], int(sys.argv[4]), sys.argv[5])
    else: raise SystemExit(__doc__)


if __name__ == '__main__':
    main()
