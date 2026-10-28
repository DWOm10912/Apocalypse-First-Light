# Cooking V1 K0b (docs/gameplay/cooking_v1_plan.md 6, 2026-10-27): the box stove only heats water, in the enamel pail; the
# forest home's old enamelled wood cookstove (tools/stove/cookstove_v1/model.py) does the cooking, one pot at a time; the
# town houses' propane range (tools/stove/propane_range_v1/model.py) in a plain town kitchen; the pots one size up (7 qt
# dutch oven, 12 in skillet); and what the player sees standing at the cookstove (first person, 70 deg vertical FOV like
# the game), before and after the camera eases in (and narrows to 50 deg).
# blender -b --factory-startup --python cooking_k0b_render.py -- [states...] [--quick]
# Reads the generator's --preview output in cooking_k0/ next to this script:
#   node tools/build-equipment-meshes-v1.mjs cast_iron_dutch_oven cast_iron_skillet table_spoon enamel_water_pail --preview design/items/cooking_v1/source/cooking_k0
import bpy, bmesh, json, math, os, random, sys
import numpy as np
from mathutils import Vector, Euler, Matrix

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", "..", ".."))
A = os.path.join(ROOT, "src", "main", "resources", "assets", "apocalypse_firstlight")
SP = os.path.join(HERE, "cooking_k0")
OUT = os.path.join(SP, "renders")
os.makedirs(OUT, exist_ok=True)
argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
QUICK = "--quick" in argv
STATES = [a for a in argv if not a.startswith("--")]

def mc(x, y, z):            # MC block coords (Y up, front at z 0) -> Blender (Z up)
    return Vector((x, -z, y))

# ---------------------------------------------------------------- materials
IMG = {}
def img(path, data=False):
    if path not in IMG:
        im = bpy.data.images.load(path)
        if data: im.colorspace_settings.name = "Non-Color"
        IMG[path] = im
    return IMG[path]

MAT = {}
def tex_mat(name, base):
    """Principled from an AFL LabPBR set: base, base_s (R smoothness, G f0 >= 230 metal), base_n (B AO)."""
    if name in MAT: return MAT[name]
    m = bpy.data.materials.new(name); m.use_nodes = True
    nt = m.node_tree; N = nt.nodes; L = nt.links
    bsdf = next(n for n in N if n.type == "BSDF_PRINCIPLED")
    t = N.new("ShaderNodeTexImage"); t.image = img(base + ".png"); t.interpolation = "Closest"
    col = t.outputs["Color"]
    if os.path.exists(base + "_n.png"):
        tn = N.new("ShaderNodeTexImage"); tn.image = img(base + "_n.png", True); tn.interpolation = "Closest"
        sep = N.new("ShaderNodeSeparateColor"); L.new(tn.outputs["Color"], sep.inputs[0])
        mix = N.new("ShaderNodeMix"); mix.data_type = "RGBA"; mix.blend_type = "MULTIPLY"; mix.inputs["Factor"].default_value = 1.0
        L.new(col, mix.inputs[6]); L.new(sep.outputs[2], mix.inputs[7]); col = mix.outputs[2]
    L.new(col, bsdf.inputs["Base Color"])
    if os.path.exists(base + "_s.png"):
        ts = N.new("ShaderNodeTexImage"); ts.image = img(base + "_s.png", True); ts.interpolation = "Closest"
        sep = N.new("ShaderNodeSeparateColor"); L.new(ts.outputs["Color"], sep.inputs[0])
        inv = N.new("ShaderNodeMath"); inv.operation = "SUBTRACT"; inv.inputs[0].default_value = 1.0; L.new(sep.outputs[0], inv.inputs[1])
        sq = N.new("ShaderNodeMath"); sq.operation = "POWER"; sq.inputs[1].default_value = 2.0; L.new(inv.outputs[0], sq.inputs[0])
        L.new(sq.outputs[0], bsdf.inputs["Roughness"])
        met = N.new("ShaderNodeMath"); met.operation = "GREATER_THAN"; met.inputs[1].default_value = 229.5 / 255; L.new(sep.outputs[1], met.inputs[0])
        L.new(met.outputs[0], bsdf.inputs["Metallic"])
    MAT[name] = m
    return m

def plain_mat(name, color, rough=0.5, metal=0.0, **kw):
    m = bpy.data.materials.new(name); m.use_nodes = True
    b = next(n for n in m.node_tree.nodes if n.type == "BSDF_PRINCIPLED")
    b.inputs["Base Color"].default_value = (*color, 1); b.inputs["Roughness"].default_value = rough; b.inputs["Metallic"].default_value = metal
    for k, v in kw.items(): b.inputs[k].default_value = v
    return m

def tex_path(ref):
    ns, p = ref.split(":")
    return os.path.join(A, "textures", *p.split("/"))

