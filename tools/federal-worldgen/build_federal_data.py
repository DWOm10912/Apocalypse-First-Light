"""C2 federal experimental world data (2026-10-14, docs/worldgen/federal_six_state_c2.md).

Writes, for the "AFL Federation (experimental)" world preset only:
  * the 8 federal biomes and their vanilla tree-mix features (the mesh forest replaces them where it has stands),
  * density_function/federal/plan_*.json: copies of the r1 plan density functions with "profile": "federal" on every
    plan type (the federal plan) and the top slide raised from Y240-256 to Y296-312 (the range reaches about Y265),
  * noise_settings/federal_experimental.json: the r1 overworld settings (minecraft:overworld, which stays r1) on the
    federal density functions, with the federal surface-rule branch in front of the r1 branch,
  * world_preset/federal_experimental.json and the world-type list tag (minecraft:normal, replace false),
  * the federal biomes in #minecraft:is_overworld and in the mesh-forest biome modifier (merged, r1 entries kept),
  * biome / preset names (en_us, zh_cn: the biome section) and AFL temperature entries.
then checks the vegetation feature order over r1 + federal + the vanilla biomes still possible (a cycle crashes world
creation) and that only the alpine peaks can take snow.

  python tools/federal-worldgen/build_federal_data.py          write everything (idempotent)
  python tools/federal-worldgen/build_federal_data.py --check  only validate

Reuses tools/terrain-v2-research/build_ecology_data.py (r1) without changing it. Rerunning the r1 builder rewrites
is_overworld.json with the r1 biomes only: run this one after it.
"""
import copy
import importlib.util
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
spec = importlib.util.spec_from_file_location('r1eco', os.path.join(ROOT, 'tools', 'terrain-v2-research', 'build_ecology_data.py'))
r1 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(r1)

NS = r1.NS
DATA = r1.DATA
RES = r1.RES
T = lambda n: '%s:%s' % (NS, n)
C, SEQ, B, NOT, NOISE, STEEP, HIGH = r1.C, r1.SEQ, r1.B, r1.NOT, r1.NOISE, r1.STEEP, r1.HIGH
DRY, FLOOR, UNDER, GRASS = r1.DRY, r1.FLOOR, r1.UNDER, r1.GRASS
NEG = -1.7976931348623157e308
BANDS = {'type': 'minecraft:bandlands'}

# ---------------------------------------------------------------- trees (vanilla stand-ins; mesh stands where they exist)
TREES = {
    # spruce-fir stand-in (spruce, pine), birch on burns
    'eco_trees_montane': ('minecraft:spruce_checked', [(0.33, 'minecraft:pine_checked'), (0.1, 'minecraft:birch_checked')], [(10, 9), (11, 1)]),
    # northern mixed forest stand-in: spruce (hemlock / fir), oak (red oak / maple), birch (yellow / paper birch), pine
    'eco_trees_lakeshore': ('minecraft:spruce_checked', [(0.3, 'minecraft:oak_checked'), (0.25, 'minecraft:birch_checked'),
                                                         (0.15, 'minecraft:pine_checked')], [(8, 9), (9, 1)]),
    # a few stunted spruces at the lower edge of the meadows
    'eco_trees_alpine': ('minecraft:spruce_checked', [], [(0, 8), (1, 2)]),
    # tamarack / black spruce stand-in, scattered
    'eco_trees_fen': ('minecraft:spruce_checked', [(0.4, 'minecraft:birch_checked')], [(0, 7), (1, 3)]),
}

