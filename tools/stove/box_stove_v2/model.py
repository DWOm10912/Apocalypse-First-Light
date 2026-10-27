# Box Stove V2 (docs/models/box_stove_v2_plan.md): the forest home's cast-iron box stove rebuilt in Blender and baked.
# User 2026-10-26: the V1 stove (70 x 32 cm, top at 56 cm) is too small and its textures plain; one cell used to the full,
# 96 x 50 cm, two cook lids, the flue collar staying at the cell's middle (the house's pipe column, attic floor and roof
# boot unchanged). exec()'d by build.py; builds the parts in a fresh scene, returns nothing.
#
# Frame: the AFL cabin piece frame, facing north: x along the stove (the loading door at the x 0 end), y up, z from the
# room (front, z 0) to the wall (z 1); block units (1 = 1 m). P(x, y, z) turns it into Blender's (x, -z, y).
# Parts are named <group>:<what>; groups: body (chunk-drawn cabin piece), door (the animated door mesh, hinge on the
# wall side), inside (firebrick and ash, part of the body piece).
import bpy, bmesh, math
from mathutils import Vector, Matrix

def P(x, y, z):
    return Vector((x, -z, y))

BODY = dict(x0=0.06, x1=0.96, z0=0.13, z1=0.63, y0=0.19, y1=0.62, top=0.655)
T = 0.012                                    # wall
LIDS = [(0.20, 0.38), (0.80, 0.38)]          # cook lid centres (x, z)
LID_R, LID_TOP = 0.110, 0.661                # a pot stands on the lid top
FLUE = (0.5, 0.5); PIPE_R = 0.0755
OPEN = dict(y0=0.28, y1=0.54, z0=0.24, z1=0.52)     # loading opening in the x0 end wall
DOOR = dict(y0=0.26, y1=0.56, z0=0.22, z1=0.54, x0=0.044, x1=0.056)     # closes onto the opening's cast lip
HINGE = (0.050, 0.555)                        # hinge axis (x, z), vertical

COL = bpy.context.scene.collection
PARTS = []

def link(name, me, mat):
    ob = bpy.data.objects.new(name, me); COL.objects.link(ob)
    me.materials.append(bpy.data.materials[mat] if isinstance(mat, str) else mat)
    PARTS.append(ob); return ob

def bevel(ob, w, seg=2, angle=40):
    m = ob.modifiers.new("bevel", "BEVEL"); m.width = w; m.segments = seg; m.limit_method = "ANGLE"; m.angle_limit = math.radians(angle)
    m.harden_normals = False; m.miter_outer = "MITER_ARC"
    return ob

def box(name, lo, hi, mat, bev=0.003, seg=2):
    bm = bmesh.new(); bmesh.ops.create_cube(bm, size=1.0)
    a, b = P(*lo), P(*hi); c = (a + b) / 2; s = Vector([abs(b[i] - a[i]) for i in range(3)])
    for v in bm.verts: v.co = Vector([c[i] + v.co[i] * s[i] for i in range(3)])
    me = bpy.data.meshes.new(name); bm.to_mesh(me); bm.free()
    ob = link(name, me, mat)
    if bev: bevel(ob, min(bev, min(s) * 0.45), seg)
    return ob

def cyl(name, axis, c, a0, a1, r, mat, seg=32, bev=0.0, r1=None, caps=True, phase=0.0):
    """A cylinder along MC axis 'x' | 'y' | 'z' through c (the other two coordinates), from a0 to a1; r1 tapers it."""
    bm = bmesh.new(); r1 = r if r1 is None else r1
    rings = []
    for t, rr in ((a0, r), (a1, r1)):
        ring = []
        for i in range(seg):
            q = 2 * math.pi * i / seg + math.radians(phase); u, w = rr * math.cos(q), rr * math.sin(q)
            p = {"x": (t, c[0] + u, c[1] + w), "y": (c[0] + u, t, c[1] + w), "z": (c[0] + u, c[1] + w, t)}[axis]
            ring.append(bm.verts.new(P(*p)))
        rings.append(ring)
    for i in range(seg):
        j = (i + 1) % seg; bm.faces.new((rings[0][i], rings[0][j], rings[1][j], rings[1][i]))
    if caps:
        bm.faces.new(rings[0]); bm.faces.new(list(reversed(rings[1])))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    me = bpy.data.meshes.new(name); bm.to_mesh(me); bm.free()
    ob = link(name, me, mat)
    if bev: bevel(ob, bev, 2)
    return ob

def loft(name, rings, mat, caps=True):
    """rings: [[(x, y, z) MC] * n], all the same n, bridged in order, end caps filled."""
    bm = bmesh.new(); R = [[bm.verts.new(P(*p)) for p in ring] for ring in rings]; n = len(rings[0])
    for k in range(len(R) - 1):
        for i in range(n):
            j = (i + 1) % n; bm.faces.new((R[k][i], R[k][j], R[k + 1][j], R[k + 1][i]))
    if caps: bm.faces.new(R[0]); bm.faces.new(list(reversed(R[-1])))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    me = bpy.data.meshes.new(name); bm.to_mesh(me); bm.free()
    return link(name, me, mat)

def cut(ob, cutter, solver="EXACT"):
    m = ob.modifiers.new("cut_" + cutter.name, "BOOLEAN"); m.operation = "DIFFERENCE"; m.object = cutter; m.solver = solver
    cutter.hide_render = True; cutter.hide_viewport = True
    if cutter in PARTS: PARTS.remove(cutter)
    # booleans first, then the bevel
    ob.modifiers.move(ob.modifiers.find(m.name), 0)
    return ob

