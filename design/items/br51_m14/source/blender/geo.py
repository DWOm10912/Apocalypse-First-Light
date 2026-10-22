# BR-51 concept: shape helpers on top of lib.py. Units mm, +y muzzle, +z up, +x right; bore axis = y axis.
# The BR-51 is Kessler Arsenal's own design. It works like the M14 (TM 9-1005-223-10) and has the M14's proportions,
# measured from a museum side view; the shapes are our own. y = 0 is the back of the buttplate. Stations:
#   receiver heel 325, rear sight 335-382, bridge + clip guide 382-430, open top 430-487, front ring 487-522,
#   bolt face 520, magazine 438-516, trigger 372, upper handguard 528-852, nose cap 850-866, gas block 862-935,
#   front sight 1018-1044, barrel muzzle 1079 (559 mm barrel), flash hider tip 1126 (overall length).
import math, bmesh, bpy
from mathutils import Vector, Matrix
exec(open(os.path.join(BASE, 'lib.py')).read())


def interp(table, y):
    """Smooth (Catmull-Rom) interpolation through (y, v) knots, clamped at the ends."""
    if y <= table[0][0]:
        return table[0][1]
    if y >= table[-1][0]:
        return table[-1][1]
    for i in range(len(table) - 1):
        y0, v0 = table[i]
        y1, v1 = table[i + 1]
        if y0 <= y <= y1:
            vm = table[i - 1][1] if i > 0 else v0 - (v1 - v0)
            vp = table[i + 2][1] if i + 2 < len(table) else v1 + (v1 - v0)
            t = (y - y0) / (y1 - y0)
            m0, m1 = (v1 - vm) / 2, (vp - v0) / 2
            t2, t3 = t * t, t * t * t
            return (2 * t3 - 3 * t2 + 1) * v0 + (t3 - 2 * t2 + t) * m0 + (-2 * t3 + 3 * t2) * v1 + (t3 - t2) * m1
    return table[-1][1]


def lerp_table(table, y):
    if y <= table[0][0]:
        return table[0][1]
    for (y0, v0), (y1, v1) in zip(table, table[1:]):
        if y0 <= y <= y1:
            return v0 + (v1 - v0) * (y - y0) / (y1 - y0)
    return table[-1][1]


def superellipse(w_bot, w_top, z_bot, z_top, n_bot, n_top, m=56, cx=0.0):
    """Closed (x, z) section: half width runs from w_bot at the bottom to w_top at the top; the lower half uses
    exponent n_bot, the upper half n_top (2 = ellipse, larger = squarer)."""
    zc, h = (z_top + z_bot) / 2, (z_top - z_bot) / 2
    pts = []
    for k in range(m):
        t = 2 * math.pi * k / m
        c, s = math.cos(t), math.sin(t)
        n = n_top if s >= 0 else n_bot
        z = zc + h * math.copysign(abs(s) ** (2 / n), s)
        f = (z - z_bot) / (z_top - z_bot)
        w = w_bot + (w_top - w_bot) * f
        pts.append((cx + w * math.copysign(abs(c) ** (2 / n), c), z))
    return pts


def sweep(name, prof, frames, mat=None):
    """Closed solid through one (u, v) profile placed at frames [(origin, u axis, v axis)] (Vectors)."""
    bm = bmesh.new()
    rings = [[bm.verts.new(o + ua * u + va * v) for u, v in prof] for o, ua, va in frames]
    n = len(prof)
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
    col().objects.link(ob)
    if mat:
        me.materials.append(mat)
    return ob


def box(name, x0, x1, y0, y1, z0, z1, mat=None, r=0.0):
    prof = rrect(x0, x1, z0, z1, r, 4) if r > 0 else [(x0, z0), (x1, z0), (x1, z1), (x0, z1)]
    return solid(name, prof, 'xz', y0, y1, mat)


def cyl(name, r, y0, y1, cx=0.0, cz=0.0, seg=48, mat=None):
    return lathe(name, [(r, y0), (r, y1)], 'y', seg, mat, center=(cx, cz))


def cyl_x(name, r, x0, x1, cy, cz, seg=40, mat=None):
    return lathe(name, [(r, x0), (r, x1)], 'x', seg, mat, center=(cy, cz))


def cyl_z(name, r, z0, z1, cx, cy, seg=40, mat=None):
    return lathe(name, [(r, z0), (r, z1)], 'z', seg, mat, center=(cx, cy))


def capsule_y(name, r, y0, y1, cx, cz, seg=32, mat=None):
    """Round-ended rod along y (groove cutter)."""
    prof = [(0.0, y0 - r)]
    for k in range(1, 9):
        a = math.pi / 2 * k / 8
        prof.append((r * math.sin(a), y0 - r * math.cos(a)))
    for k in range(0, 9):
        a = math.pi / 2 * k / 8
        prof.append((r * math.cos(a), y1 + r * math.sin(a)))
    prof.append((0.0, y1 + r))
    return lathe(name, prof, 'y', seg, mat, center=(cx, cz))


def ring_x(name, R, r, cy, cz, seg=40, mat=None):
    """Torus whose axis runs along x (a sling loop hanging in the yz plane), centred at (0, cy, cz)."""
    m = 8 if GAME else 16
    prof = [(R + r * math.cos(2 * math.pi * k / m), r * math.sin(2 * math.pi * k / m)) for k in range(m)]
    o = lathe(name, prof, 'y', seg, mat, center=(0, 0), caps=False)
    o.data.transform(Matrix.Rotation(math.radians(90), 4, 'Z'))
    o.data.transform(Matrix.Translation((0, cy, cz)))
    for p in o.data.polygons:
        p.use_smooth = True
    return o


def halfspace(name, point, normal, size=2000):
    """Big box filling the side of the plane (point, normal) the normal points into."""
    o = box(name, -size / 2, size / 2, -size / 2, size / 2, 0, size)
    n = Vector(normal).normalized()
    q = Vector((0, 0, 1)).rotation_difference(n)
    o.data.transform(Matrix.Translation(point) @ q.to_matrix().to_4x4())
    return o


def dup(ob, name):
    o2 = bpy.data.objects.new(name, ob.data.copy())
    for c in ob.users_collection:
        c.objects.link(o2)
    return o2