def finish(ob, angle=35, merge=True, recalc=False):
    bm = bmesh.new(); bm.from_mesh(ob.data)
    if merge: bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-6)
    if recalc: bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    for f in bm.faces: f.smooth = True
    for e in bm.edges:
        e.smooth = not (len(e.link_faces) != 2 or e.calc_face_angle(0) > math.radians(angle))
    bm.to_mesh(ob.data); bm.free()

def cabin(rel, offset=(0, 0, 0), name=None, angle=35):
    j = json.load(open(os.path.join(A, "cabin_mesh", rel)))
    mats = [tex_mat(t.split(":")[1], tex_path(t)) for t in j["textures"]]
    verts, faces, uvs, mi = [], [], [], []
    for q in j["quads"]:
        ids = []
        for k in range(4):
            x, y, z, u, v, nx, ny, nz = q[1 + 8 * k: 9 + 8 * k]
            verts.append(mc(x + offset[0], y + offset[1], z + offset[2])); ids.append(len(verts) - 1); uvs.append((u, 1 - v))
        P_ = [verts[i] for i in ids]; n = (P_[1] - P_[0]).cross(P_[2] - P_[0])
        if n.dot(mc(*q[6:9])) < 0: ids.reverse(); uvs[-4:] = uvs[-4:][::-1]
        faces.append(ids); mi.append(q[0])
    me = bpy.data.meshes.new(name or rel); me.from_pydata(verts, [], faces)
    for m in mats: me.materials.append(m)
    uvl = me.uv_layers.new()
    for p, f in zip(me.polygons, faces):
        p.material_index = mi[p.index]
        for li, vi in zip(p.loop_indices, range(4)): uvl.data[li].uv = uvs[p.index * 4 + vi]
    ob = bpy.data.objects.new(name or rel, me); bpy.context.collection.objects.link(ob)
    finish(ob, angle)
    return ob, j

def bbmodel(item, parts=None, name=None):
    src = json.load(open(os.path.join(SP, item + ".bbmodel")))
    W = src["resolution"]["width"]; H = src["resolution"]["height"]
    m = tex_mat(item, os.path.join(SP, item + "_mesh"))
    obs = {}
    for el in src["elements"]:
        if parts and el["name"] not in parts: continue
        keys = list(el["vertices"]); kid = {k: i for i, k in enumerate(keys)}
        verts = [Vector((p[0], -p[2], p[1])) * 0.01 for p in (el["vertices"][k] for k in keys)]
        faces, fuv = [], []
        for f in el["faces"].values():
            faces.append([kid[k] for k in f["vertices"]]); fuv.append([f["uv"][k] for k in f["vertices"]])
        me = bpy.data.meshes.new(el["name"]); me.from_pydata(verts, [], faces); me.materials.append(m)
        uvl = me.uv_layers.new()
        for p in me.polygons:
            for li, (u, v) in zip(p.loop_indices, fuv[p.index]): uvl.data[li].uv = (u / W, 1 - v / H)
        ob = bpy.data.objects.new((name or item) + ":" + el["name"], me); bpy.context.collection.objects.link(ob)
        finish(ob, 35, merge=False, recalc=True)
        obs[el["name"]] = ob
    return obs

def group(obs, name):
    e = bpy.data.objects.new(name, None); bpy.context.collection.objects.link(e)
    for o in obs.values(): o.parent = e
    return e

def bbox(objs):
    bpy.context.view_layer.update()
    pts = [o.matrix_world @ Vector(c) for o in objs for c in o.bound_box]
    return Vector([min(p[i] for p in pts) for i in range(3)]), Vector([max(p[i] for p in pts) for i in range(3)])

def rest(grp, ref, x, y, z):
    """Move a group so its reference part's plan centre is over (x, z) and its bottom at y (MC); the world point returned."""
    lo, hi = bbox([ref]); c = (lo + hi) / 2; t = mc(x, y, z)
    grp.location += Vector((t.x - c.x, t.y - c.y, t.z - lo.z))
    return Vector((t.x, t.y, t.z))

# ---------------------------------------------------------------- scene
for o in list(bpy.data.objects): bpy.data.objects.remove(o)
sc = bpy.context.scene
try: sc.render.engine = "CYCLES"
except TypeError as e: print(e)
prefs = bpy.context.preferences.addons["cycles"].preferences
for kind in ("OPTIX", "CUDA"):
    try:
        prefs.compute_device_type = kind; prefs.get_devices()
        for d in prefs.devices: d.use = True
        sc.cycles.device = "GPU"; break
    except Exception as e: print("no", kind, e)
sc.cycles.samples = 48 if QUICK else 160
sc.cycles.use_denoising = True
sc.render.resolution_x, sc.render.resolution_y = (640, 480) if QUICK else (1200, 900)
sc.view_settings.view_transform = "AgX"; sc.view_settings.exposure = -0.8
looks = [i.identifier for i in bpy.types.ColorManagedViewSettings.bl_rna.properties["look"].enum_items]
sc.view_settings.look = "AgX - Medium High Contrast" if "AgX - Medium High Contrast" in looks else "None"

