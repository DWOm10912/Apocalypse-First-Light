"""Side-by-side panels, USGS reference vs AFL, at three scales, with one renderer and one colour rule (render.py):
  regional (6 km, 10 m cells; the Pennsylvania tiles shrunk 0.55 and resampled, as the AFL belt is built),
  local (about 2 km, 2 m lidar vs the AFL 2 m window), detail (500 m, 1 m lidar vs the AFL 1 m window).
Tint = height above each panel's own 1st percentile (relative relief), hillshade NW 45 deg, no exaggeration;
slope panels use the fixed classes 1/32, 1/16, 1/8, 1/4, 1/2.
  python -I compare.py STATS_DIR OUT_DIR PLAN_NAME"""
import json
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import render as R  # noqa: E402
from analyze import resample  # noqa: E402

PAIRS = [
    # (key, real stats id, real scale, AFL window, title)
    ('plain_oh', 'mw1_darby_plains_oh', 1.0, 'afl_plain', '冰碛平原：俄亥俄 Darby Plains ↔ AFL 内陆平原'),
    ('plain_il', 'mw3_bloomington_moraine_il', 1.0, 'afl_plain', '冰碛平原与终碛：伊利诺伊 Bloomington ↔ AFL 内陆平原'),
    ('plain_in', 'mw2_wabash_valley_in', 1.0, 'afl_plain', '河谷台地：印第安纳 Wabash ↔ AFL 内陆平原'),
    ('belt', 'ap1_susquehanna_gaps_pa', 0.55, 'afl_belt', '山脊谷地：宾州 Susquehanna（×0.55）↔ AFL 山带'),
    ('front', 'ap2_blue_mountain_front_pa', 0.55, 'afl_foothill', '山前过渡：宾州 Blue Mountain（×0.55）↔ AFL 山前'),
    ('coast', 'cp1_york_pamunkey_va', 1.0, 'afl_coastal', '海岸平原与河口：弗吉尼亚 York / Pamunkey ↔ AFL 海岸'),
]


def centre(a, n):
    o0, o1 = (a.shape[0] - n) // 2, (a.shape[1] - n) // 2
    return a[o0:o0 + n, o1:o1 + n]


def fit(img, size):
    """Nearest-neighbour resize of a square panel to size x size."""
    k = np.minimum((np.arange(size) * img.shape[0] / size).astype(int), img.shape[0] - 1)
    return img[k][:, k]


def save(img, path, up=1):
    R.png(R.upscale(img, up), path)


def main():
    st_dir, out, plan = sys.argv[1], sys.argv[2], sys.argv[3]
    os.makedirs(out, exist_ok=True)
    index = []
    for key, real, scale, win, title in PAIRS:
        rid = real + ('_x%g' % scale if scale != 1 else '')
        ra = np.load(os.path.join(st_dir, rid + '.npz'))
        aa = np.load(os.path.join(st_dir, plan + '_' + win + '.npz'))
        # regional
        rz, rw = ra['z'].astype(np.float64), ra['water'].astype(bool)
        az, aw = aa['z'].astype(np.float64), aa['water'].astype(bool)
        n = min(rz.shape[0], az.shape[0], 600)
        if key == 'coast':        # the real coast panel: 3 km around the tile's detail window (estuary edge)
            n = min(n, az.shape[0])
            t = json.load(open(os.path.join(st_dir, real + '.json'), encoding='utf-8'))['sources']['dem10']['tile']['detail']
            c0 = int(rz.shape[1] / 2 + t[0] / 10 - n / 2); r0 = int(rz.shape[0] / 2 - t[1] / 10 - n / 2)
            c0 = max(0, min(rz.shape[1] - n, c0)); r0 = max(0, min(rz.shape[0] - n, r0))
            rz, rw = rz[r0:r0 + n, c0:c0 + n], rw[r0:r0 + n, c0:c0 + n]
        else:
            rz, rw = centre(rz, n), centre(rw, n)
        az, aw = centre(az, n), centre(aw, n)
        up = 2 if n <= 320 else 1
        for side, z, w in (('real', rz, rw), ('afl', az, aw)):
            save(R.panel(z, 10, w, 'tint'), os.path.join(out, '%s_regional_%s.png' % (key, side)), up)
            save(R.panel(z, 10, w, 'slope'), os.path.join(out, '%s_regional_%s_slope.png' % (key, side)), up)
        # local (2 m) and detail (1 m); a shrunk real window is smaller than the AFL one: crop AFL to the same metres
        f2r, f1r = ra['fine2'].astype(np.float64), ra['fine1'].astype(np.float64)
        f2a, f1a = aa['fine2'].astype(np.float64), aa['fine1'].astype(np.float64)
        if scale != 1:
            f2r = resample(f2r * scale, 2 * scale, 2)
            f1r = resample(f1r * scale, 1 * scale, 1)
        m2 = min(f2r.shape[0], f2a.shape[0])
        f2r, f2a = centre(f2r, m2), centre(f2a, m2)
        f1r, f1a = centre(f1r, 500), centre(f1a, 500)
        for side, z2, z1 in (('real', f2r, f1r), ('afl', f2a, f1a)):
            save(R.panel(z2, 2, None, 'tint'), os.path.join(out, '%s_local_%s.png' % (key, side)))
            save(R.panel(z1, 1, None, 'tint'), os.path.join(out, '%s_detail_%s.png' % (key, side)))
            save(R.panel(z1, 1, None, 'slope'), os.path.join(out, '%s_detail_%s_slope.png' % (key, side)))
        # one contact row per pair for a quick look: real | AFL at each scale, 360 px tall
        tiles = [R.panel(rz, 10, rw, 'tint'), R.panel(az, 10, aw, 'tint'), R.panel(f2r, 2, None, 'tint'),
                 R.panel(f2a, 2, None, 'tint'), R.panel(f1r, 1, None, 'tint'), R.panel(f1a, 1, None, 'tint')]
        row = np.concatenate([np.pad(fit(t, 360), ((0, 0), (0, 8), (0, 0)), constant_values=255) for t in tiles], axis=1)
        R.png(row, os.path.join(out, '%s_contact.png' % key))
        index.append({'key': key, 'title': title, 'real': rid, 'afl': plan + '_' + win, 'regional_km': n * 10 / 1000,
                      'local_km': m2 * 2 / 1000, 'detail_m': 500, 'scale': scale})
    json.dump(index, open(os.path.join(out, 'index.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    print('panels written:', len(index))


if __name__ == '__main__':
    main()
