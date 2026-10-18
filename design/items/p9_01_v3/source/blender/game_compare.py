# Renders of the game low poly with the composed textures (TEX_PREFIX): vanilla emulation and PBR, same shots.
# SHOTS = [(name, eye_dir, dist, target)], OUTDIR, SAMPLES.
import bpy, os

exec(open(os.path.join(BASE, 'render.py')).read())
setup(key=3.5, rim=25.0, top=6.0, fill=0.8, world=0.03)
vis = {c.name: c.hide_render for c in bpy.data.collections}
for c in bpy.data.collections:
    c.hide_render = c.name not in ('P9_lo', 'P9_v3_studio')
for o in bpy.data.collections['P9_lo'].objects:
    o.hide_render = False   # game_bake leaves the low parts render-hidden
vs = sc.view_settings
for mode in globals().get('MODES', ('vanilla', 'pbr')):
    g = {'TEX_PREFIX': TEX_PREFIX, 'MODE': mode}
    exec(open(os.path.join(BASE, 'game_preview_mat.py')).read(), g)
    if mode == 'vanilla':
        vs.view_transform = 'Standard'
        vs.look = 'None'
        for o in LC.objects:
            if o.type == 'LIGHT':
                o.hide_render = True
    else:
        vs.view_transform = 'AgX'
        vs.look = 'AgX - Medium High Contrast'
        for o in LC.objects:
            if o.type == 'LIGHT':
                o.hide_render = False
    for name, d, dist, tgt in SHOTS:
        shot(os.path.join(OUTDIR, name + '_' + mode + '.png'), d, dist, 85, globals().get('RES', (1600, 900)), target=tgt,
             samples=SAMPLES if mode == 'pbr' else 16, card=0.3 if mode == 'pbr' else 0)
for c in bpy.data.collections:
    c.hide_render = vis[c.name]
vs.view_transform = 'AgX'
print('compare done')
