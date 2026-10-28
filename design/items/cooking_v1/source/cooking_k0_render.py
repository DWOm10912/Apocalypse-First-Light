# Cooking V1 K0: offline renders of the cast iron dutch oven, skillet and table spoon on the forest home's box stove.
# blender -b --factory-startup --python cooking_k0_render.py -- <states...>
import bpy, bmesh, json, math, os, random, sys
import numpy as np
from mathutils import Vector, Euler, Matrix

ROOT = r"D:\Minecraft Modding\Apocalypse First Light"
A = ROOT + r"\src\main\resources\assets\apocalypse_firstlight"
SP = os.path.join(os.path.dirname(os.path.abspath(__file__)), "cooking_k0")
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
def tex_mat(name, base, alpha_clip=False):
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
    if alpha_clip:
        L.new(t.outputs["Alpha"], bsdf.inputs["Alpha"])
    MAT[name] = m
    return m

def plain_mat(name, color, rough=0.5, metal=0.0, **kw):
    m = bpy.data.materials.new(name); m.use_nodes = True
    b = next(n for n in m.node_tree.nodes if n.type == "BSDF_PRINCIPLED")
    b.inputs["Base Color"].default_value = (*color, 1); b.inputs["Roughness"].default_value = rough; b.inputs["Metallic"].default_value = metal
    for k, v in kw.items(): b.inputs[k].default_value = v
    return m

def tex_path(ref):           # 'apocalypse_firstlight:block/stove/cast_iron' -> file stem
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

# ---------------------------------------------------------------- cabin mesh (block units, quads)
def cabin(rel, offset=(0, 0, 0), name=None, angle=35):
    j = json.load(open(os.path.join(A, "cabin_mesh", rel)))
    mats = [tex_mat(t.split(":")[1], tex_path(t)) for t in j["textures"]]
    verts, faces, uvs, mi = [], [], [], []
    for q in j["quads"]:
        ids = []
        for k in range(4):
            x, y, z, u, v, nx, ny, nz = q[1 + 8 * k: 9 + 8 * k]
            verts.append(mc(x + offset[0], y + offset[1], z + offset[2])); ids.append(len(verts) - 1); uvs.append((u, 1 - v))
        # wind to the stored normal
        P = [verts[i] for i in ids]; n = (P[1] - P[0]).cross(P[2] - P[0])
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
    return ob

# ---------------------------------------------------------------- AFL bbmodel (cm, Y up)
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
sc.view_settings.look = "AgX - Medium High Contrast" if "AgX - Medium High Contrast" in [i.identifier for i in bpy.types.ColorManagedViewSettings.bl_rna.properties["look"].enum_items] else "None"

stove = [cabin("stove/body.json", name="stove_body"), cabin("stove/door_closed.json", name="stove_door")]
cabin("shield/panel_0.json", name="heat_shield")
for cx in (-1, 0, 1):
    for cy in (0, 1):
        cabin("log_wall/run_x.json", (cx, cy, 1), name=f"log_{cx}_{cy}", angle=60)
# flagstone hearth: 3 x 3 tiles chosen by world cell like the macro_tile loader
for cx in (-1, 0, 1):
    for cz in (-1, 0, 1):
        t = (cz % 3) * 3 + (cx % 3)
        m = tex_mat(f"flag{t}", os.path.join(A, "textures", "block", "stove", f"flagstone_top_{t}"))
        me = bpy.data.meshes.new("flag"); me.from_pydata([mc(cx, 0, cz), mc(cx + 1, 0, cz), mc(cx + 1, 0, cz + 1), mc(cx, 0, cz + 1)], [], [[0, 3, 2, 1]])
        me.materials.append(m); uv = me.uv_layers.new()
        for li, c in zip(me.polygons[0].loop_indices, [(0, 1), (0, 0), (1, 0), (1, 1)]): uv.data[li].uv = c
        if me.polygons[0].normal.z < 0: me.flip_normals()
        ob = bpy.data.objects.new(f"flag_{cx}_{cz}", me); sc.collection.objects.link(ob)
