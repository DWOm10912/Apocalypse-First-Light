# Offline Workbench renders of the hand + P9 for review. Run inside Blender:
#   ns = {}; exec(open(path).read(), ns); ns['render'](frames, which, out_dir)
import bpy, math, os
from mathutils import Vector

VIEWS = {   # name: (camera direction from target, up)
    'right': ((1, -0.25, 0.15), (0, 0, 1)),
    'left': ((-1, -0.25, 0.15), (0, 0, 1)),
    'below': ((0.35, -0.3, -1), (0, 1, 0)),
    'rear': ((0.2, -1, 0.25), (0, 0, 1)),
}
TARGET, DIST, ORTHO = Vector((0.0, -1.5, 2.2)), 40.0, 13.0


def _camera():
    cam = bpy.data.objects.get('hand_p0_cam')
    if cam is None:
        cam = bpy.data.objects.new('hand_p0_cam', bpy.data.cameras.new('hand_p0_cam'))
        bpy.context.scene.collection.objects.link(cam)
    cam.data.type = 'ORTHO'
    cam.data.ortho_scale = ORTHO
    cam.data.clip_end = 200
    return cam


def _look(cam, d, up):
    d = Vector(d).normalized()
    cam.location = TARGET + d * DIST
    z = d
    x = Vector(up).cross(z).normalized()
    y = z.cross(x)
    from mathutils import Matrix
    m = Matrix((x, y, z)).transposed().to_4x4()
    m.translation = cam.location
    cam.matrix_world = m


def _show(which):
    for name in ('Hand_P0_A', 'Hand_P0_B'):
        c = bpy.data.collections.get(name)
        if c:
            on = name.endswith('_' + which)
            c.hide_viewport = not on
            c.hide_render = not on
    arm = bpy.data.collections.get('Hand_P0')
    if arm:
        arm.hide_render = True


def render(frames, which, out_dir, views=('right', 'left', 'below', 'rear'), size=720):
    sc = bpy.context.scene
    os.makedirs(out_dir, exist_ok=True)
    sc.render.engine = 'BLENDER_WORKBENCH'
    sh = sc.display.shading
    sh.light = 'STUDIO'
    sh.color_type = 'TEXTURE'
    sh.show_cavity = True
    sh.show_shadows = False
    sc.render.resolution_x = sc.render.resolution_y = size
    sc.render.film_transparent = False
    sc.render.image_settings.file_format = 'PNG'
    cam = _camera()
    sc.camera = cam
    _show(which)
    out = []
    for f in frames:
        sc.frame_set(f)
        for v in views:
            _look(cam, *VIEWS[v])
            path = os.path.join(out_dir, '%s_f%02d_%s.png' % (which, f, v))
            sc.render.filepath = path
            bpy.ops.render.render(write_still=True)
            out.append(path)
    return out
