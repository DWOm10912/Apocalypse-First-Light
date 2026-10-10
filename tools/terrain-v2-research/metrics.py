"""Terrain metrics for Terrain V2 (2026-10-10). The SAME functions measure the USGS reference tiles and AFL's generated
terrain, so every comparison uses one method. Heights in metres (AFL: blocks), cell size in metres.

Scales kept apart (the user's brief): tens of metres (surface detail), hundreds of metres (hills, shallow valleys),
kilometres (ridges, landform regions). Absolute elevation is reported but only relative relief is compared.

Methods (all on a regular grid z[row, col], row 0 = north):
- slope: Horn (1981) 3x3 finite difference, rise / run; also on block-averaged grids (30 / 90 m) to see the scale effect
- relief(k): max - min in non-overlapping k x k blocks; rms(k): RMS about the least-squares plane in each block
- band_rms: Parseval split of the plane-removed, Hann-windowed DEM's 2D power spectrum into octave wavelength bands
  (20-40 m ... 5-10 km): the height variation each scale contributes
- hydrology: priority-flood depression filling (Barnes et al. 2014) with a 1e-4 m gradient, D8 steepest descent,
  flow accumulation; channels = cells draining >= A_c; Strahler order, confluences, channel heads, drainage density,
  hillslope flow length to the channel, HAND (height above nearest drainage along the flow path, Renno et al. 2008),
  channel spacing along E-W / N-S transects
- buildable: slope (30 m) <= 1/32 (or 1/16) and HAND >= 3 m and not water; 4-connected components
- ridges: TPI (height above the 1.5 km box mean) > 30 m; components >= 0.25 km^2; PCA length / width; the crest
  profile along the axis in 100 m bins (variation, saddles, gaps)
Known limits: a 10 km tile cuts catchments at its edges (edge cells drain out); DEMs carry roads, ditches and field
edges (lidar most); D8 on flat farmland follows ditches and culvert-less road fills."""
import heapq
import numpy as np

D8 = [(-1, -1), (-1, 0), (-1, 1), (0, -1), (0, 1), (1, -1), (1, 0), (1, 1)]


def slope(z, cell):
    p = np.pad(z.astype(np.float64), 1, mode='edge')
    a, b, c = p[:-2, :-2], p[:-2, 1:-1], p[:-2, 2:]
    d, f = p[1:-1, :-2], p[1:-1, 2:]
    g, h, i = p[2:, :-2], p[2:, 1:-1], p[2:, 2:]
    gx = ((c + 2 * f + i) - (a + 2 * d + g)) / (8 * cell)
    gy = ((g + 2 * h + i) - (a + 2 * b + c)) / (8 * cell)
    return np.hypot(gx, gy)


