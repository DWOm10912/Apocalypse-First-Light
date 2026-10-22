# Folding knife V1 concept (2026-10-22): two versions, A traditional lockback, B modern liner lock.
# Run inside Blender (exec). Builds its own scene "FK_concept" (the user's other scenes are left alone), parts authored in
# cm under a root empty scaled 0.01, so lights and camera work in real metres. Renders go to OUT.
import bpy, bmesh, math

OUT = r'C:/Users/willi/AppData/Local/Temp/claude/D--Minecraft-Modding-Apocalypse-First-Light/9acf29e6-9726-4be6-9d1a-cac1531ae610/scratchpad/fk_renders/'
SC_NAME = 'FK_concept'


def scene():
    sc = bpy.data.scenes.get(SC_NAME) or bpy.data.scenes.new(SC_NAME)
    for o in list(sc.collection.all_objects):
        bpy.data.objects.remove(o, do_unlink=True)
    return sc


# ------------------------------------------------------------------ materials
def principled(m):
    return next(n for n in m.node_tree.nodes if n.type == 'BSDF_PRINCIPLED')


def mat(name, color, metal=0.0, rough=0.5):
    m = bpy.data.materials.get(name) or bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    for n in list(nt.nodes):
        if n.type not in ('BSDF_PRINCIPLED', 'OUTPUT_MATERIAL'):
            nt.nodes.remove(n)
    b = principled(m)
    b.inputs['Base Color'].default_value = (*color, 1)
    b.inputs['Metallic'].default_value = metal
    b.inputs['Roughness'].default_value = rough
    return m


def wood(name, dark, light, scale):
    m = mat(name, dark, 0, 0.42)
    nt, b = m.node_tree, principled(m)
    tc = nt.nodes.new('ShaderNodeTexCoord')
    mp = nt.nodes.new('ShaderNodeMapping')
    mp.inputs['Scale'].default_value = (0.08, 1.0, 1.0)   # stretch the rings along the length: straight grain
    wv = nt.nodes.new('ShaderNodeTexWave')
    wv.wave_type = 'RINGS'
    wv.inputs['Scale'].default_value = scale
    wv.inputs['Distortion'].default_value = 6
    wv.inputs['Detail'].default_value = 3
    cr = nt.nodes.new('ShaderNodeValToRGB')
    cr.color_ramp.elements[0].color = (*dark, 1)
    cr.color_ramp.elements[1].color = (*light, 1)
    nt.links.new(tc.outputs['Object'], mp.inputs['Vector'])
    nt.links.new(mp.outputs['Vector'], wv.inputs['Vector'])
    nt.links.new(wv.outputs['Fac'], cr.inputs['Fac'])
    nt.links.new(cr.outputs['Color'], b.inputs['Base Color'])
    return m


def speckled(name, color, metal, r0, r1, scale, bump):
    """Noise in roughness and a little bump: G10's woven texture, a stonewashed blade."""
    m = mat(name, color, metal, r0)
    nt, b = m.node_tree, principled(m)
    tc = nt.nodes.new('ShaderNodeTexCoord')
    nz = nt.nodes.new('ShaderNodeTexNoise')
    nz.inputs['Scale'].default_value = scale
    nz.inputs['Detail'].default_value = 8
    mr = nt.nodes.new('ShaderNodeMapRange')
    mr.inputs['To Min'].default_value = r0
    mr.inputs['To Max'].default_value = r1
    bp = nt.nodes.new('ShaderNodeBump')
    bp.inputs['Strength'].default_value = bump
    nt.links.new(tc.outputs['Object'], nz.inputs['Vector'])
    nt.links.new(nz.outputs['Fac'], mr.inputs['Value'])
    nt.links.new(mr.outputs['Result'], b.inputs['Roughness'])
    nt.links.new(nz.outputs['Fac'], bp.inputs['Height'])
    nt.links.new(bp.outputs['Normal'], b.inputs['Normal'])
    return m


# ------------------------------------------------------------------ geometry (cm, X along the knife, Z up, Y across)
def catmull(pts, closed, k=5):
    out, n = [], len(pts)
    rng = range(n) if closed else range(n - 1)
    for i in rng:
        p0 = pts[(i - 1) % n] if closed or i > 0 else pts[0]
        p1, p2 = pts[i], pts[(i + 1) % n]
        p3 = pts[(i + 2) % n] if closed or i + 2 < n else pts[-1]
        for s in range(k):
            t = s / k
            out.append(tuple(0.5 * (2 * p1[j] + (-p0[j] + p2[j]) * t + (2 * p0[j] - 5 * p1[j] + 4 * p2[j] - p3[j]) * t * t
                                    + (-p0[j] + 3 * p1[j] - 3 * p2[j] + p3[j]) * t ** 3) for j in range(2)))
    if not closed:
        out.append(pts[-1])
    return out