def coil(name, axis, c, a0, a1, mat):
    """Coil-spring handle: a wire helix round a core, along an MC axis."""
    turns = max(4, round((a1 - a0) / 0.0062)); pts = []; rr = 0.0088; wire = 0.0019
    for k in range(turns * 12 + 1):
        t = k / (turns * 12); q = 2 * math.pi * turns * t; a = a0 + (a1 - a0) * t
        u, w = rr * math.cos(q), rr * math.sin(q)
        pts.append({"x": (a, c[0] + u, c[1] + w), "y": (c[0] + u, a, c[1] + w), "z": (c[0] + u, c[1] + w, a)}[axis])
    cu = bpy.data.curves.new(name, "CURVE"); cu.dimensions = "3D"; cu.bevel_depth = wire; cu.bevel_resolution = 2
    sp = cu.splines.new("POLY"); sp.points.add(len(pts) - 1)
    for p, q in zip(sp.points, pts): v = P(*q); p.co = (v.x, v.y, v.z, 1)
    ob = bpy.data.objects.new(name, cu); COL.objects.link(ob); cu.materials.append(bpy.data.materials[mat]); PARTS.append(ob)
    cyl(name + "_core", axis, c, a0 - 0.002, a1 + 0.002, 0.0066, mat, 16)
    return ob

# ---------------------------------------------------------------- materials (procedural, baked by bake.py)
def nodes(m):
    m.use_nodes = True; nt = m.node_tree; return nt, nt.nodes, nt.links

def new_mat(name):
    m = bpy.data.materials.get(name) or bpy.data.materials.new(name); return m