def block(z, k, fn=np.mean):
    H, W = (z.shape[0] // k) * k, (z.shape[1] // k) * k
    return fn(z[:H, :W].reshape(H // k, k, W // k, k), axis=(1, 3))


def blocks(z, k):
    H, W = (z.shape[0] // k) * k, (z.shape[1] // k) * k
    return z[:H, :W].reshape(H // k, k, W // k, k).transpose(0, 2, 1, 3)   # [bi, bj, k, k]


def relief(z, k, valid=None):
    b = blocks(z, k)
    r = b.max(axis=(2, 3)) - b.min(axis=(2, 3))
    return r[valid_blocks(valid, k)] if valid is not None else r.ravel()


def rms(z, k, valid=None):
    b = blocks(z.astype(np.float64), k)
    u = np.arange(k) - (k - 1) / 2
    U, V = np.meshgrid(u, u)
    m = b.mean(axis=(2, 3), keepdims=True)
    bx = (b * U).sum(axis=(2, 3), keepdims=True) / (U * U).sum()
    by = (b * V).sum(axis=(2, 3), keepdims=True) / (V * V).sum()
    r = np.sqrt(((b - m - bx * U - by * V) ** 2).mean(axis=(2, 3)))
    return r[valid_blocks(valid, k)] if valid is not None else r.ravel()


def valid_blocks(valid, k):
    return blocks(valid.astype(np.float32), k).mean(axis=(2, 3)) >= 0.999


def pct(a, ps=(5, 25, 50, 75, 95, 99)):
    a = np.asarray(a, dtype=np.float64).ravel()
    if a.size == 0:
        return None
    return {('p%d' % p): round(float(np.percentile(a, p)), 4) for p in ps} | {'mean': round(float(a.mean()), 4)}


def band_rms(z, cell, bands=None):
    """Height RMS (m) contributed by each octave wavelength band, Parseval on the plane-removed, Hann-windowed grid."""
    z = z.astype(np.float64)
    H, W = z.shape
    yy, xx = np.mgrid[0:H, 0:W]
    A = np.c_[np.ones(z.size), xx.ravel(), yy.ravel()]
    coef, *_ = np.linalg.lstsq(A, z.ravel(), rcond=None)
    d = z - (A @ coef).reshape(H, W)
    w = np.outer(np.hanning(H), np.hanning(W))
    F = np.fft.fft2(d * w)
    P = (np.abs(F) ** 2) / (H * W) ** 2 / (w ** 2).mean()
    fy = np.fft.fftfreq(H, cell)[:, None]
    fx = np.fft.fftfreq(W, cell)[None, :]
    f = np.hypot(fx, fy)
    out = {}
    lo = 2 * cell
    bands = bands or [(lo * 2 ** i, lo * 2 ** (i + 1)) for i in range(16) if lo * 2 ** (i + 1) <= min(H, W) * cell]
    for a, b in bands:
        sel = (f > 1 / b) & (f <= 1 / a)
        out['%g-%g m' % (a, b)] = round(float(np.sqrt(P[sel].sum())), 4)
    return out


# ---------------- hydrology ----------------
def hydrology(z, cell, outlet=None, eps=1e-4):
    """Priority-flood fill, D8, accumulation (cells), topological order. outlet: extra sink cells (open water)."""
    H, W = z.shape
    n = H * W
    zf = z.astype(np.float64).ravel().copy()
    done = np.zeros(n, dtype=bool)
    heap = []
    edge = np.zeros((H, W), dtype=bool)
    edge[0, :] = edge[-1, :] = edge[:, 0] = edge[:, -1] = True
    if outlet is not None:
        edge |= outlet
    for i in np.flatnonzero(edge.ravel()):
        heap.append((zf[i], int(i)))
        done[i] = True
    heapq.heapify(heap)
    offs = [(dr, dc) for dr, dc in D8]
    while heap:
        e, i = heapq.heappop(heap)
        r, c = divmod(i, W)
        for dr, dc in offs:
            rr, cc = r + dr, c + dc
            if 0 <= rr < H and 0 <= cc < W:
                j = rr * W + cc
                if not done[j]:
                    done[j] = True
                    if zf[j] <= e:
                        zf[j] = e + eps
                    heapq.heappush(heap, (zf[j], j))
    zf2 = zf.reshape(H, W)
    # D8 steepest descent on the filled surface; sinks (edge / outlet) point nowhere
    best = np.zeros((H, W))
    down = -np.ones((H, W), dtype=np.int64)
    p = np.pad(zf2, 1, mode='constant', constant_values=np.inf)
    idx = np.arange(n).reshape(H, W)
    pidx = np.pad(idx, 1, mode='constant', constant_values=-1)
    for dr, dc in D8:
        nb = p[1 + dr:1 + dr + H, 1 + dc:1 + dc + W]
        drop = (zf2 - nb) / (np.hypot(dr, dc))
        better = drop > best
        best = np.where(better, drop, best)
        down = np.where(better, pidx[1 + dr:1 + dr + H, 1 + dc:1 + dc + W], down)
    down[edge] = -1
    down = down.ravel()
    order = np.argsort(-zf, kind='stable')          # upstream first
    acc = np.ones(n, dtype=np.float64)
    dl = down.tolist()
    al = acc.tolist()
    for i in order.tolist():
        d = dl[i]
        if d >= 0:
            al[d] += al[i]
    acc = np.array(al).reshape(H, W)
    return {'filled': zf2, 'down': down, 'order': order, 'acc': acc, 'sink': edge}


def network(hyd, z, cell, area_m2, water=None):
    """Channels draining >= area_m2: drainage density, Strahler order, confluences, heads, HAND, flow length."""
    H, W = z.shape
    n = H * W
    ch = (hyd['acc'] * cell * cell >= area_m2)
    if water is not None:
        ch = ch & ~water
    chl = ch.ravel().tolist()
    down = hyd['down'].tolist()
    order = hyd['order'].tolist()
    up_ch = [0] * n
    mx = [0] * n
    cnt = [0] * n
    strahler = [0] * n
    for i in order:                                   # upstream first
        if not chl[i]:
            continue
        o = 1 if mx[i] == 0 else (mx[i] + 1 if cnt[i] >= 2 else mx[i])
        strahler[i] = o
        d = down[i]
        if d >= 0 and chl[d]:
            up_ch[d] += 1
            if o > mx[d]:
                mx[d], cnt[d] = o, 1
            elif o == mx[d]:
                cnt[d] += 1
    zl = z.ravel().astype(np.float64).tolist()
    ref = [0.0] * n
    flen = [0.0] * n
    step = [0.0] * n
    for i in range(n):
        d = down[i]
        if d >= 0:
            step[i] = cell * (1.4142135623730951 if (d - i) not in (1, -1, W, -W) else 1.0)
    hand_ok = [False] * n
    for i in reversed(order):                         # downstream first
        if chl[i]:
            ref[i], flen[i], hand_ok[i] = zl[i], 0.0, True
        else:
            d = down[i]
            if d >= 0 and hand_ok[d]:
                ref[i], flen[i], hand_ok[i] = ref[d], flen[d] + step[i], True
    zr = np.array(zl).reshape(H, W)
    hand = np.where(np.array(hand_ok).reshape(H, W), zr - np.array(ref).reshape(H, W), np.nan)
    flen = np.where(np.array(hand_ok).reshape(H, W), np.array(flen).reshape(H, W), np.nan)
    st = np.array(strahler).reshape(H, W)
    upc = np.array(up_ch).reshape(H, W)
    land = ~water if water is not None else np.ones((H, W), bool)
    area_km2 = land.sum() * cell * cell / 1e6
    # channel length: each channel cell's step to its downstream cell (a cell draining to a sink counts one cell)
    sl = np.array(step).reshape(H, W)
    length_km = float((sl * ch).sum() / 1000 + ((sl == 0) & ch).sum() * cell / 1000)
    by_order = {}
    for o in range(1, int(st.max()) + 1 if st.max() > 0 else 1):
        by_order[str(o)] = int(((st == o) & ch).sum())
    heads = int((ch & (upc == 0)).sum())
    confl = int((ch & (upc >= 2)).sum())
    return {'channels': ch, 'strahler': st, 'hand': hand, 'flow_len': flen, 'stats': {
        'threshold_km2': area_m2 / 1e6, 'drainage_density_km_per_km2': round(length_km / area_km2, 3),
        'channel_heads_per_km2': round(heads / area_km2, 3), 'confluences_per_km2': round(confl / area_km2, 3),
        'max_strahler': int(st.max()), 'cells_by_order': by_order,
        'hillslope_flow_length_m': pct(flen[land & ~ch & np.isfinite(flen)]),
        'hand_m': pct(hand[land & np.isfinite(hand)])}}


def stream_counts(net, hyd):
    """Number of Strahler streams per order and the bifurcation ratios (a stream starts at a head or where two of
    the next lower order meet)."""
    st, ch = net['strahler'].ravel(), net['channels'].ravel()
    down = hyd['down']
    n = st.size
    starts = np.zeros(n, dtype=np.int64)
    # a channel cell starts a stream when no upstream channel cell has its order
    has_same = np.zeros(n, dtype=bool)
    src = np.flatnonzero(ch & (down >= 0))
    dst = down[src]
    ok = ch[dst]
    src, dst = src[ok], dst[ok]
    same = st[src] == st[dst]
    has_same[dst[same]] = True
    begins = ch & ~has_same
    counts = {}
    for o in range(1, int(st.max()) + 1):
        counts[str(o)] = int((begins & (st == o)).sum())
    ratios = {}
    for o in range(1, int(st.max())):
        a, b = counts[str(o)], counts[str(o + 1)]
        if b:
            ratios['%d/%d' % (o, o + 1)] = round(a / b, 2)
    return counts, ratios


def transect_spacing(ch, water, cell, every=25):
    """Distance between successive channel crossings along E-W and N-S lines every `every` cells (water breaks a line)."""
    gaps = []
    for lines in (ch, ch.T):
        wl = water if lines is ch else water.T
        for r in range(every // 2, lines.shape[0], every):
            row, wrow = lines[r], wl[r]
            pos = []
            run = None
            for c in range(row.size):
                if row[c] and run is None:
                    run = c
                if (not row[c]) and run is not None:
                    pos.append((run + c - 1) / 2)
                    run = None
                if wrow[c]:
                    if pos:
                        gaps.extend(np.diff(pos).tolist())
                    pos, run = [], None
            gaps.extend(np.diff(pos).tolist())
    g = np.array(gaps) * cell
    return pct(g) if g.size else None


# ---------------- components / ridges ----------------
def label(mask, conn=4):
    H, W = mask.shape
    lab = np.zeros((H, W), dtype=np.int32)
    ml = mask.ravel().tolist()
    ll = lab.ravel().tolist()
    nbs = [(-1, 0), (1, 0), (0, -1), (0, 1)] + ([(-1, -1), (-1, 1), (1, -1), (1, 1)] if conn == 8 else [])
    k = 0
    for s in range(H * W):
        if ml[s] and not ll[s]:
            k += 1
            ll[s] = k
            stack = [s]
            while stack:
                i = stack.pop()
                r, c = divmod(i, W)
                for dr, dc in nbs:
                    rr, cc = r + dr, c + dc
                    if 0 <= rr < H and 0 <= cc < W:
                        j = rr * W + cc
                        if ml[j] and not ll[j]:
                            ll[j] = k
                            stack.append(j)
    return np.array(ll, dtype=np.int32).reshape(H, W), k


def box_mean(z, r):
    """Mean over a (2r+1)^2 box, edges by shrinking the box (integral image)."""
    H, W = z.shape
    S = np.zeros((H + 1, W + 1))
    S[1:, 1:] = np.cumsum(np.cumsum(z.astype(np.float64), 0), 1)
    r0 = np.clip(np.arange(H) - r, 0, H); r1 = np.clip(np.arange(H) + r + 1, 0, H)
    c0 = np.clip(np.arange(W) - r, 0, W); c1 = np.clip(np.arange(W) + r + 1, 0, W)
    tot = S[r1][:, c1] - S[r0][:, c1] - S[r1][:, c0] + S[r0][:, c0]
    cnt = (r1 - r0)[:, None] * (c1 - c0)[None, :]
    return tot / cnt


def ridges(z, cell, water, window_m=1500, tpi_min=30, min_km2=0.25):
    tpi = z - box_mean(z, int(window_m / cell / 2))
    mask = (tpi > tpi_min) & ~water
    lab, k = label(mask, 8)
    out = []
    ys, xs = np.mgrid[0:z.shape[0], 0:z.shape[1]]
    for i in range(1, k + 1):
        sel = lab == i
        area = sel.sum() * cell * cell / 1e6
        if area < min_km2:
            continue
        px, py = xs[sel] * cell, ys[sel] * cell
        cx, cy = px.mean(), py.mean()
        cov = np.cov(np.vstack([px - cx, py - cy]))
        ev, evec = np.linalg.eigh(cov)
        major = evec[:, 1]
        length = float(np.sqrt(12 * ev[1]))
        width = area * 1e6 / length
        t = (px - cx) * major[0] + (py - cy) * major[1]
        zz = z[sel]
        bins = np.floor((t - t.min()) / 100).astype(int)
        crest = np.full(bins.max() + 1, np.nan)
        np.fmax.at(crest, bins, zz)
        crest = crest[np.isfinite(crest)]
        base = float(np.percentile(z[~water], 5))
        top = float(np.median(crest))
        saddles = 0
        for j in range(1, len(crest) - 1):
            left, right = np.nanmax(crest[:j]), np.nanmax(crest[j + 1:])
            if crest[j] < crest[j - 1] and crest[j] <= crest[j + 1] and min(left, right) - crest[j] >= 20:
                saddles += 1
        out.append({'area_km2': round(area, 3), 'length_m': round(length), 'width_m': round(width),
                    'strike_deg': round(float(np.degrees(np.arctan2(-major[1], major[0]))) % 180, 1),
                    'crest_above_tile_p5_m': round(top - base, 1), 'crest_std_m': round(float(np.std(crest)), 1),
                    'crest_range_m': round(float(np.ptp(crest)), 1), 'saddles_20m': saddles,
                    'max_tpi_m': round(float(tpi[sel].max()), 1), 'cx_m': round(float(cx)), 'cy_m': round(float(-cy))})
    return tpi, out


def orientation(z, cell):
    """Dominant strike from the gradient structure tensor; coherence 0 (isotropic) .. 1 (one direction)."""
    gy, gx = np.gradient(z.astype(np.float64), cell)
    jxx, jyy, jxy = (gx * gx).mean(), (gy * gy).mean(), (gx * gy).mean()
    ang = 0.5 * np.arctan2(2 * jxy, jxx - jyy)     # gradient (dip) direction, image axes (y down)
    coh = np.sqrt((jxx - jyy) ** 2 + 4 * jxy ** 2) / (jxx + jyy)
    strike = (np.degrees(-ang) + 90) % 180         # map axes: degrees from east, counter-clockwise
    return round(float(strike), 1), round(float(coh), 3)


def crossstrike_spacing(z, cell, strike_deg, water):
    """Dominant ridge spacing across strike: autocorrelation of profiles taken perpendicular to the strike."""
    th = np.radians(strike_deg)
    dip = np.array([np.cos(th + np.pi / 2), -np.sin(th + np.pi / 2)])   # image (col, row) step across strike
    H, W = z.shape
    L = int(min(H, W) * 0.7)
    prof = []
    for off in np.linspace(-0.3, 0.3, 13):
        c0 = W / 2 + off * W * np.cos(th) - dip[0] * L / 2
        r0 = H / 2 - off * H * np.sin(th) - dip[1] * L / 2
        cc = c0 + dip[0] * np.arange(L)
        rr = r0 + dip[1] * np.arange(L)
        ok = (cc >= 0) & (cc < W - 1) & (rr >= 0) & (rr < H - 1)
        if ok.mean() < 0.95:
            continue
        p = z[rr[ok].astype(int), cc[ok].astype(int)].astype(np.float64)
        p = p - np.polyval(np.polyfit(np.arange(p.size), p, 1), np.arange(p.size))
        prof.append(p)
    if not prof:
        return None
    ac = np.zeros(L)
    for p in prof:
        a = np.correlate(p, p, 'full')[p.size - 1:] / (p * p).sum()
        ac[:a.size] += a / len(prof)
    # first local maximum after the first zero crossing
    zc = np.argmax(ac < 0)
    if zc == 0:
        return None
    k = zc + np.argmax(ac[zc:L // 2])
    return {'spacing_m': round(float(k * cell)), 'autocorr_at_peak': round(float(ac[k]), 3)}
