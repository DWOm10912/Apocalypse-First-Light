# Box Stove V2: offline review renders of the baked stove (build.py's blend + textures) on the forest home's hearth,
# with the cast-iron dutch oven and skillet (design/items/cooking_v1/preview) on the two cook lids, and next to V1.
#   blender -b --factory-startup --python tools/stove/box_stove_v2/render.py -- [shots...] [--quick]
import bpy, bmesh, json, math, os, sys
import numpy as np
from mathutils import Vector, Matrix

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
A = os.path.join(ROOT, "src", "main", "resources", "assets", "apocalypse_firstlight")
D = os.path.join(ROOT, "design", "furniture", "box_stove_v2")
POTS = os.path.join(ROOT, "design", "items", "cooking_v1", "preview")
OUT = os.path.join(D, "renders"); os.makedirs(OUT, exist_ok=True)
argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
QUICK = "--quick" in argv
SHOTS = [a for a in argv if not a.startswith("--")]

bpy.ops.wm.open_mainfile(filepath=os.path.join(D, "source", "box_stove_v2.blend"))
sc = bpy.context.scene
G = {}
exec(open(os.path.join(HERE, "model.py"), encoding="utf-8").read().split("# ---------------------------------------------------------------- materials")[0], G)   # constants only
LIDS, LID_TOP, HINGE = G["LIDS"], G["LID_TOP"], G["HINGE"]

def mc(x, y, z): return Vector((x, -z, y))

IMG = {}
def img(path, data=False):
    if path not in IMG:
        im = bpy.data.images.load(path)
        if data: im.colorspace_settings.name = "Non-Color"
        IMG[path] = im
    return IMG[path]

def lab_mat(name, base, closest=True, true_normal=True):
    """Principled from an AFL LabPBR set: albedo x AO (_n B), roughness = 1 - smoothness (_s R), metal = F0 >= 230 (_s G),
    the _n RG DirectX tangent normal (z rebuilt) when true_normal."""
    m = bpy.data.materials.new(name); m.use_nodes = True; nt = m.node_tree; N = nt.nodes; L = nt.links
    b = next(n for n in N if n.type == "BSDF_PRINCIPLED")
    def tex(p, data):
        t = N.new("ShaderNodeTexImage"); t.image = img(p, data); t.interpolation = "Closest" if closest else "Linear"; return t
    t = tex(base + ".png", False); col = t.outputs["Color"]
    if os.path.exists(base + "_n.png"):
        tn = tex(base + "_n.png", True); sep = N.new("ShaderNodeSeparateColor"); L.new(tn.outputs["Color"], sep.inputs[0])
        mix = N.new("ShaderNodeMix"); mix.data_type = "RGBA"; mix.blend_type = "MULTIPLY"; mix.inputs["Factor"].default_value = 1.0
        L.new(col, mix.inputs[6]); L.new(sep.outputs[2], mix.inputs[7]); col = mix.outputs[2]
        if true_normal:
            x = N.new("ShaderNodeMath"); x.operation = "MULTIPLY_ADD"; x.inputs[1].default_value = 2; x.inputs[2].default_value = -1; L.new(sep.outputs[0], x.inputs[0])
            y = N.new("ShaderNodeMath"); y.operation = "MULTIPLY_ADD"; y.inputs[1].default_value = -2; y.inputs[2].default_value = 1; L.new(sep.outputs[1], y.inputs[0])
            xx = N.new("ShaderNodeMath"); xx.operation = "MULTIPLY"; L.new(x.outputs[0], xx.inputs[0]); L.new(x.outputs[0], xx.inputs[1])
            yy = N.new("ShaderNodeMath"); yy.operation = "MULTIPLY"; L.new(y.outputs[0], yy.inputs[0]); L.new(y.outputs[0], yy.inputs[1])
            s = N.new("ShaderNodeMath"); s.operation = "ADD"; L.new(xx.outputs[0], s.inputs[0]); L.new(yy.outputs[0], s.inputs[1])
            z = N.new("ShaderNodeMath"); z.operation = "SUBTRACT"; z.inputs[0].default_value = 1; L.new(s.outputs[0], z.inputs[1])
            zr = N.new("ShaderNodeMath"); zr.operation = "SQRT"; zr.use_clamp = True; L.new(z.outputs[0], zr.inputs[0])
            cv = N.new("ShaderNodeCombineXYZ"); L.new(x.outputs[0], cv.inputs[0]); L.new(y.outputs[0], cv.inputs[1]); L.new(zr.outputs[0], cv.inputs[2])
            sc_ = N.new("ShaderNodeVectorMath"); sc_.operation = "MULTIPLY_ADD"; sc_.inputs[1].default_value = (0.5, 0.5, 0.5); sc_.inputs[2].default_value = (0.5, 0.5, 0.5)
            L.new(cv.outputs[0], sc_.inputs[0])
            nm = N.new("ShaderNodeNormalMap"); L.new(sc_.outputs[0], nm.inputs["Color"]); L.new(nm.outputs[0], b.inputs["Normal"])
    L.new(col, b.inputs["Base Color"])
    if os.path.exists(base + "_s.png"):
        ts = tex(base + "_s.png", True); sep = N.new("ShaderNodeSeparateColor"); L.new(ts.outputs["Color"], sep.inputs[0])
        inv = N.new("ShaderNodeMath"); inv.operation = "SUBTRACT"; inv.inputs[0].default_value = 1.0; L.new(sep.outputs[0], inv.inputs[1])
        L.new(inv.outputs[0], b.inputs["Roughness"])
        met = N.new("ShaderNodeMath"); met.operation = "GREATER_THAN"; met.inputs[1].default_value = 229.5 / 255; L.new(sep.outputs[1], met.inputs[0])
        L.new(met.outputs[0], b.inputs["Metallic"])
    return m

