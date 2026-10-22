import bpy, bmesh, math
from mathutils import Vector, Matrix

TEX = r"D:/Minecraft Modding/AFL-vanilla-rework/src/main/resources/assets/apocalypse_firstlight/textures/block/"
COL = None

def coll(name="Workbench"):
    c = bpy.data.collections.get(name)
    if c is None:
        c = bpy.data.collections.new(name)
        bpy.context.scene.collection.children.link(c)
    return c

def img(path):
    name = path.split("/")[-1]
    im = bpy.data.images.get(name)
    if im is None:
        im = bpy.data.images.load(TEX + path)
    return im

def pbr_mat(name, base, rough=0.6, metal=0.0, tint=None, normal=None, normal_strength=0.4, rot90=False):
    m = bpy.data.materials.get(name)
    if m:
        return m
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    bsdf = next(n for n in nt.nodes if n.type == "BSDF_PRINCIPLED")
    bsdf.inputs["Roughness"].default_value = rough
    bsdf.inputs["Metallic"].default_value = metal
    if isinstance(base, str):
        t = nt.nodes.new("ShaderNodeTexImage")
        t.image = img(base)
        t.interpolation = "Closest"
        if rot90:
            uvn = nt.nodes.new("ShaderNodeUVMap"); mp = nt.nodes.new("ShaderNodeMapping")
            mp.inputs["Rotation"].default_value[2] = math.pi / 2
            nt.links.new(uvn.outputs["UV"], mp.inputs["Vector"]); nt.links.new(mp.outputs["Vector"], t.inputs["Vector"])
        if tint:
            mix = nt.nodes.new("ShaderNodeMixRGB")
            mix.blend_type = "MULTIPLY"
            mix.inputs[0].default_value = 1.0
            mix.inputs[2].default_value = (*tint, 1)
            nt.links.new(t.outputs["Color"], mix.inputs[1])
            nt.links.new(mix.outputs[0], bsdf.inputs["Base Color"])
        else:
            nt.links.new(t.outputs["Color"], bsdf.inputs["Base Color"])
        if normal:
            n = nt.nodes.new("ShaderNodeTexImage")
            n.image = img(normal)
            n.image.colorspace_settings.name = "Non-Color"
            n.interpolation = "Closest"
            nm = nt.nodes.new("ShaderNodeNormalMap")
            nm.inputs["Strength"].default_value = normal_strength
            nt.links.new(n.outputs["Color"], nm.inputs["Color"])
            nt.links.new(nm.outputs["Normal"], bsdf.inputs["Normal"])
    else:
        bsdf.inputs["Base Color"].default_value = (*base, 1)
    return m

def box_uv(ob, grain):
    """Box UVs in metres (1 m = 1 tile); the texture's v (grain) along world axis `grain` where the face allows."""
    me = ob.data
    bm = bmesh.new(); bm.from_mesh(me)
    uv = bm.loops.layers.uv.verify()
    mw = ob.matrix_world
    gi = "xyz".index(grain)
    for f in bm.faces:
        nrm = f.normal
        ax = max(range(3), key=lambda i: abs(nrm[i]))
        plane = [i for i in range(3) if i != ax]
        if gi in plane:
            v_ax = gi; u_ax = [i for i in plane if i != gi][0]
        else:
            u_ax, v_ax = plane
        for l in f.loops:
            co = mw @ l.vert.co
            l[uv].uv = (co[u_ax] + 0.13 * hash(ob.name) % 1, co[v_ax])
    bm.to_mesh(me); bm.free()

def box(name, a, b, mat, grain="x", bevel=0.003):
    x0, y0, z0 = a; x1, y1, z1 = b
    me = bpy.data.meshes.new(name)
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0)
    for v in bm.verts:
        v.co = Vector(((x0 + x1) / 2 + v.co.x * (x1 - x0), (y0 + y1) / 2 + v.co.y * (y1 - y0), (z0 + z1) / 2 + v.co.z * (z1 - z0)))
    bm.to_mesh(me); bm.free()
    ob = bpy.data.objects.new(name, me)
    coll().objects.link(ob)
    ob.data.materials.append(mat)
    box_uv(ob, grain)
    if bevel:
        bv = ob.modifiers.new("bevel", "BEVEL"); bv.width = bevel; bv.segments = 2; bv.limit_method = "ANGLE"
    for p in me.polygons: p.use_smooth = False
    return ob