def clipx(pts, lo, hi):
    def clip(pts, keep, v):
        out = []
        for i in range(len(pts)):
            a, b = pts[i - 1], pts[i]
            ka, kb = keep(a), keep(b)
            cut = lambda: (v, a[1] + (b[1] - a[1]) * (v - a[0]) / (b[0] - a[0]))
            if kb:
                if not ka:
                    out.append(cut())
                out.append(b)
            elif ka:
                out.append(cut())
        return out
    return clip(clip(pts, lambda p: p[0] >= lo, lo), lambda p: p[0] <= hi, hi)


def interp(pl, x):
    if x <= pl[0][0]:
        return pl[0][1]
    for (x0, z0), (x1, z1) in zip(pl, pl[1:]):
        if x0 <= x <= x1:
            return z0 if x1 == x0 else z0 + (z1 - z0) * (x - x0) / (x1 - x0)
    return pl[-1][1]


def obj_from(sc, name, verts, faces, m, parent, bevel=0.05, seg=3, sharp=35):
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], faces)
    me.update()
    bm = bmesh.new()
    bm.from_mesh(me)
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-5)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(me)
    bm.free()
    o = bpy.data.objects.new(name, me)
    sc.collection.objects.link(o)
    o.data.materials.append(m)
    o.parent = parent
    if bevel:
        md = o.modifiers.new('bevel', 'BEVEL')
        md.width = bevel
        md.segments = seg
        md.limit_method = 'ANGLE'
        md.angle_limit = math.radians(sharp)
        md.harden_normals = True
    me.shade_smooth()
    try:
        me.set_sharp_from_angle(angle=math.radians(sharp))
    except Exception:
        pass
    return o


def prism(sc, name, pts, y0, y1, m, parent, **kw):
    n = len(pts)
    v = [(x, y0, z) for x, z in pts] + [(x, y1, z) for x, z in pts]
    f = [list(range(n))[::-1], list(range(n, 2 * n))] + [[i, (i + 1) % n, n + (i + 1) % n, n + i] for i in range(n)]
    return obj_from(sc, name, v, f, m, parent, **kw)


def cyl_y(sc, name, x, z, y0, y1, r, m, parent, seg=28, bevel=0.03):
    pts = [(x + r * math.cos(2 * math.pi * i / seg), z + r * math.sin(2 * math.pi * i / seg)) for i in range(seg)]
    return prism(sc, name, pts, y0, y1, m, parent, bevel=bevel, seg=2)


def box(sc, name, x0, x1, y0, y1, z0, z1, m, parent, bevel=0.02):
    return prism(sc, name, [(x0, z0), (x1, z0), (x1, z1), (x0, z1)], y0, y1, m, parent, bevel=bevel, seg=2)


def blade(sc, name, top, bot, half, edge, grind_start, grind_frac, m, parent):
    """Lofted blade: a six-point section (spine, grind line, edge, both faces) every few mm, the tip one point; distal
    taper to 55% of the spine at the tip; a flat ricasso before grind_start."""
    top, bot = catmull(top, False, 4), catmull(bot, False, 4)
    x0, tip = top[0][0], top[-1][0]
    xs = sorted(set([x0 + (tip - x0) * i / 70 for i in range(71)]))
    verts, rings = [], []
    for x in xs:
        t, b = interp(top, x), interp(bot, x)
        if x >= tip - 1e-6:
            rings.append([len(verts)])
            verts.append((tip, 0, t))
            continue
        h = half * (1 - 0.45 * max(0, (x - x0) / (tip - x0)))
        f = min(1, max(0, (x - grind_start) / 0.5))
        g = b + (t - b) * grind_frac * f
        e = h + (edge - h) * f
        ring = [(x, h, t), (x, h, g), (x, e, b), (x, -e, b), (x, -h, g), (x, -h, t)]
        rings.append(list(range(len(verts), len(verts) + 6)))
        verts += ring
    faces = [rings[0][::-1]]
    for a, c in zip(rings, rings[1:]):
        if len(c) == 1:
            faces += [[a[i], a[(i + 1) % 6], c[0]] for i in range(6)]
        else:
            faces += [[a[i], a[(i + 1) % 6], c[(i + 1) % 6], c[i]] for i in range(6)]
    return obj_from(sc, name, verts, faces, m, parent, bevel=0, sharp=6)


