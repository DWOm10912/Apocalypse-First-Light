"""National planning layers from a Terrain V2 plan export (research; same thresholds as the real-tile metrics):
building suitability, city candidates, natural transport corridors, a port site, ecology zones and land-use potential.
  python -I national_plan.py PLAN_DIR OUT_DIR
Writes OUT_DIR/national_{suitability,rivers,ecology,landuse,elevation,landforms}.png + national_plan.json.

Suitability on a 32 m grid (2 x 2 plan cells):
  HIGH       grade <= 1/32, HAND >= 3 m (above the 2 km2 network: out of the floodplain), not resistant ridge rock,
             >= 64 m from water
  MEDIUM     grade <= 1/16, HAND >= 2 m
  LOW        grade <= 1/8
  UNSUITABLE steeper, water, marsh, floodplain (HAND < 2 m within reach of a >= 2 km2 river)
City candidates: 4-connected HIGH components >= 1.5 km2, each with its largest all-HIGH square (dynamic programming).
Corridors: least-cost paths (Dijkstra, 8-neighbour, 32 m) with cost = length x (1 + 6 (grade / (1/16))^2), x 12 above
1/8, +300 m per river crossing (A >= 2 km2), +200 m per marsh cell, no open water."""
import heapq
import json
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import national as NA  # noqa: E402
import render as R  # noqa: E402


def hand(a, min_area=2e6):
    """Height above the nearest >= min_area channel along the final receivers (16 m)."""
    n = a['n']
    h = a['h'].ravel().astype(np.float64)
    recv = a['recv'].ravel()
    land = (a['water'].ravel() == 0) | (a['water'].ravel() == 3)
    ch = land & (a['area'].ravel() >= min_area)
    order = np.argsort(h, kind='stable')            # downstream first (receivers are lower)
    ref = np.full(h.size, np.nan)
    ref[~land] = h[~land]
    rl, hl, cl, ok = ref.tolist(), h.tolist(), ch.tolist(), land.tolist()
    rv = recv.tolist()
    for i in order.tolist():
        if not ok[i]:
            continue
        if cl[i]:
            rl[i] = hl[i]
        else:
            j = rv[i]
            if j >= 0 and rl[j] == rl[j]:
                rl[i] = rl[j]
            elif j < 0:
                rl[i] = 63.0
    out = h - np.array(rl)
    return out.reshape(n, n)