# id: temperature, downfall, trees, ground cover (vanilla placed features), creatures, extra
BIOMES = {
    'montane_conifer_forest': (0.4, 0.8, T('eco_trees_montane'),
                               ['minecraft:patch_large_fern', 'minecraft:patch_grass_taiga', 'minecraft:brown_mushroom_taiga',
                                'minecraft:red_mushroom_taiga', 'minecraft:patch_berry_common'], 'conifer', {}),
    'alpine_meadow': (0.45, 0.7, T('eco_trees_alpine'),
                      ['minecraft:flower_meadow', 'minecraft:patch_tall_grass_2', 'minecraft:patch_grass_plain'], 'alpine', {}),
    'alpine_peaks': (-0.3, 0.9, None, [], 'peak', {}),
    'lakeshore_mixed_forest': (0.55, 0.8, T('eco_trees_lakeshore'),
                               ['minecraft:flower_default', 'minecraft:patch_grass_forest', 'minecraft:patch_large_fern',
                                'minecraft:brown_mushroom_normal', 'minecraft:red_mushroom_normal', 'minecraft:patch_berry_common'], 'conifer', {}),
    'lake_fen': (0.55, 0.9, T('eco_trees_fen'),
                 ['minecraft:patch_tall_grass', 'minecraft:flower_swamp', 'minecraft:patch_grass_normal', 'minecraft:patch_waterlily',
                  'minecraft:patch_sugar_cane_swamp'], 'marsh', {}),
    'freshwater_lake': (0.55, 0.8, None, ['minecraft:patch_waterlily', 'minecraft:patch_sugar_cane', 'minecraft:seagrass_river'], 'lake', {}),
    'plateau_steppe': (0.95, 0.1, None, ['minecraft:patch_grass_savanna', 'minecraft:patch_tall_grass_2', 'minecraft:patch_dead_bush_2'], 'steppe', {}),
    'canyon_badlands': (1.2, 0.0, None, ['minecraft:patch_dead_bush_badlands'], 'canyon',
                        {'precipitation': False, 'grass_color': 9470285, 'foliage_color': 10387789}),
}
NAMES = {
    'montane_conifer_forest': ('Montane Conifer Forest', '山地针叶林'),
    'alpine_meadow': ('Alpine Meadow', '高山草甸'),
    'alpine_peaks': ('Alpine Peaks', '高山岩峰'),
    'lakeshore_mixed_forest': ('Lakeshore Mixed Forest', '湖岸针阔混交林'),
    'lake_fen': ('Lake Fen', '湖滨沼泽'),
    'freshwater_lake': ('Freshwater Lake', '淡水湖'),
    'plateau_steppe': ('Plateau Steppe', '高原干草原'),
    'canyon_badlands': ('Canyon Badlands', '峡谷劣地'),
}
PRESET_NAME = ('AFL Federation (Experimental)', 'AFL 联邦（实验）')
TEMPERATURE = {'montane_conifer_forest': (7, 6, 8), 'alpine_meadow': (3, 7, 6), 'alpine_peaks': (-3, 7, 2),
               'lakeshore_mixed_forest': (9, 6, 10), 'lake_fen': (9, 5, 10), 'freshwater_lake': (9, 5, 10),
               'plateau_steppe': (14, 10, 13), 'canyon_badlands': (17, 11, 15)}
SP = r1.SP
CREATURES = dict(r1.CREATURES)
CREATURES.update({
    'alpine': [SP('minecraft:sheep', 6, 2, 4), SP('minecraft:goat', 5, 1, 3), SP('minecraft:rabbit', 4, 2, 3)],
    'peak': [SP('minecraft:goat', 5, 1, 3)],
    'lake': [],
    'steppe': [SP('minecraft:rabbit', 6, 2, 3), SP('minecraft:horse', 4, 2, 6), SP('minecraft:cow', 4, 2, 4)],
    'canyon': [SP('minecraft:rabbit', 4, 2, 3)],
})
# ForestPlan stands; the alpine meadow and the fen joined 2026-10-20 (vanilla tree species retired: their vanilla stand-ins
# never grow while the mesh forest is on)
MESH_TREES = {'montane_conifer_forest': 'eco_trees_montane', 'lakeshore_mixed_forest': 'eco_trees_lakeshore',
              'alpine_meadow': 'eco_trees_alpine', 'lake_fen': 'eco_trees_fen'}


def tree_files():
    out = {}
    for tid, (default, feats, dist) in TREES.items():
        out['configured_feature/%s.json' % tid] = {'type': 'minecraft:random_selector', 'config': {
            'default': default, 'features': [{'chance': c, 'feature': f} for c, f in feats]}}
        out['placed_feature/%s.json' % tid] = {'feature': T(tid), 'placement': [
            {'type': 'minecraft:count', 'count': r1.count(dist)}, {'type': 'minecraft:in_square'},
            {'type': 'minecraft:surface_water_depth_filter', 'max_water_depth': 0},
            {'type': 'minecraft:heightmap', 'heightmap': 'OCEAN_FLOOR'}, {'type': 'minecraft:biome'}]}
    return out


_VEG = None