# the box stove (cell 0) and, at OX, the cookstove; log walls behind, flagstone hearths, floor boards
OX = 3
stove_body, stove_json = cabin("stove/body.json", name="stove_body")
cabin("stove/door_closed.json", name="stove_door")
cabin("shield/panel_0.json", name="heat_shield")
for cx in (-1, 0, 1, 2, 3, 4):
    for cy in (0, 1, 2):
        cabin("log_wall/run_x.json", (cx, cy, 1), name=f"log_{cx}_{cy}", angle=60)
for cx in (-1, 0, 1, 2, 3, 4):
    for cz in (-1, 0):
        t = (cz % 3) * 3 + (cx % 3)
        m = tex_mat(f"flag{t}", os.path.join(A, "textures", "block", "stove", f"flagstone_top_{t}"))
        me = bpy.data.meshes.new("flag"); me.from_pydata([mc(cx, 0, cz), mc(cx + 1, 0, cz), mc(cx + 1, 0, cz + 1), mc(cx, 0, cz + 1)], [], [[0, 3, 2, 1]])
        me.materials.append(m); uv = me.uv_layers.new()
        for li, c in zip(me.polygons[0].loop_indices, [(0, 1), (0, 0), (1, 0), (1, 1)]): uv.data[li].uv = c
        if me.polygons[0].normal.z < 0: me.flip_normals()
        sc.collection.objects.link(bpy.data.objects.new(f"flag_{cx}_{cz}", me))
bpy.ops.mesh.primitive_plane_add(size=12, location=mc(1.5, -0.001, -2.5)); bpy.context.object.data.materials.append(plain_mat("boards", (0.23, 0.15, 0.09), 0.7))
# the box stove's left cook lid top (from its mesh: the highest point within the lid)
LID = (0.20, 0.38)
box_top = max(q[2 + 8 * k] for q in stove_json["quads"] for k in range(4)
              if math.hypot(q[1 + 8 * k] - LID[0], q[3 + 8 * k] - LID[1]) < 0.09 and q[2 + 8 * k] < 0.8)
print("box stove lid top", box_top)

G = {"__file__": os.path.join(ROOT, "tools", "stove", "cookstove_v1", "model.py")}
exec(compile(open(G["__file__"], encoding="utf-8").read(), G["__file__"], "exec"), G)
for ob in G["PARTS"]: ob.location += mc(OX, 0, 0)
CS_TOP, COOK = G["TOP1"] + 0.003, G["COOK"]
CS_SPOT = (OX + COOK[0], CS_TOP, COOK[1])

# the town kitchen at OX2: a painted drywall wall with a baseboard, sheet vinyl on the floor, the propane range
OX2 = 10
R = {"__file__": os.path.join(ROOT, "tools", "stove", "propane_range_v1", "model.py")}
exec(compile(open(R["__file__"], encoding="utf-8").read(), R["__file__"], "exec"), R)
for ob in R["PARTS"]: ob.location += mc(OX2, 0, 0)
bpy.ops.mesh.primitive_plane_add(size=1, location=mc(OX2 + 0.5, 0.0005, -0.5)); fl = bpy.context.object; fl.scale = (5, 4, 1)
vinyl = plain_mat("vinyl", (0.62, 0.60, 0.55), 0.35)
vn = vinyl.node_tree.nodes; vb = next(n for n in vn if n.type == "BSDF_PRINCIPLED")
vco = vn.new("ShaderNodeTexCoord"); vch = vn.new("ShaderNodeTexChecker"); vch.inputs["Scale"].default_value = 33.33
vch.inputs["Color1"].default_value = (0.62, 0.60, 0.55, 1); vch.inputs["Color2"].default_value = (0.52, 0.50, 0.46, 1)
vinyl.node_tree.links.new(vco.outputs["Generated"], vch.inputs["Vector"]); vinyl.node_tree.links.new(vch.outputs["Color"], vb.inputs["Base Color"])
fl.data.materials.append(vinyl)
bpy.ops.mesh.primitive_plane_add(size=1, location=mc(OX2 + 0.5, 1.25, 1.0)); wl = bpy.context.object; wl.scale = (5, 2.5, 1)
wl.rotation_euler = (math.radians(90), 0, 0); wl.data.materials.append(plain_mat("drywall", (0.66, 0.64, 0.58), 0.8))
bpy.ops.mesh.primitive_cube_add(size=1, location=mc(OX2 + 0.5, 0.05, 0.99)); bsb = bpy.context.object; bsb.scale = (5, 0.016, 0.1)
bsb.data.materials.append(plain_mat("baseboard", (0.72, 0.70, 0.64), 0.4))
RG_SPOT = (OX2 + R["BURNERS"][0][0], R["GRATE_TOP"], R["BURNERS"][0][1])