def block2(x, fn=np.mean):
    n = x.shape[0] // 2 * 2
    return fn(x[:n, :n].reshape(n // 2, 2, n // 2, 2), axis=(1, 3))


def distance_to(mask, cell):
    """Chamfer (3-4) distance to the mask cells, metres; two raster passes."""
    H, W = mask.shape
    INF = 1e9
    d = np.where(mask, 0.0, INF)
    a, b = cell, cell * np.sqrt(2)
    dl = d.tolist()
    for r in range(H):
        row = dl[r]
        up = dl[r - 1] if r else None
        for c in range(W):
            v = row[c]
            if c and row[c - 1] + a < v: v = row[c - 1] + a
            if up is not None:
                if up[c] + a < v: v = up[c] + a
                if c and up[c - 1] + b < v: v = up[c - 1] + b
                if c + 1 < W and up[c + 1] + b < v: v = up[c + 1] + b
            row[c] = v
    for r in range(H - 1, -1, -1):
        row = dl[r]
        dn = dl[r + 1] if r + 1 < H else None
        for c in range(W - 1, -1, -1):
            v = row[c]
            if c + 1 < W and row[c + 1] + a < v: v = row[c + 1] + a
            if dn is not None:
                if dn[c] + a < v: v = dn[c] + a
                if c + 1 < W and dn[c + 1] + b < v: v = dn[c + 1] + b
                if c and dn[c - 1] + b < v: v = dn[c - 1] + b
            row[c] = v
    return np.array(dl)


def label4(mask):
    H, W = mask.shape
    lab = np.zeros((H, W), np.int32)
    ml, ll = mask.ravel().tolist(), lab.ravel().tolist()
    k = 0
    for s in range(H * W):
        if ml[s] and not ll[s]:
            k += 1
            ll[s] = k
            st = [s]
            while st:
                i = st.pop()
                r, c = divmod(i, W)
                for j in ((i - W) if r else -1, (i + W) if r + 1 < H else -1, (i - 1) if c else -1, (i + 1) if c + 1 < W else -1):
                    if j >= 0 and ml[j] and not ll[j]:
                        ll[j] = k
                        st.append(j)
    return np.array(ll, np.int32).reshape(H, W), k


def max_square(mask):
    H, W = mask.shape
    dp = np.zeros((H + 1, W + 1), np.int32)
    best, br, bc = 0, 0, 0
    m = mask.tolist()
    dl = dp.tolist()
    for r in range(H):
        prev, cur, row = dl[r], dl[r + 1], m[r]
        for c in range(W):
            if row[c]:
                v = 1 + min(prev[c], prev[c + 1], cur[c])
                cur[c + 1] = v
                if v > best:
                    best, br, bc = v, r, c
    return best, br - best + 1, bc - best + 1


def dijkstra(cost_fn, start, goals, H, W, passable):
    dist = np.full(H * W, np.inf)
    prev = np.full(H * W, -1, np.int64)
    s = start[0] * W + start[1]
    dist[s] = 0
    heap = [(0.0, s)]
    goals = {g[0] * W + g[1] for g in goals}
    found = set()
    dl = dist.tolist()
    pl = prev.tolist()
    pas = passable.ravel().tolist()
    nbs = [(-1, -1), (-1, 0), (-1, 1), (0, -1), (0, 1), (1, -1), (1, 0), (1, 1)]
    while heap:
        d, i = heapq.heappop(heap)
        if d > dl[i]:
            continue
        if i in goals:
            found.add(i)
            if found == goals:
                break
        r, c = divmod(i, W)
        for dr, dc in nbs:
            rr, cc = r + dr, c + dc
            if 0 <= rr < H and 0 <= cc < W:
                j = rr * W + cc
                if not pas[j]:
                    continue
                nd = d + cost_fn(i, j, dr != 0 and dc != 0)
                if nd < dl[j]:
                    dl[j] = nd
                    pl[j] = i
                    heapq.heappush(heap, (nd, j))
    return np.array(dl), np.array(pl)


def path_to(prev, g, W):
    out = []
    i = g[0] * W + g[1]
    while i >= 0:
        out.append(divmod(i, W))
        i = prev[i]
    return out[::-1]


def main():
    d, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    a = NA.load(d)
    meta = a['meta']
    n16 = a['n']
    origin, cell16 = meta['grid']['origin'], meta['grid']['cell']
    H16 = hand(a)
    water16 = a['water']
    # 32 m grids
    h = block2(a['h'].astype(np.float64))
    wat = block2((water16 == 1) | (water16 == 2), np.max)
    marsh = block2(water16 == 3, np.max) & ~wat
    area = block2(a['area'].astype(np.float64), np.max)
    res = block2(a['resist'] / 255.0, np.max)
    hd = block2(np.nan_to_num(H16, nan=0.0), np.min)
    prov = np.stack([block2(a['prov'][..., k]) for k in range(4)], -1)
    cell = cell16 * 2
    _, sl = R.shade(h, cell)
    land = ~wat
    dwat = distance_to(wat, cell)
    river = land & (area >= 2e6)
    flood = land & (hd < 2) & (distance_to(river, cell) < 400)
    near_river = distance_to(river, cell) < 400
    cls = np.full(h.shape, 0, np.uint8)            # 0 unsuitable, 1 low, 2 medium, 3 high
    cls[land & (sl <= 1 / 8)] = 1
    cls[land & (sl <= 1 / 16)] = 2
    cls[land & (sl <= 1 / 32) & ((hd >= 3) | ~near_river) & (res < 0.1) & (dwat >= 64)] = 3
    cls[marsh | flood | wat] = 0
    landN = int(land.sum())
    shares = {k: round(float(((cls == v) & land).sum()) / landN, 4) for k, v in (('high', 3), ('medium', 2), ('low', 1), ('unsuitable', 0))}
    # city candidates
    lab, k = label4(cls >= 2)          # a city spans small creek valleys: HIGH and MEDIUM together
    sizes = np.bincount(lab.ravel(), minlength=k + 1)
    cands = []
    for cid in np.argsort(-sizes[1:])[:12] + 1:
        km2 = sizes[cid] * cell * cell / 1e6
        if km2 < 1.5:
            break
        sq, r0, c0 = max_square(lab == cid)
        cr, cc = r0 + sq / 2, c0 + sq / 2
        x, z = origin + cc * cell, origin + cr * cell
        rows, cols = np.nonzero(lab == cid)
        cands.append({'id': 'C%d' % (len(cands) + 1), 'area_km2': round(km2, 2), 'square_m': int(sq * cell),
                      'high_share': round(float((cls[lab == cid] == 3).mean()), 3),
                      'centre_xz': [round(x), round(z)], 'square_rc': [int(r0), int(c0), int(sq)],
                      'mean_y': round(float(h[lab == cid].mean()), 1),
                      'extent_m': [int((cols.max() - cols.min() + 1) * cell), int((rows.max() - rows.min() + 1) * cell)],
                      'to_water_m': round(float(dwat[int(cr), int(cc)]))})
    # port site: an estuary / coastal cell whose 600 m neighbourhood holds the most HIGH / MEDIUM land
    est16 = water16 == 2
    est = block2(est16, np.max)
    good = (cls >= 2).astype(np.float64)
    S = np.zeros((h.shape[0] + 1, h.shape[1] + 1))
    S[1:, 1:] = np.cumsum(np.cumsum(good, 0), 1)
    rr = 19
    best, port = -1, None
    for r, c in zip(*np.nonzero(est)):
        r0, r1, c0, c1 = max(0, r - rr), min(h.shape[0], r + rr + 1), max(0, c - rr), min(h.shape[1], c + rr + 1)
        v = S[r1, c1] - S[r0, c1] - S[r1, c0] + S[r0, c0]
        if v > best:
            best, port = v, (int(r), int(c))
    # corridors between candidates (minimum spanning tree on straight distance) and from the port inland
    estl = est.ravel().tolist()
    pas = land | est                   # estuaries only by bridge
    hl = h.ravel().tolist()
    rivl = river.ravel().tolist()
    marl = marsh.ravel().tolist()
    W = h.shape[1]

    def cost(i, j, diag):
        step = cell * (1.4142 if diag else 1.0)
        g = abs(hl[j] - hl[i]) / step
        c = step * (1 + 6 * (g / 0.0625) ** 2)
        if g > 0.125:
            c *= 12
        if rivl[j] and not rivl[i]:
            c += 150
        if estl[j]:
            c += 1500
        if marl[j]:
            c += 200
        return c

    nodes = [(int(c['square_rc'][0] + c['square_rc'][2] / 2), int(c['square_rc'][1] + c['square_rc'][2] / 2)) for c in cands[:6]]
    names = [c['id'] for c in cands[:6]]
    if port:
        nodes.append(port)
        names.append('PORT')
    # MST (Prim) on straight-line distance
    edges, used = [], {0}
    while len(used) < len(nodes):
        best = None
        for u in used:
            for v in range(len(nodes)):
                if v in used:
                    continue
                dd = np.hypot(nodes[u][0] - nodes[v][0], nodes[u][1] - nodes[v][1])
                if best is None or dd < best[0]:
                    best = (dd, u, v)
        edges.append((best[1], best[2]))
        used.add(best[2])
    paths, links = [], []
    for u, v in edges:
        _, prev = dijkstra(cost, nodes[u], [nodes[v]], h.shape[0], W, pas)
        p = path_to(prev, nodes[v], W)
        if len(p) < 2:
            continue
        zs = np.array([h[r, c] for r, c in p])
        steps = np.array([cell * np.hypot(p[k + 1][0] - p[k][0], p[k + 1][1] - p[k][1]) for k in range(len(p) - 1)])
        g = np.abs(np.diff(zs)) / steps
        # grade over 96 m (3 cells), the scale a road's profile sees
        L = np.cumsum(np.r_[0, steps])
        g96 = [abs(np.interp(L[k] + 96, L, zs) - zs[k]) / 96 for k in range(len(p)) if L[k] + 96 <= L[-1]]
        cross = int(sum(1 for k in range(1, len(p)) if river[p[k]] and not river[p[k - 1]]))
        links.append({'from': names[u], 'to': names[v], 'length_m': round(float(steps.sum())),
                      'grade96_max': round(float(max(g96)), 4) if g96 else None,
                      'grade96_le_1_16': round(float(np.mean(np.array(g96) <= 1 / 16)), 3) if g96 else None,
                      'river_crossings': cross,
                      'crosses_belt': bool(any(prov[r, c, 3] > 0.5 for r, c in p))})
        paths.append(p)
    # two national trunk corridor proposals: A along the long axis through the candidates (ordered along the island's
    # long axis), B from the port across the fold belt to the largest inland candidate, free and through the main gap
    ang = np.radians(meta['frame'][2])
    def along(nd):
        x, z = origin + nd[1] * cell, origin + nd[0] * cell
        return x * np.cos(ang) + z * np.sin(ang)
    trunks = []
    def route(name, seq):
        full = []
        for u, v in zip(seq, seq[1:]):
            _, prev = dijkstra(cost, u, [v], h.shape[0], W, pas)
            seg = path_to(prev, v, W)
            if len(seg) < 2:
                return
            full += seg if not full else seg[1:]
        zs = np.array([h[r, c] for r, c in full])
        steps = np.array([cell * np.hypot(full[k + 1][0] - full[k][0], full[k + 1][1] - full[k][1]) for k in range(len(full) - 1)])
        L = np.cumsum(np.r_[0, steps])
        g96 = np.array([abs(np.interp(L[k] + 96, L, zs) - zs[k]) / 96 for k in range(len(full)) if L[k] + 96 <= L[-1]])
        trunks.append({'name': name, 'length_m': round(float(steps.sum())), 'grade96_max': round(float(g96.max()), 4),
                       'grade96_le_1_16': round(float((g96 <= 1 / 16).mean()), 3), 'max_y': round(float(zs.max()), 1),
                       'river_crossings': int(sum(1 for k in range(1, len(full)) if river[full[k]] and not river[full[k - 1]])),
                       'bridges_estuary_cells': int(sum(1 for rc in full if est[rc])),
                       'crosses_belt': bool(any(prov[r, c, 3] > 0.5 for r, c in full))})
        paths.append(full)
    order_a = sorted(range(min(6, len(cands))), key=lambda k: along(nodes[k]))
    big = max(range(min(6, len(cands))), key=lambda k: cands[k]['area_km2'])
    seq = [order_a[0]] + ([big] if big not in (order_a[0], order_a[-1]) else []) + [order_a[-1]]
    seq = sorted(seq, key=lambda k: along(nodes[k]))
    route('A 长轴干线（%s）' % '–'.join(cands[k]['id'] for k in seq), [nodes[k] for k in seq])
    if port:
        inland = max(range(min(6, len(cands))), key=lambda k: cands[k]['area_km2'])
        route('B 港口—内陆（最省力）', [port, nodes[inland]])
        tr = block2(a['trunk'] == 1, np.max) & (prov[..., 3] > 0.5) & land
        if tr.any():
            pr, pc = port
            ir, ic = nodes[inland]
            rr_, cc_ = np.nonzero(tr)
            k = np.argmin(np.hypot(rr_ - (pr + ir) / 2, cc_ - (pc + ic) / 2))
            route('B′ 港口—内陆（经穿山缺口）', [port, (int(rr_[k]), int(cc_[k])), nodes[inland]])
    # ---- maps
    r0, r1, c0, c1 = [x // 2 for x in NA.crop_box(a)]
    hs, _ = R.shade(h, cell)
    kshade = (0.45 + 0.55 * hs)[..., None] / (0.45 + 0.55 * 0.7071)
    cols = {0: (120, 112, 120), 1: (222, 160, 128), 2: (236, 214, 140), 3: (122, 186, 150)}
    img = np.zeros(h.shape + (3,))
    for v, c in cols.items():
        img[cls == v] = c
    img = np.clip(img * kshade, 0, 255)
    img[wat] = R.WATER
    img[marsh] = (130, 160, 128)
    for p in paths:
        for rc in p:
            img[rc] = (150, 40, 90)
    for c in cands[:6]:
        r_, c_, s_ = c['square_rc']
        img[r_, c_:c_ + s_] = img[r_ + s_ - 1, c_:c_ + s_] = (30, 40, 60)
        img[r_:r_ + s_, c_] = img[r_:r_ + s_, c_ + s_ - 1] = (30, 40, 60)
    if port:
        pr, pc = port
        img[max(0, pr - 4):pr + 5, max(0, pc - 4):pc + 5] = (200, 30, 30)
    R.png(R.upscale(img.astype(np.uint8)[r0:r1, c0:c1], 2), os.path.join(out, 'national_suitability.png'))
    # rivers map: hillshade grey + Strahler-scaled blue
    g16, _ = R.shade(a['h'], cell16)
    base = np.repeat((214 + 30 * (g16 - 0.7))[..., None], 3, 2)
    base[(water16 == 1) | (water16 == 2)] = (176, 200, 222)
    base[water16 == 3] = (186, 206, 180)
    st = a['strahler'].astype(int)
    A = a['area']
    show = (water16 == 0) & (A >= 0.25e6)
    col = np.array([[150, 180, 220], [110, 150, 210], [70, 115, 195], [40, 85, 175], [20, 60, 150], [10, 40, 120], [5, 25, 100]])
    o = np.clip(st - 1, 0, 6)
    base[show] = col[o[show]]
    big = (water16 == 0) & (A >= 8e6)
    base[big] = (8, 40, 130)
    R.png(np.clip(base, 0, 255).astype(np.uint8)[r0 * 2:r1 * 2, c0 * 2:c1 * 2], os.path.join(out, 'national_rivers.png'))
    # elevation map with Y contours every 20
    hh = a['h'].astype(np.float64)
    sea = (water16 == 1) | (water16 == 2)
    el = R.panel(hh, cell16, sea, 'tint', None, ref=63.0).astype(np.float64)
    deep = np.clip((63 - hh) / 60, 0, 1)
    el[sea] = np.array(R.WATER) * (1 - 0.35 * deep[sea, None])
    q = np.floor((hh - 63) / 20)
    edge = (q != np.roll(q, 1, 0)) | (q != np.roll(q, 1, 1))
    el[edge & ~sea] *= 0.72
    el[water16 == 3] = el[water16 == 3] * 0.5 + np.array([120, 150, 110]) * 0.5
    R.png(np.clip(el, 0, 255).astype(np.uint8)[r0 * 2:r1 * 2, c0 * 2:c1 * 2], os.path.join(out, 'national_elevation.png'))
    # landforms: provinces + ridges + rivers
    R.png(NA.province_map(a)[r0 * 2:r1 * 2, c0 * 2:c1 * 2], os.path.join(out, 'national_landforms.png'))
    # ecology zones (potential natural vegetation) and land-use potential, from the NLCD cross-tabs of the tiles
    eco, eco_share = ecology(a, H16, cell16)
    R.png(eco[r0 * 2:r1 * 2, c0 * 2:c1 * 2], os.path.join(out, 'national_ecology.png'))
    json.dump({'suitability_share_of_land': shares, 'city_candidates': cands, 'port': None if not port else
               {'centre_xz': [round(origin + port[1] * cell), round(origin + port[0] * cell)]}, 'corridors': links,
               'trunks': trunks, 'ecology_share_of_land': eco_share, 'crop_rows_cols_32m': [int(r0), int(r1), int(c0), int(c1)]},
              open(os.path.join(out, 'national_plan.json'), 'w', encoding='utf-8'), indent=1, ensure_ascii=False)
    print(json.dumps({'shares': shares, 'cands': cands, 'links': links, 'trunks': trunks, 'eco': eco_share}, ensure_ascii=False))


ECO = [
    ('agri_plain', '农业冰碛平原（潜在植被：栎-山核桃 / 山毛榉-槭阔叶林，现以农田为主）', (214, 206, 138)),
    ('riparian', '河岸与河漫滩林（悬铃木、三角叶杨、银槭）', (104, 160, 112)),
    ('slope_forest', '坡地与冲沟落叶阔叶林（混合中生林）', (128, 168, 102)),
    ('ridge_oak', '山脊栎林（栎类混交林，岩坡）', (150, 140, 96)),
    ('hollow_conifer', '山谷阴坡铁杉-白松（局部针叶林）', (70, 112, 86)),
    ('coastal_pine', '海岸平原松栎林（火炬松、栎）', (170, 186, 120)),
    ('swamp_forest', '海岸沼泽林', (96, 136, 118)),
    ('tidal_marsh', '潮汐盐沼 / 草本湿地', (150, 186, 170)),
    ('wet_prairie', '平原湿草甸与洼地', (176, 200, 150)),
]


def ecology(a, H16, cell):
    hh = a['h'].astype(np.float64)
    water = a['water']
    P = a['prov']
    res = a['resist'] / 255.0
    gy, gx = np.gradient(hh, cell)       # gy: +row = south
    sl = np.hypot(gx, gy)
    north = -gy / np.maximum(sl, 1e-6)  # aspect toward north (> 0 : faces north)
    hand = np.nan_to_num(H16, nan=99)
    A = a['area']
    land = water == 0
    z = np.full(hh.shape, -1, np.int8)
    plain = P[..., 0] + P[..., 1] * 0
    z[land] = 0
    z[land & (sl > 0.08)] = 2
    z[land & (P[..., 1] > 0.5) & (sl <= 0.08)] = 5
    z[land & ((res > 0.2) | ((P[..., 3] > 0.5) & (sl > 0.15)))] = 3
    z[land & (P[..., 3] + P[..., 2] > 0.5) & (sl > 0.12) & (north > 0.5) & (hand < 25)] = 4
    near = land & (hand < 3)
    z[near & (P[..., 1] <= 0.5)] = 1
    z[near & (P[..., 1] > 0.5)] = 6
    z[land & (P[..., 0] > 0.6) & (hand < 1.0) & (sl < 0.006) & (A < 2e5)] = 8
    z[water == 3] = 7
    rgb = np.zeros(hh.shape + (3,))
    for k, (_, _, c) in enumerate(ECO):
        rgb[z == k] = c
    hs, _ = R.shade(hh, cell)
    rgb = rgb * ((0.5 + 0.5 * hs)[..., None] / (0.5 + 0.5 * 0.7071))
    rgb[(water == 1) | (water == 2)] = R.WATER
    A2 = (water == 0) & (A >= 1e6)
    rgb[A2] = (70, 110, 170)
    tot = max(1, int((z >= 0).sum()))
    share = {ECO[k][0]: round(float((z == k).sum()) / tot, 4) for k in range(len(ECO))}
    return np.clip(rgb, 0, 255).astype(np.uint8), share


if __name__ == '__main__':
    main()
