# Shared by the Z0 review scenes (z0_blender_build.py, z0_fresh_build.py): the old cube body (afl_humanoid_v1) with
# Main's SkinMesh 2.1.1 ring levels (shoulder and hip rings added), rest stance, vanilla 64 net UVs read from 128 px
# skins, vanilla entity lighting. The scene scripts set MATS, BRIM and FONT before building figures.
import bpy, bmesh, math, os

HERE = r"D:\Minecraft Modding\AFL-vanilla-rework\design\creatures\infected_z0"
PX = 1 / 16

# Rig parts (px, y up, feet at 0): from, size, base uv, overlay uv, ring levels from the top end.
ARM = [0, 2, 6, 10, 12]          # top, shoulder pivot ring, elbow, wrist, end
LEG = [0, 2, 4, 6, 10, 12]       # on the pelvis, two hip rings, knee, ankle, end
def parts(slim):
    aw = 3 if slim else 4
    return {
        "head": ((-4, 24, -4), (8, 8, 8), (0, 0), (32, 0), [0, 8], 0.5),
        "body": ((-4, 12, -2), (8, 12, 4), (16, 16), (16, 32), [0, 6, 12], 0.25),
        "rarm": ((-4 - aw, 12, -2), (aw, 12, 4), (40, 16), (40, 32), ARM, 0.25),
        "larm": ((4, 12, -2), (aw, 12, 4), (32, 48), (48, 48), ARM, 0.25),
        "rleg": ((-3.9, 0, -2), (4, 12, 4), (0, 16), (0, 32), LEG, 0.25),
        "lleg": ((-0.1, 0, -2), (4, 12, 4), (16, 48), (0, 48), LEG, 0.25),
    }

def face_rect(face, u, v, w, h, d):
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}[face]

def box(name, frm, size, uvo, levels, inflate, mat):
    """Blender: X = character's left, -Y = front, Z up. Rings at every level share vertices."""
    (x0, y0, z0), (w, h, d) = frm, size
    X0, X1 = (x0 - inflate) * PX, (x0 + w + inflate) * PX
    Y0, Y1 = (z0 - inflate) * PX, (z0 + d + inflate) * PX
    top, bot = y0 + h, y0
    zs = [((top + inflate) if L == 0 else (bot - inflate) if L == h else (top - L)) * PX for L in levels]
    ts = [L / h for L in levels]
    bm = bmesh.new()
    uvl = bm.loops.layers.uv.new("UVMap")
    rings = [[bm.verts.new(c) for c in ((X0, Y0, z), (X1, Y0, z), (X1, Y1, z), (X0, Y1, z))] for z in zs]
    R = {k: face_rect(k, uvo[0], uvo[1], w, h, d) for k in ("top", "bottom", "right", "front", "left", "back")}
    def put(f, uvs):
        for loop, (U, V) in zip(f.loops, uvs):
            loop[uvl].uv = (U / 64, 1 - V / 64)
    # side faces: (corner a, corner b, face) with a at the image's left edge
    sides = [(0, 1, "front"), (2, 3, "back"), (3, 0, "right"), (1, 2, "left")]
    for i in range(len(zs) - 1):
        for a, b, fn in sides:
            fu, fv, fw, fh = R[fn]
            ra, rb = rings[i], rings[i + 1]
            f = bm.faces.new((ra[a], ra[b], rb[b], rb[a]))
            put(f, [(fu, fv + ts[i] * fh), (fu + fw, fv + ts[i] * fh), (fu + fw, fv + ts[i + 1] * fh), (fu, fv + ts[i + 1] * fh)])
    t, b_ = rings[0], rings[-1]
    fu, fv, fw, fh = R["top"]      # image top = back
    f = bm.faces.new((t[3], t[2], t[1], t[0]))
    put(f, [(fu, fv), (fu + fw, fv), (fu + fw, fv + fh), (fu, fv + fh)])
    fu, fv, fw, fh = R["bottom"]   # image top = front
    f = bm.faces.new((b_[0], b_[1], b_[2], b_[3]))
    put(f, [(fu, fv), (fu + fw, fv), (fu + fw, fv + fh), (fu, fv + fh)])
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    ob = bpy.data.objects.new(name, me)
    ob.data.materials.append(mat)
    return ob

def srgb(c):
    return [((v / 255 + 0.055) / 1.055) ** 2.4 if v / 255 > 0.04045 else v / 255 / 12.92 for v in c]

def flat_mat(name, rgb):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    nt.nodes.clear()
    e = nt.nodes.new("ShaderNodeEmission")
    e.inputs[0].default_value = (*srgb(rgb), 1)
    o = nt.nodes.new("ShaderNodeOutputMaterial")
    nt.links.new(e.outputs[0], o.inputs[0])
    return m