def iron(name, base=(0.020, 0.0195, 0.019), edge=(0.072, 0.070, 0.067), rough=0.60, rough_edge=0.42, rust=0.0, polish=0.0, heat=0.0):
    """Cast iron, stove-blacked: a fine casting grain in the bump, a faint mottle, edges worn lighter and smoother (a Bevel
    node mask), optional rust freckles low down, a polished top where pots slide, a faint heat tint."""
    m = new_mat(name); nt, N, L = nodes(m); b = N.get("Principled BSDF")
    co = N.new("ShaderNodeTexCoord")
    grain = N.new("ShaderNodeTexNoise"); grain.inputs["Scale"].default_value = 170; grain.inputs["Detail"].default_value = 6; grain.inputs["Roughness"].default_value = 0.6
    L.new(co.outputs["Object"], grain.inputs["Vector"])
    mott = N.new("ShaderNodeTexNoise"); mott.inputs["Scale"].default_value = 9; mott.inputs["Detail"].default_value = 3
    L.new(co.outputs["Object"], mott.inputs["Vector"])
    # edge mask: convex edges see little of the inside of the casting (inside AO over a few mm)
    ao = N.new("ShaderNodeAmbientOcclusion"); ao.inside = True; ao.only_local = True; ao.samples = 16; ao.inputs["Distance"].default_value = 0.0045
    em = N.new("ShaderNodeMapRange"); em.inputs["From Min"].default_value = 0.97; em.inputs["From Max"].default_value = 0.55
    L.new(ao.outputs["AO"], em.inputs["Value"])
    # wear breaks up along the edge
    wn = N.new("ShaderNodeTexNoise"); wn.inputs["Scale"].default_value = 60; L.new(co.outputs["Object"], wn.inputs["Vector"])
    wm = N.new("ShaderNodeMath"); wm.operation = "MULTIPLY"; L.new(em.outputs[0], wm.inputs[0])
    wr = N.new("ShaderNodeMapRange"); wr.inputs["From Min"].default_value = 0.35; wr.inputs["From Max"].default_value = 0.6; L.new(wn.outputs["Fac"], wr.inputs["Value"]); L.new(wr.outputs[0], wm.inputs[1])
    # base colour: base x mottle, mixed toward the edge colour by the wear mask
    mr = N.new("ShaderNodeMapRange"); mr.inputs["To Min"].default_value = 0.88; mr.inputs["To Max"].default_value = 1.12; L.new(mott.outputs["Fac"], mr.inputs["Value"])
    bc = N.new("ShaderNodeRGB"); bc.outputs[0].default_value = (*base, 1)
    mul = N.new("ShaderNodeMix"); mul.data_type = "RGBA"; mul.blend_type = "MULTIPLY"; mul.inputs["Factor"].default_value = 1
    L.new(bc.outputs[0], mul.inputs[6]); cm = N.new("ShaderNodeCombineColor"); [L.new(mr.outputs[0], cm.inputs[i]) for i in range(3)]; L.new(cm.outputs[0], mul.inputs[7])
    ec = N.new("ShaderNodeRGB"); ec.outputs[0].default_value = (*edge, 1)
    mix = N.new("ShaderNodeMix"); mix.data_type = "RGBA"; L.new(wm.outputs[0], mix.inputs["Factor"]); L.new(mul.outputs[2], mix.inputs[6]); L.new(ec.outputs[0], mix.inputs[7])
    col = mix.outputs[2]
    rgh = N.new("ShaderNodeMix"); rgh.data_type = "FLOAT"; L.new(wm.outputs[0], rgh.inputs["Factor"]); rgh.inputs[2].default_value = rough; rgh.inputs[3].default_value = rough_edge
    rough_out = rgh.outputs[0]
    if polish:   # the top plate: smoother and a touch lighter where pots are slid (noise patches)
        pn = N.new("ShaderNodeTexNoise"); pn.inputs["Scale"].default_value = 7; L.new(co.outputs["Object"], pn.inputs["Vector"])
        pr = N.new("ShaderNodeMapRange"); pr.inputs["From Min"].default_value = 0.45; pr.inputs["From Max"].default_value = 0.7; pr.inputs["To Max"].default_value = polish
        L.new(pn.outputs["Fac"], pr.inputs["Value"])
        r2 = N.new("ShaderNodeMath"); r2.operation = "SUBTRACT"; L.new(rough_out, r2.inputs[0]); L.new(pr.outputs[0], r2.inputs[1]); rough_out = r2.outputs[0]
        c2 = N.new("ShaderNodeMix"); c2.data_type = "RGBA"; L.new(pr.outputs[0], c2.inputs["Factor"]); L.new(col, c2.inputs[6]); c2.inputs[7].default_value = (*[v * 1.6 for v in base], 1); col = c2.outputs[2]
    if heat:     # heat tint: a faint brown-blue cast in patches
        hn = N.new("ShaderNodeTexNoise"); hn.inputs["Scale"].default_value = 3.5; L.new(co.outputs["Object"], hn.inputs["Vector"])
        hr = N.new("ShaderNodeMapRange"); hr.inputs["From Min"].default_value = 0.5; hr.inputs["From Max"].default_value = 0.75; hr.inputs["To Max"].default_value = heat
        L.new(hn.outputs["Fac"], hr.inputs["Value"])
        c3 = N.new("ShaderNodeMix"); c3.data_type = "RGBA"; L.new(hr.outputs[0], c3.inputs["Factor"]); L.new(col, c3.inputs[6]); c3.inputs[7].default_value = (0.040, 0.028, 0.022, 1); col = c3.outputs[2]
    if rust:     # rust freckles, more toward the floor (object z = MC y)
        rn = N.new("ShaderNodeTexNoise"); rn.inputs["Scale"].default_value = 38; rn.inputs["Detail"].default_value = 6; L.new(co.outputs["Object"], rn.inputs["Vector"])
        sep = N.new("ShaderNodeSeparateXYZ"); L.new(co.outputs["Object"], sep.inputs[0])
        low = N.new("ShaderNodeMapRange"); low.inputs["From Min"].default_value = 0.35; low.inputs["From Max"].default_value = 0.0; L.new(sep.outputs["Z"], low.inputs["Value"])
        th = N.new("ShaderNodeMapRange"); th.inputs["From Min"].default_value = 0.62; th.inputs["From Max"].default_value = 0.7; L.new(rn.outputs["Fac"], th.inputs["Value"])
        rm = N.new("ShaderNodeMath"); rm.operation = "MULTIPLY"; L.new(th.outputs[0], rm.inputs[0]); L.new(low.outputs[0], rm.inputs[1])
        rk = N.new("ShaderNodeMath"); rk.operation = "MULTIPLY"; rk.inputs[1].default_value = rust; L.new(rm.outputs[0], rk.inputs[0])
        c4 = N.new("ShaderNodeMix"); c4.data_type = "RGBA"; L.new(rk.outputs[0], c4.inputs["Factor"]); L.new(col, c4.inputs[6]); c4.inputs[7].default_value = (0.085, 0.032, 0.014, 1); col = c4.outputs[2]
        r4 = N.new("ShaderNodeMix"); r4.data_type = "FLOAT"; L.new(rk.outputs[0], r4.inputs["Factor"]); L.new(rough_out, r4.inputs[2]); r4.inputs[3].default_value = 0.85; rough_out = r4.outputs[0]
    L.new(col, b.inputs["Base Color"]); L.new(rough_out, b.inputs["Roughness"]); b.inputs["Metallic"].default_value = 0.0
    bump = N.new("ShaderNodeBump"); bump.inputs["Strength"].default_value = 0.45; bump.inputs["Distance"].default_value = 0.0008
    L.new(grain.outputs["Fac"], bump.inputs["Height"]); L.new(bump.outputs["Normal"], b.inputs["Normal"])
    m["afl_f0"] = 10; m["afl_metal"] = 0
    return m

def plain(name, color, rough, metal=0, f0=10, bump=0.0, scale=200, tint=0.0):
    m = new_mat(name); nt, N, L = nodes(m); b = N.get("Principled BSDF")
    co = N.new("ShaderNodeTexCoord")
    n = N.new("ShaderNodeTexNoise"); n.inputs["Scale"].default_value = scale; n.inputs["Detail"].default_value = 5; L.new(co.outputs["Object"], n.inputs["Vector"])
    if tint:
        mr = N.new("ShaderNodeMapRange"); mr.inputs["To Min"].default_value = 1 - tint; mr.inputs["To Max"].default_value = 1 + tint; L.new(n.outputs["Fac"], mr.inputs["Value"])
        cm = N.new("ShaderNodeCombineColor"); [L.new(mr.outputs[0], cm.inputs[i]) for i in range(3)]
        mx = N.new("ShaderNodeMix"); mx.data_type = "RGBA"; mx.blend_type = "MULTIPLY"; mx.inputs["Factor"].default_value = 1; mx.inputs[6].default_value = (*color, 1)
        L.new(cm.outputs[0], mx.inputs[7]); L.new(mx.outputs[2], b.inputs["Base Color"])
    else:
        b.inputs["Base Color"].default_value = (*color, 1)
    b.inputs["Roughness"].default_value = rough; b.inputs["Metallic"].default_value = metal
    if bump:
        bp = N.new("ShaderNodeBump"); bp.inputs["Strength"].default_value = bump; bp.inputs["Distance"].default_value = 0.001
        L.new(n.outputs["Fac"], bp.inputs["Height"]); L.new(bp.outputs["Normal"], b.inputs["Normal"])
    m["afl_f0"] = f0; m["afl_metal"] = metal
    return m

