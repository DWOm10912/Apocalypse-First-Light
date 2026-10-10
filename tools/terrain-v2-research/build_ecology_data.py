"""Terrain V2 ecology V1 data (2026-10-10, docs/worldgen/terrain_v2_ecology_v1.md): writes the 11 AFL natural biomes,
their tree-density features, the AFL surface-rule branch, the is_overworld tag, biome names and temperature entries,
then checks the vegetation feature order the way vanilla's FeatureSorter will (a cycle crashes world creation).

  python build_ecology_data.py          write everything (idempotent)
  python build_ecology_data.py --check  only validate

Vanilla 1.20.1 worldgen data is read from build/vanilla_data (extracted from the ForgeGradle client-extra.jar).
Trees and plants are vanilla configured / placed features; only the tree mixes and counts are AFL's.
"""
import colorsys
import json
import os
import subprocess
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
RES = os.path.join(ROOT, 'src', 'main', 'resources')
DATA = os.path.join(RES, 'data')
VAN = os.path.join(ROOT, 'build', 'vanilla_data', 'data', 'minecraft', 'worldgen')
JAR = os.path.expanduser('~/.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client-extra.jar')
NS = 'apocalypse_firstlight'


def ensure_vanilla():
    if os.path.isdir(os.path.join(VAN, 'biome')): return
    os.makedirs(os.path.join(ROOT, 'build', 'vanilla_data'), exist_ok=True)
    subprocess.run(['unzip', '-o', '-q', JAR, 'data/minecraft/worldgen/biome/*', 'data/minecraft/worldgen/placed_feature/*',
                    'data/minecraft/worldgen/configured_feature/*'], cwd=os.path.join(ROOT, 'build', 'vanilla_data'), check=True)


def vanilla(kind, name):
    return json.load(open(os.path.join(VAN, kind, name + '.json'), encoding='utf-8'))


def sky_color(temp):
    # net.minecraft.data.worldgen.biome.OverworldBiomes.calculateSkyColor
    f = max(-1.0, min(1.0, temp / 3.0))
    r, g, b = colorsys.hsv_to_rgb((0.62222224 - f * 0.05) % 1.0, 0.5 + f * 0.1, 1.0)
    return (int(r * 255 + 0.5) << 16) | (int(g * 255 + 0.5) << 8) | int(b * 255 + 0.5)


# ---------------------------------------------------------------- tree mixes (vanilla trees, AFL counts)
def count(dist):
    return {'type': 'minecraft:weighted_list', 'distribution': [{'data': d, 'weight': w} for d, w in dist]}


TREES = {
    # id: (default placed feature, [(chance, placed feature)], count distribution)  -> about N trees per chunk
    'eco_trees_grassland': ('minecraft:oak_checked', [(0.4, 'minecraft:fancy_oak_checked')], [(0, 9), (1, 1)]),          # 0.1
    'eco_trees_woodland': ('minecraft:oak_checked', [(0.3, 'minecraft:fancy_oak_checked'), (0.2, 'minecraft:birch_checked')], [(8, 9), (9, 1)]),
    'eco_trees_mesophytic': ('minecraft:oak_checked', [(0.25, 'minecraft:fancy_oak_checked'), (0.2, 'minecraft:birch_checked'),
                                                        (0.1, 'minecraft:super_birch_bees_0002')], [(11, 9), (12, 1)]),
    'eco_trees_ridge': ('minecraft:oak_checked', [(0.1, 'minecraft:fancy_oak_checked'), (0.15, 'minecraft:spruce_checked')], [(3, 7), (4, 3)]),
    'eco_trees_hemlock': ('minecraft:spruce_checked', [(0.3, 'minecraft:pine_checked'), (0.15, 'minecraft:birch_checked')], [(9, 9), (10, 1)]),
    'eco_trees_riparian': ('minecraft:oak_checked', [(0.25, 'minecraft:fancy_oak_checked'), (0.25, 'minecraft:birch_checked')], [(5, 8), (6, 2)]),
    'eco_trees_coastal': ('minecraft:oak_checked', [(0.4, 'minecraft:pine_checked'), (0.2, 'minecraft:spruce_checked')], [(6, 9), (7, 1)]),
    'eco_trees_clearing': ('minecraft:pine_checked', [(0.3, 'minecraft:oak_checked')], [(0, 7), (1, 3)]),
}