def plain_mat(name, color, rough=0.5):
    m = bpy.data.materials.new(name); m.use_nodes = True
    b = next(n for n in m.node_tree.nodes if n.type == "BSDF_PRINCIPLED"); b.inputs["Base Color"].default_value = (*color, 1); b.inputs["Roughness"].default_value = rough
    return m

def finish(ob, angle=35, merge=True, recalc=False):
    bm = bmesh.new(); bm.from_mesh(ob.data)
    if merge: bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-6)
    if recalc: bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    for f in bm.faces: f.smooth = True
    for e in bm.edges: e.smooth = not (len(e.link_faces) != 2 or e.calc_face_angle(0) > math.radians(angle))
    bm.to_mesh(ob.data); bm.free()

def tex_path(ref):
    ns, p = ref.split(":"); return os.path.join(A, "textures", *p.split("/"))

def cabin(rel, offset=(0, 0, 0), name=None, angle=35):
    j = json.load(open(os.path.join(A, "cabin_mesh", rel)))
    mats = [lab_mat(t.split(":")[1], tex_path(t), true_normal=False) for t in j["textures"]]
    verts, faces, uvs, mi = [], [], [], []
    for q in j["quads"]:
        ids = []
        for k in range(4):
            x, y, z, u, v = q[1 + 8 * k: 6 + 8 * k]
            verts.append(mc(x + offset[0], y + offset[1], z + offset[2])); ids.append(len(verts) - 1); uvs.append((u, 1 - v))
        P = [verts[i] for i in ids]; n = (P[1] - P[0]).cross(P[2] - P[0])
        if n.dot(mc(*q[6:9])) < 0: ids.reverse(); uvs[-4:] = uvs[-4:][::-1]
        faces.append(ids); mi.append(q[0])
    me = bpy.data.meshes.new(name or rel); me.from_pydata(verts, [], faces)
    for m in mats: me.materials.append(m)
    uvl = me.uv_layers.new()
    for p, f in zip(me.polygons, faces):
        p.material_index = mi[p.index]
        for li, vi in zip(p.loop_indices, range(4)): uvl.data[li].uv = uvs[p.index * 4 + vi]
    ob = bpy.data.objects.new(name or rel, me); sc.collection.objects.link(ob); finish(ob, angle)
    return ob

def bbmodel(item, name=None):
    src = json.load(open(os.path.join(POTS, item + ".bbmodel")))
    W = src["resolution"]["width"]; H = src["resolution"]["height"]
    m = lab_mat(item, os.path.join(POTS, item + "_mesh"), true_normal=False)
    root = bpy.data.objects.new(name or item, None); sc.collection.objects.link(root)
    for el in src["elements"]:
        keys = list(el["vertices"]); kid = {k: i for i, k in enumerate(keys)}
        verts = [Vector((p[0], -p[2], p[1])) * 0.01 for p in (el["vertices"][k] for k in keys)]
        faces, fuv = [], []
        for f in el["faces"].values():
            faces.append([kid[k] for k in f["vertices"]]); fuv.append([f["uv"][k] for k in f["vertices"]])
        me = bpy.data.meshes.new(el["name"]); me.from_pydata(verts, [], faces); me.materials.append(m)
        uvl = me.uv_layers.new()
        for p in me.polygons:
            for li, (u, v) in zip(p.loop_indices, fuv[p.index]): uvl.data[li].uv = (u / W, 1 - v / H)
        ob = bpy.data.objects.new((name or item) + ":" + el["name"], me); sc.collection.objects.link(ob)
        finish(ob, 35, merge=False, recalc=True); ob.parent = root
    return root