# floor beyond the hearth
bpy.ops.mesh.primitive_plane_add(size=8, location=mc(0.5, -0.001, -1.5)); bpy.context.object.data.materials.append(plain_mat("boards", (0.23, 0.15, 0.09), 0.7))

SPOT = mc(0.20, 0.567, 0.46)
oven = bbmodel("cast_iron_dutch_oven"); OVEN = group(oven, "OVEN"); OVEN.location = SPOT
skillet = bbmodel("cast_iron_skillet"); SKIL = group(skillet, "SKILLET")
SKIL.rotation_euler = (0, 0, math.radians(90)); SKIL.location = SPOT - Matrix.Rotation(math.radians(90), 3, "Z") @ Vector((-0.053377, 0, 0))
spoon = bbmodel("table_spoon"); SPOON = group(spoon, "SPOON")

# ---------------------------------------------------------------- contents (render only)
Ri = 0.1255
def disc_cyl(name, r, z0, z1, mat, verts=96):
    bpy.ops.mesh.primitive_cylinder_add(vertices=verts, radius=r, depth=z1 - z0, location=(0, 0, (z0 + z1) / 2))
    o = bpy.context.object; o.name = name; o.data.materials.append(mat); o.parent = OVEN
    bpy.ops.object.shade_smooth(); return o

water_mat = plain_mat("water", (0.8, 0.85, 0.85), 0.03, **{"Transmission Weight": 1.0, "IOR": 1.333})
wvol = water_mat.node_tree.nodes.new("ShaderNodeVolumeAbsorption"); wvol.inputs["Color"].default_value = (0.75, 0.82, 0.8, 1); wvol.inputs["Density"].default_value = 4.0
water_mat.node_tree.links.new(wvol.outputs[0], next(n for n in water_mat.node_tree.nodes if n.type == "OUTPUT_MATERIAL").inputs["Volume"])
WATER = disc_cyl("water", Ri - 0.0004, 0.006, 0.086, water_mat)

# boiling: the water's top bumped by a noise (a separate material on a copy), bubbles and froth on it, steam
boil_mat = water_mat.copy(); boil_mat.name = "boil_water"
bn = boil_mat.node_tree.nodes; bb = next(n for n in bn if n.type == "BSDF_PRINCIPLED")
bco = bn.new("ShaderNodeTexCoord"); bnoise = bn.new("ShaderNodeTexNoise"); bnoise.inputs["Scale"].default_value = 60.0; bnoise.inputs["Detail"].default_value = 3
bump = bn.new("ShaderNodeBump"); bump.inputs["Strength"].default_value = 0.6; bump.inputs["Distance"].default_value = 0.004
boil_mat.node_tree.links.new(bco.outputs["Object"], bnoise.inputs["Vector"]); boil_mat.node_tree.links.new(bnoise.outputs["Fac"], bump.inputs["Height"])
boil_mat.node_tree.links.new(bump.outputs["Normal"], bb.inputs["Normal"])
BOIL = disc_cyl("boil", Ri - 0.0004, 0.006, 0.086, boil_mat)
random.seed(4)
bubble_mat = plain_mat("bubble", (0.95, 0.96, 0.96), 0.05, **{"Transmission Weight": 0.85, "IOR": 1.333})
BUBBLES = []
for i in range(150):
    a = random.random() * 2 * math.pi; r = Ri * 0.92 * math.sqrt(random.random()) * (0.55 if i < 90 else 1)
    s = random.uniform(0.0012, 0.0045) if i < 140 else random.uniform(0.006, 0.009)
    bpy.ops.mesh.primitive_uv_sphere_add(radius=s, segments=16, ring_count=8, location=(r * math.cos(a), r * math.sin(a), 0.086 - s * 0.2))
    b = bpy.context.object; b.scale.z = 0.75; b.parent = OVEN; b.data.materials.append(bubble_mat); bpy.ops.object.shade_smooth(); BUBBLES.append(b)