def tree_files():
    out = {}
    for tid, (default, feats, dist) in TREES.items():
        out['configured_feature/%s.json' % tid] = {'type': 'minecraft:random_selector', 'config': {
            'default': default, 'features': [{'chance': c, 'feature': f} for c, f in feats]}}
        out['placed_feature/%s.json' % tid] = {'feature': '%s:%s' % (NS, tid), 'placement': [
            {'type': 'minecraft:count', 'count': count(dist)}, {'type': 'minecraft:in_square'},
            {'type': 'minecraft:surface_water_depth_filter', 'max_water_depth': 0},
            {'type': 'minecraft:heightmap', 'heightmap': 'OCEAN_FLOOR'}, {'type': 'minecraft:biome'}]}
    return out


def mean_trees(tid):
    if tid == 'minecraft:trees_swamp': return 2.1
    dist = TREES[tid.split(':')[1]][2]
    return sum(d * w for d, w in dist) / sum(w for _, w in dist)


# ---------------------------------------------------------------- biomes
T = lambda n: '%s:%s' % (NS, n)
BIOMES = {
    # id: temperature, downfall, trees feature (or None), ground cover (vanilla placed), creatures, kind
    'temperate_grassland': (0.8, 0.6, T('eco_trees_grassland'),
                            ['minecraft:patch_tall_grass_2', 'minecraft:flower_plains', 'minecraft:patch_grass_plain', 'minecraft:patch_sugar_cane'], 'open'),
    'oak_hickory_woodland': (0.7, 0.75, T('eco_trees_woodland'),
                             ['minecraft:forest_flowers', 'minecraft:flower_default', 'minecraft:patch_grass_forest',
                              'minecraft:brown_mushroom_normal', 'minecraft:red_mushroom_normal', 'minecraft:patch_sugar_cane'], 'forest'),
    'mixed_mesophytic_forest': (0.7, 0.8, T('eco_trees_mesophytic'),
                                ['minecraft:patch_large_fern', 'minecraft:flower_default', 'minecraft:patch_grass_forest',
                                 'minecraft:brown_mushroom_normal', 'minecraft:red_mushroom_normal'], 'forest'),
    'ridge_oak_forest': (0.65, 0.7, T('eco_trees_ridge'),
                         ['minecraft:flower_default', 'minecraft:patch_grass_forest', 'minecraft:patch_berry_common'], 'forest'),
    'hemlock_hollow': (0.6, 0.85, T('eco_trees_hemlock'),
                       ['minecraft:patch_large_fern', 'minecraft:patch_grass_taiga', 'minecraft:brown_mushroom_taiga',
                        'minecraft:red_mushroom_taiga', 'minecraft:patch_berry_common'], 'conifer'),
    'riparian_forest': (0.75, 0.85, T('eco_trees_riparian'),
                        ['minecraft:patch_tall_grass', 'minecraft:flower_default', 'minecraft:patch_grass_forest',
                         'minecraft:patch_waterlily', 'minecraft:brown_mushroom_normal', 'minecraft:red_mushroom_normal',
                         'minecraft:patch_sugar_cane'], 'forest'),
    'coastal_pine_oak_forest': (0.8, 0.7, T('eco_trees_coastal'),
                                ['minecraft:flower_default', 'minecraft:patch_grass_taiga_2', 'minecraft:patch_berry_common'], 'conifer'),
    'coastal_sandy_clearing': (0.8, 0.6, T('eco_trees_clearing'),
                               ['minecraft:patch_tall_grass_2', 'minecraft:flower_plains', 'minecraft:patch_grass_plain',
                                'minecraft:patch_berry_common'], 'open'),
    'coastal_swamp_forest': (0.8, 0.9, 'minecraft:trees_swamp',
                             ['minecraft:flower_swamp', 'minecraft:patch_grass_normal', 'minecraft:patch_waterlily',
                              'minecraft:brown_mushroom_swamp', 'minecraft:red_mushroom_swamp', 'minecraft:patch_sugar_cane_swamp',
                              'minecraft:seagrass_swamp'], 'swamp'),
    'tidal_marsh': (0.8, 0.9, None,
                    ['minecraft:patch_tall_grass', 'minecraft:patch_grass_plain', 'minecraft:patch_sugar_cane', 'minecraft:seagrass_swamp'], 'marsh'),
    'wet_prairie': (0.8, 0.85, None,
                    ['minecraft:patch_tall_grass', 'minecraft:flower_swamp', 'minecraft:patch_grass_plain', 'minecraft:patch_sugar_cane'], 'marsh'),
}
NAMES = {
    'temperate_grassland': ('Temperate Grassland', '温带草地'),
    'oak_hickory_woodland': ('Oak-Hickory Woodland', '栎-山核桃林地'),
    'mixed_mesophytic_forest': ('Mixed Mesophytic Forest', '坡地混合阔叶林'),
    'ridge_oak_forest': ('Ridge Oak Forest', '山脊栎林'),
    'hemlock_hollow': ('Hemlock Hollow', '谷地铁杉-白松林'),
    'riparian_forest': ('Riparian Forest', '河岸林'),
    'coastal_pine_oak_forest': ('Coastal Pine-Oak Forest', '海岸松栎林'),
    'coastal_sandy_clearing': ('Coastal Sandy Clearing', '海岸沙地疏林草地'),
    'coastal_swamp_forest': ('Coastal Swamp Forest', '海岸沼泽林'),
    'tidal_marsh': ('Tidal Marsh', '潮汐湿地'),
    'wet_prairie': ('Wet Prairie', '湿草甸'),
}
# AFL temperature system (data/apocalypse_firstlight/temperature/temperature_v1.json): plains-like temperate values
TEMPERATURE = {'temperate_grassland': (12, 6, 11), 'oak_hickory_woodland': (11, 5, 11), 'mixed_mesophytic_forest': (11, 5, 11),
               'ridge_oak_forest': (10, 6, 10), 'hemlock_hollow': (9, 5, 10), 'riparian_forest': (11, 5, 11),
               'coastal_pine_oak_forest': (12, 5, 11), 'coastal_sandy_clearing': (12, 6, 11), 'coastal_swamp_forest': (12, 4, 12),
               'tidal_marsh': (12, 4, 11), 'wet_prairie': (12, 5, 11)}

