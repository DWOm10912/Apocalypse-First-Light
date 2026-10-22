# Field Survival Guide V1 concept renders (2026-10-21), run in a background Blender (the user's open Blender is left alone):
#   "C:/Program Files/Blender Foundation/Blender 5.2/blender.exe" -b --factory-startup -P fsg_render.py
# A 11.5 x 18 x 2 cm paperback with the out/ textures (fsg_pages.py): closed (front + spine, back), open on a table, and held
# open in first person (reading view B: the camera narrows while reading, the book lit by the world).
import bpy
import bmesh
import math
import os
from mathutils import Vector

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, 'out')
W, H, T = 0.115, 0.18, 0.02
SPINE_U0, SPINE_U1 = 1150 / 2500, 1350 / 2500


def clear():
    for o in list(bpy.data.objects):
        bpy.data.objects.remove(o, do_unlink=True)


def img(name):
    return bpy.data.images.load(os.path.join(OUT, name), check_existing=True)


def mat(name, image=None, color=(0.5, 0.5, 0.5), rough=0.7, spec=0.3):
    m = bpy.data.materials.get(name) or bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    b = next(n for n in nt.nodes if n.type == 'BSDF_PRINCIPLED')
    b.inputs['Roughness'].default_value = rough
    try:
        b.inputs['Specular IOR Level'].default_value = spec
    except KeyError:
        pass
    if image:
        t = nt.nodes.new('ShaderNodeTexImage')
        t.image = img(image)
        t.interpolation = 'Cubic'
        nt.links.new(t.outputs['Color'], b.inputs['Base Color'])
    else:
        b.inputs['Base Color'].default_value = (*color, 1)
    return m


def quad_mesh(name, quads, mats):
    """quads: (material index, [(xyz, uv) x 4]) wound counter-clockwise seen from outside."""
    me = bpy.data.meshes.new(name)
    bm = bmesh.new()
    uv = bm.loops.layers.uv.new()
    for mi, corners in quads:
        vs = [bm.verts.new(c[0]) for c in corners]
        f = bm.faces.new(vs)
        f.material_index = mi
        for loop, c in zip(f.loops, corners):
            loop[uv].uv = c[1]
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-6)
    bm.to_mesh(me)
    bm.free()
    for m in mats:
        me.materials.append(m)
    o = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(o)
    return o


def closed_book():
    cover = mat('fsg_cover', 'cover_wrap_en_us.png', rough=0.55, spec=0.35)
    edge = mat('fsg_edge', 'page_edges.png', rough=0.9, spec=0.1)
    z0, z1 = -T / 2, T / 2
    fu = lambda x: SPINE_U1 + (1 - SPINE_U1) * x / W
    bu = lambda x: SPINE_U0 * (1 - x / W)
    su = lambda z: SPINE_U0 + (SPINE_U1 - SPINE_U0) * (z - z0) / T
    q = [
        (0, [((0, 0, z1), (fu(0), 0)), ((W, 0, z1), (fu(W), 0)), ((W, H, z1), (fu(W), 1)), ((0, H, z1), (fu(0), 1))]),
        (0, [((W, 0, z0), (bu(W), 0)), ((0, 0, z0), (bu(0), 0)), ((0, H, z0), (bu(0), 1)), ((W, H, z0), (bu(W), 1))]),
        (0, [((0, 0, z0), (su(z0), 0)), ((0, 0, z1), (su(z1), 0)), ((0, H, z1), (su(z1), 1)), ((0, H, z0), (su(z0), 1))]),
        (1, [((W, 0, z0), (0, 0)), ((W, H, z0), (1, 0)), ((W, H, z1), (1, 1)), ((W, 0, z1), (0, 1))]),
        (1, [((0, H, z0), (0, 0)), ((0, H, z1), (0, 1)), ((W, H, z1), (1, 1)), ((W, H, z0), (1, 0))]),
        (1, [((0, 0, z0), (0, 0)), ((W, 0, z0), (1, 0)), ((W, 0, z1), (1, 1)), ((0, 0, z1), (0, 1))]),
    ]
    o = quad_mesh('book_closed', q, [cover, edge])
    bv = o.modifiers.new('bevel', 'BEVEL')
    bv.width, bv.segments, bv.limit_method = 0.0012, 3, 'ANGLE'
    for p in o.data.polygons:
        p.use_smooth = True
    return o