# ---------------------------------------------------------------- the baked stove
BODY = bpy.data.objects["box_stove_v2_body"]; DOOR = bpy.data.objects["box_stove_v2_door"]; INSIDE = bpy.data.objects["box_stove_v2_inside"]
for ob, g in ((BODY, "body"), (INSIDE, "inside"), (DOOR, "door")):
    ob.data.materials.clear(); ob.data.materials.append(lab_mat("v2_" + g, os.path.join(D, "textures", "box_stove_v2_" + g), closest=False))
    for p in ob.data.polygons: p.material_index = 0
DOOR_REST = DOOR.matrix_world.copy()
def door(open_deg):
    h = mc(HINGE[0], 0, HINGE[1]); best = None
    for s in (1, -1):
        M = Matrix.Translation(h) @ Matrix.Rotation(math.radians(open_deg * s), 4, "Z") @ Matrix.Translation(-h) @ DOOR_REST
        c = sum((M @ v.co for v in DOOR.data.vertices), Vector()) / len(DOOR.data.vertices)
        if best is None or c.x < best[0]: best = (c.x, M)
    DOOR.matrix_world = best[1]

# ---------------------------------------------------------------- forest home hearth context
cabin("shield/panel_0.json", name="heat_shield")
for cx in (-1, 0, 1):
    for cy in (0, 1):
        cabin("log_wall/run_x.json", (cx, cy, 1), name=f"log_{cx}_{cy}", angle=60)
for cy in (1, 2):
    cabin("pipe/stove_pipe.json", (0, cy, 0), name=f"pipe_{cy}")
for cx in (-1, 0, 1):
    for cz in (-1, 0, 1):
        t = (cz % 3) * 3 + (cx % 3)
        m = lab_mat(f"flag{t}", os.path.join(A, "textures", "block", "stove", f"flagstone_top_{t}"), true_normal=False)
        me = bpy.data.meshes.new("flag"); me.from_pydata([mc(cx, 0, cz), mc(cx + 1, 0, cz), mc(cx + 1, 0, cz + 1), mc(cx, 0, cz + 1)], [], [[0, 3, 2, 1]])
        me.materials.append(m); uv = me.uv_layers.new()
        for li, c in zip(me.polygons[0].loop_indices, [(0, 1), (0, 0), (1, 0), (1, 1)]): uv.data[li].uv = c
        if me.polygons[0].normal.z < 0: me.flip_normals()
        ob = bpy.data.objects.new(f"flag_{cx}_{cz}", me); sc.collection.objects.link(ob)
bpy.ops.mesh.primitive_plane_add(size=8, location=mc(0.5, -0.001, -1.5)); bpy.context.object.data.materials.append(plain_mat("boards", (0.23, 0.15, 0.09), 0.7))
HEARTH = [o for o in sc.objects if o.type == "MESH" and o not in (BODY, DOOR, INSIDE)]

OVEN = bbmodel("cast_iron_dutch_oven", "OVEN"); OVEN.location = mc(LIDS[0][0], LID_TOP, LIDS[0][1])
SKIL = bbmodel("cast_iron_skillet", "SKILLET"); SKIL.rotation_euler = (0, 0, math.radians(90))
SKIL.location = mc(LIDS[1][0], LID_TOP, LIDS[1][1]) - Matrix.Rotation(math.radians(90), 3, "Z") @ Vector((-0.053377, 0, 0))
POTS_OBS = [OVEN, *OVEN.children, SKIL, *SKIL.children]

# V1 for the size comparison, one cell over toward the room's west (frame +x), shown only in that shot
V1 = [cabin("stove/body.json", (1.15, 0, 0), name="v1_body"), cabin("stove/door_closed.json", (1.15, 0, 0), name="v1_door")]

