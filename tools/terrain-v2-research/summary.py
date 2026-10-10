"""Print the key statistics of several measured tiles side by side (Markdown table, UTF-8).
Window-based entries are taken by position (base cell, 3 x cell; relief windows 3 / 10 / 30 / 100 / 300 cells; octave
bands from 2 cells up), so a tile measured at another scale (the 0.55 x Pennsylvania mountains) lines up with AFL.
  python -I tools/terrain-v2-research/summary.py STATS_DIR id1 id2 ... > table.md"""
import json
import os
import sys


def g(d, *path):
    for p in path:
        if d is None:
            return None
        if isinstance(p, int):
            if not isinstance(d, dict) or len(d) <= p:
                return None
            d = list(d.values())[p]
        else:
            d = d.get(p) if isinstance(d, dict) else None
    return d


def band(s, idx, key='band_rms_m'):
    if s is None or key not in s:
        return None
    v = list(s[key].values())
    if max(idx) >= len(v):
        return None
    return round(sum(v[i] ** 2 for i in idx) ** 0.5, 3)


def win(s, name, i, *rest):
    return g(s, name, i, *rest)


ROWS = [
    ('相对高差 P99（m）', lambda s: g(s, 'elevation_rel_m', 'p99')),
    ('坡度（1 格）P50 / P95', lambda s: (g(s, 'slope', 0, 'p50'), g(s, 'slope', 0, 'p95'))),
    ('坡度（3 格平均）P50 / P95', lambda s: (g(s, 'slope', 1, 'p50'), g(s, 'slope', 1, 'p95'))),
    ('坡度 ≤1/32 的比例（3 格）', lambda s: sum(v for k, v in (g(s, 'slope_classes', 1) or {}).items() if k in ('<1%', '1-3.1%')) or None),
    ('坡度 >1/8 的比例（3 格）', lambda s: sum(v for k, v in (g(s, 'slope_classes', 1) or {}).items() if k in ('12.5-25%', '25-50%', '>50%')) or None),
    ('起伏 3 格 P50 / P90（m）', lambda s: (win(s, 'relief_m', 0, 'p50'), win(s, 'relief_m', 0, 'p90'))),
    ('起伏 10 格 P50 / P90', lambda s: (win(s, 'relief_m', 1, 'p50'), win(s, 'relief_m', 1, 'p90'))),
    ('起伏 30 格 P50 / P90', lambda s: (win(s, 'relief_m', 2, 'p50'), win(s, 'relief_m', 2, 'p90'))),
    ('起伏 100 格 P50 / P90', lambda s: (win(s, 'relief_m', 3, 'p50'), win(s, 'relief_m', 3, 'p90'))),
    ('频带 RMS 2–8 格（m）', lambda s: band(s, (0, 1))),
    ('频带 RMS 8–32 格', lambda s: band(s, (2, 3))),
    ('频带 RMS 32–128 格', lambda s: band(s, (4, 5))),
    ('频带 RMS 128–512 格', lambda s: band(s, (6, 7))),
    ('走向一致性（0–1）', lambda s: g(s, 'orientation', 'coherence')),
    ('水系密度 A≥0.05 km²（km/km²）', lambda s: g(s, 'drainage', 0, 'drainage_density_km_per_km2')),
    ('水系密度 A≥0.5 km²', lambda s: g(s, 'drainage', 1, 'drainage_density_km_per_km2')),
    ('河道间距 A≥0.5 km² P50（m）', lambda s: g(s, 'drainage', 1, 'channel_spacing_transects_m', 'p50')),
    ('分岔比 1/2 · 2/3（A≥0.05）', lambda s: (g(s, 'drainage', 0, 'bifurcation_ratio', '1/2'), g(s, 'drainage', 0, 'bifurcation_ratio', '2/3'))),
    ('汇流点 /km²（A≥0.05）', lambda s: g(s, 'drainage', 0, 'confluences_per_km2')),
    ('分水岭离河高 HAND P50 / P90（m）', lambda s: (g(s, 'valley_depth_interfluve_hand_m', 'p50'), g(s, 'valley_depth_interfluve_hand_m', 'p90'))),
    ('河漫滩占陆地', lambda s: g(s, 'floodplain', 'fraction_of_land')),
    ('可建 ≤1/32 占陆地', lambda s: g(s, 'buildable', 'grade<=1/32', 'fraction_of_land')),
    ('可建 ≤1/32 最大连片占陆地', lambda s: g(s, 'buildable', 'grade<=1/32', 'largest_fraction_of_land')),
    ('可建 ≤1/16 最大连片占陆地', lambda s: g(s, 'buildable', 'grade<=1/16', 'largest_fraction_of_land')),
    ('山脊：条数 · 最长（m）· 宽（m）', lambda s: (len(s.get('ridges') or []) or None, max([r['length_m'] for r in s.get('ridges') or []], default=None),
                                         sorted([r['width_m'] for r in s.get('ridges') or []])[len(s.get('ridges') or []) // 2] if s.get('ridges') else None)),
    ('山脊间距（m）', lambda s: ', '.join(str(x) for x in s['ridge_spacing_centroids_m']) if s.get('ridge_spacing_centroids_m') else None),
    ('山脊侧沟 /km', lambda s: s.get('ridge_flank_hollows_per_km')),
    ('细节：坡度 P50 / P95', lambda s: (g(s, 'fine', 0, 'slope', 'p50'), g(s, 'fine', 0, 'slope', 'p95'))),
    ('细节：16 m 残差 RMS P50 / P90', lambda s: (g(s, 'fine', 0, 'rms_16m', 'p50'), g(s, 'fine', 0, 'rms_16m', 'p90'))),
    ('细节：64 m 起伏 P50 / P90', lambda s: (g(s, 'fine', 0, 'relief_64m', 'p50'), g(s, 'fine', 0, 'relief_64m', 'p90'))),
]


def fmt(v):
    if v is None:
        return '–'
    if isinstance(v, tuple):
        return ' / '.join(fmt(x) for x in v)
    if isinstance(v, float):
        return ('%.3f' % v).rstrip('0').rstrip('.') if abs(v) < 10 else '%.1f' % v
    return str(v)


def main():
    sys.stdout.reconfigure(encoding='utf-8')
    d, ids = sys.argv[1], sys.argv[2:]
    S = [json.load(open(os.path.join(d, i + '.json'), encoding='utf-8')) for i in ids]
    print('| 指标 | ' + ' | '.join(ids) + ' |')
    print('|' + '---|' * (len(ids) + 1))
    print('| 网格（m）· 范围（km） | ' + ' | '.join('%g · %g' % (s['cell_m'], s['size_m'][0] / 1000) for s in S) + ' |')
    for name, f in ROWS:
        print('| %s | %s |' % (name, ' | '.join(fmt(f(s)) for s in S)))


if __name__ == '__main__':
    main()