def open_book(lang='zh_cn'):
    """Two curved halves, spine at x = 0, lying on z = 0."""
    left = mat('fsg_p18_' + lang, f'page18_{lang}.png', rough=0.85, spec=0.15)
    right = mat('fsg_p19_' + lang, f'page19_{lang}.png', rough=0.85, spec=0.15)
    edge = mat('fsg_edge', 'page_edges.png', rough=0.9, spec=0.1)
    cov = mat('fsg_cover_in', color=(0.10, 0.17, 0.12), rough=0.6)
    t = 0.0085
    z = lambda d: 0.0012 + t * (1 - math.exp(-d / 0.007)) * (1 - 0.18 * (d / W) ** 2) + 0.0025 * math.sin(math.pi * min(d / W, 1))
    n = 48
    q = []
    for side, mi in ((-1, 0), (1, 1)):
        for i in range(n):
            d0, d1 = W * i / n, W * (i + 1) / n
            x0, x1 = side * d0, side * d1
            # page image: u 0 at the outer edge of the left page, 1 at its gutter; right page 0 at the gutter
            u0, u1 = (1 - d0 / W, 1 - d1 / W) if side < 0 else (d0 / W, d1 / W)
            top = [((x0, 0, z(d0)), (u0, 0)), ((x1, 0, z(d1)), (u1, 0)), ((x1, H, z(d1)), (u1, 1)), ((x0, H, z(d0)), (u0, 1))]
            if side < 0:
                top = [top[1], top[0], top[3], top[2]]
            q.append((mi, top))
            # the block's head and tail: the stack of sheets under the page
            for y, flip in ((0, False), (H, True)):
                f = [((x0, y, 0.0012), (d0 / W, 0)), ((x1, y, 0.0012), (d1 / W, 0)), ((x1, y, z(d1)), (d1 / W, 0.5)), ((x0, y, z(d0)), (d0 / W, 0.5))]
                if (side < 0) != flip:
                    f = f[::-1]
                q.append((2, f))
        xe = side * W
        f = [((xe, 0, 0.0012), (0, 0)), ((xe, H, 0.0012), (1, 0)), ((xe, H, z(W)), (1, 0.5)), ((xe, 0, z(W)), (0, 0.5))]
        q.append((2, f if side > 0 else f[::-1]))
    o = quad_mesh('book_open', q, [left, right, edge, cov])
    # the cover under the block
    bpy.ops.mesh.primitive_cube_add(size=1)
    c = bpy.context.active_object
    c.name = 'book_open_cover'
    c.scale = (2 * W + 0.004, H + 0.002, 0.0012)
    c.location = (0, H / 2, 0.0006)
    c.data.materials.append(cov)
    for p in o.data.polygons:
        p.use_smooth = True
    return o, c


def scene_setup(res, transparent=False):
    sc = bpy.context.scene
    sc.render.engine = 'CYCLES'
    sc.cycles.samples = 96
    sc.cycles.use_denoising = True
    sc.render.resolution_x, sc.render.resolution_y = res
    sc.render.film_transparent = transparent
    sc.view_settings.view_transform = 'Standard'
    sc.view_settings.look = 'None'
    w = bpy.data.worlds.get('fsg') or bpy.data.worlds.new('fsg')
    w.use_nodes = True
    bg = next(n for n in w.node_tree.nodes if n.type == 'BACKGROUND')
    bg.inputs['Color'].default_value = (0.42, 0.44, 0.46, 1)
    bg.inputs['Strength'].default_value = 0.25
    sc.world = w


def light(name, loc, power, size, rot_to=(0, 0, 0), color=(1, 0.96, 0.9)):
    l = bpy.data.lights.new(name, 'AREA')
    l.energy, l.size, l.color = power, size, color
    o = bpy.data.objects.new(name, l)
    bpy.context.scene.collection.objects.link(o)
    o.location = loc
    d = Vector(rot_to) - Vector(loc)
    o.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()
    return o


def camera(loc, look, lens=None, fov=None):
    c = bpy.data.cameras.new('cam')
    o = bpy.data.objects.new('cam', c)
    bpy.context.scene.collection.objects.link(o)
    o.location = loc
    o.rotation_euler = (Vector(look) - Vector(loc)).to_track_quat('-Z', 'Y').to_euler()
    if fov:
        c.sensor_fit = 'VERTICAL'
        c.angle_y = math.radians(fov)
    else:
        c.lens = lens
    c.clip_start = 0.01
    bpy.context.scene.camera = o
    return o


def table():
    m = mat('fsg_table', color=(0.16, 0.12, 0.09), rough=0.55)
    bpy.ops.mesh.primitive_plane_add(size=3, location=(0, 0, -0.0001))
    bpy.context.active_object.data.materials.append(m)


def render(name):
    bpy.context.scene.render.filepath = os.path.join(OUT, name)
    bpy.ops.render.render(write_still=True)
    print('rendered', name)


