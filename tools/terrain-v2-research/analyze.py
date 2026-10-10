"""Measure one terrain tile with metrics.py: a USGS reference tile (raw/<id>/dem10.tif + nlcd2021.tif + lidar1/2.tif)
or an AFL window exported by the planner (PlanV2Export: <id>.json + raw rasters).
  python -I tools/terrain-v2-research/analyze.py real RAW_DIR TILE_ID OUT_DIR [SCALE [CROP_CELLS]]
  python -I tools/terrain-v2-research/analyze.py afl WINDOW_JSON OUT_DIR
Writes OUT_DIR/<id>.json (statistics) and OUT_DIR/<id>.npz (slope, HAND, channels, TPI... for the renderer)."""
import json
import os
import sys
import time

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import metrics as M  # noqa: E402
import tiff  # noqa: E402

NLCD_GROUP = {11: 'water', 21: 'developed', 22: 'developed', 23: 'developed', 24: 'developed', 31: 'barren',
              41: 'forest_deciduous', 42: 'forest_evergreen', 43: 'forest_mixed', 52: 'shrub', 71: 'grassland',
              81: 'pasture_hay', 82: 'cropland', 90: 'wetland_woody', 95: 'wetland_herbaceous'}
SLOPE_CLASSES = [0, 1 / 100, 1 / 32, 1 / 16, 1 / 8, 1 / 4, 1 / 2, 99]


def slope_classes(s):
    h, _ = np.histogram(s, bins=SLOPE_CLASSES)
    names = ['<1%', '1-3.1%', '3.1-6.25%', '6.25-12.5%', '12.5-25%', '25-50%', '>50%']
    return {k: round(float(v) / max(1, s.size), 4) for k, v in zip(names, h)}


def fine_window(z, cell):
    land = np.ones(z.shape, bool)
    s = M.slope(z, cell)
    out = {'cell_m': cell, 'slope': M.pct(s), 'slope_classes': slope_classes(s.ravel())}
    for k_m in (4, 8, 16, 32, 64, 128):
        k = int(round(k_m / cell))
        if k >= 2:
            out['rms_%dm' % k_m] = M.pct(M.rms(z, k, land), (50, 90))
            out['relief_%dm' % k_m] = M.pct(M.relief(z, k, land), (50, 90))
    out['band_rms_m'] = M.band_rms(z, cell)
    if cell == 1:
        out['slope_2m'] = M.pct(M.slope(M.block(z, 2), 2))
    return out


