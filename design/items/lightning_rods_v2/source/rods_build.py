# Lightning rods V2 concept: A copper air terminal (NFPA 780 Class II, 5/8" x 36", tripod brace), B Victorian barn rod.
# Metres, Z up. A at x 0, B at x 4. Each stands on a 1 m grey block (top at z 0) with a blue cell cage.
import bpy, bmesh, math
from mathutils import Vector, Matrix

bpy.ops.wm.read_homefile(use_empty=True)
sc = bpy.context.scene

def coll(name):
    c = bpy.data.collections.get(name) or bpy.data.collections.new(name)
    if c.name not in sc.collection.children: sc.collection.children.link(c)
    return c

CUR = [None]
def put(o):
    CUR[0].objects.link(o)
    return o

def mat(name, color, metal=0.0, rough=0.5, trans=0.0, ior=1.45, emit=None):
    m = bpy.data.materials.get(name) or bpy.data.materials.new(name)
    m.use_nodes = True
    b = next(n for n in m.node_tree.nodes if n.type == "BSDF_PRINCIPLED")
    b.inputs["Base Color"].default_value = (*color, 1)
    b.inputs["Metallic"].default_value = metal
    b.inputs["Roughness"].default_value = rough
    b.inputs["Transmission Weight"].default_value = trans
    b.inputs["IOR"].default_value = ior
    if emit:
        b.inputs["Emission Color"].default_value = (*emit, 1)
        b.inputs["Emission Strength"].default_value = 3.0
    return m

COPPER = mat("copper", (0.80, 0.42, 0.26), 1, 0.32)
COPPER_OLD = mat("copper_old", (0.55, 0.30, 0.19), 1, 0.5)
BRONZE = mat("bronze", (0.52, 0.38, 0.22), 1, 0.45)
STEEL = mat("stainless", (0.75, 0.75, 0.76), 1, 0.3)
IRON = mat("cast_iron", (0.09, 0.085, 0.08), 0.6, 0.75)
COBALT = mat("cobalt_glass", (0.08, 0.22, 0.95), 0, 0.05, 1.0, 1.52)
AQUA = mat("aqua_glass", (0.55, 0.85, 0.82), 0, 0.08, 1.0, 1.5)
BLOCK = mat("block", (0.42, 0.42, 0.41), 0, 0.85)
CAGE = mat("cage", (0.2, 0.75, 1.0), 0, 0.5, emit=(0.2, 0.75, 1.0))

def obj_from_bm(name, bm, m, smooth=False):
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me); bm.free()
    if smooth:
        for p in me.polygons: p.use_smooth = True
    o = put(bpy.data.objects.new(name, me))
    o.data.materials.append(m)
    return o

def cyl(name, r, z0, z1, m, x=0, y=0, verts=32, r2=None):
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, segments=verts, radius1=r, radius2=r if r2 is None else r2, depth=z1 - z0)
    o = obj_from_bm(name, bm, m, smooth=verts > 8)
    o.location = (x, y, (z0 + z1) / 2)
    return o

def basis(d, side_hint=None):
    d = d.normalized()
    s = side_hint if side_hint is not None else (Vector((0, 0, 1)) if abs(d.z) < 0.9 else Vector((1, 0, 0))).cross(d)
    s = (s - d * s.dot(d)).normalized()
    n = s.cross(d)
    return n, s, d

def seg(name, p, q, r, m, verts=24, r2=None):
    p, q = Vector(p), Vector(q)
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, segments=verts, radius1=r, radius2=r if r2 is None else r2, depth=(q - p).length)
    o = obj_from_bm(name, bm, m, smooth=True)
    n, s, d = basis(q - p)
    o.matrix_world = Matrix.Translation((p + q) / 2) @ Matrix((n, s, d)).transposed().to_4x4()
    return o

def strap(name, p, q, w, t, m, side):
    # a flat bar from p to q, w wide along `side`, t thick
    p, q = Vector(p), Vector(q)
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0)
    o = obj_from_bm(name, bm, m)
    n, s, d = basis(q - p, Vector(side))
    o.matrix_world = Matrix.Translation((p + q) / 2) @ Matrix((n * t, s * w, d * (q - p).length)).transposed().to_4x4()
    return o

def box(name, size, loc, m, bevel=0.0):
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0)
    o = obj_from_bm(name, bm, m)
    o.scale = size; o.location = loc
    if bevel:
        b = o.modifiers.new("bev", "BEVEL"); b.width = bevel; b.segments = 2
    return o

def ball(name, r, loc, m, sz=1.0):
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=48, v_segments=24, radius=r)
    o = obj_from_bm(name, bm, m, smooth=True)
    o.location = loc; o.scale = (1, 1, sz)
    return o