SP = lambda t, w, a, b: {'type': t, 'weight': w, 'minCount': a, 'maxCount': b}
CREATURES = {
    'open': [SP('minecraft:sheep', 12, 4, 4), SP('minecraft:pig', 10, 4, 4), SP('minecraft:chicken', 10, 4, 4),
             SP('minecraft:cow', 8, 4, 4), SP('minecraft:horse', 5, 2, 6), SP('minecraft:rabbit', 4, 2, 3)],
    'forest': [SP('minecraft:sheep', 12, 4, 4), SP('minecraft:pig', 10, 4, 4), SP('minecraft:chicken', 10, 4, 4),
               SP('minecraft:cow', 8, 4, 4), SP('minecraft:wolf', 5, 4, 4)],
    'conifer': [SP('minecraft:sheep', 12, 4, 4), SP('minecraft:pig', 10, 4, 4), SP('minecraft:chicken', 10, 4, 4),
                SP('minecraft:cow', 8, 4, 4), SP('minecraft:wolf', 8, 4, 4), SP('minecraft:rabbit', 4, 2, 3), SP('minecraft:fox', 8, 2, 4)],
    'swamp': [SP('minecraft:sheep', 12, 4, 4), SP('minecraft:pig', 10, 4, 4), SP('minecraft:chicken', 10, 4, 4),
              SP('minecraft:cow', 8, 4, 4), SP('minecraft:frog', 10, 2, 5)],
    'marsh': [SP('minecraft:chicken', 10, 4, 4), SP('minecraft:rabbit', 6, 2, 3), SP('minecraft:frog', 10, 2, 5)],
}


def build_biome(bid):
    temp, down, trees, cover, kind = BIOMES[bid]
    base = vanilla('biome', 'forest')
    feats = [list(step) for step in base['features']]
    feats[1] = ['minecraft:lake_lava_underground']            # no surface lava pools; underground ones are gated by the stable layer
    feats[3] = []                                              # no monster rooms (removed overworld-wide anyway)
    veg = ['minecraft:glow_lichen'] + list(cover) + ([trees] if trees else [])
    feats[9] = order_vegetation(veg)
    swamp = kind == 'swamp'
    effects = {'fog_color': 12638463, 'sky_color': sky_color(temp),
               'water_color': 6388580 if swamp else 4159204, 'water_fog_color': 2302743 if swamp else 329011,
               'mood_sound': base['effects']['mood_sound']}
    if swamp:
        effects['grass_color_modifier'] = 'swamp'
        effects['foliage_color'] = 6975545
    if kind in ('forest', 'conifer'):
        effects['music'] = base['effects']['music']
    elif kind in ('swamp', 'marsh'):
        effects['music'] = dict(base['effects']['music'], sound='minecraft:music.overworld.swamp')
    spawners = {k: [] for k in base['spawners']}
    spawners['creature'] = CREATURES[kind]
    spawners['ambient'] = base['spawners']['ambient']
    spawners['monster'] = base['spawners']['monster']        # WorldSpawnRules lets only zombies through (vanilla plains density)
    spawners['underground_water_creature'] = base['spawners']['underground_water_creature']
    return {'carvers': base['carvers'], 'downfall': down, 'effects': effects, 'features': feats,
            'has_precipitation': True, 'spawn_costs': {}, 'spawners': spawners, 'temperature': temp}