# ---------------------------------------------------------------- cookware
pail = bbmodel("enamel_water_pail"); PAIL = group(pail, "PAIL")
PAIL_AX = rest(PAIL, pail["body"], LID[0], box_top, LID[1])
oven = bbmodel("cast_iron_dutch_oven"); OVEN = group(oven, "OVEN")
OVEN_AX = rest(OVEN, oven["body"], *CS_SPOT)
skillet = bbmodel("cast_iron_skillet"); SKIL = group(skillet, "SKILLET")
SKIL.rotation_euler.z = math.radians(180)       # the handle toward -x: seen from the front, to the right, over the oven half
SKIL_AX = rest(SKIL, skillet["pan"], *CS_SPOT)
skillet2 = bbmodel("cast_iron_skillet", name="skillet2"); SKIL2 = group(skillet2, "SKILLET2")
SKIL2.rotation_euler.z = math.radians(180)
rest(SKIL2, skillet2["pan"], *RG_SPOT)
spoon = bbmodel("table_spoon"); SPOON = group(spoon, "SPOON")

# ---------------------------------------------------------------- contents (render only)
def cone(name, base, r0, r1, z0, z1, mat, verts=96):
    bpy.ops.mesh.primitive_cone_add(vertices=verts, radius1=r0, radius2=r1, depth=z1 - z0, location=base + Vector((0, 0, (z0 + z1) / 2)))
    o = bpy.context.object; o.name = name; o.data.materials.append(mat); bpy.ops.object.shade_smooth(); return o

water_mat = plain_mat("water", (0.8, 0.85, 0.85), 0.03, **{"Transmission Weight": 1.0, "IOR": 1.333})
wvol = water_mat.node_tree.nodes.new("ShaderNodeVolumeAbsorption"); wvol.inputs["Color"].default_value = (0.75, 0.82, 0.8, 1); wvol.inputs["Density"].default_value = 4.0
water_mat.node_tree.links.new(wvol.outputs[0], next(n for n in water_mat.node_tree.nodes if n.type == "OUTPUT_MATERIAL").inputs["Volume"])
boil_mat = water_mat.copy(); boil_mat.name = "boil_water"
bn = boil_mat.node_tree.nodes; bb = next(n for n in bn if n.type == "BSDF_PRINCIPLED")
bco = bn.new("ShaderNodeTexCoord"); bnoise = bn.new("ShaderNodeTexNoise"); bnoise.inputs["Scale"].default_value = 60.0; bnoise.inputs["Detail"].default_value = 3
bump = bn.new("ShaderNodeBump"); bump.inputs["Strength"].default_value = 0.6; bump.inputs["Distance"].default_value = 0.004
boil_mat.node_tree.links.new(bco.outputs["Object"], bnoise.inputs["Vector"]); boil_mat.node_tree.links.new(bnoise.outputs["Fac"], bump.inputs["Height"])
boil_mat.node_tree.links.new(bump.outputs["Normal"], bb.inputs["Normal"])

# the pail: about 12 L, the water 0.19 m deep in the slightly flared pail (inner radius 13.0 -> 13.8 cm)
PW = 0.19
PAIL_WATER = cone("pail_boil", PAIL_AX, 0.1298, 0.1298 + 0.0083 * PW / 0.19, 0.002, PW, boil_mat)
random.seed(4)
bubble_mat = plain_mat("bubble", (0.95, 0.96, 0.96), 0.05, **{"Transmission Weight": 0.85, "IOR": 1.333})
BUBBLES = []
for i in range(170):
    a = random.random() * 2 * math.pi; r = 0.13 * math.sqrt(random.random()) * (0.55 if i < 100 else 1)
    s = random.uniform(0.0012, 0.0045) if i < 160 else random.uniform(0.006, 0.009)
    bpy.ops.mesh.primitive_uv_sphere_add(radius=s, segments=16, ring_count=8, location=PAIL_AX + Vector((r * math.cos(a), r * math.sin(a), PW - s * 0.2)))
    b = bpy.context.object; b.scale.z = 0.75; b.data.materials.append(bubble_mat); bpy.ops.object.shade_smooth(); BUBBLES.append(b)