def empty(sc, name, loc, parent=None, scale=1.0):
    e = bpy.data.objects.new(name, None)
    sc.collection.objects.link(e)
    e.location = loc
    e.scale = (scale, scale, scale)
    e.parent = parent
    return e


def mirrored(sc, name, pts, y_in, y_out, m, parent, **kw):
    return [prism(sc, name + '_L', pts, -y_out, -y_in, m, parent, **kw), prism(sc, name + '_R', pts, y_in, y_out, m, parent, **kw)]


# ------------------------------------------------------------------ the two knives
PIVOT_Z = -0.15


def knife_a(sc, loc):
    """A: traditional lockback. Closed 12.4 cm, blade 9.5 cm clip point, walnut scales, brass bolsters, back lock."""
    steel = mat('fk_satin', (0.72, 0.73, 0.74), 1.0, 0.22)
    brass = mat('fk_brass', (0.80, 0.58, 0.28), 1.0, 0.28)
    walnut = wood('fk_walnut', (0.045, 0.022, 0.011), (0.17, 0.09, 0.045), 9)
    dark = mat('fk_dark', (0.02, 0.02, 0.02), 0.0, 0.6)
    root = empty(sc, 'A_root', loc, scale=0.01)
    outline = catmull([(0.9, -0.6), (0.95, 0.3), (0.6, 0.95), (-0.5, 1.2), (-3, 1.28), (-6, 1.22), (-9, 1.15), (-10.9, 1.05),
                       (-11.5, 0.6), (-11.6, -0.1), (-11.4, -0.8), (-10.8, -1.15), (-9, -1.22), (-6, -1.1), (-3.5, -1.2),
                       (-1.5, -1.32), (0.2, -1.15)], True)
    mirrored(sc, 'A_bolster_front', clipx(outline, -1.6, 2), 0.17, 0.8, brass, root, bevel=0.12)
    mirrored(sc, 'A_bolster_rear', clipx(outline, -12, -10.1), 0.17, 0.8, brass, root, bevel=0.12)
    mirrored(sc, 'A_scale', clipx(outline, -10.1, -1.6), 0.17, 0.74, walnut, root, bevel=0.14)
    top = [(0.6, 0.95), (-0.5, 1.2), (-3, 1.28), (-6, 1.22), (-9, 1.15), (-10.9, 1.05)]
    spring = [(x, interp(sorted(top), x) - 0.04) for x in [0.2 - 0.5 * i for i in range(23)]]
    spring += [(x, z - 0.45) for x, z in reversed(spring)]
    prism(sc, 'A_backspring', spring, -0.17, 0.17, steel, root, bevel=0.03)
    box(sc, 'A_lock_release', -6.9, -5.5, -0.17, 0.17, 0.9, interp(sorted(top), -6.2) + 0.02, steel, root)
    for nm, x, z, r in [('pivot', 0, PIVOT_Z, 0.32), ('rear_pin', -10.75, 0.0, 0.26), ('rivet1', -4.2, 0.15, 0.17), ('rivet2', -8.2, 0.1, 0.17)]:
        cyl_y(sc, 'A_' + nm, x, z, -0.82, 0.82, r, brass, root)
    pivot = empty(sc, 'A_pivot', (0, 0, PIVOT_Z), root)
    blade(sc, 'A_blade',
          [(-0.75, 0.7), (-0.2, 0.95), (5.4, 0.95), (6.4, 0.84), (7.4, 0.58), (8.5, 0.22), (9.5, -0.05)],
          [(-0.75, -0.9), (0.55, -0.92), (0.75, -1.33), (3.0, -1.35), (5.0, -1.27), (7.0, -1.0), (8.3, -0.6), (9.1, -0.26), (9.5, -0.05)],
          0.15, 0.015, 0.9, 0.72, steel, pivot)
    nick = cyl_y(sc, 'A_nail_nick', 0, 0, -0.16, -0.12, 1, dark, pivot, bevel=0)
    nick.location, nick.scale = (1.5, 0, 0.5), (0.55, 1, 0.1)
    return root, pivot


