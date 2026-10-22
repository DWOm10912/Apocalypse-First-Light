# BR-51 game version: which rig bone (tools/build-br51-01-v3-native.mjs) and which texture atlas each low / high poly
# object belongs to. Shared by game_cull.py, game_uv.py (ATLAS), game_bake.py (one bake group per bone) and game_export.py.
#   atlas 'gun'  : the rifle and its own 20-round magazine (br51_01_v3_native)
#   atlas 'mags' : the 30-round magazine and the 50-round drum (br51_01_v3_mags, drawn by their own items)


def base_name(name):
    """Object name without the hi_ / lo_ prefix and Blender's .001 suffix."""
    b = name.split('_', 1)[1] if name[:3] in ('hi_', 'lo_') else name
    return b.split('.')[0]


def bone_of(name):
    b = base_name(name)
    if b.startswith('mag20'):
        return 'magazine'
    if b.startswith('mag30'):
        return 'mag30'
    if b.startswith('mag50'):
        return 'mag50'
    if b.startswith('bolt'):
        return 'bolt'
    if b.startswith('oprod'):
        return 'oprod'
    return {'rcv_boltlock': 'bolt_lock', 'rcv_selector': 'selector', 'fcg_trigger': 'trigger', 'fcg_safety': 'safety',
            'fcg_latch': 'mag_latch'}.get(b, 'gun_body')


def atlas_of(name):
    return 'mags' if bone_of(name) in ('mag30', 'mag50') else 'gun'


# parts that never move against each other: a face buried in another of them is never seen (game_cull.py)
CULL_SETS = ('gun_body', 'magazine', 'mag30', 'mag50')