def steam(name, base, h0, strength, radius=0.16):
    bpy.ops.mesh.primitive_cylinder_add(vertices=32, radius=radius, depth=0.4, location=base + Vector((0, 0, h0 + 0.2)))
    o = bpy.context.object; o.name = name
    m = bpy.data.materials.new(name); m.use_nodes = True; nt = m.node_tree; N = nt.nodes
    for n in list(N):
        if n.type != "OUTPUT_MATERIAL": N.remove(n)
    out = next(n for n in N if n.type == "OUTPUT_MATERIAL")
    vol = N.new("ShaderNodeVolumePrincipled"); vol.inputs["Color"].default_value = (0.95, 0.95, 0.95, 1)
    co = N.new("ShaderNodeTexCoord")
    noise = N.new("ShaderNodeTexNoise"); noise.inputs["Scale"].default_value = 9.0; noise.inputs["Detail"].default_value = 6
    nt.links.new(co.outputs["Object"], noise.inputs["Vector"])
    ramp = N.new("ShaderNodeValToRGB"); ramp.color_ramp.elements[0].position = 0.5; ramp.color_ramp.elements[1].position = 0.75
    nt.links.new(noise.outputs["Fac"], ramp.inputs[0])
    sepx = N.new("ShaderNodeSeparateXYZ"); nt.links.new(co.outputs["Object"], sepx.inputs[0])
    hz = N.new("ShaderNodeMapRange"); hz.inputs["From Min"].default_value = -0.19; hz.inputs["From Max"].default_value = 0.19
    hz.inputs["To Min"].default_value = 1.0; hz.inputs["To Max"].default_value = 0.0; nt.links.new(sepx.outputs["Z"], hz.inputs[0])
    rad = N.new("ShaderNodeVectorMath"); rad.operation = "LENGTH"
    xy = N.new("ShaderNodeCombineXYZ"); nt.links.new(sepx.outputs["X"], xy.inputs[0]); nt.links.new(sepx.outputs["Y"], xy.inputs[1]); nt.links.new(xy.outputs[0], rad.inputs[0])
    rr = N.new("ShaderNodeMapRange"); rr.inputs["From Min"].default_value = radius * 0.4; rr.inputs["From Max"].default_value = radius * 0.92
    rr.inputs["To Min"].default_value = 1.0; rr.inputs["To Max"].default_value = 0.0; nt.links.new(rad.outputs["Value"], rr.inputs[0])
    m1 = N.new("ShaderNodeMath"); m1.operation = "MULTIPLY"; nt.links.new(ramp.outputs["Color"], m1.inputs[0]); nt.links.new(hz.outputs[0], m1.inputs[1])
    m2 = N.new("ShaderNodeMath"); m2.operation = "MULTIPLY"; nt.links.new(m1.outputs[0], m2.inputs[0]); nt.links.new(rr.outputs[0], m2.inputs[1])
    m3 = N.new("ShaderNodeMath"); m3.operation = "MULTIPLY"; m3.inputs[1].default_value = strength; nt.links.new(m2.outputs[0], m3.inputs[0])
    nt.links.new(m3.outputs[0], vol.inputs["Density"]); nt.links.new(vol.outputs[0], out.inputs["Volume"])
    o.data.materials.append(m); return o
PAIL_STEAM = steam("pail_steam", PAIL_AX, 0.23, 60.0)

# the dutch oven's soup (7 qt, inner radius 15 cm): broth to 9.5 cm, potato, onion, kidney beans
Ri = 0.15
broth = plain_mat("broth", (0.30, 0.16, 0.06), 0.12, **{"Subsurface Weight": 0.3, "Coat Weight": 0.5, "Coat Roughness": 0.05})
SOUP = cone("soup", OVEN_AX, Ri - 0.0004, Ri - 0.0004, 0.006, 0.095, broth)
potato = plain_mat("potato", (0.62, 0.40, 0.12), 0.5, **{"Subsurface Weight": 0.25})
onion = plain_mat("onion", (0.62, 0.48, 0.22), 0.25, **{"Subsurface Weight": 0.6})
bean = plain_mat("bean", (0.30, 0.06, 0.05), 0.3)
PIECES = []
random.seed(11)
for i in range(64):
    a = random.random() * 2 * math.pi; r = (Ri - 0.014) * math.sqrt(random.random())
    kind = i % 3; loc = OVEN_AX + Vector((r * math.cos(a), r * math.sin(a), 0.095 + random.uniform(-0.005, 0.002)))
    if kind == 0:
        bpy.ops.mesh.primitive_cube_add(size=random.uniform(0.014, 0.02), location=loc)
        o = bpy.context.object; o.scale = (random.uniform(0.8, 1.3), random.uniform(0.7, 1.1), random.uniform(0.6, 1.0)); bev = o.modifiers.new("b", "BEVEL"); bev.width = 0.003; bev.segments = 3; o.data.materials.append(potato)
    elif kind == 1:
        bpy.ops.mesh.primitive_cylinder_add(vertices=16, radius=random.uniform(0.008, 0.014), depth=0.002, location=loc)
        o = bpy.context.object; o.data.materials.append(onion); o.scale.x = 0.5
    else:
        bpy.ops.mesh.primitive_uv_sphere_add(radius=0.0078, segments=16, ring_count=8, location=loc)
        o = bpy.context.object; o.scale = (1.0, 0.62, 0.55); o.data.materials.append(bean)
    o.rotation_euler = Euler([random.uniform(0, 6.3) for _ in range(3)]); bpy.ops.object.shade_smooth(); PIECES.append(o)
SOUP_STEAM = steam("soup_steam", OVEN_AX, 0.12, 22.0, 0.18)