def steam(name, strength):
    bpy.ops.mesh.primitive_cylinder_add(vertices=32, radius=0.15, depth=0.36, location=(0, 0, 0.27))
    o = bpy.context.object; o.name = name; o.parent = OVEN
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
    # fade with height above the pot and toward the cylinder's side
    sepx = N.new("ShaderNodeSeparateXYZ"); nt.links.new(co.outputs["Object"], sepx.inputs[0])
    hz = N.new("ShaderNodeMapRange"); hz.inputs["From Min"].default_value = -0.17; hz.inputs["From Max"].default_value = 0.17
    hz.inputs["To Min"].default_value = 1.0; hz.inputs["To Max"].default_value = 0.0; nt.links.new(sepx.outputs["Z"], hz.inputs[0])
    rad = N.new("ShaderNodeVectorMath"); rad.operation = "LENGTH"
    xy = N.new("ShaderNodeCombineXYZ"); nt.links.new(sepx.outputs["X"], xy.inputs[0]); nt.links.new(sepx.outputs["Y"], xy.inputs[1]); nt.links.new(xy.outputs[0], rad.inputs[0])
    rr = N.new("ShaderNodeMapRange"); rr.inputs["From Min"].default_value = 0.06; rr.inputs["From Max"].default_value = 0.14
    rr.inputs["To Min"].default_value = 1.0; rr.inputs["To Max"].default_value = 0.0; nt.links.new(rad.outputs["Value"], rr.inputs[0])
    m1 = N.new("ShaderNodeMath"); m1.operation = "MULTIPLY"; nt.links.new(ramp.outputs["Color"], m1.inputs[0]); nt.links.new(hz.outputs[0], m1.inputs[1])
    m2 = N.new("ShaderNodeMath"); m2.operation = "MULTIPLY"; nt.links.new(m1.outputs[0], m2.inputs[0]); nt.links.new(rr.outputs[0], m2.inputs[1])
    m3 = N.new("ShaderNodeMath"); m3.operation = "MULTIPLY"; m3.inputs[1].default_value = strength; nt.links.new(m2.outputs[0], m3.inputs[0])
    nt.links.new(m3.outputs[0], vol.inputs["Density"]); nt.links.new(vol.outputs[0], out.inputs["Volume"])
    o.data.materials.append(m); return o
STEAM = steam("steam", 60.0)
STEAM_SOFT = steam("steam_soft", 25.0)

# soup: broth and pieces (potato, onion, kidney beans)
broth = plain_mat("broth", (0.30, 0.16, 0.06), 0.12, **{"Subsurface Weight": 0.3, "Coat Weight": 0.5, "Coat Roughness": 0.05})
SOUP = disc_cyl("soup", Ri - 0.0004, 0.006, 0.080, broth)
potato = plain_mat("potato", (0.62, 0.40, 0.12), 0.5, **{"Subsurface Weight": 0.25})
onion = plain_mat("onion", (0.62, 0.48, 0.22), 0.25, **{"Subsurface Weight": 0.6})
bean = plain_mat("bean", (0.30, 0.06, 0.05), 0.3)
PIECES = []
random.seed(11)
for i in range(46):
    a = random.random() * 2 * math.pi; r = (Ri - 0.012) * math.sqrt(random.random())
    kind = i % 3; loc = (r * math.cos(a), r * math.sin(a), 0.080 + random.uniform(-0.005, 0.002))
    if kind == 0:
        bpy.ops.mesh.primitive_cube_add(size=random.uniform(0.012, 0.018), location=loc)
        o = bpy.context.object; o.scale = (random.uniform(0.8, 1.3), random.uniform(0.7, 1.1), random.uniform(0.6, 1.0)); bev = o.modifiers.new("b", "BEVEL"); bev.width = 0.003; bev.segments = 3; o.data.materials.append(potato)
    elif kind == 1:
        bpy.ops.mesh.primitive_cylinder_add(vertices=16, radius=random.uniform(0.008, 0.014), depth=0.002, location=loc)
        o = bpy.context.object; o.data.materials.append(onion); o.scale.x = 0.5
    else:
        bpy.ops.mesh.primitive_uv_sphere_add(radius=0.0078, segments=16, ring_count=8, location=loc)
        o = bpy.context.object; o.scale = (1.0, 0.62, 0.55); o.data.materials.append(bean)
    o.rotation_euler = Euler([random.uniform(0, 6.3) for _ in range(3)]); o.parent = OVEN; bpy.ops.object.shade_smooth(); PIECES.append(o)