def analyze(tid, z, cell, water, cover=None, fine=None):
    t0 = time.time()
    land = ~water
    st = {'id': tid, 'cell_m': cell, 'size_m': [z.shape[1] * cell, z.shape[0] * cell], 'land_fraction': round(float(land.mean()), 4)}
    zl = z[land]
    st['elevation_abs_m'] = M.pct(zl, (0, 1, 5, 50, 95, 99, 100))
    base = float(np.percentile(zl, 1))
    st['elevation_rel_m'] = M.pct(zl - base, (5, 25, 50, 75, 95, 99, 100))
    st['hypsometric_integral'] = round(float((zl.mean() - zl.min()) / max(1e-6, zl.max() - zl.min())), 3)
    s10 = M.slope(z, cell)
    z30 = M.block(z, 3)
    w30 = M.block(water.astype(np.float32), 3) > 0.5
    s30 = M.slope(z30, cell * 3)
    s90 = M.slope(M.block(z, 9), cell * 9)
    w90 = M.block(water.astype(np.float32), 9) > 0.5
    st['slope'] = {'%dm' % cell: M.pct(s10[land]), '%dm' % (cell * 3): M.pct(s30[~w30]), '%dm' % (cell * 9): M.pct(s90[~w90])}
    st['slope_classes'] = {'%dm' % cell: slope_classes(s10[land]), '%dm' % (cell * 3): slope_classes(s30[~w30])}
    st['relief_m'] = {}
    st['rms_about_plane_m'] = {}
    for k in (3, 10, 30, 100, 300):
        if k * cell <= z.shape[0] * cell / 2:
            st['relief_m']['%dm' % (k * cell)] = M.pct(M.relief(z, k, land), (50, 90, 99))
    for k in (3, 10, 30, 100):
        st['rms_about_plane_m']['%dm' % (k * cell)] = M.pct(M.rms(z, k, land), (50, 90, 99))
    st['band_rms_m'] = M.band_rms(z, cell)
    st['orientation'] = dict(zip(('strike_deg', 'coherence'), M.orientation(z, cell)))
    # hydrology
    hyd = M.hydrology(z, cell, outlet=water)
    nets = {}
    for a_km2 in (0.05, 0.5, 5.0):
        nets[a_km2] = M.network(hyd, z, cell, a_km2 * 1e6, water)
    st['drainage'] = {}
    for a_km2, net in nets.items():
        d = dict(net['stats'])
        counts, ratios = M.stream_counts(net, hyd)
        d['streams_by_order'], d['bifurcation_ratio'] = counts, ratios
        d['channel_spacing_transects_m'] = M.transect_spacing(net['channels'], water, cell)
        st['drainage']['A>=%gkm2' % a_km2] = d
    hand5 = nets[5.0]['hand']
    hand05 = nets[0.5]['hand']
    fl05 = nets[0.5]['flow_len']
    ok = land & np.isfinite(hand05) & np.isfinite(fl05) & ~nets[0.5]['channels']
    if ok.any():
        far = fl05 >= np.percentile(fl05[ok], 80)
        st['valley_depth_interfluve_hand_m'] = M.pct(hand05[ok & far], (25, 50, 75, 90, 99))
    # floodplain and buildable land (at 30 m)
    h30 = M.block(np.nan_to_num(hand5, nan=99.0), 3)
    main_len_km = float(nets[5.0]['channels'].sum() * cell / 1000)
    fp = (h30 < 3) & (s30 < 0.05) & ~w30
    st['floodplain'] = {'fraction_of_land': round(float(fp.sum() / max(1, (~w30).sum())), 4),
                        'area_km2': round(float(fp.sum() * (3 * cell) ** 2 / 1e6), 3),
                        'main_channel_km': round(main_len_km, 2),
                        'mean_width_m': round(float(fp.sum() * (3 * cell) ** 2 / max(1, main_len_km * 1000)), 1)}
    st['buildable'] = {}
    for name, lim in (('grade<=1/32', 1 / 32), ('grade<=1/16', 1 / 16)):
        b = (s30 <= lim) & (h30 >= 3) & ~w30
        lab, k = M.label(b, 4)
        sizes = np.bincount(lab.ravel())[1:] * (3 * cell) ** 2 / 1e6 if k else np.zeros(0)
        st['buildable'][name] = {'fraction_of_land': round(float(b.sum() / max(1, (~w30).sum())), 4),
                                 'largest_km2': round(float(sizes.max()) if sizes.size else 0, 3),
                                 'largest_fraction_of_land': round(float(sizes.max() * 1e6 / ((~w30).sum() * (3 * cell) ** 2)) if sizes.size else 0, 4),
                                 'components_ge_0.25km2': int((sizes >= 0.25).sum()), 'components_ge_1km2': int((sizes >= 1).sum())}
    # ridges
    tpi, rlist = M.ridges(z, cell, water)
    st['ridges'] = sorted(rlist, key=lambda r: -r['area_km2'])[:12]
    heads = nets[0.05]['channels'] & (M.box_mean(nets[0.05]['channels'].astype(np.float32), 1) * 9 <= 2.5)
    ridge_mask = tpi > 30
    if rlist:
        # flank hollows: heads of the A >= 0.01 km2 network on the ridge flanks (TPI > 10, slope > 15 %), per km of ridge
        net001 = M.network(hyd, z, cell, 0.01e6, water)
        chn = net001['channels']
        upc = np.zeros(z.shape, np.int32)
        src = np.flatnonzero(chn.ravel() & (hyd['down'] >= 0))
        dst = hyd['down'][src]
        okd = chn.ravel()[dst]
        np.add.at(upc.ravel(), dst[okd], 1)
        flank = (tpi > 10) & (s10 > 0.15)
        heads = chn & (upc == 0) & flank
        rl_km = sum(r['length_m'] for r in rlist) / 1000
        st['ridge_flank_hollows_per_km'] = round(float(heads.sum()) / max(1e-6, rl_km), 2)
    if st['orientation']['coherence'] >= 0.3 and len(rlist) >= 2:
        # spacing of parallel ridges: cross-strike positions of the centroids of ridges (>= 0.5 km2) within 20 deg of
        # the tile strike
        th = np.radians(st['orientation']['strike_deg'])
        par = [r for r in rlist if r['area_km2'] >= 0.5 and min(abs(r['strike_deg'] - st['orientation']['strike_deg']),
                                                                   180 - abs(r['strike_deg'] - st['orientation']['strike_deg'])) <= 20]
        pos = sorted(-r['cx_m'] * np.sin(th) + r['cy_m'] * np.cos(th) for r in par)
        st['ridge_spacing_centroids_m'] = [round(float(b - a)) for a, b in zip(pos, pos[1:])]
    else:
        st['ridge_spacing_centroids_m'] = None
    # hypsometry above water (coast)
    if water.any():
        wl = float(np.median(z[water]))
        bands = [0, 1, 3, 10, 20, 40, 80, 9999]
        h, _ = np.histogram(zl - wl, bins=bands)
        st['height_above_water'] = {'water_level_m': round(wl, 2),
                                    'bands': {'%g-%g' % (a, b): round(float(v) / zl.size, 4) for a, b, v in zip(bands[:-1], bands[1:], h)}}
    # land cover against landform
    if cover is not None:
        lf = np.full(s30.shape, 'steep>20%', dtype=object)
        lf[s30 < 0.20] = 'moderate8-20%'
        lf[s30 < 0.08] = 'gentle3-8%'
        lf[s30 < 0.03] = 'flat<3%'
        lf[(h30 < 3) & (s30 < 0.05)] = 'floodplain'
        lf[tpi[1::3, 1::3][:s30.shape[0], :s30.shape[1]] > 30] = 'ridge'
        lf[w30] = 'water'
        groups = np.vectorize(lambda c: NLCD_GROUP.get(int(c), 'other'))(cover[:s30.shape[0], :s30.shape[1]])
        tab = {}
        for f in ['floodplain', 'flat<3%', 'gentle3-8%', 'moderate8-20%', 'steep>20%', 'ridge']:
            sel = lf == f
            if sel.sum() == 0:
                continue
            g, c = np.unique(groups[sel], return_counts=True)
            tab[f] = {'share_of_land': round(float(sel.sum() / max(1, (~w30).sum())), 4),
                      'cover': {k: round(float(v) / sel.sum(), 3) for k, v in sorted(zip(g, c), key=lambda x: -x[1]) if v / sel.sum() >= 0.01}}
        st['cover_by_landform'] = tab
        g, c = np.unique(groups, return_counts=True)
        st['cover'] = {k: round(float(v) / groups.size, 4) for k, v in sorted(zip(g, c), key=lambda x: -x[1])}
    if fine:
        st['fine'] = {k: fine_window(v[0], v[1]) for k, v in fine.items()}
    st['seconds'] = round(time.time() - t0, 1)
    arrays = {'cell': np.float32(cell), 'z': z.astype(np.float32), 'slope': s10.astype(np.float32), 'hand5': hand5.astype(np.float32),
              'ch05': nets[0.5]['channels'], 'ch005': nets[0.05]['channels'], 'ch5': nets[5.0]['channels'],
              'acc': hyd['acc'].astype(np.float32), 'tpi': tpi.astype(np.float32), 'water': water,
              'build32': ((s30 <= 1 / 32) & (h30 >= 3) & ~w30), 'build16': ((s30 <= 1 / 16) & (h30 >= 3) & ~w30)}
    return st, arrays