# the spoon: its bowl in the soup at the axis, the handle over the rim toward the room's front right, tilted 12 deg
def place_spoon():
    d = (mc(0.84, 0, -0.54) - mc(0, 0, 0)).normalized()
    yaw = math.atan2(d.y, d.x); E = Euler((0, -math.radians(12), yaw), "XYZ")
    bowl = Vector((-0.063039, 0, 0.001))
    SPOON.rotation_euler = E; SPOON.location = OVEN_AX + Vector((0, 0, 0.09)) - E.to_matrix() @ bowl
place_spoon()

# the skillet: an oil film, a ribeye 20 x 13 cm, 2.5 cm thick, seared on top, a fat cap along one side
oil = plain_mat("oil", (0.5, 0.36, 0.12), 0.02, **{"Transmission Weight": 0.6, "IOR": 1.47})
OIL = cone("oil", SKIL_AX, 0.123, 0.123, 0.0062, 0.0068, oil)
steak_m = bpy.data.materials.new("steak"); steak_m.use_nodes = True
sn = steak_m.node_tree.nodes; sl = steak_m.node_tree.links; sb = next(n for n in sn if n.type == "BSDF_PRINCIPLED")
sb.inputs["Roughness"].default_value = 0.42; sb.inputs["Subsurface Weight"].default_value = 0.15; sb.inputs["Coat Weight"].default_value = 0.3
geo = sn.new("ShaderNodeNewGeometry"); sep = sn.new("ShaderNodeSeparateXYZ"); sl.new(geo.outputs["Normal"], sep.inputs[0])
tco = sn.new("ShaderNodeTexCoord"); tsep = sn.new("ShaderNodeSeparateXYZ"); sl.new(tco.outputs["Object"], tsep.inputs[0])
crn = sn.new("ShaderNodeTexNoise"); crn.inputs["Scale"].default_value = 35; crn.inputs["Detail"].default_value = 4; sl.new(tco.outputs["Object"], crn.inputs["Vector"])
crust = sn.new("ShaderNodeValToRGB"); crust.color_ramp.elements[0].color = (0.085, 0.032, 0.011, 1); crust.color_ramp.elements[1].color = (0.27, 0.115, 0.038, 1)
crust.color_ramp.elements[0].position = 0.3; crust.color_ramp.elements[1].position = 0.8; sl.new(crn.outputs["Fac"], crust.inputs[0])
up = sn.new("ShaderNodeMapRange"); up.inputs["From Min"].default_value = 0.5; up.inputs["From Max"].default_value = 0.9; sl.new(sep.outputs["Z"], up.inputs[0])
side = sn.new("ShaderNodeMix"); side.data_type = "RGBA"; side.inputs[6].default_value = (0.46, 0.21, 0.13, 1); sl.new(up.outputs[0], side.inputs["Factor"]); sl.new(crust.outputs["Color"], side.inputs[7])
fat = sn.new("ShaderNodeMapRange"); fat.inputs["From Min"].default_value = 0.068; fat.inputs["From Max"].default_value = 0.078; sl.new(tsep.outputs["Y"], fat.inputs[0])
fatc = sn.new("ShaderNodeMix"); fatc.data_type = "RGBA"; fatc.inputs[7].default_value = (0.70, 0.52, 0.32, 1); sl.new(fat.outputs[0], fatc.inputs["Factor"]); sl.new(side.outputs[2], fatc.inputs[6])
sl.new(fatc.outputs[2], sb.inputs["Base Color"])
def steak_mesh(name):
    bm = bmesh.new(); pts = []
    random.seed(21)
    for k in range(40):
        a = 2 * math.pi * k / 40
        r = 1 + 0.06 * math.sin(3 * a + 0.7) + 0.04 * math.sin(5 * a + 1.9)
        x, y = 0.10 * r * math.cos(a), 0.062 * r * math.sin(a)
        if y > 0: y *= 1.0 + 0.18 * math.cos(a)          # the fat cap side bulges toward one end
        pts.append(bm.verts.new((x, y, 0)))
    f = bm.faces.new(pts)
    ext = bmesh.ops.extrude_face_region(bm, geom=[f]); top = [v for v in ext["geom"] if isinstance(v, bmesh.types.BMVert)]
    bmesh.ops.translate(bm, verts=top, vec=(0, 0, 0.025))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    me = bpy.data.meshes.new(name); bm.to_mesh(me); bm.free()
    o = bpy.data.objects.new(name, me); sc.collection.objects.link(o); o.data.materials.append(steak_m)
    bev = o.modifiers.new("b", "BEVEL"); bev.width = 0.007; bev.segments = 4; bev.limit_method = "ANGLE"
    sub = o.modifiers.new("s", "SUBSURF"); sub.levels = 1; sub.render_levels = 2
    o.data.polygons.foreach_set("use_smooth", [True] * len(o.data.polygons))
    return o
