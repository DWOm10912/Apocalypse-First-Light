# Blackridge .50 game version: which rig bone (tools/build-blackridge-50-v2-native.mjs) each low / high poly object
# belongs to. Shared by game_cull.py, game_uv.py, game_bake.py (one bake group per bone) and game_export.py.
# One atlas ('gun'): the pistol and its magazine.


def base_name(name):
    """Object name without the hi_ / lo_ prefix and Blender's .001 suffix."""
    b = name.split('_', 1)[1] if name[:3] in ('hi_', 'lo_') else name
    return b.split('.')[0]


BONES = {'br50_slide': 'slide', 'br50_rsight': 'slide', 'br50_rdot0': 'slide', 'br50_rdot1': 'slide',
         'br50_bolt': 'bolt', 'br50_safety_r': 'safety', 'br50_safety_l': 'safety', 'br50_hammer': 'hammer',
         'br50_trigger': 'trigger', 'br50_slidestop': 'slide_stop', 'br50_magrel': 'magazine_release',
         'br50_mag': 'magazine', 'br50_magbase': 'magazine', 'br50_follower': 'follower'}


def bone_of(name):
    return BONES.get(base_name(name), 'gun_body')


def atlas_of(name):
    return 'gun'


# parts that never move against each other: a face buried in another of them is never seen (game_cull.py)
CULL_SETS = ('gun_body', 'slide', 'magazine')
