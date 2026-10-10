"""Minimal baseline TIFF / GeoTIFF reader for the research rasters (no GDAL here): one band, strips or tiles,
compression none / LZW / Deflate, horizontal predictor 1-3, sample formats uint8..uint32, int16/32, float32/64.
Returns (array, tags); the GeoTIFF tags (ModelPixelScale 33550, ModelTiepoint 33922, GeoKeyDirectory 34735,
GDAL_NODATA 42113) are kept so the caller can check the grid it asked for."""
import struct
import zlib
import numpy as np

TYPES = {1: ('B', 1), 2: ('c', 1), 3: ('H', 2), 4: ('I', 4), 5: ('II', 8), 6: ('b', 1), 7: ('B', 1), 8: ('h', 2),
         9: ('i', 4), 10: ('ii', 8), 11: ('f', 4), 12: ('d', 8), 16: ('Q', 8)}


def _lzw(data):
    out = bytearray()
    table = [bytes([i]) for i in range(256)] + [b'', b'']
    bits, pos, width, prev = 0, 0, 9, None
    nbits = len(data) * 8
    while pos + width <= nbits:
        code = 0
        for _ in range(width):   # MSB first
            code = (code << 1) | ((data[pos >> 3] >> (7 - (pos & 7))) & 1)
            pos += 1
        if code == 256:
            table = table[:258]
            width, prev = 9, None
            continue
        if code == 257:
            break
        if prev is None:
            entry = table[code]
        elif code < len(table):
            entry = table[code]
            table.append(prev + entry[:1])
        else:
            entry = prev + prev[:1]
            table.append(entry)
        out += entry
        prev = entry
        if len(table) + 1 >= (1 << width) and width < 12:
            width += 1
    return bytes(out)


def read(path):
    raw = open(path, 'rb').read()
    bo = {b'II': '<', b'MM': '>'}[raw[:2]]
    if struct.unpack(bo + 'H', raw[2:4])[0] != 42:
        raise ValueError('not a classic TIFF (BigTIFF unsupported): ' + path)
    ifd = struct.unpack(bo + 'I', raw[4:8])[0]
    n = struct.unpack(bo + 'H', raw[ifd:ifd + 2])[0]
    tags = {}
    for i in range(n):
        e = ifd + 2 + 12 * i
        tag, typ, cnt = struct.unpack(bo + 'HHI', raw[e:e + 8])
        fmt, size = TYPES[typ]
        total = size * cnt
        off = e + 8 if total <= 4 else struct.unpack(bo + 'I', raw[e + 8:e + 12])[0]
        chunk = raw[off:off + total]
        if typ == 2:
            tags[tag] = chunk.rstrip(b'\0').decode('latin-1')
        elif typ in (5, 10):
            v = struct.unpack(bo + fmt[0] * (2 * cnt), chunk)
            tags[tag] = [v[k] / v[k + 1] for k in range(0, len(v), 2)]
        else:
            v = struct.unpack(bo + fmt * cnt, chunk)
            tags[tag] = v if cnt > 1 else v[0]
    W, H = tags[256], tags[257]
    bps = tags.get(258, 1); bps = bps[0] if isinstance(bps, tuple) else bps
    spp = tags.get(277, 1)
    if spp != 1:
        raise ValueError('only single-band rasters: ' + path)
    comp, pred = tags.get(259, 1), tags.get(317, 1)
    sf = tags.get(339, 1); sf = sf[0] if isinstance(sf, tuple) else sf
    dt = {(1, 8): 'u1', (1, 16): 'u2', (1, 32): 'u4', (2, 8): 'i1', (2, 16): 'i2', (2, 32): 'i4', (3, 32): 'f4', (3, 64): 'f8'}[(sf, bps)]
    dtype = np.dtype(dt).newbyteorder(bo)
    tiled = 322 in tags
    if tiled:
        tw, th = tags[322], tags[323]
        offs, cnts = tags[324], tags[325]
    else:
        tw, th = W, tags.get(278, H)
        offs, cnts = tags[273], tags[279]
    offs = offs if isinstance(offs, tuple) else (offs,)
    cnts = cnts if isinstance(cnts, tuple) else (cnts,)
    out = np.zeros((H, W), dtype=dtype.newbyteorder('='))
    across = (W + tw - 1) // tw
    for k, (o, c) in enumerate(zip(offs, cnts)):
        blk = raw[o:o + c]
        if comp == 5:
            blk = _lzw(blk)
        elif comp in (8, 32946):
            blk = zlib.decompress(blk)
        elif comp != 1:
            raise ValueError('compression %d unsupported' % comp)
        rows = th if tiled else min(th, H - k * th)
        a = np.frombuffer(blk[:tw * rows * dtype.itemsize], dtype=dtype).reshape(rows, tw)
        if pred == 2:
            a = np.cumsum(a, axis=1, dtype=a.dtype)
        elif pred == 3:
            b = np.frombuffer(blk[:tw * rows * dtype.itemsize], dtype='u1').reshape(rows, tw * dtype.itemsize)
            b = np.cumsum(b, axis=1, dtype='u1')
            nb = dtype.itemsize
            b = b.reshape(rows, nb, tw).transpose(0, 2, 1)   # bytes stored most significant first
            if bo == '<':
                b = b[:, :, ::-1]
            a = np.ascontiguousarray(b).view(dtype.newbyteorder('<' if bo == '<' else '>')).reshape(rows, tw)
        r0, c0 = (k // across) * th, (k % across) * tw if tiled else 0
        rr, cc = min(rows, H - r0), min(tw, W - c0)
        out[r0:r0 + rr, c0:c0 + cc] = a[:rr, :cc]
    return out, tags
