# Blackridge .50 game version, headless runner (no Blender window, so it never touches another session's Blender):
#   blender -b --factory-startup -P game_run.py -- <steps>          steps: build uv bake compose export save (any order kept)
# build  materials (game tuning, all black steel: user 2026-10-28 "就纯黑钢吧") + game_build.py + game_cull.py, saved to
#        br50_game.blend; later steps reopen that file. bake also saves the 4096 bakes (BAKE_DIR, outside the repo).
import sys, os, bpy

BASE = os.path.dirname(os.path.abspath(__file__)) + os.sep
ROOT = os.path.abspath(os.path.join(BASE, '..', '..', '..', '..', '..'))
BLEND = BASE + 'br50_game.blend'
steps = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
G = {'BASE': BASE, '__name__': '__main__'}
G.update(FINISH='black', GRAIN_K=0.18, ALB_K=2.0, STEEL_BASE=0.030)     # the P9 v3 game material values
BAKE_DIR = os.environ.get('BR50_BAKE_DIR', os.path.join(os.environ.get('TEMP', BASE), 'br50_bakes'))


def run(name, **kw):
    g = dict(G, **kw)
    exec(open(BASE + name).read(), g)


if 'build' in steps:
    for o in list(bpy.data.objects):
        bpy.data.objects.remove(o)
    bpy.context.scene.unit_settings.scale_length = 0.001
    run('materials.py')
    run('game_build.py', LO_Q=float(os.environ.get('BR50_LO_Q', 0.6)))
    run('game_cull.py')
else:
    bpy.ops.wm.open_mainfile(filepath=BLEND)
if 'uv' in steps:
    run('game_uv.py')
if 'bake' in steps:
    run('game_bake.py', ATLAS='gun', BAKE_RES=int(os.environ.get('BR50_BAKE_RES', 4096)))
    run('game_save_bakes.py', BAKE_DIR=BAKE_DIR, PRE='br50_gun_bake_')
if 'compose' in steps:
    if not bpy.data.images.get('br50_gun_bake_color'):
        for n in ('color', 'rough', 'mat', 'nfull', 'ngeo', 'ao'):
            for ext in ('.png', '.exr'):
                p = os.path.join(BAKE_DIR, 'br50_gun_bake_' + n + ext)
                if os.path.exists(p):
                    im = bpy.data.images.load(p)
                    im.name = 'br50_gun_bake_' + n
                    im.colorspace_settings.name = 'sRGB' if n == 'color' else 'Non-Color'
    run('game_compose.py', PRE='br50_gun_bake_', OUT_PREFIX=os.path.join(ROOT, 'src', 'main', 'blockbench', 'textures', 'blackridge_50_v2_native'))
if 'export' in steps:
    run('game_export.py', EXPORT_PATH=os.path.join(ROOT, 'design', 'items', 'blackridge_50', 'source', 'blackridge_50_v2_game_lowpoly.json'))
if 'build' in steps or 'uv' in steps or 'save' in steps:
    bpy.ops.wm.save_as_mainfile(filepath=BLEND)
    print('saved', BLEND)
