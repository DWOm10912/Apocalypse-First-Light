# Quick Workbench previews of a collection (no materials): PREVIEW_COL, PREVIEW_SHOTS = [(file, dir, dist, target)], OUTDIR.
# SLIDE_BACK (mm, optional) moves the slide parts back like the locked-back animations, for checking the insides.
import bpy, mathutils, os

sc = bpy.context.scene
colname = PREVIEW_COL
cam = bpy.data.objects.get('tmp_cam') or bpy.data.objects.new('tmp_cam', bpy.data.cameras.new('tmp_cam'))
if cam.name not in sc.collection.objects:
    sc.collection.objects.link(cam)
cam.data.lens = 60
cam.data.clip_start = 1
cam.data.clip_end = 5000
sc.camera = cam
old = (sc.render.engine, sc.render.resolution_x, sc.render.resolution_y, sc.render.film_transparent)
sc.render.engine = 'BLENDER_WORKBENCH'
sc.render.resolution_x, sc.render.resolution_y = 1200, 800
sc.display.shading.light = 'STUDIO'
sc.display.shading.color_type = 'MATERIAL'
sc.display.shading.show_cavity = True
sc.display.shading.cavity_type = 'BOTH'
vis = {}
for c in bpy.data.collections:
    vis[c.name] = c.hide_render
    c.hide_render = c.name != colname
back = globals().get('SLIDE_BACK', 0.0)
moved = []
if back:
    for o in bpy.data.collections[colname].objects:
        if '_slide' in o.name:
            o.location.y -= back
            moved.append(o)
for name, d, dist, tgt in PREVIEW_SHOTS:
    tgt = mathutils.Vector(tgt)
    dv = mathutils.Vector(d).normalized()
    cam.location = tgt + dv * dist
    cam.rotation_euler = (tgt - cam.location).to_track_quat('-Z', 'Y').to_euler()
    sc.render.filepath = os.path.join(OUTDIR, name + '.png')
    bpy.ops.render.render(write_still=True)
for o in moved:
    o.location.y += back
for c in bpy.data.collections:
    c.hide_render = vis[c.name]
sc.render.engine, sc.render.resolution_x, sc.render.resolution_y, sc.render.film_transparent = old
print('previews', [s[0] for s in PREVIEW_SHOTS])