# ---------------------------------------------------------------- feature order (vanilla FeatureSorter semantics)
VANILLA_POSSIBLE = ['plains', 'ocean', 'deep_ocean', 'beach', 'lush_caves', 'dripstone_caves']
AFL_TREE_ANCHOR = 'minecraft:trees_plains'     # AFL tree mixes sit where vanilla places its tree features


def vanilla_global_order(step):
    """A topological order of every vanilla biome's features in a step (vanilla itself is consistent)."""
    lists = []
    for fn in sorted(os.listdir(os.path.join(VAN, 'biome'))):
        b = json.load(open(os.path.join(VAN, 'biome', fn), encoding='utf-8'))
        lists.append(b['features'][step] if len(b['features']) > step else [])
    return topo(lists)


def topo(lists):
    nodes, edges, indeg = [], {}, {}
    for l in lists:
        for f in l:
            if f not in edges: edges[f] = set(); indeg[f] = 0; nodes.append(f)
    for l in lists:
        for a, b in zip(l, l[1:]):
            if b not in edges[a]:
                edges[a].add(b); indeg[b] += 1
    out, ready = [], [n for n in nodes if indeg[n] == 0]
    while ready:
        n = ready.pop(0)
        out.append(n)
        for m in sorted(edges[n], key=nodes.index):
            indeg[m] -= 1
            if indeg[m] == 0: ready.append(m)
    if len(out) != len(nodes):
        raise SystemExit('feature order cycle among: %s' % [n for n in nodes if n not in out])
    return out


_VEG = None


def order_vegetation(veg):
    global _VEG
    if _VEG is None:
        g = vanilla_global_order(9)
        i = g.index(AFL_TREE_ANCHOR)
        _VEG = g[:i] + ['%s:%s' % (NS, t) for t in TREES] + g[i:]
    missing = [f for f in veg if f not in _VEG]
    if missing: raise SystemExit('unknown vegetation features %s' % missing)
    return sorted(veg, key=_VEG.index)


def check_order(biomes):
    """Every step over all possible biomes (AFL + the vanilla biomes still allowed) must sort without a cycle."""
    for step in range(11):
        lists = [b['features'][step] for b in biomes.values()]
        lists += [vanilla('biome', v)['features'][step] for v in VANILLA_POSSIBLE]
        topo(lists)
    print('feature order: no cycle over %d AFL + %d vanilla biomes' % (len(biomes), len(VANILLA_POSSIBLE)))


# ---------------------------------------------------------------- surface rules
def C(cond, then): return {'type': 'minecraft:condition', 'if_true': cond, 'then_run': then}
def SEQ(*rules): return {'type': 'minecraft:sequence', 'sequence': list(rules)}
def B(name, **props): return {'type': 'minecraft:block', 'result_state': dict({'Name': name}, **({'Properties': props} if props else {}))}
def BIOME(*ids): return {'type': 'minecraft:biome', 'biome_is': ['%s:%s' % (NS, i) for i in ids]}
def NOISE(name, lo, hi=1.7976931348623157e308): return {'type': 'minecraft:noise_threshold', 'noise': name, 'min_threshold': lo, 'max_threshold': hi}
def NOT(c): return {'type': 'minecraft:not', 'invert': c}
DRY = {'type': 'minecraft:water', 'offset': 0, 'surface_depth_multiplier': 0, 'add_stone_depth': False}
FLOOR = {'type': 'minecraft:stone_depth', 'offset': 0, 'add_surface_depth': False, 'secondary_depth_range': 0, 'surface_type': 'floor'}
UNDER = {'type': 'minecraft:stone_depth', 'offset': 0, 'add_surface_depth': True, 'secondary_depth_range': 0, 'surface_type': 'floor'}
STEEP = {'type': 'minecraft:steep'}
def HIGH(y): return {'type': 'minecraft:y_above', 'anchor': {'absolute': y}, 'surface_depth_multiplier': 0, 'add_stone_depth': False}
GRASS = B('minecraft:grass_block', snowy='false')