def firebrick(name, base):
    """Buff firebrick: iron speckle, a little colour cloud, sooted toward the top of the lining (object z = MC y)."""
    m = new_mat(name); nt, N, L = nodes(m); b = N.get("Principled BSDF")
    co = N.new("ShaderNodeTexCoord")
    cl = N.new("ShaderNodeTexNoise"); cl.inputs["Scale"].default_value = 18; cl.inputs["Detail"].default_value = 3; L.new(co.outputs["Object"], cl.inputs["Vector"])
    cr = N.new("ShaderNodeMapRange"); cr.inputs["To Min"].default_value = 0.85; cr.inputs["To Max"].default_value = 1.12; L.new(cl.outputs["Fac"], cr.inputs["Value"])
    cm = N.new("ShaderNodeCombineColor"); [L.new(cr.outputs[0], cm.inputs[i]) for i in range(3)]
    mul = N.new("ShaderNodeMix"); mul.data_type = "RGBA"; mul.blend_type = "MULTIPLY"; mul.inputs["Factor"].default_value = 1; mul.inputs[6].default_value = (*base, 1); L.new(cm.outputs[0], mul.inputs[7])
    sp = N.new("ShaderNodeTexNoise"); sp.inputs["Scale"].default_value = 520; sp.inputs["Detail"].default_value = 2; L.new(co.outputs["Object"], sp.inputs["Vector"])
    spr = N.new("ShaderNodeMapRange"); spr.inputs["From Min"].default_value = 0.66; spr.inputs["From Max"].default_value = 0.72; L.new(sp.outputs["Fac"], spr.inputs["Value"])
    spx = N.new("ShaderNodeMix"); spx.data_type = "RGBA"; L.new(spr.outputs[0], spx.inputs["Factor"]); L.new(mul.outputs[2], spx.inputs[6]); spx.inputs[7].default_value = (0.06, 0.035, 0.02, 1)
    sep = N.new("ShaderNodeSeparateXYZ"); L.new(co.outputs["Object"], sep.inputs[0])
    hz = N.new("ShaderNodeMapRange"); hz.inputs["From Min"].default_value = 0.27; hz.inputs["From Max"].default_value = 0.42; hz.inputs["To Max"].default_value = 0.8; L.new(sep.outputs["Z"], hz.inputs["Value"])
    sn = N.new("ShaderNodeTexNoise"); sn.inputs["Scale"].default_value = 11; sn.inputs["Detail"].default_value = 4; L.new(co.outputs["Object"], sn.inputs["Vector"])
    snr = N.new("ShaderNodeMapRange"); snr.inputs["From Min"].default_value = 0.35; snr.inputs["From Max"].default_value = 0.65; snr.inputs["To Min"].default_value = 0.5; L.new(sn.outputs["Fac"], snr.inputs["Value"])
    sk = N.new("ShaderNodeMath"); sk.operation = "MULTIPLY"; sk.use_clamp = True; L.new(hz.outputs[0], sk.inputs[0]); L.new(snr.outputs[0], sk.inputs[1])
    mx = N.new("ShaderNodeMix"); mx.data_type = "RGBA"; L.new(sk.outputs[0], mx.inputs["Factor"]); L.new(spx.outputs[2], mx.inputs[6]); mx.inputs[7].default_value = (0.018, 0.015, 0.012, 1)
    L.new(mx.outputs[2], b.inputs["Base Color"]); b.inputs["Roughness"].default_value = 0.93
    bp = N.new("ShaderNodeBump"); bp.inputs["Strength"].default_value = 0.35; bp.inputs["Distance"].default_value = 0.0008; L.new(sp.outputs["Fac"], bp.inputs["Height"]); L.new(bp.outputs["Normal"], b.inputs["Normal"])
    m["afl_f0"] = 10; m["afl_metal"] = 0
    return m

iron("st_iron", rust=0.5, heat=0.35)
iron("st_top", polish=0.10, heat=0.5)
iron("st_leg", rust=0.9)
iron("st_door", heat=0.3)
plain("st_nickel", (0.36, 0.35, 0.33), 0.38, metal=1, f0=255, bump=0.05, scale=300, tint=0.06)
plain("st_pipe", (0.022, 0.022, 0.023), 0.5, bump=0.06, scale=300, tint=0.08)     # stove-black painted steel
plain("st_ash", (0.15, 0.145, 0.135), 0.98, bump=0.6, scale=90, tint=0.35)      # wood ash with fines of charcoal
plain("st_inner", (0.012, 0.011, 0.010), 0.9, bump=0.2, scale=120, tint=0.2)      # soot-black iron inside
for k, c in enumerate(((0.30, 0.20, 0.105), (0.27, 0.18, 0.095), (0.32, 0.225, 0.125))):
    firebrick(f"st_brick{k}", c)
plain("st_char", (0.010, 0.009, 0.009), 0.55, bump=0.9, scale=380, tint=0.3)      # charcoal