def order_vegetation(veg):
    """The r1 order (vanilla's, with the AFL tree mixes where vanilla places its trees) plus the federal tree mixes."""
    global _VEG
    if _VEG is None:
        g = r1.vanilla_global_order(9)
        i = g.index(r1.AFL_TREE_ANCHOR)
        _VEG = g[:i] + [T(t) for t in r1.TREES] + [T(t) for t in TREES] + g[i:]
    missing = [f for f in veg if f not in _VEG]
    if missing: raise SystemExit('unknown vegetation features %s' % missing)
    return sorted(veg, key=_VEG.index)


def build_biome(bid):
    temp, down, trees, cover, kind, extra = BIOMES[bid]
    base = r1.vanilla('biome', 'forest')
    feats = [list(step) for step in base['features']]
    feats[1] = ['minecraft:lake_lava_underground']
    feats[3] = []
    feats[9] = order_vegetation(['minecraft:glow_lichen'] + list(cover) + ([trees] if trees else []))
    marsh = kind == 'marsh'
    effects = {'fog_color': 12638463, 'sky_color': r1.sky_color(temp),
               'water_color': 4159204, 'water_fog_color': 329011, 'mood_sound': base['effects']['mood_sound']}
    if 'grass_color' in extra: effects['grass_color'] = extra['grass_color']
    if 'foliage_color' in extra: effects['foliage_color'] = extra['foliage_color']
    if kind in ('conifer', 'alpine'):
        effects['music'] = base['effects']['music']
    elif marsh:
        effects['music'] = dict(base['effects']['music'], sound='minecraft:music.overworld.swamp')
    spawners = {k: [] for k in base['spawners']}
    spawners['creature'] = CREATURES[kind]
    spawners['ambient'] = base['spawners']['ambient']
    spawners['monster'] = base['spawners']['monster']
    spawners['underground_water_creature'] = base['spawners']['underground_water_creature']
    if kind == 'lake':
        spawners['water_ambient'] = [SP('minecraft:salmon', 5, 1, 5)]
    return {'carvers': base['carvers'], 'downfall': down, 'effects': effects, 'features': feats,
            'has_precipitation': extra.get('precipitation', True), 'spawn_costs': {}, 'spawners': spawners, 'temperature': temp}


def check_order(biomes):
    lists_all = list(biomes.values()) + [r1.build_biome(b) for b in r1.BIOMES]
    fallout = os.path.join(DATA, NS, 'worldgen', 'biome', 'fallout_barrens.json')
    if os.path.exists(fallout): lists_all.append(json.load(open(fallout, encoding='utf-8')))
    for step in range(11):
        lists = [b['features'][step] if len(b['features']) > step else [] for b in lists_all]
        lists += [r1.vanilla('biome', v)['features'][step] for v in r1.VANILLA_POSSIBLE]
        r1.topo(lists)
    print('feature order: no cycle over %d federal + %d r1 AFL + %d vanilla biomes'
          % (len(biomes), len(lists_all) - len(biomes), len(r1.VANILLA_POSSIBLE)))


# ---------------------------------------------------------------- surface rules (federal biomes only)
def BIOME(*ids): return {'type': 'minecraft:biome', 'biome_is': [T(i) for i in ids]}