def lit(nt, colour_socket, out):
    """Minecraft entity lighting: 0.4 ambient + 0.6 x two fixed lights, applied in sRGB (factor ^ 2.2 in linear)."""
    N = nt.nodes
    geo = N.new("ShaderNodeNewGeometry")
    acc = None
    for L in ((0.2, -0.7, 1.0), (-0.2, 0.7, 1.0)):
        n = math.sqrt(sum(c * c for c in L))
        dot = N.new("ShaderNodeVectorMath"); dot.operation = "DOT_PRODUCT"
        dot.inputs[1].default_value = [c / n for c in L]
        nt.links.new(geo.outputs["Normal"], dot.inputs[0])
        mx = N.new("ShaderNodeMath"); mx.operation = "MAXIMUM"; mx.inputs[1].default_value = 0
        nt.links.new(dot.outputs["Value"], mx.inputs[0])
        if acc is None:
            acc = mx
        else:
            add = N.new("ShaderNodeMath"); add.operation = "ADD"
            nt.links.new(acc.outputs[0], add.inputs[0]); nt.links.new(mx.outputs[0], add.inputs[1]); acc = add
    ma = N.new("ShaderNodeMath"); ma.operation = "MULTIPLY_ADD"
    ma.inputs[1].default_value = 0.6; ma.inputs[2].default_value = 0.4
    nt.links.new(acc.outputs[0], ma.inputs[0])
    mn = N.new("ShaderNodeMath"); mn.operation = "MINIMUM"; mn.inputs[1].default_value = 1
    nt.links.new(ma.outputs[0], mn.inputs[0])
    pw = N.new("ShaderNodeMath"); pw.operation = "POWER"; pw.inputs[1].default_value = 2.2
    nt.links.new(mn.outputs[0], pw.inputs[0])
    sc = N.new("ShaderNodeVectorMath"); sc.operation = "SCALE"
    nt.links.new(colour_socket, sc.inputs[0]); nt.links.new(pw.outputs[0], sc.inputs["Scale"])
    em = N.new("ShaderNodeEmission")
    nt.links.new(sc.outputs[0], em.inputs[0])
    return em

def skin_mat(key, path):
    m = bpy.data.materials.new("z0_" + key)
    m.use_nodes = True
    nt = m.node_tree
    nt.nodes.clear()
    tex = nt.nodes.new("ShaderNodeTexImage")
    tex.image = bpy.data.images.load(path, check_existing=True)
    tex.interpolation = "Closest"
    em = lit(nt, tex.outputs["Color"], None)
    gt = nt.nodes.new("ShaderNodeMath"); gt.operation = "GREATER_THAN"; gt.inputs[1].default_value = 0.5
    nt.links.new(tex.outputs["Alpha"], gt.inputs[0])
    tr = nt.nodes.new("ShaderNodeBsdfTransparent")
    mix = nt.nodes.new("ShaderNodeMixShader")
    nt.links.new(gt.outputs[0], mix.inputs[0]); nt.links.new(tr.outputs[0], mix.inputs[1]); nt.links.new(em.outputs[0], mix.inputs[2])
    o = nt.nodes.new("ShaderNodeOutputMaterial")
    nt.links.new(mix.outputs[0], o.inputs[0])
    for attr, val in (("surface_render_method", "DITHERED"), ("blend_method", "CLIP")):
        try:
            setattr(m, attr, val)
        except Exception:
            pass
    return m

def brim_mat():
    m = bpy.data.materials.new("z0_brim")
    m.use_nodes = True
    nt = m.node_tree
    nt.nodes.clear()
    geo = nt.nodes.new("ShaderNodeNewGeometry")
    sep = nt.nodes.new("ShaderNodeSeparateXYZ")
    nt.links.new(geo.outputs["Normal"], sep.inputs[0])
    lt = nt.nodes.new("ShaderNodeMath"); lt.operation = "LESS_THAN"; lt.inputs[1].default_value = -0.5
    nt.links.new(sep.outputs["Z"], lt.inputs[0])
    mx = nt.nodes.new("ShaderNodeMix"); mx.data_type = "RGBA"
    mx.inputs["A"].default_value = (*srgb((0x2D, 0x38, 0x48)), 1)   # navy top and edge
    mx.inputs["B"].default_value = (*srgb((0x3E, 0x4A, 0x3A)), 1)   # green underside
    nt.links.new(lt.outputs[0], mx.inputs["Factor"])
    em = lit(nt, mx.outputs["Result"], None)
    o = nt.nodes.new("ShaderNodeOutputMaterial")
    nt.links.new(em.outputs[0], o.inputs[0])
    return m

def cube(name, lo, hi, mat):
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1)
    for v in bm.verts:
        v.co = [(lo[k] + (hi[k] - lo[k]) * (v.co[k] + 0.5)) * PX for k in range(3)]
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me); bm.free()
    ob = bpy.data.objects.new(name, me)
    ob.data.materials.append(mat)
    return ob

def figure(key, path, slim, coll, loc, yaw, scale=1.0, head_only=False):
    root = bpy.data.objects.new(f"{key}_root", None)
    coll.objects.link(root)
    mat = MATS[key]
    for pn, (frm, size, base, over, levels, infl) in parts(slim).items():
        if head_only and pn != "head":
            continue
        for layer, uvo, inf in (("base", base, 0.0), ("over", over, infl)):
            ob = box(f"{key}_{pn}_{layer}", frm, size, uvo, levels, inf, mat)
            coll.objects.link(ob)
            ob.parent = root
    if key == "b":   # cap brim: a thin mesh piece on the forehead, 3.5 px out, 0.5 px thick
        ob = cube("b_cap_brim", (-4.3, -7.6, 29.1), (4.3, -4.5, 29.6), BRIM)
        coll.objects.link(ob)
        ob.parent = root
    if head_only:   # centre the head on the root
        for ch in root.children:
            ch.location = (0, 0, -28 * PX)
    root.location = loc
    root.rotation_euler = (0, 0, math.radians(yaw))
    root.scale = (scale,) * 3
    return root

def text(coll, s, loc, size, rgb=(0x2A, 0x2E, 0x32)):
    cu = bpy.data.curves.new("label", "FONT")
    cu.body = s
    cu.size = size
    if FONT:
        cu.font = FONT
    ob = bpy.data.objects.new("label", cu)
    ob.data.materials.append(flat_mat("label", rgb))
    ob.location = loc
    ob.rotation_euler = (math.radians(90), 0, 0)
    coll.objects.link(ob)
    return ob

