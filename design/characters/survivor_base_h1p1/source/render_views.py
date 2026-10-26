"""Orthographic Workbench renders of the H1-P1 body (run in Blender after build_survivor_base_h1p1.py).

render(objects, views, out_dir, prefix, frame=None, size=900, focus=None, span=None): one PNG per view.
views: 'front' (from -Y), 'back', 'right' (from the body's right, -X), 'left', 'top', 'three' (front-right 3/4),
or (name, direction, up) tuples. focus / span: centre (m) and width (m) of the view; default the whole body.
"""
import math
import os

import bpy
from mathutils import Vector

VIEWS = {'front': ((0, -1, 0), (0, 0, 1)), 'back': ((0, 1, 0), (0, 0, 1)), 'right': ((-1, 0, 0), (0, 0, 1)),
         'left': ((1, 0, 0), (0, 0, 1)), 'top': ((0, 0, 1), (0, 1, 0)), 'three': ((-0.6, -1, 0.25), (0, 0, 1))}


def camera():
    cam = bpy.data.objects.get('h1p1_cam')
    if cam is None:
        cam = bpy.data.objects.new('h1p1_cam', bpy.data.cameras.new('h1p1_cam'))
        bpy.context.scene.collection.objects.link(cam)
    cam.data.type = 'ORTHO'
    return cam


def setup(size):
    sc = bpy.context.scene
    try:
        sc.render.engine = 'BLENDER_WORKBENCH'
    except TypeError:
        pass
    sh = sc.display.shading
    sh.light = 'STUDIO'
    sh.color_type = 'TEXTURE'
    sh.show_cavity = False
    sh.show_shadows = False
    sc.render.resolution_x = sc.render.resolution_y = size
    sc.render.film_transparent = False
    sc.world = sc.world or bpy.data.worlds.new('World')
    sc.display.shading.background_type = 'VIEWPORT'


def render(objects, views, out_dir, prefix, frame=None, size=900, focus=None, span=None):
    sc = bpy.context.scene
    setup(size)
    if frame is not None:
        sc.frame_set(frame)
    keep = set(objects)
    hidden = {}
    for ob in sc.objects:
        if ob.type in ('MESH', 'ARMATURE', 'EMPTY'):
            hidden[ob.name] = ob.hide_render
            ob.hide_render = ob.name not in keep
    cam = camera()
    sc.camera = cam
    focus = Vector(focus or (0, 0, 0.9))
    cam.data.ortho_scale = span or 2.0
    os.makedirs(out_dir, exist_ok=True)
    out = []
    for v in views:
        name, (d, up) = (v, VIEWS[v]) if isinstance(v, str) else (v[0], (v[1], v[2]))
        d = Vector(d).normalized()
        cam.location = focus + d * 6
        cam.rotation_mode = 'QUATERNION'
        cam.rotation_quaternion = (-d).to_track_quat('-Z', 'Y')
        # roll so that `up` is up in the image
        upv = Vector(up)
        q = (-d).to_track_quat('-Z', 'Y')
        cur = q @ Vector((0, 1, 0))
        want = (upv - upv.dot(d) * d).normalized()
        ang = cur.angle(want) if cur.length and want.length else 0.0
        if cur.cross(want).dot(-d) < 0:
            ang = -ang
        from mathutils import Quaternion
        cam.rotation_quaternion = Quaternion(-d, ang) @ q
        cam.data.clip_end = 20
        sc.render.filepath = os.path.join(out_dir, '%s_%s.png' % (prefix, name))
        bpy.ops.render.render(write_still=True)
        out.append(sc.render.filepath)
    for n, h in hidden.items():
        sc.objects[n].hide_render = h
    return out


def sheet(paths, out, cols=None, scale=1.0):
    """Paste equal-size PNGs into one grid image (row-major), optionally scaled down by an integer step."""
    import numpy as np
    ims = []
    for p in paths:
        img = bpy.data.images.load(p, check_existing=False)
        w, h = img.size
        a = np.array(img.pixels[:], np.float32).reshape(h, w, 4)
        bpy.data.images.remove(img)
        ims.append(a)
    cols = cols or len(ims)
    rows = (len(ims) + cols - 1) // cols
    h, w = ims[0].shape[:2]
    big = np.zeros((rows * h, cols * w, 4), np.float32)
    for i, a in enumerate(ims):
        r, c = i // cols, i % cols
        big[(rows - 1 - r) * h:(rows - r) * h, c * w:(c + 1) * w] = a     # Blender images are bottom-up
    step = max(1, int(round(1 / scale)))
    big = big[::step, ::step]
    img = bpy.data.images.new('sheet_tmp', big.shape[1], big.shape[0], alpha=True)
    img.pixels.foreach_set(big.ravel())
    img.filepath_raw = out
    img.file_format = 'PNG'
    img.save()
    bpy.data.images.remove(img)
    return out