# ---------------------------------------------------------------- body
X0, X1, Z0, Z1, Y0, Y1, TOP = (BODY[k] for k in ("x0", "x1", "z0", "z1", "y0", "y1", "top"))
box("body:front", (X0, Y0, Z0), (X1, Y1, Z0 + T), "st_iron", 0.0025)
box("body:back", (X0, Y0, Z1 - T), (X1, Y1, Z1), "st_iron", 0.0025)
box("body:far_end", (X1 - T, Y0, Z0 + T), (X1, Y1, Z1 - T), "st_iron", 0.0025)
o = OPEN
# the door end: one plate, the loading opening cut through it, a cast lip round the opening for the door to close on
dend = box("body:door_end", (X0, Y0, Z0 + T), (X0 + T, Y1, Z1 - T), "st_iron", 0.002)
cut(dend, box("cut_opening", (X0 - 0.01, o["y0"], o["z0"]), (X0 + T + 0.01, o["y1"], o["z1"]), "st_iron", 0))
LW, LP = 0.016, 0.004
for k, (a0, a1, b0, b1) in enumerate(((o["z0"] - LW, o["z1"] + LW, o["y0"] - LW, o["y0"]), (o["z0"] - LW, o["z1"] + LW, o["y1"], o["y1"] + LW),
                                       (o["z0"] - LW, o["z0"], o["y0"], o["y1"]), (o["z1"], o["z1"] + LW, o["y0"], o["y1"]))):
    box(f"body:lip{k}", (X0 - LP, b0, a0), (X0 + 0.001, b1, a1), "st_iron", 0.0018, 2)
box("body:bottom", (X0 - 0.008, Y0 - 0.02, Z0 - 0.008), (X1 + 0.008, Y0, Z1 + 0.008), "st_iron", 0.005, 3)
top = box("body:top", (X0 - 0.016, Y1, Z0 - 0.016), (X1 + 0.016, TOP, Z1 + 0.016), "st_top", 0.009, 4)
for i, (x, z) in enumerate(LIDS):
    cut(top, cyl(f"cut_lid{i}", "y", (x, z), Y1 - 0.01, TOP + 0.01, LID_R + 0.003, "st_top", 64))
cut(top, cyl("cut_flue", "y", FLUE, Y1 - 0.01, TOP + 0.01, PIPE_R, "st_top", 48))
# corner beads
for (x, z) in ((X0, Z0), (X1, Z0), (X0, Z1), (X1, Z1)):
    cyl(f"body:bead_{x}_{z}", "y", (x, z), Y0, Y1, 0.0095, "st_iron", 16)
# one panel per face, its border a rounded cast bead (not a cabinet frame); a molded plinth band round the foot and a
# bead under the top plate's overhang
BW, BP = 0.009, 0.0035
def bead_rect_z(tag, zf, sgn, u0, u1, v0, v1):
    f0, f1 = (zf - BP, zf) if sgn < 0 else (zf, zf + BP)
    for k, (a0, a1, b0, b1) in enumerate(((u0, u1, v0, v0 + BW), (u0, u1, v1 - BW, v1), (u0, u0 + BW, v0 + BW, v1 - BW), (u1 - BW, u1, v0 + BW, v1 - BW))):
        box(f"body:bead_{tag}{k}", (a0, b0, f0), (a1, b1, f1), "st_iron", 0.0032, 3)
def bead_rect_x(tag, xf, sgn, u0, u1, v0, v1):
    f0, f1 = (xf - BP, xf) if sgn < 0 else (xf, xf + BP)
    for k, (a0, a1, b0, b1) in enumerate(((u0, u1, v0, v0 + BW), (u0, u1, v1 - BW, v1), (u0, u0 + BW, v0 + BW, v1 - BW), (u1 - BW, u1, v0 + BW, v1 - BW))):
        box(f"body:bead_{tag}{k}", (f0, b0, a0), (f1, b1, a1), "st_iron", 0.0032, 3)
PL = 0.045                                    # plinth band height
bead_rect_z("front", Z0, -1, X0 + 0.04, X1 - 0.04, Y0 + PL + 0.03, Y1 - 0.055)
bead_rect_z("back", Z1, 1, X0 + 0.04, X1 - 0.04, Y0 + PL + 0.03, Y1 - 0.055)
bead_rect_x("end", X1, 1, Z0 + 0.04, Z1 - 0.04, Y0 + PL + 0.03, Y1 - 0.055)
def band(tag, y0, y1, out, bev):
    """A band round the outside only (four bars sunk 2 mm into the walls), never a solid slab through the firebox."""
    a, b = out, 0.002
    box(f"body:{tag}_front", (X0 - a, y0, Z0 - a), (X1 + a, y1, Z0 + b), "st_iron", bev, 2)
    box(f"body:{tag}_back", (X0 - a, y0, Z1 - b), (X1 + a, y1, Z1 + a), "st_iron", bev, 2)
    box(f"body:{tag}_door_end", (X0 - a, y0, Z0 + b), (X0 + b, y1, Z1 - b), "st_iron", bev, 2)
    box(f"body:{tag}_far_end", (X1 - b, y0, Z0 + b), (X1 + a, y1, Z1 - b), "st_iron", bev, 2)
band("plinth", Y0, Y0 + PL, 0.007, 0.005)
band("top_band", Y1 - 0.028, Y1, 0.005, 0.004)
# bolt heads where the panels are bolted to the corner posts
for x in (X0 + 0.016, X1 - 0.016):
    for y in (Y0 + PL + 0.014, (Y0 + PL + Y1 - 0.028) / 2, Y1 - 0.042):
        b = cyl(f"body:bolt_{x}_{y}", "z", (x, y), Z0 - 0.0045, Z0, 0.0065, "st_iron", 12, r1=0.0045)