# skillet: oil film and potato slices
oil = plain_mat("oil", (0.5, 0.36, 0.12), 0.02, **{"Transmission Weight": 0.6, "IOR": 1.47})
bpy.ops.mesh.primitive_cylinder_add(vertices=96, radius=0.1065, depth=0.0006, location=SPOT + Vector((0, 0, 0.0058)))
OIL = bpy.context.object
OIL.data.materials.append(oil); bpy.ops.object.shade_smooth()
fried = bpy.data.materials.new("fried"); fried.use_nodes = True
fn = fried.node_tree.nodes; fb = next(n for n in fn if n.type == "BSDF_PRINCIPLED")
fb.inputs["Roughness"].default_value = 0.35; fb.inputs["Subsurface Weight"].default_value = 0.2
gco = fn.new("ShaderNodeTexCoord"); gdist = fn.new("ShaderNodeVectorMath"); gdist.operation = "LENGTH"
gsep = fn.new("ShaderNodeSeparateXYZ"); gxy = fn.new("ShaderNodeCombineXYZ")
fried.node_tree.links.new(gco.outputs["Object"], gsep.inputs[0]); fried.node_tree.links.new(gsep.outputs["X"], gxy.inputs[0]); fried.node_tree.links.new(gsep.outputs["Y"], gxy.inputs[1])
fried.node_tree.links.new(gxy.outputs[0], gdist.inputs[0])
gramp = fn.new("ShaderNodeValToRGB"); gramp.color_ramp.elements[0].position = 0.55; gramp.color_ramp.elements[0].color = (0.80, 0.52, 0.08, 1)
gramp.color_ramp.elements[1].position = 0.95; gramp.color_ramp.elements[1].color = (0.30, 0.13, 0.04, 1)
gmr = fn.new("ShaderNodeMapRange"); gmr.inputs["From Max"].default_value = 0.023
fried.node_tree.links.new(gdist.outputs["Value"], gmr.inputs[0]); fried.node_tree.links.new(gmr.outputs[0], gramp.inputs[0]); fried.node_tree.links.new(gramp.outputs["Color"], fb.inputs["Base Color"])
SLICES = []
random.seed(7)
spots = [(0, 0)] + [(0.05 * math.cos(k * 2 * math.pi / 6), 0.05 * math.sin(k * 2 * math.pi / 6)) for k in range(6)] + [(0.085 * math.cos(k * 2 * math.pi / 8 + 0.3), 0.085 * math.sin(k * 2 * math.pi / 8 + 0.3)) for k in range(8)]
for (x, y) in spots:
    bpy.ops.mesh.primitive_cylinder_add(vertices=32, radius=random.uniform(0.020, 0.024), depth=0.005, location=SPOT + Vector((x, y, 0.0085)))
    o = bpy.context.object; o.scale.x = random.uniform(0.85, 1.0); o.rotation_euler.z = random.uniform(0, 3); o.data.materials.append(fried)
    bev = o.modifiers.new("b", "BEVEL"); bev.width = 0.0012; bev.segments = 2; bpy.ops.object.shade_smooth(); SLICES.append(o)

# spoon resting in the soup, the handle over the rim toward the room's front right
def place_spoon():
    d = mc(0.84, 0, 0.54).normalized()                    # toward the pot's back right, across the camera
    yaw = math.atan2(d.y, d.x); E = Euler((0, -math.radians(12), yaw), "XYZ")
    bowl = Vector((-0.063039, 0, 0.009))
    SPOON.rotation_euler = E; SPOON.location = SPOT + Vector((0, 0, 0.074)) - d * 0.03 - E.to_matrix() @ bowl
place_spoon()

