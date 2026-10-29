# Blackridge .50 concept (copied from p9_01_v3): modelling helpers. Units: 1 BU = 1 mm. Axes: +y muzzle, +z up, +x = the gun's right side.
import bpy, bmesh, math
from mathutils import Vector, Matrix

COL = globals().get('COLNAME', 'BR50')
CUT = 'BR50_cutters'
# Game build flags (defaults reproduce the concept): Q scales the curve segment counts; GAME = low poly (no bevel modifier,
# no engraved text, no rifling or thread ridges, angle-limited smooth normals as Vextris rebuilds them); INTERIOR = the
# insides the animations show (slide channel, chamber, feed ramp, frame channel, recoil spring, magazine lips).
Q = globals().get('Q', 1.0)
GAME = globals().get('GAME', False)
INTERIOR = globals().get('INTERIOR', False)
CREASE = globals().get('CREASE', 35.0)


def col(name=COL):
    c = bpy.data.collections.get(name)
    if not c:
        c = bpy.data.collections.new(name)
        bpy.context.scene.collection.children.link(c)
    return c


def clear(prefix):
    c = bpy.data.collections.get(COL)
    for o in list(c.objects if c else []):
        if o.name.startswith(prefix):
            me = o.data if o.type == 'MESH' else None
            bpy.data.objects.remove(o)
            if me and me.users == 0:
                bpy.data.meshes.remove(me)


def fillet(pts, radii, seg=8):
    """Closed 2D polygon with a round of radius radii[i] at vertex i (0 = sharp)."""
    n = len(pts)
    if not isinstance(radii, (list, tuple)):
        radii = [radii] * n
    out = []
    for i in range(n):
        p0, p1, p2 = Vector(pts[i - 1]), Vector(pts[i]), Vector(pts[(i + 1) % n])
        r = radii[i]
        if r <= 0:
            out.append(p1)
            continue
        d1, d2 = (p0 - p1).normalized(), (p2 - p1).normalized()
        ang = math.acos(max(-1.0, min(1.0, d1.dot(d2))))
        if ang < 1e-3 or ang > math.pi - 1e-3:
            out.append(p1)
            continue
        t = min(r / math.tan(ang / 2), (p0 - p1).length * 0.5, (p2 - p1).length * 0.5)
        re = t * math.tan(ang / 2)
        a = p1 + d1 * t
        b = p1 + d2 * t
        c = p1 + (d1 + d2).normalized() * (re / math.sin(ang / 2))
        a0 = math.atan2((a - c).y, (a - c).x)
        a1 = math.atan2((b - c).y, (b - c).x)
        da = a1 - a0
        while da > math.pi:
            da -= 2 * math.pi
        while da < -math.pi:
            da += 2 * math.pi
        k_seg = max(2, int(seg * Q * abs(da) / (math.pi / 2) + 0.5))
        for k in range(k_seg + 1):
            ak = a0 + da * k / k_seg
            out.append(c + Vector((math.cos(ak), math.sin(ak))) * re)
    clean = []
    for p in out:
        if not clean or (p - clean[-1]).length > 1e-4:
            clean.append(p)
    if (clean[0] - clean[-1]).length < 1e-4:
        clean.pop()
    return [(p.x, p.y) for p in clean]


def circle(cx, cy, r, n=48):
    if Q < 1:
        n = max(8, min(int(n * Q + 0.5), int(6 + r * 4)))
    return [(cx + r * math.cos(2 * math.pi * k / n), cy + r * math.sin(2 * math.pi * k / n)) for k in range(n)]


def _P(plane, a, b, c):
    if plane == 'yz':
        return (c, a, b)
    if plane == 'xz':
        return (a, c, b)
    return (a, b, c)


def solid(name, prof, plane, lo, hi, mat=None, collection=None):
    """Extrude a closed 2D profile. plane 'yz': profile (y, z) along x; 'xz': (x, z) along y; 'xy': (x, y) along z."""
    bm = bmesh.new()
    v0 = [bm.verts.new(_P(plane, a, b, lo)) for a, b in prof]
    v1 = [bm.verts.new(_P(plane, a, b, hi)) for a, b in prof]
    n = len(prof)
    bm.faces.new(v0)
    bm.faces.new(v1)
    for i in range(n):
        j = (i + 1) % n
        bm.faces.new((v0[i], v0[j], v1[j], v1[i]))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    ob = bpy.data.objects.new(name, me)
    (collection or col()).objects.link(ob)
    if mat:
        me.materials.append(mat)
    return ob