def tube(name, pts, r, m):
    cu = bpy.data.curves.new(name, "CURVE"); cu.dimensions = "3D"
    cu.bevel_depth = r; cu.bevel_resolution = 4; cu.use_fill_caps = True
    sp = cu.splines.new("POLY"); sp.points.add(len(pts) - 1)
    for i, p in enumerate(pts): sp.points[i].co = (*p, 1)
    o = put(bpy.data.objects.new(name, cu)); o.data.materials.append(m)
    return o

def rope(name, pts, r, m, turns_per_m=28):
    # stranded cable: three strands twisted round the path
    path = [Vector(p) for p in pts]
    dense = []
    for a, b in zip(path, path[1:]):
        n = max(2, int((b - a).length / 0.004))
        dense += [a.lerp(b, i / n) for i in range(n)]
    dense.append(path[-1])
    length = 0
    for k in range(3):
        out, acc = [], 0
        for i, p in enumerate(dense):
            if i: acc += (p - dense[i - 1]).length
            d = (dense[min(i + 1, len(dense) - 1)] - dense[max(i - 1, 0)]).normalized()
            nn, ss, _ = basis(d)
            a = acc * turns_per_m * 2 * math.pi + k * 2 * math.pi / 3
            out.append(tuple(p + (nn * math.cos(a) + ss * math.sin(a)) * r * 0.55))
        tube(f"{name}_{k}", out, r * 0.5, m)

def sheet(name, poly, t, m, y=0.0):
    # a flat plate in the XZ plane, t thick along Y
    bm = bmesh.new()
    vs = [bm.verts.new((x, y - t / 2, z)) for x, z in poly]
    f = bm.faces.new(vs)
    bmesh.ops.extrude_face_region(bm, geom=[f])
    for v in bm.verts:
        if v not in vs: v.co.y = y + t / 2
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    return obj_from_bm(name, bm, m)

def stage(name, ox, cells_high):
    CUR[0] = coll(name)
    box("block", (1, 1, 1), (ox, 0, -0.5), BLOCK)
    e = []
    for x in (-0.5, 0.5):
        for y in (-0.5, 0.5):
            e.append(((x, y, 0), (x, y, cells_high)))
    for z in range(cells_high + 1):
        for (a, b) in (((-.5, -.5), (.5, -.5)), ((.5, -.5), (.5, .5)), ((.5, .5), (-.5, .5)), ((-.5, .5), (-.5, -.5))):
            e.append(((a[0], a[1], z), (b[0], b[1], z)))
    for i, (p, q) in enumerate(e):
        seg(f"cage{i}", (ox + p[0], p[1], p[2]), (ox + q[0], q[1], q[2]), 0.0025, CAGE, verts=6)

# ------------------------------------------------------------------ A: copper air terminal, Class II, braced
stage("Stage_A", 0, 1)
CUR[0] = coll("A")
IN = 0.0254
# cast bronze flat base: 115 mm square plate, raised boss with threaded socket, cable saddle clamp on +X
box("A_plate", (0.115, 0.115, 0.009), (0, 0, 0.0045), BRONZE, bevel=0.003)
cyl("A_boss", 0.026, 0.009, 0.030, BRONZE, verts=40, r2=0.020)
cyl("A_socket", 0.017, 0.030, 0.062, BRONZE, verts=6)          # hex socket the rod screws into
cyl("A_lock", 0.0105, 0.062, 0.068, BRONZE, verts=6)
for sx in (-1, 1):
    for sy in (-1, 1):
        cyl("A_washer", 0.011, 0.009, 0.0115, STEEL, sx * 0.042, sy * 0.042, 24)
        cyl("A_bolt", 0.0075, 0.0115, 0.017, STEEL, sx * 0.042, sy * 0.042, 6)
