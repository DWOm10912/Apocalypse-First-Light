"""National maps of a Terrain V2 plan export (16 m rasters from PlanV2Export): shaded relief + relative tint, rivers,
provinces. Same renderer as the reference tiles (render.py), heights above sea level (Y63 = 0 m).
  python -I national.py PLAN_DIR OUT_DIR"""
import json
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import render as R  # noqa: E402


def load(d):
    m = json.load(open(os.path.join(d, 'plan.json'), encoding='utf-8'))
    n = m['grid']['n']
    a = {'meta': m, 'n': n, 'cell': m['grid']['cell']}
    for k, dt in (('h', '<f4'), ('h0', '<f4'), ('area', '<f4'), ('recv', '<i4')):
        a[k] = np.fromfile(os.path.join(d, k + ('.f32' if dt == '<f4' else '.i32')), dtype=dt).reshape(n, n)
    for k in ('water', 'strahler', 'resist', 'trunk'):
        a[k] = np.fromfile(os.path.join(d, k + '.u8'), dtype=np.uint8).reshape(n, n)
    a['prov'] = np.fromfile(os.path.join(d, 'provinces.u8x4'), dtype=np.uint8).reshape(n, n, 4) / 255.0
    return a


def crop_box(a, pad=40):
    land = a['water'] != 1
    rows = np.flatnonzero(land.any(1)); cols = np.flatnonzero(land.any(0))
    return max(0, rows[0] - pad), min(a['n'], rows[-1] + pad), max(0, cols[0] - pad), min(a['n'], cols[-1] + pad)


def relief_map(a, rivers=True):
    h = a['h'].astype(np.float64)
    sea = (a['water'] == 1) | (a['water'] == 2)
    rgb = R.panel(h, a['cell'], sea, 'tint', None, ref=63.0).astype(np.float64)
    deep = np.clip((63 - h) / 60, 0, 1)
    rgb[sea] = np.array(R.WATER) * (1 - 0.35 * deep[sea, None])
    marsh = a['water'] == 3
    rgb[marsh] = rgb[marsh] * 0.55 + np.array([120, 150, 110]) * 0.45
    if rivers:
        river_overlay(a, rgb)
    return np.clip(rgb, 0, 255).astype(np.uint8)


def river_overlay(a, rgb, min_area=0.5e6):
    A = a['area']
    land = a['water'] == 0
    ch = land & (A >= min_area)
    w = np.clip(np.log10(A / min_area + 1) / 2.2, 0, 1)
    col = np.array([52, 92, 160], dtype=np.float64)
    rgb[ch] = rgb[ch] * (1 - (0.55 + 0.45 * w[ch, None])) + col * (0.55 + 0.45 * w[ch, None])


def province_map(a):
    P = a['prov']
    cols = np.array([[186, 206, 132], [232, 214, 160], [150, 170, 120], [146, 116, 104]], dtype=np.float64)
    rgb = (P[..., :, None] * cols[None, None]).sum(2)
    hs, _ = R.shade(a['h'], a['cell'])
    rgb *= (0.55 + 0.45 * hs[..., None]) / (0.55 + 0.45 * 0.7071)
    rgb += (a['resist'][..., None] / 255.0) * (np.array([110, 70, 60]) - rgb) * 0.6
    sea = (a['water'] == 1) | (a['water'] == 2)
    rgb[sea] = R.WATER
    rgb[a['water'] == 3] = (124, 160, 120)
    river_overlay(a, rgb, 2e6)
    return np.clip(rgb, 0, 255).astype(np.uint8)


def main():
    d, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    a = load(d)
    r0, r1, c0, c1 = crop_box(a)
    for name, img in (('relief', relief_map(a)), ('provinces', province_map(a))):
        R.png(img[r0:r1, c0:c1], os.path.join(out, 'national_%s.png' % name))
    print('crop rows', r0, r1, 'cols', c0, c1)


if __name__ == '__main__':
    main()
