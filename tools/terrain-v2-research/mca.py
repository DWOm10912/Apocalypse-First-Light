"""Minimal Anvil (1.20.1) reader: region files -> chunk NBT -> block-state lookups."""
import os
import struct
import zlib


def _nbt(buf, pos, tag):
    if tag == 1: return struct.unpack_from('>b', buf, pos)[0], pos + 1
    if tag == 2: return struct.unpack_from('>h', buf, pos)[0], pos + 2
    if tag == 3: return struct.unpack_from('>i', buf, pos)[0], pos + 4
    if tag == 4: return struct.unpack_from('>q', buf, pos)[0], pos + 8
    if tag == 5: return struct.unpack_from('>f', buf, pos)[0], pos + 4
    if tag == 6: return struct.unpack_from('>d', buf, pos)[0], pos + 8
    if tag == 7:
        n = struct.unpack_from('>i', buf, pos)[0]; pos += 4
        return buf[pos:pos + n], pos + n
    if tag == 8:
        n = struct.unpack_from('>H', buf, pos)[0]; pos += 2
        return buf[pos:pos + n].decode('utf-8', 'replace'), pos + n
    if tag == 9:
        t, n = struct.unpack_from('>bi', buf, pos); pos += 5
        out = []
        for _ in range(n):
            v, pos = _nbt(buf, pos, t); out.append(v)
        return out, pos
    if tag == 10:
        d = {}
        while True:
            t = buf[pos]; pos += 1
            if t == 0: return d, pos
            n = struct.unpack_from('>H', buf, pos)[0]; pos += 2
            name = buf[pos:pos + n].decode('utf-8', 'replace'); pos += n
            d[name], pos = _nbt(buf, pos, t)
    if tag == 11:
        n = struct.unpack_from('>i', buf, pos)[0]; pos += 4
        return list(struct.unpack_from('>%di' % n, buf, pos)), pos + 4 * n
    if tag == 12:
        n = struct.unpack_from('>i', buf, pos)[0]; pos += 4
        return list(struct.unpack_from('>%dq' % n, buf, pos)), pos + 8 * n
    raise ValueError('tag %d' % tag)


def parse_nbt(raw):
    t = raw[0]
    n = struct.unpack_from('>H', raw, 1)[0]
    v, _ = _nbt(raw, 3 + n, t)
    return v


def region_chunks(path):
    """Yields (cx, cz, nbt) for every chunk stored in a region file."""
    name = os.path.basename(path).split('.')
    rx, rz = int(name[1]), int(name[2])
    with open(path, 'rb') as f:
        data = f.read()
    if len(data) < 8192: return
    for i in range(1024):
        loc = struct.unpack_from('>I', data, i * 4)[0]
        off, cnt = loc >> 8, loc & 0xFF
        if off == 0 or cnt == 0: continue
        p = off * 4096
        ln, comp = struct.unpack_from('>IB', data, p)
        body = data[p + 5:p + 4 + ln]
        if comp == 2: raw = zlib.decompress(body)
        elif comp == 1:
            import gzip; raw = gzip.decompress(body)
        else: raw = body
        nbt = parse_nbt(raw)
        yield rx * 32 + (i & 31), rz * 32 + (i >> 5), nbt


class Chunk:
    """Block lookups in one chunk: name(lx, y, lz)."""

    def __init__(self, nbt):
        self.nbt = nbt
        self.cx, self.cz = nbt['xPos'], nbt['zPos']
        self.status = nbt.get('Status', '')
        self.sections = {}
        for s in nbt.get('sections', []):
            bs = s.get('block_states')
            if not bs: continue
            pal = [p['Name'] for p in bs['palette']]
            data = bs.get('data')
            if data is None or len(pal) == 1:
                self.sections[s['Y']] = (pal, None, 0)
                continue
            bits = max(4, (len(pal) - 1).bit_length())
            self.sections[s['Y']] = (pal, data, bits)
        self._cache = {}

    def section_array(self, sy):
        if sy in self._cache: return self._cache[sy]
        sec = self.sections.get(sy)
        if sec is None:
            arr = None
        else:
            pal, data, bits = sec
            if data is None:
                arr = (pal[0],)
            else:
                per = 64 // bits
                mask = (1 << bits) - 1
                out = []
                for v in data:
                    v &= 0xFFFFFFFFFFFFFFFF
                    for k in range(per):
                        out.append(pal[(v >> (k * bits)) & mask])
                        if len(out) == 4096: break
                    if len(out) == 4096: break
                arr = out
        self._cache[sy] = arr
        return arr

    def name(self, lx, y, lz):
        arr = self.section_array(y >> 4)
        if arr is None: return 'minecraft:air'
        if len(arr) == 1: return arr[0]
        return arr[((y & 15) * 16 + lz) * 16 + lx]


def load_world(region_dir, want=None):
    """{(cx, cz): Chunk} for the full chunks (optionally only regions in want)."""
    out = {}
    for fn in sorted(os.listdir(region_dir)):
        if not fn.endswith('.mca'): continue
        if want is not None and fn not in want: continue
        for cx, cz, nbt in region_chunks(os.path.join(region_dir, fn)):
            if nbt.get('Status', '') not in ('minecraft:full', 'full'): continue
            out[(cx, cz)] = Chunk(nbt)
    return out