STEAK = steak_mesh("steak"); STEAK.location = SKIL_AX + Vector((0, 0, 0.0066)); STEAK.rotation_euler.z = math.radians(18)
SIZZLE = steam("sizzle", SKIL_AX, 0.04, 10.0, 0.13)

# ---------------------------------------------------------------- light and camera
world = bpy.data.worlds.new("w"); sc.world = world; world.use_nodes = True
bg = next(n for n in world.node_tree.nodes if n.type == "BACKGROUND"); bg.inputs["Color"].default_value = (0.42, 0.45, 0.5, 1); bg.inputs["Strength"].default_value = 0.25
def area(name, loc, target, size, power, color):
    l = bpy.data.lights.new(name, "AREA"); l.size = size; l.energy = power; l.color = color
    o = bpy.data.objects.new(name, l); sc.collection.objects.link(o); o.location = loc
    o.rotation_euler = (Vector(target) - Vector(loc)).to_track_quat("-Z", "Y").to_euler(); return o
for dx in (0, OX, OX2):
    area(f"window{dx}", mc(dx - 1.2, 1.7, -1.6), mc(dx + 0.3, 0.7, 0.45), 1.4, 160, (1.0, 0.97, 0.92))
    area(f"fill{dx}", mc(dx + 1.8, 1.2, -1.4), mc(dx + 0.3, 0.7, 0.45), 2.0, 60, (0.85, 0.9, 1.0))
    area(f"top{dx}", mc(dx + 0.3, 2.3, 0.0), mc(dx + 0.3, 0.7, 0.45), 0.8, 50, (1.0, 0.98, 0.95))

cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam")); sc.collection.objects.link(cam); sc.camera = cam
def aim(eye, target, lens=50, fov=None):
    cam.location = mc(*eye)
    if fov:   # the game's vertical field of view
        cam.data.sensor_fit = "VERTICAL"; cam.data.angle_y = math.radians(fov)
    else:
        cam.data.sensor_fit = "AUTO"; cam.data.lens = lens
    cam.rotation_euler = (mc(*target) - cam.location).to_track_quat("-Z", "Y").to_euler()

# ---------------------------------------------------------------- states
CONTENT = [PAIL_WATER, *BUBBLES, PAIL_STEAM, SOUP, *PIECES, SOUP_STEAM, OIL, STEAK, SIZZLE]
GROUPS = {"PAIL": pail, "OVEN": oven, "SKILLET": skillet, "SPOON": spoon, "SKILLET2": skillet2}
def show_group(g, on, skip=()):
    for k, o in GROUPS[g].items(): o.hide_render = not on or k in skip
def state(name):
    for g in GROUPS: show_group(g, False)
    for o in CONTENT: o.hide_render = True
    sx, sz = OX + COOK[0], COOK[1]
    stand = (sx + 0.06, 1.62, G["Z0"] - 0.42)             # the player's eyes standing at the front (1.62 m)
    lean = (sx - 0.02, 1.27, G["Z0"] - 0.17)              # eased in over the pan (as at the workbench) and the view narrowed to 50 deg
    if name == "pail_lid":
        show_group("PAIL", True); view = ((0.95, 1.25, -0.6), (0.22, 0.78, 0.38), 45)
    elif name == "pail_boil":
        show_group("PAIL", True, ("lid", "lid_knob")); [setattr(o, "hide_render", False) for o in (PAIL_WATER, *BUBBLES, PAIL_STEAM)]
        view = ((0.7, 1.35, -0.25), (0.2, 0.85, 0.38), 50)
    elif name == "cookstove":
        view = ((OX + 1.45, 1.35, -1.25), (OX + 0.5, 0.62, 0.45), 35)
    elif name == "cs_lid":
        show_group("OVEN", True); view = ((sx + 0.85, 1.3, -0.65), (sx + 0.05, 0.86, 0.42), 45)
    elif name == "cs_soup":
        show_group("OVEN", True, ("lid", "lid_handle")); show_group("SPOON", True)
        for o in (SOUP, *PIECES, SOUP_STEAM): o.hide_render = False
        view = ((sx + 0.6, 1.35, -0.25), (sx, 0.85, 0.40), 50)
    elif name == "cs_steak":
        show_group("SKILLET", True); [setattr(o, "hide_render", False) for o in (OIL, STEAK, SIZZLE)]
        view = ((sx + 0.7, 1.25, -0.25), (sx + 0.05, 0.8, 0.42), 50)
    elif name == "range":
        view = ((OX2 + 1.45, 1.4, -1.25), (OX2 + 0.5, 0.62, 0.42), 35)
    elif name == "range_skillet":
        show_group("SKILLET2", True); view = ((OX2 + RG_SPOT[0] - OX2 + 0.7, 1.45, -0.3), (OX2 + RG_SPOT[0] - OX2 - 0.05, 0.95, 0.35), 45)
    elif name == "fp_stand":
        show_group("SKILLET", True); [setattr(o, "hide_render", False) for o in (OIL, STEAK)]
        aim(stand, (sx, CS_TOP + 0.02, sz), fov=70); return render(name)
    elif name == "fp_lean":
        show_group("SKILLET", True); [setattr(o, "hide_render", False) for o in (OIL, STEAK)]
        aim(lean, (sx, CS_TOP + 0.02, sz + 0.02), fov=50); return render(name)
    aim(*view)
    render(name)