# ---------------------------------------------------------------- light and camera
world = bpy.data.worlds.new("w"); sc.world = world; world.use_nodes = True
bg = next(n for n in world.node_tree.nodes if n.type == "BACKGROUND"); bg.inputs["Color"].default_value = (0.42, 0.45, 0.5, 1); bg.inputs["Strength"].default_value = 0.25
def area(name, loc, target, size, power, color):
    l = bpy.data.lights.new(name, "AREA"); l.size = size; l.energy = power; l.color = color
    o = bpy.data.objects.new(name, l); sc.collection.objects.link(o); o.location = loc
    o.rotation_euler = (Vector(target) - Vector(loc)).to_track_quat("-Z", "Y").to_euler(); return o
area("window", mc(-1.2, 1.7, -1.6), mc(0.2, 0.6, 0.45), 1.4, 160, (1.0, 0.97, 0.92))
area("fill", mc(1.8, 1.2, -1.4), mc(0.2, 0.6, 0.45), 2.0, 60, (0.85, 0.9, 1.0))
area("top", mc(0.3, 2.2, 0.0), mc(0.2, 0.6, 0.45), 0.8, 50, (1.0, 0.98, 0.95))

cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam")); sc.collection.objects.link(cam); sc.camera = cam
def aim(eye, target, lens=50):
    cam.location = mc(*eye); cam.data.lens = lens
    cam.rotation_euler = (mc(*target) - cam.location).to_track_quat("-Z", "Y").to_euler()

# ---------------------------------------------------------------- states
CONTENT = [WATER, BOIL, *BUBBLES, STEAM, STEAM_SOFT, SOUP, *PIECES, OIL, *SLICES]
ITEMS = {"OVEN": list(oven.values()), "SKIL": list(skillet.values()), "SPOON": list(spoon.values())}
def show(objs, on):
    for o in objs:
        for x in ITEMS.get(o.name.replace("SKILLET", "SKIL"), [o]): x.hide_render = not on
def state(name):
    show([OVEN, SKIL, SPOON], False); show(CONTENT, False)
    lid = [oven["lid"], oven["lid_handle"]]
    view = ((0.95, 1.2, -0.55), (0.2, 0.62, 0.46), 50)
    if name == "lid_on":
        show([OVEN], True)
    elif name == "water":
        show([OVEN], True); show(lid, False); show([WATER], True); view = ((0.7, 1.15, -0.25), (0.2, 0.6, 0.46), 55)
    elif name == "boiling":
        show([OVEN], True); show(lid, False); show([BOIL, *BUBBLES, STEAM], True)
        view = ((0.7, 1.15, -0.25), (0.2, 0.63, 0.46), 55)
    elif name == "soup":
        show([OVEN, SPOON], True); show(lid, False); show([SOUP, *PIECES, STEAM_SOFT], True); view = ((0.7, 1.15, -0.25), (0.2, 0.6, 0.46), 55)
    elif name == "soup_close":
        show([OVEN, SPOON], True); show(lid, False); show([SOUP, *PIECES], True); view = ((0.42, 0.95, 0.12), (0.2, 0.62, 0.46), 50)
    elif name == "skillet":
        show([SKIL], True); view = ((0.85, 1.05, -0.45), (0.2, 0.58, 0.40), 50)
    elif name == "frying":
        show([SKIL, OIL, *SLICES], True); view = ((0.6, 1.0, -0.2), (0.2, 0.58, 0.42), 55)
    aim(*view)
    WATER.hide_render = name != "water"
    sc.render.filepath = os.path.join(OUT, name + ".png")
    bpy.ops.render.render(write_still=True)
    print("rendered", name)


