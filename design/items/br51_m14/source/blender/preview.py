# BR-51 concept: quick Workbench check renders (orthographic), for modelling only. preview(path, view, center, width).
# view: 'right' (camera on +x), 'left', 'top', 'bottom', 'front' (muzzle), or (eye_dir Vector) for a perspective-free 3/4.
import bpy, math
from mathutils import Vector


def preview(path, view='right', center=(0, 560, -40), width=1200, res=(2000, 900), cavity=True):
    sc = bpy.context.scene
    cam = bpy.data.objects.get('BR51_prev')
    if not cam:
        cam = bpy.data.objects.new('BR51_prev', bpy.data.cameras.new('BR51_prev'))
        sc.collection.objects.link(cam)
    dirs = {'right': (1, 0, 0), 'left': (-1, 0, 0), 'top': (0, 0, 1), 'bottom': (0, 0, -1), 'front': (0, 1, 0), 'back': (0, -1, 0)}
    d = Vector(dirs[view] if isinstance(view, str) else view).normalized()
    c = Vector(center)
    cam.location = c + d * 3000
    up = Vector((0, 0, 1)) if abs(d.z) < 0.9 else Vector((-1, 0, 0))     # top / bottom: muzzle to the right
    cam.rotation_euler = _look(d, up)
    cam.data.type = 'ORTHO'
    cam.data.ortho_scale = width
    cam.data.clip_start = 10
    cam.data.clip_end = 8000
    sc.camera = cam
    old = sc.render.engine
    sc.render.engine = 'BLENDER_WORKBENCH'
    sh = sc.display.shading
    sh.light = 'STUDIO'
    sh.color_type = 'MATERIAL'
    sh.show_cavity = cavity
    sh.cavity_type = 'BOTH'
    sh.show_specular_highlight = True
    sc.render.resolution_x, sc.render.resolution_y = res
    sc.render.resolution_percentage = 100
    sc.render.film_transparent = False
    sc.render.filepath = path
    sc.render.image_settings.file_format = 'PNG'
    bpy.ops.render.render(write_still=True)
    sc.render.engine = old


def _look(d, up):
    # camera looks along -d (from c + d*k toward c); camera -Z = -d, camera Y = up projected
    z = d
    x = up.cross(z).normalized()
    y = z.cross(x)
    from mathutils import Matrix
    m = Matrix((x, y, z)).transposed()
    return m.to_euler()