def render(name):
    sc.render.filepath = os.path.join(OUT, name + ".png")
    bpy.ops.render.render(write_still=True)
    print("rendered", name)

# ---------------------------------------------------------------- studio (the items alone, at x = 20 m)
S0 = Vector((20, 0, 0))
st = {}
for key, item, parts in (("oven", "cast_iron_dutch_oven", None), ("open", "cast_iron_dutch_oven", ["body", "handle_east", "handle_west"]),
                         ("skil", "cast_iron_skillet", None), ("pail", "enamel_water_pail", None), ("spoon", "table_spoon", None)):
    obs = bbmodel(item, parts, name="st_" + key); st[key] = (obs, group(obs, "ST_" + key))
def st_rest(key, ref, x, y):
    obs, g = st[key]; lo, hi = bbox([obs[ref]]); c = (lo + hi) / 2
    g.location += Vector((S0.x + x - c.x, S0.y + y - c.y, -lo.z))
st_rest("oven", "body", -0.42, 0.12); st_rest("open", "body", 0.0, 0.12); st_rest("pail", "body", 0.42, 0.15)
st_rest("skil", "pan", -0.12, -0.33)
obs, g = st["spoon"]; g.rotation_euler = (0, math.radians(7), math.radians(-70)); st_rest("spoon", "bowl", 0.42, -0.30)
bpy.ops.mesh.primitive_plane_add(size=6, location=S0); ST_FLOOR = bpy.context.object
ST_FLOOR.data.materials.append(plain_mat("studio_floor", (0.30, 0.29, 0.27), 0.6))
st_lights = [area("st_key", S0 + Vector((-0.9, -0.9, 1.1)), S0, 1.2, 120, (1, 0.97, 0.92)),
             area("st_fill", S0 + Vector((1.0, -0.6, 0.6)), S0, 1.6, 40, (0.9, 0.93, 1)),
             area("st_rim", S0 + Vector((0.2, 1.0, 0.8)), S0, 1.0, 60, (1, 1, 1))]
def studio(name, eye, target, lens):
    keep = {ST_FLOOR}
    for obs, g in st.values(): keep.update(obs.values())
    saved = {o: o.hide_render for o in sc.objects}
    for o in sc.objects:
        if o.type == "MESH": o.hide_render = o not in keep
        if o.type == "LIGHT": o.hide_render = o not in st_lights
    cam.data.sensor_fit = "AUTO"; cam.location = S0 + Vector(eye); cam.data.lens = lens
    cam.rotation_euler = (S0 + Vector(target) - cam.location).to_track_quat("-Z", "Y").to_euler()
    render(name)
    for o, h in saved.items(): o.hide_render = h
STUDIO = {"studio_k0b": ((0.0, -1.45, 1.0), (0.0, -0.05, 0.06), 45)}

ALL = ["pail_lid", "pail_boil", "cookstove", "cs_lid", "cs_soup", "cs_steak", "range", "range_skillet"]
FP = ["fp_stand", "fp_lean"]
for s in (STATES or ALL + FP + list(STUDIO)):
    if s in STUDIO: studio(s, *STUDIO[s])
    else: state(s)

def sheet(names, cols, out_name):
    files = [os.path.join(OUT, n + ".png") for n in names]
    if not all(os.path.exists(f) for f in files): return
    ims = [bpy.data.images.load(f) for f in files]
    w, h = ims[0].size; gap = 12; rows = (len(ims) + cols - 1) // cols
    sh = np.ones(((h + gap) * rows + gap, (w + gap) * cols + gap, 4), np.float32) * np.array([0.12, 0.12, 0.12, 1], np.float32)
    for k, im in enumerate(ims):
        if tuple(im.size) != (w, h): continue
        px = np.array(im.pixels[:], np.float32).reshape(h, w, 4)
        r, c = rows - 1 - k // cols, k % cols
        sh[gap + r * (h + gap): gap + r * (h + gap) + h, gap + c * (w + gap): gap + c * (w + gap) + w] = px
    out = bpy.data.images.new(out_name, sh.shape[1], sh.shape[0]); out.pixels = sh.ravel()
    out.filepath_raw = os.path.join(OUT, out_name + ".png"); out.file_format = "PNG"; out.save(); print("sheet", out.filepath_raw)
sheet(["pail_boil", "cookstove", "cs_soup", "cs_steak", "range", "range_skillet"], 3, "cooking_k0b_stoves")
sheet(FP, 2, "cooking_k0b_first_person")
bpy.ops.wm.save_as_mainfile(filepath=os.path.join(SP, "cooking_k0b_scene.blend"))