# cook lids: proud of the plate, a lifter notch with its bar
for i, (x, z) in enumerate(LIDS):
    lid = cyl(f"body:lid{i}", "y", (x, z), TOP - 0.012, LID_TOP, LID_R, "st_top", 64, bev=0.004)
    notch = box(f"cut_notch{i}", (x + LID_R - 0.036, LID_TOP - 0.008, z - 0.011), (x + LID_R - 0.012, LID_TOP + 0.01, z + 0.011), "st_top", 0)
    cut(lid, notch)
    box(f"body:lid{i}_bar", (x + LID_R - 0.032, LID_TOP - 0.0075, z - 0.011), (x + LID_R - 0.026, LID_TOP - 0.0025, z + 0.011), "st_top", 0.0012)
# flue collar, the pipe stub to the cell top, the damper rod out toward the room with its coil
collar = cyl("body:collar", "y", FLUE, TOP - 0.004, TOP + 0.038, 0.094, "st_iron", 48, bev=0.004)
cut(collar, cyl("cut_collar", "y", FLUE, TOP - 0.01, TOP + 0.05, PIPE_R + 0.0015, "st_iron", 48))
# the stub is the house pipe's 16-gon (cabin_mesh/pipe/stove_pipe.json: flats at 0.075, a flat facing each axis) so it
# meets the pipe cell above without a step
PIPE16 = 0.075 / math.cos(math.radians(11.25))
pipe = cyl("body:pipe", "y", FLUE, TOP, 1.0, PIPE16, "st_pipe", 16, caps=False, phase=11.25)
cyl("body:pipe_bead", "y", FLUE, 0.80, 0.806, PIPE16 + 0.002, "st_pipe", 16, phase=11.25)
# the stub's open top (user 2026-10-26: a stove with no pipe on it showed through its one-sided wall from above): the wall's
# sooted inside down to the collar, the cut rim, the damper plate on its rod (half open) and a dark floor at the collar
PIN = PIPE16 - 0.0012
def ring_faces(name, y, r0, r1, mat, up=True):
    bm = bmesh.new()
    rings = [[bm.verts.new(P(FLUE[0] + r * math.cos(2 * math.pi * i / 16 + math.radians(11.25)), y,
                             FLUE[1] + r * math.sin(2 * math.pi * i / 16 + math.radians(11.25)))) for i in range(16)] for r in (r0, r1)]
    if r0 == 0: bm.faces.new(rings[1])
    else:
        for i in range(16):
            j = (i + 1) % 16; bm.faces.new((rings[0][i], rings[0][j], rings[1][j], rings[1][i]))
    bm.normal_update()
    for f in bm.faces:
        if (f.normal.z > 0) != up: f.normal_flip()
    me = bpy.data.meshes.new(name); bm.to_mesh(me); bm.free()
    return link(name, me, mat)
inner = cyl("body:pipe_inner", "y", FLUE, TOP + 0.02, 1.0, PIN, "st_inner", 16, caps=False, phase=11.25)
bm = bmesh.new(); bm.from_mesh(inner.data); bmesh.ops.reverse_faces(bm, faces=bm.faces[:]); bm.to_mesh(inner.data); bm.free()
ring_faces("body:pipe_rim", 1.0, PIN, PIPE16, "st_pipe")
ring_faces("body:pipe_floor", TOP + 0.02, 0, PIN, "st_inner")
damper = cyl("body:damper_plate", "y", FLUE, 0.86 - 0.0015, 0.86 + 0.0015, PIN - 0.004, "st_inner", 24)
c0 = P(FLUE[0], 0.86, FLUE[1])
damper.data.transform(Matrix.Translation(c0) @ Matrix.Rotation(math.radians(40), 4, "Y") @ Matrix.Translation(-c0))
cyl("body:damper_rod", "z", (FLUE[0], 0.86), FLUE[1] - PIPE_R - 0.06, FLUE[1] + PIPE_R + 0.004, 0.0035, "st_nickel", 12)
coil("body:damper_coil", "z", (FLUE[0], 0.86), FLUE[1] - PIPE_R - 0.058, FLUE[1] - PIPE_R - 0.014, "st_nickel")
# legs: cast, tapered, splayed out, a pad foot
def leg(tag, x, z, sx, sz):
    rings = []
    for y, s, k in ((Y0 - 0.02, 1.0, 0.0), (0.12, 0.86, 0.006), (0.06, 0.74, 0.016), (0.022, 0.70, 0.026)):
        ring = []
        for i in range(16):
            q = 2 * math.pi * i / 16; c, w = math.cos(q), math.sin(q)
            ring.append((x + sx * k + 0.024 * s * math.copysign(abs(c) ** 0.55, c), y, z + sz * k + 0.024 * s * math.copysign(abs(w) ** 0.55, w)))
        rings.append(ring)
    loft(f"body:leg_{tag}", rings, "st_leg")
    fx, fz = x + sx * 0.03, z + sz * 0.03
    cyl(f"body:foot_{tag}", "y", (fx, fz), 0.0, 0.024, 0.032, "st_leg", 24, bev=0.006, r1=0.027)
