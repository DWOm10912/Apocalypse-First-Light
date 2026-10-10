"""Review sheets for Terrain V2 (HTML laid out here, screenshot to PNG with headless Chrome): national maps with
legends, the real-vs-AFL comparison at three scales, the USGS statistics table.
  python -I pages.py PLAN_NAME OUT_DIR [CHROME]
Inputs: build/terrain-v2-research/{png/<PLAN>, png/cmp_<PLAN>, stats, <PLAN>/plan.json, national_plan.json}."""
import html
import json
import os
import subprocess
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
B = os.path.join(ROOT, 'build', 'terrain-v2-research')
CSS = """
body{margin:0;background:#f3efe6;font-family:'Microsoft YaHei','PingFang SC',sans-serif;color:#24323d}
.page{padding:36px 44px}
h1{font-size:30px;font-weight:600;margin:0 0 4px}
.sub{color:#5d6b74;font-size:15px;margin-bottom:22px}
.row{display:flex;gap:18px;align-items:flex-start}
.card{background:#fbf9f4;border:1px solid #d9d2c3;border-radius:10px;padding:14px}
.cap{font-size:14px;color:#3d4b55;margin:6px 0 0}
.lg{display:flex;align-items:center;gap:8px;font-size:14px;margin:5px 0}
.sw{width:18px;height:14px;border-radius:3px;border:1px solid rgba(0,0,0,.15)}
table{border-collapse:collapse;font-size:13px}
td,th{border-bottom:1px solid #e1dacb;padding:4px 8px;text-align:right}
th{color:#5d6b74;font-weight:600}
td:first-child,th:first-child{text-align:left}
.tag{display:inline-block;font-size:12px;color:#fff;background:#4a6b7c;border-radius:4px;padding:1px 6px;margin-right:6px}
img{display:block;image-rendering:auto}
"""


def page(title, sub, body, w):
    return ('<!doctype html><html><head><meta charset="utf-8"><style>%s</style></head><body style="width:%dpx">'
            '<div class="page"><h1>%s</h1><div class="sub">%s</div>%s</div></body></html>') % (CSS, w, html.escape(title), sub, body)


def legend(items):
    return ''.join('<div class="lg"><span class="sw" style="background:rgb%s"></span>%s</div>' % (str(tuple(c)), html.escape(t)) for t, c in items)


def img(path, w):
    return '<img src="file:///%s" style="width:%dpx">' % (path.replace('\\', '/'), w)


def shoot(chrome, src, out, w, h):
    subprocess.run([chrome, '--headless=new', '--disable-gpu', '--hide-scrollbars', '--force-device-scale-factor=1',
                    '--allow-file-access-from-files', '--screenshot=' + out, '--window-size=%d,%d' % (w, h),
                    'file:///' + src.replace('\\', '/')], check=True, capture_output=True)


