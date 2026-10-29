"""Dump a square of a Minecraft 1.20 world's columns around the player (or around x z given after y_hi): for each column every block name from y_lo to
y_hi (palette indices), as a compact text file a Java check can read. Stdlib only."""
import gzip, io, os, struct, sys, zlib

def nbt(buf):
    def rd(t):
        if t == 1: return struct.unpack('>b', buf.read(1))[0]
        if t == 2: return struct.unpack('>h', buf.read(2))[0]
        if t == 3: return struct.unpack('>i', buf.read(4))[0]
        if t == 4: return struct.unpack('>q', buf.read(8))[0]
        if t == 5: return struct.unpack('>f', buf.read(4))[0]
        if t == 6: return struct.unpack('>d', buf.read(8))[0]
        if t == 7:
            n = struct.unpack('>i', buf.read(4))[0]; return buf.read(n)
        if t == 8:
            n = struct.unpack('>H', buf.read(2))[0]; return buf.read(n).decode('utf-8', 'replace')
        if t == 9:
            et = buf.read(1)[0]; n = struct.unpack('>i', buf.read(4))[0]
            return [rd(et) for _ in range(n)]
        if t == 10:
            d = {}
            while True:
                tt = buf.read(1)[0]
                if tt == 0: return d
                k = rd(8); d[k] = rd(tt)
        if t == 11:
            n = struct.unpack('>i', buf.read(4))[0]; return list(struct.unpack('>%di' % n, buf.read(4 * n)))
        if t == 12:
            n = struct.unpack('>i', buf.read(4))[0]; return list(struct.unpack('>%dq' % n, buf.read(8 * n)))
        raise ValueError(t)
    t = buf.read(1)[0]
    rd(8)
    return rd(t)

def chunk(world, cx, cz):
    rx, rz = cx >> 5, cz >> 5
    p = os.path.join(world, 'region', 'r.%d.%d.mca' % (rx, rz))
    if not os.path.exists(p): return None
    with open(p, 'rb') as f:
        f.seek(4 * ((cx & 31) + (cz & 31) * 32))
        loc = struct.unpack('>I', f.read(4))[0]
        off, cnt = loc >> 8, loc & 255
        if off == 0: return None
        f.seek(off * 4096)
        ln, comp = struct.unpack('>IB', f.read(5))
        data = f.read(ln - 1)
    raw = zlib.decompress(data) if comp == 2 else gzip.decompress(data)
    return nbt(io.BytesIO(raw))

def section_blocks(sec):
    bs = sec.get('block_states')
    if bs is None: return None, None
    pal = [e['Name'] + ('[' + ','.join('%s=%s' % kv for kv in sorted(e.get('Properties', {}).items())) + ']' if e.get('Properties') else '') for e in bs['palette']]
    if len(pal) == 1 or 'data' not in bs: return pal, None
    bits = max(4, (len(pal) - 1).bit_length())
    per = 64 // bits
    mask = (1 << bits) - 1
    out = []
    for v in bs['data']:
        v &= (1 << 64) - 1
        for k in range(per):
            out.append((v >> (k * bits)) & mask)
    return pal, out[:4096]

def main():
    world, out, r, ylo, yhi = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4]), int(sys.argv[5])
    pd = os.path.join(world, 'playerdata')
    f = [x for x in os.listdir(pd) if x.endswith('.dat')][0]
    pl = nbt(io.BytesIO(gzip.decompress(open(os.path.join(pd, f), 'rb').read())))
    px, py, pz = (int(v) for v in pl['Pos'])
    if len(sys.argv) > 7: px, pz = int(sys.argv[6]), int(sys.argv[7])   # another centre (terrain rework R3 survey)
    yaw = pl['Rotation'][0]
    names = {}
    rows = []
    for z in range(pz - r, pz + r):
        for x in range(px - r, px + r):
            rows.append((x, z))
    cols = {}
    cache = {}
    for (x, z) in rows:
        cx, cz = x >> 4, z >> 4
        if (cx, cz) not in cache:
            c = chunk(world, cx, cz)
            secs = {}
            if c is not None:
                for s in c.get('sections', []):
                    secs[s['Y']] = section_blocks(s)
            cache[(cx, cz)] = secs
        secs = cache[(cx, cz)]
        col = []
        for y in range(ylo, yhi):
            pal, data = secs.get(y >> 4, (None, None))
            if pal is None: n = 'minecraft:air' if secs else '?'
            else: n = pal[0] if data is None else pal[data[((y & 15) * 256) + ((z & 15) * 16) + (x & 15)]]
            col.append(names.setdefault(n, len(names)))
        cols[(x, z)] = col
    with open(out, 'w', encoding='utf-8') as o:
        o.write('player %d %d %d yaw %.1f\n' % (px, py, pz, yaw))
        o.write('area %d %d %d %d %d\n' % (px - r, pz - r, 2 * r, ylo, yhi))
        o.write('names %d\n' % len(names))
        for n, i in sorted(names.items(), key=lambda kv: kv[1]): o.write('%d %s\n' % (i, n))
        for z in range(pz - r, pz + r):
            for x in range(px - r, px + r):
                o.write(' '.join(map(str, cols[(x, z)])) + '\n')
    print('player', px, py, pz, 'yaw', yaw, 'names', len(names))

main()