for tag, (x, z, sx, sz) in {"a": (X0 + 0.05, Z0 + 0.045, -1, -1), "b": (X1 - 0.05, Z0 + 0.045, 1, -1), "c": (X0 + 0.05, Z1 - 0.045, -1, 1), "d": (X1 - 0.05, Z1 - 0.045, 1, 1)}.items():
    leg(tag, x, z, sx, sz)
# ash apron under the door, hinge lugs and the latch catch on the door end
box("body:apron", (0.004, Y0 - 0.004, DOOR["z0"] - 0.01), (X0, Y0 + 0.012, DOOR["z1"] + 0.01), "st_iron", 0.003)
box("body:apron_lip", (0.004, Y0 + 0.012, DOOR["z0"] - 0.01), (0.012, Y0 + 0.03, DOOR["z1"] + 0.01), "st_iron", 0.003)
for y in (DOOR["y0"] + 0.05, DOOR["y1"] - 0.05):
    box(f"body:hinge_lug_{y}", (HINGE[0] - 0.006, y - 0.012, HINGE[1] - 0.004), (X0 + 0.002, y + 0.012, HINGE[1] + 0.03), "st_iron", 0.003)
    cyl(f"body:hinge_pin_{y}", "y", HINGE, y - 0.03, y + 0.03, 0.0045, "st_nickel", 12)
cyl("body:keeper", "x", (0.415, DOOR["z0"] - 0.012), X0 - 0.013, X0, 0.0065, "st_iron", 16, bev=0.0015)   # the latch keeper
# inside: firebrick (23 x 11.4 cm faces, two courses, joints staggered) on the long walls and the far end, an ash bed
# with charcoal, soot-black iron above the lining
import random
rnd = random.Random(7)
J, BH, BT, BL = 0.003, 0.114, 0.03, 0.23
def brick(name, lo, hi):
    box(name, lo, hi, f"st_brick{rnd.randrange(3)}", 0.0035, 2)
xa, xb = X0 + T, X1 - T - BT
for c in range(2):
    y0 = Y0 + c * BH
    for side, (za, zb) in (("front", (Z0 + T, Z0 + T + BT)), ("back", (Z1 - T - BT, Z1 - T))):
        x = xa - (BL / 2 if (c + (side == "back")) % 2 else 0)
        k = 0
        while x < xb - 0.03:
            a0, a1 = max(x, xa), min(x + BL, xb)
            if a1 - a0 > 0.03: brick(f"inside:brick_{side}{c}_{k}", (a0 + J / 2, y0 + J / 2, za), (a1 - J / 2, y0 + BH - J / 2, zb))
            x += BL; k += 1
    za, zb = Z0 + T + BT, Z1 - T - BT; w = (zb - za) / 2
    for k in range(2):
        if c % 2: z0, z1 = (za, za + w * 0.5) if k == 0 else (za + w * 0.5, zb)
        else: z0, z1 = za + k * w, za + (k + 1) * w
        brick(f"inside:brick_end{c}_{k}", (X1 - T - BT, y0 + J / 2, z0 + J / 2), (X1 - T, y0 + BH - J / 2, z1 - J / 2))
# ash bed: a lumpy grid dropping to the walls at its edge, a few lumps of charcoal on it
ax0, ax1, az0, az1 = X0 + T, X1 - T - BT, Z0 + T + BT, Z1 - T - BT
blobs = [(rnd.uniform(ax0 + 0.05, ax1 - 0.05), rnd.uniform(az0 + 0.04, az1 - 0.04), rnd.uniform(0.05, 0.11), rnd.uniform(0.004, 0.012)) for _ in range(9)]
def ash_h(x, z):
    h = 0.016 + sum(hh * math.exp(-((x - bx) ** 2 + (z - bz) ** 2) / (r * r)) for bx, bz, r, hh in blobs)
    e = min(x - ax0, ax1 - x, z - az0, az1 - z)
    return Y0 + 0.002 + h * min(1.0, max(e, 0) / 0.035) ** 0.6
NX, NZ = 40, 20
bm = bmesh.new()
V = []
for i in range(NX + 1):
    row = []
    for j in range(NZ + 1):
        x, z = ax0 + (ax1 - ax0) * i / NX, az0 + (az1 - az0) * j / NZ
        row.append(bm.verts.new(P(x, ash_h(x, z), z)))
    V.append(row)
for i in range(NX):
    for j in range(NZ): bm.faces.new((V[i][j], V[i + 1][j], V[i + 1][j + 1], V[i][j + 1]))
bm.normal_update()
for f in bm.faces:
    if f.normal.z < 0: f.normal_flip()
me = bpy.data.meshes.new("inside:ash"); bm.to_mesh(me); bm.free(); link("inside:ash", me, "st_ash")
# charcoal: irregular lumps (a bevelled box, its corners jittered), more toward the far end, sunk into the ash
for k in range(34):
    x = ax0 + (ax1 - ax0) * (1 - rnd.random() ** 1.6) ** 0.8 * 0.94 + 0.02
    z = rnd.uniform(az0 + 0.03, az1 - 0.03)
    s0 = rnd.uniform(0.007, 0.028); sx, sz, sy = s0 * rnd.uniform(1.0, 1.8), s0 * rnd.uniform(0.7, 1.1), s0 * rnd.uniform(0.5, 0.8)
    hy = ash_h(x, z) - sy * rnd.uniform(0.35, 0.6)
    ch = box(f"inside:char{k}", (x - sx / 2, hy, z - sz / 2), (x + sx / 2, hy + sy, z + sz / 2), "st_char", 0, 1)
    for v in ch.data.vertices:
        v.co.x += rnd.uniform(-0.25, 0.25) * sx * 0.5; v.co.y += rnd.uniform(-0.25, 0.25) * sz * 0.5; v.co.z += rnd.uniform(-0.2, 0.2) * sy * 0.5
    bevel(ch, min(sx, sy, sz) * 0.22, 1)
    c0 = P(x, hy, z)
    ch.data.transform(Matrix.Translation(c0) @ Matrix.Rotation(rnd.uniform(0, 3.1), 4, "Z") @ Matrix.Rotation(rnd.uniform(-0.35, 0.35), 4, "X") @ Matrix.Translation(-c0))
