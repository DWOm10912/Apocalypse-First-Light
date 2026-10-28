# Old-body clothes V1 review sheet (docs/dev/old_body_clothes_v1.md): the game's own skin mesh and garment shells, exported
# by `gradlew skinJointCompare -Pfemale -Pout=<dir>` (SkinJointCompare#female), dressed in the game textures and rendered
# on B's r6 stage. Rows: the man, the woman; columns: underwear, shirt + jeans + shoes, + jacket, then dressed sat / mid run
# / crouched. A last row: the woman's chest close, bare (the bra; also from below), under the shirt and the jacket.
#   blender -b --factory-startup --python tools/characters/old_body_clothes_sheet_v1.py -- <obj dir> <out.png>
import bpy, math, os, sys
from mathutils import Vector
import numpy as np

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "apocalypse_firstlight", "textures", "entity")
args = sys.argv[sys.argv.index("--") + 1:]
OBJ, OUT = args[0], args[1]

bpy.ops.wm.read_factory_settings(use_empty=True)
sc = bpy.context.scene
for eng in ("BLENDER_EEVEE_NEXT", "BLENDER_EEVEE"):
    try:
        sc.render.engine = eng
        break
    except TypeError:
        pass
sc.view_settings.view_transform = "Standard"
world = bpy.data.worlds.new("W"); sc.world = world; world.use_nodes = True
bg = next(n for n in world.node_tree.nodes if n.type == "BACKGROUND")
bg.inputs[0].default_value = (0.36, 0.38, 0.41, 1); bg.inputs[1].default_value = 0.55

MATS = {}
def mat(path):
    if path in MATS: return MATS[path]
    m = bpy.data.materials.new(os.path.basename(path)); m.use_nodes = True
    nt = m.node_tree
    bsdf = next(n for n in nt.nodes if n.type == "BSDF_PRINCIPLED")
    t = nt.nodes.new("ShaderNodeTexImage"); t.image = bpy.data.images.load(path); t.interpolation = "Closest"
    nt.links.new(t.outputs[0], bsdf.inputs["Base Color"]); nt.links.new(t.outputs[1], bsdf.inputs["Alpha"])
    bsdf.inputs["Roughness"].default_value = 0.85
    for attr, val in (("blend_method", "CLIP"), ("surface_render_method", "DITHERED")):
        try: setattr(m, attr, val)
        except Exception: pass
    MATS[path] = m
    return m

def load(name, texture, x):
    bpy.ops.wm.obj_import(filepath=os.path.join(OBJ, name + ".obj"), forward_axis="Y", up_axis="Z")
    ob = bpy.context.selected_objects[0]
    ob.location.x = x
    ob.data.materials.clear(); ob.data.materials.append(mat(texture))
    return ob

def clothing(g, slim): return os.path.join(TEX, "clothing", g + ("_slim" if slim and g in ("shirt", "jacket") else "") + ".png")

COLS = [("rest", []), ("rest", ["jeans", "shoes", "shirt"]), ("rest", ["jeans", "shoes", "shirt", "jacket"]),
        ("sat", ["jeans", "shoes", "shirt", "jacket"]), ("run", ["jeans", "shoes", "shirt", "jacket"]), ("crouch", ["jeans", "shoes", "shirt"])]

def figure(sex, shot, garments, x):
    obs = [load(f"{sex}_{shot}", os.path.join(TEX, "survivor", f"afl_{sex}_v1.png"), x)]
    for g in garments: obs.append(load(f"{sex}_{shot}_{g}", clothing(g, sex == "female"), x))
    return obs

def clear():
    for ob in list(sc.objects):
        if ob.type == "MESH" and ob.name != "floor": bpy.data.objects.remove(ob)

bpy.ops.mesh.primitive_plane_add(size=600, location=(0, 0, 0))
floor = bpy.context.active_object; floor.name = "floor"
fm = bpy.data.materials.new("floor"); fm.use_nodes = True
next(n for n in fm.node_tree.nodes if n.type == "BSDF_PRINCIPLED").inputs["Base Color"].default_value = (0.55, 0.56, 0.58, 1)
floor.data.materials.append(fm)
def light(name, energy, rot):
    l = bpy.data.lights.new(name, "SUN"); l.energy = energy; l.angle = math.radians(6)
    o = bpy.data.objects.new(name, l); sc.collection.objects.link(o)
    o.rotation_euler = tuple(math.radians(a) for a in rot)
light("sun", 3.2, (48, 0, 145)); light("rim", 1.2, (70, 0, -30))
cam = bpy.data.cameras.new("C"); co = bpy.data.objects.new("C", cam); sc.collection.objects.link(co); sc.camera = co

def shoot(d, target, dist, w, h, lens):
    cam.lens = lens
    sc.render.resolution_x, sc.render.resolution_y = w, h
    d = Vector(d).normalized()
    co.location = target + d * dist
    co.rotation_euler = (-d).to_track_quat("-Z", "Y").to_euler()
    p = os.path.join(os.path.dirname(OUT), "_tile.png")
    sc.render.filepath = p
    bpy.ops.render.render(write_still=True)
    img = bpy.data.images.load(p)
    a = np.array(img.pixels[:], dtype=np.float32).reshape(h, w, 4)[::-1, :, :3]
    bpy.data.images.remove(img)
    os.remove(p)
    return a

rows = []
for sex in ("male", "female"):
    tiles = []
    for shot, garments in COLS:
        clear()
        figure(sex, shot, garments, 0)
        tiles.append(shoot((0.62, 1, 0.2), Vector((0, 0, 12)), 62, 420, 640, 50))
    rows.append(np.concatenate(tiles, axis=1))
# the woman close: the chest bare, under the shirt and the jacket
close = []
# (r2, chest r4: the bra from the front, the side and from below, then the shirt and the jacket over it)
for garments, d in (([], (0.0, 1, 0.08)), ([], (1, 0.03, 0.05)), ([], (0.5, 1, -0.45)), (["jeans", "shirt"], (0.0, 1, 0.08)),
                    (["jeans", "shirt"], (1, 0.03, 0.05)), (["jeans", "shirt", "jacket"], (0.7, 1, 0.15))):
    clear()
    figure("female", "rest", garments, 0)
    close.append(shoot(d, Vector((0, 0, 17.5)), 30, 420, 640, 50))
rows.append(np.concatenate(close, axis=1))
sheet = np.clip(np.concatenate(rows, axis=0), 0, 1)
h, w = sheet.shape[:2]
img = bpy.data.images.new("sheet", w, h, alpha=False)
img.pixels[:] = np.concatenate([sheet[::-1], np.ones((h, w, 1), np.float32)], axis=2).ravel()
img.filepath_raw = OUT; img.file_format = "PNG"; img.save()
print("sheet", OUT, w, h)