def resample(z, cell_in, cell_out):
    """Bilinear resampling of a grid with cell_in onto cell_out (same origin, the covered extent kept)."""
    H, W = z.shape
    n = int((H * cell_in) // cell_out)
    t = (np.arange(n) + 0.5) * cell_out / cell_in - 0.5
    t = np.clip(t, 0, H - 1.000001)
    i0 = np.floor(t).astype(int)
    f = t - i0
    rows = z[i0] * (1 - f)[:, None] + z[np.minimum(i0 + 1, H - 1)] * f[:, None]
    return rows[:, i0] * (1 - f)[None, :] + rows[:, np.minimum(i0 + 1, W - 1)] * f[None, :]


def main():
    mode = sys.argv[1]
    if mode == 'real':
        raw, tid, out = sys.argv[2], sys.argv[3], sys.argv[4]
        d = os.path.join(raw, tid)
        z, _ = tiff.read(os.path.join(d, 'dem10.tif'))
        cov, _ = tiff.read(os.path.join(d, 'nlcd2021.tif'))
        cov = cov[:334, :334]
        water = np.repeat(np.repeat(cov == 11, 3, 0), 3, 1)[:z.shape[0], :z.shape[1]]
        f1, _ = tiff.read(os.path.join(d, 'lidar1.tif'))
        f2, _ = tiff.read(os.path.join(d, 'lidar2.tif'))
        fine = {'1km_at_1m': (f1.astype(np.float64), 1), '2km_at_2m': (f2.astype(np.float64), 2)}
        scale = float(sys.argv[5]) if len(sys.argv) > 5 else 1.0   # isotropic scale (AFL mountains: 0.55 x Pennsylvania)
        crop = int(sys.argv[6]) if len(sys.argv) > 6 else 0          # centre crop, cells (to match an AFL window)
        if crop:
            o = (z.shape[0] - crop) // 2
            z, water, cov = z[o:o + crop, o:o + crop], water[o:o + crop, o:o + crop], cov[o // 3:(o + crop) // 3, o // 3:(o + crop) // 3]
            tid = tid + '_c%d' % crop
        if scale != 1.0:
            # shrink the terrain isotropically, then resample it back onto the physical cell (bilinear), so every
            # window below is the same number of metres as on an AFL tile
            fine = {k: (resample(v[0] * scale, v[1] * scale, v[1]), v[1]) for k, v in fine.items()}
            zf = resample(z.astype(np.float64) * scale, 10 * scale, 10)
            m = zf.shape[0]
            water = resample(water.astype(np.float64), 10 * scale, 10)[:m, :m] > 0.5
            cov = None
            tid = tid + '_x%g' % scale
            z = zf
        st, arrays = analyze(tid, z.astype(np.float64), 10, water, cov, fine)
        st['scale'] = scale
        st['sources'] = {k: json.load(open(os.path.join(d, k + '.json'), encoding='utf-8')) for k in ('dem10', 'nlcd2021', 'lidar1', 'lidar2')}
        for v in st['sources'].values():
            v.pop('url', None)
        arrays['fine1'], arrays['fine2'] = f1.astype(np.float32), f2.astype(np.float32)
    else:
        # an AFL window exported by PlanV2Export: <id>.json with z (10 m + water), fine2 (2 m), fine1 (1 m)
        meta, out = sys.argv[2], sys.argv[3]
        d = os.path.dirname(meta)
        m = json.load(open(meta, encoding='utf-8'))
        tid = m['id']
        def grid(e):
            return np.fromfile(os.path.join(d, e['file']), dtype='<f4').reshape(e['n'], e['n']).astype(np.float64)
        z = grid(m['z'])
        water = np.fromfile(os.path.join(d, m['z']['water']), dtype=np.uint8).reshape(z.shape).astype(bool)
        f1, f2 = grid(m['fine1']), grid(m['fine2'])
        fine = {'1km_at_1m': (f1, 1), '2km_at_2m': (f2, 2)}
        st, arrays = analyze(tid, z, float(m['z']['cell']), water, None, fine)
        st['window'] = {k: m[k] for k in ('centre', 'detail_centre')}
        arrays['fine1'], arrays['fine2'] = f1.astype(np.float32), f2.astype(np.float32)
    os.makedirs(out, exist_ok=True)
    json.dump(st, open(os.path.join(out, tid + '.json'), 'w', encoding='utf-8'), indent=1, ensure_ascii=False)
    np.savez_compressed(os.path.join(out, tid + '.npz'), **arrays)
    print(tid, 'done in', st['seconds'], 's')


if __name__ == '__main__':
    main()