def surface_branch():
    """Federal biomes: floor and sub-floor blocks; the canyon (all depths above the preliminary surface) and the steep
    steppe ground in bands of terracotta, as in vanilla badlands; everything else falls through."""
    wet_floor = SEQ(                                                       # lake beds, stream beds, flooded shores
        C(BIOME('lake_fen'), B('minecraft:mud')),
        C(BIOME('canyon_badlands', 'plateau_steppe'), SEQ(C(NOISE('minecraft:gravel', 0.0), B('minecraft:gravel')), B('minecraft:red_sand'))),
        C(BIOME('alpine_peaks', 'alpine_meadow', 'montane_conifer_forest'),
          SEQ(C(NOISE('minecraft:gravel', -0.3), B('minecraft:gravel')), B('minecraft:stone'))),
        C(NOISE('minecraft:gravel', 0.15), B('minecraft:gravel')),
        C(NOISE('minecraft:surface', NEG, -0.25), B('minecraft:clay')),
        B('minecraft:sand'))
    dry_floor = SEQ(
        C(BIOME('alpine_peaks'), SEQ(C(STEEP, B('minecraft:stone')), C(NOISE('minecraft:surface', 0.25), B('minecraft:stone')),
                                     C(NOISE('minecraft:gravel', 0.45), B('minecraft:gravel')), B('minecraft:snow_block'))),
        C(BIOME('alpine_meadow'), SEQ(C(STEEP, B('minecraft:stone')), C(NOISE('minecraft:surface', 0.40), B('minecraft:stone')),
                                      C(NOISE('minecraft:gravel', 0.50), B('minecraft:gravel')))),
        C(BIOME('montane_conifer_forest'), SEQ(C(STEEP, B('minecraft:stone')), C(NOISE('minecraft:surface', 0.25), B('minecraft:coarse_dirt')),
                                               C(NOISE('minecraft:surface', -0.2), B('minecraft:podzol', snowy='false')))),
        C(BIOME('lakeshore_mixed_forest'), SEQ(C(NOISE('minecraft:surface', 0.40), B('minecraft:podzol', snowy='false')),
                                               C(NOISE('minecraft:surface', NEG, -0.45), B('minecraft:coarse_dirt')))),
        C(BIOME('freshwater_lake'), SEQ(C(NOISE('minecraft:gravel', 0.0), B('minecraft:gravel')), B('minecraft:sand'))),   # its dry rim: the beach
        C(BIOME('lake_fen'), C(NOISE('minecraft:surface_swamp', 0.0), B('minecraft:mud'))),
        C(BIOME('plateau_steppe'), SEQ(C(STEEP, BANDS), C(NOISE('minecraft:surface', 0.20), B('minecraft:coarse_dirt')),
                                       C(NOISE('minecraft:surface', NEG, -0.55), B('minecraft:red_sand')))),
        C(BIOME('canyon_badlands'), SEQ(C(NOISE('minecraft:surface', -0.1), B('minecraft:red_sand')), BANDS)),
        GRASS)
    sub_floor = SEQ(
        C(BIOME('alpine_peaks'), B('minecraft:stone')),
        C(BIOME('canyon_badlands'), BANDS),
        B('minecraft:dirt'))
    wet_sub = SEQ(C(BIOME('lake_fen'), B('minecraft:mud')), C(BIOME('canyon_badlands', 'plateau_steppe'), B('minecraft:red_sand')),
                  C(BIOME('alpine_peaks', 'alpine_meadow', 'montane_conifer_forest'), B('minecraft:gravel')), B('minecraft:sand'))
    return C({'type': 'minecraft:above_preliminary_surface'},
             C(BIOME(*BIOMES.keys()), SEQ(
                 C(FLOOR, SEQ(C(NOT(DRY), wet_floor), dry_floor)),
                 C(UNDER, C(DRY, sub_floor)),
                 C(UNDER, wet_sub),
                 C(BIOME('canyon_badlands'), BANDS),
                 C(BIOME('plateau_steppe'), C(STEEP, BANDS)))))


def is_federal_branch(rule):
    return 'canyon_badlands' in json.dumps(rule)


# ---------------------------------------------------------------- density functions and noise settings
PLAN_FILES = ['plan_caves', 'plan_height', 'plan_initial_density', 'plan_initial_land', 'plan_ocean_caves',
              'plan_sloped_cheese', 'plan_stability', 'plan_stable_floor']
PLAN_TYPES = {T('plan_height'), T('plan_stability'), T('plan_terrain')}
TOP_SLIDE = {240: 296, 256: 312}


def federalise(obj):
    """Deep copy: plan references to the federal copies, "profile": "federal" on plan types, the top slide raised."""
    if isinstance(obj, dict):
        out = {k: federalise(v) for k, v in obj.items()}
        if out.get('type') in PLAN_TYPES: out['profile'] = 'federal'
        if out.get('type') == 'minecraft:y_clamped_gradient' and out.get('from_y') == 240 and out.get('to_y') == 256:
            out['from_y'], out['to_y'] = TOP_SLIDE[240], TOP_SLIDE[256]
        return out
    if isinstance(obj, list): return [federalise(v) for v in obj]
    if isinstance(obj, str):
        for f in PLAN_FILES:
            if obj == T('terrain/' + f): return T('federal/' + f)
    return obj


def density_files():
    src = os.path.join(DATA, NS, 'worldgen', 'density_function', 'terrain')
    return {f: federalise(json.load(open(os.path.join(src, f + '.json'), encoding='utf-8'))) for f in PLAN_FILES}


def noise_settings():
    ns = federalise(json.load(open(os.path.join(DATA, 'minecraft', 'worldgen', 'noise_settings', 'overworld.json'), encoding='utf-8')))
    seq = ns['surface_rule']['sequence']
    seq[:] = [r for r in seq if not is_federal_branch(r)]
    seq.insert(1, surface_branch())
    return ns