def loft(name, sections, plane='xy', mat=None, collection=None):
    """Closed solid through equal-count profiles: sections = [(profile, h), ...], h along the plane normal."""
    bm = bmesh.new()
    rings = [[bm.verts.new(_P(plane, a, b, h)) for a, b in prof] for prof, h in sections]
    n = len(rings[0])
    bm.faces.new(rings[0])
    bm.faces.new(rings[-1])
    for r0, r1 in zip(rings, rings[1:]):
        for i in range(n):
            j = (i + 1) % n
            bm.faces.new((r0[i], r0[j], r1[j], r1[i]))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    ob = bpy.data.objects.new(name, me)
    (collection or col()).objects.link(ob)
    if mat:
        me.materials.append(mat)
    return ob


def rrect(x0, x1, y0, y1, r, seg=8):
    """Rounded rectangle; r = one radius or four (x0y0, x1y0, x1y1, x0y1)."""
    return fillet([(x0, y0), (x1, y0), (x1, y1), (x0, y1)], r, seg)


def lathe(name, prof, axis='y', seg=64, mat=None, collection=None, center=(0, 0), caps=True):
    """Revolve an open (r, t) profile around an axis through center. axis 'y': t along y, center = (x, z)."""
    if Q < 1:
        seg = max(12, min(int(seg * Q + 0.5), int(8 + max(r for r, t in prof) * 3)))
    bm = bmesh.new()
    rings = []
    for k in range(seg):
        a = 2 * math.pi * k / seg
        ring = []
        for r, t in prof:
            if axis == 'y':
                co = (center[0] + r * math.cos(a), t, center[1] + r * math.sin(a))
            elif axis == 'x':
                co = (t, center[0] + r * math.cos(a), center[1] + r * math.sin(a))
            else:
                co = (center[0] + r * math.cos(a), center[1] + r * math.sin(a), t)
            ring.append(bm.verts.new(co))
        rings.append(ring)
    m = len(prof)
    for k in range(seg):
        a, b = rings[k], rings[(k + 1) % seg]
        for i in range(m - 1):
            bm.faces.new((a[i], b[i], b[i + 1], a[i + 1]))
    # caps where the profile ends at r > 0
    if caps and prof[0][0] > 1e-6:
        bm.faces.new([rings[k][0] for k in range(seg)])
    if caps and prof[-1][0] > 1e-6:
        bm.faces.new([rings[k][-1] for k in range(seg)])
    if not caps:
        # closed profile (last point joins the first): stitch the last ring segment
        for k in range(seg):
            a, b = rings[k], rings[(k + 1) % seg]
            bm.faces.new((a[m - 1], b[m - 1], b[0], a[0]))
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-5)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    ob = bpy.data.objects.new(name, me)
    (collection or col()).objects.link(ob)
    if mat:
        me.materials.append(mat)
    return ob


def apply_mods(ob):
    dg = bpy.context.evaluated_depsgraph_get()
    ev = ob.evaluated_get(dg)
    me = bpy.data.meshes.new_from_object(ev, preserve_all_data_layers=True, depsgraph=dg)
    old = ob.data
    ob.modifiers.clear()
    ob.data = me
    if old.users == 0:
        bpy.data.meshes.remove(old)
    me.name = ob.name


def boolean(target, cutters, op='DIFFERENCE', keep=False):
    """Exact boolean with one object or a list (a temp collection). Cutter materials transfer to the new faces."""
    if not isinstance(cutters, (list, tuple)):
        cutters = [cutters]
    m = target.modifiers.new('bool', 'BOOLEAN')
    m.operation = op
    m.solver = 'EXACT'
    try:
        m.material_mode = 'TRANSFER'
    except Exception:
        pass
    if len(cutters) == 1:
        m.object = cutters[0]
    else:
        tc = bpy.data.collections.new('tmp_cut')
        bpy.context.scene.collection.children.link(tc)
        for c in cutters:
            for uc in list(c.users_collection):
                uc.objects.unlink(c)
            tc.objects.link(c)
        m.operand_type = 'COLLECTION'
        m.collection = tc
    for c in cutters:
        c.hide_render = True
        c.display_type = 'WIRE'
    apply_mods(target)
    if len(cutters) > 1:
        bpy.data.collections.remove(bpy.data.collections['tmp_cut'])
    if not keep:
        for c in cutters:
            me = c.data
            if c.name in bpy.data.objects:
                bpy.data.objects.remove(c)
            if me.users == 0:
                bpy.data.meshes.remove(me)
    return target