def centre(o):
    bpy.context.view_layer.update()
    pts = [o.matrix_world @ Vector(c) for c in o.bound_box]
    c = sum(pts, Vector()) / 8
    o.location.x -= c.x
    o.location.y -= c.y
    o.location.z -= min(p.z for p in pts)


def shot_closed():
    clear()
    scene_setup((1600, 1200))
    table()
    b = closed_book()
    b.rotation_euler = (0, 0, math.radians(-18))
    centre(b)
    light('key', (-0.35, -0.25, 0.55), 16, 0.5)
    light('rim', (0.4, 0.35, 0.25), 6, 0.3)
    camera((-0.05, -0.27, 0.25), (0.0, 0.0, 0.0), lens=58)
    render('r_closed.png')
    # the back and the spine: turned over, spine towards the camera
    b.location = (0, 0, 0)
    b.rotation_euler = (0, math.radians(180), math.radians(-62))
    centre(b)
    render('r_back.png')


def shot_open():
    clear()
    scene_setup((1600, 1100))
    table()
    o, c = open_book('zh_cn')
    for x in (o, c):
        x.location.y -= H / 2
    light('key', (-0.3, -0.3, 0.6), 22, 0.5)
    light('fill', (0.4, -0.1, 0.4), 6, 0.4)
    camera((0, -0.30, 0.36), (0, 0.0, 0.0), lens=50)
    render('r_open.png')


def box_between(name, a, b, wide, thick, m, up=Vector((0, 0, 1))):
    """A bevelled box from point a to point b (the AFL view model: one box per arm part)."""
    a, b = Vector(a), Vector(b)
    bpy.ops.mesh.primitive_cube_add(size=1)
    o = bpy.context.active_object
    o.name = name
    o.scale = (wide, (b - a).length, thick)
    o.location = (a + b) / 2
    o.rotation_euler = (b - a).to_track_quat('Y', 'Z').to_euler()
    o.data.materials.append(m)
    bv = o.modifiers.new('bv', 'BEVEL')
    bv.width, bv.segments = 0.004, 2
    return o


def shot_held(lang='zh_cn'):
    clear()
    scene_setup((1920, 1080), transparent=True)
    o, c = open_book(lang)
    root = bpy.data.objects.new('held', None)
    bpy.context.scene.collection.objects.link(root)
    for x in (o, c):
        x.location.y -= H / 2
        x.parent = root
    # held 40 cm out and 6 cm below the eye, the page turned up towards the face (72 deg from flat)
    root.location = (0.0, 0.40, -0.06)
    root.rotation_euler = (math.radians(72), 0, 0)
    bpy.context.view_layer.update()
    mw = root.matrix_world
    sleeve = mat('fsg_sleeve', color=(0.16, 0.18, 0.13), rough=0.85)
    skin = mat('fsg_skin', color=(0.55, 0.37, 0.27), rough=0.6)
    for side in (-1, 1):
        # the hand grips the lower outer corner: palm behind the cover, thumb on the page
        grip = mw @ Vector((side * (W - 0.006), -H / 2 + 0.03, 0.0))
        wrist = grip + Vector((side * 0.035, -0.06, -0.075))
        elbow = wrist + Vector((side * 0.10, -0.20, -0.16))
        box_between(f'hand{side}', wrist, grip + Vector((0, 0.004, -0.012)), 0.085, 0.035, skin)
        box_between(f'sleeve{side}', elbow, wrist, 0.095, 0.075, sleeve)
        thumb = grip + Vector((-side * 0.004, -0.012, 0.006))
        box_between(f'thumb{side}', thumb - Vector((side * -0.01, 0.02, 0.025)), thumb, 0.022, 0.018, skin)
    sun = bpy.data.lights.new('sun', 'SUN')
    sun.energy, sun.angle = 2.6, math.radians(3)
    so = bpy.data.objects.new('sun', sun)
    bpy.context.scene.collection.objects.link(so)
    so.rotation_euler = (math.radians(50), math.radians(-25), math.radians(20))
    light('sky', (0.4, -0.3, 0.6), 8, 1.0, rot_to=(0, 0.40, -0.06), color=(0.85, 0.92, 1.0))
    camera((0, 0, 0), (0, 0.40, -0.075), fov=36)
    render(f'r_held_{lang}.png')


import sys
if '--held' not in sys.argv:
    shot_closed()
    shot_open()
shot_held('zh_cn')
bpy.ops.wm.save_as_mainfile(filepath=os.path.join(HERE, 'fsg_concept.blend'))