PRESET = {'dimensions': {
    'minecraft:overworld': {'type': 'minecraft:overworld', 'generator': {
        'type': 'minecraft:noise', 'biome_source': {'type': 'minecraft:multi_noise', 'preset': 'minecraft:overworld'},
        'settings': T('federal_experimental')}},
    'minecraft:the_nether': {'type': 'minecraft:the_nether', 'generator': {
        'type': 'minecraft:noise', 'biome_source': {'type': 'minecraft:multi_noise', 'preset': 'minecraft:nether'},
        'settings': 'minecraft:nether'}},
    'minecraft:the_end': {'type': 'minecraft:the_end', 'generator': {
        'type': 'minecraft:noise', 'biome_source': {'type': 'minecraft:the_end'}, 'settings': 'minecraft:end'}}}}


def merge_values(path, values):
    obj = json.load(open(path, encoding='utf-8')) if os.path.exists(path) else {'replace': False, 'values': []}
    for v in values:
        if v not in obj['values']: obj['values'].append(v)
    return r1.write_json(path, obj)


# ---------------------------------------------------------------- main
def main():
    r1.ensure_vanilla()
    check_only = '--check' in sys.argv
    biomes = {bid: build_biome(bid) for bid in BIOMES}
    check_order(biomes)
    for bid, (temp, *_r) in BIOMES.items():
        worst = temp - (8 + 270 - 80) * 0.05 / 40           # the highest ground (about Y265) with temperature noise +8
        snowy = temp - (0 + 200 - 80) * 0.05 / 40 < 0.15    # at Y200, mean noise
        assert (bid == 'alpine_peaks') == (worst < 0.15), (bid, worst)
        print('  %-24s t %.2f  coldest %.3f  %s' % (bid, temp, worst, 'snow' if snowy else 'no snow'))
    dens = density_files()
    ns = noise_settings()
    if check_only:
        print('check only: nothing written')
        return
    n = 0
    w = r1.write_json
    for bid, b in biomes.items():
        n += w(os.path.join(DATA, NS, 'worldgen', 'biome', bid + '.json'), b)
    for rel, obj in tree_files().items():
        n += w(os.path.join(DATA, NS, 'worldgen', rel), obj)
    for f, obj in dens.items():
        n += w(os.path.join(DATA, NS, 'worldgen', 'density_function', 'federal', f + '.json'), obj)
    n += w(os.path.join(DATA, NS, 'worldgen', 'noise_settings', 'federal_experimental.json'), ns)
    n += w(os.path.join(DATA, NS, 'worldgen', 'world_preset', 'federal_experimental.json'), PRESET)
    n += merge_values(os.path.join(DATA, 'minecraft', 'tags', 'worldgen', 'world_preset', 'normal.json'), [T('federal_experimental')])
    n += merge_values(os.path.join(DATA, 'minecraft', 'tags', 'worldgen', 'biome', 'is_overworld.json'), [T(b) for b in BIOMES])
    mf = os.path.join(DATA, NS, 'forge', 'biome_modifier', 'mesh_forest.json')
    obj = json.load(open(mf, encoding='utf-8'))
    for bid, tid in MESH_TREES.items():
        if T(bid) not in obj['biomes']: obj['biomes'].append(T(bid))
        if T(tid) not in obj['remove']: obj['remove'].append(T(tid))
    n += w(mf, obj)
    for lang, k in (('en_us', 0), ('zh_cn', 1)):
        lines = ['  "biome.%s.%s": %s,' % (NS, bid, json.dumps(names[k], ensure_ascii=False)) for bid, names in NAMES.items()]
        lines.append('  "generator.%s.federal_experimental": %s,' % (NS, json.dumps(PRESET_NAME[k], ensure_ascii=False)))
        n += r1.insert_after(os.path.join(RES, 'assets', NS, 'lang', lang + '.json'), '"biome.%s.wet_prairie"' % NS, lines, comma_anchor=False)
    lines = ['    "%s:%s": { "mean": %d, "amp": %d, "water": %d }' % (NS, bid, m, a, wt) for bid, (m, a, wt) in TEMPERATURE.items()]
    n += r1.insert_after(os.path.join(DATA, NS, 'temperature', 'temperature_v1.json'), '"%s:wet_prairie"' % NS, lines, comma_anchor=True)
    print('files changed: %d' % n)


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    main()