def main():
    plan, out = sys.argv[1], sys.argv[2]
    chrome = sys.argv[3] if len(sys.argv) > 3 else r'C:\Program Files\Google\Chrome\Application\chrome.exe'
    os.makedirs(out, exist_ok=True)
    P = os.path.join(B, 'png', plan)
    C = os.path.join(B, 'png', 'cmp_' + plan)
    meta = json.load(open(os.path.join(B, plan, 'plan.json'), encoding='utf-8'))
    nat = json.load(open(os.path.join(P, 'national_plan.json'), encoding='utf-8'))
    seed = meta['seed']
    common = '种子 %d · 规划 %s · 16 m 规划网格 · 北 = -Z（上），东 = +X（右）· 海平面 Y63 · 研究预览，未接入游戏生成' % (seed, meta['version'])
    sheets = []
    # ---- national maps
    import national_plan as NP
    maps = [
        ('national_landforms', '全国地貌分区', [('内陆冰碛平原', (186, 206, 132)), ('海岸平原', (232, 214, 160)), ('山前丘陵', (150, 170, 120)),
                                            ('褶皱山带', (146, 116, 104)), ('抗蚀岩山脊（越深越强）', (110, 70, 60)), ('河流（≥2 km²）', (52, 92, 160)),
                                            ('河口湾 / 海', (88, 128, 168)), ('潮汐湿地', (124, 160, 120))],
         '颜色按分区权重混合，过渡带宽 600–1500 m；山脊是褶皱抗蚀岩层的露头。'),
        ('national_elevation', '全国目标高程', [('海平面 Y63', (96, 132, 92)), ('Y68', (120, 152, 98)), ('Y78', (156, 176, 108)),
                                             ('Y98', (200, 196, 128)), ('Y133', (204, 172, 116)), ('Y193', (176, 138, 102)), ('Y263', (150, 124, 108))],
         '晕渲：西北光源 45°，不夸张高度；等高线每 20 格。规划高度是地表方块的 Y 参考值，未接入实际生成。'),
        ('national_rivers', '全国河流水系', [('1 级（≥0.25 km²）', (150, 180, 220)), ('2 级', (110, 150, 210)), ('3 级', (70, 115, 195)),
                                          ('4 级', (40, 85, 175)), ('5 级及以上', (20, 60, 150)), ('干流（≥8 km²）', (8, 40, 130)),
                                          ('河口湾 / 海', (176, 200, 222)), ('潮汐湿地', (186, 206, 180))],
         'Strahler 分级；河道、汇流和高度来自同一次计算；所有河道最终入海，无逆坡段（检查：A≥1 km² 河道逆坡 0 处）。'),
        ('national_suitability', '全国建设适宜性、城市候选与自然走廊', [('高（坡度 ≤1/32，高出河网 ≥3 m）', (122, 186, 150)), ('中（≤1/16）', (236, 214, 140)),
                                                                      ('低（≤1/8）', (222, 160, 128)), ('不适宜 / 河漫滩 / 湿地', (120, 112, 120)),
                                                                      ('城市候选的最大完整方块', (30, 40, 60)), ('自然走廊（最小代价路径）', (150, 40, 90)), ('港口候选', (200, 30, 30))],
         '32 m 网格；走廊代价 = 长度 ×（1 + 6 ×（坡度 / 1/16）²），坡度 >1/8 再 ×12，跨河 +300 m，湿地 +200 m，不过水面。'),
        ('national_ecology', '全国生态分区（潜在自然植被）', [(t, c) for _, t, c in NP.ECO] + [('河流（≥1 km²）', (70, 110, 170))],
         '按真实样方 NLCD 2021 与地貌的关系分区：平地以农田为主，坡地、冲沟、山脊以落叶阔叶林为主，河漫滩为河岸林，海岸 0–1 m 为湿地。'),
    ]
    for key, title, items, note in maps:
        body = '<div class="row"><div class="card">%s</div><div style="width:360px">%s<p class="cap">%s</p>%s</div></div>' % (
            img(os.path.join(P, key + '.png'), 1180), legend(items), html.escape(note), extra(key, nat))
        sheets.append((key, page(title, common, body, 1640), 1640, 1320))
    # ---- comparisons
    idx = json.load(open(os.path.join(C, 'index.json'), encoding='utf-8'))
    import summary as S
    for e in idx:
        k = e['key']
        rs = json.load(open(os.path.join(B, 'stats', e['real'] + '.json'), encoding='utf-8'))
        afl = json.load(open(os.path.join(B, 'stats', e['afl'] + '.json'), encoding='utf-8'))
        rows = ''.join('<tr><td>%s</td><td>%s</td><td>%s</td></tr>' % (html.escape(n), S.fmt(f(rs)), S.fmt(f(afl))) for n, f in S.ROWS[:24])
        def pair(scale, label, slope=False):
            cells = ''
            for side, name in (('real', '真实（USGS 3DEP）'), ('afl', 'AFL V2 规划')):
                im = img(os.path.join(C, '%s_%s_%s.png' % (k, scale, side)), 400)
                sm = img(os.path.join(C, '%s_%s_%s_slope.png' % (k, scale, side)), 200) if slope else ''
                cells += '<div class="card"><span class="tag">%s</span>%s<div class="row" style="margin-top:8px">%s</div></div>' % (name, html.escape(label), im + sm)
            return '<div class="row" style="margin-bottom:16px">%s</div>' % cells
        scale_note = '（真实山地按 0.55 同比例缩小后重采样到相同网格）' if e['scale'] != 1 else ''
        body = (pair('regional', '区域 %.1f km · 10 m' % e['regional_km'], True) + pair('local', '局部 %.1f km · 2 m' % e['local_km']) +
                pair('detail', '细节 500 m · 1 m', True) +
                '<div class="card"><table><tr><th>指标（同一统计代码）</th><th>真实</th><th>AFL</th></tr>%s</table></div>' % rows)
        sub = '颜色 = 本图自身第 1 百分位以上的相对高度（同一色阶），坡度图分级 1/32 · 1/16 · 1/8 · 1/4 · 1/2；%s %s' % (scale_note, common)
        sheets.append(('compare_' + k, page(e['title'], sub, body, 1360), 1360, 2350))
    # ---- real statistics table
    ids = ['mw1_darby_plains_oh', 'mw2_wabash_valley_in', 'mw3_bloomington_moraine_il', 'ap1_susquehanna_gaps_pa', 'ap2_blue_mountain_front_pa', 'cp1_york_pamunkey_va']
    T = [json.load(open(os.path.join(B, 'stats', i + '.json'), encoding='utf-8')) for i in ids]
    head = '<tr><th>指标</th>%s</tr>' % ''.join('<th>%s</th>' % html.escape(i.split('_', 1)[1]) for i in ids)
    rows = ''.join('<tr><td>%s</td>%s</tr>' % (html.escape(n), ''.join('<td>%s</td>' % S.fmt(f(t)) for t in T)) for n, f in S.ROWS)
    srcs = ''.join('<tr><td>%s</td><td>%.2f, %.2f</td><td>%s</td><td>%s</td><td>%s</td></tr>' % (
        html.escape(t['id']), t['sources']['dem10']['tile']['lat'], t['sources']['dem10']['tile']['lon'],
        ', '.join(str(v) for v in t['sources']['dem10']['bbox']), html.escape(t['sources']['dem10']['utc']),
        html.escape(', '.join(s['Name'] for s in t['sources']['lidar1']['sources_at_centre'] if s['LowPS'] < 5)[:60])) for t in T)
    body = ('<div class="card"><table>%s%s</table></div><div class="card" style="margin-top:16px"><table><tr><th>样方</th><th>中心（NAD83°）</th>'
            '<th>EPSG:5070 范围（m）</th><th>获取（UTC）</th><th>1 m 激光雷达源</th></tr>%s</table>'
            '<p class="cap">10 m：USGS 3DEP 1/3 弧秒无缝 DEM（镶嵌规则只取 LowPS 10–11 的源），双线性重采样到 EPSG:5070 的 10 m 网格，对齐 NLCD 30 m 网格；'
            '土地覆盖：NLCD 2021 Land Cover（MRLC WCS）；细节窗口：3DEP 激光雷达 DEM（LowPS<5）1 m / 2 m。原始数据只在 build/ 下，不进 Git。</p></div>') % (head, rows, srcs)
    sheets.append(('real_statistics', page('美国真实地貌统计（6 块 USGS 样方）', '同一套统计代码（tools/terrain-v2-research/metrics.py）；高程为 NAVD88 米，只比较相对起伏', body, 1700), 1700, 1500))
    # ---- overview, Codex Phase 1 against V2, other seeds
    body = '<div class="row"><div class="card">%s</div><div style="width:360px">%s<p class="cap">%s</p></div></div>' % (
        img(os.path.join(P, 'national_relief.png'), 1180),
        legend([('海平面 Y63', (96, 132, 92)), ('Y78', (156, 176, 108)), ('Y98', (200, 196, 128)), ('Y133', (204, 172, 116)), ('Y193', (176, 138, 102)),
                ('河流（≥0.5 km²）', (52, 92, 160)), ('河口湾 / 海', (88, 128, 168))]),
        html.escape('东南大片冰碛平原，西北侧一条褶皱山带（船形闭合、分叉，北端一处穿山缺口），山带与西北海岸之间是山前与海岸平原；河口是被淹没的河谷。'))
    sheets.insert(0, ('national_overview', page('全国地貌总览（晕渲 + 相对高度）', common, body, 1640), 1640, 1320))
    cx = os.path.join(B, '..', '..', 'build', 'terrain-v2-research', 'codex_phase1')
    cdx = os.path.join(os.environ.get('CODEX_PHASE1', ''), '')
    if os.path.isdir(os.environ.get('CODEX_PHASE1', '')):
        body = ('<div class="row"><div class="card"><span class="tag">Codex Phase 1</span>地貌分区%s</div><div class="card"><span class="tag">V2 r1</span>地貌分区%s</div></div>'
                '<div class="row" style="margin-top:16px"><div class="card"><span class="tag">Codex Phase 1</span>目标高程%s</div><div class="card"><span class="tag">V2 r1</span>目标高程%s</div></div>') % (
            img(os.path.join(cdx, 'landforms.png'), 780), img(os.path.join(P, 'national_landforms.png'), 700),
            img(os.path.join(cdx, 'target_elevation.png'), 780), img(os.path.join(P, 'national_elevation.png'), 700))
        sheets.append(('phase1_vs_v2', page('Codex Phase 1 与 V2 r1 对照（同一种子、同一海陆掩码）', '左：Codex 分支 codex/terrain-v2-phase1 的原图；右：本轮规划', body, 1580), 1580, 1700))
    cells = ''
    for sd in ('1', '20261010'):
        m = json.load(open(os.path.join(B, 'seed_' + sd, 'plan.json'), encoding='utf-8'))
        cells += '<div class="card"><span class="tag">种子 %s</span>规划 %.1f s · 逆坡 %d · 封闭洼地 %d%s</div>' % (
            sd, m['seconds_plan'], m['uphill_steps_A1km2'], m['closed_pits_16m'], img(os.path.join(B, 'png', 'seed_' + sd, 'national_relief.png'), 720))
    sheets.append(('other_seeds', page('同一套规则在其他种子上的结果', '国家地理性格固定，具体地图随种子变化：山带方位、长度、褶皱形态、河网与缺口都不同', '<div class="row">%s</div>' % cells, 1560), 1560, 900))
    for name, doc, w, h in sheets:
        src = os.path.join(out, name + '.html')
        open(src, 'w', encoding='utf-8').write(doc)
        shoot(chrome, src, os.path.join(out, name + '.png'), w, h)
        print('sheet', name)