def cyl(name, p0, p1, r, mat, verts=16, bevel=0.0, grain=None):
    p0 = Vector(p0); p1 = Vector(p1)
    d = p1 - p0
    me = bpy.data.meshes.new(name)
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, segments=verts, radius1=r, radius2=r, depth=d.length)
    bm.to_mesh(me); bm.free()
    ob = bpy.data.objects.new(name, me)
    coll().objects.link(ob)
    ob.data.materials.append(mat)
    q = Vector((0, 0, 1)).rotation_difference(d.normalized())
    ob.matrix_world = Matrix.Translation((p0 + p1) / 2) @ q.to_matrix().to_4x4()
    bpy.context.view_layer.update()
    if grain is None:
        g = max(range(3), key=lambda i: abs(d[i])); grain = "xyz"[g]
    box_uv(ob, grain)
    for p in me.polygons: p.use_smooth = True
    if bevel:
        bv = ob.modifiers.new("bevel", "BEVEL"); bv.width = bevel; bv.segments = 2; bv.limit_method = "ANGLE"
    return ob

def poly_prism(name, pts_xz, y0, y1, mat, grain="z"):
    """A prism from an x/z outline, extruded along y."""
    me = bpy.data.meshes.new(name)
    bm = bmesh.new()
    bottom = [bm.verts.new((x, y0, z)) for x, z in pts_xz]
    top = [bm.verts.new((x, y1, z)) for x, z in pts_xz]
    bm.faces.new(bottom[::-1]); bm.faces.new(top)
    n = len(pts_xz)
    for i in range(n):
        j = (i + 1) % n
        bm.faces.new((bottom[i], bottom[j], top[j], top[i]))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(me); bm.free()
    ob = bpy.data.objects.new(name, me)
    coll().objects.link(ob)
    ob.data.materials.append(mat)
    box_uv(ob, grain)
    return ob

def mats():
    return dict(
        top=pbr_mat("wbm_top_maple", "wood/red_maple_timber_side.png", 0.45, tint=(0.95, 0.82, 0.66), normal="wood/red_maple_timber_side_n.png"),
        slat=pbr_mat("wbm_slat_hemlock", "wood/eastern_hemlock_timber_side.png", 0.6, normal="wood/eastern_hemlock_timber_side_n.png"),
        pine=pbr_mat("wbm_pine", "wood/white_pine_timber_side.png", 0.65, tint=(0.9, 0.8, 0.62), normal="wood/white_pine_timber_side_n.png"),
        frame=pbr_mat("wbf_frame", "furniture/wood_v.png", 0.55, normal="furniture/wood_v_n.png"),
        board=pbr_mat("wbm_board_oak", "wood/white_oak_timber_side.png", 0.6, tint=(0.88, 0.76, 0.6), normal="wood/white_oak_timber_side_n.png"),
        flat=pbr_mat("wbf_flat", "furniture/wood.png", 0.55, normal="furniture/wood_n.png", rot90=True),
        peg=pbr_mat("wb_peg", "wood/shagbark_hickory_timber_side.png", 0.55),
        iron=pbr_mat("wb_cast_iron", "furniture/cast_iron.png", 0.55, 0.6, normal="furniture/cast_iron_n.png"),
        vise=pbr_mat("wb_vise_paint", (0.13, 0.17, 0.19), 0.45, 0.2),
        steel=pbr_mat("wb_steel", (0.55, 0.56, 0.58), 0.3, 1.0),
        dark=pbr_mat("wb_dark", (0.03, 0.025, 0.02), 0.9),
        handle=pbr_mat("wb_handle", "wood/shagbark_hickory_timber_side.png", 0.4, tint=(0.75, 0.55, 0.38)),
        red=pbr_mat("wb_red_paint", (0.42, 0.06, 0.04), 0.5),
        tin=pbr_mat("wb_tin", (0.62, 0.6, 0.55), 0.35, 0.9),
        brass=pbr_mat("wb_brass", (0.7, 0.5, 0.22), 0.35, 1.0),
    )