# ---------------------------------------------------------------- studio shots (the items alone, at x = 10 m)
ENV = [o for o in sc.objects if o.type == "MESH"]
S0 = Vector((10, 0, 0))
st_oven = bbmodel("cast_iron_dutch_oven", name="st_oven"); ST_OVEN = group(st_oven, "ST_OVEN")
st_open = bbmodel("cast_iron_dutch_oven", ["body", "handle_east", "handle_west"], name="st_open"); ST_OPEN = group(st_open, "ST_OPEN")
st_lid = bbmodel("cast_iron_dutch_oven", ["lid", "lid_handle"], name="st_lid"); ST_LID = group(st_lid, "ST_LID")
st_skil = bbmodel("cast_iron_skillet", name="st_skil"); ST_SKIL = group(st_skil, "ST_SKIL")
st_skil2 = bbmodel("cast_iron_skillet", name="st_skil2"); ST_SKIL2 = group(st_skil2, "ST_SKIL2")
st_spoon = bbmodel("table_spoon", name="st_spoon"); ST_SPOON = group(st_spoon, "ST_SPOON")
bpy.ops.mesh.primitive_plane_add(size=6, location=S0); ST_FLOOR = bpy.context.object
ST_FLOOR.data.materials.append(plain_mat("studio_floor", (0.30, 0.29, 0.27), 0.6))
ST_OVEN.location = S0 + Vector((-0.23, 0.05, 0))
ST_OPEN.location = S0 + Vector((0.16, 0.05, 0))
ST_LID.location = S0 + Vector((0.12, 0.05, -0.102 + 0.0005))
ST_LID.matrix_world = Matrix.Translation(S0 + Vector((0.16, -0.25, 0.0))) @ Matrix.Rotation(math.radians(180), 4, "X") @ Matrix.Translation(Vector((0, 0, -0.1155)))
ST_SKIL.location = S0 + Vector((-0.15, -0.34, 0))
ST_SKIL2.location = S0 + Vector((0.35, -0.42, 0.0)); ST_SKIL2.rotation_euler = (math.radians(180), 0, math.radians(200)); ST_SKIL2.location.z = 0.0733
ST_SPOON.location = S0 + Vector((-0.52, -0.22, 0.0)); ST_SPOON.rotation_euler = (0, math.radians(7), math.radians(-70))
STUDIO = [ST_FLOOR, ST_OVEN, ST_OPEN, ST_LID, ST_SKIL, ST_SKIL2, ST_SPOON]
st_lights = [area("st_key", S0 + Vector((-0.9, -0.9, 1.1)), S0, 1.2, 120, (1, 0.97, 0.92)),
             area("st_fill", S0 + Vector((1.0, -0.6, 0.6)), S0, 1.6, 40, (0.9, 0.93, 1)),
             area("st_rim", S0 + Vector((0.2, 1.0, 0.8)), S0, 1.0, 60, (1, 1, 1))]
def studio(name, eye, target, lens):
    keep = set()
    for g in STUDIO:
        keep.add(g); keep.update(g.children_recursive)
    saved = {o: o.hide_render for o in sc.objects}
    for o in sc.objects:
        if o.type == "MESH": o.hide_render = o not in keep
        if o.type == "LIGHT": o.hide_render = o not in st_lights
    cam.location = S0 + Vector(eye); cam.data.lens = lens
    cam.rotation_euler = (S0 + Vector(target) - cam.location).to_track_quat("-Z", "Y").to_euler()
    sc.render.filepath = os.path.join(OUT, name + ".png"); bpy.ops.render.render(write_still=True); print("rendered", name)
    for o, h in saved.items(): o.hide_render = h
STUDIO_SHOTS = {
    "studio_all": ((0.0, -1.35, 0.95), (-0.02, -0.17, 0.04), 50),
    "studio_oven": ((-0.04, -0.95, 0.55), (-0.04, 0.03, 0.06), 45),
    "studio_skillet": ((0.25, -0.95, 0.55), (0.08, -0.37, 0.03), 50),
    "studio_spoon": ((-0.66, -0.52, 0.30), (-0.52, -0.22, 0.008), 70),
}
ALL = ["lid_on", "water", "boiling", "soup", "skillet", "frying"]
for s in (STATES or ALL + list(STUDIO_SHOTS)):
    if s in STUDIO_SHOTS: studio(s, *STUDIO_SHOTS[s])
    else: state(s)

# contact sheets from what is on disk
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
sheet(ALL, 3, "cooking_k0_stove_states")
sheet(list(STUDIO_SHOTS), 2, "cooking_k0_items")
bpy.ops.wm.save_as_mainfile(filepath=os.path.join(SP, "cooking_k0_scene.blend"))