def extra(key, nat):
    if key == 'national_suitability':
        sh = nat['suitability_share_of_land']
        rows = ''.join('<tr><td>%s</td><td>%.1f km²</td><td>%d m</td><td>Y%.0f</td></tr>' % (c['id'], c['area_km2'], c['square_m'], c['mean_y']) for c in nat['city_candidates'][:6])
        links = ''.join('<tr><td>%s–%s</td><td>%.1f km</td><td>%.1f%%</td><td>%s</td></tr>' % (l['from'], l['to'], l['length_m'] / 1000, 100 * (l['grade96_max'] or 0),
                                                                                       '穿山带' if l['crosses_belt'] else '') for l in nat['corridors'])
        head = '<p class="cap">陆地占比：高 %.0f%% · 中 %.0f%% · 低 %.0f%% · 不适宜 %.0f%%</p>' % tuple(100 * sh[k] for k in ('high', 'medium', 'low', 'unsuitable'))
        trunks = ''.join('<tr><td>%s</td><td>%.1f km</td><td>%.1f%%</td><td>%s</td></tr>' % (html.escape(t['name']), t['length_m'] / 1000, 100 * t['grade96_max'],
                                                                                  '穿山带' if t['crosses_belt'] else '') for t in nat.get('trunks', []))
        return (head + '<table><tr><th>城市候选</th><th>连片</th><th>最大方块</th><th>均高</th></tr>' + rows + '</table>'
                + '<table style="margin-top:10px"><tr><th>候选间走廊</th><th>长</th><th>最大坡度（96 m）</th><th></th></tr>' + links + '</table>'
                + '<table style="margin-top:10px"><tr><th>国家级主干走廊建议</th><th>长</th><th>最大坡度</th><th></th></tr>' + trunks + '</table>')
    if key == 'national_ecology':
        import national_plan as NP
        names = {k: t for k, t, _ in NP.ECO}
        return '<table>%s</table>' % ''.join('<tr><td>%s</td><td>%.1f%%</td></tr>' % (html.escape(names[k][:14]), 100 * v) for k, v in nat['ecology_share_of_land'].items())
    return ''


if __name__ == '__main__':
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    main()