# ---------------------------------------------------------------- render setup
try: sc.render.engine = "CYCLES"
except TypeError as e: print(e)
prefs = bpy.context.preferences.addons["cycles"].preferences
for kind in ("OPTIX", "CUDA"):
    try:
        prefs.compute_device_type = kind; prefs.get_devices()
        for d in prefs.devices: d.use = True
        sc.cycles.device = "GPU"; break
    except Exception as e: print("no", kind, e)
sc.cycles.samples = 48 if QUICK else 192; sc.cycles.use_denoising = True
sc.render.resolution_x, sc.render.resolution_y = (640, 480) if QUICK else (1200, 900)
sc.view_settings.view_transform = "AgX"; sc.view_settings.exposure = -0.8
looks = [i.identifier for i in bpy.types.ColorManagedViewSettings.bl_rna.properties["look"].enum_items]
sc.view_settings.look = "AgX - Medium High Contrast" if "AgX - Medium High Contrast" in looks else "None"
world = bpy.data.worlds.new("w"); sc.world = world; world.use_nodes = True
bg = next(n for n in world.node_tree.nodes if n.type == "BACKGROUND"); bg.inputs["Color"].default_value = (0.42, 0.45, 0.5, 1); bg.inputs["Strength"].default_value = 0.25
def area(name, loc, target, size, power, color):
    l = bpy.data.lights.new(name, "AREA"); l.size = size; l.energy = power; l.color = color
    o = bpy.data.objects.new(name, l); sc.collection.objects.link(o); o.location = loc
    o.rotation_euler = (Vector(target) - Vector(loc)).to_track_quat("-Z", "Y").to_euler(); return o
area("window", mc(-1.2, 1.7, -1.6), mc(0.4, 0.6, 0.45), 1.4, 170, (1.0, 0.97, 0.92))
area("fill", mc(2.2, 1.2, -1.4), mc(0.5, 0.6, 0.45), 2.0, 70, (0.85, 0.9, 1.0))
area("top", mc(0.5, 2.2, 0.0), mc(0.5, 0.6, 0.45), 0.8, 50, (1.0, 0.98, 0.95))
cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam")); sc.collection.objects.link(cam); sc.camera = cam
def aim(eye, target, lens):
    cam.location = mc(*eye); cam.data.lens = lens
    cam.rotation_euler = (mc(*target) - cam.location).to_track_quat("-Z", "Y").to_euler()

TORCH = area("torch", mc(-0.42, 0.58, 0.3), mc(0.55, 0.22, 0.4), 0.12, 25, (1.0, 0.95, 0.85))
def shot(name, eye, target, lens, pots=True, open_deg=0, v1=False, torch=False, shell=True):
    TORCH.hide_render = not torch
    BODY.hide_render = DOOR.hide_render = not shell
    for o in POTS_OBS: o.hide_render = not pots
    for o in V1: o.hide_render = not v1
    door(open_deg); aim(eye, target, lens)
    sc.render.filepath = os.path.join(OUT, name + ".png"); bpy.ops.render.render(write_still=True); print("rendered", name)

ALL = {
    "v2_pots": dict(eye=(1.25, 1.3, -0.85), target=(0.5, 0.55, 0.45), lens=38),
    "v2_front": dict(eye=(0.5, 0.78, -1.45), target=(0.5, 0.45, 0.42), lens=38, pots=False),
    "v2_door_open": dict(eye=(-0.75, 0.85, -0.6), target=(0.15, 0.42, 0.4), lens=40, pots=False, open_deg=95),
    "v2_top": dict(eye=(0.62, 1.12, -0.18), target=(0.47, 0.64, 0.42), lens=45, pots=False),
    "v2_inside": dict(eye=(-0.5, 0.6, 0.2), target=(0.5, 0.34, 0.4), lens=32, pots=False, open_deg=95, torch=True),
    "dbg_firebox": dict(eye=(0.12, 0.56, 0.38), target=(0.6, 0.2, 0.38), lens=18, pots=False, shell=False),
    "v2_vs_v1": dict(eye=(1.1, 1.05, -1.75), target=(1.1, 0.42, 0.45), lens=34, pots=False, v1=True),
}
for s in (SHOTS or [k for k in ALL if not k.startswith("dbg")]):
    shot(s, **ALL[s])

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
sheet(["v2_pots", "v2_front", "v2_door_open", "v2_top"], 2, "box_stove_v2_review")
sheet(["v2_door_open", "v2_inside"], 2, "box_stove_v2_firebox")