def knife_b(sc, loc):
    """B: modern liner lock. Closed 11.4 cm, blade 8.3 cm drop point, olive G10 over steel liners, thumb stud, pocket clip."""
    stone = speckled('fk_stonewash', (0.55, 0.56, 0.57), 1.0, 0.32, 0.62, 260, 0.04)
    g10 = speckled('fk_g10', (0.07, 0.085, 0.055), 0.0, 0.55, 0.85, 900, 0.25)
    liner = mat('fk_liner', (0.65, 0.66, 0.67), 1.0, 0.35)
    black = mat('fk_black', (0.025, 0.025, 0.027), 0.8, 0.42)
    screw = mat('fk_screw', (0.2, 0.2, 0.21), 1.0, 0.3)
    root = empty(sc, 'B_root', loc, scale=0.01)
    outline = catmull([(0.85, -0.7), (0.95, 0.2), (0.7, 0.95), (-0.3, 1.22), (-3, 1.25), (-7, 1.2), (-9.8, 1.12), (-10.5, 0.8),
                       (-10.65, 0.0), (-10.4, -0.85), (-9.6, -1.08), (-7, -1.0), (-4.6, -1.18), (-3.2, -1.0), (-2.2, -0.95),
                       (-1.3, -1.12), (-0.9, -1.42), (-0.3, -1.3), (0.3, -1.1)], True)
    mirrored(sc, 'B_scale', outline, 0.33, 0.6, g10, root, bevel=0.1)
    mirrored(sc, 'B_liner', outline, 0.24, 0.33, liner, root, bevel=0.02)
    top = [(0.7, 0.95), (-0.3, 1.22), (-3, 1.25), (-7, 1.2), (-9.8, 1.12)]
    spacer = [(x, interp(sorted(top), x) - 0.05) for x in [-4.0 - 0.3 * i for i in range(20)]]
    spacer += [(x, z - 0.5) for x, z in reversed(spacer)]
    prism(sc, 'B_spacer', spacer, -0.24, 0.24, black, root, bevel=0.03)
    for i in range(7):   # jimping on the handle spine, where the thumb rests
        x = -0.6 - 0.22 * i
        box(sc, 'B_jimp%d' % i, x - 0.05, x + 0.05, -0.5, 0.5, interp(sorted(top), x) - 0.03, interp(sorted(top), x) + 0.01, black, root, bevel=0)
    cyl_y(sc, 'B_pivot', 0, PIVOT_Z, -0.64, 0.64, 0.46, screw, root)
    for nm, x, z in [('screw1', -5.0, 0.85), ('screw2', -9.7, 0.85), ('screw3', -9.5, -0.6)]:
        cyl_y(sc, 'B_' + nm, x, z, -0.63, 0.63, 0.16, screw, root)
    clip = [(-10.3, 0.45), (-10.3, 1.0), (-5.0, 0.98), (-4.5, 0.86), (-4.35, 0.72), (-4.5, 0.58), (-5.0, 0.5)]
    prism(sc, 'B_clip', clip, 0.6, 0.68, black, root, bevel=0.025)
    pivot = empty(sc, 'B_pivot_e', (0, 0, PIVOT_Z), root)
    blade(sc, 'B_blade',
          [(-0.7, 0.75), (-0.2, 1.0), (5.0, 1.0), (6.2, 0.84), (7.2, 0.52), (8.0, 0.16), (8.5, -0.08)],
          [(-0.7, -0.85), (0.5, -0.87), (0.7, -1.25), (1.2, -1.33), (4.0, -1.33), (6.0, -1.15), (7.3, -0.76), (8.1, -0.36), (8.5, -0.08)],
          0.17, 0.015, 1.0, 0.82, stone, pivot)
    cyl_y(sc, 'B_thumb_stud', 1.75, 0.55, -0.45, 0.45, 0.2, black, pivot, bevel=0.06)
    return root, pivot