def surface_branch():
    """AFL natural biomes: floor and sub-floor blocks; everything deeper and every ceiling falls through to vanilla."""
    wet_floor = SEQ(                                                     # river beds, pool beds, flooded shores
        C(BIOME('tidal_marsh', 'coastal_swamp_forest', 'wet_prairie'), B('minecraft:mud')),
        C(NOISE('minecraft:gravel', 0.25), B('minecraft:gravel')),
        C(NOISE('minecraft:surface', -1.7976931348623157e308, -0.30), B('minecraft:clay')),
        B('minecraft:sand'))
    dry_floor = SEQ(
        C(BIOME('ridge_oak_forest'), SEQ(C(STEEP, B('minecraft:stone')), C(NOISE('minecraft:surface', 0.30), B('minecraft:coarse_dirt')),
                                         C(NOISE('minecraft:gravel', 0.45), B('minecraft:gravel')))),
        C(BIOME('mixed_mesophytic_forest'), SEQ(C(STEEP, B('minecraft:coarse_dirt')), C(NOISE('minecraft:surface', 0.40), B('minecraft:podzol', snowy='false')))),
        C(BIOME('hemlock_hollow'), SEQ(C(NOISE('minecraft:surface', 0.21), B('minecraft:coarse_dirt')),
                                       C(NOISE('minecraft:surface', -0.115), B('minecraft:podzol', snowy='false')))),
        C(BIOME('oak_hickory_woodland'), C(NOISE('minecraft:surface', 0.45), B('minecraft:coarse_dirt'))),
        # sand (2026-10-10 acceptance fix, measured with SandPatchSim on vanilla noise): broad sand only on the ground
        # just above the sea (dunes and sandy flats under Y65), medium patches up to Y67, above that only small spots
        # (two noises at once: the 16 m 'ice' octaves inside the 64 m 'surface' patches) ringed by coarse dirt, so sand
        # mixes into the grass instead of standing as bright blocks of 100 m deep inland.
        # inland: pine-oak about 3 % sand in spots of at most about 280 columns, clearings about 6 % plus a coarse-dirt ring
        C(BIOME('coastal_pine_oak_forest'), SEQ(
            C(NOT(HIGH(65)), C(NOISE('minecraft:surface', 0.25), B('minecraft:sand'))),
            C(NOISE('minecraft:surface', 0.10), SEQ(C(NOISE('minecraft:ice', 0.45), B('minecraft:sand')),
                                                    C(NOISE('minecraft:ice', 0.25), B('minecraft:coarse_dirt')))),
            C(NOISE('minecraft:surface', -1.7976931348623157e308, -0.45), B('minecraft:coarse_dirt')))),
        C(BIOME('coastal_sandy_clearing'), SEQ(
            C(NOT(HIGH(65)), C(NOISE('minecraft:surface', -0.20), B('minecraft:sand'))),
            C(NOT(HIGH(67)), C(NOISE('minecraft:surface', 0.0), C(NOISE('minecraft:ice', 0.15), B('minecraft:sand')))),
            C(NOISE('minecraft:surface', 0.0), SEQ(C(NOISE('minecraft:ice', 0.40), B('minecraft:sand')),
                                                   C(NOISE('minecraft:ice', 0.10), B('minecraft:coarse_dirt')))))),
        C(BIOME('coastal_swamp_forest'), C(NOISE('minecraft:surface_swamp', 0.20), B('minecraft:mud'))),
        C(BIOME('wet_prairie', 'tidal_marsh'), C(NOISE('minecraft:surface_swamp', 0.45), B('minecraft:mud'))),
        GRASS)
    sub_floor = SEQ(
        C(BIOME('coastal_sandy_clearing', 'coastal_pine_oak_forest'),
          C(NOT(HIGH(65)), C(NOISE('minecraft:surface', 0.25), B('minecraft:sand')))),   # dune sand runs a little deeper
        B('minecraft:dirt'))
    return C({'type': 'minecraft:above_preliminary_surface'},
             C(BIOME(*BIOMES.keys()), SEQ(
                 C(FLOOR, SEQ(C(NOT(DRY), wet_floor), dry_floor)),
                 C(UNDER, C(DRY, sub_floor)),
                 C(UNDER, SEQ(C(BIOME('tidal_marsh', 'coastal_swamp_forest', 'wet_prairie'), B('minecraft:mud')), B('minecraft:sand'))))))