box("inside:soot_ceiling", (X0 + T, Y1 - 0.004, Z0 + T), (X1 - T, Y1, Z1 - T), "st_inner", 0)

# ---------------------------------------------------------------- door (its own mesh; turns about HINGE)
d = DOOR
door = box("door:plate", (d["x0"], d["y0"], d["z0"]), (d["x1"], d["y1"], d["z1"]), "st_door", 0.003)
for k, (a0, a1, b0, b1) in enumerate(((d["z0"], d["z1"], d["y0"], d["y0"] + 0.02), (d["z0"], d["z1"], d["y1"] - 0.02, d["y1"]),
                                       (d["z0"], d["z0"] + 0.02, d["y0"] + 0.02, d["y1"] - 0.02), (d["z1"] - 0.02, d["z1"], d["y0"] + 0.02, d["y1"] - 0.02))):
    box(f"door:frame{k}", (d["x0"] - 0.005, b0, a0), (d["x0"], b1, a1), "st_door", 0.002)
# liner plate on the inside face, sized to enter the opening (12 mm clear all round), on four studs
box("door:liner", (d["x1"] - 0.001, o["y0"] + 0.012, o["z0"] + 0.012), (d["x1"] + 0.010, o["y1"] - 0.012, o["z1"] - 0.012), "st_door", 0.003)
for k, (yy, zz) in enumerate(((o["y0"] + 0.035, o["z0"] + 0.035), (o["y0"] + 0.035, o["z1"] - 0.035), (o["y1"] - 0.035, o["z0"] + 0.035), (o["y1"] - 0.035, o["z1"] - 0.035))):
    cyl(f"door:liner_stud{k}", "x", (yy, zz), d["x1"] + 0.010, d["x1"] + 0.015, 0.006, "st_door", 12, bev=0.0015, r1=0.0045)
cy, cz = (d["y0"] + d["y1"]) / 2, (d["z0"] + d["z1"]) / 2
boss = cyl("door:draft_boss", "x", (cy, cz), d["x0"] - 0.006, d["x0"], 0.058, "st_door", 48, bev=0.002)
for k in range(6):   # six sector holes through the boss and the plate
    q = 2 * math.pi * k / 6; hy, hz = cy + 0.034 * math.sin(q), cz + 0.034 * math.cos(q)
    h = cyl(f"cut_hole{k}", "x", (hy, hz), d["x0"] - 0.02, d["x1"] + 0.01, 0.011, "st_door", 16)
    cut(boss, h, "EXACT"); hb = cyl(f"cut_hole{k}b", "x", (hy, hz), d["x0"] - 0.02, d["x1"] + 0.01, 0.011, "st_door", 16); cut(door, hb, "EXACT")
shutter = cyl("door:draft_shutter", "x", (cy, cz), d["x0"] - 0.009, d["x0"] - 0.006, 0.05, "st_door", 48, bev=0.001)
for k in range(6):   # the shutter half open: its holes turned 18 deg
    q = 2 * math.pi * k / 6 + math.radians(18); hy, hz = cy + 0.034 * math.sin(q), cz + 0.034 * math.cos(q)
    cut(shutter, cyl(f"cut_sh{k}", "x", (hy, hz), d["x0"] - 0.02, d["x0"], 0.011, "st_door", 16))
cyl("door:draft_knob", "x", (cy, cz), d["x0"] - 0.022, d["x0"] - 0.009, 0.009, "st_nickel", 16, bev=0.002, r1=0.007)
for y in (d["y0"] + 0.05, d["y1"] - 0.05):   # hinge knuckles round the pin, on straps over the door
    k1 = cyl(f"door:knuckle_{y}", "y", HINGE, y - 0.014, y + 0.014, 0.0085, "st_door", 16, bev=0.0015)
    box(f"door:strap_{y}", (d["x0"] - 0.004, y - 0.011, HINGE[1] - 0.09), (d["x0"] + 0.002, y + 0.011, HINGE[1] - 0.004), "st_door", 0.002)
# latch: a pivot boss on the room edge, the lever out past the edge, a coil grip on it
lz = d["z0"] + 0.045
cyl("door:latch_boss", "x", (0.415, lz), d["x0"] - 0.012, d["x0"], 0.011, "st_door", 16, bev=0.002)
box("door:latch_lever", (d["x0"] - 0.017, 0.408, d["z0"] - 0.03), (d["x0"] - 0.011, 0.422, lz + 0.006), "st_door", 0.002)
coil("door:latch_coil", "z", (d["x0"] - 0.026, 0.415), d["z0"] - 0.03, d["z0"] + 0.025, "st_nickel")
box("door:latch_arm", (d["x0"] - 0.03, 0.409, d["z0"] - 0.036), (d["x0"] - 0.011, 0.421, d["z0"] - 0.028), "st_door", 0.002)

print("stove v2 parts", len(PARTS))