# ------------------------------------------------------------------ render
def setup(sc):
    sc.render.engine = 'CYCLES'
    sc.cycles.samples = 96
    sc.cycles.use_denoising = True
    sc.render.film_transparent = True
    sc.render.image_settings.file_format = 'PNG'
    sc.render.image_settings.color_mode = 'RGBA'
    if sc.world is None:
        sc.world = bpy.data.worlds.new('FK_world')
    try:
        sc.view_settings.look = 'AgX - Medium High Contrast'
    except TypeError:
        pass
    # Studio sky for the steel to reflect: dark floor, bright soft band overhead, two softbox stripes.
    sc.world.use_nodes = True
    nt = sc.world.node_tree
    for n in list(nt.nodes):
        if n.type not in ('BACKGROUND', 'OUTPUT_WORLD'):
            nt.nodes.remove(n)
    bg = next(n for n in nt.nodes if n.type == 'BACKGROUND')
    tc = nt.nodes.new('ShaderNodeTexCoord')
    sp = nt.nodes.new('ShaderNodeSeparateXYZ')
    cr = nt.nodes.new('ShaderNodeValToRGB')
    els = cr.color_ramp.elements
    els[0].position, els[0].color = 0.0, (0.015, 0.015, 0.017, 1)
    els[1].position, els[1].color = 0.95, (0.55, 0.56, 0.58, 1)
    for pos, c in [(0.22, 0.05), (0.30, 0.75), (0.38, 0.10), (0.50, 0.12), (0.62, 0.9), (0.66, 0.08), (0.80, 0.15), (0.84, 1.2), (0.88, 0.25)]:
        e = els.new(pos)
        e.color = (c, c, c * 1.03, 1)
    mr = nt.nodes.new('ShaderNodeMapRange')
    mr.inputs['From Min'].default_value, mr.inputs['From Max'].default_value = -1, 1
    nt.links.new(tc.outputs['Generated'], sp.inputs['Vector'])
    nt.links.new(sp.outputs['Z'], mr.inputs['Value'])
    nt.links.new(mr.outputs['Result'], cr.inputs['Fac'])
    nt.links.new(cr.outputs['Color'], bg.inputs['Color'])
    bg.inputs['Strength'].default_value = 0.6
    for nm, loc, rot, size, power in [('key', (0.25, -0.35, 0.45), (0.9, 0.2, 0.6), 0.35, 9),
                                      ('fill', (-0.4, -0.3, 0.1), (1.4, 0, -0.9), 0.5, 3),
                                      ('rim', (0.0, 0.45, 0.35), (-0.9, 0, 3.1), 0.25, 7),
                                      ('key2', (0.25, 0.35, 0.45), (-0.9, 0.2, 2.6), 0.35, 9)]:
        ld = bpy.data.lights.new('FK_' + nm, 'AREA')
        ld.size, ld.energy = size, power
        lo = bpy.data.objects.new('FK_' + nm, ld)
        lo.location, lo.rotation_euler = loc, rot
        sc.collection.objects.link(lo)


def shoot(sc, roots, show, name, cam_loc, target, res, lens=85, ortho=None):
    for r in roots:
        hide = r is not show
        for o in [r] + list(r.children_recursive):
            o.hide_render = hide
    for o in [o for o in sc.collection.objects if o.name.startswith('FK_cam') or o.name.startswith('FK_target')]:
        bpy.data.objects.remove(o, do_unlink=True)
    tgt = empty(sc, 'FK_target', target)
    cd = bpy.data.cameras.new('FK_cam')
    if ortho:
        cd.type, cd.ortho_scale = 'ORTHO', ortho
    else:
        cd.lens = lens
    cam = bpy.data.objects.new('FK_cam', cd)
    sc.collection.objects.link(cam)
    cam.location = cam_loc
    c = cam.constraints.new('TRACK_TO')
    c.target, c.track_axis, c.up_axis = tgt, 'TRACK_NEGATIVE_Z', 'UP_Y'
    sc.camera = cam
    sc.render.resolution_x, sc.render.resolution_y = res
    sc.render.filepath = OUT + name + '.png'
    bpy.ops.render.render(write_still=True, scene=sc.name)


def build_and_render(which=('A', 'B')):
    import os
    os.makedirs(OUT, exist_ok=True)
    sc = scene()
    setup(sc)
    ra, pa = knife_a(sc, (0, 0, 0))
    rb, pb = knife_b(sc, (0, 0, 0))
    roots = [ra, rb]
    prev = bpy.context.window.scene
    bpy.context.window.scene = sc
    try:
        for tag, root, piv, cx_open, cx_closed in [('A', ra, pa, -0.010, -0.055), ('B', rb, pb, -0.012, -0.050)]:
            if tag not in which:
                continue
            piv.rotation_euler = (0, 0, 0)
            shoot(sc, roots, root, tag + '_open', (cx_open + 0.10, -0.42, 0.16), (cx_open, 0, -0.002), (1800, 1000), 70)
            shoot(sc, roots, root, tag + '_side_open', (cx_open, -1, 0), (cx_open, 0, 0), (2000, 560), ortho=0.235)
            piv.rotation_euler = (0, math.pi, 0)
            shoot(sc, roots, root, tag + '_closed', (cx_closed - 0.06, 0.30, 0.12), (cx_closed, 0, -0.002), (1200, 900), 70)
            shoot(sc, roots, root, tag + '_side_closed', (cx_open, -1, 0), (cx_open, 0, 0), (2000, 560), ortho=0.235)
    finally:
        bpy.context.window.scene = prev
    return 'rendered ' + ','.join(which)