def is_afl_branch(rule):
    return 'temperate_grassland' in json.dumps(rule)


# ---------------------------------------------------------------- write
def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    text = json.dumps(obj, indent=2, ensure_ascii=False) + '\n'
    old = open(path, encoding='utf-8').read() if os.path.exists(path) else None
    if old != text:
        open(path, 'w', encoding='utf-8', newline='\n').write(text)
        return 1
    return 0


def main():
    ensure_vanilla()
    check_only = '--check' in sys.argv
    biomes = {bid: build_biome(bid) for bid in BIOMES}
    check_order(biomes)
    for bid, (temp, *_rest) in BIOMES.items():
        # vanilla height-adjusted temperature: t - (noise * 8 + y - 80) * 0.05 / 40 above Y80; snow and ice under 0.15
        worst = temp - (8 + 320 - 80) * 0.05 / 40
        assert worst > 0.15, (bid, worst)
    print('snow: coldest possible (Y320, temperature noise +8) is %.3f > 0.15, so no snow or ice anywhere'
          % min(t - (8 + 320 - 80) * 0.05 / 40 for t, *_ in BIOMES.values()))
    for bid, (temp, down, trees, cover, kind) in BIOMES.items():
        print('  %-26s t %.2f d %.2f  trees/chunk %.2f  %s' % (bid, temp, down, mean_trees(trees) if trees else 0, kind))
    if check_only: return
    n = 0
    for bid, b in biomes.items():
        n += write_json(os.path.join(DATA, NS, 'worldgen', 'biome', bid + '.json'), b)
    for rel, obj in tree_files().items():
        n += write_json(os.path.join(DATA, NS, 'worldgen', rel), obj)
    n += write_json(os.path.join(DATA, 'minecraft', 'tags', 'worldgen', 'biome', 'is_overworld.json'),
                    {'replace': False, 'values': ['%s:%s' % (NS, b) for b in BIOMES]})
    # surface rule: insert / replace the AFL branch right after AFL's marine rule (index 0)
    p = os.path.join(DATA, 'minecraft', 'worldgen', 'noise_settings', 'overworld.json')
    ns = json.load(open(p, encoding='utf-8'))
    seq = ns['surface_rule']['sequence']
    seq[:] = [r for r in seq if not is_afl_branch(r)]
    seq.insert(1, surface_branch())
    n += write_json(p, ns)
    # names and AFL temperature entries: inserted as text after an anchor line (the files keep their own layout)
    for lang, k in (('en_us', 0), ('zh_cn', 1)):
        lines = ['  "biome.%s.%s": %s,' % (NS, bid, json.dumps(names[k], ensure_ascii=False)) for bid, names in NAMES.items()]
        n += insert_after(os.path.join(RES, 'assets', NS, 'lang', lang + '.json'), '"biome.%s.fallout_barrens"' % NS, lines, comma_anchor=False)
    lines = ['    "%s:%s": { "mean": %d, "amp": %d, "water": %d }' % (NS, bid, m, a, w) for bid, (m, a, w) in TEMPERATURE.items()]
    n += insert_after(os.path.join(DATA, NS, 'temperature', 'temperature_v1.json'), '"minecraft:deep_ocean"', lines, comma_anchor=True)
    print('files changed: %d' % n)


def insert_after(path, anchor, lines, comma_anchor):
    """Insert lines after the line holding anchor, once (skipped when the first line's key is already there).
    comma_anchor: the anchor line ends an object without a comma, so it gets one and the last new line gets none."""
    raw = open(path, 'rb').read().decode('utf-8')
    nl = '\r\n' if '\r\n' in raw else '\n'
    text = raw.replace('\r\n', '\n')

    if lines[0].strip().split('":')[0] in text: return 0
    rows = text.split('\n')
    i = next(k for k, r in enumerate(rows) if anchor in r)
    new = list(lines)
    if comma_anchor:
        rows[i] = rows[i].rstrip() + ','
        new = [l + ',' for l in new[:-1]] + [new[-1]]
    rows[i + 1:i + 1] = new
    open(path, 'wb').write(nl.join(rows).encode('utf-8'))
    return 1


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    main()