def transform(ob, mat):
    ob.data.transform(mat)
    ob.data.update()
    return ob


def rot_x(deg, pivot=(0, 0, 0)):
    p = Vector(pivot)
    return Matrix.Translation(p) @ Matrix.Rotation(math.radians(deg), 4, 'X') @ Matrix.Translation(-p)


def rot_y(deg, pivot=(0, 0, 0)):
    p = Vector(pivot)
    return Matrix.Translation(p) @ Matrix.Rotation(math.radians(deg), 4, 'Y') @ Matrix.Translation(-p)


def rot_z(deg, pivot=(0, 0, 0)):
    p = Vector(pivot)
    return Matrix.Translation(p) @ Matrix.Rotation(math.radians(deg), 4, 'Z') @ Matrix.Translation(-p)


def mirror_x(ob, name):
    me = ob.data.copy()
    me.transform(Matrix.Scale(-1, 4, (1, 0, 0)))
    me.flip_normals()
    o2 = bpy.data.objects.new(name, me)
    for c in ob.users_collection:
        c.objects.link(o2)
    return o2


def sharp_by_angle(ob, deg=30):
    bm = bmesh.new()
    bm.from_mesh(ob.data)
    lim = math.radians(deg)
    for e in bm.edges:
        if len(e.link_faces) == 2:
            e.smooth = e.calc_face_angle(0) < lim
        else:
            e.smooth = False
    bm.to_mesh(ob.data)
    bm.free()
    for p in ob.data.polygons:
        p.use_smooth = True


def finish(ob, width=0.25, segs=3, angle=30, harden=True, weighted=True):
    """Small rounded edges for the render: angle-limited bevel, smooth shading, hardened normals."""
    if GAME:
        # low poly: no bevel (the bake carries it); soft below CREASE, face-area weighted = Vextris crease_weighting "area"
        sharp_by_angle(ob, CREASE)
        w = ob.modifiers.new('wnormal', 'WEIGHTED_NORMAL')
        w.mode = 'FACE_AREA'
        w.keep_sharp = True
        w.weight = 50
        return ob
    sharp_by_angle(ob, angle)
    if width > 0:
        m = ob.modifiers.new('bevel', 'BEVEL')
        m.width = width
        m.segments = segs
        m.limit_method = 'ANGLE'
        m.angle_limit = math.radians(angle)
        m.use_clamp_overlap = True
        m.harden_normals = harden
    if weighted:
        w = ob.modifiers.new('wnormal', 'WEIGHTED_NORMAL')
        w.keep_sharp = True
        w.weight = 50
    return ob


def text_solid(name, body, size, origin, side='+x', depth=0.15, align='LEFT', spacing=1.0):
    """Text as a solid for engraving: reads correctly seen from `side` ('+x' or '-x'), baseline-left at origin (x on the face)."""
    cu = bpy.data.curves.new(name, 'FONT')
    cu.body = body
    cu.size = size
    cu.extrude = 0.6
    cu.align_x = align
    cu.space_character = spacing
    ob = bpy.data.objects.new(name, cu)
    bpy.context.scene.collection.objects.link(ob)
    dg = bpy.context.evaluated_depsgraph_get()
    me = bpy.data.meshes.new_from_object(ob.evaluated_get(dg))
    bpy.data.objects.remove(ob)
    bpy.data.curves.remove(cu)
    ox, oy, oz = origin
    if side == '+x':
        rot = Matrix(((0, 0, 1, 0), (1, 0, 0, 0), (0, 1, 0, 0), (0, 0, 0, 1)))
        off = Vector((ox - depth + 0.6, oy, oz))
    else:
        rot = Matrix(((0, 0, -1, 0), (-1, 0, 0, 0), (0, 1, 0, 0), (0, 0, 0, 1)))
        off = Vector((ox + depth - 0.6, oy, oz))
    me.transform(Matrix.Translation(off) @ rot)
    t = bpy.data.objects.new(name, me)
    col().objects.link(t)
    return t


def mat(name):
    m = bpy.data.materials.get(name)
    if not m:
        m = bpy.data.materials.new(name)
        m.use_nodes = True
    return m