box("A_saddle", (0.034, 0.030, 0.020), (0.040, 0, 0.019), BRONZE, bevel=0.002)
box("A_saddle_cap", (0.030, 0.034, 0.008), (0.040, 0, 0.033), BRONZE, bevel=0.002)
cyl("A_cap_screw", 0.005, 0.037, 0.042, STEEL, 0.040, 0.011, 6)
cyl("A_cap_screw2", 0.005, 0.037, 0.042, STEEL, 0.040, -0.011, 6)
# 5/8" solid copper rod, 36" long, rounded blunt tip
ROD_R, TOP = 5 / 16 * IN, 0.062 + 36 * IN
cyl("A_rod", ROD_R, 0.055, TOP - 0.006, COPPER, verts=32)
ball("A_tip", ROD_R * 1.15, (0, 0, TOP - 0.006), COPPER, sz=1.1)
# tripod brace (NFPA practice: terminals over 24" braced at half height or more): collar + three copper straps
CZ = 0.50
cyl("A_collar", 0.017, CZ - 0.016, CZ + 0.016, BRONZE, verts=32)
cyl("A_collar_screw", 0.0045, CZ - 0.004, CZ + 0.004, STEEL, verts=6).rotation_euler = (0, math.pi / 2, 0)
FOOT = 0.27
for k, ang in enumerate((90, 210, 330)):
    a = math.radians(ang)
    u = Vector((math.cos(a), math.sin(a), 0)); s = Vector((-u.y, u.x, 0))
    top = u * 0.016 + Vector((0, 0, CZ - 0.008))
    knee = u * (FOOT - 0.02) + Vector((0, 0, 0.012))
    strap(f"A_leg{k}", top, knee, 0.019, 0.0035, COPPER, s)
    foot = u * (FOOT + 0.035) + Vector((0, 0, 0.0018))
    strap(f"A_foot{k}", knee + Vector((0, 0, -0.01)), foot, 0.019, 0.0035, COPPER, s)
    cyl(f"A_foot_screw{k}", 0.0055, 0.0035, 0.0075, STEEL, *(u * (FOOT + 0.022)).xy, 16)
# 1/2" class II stranded copper main conductor from the saddle out along the roof to the edge, two cable holders
rope("A_cable", [(0.040, 0, 0.022), (0.075, 0, 0.012), (0.12, 0, 0.0075), (0.62, 0, 0.0075)], 0.0068, COPPER)
for x in (0.28, 0.45):
    box("A_holder", (0.022, 0.04, 0.003), (x, 0, 0.0015), BRONZE, bevel=0.001)
    strap("A_holder_strap", (x, -0.010, 0.0075), (x, 0.010, 0.0075), 0.014, 0.004, BRONZE, (1, 0, 0))

# ------------------------------------------------------------------ B: Victorian barn rod, c. 1900-1920
OX = 4.0
stage("Stage_B", OX, 2)
CUR[0] = coll("B")
# wrought-iron tripod stand: three curved legs from a cast collar, flat feet
cyl("B_collar", 0.028, 0.20, 0.31, IRON, OX, 0, 32, r2=0.022)
cyl("B_collar_ring", 0.032, 0.20, 0.215, IRON, OX, 0, 32)
for k, ang in enumerate((90, 210, 330)):
    a = math.radians(ang); u = Vector((math.cos(a), math.sin(a), 0))
    pts = []
    for i in range(25):
        t = i / 24
        r = 0.028 + t * 0.20 + 0.035 * math.sin(t * math.pi) * 0.6
        z = 0.205 * (1 - t) ** 1.35 + 0.006
        pts.append(tuple(Vector((OX, 0, 0)) + u * r + Vector((0, 0, z))))
    tube(f"B_leg{k}", pts, 0.0085, IRON)
    f =Vector((OX, 0, 0)) + u * 0.235
    box(f"B_foot{k}", (0.05, 0.03, 0.006), (f.x, f.y, 0.003), IRON).rotation_euler = (0, 0, a)
# twisted square shaft, copper-skinned iron, 3/4" across, from the collar to the finial
def twisted(name, z0, z1, w, turns, m):
    bm = bmesh.new(); N = 240; rings = []
    sq = [(w / 2, w / 2 * 0.62), (w / 2 * 0.62, w / 2)]
    prof = []
    for q in range(4):
        for (x, y) in sq:
            c, s_ = math.cos(q * math.pi / 2), math.sin(q * math.pi / 2)
            prof.append((x * c - y * s_, x * s_ + y * c))
    for i in range(N + 1):
        z = z0 + (z1 - z0) * i / N; a = turns * 2 * math.pi * i / N
        ca, sa = math.cos(a), math.sin(a)
        rings.append([bm.verts.new((OX + x * ca - y * sa, x * sa + y * ca, z)) for x, y in prof])
    for i in range(N):
        for j in range(len(prof)):
            bm.faces.new((rings[i][j], rings[i][(j + 1) % len(prof)], rings[i + 1][(j + 1) % len(prof)], rings[i + 1][j]))
    bm.faces.new(list(reversed(rings[0]))); bm.faces.new(rings[-1])
    return obj_from_bm(name, bm, m)
twisted("B_shaft_low", 0.30, 1.09, 0.019, 5, COPPER_OLD)
twisted("B_shaft_high", 1.21, 1.63, 0.019, 2.7, COPPER_OLD)
# cobalt glass ball, 4-1/2", with copper end sleeves
BZ = 1.15
ball("B_ball", 0.057, (OX, 0, BZ), COBALT, sz=1.05)
for sgn in (-1, 1):
    cyl("B_sleeve", 0.0125, BZ + sgn * 0.058, BZ + sgn * 0.09, COPPER_OLD, OX, 0, 32, r2=None)
    cyl("B_sleeve_flare", 0.020, BZ + sgn * 0.052, BZ + sgn * 0.064, COPPER_OLD, OX, 0, 32, r2=0.0125) if sgn < 0 else \
        cyl("B_sleeve_flare", 0.0125, BZ + 0.052, BZ + 0.064, COPPER_OLD, OX, 0, 32, r2=0.020)
# sheet-copper arrow vane under the top, 23" long, on a collar
VZ = 1.47
cyl("B_vane_collar", 0.017, VZ - 0.03, VZ + 0.03, COPPER_OLD, OX, 0, 32)
seg("B_arrow_shaft", (OX - 0.30, 0, VZ), (OX + 0.27, 0, VZ), 0.0055, COPPER_OLD, 16)
sheet("B_arrow_head", [(OX + 0.26, VZ + 0.05), (OX + 0.36, VZ), (OX + 0.26, VZ - 0.05), (OX + 0.285, VZ)], 0.002, COPPER_OLD)
sheet("B_arrow_tail", [(OX - 0.33, VZ + 0.075), (OX - 0.20, VZ + 0.075), (OX - 0.13, VZ + 0.008), (OX - 0.13, VZ - 0.008), (OX - 0.20, VZ - 0.075), (OX - 0.33, VZ - 0.075), (OX - 0.27, VZ)], 0.002, COPPER_OLD)
for k, x in enumerate((-0.30, -0.255, -0.21)):   # pressed ribs on the tail
    for sz in (1, -1):
        seg(f"B_rib{k}{sz}", (OX + x, -0.0014, VZ + sz * 0.068), (OX + x + 0.05, -0.0014, VZ + sz * 0.012), 0.0011, COPPER_OLD, 6)
# finial: knob, three-pronged crow's-foot point round a long centre spike
FZ = 1.63
ball("B_knob", 0.024, (OX, 0, FZ + 0.012), COPPER_OLD)
cyl("B_neck", 0.011, FZ + 0.03, FZ + 0.06, COPPER_OLD, OX, 0, 24, r2=0.007)
seg("B_spike", (OX, 0, FZ + 0.06), (OX, 0, 1.80), 0.0065, COPPER_OLD, 24, r2=0.0008)
for k, ang in enumerate((0, 120, 240)):
    a = math.radians(ang); u = Vector((math.cos(a), math.sin(a), 0))
    base = Vector((OX, 0, FZ + 0.05)); mid = base + u * 0.035 + Vector((0, 0, 0.03))
    seg(f"B_prong{k}a", base, mid, 0.0035, COPPER_OLD, 12)
    seg(f"B_prong{k}b", mid, mid + u * 0.008 + Vector((0, 0, 0.055)), 0.0035, COPPER_OLD, 12, r2=0.0006)
# twisted copper cable from a lug on the collar down to the block top and off the edge, held on a glass knob insulator
cyl("B_lug", 0.012, 0.215, 0.24, COPPER_OLD, OX + 0.036, 0, 16)
rope("B_cable", [(OX + 0.04, 0, 0.215), (OX + 0.07, 0, 0.12), (OX + 0.12, 0, 0.06), (OX + 0.33, 0, 0.055), (OX + 0.36, 0, 0.04), (OX + 0.48, 0, 0.012), (OX + 0.62, 0, 0.012)], 0.0055, COPPER_OLD, 22)
box("B_ins_bracket", (0.012, 0.012, 0.03), (OX + 0.33, 0, 0.015), IRON)
cyl("B_insulator", 0.021, 0.03, 0.05, AQUA, OX + 0.33, 0, 32, r2=0.017)
ball("B_insulator_top", 0.017, (OX + 0.33, 0, 0.052), AQUA, sz=0.7)

# ------------------------------------------------------------------ light + world
w = bpy.data.worlds.new("w"); sc.world = w; w.use_nodes = True
bg = next(n for n in w.node_tree.nodes if n.type == "BACKGROUND")
bg.inputs["Color"].default_value = (0.42, 0.45, 0.5, 1); bg.inputs["Strength"].default_value = 0.7
CUR[0] = coll("Lights")
sun = bpy.data.lights.new("sun", "SUN"); sun.energy = 3.2; sun.angle = math.radians(2)
so = put(bpy.data.objects.new("sun", sun)); so.rotation_euler = (math.radians(50), 0, math.radians(35))
fill = bpy.data.lights.new("fill", "AREA"); fill.energy = 60; fill.size = 3
fo = put(bpy.data.objects.new("fill", fill)); fo.location = (-3, -3, 2.5); fo.rotation_euler = (math.radians(60), 0, math.radians(-45))
print("built", len(bpy.data.objects))
